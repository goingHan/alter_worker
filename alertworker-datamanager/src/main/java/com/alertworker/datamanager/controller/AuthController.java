package com.alertworker.datamanager.controller;

import com.alertworker.datamanager.config.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenUtil tokenUtil;

    @Value("${alertworker.auth.username:admin}")
    private String adminUsername;

    @Value("${alertworker.auth.password:admin123}")
    private String adminPassword;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null
                || !adminUsername.equals(username) || !adminPassword.equals(password)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        String token = tokenUtil.createToken(username);
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", username);
        result.put("expiresIn", 12 * 3600);
        log.info("User {} logged in", username);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        String username = tokenUtil.verifyToken(token);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
        }
        return ResponseEntity.ok(Map.of("username", username));
    }
}
