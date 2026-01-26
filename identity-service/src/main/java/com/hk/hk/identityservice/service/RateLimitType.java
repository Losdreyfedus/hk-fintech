package com.hk-fintech.hk.identityservice.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum RateLimitType {
    LOGIN(3, Duration.ofMinutes(5)),   // 5 dakikada 3 hak
    REGISTER(1, Duration.ofMinutes(5)); // 5 dakikada 1 hak

    private final int limit;
    private final Duration duration;
}