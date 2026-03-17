package com.hk.invoiceservice.exception;

import com.hk.common.exception.BusinessException;

public class UnauthorizedInvoiceAccessException extends BusinessException {

    public UnauthorizedInvoiceAccessException() {
        super("Hata: Bu fatura size ait değil, ödeyemezsiniz!", 403);
    }
}
