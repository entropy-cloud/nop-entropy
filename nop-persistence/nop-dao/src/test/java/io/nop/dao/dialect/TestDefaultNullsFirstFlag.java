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
 *
 * <p>WI7（roadmap EC ③b）已兑现移交链：`supportNullsFirst` accessor 落地
 * （`IDialect.isSupportNullsFirst()`），消费点 = `AstToSqlGenerator.visitSqlOrderByItem`
 * 的子句输出唯一门。本测试的 `testSupportNullsFirstUntouched` 自 WI7 Phase 2 起从模型层
 * 断言升级为 accessor 双侧契约断言（true 侧 4 方言显式 true；false 侧 mysql/tdengine
 * features 未配置 → 模型 null → accessor false，`Boolean.TRUE.equals` 保守缺省语义钉死）。
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
     * supportNullsFirst accessor 契约 true 侧（WI7 EC ③b）：duckdb/h2/postgresql/oracle
     * 显式配置 true ⇒ accessor true（消费点 = AstToSqlGenerator 子句输出门按请求打印）。
     * 原 WI3 版本为模型层断言，WI7 Phase 2 随 accessor 落地升级（断言值不变、不弱化）。
     */
    @Test
    public void testSupportNullsFirstUntouched() {
        for (String name : new String[]{"duckdb", "h2", "postgresql", "oracle"}) {
            assertTrue(dialectOf(name).isSupportNullsFirst(),
                    () -> name + " supportNullsFirst=true accessor contract (WI7 EC ③b)");
        }
    }

    /**
     * supportNullsFirst accessor 契约 false 侧（WI7 EC ③b）：mysql/tdengine 的 features
     * **未配置** supportNullsFirst ⇒ 模型 null → accessor false（`Boolean.TRUE.equals`
     * 保守缺省语义钉死——防"缺配置被读成 true"或"缺配置走不进门控报错分支"回归；
     * false 侧方言显式 nullsFirst 请求走省略/报错分支）。
     */
    @Test
    public void testSupportNullsFirstFalseSideUnconfiguredDialects() {
        for (String name : new String[]{"mysql", "tdengine"}) {
            assertFalse(dialectOf(name).isSupportNullsFirst(),
                    () -> name + " unconfigured supportNullsFirst → model null → accessor false"
                            + " (conservative default, WI7 EC ③b false side)");
        }
    }
}
