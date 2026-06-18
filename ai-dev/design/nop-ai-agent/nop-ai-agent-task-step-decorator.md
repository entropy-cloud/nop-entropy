# nop-ai-agent Task-Step Decorator（retry / timeout / rate-limit）

> Status: implemented
> Last Reviewed: 2026-06-18
> Source: `ai-dev/plans/246-nop-ai-agent-task-step-decorator.md`
> Related: `nop-ai-agent-task-flow-integration.md`（plan 233 DAG 集成）、`nop-ai-agent-task-scheduler-daemon.md`（plan 236 daemon）、`nop-ai-agent-team-task-reclaim.md`（plan 240 reclaim）

## Purpose

记录 nop-task 可组合 decorator 体系扩展的核心裁定：把 nop-task 既有 `<decorator>` 接入点从「仅 transaction / ormSession 两枚」扩展为「retry / timeout / rate-limit 三枚可组合 reliability decorator」。本设计文档只记最终设计状态与决策，无类签名/代码（源码是唯一事实）。

## Domain

retry / timeout / rate-limit 是横切可靠性关注点（cross-cutting reliability concerns），非 agent 专属。位置 = `nop-task-ext`（generic 扩展层），与既有 `transaction` / `ormSession` decorator 同位置，所有 nop-task 用户可用，依赖方向正确（nop-task-ext 不反向依赖 nop-ai-agent）。

## 核心设计裁定

### 1. decorator-bean 形式（可组合 `<decorator>`）vs first-class-attr 形式

**裁定：decorator-bean 形式（接入 `ITaskStepDecorator` 可组合体系）**。

nop-task 已有 first-class `<step retry="..." timeout="..." rateLimit="..."/>`（`TaskStepEnhancer.wrap`），但 carry-over 明示「nop-task decorator 接入」= 接入 `ITaskStepDecorator` 可组合体系。

decorator-bean 形式相比 first-class-attr：
- **可组合**：多枚 decorator 叠加 + `order` 控制（`TaskDecoratorModel.order`）
- **通用**：对程序化构造的 step model 通用（team-task step 经 `stepModel.getDecorators()` 即生效，无需 first-class attr）
- **对称**：与既有 transaction/ormSession decorator 对称

两者不冲突——first-class-attr 是快捷形式，decorator-bean 是可组合形式。同一 step 同时声明会嵌套包装（见裁定 8）。

### 2. 委托既有 wrapper，不重写算法（Anti-Hollow #8）

**裁定：每枚 decorator 提取配置后构造并返回既有 `RetryTaskStepWrapper` / `TimeoutTaskStepWrapper` / `RateLimitTaskStepWrapper`**。

decorator 是薄配置适配层（配置提取 → 既有 wrapper 构造）。重试/超时/限流执行算法（`RetryTaskStepWrapper.execute` → `TaskStepHelper.retry` / `TimeoutTaskStepWrapper.execute` → `TaskStepHelper.timeout` / `RateLimitTaskStepWrapper.execute` → `IRateLimiter.tryAcquire`）由既有 wrapper 委托，不复制。

理由：
- 避免双份执行代码的漂移风险
- 既有 wrapper 已经过 nop-task-core 测试覆盖
- 重复实现是典型 hollow/duplicate 风险

### 3. retry decorator 经 `RetryPolicy` 公开 setter 构造 policy（配置提取，非算法重实现）

`RetryTaskStepWrapper(ITaskStep, IRetryPolicy<ITaskStepRuntime>)` 需构造好的 `IRetryPolicy`。nop-task 既有的 `TaskStepEnhancer.buildRetryPolicy(TaskRetryModel)` 是 **private**（不可直接复用，且修改为 public 触及 nop-task-core Non-Goal）。

**裁定：`RetryTaskStepDecorator` 经 nop-commons `RetryPolicy` 的公开 setter（`setRetryDelay` / `setMaxRetryDelay` / `setMaxRetryCount` / `setExponentialDelay` / `setExceptionFilter`）从 `retry:*` 配置构造 policy**。

这是 ~10 行配置适配（镜像 `buildRetryPolicy` 的构造序列），**不是**重试执行算法的重实现。重试执行算法（`RetryTaskStepWrapper.execute` → `TaskStepHelper.retry`）由既有 wrapper 委托，不复制。

「不重实现」约束针对执行算法；policy 构造是必要的配置适配。

timeout/rate-limit decorator 直接传原始配置值给 wrapper 构造（`TimeoutTaskStepWrapper(ITaskStep, long)` / `RateLimitTaskStepWrapper(ITaskStep, double, boolean, int, IEvalAction)`），无 policy 构造。

### 4. config surface = `TaskDecoratorModel` props + `TaskExtConstants` namespace

**裁定：遵循 transaction/ormSession 模式（`prefix:attr` namespace 字符串常量）**。

decorator 专属配置经 `<decorator name="retry" retry:maxRetryCount="2"/>` 形式传入，XDsl parser 自动把带 `:` 的属性放入 `TaskDecoratorModel.extProps`，decorator 经 `config.prop_get(...)` 读取。XML 属性值为 String，decorator 内部经 `ConvertHelper` 转换为目标类型。

namespace 常量集中在 `TaskExtConstants`：
- `retry:maxRetryCount` / `retry:retryDelay` / `retry:maxRetryDelay` / `retry:exponentialDelay`
- `timeout:timeout`
- `rateLimit:requestPerSecond` / `rateLimit:global` / `rateLimit:maxWait`

理由：既有约定，零学习成本，与 transaction（`txn:txnGroup` / `txn:propagation`）/ ormSession（`orm:newSession`）一致。

### 5. 诚实失败（No Silent No-Op #24）

**裁定：无效配置各路径 throw `NopException`（nop-task-ext 模块级 `TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG`），不静默返回原 step 不包装**。

无效配置定义：
- `timeout:timeout` <= 0 或缺失（必填）
- `rateLimit:requestPerSecond` <= 0 或缺失（必填）
- `retry:maxRetryCount` < 0 或缺失（必填）
- `retry:retryDelay` / `retry:maxRetryDelay` < 0
- `rateLimit:maxWait` < 0
- 类型转换失败（如非数字字符串）

合法配置 / 未声明 decorator（不进入 decorate 路径）→ 正常行为。

理由：Minimum Rules #24（缺失功能/无效配置必须显式失败，非静默 no-op）。

### 6. 拒绝 team-task builder 自动传播 + DB 配置面（ORM Protected Area successor）

**裁定：本计划只交付 generic decorator bean（对 team-task step「可用」），不自动从 team 记录填充配置**。

`TeamTaskGraphBuilder.buildGraph` 为每个 team task 构造 `SimpleTaskStepModel`（仅设 name/bean/waitSteps）。step 一旦携带 `decorators` 配置即自动经 `TaskStepEnhancer.decorateStep` 生效——但 builder 不自动从 team 记录填充。

理由：
- ORM 是 Protected Area（plan-first）
- 配置面（team/task 记录如何声明 decorator policy）是独立结果面
- decorator bean 先落地使后续 builder 传播有目标可接

### 7. decorator name 取 camelCase（与 bean id 逐字匹配）

**裁定：decorator name = `retry` / `timeout` / `rateLimit`（camelCase）**。

`TaskStepEnhancer.decorate` 按 `nopTaskStepDecorator_<exact name>`（`TaskConstants.BEAN_PREFIX_TASK_STEP_DECORATOR` + name）查 bean，name 与 bean id 必须逐字匹配。

carry-over 工作项标签写作 "rate-limit"（kebab-case），技术 bean name 为 `rateLimit`（与 `RateLimitTaskStepWrapper` / first-class attr `rateLimit` 一致）。

### 8. decorator 与 first-class-attr 同时声明 = 嵌套包装组合语义（用户责任）

**裁定：本计划不阻止同时声明，嵌套包装组合语义是 decorator 普遍组合的自然结果**。

`TaskStepEnhancer.wrap()` 先应用 decorator（`decorateStep`，内层）再应用 first-class-attr wrapper（外层）：同一 step 同时声明 `<decorator name="retry"/>` 与 first-class `<retry .../>` 会产生嵌套（decorator wrapper 包原 step，attr wrapper 再包 decorator wrapper）= 组合（乘性重试 / 取严超时 / 取严限流）。

阻止同时声明需在 decorator 内读 `stepModel.getRetry()` 等 = 跨切面耦合，且 `TaskStepEnhancer.wrap` 顺序不可改 = nop-task-core Non-Goal。

测试 `nesting_decoratorAndFirstClassAttrProduceNestedWrap` 记录此语义（同时声明 → 嵌套包装可观测）。

## 已知限制（诚实记录）

- **nop-task in-memory `TaskStepStateBean.fail()` 为 no-op**：不保存 exception 引用，故 `RetryPolicy.getRetryDelay(state.exception(), ...)` 接收 null exception 跳过 `isRecoverableException` 判定——所有异常均按 retryCount 重试（不可重试分类在 in-memory 模式下不生效）。`bizFatal` 标记或自定义 `exceptionFilter` 在持久化 state store 模式下才会生效。不可重试分类的运行时检测为独立 successor（依赖 state 保存 exception，触及 nop-task-core 内部，本计划 Non-Goal）。
- **`RetryTaskStepWrapper` 同步返回路径不主动 return 成功结果**：`TaskStepHelper.retry` 同步成功分支无显式 `return result;`，loop 继续直到 retryCount 耗尽。Async 路径（`doRetry` helper）正常返回成功结果。该行为是 nop-task 既有 quirk（不在本计划 scope 修正，nop-task-core 内部变更为 Non-Goal），decorator 测试以「真实重试 ≥ 2 次 + 耗尽抛出」语义验证。

## 拒绝的替代方案

| 替代方案 | 拒绝原因 |
|---------|---------|
| 重写重试/超时/限流执行算法（不委托 wrapper） | Anti-Hollow #8：双份代码漂移风险 + 重复实现 |
| 放在 nop-ai-agent 模块（agent 专属） | 位置错：retry/timeout/rateLimit 是 generic task-step reliability concern，非 agent 专属；反向依赖污染 |
| 改 nop-task-core（暴露 `buildRetryPolicy` 为 public / 修 `TaskStepStateBean.fail()` / 修 retry loop 同步返回路径） | Protected Area（plan-first）+ 跨切面影响既有用户 |
| 阻止 decorator 与 first-class-attr 同时声明 | 跨切面耦合 + `TaskStepEnhancer.wrap` 顺序不可改 = nop-task-core Non-Goal |
| team-task builder 自动传播 decorator 配置 + DB 配置面 | ORM Protected Area successor；独立结果面 |

## Non-Goals / Successors

- **team-task decorator 配置持久化面 + `TeamTaskGraphBuilder` 自动传播**：Classification: successor plan required（ORM Protected Area plan-first + 配置面设计）
- **throttle decorator bean（`nopTaskStepDecorator_throttle`）**：Classification: successor plan required（carry-over 未明示；nop-task 既有 `ThrottleTaskStepWrapper` + first-class attr 已可用）
- **decorator 在 nop-ai-agent team-task DAG 的实战接线**（daemon/orchestrator step 携带 decorator policy）：Classification: successor plan required（依赖 team-task 配置面 successor）
- **LLM 调用层 retry / circuit breaker**：Classification: rejected（已落地，不同层——`nop-ai-agent` reliability 包覆盖 ReAct LLM 单次调用周期）
- **nop-task-core 内部变更**（修 in-memory state exception 引用 / 修 retry loop 同步返回 / 暴露 `buildRetryPolicy`）：Classification: rejected（Protected Area）
- **新的重试/超时/限流算法实现**：Classification: rejected（Anti-Hollow #8）
- **团队任务 reclaim / timeout-abandon**：Classification: rejected（已落地 plan 240，正交）
- **daemon / orchestrator dispatch 路径变更**：Classification: rejected（plans 236-245 已闭合）

## 接入点

- 3 枚 decorator 类：`io.nop.task.ext.retry.RetryTaskStepDecorator` / `io.nop.task.ext.timeout.TimeoutTaskStepDecorator` / `io.nop.task.ext.ratelimit.RateLimitTaskStepDecorator`
- 配置常量：`io.nop.task.ext.TaskExtConstants`（`ATTR_RETRY_*` / `ATTR_TIMEOUT_*` / `ATTR_RATE_LIMIT_*` namespace 常量）
- 错误码：`io.nop.task.ext.TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG`
- bean 注册：`nop-task/nop-task-ext/src/main/resources/_vfs/nop/task/beans/task-ext.beans.xml`（`nopTaskStepDecorator_retry` / `_timeout` / `_rateLimit`）

使用示例（任何 nop-task step）：

```xml
<step name="...">
    <decorator name="retry" retry:maxRetryCount="3" retry:retryDelay="1000"/>
    <decorator name="timeout" timeout:timeout="30000"/>
    <decorator name="rateLimit" rateLimit:requestPerSecond="10" rateLimit:global="true"/>
    ...
</step>
```
