# eliavamar/repositories-wiki 项目深度分析

> Status: open
> Date: 2026-09-23
> Scope: eliavamar/repositories-wiki 的架构设计、六步管线、三层 LLM 调度、Tree-sitter 集成、MCP 服务及 Agent-Skills 机制全面调研
> Conclusion: open
> Superseded By: —

## Context

Repositories Wiki 是一个基于 TypeScript 的 Turborepo 单体仓库（monorepo）项目，提供 LLM 驱动的代码库 Wiki 自动生成能力。项目受 Andrej Karpathy 的 "LLM Wiki" 模式启发——LLM 应该构建和维护持久知识库，而不是每次查询都从零开始重新发现信息。Repositories Wiki 将这一理念应用于源代码：解析一次、编译为结构化 Wiki，让每个未来的开发会话都带着完整的架构上下文启动。本分析旨在深入调研其三层 LLM 调度、Tree-sitter 结构提取和增量更新机制，为 Nop 平台的文档自动化能力建设提供参考。

---

## Analysis

### 1. 项目概览与架构

**eliavamar/repositories-wiki**（GitHub: `eliavamar/repositories-wiki`）是一个基于 TypeScript/Node.js 构建的 Turborepo 单体仓库，当前已获 20 stars、7 forks。项目核心定位是为代码库自动生成结构化、互连的 Wiki 文档，使 AI 编码代理在每次会话中都能直接读取持久化的知识层，而无需重新探索代码库。

#### 1.1 单体仓库结构

| 目录 | 职责 |
|------|------|
| `packages/common/` | 共享类型和工具函数 |
| `packages/repository-wiki/` | 主 CLI 工具与库，包含管线、Agent、Tree-sitter 核心 |
| `packages/mcp/` | MCP 服务器，将生成的 Wiki 提供给 AI 编码工具 |
| `skills/update-wiki/` | Agent 技能，用于维护 Wiki 的持续更新 |
| `examples/` | 示例 Wiki 输出（Claude Agent SDK、LangChain、Pi Mono） |
| `scripts/` | 辅助脚本 |

#### 1.2 核心架构特征

- **Turborepo 单体仓库**：三个包（common、repository-wiki、mcp）通过 Turborepo 管理构建顺序
- **三层 LLM 调度**：Opus（Planner）→ Haiku（Explorer）→ Sonnet（Builder），每个层级使用最适合该任务的模型
- **Tree-sitter 结构提取**：对 TypeScript、TSX、JS、JSX、Python、Java、Go 等语言，在发送至 LLM 前提取结构性签名（类、函数、接口）
- **Agent-Skills 标准**：内置 `update-wiki` 技能，可安装到 Claude Code、Cursor、OpenCode 等工具中
- **MCP 协议支持**：提供 `read_wiki_index`、`read_wiki_pages`、`read_source_files` 三个工具
- **结构化输出解析**：LLM 返回的内容被限制在 `<content>...</content>` 标签内，便于程序化解析
- **并发页面生成**：支持多页面并发生成，带有超时重试机制

---

### 2. 处理管线（六步 Pipeline）

Repositories Wiki 的核心是 `WikiGeneratorPipeline`，一个六步顺序管线，每步由独立的 Step 类实现。

#### 2.1 管线总览

```
Step 1: SetupRepositoryStep → Step 2: InferFilesStep → Step 3: GenerateStructureStep
→ Step 4: GeneratePagesStep → Step 5: WriteToLocalStep → Step 6: PushToGitHubStep
```

#### 2.2 Step 1: SetupRepositoryStep

- 克隆或验证目标仓库
- 获取当前 commit ID（作为 Wiki 版本的锚点）
- 初始化工作目录和配置

#### 2.3 Step 2: InferFilesStep

- **技术栈**：Tree-sitter + LLM（Haiku 类模型）
- 对代码库中的所有文件进行结构分析
- 使用 Tree-sitter 提取 TypeScript、TSX、JS、JSX、Python、Java、Go 的结构性签名
- LLM（Haiku）对文件重要性进行评估和排序
- 输出：重要文件列表（最大 150 个）
- 排除测试文件、生成文件、资产文件
- 优先选择广泛导入的文件、入口文件和配置文件

#### 2.4 Step 3: GenerateStructureStep

- **技术栈**：LLM（Opus 类模型）
- 基于 Step 2 的文件列表，设计 Wiki 的整体结构
- 输出：`WikiStructureModel`，包含 sections 和 pages 的层次定义
- 每个页面关联一组源文件（每个页面至少 5 个文件）
- 不固定页面数量，由 LLM 根据代码库复杂度动态决定
- 包含可视化图表页面的设计

#### 2.5 Step 4: GeneratePagesStep

- **技术栈**：LLM（Sonnet 类模型）
- 基于 `WikiStructureModel`，逐个生成 Wiki 页面内容
- 支持并发生成（有并发限制），提高吞吐量
- 每个页面的生成使用 `generatePageContentPrompt()`，要求返回 `<content>...</content>` 标签内的结构化内容
- 包含超时重试机制：若响应被截断，使用简洁重提示（timeout retry prompts）重新生成
- 页面内容要求：Introduction + H2/H3 章节 + Mermaid 图表 + Source 引用

#### 2.6 Step 5: WriteToLocalStep

- 将生成的页面内容写入本地 `repository-wiki/` 目录
- 生成 `INDEX.md` 作为 Wiki 的入口导航文件
- 生成 `AGENTS.md`，在仓库根目录添加指令，告诉 AI 代理如何查阅 Wiki 以及何时更新

#### 2.7 Step 6: PushToGitHubStep（可选）

- 将生成的 Wiki 推送到 GitHub
- 可通过 `--push` 标志启用

---

### 3. 三层 LLM 调度体系

Repositories Wiki 的核心设计创新在于将 LLM 任务按角色拆分为三个层级，每个层级使用最适合该任务的模型。

| 角色 | 模型层级 | 具体模型示例 | 任务 | 成本特征 |
|------|----------|-------------|------|----------|
| **Planner** | Opus-class | `claude-opus-4-6` | 设计 Wiki 结构——章节、页面及页面间关联 | 最高成本，用于最复杂的推理任务 |
| **Explorer** | Haiku-class | `claude-haiku-4-5` | 快速、廉价的文件级分析——提取元数据和摘要 | 最低成本，用于大规模文件筛选 |
| **Builder** | Sonnet-class | `claude-sonnet-4-6` | 详细编写每个 Wiki 页面 | 中等成本，用于内容生成 |

**设计优势：**
- **成本优化**：不需要用最贵的模型做简单的文件筛选
- **质量保证**：架构设计由最强模型完成，内容生成由平衡模型完成
- **可配置性**：用户可通过 CLI 参数为每个层级选择不同的模型和 Provider
- **Provider 兼容**：支持 Anthropic、OpenAI、Azure OpenAI、Google GenAI、Bedrock、SAP AI Core 等

---

### 4. Tree-sitter 集成

#### 4.1 作用与原理

Tree-sitter 在 LLM 处理前对源代码进行结构分析，提取语法层面的签名信息（类、函数、接口、类型定义等）。这些结构化信息作为上下文传递给 LLM，使生成的 Wiki 页面更加精准，减少 LLM 的 token 消耗。

#### 4.2 支持语言

| 语言 | 扩展名 | Tree-sitter 支持 |
|------|--------|-----------------|
| TypeScript | `.ts` | ✅ |
| TSX | `.tsx` | ✅ |
| JavaScript | `.js`, `.jsx`, `.mjs`, `.cjs` | ✅ |
| Python | `.py`, `.pyw` | ✅ |
| Java | `.java` | ✅ |
| Go | `.go` | ✅ |

#### 4.3 扩展机制

用户可通过以下方式添加新的 Tree-sitter 语言支持：

1. 在 `packages/repository-wiki/src/tree-sitter/language-queries/` 实现语言查询
2. 将对应的 `.wasm` 语法文件放入 `packages/repository-wiki/assets/grammars/`
3. 参考现有语言查询的实现

#### 4.4 自定义 Grammar

项目支持自定义 Grammar `.wasm` 文件，允许为用户特定的编程语言或框架扩展结构提取能力。

---

### 5. 提取模板与 Prompt 工程

项目的 Prompt 工程通过 `packages/repository-wiki/src/pipeline/prompts.ts` 实现，包含四种核心模板。

#### 5.1 `generateWikiStructurePrompt()` — Planner Prompt

- 用于 Step 3，由 Opus 类模型执行
- 设计自定义结构，不固定页面数量
- 要求每个页面至少关联 5 个文件
- 包含可视化图表页面的设计要求
- 输入：文件列表和 Tree-sitter 提取的结构签名
- 输出：WikiStructureModel（sections/pages 层次定义）椿

#### 5.2 `inferImportantFilesPrompt()` — Explorer Prompt

- 用于 Step 2，由 Haiku 类模型执行
- 筛选最大 150 个重要文件
- 排除测试、生成、资产文件
- 优先选择广泛导入的文件、入口文件、配置文件
- 目标：以最低成本获得最优的文件重要性排序

#### 5.3 `generatePageContentPrompt()` — Builder Prompt

- 用于 Step 4，由 Sonnet 类模型执行
- 要求返回 **仅** `<content>...</content>` 标签内的内容
- 页面结构：Introduction + H2/H3 章节 + Mermaid 图表 + Source 引用
- Source 引用格式：`Sources: [filename.ext:start-end](path)`
- 超时重试使用简洁重提示，处理被截断的响应

#### 5.4 Timeout Retry Prompts

- 处理 LLM 响应超时被截断的情况
- 使用更简洁的提示重新请求缺失部分
- 避免因单次超时导致整个页面生成失败

---

### 6. MCP 服务器

`packages/mcp/` 提供一个独立的 MCP 服务器，将生成的 Wiki 以工具形式暴露给 AI 编码工具。

#### 6.1 三大工具

| 工具名 | 功能 |
|--------|------|
| `read_wiki_index` | 读取 Wiki 的 INDEX.md，获取整体结构和导航 |
| `read_wiki_pages` | 读取特定 Wiki 页面的详细内容 |
| `read_source_files` | 读取源文件内容，提供上下文验证 |

#### 6.2 多仓库支持

单个 MCP 服务器可同时服务多个仓库：

```json
{
  "REPOS_WIKI_MCP_CONFIG": "{\"repos\":[{\"path\":\"/path/to/project-a\"},{\"url\":\"https://github.com/owner/project-b\",\"token\":\"ghp_...\",\"branch\":\"main\"}]}"
}
```

#### 6.3 集成方式

支持 Claude Desktop、Cursor、OpenCode 等 AI 编码工具，通过 JSON 配置添加 MCP Server。

---

### 7. Agent-Skills 机制

`skills/update-wiki/` 目录包含一个符合 Agent-Skills 标准的维护技能。

#### 7.1 核心功能

- **自动检测**：当 AI 代理修改代码时，自动检测哪些文档页面受到影响
- **外科手术式更新**：只更新受影响的页面，而非全量重新生成
- **新增页面**：为新模块自动添加新的 Wiki 页面
- **删除页面**：为已删除的代码自动移除对应页面

#### 7.2 安装方式

```bash
# Claude Code
cp -r skills/update-wiki ~/.claude/skills/update-wiki

# OpenCode
cp -r skills/update-wiki ~/.config/opencode/skills/update-wiki

# Cursor
cp -r skills/update-wiki ~/.cursor/skills/update-wiki
```

安装后，生成的 `AGENTS.md` 已包含更新指令，技能则教授代理 *如何* 更新。

---

### 8. 支持的 LLM Providers

| Provider | 环境变量 | 文档 |
|----------|---------|------|
| Anthropic | `ANTHROPIC_API_KEY` | LangChain docs |
| OpenAI | `OPENAI_API_KEY` | LangChain docs |
| Azure OpenAI | `AZURE_OPENAI_API_KEY` | LangChain docs |
| Google GenAI | `GOOGLE_API_KEY` | LangChain docs |
| Bedrock | AWS credentials | LangChain docs |
| SAP AI Core | `AICORE_SERVICE_KEY` | SAP AI SDK docs |

---

### 9. 与 Nop 平台的关联分析

Repositories Wiki 的设计对 Nop 平台有以下借鉴意义：

- **三层 LLM 调度模式**：Planner/Explorer/Builder 的分层设计可映射到 Nop 的 AI 服务编排——架构规划用强模型，文件分析用弱模型，内容生成用平衡模型
- **Tree-sitter 结构提取**：在发送 LLM 前先提取代码结构签名，这一模式可直接应用于 Nop 的 ORM 模型分析和文档生成
- **Agent-Skills 增量更新**：`update-wiki` 技能的自维护机制与 Nop 的文档生命周期管理方向一致
- **MCP 工具暴露**：`read_wiki_index`、`read_wiki_pages`、`read_source_files` 三个工具的设计模式与 Nop 的 MCP 扩展可对照
- **结构化输出解析**：`<content>...</content>` 标签约束输出格式的做法，可应用于 Nop 的 AI 响应规范化
- **Turborepo 单体仓库**：packages/common、packages/repository-wiki、packages/mcp 的分层设计对 Nop 的模块组织有参考价值

---

## Conclusion

Repositories Wiki 是一个设计精巧的单体仓库 LLM 文档生成工具。其核心创新在于三层 LLM 调度（Opus/Haiku/Sonnet 分别承担规划、探索、构建角色）和 Tree-sitter 结构提取前置的工程策略。Agent-Skills 机制使 Wiki 具备自维护能力，MCP 服务器则将知识库以标准化工具形式暴露给 AI 编码工具。项目规模虽小（20 stars），但架构设计完整，尤其在成本优化和结构化输出方面提供了可复用的工程模式。

与 OpenDeepWiki 相比，Repositories Wiki 更侧重于 CLI 工具和开发者工作流集成（Agent-Skills），而非 SaaS 产品化。两者形成互补：前者提供完整的文档生成基础设施，后者提供产品化的知识管理平台。

## Open Questions

- [ ] Tree-sitter 自定义 Grammar .wasm 文件的具体实现规范
- [ ] `update-wiki` 技能的增量更新算法细节（如何检测变更影响范围）
- [ ] 并发页面生成的信号量控制和错误处理策略
- [ ] Timeout retry prompts 的具体重试逻辑和最大重试次数
- [ ] 与 Nop 平台的集成可行性分析——是否可作为 npm 包或独立服务接入

## References

- `https://github.com/eliavamar/repositories-wiki`
- `docs-for-ai/02-core-guides/service-layer.md`
- `docs-for-ai/02-core-guides/api-and-graphql.md`
- `ai-dev/analysis/deepwiki-survey/11-opendeepwiki.md`
- `https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f` (LLM Wiki 原始 inspiration)
