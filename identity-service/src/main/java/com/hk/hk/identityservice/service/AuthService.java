package com.hk-fintech.hk.identityservice.service;

import com.hk-fintech.hk.common.events.kafka.UserCreatedEvent;
import com.hk-fintech.hk.identityservice.dto.request.AuthRequest;
import com.hk-fintech.hk.identityservice.dto.response.AuthResponse;
import com.hk-fintech.hk.identityservice.dto.request.RegisterRequest;
import com.hk-fintech.hk.identityservice.entity.Role;
import com.hk-fintech.hk.identityservice.entity.User;
import com.hk-fintech.hk.identityservice.kafka.producer.IdentityProducer;
import com.hk-fintech.hk.identityservice.repository.UserRepository;
import com.hk-fintech.hk.identityservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final IdentityProducer identityProducer;

        @Transactional
        public AuthResponse register(RegisterRequest request) {
                var user = User.builder()
                        .firstName(request.getFirstName())
                        .lastName(request.getLastName())
                        .email(request.getEmail())
                        .phoneNumber(request.getPhoneNumber())
                        .password(passwordEncoder.encode(request.getPassword()))
                        .role(Role.USER)
                        .build();

                User savedUser = userRepository.save(user);
                log.info("Kullanıcı DB'ye kaydedildi. ID: {}", savedUser.getId());

                UserCreatedEvent event = new UserCreatedEvent(
                        savedUser.getId(),
                        savedUser.getEmail(),
                        savedUser.getFirstName() + " " + savedUser.getLastName()
                );
                identityProducer.scheduleUserCreatedEvent(event);

                String jwtToken = generateToken(savedUser);

                return AuthResponse.builder()
                        .token(jwtToken)
                        .build();
        }

        public AuthResponse login(AuthRequest request) {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                var user = userRepository.findByEmail(request.getEmail()).orElseThrow();

                String jwtToken = generateToken(user);

                return AuthResponse.builder().token(jwtToken).build();
        }

                public boolean existsById(Long id) {
                return userRepository.existsById(id);
        }

        private String generateToken(User user) {
                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", user.getId());
                claims.put("role", user.getRole());
                return jwtService.generateToken(claims, user);
        }

        public Long validateToken(String token) {
                if (!jwtService.validateToken(token)) {
                        throw new RuntimeException("Geçersiz veya süresi dolmuş token!");
                }

                Long userId = jwtService.extractUserId(token);
                if (!userRepository.existsById(userId)) {
                        throw new RuntimeException("Kullanıcı bulunamadı!");
                }

                return userId;
        }
}
