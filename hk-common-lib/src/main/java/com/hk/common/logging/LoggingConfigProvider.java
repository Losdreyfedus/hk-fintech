package com.hk.common.logging;

import java.util.Set;

public interface LoggingConfigProvider {

    Set<String> getSensitiveHeaders();

    Set<String> getSensitiveBodyFields();

    Set<String> getExcludedPaths();

    int getMaxBodyLogLength();
}
