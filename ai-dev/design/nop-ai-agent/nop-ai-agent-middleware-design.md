# Middleware 洋葱链设计

> Status: final
> Date: 2026-07-17
> Scope: nop-ai-agent Hook 系统架构升级
> Motivation: AgentScope 独立审计发现 Hook 系统有语义优势（Veto/Reenter）但架构结构弱（扁平 List 无组合能力）

---

## 一、问题分析

### 当前 Hook 系统的结构

```
IAgentLifecycleHook.onEvent(HookContext) → HookResult (Pass/Veto/Reenter)
```

各 Hook 在同一生命周期点以**扁平列表**执行：

```
for each hook:
    result = hook.onEvent(ctx)
    if veto → break
```

**问题**：每个 Hook 是独立观察者，无法封装横切关注点。

**对比**：AgentScope 的 MiddlewareBase 用 `next.apply()` 去中心化包装：

```
MW1.before → MW2.before → CORE → MW2.after → MW1.after
```

每个 Middleware 持有 `next`，自主决定何时/是否调用下一层——日志、权限、重试等可任意组合。

### nop 的 Hook 优于 AgentScope 的地方

| 维度 | nop | AgentScope |
|------|-----|-----------|
| **语义控制** | `VetoResult` 停止、`ReenterResult` 重入 | 仅 before/after |
| **生命周期粒度** | 12 个 `AgentLifecyclePoint` | 5 个拦截点 |

**方案是吸收而非替换**：保留 Reenter/Veto 语义，新增洋葱链执行结构，与 Hook 双轨共存。

---

## 二、设计

### 2.1 新增接口

```java
// io.nop.ai.agent.middleware.IAgentMiddleware
public interface IAgentMiddleware {
    HookResult execute(HookContext ctx, MiddlewareChain next);
}
```

```java
// io.nop.ai.agent.middleware.MiddlewareChain
public final class MiddlewareChain {
    private final List<IAgentMiddleware> middlewares;
    private final int index;
    private final Function<HookContext, HookResult> core;

    public HookResult proceed(HookContext ctx) {
        if (index >= middlewares.size())
            return core.apply(ctx);
        return middlewares.get(index).execute(ctx,
            new MiddlewareChain(middlewares, index + 1, core));
    }
}
```

`MiddlewareChain` 的 core 是一个 `Function<HookContext, HookResult>`，其实现为 `invokeHooks(point, ...)`（该生命周期点的所有 Hook 观察者循环）。

### 2.2 与现有 Hook 的关系（双轨共存）

| | `IAgentLifecycleHook`（保持） | `IAgentMiddleware`（新增） |
|--|------------------------------|--------------------------|
| 角色 | 事件观察者 | 拦截控制器 |
| 控制流 | 不能跳过 core | 可跳过/包装 core |
| 返回值 | `HookResult` | `HookResult`（同一套） |
| 排列 | 按 priority 排序 | 从外到内链式 |
| 组合 | 独立执行 | `next.proceed()` 委托 |
| 用例 | 审计日志、遥测 | 权限检查、速率限制、重试、技能注入 |

**执行顺序**（以 PRE_REASONING 为例）：

```
Middleware-1.before
  Middleware-2.before
    Hook-1 (观察者)
    Hook-2 (观察者)
    CORE (实际推理逻辑)
  Middleware-2.after
Middleware-1.after
```

Middleware 包裹 core，Hook 在 core 执行前后（Middleware 层内）按 priority 触发。

### 2.3 启用链式的 `AgentLifecyclePoint`

`AgentLifecyclePoint` 共 12 个，其中 **9 个启用链式拦截**：

| 生命周期点 | 需要链？ | 理由 |
|-----------|---------|------|
| `PRE_CALL` | **是** | 日志 + 权限 + 速率限制组合 |
| `PRE_REASONING` | **是** | 预算检查 + 模型路由 + Prompt 注入 |
| `POST_REASONING` | **是** | 输出筛选 + 审计 |
| `PRE_ACTING` | **是** | 权限 + 拒绝账本 + 沙箱 |
| `POST_ACTING` | **是** | 审计 + 用量记录 |
| `POST_CALL` | **是** | 清理 + 关机检查 |
| `PRE_COMPACT` | **是** | 压缩前状态保存 + 验证 |
| `BEFORE_TOOL_RESULT_PROCESSED` | **是** | 结果校验 + 转换 |
| `AFTER_TOOL_RESULT_PROCESSED` | **是** | 结果校验 + 转换 |
| `ON_ERROR` | **否** | 单点通知无需链 |
| `REASONING_CHUNK` | **否** | 流式块通知 |
| `POST_COMPACT` | **否** | 单点通知 |

`ReActAgentExecutor.executeWithMiddleware()` 在 9 个链式点调用 `MiddlewareChain.proceed()`，在 3 个非链式点（ON_ERROR / REASONING_CHUNK / POST_COMPACT）直接调用 `invokeHooks()`。

### 2.4 装配

Middleware 在**装配时**一次性注册到 `IHookRegistry`，之后不可变。

`IHookRegistry` 新增两个 default 方法：
- `List<IAgentMiddleware> getMiddlewares(AgentLifecyclePoint point, String agentName)` — 默认返回空列表
- `void registerMiddleware(AgentLifecyclePoint point, IAgentMiddleware middleware)` — 默认抛 `UnsupportedOperationException`

`DefaultHookRegistry` 覆盖实现这两个方法，并新增 `buildChain(point, core)` 便捷方法。

`DefaultAgentEngine.resolveExecutor()` 在 `fromAgentModel` + `resolveHookContributions` 之后调用 `resolveMiddlewares(model, hookRegistry)`，从 AgentModel 的 `<middlewares>` 声明实例化并注册。

**设计决定**：运行时不对 Middleware 链做动态重排（不同于 AgentScope 的 `addMiddleware` 运行时修改）。理由：Nop 的 IoC 提供了声明式装配，动态重排带来的复杂度 > 收益。

### 2.5 Veto/Reenter 在链中的语义

- **Middleware 返回 `VetoResult`**：该 Middleware 不调 `next.proceed()`，外层 Middleware 的 `next.proceed()` 返回该 Veto 结果，外层 after 逻辑仍执行。该生命周期点整体拒绝（core 不执行）。
- **Middleware 返回 `ReenterResult`**：链中断，返回给调用方，由 `executeWithMiddleware` 的调用者（ReActAgentExecutor）处理重入逻辑（带重入计数器防死循环）。仅在 `BEFORE_TOOL_RESULT_PROCESSED` / `AFTER_TOOL_RESULT_PROCESSED` 有效。

---

## 三、改动范围

| 改动 | 范围 | 向后兼容？ |
|------|------|-----------|
| 新增 `IAgentMiddleware` 接口 | `nop-ai-agent/middleware/` | ✅ 新增 |
| 新增 `MiddlewareChain` 类 | `nop-ai-agent/middleware/` | ✅ 新增 |
| `IHookRegistry` 新增 `getMiddlewares` / `registerMiddleware` default 方法 | `nop-ai-agent/hook/` | ✅ default 方法 |
| `NoOpHookRegistry` 覆盖实现 | `nop-ai-agent/hook/` | ✅ getMiddlewares 返空、register 抛异常 |
| `DefaultHookRegistry` 实现 middleware 存储 + `buildChain` | `nop-ai-agent/hook/` | ✅ 已有注册路径不变 |
| `ReActAgentExecutor` 9 个点位改为 `executeWithMiddleware` | `nop-ai-agent/engine/` | ✅ 无 middleware 时直接调 `invokeHooks` |
| `AgentModel` 新增 `<middlewares>` xdef 声明 | `nop-xdefs` | ✅ 新增可选字段 |
| `DefaultAgentEngine.resolveExecutor` 新增 `resolveMiddlewares` | `nop-ai-agent/engine/` | ✅ 无声明时 no-op |

**不删除** `IAgentLifecycleHook`，**不修改** `HookResult` 密封层级。

**会话级 `AgentLifecyclePoint` 枚举（12 个值）的值与语义完全不变。** plan 296 §三的"不修改 `AgentLifecyclePoint` 枚举值"约束，其 scope 是 plan 296 的会话级洋葱链实现——在**已有**点上启用链式拦截，不在枚举里加新点。W3-1（§5.1）新增的执行级触发点由**独立的 `ExecutionPoint` 枚举**承载（`PRE_LLM_ATTEMPT`/`POST_LLM_ATTEMPT`/`PRE_TOOL_ATTEMPT`/`POST_TOOL_ATTEMPT`），不污染会话级枚举。两个枚举、两层 scope、两套 registry 存储（`Map<AgentLifecyclePoint,List>` + `Map<ExecutionPoint,List>`）永不交叉。

---

## 四、验证

1. 现有 Hook 测试全部通过（行为不变，无 middleware 时 `executeWithMiddleware` 直接调 `invokeHooks`）
2. Middleware 3 层 wrapping 断言（outer.before → mid.before → inner.before → core → inner.after → mid.after → outer.after）
3. Veto 在链中间层生效（core 和后续层不执行，外层 after 仍执行）
4. Reenter 从链中断返回（带重入计数器）
5. Middleware + Hook 混合时序：Middleware 包裹 core，Hook 在 core 内触发

---

## References

- AgentScope `MiddlewareBase.java:26-43`（5 拦截点定义）
- AgentScope `MiddlewareChain.java:46-62`（从后往前组装算法）
- 现有 `IAgentLifecycleHook` + `HookResult`（保留语义）
- `ai-dev/analysis/agent-survey/2026-07-16-agentscope-vs-nop-ai-agent-deep-comparison.md` §8.1
- `ai-dev/plans/296-nop-ai-agent-middleware-and-tool-tag-system-implementation.md`

---

## 五、双层中间件与声明式 filter chain（2026-08-01）

> 来源：agent-survey（hive 双层中间件 / plano 声明式 filter chain）。nop middleware 洋葱链已超越外部实现；§5.1（双层中间件）与 §5.2（声明式 filter chain）是两个结构性增量，均已落地为 final。

### 5.1 双层中间件（retry 时重新评估）— final

> **Status: final**（W3-1 已落地，2026-08-01）。本节由方向性描述重写为最终架构决策，含 D1/D2/D3/D4 裁定、双层表、触发点清单、retry 重评估语义、Veto 控制流映射、线程模型。

nop middleware 原本是"每请求一次"（会话级）。本节新增**执行级**层（对标 hive 的 PipelineStage + ExecutionMiddleware），使每次 LLM/工具调用尝试都经过中间件拦截，**retry/resurrection 时安全检查重新评估**（核心价值：attempt N 改变的状态对 attempt N+1 的安全检查必须可见）。

#### 双层表

| 层级 | scope | 触发 | 触发点枚举 | 用途 |
|------|-------|------|-----------|------|
| **会话级**（现有，plan 296） | `session`（默认） | 每请求一次 | `AgentLifecyclePoint`（9 个链式点：PRE_CALL/PRE_REASONING/...） | 认证/限流/路由 |
| **执行级**（新增，W3-1） | `execution` | 每次 LLM/工具尝试 | `ExecutionPoint`（4 个：PRE/POST_LLM_ATTEMPT、PRE/POST_TOOL_ATTEMPT） | 熔断检查、安全拦截——**retry 时重新评估** |

两层**复用同一** `IAgentMiddleware` 接口 + `MiddlewareChain`（洋葱链执行模型不变），仅在声明层（`<middleware scope="..."/>`）和 registry 存储层区分 scope。

#### D1：scope 建模（裁定：方案 B 强化为独立 ExecutionPoint 枚举）

- **采纳**：`<middleware>` 增 `scope` 属性（默认 `session`，零回归）。会话级走 `AgentLifecyclePoint`（不变），执行级走**新建独立 `ExecutionPoint` 枚举**。
- **拒绝方案 A**（向 `AgentLifecyclePoint` 加执行级值）：两种 scope 概念混入同一枚举，违反"会话级 12 值语义不变"且语义混乱。
- **拒绝方案 C**（平行新接口 `IExecutionMiddleware`）：nop 风格倾向复用，执行级与会话级执行模型（洋葱链）完全相同，无需新接口。
- registry 用 scope 维度分离存储：会话级 `Map<AgentLifecyclePoint,List>`（不变）+ 执行级 `Map<ExecutionPoint,List>`（新增 `getExecutionMiddlewares`/`registerExecutionMiddleware`）。两 scope 永不交叉。

#### D2：attempt 级上下文（裁定：强类型 `AttemptContext` 挂在 `HookContext`）

- **采纳**：新建强类型 `AttemptContext`（`attempt`(int,0-based) / `retry`(boolean) / `lastErrorClassification`(`ErrorClassification`)），经 `HookContext.getAttemptContext()` 暴露，仅执行级触发时填充（会话级为 null）。
- **拒绝方案 a**（`HookContext.data` Map 弱类型）：违反 nop 强类型风格。
- **拒绝方案 b 原版**（挂 `AgentExecutionContext`）：`AgentExecutionContext` 是 per-request，attempt 是 retry loop 内瞬态值，挂在 per-request 对象上语义错误。挂在 `HookContext`（per-invocation）才是 attempt 级信息的正确归属。

#### 执行级触发点清单

| 触发点 | 位置 | 触发频率 | veto 语义 |
|--------|------|---------|----------|
| `PRE_LLM_ATTEMPT` | `LlmCallCoordinator.doLlmCallWithRetry` retry loop 内，`callChatWithTimeout` **前**（try 块前） | 每次 attempt（含 retry） | 跳过本次调用 → 合成 NON_TRANSIENT 失败 → retry 决策 |
| `POST_LLM_ATTEMPT` | 同上，`callChatWithTimeout` 返回**后**、success/错误分类**前** | 每次返回响应的 attempt（传输异常无响应，不触发） | 拒绝响应 → 合成 NON_TRANSIENT → retry 决策 |
| `PRE_TOOL_ATTEMPT` | `AgentToolDispatcher.executeAllowedCalls` fan-out 循环内，提交 future **前**（同步，D4） | 每个工具调用 | 该工具产错误 result，不提交 future，不影响同 batch 其他工具 |
| `POST_TOOL_ATTEMPT` | 同上，结果处理循环内，join **后**、commit **前** | 每个工具调用 | 该工具 result 替换为错误 result，不影响同 batch 其他工具 |

#### D3：Veto → retry loop 控制流映射（裁定：synthetic NON_TRANSIENT + retryPolicy 决策 + veto cap）

- **PRE/POST_LLM_ATTEMPT Veto** → 构造 synthetic 失败 attempt（`ChatResponse.error(NON_TRANSIENT,...)`，error="vetoed by ... middleware: <reason>"），喂入现有 `retryPolicy.shouldRetry()` 决策路径（由 retryPolicy 决 RETRY/STOP/FALLBACK，**非无条件 retry**）。
- **veto ≠ 模型失败**：veto 路径**不记录** `circuitBreaker.recordFailure`（不污染熔断器——熔断器追踪的是模型连续失败，veto 是安全否决）。
- **防无限循环**：`LlmCallCoordinator.MAX_EXECUTION_VETOES = 3`（与 `DEFAULT_MAX_REENTRIES` 一致），跨 attempt 累计 veto 次数，超限强制 fail-loud（抛 `NopAiAgentException`，不静默 continue）。防止"中间件每次 veto + retryPolicy 每次 RETRY → 无限循环"。
- **工具侧 Veto** → 该单工具调用产 `AiToolCallResult.errorResult`（不 retry 工具，工具无 retry 机制），不影响同 batch 其他工具调用。

#### D4：工具 dispatch 线程模型（裁定：方案 a — 同步 before + 异步执行 + 同步 after）

- **采纳方案 a**：PRE_TOOL_ATTEMPT 在 fan-out 循环内、提交 future **前**同步触发（调用线程上）；POST_TOOL_ATTEMPT 在结果处理循环内、join **后**、commit **前**同步触发（调用线程上）。
- **拒绝方案 b**（在 `CompletableFuture.supplyAsync` 内、池线程上触发）：安全检查应在工具执行前**确定性**完成，不应受线程池调度影响；且 `HookContext` 线程安全需额外保证。
- **Anti-Hollow 红线**：执行级中间件调用的返回值**必须检查**（veto 生效）。现有 `AgentToolDispatcher:282` 的 PRE_ACTING `executeWithMiddleware` 返回值被**丢弃**——执行级中间件**绝不**复用此模式（已在 PRE/POST_TOOL_ATTEMPT 实现中显式检查 `isVeto()`，并有测试 `TestExecutionMiddlewareToolDispatch` 对比验证）。

#### retry 重评估语义（核心价值）

retry loop 内每次 attempt 重新触发 PRE/POST_LLM_ATTEMPT，携带 `AttemptContext(attempt=N+1, retry=true, lastErrorClassification=<上次分类>)`。安全/熔断检查据此**重新评估**——attempt N 改变的状态（如工具调用消耗的配额、注入的 prompt）对 attempt N+1 可见。测试 `TestExecutionMiddlewareLlmRetry.retryReEvaluatesExecutionMiddlewareCarryingRetrySignalAndLastClassification` 验证此路径。

### 5.2 声明式 filter chain（DSL 声明有序 ID 列表）— final

> **Status: final**（W3-2 已落地，2026-08-01）。本节由方向性描述重写为最终架构决策，含 D1/D2/D3 裁定、filter-chain DSL 结构、input/output 映射表（含多次触发语义分析）、与 `<middlewares>` 共存合并规则、ResolvedFilterChain 模式。

nop middleware 原本是**代码类装配**（`<middleware impl="class-name" point="..."/>`，类名硬编码在 agent 模型）。本节引入**声明式 filter chain**（对标 plano `AgentFilterChain` / `ResolvedFilterChain` / `FilterPipeline`）：DSL 声明有序 filter ID 列表，input（请求侧）/ output（响应侧）双链独立配置，ID 在装配时解析为 `IAgentMiddleware` 实例。这使 guardrail 管道**可审计、可序列化**（声明侧 filter IDs 与执行侧 resolved 对象保持同步），且**零代码编排**。

#### DSL 结构（agent.xdef）

```xml
<agent>
  <filter-chain>
    <!-- D1：agent 内自包含 id->impl 映射 -->
    <filter-definitions>
      <filter-def id="auth" impl="com.example.AuthFilter"/>
      <filter-def id="rate-limit" impl="com.example.RateLimitFilter"/>
      <filter-def id="content-check" impl="com.example.ContentCheckFilter"/>
    </filter-definitions>
    <!-- input 链：请求侧 guardrail（默认 PRE_CALL） -->
    <input-filters>
      <filter ref="auth"/>
      <filter ref="rate-limit"/>
    </input-filters>
    <!-- output 链：响应侧 guardrail（默认 POST_CALL） -->
    <output-filters>
      <filter ref="content-check"/>
    </output-filters>
  </filter-chain>
</agent>
```

- `<filter-chain>` 含三段：`<filter-definitions>`（id→impl 映射，D1）、`<input-filters>`、`<output-filters>`（均为有序 `<filter ref points>` 引用列表）
- codegen 生成 `AgentFilterChainModel` / `FilterDefModel` / `FilterRefModel`；`AgentModel.getFilterChain()`
- `<output-filters>` 经 `xdef:ref="FilterRefModel"` 复用 input 已定义的 `FilterRefModel`（规避 `xdef:name` 全局唯一约束）

#### D1：filter ID 解析来源（裁定：方案 B — agent 内 `<filter-definitions>` 自包含）

- **采纳方案 B**：filter ID 在 agent 模型内局部解析（`<filter-def id="auth" impl="..."/>`），复用既有 `resolveMiddlewares` 的 `ClassHelper.safeNewInstance` 路径实例化。
- **拒绝方案 A**（filter ID = IoC bean ID）：`AgentExecutorResolver` 经 Builder/构造注入，**不持有 `IBeanContainer`**；仓库 `*.beans.xml` 无任何已注册 `IAgentMiddleware` bean。方案 A 需先打通容器注入路径（架构变更面大）。
- **方案 C**（混合：`<filter-definitions>` 优先、未命中查 IoC）留后续迭代——当跨 agent filter 共享成为需求时演进。首版跨 agent 复用为 Non-Goal。
- **无静默跳过**：未知 ID / 缺 impl / impl 非 `IAgentMiddleware` / 实例化失败 / 未知 `points` 名 均抛 `NopAiAgentException`（含 ID 名/point 名，5 个 error code）。
- **per-id 实例缓存**：同一 filter 被 input+output 双链引用时实例化一次、identity 共享，使 D3 重复检测无歧义。

#### D2：input/output → 生命周期点映射（裁定：默认请求边界点 + `points` 覆盖）

**多次触发语义分析**（核心问题）：nop 生命周期点是**多次触发**模型，而 plano 的 input/output 是请求/响应级**单次触发**：

| 生命周期点 | 触发频率 | 用作 input 默认？ |
|-----------|---------|------------------|
| `PRE_CALL` | 每请求 **1 次**（请求开始） | ✅ input 默认 |
| `PRE_REASONING` | 每请求 **N 次**（每次 LLM 调用） | ❌ 多触发 |
| `PRE_ACTING` | 每请求 **M 次**（每次工具调用） | ❌ 多触发 |
| `POST_CALL` | 每请求 **1 次**（请求结束） | ✅ output 默认 |

"全装到所有 input 点"会让 auth-filter 一次请求触发 1+N+M+K 次（语义错误）。

**裁定**：
- **input-filters 默认 → `PRE_CALL`**（请求边界，单次触发），与 plano 请求侧单次触发语义一致
- **output-filters 默认 → `POST_CALL`**（响应边界，单次触发）
- **`points` 属性覆盖默认**：`<filter ref="prompt-check" points="pre_reasoning"/>` 精确装到指定点（csv-set 可多值），未指定走默认
- 避免多次触发问题，同时保留灵活性

#### D3：`<filter-chain>` 与 `<middlewares>` 共存合并（裁定：声明式在前 + 跨机制重复快速失败）

- **合并规则**：同一生命周期点的中间件列表 = 声明式 filter（按 `<filter-chain>` 内声明顺序）+ 代码类中间件（按 `<middlewares>` 内声明顺序）。**声明式 filter 在前**（guardrail 性质的 filter 应先执行 = 洋葱链最外层，其 `before` 先运行）。实现：`AgentExecutorResolver` 先调 `resolveFilterChain` 注册声明式 filter、再调 `resolveMiddlewares` 注册代码类中间件，registry 的 append 顺序天然保证声明式在前。
- **冲突检测**：同一 `AgentLifecyclePoint` 上，同一 impl class 同时出现在声明式 filter-chain 与代码类 `<middlewares>` 两处时，抛 `ERR_AGENT_FILTER_DUPLICATE_DECLARATION`（含 impl + point，非静默去重/保留两份）。检测按 **impl class**（两路径各自 `safeNewInstance` 产独立实例，identity 不适用）。intra-mechanism 重复（同机制内两处用同 impl）不触发——仅跨机制冲突算重复。
- 两种装配方式**共存**：声明式不替代代码类，agent 可同时用两者。

#### ResolvedFilterChain 模式

`FilterChainResolver.resolve(chain)` 在装配时一次性解析，产出不可变 `ResolvedFilterChain`：
- **声明侧**：`inputFilterRefs` / `outputFilterRefs`（`List<FilterRefModel>`，可序列化/可审计）
- **执行侧**：`resolvedByPoint: Map<AgentLifecyclePoint, List<IAgentMiddleware>>`（按声明顺序）
- 两者同步：每个 ref 已解析为恰好一个 `IAgentMiddleware` 实例（未知 ID 快速失败）
- 不可变视图：声明后不可运行时修改（与 plan 296 "装配时一次性注册、运行时不重排" 决策一致）

#### 执行模型

**不变**：声明式是**装配方式**，执行仍走 `MiddlewareChain` 洋葱链（plan 296 模型，零新执行路径）。声明式 filter 经 `AgentHookInvoker.executeWithMiddleware(point, ...)` → `DefaultHookRegistry.buildChain` → `MiddlewareChain.proceed()` 执行，与代码类中间件、hook 观察者同链。`<filter-chain>` 仅影响**会话级**点（`AgentLifecyclePoint`）；执行级 scope（W3-1）仍是 `<middlewares scope="execution">` 的专属领域。

### 5.3 与现有设计的边界

- 现有 middleware-design §一~四：洋葱链执行模型 + 9 点链式拦截（已落地，不变）
- §5.1：执行级分层（双层中间件）——不改洋葱链执行模型
- §5.2：声明式 filter chain（装配方式升级）——不改洋葱链执行模型
- §5.1 与 §5.2 正交：§5.2 的声明式 filter chain 仅作用会话级点；执行级 scope 是 §5.1 的专属领域
