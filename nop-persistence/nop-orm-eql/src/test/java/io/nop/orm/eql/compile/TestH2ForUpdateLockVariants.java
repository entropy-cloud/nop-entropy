/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.dao.dialect.DialectManager;
import io.nop.dao.dialect.IDialect;
import io.nop.orm.eql.ICompiledSql;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 交付②：`FOR UPDATE NOWAIT` / `FOR UPDATE SKIP LOCKED` 的生产方言配置面 + H2 真库
 * 执行证据（plan 38 Phase 2；证据分级 = h2 [实测-jar]+[实测-真库]、pg/mysql [文档]）。
 *
 * <p>负向载体（裁定文档 §2 矩阵）：mssql（T-SQL 无 FOR UPDATE 子句变体体系）、
 * duckdb（无悲观锁 + 显式空覆盖）、mysql5.7（8.0.1 才引入 NOWAIT/SKIP LOCKED，
 * 继承 mysql 后显式空覆盖关断——继承链关断面的直接证明）。
 */
public class TestH2ForUpdateLockVariants {
    static final String NOWAIT_EQL = "select o.id from AppUser o where o.name = 'a' for update nowait";
    static final String SKIP_LOCKED_EQL = "select o.id from AppUser o where o.name = 'a' for update skip locked";
    static final String PLAIN_EQL = "select o.id from AppUser o where o.name = 'a' for update";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    static String sqlOf(String dialectName, String eql) {
        IDialect dialect = DialectManager.instance().getDialect(dialectName);
        assertNotNull(dialect, dialectName);
        ICompiledSql compiled = new EqlCompiler().compile("test", eql,
                TestH2WindowFrameCompileEndToEnd.newContext(dialect));
        return compiled.getSql().getText().replaceAll("\\s+", " ").trim();
    }

    @Test
    public void testH2ConfigBothVariants() {
        // [实测-jar] 形态：h2 [ FOR UPDATE [ NOWAIT | WAIT secondsNumeric | SKIP LOCKED ] ]
        assertTrue(sqlOf("h2", NOWAIT_EQL).endsWith("for update nowait"));
        assertTrue(sqlOf("h2", SKIP_LOCKED_EQL).endsWith("for update skip locked"));
        assertTrue(sqlOf("h2", PLAIN_EQL).endsWith("for update"));
    }

    @Test
    public void testPostgresqlConfigBothVariants() {
        // [文档] PG locking clause FOR lock_strength [ OF … ] [ NOWAIT | SKIP LOCKED ]
        assertTrue(sqlOf("postgresql", NOWAIT_EQL).endsWith("for update nowait"));
        assertTrue(sqlOf("postgresql", SKIP_LOCKED_EQL).endsWith("for update skip locked"));
    }

    @Test
    public void testMssqlNegativeBothVariants() {
        // [文档] T-SQL SELECT 无 FOR UPDATE 子句变体（锁提示 WITH(UPDLOCK,READPAST) 体系）
        NopException e1 = assertThrows(NopException.class, () -> sqlOf("mssql", NOWAIT_EQL));
        assertEquals(ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE.getErrorCode(), e1.getErrorCode());
        assertEquals("forUpdateNowait", e1.getParam("feature"));

        NopException e2 = assertThrows(NopException.class, () -> sqlOf("mssql", SKIP_LOCKED_EQL));
        assertEquals("forUpdateSkipLocked", e2.getParam("feature"));
    }

    @Test
    public void testDuckDbNegativeBothVariants() {
        // duckdb 无悲观锁：<forUpdate>/<forUpdateNowait>/<forUpdateSkipLocked> 全空
        NopException e1 = assertThrows(NopException.class, () -> sqlOf("duckdb", NOWAIT_EQL));
        assertEquals("forUpdateNowait", e1.getParam("feature"));

        NopException e2 = assertThrows(NopException.class, () -> sqlOf("duckdb", SKIP_LOCKED_EQL));
        assertEquals("forUpdateSkipLocked", e2.getParam("feature"));
    }

    @Test
    public void testMysql57ExplicitOffNegative() {
        // 继承链显式关断面：mysql5.7 extends mysql（8.0 才有 NOWAIT/SKIP LOCKED），
        // x:override="replace" 空覆盖后与未配置同口径——编译期响亮而非静默继承
        NopException e1 = assertThrows(NopException.class, () -> sqlOf("mysql5.7", NOWAIT_EQL));
        assertEquals("forUpdateNowait", e1.getParam("feature"));

        NopException e2 = assertThrows(NopException.class, () -> sqlOf("mysql5.7", SKIP_LOCKED_EQL));
        assertEquals("forUpdateSkipLocked", e2.getParam("feature"));
    }

    @Test
    public void testH2TrueDbExecutionBothVariants() throws Exception {
        // 真库执行证据（plan 证据分级：[实测-jar] 升级 [实测]）：编译产物在真实 H2 上
        // 事务内执行不抛错且结果集正确（autocommit=false + rollback 探针边界）
        String nowaitSql = sqlOf("h2", NOWAIT_EQL);
        String skipLockedSql = sqlOf("h2", SKIP_LOCKED_EQL);
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:e2e_lock;DB_CLOSE_DELAY=-1")) {
            con.setAutoCommit(false);
            try (Statement st = con.createStatement()) {
                st.execute("create table APP_USER (SID int primary key, NAME varchar(64))");
                st.execute("insert into APP_USER values (1,'a'),(2,'b')");
            }

            List<Integer> ids = new ArrayList<>();
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(nowaitSql)) {
                while (rs.next())
                    ids.add(rs.getInt(1));
            }
            assertEquals(1, ids.size());
            assertEquals(1, ids.get(0).intValue());

            ids.clear();
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery(skipLockedSql)) {
                while (rs.next())
                    ids.add(rs.getInt(1));
            }
            assertEquals(1, ids.size());
            assertEquals(1, ids.get(0).intValue());
            assertTrue(nowaitSql.endsWith("for update nowait"));
            assertTrue(skipLockedSql.endsWith("for update skip locked"));
        }
    }
}
