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

import java.nio.charset.StandardCharsets
import java.util.function.Supplier

import com.sun.net.httpserver.HttpServer
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Tiny dependency-free HTTP endpoint serving the current metrics in
 * Prometheus text exposition format on {@code /metrics}.
 *
 * Lives for the duration of the workflow run: classic scrape mode for
 * head jobs whose node is reachable by the Prometheus server. For
 * firewalled or air-gapped nodes use the textfile or Pushgateway modes.
 */
@Slf4j
@CompileStatic
class MetricsHttpServer {

    private final HttpServer server
    private final Supplier<String> payload

    private MetricsHttpServer(HttpServer server, Supplier<String> payload) {
        this.server = server
        this.payload = payload
    }

    /**
     * Try to bind and start; returns null (and warns) on failure —
     * metrics must never break the pipeline.
     *
     * @param port TCP port to bind on all interfaces; 0 for an ephemeral port
     */
    static MetricsHttpServer tryStart(int port, Supplier<String> payload) {
        try {
            final server = HttpServer.create(new InetSocketAddress(port), 0)
            final instance = new MetricsHttpServer(server, payload)
            server.createContext('/metrics') { exchange ->
                try {
                    if( exchange.requestMethod != 'GET' ) {
                        exchange.sendResponseHeaders(405, -1)
                        return
                    }
                    final body = instance.payload.get().getBytes(StandardCharsets.UTF_8)
                    exchange.responseHeaders.set('Content-Type', 'text/plain; version=0.0.4; charset=utf-8')
                    exchange.sendResponseHeaders(200, body.length)
                    exchange.responseBody.write(body)
                }
                finally {
                    exchange.close()
                }
            }
            server.start()
            log.info "nf-prometheus: serving metrics on http://0.0.0.0:${server.address.port}/metrics"
            return instance
        }
        catch( Exception e ) {
            log.warn "nf-prometheus: cannot start metrics HTTP server on port ${port}: ${e.message}"
            return null
        }
    }

    int getPort() {
        return server.address.port
    }

    void stop() {
        try {
            server.stop(0)
        }
        catch( Exception e ) {
            log.debug "nf-prometheus: error stopping metrics HTTP server: ${e.message}"
        }
    }
}
