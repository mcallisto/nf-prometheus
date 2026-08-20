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
import nextflow.config.spec.ConfigOption
import nextflow.config.spec.ConfigScope
import nextflow.config.spec.ScopeName
import nextflow.script.dsl.Description

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
 *
 * Registered as a {@link ConfigScope} extension point so that Nextflow
 * recognizes the {@code prometheus} block (no "unrecognized config
 * option" warnings; config validation and documentation support).
 */
@ScopeName('prometheus')
@Description('The `prometheus` scope allows you to configure the `nf-prometheus` plugin.')
@CompileStatic
class PrometheusConfig implements ConfigScope {

    @ConfigOption
    @Description('Enable or disable the nf-prometheus observer (default: `true`).')
    Boolean enabled

    @ConfigOption
    @Description('Path of the metrics file written in the node_exporter textfile-collector format (default: `nf-prometheus.prom`, relative to the launch directory).')
    String path

    @ConfigOption
    @Description('Base URL of a Prometheus Pushgateway (e.g. `http://pushgateway.example.org:9091`). When set, metrics are also pushed to the group `job/<pushJob>/run_name/<run name>`. Empty by default (disabled).')
    String pushgateway

    @ConfigOption
    @Description('Job name used in the Pushgateway grouping key (default: `nextflow`).')
    String pushJob

    /* no-arg constructor required by the extension point system for config schema discovery */
    PrometheusConfig() {}

    PrometheusConfig(Map opts) {
        opts = opts ?: Collections.emptyMap()
        this.enabled = opts.get('enabled') == null ? true : opts.get('enabled') as boolean
        this.path = opts.get('path') as String ?: 'nf-prometheus.prom'
        this.pushgateway = opts.get('pushgateway') as String
        this.pushJob = opts.get('pushJob') as String ?: 'nextflow'
    }

    boolean isEnabled() {
        return enabled == null ? true : enabled
    }

    String getPath() {
        return path ?: 'nf-prometheus.prom'
    }

    String getPushJob() {
        return pushJob ?: 'nextflow'
    }
}
