package com.alertworker.common.scheduler;

import com.alertworker.common.entity.ScheduledJob;

public interface JobExecutor {
    String jobType();

    void execute(ScheduledJob job);
}
