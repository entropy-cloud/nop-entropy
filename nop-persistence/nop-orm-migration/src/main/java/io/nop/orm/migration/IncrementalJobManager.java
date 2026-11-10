/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;
import io.nop.stream.connector.debezium.DebeziumCdcSourceFunction;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
import io.nop.stream.core.datastream.DataStreamSource;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.orm.migration.MigrationErrors.ARG_CONNECTOR_TYPE;
import static io.nop.orm.migration.MigrationErrors.ARG_JOB_ID;
import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CHAIN_JOB_CONFLICT;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CONFIG_INVALID;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_INCREMENTAL_NOT_SUPPORTED;

/**
 * INCREMENTAL 阶段的流图构造与运行（设计 §4.4）。
 *
 * <p><b>拓扑裁定（M1 前置验证落地形态）</b>：v1 缺省落地<b>回退形态</b>——每目标分片一个独立
 * 单 sink 链作业（debezium-cdc 源全订阅源表 + filter 本分片行 + 单 jdbc-2pc sink，
 * UPSERT_DELETE）；正确性等价（每作业只写自己的目标表），代价是 M 倍源事件消费，设计明示
 * v1 接受。M&gt;1 fan-out 流图仅在 {@link MultiSinkTopologyProbe#probe} 编译通过且配置显式
 * opt-in 时启用（探测不通过回退形态接管，不静默降级——探测与回退选择都留痕日志）。</p>
 *
 * <p>位点契约（设计 §4.2）：每个链作业的 CDC 源首跑 offset 种子化为编排器钉定的 P0
 * （{@link DebeziumCdcSourceFunction#setInitialOffsets}），snapshot.mode=never 禁 initial
 * snapshot。链作业通过 env.execute 在独立线程长驻运行；ABORT/阶段推进由 {@link #stopAll}
 * 停止（cancel source → 有界 join → 超时 interrupt → 退出确认，plan 2518 裁定④）。</p>
 *
 * <p><b>并发契约（plan 2518）</b>：⑨ connector 名派生单点函数（qs 身份键 SSOT）+
 * RunningChainJob 构造期携带 connectorName 与 Thread（发布先于 start）；④ 五段式 stop
 * （cancel → 有界 join → interrupt → 宽限 join → 退出确认复核）；⑦ jobName 键派生纳入
 * querySpace + runningJobs putIfAbsent fail-fast + 部分启动补偿回收。</p>
 */
public class IncrementalJobManager {
    private static final Logger LOG = LoggerFactory.getLogger(IncrementalJobManager.class);

    private final TargetShardMapping mapping;
    private final ShardChangeEventRouter router;
    private final ITargetSinkFactory sinkFactory;
    private final long checkpointIntervalMs;
    private final Map<String, RunningChainJob> runningJobs = new ConcurrentHashMap<>();
    /** 单链停止 join 上界（裁定④，编排器 setter 配置化，零新 @cfg: 键）。 */
    private volatile long chainStopJoinTimeoutMs = DEFAULT_CHAIN_STOP_JOIN_TIMEOUT_MS;

    /** 链停止 join 缺省上界（裁定④缺省常量 10s）。 */
    public static final long DEFAULT_CHAIN_STOP_JOIN_TIMEOUT_MS = 10_000L;
    /** interrupt 后宽限 join 上界（裁定④退出确认复核前的短上界）。 */
    static final long CHAIN_STOP_GRACE_JOIN_MS = 1_000L;

    /**
     * 目标 sink 工厂 seam：测试注入手动驱动 epoch 的 sink，生产装配 jdbc-2pc sink。
     */
    public interface ITargetSinkFactory {
        JdbcTwoPhaseCommitSink<ChangeEvent> createSink(PhysicalTable target);
    }

    public IncrementalJobManager(TargetShardMapping mapping, ITargetSinkFactory sinkFactory,
                                 long checkpointIntervalMs) {
        this.mapping = mapping;
        this.router = new ShardChangeEventRouter(mapping);
        this.sinkFactory = sinkFactory;
        this.checkpointIntervalMs = checkpointIntervalMs;
    }

    /**
     * 单链停止 join 上界注入（裁定④：编排器 setter 配置化，零新配置键）。
     */
    public void setChainStopJoinTimeoutMs(long chainStopJoinTimeoutMs) {
        this.chainStopJoinTimeoutMs = chainStopJoinTimeoutMs;
    }

    /**
     * 裁定⑨（C3-008 键派生单点化，qs 身份键 SSOT）：connector 名派生唯一入口——
     * 输入（baseConnector, 目标数）→ 有序名清单（单目标 = base；多目标 = base-i 全枚举）。
     * startFallbackChains 与 runVerify（编排器逐链探测）双入口都消费本函数，禁第二套派生。
     */
    public static List<String> deriveConnectorNames(String baseConnector, int targetCount) {
        if (targetCount <= 0)
            return Collections.emptyList();
        if (targetCount == 1)
            return Collections.singletonList(baseConnector);
        List<String> names = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            names.add(baseConnector + "-" + i);
        }
        return names;
    }

    /**
     * 运行链快照（裁定⑥⑨：编排器 runVerify 前置 failure 检查与逐链 connector 名探测的消费面）。
     */
    public List<RunningChainJob> snapshotRunningJobs() {
        return new ArrayList<>(runningJobs.values());
    }

    /**
     * 构造 Debezium 连接配置：snapshot.mode=never（全量由 nop-batch 承载，B1 契约）、
     * 白名单校验（connectorType 限 mysql/postgres）、连接参数来自 incremental.debezium。
     *
     * @param jobId           编排作业标识
     * @param config          作业配置
     * @param connectorName   connector 名（裁定⑨单点派生函数产出）
     * @param tableIncludeList 源物理表订阅清单（逗号分隔）
     */
    public DebeziumConfig buildCdcConfig(String jobId, MigrationJobConfig config, String connectorName,
                                         String tableIncludeList) {
        String connectorType = stringParam(config, "connector-type");
        if (connectorType == null)
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON,
                    "incremental.debezium.connector-type is required (whitelist: mysql/postgres)");
        if (!SourcePosition.TYPE_MYSQL.equalsIgnoreCase(connectorType)
                && !SourcePosition.TYPE_POSTGRES.equalsIgnoreCase(connectorType))
            throw new NopException(ERR_MIG_INCREMENTAL_NOT_SUPPORTED).param(ARG_CONNECTOR_TYPE, connectorType);

        DebeziumConfig cdc = new DebeziumConfig();
        cdc.setName(connectorName);
        cdc.setConnectorType(connectorType.toLowerCase());
        cdc.setServerName(stringParam(config, "server-name") != null
                ? stringParam(config, "server-name") : connectorName);
        cdc.setDatabaseHost(stringParam(config, "database-host"));
        cdc.setDatabasePort(intParam(config, "database-port"));
        cdc.setDatabaseUser(stringParam(config, "database-user"));
        cdc.setDatabasePassword(stringParam(config, "database-password"));
        cdc.setDatabaseName(stringParam(config, "database-name"));
        cdc.setTableIncludeList(tableIncludeList);
        // B1 契约：全量由 nop-batch 承载，CDC 只做增量重放
        cdc.setSnapshotMode("never");
        Long serverId = longParam(config, "database-server-id");
        if (serverId != null)
            cdc.setDatabaseServerId(serverId);
        String schemaHistoryStore = stringParam(config, "schema-history-store");
        if (schemaHistoryStore != null)
            cdc.setSchemaHistoryStore(schemaHistoryStore);
        String schemaHistoryPath = stringParam(config, "schema-history-path");
        if (schemaHistoryPath != null)
            cdc.setSchemaHistoryPath(schemaHistoryPath + "/" + connectorName);
        return cdc;
    }

    /**
     * 启动回退形态：每目标分片一个单 sink 链作业。返回已启动的作业清单。
     *
     * <p>裁定⑦（C3-001）：中途单链启动失败时回收已启动链（逐链五段式 stop），不留孤儿链；
     * 相位后翻（ABORT 落账）由编排器在捕获异常后显式执行。</p>
     *
     * @param jobId        编排作业标识
     * @param config       作业配置（connector 名缺省取 incremental.connector-name）
     * @param positionP0   PREPARE 钉定的位点（未钉定显式失败——ERR_MIG_P0_NOT_PINNED）
     */
    public List<RunningChainJob> startFallbackChains(String jobId, MigrationJobConfig config,
                                                     SourcePosition positionP0) {
        List<PhysicalTable> targets = mapping.getTargetTables();
        List<RunningChainJob> started = new ArrayList<>(targets.size());
        String baseConnector = config.getIncrementalConnectorName() != null
                ? config.getIncrementalConnectorName() : "mig-" + jobId;
        String tableIncludeList = String.join(",", sourceTableNames());
        // 2.5-C3-012（plan 2554 裁定 J）：链启动前位点三方对齐 fail-fast（seed 表集 ⊆ 订阅清单）
        CdcOffsetSeedBuilder.checkThreeWayAlignment(jobId, sourceTableNames(),
                Arrays.asList(tableIncludeList.split(",")));
        // 裁定⑨：connector 名派生单点消费（M>1 = base-i 枚举清单）
        List<String> connectorNames = deriveConnectorNames(baseConnector, targets.size());

        // M1 前置验证留痕：fan-out 探测结果与 v1 拓扑裁定（缺省回退形态）显式记录
        MultiSinkTopologyProbe.ProbeResult probe = MultiSinkTopologyProbe.probe(targets.size());
        LOG.info("nop.orm.migration.incremental-topology:jobId={},targets={},fanout-compilable={},"
                        + "topology=fallback-per-shard-chain", jobId, targets.size(), probe.isCompilable());

        for (int i = 0; i < targets.size(); i++) {
            PhysicalTable target = targets.get(i);
            try {
                DebeziumConfig cdcConfig = buildCdcConfig(jobId, config, connectorNames.get(i),
                        tableIncludeList);
                started.add(startChain(jobId, cdcConfig, target, positionP0));
            } catch (RuntimeException e) {
                // 裁定⑦：部分启动补偿——回收已启动链（逐链五段式 stop，有界 join），不留孤儿链
                compensateStartedChains(jobId, started);
                throw e;
            }
        }
        return started;
    }

    private void compensateStartedChains(String jobId, List<RunningChainJob> started) {
        for (RunningChainJob job : started) {
            try {
                job.stop(chainStopJoinTimeoutMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                LOG.warn("nop.orm.migration.chain-stop-interrupted:jobId={},jobName={}", jobId, job.getJobName());
            }
            runningJobs.remove(job.getJobName(), job);
            if (job.getFailure() != null)
                LOG.warn("nop.orm.migration.chain-failure-after-stop:jobId={},jobName={},failure={}",
                        jobId, job.getJobName(), String.valueOf(job.getFailure()));
        }
        LOG.warn("nop.orm.migration.incremental-start-compensated:jobId={},recycledChains={}",
                jobId, started.size());
    }

    /**
     * 启动单条链作业（源种子化 P0 → filter 本目标行 → 单 jdbc-2pc sink）并长驻运行。
     *
     * <p>裁定④：Thread 在 start 之前构造并注入 RunningChainJob（发布先于启动，join 无
     * stale-null 空转窗）；裁定⑦：jobName 键派生纳入 querySpace（qs 身份键单点族），
     * runningJobs putIfAbsent fail-fast（同名重入类型化拒绝）。</p>
     */
    public RunningChainJob startChain(String jobId, DebeziumConfig cdcConfig, PhysicalTable target,
                                      SourcePosition positionP0) {
        if (positionP0 == null)
            throw new NopException(MigrationErrors.ERR_MIG_P0_NOT_PINNED)
                    .param(MigrationErrors.ARG_JOB_ID, jobId)
                    .param(MigrationErrors.ARG_DETAIL, "INCREMENTAL requires the P0 pinned in PREPARE");

        Map<String, String> seed = CdcOffsetSeedBuilder.buildSerializedSeed(
                jobId, positionP0, cdcConfig.getServerName());

        DebeziumCdcSourceFunction source = new DebeziumCdcSourceFunction(cdcConfig);
        source.setInitialOffsets(seed);
        // 首跑路径：清 registry 陈旧偏移 + 种子化 P0（WI9 契约，run 之前显式执行以对齐
        // 测试面与部署面的 initializeState 语义）
        try {
            source.initializeState(null);
        } catch (Exception e) {
            throw new NopException(MigrationErrors.ERR_MIG_P0_NOT_PINNED, e)
                    .param(MigrationErrors.ARG_JOB_ID, jobId)
                    .param(MigrationErrors.ARG_DETAIL, "failed to seed CDC offset store: " + e.getMessage());
        }

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(checkpointIntervalMs);
        DataStreamSource<ChangeEvent> stream = env.addSource(source, "mig-cdc-" + cdcConfig.getName());
        JdbcTwoPhaseCommitSink<ChangeEvent> sink = sinkFactory.createSink(target);
        stream.filter(event -> router.belongsTo(event, target)).sink(sink);

        // 裁定⑦：jobName 键派生纳入 querySpace（多 querySpace 同名目标表撞键覆盖消除）
        String jobName = "orm-migration-incremental-" + jobId + "-" + target.getQuerySpace()
                + "-" + target.getTableName();
        // 裁定④：构造期注入——线程对象在 start 之前构造并发布（发布先于启动）；
        // 句柄经 holder 在线程启动前注入（thread.start 之前 holder 已就绪，failure 观测无窗）
        java.util.concurrent.atomic.AtomicReference<RunningChainJob> handleHolder =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                env.execute(jobName);
            } catch (Throwable e) {
                RunningChainJob handle = handleHolder.get();
                if (handle != null)
                    handle.failure.set(e);
            }
        }, jobName);
        thread.setDaemon(true);
        RunningChainJob running = new RunningChainJob(jobName, source, cdcConfig.getName(), thread);
        handleHolder.set(running);
        // 裁定⑦：putIfAbsent fail-fast——同名重入显式拒绝并回收本次已建资源（禁静默顶替旧句柄）
        RunningChainJob previous = runningJobs.putIfAbsent(jobName, running);
        if (previous != null) {
            thread.start();
            thread.interrupt();
            throw new NopException(ERR_MIG_CHAIN_JOB_CONFLICT)
                    .param(ARG_JOB_ID, jobId)
                    .param(ARG_REASON, "duplicate incremental chain job name (querySpace identity"
                            + " collision or reentry): " + jobName);
        }
        thread.start();
        return running;
    }

    private List<String> sourceTableNames() {
        List<String> names = new ArrayList<>();
        for (PhysicalTable table : mapping.getSourceTables()) {
            names.add(table.getTableName());
        }
        return names;
    }

    /**
     * 停止全部链作业（ABORT/SWITCH 收敛后由编排器调用）。
     *
     * <p>裁定④：逐链串行五段式 stop、全部 join 完成后才清空注册表（「仅 cancel 立即 clear」
     * 废止）；总时间预算 = 单链上界 × 链数（串行最简实现显式接受）。裁定⑥(b)：停链后逐链
     * 残留 failure 复核——非空 WARN 留痕（停止协议异常终止可观测）。</p>
     */
    public void stopAll() {
        for (RunningChainJob job : runningJobs.values()) {
            try {
                job.stop(chainStopJoinTimeoutMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("nop.orm.migration.chain-stop-interrupted:jobName={}", job.getJobName());
            }
            if (job.getFailure() != null)
                LOG.warn("nop.orm.migration.chain-failure-after-stop:jobName={},failure={}",
                        job.getJobName(), String.valueOf(job.getFailure()));
        }
        runningJobs.clear();
    }

    /**
     * 运行中的链作业句柄：cancel source + 有界 join + 观测失败（plan 2518 裁定④⑤⑦⑨）。
     */
    public static final class RunningChainJob {
        private final String jobName;
        private final DebeziumCdcSourceFunction source;
        /** 裁定⑨：链注册 connector 名（构造期携带，句柄上可观测）。 */
        private final String connectorName;
        /** 裁定④：构造期注入（发布先于 start），join 无 stale-null 空转窗。 */
        private final Thread thread;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        /** 裁定④（M4）：退出确认标记——interrupt 后仍未退出即置 false，编排重入被拒。 */
        private volatile boolean exitConfirmed = true;

        RunningChainJob(String jobName, DebeziumCdcSourceFunction source, String connectorName,
                        Thread thread) {
            this.jobName = jobName;
            this.source = source;
            this.connectorName = connectorName;
            this.thread = thread;
        }

        public String getJobName() {
            return jobName;
        }

        public String getConnectorName() {
            return connectorName;
        }

        public DebeziumCdcSourceFunction getSource() {
            return source;
        }

        /** 任务是否已观测到失败（fail-fast：编排器轮询到 failure 即阶段失败）。 */
        public Throwable getFailure() {
            return failure.get();
        }

        /** 链线程是否仍存活（裁定④退出确认复核面）。 */
        public boolean isAlive() {
            return thread.isAlive();
        }

        /** 裁定④（M4）：interrupt 后线程仍未确认退出即 false——编排重入 gateFailed 拒绝。 */
        public boolean isExitConfirmed() {
            return exitConfirmed;
        }

        /**
         * 五段式停止（裁定④）：cancel → 有界 join → 超时 interrupt → 句柄保留 → 退出确认复核
         * （宽限 join 后 isAlive 仍活即置「未退出」标记，后续编排重入被 gateFailed 拒绝——
         * 防半终止链与新链双写同目标表）。
         */
        void stop(long joinTimeoutMillis) throws InterruptedException {
            source.cancel();
            // 构造期注入后 thread 恒非 null；防御性判空仍显式留痕（禁静默跳过，rule 24）
            if (thread == null) {
                LOG.warn("nop.orm.migration.chain-thread-handle-missing:jobName={}", jobName);
                return;
            }
            thread.join(joinTimeoutMillis);
            if (thread.isAlive()) {
                LOG.warn("nop.orm.migration.chain-stop-join-timeout:jobName={},timeoutMs={}",
                        jobName, joinTimeoutMillis);
                thread.interrupt();
                thread.join(CHAIN_STOP_GRACE_JOIN_MS);
                exitConfirmed = !thread.isAlive();
            } else {
                exitConfirmed = true;
            }
        }

        public void join(long millis) throws InterruptedException {
            if (thread == null) {
                LOG.warn("nop.orm.migration.chain-thread-handle-missing:jobName={}", jobName);
                return;
            }
            thread.join(millis);
        }
    }

    private static String stringParam(MigrationJobConfig config, String key) {
        Object value = config.getIncrementalDebezium().get(key);
        return value == null ? null : value.toString();
    }

    private static int intParam(MigrationJobConfig config, String key) {
        Object value = config.getIncrementalDebezium().get(key);
        return value == null ? 0 : Integer.parseInt(value.toString());
    }

    private static Long longParam(MigrationJobConfig config, String key) {
        Object value = config.getIncrementalDebezium().get(key);
        return value == null ? null : Long.valueOf(value.toString());
    }
}
