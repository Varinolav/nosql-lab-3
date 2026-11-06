package ru.nosql.lab2.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

@Component
public class RedisRateLimitInterceptor implements HandlerInterceptor {

    private static final int LIMIT = 60;
    private static final int WINDOW_SECONDS = 60;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>();

    static {
        INCREMENT_SCRIPT.setScriptText("""
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return count
                """);
        INCREMENT_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String clientIp = request.getHeader("X-Real-IP");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        }
        String key = "rate_limit:ip:" + clientIp;
        Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), Integer.toString(WINDOW_SECONDS));
        if (count == null) {
            throw new IllegalStateException("Redis не вернул значение счётчика запросов");
        }
        if (count <= LIMIT) {
            return true;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", Integer.toString(WINDOW_SECONDS));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\"}");
        return false;
    }
}
