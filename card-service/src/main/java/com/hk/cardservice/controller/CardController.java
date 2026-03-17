package com.hk.cardservice.controller;

import com.hk.cardservice.dto.request.CreateCardRequest;
import com.hk.cardservice.dto.response.CardResponse;
import com.hk.cardservice.service.CardService;
import com.hk.cardservice.service.RateLimitService;
import com.hk.cardservice.service.RateLimitType;
import io.github.bucket4j.Bucket;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final RateLimitService rateLimitService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse createCard(
            @RequestBody @Valid CreateCardRequest request,
            @AuthenticationPrincipal Long userId
    ) {
        Bucket bucket = rateLimitService.resolveBucket(RateLimitType.CARD_CREATE, userId);

        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Kart ekleme sınırını aştınız! Lütfen bekleyin.");
        }

        return cardService.createCard(request, userId);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<CardResponse> getAllCards(@AuthenticationPrincipal Long userId) {
        Bucket bucket = rateLimitService.resolveBucket(RateLimitType.CARD_LIST, userId);
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Kart Listeleme sınırını aştınız! Lütfen bekleyin.");
        }

        return cardService.getAllCards(userId);
    }
}