/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用侧规范化 hash（设计 §4.6）：null 专用标记 / 数值 BigDecimal 规范化 / 时间统一毫秒精度 /
 * 二进制固定 Base64 / 类型标签防跨型碰撞；enc 主列（物理列值）原样进入规范化串（密文比对）。
 */
public class TestConsistencyCanonical {

    @Test
    public void testNullMarkerAndTypeTags() {
        assertEquals("\u0001NULL", ConsistencyVerifier.canonical(null));
        assertTrue(ConsistencyVerifier.canonical("abc").startsWith("s:"));
        assertTrue(ConsistencyVerifier.canonical(1).startsWith("n:"));
        assertTrue(ConsistencyVerifier.canonical(true).startsWith("b:"));
        // 类型标签防碰撞：字符串 "1" 与数值 1 的规范化串不同
        assertNotEquals(ConsistencyVerifier.canonical("1"), ConsistencyVerifier.canonical(1));
    }

    @Test
    public void testNumericNormalization() {
        // BigDecimal 精度规范化：1.50 与 1.5 同一规范化串
        assertEquals(ConsistencyVerifier.canonical(new BigDecimal("1.50")),
                ConsistencyVerifier.canonical(new BigDecimal("1.5")));
        // 整型与同值 BigDecimal 规范化一致（跨库类型漂移容忍）
        assertEquals(ConsistencyVerifier.canonical(42L), ConsistencyVerifier.canonical(new BigDecimal("42")));
        assertEquals(ConsistencyVerifier.canonical(42), ConsistencyVerifier.canonical(42L));
        // 浮点经 BigDecimal 规范化
        assertEquals(ConsistencyVerifier.canonical(1.0d), ConsistencyVerifier.canonical(1.0f));
    }

    @Test
    public void testTimestampNormalization() {
        Timestamp ts = Timestamp.valueOf("2026-01-02 03:04:05");
        // 墙钟 UTC 等价归一：Timestamp 与同墙钟 LocalDateTime 规范化一致（跨 JVM 时区稳定）
        java.time.LocalDateTime ldt = ts.toLocalDateTime();
        assertEquals(ConsistencyVerifier.canonical(ts), ConsistencyVerifier.canonical(ldt));
        assertEquals("t:" + ldt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli(),
                ConsistencyVerifier.canonical(ts));
    }

    @Test
    public void testBinaryNormalization() {
        byte[] bytes = new byte[]{1, 2, 3, -1};
        String encoded = ConsistencyVerifier.canonical(bytes);
        assertTrue(encoded.startsWith("z:"));
        assertEquals(encoded, ConsistencyVerifier.canonical(bytes.clone()));
    }

    @Test
    public void testRowHashStableAcrossColumnOrderAndCase() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("SID", "a1");
        row1.put("AMOUNT", new BigDecimal("10.50"));
        row1.put("ENC_COL", "@enc:cipher-text"); // 密文原样比对

        Map<String, Object> row2 = new HashMap<>();
        row2.put("amount", new BigDecimal("10.5"));
        row2.put("sid", "a1");
        row2.put("enc_col", "@enc:cipher-text");

        assertEquals(ConsistencyVerifier.rowHash(row1), ConsistencyVerifier.rowHash(row2),
                "column order/case must not affect the row hash");

        Map<String, Object> row3 = new LinkedHashMap<>(row1);
        row3.put("AMOUNT", new BigDecimal("10.51"));
        assertNotEquals(ConsistencyVerifier.rowHash(row1), ConsistencyVerifier.rowHash(row3),
                "value change must change the row hash");
    }

    @Test
    public void testStableHashIsJlsStable() {
        assertEquals("abc".hashCode(), ConsistencyVerifier.stableHash("abc"));
    }
}
