package com.alertworker.gateway.service;

import com.alertworker.common.entity.MessageChannel;
import com.alertworker.common.enums.ChannelType;
import com.alertworker.common.mapper.MessageChannelMapper;
import com.alertworker.common.service.JinjavaTemplateService;
import com.alertworker.common.util.DingDingConfigUtil;
import com.alertworker.common.util.DingDingSignUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelTestService {

    private final MessageChannelMapper messageChannelMapper;
    private final JinjavaTemplateService jinjavaTemplateService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean testChannel(Long channelId) {
        MessageChannel channel = messageChannelMapper.selectById(channelId);
        if (channel == null || channel.getDeleted() == 1) {
            throw new IllegalArgumentException("Channel not found or deleted");
        }

        if (channel.getChannelType() == ChannelType.DINGDING) {
            return testDingDing(channel);
        } else if (channel.getChannelType() == ChannelType.EMAIL) {
            return testEmail(channel);
        }
        return false;
    }

    private boolean testDingDing(MessageChannel channel) {
        try {
            Map<String, Object> config = objectMapper.readValue(channel.getConfigJson(), Map.class);
            String webhookUrl = (String) config.get("webhookUrl");
            if (webhookUrl == null) {
                return false;
            }
            String secret = (String) config.get("secret");
            if (secret != null && !secret.isEmpty()) {
                webhookUrl = DingDingSignUtil.signUrl(webhookUrl, secret);
            }

            String content = DingDingConfigUtil.buildMessageContent(config.get("keyword"), "[AlertWorker] Channel test message", "text");
            Map<String, Object> body = Map.of(
                    "msgtype", "text",
                    "text", Map.of("content", content)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, entity, String.class);
            return isDingDingSuccess(response);
        } catch (Exception e) {
            log.error("DingDing channel test failed", e);
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

    private boolean testEmail(MessageChannel channel) {
        // Email test is delegated to Worker or JavaMailSender configuration check
        log.info("Email channel test for channelId={} - skipping actual send in gateway", channel.getId());
        return true;
    }
}
