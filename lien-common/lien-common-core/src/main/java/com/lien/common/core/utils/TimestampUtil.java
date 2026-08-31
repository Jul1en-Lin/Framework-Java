package com.lien.common.core.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 时间戳工具类。
 *
 * <p>秒级时间戳使用 Unix timestamp 的秒数，毫秒级时间戳使用 Unix timestamp 的毫秒数。
 * 月和年的计算按照日历规则处理，秒、分钟和天按照时间间隔处理。</p>
 */
@Slf4j
public final class TimestampUtil {

    private TimestampUtil() {} // 私有化构造函数，防止实例化

    /**
     * 获取当前毫秒级时间戳。
     */
    public static long currentMillis() {
        return Instant.now().toEpochMilli();
    }

    /**
     * 获取当前秒级时间戳。
     */
    public static long currentSeconds() {
        return Instant.now().getEpochSecond();
    }

    /**
     * 计算两个毫秒级时间戳的绝对差值。
     */
    public static long differenceMillis(long firstTimestampMillis, long secondTimestampMillis) {
        return Math.abs(firstTimestampMillis - secondTimestampMillis);
    }

    /**
     * 计算两个秒级时间戳的绝对差值。
     */
    public static long differenceSeconds(long firstTimestampSeconds, long secondTimestampSeconds) {
        return Math.abs(firstTimestampSeconds - secondTimestampSeconds);
    }

    /**
     * 获取未来指定秒数对应的毫秒级时间戳。
     */
    public static long afterSecondsMillis(long seconds) {
        return afterDurationMillis(seconds, ChronoUnit.SECONDS);
    }

    /**
     * 获取未来指定秒数对应的秒级时间戳。
     */
    public static long afterSecondsSeconds(long seconds) {
        return afterSecondsMillis(seconds) / 1000;
    }

    /**
     * 获取未来指定分钟数对应的毫秒级时间戳。
     */
    public static long afterMinutesMillis(long minutes) {
        return afterDurationMillis(minutes, ChronoUnit.MINUTES);
    }

    /**
     * 获取未来指定分钟数对应的秒级时间戳。
     */
    public static long afterMinutesSeconds(long minutes) {
        return afterMinutesMillis(minutes) / 1000;
    }

    /**
     * 获取未来指定天数对应的毫秒级时间戳。
     */
    public static long afterDaysMillis(long days) {
        return afterDurationMillis(days, ChronoUnit.DAYS);
    }

    /**
     * 获取未来指定天数对应的秒级时间戳。
     */
    public static long afterDaysSeconds(long days) {
        return afterDaysMillis(days) / 1000;
    }

    /**
     * 获取未来指定月数对应的毫秒级时间戳，按日历月份计算。
     */
    public static long afterMonthsMillis(long months) {
        return afterCalendarMillis(months, ChronoUnit.MONTHS);
    }

    /**
     * 获取未来指定月数对应的秒级时间戳，按日历月份计算。
     */
    public static long afterMonthsSeconds(long months) {
        return afterMonthsMillis(months) / 1000;
    }

    /**
     * 获取未来指定年数对应的毫秒级时间戳，按日历年份计算。
     */
    public static long afterYearsMillis(long years) {
        return afterCalendarMillis(years, ChronoUnit.YEARS);
    }

    /**
     * 获取未来指定年数对应的秒级时间戳，按日历年份计算。
     */
    public static long afterYearsSeconds(long years) {
        return afterYearsMillis(years) / 1000;
    }

    private static long afterDurationMillis(long amount, ChronoUnit unit) {
        if (!validateAmount(amount)) {
            return 0L;
        }
        return Instant.now().plus(amount, unit).toEpochMilli();
    }

    private static long afterCalendarMillis(long amount, ChronoUnit unit) {
        if (!validateAmount(amount)) {
            return 0L;
        }
        return ZonedDateTime.now().plus(amount, unit).toInstant().toEpochMilli();
    }

    private static boolean validateAmount(long amount) {
        if (amount < 0) {
            log.error("未来时间的数量不能为负数：{}", amount);
            return false;
        }
        return true;
    }
}
