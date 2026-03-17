package com.hk.paymentservice.dto.response;

import com.hk.paymentservice.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long invoiceId,
        BigDecimal amount,
        PaymentStatus status,
        LocalDateTime createdDate
) {}