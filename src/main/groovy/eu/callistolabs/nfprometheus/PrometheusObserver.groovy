/*
 * Copyright 2026, Mario Callisto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.callistolabs.nfprometheus

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserverV2
import nextflow.trace.TraceRecord
import nextflow.trace.event.TaskEvent

/**
 * Collects workflow/task events and writes them as Prometheus metrics
 * in the node_exporter "textfile collector" format.
 *
 * The textfile mode requires NO network connectivity from the head job:
 * it only needs a path readable by a node_exporter instance (typically
 * on a shared filesystem), which makes it suitable for air-gapped and
 * firewalled HPC environments.
 */
@Slf4j
@CompileStatic
class PrometheusObserver implements TraceObserverV2 {

    private final PrometheusConfig config
    private final MetricsRegistry registry = new MetricsRegistry()

    /** Minimum interval between two pushes to the gateway during a run. */
    private static final long PUSH_INTERVAL_MS = 5_000

    private Session session
    private String runName
    private long startMillis
    private volatile boolean errored

    private PushgatewayClient pusher
    private long lastPushMillis
    private MetricsHttpServer httpServer

    PrometheusObserver(PrometheusConfig config) {
        this.config = config
    }

    MetricsRegistry getRegistry() { registry }

    /**
     * Ask Nextflow to collect per-task resource metrics (peak RSS, CPU usage).
     * Without this, {@code peak_rss} and friends are absent from the trace
     * record and the corresponding gauges stay empty.
     */
    @Override
    boolean enableMetrics() {
        return true
    }

    @Override
    void onFlowCreate(Session session) {
        this.session = session
        this.runName = session.runName ?: 'unknown'
        this.startMillis = System.currentTimeMillis()
        if( config.pushgateway )
            this.pusher = new PushgatewayClient(config.pushgateway, config.pushJob, runName)
        if( config.httpPort != null )
            this.httpServer = MetricsHttpServer.tryStart(config.httpPort, { registry.render() } as java.util.function.Supplier<String>)

        registry.describe('nf_workflow_info', 'gauge', 'Workflow run information (always 1)')
        registry.describe('nf_workflow_status', 'gauge', 'Workflow status: 0=running 1=complete 2=error')
        registry.describe('nf_workflow_duration_seconds', 'gauge', 'Wall clock duration of the workflow run')
        registry.describe('nf_tasks_total', 'counter', 'Tasks by process and final status')
        registry.describe('nf_task_queue_seconds_total', 'counter', 'Total time tasks spent queued (submit to start), by process')
        registry.describe('nf_task_realtime_seconds_total', 'counter', 'Total task execution wall time, by process')
        registry.describe('nf_task_cpus_requested_total', 'counter', 'Sum of CPUs requested by completed tasks, by process')
        registry.describe('nf_task_peak_rss_bytes_max', 'gauge', 'Maximum peak RSS observed among tasks, by process')

        registry.set('nf_workflow_info', [run_name: runName, session_id: session.uniqueId?.toString() ?: ''], 1d)
        registry.set('nf_workflow_status', [run_name: runName], 0d)
        write()
    }

    @Override
    void onTaskSubmit(TaskEvent event) {
        registry.inc('nf_tasks_total', [run_name: runName, process: processOf(event.trace), status: 'submitted'])
        write()
    }

    @Override
    void onTaskComplete(TaskEvent event) {
        final trace = event.trace
        final process = processOf(trace)
        final status = (trace.get('status') as String ?: 'COMPLETED').toLowerCase()
        registry.inc('nf_tasks_total', [run_name: runName, process: process, status: status])

        final submit = trace.get('submit') as Long
        final start = trace.get('start') as Long
        if( submit != null && start != null && start >= submit )
            registry.inc('nf_task_queue_seconds_total', [run_name: runName, process: process], (start - submit) / 1000d)

        final realtime = trace.get('realtime') as Long
        if( realtime != null )
            registry.inc('nf_task_realtime_seconds_total', [run_name: runName, process: process], realtime / 1000d)

        final cpus = trace.get('cpus') as Integer
        if( cpus != null )
            registry.inc('nf_task_cpus_requested_total', [run_name: runName, process: process], cpus as double)

        final peakRss = trace.get('peak_rss') as Long
        if( peakRss != null )
            registry.max('nf_task_peak_rss_bytes_max', [run_name: runName, process: process], peakRss as double)

        write()
    }

    @Override
    void onTaskCached(TaskEvent event) {
        registry.inc('nf_tasks_total', [run_name: runName, process: processOf(event.trace), status: 'cached'])
        write()
    }

    @Override
    void onFlowError(TaskEvent event) {
        errored = true
        registry.set('nf_workflow_status', [run_name: runName], 2d)
        write(true)
    }

    @Override
    void onFlowComplete() {
        // do not overwrite an error status
        if( !errored )
            registry.set('nf_workflow_status', [run_name: runName], 1d)
        registry.set('nf_workflow_duration_seconds', [run_name: runName], (System.currentTimeMillis() - startMillis) / 1000d)
        write(true)
        httpServer?.stop()
        log.debug "nf-prometheus: metrics written to ${outputPath()}"
    }

    private static String processOf(TraceRecord trace) {
        return trace?.get('process') as String ?: 'unknown'
    }

    protected Path outputPath() {
        final p = Paths.get(config.path)
        return p.isAbsolute() ? p : Paths.get(System.getProperty('user.dir')).resolve(p)
    }

    /**
     * Atomically (re)write the metrics file — temp file + rename, the
     * contract expected by the node_exporter textfile collector — and,
     * when a Pushgateway is configured, push the same payload (throttled
     * to one push every {@link #PUSH_INTERVAL_MS} unless {@code force}).
     */
    protected synchronized void write(boolean force = false) {
        final payload = registry.render()
        try {
            final target = outputPath()
            if( target.parent && !Files.exists(target.parent) )
                Files.createDirectories(target.parent)
            final tmp = target.resolveSibling(target.fileName.toString() + '.tmp')
            Files.writeString(tmp, payload)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        catch( Exception e ) {
            // metrics must never break the pipeline
            log.warn "nf-prometheus: cannot write metrics file: ${e.message}"
        }

        if( pusher != null ) {
            final now = System.currentTimeMillis()
            if( force || now - lastPushMillis >= PUSH_INTERVAL_MS ) {
                lastPushMillis = now
                pusher.push(payload)
            }
        }
    }
}
