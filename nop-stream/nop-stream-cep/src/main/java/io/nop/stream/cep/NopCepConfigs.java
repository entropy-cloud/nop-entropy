/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import static io.nop.api.core.config.AppConfig.varRef;

public interface NopCepConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopCepConfigs.class);

    String COMMON_HINT =
            "And it could accelerate the CEP operate process "
                    + "speed and limit the capacity of cache in pure memory. Note: the CEP "
                    + "operator currently only supports the Memory state backend — 'rocksdb' is "
                    + "NOT supported for CEP (SharedBuffer Lockable is not JSON-serializable; the "
                    + "combination fails fast at deployment, R5 CEP-1).";

    @Description("The Config option to set the maximum element number the "
            + "eventsBufferCache of SharedBuffer could hold. "
            + COMMON_HINT)
    IConfigReference<Integer> CEP_SHARED_BUFFER_EVENT_CACHE_SLOTS =
            varRef(s_loc, "nop.cep.pipeline.sharedbuffer.cache.event-slots", Integer.class, 1024);

    @Description("The Config option to set the maximum element number the entryCache"
            + " of SharedBuffer could hold. And it could accelerate the"
            + " CEP operate process speed with state."
            + COMMON_HINT)
    IConfigReference<Integer> CEP_SHARED_BUFFER_ENTRY_CACHE_SLOTS =
            varRef(s_loc, "nop.cep.pipeline.sharedbuffer.cache.entry-slots", Integer.class, 1024);

    @Description("The interval to log the information of cache state statistics in "
            + "CEP operator.")
    IConfigReference<Duration> CEP_CACHE_STATISTICS_INTERVAL =
            varRef(s_loc, "nop.cep.pipeline.sharedbuffer.cache.statistics-interval", Duration.class, Duration.of(30, ChronoUnit.MINUTES));

    /**
     * CRA 8.43 R4-03 W-10 (C8-002, adjudication C.2): subtask-level upper bound
     * of in-flight (buffered, not yet watermark-consumed) match events across
     * ALL keys of one CEP operator instance. The match buffer
     * ({@code elementQueueState} buckets + the SharedBuffer backing that grows
     * with them) consumes only when the watermark advances — an idle source
     * partition or a long-period pattern without {@code within} otherwise
     * accumulates without bound until OOM. Overflow fails the task typed
     * (visible failure over silent OOM); the typed param carries key, current
     * count and limit as the minimal observability face (depth gauges belong
     * to the observability governance track, C8-008 deferred).
     */
    @Description("Maximum number of in-flight match-buffer events per CEP "
            + "operator subtask (across all keys). Overflow fails the task "
            + "typed with guidance to check watermark progress or add a "
            + "pattern within clause. Must be > 0.")
    IConfigReference<Integer> MATCH_BUFFER_MAX_EVENTS =
            varRef(s_loc, "nop.cep.pipeline.match-buffer.max-events", Integer.class, 100000);
}
