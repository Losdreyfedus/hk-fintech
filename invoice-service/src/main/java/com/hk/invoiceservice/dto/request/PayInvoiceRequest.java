package com.hk.invoiceservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record PayInvoiceRequest(
        Long cardId, // Eğer cüzdanla ödenecekse null olabilir

        @NotNull(message = "Ödeme yöntemi zorunludur")
        PaymentMethod paymentMethod
) {}