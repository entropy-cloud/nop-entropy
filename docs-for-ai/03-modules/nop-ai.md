# nop-ai — AI 集成模块

## 功能概览

全面的 AI 集成子系统，覆盖 LLM 交互到 AI 辅助开发。

- **LLM Chat**：多模型聊天接口
- **Prompt 模板管理**：版本化 Prompt 模板
- **AI Agent**：Agent 框架
- **RAG**：检索增强生成
- **AI Coder**：AI 辅助编码
- **MCP Server**：Model Context Protocol 服务端
- **AI Shell**：命令行 AI 交互
- **AI Skills**：技能/工具包
- **多模型测试与评分**：对比不同模型输出质量

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopAiProject | `nop_ai_project` | AI 项目 |
| NopAiProjectRule | `nop_ai_project_rule` | 项目规则 |
| NopAiModel | `nop_ai_model` | AI 模型注册（provider, modelName, baseUrl, apiKey） |
| NopAiRequirement | `nop_ai_requirement` | 需求管理 |
| NopAiKnowledge | `nop_ai_knowledge` | 知识库 |
| NopAiPromptTemplate | `nop_ai_prompt_template` | Prompt 模板 |
| NopAiChatRequest | `nop_ai_chat_request` | 聊天请求 |
| NopAiChatResponse | `nop_ai_chat_response` | 聊天响应（含评分） |
| NopAiSession | `nop_ai_session` | 聊天会话 |
| NopAiGenFile | `nop_ai_gen_file` | AI 生成文件 |
| NopAiTestCase | `nop_ai_test_case` | 测试用例 |

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-ai-core` | AI 核心接口 |
| `nop-ai-llm` | LLM 集成 |
| `nop-ai-agent` | Agent 框架 |
| `nop-ai-rag` | RAG 检索增强 |
| `nop-ai-skills` | AI 技能 |
| `nop-ai-tools` | AI 工具 |
| `nop-ai-toolkit` | 工具包 |
| `nop-ai-coder` | AI 辅助编码 |
| `nop-ai-shell` | 命令行交互 |
| `nop-ai-mcp-server` | MCP Server |
| `nop-ai-dao` | ORM 实体与 DAO |
| `nop-ai-service` | 业务逻辑 |
| `nop-ai-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-ai/model/nop-ai.orm.xml` |
| 引擎可靠性/超时 | `AIREL-001`（见 `../04-reference/source-anchors.md`）：`nop-ai-agent` 的 `DefaultAgentEngine` |

## Agent 引擎可靠性配置（nop-ai-agent）

`DefaultAgentEngine` 通过 setter 暴露可靠性/超时配置，保证 agent 会话、worker 线程、takeover lock 不被永久阻塞，且并发 agent 不互相饿死。默认值保证开箱即用（均为正数）：

| 配置项 | 默认值 | 语义 |
|--------|--------|------|
| `agentExecutor` | 专用 cached 守护线程池（线程名 `nop-ai-agent-exec-*`） | 三个入口点（`execute`/`resumeSession`/`restoreSession`）的 `supplyAsync` executor，替代 `ForkJoinPool.commonPool()`（默认仅 3-7 线程，多并发 agent 易互相饿死）。可通过 `setAgentExecutor` 覆盖（建议用 cached/virtual-thread 池，固定大小池在 ReAct LLM 超时回派到同一池时有自死锁风险） |
| `callAgentTimeoutMs` | `60000`（60s） | call-agent 子 agent 执行的 wall-clock 超时。超时后调用 `engine.cancelSession(childSessionId, forced=true)` 取消子 agent，释放 LLM/DB 资源（非僵尸执行）。必须为正数 |
| `llmTimeoutMs` | `120000`（120s） | ReAct 主循环单次 LLM 调用的 wall-clock 超时（经 `callChatWithTimeout` 用可中断的 `f.get(timeout)` 包裹）。`<= 0` 禁用（向后兼容逃生舱） |
| `toolTimeoutMs` | `300000`（300s） | ReAct dispatch fanout 中单次工具调用的 per-tool `.orTimeout`。超时转为 LLM 可见的工具错误响应。`<= 0` 禁用（向后兼容逃生舱） |

超时发生时执行显式失败（`AgentExecStatus.failed` 或工具错误响应），不静默跳过。接线锚点见 `AIREL-001`。

## 相关文档

- `../reusable-modules-overview.md`
