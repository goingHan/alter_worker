package com.alertworker.worker.channel;

import com.alertworker.common.dto.AlertRecipient;
import com.alertworker.common.entity.MessageChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelHandler {

    private final JavaMailSender javaMailSender;
    private final ObjectMapper objectMapper;

    public boolean send(MessageChannel channel, AlertRecipient recipient, String renderedContent, boolean htmlInline) {
        try {
            Map<String, Object> config = objectMapper.readValue(channel.getConfigJson(), Map.class);
            String from = (String) config.get("from");
            String subject = config.get("subject") != null ? (String) config.get("subject") : "Alert Notification";
            String format = config.get("format") != null ? (String) config.get("format") : "plain";
            boolean html = htmlInline || "html".equalsIgnoreCase(format);

            // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260902: prefer assignee email, then phone, then testTo
            String toEmail = recipient.getEmail();
            if (toEmail == null || !toEmail.contains("@")) {
                toEmail = recipient.getPhone();
            }
            if (toEmail == null || !toEmail.contains("@")) {
                toEmail = (String) config.get("testTo"); // for testing or fallback
            }
            if (toEmail == null) {
                log.error("No valid email address for recipient: {}", recipient.getAssigneeId());
                return false;
            }

            if (html) {
                MimeMessage mimeMessage = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(from);
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(renderedContent, true);
                javaMailSender.send(mimeMessage);
            } else {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(renderedContent);
                javaMailSender.send(message);
            }
            return true;
        } catch (Exception e) {
            log.error("Email send failed", e);
            return false;
        }
    }
}
