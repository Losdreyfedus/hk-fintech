package com.hk-fintech.hk.common.logging;

import java.util.Set;

public class DefaultLoggingConfigProvider implements LoggingConfigProvider {

    @Override
    public Set<String> getSensitiveHeaders() {
        return Set.of("authorization", "cookie", "set-cookie", "x-api-key");
    }

    @Override
    public Set<String> getSensitiveBodyFields() {
        return Set.of("password", "token", "secret", "creditCardNumber");
    }

    @Override
    public Set<String> getExcludedPaths() {
        return Set.of("/actuator", "/swagger", "/v3/api-docs", "/favicon.ico");
    }

    @Override
    public int getMaxBodyLogLength() {
        return 1000;
    }
}
