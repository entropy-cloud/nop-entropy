package io.nop.metadata.service;

import io.nop.metadata.service.security.HostSecurityUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HostSecurityUtil 共享主机判定工具专项单测：内网判定语义与 JDK 实际解析
 * （jshell 实测 JDK 21/26 + OS getaddrinfo inet_aton 回退）一致。
 *
 * <p>覆盖全部分支：1-4 段短格式 / 前导零（严格十进制）/ 十进制整数（mod 2^32）/
 * 0.0.0.0/8 / 点分段 mod 2^32 截断 / 0x 十六进制（fail-closed 超集）/ IPv4-mapped /
 * IPv6 字面量 / hostname 前缀 fail-closed / trim。
 *
 * <p>区分性断言：拒绝向量必须为内部判定（true），放行向量必须为外部判定（false）。
 */
public class TestHostSecurityUtil {

    // ===== 正例：判内网（拒绝）=====

    /** 单段整数 0 → 0.0.0.0（0.0.0.0/8）。 */
    @Test
    public void testSingleZeroInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("0"), "0 -> 0.0.0.0 (0.0.0.0/8) must be internal");
    }

    /** 短格式 1 段与 2 段（JDK 位移解析）。 */
    @Test
    public void testShortFormInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("0.1"), "0.1 -> 0.0.0.1 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("0.256"), "0.256 -> 0.0.1.0 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("127.1"), "127.1 -> 127.0.0.1 must be internal");
    }

    /** 十进制整数形式（JDK/OS 均按 mod 2^32 截断）。 */
    @Test
    public void testDecimalIntegerInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("2130706433"), "2130706433 -> 127.0.0.1 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("4294967297"), "4294967297 -> 0.0.0.1 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("18446744073709551616"),
                "2^64 -> 0.0.0.0 (mod 2^32) must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("18446744073709551618"),
                "2^64+2 -> 0.0.0.2 (mod 2^32) must be internal");
    }

    /** 前导零按严格十进制（废弃 inet_aton 八进制）。 */
    @Test
    public void testLeadingZeroStrictDecimalInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("010.0.0.1"), "010.0.0.1 -> 10.0.0.1 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("0127.0.0.1"), "0127.0.0.1 -> 127.0.0.1 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("0169.254.169.254"),
                "0169.254.169.254 -> 169.254.169.254 must be internal");
    }

    /** 标准点分内网段。 */
    @Test
    public void testStandardInternalV4() {
        assertTrue(HostSecurityUtil.isInternalHost("127.0.0.1"));
        assertTrue(HostSecurityUtil.isInternalHost("10.1.2.3"));
        assertTrue(HostSecurityUtil.isInternalHost("192.168.1.1"));
        assertTrue(HostSecurityUtil.isInternalHost("172.16.0.1"));
        assertTrue(HostSecurityUtil.isInternalHost("169.254.169.254"));
    }

    /** 点分段 mod 2^32 截断后落内网（与 OS getaddrinfo inet_aton 回退一致）。 */
    @Test
    public void testWrappedSegmentInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("0.1.2.18446744073709551618"),
                "last segment mod 2^32 = 2 -> 0.1.2.2 (0.0.0.0/8) must be internal");
    }

    /** IPv6 字面量（不带方括号，输入契约）。 */
    @Test
    public void testIpv6LiteralInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("::1"));
        assertTrue(HostSecurityUtil.isInternalHost("0:0:0:0:0:0:0:1"));
        assertTrue(HostSecurityUtil.isInternalHost("fe80::1"));
        assertTrue(HostSecurityUtil.isInternalHost("::ffff:127.0.0.1"));
    }

    /** hostname fast path。 */
    @Test
    public void testHostnameInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("localhost"));
        assertTrue(HostSecurityUtil.isInternalHost("a.localhost"));
    }

    /** 非 IP 字面量命中内网前缀 → fail-closed 拦截（JDK 解析失败/DNS 可达内网均被覆盖）。 */
    @Test
    public void testHostnamePrefixFailClosedInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("127.abc"));
        assertTrue(HostSecurityUtil.isInternalHost("10.0.0.1.nip.io"));
    }

    /** 输入先 trim（防 "localhost " 类绕过）。 */
    @Test
    public void testTrimApplied() {
        assertTrue(HostSecurityUtil.isInternalHost("  localhost  "), "trimmed localhost must be internal");
    }

    /** 0x 十六进制整数形式：fail-closed 超集（JDK 视为歧义/非法，但 OS inet_aton 可解析十六进制）。 */
    @Test
    public void testHexIntegerFailClosedInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("0x7f000001"), "0x7f000001 kept blocked (fail-closed superset)");
        assertTrue(HostSecurityUtil.isInternalHost("0x100000001"), "0x100000001 -> 0.0.0.1 must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("0x7f0000012"), "0x7f0000012 kept blocked (fail-closed superset)");
        assertTrue(HostSecurityUtil.isInternalHost("0X7F000001"), "0X uppercase prefix kept blocked");
    }

    // ===== 负例：判外部（放行）=====

    /** 0177.0.0.1：JDK 严格十进制 → 177.0.0.1（外部）；172.16 → 172.0.0.16（第二段 0 非 RFC1918）。 */
    @Test
    public void testInetAtonOctalLegacyExternal() {
        assertFalse(HostSecurityUtil.isInternalHost("0177.0.0.1"),
                "0177.0.0.1 -> 177.0.0.1 (strict decimal) must be external");
        assertFalse(HostSecurityUtil.isInternalHost("172.16"),
                "172.16 -> 172.0.0.16 (second octet 0, not RFC1918) must be external");
    }

    /** 标准外部地址与 hostname。 */
    @Test
    public void testStandardExternal() {
        assertFalse(HostSecurityUtil.isInternalHost("8.8.8.8"));
        assertFalse(HostSecurityUtil.isInternalHost("172.32.0.1"));
        assertFalse(HostSecurityUtil.isInternalHost("1.1.1.1"));
        assertFalse(HostSecurityUtil.isInternalHost("255.1.1.1"));
        assertFalse(HostSecurityUtil.isInternalHost("18446744073709551871.10.0.1"),
                "first segment mod 2^32 = 255 -> 255.10.0.1 external");
        assertFalse(HostSecurityUtil.isInternalHost("example.com"));
        assertFalse(HostSecurityUtil.isInternalHost("1.1.1.18446744073709551871"),
                "last segment mod 2^32 = 255 -> 1.1.1.255 external");
    }

    /** 非法数字字面量：5+ 段 / 越界段 → 非字面量，hostname 路径判外部（JDK 同样无法解析）。 */
    @Test
    public void testInvalidNumericLiteralExternal() {
        assertFalse(HostSecurityUtil.isInternalHost("1.2.3.4.5"));
        assertFalse(HostSecurityUtil.isInternalHost("1.2.3.4.5.6"));
        assertFalse(HostSecurityUtil.isInternalHost("256.1.1.1"));
        assertFalse(HostSecurityUtil.isInternalHost("1.1.1.256"));
        assertFalse(HostSecurityUtil.isInternalHost("1.16777216"));
        assertFalse(HostSecurityUtil.isInternalHost("1..2"));
        assertFalse(HostSecurityUtil.isInternalHost(".1.2.3"));
        assertFalse(HostSecurityUtil.isInternalHost("1.2.3."));
    }

    /** 非 0x 十六进制/歧义形式 → hostname 路径（JDK 硬拒绝不查 DNS，无连接风险）。 */
    @Test
    public void testAmbiguousOrNonHexExternal() {
        assertFalse(HostSecurityUtil.isInternalHost("0xzz"), "not a hex integer -> hostname path");
        assertFalse(HostSecurityUtil.isInternalHost("0x7f.0.0.1"),
                "dotted hex segment -> JDK rejects, no connection possible");
        assertFalse(HostSecurityUtil.isInternalHost("deadbeef"));
    }

    /** 空串 / null → 外部。 */
    @Test
    public void testEmptyAndNullExternal() {
        assertFalse(HostSecurityUtil.isInternalHost(""));
        assertFalse(HostSecurityUtil.isInternalHost("   "));
        assertFalse(HostSecurityUtil.isInternalHost(null));
    }

    // ===== AR-02/AR-03 残余变体（plan-2026-08-06-0553-1 Phase 1）：FQDN 尾点 + 无括号 IPv6 带端口 =====

    /** red→green：localhost. / a.localhost. 尾点 FQDN——当前前缀不命中判外部，修复后剥离尾点按 localhost 判内网。 */
    @Test
    public void testTrailingDotFqdnInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("localhost."),
                "localhost. (trailing-dot FQDN, resolves to 127.0.0.1) must be internal");
        assertTrue(HostSecurityUtil.isInternalHost("a.localhost."),
                "a.localhost. (trailing-dot FQDN) must be internal");
    }

    /** red→green：0.0.0.0. 数字尾点——当前前缀不命中判外部，修复后剥离尾点按 0.0.0.0/8 判内网。 */
    @Test
    public void testTrailingDotNumericInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("0.0.0.0."),
                "0.0.0.0. (trailing dot) must be internal after normalization");
    }

    /** red→green：无括号 IPv6 带端口——当前整串交给 getByName 解析为外部地址，修复后主动剥离端口按头部判定。 */
    @Test
    public void testUnbracketedIpv6WithPortInternal() {
        assertTrue(HostSecurityUtil.isInternalHost("::1:3306"),
                "::1:3306 must be internal (loopback ::1 + port)");
        assertTrue(HostSecurityUtil.isInternalHost("::ffff:127.0.0.1:3306"),
                "::ffff:127.0.0.1:3306 must be internal (IPv4-mapped 127.0.0.1 + port)");
    }

    /** keep-green：修复前已通过的回归守卫——127.0.0.1.（前缀已拦）/ fe80::1:3306（JDK 解析 link-local）修复后必须保持。 */
    @Test
    public void testKeepGreenRegressionGuards() {
        assertTrue(HostSecurityUtil.isInternalHost("127.0.0.1."),
                "127.0.0.1. kept internal (127. prefix already blocked)");
        assertTrue(HostSecurityUtil.isInternalHost("fe80::1:3306"),
                "fe80::1:3306 kept internal (JDK 26 parses as link-local)");
        assertTrue(HostSecurityUtil.isInternalHost("FE80::1:3306"),
                "FE80::1:3306 uppercase kept internal (same link-local judgment, no prefix special-casing)");
    }

    /** 反例：外网 host / 外网 IPv6 / 带括号形态（util 输入契约不带方括号，调用方已剥离）必须保持放行。 */
    @Test
    public void testReverseCasesStayExternal() {
        assertFalse(HostSecurityUtil.isInternalHost("example.com"));
        assertFalse(HostSecurityUtil.isInternalHost("example.com."),
                "example.com. (external FQDN) must stay external");
        assertFalse(HostSecurityUtil.isInternalHost("2001:db8::1"),
                "2001:db8::1 (documentation IPv6) must stay external");
        assertFalse(HostSecurityUtil.isInternalHost("[2001:db8::1]:3306"),
                "bracketed form is out of util contract (caller strips brackets) - must stay external");
    }

    // ===== F8（plan 2026-08-14-1133-1）：isInternalIpv6Literal charset 前置过滤（不触发 DNS）=====

    /**
     * <b>F8 adversarial</b>：含非 hex 字母（g-z）的含 {@code :} 串（{@code gzzz::1}）经 {@code isInternalHost}
     * 判定为外部（返回 false），且 charset 前置过滤使 {@link java.net.InetAddress#getByName} 不被调用（不触发 DNS）。
     *
     * <p>修复前任何含 {@code :} 的输入都路由到 {@code isInternalIpv6Literal} 直接调 {@code getByName}，
     * 含非 hex 字母的串会触发 DNS 查找，违反类 javadoc "纯确定性解析，不触发 DNS" 契约。
     */
    @Test
    public void testF8NonHexColonStringExternalNoDns() {
        assertFalse(HostSecurityUtil.isInternalHost("gzzz::1"),
                "non-hex chars (g,z) with ':' must be external (charset pre-filter, no DNS)");
        assertFalse(HostSecurityUtil.isInternalHost("hello:world"),
                "non-hex chars with ':' must be external (single colon < 2 also filtered)");
        assertFalse(HostSecurityUtil.isInternalHost("xyz:abc:def"),
                "non-hex chars with multiple ':' must be external without DNS lookup");
    }

    /**
     * F8：合法 IPv6 字面量判定不回归——charset 前置过滤仅排除非 hex 输入，合法 hex IPv6 字面量仍走
     * {@code getByName} 字面量解析（内网 loopback/link-local 仍判 true，外网仍判 false）。
     */
    @Test
    public void testF8LegitIpv6LiteralsNotRegressed() {
        assertTrue(HostSecurityUtil.isInternalHost("::1"),
                "::1 loopback still internal after F8 charset filter");
        assertTrue(HostSecurityUtil.isInternalHost("fe80::1"),
                "fe80::1 link-local still internal after F8 charset filter");
        assertTrue(HostSecurityUtil.isInternalHost("::ffff:127.0.0.1"),
                "::ffff:127.0.0.1 IPv4-mapped loopback still internal after F8 charset filter");
        assertFalse(HostSecurityUtil.isInternalHost("2001:db8::1"),
                "2001:db8::1 external documentation IPv6 still external after F8 charset filter");
    }

    /**
     * F8 charset 一致性：{@code isInternalIpv6Literal} 的 charset 前置过滤（[0-9a-fA-F:.] + ≥2 冒号）与
     * {@code isIpLiteral} 的 charset 检查逐项一致（代码审查验证）——两者均要求字符集 ⊆ hex+冒号+点
     * 且 ≥2 冒号才尝试 {@code getByName}。单冒号输入（如 {@code 1:2}，colons<2）也 return false 不触发 DNS。
     */
    @Test
    public void testF8SingleColonFilteredAsNonLiteral() {
        assertFalse(HostSecurityUtil.isInternalHost("1:2"),
                "single colon (colons<2) filtered as non-IPv6-literal, no DNS");
    }
}
