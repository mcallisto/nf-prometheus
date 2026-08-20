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

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 * Minimal, dependency-free metrics registry rendering the Prometheus
 * text exposition format (version 0.0.4).
 *
 * Only what nf-prometheus needs: counters and gauges with labels.
 * Thread-safe: task events arrive from multiple threads.
 */
@CompileStatic
class MetricsRegistry {

    @Canonical
    static class MetricKey {
        String name
        Map<String,String> labels
    }

    private final Map<String,String> helps = new ConcurrentHashMap<>()
    private final Map<String,String> types = new ConcurrentHashMap<>()
    private final Map<MetricKey,Double> values = new ConcurrentHashMap<>()

    void describe(String name, String type, String help) {
        types.put(name, type)
        helps.put(name, help)
    }

    void inc(String name, Map<String,String> labels, double delta = 1d) {
        values.merge(new MetricKey(name, labels), delta, { Double a, Double b -> (a + b) as Double })
    }

    void set(String name, Map<String,String> labels, double value) {
        values.put(new MetricKey(name, labels), value)
    }

    void max(String name, Map<String,String> labels, double value) {
        values.merge(new MetricKey(name, labels), value, { Double a, Double b -> Math.max(a, b) as Double })
    }

    /**
     * Render all metrics in Prometheus text exposition format.
     */
    String render() {
        final sb = new StringBuilder()
        final byName = values.keySet().groupBy { MetricKey k -> k.name }
        for( String name : byName.keySet().sort() ) {
            final help = helps.get(name)
            final type = types.get(name)
            if( help ) sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
            if( type ) sb.append("# TYPE ").append(name).append(' ').append(type).append('\n')
            for( MetricKey key : byName.get(name).sort { MetricKey k -> k.labels.toString() } ) {
                sb.append(name)
                if( key.labels ) {
                    sb.append('{')
                    sb.append( key.labels.collect { k, v -> "${k}=\"${escape(v)}\"" }.join(',') )
                    sb.append('}')
                }
                sb.append(' ').append(format(values.get(key))).append('\n')
            }
        }
        return sb.toString()
    }

    static String escape(String value) {
        if( value == null ) return ''
        return value.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')
    }

    static String format(Double value) {
        if( value == null ) return '0'
        if( value == Math.floor(value) && !value.isInfinite() )
            return String.valueOf(value.longValue())
        return String.valueOf(value)
    }
}
