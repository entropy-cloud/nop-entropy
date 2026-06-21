# 05 · 运行时与执行流程

> 本章描述一个 agent 从「磁盘上的目录」到「HTTP 响应流」的完整链路：**Discovery → Compile → Load → Workflow 执行**。

## 四阶段总览

```
[agent/ 目录]                                            [HTTP 响应]
      ↓                                                       ↑
 ① Discovery ─→ ② Compile ─→ .eve/ 产物 ─→ ③ Load ─→ ④ Workflow 执行
 (扫描目录)     (normalize)   (JSON+ESM)    (hydrate)   (durable run)
```

| 阶段 | 输入 | 输出 | 关键文件 |
|---|---|---|---|
| ① Discovery | `agent/` 目录树 | `AgentSourceManifest`（path-derived refs） | `src/discover/discover-agent.ts:58` |
| ② Compile | source manifest | `CompiledAgentManifest` + `module-map.mjs` 写入 `.eve/` | `src/compiler/compile-agent.ts:62`、`artifacts.ts:201` |
| ③ Load | `.eve/compile/*` | `ResolvedAgentGraphBundle`（hydrated 函数/对象） | `src/runtime/sessions/compiled-agent-cache.ts:145`、`resolve-agent-graph.ts:84` |
| ④ Workflow 执行 | HTTP 请求 | NDJSON 事件流 | `src/execution/workflow-runtime.ts:78` |

---

## 阶段 ①：Discovery（纯文本扫描）

**核心特征**：不导入任何 authored module，纯文本遍历，所有 IO 走 `ProjectSource` 抽象。

### 项目根解析

`resolveDiscoveryProject`（`src/discover/project.ts:52-100`）从 `startPath` 往上爬，识别两种 layout：
- **nested**：目录里有 `agent/` 子目录（`agent/agent.ts` 标记）
- **flat**：目录本身就是 agent root（含 `agent.ts` / `instructions.md` 等任一 marker）

返回 `{ agentRoot, appRoot, layout }`。

### ProjectSource 抽象

`src/discover/project-source.ts:43-99`：所有 IO 走这里。
- 生产用 `createDiskProjectSource()`（直接 `node:fs/promises`）
- 测试用 `createMemoryProjectSource()`（in-memory 文件树）

**这让 discovery 完全可被 in-memory 测试**——是 eve 测试体系的基础。

### discoverAgent 遍历

`src/discover/discover-agent.ts:58-197` 遍历 agent root 下所有 slot：

| slot | discoverer |
|---|---|
| `instructions`（root，`.md` 或 `.ts`） | `discoverInstructionsSource` |
| `agent.ts`（flat module） | `discoverFlatModuleSource` |
| `channels/`（递归） | `discoverNamedSourceDirectory` |
| `lib/` | `discoverLibSources` |
| `schedules/`（递归，md + TS） | `discoverScheduleSources`（`schedules.ts:47-71`） |
| `connections/`（递归，folder-form + file-form） | `discoverConnectionSources`（`connections.ts:51-80`） |
| `sandbox.ts` 或 `sandbox/sandbox.ts` | `discoverSandboxSource` |
| `tools/`（递归） | `discoverNamedSourceDirectory` |
| `hooks/`（递归） | `discoverNamedSourceDirectory` |
| `skills/` | `discoverSkills`（flat + Agent Skills package）（`skills.ts:58-100`） |
| `subagents/`（递归） | `discoverSubagents`（对每个子目录**递归重复整个 discoverAgent**） |

每个分类有专属诊断码（如 `DISCOVER_CHANNELS_DIRECTORY_INVALID`）。

产出 `AgentSourceManifest`——**全部 path-derived，identity 来自文件 slug**。

---

## 阶段 ②：Compile（normalize + emit 产物）

入口：`writeCompilerArtifacts`（`src/compiler/artifacts.ts:201-253`）。

### 2.1 normalize

`compileAgentManifest(manifest)`（`normalize-manifest.ts:31-53`）：
1. `compileAgentConfig`（`normalize-agent-config.ts`）解析 `agent.ts` 默认导出（通过 authored module loader 加载）
2. 对每个 slot 调用专属 normalize：
   - `compileChannelDefinition` / `compileToolEntry` / `compileSkillSource` / `compileHookEntry` / `compileScheduleDefinition` / `compileConnectionDefinition` / `compileSandboxDefinition` / `compileInstructionsEntry`
3. `compileSubagentGraph` 递归 flatten subagent 节点 → `CompiledSubagentNode[]` + `CompiledSubagentEdge[]`（父子图）

输出符合 zod schema `compiledAgentManifestSchema`（`manifest.ts:602-629`）的 `CompiledAgentManifest`，版本号 `COMPILED_AGENT_MANIFEST_VERSION = 30`。

### 2.2 module-map 生成（关键机制）

`createCompiledModuleMapSource`（`module-map.ts:61-`）生成 **`module-map.mjs`**：

```js
// 一个 ESM 文件，顶部静态导入所有 module-backed authored 源文件
import * as module_0 from "<specifier>";
import * as module_1 from "<specifier>";
// ...
export const modules = { module_0, module_1, ... };
```

- 默认 `relative` 风格 specifier
- dev 用 `absolute` 风格让 bundler 通过 virtual id 消费

**这是把「磁盘上的零散 TS」变成「可被 runtime 引用的命名空间」的关键机制。**

### 2.3 写入 6 个产物

`artifacts.ts:231-244` 写入：
| 文件 | 内容 |
|---|---|
| `.eve/discovery/agent-discovery-manifest.json` | 发现阶段产出 |
| `.eve/discovery/diagnostics.json` | 错误/警告 |
| `.eve/compile/compiled-agent-manifest.json` | runtime 加载的主 manifest |
| `.eve/compile/module-map.mjs` | 静态 import map |
| `.eve/compile/channel-instrumentation-types.ts` | 类型声明（让 `isChannel()` 基于 path-derived kind narrow） |
| `.eve/compile/compile-metadata.json` | SHA256 摘要 + generator 版本，`status: ready \| failed` |

如果 discovery 有 error，`compileAgent` 抛 `CompileAgentError`（`compile-agent.ts:43-56`）。

---

## 阶段 ③：Load（runtime 解析 / hydrate）

入口：`getCompiledRuntimeAgentBundle`（`src/runtime/sessions/compiled-agent-cache.ts:145-171`）。

### 3.1 读产物

```ts
const [manifest, moduleMap] = await Promise.all([
  loadCompiledManifest({ compiledArtifactsSource }),      // src/runtime/loaders/manifest.ts:36
  loadRuntimeCompiledModuleMap(compiledArtifactsSource),  // compiled-agent-cache.ts:68-79
]);
```

- `loadCompiledManifest`（`loaders/manifest.ts:36-68`）：从 `.eve/compile/compiled-agent-manifest.json` 读 + zod 校验；若 `kind === "bundled"`（生产 Nitro 产物）则从内嵌 snapshot 读
- `loadCompiledModuleMap`（`loaders/module-map.ts:36-72`）：从 `module-map.mjs` import 命名空间

### 3.2 Dev 模式特例

`loadCompiledModuleMapFromAuthoredSource`（`internal/authored-module-map-loader.ts:15-23`）跳过 `module-map.mjs`，对每个 module ref 调用 `loadAuthoredModuleNamespace` 直接从 authored 源 hydrate。

`loadAuthoredModuleNamespace`（`authored-module-loader.ts:91-112`）→ `loadBundledAuthoredModule`（`142-247`）：
- 用 **Nitro 自带的 rolldown**（`buildWithNitroRolldown`，`nitro-rolldown.ts:93-98`）bundle 单个 TS module 到 `node_modules/.cache/eve/authored-modules/<sha1>.mjs`
- 加多个 plugin（authored asset import / package tsconfig paths / node esm compat banner / package boundary / channel identity cache）
- **in-flight load 去重**（`inFlightModuleLoads`，84）防止并发 race
- 通过 `import(file://...?v=hash)` 加载 hashed bundle，让 Node ESM cache 按 content hash 去重

### 3.3 resolveRuntimeAgentGraph（递归 hydrate）

`src/runtime/resolve-agent-graph.ts:84-100`：递归把 manifest + moduleMap 变成 `ResolvedAgentGraphBundle`，每个 node 含：

| 注册表 | 内容 | 来源 |
|---|---|---|
| `agent` (ResolvedAgent) | hydration 所有 module refs 为实际函数/对象 | `resolveAgent`（`resolve-agent.ts:37-129`） |
| `channels` | 框架默认 + authored - authored disables | `framework-channels/index.ts:25-53` |
| `tools` | 框架默认 10 个 + authored + subagent + dynamic | `framework-tools/index.ts:20-31` |
| `subagentRegistry` | 把 subagent 包装成 `PreparedRuntimeDelegationTool`（固定 input schema `{ message, outputSchema? }`） | `subagents/registry.ts:49-95` |
| `hookRegistry` / `sandboxRegistry` / `turnAgent` | 含 model bootstrap | — |

**session 级缓存**（`session.bundleCache`，`compiled-agent-cache.ts:99-134`），dev server 文件变更时通过 `clearCompiledRuntimeAgentBundleCache` 失效。

---

## 阶段 ④：Workflow 执行（HTTP → 模型 → 响应）

### 完整调用链

```
HTTP Request
  → Nitro route (dispatchChannelRequest)            [channel-dispatch.ts:33]
  → resolveNitroChannelRuntimeBundle                [runtime-stack.ts:33]
    → getCompiledRuntimeAgentBundle                 [阶段 ③]
    → createWorkflowRuntime                         [workflow-runtime.ts:78]
  → runtime.run(input)
    → workflowEntry (durable workflow)              [workflow-entry.ts:76]
      → createSessionStep                           [create-session-step.ts]
      → runDriverLoop                               [workflow-entry.ts:155-400]
        → dispatchAndAwaitTurn                      [424-457]
          → turnWorkflow (child workflow)           [turn-workflow.ts:32]
            → turnStep
              → createExecutionNodeStep             [node-step.ts:62]
                → createToolLoopHarness             [harness/tool-loop.ts]
                  → LanguageModel.call + tool loop  [Vercel AI SDK ToolLoopAgent]
                  → returns { action: "done"|"park"|"dispatch-runtime-actions"|... }
            → notifyDriverStep (resumeHook)         [turn-workflow.ts:129-138]
        → NextDriverAction 分支: done / park / dispatch
  → NDJSON event stream → response
```

### Step 1：HTTP 进入

Nitro 把每个 channel 的每条 route 注册为单独的 virtual handler。请求匹配后调用 `dispatchChannelRequest(event, routeKey, config)`（`channel-dispatch.ts:33-84`）：
- authored channel → `matchedChannel.handler(event.req, routeArgs.args)`
- framework-internal channel（如 connection callback）→ `matchedChannel.fetch(event.req, { agent: bundle.runtime, ... })`

### Step 2：解析 runtime bundle

`resolveNitroChannelRuntimeBundle`（`runtime-stack.ts:33-45`）：
```ts
const compiledArtifactsSource = resolveNitroCompiledArtifactsSource(config);
const bundle = await getCompiledRuntimeAgentBundle({ compiledArtifactsSource });
const runtime = createWorkflowRuntime({ compiledArtifactsSource });
return { channels: bundle.graph.root.channels, runtime };
```

### Step 3：Channel adapter → runtime.run / deliver

Channel 的 `handler` 通过 `RouteContext.agent` 调用（`public/definitions/channel.ts:80-108`）：
- `agent.run({ input, continuationToken?, ... })` —— 启动新 session
- `agent.deliver({ continuationToken, ... })` —— 给 parked session 投递后续消息
- `agent.getEventStream(sessionId)` —— 读事件流

`agent` 实际是 `createWorkflowRuntime` 返回的 `Runtime` 对象（`workflow-runtime.ts:78-119`），封装 Workflow DevKit 的 `start` / `resumeHook` / `getRun`。

### Step 4：启动 durable workflow

`runtime.run`（`workflow-runtime.ts:83-118`）：
```ts
const bundle = await getCompiledRuntimeAgentBundle({ compiledArtifactsSource, nodeId });
const ctx = buildRunContext({ bundle, run: input });
const run = await startWorkflowPreferLatest(workflowEntryReference, [{ input: input.input, serializedContext }]);
return {
  continuationToken: input.continuationToken ?? run.runId,
  sessionId: run.runId,
  get events() { ... }  // NDJSON-parsed ReadableStream<HandleMessageStreamEvent>
};
```

- `workflowEntryReference.workflowId = "workflow//eve//workflowEntry"`（`workflow-runtime.ts:59-61`），**不带版本号**，跨 deployment 稳定
- `start` 来自 `@workflow/core/runtime`（vendored），返回 `runId` 即 `sessionId`

### Step 5：Workflow body（driver loop）

`workflowEntry`（`workflow-entry.ts:76-153`）：
1. `"use workflow"` directive（被 workflow bundler 识别改写）
2. 从 `serializedContext` 取 `bundle.source` / `mode` / `capabilities`
3. `createSessionStep`：建 durable session state、emit `$eve.*` tags、写 `eve.sessionId`
4. `runDriverLoop`（`workflow-entry.ts:155-400`）：
   - 创建 `authHook`（per-session OAuth 回调）+ 可重 key 的 `parkHook`（per-continuationToken）
   - `dispatchAndAwaitTurn`（`424-457`）：dispatch 一个 `turnWorkflow` child workflow，等 completion hook
   - 拿到 `NextDriverAction`，按 `kind` 分支：
     - `done` → `finalizeDone`（emit `session.completed/failed` + 通知委托 parent）
     - `park` → 等 `authHook` 或 `parkHook` 投递（用户 follow-up / OAuth 完成），再 `dispatchAndAwaitTurn`
     - `dispatch-runtime-actions` / `dispatch-code-mode-runtime-actions` → 处理 subagent 返回值、code-mode runtime actions
   - 循环直到 done 或 hook 关闭

### Step 6：Turn workflow + harness step

`turnWorkflow`（`turn-workflow.ts:32-126`）：另一个 durable workflow，循环调 `turnStep` 直到拿到 `done` / `park` / `dispatch-code-mode-runtime-actions`，再通过 `notifyDriverStep`（`129-138`）`resumeHook(completionToken, payload)` 唤醒 driver。

`turnStep` 最终调用 `HarnessSession`，由 `createExecutionNodeStep`（`node-step.ts:62-76`）构造：
```ts
return createToolLoopHarness({
  capabilities, codeMode, workflow, handleEvent, mode,
  onCompaction: preserveFrameworkStateOnCompaction,
  resolveModel,        // createRuntimeModelResolver → @ai-sdk/* 或 gateway
  runtimeIdentity,     // agentId, agentName, modelId, eveVersion, build (gitSha, ...)
  tools,               // createNodeHarnessTools（合并 framework + authored + subagent + dynamic）
});
```

### Step 7：Tool-loop（Vercel AI SDK）

`createToolLoopHarness`（`harness/tool-loop.ts`，2225 行）是核心。它包装 Vercel AI SDK 的 `ToolLoopAgent`（`tool-loop.ts:13`），每个 step：

1. **Pre-turn**：emit `turn.started`、构建 dynamic instructions、构建 dynamic tools、stage 附件到 sandbox
2. **Compaction check**：`shouldCompact` + `compactMessages`（用 `resolveCompactionModel`，支持跨 step 状态保留）
3. **Model call**：通过 `LanguageModel` 调配置模型
4. **Stream events**：emit `message.appended` / `reasoning.appended` 等
5. **Tool execution**：批量执行 model 请求的 tools（含 approval gate、sandbox surface 选择、code-mode 拦截）
6. **Input requests**：approval / question / structured input → emit `input.requested`、accumulate `pendingInputBatch`
7. **Step result**：返回 `{ action: "continue" | "done" | "park", output?, ... }`

### Step 8：输出流

`runtime.run` 返回的 `RunHandle.events` 是 NDJSON-parsed `ReadableStream<HandleMessageStreamEvent>`（`workflow-runtime.ts:108-110`）：
- 来自 `getRun(runId).getReadable()` —— Workflow DevKit 的 durable event stream
- 由 `parseNdjsonStream` 解析
- Channel route（如 `eveChannel`）把 events 投递回 HTTP/SSE/WebSocket 客户端

事件类型在 `src/protocol/message.ts` 定义，包括：
`session.started/failed/completed`、`turn.started/completed/failed`、`message.appended/completed`、`reasoning.*`、`action.result`、`actions.requested`、`input.requested`、`authorization.required/completed`、`compaction.*`

---

## 两个稳定 workflow id

| workflow id | 角色 | lifetime |
|---|---|---|
| `workflow//eve//workflowEntry` | 长 lifetime 的 driver | 整个 session |
| `workflow//eve//turnWorkflow` | 每个 turn 一个 child run | 单个 turn |

`"use workflow"` / `"use step"` directive 被 `workflow-transformer.ts` 在 bundle 时改写——这是 Workflow DevKit 的 durable 编程模型。

---

## Subagent 调度机制

`createRuntimeSubagentRegistry`（`subagents/registry.ts:49-95`）把每个 subagent 包装成 `PreparedRuntimeDelegationTool`：

```ts
// 对父 agent 来说就是一个 tool，固定 input schema
SUBAGENT_TOOL_INPUT_SCHEMA = {
  type: "object",
  properties: { message: ..., outputSchema: ... },
  required: ["message"],
}
```

父 agent 模型调用这个 tool 时，runtime 启动 **delegated child workflow run**（同 workflow runtime），子跑完后通过 `notifyDelegatedParentStep`（`workflow-entry.ts:147-151`）把结果发回 parent。

**HITL proxy**：子的 input request 可通过 `subagent-hitl-proxy.ts` 代理到父的 channel adapter——让用户在父 channel 上批准子的请求。

---

## Connection 工具暴露机制

`ConnectionRegistryImpl`（`runtime/connections/registry.ts:14-78`）按 `protocol` 选 `McpConnectionClient` 或 `OpenApiConnectionClient`，懒加载 + 缓存。

Connection 的工具**自动注册成 model-visible tools**（通过 `connection-search` dynamic resolver）：
- `connection__search` —— 搜 operation
- `connection__<conn>__<opId>` —— 调 operation

Auth 缺失时抛 `ConnectionAuthorizationRequiredError` → runtime 捕获 → suspend turn → 显示 "Sign in" → OAuth callback → resume。

---

## Schedule → Nitro task

`createScheduleRegistrations`（`runtime/schedules/register.ts:58-75`）把每个 schedule 转成 Nitro task：
- `taskName = "eve.schedule." + sourceId`
- 注册到 Nitro 的 cron 任务系统

dev 触发：`POST /eve/v1/dev/schedules/:scheduleId`（`protocol/routes.ts:50`，仅 dev 挂载）。

---

## 关键代码位置速查

| 关注点 | 文件:行 |
|---|---|
| Discovery 入口 | `src/discover/discover-agent.ts:58` |
| 项目根解析 | `src/discover/project.ts:52` |
| ProjectSource 抽象 | `src/discover/project-source.ts:43` |
| Compile 入口 | `src/compiler/compile-agent.ts:62` |
| 产物写入 | `src/compiler/artifacts.ts:201` |
| Manifest schema | `src/compiler/manifest.ts:602` |
| Module map 生成 | `src/compiler/module-map.ts:61` |
| Manifest 加载 | `src/runtime/loaders/manifest.ts:36` |
| Module map 加载 | `src/runtime/loaders/module-map.ts:36` |
| Runtime bundle 缓存 | `src/runtime/sessions/compiled-agent-cache.ts:145` |
| Agent graph 解析 | `src/runtime/resolve-agent-graph.ts:84` |
| Workflow runtime 工厂 | `src/execution/workflow-runtime.ts:78` |
| Workflow 入口（driver） | `src/execution/workflow-entry.ts:76` |
| Driver loop | `src/execution/workflow-entry.ts:155` |
| Turn workflow | `src/execution/turn-workflow.ts:32` |
| Harness step 工厂 | `src/execution/node-step.ts:62` |
| Tool-loop harness | `src/harness/tool-loop.ts:1` |
| Channel dispatch（Nitro） | `src/internal/nitro/routes/channel-dispatch.ts:33` |
| 稳定 HTTP 路由常量 | `src/protocol/routes.ts:5-134` |
| Subagent 注册表 | `src/runtime/subagents/registry.ts:49` |
| Connection 注册表 | `src/runtime/connections/registry.ts:14` |
| Schedule → Nitro task | `src/runtime/schedules/register.ts:58` |

---

## 架构小结

eve 的运行时是一个**精心分层、可检视、durable** 的引擎：

1. **Discovery 与执行隔离**：纯文本扫描 + ProjectSource 抽象，让发现逻辑完全可测
2. **Compile 产物可 audit**：JSON + ESM，git diff 友好
3. **Dev 模式直接 hydrate 源码**：跳过 module-map，改文件即时生效
4. **两层 durable workflow**：driver（session）+ turn（child），各自 checkpoint
5. **Vercel AI SDK 负责模型调用**：eve 不重造 tool loop，专注 durable + channel + 编排
6. **Nitro 提供一切基础设施**：HTTP host + rolldown bundler + cron，单依赖

下一章 `06-durability-and-state.md` 详述 durable 语义。
