# DeepSeek Harness 深度分析：一切皆插件的官方 Agent Harness

> Status: open
> Date: 2026-08-13（nop 侧对比实测补充同日完成）
> Scope: `~/ai/deepseek-harness`（deepseek-ai/deepseek-harness，2026-08-13 公开发布，v0.1.0-rc.5，MIT，基于 vendored Cordis 插件框架）vs `nop-ai-agent`（nop-ai/nop-ai-agent，551 个 Java 文件，核心循环/容错/安全已逐类实测）
> Conclusion: dsh 与 nop-ai-agent 是**两个完成度都极高的成熟 harness，但走的是相反路线**：dsh 以"事件日志 + 一切皆插件"换可恢复性/可替换性（无 turn/step 对象、无显式容错层，靠重放），nop 以"显式容错栈（重试/熔断/三通道故障转移/checkpoint journal/恢复守护）+ 纵深安全链（7-checkpoint）+ 显式计划状态机"换生产可用性（消息数组会话 + 压缩扣减计价）。**核心逻辑对比（实测）**：循环模型（dsh 持久化 turn/step 事件 vs nop ReAct 迭代计数 + checkpoint 补丁）、上下文压缩（dsh surface 遮蔽 + KV cache 保持 vs nop 4 层升级压缩 + EMA 校准计价——机制等价、dsh 在计价/前缀保持占优，nop 在分层裁剪占优）、容错（nop 全面强于 dsh：错误分类 6 类、指数退避全抖动、熔断、账号/模型/跨 provider 三通道故障转移、幂等键发散检测、60s 恢复守护；dsh 仅有 request-error 重试 + goal rounds）、安全（nop 7-checkpoint 纵深链显著强于 dsh 的轻量 guard，dsh 仅在 OS 级沙箱占优）、计划（nop PlanExecutor 状态机强于 dsh plan-mode）、团队/记忆（nop 有，dsh 无）、子代理（dsh 6 provider + capabilities 契约强于 nop call-agent）。对 nop 的价值：吸收 dsh 的 **surface 语义压缩计价与 KV 前缀保持、capabilities 契约 fail-loud、spill 溢出存储**三机制；dsh 的事件日志 vs nop 的 checkpoint journal 是"纯重放 vs 补丁式恢复"的路线对照，nop 短期无需转向事件日志，但可吸收其"阴影计价"思想。

---

## 一、总览

**DeepSeek Harness（`dsh`）** 是 DeepSeek 官方开发的 agent harness，2026-08-13 11:56 UTC 在 GitHub 公开（当天即 2.1 万+ star）。它是 DeepSeek 补上"模型之外那一层"的战略产品——过去开发者用 Claude Code/Codex 等第三方 harness 驱动 DeepSeek 模型，模型是 DeepSeek 的，**决定开发体验的 harness 却在别人手里**；Harness 就是把这层补回来（崔添翼 8 月 1 日内测招募帖的公开背景）。

| 维度 | DeepSeek Harness | nop-ai-agent |
|------|------------------|--------------|
| 语言/运行时 | TypeScript/Node（pnpm monorepo，Node ^22.19） | Java 21（Nop IoC 引擎内嵌） |
| 框架底座 | **vendored Cordis**（koishi 生态插件框架，源码级 vendor + 18 条本地修改） | Nop IoC + DSL-first |
| 核心抽象 | 一切皆插件：Service Definition/Provider/Consumer 三层 seam | @BizModel + beans.xml 装配 |
| 会话模型 | **append-only SessionEvent 日志**（event sourcing）+ SurfaceManager 可替换消息面 | 扁平 `AgentSession`（消息数组 + 计数器 + planId）+ ISessionStore（InMemory/FileBacked/DBSessionStore）+ checkpoint journal 补丁式恢复 |
| 上下文管理 | compaction（surface replace + KV cache 保持 + shadowed token 计价）+ spill（溢出存储） | **PipelineCompactor 4 层升级**（截断→micro 占位→turn 剪枝→LLM 总结）+ 替换后 tokensUsed 同步扣减 + CalibratedTokenEstimator（EMA）计价 + ReferenceCompaction（内容寻址） |
| 循环层级 | turn > step > round 三级 + `agent/*` live 事件 + `session/*` 持久事件双通道 | ReAct 双层 while（reactLoop + sustainer 续跑），currentIteration 计数，无 turn/step 对象；12 类生命周期事件 |
| LLM 容错 | request-error waterfall 重试（llm-retry 策略） | **6 类错误分类 + 指数退避全抖动 + Retry-After floor + 熔断（阈值 3/60s）+ 三通道故障转移（账号链/模型 tier/跨 provider）** |
| 恢复机制 | 事件日志重放（interrupted turn 合成闭合） | checkpoint journal（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR）+ 幂等键发散检测 + 60s 恢复守护（超时/孤儿/团队任务）+ DB 接管锁 |
| 安全 | guard 包（repeat-tool 提醒/timeout-policy）+ approval policy + OS 级沙箱 | **7-checkpoint 纵深链**（denial ledger 阈值 3 / post-denial 指纹 / 权限矩阵 / 路径检查含 symlink / 审批门 / 写冲突 fail-fast） |
| 子代理 | 6 provider（spawn/fork/acp/claude-code/codex/dsh-sdk）+ capabilities 声明校验 + continuation | call-agent 工具（async mailbox + fork 直调，深度上限 4，agentId 白名单），无 capabilities 契约 |
| 计划/目标 | plan mode（日志内状态 + 策略 prompt）+ goal（CAS + rounds 自动续轮） | **PlanExecutor 独立状态机**（phase 门控/replanner/停滞检测/DAG 校验）+ goal-tracker 卡死检测（STUCK） |
| 团队/记忆 | 无 memory 包（MCP 示例）、无团队 | memory/（AiMemoryStore + 向量适配 + 1024 token 注入预算）+ team/（TeamManager/ACL/8 成员配额/任务流编排） |
| 沙箱 | bwrap→Landlock→Seatbelt→Windows ACL restricted token（confine(argv) 包装，不启容器） | NoOp/DockerSandboxBackend（fail-closed，隔离不可达即抛 SandboxException） |
| 工作流 | 模型编写 JS 编排脚本（worker_thread + vm，非 DAG） | xwf 工作流（DSL）+ TeamTaskFlowOrchestrator |
| 发布形态 | web UI / headless CLI / ACP server / Python SDK（JSON-RPC over stdio + 单文件 exe runtime） | 服务化 + MCP server（nop-ai-mcp-server）+ nop-ai-app |
| 许可证 | MIT（含权重、SDK、runtime） | Apache 2.0（Nop 平台） |

**核心结论先行**：dsh 把 2026 年"Harness Engineering"行业的共识机制（2026-08-01 harness-mechanism-reference-framework 的 12 大维度）几乎全部落地为**可替换插件 + 持久化事件**，且工程纪律（postmortem、agent notes、invariants、覆盖率 100% 门禁）极强。它是本目录调研项目里**唯一一个把"会话日志即事实源"贯彻到收件箱、compaction、goal、plan mode、sandbox mode 全部状态的**。对 nop-ai-agent 的三层价值：**机制可借鉴**（surface compaction、seam 契约、goal rounds、spill）、**架构可对照**（事件日志 vs 可变状态）、**形态可参考**（headless/jsonrpc/ACP 的无人值守基准形态——DeepSeek 自己 7 月 31 日就是用"Harness 极简模式"跑 V4-Flash 官方 agent 基准的）。

---

## 二、Context（调研背景）

- **为什么需要这个分析**：DeepSeek 是 nop-ai 最核心的模型供应商（deepseek-v4-flash 为默认模型之一），其官方 harness 今天公开发布，是"模型厂商亲自下场做 harness"的标志性事件；且 7 月 31 日 V4-Flash 官方 agent 基准全部由该 harness 跑出，其"极简模式"就是无人值守编码 agent 的最小形态——与 nop-ai-agent 的 headless 方向直接对标。
- **要回答的问题**：dsh 的架构核心是什么？哪些机制/契约值得 nop-ai-agent 吸收？哪些因技术栈差异不可移植？它相对 Claude Code/Codex 的差异化架构决策是什么？
- **约束**：dsh 是 TS/Node 产物（~198k 行生产代码），nop 是 Java/DSL 栈。本分析侧重**机制与契约**而非代码搬运。已下载到 `~/ai/deepseek-harness`（完整 git 历史 12293 commits）。

---

## 三、发布事实与时间线（含今天）

### 3.1 发布硬事实（今日实测）

| 项 | 值 |
|---|---|
| 仓库 | `github.com/deepseek-ai/deepseek-harness` |
| 创建时间 | 2026-08-13T11:56:32Z（UTC） |
| 版本 | `@deepseek-ai/dsh` 0.1.0-rc.5（rc.1→rc.5 同日连续发布） |
| 描述 | "DeepSeek Harness: Everything is a Plugin." |
| 许可证 | MIT（README/THIRD_PARTY_NOTICES 双声明） |
| 规模 | 7412 个 git 文件、12293 commits、packages/ 下 198,402 行 src 生产代码（1,185 个 TS 文件）+ 734 个测试文件 |
| 团队 | 崔添翼（负责人）5235 commits、Yichen Jiang 1361、imccyu 1297、Chinesezjc 587 等 15+ 活跃提交者 |
| 开发周期 | 2026-06-10 仓库初始化 → 7 月 8273 commits（高峰）→ 8 月 3439；约 2 个月从零到发布 |
| 运行方式 | `npx @deepseek-ai/dsh web`（Web UI，默认 :3080）或 `pnpm dsh web` |

### 3.2 事件时间线（官方/公开）

| 时间 | 事件 |
|---|---|
| 2026-03 | 崔添翼加入 DeepSeek，组建 Harness 团队（内部工作名 "DeepSeek Code"） |
| 2026-06-10 | 仓库初始化（README/AGENTS.md，首个 commit） |
| 2026-07-31 | V4-Flash 正式版 API 公测；更新日志首次点名 Harness："Code Agent 基准使用 DeepSeek Harness 极简模式（即将发布），max effort、topp=0.95、temperature=1.0"——9 项 agent 基准全面反超 V4-Pro 预览（Terminal Bench 82.7、Toolathlon 70.3、DSBench-Hard 59.6 等） |
| 2026-08-01 | 崔添翼 X 招募内测（回 GitHub ID + 项目地址）；社区统计 769 报名/712 仓库/120 万+ stars（"开源 Agent 春晚"） |
| 2026-08-05 | 崔添翼列出 Harness 生态接入方向：**plugin、skill、MCP、orchestrator、aggregator、UI** |
| 2026-08-13 | 公开发布（今日）；同日 V4-Pro 正式版 0813 更新（API 层） |

### 3.3 与基准的关系（重要）

V4-Flash 官方 9 项 agent 基准成绩是 **"模型 + Harness 极简模式"联合体**的分数，不是裸模型。dsh 为此提供的 `examples/jsonrpc-agent/minimal.cordis.yml` + `minimal.py`（Python SDK 32 行）就是官方"极简模式"的开源复现：仅 2 个工具（bash + str_replace_editor）、本地 PTY、fs-local、danger-full-access、无 compaction。**第三方复现 DeepSeek 官方基准的全部要件今天已开源**。

---

## 四、架构核心：Cordis + 事件日志 + Seam

### 4.1 一切皆插件（Cordis）

dsh 的底层是 **Cordis**（koishi 生态插件框架，4.0.0-rc.7），整个框架层被 **vendored 进 monorepo**（vendor/ 目录，9 个包改名 `@deepseek-ai` scope，18 条本地修改记录在 vendor/README.md）——不是 npm 依赖，是可审计、可打补丁、可钉版本的源码。核心机制：

- **插件 = 实现 Service 的对象**：函数或 `Service` 子类，生命周期由 Cordis 挂载进 context。
- **Context = 服务仓库**：服务声明稳定 `ctx.<key>`（`ctx.tools`、`ctx.llm`、`ctx.sessions`…），插件按 key 找服务而非 import 具体实现。
- **依赖声明式**：`inject` 声明服务依赖，加载顺序由服务需求表达，非手动 boot 排序。
- **事件四种派发模式**：`emit`（观察，不等）、`waterfall`（围绕式中间件，可短路可换值）、`parallel`（并发）、`serial`（顺序带返回值）。waterfall 是 dsh 拦截策略的主干（如 `agent/pre-step` 的权威决策、`tools/post-execute` 的策略追加）。
- **注册即可逆 effect**：prompt section、tool schema、adapter、listener 都通过 `ctx.effect()`/`ctx.on()` 安装，插件卸载时自动 unwind。

**没有特权核心**：模型适配器、工具注册表、会话日志、乃至 agent loop 本身都是插件，全部可从配置替换。AGENTS.md 明言 "There is no privileged core to patch"。

### 4.2 会话日志（event sourcing）是唯一事实源

`Session`（packages/core/session，3156 行）是 **append-only 事件日志**：`SessionEventMap` 通过 TS 声明合并（module augmentation）让每个插件新增自己的持久事件类型。关键不变量（architecture.md）：

> **Model-visible means logged.** 任何到达模型请求的输入必须能从日志重建，运行时 invariant 断言之。

三个支撑机制：

1. **SurfaceManager**：日志之上维护"可替换的有序消息面"。compaction 不是删除消息，而是写入带 `surfaceOp: {op:'replace', start, end}` 的节点**遮蔽**区间；`deriveMessages()` 按 surface 增量投影 LLM 消息历史（带 generation 缓存，replace 自动失效）。UI、计价、重放、fork、resume 全部共享这同一协议。
2. **Inbox 持久化**：收件箱 splice 操作先写 `agent/inbox/spliced` 会话事件再改内存投影，恢复时从日志重放——连"用户发了什么还没被消费"都是可恢复状态。
3. **Interrupted turn 合成闭合**：崩溃后的 turn 由持久化后端在 reload 时合成 `interrupted` 结束事件（core/session/src/types.ts:173）。崩溃恢复是架构承诺而非补丁。

### 4.3 Seam：Service Definition / Provider / Consumer 三层

每个能力都是 seam，三种角色必须齐备（docs/capability-seams.md，56 行服务表 + 生成图）：

- **Service Definition**：Cordis Service（`ctx.<key>`），**必须是抽象类或具体 registry，绝不能是 TS interface**（编译期保证）。
- **Service Provider**：实现者，可多个并存（如 `ctx.shell` 有 bash-local/bash-sandbox/pwsh-local）。
- **Consumer**：模型面工具（defineTool）或 hooks。

关键性质：**换一个 provider 换整个产品**。文件系统和子进程 provider 共享同一执行世界，把 `ctx.fs`/`ctx.subprocess` 指向远程沙箱（E2B）时 Bash、PTY、LSP 随之迁移，无 provider fork；`ctx.subagents` 背后从进程内子代理到 Codex 外部 CLI 都是同一接口。

### 4.4 循环层级与双通道事件

- **层级**：`turn`（一次 drain，零或多个 step）> `step`（一次模型请求 + 它调用的工具）> `round`（外层策略迭代，goal driver 使用）。一次 turn 的序列：`turn/start` → claim inbox → `agent/pre-step`（权威：reject 或 enter）→ `step/start` → `user/message` → `system-prompt/assemble` → `agent/request` → `llm/stream` → `assistant/chunk*` → `assistant/message` → `tool/call*` → `tools/pre-execute` → `tools/execute` → `tools/post-execute` → `tool/result*` → `step/end` → `agent/turn-stopping`（串行 terminal checkpoint）→ `turn/end`。
- **双通道**：`session/event` 是持久可重放事实（UI、SDK 转录、replay 都消费它）；`agent/*` 是 live 协调 API（队列/状态/拦截/steering/错误）。SDK 文档明确："replayable transcript → session/event；live coordination → agent/*"。
- **错误恢复**：`agent/request-error` waterfall 返回 `{kind:'retry'}` 即可重试失败的模型请求；compaction 的 overflow 压缩正是挂在这个点。

---

## 五、关键子系统机制详解（有亮点、可借鉴的部分）

### 5.1 上下文管理：compaction + spill（dsh 最值得借鉴的机制）

**compaction**（packages/compaction/*，8032 行，4 子包）：

- 抽象 `CompactionEngine`（`compactIfNeeded`/`compactNow`/`compactRegion`），默认 `BasicCompactionEngine`。
- 双触发：`agent/pre-step` 按 token 压力阈值（thresholdRatio）自动检查；`agent/request-error` 收到 `CONTEXT_WINDOW_EXCEEDED` 时 overflow 压缩并返回 retry（带 maxOverflowRetries 上限）。
- **KV cache 保持**：摘要调用复用对话自己的 system/tools/前置 messages 前缀做一次性调用——摘要 token 与原文共享前缀，provider 前缀缓存连续（DeepSeek 缓存命中 0.02 元/百万 token 的定价下这是成本关键）。
- **表面替换 + 影子计价**：压缩区间用 `surfaceOp: replace` 遮蔽，`compaction/summary` 事件记录 `shadowedTokenCount` 供计价（shadow 的 token 不再计费）；`compaction/start…end` 事件对充当并发锁。
- **增量修剪**：`compaction-tool-result-pruner` 做模型无关的 tool/result 节点修剪（先 prune 再 summarize）。
- 手动压缩走 `agent.runMaintenance()`，只在 idle 时执行，失败分类为 `ManualCompactionError`（busy/cancelled/changed/summary/commit/persistence）。

**spill**（packages/spill/*，1473 行，3 子包）：

- `SpillStore` 抽象只有一个方法 `saveText()`；`spill-policy` 挂在 `tools/post-execute`（prepend）：纯文本工具结果超过 `maxInlineBytes` → 全文落盘 → 模型面替换为 head/tail 预览 + `(N bytes omitted. Full result stored at: <locator>)` 检索提示。
- 刻意窄化：跳过 `read`（避免 read→spill→read 循环）、跳过带 value 替换的决策；存储失败保持内联原文（best-effort，绝不把成功调用变 isError）。
- 意义：**token 计价与上下文预算挂钩**，长输出不膨胀模型上下文、不丢失（可检索）。

### 5.2 subagent：6 个 provider + capabilities 契约（24266 行，最大业务包）

| provider | 包 | 继承方式 |
|---|---|---|
| `spawn` | subagent-spawn-in-process | 全新子 Agent（不继承上下文） |
| `fork` | subagent-fork-in-process | seed 父会话已完成 turn 前缀，`inheritsParentContext=true` |
| `acp` | subagent-acp | Agent Client Protocol 外部进程 |
| `claude-code` | subagent-claude-code | 官方 @anthropic-ai/claude-agent-sdk 外部进程 |
| `codex` | subagent-codex | OpenAI Codex CLI 外部进程 |
| `dsh-sdk` | subagent-dsh-sdk | 通过 SDK 连另一个 dsh 实例 |

机制亮点：

- **Capabilities 声明 + fail-loud**：provider 必须声明 `{outputSchema, depthLimit, toolFilter, persona}`，服务在委托前 `assertCapabilities` 校验并**拒绝**（不静默降级）。
- **深度持久化**：`delegationDepth` 存在 SessionHeader，重启后仍递归限深。
- **Continuable 子代理**：provider 只贡献 `prepareContinuable()` 的数据（是否 seed 父历史），子代理的 Agent/轮次/销毁全由 `SubagentContinuationManager` 持有；`followup` 支持冷恢复（persisted Session 重新物化）与热投递（inbox）；子代理可 `reportFrom()` 向父汇报。
- **结算**：turn 结果映射为 completed/aborted/error/max-tokens/**refusal**（pre-step reject 视为拒绝）——结构化输出经 capture 工具注入。

### 5.3 workflow：模型编写的 JS 编排脚本（非 DAG）

`ctx.workflow` 接受**模型生成的 JS 脚本字符串 + meta JSON**，在 worker_thread + `node:vm` realm 中执行；脚本内可用 `agent(prompt, options)`（经同一 subagent seam 启动真子代理）、`phase(title)`、`log()`、`parallel()`/`pipeline()` 组合子。限制每 run 有 maxTotalAgents/AGENT_CAP/ITEM_CAP；与 Claude Code dynamic-workflows 的 meta block 词汇兼容（WorkflowMeta 注释明示）。

**诚实声明**：vm 不是安全边界（realm.ts 注释明说），隔离靠 worker 线程 + 强制终止。"信任模型 + 结构隔离"的取舍写进了代码注释——这是 dsh 工程文化的一个缩影。

### 5.4 goal / plan / guard

- **goal**（5786 行）：目标不是 LLM 维护的 todo，而是**持久、带 CAS 修订（id+revision）与轮次上限（maxGoalRounds）的会话投影状态**；`goal-round-driver` 在 agent idle 且 goal armed 时自动把下一轮注入 inbox（round+1）——"自动继续执行直到 maxGoalRounds"是持久化的外层策略。
- **plan mode**（2028 行）：计划模式 = **会话日志中的持久状态**（`plan/mode` 事件，last-wins fold）；激活时给每次请求附加 `plan:policy` prompt section；`exit_plan_mode` 工具呈现计划并请求用户批准（REVIEW_ID 用户问题）。resume/fork 自动恢复（状态在日志里，无需额外持久化）。
- **guard**（1021 行）：repeat-tool-reminder（连续相同工具+相同规范化参数计数，阈值 [3,5,8] 注入提醒，不 veto）；timeout-policy（工具声明 timeoutMs 时 deadline 信号替换，超时转 `TOOL_TIMEOUT` 结构化错误）。

### 5.5 sandbox：OS 原生机制而非容器

`ctx.sandbox` 抽象只有一个方法 `confine(argv, policy)`——**返回包装后的 argv，不执行进程**：Linux **bwrap → Landlock**（node-addon）、macOS **Seatbelt**（sandbox-exec SBPL）、Windows **ACL restricted-token runner**。功能探测 + fail-closed；`SandboxUnavailableError` 失败关闭。把"后端特定拒绝方言"（denialSignatures）作为契约暴露给上层分类。

### 5.6 skill / storage / LLM

- **skill**（6242 行）：多 provider 目录合并 + **rank 取胜**（bundled rank 600 > runtime rank 250）+ agent scope 分层；从 `.dsh/skills`、`.agents/skills`、bundled 根发现（chokidar 监听）；渲染规范 `<skill_content name=…><skill_instructions>…</skill_instructions></skill_content>` 块。
- **storage**（3400 行）：命名 backend 注册表（JSON 原子写 / SQLite）+ StorageForms 声明合并扩展。
- **llm**（20414 行）：`LlmAdapter` 唯一必需方法 `abstract stream(): AsyncIterable<StreamChunk>`；`StreamChunk` 是块级增量协议（block-start/text-delta/reasoning-delta/tool-call-delta/block-end/usage/finish）。实现仅 2 个：`llm-deepseek`（手写 SSE 直连 DeepSeek API）与 `llm-pi-ai`——**发布时只自带 DeepSeek 官方适配**，其余靠 catalog provider（Anthropic/OpenAI/Bedrock/Vertex/Azure/Codex OAuth）。

---

## 六、产品形态与 Python SDK（无人值守基准形态）

四种运行形态，全部共享同一 Cordis 配置树（profile/bundle/patch 分层合成，`dsh --dump-config` 可打印任意一行并 patch 之）：

1. **web**：`dsh web` → Vite 6 + React 18 桌面壳（dsh-web-frontend 薄壳 + packages/client 40+ 包，浏览器↔主机经 host apiproxy + WebSocket downlink + `__DSH_BOOT__` 引导）。
2. **headless**：`dsh --profile headless "job"` 一次任务打印最终回答退出；examples/headless-agent 是 DeepSeek V4 + bash/fs + subagent 委派 + workflow/Ralph + todo_write + JSONL 的完整组合，含大量 snapshot/e2e 测试。
3. **ACP server**：Agent Client Protocol JSON-RPC stdio 自动化服务器，供父 agent 作为子代理调用。
4. **Python SDK**（PyPI `deepseek-harness-sdk` + `deepseek-harness-runtime-bin`）：
   - 交互协议 = **JSON-RPC 2.0 over stdio（NDJSON）**：3 个请求方法（initialize / session/prompt / shutdown）+ 4 个服务端通知（session.event 可回放事件流 / session.status / subagent.started / subagent.finished）。
   - runtime 以**单文件 exe** 分发（@yao-pkg/pkg --sea，目标机器无需 Node；macOS 附 spawn-helper 处理 node-pty）。
   - 高层 API：`DeepSeekHarness` context manager + `harness.run(input, session_id)` → RunResult（final_response 取最后一个 assistant/message；finish_reason 取最后一个 turn/end 的 reason.kind）。
   - `minimal.py` 32 行 + `minimal.cordis.yml`（2 工具、无 compaction、danger-full-access）= 官方基准"极简模式"的开源复现。

**用户视角**（docs/user/guide）：Settings → Models 填 DeepSeek API key（立即生效）→ Choose workspace → 发任务；agent 可读改写文件、跑命令、委派、维护 plan，敏感操作按权限策略弹审批。凭据存 `$DSH_HOME/.credentials.yaml`（只写，settings 只存引用）。

---

## 七、工程实践亮点（nop 的 AI 协作基建可对照）

1. **Agent Notes 体系**（.agents/notes/，250+ 条非归档）：设计文档唯一形式，路径编码双轴 `{lifecycle}/{class}/yyyy-mm-dd-topic-title.md`（proposed/implemented/rejected/archived × feature/bug-fix/simplification/architecture/process/testing），`## Alternatives considered` 强制，`pnpm run verify-agent-note-format` 门禁；"非平凡变更必须写 Agent Note"是 process 级 rule（implemented/process/2026-07-19 起强制）。
2. **Postmortem**（docs/postmortem/ 4 篇）：0001 ACP server crashed on connect（Loader unwrapExports 丢 default export）→ 0004 Landlock partial-notice 误分类子进程失败。每篇有 Executive summary/Timeline/Root cause/Guardrails。
3. **Defensive patterns**（docs/defensive-patterns.md 7 条）：独立事实独立上报、公共契约两侧归一、异步状态≠同步状态、dispose 必须到达静止、dispatcher 内捕获回调异常（一个坏 listener 不能饿死后续）、不给不可信输出环境变量/可预测路径（spawn 前清洗 *KEY*/*SECRET*/*TOKEN*/*PASSWORD*；临时文件 0700 + 随机名）、symlink 安全删除。
4. **Invariants**：几乎每个包一个 invariant 测试文件（运行时诊断），如 "model-visible means logged" 断言。
5. **文档生成管线**：module-graph、tool-catalog、capability-seams、event-producer-consumer、agent-lifecycle、config-catalog 全部由 scripts 生成 + verify 门禁（`pnpm run gen-doc-graphs` / `verify-doc-graphs`）——**文档与代码同源，杜绝漂移**。
6. **代码质量门禁**：覆盖率 per-file 100%（CI 门禁）、jscpd 克隆检测、knip、publint、workspace constraints、NodeNext consumer check、multi-platform CI（wine 跑 Windows 失败诊断）。
7. **发布工程**：同日连发 rc.1→rc.5；Python SDK 发布流程有专门 agent note（2026-08-11-python-publication-workflow）；THIRD_PARTY_NOTICES 由脚本生成。

---

## 八、与 nop-ai-agent 的深度对比（两侧均代码实测）

> 本节 nop 侧基于 `nop-ai/nop-ai-agent` 551 个 Java 文件逐类调研（ReActAgentExecutor/LlmCallCoordinator/AgentSecurityConsultation/PipelineCompactor/PlanExecutor/CheckpointJournal 等全量阅读）；dsh 侧同前。这是本目录首次对两个"完整工业级 harness"做逐机制对比。

### 8.1 核心循环：dsh 事件化 turn/step vs nop ReAct 迭代 + checkpoint 补丁

| 维度 | dsh | nop-ai-agent | 判定 |
|---|---|---|---|
| 循环结构 | turn（持久化事件，0..n steps）> step（1 次模型请求 + 工具批）> round | 双层 while：`reactLoop`（迭代计数）包在 `sustainLoop`（sustainer 扩展预算）内，单次迭代 = 1 次 LLM + 并行工具 fan-out（ReActAgentExecutor.java:441-443） | 语义等价：dsh 的 turn/step 是**持久化事件对象**，nop 的迭代是**内存计数**——恢复手段因此不同 |
| 状态持久化 | 一切 model-visible 输入写入 session 日志，重放即恢复（"model-visible means logged" 运行时不变式） | 消息数组回写 session（`replaceMessages`，DefaultAgentEngine.java:871）+ **checkpoint journal**（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR 四类，append-only journal.md + 幂等键 sha256 32hex）+ 幂等键发散检测（重算比对不一致→降级为 session replay） | **路线对照**：纯重放 vs 补丁式恢复。nop 的 journal 是"增量补丁 + 校验"，dsh 是"全量事件重放"。nop 的补丁式更省存储、dsh 的重放更简单一致 |
| 会话对象 | Session = 事件日志 + SurfaceManager 投影 | AgentSession = 扁平消息数组 + totalTokensUsed/totalIterations/planId（AgentSession.java:16） | nop 扁平、dsh 事件化 |
| 收件箱 | Inbox 双队列，splice 先写事件再改内存（崩溃可恢复未消费输入） | steeringQueue（ConcurrentLinkedQueue，Actor 线程写 ReAct 线程读，AgentExecutionContext.java:83）+ 消息服务（ai_agent_message 表 at-least-once） | 各有持久化，nop 靠 DB 表、dsh 靠事件日志 |
| 终止 | completion/无工具/escaped/force-stop/turn-stopping 串行 checkpoint | completion judge（Complete/Continue/Escalate，连续 Continue 上限 3）+ sustainer（STOP→truncated 终态，与 completed 区分）+ force-stop 硬保护（预估算 >0.9×max 先压缩再停）+ WAIT_FOR 挂起 + goal STUCK→escalated | nop 的终止路径更多、显式状态（10 种 AgentExecStatus）；dsh 更少但全持久化 |
| 循环治理扩展 | `agent/pre-step`（权威 reject/enter）、`agent/request`、`agent/turn-stopping` waterfall | 12 类生命周期 hook（PRE_REASONING/POST_REASONING/PRE_ACTING/POST_ACTING…）+ 4 类执行级中间件（PRE/POST_LLM_ATTEMPT、PRE/POST_TOOL_ATTEMPT）+ HookResult（Pass/Veto/Reenter/Bail） | nop 的钩子粒度更细（到 attempt 级），dsh 更结构化（waterfall 语义） |

### 8.2 上下文压缩：机制等价，各占半壁

| 维度 | dsh | nop-ai-agent |
|---|---|---|
| 触发 | token 压力阈值（agent/pre-step）+ CONTEXT_WINDOW_EXCEEDED 溢出（agent/request-error） | tokensUsed > 0.8×max 或 messages > 30（AgentCompactionCoordinator.java:55-61）；强制停止线 0.9×（预估算） |
| 压缩层级 | 单引擎（BasicCompactionEngine）：surface replace 遮蔽区间 + summarizeWithLlm（复用原对话前缀做一次调用，**KV cache 连续**） | **4 层升级**：ToolResultTruncator（>8000 字符截 6000 头+1000 尾）→ MicroCompression（旧工具结果换占位符，保留最近 maxRecentToolResults）→ Layer2TurnPruning（turn 归组剪枝，**保证 tool_call↔tool_response 配对完整性**，assertBoundaryIntegrity）→ Layer3FullSummary（7 段式 prompt：Goal/Constraints/Progress/Key Decisions/Next Steps/Critical Context/Relevant Files；失败优雅降级为 L2） | **nop 的分层裁剪策略更丰富**（4 级 + 配对完整性校验）；dsh 的遮蔽语义 + KV 前缀保持是 nop 没有的 |
| 计价 | shadowedSeqs/shadowedTokenCount 影子计价（被遮蔽 token 不再计费），compaction/start…end 事件对做并发锁 | 替换后 `ctx.tokensUsed -= (tokensBefore - tokensAfter)`（AgentCompactionCoordinator.java:128-135）+ CalibratedTokenEstimator（EMA α=0.3 clamp[0.25,4.0] 用实际 promptTokens 校准） | **双方都做"压缩后扣减计价"**；nop 的 EMA 校准估算更精，dsh 的阴影计价语义更显式 |
| 归档 | 无独立归档（surface 遮蔽即历史） | ReferenceCompactionStrategy 内容寻址归档 + read-ref 回读 + compaction snapshot archive（PRE_COMPACT 前归档） | nop 有显式归档+回读工具 |
| spill | 长工具结果落盘 + head/tail 预览 + locator 检索提示（best-effort） | 无对应（截断为主） | dsh 独有 |

### 8.3 容错策略：nop 全面领先（实测差距最大的一维）

| 容错机制 | dsh | nop-ai-agent | 判定 |
|---|---|---|---|
| LLM 重试 | request-error waterfall 返回 retry（无退避策略细节） | **6 类错误分类**（LlmErrorClassifier：TRANSIENT/RATE_LIMITED/NON_TRANSIENT/QUOTA_EXCEEDED/AUTH_INVALID/CACHE_STATE_LOST，cause 链解包）+ 指数退避**全抖动**（[0, min(base×2^n, 30s)]）+ **Retry-After floor**（服务器显式值优先）+ 流式已输出即 STOP | nop 显著更强 |
| 熔断 | 无 | ThresholdBreaker：per provider:model 键，阈值 3/冷却 60s，懒 HALF_OPEN（无后台定时器，冷却后首个调用者做唯一探针）+ 熔断感知路由扫描 fallback 链 | nop 独有 |
| 故障转移 | 无 | **三通道**：QUOTA/AUTH→账号链（同模型换 key）→ 跨 provider failover（冷却队列）；TRANSIENT→模型 tier 回退（SmartModelRouter 按复杂度分级 + 预算耗尽降级）；耗尽 fail-loud NopAiAgentException | nop 独有 |
| 工具容错 | timeout-policy（deadline 信号 + TOOL_TIMEOUT 结构化错误）+ repeat-tool 提醒 | 工具 orTimeout 300s（错误结果回喂不中断 batch）+ ToolResultTruncator + ChainRepairer 4 阶段修复（名称规范化/参数结构/类型强转/schema 清理，必填缺口不伪造）+ hook 重入上限 3 | nop 的工具修复链更全（dsh 无调用修复） |
| 卡死检测 | 无（靠 goal rounds 有限续轮） | SessionGoalTracker：滑动窗口 5、同签名工具调用重复 ≥3 → STUCK → escalated | nop 独有 |
| 恢复 | 事件日志重放 + interrupted turn 合成闭合 | checkpoint journal + 幂等键发散检测 + ScheduledRecoveryManager（60s 扫描：过期锁清理/30min 超时三路属主分类（本地取消/远程跳过/强制失败）/孤儿恢复/卡死团队任务）+ DbSessionTakeoverLock（CAS 两步 + 续租失败=租约丢失即中止防双执行） | **nop 的恢复工程是生产级**（多实例/跨 JVM/守护进程），dsh 重放简单但无守护 |
| 挂起原语 | 无显式 | WAIT_FOR 原语（IWaitCoordinator：NONE/SUSPEND/PROCEED 防重挂 + checkpoint 持久化条件 JSON） | nop 独有 |
| 续跑 | goal round driver 自动续轮 | SisypheanSustainer：at-least-once、maxSustainCount 硬上限、truncated 终态 | 机制相似（dsh 无 truncated 终态语义） |

### 8.4 安全：nop 纵深链 vs dsh 轻量 guard + OS 沙箱

| 维度 | dsh | nop-ai-agent |
|---|---|---|
| 调度路径安全 | approval policy（权限策略弹审批）+ guard（repeat-tool/timeout） | **7-checkpoint 链**（AgentSecurityConsultation.buildCheckpointChain，ReActAgentExecutor.java:874-884）：post-denial 指纹预拦（SHA-256 action 指纹防"重试守卫拒绝结果"循环）→ 工具访问（硬编码 deny list：bash/write-file 等 8 个）→ 权限矩阵（channel×SecurityLevel，WEBUI 全档/API 拒 RESTRICTED）→ 路径访问（.. 穿越/敏感前缀/symlink real-path 重检 fail-closed）→ L2 安全策略（level resolver + matrix）→ L3 审批门（RESTRICTED 防御性 deny，AutoApprove 触发启动 WARN）→ **写冲突（跨 session WriteIntent 原子 check-then-register + FailFastStrategy）** | nop 明显更深（7 层 vs 2 类 guard）；dsh 无 denial ledger/post-denial 指纹/写冲突 |
| 拒绝账本 | 无 | DefaultDenialLedger 阈值 3（超限→session paused + SESSION_PAUSED 事件）+ DBDenialLedger 持久化 | nop 独有 |
| 沙箱 | OS 原生机制（bwrap/Landlock/Seatbelt/Windows ACL restricted token），confine(argv) 契约 + denialSignatures 契约化 | NoOpSandboxBackend（基线非降级）/DockerSandboxBackend（all-or-nothing，隔离不可达必须抛 SandboxException，绝不静默回退 host） | dsh 的 OS 级沙箱是独特优势（fail-closed 语义 nop 也有） |
| 内容安全 | 无内置注入防护 | PromptInjectionGuardrail（OpenSquilla 4 正则：prompt_override/role_hijack/exfiltration/invisible_char，INPUT/OUTPUT 同规则，ENFORCE 默认 Block）+ RuleGraphGuardrail（BLOCK/MODIFY 链式） | nop 独有 |
| 治理 | 无 | IFencingTokenService（per-actor 单调计数器 + only-if-greater CAS 高水位防并发回退）+ quota（团队 8 成员/租户 10 并发）+ budget（成本预算 exceeded→模型降级信号，不中止）+ usage 记录（DB 落 nop_ai_chat_response） | nop 独有（治理完整度是本目录调研项目最高之一） |

### 8.5 计划/子代理/团队/记忆

| 维度 | dsh | nop-ai-agent | 判定 |
|---|---|---|---|
| 计划 | plan mode = 日志内状态 + 策略 prompt + exit_plan_mode 审批 | **PlanExecutor 独立状态机**：plan 冻结模板 + PlanExecutionState overlay、逐 phase 门控（retry 有界/block/escalate/require-explicit-verdict）、PlanReplanner（ROLLBACK_PHASE/SPLIT_TASK，收敛上限）、StagnationDetector、DAG 校验 | **nop 的计划执行器显著更强**（状态机 + 重规划 + 停滞检测）；dsh 是轻量模式切换 |
| 目标 | goal：CAS 修订 + maxGoalRounds 自动续轮（持久化会话投影） | goal-tracker 仅做卡死检测（STUCK→escalated），无持久目标对象 | dsh 的目标驱动机制更强 |
| 子代理 | 6 provider + capabilities 契约（outputSchema/depthLimit/toolFilter/persona）fail-loud + continuation 冷/热恢复 + reportFrom | call-agent 工具：async mailbox（REQUEST 信封 + 引擎 handler 回 RESPONSE）或 fork+exec 直调、深度上限 4、agentId 正则白名单；无 capabilities 契约 | dsh 的契约化子代理更强；nop 的 mailbox 异步委托是独有模式 |
| 团队 | 无 | team/：TeamManager + ACL（DefaultTeamAclChecker）+ 成员配额 8 + TeamTaskFlowOrchestrator（基于 TaskFlowManager 编排 + TaskStepReturn 通信）+ DB 三表 | nop 独有 |
| 记忆 | 无 memory 包（mcp-memory 是第三方 MCP 示例） | memory/：AiMemoryStore（key/value + priority + checksum）+ 向量/嵌入适配器 + Read/Write/SearchMemory 工具 + 1024 token 注入预算 | nop 独有（显式记忆） |

### 8.6 总结论（对比）

1. **核心逻辑**：两套成熟但路线相反的实现。nop 的 ReAct 循环 + checkpoint 补丁与 dsh 的事件日志重放在**恢复能力上等价**（nop 靠 journal + 幂等键校验，dsh 靠全量重放），但 nop 多出守护进程级恢复（60s 扫描/超时分类/孤儿检测/跨实例锁），生产形态更完整。
2. **容错**：nop 全面领先（错误分类/退避/熔断/三通道故障转移/修复链/卡死检测/挂起原语均优于或独有于 dsh）。这是本对比差距最大的一维。
3. **安全**：nop 7-checkpoint 纵深链远深于 dsh 的轻量 guard；dsh 仅在 OS 级沙箱（bwrap/Landlock/Seatbelt）占优——这符合两者定位：dsh 是单机开发者工具（信任本地进程 + 系统隔离），nop 是多租户服务（信任边界在应用层）。
4. **dsh 独有的三个机制**（nop 没有）：surface 遮蔽 + 阴影计价、KV 前缀保持的压缩摘要、spill 溢出存储、capabilities 契约子代理、目标轮次驱动。
5. **nop 独有**（dsh 没有）：熔断/故障转移/修复链/守护恢复/7 层安全链/内容护栏/计划状态机/团队/显式记忆/配额预算治理。

---

## 九、对 nop 的借鉴点与不可借鉴点（基于两侧实测）

### 可借鉴（机制/契约层）

1. **Surface 语义的 compaction 计价**：dsh 压缩 = 遮蔽区间 + 投影失效 + shadowedTokenCount 阴影计价。nop 已做到"替换后扣减 tokensUsed + EMA 校准"，差距在**遮蔽语义**（nop 是物理替换，dsh 是遮蔽 + 可审计阴影计数）。建议 nop 在 compaction/summary 事件里补"阴影 token 计价"字段，供审计与计费链路使用。
2. **KV cache 保持的摘要调用**：dsh 摘要复用原对话前缀一次性调用保持 provider 前缀缓存连续。nop 的 Layer3FullSummaryStrategy 是独立调用，建议改为"head + 摘要"拼接调用——在 DeepSeek 前缀缓存定价（缓存命中 0.02 元 vs 未命中 1 元/百万 token）下收益显著，无栈差异，可直接采用。
3. **capabilities 契约 + fail-loud**：dsh 的 subagent provider 声明 `{outputSchema, depthLimit, toolFilter, persona}`、委托前 assertCapabilities 拒绝而非降级。nop 的 call-agent 无此契约——建议补 provider 能力声明，深度上限从硬编码 4 改为契约字段。
4. **spill 溢出存储**：长工具结果落盘 + head/tail 预览 + locator 检索提示。nop 目前以截断为主（ToolResultTruncator 8000 字符），截断即丢失；spill 是"不丢 + 不膨胀"的替代方案，建议作为 Layer 0.5 插入。
5. **goal rounds 自动驱动**：dsh goal = 持久化会话投影 + CAS 修订 + idle 自动注入下一轮（maxGoalRounds 上限）。nop 的 goal-tracker 只有卡死检测——建议补"持久目标对象 + 轮次驱动"，与 nop 的 checkpoint/WAIT_FOR 挂起机制天然配合。
6. **双通道事件（持久事实 vs live 协调）**：nop 已有 AgentEventType 事件体系 + session/event 持久化，但未显式拆"可重放事实"与"实时协调"两个域。可对照整理事件分类，UI/审计消费前者、拦截消费后者。
7. **minimal 基准组合口径**：nop-ai-agent 的 headless 评测可对照 `jsonrpc-agent/minimal.cordis.yml`（2 工具 + 无压缩 + 全权限）——与 DeepSeek 官方基准同口径，便于横向对比 V4-Flash 成绩。
8. **文档生成管线 + invariant 门禁**：dsh 的 module-graph/event-map 由代码生成 + CI 校验。nop 的 docs-for-ai 体系可补同款生成器（nop 已有 module-groups 文档，可自动化）。

### 不可借鉴 / 需谨慎

1. **事件日志会话路线**：dsh 的 append-only 事件日志换来重放一致性，代价是全部状态（含收件箱/锁/计价）都要事件化。nop 的 checkpoint journal + 幂等键 + 恢复守护已达成等价恢复能力且适配多租户 DB 形态——**短期不应转向事件日志**，可先吸收"阴影计价"思想。
2. **技术栈整体**：TS/Node + Cordis + pnpm monorepo 与 nop Java/DSL 栈不可共享；Cordis 声明合并（TS module augmentation）Java 侧无对应物（Nop 用 XDef/DSL 承担类似角色）。
3. **模型写 JS 脚本的 workflow**：dsh 自己承认 vm 非安全边界。nop 走 xwf 确定性工作流 + 审批是正确路线；可借鉴的只是"agent() 经统一 seam 派发"。
4. **vendored 框架**：vendor 整个 Cordis 是深度绑定决策；nop 已有 Nop IoC 底座，不需要同款 vendor。
5. **无人值守默认全权限**：minimal 组合是 danger-full-access，仅适合官方基准场景；nop 服务端形态必须保留 7-checkpoint 安全链（nop 已远强于 dsh，不必回退）。
6. **pre-release 兼容性免责**：dsh 明言"将有破坏性变更"；nop 有企业用户，兼容承诺策略应相反。

---

## 十、Conclusion

- **dsh 是什么**：DeepSeek 官方的 agent harness（v0.1.0-rc.5，MIT，2026-08-13 公开），一切皆插件（vendored Cordis），事件溯源会话日志为唯一事实源，seam 三层抽象支撑全部能力。是 V4-Flash 官方 agent 基准（Terminal Bench 82.7 等）的"极简模式"载体，也是 DeepSeek 补齐"模型之外那层"、对标 Claude Code/Codex 的战略产品。
- **它证明了什么**：2026-08-01 harness-mechanism-reference-framework 的 12 大机制维度在一个开源产品里全部落地且互相咬合（compaction↔计价↔重放、subagent↔workflow↔goal 共享同一会话日志与 seam），说明"Harness 工程"已经从概念进入工程化成熟期；模型厂商亲自下场意味着 coding agent 竞争从模型层上移到 harness 层。
- **对比结论（两侧均实测）**：nop-ai-agent 与 dsh 是两条成熟路线的代表——**nop 以显式容错栈 + 纵深安全链 + 计划状态机换生产可用性（多租户服务形态），dsh 以事件日志 + 全量插件化换可恢复性与可替换性（单机开发者工具形态）**。nop 在容错（熔断/三通道故障转移/修复链/守护恢复）、安全（7-checkpoint 链/护栏/治理）和计划/团队/记忆维度全面领先或独有；dsh 在 surface 遮蔽计价、KV 前缀保持、capabilities 契约子代理、spill、目标轮次驱动五个机制上领先或独有。**双方不构成替代关系，nop 无需向 dsh 的路线迁移**。
- **对 nop 的建议（按优先级）**：(1) Layer3 摘要调用改为"head+摘要"拼接保持 KV 前缀缓存连续（低成本高收益）；(2) compaction/summary 事件补阴影 token 计价字段；(3) call-agent 补 capabilities 契约（深度上限 4 改为契约字段）；(4) ToolResultTruncator 之上补 spill 溢出存储；(5) goal-tracker 扩展为持久目标 + 轮次驱动。均属机制级吸收，无栈障碍。
- **后续工作**：若采纳上述机制，产出 `ai-dev/design/` 设计文档并拆 `ai-dev/plans/`。本分析对 nop-ai-agent 的结论基于源码逐类阅读，未做运行时实测（对照 dsh 亦无实测），如需定量对比建议后续补同任务双跑实验。

## Open Questions

- [ ] dsh 的 surface 遮蔽/阴影计价能否低成本映射到 nop 现有 compaction 事件模型（nop 为物理替换 + tokensUsed 扣减）？映射后对审计/计费链路的价值验证。
- [ ] nop Layer3FullSummaryStrategy 改为"head+摘要"拼接调用后，在 DeepSeek 前缀缓存定价下的实测成本节省（预计每压缩轮省 1-2 个数量级的输入成本，待验证）。
- [ ] call-agent 的 capabilities 契约设计：沿用 dsh 的 {outputSchema/depthLimit/toolFilter/persona} 四字段还是按 nop 场景裁剪（如加 tenant/approval 维度）？
- [ ] dsh 的 headless/jsonrpc 基准组合与 nop-ai-agent 评测体系对齐（工具集、配置、终止条件）后，能否复现/对齐 DeepSeek 官方 V4-Flash 基准成绩口径？
- [ ] DeepSeek 官方后续会否开源更完整的编码 agent（"DeepSeek Code"）？dsh 仅 web/headless 形态，桌面 IDE 形态未公开。
- [ ] 两套实现的双跑对比实验（同任务集、同模型 V4-Flash、成本/时长/成功率）——本报告为架构与机制对比，无运行时实测。

## References

- `~/ai/deepseek-harness`（deepseek-ai/deepseek-harness，今日 clone，12293 commits）
- `docs/architecture.md`、`docs/cordis-primer.md`、`docs/agent-lifecycle.md`、`docs/capability-seams.md`、`docs/defensive-patterns.md`、`docs/postmortem/0001-0004`、`docs/user/guide/{index,providers,python-sdk}.md`
- `vendor/README.md`（Cordis vendoring 决策）、`AGENTS.md`（仓库顶层）、`.agents/notes/README.md`（Agent Note 体系）
- nop 侧：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/`——`engine/ReActAgentExecutor.java`（循环/治理接线）、`engine/LlmCallCoordinator.java`（重试/熔断/三通道）、`engine/AgentSecurityConsultation.java`（7-checkpoint 链）、`compact/PipelineCompactor.java`（4 层压缩）、`plan/runtime/PlanExecutor.java`（计划状态机）、`reliability/{LlmErrorClassifier,StandardRetryPolicy,ThresholdBreaker,CheckpointJournalWriter}.java`、`runtime/recovery/ScheduledRecoveryManager.java`、`security/*`、`memory/*`、`team/*`、`NopAiAgentErrors.java`（错误码 nop.err.ai.agent.*）
- 官方：api-docs.deepseek.com 更新日志 2026-07-31（V4-Flash 正式版 + Harness 极简模式）、2026-08-13（V4-Pro 0813）
- 媒体：智猩猩 2026-08-12《DeepSeek Harness 浮出水面》、AIGC新知 2026-08-13《DeepSeek 悄悄更新 V4-Pro 正式版，harness 也不远了》、zicode 2026-08-05《deepseek-v4-flash 为什么适配 Codex 这么好》、36kr 2026-08-12
- 本目录：`2026-08-01-harness-mechanism-reference-framework.md`（12 大机制维度基准）、`2026-08-01-2026-07-blog-projects-survey-summary.md`
- GitHub API：orgs/deepseek-ai/repos（今日实测创建/推送时间、stars）