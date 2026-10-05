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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI10b BUG-13 死标志三档处置：`IDialect` accessor 层与方言 XML 的配置断言。
 *
 * <p>裁定（`ai-dev/design/eql-cross-db-compat/2026-10-04-wi10b-phase0-adjudications.md`
 * §1/§2/§3）：本 plan 方言 XML **零改值**，全部断言锁定既有配置与 accessor 链的连通。
 * 编译期门行为由 nop-orm-eql 的 `TestEqlRightJoinDialectGate`/
 * `TestEqlRowValueConstructorGate`/`TestEqlTableAliasSuppressionContract` 验证。
 */
public class TestDialectDeadFlagDisposition {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /* default/geo-support/window-expr-support 三个基础模板不可实例化（WI5 Phase 0 实测口径：
       15 个可加载方言），继承 default 的断言全部用真实方言承载。 */
    static IDialect dialectOf(String name) {
        IDialect dialect = DialectManager.instance().getDialect(name);
        assertTrue(dialect instanceof DialectImpl, () -> name + " must resolve to DialectImpl");
        return dialect;
    }

    /**
     * §2.5 接线档：isSupportRightJoin accessor 落地；全部方言继承 default 的 true
     * （RIGHT JOIN 为全支持标准能力，无任何真实方言应配 false 的一手证据）。
     */
    @Test
    public void testRightJoinAllDialectsTrue() {
        for (String name : new String[]{"mysql", "mysql5.7", "mariadb", "postgresql", "postgis",
                "oracle", "mssql", "h2", "duckdb", "tdengine", "dm"}) {
            assertTrue(dialectOf(name).isSupportRightJoin(), () -> name + " inherits default true");
        }
    }

    /**
     * §2.6 接线档：supportRowValueConstructor 全部方言继承 default 的 true
     * （行值构造器编译期门只对显式 false 的部署生效，真实方言零 BC）。
     */
    @Test
    public void testRowValueConstructorAllDialectsTrue() {
        for (String name : new String[]{"mysql", "postgresql", "oracle", "mssql", "h2", "duckdb"}) {
            assertTrue(dialectOf(name).isSupportRowValueConstructor(), () -> name + " inherits default true");
        }
    }

    /**
     * §2.4 档 2：isSupportDeleteFromJoin accessor 落地；既有配置值不变（default false，
     * 全部继承）。未来 DELETE 多表文法 WI 立项时必须接线本标志（xdef javadoc 注记）。
     */
    @Test
    public void testDeleteFromJoinConfigUnchanged() {
        assertFalse(dialectOf("mysql").isSupportDeleteFromJoin(), "inherits default false");
        assertFalse(dialectOf("mysql").isSupportDeleteFromJoin(), "mysql inherits default false");
        assertFalse(dialectOf("mssql").isSupportDeleteFromJoin(), "mssql inherits default false");
    }

    /**
     * §2.2 档 2：supportUpdateFromJoin 配置值不变（default false，全部继承；
     * accessor 既有）。EQL 文法暂无 UPDATE..FROM（文法契约测试在 nop-orm-eql
     * `TestEqlDeadFlagGrammarContract`）。
     */
    @Test
    public void testUpdateFromJoinConfigUnchanged() {
        assertFalse(dialectOf("mysql").isSupportUpdateFromJoin(), "inherits default false");
        assertFalse(dialectOf("mysql").isSupportUpdateFromJoin(), "mysql inherits default false");
        assertFalse(dialectOf("mssql").isSupportUpdateFromJoin(), "mssql inherits default false");
    }

    /**
     * §2.3 档 2：supportTruncateTable 为 JDBC/DAO 层能力声明（EQL 永不消费——roadmap
     * ledger 永久否决 TRUNCATE 进 EQL）；配置值不变：default true，全部方言继承（无任何
     * XML 覆写该键）。
     */
    @Test
    public void testTruncateTableConfigUnchanged() {
        assertTrue(dialectOf("mysql").isSupportTruncateTable(), "mysql inherits default true");
        assertTrue(dialectOf("tdengine").isSupportTruncateTable(), "tdengine inherits default true");
        assertTrue(dialectOf("mssql").isSupportTruncateTable(), "mssql inherits default true");
    }

    /**
     * §2.7 档 2（候选 B）：isSupportOutputForUpdate accessor 落地；唯一 true 载体 = mssql，
     * 其余继承 default false。运行时消费点豁免已记录（v1 无 MSSQL OUTPUT lowering）；
     * 升级触发条件 = EQL 立项 MSSQL OUTPUT 语义（文法 WI，plan-first）。
     */
    @Test
    public void testOutputForUpdateOnlyMssqlTrue() {
        assertTrue(dialectOf("mssql").isSupportOutputForUpdate(), "mssql explicit true");
        for (String name : new String[]{"mysql", "postgresql", "oracle", "h2", "duckdb"}) {
            assertFalse(dialectOf(name).isSupportOutputForUpdate(), () -> name + " inherits default false");
        }
    }

    /**
     * §3.1 改档（档 2）：别名两标志全部方言显式或继承 false；EQL 的实际行为是
     * "接受并归一化"（编译行为测试在 nop-orm-eql `TestEqlTableAliasSuppressionContract`）。
     * 编译期门已被裁定否决（全方言 false ⇒ 门=拒绝语义无损写法，纯 BC 破坏零收益）。
     */
    @Test
    public void testTableAliasFlagsAllFalse() {
        for (String name : new String[]{"mysql", "postgresql", "oracle", "mssql", "h2", "duckdb", "tdengine"}) {
            assertFalse(dialectOf(name).isSupportUpdateTableAlias(), () -> name + " update alias false");
            assertFalse(dialectOf(name).isSupportDeleteTableAlias(), () -> name + " delete alias false");
        }
    }

    /**
     * 软弃用 3 个（§1）：xdef 属性保留、配置照常加载——既有值回归（default:
     * savePoint=true / transaction=true / sequence=false）证明软弃用标注未破坏绑定行为。
     */
    @Test
    public void testSoftDeprecatedFlagsStillBind() {
        assertTrue(dialectOf("mysql").isSupportSavePoint(), "default supportSavePoint=true inherited unchanged");
        assertTrue(dialectOf("mysql").isSupportTransaction(), "default supportTransaction=true inherited unchanged");
        assertFalse(dialectOf("mysql").isSupportSequence(), "default supportSequence=false inherited unchanged");
        assertTrue(dialectOf("h2").isSupportSequence(), "h2 supportSequence=true unchanged");
        assertFalse(dialectOf("tdengine").isSupportSequence(), "tdengine supportSequence=false unchanged");
    }

    /**
     * §2.1 档 2（supportILike 契约登记）：标志与函数表**互不强约束**——
     * postgresql 继承 supportILike=false 但 WI3 已注册 ilike 函数且真实可用
     * （接标志为第二道门会引入假阴性，裁定禁止）；h2 两者皆备；mysql 两者皆无
     * （响亮点在函数表：`nop.err.eql.not-support-ilike-operator`）。
     */
    @Test
    public void testILikeFlagAndFunctionRegistrationIndependent() {
        assertTrue(dialectOf("h2").isSupportILike(), "h2 explicit true");
        assertNotNull(dialectOf("h2").getFunction("ilike"), "h2 has ilike function (WI3)");

        assertFalse(dialectOf("postgresql").isSupportILike(), "postgresql inherits default false");
        assertNotNull(dialectOf("postgresql").getFunction("ilike"),
                "postgresql has ilike function despite flag=false (WI3, contract-locked)");

        assertFalse(dialectOf("mysql").isSupportILike(), "mysql inherits default false");
        assertNull(dialectOf("mysql").getFunction("ilike"), "mysql has no ilike function - loud via function table");
    }
}
