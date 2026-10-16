package com.rockettrading.rocket_trading.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerpetualFuturesWorkerTest {
    @Test void fundingRunsOnTheHourWithRetriesOnlyInItsFirstFiveMinutes() {
        CronExpression schedule = CronExpression.parse(PerpetualFuturesWorker.FUNDING_SCHEDULE);
        ZonedDateTime at = ZonedDateTime.of(2026, 10, 16, 10, 59, 59, 0, ZoneOffset.UTC);
        List<ZonedDateTime> runs = new ArrayList<>();
        for (int i = 0; i < 21; i++) runs.add(at = schedule.next(at));

        assertEquals(ZonedDateTime.of(2026, 10, 16, 11, 0, 0, 0, ZoneOffset.UTC), runs.get(0));
        assertEquals(ZonedDateTime.of(2026, 10, 16, 11, 0, 15, 0, ZoneOffset.UTC), runs.get(1));
        // the last retry of the hour, then nothing until the next hour
        assertEquals(ZonedDateTime.of(2026, 10, 16, 11, 4, 45, 0, ZoneOffset.UTC), runs.get(19));
        assertEquals(ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, ZoneOffset.UTC), runs.get(20));
    }
}
