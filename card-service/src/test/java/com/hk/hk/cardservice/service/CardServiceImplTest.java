package com.hk-fintech.hk.cardservice.service;

import com.hk-fintech.hk.cardservice.dto.request.CreateCardRequest;
import com.hk-fintech.hk.cardservice.dto.response.CardResponse;
import com.hk-fintech.hk.cardservice.entity.Card;
import com.hk-fintech.hk.cardservice.mapper.CardMapper;
import com.hk-fintech.hk.cardservice.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardServiceImpl Unit Tests")
class CardServiceImplTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private CardMapper cardMapper;

    @InjectMocks
    private CardServiceImpl cardService;

    private static final Long USER_ID = 1L;

    private CreateCardRequest validRequest;
    private Card testCard;
    private CardResponse testResponse;

    @BeforeEach
    void setUp() {
        validRequest = new CreateCardRequest(
                "Enes K.", "5555666677778888", "12", "2028", "123", "Kişisel Kartım");

        testCard = Card.builder()
                .id(1L)
                .userId(USER_ID)
                .cardHolder("Enes K.")
                .maskedCardNumber("555566******8888")
                .expireMonth("12")
                .expireYear("2028")
                .cardToken("uuid-token")
                .cardAlias("Kişisel Kartım")
                .isActive(true)
                .build();

        testResponse = new CardResponse(
                1L, "Enes K.", "555566******8888", "12", "2028", "Kişisel Kartım", "uuid-token");
    }

    @Nested
    @DisplayName("Kart Ekleme (createCard)")
    class CreateCardTests {

        @Test
        @DisplayName("Geçerli kart bilgileriyle → kart oluşturulmalı ve maskelenmiş numara kaydedilmeli")
        void shouldCreateCard_WhenRequestIsValid() {
            // ARRANGE
            given(cardRepository.existsByUserIdAndMaskedCardNumber(eq(USER_ID), anyString())).willReturn(false);
            given(cardMapper.toEntity(validRequest)).willReturn(new Card());
            given(cardRepository.save(any(Card.class))).willAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            given(cardMapper.toResponse(any(Card.class))).willReturn(testResponse);

            // ACT
            CardResponse response = cardService.createCard(validRequest, USER_ID);

            // ASSERT
            assertThat(response).isNotNull();
            assertThat(response.maskedCardNumber()).isEqualTo("555566******8888");

            ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
            verify(cardRepository).save(captor.capture());

            Card savedCard = captor.getValue();
            assertThat(savedCard.getUserId()).isEqualTo(USER_ID);
            assertThat(savedCard.getMaskedCardNumber()).isEqualTo("555566******8888");
            assertThat(savedCard.getCardToken()).isNotNull();
        }

        @Test
        @DisplayName("Aynı kart daha önce eklenmişse → RuntimeException fırlatılmalı")
        void shouldThrowException_WhenDuplicateCardExists() {
            // ARRANGE
            given(cardRepository.existsByUserIdAndMaskedCardNumber(eq(USER_ID), anyString())).willReturn(true);

            // ACT & ASSERT
            assertThatThrownBy(() -> cardService.createCard(validRequest, USER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("zaten");

            verify(cardRepository, never()).save(any());
        }

        @Test
        @DisplayName("Süresi geçmiş kart → RuntimeException fırlatılmalı")
        void shouldThrowException_WhenCardIsExpired() {
            // ARRANGE
            CreateCardRequest expiredCard = new CreateCardRequest(
                    "Enes K.", "5555666677778888", "01", "2020", "123", "Eski Kart");

            // ACT & ASSERT
            assertThatThrownBy(() -> cardService.createCard(expiredCard, USER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("süresi dolmuş");

            verify(cardRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Kart Listeleme (getAllCards)")
    class GetAllCardsTests {

        @Test
        @DisplayName("Kullanıcının aktif kartları varsa → liste dönmeli")
        void shouldReturnCardList_WhenUserHasActiveCards() {
            // ARRANGE
            given(cardRepository.findAllByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of(testCard));
            given(cardMapper.toResponse(testCard)).willReturn(testResponse);

            // ACT
            List<CardResponse> result = cardService.getAllCards(USER_ID);

            // ASSERT
            assertThat(result).hasSize(1);
            assertThat(result.get(0).cardHolder()).isEqualTo("Enes K.");
        }

        @Test
        @DisplayName("Kullanıcının kartı yoksa → boş liste dönmeli")
        void shouldReturnEmptyList_WhenUserHasNoCards() {
            // ARRANGE
            given(cardRepository.findAllByUserIdAndIsActiveTrue(USER_ID)).willReturn(List.of());

            // ACT
            List<CardResponse> result = cardService.getAllCards(USER_ID);

            // ASSERT
            assertThat(result).isEmpty();
        }
    }
}
