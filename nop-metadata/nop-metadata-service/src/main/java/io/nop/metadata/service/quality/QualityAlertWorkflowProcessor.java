package io.nop.metadata.service.quality;

import io.nop.metadata.core._NopMetadataCoreConstants;
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
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.tableref.MetaEntityReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import io.nop.orm.IOrmTemplate;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.wf.api.WfReference;
import io.nop.wf.core.IWorkflow;
import io.nop.wf.core.IWorkflowManager;
import io.nop.wf.core.store.IWorkflowStore;
import io.nop.wf.core.NopWfCoreConstants;
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

    /**
     * W-12（plan 2528 裁定⑨）：wf.start 半程失败时的实例自清理通道。经 nop-wf 既有公开能力
     * {@link IWorkflowStore#removeWfRecord}（bean {@code nopWorkflowStore}，wf-dao.beans.xml ioc:default）
     * 消费，nop-wf 零产品 diff。注入缺失（部署裁剪 nop-wf-dao 理论形态）时 WARN + 退化为现状行为
     * （半程实例残留显式可见，非静默）。与第七面 retention（NopMetaLedgerCleaner 第七通道）共享
     * 同一删除原语，禁两套清理机制对同一表各自发明。
     */
    @Inject
    @Nullable
    protected IWorkflowStore workflowStore;

    private final MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();
    private final MetaEntityReferenceResolver tableRefResolver = new MetaEntityReferenceResolver();
    // W-04（plan 2528 裁定③）：懒初始化实例字段 volatile 安全发布（R23 族先例——可见性缺口闭合；check-then-act 重复构造为良性竞态）
    private volatile TableReferenceExecutor tableRefExecutor;

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
        // 业务对象信息必须经 start args 传入：WorkflowEngineImpl.removeStdStartParam 会以 args 中的
        // bizObjName/bizObjId 覆盖 record 上预先 set 的值（args 缺失时覆盖为 null）
        vars.put(NopWfCoreConstants.PARAM_BIZ_OBJ_NAME, "NopMetaQualityResult");
        vars.put(NopWfCoreConstants.PARAM_BIZ_OBJ_ID, result.getQualityResultId());

        IWorkflow wf = wfManager.newWorkflow("qualityBreachApproval", 1L);

        // MA7.6-02：显式 ctx > 当前线程绑定 ctx > 新建脱离请求 ctx 三级兜底，杜绝 null ctx NPE
        IServiceContext ctx = serviceContext != null ? serviceContext : IServiceContext.getCtx();
        if (ctx == null) {
            ctx = new ServiceContextImpl();
        }
        try {
            wf.start(vars, ctx);
        } catch (Exception e) {
            // W-12（plan 2528 裁定⑨，R10 族分支 (a) 补偿）：wf.start save-then-finish 窗口
            // （引擎 initCreateStatus 先落库 CREATED → 起始步 actor 解析/saveStarted 前任一步抛错）
            // 半程失败时删除已落库的半程实例，随后重抛原异常——调用方既有 catch 隔离语义零变化，
            // 不再累积 CREATED 零 step 孤儿。自清理与 store 同一 ORM 会话（无 REQUIRES_NEW）：
            // 外层提交 → 创建+删除同净效应；外层回滚 → 创建本身消失，天然一致。
            removeHalfStartedWorkflow(wf, e);
            throw e;
        }
        return wf.getWfReference();
    }

    /**
     * W-12（裁定⑨）：半程实例自清理。仅当 record 已持久化（wfId 非空）时删除——start 在
     * initCreateStatus 之前失败（如 allowStart 条件/参数校验）时无行可清，直接跳过。
     * 清理体自身失败（理论不可达——同会话同实体 dao）时保留原异常并 ERROR 日志显式可见
     * （fail-visible，非吞）。
     */
    private void removeHalfStartedWorkflow(IWorkflow wf, Exception original) {
        if (wf == null) {
            return;
        }
        if (workflowStore == null) {
            LOG.warn("nop.meta.quality-alert.half-start-cleanup-unavailable: wf.start failed and no"
                            + " IWorkflowStore bean is available; the half-started instance (wfName={}) may"
                            + " remain as a CREATED orphan (deployments without nop-wf-dao)",
                    wf.getWfName(), original);
            return;
        }
        try {
            if (wf.getRecord() == null || wf.getRecord().getWfId() == null) {
                // start 在 CREATED 落库前失败——无行可清
                return;
            }
            workflowStore.removeWfRecord(wf.getRecord());
            LOG.info("nop.meta.quality-alert.half-start-cleanup: removed half-started workflow instance"
                    + " wfName={}, wfId={} after start failure", wf.getWfName(), wf.getRecord().getWfId());
        } catch (Exception cleanupEx) {
            LOG.error("nop.meta.quality-alert.half-start-cleanup-failed: errorCode={} wfName={}",
                    NopMetadataErrors.ERR_QUALITY_ALERT_WORKFLOW_ISOLATED.getErrorCode(),
                    wf.getWfName(), cleanupEx);
        }
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

        NopMetaEntity table = resolveTargetEntityOrThrow(rule);
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm);

        QualityRuleJudgment judgment = ensureTableRefExecutor().execute(ref,
                (Connection conn, DatabaseMetaData metaData, String productName) ->
                        executor.judge(conn, ref, table.getDbSchema(),
                                rule.getRuleType(), rule.getEntityType(),
                                rule.getParams(), rule.getSqlExpression(),
                                rule.getThreshold(), productName));

        result.setStatus(judgment.getStatus());
        result.setActualValue(judgment.getActualValue());
        result.setExpectedValue(judgment.getExpectedValue());
        result.setMessage(judgment.getMessage());
        result.setDetails(JsonTool.stringify(judgment.getDetails()));
        daoFor(NopMetaQualityResult.class).updateEntity(result);

        return _NopMetadataCoreConstants.QUALITY_RESULT_STATUS_PASS.equals(judgment.getStatus());
    }

    /**
     * MA7.6-03 fail-closed 包装：reJudge 内部异常（规则/结果/目标表缺失或连库失败）转 false（reject），
     * 供 xwf verify 脚本调用（XLang 不支持 try/catch 语句，异常处理必须在 Java 侧完成——脚本侧
     * try/catch 会在 XLang 编译期抛 not-supported-node）。
     */
    public boolean reJudgeFailClosed(String ruleId, String resultId) {
        try {
            return reJudge(ruleId, resultId);
        } catch (Exception e) {
            LOG.warn("nop.meta.quality-alert.rejudge-failed: errorCode={} ruleId={} resultId={}",
                    NopMetadataErrors.ERR_QUALITY_ALERT_WORKFLOW_ISOLATED.getErrorCode(),
                    ruleId, resultId, e);
            return false;
        }
    }

    private NopMetaEntity resolveTargetEntityOrThrow(NopMetaQualityRule rule) {
        IEntityDao<NopMetaEntity> tableDao = daoFor(NopMetaEntity.class);
        NopMetaEntity table = tableDao.getEntityById(rule.getEntityId());
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
