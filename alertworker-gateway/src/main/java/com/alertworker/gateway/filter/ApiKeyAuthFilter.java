package com.alertworker.gateway.filter;

import com.alertworker.common.entity.ApiKey;
import com.alertworker.common.mapper.ApiKeyMapper;
import com.alertworker.gateway.service.RateLimiterService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements Filter {

    // #AI-CODE-CLASS-dev_release/v0.0.1-20260804: Servlet API has no 429 constant, define locally
    private static final int SC_TOO_MANY_REQUESTS = 429;

    private final ApiKeyMapper apiKeyMapper;
    private final RateLimiterService rateLimiterService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260915: use servlet path (without context path) so the
        // /pw/alertworker/gw prefix added on 20260908 no longer bypasses API key validation
        String path = httpRequest.getServletPath();
        if (!path.startsWith("/api/v1/alerts")) {
            chain.doFilter(request, response);
            return;
        }

        String apiKeyValue = httpRequest.getHeader("X-API-Key");
        if (!StringUtils.hasText(apiKeyValue)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Missing X-API-Key header");
            return;
        }

        ApiKey apiKey = apiKeyMapper.selectOne(
                new LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getApiValue, apiKeyValue)
                        .eq(ApiKey::getDeleted, 0)
        );

        if (apiKey == null) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Invalid API key");
            return;
        }

        int limit = apiKey.getRateLimit() != null ? apiKey.getRateLimit() : 100;
        if (!rateLimiterService.allowRequest(apiKeyValue, limit)) {
            httpResponse.setStatus(SC_TOO_MANY_REQUESTS);
            httpResponse.getWriter().write("Rate limit exceeded");
            return;
        }

        httpRequest.setAttribute("apiKeyId", apiKey.getId());
        chain.doFilter(request, response);
    }
}
