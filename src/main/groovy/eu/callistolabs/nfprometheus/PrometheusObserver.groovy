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
import nextflow.processor.TaskHandler
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord

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
class PrometheusObserver implements TraceObserver {

    private final PrometheusConfig config
    private final MetricsRegistry registry = new MetricsRegistry()

    private Session session
    private String runName
    private long startMillis
    private volatile boolean errored

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
    void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
        registry.inc('nf_tasks_total', [run_name: runName, process: processOf(trace), status: 'submitted'])
        write()
    }

    @Override
    void onProcessComplete(TaskHandler handler, TraceRecord trace) {
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
    void onProcessCached(TaskHandler handler, TraceRecord trace) {
        registry.inc('nf_tasks_total', [run_name: runName, process: processOf(trace), status: 'cached'])
        write()
    }

    @Override
    void onFlowError(TaskHandler handler, TraceRecord trace) {
        errored = true
        registry.set('nf_workflow_status', [run_name: runName], 2d)
        write()
    }

    @Override
    void onFlowComplete() {
        // do not overwrite an error status
        if( !errored )
            registry.set('nf_workflow_status', [run_name: runName], 1d)
        registry.set('nf_workflow_duration_seconds', [run_name: runName], (System.currentTimeMillis() - startMillis) / 1000d)
        write()
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
     * Atomically (re)write the metrics file: write to a temp file in the
     * same directory, then rename. This is the contract expected by the
     * node_exporter textfile collector to avoid partial reads.
     */
    protected synchronized void write() {
        try {
            final target = outputPath()
            if( target.parent && !Files.exists(target.parent) )
                Files.createDirectories(target.parent)
            final tmp = target.resolveSibling(target.fileName.toString() + '.tmp')
            Files.writeString(tmp, registry.render())
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        catch( Exception e ) {
            // metrics must never break the pipeline
            log.warn "nf-prometheus: cannot write metrics file: ${e.message}"
        }
    }
}
