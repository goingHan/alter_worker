package com.alertworker.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HourlyStatView {
    private LocalDateTime hour;
    private Long total;
    private Long failed;
    private Long deadLetters;
}
