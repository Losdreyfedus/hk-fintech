package com.hk.invoiceservice.dto.request;

import com.hk.invoiceservice.entity.BillType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInvoiceRequest(
        @NotNull(message = "User ID boş olamaz (Fatura kime kesilecek?)")
        Long userId,

        @NotNull(message = "Fatura tipi seçilmelidir (Elektrik, Su, GSM vb.)")
        BillType billType,

        @NotBlank(message = "Kurum adı boş olamaz (Örn: Hk-fintech, İSKİ)")
        String institutionName,

        @NotBlank(message = "Abone/Hizmet numarası boş olamaz")
        String accountNumber,

        @NotBlank(message = "Açıklama boş olamaz")
        String description,

        @NotNull(message = "Tutar girilmelidir")
        @DecimalMin(value = "0.01", message = "Fatura tutarı en az 0.01 olabilir")
        BigDecimal amount,

        @NotNull(message = "Son ödeme tarihi girilmelidir")
        @Future(message = "Son ödeme tarihi geçmiş bir tarih olamaz")
        LocalDate dueDate
) {}