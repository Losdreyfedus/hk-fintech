package com.hk.paymentservice.dto.client;

public record CardResponse(
        Long id,
        String cardHolder,
        String cardNumber,
        String cardToken
) {}