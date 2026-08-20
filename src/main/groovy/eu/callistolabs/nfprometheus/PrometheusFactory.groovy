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
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory

/**
 * Creates the {@link PrometheusObserver} when the plugin is enabled.
 */
@CompileStatic
class PrometheusFactory implements TraceObserverFactory {

    @Override
    Collection<TraceObserver> create(Session session) {
        final opts = session.config?.get('prometheus') as Map
        final config = new PrometheusConfig(opts)
        if( !config.enabled )
            return List.<TraceObserver>of()
        return List.<TraceObserver>of(new PrometheusObserver(config))
    }
}
