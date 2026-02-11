package com.hk-fintech.hk.paymentservice.dto.client;

public record CardResponse(
        Long id,
        String cardHolder,
        String cardNumber,
        String cardToken
) {}