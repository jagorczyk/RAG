package com.rag.rag.core.ratelimit;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private RateLimitService rateLimitService;
    private CurrentUserService currentUserService;
    private RateLimitInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        rateLimitService = mock(RateLimitService.class);
        currentUserService = mock(CurrentUserService.class);
        interceptor = new RateLimitInterceptor(rateLimitService, currentUserService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(currentUserService.findUserId()).thenReturn(Optional.of(userId));
        when(request.getContextPath()).thenReturn("");
    }

    @Test
    void resolveActionMapsChatSendAndUpload() {
        assertEquals(RateLimitService.ACTION_CHAT_SEND, interceptor.resolveAction("/api/chat/abc/send"));
        assertEquals(RateLimitService.ACTION_UPLOAD, interceptor.resolveAction("/api/folders/xyz/upload"));
        assertNull(interceptor.resolveAction("/api/chat/all"));
        assertNull(interceptor.resolveAction("/api/folders"));
    }

    @Test
    void allowsWhenUnderLimit() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/chat/" + UUID.randomUUID() + "/send");
        when(rateLimitService.tryAcquire(eq(RateLimitService.ACTION_CHAT_SEND), eq("user:" + userId)))
                .thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void throws429WhenLimitExceeded() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/folders/" + UUID.randomUUID() + "/upload");
        when(rateLimitService.tryAcquire(eq(RateLimitService.ACTION_UPLOAD), eq("user:" + userId)))
                .thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> interceptor.preHandle(request, response, new Object()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("RATE_LIMIT_EXCEEDED", ex.getCode());
        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank());
    }

    @Test
    void skipsNonPost() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/chat/x/send");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(rateLimitService, never()).tryAcquire(anyString(), anyString());
    }
}
