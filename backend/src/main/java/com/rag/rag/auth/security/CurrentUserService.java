package com.rag.rag.auth.security;

import com.rag.rag.core.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CurrentUserService {

    public UserPrincipal requirePrincipal() {
        return findPrincipal()
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Wymagane zalogowanie."));
    }

    public UUID requireUserId() {
        return requirePrincipal().getId();
    }

    public Optional<UserPrincipal> findPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return Optional.of(userPrincipal);
        }
        return Optional.empty();
    }

    public Optional<UUID> findUserId() {
        return findPrincipal().map(UserPrincipal::getId);
    }
}
