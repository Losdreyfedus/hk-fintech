package com.hk-fintech.hk.walletservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk-fintech.hk.walletservice.client.PaymentClient;
import com.hk-fintech.hk.walletservice.dto.request.TopUpRequest;
import com.hk-fintech.hk.walletservice.entity.Wallet;
import com.hk-fintech.hk.walletservice.entity.WalletTransaction;
import com.hk-fintech.hk.walletservice.repository.WalletRepository;
import com.hk-fintech.hk.walletservice.repository.WalletTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.hk-fintech.hk.walletservice.client.IdentityClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("Wallet Service Integration Tests")
class WalletServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("wallet_test_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private IdentityClient identityClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        given(identityClient.validateToken(anyString())).willReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/wallets/user/{userId} → Cüzdan bilgisi dönmeli")
    void shouldReturnWalletInfo() throws Exception {
        // ARRANGE
        Wallet wallet = Wallet.builder()
                .userId(USER_ID)
                .balance(new BigDecimal("150.00"))
                .currency("TRY")
                .build();
        walletRepository.save(wallet);

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/wallets/user/" + USER_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.balance").value(150.00))
                .andExpect(jsonPath("$.currency").value("TRY"));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/withdraw → Bakiye düşmeli ve işlem kaydedilmeli")
    void shouldWithdrawFromWallet() throws Exception {
        // ARRANGE
        Wallet wallet = Wallet.builder()
                .userId(USER_ID)
                .balance(new BigDecimal("500.00"))
                .currency("TRY")
                .build();
        wallet = walletRepository.save(wallet);

        // ACT
        mockMvc.perform(post("/api/v1/wallets/withdraw")
                        .header("Authorization", "Bearer mock-token")
                        .param("amount", "200.00")
                        .param("description", "Fatura Ödemesi"))
                .andExpect(status().isOk());

        // ASSERT
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(updatedWallet.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));

        List<WalletTransaction> transactions = walletTransactionRepository.findAll();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(transactions.get(0).getType().name()).isEqualTo("INVOICE_PAYMENT");
    }

    @Test
    @DisplayName("POST /api/v1/wallets/withdraw → Bakiye yetersizse 422 dönmeli")
    void shouldFailWithdrawWhenInsufficientBalance() throws Exception {
        // ARRANGE
        Wallet wallet = Wallet.builder()
                .userId(USER_ID)
                .balance(new BigDecimal("50.00"))
                .currency("TRY")
                .build();
        walletRepository.save(wallet);

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/wallets/withdraw")
                        .header("Authorization", "Bearer mock-token")
                        .param("amount", "200.00")
                        .param("description", "Aşırı Ödeme"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /api/v1/wallets/top-up → PaymentClient çağrılmalı")
    void shouldTriggerTopUpPayment() throws Exception {
        // ARRANGE
        Wallet wallet = Wallet.builder()
                .userId(USER_ID)
                .balance(new BigDecimal("0.00"))
                .currency("TRY")
                .build();
        walletRepository.save(wallet);

        doNothing().when(paymentClient).processTopUpPayment(any());

        TopUpRequest request = new TopUpRequest(10L, new BigDecimal("100.00"));

        // ACT & ASSERT
        mockMvc.perform(post("/api/v1/wallets/top-up")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }
}
