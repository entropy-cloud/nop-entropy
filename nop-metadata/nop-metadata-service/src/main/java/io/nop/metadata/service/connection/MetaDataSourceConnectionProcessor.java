/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.connection;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.service.NopMetadataErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.regex.Pattern;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 按需建连实现：每次调用从 connectionConfig 构建一个 {@link SimpleDataSource}（不池化、
 * 不注册到 ORM 路由），建连 → 执行 → finally 关闭。
 *
 * <p>仅支持 jdbc 类型；http/rest/file 首版显式抛 {@link UnsupportedOperationException}。
 *
 * <p>安全加固（AR-02）：jdbcUrl 协议白名单（mysql/postgresql/h2）+ 危险参数黑名单
 * （allowLoadLocalInfile/INIT=/allowMultiQueries 等）+ driverClassName 白名单 +
 * DriverManager.setLoginTimeout(5) 在构造函数中初始化（{@link SimpleDataSource#setLoginTimeout} 为 no-op）。
 */
public class MetaDataSourceConnectionProcessor implements IMetaDataSourceConnectionProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(MetaDataSourceConnectionProcessor.class);

    /** AR-02: jdbcUrl 协议/主机不在白名单 或 含危险参数。 */
    /** AR-02: driverClassName 不在白名单（防任意类加载攻击）。 */

    private static final String CFG_JDBC_URL = "jdbcUrl";
    private static final String CFG_USERNAME = "username";
    private static final String CFG_PASSWORD = "password";
    private static final String CFG_DRIVER_CLASS_NAME = "driverClassName";

    /** AR-02: 允许的 JDBC 协议前缀（mysql/postgresql/h2 mem/file 本地模式）。H2 tcp/ssl 网络模式禁用（远程 H2 攻击面）。 */
    private static final Set<String> ALLOWED_JDBC_PROTOCOLS = new HashSet<>(Arrays.asList(
            "jdbc:mysql:", "jdbc:postgresql:", "jdbc:h2:mem:", "jdbc:h2:file:"));

    /** AR-02: 危险 JDBC URL 参数/子串（大小写不敏感 contains）。 */
    private static final Set<String> DANGEROUS_URL_TOKENS = new HashSet<>(Arrays.asList(
            "allowLoadLocalInfile".toLowerCase(),
            "allowmultiqueries",
            "allowurlinlocalinfile",
            "autoddeserialize",
            "usessl=false",
            "requiressl=false",
            "allownativepasswords",
            "allowpublickeyretrieval=true",
            "init=",
            "#initscript",
            "runscript",
            "executeimmediate",
            "tracemaster"));

    /** AR-02: 允许的 JDBC driver 类名白名单（H2/MySQL/PostgreSQL）。 */
    private static final Set<String> ALLOWED_DRIVER_CLASSES = new HashSet<>(Arrays.asList(
            "org.h2.Driver",
            "com.mysql.cj.jdbc.Driver",
            "com.mysql.jdbc.Driver",
            "org.postgresql.Driver"));

    /** AR-02: 默认建连超时秒数（{@link SimpleDataSource#setLoginTimeout} 是 no-op，实际靠 {@link DriverManager#setLoginTimeout}）。 */
    public static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 5;

    /** 从 jdbcUrl 中提取 user:password@ 前缀用于 redaction 的正则。 */
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(://)([^:@/]+)(?::[^@/]*)?@");

    public MetaDataSourceConnectionProcessor() {
        setGlobalLoginTimeout();
    }

    /**
     * 包内可重写（测试用）：实际调用 {@link DriverManager#setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS)}。
     * 方法级抽象使测试可以 mock/子类化覆盖而不依赖全局状态副作用。
     */
    void setGlobalLoginTimeout() {
        try {
            DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS);
        } catch (SecurityException se) {
            LOG.warn("DriverManager.setLoginTimeout denied by security manager", se);
        }
    }

    /** AR-02: 可配置的允许内网/RFC1918 主机集合（逗号分隔小写 host）。默认空：禁内网。 */
    @InjectValue(value = "@cfg:nop.metadata.datasource.allowed-hosts|")
    protected String allowedInternalHostsCsv = "";

    /** 解析后的允许内网主机集合（小写）。 */
    protected Set<String> resolveAllowedInternalHosts() {
        if (allowedInternalHostsCsv == null || allowedInternalHostsCsv.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        for (String token : allowedInternalHostsCsv.split(",")) {
            String trimmed = token.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    @Override
    public void withConnection(String datasourceType, String connectionConfig,
                               BiConsumer<Connection, DatabaseMetaData> action) {
        DataSource dataSource = buildDataSource(datasourceType, connectionConfig);
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            action.accept(conn, metaData);
        } catch (SQLException e) {
            throw newNopConnectException(datasourceType, e);
        } finally {
            IoHelper.safeCloseObject(conn);
        }
    }

    @Override
    public Map<String, Object> testConnect(String datasourceType, String connectionConfig) {
        requireJdbcType(datasourceType);

        DataSource dataSource = buildDataSource(datasourceType, connectionConfig);

        Map<String, Object> result = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            DatabaseMetaData metaData = conn.getMetaData();
            result.put("connected", true);
            result.put("databaseProductName", metaData.getDatabaseProductName());
            result.put("databaseProductVersion", metaData.getDatabaseProductVersion());
            result.put("driverName", metaData.getDriverName());
            result.put("driverVersion", metaData.getDriverVersion());
            return result;
        } catch (SQLException e) {
            LOG.warn("testConnect failed for datasourceType={}", datasourceType, e);
            result.put("connected", false);
            result.put("error", "Connection failed");
            return result;
        } finally {
            IoHelper.safeCloseObject(conn);
        }
    }

    /**
     * 从 connectionConfig JSON 构建 {@link SimpleDataSource}（非 jdbc 类型快速失败）。
     * 仅 jdbc 类型支持；其余类型抛 {@link NopException}({@link #NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED})。
     *
     * <p>AR-02 安全加固：(a) jdbcUrl 协议白名单 + 危险参数黑名单 + 内网主机白名单；
     * (b) driverClassName 白名单； (c) {@link DriverManager#setLoginTimeout} 在构造函数中设置（非每连接调用）。
     */
    private DataSource buildDataSource(String datasourceType, String connectionConfig) {
        requireJdbcType(datasourceType);

        Map<String, Object> cfg = parseConnectionConfig(connectionConfig, datasourceType);
        String jdbcUrl = requireNonBlank(cfg, CFG_JDBC_URL, datasourceType);
        String username = requireNonBlank(cfg, CFG_USERNAME, datasourceType);
        // password 允许空串（如 H2 默认空密码），仅要求 key 存在（缺失才快速失败）
        String password = requireField(cfg, CFG_PASSWORD, datasourceType);
        String driverClassName = optString(cfg, CFG_DRIVER_CLASS_NAME);

        // AR-02 (a): jdbcUrl 协议白名单 + 危险参数黑名单 + 主机白名单
        validateJdbcUrl(jdbcUrl);
        // AR-02 (b): driverClassName 白名单（显式指定时校验）
        if (driverClassName != null && !driverClassName.isEmpty()) {
            validateDriverClassName(driverClassName);
        }
        SimpleDataSource ds = new SimpleDataSource();
        ds.setUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        if (driverClassName != null && !driverClassName.isEmpty()) {
            ds.setDriverClassName(driverClassName);
        }
        return ds;
    }

    private void requireJdbcType(String datasourceType) {
        if (!_NopMetadataCoreConstants.DATASOURCE_TYPE_JDBC.equals(datasourceType)) {
            // 维度09-07：使用 inline ErrorCode 而非 UnsupportedOperationException（与 URL 白名单配合产生有意义错误码）
            throw new NopException(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED)
                    .param("datasourceType", String.valueOf(datasourceType));
        }
    }

    /**
     * AR-02: 校验 jdbcUrl 安全策略。
     * <ol>
     *   <li>协议白名单：必须以 jdbc:mysql:/jdbc:postgresql:/jdbc:h2: 之一开头。</li>
     *   <li>危险参数黑名单：禁止 allowLoadLocalInfile / INIT= / allowMultiQueries / RUNSCRIPT 等（大小写不敏感 contains）。</li>
     *   <li>主机白名单：默认禁内网（RFC1918 + 169.254 + localhost）；显式配置 nop.metadata.datasource.allowed-hosts 后允许。</li>
     * </ol>
     * 失败时显式抛 {@link #NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED}，附 {@code jdbcUrl}/{@code reason} 参数。
     */
    void validateJdbcUrl(String jdbcUrl) {
        // (1) 协议白名单
        String lower = jdbcUrl.toLowerCase();
        boolean protocolOk = false;
        for (String proto : ALLOWED_JDBC_PROTOCOLS) {
            if (lower.startsWith(proto)) {
                protocolOk = true;
                break;
            }
        }
        if (!protocolOk) {
            throw new NopException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                    .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                    .param(NopMetadataErrors.ARG_RAW_JDBC_URL, jdbcUrl)
                    .param("reason", "protocol not in whitelist (mysql/postgresql/h2)");
        }
        // (2) 危险参数黑名单
        for (String dangerous : DANGEROUS_URL_TOKENS) {
            if (lower.contains(dangerous)) {
                throw new NopException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                        .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                        .param(NopMetadataErrors.ARG_RAW_JDBC_URL, jdbcUrl)
                        .param("reason", "dangerous parameter/token present: " + dangerous);
            }
        }
        // (3) 主机白名单：默认禁内网（RFC1918 + link-local + localhost）
        String host = extractHost(jdbcUrl);
        if (host != null && !host.isEmpty() && isInternalHost(host)
                && !resolveAllowedInternalHosts().contains(host.toLowerCase())) {
            throw new NopException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                    .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                    .param(NopMetadataErrors.ARG_RAW_JDBC_URL, jdbcUrl)
                    .param("reason", "internal/link-local/loopback host not in allowed-hosts: " + host);
        }
    }

    /**
     * 脱敏 jdbcUrl 中的凭据：移除 {@code user:password@} 段，保留其余部分不变。
     * <ul>
     *   <li>{@code jdbc:mysql://user:pass@host:3306/db} → {@code jdbc:mysql://host:3306/db}</li>
     *   <li>{@code jdbc:mysql://host:3306/db} → {@code jdbc:mysql://host:3306/db}（无变化）</li>
     *   <li>{@code jdbc:h2:mem:test} → {@code jdbc:h2:mem:test}（无变化）</li>
     * </ul>
     */
    public static String redactJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) return null;
        return CREDENTIAL_PATTERN.matcher(jdbcUrl).replaceAll("$1");
    }

    /** 从 jdbcUrl 粗提取 host（jdbc:h2:mem / jdbc:h2:file 不返回 host，跳过内网校验）。 */
    private static String extractHost(String jdbcUrl) {
        // jdbc:mysql://host:port/db  |  jdbc:postgresql://host:port/db
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            // jdbc:h2:mem:xxx / jdbc:h2:file:xxx → 不做 host 检查（本地内存/文件）
            return null;
        }
        String rest = jdbcUrl.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        int comma = rest.indexOf(',');
        int q = rest.indexOf('?');
        int end = minPositive(minPositive(slash, comma), q);
        String hostPort = end > 0 ? rest.substring(0, end) : rest;
        int colon = hostPort.indexOf(':');
        String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;
        return host.isEmpty() ? null : host;
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /** 是否内网/保留段主机（RFC1918 + RFC3927 link-local + loopback）。 */
    private static boolean isInternalHost(String host) {
        String h = host.toLowerCase();
        if ("localhost".equals(h) || h.endsWith(".localhost")) {
            return true;
        }
        if (h.equals("127.0.0.1") || h.startsWith("127.")) {
            return true;
        }
        if (h.startsWith("10.") || h.startsWith("192.168.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字段不算 RFC1918，落入后续检查
                }
            }
        }
        if (h.startsWith("169.254.")) {
            return true;
        }
        // IPv6 loopback
        if ("::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h)) {
            return true;
        }
        return false;
    }

    /** AR-02: driverClassName 必须在白名单内（防任意类加载攻击）。 */
    private static void validateDriverClassName(String driverClassName) {
        if (!ALLOWED_DRIVER_CLASSES.contains(driverClassName)) {
            throw new NopException(NopMetadataErrors.ERR_DATASOURCE_DRIVER_NOT_ALLOWED)
                    .param("driverClassName", driverClassName);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConnectionConfig(String connectionConfig, String datasourceType) {
        if (connectionConfig == null || connectionConfig.trim().isEmpty()) {
            throw newNopConfigInvalidException(datasourceType, "connectionConfig is empty");
        }
        Object parsed;
        try {
            parsed = JsonTool.parseBeanFromText(connectionConfig, Object.class);
        } catch (Exception e) {
            throw newNopConfigInvalidException(datasourceType, "connectionConfig is not valid JSON: " + e.getMessage());
        }
        if (!(parsed instanceof Map)) {
            throw newNopConfigInvalidException(datasourceType, "connectionConfig must be a JSON object");
        }
        return (Map<String, Object>) parsed;
    }

    private String requireField(Map<String, Object> cfg, String key, String datasourceType) {
        if (!cfg.containsKey(key)) {
            throw newNopConfigInvalidException(datasourceType, "missing required field: " + key);
        }
        Object value = cfg.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private String requireNonBlank(Map<String, Object> cfg, String key, String datasourceType) {
        String value = requireField(cfg, key, datasourceType);
        if (value.isEmpty()) {
            throw newNopConfigInvalidException(datasourceType, "field must not be blank: " + key);
        }
        return value;
    }

    private String optString(Map<String, Object> cfg, String key) {
        Object value = cfg.get(key);
        return value == null ? null : value.toString().trim();
    }

    private static NopException newNopConfigInvalidException(String datasourceType, String reason) {
        return new NopException(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID)
                .param("datasourceType", datasourceType)
                .param("reason", reason);
    }

    private static NopException newNopConnectException(String datasourceType, SQLException e) {
        String msg = e.getMessage();
        return new NopException(NopMetadataErrors.ERR_DATASOURCE_CONNECT_FAILED, e)
                .param("datasourceType", datasourceType)
                .param("error", msg != null ? msg : e.getClass().getName());
    }
}
