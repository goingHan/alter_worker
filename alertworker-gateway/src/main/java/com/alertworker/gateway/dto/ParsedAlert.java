package com.alertworker.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ParsedAlert {
    private String rawPayload;
    private Map<String, Object> context;
    private String serviceType;
}
