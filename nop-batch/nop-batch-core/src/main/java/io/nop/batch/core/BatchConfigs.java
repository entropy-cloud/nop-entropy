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
}
