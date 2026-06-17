# nop-job — 定时任务调度

nop-job 提供两种调度模式，共享同一套触发器（CRON / 固定频率 / 固定延迟 / 一次性）能力：

- **本地模式（LocalJobScheduler）**：纯内存调度器，无需数据库，适用于单机嵌入式场景。引入 `nop-job-core` 后手动构造。
- **分布式模式（Coordinator/Worker）**：数据库驱动的集群调度系统，扫描器自动启动，支持高可用、分片、阻塞策略、超时控制。

> **如何选择**：单实例、作业定义可丢失（重启重建）→ 本地模式（仅引入 `nop-job-core`）。需要持久化、跨实例、可视化管理（AMIS 页面）→ 分布式模式（引入 `nop-job-service`，自动包含 coordinator + worker）。分布式模式退化为单实例（只部署一个节点）也能正常工作。

## 功能概览

- **CRON 表达式**：标准 cron 调度
- **固定频率**：fixed-rate 固定间隔执行
- **固定延迟**：fixed-delay 上次结束后延迟执行
- **一次性触发**：手动触发单次执行
- **Misfire 处理**：错过触发的补偿策略（仅分布式模式）
- **暂停日历**：指定日期不执行
- **阻塞策略**：任务重叠时的处理策略（仅分布式模式）
- **超时控制**：任务执行超时设置（仅分布式模式）
- **分片执行**：支持任务分片到多个 worker（仅分布式模式）
- **与 nop-retry 集成**：失败重试（仅分布式模式，需引入 `nop-job-retry-adapter`）

---

## 一、本地模式（LocalJobScheduler）

本地模式有两种用法：

- **编程式**：仅引入 `nop-job-core`，手动构造 `LocalJobScheduler` + 自行实现 `IJobInvoker`。最轻量，适合嵌入式场景。
- **YAML 配置式**：引入 `nop-job-local`，通过 `scheduler.yaml` 配置文件定义作业，自动注册到 `LocalJobScheduler` 并调用 IoC bean 方法。

### 1.1 编程式（仅 nop-job-core）

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-job-core</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

`nop-job-core` 不会自动注册任何 bean。`LocalJobScheduler` 必须手动构造。

### 1.2 编程式完整使用示例

```java
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.job.api.execution.*;
import io.nop.job.api.spec.*;
import io.nop.job.core.LocalJobScheduler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// 1. 实现 IJobInvoker（作业执行逻辑）
public class MyJobInvoker implements IJobInvoker {
    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
        System.out.println("执行 job=" + ctx.getJobName()
                + " 第 " + ctx.getExecCount() + " 次, params=" + ctx.getJobParams());
        // 返回 null 表示按默认调度逻辑继续；返回 CONTINUE 显式继续
        return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
        return CompletableFuture.completedFuture(true);
    }
}

// 2. 构造调度器（invoker 通过 resolver 函数按名字解析）
IScheduledExecutor executor = GlobalExecutors.globalTimer();
LocalJobScheduler scheduler = new LocalJobScheduler(
        executor,
        name -> new MyJobInvoker()   // Function<String, IJobInvoker>
);
scheduler.activate();   // 使用前必须激活

// 3. 定义并注册作业
JobSpec spec = new JobSpec();
spec.setJobName("demo-cron");
spec.setJobInvoker("myJob");       // 传给 resolver 的 name
spec.setJobParams(Map.of("key", "value"));

TriggerSpec ts = new TriggerSpec();
ts.setCronExpr("0 */5 * * * ?");   // 每 5 分钟
// 或者固定间隔：ts.setRepeatInterval(5000); ts.setRepeatFixedDelay(true);
ts.setMaxExecutionCount(0);        // 0 = 无限执行
spec.setTriggerSpec(ts);

scheduler.addJob(spec, false);     // false = 已存在则抛 already-exists

// 4. 运行期管理
scheduler.suspendJob("demo-cron"); // 暂停（保留在调度器中）
scheduler.resumeJob("demo-cron");  // 恢复
scheduler.fireNow("demo-cron");    // 立即触发一次（同一 jobName 任意时刻只跑一个实例）
scheduler.removeJob("demo-cron");  // 删除
scheduler.deactivate();            // 关闭，清空所有作业
```

参考测试：`nop-job/nop-job-core/src/test/java/io/nop/job/core/TestLocalJobScheduler.java`。

### 1.3 YAML 配置式（推荐，引入 nop-job-local）

在 `nop-job-core` 的基础上，引入 `nop-job-local` 可通过 YAML 配置文件声明式定义定时任务：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-job-local</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

引入后，`nop-job-local` 自动注册以下 bean：

| Bean ID | 类型 | 说明 |
|---------|------|------|
| `nopJobScheduledExecutor` | `DefaultScheduledExecutor` | 单线程调度执行器 |
| `nopJobInvokerResolver` | `BeanContainerInvokerResolver` | 通过 `BeanContainer.tryGetBean("nopJobInvoker_{name}")` 解析 invoker |
| `nopJobLocalScheduler` | `LocalJobScheduler` | 内存调度器，使用上述 executor + resolver |
| `nopJobInvoker_beanMethod` | `BeanMethodJobInvoker` | 通过 IoC bean 反射调用指定方法 |
| `nopJobLocalConfigLoader` | `LocalJobConfigLoader` | 启动时从 YAML 加载作业并注册到 `LocalJobScheduler` |

在 VFS 路径 `/nop/job/conf/scheduler.yaml` 定义作业（支持 `x:extends` delta 覆盖）：

```yaml
enabled: true
jobs:
  - jobName: my-cron-job
    displayName: My Cron Job
    trigger:
      cronExpr: "0 */5 * * * ?"
    invoker:
      bean: myService
      method: doWork
    params:
      key: value
```

配置路径可通过 `nop.job.scheduler.config-path` 配置项覆盖，默认 `/nop/job/conf/scheduler.yaml`。文件不存在时静默跳过，不启动调度。

参考实现：`nop-job/nop-job-local/src/main/java/io/nop/job/local/`。

### 1.4 注意事项

- 作业定义只存在于内存，**重启即丢失**，不持久化。
- `LocalJobScheduler` 不参与分布式协调，不要在多实例部署中使用它调度需要唯一执行的作业。
- 不支持 Misfire 补偿、阻塞策略、超时控制、分片——这些是分布式模式的能力。

---

## 二、分布式模式（Coordinator/Worker）

### 2.1 Maven 依赖

```xml
<!-- 只需引入 nop-job-service，自动传递依赖 coordinator + worker + dao -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-job-service</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

`nop-job-service` 已声明对 `nop-job-coordinator` 和 `nop-job-worker` 的编译依赖，因此**引入 `nop-job-service` 即自动获得完整的分布式调度能力**。`nop-job-dao`、`nop-job-core`、`nop-job-api` 也作为传递依赖被引入。

### 2.2 自动启动机制

引入 `nop-job-service` 并配置数据库后，无需手动启动：

- `JobCoordinator` 继承 `LifeCycleSupport`，`doStart()` 自动调用四个 scanner 的 `startScanning()`：Planner、Dispatcher、Completion、Timeout。
- `JobWorker` 同样继承 `LifeCycleSupport`，自动启动 WorkerScanner。

NopIoC 在容器启动时自动调用 `LifeCycleSupport` bean 的 `start()`，因此**单实例部署也能开箱即用**——扫描器自动轮询数据库，发现到期的 Schedule 即创建 Fire → Task 并执行。

### 2.3 新增一个调度作业（完整步骤）

**步骤 1：实现并注册 IJobInvoker**

```java
import io.nop.api.core.annotations.ioc.IocBean;
import io.nop.job.api.execution.*;

@IocBean("nopJobInvoker_myJob")   // bean 名必须为 nopJobInvoker_<executorKind>
public class MyJobInvoker implements IJobInvoker {
    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx) {
        System.out.println("job=" + ctx.getJobName() + ", params=" + ctx.getJobParams());
        return CompletableFuture.completedFuture(JobFireResult.CONTINUE);
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx) {
        return CompletableFuture.completedFuture(true);
    }
}
```

> **bean 命名约定**：Worker 通过 `DefaultJobInvokerResolver` 解析 invoker，bean 名固定前缀为 `nopJobInvoker_`，后缀为作业记录中的 `executorKind` 字段值。上面的例子对应 `executorKind = myJob`。参考 `nop-job-service` 内置的 `nopJobInvoker_rpc`、`nopJobInvoker_rpcBroadcast`、`nopJobInvoker_test`。

**步骤 2：创建 NopJobSchedule 记录**

往 `nop_job_schedule` 表插入一条记录（通过 GraphQL mutation、BizModel 或直接 SQL）。关键字段：

| 字段 | 值 | 说明 |
|------|-----|------|
| `jobName` | `demo-job` | 作业名，全局唯一 |
| `scheduleStatus` | `10`（ENABLED） | 状态码：DISABLED=0, ENABLED=10, PAUSED=20, COMPLETED=30, ARCHIVED=40 |
| `executorKind` | `myJob` | 对应 invoker bean 名后缀 |
| `triggerType` | `CRON` | CRON / FIXED_RATE / FIXED_DELAY / ONCE |
| `cronExpr` | `0 */5 * * * ?` | CRON 模式时填写 |
| `repeatIntervalMs` | — | FIXED_RATE/FIXED_DELAY 模式时填写（毫秒） |
| `jobParams` | `{"key":"value"}` | JSON 格式参数 |
| `nextFireTime` | 自动计算 | 启用/恢复时由 `recalculateNextFireTime` 填充 |

**步骤 3：等待调度执行**

Planner 每 5 秒扫描 `nextFireTime <= now` 且 `scheduleStatus = ENABLED` 的记录 → 创建 `NopJobFire`（触发批次）→ Dispatcher 创建 `NopJobTask` → Worker 认领执行 → Completion 汇总结果并推进 `nextFireTime`。

### 2.4 运行期管理（GraphQL / BizModel）

`NopJobScheduleBizModel` 提供以下管理命令（暴露为 GraphQL mutation）：

| 命令 | 说明 | 前置状态要求 |
|------|------|-------------|
| `enableSchedule(id)` | 启用调度，自动计算 nextFireTime | DISABLED |
| `disableSchedule(id)` | 禁用调度 | ENABLED 或 PAUSED |
| `pauseSchedule(id)` | 暂停 | ENABLED |
| `resumeSchedule(id)` | 恢复，自动计算 nextFireTime | PAUSED |
| `triggerNow(id, overrideParams)` | 手动触发一次 | ENABLED |
| `archiveSchedule(id)` | 归档 | ENABLED/DISABLED/PAUSED/COMPLETED |

---

## 三、核心 SPI 契约

### 3.1 IJobInvoker（必须实现）

```java
public interface IJobInvoker {
    // 每次触发时调用，返回异步结果
    CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext ctx);

    // 取消当前运行实例
    CompletionStage<Boolean> cancelAsync(IJobExecutionContext ctx);
}
```

`IJobExecutionContext` 提供：`getJobName()`、`getJobParams()`、`getInstanceId()`、`getExecCount()`（从 1 开始）、`getScheduledExecTime()`、`getExecFailCount()`、`getAttribute(name)`/`setAttribute(name, val)` 等。

### 3.2 JobFireResult（返回值约定）

| 返回值 | 含义 |
|--------|------|
| `null` | 按默认调度逻辑继续（trigger 计算下次时间） |
| `JobFireResult.CONTINUE` | 显式继续，使用 trigger 计算的时间 |
| `JobFireResult.CONTINUE(nextScheduleTime)` | 覆盖 trigger，用指定时间做下次调度（`>0` 时生效） |
| `JobFireResult.COMPLETED` | 作业进入 COMPLETED 状态，不再调度 |
| `JobFireResult.ERROR(errorBean)` | 作业进入 ERROR 状态，不再调度 |

### 3.3 触发器类型

| TriggerSpec 字段 | 触发类型 | 说明 |
|------------------|---------|------|
| `cronExpr` 非空 | CRON | 标准 Unix cron 表达式 |
| `repeatInterval > 0` 且 `repeatFixedDelay=false` | FIXED_RATE | 从上次调度时间起固定间隔 |
| `repeatInterval > 0` 且 `repeatFixedDelay=true` | FIXED_DELAY | 从上次执行结束起固定延迟 |
| 都未设置 | ONCE | 触发一次 |

通用字段：`maxExecutionCount`（最大执行次数，0=无限）、`minScheduleTime`/`maxScheduleTime`（调度时间窗口）、`misfireThreshold`（misfire 阈值）、`pauseCalendars`（暂停日历）。

---

## 四、核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopJobSchedule | `nop_job_schedule` | 调度定义（持久化的作业配置） |
| NopJobFire | `nop_job_fire` | 触发批次（一次触发产生一条） |
| NopJobTask | `nop_job_task` | 执行任务（一个 fire 可拆分多个 task，支持分片） |

**NopJobSchedule 关键字段**：`triggerType`、`cronExpr`、`repeatIntervalMs`、`executorKind`、`jobParams`、`scheduleStatus`、`blockStrategy`、`timeoutSeconds`、`nextFireTime`、`partitionIndex`。

**NopJobFire 关键字段**：`triggerSource`（SCHEDULE/MANUAL/RECOVERY）、`fireStatus`、`durationMs`。

**NopJobTask 关键字段**：`taskStatus`、`shardingIndex`/`shardingTotal`、`progress`。

## 架构

```
Coordinator (协调器)                    Worker (工作者)
┌─────────────────┐                   ┌─────────────────┐
│ NopJobSchedule   │──fire──>          │ NopJobTask       │
│ NopJobFire       │                   │ taskPayload      │
│ CRON/调度计算     │                   │ 执行 → result    │
└─────────────────┘                   └─────────────────┘
```

调度流水线（每 5 秒扫描一次）：
1. **Planner**：扫描到期的 Schedule → 创建 Fire → 推进 nextFireTime
2. **Dispatcher**：扫描 WAITING 的 Fire → 创建 Task
3. **Worker**：认领 WAITING 的 Task → 解析 `nopJobInvoker_<executorKind>` → 执行
4. **Completion**：汇总 Task 结果 → 更新 Fire/Schedule 状态
5. **TimeoutChecker**：标记超时的 RUNNING Task

---

## 五、配置项参考（分布式模式）

所有配置项通过 `@InjectValue("@cfg:...")` 注入，默认值已内置，按需覆盖。

### 协调器（Coordinator）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.job.coordinator.planner.scan-interval-ms` | 5000 | Planner 扫描间隔（毫秒） |
| `nop.job.coordinator.planner.batch-size` | 100 | Planner 每次扫描的记录数 |
| `nop.job.coordinator.planner.lock-timeout-ms` | 60000 | Planner 乐观锁超时 |
| `nop.job.coordinator.dispatcher.scan-interval-ms` | 5000 | Dispatcher 扫描间隔 |
| `nop.job.coordinator.dispatcher.batch-size` | 100 | Dispatcher 批量大小 |
| `nop.job.coordinator.dispatcher.lock-timeout-ms` | 60000 | Dispatcher 锁超时 |
| `nop.job.coordinator.completion.scan-interval-ms` | 5000 | Completion 扫描间隔 |
| `nop.job.coordinator.completion.batch-size` | 100 | Completion 批量大小 |
| `nop.job.coordinator.timeout.scan-interval-ms` | 5000 | 超时检查器扫描间隔 |
| `nop.job.coordinator.timeout.batch-size` | 100 | 超时检查器批量大小 |
| `nop.job.coordinator.dispatch-timeout-ms` | 300000 | 调度超时（5 分钟） |
| `nop.job.coordinator.execution-timeout-ms` | -1 | 全局执行超时（-1 不限制，单作业可用 `timeoutSeconds` 字段覆盖） |
| `nop.job.coordinator.assigned-partitions` | （空） | 本节点负责的分区，逗号分隔 |

### 工作者（Worker）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.job.worker.scan-interval-ms` | 5000 | Worker 扫描间隔 |
| `nop.job.worker.batch-size` | 100 | Worker 每次认领的任务数 |
| `nop.job.worker.lock-timeout-ms` | 60000 | Worker 锁超时 |
| `nop.job.worker.assigned-partitions` | （空） | 本节点负责的分区 |
| `nop.job.worker.max-concurrency` | 0 | 最大并发执行数（0=不限） |

### 集群（可选）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.job.cluster.enable-cluster` | false | 是否启用集群模式 |
| `nop.job.cluster.service-name` | （空） | 集群服务名 |
| `nop.job.cluster.stable-window-ms` | 30000 | 分区稳定窗口 |

---

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-job-api` | 公共接口、DTO、SPI（`IJobScheduler`、`IJobInvoker`、`JobSpec`、`TriggerSpec`） |
| `nop-job-core` | 触发器计算 + 本地内存调度器 `LocalJobScheduler` |
| `nop-job-local` | YAML 配置加载 + IoC bean 方法调用桥接（无 DB 依赖） |
| `nop-job-coordinator` | 分布式协调器（Planner/Dispatcher/Completion/Timeout） |
| `nop-job-worker` | 分布式工作者（扫描任务、解析 invoker、执行） |
| `nop-job-dao` | ORM 实体与 Store |
| `nop-job-service` | BizModel、内置 invoker（rpc/rpcBroadcast/test） |
| `nop-job-web` | XMeta、AMIS 管理页面 |
| `nop-job-app` | 可独立运行的示例应用 |
| `nop-job-retry-adapter` | 与 nop-retry 集成的适配器（可选） |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-job/model/nop-job.orm.xml` |
| IJobScheduler 接口 | `nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobScheduler.java` |
| LocalJobScheduler | `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java` |
| IJobInvoker SPI | `nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/IJobInvoker.java` |
| DefaultJobInvokerResolver | `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/DefaultJobInvokerResolver.java` |
| JobCoordinator | `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java` |
| BeanMethodJobInvoker | `nop-job/nop-job-local/src/main/java/io/nop/job/local/executor/BeanMethodJobInvoker.java` |
| LocalJobConfigLoader | `nop-job/nop-job-local/src/main/java/io/nop/job/local/config/LocalJobConfigLoader.java` |
| 本地模式测试（core） | `nop-job/nop-job-core/src/test/java/io/nop/job/core/TestLocalJobScheduler.java` |
| 本地配置测试（local） | `nop-job/nop-job-local/src/test/java/io/nop/job/local/` |

## 相关文档

- `../nop-retry.md`
- `../reusable-modules-overview.md`
- `../../02-core-guides/concurrency-and-transactions.md`（分布式 scanner 的乐观锁与事务隔离模式）
- `../../03-runbooks/non-bizmodel-orm-access.md`（定时任务中的非 BizModel DAO 访问模式）
