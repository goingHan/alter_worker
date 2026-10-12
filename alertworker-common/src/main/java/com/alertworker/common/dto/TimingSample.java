package com.alertworker.common.dto;

import lombok.Data;

/**
 * One message's end-to-end timing samples in milliseconds, used by the MySQL analytics implementation to
 * compute avg / p95 / max in Java (MySQL has no {@code PERCENTILE_CONT}). Any segment whose timestamps are
 * missing is {@code null} and is skipped by the aggregation.
 */
@Data
public class TimingSample {
    private Double gwMs;
    private Double mqMs;
    private Double workerMs;
    private Double totalMs;
}
