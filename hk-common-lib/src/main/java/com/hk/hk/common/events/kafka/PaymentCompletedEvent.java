package com.hk-fintech.hk.common.events.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long paymentId;
    private Long invoiceId;
    private Long userId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime completedAt;
}
