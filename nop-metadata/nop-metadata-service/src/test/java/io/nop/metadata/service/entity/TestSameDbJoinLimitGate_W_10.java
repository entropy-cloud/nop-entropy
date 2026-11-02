
package io.nop.metadata.service.entity;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.NopMetadataErrors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * W-10 回归钉（plan 2528 红⑧，CRA 5.5 C5-101）：同库 JOIN 正值 limit 硬闸 + 注释契约修正
 * （{@code TestLimitNegativeValueInvariant} 同构正值分支）。
 *
 * <p>缺陷形态（pre-fix）：{@code normalizeJoinQueryLimit} 正值原样透传无封顶（对照
 * {@code normalizeQueryLimit} Math.min 封顶）——公开 {@code @BizQuery queryJoinData} 可传
 * {@code limit=2147483647} → 大表 1:N JOIN 整包物化进程级内存峰值；javadoc 自称「超大值由
 * 截断层显式拒绝」但截断层仅存在于跨库 merge 路径（同库路径无任何行数闸——契约漂移）。
 *
 * <p>修复后（裁定⑧第三案）：正值超有效上限显式抛 {@code ERR_PAGINATION_LIMIT_TOO_LARGE}
 * （param ARG_LIMIT——{@code AggregationHelper.truncateCrossDb} 同码先例）；有效上限 =
 * {@code configuredMaxQueryLimit}（{@code nop.metadata.query.max-limit}，>0 时）否则
 * {@code DEFAULT_MAX_QUERY_LIMIT=10000}——与 normalizeQueryLimit 同一上限来源；单点归一覆盖
 * queryJoinData 与 queryAggregation 两个公开入口；「不静默截断」API 语义维持（闸=显式拒绝）；
 * 负值拒绝与 null/0 缺省形态零改动。
 */
public class TestSameDbJoinLimitGate_W_10 {

    private static Long normalize(NopMetaEntityBizModel bizModel, Long limit) throws Throwable {
        try {
            Method m = NopMetaEntityBizModel.class.getDeclaredMethod("normalizeJoinQueryLimit", Long.class);
            m.setAccessible(true);
            return (Long) m.invoke(bizModel, limit);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** 红⑧ 主形态：超大正值显式拒绝（pre-fix 原样透传不抛 → red）。 */
    @Test
    public void testOversizedPositiveJoinLimitRejected() {
        NopMetaEntityBizModel bizModel = new NopMetaEntityBizModel();
        NopException ex = assertThrows(NopException.class,
                () -> normalize(bizModel, 2147483647L),
                "join/analysis limit beyond the effective cap must be explicitly rejected"
                        + " (ERR_PAGINATION_LIMIT_TOO_LARGE, no silent pass-through)");
        assertEquals("nop.err.metadata.pagination-limit-too-large", ex.getErrorCode(),
                "same error code as the cross-db merge truncate gate (AggregationHelper.truncateCrossDb)");
        assertEquals(2147483647L, ex.getParam(NopMetadataErrors.ARG_LIMIT),
                "the offending limit must be carried under ARG_LIMIT");
    }

    /** 边界对照：limit == 有效上限（缺省 10000）不误伤（显式拒绝只针对超界）。 */
    @Test
    public void testLimitAtDefaultCapPasses() throws Throwable {
        NopMetaEntityBizModel bizModel = new NopMetaEntityBizModel();
        assertEquals(10000L, normalize(bizModel, 10000L),
                "limit == default cap must pass through unchanged (boundary not over-triggered)");
        assertEquals(500L, normalize(bizModel, 500L), "normal positive limit passes through");
    }

    /** 配置上限生效：nop.metadata.query.max-limit>0 时同一来源收口（与 normalizeQueryLimit 同源）。 */
    @Test
    public void testConfiguredMaxQueryLimitIsTheCapSource() throws Throwable {
        NopMetaEntityBizModel bizModel = new NopMetaEntityBizModel();
        bizModel.configuredMaxQueryLimit = 100;
        assertEquals(100L, normalize(bizModel, 100L), "limit == configured cap passes");
        NopException ex = assertThrows(NopException.class,
                () -> normalize(bizModel, 101L),
                "limit beyond the configured cap must be rejected with the same gate");
        assertEquals("nop.err.metadata.pagination-limit-too-large", ex.getErrorCode());
    }

    /** null/0 缺省与负值拒绝形态零改动（负值钉 TestLimitNegativeValueInvariant 同面复核）。 */
    @Test
    public void testNullZeroAndNegativeFormsUnchanged() throws Throwable {
        NopMetaEntityBizModel bizModel = new NopMetaEntityBizModel();
        assertEquals(1000L, normalize(bizModel, null), "null -> DEFAULT_QUERY_LIMIT (unchanged)");
        assertEquals(1000L, normalize(bizModel, 0L), "0 -> DEFAULT_QUERY_LIMIT (unchanged)");
        NopException ex = assertThrows(NopException.class, () -> normalize(bizModel, -1L),
                "negative limit must keep its explicit reject (negative pin zero regression)");
        assertEquals("nop.err.metadata.pagination-limit-invalid", ex.getErrorCode());
    }
}
