package com.hk.cardservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hk.cardservice.client.IdentityServiceClient;
import com.hk.cardservice.dto.request.CreateCardRequest;
import com.hk.cardservice.entity.Card;
import com.hk.cardservice.repository.CardRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
@DisplayName("Card Service Integration Tests")
class CardServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("card_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean
    private IdentityServiceClient identityServiceClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CardRepository cardRepository;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        given(identityServiceClient.validateToken(anyString())).willReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        cardRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/cards → Kart oluşturmalı ve 201 dönmeli")
    void shouldCreateCard() throws Exception {
        // ARRANGE
        CreateCardRequest request = new CreateCardRequest(
                "Enes",
                "4242424242424242",
                "12",
                "2030",
                "123",
                "HK-FINTECH"
        );

        // ACT
        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maskedCardNumber").value("424242******4242"))
                .andExpect(jsonPath("$.cardHolder").value("Enes"));

        // ASSERT
        List<Card> cards = cardRepository.findAll();
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getUserId()).isEqualTo(USER_ID);
        assertThat(cards.get(0).getMaskedCardNumber()).isEqualTo("424242******4242");
    }

    @Test
    @DisplayName("GET /api/v1/cards → Kullanıcının kartlarını listelemeli")
    void shouldReturnMyCards() throws Exception {
        // ARRANGE
        Card card = new Card();
        card.setUserId(USER_ID);
        card.setMaskedCardNumber("9876********4321");
        card.setCardHolder("Enes K");
        card.setCardToken("fake-token-abcd");
        card.setExpireMonth("12");
        card.setExpireYear("2030");
        card.setIsActive(true);
        cardRepository.save(card);

        // ACT & ASSERT
        mockMvc.perform(get("/api/v1/cards")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].maskedCardNumber").value("9876********4321"));
    }
}
