package io.nop.excel.format;

import org.junit.jupiter.api.Test;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 单元格格式串 -> Java Format 的转换语义：
 * General/@ 不转换；SSN/Zip/Phone 等 builtin 特殊格式按位拼接；日期格式串转 SimpleDateFormat。
 */
public class TestExcelFormatHelper {

    // General 与文本格式 "@" 不产生 Java Format；无法识别的格式串返回 null 而不是抛异常
    @Test
    public void testGeneralTextAndUnknownFormatsReturnNull() {
        assertNull(ExcelFormatHelper.getFormat(null));
        assertNull(ExcelFormatHelper.getFormat("General"));
        assertNull(ExcelFormatHelper.getFormat("@"));

        // 不含 0/# 数字占位符的非日期格式无法转换为 Format
        assertNull(ExcelFormatHelper.getFormat("no-placeholders"));
    }

    // SSN/Zip+4/Phone 内置格式：数字按固定位数补零拼接，段间加分隔符
    @Test
    public void testBuiltinSsnZipPhoneFormats() {
        try {
            assertEquals("123-45-6789", ExcelFormatHelper.getFormat("000-00-0000").format(123456789));
            assertEquals("12345-6789", ExcelFormatHelper.getFormat("00000-0000").format(123456789));
            // 转义与非转义两种写法注册的是同一 format
            assertNotNull(ExcelFormatHelper.getFormat("00000\\-0000"));

            // 7 位电话：seg1 为空不输出区号括号
            assertEquals("555-1234", ExcelFormatHelper.getFormat("###-####;(###) ###-####").format(5551234));
            // 10 位电话：输出 "(区号) 前缀-后缀"
            assertEquals("(212) 555-1234",
                    ExcelFormatHelper.getFormat("###-####;(###) ###-####").format(2125551234L));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // 日期格式串被识别并转换为 SimpleDateFormat，格式化结果与 SimpleDateFormat 一致
    @Test
    public void testDateFormatStringConvertsToSimpleDateFormat() {
        Format format = ExcelFormatHelper.getFormat("yyyy-mm-dd");
        assertNotNull(format);
        assertTrue(format instanceof SimpleDateFormat);

        Calendar cal = new GregorianCalendar(2024, Calendar.FEBRUARY, 29, 0, 0, 0);
        Date date = cal.getTime();
        // createDateFormat 将 excel 的 m（随 y/d 出现时为月）转换为 SimpleDateFormat 的 M
        assertEquals("2024-02-29", format.format(date));
    }

    // 含颜色段 "[Red]" 的格式串：颜色段被剥离后仍可转换
    @Test
    public void testColorSectionStrippedBeforeConvert() {
        // [Red] 被剥离后剩 "0.00"，可转换为数字格式
        Format format = ExcelFormatHelper.getFormat("[Red]0.00");
        assertNotNull(format);
    }
}
