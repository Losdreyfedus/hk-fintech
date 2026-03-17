package com.hk.invoiceservice.exception;

import com.hk.common.exception.BusinessException;

public class PaymentNotSuccessfulException extends BusinessException {

    public PaymentNotSuccessfulException(Long invoiceId) {
        super("Ödeme başarısız oldu! Fatura ödenemedi. Fatura ID: " + invoiceId, 422);
    }
}
