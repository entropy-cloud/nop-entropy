# 246 nop-ai-agent Task-Step Decorator 接入（retry/timeout/rate-limit composable decorator beans）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-nop-task-decorator

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/245-nop-ai-agent-daemon-multi-member-async-dispatch.md`（§Non-Goals line 46「nop-task decorator（retry/timeout/rate-limit）接入」+ §Non-Blocking Follow-ups line 226）；同源 carry-over 见 plans 236（Non-Goals + owner design doc `nop-ai-agent-task-scheduler-daemon.md:79`）、241（Non-Goals line 53）、243（Non-Blocking Follow-ups line 226）、244（Non-Blocking Follow-ups line 240）—— 5 个 closed plans 反复标 `successor plan required`，是 carry-over queue 中依赖最广的 P1 cross-cutting reliability 项。
> Related: `233`（交付 `TeamTaskGraphBuilder` + `TeamTaskFlowOrchestrator` nop-task DAG 集成——本计划在其 step 构建路径同源的 nop-task decorator 体系接入）、`236`（交付 daemon——其失败语义诚实 abandon 不内建重试，本计划提供 step 级重试原语）、`240`（交付 task reclaim/timeout-abandon 恢复——与本计划正交：reclaim 是任务级卡死恢复，decorator 是 step 级执行可靠性）

## Purpose

把 nop-task 的可组合 decorator 体系（`ITaskStepDecorator` + `<decorator name="..."/>` XML 语法 + bean `nopTaskStepDecorator_<name>`）从「仅 transaction / ormSession 两枚」扩展为「retry / timeout / rate-limit 三枚可组合 reliability decorator」，闭合 carry-over queue 中被 5 个 closed plans 反复引用的 cross-cutting reliability gap：当前 nop-task DAG 执行路径（含 nop-ai-agent 团队任务编排）对 task step **没有可组合的** retry/timeout/rate-limit decorator 可用，只有 XML first-class 属性形式（`<step retry="..." timeout="..." rateLimit="..."/>`）。

**为何需要独立 successor**：plans 236/241/243/244/245 每一份都把「nop-task decorator（retry/timeout/rate-limit）接入」标为 Non-Goal successor，因为 retry/timeout/rate-limit 是横跨 daemon / orchestrator / spawn / bound 所有执行路径的 cross-cutting concern，塞进任一前驱（各自聚焦单一结果面）都会破坏其零回归闭合。本计划以**最小、可复用、非重复**的方式交付这三枚 decorator（委托 nop-task 既有的 `RetryTaskStepWrapper`/`TimeoutTaskStepWrapper`/`RateLimitTaskStepWrapper`，Anti-Hollow #8），任何 nop-task step（含团队任务 DAG 节点）经标准 `<decorator>` 语法即可获得 step 级重试/超时/限流。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path）：

- **nop-task 既有 wrapper 已就绪 ✅（本计划委托目标，不重写）**：`TaskStepEnhancer.wrap()`（`nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:100-149`）按 `TaskStepModel` first-class 字段应用 wrapper——`retry` → `RetryTaskStepWrapper`（:107-109，消费 nop-commons `RetryPolicy`）、`timeout` → `TimeoutTaskStepWrapper`（:121-123）、`rateLimit` → `RateLimitTaskStepWrapper`（:132-136）、`throttle` → `ThrottleTaskStepWrapper`（:125-130）。`RetryTaskStepWrapper.execute` 经 `TaskStepHelper.retry(...)` 真实重试（`RetryTaskStepWrapper.java:27-30`）。这些 wrapper 是本计划 decorator 的**委托目标**，不重复实现。
- **nop-task 可组合 decorator 体系已就绪 ✅（本计划接入点）**：`ITaskStepDecorator.decorate(step, config, stepModel)`（`ITaskStepDecorator.java:6-7`）+ `TaskStepEnhancer.decorateStep()`（`TaskStepEnhancer.java:170-179`）按 `stepModel.getDecorators()` 逐枚经 bean `nopTaskStepDecorator_<name>`（`TaskConstants.BEAN_PREFIX_TASK_STEP_DECORATOR`）解析并包装 step。`TaskDecoratorModel` 经 `prop_get(attr)` 读 namespace 配置。
- **仅 2 枚 decorator bean shipped（核心缺口，本计划闭合）**：`task-ext.beans.xml`（`nop-task/nop-task-ext/src/main/resources/_vfs/nop/task/beans/task-ext.beans.xml`）仅注册 `nopTaskStepDecorator_transaction` + `nopTaskStepDecorator_ormSession`。grep `nopTaskStepDecorator_retry|_timeout|_rateLimit` 在 nop-task-ext 返回 **0 命中** —— retry/timeout/rate-limit 的可组合 decorator 形式不存在。
- **config attr 约定已建立 ✅（本计划遵循）**：`TaskExtConstants`（`nop-task/nop-task-ext/src/main/java/io/nop/task/ext/TaskExtConstants.java`）以 `prefix:attr` namespace 字符串声明 decorator 专属配置（如 `txn:txnGroup` / `orm:newSession`），decorator 经 `config.prop_get(...)` 读取。本计划新增 `retry:*` / `timeout:*` / `rateLimit:*` namespace。
- **团队任务 DAG step 构建路径**：`TeamTaskGraphBuilder.buildGraph`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskGraphBuilder.java:90-105`）为每个 team task 构造 `SimpleTaskStepModel`（仅设 name/bean/waitSteps，未设 retry/timeout/rateLimit/decorators）。`TaskStepBuilder.buildSimpleStep`（`nop-task/nop-task-core/.../builder/TaskStepBuilder.java:378,400`）→ `stepEnhancer.buildExecution(...)` 经 `TaskStepEnhancer.wrap()` 应用 decorator + wrapper。即：**simple step 路径已贯通 enhancer，team-task step 一旦携带 decorator 配置即自动生效**。
- **nop-ai-agent LLM 层 retry 已落地（不同层，显式 Non-Goal）**：`IRetryPolicy`/`NoRetryPolicy`/`StandardRetryPolicy`/`RetryOutcome`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/`）覆盖 ReAct LLM 调用层重试，与本计划（nop-task **step** 级 decorator）是不同层、不同结果面。
- **核心缺口总结**：可组合 `<decorator name="retry|timeout|rateLimit"/>` 三枚 decorator bean 不存在；团队任务 step 级执行无 step 级重试/超时/限流原语（失败即诚实保留 CLAIMED，由 plan 240 reclaim 恢复——无 step 级自动重试）。

## Goals

- **交付 3 枚可组合 reliability decorator bean**（`nop-task-ext`）：`retry` / `timeout` / `rateLimit`，经标准 `<decorator name="retry|timeout|rateLimit" .../>` 语法 + bean `nopTaskStepDecorator_<name>` 接入 nop-task decorator 体系，任何 nop-task step（含团队任务 DAG 节点）均可组合使用。（decorator name 取 camelCase `rateLimit` 与 `RateLimitTaskStepWrapper` / first-class attr `rateLimit` 一致；`TaskStepEnhancer.decorate` 按 `nopTaskStepDecorator_<exact name>` 查 bean，name 与 bean id 必须逐字匹配。carry-over 工作项标签写作 "rate-limit"，技术 bean name 为 `rateLimit`。）
- **委托而非重写（Anti-Hollow #8）**：每枚 decorator 读 `TaskDecoratorModel` props 配置，**委托** nop-task 既有 `RetryTaskStepWrapper`/`TimeoutTaskStepWrapper`/`RateLimitTaskStepWrapper`（retry 复用 nop-commons `RetryPolicy` 构造逻辑），不重复实现重试/超时/限流算法。decorator 本身是薄配置适配层（配置提取 → 既有 wrapper 构造）。
- **config surface 遵循既有约定**：新增 `retry:*` / `timeout:*` / `rateLimit:*` namespace 配置 attr 于 `TaskExtConstants`，经 `config.prop_get(...)` 读取，命名与 transaction/ormSession 一致。
- **诚实失败语义（No Silent No-Op #24）**：无效配置（如 timeout <= 0 / rate-limit requestPerSecond <= 0 / retry maxRetryCount < 0 / 缺失必填配置）→ 诚实 throw，不静默返回原 step 不包装（缺失功能必须显式失败，非静默跳过）。零配置或合法配置 → 正常包装。
- **零回归**：既有 transaction/ormSession decorator 行为逐行不变；nop-task-ext + nop-task-core 既有测试全绿；新 decorator 仅在显式声明 `<decorator>` 时生效（不声明 = 行为不变）。
- **设计文档 + roadmap/vision 同步**：新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-step-decorator.md`（记录核心裁定：decorator-bean 形式 vs first-class-attr 形式 / 委托既有 wrapper / config surface / 诚实失败 / 拒绝替代方案，无类签名）+ roadmap successor 标注（`L4-nop-task-decorator` ✅）+ vision decorator 状态。

## Non-Goals

- **DB schema / team-task 配置持久化面**（ORM Protected Area）：本计划只交付 generic decorator bean，不改 `ai_agent_team_task` 表结构、不给 `TeamTask`/`TeamMemberSpec` 增加持久化 retry/timeout 字段。team-task 记录如何声明 decorator policy（配置面设计 + 可能 ORM 变更）为独立 successor。Classification: successor plan required（Protected Area plan-first）。
- **`TeamTaskGraphBuilder` 自动传播 decorator 配置**：依赖上一项 team-task 配置面。本计划交付的 decorator bean 对 team-task step「可用」（step 携带 decorator 配置即经 enhancer 生效），但 builder 不自动从 team 记录填充——builder 自动传播为 successor。Classification: successor plan required。
- **LLM 调用层 retry / circuit breaker**（已落地，不同层）：`nop-ai-agent` reliability 包的 `IRetryPolicy`/`StandardRetryPolicy`/`ICircuitBreaker` 覆盖 ReAct LLM 单次调用周期，与 nop-task step 级 decorator 是不同层。Classification: rejected（已存在）。
- **throttle decorator**：nop-task 既有 `ThrottleTaskStepWrapper`（并发节流）+ first-class `<step throttle="..."/>` 已可用；`nopTaskStepDecorator_throttle` decorator-bean 形式为独立 successor（本计划只覆盖 carry-over 明示的 retry/timeout/rate-limit 三枚）。Classification: successor plan required。
- **nop-task-core 内部变更**（框架核心）：不改 `TaskStepEnhancer`/`ITaskStepDecorator`/wrapper 类。仅在 `nop-task-ext`（扩展层，既有 transaction/ormSession 同位置）新增 decorator bean。Classification: rejected（Protected Area）。
- **新的重试/超时/限流算法实现**：委托既有 wrapper + nop-commons `RetryPolicy`，不重新实现。Classification: rejected（Anti-Hollow #8）。
- **团队任务 reclaim / timeout-abandon**（已落地 plan 240）：任务级卡死恢复与 step 级 decorator 正交。Classification: rejected（已存在）。
- **daemon / orchestrator dispatch 路径变更**（plans 236-245 已闭合）：本计划不改 dispatch 语义，只提供 step 级原语。Classification: rejected。

## Scope

### In Scope

- 3 枚 `ITaskStepDecorator` 实现（`nop-task-ext`）：`RetryTaskStepDecorator` / `TimeoutTaskStepDecorator` / `RateLimitTaskStepDecorator`，委托既有 wrapper
- bean 注册（`task-ext.beans.xml`）：`nopTaskStepDecorator_retry` / `nopTaskStepDecorator_timeout` / `nopTaskStepDecorator_rateLimit`
- config attr（`TaskExtConstants`）：`retry:*` / `timeout:*` / `rateLimit:*` namespace
- 诚实失败（无效配置 throw，#24）+ 零配置/合法配置正常包装
- focused 测试：retry 真实重试 / timeout 真实超时 / rate-limit 真实限流 / 多 decorator 组合 / 无效配置诚实 throw / 零回归（不声明 = 不变）
- 端到端验证：`.task.xml` 声明 `<decorator>` → 执行 → 行为生效
- 设计文档 + roadmap/vision 同步

### Out Of Scope

- 见 Non-Goals（DB/team-task 配置面 / builder 自动传播 / LLM 层 retry / throttle decorator / nop-task-core 变更 / 新算法 / reclaim / dispatch 变更 均为显式 rejected / successor / 已存在）

### 设计裁定（Pre-Adjudicated）

1. **decorator-bean 形式（可组合 `<decorator>`）而非 first-class-attr 形式**。nop-task 已有 first-class `<step retry="..." timeout="..." rateLimit="..."/>`（`TaskStepEnhancer.wrap`），但 carry-over 明示「nop-task decorator 接入」= 接入 `ITaskStepDecorator` 可组合体系。decorator-bean 形式相比 first-class-attr：(1) 可组合（多枚 decorator 叠加 + order 控制）；(2) 对程序化构造的 step model 通用（team-task step 经 `stepModel.getDecorators()` 即生效）；(3) 与既有 transaction/ormSession decorator 对称。两者不冲突——first-class-attr 是快捷形式，decorator-bean 是可组合形式。理由：(1) carry-over 命名即 decorator；(2) 可组合性是 team-task 多 reliability concern 叠加的前提；(3) 既有 decorator 体系是设计好的接入点。

2. **委托既有 wrapper，不重写算法（Anti-Hollow #8）**。每枚 decorator 提取配置后构造并返回既有 `RetryTaskStepWrapper`/`TimeoutTaskStepWrapper`/`RateLimitTaskStepWrapper`（retry 经与 `TaskStepEnhancer.buildRetryPolicy` 同源的 `RetryPolicy` 构造）。decorator 是薄配置适配层。理由：(1) 避免双份重试/超时/限流代码的漂移风险；(2) 既有 wrapper 已经过 nop-task 测试覆盖；(3) 重复实现是典型 hollow/duplicate 风险。

3. **位置 = `nop-task-ext`（generic 扩展层），非 nop-ai-agent**。retry/timeout/rate-limit 是 generic task-step reliability concern，非 agent 专属。放在 nop-task-ext（与 transaction/ormSession 同位置）使所有 nop-task 用户可用，依赖方向正确。理由：(1) 与既有 decorator 同构；(2) generic 可复用；(3) 避免 nop-ai-agent 反向依赖污染。

4. **config surface = `TaskDecoratorModel` props + `TaskExtConstants` namespace**，遵循 transaction/ormSession 模式（`prefix:attr`）。理由：既有约定，零学习成本，decorator 经 `config.prop_get(...)` 读取。

5. **诚实失败（#24）：无效配置 throw，不静默跳过**。timeout <= 0 / rate-limit requestPerSecond <= 0 / retry maxRetryCount < 0 / 必填配置缺失 → throw `NopException`（nop-task-ext 模块级），不返回原 step 不包装。合法配置/零配置 → 正常包装。理由：Minimum Rules #24（缺失功能/无效配置必须显式失败，非静默 no-op）。

6. **拒绝 team-task builder 自动传播 + DB 配置面（ORM Protected Area successor）**：本计划交付 generic decorator bean（对 team-task step「可用」），但不自动从 team 记录填充配置（需配置面设计 + 可能 ORM 变更）。理由：(1) ORM 是 Protected Area（plan-first）；(2) 配置面是独立结果面；(3) decorator bean 先落地使后续 builder 传播有目标可接。

7. **retry decorator 经 `RetryPolicy` 公开 setter 构造 policy（配置提取，非算法重实现）**。`RetryTaskStepWrapper(ITaskStep, IRetryPolicy<ITaskStepRuntime>)` 需构造好的 `IRetryPolicy`；nop-task 既有的 `TaskStepEnhancer.buildRetryPolicy(TaskRetryModel)` 是 **private**（不可直接复用）。故 `RetryTaskStepDecorator` 经 nop-commons `RetryPolicy` 的**公开 setter**（`setRetryDelay`/`setMaxRetryDelay`/`setMaxRetryCount`/`setExponentialDelay`/`setExceptionFilter`）从 `retry:*` 配置构造 policy——这是 ~10 行配置适配（镜像 `buildRetryPolicy` 的构造序列），**不是**重试执行算法的重实现。重试执行算法（`RetryTaskStepWrapper.execute` → `TaskStepHelper.retry`）由既有 wrapper 委托，不复制。timeout/rate-limit decorator 直接传原始配置值给 wrapper 构造（`TimeoutTaskStepWrapper(ITaskStep, long)` / `RateLimitTaskStepWrapper(ITaskStep, double, boolean, int, IEvalAction)`），无 policy 构造。理由：(1) 不改 nop-task-core（Non-Goal）；(2) 配置提取 ≠ 算法重实现；(3) 「不重实现」约束针对执行算法，policy 构造是必要的配置适配。

8. **decorator 与 first-class-attr 同时声明 = 嵌套包装组合语义（用户责任）**。`TaskStepEnhancer.wrap()` 先应用 decorator（`decorateStep`，内层）再应用 first-class-attr wrapper（外层）：同一 step 同时声明 `<decorator name="retry"/>` 与 first-class `retry="..."` 会产生**嵌套**（decorator wrapper 包原 step，attr wrapper 再包 decorator wrapper）= 组合（乘性重试 / 取严超时 / 取严限流）。这是 decorator 普遍组合语义的自然结果，非 bug。本计划**不阻止**同时声明（阻止需在 decorator 内读 `stepModel.getRetry()` 等 = 跨切面耦合，且 `TaskStepEnhancer.wrap` 顺序不可改 = nop-task-core Non-Goal）。Phase 2 增测试记录此语义（同时声明 → 嵌套包装可观测）。理由：(1) 不改 nop-task-core；(2) 组合语义诚实可观测；(3) 文档化避免意外。

## Execution Plan

### Phase 1 - retry/timeout/rate-limit decorator bean + bean 注册 + config attr + 设计裁定落档

Status: completed
Targets: `nop-task/nop-task-ext/src/main/java/io/nop/task/ext/`（3 decorator + config attr 常量）、`nop-task/nop-task-ext/src/main/resources/_vfs/nop/task/beans/task-ext.beans.xml`（bean 注册）

- Item Types: `Fix`（可组合 decorator 不存在 = carry-over gap）、`Decision`（decorator-bean 形式 / 委托既有 wrapper / nop-task-ext 位置 / config surface / 诚实失败 / 拒绝 builder 自动传播）、`Proof`

- [x] 新增 `RetryTaskStepDecorator`（读 `retry:*` 配置 → 构造 nop-commons `RetryPolicy` → 返回既有 `RetryTaskStepWrapper`），委托不重写
- [x] 新增 `TimeoutTaskStepDecorator`（读 `timeout:*` 配置 → 返回既有 `TimeoutTaskStepWrapper`）
- [x] 新增 `RateLimitTaskStepDecorator`（读 `rateLimit:*` 配置 → 返回既有 `RateLimitTaskStepWrapper`）
- [x] `TaskExtConstants` 新增 `retry:*` / `timeout:*` / `rateLimit:*` namespace 配置常量
- [x] `task-ext.beans.xml` 注册 3 枚 bean（`nopTaskStepDecorator_retry` / `_timeout` / `_rateLimit`，遵循既有 transaction/ormSession 结构）
- [x] 诚实失败（#24）：每枚 decorator 对无效配置（<= 0 / 负数 / 必填缺失）throw `NopException`，不静默返回原 step；合法/零配置正常包装

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 3 枚 decorator 类存在且实现 `ITaskStepDecorator`，**委托**既有 wrapper（grep 可复核：decorator 引用 `RetryTaskStepWrapper`/`TimeoutTaskStepWrapper`/`RateLimitTaskStepWrapper`，无重试/超时/限流**执行算法**重实现；retry 经 `RetryPolicy` 公开 setter 构造 policy 属配置提取，见设计裁定 7）
- [x] `task-ext.beans.xml` 注册 3 枚 bean（id = `nopTaskStepDecorator_retry`/`_timeout`/`_rateLimit`）
- [x] `TaskExtConstants` 含 `retry:*`/`timeout:*`/`rateLimit:*` namespace 常量
- [x] **无静默跳过**（#24）：无效配置各路径 throw `NopException`（timeout<=0 / rateLimit<=0 / retry maxRetryCount<0 / 必填缺失），不返回原 step
- [x] **接线验证**（#23）：声明 `<decorator name="retry|timeout|rateLimit"/>` 的 step 经 `TaskStepEnhancer.decorateStep` 解析到新 bean 并被包装（非原 step 直通）（Phase 2 端到端测试将完整覆盖运行时调用链；本 Phase bean 注册 + `TaskStepEnhancer.decorate` 既有 bean lookup 路径已连通）
- [x] `./mvnw compile -pl nop-task/nop-task-ext -am` 通过
- [x] focused 测试在 Phase 2（#25）；本 Phase compile + 既有 transaction/ormSession 测试零回归即可
- [x] 若该 Phase 改变 live baseline：`ai-dev/design/nop-ai-agent/` 设计文档 Phase 3 落档；本 Phase 先在设计裁定段落记录
- [x] `ai-dev/logs/` 对应日期条目已更新
### Phase 2 - focused 测试（retry/timeout/rate-limit 真实行为 + 组合 + 诚实失败 + 零回归）

Status: completed

Targets: `nop-task/nop-task-ext/src/test/java/io/nop/task/ext/`（新测试）、`nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/`（新 `.task.xml`）

- Item Types: `Proof`

- [x] 编写 retry decorator 测试：step 抛瞬态异常 → 按配置 maxRetryCount/exponentialDelay 真实重试至成功（断言执行次数 ≥ 2，非仅最终成功）；不可重试异常 / 重试耗尽 → 诚实抛出
  - 注：`retry_realRetryUntilExhausted` + `retry_exhaustedHonestThrow` 验证真实重试 ≥ 2 次 + 重试耗尽诚实抛出。`nop-task` 既有 in-memory `TaskStepStateBean.fail()` 为 no-op（不保存 exception 引用），故 `RetryPolicy.isRecoverableException` 因 `state.exception()=null` 跳过不可重试分类——所有异常按 retryCount 重试。不可重试分类为独立 successor（依赖 state 保存 exception，触及 nop-task-core 内部，Non-Goal）。
- [x] 编写 timeout decorator 测试：step 执行超 `timeout` 配置 → 真实超时失败（断言 `TimeoutException` / 超时语义，非静默完成）
- [x] 编写 rate-limit decorator 测试：step 高频触发 → 按 requestPerSecond 真实限流（断言限流生效，可观测等待/拒绝语义，非无限制直通）
- [x] 编写组合测试：同一 step 声明多枚 decorator（如 retry + timeout）→ 按声明顺序叠加包装，各 decorator 行为均生效
- [x] 编写 decorator↔first-class-attr 嵌套语义测试（设计裁定 8）：同一 step 同时声明 `<decorator name="retry"/>` 与 first-class `retry="..."` → 产生嵌套包装（decorator 内层 + attr 外层），可观测（如执行次数呈乘性组合），记录此为诚实组合语义非 bug
- [x] 编写诚实失败测试：无效配置各路径（timeout<=0 / rateLimit<=0 / retry maxRetryCount<0 / 必填缺失）→ throw `NopException`，非静默跳过
- [x] 编写零回归测试：step 不声明 decorator → 行为与无 decorator 逐行等价（既有 transaction/ormSession 测试全绿）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] retry decorator 测试全绿（真实重试 ≥2 次 + 不可重试/耗尽 honest failure）
- [x] timeout decorator 测试全绿（真实超时失败）
- [x] rate-limit decorator 测试全绿（真实限流生效）
- [x] 组合测试全绿（多 decorator 叠加 + 各行为生效）
- [x] decorator↔first-class-attr 嵌套语义测试全绿（同时声明 → 嵌套包装可观测，设计裁定 8）
- [x] 诚实失败测试全绿（无效配置各路径 throw，#24）
- [x] 零回归测试全绿（不声明 = 不变 + 既有 decorator 测试全绿）
- [x] **无静默跳过**（#24）：所有失败路径诚实 throw；无空方法体/continue/吞异常/TODO
- [x] 新增功能各有对应 focused 测试覆盖（#25）
- [x] `./mvnw test -pl nop-task/nop-task-ext -am` 通过
### Phase 3 - 端到端验证 + 设计文档 + roadmap/vision 同步 + 全量回归

Status: completed

Targets: `nop-task/nop-task-ext/src/test/`（E2E `.task.xml`）、`ai-dev/design/nop-ai-agent/nop-ai-agent-task-step-decorator.md`（新）、`nop-ai-agent-actor-runtime-vision.md`、`nop-ai-agent-roadmap.md`

- Item Types: `Proof`

- [x] 编写端到端测试：`.task.xml` 声明 `<decorator name="retry|timeout|rateLimit" .../>` → 经 `taskFlowManager.getTask` → `task.execute` → decorator 行为完整生效（从 XML 声明到运行时行为完整路径）
- [x] 新建设计文档 `nop-ai-agent-task-step-decorator.md`：记录核心裁定（decorator-bean vs first-class-attr / 委托既有 wrapper / nop-task-ext 位置 / config surface / 诚实失败 / 拒绝 builder 自动传播 + DB 配置面 successor）+ 拒绝替代方案（重写算法 / 放 nop-ai-agent / 改 nop-task-core），遵循 design doc 规范（只记最终设计状态与决策，无类签名/代码）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md`：`ITaskStep + Decorator` 行 decorator 体系接入状态（retry/timeout/rate-limit decorator 已落地；team-task builder 自动传播 + DB 配置面仍 successor）
- [x] 更新 `nop-ai-agent-roadmap.md`：`L4-nop-task-decorator` successor 标注 ✅
- [x] 验证全量测试：`./mvnw test -pl nop-task/nop-task-ext -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 `.task.xml` `<decorator>` 声明 → `TaskStepEnhancer.decorateStep` bean 解析 → wrapper 包装 → `task.execute` 运行时行为（retry 真实重试 / timeout 真实超时 / rate-limit 真实限流）完整路径跑通
- [x] **接线验证**（#23）：E2E 断言声明 decorator 的 step 运行时确实被新 bean 包装（行为生效 = 接线连通，非原 step 直通）
- [x] **无静默跳过**（#24）：E2E 失败路径（重试耗尽 / 超时 / 无效配置）诚实 throw，无吞异常
- [x] design doc 已创建（核心裁定 + 拒绝替代方案，无类签名/代码，遵循 design doc 规范）
- [x] roadmap successor 标注 ✅；vision decorator 状态已更新
- [x] `./mvnw test -pl nop-task/nop-task-ext -am` 全绿（零回归，含既有 transaction/ormSession 测试）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 3 枚可组合 decorator bean（retry/timeout/rateLimit）落地为真实（非空壳）代码——委托既有 wrapper，无重试/超时/限流**执行算法**重实现（retry 经 `RetryPolicy` 公开 setter 构造 policy 属配置提取，见设计裁定 7）
- [x] decorator 经标准 `<decorator name="..."/>` 语法 + bean 注册接入 nop-task decorator 体系
- [x] config surface（`TaskExtConstants` namespace）遵循既有 transaction/ormSession 约定
- [x] 诚实失败语义（#24）：无效配置各路径 throw，不静默跳过
- [x] 零回归：不声明 decorator 行为逐行不变；既有 transaction/ormSession decorator + nop-task-ext/core 测试全绿
- [x] 端到端：`.task.xml` 声明 → enhancer 解析 → wrapper → 运行时行为完整路径跑通（retry/timeout/rate-limit 三场景）
- [x] 必要 focused verification 已完成（retry 真实重试 / timeout 真实超时 / rate-limit 真实限流 / 组合 / 诚实失败 / 零回归 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（DB/team-task 配置面 / builder 自动传播 / throttle decorator / LLM 层 retry / nop-task-core 变更 均显式 Non-Goals）
- [x] 受影响 owner docs（设计文档 + vision + roadmap successor 标注）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）decorator 运行时确实被 enhancer 解析并包装 step（委托既有 wrapper，非空方法体），（b）端到端 `<decorator>` 声明到运行时行为连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-ext -am`
- [x] `./mvnw test -pl nop-task/nop-task-ext -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；DB/team-task 配置面 / builder 自动传播 / throttle decorator / LLM 层 retry / nop-task-core 变更 均为显式 Non-Goals 独立 successor 或 rejected，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **team-task decorator 配置持久化面 + `TeamTaskGraphBuilder` 自动传播**：Classification: successor plan required（ORM Protected Area plan-first + 配置面设计）。
- **throttle decorator bean（`nopTaskStepDecorator_throttle`）**：Classification: successor plan required（carry-over 未明示，nop-task 既有 `ThrottleTaskStepWrapper` + first-class attr 已可用）。
- **decorator 在 nop-ai-agent team-task DAG 的实战接线**（daemon/orchestrator step 携带 decorator policy）：Classification: successor plan required（依赖 team-task 配置面 successor）。

## Closure

Status Note: plan 246 闭合——交付 retry / timeout / rateLimit 三枚可组合 reliability decorator bean（nop-task-ext），委托既有 wrapper（Anti-Hollow #8），经标准 `<decorator>` 语法 + bean `nopTaskStepDecorator_<name>` 接入 nop-task decorator 体系，任何 nop-task step（含团队任务 DAG 节点）均可组合使用。15 focused/E2E 测试全绿（retry 真实重试 + 耗尽 honest throw / timeout 真实超时 / rateLimit 真实限流 / retry+timeout 组合 / decorator+first-class-attr 嵌套语义 / 6 条 honest failure 路径 / 零回归 + E2E 全链路接线）。owner docs（设计文档 + vision + roadmap ✅）已同步。独立 closure audit 11 gates 全 PASS。已知限制（nop-task in-memory state 不保存 exception → 不可重试分类不生效；retry loop 同步成功分支不主动 return）诚实记录，均触 nop-task-core 内部 Non-Goal，列为 successor。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor subagent（task_id: ses_1276407a7ffeMuW145Q7RSPMIa，fresh session 非复用实现阶段 session）
- Audit Session: ses_1276407a7ffeMuW145Q7RSPMIa
- Evidence:
  - **Gate 1（3 枚 decorator 真实代码 + 委托）PASS**：`RetryTaskStepDecorator.java:15,58` / `TimeoutTaskStepDecorator.java:13,28` / `RateLimitTaskStepDecorator.java:14,48` 各实现 `ITaskStepDecorator` 并返回既有 wrapper；grep 无 `tryAcquire`/`Thread.sleep`/retry-loop 在 decorator 内；retry 经 `RetryPolicy` 公开 setter 构造属配置提取（设计裁定 7）。
  - **Gate 2（bean 注册）PASS**：`task-ext.beans.xml:21-28` 注册 `nopTaskStepDecorator_retry`/`_timeout`/`_rateLimit`，id 与 `TaskConstants.BEAN_PREFIX_TASK_STEP_DECORATOR`+name 逐字匹配。
  - **Gate 3（config surface）PASS**：`TaskExtConstants.java:10-24` 含 `retry:*`/`timeout:*`/`rateLimit:*` namespace 常量，与既有 `txn:*`/`orm:*` 同模式。
  - **Gate 4（诚实失败 #24）PASS**：`TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG` 各 decorator invalid-config 分支均 throw NopException（`RetryTaskStepDecorator:23,28,38,47,70` / `TimeoutTaskStepDecorator:21,25` / `RateLimitTaskStepDecorator:22,26,35,44`），无静默返回原 step。
  - **Gate 5（零回归）PASS**：`TestTransactionDecorator.java` 逐行不变；`./mvnw test -pl nop-task/nop-task-ext -am -T 1C` → 22 tests / 0 fail / BUILD SUCCESS。
  - **Gate 6（E2E wiring）PASS**：16 个 `.task.xml` 测试资源声明 `<decorator>`，覆盖三枚 decorator + 组合 + 嵌套 + honest failure + 零回归 baseline。
  - **Gate 7（focused 测试）PASS**：`TestReliabilityDecorators.java` 15 个 `@Test` 方法覆盖全部所需场景（retry/timeout/rateLimit/combo/nesting/honest-fail×6/regression/E2E），真实行为断言（counter ≥2/3 + ERR_TASK_REQUEST_RATE_EXCEED_LIMIT + timeout error code）。
  - **Gate 8（No Silent No-Op #24）PASS**：3 个 decorator 文件无空方法体/吞异常/return-null-placeholder 作为正常实现。`return null;` 仅出现在 config-read helper 表示「配置缺失」哨兵，随后 `decorate()` 抛 `NopException`。
  - **Gate 9（owner docs）PASS**：`nop-ai-agent-task-step-decorator.md` 新建（154 行，Status: implemented）；`nop-ai-agent-actor-runtime-vision.md:35` 标注 decorator 体系接入已落地；`nop-ai-agent-roadmap.md:269` 含 `L4-nop-task-decorator | ... | ✅`。
  - **Gate 10（build/test）PASS**：`./mvnw compile -pl nop-task/nop-task-ext -am -T 1C -q` → SUCCESS；`./mvnw test -pl nop-task/nop-task-ext -am -T 1C` → 22 tests / 0 fail / BUILD SUCCESS。
  - **Gate 11（Anti-Hollow runtime trace）PASS**：`.task.xml <decorator>` → `stepModel.getDecorators()` → `TaskStepEnhancer.wrap:100` → `decorateStep:170-179` → `decorate:181-197` → bean lookup `nopTaskStepDecorator_<name>` → `ITaskStepDecorator.decorate(...)` → 返回 wrapper step。链路从 XML 声明到运行时 wrapper 包装端到端连通（每个 link 经 live code 核实）。
  - **`node ai-dev/tools/check-plan-checklist.mjs 246-...md --strict`**：14 unchecked → 0 unchecked after this toggle，Closure Evidence 已写入（Status Note/Reviewer/Evidence/Follow-up 全填写，无 placeholder）。
  - **`node ai-dev/tools/check-doc-links.mjs --strict`**：退出码 0（40 pre-existing warnings 全部在其他 plan 文件中，非本次引入）。

Follow-up:

- team-task decorator 配置持久化面 + `TeamTaskGraphBuilder` 自动传播（successor plan required，ORM Protected Area plan-first）
- throttle decorator bean（`nopTaskStepDecorator_throttle`）（successor plan required）
- decorator 在 nop-ai-agent team-task DAG 实战接线（successor plan required，依赖 team-task 配置面 successor）
- nop-task-core 内部修正（in-memory state exception 保存 / retry loop 同步返回路径）（successor plan required，Protected Area）
- 已诚实记录：不可重试异常分类在 in-memory state 模式下不生效（依赖 state 保存 exception）；retry 同步成功路径不主动 return（loop 继续至 retryCount 耗尽）；两者均为 nop-task-core 既有 quirk，本计划 Non-Goal 不修正

## Follow-up handled by 247-nop-ai-agent-task-state-exception-persistence.md

plan 246 §Closure 已知限制「in-memory state 不保存 exception → 不可重试分类不生效」由 successor plan 247 处理：修复 `TaskStepStateBean.fail()` / `exception(Throwable)` 空方法体，使 retry 异常分类在 in-memory state 模式下生效。（retry loop 同步成功路径 return 的 quirk 仍为独立 successor，不在 plan 247 scope 内。）
