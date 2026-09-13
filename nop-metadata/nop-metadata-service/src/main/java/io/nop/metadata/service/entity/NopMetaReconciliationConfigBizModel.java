
package io.nop.metadata.service.entity;


import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaReconciliationConfigBiz;
import io.nop.metadata.biz.INopMetaReconciliationResultBiz;
import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaReconciliationConfig;
import io.nop.metadata.dao.entity.NopMetaReconciliationResult;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.reconciliation.IReconciliationProcessor;
import io.nop.metadata.service.reconciliation.ReconciliationExecutor;
import io.nop.metadata.service.NopMetadataException;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对账配置 BizModel：基线 CRUD（{@link CrudBizModel}）+ 对账执行入口
 * （设计 08-reconciliation.md §3.3 行为契约，plan 0900-2 Phase 2）。
 *
 * <p>{@code executeReconciliation(configId)}（{@code @BizMutation}）：
 * <ol>
 *   <li>加载 config；config 不存在 → {@code requireEntity} 抛平台标准 not-found 错误（不 NPE）。</li>
 *   <li>校验 {@code columnName} 在目标表 {@link MetaTableFieldResolver} 解析字段集合内；
 *       非法 → 抛 {@link #NopMetadataErrors.ERR_RECON_COLUMN_NOT_FOUND}。</li>
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


    /**
     * B2 方案 b：plan 2026-07-19-1250-3 Phase 1 维度07-02——注入 {@link INopMetaTableBiz} 接口
     * （而非 NopMetaTableBizModel 具体类）调 queryTableData 取数。
     */
    @Inject
    protected INopMetaTableBiz tableBizModel;

    /** 跨聚合访问（plan 353 MD-1）：ReconciliationResult 写入经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaReconciliationResultBiz reconciliationResultBiz;

    /**
     * check2 P2-07（2026-08-23 审计）：对账取数上限。修复前 executeReconciliation 传 limit=null，
     * 被 queryTableData 的防 OOM 缺省（1000）静默截断——对账统计（statistics.totalRows/matchRate
     * 持久化）在 >1000 行表上系统性失真且无截断标记。默认对齐 queryTableData 上限
     * （{@link NopMetaTableBizModel#DEFAULT_MAX_QUERY_LIMIT}），可经
     * {@code nop.metadata.reconciliation.fetch-limit} 显式配置；无论实际取到多少行，
     * statistics 恒记录 fetchedLimit + truncated（达上限即保守置 true，失真可见可诊断）。
     */
    @InjectValue(value = "@cfg:nop.metadata.reconciliation.fetch-limit|0")
    protected int configuredReconFetchLimit = 0;

    /** 跨表类型字段解析器（校验 config.columnName 在目标表可用字段集合内）。无状态。 */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    /** 对账执行器（纯组件，rows 由本 BizModel 传入）。 */
    protected ReconciliationExecutor reconciliationExecutor;

    @Inject
    protected IReconciliationProcessor reconciliationService;

    public NopMetaReconciliationConfigBizModel() {
        setEntityName(NopMetaReconciliationConfig.class.getName());
    }

    @PostConstruct
    public void init() {
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
        NopMetaReconciliationConfig config = requireEntity(configId, "executeReconciliation", context);
        String metaTableId = config.getMetaTableId();

        // 校验目标表存在（跨聚合读取经 Biz 接口，plan 353 MD-1）
        NopMetaTable table = tableBizModel.get(metaTableId, false, context);
        if (table == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_RECON_TABLE_NOT_FOUND)
                    .param("configId", configId)
                    .param("metaTableId", String.valueOf(metaTableId));
        }

        // 校验 columnName 在目标表可用字段集合内（不静默放行非法列名）
        // resolver 边界：MetaTableFieldResolver API 消费 IEntityDao（resolver 包不在 MD-1 转换范围），保留 dao 直连（plan 353 MD-1 裁定）
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        Set<String> availableFields = fieldResolver.resolveFieldNames(table, fieldDao);
        String columnName = config.getColumnName();
        if (columnName == null || !availableFields.contains(columnName)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_RECON_COLUMN_NOT_FOUND)
                    .param("configId", configId)
                    .param("metaTableId", metaTableId)
                    .param("columnName", String.valueOf(columnName))
                    .param("availableFields", availableFields);
        }

        // 取数：BizModel 调 queryTableData 取 items（B2 方案 b）。失败显式抛 ErrorCode（不吞异常）。
        // check2 P2-07：显式传入对账取数上限（不再走 null → 缺省 1000 的静默截断路径）。
        long fetchLimit = reconFetchLimit();
        List<Map<String, Object>> items;
        try {
            items = tableBizModel.queryTableData(metaTableId, null, fetchLimit, null, null, context).getItems();
        } catch (NopException e) {
            // queryTableData 内部已抛带语义的 ErrorCode，此处附加 config 上下文后重新抛出
            throw new NopMetadataException(NopMetadataErrors.ERR_RECON_FETCH_TABLE_DATA_FAILED, e)
                    .param("configId", configId)
                    .param("metaTableId", metaTableId)
                    .param("error", messageOf(e));
        }

        // 执行器纯组件消费 items，产出未持久化的 Result
        NopMetaReconciliationResult result = reconciliationExecutor.execute(config, items);

        // check2 P2-07：statistics 记录 fetchedLimit + truncated——达上限即无法区分是否还有
        // 更多行，保守置 true（fail-visible：跨上限边界的前后两次执行结果不可比时可诊断）。
        Map<String, Object> statistics = parseStatistics(result.getStatistics());
        statistics.put("fetchedLimit", fetchLimit);
        statistics.put("truncated", items != null && items.size() >= fetchLimit);
        result.setStatistics(JsonTool.stringify(statistics));

        result.setExecuteTime(CoreMetrics.currentTimestamp());

        // 落库（跨聚合写入经 Biz 接口，plan 353 MD-1）
        reconciliationResultBiz.saveEntity(result, null, context);
        return result;
    }

    /** 对账取数上限：显式配置优先，缺省对齐 queryTableData 上限（DEFAULT_MAX_QUERY_LIMIT）。 */
    private long reconFetchLimit() {
        return configuredReconFetchLimit > 0 ? configuredReconFetchLimit
                : NopMetaTableBizModel.DEFAULT_MAX_QUERY_LIMIT;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseStatistics(String statisticsJson) {
        if (statisticsJson == null || statisticsJson.trim().isEmpty()) {
            return new java.util.LinkedHashMap<>();
        }
        Object parsed = JsonTool.parse(statisticsJson);
        return parsed instanceof Map ? (Map<String, Object>) parsed : new java.util.LinkedHashMap<>();
    }

    private static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }
}
