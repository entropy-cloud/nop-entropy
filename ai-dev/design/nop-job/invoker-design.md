# nop-job Invoker 设计文档

**日期**：2026-05-17（v5 更新：executorKind 统一路由，废弃 executorRef）
**范围**：`nop-job` 的 IJobInvoker / IJobTaskBuilder / IJobWorker 设计
**状态**：草案 v5，待确认

---

## 一、核心结论

1. **不新增模块**。Invoker / TaskBuilder 实现直接放在 `nop-job-service`，通过 IoC 注册。
2. **executorKind 是唯一路由字段**。扩展 `job/executor-kind` dict 为 `test`/`rpc`/`rpcBroadcast`，Resolver 据此查找 `nopJobInvoker_{executorKind}` bean。
3. **找不到 invoker 时抛异常**，不返回 null。
4. **废弃 executorRef**。executorKind 直接确定调用方式，无需额外 ref 字段。
5. **不需要 executorMethod**。RPC 调用的 serviceName、serviceMethod 等参数由 invoker 自行从 `jobParams` 中解析。
6. **RPC invoker 注入 `IRpcServiceInvoker`**（平台已有的 `nopRpcServiceInvoker` bean），利用已有服务发现和负载均衡。
7. **RPC 调用参数由 header + data 两部分构成**，都从 `jobParams` 解析。`jobParams` 中可包含 `headers` 子对象指定 RPC header。
8. **广播/分片通过 `IJobTaskBuilder` 接口扩展**。Dispatcher 查找 `nopJobTaskBuilder_{dispatchMode}` 或 `nopJobTaskBuilder_{executorKind}`，找不到则用默认的单 task 构建。**`dispatchMode` 优先于 `executorKind` 路由**（Plan 213 引入），详见 §3.6。

---

## 二、简化后的设计

### 2.1 Resolver 逻辑

```
实体存储:
  NopJobSchedule
  ├── executorKind   = "rpc"           ← 实体列(string)，页面下拉框选择
  ├── jobParams      = {               ← JSON 列，存储 RPC 调用参数
  │     "serviceName": "myService",
  │     "serviceMethod": "doWork",
  │     "headers": {"nop-svc-route": "myService:^1.2"}
  │   }
  └── executorRef    = (废弃)

Fire 触发时 (快照):
  NopJobFire
  ├── executorKind       = "rpc"                        ← string，从 schedule 拷贝
  ├── jobParamsSnapshot  = { "serviceName": "..." }     ← JSON，从 schedule 拷贝

Resolver 优先级:
  1. fire.executorKind（优先，保证历史一致性）
  2. schedule.executorKind（fallback）
  3. 拼接 bean name → "nopJobInvoker_rpc"
  4. BeanContainer.tryGetBean → 找不到抛异常
```

### 2.2 DefaultJobInvokerResolver 修改

```java
public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire) {
        String executorKind = resolveExecutorKind(schedule, fire);
        if (executorKind == null || executorKind.isBlank())
            throw new NopException(ERR_JOB_EXECUTOR_KIND_EMPTY);

        String beanName = INVOKER_PREFIX + executorKind;
        IJobInvoker invoker = BeanContainer.tryGetBean(beanName);
        if (invoker == null)
            throw new NopException(ERR_JOB_INVOKER_NOT_FOUND)
                .param(ARG_EXECUTOR_KIND, executorKind)
                .param(ARG_BEAN_NAME, beanName);
        return invoker;
    }

    protected String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
        String kind = fire.getExecutorKind();
        return kind != null ? kind : schedule.getExecutorKind();
    }
}
```

### 2.3 RpcJobInvoker

**jobParams 结构：**

```json
{
  "serviceName": "order-service",
  "serviceMethod": "processOrder",
  "headers": {
    "nop-svc-route": "order-service:^1.2",
    "nop-svc-target-host": "10.0.0.3:8080",
    "nop-svc-tags": "zone:east"
  },
  "data": {
    "orderId": "12345",
    "action": "process"
  }
}
```

- `serviceName`（必填）：RPC 目标服务名，用于服务发现
- `serviceMethod`（可选）：RPC 方法名，默认 `invokeJob`
- `headers`（可选）：RPC 请求头，传递给 `ApiRequest.setHeaders()`。支持所有平台内置 header：
  - `nop-svc-target-host`：指定特定实例（精确匹配）
  - `nop-svc-route`：版本路由（语义版本号过滤）
  - `nop-svc-tags`：标签过滤
  - `nop-app-zone`：zone 过滤
  - 其他自定义 header
- `data`（可选）：RPC 请求体数据。不指定时由 invoker 自动构建（包含 jobName、jobGroup 等）

```java
public class RpcJobInvoker implements IJobInvoker {
    @Inject
    protected IRpcServiceInvoker rpcServiceInvoker;

    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> params = jobCtx.getJobParams();
        String serviceName = getRequiredParam(params, "serviceName");
        String serviceMethod = (String) params.getOrDefault("serviceMethod", "invokeJob");

        ApiRequest<Object> request = new ApiRequest<>();
        applyHeaders(request, params);
        request.setData(resolveData(params, jobCtx));

        return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null)
            .thenApply(this::toFireResult);
    }

    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> params = jobCtx.getJobParams();
        String serviceName = getRequiredParam(params, "serviceName");

        ApiRequest<Object> request = new ApiRequest<>();
        applyHeaders(request, params);
        request.setData(resolveData(params, jobCtx));

        return rpcServiceInvoker.invokeAsync(serviceName, "cancelJob", request, null)
            .thenApply(ApiResponse::isOk);
    }

    @SuppressWarnings("unchecked")
    private void applyHeaders(ApiRequest<Object> request, Map<String, Object> params) {
        Map<String, Object> headers = (Map<String, Object>) params.get("headers");
        if (headers != null) {
            headers.forEach(request::setHeader);
        }
    }

    @SuppressWarnings("unchecked")
    private Object resolveData(Map<String, Object> params, IJobExecutionContext jobCtx) {
        Object data = params.get("data");
        if (data != null)
            return data;
        Map<String, Object> autoData = new HashMap<>();
        autoData.put("jobName", jobCtx.getJobName());
        autoData.put("jobGroup", jobCtx.getJobGroup());
        autoData.put("instanceId", jobCtx.getInstanceId());
        autoData.put("execCount", jobCtx.getExecCount());
        return autoData;
    }

    private JobFireResult toFireResult(ApiResponse<?> response) {
        if (!response.isOk()) {
            ErrorBean error = new ErrorBean();
            error.setStatus(response.getStatus());
            error.setErrorCode(response.getCode());
            error.setDescription(response.getMsg());
            return JobFireResult.ERROR(error);
        }
        Object data = response.getData();
        if (data instanceof JobFireResult)
            return (JobFireResult) data;
        return JobFireResult.CONTINUE;
    }

    private String getRequiredParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (!(value instanceof String) || ((String) value).isBlank())
            throw new NopException(ERR_RPC_INVOKER_MISSING_PARAM).param(ARG_PARAM_NAME, key);
        return (String) value;
    }
}
```

### 2.4 测试 Invoker

```java
// bean id: nopJobInvoker_test
public class NopE2eTestJobInvoker implements IJobInvoker {
    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        return CompletableFuture.completedFuture(JobFireResult.CONTINUE(0L));
    }
    @Override
    public CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx) {
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }
}
```

### 2.5 Bean 注册

```xml
<!-- app-service.beans.xml -->
<bean id="nopJobInvoker_test" class="io.nop.job.service.executor.NopE2eTestJobInvoker"/>
<bean id="nopJobInvoker_rpc" class="io.nop.job.service.executor.RpcJobInvoker"/>
<bean id="nopJobTaskBuilder_rpcBroadcast" class="io.nop.job.service.executor.RpcDistributedTaskBuilder"/>
```

---

## 三、广播 Task 构建机制

### 3.1 问题

当前 Dispatcher 每次触发只创建 1 个 task（hardcoded `taskNo=1`）。广播模式下需要为每个服务实例创建独立 task，每个 task 的结果单独记录。

### 3.2 设计：IJobTaskBuilder 接口

```java
// nop-job-api 或 nop-job-coordinator 中
public interface IJobTaskBuilder {
    List<NopJobTask> buildTasks(NopJobFire fire);
}
```

**默认实现：**

```java
// bean id: nopJobTaskBuilder_default (ioc:default=true)
public class DefaultJobTaskBuilder implements IJobTaskBuilder {
    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        return List.of(buildSingleTask(fire));
    }

    private NopJobTask buildSingleTask(NopJobFire fire) {
        // 当前 buildTask 的逻辑移到这里
        ...
    }
}
```

**广播/分片实现（统一 RpcDistributedTaskBuilder）：**

同一个 builder 同时覆盖广播和分片两种场景。通过向每个 task 的 RPC header 注入 `shardingIndex`（0..N-1）和 `shardingTotal`（= 实例数），接收方从 header 即可判断自己在哪个分片。`shardingTotal` 直接采用服务实例个数，无需额外配置。

```java
// bean id: nopJobTaskBuilder_rpcBroadcast
public class RpcDistributedTaskBuilder implements IJobTaskBuilder {
    @Inject
    protected IServerChooser<ApiRequest<?>> serverChooser;

    @Override
    public List<NopJobTask> buildTasks(NopJobFire fire) {
        Map<String, Object> params = fire.getJobParamsSnapshotComponent().get_jsonMap();
        String serviceName = (String) params.get("serviceName");

        List<ServiceInstance> instances = serverChooser.getServers(serviceName, new ApiRequest<>());
        if (instances.isEmpty())
            throw new NopException(ERR_BROADCAST_NO_INSTANCES)
                .param(ARG_SERVICE_NAME, serviceName);

        List<NopJobTask> tasks = new ArrayList<>(instances.size());
        int shardingTotal = instances.size();
        for (int i = 0; i < shardingTotal; i++) {
            NopJobTask task = buildTask(fire, i + 1, i, shardingTotal, instances.get(i));
            tasks.add(task);
        }
        return tasks;
    }

    private NopJobTask buildTask(NopJobFire fire, int taskNo,
                                  int shardingIndex, int shardingTotal,
                                  ServiceInstance instance) {
        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(taskNo);
        task.setTaskStatus(TASK_STATUS_WAITING);
        task.setPartitionIndex(shardingIndex);

        Map<String, Object> payload = new HashMap<>();
        payload.put("jobFireId", fire.getJobFireId());
        payload.put("executorKind", fire.getExecutorKind());
        payload.put("jobParamsSnapshot", injectShardingHeaders(fire, instance, shardingIndex, shardingTotal));
        task.getTaskPayloadComponent().set_jsonValue(payload);

        // ... timestamp fields
        return task;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> injectShardingHeaders(NopJobFire fire, ServiceInstance instance,
                                                       int shardingIndex, int shardingTotal) {
        Map<String, Object> params = new HashMap<>(
            emptyIfNull(fire.getJobParamsSnapshotComponent().get_jsonMap()));
        Map<String, Object> headers = (Map<String, Object>) params.getOrDefault("headers", new HashMap<>());
        headers = new HashMap<>(headers);
        headers.put("nop-svc-target-host", instance.getHost());
        headers.put("nop-sharding-index", shardingIndex);
        headers.put("nop-sharding-total", shardingTotal);
        params.put("headers", headers);
        return params;
    }
}
```

接收方从 RPC header 中读取分片信息：

```java
// 在 Worker 或业务服务中
int shardingIndex = (int) request.getHeader("nop-sharding-index");    // 0..N-1
int shardingTotal = (int) request.getHeader("nop-sharding-total");    // N
```

- **广播模式**：接收方忽略 `shardingIndex`/`shardingTotal`，全部执行相同逻辑
- **分片模式**：接收方按 `shardingIndex` / `shardingTotal` 处理对应数据子集

### 3.3 Dispatcher 修改

```java
public class JobDispatcherScannerImpl implements IJobDispatcherScanner {
    static final String TASK_BUILDER_PREFIX = "nopJobTaskBuilder_";

    @Inject
    protected IJobTaskBuilder defaultTaskBuilder;

    void scanOnce() {
        var fires = fireStore.fetchWaitingFires(batchSize, assignedPartitions);
        var locked = fireStore.tryLockFiresForDispatch(fires, AppConfig.hostId(), lockTimeoutMs);
        for (NopJobFire fire : locked) {
            IJobTaskBuilder builder = resolveTaskBuilder(fire);
            List<NopJobTask> tasks = builder.buildTasks(fire);
            fireStore.insertTasksAndMarkFireDispatching(fire, tasks);
        }
    }

    private IJobTaskBuilder resolveTaskBuilder(NopJobFire fire) {
        String executorKind = fire.getExecutorKind();
        if (executorKind != null && !executorKind.isBlank()) {
            String beanName = TASK_BUILDER_PREFIX + executorKind;
            IJobTaskBuilder builder = BeanContainer.tryGetBean(beanName);
            if (builder != null)
                return builder;
        }
        return defaultTaskBuilder;
    }
}
```

### 3.4 IJobFireStore 扩展

```java
public interface IJobFireStore {
    // ... existing methods ...

    // 新增：批量插入 task
    void insertTasksAndMarkFireDispatching(NopJobFire fire, List<NopJobTask> tasks);
}
```

`JobFireStoreImpl` 实现：批量 `taskDao().saveEntityDirectly()` 每个 task，然后将 fire 状态改为 RUNNING。

### 3.5 完整流程对比

**普通模式（单 task）：**
```
Fire(WAITING) → Dispatcher → nopJobTaskBuilder_rpc (未注册，fallback to default)
                         → defaultTaskBuilder.buildTasks(fire) → [task_1]
                         → insertTasksAndMarkFireDispatching
                         → Worker → invoker.invokeAsync() → task_1 完成
```

**分布式模式（N tasks，统一 RpcDistributedTaskBuilder）：**
```
Fire(WAITING) → Dispatcher → nopJobTaskBuilder_rpcBroadcast (已注册)
                         → distributedBuilder.buildTasks(fire)
                           → serverChooser.getServers("data-processor") → [inst_1, inst_2, inst_3]
                           → [task_1(shardingIdx=0, shardTotal=3, targetHost=inst_1),
                              task_2(shardingIdx=1, shardTotal=3, targetHost=inst_2),
                              task_3(shardingIdx=2, shardTotal=3, targetHost=inst_3)]
                         → insertTasksAndMarkFireDispatching(fire, [task_1, task_2, task_3])
                         → Worker 扫描到 task，RPC 调用目标服务
                           → 接收方从 header 获取 shardingIndex/shardingTotal
                           → 广播：忽略分片信息，全部执行
                           → 分片：按 shardingIndex 处理对应数据子集
```

### 3.6 executorKind / dispatchMode dict 与配套 bean 对照表

扩展 `job/executor-kind` dict：

```xml
<dict name="job/executor-kind" valueType="string">
    <option code="test" label="测试执行器"/>
    <option code="rpc" label="RPC 执行器（单次调用）"/>
    <option code="rpcBroadcast" label="RPC 广播执行器（每个实例一个 task）"/>
</dict>
```

新增 `job/dispatch-mode` dict（Plan 213）：

```xml
<dict name="job/dispatch-mode" valueType="string">
    <option code="single" label="单任务"/>
    <option code="partition" label="分片"/>
    <option code="broadcast" label="广播"/>
    <option code="bestFit" label="最优匹配"/>
</dict>
```

**路由优先级**：`dispatchMode` 非空且非 `single` 时优先路由（`nopJobTaskBuilder_{dispatchMode}`），否则回退到 `executorKind` 路由（`nopJobTaskBuilder_{executorKind}`）。`dispatchMode=bestFit` 在 Plan 215 落地前抛 `NopException`。

配套 bean 对照：

| dispatchMode | executorKind | Invoker Bean | TaskBuilder Bean | 说明 |
|-------------|-------------|-------------|-----------------|------|
| `single`(默认) / 未设 | `test` | `nopJobInvoker_test` | — (default) | e2e 测试 |
| `single`(默认) / 未设 | `rpc` | `nopJobInvoker_rpc` | — (default) | 单次 RPC |
| `single`(默认) / 未设 | `rpcBroadcast` | `nopJobInvoker_rpc` | `nopJobTaskBuilder_rpcBroadcast` | 每个实例一个 task |
| `broadcast` | (任意) | (按 executorKind) | `nopJobTaskBuilder_rpcBroadcast` | 1:1 广播 |
| `partition` | (任意) | (按 executorKind) | `nopJobTaskBuilder_partition` | 按 weight 切 hash range |
| `bestFit` | (任意) | — | `nopJobTaskBuilder_bestFit` (Plan 215) | 负载感知派发 |

---

## 四、Worker RPC 接口

### 4.1 Worker 端 RPC 服务定义

Worker 通过 `nop-rpc` 暴露一个标准的 RPC 服务接口，Coordinator（或 Dispatcher）通过 RPC 调用向其分发任务：

```java
@RpcService
public interface IJobWorker {
    CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx);
    CompletionStage<Boolean> cancelAsync(IJobExecutionContext jobCtx);
}
```

注意 `IJobWorker` 的方法签名与 `IJobInvoker` 一致，但语义不同：
- **IJobInvoker**：Coordinator 侧的调用抽象，解析 jobParams 后决定调谁
- **IJobWorker**：Worker 侧的实际执行入口，接收 Coordinator 分发来的执行请求

### 4.2 Worker 注册与发现

Worker 启动时通过 `nop-rpc` 的服务注册机制自动注册 `IJobWorker` 接口：

```xml
<!-- app-service.beans.xml -->
<bean id="jobWorker" class="io.nop.job.worker.engine.JobWorker"/>
```

- 注册用 `@RpcService` 注解（或手动注册），nop-rpc 自动完成服务发布
- Coordinator 通过 RPC 服务发现（`IRpcServiceInvoker`）获取当前所有 Worker 实例列表
- 服务发现支持：精确地址、版本路由、标签过滤、zone 过滤等

### 4.3 完整执行链路

```
┌───────────────────────────────────────────────────┐
│  Coordinator                                       │
│                                                    │
│  Schedule → Fire → IJobTaskBuilder.buildTasks(fire)│
│                    (1..N NopJobTask)               │
│                         → insertTasksAndMarkFire   │
│                         → Worker 侧扫描 task       │
└──────────────────────┬──────────────────────────────┘
                       │
          ┌────────────┴────────────┐
          │ DB (nop_job_task)       │
          └────────────┬────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│  Worker                                              │
│                                                      │
│  JobWorkerScannerImpl (轮询/DB)                       │
│    → tryLockTasksForExecute(workerInstanceId)        │
│    → 匹配到 task → IJobInvokerResolver.resolve()     │
│    → RpcJobInvoker.invokeAsync()                    │
│      → IRpcServiceInvoker.invokeAsync()             │
│        → nop-rpc 调用目标服务                        │
│    → 更新 task 状态                                  │
└─────────────────────────────────────────────────────┘
```

### 4.4 Worker 侧任务获取方式

当前设计采用 **Worker 轮询 DB** 模式：

```java
// JobWorkerScannerImpl 定期扫描
List<NopJobTask> pending = taskStore.findWaitingTasks(AppConfig.hostId(), batchSize);
List<NopJobTask> locked = taskStore.tryLockTasksForExecute(pending, AppConfig.hostId(), lockTimeoutMs);
// 对每个 locked task，解析 invoker 并执行
```

这种方式的好处：
- Worker 无需关心 Coordinator 在哪，DB 是唯一的状态源
- 天然支持 Worker 伸缩（新 Worker 上线自动扫描到分配给自己的 task）
- 与 Coordinator 解耦，Worker 重启后未完成 task 依然在 DB 中

---

## 六、变更清单

| 序号 | 变更 | 文件 | 说明 |
|------|------|------|------|
| 1 | 扩展 executorKind dict | `nop-job.orm.xml` | dict `job/executor-kind` 新增 test/rpc/rpcBroadcast，valueType 改为 string |
| 2 | 废弃 executorRef | ORM / xmeta / view | 从 NopJobSchedule 中移除 executorRef 字段，只保留 executorKind |
| 3 | 修正 resolver | `DefaultJobInvokerResolver.java` | 改为按 executorKind 查找 `nopJobInvoker_{executorKind}` |
| 4 | 新增错误码 | `JobWorkerErrors.java` | `ERR_JOB_EXECUTOR_KIND_EMPTY`, `ERR_JOB_INVOKER_NOT_FOUND` |
| 5 | 新增 RpcJobInvoker | 新文件 | 注入 IRpcServiceInvoker，从 jobParams 解析 serviceName/serviceMethod/headers/data |
| 6 | 注册 bean | `app-service.beans.xml` | `nopJobInvoker_rpc`, `nopJobInvoker_test` |
| 7 | 添加依赖 | `nop-job/nop-job-service/pom.xml` | `nop-rpc-cluster` |
| 8 | 新增 IJobTaskBuilder 接口 | nop-job-coordinator 或 nop-job-api | `List<NopJobTask> buildTasks(NopJobFire fire)` |
| 9 | DefaultJobTaskBuilder | 新文件 | 当前 buildTask 逻辑移入 |
| 10 | RpcDistributedTaskBuilder | 新文件 | 注入 IServerChooser，每个实例一个 task，注入 shardingIndex/shardingTotal/targetHost header |
| 11 | 修改 Dispatcher | `JobDispatcherScannerImpl.java` | 查找 nopJobTaskBuilder_{executorKind}，fallback default |
| 12 | IJobFireStore 扩展 | `IJobFireStore.java` / `JobFireStoreImpl.java` | `insertTasksAndMarkFireDispatching(fire, List<NopJobTask>)` |
| 13 | 删除 nop-job-invokers | 删除目录 | 回退之前的错误实现 |

---

## 七、不做的事

| 决定 | 理由 |
|------|------|
| 不新增 `nop-job-invokers` 模块 | 几十行代码，不值得独立模块 |
| 不保留 executorRef | executorKind + dict 足够，多一个字段反而混淆 |
| 不在 resolver 解析 executorMethod | 这些是 invoker 的内部细节，从 jobParams 自行读取 |
| 不自建 Worker 注册中心 | nop-rpc 已有服务注册发现，Worker 通过 RPC 接口注册即可 |
| 不自建任务分发通信协议 | nop-rpc 已覆盖，Coordinator → Worker 直接 RPC 调用 |
