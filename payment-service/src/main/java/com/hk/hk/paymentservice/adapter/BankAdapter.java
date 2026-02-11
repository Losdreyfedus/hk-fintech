package com.hk-fintech.hk.paymentservice.adapter;

import java.math.BigDecimal;

public interface BankAdapter {
    // Banka ne olursa olsun (Iyzico, Stripe, Fake) bu metodu sağlamak zorunda!
    boolean pay(String cardToken, BigDecimal amount);
}