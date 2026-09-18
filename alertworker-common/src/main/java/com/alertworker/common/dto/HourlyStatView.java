package com.alertworker.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HourlyStatView {
    private LocalDateTime hour;
    private Long total;
    private Long failed;
    private Long deadLetters;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: messages dropped by a silenced rule in this hour
    private Long silenced;
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: messages dropped because no alert rule matched in this hour
    private Long unmatched;
}
