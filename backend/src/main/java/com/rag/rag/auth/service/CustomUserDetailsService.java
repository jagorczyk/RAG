package com.rag.rag.auth.service;

import com.rag.rag.auth.repository.UserRepository;
import com.rag.rag.auth.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(username)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Użytkownik nie istnieje: " + username));
    }
}
