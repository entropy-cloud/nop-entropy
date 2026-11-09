package io.nop.batch.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;

import static io.nop.api.core.config.AppConfig.varRef;

public interface BatchConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(BatchConfigs.class);

    @Description("异步Processor执行的缺省超时时间")
    IConfigReference<Duration> CFG_BATCH_ASYNC_PROCESS_TIMEOUT = varRef(s_loc, "nop.batch.async-process-timeout",
            Duration.class, Duration.ofMinutes(10)); // 缺省为10分钟

    /**
     * concurrency / fetchThreadCount 的装配期上界（1.7-C8-003，plan 2498 Phase 5）。
     * 超界时 BatchTask 构造（concurrency）与 BatchDispatchConfig 装配（fetchThreadCount）
     * 以类型化异常 fail-fast。缺省 200 与平台共享 cached 池上界
     * （nop.commons.concurrent.cached-pool.maxPoolSize）对齐：单任务并发不可能超过兜底池容量；
     * 需要更高并发的部署必须同时显式调大两个配置键。
     */
    @Description("concurrency/fetchThreadCount的装配期上界，超界构造期报错")
    IConfigReference<Integer> CFG_BATCH_MAX_CONCURRENCY_CAP = varRef(s_loc, "nop.batch.max-concurrency-cap",
            Integer.class, 200);

    /**
     * 1.7-C5-004（plan 2545 W-04）：provider 返回 List 时该 List 常驻任务全程——
     * 规模达此阈值即 WARN（可观测）。&lt;=0 = 关闭告警。
     */
    IConfigReference<Integer> CFG_BATCH_LIST_LOADER_WARN_SIZE = varRef(s_loc,
            "nop.batch.list-loader.warn-size", Integer.class, 1_000_000);

    /**
     * 1.7-C5-003（plan 2545 W-05；closure audit B-2 修正）：单 chunk 输出集上界。
     * {@code useBatchRequestGenerator} 路径下单个 chunk item 可在 process 内循环展开
     * {@code totalCount} 条输出——{@code batchSize} 只约束输入条数，不约束展开数。
     * 本键是该路径的内存护栏：超过即 fail-fast（{@code ERR_BATCH_TOO_MANY_PROCESSING_ITEMS}），
     * 不静默膨胀。
     * <p>
     * 缺省 0 = 不限制（保持既有部署行为，上界为 opt-in）。设为正值即生效于：
     * ① {@code BatchTaskBuilder.buildChunkProcessor}（主装配链）；
     * ② {@code OrmBatchConsumerProvider.setup}（自建 {@code BatchProcessorConsumer} 的旁路，须独立接线）。
     */
    @Description("单chunk输出集上界（processor展开面护栏），<=0表示不限制")
    IConfigReference<Integer> CFG_BATCH_MAX_OUTPUT_ITEMS_PER_CHUNK = varRef(s_loc,
            "nop.batch.max-output-items-per-chunk", Integer.class, 0);

    /**
     * 1.7-C8-004（plan 2546 W-07）：任务级 metrics 开关。
     * <p>
     * 缺省 true：每个任务执行都会向 {@code GlobalMeterRegistry} 注册 task 维度 meter。
     * 基数上界 = 不同 taskName 数 × 14 meter（10 Timer + 4 Counter）。置 false 可整体关闭。
     */
    @Description("是否启用批任务级metrics（缺省true）")
    IConfigReference<Boolean> CFG_BATCH_METRICS_ENABLED = varRef(s_loc,
            "nop.batch.metrics.enabled", Boolean.class, true);
}
