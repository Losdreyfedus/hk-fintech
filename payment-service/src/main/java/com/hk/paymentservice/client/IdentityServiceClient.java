package com.hk.paymentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "identity-service", url = "${identity.service.url:http://localhost:9090}/api/v1/auth")
public interface IdentityServiceClient {
    @GetMapping("/validate")
    Long validateToken(@RequestParam("token") String token);
}