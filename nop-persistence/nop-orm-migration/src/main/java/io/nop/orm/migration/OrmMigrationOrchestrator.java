/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.dao.DaoConfigs;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.EmptyShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.message.debezium.ChangeEvent;
import io.nop.orm.IOrmSessionFactory;
import io.nop.orm.OrmMigrationWriteGate;
import io.nop.orm.factory.SessionFactoryImpl;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.orm.migration.MigrationErrors.ARG_ACTUAL_PHASE;
import static io.nop.orm.migration.MigrationErrors.ARG_EXPECTED_PHASE;
import static io.nop.orm.migration.MigrationErrors.ARG_GATE;
import static io.nop.orm.migration.MigrationErrors.ARG_JOB_ID;
import static io.nop.orm.migration.MigrationErrors.ARG_PHASE;
import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ARG_TARGET_TABLE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_GATE_NOT_SATISFIED;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_JOB_NOT_FOUND;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_JOB_STATE_FENCING;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_TARGET_TABLE_NOT_READY;

/**
 * 数据搬迁编排器（WI9，设计 §4.1 作业模型）：五状态机 + 执行面委托。
 * 编排器只做阶段状态迁移与门禁判定，不碰数据面——FULL 委托 nop-batch，INCREMENTAL 委托
 * nop-stream，校验/切换是编排器自带门禁步骤。
 *
 * <p>状态迁移只能由本编排器推进，且每步迁移前检查前置门禁；重启后按状态行从最后完成的阶段
 * 继续，绝不隐式回退阶段（状态行经 {@link MigrationJobStateStore} 持久化）。切换协议
 * （§4.3）：VERIFY 通过 → 编排 API 显式 confirm（v1 人工收敛门）→ {@code nop.orm.shard}
 * 原子翻转 + 路由重建 → 开启写门 → DONE；ABORT 全程可达（旧配置未动无损）。写门冻结期
 * fix-forward 与回翻均无损（不可回滚点 = 恢复写收敛门满足之后）。</p>
 *
 * <p><b>并发契约（plan 2518）</b>：</p>
 * <ul>
 * <li>裁定④（C6-003，与 MA-03 同变更集）：链句柄构造期发布 + 有界 join + interrupt + 退出确认
 *     复核（未确认退出即 gateFailed 拒绝重入）；join null 显式留痕。</li>
 * <li>裁定⑤（C7-003）：状态行 UPDATE 期望相位谓词（updated==0 经二次 load 消歧类型化失败，
 *     跨进程 fencing）+ 编排 API per-jobId 进程内单飞（putIfAbsent fail-fast）+
     * incrementalManager 等惰性访问器 synchronized double-check。</li>
 * <li>裁定⑥（C7-001，硬前置裁定⑨）：runVerify 前置消费链 failure（死链即 fail 门禁）+
 *     manager==null 重启路径类型化拒绝重入。</li>
 * <li>裁定⑨（C3-008）：connector 名派生单点（{@link IncrementalJobManager#deriveConnectorNames}），
 *     runVerify 按链句柄携带的实际名逐链聚合探测。</li>
 * <li>裁定⑦（C3-001）：startFallbackChains 部分启动失败 → 已启动链回收 + 相位后翻 ABORT。</li>
 * <li>裁定⑪（C7-002）：失败相位白名单受控自环（INCREMENTAL/VERIFY），仅经既有编排 API 显式
 *     重入 + 进程内重试计数上界。</li>
 * </ul>
 */
public class OrmMigrationOrchestrator {
    static final Logger LOG = LoggerFactory.getLogger(OrmMigrationOrchestrator.class);

    /** 裁定⑪：fix-forward 受控自环重试计数上界（进程内缺省常量，零新配置键）。 */
    static final int MAX_FIX_FORWARD_RETRIES = 3;

    private final MigrationJobConfig config;
    private final IJdbcTemplate jdbcTemplate;
    /** 编排状态行落点 querySpace（缺省 default querySpace）。 */
    private final String stateQuerySpace;
    private final IOrmSessionFactory sessionFactory;
    /** 目标端 JDBC 面（relocate 指向新机房数据源；缺省与源同模板，由 SQL querySpace 路由）。 */
    private final IJdbcTemplate targetJdbcTemplate;
    private final String ledgerTableName;

    private MigrationJobStateStore stateStore;
    private TargetShardMapping mapping;
    private FullCopyBatchJob fullJob;
    private IncrementalJobManager incrementalManager;
    private ConsistencyVerifier verifier;
    private SourcePositionPinner pinner = new SourcePositionPinner();
    private EncryptKeyConsistencyChecker keyChecker = new EncryptKeyConsistencyChecker();
    /** PREPARE 密钥一致门禁的目标侧覆盖（relocate 跨环境显式声明）。 */
    private Map<String, String> targetKeyOverrides;
    private IBatchStateStoreProvider batchStateStoreProvider;
    /** 增量链作业 checkpoint 间隔（毫秒）。 */
    private long checkpointIntervalMs = 30_000L;
    /** 裁定④：单链停止 join 上界（setter 配置化，缺省常量 10s，零新 @cfg: 键）。 */
    private volatile long chainStopJoinTimeoutMs = IncrementalJobManager.DEFAULT_CHAIN_STOP_JOIN_TIMEOUT_MS;

    /** 裁定⑤：编排 API per-jobId 进程内单飞域（putIfAbsent fail-fast，败者类型化拒绝）。 */
    private final ConcurrentHashMap<String, AtomicReference<Boolean>> inFlightJobs =
            new ConcurrentHashMap<>();
    /** 裁定⑪：fix-forward 受控自环重试计数（进程内承载；跨重启恢复走 resume 非自环语义）。 */
    private final ConcurrentHashMap<String, AtomicInteger> fixForwardRetries = new ConcurrentHashMap<>();

    public OrmMigrationOrchestrator(MigrationJobConfig config, IJdbcTemplate jdbcTemplate,
                                    IOrmSessionFactory sessionFactory) {
        this(config, jdbcTemplate, sessionFactory, jdbcTemplate, "");
    }

    public OrmMigrationOrchestrator(MigrationJobConfig config, IJdbcTemplate jdbcTemplate,
                                    IOrmSessionFactory sessionFactory, IJdbcTemplate targetJdbcTemplate,
                                    String stateQuerySpace) {
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
        this.sessionFactory = sessionFactory;
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.stateQuerySpace = stateQuerySpace;
        this.ledgerTableName = "stream_epoch_ledger_v2";
    }

    public void setStateStore(MigrationJobStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void setPositionProvider(SourcePositionPinner.PositionProvider provider) {
        this.pinner.setPositionProvider(provider);
    }

    public void setTargetKeyOverrides(Map<String, String> targetKeyOverrides) {
        this.targetKeyOverrides = targetKeyOverrides;
    }

    /**
     * 宿主批断点存储注入口（nop-batch-dao 在 classpath 时提供 {@code DaoBatchStateStore}）。
     */
    public void setBatchStateStoreProvider(IBatchStateStoreProvider provider) {
        this.batchStateStoreProvider = provider;
    }

    public void setCheckpointIntervalMs(long checkpointIntervalMs) {
        this.checkpointIntervalMs = checkpointIntervalMs;
    }

    /**
     * 裁定④：单链停止 join 上界注入（stopAll 汇聚等待上界配置化；零新 @cfg: 配置键）。
     */
    public void setChainStopJoinTimeoutMs(long chainStopJoinTimeoutMs) {
        this.chainStopJoinTimeoutMs = chainStopJoinTimeoutMs;
        IncrementalJobManager manager = this.incrementalManager;
        if (manager != null)
            manager.setChainStopJoinTimeoutMs(chainStopJoinTimeoutMs);
    }

    /**
     * 注入增量链管理器（宿主共享装配或测试观测用；缺省由 {@link #incrementalManager()} 惰性构造）。
     */
    public void setIncrementalManager(IncrementalJobManager incrementalManager) {
        this.incrementalManager = incrementalManager;
        incrementalManager.setChainStopJoinTimeoutMs(chainStopJoinTimeoutMs);
    }

    /**
     * 宿主批断点存储 SPI（避免对本模块未见的 nop-batch-dao 编译依赖）。
     */
    public interface IBatchStateStoreProvider {
        io.nop.batch.core.IBatchStateStore getStore();
    }

    private synchronized MigrationJobStateStore store() {
        if (stateStore == null)
            stateStore = new MigrationJobStateStore(jdbcTemplate, stateQuerySpace);
        return stateStore;
    }

    // ------------------------------------------------------------------
    // 编排 API（阶段推进 + 显式确认门）
    // ------------------------------------------------------------------

    /**
     * 发起作业：创建状态行并执行 PREPARE（目标表就绪校验 + 密钥一致门禁 + P0 钉定）。
     * PREPARE 校验幂等；P0 重复执行取新值（设计 §4.1）。
     *
     * @return jobId（调用方指定或缺省按实体+时间派生）
     */
    public String start(String jobId) {
        MigrationJobState state = new MigrationJobState();
        state.setJobId(jobId);
        state.setPhase(MigrationPhase.PREPARE);
        state.setScenario(config.getScenario());
        state.setEntityName(config.getEntityName());
        state.setCreateTimeMillis(CoreMetrics.currentTimeMillis());
        state.setUpdateTimeMillis(state.getCreateTimeMillis());
        store().insert(state);
        return executePrepare(state);
    }

    /**
     * 重启恢复：按状态行从最后完成阶段继续（绝不隐式回退）。终态作业原样返回。
     */
    public String resume(String jobId) {
        return inSingleFlight(jobId, () -> {
            MigrationJobState state = requireState(jobId);
            LOG.info("nop.orm.migration.resume:jobId={},phase={}", jobId, state.getPhase());
            switch (state.getPhase()) {
                case PREPARE:
                    return executePrepare(state);
                case FULL:
                    // FULL 可重入：keyed upsert 幂等 + batch 断点（宿主注入 stateStore 时）
                    return executeFull(state);
                case INCREMENTAL:
                case VERIFY:
                case SWITCH:
                case DONE:
                case ABORT:
                    // 非终态阶段恢复 = 状态行已在，门禁与执行动作由对应编排 API 续推
                    return jobId;
                default:
                    throw new IllegalStateException("unknown phase: " + state.getPhase());
            }
        });
    }

    /**
     * PREPARE → FULL：执行前置门禁（目标表就绪 + 密钥一致 + P0 已钉定[增量启用时]）后启动全量。
     * 全量同步执行（batch 作业天然出现在 nop-batch 任务管理面），完成后状态行进 FULL 完成态。
     */
    public String runFull(String jobId) {
        MigrationJobState state = requireState(jobId);
        state.getPhase().checkTransition(jobId, MigrationPhase.FULL);
        return executeFull(state);
    }

    /**
     * FULL → INCREMENTAL：启动增量链作业（首跑 offset 种子化为 P0）。
     *
     * <p>裁定⑪：INCREMENTAL 相位显式重入 = fix-forward 受控自环（重试计数上界 3；重入前先
     * stopIncrementalChains 回收旧链，旧链未确认退出即拒绝重入）。裁定⑦：启动中途失败 →
     * 已启动链由管理器回收 + 相位后翻 ABORT 显式落账。</p>
     */
    public List<IncrementalJobManager.RunningChainJob> runIncremental(String jobId) {
        return inSingleFlight(jobId, () -> doRunIncremental(jobId));
    }

    private List<IncrementalJobManager.RunningChainJob> doRunIncremental(String jobId) {
        MigrationJobState state = requireState(jobId);
        if (!config.isIncrementalEnabled()) {
            throw gateFailed(jobId, MigrationPhase.INCREMENTAL,
                    "incremental disabled in config (FULL->VERIFY path for non-CDC sources)");
        }
        state.getPhase().checkTransition(jobId, MigrationPhase.INCREMENTAL);
        if (state.getPositionP0() == null || state.getPositionP0().isEmpty()) {
            throw new NopException(MigrationErrors.ERR_MIG_P0_NOT_PINNED).param(ARG_JOB_ID, jobId)
                    .param(MigrationErrors.ARG_DETAIL, "P0 must be pinned before INCREMENTAL");
        }
        boolean selfLoopReentry = state.getPhase() == MigrationPhase.INCREMENTAL;
        if (selfLoopReentry) {
            // 裁定⑪：受控自环重试计数上界
            if (incrementRetryCount(jobId, MigrationPhase.INCREMENTAL) > MAX_FIX_FORWARD_RETRIES) {
                throw gateFailed(jobId, MigrationPhase.INCREMENTAL,
                        "nop.orm.migration.fix-forward-retry-exhausted: INCREMENTAL self-loop reentry"
                                + " exceeded the retry bound " + MAX_FIX_FORWARD_RETRIES);
            }
            // 裁定④⑪协同：重入前回收旧链；旧链未确认退出（半终止）即拒绝重入
            stopIncrementalChains(jobId);
            IncrementalJobManager manager = this.incrementalManager;
            if (manager != null) {
                for (IncrementalJobManager.RunningChainJob chain : manager.snapshotRunningJobs()) {
                    if (chain.isAlive() && !chain.isExitConfirmed()) {
                        throw gateFailed(jobId, MigrationPhase.INCREMENTAL,
                                "nop.orm.migration.chain-not-confirmed:" + chain.getJobName()
                                        + " (chain thread did not confirm exit after stop; refusing reentry"
                                        + " to prevent dual-write)");
                    }
                }
            }
        }
        MigrationPhase expectedPhase = state.getPhase();
        state.setPhase(MigrationPhase.INCREMENTAL);
        updateStateFenced(state, expectedPhase);

        IncrementalJobManager manager = incrementalManager();
        List<IncrementalJobManager.RunningChainJob> jobs;
        try {
            jobs = manager.startFallbackChains(jobId, config,
                    SourcePosition.fromJson(state.getPositionP0()));
        } catch (RuntimeException e) {
            // 裁定⑦：部分启动补偿——已启动链已由管理器回收；相位后翻 ABORT 显式落账
            // （沿 INCREMENTAL→ABORT 既有合法边，经状态行谓词写）
            state.setAbortReason("incremental-start-failed:" + e.getMessage());
            state.setPhase(MigrationPhase.ABORT);
            updateStateFenced(state, MigrationPhase.INCREMENTAL);
            throw e;
        }
        LOG.info("nop.orm.migration.incremental-started:jobId={},chains={}", jobId, jobs.size());
        return jobs;
    }

    /**
     * INCREMENTAL → VERIFY 门禁：写停止关关闭 + 死链 fail 门禁（裁定⑥）+ lag=0（m6 两观测量
     * 静默期同时满足，裁定⑨逐链聚合探测）。通过后推进到 VERIFY 并执行三级校验；校验结果记入状态行。
     *
     * <p>裁定⑪：VERIFY 相位显式重入 = fix-forward 受控自环（重试计数上界 3）。</p>
     */
    public ConsistencyVerifier.VerifyReport runVerify(String jobId) {
        return inSingleFlight(jobId, () -> doRunVerify(jobId));
    }

    private ConsistencyVerifier.VerifyReport doRunVerify(String jobId) {
        MigrationJobState state = requireState(jobId);
        state.getPhase().checkTransition(jobId, MigrationPhase.VERIFY);
        boolean selfLoopReentry = state.getPhase() == MigrationPhase.VERIFY;
        if (selfLoopReentry) {
            // 裁定⑪：受控自环重试计数上界
            if (incrementRetryCount(jobId, MigrationPhase.VERIFY) > MAX_FIX_FORWARD_RETRIES) {
                throw gateFailed(jobId, MigrationPhase.VERIFY,
                        "nop.orm.migration.fix-forward-retry-exhausted: VERIFY self-loop reentry"
                                + " exceeded the retry bound " + MAX_FIX_FORWARD_RETRIES);
            }
        }

        // 门禁 1（M3）：写停止关必须已关闭——lag=0 与校验资格都以"源端无新写"为前提
        if (!OrmMigrationWriteGate.isClosed()) {
            throw gateFailed(jobId, state.getPhase(), "write-gate must be closed before VERIFY"
                    + " (set nop.orm.migration.write-gate=closed)");
        }

        // 门禁 1.5（裁定⑥，硬前置裁定⑨已落地）：死链 fail 门禁——链 failure 非空即拒绝，
        // lag=0 观测量不得放行死亡链；manager==null 且 phase==INCREMENTAL = 重启路径
        // 「进程内句柄消亡 + lag 观测量平凡通过」，类型化拒绝重入（M2）
        IncrementalJobManager manager = this.incrementalManager;
        List<IncrementalJobManager.RunningChainJob> chains = null;
        if (config.isIncrementalEnabled()) {
            if (manager == null) {
                if (state.getPhase() == MigrationPhase.INCREMENTAL) {
                    throw gateFailed(jobId, state.getPhase(),
                            "incremental-manager-missing-restart-reentry: in-process chain handles died"
                                    + " with the process; resume or re-run runIncremental to rebuild chains"
                                    + " before VERIFY");
                }
                // 无增量形态（FULL→VERIFY 路径）manager 缺席 = 合法路径：显式留痕跳过（非静默）
                LOG.info("nop.orm.migration.chain-failure-gate:jobId={},skipped=no-incremental-manager",
                        jobId);
            } else {
                chains = manager.snapshotRunningJobs();
                for (IncrementalJobManager.RunningChainJob chain : chains) {
                    // 裁定④：链未确认退出（半终止）→ gateFailed 拒绝（防在死亡链上放行 VERIFY）
                    if (chain.isAlive() && !chain.isExitConfirmed()) {
                        throw gateFailed(jobId, state.getPhase(),
                                "nop.orm.migration.chain-not-confirmed:" + chain.getJobName()
                                        + " (chain thread did not confirm exit after stop)");
                    }
                    Throwable chainFailure = chain.getFailure();
                    if (chainFailure != null) {
                        throw gateFailed(jobId, state.getPhase(),
                                "incremental-chain-failed:" + chain.getJobName()
                                        + " (" + chainFailure + ")");
                    }
                }
                if (chains.isEmpty() && state.getPhase() == MigrationPhase.INCREMENTAL) {
                    throw gateFailed(jobId, state.getPhase(),
                            "incremental-chain-failed:none-running (INCREMENTAL phase with no live chains)");
                }
            }
        }

        // 门禁 2（m6 + 裁定⑨）：lag=0——按链句柄携带的实际 connector 名逐链聚合探测（单静默窗），
        // 不再直传原始 base 名（M>1 键错位使观测量恒空的结构性缺陷闭合）
        String connectorName = null; // 无增量阶段：仅观测 ledger 静止
        IncrementalLagProbe probe = new IncrementalLagProbe(targetJdbcTemplate, stateQuerySpace,
                ledgerTableName);
        boolean lagZero;
        if (config.isIncrementalEnabled() && chains != null && !chains.isEmpty()) {
            List<String> connectorNames = new ArrayList<>(chains.size());
            for (IncrementalJobManager.RunningChainJob chain : chains) {
                connectorNames.add(chain.getConnectorName());
            }
            lagZero = probe.awaitLagZeroChains(connectorNames, config.getQuiesceSeconds());
        } else {
            lagZero = probe.awaitLagZero(connectorName, config.getQuiesceSeconds());
        }
        if (!lagZero) {
            throw gateFailed(jobId, state.getPhase(),
                    "lag != 0 within quiesce window (offset advanced or ledger committed)");
        }
        // 门禁复核 1（plan 2507 裁定⑤ 2.5-C1-010）：lag 静默窗后复核写门仍关闭——
        // 门禁检查是瞬时读而冻结承诺贯穿 VERIFY 全程，窗口内翻转（含租约 fail-open）不得无感知
        requireGateStillClosed(jobId, state.getPhase(),
                "write-gate reopened after lag quiesce window (VERIFY precondition lost)");

        MigrationPhase expectedPhase = state.getPhase();
        state.setPhase(MigrationPhase.VERIFY);
        updateStateFenced(state, expectedPhase);

        ConsistencyVerifier.VerifyReport report;
        try {
            report = verifier().verify(config);
        } catch (Exception e) {
            // 校验失配结论必须落状态行（设计 §4.6：失配明细不允许被"总体一致"掩盖）——
            // confirm 门禁读 verifyResult 判定
            state.setVerifyResult("failed");
            updateStateFenced(state, state.getPhase());
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            RuntimeException re = NopException.adapt(e);
            if (re instanceof NopException)
                ((NopException) re).param(ARG_REASON, "verify failed: " + e.getMessage());
            throw re;
        }
        // 门禁复核 2（plan 2507 裁定⑤ 2.5-C1-010）：三级校验落账前复核写门仍关闭——
        // 校验窗口内门被翻开（外部翻转或租约 fail-open）时不得在漂移数据上记录 passed
        if (!OrmMigrationWriteGate.isClosed()) {
            state.setVerifyResult("failed");
            updateStateFenced(state, state.getPhase());
            throw gateFailed(jobId, state.getPhase(),
                    "write-gate reopened during VERIFY window (external flip or lease fail-open)");
        }
        state.setVerifyResult(report.isPassed() ? "passed" : "failed");
        updateStateFenced(state, state.getPhase());
        LOG.info("nop.orm.migration.verify-done:jobId={},passed={},mismatch={},details={}",
                jobId, report.isPassed(), report.mismatchCount, report.mismatchDetails.size());
        return report;
    }

    /**
     * SWITCH 的显式确认门（M2 收敛门 v1 形态 = 运维在编排器门禁处人工确认）：
     * VERIFY 已通过才允许。确认后执行配置翻转 + 路由重建，状态行推进 SWITCH → 等待开门。
     */
    public void confirmSwitch(String jobId) {
        inSingleFlight(jobId, () -> {
            doConfirmSwitch(jobId);
            return null;
        });
    }

    private void doConfirmSwitch(String jobId) {
        MigrationJobState state = requireState(jobId);
        if (!"passed".equals(state.getVerifyResult())) {
            throw gateFailed(jobId, MigrationPhase.SWITCH,
                    "VERIFY has not passed (verifyResult=" + state.getVerifyResult() + ")");
        }
        if (!OrmMigrationWriteGate.isClosed()) {
            throw gateFailed(jobId, MigrationPhase.SWITCH, "write-gate must stay closed during SWITCH");
        }
        state.getPhase().checkTransition(jobId, MigrationPhase.SWITCH);
        MigrationPhase expectedPhase = state.getPhase();
        state.setPhase(MigrationPhase.SWITCH);
        updateStateFenced(state, expectedPhase);

        // 配置翻转（单一配置点，原子生效）：nop.orm.shard 整体替换 / relocate 翻 querySpace 指向
        flipShardConfig(state);
        rebuildRouting(state);
        // 门禁复核（plan 2507 裁定⑤ 2.5-C1-010）：翻转+路由重建时长窗后复核写门仍关闭——
        // 窗口内门被翻开（外部翻转或租约 fail-open）时继续收敛 SWITCH 会掩盖双写窗，留痕后类型化失败
        if (!OrmMigrationWriteGate.isClosed()) {
            state.setRouteRebuild("gate-reopened-after-rebuild");
            updateStateFenced(state, state.getPhase());
            throw gateFailed(jobId, MigrationPhase.SWITCH,
                    "write-gate reopened during SWITCH window (external flip or lease fail-open)");
        }
        // SWITCH 收敛（翻转+路由重建完成）即停止增量链：此后源事件重放落入已晋级的线上
        // 目标表属于幽灵写（IncrementalJobManager.stopAll 契约：ABORT/SWITCH 收敛后由编排器调用）
        stopIncrementalChains(jobId);
        LOG.info("nop.orm.migration.switch-flipped:jobId={},routeRebuild={}", jobId, state.getRouteRebuild());
    }

    /**
     * 收敛门满足（全部实例路由重建完成）后开启写门 → 恢复写 → DONE。【不可回滚点 = 此处】。
     */
    public String completeSwitch(String jobId) {
        return inSingleFlight(jobId, () -> {
            MigrationJobState state = requireState(jobId);
            if (state.getPhase() != MigrationPhase.SWITCH) {
                throw gateFailed(jobId, MigrationPhase.DONE, "SWITCH phase required (current="
                        + state.getPhase() + ")");
            }
            // 开门前兜底停链（幂等）：覆盖中断重启后直接从 SWITCH 收敛到 DONE 的路径
            stopIncrementalChains(jobId);
            OrmMigrationWriteGate.setClosed(false);
            state.setPhase(MigrationPhase.DONE);
            updateStateFenced(state, MigrationPhase.SWITCH);
            LOG.info("nop.orm.migration.done:jobId={}", jobId);
            return jobId;
        });
    }

    /**
     * ABORT：全程可达（PREPARE/FULL/INCREMENTAL/VERIFY）。旧配置未动，恢复写即回滚——
     * 写门保持 closed 期间数据面冻结，开门即回到旧策略无损状态。
     */
    public void abort(String jobId, String reason) {
        inSingleFlight(jobId, () -> {
            doAbort(jobId, reason);
            return null;
        });
    }

    private void doAbort(String jobId, String reason) {
        MigrationJobState state = requireState(jobId);
        if (state.getPhase().isTerminal())
            return;
        state.getPhase().checkTransition(jobId, MigrationPhase.ABORT);
        if (config.isIncrementalEnabled()) {
            IncrementalJobManager manager = this.incrementalManager;
            if (manager != null)
                manager.stopAll();
        }
        // 写门若已关闭则恢复（ABORT 路径只发生在 SWITCH 翻转之前——配置未动，开门即回滚）
        if (OrmMigrationWriteGate.isClosed())
            OrmMigrationWriteGate.setClosed(false);
        state.setAbortReason(reason);
        MigrationPhase expectedPhase = state.getPhase();
        state.setPhase(MigrationPhase.ABORT);
        updateStateFenced(state, expectedPhase);
        LOG.info("nop.orm.migration.abort:jobId={},reason={}", jobId, reason);
    }

    public MigrationJobState getState(String jobId) {
        return requireState(jobId);
    }

    // ------------------------------------------------------------------
    // 裁定⑤：单飞域 + 状态行 fencing
    // ------------------------------------------------------------------

    /**
     * 编排 API per-jobId 进程内单飞（裁定⑤）：同 jobId 两入口并发时败者类型化拒绝
     * （禁排队等待——编排 API 为运维显式低频调用，排队掩盖误用）；跨 jobId 零互斥。
     * 锁序：单飞域内可嵌套 stopIncrementalChains（有界 join），禁嵌套取另一 jobId 单飞锁。
     */
    private <T> T inSingleFlight(String jobId, java.util.function.Supplier<T> action) {
        AtomicReference<Boolean> token = new AtomicReference<>(Boolean.TRUE);
        AtomicReference<Boolean> previous = inFlightJobs.putIfAbsent(jobId, token);
        if (previous != null) {
            MigrationJobState state = store().load(jobId);
            LOG.warn("nop.orm.migration.running-jobs-reentry:jobId={},phase={}",
                    jobId, state == null ? "-" : state.getPhase().name());
            throw gateFailed(jobId, state == null ? MigrationPhase.PREPARE : state.getPhase(),
                    "nop.orm.migration.running-jobs-reentry: another orchestration API call is active"
                            + " for this job (per-jobId single-flight)");
        }
        try {
            return action.get();
        } finally {
            inFlightJobs.remove(jobId, token);
        }
    }

    /** 裁定⑪：自环重试计数（进程内；进入非自环路径时清零由调用方隐式承载——计数仅自环递增）。 */
    private int incrementRetryCount(String jobId, MigrationPhase phase) {
        return fixForwardRetries
                .computeIfAbsent(jobId + "@" + phase.name(), key -> new AtomicInteger())
                .incrementAndGet();
    }

    /**
     * 状态行带谓词写 + updated==0 二次 load 消歧（裁定⑤，m4）：
     * 行缺失 → ERR_MIG_JOB_NOT_FOUND；行在且相位≠期望 → 跨进程 fencing 类型化拒绝；
     * 行在且相位==期望（理论不可达，防御分支）→ 显式重试一次后仍失败即抛。
     */
    private void updateStateFenced(MigrationJobState state, MigrationPhase expectedPhase) {
        long updated = store().update(state, expectedPhase);
        if (updated == 1)
            return;
        MigrationJobState current = store().load(state.getJobId());
        if (current == null)
            throw new NopException(ERR_MIG_JOB_NOT_FOUND).param(ARG_JOB_ID, state.getJobId());
        if (current.getPhase() != expectedPhase)
            throw new NopException(ERR_MIG_JOB_STATE_FENCING)
                    .param(ARG_JOB_ID, state.getJobId())
                    .param(ARG_EXPECTED_PHASE, expectedPhase.name())
                    .param(ARG_ACTUAL_PHASE, current.getPhase().name());
        updated = store().update(state, expectedPhase);
        if (updated != 1)
            throw new NopException(ERR_MIG_JOB_STATE_FENCING)
                    .param(ARG_JOB_ID, state.getJobId())
                    .param(ARG_EXPECTED_PHASE, expectedPhase.name())
                    .param(ARG_ACTUAL_PHASE, current.getPhase().name());
    }

    // ------------------------------------------------------------------
    // 阶段执行体
    // ------------------------------------------------------------------

    private String executePrepare(MigrationJobState state) {
        String jobId = state.getJobId();
        // 1. 目标表就绪校验（auto-upgrade 产物，管道不建表不改表）
        ensureTargetTablesReady(jobId);
        // 2. 密钥一致门禁（M4）
        keyChecker.check(config, targetKeyOverrides);
        // 3. P0 钉定（增量启用时；重复执行取新值）
        if (config.isIncrementalEnabled()) {
            SourcePosition p0 = pinP0(state);
            state.setPositionP0(p0.toJson());
            config.setIncrementalPositionP0(p0.toJson());
        }
        updateStateFenced(state, state.getPhase());
        LOG.info("nop.orm.migration.prepared:jobId={},targetTables={},p0={}", jobId,
                mapping().getTargetTables().size(), state.getPositionP0() != null ? "pinned" : "n/a");
        return jobId;
    }

    private String executeFull(MigrationJobState state) {
        String jobId = state.getJobId();
        MigrationPhase expectedPhase = state.getPhase();
        state.setPhase(MigrationPhase.FULL);
        String taskName = config.getBatchTaskName();
        state.setFullTaskName(taskName);
        updateStateFenced(state, expectedPhase);

        FullCopyBatchJob job = fullJob();
        if (batchStateStoreProvider != null)
            job.setStateStore(batchStateStoreProvider.getStore());
        long copied = job.run(jobId);
        LOG.info("nop.orm.migration.full-done:jobId={},taskName={},copied={}", jobId, taskName, copied);
        return jobId;
    }

    private SourcePosition pinP0(MigrationJobState state) {
        String connectorType = stringParam("connector-type");
        String sourceQuerySpace = config.getSourceQuerySpaces().isEmpty()
                ? "" : config.getSourceQuerySpaces().get(0);
        return pinner.pin(jdbcTemplate, sourceQuerySpace, connectorType);
    }

    /**
     * 停止增量链作业（成功收敛路径）。只停本进程已启动的链——直接读字段而非
     * {@link #incrementalManager()} 惰性构造：编排器重启后字段为空即链已随进程消亡，
     * 无须（也不得）为停止而新建管理器。
     */
    private void stopIncrementalChains(String jobId) {
        if (!config.isIncrementalEnabled())
            return;
        IncrementalJobManager manager = this.incrementalManager;
        if (manager == null)
            return;
        manager.stopAll();
        LOG.info("nop.orm.migration.incremental-stopped:jobId={}", jobId);
    }

    private void ensureTargetTablesReady(String jobId) {
        for (PhysicalTable target : mapping().getTargetTables()) {
            if (!targetTableExists(target)) {
                throw new NopException(ERR_MIG_TARGET_TABLE_NOT_READY)
                        .param(ARG_TARGET_TABLE, target.getTableName());
            }
        }
    }

    /**
     * 目标表存在性检查（JDBC 查询面，表不存在即抛错；auto-upgrade 建表在 PREPARE 之前完成——
     * 设计 §4.3：搬迁管道不建表、不改表）。
     */
    private boolean targetTableExists(PhysicalTable target) {
        try {
            targetJdbcTemplate.getTableMeta(target.getQuerySpace(), target.getTableName());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void flipShardConfig(MigrationJobState state) {
        if (config.isRescale()) {
            String newConfigText = config.getTargetShardConfigText();
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, newConfigText);
        } else {
            // relocate：数据源指向翻转归部署配置面（target-query-space-mapping 的语义载体是
            // 数据源注册表），编排器留痕；本步骤在单 JVM 内表现为 mapping 语义翻转完成
            LOG.info("nop.orm.migration.relocate-flip:jobId={},mapping={}", state.getJobId(),
                    config.getTargetQuerySpaceMapping());
        }
    }

    private void rebuildRouting(MigrationJobState state) {
        // 单 JVM 形态：以新配置 init 新 ConfigShardSelector 并热替换 session factory 的 selector
        // （shardSelector 为 volatile 读面，热替换经 volatile 写安全发布；替换后读回同一性确认——
        // plan 2507 裁定⑤ 2.5-C1-007：可见性失配转化为显式 gateFailed，保持写门冻结，
        // 绝不带失配路由开门）。非 SessionFactoryImpl 宿主按设计降级滚动重启，
        // 状态行标注 restart-required（待重启实例语义可见）。
        IShardSelector expected;
        try {
            String shardConfig = DaoConfigs.CFG_ORM_SHARD.get();
            if (!(sessionFactory instanceof SessionFactoryImpl)) {
                // 非 SessionFactoryImpl 宿主：热重建不可行，按设计降级滚动重启，
                // 状态行标注待重启实例语义（收敛门 = 全部实例重建完成）
                state.setRouteRebuild("restart-required");
                updateStateFenced(state, state.getPhase());
                return;
            }
            expected = buildRebuiltSelector(shardConfig);
        } catch (Exception e) {
            // 路由重建失败 = 收敛门不满足：保持写门冻结，fix-forward（修复后 confirm 重试）
            // 或回翻（shard 翻回旧配置 + 开门）。绝不带着半重建路由开门。
            state.setRouteRebuild("rebuild-failed:" + e.getMessage());
            updateStateFenced(state, state.getPhase());
            throw gateFailed(state.getJobId(), MigrationPhase.SWITCH,
                    "route rebuild failed: " + e.getMessage());
        }
        publishRebuiltSelector(state, (SessionFactoryImpl) sessionFactory, expected);
        state.setRouteRebuild("rebuild-done");
        updateStateFenced(state, state.getPhase());
    }

    /**
     * 构建重建后的 selector（新配置 init 新 ConfigShardSelector / 无配置回 EmptyShardSelector）。
     * 配置解析失败抛出（由 rebuild 包装为 rebuild-failed 留痕）。
     */
    private IShardSelector buildRebuiltSelector(String shardConfig) {
        if (shardConfig == null || shardConfig.isEmpty())
            return EmptyShardSelector.INSTANCE;
        ConfigShardSelector selector = new ConfigShardSelector();
        selector.init();
        return selector;
    }

    /**
     * 热替换发布 + 读回同一性确认（plan 2507 裁定⑤ 2.5-C1-007 采信最小形态）：
     * volatile 写后立即读回 {@code getShardSelector()} 比对——失配 = 发布可见性异常
     * （JMM 下不可观测缺陷的显式化），状态行留痕后类型化失败，保持写门冻结。
     * 留痕值须落在 route_rebuild VARCHAR(32) 容量内。
     */
    private void publishRebuiltSelector(MigrationJobState state, SessionFactoryImpl factory,
                                        IShardSelector expected) {
        factory.setShardSelector(expected);
        if (factory.getShardSelector() != expected) {
            state.setRouteRebuild("rebuild-failed:pub-mismatch");
            updateStateFenced(state, state.getPhase());
            throw gateFailed(state.getJobId(), MigrationPhase.SWITCH,
                    "shard selector publication mismatch: value written by setShardSelector"
                            + " not visible on read-back");
        }
    }

    // ------------------------------------------------------------------
    // 惰性组件（裁定⑤：synchronized double-check，C1-005/C1-009 吸收面）
    // ------------------------------------------------------------------

    public synchronized TargetShardMapping mapping() {
        if (mapping == null)
            mapping = TargetShardMapping.of(sessionFactory, config);
        return mapping;
    }

    public synchronized FullCopyBatchJob fullJob() {
        if (fullJob == null)
            fullJob = new FullCopyBatchJob(jdbcTemplate, mapping(), config, pkColumns());
        return fullJob;
    }

    public synchronized IncrementalJobManager incrementalManager() {
        if (incrementalManager == null) {
            IJdbcTemplate sinkTemplate = targetJdbcTemplate;
            incrementalManager = new IncrementalJobManager(mapping(), target -> {
                JdbcTwoPhaseCommitSinkBuilderHolder holder = new JdbcTwoPhaseCommitSinkBuilderHolder(
                        sinkTemplate, target, ledgerTableName, pkColumns());
                return holder.build();
            }, checkpointIntervalMs);
            incrementalManager.setChainStopJoinTimeoutMs(chainStopJoinTimeoutMs);
        }
        return incrementalManager;
    }

    public synchronized ConsistencyVerifier verifier() {
        if (verifier == null)
            verifier = new ConsistencyVerifier(jdbcTemplate, mapping(), pkColumns());
        return verifier;
    }

    private List<String> pkColumns() {
        List<String> cols = new ArrayList<>();
        sessionFactory.getOrmModel().requireEntityModel(config.getEntityName())
                .getPkColumns().forEach(col -> cols.add(col.getCode()));
        if (cols.isEmpty())
            throw new NopException(MigrationErrors.ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    "entity has no primary key: " + config.getEntityName());
        return cols;
    }

    private String stringParam(String key) {
        Object value = config.getIncrementalDebezium().get(key);
        return value == null ? null : value.toString();
    }

    private MigrationJobState requireState(String jobId) {
        MigrationJobState state = store().load(jobId);
        if (state == null)
            throw new NopException(ERR_MIG_JOB_NOT_FOUND).param(ARG_JOB_ID, jobId);
        return state;
    }

    /**
     * 写门窗口复核（plan 2507 裁定⑤ 2.5-C1-010）：复核失败 = 门在窗口内被翻开
     * （外部翻转或租约 fail-open，两者在 isClosed() 读面同型）——类型化失败，绝不静默继续。
     */
    private void requireGateStillClosed(String jobId, MigrationPhase phase, String gate) {
        if (!OrmMigrationWriteGate.isClosed())
            throw gateFailed(jobId, phase, gate);
    }

    private static NopException gateFailed(String jobId, MigrationPhase phase, String gate) {
        return new NopException(ERR_MIG_GATE_NOT_SATISFIED)
                .param(ARG_JOB_ID, jobId)
                .param(ARG_PHASE, phase.name())
                .param(ARG_GATE, gate);
    }

    /**
     * 增量 sink 分支构造（每分支固定一张目标物理表；UPSERT_DELETE + epoch ledger 幂等）。
     * 独立持有者类隔离 jdbc-2pc 依赖的构造细节。
     */
    private static final class JdbcTwoPhaseCommitSinkBuilderHolder {
        private final IJdbcTemplate template;
        private final PhysicalTable target;
        private final String ledgerTable;
        private final List<String> keyColumns;

        JdbcTwoPhaseCommitSinkBuilderHolder(IJdbcTemplate template, PhysicalTable target,
                                            String ledgerTable, List<String> keyColumns) {
            this.template = template;
            this.target = target;
            this.ledgerTable = ledgerTable;
            this.keyColumns = keyColumns;
        }

        io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink<ChangeEvent> build() {
            io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink<ChangeEvent> sink =
                    io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink.<ChangeEvent>builder()
                            .jdbcTemplate(template)
                            .querySpace(target.getQuerySpace())
                            .tableName(target.getTableName())
                            .ledgerTableName(ledgerTable)
                            .dmlMode(io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink.DmlMode.UPSERT_DELETE)
                            .keyColumns(keyColumns.toArray(new String[0]))
                            .build();
            sink.initializeLedgerTable();
            return sink;
        }
    }
}
