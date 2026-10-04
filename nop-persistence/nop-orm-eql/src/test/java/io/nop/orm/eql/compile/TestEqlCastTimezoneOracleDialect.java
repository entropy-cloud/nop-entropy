/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.dialect.model.SqlDataTypeModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoErrors.ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9b（plan 32 Phase 3）：oracle 真方言载体——`TIMESTAMP_WITH_LOCAL_TIME_ZONE` 的唯一
 * "支持"方言（裁定文档 `ai-dev/design/eql-cross-db-compat/2026-10-05-wi9b-phase0-adjudications.md`
 * §六：候选 (a) 零撞名纯新增键；Oracle 23ai SQL Language Reference 一手文档）。
 *
 * <p>断言链按裁定 §6.3 四层设计：
 * ① std→native 渲染层（`cast` 端到端 → `TIMESTAMP WITH LOCAL TIME ZONE`）；
 * ② native→std 反查层（`getNativeType` 裸名 → LOCAL 成员，往返归类同一性；带 precision 既有
 * alias 仍归 std `TIMESTAMP`——既有键零改写）；③ 负向响亮层见
 * {@code TestEqlCastTimezoneTypeCompile}（h2/最小方言/oracle-TIME 成员）；
 * ④ 真实 Oracle 引擎往返 `[未取证]`（CI 真库仅 H2，H2 2.4.240 实测不支持 LOCAL 形态建表）。
 *
 * <p>期望渲染文本按 `stdToNativeSqlType` 链路推导：oracle 标识符小写渲染 ⇒ 键部分小写比对
 * （WI9a oracle NUMBER 用例同口径）。
 */
public class TestEqlCastTimezoneOracleDialect {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static IDialect oracle() {
        IDialect dialect = DialectManager.instance().getDialect("oracle");
        assertNotNull(dialect, "oracle dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    // ---- ① std→native 渲染层（真实 oracle 方言 XML 条目） ----

    @Test
    public void testOracleSpelledTimestampWithLocalTimeZoneRenders() {
        String sql = new EqlCompiler().compile("test", "select cast(o.id as timestamp with local time zone) from AppDept o",
                TestEqlCastTypeAliasCompile.contextOf(oracle())).getSql().getText().replaceAll("\\s+", " ");
        assertTrue(sql.toLowerCase().contains("cast(o.sid as timestamp with local time zone)"), sql);
    }

    @Test
    public void testOracleUnderscoreSpellingRendersIdentically() {
        // 约束 4 一致性：下划线拼写（sqlIdentifier_ 兜底）与多词拼写同方言输出逐字一致
        IDialect oracle = oracle();
        TestEqlCastTypeAliasCompile.TestCompileContext ctx = TestEqlCastTypeAliasCompile.contextOf(oracle);
        String viaUnderscore = new EqlCompiler().compile("test",
                "select cast(o.id as timestamp_with_local_time_zone) from AppDept o", ctx).getSql().getText();
        String viaSpelled = new EqlCompiler().compile("test",
                "select cast(o.id as timestamp with local time zone) from AppDept o", ctx).getSql().getText();
        assertEquals(viaSpelled, viaUnderscore);
    }

    @Test
    public void testOraclePrecisionAfterSuffixRendersSameAsBare() {
        // 成员 (false,false) ⇒ precision 后置形态与 bare 渲染逐字一致
        IDialect oracle = oracle();
        TestEqlCastTypeAliasCompile.TestCompileContext ctx = TestEqlCastTypeAliasCompile.contextOf(oracle);
        String bare = new EqlCompiler().compile("test",
                "select cast(o.id as timestamp with local time zone) from AppDept o", ctx).getSql().getText();
        String withPrecision = new EqlCompiler().compile("test",
                "select cast(o.id as timestamp with local time zone(6)) from AppDept o", ctx).getSql().getText();
        assertEquals(bare, withPrecision);
    }

    // ---- ② native→std 反查层（往返归类同一性 + 既有键零改写） ----

    @Test
    public void testOracleReverseLookupBareNameRoundTripsToSameMember() {
        // 往返归类同一性：std 成员 → 渲染名 → 反查回同一 std 成员
        SqlDataTypeModel model = oracle().getNativeType("TIMESTAMP WITH LOCAL TIME ZONE");
        assertNotNull(model, "bare LOCAL name must be a registered key after WI9b Phase 3");
        assertEquals(StdSqlType.TIMESTAMP_WITH_LOCAL_TIME_ZONE, model.getStdSqlType());
    }

    @Test
    public void testOracleReverseLookupPrecisionQualifiedAliasUnchanged() {
        // 既有键零改写：带 precision 的 LOCAL 名仍归 std TIMESTAMP（WI9b 前行为）
        SqlDataTypeModel model = oracle().getNativeType("TIMESTAMP(6) WITH LOCAL TIME ZONE");
        assertNotNull(model);
        assertEquals(StdSqlType.TIMESTAMP, model.getStdSqlType());
    }

    @Test
    public void testOracleReverseLookupPlainTimestampUnchanged() {
        // 既有键零改写：native TIMESTAMP 仍归 std TIMESTAMP
        SqlDataTypeModel model = oracle().getNativeType("TIMESTAMP");
        assertNotNull(model);
        assertEquals(StdSqlType.TIMESTAMP, model.getStdSqlType());
    }

    // ---- ③ 逐成员精确支持面（TIME 成员在 oracle 仍响亮报错） ----

    @Test
    public void testOracleTimeMemberStillFailsLoudly() {
        NopException e = assertThrows(NopException.class,
                () -> TestEqlCastTypeAliasCompile.sqlOf(oracle(),
                        "select cast(o.id as time with local time zone) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }
}
