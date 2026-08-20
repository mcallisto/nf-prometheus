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

import spock.lang.Specification

class MetricsRegistryTest extends Specification {

    def 'should render counters with labels in exposition format'() {
        given:
        def registry = new MetricsRegistry()
        registry.describe('nf_tasks_total', 'counter', 'Tasks by process and final status')

        when:
        registry.inc('nf_tasks_total', [process: 'FASTQC', status: 'completed'])
        registry.inc('nf_tasks_total', [process: 'FASTQC', status: 'completed'])
        registry.inc('nf_tasks_total', [process: 'MULTIQC', status: 'failed'])
        def out = registry.render()

        then:
        out.contains('# HELP nf_tasks_total Tasks by process and final status')
        out.contains('# TYPE nf_tasks_total counter')
        out.contains('nf_tasks_total{process="FASTQC",status="completed"} 2')
        out.contains('nf_tasks_total{process="MULTIQC",status="failed"} 1')
    }

    def 'should set and max gauges'() {
        given:
        def registry = new MetricsRegistry()

        when:
        registry.set('nf_workflow_status', [run_name: 'boring_euler'], 0d)
        registry.set('nf_workflow_status', [run_name: 'boring_euler'], 1d)
        registry.max('nf_task_peak_rss_bytes_max', [process: 'ALIGN'], 100d)
        registry.max('nf_task_peak_rss_bytes_max', [process: 'ALIGN'], 50d)
        def out = registry.render()

        then:
        out.contains('nf_workflow_status{run_name="boring_euler"} 1')
        out.contains('nf_task_peak_rss_bytes_max{process="ALIGN"} 100')
    }

    def 'should escape label values'() {
        expect:
        MetricsRegistry.escape('a"b\\c\nd') == 'a\\"b\\\\c\\nd'
    }

    def 'should format integral values without decimals'() {
        expect:
        MetricsRegistry.format(2.0d) == '2'
        MetricsRegistry.format(2.5d) == '2.5'
        MetricsRegistry.format(null) == '0'
    }
}
