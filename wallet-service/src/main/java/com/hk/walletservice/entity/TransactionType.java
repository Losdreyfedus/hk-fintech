package com.hk.walletservice.entity;

public enum TransactionType {
    TOP_UP, // Cüzdana para yükleme
    INVOICE_PAYMENT, // Cüzdandan fatura ödeme
    TRANSFER // Cüzdandan cüzdana transfer (İleriki aşamalar için)
}
