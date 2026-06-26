package com.framework.tools.date;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Test
    void formatsParsesAndConvertsWithDefaultZone() {
        LocalDateTime dateTime = LocalDateTime.of(2026, 6, 25, 9, 30, 15);

        assertThat(DateUtils.formatDateTime(dateTime)).isEqualTo("2026-06-25 09:30:15");
        assertThat(DateUtils.parseDateTime("2026-06-25 09:30:15")).isEqualTo(dateTime);
        assertThat(DateUtils.formatDate(LocalDate.of(2026, 6, 25))).isEqualTo("2026-06-25");
        assertThat(DateUtils.parseDate("2026-06-25")).isEqualTo(LocalDate.of(2026, 6, 25));

        long epochMilli = DateUtils.toEpochMilli(dateTime);
        Date date = DateUtils.toDate(dateTime);

        assertThat(DateUtils.fromEpochMilli(epochMilli)).isEqualTo(dateTime);
        assertThat(DateUtils.toLocalDateTime(date)).isEqualTo(dateTime);
    }

    @Test
    void calculatesDurationDiffs() {
        LocalDateTime start = LocalDateTime.of(2026, 6, 24, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 25, 11, 30);

        assertThat(DateUtils.diffDays(start, end)).isEqualTo(1);
        assertThat(DateUtils.diffHours(start, end)).isEqualTo(26);
        assertThat(DateUtils.diffMinutes(start, end)).isEqualTo(1590);
    }
}
