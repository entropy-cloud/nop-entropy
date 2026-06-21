# 04 · 核心 API 清单（`define*` 助手）

> 所有 `define*` 都是 **identity helper**——返回原对象、保留字面量类型、加 brand/sentinel、做最小校验。真正的 normalize 在 compile 阶段。

## 入口

`src/index.ts:1` 仅一行：
```ts
export * from "#public/index.js";
```

`src/public/index.ts:1-19` 只导出两类核心：
```ts
export { defineAgent, type AgentDefinition, ... } from "#public/definitions/agent.js";
export { defineRemoteAgent, type RemoteAgentDefinition, ... } from "#public/definitions/remote-agent.js";
```

其他 `define*` 通过对应子路径（`eve/tools`、`eve/skills`、`eve/channels`...）导出。

---

## 核心 `define*` 清单

### `defineAgent(definition)` —— `eve`

```ts
// packages/eve/src/public/definitions/agent.ts:35-39
export function defineAgent<T extends AgentDefinition>(definition: T): T
```

- **identity helper**：接受 `AgentDefinition`（`model` / `name` / `build` / `compaction` / `experimental` / `outputSchema`），返回原对象
- **identity 由编译期派生**（`manifest.agentId`），**不允许写 `name`**
- `model`：字符串 model id（走 Vercel AI Gateway）或 `LanguageModel` 实例（直连 provider）
- 子代理**必须**带 `description`（父 agent 用它决定何时委派）

最小例子（`apps/fixtures/weather-agent/agent/agent.ts`）：
```ts
import { defineAgent } from "eve";
export default defineAgent({
  model: "openai/gpt-5.5",
  modelOptions: { providerOptions: { openai: { reasoningEffort: "high" } } },
});
```

### `defineRemoteAgent(input)` —— `eve`

```ts
// src/public/definitions/remote-agent.ts:53-59
```
- 定义**远程 eve 部署**作为父 agent 的 subagent tool
- 编译期 lower 成 subagent
- `path` 默认 `/eve/v1/session`

### `defineTool(definition)` —— `eve/tools`

```ts
// packages/eve/src/public/definitions/tool.ts:140-224（4 个重载）
export function defineTool<TInput, TOutput>(definition): ToolDefinition<TInput, TOutput>
```

- 4 个重载：inputSchema / outputSchema 用 StandardJSONSchemaV1 推断
- 打 `TOOL_BRAND`，规范化 `auth`
- `needsApproval`：`never()` / `once()` / `always()` / `(ctx) => boolean`
- `execute(input, ctx)` 的 `ctx`（`ToolContext`，`tool.ts:61-82`）含 `session.auth`、`getToken()`、`requireAuth()`

典型例子：
```ts
// apps/fixtures/weather-agent/agent/tools/get_weather.ts
import { defineTool } from "eve/tools";
import { never } from "eve/tools/approval";
import { z } from "zod";

export default defineTool({
  needsApproval: never(),
  description: "Get the current weather for a city.",
  inputSchema: z.object({ city: z.string() }),
  async execute(input) {
    return { city: input.city, temperatureF: 72, condition: "Sunny" };
  },
});
```

### `defineDynamic(definition)` —— `eve/tools` / `eve/skills`

```ts
// src/public/definitions/tool.ts:268-275
```
- **stream-event 驱动的动态解析器**；返回 sentinel
- 可写于 `tools/` / `skills/` / `instructions/` 三个 slot
- 事件 key 由所在目录决定：`session.started` / `turn.started` / `step.started`
- 用途：按会话/轮次动态生成工具、技能、指令

### `disableTool()` —— `eve/tools`

```ts
// src/public/definitions/tool.ts:295-299
```
- sentinel，关闭与文件 slug 同名的框架 tool（如 `agent/tools/bash.ts` 导出 `disableTool()` 关闭内置 bash）

### `ExperimentalWorkflow` —— `eve/tools`

```ts
// src/public/definitions/tool.ts:342-344
```
- sentinel，开启实验性 `Workflow` 编排 tool（让模型在 code-mode sandbox 里 orchestrate subagents）

### `defineSkill(definition)` —— `eve/skills`

```ts
// src/public/definitions/skill.ts:29-34
export function defineSkill(definition): SkillDefinition
```
- `{ name, description, markdown, license?, metadata? }`
- 默认导出时为静态 skill；在 `defineDynamic` 里用时打 `SKILL_BRAND`

### `defineSchedule(definition)` —— `eve/schedules`

```ts
// src/public/definitions/schedule.ts:104-108
```
- 两种形态二选一：`{ cron, markdown }`（prompt 模式）或 `{ cron, run }`（handler 模式）

### `defineChannel<...>(definition)` —— `eve/channels`

```ts
// src/public/definitions/defineChannel.ts:200-218
```
- 把 `{ routes, state?, context?, events?, adapter?, kindHint?, fetchFile? }` 编译成 `CompiledChannel`
- 含 `__kind: CHANNEL_SENTINEL`、`adapter`、`receive`
- 路由 helpers：`GET` / `POST` / `PUT` / `PATCH` / `DELETE` / `WS`（re-export 自 `#channel/routes.js`）

### `disableRoute()` —— `eve/channels`

```ts
// src/public/definitions/channel.ts:130-134
```
- sentinel，关闭与文件 slug 同名的框架路由

### `defineHook(definition)` —— `eve/hooks`

```ts
// src/public/definitions/hook.ts:65-69
```
- 声明 stream-event 订阅器 `{ events: StreamEventHooks }`

### `defineInstructions(definition)` —— `eve/instructions`

```ts
// src/public/definitions/instructions.ts:34-38
```
- `{ markdown }` 包装；在 dynamic resolver 里返回时打 `INSTRUCTIONS_BRAND`

### `defineSandbox<BO, SO>(definition)` —— `eve/sandbox`

```ts
// src/public/definitions/sandbox.ts:51-55
```
- 沙箱定义；`backend` 默认 `defaultBackend()`
- 支持 `bootstrap`（每 template 一次）+ `onSession`（每 session 一次）

### `defineMcpClientConnection(...)` —— `eve/connections`

```ts
// src/public/connections/index.ts:19-21
```
- MCP 协议连接

### `defineOpenAPIConnection(...)` —— `eve/connections`

```ts
// src/public/connections/index.ts:22-26
```
- OpenAPI 协议连接

### `defineInteractiveAuthorization(...)` —— `eve/connections`

```ts
// src/public/connections/index.ts:16
```
- 工具/连接的交互式 OAuth 策略（固定 `principalType: "user"`）

### `defineEval(input)` —— `eve/evals`

```ts
// src/evals/define-eval.ts:21-28
```
- 定义单个 eval case；identity 从 `evals/<path>.eval.ts` 文件路径派生

### `defineEvalConfig(input)` —— `eve/evals`

```ts
// src/evals/define-eval-config.ts
```
- `evals/evals.config.ts` 的全局配置（`judge` / `reporters` / `maxConcurrency` / `timeoutMs`）

### 框架默认工具的可定制工厂 —— `eve/tools`

```ts
// src/public/tools/index.ts:35-42
defineBashTool / defineGlobTool / defineGrepTool / defineReadFileTool / defineWriteFileTool
```
- 让作者重写默认行为（同路径文件覆盖）

### `defineState<T>(...)` —— `eve/context`

```ts
// src/public/definitions/state.ts
```
- 类型安全的 **session 级** state handle
- `defineState("ns", () => initial)`，提供 `get()` / `update()`
- 跨 step 边界存活；子代理**永不**继承父的状态

---

## 框架默认工具（10 个）

来自 `src/runtime/framework-tools/index.ts:20-31`：

| 工具 | 用途 | 可定制工厂 |
|---|---|---|
| `ask_question` | HITL 提问（`display: "select"` / `"confirmation"`） | — |
| `bash` | 在 sandbox 跑 bash | `defineBashTool` |
| `glob` | 文件名 glob 搜索 | `defineGlobTool` |
| `grep` | 内容正则搜索 | `defineGrepTool` |
| `read_file` | 读文件 | `defineReadFileTool` |
| `write_file` | 写文件 | `defineWriteFileTool` |
| `todo` | 任务清单管理 | — |
| `web_fetch` | 抓 URL | — |
| `web_search` | 网页搜索 | — |
| `skill` | lazy-load skill | — |

> 这套默认工具集与 Claude Code / Cursor 的 IDE agent 工具集高度相似——eve 把"IDE agent 的能力"标准化成了 agent 框架的 baseline。

---

## Approval API —— `eve/tools/approval`

```ts
// packages/eve/src/public/tools/approval/approval-helpers.ts:7-28
never()   // 永不要求批准
always()  // 每次都要求批准
once()    // session 内首次批准后免批；拒绝不留记录，下次再问
```
也可写自定义 `(ctx: { approvedTools, toolInput, toolName }) => boolean`。

---

## Channel Auth API —— `eve/channels/auth`

| helper | 用途 |
|---|---|
| `localDev()` | 本地 dev 免鉴权 |
| `vercelOidc()` | Vercel OIDC |
| `placeholderAuth()` | 占位（必须替换） |
| `none()` | 不鉴权（fail-open，仅 demo 用） |
| `httpBasic()` / `jwtHmac()` / `oidc()` | 标准方案 |
| 自定义 `AuthFn` | 任意逻辑 |

默认 eve channel 模板（`apps/templates/web-chat-next/agent/channels/eve.ts`）：
```ts
import { eveChannel } from "eve/channels/eve";
import { localDev, vercelOidc } from "eve/channels/auth";

export default eveChannel({
  auth: [localDev(), vercelOidc(), placeholderAuth()],
});
```

---

## 内置 Channel 工厂

| 工厂 | 子路径 | 凭证管理 |
|---|---|---|
| `eveChannel(...)` | `eve/channels/eve` | 内置 auth helpers |
| `slackChannel(...)` | `eve/channels/slack` | `connectSlackCredentials("slack/<uid>")` from `@vercel/connect/eve` |
| `githubChannel(...)` | `eve/channels/github` | 类似 |
| `discordChannel(...)` | `eve/channels/discord` | 类似 |
| `telegramChannel(...)` | `eve/channels/telegram` | 类似 |
| `twilioChannel(...)` | `eve/channels/twilio` | 类似 |
| `teamsChannel(...)` | `eve/channels/teams` | 类似 |
| `linearChannel(...)` | `eve/channels/linear` | 类似 |

安装方式：`eve channels add slack`（CLI 写文件 + 加 `@vercel/connect` 依赖 + 跑 Connect setup）。

---

## 前端 SDK API

| 框架 | hook | 子路径 |
|---|---|---|
| React | `useEveAgent()` | `eve/react` |
| Vue | `useEveAgent`（composable） | `eve/vue` |
| Svelte | `useEveAgent` | `eve/svelte` |

框架集成入口：
| 框架 | 入口 | 用法 |
|---|---|---|
| Next.js | `eve/next` 的 `withEve()` | `next.config.ts` 包一层 |
| Nuxt | `eve/nuxt` 模块 | `modules: ["eve/nuxt"]` |
| SvelteKit | `eve/sveltekit` 的 `eveSvelteKit()` | `vite.config.ts` 加 plugin |

---

## 设计模式总结

所有 `define*` 函数遵循统一模式：
1. **identity helper**：输入即输出，保留字面量类型（让 TS 推断精确）
2. **brand/sentinel**：打私有 symbol 或 sentinel 字段，运行时识别
3. **最小校验**：只做结构性校验，深度校验留给 compile 阶段
4. **路径即身份**：不接受 `name`/`id` 参数
5. **default export 约定**：作者文件用 `export default defineXxx(...)`

这种模式让 authoring surface 极简，同时把复杂度全部集中到 compile 阶段（`.eve/compile/` 可 audit）。
