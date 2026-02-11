package com.hk-fintech.hk.paymentservice.client;

import com.hk-fintech.hk.paymentservice.dto.client.CardResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@FeignClient(name = "card-service", url = "http://localhost:9092/api/v1/cards")
public interface CardServiceClient {

    @GetMapping("/user/{userId}")
    List<CardResponse> getAllCardsByUserId(@PathVariable Long userId);
}