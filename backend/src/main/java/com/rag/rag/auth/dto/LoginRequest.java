package com.rag.rag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Nieprawidłowy adres e-mail")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        String password
) {}
