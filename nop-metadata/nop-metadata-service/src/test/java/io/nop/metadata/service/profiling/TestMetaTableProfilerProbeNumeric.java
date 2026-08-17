package io.nop.metadata.service.profiling;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-06 回归测试：profiler {@code probeNumeric} 不再静默塌缩基础设施失败为"非数值列"。
 *
 * <p>旧实现 {@code catch (SQLException)} 后仅 DEBUG 日志 + return false，无法区分：
 * <ul>
 *   <li><b>类型不匹配</b>（良性，SUM(text_col) 失败 → return false 正确回退 string stats）</li>
 *   <li><b>基础设施失败</b>（连接断开 / 列被撤销 / 表被删除 / 权限）—— 旧实现同样静默 return false，
 *       列被错误剖析为 string stats，DEBUG 级别无任何可见信号</li>
 * </ul>
 *
 * <p>修复后：类型不匹配（SQLState {@code 22*} data exception）仍 DEBUG + return false；
 * 基础设施失败（{@code 08*} / {@code 42*} / {@code 42501} / null）至少 WARN 日志 + 上下文，仍 return false
 * 保持剖析不中断（签名不变，错误通过 WARN 暴露而非侵入 profile 方法签名）。
 *
 * <p>本测试置于 {@code io.nop.metadata.service.profiling} 包以直接访问 package-private 的
 * {@code isInfrastructureFailure}；{@code probeNumeric} 为 private，经反射调用（anti-hollow：
 * 实际执行 JDBC 路径，验证 catch 分支语义，不只是方法存在）。
 */
public class TestMetaTableProfilerProbeNumeric {

    // ===== isInfrastructureFailure 单元（package-private，直接调用）=====

    @Test
    public void testIsInfrastructureFailureClassification() {
        // data exception 22* = 类型不匹配（良性）→ 非基础设施
        assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlEx("22018", "data conversion error")));
        assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlEx("22000", "data exception")));
        // H2 vendor SQLState 90015 (SUM/AVG wrong data type) = 良性类型不匹配 → 非基础设施
        assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlEx("90015", "SUM or AVG on wrong data type")));
        // 连接 08* = 基础设施
        assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlEx("08006", "connection lost")));
        // 授权 28* = 基础设施
        assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlEx("28000", "invalid authorization")));
        // 表/列不存在 42* = 基础设施
        assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlEx("42S02", "table not found")));
        // 权限 42501（前缀 42）= 基础设施
        assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlEx("42501", "permission denied")));
        // null SQLState 但消息含基础设施线索 → infra（消息识别）
        assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlEx(null, "connection refused")));
        assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlEx(null, "permission denied for table")));
        // null SQLState + 无线索消息 → 良性（避免 WARN 淹没）
        assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlEx(null, "no sqlstate")));
        assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlEx(" ", "blank sqlstate")));
    }

    // ===== probeNumeric 类型不匹配（真实 H2，行为不变）=====

    /** probeNumeric 对数值列返回 true。 */
    @Test
    public void testProbeNumericOnNumericColumnReturnsTrue() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:probe_num;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE T_NUM (id INT)");
            st.execute("INSERT INTO T_NUM VALUES (10)");
            MetaTableProfiler profiler = new MetaTableProfiler();
            assertTrue(invokeProbeNumeric(profiler, conn, "T_NUM", "id"),
                    "probeNumeric on numeric column must return true");
        }
    }

    /**
     * probeNumeric 对文本列（类型不匹配）返回 false —— 行为不变，非数值列正确回退 string stats。
     * 类型不匹配是预期的良性失败，不应抛异常打断剖析。
     */
    @Test
    public void testProbeNumericOnTextColumnTypeMismatchReturnsFalse() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:probe_text;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE T_TXT (c VARCHAR(20))");
            st.execute("INSERT INTO T_TXT VALUES ('abc')");
            MetaTableProfiler profiler = new MetaTableProfiler();
            assertFalse(invokeProbeNumeric(profiler, conn, "T_TXT", "c"),
                    "probeNumeric on text column (type mismatch) must return false, not throw");
        }
    }

    // ===== probeNumeric 基础设施失败（模拟连接失败 → WARN，不再静默）=====

    /**
     * AR-06 核心回归：模拟连接失败（createStatement 抛 SQLException SQLState 08006）——probeNumeric
     * 仍 return false（剖析不中断），但必须发出 WARN 日志（修复前为 DEBUG 级别静默吞掉）。
     */
    @Test
    public void testProbeNumericInfrastructureFailureLogsWarnNotSilent() throws Exception {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MetaTableProfiler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // 真实 H2 连接仅用于获得 DatabaseMetaData 占位；probeNumeric 实际经 createStatement 执行，
            // 代理拦截 createStatement 抛 08006（connection failure）SQLException
            try (Connection real = DriverManager.getConnection("jdbc:h2:mem:probe_infra;DB_CLOSE_DELAY=-1", "sa", "")) {
                Connection failing = (Connection) java.lang.reflect.Proxy.newProxyInstance(
                        Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if ("createStatement".equals(method.getName())) {
                                throw sqlEx("08006", "connection lost (simulated infrastructure failure)");
                            }
                            try {
                                return method.invoke(real, args);
                            } catch (InvocationTargetException e) {
                                throw e.getCause();
                            }
                        });
                MetaTableProfiler profiler = new MetaTableProfiler();
                boolean result = invokeProbeNumeric(profiler, failing, "ANY_TABLE", "ANY_COL");
                assertFalse(result, "probeNumeric on infrastructure failure must still return false (no crash)");
            }
            boolean warnLogged = appender.list.stream().anyMatch(e ->
                    e.getLevel() == ch.qos.logback.classic.Level.WARN
                            && e.getFormattedMessage().contains("infrastructure failure"));
            assertTrue(warnLogged,
                    "infrastructure failure (SQLState 08006) must be logged with WARN (AR-06), got: "
                            + appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.toList()));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ===== 跨 Phase 依赖（AR-05 移除 BOOLEAN 后，BOOLEAN 列经 probeNumeric 回退 string stats）=====

    /**
     * 跨 Phase 依赖：AR-05 把 BOOLEAN 移出数值集合后，BOOLEAN 列（isNumericType=false, isStringType=false）
     * 落入 probeNumeric 路径（经守卫探测，而非旧实现直接 collectNumericStats 产非法 SQL）。
     *
     * <p>H2 的 SUM(BOOLEAN) 实际成功（视 TRUE 为 1），故 probeNumeric 返回 true 后 collectNumericStats 在
     * AVG(BOOLEAN) 上失败 → 该列被 per-column 失败隔离收集。本测试验证：表级剖析不崩溃（rowCount 正确、
     * INT 列正常剖析），BOOLEAN 列的失败被隔离（不整表失败）。这证明 AR-05 改动未引入表级回归。
     */
    @Test
    public void testBooleanColumnProfileIsolatedNoTableCrash() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:probe_bool;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE T_BOOL (id INT, flag BOOLEAN)");
            st.execute("INSERT INTO T_BOOL VALUES (1, TRUE)");
            st.execute("INSERT INTO T_BOOL VALUES (2, FALSE)");

            MetaTableProfiler profiler = new MetaTableProfiler();
            TableReference ref = new TableReference(
                    TableReference.Kind.EXTERNAL, "mt-test", "T_BOOL", null,
                    null, null, null, null);

            io.nop.metadata.service.profiling.ProfilingSnapshot snapshot = profile(profiler, conn, ref);

            // 表级剖析完成（无异常崩溃），rowCount 正确
            assertEquals(2L, snapshot.getRowCount(), "BOOLEAN table must be profiled without table-level crash");
            // id (INT) 列正常剖析（在 columnStats 中）
            boolean idProfiled = snapshot.getColumnStats().stream()
                    .anyMatch(cs -> "ID".equalsIgnoreCase(cs.getColumnName()));
            assertTrue(idProfiled, "INT column 'id' must be profiled successfully");
            // BOOLEAN 不在数值集合 → isNumericType false（确保走 probeNumeric 守卫而非直接 collectNumericStats）
            assertFalse(MetaTableProfiler.isNumericType("BOOLEAN"),
                    "BOOLEAN must be out of numeric set so it goes through probeNumeric guard");
            // probeNumeric 类型不匹配守卫本身不抛异常（return false）—— 已由 testProbeNumericOnTextColumnTypeMismatchReturnsFalse 钉死
        }
    }

    // ===== helpers =====

    /** 反射调用 private probeNumeric。 */
    private static boolean invokeProbeNumeric(MetaTableProfiler profiler, Connection conn,
                                              String table, String col) throws Exception {
        Method m = MetaTableProfiler.class.getDeclaredMethod("probeNumeric",
                Connection.class, String.class, String.class);
        m.setAccessible(true);
        try {
            return (boolean) m.invoke(profiler, conn, table, col);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        }
    }

    /** 反射调用 private profile（真实 H2 连接 + DatabaseMetaData）。 */
    private static io.nop.metadata.service.profiling.ProfilingSnapshot profile(
            MetaTableProfiler profiler, Connection conn, TableReference ref) throws Exception {
        Method m = MetaTableProfiler.class.getDeclaredMethod("profile",
                Connection.class, DatabaseMetaData.class, TableReference.class,
                String.class, String.class, String.class);
        m.setAccessible(true);
        try {
            return (io.nop.metadata.service.profiling.ProfilingSnapshot) m.invoke(
                    profiler, conn, conn.getMetaData(), ref, "PUBLIC", null, "H2");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        }
    }

    private static SQLException sqlEx(String sqlState, String msg) {
        return new SQLException(msg, sqlState);
    }
}
