package com.alertworker.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Data-processing module: owns the scheduled-job tick (periodic test messages, daily-report generation) and, for
 * HTML report templates, renders and uploads the report file to object storage before enqueueing it.
 */
@SpringBootApplication(scanBasePackages = {"com.alertworker"})
public class ProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcessorApplication.class, args);
    }
}
