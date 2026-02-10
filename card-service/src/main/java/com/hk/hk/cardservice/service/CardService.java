package com.hk-fintech.hk.cardservice.service;

import com.hk-fintech.hk.cardservice.dto.request.CreateCardRequest;
import com.hk-fintech.hk.cardservice.dto.response.CardResponse;

import java.util.List;

public interface CardService {
    CardResponse createCard(CreateCardRequest request, Long userId);
    List<CardResponse> getAllCards(Long userId);

}