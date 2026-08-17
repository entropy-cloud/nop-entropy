package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * INV-LOCALE（Cycle 2 / P1-A，adjudication-table-cycle2 §2 #37）：custom_sql sandbox 关键字扫描的
 * tr-TR 默认 locale 回归 + 注入探测绕过对抗测试。
 *
 * <p>修复前 {@code validateCustomSqlSandbox} 用默认 locale 的 {@code sql.trim().toUpperCase()}——
 * tr-TR 下小写 {@code i} 映射为带点 {@code İ}（U+0130）：{@code "insert into ..."} upper 后为
 * {@code "İNSERT İNTO ..."}，token {@code İNSERT} ≠ 关键字 {@code INSERT} → 注入探测绕过（安全语义缺陷）。
 * 修复后使用 {@code Locale.ROOT}，locale 无关。
 */
public class TestMetaQualityRuleExecutorSandboxLocale {

    /**
     * P1-A #37 对抗（安全语义缺陷）：tr-TR 默认 locale 下，小写拼写的 {@code insert into}
     * 必须仍被 sandbox 关键字 blocklist 拦截。
     */
    @Test
    public void turkishLocaleLowercaseInsertStillBlocked() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThrows(NopException.class,
                    () -> MetaQualityRuleExecutor.validateCustomSqlSandbox(
                            "insert into t values(1)", "rule-key-1", "hash-1"),
                    "lowercase 'insert into' must be blocked under tr-TR default locale "
                            + "(old default-locale uppercase mapped i to İ and missed the INSERT keyword)");
            // 混合大小写变体（含小写 i 的 Insert）同样必须拦截
            assertThrows(NopException.class,
                    () -> MetaQualityRuleExecutor.validateCustomSqlSandbox(
                            "Insert into t values(1)", "rule-key-1", "hash-1"),
                    "mixed-case 'Insert into' must be blocked under tr-TR default locale");
            // delete 族同机理（关键字 DELETE 的 e 无影响，但 SQL 中小写 delete 的 i 不存在于关键字本身——
            // 用 update（含小写路径无 i）验证非 I 关键字不受 tr 影响）
            assertThrows(NopException.class,
                    () -> MetaQualityRuleExecutor.validateCustomSqlSandbox(
                            "update t set a=1", "rule-key-1", "hash-1"),
                    "lowercase 'update' must be blocked under tr-TR default locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 良性 SELECT 探测语句在 tr-TR 与默认 locale 下均放行（防过度拦截回归）。 */
    @Test
    public void benignSelectProbePassesUnderTurkishLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertDoesNotThrow(
                    () -> MetaQualityRuleExecutor.validateCustomSqlSandbox(
                            "select count(*) from t where deleted = 0", "rule-key-1", "hash-1"),
                    "benign select probe must pass under tr-TR default locale");
        } finally {
            Locale.setDefault(original);
        }
        assertDoesNotThrow(
                () -> MetaQualityRuleExecutor.validateCustomSqlSandbox(
                        "select count(*) from t", "rule-key-1", "hash-1"));
    }

    /** 默认 locale 下的既有 sandbox 基线（回归，不依赖 tr）。 */
    @Test
    public void defaultLocaleInsertStillBlocked() {
        assertThrows(NopException.class,
                () -> MetaQualityRuleExecutor.validateCustomSqlSandbox(
                        "insert into t values(1)", "rule-key-1", "hash-1"),
                "insert must be blocked regardless of locale");
    }
}
