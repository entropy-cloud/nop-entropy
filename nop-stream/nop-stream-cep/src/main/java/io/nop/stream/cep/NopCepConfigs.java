/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static io.nop.api.core.config.AppConfig.varRef;

public interface NopCepConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(NopCepConfigs.class);

    String COMMON_HINT =
            "And it could accelerate the CEP operate process "
                    + "speed and limit the capacity of cache in pure memory. Note: It's only effective to "
                    + "limit usage of memory when 'state.backend' was set as 'rocksdb', which would "
                    + "transport the elements exceeded the number of the cache into the rocksdb state "
                    + "storage instead of memory state storage.";

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

}
