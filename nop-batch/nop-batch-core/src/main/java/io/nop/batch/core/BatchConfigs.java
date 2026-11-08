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
}
