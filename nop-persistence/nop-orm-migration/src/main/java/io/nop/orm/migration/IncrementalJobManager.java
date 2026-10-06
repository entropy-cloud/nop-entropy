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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.orm.migration.MigrationErrors.ARG_CONNECTOR_TYPE;
import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
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
 * 停止（cancel source → run 循环退出）。</p>
 */
public class IncrementalJobManager {

    private final TargetShardMapping mapping;
    private final ShardChangeEventRouter router;
    private final ITargetSinkFactory sinkFactory;
    private final long checkpointIntervalMs;
    private final Map<String, RunningChainJob> runningJobs = new ConcurrentHashMap<>();

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
     * 构造 Debezium 连接配置：snapshot.mode=never（全量由 nop-batch 承载，B1 契约）、
     * 白名单校验（connectorType 限 mysql/postgres）、连接参数来自 incremental.debezium。
     *
     * @param jobId           编排作业标识
     * @param config          作业配置
     * @param connectorName   connector 名（回退形态按目标分片唯一化：&lt;name&gt;-&lt;序号&gt;）
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

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(IncrementalJobManager.class);

    /**
     * 启动回退形态：每目标分片一个单 sink 链作业。返回已启动的作业清单。
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

        // M1 前置验证留痕：fan-out 探测结果与 v1 拓扑裁定（缺省回退形态）显式记录
        MultiSinkTopologyProbe.ProbeResult probe = MultiSinkTopologyProbe.probe(targets.size());
        LOG.info("nop.orm.migration.incremental-topology:jobId={},targets={},fanout-compilable={},"
                        + "topology=fallback-per-shard-chain", jobId, targets.size(), probe.isCompilable());

        for (int i = 0; i < targets.size(); i++) {
            PhysicalTable target = targets.get(i);
            String connectorName = targets.size() == 1 ? baseConnector : baseConnector + "-" + i;
            DebeziumConfig cdcConfig = buildCdcConfig(jobId, config, connectorName, tableIncludeList);
            started.add(startChain(jobId, cdcConfig, target, positionP0));
        }
        return started;
    }

    /**
     * 启动单条链作业（源种子化 P0 → filter 本目标行 → 单 jdbc-2pc sink）并长驻运行。
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

        String jobName = "orm-migration-incremental-" + jobId + "-" + target.getTableName();
        RunningChainJob running = new RunningChainJob(jobName, source);
        Thread thread = new Thread(() -> {
            try {
                env.execute(jobName);
            } catch (Throwable e) {
                running.failure.set(e);
            }
        }, jobName);
        thread.setDaemon(true);
        thread.start();
        running.thread = thread;
        runningJobs.put(jobName, running);
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
     */
    public void stopAll() {
        for (RunningChainJob job : runningJobs.values()) {
            job.stop();
        }
        runningJobs.clear();
    }

    /**
     * 运行中的链作业句柄：cancel source + 观测失败。
     */
    public static final class RunningChainJob {
        private final String jobName;
        private final DebeziumCdcSourceFunction source;
        private volatile Thread thread;
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        RunningChainJob(String jobName, DebeziumCdcSourceFunction source) {
            this.jobName = jobName;
            this.source = source;
        }

        public String getJobName() {
            return jobName;
        }

        public DebeziumCdcSourceFunction getSource() {
            return source;
        }

        /** 任务是否已观测到失败（fail-fast：编排器轮询到 failure 即阶段失败）。 */
        public Throwable getFailure() {
            return failure.get();
        }

        public void stop() {
            source.cancel();
        }

        public void join(long millis) throws InterruptedException {
            Thread t = thread;
            if (t != null)
                t.join(millis);
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
