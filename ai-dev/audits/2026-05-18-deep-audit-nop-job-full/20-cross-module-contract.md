# 维度20：跨模块契约一致性

## 第 1 轮（初审）

### 审计范围

- **目标模块**：nop-job（含 10 个子模块）
- **外部依赖**：nop-cluster-core、nop-retry-engine、nop-orm (ICrudBiz)、nop-api-core (IRpcCall)
- **审核维度**：接口稳定性、DTO 匹配性、事件契约、配置一致性、硬编码依赖

---

### 发现 1：retry-adapter 硬编码服务调用契约（P2）

**文件**：`nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java`
**行号**：20-21, 38-42

**证据**（10 行）：
```java
// L20-L21
static final String SERVICE_NAME = "NopJobService";
static final String SERVICE_METHOD = "fireJob";

// L38-L42
IRetryTask task = retryEngine.newRetryTask(SERVICE_NAME, SERVICE_METHOD)
        .withPolicyId(event.getRetryPolicyId())
        .withIdempotentId(event.getJobFireId())
        .withNamespaceId(event.getNamespaceId())
        .withGroupId(event.getGroupId());
```

**严重程度**：P2

**现状**：
- `NopRetryJobRetryBridge` 硬编码服务名 `"NopJobService"` 和方法名 `"fireJob"`。
- 在 `nop-job-dao` 中定义了 `INopJobFireBiz` 和 `INopJobScheduleBiz`，但二者均不包含 `fireJob` 方法。
- retry-adapter 期望通过 RPC 调用 `NopJobService.fireJob`，而项目中不存在名为 `NopJobService` 的 Biz 接口或 `@BizMutation("fireJob")` 的方法定义。
- 测试代码 `TestNopRetryJobRetryBridge` (L50-51) 同样依赖此硬编码值。

**风险**：
- 重试任务提交后无法路由到正确的服务方法，`nop-retry-engine` 实际调用时将因找不到 `NopJobService.fireJob` 而失败。
- 服务名或方法名发生变更时无编译时保护，只能通过运行时失败暴露。
- 测试代码与生产代码耦合于同一硬编码常量，无法独立验证契约正确性。

**建议**：
1. 在 `nop-job-api` 或 `nop-job-dao` 中定义 `fireJob` 的 Biz 接口方法，或在已有接口（如 `INopJobFireBiz`）中增加 `@BizMutation("fireJob")` 方法。
2. 将 `SERVICE_NAME` 和 `SERVICE_METHOD` 提取为 `nop-job-api` 中的常量类，或通过 `@InjectValue` 配置化。
3. 更新测试代码引用统一常量。

**误报排除**：已全局搜索 `NopJobService` 和 `fireJob`，确认项目中不存在对应的 Biz 接口定义或 `@BizMutation` 声明。

---

### 发现 2：配置项缺少默认配置文件与文档（P3）

**文件**：
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPartitionResolver.java`（L33-46）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`（L57-72）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`（L84-99）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`（L56-71）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`（L85-95）
- `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`（L78-100）

**证据**（以 `JobPartitionResolver` 为例，8 行）：
```java
// L33-46
@InjectValue("@cfg:nop.job.cluster.service-name|")
public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
}

@InjectValue("@cfg:nop.job.cluster.enable-cluster|false")
public void setEnableCluster(boolean enableCluster) {
    this.enableCluster = enableCluster;
}

@InjectValue("@cfg:nop.job.cluster.stable-window-ms|30000")
public void setStableWindowMs(long stableWindowMs) {
    this.stableWindowMs = stableWindowMs;
}
```

**严重程度**：P3

**现状**：
- 6 个类共定义了 23 个 `@InjectValue` 配置项（`nop.job.*` 前缀）。
- 所有配置项均使用 `|defaultValue` 语法定义内联默认值，未在 yaml/properties 文件中声明。
- 全局搜索 `nop-job` 目录下的 `*.yaml`、`*.properties`、`*.beans` 文件，未找到任何 `nop.job.` 配置项的显式定义。
- 配置项分散在 6 个不同类中，无集中管理。

**风险**：
- 用户不知道存在哪些可调配置项，只能通过阅读源码发现。
- 默认值在代码中内联定义，修改默认值需要改代码重新编译，无法通过配置文件覆盖。
- 多个 Scanner 类的配置模式高度相似但各自独立定义，容易遗漏同步更新。

**建议**：
1. 在 `nop-job-app` 或 `nop-job-coordinator` 的 `src/main/resources` 中创建 `application-default.yaml`，集中声明所有 `nop.job.*` 配置项及默认值。
2. 考虑抽取配置对象类（如 `JobScannerConfig`），将通用配置（scan-interval-ms、batch-size、lock-timeout-ms、assigned-partitions）统一管理。
3. 在 `docs-for-ai` 或模块 README 中补充配置项说明。

**误报排除**：已搜索所有 yaml/properties/beans 文件，确认无 `nop.job.*` 配置项的显式定义。Nop 平台的 `@cfg:` 机制支持运行时动态配置，但缺少文档化的默认配置文件仍是一个可维护性问题。

---

### 发现 3：跨模块 RPC Header 契约未定义为常量（P2）

**文件**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`
**行号**：26-30, 70-83

**证据**（9 行）：
```java
// L26-30 (Javadoc)
/**
 * Builds one NopJobTask per registered service instance for broadcast RPC.
 * Each task carries the target host, shardingIndex, and shardingTotal in its payload
 * so that the worker's execution context can inject the {@code nop-svc-target-host} header
 * for per-instance routing.
 */

// L70-83 (task building)
for (int i = 0; i < total; i++) {
    ServiceInstance instance = instances.get(i);
    tasks.add(buildTask(fire, i, total, instance));
}
```

**严重程度**：P2

**现状**：
- `RpcBroadcastTaskBuilder` 的 Javadoc 提到 `nop-svc-target-host` header，但该字符串仅存在于注释中，未定义为常量。
- Header 的实际注入发生在 worker 端的执行上下文中（`TestRpcJobInvoker` 测试中可见相关逻辑），但 coordinator 和 worker 之间没有共享的常量定义。
- 全局搜索 `"nop-svc-target-host"` 仅在 Javadoc 注释中出现，无 Java 常量或接口声明。

**风险**：
- Header 名称拼写不一致时，任务路由静默失效（coordinator 写入、worker 读取，任一端拼写错误都无法编译期发现）。
- 新开发者无法通过接口契约理解跨模块协作机制。
- RPC 框架的 header 机制变更时，缺乏统一的修改入口。

**建议**：
1. 在 `nop-job-api` 中新增常量类（如 `JobRpcHeaders`），定义 `String TARGET_HOST = "nop-svc-target-host"`。
2. Coordinator 端和 Worker 端均引用该常量。
3. 在 `IJobExecutionContext` 接口或其实现中增加 `getTargetHost()` / `setTargetHost()` 方法。

**误报排除**：已全局搜索 `nop-svc-target-host`、`NOP_SVC_TARGET_HOST`，确认无 Java 常量定义。该 header 名称仅出现在 Javadoc 注释中。

---

### 发现 4：已废弃接口仍暴露在 API 公共模块（P2）

**文件**：`nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobScheduler.java`
**行号**：23-27

**证据**（5 行）：
```java
// L23-27
/**
 * @deprecated This legacy scheduler interface is being replaced by the new coordinator/worker architecture.
 */
@Deprecated
public interface IJobScheduler {
    List<String> getJobNames();
    ...
}
```

**严重程度**：P2

**现状**：
- `IJobScheduler` 接口标记为 `@Deprecated`，但仍然位于 `nop-job-api` 模块中，对外暴露。
- 唯一实现 `LocalJobScheduler`（`nop-job-core` 模块）同样标记为 `@Deprecated`。
- 新架构使用 coordinator/worker 模式（`IJobPlannerScanner`、`IJobDispatcherScanner` 等），与旧接口无关联。
- `nop-job-api` 的 `pom.xml` 仅依赖 `nop-api-core`，是纯 API 模块，应只包含稳定契约。

**风险**：
- 外部消费者可能误用已废弃接口，导致后续移除时产生破坏性变更。
- API 模块膨胀，包含不应再使用的接口。
- `@Deprecated` 注解缺少 `forRemoval` 属性和替代方案指引。

**建议**：
1. 评估是否有外部模块依赖 `IJobScheduler`（通过全局引用搜索确认）。
2. 如无外部依赖，将其从 `nop-job-api` 移至 `nop-job-core` 的 internal 包中，或完全删除。
3. 如需保留兼容性，添加 `@Deprecated(forRemoval = true, since = "2.0")` 并在 Javadoc 中明确指向新架构的入口。

**误报排除**：已确认接口确实标记 `@Deprecated` 且位于公共 API 模块。`LocalJobScheduler` 同样废弃，新架构无使用此接口。

---

### 发现 5：Biz 接口与 retry-adapter 调用契约不匹配（P2）

**文件**：
- `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobFireBiz.java`（L11-16）
- `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java`（L13-31）
- `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java`（L20-21）

**证据**（10 行）：
```java
// INopJobFireBiz.java (L11-16)
public interface INopJobFireBiz extends ICrudBiz<NopJobFire>{
    @BizMutation("cancelFire")
    void cancelFire(@Name("id") String id, IServiceContext context);

    @BizMutation("rerunFire")
    void rerunFire(@Name("id") String id, IServiceContext context);
}

// NopRetryJobRetryBridge.java (L20-21)
static final String SERVICE_NAME = "NopJobService";
static final String SERVICE_METHOD = "fireJob";

// NopRetryJobRetryBridge.java (L44-50) - 重试时传递的数据
data.put("jobFireId", event.getJobFireId());
data.put("jobScheduleId", event.getJobScheduleId());
data.put("jobName", event.getJobName());
data.put("executorKind", event.getExecutorKind());
data.put("errorCode", event.getErrorCode());
data.put("errorMessage", event.getErrorMessage());
```

**严重程度**：P2

**现状**：
- retry-adapter 调用 `NopJobService.fireJob`，传递 6 个字段的 Map 作为请求体。
- `INopJobFireBiz` 仅定义 `cancelFire` 和 `rerunFire`，不含 `fireJob`。
- `INopJobScheduleBiz` 定义了 `triggerNow`，其参数为 `(String id, Map<String, Object> overrideParams, IServiceContext context)`，与 retry-adapter 传递的数据结构不匹配。
- Biz 接口面向 GraphQL/REST 暴露，而 retry-adapter 通过 RPC 调用，二者使用不同的服务发现机制。

**风险**：
- 重试引擎在尝试调用 `fireJob` 时将找不到对应的服务方法，导致重试静默失败。
- `JobFireFailedEvent` 传递的错误信息（errorCode、errorMessage）无法被 `cancelFire` 或 `rerunFire` 的参数模型接收。
- 消费者在 `NopJobFireBizModel` 中添加 `fireJob` 实现时，需要手动确保与 retry-adapter 的数据契约一致。

**建议**：
1. 在 `INopJobFireBiz` 或新的 `INopJobRetryBiz` 接口中增加 `@BizMutation("fireJob")` 方法，参数与 retry-adapter 传递的 data Map 字段对齐。
2. 或修改 `NopRetryJobRetryBridge` 调用已有的 `rerunFire` 方法，并将 `JobFireFailedEvent` 字段映射到 `rerunFire` 的参数模型。
3. 无论哪种方案，都需要在 `NopJobFireBizModel` 中实现对应方法。

**误报排除**：已确认 `INopJobFireBiz` 和 `INopJobScheduleBiz` 中均无 `fireJob` 方法。`NopJobFireBizModel` 实现类中同样无此方法。

---

### 发现 6：告警事件接口缺少发布端契约（P3）

**文件**：
- `nop-job/nop-job-api/src/main/java/io/nop/job/api/alarm/IJobAlarmHandler.java`（L3-7）
- `nop-job/nop-job-api/src/main/java/io/nop/job/api/alarm/JobAlarmEvent.java`（L3-33）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`（L71-73, 167-177）

**证据**（10 行）：
```java
// IJobAlarmHandler.java (L3-7)
public interface IJobAlarmHandler {
    void onFireFailed(JobAlarmEvent event);
    void onFireTimeout(JobAlarmEvent event);
}

// JobAlarmEvent.java (L3-12)
public class JobAlarmEvent {
    private final String jobFireId;
    private final String jobScheduleId;
    private final String jobName;
    private final String namespaceId;
    private final String groupId;
    private final String errorCode;
    private final String errorMessage;
    private final Long durationMs;
    ...
}

// JobTimeoutCheckerImpl.java (L71-73) - 注入方式非 @Inject
public void setAlarmHandler(IJobAlarmHandler alarmHandler) {
    this.alarmHandler = alarmHandler;
}

// JobTimeoutCheckerImpl.java (L167-177) - 发布调用
if (alarmHandler != null) {
    JobAlarmEvent alarmEvent = new JobAlarmEvent(
            fire.getSid(), fire.getJobScheduleId(),
            schedule.getJobName(), schedule.getNamespaceId(),
            schedule.getGroupId(), null, null, null);
    alarmHandler.onFireTimeout(alarmEvent);
}
```

**严重程度**：P3

**现状**：
- `IJobAlarmHandler` 定义了 `onFireFailed` 和 `onFireTimeout` 两个方法，但二者使用相同的 `JobAlarmEvent` 类型。
- `JobAlarmEvent.durationMs` 字段仅在 timeout 场景有意义，在 failed 场景传 `null`。
- 告警处理器的注入在 `JobTimeoutCheckerImpl` 中使用普通 setter（非 `@Inject`），依赖 beans.xml 显式配置。
- beans.xml 中注册了 `LoggingJobAlarmHandler` 作为默认实现，但没有文档说明如何扩展。
- `IJobAlarmHandler.onFireFailed` 在当前代码中未被任何地方调用（timeout checker 只调用 `onFireTimeout`）。

**风险**：
- `onFireFailed` 方法是死代码，消费者实现后永远不会被触发。
- 新增告警处理器时无法确定正确集成方式（需要阅读 beans.xml 了解注入机制）。
- 未来增加 `onFireFailed` 的发布点时，`JobAlarmEvent` 的 `durationMs` 字段语义不清。

**建议**：
1. 确认 `onFireFailed` 是否有预期调用场景；如无，移除或标注 `@Deprecated`。
2. 如需保留，在 `JobCompletionProcessorImpl` 的失败处理逻辑中增加 `onFireFailed` 调用。
3. 在 `IJobAlarmHandler` 的 Javadoc 中明确两个方法的触发时机和事件字段语义。

**误报排除**：已搜索 `alarmHandler.onFireFailed` 和 `IJobAlarmHandler.onFireFailed` 的所有调用点，确认当前无任何发布端调用此方法。`onFireTimeout` 仅在 `JobTimeoutCheckerImpl` 中被调用。

---

### 发现 7：IJobInvoker 与 IRpcCall 的返回类型契约差异未适配（P2）

**文件**：
- `nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/IJobInvoker.java`（L15-30）
- `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/rpc/IRpcCall.java`（L16-35）
- `nop-retry/nop-retry-api/src/main/java/io/nop/retry/api/IRetryTask.java`（L1-56）

**证据**（10 行）：
```java
// IJobInvoker.java (L15-23)
public interface IJobInvoker {
    CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx);
    CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx);
}

// IRpcCall.java (L16-24)
public interface IRpcCall {
    CompletionStage<ApiResponse<?>> callAsync(ApiRequest<?> request, ICancelToken cancelToken);
    default ApiResponse<?> call(ApiRequest<?> request, ICancelToken cancelToken) {
        return FutureHelper.syncGet(callAsync(request, cancelToken));
    }
}

// IRetryTask extends IRpcCall (IRetryTask.java L12)
public interface IRetryTask extends IRpcCall { ... }

// NopRetryJobRetryBridge.java (L52-55) - 构造 ApiRequest 传给 IRpcCall
ApiRequest<Map<String, Object>> request = new ApiRequest<>();
request.setData(data);
task.callAsync(request, null)
```

**严重程度**：P2

**现状**：
- `IJobInvoker` 接受 `IJobExecutionContext`，返回 `CompletionStage<JobFireResult>`。
- `IRpcCall`（`IRetryTask` 的父接口）接受 `ApiRequest<?>`，返回 `CompletionStage<ApiResponse<?>>`。
- retry-adapter 构造 `ApiRequest<Map<String, Object>>` 传给 `IRetryTask.callAsync`，期望最终由某个 RPC 服务端（`NopJobService.fireJob`）处理。
- 但 `IJobInvoker` 的实现（如 `RpcJobInvoker`）将 `IJobExecutionContext` 转换为 RPC 调用，返回类型是 `JobFireResult` 而非 `ApiResponse<?>`。
- 两条调用链（直接调用 `IJobInvoker` vs 通过 retry-adapter 的 RPC 重试）使用不同的参数和返回类型，没有适配层。

**风险**：
- retry-adapter 通过 RPC 重试时，服务端需要额外实现 `fireJob` 方法来接收 `ApiRequest<Map>` 并返回 `ApiResponse<?>`，这与 `IJobInvoker` 的调用路径完全独立。
- 两条路径的业务逻辑可能不一致（直接调用走 `IJobInvoker`，重试走 `fireJob` RPC）。
- `JobFireResult` 到 `ApiResponse<?>` 的转换逻辑未定义。

**建议**：
1. 明确重试路径的服务端实现位置（是在 `NopJobFireBizModel` 还是独立 BizModel 中）。
2. 确保 `fireJob` 的 RPC 实现复用 `IJobInvoker` 的核心逻辑，避免两条路径行为不一致。
3. 如果 `IJobInvoker` 的实现已经是 RPC 代理（如 `RpcJobInvoker`），考虑让重试直接调用同一 RPC 接口而非硬编码新的服务名/方法名。

**误报排除**：已确认 `IJobInvoker` 和 `IRpcCall` 是不同的接口，参数和返回类型不兼容。`RpcJobInvoker` 使用 `IRpcServiceInvoker`，与 `IRetryTask` 的 `IRpcCall` 是不同抽象。

---

### 发现 8：多个 Scanner 配置项模式重复无抽象（P3）

**文件**：
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`（L57-72）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`（L56-71）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java`（L84-99）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`（L85-95）
- `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`（L78-100）

**证据**（以 3 个 Scanner 为例，10 行）：
```java
// JobPlannerScannerImpl.java
@InjectValue("@cfg:nop.job.coordinator.planner.scan-interval-ms|5000")
@InjectValue("@cfg:nop.job.coordinator.planner.batch-size|100")
@InjectValue("@cfg:nop.job.coordinator.planner.lock-timeout-ms|60000")
@InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")

// JobDispatcherScannerImpl.java
@InjectValue("@cfg:nop.job.coordinator.dispatcher.scan-interval-ms|5000")
@InjectValue("@cfg:nop.job.coordinator.dispatcher.batch-size|100")
@InjectValue("@cfg:nop.job.coordinator.dispatcher.lock-timeout-ms|60000")
@InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")

// JobWorkerScannerImpl.java
@InjectValue("@cfg:nop.job.worker.scan-interval-ms|5000")
@InjectValue("@cfg:nop.job.worker.batch-size|100")
@InjectValue("@cfg:nop.job.worker.lock-timeout-ms|60000")
@InjectValue("@cfg:nop.job.worker.assigned-partitions|")
```

**严重程度**：P3

**现状**：
- 5 个 Scanner/Checker 类各自定义了 3-4 个相同语义的配置项（scan-interval-ms、batch-size、lock-timeout-ms、assigned-partitions）。
- 配置项仅前缀不同（`planner.*`、`dispatcher.*`、`worker.*`），默认值完全一致。
- 没有公共基类或配置对象抽取这些共享配置。
- 修改默认值或增加新配置项需要同步修改 5 个类。

**风险**：
- 默认值变更时容易遗漏某个 Scanner。
- 代码重复增加维护负担，违反 DRY 原则。
- 新增 Scanner 类型时需要重新定义相同配置。

**建议**：
1. 抽取 `AbstractJobScannerConfig` 基类或 `JobScannerConfig` 数据类，封装 scan-interval-ms、batch-size、lock-timeout-ms、assigned-partitions。
2. 各 Scanner 通过组合或继承复用配置定义。
3. 配置前缀可参数化（如通过构造函数传入 `"nop.job.coordinator.planner"`）。

**误报排除**：已确认 5 个类的配置项字段名、类型和默认值高度一致，属于有意义的重复。

---

## 审计汇总

| # | 严重程度 | 发现 | 关键文件 |
|---|---------|------|---------|
| 1 | P2 | retry-adapter 硬编码服务调用契约 | NopRetryJobRetryBridge.java |
| 2 | P3 | 配置项缺少默认配置文件与文档 | 6 个 Scanner/Resolver 类 |
| 3 | P2 | 跨模块 RPC Header 契约未定义为常量 | RpcBroadcastTaskBuilder.java |
| 4 | P2 | 已废弃接口仍暴露在 API 公共模块 | IJobScheduler.java |
| 5 | P2 | Biz 接口与 retry-adapter 调用契约不匹配 | INopJobFireBiz.java / INopJobScheduleBiz.java |
| 6 | P3 | 告警事件接口缺少发布端契约 | IJobAlarmHandler.java |
| 7 | P2 | IJobInvoker 与 IRpcCall 返回类型契约差异未适配 | IJobInvoker.java / IRpcCall.java |
| 8 | P3 | 多个 Scanner 配置项模式重复无抽象 | 5 个 Scanner 类 |

**统计**：P2 × 5，P3 × 3，共 8 个发现。

**优先修复建议**：
1. **发现 1+5（关联）**：定义 `fireJob` Biz 接口方法并实现，消除 retry-adapter 的硬编码契约断裂。这是最关键的修复——当前重试机制在生产环境中实际无法工作。
2. **发现 3**：定义 `JobRpcHeaders` 常量类，防止跨模块 header 名称拼写不一致导致路由静默失效。
3. **发现 4**：评估并清理 `IJobScheduler` 废弃接口，减少 API 表面积。
4. **发现 2+8（关联）**：创建默认配置文件并抽取公共配置基类。
5. **发现 6+7**：补充事件发布端契约和接口适配文档。
