# nop-job Invoker 设计文档

**日期**：2026-05-17
**范围**：`nop-job` 的 IJobInvoker 实现方案（含广播 task 构建）
**状态**：草案 v3，待确认

---

## 一、核心结论

1. **不新增模块**。Invoker / TaskBuilder 实现直接放在 `nop-job-service`，通过 IoC 注册。
2. **Resolver 只用 `nopJobInvoker_{executorRef}` 前缀查找**，不直接使用 executorRef 作为 bean name（防止任意 bean 访问攻击）。
3. **找不到 invoker 时抛异常**，不返回 null。
4. **不需要 executorKind 路由**。executorRef 直接就是 invoker 名（如 `rpc`、`rpcBroadcast`、`test`），拼成 `nopJobInvoker_rpc` 即可。
5. **不需要 executorMethod**。RPC 调用的 serviceName、serviceMethod 等参数由 invoker 自行从 `jobParams` 中解析。
6. **RPC invoker 注入 `IRpcServiceInvoker`**（平台已有的 `nopRpcServiceInvoker` bean），利用已有服务发现和负载均衡。
7. **RPC 调用参数由 header + data 两部分构成**，都从 `jobParams` 解析。`jobParams` 中可包含 `headers` 子对象指定 RPC header。
8. **广播模式通过 `IJobTaskBuilder` 接口扩展**。Dispatcher 查找 `nopJobTaskBuilder_{executorRef}`，找不到则用默认的单 task 构建。

---

## 二、简化后的设计

### 2.1 Resolver 逻辑

```
Schedule 创建时:
  executorRef = "rpc"  (用户填写的 invoker 名称)
  jobParams = {
    "serviceName": "myService",
    "serviceMethod": "doWork",
    "headers": {"nop-svc-route": "myService:^1.2"}
  }

Fire 触发时:
  executorSnapshot = {"executorRef": "rpc"}  (从 schedule 快照)
  jobParamsSnapshot = { ... }               (从 schedule 快照)

Resolver:
  1. 从 executorSnapshot 取 executorRef → "rpc"
  2. 拼接 bean name → "nopJobInvoker_rpc"
  3. BeanContainer.tryGetBean("nopJobInvoker_rpc")
  4. 找不到 → 抛 NopException
```

### 2.2 DefaultJobInvokerResolver 修改

```java
public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire) {
        String executorRef = resolveExecutorRef(schedule, fire);
        if (executorRef == null || executorRef.isBlank())
            throw new NopException(ERR_JOB_EXECUTOR_REF_EMPTY);

        String beanName = INVOKER_PREFIX + executorRef;
        IJobInvoker invoker = BeanContainer.tryGetBean(beanName);
        if (invoker == null)
            throw new NopException(ERR_JOB_INVOKER_NOT_FOUND)
                .param(ARG_EXECUTOR_REF, executorRef)
                .param(ARG_BEAN_NAME, beanName);
        return invoker;
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

**广播实现：**

```java
// bean id: nopJobTaskBuilder_rpcBroadcast
public class RpcBroadcastTaskBuilder implements IJobTaskBuilder {
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
        int taskNo = 1;
        for (ServiceInstance instance : instances) {
            NopJobTask task = buildTask(fire, taskNo++, instance);
            tasks.add(task);
        }
        return tasks;
    }

    private NopJobTask buildTask(NopJobFire fire, int taskNo, ServiceInstance instance) {
        NopJobTask task = new NopJobTask();
        task.setJobFireId(fire.getJobFireId());
        task.setTaskNo(taskNo);
        task.setTaskStatus(TASK_STATUS_WAITING);

        Map<String, Object> payload = new HashMap<>();
        payload.put("jobFireId", fire.getJobFireId());
        payload.put("executorSnapshot", emptyIfNull(fire.getExecutorSnapshotComponent().get_jsonMap()));
        payload.put("jobParamsSnapshot", mergeTargetHost(fire, instance));
        task.getTaskPayloadComponent().set_jsonValue(payload);

        task.setPartitionIndex(fire.getPartitionIndex());
        // ... timestamp fields
        return task;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeTargetHost(NopJobFire fire, ServiceInstance instance) {
        Map<String, Object> params = new HashMap<>(
            emptyIfNull(fire.getJobParamsSnapshotComponent().get_jsonMap()));
        Map<String, Object> headers = (Map<String, Object>) params.getOrDefault("headers", new HashMap<>());
        headers = new HashMap<>(headers);
        headers.put("nop-svc-target-host", instance.getHost());
        params.put("headers", headers);
        return params;
    }
}
```

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
        Map<String, Object> snapshot = fire.getExecutorSnapshotComponent().get_jsonMap();
        Object ref = snapshot == null ? null : snapshot.get("executorRef");
        if (ref instanceof String executorRef && !executorRef.isBlank()) {
            String beanName = TASK_BUILDER_PREFIX + executorRef;
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

**广播模式（N tasks）：**
```
Fire(WAITING) → Dispatcher → nopJobTaskBuilder_rpcBroadcast (已注册)
                         → broadcastBuilder.buildTasks(fire)
                           → serverChooser.getServers("order-service") → [inst_1, inst_2, inst_3]
                           → [task_1(targetHost=inst_1), task_2(targetHost=inst_2), task_3(targetHost=inst_3)]
                         → insertTasksAndMarkFireDispatching(fire, [task_1, task_2, task_3])
                         → Worker 分别执行每个 task
                           → task_1: invoker(header: nop-svc-target-host=inst_1) → 结果记录到 task_1
                           → task_2: invoker(header: nop-svc-target-host=inst_2) → 结果记录到 task_2
                           → task_3: invoker(header: nop-svc-target-host=inst_3) → 结果记录到 task_3
```

### 3.6 ExecutorRef 与配套 bean 对照表

| executorRef | Invoker Bean | TaskBuilder Bean | 说明 |
|-------------|-------------|-----------------|------|
| `test` | `nopJobInvoker_test` | — (default) | e2e 测试 |
| `rpc` | `nopJobInvoker_rpc` | — (default) | 单次 RPC，负载均衡选一个实例 |
| `rpcBroadcast` | `nopJobInvoker_rpc` | `nopJobTaskBuilder_rpcBroadcast` | 广播 RPC，每个实例一个 task |

注意：`rpc` 和 `rpcBroadcast` 共用同一个 `nopJobInvoker_rpc`，因为 invoker 本身的逻辑相同（构造 ApiRequest → 调用 IRpcServiceInvoker）。区别在于 Dispatcher 阶段创建的 task 数量和每个 task 的 `targetHost` header。

---

## 四、变更清单

| 序号 | 变更 | 文件 | 说明 |
|------|------|------|------|
| 1 | 修正 resolver | `DefaultJobInvokerResolver.java` | prefix → `nopJobInvoker_`，找不到抛异常 |
| 2 | 新增错误码 | `JobWorkerErrors.java` | `ERR_JOB_EXECUTOR_REF_EMPTY`, `ERR_JOB_INVOKER_NOT_FOUND` |
| 3 | 重命名 bean | `app-service.beans.xml` | `nopE2eTestInvoker` → `nopJobInvoker_test` |
| 4 | 新增 RpcJobInvoker | 新文件 | 注入 IRpcServiceInvoker，从 jobParams 解析 serviceName/serviceMethod/headers/data |
| 5 | 注册 bean | `app-service.beans.xml` | `nopJobInvoker_rpc` |
| 6 | 添加依赖 | `nop-job-service/pom.xml` | `nop-rpc-cluster` |
| 7 | 新增 IJobTaskBuilder 接口 | nop-job-coordinator 或 nop-job-api | `List<NopJobTask> buildTasks(NopJobFire fire)` |
| 8 | DefaultJobTaskBuilder | 新文件 | 当前 buildTask 逻辑移入 |
| 9 | RpcBroadcastTaskBuilder | 新文件 | 注入 IServerChooser，每个实例一个 task + targetHost header |
| 10 | 修改 Dispatcher | `JobDispatcherScannerImpl.java` | 查找 nopJobTaskBuilder_{ref}，fallback default |
| 11 | IJobFireStore 扩展 | `IJobFireStore.java` / `JobFireStoreImpl.java` | `insertTasksAndMarkFireDispatching(fire, List<NopJobTask>)` |
| 12 | 删除 nop-job-invokers | 删除目录 | 回退之前的错误实现 |
| 13 | 恢复 pom | `nop-job/pom.xml`, `nop-job-app/pom.xml` | 移除 nop-job-invokers module/依赖 |

---

## 五、不做的事

| 决定 | 理由 |
|------|------|
| 不新增 `nop-job-invokers` 模块 | 几十行代码，不值得独立模块 |
| 不用 executorKind 做路由 | executorRef 直接就是 invoker 名，不需要额外路由层 |
| 不在 resolver 解析 executorMethod | 这些是 invoker 的内部细节，从 jobParams 自行读取 |
