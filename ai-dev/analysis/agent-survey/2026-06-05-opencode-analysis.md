# OpenCode 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/opencode — anomalyco 开源 AI Coding Agent（Agent Team 设计重点）
> Conclusion:

## Context

- OpenCode 是 anomalyco 开源的 AI coding agent，CLI/TUI 为主，支持 desktop/web/console 多前端
- 核心差异化：Effect v4 函数式架构 + 双 LLM 运行时 + 层次化多 Agent 团队 + 权限继承模型
- 调研目的：深入理解 Agent Team 设计模式、Agent 循环架构、工具/权限/插件体系，为 Nop Agent 生态提供参考

## Analysis

### 项目定位

- **组织**: anomalyco
- **许可**: Apache-2.0
- **语言**: TypeScript (strict, ESM, Bun runtime)
- **LOC**: ~430K 行 TS/JS/TSX/JSX (25 packages, ~5,187 文件；含所有文件类型 ~944K 行)
- **Commit**: fff36b70b (2026-06-05)
- **GitHub**: https://github.com/anomalyco/opencode
- **核心框架**: Effect v4 beta (Layer/Service/Schema/Stream)
- **LLM SDK**: 双运行时 — Vercel AI SDK v6 (默认) + `@opencode-ai/llm` (实验性原生运行时)
- **定位**: 全栈 AI Coding Agent——不是框架，是产品级 Agent runtime

### 顶层架构

```
opencode/
├── packages/
│   ├── opencode/              # 主 CLI/TUI agent (src ~107K 行 / 含测试 ~235K 行, 807 文件)
│   │   └── src/
│   │       ├── agent/         # Agent 定义 + 权限 + 子 agent 派生
│   │       ├── session/       # V1 Session 核心 (prompt, processor, run-state)
│   │       ├── tool/          # 16 内置工具 + 注册表
│   │       ├── provider/      # 21 Provider SDK 适配
│   │       ├── mcp/           # MCP 客户端 (stdio + HTTP/SSE + OAuth)
│   │       ├── plugin/        # 插件系统 (内建 + 外部 + 文件系统)
│   │       ├── config/        # 多源配置加载
│   │       ├── permission/    # 权限评估引擎
│   │       ├── skill/         # Skill 加载
│   │       └── session/llm.ts # LLM 流式调度
│   ├── core/                  # 核心库 (~60K 行) — DB schema, V1/V2 types, Effect services
│   ├── llm/                   # 原生 LLM 运行时 (~19K 行, 99 文件, 10-11 Provider facades)
│   ├── app/                   # React 前端 (~85K 行)
│   ├── ui/                    # 共享 UI 组件 (~37K 行)
│   ├── console/               # Console web app (~105K 行)
│   ├── sdk/                   # 生成 SDK (~41K 行)
│   ├── server/                # HTTP 服务器 (~1.8K 行)
│   ├── plugin/                # 插件类型定义 (~1.7K 行)
│   ├── desktop/               # Desktop app (Electron/Tauri, ~7.3K 行)
│   ├── web/                   # Web app (~24K 行)
│   ├── stats/                 # 统计/分析 (~8K 行)
│   ├── enterprise/            # 企业特性 (~95K 行)
│   └── effect-drizzle-sqlite/ # Effect + Drizzle SQLite 封装 (~3.5K 行)
└── specs/v2/                  # V2 Session 规范草案
```

### Agent Team 设计（核心重点）

#### 内置 Agent 体系

7 个内置 Agent，分三类角色：

| Agent | 模式 | 角色描述 | 关键权限特征 |
|-------|------|----------|-------------|
| **build** | `primary` | 默认执行 agent | 全工具访问，`question: allow`，`plan_enter: allow` |
| **plan** | `primary` | 规划模式，禁止编辑 | `edit: { "*": "deny" }`，仅允许写 `.opencode/plans/*.md` 和 `$DATA/plans/`，`plan_exit: allow` |
| **general** | `subagent` | 通用多步子 agent | 全工具访问（除 `todowrite: deny`） |
| **explore** | `subagent` | 快速代码搜索专家 | 只读：`grep`, `glob`, `list`, `bash`, `webfetch`, `websearch`, `read` |
| **compaction** | `primary` (hidden) | 上下文压缩 | `*": "deny"`（无工具），专用压缩 prompt |
| **title** | `primary` (hidden) | 会话标题生成 | 无工具，`temperature: 0.5` |
| **summary** | `primary` (hidden) | 会话摘要生成 | 无工具 |

**Agent 模式定义**：
- `primary` — 用户可选择为顶层模式（如 build/plan 切换）
- `subagent` — 仅通过 `task` 工具由其他 agent 调用
- `all` — 用户自定义 agent 默认值（可作 primary 或 subagent）

#### 层次化委托模型

协作模型是**通过 `task` 工具的层次化委托**，而非中央编排器：

```
User
 └─ build agent (primary)
     ├─ task(explore, "search for X")  → explore subagent session
     ├─ task(general, "refactor Y")    → general subagent session
     └─ task(general, "write tests")   → general subagent session (parallel)
```

**关键机制**：
1. **无中央编排器** — 当前活跃 agent 调用 `task` 工具生成子 agent session
2. **`task` 工具** (`packages/opencode/src/tool/task.ts`, 338 行) 创建**子 session**，独立 agent/model/权限集
3. 子 agent 在**独立 session** 运行（设 `parentID`），共享同一数据库
4. 父 agent 等待子 agent 完成（前台）或可后台启动（实验性）
5. `task` 工具动态描述会列出可用 subagent，LLM 自行决定委派

#### 子 Agent 权限派生

`packages/opencode/src/agent/subagent-permissions.ts` (35 行)：
- 继承父 **agent** 的编辑类 deny 规则（plan 模式的编辑限制传播到子 agent）
- 继承父 **session** 的 deny 规则和 `external_directory` 规则
- 默认 deny `todowrite` 和 `task`（禁止递归子 agent，除非显式允许）

#### 用户自定义 Agent

三种机制：

**1. Markdown 文件** (`.opencode/agent/` 或 `.opencode/agents/`)

```markdown
---
mode: primary
hidden: true
model: opencode/claude-haiku-4-5
color: "#E67E22"
permission:
  "*": deny
  "some-tool": allow
---
You are a specialized agent for X...
```

**2. JSON 配置** (`opencode.json` / `opencode.jsonc`)

```json
{
  "agent": {
    "build": { "model": "anthropic/claude-sonnet-4-20250514" },
    "my-custom": {
      "mode": "subagent",
      "description": "Custom agent",
      "prompt": "...",
      "permission": { "edit": "deny" }
    }
  }
}
```

**3. LLM 动态生成** — `Agent.generate()` 使用 `generateObject` + `GeneratedAgent` schema 从自然语言描述创建 agent，返回 `{ identifier, whenToUse, systemPrompt }`

#### Agent 配置 Schema

每个 Agent 可配置：`model`, `variant`, `prompt`, `mode`, `hidden`, `temperature`, `top_p`, `steps` (最大迭代), `permission`, `disable`, `description`, `color`

### Agent 循环

核心循环在 `packages/opencode/src/session/prompt.ts` (1755 行)，`runLoop` 函数 (L1217-1473)：

```
User sends prompt
  → createUserMessage() 创建用户消息 + parts
  → loop() → state.ensureRunning() → runLoop()

runLoop: while (true):
  1. 从 DB 加载消息（过滤已压缩）
  2. 提取 lastUser, lastAssistant, tasks
  3. 检查退出条件（assistant 完成，无待处理 tool calls）
  4. 递增 step 计数器
  5. 检查 subtask/compaction 任务
  6. 检查是否需要压缩（overflow 检测）
  7. 从 lastUser.agent 解析 agent
  8. 应用 reminders
  9. 创建 assistant message
  10. 创建 processor handle
  11. 解析工具 (SessionTools.resolve)
  12. 流式 LLM 响应 (processor.process)
  13. 处理结果: "break" | "continue"
```

**关键设计**：每轮循环**恰好一次** `llm.stream()` 调用——AGENTS.md 明确要求 "Preserve one explicit `llm.stream(request)` call per provider turn"

### 工具系统

#### 16 内置工具

| 工具 | 文件 | 用途 |
|------|------|------|
| `bash` | `shell.ts` | Shell 命令执行 |
| `read` | `read.ts` | 文件/目录读取 |
| `edit` | `edit.ts` | 文件编辑 (search/replace) |
| `write` | `write.ts` | 文件写入 |
| `apply_patch` | `apply_patch.ts` | GPT 专用 patch 应用 |
| `glob` | `glob.ts` | 文件模式匹配 |
| `grep` | `grep.ts` | 正则内容搜索 |
| `task` | `task.ts` (338 行) | 子 agent 生成 |
| `webfetch` | `webfetch.ts` | URL 内容获取 |
| `websearch` | `websearch.ts` | Web 搜索 (Exa/Parallel) |
| `todo` (`todowrite`) | `todo.ts` | Todo 列表管理 |
| `skill` | `skill.ts` | Skill 加载 |
| `question` | `question.ts` | 用户交互（问题/选项） |
| `plan_exit` | `plan.ts` | plan→build 模式切换 |
| `lsp` | `lsp.ts` | LSP 集成 (实验性) |
| `invalid` | `invalid.ts` | 处理未知 tool calls |

#### 工具定义模式

```typescript
export const MyTool = Tool.define("tool_name", Effect.gen(function* () {
  return {
    description: "...",
    parameters: Schema.Struct({ ... }),
    execute: (args, ctx) => Effect.gen(function* () {
      return { title, metadata, output }
    }),
  }
}))
```

`Tool.Context` 提供：`sessionID`, `messageID`, `agent`, `abort` signal, `messages`, `metadata()`, `ask()` (权限请求)

#### 工具解析流程

`ToolRegistry.tools()` (registry.ts:313-358):
1. 获取所有 内置 + 自定义 + MCP 工具
2. 按 agent 权限过滤 (`Permission.evaluate()`)
3. 按模型过滤（GPT 模型用 `apply_patch` 替代 `edit`/`write`）
4. 附加动态描述：`task` 工具描述列出可用 subagent；`skill` 工具列出可用 skills

### LLM Provider

#### 双运行时架构

| 运行时 | 实现 | 触发方式 | 特点 |
|--------|------|----------|------|
| **AI SDK** (默认) | Vercel AI SDK v6 `streamText()` | 默认 | 成熟稳定，内置 tool 执行 |
| **Native** | `@opencode-ai/llm` | `OPENCODE_EXPERIMENTAL_NATIVE_LLM` | Schema-first，provider-neutral 事件流，缓存控制 |

#### Provider 支持

通过 `packages/opencode/src/provider/provider.ts` (1993 行) 集成 21 个 Provider SDK，`@opencode-ai/llm` 提供 10-11 个原生 Provider facades：

- OpenAI (含 Codex/WebSocket)
- Anthropic (Claude)
- Google Gemini / Google Vertex
- Amazon Bedrock (Converse API)
- Azure OpenAI
- GitHub Copilot
- GitLab AI
- Cloudflare (Workers AI + AI Gateway)
- DigitalOcean
- xAI (Grok)
- OpenRouter
- Poe
- Alibaba (通义)
- Mistral
- Groq
- Cerebras
- Cohere
- DeepInfra
- TogetherAI
- Perplexity
- Vercel AI Gateway
- 任意 OpenAI-compatible endpoint

#### 系统提示词选择

按 provider 分发不同提示词模板：Claude→`anthropic.txt`, GPT-4/o1/o3→`beast.txt`, GPT(other)→`gpt.txt`, Codex→`codex.txt`, Gemini→`gemini.txt`, Trinity→`trinity.txt`, Kimi→`kimi.txt`, Default→`default.txt`

### Session 管理

#### V1 Session (当前活跃)

`packages/opencode/src/session/session.ts` (1116 行):
- SQLite 持久化 (Drizzle ORM)
- Session 字段：`id`, `slug`, `projectID`, `workspaceID`, `parentID`, `agent`, `model`, `version`, `cost`, `tokens`, `permission`, `time`, `summary`, `revert`
- 子 session (subagent) 通过 `parentID` 关联
- Session forking（消息克隆）

#### V2 Session (规范草案)

`specs/v2/session.md` (171 行):
- **Durable prompt admission**: `session_input` inbox，prompt 在安全边界提升为可见历史
- **Context Epochs**: 不可变基线系统上下文，provider-turn 边界懒协调
- **SessionExecution**: 进程全局，通过 `SessionStore` 发现放置
- **SessionRunner**: Location-scoped，处理模型解析、工具、权限
- **SessionRunCoordinator**: 序列化 per-session drain，允许并发 sessions
- 当前部分实现，`experimentalEventSystem` flag 控制

### 权限系统

`packages/opencode/src/permission/index.ts` (230 行):

#### 评估模型

权限是 `{ permission, pattern, action }` 元组的扁平规则集：
- `permission`: 工具类别 (`"*"`, `"edit"`, `"bash"`, `"task"`, `"external_directory"` 等)
- `pattern`: glob 匹配 (`"*"`, `"*.env"`, 具体路径)
- `action`: `"allow"` | `"deny"` | `"ask"` (默认)

**评估**：`findLast` 匹配规则胜出（后规则覆盖前规则）

#### 运行时权限流

1. 工具执行调用 `ctx.ask()` 发起权限请求
2. `Permission.ask()` 评估合并后规则集
3. `deny` → `PermissionV1.DeniedError`
4. `allow` → 静默通过
5. `ask` → 发布 `permission.asked` 事件，通过 `Deferred` 等待用户回复
6. 用户回复：`"once"` | `"always"` (加入批准列表) | `"reject"`

### 插件系统

`packages/opencode/src/plugin/index.ts` (320 行):

#### 插件类型

1. **内建插件** (硬编码): CodexAuth, CopilotAuth, GitlabAuth, PoeAuth, CloudflareWorkers, CloudflareAIGateway, AzureAuth, DigitalOceanAuth, XaiAuth
2. **外部插件** (npm/local): 通过 `PluginLoader.loadExternal()` 加载
3. **用户工具** (`.opencode/tool/` 或 `.opencode/tools/`): 文件系统 `.ts`/`.js` 文件

#### Hook 系统

插件实现 `Hooks` 接口（共 21 个生命周期钩子），核心钩子：

| Hook | 用途 |
|------|------|
| `experimental.chat.system.transform` | 修改系统提示词 |
| `experimental.chat.messages.transform` | 修改消息历史 |
| `chat.message` | 新消息通知 |
| `chat.params` | 修改 LLM 参数 |
| `chat.headers` | 修改请求头 |
| `tool.definition` | 修改工具定义 |
| `tool.execute.before` | 工具执行前拦截 |
| `tool.execute.after` | 工具执行后处理 |
| `shell.env` | 注入 shell 环境变量 |
| `permission.ask` | 权限拦截 |
| `command.execute.before` | 命令执行前拦截 |
| `event` | 接收所有事件 |
| `config` | 配置更新通知 |
| `dispose` | 清理 |

另有 `auth`, `provider`, `experimental.provider.small_model`, `experimental.session.compacting`, `experimental.compaction.autocontinue`, `experimental.text.complete`, `tool` 等实验性/内部钩子。

### MCP 集成

`packages/opencode/src/mcp/index.ts` (982 行):
- 支持 `local` (stdio) 和 `remote` (HTTP/SSE) 传输
- OAuth 流程（浏览器认证）
- 动态工具发现 (`tools/list_changed` 通知)
- MCP 工具转换为 AI SDK `dynamicTool()` 格式

### Effect 架构特点

OpenCode 大量使用 Effect v4 beta 的函数式模式：

- **Layer**: 依赖注入（所有 Service 通过 Layer 组装）
- **Service**: 模块化服务定义
- **Schema**: 运行时类型验证
- **Stream**: 流式处理（LLM 事件流）
- **Effect.gen**: generator-based 的"do notation"

这种选择使核心逻辑高度可组合、可测试，但学习曲线陡峭。

### 测试

- **框架**: Bun test (原生)
- **文件**: 262 个测试文件 (`packages/opencode/test/`)
- **原则**: 尽量避免 mock，测试实际实现
- **分类**: tool (18), session (18), server (47), skill (2), snapshot (1), storage (2), util (13)

## Conclusion

### 设计模式总结

OpenCode 的 Agent Team 设计是**工具驱动的层次委托**模型，核心特征：

1. **Agent 即配置** — Agent 是 Schema 驱动的数据结构（不是类继承），可 JSON/Markdown/LLM 动态生成
2. **权限即类型** — 权限规则嵌入 Agent 定义，子 agent 自动继承+限制
3. **task 工具即编排** — 没有独立编排器，LLM 通过 `task` 工具自行决定委派
4. **Session 即隔离** — 子 agent 运行在独立 session，有自己的 DB 记录和权限上下文
5. **Effect 即骨架** — 整个运行时基于 Effect 的 Layer/Service/Stream 函数式架构

### 对 Nop 的借鉴价值

| 借鉴点 | 优先级 | 说明 |
|--------|--------|------|
| Agent Schema 即配置 (JSON/Markdown/LLM 生成) | **P0** | Nop 的 XDSL 天然适合做 Agent 定义语言 |
| 权限继承 + 子 agent 派生 | **P0** | `subagent-permissions.ts` 仅 35 行，极简但完整的权限派生模型 |
| task 工具委托模型 | **P0** | 无需中央编排器，LLM 自行委派——与 Nop 的 biz action 模型天然契合 |
| 双 LLM 运行时 + Provider 抽象 | **P1** | AI SDK v6 + 原生运行时的双轨设计值得参考 |
| Hook 式插件系统 | **P1** | `tool.execute.before/after` 模式简单实用 |
| V2 Session 的 Durable Admission | **P1** | prompt inbox → 安全边界提升的设计思路 |
| Effect 函数式架构 | **P2** | 对 Java/Nop 不直接适用，但 Service Layer 模式可借鉴 |

### 与已分析项目的对比

| 维度 | OpenCode | Reasonix | PilotDeck | VoltAgent |
|------|----------|----------|-----------|-----------|
| **Agent 团队** | 7 内置 + 用户自定义 + LLM 动态生成 | 单 agent | 23 通道 but 单核心 | Workflow DSL 16 步 |
| **编排模型** | 层次委托 (task tool) | N/A | Smart Routing | DAG 工作流 |
| **权限模型** | 扁平规则集 + 继承派生 | N/A | N/A | Guardrail Pipeline |
| **容错** | 依赖 LLM 重试 + Session fork | Cache-First + Tool-Call Repair + Event Sourcing | 熔断器 + 三级压缩 | N/A |
| **LOC** | ~430K (TS/JS) | ~94K | ~142K | ~48K |
| **语言** | TypeScript/Effect | TypeScript | Python | TypeScript |

### 不可借鉴的点

- **Effect 框架**: Java 生态无等价物，Nop 有自己的 IoC/XLang 体系
- **Bun runtime**: Java 生态用 JVM，不可迁移
- **AI SDK v6**: Java 生态有自己的 LLM 抽象（Spring AI 等）
- **SQLite + Drizzle**: Nop 已有自己的 ORM 层

## Open Questions

- [ ] V2 Session 的 Durable Admission 是否已在 trunk 上部分实现？实现到什么程度？
- [ ] `task` 工具的后台模式（非阻塞）稳定性如何？是否有超时/错误传播机制？
- [ ] LLM 动态生成 Agent (`Agent.generate()`) 在生产中的实际使用效果如何？生成的 agent 质量如何保证？
- [ ] OpenCode 的 Effect 架构是否影响了贡献者门槛？社区活跃度如何？
- [ ] 权限的 `ask` 模式在 headless/server 模式下如何工作？

## References

- `~/ai/opencode/` — 项目根目录
- `~/ai/opencode/packages/opencode/src/agent/agent.ts` — Agent 定义核心 (433 行)
- `~/ai/opencode/packages/opencode/src/agent/subagent-permissions.ts` — 子 agent 权限派生 (35 行)
- `~/ai/opencode/packages/opencode/src/tool/task.ts` — task 工具 (338 行)
- `~/ai/opencode/packages/opencode/src/session/prompt.ts` — Agent 循环 (1755 行)
- `~/ai/opencode/packages/opencode/src/session/processor.ts` — 流式处理器 (1063 行)
- `~/ai/opencode/packages/opencode/src/permission/index.ts` — 权限引擎 (230 行)
- `~/ai/opencode/packages/opencode/src/plugin/index.ts` — 插件系统 (320 行)
- `~/ai/opencode/packages/opencode/src/tool/registry.ts` — 工具注册表
- `~/ai/opencode/packages/opencode/src/provider/provider.ts` — Provider 适配 (1993 行)
- `~/ai/opencode/packages/llm/` — 原生 LLM 运行时 (~19K 行)
- `~/ai/opencode/specs/v2/session.md` — V2 Session 规范草案 (171 行)
- `~/ai/opencode/AGENTS.md` — 开发指南
- `ai-dev/analysis/agent-survey/2026-06-05-agent-design-key-elements.md` — 综合文档
