package com.alertworker.datamanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.alertworker"})
public class DataManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataManagerApplication.class, args);
    }
}
