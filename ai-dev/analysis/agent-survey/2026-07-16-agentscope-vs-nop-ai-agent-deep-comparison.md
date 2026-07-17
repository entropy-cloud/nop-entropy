# AgentScope Java vs nop-ai-agent 深度对比分析

> Status: updated
> Date: 2026-07-17 (design-doc-informed revision)
> Scope: ~/ai/agentscope-java — 阿里巴巴 Java AI Agent 框架 vs `nop-ai-agent` + `nop-ai-toolkit`
> Reference: 本对比基于 Nop AI Agent 设计文档体系（`ai-dev/design/nop-ai-agent/README.md` 必读路径）重读后进行，修正了初版"从 AgentScope 出发搜 Nop 等价物"的方法偏差
> Conclusion: AgentScope 是全响应式 Agent 运行时，nop-ai-agent 是声明式 Agent 引擎；两者架构理念互补，可互相借鉴

## Context

- AgentScope Java 是阿里巴巴通义实验室推出的 Java 版 Agent 框架，2025 年 12 月发布 1.0
- nop-ai-agent 是 Nop 平台的 AI Agent 引擎，基于可逆计算原理的声明式 Agent 框架。设计文档体系位于 `ai-dev/design/nop-ai-agent/`，按 00-vision → 01-architecture-baseline → 02-execution-model → 04-tool-invocation 的必读路径组织
- 两者都实现了 ReAct 循环，但架构理念和技术选型差异显著
- 对 Nop 意义：对比设计决策，提取可借鉴的模式。本对比以 Nop 设计文档为理解 Nop 的权威来源，以 AgentScope 源码为理解 AgentScope 的权威来源
- **方法论反思**：初版从"AgentScope 有什么"出发去搜 Nop 的等价物，漏掉了 `nop-ai-toolkit`（Tool Layer）中的 `IToolFileSystem`。修正后的方法：先读 Nop 设计文档（模块边界 `01-architecture-baseline.md` §三、Tool 层归属、IToolFileSystem 设计决策 `nop-ai-shell-design.md` §5.4），再与 AgentScope 做对等对比

## 核心架构对比

### 1. 架构理念

| 维度 | AgentScope Java | nop-ai-agent | Nop 设计文档 |
|------|----------------|--------------|-------------|
| **核心理念** | 全响应式 Agent 运行时 | 声明式 Agent 引擎 + 可逆计算 | `00-vision.md` §一：面向大规模无人值守自动化 |
| **编程模型** | Project Reactor (Mono/Flux) | 同步/命令式 + Virtual Threads | `01-architecture-baseline.md` §十：选择 `CompletionStage` + `IMessageService`，拒绝 Reactor 作为引擎内部模型 |
| **配置方式** | Builder 模式 + 代码配置 | XDSL 声明式配置 + 代码生成 | `00-vision.md` §三约束1（DSL-First）：先定义 xdef schema，再定义 runtime 解释 |
| **扩展机制** | Middleware 拦截链 | Hook + Contribution Registry | `02-execution-model.md` §五：10 个生命周期点，双循环模型 |
| **状态管理** | AgentState 不可变对象 + 可变句柄 | AgentModel + Agent + AgentSession 三分离 | `00-vision.md` §三约束3（配置、执行、状态三分离）+ §七核心隐喻 |
| **模块边界** | AgentScope 单体（core + harness） | Agent Engine（`nop-ai-agent`）+ Tool Layer（`nop-ai-toolkit`）独立分层 | `01-architecture-baseline.md` §三：Tool Layer 与 Agent Engine Layer 严格分离，Agent Engine 不直接依赖 `nop-ai-tools` |

### 2. Agent 体系结构

#### AgentScope Java

```
Agent (顶层接口, 三合一 Facade)
├── CallableAgent     — 同步/异步调用
├── StreamableAgent   — 流式执行
└── ObservableAgent   — 被动观察

AgentBase (abstract) → ReActAgent (core) / HarnessAgent (harness)
```

**关键设计**：
- Agent 接口是三合一 Facade，组合三种能力
- AgentBase 提供 Hook 生命周期、MsgHub 订阅、协作式中断、Per-session 调用序列化
- ReActAgent 是核心实现，内部类 CallExecution 封装每次调用状态

#### nop-ai-agent

```
IAgentEngine (顶层引擎接口)
├── IAgentExecutor (执行策略)
│     ├── ReActAgentExecutor (核心)
│     └── SingleTurnExecutor (简化)
├── ISessionStore (会话持久化)
└── IAgentEventPublisher (事件发布)

AgentModel (声明式定义) → AgentExecutionContext (运行时状态)
```

**关键设计**：
- AgentModel 是通过 XDSL 生成的不可变组件模型，包含 prompt/tools/permissions/hooks 等
- AgentExecutionContext 是一次执行的可变运行状态容器
- 采用策略模式，IAgentEngine 将执行委托给 IAgentExecutor

### 3. ReAct 循环实现

#### AgentScope Java

```
reasoning(iter) → model.stream() → 累积 chunks → acting(iter)
  → toolkit.callTools() (parallel/sequential) → executeIteration(iter+1)
  → summarizing() (maxIters)
```

**特点**：
- 全响应式：model 始终 stream，chunks 累积
- 中断检查点：iteration start, reasoning, tool exec, streaming chunks
- Pending tool recovery：无结果的工具调用可恢复
- Graceful shutdown：通过 GracefulShutdownManager 保存状态
- **文件系统抽象**：`AbstractFilesystem` 是 Agent Layer 的一等抽象（`WorkspaceManager.getFilesystem()` 随处可访问），5 种实现（Local/Remote/Sandbox/Overlay/Composite），含 `RuntimeContext` 会话感知、Result Object 错误模式、`edit` 精确替换

#### nop-ai-agent

```
sustainLoop (外层 — 续命循环)
  └── reactLoop (内层 — ReAct 迭代循环)
        ├── 1-17 步执行流程
        └── dispatchLoop (调度循环)
              ├── ToolCallRepairer 修复
              ├── 4 层安全检查
              ├── 写冲突检测
              └── 并行工具执行
```

**特点**：
- 双层循环：外层续命，内层 ReAct
- 17 步执行流程：从取消检查到工具调度
- 工具调用修复链：4 阶段确定性修复
- 四层纵深安全：工具级 → 安全策略 → 审批门禁 → 沙箱隔离
- **文件系统抽象**：`IToolFileSystem`（`nop-ai-toolkit` 模块，Tool Layer）是 Agent 工具的唯一文件操作接口。设计决策（`nop-ai-shell-design.md` §5.4）明确拒绝复用 VFS 的 `IResourceStore`，理由是路径校验不一致、`LocalToolFileSystem` 已有完善的 `isPathAllowed()` 和路径规范化、Agent Engine 通过 `IToolFileSystem` 管理文件访问控制。`IToolFileSystem` 是**工具层抽象**（`IToolExecuteContext.getFileSystem()`），引擎本身不直接通过它操作文件。深层对比见 `agent-survey/agentscope-fs-vs-nop-fs-with-delta.md`

### 4. Tool 系统设计

#### AgentScope Java

**注册路径**：
1. 注解式 (`@Tool`)：扫描 POJO 方法，victools 生成 JSON Schema
2. 编程式 (`AgentTool` 接口)：直接实现

**工具管理**：
- Toolkit 是工具注册和调度的门面，内部委托给 5 个管理器
- ToolGroup 实现运行时工具可见性控制
- ToolExecutor 处理批量执行、重试、超时

**特殊工具**：
- SubAgentTool：agent 作为工具
- SchemaOnlyTool：纯 schema 外部工具
- McpTool：MCP 协议工具

#### nop-ai-agent

**工具注册**：
- 通过 IToolManager 统一管理
- AgentModel 声明工具白名单
- 内置工具执行器：call-agent, send-message, read/write/search-memory, team-*

**工具调用修复**：
- ChainRepairer 实现 4 阶段确定性修复管线
- ToolNameNormalization → ArgumentStructureRepair → ArgumentValueCoercion → ArgumentCleanup

**执行上下文**：
- AgentToolExecuteContext 将 agent 引擎的内部能力暴露给工具执行器
- 支持权限继承、路径规则、委托深度控制

### 5. Memory/State 管理

#### AgentScope Java

**State 体系**：
```
AgentState (final, immutable builder pattern)
├── sessionId, userId
├── context: List<Msg> — 对话缓冲区
├── summary: String — 滚动摘要
├── permissionContext, toolContext, tasksContext...
└── interruptControl (transient, runtime-only)
```

**持久化**：
- AgentStateStore 接口：InMemory, JsonFile, Redis/MySQL (extensions)
- 防御性拷贝 + 可变句柄双模式

#### nop-ai-agent

**会话持久化**：
```
ISessionStore (接口)
├── InMemorySessionStore
├── FileBackedSessionStore
└── DBSessionStore
```

**工作记忆系统**：
- IMemoryStoreProvider → 按 sessionId 隔离
- IAiMemoryStore：InMemory 或 AdapterBacked（可插拔适配器）
- 支持 read/write/search 三种操作

**上下文压缩**：
- PipelineCompactor：L1 微压缩 → L2 回合裁剪 → L3 LLM 全量摘要

### 6. 拦截机制

#### AgentScope Java

**v2.0 Middleware 系统**（推荐）：
- MiddlewareBase 定义 5 个拦截点：onAgent, onReasoning, onActing, onModelCall, onSystemPrompt
- MiddlewareChain 实现洋葱模型
- 15+ 个内置中间件（GracefulShutdown, Workspace, Skill, Subagents, Compaction 等）

**v1 Hook 系统**（已废弃）：
- 基于事件的拦截：PreCall, PostCall, PreReasoning 等
- 按优先级排序执行

#### nop-ai-agent

**Hook 系统**：
- IAgentLifecycleHook 接口：12 个生命周期点
- HookResult：PassResult, VetoResult, ReenterResult
- 支持否决和重新进入

**Contribution Registry**：
- 7 种贡献类型：PROMPT, HOOK, TOOL, GUARDRAIL, LISTENER, DECORATOR, EXTENSION
- 在执行装配时一次性解析

**四层纵深安全**：
- Layer 1: 工具级访问控制
- Layer 2: 安全策略层
- Layer 3: 审批门禁层
- Layer 4: 沙箱隔离层

### 7. 多 Agent 编排

#### AgentScope Java

**SubAgent 工具模式**：
- SubAgentTool 将 Agent 包装为 Tool
- SubAgentProvider 工厂接口，每次调用创建新 Agent 实例

**HarnessAgent**：
- 在 ReActAgent 之上添加完整编排能力
- 支持 Workspace, Sandbox, Skill, Memory, PlanMode

**消息总线**：
- MsgHub pub/sub 多 agent 对话
- Gateway 体系支持跨节点通信

#### nop-ai-agent

**call-agent 工具**：
- 支持三种子会话模式：Create-new, Fork, Continue
- 防递归保护：delegationDepth 超过阈值时拒绝

**Team 系统**：
- ITeamManager 团队生命周期管理
- ITeamTaskStore 团队任务存储
- ITeamAclChecker 团队 ACL

**跨进程协调**：
- Session Takeover Lock：跨 JVM 进程的会话接管锁
- Write Intent Registry：跨会话写冲突检测
- Recovery Manager：持续性陈旧锁清理

## 可借鉴的设计模式

### 1. AgentScope → nop-ai-agent

#### 全响应式架构
- **优势**：高吞吐 + 自然流式
- **借鉴点**：nop-ai-agent 可考虑在 LLM 调用层引入响应式，但保持上层同步
- **实现路径**：LLM 调用使用 Mono/Flux，其他部分保持同步

#### Middleware 拦截架构
- **优势**：5 层拦截点，覆盖 agent 全生命周期
- **借鉴点**：nop-ai-agent 的 Hook 系统可扩展为 Middleware 架构
- **实现路径**：将现有 Hook 扩展为 MiddlewareBase，支持洋葱模型
- **设计文档**：[Middleware 洋葱链设计](../../design/nop-ai-agent/nop-ai-agent-middleware-design.md)

#### ToolGroup 动态激活
- **优势**：运行时控制工具可见性，支持 Agent 自管理
- **借鉴点**：nop-ai-agent 的工具白名单可扩展为 ToolGroup 概念 → 但最终采用更轻量的 **K8s-style 标签方案**（拒绝 ToolGroup 的过度工程化）
- **设计文档**：[Tag-based Tool Visibility 设计](../../design/nop-ai-agent/nop-ai-agent-tool-tag-system.md)

#### Permission Engine
- **优势**：三态权限（ALLOW/ASK/DENY）+ per-session 缓存
- **借鉴点**：nop-ai-agent 的四层安全架构可简化权限引擎
- **实现路径**：在 PermissionMatrix 中引入三态权限

### 2. nop-ai-agent → AgentScope Java

#### 声明式配置
- **优势**：XDSL 定义 schema，生成不可变组件模型
- **借鉴点**：AgentScope 可考虑引入声明式 Agent 配置
- **实现路径**：定义 Agent XDSL schema，生成 Java 配置类

#### 四层纵深安全
- **优势**：完整的安全链，从工具级到沙箱隔离
- **借鉴点**：AgentScope 可增强安全架构
- **实现路径**：在 Middleware 中添加安全检查层

#### 工具调用修复链
- **优势**：4 阶段确定性修复，保证工具调用正确性
- **借鉴点**：AgentScope 可引入工具调用修复机制
- **实现路径**：在 Toolkit 中添加 ToolCallRepairer

#### Sustainer 续命模型
- **优势**：支持长时间运行的 agent 跨越迭代预算
- **借鉴点**：AgentScope 可考虑引入续命机制
- **实现路径**：在 ReActAgent 中添加 ISustainer 接口

#### 拒绝账本
- **优势**：累计拒绝次数达到阈值自动暂停
- **借鉴点**：AgentScope 可增强 HITL 机制
- **实现路径**：在 PermissionEngine 中添加 DenialLedger

## 8 个核心设计模式的深度代码对比

本次调研从代码层面对比了 8 个对 nop-ai-agent 具有直接参考价值的设计模式，包含 AgentScope Java 的具体文件路径、核心接口、设计模式归类，以及与 nop-ai-agent 的逐项对比。

### 1. MiddlewareBase（洋葱拦截架构）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 拦截点 | 5 个：onAgent / onReasoning / onActing / onModelCall / onSystemPrompt | 1 个：IToolCallInterceptor (beforeCall/afterCall) |
| 模式 | 洋葱模型 (Onion) + Pipeline 混合 | 简单 around-advice |
| 链构建 | 从后往前组装，首 middleware 为最外层 | 无链式组合 |
| 返回值 | Flux\<AgentEvent\>（响应式） | 同步阻塞 |
| 系统 Prompt | onSystemPrompt 为纯 Pipeline 变换 | 手动字符串拼接 |

**关键文件**：`agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java` (L26-L43，5 个接口定义), `MiddlewareChain.java` (L46-62，链构建算法).

**实现细节**：
- `MiddlewareMethod<I>` 函数式接口解耦链构造与钩子分发（L69-77）
- `onSystemPrompt` 无 `next` 参数，纯顺序变换（L140-142）
- 反射式中间件热插拔：15+ Harness 中间件（PlanMode, GracefulShutdown, Compaction, MemoryFlush, SandboxLifecycle 等）

**nop-ai-agent 建议**：**HIGH 优先级引入**。这是架构层面的根本升级，比现有的 Hook 系统更组合友好。nop-ai-agent 可以在现有 `IToolCallInterceptor` 基础上扩展出 agent 级、reasoning 级、model-call 级的拦截点，用洋葱模型组织。
**设计文档**：[Middleware 洋葱链设计](../../design/nop-ai-agent/nop-ai-agent-middleware-design.md) — 保留 Veto/Reenter 语义，IAgentMiddleware + MiddlewareChain 双轨共存。

### 2. PermissionEngine（三态权限引擎）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 规则级联 | 6 步严格优先级：DENY > ASK > 工具自检 > ALLOW > BYPASS > 默认 ASK | 四层纵深 + 路径规则 |
| 权限模式 | 5 种：DEFAULT, BYPASS, EXPLORE, ACCEPT_EDITS, DONT_ASK | SecurityLevel + hint |
| ASK 流程 | 内置 ASK 行为，用户确认返回路径 | 审批门（ApprovalGate）分离 |
| 快速路径 | `isTrivial()` — 无规则时跳过引擎构建 | 无 |
| 模式语义 | EXPLORE 只读，ACCEPT_EDITS 只读+写提示，DONT_ASK 降级 ASK 为 DENY | 路径 deny/allow 优先 |

**关键文件**：`agentscope-core/src/main/java/io/agentscope/core/permission/PermissionEngine.java` (L139-202，6 步评估管道), `PermissionMode.java` (L33-71，5 模式枚举).

**评估管线**（引擎核心，L139-202）：
```
1. DENY 规则（最高优先级，行 144-147）
2. ASK 规则（行 150-153）
3. 工具特异性检查（行 156-177，BYPASS 免疫）
   3a. EXPLORE/ACCEPT_EDITS 模式检查（行 212-218）
   3b. ToolBase.checkPermissions() 自检（行 219）
4. ALLOW 规则（行 183-186）
5. BYPASS 模式兜底（行 189-196）
6. 默认 ASK（DONT_ASK 下转为 DENY，行 199-202）
```

**nop-ai-agent 建议**：**MEDIUM 优先级采纳**。EXPLORE 和 DONT_ASK 模式 + `isTrivial()` 快速路径是明确的增量改进。nop-ai-agent 的路径级规则和审批门已经更企业级，但缺少模式切换的语义。

### 3. ToolGroupManager（工具动态激活）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 工具可见性控制 | 工具组 (ToolGroup) + 激活标志 | 全局注册，无分组概念 |
| 管理范围 | META (Agent 可管理) vs EXTERNAL (开发者管理) | 无 |
| 元工具 | `reset_equipped_tools` 动态开关组 | 无 |
| 默认激活 | 不在任何组的工具默认激活；在组中的工具需所在组激活 | 注册即用 |
| 反向索引 | `tools -> groups` 高效 `isActiveTool()` | 无 |

**关键文件**：`agentscope-core/src/main/java/io/agentscope/core/tool/ToolGroupManager.java` (L32-550).

**核心算法**（`isActiveTool` 行 322-336）：
```java
Set<String> toolGroups = this.tools.get(toolName);
if (toolGroups == null || toolGroups.isEmpty()) return true; // 默认激活
return activeGroups.stream().anyMatch(toolGroups::contains);
```

**META vs EXTERNAL 隔离**：`replaceMetaActiveGroups()` (L375-394) 只替换 META 域激活组，EXTERNAL 组不受影响。Agent 通过 `reset_equipped_tools` 元工具只能控制 META 组。

**nop-ai-agent 建议**：**HIGH 优先级引入**。ToolGroup 提供了动态缩小工具暴露面的机制，对多租户、Skill 绑定、上下文窗口管理至关重要。META/EXTERNAL 分离防止 Agent 篡改基础设施工具。
**设计文档**：[Tag-based Tool Visibility 设计](../../design/nop-ai-agent/nop-ai-agent-tool-tag-system.md) — nop 最终选择 K8s-style 标签平铺方案（拒绝 ToolGroup 的组 + 成员 + 工厂复杂度），工具打标签 + Agent 声明 activeTags 实现更灵活的多维筛选。

### 4. SubAgentTool（Agent 作为工具）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| Agent 创建 | SubAgentProvider 工厂，每次调用创建新实例 | 预注册 Agent，按名引用 |
| 会话管理 | session_id 驱动多轮对话，StateStore 持久化 | SessionStore 驱动 |
| 事件转发 | ToolEmitter 流式转发子 Agent 事件 | 无 |
| 取消处理 | `doFinally(SignalType.CANCEL)` 中断子 Agent | 超时取消 |
| 线程安全 | 工厂模式确保每次调用独立实例 | Actor 模型 |
| 名称国际化 | 非 ASCII 名称智能哈希（8 字符后缀） | 静态配置 |

**关键文件**：`agentscope-core/src/main/java/io/agentscope/core/tool/subagent/SubAgentTool.java` (L62-587), `SubAgentProvider.java` (L42-54).

**设计要点**：
- `SubAgentProvider<T>` 函数式接口（行 42-54）："Since ReActAgent is not thread-safe, this provider pattern ensures that each tool call gets a fresh agent instance."
- 事件转发（行 399-413）：子 Agent 事件携带 `source` 路径（如 `"main/researcher"`）注入父事件流

**nop-ai-agent 建议**：**MEDIUM 优先级**。工厂模式比 nop-ai-agent 当前的预注册模式更灵活，事件转发机制适合监控场景。取消处理的 `doFinally` 防止孤儿资源是良好的工程实践。

### 5. GracefulShutdownManager（优雅关闭）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 状态机 | 3 态：RUNNING -> SHUTTING_DOWN -> TERMINATED | 无状态机 |
| 请求追踪 | per-call requestId，绑定特定 (userId, sessionId) | 无 |
| 安全点检查 | doOnComplete 钩子：推理/行动阶段完成后才中断 | 检查点在步骤边界 |
| 超时强制执行 | 监控线程 + force-interrupt | Future 超时 |
| 状态保存 | `shutdownInterrupted=true` 标记保存 | 检查点管理器 |
| 偏推理策略 | PartialReasoningPolicy.SAVE | 隐式检查点 |

**关键文件**：`agentscope-core/src/main/java/io/agentscope/core/shutdown/GracefulShutdownManager.java` (L40-332).

**设计亮点**：
- `ActiveRequestContext` (L34-104) 追踪 per-call 粒度，非 per-agent
- `GracefulShutdownMiddleware.onReasoning()` (L64-76) 和 `onActing()` (L79-91) 在 `doOnComplete` 中检查关闭信号——确保当前阶段自然结束
- `ShutdownStateSaver` (L28-37) 策略接口解耦状态持久化

**nop-ai-agent 建议**：**MEDIUM 优先级**。per-request + per-session 绑定方式能解决一类并发关闭 bug。nop-ai-agent 的检查点/会话存储设施更数据库化，但缺少安全点模式。

### 6. AgentState（不可变构建器 + 双访问模式）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 状态模型 | AgentState 单体 + 5 个子上下文 | AgentSession + Checkpoint |
| 不可变性 | Builder 构建不可变字段 + 双访问模式 | 快照不可变 |
| 双访问模式 | `getContext()` 防御性拷贝 vs `contextMutable()` 实时引用 | 无对应 |
| 序列化 | Jackson 直接 JSON | DB 表 + JSON |
| 子上下文 | permission / tool / task / plan-mode / context | Channel + MemoryStore |
| 中断信号 | 嵌入 InterruptControl（transient） | 独立中断机制 |

**关键文件**：`agentscope-core/src/main/java/io/agentscope/core/state/AgentState.java` (L59-423).

**双访问模式**（行 181-188）：
```java
public List<Msg> getContext() {
    return Collections.unmodifiableList(new ArrayList<>(context)); // 安全只读
}
public List<Msg> contextMutable() {
    return context; // 实时引用，供 Middleware/Tool 追加
}
```

**nop-ai-agent 建议**：**MEDIUM 优先级**。`PlanModeContextState` 将「计划模式」状态嵌入持久化 AgentState 的模式值得引入——使计划模式跨进程重启保持。双访问模式是良好的最小化拷贝模式。

### 7. PlanModeManager（设计优先模式）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 计划模式开关 | PlanModeContextState.planActive 持久化在 AgentState | 无对应概念 |
| 执行强制 | PlanModeMiddleware.onActing() 拦截工具调用 | 无 |
| 只读策略 | 白名单工具 + 只读解析器 Predicate | 无 |
| 系统 Prompt 注入 | PLAN_BANNER / BUILD_MODE_PLAN_HINT 模板 | 手动编写 |
| 计划文件路径 | workspace-relative, 持久化在 state | Plan markdown 加载 |

**关键文件**：`agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java` (L39-115), `PlanModeMiddleware.java` (L58-243).

**强制执行逻辑**（`PlanModeMiddleware.onActing()`, L169-233）：
```
planActive=true 时：
  - 白名单工具（plan_enter/write/exit, todo_write, agent_spawn/send/list）：始终放行
  - 只读工具：通过 readOnlyResolver Predicate 判定
  - opt-in 逃生口：additionalAllowed 列表
  - 其他所有工具：合成 DENY 消息写入 context
```

**持久化设计**（`PlanModeContextState` Javadoc L31-34）：
> "This lives inside AgentState (rather than in volatile in-process memory) precisely because agentscope agents are distributed and resumable: a plan-mode session must survive process restarts and hand-offs across nodes."

**nop-ai-agent 建议**：**HIGH 优先级引入**。计划模式是生产级 Agent 的关键模式。将模式持久化在 AgentState 中跨重启保持、通过 Middleware 自动强制的设计清晰健壮。nop-ai-agent 已有 Plan markdown 基础能力，缺少的是模式切换和执行强制。

### 8. SkillCurator（技能化育管线）

| 维度 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| 生命周期 | DRAFT -> ACTIVE -> STALE -> ARCHIVED（时间驱动） | LLM 驱动的 curation |
| 推广管线 | 4 阶段：Load Draft -> Security Scan -> Gate Review -> Physical Move | ISkillProvider 接口 |
| 门禁决策 | sealed 层级：Approve / Reject / Defer | 无对应 |
| 安全扫描 | SkillSecurityScanner（检查脚本/模板/资产） | 沙箱隔离 |
| 遥测 | SkillUsageStore（use/view/patch 计数 + 状态迁移） | IUsageRecorder |
| 聚合建议 | 前缀聚类干运行报告 | LLM 聚合 |
| 挡板存储 | `skills/.usage.json` 侧边文件 | 数据库 |

**关键文件**：`agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/curator/SkillCurator.java` (L58-344), `SkillPromoter.java` (L37-335).

**推广管线**（`SkillPromoter.promote()`, L87-118）：
```
1. 从 _drafts/ 加载草稿
2. SecurityScanner.scan() 安全检查
3. PromotionGate.review() → Approve / Reject / Defer
4. WorkspaceManager.moveSkill() 物理移动 _drafts/ → skills/
5. UsageStore.markAgentCreated() 印记出处
```

**nop-ai-agent 建议**：**LOW 优先级**。nop-ai-agent 的基础设施已更数据库化，但推广管线的「草稿 -> 安全扫描 -> 门禁 -> 物理移动」结构比当前接口设计更清晰。sealed 门禁决策是 Java 17 的 idiomatic 用法。

### 深度代码对比总结

| 模式 | 优先级 | 核心价值 | AgentScope 文件 |
|------|--------|---------|----------------|
| MiddlewareBase 5 点洋葱 | **HIGH** | 架构升级：组合式 Agent 生命周期拦截 | `core/middleware/MiddlewareBase.java` | [设计文档](../../design/nop-ai-agent/nop-ai-agent-middleware-design.md) |
| ToolGroup → Tag System | **HIGH** | 动态工具表面控制，多租户关键 → K8s-style 标签方案 | `core/tool/ToolGroupManager.java` | [设计文档](../../design/nop-ai-agent/nop-ai-agent-tool-tag-system.md) |
| PlanMode 设计优先 | **HIGH** | 生产级「先想后做」强制模式 | `harness/workspace/plan/PlanModeManager.java` |
| GracefulShutdown 安全点 | **MEDIUM** | per-request + per-session 并发关闭 | `core/shutdown/GracefulShutdownManager.java` |
| PermissionEngine ASK 模式 | **MEDIUM** | EXPLORE/DONT_ASK 模式 + isTrivial() | `core/permission/PermissionEngine.java` |
| SubAgentTool 工厂模式 | **MEDIUM** | 干净的事件转发 + 取消守卫 | `core/tool/subagent/SubAgentTool.java` |
| AgentState 计划模式持久化 | **MEDIUM** | PlanModeContextState 跨重启保留 | `core/state/PlanModeContextState.java` |
| SkillCurator 推广管线 | **LOW** | 草稿->扫描->门禁->上线结构化 | `harness/skill/curator/SkillCurator.java` |

## 集成路径建议

### 1. 技术集成

#### AgentScope 作为 Nop 服务的"智能用户"
- AgentScope Agent 通过工具调用 Nop GraphQL API 或 Biz 层
- Nop IoC 管理 AgentScope Agent 实例（作为 beans）
- Nop 的代码生成管线为 AgentScope 生成 tool schema / agent config

#### nop-ai-agent 引入响应式 LLM 调用
- LLM 调用层使用 Project Reactor Mono/Flux
- 保持上层同步/命令式架构
- 通过适配器桥接响应式和同步代码

### 2. 设计模式融合

#### Middleware + Hook 混合架构
- 核心拦截点使用 Middleware（洋葱模型）
- 细粒度事件使用 Hook（事件驱动）
- 两者通过 LegacyHookDispatcher 桥接

#### 声明式 + Builder 混合配置
- Agent 定义使用声明式 XDSL
- 运行时配置使用 Builder 模式
- 两者通过代码生成统一

#### 三态权限 + 四层安全
- 权限评估使用三态（ALLOW/ASK/DENY）
- 安全检查使用四层纵深
- 两者通过 PermissionMatrix 统一

## 性能对比

### 响应式 vs 同步

| 场景 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| **高并发 LLM 调用** | 优势（Reactor 非阻塞） | 劣势（同步阻塞） |
| **工具并行执行** | 优势（Flux 并行） | 优势（CompletableFuture） |
| **内存占用** | 劣势（Reactor 开销） | 优势（轻量级） |
| **冷启动** | 劣势（Reactor 初始化） | 优势（Virtual Threads） |

### 状态管理

| 场景 | AgentScope Java | nop-ai-agent |
|------|----------------|--------------|
| **会话持久化** | 优势（AgentState 不可变） | 优势（AgentSession 灵活） |
| **崩溃恢复** | 优势（Graceful Shutdown） | 优势（恢复协议） |
| **多租户隔离** | 优势（Slot-based） | 优势（Session-based） |

## 结论

AgentScope Java 和 nop-ai-agent 代表了两种不同的 Agent 框架设计哲学：

- **AgentScope Java**：全响应式 Agent 运行时，强调高吞吐和流式处理
- **nop-ai-agent**：声明式 Agent 引擎，强调可逆计算和增量能力

两者架构理念互补，可互相借鉴：
- AgentScope 的响应式架构和 Middleware 拦截可增强 nop-ai-agent 的性能
- nop-ai-agent 的声明式配置和四层安全可增强 AgentScope 的可配置性和安全性

最直接的集成路径是 AgentScope Agent 作为 Nop 服务的"智能用户"，通过工具调用 Nop 平台能力。

## 深度专题分析

以下专题文档在本对比之上提供了更深入的逐项分析：

| 专题 | 文件 | 核心内容 |
|------|------|---------|
| 文件系统抽象 + Delta | `agentscope-fs-vs-nop-fs-with-delta.md` | 接口签名对比、错误处理哲学、Delta 分层覆盖分析、LayeredToolFileSystem 设计推演 |
| Tool Dispatch Pipeline | `agentscope-vs-nop-tool-dispatch.md` | 工具注册/发现/执行流程、Middleware vs 安全 pipeline、并行策略、Tool call repair |
| Memory / Knowledge | `agentscope-vs-nop-memory-knowledge.md` | 记忆架构（文件 vs 结构化）、ingestion 路径、检索路径、上下文压缩 |
| Subagent Orchestration | `agentscope-vs-nop-subagent-orchestration.md` | 子代理模型（工具驱动 vs 会话驱动）、通信机制、DAG 编配、安全继承 |

## 对 nop-ai-agent 的 8 点具体建议

按本报告深度代码对比分析（第 8 节），按实施优先级排列：

### P0 — 立即实施

1. **引入 MiddlewareBase 洋葱架构**：在现有 IToolCallInterceptor 基础上扩展为 5 点拦截（onAgent/onReasoning/onActing/onModelCall/onSystemPrompt），取代零散的 Hook 逻辑。具体参考 `agentscope-core/.../middleware/MiddlewareBase.java` 的 5 接口定义 + `MiddlewareChain.java` 的从后往前组装算法。

2. **引入 ToolGroup 动态激活**：在 AgentModel 中添加 ToolGroup 定义（groupName + scope + active 标志），ToolGroupManager 实现 `isActiveTool()` 算法。META 组可通过 `reset_equipped_tools` 元工具动态管理，EXTERNAL 组仅开发者可管理。具体参考 `agentscope-core/.../tool/ToolGroupManager.java` 的 `isActiveTool()` (L322-336) 核心算法。

3. **引入 PlanMode 设计优先模式**：在 AgentState 中添加 planActive 标志（持久化跨重启），添加 PlanModeMiddleware 在 onActing 中拦截非只读工具调用。参考 `agentscope-harness/.../plan/PlanModeManager.java` + `PlanModeMiddleware.java` (L169-233 强制执行逻辑)。

### P1 — 下一轮迭代

4. **优化 GracefulShutdown**：引入 per-request requestId + per-session state 绑定，在 Safe Point（推理/行动阶段完成时）检查中断信号。

5. **PermissionEngine 模式扩展**：引入 EXPLORE/DONT_ASK 模式 + `isTrivial()` 快速路径优化。

6. **SubAgentTool 工厂模式**：将 `call-agent` 工具从预注册模式改为 `SubAgentProvider` 工厂模式，添加事件转发和 `doFinally` 取消守卫。

### P2 — 长期参考

7. **AgentState PlanModeContext 持久化**：将「模式状态」直接嵌入 AgentSession 持久化，确保跨进程重启保持。

8. **SkillCurator 推广管线**：草稿 -> 安全扫描 -> 门禁审批 -> 物理移动 -> 印记出处的结构化管线，sealed 门禁决策。

## Open Questions

- [ ] AgentScope 的 Reactor 响应式模型如何与 Nop 的同步模型桥接？
- [ ] Nop IoC 能否直接管理 AgentScope 的 Agent bean？
- [ ] AgentScope 的 Middleware 系统是否适合作为 Nop biz 拦截器的参考？
- [ ] nop-ai-agent 的声明式配置是否可移植到 AgentScope？
- [ ] AgentScope 的 ToolGroup 动态激活是否适合 nop-ai-agent 的 Skill 系统？

## References

- ~/ai/agentscope-java/README.md, README_zh.md
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareBase.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/middleware/MiddlewareChain.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/permission/PermissionEngine.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/permission/PermissionMode.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/ToolGroupManager.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/tool/subagent/SubAgentTool.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/shutdown/GracefulShutdownManager.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/AgentState.java
- ~/ai/agentscope-java/agentscope-core/src/main/java/io/agentscope/core/state/PlanModeContextState.java
- ~/ai/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/workspace/plan/PlanModeManager.java
- ~/ai/agentscope-java/agentscope-harness/src/main/java/io/agentscope/harness/agent/skill/curator/SkillCurator.java
- nop-ai/nop-ai-agent/ (Nop AI Agent 模块)
- nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/api/IToolCallInterceptor.java
- nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java
- ai-dev/analysis/agent-survey/2026-06-05-agentscope-java-analysis.md
