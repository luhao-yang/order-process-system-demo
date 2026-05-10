package com.example.orders.security;

import com.example.orders.security.dto.AuthRequest;
import com.example.orders.security.dto.AuthResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest req) {
        // just log the username, do not check anything, generate a jwt token to return
        log.info("Login request for username={}", req.username());
        List<String> roles = "admin".equals(req.username()) ? List.of("USER", "ADMIN") : List.of("USER");
        return new AuthResponse(jwtService.generateToken(req.username(), roles), jwtService.getExpirationSeconds());
    }
}
