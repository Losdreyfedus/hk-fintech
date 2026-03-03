package com.hk-fintech.hk.walletservice.config;

import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign client yapılandırması.
 * <p>
 * POST gibi idempotent olmayan (non-idempotent) isteklerde
 * otomatik retry mekanizması çift ödeme gibi kritik sorunlara yol açabilir.
 * Bu nedenle retry devre dışı bırakılmıştır.
 */
@Configuration
public class FeignConfig {

    @Bean
    public Retryer feignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
