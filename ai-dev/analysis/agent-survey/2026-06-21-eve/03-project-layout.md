# 03 · agent 目录结构与概念词典

> 来源：`docs/reference/project-layout.md` + `docs/introduction.mdx` + 实际 fixture 解剖

## 完整目录结构

```text
my-agent/
├── package.json
├── tsconfig.json
├── AGENTS.md / CLAUDE.md          # AI 助手引导（@AGENTS.md include 语法）
├── evals/                          # 与 agent/ 平级：eval 文件根
│   └── evals.config.ts             # 每个根目录必需
└── agent/
    ├── agent.ts                    # defineAgent(...)，运行时配置
    ├── instructions.md             # always-on 系统提示（或 .ts / instructions/ 目录）
    ├── instrumentation.ts          # OTel exporter / span 配置（仅根，自动发现）
    ├── tools/                      # 类型化可执行集成（文件名 = 工具名）
    ├── skills/                     # 按需加载的 markdown 程序（SKILL.md 约定）
    ├── connections/                # 外部 MCP/OpenAPI 连接（每文件一个）
    ├── channels/                   # HTTP/消息入口（仅根；eve.ts 是默认 HTTP 通道）
    ├── hooks/                      # 生命周期/流事件订阅（module-backed）
    ├── sandbox.ts 或 sandbox/      # 唯一沙箱定义；sandbox/workspace/** 启动时镜像到 /workspace
    │   └── workspace/              # 启动时镜像进 /workspace，目录结构保留
    ├── schedules/                  # cron 任务（仅根；<name>.ts 或 <name>.md）
    ├── subagents/                  # 子代理目录，每个 <id>/ 是独立 agent 包（可嵌套）
    └── lib/                        # 共享 TypeScript helper（import-only，永不进 workspace）
```

## 各 slot 详解

### `agent.ts` —— agent 入口

- **必填**：`model`（字符串 model id 走 Vercel AI Gateway，或 `LanguageModel` 实例直连 provider）
- **可选**：`modelOptions`、`compaction`、`experimental.workflow.world`、`outputSchema`、`build.externalDependencies`
- **根 agent 可省略**（用默认）；**子代理必需且必须带 `description`**
- identity 编译期派生（包名 / app 根 basename），**没有 `name` 字段**

```ts
// packages/eve/src/public/definitions/agent.ts:35-39
export function defineAgent<T extends AgentDefinition>(definition: T): T {
  return definition;  // identity helper，保留字面量类型
}
```

### `instructions.md` —— always-on 系统提示

- 文件名固定 `instructions.md`，框架按约定加载
- 也可用 `instructions.ts` 或 `instructions/` 目录（build 时合成）
- 动态版用 `defineDynamic` 在 `session.started` / `turn.started` / `step.started` 事件里 resolve

### `tools/` —— 类型化工具

- 位置：`agent/tools/<name>.ts`，默认导出 `defineTool(...)`
- **文件名即工具名**（snake_case ASCII），无 `name` 字段
- 同名文件**覆盖内置工具**；`disableTool()` 关闭内置工具
- 内置工具 10 个：`ask_question` / `bash` / `glob` / `grep` / `read_file` / `write_file` / `todo` / `web_fetch` / `web_search` / `skill`

### `skills/` —— 按需加载技能

- 位置：`agent/skills/<name>.md`（flat）或 `<name>/SKILL.md`（Agent Skills package，含 `references/` / `assets/`）或 `<name>.ts`（`defineSkill`）
- 必填：YAML frontmatter `description`（模型可见的「何时调用」提示）
- **默认不进 prompt**——模型通过内置 `skill` 工具按需 lazy-load（「渐进式披露」）
- 运行时镜像到 `/workspace/skills/...`

### `connections/` —— 外部连接

- 位置：`agent/connections/<name>.ts`（file-form）或 `<name>/connection.ts`（folder-form）
- 两种协议：`defineMcpClientConnection(...)` / `defineOpenAPIConnection(...)`
- 模型看到的是自动生成的 `connection__search`（搜 operation）和 `connection__<conn>__<opId>`（调 operation）工具
- **URL / 凭证从不暴露给模型**
- 支持交互式 OAuth 2.1 + PKCE（`defineInteractiveAuthorization`）

### `channels/` —— 入口适配器

- 位置：`agent/channels/<name>.ts`（递归子目录支持），默认导出 `defineChannel(...)` 或 `disableRoute()`
- **仅根 agent**（子代理不能有 channel）
- `eve.ts` 是**默认 HTTP 通道**（无文件也存在），路由 `/eve/v1/session`、`*/session/:sessionId`、`*/session/:sessionId/stream`
- 内置 channel 工厂：slack / github / discord / twilio / telegram / teams / linear
- channel 拥有 `continuationToken`（"下一条消息"恢复句柄）

### `hooks/` —— 事件流订阅

- 位置：`agent/hooks/<name>.ts`，默认导出 `defineHook(...)`
- 订阅生命周期/流事件：`turn.started`、`message.completed`、`action.result`、`session.*` 等
- 支持嵌套目录
- throw 会让事件流转 `turn.failed`

### `sandbox.ts` / `sandbox/` —— 沙箱定义

- 每个 agent **唯一**一个沙箱定义
- 顶层 `sandbox.ts` 用于「仅定义」；文件夹形式才能配 `sandbox/workspace/**`
- `sandbox/workspace/**` 启动时镜像进 `/workspace`
- 后端可切：`vercel()` / `docker()` / `microsandbox()` / `just-bash()` / `defaultBackend()` / 自定义

### `schedules/` —— cron 任务

- 位置：`agent/schedules/<name>.ts`（`defineSchedule`）或 `<name>.md`（frontmatter `cron:` + body prompt）
- **仅根 agent**
- `defineSchedule` 两种形态：`{ cron, markdown }`（prompt 模式）或 `{ cron, run }`（handler 模式）
- 在 Vercel 上变成真实 Cron Job；本地/dev 可通过 `POST /eve/v1/dev/schedules/:scheduleId` 触发

### `subagents/` —— 子代理

- 位置：`agent/subagents/<id>/agent.ts`，每个 `<id>/` 是独立 agent 包（有自己的完整目录树）
- **可嵌套**（subagent 可以有自己的 subagents）
- 远程 agent：`agent/subagents/<id>.ts` 用 `defineRemoteAgent({ url, description, outputSchema? })`，编译期 lower 成 subagent tool
- 子代理对父 agent 来说**就是一个 tool**（固定 input schema `{ message, outputSchema? }`）

### `lib/` —— 共享代码

- 共享 TypeScript helper，**仅 import**，永不进 sandbox workspace
- 配合 `package.json` 的 `"imports": { "#*": "./agent/*" }` 子路径映射使用

### `instrumentation.ts` —— OTel 配置（仅根）

- OTel exporter 与 AI SDK span 设置
- agent 代码执行前先跑
- 根独占

---

## 「运行时只看这两类源」

`project-layout.md:65-72` 明确：整个 `agent/` 树**不会全部塞进 sandbox workspace**，只有：
- `skills/` 文件 → `/workspace/skills/...`
- `agent/sandbox/workspace/**` → `/workspace/...`

`lib/` **永不进 workspace**。这是信任边界的体现：sandbox 是 isolated 环境（仅 `/workspace`），app runtime 是 trusted 环境（有 `process.env`）。

---

## 信任边界模型（双上下文）

| | app runtime（trusted） | sandbox（isolated） |
|---|---|---|
| 谁在这里跑 | tools 的 `execute`、hooks、schedules 的 `run`、channel handlers | 模型生成的代码（`bash` 工具、code-mode） |
| 能访问 | `process.env`、网络、文件系统（agent 进程权限内） | 仅 `/workspace` + 显式网络策略 |
| 状态来源 | durable session state（`defineState`） | `/workspace` 文件 + skills 镜像 |

详见 `docs/concepts/security-model.md`（fail-closed 原则）。

---

## 概念词典

| 概念 | 含义 | 位置 |
|---|---|---|
| **filesystem-first** | 文件位置决定角色，路径决定身份，无注册表 | `introduction.mdx:44-67` |
| **agent** | durable 的文件驱动对话型 AI 应用；最小 = `instructions.md` + `agent.ts` | `introduction.mdx:6,16-42` |
| **instructions** | always-on 系统提示；每次模型调用前注入 | `instructions.mdx:6-37` |
| **tool** | 模型可调用的类型化函数；跑在 app runtime；可用 `needsApproval` 闸住 | `tools/overview.mdx:7-45` |
| **skill** | 模型按需 lazy-load 的 markdown 程序；「渐进式披露」 | `skills.mdx:6-57` |
| **connection** | 外部 MCP/OpenAPI 连接器；模型只看到派生工具，看不到 URL/凭证 | `connections.mdx:6-115` |
| **channel** | 入口适配器；归一化平台输入；拥有 `continuationToken` | `channels/overview.mdx:6-14` |
| **schedule** | cron 定时任务；prompt 或 handler 模式；仅根 | `schedules.mdx:6-83` |
| **subagent** | 子代理；内置 `agent` 工具或 `subagents/<id>/` 声明式 | `subagents.mdx:6-73` |
| **durable** | session 是 durable 对话；自动 checkpoint；崩溃/重新部署可恢复 | `execution-model-and-durability.md:6-45` |
| **eval** | `defineEval` 写的打分检查；走真实 HTTP 表面；`eve eval` 运行 | `evals/overview.mdx:6-104` |
| **continuationToken** | channel 拥有的「下一轮」恢复句柄 | `sessions-runs-and-streaming.md:9-16` |
| **sessionId / runId** | runtime 拥有的「流式 + 观察」句柄（= workflow runId） | 同上 |
| **turn** | 一条用户消息及其触发的全部工作（模型调用 + 工具调用 + 推理） | `execution-model-and-durability.md` |
| **step** | turn 内的 durable checkpoint（一次模型调用 + 其工具调用） | 同上 |
| **park** | turn durable 挂起（HITL/OAuth/长跑子代理），不占计算，等到输入到来再恢复 | 同上 |
| **harness** | 一个 "step" = 一次 model call + 工具批量执行单元 | README + concepts |
| **hITL** | Human-in-the-loop；`needsApproval` 工具或 `ask_question` | `tools/human-in-the-loop.md` |

---

## 身份派生规则汇总

| slot | 身份来源 | 例子 |
|---|---|---|
| agent | `manifest.agentId`（包名 or app 根 basename） | `weather-agent` |
| tool | `agent/tools/<slug>.ts` 文件名（去扩展名） | `get_weather` |
| skill | `agent/skills/<name>.md` 文件名（去扩展名） | `get-weather` |
| channel | `agent/channels/<slug>.ts` 文件名 | `slack` |
| connection | `agent/connections/<slug>.ts` 文件名 | `tfl` |
| schedule | `agent/schedules/<slug>.ts` 文件名 | `heartbeat` |
| subagent | `agent/subagents/<id>/` 目录名 | `researcher` |
| hook | `agent/hooks/<slug>.ts` 文件名 | `tool-result-narrowing` |
| eval | `evals/<path>.eval.ts` 文件路径（去 `.eval.ts`） | `weather/brooklyn-forecast` |

**所有身份都是 path-derived，写 `name`/`id` 字段会被编译期拒绝。**

---

## 与 Nop AI agent 的结构对比（预告）

> 基于 `ai-dev/design/nop-ai-agent/` 设计文档的准确对比。

| | Nop AI agent（DSL-First） | eve（Filesystem-First） |
|---|---|---|
| 真相载体 | **DSL**（`agent.xdef`/`tool.xdef` xdef schema 定义语义） | 文件系统（目录结构即配置） |
| 统一抽象 | **VFS**：DSL 文件 + 工作文件 + `IToolFileSystem` 三者统一，后端可替换 | 物理目录 + 编译产物 `.eve/` |
| 身份来源 | DSL `name` 属性，经 VFS 路径 `/{name}.agent.xml` 加载 | 纯文件路径推导，禁写 `name` |
| 定制 | **Delta 可逆覆盖**（可叠加、可撤销） | 同名文件覆盖（不可逆） |
| 工具文件系统 | `IToolFileSystem`（统一接口 + `isPathAllowed` 白名单 + 底层映射 VFS） | sandbox `/workspace` + app runtime |
| 状态 | Event Log（append-only）+ Session Tree（fork）+ 分布式 actor | durable workflow（turn/step checkpoint） |

详见 `10-insights.md`。
