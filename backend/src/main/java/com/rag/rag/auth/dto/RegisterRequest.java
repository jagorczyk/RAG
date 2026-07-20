package com.rag.rag.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Nieprawidłowy adres e-mail")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        @Size(min = 8, max = 100, message = "Hasło musi mieć co najmniej 8 znaków")
        String password,

        @Size(max = 255)
        String displayName
) {}
