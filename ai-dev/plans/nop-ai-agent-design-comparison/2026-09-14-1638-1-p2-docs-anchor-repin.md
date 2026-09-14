---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-DOCS
group: "2026-09-14-1638"
verify: [test]
---

# P2 分析产物锚点重钉与文档漂移修复（7 份报告 + roadmap + owner docs）

## Current Baseline

- 来源：deep-audit round 1/2 的 P2 Follow-up Backlog（`ai-dev/backlog/nop-ai-agent-design-comparison-roadmap.md` 第 1/2/5/6/7/8/9/25 项）。roadmap M0–M6 全部 WI 已勾选；本轮从剩余 P2 集按序取文档/分析一致性类 8 项，代码类 P2 由同批 `2026-09-14-1638-2`/`-3` 计划覆盖，其余留后续轮次。
- 项 1（报告锚点漂移）：7 份报告 `ai-dev/analysis/compare-agent-design/{03-flow-agent-loop,04-extension-capability-matrix,05-extension-ordering,06-extension-composition,dsh-D9-session-persistence,dsh-D5-auto-failover,pi-D4-fault-tolerance}.md` 含 `.java:<line>` 锚点 154 处（唯一 file:line 约百组、无扩展名 `类名:行号` 引用约 50 处，精确口径以 Proof 脚本提取为准）。审计 round 1 估约 118 处已漂移。漂移源：plan 355 结构重构（ReActAgentExecutor 1053→1387）叠加 M5/M6 修复（当前 HEAD `4582e780dad4`：`ReActAgentExecutor.java` 1407 行、`LlmCallCoordinator.java` 872 行、`AgentToolDispatcher.java` 566 行）。全部类/机制锚点仍有效、结论不受影响，仅行号需重钉。
- 项 2（矩阵措辞）：`ai-dev/design/nop-ai-agent/03-extension-matrix.md:192`（§5.1 统计口径行）与 `:104`/`:203`（§4.2 表 / §5.2 清单的 IBudgetProvider 行）称唯一实现是 NoOp；live test scope 存在 `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/budget/InMemoryBudgetProvider.java`（implements IBudgetProvider），"唯一"措辞绝对化易被误读。
- 项 5（roadmap 机器校验声明不可满足）：roadmap `## Cross-Cutting` 宣称运行 `tools/mission-driver/src/roadmap-check.mjs`（指向本文件）得 `passed: true`；实测该脚本仅导出 `parseRoadmapMarkdown`/`roadmapAllDone`、无 CLI 入口，且解析格式为 bullet/table，不匹配本文件 checkbox 通道；AGE 模板的 ledger 校验（`scanRoadmapLedger`/`validateRoadmapFrontmatter`）不在本仓库副本中。
- 项 6（roadmap 基线快照漂移）：roadmap `## Current Baseline` 记 nop-ai-agent main java 536 文件；live 实测 535（`find nop-ai/nop-ai-agent/src/main/java -name '*.java' | wc -l`）；包计数 engine 42 / plan 66 / security 74 / team 55 / reliability 30 需按 live 复核。
- 项 7（nop-ai.md xbiz 计数）：`docs-for-ai/03-modules/nop-ai.md:126` 记 "42 个 xbiz 文件"；live main 资源 44（`find nop-ai -path '*/src/main/resources/_vfs*' -name '*.xbiz' | wc -l`）。
- 项 8（module-groups.md 表述）：`docs-for-ai/01-repo-map/module-groups.md:85` 记 nop-ai-agent "不直接依赖 core 内部包"；live nop-ai-agent main 直接 import `io.nop.ai.core.reliability` 类型（ThresholdBreaker/LlmErrorClassifier/ProviderFailoverChain/StandardRetryPolicy 等），表述不精确。
- 项 9（plan 356 广义表述）：`ai-dev/plans/356-nop-ai-error-codes.md`（completed，2026-09-13）记 AI-14 "裸 IAE/ISE 归零"；live 四模块 main 仍残留 10 处字面 `throw new UnsupportedOperationException` fail-fast 默认/NoOp 占位（agent 7：NoOpEmbeddingAdapter:32/:40、PlanReplanner:181、NoOpActorRuntime:46、NoOpAgentMessenger:48、IHookRegistry:39/:69；core 1：ILlmDialect:242；toolkit 1：IToolExecuteContext:73；shell 1：ExternalCommandAdapter:11），均为英文消息、接口 default/NoOp 占位、属 plan 356 范围外；另 NoOpTeamTaskStore/NoOpTeamManager 经 `notEnabled()` 的 9 个 `throw notEnabled()` 站点同属 UOE fail-fast 占位，登记时以 live grep 复核口径为准。审计建议在 plan Deferred 段补记清单防后续审核误判。
- 项 25（docs-for-ai 计数/锚点漂移）：`docs-for-ai/03-modules/nop-ai.md:20-32` 实体表仅列 11 行，live nop-ai-dao 实体 22 个（漏 NopAiChannelSession/NopAiEvent/NopAiGenFileHistory/NopAiProjectConfig/NopAiPromptTemplateHistory/NopAiRequirementHistory/NopAiSessionContext/NopAiSessionInput/NopAiSessionMessage/NopAiTestResult/NopAiTodo）；`docs-for-ai/03-modules/nop-auth.md:247` 段落两处锚点：`ChannelLoginApiBizModel.loginByScan`（`:144`）→ live `@BizMutation("loginByScan")` :146 / 方法 `loginByScanAsync` :148，`ERR_AUTH_MFA_REQUIRED`（`:216`）→ live `NopAuthErrors.java:97`。
- 纯文档任务：不改任何 Java 源码（`git status --short nop-ai/` 应保持空）；owner doc 属 `ai-dev/design/` 与 `docs-for-ai/`。

## Goals

- 7 份报告的 `.java:<line>` 与 `类名:<line>` 锚点重钉到当前 HEAD，锚点指向的类/方法/机制仍可解析；结论与既有章节结构不变（专项报告 03/04/05/06 按各自 WI2 登记结构；dsh-D9/dsh-D5/pi-D4 按 6 节模板）；每份报告头部记录重钉日期与 HEAD commit。
- roadmap 的机器校验声明与基线快照修正为与 live 一致的表述；plan 356 的 UOE fail-fast residual 清单（以 live grep 口径为准）被显式登记，消除广义"归零"误读。
- 03-extension-matrix.md §5.2 措辞补注 test-scope 实现；nop-ai.md / nop-auth.md / module-groups.md 的计数与锚点与 live 一致。
- `node ai-dev/tools/check-doc-links.mjs --strict` 保持 0 error。

## Non-Goals

- 不改任何 Java 源码或测试；不重新裁定任何 WI 的对比结论；不新增对比内容；不重跑三方探查。
- 不改 roadmap 工作项勾选状态与 WI 顺序（M0–M6 已完成勾选不动）；不新增工作项。
- 不处理 P2 集其余项（代码类由 `2026-09-14-1638-2`/`-3` 覆盖，第 19-23 项留后续轮次）。
- 不运行 mvn 构建与测试（纯文档任务）。

## Phase 1 — 7 份对比报告锚点重钉

Status: planned

Targets: `ai-dev/analysis/compare-agent-design/{03-flow-agent-loop,04-extension-capability-matrix,05-extension-ordering,06-extension-composition,dsh-D9-session-persistence,dsh-D5-auto-failover,pi-D4-fault-tolerance}.md`

- Item Types: `Fix | Proof`

- [x] `Proof` 用一次性脚本/命令逐份提取 `.java:<line>` 与 `类名:<line>` 锚点，与 live 文件解析结果比对，产出漂移清单（旧行号 → 新行号 + 目标构造签名）；对每条锚点确认目标行仍是所引机制（类/方法/字段级验证），不能只对行号。
- [x] `Fix` 逐条重钉行号：锚点指向的类/方法/字段按新行号解析成立；机制已迁移的锚点标注新位置或按事实修正类名/方法名（结论文字不变）。
- [x] `Fix` 每份报告头部更新"锚点重钉"说明（日期 2026-09-14 + HEAD `4582e780dad4`），保留原分析日期与结论；报告章节结构不动（专项报告各自结构；D 报告 6 节模板）。
- [x] `Proof` 逐份 diff 复核：改动仅限行号与头部说明，无结论性文字变化。

Exit Criteria:

- [x] 7 份报告全部锚点重钉完毕；每份抽查 ≥5 个锚点（共 ≥35）可解析到声明的类/方法。
- [x] 无结论性文字改动；报告章节结构完整（专项报告 ①–⑤ 结构；dsh-D9/dsh-D5/pi-D4 按 6 节模板 + References），无结构性改动。
- [x] **端到端验证**（不适用）：纯文档修订，无运行时路径。
- [x] **接线验证**（不适用）：无新组件与已有组件协作。
- [x] **无静默跳过**（不适用）：无代码变更。
- [x] 报告本身即交付物；`No owner-doc update required`（不改 owner docs）。
- [x] No new test required: 纯文档修订，无代码变更。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 2 — roadmap 声明修复 + plan 356 residual 登记

Status: planned

Targets: `ai-dev/backlog/nop-ai-agent-design-comparison-roadmap.md`、`ai-dev/plans/356-nop-ai-error-codes.md`（Deferred 段事实性更正）

- Item Types: `Fix | Proof`

- [x] `Fix` roadmap 的机器校验声明（两处：`## Cross-Cutting` :188 与 `Framework / Platform Reuse` 表 :148）：删除/改写不可满足的 `roadmap-check.mjs passed: true` 要求，改为实际可执行的自检命令（如 check-doc-links + checkbox 人工/脚本核对），或按事实登记"本仓库无对应 ledger 校验器"；不得保留无法满足的门禁表述。
- [x] `Fix` roadmap `## Current Baseline`：536→535，按 live 复核包计数（engine 42 / plan 66 / security 74 / team 55 / reliability 30）与探测日期，标注复核 HEAD。
- [x] `Fix` plan 356 `## Deferred But Adjudicated` 增补一条 factual residual：UOE fail-fast 占位清单（以 live grep 口径为准——审计基线 10 处字面 `throw new UnsupportedOperationException` 站点：NoOpEmbeddingAdapter:32/:40、PlanReplanner:181、NoOpActorRuntime:46、NoOpAgentMessenger:48、IHookRegistry:39/:69、ILlmDialect:242、IToolExecuteContext:73、ExternalCommandAdapter:11；另 NoOpTeamTaskStore/NoOpTeamManager 经 `notEnabled()` 的 9 个 `throw notEnabled()` 站点，逐条核对是否同属范围外并登记；每条含 类:行 + 模块 + 英文消息 + plan 范围外裁定依据），`Why Not Blocking Closure` 说明这是对广义"归零"表述的事实性更正（plan guide Minimum Rule #20 允许的事实性修复，非模板回写），`Successor Required: no`；同步 daily log 记录。
- [x] `Proof` 复核 roadmap Follow-up Backlog 对应项（1/5/6/9）的处置与残留说明一致；plan 356 登记的 UOE 站点可逐条在 live 找到（含 team `notEnabled()` 站点）。

Exit Criteria:

- [x] roadmap 不再含不可满足的机器校验声明（Cross-Cutting 与 Reuse 表两处均收口）；基线计数与 live 一致（含复核命令与日期）。
- [x] plan 356 Deferred 段含完整 UOE residual 清单（10 处字面站点 + team `notEnabled()` 站点，以 live grep 为准）；广义"归零"误读消除，WI/勾选状态未动。
- [x] **端到端验证**（不适用）：纯文档修订，无运行时路径。
- [x] **接线验证**（不适用）：无新组件协作。
- [x] **无静默跳过**（不适用）：无代码变更。
- [x] No new test required: 纯文档修订。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Phase 3 — owner docs 与 docs-for-ai 漂移修复

Status: planned

Targets: `ai-dev/design/nop-ai-agent/03-extension-matrix.md`、`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/nop-auth.md`

- Item Types: `Fix | Proof`

- [x] `Fix` `03-extension-matrix.md` 的 IBudgetProvider 表述补注 test-scope `InMemoryBudgetProvider` 实现（§4.2 表 :104 与 §5.2 清单 :203 的"唯一实现是 NoOp"措辞）；§5.1 统计行（:192）的 🟡 计数口径如需变动按 live 重算并同步。
- [x] `Fix` `nop-ai.md:126` xbiz 计数 42→44（记录复核命令 + 日期）；实体表补全至 22 实体（表名与用途逐行核对 ORM 模型）。
- [x] `Fix` `module-groups.md:85` 表述改为"token 估算经 bridge，可靠性机制直接复用 core.reliability 包"（或同等准确表述），与 live import 一致。
- [x] `Fix` `nop-auth.md:247` 段落两处锚点重钉：`loginByScan` `:144` → `ChannelLoginApiBizModel.java:146/:148`，`ERR_AUTH_MFA_REQUIRED` `:216` → `NopAuthErrors.java:97`。
- [x] `Proof` 文档中每个新锚点/计数可回溯到 live 命令或文件行；实体表与 `nop-ai-dao` 实体文件一一对应。

Exit Criteria:

- [x] 4 份文档修订完成，每处计数/锚点有 live 依据。
- [x] nop-ai.md 实体表 22 行与 nop-ai-dao 实体一一对应（diff 为空）。
- [x] **端到端验证**（不适用）：纯文档修订，无运行时路径。
- [x] **接线验证**（不适用）：无新组件协作。
- [x] **无静默跳过**（不适用）：无代码变更。
- [x] No new test required: 纯文档修订。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（新/改锚点均可解析）。
- [x] `ai-dev/logs/2026/09-14.md` 对应条目已更新。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-14-1638-1-p2-docs-anchor-repin-1-1702e6a3 to opencode-pid-69897
- 2026-09-14：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-14-1638-1-p2-docs-anchor-repin-1-1702e6a3

## Verification

- pass test 20260914175558 exit=0
- pass test 20260914195236 exit=0

## Closure

- dispatch audit #audit-20260914175558-2026-09-14-1638-1-p2-docs-anchor-repin-1-a4234a89 to opencode-closure-audit-87210 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260914175558-2026-09-14-1638-1-p2-docs-anchor-repin-1-a4234a89：3 Phase 全部独立复核落地——Phase 1 七份报告锚点重钉抽查（sustainLoop :419-420、PRE_CALL :614-623、doLlmCallWithRetry :160、scanOnce :404、computeIdempotencyKey :210、dsh agent.ts:249-255 等 43 处）全部解析成立且 diff 仅行号+头部行；Phase 2 roadmap 门禁改写与 536→535/包计数（engine 42/plan 66/security 74/team 55/reliability 30）按 live 复核一致、plan 356 UOE residual 清单与 live grep 逐条吻合（10 字面 UOE + 10 notEnabled）；Phase 3 四处 owner doc 锚点/计数全部 live 验证（xbiz 44、ORM 22 entity、dao 22 实体类、InMemoryBudgetProvider:37、ChannelLoginApiBizModel:146/:148、NopAuthErrors:97、ScanLoginResult:32、ILoginSpi:43、core.reliability 44 处直连 import）；check-doc-links --strict exit=0（8 warnings 为其他计划存量）；nop-ai/ 零代码变更
- dispatch audit #audit-20260914195236-2026-09-14-1638-1-p2-docs-anchor-repin-2-123136ac to opencode-closure-audit-26447 models={exec:opencode-go/deepseek-v4-flash,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260914195236-2026-09-14-1638-1-p2-docs-anchor-repin-2-123136ac：本次独立收口复核通过——机械修复 `## Verification` pass 行前导空格后 completion formula 可派生（derivedCompleted: true，36/36 items）；test 命令本次实测 `node ai-dev/tools/check-doc-links.mjs --strict` = 0 error（8 warnings 均为其他计划存量）；Phase 1 七份报告锚点抽查（ReActAgentExecutor:1059/1148/1206、LlmCallCoordinator:160/784、AgentToolDispatcher:118、ScheduledRecoveryManager.scanOnce:404、AgentHookInvoker.invokeHooks:142、AgentExecutionResult.fromContext:61 等）全部解析成立；Phase 2 roadmap 门禁改写 + 535/包计数 + plan 356 UOE residual 清单与 live 吻合；Phase 3 owner docs（xbiz 44、ORM 22 entity、dao entity 22、InMemoryBudgetProvider:37、ChannelLoginApiBizModel:146/:148、NopAuthErrors:97、ScanLoginResult:32、ILoginSpi:43、module-groups 按 live import）逐条 live 验证一致；`git status --short nop-ai/` 为空（零代码变更）
