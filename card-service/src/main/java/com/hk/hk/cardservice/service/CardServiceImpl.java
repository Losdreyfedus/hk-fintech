package com.hk-fintech.hk.cardservice.service;

import com.hk-fintech.hk.cardservice.adapter.IdentityAdapter; // Yeni import
import com.hk-fintech.hk.cardservice.dto.request.CreateCardRequest;
import com.hk-fintech.hk.cardservice.dto.response.CardResponse;
import com.hk-fintech.hk.cardservice.entity.Card;
import com.hk-fintech.hk.cardservice.mapper.CardMapper;
import com.hk-fintech.hk.cardservice.repository.CardRepository;
import com.hk-fintech.hk.cardservice.util.CardFormatter;
import com.hk-fintech.hk.cardservice.util.CardRuleValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final CardMapper cardMapper;
    private final IdentityAdapter identityAdapter;

    @Override
    public CardResponse createCard(CreateCardRequest request, Long userId) {
        log.info("Kart ekleme isteği alındı. User ID: {}", userId);
        identityAdapter.checkUserExists(userId);

        CardRuleValidator.validateExpiry(request.expireMonth(), request.expireYear());

        String maskedPan = CardFormatter.maskCardNumber(request.cardNumber());

        if (cardRepository.existsByUserIdAndMaskedCardNumber(userId, maskedPan)) {
            log.warn("Mükerrer kart: {}", maskedPan);
            throw new RuntimeException("Bu kart zaten cüzdanınızda ekli!");
        }

        String fakeBankToken = UUID.randomUUID().toString();

        Card card = cardMapper.toEntity(request);
        card.setUserId(userId);
        card.setMaskedCardNumber(maskedPan);
        card.setCardToken(fakeBankToken);

        cardRepository.save(card);
        log.info("Kart kaydedildi. ID: {}", card.getId());

        return cardMapper.toResponse(card);
    }

    @Override
    public List<CardResponse> getAllCards(Long userId) {


        return cardRepository.findAllByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(cardMapper::toResponse)
                .toList();
    }
}