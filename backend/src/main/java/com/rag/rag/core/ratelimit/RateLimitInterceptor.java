package com.rag.rag.core.ratelimit;

import com.rag.rag.auth.security.CurrentUserService;
import com.rag.rag.core.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Applies Redis rate limits to chat send and file upload endpoints.
 * Throws {@link ApiException} 429 when the limit is exceeded (handled by RestExceptionHandler).
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Pattern CHAT_SEND = Pattern.compile("^/api/chat/[^/]+/send$");
    private static final Pattern FOLDER_UPLOAD = Pattern.compile("^/api/folders/[^/]+/upload$");

    private final RateLimitService rateLimitService;
    private final CurrentUserService currentUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = normalizePath(request);
        String action = resolveAction(path);
        if (action == null) {
            return true;
        }

        String principalKey = resolvePrincipalKey(request);
        if (!rateLimitService.tryAcquire(action, principalKey)) {
            throw ApiException.tooManyRequests(
                    "RATE_LIMIT_EXCEEDED",
                    "Przekroczono limit żądań. Spróbuj ponownie za chwilę."
            );
        }
        return true;
    }

    /**
     * Visible for unit tests — maps request path to rate-limit action name.
     */
    public String resolveAction(String path) {
        if (path == null) {
            return null;
        }
        if (CHAT_SEND.matcher(path).matches()) {
            return RateLimitService.ACTION_CHAT_SEND;
        }
        if (FOLDER_UPLOAD.matcher(path).matches()) {
            return RateLimitService.ACTION_UPLOAD;
        }
        return null;
    }

    private String resolvePrincipalKey(HttpServletRequest request) {
        Optional<UUID> userId = currentUserService.findUserId();
        if (userId.isPresent()) {
            return "user:" + userId.get();
        }
        String ip = request.getRemoteAddr();
        return "ip:" + (ip == null || ip.isBlank() ? "unknown" : ip);
    }

    private static String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }
}
