package com.hk-fintech.hk.invoiceservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk-fintech.hk.invoiceservice.client.IdentityClient;
import com.hk-fintech.hk.invoiceservice.client.PaymentClient;
import com.hk-fintech.hk.invoiceservice.dto.request.CreateInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.request.PayInvoiceRequest;
import com.hk-fintech.hk.invoiceservice.dto.request.PaymentMethod;
import com.hk-fintech.hk.invoiceservice.dto.response.PaymentResponse;
import com.hk-fintech.hk.invoiceservice.entity.BillType;
import com.hk-fintech.hk.invoiceservice.entity.Invoice;
import com.hk-fintech.hk.invoiceservice.entity.InvoiceStatus;
import com.hk-fintech.hk.invoiceservice.repository.InvoiceRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("Invoice Service Integration Tests")
class InvoiceServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("invoice_test_db")
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
    private InvoiceRepository invoiceRepository;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        given(identityClient.validateToken(anyString())).willReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        invoiceRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/invoices → Fatura oluşturmalı ve 201 dönmeli")
    void shouldCreateInvoice() throws Exception {
        // ARRANGE
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                USER_ID,
                BillType.ELECTRICITY,
                "BEDAŞ",
                "123456789",
                "Mayıs 2024 Elektrik Faturası",
                new BigDecimal("450.50"),
                LocalDate.now().plusDays(10)
        );

        // ACT
        mockMvc.perform(post("/api/v1/invoices")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.institutionName").value("BEDAŞ"))
                .andExpect(jsonPath("$.amount").value(450.50))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // ASSERT
        List<Invoice> invoices = invoiceRepository.findAll();
        assertThat(invoices).hasSize(1);
        assertThat(invoices.get(0).getInstitutionName()).isEqualTo("BEDAŞ");
        assertThat(invoices.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("450.50"));
    }

    @Test
    @DisplayName("GET /api/v1/invoices → Kullanıcının faturalarını listelemeli")
    void shouldReturnMyInvoices() throws Exception {
        // ARRANGE
        Invoice invoice = Invoice.builder()
                .userId(USER_ID)
                .billType(BillType.WATER)
                .institutionName("İSKİ")
                .accountNumber("987654321")
                .description("Su Faturası")
                .amount(new BigDecimal("120.75"))
                .dueDate(LocalDate.now().plusDays(5))
                .status(InvoiceStatus.PENDING)
                .build();
        invoiceRepository.save(invoice);

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/invoices")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].institutionName").value("İSKİ"))
                .andExpect(jsonPath("$[-0].amount").value(120.75));
    }

    @Test
    @DisplayName("POST /api/v1/invoices/{id}/pay → Faturayı ödenmiş yapmalı (Cüzdan ile)")
    void shouldPayInvoiceWithWallet() throws Exception {
        // ARRANGE
        Invoice invoice = Invoice.builder()
                .userId(USER_ID)
                .billType(BillType.INTERNET)
                .institutionName("Superonline")
                .accountNumber("555444333")
                .description("İnternet Faturası")
                .amount(new BigDecimal("299.90"))
                .dueDate(LocalDate.now().plusDays(3))
                .status(InvoiceStatus.PENDING)
                .build();
        invoice = invoiceRepository.save(invoice);

        // Mock payment response as success
        PaymentResponse mockResponse = new PaymentResponse(1L, invoice.getId(), invoice.getAmount(), "SUCCESS", LocalDateTime.now());
        given(paymentClient.processPayment(any())).willReturn(mockResponse);

        PayInvoiceRequest request = new PayInvoiceRequest(null, PaymentMethod.WALLET);

        // ACT
        mockMvc.perform(post("/api/v1/invoices/" + invoice.getId() + "/pay")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // ASSERT
        Invoice savedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(savedInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }
}
