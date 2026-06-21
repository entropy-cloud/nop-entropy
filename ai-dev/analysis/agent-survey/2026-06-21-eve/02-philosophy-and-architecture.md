# 02 · 设计哲学与架构分层

## filesystem-first：五条核心命题

> 「A file's location says what it does, and its path usually gives it a name.」
> —— `docs/introduction.mdx:46`

### 命题 1：位置 = 角色

不需要写注册表。把 `get_weather.ts` 放进 `tools/` 它就是工具；移到 `skills/` 它就是 skill；再移到 `subagents/researcher/agent.ts` 它就是子代理。**目录结构本身就是框架契约**。

### 命题 2：路径 = 身份

`define*` 调用**永不**写 `name`/`id` 字段：
- `agent/tools/get_weather.ts` → 运行时工具名 `get_weather`
- `agent/subagents/researcher/agent.ts` → 子代理名 `researcher`
- `agent/channels/slack.ts` → channel id `slack`
- `evals/weather/brooklyn-forecast.eval.ts` → eval id `weather/brooklyn-forecast`
- agent 自身的 identity 在编译期从 `manifest.agentId`（包名或 app 根目录 basename）派生

### 命题 3：目录可读即能力清单

> 「The directory tells you what the agent can do.」

一个 eve 项目**在运行之前就先可读**——读目录树等于读 agent 的能力描述。这让 audit、review、协作都极其低成本。

### 命题 4：从最小起步，按需扩展

最小 agent = `instructions.md` + `agent.ts`。需要时再加 `tools/` / `skills/` / `channels/` / `connections/` / `sandbox/` / `subagents/` / `schedules/` / `lib/`。每个关注点都有「clear home」。

### 命题 5：可检视的编译产物

Discovery → Compile 阶段会把结果写到 `.eve/`：
```
.eve/
├── discovery/
│   ├── agent-discovery-manifest.json   ← 发现阶段产出
│   └── diagnostics.json                ← 错误/警告
└── compile/
    ├── compiled-agent-manifest.json    ← runtime 加载的主 manifest
    ├── module-map.mjs                  ← 静态 import map（ESM）
    ├── channel-instrumentation-types.ts
    └── compile-metadata.json           ← SHA256 摘要 + 版本，status: ready|failed
```

这些都是 JSON + ESM，**能 git diff、能 `eve info` 读取、能在部署前 audit**。

---

## 三层分离架构

eve 把运行时显式切成三个关注点（`docs/concepts/execution-model-and-durability.md` + README）：

```
┌─────────────────────────────────────────────────────────────┐
│                      Channel 层                              │
│  归一化 transport + auth + delivery policy                   │
│  拥有 continuationToken（"下一条消息"句柄）                  │
│  例子：eveChannel / slackChannel / 自定义 webhook            │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      Harness 层                              │
│  一次 model call + 工具批量执行单元（一个 "step"）           │
│  封装 Vercel AI SDK 的 ToolLoopAgent                         │
│  处理 compaction / sandbox surface / code-mode / HITL        │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                      Runtime 层                              │
│  持久化 state + 驱动 next + stream events                    │
│  拥有 sessionId（workflow runId）                            │
│  底层 = Workflow DevKit（durable execution）                 │
└─────────────────────────────────────────────────────────────┘
```

**关键解耦**：`continuationToken`（channel 拥有的恢复句柄）和 `sessionId`（runtime 拥有的事件流+观察句柄）是**两个独立句柄**，让 channel 可以重新 key（比如 Slack 频道换了）而 session 持久。

---

## 源码模块职责（`packages/eve/src/`）

| 子目录 | 职责 | 关键文件 |
|---|---|---|
| `index.ts` | 包入口，仅 `export * from "#public/index.js"` | `src/index.ts:1` |
| `public/` | **所有公开 API 表面**，按子路径分发 | `src/public/index.ts:1` |
| `public/definitions/` | 所有 `define*` 助手（identity helper） | `agent.ts`、`tool.ts`、`skill.ts`、`defineChannel.ts`... |
| `discover/` | **Discovery**：纯文本扫描 agent 目录（不导入 authored module），产出 `AgentSourceManifest` | `discover-agent.ts:58`、`project.ts:52`、`project-source.ts:43` |
| `compiler/` | **Compile**：把 source manifest 编译成 `CompiledAgentManifest`，emit `.eve/` 产物 | `compile-agent.ts:62`、`artifacts.ts:201`、`normalize-manifest.ts:31`、`manifest.ts:602` |
| `runtime/` | **Runtime 解析层**：把编译产物 hydrate 成 `ResolvedAgent` + 各 registry | `resolve-agent-graph.ts:84`、`resolve-agent.ts:37`、`sessions/compiled-agent-cache.ts:145`、`agent/bootstrap.ts` |
| `execution/` | **Workflow / driver 层**：durable workflow 入口、driver loop、turn workflow、session store、subagent 调度、HITL proxy | `workflow-entry.ts:76`、`turn-workflow.ts:32`、`workflow-runtime.ts:78`、`node-step.ts:62` |
| `harness/` | **Tool-loop harness**：封装 AI SDK `ToolLoopAgent`，处理 compaction/sandbox/code-mode/HITL | `tool-loop.ts`（2225 行） |
| `channel/` | Channel adapter 内部实现 | `adapter.ts`、`compiled-channel.ts`、`send.ts`、`session.ts` |
| `protocol/` | 稳定 HTTP 协议常量 + 流事件类型 | `routes.ts:5-134`、`message.ts` |
| `internal/` | 框架内部基础设施（application/bundler/nitro/workflow-bundle/logging...） | `nitro-rolldown.ts:43`、`authored-module-loader.ts:91,142` |
| `cli/` | CLI 实现（Commander.js） | `run.ts:677`、`commands/*` |
| `setup/` | `eve init` 的脚手架引擎 | `setup/scaffold/create/project.ts:246` |
| `evals/` | Eval 子系统 | `define-eval.ts:21`、`cli/eval.ts:38`、`runner/verdict.ts:11` |
| `sandbox/` | Sandbox backend 实现（execution 端） | — |
| `client/` `react/` `vue/` `svelte/` | 前端客户端 SDK | — |

---

## 编程原则（来自 AGENTS.md）

AGENTS.md 第 57-95 行明确 8 条 coding principles，其中几条与架构强相关：

1. **Public APIs need docs and tests.** 导出的函数/类/重要 public type 都要有文档注释和至少一个测试。
2. **Small modules over big helpers.** 倾向窄职责的可组合原语，承担多个关注点的文件要拆。
3. **Wrap third-party dependencies.** 不把三方 API 直接暴露为 eve public API。**运行时 `dependencies` 只作为最后手段**——优先 vendoring 代码或生成物到仓库、把源包列在 `devDependencies`。`eve` 包的目标是**只保留 `nitro` 一个运行时依赖**。→ 解释了为什么是单依赖 + 27 个 vendored 库。
4. **Pre-1.0: prefer breaking changes.** 偏好正确与简洁，不要 backwards-compat 逻辑，不要 legacy fallback。
5. **Derive names from file paths.** 名字从路径派生，不加冗余 `name` 字段。
6. **Name definitions for the protocol they target.** 用 `defineMcpClientConnection`，不用 `defineConnection`。
7. **All runtime functionality lives in the `eve` package.** 永不依赖 emitted/generated code 跑运行时。
8. **Comment why, not what.** 默认不写注释，well-named code 即文档；只注释代码说不出的东西（why / invariant / 惊讶的边界 case）。

这些原则由 `pnpm guard:invariants`（机械不变量检查，在 CI 跑）强制——**baseline 只能缩小**。

---

## 测试四层（AGENTS.md 第 97-128 行）

| 层 | 位置 | 特征 |
|---|---|---|
| **Unit** | `src/**/*.test.ts` | 纯逻辑、colocated；无 fs 写、无子进程、无真实网络 |
| **Integration** | `src/**/*.integration.test.ts` | 内存中多模块协作 |
| **Scenario** | `src/**/*.scenario.test.ts`、`test/scenarios/` | 真实子进程 / HTTP port / bundler；耗时 2-5 分钟 |
| **E2E** | `e2e/fixtures/*/evals/`、`apps/fixtures/weather-agent/evals/` | fixture-owned `eve eval` 套件；跑真实模型（`openai/gpt-5.5`） |

**关键坑**（AGENTS.md 第 110-119 行）：单跑一个测试文件**必须带 tier config**：
```sh
pnpm --filter eve exec vitest run --config vitest.unit.config.ts <path>
```
因为只有 tier config 才把 `#*` 别名解析到 `./src`，否则会测到 stale 的 `./dist`。

**E2E 是 fixture-owned 的**：每个 fixture 是独立的 eve app，`package.json` 里 `"test:e2e": "eve eval --strict"`，跑自己的 eval 套件。这是"端到端 = 真实部署 + 真实模型 + 真实 HTTP 表面"的工程化体现。

---

## 变更管理

- **Changesets**：每个改 published `eve` 包的 PR 必须带 changeset（`pnpm changeset`）。pre-1.0 期，**bug fix 和新 feature 默认 `patch`，只有破坏 public API 才用 `minor`**。
- **DCO + 签名**：commit 必须用 GitHub-verified key 加密签名 + `Signed-off-by` trailer（`git commit -s`）。
- **pre-commit hook**：simple-git-hooks 跑 oxfmt。

---

## 设计哲学小结

eve 的架构哲学可以浓缩成三对张力：

| 张力 | eve 的选择 |
|---|---|
| 配置 vs 约定 | **约定消灭配置**——目录即 manifest，路径即身份 |
| 灵活 vs 可检视 | **可检视优先**——编译产物是可 git diff 的 JSON/ESM |
| 强大 vs 可靠 | **durable by default**——牺牲一点灵活性换崩溃恢复与跨天存活 |

下一章 `03-project-layout.md` 详述这套约定如何在目录结构上落地。
