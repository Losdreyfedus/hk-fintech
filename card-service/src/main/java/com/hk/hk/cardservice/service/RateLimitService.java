package com.hk-fintech.hk.cardservice.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(RateLimitType type, Long userId) {
        String key = type.name() + "_" + userId;
        return cache.computeIfAbsent(key, k -> newBucket(type));
    }

    private Bucket newBucket(RateLimitType type) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(type.getLimit())
                .refillGreedy(type.getLimit(), type.getDuration())
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}