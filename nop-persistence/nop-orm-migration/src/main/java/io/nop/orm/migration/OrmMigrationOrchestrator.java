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

import static io.nop.orm.migration.MigrationErrors.ARG_GATE;
import static io.nop.orm.migration.MigrationErrors.ARG_JOB_ID;
import static io.nop.orm.migration.MigrationErrors.ARG_PHASE;
import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ARG_TARGET_TABLE;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_GATE_NOT_SATISFIED;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_JOB_NOT_FOUND;
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
 */
public class OrmMigrationOrchestrator {
    static final Logger LOG = LoggerFactory.getLogger(OrmMigrationOrchestrator.class);

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
     * 宿主批断点存储 SPI（避免对本模块未见的 nop-batch-dao 编译依赖）。
     */
    public interface IBatchStateStoreProvider {
        io.nop.batch.core.IBatchStateStore getStore();
    }

    private MigrationJobStateStore store() {
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
     */
    public List<IncrementalJobManager.RunningChainJob> runIncremental(String jobId) {
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
        state.setPhase(MigrationPhase.INCREMENTAL);
        store().update(state);

        IncrementalJobManager manager = incrementalManager();
        List<IncrementalJobManager.RunningChainJob> jobs = manager.startFallbackChains(jobId, config,
                SourcePosition.fromJson(state.getPositionP0()));
        LOG.info("nop.orm.migration.incremental-started:jobId={},chains={}", jobId, jobs.size());
        return jobs;
    }

    /**
     * INCREMENTAL → VERIFY 门禁：写停止关关闭 + lag=0（m6 两观测量静默期同时满足）。
     * 通过后推进到 VERIFY 并执行三级校验；校验结果记入状态行。
     */
    public ConsistencyVerifier.VerifyReport runVerify(String jobId) {
        MigrationJobState state = requireState(jobId);
        state.getPhase().checkTransition(jobId, MigrationPhase.VERIFY);

        // 门禁 1（M3）：写停止关必须已关闭——lag=0 与校验资格都以"源端无新写"为前提
        if (!OrmMigrationWriteGate.isClosed()) {
            throw gateFailed(jobId, state.getPhase(), "write-gate must be closed before VERIFY"
                    + " (set nop.orm.migration.write-gate=closed)");
        }
        // 门禁 2（m6）：lag=0（无增量阶段时仅观测 ledger 静止）
        String connectorName = config.isIncrementalEnabled() ? config.getIncrementalConnectorName() : null;
        IncrementalLagProbe probe = new IncrementalLagProbe(targetJdbcTemplate, stateQuerySpace,
                ledgerTableName);
        if (!probe.awaitLagZero(connectorName, config.getQuiesceSeconds())) {
            throw gateFailed(jobId, state.getPhase(),
                    "lag != 0 within quiesce window (offset advanced or ledger committed)");
        }

        state.setPhase(MigrationPhase.VERIFY);
        store().update(state);

        ConsistencyVerifier.VerifyReport report;
        try {
            report = verifier().verify(config);
        } catch (Exception e) {
            // 校验失配结论必须落状态行（设计 §4.6：失配明细不允许被"总体一致"掩盖）——
            // confirm 门禁读 verifyResult 判定
            state.setVerifyResult("failed");
            store().update(state);
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            RuntimeException re = NopException.adapt(e);
            if (re instanceof NopException)
                ((NopException) re).param(ARG_REASON, "verify failed: " + e.getMessage());
            throw re;
        }
        state.setVerifyResult(report.isPassed() ? "passed" : "failed");
        store().update(state);
        LOG.info("nop.orm.migration.verify-done:jobId={},passed={},mismatch={},details={}",
                jobId, report.isPassed(), report.mismatchCount, report.mismatchDetails.size());
        return report;
    }

    /**
     * SWITCH 的显式确认门（M2 收敛门 v1 形态 = 运维在编排器门禁处人工确认）：
     * VERIFY 已通过才允许。确认后执行配置翻转 + 路由重建，状态行推进 SWITCH → 等待开门。
     */
    public void confirmSwitch(String jobId) {
        MigrationJobState state = requireState(jobId);
        if (!"passed".equals(state.getVerifyResult())) {
            throw gateFailed(jobId, MigrationPhase.SWITCH,
                    "VERIFY has not passed (verifyResult=" + state.getVerifyResult() + ")");
        }
        if (!OrmMigrationWriteGate.isClosed()) {
            throw gateFailed(jobId, MigrationPhase.SWITCH, "write-gate must stay closed during SWITCH");
        }
        state.getPhase().checkTransition(jobId, MigrationPhase.SWITCH);
        state.setPhase(MigrationPhase.SWITCH);
        store().update(state);

        // 配置翻转（单一配置点，原子生效）：nop.orm.shard 整体替换 / relocate 翻 querySpace 指向
        flipShardConfig(state);
        rebuildRouting(state);
        LOG.info("nop.orm.migration.switch-flipped:jobId={},routeRebuild={}", jobId, state.getRouteRebuild());
    }

    /**
     * 收敛门满足（全部实例路由重建完成）后开启写门 → 恢复写 → DONE。【不可回滚点 = 此处】。
     */
    public String completeSwitch(String jobId) {
        MigrationJobState state = requireState(jobId);
        if (state.getPhase() != MigrationPhase.SWITCH) {
            throw gateFailed(jobId, MigrationPhase.DONE, "SWITCH phase required (current="
                    + state.getPhase() + ")");
        }
        OrmMigrationWriteGate.setClosed(false);
        state.setPhase(MigrationPhase.DONE);
        store().update(state);
        LOG.info("nop.orm.migration.done:jobId={}", jobId);
        return jobId;
    }

    /**
     * ABORT：全程可达（PREPARE/FULL/INCREMENTAL/VERIFY）。旧配置未动，恢复写即回滚——
     * 写门保持 closed 期间数据面冻结，开门即回到旧策略无损状态。
     */
    public void abort(String jobId, String reason) {
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
        state.setPhase(MigrationPhase.ABORT);
        store().update(state);
        LOG.info("nop.orm.migration.abort:jobId={},reason={}", jobId, reason);
    }

    public MigrationJobState getState(String jobId) {
        return requireState(jobId);
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
        store().update(state);
        LOG.info("nop.orm.migration.prepared:jobId={},targetTables={},p0={}", jobId,
                mapping().getTargetTables().size(), state.getPositionP0() != null ? "pinned" : "n/a");
        return jobId;
    }

    private String executeFull(MigrationJobState state) {
        String jobId = state.getJobId();
        state.setPhase(MigrationPhase.FULL);
        String taskName = config.getBatchTaskName();
        state.setFullTaskName(taskName);
        store().update(state);

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
        // （getShardSelector 每查询即时读取，替换后新查询立即生效）。非 SessionFactoryImpl 宿主
        // 按设计降级滚动重启，状态行标注 restart-required（待重启实例语义可见）。
        try {
            String shardConfig = DaoConfigs.CFG_ORM_SHARD.get();
            if (sessionFactory instanceof SessionFactoryImpl) {
                if (shardConfig == null || shardConfig.isEmpty()) {
                    ((SessionFactoryImpl) sessionFactory).setShardSelector(EmptyShardSelector.INSTANCE);
                } else {
                    ConfigShardSelector selector = new ConfigShardSelector();
                    selector.init();
                    ((SessionFactoryImpl) sessionFactory).setShardSelector(selector);
                }
                state.setRouteRebuild("rebuild-done");
            } else {
                // 非 SessionFactoryImpl 宿主：热重建不可行，按设计降级滚动重启，
                // 状态行标注待重启实例语义（收敛门 = 全部实例重建完成）
                state.setRouteRebuild("restart-required");
            }
        } catch (Exception e) {
            // 路由重建失败 = 收敛门不满足：保持写门冻结，fix-forward（修复后 confirm 重试）
            // 或回翻（shard 翻回旧配置 + 开门）。绝不带着半重建路由开门。
            state.setRouteRebuild("rebuild-failed:" + e.getMessage());
            store().update(state);
            throw gateFailed(state.getJobId(), MigrationPhase.SWITCH,
                    "route rebuild failed: " + e.getMessage());
        }
        store().update(state);
    }

    // ------------------------------------------------------------------
    // 惰性组件
    // ------------------------------------------------------------------

    public TargetShardMapping mapping() {
        if (mapping == null)
            mapping = TargetShardMapping.of(sessionFactory, config);
        return mapping;
    }

    public FullCopyBatchJob fullJob() {
        if (fullJob == null)
            fullJob = new FullCopyBatchJob(jdbcTemplate, mapping(), config, pkColumns());
        return fullJob;
    }

    public IncrementalJobManager incrementalManager() {
        if (incrementalManager == null) {
            IJdbcTemplate sinkTemplate = targetJdbcTemplate;
            incrementalManager = new IncrementalJobManager(mapping(), target -> {
                JdbcTwoPhaseCommitSinkBuilderHolder holder = new JdbcTwoPhaseCommitSinkBuilderHolder(
                        sinkTemplate, target, ledgerTableName, pkColumns());
                return holder.build();
            }, checkpointIntervalMs);
        }
        return incrementalManager;
    }

    public ConsistencyVerifier verifier() {
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
