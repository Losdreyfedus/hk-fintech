package com.hk-fintech.hk.common.exception;

import lombok.Getter;

/**
 * Tüm servislerin iş kuralı ihlallerinde fırlattığı temel exception sınıfı.
 * Her alt sınıf kendi HTTP status kodunu ve açıklayıcı mesajını taşır.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final int httpStatus;

    protected BusinessException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}
