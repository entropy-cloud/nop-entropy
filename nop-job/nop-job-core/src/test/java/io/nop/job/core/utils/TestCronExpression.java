package io.nop.job.core.utils;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestCronExpression {

    @Test
    void test_equalsAndHashCode_sameExpressionSameTimeZone() throws ParseException {
        CronExpression a = new CronExpression("0 0 12 ? * MON-FRI");
        CronExpression b = new CronExpression("0 0 12 ? * MON-FRI");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void test_equalsDifferentTimeZone() throws ParseException {
        CronExpression utc = new CronExpression("0 0 12 ? * MON-FRI", TimeZone.getTimeZone("UTC"));
        CronExpression tokyo = new CronExpression("0 0 12 ? * MON-FRI", TimeZone.getTimeZone("Asia/Tokyo"));
        assertNotEquals(utc, tokyo, "CronExpressions with different timeZones should not be equal");
        assertNotEquals(utc.hashCode(), tokyo.hashCode(), "hashCode should differ for different timeZones");
    }

    @Test
    void test_equalsSameTimeZone() throws ParseException {
        TimeZone tz = TimeZone.getTimeZone("America/New_York");
        CronExpression a = new CronExpression("0 30 9 ? * *", tz);
        CronExpression b = new CronExpression("0 30 9 ? * *", tz);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
