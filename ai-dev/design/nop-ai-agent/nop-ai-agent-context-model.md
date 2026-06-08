# Nop AI Agent 上下文模型设计

## 1. 目标

本篇定义 Agent 的上下文模型——Agent 执行时携带的环境信息、与 Tool 的共享机制、以及子 Agent 的上下文继承与分叉语义。

核心隐喻：**Agent 类似操作系统子进程**。Agent 的上下文类似进程环境，工具调用类似系统调用，子 Agent 派生类似 fork/exec。

## 2. 设计定位

本篇属于架构总览层的补充，解决以下问题：

1. Agent 执行时能访问什么信息
2. Tool 执行时能访问什么信息
3. 父子 Agent 之间上下文如何传递
4. Fork 后的上下文独立性如何保证

本篇不定义具体的上下文数据结构——源码是唯一事实。

## 3. Agent 上下文的组成

Agent 上下文是引擎层概念，不是 DSL 层概念。它由以下维度构成：

| 维度 | 说明 | 是否持久化 |
|------|------|-----------|
| 消息历史 | 当前会话的完整消息序列 | 是（VFS `.nop/` Event Log） |
| 协调消息 | 其他 Agent 的 scope_claim/operation_intent（见 multi-agent.md §4） | 否（每轮 ReAct 前注入，不持久化） |
| 计划状态 | 当前 Plan 的结构和进度 | 是（项目级 `ai-dev/plans/`，AGE 规范，跨 session） |
| 工具集 | 当前 Agent 可见的工具集合 | 否（运行时装配） |
| 约束配置 | maxIterations、token 预算、超时等 | 否（从 DSL 读取） |
| 环境信息 | 工作目录、环境变量、文件系统访问范围 | 否（运行时注入） |
| 会话标识 | sessionId、snapshotId、parentSession | 是（VFS `.nop/` Event Log） |

**决策**：Tool 执行时能访问 Agent 上下文的一个受控子集（只读或受限修改），而不是全部。

**理由**：Tool 不应该能直接修改 Agent 的消息历史或计划状态。Tool 只能通过返回值（tool result）间接影响 Agent 的后续推理。

**拒绝了**：Tool 直接操作 Agent 上下文。理由是这与操作系统的系统调用模型一致——系统调用通过返回值影响进程，而不是直接修改进程内存。

## 4. Tool 上下文环境

### 4.1 Tool 能访问什么

Tool 执行时，引擎提供以下上下文信息：

| 可见信息 | 访问方式 |
|---------|---------|
| 当前工具调用的参数 | 由 LLM 输出解析得到 |
| 工作目录和文件系统范围 | 由 Agent 环境配置决定 |
| Agent 的 sessionId | 只读，用于需要持久化的工具 |
| 当前请求的环境变量 | 只读 |

| 不可见信息 | 原因 |
|-----------|------|
| Agent 的完整消息历史 | Tool 不需要知道整个对话 |
| Agent 的 Plan 状态 | Tool 不需要知道全局计划 |
| 其他 Tool 的内部状态 | 隔离性 |
| 其他 Agent 的上下文 | 隔离性 |

### 4.2 ask-oracle（人机交互）的特殊性

`ask-oracle` 是一个特殊工具，它的语义是"向人类请求指导"。它的执行不依赖 Agent 上下文，但返回值（人类的回答）会写入消息历史。

**决策**：ask-oracle 作为标准 Tool 定义在 nop-ai-toolkit 中，不享受特殊引擎层待遇。

**理由**：所有交互都走 XML Tool 路径，保持一致性。ask-oracle 与 read-file 在引擎层面没有本质区别——都是"执行一个工具，返回一个结果"。

## 5. Agent-as-Subprocess 模型

### 5.1 进程隐喻映射

| 操作系统概念 | Agent 对应 | 说明 |
|------------|-----------|------|
| 进程 (Process) | Agent 执行实例 | 一次运行中的 Agent |
| fork | 上下文复制 + 新 sessionId | 子 Agent 获得父 Agent 的上下文快照 |
| exec | agentName 切换 | 在 fork 的基础上切换到新 Agent 配置 |
| 环境变量继承 | 上下文继承 | 子 Agent 继承父 Agent 的环境信息 |
| 标准输入 (stdin) | 初始消息/请求 | Agent 启动时的输入 |
| 标准输出 (stdout) | 最终结果 | Agent 执行完成后的输出 |
| 标准错误 (stderr) | 错误和日志 | Agent 执行过程中的错误信息 |
| 进程间通信 (pipe) | 消息传递 | 父子 Agent 之间的消息流 |
| 退出码 (exit code) | 执行状态 | success/failure/timeout 等 |

### 5.2 call-agent 的语义重述

`call-agent` 工具是 Agent-as-Subprocess 模型的入口：

| call-agent 参数 | 进程隐喻 | 说明 |
|----------------|---------|------|
| agentName | exec 的目标程序 | 要执行的 Agent 配置 |
| sessionId | 进程的会话关联 | 延续已有会话或新建 |
| inheritContext | 是否继承环境变量 | self 模式下的上下文继承 |
| skills | 进程的能力配置 | 本次调用的技能集合 |
| input | stdin | 传递给子 Agent 的输入 |
| output | stdout | 子 Agent 的返回结果 |

### 5.3 Fork 语义

**决策**：Fork 是 Session 级别的操作，不是 Agent 级别。

**期望行为**：

1. Fork 基于当前 session 创建新的 session
2. 新 session 的消息历史是当前 session 的快照
3. 新 session 的 Plan 状态是当前 Plan 的深拷贝
4. 新 session 的工具集和约束配置从新 Agent 配置重新装配
5. Fork 后，父子 session 完全独立——修改互不影响

**与 `call-agent` 的关系**：`call-agent` 的 `inheritContext=true` + `agentName="self"` 等价于 fork。

**拒绝了**：写时复制（Copy-on-Write）。理由是 Agent 的消息和 Plan 不是高频修改的热数据，深拷贝的性能开销可接受，而 CoW 的实现复杂度不值得。

### 5.4 上下文继承协议

当子 Agent 通过 `call-agent` 启动时，上下文传递规则：

| 上下文维度 | 继承行为 |
|-----------|---------|
| 消息历史 | inheritContext=true 时继承快照，否则为空 |
| Plan 状态 | inheritContext=true 时继承深拷贝，否则为空 |
| 工具集 | 不继承——从子 Agent 的 agent.xdef 重新装配 |
| 约束配置 | 不继承——从子 Agent 的 agent.xdef 重新装配 |
| 环境信息 | 继承（工作目录、文件系统范围） |
| Session 标识 | 新 sessionId，parentSession 指向父 |

## 6. 内部 Agent 化

### 6.1 概念

部分引擎内部能力（如上下文压缩、错误修复、结果评审）可以用 Agent 来实现。这些内部 Agent 与外部 Agent 的区别在于：

| 维度 | 内部 Agent | 外部 Agent |
|------|-----------|-----------|
| 触发方式 | 引擎内部自动触发 | 外部请求触发 |
| Session | 可共享父 Agent 的 session | 独立 session |
| 工具集 | 受限（只暴露必要工具） | 完整 |
| 可见性 | 对外部不可见 | 对外部可见 |
| 失败影响 | 不中止父 Agent（降级为默认策略） | 可中止 |

### 6.2 可 Agent 化的能力

以下引擎内部能力可以设计为 Agent 接口：

| 能力 | 输入 | 输出 | 失败降级策略 |
|------|------|------|------------|
| 上下文压缩 | 当前消息历史 | 压缩后的消息历史 | 5 层渐进管道（Layer 0-4，见 reliability.md §7） |
| 错误修复 | 错误信息 + 当前上下文 | 修复后的消息或指令 | 标准错误处理 |
| 结果评审 | Agent 的执行结果 | 通过/不通过 + 理由 | 信任原始结果 |
| Plan 调整 | 当前 Plan + 执行进度 | 调整后的 Plan | 保持原 Plan |

### 6.3 接口契约

内部 Agent 化的接口遵循统一的薄接口模式：

- 输入是结构化数据（不是自由文本 prompt）
- 输出是结构化数据（不是自由文本 response）
- 接口实现可以是硬编码逻辑，也可以是 Agent 调用
- 引擎不关心实现方式，只关心接口契约

**决策**：内部 Agent 化是 Phase 2 的设计重点。Phase 1 所有内部能力用硬编码逻辑实现。

**理由**：先稳定核心循环，再逐步将硬编码逻辑替换为 Agent 实现。接口设计时预留 Agent 化空间即可。

## 7. 与现有文档的关系

| 本篇内容 | 相关文档 | 关系 |
|---------|---------|------|
| §3 上下文组成 | session-and-storage.md | 本篇定义维度，session-and-storage.md 定义持久化格式 |
| §4 Tool 上下文 | tool-dsl.md | 本篇定义运行时可见性，tool-dsl.md 定义 DSL 形态 |
| §5 Agent-as-Subprocess | call-agent-dsl.md | 本篇定义语义模型，call-agent-dsl.md 定义 DSL 字段 |
| §6 内部 Agent 化 | hook-skill-engine.md | 本篇定义可 Agent 化的能力清单，hook-skill-engine.md 定义扩展机制 |
