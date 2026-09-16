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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DingDingChannelHandler {

    private final JinjavaTemplateService jinjavaTemplateService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean send(MessageChannel channel, List<AlertRecipient> recipients, String renderedContent) {
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
            Map<String, Object> body = buildBody(msgType, content, config, recipients);

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

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: add DingDing @ (at) block for markdown/text
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260907: batch all recipients of the same channel into a single message
    private Map<String, Object> buildBody(String msgType, String content, Map<String, Object> config,
                                          List<AlertRecipient> recipients) {
        Map<String, Object> at = buildAt(recipients);
        String mention = buildAtMentionText(recipients);
        return switch (msgType) {
            case "markdown" -> {
                Map<String, Object> markdown = new HashMap<>();
                markdown.put("title", "Alert");
                markdown.put("text", appendMention(content, mention));
                Map<String, Object> body = new HashMap<>();
                body.put("msgtype", "markdown");
                body.put("markdown", markdown);
                if (at != null) {
                    body.put("at", at);
                }
                yield body;
            }
            case "action_card" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionCard = (Map<String, Object>) config.get("actionCard");
                Map<String, Object> card = new HashMap<>();
                card.put("title", actionCard != null ? actionCard.getOrDefault("title", "Alert") : "Alert");
                card.put("text", content);
                card.put("singleTitle", actionCard != null ? actionCard.getOrDefault("singleTitle", "View") : "View");
                card.put("singleURL", actionCard != null ? actionCard.getOrDefault("singleUrl", "") : "");
                Map<String, Object> body = new HashMap<>();
                body.put("msgtype", "actionCard");
                body.put("actionCard", card);
                yield body;
            }
            default -> {
                Map<String, Object> text = new HashMap<>();
                text.put("content", appendMention(content, mention));
                Map<String, Object> body = new HashMap<>();
                body.put("msgtype", "text");
                body.put("text", text);
                if (at != null) {
                    body.put("at", at);
                }
                yield body;
            }
        };
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260916: @ DingDing users by dingdingUsername instead of phone number
    private Map<String, Object> buildAt(List<AlertRecipient> recipients) {
        Set<String> atUserIds = new LinkedHashSet<>();
        for (AlertRecipient recipient : recipients) {
            if (StringUtils.hasText(recipient.getDingdingUsername())) {
                atUserIds.add(recipient.getDingdingUsername());
            }
        }
        if (atUserIds.isEmpty()) {
            return null;
        }
        Map<String, Object> at = new HashMap<>();
        at.put("atUserIds", new ArrayList<>(atUserIds));
        at.put("isAtAll", false);
        return at;
    }

    private String buildAtMentionText(List<AlertRecipient> recipients) {
        List<String> names = recipients.stream()
                .map(AlertRecipient::getDingdingUsername)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        return names.stream().map(name -> "@" + name).collect(Collectors.joining(" ")) + " ";
    }

    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260908: place @ mentions at the end of the message body instead of the beginning
    private String appendMention(String content, String mention) {
        if (mention != null && !content.contains(mention.trim())) {
            return content + "\n" + mention;
        }
        return content;
    }
}
