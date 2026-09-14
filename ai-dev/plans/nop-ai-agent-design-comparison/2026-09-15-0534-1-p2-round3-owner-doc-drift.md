---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-ROUND3-DOCS
group: "2026-09-15-0534"
verify: [test]
---

# P2 round-3 owner-doc 契约漂移与锚点修复（8 项：react-engine / execution-model / security / channel-connector / reliability / session-and-storage / tool-invocation 等）

## Current Baseline

- 来源：deep-audit round 3 登记 P2 Follow-up Backlog 中 8 项未勾选的 owner-doc 漂移项（roadmap `## Follow-up Backlog` 文档顺序，react-engine.md 3 项 + 锚点类 4 项 + 计数/表述类 2 项），全部经 live repo 复核（HEAD `51544255b8`，2026-09-15）：
  1. **react-engine.md §9 Actor 状态表列 `cancelling` 状态在代码中不存在**（`ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md:302/:313-316`）：`AgentActorStatus`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/AgentActorStatus.java`）仅 7 值（`CREATED`/`READY`/`RUNNING`/`IDLE`/`FAILED`/`RECOVERING`/`STOPPED`），两级取消经 ctx 标志 + interrupt 实现（`DefaultAgentEngine.cancelSession` :565 已实现）。§9 末行 :317 声称 "IAgentEngine.cancelSession ... 已在 Phase 1 作为 default UOE 预留" 已过时——`IAgentEngine.java:49-51` 现抛 `ERR_AGENT_CANCEL_SESSION_NOT_SUPPORTED`，`DefaultAgentEngine:565` 已实现 cancelSession。
  2. **react-engine.md §3.2 IAgentEngine 接口片段与 live 不符**（`ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md:37-113`）：接口片段把 `execute` 写成 default 方法（:93），且称 sendMessage/followUp 消息模型；实际 `IAgentEngine.java:15` `execute` 是抽象方法，default 方法现抛 `NopAiAgentException(ERR_AGENT_*_NOT_SUPPORTED)`，接口已新增文档未列的 `resumeSession`/`restoreSession`/`wakeSession`/`restorePendingSessions`/`close`（:79-240）。
  3. **02-execution-model.md §5.1 Hook 生命周期清单漏 2 个已实现重入点**（`ai-dev/design/nop-ai-agent/02-execution-model.md:89-107`）：§5.1 只列 Layer 1 五点 + Layer 2 五点，漏 `BEFORE_TOOL_RESULT_PROCESSED`/`AFTER_TOOL_RESULT_PROCESSED`；`AgentLifecyclePoint` 枚举共 12 值（`hook/AgentLifecyclePoint.java:4-15`），`AgentToolDispatcher.java:390/:462` 实际执行两点（re-entry 计数 :393-398/:465-470）；react-engine.md §8 自称"核心 7 点"回指该清单，两份文档枚举互相矛盾。
  4. **security-and-permissions.md:616 沙箱段落行号锚点全部失效**（`nop-ai-agent-security-and-permissions.md:616`）：引用 `DefaultAgentEngine.java:346-347/:1520`、`ReActAgentExecutor.java:3114/:299`——两文件现分别为 1045/1418 行，`:1520`/`:3114` 超文件长度；实质结论（`SandboxRequest` 无生产构造点、`ISandboxBackend.execute` 从未被调用）仍成立但锚点不可追溯。
  5. **channel-connector.md:169 §7.2 事件 payload 锚点漂移**（`nop-ai-agent-channel-connector.md:169`）：`EXECUTION_COMPLETED` payload 构建现于 `ReActAgentExecutor.java:1334-1340`（原 :1015-1027）、`LLM_RESPONSE_RECEIVED` 的 `hasToolCalls` 现于 :973（原 :754-757）；实质裁定（payload 不含响应文本）仍正确。
  6. **reliability.md §13 WAIT_FOR/idempotency_key 锚点集体漂移**（`nop-ai-agent-reliability.md:712/:719/:729/:735` 等）：`resumeSession` 已迁 `AgentSessionLifecycle`（:954 委托）、`Checkpoint.computeIdempotencyKey` 现 :210、`:920`/`:425-428`/`resumeSession:248-252`/`Checkpoint:187-189` 等 7 处失效；功能本身（waiting 状态/WAIT_FOR/idempotencyKey/wakeSession 门禁）复核全部存在（live 复核：`AgentSessionLifecycle.java:245-263` 与 `:414` 的 status 置 running、`IWaitCoordinator`/`wakeSession` 契约在 `IAgentEngine.java:145`）。
  7. **session-and-storage.md §16.1 "6 张表"计数缺 `nop_ai_channel_session`**（`nop-ai-agent-session-and-storage.md:390-401`）：orm.xml（`nop-ai/model/nop-ai.orm.xml`）现 7 张 agent 运行时相关表——6 张既有 + `NopAiChannelSession`（:1418-1420，`nop_ai_channel_session`），channel 映射表已被 `ChannelSessionStoreImpl` 消费。
  8. **04-tool-invocation.md/01-architecture-baseline.md/context-model.md 低价值锚点/表述漂移**：`04-tool-invocation.md:72` 称 `AskOracleExecutor` 99 行（live 91 行）；`01-architecture-baseline.md:75` 称 `DefaultAgentEngine.buildBaseExecutionContext`（live 已迁 `AgentSessionLifecycle.java:138`）、`:146` mailbox 调用模型未提 plan 224 async 落地；`context-model.md:207` 称 `IToolExecuteContext` 实现 22 处（live 24 处，含生产 + 测试 mock）。
- 归属：全部为 `ai-dev/design/nop-ai-agent/` 下 owner doc 修订，零代码变更。
- 验证面：纯文档计划。完成判定以"锚点可解析 + 表述与 live 一致 + check-doc-links 0 error"为准。

## Goals

- react-engine.md §9 Actor 状态表与 §3.2 IAgentEngine 接口片段与 live 代码一致（7 值状态机、两级取消经 ctx 标志/interrupt、`execute` 抽象 + default 抛 `ERR_AGENT_*_NOT_SUPPORTED`、新增 5 个 session 生命周期 API）。
- 02-execution-model.md §5.1 Hook 生命周期清单补全 12 点（含 2 个 TOOL_RESULT_PROCESSED 重入点），与 `AgentLifecyclePoint` 枚举、react-engine.md §8 一致。
- 4 处失效行号锚点（security-and-permissions:616、channel-connector:169、reliability §13、04-tool-invocation:72/01-architecture-baseline:75/context-model:207）重钉至 live HEAD；3 处计数/归属表述（6→7 张表、22→24 实现、99→91 行）与 live 一致。
- `node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不修改任何 `nop-ai/**` 源码（纯文档计划；代码行为事实以 live 为准，仅同步文档表述）。
- 不处置本轮 roadmap 其余未勾选项（round-3 P2 测试反模式/弱断言、round-4 P2/P3 项，另开计划）。
- 不重写设计决策内容（只修事实性漂移：锚点、计数、接口形态、状态词表；不改变任何设计裁定）。
- 不运行 mvn 构建（无代码变更）。

## Phase 1 — react-engine.md 契约面漂移（§9 Actor 状态表 + §3.2 IAgentEngine 接口片段）

Status: planned

Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`

- Item Types: `Fix | Proof`

- [ ] `Fix` §9 Actor 状态表：删除/改写 `cancelling` 行——`AgentActorStatus` 7 值（`CREATED`/`READY`/`RUNNING`/`IDLE`/`FAILED`/`RECOVERING`/`STOPPED`）为权威；两级取消（graceful/forced）改写为经 `AgentExecutionContext` 取消标志 + `ICancelToken.interrupt` 实现的过渡行为（非独立状态），与 `01-architecture-baseline.md` actor 段现有 7 值表述一致。
- [ ] `Fix` §9 末行 "cancelSession 作为 default UOE 预留"：改写为 live 语义——`IAgentEngine.cancelSession` default 抛 `ERR_AGENT_CANCEL_SESSION_NOT_SUPPORTED`，`DefaultAgentEngine:565` 已实现（两级取消经 ctx 标志 + interrupt）。
- [ ] `Fix` §3.2 IAgentEngine 接口片段：`execute` 改为抽象方法（非 default UOE）；default 方法统一描述为抛 `NopAiAgentException(ERR_AGENT_*_NOT_SUPPORTED)`；补 `resumeSession`/`restoreSession`/`wakeSession`/`restorePendingSessions`/`close` 五个已实现 API（用途一句话 + 锚点 `IAgentEngine.java` 方法行）；sendMessage/followUp 消息模型保留但标注与 `execute` 的关系（如有冲突按 live 代码为准）。
- [ ] `Proof` 复核：`grep -c` `AgentActorStatus` 枚举值 = 7；`IAgentEngine.java` 抽象/default 方法清单与文档一致；`cancelSession` 锚点 `DefaultAgentEngine.java:565` 可解析。

Exit Criteria:

- [ ] §9 状态表不再含 `cancelling` 独立状态行，两级取消语义与 live（ctx 标志 + interrupt）一致。
- [ ] §3.2 接口片段与 `IAgentEngine.java` live 形态一致（抽象 execute + default 抛错 + 5 个生命周期 API 已列）。
- [ ] 全部新锚点（类名 + 行号）可在仓库解析（抽查 ≥5 个）。
- [ ] **端到端验证**（不适用）：纯文档修订，无运行时路径。
- [ ] **接线验证**（不适用）：无新组件协作。
- [ ] **无静默跳过**（不适用）：无代码行为变更。
- [ ] No new test required: 纯文档修订（Minimum Rules #25）。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — Hook 生命周期清单补全（02-execution-model.md §5.1）+ 跨文档一致性

Status: planned

Targets: `ai-dev/design/nop-ai-agent/02-execution-model.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`（§8 回指句）

- Item Types: `Fix | Proof`

- [ ] `Fix` §5.1 清单补全：Layer 2 扩展新增 `BEFORE_TOOL_RESULT_PROCESSED`/`AFTER_TOOL_RESULT_PROCESSED` 两行（触发时机 = 工具结果处理后、允许 ReAct 重入，锚点 `AgentToolDispatcher.java:390/:462` + re-entry 计数 :393-398/:465-470），并注明与 §5.1 既有"Layer 1 核心 5 点 + Layer 2 扩展 5 点"的分层口径如何扩展为 12 点。
- [ ] `Fix` react-engine.md §8 回指句："核心 7 点"表述与补全后的 12 点清单一致（或明确 7 点指 Layer 1 核心子集），消除两份文档枚举矛盾。
- [ ] `Proof` 复核：`AgentLifecyclePoint.java:4-15` 12 个枚举值 ↔ §5.1 表格行数 12 行；`grep -rn "TOOL_RESULT_PROCESSED"` agent main 命中执行点（AgentToolDispatcher 两处 + 枚举 + hook 注册）。

Exit Criteria:

- [ ] §5.1 清单覆盖全部 12 个 `AgentLifecyclePoint`（含 2 个 TOOL_RESULT_PROCESSED 重入点），每行可经锚点解析。
- [ ] react-engine.md §8 与 02-execution-model.md §5.1 的枚举口径一致（不再互相矛盾）。
- [ ] **端到端验证**（不适用）：纯文档修订。
- [ ] **接线验证**（不适用）：无新组件协作。
- [ ] **无静默跳过**（不适用）：无代码行为变更。
- [ ] No new test required: 纯文档修订。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — 失效锚点重钉 + 计数/表述修正（security / channel-connector / reliability / tool-invocation / architecture-baseline / context-model / session-and-storage）

Status: planned

Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`、`nop-ai-agent-channel-connector.md`、`nop-ai-agent-reliability.md`、`04-tool-invocation.md`、`01-architecture-baseline.md`、`nop-ai-agent-context-model.md`、`nop-ai-agent-session-and-storage.md`

- Item Types: `Fix | Proof`

- [ ] `Fix` security-and-permissions.md:616 沙箱段落：`DefaultAgentEngine.java:346-347` → 现沙箱默认装配锚点（`DefaultAgentEngine` 现 1045 行，Builder 字段 :161 + setter :456 + 接线 :257），删除 `:1520`/`ReActAgentExecutor.java:3114/:299` 超长锚点或重钉至真实位置（`ReActAgentExecutor.java:166/:219/:244-245` 持有字段，`:342` getter）；实质结论（无生产构造点、execute 零调用）保持不变。
- [ ] `Fix` channel-connector.md:169：payload 锚点重钉 `ReActAgentExecutor.java:1334-1340`（EXECUTION_COMPLETED metrics）与 `:973`（LLM_RESPONSE_RECEIVED hasToolCalls）；实质裁定（payload 不含响应文本）文字不变。
- [ ] `Fix` reliability.md §13：`resumeSession:248-252` 等 7 处失效锚点重钉——resumeSession/wakeSession 现于 `AgentSessionLifecycle.java`（:245-263 状态前置、:301/:435 tryAcquire、:380/:489 save），`Checkpoint.computeIdempotencyKey` :210，`:920` 完成 future 的调用链改指 `IAgentEngine.execute` 现位置；功能语义段落（waiting/WAIT_FOR/idempotencyKey/wakeSession 门禁）复核一致后保留。
- [ ] `Fix` session-and-storage.md §16.1："6 张表" → 7 张（新增 `nop_ai_channel_session` 行：职责 = 信道会话映射，写入频率按 live 消费面，锚点 `nop-ai/model/nop-ai.orm.xml:1418-1420` + `ChannelSessionStoreImpl`）。
- [ ] `Fix` 04-tool-invocation.md:72：`AskOracleExecutor` 99 → 91 行（live `wc -l`）；01-architecture-baseline.md:75：`DefaultAgentEngine.buildBaseExecutionContext` → `AgentSessionLifecycle.buildBaseExecutionContext`（:138），`:146` mailbox 调用模型补 plan 224 async 落地状态（或标注现行实现位置）；context-model.md:207：22 → 24 处 `IToolExecuteContext` 实现（按 live grep 计数，含生产 + 测试 mock）。
- [ ] `Proof` 复核：全部重钉锚点 `grep -n` 可解析（每份文档抽查 ≥3）；`wc -l` 计数与文档一致；`IToolExecuteContext` 实现计数按 live `grep -rln "implements IToolExecuteContext"` 复核；`nop_ai_channel_session` 表 + 消费类存在。

Exit Criteria:

- [ ] 6 份文档的失效锚点全部重钉至 live HEAD，无一残留超长/失效行号（抽查可解析）。
- [ ] 3 处计数（7 张表 / 24 处实现 / 91 行）与 live 一致。
- [ ] 实质结论/裁定文字未被改写（仅锚点与计数修订）。
- [ ] **端到端验证**（不适用）：纯文档修订。
- [ ] **接线验证**（不适用）：无新组件协作。
- [ ] **无静默跳过**（不适用）：无代码行为变更。
- [ ] No new test required: 纯文档修订。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0534-1-p2-round3-owner-doc-drift-1-7b81c365 to opencode-pid-49943
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0534-1-p2-round3-owner-doc-drift-1-7b81c365

## Verification

（空，由 BUILD_VERIFY 填写）

## Closure

（空，由 CLOSURE_AUDIT 填写）