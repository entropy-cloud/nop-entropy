package io.nop.metadata.service.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 共享主机安全判定工具：内网/保留段主机判定，供 JDBC 建连校验
 * （{@link io.nop.metadata.service.connection.MetaDataSourceConnectionProcessor}）与 webhook 主机校验
 * （{@link io.nop.metadata.service.quality.CheckpointActionDispatcher}）统一使用，消除重复实现与语义漂移。
 *
 * <p>判定语义与 JDK 实际解析严格一致（jshell 实测 JDK 21/26）：
 * <ul>
 *   <li>IPv4 点分形式按 1-4 段严格十进制解析（前导零按十进制，废弃 inet_aton 八进制语义）；
 *       前 n-1 段每段须 ≤255，末段须 ≤ 2^(8*(5-n))-1（n=1: 2^32-1、n=2: 2^24-1、n=3: 2^16-1、n=4: 255）。</li>
 *   <li>十进制整数形式按 mod 2^32 取值（JDK 与 OS inet_aton 回退均如此截断，
 *       如 2130706433→127.0.0.1、4294967297→0.0.0.1、18446744073709551616→0.0.0.0）。</li>
 *   <li>点分段按 mod 2^32 截断后做界检查（与 OS inet_aton 回退一致，
 *       如 0.1.2.18446744073709551618→0.1.2.2 属 0.0.0.0/8 内网）。</li>
 *   <li>0x 十六进制整数形式（0x7f000001 等）一律判内网（fail-closed 超集——JDK 视为歧义/非法字面量，
 *       但 macOS/Linux getaddrinfo 的 inet_aton 回退可解析十六进制，保守拦截成本为零）。</li>
 *   <li>非 IP 字面量（hostname，如 127.abc / 10.0.0.1.nip.io）走字符串前缀比对
 *       （localhost/*.localhost/127./10./192.168./172.16-31/169.254.，fail-closed 维持拦截）。</li>
 *   <li>IPv4-mapped IPv6（::ffff:a.b.c.d）提取 IPv4 段复用 IPv4 判定；IPv6 字面量
 *       （::1、fe80::/10、0:0:0:0:0:0:0:1）经字面量解析判定（不查 DNS）。</li>
 * </ul>
 *
 * <p>纯确定性解析，不触发 DNS。输入契约：接收不带方括号的 host（调用方已在调用前剥离），
 * 本工具统一 {@code trim()} 后再判定。
 */
public final class HostSecurityUtil {

    private HostSecurityUtil() {
    }

    /**
     * 是否内网/保留段主机（RFC1918 + RFC3927 link-local + loopback + 0.0.0.0/8）。
     *
     * @param host 不带方括号的 host（可为 null/空白；内部统一 trim）
     */
    public static boolean isInternalHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.isEmpty()) {
            return false;
        }
        // IPv4-mapped IPv6：::ffff:a.b.c.d → 取 IPv4 段复用 IPv4 判定
        int ffffIdx = h.lastIndexOf("::ffff:");
        if (ffffIdx >= 0) {
            long[] v4 = parseNumericLiteral(h.substring(ffffIdx + 7));
            if (v4 != null) {
                return isInternalV4(v4);
            }
            // ::ffff: 后非点分形式 → 落入下方 IPv6 字面量判定
        }
        // 十进制整数 / 1-4 段点分字面量（严格十进制 + mod 2^32 位移，与 JDK/OS inet_aton 一致）。
        // 合法字面量直接返回判定结果（外部即放行，不再落 hostname 前缀路径——172.16→172.0.0.16 非 RFC1918）；
        // 非法数字字面量（如 1.2.3.4.5 / 256.1.1.1）→ 落入 hostname 路径
        if (looksLikeNumericLiteral(h)) {
            long[] v4 = parseNumericLiteral(h);
            if (v4 != null) {
                return isInternalV4(v4);
            }
        }
        // 0x 十六进制整数形式：fail-closed 超集，一律拦截（理由见类注释）
        if (isHexInteger(h)) {
            return true;
        }
        // IPv6 字面量（仅含 ':' 的输入才进入；字面量解析不查 DNS）
        if (h.indexOf(':') >= 0) {
            return isInternalIpv6Literal(h);
        }
        // hostname fast path（维持既有字符串比对行为，不触发 DNS）
        return isInternalHostname(h);
    }

    /** 数字字面量候选：仅含 ASCII 数字与点（1-4 段），且不以点开头/结尾。 */
    private static boolean looksLikeNumericLiteral(String h) {
        if (h.isEmpty() || h.charAt(0) == '.' || h.charAt(h.length() - 1) == '.') {
            return false;
        }
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c != '.' && (c < '0' || c > '9')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析 1-4 段点分/单段十进制整数 IPv4。每段按十进制累加（long 自然回绕 = mod 2^64，
     * 取低 32 位 = mod 2^32，与 JDK/OS 截断语义一致）；前 n-1 段须 ≤255、末段须 ≤ 2^(8*(5-n))-1；
     * 前 n-1 段直接作为前导字节，末段填入右侧剩余 (5-n) 字节（与 JDK textToNumericFormatV4 布局一致，
     * 如 172.16 → 172.0.0.16）。
     *
     * @return 4 octets（long 数组）；非数字字面量返回 {@code null}
     */
    private static long[] parseNumericLiteral(String h) {
        String[] parts = h.split("\\.");
        if (parts.length == 0 || parts.length > 4) {
            return null;
        }
        long[] octets = new long[4];
        for (int i = 0; i < parts.length - 1; i++) {
            long part = parseSegment(parts[i]);
            if (part < 0 || part > 0xFFL) {
                return null;
            }
            octets[i] = part;
        }
        long last = parseSegment(parts[parts.length - 1]);
        if (last < 0) {
            return null;
        }
        long limit;
        switch (parts.length) {
            case 1: limit = 0xFFFFFFFFL; break;
            case 2: limit = 0xFFFFFFL; break;
            case 3: limit = 0xFFFFL; break;
            default: limit = 0xFFL; break;
        }
        if (last > limit) {
            return null;
        }
        // 末段填入右侧 (5-n) 字节（n=1: 4 字节；n=2: 3 字节；n=3: 2 字节；n=4: 1 字节）
        for (int k = 0; k < 5 - parts.length; k++) {
            octets[parts.length - 1 + k] = (last >> (8 * (4 - parts.length - k))) & 0xFF;
        }
        return octets;
    }

    /** 解析单个十进制段：空段/非数字返回 -1；否则返回段值 mod 2^32。 */
    private static long parseSegment(String s) {
        if (s.isEmpty()) {
            return -1;
        }
        long v = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            v = v * 10 + (c - '0');
        }
        return v & 0xFFFFFFFFL;
    }

    /** 0x/0X 十六进制整数形式（0x + 1+ 十六进制数字）。 */
    private static boolean isHexInteger(String h) {
        if (h.length() <= 2 || !(h.startsWith("0x") || h.startsWith("0X"))) {
            return false;
        }
        for (int i = 2; i < h.length(); i++) {
            char c = h.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /** RFC1918 + link-local + loopback + 0.0.0.0/8（0.0.0.0 多数平台 connect 等效 localhost）。 */
    private static boolean isInternalV4(long[] o) {
        if (o[0] == 0) {
            return true;              // 0.0.0.0/8（含 0.0.0.0）
        }
        if (o[0] == 127) {
            return true;              // loopback
        }
        if (o[0] == 10) {
            return true;              // RFC1918
        }
        if (o[0] == 192 && o[1] == 168) {
            return true;
        }
        if (o[0] == 172 && o[1] >= 16 && o[1] <= 31) {
            return true;
        }
        return o[0] == 169 && o[1] == 254;  // link-local
    }

    /** hostname fast path：localhost / *.localhost 及内网段前缀字符串比对（fail-closed，不触发 DNS）。 */
    private static boolean isInternalHostname(String h) {
        if ("localhost".equals(h) || h.endsWith(".localhost")) {
            return true;
        }
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")) {
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
        return h.startsWith("169.254.");
    }

    /** IPv6 字面量内网判定：loopback（::1）、link-local（fe80::/10）、IPv4-mapped（::ffff:a.b.c.d）。 */
    private static boolean isInternalIpv6Literal(String h) {
        InetAddress addr;
        try {
            addr = InetAddress.getByName(h);
        } catch (UnknownHostException e) {
            // 非合法字面量：无法确定为内网，视为外部（不破坏合法外网主机）
            return false;
        }
        byte[] b = addr.getAddress();
        if (b == null || b.length != 16) {
            return false;
        }
        // IPv4-mapped ::ffff:a.b.c.d
        if (isAllZero(b, 0, 10) && b[10] == (byte) 0xFF && b[11] == (byte) 0xFF) {
            long[] o = {b[12] & 0xFF, b[13] & 0xFF, b[14] & 0xFF, b[15] & 0xFF};
            return isInternalV4(o);
        }
        // loopback ::1
        if (isAllZero(b, 0, 15) && b[15] == 1) {
            return true;
        }
        // link-local fe80::/10
        return (b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80;
    }

    private static boolean isAllZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
