package com.hk-fintech.hk.paymentservice.exception;

import com.hk-fintech.hk.common.exception.BusinessException;

public class PaymentFailedException extends BusinessException {

    public PaymentFailedException(String reason) {
        super("Ödeme işlemi başarısız: " + reason, 422);
    }
}
