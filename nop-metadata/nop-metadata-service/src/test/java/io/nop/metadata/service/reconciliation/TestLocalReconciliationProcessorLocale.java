package io.nop.metadata.service.reconciliation;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AR-12：{@link LocalReconciliationProcessor#levenshteinSimilarity} locale-insensitive 验证。
 *
 * <p>修复前使用默认 locale 的 {@code toLowerCase()}——tr-TR 下 {@code "I".toLowerCase()} → {@code "ı"}
 * （无点 i），同一数据在不同 JVM locale 下匹配结果不同。修复后使用 {@code Locale.ROOT}，
 * 与精确路径（{@code equalsIgnoreCase} 天然 locale-insensitive）一致。
 *
 * <p>纯静态方法单元测试（package-private access），不依赖 ORM session / IoC。
 */
public class TestLocalReconciliationProcessorLocale {

    /**
     * 在 Turkish locale（tr-TR）下 "I" 与 "i" 的相似度应为 1.0（locale-insensitive）。
     *
     * <p>tr-TR locale 下默认 {@code "I".toLowerCase()} → {@code "ı"}（U+0131）而非 {@code "i"}，
     * 导致旧实现下 levenshteinSimilarity("I","i") 返回 0.0（两个完全不同的字符）。
     * {@code Locale.ROOT} 下 {@code "I".toLowerCase()} → {@code "i"}，相似度 1.0。
     */
    @Test
    public void turkishLocaleNotAffectingSimilarity() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            // "I" vs "i" — under ROOT: toLowerCase("I")="i", identical → 1.0
            // under tr-TR default: toLowerCase("I")="ı" ≠ "i" → would be 0.0 (bug)
            double sim = LocalReconciliationProcessor.levenshteinSimilarity("I", "i");
            assertEquals(1.0, sim, 0.0001,
                    "I/i must be locale-insensitive identical (similarity 1.0), not affected by tr-TR locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    /**
     * 多字符混合大小写的相似度在 Turkish locale 下不变。
     * "MICROSOFT" vs "microsoft" → 完全相同（大小写差异）→ similarity 1.0。
     */
    @Test
    public void mixedCaseSimilarityLocaleInsensitive() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            double sim = LocalReconciliationProcessor.levenshteinSimilarity("MICROSOFT", "microsoft");
            assertEquals(1.0, sim, 0.0001,
                    "MICROSOFT/microsoft must be identical under any locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 正常相似度计算在默认 locale 下不变（回归基线）。 */
    @Test
    public void normalSimilarityUnchangedDefaultLocale() {
        // "Microsoftx" vs "Microsoft" → distance 1 / maxLen 10 = 0.9
        double sim = LocalReconciliationProcessor.levenshteinSimilarity("Microsoftx", "Microsoft");
        assertEquals(0.9, sim, 0.0001);

        // identical
        assertEquals(1.0, LocalReconciliationProcessor.levenshteinSimilarity("abc", "abc"), 0.0001);

        // completely different
        assertEquals(0.0, LocalReconciliationProcessor.levenshteinSimilarity("abc", "xyz"), 0.0001);
    }
}
