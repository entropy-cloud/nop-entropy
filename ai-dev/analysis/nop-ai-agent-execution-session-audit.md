# 渐进式设计审计就绪度分析：执行模型与 Session 设计

**日期**: 2026-06-11
**分析范围**: `ai-dev/design/nop-ai-agent/` 下的执行模型和 Session 设计文档
**审计目标**: 评估执行模型的渐进式纯度（Progressive Design）、审计就绪度（Audit Readiness）、识别 Gaps

---

## 1. 执行模型渐进式设计总览

### Vision 约束 #4 基线

> 内部运行时实现最简化——只做"不引入任何外部假定的最简行为"。更多假定通过外部 XDSL 模型逐步引入。扩展通过添加接口实现，不通过阶段切换。

### 总体评分

| 维度 | 评分 | 理由 |
|------|------|------|
| IAgentExecutor 策略模式 | ✅ **通过** | `IAgentExecutor` 定义为接口，`ReActAgentExecutor` 是 Layer 1 实现。见 `01-architecture-baseline.md:62`、"`IAgentExecutor` 定义执行模式的策略接口" |
| Hook 分层 | ✅ **通过** | Layer 1 (5 core) + Layer 2 (5 extension) 明确分离。见 `02-execution-model.md:83-103` |
| 无 Hook 可运行 | ✅ **通过** | ReAct loop 设计不依赖 Hook（Hook 是"增强"，引擎负责主流程）。见 `nop-ai-agent-react-engine.md:177` |
| Steering 渐进式 | ❌ **未通过** | Steering 检查被硬编码到 ReAct 内层循环（每轮工具执行后），不是渐进添加的扩展。见 `02-execution-model.md:28` 和 `nop-ai-agent-react-engine.md:139-140` |
| followUp/ReAct 独立 | ✅ **通过** | 外层 followUp 循环和内层 ReAct 循环设计为独立嵌套，无状态缠绕。见 `nop-ai-agent-react-engine.md:116-127` |
| Layer 2 扩展不修改 Layer 1 | ❌ **未通过** | ReAct 引擎当前只暴露 Layer 1 的 5 个 Hook 点（`nop-ai-agent-react-engine.md:169-177`）。Layer 2 的 `PRE_CALL`/`POST_CALL`/`PRE_COMPACT`/`POST_COMPACT`/`REASONING_CHUNK` 需要修改引擎代码才能触发 |

### 关键发现

**1a. Steering 是核心循环的固有部分，不是渐进扩展**

`02-execution-model.md:28-29` 定义内层循环包含 "每轮工具执行后检查 steering 队列"。`nop-ai-agent-react-engine.md:139-140` 将这个检查嵌入循环体：

```
-> check steering queue:
   -> if has steering: 注入 steering, 跳出当前轮
```

这违反了 Vision 约束 #4 的"内部运行时实现最简化"。Steering 是外部注入机制，理论上应通过 Hook/Interceptor 渐进引入，而不是硬编码在 ReAct 循环中。

该设计的理由（`02-execution-model.md:65`）说"通过 Hook 注入 steering 消息：Hook 的语义是'增强当前事件'，不是'注入新消息流'。职责不同。"——但这恰恰说明 engine loop 将"注入外部消息"作为了一等职责，而非最小行为。

**1b. Layer 2 Hook 点需要修改 Layer 1 ReAct 引擎**

`nop-ai-agent-react-engine.md:169-177`：
> ReAct 引擎暴露 Layer 1 核心 5 个生命周期点...

Layer 2 的 5 个扩展点定义在 `02-execution-model.md:95-103`：
- `PRE_CALL`/`POST_CALL`：包裹整个 Agent 调用，不在 ReAct 循环内
- `REASONING_CHUNK`：LLM 流式块事件，当前引擎明确不做流式决策（`nop-ai-agent-react-engine.md:149`）
- `PRE_COMPACT`/`POST_COMPACT`：当前引擎未实现压缩触发

这意味着要启用任何 Layer 2 Hook，必须修改 `ReActAgentExecutor` 代码或创建新的 Engine wrapper。扩展不能通过纯配置（DSL）完成。

---

## 2. 核心架构分析：AgentModel/Agent/AgentSession 分离

### 2.1 渐进式设计检查

| 标准 | 状态 | 证据 |
|------|------|------|
| 配置、执行、状态三者分离 | ✅ | `01-architecture-baseline.md:86-99` — 三个对象各司其职 |
| AgentModel 可被 Delta 定制 | ✅ | `01-architecture-baseline.md:57` — "可被 Delta 定制" |
| Agent 无状态 | ✅ | `01-architecture-baseline.md:59` — "无状态执行体" |
| Session 独立持久化 | ✅ | `01-architecture-baseline.md:60` — "持久化跨请求存在；可以被任意服务实例接管" |
| 新增 Executor 实现无需修改 Engine | ✅ | `IAgentExecutor` 是策略接口，新实现只需实现 `execute()` |

### 2.2 审计就绪度检查

| 标准 | 状态 | 证据 |
|------|------|------|
| Executor 选择可追溯 | ⚠️ **部分** | 当前只有 `ReActAgentExecutor`，但选择逻辑未在设计中明确定义 |
| 配置变更历史可审计 | ❌ **缺失** | `AgentModel` 加载后无版本/变更审计 |
| Session 创建/恢复可追溯 | ✅ | `AgentEngine` 的 `lookupOrCreateActor` 在 `nop-ai-agent-react-engine.md:67` 定义 |
| Actor 生命周期事件发布 | ✅ | `nop-ai-agent-react-engine.md:74-75` — `AgentResult`/`AgentError` 事件 |

### 2.3 Gaps

- **配置版本审计缺失**：`AgentModel` 从 `agent.xdef` 加载后，无配置版本信息传递到执行上下文。审计时无法判断"当前 Agent 行为由哪个版本的配置定义"。
- **Executor 选择策略未定义**：当有多个 `IAgentExecutor` 实现时，如何选择（DSL 配置 vs 运行时判定）未在设计中说明。

---

## 3. 双循环分析：followUp + ReAct

### 3.1 Steering 是渐进式还是内置？

**结论：非渐进式。**

- `02-execution-model.md:28`："每轮工具执行后检查 steering 队列"
- `nop-ai-agent-react-engine.md:139-140`：steering 检查是 ReAct 循环体内的固定步骤
- `02-execution-model.md:73`："Steering 是引擎层机制，当前不需要 DSL 支持"

**问题**：最小引擎不应该假设有外部消息注入能力。如果按 Vision #4 的"只做最简行为"，引擎应在工具执行完毕后直接进入下一轮推理，不检查外部队列。Steering 应通过 `PRE_ACTING` Hook 拦截并在 Hook 内进行队列检查。

**影响**：
1. 没有 steering 的场景下（完全自主 Agent），引擎仍然支付检查成本
2. 引擎内核假设了 Actor 外部消息的存在，不是"纯 ReAct"
3. 违反了"扩展通过添加接口实现，不通过阶段切换"——steering 是阶段切换式的：无 steering → 有 steering 需要修改引擎

### 3.2 审计轨迹：循环决策

| 决策点 | 是否可审计 | 证据/问题 |
|--------|-----------|----------|
| 进入 followUp | ⚠️ 部分 | 队列为空则结束，但决策本身未要求记录 |
| 注入 steering 消息 | ❌ 缺失 | Steering 注入不会产生独立审计事件 |
| 跳出内层循环（无工具调用） | ✅ | 这是 LLM 输出解析的自然结果 |
| 达到 maxIterations | ✅ | 约束检查，结果明确 |
| 错误类型决定中止/重试 | ❌ 缺失 | `on_error` Hook 可修改策略，但策略选择本身不审计 |

**关键问题**：Steering 是外部消息注入引擎的关键路径点，但设计中没有任何事件记录 steering 何时注入、注入内容是什么、跳过了哪些工具。

---

## 4. Hook 系统分析

### 4.1 渐进式设计检查

| 标准 | 状态 | 证据 |
|------|------|------|
| Layer 1 核心点可在无 Layer 2 点下正常工作 | ✅ | ReAct 引擎只触发 5 个 Layer 1 点 |
| Layer 2 扩展点可不修改引擎代码添加 | ❌ | 见 1b——需修改 `ReActAgentExecutor` |
| Hook 可通过 AgentModel 配置控制 | ✅ | `02-execution-model.md:117` — `<hooks>` 在 `agent.xdef` 中声明 |
| Hook 优先级可排序 | ✅ | `02-execution-model.md:112` — "数值越小优先级越高" |
| Hook 可在运行时动态装配 | ⚠️ 未明确定义 | Skill 组装在 "`created → ready` 时执行一次"（`nop-ai-agent-react-engine.md:197`），未说明运行时热插拔 |

### 4.2 审计日志覆盖

**关键 gap**: `nop-ai-agent-session-and-storage.md:96-104` 定义了 4 种 Event Log Entry 类型：

| Entry 类型 | 是否覆盖 Hook 执行 |
|-----------|-------------------|
| `session_header` | ❌ Session 元数据 |
| `message` | ❌ LLM 消息 |
| `compaction` | ❌ 压缩边界 |
| `state_change` | ❌ Actor 状态转换 |

**没有任何一种 Entry 类型记录 Hook 执行**。这意味着：

- 审计员无法确认某个 `PRE_REASONING` Hook 是否已执行
- 无法判断 Hook 执行顺序是否正确
- 无法判断 Hook 是否失败、失败后采取了什么策略
- `NopAiEvent` 表（`session-and-storage.md:388`）是通用审计日志，但未定义 Hook 执行事件 schema

### 4.3 Hook 失败传播

`02-execution-model.md:113`：
> Hook 执行失败时，引擎根据错误类型决定继续还是中止

**模糊性**：
- "错误类型"未明确定义
- 无默认策略（默认继续？默认中止？）
- 无 Hook 失败的降级语义（某个 Hook 失败是否导致整个 Agent 失败？）
- 一个 Hook 失败是否阻止同优先级 Hook 的执行？

**推荐**：需要明确 `HookExecutionPolicy` 的契约：
- `FAIL_FAST`：中止整个执行
- `FAIL_CONTINUE`：记录错误，继续执行
- `FAIL_DEGRADE`：降级到默认行为
- 同 Layer 内联动 vs 隔离

---

## 5. Session 与存储分析

### 5.1 Session Tree 可审计性

| 标准 | 状态 | 证据 |
|------|------|------|
| 父子关系可追溯 | ⚠️ 部分 | 子 session 记录父 session（`session-and-storage.md:259`），父不维护子列表（`session-and-storage.md:260`） |
| Fork 决策可追溯 | ❌ 缺失 | Fork 操作本身不是 Event Log Entry 类型 |
| Fork 时生成快照 | ✅ | `session-and-storage.md:250-253` — Fork 流程包含快照生成 |
| 跨 session Plan 共享 | ✅ | `session-and-storage.md:135-167` — Plan 跨 session 存在，通过 planId 引用 |

**问题**：Fork 是重要的审计事件（"谁在什么上下文中创建了子 Agent？"），但 `nop-ai-agent-session-and-storage.md:96-104` 的事件类型中不包括 `session_fork`。Fork 产生的 `snapshotId` 和 `parentSessionId` 在存储层面存在（Session header 或快照中），但作为"操作记录"没有被明确写为事件。

### 5.2 快照与恢复审计轨迹

| 标准 | 状态 | 证据 |
|------|------|------|
| Event Log 是 source of truth | ✅ | `session-and-storage.md:83` — append-only event log 是权威 |
| Snapshot 是派生缓存 | ✅ | `session-and-storage.md:60` — 从 event log 派生的快照缓存 |
| 重建算法已定义 | ✅ | `session-and-storage.md:125-131` — 5.3 重建算法具体 |
| 压缩写入有审计 | ✅ | `session-and-storage.md:276` — CompactionEntry 包含 summary、tokensBefore/After 等 |

### 5.3 Event Sourcing vs Snapshot

**评估：良好，但有 gap**。

设计明确区分了 Event Sourcing（event log, source of truth）和 Snapshot（派生缓存）。`session-and-storage.md:83-94` 清晰说明了理由。

**Gap**：snapshot 生成的时间点和触发条件未定义：
- 是每次 message 写入后自动更新？
- 是在 session 加载时按需生成？
- 是在 compaction 后更新？

`session-and-storage.md:317-321` 建议了关键写回顺序，但 snapshot 的"何时生成"是 load-time 策略还是 write-time 策略未定。

### 5.4 并发控制审计

`session-and-storage.md:289-321` 定义了 Session 级锁和原子性规则。但：

- **无死锁检测**：规则是"避免跨 Session 嵌套锁"（`session-and-storage.md:307`），但无检测机制
- **锁内长时间操作**：规则是"避免在锁内做长时间 LLM 调用或工具调用"（`session-and-storage.md:308`），但可用代码审查约束

---

## 6. 关键 Gaps 汇总

| # | Gap | 严重度 | 涉及模块 | 修复方式 |
|---|-----|--------|---------|---------|
| G1 | **Steering 不是渐进式** — 硬编码在 ReAct 主循环中 | 高 | Execution Model | 将 steering 队列检查移入一个 Layer 2 `PRE_REASONING` Hook，或定义一个 `ISteeringProvider` 接口并默认空实现 |
| G2 | **Layer 2 Hook 需修改 Layer 1 引擎** — `PRE_CALL`/`POST_CALL` 等 5 个点不在 ReAct 循环中 | 高 | Hook System | 将 ReAct 引擎改为按生命周期驱动（event-driven loop），Layer 2 点通过事件注册添加 |
| G3 | **Hook 执行无审计事件** — Event Log 无 Hook 相关 Entry 类型 | 高 | Session/Storage | 增加 `hook_execution` 事件类型（含 hookName, lifecyclePoint, duration, result, error） |
| G4 | **Steering 注入无审计事件** | 中 | Execution Model | Steering 注入时写入 `steering_injected` 事件到 Event Log |
| G5 | **Fork 操作不是事件** — 无 `session_fork` Entry 类型 | 中 | Session/Storage | 增加 `session_fork` 事件类型（含 parentSessionId, childSessionId, snapshotId, reason） |
| G6 | **Hook 失败传播策略模糊** — "根据错误类型决定"无具体契约 | 中 | Hook System | 定义 `HookExecutionPolicy` 明确的 3 种语义（FAIL_FAST/FAIL_CONTINUE/FAIL_DEGRADE） |
| G7 | **无 Hook 是否被跳过的验证机制** | 中 | Audit | 为每个 lifecycle point 定义 mandatory hooks list，引擎校验全部已执行 |
| G8 | **Executor 选择策略未定义** — 多 IAgentExecutor 实现时无选择规则 | 低 | Architecture | 定义 AgentModel 的 `executor` 字段或运行时匹配策略 |
| G9 | **Snapshot 更新时机未定义** | 低 | Session/Storage | 明确 snapshot 是 write-time 还是 load-time 更新 |
| G10 | **无死锁检测** — Session 级锁无超时/检测机制 | 低 | Session/Storage | 锁获取加超时，记录锁等待图 |

---

## 7. 按渐进式原则评估 Vision #4 符合度

| Vision #4 子句 | 评估 |
|---------------|------|
| "内部运行时实现最简化" | ❌ Steering 破坏了最小化。引擎假设了外部消息注入能力 |
| "不引入任何外部假定的最简行为" | ⚠️ 基本满足，但 steering 增加了一个"外部消息可能存在"的假定 |
| "更多假定通过外部 XDSL 模型逐步引入" | ✅ Hook 和 Skill 是好的例子，executor 策略接口也是 |
| "扩展通过添加接口实现" | ✅ IAgentExecutor 通过添加实现扩展。Hook 通过注册扩展 |
| "不通过阶段切换" | ❌ Layer 2 Hook 点和 Steering 实质上是阶段切换——需要改引擎代码才能启用 |

---

## 8. 按审计就绪度评估

| 审计需求 | 就绪度 | 关键依赖 |
|---------|--------|---------|
| "每一步都可追溯" | ⚠️ 70% | 缺少 Hook 事件、Steering 事件、Fork 事件 |
| "Hook 执行有上下文日志" | ❌ 0% | 无 Hook 事件 Entry 类型 |
| "可从 Event Log 完全重建" | ✅ 90% | Event Sourcing 设计完成 |
| "Event Sourcing vs Snapshot 清晰" | ✅ 100% | 明确区分，重建算法已定义 |
| "Fork/Join 记录父子关系" | ⚠️ 50% | 子→父单向存在，无 Fork 事件 |
| "可验证无 Hook 被跳过" | ❌ 0% | 无验证机制 |

---

## 9. 推荐修复优先级

### P0（必须修复才能达到渐进式设计标准）

1. **解耦 Steering 出核心循环** — 将 steering 队列检查重构为 `IAgentExecutor` 的 wrapper 或 Layer 1 `PRE_REASONING` Hook。最小引擎只做纯 ReAct。
2. **使 Layer 2 Hook 点可通过配置注册** — 不改 `ReActAgentExecutor` 代码即可启用 `PRE_CALL`/`POST_CALL`/`PRE_COMPACT`/`POST_COMPACT`。建议将引擎改为"生命周期事件循环"，ReAct 只是默认的事件生产者。

### P1（必须修复才能实现审计就绪）

3. **添加 Hook 执行事件** — Event Log 增加 `hook_execution` 类型，包含 `hookName`, `lifecyclePoint`, `executionOrder`, `durationMs`, `result`（`success|skipped|failed`）, `error`。
4. **添加 Steering 注入事件** — steering 注入时写入事件。
5. **添加 Session Fork 事件** — fork 时写入 `session_fork` 事件。

### P2（推荐修复）

6. **定义 `HookExecutionPolicy` 契约** — 明确 FAIL_FAST / FAIL_CONTINUE / FAIL_DEGRADE。
7. **定义 Mandatory Hook 校验** — 引擎可在生命周期结束时校验所有标记为 mandatory 的 Hook 已执行。
8. **定义 Snapshot 更新时机** — 建议 load-time on-demand 更新，避免 write-time 性能损失。

---

## 10. 与其他文档的关系

- `ai-dev/design/nop-ai-agent/00-vision.md:29` — 本次审计的渐进式增强约束
- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md:62` — IAgentExecutor 策略接口
- `ai-dev/design/nop-ai-agent/02-execution-model.md:28,65,83,113` — Steering 硬编码、Hook 分层、失败传播
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md:139,169-177` — Steering 在循环中、Layer 1 仅暴露 5 点
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md:96-104,259-260` — Event Log 无 Hook/Fork 事件
- `ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md` — Hook 引擎对象定义但无审计需求
