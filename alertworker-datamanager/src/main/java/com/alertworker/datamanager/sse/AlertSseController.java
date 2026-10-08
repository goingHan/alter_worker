package com.alertworker.datamanager.sse;

import com.alertworker.common.entity.AlertMessage;
import com.alertworker.common.mapper.AlertMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class AlertSseController {

    private final AlertMessageMapper alertMessageMapper;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private LocalDateTime lastCheck = LocalDateTime.now();

    @GetMapping("/alerts")
    public SseEmitter streamAlerts() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        return emitter;
    }

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<AlertMessage> newMessages = alertMessageMapper.selectList(
                        new LambdaQueryWrapper<AlertMessage>()
                                .gt(AlertMessage::getCreatedAt, lastCheck)
                                .orderByDesc(AlertMessage::getCreatedAt)
                );
                if (!newMessages.isEmpty()) {
                    lastCheck = newMessages.get(0).getCreatedAt();
                    newMessages.forEach(m -> m.setRawPayload(null));
                    String json = objectMapper.writeValueAsString(newMessages);
                    for (SseEmitter emitter : emitters) {
                        try {
                            emitter.send(SseEmitter.event().name("alerts").data(json));
                        } catch (Exception e) {
                            emitters.remove(emitter);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("SSE broadcast error", e);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
    }
}
