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

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {

        String clientIp = clientIpHelper.getClientIpAddress(servletRequest);

        if (!rateLimitService.tryConsume(RateLimitType.REGISTER, clientIp)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Çok fazla kayıt denemesi! Lütfen 5 dakika bekleyiniz.");
        }

        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {

        if (!rateLimitService.tryConsume(RateLimitType.LOGIN, request.getEmail())) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Çok fazla giriş denemesi. Lütfen 5 dakika bekleyiniz.");
        }

        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/validate")
    public ResponseEntity<Long> validateToken(@RequestParam("token") String token) {
        return ResponseEntity.ok(authService.validateToken(token));
    }
}