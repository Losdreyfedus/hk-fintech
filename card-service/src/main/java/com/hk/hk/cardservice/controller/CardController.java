package com.hk-fintech.hk.cardservice.controller;

import com.hk-fintech.hk.cardservice.dto.request.CreateCardRequest;
import com.hk-fintech.hk.cardservice.dto.response.CardResponse;
import com.hk-fintech.hk.cardservice.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse createCard(@RequestBody @Valid CreateCardRequest request, @AuthenticationPrincipal Long userId){
        return cardService.createCard(request, userId);
    }
    @GetMapping("/user/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public List<CardResponse> getAllCards(@PathVariable Long userId) {
        return cardService.getAllCards(userId);
    }
}