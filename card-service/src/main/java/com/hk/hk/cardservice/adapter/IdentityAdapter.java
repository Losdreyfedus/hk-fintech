package com.hk-fintech.hk.cardservice.adapter;

import com.hk-fintech.hk.cardservice.client.IdentityClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityAdapter {

    private final IdentityClient identityClient;

    public void checkUserExists(Long userId) {
        try {
            boolean exists = identityClient.existsById(userId);
            if (!exists) {
                throw new RuntimeException("Kullanıcı bulunamadı! ID: " + userId);
            }
        } catch (FeignException e) {
            log.error("Identity servisine erişirken hata oluştu: {}", e.getMessage());
            throw new RuntimeException("Identity servisine erişilemiyor!");
        } catch (Exception e) {
            log.error("Beklenmedik bir hata: {}", e.getMessage());
            throw e;
        }
    }
}