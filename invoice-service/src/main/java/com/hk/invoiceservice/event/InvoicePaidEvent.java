package com.hk.invoiceservice.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoicePaidEvent(
        Long invoiceId,
        Long userId,
        BigDecimal amount,
        String institutionName,
        LocalDateTime paidAt
) {}