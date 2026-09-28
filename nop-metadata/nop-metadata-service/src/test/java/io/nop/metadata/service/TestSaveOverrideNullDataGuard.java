package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizErrors;
import io.nop.metadata.service.entity.NopMetaBusinessDomainBizModel;
import io.nop.metadata.service.entity.NopMetaEntityFieldBizModel;
import io.nop.metadata.service.entity.NopMetaModuleBizModel;
import io.nop.metadata.service.entity.NopMetaEntityBizModel;
import io.nop.metadata.service.entity.NopMetaEntityDimensionBizModel;
import io.nop.metadata.service.entity.NopMetaEntityFilterBizModel;
import io.nop.metadata.service.entity.NopMetaEntityJoinBizModel;
import io.nop.metadata.service.entity.NopMetaEntityMeasureBizModel;
import io.nop.metadata.service.entity.NopMetaTagBizModel;
import io.nop.metadata.service.entity.NopMetaTagLabelBizModel;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P2-19（plan 2026-08-16-0226-3 Phase 2）：save override null/empty data 防护——统一提前委托形态。
 *
 * <p>修复前 6 处 override 在 super.save 前解引用 data（stringOf/containsKey/validateJoin），
 * null data 抢先 NPE（而非基类 {@code ERR_BIZ_EMPTY_DATA_FOR_SAVE}）。修复后全部 override
 * （含 Module/Table/Tag 三个既有防护点，形态一并统一）对 null 与 empty map 都直落基类错误。
 *
 * <p>直调可行性：提前委托分支在触达任何 dao/注入依赖之前抛出（doSave 首行即 isEmptyMap 检查，
 * {@code getBizObjName()} 只读 @BizModel 注解），无需容器。
 */
public class TestSaveOverrideNullDataGuard {

    private interface SaveInvoker {
        void invoke(Map<String, Object> data);
    }

    private static void assertEmptyDataForSave(SaveInvoker invoker, Map<String, Object> data, String label) {
        NopException ex = assertThrows(NopException.class, () -> invoker.invoke(data), label);
        assertEquals(BizErrors.ERR_BIZ_EMPTY_DATA_FOR_SAVE.getErrorCode(), ex.getErrorCode(),
                label + " must fail with base ERR_BIZ_EMPTY_DATA_FOR_SAVE, got: " + ex.getErrorCode());
    }

    /** 6 个修复目标 + 3 个既有防护点（Module/Table/Tag），null data 统一基类错误。 */
    @Test
    public void testNullDataFallsToBaseError() {
        assertEmptyDataForSave(d -> new NopMetaEntityDimensionBizModel().save(d, null), null,
                "NopMetaEntityDimension.save(null)");
        assertEmptyDataForSave(d -> new NopMetaEntityFieldBizModel().save(d, null), null,
                "NopMetaEntityField.save(null)");
        assertEmptyDataForSave(d -> new NopMetaEntityJoinBizModel().save(d, null), null,
                "NopMetaEntityJoin.save(null)");
        assertEmptyDataForSave(d -> new NopMetaEntityMeasureBizModel().save(d, null), null,
                "NopMetaEntityMeasure.save(null)");
        assertEmptyDataForSave(d -> new NopMetaEntityFilterBizModel().save(d, null), null,
                "NopMetaEntityFilter.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTagLabelBizModel().save(d, null), null,
                "NopMetaTagLabel.save(null)");
        // P2-28 新增 override（plan 2026-08-16-0920-1）：守卫在 super.save 之后运行，
        // null/empty data 直落基类错误（同单一形态）
        assertEmptyDataForSave(d -> new NopMetaBusinessDomainBizModel().save(d, null), null,
                "NopMetaBusinessDomain.save(null)");
        // 既有防护 override 对照断言：行为不变（null 同样落基类错误，且不 NPE）
        assertEmptyDataForSave(d -> new NopMetaModuleBizModel().save(d, null), null,
                "NopMetaModule.save(null)");
        assertEmptyDataForSave(d -> new NopMetaEntityBizModel().save(d, null), null,
                "NopMetaEntity.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTagBizModel().save(d, null), null,
                "NopMetaTag.save(null)");
    }

    /** empty map 同样统一基类错误（三元/!=null 旧形态只防 null 不防 empty，本批统一）。 */
    @Test
    public void testEmptyMapFallsToBaseError() {
        assertEmptyDataForSave(d -> new NopMetaEntityDimensionBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntityDimension.save({})");
        assertEmptyDataForSave(d -> new NopMetaEntityFieldBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntityField.save({})");
        assertEmptyDataForSave(d -> new NopMetaEntityJoinBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntityJoin.save({})");
        assertEmptyDataForSave(d -> new NopMetaEntityMeasureBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntityMeasure.save({})");
        assertEmptyDataForSave(d -> new NopMetaEntityFilterBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntityFilter.save({})");
        assertEmptyDataForSave(d -> new NopMetaTagLabelBizModel().save(d, null), new HashMap<>(),
                "NopMetaTagLabel.save({})");
        assertEmptyDataForSave(d -> new NopMetaBusinessDomainBizModel().save(d, null), new HashMap<>(),
                "NopMetaBusinessDomain.save({})");
        assertEmptyDataForSave(d -> new NopMetaModuleBizModel().save(d, null), new HashMap<>(),
                "NopMetaModule.save({})");
        assertEmptyDataForSave(d -> new NopMetaEntityBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntity.save({})");
        assertEmptyDataForSave(d -> new NopMetaTagBizModel().save(d, null), new HashMap<>(),
                "NopMetaTag.save({})");
    }
}
