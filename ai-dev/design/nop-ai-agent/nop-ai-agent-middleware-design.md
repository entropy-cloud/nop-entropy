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

**不删除** `IAgentLifecycleHook`，**不修改** `HookResult` 密封层级，**不修改** `AgentLifecyclePoint` 枚举值。

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
