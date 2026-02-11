package com.hk-fintech.hk.cardservice.service;

import lombok.Getter;
import java.time.Duration;

@Getter
public enum RateLimitType {

    CARD_CREATE(5, Duration.ofMinutes(1)),

    CARD_LIST(20, Duration.ofMinutes(1));

    private final long limit;
    private final Duration duration;

    RateLimitType(long limit, Duration duration) {
        this.limit = limit;
        this.duration = duration;
    }
}