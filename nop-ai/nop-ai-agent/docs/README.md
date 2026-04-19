# Nop AI Agent 设计文档

本目录采用分层设计方式，明确区分：

1. DSL 层
2. Java 引擎层
3. DSL 到引擎的语义映射层
4. 策略层

设计起点应先从 DSL 开始，但完整设计文档不能只有 DSL，也不能只有 Java 引擎。二者是两个层面。

## DSL 层

- `nop-ai-agent-dsl.md`
  - 对应 `agent.xdef`
- `nop-ai-agent-plan-dsl.md`
  - 对应 `agent-plan.xdef`
- `nop-ai-tool-dsl.md`
  - 对应 `tool.xdef`、`tool-call.xdef`、`call-tools.xdef`、`call-tools-response.xdef`
- `nop-ai-call-agent-dsl.md`
  - 对应真实 `call-agent.tool.xml`

## Java 引擎层

- `nop-ai-agent-engine.md`
  - Java 执行引擎总体设计
- `nop-ai-agent-react-engine.md`
  - 单 Agent ReAct 执行引擎
- `nop-ai-agent-hook-skill-engine.md`
  - Hook / Skill 的引擎层组织方式
- `nop-ai-agent-session-engine.md`
  - Session 的引擎层设计

## 语义映射层

- `nop-ai-agent-runtime-semantics.md`
  - 解释 DSL 如何映射到运行时行为

## 策略层

- `nop-ai-agent-session-and-storage.md`
  - Session、快照、分叉、压缩回写、存储边界
- `nop-ai-agent-security-and-permissions.md`
  - 权限、安全边界、目录保护
- `nop-ai-agent-reliability.md`
  - 错误分类、压缩、超时、回退、恢复
- `nop-ai-agent-roadmap.md`
  - DSL-first + engine-aware 的实施路线

## 阅读顺序

建议按下面顺序阅读：

1. `nop-ai-agent-dsl.md`
2. `nop-ai-agent-plan-dsl.md`
3. `nop-ai-tool-dsl.md`
4. `nop-ai-call-agent-dsl.md`
5. `nop-ai-agent-engine.md`
6. `nop-ai-agent-react-engine.md`
7. `nop-ai-agent-hook-skill-engine.md`
8. `nop-ai-agent-session-engine.md`
9. `nop-ai-agent-runtime-semantics.md`
10. `nop-ai-agent-session-and-storage.md`
11. `nop-ai-agent-security-and-permissions.md`
12. `nop-ai-agent-reliability.md`
13. `nop-ai-agent-roadmap.md`

## 设计原则

1. 先明确 DSL 形态
2. 再明确 Java 引擎设计
3. 再说明 DSL 如何映射到引擎
4. 再补安全、存储、可靠性等策略层约束
5. 不把未来设想伪装成当前 DSL 字段

## 当前结论

当前目录已经形成四层结构：

- DSL 层定义配置形态
- Java 引擎层定义执行模型
- 语义映射层定义两者关系
- 策略层定义边界和运行约束

这组文档现在可以作为当前 `nop-ai-agent` 的设计文档集合。
