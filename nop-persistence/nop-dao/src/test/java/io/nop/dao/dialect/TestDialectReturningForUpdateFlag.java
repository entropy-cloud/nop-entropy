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
 * WI3 第 1/2/3/4/5 项：`supportReturningForUpdate` 配置断言（XML → 模型 → accessor 运行时连通）。
 *
 * <p>真实语义（roadmap WI3 节头 + `dialect.xdef` javadoc）：仅指 `UPDATE` 上返回
 * <em>投影列表形式</em>（无 `INTO` 宿主变量）的 returning 子句，不是"该库有没有 RETURNING"。
 * 授权：G-4 逐项批准（`ai-dev/design/eql-cross-db-compat/2026-10-03-wi0-gate-adjudications.md`），
 * 逐项裁定与回滚单位见 plan 20 Phase 1。
 *
 * <p>编译期门行为（负向用例/正向对照）由 nop-orm-eql 的
 * {@code TestEqlUpdateReturningDialectGate} 以真实方言验证；两模块测试合起来构成
 * "XML 配置值 → features → accessor → 门行为"的运行时连通证明。
 */
public class TestDialectReturningForUpdateFlag {

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
     * WI3 第 1 项：mysql `true` → `false`（MySQL 8.4 文法无 RETURNING；原误导注释
     * "mysql 8.0.14 和mariadb都支持returning语句"已随同一提交删除——该错误值的来源记录）。
     */
    @Test
    public void testMysqlExplicitFalse() {
        assertFalse(dialectOf("mysql").isSupportReturningForUpdate(),
                "MySQL has no UPDATE..RETURNING (projection-list form); see WI3 item 1");
    }

    /**
     * WI3 第 2 项：oracle `true` → `false`（Oracle 的 UPDATE … returning_clause 仅允许
     * INTO 宿主变量形式，生成器的投影列表形式非法）。
     */
    @Test
    public void testOracleExplicitFalse() {
        assertFalse(dialectOf("oracle").isSupportReturningForUpdate(),
                "Oracle UPDATE returning_clause only allows INTO host-variable form; see WI3 item 2");
    }

    /**
     * 继承涟漪：oracle 翻转经 x:extends 传播到 oracle-reverse（无本值配置，继承 oracle）。
     */
    @Test
    public void testInheritanceRippleFromOracle() {
        assertFalse(dialectOf("oracle-reverse").isSupportReturningForUpdate(),
                "oracle-reverse inherits oracle's explicit false");
    }

    /**
     * 继承涟漪：mysql 翻转经 x:extends 传播——mariadb（无本值配置，继承 mysql）、
     * mysql5.7（本就显式 false，双层证明）加载后有效值均为 false。
     * mariadb 的显式化（第 6 项，G-1 版本门形态）由 Phase 4 落地。
     */
    @Test
    public void testInheritanceRippleFromMysql() {
        assertFalse(dialectOf("mariadb").isSupportReturningForUpdate(),
                "mariadb inherits mysql's explicit false");
        assertFalse(dialectOf("mysql5.7").isSupportReturningForUpdate(),
                "mysql5.7 declares false itself and inherits mysql's false");
    }

    /**
     * WI3 第 3 项：h2 `true` → `false`（H2 五页文档零 RETURNING）；继承涟漪 h2gis 同步。
     */
    @Test
    public void testH2ExplicitFalseAndRipple() {
        assertFalse(dialectOf("h2").isSupportReturningForUpdate(),
                "H2 has no RETURNING clause at all; see WI3 item 3");
        assertFalse(dialectOf("h2gis").isSupportReturningForUpdate(),
                "h2gis inherits h2's explicit false");
    }

    /**
     * WI3 第 4 项（确认无需改动桶的防回归断言）：duckdb 保持 false——DuckDB 的 RETURNING
     * 仅在 INSERT/DELETE/MERGE 上，UPDATE 文档零 RETURNING，现值正确（v1 曾误判为 true）。
     */
    @Test
    public void testDuckdbConfirmedFalse() {
        assertFalse(dialectOf("duckdb").isSupportReturningForUpdate(),
                "DuckDB RETURNING exists only on INSERT/DELETE/MERGE; false is correct (WI3 item 4)");
    }

    /**
     * WI3 第 5 项（unverified 固化断言，不改值）：dm 保持 true 并显式标注 unverified。
     * <p>
     * ⚠️ UNVERIFIED：DM8 无公开一手文档（roadmap 红线「证据等级纪律」：禁止以未取证支撑
     * "已修正"结论），且本文件由 AI 仿 mysql 方言自动生成、未经实测（文件头自注）。
     * 现值 true 可能高估能力——DM8 的 UPDATE…RETURNING 疑为 INTO 宿主变量风格（同 Oracle），
     * 投影列表形式未取证。本断言固化现值防静默漂移；结论修订归 successor
     * （plan 20 Deferred But Adjudicated「dm RETURNING 语义取证」，取得一手文档或可实测环境后重裁定）。
     */
    @Test
    public void testDmUnverifiedValuePinned() {
        assertTrue(dialectOf("dm").isSupportReturningForUpdate(),
                "dm keeps true with explicit UNVERIFIED annotation (WI3 item 5); "
                        + "re-adjudicate when DM8 primary documentation becomes available");
    }
}
