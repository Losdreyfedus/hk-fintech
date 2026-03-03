package com.hk-fintech.hk.invoiceservice.dto.request;

import java.math.BigDecimal;

public record PaymentRequest(
        Long invoiceId,
        Long cardId,
        BigDecimal amount,
        PaymentMethod paymentMethod
) {}