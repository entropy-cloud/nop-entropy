package io.nop.metadata.service.entity;


import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaReconciliationConfigBiz;
import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.reconciliation.IReconciliationProcessor;
import io.nop.metadata.service.reconciliation.ReconciliationExecutor;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对账配置 BizModel：基线 CRUD（{@link CrudBizModel}）+ 对账执行入口
 * （设计 08-reconciliation.md §3.3 行为契约，plan 0900-2 Phase 2）。
 *
 * <p>{@code executeReconciliation(configId)}（{@code @BizMutation}）：
 * <ol>
 *   <li>加载 config；config 不存在 → 抛 {@link #ERR_RECON_CONFIG_NOT_FOUND}（不 NPE）。</li>
 *   <li>校验 {@code columnName} 在目标表 {@link MetaTableFieldResolver} 解析字段集合内；
 *       非法 → 抛 {@link #ERR_RECON_COLUMN_NOT_FOUND}。</li>
 *   <li>经 {@code @Inject NopMetaTableBizModel tableBizModel}（protected，B2 方案 b）调
 *       {@code queryTableData(metaTableId, null, null, null, context)} 取得 {@code items}（行列表）。</li>
 *   <li>调 {@link ReconciliationExecutor#execute}（rows 由本 BizModel 传入，执行器纯组件）→ 返回 Result。</li>
 * </ol>
 *
 * <p>失败路径显式（不吞异常、不静默跳过）：config 不存在 / tableId 不存在 / columnName 非法 /
 * queryTableData 失败 / 行缺失列名键均抛 ErrorCode。空候选→UNMATCHED 体现在结果（非整体异常、不静默 pass）。
 *
 * <p>ErrorCode 按模块惯例内联于本类顶部。平台 IoC：{@code @Inject} 使用 {@code protected} 字段（AGENTS.md）。
 */
@BizModel("NopMetaReconciliationConfig")
public class NopMetaReconciliationConfigBizModel extends CrudBizModel<NopMetaReconciliationConfig>
        implements INopMetaReconciliationConfigBiz {

    static final ErrorCode ERR_RECON_CONFIG_NOT_FOUND =
            ErrorCode.define("metadata.recon-config-not-found",
                    "Reconciliation config not found: {configId}", "configId");
    static final ErrorCode ERR_RECON_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.recon-table-not-found",
                    "MetaTable not found for reconciliation: configId={configId} metaTableId={metaTableId}",
                    "configId", "metaTableId");
    static final ErrorCode ERR_RECON_COLUMN_NOT_FOUND =
            ErrorCode.define("metadata.recon-column-not-found",
                    "Configured columnName is not in the table's available field set: "
                            + "configId={configId} metaTableId={metaTableId} columnName={columnName} "
                            + "availableFields={availableFields}",
                    "configId", "metaTableId", "columnName", "availableFields");
    static final ErrorCode ERR_RECON_FETCH_TABLE_DATA_FAILED =
            ErrorCode.define("metadata.recon-fetch-table-data-failed",
                    "queryTableData failed for reconciliation: configId={configId} metaTableId={metaTableId} "
                            + "-- {error}", "configId", "metaTableId", "error");

    /**
     * B2 方案 b：plan 2026-07-19-1250-3 Phase 1 维度07-02——注入 {@link INopMetaTableBiz} 接口
     * （而非 NopMetaTableBizModel 具体类）调 queryTableData 取数。
     */
    @Inject
    protected INopMetaTableBiz tableBizModel;

    /** 跨表类型字段解析器（校验 config.columnName 在目标表可用字段集合内）。无状态。 */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    /** 对账执行器（纯组件，rows 由本 BizModel 传入）。 */
    private final ReconciliationExecutor reconciliationExecutor;

    @Inject
    public NopMetaReconciliationConfigBizModel(IReconciliationProcessor reconciliationService) {
        setEntityName(NopMetaReconciliationConfig.class.getName());
        this.reconciliationExecutor = new ReconciliationExecutor(reconciliationService);
    }

    /**
     * 执行对账（设计 §3.3）。取数由本 BizModel 调 queryTableData 取 items 传入 executor（B2 方案 b）。
     *
     * @param configId 对账配置 ID
     * @param context  服务上下文
     * @return 新建的 {@link NopMetaReconciliationResult}（含 statistics + details）
     */
    @BizMutation
    public NopMetaReconciliationResult executeReconciliation(@Name("configId") String configId,
                                                              IServiceContext context) {
        IEntityDao<NopMetaReconciliationConfig> configDao = dao();
        NopMetaReconciliationConfig config = configDao.getEntityById(configId);
        if (config == null) {
            throw new NopException(ERR_RECON_CONFIG_NOT_FOUND).param("configId", configId);
        }
        String metaTableId = config.getMetaTableId();

        // 校验目标表存在
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_RECON_TABLE_NOT_FOUND)
                    .param("configId", configId)
                    .param("metaTableId", String.valueOf(metaTableId));
        }

        // 校验 columnName 在目标表可用字段集合内（不静默放行非法列名）
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        Set<String> availableFields = fieldResolver.resolveFieldNames(table, fieldDao);
        String columnName = config.getColumnName();
        if (columnName == null || !availableFields.contains(columnName)) {
            throw new NopException(ERR_RECON_COLUMN_NOT_FOUND)
                    .param("configId", configId)
                    .param("metaTableId", metaTableId)
                    .param("columnName", String.valueOf(columnName))
                    .param("availableFields", availableFields);
        }

        // 取数：BizModel 调 queryTableData 取 items（B2 方案 b）。失败显式抛 ErrorCode（不吞异常）。
        List<Map<String, Object>> items;
        try {
            Map<String, Object> queryResult = tableBizModel.queryTableData(metaTableId, null, null, null, null, context);
            items = extractItems(queryResult);
        } catch (NopException e) {
            // queryTableData 内部已抛带语义的 ErrorCode，此处附加 config 上下文后重新抛出
            throw new NopException(ERR_RECON_FETCH_TABLE_DATA_FAILED, e)
                    .param("configId", configId)
                    .param("metaTableId", metaTableId)
                    .param("error", messageOf(e));
        }

        // 执行器纯组件消费 items，产出未持久化的 Result
        NopMetaReconciliationResult result = reconciliationExecutor.execute(config, items);
        result.setExecuteTime(CoreMetrics.currentTimestamp());

        // 落库
        IEntityDao<NopMetaReconciliationResult> resultDao = daoFor(NopMetaReconciliationResult.class);
        resultDao.saveEntity(result);
        return result;
    }

    /** 从 queryTableData 返回结构 {tableType, items:[...]} 提取 items 行列表。 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractItems(Map<String, Object> queryResult) {
        if (queryResult == null) {
            return new ArrayList<>();
        }
        Object itemsObj = queryResult.get("items");
        if (itemsObj instanceof List) {
            return (List<Map<String, Object>>) itemsObj;
        }
        return new ArrayList<>();
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
