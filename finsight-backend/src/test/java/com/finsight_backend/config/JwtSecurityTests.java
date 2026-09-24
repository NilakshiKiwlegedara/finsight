package com.finsight_backend.config;

import com.finsight_backend.controller.AuthController;
import com.finsight_backend.entity.User;
import com.finsight_backend.repository.UserRepository;
import com.finsight_backend.service.AuthService;
import com.finsight_backend.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(JwtSecurityTests.TestConfig.class)
@WebAppConfiguration
class JwtSecurityTests {
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(users);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @Test
    void validTokenAuthenticatesEmailWithoutCreatingSession() throws Exception {
        when(users.existsByEmail("user@example.test")).thenReturn(true);
        var result = mvc.perform(get("/api/protected")
                        .header("Authorization", "Bearer " + jwt.generateToken("user@example.test")))
                .andExpect(status().isOk()).andExpect(content().string("user@example.test"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        // A subsequent request cannot reuse an earlier request's authentication.
        mvc.perform(get("/api/protected")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingMalformedAndEmptyCredentialsAreUnauthorized() throws Exception {
        mvc.perform(get("/api/protected")).andExpect(status().isUnauthorized());
        for (String header : new String[]{"Bearer garbage", "Bearer ", "Basic abc"}) {
            mvc.perform(get("/api/protected").header("Authorization", header))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users);
    }

    @Test
    void expiredAndWronglySignedTokensAreUnauthorized() throws Exception {
        String expired = Jwts.builder().subject("user@example.test")
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        String forged = new JwtService(UUID.randomUUID().toString() + UUID.randomUUID())
                .generateToken("user@example.test");
        for (String token : new String[]{expired, forged}) {
            mvc.perform(get("/api/protected").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users);
    }

    @Test
    void deletedUserAndEmptySubjectAreUnauthorized() throws Exception {
        for (String email : new String[]{"deleted@example.test", " "}) {
            mvc.perform(get("/api/protected")
                            .header("Authorization", "Bearer " + jwt.generateToken(email)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void publicLoginStillReturnsValidJwtEvenWithStaleBearerHeader() throws Exception {
        User user = new User();
        user.setEmail("user@example.test");
        user.setPassword(encoder.encode("test-password"));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .header("Authorization", "Bearer invalid")
                        .content("{\"email\":\"user@example.test\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        assertTrue(jwt.isTokenValid(token));
        assertEquals(user.getEmail(), jwt.extractEmail(token));
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void publicRegistrationStillHashesPasswordAndHidesItInResponse() throws Exception {
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertTrue(encoder.matches("test-password", saved.getPassword()));
            return saved;
        });
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"name\":\"Test\",\"email\":\"user@example.test\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        verify(users).save(any(User.class));
    }

    @Test
    void weakSigningKeyIsRejected() {
        assertThrows(io.jsonwebtoken.security.WeakKeyException.class, () -> new JwtService("short"));
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class, AuthService.class, ProbeController.class})
    static class TestConfig {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JwtService jwt() { return new JwtService(SECRET); }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/protected")
        String protectedEndpoint(Principal principal) { return principal.getName(); }
    }
}
