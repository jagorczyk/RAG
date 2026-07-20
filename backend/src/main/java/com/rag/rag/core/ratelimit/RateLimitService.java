package com.rag.rag.core.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Redis INCR + EXPIRE.
 * When Redis is unavailable and {@code fail-open} is true, requests are allowed.
 */
@Slf4j
@Service
public class RateLimitService {

    public static final String ACTION_CHAT_SEND = "chat-send";
    public static final String ACTION_UPLOAD = "upload";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    private final boolean enabled;
    private final boolean failOpen;
    private final int chatSendLimit;
    private final long chatSendWindowSeconds;
    private final int uploadLimit;
    private final long uploadWindowSeconds;

    public RateLimitService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.fail-open:true}") boolean failOpen,
            @Value("${app.rate-limit.chat-send.limit:30}") int chatSendLimit,
            @Value("${app.rate-limit.chat-send.window-seconds:60}") long chatSendWindowSeconds,
            @Value("${app.rate-limit.upload.limit:20}") int uploadLimit,
            @Value("${app.rate-limit.upload.window-seconds:60}") long uploadWindowSeconds
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.enabled = enabled;
        this.failOpen = failOpen;
        this.chatSendLimit = chatSendLimit;
        this.chatSendWindowSeconds = chatSendWindowSeconds;
        this.uploadLimit = uploadLimit;
        this.uploadWindowSeconds = uploadWindowSeconds;
    }

    /**
     * @return true if the request is allowed, false if the limit is exceeded
     */
    public boolean tryAcquire(String action, String principalKey) {
        if (!enabled) {
            return true;
        }
        LimitSpec spec = resolveSpec(action);
        if (spec == null || principalKey == null || principalKey.isBlank()) {
            return true;
        }

        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return allowWhenUnavailable("StringRedisTemplate missing");
        }

        String key = "ratelimit:" + action + ":" + principalKey;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return allowWhenUnavailable("INCR returned null");
            }
            if (count == 1L) {
                redis.expire(key, Duration.ofSeconds(spec.windowSeconds()));
            }
            return count <= spec.limit();
        } catch (Exception ex) {
            log.warn("Rate limit Redis error (failOpen={}): {}", failOpen, ex.getMessage());
            return allowWhenUnavailable(ex.getMessage());
        }
    }

    /**
     * Exposed for tests: direct window check against a pre-supplied counter backend.
     */
    public boolean isWithinLimit(long currentCount, int limit) {
        return currentCount <= limit;
    }

    public int limitFor(String action) {
        LimitSpec spec = resolveSpec(action);
        return spec == null ? Integer.MAX_VALUE : spec.limit();
    }

    public long windowSecondsFor(String action) {
        LimitSpec spec = resolveSpec(action);
        return spec == null ? 0L : spec.windowSeconds();
    }

    private boolean allowWhenUnavailable(String reason) {
        if (failOpen) {
            log.debug("Rate limit fail-open: {}", reason);
            return true;
        }
        return false;
    }

    private LimitSpec resolveSpec(String action) {
        if (ACTION_CHAT_SEND.equals(action)) {
            return new LimitSpec(chatSendLimit, chatSendWindowSeconds);
        }
        if (ACTION_UPLOAD.equals(action)) {
            return new LimitSpec(uploadLimit, uploadWindowSeconds);
        }
        return null;
    }

    private record LimitSpec(int limit, long windowSeconds) {}
}
