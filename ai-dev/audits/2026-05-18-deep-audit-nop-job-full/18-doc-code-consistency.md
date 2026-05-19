# 维度18：文档-代码一致性

## 第 1 轮（初审）

### 问题 1：rewrite-design.md 引用不存在的文件路径

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/ai-dev/design/nop-job/rewrite-design.md`

**行号**：35-42

**证据代码**：
```markdown
| 问题 | 代码证据 | 影响 |
|------|----------|------|
| 持久化接口未完成 | `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/DaoJobSchedulerStore.java` 中 `loadJobDetail()` 直接返回 `null`，`saveInstanceState()` 为空 | 数据库状态无法支撑真实调度 |
| 调度中心以内存为主 | `nop-job/nop-job-core/.../DefaultJobScheduler.java` 使用 `ConcurrentHashMap<String, JobExecution>` 保存运行态 | 重启恢复、主从切换、集群一致性都不可靠 |
| 调度分发器不存在 | `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/queue/DaoJobPlanDispatcher.java` 是空类 | 没有完整的"计划 -> 分发 -> 执行"链路 |
```

**严重程度**：P0

**现状**：文档引用的 3 个文件均不存在：
- `DaoJobSchedulerStore.java` → NOT_FOUND（已通过 `test -f` 验证）
- `DefaultJobScheduler.java` → 实际文件名为 `LocalJobScheduler.java`（已通过 `find` 验证）
- `DaoJobPlanDispatcher.java` → NOT_FOUND（已通过 `test -f` 验证）

**风险**：
1. 新开发者无法验证文档中描述的问题
2. 这些"问题"可能已经过时或不存在
3. 降低文档可信度，误导读者

**建议**：
1. 删除或更新 rewrite-design.md 中的"现状问题"表格（§2.1）
2. 改为引用实际存在的文件路径，或标记为"历史问题（已重写）"
3. 如果这些问题已解决，应移除该表格或更新为"已解决"状态
4. 或者将该表格移至单独的"历史遗留问题"章节

**误报排除**：已通过 `test -f` 命令和 `find` 命令确认文件不存在，非误报。

**审查状态**：✅ 已确认

---

### 问题 2：cluster-ha-design.md 描述未完全实现的 HA 功能但状态标记为 active

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/ai-dev/design/nop-job/cluster-ha-design.md`

**行号**：1-6, 10-12, 47-68

**证据代码**：
```markdown
> Status: active
> Created: 2026-05-18
> Last Verified: 2026-05-18

nop-job 当前通过 `@InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")` 静态配置 partition 范围。节点失效后其 partition 上的任务不会被自动接管。

**决策**：集成 nop-cluster 的 `IDiscoveryClient` + `PartitionAssignHelper`，每个 Scanner 启动时动态计算自己的 partition 范围，集群成员变更时自动重新分配。**不需要 Leader Election**。

每个 Scanner（Planner/Dispatcher/TimeoutChecker/CompletionProcessor/Worker）：
```
启动:
  1. 注册到 IDiscoveryClient（服务名如 "nop-job-coordinator"）
     → 注册动作本身就是心跳的起点，服务发现基础设施接管后续续期
```
```

实际代码证据（JobPlannerScannerImpl.java:72-78）：
```java
@InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")
public void setAssignedPartitions(String partitions) {
    if (partitionResolver == null) {
        partitionResolver = new JobPartitionResolver();
    }
    partitionResolver.setAssignedPartitions(partitions);
}
```

实际代码证据（所有 4 个 Scanner 均使用静态配置）：
- JobPlannerScannerImpl.java:72
- JobDispatcherScannerImpl.java:71
- JobTimeoutCheckerImpl.java:95
- JobCompletionProcessorImpl.java:95

**严重程度**：P0

**现状**：
1. 文档状态标记为 `active`，暗示功能已实现
2. 代码中 4 个 Scanner 仍使用静态配置 `@InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")`
3. 虽然 `JobPartitionResolver` 已实现基于 `INamingService` + `PartitionAssignHelper` 的动态分区解析逻辑，但：
   - Scanner 未注册到 `IDiscoveryClient`
   - 未触发服务发现基础设施的心跳
   - 文档 §3.2 描述的"Scanner 分区刷新流程"未实现
4. 代码中未找到任何 Scanner 实现调用 `refreshPartitionRange()` 或类似动态刷新方法

**风险**：
1. 状态标记误导开发者和评审人员，认为 HA 功能已可用
2. 可能导致 HA 功能被误认为已实现而投入生产
3. 集群节点失效时任务无法自动接管，存在单点故障风险
4. 动态分区刷新缺失可能导致 partition 分配不一致

**建议**：
1. 将文档状态从 `active` 改为 `partial` 或 `draft`，明确标注部分实现状态
2. 更新 `Last Verified` 日期为实际验证日期
3. 在文档中添加"实现状态"章节，明确列出：
   - ✅ 已实现：JobPartitionResolver 的动态分区计算逻辑
   - ❌ 未实现：Scanner 注册到 IDiscoveryClient
   - ❌ 未实现：运行时动态分区刷新机制
4. 如果计划完全实现 HA 功能，应补充相应的实施计划和状态追踪

**误报排除**：已通过代码搜索确认：
- 4 个 Scanner 均使用静态配置（grep 验证）
- JobPartitionResolver 存在但未被 Scanner 动态刷新（grep 验证）
- Scanner 未注册到服务发现（grep 验证）
非误报。

**审查状态**：✅ 已确认

---

### 问题 3：retry-integration-design.md 接口签名与实际实现不一致

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/ai-dev/design/nop-job/retry-integration-design.md`

**行号**：64-73

**证据代码**（文档描述）：
```markdown
public interface IJobRetryBridge {
    /**
     * Fire 失败时调用。如果 fire 关联了 retryPolicyId，
     * 桥接实现应将其提交给 nop-retry。
     *
     * @return retry_record_id，null 表示未提交重试
     */
    String onFireFailed(NopJobFire fire, NopJobSchedule schedule);
}
```

实际接口定义（IJobRetryBridge.java:1-5）：
```java
package io.nop.job.api.retry;

public interface IJobRetryBridge {
    String onFireFailed(JobFireFailedEvent event);
}
```

实际实现（NopRetryJobRetryBridge.java:31-65）：
```java
@Override
public String onFireFailed(JobFireFailedEvent event) {
    if (retryEngine == null) {
        LOG.warn("nop.job.retry.engine-not-available:fireId={}", event.getJobFireId());
        return null;
    }

    try {
        IRetryTask task = retryEngine.newRetryTask(SERVICE_NAME, SERVICE_METHOD)
                .withPolicyId(event.getRetryPolicyId())
                .withIdempotentId(event.getJobFireId())
                .withNamespaceId(event.getNamespaceId())
                .withGroupId(event.getGroupId());

        // ... 其他逻辑

        return event.getJobFireId();
    } catch (Exception e) {
        LOG.error("nop.job.retry.submit-error:fireId={}", event.getJobFireId(), e);
        return null;
    }
}
```

JobFireFailedEvent 类定义：
```java
public class JobFireFailedEvent {
    private final String jobFireId;
    private final String jobScheduleId;
    private final String retryPolicyId;
    private final String namespaceId;
    private final String groupId;
    private final String jobName;
    private final String executorKind;
    private final String errorCode;
    private final String errorMessage;
    // ... getters
}
```

**严重程度**：P1

**现状**：
1. 文档描述的接口签名为 `String onFireFailed(NopJobFire fire, NopJobSchedule schedule)`（2 个参数）
2. 实际接口定义为 `String onFireFailed(JobFireFailedEvent event)`（1 个参数）
3. `JobFireFailedEvent` 对象封装了 9 个字段，包括 fire 和 schedule 的所有相关信息
4. `NopRetryJobRetryBridge` 实际实现使用 `JobFireFailedEvent`
5. 文档承诺的"返回 retry_record_id"与实际实现不一致（实际返回 `event.getJobFireId()` 或 `null`）

**风险**：
1. 开发者按照文档实现接口时会出现编译错误
2. 接口变更未同步更新文档，可能导致接口误用
3. 文档承诺的返回值语义（retry_record_id）与实际实现（jobFireId）不一致
4. 新增的 retry-adapter 模块实现者会被误导

**建议**：
1. 更新 retry-integration-design.md §3.1 的接口定义为：
```java
public interface IJobRetryBridge {
    /**
     * Fire 失败时调用。如果 fire 关联了 retryPolicyId，
     * 桥接实现应将其提交给 nop-retry。
     *
     * @return jobFireId（用于幂等控制），null 表示未提交重试
     */
    String onFireFailed(JobFireFailedEvent event);
}
```
2. 补充 `JobFireFailedEvent` 类的字段说明文档
3. 明确返回值的语义（当前实现返回 jobFireId，而非 retry_record_id）
4. 在 §3.2 描述中说明 event 参数的 9 个字段来源和用途

**误报排除**：已通过读取实际接口文件和实现文件确认签名不一致，非误报。

**审查状态**：✅ 已确认

---

### 问题 4：invoker-design.md 方法签名描述不准确

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/ai-dev/design/nop-job/invoker-design.md`

**行号**：51-72

**证据代码**（文档描述）：
```java
public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    static final String INVOKER_PREFIX = "nopJobInvoker_";
    static final String EXECUTOR_KIND_KEY = "executorKind";

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
}
```

实际实现（DefaultJobInvokerResolver.java:12-39）：
```java
public class DefaultJobInvokerResolver implements IJobInvokerResolver {
    static final String INVOKER_PREFIX = "nopJobInvoker_";

    @Override
    public IJobInvoker resolveInvoker(NopJobSchedule schedule, NopJobFire fire) {
        String executorKind = resolveExecutorKind(schedule, fire);
        if (executorKind == null || executorKind.isBlank()) {
            throw new NopException(ERR_JOB_EXECUTOR_KIND_EMPTY)
                    .param("jobName", schedule.getJobName())
                    .param("jobGroup", schedule.getGroupId());
        }

        String beanName = INVOKER_PREFIX + executorKind;
        Object bean = BeanContainer.tryGetBean(beanName);
        if (!(bean instanceof IJobInvoker)) {
            throw new NopException(ERR_JOB_INVOKER_NOT_FOUND)
                    .param("executorKind", executorKind)
                    .param("beanName", beanName)
                    .param("jobName", schedule.getJobName())
                    .param("jobGroup", schedule.getGroupId());
        }
        return (IJobInvoker) bean;
    }

    private String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
        String kind = fire.getExecutorKind();
        return kind != null ? kind : schedule.getExecutorKind();
    }
}
```

**严重程度**：P1

**现状**：
1. 文档中不存在 `EXECUTOR_KIND_KEY` 常量（实际代码未定义）
2. 文档中的 `BeanContainer.tryGetBean(beanName)` 返回 `IJobInvoker`，实际返回 `Object`
3. 实际实现使用 `instanceof` 检查类型（`!(bean instanceof IJobInvoker)`），文档中直接使用 `== null` 判断
4. 实际实现的异常参数更多：
   - ERR_JOB_EXECUTOR_KIND_EMPTY: 添加了 `jobName` 和 `jobGroup`
   - ERR_JOB_INVOKER_NOT_FOUND: 添加了 `jobName` 和 `jobGroup`
5. 文档未展示 `resolveExecutorKind()` 方法的实现，但实际代码中有且非常简洁

**风险**：
1. 开发者按照文档重写或修改代码时会产生编译错误
2. `EXECUTOR_KIND_KEY` 常量不存在可能导致代码逻辑混淆
3. 类型检查逻辑差异（`== null` vs `instanceof`）可能导致运行时类型不安全
4. 异常参数缺失可能影响错误诊断和日志追踪

**建议**：
1. 删除不存在的 `EXECUTOR_KIND_KEY` 常量引用（第 54 行）
2. 更新 `tryGetBean` 的返回类型为 `Object` 并补充类型检查逻辑
3. 同步异常参数列表，包括 `jobName` 和 `jobGroup`
4. 补充 `resolveExecutorKind()` 方法的实现代码（第 36-39 行）
5. 更新文档 §2.2 的代码片段与实际实现完全一致

**误报排除**：已通过读取实际实现文件（DefaultJobInvokerResolver.java）确认签名不一致，非误报。

**审查状态**：✅ 已确认

---

### 问题 5：invoker-design.md 描述的 executorSnapshot 字段不存在

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/ai-dev/design/nop-job/invoker-design.md`

**行号**：38-43, 72

**证据代码**（文档描述）：
```markdown
实体存储:
  NopJobSchedule
  ├── executorKind   = "rpc"           ← 实体列(string)，页面下拉框选择
  ├── jobParams      = { ... }         ← JSON 列，存储 RPC 调用参数
  └── executorRef    = (废弃)

Fire 触发时 (快照):
  NopJobFire
  ├── executorSnapshot   = {"executorKind": "rpc"}      ← JSON，从 schedule 拷贝
  ├── jobParamsSnapshot  = { "serviceName": "..." }     ← JSON，从 schedule 拷贝

Resolver 优先级:
  1. fire.executorSnapshot → executorKind（优先，保证历史一致性）
  2. schedule.executorKind（fallback）
```

实际 ORM 定义（nop-job.orm.xml）：
```xml
<entity name="io.nop.job.dao.entity.NopJobFire" ...>
    <column code="EXECUTOR_KIND" name="executorKind" ... />
    <column code="JOB_PARAMS_SNAPSHOT" name="jobParamsSnapshot" ... />
    <!-- 没有 EXECUTOR_SNAPSHOT 字段 -->
</entity>
```

实际代码实现（DefaultJobInvokerResolver.java:36-39）：
```java
private String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
    String kind = fire.getExecutorKind();
    return kind != null ? kind : schedule.getExecutorKind();
}
```

实体字段验证（_NopJobFire.java:84-85, 279, 433-434）：
```java
public static final String PROP_NAME_executorKind = "executorKind";
public static final int PROP_ID_executorKind = 16;
// ...
private java.lang.String _executorKind;
// ...
public java.lang.String getExecutorKind() {
    return this._executorKind;
}
```

文档第 72 行错误引用（invoker-design.md:72）：
```java
// 从 executorSnapshot 中取 executorKind
// 实际代码中不存在 getExecutorSnapshot() 方法
```

文档第 290 行错误引用（invoker-design.md:290）：
```java
payload.put("executorSnapshot", emptyIfNull(fire.getExecutorSnapshotComponent().get_jsonMap()));
// 实际代码中不存在 getExecutorSnapshotComponent() 方法
```

**严重程度**：P1

**现状**：
1. `NopJobFire` 实体中没有 `executorSnapshot` JSON 字段
2. `NopJobFire` 实体中有直接的 `executorKind` 字段（String 类型）
3. 实际代码实现直接读取 `fire.getExecutorKind()`，而非从 JSON 快照中解析
4. 文档第 72、290 行引用了不存在的方法（`getExecutorSnapshot()`、`getExecutorSnapshotComponent()`）
5. `jobParamsSnapshot` 字段存在且符合文档描述

**风险**：
1. 文档误导读者认为使用 JSON 快照机制，实际使用的是直接字段存储
2. 如果按照文档实现相关代码，会出现编译错误（方法不存在）
3. "历史一致性"的设计目标可能无法通过 JSON 快照实现
4. 新增 executorKind 相关功能时可能设计方向错误

**建议**：
1. 删除文档中所有关于 `executorSnapshot` 的描述（§2.1 第 40 行，第 43 行，第 72 行，第 290 行）
2. 更新文档为与实际实现一致的描述：
```markdown
Fire 触发时 (快照):
  NopJobFire
  ├── executorKind   = "rpc"      ← String 字段，从 schedule 拷贝
  ├── jobParamsSnapshot  = { "serviceName": "..." }     ← JSON，从 schedule 拷贝

Resolver 优先级:
  1. fire.executorKind（直接字段读取，已包含历史值）
  2. schedule.executorKind（fallback）
```
3. 删除或修正第 290 行的错误代码引用
4. 在文档中说明为何选择直接字段存储而非 JSON 快照（如果有设计理由）

**误报排除**：已通过检查 ORM 定义和实体生成代码确认 `executorSnapshot` 字段不存在，非误报。

**审查状态**：✅ 已确认

---

### 问题 6：rate-limiting-design.md 状态标记与实际功能状态不一致

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/ai-dev/design/nop-job/rate-limiting-design.md`

**行号**：1-6, 70-78

**证据代码**（文档状态）：
```markdown
# nop-job 限流设计

> Status: draft
> Created: 2026-05-17
> Last Updated: 2026-05-18
```

证据代码（§2.1 "已覆盖的"表格）：
```markdown
| 场景 | 覆盖方式 |
|------|---------|
| Job 级并发控制 | 4 种阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY） |
| GraphQL 请求限流 | GraphQLEngine 已注入 FlowControlRunner |
| Step 级限流 | nop-task 的 RateLimitTaskStepWrapper |
| Job 触发 RPC 时的调用 | 如果 RPC interceptor 链启...
```

代码验证结果：
```bash
# 搜索 FlowControlRunner 使用
grep -rn "nopFlowControlRunner\|IFlowControlRunner" /path/to/nop-job --include="*.java"
# 结果：(no output)

# 搜索 RateLimiter 使用
grep -rn "IRateLimiter\|tryAcquire" /path/to/nop-job --include="*.java"
# 结果：(no output)
```

**严重程度**：P1

**现状**：
1. 文档状态标记为 `draft`，暗示设计未最终确定或未开始实现
2. 但文档 §2.1 列出了多个"已覆盖"的限流场景
3. 代码中未找到 `IFlowControlRunner` 或 `IRateLimiter` 的使用（已通过 grep 验证）
4. 阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY）已实现，但这属于"并发控制"（concurrency control）而非"限流"（rate limiting）
5. 文档混淆了"限流"和"并发控制"两个不同的概念

**风险**：
1. 状态标记 `draft` 可能让开发者误认为限流功能未实现，但文档又描述为"已覆盖"
2. 概念混淆（限流 vs 并发控制）可能导致需求理解偏差
3. 实际限流功能缺失，但文档描述为"已覆盖"，可能误导决策
4. 未来需要限流功能时，缺乏清晰的设计指导

**建议**：
1. 将文档标题改为"并发控制与限流设计"以区分两种机制
2. 在文档开头添加概念区分说明：
   - 并发控制（Concurrency Control）：限制同时执行的任务数量（已实现：阻塞策略）
   - 限流（Rate Limiting）：限制单位时间内的执行次数（未实现）
3. 更新状态标记：
   - 如果限流功能尚未实现：保持 `draft`，并在 §2.1 中明确标记哪些已实现、哪些未实现
   - 如果限流功能已在其他模块实现（如 GraphQL 层）：改为 `implemented` 并说明实现位置
4. 在 §2.1 表格中添加"状态"列，标记每个场景的实现状态

**误报排除**：已通过代码搜索确认 `IFlowControlRunner` 和 `IRateLimiter` 未在 nop-job 模块中使用，非误报。

**审查状态**：✅ 已确认

---

### 问题 7：docs-for-ai/ 中缺少 nop-job 相关文档

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/docs-for-ai/INDEX.md`

**行号**：全文件（1-63）

**证据代码**（文档列表）：
```markdown
## 快速路由

| 任务 | 首选文档 |
|------|---------|
| 理解整体仓库结构 | `01-repo-map/module-groups.md` |
| 判断一个业务模块怎么分层 | `01-repo-map/domain-module-pattern.md` |
| 找模型、页面、测试、模块入口 | `01-repo-map/where-things-live.md` |
| 从模型开始开发 | `02-core-guides/model-first-development.md` |
| 编写 BizModel / 服务层逻辑 | `02-core-guides/service-layer.md` |
| 理解 GraphQL / API 暴露方式 | `02-core-guides/api-and-graphql.md` |
| 判断领域逻辑和 DDD 落位 | `02-core-guides/domain-logic-and-ddd.md` |
| 判断 DTO / JSON / message bean 写法 | `02-core-guides/dto-json-and-message-beans.md` |
| 判断 IoC 注入和配置写法 | `02-core-guides/ioc-and-config.md` |
| 判断错误处理和错误码写法 | `02-core-guides/error-handling.md` |
| 查询当前仓库代码风格 | `02-core-guides/code-style.md` |
| 理解外部应用模块开发 | `02-core-guides/external-app-development.md` |
| 定制 view / page 页面 | `02-core-guides/view-and-page-customization.md` |
| 查复杂页面 DSL 配置模式 | `02-core-guides/page-dsl-pattern-catalog.md` |
| 调试与排障 | `02-core-guides/debugging-and-diagnostics.md` |
| 做 Delta 定制 | `02-core-guides/delta-customization.md` |
| 编写 XDef / XDSL 文件 | `02-core-guides/xdef-and-xdsl.md` |
| 理解 XLang / XPL / xrun / xgen 基本写法 | `02-core-guides/xlang-and-xpl-basics.md` |
| 编写测试 | `02-core-guides/testing.md` |
| 新建实体 | `03-runbooks/create-new-entity.md` |
| 新增字段或校验 | `03-runbooks/add-field-and-validation.md` |
| 新增字典或常量 | `03-runbooks/add-dict-and-constants.md` |
| 模型变更后重新生成 | `03-runbooks/change-model-and-regenerate.md` |
| 调试生成链路或生成文件 | `03-runbooks/debug-codegen-and-generated-files.md` |
| 查后台页面开发路线图 | `03-runbooks/admin-page-development-roadmap.md` |
| 写 BizModel 方法 | `03-runbooks/write-bizmodel-method.md` |
| 创建 Request / Response DTO | `03-runbooks/create-request-response-dto.md` |
| 新增跨模块 Biz 接口 | `03-runbooks/add-cross-module-biz-interface.md` |
| 选择 Entity / BizModel / Processor | `03-runbooks/choose-entity-bizmodel-processor.md` |
| 实现复杂业务流程 | `03-runbooks/implement-complex-business-flow.md`
```

验证结果：
```bash
$ grep -rn "job\|Job\|JOB" /Users/abc/app/nop-entropy-wt/nop-entropy-master/docs-for-ai/
# 结果：(no output)
```

`ai-dev/design/nop-job/` 中的设计文档：
- cluster-ha-design.md（平台开发文档）
- rewrite-design.md（平台开发文档）
- retry-integration-design.md（平台开发文档）
- metrics-design.md（平台开发文档）
- rate-limiting-design.md（平台开发文档）
- block-strategy-design.md（平台开发文档）
- invoker-design.md（平台开发文档）

**严重程度**：P2

**现状**：
1. `docs-for-ai/` 目录中没有任何关于 nop-job 的文档（已通过 glob 和 grep 验证）
2. `docs-for-ai/INDEX.md` 的快速路由表中没有 nop-job 相关条目
3. `ai-dev/design/nop-job/` 中的所有文档都是平台开发文档（针对改造 Nop 框架本身），非平台使用文档
4. nop-job 是一个完整的功能模块，但缺乏面向应用开发者的使用文档

**风险**：
1. 基于 Nop 平台开发应用的开发者无法从 `docs-for-ai/` 获取 nop-job 使用指南
2. 违反了 AGENTS.md 中"docs-for-ai 是平台使用知识的权威来源"的原则
3. 开发者可能直接阅读源码或设计文档，降低开发效率
4. 缺乏标准化的使用模式指导，可能导致不一致的代码实践
5. 新入职开发者难以快速上手 nop-job 模块

**建议**：
1. 在 `docs-for-ai/` 中创建 nop-job 使用文档，至少应包含：
   - 基本概念（Schedule/Fire/Task 三层模型）
   - 如何创建和配置 Job（从模型到页面）
   - executorKind 的使用方式（rpc/rpcBroadcast/test）
   - 阻塞策略说明（DISCARD/OVERLAY/PARALLEL/RECOVERY）
   - trigger 配置（cron/periodic/manual）
   - jobParams 和 jobParamsSnapshot 的区别与使用
   - 失败重试配置（retryPolicyId）
   - Metrics 监控和告警
   - 集群 HA 配置（如果已实现）
2. 更新 `docs-for-ai/INDEX.md` 添加 nop-job 相关条目：
   - 添加任务类型："使用 nop-job 创建定时任务"
   - 添加文档路径：`03-runbooks/use-nop-job.md` 或 `02-core-guides/nop-job-basics.md`
3. 考虑添加 nop-job 到 `01-repo-map/module-groups.md` 的模块分组说明中

**误报排除**：已通过 glob 和 grep 确认 docs-for-ai/ 中确实无 job 相关内容，非误报。

**审查状态**：✅ 已确认

---

### 问题 8：source-anchors.md BIZ-005 锚点路径不规范的严重程度为 P3

**文件路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/docs-for-ai/04-reference/source-anchors.md`

**行号**：25

**证据代码**：
```markdown
| `BIZ-005` | `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java` + `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java` | 跨 BizModel 协作通常通过 `I*Biz` 接口 |
```

验证结果：
```bash
$ test -f /path/to/nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java
# 结果：EXISTS

$ test -f /path/to/nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java
# 结果：EXISTS
```

实际模块结构：
- `INopJobScheduleBiz.java` 位于 `nop-job-dao` 模块（而非标准 `nop-job-biz` 模块）
- `NopJobScheduleBizModel.java` 位于 `nop-job-service` 模块
- 根据 `domain-module-pattern.md`，标准领域模块应包含 `*-biz` 子模块

**严重程度**：P3

**现状**：
1. 两个文件路径均存在且可访问
2. 但 `INopJobScheduleBiz` 位于 `nop-job-dao` 模块而非标准的 `nop-job-biz` 模块
3. nop-job 的模块结构与标准领域模块模式（`domain-module-pattern.md`）不完全一致
4. 锚点本身作为"实现示例"是有效的，但模块组织方式可能不够典型

**风险**：
1. 低风险，但可能误导开发者理解模块结构
2. 跨模块协作的最佳实践示例不够典型（不符合标准模块模式）
3. 如果其他模块模仿此结构，可能导致模块组织混乱

**建议**：
1. 确认 `INopJobScheduleBiz` 是否应移至独立的 `nop-job-biz` 模块，以符合标准领域模块模式
2. 如果保持现状，应在锚点说明中补充注释：
   ```markdown
   | `BIZ-005` | `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java` + `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java` | 跨 BizModel 协作通常通过 `I*Biz` 接口。（注：nop-job 的模块结构为简化设计，将接口置于 dao 模块，BizModel 位于 service 模块） |
   ```
3. 或更新注释说明为什么选择这种模块结构（历史原因、简化设计等）

**误报排除**：路径存在，但模块结构与标准模式不一致，属于建议性问题，非误报。

**审查状态**：✅ 已确认

---

## 审计总结

### 发现统计

| 严重程度 | 数量 | 问题编号 |
|---------|------|---------|
| P0 | 2 | 问题 1, 问题 2 |
| P1 | 4 | 问题 3, 问题 4, 问题 5, 问题 6 |
| P2 | 1 | 问题 7 |
| P3 | 1 | 问题 8 |
| **总计** | **8** | - |

### 关键发现

1. **文档状态标记严重不准确**（P0）：cluster-ha-design.md 标记为 `active` 但 HA 功能未完全实现
2. **历史遗留文档未清理**（P0）：rewrite-design.md 引用不存在的旧文件
3. **接口/方法签名与代码不一致**（P1 × 3）：retry-integration-design.md、invoker-design.md 多处签名不准确
4. **概念混淆**（P1）：rate-limiting-design.md 混淆限流和并发控制
5. **平台使用文档缺失**（P2）：docs-for-ai/ 中无 nop-job 使用指南

### 已对齐的部分（值得肯定）

以下设计文档与代码实现基本对齐：

1. **block-strategy-design.md**（Status: implemented）
   - 阻塞策略方法实现位置正确
   - `overlayFireAndAdvanceSchedule()` 和 `recoveryFireAndAdvanceSchedule()` 方法存在
   - 虽方法名不完全一致（`shouldOverlay()` vs `overlayFireAndAdvanceSchedule()`），但核心功能已实现

2. **metrics-design.md**（Status: implemented）
   - Metrics 接口和实现类存在且符合设计规范
   - 三件套模式（接口+真实实现+空实现）正确实现
   - Meter 命名符合 `nop.job.{component}.{metric-name}` 契约
   - 统计字段（lastDurationMs、totalFireCount、successFireCount、failFireCount）已在 ORM 中定义

3. **retry-integration-design.md**
   - `IJobRetryBridge` 接口和 `NopRetryJobRetryBridge` 实现存在
   - `NoOpJobRetryBridge` 默认实现存在
   - `JobFireFailedEvent` 事件类包含完整的失败信息

4. **invoker-design.md**（部分）
   - `DefaultJobInvokerResolver` 实现了基于 executorKind 的路由逻辑
   - `IJobTaskBuilder` 接口和 `DefaultJobTaskBuilder` 默认实现存在
   - `RpcJobInvoker` 实现存在且符合设计思路
   - `RpcBroadcastTaskBuilder` 已注册为 bean

5. **cluster-ha-design.md**（部分）
   - `JobPartitionResolver` 已实现基于 `INamingService` + `PartitionAssignHelper` 的动态分区解析
   - `stableWindowMs` 稳定性窗口机制已实现
   - `isUnstable()` 实例变更检测逻辑已实现

6. **source-anchors.md BIZ-005**
   - 引用的两个文件路径存在且有效
   - 展示了跨 BizModel 协作的实际示例

### 改进建议

1. **立即修复 P0 问题**：
   - 更新 rewrite-design.md 的"现状问题"表格，删除或更新不存在的文件引用
   - 更新 cluster-ha-design.md 状态标记，明确 HA 功能的已实现和未实现部分

2. **同步更新 P1 问题**：
   - 修正 retry-integration-design.md 的接口签名
   - 修正 invoker-design.md 的方法签名和字段引用
   - 澄清 rate-limiting-design.md 的限流 vs 并发控制概念

3. **补充 P2 文档**：
   - 在 docs-for-ai/ 中创建 nop-job 使用文档
   - 更新 docs-for-ai/INDEX.md 添加相关条目

4. **建立文档-代码同步机制**：
   - 在代码变更时同步更新相关文档
   - 定期执行文档-代码一致性审计（建议每季度）
   - 在 CI/CD 中添加文档检查（如 markdown 链接有效性检查）

### 总体评估

nop-job 模块的代码实现质量较好，核心功能（调度、分发、执行、监控）已基本实现并符合设计意图。但文档与代码的一致性存在明显问题，主要体现在：

1. **设计文档未及时更新**：接口变更、字段变更未同步到文档
2. **历史文档未清理**：重写前的旧代码引用仍存在于设计文档中
3. **状态标记不准确**：设计文档的状态标记与实际实现状态不匹配
4. **使用文档缺失**：缺乏面向应用开发者的标准化使用指南

建议优先修复 P0 和 P1 问题，然后补充 P2 使用文档，最后建立长期维护机制确保文档与代码持续对齐。

## 深挖第 2 轮追加

### [18-14] P1 - invoker-design.md resolveExecutorKind 代码示例引用不存在的 API

**文件**：`ai-dev/design/nop-job/invoker-design.md:71-81`

**证据（文档描述）**：
```java
protected String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
    Map<String, Object> snapshot = fire.getExecutorSnapshotComponent().get_jsonMap();
    if (snapshot != null) {
        Object kind = snapshot.get(EXECUTOR_KIND_KEY);
        if (kind instanceof String && !((String) kind).isBlank())
            return (String) kind;
    }
    return schedule.getExecutorKind();
}
```

**实际代码**（`DefaultJobInvokerResolver.java:36-39`）：
```java
private String resolveExecutorKind(NopJobSchedule schedule, NopJobFire fire) {
    String kind = fire.getExecutorKind();
    return kind != null ? kind : schedule.getExecutorKind();
}
```

**严重程度**：P1

**现状**：文档展示的 `resolveExecutorKind` 使用 `fire.getExecutorSnapshotComponent().get_jsonMap()` 获取 executorKind。实际代码直接调用 `fire.getExecutorKind()`。`getExecutorSnapshotComponent()` 在 `NopJobFire` 上不存在；`_NopJobFire._gen` 仅有 `executorKind` 字段（propId=16）。文档中的 `EXECUTOR_KIND_KEY` 常量在实际代码中也不存在。

**风险**：开发者按文档理解时假设存在 executorSnapshot → JSON map → 解析的间接路径，但实际是直接的实体字段读取。理解偏差会导致扩展 invoker 时实现错误的 fallback 逻辑。

**建议**：将 invoker-design.md 的代码示例替换为 `DefaultJobInvokerResolver.java` 的实际实现；删除 `EXECUTOR_KIND_KEY` 常量和 `executorSnapshotComponent` 引用。

**误报排除**：已 grep `executorSnapshotComponent` 全 nop-job 模块零匹配；已确认 `_NopJobFire._gen` 仅暴露 `executorKind` 字段。非误报。

---

### [18-15] P2 - invoker-design.md 状态标记为"草案 v5"但核心实现已完成

**文件**：`ai-dev/design/nop-job/invoker-design.md:5`

**证据（文档）**：
```
状态：草案 v5，待确认
```

**实际实现状态**：
- `DefaultJobInvokerResolver` 已完整实现（resolveInvoker + resolveExecutorKind）
- `IJobTaskBuilder` 接口、`DefaultJobTaskBuilder`、`RpcBroadcastTaskBuilder` 已实现
- `JobDispatcherScannerImpl` 已集成 `nopJobTaskBuilder_{executorKind}` 路由
- 测试覆盖：`TestDefaultJobInvokerResolver` 已存在

**严重程度**：P2

**现状**：文档核心结论（executorKind 唯一路由、nopJobInvoker_ 前缀、IJobTaskBuilder 扩展点）已全部落地到代码，但文档仍标记为"草案 v5，待确认"。

**风险**：下游开发者或 AI agent 看到草案状态后可能认为设计未确定而不敢参考，或尝试做替代设计导致分裂。

**建议**：将第 5 行状态更新为 `Status: implemented`。同时更新 `Last Updated` 日期。

**误报排除**：已验证 DefaultJobInvokerResolver.java 40 行完整实现；已验证 IJobTaskBuilder 接口和两个实现类存在；已验证 JobDispatcherScannerImpl 中 TASK_BUILDER_PREFIX 和 resolveTaskBuilder 方法。非误报。

---

### [18-16] P2 - rate-limiting-design.md 将未满足条件的 RPC 限流列为"已覆盖"

**文件**：`ai-dev/design/nop-job/rate-limiting-design.md:74-77`

**证据（文档表格）**：
```markdown
### 2.1 已覆盖的

| 场景 | 覆盖方式 |
|------|---------|
| Job 级并发控制 | 4 种阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY） |
| GraphQL 请求限流 | GraphQLEngine 已注入 FlowControlRunner |
| Step 级限流 | nop-task 的 RateLimitTaskStepWrapper |
| Job 触发 RPC 时的调用 | 如果 RPC interceptor 链启用 FlowControl |
```

**严重程度**：P2

**现状**：最后一行"Job 触发 RPC 时的调用"使用条件语句"如果 RPC interceptor 链启用 FlowControl"，实际上 nop-job-worker 中不存在 FlowControlRpcServiceInterceptor 的配置。该场景的限流依赖于外部模块是否启用，不应列为"已覆盖"。

**风险**：读者误认为 RPC 限流已开箱即用，实际部署时可能未启用任何限流保护。

**建议**：将该行的覆盖方式改为"依赖外部配置（非开箱即用）"，或添加备注说明需要额外配置。

**误报排除**：已确认 nop-job-worker 中无 FlowControl 相关 beans.xml 配置，FlowControlRpcServiceInterceptor 在 nop-job 中无匹配。非误报。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 问题 1 | rewrite-design.md 引用不存在的文件路径 | 驳回 | 文档已在提交 `b7375746d` 中完全重写，§2.1 旧"现状问题"表格已被 §"二、设计背景"取代，三个不存在的文件引用已从文档中删除。审计基于文档旧版本，问题已不存在。 |
| 问题 2 | cluster-ha-design.md 状态标记为 active 但 HA 功能未完全实现 | 降级 P0→P2 | `ai-dev/design/` 是设计决策文档，`active` 表示"设计已采纳"而非"功能已上线"；`JobPartitionResolver` 已实现完整动态分区逻辑，4 个 Scanner 每次 scan 调用 `resolvePartitions()`；`namingService` 未在 beans.xml 中注入是配置 gap，非架构缺失；静态 `assignedPartitions` 作为 fallback 是合理的降级设计。 |
| 问题 3 | retry-integration-design.md 接口签名与实际实现不一致 | 保留 P1 | 文档描述 `onFireFailed(NopJobFire fire, NopJobSchedule schedule)`（2参数），实际代码 `onFireFailed(JobFireFailedEvent event)`（1参数），按文档编写代码会编译失败。 |
| 问题 4 | invoker-design.md 方法签名描述不准确 | 降级 P1→P2 | 不存在的 `EXECUTOR_KIND_KEY` 常量仅在文档中，不影响编译；`tryGetBean` 返回类型差异是代码风格问题，核心路由逻辑一致。 |
| 问题 5 | invoker-design.md 描述的 executorSnapshot 字段不存在 | 保留 P1 | 文档 4 处引用 `executorSnapshot`/`getExecutorSnapshot()`/`getExecutorSnapshotComponent()`，实际 `NopJobFire` 只有 `executorKind`（String 直接字段），按文档实现会编译失败。 |
| 问题 6 | rate-limiting-design.md 状态标记与功能状态不一致 | 驳回 | 文档 §1.2 明确区分"已启用"/"未启用"；§2.1 的"已覆盖"指平台级限流基础设施（`IRateLimiter`、`IFlowControlRunner`），它们存在于 `nop-commons`/`nop-rpc` 模块，审计 grep 仅限 `nop-job` 所以未找到。 |
| 问题 7 | docs-for-ai/ 中缺少 nop-job 相关文档 | 驳回 | `docs-for-ai/` 中有 31 处 nop-job 引用，覆盖 INDEX.md、page-dsl-pattern-catalog.md、5 个 runbook 文件、source-anchors.md 等。 |
| 问题 8 | source-anchors.md BIZ-005 锚点路径不规范 | 保留 P3 | 两个文件路径均存在，但 `INopJobScheduleBiz` 位于 `nop-job-dao` 而非标准 `nop-job-biz`，与 `domain-module-pattern.md` 描述的标准模块结构不一致。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 问题 2 | P2（从P0降级） | `ai-dev/design/nop-job/cluster-ha-design.md` | namingService 未在 beans.xml 中注入到 jobPartitionResolver，动态分区切换需要配置指南 |
| 问题 3 | P1 | `ai-dev/design/nop-job/retry-integration-design.md` | `IJobRetryBridge.onFireFailed` 签名文档写 2 参数，实际代码 1 参数 |
| 问题 4 | P2（从P1降级） | `ai-dev/design/nop-job/invoker-design.md` | `DefaultJobInvokerResolver` 代码片段含不存在的 `EXECUTOR_KIND_KEY` 常量 |
| 问题 5 | P1 | `ai-dev/design/nop-job/invoker-design.md` | 4 处引用不存在的 `executorSnapshot` 字段/方法，实际使用直接 `executorKind` String 字段 |
| 问题 8 | P3 | `docs-for-ai/04-reference/source-anchors.md` | BIZ-005 示例的模块结构（接口在 dao 而非 biz）不符合标准领域模块模式 |
