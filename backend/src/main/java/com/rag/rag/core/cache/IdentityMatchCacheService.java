package com.rag.rag.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis cache for expensive face identity match results (entity id + scores).
 * Disabled or fail-open when Redis is unavailable so ingest continues.
 */
@Slf4j
@Service
public class IdentityMatchCacheService {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enabled;
    private final long ttlSeconds;
    private final String keyPrefix;

    public IdentityMatchCacheService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.identity-cache.enabled:true}") boolean enabled,
            @Value("${app.identity-cache.ttl-seconds:300}") long ttlSeconds,
            @Value("${app.identity-cache.key-prefix:identity:match:}") String keyPrefix
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.enabled = enabled;
        this.ttlSeconds = Math.max(1L, ttlSeconds);
        this.keyPrefix = keyPrefix == null ? "identity:match:" : keyPrefix;
    }

    public Optional<CachedIdentityMatch> get(String cacheKey) {
        if (!enabled || cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(keyPrefix + cacheKey);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            if ("null".equals(json)) {
                return Optional.of(CachedIdentityMatch.notFound());
            }
            return Optional.of(objectMapper.readValue(json, CachedIdentityMatch.class));
        } catch (Exception ex) {
            log.debug("Identity cache get failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(String cacheKey, CachedIdentityMatch value) {
        if (!enabled || cacheKey == null || cacheKey.isBlank()) {
            return;
        }
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            String payload = value == null || value.isNegative()
                    ? "null"
                    : objectMapper.writeValueAsString(value);
            redis.opsForValue().set(keyPrefix + cacheKey, payload, Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException ex) {
            log.debug("Identity cache serialize failed: {}", ex.getMessage());
        } catch (Exception ex) {
            log.debug("Identity cache put failed: {}", ex.getMessage());
        }
    }

    public void putHit(String cacheKey, UUID entityId, double score, double rankingScore, double margin) {
        put(cacheKey, new CachedIdentityMatch(entityId, score, rankingScore, margin, false));
    }

    public void putMiss(String cacheKey) {
        put(cacheKey, CachedIdentityMatch.notFound());
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Stable cache key from match inputs (embedding bytes + exclude path + threshold).
     */
    public String buildKey(float[] queryEmbedding, String excludeFilePath, double threshold) {
        return buildKey(queryEmbedding, excludeFilePath, threshold, null);
    }

    /**
     * Stable cache key including gallery owner so cross-user results never share a cache entry.
     */
    public String buildKey(float[] queryEmbedding, String excludeFilePath, double threshold, UUID ownerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (queryEmbedding != null) {
                ByteBuffer buffer = ByteBuffer.allocate(queryEmbedding.length * 4);
                for (float v : queryEmbedding) {
                    buffer.putFloat(v);
                }
                digest.update(buffer.array());
            }
            String pathPart = excludeFilePath == null ? "" : excludeFilePath;
            digest.update(pathPart.getBytes(StandardCharsets.UTF_8));
            digest.update(Double.toString(threshold).getBytes(StandardCharsets.UTF_8));
            String ownerPart = ownerId == null ? "null" : ownerId.toString();
            digest.update(ownerPart.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always present on the JVM
            return Integer.toHexString(java.util.Arrays.hashCode(queryEmbedding))
                    + ":" + (excludeFilePath == null ? "" : excludeFilePath)
                    + ":" + threshold
                    + ":" + (ownerId == null ? "null" : ownerId);
        }
    }

    public record CachedIdentityMatch(
            UUID entityId,
            double score,
            double rankingScore,
            double margin,
            boolean negative
    ) {
        public static CachedIdentityMatch notFound() {
            return new CachedIdentityMatch(null, 0, 0, 0, true);
        }

        public boolean isNegative() {
            return negative || entityId == null;
        }
    }
}
