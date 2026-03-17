package com.hk.invoiceservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Service'in döndürdüğü ödeme sonucuna karşılık gelen DTO.
 */
public record PaymentResponse(
        Long id,
        Long invoiceId,
        BigDecimal amount,
        String status,
        LocalDateTime createdDate) {
}
