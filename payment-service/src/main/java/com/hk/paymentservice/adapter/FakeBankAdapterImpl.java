package com.hk.paymentservice.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@Slf4j
public class FakeBankAdapterImpl implements BankAdapter {

    @Override
    public boolean pay(String cardToken, BigDecimal amount) {
        log.info("fake-bank-api -> Ödeme alınıyor... Token: {}, Tutar: {}", cardToken, amount);
        return true;
    }
}
