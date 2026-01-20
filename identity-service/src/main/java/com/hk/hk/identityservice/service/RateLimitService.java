package com.hk-fintech.hk.identityservice.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitService {

    // Tek bir Cache Map yeterli
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    public boolean tryConsume(RateLimitType type, String key) {
        // Key Çakışmasını Önlemek İçin: "LOGIN_ahmet@mail.com" veya "REGISTER_192.168.1.1"
        String cacheKey = type.name() + "_" + key;

        // Varsa getir, yoksa oluştur
        Bucket bucket = bucketCache.computeIfAbsent(cacheKey, k -> createNewBucket(type));

        return bucket.tryConsume(1);
    }

    private Bucket createNewBucket(RateLimitType type) {
        // Enum içindeki bilgileri okuyoruz (Clean Code)
        Bandwidth limit = Bandwidth.classic(type.getLimit(),
                Refill.greedy(type.getLimit(), type.getDuration()));

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}