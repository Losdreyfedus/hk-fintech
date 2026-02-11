package com.hk-fintech.hk.paymentservice.adapter;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class FakeBankAdapterImpl implements BankAdapter {

    @Override
    public boolean pay(String cardToken, BigDecimal amount) {

        System.out.println("fake-bank-api -> Ödeme alınıyor...");
        System.out.println("Token: " + cardToken);
        System.out.println("Tutar: " + amount);
        return true;
    }
}