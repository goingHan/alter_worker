package com.alertworker.gateway.controller;

import com.alertworker.gateway.service.ChannelTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChannelTestController {

    private final ChannelTestService channelTestService;

    @PostMapping("/channels/{id}/test")
    public ResponseEntity<?> testChannel(@PathVariable Long id) {
        boolean success = channelTestService.testChannel(id);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Channel test passed"));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Channel test failed"));
    }
}
