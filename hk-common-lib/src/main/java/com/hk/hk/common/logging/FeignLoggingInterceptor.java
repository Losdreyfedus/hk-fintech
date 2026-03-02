package com.hk-fintech.hk.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Request;
import feign.Response;
import feign.Util;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class FeignLoggingInterceptor extends Logger {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(FeignLoggingInterceptor.class);
    private static final String MASKED_VALUE = "***MASKED***";

    private final LoggingConfigProvider configProvider;
    private final ObjectMapper objectMapper;

    public FeignLoggingInterceptor(LoggingConfigProvider configProvider) {
        this.configProvider = configProvider;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        log.debug(String.format(methodTag(configKey) + format, args));
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        String body = request.body() != null
                ? new String(request.body(), StandardCharsets.UTF_8)
                : null;

        HttpLogEntity logEntity = HttpLogEntity.builder()
                .type("FEIGN_REQUEST")
                .correlationId(MDC.get("correlationId"))
                .method(request.httpMethod().name())
                .uri(request.url())
                .body(body)
                .targetService(extractServiceName(configKey))
                .headers(maskHeaders(request.headers()))
                .build();

        log.info(toJson(logEntity));
    }

    @Override
    protected Response logAndRebufferResponse(String configKey,
            Level logLevel,
            Response response,
            long elapsedTime) throws IOException {

        byte[] bodyData = response.body() != null
                ? Util.toByteArray(response.body().asInputStream())
                : null;

        String body = bodyData != null ? new String(bodyData, StandardCharsets.UTF_8) : null;
        if (body != null && body.length() > configProvider.getMaxBodyLogLength()) {
            body = body.substring(0, configProvider.getMaxBodyLogLength()) + "... [TRUNCATED]";
        }

        HttpLogEntity logEntity = HttpLogEntity.builder()
                .type("FEIGN_RESPONSE")
                .correlationId(MDC.get("correlationId"))
                .method(response.request().httpMethod().name())
                .uri(response.request().url())
                .status(response.status())
                .durationMs(elapsedTime)
                .body(body)
                .targetService(extractServiceName(configKey))
                .headers(maskHeaders(response.headers()))
                .build();

        logByStatus(toJson(logEntity), response.status());

        return response.toBuilder()
                .body(bodyData)
                .build();
    }

    private String extractServiceName(String configKey) {
        return configKey.contains("#")
                ? configKey.substring(0, configKey.indexOf('#'))
                : configKey;
    }

    private Map<String, String> maskHeaders(Map<String, Collection<String>> rawHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rawHeaders == null) {
            return headers;
        }
        rawHeaders.forEach((name, values) -> {
            String value = configProvider.getSensitiveHeaders().contains(name.toLowerCase())
                    ? MASKED_VALUE
                    : String.join(", ", values);
            headers.put(name, value);
        });
        return headers;
    }

    private void logByStatus(String jsonLog, int status) {
        if (status >= 500) {
            log.error(jsonLog);
        } else if (status >= 400) {
            log.warn(jsonLog);
        } else {
            log.info(jsonLog);
        }
    }

    private String toJson(HttpLogEntity entity) {
        try {
            return objectMapper.writeValueAsString(entity);
        } catch (JsonProcessingException e) {
            log.warn("HttpLogEntity serialization failed", e);
            return entity.toString();
        }
    }
}
