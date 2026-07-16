package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaQualityCheckpointBiz;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionService;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.quality.MetaQualityCheckpointExecutor;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.quality.QualityResultWriter;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * 质量检查点 BizModel：基线 CRUD（{@link CrudBizModel}）+ 检查点批量执行（架构基线 §2.7.3）。
 *
 * <p>检查点编排（D2/D3/D4/D5）委托无状态 {@link MetaQualityCheckpointExecutor}，后者复用既有 §2.7.1 单规则
 * 执行路径（{@link MetaQualityRuleExecutor#judge} + {@link TableReferenceExecutor} + {@link QualityResultWriter}），
 * 产出执行摘要。本 BizModel 仅承担入口加载 + 延迟装配 executor（需 {@code orm()}，构造时不可用）。
 *
 * <p>失败路径显式化：检查点不存在 → 抛 {@link #ERR_CHECKPOINT_NOT_FOUND}；其余不可执行路径（非 ACTIVE 状态 /
 * 未知动作 / 空规则集 / 单规则执行异常）由 executor 显式处理（详见 {@link MetaQualityCheckpointExecutor}）。
 */
@BizModel("NopMetaQualityCheckpoint")
public class NopMetaQualityCheckpointBizModel extends CrudBizModel<NopMetaQualityCheckpoint>
        implements INopMetaQualityCheckpointBiz {

    static final ErrorCode ERR_CHECKPOINT_NOT_FOUND =
            ErrorCode.define("metadata.checkpoint-not-found",
                    "Quality checkpoint not found: {checkpointId}", "checkpointId");

    @Inject
    protected IMetaDataSourceConnectionService connectionService;

    /** 共享 table-reference 解析器（架构基线 §4.4.3 D3）。 */
    private final MetaTableReferenceResolver tableRefResolver = new MetaTableReferenceResolver(
            new MetaDataSourceResolver(), new io.nop.metadata.service.field.MetaTableFieldResolver());

    /** 质量规则执行器（无状态，复用 §2.7.1 judge 算法）。 */
    private final MetaQualityRuleExecutor ruleExecutor = new MetaQualityRuleExecutor();

    /** 结果写入共享 helper（§2.7.3 D3）。 */
    private final QualityResultWriter resultWriter = new QualityResultWriter();

    /** 按 table-reference 形态分派 Connection 获取（§4.4.3 D1/D2）。延迟初始化（需 orm()）。 */
    private TableReferenceExecutor tableRefExecutor;

    /** 检查点编排执行器（延迟初始化，需 orm() + tableRefExecutor）。 */
    private MetaQualityCheckpointExecutor checkpointExecutor;

    public NopMetaQualityCheckpointBizModel() {
        setEntityName(NopMetaQualityCheckpoint.class.getName());
    }

    /**
     * 手动执行检查点（架构基线 §2.7.3 D5，仅手动触发）。
     *
     * <p>加载检查点 → 委托 {@link MetaQualityCheckpointExecutor#execute}（状态门禁 → 动作校验 → 规则解析 →
     * 逐条复用单规则执行路径写 NopMetaQualityResult → 失败隔离 → 摘要）。
     *
     * @param checkpointId  检查点 ID
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @param context       服务上下文
     * @return {@code {checkpointId, executedCount, passCount, failCount, errorCount, results:[...], errors:[...]}}
     */
    @BizMutation
    public Map<String, Object> executeCheckpoint(@Name("checkpointId") String checkpointId,
                                                  @Optional @Name("schemaPattern") String schemaPattern,
                                                  IServiceContext context) {
        NopMetaQualityCheckpoint cp = dao().getEntityById(checkpointId);
        if (cp == null) {
            throw new NopException(ERR_CHECKPOINT_NOT_FOUND).param("checkpointId", checkpointId);
        }
        return ensureCheckpointExecutor().execute(cp, schemaPattern);
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 延迟初始化 TableReferenceExecutor（需 orm()，构造时 orm 不可用）。 */
    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    /** 延迟初始化 MetaQualityCheckpointExecutor（需 orm() + tableRefExecutor）。 */
    private MetaQualityCheckpointExecutor ensureCheckpointExecutor() {
        if (checkpointExecutor == null) {
            checkpointExecutor = new MetaQualityCheckpointExecutor(ruleExecutor, tableRefResolver,
                    ensureTableRefExecutor(), resultWriter, daoProvider(), orm());
        }
        return checkpointExecutor;
    }
}
