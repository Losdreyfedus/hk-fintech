package com.hk-fintech.hk.invoiceservice.dto.response;

import com.hk-fintech.hk.invoiceservice.entity.BillType;
import com.hk-fintech.hk.invoiceservice.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceResponse(
        Long id,
        Long userId,             // Faturanın kime ait olduğu
        BillType billType,       // Örn: GSM
        String institutionName,  // Örn: Hk-fintech
        String accountNumber,    // Örn: 532...
        String description,      // Örn: Şubat Faturası
        BigDecimal amount,       // Örn: 450.50
        InvoiceStatus status,    // PENDING, PAID
        LocalDate dueDate,       // Son ödeme tarihi
        LocalDateTime createdDate // Faturanın sisteme düştüğü an
) {}