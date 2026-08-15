
package io.nop.metadata.service.connection;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.jdbc.datasource.SimpleDataSource;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.security.HostSecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * AR-02: 危险 JDBC URL 参数/子串（大小写不敏感 contains）。
     *
     * <p>F5（plan 2026-08-14-1133-1）补全触发反射类加载的参数族（RCE-chain 潜力，取决于部署 classpath）：
     * MySQL {@code socketfactory} / {@code statementinterceptors} / {@code detectcustomcollatz}，
     * PostgreSQL {@code sslfactory}（SSL SocketFactory 反射实例化）与 {@code options=}（向 backend
     * 透传命令行风格选项，可绕过应用层约束）。{@code driverClassName} 白名单缓解但不能替代 URL 参数侧的 fail-closed。
     */
    private static final Set<String> DANGEROUS_URL_TOKENS = new HashSet<>(Arrays.asList(
            "allowLoadLocalInfile".toLowerCase(Locale.ROOT),
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
            "tracemaster",
            // F5: 反射类加载参数族（MySQL）
            "socketfactory",
            "statementinterceptors",
            "detectcustomcollatz",
            // F5: 反射类加载 / 选项透传参数族（PostgreSQL）
            "sslfactory",
            "options="));

    /** AR-02: 允许的 JDBC driver 类名白名单（H2/MySQL/PostgreSQL）。 */
    private static final Set<String> ALLOWED_DRIVER_CLASSES = new HashSet<>(Arrays.asList(
            "org.h2.Driver",
            "com.mysql.cj.jdbc.Driver",
            "com.mysql.jdbc.Driver",
            "org.postgresql.Driver"));

    /**
     * F2 re-audit hostless attribute-group sentinel (plan 2026-08-15-1913-1).
     *
     * <p>{@code extractSingleHost} 对含 {@code =}/{@code (}/{@code )} 却无
     * {@code host=} 键命中的属性组段返回本哨兵，由 {@code validateJdbcUrl} 统一抛
     * {@code ERR_DATASOURCE_JDBC_URL_BLOCKED}——static 提取链路无 jdbcUrl 上下文，
     * 哨兵 + 集中抛出是避免错误参数丢失脱敏的最小方案。取非主机形状串
     * {@code "("}，即使漏过哨兵等值判断也会被 {@code isPlausibleHostShape}
     * 拒绝（纵深）。
     */
    private static final String HOSTLESS_ATTRIBUTE_GROUP = "(";

    /** AR-02: 默认建连超时秒数（{@link SimpleDataSource#setLoginTimeout} 是 no-op，实际靠 {@link DriverManager#setLoginTimeout}）。 */
    public static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 5;

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
            LOG.warn(NopMetadataErrors.ERR_DATASOURCE_SECURITY_CHECK_SKIPPED.getErrorCode() + ": DriverManager.setLoginTimeout denied by security manager", se);
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
            String trimmed = token.trim().toLowerCase(Locale.ROOT);
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
            LOG.warn(NopMetadataErrors.ERR_DATASOURCE_TEST_CONNECT_FAILED.getErrorCode() + ": testConnect failed for datasourceType={}", datasourceType, e);
            result.put("connected", false);
            result.put("error", NopMetadataErrors.ERR_DATASOURCE_TEST_CONNECT_FAILED.getErrorCode() + ": Connection failed");
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
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED)
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
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        boolean protocolOk = false;
        for (String proto : ALLOWED_JDBC_PROTOCOLS) {
            if (lower.startsWith(proto)) {
                protocolOk = true;
                break;
            }
        }
        if (!protocolOk) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                    .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                    .param("reason", "protocol not in whitelist (mysql/postgresql/h2)");
        }
        // (2) 危险参数黑名单
        for (String dangerous : DANGEROUS_URL_TOKENS) {
            if (lower.contains(dangerous)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                        .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                        .param("reason", "dangerous parameter/token present: " + dangerous);
            }
        }
        // (3) 主机白名单：默认禁内网（RFC1918 + link-local + loopback + 0.0.0.0/8 + IP 记法变体归一化）
        // F2（plan 2026-08-14-0707-1）：对 JDBC URL 中**每一**主机执行校验，关闭多主机 SSRF 绕过。
        // F2 再审计（plan 2026-08-15-1913-1）：主机集合同时覆盖 authority 段与 query 中
        // host=/hostaddr= 参数主机（extractHosts 内合并），驱动语义级绕过（pgjdbc query
        // host= 完全覆盖 authority 主机）在校验层不再可达。
        for (String host : extractHosts(jdbcUrl)) {
            // F2 再审计：hostless 属性组（如 (port=3306)、address=(port=3306)，无 host= 键）
            // ——驱动取隐式 localhost（MySQL Connector/J hostless 属性组默认 localhost），
            // SSRF 语义等价内网主机，fail-closed 拒绝。
            if (HOSTLESS_ATTRIBUTE_GROUP.equals(host)) {
                throw blocked(jdbcUrl, "hostless attribute group"
                        + " (driver implicit localhost) not allowed");
            }
            // F7（plan 2026-08-14-1133-1）：空/畸形主机 fail-closed。extractHosts 对 jdbc:mysql:///db
            // （空主机）和 jdbc:mysql://:3306/db（空主机带端口）会返回非主机形状串（"/db" / ":3306"），
            // HostSecurityUtil 判其为外部 → 静默放行（当前无害——驱动拒绝空主机——但属 "lucky fail-closed"
            // 而非 enforced fail-closed）。这里显式拒绝非主机形状串，不再依赖驱动拒绝。
            if (!isPlausibleHostShape(host)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                        .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                        .param("reason", "host unparseable: " + host);
            }
            if (HostSecurityUtil.isInternalHost(host)
                    && !resolveAllowedInternalHosts().contains(host.toLowerCase(Locale.ROOT))) {
                throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED)
                        .param("jdbcUrl", redactJdbcUrl(jdbcUrl))
                        .param("reason", "internal/link-local/loopback host not in allowed-hosts: " + host);
            }
        }
    }

    /**
     * Build blocked-url exception with redacted jdbcUrl.
     *
     * @param jdbcUrl raw JDBC URL（脱敏后入 error param）
     * @param reason  拒绝原因
     * @return 已附加 jdbcUrl/reason 参数的异常实例
     */
    private static NopException blocked(String jdbcUrl, String reason) {
        NopMetadataException e = new NopMetadataException(
                NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED);
        return e.param("jdbcUrl", redactJdbcUrl(jdbcUrl)).param("reason", reason);
    }

    /**
     * F7：判断提取出的 host 段是否为合理的主机形状。拒绝：
     * <ul>
     *   <li>空串/null（{@code jdbc:mysql:///db}）</li>
     *   <li>以 {@code /} 开头（authority 解析出 path 片段）</li>
     *   <li>以 {@code (} 开头（属性组片段，非主机形状——F2 再审计纵深：hostless 哨兵
     *       正常路径先行拦截）</li>
     *   <li>含 {@code %}（percent 编码残片：query host 值解码失败原样保留 /
     *       authority 字面 {@code %}，均非驱动可解析主机名，fail-closed）</li>
     *   <li>以 {@code :} 开头且无第二个冒号——纯端口，无主机（{@code jdbc:mysql://:3306/db}）</li>
     * </ul>
     * 合法主机：以字母/数字开头，或以 {@code :} 开头但含第二个冒号（无括号 IPv6 字面量如 {@code ::1}，
     * 经 {@link HostSecurityUtil#isInternalHost} 判定），或数字开头（十进制 IP 字面量）。
     * 不以 {@code [} 开头——{@code extractHosts} 已剥离 IPv6 方括号。
     */
    private static boolean isPlausibleHostShape(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        char c = host.charAt(0);
        if (c == '/') {
            // path 片段，非主机形状
            return false;
        }
        if (c == '(') {
            // 属性组片段（如 (port=3306)），非主机形状
            return false;
        }
        if (host.indexOf('%') >= 0) {
            // percent 编码残片，非驱动可解析主机名（fail-closed：解码失败不得静默放行）
            return false;
        }        if (c == ':') {
            // 纯端口（:3306）非主机形状；无括号 IPv6 字面量（::1）含第二个冒号 → 合法主机
            return host.indexOf(':', 1) >= 0;
        }
        return true;
    }

    /**
     * 脱敏 jdbcUrl 中的凭据：移除 {@code user:password@} 段，保留其余部分不变。
     * <ul>
     *   <li>{@code jdbc:mysql://user:pass@host:3306/db} → {@code jdbc:mysql://host:3306/db}</li>
     *   <li>{@code jdbc:mysql://host:3306/db} → {@code jdbc:mysql://host:3306/db}（无变化）</li>
     *   <li>{@code jdbc:h2:mem:test} → {@code jdbc:h2:mem:test}（无变化）</li>
     * </ul>
     *
     * <p>F6（plan 2026-08-14-1133-1）：原 {@code CREDENTIAL_PATTERN} 正则 {@code (://)([^:@/]+)(?::[^@/]*)?@}
     * 在用户名/口令含 {@code @} 时于第一个 {@code @} 处停止，口令尾部泄漏进 redacted 错误消息
     * （{@code user:p@ss@host} → {@code ss@host} 泄漏）。改为与 {@code extractHosts} 自身逻辑一致：
     * 定位 authority 段（{@code ://} 后到首个 {@code /} 或 {@code ?} 之间），用 {@code lastIndexOf('@')}
     * 找 userinfo 边界，substring-replace userinfo 段（含末尾 {@code @}）。多 {@code @} 极端用例下
     * 仅保留最后一个 {@code @} 之后的内容（host 段不含 {@code @}），凭据不再泄漏。
     */
    public static String redactJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            // jdbc:h2:mem:xxx / jdbc:h2:file:xxx → 无 authority，不含 userinfo
            return jdbcUrl;
        }
        int authorityStart = schemeEnd + 3;
        // authority 终止于首个 '/' 或 '?'（path / query 中的 '@' 不属于 userinfo）
        int authorityEnd = jdbcUrl.length();
        int slash = jdbcUrl.indexOf('/', authorityStart);
        int q = jdbcUrl.indexOf('?', authorityStart);
        if (slash >= 0 && slash < authorityEnd) {
            authorityEnd = slash;
        }
        if (q >= 0 && q < authorityEnd) {
            authorityEnd = q;
        }
        String authority = jdbcUrl.substring(authorityStart, authorityEnd);
        int lastAt = authority.lastIndexOf('@');
        if (lastAt < 0) {
            return jdbcUrl;
        }
        // 剥离 userinfo 段：authority 起点 到 最后一个 '@'（含 '@'）
        return jdbcUrl.substring(0, authorityStart) + jdbcUrl.substring(authorityStart + lastAt + 1);
    }

    /**
     * 从 jdbcUrl 提取**所有**主机（F2 修复，plan 2026-08-14-0707-1）。
     *
     * <p>原 {@code extractHost} 仅在第一个逗号处截断 authority，只校验第一主机，导致多主机 URL
     * （MySQL Connector/J / PostgreSQL JDBC 官方支持的逗号分隔与 {@code address=(host=...)} 形式）
     * 的第二（及以后）主机未经内网校验，构成 SSRF 绕过。
     *
     * <p>本方法覆盖三种 MySQL/PG 多主机 URL 语法（对照 MySQL Connector/J 官方 URL 语法）：
     * <ul>
     *   <li>逗号分隔：{@code jdbc:mysql://h1,h2:port/db}（主要场景）</li>
     *   <li>{@code address=} 形式：{@code jdbc:mysql://address=(host=h1)(port=3306),address=(host=h2)(port=3306)/db}</li>
     *   <li>key-value 形式：{@code jdbc:mysql://(host=h1,port=3306),(host=h2,port=3306)/db}</li>
     * </ul>
     *
     * <p>jdbc:h2:mem / jdbc:h2:file 不返回 host（跳过内网校验，本地内存/文件）。
     *
     * <p>MA7.2-01 兼容：(a) 剥离 userinfo（{@code user:pass@host} 取最后一个 {@code @} 之后）；
     * (b) 支持 IPv6 字面量 {@code [::1]} / {@code [::ffff:127.0.0.1]}，IPv4-mapped 形式归一化为 IPv4 段。
     */
    private static List<String> extractHosts(String jdbcUrl) {
        // jdbc:mysql://host:port/db  |  jdbc:postgresql://host:port/db
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            // jdbc:h2:mem:xxx / jdbc:h2:file:xxx → 不做 host 检查（本地内存/文件）
            return Collections.emptyList();
        }
        String rest = jdbcUrl.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        int q = rest.indexOf('?');
        int end = minPositive(slash, q);
        String authority = end > 0 ? rest.substring(0, end) : rest;
        // 剥离 userinfo：user:pass@host 形式取最后一个 @ 之后（用户名/密码可能含 @ 编码变体）
        int lastAt = authority.lastIndexOf('@');
        if (lastAt >= 0) {
            authority = authority.substring(lastAt + 1);
        }
        // F2：按顶层逗号（paren-depth=0）切分为多主机段——不切分括号内的逗号（如 (host=h1,port=3306)）
        List<String> hosts = new ArrayList<>();
        for (String segment : splitTopLevelCommas(authority)) {
            String host = extractSingleHost(segment);
            if (host != null && !host.isEmpty()) {
                hosts.add(host);
            }
        }
        // F2 再审计（plan 2026-08-15-1913-1）：query 中的 host=/hostaddr= 参数主机
        // 同样纳入逐主机校验（pgjdbc Driver.parseURL 语义：query host= 完全覆盖
        // authority 主机——不提取即整层校验被绕过）
        hosts.addAll(extractQueryHosts(jdbcUrl));
        return hosts;
    }

    /**
     * Extract hosts from URL query host= / hostaddr= parameters.
     *
     * <p>F2 再审计（plan 2026-08-15-1913-1），按 pgjdbc {@code Driver.parseURL}
     * 语义对齐：
     * <ul>
     *   <li>参数名与参数值均做 percent-decode 后匹配/校验
     *       （{@code ?host=%31%32%37...} 解码后校验）；</li>
     *   <li>参数名大小写不敏感（{@code Host=} / {@code HOST=} 同
     *       {@code host=}）；</li>
     *   <li>重复参数逐值校验（{@code ?host=a&host=b} 两个值都提取），单值内
     *       逗号分隔（pgjdbc 多主机语法）同样逐值提取；</li>
     *   <li>{@code hostaddr=} 同 {@code host=} 处置（pgjdbc 中二者等价指定
     *       连接目标主机）；</li>
     *   <li>空值（{@code ?host=} / 裸 {@code ?host}）= 驱动回落默认主机
     *       （localhost）→ hostless 哨兵 fail-closed；</li>
     *   <li>percent-decode 失败的值原样保留（含 {@code %}，由
     *       {@code isPlausibleHostShape} 拒绝，不静默放行）；值含
     *       {@code =}/{@code (} 等属性组字符由 {@code extractSingleHost}
     *       哨兵拒绝。</li>
     * </ul>
     *
     * @param jdbcUrl 完整 JDBC URL
     * @return query 部分提取到的主机列表（可能含 hostless 哨兵）
     */
    private static List<String> extractQueryHosts(final String jdbcUrl) {
        int schemeEnd = jdbcUrl.indexOf("://");
        if (schemeEnd < 0) {
            return Collections.emptyList();
        }
        // "://" 内不含 '?'，从 schemeEnd 起扫描与从 schemeEnd+3 起等价
        int q = jdbcUrl.indexOf('?', schemeEnd);
        if (q < 0) {
            return Collections.emptyList();
        }
        List<String> hosts = new ArrayList<>();
        for (String param : jdbcUrl.substring(q + 1).split("&")) {
            int eq = param.indexOf('=');
            String rawName = eq >= 0 ? param.substring(0, eq) : param;
            String name = percentDecode(rawName);
            if (name == null) {
                // 参数名解码失败：驱动同样无法识别该参数（不构成主机指定），跳过
                continue;
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!"host".equals(lowerName) && !"hostaddr".equals(lowerName)) {
                continue;
            }
            if (eq < 0) {
                // 裸 host/hostaddr token（无 =）：pgjdbc 语义下值为空 → 默认主机
                hosts.add(HOSTLESS_ATTRIBUTE_GROUP);
                continue;
            }
            String rawValue = param.substring(eq + 1);
            String value = percentDecode(rawValue);
            if (value == null) {
                value = rawValue;
            }
            if (value.isEmpty()) {
                // 空值 = 驱动回落默认主机（localhost），fail-closed
                hosts.add(HOSTLESS_ATTRIBUTE_GROUP);
                continue;
            }
            for (String part : value.split(",")) {
                String host = extractSingleHost(part);
                if (host != null && !host.isEmpty()) {
                    hosts.add(host);
                }
            }
        }
        return hosts;
    }

    /**
     * percent-decode（UTF-8）.
     *
     * @param s 待解码串
     * @return 不含 {@code %} 时原样返回；非法转义返回 null（由调用方 fail-closed 处理）
     */
    private static String percentDecode(final String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // INV-SILENT-SWALLOW（plan 2026-08-14-1448-2 benign-miss 形式化，沿
            // AggregationHelper.toBigDecimal 先例）：非法 percent 转义是预期解码 miss——
            // null 返回值即文档化 fail-closed 信号（调用方拒收），DEBUG 日志保留可见信号不静默吞
            String code = NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode();
            LOG.debug(code + ": percentDecode null (illegal percent escape, caller fails closed)", e);
            return null;
        }
    }

    /**
     * 按 paren-depth=0 处的逗号切分（不切分括号内的逗号）。
     * 例：{@code h1,(host=h2,port=3306),h3} → {@code ["h1", "(host=h2,port=3306)", "h3"]}。
     */
    private static List<String> splitTopLevelCommas(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth > 0) {
                    depth--;
                }
            } else if (c == ',' && depth == 0) {
                result.add(s.substring(start, i));
                start = i + 1;
            }
        }
        result.add(s.substring(start));
        return result;
    }

    /**
     * 从单个主机段提取主机名。覆盖：
     * <ul>
     *   <li>MySQL Connector/J key-value/address 形式（含 {@code host=} 键）→ 取 {@code host=} 的值</li>
     *   <li>hostless 属性组（含 {@code =}/{@code (}/{@code )} 但无 {@code host=} 键命中，
     *       如 {@code (port=3306)}、{@code address=(port=3306)}）→ 返回
     *       {@link #HOSTLESS_ATTRIBUTE_GROUP} 哨兵（F2 再审计：驱动对 hostless
     *       属性组取隐式 localhost，fail-closed 拒绝。键匹配
     *       {@code HOST_KEY_VALUE_PATTERN} 大小写敏感，{@code (HOST=...)} 段
     *       无键命中同样按 hostless 拒绝——驱动侧键大小写不敏感，fail-closed
     *       方向安全，误伤面接受并在 owner doc 记载）</li>
     *   <li>普通 host / [ipv6] / host:port → 复用既有 IPv6/端口剥离逻辑</li>
     * </ul>
     */
    private static String extractSingleHost(String rawSegment) {
        if (rawSegment == null) {
            return null;
        }
        String segment = rawSegment.trim();
        if (segment.isEmpty()) {
            return null;
        }
        // F2：key-value/address 形式（host=X）。hostname 不含 '='，故 host= 是 key-value 形式的可靠标志。
        String kvHost = extractHostKeyValue(segment);
        if (kvHost != null) {
            return kvHost;
        }
        // F2 再审计：属性组字符（= / 括号）却无 host= 键命中 → hostless 属性组
        // 哨兵（驱动隐式 localhost）
        if (segment.indexOf('=') >= 0 || segment.indexOf('(') >= 0
                || segment.indexOf(')') >= 0) {
            return HOSTLESS_ATTRIBUTE_GROUP;
        }
        return extractPlainHost(segment);
    }

    /**
     * 从 key-value/address 段中提取 {@code host=VALUE} 的值（MySQL Connector/J 多主机 URL 语法）。
     * VALUE 终止于 {@code )} 或 {@code ,} 或段尾；支持 {@code [ipv6]} 括号形式归一化。
     */
    private static final Pattern HOST_KEY_VALUE_PATTERN =
            Pattern.compile("host\\s*=\\s*(\\[[^\\]]*\\]|[^\\s),]+)");

    private static String extractHostKeyValue(String segment) {
        Matcher m = HOST_KEY_VALUE_PATTERN.matcher(segment);
        if (!m.find()) {
            return null;
        }
        String val = m.group(1);
        // 括号 IPv6 形式：host=[::ffff:127.0.0.1] → 归一化
        if (val.startsWith("[")) {
            int closeBracket = val.indexOf(']');
            if (closeBracket > 0) {
                return normalizeIpv4MappedHost(val.substring(1, closeBracket));
            }
            return null;
        }
        return val;
    }

    /**
     * 从普通主机段（无 {@code host=} 键）提取主机名：剥离端口，处理 IPv6 字面量。
     * 与 MA7.2-01 既有单主机语义一致（无括号 IPv6 + 端口判定保留）。
     */
    private static String extractPlainHost(String hostPort) {
        // IPv6 字面量形如 [::1] 或 [::ffff:127.0.0.1]:3306
        if (hostPort.startsWith("[")) {
            int closeBracket = hostPort.indexOf(']');
            if (closeBracket > 0) {
                return normalizeIpv4MappedHost(hostPort.substring(1, closeBracket));
            }
            return null;
        }
        // 无括号 IPv6 带端口（::1:3306 / fe80::1:5432 / ::ffff:127.0.0.1:3306）：按 lastIndexOf(':') 分割，
        // 仅当尾部为纯数字（端口候选）且头部解析为 IP 地址（16 字节 IPv6 字面量或 4 字节 ::ffff: IPv4-mapped，
        // 仅字面量解析绝不触发 DNS）时取头部；否则回退既有首冒号分割语义（127.0.0.1:3306 / localhost:3306 /
        // example.com:3306 等单冒号形态维持现状，绝不把"含端口整体串"交给 HostSecurityUtil——整体串含冒号
        // 会走 IPv6 判定路径解析失败放行，导致基础防护全线失效）；无端口形态（::1）原样返回。
        int lastColon = hostPort.lastIndexOf(':');
        if (hostPort.indexOf(':') != lastColon) {
            String tail = hostPort.substring(lastColon + 1);
            if (isAllDigits(tail) && isIpLiteral(hostPort.substring(0, lastColon))) {
                return hostPort.substring(0, lastColon);
            }
        }
        int colon = hostPort.indexOf(':');
        String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;
        return host.isEmpty() ? null : host;
    }

    /** IPv4-mapped IPv6（{@code ::ffff:a.b.c.d}）→ IPv4 段；其余形式原样返回。 */
    private static String normalizeIpv4MappedHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        int idx = h.lastIndexOf("::ffff:");
        if (idx < 0) {
            return host;
        }
        String tail = h.substring(idx + 7);
        return isIpv4Literal(tail) ? tail : host;
    }

    /** 尾段是否为纯数字（端口候选）。空串不算（`::1:` 尾空段不是端口）。 */
    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * 头部是否为 IP 字面量（16 字节 IPv6 字面量，或 4 字节 {@code ::ffff:} IPv4-mapped 形式——
     * JDK 的 getByName 对 mapped 形式返回 Inet4Address）。与 HostSecurityUtil 判内网前的剥离判读共用同一规则。
     *
     * <p>DNS 安全：仅对"含至少 2 个冒号且字符集 ⊆ [0-9a-fA-F:.]"的串尝试字面量解析（合法 IPv6 字面量
     * 至少含 2 个冒号，hostname 头部被前置排除），解析失败即非字面量，不会把 hostname 交给解析器。
     * 十六进制大小写均接受——URL 原样传入未小写化，`FE80::1:3306` 大写形态必须命中同一判定（不按前缀特判）。
     */
    private static boolean isIpLiteral(String head) {
        int colons = 0;
        for (int i = 0; i < head.length(); i++) {
            char c = head.charAt(i);
            if (c == ':') {
                colons++;
            } else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F') || c == '.')) {
                return false;
            }
        }
        if (colons < 2) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(head);
            byte[] b = addr.getAddress();
            return b != null && (b.length == 4 || b.length == 16);
        } catch (UnknownHostException e) {
            LOG.debug(NopMetadataErrors.ERR_DATASOURCE_HOST_RESOLVE_SKIPPED.getErrorCode() + ": IP literal resolution failed", e);
            return false;
        }
    }

    private static boolean isIpv4Literal(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) {
                return false;
            }
            for (int i = 0; i < p.length(); i++) {
                if (!Character.isDigit(p.charAt(i))) {
                    return false;
                }
            }
            try {
                if (Integer.parseInt(p) > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                LOG.debug(NopMetadataErrors.ERR_DATASOURCE_PORT_PARSE_SKIPPED.getErrorCode() + ": octet parse failed", e);
                return false;
            }
        }
        return true;
    }

    private static int minPositive(int a, int b) {
        if (a < 0) return b;
        if (b < 0) return a;
        return Math.min(a, b);
    }

    /** AR-02: driverClassName 必须在白名单内（防任意类加载攻击）。 */
    private static void validateDriverClassName(String driverClassName) {
        if (!ALLOWED_DRIVER_CLASSES.contains(driverClassName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_DRIVER_NOT_ALLOWED)
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
        return new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CONFIG_INVALID)
                .param("datasourceType", datasourceType)
                .param("reason", reason);
    }

    private static NopException newNopConnectException(String datasourceType, SQLException e) {
        String msg = e.getMessage();
        return new NopMetadataException(NopMetadataErrors.ERR_DATASOURCE_CONNECT_FAILED, e)
                .param("datasourceType", datasourceType)
                .param("error", msg != null ? msg : e.getClass().getName());
    }
}
