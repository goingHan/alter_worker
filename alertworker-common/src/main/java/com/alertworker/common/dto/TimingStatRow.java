package com.alertworker.common.dto;

import lombok.Data;

@Data
public class TimingStatRow {
    private Double gwAvg;
    private Double gwP95;
    private Double gwMax;
    private Double mqAvg;
    private Double mqP95;
    private Double mqMax;
    private Double workerAvg;
    private Double workerP95;
    private Double workerMax;
    private Double totalAvg;
    private Double totalP95;
    private Double totalMax;
    private Long count;
}
