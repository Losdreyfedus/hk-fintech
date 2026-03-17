package com.hk.identityservice.controller;

import com.hk.identityservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users") // CardService buraya istek atıyor
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping("/{id}/exists")
    public boolean existsById(@PathVariable Long id) {
        return authService.existsById(id);
    }
}