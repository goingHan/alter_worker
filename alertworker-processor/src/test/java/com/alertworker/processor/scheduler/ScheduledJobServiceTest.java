package com.alertworker.processor.scheduler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduledJobServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * The scheduler advances next_run_at via Spring's {@code CronExpression.next} pinned to Asia/Shanghai.
     * A daily 08:00 cron fired from Oct 8 09:30 (+08:00) must land on the NEXT day's 08:00 local time.
     */
    @Test
    void nextRunInZone_advancesDaily0800CronToNextLocalDay() {
        ZonedDateTime after = ZonedDateTime.of(2026, 10, 8, 9, 30, 0, 0, ZONE);

        LocalDateTime next = ScheduledJobService.nextRunInZone("0 0 8 * * *", after);

        assertEquals(LocalDateTime.of(2026, 10, 9, 8, 0, 0), next);
    }

    @Test
    void nextRunInZone_sameDayWhenCronInstantStillAhead() {
        ZonedDateTime after = ZonedDateTime.of(2026, 10, 8, 5, 0, 0, 0, ZONE);

        LocalDateTime next = ScheduledJobService.nextRunInZone("0 0 8 * * *", after);

        assertEquals(LocalDateTime.of(2026, 10, 8, 8, 0, 0), next);
    }
}
