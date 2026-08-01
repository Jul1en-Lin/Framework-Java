package utils;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampUtilTest {

    @Test
    void shouldGetCurrentTimestampInMillisecondsAndSeconds() {
        long beforeMillis = System.currentTimeMillis();

        long currentMillis = TimestampUtil.currentMillis();
        long currentSeconds = TimestampUtil.currentSeconds();

        long afterMillis = System.currentTimeMillis();

        assertTrue(currentMillis >= beforeMillis && currentMillis <= afterMillis);
        assertTrue(currentSeconds >= beforeMillis / 1000);
        assertTrue(currentSeconds <= afterMillis / 1000 + 1);
        assertTrue(Math.abs(currentMillis / 1000 - currentSeconds) <= 1);
    }

    @Test
    void shouldCalculateAbsoluteDifferenceBetweenMillisecondTimestamps() {
        assertEquals(1500, TimestampUtil.differenceMillis(2500, 1000));
        assertEquals(1500, TimestampUtil.differenceMillis(1000, 2500));
    }

    @Test
    void shouldCalculateAbsoluteDifferenceBetweenSecondTimestamps() {
        assertEquals(35, TimestampUtil.differenceSeconds(135, 100));
        assertEquals(35, TimestampUtil.differenceSeconds(100, 135));
    }

    @Test
    void shouldGetFutureSecondsTimestampInMilliseconds() {
        long before = System.currentTimeMillis();

        long future = TimestampUtil.afterSecondsMillis(5);

        long after = System.currentTimeMillis();

        assertTrue(future >= before + 5_000);
        assertTrue(future <= after + 5_000);
    }

    @Test
    void shouldGetFutureMinutesTimestampInMilliseconds() {
        long before = System.currentTimeMillis();

        long future = TimestampUtil.afterMinutesMillis(2);

        long after = System.currentTimeMillis();

        assertTrue(future >= before + 120_000);
        assertTrue(future <= after + 120_000);
    }

    @Test
    void shouldGetFutureDaysTimestampInMilliseconds() {
        long before = System.currentTimeMillis();

        long future = TimestampUtil.afterDaysMillis(3);

        long after = System.currentTimeMillis();

        assertTrue(future >= before + 3 * 24 * 60 * 60 * 1000L);
        assertTrue(future <= after + 3 * 24 * 60 * 60 * 1000L);
    }

    @Test
    void shouldGetFutureMonthsTimestampInMillisecondsByCalendarMonth() {
        ZonedDateTime before = ZonedDateTime.now();

        long future = TimestampUtil.afterMonthsMillis(2);

        ZonedDateTime after = ZonedDateTime.now();
        long earliest = before.plusMonths(2).toInstant().toEpochMilli();
        long latest = after.plusMonths(2).toInstant().toEpochMilli();

        assertTrue(future >= earliest);
        assertTrue(future <= latest);
    }

    @Test
    void shouldGetFutureYearsTimestampInMillisecondsByCalendarYear() {
        ZonedDateTime before = ZonedDateTime.now();

        long future = TimestampUtil.afterYearsMillis(2);

        ZonedDateTime after = ZonedDateTime.now();
        long earliest = before.plusYears(2).toInstant().toEpochMilli();
        long latest = after.plusYears(2).toInstant().toEpochMilli();

        assertTrue(future >= earliest);
        assertTrue(future <= latest);
    }

    @Test
    void shouldProvideSecondBasedFutureTimestamps() {
        assertTrue(Math.abs(
                TimestampUtil.afterSecondsMillis(5) / 1000
                        - TimestampUtil.afterSecondsSeconds(5)) <= 1);
        assertTrue(Math.abs(
                TimestampUtil.afterMinutesMillis(2) / 1000
                        - TimestampUtil.afterMinutesSeconds(2)) <= 1);
        assertTrue(Math.abs(
                TimestampUtil.afterDaysMillis(3) / 1000
                        - TimestampUtil.afterDaysSeconds(3)) <= 1);
        assertTrue(Math.abs(
                TimestampUtil.afterMonthsMillis(2) / 1000
                        - TimestampUtil.afterMonthsSeconds(2)) <= 1);
        assertTrue(Math.abs(
                TimestampUtil.afterYearsMillis(2) / 1000
                        - TimestampUtil.afterYearsSeconds(2)) <= 1);
    }

    @Test
    void shouldReturnZeroForNegativeFutureTimeAmount() {
        assertEquals(0L, TimestampUtil.afterSecondsMillis(-1));
        assertEquals(0L, TimestampUtil.afterMinutesSeconds(-1));
        assertEquals(0L, TimestampUtil.afterDaysMillis(-1));
        assertEquals(0L, TimestampUtil.afterMonthsSeconds(-1));
        assertEquals(0L, TimestampUtil.afterYearsMillis(-1));
    }
}
