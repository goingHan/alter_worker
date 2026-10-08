package com.alertworker.worker.service;

// #AI-CODE-FILE-dev_release/v0.0.1-20260811: processing result contract shared by MQ consumer and HTTP debug endpoint
public record ProcessingResult(
        boolean success,
        boolean shouldRetry,
        long retryDelayMs,
        int successCount,
        int recipientCount,
        String error) {

    public static ProcessingResult ok(int successCount, int recipientCount) {
        return new ProcessingResult(true, false, 0L, successCount, recipientCount, null);
    }

    public static ProcessingResult failed(int successCount, int recipientCount,
                                          boolean shouldRetry, long retryDelayMs, String error) {
        return new ProcessingResult(false, shouldRetry, retryDelayMs, successCount, recipientCount, error);
    }
}
