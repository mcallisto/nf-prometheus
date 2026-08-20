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

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.function.Supplier

import spock.lang.Specification

class MetricsHttpServerTest extends Specification {

    def 'should serve the current payload on /metrics'() {
        given:
        def server = MetricsHttpServer.tryStart(0, { 'nf_workflow_status{run_name="r"} 0\n' } as Supplier<String>)
        def http = HttpClient.newHttpClient()

        when:
        def response = http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.port}/metrics")).GET().build(),
            HttpResponse.BodyHandlers.ofString() )

        then:
        response.statusCode() == 200
        response.headers().firstValue('Content-Type').get().startsWith('text/plain')
        response.body().contains('nf_workflow_status')

        cleanup:
        server?.stop()
    }

    def 'should return 404 outside /metrics and 405 for non-GET'() {
        given:
        def server = MetricsHttpServer.tryStart(0, { 'x 1\n' } as Supplier<String>)
        def http = HttpClient.newHttpClient()

        expect:
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.port}/other")).GET().build(),
            HttpResponse.BodyHandlers.ofString()).statusCode() == 404
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.port}/metrics"))
            .POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString()).statusCode() == 405

        cleanup:
        server?.stop()
    }

    def 'should return null instead of failing when the port is taken'() {
        given:
        def first = MetricsHttpServer.tryStart(0, { 'x 1\n' } as Supplier<String>)

        expect:
        MetricsHttpServer.tryStart(first.port, { 'x 1\n' } as Supplier<String>) == null

        cleanup:
        first?.stop()
    }
}
