/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.binder;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDataParametersDefaults {

    /**
     * 最小 fake：仅实现两个抽象方法，其余全部走 IDataParameters 的 default 逻辑（被测对象）
     */
    static final class ArrayDataParameters implements IDataParameters {
        private final Object[] values;

        ArrayDataParameters(int size) {
            this.values = new Object[size];
        }

        @Override
        public Object getObject(int index) {
            return values[index];
        }

        @Override
        public void setObject(int index, Object value) {
            values[index] = value;
        }
    }

    @Test
    public void testNullSemantics() {
        ArrayDataParameters params = new ArrayDataParameters(2);
        assertTrue(params.isNull(0));
        params.setObject(0, "x");
        assertFalse(params.isNull(0));
        params.setNull(0);
        assertTrue(params.isNull(0));
        assertNull(params.getObject(0));
        // 缺省实现不持有 native connection
        assertNull(params.getNativeConnection());
    }

    @Test
    public void testSetterDefaultsDelegateToSetObject() {
        ArrayDataParameters params = new ArrayDataParameters(3);
        // 三参 setObject 与各 setter 的 default 实现最终落到 setObject(index, value)
        params.setObject(0, "raw", 99);
        assertEquals("raw", params.getObject(0));
        params.setInt(1, 42);
        assertEquals(42, params.getObject(1));
        params.setString(2, "s");
        assertEquals("s", params.getObject(2));
    }

    @Test
    public void testGetStringGetIntGetLongConversions() {
        ArrayDataParameters params = new ArrayDataParameters(3);
        params.setObject(0, "123");
        assertEquals("123", params.getString(0));
        assertEquals(Integer.valueOf(123), params.getInt(0));
        assertEquals(Long.valueOf(123), params.getLong(0));

        params.setObject(1, 4.5d);
        assertEquals("4.5", params.getString(1));

        // null 输入转换为 null 而不是异常
        assertNull(params.getInt(2));
        assertNull(params.getLong(2));
        assertNull(params.getString(2));
    }

    @Test
    public void testNumericAndTemporalConversions() {
        ArrayDataParameters params = new ArrayDataParameters(9);
        params.setObject(0, "true");
        assertEquals(Boolean.TRUE, params.getBoolean(0));
        params.setObject(1, "2.75");
        assertEquals(Double.valueOf(2.75d), params.getDouble(1));
        assertEquals(Float.valueOf(2.75f), params.getFloat(1));

        // 字符串到 Short/Byte 的转换按整数语义解析（"2.75" 会被拒绝）
        params.setObject(2, "7");
        assertEquals(Short.valueOf((short) 7), params.getShort(2));
        assertEquals(Byte.valueOf((byte) 7), params.getByte(2));

        params.setObject(3, "12.5");
        assertEquals(BigDecimal.valueOf(12.5d), params.getBigDecimal(3));

        LocalDateTime dt = LocalDateTime.of(2026, 10, 2, 8, 30);
        params.setObject(4, dt);
        assertEquals(dt, params.getLocalDateTime(4));
        assertEquals(dt, params.getTimestamp(4).toLocalDateTime());
        // 契约：getInstant 从 getTimestamp 派生
        assertEquals(params.getTimestamp(4).toInstant(), params.getInstant(4));

        // LocalTime / LocalDate 需要直接存储对应类型（转换器不做跨类型降级）
        LocalTime time = LocalTime.of(9, 15);
        params.setObject(5, time);
        assertEquals(time, params.getLocalTime(5));
        LocalDate date = LocalDate.of(2026, 10, 2);
        params.setObject(6, date);
        assertEquals(date, params.getLocalDate(6));

        byte[] bytes = new byte[]{1, 2, 3};
        params.setBytes(7, bytes);
        assertTrue(Arrays.equals(bytes, params.getBytes(7)));
        // ByteString 读写在 null 上对称
        params.setByteString(8, null);
        assertNull(params.getByteString(8));
        params.setObject(8, bytes);
        assertEquals(bytes.length, params.getByteString(8).toByteArray().length);
    }

    @Test
    public void testConvertErrorCarriesIndexParam() {
        ArrayDataParameters params = new ArrayDataParameters(1);
        params.setObject(0, "not-a-number");
        // 转换失败走 handleConvertError：NopException 携带 index 参数
        NopException e = assertThrows(NopException.class, () -> params.getInt(0));
        assertEquals(0, e.getParam("index"));
    }
}
