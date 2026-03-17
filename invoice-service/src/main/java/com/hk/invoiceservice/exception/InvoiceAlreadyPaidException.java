package com.hk.invoiceservice.exception;

import com.hk.common.exception.BusinessException;

public class InvoiceAlreadyPaidException extends BusinessException {

    public InvoiceAlreadyPaidException(Long invoiceId) {
        super("Bu fatura zaten ödenmiş! Fatura ID: " + invoiceId, 409);
    }
}
