package io.nop.config.source.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2497 Phase 2（2.2-C4-001）：JdbcConfig JDBC 超时两面（连接级 URL 参数 + 语句级 queryTimeout）
 * 单元契约。作用域裁定 = 连接级/语句级，拒绝 DriverManager.setLoginTimeout 全局静态
 * （JVM 全局副作用 + login timeout 不覆盖 socket 读挂起）——见 JdbcConfig 类内 javadoc。
 */
public class TestJdbcConfigTimeout {

    private static JdbcConfig newConfig(String url) {
        JdbcConfig config = new JdbcConfig();
        config.setJdbcUrl(url);
        return config;
    }

    /**
     * 默认即闭合缺陷：三个超时面默认均为有限正值（DB 分区默认从「无限期占死」退化为
     * 「周期性失败重试」，不依赖运维显式配置）。
     */
    @Test
    public void testFiniteTimeoutDefaultsActiveByDefault_C4_001() {
        JdbcConfig config = new JdbcConfig();
        assertNotNull(config.getConnectTimeout());
        assertNotNull(config.getSocketTimeout());
        assertNotNull(config.getQueryTimeout());
        assertTrue(isPositive(config.getConnectTimeout()), "connectTimeout 默认必须为有限正值");
        assertTrue(isPositive(config.getSocketTimeout()), "socketTimeout 默认必须为有限正值（审计根因 = MySQL 缺省 socketTimeout=0 无限读阻塞）");
        assertTrue(isPositive(config.getQueryTimeout()), "queryTimeout 默认必须为有限正值");
    }

    /**
     * 无既有查询串的 URL：追加 connectTimeout/socketTimeout（毫秒），分隔符 = '?'。
     */
    @Test
    public void testConnectionUrlTimeoutParamsAppended_C4_001() {
        JdbcConfig config = newConfig("jdbc:mysql://localhost:3306/db");
        config.setConnectTimeout(Duration.ofSeconds(5));
        config.setSocketTimeout(Duration.ofSeconds(30));
        assertEquals("jdbc:mysql://localhost:3306/db?connectTimeout=5000&socketTimeout=30000",
                config.buildConnectionUrl());
    }

    /**
     * 已带查询串的 URL：以 '&amp;' 续接，不破坏既有参数。
     */
    @Test
    public void testConnectionUrlAppendsToExistingQuery_C4_001() {
        JdbcConfig config = newConfig("jdbc:mysql://localhost:3306/db?useSSL=false");
        config.setConnectTimeout(Duration.ofMillis(1500));
        config.setSocketTimeout(Duration.ofSeconds(1));
        assertEquals("jdbc:mysql://localhost:3306/db?useSSL=false&connectTimeout=1500&socketTimeout=1000",
                config.buildConnectionUrl());
    }

    /**
     * URL 已自带同名参数时不重复追加（URL 显式配置优先——不同驱动的参数语义由用户掌控）。
     */
    @Test
    public void testConnectionUrlDoesNotDuplicateExistingParams_C4_001() {
        JdbcConfig config = newConfig("jdbc:postgresql://localhost/db?connectTimeout=5&socketTimeout=30");
        assertEquals("jdbc:postgresql://localhost/db?connectTimeout=5&socketTimeout=30",
                config.buildConnectionUrl());
    }

    /**
     * 显式配置 ≤0（或 null）= 显式停用该超时面：不追加对应参数（非 MySQL/MariaDB 驱动
     * 参数名不同的逃生通道）。
     */
    @Test
    public void testTimeoutOptOutDisablesAppending_C4_001() {
        JdbcConfig config = newConfig("jdbc:custom://localhost/db");
        config.setConnectTimeout(Duration.ZERO);
        config.setSocketTimeout(Duration.ofSeconds(-1));
        assertEquals("jdbc:custom://localhost/db", config.buildConnectionUrl());

        config.setConnectTimeout(null);
        config.setSocketTimeout(null);
        assertEquals("jdbc:custom://localhost/db", config.buildConnectionUrl());
    }

    /**
     * 语句级传导：正值超时按秒施加（亚秒向上取整为 1s，避免亚秒配置被静默解释为无超时）。
     */
    @Test
    public void testApplyQueryTimeoutConductsToStatement_C4_001() throws Exception {
        RecordingStatement stm = new RecordingStatement();

        JdbcConfig config = new JdbcConfig();
        config.setQueryTimeout(Duration.ofSeconds(45));
        config.applyQueryTimeout(stm.proxy());
        assertEquals(List.of(45), stm.queryTimeouts, "queryTimeout 必须按秒传导到 Statement");

        stm.queryTimeouts.clear();
        config.setQueryTimeout(Duration.ofMillis(300));
        config.applyQueryTimeout(stm.proxy());
        assertEquals(List.of(1), stm.queryTimeouts, "亚秒正值必须向上取整为 1s（不得静默失效为无超时）");
    }

    /**
     * 语句级显式停用：null / ≤0 不调用 setQueryTimeout（0 语义 = JDBC 无超时，与停用一致）。
     */
    @Test
    public void testApplyQueryTimeoutOptOut_C4_001() throws Exception {
        RecordingStatement stm = new RecordingStatement();

        JdbcConfig config = new JdbcConfig();
        config.setQueryTimeout(null);
        config.applyQueryTimeout(stm.proxy());
        assertTrue(stm.queryTimeouts.isEmpty(), "未配置不得施加");

        config.setQueryTimeout(Duration.ZERO);
        config.applyQueryTimeout(stm.proxy());
        assertTrue(stm.queryTimeouts.isEmpty(), "显式 0 = 停用面，不得施加");

        config.setQueryTimeout(Duration.ofSeconds(-5));
        config.applyQueryTimeout(stm.proxy());
        assertTrue(stm.queryTimeouts.isEmpty(), "显式负值 = 停用面，不得施加");
    }

    private static boolean isPositive(Duration d) {
        return d != null && !d.isZero() && !d.isNegative();
    }

    /**
     * 记录 setQueryTimeout 调用的 Statement 桩。
     */
    static class RecordingStatement {
        final List<Integer> queryTimeouts = new ArrayList<>();
        final Statement proxy = (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class[]{Statement.class},
                (p, method, args) -> {
                    if (method.getName().equals("setQueryTimeout")) {
                        queryTimeouts.add((Integer) args[0]);
                        return null;
                    }
                    return null;
                });

        Statement proxy() {
            return proxy;
        }
    }
}
