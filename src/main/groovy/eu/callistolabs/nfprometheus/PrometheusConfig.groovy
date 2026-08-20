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

import groovy.transform.CompileStatic

/**
 * Configuration for the nf-prometheus plugin, read from the
 * {@code prometheus} scope of the Nextflow configuration:
 *
 * <pre>
 * prometheus {
 *     enabled = true
 *     path    = 'nf-prometheus.prom'   // textfile-collector output
 * }
 * </pre>
 */
@CompileStatic
class PrometheusConfig {

    /** Master switch; the plugin is active when it is loaded, unless disabled. */
    final boolean enabled

    /** Output path of the metrics file (node_exporter textfile collector format). */
    final String path

    PrometheusConfig(Map opts) {
        opts = opts ?: Collections.emptyMap()
        this.enabled = opts.get('enabled') == null ? true : opts.get('enabled') as boolean
        this.path = opts.get('path') as String ?: 'nf-prometheus.prom'
    }
}
