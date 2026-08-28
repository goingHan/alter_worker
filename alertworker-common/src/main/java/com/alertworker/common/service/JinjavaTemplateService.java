package com.alertworker.common.service;

import com.hubspot.jinjava.Jinjava;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class JinjavaTemplateService {

    private final Jinjava jinjava;

    public JinjavaTemplateService() {
        this.jinjava = new Jinjava();
    }

    public String render(String template, Map<String, Object> context) {
        try {
            return jinjava.render(template, context);
        } catch (Exception e) {
            log.error("Failed to render template, error: {}", e.getMessage(), e);
            throw new RuntimeException("Template render failed: " + e.getMessage(), e);
        }
    }
}
