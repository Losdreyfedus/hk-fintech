package com.hk-fintech.hk.invoiceservice.dto.request;

import jakarta.validation.constraints.NotNull;

public record PayInvoiceRequest(
        @NotNull(message = "Kart seçimi zorunludur")
        Long cardId
) {}