package com.alertworker.common.enums;

/**
 * Alert severity owned by an {@code alert_rule} (not the incoming alert system). The rule's level is authoritative:
 * it overrides the severity carried by the alert payload when rendering templates and when aggregating daily reports.
 */
public enum AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}
