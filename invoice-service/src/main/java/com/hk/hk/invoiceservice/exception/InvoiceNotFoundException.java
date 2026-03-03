package com.hk-fintech.hk.invoiceservice.exception;

import com.hk-fintech.hk.common.exception.BusinessException;

public class InvoiceNotFoundException extends BusinessException {

    public InvoiceNotFoundException(Long invoiceId) {
        super("Fatura bulunamadı! ID: " + invoiceId, 404);
    }
}
