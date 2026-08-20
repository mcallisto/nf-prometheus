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

import java.util.concurrent.atomic.AtomicReference

import com.sun.net.httpserver.HttpServer
import spock.lang.Specification

class PushgatewayClientTest extends Specification {

    def 'should PUT the payload on the run group and report success'() {
        given:
        def method = new AtomicReference<String>()
        def path = new AtomicReference<String>()
        def body = new AtomicReference<String>()
        def server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/') { exchange ->
            method.set(exchange.requestMethod)
            path.set(exchange.requestURI.path)
            body.set(new String(exchange.requestBody.readAllBytes()))
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        def base = "http://127.0.0.1:${server.address.port}"

        when:
        def client = new PushgatewayClient(base, 'nextflow', 'boring_euler')
        def ok = client.push('nf_workflow_status{run_name="boring_euler"} 1\n')

        then:
        ok
        method.get() == 'PUT'
        path.get() == '/metrics/job/nextflow/run_name/boring_euler'
        body.get().contains('nf_workflow_status')

        cleanup:
        server.stop(0)
    }

    def 'should fail quietly when the gateway is unreachable'() {
        given:
        def client = new PushgatewayClient('http://127.0.0.1:1', 'nextflow', 'r')

        expect:
        !client.push('x 1\n')
    }

    def 'should build the endpoint from config defaults'() {
        expect:
        new PushgatewayClient('http://pg:9091/', 'nextflow', 'my run')
            .endpoint.toString() == 'http://pg:9091/metrics/job/nextflow/run_name/my+run'
    }
}
