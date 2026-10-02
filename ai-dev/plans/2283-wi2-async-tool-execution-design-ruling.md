# 2283 WI2 异步占位模式改造面评估与设计裁定（refactor roadmap M1）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI2（Item Type: Decision）
> Related: `ai-dev/analysis/2026-09/2026-09-28-long-tool-wait-design-review.md`（设计取向钉定评审）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI1 划界裁定：WI2 本 roadmap 吸收、无 autonomous 重叠）

## Purpose

产出新 owner doc `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`，收口设计取向已钉定（等待优先 + 唤醒源仅 steering/心跳 + 派生态占位）前提下的五项必裁：① provider 契约（steering 插队请求合法构造形态、双份 tool_result 容忍度）；② toolTimeoutMs 按工具类型分层与心跳兜底关系；③ 唤醒轮的 maxIterations/ISustainer/治理闸门计数归属；④ 等待实现形态、占位模式采用范围与 7-checkpoint 安全链/W3 双层中间件/溢出恢复环交互；⑤ 工具级后台模式（runInBackground）形态边界、后台超时独立分层、shell `&` 处置。本项为纯 Decision，不实施代码；WI3/WI4/WI5/WI25 按本 doc 实施。

## Current Baseline

（执行时 HEAD `856156af4d`，2026-10-02 复核；锚点来自 2026-10-02 只读考察；划界依据 `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`）

- 设计取向已钉定：`ai-dev/analysis/2026-09/2026-09-28-long-tool-wait-design-review.md` Conclusion——等待优先、唤醒源仅 steering/心跳、占位为派生态不入史、三条不变量（派生态渲染/原位插入/checkpoint 差集恢复判定）、否决 unreal 全量事件循环与 session 事件溯源。
- 现状等待机制：`AgentToolDispatcher`（nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java）future 创建 `buildToolFutures:222-296`（orTimeout :271-273、TimeoutException→错误 result :274-283）、`awaitToolFutures:304-332`（allOf+可中断 get :311-319）、`commitToolResults:343-487`（POST_TOOL_ATTEMPT :357-369、TOOL_EXECUTION checkpoint :427-441）。ReAct 扇出逐工具 `callTool` 不经过 ToolManagerImpl 的 executeParallel Semaphore（该 Semaphore 仅 `callTools` 入口 :79-105，fair :85-87）。
- 配置现状：`DefaultAgentEngineConfig.toolTimeoutMs` 默认 300_000 :152（setter 拒绝 <=0 :960-964）；llmTimeoutMs 120s :151；memberExecTimeoutMs 120s :153/:163；无 maxIterations 字段——迭代预算在 `AgentExecutionContext.java:90` 默认 10，`ReActAgentExecutor:422` 循环条件 `currentIteration < maxIterations`，:1244 递增，ISustainer 续期 :455-478（仅 MAX_ITERATIONS 自然退出时咨询，SustainStopReason 唯一值 MAX_ITERATIONS；SisypheanSustainer 默认上限 3 轮）。
- 治理闸门：`ReActAgentExecutor.checkIterationStartGuards:729-817` 顺序 cancel→pause→WAIT_FOR suspend→forceStop(0.9 硬闸 `AgentLoopGuard:89-99`)→assessGoal→压缩触发；溢出行为=forced_stopped 终态+尽力压缩（默认 NoOpContextCompactor，无自治恢复——autonomous WI4/WI5 未落地）。
- steering 现状：round 边界 drain `ReActAgentExecutor.runToolFanout:1234-1242`（工具完成后 drainSteering 逐条 append）；队列 `AgentExecutionContext.steeringQueue:84`/enqueue :298-303/drain :319-323；mailbox 体系在位（message/ 包 IMailbox:52/DeferredAckMailbox:48/NoOp 默认；Actor 消费循环 poll 后经 InMemoryActorRuntime:445 直写 steeringQueue）。
- 7-checkpoint 安全链：`AgentSecurityConsultation.buildCheckpointChain:113-285`（postDenial→toolAccess→permission→pathAccess→layer2 matrix→layer3 approval→conflict，:278-284 add 序）；每 tool call 评估 `ReActAgentExecutor:1185-1193`。与可靠性 checkpoint `CheckpointType`（4 值：TOOL_EXECUTION:26/LLM_TURN:37/COMPACTION:46/WAIT_FOR:58）是两个同名不同物。
- W3 双层中间件：`ExecutionPoint` 4 值（PRE/POST_LLM_ATTEMPT、PRE/POST_TOOL_ATTEMPT，middleware/ExecutionPoint.java:43-64）；SESSION 层 9 个 lifecycle 点 vs EXECUTION 层 attempt 级重评（MiddlewareScope.java:30/:36，分发 AgentHookInvoker:61/:113）。
- shell `&` 残件：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.executeBackground:346-361`——实例内 `backgroundJobs` LinkedHashMap :50、jobId 实例内计数、完成即移除、立即返回 "[jobId] running in background"、无查询/等待/取消/输出回收、无 wall-clock 超时。toolkit 侧 `BashExecutor.java:101` timeoutMs 默认 30s。
- tool schema：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/tool.xdef`无类型枚举、无 timeoutMs（调用侧 tool-call.xdef:4 timeoutMs 属性 + unknown-attr 透传）；tags csv-set :22 可用于能力声明；subagent = `CallAgentExecutor`（TOOL_NAME "call-agent" :85，深度上限 4 :110，orTimeout :459 只 cancel Future 不停子 agent :440 注释）。
- zcode 参照（roadmap Current Baseline 2026-09-28 节）：per-call timeout + 策略封顶（默认 120s/max 600s）、subagent 后台强制 max runtime、后台任务 registry + 1s 轮询快照 + 完成通知 claim 令牌恰好一次、TaskOutput 已废弃收敛单一结果通道。
- provider 契约实测环境：仓库与沙箱内无任何 provider API key（env/配置零命中）——live probe 不可行，必裁①以官方契约文档 + 设计规避 + dialect 单测断言替代（见 Goals 必裁①说明）。

## Goals

产出 owner doc `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`（遵循 `ai-dev/design/00-design-writing-guide.md`：只写决策+理由+拒绝+约束，不写类签名实现），收口五项必裁：

- **必裁① provider 契约**：steering 插队请求合法构造形态裁定——占位 tool_result 紧随未完成 tool_use 之后的同一 user 轮（Anthropic tool_result 先于文本块、同角色轮合并；OpenAI tool role 消息紧随 tool_calls 后、steering 为其后独立 user 消息；Gemini functionResponse 同轮），占位与 steering 文本共存于唤醒请求但占位永不入史；双份 tool_result 在派生态设计下结构性不可达，容忍度裁定为"不依赖 provider 容忍度"。实测降级说明：无 API key 环境不可 live probe，以官方契约 + WI3 dialect 单测断言请求 JSON 形态替代，残留风险（provider 未文档化严格性）登记至 plan Follow-up。
- **必裁② 超时分层与心跳兜底**：三层模型——模型 per-call 可调 timeoutMs（既有 tool-call.xdef 属性，纳入策略封顶）→ 引擎级工具类型分层默认与硬上限（配置面 toolTimeoutPolicy：按工具名/tags 覆盖 + 全局 cap）→ 心跳模型判断为主路径（心跳轮模型可裁决继续等待/放弃，被裁"继续等待"的工具免疫机械超时至策略 cap）；机械超时降级为兜底（心跳不可用或模型未裁决时按分层超时执行）；推导约束：单工具等待期心跳轮数上界 = 策略硬上限 / heartBeatIntervalMs；封顶口径为单工具（派生工具各获独立 cap 窗口，整轮聚合等待的治理兜底转为 forceStop 闸 + 心跳模型判断）。心跳间隔默认 60s（配置 heartBeatIntervalMs；unreal 10min 对 mvn 场景偏长，输出增量 60s 可见性更优）。
- **必裁③ 唤醒轮计数归属**：唤醒轮（steering/心跳瞬时请求）不消耗 currentIteration/maxIterations、不触发 ISustainer（其仅在 MAX_ITERATIONS 自然退出时咨询，语义不变）；唤醒轮前置门槛集 = cancel + pause + forceStop 硬闸（唤醒请求构造前检查，防唤醒请求自身溢出；无 WAIT_FOR/assessGoal/压缩触发——无迭代语义）；唤醒轮 LLM 调用走 LlmCallCoordinator（retry/熔断/执行级 PRE/POST_LLM_ATTEMPT 保留）；会话级 lifecycle 钩子点（9 点）与 EXECUTION_COMPLETED 事件不在唤醒轮触发（完成语义不适用），但 LLM 输出内容 guardrail（POST_REASONING/POST_CALL 的 BAIL 检查）对唤醒轮 assistant 回复照常适用（BAIL→放弃该唤醒回复、按继续等待处理并记审计，guardrail 覆盖面不裁减，唤醒轮实际适用的是 checkOutputGuardrail 硬编码快速路径或其等价抽取，按机制命名实施）。**唤醒轮响应能力**：assistant 回复可携带新 tool_use（对齐 react-engine §5.5"并行独立工作"三选项）——子裁定：①派生 tool call 立即经正常 dispatch 路径执行（全量过 7-checkpoint 安全链 + PRE_TOOL_ATTEMPT），future 并入原批次 pending 集合，原 round 继续等待全部 pending 完成；②不计入迭代预算（归唤醒轮语义）；③后续唤醒请求中与其他运行中工具同样全量占位渲染（同一不变量）；④唤醒轮回复纯决策（继续等待/放弃）时不派生工具调用。唤醒轮的 steering/心跳消息与其 assistant 回复入史（事实记录），占位不入史（不变量①）。**唤醒轮持久化**：入史 = ctx.addMessage，持久化随下一次正常 checkpoint（TOOL_EXECUTION/LLM_TURN）同步落盘，不新增 checkpoint 类型、不写独立 LLM_TURN；崩溃窗口内唤醒消息丢失可接受——steering 靠 WI7 幂等重投、心跳丢失无害（下一周期重建）。
- **必裁④ 等待实现形态与交互**：等待实现 = CompletableFuture 组合（anyOf 竞争：全部工具完成 / steering 到达 / 心跳 timer），等待可中断（沿用 awaitToolFutures 可中断 get 纪律），不引入 event loop；占位模式采用范围 = 全量（等待期唤醒请求中所有运行中工具一律占位渲染，正常 round 完成路径零开销），否决按工具类型白名单（任何工具都可能慢，白名单徒增配置面与漏配风险）；7-checkpoint 安全链交互 = 占位是请求构造期渲染不经过安全链（安全链评估真实 dispatch），真实结果 commit 仍走 POST_TOOL_ATTEMPT + TOOL_EXECUTION checkpoint；POST_CALL guardrail 交互 = 占位不经 POST_CALL（会话级执行完成点，评审 Open Question ①收口：占位与 POST_CALL 兼容形态即"占位不触发"），唤醒轮回复的内容 guardrail 适用（见必裁③）；W3 交互见必裁③；溢出恢复环交互 = 唤醒轮前置 forceStop 硬闸，autonomous WI5 落地后自动升级为压缩重算（协同条款登记）。
- **必裁⑤ runInBackground 第二形态**：开放面 = bash/shell 类工具（tag "background" 声明能力，tool.xdef tags 既有机制）；subagent（call-agent）不开放模型自选后台（member spawn 已有异步面，避免双通道）；文件类无后台语义不开放。后台超时独立分层：backgroundTaskTimeoutMs 默认 3_600_000（策略封顶，模型 per-call 可调不高于 cap）。shell `&` 处置：接入 WI25 后台任务 registry 闭环（`&` 解析为 runInBackground 提交、jobId=registry taskId、可查询/等待/取消/回收输出），禁绝启动即失联；registry 未就绪的过渡期 `&` 显式报错（快速失败）。完成通知桥入 mailbox 作为新输入（完成通知非第三唤醒源，经由既有 mailbox/steering 通道，不违反"唤醒源仅 steering/心跳"），读取与完成通知共享 claim 令牌防双发。

## Non-Goals

- 不实施任何代码变更（WI3/WI4/WI5/WI25 承接）。
- 不裁定 WI5 Phase B（在飞 LLM 请求打断-重建）——按 roadmap 由 WI5 Phase A 收口后裁定。
- 不引入 unreal 全量事件循环 / session 事件溯源 / 完成即唤醒（评审已否决，本文档仅登记否决理由）。
- 不修改既有 owner docs 的既有章节语义（新增引用与交叉链接除外）。

## Scope

### In Scope

- 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`（五项必裁裁定 + 约束边界 + 拒绝方案）。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`：§5.5 末尾（及 :310 实施编排指引行）增加指向新 owner doc 的引用（最小增量，不改既有章节语义）。
- `ai-dev/design/nop-ai-agent/guardrail-contract.md`：增加唤醒轮 guardrail 适用语义的交叉引用（内容 guardrail 适用/lifecycle 钩子点不触发的边界说明，最小增量）。
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md`：§4.5 形态边界收口指针回填指向新 owner doc（对齐 §4.4 先例）。
- `ai-dev/design/nop-ai-agent/README.md`：索引行（如该 README 维护文档清单）。
- `ai-dev/logs/2026/10-02.md`：WI2 条目。
- `node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

### Out Of Scope

- `docs-for-ai/` 更新（能力落地后由实施 WI 同步，本项纯设计裁定）。
- autonomous roadmap 任何变更。

## Execution Plan

### Phase 1 - owner doc 撰写与引用回填

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md`

- Item Types: `Decision`

- [x] 撰写 owner doc：设计结论（等待优先+双唤醒源+派生态占位+runInBackground 第二形态）、五项必裁各一小节（决策+理由+代码/契约锚点）、拒绝方案（unreal 事件循环/事件溯源/纯机械超时/按类型占位白名单/call-agent 模型自选后台）、与既有设计关系（react-engine §5.2 steering 语义、04-tool-invocation、guardrail-contract 7-checkpoint 链、session-and-storage 三不变量）
- [x] react-engine.md §5.5 末尾（及 :310 指引行）增加指向新 doc 的引用；README.md 索引（若维护清单）；guardrail-contract.md 交叉引用；04-tool-invocation.md §4.5 收口指针回填

Exit Criteria:

- [x] owner doc 五项必裁全部有明确结论且结论与 Current Baseline 代码事实一致（锚点抽查复核）
- [x] owner doc 无实现代码片段/类字段清单（design guide 合规），钉定取向作为独立章节呈现而非引用 analysis 文档；引用面限 design/docs-for-ai/源码/lessons/roadmap（无 analysis/discussions 引用）
- [x] react-engine.md / guardrail-contract.md / 04-tool-invocation.md 引用在位且不改变既有章节语义
- [x] `ai-dev/logs/2026/10-02.md` WI2 条目在位
- [x] No owner-doc update required 之外的文档更新已完成（react-engine/README/guardrail-contract/04-tool-invocation 属最小引用增量）

### Phase 2 - 工具检查

Status: completed
Targets: 仓库文档面

- Item Types: `Proof`

- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] check-doc-links --strict 退出码 0

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI2 checkbox → git commit → 本 plan 标 completed。

- [x] owner doc 五项必裁结论明确、锚点真实、design guide 合规
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 无代码变更（纯 Decision 计划，`./mvnw test` 不适用，按 plan guide 纯文档计划条款豁免）
- [x] git commit 完成（含全部 In Scope 文件）

## Deferred But Adjudicated

### provider live probe（必裁①残留风险）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 裁定基于官方契约文档 + 派生态设计结构性规避（占位与真实结果永不同请求共存、双份 tool_result 不可达），live probe 仅验证 provider 未文档化严格性；WI3 dialect 单测断言请求 JSON 形态已覆盖构造合法性验证，环境具备 API key 时可在 WI3/WI4 执行中补测。
- Successor Required: no
- Successor Path: WI3 plan 的测试矩阵（若环境具备则升级为 live probe）

## Closure

Status Note: owner doc `ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md` 已产出，五项必裁全部收口（provider 契约/超时三层/唤醒轮语义含响应能力与持久化/等待实现与交互/runInBackground 形态边界），四处关联 owner doc 交叉引用回填，log 已记录——WI2 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_98d0df54-9b48-4ff0-abe2-5a0463c901e3（fresh session）
- Evidence:
  - 五项必裁结论完整性 PASS + 自洽性专项核验 PASS（③×④ 全量占位、⑤×双唤醒源、封顶口径单工具）
  - 锚点真实性 12 处 live 核对全部一致（AgentToolDispatcher/DefaultAgentEngineConfig/CheckpointType/buildCheckpointChain/ReActAgentExecutor/ShellCommandExecutor/tool-call.xdef/BashExecutor/tool.xdef tags 等）
  - design guide 合规 PASS（无代码片段、无 analysis/discussions 引用、无演进叙事、钉定取向独立呈现）
  - 交叉引用回填四处 PASS（react-engine §5.5+实施编排行、guardrail-contract BAIL 节、04-tool-invocation §4.5、design README 索引），git diff 确认最小增量
  - plan 一致性 PASS（Phase checklist 与 live 产物一致；Deferred live probe 分类合规）
  - `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（3 warning 为 nop-bytecode 预先存在项）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2283-wi2-async-tool-execution-design-ruling.md --strict` exit 0（收口勾选后运行）
  - 纯 Decision 计划：git status 仅 ai-dev 下文档，mvn 豁免

Follow-up:

- provider live probe（watch-only residual，见 Deferred But Adjudicated）
