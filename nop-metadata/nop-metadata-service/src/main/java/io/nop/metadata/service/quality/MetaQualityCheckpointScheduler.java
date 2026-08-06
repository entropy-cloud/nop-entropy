
package io.nop.metadata.service.quality;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.job.api.IJobScheduler;
import io.nop.job.api.JobDetail;
import io.nop.job.api.spec.JobSpec;
import io.nop.job.api.spec.TriggerSpec;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.api.dto.CheckpointExecutionResultDTO;
import io.nop.metadata.api.dto.CheckpointExtConfig;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.entity.NopMetaQualityCheckpointBizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.NopMetadataHelper;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 *   <li>未知 checkpointId（cron 触发时检查点已被删除）/ status 非 ACTIVE / 空规则集 / 不支持动作 /
 *       规则目标表缺失 → {@code executeCheckpoint} 抛对应 inline ErrorCode，{@link #executeScheduledCheckpoint}
 *       捕获后记 ERROR 并返回带错误信息的正常结果（MA7.5-01：不向外抛——invoker 会把异常转
 *       {@code JobFireResult.ERROR}，LocalJobScheduler 将 job 永久置 FAILED 且修复配置无法复活）</li>
 *   <li>空/非法 cron → scanner 注册期 catch 显式跳过并记录（D4 容错，不静默、不抛崩）；运行时 cron 被改为
 *       非法值导致 {@code addJob} 失败 → 移除旧 job 防过期调度残留（MA7.5-03）</li>
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
     * <p>失败路径显式化：未知 checkpointId / status 非 ACTIVE / 空规则集等 checkpoint 级业务错误由
     * {@code executeCheckpoint} / executor 抛 inline ErrorCode，本方法捕获后记 ERROR 日志并返回
     * 带错误信息的正常结果（MA7.5-01）——若让异常传播到 invoker，会转 {@code JobFireResult.ERROR} 使
     * LocalJobScheduler 将 job 永久置 FAILED（修复配置也无法复活，仅重启 JVM 可恢复）。
     *
     * @param params jobParams（移除 beanName/methodName 后）：{@code {checkpointId: <id>}}
     * @return {@code executeCheckpoint} 的执行摘要 Map；checkpoint 级错误时为带 executionErrors 的摘要
     */
    public CheckpointExecutionResultDTO executeScheduledCheckpoint(Map<String, Object> params) {
        String checkpointId = null;
        try {
            // AR-12：缺失 checkpointId（遗留/损坏 job 参数）在 try 边界内显式失败——
            // 若让异常逃逸到 invoker 会转 JobFireResult.ERROR 使 job 永久 FAILED（MA7.5-01 要消除的模式）。
            Object cpId = params != null ? params.get(PARAM_CHECKPOINT_ID) : null;
            if (cpId == null) {
                throw new NopMetadataException(NopMetadataErrors.ERR_CHECKPOINT_MISSING_ID);
            }
            checkpointId = String.valueOf(cpId);
            // null context 安全：computeQualityScore 内部从不解引用 context（架构基线 §2.7.3.1 D3 R2 核实）
            return checkpointBizModel.executeCheckpoint(checkpointId, null, null);
        } catch (Exception e) {
            // MA7.5-01：checkpoint 级执行错误不向外抛——否则 invoker 转 JobFireResult.ERROR 后
            // LocalJobScheduler 将 job 永久置 FAILED（addJob(allowUpdate=true) 仅对 WAITING/SUSPENDED
            // 重排程，FAILED 不复活，唯一恢复手段是重启 JVM）。记录日志并返回正常结果，job 存活按 cron
            // 继续触发；配置修复后下一次触发即恢复。
            if (isConcurrentRunRejection(e)) {
                // R4.3（Minor-8）：cron tick 与手动执行并发被运行标记 fail-fast 拒绝——预期运维噪音，降级 WARN
                // （区别于真实故障的 ERROR，避免 MA7.5-01 catch-all 转 ERROR 造成运维误读）
                LOG.warn("nop.meta.checkpoint-scheduler.scheduled-exec-skipped: checkpointId={} "
                        + "(already running, concurrent execution rejected fail-fast)", checkpointId);
            } else {
                LOG.error("nop.meta.checkpoint-scheduler.scheduled-exec-failed: checkpointId={} error={}",
                        checkpointId, NopMetadataHelper.toErrorMessage(e), e);
            }
            return buildErrorResult(checkpointId, e);
        }
    }

    /** R4.3：运行期并发重复触发（ERR_CHECKPOINT_ALREADY_RUNNING）判定——仅该错误码降级 WARN。 */
    private static boolean isConcurrentRunRejection(Exception e) {
        return e instanceof NopException
                && NopMetadataErrors.ERR_CHECKPOINT_ALREADY_RUNNING.getErrorCode()
                .equals(((NopException) e).getErrorCode());
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 构建 checkpoint 级错误的结果 DTO（executionErrors 记录错误，job 存活不抛异常，MA7.5-01）。 */
    private static CheckpointExecutionResultDTO buildErrorResult(String checkpointId, Exception e) {
        CheckpointExecutionResultDTO dto = new CheckpointExecutionResultDTO();
        dto.setCheckpointId(checkpointId);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("source", "scheduler");
        err.put("error", NopMetadataHelper.toErrorMessage(e));
        dto.getExecutionErrors().add(err);
        return dto;
    }

    /** 读取调度器中该检查点当前注册 job 的 cron（addJob 失败时的诊断用；无 job / 读失败返回 null）。 */
    private String readRegisteredCron(String checkpointId) {
        try {
            JobDetail detail = scheduler.getJobDetail(jobName(checkpointId));
            return detail != null && detail.getTriggerSpec() != null
                    ? detail.getTriggerSpec().getCronExpr() : null;
        } catch (Exception e) {
            // scheduler 查询失败 → null（诊断用，不影响主流程），但留 WARN 根因
            LOG.warn("nop.meta.checkpoint-scheduler.read-registered-cron-failed: checkpointId={}",
                    checkpointId, e);
            return null;
        }
    }

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
        try {
            // addJob 内部 buildTrigger 可能因非法 cron 抛异常（如 ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL）
            scheduler.addJob(spec, true);
        } catch (Exception e) {
            // MA7.5-03：addJob 失败（如 cron 被运维改为非法值）时，旧 job（旧 cron）仍留在调度器继续触发，
            // 检查点会按过期时间表运行（可能凌晨误跑）且运维误以为已停用。清理残留 job；
            // removeJob 自身失败不掩盖 addJob 失败原因。
            LOG.error("nop.meta.checkpoint-scheduler.add-job-failed: checkpointId={} oldCron={} newCron={}",
                    checkpointId, readRegisteredCron(checkpointId), cron, e);
            try {
                scheduler.removeJob(jobName(checkpointId));
            } catch (Exception re) {
                LOG.error("nop.meta.checkpoint-scheduler.remove-stale-job-failed: checkpointId={}", checkpointId, re);
            }
            return false;
        }
        LOG.info("nop.meta.checkpoint-scheduler.registered: checkpointId={} cron={}", checkpointId, cron);
        return true;
    }

    /**
     * 解析 {@code extConfig.schedule}（cron 表达式）。extConfig 缺失 / 非 JSON Map / 无 schedule 键 / 非字符串 → null。
     *
     * <p>R2.12（P2-MA4-101）：消费方改用强类型 {@link CheckpointExtConfig}（parseBeanFromText），消除死 DTO。
     */
    private static String readScheduleCron(NopMetaQualityCheckpoint cp) {
        String json = cp.getExtConfig();
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            CheckpointExtConfig config = JsonTool.parseBeanFromText(json, CheckpointExtConfig.class);
            return config == null ? null : config.getSchedule();
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
