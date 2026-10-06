/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.api.core.exceptions.NopException;
import io.nop.batch.core.IBatchStateStore;
import io.nop.batch.core.IBatchTask;
import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import io.nop.batch.jdbc.loader.JdbcPageBatchLoaderProvider;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.migration.TargetShardMapping.PhysicalTable;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import static io.nop.orm.migration.MigrationErrors.ARG_REASON;
import static io.nop.orm.migration.MigrationErrors.ERR_MIG_CONFIG_INVALID;

/**
 * FULL 阶段的执行面委托（设计 §4.1 裁定 1 + §4.2 位点时序契约）：逐源物理表构造 nop-batch
 * chunk 作业——offset 分页读源（查询成功后才推进游标，{@link JdbcPageBatchLoaderProvider}
 * 既有断点语义）→ keyed upsert 写目标分片表。限流/批次/并发复用 batch 既有旋钮
 * （rateLimit/batchSize/concurrency，设计 §4.7：不新造限流器）。
 *
 * <p>断点续传：{@code IBatchStateStore} 由宿主注入（nop-batch-dao 在 classpath 时注入
 * {@code DaoBatchStateStore}，状态行经 {@code NopBatchTask} 持久化，任务行带 flowId 与编排状态
 * 双向追溯）；未注入时任务无跨重启断点，重跑安全由 keyed upsert 幂等覆盖保证（设计 §4.5）。</p>
 */
public class FullCopyBatchJob {
    private final IJdbcTemplate jdbcTemplate;
    private final TargetShardMapping mapping;
    private final MigrationJobConfig config;
    private final List<String> keyColumns;
    private IBatchStateStore stateStore;

    public FullCopyBatchJob(IJdbcTemplate jdbcTemplate, TargetShardMapping mapping,
                            MigrationJobConfig config, List<String> keyColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapping = mapping;
        this.config = config;
        this.keyColumns = keyColumns;
    }

    /**
     * 注入宿主批断点存储（可选）。
     */
    public void setStateStore(IBatchStateStore stateStore) {
        this.stateStore = stateStore;
    }

    /**
     * 执行全量复制，返回复制的总行数。任务名/任务键与编排 jobId 绑定：
     * taskName = batch-task-name，taskKey = {@code <jobId>:<源物理表>}，flowId = jobId。
     */
    public long run(String jobId) {
        if (mapping.getSourceTables().isEmpty())
            throw new NopException(ERR_MIG_CONFIG_INVALID).param(ARG_REASON, "no source physical tables");
        long total = 0;
        for (PhysicalTable source : mapping.getSourceTables()) {
            total += copyTable(jobId, source);
        }
        return total;
    }

    private long copyTable(String jobId, PhysicalTable source) {
        LongAdder copied = new LongAdder();
        ShardedUpsertConsumerProvider consumer =
                new ShardedUpsertConsumerProvider(jdbcTemplate, mapping, keyColumns) {
                    @Override
                    void consume(java.util.Collection<java.util.Map<String, Object>> items,
                                 io.nop.batch.core.IBatchChunkContext context) {
                        super.consume(items, context);
                        copied.add(items.size());
                    }
                };
        consumer.setSourceTableName(source.getTableName());

        BatchTaskBuilder<java.util.Map<String, Object>, java.util.Map<String, Object>> builder =
                BatchTaskBuilder.create();
        builder.taskName(config.getBatchTaskName());
        builder.batchSize(config.getThrottleBatchSize());
        builder.concurrency(config.getThrottleConcurrency());
        if (config.getThrottleRateLimit() > 0)
            builder.rateLimit(config.getThrottleRateLimit());
        if (stateStore != null)
            builder.stateStore(stateStore);
        builder.loader(newSourceLoader(source));
        builder.consumer(consumer);

        IBatchTask task = builder.buildTask();
        BatchTaskContextImpl context = new BatchTaskContextImpl();
        context.setTaskKey(jobId + ":" + source.getTableName());
        context.setFlowId(jobId);

        try {
            task.execute(context);
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new NopException(MigrationErrors.ERR_MIG_FULL_COPY_FAILED, e)
                    .param(ARG_REASON, "full copy failed for source table " + source);
        }
        return copied.sum();
    }

    private JdbcPageBatchLoaderProvider<java.util.Map<String, Object>> newSourceLoader(PhysicalTable source) {
        JdbcPageBatchLoaderProvider<java.util.Map<String, Object>> loader = new JdbcPageBatchLoaderProvider<>();
        loader.setJdbcTemplate(jdbcTemplate);
        loader.setSqlGenerator(ctx -> buildSelectSql(source));
        return loader;
    }

    private SQL buildSelectSql(PhysicalTable source) {
        IDialect dialect = jdbcTemplate.getDialectForQuerySpace(source.getQuerySpace());
        StringBuilder sb = new StringBuilder("select * from ");
        sb.append(dialect.escapeSQLName(source.getTableName()));
        if (!keyColumns.isEmpty()) {
            sb.append(" order by ");
            for (int i = 0; i < keyColumns.size(); i++) {
                if (i > 0)
                    sb.append(',');
                sb.append(dialect.escapeSQLName(keyColumns.get(i)));
            }
        }
        return SQL.begin().querySpace(source.getQuerySpace())
                .name("orm-migration-full-select:" + source.getTableName())
                .sql(sb.toString()).end();
    }
}
