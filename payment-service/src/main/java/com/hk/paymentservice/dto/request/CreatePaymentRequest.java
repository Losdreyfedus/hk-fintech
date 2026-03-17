package com.hk.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull(message = "Fatura ID boş olamaz")
        Long invoiceId,

        Long cardId,

        @NotNull(message = "Tutar boş olamaz")
        @Positive(message = "Tutar 0'dan büyük olmalıdır")
        BigDecimal amount,
        
        @NotNull(message = "Ödeme yöntemi zorunludur")
        PaymentMethod paymentMethod
) {}