package io.nop.excel.format;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 序列日期与 Java 日期的双向转换语义（格式转换语义，无求值参与）：
 * 1900 日期系统锚点、1900-02-29 伪闰日补偿、1904 窗口、日期格式串识别、时间分数转换。
 */
public class TestExcelDateHelper {

    private static Calendar utcCalendar(int year, int month, int day, int hour, int minute, int second) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(year, month - 1, day, hour, minute, second);
        return cal;
    }

    // Excel 序列值 1 锚定 1900-01-01（1900 日期系统）；序列 61 因补 Excel 伪闰日 1900-02-29 映射为 1900-03-01
    @Test
    public void testExcelEpochAnchorAndFakeLeapDay() {
        Calendar cal1 = ExcelDateHelper.getJavaCalendarUTC(1, false);
        assertEquals(1900, cal1.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, cal1.get(Calendar.MONTH));
        assertEquals(1, cal1.get(Calendar.DAY_OF_MONTH));

        // Excel 认为 1900-02-29 存在（序列 60），序列 60/61 都落在真实的 1900-03-01
        Calendar cal60 = ExcelDateHelper.getJavaCalendarUTC(60, false);
        assertEquals(1900, cal60.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, cal60.get(Calendar.MONTH));
        assertEquals(1, cal60.get(Calendar.DAY_OF_MONTH));

        Calendar cal61 = ExcelDateHelper.getJavaCalendarUTC(61, false);
        assertEquals(Calendar.MARCH, cal61.get(Calendar.MONTH));
        assertEquals(1, cal61.get(Calendar.DAY_OF_MONTH));

        // 序列 0 为 1900 日期系统起点前一天
        Calendar cal0 = ExcelDateHelper.getJavaCalendarUTC(0, false);
        assertEquals(1899, cal0.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, cal0.get(Calendar.MONTH));
        assertEquals(31, cal0.get(Calendar.DAY_OF_MONTH));
    }

    // LocalDateTime -> Excel 序列 -> LocalDateTime roundtrip 恒等，整数部分为 1900 系统天数
    @Test
    public void testLocalDateTimeRoundtripIdentity() {
        LocalDateTime ldt = LocalDateTime.of(2024, 2, 29, 12, 0, 0);
        double excelDate = ExcelDateHelper.localDateTimeToExcelDate(ldt);

        // 2024-02-29 距 1899-12-30 为 45351 天（2024 为闰年）
        assertEquals(45351L, (long) Math.floor(excelDate));
        // 12:00 为半天，允许浮点误差
        assertEquals(0.5, excelDate - Math.floor(excelDate), 1e-9);

        LocalDateTime restored = ExcelDateHelper.excelDateToLocalDateTime(excelDate);
        assertEquals(ldt, restored);
    }

    // 1904 窗口：1904-01-01 为序列 0；1900 系统下 1900 年以前的日期非法（返回 -1，反向转换得 null）
    @Test
    public void test1904WindowingAndInvalidDates() {
        double d = ExcelDateHelper.getExcelDate(utcCalendar(1904, 1, 1, 0, 0, 0), true);
        assertEquals(0.0, d, 1e-9);

        // 1900 窗口下 1899-12-31 早于 1900 起点 → BAD_DATE
        assertEquals(-1.0, ExcelDateHelper.getExcelDate(utcCalendar(1899, 12, 31, 0, 0, 0), false), 1e-9);

        // 非法序列值反向转换返回 null 而不是抛异常
        assertFalse(ExcelDateHelper.isValidExcelDate(-1.0));
        assertTrue(ExcelDateHelper.isValidExcelDate(0.0));
        assertNull(ExcelDateHelper.getJavaCalendar(-1.0));
        assertNull(ExcelDateHelper.excelDateToLocalDateTime(-1.0));
    }

    // 格式串识别：日期格式、时间格式、流逝时间格式为 true；数字/文本格式为 false
    @Test
    public void testIsADateFormatDetection() {
        assertTrue(ExcelDateHelper.isADateFormat("yyyy-mm-dd"));
        assertTrue(ExcelDateHelper.isADateFormat("h:mm:ss AM/PM"));
        // [h]:mm:ss 为流逝时间格式
        assertTrue(ExcelDateHelper.isADateFormat("[h]:mm:ss"));
        // 内置日期格式索引（0x0e=m/d/yy 等）
        assertTrue(ExcelDateHelper.isInternalDateFormat(0x0e));
        assertTrue(ExcelDateHelper.isInternalDateFormat(0x14));

        assertFalse(ExcelDateHelper.isADateFormat("0.00"));
        assertFalse(ExcelDateHelper.isADateFormat("#,##0"));
        assertFalse(ExcelDateHelper.isADateFormat("General"));
        assertFalse(ExcelDateHelper.isADateFormat(""));
        assertFalse(ExcelDateHelper.isADateFormat(null));
        assertFalse(ExcelDateHelper.isInternalDateFormat(0));
    }

    // "HH:MM" / "HH:MM:SS" 转一天内的分数；越界或段数错误必须拒绝
    @Test
    public void testConvertTimeFractionOfDay() {
        assertEquals(0.0, ExcelDateHelper.convertTime("00:00"), 1e-12);
        assertEquals((12 * 3600 + 34 * 60 + 56) / 86400.0, ExcelDateHelper.convertTime("12:34:56"), 1e-12);
        assertEquals(86399 / 86400.0, ExcelDateHelper.convertTime("23:59:59"), 1e-12);

        // 小时 >= 24、分钟 >= 60 越界拒绝
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.convertTime("24:00"));
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.convertTime("12:60"));
        // 段数不是 2 或 3
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.convertTime("12"));
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.convertTime("1:2:3:4"));
    }

    // "YYYY/MM/DD" 解析为 Java Date；非法年月日必须拒绝
    @Test
    public void testParseYYYYMMDDDate() {
        Date date = ExcelDateHelper.parseYYYYMMDDDate("2024/02/29");
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        assertEquals(2024, cal.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH));
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH));

        // 月份越界
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.parseYYYYMMDDDate("2024/13/01"));
        // 2 月 30 日必须拒绝（回归覆盖 wi9#2，plan 2306 项 24：非 lenient 校验），
        // 修复前经 lenient Calendar 静默归一化为 3 月 1 日
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.parseYYYYMMDDDate("2024/02/30"));
        // 日越界
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.parseYYYYMMDDDate("2024/02/32"));
        // 分隔符必须为 '/'（回归覆盖 wi9#2：此前 "2024-02-29" 被静默接受）
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.parseYYYYMMDDDate("2024-02-29"));
        // 长度不足 10
        assertThrows(IllegalArgumentException.class, () -> ExcelDateHelper.parseYYYYMMDDDate("2024/2/9"));
    }
}
