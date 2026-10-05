/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.commons.type.StdSqlType;
import io.nop.dao.dialect.impl.DialectImpl;
import io.nop.dao.dialect.model.DialectFeatures;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.dao.dialect.model.DialectSqls;
import io.nop.dao.dialect.model.SqlNativeFunctionModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2308 Phase 2 / D1：{@code supportNullsFirst} 能力位接线。
 *
 * <p>该属性此前在 {@code dialect.xdef} 与 4 个方言 xml 中已声明/设值，但 Java 侧零读取方
 * （"声明即死"）。本类钉住它成为活能力位后的行为，尤其是
 * <b>{@code null}（未声明）必须等价于 false</b> 这一保守缺省。
 *
 * <p>为什么 null→false 是安全的：false 时 EQL 侧走 {@code CASE WHEN ...} 改写，
 * 改写只用到 SQL 标准普遍支持的 {@code CASE WHEN} 与 {@code IS NULL}，对任何方言都语义正确；
 * false 只会让 SQL 多一个 CASE 键，不会产出错误结果。反之若把 null 当 true，
 * 在 MySQL/MariaDB 上会直接产出语法错误的 {@code NULLS FIRST}。
 */
public class TestDialectSupportNullsFirst {

    static DialectModel model(Boolean supportNullsFirst) {
        DialectModel m = new DialectModel();
        DialectFeatures features = new DialectFeatures();
        if (supportNullsFirst != null)
            features.setSupportNullsFirst(supportNullsFirst);
        m.setFeatures(features);
        m.setReservedKeywords(Collections.emptySet());
        m.setSqls(new DialectSqls());
        m.setSqlDataTypes(Collections.emptyList());
        // DialectImpl 构造末尾调用 buildFunctionSql("current_timestamp")，缺该函数会 NPE
        SqlNativeFunctionModel currentTimestamp = new SqlNativeFunctionModel();
        currentTimestamp.setName("current_timestamp");
        currentTimestamp.setHasParenthesis(false);
        currentTimestamp.setReturnType(StdSqlType.TIMESTAMP);
        m.setFunctions(Collections.singletonList(currentTimestamp));
        return m;
    }

    // ---- 核心三态 ----

    @Test
    public void testExplicitTrueIsHonored() {
        assertTrue(new DialectImpl(model(true)).isSupportNullsFirst());
    }

    @Test
    public void testExplicitFalseIsHonored() {
        assertFalse(new DialectImpl(model(false)).isSupportNullsFirst());
    }

    @Test
    public void testUnsetIsFalseNotAnError() {
        // 保守缺省：未声明 -> false（走 CASE 改写），绝不是 true
        DialectImpl dialect = new DialectImpl(model(null));
        assertFalse(dialect.isSupportNullsFirst(),
                "未声明 supportNullsFirst 必须等价于 false，否则会在 MySQL 上产出非法 NULLS FIRST");
    }

    @Test
    public void testUnsetDoesNotThrow() {
        // 反证：这是"缺失即回落"而不是"缺失即失败"
        assertFalse(new DialectImpl(model(null)).isSupportNullsFirst());
    }

    // ---- 三态 × 两个方言实例独立 ----

    @ParameterizedTest
    @CsvSource({"true,true", "false,false"})
    public void testDeclaredTriStateMapping(Boolean declared, boolean expected) {
        assertEquals(expected, new DialectImpl(model(declared)).isSupportNullsFirst());
    }

    @Test
    public void testUnsetTriStateMapping() {
        // CsvSource 无法把 "null" 转成 Boolean，null 档单独用 @Test 覆盖
        assertFalse(new DialectImpl(model(null)).isSupportNullsFirst());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAccessorIsStableAcrossCalls(boolean declared) {
        DialectImpl dialect = new DialectImpl(model(declared));
        assertEquals(dialect.isSupportNullsFirst(), dialect.isSupportNullsFirst());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testTwoDialectInstancesDoNotShareState(boolean declared) {
        DialectImpl a = new DialectImpl(model(declared));
        DialectImpl b = new DialectImpl(model(!declared));
        assertEquals(declared, a.isSupportNullsFirst());
        assertEquals(!declared, b.isSupportNullsFirst(),
                "两个方言实例的取值不应互相污染");
    }

    // ---- 与既有能力位共存（回归钉） ----

    @Test
    public void testOtherFeatureFlagsUnaffected() {
        DialectModel m = model(true);
        m.getFeatures().setSupportWindowFrameRows(true);
        DialectImpl dialect = new DialectImpl(m);
        assertTrue(dialect.isSupportNullsFirst());
        assertTrue(dialect.isSupportWindowFrameRows(),
                "新增能力位不得影响既有窗口 frame 能力位");
    }

    @Test
    public void testWindowFrameFlagsStillNullSafe() {
        // 既有能力位同样把 null 当 false —— 新能力位与它们保持一致的语义
        DialectImpl dialect = new DialectImpl(model(null));
        assertFalse(dialect.isSupportWindowFrameRows());
        assertFalse(dialect.isSupportWindowFrameRange());
        assertFalse(dialect.isSupportWindowFrameGroups());
    }
}