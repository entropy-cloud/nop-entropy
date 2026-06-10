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

## 相关文档

- `../reusable-modules-overview.md`
