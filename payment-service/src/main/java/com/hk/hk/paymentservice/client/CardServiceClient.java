package com.hk-fintech.hk.paymentservice.client;

import com.hk-fintech.hk.paymentservice.dto.client.CardResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@FeignClient(name = "card-service", url = "${card.service.url:http://localhost:9092}/api/v1/cards")
public interface CardServiceClient {

    @GetMapping
    List<CardResponse> getAllCardsByUserId();
}