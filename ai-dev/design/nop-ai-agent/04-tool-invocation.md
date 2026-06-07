# Nop AI Agent 工具调用架构

**日期**：2026-06-06
**范围**：Agent Engine Layer 的工具发现、执行和并行策略
**状态**：active

---

## 一、设计结论

1. 工具发现通过 `agent.xdef` 的 `<tools>` 声明 → 引擎按名称加载 `.tool.xml` → 构建 LLM 可见的工具 schema
2. 工具执行流经 PRE_ACTING hook → HITL 检查 → 执行器 → POST_ACTING hook → 结果写回
3. 并行工具执行通过 `call-tools.xdef` 的 `parallel` 属性控制
4. 保持 XML Tool DSL 为主格式，JSON Schema 作为中间转换格式（Phase 2）

## 二、工具发现

```
agent.xdef 中的 <tools> 声明工具名列表
  → AgentEngine 根据 tool name 加载对应的 .tool.xml
  → 构建 LLM 可见的工具 schema
  → 工具 schema 注入 LLM 请求
```

## 三、工具执行流程

```
LLM 返回工具调用（XML 格式，解析为 ToolCall 对象）
  → PRE_ACTING hook（可 block）
  → HITL 检查（如果启用人机协同）
  → 工具执行器执行
  → POST_ACTING hook（可修改结果）
  → 结果写回消息历史
```

## 四、并行工具执行

- `call-tools.xdef` 的 `parallel` 属性控制是否并行
- `maxConcurrency` 限制并发数
- 引擎应支持并行执行多工具调用

## 五、JSON Schema 兼容

**决策**：保持 XML Tool DSL 作为主要格式，增加 JSON Schema 格式作为工具参数的中间转换格式。

**理由**：
- 部分 LLM Provider 更擅长处理 JSON 格式的工具定义
- 可以作为 `.tool.xml` 到 LLM prompt 的中间格式，而不改变 XML DSL 本身

**阶段归属**：Phase 2（可插拔增强），不在 Phase 1 核心闭环中。Phase 1 使用现有 XML Tool DSL 即可。

**拒绝了**：完全切换到 JSON Schema。理由是 XML DSL 是 Nop XLang 生态的一部分，放弃它会破坏一致性。

---

## 与其他文档的关系

- `02-execution-model.md` — 本篇嵌入的执行模型（ReAct 循环中的工具执行环节）
- `nop-ai-tool-dsl.md` — 工具 DSL 详细设计（`tool.xdef`、`tool-call.xdef`、`call-tools.xdef`）
- `nop-ai-call-agent-dsl.md` — call-agent 工具 DSL
- `nop-ai-agent-security-and-permissions.md` — 工具执行的安全边界
