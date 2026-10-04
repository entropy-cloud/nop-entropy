/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.commons.type;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9b：`StdSqlType` 时区两成员（`TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE`）
 * 落活后的三 map 不变量守护（plan 32 Phase 1；WI9a UUID 三 map 核验先例）。
 *
 * <p>裁定依据：`ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md` §三——
 * 成员形态 = `Types.OTHER` + `StdDataType.TIME/TIMESTAMP` + `(false,false)`；三 map 保持策略 =
 * 循环后三处显式重钉。修复前红锚（2026-10-05 实测 `_tmp/wi9b/stdnames-probe.log`）：
 * `fromStdName` 对两个时区名返回 null、`getNames()` SIZE=30。
 */
public class TestStdSqlTypeTimezoneMembers {

    @Test
    public void testNewMembersResolvedByStdName() {
        assertSame(StdSqlType.TIME_WITH_LOCAL_TIME_ZONE, StdSqlType.fromStdName("TIME_WITH_LOCAL_TIME_ZONE"));
        assertSame(StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE,
                StdSqlType.fromStdName("TIMESTAMP_WITH_LOCAL_TIME_ZONE"));
    }

    @Test
    public void testNewMemberShapeAdjudicated() {
        // 裁定文档 §三：Types.OTHER（2013/2014 被拒——真实驱动上两码表示保偏移类型）+ TIME/TIMESTAMP 归族 + (false,false)
        assertEquals(java.sql.Types.OTHER, StdSqlType.TIME_WITH_LOCAL_TIME_ZONE.getJdbcType());
        assertEquals(StdDataType.TIME, StdSqlType.TIME_WITH_LOCAL_TIME_ZONE.getStdDataType());
        assertEquals(java.sql.Types.OTHER, StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getJdbcType());
        assertEquals(StdDataType.TIMESTAMP, StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.getStdDataType());
        assertEquals(false, StdSqlType.TIME_WITH_LOCAL_TIME_ZONE.isAllowPrecision());
        assertEquals(false, StdSqlType.TIME_WITH_LOCAL_TIME_ZONE.isAllowScale());
        assertEquals(false, StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.isAllowPrecision());
        assertEquals(false, StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE.isAllowScale());
    }

    @Test
    public void testJdbcTypeMapInvariantsUnflipped() {
        // 先到先得键被重钉保持：OTHER 归 OTHER（不翻为时区成员）
        assertSame(StdSqlType.OTHER, StdSqlType.fromJdbcType(java.sql.Types.OTHER));
        // 候选 (a) 被拒的直接结果：2013/2014 保持未登记（null），真实驱动读回归类路径行为不变
        assertNull(StdSqlType.fromJdbcType(java.sql.Types.TIME_WITH_TIMEZONE));
        assertNull(StdSqlType.fromJdbcType(java.sql.Types.TIMESTAMP_WITH_TIMEZONE));
        // 既有键抽查不翻转（Types.TIMESTAMP 的 first-wins 归属 = 先声明的 DATETIME，历史行为）
        assertSame(StdSqlType.DATETIME, StdSqlType.fromJdbcType(java.sql.Types.TIMESTAMP));
        assertSame(StdSqlType.TIME, StdSqlType.fromJdbcType(java.sql.Types.TIME));
        assertSame(StdSqlType.VARCHAR, StdSqlType.fromJdbcType(java.sql.Types.VARCHAR));
    }

    @Test
    public void testStdTypeMapInvariantsUnflipped() {
        // last-wins 键被重钉保持：TIME/TIMESTAMP/DATETIME 归既有成员（不翻为时区成员）
        assertSame(StdSqlType.TIME, StdSqlType.fromStdDataTYpe(StdDataType.TIME));
        assertSame(StdSqlType.TIMESTAMP, StdSqlType.fromStdDataTYpe(StdDataType.TIMESTAMP));
        assertSame(StdSqlType.DATETIME, StdSqlType.fromStdDataTYpe(StdDataType.DATETIME));
        // 既有重钉行不回退
        assertSame(StdSqlType.VARCHAR, StdSqlType.fromStdDataTYpe(StdDataType.STRING));
        assertSame(StdSqlType.VARBINARY, StdSqlType.fromStdDataTYpe(StdDataType.BYTES));
    }

    @Test
    public void testFromJavaClassPathsUnchanged() {
        assertSame(StdSqlType.TIME, StdSqlType.fromJavaClass(LocalTime.class));
        assertSame(StdSqlType.DATETIME, StdSqlType.fromJavaClass(LocalDateTime.class));
        assertSame(StdSqlType.TIMESTAMP, StdSqlType.fromJavaClass(Timestamp.class));
    }

    @Test
    public void testGetNamesKeySet30To32() {
        Set<String> names = StdSqlType.getNames();
        // 裁定文档 §九附录：修复前 30 键（26 活成员 + INT/TEXT/NUMBER/BOOL 四别名）；落活后 32
        assertEquals(32, names.size());
        assertTrue(names.contains("TIME_WITH_LOCAL_TIME_ZONE"));
        assertTrue(names.contains("TIMESTAMP_WITH_LOCAL_TIME_ZONE"));
        // 既有别名/枚举键抽查（WI9a 底线）
        assertTrue(names.contains("INT"));
        assertTrue(names.contains("TEXT"));
        assertTrue(names.contains("NUMBER"));
        assertTrue(names.contains("BOOL"));
        assertTrue(names.contains("UUID"));
    }

    @Test
    public void testExistingAliasAndEnumLookupsUnchanged() {
        assertSame(StdSqlType.INTEGER, StdSqlType.fromStdName("INT"));
        assertSame(StdSqlType.CLOB, StdSqlType.fromStdName("TEXT"));
        assertSame(StdSqlType.DECIMAL, StdSqlType.fromStdName("NUMBER"));
        assertSame(StdSqlType.BOOLEAN, StdSqlType.fromStdName("BOOL"));
        assertSame(StdSqlType.UUID, StdSqlType.fromStdName("UUID"));
        assertSame(StdSqlType.TIME, StdSqlType.fromStdName("TIME"));
        assertSame(StdSqlType.TIMESTAMP, StdSqlType.fromStdName("TIMESTAMP"));
        assertNull(StdSqlType.fromStdName("NOSUCHTYPE"));
    }
}
