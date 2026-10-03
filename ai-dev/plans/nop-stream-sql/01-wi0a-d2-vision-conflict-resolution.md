# 01 WI0a D2 治理解冲突——vision 断言收窄修订

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/nop-stream-sql-roadmap.md（WI0a、Purpose「必须修订的断言全集」、D2 行）
> Related: ai-dev/design/nop-stream/00-vision.md、ai-dev/design/nop-stream/comparison.md、ai-dev/design/nop-stream/component-roadmap.md
> Owner: 仓库 owner（2026-10-02 执行指令委托，见 Phase 3「owner 授权证据」）
>
> 注：未来交付物路径按 roadmap 书写约定不加反引号（避免永久坏链）；已存在的文档路径用反引号。

## Purpose

解除 nop-stream 窄范围 SQL 接口 roadmap 的治理门：把 `ai-dev/design/nop-stream/` 三份治理文档中与窄范围 SQL 能力冲突的 **12 条断言**逐条修订为收窄表述（D2 = 收窄表述），每条记录修订前文本（含修订前行号）与修订后文本及依据，汇总落 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md（未来交付物，不加反引号）。完成后 roadmap WI0a 达成 done 条件，解除对 WI6/WI13/WI15 的门控。

## Current Baseline

- roadmap `ai-dev/backlog/nop-stream-sql-roadmap.md` v7（commit 2222e13995）已立项，31 工作项 + 7 里程碑全部 `todo`。
- D2 三个选项中 roadmap 建议**收窄表述**；D2 取「保留」时本 roadmap 除 Phase 1 外整体阻塞——本次裁定取收窄表述，不触发该分支。
- 12 条断言现状（2026-10-02 实测行号）：
  1. `00-vision.md:47` §四 `| 双流 Join（interval join / window join / broadcast join） | 复杂度极高，用例有限。可通过 CEP 或外部 lookup 替代 |`
  2. `00-vision.md:48` §四 `| SQL API | Nop 平台已有 GraphQL，流式 SQL 需求不迫切 |`
  3. `00-vision.md:54-64` §五 收敛路径五阶段，第 5 阶段仅列「连接器、CEP、XDSL 编排」，无 SQL 编译层 / join 算子插入点
  4. `00-vision.md:70` §六 #1 `StreamModel 的核心结构变更（如新增 Transformation 类型、修改 StreamComponents 注册表结构）`——该决策点未记录窄范围 SQL roadmap 已获授权的结构变更范围
  5. `00-vision.md:87` §七 `**去除**：复杂 Join、广播流、异步算子（...）`——未区分「复杂 join」与窄范围等值 join
  6. `comparison.md:187` §4.1 `| SQL | TableEnvironment.sqlQuery("SELECT ...") | Transform { Sql { ... } } | ❌ 明确不实现 |`
  7. `comparison.md:188` §4.1 `| 双流 Join | ds1.join(ds2)... | 通过 SQL 或外部插件 | ❌ 明确不实现 |`
  8. `comparison.md:271` §5.1 `| TwoInputStreamOperator<IN1, IN2, OUT> | 无等价物 | ❌ 不实现 |`
  9. `comparison.md:672` §14.2 `| 双流 Join | ✅ 完整 | ✅ SQL Join | ❌ 不实现 |`
  10. `comparison.md:673` §14.2 `| SQL API | ✅ Table API + SQL | ✅ SQL Transform | ❌ 不实现 |`
  11. `comparison.md:685` §14.2 `| 声明式编排 | ❌（SQL 除外） | ✅ HOCON | ⚠️ 规划 Phase 5 |`——XDSL 编排实际已由 StreamModelDslBuilder 落地（vision §一、§五 已载），该行口径过时且未提 SQL 编排
  12. `component-roadmap.md:13` `**核心取舍**：...去除复杂 Join、广播流、异步算子等高级特性，聚焦于单流窗口聚合 + CEP 模式匹配 + Checkpoint 容错。`——未区分复杂 join 与窄范围等值 join
- check-doc-links 基线（2026-10-02 实测）：`--strict` 退出码 1，其中 2 个 error 来自既有提交引入的 `ai-dev/design/nop-deepwiki/01-toolchain-absorption-design.md:75,163` 对 gitignored 目录 ai-dev/tools/node_modules 的反引号引用（该目录未 pnpm install 时不存在于工作树）。这 2 个 error 在本 plan scope 外，但会使「--strict 退出码 0」硬判据在任何纯文档计划中不可达成，故纳入本 plan 一并修复（去反引号，不改语义）。
- roadmap 前置裁定表 D2 行（roadmap :77）当前为「待裁 WI0a」；先例（D3、D14 裁定后该表更新为「已裁定 owner + 日期」）表明 WI0a 落档后该行须同步。
- `ai-dev/plans/nop-stream-productization/2026-09-01-0938-1-design-productization-gap-analysis.md:22` 的「裁定不得与非 goal 冲突」约束，按 roadmap Purpose 已裁定其作用域为该 plan 自身；实际解冲突由本 plan 统一执行。

## Goals

- 12 条断言逐条修订，全部落「收窄表述」：窄范围 SQL 能力（单表查询、静态维表 lookup join、双流**等值** join 的 hash/window 两种、窗口与分析窗口聚合）标记为规划中（引用 roadmap），代价优化器、非等值/范围 join、广播 join、Flink SQL 方言兼容、CEP 级复杂编排仍保持排除。
- 每条修订在 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md（未来交付物）记录：修订前文本 + 修订前行号、修订后文本、依据（roadmap 条款）。
- §五 收敛路径为 SQL 编译层与 join 算子给出显式插入点（不改变「不可逆序」约束本身）。
- §六 #1 记录窄范围 SQL roadmap 已获授权的 StreamModel 结构变更范围（新增 Transformation 类型与注册表条目，依 D14=(a) 与门控条款表）。
- 修复 deepwiki 文档 2 条既有 BROKEN_LINK error，使 check-doc-links --strict 全仓退出码 0 可达成。
- roadmap WI0a 状态推进至 `done`（在独立 closure audit 通过**之后**翻转）。

## Non-Goals

- 不修改 `00-vision.md` §三 约束与 §八 15 条设计不变量的任何内容（它们是被验证对象，不是修订对象）。
- 不裁定 D1/D4/D5/D6/D15（WI0b）、D7/D8/D13（WI0c）、D9-D12（WI0d）。
- 不改任何产品代码、grammar、xdef。
- 不处理 §七 #G36 广播流裁定（其排除立场与本次收窄一致，无需变更）。
- roadmap 自身只改两处：WI0a 状态行、前置裁定表 D2 行；其余内容不动。

## Scope

### In Scope

- `ai-dev/design/nop-stream/00-vision.md`：§四两行、§五插入点、§六 #1 裁决记录、§七一行（共 5 条断言）。
- `ai-dev/design/nop-stream/comparison.md`：:187、:188、:271、:672、:673、:685 六行。
- `ai-dev/design/nop-stream/component-roadmap.md`：:13 一行。
- `ai-dev/design/nop-deepwiki/01-toolchain-absorption-design.md`：:75、:163 两行去掉对 ai-dev/tools/node_modules 的反引号（消除既有 2 个 BROKEN_LINK error，不改正文语义）。
- 新建 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md（未来交付物）：D2 裁定记录（选项、结论、负责人、日期、owner 授权证据原文引用）+ 12 条断言修订前后对照表（含修订前行号）。
- roadmap `ai-dev/backlog/nop-stream-sql-roadmap.md`：WI0a 状态行、前置裁定表 D2 行。
- 当日 ai-dev/logs/2026/10-02.md（未来交付物）更新。

### Out Of Scope

- 其他 30 个 WI 的任何工作。
- `comparison.md` 中除上述 6 行外的其它行（即使表述过时）。
- `component-roadmap.md` 其余章节。
- deepwiki 文档除 :75、:163 外的其余内容。

## Execution Plan

### Phase 1 - vision 断言修订（断言 1-5）

Status: completed
Targets: `ai-dev/design/nop-stream/00-vision.md`

- Item Types: `Decision`

- [x] §四 `双流 Join` 行改为收窄表述：等值 join（hash / window merge）与静态维表 lookup join 纳入规划（引用 roadmap），非等值 / 范围 / 广播 join 仍排除
- [x] §四 `SQL API` 行改为收窄表述：窄范围流 SQL（EQL 复用、单表查询 + 窗口聚合 + 等值 join）规划中（引用 roadmap），Flink SQL / Table 栈复刻与代价优化器仍排除
- [x] §五 收敛路径第 5 阶段补 SQL 编译层与 join 算子的显式插入点（不改「不可逆序」表述）
- [x] §六 #1 下补一条裁决记录：窄范围 SQL roadmap（2026-09-30 立项、2026-10-02 owner 授权执行）已获授权的结构变更范围 = 新增 Transformation 类型（如 union/join 所需）与 StreamComponents 注册表新增条目（窗口参数化、聚合、join 声明面），并注明该授权不覆盖决策点 #2-#6
- [x] §七 `去除` 行改为「去除复杂（非等值 / 范围）Join、广播流、异步算子」，注明窄范围等值 join 已纳入规划并引用 roadmap

Exit Criteria:

- [x] 5 条断言修订后文本在 `00-vision.md` 中 grep 实测存在，且未改动 §三 / §八 / §九 / §十（独立 audit 实测：git diff 仅 §四/§五/§六/§七 4 hunk）
- [x] §六 新裁决记录含日期与授权依据（独立 audit：00-vision.md:84-86）
- [x] No new test required: 纯文档变更，无产品代码
- [x] ai-dev/logs/2026/10-02.md 已更新本 Phase 记录

### Phase 2 - comparison.md 断言修订（断言 6-11）

Status: completed
Targets: `ai-dev/design/nop-stream/comparison.md`

- Item Types: `Decision`

- [x] :187 SQL 行 `❌ 明确不实现` → 规划窄范围流 SQL（引用 roadmap），保持与 Flink SQL / SeaTunnel SQL 的差距表述（不做方言兼容 / 优化器）
- [x] :188 双流 Join 行 `❌ 明确不实现` → 规划等值 join（hash / window）与静态维表 lookup join（引用 roadmap）
- [x] :271 `TwoInputStreamOperator ❌ 不实现` → 规划中等值 join 需要双输入算子（引用 roadmap）
- [x] :672 §14.2 双流 Join 行 → ⚠️ 规划（窄范围等值 join，引用 roadmap）
- [x] :673 §14.2 SQL API 行 → ⚠️ 规划（窄范围流 SQL，引用 roadmap）
- [x] :685 声明式编排行 → 修正过时口径：XDSL 编排已落地（StreamModelDslBuilder），SQL 编排（窄范围流 SQL）规划中（引用 roadmap）

Exit Criteria:

- [x] 6 行修订后 grep 实测含「规划」与 roadmap 引用，且这 6 行无残留 ❌ 标记（2026-10-02 实测）
- [x] 表格列数与所在表一致，不破坏 markdown 表结构（:187/:188/:672/:673/:685 为 4 列，:271 为 3 列，实测保持）
- [x] No new test required: 纯文档变更，无产品代码
- [x] ai-dev/logs/2026/10-02.md 已更新本 Phase 记录

### Phase 3 - component-roadmap 修订、坏链修复与汇总落档（断言 12 + 汇总）

Status: completed
Targets: `ai-dev/design/nop-stream/component-roadmap.md`、`ai-dev/design/nop-deepwiki/01-toolchain-absorption-design.md`、ai-dev/design/nop-stream/sql-vision-conflict-resolution.md（未来交付物）

- Item Types: `Decision`

- [x] `component-roadmap.md:13` 核心取舍行改为收窄表述：去除复杂（非等值 / 范围）join、广播流、异步算子；聚焦单流窗口聚合 + CEP + Checkpoint，窄范围等值 join 与流 SQL 规划中（引用 roadmap）
- [x] `01-toolchain-absorption-design.md:75,163` 对 ai-dev/tools/node_modules 的引用去反引号（消除既有 BROKEN_LINK error；实际该两行共 3 处反引号引用，全部去除）
- [x] 新建 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md：D2 裁定记录（选项、结论、负责人、日期）+ owner 授权证据原文引用（见下）+ 12 条断言的修订前文本（含修订前行号）与修订后文本及依据逐条对照表
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2026-10-02 实测退出码 0）

owner 授权证据（原文引用，落汇总文档）：

> 用户指令（2026-10-02，会话 /goal）：「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」

该指令构成对按 roadmap 建议项执行全部裁定（含 D2 = 收窄表述）与本次 12 条修订文本的委托确认；独立 closure audit 须显式核验此授权解释与修订文本的一致性。

Exit Criteria:

- [x] 12 条断言全部修订且汇总文档含逐条前后对照（含修订前行号）
- [x] check-doc-links --strict 退出码 0（全仓，含既有 error 已消除）
- [x] No new test required: 纯文档变更，无产品代码
- [x] ai-dev/logs/2026/10-02.md 已更新本 Phase 记录

### Phase 4 - 收口：独立 closure audit 与 roadmap 状态翻转

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-roadmap.md`、ai-dev/audits/nop-stream-sql/wi0a-closure-audit.md（未来交付物）

- Item Types: `Proof`

- [x] 启动独立子 agent（不同 task_id）对本 plan 全部 Exit Criteria 与 Closure Gates 做 closure audit，audit 证据落 ai-dev/audits/nop-stream-sql/wi0a-closure-audit.md：逐条 PASS/FAIL + live 文件证据；audit 显式核验 owner 授权解释与 12 条修订文本一致性（裁定 PASS，2026-10-02）
- [x] audit 通过后：roadmap WI0a 状态行 `todo` → `done`（括注单层非嵌套、内部无右括号）；前置裁定表 D2 行同步为「**已裁定 owner 2026-10-02**（收窄表述，落档 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md）」
- [x] 重跑 mission-driver 解析器核对条目数仍为 31 工作项 + 7 里程碑：`node -e "import('./tools/mission-driver/src/roadmap-check.mjs').then(m=>{const r=m.parseRoadmapMarkdown(require('fs').readFileSync('ai-dev/backlog/nop-stream-sql-roadmap.md','utf8'));console.log('items',r.phases.filter(i=>!i.isMilestone).length,'milestones',r.phases.filter(i=>i.isMilestone).length)})"`（phases 为扁平条目数组，2026-10-02 实测；若模块演进则以实际导出为准，核对总数不变即可。翻转后实测输出 items 31 milestones 7，WI0a=done）
- [x] 本 plan Closure 段写入 audit 证据，Plan Status → `completed`
- [x] 最后运行 `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/01-wi0a-d2-vision-conflict-resolution.md --strict`（须在 Plan Status 翻转**之后**运行才有门禁效力；非零则回退修复后再翻转）
- [x] ai-dev/logs/2026/10-02.md 已更新收口记录

Exit Criteria:

- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql/wi0a-closure-audit.md
- [x] roadmap WI0a = `done` 且 D2 行已同步，解析器条目数 31 + 7 不变
- [x] check-plan-checklist --strict 退出码 0
- [x] ai-dev/logs/2026/10-02.md 收口记录与 roadmap、plan 三处一致

## Closure Gates

> 纯文档计划：本计划不涉及任何产品代码变更（全部文件在 ai-dev/ 与治理文档内），`./mvnw compile`、`./mvnw test`、hollow-scan 条目按 guide「纯文档计划」豁免条款删除。

- [x] 12 条断言逐条修订完成，无遗漏（对照 roadmap Purpose「必须修订的断言全集」清单 12/12，独立 audit 实测）
- [x] D2 裁定记录含选项 / 结论 / 负责人 / 日期，owner 授权证据原文已引用且经独立 audit 核验
- [x] ai-dev/design/nop-stream/sql-vision-conflict-resolution.md 与三份治理文档实际文本一致（独立 audit 逐条 diff 抽查，修订前行号经 git show HEAD: 验证）
- [x] 不存在被静默降级的 in-scope 断言
- [x] 既有 BROKEN_LINK error 已消除，check-doc-links --strict 退出码 0（实测）
- [x] 受影响 owner docs 已同步（本次修订对象即 owner docs 本身；roadmap D2 行已同步）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session 独立 agent，2026-10-02，裁定 PASS）
- [x] Anti-Hollow Check（文档版）：12 条修订均为实际文本变更而非占位；汇总文档与 live 文件逐条一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/01-wi0a-d2-vision-conflict-resolution.md --strict` 退出码 0

## Deferred But Adjudicated

（无——12 条断言与 deepwiki 坏链全部 in scope）

## Non-Blocking Follow-ups

- `comparison.md` 其余过时行（如 §4.1 其它行）如后续发现与 live baseline 漂移，归各自主 plan 处理，不阻塞本计划。
- `ai-dev/plans/nop-bytecode/06-resource-leak-v1.md` 的 3 条 BROKEN_LINK warning（基线即存在）不属本计划，留给该 plan 域。

## Closure

Status Note: D2 治理解冲突完成——12 条 vision 断言全部收窄修订，汇总落档 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md，roadmap WI0a 翻 done（解析器 31+7 复核），WI6/WI13/WI15 门控解除。纯文档计划，无产品代码变更。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 agent、执行 agent 均不同 task；audit 裁定 PASS）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi0a-closure-audit.md
- Evidence:
  - Phase 1/2/3 Exit Criteria 逐条 PASS（live grep + git diff 证据，见 audit 文件 §1）
  - 12 条断言对照 roadmap 全集 12/12 PASS；修订前行号经 git show HEAD: 验证正确
  - owner 授权核验 PASS，无超授权内容
  - check-doc-links --strict 退出码 0（实测）；mission-driver 解析器 items 31 milestones 7（翻转后实测）
  - audit 发现 M1（三条 Phase 1 判据未勾选）已按 audit 报告补勾；m1/m2 已在 audit 文件 §2 处置
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/01-wi0a-d2-vision-conflict-resolution.md --strict` 退出码 0（completed 后运行）
  - Anti-Hollow（文档版）：修订为实义文本变更、汇总与 live 一致

Follow-up:

- 见 Non-Blocking Follow-ups；无 plan-owned 剩余工作
