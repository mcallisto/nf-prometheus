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

import nextflow.Session
import spock.lang.Specification

class PrometheusFactoryTest extends Specification {

    def 'should create the observer by default'() {
        given:
        def session = Mock(Session) { getConfig() >> [:] }

        when:
        def result = new PrometheusFactory().create(session)

        then:
        result.size() == 1
        result.first() instanceof PrometheusObserver
    }

    def 'should create no observer when disabled'() {
        given:
        def session = Mock(Session) { getConfig() >> [prometheus: [enabled: false]] }

        when:
        def result = new PrometheusFactory().create(session)

        then:
        result.isEmpty()
    }

    def 'should read the output path from config'() {
        expect:
        new PrometheusConfig([path: '/shared/metrics/run.prom']).path == '/shared/metrics/run.prom'
        new PrometheusConfig(null).path == 'nf-prometheus.prom'
        new PrometheusConfig(null).enabled
    }
}
