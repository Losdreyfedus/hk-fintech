package com.hk-fintech.hk.identityservice.controller;

import com.hk-fintech.hk.identityservice.dto.request.AuthRequest; // Login Request
import com.hk-fintech.hk.identityservice.dto.request.RegisterRequest;
import com.hk-fintech.hk.identityservice.service.AuthService;
import com.hk-fintech.hk.identityservice.service.RateLimitService;
import com.hk-fintech.hk.identityservice.service.RateLimitType;
import com.hk-fintech.hk.identityservice.util.ClientIpHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;
    private final ClientIpHelper clientIpHelper;

    // 1. REGISTER (Kayıt Ol) - IP Bazlı Kısıtlama
    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {

        // IP Adresini bul (Helper sayesinde)
        String clientIp = clientIpHelper.getClientIpAddress(servletRequest);

        // Kova kontrolü: Hakkı var mı?
        if (!rateLimitService.tryConsume(RateLimitType.REGISTER, clientIp)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS) // 429 Hatası
                    .body("Çok fazla kayıt denemesi! Lütfen 5 dakika bekleyiniz.");
        }

        // Hakkı varsa servise git
        return ResponseEntity.ok(authService.register(request));
    }

    // 2. LOGIN (Giriş Yap) - Email Bazlı Kısıtlama
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) { // LoginRequest veya AuthRequest

        // Kova kontrolü: Bu email için hak var mı?
        if (!rateLimitService.tryConsume(RateLimitType.LOGIN, request.getEmail())) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS) // 429 Hatası
                    .body("Çok fazla giriş denemesi. Lütfen 5 dakika bekleyiniz.");
        }

        // Hakkı varsa servise git
        return ResponseEntity.ok(authService.login(request));
    }
}