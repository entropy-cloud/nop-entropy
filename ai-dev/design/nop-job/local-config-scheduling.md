# nop-job 本地配置调度设计

**日期**：2026-06-17
**范围**：nop-job-api, nop-job-core, nop-job-service
**状态**：草案
**灵感来源**：nop-job 现有分布式调度方案（coordinator/worker）的轻量级替代，常用于本地开发、单机部署、嵌入式场景

---

## 一、设计结论

1. **新增 YAML 配置文件** `/nop/job/conf/scheduler.yaml`，支持 `x:extends` delta 定制加载。不存在时不启动本地调度，不影响现有分布式调度。
2. **新增 `BeanMethodJobInvoker`**，通过 `BeanContainer` 获取 IoC 中的 bean 并反射调用指定方法。注册为 `nopJobInvoker_beanMethod`，与现有 `nopJobInvoker_rpc`/`nopJobInvoker_test` 并列。
3. **新增 `LocalJobConfigLoader`**，启动时加载 YAML 配置，解析为 `LocalSchedulerConfig` → `List<LocalJobConfig>`，构造 `JobSpec` 后注册到 `LocalJobScheduler`。
4. **条件激活**：以配置路径对应的 VFS 资源是否存在为唯一开关。不存在则 Loader 直接返回（无调度启动），不影响 coordinator/worker 分布式调度。
5. **不新增模块**。BeanMethodJobInvoker 放在 `nop-job-service`，config 模型类和 Loader 也放在 `nop-job-service`，IoC bean 注册在 `app-service.beans.xml` 中新增文件。

## 二、背景与动机

现有 nop-job 的分布式调度方案依赖数据库中的 `NopJobSchedule`/`NopJobFire`/`NopJobTask` 三张表和 coordinator/worker 两套引擎，适合多实例集群场景。

但在以下场景中需要更轻量的方案：
- **本地开发与测试**：不需要启动数据库和 coordinator/worker 组件即可验证定时任务
- **单机嵌入式部署**：作为 SDK 嵌入到其他应用时，不希望强制依赖数据库
- **快速原型**：新业务快速上线，后期再迁移到分布式调度

本地配置调度在上述场景中通过 YAML 配置文件直接声明定时任务，无需建表、无需启用 coordinator/worker。

## 三、核心设计

### 3.1 配置格式与位置

配置文件位于 VFS 路径 `/nop/job/conf/scheduler.yaml`，可通过配置项 `nop.job.scheduler.config-path` 自定义。

**格式：**

```yaml
scheduler:
  enabled: true
  jobs:
    - jobName: data-cleanup
      displayName: "数据清理"
      description: "每天凌晨清理过期数据"
      trigger:
        cronExpr: "0 0 2 * * ?"
      invoker:
        bean: dataCleanupService
        method: cleanup
      params:
        retentionDays: 30

    - jobName: health-check
      displayName: "健康检查"
      trigger:
        repeatInterval: 300000
        repeatFixedDelay: true
      invoker:
        bean: healthCheckService
        method: check
```

每个 job 中 `trigger` 字段直接映射为 `TriggerSpec`，`invoker` 定义调用的 bean 和方法，`params` 作为 `jobParams` 传递给目标方法。

**支持 delta 定制：**

应用层可通过标准 delta 覆盖机制定制调度配置：

```
# _delta/app/nop/job/conf/scheduler.yaml
x:extends: super
scheduler:
  jobs:
    - jobName: data-cleanup
      trigger:
        cronExpr: "0 0 3 * * ?"  # 覆盖为凌晨3点
```

Delta 层文件通过 `x:extends: super` 继承 base 层的配置并做局部覆盖，使用 `JsonTool.loadDeltaBeanFromResource()` 加载（处理 `x:extends` + `x:override`）。

### 3.2 数据模型

```java
// nop-job-api — LocalSchedulerConfig.java
@DataBean
public class LocalSchedulerConfig {
    private boolean enabled;
    private List<LocalJobConfig> jobs;
}

// nop-job-api — LocalJobConfig.java
@DataBean
public class LocalJobConfig {
    private String jobName;
    private String displayName;
    private String description;
    private String jobGroup;
    private TriggerSpec trigger;
    private LocalInvokerConfig invoker;
    private Map<String, Object> params;
    private boolean onceTask;
}

// nop-job-api — LocalInvokerConfig.java
@DataBean
public class LocalInvokerConfig {
    private String bean;
    private String method;
}
```

`LocalSchedulerConfig` 直接映射 YAML 结构。Loader 将其转换为 `JobSpec` 列表：

```yaml
# 映射关系：
LocalJobConfig.jobName        → JobSpec.jobName
LocalJobConfig.displayName     → JobSpec.displayName
LocalJobConfig.description     → JobSpec.description
LocalJobConfig.jobGroup        → JobSpec.jobGroup
LocalJobConfig.trigger         → JobSpec.triggerSpec
LocalJobConfig.params          → JobSpec.jobParams
LocalJobConfig.onceTask        → JobSpec.onceTask
LocalInvokerConfig.bean        → JobSpec.jobParams["beanName"]
LocalInvokerConfig.method      → JobSpec.jobParams["methodName"]
```

`JobSpec.jobInvoker` 固定设为 `"beanMethod"`，由 `LocalJobScheduler` 的 `invokerResolver` 解析为 `BeanMethodJobInvoker` bean。

### 3.3 条件激活

`LocalJobConfigLoader` 的激活逻辑：

```
启动时
  ↓
读取配置项 nop.job.scheduler.config-path (默认 /nop/job/conf/scheduler.yaml)
  ↓
VirtualFileSystem.instance().getResource(path)
  ↓
资源存在?   ───否──→ 不执行任何操作，调度器不启动
  ↓是
JsonTool.loadDeltaBeanFromResource(resource, LocalSchedulerConfig.class)
  ↓
config.enabled == true?  ───否──→ 不执行任何操作
  ↓是
逐条解析 jobs → 构造 JobSpec → LocalJobScheduler.addJob(spec, true)
```

**关键规则**：
- VFS 资源不存在 = silent no-op，不会输出错误日志
- `enabled: false` = 配置存在但不启用，用于临时关闭
- Loader 只注册到 `LocalJobScheduler`，不涉及任何实体存储
- `LocalJobScheduler.activate()` 和 `deactivate()` 由 Loader 或外部管理

### 3.4 BeanMethodJobInvoker

```java
// nop-job-service — BeanMethodJobInvoker.java
public class BeanMethodJobInvoker implements IJobInvoker {

    @Override
    public CompletionStage<JobFireResult> invokeAsync(IJobExecutionContext jobCtx) {
        Map<String, Object> params = jobCtx.getJobParams();
        String beanName = (String) params.get("beanName");
        String methodName = (String) params.get("methodName");

        Object bean = BeanContainer.instance().getBean(beanName);
        Map<String, Object> methodParams = extractMethodParams(params);

        // 方法签名支持：
        // 1. void methodName() — 无参
        // 2. void methodName(Map<String, Object>) — 接收 jobParams
        // 3. Object methodName() — 有返回值
        // 4. Object methodName(Map<String, Object>) — 接收 jobParams 并返回值
        Object result = invokeBeanMethod(bean, methodName, methodParams);
        return CompletableFuture.completedFuture(JobFireResult.CONTINUE(-1));
    }

    private Map<String, Object> extractMethodParams(Map<String, Object> params) {
        // 排除 beanName 和 methodName，其余作为业务参数
        Map<String, Object> result = new HashMap<>(params);
        result.remove("beanName");
        result.remove("methodName");
        return result;
    }
}
```

**方法匹配策略**（按优先级）：

| 优先级 | 签名 | 说明 |
|--------|------|------|
| 1 | `method(Map<String, Object>)` | 接收完整业务参数（排除 beanName/methodName） |
| 2 | `method()` | 无参调用 |
| 3 | `method(Map)` 且 params 不为空 | 同上，接收 map 参数 |

**错误处理**：
- Bean 不存在：抛 `NopException(ERR_JOB_BEAN_NOT_FOUND).param(ARG_BEAN_NAME, beanName)`
- Method 不存在：抛 `NopException(ERR_JOB_METHOD_NOT_FOUND).param(ARG_METHOD_NAME, methodName)`
- 方法执行异常：包装为 `JobFireResult.ERROR(errorBean)`，调度器标记为 FAILED

### 3.5 IoC 集成

**Bean 注册**（在 `app-service.beans.xml` 或新增 `app-local-scheduler.beans.xml`）：

```xml
<bean id="nopJobInvoker_beanMethod" class="io.nop.job.service.executor.BeanMethodJobInvoker"/>
<bean id="nopJobLocalConfigLoader" class="io.nop.job.service.config.LocalJobConfigLoader"
      ioc:init-method="init" ioc:destroy-method="destroy">
    <property name="scheduler" ref="nopJobLocalScheduler"/>
    <property name="configPath" value="@cfg:nop.job.scheduler.config-path|/nop/job/conf/scheduler.yaml"/>
</bean>
```

`LocalJobConfigLoader` 实现 `InitializingBean` 接口，在 `afterPropertiesSet()` 中执行条件激活逻辑。在 `destroy()` 中调用 `scheduler.deactivate()`。

**nopJobLocalScheduler bean**（在 `app-core.beans.xml` 或 `app-service.beans.xml`）：

```xml
<bean id="nopJobLocalScheduler" class="io.nop.job.core.LocalJobScheduler">
    <constructor-arg ref="nopJobScheduledExecutor"/>
    <constructor-arg>
        <bean class="io.nop.job.core.DefaultInvokerResolver"/>
    </constructor-arg>
    <property name="active" value="false"/> <!-- 由 Loader 控制激活 -->
</bean>
```

使用已有的 `GlobalExecutors.scheduledExecutor()` 作为 `IScheduledExecutor`。`DefaultInvokerResolver` 按照 `executorKind → nopJobInvoker_{executorKind}` 的约定从 `BeanContainer` 中查找 invoker。

**配置项**：

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `nop.job.scheduler.config-path` | `/nop/job/conf/scheduler.yaml` | 调度配置文件 VFS 路径 |
| `nop.job.scheduler.enabled` | `true` | 全局开关，配合 config 中的 `enabled` 双重控制 |

### 3.6 模块变更清单

| 模块 | 新增/修改 | 说明 |
|------|----------|------|
| `nop-job-api` | 新增 `LocalSchedulerConfig.java` | 配置数据模型（与 YAML 结构一一映射） |
| `nop-job-api` | 新增 `LocalJobConfig.java` | 单条 job 定义 |
| `nop-job-api` | 新增 `LocalInvokerConfig.java` | bean 方法调用配置 |
| `nop-job-core` | — | 无修改，复用现有 `LocalJobScheduler` |
| `nop-job-service` | 新增 `BeanMethodJobInvoker.java` | bean 方法调用 invoker |
| `nop-job-service` | 新增 `LocalJobConfigLoader.java` | 配置加载 + 条件激活 |
| `nop-job-service` | 新增 `DefaultInvokerResolver.java` | 默认的 invoker resolver（可选，若 LocalJobScheduler 未内置） |
| `nop-job-service` | 修改 `app-service.beans.xml` | 注册 `nopJobInvoker_beanMethod` 和 `nopJobLocalConfigLoader` |

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| **复用现有 coordinator/worker 但使用内存 Store** | 虽然可以 mock 三个 Store 接口，但 coordinator/worker 组件本身为多实例设计，启动了大量不需要的 Scanner/Processor，且依赖分区分配、RPC 等服务。本地调度不应承担这些复杂度。 |
| **在 nop-job-core 中新增独立子模块** | BeanMethodInvoker + ConfigLoader 总共约 200 行代码，不值得独立模块，放在 nop-job-service 与现有 invoker 同层。 |
| **Config 模型直接复用 JobSpec** | YAML 配置的表达方式与 `JobSpec` 不完全一致（例如 `invoker.bean/method` 需要拆解为 `jobInvoker` + `jobParams`），用中间模型更清晰。 |
| **使用 properties 文件而非 YAML** | Properties 不支持嵌套结构和 x:extends，无法满足 delta 定制需求。YAML + `JsonTool.loadDeltaBeanFromResource()` 是平台一致的标准方案。 |
| **通过 @Schedule 注解方式扫描 bean** | 注解方式需要字节码扫描，且不易支持条件激活。YAML 配置只需判断文件存在性，语义明确。 |

## 五、与已有设计的关系

- 本设计与现有 **coordinator/worker 分布式调度** 是**并列替代关系**，不是扩展。两者通过配置路径是否存在自动切换：有 scheduler.yaml → 本地调度；无 → 分布式调度（或纯手动注册）。不存在"同时启动"的场景。
- 复用现有 **LocalJobScheduler** 和 **TriggerBuilder** 全部逻辑（`nop-job-core`），包括 cron 解析、misfire 处理、触发链、状态机。
- 复用现有 **invoker 路由约定**：`executorKind → nopJobInvoker_{executorKind}`。本地调度配置隐式使用 `executorKind = "beanMethod"`，与现有的 `rpc`/`test` 并列。
- `LocalJobConfigLoader` 通过配置路径是否存在实现条件加载，与 `invoker-design.md` 中的 executorKind 路由体系正交。
