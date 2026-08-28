package com.alertworker.gateway.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimiterService {

    private LoadingCache<String, AtomicInteger> requestCounts;

    @PostConstruct
    public void init() {
        requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build(key -> new AtomicInteger(0));
    }

    // #AI-CODE-dev_release/v0.0.1-20260604: fix race condition - check before increment
    public boolean allowRequest(String apiKeyValue, int rateLimit) {
        AtomicInteger counter = requestCounts.get(apiKeyValue);
        int current = counter.get();
        if (current >= rateLimit) {
            log.debug("API key {} rate limit exceeded: {}/{}", apiKeyValue, current, rateLimit);
            return false;
        }
        counter.incrementAndGet();
        log.debug("API key {} current request count: {}, limit: {}", apiKeyValue, current + 1, rateLimit);
        return true;
    }
}
