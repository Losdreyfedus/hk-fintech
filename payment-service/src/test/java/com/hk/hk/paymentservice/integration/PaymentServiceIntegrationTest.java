package com.hk-fintech.hk.paymentservice.integration;

import com.hk-fintech.hk.paymentservice.adapter.BankAdapter;
import com.hk-fintech.hk.paymentservice.client.CardServiceClient;
import com.hk-fintech.hk.paymentservice.client.IdentityServiceClient;
import com.hk-fintech.hk.paymentservice.client.WalletServiceClient;
import com.hk-fintech.hk.paymentservice.dto.client.CardResponse;
import com.hk-fintech.hk.paymentservice.dto.request.CreatePaymentRequest;
import com.hk-fintech.hk.paymentservice.dto.request.PaymentMethod;
import com.hk-fintech.hk.paymentservice.dto.request.TopUpPaymentRequest;
import com.hk-fintech.hk.paymentservice.entity.Payment;
import com.hk-fintech.hk.paymentservice.entity.PaymentStatus;
import com.hk-fintech.hk.paymentservice.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Payment Service Integration Tests")
class PaymentServiceIntegrationTest {

        @Container
        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                        DockerImageName.parse("postgres:15-alpine"))
                        .withDatabaseName("payment_test_db")
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
        private CardServiceClient cardServiceClient;

        @MockBean
        private WalletServiceClient walletServiceClient;

        @MockBean
        private IdentityServiceClient identityServiceClient;

        @MockBean
        private BankAdapter bankAdapter;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private PaymentRepository paymentRepository;

        private static final Long USER_ID = 1L;
        private static final Long CARD_ID = 10L;
        private static final String CARD_TOKEN = "tok_integration_test";

        @BeforeEach
        void setUp() {
                // JWT bypass
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(USER_ID, null,
                                Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void tearDown() {
                paymentRepository.deleteAll();
                SecurityContextHolder.clearContext();
        }

        @Test
        @Order(1)
        @DisplayName("Flyway migration → payments tablosu Testcontainers PostgreSQL'de oluşturulmalı")
        void shouldRunFlywayMigration_AndCreatePaymentsTable() {
                long count = paymentRepository.count();
                assertThat(count).isGreaterThanOrEqualTo(0);
        }

        @Test
        @Order(2)
        @DisplayName("POST /api/v1/payments (CARD) → 201 Created + veritabanına kayıt")
        void shouldCreatePayment_WithCardMethod_AndPersistToDatabase() throws Exception {
                // ARRANGE
                CardResponse card = new CardResponse(CARD_ID, "Enes K.", "5555xxxx8888", CARD_TOKEN);
                given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(card));
                given(bankAdapter.pay(CARD_TOKEN, new BigDecimal("250.00"))).willReturn(true);

                CreatePaymentRequest request = new CreatePaymentRequest(
                                100L, CARD_ID, new BigDecimal("250.00"), PaymentMethod.CARD);

                // ACT
                mockMvc.perform(post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                // ASSERT
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.amount").value(250.00));

                List<Payment> payments = paymentRepository.findAll();
                assertThat(payments).hasSize(1);

                Payment savedPayment = payments.get(0);
                assertThat(savedPayment.getUserId()).isEqualTo(USER_ID);
                assertThat(savedPayment.getCardId()).isEqualTo(CARD_ID);
                assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
                assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
        }

        @Test
        @Order(3)
        @DisplayName("POST /api/v1/payments (WALLET) → 201 Created + veritabanına kayıt")
        void shouldCreatePayment_WithWalletMethod_AndPersistToDatabase() throws Exception {
                // ARRANGE
                doNothing().when(walletServiceClient).withdrawFromWallet(any(), anyString());

                CreatePaymentRequest request = new CreatePaymentRequest(
                                101L, CARD_ID, new BigDecimal("100.00"), PaymentMethod.WALLET);

                // ACT
                mockMvc.perform(post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("SUCCESS"));

                // ASSERT
                List<Payment> payments = paymentRepository.findAll();
                assertThat(payments).hasSize(1);
                assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @Test
        @Order(4)
        @DisplayName("Banka ödemeyi reddederse → DB'de FAILED kaydı olmalı")
        void shouldPersistFailedPayment_WhenBankRejectsPayment() throws Exception {
                // ARRANGE
                CardResponse card = new CardResponse(CARD_ID, "Enes K.", "5555xxxx8888", CARD_TOKEN);
                given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(card));
                given(bankAdapter.pay(CARD_TOKEN, new BigDecimal("500.00"))).willReturn(false);

                CreatePaymentRequest request = new CreatePaymentRequest(
                                102L, CARD_ID, new BigDecimal("500.00"), PaymentMethod.CARD);

                // ACT
                mockMvc.perform(post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnprocessableEntity());

                // ASSERT
                List<Payment> payments = paymentRepository.findAll();
                assertThat(payments).hasSize(1);
                assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @Order(5)
        @DisplayName("POST /api/v1/payments/top-up → 201 Created + invoiceId null olmalı")
        void shouldCreateTopUpPayment_AndPersistWithNullInvoiceId() throws Exception {
                // ARRANGE
                CardResponse card = new CardResponse(CARD_ID, "Enes K.", "5555xxxx8888", CARD_TOKEN);
                given(cardServiceClient.getAllCardsByUserId()).willReturn(List.of(card));
                given(bankAdapter.pay(CARD_TOKEN, new BigDecimal("200.00"))).willReturn(true);

                TopUpPaymentRequest request = new TopUpPaymentRequest(CARD_ID, new BigDecimal("200.00"));

                // ACT
                mockMvc.perform(post("/api/v1/payments/top-up")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.invoiceId").isEmpty());

                // ASSERT
                List<Payment> payments = paymentRepository.findAll();
                assertThat(payments).hasSize(1);
                assertThat(payments.get(0).getInvoiceId()).isNull();
        }

        @Test
        @Order(6)
        @DisplayName("Kimlik doğrulaması olmadan istek → 403 Forbidden")
        void shouldReturn403_WhenNotAuthenticated() throws Exception {
                // Auth context'i temizle
                SecurityContextHolder.clearContext();

                CreatePaymentRequest request = new CreatePaymentRequest(
                                103L, CARD_ID, new BigDecimal("50.00"), PaymentMethod.CARD);

                mockMvc.perform(post("/api/v1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());

                assertThat(paymentRepository.count()).isZero();
        }
}
