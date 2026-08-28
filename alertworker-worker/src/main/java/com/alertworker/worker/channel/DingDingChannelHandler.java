package com.alertworker.worker.channel;

import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.service.JinjavaTemplateService;
import com.alertworker.common.util.DingDingConfigUtil;
import com.alertworker.common.util.DingDingSignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DingDingChannelHandler {

    private final JinjavaTemplateService jinjavaTemplateService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean send(MessageChannel channel, AlertRecipient recipient, String renderedContent) {
        try {
            Map<String, Object> config = objectMapper.readValue(channel.getConfigJson(), Map.class);
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl == null) {
                throw new IllegalArgumentException("Missing webhookUrl in DingDing channel config");
            }
            String secret = (String) config.get("secret");
            if (secret != null && !secret.isEmpty()) {
                webhookUrl = DingDingSignUtil.signUrl(webhookUrl, secret);
            }

            String msgType = config.get("msgType") != null ? (String) config.get("msgType") : "text";
            String content = DingDingConfigUtil.buildMessageContent(config.get("keyword"), renderedContent, msgType);
            Map<String, Object> body = buildBody(msgType, content, config);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            boolean success = isDingDingSuccess(response);
            if (!success) {
                log.error("DingDing send failed, response: {}", response.getBody());
            }
            return success;
        } catch (Exception e) {
            log.error("DingDing send failed", e);
            return false;
        }
    }

    private boolean isDingDingSuccess(ResponseEntity<String> response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return false;
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> result = objectMapper.readValue(body, Map.class);
            Object errcode = result.get("errcode");
            return errcode == null || ((Number) errcode).intValue() == 0;
        } catch (Exception e) {
            log.warn("Unable to parse DingDing response body: {}", body);
            return true;
        }
    }

    private Map<String, Object> buildBody(String msgType, String content, Map<String, Object> config) {
        return switch (msgType) {
            case "markdown" -> Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("title", "Alert", "text", content)
            );
            case "action_card" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionCard = (Map<String, Object>) config.get("actionCard");
                yield Map.of(
                        "msgtype", "actionCard",
                        "actionCard", Map.of(
                                "title", actionCard != null ? actionCard.getOrDefault("title", "Alert") : "Alert",
                                "text", content,
                                "singleTitle", actionCard != null ? actionCard.getOrDefault("singleTitle", "View") : "View",
                                "singleURL", actionCard != null ? actionCard.getOrDefault("singleUrl", "") : ""
                        )
                );
            }
            default -> Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", content)
            );
        };
    }
}
