package io.nop.task;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

public interface TaskConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(TaskConfigs.class);

    @Description("最多允许多少个全局Semaphore对象")
    IConfigReference<Integer> CFG_TASK_MAX_GLOBAL_SEMAPHORES =
            AppConfig.varRef(s_loc, "nop.task.max-global-semaphores", Integer.class, 10000);

    @Description("最多允许多少个全局RateLimiter对象")
    IConfigReference<Integer> CFG_TASK_MAX_GLOBAL_RATE_LIMITERS =
            AppConfig.varRef(s_loc, "nop.task.max-global-rate-limiters", Integer.class, 10000);

    // plan 2514 MA-08（裁定⑤）：闸门注册表键空间治理——空闲逐出阈值与容量告警阈值。
    // 逐出为同 key 复访内联渐进收敛口径（无全 map 清扫位），permit 池完整性约束见 TaskFlowManagerImpl。
    @Description("全局闸门注册表条目空闲逐出阈值（毫秒）；空闲且信号量满额才逐出")
    IConfigReference<Integer> CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS =
            AppConfig.varRef(s_loc, "nop.task.global-gate.idle-evict-ms", Integer.class, 300000);

    @Description("全局闸门注册表容量告警阈值（超限周期性 WARN，不做强制清扫）")
    IConfigReference<Integer> CFG_TASK_GLOBAL_GATE_MAX_ENTRIES =
            AppConfig.varRef(s_loc, "nop.task.global-gate.max-entries", Integer.class, 10000);

    // plan 2514 MA-03（裁定③）：sync 包装等待时限；0=不限时（仅取消唤醒）；负值装配期 fail-fast
    @Description("sync 步骤同步等待时限（毫秒）；0=不限时（仅取消唤醒），负值非法")
    IConfigReference<Integer> CFG_TASK_STEP_SYNC_WAIT_TIMEOUT_MS =
            AppConfig.varRef(s_loc, "nop.task.step.sync-wait-timeout-ms", Integer.class, 600000);
}
