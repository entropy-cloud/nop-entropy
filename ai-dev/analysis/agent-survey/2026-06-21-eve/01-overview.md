# 01 · 项目定位与技术栈

## 一句话定位

> **eve is a filesystem-first framework for durable backend AI agents that run anywhere.**
> （文件系统优先的、面向持久化后端 AI agent 的、可随处运行的框架）

—— `packages/eve/package.json:5`

## 三个关键词的展开

### 1. Filesystem-first（文件系统优先）

作者把 agent 写成磁盘上的一个目录。`agent/` 下每个文件/子目录的位置决定了它的角色：
- `agent/tools/get_weather.ts` → 一个工具，名叫 `get_weather`
- `agent/skills/get-weather.md` → 一个技能
- `agent/subagents/researcher/agent.ts` → 一个子代理，名叫 `researcher`

**文件路径即身份**：`define*` 调用里**永远不写 `name`/`id` 字段**。移动文件 = 移动身份。

### 2. Durable（持久化）

每个会话（session）底层是一个 **Workflow DevKit 的 durable workflow run**：
- 自动 checkpoint，崩溃/重新部署后从最后一个完成的 step 恢复
- HITL 审批、OAuth 登录、长跑子代理都会"durable park"——**进程可重启，park 不丢**
- 会话可以跨天、跨周运行

底层是开源的 `@workflow/core`（Vercel Workflow / workflow-sdk.dev）。

### 3. Run anywhere（随处运行）

- **Vercel**：一等公民，用 Vercel Workflow / Vercel Sandbox / Vercel Cron / Vercel OIDC / Deployment Protection
- **自托管**：`eve build && eve start`，跑 Nitro 的 Node standalone 输出，状态默认在 `.workflow-data/`
- **本地 dev**：`eve dev` 起 TUI + 本地 runtime

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | TypeScript（`typescript@7.0.1-rc`，严格模式） |
| 运行时 | Node.js `>= 24`（ESM-only，`"type": "module"`） |
| HTTP 服务器 / 构建器 | **Nitro**（`3.0.260610-beta`，唯一运行时依赖，同时提供 HTTP host + rolldown bundler） |
| Monorepo | pnpm workspace（`11.7.0`）+ turbo（`2.9.18`） |
| Lint / Format | oxlint（`1.70.0`）+ oxfmt（`0.55.0`） |
| 测试 | vitest（`4.1.7`），四层：unit / integration / scenario / e2e |
| Schema | zod（`4.4.3`）+ `@standard-schema/spec` |
| AI 调用 | **Vercel AI SDK**（`ai@7.0.0-beta.178` + `@ai-sdk/*`），核心用其 `ToolLoopAgent` |
| Durable 执行 | `@workflow/core` / `@workflow/errors` / `@workflow/world`（vendored） |
| 模型路由 | 字符串 model id 走 Vercel AI Gateway；`LanguageModel` 实例直连 provider |
| 沙箱 | `@vercel/sandbox` / microsandbox / Docker / just-bash（可切换后端） |
| 变更管理 | changesets + simple-git-hooks（pre-commit 跑 oxfmt） |
| DCO | 所有 commit 必须 `git commit -s`（签名 + Signed-off-by trailer） |

## 运行时依赖策略（关键设计）

`eve` 包对终端用户的 `package.json` 里**只声明一个运行时依赖**：

```json
"dependencies": {
  "nitro": "3.0.260610-beta"
}
```

所有其他常用库（`ai`、`zod`、`@ai-sdk/*`、`@workflow/*`、`commander`、`jose`、`chokidar`、`gray-matter`、`semver`、`picocolors`、`turndown`、`jsonc-parser` 等 27 个）都放在 **`devDependencies`**，在构建时被 **vendoring**（拷贝）进 `.generated/compiled/`，通过 Node 的 subpath imports 别名 `#compiled/*` 引用。

**目的**：
1. 让 `eve` 的安装体积尽可能小
2. 避免 hijacked 的传递依赖通过用户 lockfile 漏进来
3. 跨部署环境行为一致（不依赖用户 node_modules 的解析）

详见 `09-toolchain-deployment.md` 的「`#compiled/*` 虚拟模块」一节。

## Monorepo 布局

```
eve-monorepo/                      (package.json:1, private)
├── packages/
│   ├── eve/                        ← 框架本体 + CLI（主包，发布到 npm）
│   └── eve-catalog/                ← 内部库（不发布）
├── apps/
│   ├── fixtures/                   ← 共享开发 fixture（真实 eve app）
│   │   ├── weather-agent/          ← 核心示例（撑起 pnpm dev / smoke）
│   │   └── agent-tui-client/       ← TUI smoke 用
│   ├── frameworks/                 ← 框架集成 demo（Next/Nuxt/SvelteKit）
│   ├── templates/                  ← 脚手架源（生成 eve init 模板）
│   └── docs/                       ← 官方文档站（Next.js + Velite）
├── e2e/                            ← fixture-owned eval 端到端测试
│   ├── README.md
│   ├── provision/
│   └── fixtures/                   ← 10 个独立 fixture（每个是 eve app）
├── docs/                           ← 发布给用户的文档内容（随包发布）
├── scripts/                        ← 构建脚本（vendor-compiled / build-rolldown / ...）
└── package.json                    ← monorepo 根
```

> **注意**：`eve` 包自带完整文档，安装后位于 `node_modules/eve/docs`——**coding agent 可以本地读文档**（这是 eve 明确的设计意图，README.md:57-59）。

## 导出的子路径（package.json `exports`，30+）

| 类别 | 子路径 |
|---|---|
| 核心 | `eve`, `eve/client` |
| 前端集成 | `eve/react`, `eve/vue`, `eve/svelte`, `eve/next`, `eve/nuxt`, `eve/sveltekit` |
| 工具/技能/指令 | `eve/tools`, `eve/tools/approval`, `eve/tools/defaults`, `eve/skills`, `eve/instructions` |
| Channels | `eve/channels`, `eve/channels/eve`, `eve/channels/auth`, `eve/channels/{slack,github,discord,twilio,telegram,teams,linear}` |
| 连接 | `eve/connections` |
| 调度 | `eve/schedules` |
| Sandbox | `eve/sandbox`, `eve/sandbox/{docker,just-bash,microsandbox,vercel}` |
| 钩子/上下文 | `eve/hooks`, `eve/context`, `eve/instrumentation` |
| Auth | `eve/agents/auth` |
| Evals | `eve/evals`, `eve/evals/expect`, `eve/evals/loaders`, `eve/evals/reporters` |
| 脚手架 | `eve/setup`, `eve/setup/scaffold` |

## 与 Vercel 生态的绑定

eve 是 Vercel 战略级的产品，深度绑定 Vercel 平台能力（离开 Vercel 需显式替换）：

| Vercel 能力 | eve 中的角色 | 离开 Vercel 时 |
|---|---|---|
| **Vercel Workflow** | durable session 引擎 | 用 workflow-sdk 的 local world（`.workflow-data/`） |
| **Vercel Sandbox** | 隔离代码执行 | Docker / microsandbox / just-bash / 自定义 backend |
| **Vercel Cron** | 自动 wire `schedules/` | 手动触发 dev route |
| **Vercel OIDC** | 默认 channel 鉴权 | `httpBasic()` / `jwtHmac()` / 自定义 `AuthFn` |
| **Deployment Protection** | bypass key | 显式处理 |
| **AI Gateway** | 模型路由 + BYOK | 直连 provider + 自备凭证 |
| **Vercel Connect** | Slack 等 channel 凭证管理 | 自行管理 bot token |
| **Agent Runs（Observability）** | 自动 dashboard tab | 自建 OTel / Braintrust |

> 这是一个**强 Vercel binding 但不 lock-in** 的设计：框架本身开源、可自托管，但最佳体验在 Vercel。

## 一句话总结

eve 是一个**把"写 agent"这件事降维成"写一个目录"**的框架——用文件系统约定消灭配置对象，用 durable workflow 消灭状态管理，用 vendoring + 单依赖消灭供应链风险，用一等公民的 eval 系统消灭"测不准"。它的工程深度（编译产物可 audit、四层测试、机械不变量守卫）在 agent 框架里独树一帜。
