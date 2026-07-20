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
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.message.IMessageService;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.http.api.client.IHttpClient;
import io.nop.metadata.biz.INopMetaQualityCheckpointBiz;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.quality.CheckpointActionDispatcher;
import io.nop.metadata.service.quality.MetaQualityCheckpointExecutor;
import io.nop.metadata.service.quality.MetaQualityCheckpointScheduler;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.quality.QualityResultWriter;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量检查点 BizModel：基线 CRUD（{@link CrudBizModel}）+ 检查点批量执行（架构基线 §2.7.3）。
 *
 * <p>检查点编排（D2/D3/D4/D5）委托无状态 {@link MetaQualityCheckpointExecutor}，后者复用既有 §2.7.1 单规则
 * 执行路径（{@link MetaQualityRuleExecutor#judge} + {@link TableReferenceExecutor} + {@link QualityResultWriter}），
 * 产出执行摘要。本 BizModel 仅承担入口加载 + 延迟装配 executor（需 {@code orm()}，构造时不可用）。
 *
 * <p>D6（自动评分触发，§2.7.3 D6）：{@code executeCheckpoint} 在 executor 返回后，按摘要中的
 * {@code affectedTableIds}（仅实际被判定 judge 的非 database 规则的去重 tableId）逐表调注入的
 * {@link NopMetaQualityScoreBizModel#computeQualityScore}（含 score + 落盘 + 返回 scoreId），**复用既有 scorer，
 * 零落盘逻辑复制**（不在本 BizModel 内 new scorer、不复制 ScoreBizModel 落盘六行）。per-table try/catch +
 * {@code clearSession} 失败隔离（对齐既有 per-rule 隔离模式），失败记入摘要 errors 不中断其他表评分、不回滚
 * 已落盘的 checkpoint store。受 {@code extConfig.autoScore} 控制（默认开启；关闭时跳过且摘要标注 skipped）。
 *
 * <p>失败路径显式化：检查点不存在 → 抛 {@link #NopMetadataErrors.ERR_CHECKPOINT_NOT_FOUND}；其余不可执行路径（非 ACTIVE 状态 /
 * 未知动作 / 空规则集 / 单规则执行异常）由 executor 显式处理（详见 {@link MetaQualityCheckpointExecutor}）。
 */
@BizModel("NopMetaQualityCheckpoint")
public class NopMetaQualityCheckpointBizModel extends CrudBizModel<NopMetaQualityCheckpoint>
        implements INopMetaQualityCheckpointBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaQualityCheckpointBizModel.class);


    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    /**
     * 注入 {@link NopMetaQualityScoreBizModel}（NopIoC bean）调 {@code computeQualityScore} 实现自动评分触发
     * （B2 方案 b，对齐 {@code NopMetaReconciliationConfigBizModel} 注入 {@code NopMetaTableBizModel} 模式）。
     *
     * <p>维度07-02 裁定（plan 2026-07-19-1250-3 Phase 1）：保留 raw impl 注入而非 {@code INopMetaQualityScoreBiz}
     * 接口注入。理由同 {@code MetaQualityCheckpointScheduler.setCheckpointBizModel}：cron 触发的 autoScore
     * 链路经 raw impl 调用，避免 BizProxy 事务隔离问题（TestMetaQualityCheckpointScheduler#testCronJobFireNowWritesResultsAndScores 验证）。
     */
    @Inject
    protected NopMetaQualityScoreBizModel scoreBizModel;

    /**
     * 注入 {@link IHttpClient}（NopIoC bean，{@code @Nullable}——宿主未拉 HTTP client impl 时不注入）。
     * webhook 动作经此投递执行摘要。为 null 时 webhook 动作显式失败（不 NPE、不启动失败）。
     */
    @Inject
    @Nullable
    protected IHttpClient httpClient;

    /**
     * 注入 {@link IMessageService}（NopIoC bean，{@code @Nullable}——宿主未注册消息实现时不注入）。
     * notify 动作经此向消息通道投递执行摘要。为 null 时 notify 动作显式失败（不 NPE、不静默）。
     */
    @Inject
    @Nullable
    protected IMessageService messageService;

    /**
     * 维度13-04：webhook SSRF 防护的 host allowlist（逗号分隔，小写 host）。
     * 默认空：fail-closed 拒绝内网（部署 webhook 必须先配 {@code nop.metadata.checkpoint.webhook-allowed-hosts}）。
     */
    @InjectValue(value = "@cfg:nop.metadata.checkpoint.webhook-allowed-hosts|")
    protected String webhookAllowedHostsCsv = "";

    /**
     * 维度13-04：webhook 超时（秒）。默认 30s。{@code @InjectValue} 默认空时由 dispatcher 用 {@code DEFAULT_WEBHOOK_TIMEOUT_SECONDS}。
     */
    @InjectValue(value = "@cfg:nop.metadata.checkpoint.webhook-timeout-seconds|0")
    protected int webhookTimeoutSeconds = 0;

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

    /** 结果动作分发器（webhook/notify 投递）。延迟初始化，依赖注入的 httpClient/messageService。 */
    private CheckpointActionDispatcher actionDispatcher;

    public NopMetaQualityCheckpointBizModel() {
        setEntityName(NopMetaQualityCheckpoint.class.getName());
    }

    /**
     * 手动执行检查点（架构基线 §2.7.3 D5 + D6 自动评分触发）。
     *
     * <p>加载检查点 → 委托 {@link MetaQualityCheckpointExecutor#execute}（状态门禁 → 动作校验 → 规则解析 →
     * 逐条复用单规则执行路径写 NopMetaQualityResult → 失败隔离 → 摘要 + 收集 affectedTableIds）。
     *
     * <p>执行完成后，若 {@code extConfig.autoScore} 非 false（默认开启），按 {@code affectedTableIds} 逐表调
     * {@link NopMetaQualityScoreBizModel#computeQualityScore} 重算评分并落盘，per-table 失败隔离，per-table
     * 评分结果（scoreId/overallScore）与评分 errors 记入摘要。
     *
     * @param checkpointId  检查点 ID
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @param context       服务上下文
     * @return {@code {checkpointId, executedCount, passCount, failCount, errorCount, affectedTableIds:[...],
     *         autoScore, scoreResults:[{metaTableId, scoreId, overallScore}], results:[...], errors:[...]}}
     */
    @BizMutation
    public Map<String, Object> executeCheckpoint(@Name("checkpointId") String checkpointId,
                                                  @Optional @Name("schemaPattern") String schemaPattern,
                                                  IServiceContext context) {
        NopMetaQualityCheckpoint cp = dao().getEntityById(checkpointId);
        if (cp == null) {
            throw new NopException(NopMetadataErrors.ERR_CHECKPOINT_NOT_FOUND).param("checkpointId", checkpointId);
        }
        Map<String, Object> summary = ensureCheckpointExecutor().execute(cp, schemaPattern);

        // D6：自动评分触发——按 affectedTableIds 逐表重算评分（复用既有 scorer，零落盘逻辑复制）
        triggerAutoScoring(cp, summary, context);

        // D4：结果动作投递——store 提交后才触发 webhook/notify（post-commit dispatch）
        dispatchActions(cp, summary);
        return summary;
    }

    // ============================================================
    // §2.7.3.1 D4：运行时增量调度 hook（save/delete override）
    // ============================================================

    /**
     * save override（§2.7.3.1 D4 运行时增量）：持久化后通知调度器重新注册该检查点的 cron job。
     *
     * <p>调度器经 {@link BeanContainer#tryGetBean} 懒查找（非 {@code @Inject}），避免与
     * {@link MetaQualityCheckpointScheduler}（注入本 BizModel）构成构造期循环依赖。调度器 bean 缺失
     * （宿主未注册 {@code IJobScheduler}）时 tryGetBean 返回 null，跳过（不抛崩）。
     */
    @Override
    public NopMetaQualityCheckpoint save(Map<String, Object> data, IServiceContext context) {
        NopMetaQualityCheckpoint saved = super.save(data, context);
        notifySchedulerRegister(saved.getCheckpointId());
        return saved;
    }

    /**
     * delete override（§2.7.3.1 D4 运行时增量）：删除前先移除该检查点的 cron job，避免删除后 cron 仍触发
     * （触发时 {@code executeCheckpoint} 会因检查点不存在抛 {@code NopMetadataErrors.ERR_CHECKPOINT_NOT_FOUND}，但提前移除更干净）。
     */
    @Override
    public boolean delete(String id, IServiceContext context) {
        notifySchedulerUnregister(id);
        return super.delete(id, context);
    }

    private void notifySchedulerRegister(String checkpointId) {
        try {
            MetaQualityCheckpointScheduler scheduler = (MetaQualityCheckpointScheduler)
                    BeanContainer.tryGetBean(MetaQualityCheckpointScheduler.BEAN_NAME);
            if (scheduler != null) {
                scheduler.registerCheckpoint(checkpointId);
            }
        } catch (Exception e) {
            // 调度器注册失败不影响 save 主路径（调度是旁路能力）
            LOG.warn("nop.meta.checkpoint-scheduler.register-after-save-failed: checkpointId={}", checkpointId, e);
        }
    }

    private void notifySchedulerUnregister(String checkpointId) {
        try {
            MetaQualityCheckpointScheduler scheduler = (MetaQualityCheckpointScheduler)
                    BeanContainer.tryGetBean(MetaQualityCheckpointScheduler.BEAN_NAME);
            if (scheduler != null) {
                scheduler.unregisterCheckpoint(checkpointId);
            }
        } catch (Exception e) {
            LOG.warn("nop.meta.checkpoint-scheduler.unregister-before-delete-failed: checkpointId={}", checkpointId, e);
        }
    }

    // ============================================================
    // D6：自动评分触发
    // ============================================================

    /**
     * 按 {@code affectedTableIds} 逐表触发自动评分（§2.7.3 D6）。
     *
     * <p>受 {@code extConfig.autoScore} 控制（默认开启）。开启时逐表调
     * {@link NopMetaQualityScoreBizModel#computeQualityScore}，per-table try/catch +
     * {@code flushSession/clearSession} 失败隔离（对齐既有 per-rule 隔离模式）。
     * 关闭时跳过且摘要标注 {@code autoScore=false} + {@code scoreSkipped=true}。
     */
    @SuppressWarnings("unchecked")
    private void triggerAutoScoring(NopMetaQualityCheckpoint cp, Map<String, Object> summary,
                                     IServiceContext context) {
        boolean autoScore = readAutoScoreConfig(cp);
        summary.put("autoScore", autoScore);

        List<String> affectedTableIds = (List<String>) summary.get("affectedTableIds");
        if (!autoScore) {
            summary.put("scoreSkipped", true);
            return;
        }
        summary.put("scoreSkipped", false);

        List<Map<String, Object>> scoreResults = new ArrayList<>();
        // 复用摘要既有 errors 列表（追加评分 errors，不新建独立列表）
        List<Map<String, Object>> errors = (List<Map<String, Object>>) summary.get("errors");

        if (affectedTableIds == null || affectedTableIds.isEmpty()) {
            summary.put("scoreResults", scoreResults);
            return;
        }

        for (String metaTableId : affectedTableIds) {
            try {
                Map<String, Object> scoreSummary = scoreBizModel.computeQualityScore(metaTableId, context);
                // flush 以隔离：已落盘的评分行在后续表评分失败时（clearSession）不丢失
                orm().flushSession();

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("metaTableId", metaTableId);
                entry.put("scoreId", scoreSummary.get("scoreId"));
                entry.put("overallScore", scoreSummary.get("overallScore"));
                scoreResults.add(entry);
            } catch (Exception e) {
                LOG.error("auto-score failed for affected table: {}", metaTableId, e);
                Map<String, Object> errEntry = new LinkedHashMap<>();
                errEntry.put("source", "autoScore");
                errEntry.put("metaTableId", metaTableId);
                errEntry.put("error", toErrorMessage(e));
                errors.add(errEntry);
                // 失败隔离：清理未刷出的脏实体，不影响已 flush 的评分行与其他表评分
                orm().clearSession();
            }
        }
        summary.put("scoreResults", scoreResults);
    }

    /**
     * 读取 {@code extConfig.autoScore}（默认开启；仅显式 {@code false} 关闭）。extConfig 缺失 / 非 JSON Map /
     * 无 autoScore 键 / 值非布尔 → 默认开启（不静默伪造关闭）。
     */
    @SuppressWarnings("unchecked")
    private boolean readAutoScoreConfig(NopMetaQualityCheckpoint cp) {
        String json = cp.getExtConfig();
        if (json == null || json.trim().isEmpty()) {
            return true;
        }
        try {
            Object parsed = JsonTool.parse(json);
            if (!(parsed instanceof Map)) {
                return true;
            }
            Object val = ((Map<String, Object>) parsed).get("autoScore");
            // 仅显式 false 关闭；null / 非布尔 / 缺失 → 默认开启
            return !Boolean.FALSE.equals(val);
        } catch (Exception e) {
            // extConfig 不可解析 → 默认开启（不静默伪造关闭）
            return true;
        }
    }

    // ============================================================
    // helpers
    // ============================================================

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }

    // ============================================================
    // D4：结果动作投递（transaction-isolated dispatch）
    // ============================================================

    /**
     * 执行结果动作投递（§2.7.3 D4）：store（QualityResult）落盘 + flush 后，按 {@code actions} 配置向外部
     * 投递执行摘要（webhook/notify）。
     *
     * <p><b>事务隔离</b>：dispatch 经 {@link ITransactionTemplate#runWithoutTransaction} 在 store 事务之外执行。
     * 由此（a）dispatch 异常/超时<b>不可能回滚</b> store（dispatcher 内部 per-action try/catch 隔离 + 不在事务内）；
     * （b）HTTP/消息调用<b>不占用</b> store 事务连接（dispatch 在 runWithoutTransaction 内运行）。
     *
     * <p>store 已由 executor 的 per-rule {@code flushSession} 落盘（可见且持久），dispatch 在 flush 之后运行。
     * dispatcher 内部 per-action try/catch + 顶层 try/catch 双重兜底，保证投递失败不阻断 executeCheckpoint 返回。
     */
    private void dispatchActions(NopMetaQualityCheckpoint cp, Map<String, Object> summary) {
        CheckpointActionDispatcher dispatcher = ensureActionDispatcher();
        ITransactionTemplate txnTemplate = orm().getSessionFactory().txn();
        try {
            txnTemplate.runWithoutTransaction(null, () -> {
                dispatcher.dispatch(cp, summary);
                return null;
            });
        } catch (Exception e) {
            // dispatcher 内部 per-action try/catch 已隔离；此处仅兜底防异常外泄到 executeCheckpoint
            LOG.error("action dispatch failed for checkpoint {}", cp.getCheckpointId(), e);
        }
    }

    /**
     * 延迟初始化动作分发器。httpClient/messageService 由 IoC 注入（@Nullable——宿主未注册实现时为 null）。
     * 均为 null 时分发器仍可创建（webhook/notify 配置存在时在 dispatch 时显式失败，不静默跳过）。
     *
     * <p>维度13-04：把 webhook SSRF 配置（allowed-hosts + timeout）经 {@code configureWebhookSsrf} 注入到 dispatcher。
     */
    private CheckpointActionDispatcher ensureActionDispatcher() {
        if (actionDispatcher == null) {
            actionDispatcher = new CheckpointActionDispatcher(httpClient, messageService);
            actionDispatcher.configureWebhookSsrf(webhookAllowedHostsCsv, webhookTimeoutSeconds);
        }
        return actionDispatcher;
    }

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
