# nop-job-core 内存调度器重构计划

## 1. 背景与动机

### 1.1 现状问题

`nop-job-core` 中的内存调度器（`DefaultJobScheduler` + `TriggerExecutorImpl`）是一套完整的 JVM 内调度循环，
但当前项目已全面转向 Coordinator-Worker 分布式架构（DB 扫描 + 状态机推进），内存调度器处于完全未使用的死代码状态：

- **未被任何 beans.xml 注册**：`DefaultJobScheduler`、`TriggerExecutorImpl` 均未注册为 IoC Bean
- **未被任何生产代码引用**：`IJobScheduler` 接口只有 `DefaultJobScheduler` 自己 import
- **仅存在于测试中**：`TestJobScheduler`、`TestTriggerExecutor`

更关键的是，这套代码设计过度抽象，引入了大量不必要的接口和状态机复杂度：

| 要删除的类/接口 | 行为 | 为什么多余 |
|---|---|---|
| `ITriggerExecutor` | 调度执行器接口 | 只有1个实现，且仅在 DefaultJobScheduler 内使用 |
| `ITriggerExecution` | 调度执行实例接口 | 同上，内部类的接口提取无意义 |
| `ITriggerAction` | 触发动作接口 | 只在 TriggerExecutorImpl 内使用，lambda 即可 |
| `ITriggerHook` | 触发器钩子接口 | 状态变更回调，无人实现 |
| `ITriggerContext` | 触发器上下文（含大量 onXxx 状态变更方法） | 66 行接口，大部分方法只有 TriggerContextImpl 实现 |
| `TriggerContextImpl` | 301 行状态机，含持久化逻辑 | 重量级，且分布式模式用 JobTriggerCalculator 替代 |
| `TriggerExecutorImpl` | 321 行内部调度循环 | 用 ScheduledExecutorService 反复调度，核心循环复杂 |
| `DefaultJobScheduler` | 369 行内存调度器 | 未使用 |
| `JobExecution` | 180 行，内存中的 Job 执行状态 | 未使用 |
| `ResolvedJobSpec` | 57 行，JobSpec 解析结果 | 未使用 |

**总计约 1500+ 行可删除代码。**

### 1.2 目标

保留分布式模式真正依赖的部分（Trigger 计算链 + Calendar + 常量 + JobTriggerCalculator），
用一个**极简的** `LocalJobScheduler`（约 200 行）替代当前的内存调度器，仅用于：

1. **嵌入式/单机模式**：不需要数据库的轻量调度场景
2. **单元测试**：验证 Trigger 时间计算 + Job 执行逻辑

---

## 2. 依赖分析

### 2.1 分布式模块对 core 的实际依赖（保留）

Coordinator 和 Worker 对 `nop-job-core` 的 import 非常有限：

```
coordinator 依赖 core:
  ├── _NopJobCoreConstants           ← 常量（状态码、策略码）
  ├── ITriggerEvalContext            ← Trigger 纯计算上下文（只读）
  └── trigger.JobTriggerCalculator   ← 纯函数：计算下次触发时间

worker 依赖 core:
  └── _NopJobCoreConstants           ← 常量
```

**分布式模块完全不依赖内存调度器相关的任何接口。**

### 2.2 需要保留的代码

| 类/包 | 用途 | 被谁使用 |
|---|---|---|
| `ITrigger` | Trigger 计算接口 | TriggerBuilder → JobTriggerCalculator |
| `ITriggerEvalContext` | 纯计算只读上下文 | Coordinator 的 Planner/CompletionProcessor |
| `ICalendar` | 日历排除接口 | TriggerBuilder |
| `calendar/*` | 日历实现 | TriggerBuilder |
| `trigger/TriggerBuilder` | 构建 Trigger 装饰器链 | JobTriggerCalculator |
| `trigger/CronTrigger` | Cron 触发器 | TriggerBuilder |
| `trigger/PeriodicTrigger` | 固定间隔触发器 | TriggerBuilder |
| `trigger/LimitCountTrigger` | 限制执行次数 | TriggerBuilder |
| `trigger/LimitTimeTrigger` | 限制时间窗口 | TriggerBuilder |
| `trigger/CheckActiveTrigger` | 检查是否活跃 | TriggerBuilder |
| `trigger/PauseCalendarTrigger` | 日历排除触发器 | TriggerBuilder |
| `trigger/HandleMisfireTrigger` | Misfire 处理 | TriggerBuilder |
| `trigger/OnceTrigger` | 单次触发 | DefaultJobScheduler → 新 LocalJobScheduler |
| `trigger/JobTriggerCalculator` | 纯函数：下次触发时间 | Coordinator |
| `trigger/TriggerContextImpl` | TriggerContext 状态机 | **需要重构**（见下文）|
| `utils/ICronExpression` | Cron 接口 | CronExpression |
| `utils/CronExpression` | Cron 实现 | CronTrigger |
| `NopJobCoreConstants` | 运行时状态常量 | Worker |
| `_NopJobCoreConstants` | 业务状态常量 | Coordinator + Worker |
| `JobCoreErrors` | 错误码 | core 内部 |

### 2.3 需要删除的代码

| 类 | 原因 |
|---|---|
| `ITriggerExecutor` | 内存调度器专用接口 |
| `ITriggerExecution` | 内存调度器专用接口 |
| `ITriggerAction` | 内存调度器专用接口 |
| `ITriggerHook` | 无人实现，内存调度器钩子 |
| `ITriggerContext` | 过度抽象的状态上下文（含 15 个 onXxx 方法） |
| `TriggerExecutorImpl` | 内存调度循环核心 |
| `DefaultJobScheduler` | 内存调度器主类 |
| `JobExecution` | 内存调度器内部状态 |
| `ResolvedJobSpec` | 内存调度器内部数据 |
| `TestJobScheduler` | DefaultJobScheduler 的测试 |
| `TestTriggerExecutor` | TriggerExecutorImpl 的测试 |

---

## 3. 新设计

### 3.1 设计原则

1. **Trigger 是纯函数**：给定 `TriggerSpec` + 上下文 + 当前时间 → 返回下次触发时间。无副作用。
2. **调度器是薄壳**：`ScheduledExecutorService.schedule()` + Trigger 计算 + 调用 `IJobInvoker`。
3. **状态最小化**：只记录运行所需的最少状态（执行计数、上次执行时间），不做重量级状态机。
4. **分布式优先**：core 模块的服务对象是 Coordinator，内存调度器只是附属品。

### 3.2 新的 `LocalJobScheduler`

```
nop-job-core/
├── ICalendar.java                     ← 保留
├── ITrigger.java                      ← 保留
├── ITriggerEvalContext.java           ← 保留
├── NopJobCoreConstants.java           ← 保留（精简）
├── _NopJobCoreConstants.java          ← 保留
├── JobCoreErrors.java                 ← 保留
├── LocalJobScheduler.java             ← 新增：极简内存调度器（~200行）
├── calendar/                          ← 保留（全部）
├── trigger/
│   ├── TriggerBuilder.java            ← 保留
│   ├── JobTriggerCalculator.java      ← 保留
│   ├── CronTrigger.java               ← 保留
│   ├── PeriodicTrigger.java           ← 保留
│   ├── LimitCountTrigger.java         ← 保留
│   ├── LimitTimeTrigger               ← 保留
│   ├── CheckActiveTrigger             ← 保留
│   ├── PauseCalendarTrigger           ← 保留
│   ├── HandleMisfireTrigger           ← 保留
│   ├── OnceTrigger                    ← 保留
│   └── TriggerContextImpl.java        ← 删除（功能合并到 LocalJobScheduler）
├── utils/                             ← 保留（全部）
└── scheduler/                         ← 删除整个包
    ├── DefaultJobScheduler.java
    ├── JobExecution.java
    └── ResolvedJobSpec.java
```

### 3.3 `LocalJobScheduler` 设计

```java
/**
 * 极简的内存任务调度器。
 *
 * 使用场景：单机嵌入式部署、单元测试。
 * 分布式场景请使用 nop-job-coordinator。
 */
public class LocalJobScheduler implements IJobScheduler {

    // ---- 依赖 ----
    private final IScheduledExecutor executor;           // 定时器
    private final Function<String, IJobInvoker> invokerResolver; // 按 beanName 解析 invoker

    // ---- 状态 ----
    private final ConcurrentHashMap<String, ScheduledJob> jobs = new ConcurrentHashMap<>();
    private volatile boolean active;

    // ---- 内部类 ----
    private class ScheduledJob {
        final JobSpec spec;
        final IJobInvoker invoker;
        final ITrigger trigger;
        final SimpleJobState state;   // 执行计数、上次时间等
        ScheduledFuture<?> nextFire;  // 当前等待触发的定时任务
        CompletableFuture<?> running; // 当前正在执行的任务（null = 空闲）
    }
}
```

**核心调度循环（约 30 行）：**

```
scheduleNext(ScheduledJob job):
  1. trigger.nextScheduleTime(now, job.state) → nextTime
  2. if nextTime <= 0 → removeJob (完成)
  3. executor.schedule(task, nextTime - now)
  4. task:
     a. job.running = invoker.invokeAsync(context)
     b. job.running.whenComplete → job.state.execCount++, update lastTime
     c. scheduleNext(job)  // 递归调度下一次
```

**与旧设计的关键差异：**

| 维度 | 旧设计（DefaultJobScheduler） | 新设计（LocalJobScheduler） |
|---|---|---|
| 接口数量 | ITriggerExecutor + ITriggerExecution + ITriggerAction + ITriggerHook + ITriggerContext | 无新接口 |
| 状态管理 | TriggerContextImpl（301行状态机，15个onXxx方法） | SimpleJobState（~30行纯数据） |
| 调度循环 | TriggerExecutorImpl 内部类（321行） | 递归 scheduleNext（~30行） |
| 总代码量 | ~1500行 | ~200行 |
| 持久化 | IJobScheduleStore + ITriggerHook 回调 | 无（纯内存） |
| 复杂度 | execIndex 防重入、scheduleIndex 防过期、executing 标志位 | ScheduledFuture.cancel() + 状态枚举 |

### 3.4 `SimpleJobState` 设计

代替 `TriggerContextImpl`，只保留 Trigger 计算链需要的上下文数据：

```java
/**
 * 内存调度器内部的极简 Job 状态。
 * 实现 ITriggerEvalContext，供 Trigger 装饰器链读取。
 */
public class SimpleJobState implements ITriggerEvalContext {
    String jobName;
    long execCount;         // 已执行次数
    long lastScheduleTime;  // 上次调度时间
    long lastEndTime;       // 上次结束时间

    // 以下从 TriggerSpec 初始化，不变
    long minScheduleTime;
    long maxScheduleTime;
    long maxExecutionCount;

    boolean completed;
}
```

### 3.5 `ITriggerContext` 的处理

`ITriggerContext` 当前是一个 66 行的接口，既有读方法又有 15 个 onXxx 写方法。
它的两个消费者：

1. **Trigger 装饰器链**：只读取 `execCount`、`lastScheduleTime`、`lastEndTime`、`isScheduleEnabled` 等
2. **TriggerContextImpl**：实现全部 onXxx 方法做状态转换

`JobTriggerCalculator` 已经证明了纯计算模式不需要 ITriggerContext 的写方法——它用 `ReadOnlyTriggerContext` 适配 `ITriggerEvalContext`。

**方案**：删除 `ITriggerContext`。Trigger 装饰器链统一使用 `ITriggerEvalContext`（只读，7 个 getter）。
`SimpleJobState` 实现 `ITriggerEvalContext`，`JobTriggerCalculator` 内部的 `ReadOnlyTriggerContext` 也可以简化为直接用 `ITriggerEvalContext`。

这意味着 `ITrigger.nextScheduleTime()` 的签名从：

```java
long nextScheduleTime(long afterTime, ITriggerContext context);
```

改为：

```java
long nextScheduleTime(long afterTime, ITriggerEvalContext context);
```

**影响范围**：
- `ITrigger` 接口签名
- 所有 Trigger 实现（CronTrigger、PeriodicTrigger 等）的参数类型
- `TriggerBuilder` 无需改动
- `JobTriggerCalculator` 简化（去掉 ReadOnlyTriggerContext 适配层）

### 3.6 `NopJobCoreConstants` 精简

`NopJobCoreConstants` 中以下常量是内存调度器专用的状态码，删除后不影响分布式模块：

```
JOB_INSTANCE_STATUS_CREATED = 0
JOB_INSTANCE_STATUS_SUSPENDED = 10
JOB_INSTANCE_STATUS_WAITING = 20
JOB_INSTANCE_STATUS_SCHEDULED = 30
JOB_INSTANCE_STATUS_RUNNING = 40
JOB_INSTANCE_STATUS_EXEC_SUCCESS = 50
JOB_INSTANCE_STATUS_EXEC_FAILED = 60
JOB_INSTANCE_STATUS_EXEC_CANCELLED = 70
JOB_INSTANCE_STATUS_EXEC_TIMEOUT = 80
JOB_INSTANCE_STATUS_JOB_FINISHED = 90
JOB_INSTANCE_STATUS_JOB_FAILED = 100
JOB_INSTANCE_STATUS_JOB_KILLED = 110
CANCEL_REASON_KILL / SUSPEND / CANCEL
```

LocalJobScheduler 内部用枚举代替。

---

## 4. 执行计划

### Phase 1：修改 Trigger 接口签名（影响 core 内部）

**目标**：将 `ITrigger` 的上下文参数从 `ITriggerContext` 改为 `ITriggerEvalContext`。

**步骤**：

1. 修改 `ITrigger.nextScheduleTime(long, ITriggerEvalContext)`
2. 修改所有 Trigger 实现的对应方法签名：
   - `CronTrigger`
   - `PeriodicTrigger`
   - `LimitCountTrigger`
   - `LimitTimeTrigger`
   - `CheckActiveTrigger`
   - `PauseCalendarTrigger`
   - `HandleMisfireTrigger`
   - `OnceTrigger`
3. 简化 `JobTriggerCalculator`：
   - 删除内部类 `ReadOnlyTriggerContext`（不再需要适配）
   - `calculateNextFireTime` 直接传 `ITriggerEvalContext` 给 trigger
4. 运行 `TestTrigger`、`TestJobTriggerCalculator` 确认通过

**文件变更**：
- 修改：`ITrigger.java`
- 修改：`trigger/*.java`（8 个 Trigger 实现）
- 修改：`trigger/JobTriggerCalculator.java`
- 不变：`trigger/TriggerBuilder.java`（不直接调用 trigger）
- 测试：`TestTrigger.java`、`TestJobTriggerCalculator.java`

### Phase 2：删除旧的内存调度器

**目标**：删除所有不再使用的代码。

**步骤**：

1. 删除 `scheduler/` 包：
   - `DefaultJobScheduler.java`
   - `JobExecution.java`
   - `ResolvedJobSpec.java`
2. 删除接口：
   - `ITriggerExecutor.java`
   - `ITriggerExecution.java`
   - `ITriggerAction.java`
   - `ITriggerHook.java`
   - `ITriggerContext.java`
3. 删除 `trigger/TriggerContextImpl.java`
4. 删除旧测试：
   - `TestJobScheduler.java`
   - `TestTriggerExecutor.java`
5. 精简 `NopJobCoreConstants.java`（移除 `JOB_INSTANCE_STATUS_*` 和 `CANCEL_REASON_*`）
6. 检查 `JobCoreErrors.java` 是否有仅被旧代码引用的错误码，清理

**文件变更**：
- 删除：~13 个文件
- 修改：`NopJobCoreConstants.java`、`JobCoreErrors.java`

### Phase 3：实现 `LocalJobScheduler`

**目标**：用 ~200 行实现极简内存调度器。

**步骤**：

1. 新建 `SimpleJobState` 内部类（实现 `ITriggerEvalContext`）
2. 新建 `LocalJobScheduler`（实现 `IJobScheduler`）
3. 实现 `addJob`：
   - 验证 spec → 解析 invoker → 构建 Trigger → 创建 ScheduledJob → scheduleNext
4. 实现 `removeJob`：cancel ScheduledFuture → 从 Map 移除
5. 实现 `suspendJob`：cancel ScheduledFuture，保留在 Map
6. 实现 `resumeJob`：重新 scheduleNext
7. 实现 `cancelJob`：cancel ScheduledFuture + running Future
8. 实现 `fireNow`：立即执行 invoker（如果当前没有 running）
9. 实现 `activate/deactivate`
10. 新建测试 `TestLocalJobScheduler`：
    - 测试 add/remove/suspend/resume/cancel/fireNow
    - 测试 Trigger 时间计算正确性
    - 测试 onceTask 自动移除

**文件变更**：
- 新增：`LocalJobScheduler.java`
- 新增：`TestLocalJobScheduler.java`

### Phase 4：更新 `IJobScheduler` 接口

**目标**：精简 `IJobScheduler` 接口，移除与内存调度强相关的方法。

**当前接口方法审查**：

```
getJobNames()                     ← 保留
getJobDetail(jobName)             ← 保留
getJobDetails(names, ignoreUnknown) ← 保留（default 实现）
addJob(spec, allowUpdate)         ← 保留
addJobs(specs, allowUpdate)       ← 保留（default 实现）
removeJob(jobName)                ← 保留
removeJobs / clearJobs            ← 保留（default 实现）
getTriggerStatus(jobName)         ← 考虑移除（内存调度器专用状态码）
resumeJob(jobName)                ← 保留
suspendJob(jobName)               ← 保留
cancelJob(jobName)                ← 保留
fireNow(jobName)                  ← 保留
activate()                        ← 保留
deactivate()                      ← 保留
```

`getTriggerStatus` 返回 `int` 状态码，使用的是 `JOB_INSTANCE_STATUS_*` 常量（Phase 2 中删除）。
改为返回枚举或简单布尔方法。

**方案**：将 `getTriggerStatus` 改为 `JobState getState(jobName)`：

```java
enum JobState {
    WAITING,    // 等待下次触发
    RUNNING,    // 正在执行
    SUSPENDED,  // 已暂停
    COMPLETED,  // 已完成（Trigger 返回 -1）
    FAILED      // 因错误终止
}
```

**步骤**：

1. 在 `nop-job-api` 中新建 `JobState` 枚举
2. 修改 `IJobScheduler.getTriggerStatus` → `getState`
3. 更新 `LocalJobScheduler` 实现
4. 检查外部是否有代码调用 `getTriggerStatus`（已确认无）

**文件变更**：
- 新增：`nop-job-api/JobState.java`
- 修改：`nop-job-api/IJobScheduler.java`
- 修改：`LocalJobScheduler.java`

### Phase 5：验证与收尾

**步骤**：

1. 全量编译：`./mvnw clean install -DskipTests -T 1C`
2. 运行 core 模块测试
3. 运行 coordinator 模块测试
4. 运行 worker 模块测试
5. 运行全量测试：`./mvnw test`
6. 检查 `nop-job-api` 中 `IJobScheduleStore` 和 `IJobInstanceState` 是否需要清理
   - `IJobScheduleStore` 仅有 `DefaultJobScheduler` 在 `TriggerContextImpl` 中使用
   - 确认是否可以移除或保留为扩展点
7. 更新 `docs-for-ai/` 中相关文档（如有）

---

## 5. 文件变更清单

### 新增文件

| 文件 | 说明 |
|---|---|
| `nop-job-core/.../LocalJobScheduler.java` | 极简内存调度器（~200行） |
| `nop-job-core/.../test/.../TestLocalJobScheduler.java` | 测试 |
| `nop-job-api/.../JobState.java` | Job 状态枚举 |

### 删除文件

| 文件 | 说明 |
|---|---|
| `nop-job-core/.../ITriggerExecutor.java` | 内存调度器接口 |
| `nop-job-core/.../ITriggerExecution.java` | 内存调度器接口 |
| `nop-job-core/.../ITriggerAction.java` | 内存调度器接口 |
| `nop-job-core/.../ITriggerHook.java` | 内存调度器钩子 |
| `nop-job-core/.../ITriggerContext.java` | 重量级状态上下文 |
| `nop-job-core/.../trigger/TriggerContextImpl.java` | 301行状态机实现 |
| `nop-job-core/.../scheduler/DefaultJobScheduler.java` | 旧内存调度器 |
| `nop-job-core/.../scheduler/JobExecution.java` | 旧调度器内部类 |
| `nop-job-core/.../scheduler/ResolvedJobSpec.java` | 旧调度器内部类 |
| `nop-job-core/.../test/TestJobScheduler.java` | 旧测试 |
| `nop-job-core/.../test/TestTriggerExecutor.java` | 旧测试 |

### 修改文件

| 文件 | 变更 |
|---|---|
| `nop-job-core/.../ITrigger.java` | 参数类型 `ITriggerContext` → `ITriggerEvalContext` |
| `nop-job-core/.../trigger/CronTrigger.java` | 同上 |
| `nop-job-core/.../trigger/PeriodicTrigger.java` | 同上 |
| `nop-job-core/.../trigger/LimitCountTrigger.java` | 同上 |
| `nop-job-core/.../trigger/LimitTimeTrigger.java` | 同上 |
| `nop-job-core/.../trigger/CheckActiveTrigger.java` | 同上 |
| `nop-job-core/.../trigger/PauseCalendarTrigger.java` | 同上 |
| `nop-job-core/.../trigger/HandleMisfireTrigger.java` | 同上 |
| `nop-job-core/.../trigger/OnceTrigger.java` | 同上 |
| `nop-job-core/.../trigger/JobTriggerCalculator.java` | 删除 ReadOnlyTriggerContext，简化 |
| `nop-job-core/.../NopJobCoreConstants.java` | 移除 `JOB_INSTANCE_STATUS_*`、`CANCEL_REASON_*` |
| `nop-job-core/.../JobCoreErrors.java` | 清理仅旧代码引用的错误码 |
| `nop-job-api/.../IJobScheduler.java` | `getTriggerStatus` → `getState` |

---

## 6. 风险与注意事项

1. **`IJobInstanceState` / `IJobScheduleStore` 的去留**：
   - `IJobInstanceState` 被 `IJobExecutionContext` 继承，Worker 使用 → 保留
   - `IJobScheduleStore` 只有 `TriggerContextImpl.onChange()` 使用 → 删除 `TriggerContextImpl` 后成为死接口 → 可在 Phase 2 中一并删除
   - `JobDetail` 无外部使用者 → 可考虑删除，但暂保留作为 API 的一部分

2. **`IJobExecutionContext` 中的方法**：
   - `isJobFinished()`、`isInstanceRunning()`、`isScheduleEnabled()` 这些方法来自 `ITriggerContext`
   - 删除 `ITriggerContext` 后需要决定这些方法留在 `IJobExecutionContext` 还是移除
   - Worker 的 `DefaultJobExecutionContextBuilder` 实现了 `IJobExecutionContext` → 需要调整
   - **建议**：保留在 `IJobExecutionContext` 中（Worker 需要这些语义），但简化实现

3. **`LimitCountTrigger` 和 `LimitTimeTrigger` 对 context 的依赖**：
   - 它们读取 `context.getExecCount()`、`context.getMaxExecutionCount()` 等
   - 这些字段在 `ITriggerEvalContext` 中已有 → 切换无问题

4. **编译顺序**：Phase 1（改 Trigger 签名）和 Phase 2（删旧代码）必须先于 Phase 3（新实现）
