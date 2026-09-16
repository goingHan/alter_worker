package com.alertworker.datamanager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Token";

    @Value("${alertworker.datamanager.admin-token:}")
    private String adminToken;

    private final TokenUtil tokenUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = request.getHeader(HEADER);
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        boolean staticTokenOk = adminToken != null && !adminToken.isBlank() && adminToken.equals(token);
        boolean signedTokenOk = tokenUtil.verifyToken(token) != null;
        if (staticTokenOk || signedTokenOk) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing admin token\"}");
        return false;
    }
}
