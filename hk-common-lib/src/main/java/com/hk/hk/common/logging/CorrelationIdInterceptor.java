package com.hk-fintech.hk.common.logging;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

public class CorrelationIdInterceptor implements RequestInterceptor {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            template.header(CORRELATION_HEADER, correlationId);
        }
    }
}
