package com.hk.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TopUpPaymentRequest(
        @NotNull(message = "Kart ID boş olamaz")
        Long cardId,

        @NotNull(message = "Tutar boş olamaz")
        @Positive(message = "Tutar 0'dan büyük olmalıdır")
        BigDecimal amount
) {}
