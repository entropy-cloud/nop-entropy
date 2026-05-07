# Plan Authoring And Execution Guide

> Status: active workflow guide
> Last Reviewed: 2026-05-05
> Sources: `docs/logs/2026/03-31.md`, `docs/logs/2026/04-03.md`, `docs/logs/2026/04-04.md`, `docs/logs/2026/04-07.md`, `docs/logs/2026/04-08.md`, `docs/logs/2026/04-09.md`, `docs/logs/2026/04-10.md`, `docs/logs/2026/05-04.md`, `docs/logs/2026/05-05.md`

## Goal

这份指南只解决两件事：

- 计划怎么写才不容易缺项。
- 计划执行完后怎么审，才不容易漏 phase 或剩余工作。

`docs/plans/` 是执行文档，不是 ideas dump，也不是 architecture 的替代品。

## Lessons From History

从 `docs/logs/` 看，最常见的问题只有 5 类：

1. 没先审 current baseline，直接沿用旧计划或旧 completion note。
2. 一个计划过宽，后面不得不重写或拆分。
3. 只记录最近 landing 的改动，没有回头逐条核对整个 plan。
4. 剩余工作没有明确归属，导致计划看起来完成，实际上还有隐含 debt。
5. 把“最近一个 slice 已 landing”误当成“整份 plan 可关闭”，缺少独立 closure audit。
6. 看到接口、类型、方法名已经出现，就误判对应语义已经完整落地，没有继续核对 live behavior 和 focused tests。
7. 顶部 `Plan Status`、slice `Status`、phase `Exit Criteria`、`Closure Gates` 没有一起同步，导致文本里同时出现“completed”与未勾选 closure checklist 的矛盾状态。

所以本指南只保留最少规则，并把它们直接体现在模板里。

## Minimum Rules

1. 写计划前先核对 live repo，再写 `Current Baseline`。
2. 一个计划只负责一个明确结果面；过宽就拆成新 plan。
3. 必须有 `Goals` 和 `Non-Goals`，因为历史上真正稳定的计划几乎都靠这两段防止 scope drift。
4. 必须有 plan 级状态、execution-slice 级状态、Closure Gates。
5. 不要求给 `Purpose`、`Scope`、`Risks` 这类说明段落单独标记完成状态。
6. 只有当前 scope 真正完成，且 leftover 已明确移出 debt，才能标 `completed`。
7. 旧 baseline 失效时，显式写 `Outdated Note`、`Supersession Note` 或 `replaced/superseded`，不要让多套 baseline 并存。
8. `completed` 必须来自单独的 closure audit，不要在完成最后一个编码 slice 的同时顺手宣布 plan 关闭。
9. 任何 execution slice 只要还有一项未完成、blocked、或未移出 scope，plan 就不能标 `completed`。
10. 如果目标本身是一个用户可感知的完整 feature，优先写成能收口该 feature 的完整实现计划；不要默认先拆成多个彼此依赖的零散计划，除非 live repo 证据已经表明该 feature 无法由一个 owner plan 清晰收口。
11. 关闭计划时，必须区分“contract surface 已出现”和“contract semantics 已落地”；前者不能替代后者。
12. 标记 `completed` 前，必须完成一次由独立审阅者或独立子 agent 执行的 closure audit，并把证据写进 plan 或对应 daily log。self-audit 可用于执行中的自查，但不能替代 `completed` 所需的独立 closure audit。
13. **已经进入 `lint`、静态检查脚本、或 CI fail-fast 的固定规则，都是不可降级的硬约束。** 计划里不能把这些规则改写成 advisory、follow-up、或“如有时间再做”的事项。
14. `docs/architecture/` 下的文档**只描述当前最新设计状态**：最终方案、选择原因、拒绝的替代方案及原因。不写历史变迁、不写"Proposed vs Current"对比、不写演进叙事。如果一个 design doc 包含 "Proposed Design" 或 "Current vs Proposed" 章节，说明它还停留在 draft 状态，实施完成后必须重写为最终设计文档。
15. 每个 execution item 都必须能被归类为 `Fix`、`Decision`、`Proof`、或 `Follow-up`。已确认的 live defect 或 contract drift 只能属于 `Fix`，不能降级成 `Follow-up`。
16. 允许 deferred 的是优化项或已裁定为 non-blocking 的 residual，不允许 deferred 的是已确认且仍在 scope 内的 live defect、contract gap、owner-doc drift、以及未满足的硬门禁。
17. 如果某个 Phase 改变了 live baseline、public contract、或 owner behavior，该 Phase 的 Exit Criteria 必须包含相应文档更新项。纯测试拆分、纯工具整理、纯内部重构可以显式写明 `No owner-doc update required`，但不能默默跳过文档裁定。
18. **Checklist 打勾是 closure 的前置条件，不是附带动作。** 执行完一个 item 后必须立即将对应 `- [ ]` 改为 `- [x]`。标记 `Plan Status: completed` 时，文件内不得残留任何未勾选的 in-scope checklist item。如果存在未勾选项，要么完成它，要么显式移入 `Deferred But Adjudicated` 并写清原因。
19. **标记 `completed` 前，必须做一次文本一致性核对。** 至少逐项确认以下五处彼此一致：`Plan Status`、每个 slice 的 `Status`、每个 slice 的 `Exit Criteria`、`Closure Gates`、以及对应 `docs/logs/` 收口记录。任何一处仍显示未完成，都不能把 plan 视为真正关闭。

## Anti-Slacking Rule

计划可以延期优化工作，但不能延期“这个 in-scope 项目到底是不是 closure 必需项”的裁定。

写法要求：

- 每个 in-scope 项在计划关闭前，必须落到且只落到以下一种状态：`landed`、`adjudicated as residual-risk-only / watch-only`、`moved to explicit successor ownership`、或 `removed from scope through a recorded scope change`。
- 不允许用 `optional`、`if time permits`、`consider`、`maybe`、`nice to have`、`as needed` 这类模糊词替代状态裁定。
- 已确认的 live defect、contract drift、owner-doc drift、或硬门禁失败项，不能放进 non-blocking follow-up。
- 可以延期的项，必须写清 `Why Not Blocking Closure`，说明为什么它不影响当前 supported baseline 成立。

### Non-Degradable Items

以下事项不能在 plan 中降级成 advisory 或 non-blocking：

- `lint` / 静态检查脚本 / CI fail-fast 中已经固定的仓库规则
- 已确认的 live defect
- 已确认的 public-contract drift
- 已确认的 owner-doc 与 live baseline 不一致
- 为已落地行为提供的必要 focused verification

### Allowed Deferred Classifications

只有以下类型可以进入 deferred / non-blocking 区域：

- `watch-only residual`
- `optimization candidate`
- `out-of-scope improvement`

它们都必须附带明确的 non-blocking 理由；没有理由的 deferred 项按未完成处理。

## Required Status Markers

### Plan-Level Status

每个 plan 顶部必须有：

- `> Plan Status: proposed | planned | in progress | partially completed | completed | superseded | replaced | deferred | cancelled`
- `> Last Reviewed: YYYY-MM-DD`
- `> Source: <<说明>>`

说明：

- `proposed` 适合已经成型但还未进入正式执行的计划。
- `superseded` / `replaced` 适合历史计划或已被新计划接管的计划。
- `deferred` 适合明确延后、不作为当前 active queue 的计划。

### Execution-Slice Status

每个 execution slice 都必须有自己的状态。slice 可以是顺序 `Phase`，也可以是并行 `Workstream`。

- `planned`
- `in progress`
- `completed`
- `blocked`
- `cancelled`

推荐做法：

- 顺序执行的计划：用 `Phase`。
- 可并行或按主题拆开的计划：用 `Workstream`。
- `## Phase Status` / `## Workstream Status` 总表是可选的，不是强制的；真正强制的是每个 slice 本身要写 `Status: ...`。

### Checklist Status

执行和验收项统一用 checkbox：

- 未完成：`[ ]`
- 已完成：`[x]`

## Template

下面这个模板就是默认格式。`<<说明>>` 保留为占位提示，写 plan 时直接替换。

历史核对后的结论：

- `Goals` / `Non-Goals` 应保留，它们在近期高质量计划里几乎是稳定项。
- `Phase Status` 总表不应强制，因为历史上很多好计划只有 slice 内状态，没有总表。
- `Phase` 与 `Workstream` 都应允许，取决于任务是顺序还是并行。
- `Closure Gates`、`Deferred But Adjudicated`、`Non-Blocking Follow-ups` 用来防止把真实问题伪装成“后续再说”。

```md
# NN <<计划标题>>

> Plan Status: planned
> Last Reviewed: YYYY-MM-DD
> Source: <<关联 architecture / analysis / logs>>
> Related: <<相关计划，可选>>

## Purpose

<<这份计划要把什么收口到什么状态>>

## Current Baseline

- <<当前已经成立的事实>>
- <<已完成但旧文档/旧计划可能还没同步的事实>>
- <<真正剩余的 gap>>

## Goals

- <<这份计划要达成的结果>>
- <<这份计划要达成的结果>>

## Non-Goals

- <<明确不在本计划内的方向>>
- <<明确不在本计划内的方向>>

## Scope

### In Scope

- <<说明>>
- <<说明>>

### Out Of Scope

- <<说明>>
- <<说明>>

## Execution Plan

<<顺序任务用 Phase；并行任务用 Workstream。二选一即可，不要求同时使用。>>

### Phase 1 - <<名称>>

Status: planned
Targets: `<<文件/模块/文档>>`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] <<执行项>>
- [ ] <<执行项>>

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] <<完成判定>>
- [ ] <<验证点>>
- [ ] <<若该 Phase 改变 live baseline：相关 `docs/architecture/` / `docs/components/` 已更新；否则明确写 `No owner-doc update required`>>
- [ ] `docs/logs/` 对应日期条目已更新

### Phase 2 - <<名称>>

Status: planned
Targets: `<<文件/模块/文档>>`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] <<执行项>>
- [ ] <<执行项>>

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] <<完成判定>>
- [ ] <<验证点>>
- [ ] <<若该 Phase 改变 live baseline：相关 `docs/architecture/` / `docs/components/` 已更新；否则明确写 `No owner-doc update required`>>
- [ ] `docs/logs/` 对应日期条目已更新

### Workstream 1 - <<名称>>

Status: planned
Targets: `<<文件/模块/文档>>`

- Item Types: `Fix | Decision | Proof | Follow-up`

- [ ] <<执行项>>
- [ ] <<执行项>>

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] <<完成判定>>
- [ ] <<验证点>>
- [ ] <<若该 Workstream 改变 live baseline：相关 `docs/architecture/` / `docs/components/` 已更新；否则明确写 `No owner-doc update required`>>
- [ ] `docs/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：如果计划不涉及任何代码变更（仅修改 `docs/` 下的文件），`pnpm test`、`pnpm lint`、`pnpm typecheck`、`pnpm build` 这些条目可以直接从 Closure Gates 中删除，不需要执行。

- [ ] <<所有 in-scope confirmed live defects 已修复>>
- [ ] <<所有 in-scope confirmed contract drifts 已收敛>>
- [ ] <<行为/契约结果已达成>>
- [ ] <<必要 focused verification 已完成>>
- [ ] <<不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift>>
- [ ] <<受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required>>
- [ ] <<独立子 agent / 独立审阅者 closure-audit 已完成并记录证据>>
- [ ] `pnpm typecheck`
- [ ] `pnpm build`
- [ ] `pnpm lint`
- [ ] `pnpm test`

## Deferred But Adjudicated

### <<条目名称>>

- Classification: `watch-only residual | optimization candidate | out-of-scope improvement`
- Why Not Blocking Closure: <<一条明确理由>>
- Successor Required: `yes | no`
- Successor Path: <<如需要则填写 plan path>>

## Non-Blocking Follow-ups

- <<不影响当前 contract closure 的治理项或优化项>>
- <<不影响当前 contract closure 的治理项或优化项>>

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>

## Optional Sections

- `## Problem`
- `## Root Cause`
- `## Risks And Rollback`
- `## Outdated Note`
- `## Supersession Note`
- `## Documentation Follow-Up`
```

## How To Use The Template

### When Drafting

1. 先写 `Current Baseline`，再写 phase。
2. 如果 `Out Of Scope` 写不清，说明 plan 还太宽。
3. 如果 `Goals` / `Non-Goals` 写不清，说明边界还不够硬。
4. 如果 slice 写不出 `Exit Criteria`，说明它还不够可执行。
5. 如果你正在规划的是一个完整 feature，先问自己这份 plan 是否真的能把 feature 收口；如果答案是否定的，再考虑拆成 successor plans，而不是一开始就把 feature 切碎。
6. `Exit Criteria` 尽量写成 repo-observable 结果——在仓库里能直接看到、能验证的东西，而不是模糊的抽象语义。不同性质的计划，"可观测"的含义不同：
   - **代码变更**：具体 API 签名、具体行为描述、对应的单元测试用例及其预期结果。
   - **文档改进**：具体文件路径、章节标题、内容与 live repo 中代码实际行为的一致性。例如"`docs/architecture/flux-core.md` 的 Scope Model 章节已更新为 `compileScope()` 的当前行为"。
   - **重构治理**：具体文件行数阈值、迁移完成的文件清单、`pnpm typecheck && pnpm build && pnpm test` 全过。
     判断标准：读完这条 Exit Criteria 后，任何人都能在仓库里找到对应的文件、代码或文档，明确判断它是否成立。
7. 如果计划要处理重构热点或大文件治理，先基于 live repo audit 写清当前超大文件清单、目标阈值，以及 closure 时将使用的复核命令；不要只引用旧日志或旧计划里的行数结论。
8. 为每个 execution item 标记类型：`Fix`、`Decision`、`Proof`、`Follow-up`。如果一个项已经被确认为 live defect 或 contract drift，就不能写成 `Follow-up`。
9. 如果某个 Phase 改了代码或行为，该 Phase 的 exit criteria 必须列出需要更新的 `docs/architecture/`、`docs/components/` 或 `docs/logs/` 条目；如果不需要 owner-doc 更新，也要显式写出 `No owner-doc update required`。文档更新不是全局收尾工作，而是 Phase 内的工作。`docs/architecture/` 下的文档只写最终设计状态（见 Minimum Rules 第 14 条）。

### When Executing

1. 开始某个 slice 时，把它改成 `in progress`。
2. slice 完成后，把该 slice 的 `Status` 改成 `completed`，并勾掉对应 checklist。
3. 非执行性的说明段落不用打完成状态。
4. 如果只完成了类型/接口/方法壳，而语义或测试还没对齐，不要把 slice 标成 `completed`；这类情况通常应保持 `in progress` 或改成 `partially completed` 的 plan-level 状态。
5. 如果某个项决定延期，先把它移到 `Deferred But Adjudicated` 或 `Non-Blocking Follow-ups`，并写清 `Why Not Blocking Closure`；不能只在 execution list 里放着不勾选。

### When Closing The Plan

关闭前必须做 7 件事：

1. 从头重读整份 plan，不只看最近 landing 的部分。
2. 逐条核对每个 slice 的 `Exit Criteria`。
3. 逐条核对 `Closure Gates`。
4. 逐项核对文本一致性：`Plan Status`、每个 slice 的 `Status`、每个 slice 的 `Exit Criteria`、`Closure Gates`、`docs/logs/` 收口记录必须彼此一致，不能保留“顶部已 completed、内部仍未勾选”的状态。
5. 把剩余工作写进 `Follow-up`，明确 successor plan 或明确无剩余 debt。
6. 明确区分“接口存在”与“行为完成”（对代码变更计划：至少抽查一轮 live code path 和 focused tests；对纯文档计划：抽查文档内容与 live repo 代码的一致性），确认实现语义真的满足 exit criteria。
7. 由独立审阅者或独立子 agent 做 closure-audit，并在 plan 或对应 daily log 中记录证据。这里的独立子 agent 指为 closure audit 单独启动的 fresh session，而不是复用实现阶段的同一 task session 继续自查。
8. 逐条检查 deferred / follow-up 项是否真的 non-blocking，确认没有把 in-scope live defect、contract drift、或硬门禁失败项偷偷改写成“后续再做”。

如果这些事没做完，就不要把 `Plan Status` 改成 `completed`。

### Closure Audit Rule

把 plan 改成 `completed` 前，必须把“执行”与“收口审计”当成两件事。

最低要求：

1. 关闭动作必须发生在一次明确的 closure-audit pass 中，而不是某个实现 slice 的顺手附带动作。
2. closure audit 要回看 live repo，而不是只看旧 completion note、旧 checklist、或最近一次提交说明。
3. closure audit 必须由独立审阅者或独立子 agent 执行；实现者自己的 self-audit 不能单独作为 `completed` 的依据。
4. 每个 `Phase` / `Workstream` 都必须已经是 `completed`，否则 plan 不能关闭。
5. 如果某个 slice 的工作不再属于本 plan，先把它显式移到 successor plan 或标注取消原因，再关闭本 plan。
6. `Closure Gates` 中的未完成项只能保留在 plan 仍未关闭时；若计划关闭，这些项也必须完成或被移出当前 scope。
7. closure audit 必须抽查“关键行为是否真的被实现”，不能只因为接口、类型、方法名、或注释已经存在就判定完成。
8. 如果 closure audit 发现 only-partial landing，必须把 plan 改成 `partially completed` 或 `in progress`，而不是勉强保留 `completed`。
9. closure audit 必须检查 deferred / follow-up 项的分类是否诚实；已确认的 live defect、contract drift、owner-doc drift、或硬门禁失败项不能留在 non-blocking 区域。

推荐 closure-audit 证据来源：

- live code or docs paths that satisfy each slice exit criterion
- focused verification results or a clearly cited already-green workspace baseline
- daily log entry recording the closure pass and any final doc-sync work
- independent reviewer / subagent findings with task id or cited review note that explicitly check for plan/doc drift and interface-vs-semantics mismatch
- explicit justification for each deferred item that remained non-blocking at closure

实操上可以把 closure audit 理解为一轮独立复核：

- 不是“我刚做完最后一项，所以应该没问题”
- 而是“我现在重新核对整份计划，确认没有剩余 plan-owned work”

一个常见误区：

- “接口已经有了，所以这一 phase 应该算完成”

正确做法：

- 继续核对该接口是否真的被调用、是否满足文档语义、是否有 focused tests 证明行为成立；否则最多算 partial landing。

## Practical Rule

计划不需要写得很长，但必须一眼看清 3 件事：

- 当前 baseline 是什么。
- phase 到哪一步了。
- 剩余工作归谁。

补充判断：

- 如果读者看完 plan 仍然不知道“这个 feature 什么时候算真正可用”，说明计划可能被切得过碎。
- 如果计划中的多个 slice 只有全部完成后 feature 才第一次成立，那么默认应把它们放在同一个 owner plan 下，直到 live repo 证据证明需要拆分。
