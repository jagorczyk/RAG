package com.rag.rag.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("change-me-to-a-long-secret-at-least-32-chars", 60);
        principal = new UserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "user@example.com",
                "hash",
                "User"
        );
    }

    @Test
    void generateAndParseRoundTrip() {
        String token = jwtService.generateToken(principal);

        assertEquals("user@example.com", jwtService.extractEmail(token));
        assertEquals(principal.getId(), jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, principal));
    }

    @Test
    void invalidTokenFailsValidation() {
        assertThrows(Exception.class, () -> jwtService.parseClaims("not-a-jwt"));
    }

    @Test
    void shortSecretRejected() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 60));
    }

    @Test
    void tokenInvalidForOtherUser() {
        String token = jwtService.generateToken(principal);
        UserPrincipal other = new UserPrincipal(
                UUID.randomUUID(),
                "other@example.com",
                "hash",
                "Other"
        );
        assertFalse(jwtService.isTokenValid(token, other));
    }
}
