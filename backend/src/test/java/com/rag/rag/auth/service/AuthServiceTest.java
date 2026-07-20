package com.rag.rag.auth.service;

import com.rag.rag.auth.dto.RegisterRequest;
import com.rag.rag.auth.entity.UserEntity;
import com.rag.rag.auth.repository.UserRepository;
import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.auth.security.JwtService;
import com.rag.rag.auth.security.UserPrincipal;
import com.rag.rag.core.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;
    private CurrentUserService currentUserService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("change-me-to-a-long-secret-at-least-32-chars", 60);
        authenticationManager = mock(AuthenticationManager.class);
        currentUserService = mock(CurrentUserService.class);
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                authenticationManager,
                currentUserService
        );
    }

    @Test
    void registerHashesPasswordAndReturnsToken() {
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            return user;
        });

        var response = authService.register(new RegisterRequest(
                "alice@example.com",
                "password123",
                "Alice"
        ));

        assertNotNull(response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("alice@example.com", response.user().email());
        assertEquals("Alice", response.user().displayName());
        assertNotNull(response.user().id());
    }

    @Test
    void registerConflictOnExistingEmail() {
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                authService.register(new RegisterRequest("alice@example.com", "password123", "Alice")));

        assertEquals("EMAIL_TAKEN", ex.getCode());
    }

    @Test
    void passwordEncoderUsesBcrypt() {
        String hash = passwordEncoder.encode("password123");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
        assertTrue(passwordEncoder.matches("password123", hash));
    }

    @Test
    void meReturnsCurrentUser() {
        UUID id = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(id)
                .email("bob@example.com")
                .passwordHash("hash")
                .displayName("Bob")
                .build();
        when(currentUserService.requirePrincipal()).thenReturn(UserPrincipal.from(user));
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        var me = authService.me();
        assertEquals("bob@example.com", me.email());
        assertEquals("Bob", me.displayName());
    }
}
