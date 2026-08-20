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
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

/**
 * nf-prometheus plugin entry point.
 *
 * Exports Nextflow workflow and task metrics in the Prometheus
 * exposition format, designed for on-premise HPC clusters
 * (textfile collector first, no network requirements).
 */
@CompileStatic
class PrometheusPlugin extends BasePlugin {

    PrometheusPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }
}
