# 06 · Durable 执行与状态管理

> 来源：`docs/concepts/execution-model-and-durability.md` + `docs/concepts/sessions-runs-and-streaming.md` + `docs/guides/state.md`
> 这是 eve 与传统 agent 框架最大的差异化点。

## 核心命题

> 「An eve session is a durable conversation. It can run for days and survives process restarts and redeploys without any work on your part.」
> —— `docs/concepts/execution-model-and-durability.md:6`

> 「There's nothing to configure. eve owns the workflow lifecycle, and sessions are durable by default.」
> —— 同上:43

## 三层嵌套模型

```
session  ──────────────────────────────────────────────────────►  (可跨天/跨周)
  │  durable workflow run (workflowEntry)
  │
  ├─ turn 1  ─────────────────────►  (一条用户消息触发的全部工作)
  │    │  child workflow run (turnWorkflow)
  │    │
  │    ├─ step 1  ──────────►  (一次模型调用 + 其工具调用)  ← durable checkpoint
  │    ├─ step 2
  │    └─ step 3
  │       └─ [park: HITL/OAuth/subagent]  ── 不占计算，等到输入到来再恢复
  │
  ├─ turn 2
  └─ turn 3
```

| 层 | 定义 | workflow id |
|---|---|---|
| **session** | 整段 durable 对话/任务，可跨天/跨周 | `workflow//eve//workflowEntry` |
| **turn** | 一条用户消息及其触发的全部工作（模型调用 + 工具调用 + 推理），到 agent 产生回复 | `workflow//eve//turnWorkflow` |
| **step** | turn 内的 durable checkpoint（一次模型调用 + 其工具调用） | harness step |

## 底层引擎

**Workflow DevKit**（开源，`workflow-sdk.dev`）：
- 在 Vercel 上跑 **Vercel Workflow**
- 本地或 `eve start` 用 SDK 的 **local world**（持久化在 `.workflow-data/`）
- 可通过 `experimental.workflow.world` 换 Postgres world 等

提供的原语：
- `start(workflowRef, args)` —— 启动 durable workflow run
- `resumeHook(token, payload)` —— 唤醒 parked workflow
- `createHook({ token })` —— 注册回调点
- `getWritable<Uint8Array>()` —— workflow 级事件流写入端
- `getRun(runId).getReadable()` —— 读取事件流
- `"use workflow"` / `"use step"` directive —— 被 `workflow-transformer.ts` 在 bundle 时改写

## 六大关键性质

### 1. 崩溃恢复

进程崩、超时、redeploy 中途——run 从**「最后完成的 step」恢复**，**不重放整个 turn**。

- **已完成的 step 绝不重跑**（只 replay 结果）
- 被中断的 step 会重跑 → 所以**非幂等的副作用要么幂等化，要么用 `needsApproval` 闸住**

（`execution-model-and-durability.md:39-43`）

### 2. Parked work（不占计算）

HITL 审批、OAuth 登录、长跑子代理——turn 在此 **durable 挂起**：
- **不占计算资源**
- 等到输入到来再恢复
- 进程重启不影响

这是「durable」最直观的价值：agent 可以「等」人/等外部事件，而不需要你搭队列/状态机。

（`execution-model-and-durability.md:47-49`）

### 3. 历史 append-only

session 内的 turn 与其中的 tool 调用都按发生顺序排列。

（`execution-model-and-durability.md:63-65`）

### 4. 流可重连/回放

每个事件在 step 完成前已落盘。
- `?startIndex=<count>` 可按事件序号断点续传
- 或倒回开头重放

（`sessions-runs-and-streaming.md:89-95`）

### 5. defineState（session 级状态）

```ts
import { defineState } from "eve/context";

const counter = defineState("counter", () => 0);
// 在 tool / hook 里：
counter.update((n) => n + 1);
const current = counter.get();
```

- 按 session 命名的 durable 内存槽
- 跨 step 边界存活
- **子代理永不继承父的状态**（隔离）

（`docs/guides/state.md:6-68`）

### 6. 跨 deployment 路由

`deploymentId: "latest"` 让 session 路由到最新部署——重新部署后老 session 自动用新代码继续。

## 两个句柄（务必分清）

| 句柄 | 拥有者 | 含义 |
|---|---|---|
| **`continuationToken`** | channel | 「下一条消息」恢复句柄——投递后续消息用 |
| **`sessionId` / `runId`** | runtime | 「事件流 + 观察」句柄（= workflow runId） |

两者**解耦**：channel 可以重新 key（比如 Slack 频道换了）而 session 持久。

```bash
# 起一个 durable session
curl -X POST http://127.0.0.1:3000/eve/v1/session \
  -H 'content-type: application/json' \
  -d '{"message":"What is the weather?"}'
# 返回 body 含 continuationToken；header x-eve-session-id 是 sessionId

# 流式订阅
curl http://127.0.0.1:3000/eve/v1/session/<sessionId>/stream   # NDJSON

# 用 continuationToken 发后续消息
curl -X POST http://127.0.0.1:3000/eve/v1/session/<sessionId> \
  -H 'content-type: application/json' \
  -d '{"continuationToken":"<token>","message":"Now do Queens."}'
```

## 消息投递语义（重要坑！）

> eve **不**为 session 维护 durable FIFO 用户消息队列。

`continuationToken` 只是「当前 workflow hook」的恢复句柄。
- 并发发给同一 session **不保证**像 chat 队列那样有序
- 要做确定性顺序：**一次只发一条**，等 `session.waiting` 再发下一条

（`execution-model-and-durability.md:51-57`）

## 流事件类型

来自 `src/protocol/message.ts`：

| 类别 | 事件 |
|---|---|
| session 生命周期 | `session.started` / `session.failed` / `session.completed` |
| turn 生命周期 | `turn.started` / `turn.completed` / `turn.failed` |
| 消息 | `message.appended` / `message.completed` |
| 推理 | `reasoning.appended` / `reasoning.*` |
| 工具 | `action.result` / `actions.requested` |
| HITL | `input.requested` |
| OAuth | `authorization.required` / `authorization.completed` |
| 上下文压缩 | `compaction.*` |

这些事件是 **durable 落盘**的（在 step 完成前已写入），所以才能重连/回放。

## HITL（Human-in-the-loop）模式

### Tool approval

```ts
defineTool({
  needsApproval: never() | once() | always() | ((ctx) => boolean),
  // ...
});
```

| 模式 | 行为 |
|---|---|
| `never()` 或不设 | 直接执行 |
| `once()` | session 内首次批准后免批；拒绝不留记录，下次再问 |
| `always()` | 每次都问 |
| 自定义 `(ctx) => boolean` | 基于 `approvedTools` / `toolInput` / `toolName` 决策 |

approval 触发时，turn **durable park**，emit `input.requested` 事件，等用户响应后 resume。

### ask_question（框架工具）

模型调用 `ask_question` 工具：
- `display: "select"` —— 提供选项
- `display: "confirmation"` —— 确认对话框
- 用 `t.respondAll(optionId)` 响应

## Subagent 与 durable 的关系

- subagent 是父 agent 的一个 tool（delegated child workflow run）
- 子的 HITL 请求可通过 `subagent-hitl-proxy.ts` 代理到父 channel
- 父 agent 通过 `subagent.called` / `subagent.completed` 事件感知委派
- 子跑完后通过 `notifyDelegatedParentStep` 把结果发回父

## 与传统状态管理的对比

| | 传统（Redis/DB + 自己搭状态机） | eve |
|---|---|---|
| 配置 | 大量 | **零配置**（durable by default） |
| checkpoint | 手动 | **自动**（每个 step） |
| 崩溃恢复 | 自己实现 | **框架内置** |
| park/resume | 自己搭队列 | **内建**（HITL/OAuth/subagent） |
| 跨重启 | 自己持久化 | **`.workflow-data/` 或 Vercel Workflow** |
| 事件回放 | 自己存 | **durable event stream** |

## 工程启示

eve 的 durable 模型解决了 agent 框架的几个老大难：
1. **长任务**：不再需要自己管「agent 等人」的状态
2. **崩溃恢复**：进程随便重启，session 不丢
3. **重新部署**：老 session 自动迁移到新代码
4. **可观察性**：durable event stream 天然支持重连/回放

代价：
1. **非幂等副作用要小心**——被中断的 step 会重跑
2. **消息无 FIFO 队列**——要自己控制并发投递
3. **强绑定 workflow 引擎**——离开 Vercel Workflow 要切 local world / Postgres world

这是 eve「durable backend agent」定位的核心工程支撑，也是它与 LangChain/Autogen 等「一次 request/response」框架的本质差异。
