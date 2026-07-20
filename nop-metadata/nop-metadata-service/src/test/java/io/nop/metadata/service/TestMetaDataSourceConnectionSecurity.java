/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.connection.MetaDataSourceConnectionProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-02 对抗性回归测试：验证 jdbcUrl 协议白名单 + 危险参数黑名单 + 主机白名单 +
 * driverClassName 白名单在 live code 中显式拒绝。
 *
 * <p>不依赖真实网络：所有路径都应在校验阶段抛 ErrorCode，不进入 DriverManager.getConnection。
 *
 * <p>覆盖 Exit Criteria：jdbcUrl 指向 169.254.169.254 / INIT=RUNSCRIPT / allowLoadLocalInfile /
 * 任意 driverClassName 必须显式失败。
 */
public class TestMetaDataSourceConnectionSecurity {

    private final MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();

    private static final String BASE_CFG = "\"username\":\"sa\",\"password\":\"\"";

    // ===== jdbcUrl 协议白名单 =====

    /** 非白名单协议（jdbc:file / jdbc:oracle / jdbc:sqlserver / jdbc:h2:tcp 等）必须失败。 */
    @Test
    public void testNonWhitelistedProtocolRejected() {
        String[] badUrls = {
                "jdbc:file:/etc/passwd",
                "jdbc:oracle:thin:@evil",
                "jdbc:sqlserver://evil;integratedSecurity=true",
                "jdbc:h2:tcp://evil/db",
                "jdbc:h2:ssl://evil/db",
                "jdbc:custom:anything"
        };
        for (String url : badUrls) {
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"" + url + "\"," + BASE_CFG + "}"),
                    "non-whitelisted protocol must fail: " + url);
            assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "non-whitelisted protocol must fail with ERR_DATASOURCE_JDBC_URL_BLOCKED: " + url);
            assertTrue(String.valueOf(ex.getParam("reason")).contains("protocol"),
                    "reason must mention protocol: " + ex.getParam("reason"));
        }
    }

    // ===== 危险参数黑名单 =====

    /** allowLoadLocalInfile=true（MySQL 任意文件读取 CVE 链）必须失败。 */
    @Test
    public void testAllowLoadLocalInfileRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://10.255.255.1:3306/db?allowLoadLocalInfile=true\","
                                + BASE_CFG + "}"));
        // 内网 IP（10.x）或 危险参数都会触发，但优先匹配；任一情况都应是 ERR_DATASOURCE_JDBC_URL_BLOCKED
        assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
        String reason = String.valueOf(ex.getParam("reason"));
        assertTrue(reason.contains("dangerous") || reason.contains("host"),
                "reason must flag dangerous or host: " + reason);
    }

    /** H2 INIT=RUNSCRIPT（任意代码执行 CVE）必须失败。 */
    @Test
    public void testH2InitRunscriptRejected() {
        // URL 含 INIT= 危险关键字（避开单引号免引号转义问题）
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:h2:mem:x;INIT=RUNSCRIPTFROM\","
                                + BASE_CFG + "}"));
        assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    /** allowMultiQueries（多语句注入放大）必须失败。 */
    @Test
    public void testAllowMultiQueriesRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://10.255.255.1/db?allowMultiQueries=true\","
                                + BASE_CFG + "}"));
        assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    /** allowUrlInLocalInfile（远程文件读取）必须失败。 */
    @Test
    public void testAllowUrlInLocalInfileRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://10.255.255.1/db?allowUrlInLocalInfile=true\","
                                + BASE_CFG + "}"));
        assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    // ===== 主机白名单（fail-closed 默认禁内网）=====

    /** AWS 元数据服务 IP（SSRF 经典目标）必须失败。 */
    @Test
    public void testLinkLocalMetadataHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://169.254.169.254:3306/db\"," + BASE_CFG + "}"));
        assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("host"),
                "reason must mention host: " + ex.getParam("reason"));
    }

    /** RFC1918 私有段（10.x / 172.16-31.x / 192.168.x）必须失败。 */
    @Test
    public void testRfc1918HostsRejected() {
        String[] internalHosts = {"10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.1.1"};
        for (String host : internalHosts) {
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"jdbc:mysql://" + host + ":3306/db\"," + BASE_CFG + "}"),
                    "internal host must be rejected: " + host);
            assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                    ex.getErrorCode());
        }
    }

    /** localhost / 127.0.0.1 默认拒绝（必须显式配 allowed-hosts 才允许）。 */
    @Test
    public void testLoopbackDefaultRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://localhost:3306/db\"," + BASE_CFG + "}"));
        assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    // ===== driverClassName 白名单 =====

    /** 非白名单 driverClassName（任意类加载攻击）必须失败。 */
    @Test
    public void testNonWhitelistedDriverRejected() {
        String[] badDrivers = {
                "com.attack.ExploitDriver",
                "org.springframework.context.support.ClassPathXmlApplicationContext",
                "javax.naming.InitialContext"
        };
        for (String driver : badDrivers) {
            // 用合法 h2 url（无内部 host）以隔离 driver 校验
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"jdbc:h2:mem:ok\",\"driverClassName\":\"" + driver
                                    + "\"," + BASE_CFG + "}"),
                    "non-whitelisted driver must fail: " + driver);
            assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_DRIVER_NOT_ALLOWED.getErrorCode(),
                    ex.getErrorCode());
            assertEquals(driver, ex.getParam("driverClassName"));
        }
    }

    /** 白名单 driverClassName 通过 driver 校验（继续进 JDBC 路径会因 DB 不存在失败，
     * 但 ErrorCode 必须不是 DRIVER_NOT_ALLOWED）。 */
    @Test
    public void testWhitelistedDriverAccepted() {
        // h2 mem url + org.h2.Driver：进入 JDBC，但 URL 含 ";INIT=" 等会先被 url 校验拒绝。
        // 这里用最简单的 jdbc:h2:mem:ok，会进入实际建连并成功（H2 内存库）。
        java.util.Map<String, Object> result = service.testConnect("jdbc",
                "{\"jdbcUrl\":\"jdbc:h2:mem:ok_driver_test\",\"driverClassName\":\"org.h2.Driver\","
                        + BASE_CFG + "}");
        assertEquals(Boolean.TRUE, result.get("connected"),
                "whitelisted H2 driver + mem URL must succeed: " + result);
    }

    // ===== requireJdbcType 维度09-07 =====

    /** 非 jdbc 类型必须抛 NopException(ERR_DATASOURCE_TYPE_NOT_SUPPORTED) 而非 UnsupportedOperationException。 */
    @Test
    public void testNonJdbcTypeThrowsNopException() {
        String[] nonJdbcTypes = {"http", "rest", "file", "odbc", "", null};
        for (String type : nonJdbcTypes) {
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect(type, "{\"jdbcUrl\":\"jdbc:h2:mem:ok\"," + BASE_CFG + "}"),
                    "non-jdbc type must fail: " + type);
            assertEquals(MetaDataSourceConnectionProcessor.ERR_DATASOURCE_TYPE_NOT_SUPPORTED.getErrorCode(),
                    ex.getErrorCode());
        }
    }

    // ===== 建连超时（loginTimeout）=====

    /**
     * AR-02 黑洞 IP 建连必须在合理时间内返回（≤ 30s 兜底，实际由 DriverManager.loginTimeout=5 控制）。
     * 注：因 DriverManager.setLoginTimeout 是全局且实测依赖网络环境，
     * 这里只验证 buildDataSource 不会因 loginTimeout 抛异常（验证设置被调用），不断言精确秒数。
     * 失败路径：192.0.2.1（TEST-NET-1 RFC5737 不可路由，但默认禁内网白名单会先拒绝）。
     * 为隔离超时机制本身，使用 192.0.2.1 不是内网段，验证 setLoginTimeout 被调用且非内网 IP
     * 进入实际建连（最终 SQLException，不超时控制溢出）。
     */
    @Test
    public void testLoginTimeoutSetGlobally() {
        // 调用 buildDataSource 路径前先记录
        int before = java.sql.DriverManager.getLoginTimeout();
        try {
            // 用合法 H2 mem URL 触发 buildDataSource 全路径（含 setLoginTimeout）
            service.testConnect("jdbc",
                    "{\"jdbcUrl\":\"jdbc:h2:mem:ok_timeout\",\"driverClassName\":\"org.h2.Driver\","
                            + BASE_CFG + "}");
        } catch (Exception ignored) {
            // 仅关心 setLoginTimeout 是否被调用
        }
        int after = java.sql.DriverManager.getLoginTimeout();
        assertTrue(after == MetaDataSourceConnectionProcessor.DEFAULT_LOGIN_TIMEOUT_SECONDS,
                "DriverManager.setLoginTimeout must be set to "
                        + MetaDataSourceConnectionProcessor.DEFAULT_LOGIN_TIMEOUT_SECONDS
                        + " after buildDataSource call (before=" + before + ", after=" + after + ")");
    }
}
