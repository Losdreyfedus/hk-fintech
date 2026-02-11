package com.hk-fintech.hk.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull(message = "Fatura ID boş olamaz")
        Long invoiceId,

        @NotNull(message = "Kart ID boş olamaz")
        Long cardId,

        @NotNull(message = "Tutar boş olamaz")
        @Positive(message = "Tutar 0'dan büyük olmalıdır")
        BigDecimal amount
) {}