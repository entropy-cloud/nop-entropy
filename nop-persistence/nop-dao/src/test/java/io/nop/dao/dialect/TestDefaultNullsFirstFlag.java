/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect;

import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.impl.DialectImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI3 第 10/11 项：`defaultNullsFirst` accessor 契约 + 8 方言配置断言
 * （XML 根属性 → 模型 → accessor 运行时连通，Minimum Rules #23）。
 *
 * <p>概念边界（正交，非重复标志）：`defaultNullsFirst` = 缺省升序下 NULL 排前与否的
 * <em>事实</em>；`supportNullsFirst` = 是否原生接受 `NULLS FIRST/LAST` <em>子句</em>。
 * 本测试同时锁定 supportNullsFirst 四处现配置未被触碰。
 *
 * <p>⚠️ 消费点唯一 owner = roadmap WI7（文法接受 NULLS FIRST/LAST + supportNullsFirst
 * accessor 与门控 + defaultNullsFirst 默认序消费）。WI3 交付止于 accessor + 配置正确性，
 * 「暂无生成器消费方」是登记过的 successor 移交（plan 20 专节 + Deferred But Adjudicated），
 * 不是静默 no-op。
 */
public class TestDefaultNullsFirstFlag {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return dialect;
    }

    /**
     * 8 方言 accessor 断言（WI3 第 10 项）：mysql/mssql/dm 显式 true；
     * tdengine/duckdb/postgresql/oracle 显式 false；h2 为第 11 项补配后的 true。
     */
    @Test
    public void testEightDialectAccessorContract() {
        assertTrue(dialectOf("mysql").isDefaultNullsFirst(), "mysql defaultNullsFirst=true");
        assertTrue(dialectOf("mssql").isDefaultNullsFirst(), "mssql defaultNullsFirst=true");
        assertTrue(dialectOf("dm").isDefaultNullsFirst(), "dm defaultNullsFirst=true");
        assertTrue(dialectOf("h2").isDefaultNullsFirst(),
                "h2 default NULL ordering is LOW (NULLs first in ASC) — WI3 item 11补配");

        assertFalse(dialectOf("tdengine").isDefaultNullsFirst(), "tdengine defaultNullsFirst=false");
        assertFalse(dialectOf("duckdb").isDefaultNullsFirst(),
                "duckdb default_null_order=NULLS_LAST → false（与 supportNullsFirst=true 正交共存）");
        assertFalse(dialectOf("postgresql").isDefaultNullsFirst(),
                "postgresql: NULLs sort last in ASC by default → false");
        assertFalse(dialectOf("oracle").isDefaultNullsFirst(),
                "oracle: NULLs sort last in ASC by default → false");
    }

    /**
     * 继承面抽查：mysql5.7 经 mysql 继承 true；db2（未配置该属性，extends default）
     * 模型值为 null → accessor false（保守缺省语义与文档一致）。
     */
    @Test
    public void testInheritanceAndUnconfiguredDefault() {
        assertTrue(dialectOf("mysql5.7").isDefaultNullsFirst(),
                "mysql5.7 inherits mysql's true");
        assertFalse(dialectOf("db2").isDefaultNullsFirst(),
                "unconfigured attribute → model null → accessor false (conservative default)");
    }

    /**
     * supportNullsFirst 四处现配置未被触碰（正交标志的防误伤断言）。注意：
     * supportNullsFirst 的 accessor 归 WI7（WI3 不接），此处经模型层断言锁定 XML 现值。
     */
    @Test
    public void testSupportNullsFirstUntouched() {
        for (String name : new String[]{"duckdb", "h2", "postgresql", "oracle"}) {
            Boolean v = ((DialectImpl) dialectOf(name)).getDialectModel().getFeatures()
                    .getSupportNullsFirst();
            assertTrue(Boolean.TRUE.equals(v),
                    () -> name + " supportNullsFirst=true stays (WI7 owns its accessor)");
        }
    }
}
