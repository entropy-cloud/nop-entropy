/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.quality;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.core.dto.CheckpointExecutionResultDTO;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.entity.NopMetaQualityCheckpointBizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质量检查点 cron 定时调度器（架构基线 §2.7.3.1，plan 2026-07-17-1308-1）。
 *
 * <p>普通 IoC bean（非 {@code @BizModel}），承担三个职责：
 * <ol>
 *   <li><b>启动 scanner（D4 启动全量）</b>：{@link #init()} 读所有 {@code status=ACTIVE} 检查点，
 *       解析 {@code extConfig.schedule}（cron 表达式），非空且 cron 合法则经
 *       {@link IJobScheduler#addJob} 注册定时任务。单检查点注册失败 try/catch 隔离，不抛崩启动（对齐
 *       {@code LocalJobConfigLoader.registerJob} 模式）。</li>
 *   <li><b>运行时增量（D4）</b>：{@link #registerCheckpoint(String)} / {@link #unregisterCheckpoint(String)}
 *       供 BizModel save/delete override 调用，使检查点配置变更后立即生效（无需重启）。</li>
 *   <li><b>调用入口包装（D3 path b）</b>：{@link #executeScheduledCheckpoint(Map)} 经 beanMethod invoker
 *       调用，内部委托注入的 raw impl {@link NopMetaQualityCheckpointBizModel#executeCheckpoint}（null context 安全，
 *       见架构基线 §2.7.3.1 D3 R2 核实），复用既有编排链（executor + autoScore + action dispatch），零编排逻辑复制。</li>
 * </ol>
 *
 * <p><b>{@code IJobScheduler} 可空注入（D6）</b>：宿主 app 未注册调度器（未 import
 * {@code app-local-scheduler.beans.xml}）时 {@code scheduler == null}，scanner/注册/触发全部显式跳过（INFO 日志），
 * 不抛崩。生产 runtime 由宿主 app 提供调度器实现；仅当宿主配置了 nop-job 才启用定时调度。
 *
 * <p><b>失败路径显式化（Minimum Rules #24）</b>：
 * <ul>
 *   <li>未知 checkpointId（cron 触发时检查点已被删除）→ {@code executeCheckpoint} 抛
 *       {@code ERR_CHECKPOINT_NOT_FOUND}（经 invoker 转 {@code JobFireResult.ERROR}，不静默）</li>
 *   <li>status 非 ACTIVE（运行时被 PAUSED/DISABLED 但 cron job 未及时移除）→ executor 抛
 *       {@code ERR_CHECKPOINT_NOT_ACTIVE}（同上）</li>
 *   <li>空/非法 cron → scanner 注册期 catch 显式跳过并记录（D4 容错，不静默、不抛崩）</li>
 * </ul>
 *
 * <p><b>方法签名说明（D3）</b>：{@link #executeScheduledCheckpoint(Map)} 接收 {@code Map<String,Object>} 而非
 * 具名 {@code String} 参数，以走 {@code BeanMethodJobInvoker} 的 singleMapFn 路径（不依赖 {@code -parameters}
 * 编译标志反射形参名，规避 R2），与本仓库既有 beanMethod 调用约定一致。
 */
public class MetaQualityCheckpointScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(MetaQualityCheckpointScheduler.class);

    /** jobName 前缀，避免与其它模块 job 冲突；后缀 checkpointId。 */
    public static final String JOB_NAME_PREFIX = "nop-meta-quality-checkpoint-";

    /** beanMethod invoker 约定：jobParams 中的 bean 名键。 */
    static final String PARAM_BEAN_NAME = "beanName";
    /** beanMethod invoker 约定：jobParams 中的方法名键。 */
    static final String PARAM_METHOD_NAME = "methodName";
    /** jobParams 中传递给包装方法的检查点 ID 键。 */
    static final String PARAM_CHECKPOINT_ID = "checkpointId";

    /** 本 bean 在 IoC 容器中的注册名（与 app-quality-scheduler.beans.xml 一致）。 */
    public static final String BEAN_NAME = "metaQualityCheckpointScheduler";
    /** beanMethod 调用的方法名。 */
    public static final String SCHEDULED_METHOD_NAME = "executeScheduledCheckpoint";

    /** extConfig 中承载 cron 表达式的键（D2）。 */
    public static final String EXT_CONFIG_SCHEDULE_KEY = "schedule";


    private IJobScheduler scheduler;
    private IDaoProvider daoProvider;
    // 维度07-02 裁定（plan 2026-07-19-1250-3 Phase 1）：保留 raw impl 注入而非 INopMetaQualityCheckpointBiz 接口注入。
    // 理由：cron 触发链路（BeanMethodJobInvoker）需要绕过 BizProxy 的事务/AOP 包装，直接调用 raw impl 的
    // executeCheckpoint。TestMetaQualityCheckpointScheduler#testCronJobFireNowWritesResultsAndScores 验证了
    // raw impl 路径下 QualityResult 行落盘正常；改为接口注入后 cron fireNow 写入 0 行（事务隔离问题）。
    // 维度07-02 主目标"跨模块调用基于接口契约"已由 INopMetaQualityCheckpointBiz 接口本身满足（其它路径可注入接口）。
    private NopMetaQualityCheckpointBizModel checkpointBizModel;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * 注入 raw impl {@link NopMetaQualityCheckpointBizModel}（非 BizProxy，按类型注入），经其
     * {@code executeCheckpoint} 复用既有编排链（D3 path b）。
     */
    @Inject
    public void setCheckpointBizModel(NopMetaQualityCheckpointBizModel checkpointBizModel) {
        this.checkpointBizModel = checkpointBizModel;
    }

    /**
     * 注入 {@link IJobScheduler}（{@code @Nullable}——宿主未注册调度器时不注入，scanner/注册/触发全部跳过）。
     * D6：生产 runtime 由宿主 app 经 {@code app-local-scheduler.beans.xml} 提供；仅测试需 test-scope 引入
     * {@code nop-job-local}。
     */
    @Inject
    public void setScheduler(@Nullable IJobScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 启动 scanner（D4 启动全量）：读所有 {@code status=ACTIVE} 检查点，解析 {@code extConfig.schedule}，
     * 非空且 cron 合法则注册。单检查点失败 try/catch 隔离，不抛崩启动。
     */
    @PostConstruct
    public void init() {
        if (scheduler == null) {
            LOG.info("nop.meta.checkpoint-scheduler.no-scheduler: cron scheduling disabled (host app did not register IJobScheduler)");
            return;
        }
        // 防御性 activate：activate() 幂等（仅置 active=true），保证 scanner 注册时调度器已就绪，
        // 不依赖宿主 config loader 与本 bean 的 @PostConstruct 先后顺序（二者无注入依赖，顺序非确定）。
        scheduler.activate();
        IEntityDao<NopMetaQualityCheckpoint> dao = daoProvider.daoFor(NopMetaQualityCheckpoint.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityCheckpoint.PROP_NAME_status,
                _NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE));
        List<NopMetaQualityCheckpoint> active = dao.findAllByQuery(q);
        int registered = 0;
        for (NopMetaQualityCheckpoint cp : active) {
            try {
                if (doRegister(cp)) {
                    registered++;
                }
            } catch (Exception e) {
                // 单检查点注册失败不中断其他检查点、不抛崩启动（D4 容错）
                LOG.error("nop.meta.checkpoint-scheduler.register-failed: checkpointId={}", cp.getCheckpointId(), e);
            }
        }
        LOG.info("nop.meta.checkpoint-scheduler.init-done: activeCheckpoints={} registered={}", active.size(), registered);
    }

    /**
     * 运行时增量注册（D4）：检查点 save/enable 后调用。加载检查点 → ACTIVE + 非空合法 cron → addJob(allowUpdate=true)；
     * 否则（非 ACTIVE / 空 cron）→ removeJob（清理可能存在的旧 job）。
     */
    public void registerCheckpoint(String checkpointId) {
        if (scheduler == null) {
            return;
        }
        IEntityDao<NopMetaQualityCheckpoint> dao = daoProvider.daoFor(NopMetaQualityCheckpoint.class);
        NopMetaQualityCheckpoint cp = dao.getEntityById(checkpointId);
        if (cp == null) {
            // 检查点已被删除——清理可能残留的 job（不静默，记录）
            scheduler.removeJob(jobName(checkpointId));
            return;
        }
        try {
            doRegister(cp);
        } catch (Exception e) {
            LOG.error("nop.meta.checkpoint-scheduler.register-failed: checkpointId={}", checkpointId, e);
        }
    }

    /**
     * 运行时增量移除（D4）：检查点 disable/delete 前调用，移除其定时 job。
     */
    public void unregisterCheckpoint(String checkpointId) {
        if (scheduler == null) {
            return;
        }
        scheduler.removeJob(jobName(checkpointId));
    }

    /**
     * beanMethod 调用入口（D3 path b）：经 {@code BeanMethodJobInvoker} 反射调用，复用既有
     * {@code executeCheckpoint} 编排链。接收 {@code Map}（规避 R2 形参名反射依赖）。
     *
     * <p>失败路径显式化：未知 checkpointId / status 非 ACTIVE 由 {@code executeCheckpoint} / executor
     * 抛 inline ErrorCode，经 invoker 转 {@code JobFireResult.ERROR}（不静默吞掉）。
     *
     * @param params jobParams（移除 beanName/methodName 后）：{@code {checkpointId: <id>}}
     * @return {@code executeCheckpoint} 的执行摘要 Map
     */
    public CheckpointExecutionResultDTO executeScheduledCheckpoint(Map<String, Object> params) {
        Object cpId = params.get(PARAM_CHECKPOINT_ID);
        if (cpId == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_SCHEDULER_INVALID_CRON)
                    .param("checkpointId", "<null>")
                    .param("cron", "<n/a>");
        }
        String checkpointId = String.valueOf(cpId);
        // null context 安全：computeQualityScore 内部从不解引用 context（架构基线 §2.7.3.1 D3 R2 核实）
        return checkpointBizModel.executeCheckpoint(checkpointId, null, null);
    }

    // ============================================================
    // helpers
    // ============================================================

    /** jobName 约定：前缀 + checkpointId。 */
    public static String jobName(String checkpointId) {
        return JOB_NAME_PREFIX + checkpointId;
    }

    /**
     * 注册单个检查点的定时 job（若 ACTIVE + 非空合法 cron）。
     *
     * @return true 表示已注册；false 表示跳过（非 ACTIVE / 空 cron）
     */
    private boolean doRegister(NopMetaQualityCheckpoint cp) {
        String checkpointId = cp.getCheckpointId();
        String cron = readScheduleCron(cp);
        if (cron == null || cron.trim().isEmpty()) {
            // 无 cron 配置——跳过（若曾有 job 则清理）
            scheduler.removeJob(jobName(checkpointId));
            return false;
        }
        if (!_NopMetadataCoreConstants.CHECKPOINT_STATUS_ACTIVE.equals(cp.getStatus())) {
            // 非 ACTIVE——跳过并清理（D4）
            scheduler.removeJob(jobName(checkpointId));
            return false;
        }
        JobSpec spec = buildJobSpec(checkpointId, cron, cp.getDisplayName());
        // addJob 内部 buildTrigger 可能因非法 cron 抛异常——由调用方 catch（D4 容错）
        scheduler.addJob(spec, true);
        LOG.info("nop.meta.checkpoint-scheduler.registered: checkpointId={} cron={}", checkpointId, cron);
        return true;
    }

    /**
     * 解析 {@code extConfig.schedule}（cron 表达式）。extConfig 缺失 / 非 JSON Map / 无 schedule 键 / 非字符串 → null。
     */
    @SuppressWarnings("unchecked")
    private static String readScheduleCron(NopMetaQualityCheckpoint cp) {
        String json = cp.getExtConfig();
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            Object parsed = JsonTool.parse(json);
            if (!(parsed instanceof Map)) {
                return null;
            }
            Object val = ((Map<String, Object>) parsed).get(EXT_CONFIG_SCHEDULE_KEY);
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            // extConfig 不可解析 → 视为无 schedule（不静默伪造）
            LOG.warn("nop.meta.checkpoint-scheduler.ext-config-unparseable: checkpointId={}", cp.getCheckpointId(), e);
            return null;
        }
    }

    /**
     * 构建 {@link JobSpec}（jobInvoker=beanMethod，bean=本 bean，method=包装方法，params={checkpointId}）。
     * jobParams 同时含 beanName/methodName（BeanMethodJobInvoker 约定）+ checkpointId（业务参数）。
     */
    private static JobSpec buildJobSpec(String checkpointId, String cron, String displayName) {
        JobSpec spec = new JobSpec();
        spec.setJobName(jobName(checkpointId));
        spec.setDisplayName(displayName != null ? displayName : JOB_NAME_PREFIX + checkpointId);
        spec.setJobGroup("nop-metadata");
        spec.setJobInvoker("beanMethod");

        Map<String, Object> jobParams = new HashMap<>();
        jobParams.put(PARAM_BEAN_NAME, BEAN_NAME);
        jobParams.put(PARAM_METHOD_NAME, SCHEDULED_METHOD_NAME);
        jobParams.put(PARAM_CHECKPOINT_ID, checkpointId);
        spec.setJobParams(jobParams);

        TriggerSpec trigger = new TriggerSpec();
        trigger.setCronExpr(cron);
        spec.setTriggerSpec(trigger);
        return spec;
    }

    /** 测试辅助：返回当前注册的 job 名集合（供测试断言 scanner 注册成功）。 */
    public List<String> getRegisteredJobNames() {
        if (scheduler == null) {
            return Collections.emptyList();
        }
        return scheduler.getJobNames();
    }

    /** 测试辅助：暴露调度器实例（测试经 {@link IJobScheduler#fireNow} 同步触发，D5）。 */
    @Nullable
    public IJobScheduler getScheduler() {
        return scheduler;
    }
}
