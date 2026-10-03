# 2282 WI1 吸收候选基线复核与划界裁定（refactor roadmap M0）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI1（M0 划界复核与吸收裁定）
> Related: `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（划界对象，20 项）

## Purpose

完成 refactor roadmap WI1：对 M1–M7 全部吸收候选按执行时 HEAD 复核，与 autonomous roadmap 20 项逐项去重裁定，产出裁定表一份（M1–M7 候选 × autonomous 20 项），含三项必裁（① pi-D4 建议2 重试前摘除失败响应复核；② WI20 与 autonomous WI8 同靶合并裁定；③ WI21 与 autonomous WI3 观测/消费分界复核）。裁定结论落当日 log；后续各 WI plan 拟制时在其 Current Baseline 链接裁定表（本 plan 交付时各 WI plan 尚不存在，链接义务 defer 至各 WI plan 拟制时履行）。

## Current Baseline

- 基线快照声明复核（2026-10-02 @ HEAD `86ad2c389d`）：2026-09-25 之后 nop-ai 侧落地仅 RAG/向量相关（K1 `c05c2c7600` 在 `nop-ai-core` embedding 面；K2 `97be7d367a` 跨 `nop-ai-core` vectorstore 面 + 新建 `nop-ai-rag` 模块）与 nop-stream/nop-jq/nop-metadata，未触碰 `nop-ai-agent`、`nop-ai-api`（ChatUsage/ChatMessage/ILlmDialect）、`nop-ai-shell` 执行模型面——refactor roadmap "autonomous roadmap 之后无相关代码落地" 的前提成立。
- 必裁①代码证据（live 复核 2026-10-02）：
  - retry 循环完全在 `LlmCallCoordinator.runRetryLoop`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java:206`）内部，失败响应只存在于 `RetryState.attemptResponse`，重试耗尽走 `fallbackExhausted` 异常路径（`:170-172`）；
  - LLM 成功响应产生的 assistant 消息在 `ReActAgentExecutor.recordLlmTurn`（`ReActAgentExecutor.java:959` `ctx.addMessage(assistantMsg)`）追加，唯一调用点 `:711` 前置 `:707` `isSuccess()` 守卫；另有 `:842`（input guardrail block 合成 assistant 消息）为非 LLM 失败响应路径——**LLM 失败响应从不入上下文**，pi-D4 建议2 的"重试前摘除失败响应"在 nop 为结构性已满足。
- 必裁②代码证据：`AnthropicDialect.convertMessage` 请求侧 cache_control 仅 providerHints 透传且置于 message map 顶层（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:603-604`）；autonomous WI8（放置纠错：顶层→content block 级）与 refactor WI20（放置框架+断点引擎，替换 providerHints 手工透传）同靶此代码区域。
- 必裁③代码证据：
  - 解析面：`ChatUsage.cacheHitTokens/cacheCreationTokens/cacheMissTokens` 在位（`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUsage.java:38-48`）；`AnthropicDialect` 响应侧已解析（`:369-381`）；OpenAI/Ollama/Gemini 走 `AbstractLlmDialect` 通用通道（path 配置 `LlmResponseModel.promptCacheHitTokensPath/promptCacheCreationTokensPath`，生成类 `_LlmResponseModel.java:42-52`），仓库内无 provider 配置默认值（开箱不生效）；`ResponsesDialect.parseUsageFromResponses:465` 无缓存字段。
  - 消费面：`UsageRecord`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/UsageRecord.java:30-41`）只有 promptTokens/completionTokens/duration 等，**无 cache 字段**；`IUsageRecorder.record(UsageRecord)` 单方法接口在位。
- autonomous roadmap 20 项现状：全部未勾选（`audit-rounds: 0`），无任何一项已落地或已移交。

## Goals

- 产出裁定表：M1–M7 全部吸收候选（WI2–WI23 + WI25 共 23 项）× autonomous 20 项的逐项去重裁定结论，落 `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（Status: resolved）。裁定结论四分类沿用 roadmap WI1 原枚举：**本 roadmap 吸收 / 已落地无需做 / 移交 autonomous / 裁定移出**；与 autonomous 无重叠的项显式写"无重叠"。
- 反向覆盖：裁定表附 autonomous WI1–WI20 反向索引，20 项逐项标注"被 refactor 某 WI 裁定覆盖 / 无对象"，确保"逐项去重"双向可验证。
- 三项必裁显式裁定并给出代码锚点：① pi-D4 建议2（复核结论：已满足/需做）；② WI20 vs autonomous WI8 归属裁定（预期结论：合并归属 refactor WI20，以执行时复核为准）；③ WI21 vs autonomous WI3 分界裁定。
- 当日 log 条目记录裁定摘要与裁定表链接。

## Non-Goals

- 不执行任何 M1–M7 工作项本身（裁定表只裁归属与去重，不写实现方案）。
- 不修改 autonomous roadmap 的 checkbox 状态或工作项顺序（只允许 WI8 条目上以交叉引用式文字加注裁定结论，不触碰其状态通道）。
- 不更新 `ai-dev/design/` owner docs（裁定结论由后续各 WI 执行时同步到对应 owner doc）。

## Scope

### In Scope

- 新建 `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（裁定表 + autonomous 反向索引 + 三项必裁结论 + 复核证据锚点）。
- `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`：WI1 checkbox 勾选（收口时）+ Current Baseline 增加一行指向裁定表。
- `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`：仅 WI8 条目加注一行交叉引用（必裁②，措辞如 "WI1 裁定 2026-10-02：与 refactor roadmap WI20 同靶，合并归属见 `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`"），不改 checkbox、不改语义、不改顺序。
- `ai-dev/logs/2026/10-02.md`：当日条目（文件不存在则创建）。
- `node ai-dev/tools/check-doc-links.mjs --strict` 保持 0 error。

### Out Of Scope

- M1–M7 任何执行项的代码变更。
- autonomous roadmap 其余 19 项的任何状态变更。

## Execution Plan

### Phase 1 - 复核与裁定表产出

Status: completed
Targets: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`

- Item Types: `Decision`

- [x] 撰写裁定表：23 项候选（WI2–WI23 + WI25）逐项给出四分类去重结论（本 roadmap 吸收 / 已落地无需做 / 移交 autonomous / 裁定移出），与 autonomous 有接触面的项列出全部相关 autonomous WI id，无重叠的显式写"无重叠"
- [x] 裁定表附 autonomous WI1–WI20 反向索引（每项标注"被 refactor 某 WI 裁定覆盖 / 无对象"）
- [x] 三项必裁各占独立小节，附 Current Baseline 已核代码锚点；必裁②结论标注"预期结论，以 WI20 动工时复核为准"
- [x] 裁定②在 autonomous roadmap WI8 条目加注一行交叉引用（仅此一处变更）

Exit Criteria:

- [x] 裁定表覆盖 refactor roadmap M1–M7 全部 23 个执行/裁定项（WI2–WI23 + WI25），每项有明确四分类结论
- [x] autonomous WI1–WI20 反向索引 20 项逐项在位，双向覆盖可验证
- [x] 三项必裁结论明确且含 live 代码锚点（文件 + 类/方法 + 必要 :line）
- [x] 裁定表 Status: resolved，Conclusion 一句话结论在位
- [x] autonomous roadmap 仅 WI8 一条加注变更（git diff 验证）
- [x] No owner-doc update required（裁定结论由后续 WI 执行时同步到对应 owner doc）
- [x] `ai-dev/logs/2026/10-02.md` 条目已创建并含裁定表链接

### Phase 2 - roadmap 回填与工具检查

Status: completed
Targets: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`

- Item Types: `Decision`

- [x] refactor roadmap Current Baseline 增加裁定表链接行（WI1 checkbox 勾选不在本 Phase——勾选属收口动作，见 Closure 流程）

Exit Criteria:

- [x] refactor roadmap Current Baseline 链接行在位，WI1 checkbox 仍未勾选
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI1 checkbox → git commit（plan + 裁定表 + 两 roadmap + log）→ 本 plan 标 completed。WI1 勾选与 commit 属收口动作，不属于任何 Phase。

- [x] 裁定表产出且三项必裁结论明确（含代码锚点）
- [x] autonomous roadmap WI8 加注完成且无其他改动
- [x] refactor roadmap WI1 checkbox 勾选（audit 通过后）+ Current Baseline 链接行在位
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] No owner-doc update required（显式裁定，理由：纯裁定项，owner doc 同步由后续 WI 执行承担）
- [x] 无代码变更（纯文档/裁定计划，`./mvnw test` 不适用，按 plan guide 纯文档计划条款豁免）
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: 裁定表 `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md` 已产出（23 项候选全部本 roadmap 吸收；必裁①已满足登记不做、② autonomous WI8 移交 WI20 合并执行、③ WI21/WI3 正交分层），autonomous roadmap WI8 已加注，refactor roadmap WI1 已勾选，log 已记录——WI1 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_2b3617c3-c055-4d96-95a1-ec3dd188b896（fresh session）
- Evidence:
  - 裁定表 23 行候选逐项四分类 PASS + autonomous WI1–WI20 反向索引 20 项在位
  - 代码锚点 7 处逐字核对全部 PASS（LlmCallCoordinator:206/:170-172、ReActAgentExecutor:959/:707/:711、AnthropicDialect:603-604、ChatUsage:38-48、UsageRecord:30-41）
  - autonomous roadmap git diff 恰一行（WI8 加注）；refactor roadmap Current Baseline 链接行在位
  - `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（0 error；3 warning 为 nop-bytecode 预先存在项）
  - 反向索引与两 roadmap 原文一致性抽查 PASS（含 WI20"先动工者"条款与裁定无冲突验证）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2282-wi1-dedup-ruling-vs-autonomous-roadmap.md --strict` exit 0（收口勾选后运行）
  - 纯文档计划：`git diff --name-only` 在 ai-dev/ 外为 0，mvn 验证按纯文档条款豁免
  - Deferred 项分类检查：无 deferred 项；必裁①"已满足登记不做"是裁定结论而非降级

Follow-up:

- no remaining plan-owned work
