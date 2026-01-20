package com.hk-fintech.hk.identityservice.service;

import com.hk-fintech.hk.identityservice.client.WalletClient;
import com.hk-fintech.hk.identityservice.dto.request.AuthRequest;
import com.hk-fintech.hk.identityservice.dto.request.CreateWalletRequest;
import com.hk-fintech.hk.identityservice.dto.response.AuthResponse;
import com.hk-fintech.hk.identityservice.dto.request.RegisterRequest;
import com.hk-fintech.hk.identityservice.entity.Role;
import com.hk-fintech.hk.identityservice.entity.User;
import com.hk-fintech.hk.identityservice.repository.UserRepository;
import com.hk-fintech.hk.identityservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;


        private final WalletClient walletClient;


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

                CreateWalletRequest walletRequest = new CreateWalletRequest(savedUser.getId());
                walletClient.createWallet(walletRequest);

                var jwtToken = jwtService.generateToken(user);

                return AuthResponse.builder()
                                .token(jwtToken)
                                .build();
        }

        public AuthResponse login(AuthRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));

                var user = userRepository.findByEmail(request.getEmail())
                                .orElseThrow();

                var jwtToken = jwtService.generateToken(user);

                return AuthResponse.builder()
                                .token(jwtToken)
                                .build();
        }
}