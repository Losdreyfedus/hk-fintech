package com.hk-fintech.hk.cardservice.dto.response;

public record CardResponse(
        Long id,
        String cardHolder,
        String maskedCardNumber,
        String expireMonth,
        String expireYear,
        String cardAlias,
        String cardToken
) {}