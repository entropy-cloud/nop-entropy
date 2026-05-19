# Nop AI Agent 设计文档

核心定位：**面向大规模无人值守自动化执行**。

本目录采用分层设计方式，明确区分：

1. 架构总览层
2. DSL 层
3. Java 引擎层
4. DSL 到引擎的语义映射层
5. 策略层

## 架构总览层

- `nop-ai-agent-core-architecture.md`
  - 核心架构设计：无人值守定位、分层、核心对象、双循环执行流程、关键决策
  - 本目录的顶层入口，建议首先阅读
- `nop-ai-agent-architecture-comparison.md`
  - 外部框架对比分析（agentscope-java, solon-ai, pi-agent, openai-agents, smolagents）
  - 记录架构决策的外部参照和理由
- `nop-ai-agent-context-model.md`
  - Agent 上下文模型：组成维度、Tool 可见性、Agent-as-Subprocess 隐喻、上下文继承与 fork、内部 Agent 化
- `nop-ai-agent-multi-agent.md`
  - 多 Agent 并行协同：冲突分类、文件写意图、资源声明、通信模型

## DSL 层

- `nop-ai-agent-dsl.md`
  - 对应 `agent.xdef`
- `nop-ai-agent-plan-dsl.md`
  - 对应 `agent-plan.xdef`
- `nop-ai-tool-dsl.md`
  - 对应 `tool.xdef`、`tool-call.xdef`、`call-tools.xdef`、`call-tools-response.xdef`
  - 包含工具执行上下文可见性定义
- `nop-ai-call-agent-dsl.md`
  - 对应真实 `call-agent.tool.xml`

## Java 引擎层

- `nop-ai-agent-engine.md`
  - Java 执行引擎总体设计
  - **注意**：此文档已被 `nop-ai-agent-core-architecture.md` 替代。本目录保留它作为历史参照。
- `nop-ai-agent-react-engine.md`
  - 单 Agent ReAct 执行引擎，含双循环模型（仍然有效）
- `nop-ai-agent-hook-skill-engine.md`
  - Hook / Skill 的引擎层组织方式，含内部 Agent 化概念
- `nop-ai-agent-session-engine.md`
  - Session 的引擎层设计

## 语义映射层

- `nop-ai-agent-runtime-semantics.md`
  - 解释 DSL 如何映射到运行时行为

## 策略层

- `nop-ai-agent-session-and-storage.md`
  - Session、快照、分叉、每消息 snapshotId、Session Tree、压缩回写、存储边界
- `nop-ai-agent-security-and-permissions.md`
  - 权限、安全边界、目录保护
- `nop-ai-agent-reliability.md`
  - 错误分类、压缩、超时、回退、恢复
- `nop-ai-agent-roadmap.md`
  - DSL-first + engine-aware 的实施路线

## 阅读顺序

1. `nop-ai-agent-core-architecture.md` — 核心架构全景（含无人值守定位）
2. `nop-ai-agent-context-model.md` — Agent 上下文与 subprocess 模型
3. `nop-ai-agent-architecture-comparison.md` — 为什么这样设计
4. `nop-ai-agent-dsl.md`
5. `nop-ai-agent-plan-dsl.md`
6. `nop-ai-tool-dsl.md`
7. `nop-ai-call-agent-dsl.md`
8. `nop-ai-agent-react-engine.md`
9. `nop-ai-agent-hook-skill-engine.md`
10. `nop-ai-agent-session-engine.md`
11. `nop-ai-agent-runtime-semantics.md`
12. `nop-ai-agent-multi-agent.md`
13. `nop-ai-agent-session-and-storage.md`
14. `nop-ai-agent-security-and-permissions.md`
15. `nop-ai-agent-reliability.md`
16. `nop-ai-agent-roadmap.md`

## 设计原则

1. 核心定位：大规模无人值守自动化
2. 先明确 DSL 形态
3. 再明确 Java 引擎设计
4. 再说明 DSL 如何映射到引擎
5. 再补安全、存储、可靠性等策略层约束
6. 不把未来设想伪装成当前 DSL 字段
7. 架构决策有外部参照（对比分析文档）
8. Agent 类似子进程——fork、exec、inherit、pipe

## 当前结论

当前目录已经形成五层结构，覆盖 17 篇设计文档：

- 架构总览层定义核心架构、上下文模型、多 Agent 协同和决策理由
- DSL 层定义配置形态
- Java 引擎层定义执行模型
- 语义映射层定义两者关系
- 策略层定义边界和运行约束
