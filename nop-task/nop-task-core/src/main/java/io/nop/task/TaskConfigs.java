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

    // plan 2541 W-09/W-10（CRA 8.48 R4-08，MA-16/MA-18 裁定 B/D）：运行期表达式上界（路线 β）。
    // sleep/fork-n count/loop-n 迭代数为运行期求值表达式，构建期校验不可判定（R2 Major-1 义务）；
    // 超上界运行期 typed 拒绝，消除外部输入驱动的 OOM 通道与长时占线程面
    @Description("sleep 步骤时长上限（毫秒）；sleep 为运行期表达式，超上限在执行期显式拒绝")
    IConfigReference<Integer> CFG_TASK_STEP_MAX_SLEEP_MS =
            AppConfig.varRef(s_loc, "nop.task.step.max-sleep-ms", Integer.class, 600000);

    @Description("forkN 步骤分支数上限；count 为运行期表达式，超上限在执行期显式拒绝")
    IConfigReference<Integer> CFG_TASK_STEP_MAX_FORK_COUNT =
            AppConfig.varRef(s_loc, "nop.task.step.max-fork-count", Integer.class, 1000);

    @Description("loopN 步骤迭代数上限（含端点：floor(|end-begin|/|step|)+1）；超上限在执行期显式拒绝")
    IConfigReference<Integer> CFG_TASK_STEP_MAX_LOOP_COUNT =
            AppConfig.varRef(s_loc, "nop.task.step.max-loop-count", Integer.class, 10000);

    // ===== CRA 8.49 R4-09（plan 2542）新增键（裁定 B/E 一次成文，禁散落）=====

    @Description("节点身份标识（task 行 workerId 属主归因；缺省=主机名+启动随机后缀，实例内一次生成）")
    IConfigReference<String> CFG_TASK_NODE_ID =
            AppConfig.varRef(s_loc, "nop.task.node-id", String.class, null);

    @Description("SUSPENDED 任务滞留判定阈值（毫秒）；updateTime 早于 now-threshold 记入 stale 指标")
    IConfigReference<Integer> CFG_TASK_SUSPENDED_STALE_MS =
            AppConfig.varRef(s_loc, "nop.task.suspended.stale-ms", Integer.class, 86400000);
}
