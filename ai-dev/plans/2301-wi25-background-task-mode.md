# 2301 WI25 工具级后台模式 runInBackground（refactor roadmap M5）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI25（Deliverable: 代码变更 + 测试；deps: WI2 已完成；软依赖 WI5 已完成；zcode 比对 2026-09-28 + 用户裁定）
> Design Contract: `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md` §三.7（形态边界权威）+ Owner `ai-dev/design/nop-ai-agent/04-tool-invocation.md`
> Cross-Cutting: 形态边界由 WI2 裁定⑤收口（本文不重裁）；zcode 参照 `~/ai/zcode` @ 29628c9
> Adversarial Review: round 1 子代理 agent_19ed8be0——1 Blocker + 7 Major 已全部吸收；round 2 子代理 agent_5542f8b5——PASS 可执行（全部修法在 live code 有真实落点与先例），2 cosmetic（In Scope 补 nop-xdefs；m6/M6 编号去歧义）已落实

## Purpose

第三条长任务路线（zcode 已验证）：工具参数 `runInBackground: true`（camelCase 对齐 timeoutMs/workingDir）时立即返回启动确认（taskId + 输出指引），真实结果经完成通知桥入 mailbox 作为新输入驱动下一轮；配套跨调用可寻址后台任务 registry（状态/输出/进度快照）、任务管理工具面（输出读取 + 停止）、恰好一次 claim 防双发、后台超时独立分层；**修复 nop-ai-shell `&` 启动即失联陷阱**（ShellCommandExecutor.java:346-361）。

## Current Baseline

（执行时 HEAD `8ea8c307b2`，2026-10-03）

- 形态边界（§三.7 权威已裁定）：开放面 = bash/shell 类长时工具（tool.xdef tags 声明 `background` 能力 tag）；不开放 = call-agent（member spawn 已有异步编排面，双通道治理面无增量）与文件读写类（毫秒级无后台语义）。
- 完成通知通道：`DefaultAgentEngine.injectInput(sessionId, message, semantics)`（DefaultAgentEngine.java:686，WI8 两分支——running 时入 live ctx 队列 STEER 唤醒 wait phase；非 running 时 staging 持久化下轮合并）——完成通知走 FOLLOWUP 语义注入，**不是第三唤醒源**（经既有 mailbox/steering 通道，裁定不破）。
- 进度证据接缝：`IToolProgressTracker`（tool/IToolProgressTracker.java:18，WI4 落地，IBashSandbox callId 寻址登记）——registry 快照复用；无实现时显式空值=证据不可用。
- `&` 陷阱：`ShellCommandExecutor.executeBackground`（nop-ai-shell/.../executor/ShellCommandExecutor.java:346-361）——jobId 实例内 AtomicLong 计数、backgroundJobs 实例 Map、无查询/等待/取消/输出回收，启动即失联（:359 返回 "[jobId] running in background" 后随流丢弃）。模块边界：nop-ai-shell 不依赖 nop-ai-agent（sink 注入方向必须 shell←agent）。
- 引擎可达性：`AgentToolExecuteContext.getEngine()`（:346，engine-aware 工具 casting 先例 = call-agent）。
- **toolkit 接缝先例（round 1 B1）**：`IToolExecuteContext.getCompactionArchiveReader()` default-UOE bridge（IToolExecuteContext.java:86-91，注释明说 "toolkit cannot import the agent module"）——registry 访问同构：接口加 default 方法抛 UOE，AgentToolExecuteContext override。
- **潜伏缺陷（round 1 M6，本项顺带修复）**：`prepareDispatchContext`（AgentToolDispatcher.java:547-564）向 context 传 executor=null，而 BashExecutor.executeAsync 直接 `context.getExecutor().submit(...)`（:86）——engine 派发路径下真 BashExecutor 即 NPE（无集成测试覆盖故潜伏）；后台分支亦需专用执行器（1h 级任务不得占前台池）。
- **通知边界（round 1 M3）**：FOLLOWUP 注入不唤醒——running 会话 wait-phase 探针仅响应 STEER（AgentExecutionContext.java:86）；非 running 仅 staging 持久化（DefaultAgentEngine.java:703-706），无自动 execute，需下一次 engine.execute 在 buildBaseExecutionContext 合并；会话终态后通知滞留 staged。
- zcode 参照：taskId 启动即返（background-tasks.ts:818-824）、1s 轮询快照 tail+bytes（:440-448）、完成通知 claim 恰好一次（background-task-registry.ts:168-181）、TaskOutput 双结果通道已废弃教训（收敛单一通道）。

## Goals

- **`IBackgroundTaskRegistry`（nop-ai-toolkit api 接口）+ `DefaultBackgroundTaskRegistry`（nop-ai-agent 实现）**：submit→taskId；条目 {taskId, toolName, sessionId, callId, submitTime, timeoutMs, status RUNNING/COMPLETED/FAILED/TIMEOUT/NOTIFICATION_FAILED, 输出 tail 缓冲（有界 8KB）+ 总字节数, notificationSent claim}；snapshot 返回 toolkit 局部 `TaskSnapshot`（状态+tail+bytes——不引用 agent 类型）；stop(taskId)（**优先 IToolProgressTracker.cancelExecution(callId) 进程 kill 通道，tracker 缺席降级 future.cancel**——对齐 design §三.6）；complete 幂等。全局单例语义（跨调用可寻址），DefaultAgentEngine Builder 创建、经 dispatcher.prepareDispatchContext 流入 context。
- **完成通知桥**：terminal 状态时若 !notificationSent → 构造 `[BACKGROUND_TASK taskId=... status=... tail 预览]` ChatUserMessage → injectInput(FOLLOWUP) → 成功后 notificationSent=true（恰好一次）。**失败处置（round 1 M2 具体化）**：injectInput 抛异常 → 状态迁 NOTIFICATION_FAILED（snapshot 可见，fail-visible）+ **读取补投**——task-output/snapshot 读取 NOTIFICATION_FAILED 任务时重试注入一次（惰性补投，无悬空重试器）；**边界如实登记**：FOLLOWUP 不唤醒 idle/终态会话（非 running 仅 staging，待下次 execute 合并；终态会话通知滞留 staged 为 Known Residual）。
- **bash `runInBackground`**：bash.tool.xml schema 增可选参数 `runInBackground="boolean"` 与 `backgroundTaskTimeoutMs="long"`；**能力声明用 tool.xdef 专用元素 `<background-capable>!boolean=false</background-capable>`（不用 tags——tags 是可见性过滤机制，加 tag 会使 activeTags 非空且不含该 tag 的 agent 隐藏 bash，回归；round 1 M5）**；BashExecutor 检测参数置真时——registry 经 `IToolExecuteContext.getBackgroundTaskRegistry()` default-UOE 接缝获取（round 1 B1）→ **后台专用执行器 fork 真实 bash（不占前台池）+ wallSeconds = clamp(per-call backgroundTaskTimeoutMs，cap 3_600_000)**（round 1 M4：防前台 30s 沙箱超时杀掉 1h 后台语义），registry.submit → 立即返回确认；registry 不可达 → 显式报错（fail-loud）。**顺带修复 M6 潜伏 NPE**：prepareDispatchContext 装配 executor（engine Builder 持有 foreground/background 两执行器，background 池供后台 fork）。subagent 强制 max runtime 不适用（call-agent 不开放）。
- **任务管理工具**：`task-output`（taskId → 状态+tail+bytes，幂等读取；任务不存在 fail-loud）+ `task-stop`（taskId → 取消；已终态幂等报状态）——**`meta=true`（read-spill 先例，避免 activeTags 过滤的 agent 在需要时看不到）**；**跨会话控制（round 1 M7）：同 session 默认 + 跨 session fail-loud（read-spill 同款安全默认）**。claim 口径收窄（round 1 minor-6）：恰好一次指完成通知；task-output 幂等可重复读。
- **`&` 闭环**：nop-ai-shell 新 SPI `IBackgroundJobSink`（shell←agent 方向注入）：sink 在位时 `&` 经 sink 提交（返回 taskId 闭环：可 snapshot/stop/完成通知）；sink 缺席 → `&` 显式报错（fail-loud 快速失败，不维持失联现状）。agent 侧装配 sink 适配器（engine 装配点注册到 shell executor 工厂）。
- **测试**：registry 状态机/claim 恰好一次/通知桥注入语义/超时分层/bash 后台提交与立即返回/task 工具/`&` 闭环与缺席 fail-loud/零回归。

## Non-Goals

- call-agent/文件类工具后台化（形态边界裁定排除）。
- 双结果查询通道（zcode TaskOutput 废弃教训——单一通道：通知携带 taskId，内容经 task-output）。
- 输出落盘文件路径（registry 内存 tail 有界缓冲足够；文件 offload 为 follow-up）。
- nop-stream/nop-ai-shell 以外的 shell 面。

## Scope

### In Scope

- nop-kernel/nop-xdefs（tool.xdef 编辑落点）、nop-ai-toolkit：IBackgroundTaskRegistry 接口、BashExecutor runInBackground、bash.tool.xml。
- nop-ai-agent：DefaultBackgroundTaskRegistry + 通知桥 + AgentToolExecuteContext 访问器 + task-output/task-stop 工具 + shell sink 适配器。
- nop-ai-shell：IBackgroundJobSink SPI + `&` 改造（sink 提交或 fail-loud）。
- 测试 + owner doc + log。

### Out Of Scope

- WI23 流式；溢出恢复环（autonomous WI5，唤醒轮复核条款已有）；输出文件持久化。

## Execution Plan

### Phase 1 - registry + 通知桥（核心状态机）

Status: completed
Targets: nop-ai-toolkit（接口）、nop-ai-agent（DefaultBackgroundTaskRegistry、桥）

- Item Types: `Feature`

- [x] `IToolExecuteContext` 加 `default getBackgroundTaskRegistry()`（UOE 语义，getCompactionArchiveReader 同构——round 1 B1）；接口含 toolkit 局部 TaskSnapshot
- [x] `DefaultBackgroundTaskRegistry`（状态机含 NOTIFICATION_FAILED、有界 tail、notificationSent claim、**tracker 优先 stop**、幂等 complete、NOTIFICATION_FAILED 读取补投）+ 通知桥（injectInput FOLLOWUP，成功才置 claim）
- [x] `AgentToolExecuteContext` override + DefaultAgentEngine Builder 持有 registry 与双执行器（foreground/background）→ prepareDispatchContext 传入（**顺带修复 executor=null 潜伏 NPE**，round 1 M6；执行落地：适配器提为独立类 `AgentDispatchExecutor`——匿名类内 `execute(Runnable)` 方法名会误触 gate-2 编排入口机械派生正则）
- [x] 单测：状态机全迁移/claim 恰好一次/NOTIFICATION_FAILED 补投/stop tracker 优先与降级/快照/桥注入断言（mock engine）/executor 供给后前台 bash 不再 NPE

Exit Criteria:

- [x] 单测全绿；**接线验证**：bridge→injectInput 真实调用（mock engine 断言即证明——`TestBackgroundCompletionBridge.terminalTaskBridgesIntoSessionPendingInjections` 断言通知落 session pendingInjections）
- [x] **无静默跳过**：通知失败显式传播（重试语义），无空方法体（执行发现并修复 bridge↔snapshot 惰性补投无限递归 bug——bridge 改读 Entry 字段直取）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（3524 全绿，2026-10-03）

### Phase 2 - bash 后台提交 + 任务工具

Status: completed
Targets: nop-ai-toolkit（BashExecutor、bash.tool.xml）、nop-ai-agent（task-output/task-stop）

- Item Types: `Feature`

- [x] tool.xdef `<background-capable>` 元素 + bash.tool.xml（runInBackground/backgroundTaskTimeoutMs 参数，不加 tag——round 1 M5）；BashExecutor 后台分支（UOE 接缝获取 registry fail-loud、专用 background 执行器 fork、wallSeconds=clamp(backgroundTaskTimeoutMs)）
- [x] `task-output`/`task-stop` 工具（tool.xdef 声明 meta=true + executor + beans 注册；同 session 默认跨 session fail-loud）
- [x] 单测：后台提交立即返回/完成通知含 taskId/超时 TIMEOUT 迁移通知/task-output claim 读取/task-stop 取消与幂等/registry 缺席 fail-loud/端到端（engine 装配下后台 bash→完成→通知入 session→task-output 读取）

Exit Criteria:

- [x] 测试全绿含端到端（TestBackgroundTaskEndToEnd：engine 装配→bash 后台→完成→通知 staged→下轮合并→task-output 读取全链路）；`./mvnw test -pl nop-ai/nop-ai-agent -am` + toolkit 全绿（3531 + 560，2026-10-03）
- [x] **接线验证**：bash→registry→bridge→injectInput→session 输入链真实连通（端到端断言：通知与工具结果同现于最终 LLM 请求）
- [x] **无静默跳过**：registry 不可达 fail-loud；工具未知名解析 fail-loud（WI13 先例）；执行中按 CallAgentExecutor 先例补**双源参数解析**（engine 派发路径 input JSON、node 恒 null——存量缺陷顺带修复：bash/write-file 类经 engine 派发时读不到参数；chokepoint runInBackground 检查同改双源，ToolPathAffinity 先例）
- [x] `ai-dev/logs/` 条目更新（owner doc 归 Phase 4 显式归属）

### Phase 3 - shell `&` 闭环

Status: completed
Targets: nop-ai-shell（SPI、ShellCommandExecutor）、nop-ai-agent（sink 适配器）

- Item Types: `Fix`（既有 live defect：启动即失联）

- [x] `IBackgroundJobSink` SPI；ShellCommandExecutor `&` 分支改造（sink 在位提交/缺席 fail-loud）
- [x] agent 侧 sink 适配器（**test-wiring only——round 1 M1：nop-ai-shell 为孤儿模块（无生产消费者），生产消费者缺席登记 residual**；落地为 agent test 源 `BackgroundRegistryJobSink` + nop-ai-shell test-scope 依赖 + bom 登记）
- [x] 单测：shell 侧 sink 在位闭环（taskId 可 snapshot/stop）/缺席 fail-loud（6 个旧 `&` 测试按新契约重写 + RecordingBackgroundJobSink 桩 + agent 侧 TestBackgroundRegistryJobSink 2 项）

Exit Criteria:

- [x] 测试全绿；`./mvnw test -pl nop-ai/nop-ai-shell -am`（聚合命令）通过（shell 295 + toolkit 560 + agent 3533 全绿，2026-10-03）
- [x] **接线验证**：sink 在位时 `&` 返回可寻址 taskId（确认携带 `[taskId=...]`，sink 侧完成捕获 exit code/输出）
- [x] `ai-dev/logs/` 条目更新

### Phase 4 - owner doc 与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`（§4.5 落地同步）、log、roadmap

- Item Types: `Proof`

- [x] owner doc 更新（§4.5 **契约改写**：输出为内存有界 tail 而非设计初稿的"输出文件路径"——显式偏差裁定；开放面用 background-capable 元素；通知边界与 residuals；同步 `nop-ai-agent-async-tool-execution.md` §三.7 两处被推翻表述）

Exit Criteria:

- [x] owner doc 在位且与 live 一致；全部验证门通过

## Closure Gates

- [x] 后台模式端到端连通（bash 后台提交→registry→完成通知注入→task-output 读取；`&` 闭环）——TestBackgroundTaskEndToEnd 全链断言 + shell sink 测试
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_e815af98，APPROVED：0 Blocker / 0 Major / 5 Minor 非阻塞）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（toolkit+agent 3793 全绿；shell 面 295 全绿）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2301-wi25-background-task-mode.md --strict` 退出码 0
- [x] checkstyle：commit 钩子承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步（04-tool-invocation §4.5、async-tool-execution §三.7、invariant-catalog §3.1/§3.2、gate-gaps.yaml、roadmap 锚点、当日 log）
- [x] git commit 完成（排除无关杂散改动：nop-format samples .rels 行尾噪音）

## Known Residuals

### 输出仅内存有界 tail（8KB），无文件 offload
- Classification: optimization candidate
- Why Not Blocking Closure: 通知+task-output 通道在 8KB tail 下对诊断/决策足够；文件 offload 为 zcode 对齐增强
- Successor Required: no

### 完成通知不唤醒 idle/终态会话（FOLLOWUP 边界）
- Classification: watch-only residual
- Why Not Blocking Closure: 语义与 WI2 裁定（非第三唤醒源）一致；idle 会话下次 execute 合并 staged 注入；终态会话通知滞留 staged 有界（pendingInjections ≤256）
- Successor Required: no

### shell `&` 生产消费者缺席（孤儿模块）
- Classification: watch-only residual
- Why Not Blocking Closure: nop-ai-shell 无生产装配（全仓零依赖）；SPI+fail-loud 已封失联缺陷；sink 适配器 test-wiring only 并如实登记
- Successor Required: no

### 后台任务 registry 进程内单例（进程重启任务丢失）
- Classification: watch-only residual
- Why Not Blocking Closure: 1h 级任务跨进程存活需求未出现；checkpoint 面协同属后续演进
- Successor Required: no

### 非 bash 工具后台化未开放（形态边界裁定）
- Classification: watch-only residual
- Why Not Blocking Closure: WI2 裁定⑤排除；需求出现时按 background-capable+参数模式逐工具扩展
- Successor Required: no

## Non-Blocking Follow-ups

- totalBytes 口径为 chars 非 bytes（中文输出时 chars≠bytes，与"总字节数"措辞有精度差；行为自洽）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 有界 tail 同口径截取、行为自洽，taskId 可查询快照，无功能影响
  - Successor Required: no
- 惰性补投路径的通知 status 显示 NOTIFICATION_FAILED 而非真实终态（窗口窄：仅首投失败后首次读取）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: fail-visible 语义未破坏；真实终态可经 task-output 查询
  - Successor Required: no
- stop() 返回值 TOCTOU（并发真实完成时观测返回值可能失真；幂等保护使状态机无损）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 仅观测性失真，状态机无损
  - Successor Required: no

## Closure

Status Note: 4 Phase 全收口——registry 状态机（幂等终态/恰好一次 claim/NOTIFICATION_FAILED 惰性补投/tracker 优先 stop/8KB tail/live tail hook）、bash runInBackground（双源解析、clamp 1h、TIMEOUT 映射、fail-loud 接缝）、task-output/task-stop meta 工具、chokepoint background-capable 执法、shell `&` 经 IBackgroundJobSink 闭环（缺席 exit 126）；执行中发现并修复 bridge↔snapshot 递归 bug 与存量缺陷（engine 派发路径 AiToolCall 节点恒 null → 双源参数解析；executor=null NPE → AgentDispatchExecutor）。tool.xdef 为平台级契约演进（<background-capable> 元素，_AiToolModel 再生成，迁移面 additive）。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理 agent_e815af98-1944-4f1f-b273-faf0384f0fd5（fresh session）
- Evidence:
  - Phase 1-4 全部 Exit Criteria 逐条 PASS（live 代码锚点：DefaultBackgroundTaskRegistry 状态机 :142-176/:231-247、DefaultAgentEngine 桥 :778-788 + close 回收 :1243-1248、BashExecutor 双源 :105-109/:393-456 + clamp :251-256、ToolManagerImpl chokepoint :75-79/:156-172、ShellCommandExecutor exit 126 :356-361、AgentToolDispatcher AgentDispatchExecutor 装配 :554-560）
  - Anti-Hollow 调用链追踪：bash runInBackground → registry.submit/run（专属池）→ complete（幂等+claim）→ bridge → injectInput(FOLLOWUP) → staged pendingInjections → 下轮 buildBaseExecutionContext merge → task-output 读取——全链真实连通，TestBackgroundTaskEndToEnd 对每段有断言
  - 实测：toolkit+agent 3793 tests / shell 295 tests 全绿；check-doc-links --strict 0；scan-hollow --severity high 0；check-plan-checklist 0
  - invariant gate 同步：catalog §3.1 35 条（DefaultBackgroundTaskRegistry + 存量漂移 DefaultSelectionStrategy 补录）/ §3.2 19 条（getBackgroundTaskRegistry not-applicable）+ gate-gaps.yaml
  - Minor 项（5，均非阻塞）：totalBytes 口径 chars 非 bytes、惰性补投通知状态保真度、stop 返回值 TOCTOU、commit 时排除杂散文件、backgroundTaskTimeoutMs int 声明宽于 cap（executor clamp 兜底）——前 3 项登记 Non-Blocking Follow-ups watch-only

Follow-up:

- 见 Known Residuals + Non-Blocking Follow-ups（closure audit 3 项 watch-only）
