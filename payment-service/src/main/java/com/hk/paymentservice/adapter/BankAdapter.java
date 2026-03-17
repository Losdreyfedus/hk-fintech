package com.hk.paymentservice.adapter;

import java.math.BigDecimal;

public interface BankAdapter {
    boolean pay(String cardToken, BigDecimal amount);
}