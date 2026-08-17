package io.nop.metadata.service.connection;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INV-LOCALE（Cycle 2 / P1-A，adjudication-table-cycle2 §2 #2-#6）：JDBC URL 安全校验路径的
 * tr-TR 默认 locale 回归 + 大小写变体绕过对抗测试。
 *
 * <p>修复前 {@code MetaDataSourceConnectionProcessor} 的危险参数 blocklist 常量与 URL/主机归一化
 * 使用默认 locale 的 {@code toLowerCase()}——tr-TR 下大写 {@code I} 映射为无点 {@code ı}（U+0131）：
 * <ul>
 *   <li>blocklist 常量 {@code "allowLoadLocalInfile".toLowerCase()} → {@code "allowloadlocalınfıle"}，
 *       而攻击者用全小写 {@code allowloadlocalinfile} 拼写的 URL 参数经同一 toLowerCase 后仍为
 *       普通 {@code i}——contains 比对失配 → 危险参数绕过（安全语义缺陷）；</li>
 *   <li>allowed-hosts 集合归一化与 URL 侧 host 小写化同理失配 → 已放行的内网主机被误拒。</li>
 * </ul>
 * 修复后全部机器比较语义使用 {@code Locale.ROOT}，locale 无关。
 *
 * <p>locale 切换机制钉死为测试内 {@code Locale.setDefault} + {@code finally} 恢复
 * （先例 {@code TestLocalReconciliationProcessorLocale}，不使用 -Duser.language 全局注入）。
 */
public class TestMetaDataSourceConnectionProcessorLocale {

    private final MetaDataSourceConnectionProcessor processor = new MetaDataSourceConnectionProcessor();

    /**
     * P1-A #2/#4 对抗（安全语义缺陷）：tr-TR 默认 locale 下，全小写拼写的危险参数
     * {@code allowloadlocalinfile=true} 必须仍被 blocklist 拦截。
     *
     * <p>修复前：常量侧 {@code I→ı}、URL 侧普通 {@code i}，contains 失配 → 不抛异常（绕过）。
     */
    @Test
    public void turkishLocaleLowercaseDangerousParamStillBlocked() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThrows(NopException.class,
                    () -> processor.validateJdbcUrl("jdbc:mysql://public.example.com/db?allowloadlocalinfile=true"),
                    "all-lowercase allowLoadLocalInfile variant must be blocked under tr-TR default locale "
                            + "(old default-locale lowercase mapped constant I to dotless ı and missed the token)");
            // 混合大小写变体（与常量同形态）同样必须拦截
            assertThrows(NopException.class,
                    () -> processor.validateJdbcUrl("jdbc:mysql://public.example.com/db?AllowLoadLocalInfile=true"),
                    "mixed-case allowLoadLocalInfile variant must be blocked under tr-TR default locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * P1-A #3/#5 对抗：tr-TR 默认 locale 下，allowed-hosts 配置的小写内网主机必须与
     * URL 侧混合大小写 host（含大写 I）匹配（修复前 I→ı 失配 → 已放行主机被误拒）。
     */
    @Test
    public void turkishLocaleAllowedInternalHostsStillMatchMixedCaseUrlHost() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            processor.allowedInternalHostsCsv = "api.intra.localhost";
            // host "Api.Intra.Localhost" 含大写 I；.localhost 后缀 → HostSecurityUtil 判内网（ROOT 归一化，不受影响）
            // 修复前 host.toLowerCase() → "api.ıntra.localhost" ≠ allowed "api.intra.localhost" → 误拒
            assertDoesNotThrow(() -> processor.validateJdbcUrl("jdbc:mysql://Api.Intra.Localhost:3306/db"),
                    "configured allowed internal host must match mixed-case URL host under tr-TR default locale");
            // 未配置放行时内网 host 仍拒绝（fail-closed 基线不受修复影响）
            processor.allowedInternalHostsCsv = "";
            assertThrows(NopException.class,
                    () -> processor.validateJdbcUrl("jdbc:mysql://Api.Intra.Localhost:3306/db"),
                    "internal host without allowance must stay blocked under tr-TR default locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * P1-A #6 回归（latent 形态：IPv6 十六进制字母表无 I，tr 机理不可达，钉住修复不破坏路径）：
     * tr-TR 默认 locale 下 IPv4-mapped IPv6 内网字面量仍判内网并默认拒绝。
     */
    @Test
    public void turkishLocaleIpv4MappedHostNormalizationStillInternal() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            processor.allowedInternalHostsCsv = "";
            assertThrows(NopException.class,
                    () -> processor.validateJdbcUrl("jdbc:mysql://address=(host=[::FFFF:192.168.1.1])/db"),
                    "IPv4-mapped IPv6 RFC1918 literal must be recognized internal under tr-TR default locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 默认 locale 下的既有安全基线（回归，不依赖 tr）。 */
    @Test
    public void defaultLocaleDangerousParamStillBlocked() {
        assertThrows(NopException.class,
                () -> processor.validateJdbcUrl("jdbc:mysql://public.example.com/db?allowLoadLocalInfile=true"),
                "allowLoadLocalInfile must be blocked regardless of locale");
        assertDoesNotThrow(() -> processor.validateJdbcUrl("jdbc:mysql://public.example.com/db"),
                "clean public URL must pass");
    }
}
