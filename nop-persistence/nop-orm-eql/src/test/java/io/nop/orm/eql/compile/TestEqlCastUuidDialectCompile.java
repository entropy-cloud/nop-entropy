/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.type.StdSqlType;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoErrors.ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI9a：`UUID`（新 {@link StdSqlType} 枚举常量）的方言正/负向用例。
 *
 * <p>裁定依据：`ai-dev/design/eql-cross-db-compat/2026-10-04-wi9a-phase0-adjudications.md`
 * §四——UUID 为新闭集成员（非别名），h2/postgresql/duckdb 有原生 UUID 类型、mssql 为
 * UNIQUEIDENTIFIER（code 渲染），四方言补 {@code <sqlDataType>} 映射（逐条独立提交）；
 * mysql/oracle/tdengine/db2 无原生 UUID/无映射 ⇒ "不支持"真实方言载体。
 *
 * <p>负向用例（本文件初始集）：无映射方言解析过、生成期响亮报
 * {@code ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED}，不静默降级/改写。
 */
public class TestEqlCastUuidDialectCompile {

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
        assertNotNull(dialect, name + " dialect must be loadable in nop-orm-eql test env");
        return dialect;
    }

    static String sqlOf(IDialect dialect, String eql) {
        return TestEqlCastTypeAliasCompile.sqlOf(dialect, eql);
    }

    /**
     * 手工最小 dialect：无任何 sqlDataType 映射（TestEqlCastTypeAliasCompile.minimalDialect 复用）。
     */
    static IDialect minimalDialect() {
        return TestEqlCastTypeAliasCompile.minimalDialect();
    }

    // ---- "支持"载体（原生 UUID 类型，逐方言独立提交携带） ----

    @Test
    public void testH2UuidCastGeneratesUuid() {
        String sql = sqlOf(dialectOf("h2"), "select cast(o.id as uuid) from AppDept o");
        assertTrue(sql.contains("cast(o.SID as UUID)"), sql);
    }

    @Test
    public void testPostgresqlUuidCastGeneratesUuid() {
        String sql = sqlOf(dialectOf("postgresql"), "select cast(o.id as uuid) from AppDept o");
        assertTrue(sql.toLowerCase().contains("cast(o.sid as uuid)"), sql);
    }

    // ---- "不支持"载体（真实方言无 UUID 映射 + 手工最小方言） ----

    @Test
    public void testMySqlUuidFailsLoudlyNoMapping() {
        // mysql 无原生 UUID 类型 ⇒ 方言配置无 UUID 映射：解析过、生成期响亮报错
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectOf("mysql"), "select cast(o.id as uuid) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testOracleUuidFailsLoudlyNoMapping() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(dialectOf("oracle"), "select cast(o.id as uuid) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMinimalDialectUuidFailsLoudly() {
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(minimalDialect(), "select cast(o.id as uuid) from AppDept o"));
        assertEquals(ERR_DIALECT_STD_DATA_TYPE_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testMinimalDialectUuidErrorParamCarriesUuidName() {
        // 错误 param 携带 std 类型名 UUID（不静默吞掉目标类型信息）
        NopException e = assertThrows(NopException.class,
                () -> sqlOf(minimalDialect(), "select cast(o.id as uuid) from AppDept o"));
        assertEquals("UUID", e.getParam("dataType"));
    }
}
