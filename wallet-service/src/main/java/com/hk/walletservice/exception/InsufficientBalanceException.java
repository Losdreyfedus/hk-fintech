package com.hk.walletservice.exception;

import com.hk.common.exception.BusinessException;

import java.math.BigDecimal;

public class InsufficientBalanceException extends BusinessException {

    public InsufficientBalanceException(BigDecimal currentBalance, BigDecimal requestedAmount) {
        super("Cüzdan bakiyesi yetersiz! Mevcut: " + currentBalance + ", İstenen: " + requestedAmount, 422);
    }
}
