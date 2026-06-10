# Nop AI Agent 核心术语表

本术语表定义 nop-ai-agent 设计文档中的核心概念、接口和类型，确保跨文档命名一致。

## 标识符

| 术语 | 语义 | 说明 |
|------|------|------|
| `agentName` | Agent 配置名（来自 `agent.xdef` `name` 属性） | DSL 和 API 层使用，包括 `call-agent` DSL 的 `agentName` 属性 |
| `actorId` | Agent 运行时实例 UUID | 引擎内部和 Actor 寻址使用 |
| `sessionId` | Agent Session 的唯一标识 | 跨请求持久存在 |
| `channelType` | 具体信道类型标识 | 如 `"feishu"`, `"dingtalk"`, `"api"` |
| `channelKind` | 权限分类的信道类别 | 如 `"webui"`, `"api"`, `"dm"`, `"group"` |

## 核心对象

| 术语 | 类型 | 定义 | 首次定义 |
|------|------|------|---------|
| `AgentModel` | 类 | 从 `agent.xdef` 装载的纯配置对象，DSL 在引擎层的投影 | 01-architecture-baseline.md §4 |
| `Agent` | 类 | 无状态执行体，根据 AgentModel 驱动执行循环 | 01-architecture-baseline.md §4 |
| `AgentSession` | 类 | 按 sessionId 获取的独立持久化状态对象 | 01-architecture-baseline.md §4 |
| `AgentExecutionContext` | 类 | 单次执行的全部内存态数据容器 | 01-architecture-baseline.md §4 |
| `ChatMessage` | abstract class | Provider 无关的统一消息基类（5 种子类型：user/assistant/system/tool/custom），定义在 `nop-ai-api` | nop-ai-agent-llm-layer.md §3 |
| `ChatToolCall` | 类 | 工具调用数据：`id`, `name`, `Map arguments` | nop-ai-agent-llm-layer.md §3 |
| `ChatAttachment` | 类 | 多模态附件：`resourceType`, `resourceUrl`, `fileName` | nop-ai-agent-llm-layer.md §3 |
| `CompactionEntry` | 数据结构 | Event Log 中的压缩边界记录 | nop-ai-agent-session-and-storage.md §5.2 |

## 核心接口

| 术语 | 层级 | 定义 |
|------|------|------|
| `IAgentEngine` | Gateway | Actor 消息入口：接受消息，路由到 AgentActor |
| `IAgentExecutor` | Layer 1 | 执行策略接口（`execute()` 返回 `CompletionStage<AgentExecutionResult>`） |
| `IAgentMemory` | Layer 1 | 三层记忆管理（短期 compaction + Working Memory 工具 + 长期 IMemoryAdapter） |
| `IMessageService` | 基础设施 | Agent 间内部通信（LocalMessageService / DB-backed） |
| `ILlmDialect` | nop-ai-core | Provider 消息格式转换（Formatter pattern），Agent Engine 不直接依赖 |
| `IModelRouter` | Layer 2 | 请求路由策略（Judge + Fallback Chain） |
| `ITalent` | Layer 2 | 动态行为准入（运行时上下文开关工具集） |
| `IRetryPolicy` | Layer 3 | Provider 重试策略 |
| `IContextCompactor` | Layer 2 | 渐进上下文压缩 |
| `IToolCallRepairer` | Layer 2 | 工具调用修复链 |
| `IContentGuardrail` | Layer 2 | 输入/输出内容护栏 |
| `ISecurityLevelResolver` | Layer 2 | 安全等级解析 |
| `IPermissionMatrix` | Layer 2 | 通道 × 等级权限矩阵 |
| `IAgentLifecycleHook` | Layer 2 | 生命周期事件处理 |
| `IPermissionProvider` | Layer 1 | 权限派生（3-source merge） |
| `IToolAccessChecker` | Layer 1 | 工具 deny/allow 检查 |
| `IPathAccessChecker` | Layer 1 | 路径 deny/allow 检查 |
| `IApprovalGate` | Layer 3 | 人类审批门 |
| `IDenialLedger` | Layer 3 | 拒绝计数 + 阈值暂停 |
| `IMemoryAdapter` | Layer 4 | 记忆持久化适配器（Storage / Embedding / Vector） |
| `IChannelConnector` | Gateway | 外部信道连接器 |
| `ISkillProvider` | Layer 2 | Skill 引擎：发现和装配技能 |

## 事件类型

| 术语 | 定义 |
|------|------|
| `TextChunk` | LLM 流式输出中间块（纯观察事件） |
| `ThinkingChunk` | LLM 推理过程中间块 |
| `ToolCallStart` | 工具调用开始 |
| `ToolCallComplete` | 工具调用完成 |
| `AgentResult` | Agent 执行完成 |
| `AgentError` | Agent 执行错误 |
| `AgentInterrupted` | Agent 被中断 |

## Hook 生命周期点

Java 常量使用 `UPPER_SNAKE_CASE`，DSL event 属性匹配使用 `snake_case`。二者是同一概念的两种表示。

**Layer 1 核心**：

| Java 常量 | DSL 属性 | 触发时机 |
|-----------|---------|---------|
| `PRE_REASONING` | `before_reasoning` | LLM 调用前 |
| `POST_REASONING` | `after_reasoning` | LLM 响应后 |
| `PRE_ACTING` | `before_acting` | 工具执行前 |
| `POST_ACTING` | `after_acting` | 工具执行后 |
| `ON_ERROR` | `on_error` | 错误发生时 |

**Layer 2 扩展**：

| Java 常量 | DSL 属性 | 触发时机 |
|-----------|---------|---------|
| `PRE_CALL` | `before_call` | Agent 执行前 |
| `POST_CALL` | `after_call` | Agent 执行后 |
| `REASONING_CHUNK` | `reasoning_chunk` | LLM 流式输出块 |
| `PRE_COMPACT` | `before_compact` | 上下文压缩前 |
| `POST_COMPACT` | `after_compact` | 上下文压缩后 |

## 概念术语

| 术语 | 定义 |
|------|------|
| compaction（压缩） | 上下文窗口管理：减少消息历史长度以适配 token 预算。名词 `compaction`，动词 `compact`。不使用 `compression` |
| followUp | 外层循环：Agent 完成执行后检查排队的后续消息 |
| steering | 内层循环中的外部消息注入机制 |
| HITL | Human-In-The-Loop 缩写。描述性文本中使用，架构流程中使用 `IApprovalGate` |
| Skill | 结构化能力定义（含匹配签名、场景阶段、资源声明） |
| Talent | 运行时动态准入（ITalent 接口，基于上下文开关工具集） |
| Plan | 结构化执行计划（`agent-plan.xdef`），项目级实体 |
| Task | Plan 内的子单元 |
| Todo | 轻量任务补充（`constraints.todos`） |

## 命名约定

| 类别 | 约定 | 示例 |
|------|------|------|
| 接口名 | `I` + PascalCase | `IAgentEngine`, `IToolExecutor` |
| 类名 | PascalCase | `AgentModel`, `ChatMessage` |
| 字段名 | camelCase | `sessionId`, `actorId`, `agentName` |
| DSL 属性 | camelCase | `tokenCompactionThreshold` |
| Hook 常量 | UPPER_SNAKE_CASE | `PRE_REASONING`, `POST_ACTING` |
| Hook DSL 匹配值 | snake_case | `before_reasoning`, `after_acting` |
| 事件类型 | PascalCase | `AgentResult`, `ToolCallComplete` |
