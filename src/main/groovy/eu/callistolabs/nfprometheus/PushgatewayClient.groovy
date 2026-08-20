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

import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Minimal Prometheus Pushgateway client.
 *
 * Pushes the whole exposition payload with PUT (replace) on the group
 * {@code job/<job>/run_name/<runName>}, so every workflow run owns its
 * own metric group and repeated pushes replace it atomically.
 *
 * Failures are logged (once) and never propagated: metrics must never
 * break a pipeline.
 */
@Slf4j
@CompileStatic
class PushgatewayClient {

    private final URI endpoint
    private final HttpClient http
    private volatile boolean warned

    PushgatewayClient(String baseUrl, String job, String runName) {
        final base = baseUrl.replaceAll('/+$', '')
        this.endpoint = URI.create(
            base + '/metrics/job/' + encode(job) + '/run_name/' + encode(runName) )
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

    URI getEndpoint() { endpoint }

    /**
     * PUT the payload; returns true on 2xx.
     */
    boolean push(String body) {
        try {
            final request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header('Content-Type', 'text/plain; version=0.0.4; charset=utf-8')
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()
            final response = http.send(request, HttpResponse.BodyHandlers.ofString())
            final ok = response.statusCode() >= 200 && response.statusCode() < 300
            if( !ok && !warned ) {
                warned = true
                log.warn "nf-prometheus: pushgateway returned HTTP ${response.statusCode()} for ${endpoint}"
            }
            return ok
        }
        catch( Exception e ) {
            if( !warned ) {
                warned = true
                log.warn "nf-prometheus: cannot reach pushgateway at ${endpoint}: ${e.message}"
            }
            return false
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value ?: 'unknown', StandardCharsets.UTF_8)
    }
}
