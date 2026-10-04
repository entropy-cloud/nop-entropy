/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2308 Phase 5：{@code supportNullsFirst} 能力位的<b>取值守护</b>。
 *
 * <p>存在理由：该位从Phase 2 起被真正读取（此前全仓库 0 处读取），一旦某个方言的取值
 * 被误改，emission 会静默走向另一条分支——方言说支持就直出、不支持就改写，
 * <b>不会报错</b>。因此取值必须由常驻测试钉住，让漂移在 CI 就暴露，而不是等到
 * 某个库上的排序结果诡异时才被发现。
 *
 * <p>取值证据分级（每格必须可追溯到其中之一）：
 * <ul>
 *   <li><b>实测</b>——{@code ai-dev/tools/probe-nulls-order-matrix.sh} 在真实容器上执行过
 *       四个方向组合并核对行序。该脚本的方言分发只含 {@code postgresql | mysql | h2}，
 *       目前：<b>h2 / postgresql / mysql</b>。</li>
 *   <li><b>方言文档</b>——未跑容器，但该库官方文档明确写了支持
 *       {@code NULLS FIRST/LAST}。目前：oracle。</li>
 *   <li><b>保守缺省 false</b>——未实测且无文档依据，留 false。
 *       <b>false 在任何方言上都语义正确</b>（走 CASE 改写），故此项只影响 SQL 冗长度，
 *       不构成正确性风险。这正是把该位设计成单能力位而非完整空序模型的好处。</li>
 * </ul>
 *
 * <p>范式对齐 {@code TestDefaultDialectWindowFeatures}（同为「真实方言绑定 + fixture 覆盖」
 * 的守护型测试）。
 */
public class TestDefaultDialectNullsFirstFeatures {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect dialect(String name) {
        IDialect d = DialectManager.instance().getDialect(name);
        assertNotNull(d, "dialect not loaded: " + name);
        return d;
    }

    // ---------- 实测（probe-nulls-order-matrix.sh，真实容器四方向组合核对行序） ----------

    @Test
    public void testH2SupportsNativeNullsOrder() {
        assertTrue(dialect("h2").isSupportNullsFirst(),
                "H2 2.3.232 实测原生接受 NULLS FIRST/LAST");
    }

    @Test
    public void testPostgresqlSupportsNativeNullsOrder() {
        assertTrue(dialect("postgresql").isSupportNullsFirst(),
                "PostgreSQL 16.15 实测原生接受 NULLS FIRST/LAST");
    }

    @Test
    public void testMySqlDoesNotSupportNativeNullsOrder() {
        // MySQL 8.0.46 不接受 NULLS 子句——置 true 会直接产出语法错误的 SQL
        assertFalse(dialect("mysql").isSupportNullsFirst(),
                "MySQL 8.0.46 实测不接受 NULLS 子句，必须走 CASE 改写");
    }

    @Test
    public void testDuckdbSupportsNativeNullsOrder() {
        // 证据等级：官方文档 + 本仓库既有声明，**非本计划实测**（探针脚本不含 duckdb 分支）。
        // 取值 true 是既有声明，非本计划引入。
        assertTrue(dialect("duckdb").isSupportNullsFirst(),
                "DuckDB 官方文档支持 NULLS 子句。注意它 ASC/DESC 都不翻转缺省，"
                        + "故省略子句的优化对本方言不安全——见 design §3.8");
    }

    // ---------- 方言文档依据（未跑容器） ----------

    @Test
    public void testOracleSupportsNativeNullsOrder() {
        // Oracle 官方文档明确支持 NULLS FIRST/LAST；该 true 为本计划之前既有声明，
        // 非本计划所设。未实测属已知残余（见 plan Non-Blocking Follow-ups），
        // 但取值方向正确，不会产出语法错误 SQL。
        assertTrue(dialect("oracle").isSupportNullsFirst());
    }

    // ---------- 派生方言继承（x:extends 后该位一并继承） ----------

    /**
     * DB2 LUW 原生支持 ORDER BY ... NULLS FIRST/LAST。
     *
     * <p>依据：IBM 官方文档 DB2 11.5.x「order-by-clause」明确列出
     * 「NULLS FIRST — When ordering rows in ascending or descending order, list null
     * values before all other values」「NULLS LAST — ... list null values after all
     * other values」。本仓库 db2.dialect.xml 的 {@code dbProductNames} 为 DB2，
     * 且 errorCodes 用 LUW 风格 SQLCODE（-104 / -911），故按 LUW 判定。
     *
     * <p>此前该位缺省为 false（2308 因无 DB2 环境未能实测而保守留 false），
     * 导致 DB2 上的 ORDER BY 空序走 CASE 改写——语义虽正确，但属无谓的 SQL 冗余，
     * 且聚合查询路径会直接 fail-fast。经用户指出后改为 true。
     *
     * <p>注：DB2 LUW 的缺省空序与 PostgreSQL 相反（文档：null 值高于所有其他值，
     * 故 ASC 缺省为 NULLS LAST、DESC 为 NULLS FIRST）。这正是本框架**不做省略优化**的原因
     * ——见 design §3.8 关于 DuckDB 的说明。单布尔无法表达这类模型。
     */
    @Test
    public void testDb2SupportsNativeNullsOrder() throws Exception {
        // db2.dialect.xml 缺少 mandatory 的 driverClassName，无法作为具名方言经
        // DialectManager 单独加载（与 default.dialect.xml 同款限制），故直接断言 xml 取值。
        // 守护目标不变：取值被改错时本用例转红。
        String xml = ResourceHelper.readText(
                VirtualFileSystem.instance().getResource("/nop/dao/dialect/db2.dialect.xml"))
                .replaceAll("\\s+", " ");
        assertTrue(xml.contains("supportNullsFirst=\"true\""),
                "DB2 LUW 官方文档明确支持 ORDER BY ... NULLS FIRST/LAST，取值应为 true: " + xml);
    }

    @Test
    public void testDerivedDialectsInheritFromBase() {
        // h2gis extends h2、postgis extends postgresql、oracle-reverse extends oracle。
        // 三者均未单独声明该位，故继承为 true——顺带证明 extends 的按属性合并没有把
        // features 整块替换掉。
        assertTrue(dialect("h2gis").isSupportNullsFirst(), "继承 h2 的 true");
        assertTrue(dialect("postgis").isSupportNullsFirst(), "继承 postgresql 的 true");
        assertTrue(dialect("oracle-reverse").isSupportNullsFirst(), "继承 oracle 的 true");
    }

    // ---------- 保守缺省 false（未实测；false 在任何方言上都语义正确） ----------

    @Test
    public void testUnverifiedDialectsConservativelyDefaultToFalse() {
        for (String name : new String[]{"mariadb", "mysql5.7", "dm", "tdengine", "es"}) {
            assertFalse(dialect(name).isSupportNullsFirst(),
                    name + " 未实测且无文档依据，保守 false（走 CASE 改写，语义仍正确）");
        }
    }

    // 注：db2.dialect.xml 与 default.dialect.xml 缺少 mandatory 的 driverClassName，
    // 无法作为具名方言单独加载，故不在此断言其取值。二者均为 x:extends 的基类，
    // 其 features 由派生方言继承，drift 由派生方言的断言间接覆盖。

    // ---------- null 回落语义 ----------

    // 注：default.dialect.xml 是被其他方言 x:extends 的基类，缺少 mandatory 的
    // driverClassName，不能作为具名方言单独加载，故不在此断言其取值。

    @Test
    public void testAbsentAttributeFallsBackToFalse() {
        // mysql.dialect.xml 未声明 supportNullsFirst，DialectFeatures 的 Boolean 为 null，
        // DialectImpl 必须回落为 false 而不是 NPE 或 true
        assertFalse(dialect("mysql").isSupportNullsFirst());
    }

    @Test
    public void testFixtureOverridesToFalseAndInheritsOthers() {
        // fixture extends h2（true）后显式置 false：验证按属性（非整块）合并语义，
        // 与 TestDefaultDialectWindowFeatures 的 rows fixture 同一范式
        IDialect d = dialect("test-nulls-features");
        assertNotNull(d, "fixture dialect not loaded");
        assertFalse(d.isSupportNullsFirst(), "fixture 显式覆盖为 false");
        // 同族能力位仍应继承 h2 的取值，证明不是整块替换
        assertTrue(d.isSupportSequence(), "未覆盖的属性应继承 h2");
    }
}