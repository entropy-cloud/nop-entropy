package io.nop.metadata.service.contract;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INV-LOCALE（Cycle 2 / P1-B，adjudication-table-cycle2 §2 #7）：SLA 时间单位 token 归一化的
 * tr-TR 默认 locale 回归。
 *
 * <p>修复前 {@code toDurationMillis} 把单位 token 用默认 locale {@code toLowerCase()} 后进 switch——
 * tr-TR 下大写 {@code I} 映射无点 {@code ı}：{@code "MINUTE"} → {@code "mınute"} 不匹配任何 case
 * → 误抛 ERR_CONTRACT_SLA_INVALID（未知单位）。修复后 {@code Locale.ROOT}，locale 无关。
 */
public class TestMetaContractCheckerSlaUnitLocale {

    private final MetaContractChecker checker = new MetaContractChecker(null);

    private static Map<String, Object> duration(Object amount, String unit) {
        Map<String, Object> m = new HashMap<>();
        m.put("interval", amount);
        m.put("value", amount);
        m.put("unit", unit);
        return m;
    }

    /** tr-TR 默认 locale 下混合/大写单位 token（含大写 I）仍必须正确解析。 */
    @Test
    public void turkishLocaleUnitTokenStillParsed() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            // "MINUTE" 含大写 I：旧实现 → "mınute" → switch 落 default 抛异常
            assertEquals(Long.valueOf(60_000L),
                    checker.toDurationMillis("cid", duration(1, "MINUTE"), "interval", "unit"),
                    "'MINUTE' must parse to 60000ms under tr-TR (old default-locale lowercase made 'mınute' unknown)");
            assertEquals(Long.valueOf(3_600_000L),
                    checker.toDurationMillis("cid", duration(1, "HOUR"), "value", "unit"),
                    "'HOUR' must parse under tr-TR");
            assertEquals(Long.valueOf(86_400_000L),
                    checker.toDurationMillis("cid", duration(1, "DAY"), "value", "unit"));
            // 混合大小写 Minute 同样必须解析
            assertEquals(Long.valueOf(60_000L),
                    checker.toDurationMillis("cid", duration(1, "Minute"), "interval", "unit"),
                    "'Minute' must parse under tr-TR");
            // 已全小写单位不受影响（回归）
            assertEquals(Long.valueOf(1_000L),
                    checker.toDurationMillis("cid", duration(1, "sec"), "value", "unit"));
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 默认 locale 基线（回归）。 */
    @Test
    public void defaultLocaleUnitTokensBaseline() {
        assertEquals(Long.valueOf(60_000L),
                checker.toDurationMillis("cid", duration(1, "MINUTE"), "interval", "unit"));
        assertEquals(Long.valueOf(500L),
                checker.toDurationMillis("cid", duration(0.5, "sec"), "value", "unit"));
    }
}
