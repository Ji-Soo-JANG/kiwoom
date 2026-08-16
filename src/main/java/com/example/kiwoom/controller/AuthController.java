package com.example.kiwoom.controller;

import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @GetMapping("/me")
    public Mono<Map<String, String>> me(Principal principal) {
        return Mono.just(Map.of("username", principal.getName()));
    }
}
