/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaReconciliationResultBiz;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对账结果 BizModel：基线 CRUD（{@link CrudBizModel}）+ 人工确认入口
 * （设计 08-reconciliation.md §3.4 行为契约，plan 0900-2 Phase 2）。
 *
 * <p>人工确认（{@code @BizMutation}）：
 * <ul>
 *   <li>{@code confirmMatch(resultId, rowIndex, selectedEntityId)}：更新 {@code details[rowIndex]} 的
 *       {@code status=MANUAL} + {@code selectedId}。</li>
 *   <li>{@code batchConfirmMatches(resultId, selections)}：批量执行上述更新。</li>
 *   <li>越界 {@code rowIndex} / result 不存在 → 显式失败（不静默忽略）。</li>
 * </ul>
 *
 * <p>{@code rowIndex} 为 {@code details} JSON 数组下标，首版语义绑定本次执行快照
 * （重排/分页漂移为 follow-up，可后续引入 stable rowKey）。
 *
 * <p>ErrorCode 按模块惯例内联于本类顶部。
 */
@BizModel("NopMetaReconciliationResult")
public class NopMetaReconciliationResultBizModel extends CrudBizModel<NopMetaReconciliationResult>
        implements INopMetaReconciliationResultBiz {


    /** details 中 status 字段名（人工确认写入 MANUAL）。 */
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_SELECTED_ID = "selectedId";
    /** 输入选择项中候选实体 ID 的键名（与 confirmMatch 入参 selectedEntityId 一致）。 */
    private static final String FIELD_SELECTED_ENTITY_ID = "selectedEntityId";
    private static final String FIELD_ROW_INDEX = "rowIndex";
    /** status 常量（人工确认写入）。 */
    private static final String STATUS_MANUAL = "MANUAL";

    public NopMetaReconciliationResultBizModel() {
        setEntityName(NopMetaReconciliationResult.class.getName());
    }

    /**
     * 人工确认单条匹配（设计 §3.4）：更新 {@code details[rowIndex].status=MANUAL} + {@code selectedId}。
     *
     * @param resultId         对账结果 ID
     * @param rowIndex         details JSON 数组下标
     * @param selectedEntityId 选中的候选实体 ID
     * @param context          服务上下文
     * @return 更新后的 {@link NopMetaReconciliationResult}
     */
    @BizMutation
    public NopMetaReconciliationResult confirmMatch(@Name("resultId") String resultId,
                                                     @Name("rowIndex") int rowIndex,
                                                     @Name("selectedEntityId") String selectedEntityId,
                                                     IServiceContext context) {
        NopMetaReconciliationResult result = loadResultOrThrow(resultId);
        List<Object> details = parseDetailsOrThrow(result);
        checkRowIndex(resultId, rowIndex, details.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) details.get(rowIndex);
        Map<String, Object> updated = new LinkedHashMap<>(row);
        updated.put(FIELD_STATUS, STATUS_MANUAL);
        updated.put(FIELD_SELECTED_ID, selectedEntityId);
        details.set(rowIndex, updated);

        result.setDetails(JsonTool.stringify(details));
        dao().updateEntity(result);
        return result;
    }

    /**
     * 人工确认批量匹配（设计 §3.4）。
     *
     * @param resultId   对账结果 ID
     * @param selections 选择列表，每项 {@code {rowIndex, selectedEntityId}}
     * @param context    服务上下文
     * @return 更新后的 {@link NopMetaReconciliationResult}
     */
    @BizMutation
    public NopMetaReconciliationResult batchConfirmMatches(@Name("resultId") String resultId,
                                                            @Name("selections") List<Map<String, Object>> selections,
                                                            IServiceContext context) {
        if (selections == null || selections.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_RECON_SELECTIONS_EMPTY).param("resultId", resultId);
        }
        NopMetaReconciliationResult result = loadResultOrThrow(resultId);
        List<Object> details = parseDetailsOrThrow(result);
        int size = details.size();

        for (Map<String, Object> sel : selections) {
            int rowIndex = toInt(sel.get(FIELD_ROW_INDEX));
            // 输入选择项键为 selectedEntityId（与 confirmMatch 入参一致），写入 detail 键为 selectedId
            String selectedEntityId = toStr(sel.getOrDefault(FIELD_SELECTED_ENTITY_ID, sel.get(FIELD_SELECTED_ID)));
            checkRowIndex(resultId, rowIndex, size);

            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) details.get(rowIndex);
            Map<String, Object> updated = new LinkedHashMap<>(row);
            updated.put(FIELD_STATUS, STATUS_MANUAL);
            updated.put(FIELD_SELECTED_ID, selectedEntityId);
            details.set(rowIndex, updated);
        }

        result.setDetails(JsonTool.stringify(details));
        dao().updateEntity(result);
        return result;
    }

    // ============================================================
    // helpers
    // ============================================================

    private NopMetaReconciliationResult loadResultOrThrow(String resultId) {
        IEntityDao<NopMetaReconciliationResult> resultDao = dao();
        NopMetaReconciliationResult result = resultDao.getEntityById(resultId);
        if (result == null) {
            throw new NopException(NopMetadataErrors.ERR_RECON_RESULT_NOT_FOUND).param("resultId", resultId);
        }
        return result;
    }

    private List<Object> parseDetailsOrThrow(NopMetaReconciliationResult result) {
        String detailsJson = result.getDetails();
        if (detailsJson == null || detailsJson.trim().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_RECON_DETAILS_EMPTY).param("resultId", result.getResultId());
        }
        Object parsed = JsonTool.parse(detailsJson);
        if (!(parsed instanceof List)) {
            throw new NopException(NopMetadataErrors.ERR_RECON_DETAILS_EMPTY).param("resultId", result.getResultId());
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) parsed;
        return new ArrayList<>(list);
    }

    private void checkRowIndex(String resultId, int rowIndex, int detailsSize) {
        if (rowIndex < 0 || rowIndex >= detailsSize) {
            throw new NopException(NopMetadataErrors.ERR_RECON_ROW_INDEX_OUT_OF_RANGE)
                    .param("resultId", resultId)
                    .param("rowIndex", rowIndex)
                    .param("detailsSize", detailsSize);
        }
    }

    private static int toInt(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return Integer.parseInt(String.valueOf(v));
    }

    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
