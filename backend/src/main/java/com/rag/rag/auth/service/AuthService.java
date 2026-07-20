package com.rag.rag.auth.service;

import com.rag.rag.auth.dto.AuthResponse;
import com.rag.rag.auth.dto.LoginRequest;
import com.rag.rag.auth.dto.RegisterRequest;
import com.rag.rag.auth.dto.UserResponse;
import com.rag.rag.auth.entity.UserEntity;
import com.rag.rag.auth.repository.UserRepository;
import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.auth.security.JwtService;
import com.rag.rag.auth.security.UserPrincipal;
import com.rag.rag.core.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CurrentUserService currentUserService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Konto z tym adresem e-mail już istnieje.");
        }

        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? email
                : request.displayName().trim();

        UserEntity user = UserEntity.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(displayName)
                .build();
        userRepository.save(user);

        UserPrincipal principal = UserPrincipal.from(user);
        String token = jwtService.generateToken(principal);
        return AuthResponse.of(token, toUserResponse(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Nieprawidłowe dane logowania."));
        UserPrincipal principal = UserPrincipal.from(user);
        String token = jwtService.generateToken(principal);
        return AuthResponse.of(token, toUserResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        UserPrincipal principal = currentUserService.requirePrincipal();
        UserEntity user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Użytkownik nie istnieje."));
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(UserEntity user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
