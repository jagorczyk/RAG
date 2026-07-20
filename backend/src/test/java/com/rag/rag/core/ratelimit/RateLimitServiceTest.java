package com.rag.rag.core.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);

        service = new RateLimitService(
                provider,
                true,
                true,
                3,
                60,
                2,
                60
        );
    }

    @Test
    void allowsRequestsUnderLimit() {
        when(valueOps.increment("ratelimit:chat-send:user:1")).thenReturn(1L, 2L, 3L);

        assertTrue(service.tryAcquire(RateLimitService.ACTION_CHAT_SEND, "user:1"));
        assertTrue(service.tryAcquire(RateLimitService.ACTION_CHAT_SEND, "user:1"));
        assertTrue(service.tryAcquire(RateLimitService.ACTION_CHAT_SEND, "user:1"));

        verify(redis).expire(eq("ratelimit:chat-send:user:1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    void rejectsWhenLimitExceeded() {
        when(valueOps.increment("ratelimit:chat-send:user:1")).thenReturn(4L);

        assertFalse(service.tryAcquire(RateLimitService.ACTION_CHAT_SEND, "user:1"));
    }

    @Test
    void uploadUsesSeparateLimit() {
        when(valueOps.increment("ratelimit:upload:user:9")).thenReturn(2L, 3L);

        assertTrue(service.tryAcquire(RateLimitService.ACTION_UPLOAD, "user:9"));
        assertFalse(service.tryAcquire(RateLimitService.ACTION_UPLOAD, "user:9"));
    }

    @Test
    void failOpenWhenRedisThrows() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));

        assertTrue(service.tryAcquire(RateLimitService.ACTION_CHAT_SEND, "user:1"));
    }

    @Test
    void disabledAlwaysAllowsWithoutRedisCall() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        RateLimitService disabled = new RateLimitService(provider, false, true, 1, 60, 1, 60);

        assertTrue(disabled.tryAcquire(RateLimitService.ACTION_CHAT_SEND, "user:1"));
        verify(provider, never()).getIfAvailable();
    }

    @Test
    void isWithinLimitHelper() {
        assertTrue(service.isWithinLimit(3, 3));
        assertFalse(service.isWithinLimit(4, 3));
    }
}
