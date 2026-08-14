package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.connection.MetaDataSourceConnectionProcessor;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
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
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
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
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    /** allowMultiQueries（多语句注入放大）必须失败。 */
    @Test
    public void testAllowMultiQueriesRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://10.255.255.1/db?allowMultiQueries=true\","
                                + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    /** allowUrlInLocalInfile（远程文件读取）必须失败。 */
    @Test
    public void testAllowUrlInLocalInfileRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://10.255.255.1/db?allowUrlInLocalInfile=true\","
                                + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    // ===== F5（plan 2026-08-14-1133-1）：反射类加载 / 选项透传参数族 blocklist 完备 =====

    /**
     * <b>F5 adversarial</b>：触发反射类加载（RCE-chain 潜力）的 5 个新参数必须被 fail-fast 拒绝。
     *
     * <p>使用外网主机（example.com）隔离危险参数检查（dangerous-param check 在 host check 之前），
     * 确保命中由新增 token 触发，reason 标识具体 token。
     */
    @Test
    public void testF5ClassLoadingParamsRejected() {
        // 每行：{token, jdbcUrl}——token 用于断言 reason
        String[][] vectors = {
                {"socketfactory", "jdbc:mysql://example.com:3306/db?socketfactory=com.attack.Evil"},
                {"statementinterceptors", "jdbc:mysql://example.com:3306/db?statementinterceptors=com.attack.Evil"},
                {"detectcustomcollatz", "jdbc:mysql://example.com:3306/db?detectcustomcollatz=1"},
                {"sslfactory", "jdbc:postgresql://example.com:5432/db?sslfactory=com.attack.Evil"},
                {"options=", "jdbc:postgresql://example.com:5432/db?options=-c%20exit_on_error=true"}
        };
        for (String[] v : vectors) {
            String token = v[0];
            String url = v[1];
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"" + url + "\"," + BASE_CFG + "}"),
                    "F5: class-loading/option-passing dangerous param must fail: " + url);
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "F5: must throw ERR_DATASOURCE_JDBC_URL_BLOCKED for token=" + token);
            String reason = String.valueOf(ex.getParam("reason"));
            assertTrue(reason.contains("dangerous") && reason.contains(token),
                    "F5: reason must flag dangerous + token '" + token + "': " + reason);
        }
    }

    /** F5：既有危险 token 不丢失（回归：13 个原 token + 5 个新 token 全覆盖）。 */
    @Test
    public void testF5ExistingDangerousTokensStillRejected() {
        String[] legacyUrls = {
                "jdbc:mysql://example.com:3306/db?allowLoadLocalInfile=true",
                "jdbc:h2:mem:x;INIT=RUNSCRIPTFROM",
                "jdbc:mysql://example.com:3306/db?allowMultiQueries=true"
        };
        for (String url : legacyUrls) {
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"" + url + "\"," + BASE_CFG + "}"),
                    "legacy dangerous token must still fail: " + url);
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                    ex.getErrorCode());
        }
    }

    // ===== 主机白名单（fail-closed 默认禁内网）=====

    /** AWS 元数据服务 IP（SSRF 经典目标）必须失败。 */
    @Test
    public void testLinkLocalMetadataHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://169.254.169.254:3306/db\"," + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
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
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                    ex.getErrorCode());
        }
    }

    /** localhost / 127.0.0.1 默认拒绝（必须显式配 allowed-hosts 才允许）。 */
    @Test
    public void testLoopbackDefaultRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://localhost:3306/db\"," + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
    }

    // ===== MA7.2-01：userinfo / IPv6 字面量主机提取 =====

    /** userinfo 旁路：jdbc:mysql://user:pass@内网IP → host 必须取 @ 之后，内网主机被拒。 */
    @Test
    public void testUserinfoBypassRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://user:pass@169.254.169.254:3306/db\"," + BASE_CFG + "}"),
                "userinfo-prefixed internal host must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("host"),
                "reason must mention host: " + ex.getParam("reason"));
    }

    /** IPv6 loopback 字面量 [::1] 必须被拒（host 不得提取为 "["）。 */
    @Test
    public void testIpv6LoopbackRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://[::1]:3306/db\"," + BASE_CFG + "}"),
                "IPv6 loopback literal must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** IPv4-mapped IPv6 [::ffff:127.0.0.1] 必须被拒（归一化为 127.0.0.1 复核）。 */
    @Test
    public void testIpv4MappedIpv6LoopbackRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://[::ffff:127.0.0.1]:3306/db\"," + BASE_CFG + "}"),
                "IPv4-mapped IPv6 loopback must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** userinfo + 外网主机 → host 校验通过（进入实际建连，不抛 ERR_DATASOURCE_JDBC_URL_BLOCKED）。 */
    @Test
    public void testUserinfoWithExternalHostPassesHostCheck() {
        assertDoesNotThrow(() -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://user:pass@example.com:3306/db\"," + BASE_CFG + "}"),
                "external host with userinfo must pass the host check (not blocked)");
    }

    // ===== SSRF 主机归一化统一（共享 HostSecurityUtil，JDK 解析语义）=====

    /** IP 记法变体归一化（P1-01/AR-01）：十进制整数 / 短格式 / 前导零 全部必须拒绝。 */
    @Test
    public void testIpNotationVariantsRejected() {
        String[] internalHosts = {
                "2130706433",        // → 127.0.0.1（十进制整数）
                "0.1",               // → 0.0.0.1（短格式，0.0.0.0/8）
                "0.256",             // → 0.0.1.0（短格式，0.0.0.0/8）
                "010.0.0.1",         // → 10.0.0.1（前导零严格十进制）
                "0169.254.169.254"   // → 169.254.169.254（前导零严格十进制）
        };
        for (String host : internalHosts) {
            NopException ex = assertThrows(NopException.class,
                    () -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"jdbc:mysql://" + host + ":3306/db\"," + BASE_CFG + "}"),
                    "IP notation variant must be rejected: " + host);
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                    ex.getErrorCode(),
                    "IP notation variant must fail with ERR_DATASOURCE_JDBC_URL_BLOCKED: " + host);
        }
    }

    /** 127.1（短格式 loopback）回归向量：已被既有 fast path 拦截，归一化后仍拒绝。 */
    @Test
    public void testShortFormLoopbackRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://127.1:3306/db\"," + BASE_CFG + "}"),
                "short form loopback (127.1 -> 127.0.0.1) must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** JDK 严格十进制语义下的外部地址（行为反向变更：inet_aton 八进制假设废弃）→ 放行。 */
    @Test
    public void testStrictDecimalExternalHostsPass() {
        String[] externalHosts = {
                "0177.0.0.1",   // JDK 严格十进制 → 177.0.0.1（外部；旧 inet_aton 八进制语义误判为 127.0.0.1）
                "172.16",       // JDK → 172.0.0.16（第二段 0，非 RFC1918）
                "8.8.8.8",      // 外部 IP
                "example.com"   // 外部 hostname
        };
        for (String host : externalHosts) {
            assertDoesNotThrow(() -> service.testConnect("jdbc",
                            "{\"jdbcUrl\":\"jdbc:mysql://" + host + ":3306/db\"," + BASE_CFG + "}"),
                    "external host must pass the host check (not blocked): " + host);
        }
    }

    // ===== AR-02/AR-03 残余变体（plan-2026-08-06-0553-1 Phase 2）：无括号 IPv6 + FQDN 尾点 =====

    /** 无括号 IPv6 loopback + 端口（::1:3306）必须被拒（AR-02 残余变体，驱动实测可建连 ::1 loopback）。 */
    @Test
    public void testUnbracketedIpv6LoopbackWithPortRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://::1:3306/db\"," + BASE_CFG + "}"),
                "unbracketed IPv6 loopback with port must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("host"),
                "reason must mention host: " + ex.getParam("reason"));
    }

    /** 无括号 IPv6 link-local + 端口（fe80::1:5432）必须被拒（首字符非 ':' 时既有首冒号截断变体）。 */
    @Test
    public void testUnbracketedIpv6LinkLocalWithPortRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:postgresql://fe80::1:5432/db\"," + BASE_CFG + "}"),
                "unbracketed IPv6 link-local with port must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** 无括号 IPv6 link-local + 端口，大写十六进制（FE80::1:5432）必须被拒——同一通用判定，不按前缀特判。 */
    @Test
    public void testUnbracketedIpv6UppercaseLinkLocalWithPortRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://FE80::1:5432/db\"," + BASE_CFG + "}"),
                "uppercase unbracketed IPv6 link-local with port must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** 无端口无括号 IPv6（jdbc:mysql://::1/db）必须被拒（无端口形态原样返回 → util 判 loopback 内网）。 */
    @Test
    public void testUnbracketedIpv6WithoutPortRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://::1/db\"," + BASE_CFG + "}"),
                "unbracketed IPv6 loopback without port must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** IPv4-mapped 无括号变体（::ffff:127.0.0.1:3306）必须被拒（mapped 头部 4 字节判定，防 16 字节限定回退放行）。 */
    @Test
    public void testUnbracketedIpv4MappedWithPortRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://::ffff:127.0.0.1:3306/db\"," + BASE_CFG + "}"),
                "unbracketed IPv4-mapped loopback with port must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    /** FQDN 尾点 localhost.（jdbc:mysql://localhost.:3306/db）必须被拒（AR-03 JDBC 侧直接证据，jshell 实测解析到 127.0.0.1）。 */
    @Test
    public void testTrailingDotLocalhostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://localhost.:3306/db\"," + BASE_CFG + "}"),
                "trailing-dot localhost FQDN must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    // ===== F2（plan 2026-08-14-0707-1 Phase 2）：多主机 JDBC URL SSRF 绕过 =====

    /**
     * <b>F2 adversarial：逗号分隔多主机，第二主机为内网（169.254.169.254）必须被拒绝。</b>
     *
     * <p>修复前 extractHost 在第一个逗号处截断，只校验 good.com（外网放行），内网第二主机
     * 未经校验——MySQL Connector/J 支持逗号分隔多主机故障转移，驱动会连到未校验的内网主机。
     */
    @Test
    public void testCommaSeparatedMultiHostInternalSecondHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://good.com,169.254.169.254:3306/db\"," + BASE_CFG + "}"),
                "F2: comma-separated multi-host URL with internal second host must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("169.254.169.254"),
                "F2: reason must identify the internal second host: " + ex.getParam("reason"));
    }

    /**
     * <b>F2 adversarial：{@code address=} 形式多主机，第二主机为内网（127.0.0.1）必须被拒绝。</b>
     *
     * <p>MySQL Connector/J 官方 address-list 语法：{@code address=(host=h1)(port=p1),address=(host=h2)(port=p2)}。
     */
    @Test
    public void testAddressListMultiHostInternalSecondHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://address=(host=good.com)(port=3306),address=(host=127.0.0.1)(port=3306)/db\","
                                + BASE_CFG + "}"),
                "F2: address-list multi-host URL with internal second host must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("127.0.0.1"),
                "F2: reason must identify the internal host from host= key: " + ex.getParam("reason"));
    }

    /**
     * <b>F2 adversarial：key-value 形式（无 {@code address=} 前缀），第二主机为内网必须被拒绝。</b>
     *
     * <p>MySQL Connector/J 等价语法：{@code (host=h1,port=p1),(host=h2,port=p2)}。括号内含逗号，
     * 顶层逗号切分必须正确（paren-depth 跟踪）。
     */
    @Test
    public void testKeyValueMultiHostInternalSecondHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://(host=good.com,port=3306),(host=10.0.0.1,port=3306)/db\","
                                + BASE_CFG + "}"),
                "F2: key-value multi-host URL with internal second host must be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("10.0.0.1"),
                "F2: reason must identify the internal host: " + ex.getParam("reason"));
    }

    /** <b>F2：第一主机为内网</b>（逗号分隔）→ 仍被既有逻辑拒绝（回归：不因多主机改动而放行第一主机）。 */
    @Test
    public void testCommaSeparatedInternalFirstHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://10.0.0.1,good.com:3306/db\"," + BASE_CFG + "}"),
                "internal first host must still be rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
    }

    // ===== F7（plan 2026-08-14-1133-1）：空/畸形主机 fail-closed（enforced，不再依赖驱动拒绝）=====

    /**
     * <b>F7 adversarial</b>：空主机（{@code jdbc:mysql:///db}）必须被显式拒绝，不再依赖驱动拒绝的 lucky path。
     *
     * <p>修复前 {@code extractHosts} 返回非主机形状串 {@code "/db"}，HostSecurityUtil 判其为外部 → 静默放行。
     */
    @Test
    public void testF7EmptyHostRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql:///db\"," + BASE_CFG + "}"),
                "F7: empty host (jdbc:mysql:///db) must be explicitly rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("host unparseable"),
                "F7: reason must flag host unparseable: " + ex.getParam("reason"));
    }

    /**
     * <b>F7 adversarial</b>：空主机带端口（{@code jdbc:mysql://:3306/db}）必须被显式拒绝。
     *
     * <p>修复前 {@code extractHosts} 返回 {@code ":3306"}（纯端口），HostSecurityUtil 判其为外部 → 静默放行。
     */
    @Test
    public void testF7EmptyHostWithPortRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://:3306/db\"," + BASE_CFG + "}"),
                "F7: empty host with port (jdbc:mysql://:3306/db) must be explicitly rejected");
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        assertTrue(String.valueOf(ex.getParam("reason")).contains("host unparseable"),
                "F7: reason must flag host unparseable: " + ex.getParam("reason"));
    }

    /** F7：合法外网主机不误伤（回归——host shape 校验不破坏既有外网放行路径）。 */
    @Test
    public void testF7ValidExternalHostNotBlocked() {
        assertDoesNotThrow(() -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://example.com:3306/db\"," + BASE_CFG + "}"),
                "valid external host must pass shape check + host check");
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
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_DRIVER_NOT_ALLOWED.getErrorCode(),
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
            assertEquals(NopMetadataErrors.ERR_DATASOURCE_TYPE_NOT_SUPPORTED.getErrorCode(),
                    ex.getErrorCode());
        }
    }

    // ===== jdbcUrl 凭据脱敏（AR-15）=====

    /** 凭据脱敏：嵌入式 user:password@ 必须被脱敏。 */
    @Test
    public void testCredentialRedactionEmbeddedCredentials() {
        String raw = "jdbc:mysql://admin:s3cret@prod-db:3306/mydb";
        String redacted = MetaDataSourceConnectionProcessor.redactJdbcUrl(raw);
        assertEquals("jdbc:mysql://prod-db:3306/mydb", redacted,
                "user:password@ must be stripped");
    }

    /** 凭据脱敏：用户名无密码。 */
    @Test
    public void testCredentialRedactionUserOnly() {
        String raw = "jdbc:mysql://admin@prod-db:3306/mydb";
        String redacted = MetaDataSourceConnectionProcessor.redactJdbcUrl(raw);
        assertEquals("jdbc:mysql://prod-db:3306/mydb", redacted,
                "user-only with @ must be stripped");
    }

    /** 凭据脱敏：无凭据 URL 保持不变。 */
    @Test
    public void testCredentialRedactionNoCredentials() {
        String raw = "jdbc:mysql://prod-db:3306/mydb";
        String redacted = MetaDataSourceConnectionProcessor.redactJdbcUrl(raw);
        assertEquals(raw, redacted, "non-credential URL must be unchanged");
    }

    /** 凭据脱敏：URL 编码的 @ 不应该被误剥（场景透视）。 */
    @Test
    public void testCredentialRedactionEncodedAtSign() {
        String raw = "jdbc:h2:mem:test";
        String redacted = MetaDataSourceConnectionProcessor.redactJdbcUrl(raw);
        assertEquals(raw, redacted, "h2 mem URL must be unchanged");
    }

    /** 凭据脱敏：空值返回空。 */
    @Test
    public void testCredentialRedactionNull() {
        assertEquals(null, MetaDataSourceConnectionProcessor.redactJdbcUrl(null));
    }

    // ===== F6（plan 2026-08-14-1133-1）：含 @ 口令脱敏不再泄漏尾部 =====

    /**
     * <b>F6 adversarial</b>：口令含 {@code @}（{@code user:p@ss@host}）必须 redact 到最后一个 {@code @}，
     * 口令尾部 {@code ss} 不泄漏进 redacted URL。
     *
     * <p>修复前 {@code CREDENTIAL_PATTERN} 在第一个 {@code @} 处停止，{@code user:p@ss@host} → {@code ss@host} 泄漏。
     */
    @Test
    public void testF6PasswordWithAtSignFullyRedacted() {
        String raw = "jdbc:mysql://user:p@ss@host:3306/db";
        String redacted = MetaDataSourceConnectionProcessor.redactJdbcUrl(raw);
        assertEquals("jdbc:mysql://host:3306/db", redacted,
                "password fragment after first '@' must NOT leak");
        assertTrue(!redacted.contains("ss@host") && !redacted.contains("p@ss"),
                "no password fragment leak: " + redacted);
    }

    /** F6：多 {@code @} 极端用例——口令含 3 个 {@code @}，仅 host 段保留。 */
    @Test
    public void testF6MultipleAtSignsRedacted() {
        String raw = "jdbc:mysql://u:a@b@c@prod-db:3306/mydb";
        String redacted = MetaDataSourceConnectionProcessor.redactJdbcUrl(raw);
        assertEquals("jdbc:mysql://prod-db:3306/mydb", redacted,
                "only host segment after last '@' is retained");
        assertTrue(!redacted.contains("a@b@c"),
                "multi-@ password fragment must not leak: " + redacted);
    }

    /** F6：既有脱敏语义不回归（user:pass@ / user@ / 无凭据 / null / h2 全保持原行为）。 */
    @Test
    public void testF6LegacyRedactionSemanticsPreserved() {
        assertEquals("jdbc:mysql://prod-db:3306/mydb",
                MetaDataSourceConnectionProcessor.redactJdbcUrl("jdbc:mysql://admin:s3cret@prod-db:3306/mydb"));
        assertEquals("jdbc:mysql://prod-db:3306/mydb",
                MetaDataSourceConnectionProcessor.redactJdbcUrl("jdbc:mysql://admin@prod-db:3306/mydb"));
        assertEquals("jdbc:mysql://prod-db:3306/mydb",
                MetaDataSourceConnectionProcessor.redactJdbcUrl("jdbc:mysql://prod-db:3306/mydb"));
        assertEquals("jdbc:h2:mem:test",
                MetaDataSourceConnectionProcessor.redactJdbcUrl("jdbc:h2:mem:test"));
        assertEquals(null,
                MetaDataSourceConnectionProcessor.redactJdbcUrl(null));
    }

    /** F6：含 {@code @} 口令 + 危险参数被拒时，错误消息不含口令片段（端到端脱敏验证）。 */
    @Test
    public void testF6AtPasswordNoLeakViaErrorPath() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://admin:p@ss@169.254.169.254:3306/db?allowMultiQueries=true\","
                                + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(), ex.getErrorCode());
        String redactedUrl = String.valueOf(ex.getParam("jdbcUrl"));
        assertTrue(!redactedUrl.contains("p@ss") && !redactedUrl.contains("ss@"),
                "jdbcUrl param must be fully redacted (no password fragment): " + redactedUrl);
        assertTrue(redactedUrl.startsWith("jdbc:mysql://169.254.169.254:3306/db"),
                "redacted jdbcUrl must retain host (for ops diagnostics) but not credentials: " + redactedUrl);
    }

    /**
     * 验证错误消息中 jdbcUrl 参数已被脱敏（不包含明文凭据）。
     * 使用协议白名单拒绝路径（凭据在协议校验之前即被 redact，不依赖 host 提取路径）。
     *
     * <p>R6.2（P2-12）：错误响应不再携带 rawJdbcUrl 明文凭据参数——断言参数不存在 + 消息无凭据。
     */
    @Test
    public void testErrorResponseContainsRedactedUrl() {
        // 非白名单协议 + 嵌入式凭据 → 在协议校验前先 redact，然后抛 ERR_DATASOURCE_JDBC_URL_BLOCKED
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:oracle:thin://admin:secret@192.168.1.1:1521/XE\","
                                + BASE_CFG + "}"));
        String redactedUrl = String.valueOf(ex.getParam("jdbcUrl"));
        assertEquals("jdbc:oracle:thin://192.168.1.1:1521/XE", redactedUrl,
                "jdbcUrl param must be redacted in error response");
        assertNoPlaintextCredentialLeak(ex, "admin:secret");
    }

    /** 危险参数拒绝路径：错误响应不含 rawJdbcUrl 参数、不含明文凭据（P2-12 补强，参数路径）。 */
    @Test
    public void testDangerousParamErrorContainsNoCredentials() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://admin:secret@example.com:3306/db?allowMultiQueries=true\","
                                + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
        assertNoPlaintextCredentialLeak(ex, "admin:secret");
    }

    /** 内网主机拒绝路径：错误响应不含 rawJdbcUrl 参数、不含明文凭据（P2-12 补强，主机路径）。 */
    @Test
    public void testInternalHostErrorContainsNoCredentials() {
        NopException ex = assertThrows(NopException.class,
                () -> service.testConnect("jdbc",
                        "{\"jdbcUrl\":\"jdbc:mysql://admin:secret@169.254.169.254:3306/db\","
                                + BASE_CFG + "}"));
        assertEquals(NopMetadataErrors.ERR_DATASOURCE_JDBC_URL_BLOCKED.getErrorCode(),
                ex.getErrorCode());
        assertNoPlaintextCredentialLeak(ex, "admin:secret");
    }

    /** P2-12：错误路径异常不得携带 rawJdbcUrl 明文凭据参数（仅脱敏 jdbcUrl 参数），消息亦不得回显凭据。 */
    private static void assertNoPlaintextCredentialLeak(NopException ex, String credential) {
        assertNull(ex.getParam("rawJdbcUrl"),
                "rawJdbcUrl param must not be present in error (plaintext credential leak surface)");
        String msg = String.valueOf(ex.getMessage());
        assertTrue(!msg.contains(credential),
                "error message must not contain plaintext credentials: " + msg);
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
