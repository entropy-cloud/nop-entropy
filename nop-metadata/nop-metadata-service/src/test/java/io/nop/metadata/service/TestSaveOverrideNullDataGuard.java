package io.nop.metadata.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizErrors;
import io.nop.metadata.service.entity.NopMetaEntityFieldBizModel;
import io.nop.metadata.service.entity.NopMetaModuleBizModel;
import io.nop.metadata.service.entity.NopMetaTableBizModel;
import io.nop.metadata.service.entity.NopMetaTableDimensionBizModel;
import io.nop.metadata.service.entity.NopMetaTableFilterBizModel;
import io.nop.metadata.service.entity.NopMetaTableJoinBizModel;
import io.nop.metadata.service.entity.NopMetaTableMeasureBizModel;
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
        assertEmptyDataForSave(d -> new NopMetaTableDimensionBizModel().save(d, null), null,
                "NopMetaTableDimension.save(null)");
        assertEmptyDataForSave(d -> new NopMetaEntityFieldBizModel().save(d, null), null,
                "NopMetaEntityField.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTableJoinBizModel().save(d, null), null,
                "NopMetaTableJoin.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTableMeasureBizModel().save(d, null), null,
                "NopMetaTableMeasure.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTableFilterBizModel().save(d, null), null,
                "NopMetaTableFilter.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTagLabelBizModel().save(d, null), null,
                "NopMetaTagLabel.save(null)");
        // 既有防护 override 对照断言：行为不变（null 同样落基类错误，且不 NPE）
        assertEmptyDataForSave(d -> new NopMetaModuleBizModel().save(d, null), null,
                "NopMetaModule.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTableBizModel().save(d, null), null,
                "NopMetaTable.save(null)");
        assertEmptyDataForSave(d -> new NopMetaTagBizModel().save(d, null), null,
                "NopMetaTag.save(null)");
    }

    /** empty map 同样统一基类错误（三元/!=null 旧形态只防 null 不防 empty，本批统一）。 */
    @Test
    public void testEmptyMapFallsToBaseError() {
        assertEmptyDataForSave(d -> new NopMetaTableDimensionBizModel().save(d, null), new HashMap<>(),
                "NopMetaTableDimension.save({})");
        assertEmptyDataForSave(d -> new NopMetaEntityFieldBizModel().save(d, null), new HashMap<>(),
                "NopMetaEntityField.save({})");
        assertEmptyDataForSave(d -> new NopMetaTableJoinBizModel().save(d, null), new HashMap<>(),
                "NopMetaTableJoin.save({})");
        assertEmptyDataForSave(d -> new NopMetaTableMeasureBizModel().save(d, null), new HashMap<>(),
                "NopMetaTableMeasure.save({})");
        assertEmptyDataForSave(d -> new NopMetaTableFilterBizModel().save(d, null), new HashMap<>(),
                "NopMetaTableFilter.save({})");
        assertEmptyDataForSave(d -> new NopMetaTagLabelBizModel().save(d, null), new HashMap<>(),
                "NopMetaTagLabel.save({})");
        assertEmptyDataForSave(d -> new NopMetaModuleBizModel().save(d, null), new HashMap<>(),
                "NopMetaModule.save({})");
        assertEmptyDataForSave(d -> new NopMetaTableBizModel().save(d, null), new HashMap<>(),
                "NopMetaTable.save({})");
        assertEmptyDataForSave(d -> new NopMetaTagBizModel().save(d, null), new HashMap<>(),
                "NopMetaTag.save({})");
    }
}
