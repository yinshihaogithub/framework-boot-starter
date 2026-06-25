package com.framework.tools.date;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * 日期时间工具
 */
public final class DateUtils {

    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private DateUtils() {}

    public static String formatDateTime(LocalDateTime dateTime) {
        return format(dateTime, PATTERN_DATETIME);
    }

    public static String formatDate(LocalDate date) {
        return format(date, PATTERN_DATE);
    }

    public static String format(TemporalAccessor temporal, String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(temporal);
    }

    public static LocalDateTime parseDateTime(String text) {
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(PATTERN_DATETIME));
    }

    public static LocalDate parseDate(String text) {
        return LocalDate.parse(text, DateTimeFormatter.ofPattern(PATTERN_DATE));
    }

    public static Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(DEFAULT_ZONE).toInstant());
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(DEFAULT_ZONE).toLocalDateTime();
    }

    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(DEFAULT_ZONE).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), DEFAULT_ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(DEFAULT_ZONE);
    }

    public static long diffDays(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toDays();
    }

    public static long diffHours(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toHours();
    }

    public static long diffMinutes(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toMinutes();
    }
}
