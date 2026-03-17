package com.hk.paymentservice.exception;

import com.hk.common.exception.BusinessException;

public class PaymentFailedException extends BusinessException {

    public PaymentFailedException(String reason) {
        super("Ödeme işlemi başarısız: " + reason, 422);
    }
}
