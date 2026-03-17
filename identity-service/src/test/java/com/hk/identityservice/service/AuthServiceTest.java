package com.hk.identityservice.service;

import com.hk.identityservice.dto.request.AuthRequest;
import com.hk.identityservice.dto.request.RegisterRequest;
import com.hk.identityservice.dto.response.AuthResponse;
import com.hk.identityservice.entity.Role;
import com.hk.identityservice.entity.User;
import com.hk.identityservice.kafka.producer.IdentityProducer;
import com.hk.identityservice.repository.UserRepository;
import com.hk.identityservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private IdentityProducer identityProducer;

    @InjectMocks
    private AuthService authService;

    private static final String TEST_EMAIL = "enes@hk-fintech.com";
    private static final String TEST_PASSWORD = "Secure123!";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";
    private static final String JWT_TOKEN = "eyJhbGciOiJIUzI1NiJ9.test.token";

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .firstName("Enes")
                .lastName("K.")
                .email(TEST_EMAIL)
                .phoneNumber("5321234567")
                .password(ENCODED_PASSWORD)
                .role(Role.USER)
                .build();
    }

    @Nested
    @DisplayName("Kayıt Ol (register)")
    class RegisterTests {

        @Test
        @DisplayName("Geçerli bilgilerle → kullanıcı oluşturulmalı, JWT dönmeli ve Kafka event gönderilmeli")
        void shouldRegisterUser_AndReturnJwtToken() {
            // ARRANGE
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("Enes")
                    .lastName("K.")
                    .email(TEST_EMAIL)
                    .password(TEST_PASSWORD)
                    .phoneNumber("5321234567")
                    .build();

            given(passwordEncoder.encode(TEST_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willReturn(testUser);
            given(jwtService.generateToken(anyMap(), any(User.class))).willReturn(JWT_TOKEN);

            // ACT
            AuthResponse response = authService.register(request);

            // ASSERT
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo(JWT_TOKEN);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User savedUser = captor.getValue();
            assertThat(savedUser.getRole()).isEqualTo(Role.USER);
            assertThat(savedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);

            verify(identityProducer).scheduleUserCreatedEvent(any());
        }

        @Test
        @DisplayName("Kayıt sırasında şifre encode edilmeli (plain text kaydedilMEMELİ)")
        void shouldEncodePassword_BeforeSaving() {
            // ARRANGE
            RegisterRequest request = RegisterRequest.builder()
                    .firstName("Enes").lastName("K.")
                    .email(TEST_EMAIL).password(TEST_PASSWORD)
                    .phoneNumber("5321234567").build();

            given(passwordEncoder.encode(TEST_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willReturn(testUser);
            given(jwtService.generateToken(anyMap(), any(User.class))).willReturn(JWT_TOKEN);

            // ACT
            authService.register(request);

            // ASSERT
            verify(passwordEncoder).encode(TEST_PASSWORD);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isNotEqualTo(TEST_PASSWORD);
        }
    }

    @Nested
    @DisplayName("Giriş Yap (login)")
    class LoginTests {

        @Test
        @DisplayName("Doğru kimlik bilgileriyle → JWT token dönmeli")
        void shouldReturnJwtToken_WhenCredentialsAreValid() {
            // ARRANGE
            AuthRequest request = AuthRequest.builder()
                    .email(TEST_EMAIL)
                    .password(TEST_PASSWORD)
                    .build();

            given(userRepository.findByEmail(TEST_EMAIL)).willReturn(Optional.of(testUser));
            given(jwtService.generateToken(anyMap(), any(User.class))).willReturn(JWT_TOKEN);

            // ACT
            AuthResponse response = authService.login(request);

            // ASSERT
            assertThat(response.getToken()).isEqualTo(JWT_TOKEN);
            verify(authenticationManager).authenticate(any());
        }

        @Test
        @DisplayName("Var olmayan email ile → NoSuchElementException fırlatılmalı")
        void shouldThrowException_WhenEmailNotFound() {
            // ARRANGE
            AuthRequest request = AuthRequest.builder()
                    .email("ghost@email.com")
                    .password(TEST_PASSWORD)
                    .build();

            given(userRepository.findByEmail("ghost@email.com")).willReturn(Optional.empty());

            // ACT & ASSERT
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("Token Doğrulama (validateToken)")
    class ValidateTokenTests {

        @Test
        @DisplayName("Geçerli token ve var olan kullanıcı → userId dönmeli")
        void shouldReturnUserId_WhenTokenIsValidAndUserExists() {
            // ARRANGE
            given(jwtService.validateToken(JWT_TOKEN)).willReturn(true);
            given(jwtService.extractUserId(JWT_TOKEN)).willReturn(1L);
            given(userRepository.existsById(1L)).willReturn(true);

            // ACT
            Long userId = authService.validateToken(JWT_TOKEN);

            // ASSERT
            assertThat(userId).isEqualTo(1L);
        }

        @Test
        @DisplayName("Geçersiz token → RuntimeException fırlatılmalı")
        void shouldThrowException_WhenTokenIsInvalid() {
            // ARRANGE
            given(jwtService.validateToken("bad-token")).willReturn(false);

            // ACT & ASSERT
            assertThatThrownBy(() -> authService.validateToken("bad-token"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Geçersiz");
        }

        @Test
        @DisplayName("Geçerli token ama kullanıcı silinmişse → RuntimeException fırlatılmalı")
        void shouldThrowException_WhenUserNoLongerExists() {
            // ARRANGE
            given(jwtService.validateToken(JWT_TOKEN)).willReturn(true);
            given(jwtService.extractUserId(JWT_TOKEN)).willReturn(999L);
            given(userRepository.existsById(999L)).willReturn(false);

            // ACT & ASSERT
            assertThatThrownBy(() -> authService.validateToken(JWT_TOKEN))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("bulunamadı");
        }
    }

    @Nested
    @DisplayName("Kullanıcı Varlık Kontrolü (existsById)")
    class ExistsByIdTests {

        @Test
        @DisplayName("Kullanıcı varsa → true dönmeli")
        void shouldReturnTrue_WhenUserExists() {
            given(userRepository.existsById(1L)).willReturn(true);
            assertThat(authService.existsById(1L)).isTrue();
        }

        @Test
        @DisplayName("Kullanıcı yoksa → false dönmeli")
        void shouldReturnFalse_WhenUserDoesNotExist() {
            given(userRepository.existsById(999L)).willReturn(false);
            assertThat(authService.existsById(999L)).isFalse();
        }
    }
}
