package io.nop.metadata.service.profiling;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INV-LOCALE（Cycle 2 / P1-B，adjudication-table-cycle2 §2 #26）：AR-06 infra 分类器消息线索匹配的
 * tr-TR 默认 locale 回归。
 *
 * <p>修复前 {@code isInfrastructureFailure} 把异常消息用默认 locale {@code toLowerCase()} 后做线索
 * contains——tr-TR 下大写 {@code I} 映射为无点 {@code ı}：全大写驱动消息（如
 * {@code "CONNECTION REFUSED"}）小写化为 {@code "connectıon refused"}，线索 {@code connection}
 * 失配 → 基础设施失败被误分类为良性类型不匹配（AR-06 分类漂移：WARN 信号丢失）。修复后
 * {@code Locale.ROOT}，locale 无关。
 */
public class TestMetaTableProfilerLocale {

    private static SQLException sqlException(String sqlState, String message) {
        return new SQLException(message, sqlState);
    }

    /** tr-TR 默认 locale 下，全大写消息中的连接/权限线索仍必须判 infra（WARN 路径不丢）。 */
    @Test
    public void turkishLocaleUppercaseMessageCluesStillInfra() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException(null, "CONNECTION REFUSED")),
                    "uppercase 'CONNECTION' clue must still classify infra under tr-TR "
                            + "(old default-locale lowercase mapped I to ı and missed the 'connection' clue)");
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException(null, "PERMISSION DENIED FOR TABLE")),
                    "uppercase 'PERMISSION' clue must still classify infra under tr-TR");
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException(null, "COMMUNICATION LINK FAILURE")),
                    "uppercase 'COMMUNICATION' clue must still classify infra under tr-TR");
            // 混合大小写（含小写 i 侧不受影响、含大写 I 侧修复前失配）同样必须命中
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException(null, "Connection Indicated Failure")),
                    "mixed-case 'Connection' clue must still classify infra under tr-TR");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** 良性类型不匹配消息在 tr-TR 下仍不误报 infra（防过度拦截回归）。 */
    @Test
    public void turkishLocaleBenignMismatchStillNotInfra() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlException(null, "Wrong data type")),
                    "benign type mismatch must stay non-infra under tr-TR");
            assertFalse(MetaTableProfiler.isInfrastructureFailure(sqlException("22001", "value too long")),
                    "22* data exception must stay non-infra under tr-TR");
        } finally {
            Locale.setDefault(original);
        }
    }

    /** SQLState 精确码路径（08x/28x/42x 前缀）在 tr-TR 下不受消息形态影响（回归）。 */
    @Test
    public void turkishLocaleSqlStateCodesStillInfra() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException("08006", null)));
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException("28P01", null)));
            assertTrue(MetaTableProfiler.isInfrastructureFailure(sqlException("42501", null)));
        } finally {
            Locale.setDefault(original);
        }
    }
}
