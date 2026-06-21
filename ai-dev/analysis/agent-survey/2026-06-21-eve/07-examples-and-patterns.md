# 07 · 示例解剖与典型模式

> 基于 `apps/fixtures/weather-agent`、`e2e/fixtures/*`、`apps/templates/web-chat-next` 的实际代码。

## weather-agent 示例完整解剖

> 核心示例，撑起根目录 `pnpm dev`、bundle 分析、smoke。
> **注意**：实际目录是 `apps/fixtures/weather-agent/`（README 里提到的 `weather-fixture` 是旧名，`apps/fixtures/README.md:7` 已更正）。

### 目录结构（共 7 个有效文件）

```
apps/fixtures/weather-agent/
├── .gitignore
├── README.md
├── package.json          # 5 个脚本（dev/build/start/info/typecheck）
├── tsconfig.json         # 严格模式，include: agent/**, evals/**, .eve/**
├── turbo.json            # 依赖 eve#build
└── agent/
    ├── agent.ts          # defineAgent(...)
    ├── instructions.md   # always-on 指令
    ├── tools/
    │   └── get_weather.ts
    └── skills/
        └── get-weather.md
```

### `agent/agent.ts`（13 行）

```ts
import { defineAgent } from "eve";

export default defineAgent({
  model: "openai/gpt-5.5",
  modelOptions: {
    providerOptions: {
      openai: { reasoningEffort: "high", reasoningSummary: "auto" },
    },
  },
});
```

要点：
- **没名字字段**——identity 编译期派生
- `defineAgent` 是 idempotent 类型守卫（`agent.ts:35-39`），返回原对象并保留字面量类型
- `providerOptions` 透传到 AI SDK provider

### `agent/instructions.md`（1 行）

```
You are a weather-focused assistant. Be concise, accurate, and explicit about when you are using the local weather tool.
```

### `agent/tools/get_weather.ts`（25 行，典型范例）

```ts
import { defineTool } from "eve/tools";
import { never } from "eve/tools/approval";
import { z } from "zod";

const sleep = (ms: number) => new Promise((res) => setTimeout(res, ms));

export default defineTool({
  needsApproval: never(),
  description: "Get the current weather for a city.",
  inputSchema: z.object({ city: z.string() }),
  async execute(input) {
    const city = input.city;
    await sleep(300);
    return {
      city,
      temperatureF: 72,
      condition: "Sunny",
      summary: `Sunny in ${city} with a light breeze.`,
    };
  },
});
```

要点：
- **tool 名是路径派生**：`agent/tools/get_weather.ts` → `get_weather`
- `inputSchema` 用 zod；输出是任意 JSON-serializable 对象
- **`needsApproval`** 是 HITL 钩子，可选 `never()` / `once()` / `always()` / 自定义
- `execute(input, ctx)` 的 `ctx` 含 `session.auth`、`getToken()`、`requireAuth()`

### `agent/skills/get-weather.md`（5 行）

```markdown
---
description: Use the weather tool before answering forecast or temperature questions.
---

When the user asks about weather, temperature, or forecast conditions, call the `get_weather` tool before answering.
```

要点：
- YAML frontmatter `description` 是模型可见的「何时调用」提示
- markdown body 是 skill 内容，**按需 lazy-load**（框架提供 `skill` 工具）

---

## 典型模式归纳（来自 e2e fixtures）

### 模式 1：Tool 错误恢复

`e2e/fixtures/agent-tools/agent/tools/always-throws.ts:8-15`：

```ts
export default defineTool({
  description: "Always throws an error.",
  inputSchema: z.object({}),
  async execute() {
    throw new Error("This tool always fails.");
  },
});
```

**关键**：`execute` 同步 `throw`，AI SDK 把它合成 `action.result` with `isError: true`，**不会**触发 `turn.failed`。session 还活着——eval 验证下一轮仍可正常对话。

（`e2e/fixtures/agent-tools/evals/throw-recover.eval.ts:7-34`）

### 模式 2：Tool 输出 narrowing（hook）

`e2e/fixtures/agent-tools/agent/hooks/tool-result-narrowing.ts:5-30`：

```ts
const hook: HookDefinition = defineHook({
  events: {
    "action.result"(event) {
      const match = toolResultFrom(event.data.result, structuredEcho);
      if (match === undefined) return;
      // 对 match.output 做类型 narrow 后处理
    },
  },
});
```

- 监听 `action.result`，用 `toolResultFrom(result, toolDef)` 以 symbol 身份匹配工具输出
- 典型的 typed narrowing 模式

### 模式 3：HITL approval park/resume

`e2e/fixtures/agent-tools-hitl/evals/hitl/approve-then-no-regate.eval.ts:13-40`：

```ts
async test(t) {
  await t.send("...");
  await t.expectInputRequests({ display: "confirmation", toolName: "..." });
  t.calledTool("...", { isError: false });  // 还没批准，没结果
  await t.respondAll("approve");
  t.calledTool("...", { isError: false });  // 批准后执行
  // 第二轮不再 park
  await t.send("...");
  t.calledTool("...");  // 直接执行，无 input.requested
}
```

**关键**：`once()` 模式下，session 内首次批准后免批。

### 模式 4：ask_question（select / confirmation）

`e2e/fixtures/agent-tools-hitl/evals/hitl/ask-question-select.eval.ts:20-37`：

```ts
async test(t) {
  await t.send("Ask me a select question with two options.");
  await t.expectInputRequests({ display: "select" });
  // 断言 option ids
  t.event((e) => e.type === "input.requested" && e.data.options?.length === 2);
  await t.respondAll("option-a");
  t.messageIncludes("...");  // 模型根据选择回复
}
```

### 模式 5：OpenAPI Connection + per-call HITL

`e2e/fixtures/agent-openapi-swagger/agent/connections/tfl.ts`：

```ts
import { defineOpenAPIConnection } from "eve/connections";
import { always } from "eve/tools/approval";

export default defineOpenAPIConnection({
  spec: "https://api.tfl.gov.uk/swagger/docs/v1",
  description: "Transport for London Unified API from its public Swagger 2.0 document.",
  operations: { allow: ["Journey_Meta"] },
});
```

带 per-call HITL（`tfl-approval.ts`）：
```ts
export default defineOpenAPIConnection({
  // ...
  approval: always(),
});
```

eval 验证（`tfl-swagger-approval.eval.ts:12-60`）：
```ts
await t.expectInputRequests({ display: "confirmation", toolName: "connection__tfl__Journey_Meta" });
// 检查 approve/deny options
await t.respondAll("approve");
```

### 模式 6：MCP Connection + OAuth 2.1 + PKCE

`apps/fixtures/agent-tui-client/agent/connections/stub-mcp-user.ts:73-163`：

```ts
const definition: McpClientConnectionDefinition = {
  url,
  description: "...",
};

if (userAuthEnabled) {
  definition.auth = defineInteractiveAuthorization<OAuthState>({
    async getToken({ principal }) { /* 从 durable state 读 token */ },
    async startAuthorization({ callbackUrl }) { /* OAuth 2.1 + PKCE 起手 */ },
    async completeAuthorization({ principal, resume, callbackUrl, callback }) {
      /* 处理 callback，验证 state，换 token */
    },
  });
}

export default defineMcpClientConnection(definition);
```

要点：
- MCP 连接支持**真实 OAuth 2.1 + PKCE**
- `defineInteractiveAuthorization` 固定 `principalType: "user"`
- **`resume` 字段跨 workflow step 序列化**——park 后还能恢复 PKCE verifier（这是 durable 的价值）
- token cache 是 connection 作者的责任（per-step cache 不够）

### 模式 7：Custom Channel + 跨 channel handoff

`e2e/fixtures/agent-channels/agent/channels/webhook.ts:10-29`：

```ts
export default defineChannel({
  routes: [
    POST("/webhook", async (req, args) => {
      const session = await args.receive(target, { message, target, auth });
      return Response.json({ ok: true, sessionId: session.id });
    }),
  ],
});
```

**关键**：`args.receive(otherChannel, input)` 实现**跨 channel handoff**——webhook 收到的消息可以 handoff 到 eve channel 处理。

### 模式 8：Slack Channel

`apps/docs/lib/integrations/data.ts:117-125`：

```ts
import { slackChannel } from "eve/channels/slack";
import { connectSlackCredentials } from "@vercel/connect/eve";

export default slackChannel({
  credentials: connectSlackCredentials("slack/my-agent"),
});
```

- `connectSlackCredentials` 用 [Vercel Connect](https://vercel.com/docs/connect) 管理出站 bot token 和入站 webhook 验证
- 默认处理：mentions、DMs、typing indicators、delivery、HITL consent
- 安装：`eve channels add slack`（写文件 + 加依赖 + 跑 Connect setup）
- 实现源码：`packages/eve/src/public/channels/slack/slackChannel.ts:474`

### 模式 9：Schedule（markdown + code）

`e2e/fixtures/agent-schedules/agent/schedules/heartbeat.ts:12-18`：

```ts
export default defineSchedule({
  cron: "0 0 * * *",
  markdown: [
    "Call the `record-heartbeat` tool exactly once with note 'cron-tick'.",
    "Do not call any other tool. You have no other task.",
  ].join("\n"),
});
```

- 文件位置 `agent/schedules/<name>.ts`
- 在 Vercel 上变成真实 Cron Job
- 本地/dev 通过 `t.target.dispatchSchedule("heartbeat")` 触发
- eval 验证（`schedule-dispatch.eval.ts:22-67`）：dispatch → attachSession → 在事件流里找 `record-heartbeat` 工具结果

### 模式 10：Subagent delegation

`e2e/fixtures/agent-subagents/agent/subagents/echo-marker/agent.ts:12-16`：

```ts
export default defineAgent({
  description:
    "Smoke-test echo subagent. Call this whenever the user mentions the phrase 'echo marker subagent'. ...",
  model: "openai/gpt-5.5",
});
```

- 子 agent **必须**有 `description`（父 agent 用它决定何时委派）
- 父 agent 通过 `subagent.called` / `subagent.completed` 事件感知委派
- eval 验证（`local-delegation.eval.ts:14-21`）：`calledSubagent("echo-marker", { output: /.../ })` + 消息含 SUBAGENT_TOKEN
- 子 agent 的工具 HITL 请求会**代理到父 session stream**（`e2e/fixtures/agent-subagents-hitl/evals/hitl.eval.ts:30-51`）

### 模式 11：Sandbox（隔离代码执行）

`e2e/fixtures/agent-tools-sandbox/agent/sandbox/sandbox.ts:61-87`：

```ts
export default defineSandbox({
  backend,  // defaultBackend() 或 vercel({ source: { snapshotId, type } })
  revalidationKey: () => "agent-tools-sandbox-bootstrap-v2",
  async bootstrap({ use }) {
    const sandbox = await use();
    await sandbox.writeTextFile({ path, content });
    // install CLI onto PATH, etc.
  },
  async onSession({ use }) {
    const sandbox = await use();
    await sandbox.writeTextFile({ path: SESSION_MARKER_PATH, content: ... });
  },
});
```

要点：
- `bootstrap` 每个 template 跑一次（写 marker、装 CLI）
- `onSession` 每个 live session 跑一次
- 后端可切：`docker()` / `vercel()` / `microsandbox()` / 快照源
- 配套工具 `bash.ts` / `run_python.ts` / `network-probe.ts` 在 sandbox 里跑
- eval 验证网络策略（`network-policy.eval.ts:23-37`）：用正则区分「网络被拒」vs「curl 不存在」

### 模式 12：Dynamic skill / instructions

`e2e/fixtures/agent-skills/agent/instructions/dynamic-context.ts:5-13`：

```ts
export default defineDynamic({
  events: {
    "session.started"(event) {
      return defineInstructions({ markdown: `Current time: ${new Date().toISOString()}` });
    },
  },
});
```

- 在 `session.started` / `turn.started` / `step.started` 事件里 resolve
- 可动态生成 instructions / tools / skills

### 模式 13：Compaction（上下文压缩）

`agent.ts` 配置：
```ts
defineAgent({
  model: "...",
  compaction: { /* 配置压缩模型、策略 */ },
});
```

harness 在 `shouldCompact` 触发时调 `resolveCompactionModel` 压缩历史消息，**支持跨 step 状态保留**——压缩后 session 行为不退化。

### 模式 14：Output schema（结构化输出）

`e2e/fixtures/agent-basic-runtime/evals/output-schema-turn.eval.ts:24-37`：

```ts
async test(t) {
  await t.send({
    message: "Return a structured result",
    outputSchema: { type: "object", properties: { ... } },
  });
  t.check(t.outputValue, matches(z.object({ ... })));
  // 断言下一轮不泄漏 result.completed
}
```

---

## 默认 `eve init` 模板结构

### 入口

`packages/eve/src/cli/run.ts:413-422`：
```ts
program.command("init [target]")
  .option("--channel-web-nextjs", "Add the Web Chat application (Next.js)")
  .action(async (target, options) => { ... });
```

两种模式：
- **fresh scaffold**（在 parent dir 下建子目录）
- **add-to-existing**（在现有项目里加 agent）

### `scaffoldBaseProject` 生成的文件

`packages/eve/src/setup/scaffold/create/project.ts:246-256` 定义 `templateFiles()`：

| 文件 | 内容 |
|---|---|
| `agent/agent.ts` | `defineAgent({ model: "__EVE_INIT_MODEL__" })`（或 BYOK 变体） |
| `agent/channels/eve.ts` | `eveChannel({ auth: [localDev(), vercelOidc(), placeholderAuth()] })` |
| `agent/instructions.md` | `# Identity\n\nYou are a helpful assistant.\n` |
| `package.json` | `imports: { "#*": "./agent/*", "#evals/*": "./evals/*" }` + scripts + deps |
| `tsconfig.json` | ES2022/NodeNext，strict，include `agent/**`, `evals/**`, `.eve/**` |
| `.gitignore` | node_modules、`.env*`、`.eve`、`.vercel`、`.workflow-data` |
| `.vercelignore` | 同上 |
| `AGENTS.md` | 引导读 `node_modules/eve/docs/` |
| `CLAUDE.md` | `@AGENTS.md`（include 语法） |
| 包管理器特定文件 | pnpm/npm/yarn/bun |

### `agent/agent.ts` 模板（两种变体）

**BASE_AGENT_TEMPLATE**（默认，依赖 Vercel AI Gateway）：
```ts
import { defineAgent } from "eve";
export default defineAgent({ model: "__EVE_INIT_MODEL__" });
```

**BYOK_AGENT_TEMPLATE**（自带 API key）：
```ts
import { defineAgent } from "eve";
export default defineAgent({
  model: "__EVE_INIT_MODEL__",
  modelOptions: {
    providerOptions: {
      gateway: {
        byok: {
          "__EVE_INIT_BYOK_PROVIDER__": [{ apiKey: process.env.__EVE_INIT_BYOK_ENV_VAR__! }],
        },
      },
    },
  },
});
```

变量替换（`renderTemplate()`，`project.ts:100-113`）：
- `__EVE_INIT_APP_NAME__` ← 目录 basename
- `__EVE_INIT_MODEL__` ← 默认 `DEFAULT_AGENT_MODEL_ID`
- `__EVE_INIT_BYOK_PROVIDER__` ← 模型 id 的 provider slug（`/` 之前）
- `__EVE_INIT_BYOK_ENV_VAR__` ← 自动派生（如 `ANTHROPIC_API_KEY`）

### `package.json` 模板要点

```json
{
  "imports": {
    "#*": "./agent/*",          ← 让 agent/ 内文件互相 import
    "#evals/*": "./evals/*"
  },
  "dependencies": {
    "@vercel/connect": "...",   ← 提前装好，便于后续 eve channels add slack 不引入新依赖
    "ai": "...",
    "eve": "...",
    "zod": "..."
  }
}
```

### Web 模板（`--channel-web-nextjs`）

源 app：`apps/templates/web-chat-next/`，包含：
- Next.js 16 + React 19 + Tailwind 4 + shadcn-style AI Elements 组件
- `app/page.tsx` + `app/_components/agent-chat.tsx`（调用 `useEveAgent()` from `eve/react`）
- `next.config.ts` 用 `withEve(nextConfig)`

`withEve()` 的三种部署形态（`apps/frameworks/next/README.md:9-26`）：
- **本地 dev**：在随机端口启动 eve dev，把 `/eve/v1/*` 重写到该端口
- **Vercel 部署**：把 `experimentalServices` 写到 `.vercel/output/config.json`，Next.js 在 `/`，eve 跑在私有 `/_eve_internal/eve`
- **非 Vercel production**：设 `EVE_NEXT_PRODUCTION_ORIGIN` 指向独立 eve origin

---

## 框架集成示例

| 框架 | 包名 | 集成入口 | 用法 |
|---|---|---|---|
| Next.js | `framework-next` | `eve/next` 的 `withEve()` | `next.config.ts` 包一层 |
| Nuxt 4 | `framework-nuxt` | `eve/nuxt` 模块 | `modules: ["eve/nuxt"]` |
| SvelteKit | `framework-sveltekit` | `eve/sveltekit` 的 `eveSvelteKit()` | `vite.config.ts` 加 plugin |

共同模式：每个 demo 都有同一个 `agent/` 目录结构 + 默认 eve channel + 各框架的 hook（`useEveAgent`）。

---

## 一个完整的端到端心智模型

```
[npx eve init my-app]
       ↓
[scaffoldBaseProject(...)]  ← packages/eve/src/setup/scaffold/create/project.ts:302
       ↓ 写
my-app/
├── agent/agent.ts            # defineAgent({ model })
├── agent/channels/eve.ts     # eveChannel({ auth: [...] })
├── agent/instructions.md     # always-on prompt
├── package.json              # scripts: dev/build/start
└── AGENTS.md / CLAUDE.md
       ↓
[eve dev]  → 启动 TUI，本地端口
       ↓
用户在 TUI 输入 → agent 跑模型 → 可能调 agent/tools/* → 回复

[加 web UI]  eve channels add web   → 加 Next.js 模板
[加 slack]   eve channels add slack → 写 agent/channels/slack.ts
[写 eval]    evals/<name>.eval.ts
[跑 eval]    eve eval --strict
```

**整个框架的"约定大于配置"非常明显**：所有身份都从文件路径派生，用户从不写 ID。
