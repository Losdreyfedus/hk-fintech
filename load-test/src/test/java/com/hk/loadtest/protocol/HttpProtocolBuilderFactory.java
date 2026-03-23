package com.hk.loadtest.protocol;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;
import static io.gatling.javaapi.http.HttpDsl.http;

public class HttpProtocolBuilderFactory {

    public static HttpProtocolBuilder createBaseProtocol() {
        return http
                .baseUrl(com.hk.loadtest.config.ConfigManager.getBaseUrl())
                .header("X-Correlation-ID", session -> UUID.randomUUID().toString())
                .acceptHeader("application/json")
                .contentTypeHeader("application/json")
                .userAgentHeader("Gatling Load Testing Framework");
    }
}
