package com.finsight_backend.controller;

import com.finsight_backend.dto.LoginRequestDTO;
import com.finsight_backend.dto.LoginResponseDTO;
import com.finsight_backend.entity.User;
import com.finsight_backend.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Register
    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return authService.register(user);
    }

    // Login
    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO request) {

        String token = authService.login(
                request.getEmail(),
                request.getPassword()
        );

        return new LoginResponseDTO(token);
    }
}