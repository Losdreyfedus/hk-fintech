package com.hk-fintech.hk.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MASKED_VALUE = "***MASKED***";

    private final LoggingConfigProvider configProvider;
    private final ObjectMapper objectMapper;

    public HttpLoggingFilter(LoggingConfigProvider configProvider) {
        this.configProvider = configProvider;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);
        MDC.put("correlationId", correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            logIncomingRequest(wrappedRequest, correlationId);
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logOutgoingResponse(wrappedResponse, correlationId, duration, wrappedRequest.getRequestURI());
            wrappedResponse.copyBodyToResponse();
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private void logIncomingRequest(ContentCachingRequestWrapper request, String correlationId) {
        HttpLogEntity logEntity = HttpLogEntity.builder()
                .type("INCOMING_REQUEST")
                .correlationId(correlationId)
                .method(request.getMethod())
                .uri(request.getRequestURI())
                .queryString(request.getQueryString())
                .headers(extractHeaders(request))
                .build();

        log.info(toJson(logEntity));
    }

    private void logOutgoingResponse(ContentCachingResponseWrapper response,
            String correlationId,
            long duration,
            String uri) {

        HttpLogEntity logEntity = HttpLogEntity.builder()
                .type("OUTGOING_RESPONSE")
                .correlationId(correlationId)
                .uri(uri)
                .status(response.getStatus())
                .durationMs(duration)
                .body(maskSensitiveBodyFields(extractBody(response)))
                .build();

        logByStatus(toJson(logEntity), response.getStatus());
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            String value = configProvider.getSensitiveHeaders().contains(headerName.toLowerCase())
                    ? MASKED_VALUE
                    : request.getHeader(headerName);
            headers.put(headerName, value);
        });
        return headers;
    }

    private String extractBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        String body = new String(content, StandardCharsets.UTF_8);
        int maxLength = configProvider.getMaxBodyLogLength();
        return body.length() > maxLength
                ? body.substring(0, maxLength) + "... [TRUNCATED]"
                : body;
    }

    private String maskSensitiveBodyFields(String body) {
        if (body == null) {
            return null;
        }
        for (String field : configProvider.getSensitiveBodyFields()) {
            body = body.replaceAll(
                    "\"" + field + "\"\\s*:\\s*\"[^\"]*\"",
                    "\"" + field + "\":\"" + MASKED_VALUE + "\"");
        }
        return body;
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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return configProvider.getExcludedPaths().stream().anyMatch(path::startsWith);
    }
}
