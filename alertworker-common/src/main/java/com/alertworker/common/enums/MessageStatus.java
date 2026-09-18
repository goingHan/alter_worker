package com.alertworker.common.enums;

public enum MessageStatus {
    recieved,
    GW_SENT,
    DELIVERED,
    WORKER_SENT,
    FAILED,
    success,
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: terminal state for alerts dropped by a silenced rule;
    // the message is persisted for the dashboard but never enqueued to RabbitMQ
    SILENCED,
    // #AI-CODE-FUNCTION-dev_release/v0.0.1-20260917: terminal state for alerts that matched no alert rule at all —
    // also dropped at the gateway (no recipients, no MQ), counted separately from failures on the dashboard
    UNMATCHED
}
