package io.nop.metadata.service.quality;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.wf.api.WfReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.metadata.service.NopMetadataException;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

public class QualityAlertWorkflowProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(QualityAlertWorkflowProcessor.class);

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    @Inject
    protected IOrmTemplate orm;

    @Inject
    @Nullable
    protected IWorkflowManager wfManager;

    private final MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
    private final MetaTableReferenceResolver tableRefResolver = new MetaTableReferenceResolver();
    private TableReferenceExecutor tableRefExecutor;

    /**
     * 在质量规则 FAIL + severity=ERROR 时创建质量告警工作流实例
     *
     * @param result         触发告警的质量结果行
     * @param serviceContext 服务上下文；null 时兜底取当前线程绑定的服务上下文（{@link IServiceContext#getCtx()}），
     *                       仍为空则新建脱离请求的 {@link ServiceContextImpl}。MA7.6-02：此前恒传 null →
     *                       WfRuntime 构造器直接解引用 {@code serviceContext.getEvalScope()} → 确定性 NPE →
     *                       告警流静默不创建。
     */
    public WfReference createAlertWorkflow(NopMetaQualityResult result, IServiceContext serviceContext) {
        if (wfManager == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_WORKFLOW_MANAGER_UNAVAILABLE)
                    .param(NopMetadataErrors.ARG_QUALITY_RESULT_ID, result.getQualityResultId());
        }

        NopMetaQualityRule rule = daoFor(NopMetaQualityRule.class).getEntityById(result.getQualityRuleId());
        if (rule == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND)
                    .param("qualityRuleId", result.getQualityRuleId());
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("ruleId", result.getQualityRuleId());
        vars.put("tableId", rule.getEntityId());
        vars.put("severity", rule.getSeverity());
        vars.put("message", result.getMessage());

        IWorkflow wf = wfManager.newWorkflow("qualityBreachApproval", 1L);
        wf.getRecord().setBizObjName("NopMetaQualityResult");
        wf.getRecord().setBizObjId(result.getQualityResultId());

        // MA7.6-02：显式 ctx > 当前线程绑定 ctx > 新建脱离请求 ctx 三级兜底，杜绝 null ctx NPE
        IServiceContext ctx = serviceContext != null ? serviceContext : IServiceContext.getCtx();
        if (ctx == null) {
            ctx = new ServiceContextImpl();
        }
        wf.start(vars, ctx);
        return wf.getWfReference();
    }

    /**
     * 重新执行规则判定（工作流 agree 路径的 re-judge），更新 result 状态
     *
     * @return true=PASS, false=FAIL
     */
    public boolean reJudge(String ruleId, String resultId) {
        NopMetaQualityRule rule = daoFor(NopMetaQualityRule.class).getEntityById(ruleId);
        if (rule == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND)
                    .param("qualityRuleId", ruleId);
        }

        NopMetaQualityResult result = daoFor(NopMetaQualityResult.class).getEntityById(resultId);
        if (result == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUALITY_RESULT_NOT_FOUND)
                    .param("qualityResultId", resultId);
        }

        NopMetaTable table = resolveTargetTableOrThrow(rule);
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm);

        QualityRuleJudgment judgment = ensureTableRefExecutor().execute(ref,
                (Connection conn, DatabaseMetaData metaData, String productName) ->
                        executor.judge(conn, ref, table.getMetaSchema(),
                                rule.getRuleType(), rule.getEntityType(),
                                rule.getParams(), rule.getSqlExpression(),
                                rule.getThreshold(), productName));

        result.setStatus(judgment.getStatus());
        result.setActualValue(judgment.getActualValue());
        result.setExpectedValue(judgment.getExpectedValue());
        result.setMessage(judgment.getMessage());
        result.setDetails(JsonTool.stringify(judgment.getDetails()));
        daoFor(NopMetaQualityResult.class).updateEntity(result);

        return "PASS".equals(judgment.getStatus());
    }

    private NopMetaTable resolveTargetTableOrThrow(NopMetaQualityRule rule) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(rule.getEntityId());
        if (table == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_QUALITY_TABLE_NOT_FOUND)
                    .param("qualityRuleId", rule.getQualityRuleId())
                    .param("entityId", rule.getEntityId());
        }
        return table;
    }

    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm);
        }
        return tableRefExecutor;
    }

    private <T extends IDaoEntity> IEntityDao<T> daoFor(Class<T> clazz) {
        return daoProvider.daoFor(clazz);
    }
}
