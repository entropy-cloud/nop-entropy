package io.nop.task;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

public interface TaskConfigs {
    SourceLocation S_LOC = SourceLocation.fromClass(TaskConfigs.class);

    @Description("最多允许多少个全局Semaphore对象")
    IConfigReference<Integer> CFG_TASK_MAX_GLOBAL_SEMAPHORES =
            AppConfig.varRef(S_LOC, "nop.task.max-global-semaphores", Integer.class, 10000);

    @Description("最多允许多少个全局RateLimiter对象")
    IConfigReference<Integer> CFG_TASK_MAX_GLOBAL_RATE_LIMITERS =
            AppConfig.varRef(S_LOC, "nop.task.max-global-rate-limiters", Integer.class, 10000);
}
