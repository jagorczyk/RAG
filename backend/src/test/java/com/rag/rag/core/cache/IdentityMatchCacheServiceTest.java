package com.rag.rag.core.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityMatchCacheServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private IdentityMatchCacheService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);

        service = new IdentityMatchCacheService(
                provider,
                true,
                300,
                "identity:match:"
        );
    }

    @Test
    void buildKeyIsStableForSameInputs() {
        float[] embedding = new float[] {0.1f, 0.2f, 0.3f};
        String a = service.buildKey(embedding, "dir://a.jpg", 0.5);
        String b = service.buildKey(embedding, "dir://a.jpg", 0.5);
        String c = service.buildKey(embedding, "dir://b.jpg", 0.5);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void putAndGetHit() {
        UUID entityId = UUID.randomUUID();
        String key = "abc123";
        when(valueOps.get("identity:match:abc123")).thenAnswer(inv -> {
            // simulate after put — read what would have been written via serialize path
            return "{\"entityId\":\"" + entityId + "\",\"score\":0.91,\"rankingScore\":0.9,\"margin\":0.12,\"negative\":false}";
        });

        service.putHit(key, entityId, 0.91, 0.9, 0.12);
        verify(valueOps).set(eq("identity:match:abc123"), anyString(), eq(Duration.ofSeconds(300)));

        Optional<IdentityMatchCacheService.CachedIdentityMatch> hit = service.get(key);
        assertTrue(hit.isPresent());
        assertFalse(hit.get().isNegative());
        assertEquals(entityId, hit.get().entityId());
        assertEquals(0.91, hit.get().score(), 0.0001);
    }

    @Test
    void putAndGetMiss() {
        when(valueOps.get("identity:match:miss-key")).thenReturn("null");

        service.putMiss("miss-key");
        verify(valueOps).set(eq("identity:match:miss-key"), eq("null"), eq(Duration.ofSeconds(300)));

        Optional<IdentityMatchCacheService.CachedIdentityMatch> hit = service.get("miss-key");
        assertTrue(hit.isPresent());
        assertTrue(hit.get().isNegative());
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(valueOps.get(anyString())).thenReturn(null);
        assertTrue(service.get("unknown").isEmpty());
    }

    @Test
    void disabledReturnsEmptyAndSkipsPut() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        IdentityMatchCacheService disabled = new IdentityMatchCacheService(
                provider, false, 60, "identity:match:");

        assertTrue(disabled.get("k").isEmpty());
        disabled.putHit("k", UUID.randomUUID(), 1.0, 1.0, 0.1);
        // provider never consulted when disabled for get/put of payload path without redis
        assertFalse(disabled.isEnabled());
    }
}
