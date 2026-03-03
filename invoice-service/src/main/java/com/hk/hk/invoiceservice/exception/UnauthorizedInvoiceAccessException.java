package com.hk-fintech.hk.invoiceservice.exception;

import com.hk-fintech.hk.common.exception.BusinessException;

public class UnauthorizedInvoiceAccessException extends BusinessException {

    public UnauthorizedInvoiceAccessException() {
        super("Hata: Bu fatura size ait değil, ödeyemezsiniz!", 403);
    }
}
