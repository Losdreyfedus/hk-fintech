package com.hk.loadtest.config;

import java.util.Optional;

public class ConfigManager {

    public static String getBaseUrl() {
        return getEnv("BASE_URL", "http://localhost");

    public static int getBaseUsers() {
        return Integer.parseInt(getEnv("BASE_USERS", "10"));
    }

    public static int getSpikeUsers() {
        return Integer.parseInt(getEnv("SPIKE_USERS", "500"));
    }

    public static int getDurationSeconds() {
        return Integer.parseInt(getEnv("DURATION_SECONDS", "30"));
    }

    private static String getEnv(String key, String defaultValue) {
        return Optional.ofNullable(System.getenv(key))
                .orElse(defaultValue);
    }
}
