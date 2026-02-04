package com.hk-fintech.hk.cardservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
@FeignClient(name = "identity-service", url = "http://localhost:9090")
public interface IdentityClient {
    @GetMapping("/api/v1/users/{id}/exists")
    boolean existsById(@PathVariable Long id);
}