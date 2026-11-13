
package io.nop.metadata.service.connection;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataDatasourceConfigs;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-01 回归钉（plan 2528 红①，CRA 5.5 C7-102+C7-001+C4-R2-001）：外部 JDBC 超时预算单点收口。
 *
 * <p>缺陷形态（pre-fix）：外部库语句执行全链无超时预算——loginTimeout=5 仅界建连握手且为 JVM
 * 全局态，建连后 socket 读（getMetaData 第一读 + 全部语句点）零超时，外部源挂起 → 工作线程
 * 永久阻塞。修复后预算收口于连接打开缝：{@code setNetworkTimeout} 以缺省 30s（webhook 30s
 * 惯例）被调用且先于首次 socket 读（getMetaData）；withConnection/testConnect 两条建连路径
 * 均覆盖；H2 白名单子集（mem/file，进程内连接）显式跳过并保持可连。
 *
 * <p>配置语义钉：负值显式失败（ERR_DATASOURCE_CONFIG_INVALID，不静默缺省）；0 = 显式禁用档。
 */
public class TestExternalJdbcTimeoutBudget_W_01 {

    private static final String BASE_CFG = "\"username\":\"sa\",\"password\":\"\"";

    private static String h2Config(String memDbName) {
        return "{\"jdbcUrl\":\"jdbc:h2:mem:" + memDbName + "\","
                + "\"driverClassName\":\"org.h2.Driver\"," + BASE_CFG + "}";
    }

    private static String mysqlConfig() {
        // 外部主机（example.invalid 非内网、不可解析）——记录型打开缝替换真实建连，不触网
        return "{\"jdbcUrl\":\"jdbc:mysql://example.invalid:3306/meta_w01\","
                + "\"driverClassName\":\"com.mysql.cj.jdbc.Driver\"," + BASE_CFG + "}";
    }

    @AfterEach
    public void restoreTimeoutConfigs() {
        restore(NopMetadataDatasourceConfigs.CFG_DATASOURCE_NETWORK_TIMEOUT_SECONDS);
        restore(NopMetadataDatasourceConfigs.CFG_DATASOURCE_LOGIN_TIMEOUT_SECONDS);
    }

    private static void restore(IConfigReference<Integer> ref) {
        AppConfig.getConfigProvider().updateConfigValue(ref, ref.getDefaultValue());
    }

    /** 记录型打开缝：替换真实 Connection，捕获预算应用与调用次序（包内可覆写测试缝）。 */
    static class BudgetRecordingProcessor extends MetaDataSourceConnectionProcessor {
        final List<String> calls = new ArrayList<>();
        Integer networkTimeoutMillis;

        @Override
        Connection openConnection(SimpleDataSource dataSource) throws SQLException {
            Connection proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, this::handleConnectionCall);
            // 预算应用必须在「调用方首次 socket 读」之前——由本缝先行调用生产代码 applyConnectionBudget
            applyConnectionBudget(proxy, dataSource.getUrl());
            return proxy;
        }

        private Object handleConnectionCall(Object proxy, java.lang.reflect.Method method, Object[] args) {
            calls.add(method.getName());
            if ("setNetworkTimeout".equals(method.getName())) {
                networkTimeoutMillis = (Integer) args[1];
            }
            if ("getMetaData".equals(method.getName())) {
                return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                        new Class<?>[]{DatabaseMetaData.class}, (p, m, a) -> defaultValue(m.getReturnType()));
            }
            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> type) {
            if (type == boolean.class) return Boolean.FALSE;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        }
    }

    /** 缺省预算钉：mysql 族记录型连接上 setNetworkTimeout(30_000)（30s = webhook 30s 惯例）。 */
    @Test
    public void testWithConnectionAppliesDefaultNetworkTimeout() {
        BudgetRecordingProcessor service = new BudgetRecordingProcessor();
        Map<String, Object> seen = new LinkedHashMap<>();
        service.withConnection("jdbc", mysqlConfig(), (conn, metaData) -> seen.put("action", true));
        assertTrue(seen.containsKey("action"), "action must run on the injected recording connection");
        assertEquals(Integer.valueOf(30_000), service.networkTimeoutMillis,
                "setNetworkTimeout must be applied with the 30s default budget (ms)");
        assertEquals(0, service.calls.indexOf("setNetworkTimeout"),
                "budget must be applied before the caller's first socket read (getMetaData)");
        assertTrue(service.calls.indexOf("getMetaData") > service.calls.indexOf("setNetworkTimeout"),
                "getMetaData (first socket read) must come after the budget application: " + service.calls);
    }

    /** testConnect 独立获取路径同经单一打开缝（评审 Minor-3 钉面）。 */
    @Test
    public void testTestConnectAppliesDefaultNetworkTimeout() {
        BudgetRecordingProcessor service = new BudgetRecordingProcessor();
        Map<String, Object> result = service.testConnect("jdbc", mysqlConfig());
        assertNotNull(result, "testConnect must return a structured result");
        assertEquals(Integer.valueOf(30_000), service.networkTimeoutMillis,
                "testConnect path must apply the 30s default network budget");
        assertTrue(service.calls.indexOf("getMetaData") > service.calls.indexOf("setNetworkTimeout"),
                "budget must precede getMetaData on the testConnect path: " + service.calls);
    }

    /** H2 mem 路径：进程内白名单子集跳过预算且保持可连（族跳过分支显式可见）。 */
    @Test
    public void testH2MemPathStillConnects() {
        MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();
        Map<String, Object> result = service.testConnect("jdbc", h2Config("ok_w01"));
        assertEquals(Boolean.TRUE, result.get("connected"),
                "H2 mem whitelist subset must stay connectable: " + result);
        assertEquals("H2", result.get("databaseProductName"),
                "real H2 metadata expected on the in-process path");
    }

    /** 负值配置显式失败（不静默缺省——无静默跳过钉）。 */
    @Test
    public void testNegativeNetworkTimeoutConfigFailsFast() {
        AppConfig.getConfigProvider().updateConfigValue(
                NopMetadataDatasourceConfigs.CFG_DATASOURCE_NETWORK_TIMEOUT_SECONDS, -1);
        MetaDataSourceConnectionProcessor service = new MetaDataSourceConnectionProcessor();
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc", h2Config("w01_neg")));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID.getErrorCode(), ex.getErrorCode(),
                "negative network-timeout-seconds must fail fast with ERR_DATASOURCE_CONFIG_INVALID");
    }

    /** 0 = 显式禁用档：跳过预算、路径可用（WARN 一次由生产侧留证）。 */
    @Test
    public void testZeroNetworkTimeoutDisablesBudgetExplicitly() {
        AppConfig.getConfigProvider().updateConfigValue(
                NopMetadataDatasourceConfigs.CFG_DATASOURCE_NETWORK_TIMEOUT_SECONDS, 0);
        BudgetRecordingProcessor service = new BudgetRecordingProcessor();
        Map<String, Object> seen = new LinkedHashMap<>();
        service.withConnection("jdbc", mysqlConfig(), (conn, metaData) -> seen.put("action", true));
        assertTrue(seen.containsKey("action"), "disabled budget must not break the path");
        assertEquals(null, service.networkTimeoutMillis,
                "network-timeout-seconds=0 must explicitly skip setNetworkTimeout (disable档)");
    }

    /**
     * 红④ 断言 (b)（W-05 反向钉改写同 diff——与改写后的
     * TestMetaDataSourceConnectionSecurity#testLoginTimeoutNotGlobalAnymore 断言 (a) 配对）：
     * 每连接属性路径——MySQL 族注入 connectTimeout=5000（毫秒，5s 缺省）；PostgreSQL 族注入秒值；
     * H2 白名单子集（mem/file，进程内连接）不注入；login-timeout=0 禁用档不注入。
     */
    @Test
    public void testLoginTimeoutHandshakePropertiesPerFamily() {
        java.util.Properties mysqlProps = MetaDataSourceConnectionProcessor.handshakeProperties(
                "jdbc:mysql://example.invalid:3306/db", 5);
        assertEquals("5000", mysqlProps.getProperty("connectTimeout"),
                "MySQL family must inject connectTimeout=login-timeout-seconds*1000 ms per connection");
        java.util.Properties pgProps = MetaDataSourceConnectionProcessor.handshakeProperties(
                "jdbc:postgresql://example.invalid/db", 5);
        assertEquals("5", pgProps.getProperty("connectTimeout"),
                "PostgreSQL family must inject connectTimeout=login-timeout-seconds (seconds)");
        assertTrue(MetaDataSourceConnectionProcessor.handshakeProperties("jdbc:h2:mem:x", 5).isEmpty(),
                "H2 in-process whitelist subset must not inject handshake properties");
        assertTrue(MetaDataSourceConnectionProcessor.handshakeProperties("jdbc:mysql://example.invalid/db", 0).isEmpty(),
                "login-timeout-seconds=0 (disable档) must inject no handshake properties");
    }
}
