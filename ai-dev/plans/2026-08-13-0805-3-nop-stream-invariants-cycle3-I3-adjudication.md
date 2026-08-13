# Cycle 3 / I3 — 发现裁决与工作项拟制（wiring 族 red list 裁决 + I4 派发）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：1 Major（无 I2 完成机械门——陈旧 Cycle 2 red list 可被误裁）+ 4 Minor（纯文档门删除声明缺失 / 派发清单缺收尾三连 / Outdated Note 缺失 / I4 移交未入 Follow-ups），全部修复；round 2：全部修复在案 + live 判别器验证（precheck (b)(c) 当前均失败 = 门真实生效；收尾三连同 WI-C2-1 先例），残余 2 Minor（Phase 2 出口标准未列收尾三连 / 预检缺残留 Cycle 3 节检查）已并入，verdict APPROVE 可转 active）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 3 / I3 行（red list 逐条裁决 → P0/P1 派 I4；新族派 Cycle 4 / I1（Loop Rule）；P2/P3 入 Follow-up Backlog；裁决表零悬挂）；前置 I2 产出 `ai-dev/audits/nop-stream-invariants/red-list.md`（Cycle 3 / I2 权威版）+ `cycle3-I2-probing-report.md`；mission `nop-stream-invariant-loop` 授权声明；`ai-dev/audits/nop-stream-invariants/adjudication-table.md`（既有 Cycle 1/2 历史节）
> Related: 前置 `2026-08-13-0805-2-nop-stream-invariants-cycle3-I2-invariant-driven-audit.md`（I2，硬串行）；后续 Cycle 3 / I4（修复，以本 plan 的 P0/P1 派发清单为输入，I3 裁决后另立 plan）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 3 / I3. 发现裁决与工作项拟制

## Purpose

对 Cycle 3 / I2 权威 red list 逐条裁决（P0/P1/P2/P3），P0/P1 派发为 I4 修复工作项（按族组织 + 类别清扫范围 + 测试要求）；新族（如有）登记 Cycle 4 / I1 派生输入（Loop Rule 预授权）；P2/P3 入 Follow-up Backlog；**裁决表零悬挂**。本 plan 是纯决策计划：不写代码、不跑门禁、不修复。

## Current Baseline

> **重要：Cycle 3 / I2 red list 权威版尚不存在**（`ai-dev/audits/nop-stream-invariants/red-list.md` 目前为 Cycle 2 / I2 权威版 + Cycle 1 历史版）——本 plan 的 Current Baseline 对 I2 产出只作"前置依赖声明"，不声称已核对不存在的文件；I2 completed 后、本 plan 执行前，须按 Cycle 3 / I2 权威版内容复核本 plan 的假设（red list 条目集、pin 裁定、探查发现）。

- **前置依赖（硬串行）**：Cycle 3 / I2（`2026-08-13-0805-2-...`）产出的权威 `red-list.md` 为本 plan 的输入；本 plan 执行前 I2 必须已 `completed`。
- **已知裁决输入（登记在案，I2 权威版可能增减）**：
  - **仅测试注入复探结果**（I2 Phase 3-a 产出，可能含 checkpoint/watermark 服务同族实例——若确认 → 裁决严重度并派发/入 backlog；若未确认 → 关闭条目）
  - **P2 backlog 触发评估表**（I2 Phase 3-e 产出：open-audit P2 批次 + multi-audit P2 批次逐条触发状态——已触发条目入本 plan 裁决面）
  - **`TimestampsAndWatermarksOperator` residual 复核**（I2 Phase 3-a 产出）
  - **恢复路径 / 时序组合面探查发现**（I2 Phase 3-b/c 产出，可能含新族候选）
- **裁决规则来源**：roadmap I3（red list 逐条裁决 → P0/P1 派 I4；新族派 Cycle 4 / I1；P2/P3 入 Follow-up Backlog；裁决表零悬挂）；mission 授权（P0/P1 自动修复预授权，同 audit-remediation 先例；结构性重构——公共 API、模块边界、Operator 接口变更——执行前人工确认）；I0 catalog §1 历史严重度仅作参考，以本 plan 裁决分析为准。
- **分类诚实性基准**（guide Minimum Rules #16 + Anti-Slacking Rule）：允许的是**已裁定**为 non-blocking 的 residual / 优化项（附依据）；禁止的是**未裁定**的 deferral 与**无依据/静默**降级。
- **I4 输入契约**：P0/P1 派发须按族组织并标注类别清扫范围（roadmap「类别清扫强制」），I4 计划以裁决表为输入起草。

## Goals

- 裁决表：Cycle 3 / I2 red list 每条 → 严重度（P0/P1/P2/P3）+ 族归属 + 处置（派 I4 / 入 backlog / 关闭），**零悬挂**。
- P0/P1 → I4 工作项派发清单（按族组织、含类别清扫范围与测试要求）。
- 新族（如有）→ Cycle 4 / I1 派生登记（不变式陈述 + 触发证据，PD-n 先例链编号）。
- P2/P3 → Follow-up Backlog 登记。
- roadmap Work Item Cycle 3 / I3 状态流转（`todo`→`planned`→`done`）。

## Non-Goals

- **不修复任何项**（属 I4；裁决后未派发的项绝不修复）。
- **不跑门禁 / 不探查 / 不补验证**（属 I2；裁决遇信息不足时记录"需 I2 补充"并返回，不自行验证）。
- **不写新门禁**（新族门禁属 Cycle 4 / I1）。
- **不正式追加 Cycle 4 work item 到 roadmap**（追加动作属 I6 收口；I3 只登记派生输入记录）。
- **不裁决 `HG-01` 线协议去留**（人工确认门，已登记待办；本 plan 仅确认其不在 I4 自动信封内——除非 I2 探查发现其与新族有联动，此时仅登记联动事实，仍不裁决）。
- **信封复核边界（显式声明）**：如 P0/P1 项含需人工确认的结构性重构（公共 API / 模块边界 / Operator 接口变更），本 plan 只标注「需人工确认」门并登记，不设计重构方案。

## Scope

### In Scope

- 裁决表拟制（Cycle 3 / I2 red list 逐条：严重度 / 族 / 处置）。
- I4 派发清单（按族 + 类别清扫范围 + 测试要求 + 门禁复跑要求）。
- 新族派生登记（如有）+ P2/P3 Follow-up Backlog。
- 独立共识审查（裁决表分类诚实性）。
- `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 3 节）+ `ai-dev/logs/` 更新。

### Out Of Scope

- 修复（I4）、门禁运行（I2）、Cycle 4 派生（I6）、新门禁（Cycle 4 / I1）、`HG-01` 线协议设计。
- 结构性重构方案的拟定（若某 P0/P1 项需要公共 API / 模块边界变更，只标注"需人工确认"门，具体方案由 I4 前置人工确认流程处理，不在本 plan 内设计）。

## Execution Plan

### Phase 1 - 裁决表拟制

Status: planned
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 3 节，追加到现有文件）

- Item Types: `Decision`

- [ ] **I2 硬前置预检（机械门，先于任何裁决动作）**：逐一验证 (a) `2026-08-13-0805-2-...` Plan Status == `completed`；(b) `red-list.md` 头部为「Cycle 3 / I2 权威版」（**防陈旧 Cycle 2 权威版被误当 Cycle 3 基线裁决**；匹配 = 头部标题含该短语即可）；(c) `ai-dev/audits/nop-stream-invariants/cycle3-I2-probing-report.md` 存在；(d) `adjudication-table.md` 无既有 Cycle 3 节（防中止的旧 run 残留部分裁决被叠加——如有残留 → 记录并按权威 red list 重建该节，不沿用残留）；任一不满足 → **立即返回 `blocked`**（不开始任何 Phase），记录缺项
- [ ] 与 Cycle 3 / I2 权威版对照复核本 plan 假设（red list 条目集、pin 裁定、探查发现、注册表行号），差异逐条记录在案（本 plan 执行前 I2 必须已 completed；**差异仅限 I2 已完成后的条目集增减（预期内，按 I2 权威版纳入）**；出现超出预期的差异 → 按 I2 权威版修正基线并记录）
- [ ] 逐条裁决 Cycle 3 / I2 red list，**裁决表主键 = finding-ID / 注册表条目（服务 fqcn 或稳定标识）**，每条：严重度 + 族归属 + 处置 + 依据。严重度标尺（沿 Cycle 2 / I3 先例）：**P0** = 数据丢失 / 损坏或核心恢复语义破坏；**P1** = 现实场景正确性 / 并发安全缺陷；**P2** = 健壮性 / 资源泄漏 / 边界场景；**P3** = 次要治理或优化
- [ ] 处置枚举：**P0/P1 → 派 I4**（已知族直接派发；新族 P0/P1 确认 defect 走**双轨**：I4 修复 + Cycle 4 派生登记（Phase 2 执行））；**P2/P3 → 入 Follow-up Backlog**（已裁定处置，附依据即合规）；**已 verified 条目 → 记录关闭**（转述 I2 裁定不重裁）。**新族 P2/P3 显式规则**：新族条目的 P2/P3 在入 Follow-up Backlog 的同时**必须登记 Cycle 4 派生输入**（不变式陈述 + 触发证据，供 I6 按 Loop Rule 评估是否正式派生）——或显式裁定"不派生"并附依据；不允许只入 backlog 不裁定派生归属
- [ ] **仅测试注入复探结果裁决**：checkpoint/watermark 服务同族实例（如有）——按严重度标尺逐条裁决（测试绿灯生产必崩形态 = P1 起），派发或入 backlog 附依据；`TimestampsAndWatermarksOperator` residual 复核结论裁决（维持 watch-only / 升格修复）
- [ ] 每条裁决依据逐条记录（历史严重度参考 + 影响面分析 + I2 验证 / 探查结论引用），禁止无依据裁决
- [ ] 裁决表写入 `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（新增 Cycle 3 节，保留 Cycle 1/2 历史节；每条：标识 / 位置 / 族 / 严重度 / 处置 / 依据 / I2 结论引用）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 裁决表 Cycle 3 节存在且 I2 red list 每条一行，**零悬挂**（无未裁决条目）
- [ ] 每条裁决依据可追溯（引用 I2 red list 条目 + 影响面分析）
- [ ] 假设复核记录存在（与 I2 权威版无差异，或差异已按权威版修正）
- [ ] 信息不足处置：裁决遇信息不足的条目标 `NEEDS_I2_SUPPLEMENT` + 记录提交人 / 日志并升级阻塞；阻塞解除 = I2 补充产出后重开本 plan Phase 1；有此类条目则该 Phase 不标 completed
- [ ] No owner-doc update required（裁决为过程产出，不改变行为契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 工作项派发

Status: planned
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 3 节）；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 的 `## Follow-up Backlog` 节

- Item Types: `Decision | Follow-up`

- [ ] **P0/P1 派发清单**：按族组织为 I4 工作项，**落点 = `adjudication-table.md` Cycle 3 节「P0/P1 派发清单」小节**（每条：目标类 + 缺陷描述 + 预期行为 + **类别清扫范围**（grep 全类兄弟清单）+ 测试要求（test-first 先红后绿）+ 门禁复跑要求 + **收尾三连（如适用）** = `wiring-registry.json` 注册表分类更新 + `mjs-pins.json` 过渡 pin 增删 + JUnit 断言三处一致同步——沿 WI-C2-1 先例（Cycle 2 / I3 §8），防「mjs 绿但注册表旧」脱钩）；结构性重构项（公共 API / 模块边界 / Operator 接口）单独标注"需人工确认"——**注意**：I4 修复计划不在本 plan 起草（I3 只产出派发清单，I4 计划由 mission-driver 下轮按裁决表另立 plan 起草，roadmap I4 行追加）
- [ ] 新族派生登记（如有）：**落点 = `adjudication-table.md` Cycle 3 节「Cycle 4 派生登记」小节**，每条含不变式陈述 + 触发证据（`文件:行`）+ PD-n 编号（接续既有编号：本仓已铸 PD-15；编号规则 = `max(ai-dev/lessons/ 最高编号, 已铸 PD 号最大值) + 1`，live grep 复核）；供 I6 按 Loop Rule 正式追加 Cycle 4 / I1 work item
- [ ] P2/P3 处置：入 roadmap `## Follow-up Backlog` 节（记录位置 / 严重度 / 未来触发条件），与 roadmap 裁决规则及既有条目格式一致（含 I2 触发评估表已触发条目的升级登记）
- [ ] 零悬挂复核：裁决表每条处置与派发清单一一对应（无"已裁决但无处可去"项）
- [ ] **独立共识审查**（沿 Cycle 2 / I3 先例）：独立子 agent 审查裁决表分类诚实性（无 in-scope live defect 被降级为 P2/P3/follow-up；P0/P1 判级与影响面一致；信封边界正确）+ 零悬挂复核通过后本 plan 才可标 completed

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] P0/P1 派发清单在案（每条六要素 + 类别清扫范围 + 测试要求 + 门禁复跑要求 + **收尾三连（如适用）**；结构性重构项标注「需人工确认」）
- [ ] 新族派生登记（如有）或显式「无新独立族」声明 + PD 编号规则复核记录在案
- [ ] P2/P3 全部入 roadmap Follow-up Backlog（格式与既有条目一致，含触发条件）
- [ ] 零悬挂复核表在案（每条处置 ↔ 派发/backlog/关闭一一对应）
- [ ] 独立共识审查结论在案（无 Blocker；有 Major → 修复后重审）
- [ ] No owner-doc update required（派发为过程产出）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 收口与移交

Status: planned
Targets: roadmap（Cycle 3 / I3 行流转）、`ai-dev/logs/`、`adjudication-table.md`（终版复核）

- Item Types: `Decision | Proof`

- [ ] 裁决表 + 派发清单 + backlog 登记三处一致复核（无悬挂、无降级、无静默）
- [ ] roadmap Cycle 3 / I3 行状态流转 `todo`→`planned`（激活时）→`done`（closure audit 通过后，不得提前）
- [ ] I4 移交声明：P0/P1 派发清单为 Cycle 3 / I4 唯一输入（I4 计划 = mission-driver 下轮按裁决表另立）；roadmap I4 行追加动作登记（I4 计划起草时随附）
- [ ] `ai-dev/logs/` 对应日期条目已更新（含独立共识审查结论）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 三处一致复核表在案（裁决表 / 派发清单 / backlog 登记）
- [ ] roadmap Cycle 3 / I3 行已流转 `done`（closure audit 通过后）
- [ ] I4 移交声明在案（输入契约 + 下轮另立计划）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] No owner-doc update required（纯决策产出）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] I2（`2026-08-13-0805-2-...`）已完成（硬前置）；裁决范围与 I2 权威版一致
- [ ] 裁决表零悬挂（每条有严重度 + 族 + 处置 + 依据）
- [ ] P0/P1 派发清单完整（按族 + 类别清扫范围 + 测试要求；结构性重构标注「需人工确认」）
- [ ] P2/P3 全部入 Follow-up Backlog（格式一致 + 触发条件在案）；新族（如有）派生登记在案
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 独立共识审查通过（分类诚实性 + 零悬挂；evidence 写入本 plan 或 daily log）
- [ ] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 验证（a）每条裁决可回溯 I2 权威版证据（非空壳裁决），（b）派发清单与实际 red list 条目一一对应，（c）无"已裁决但无处可去"悬挂项
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] **纯决策计划声明**：本 plan 零代码变更（仅 `ai-dev/` 文档）——按 guide「纯文档计划」条款，`./mvnw` 构建门禁与 `scan-hollow-implementations.mjs` 不适用（显式删除，不在 Closure Gates 中保留）；closure audit 时 `git status` 实测仅 ai-dev 文档文件变更（记录在案）

## Outdated Note

- 无（本 plan 为新建 Cycle 3 / I3 计划，无旧基线被取代）。

## Deferred But Adjudicated

### P2/P3 批次（open-audit + multi-audit）未触发条目

- Classification: `watch-only residual`（roadmap Follow-up Backlog 既有机制）
- Why Not Blocking Closure: 触发条件评估由 I2 完成（触发评估表在案）；未触发条目维持 backlog 原状（已裁定处置附依据即合规，roadmap 声明），不驱动独立修复计划。
- Successor Required: `no`

## Non-Blocking Follow-ups

- **Cycle 3 / I4 计划起草**（Successor ownership，显式移交）：I4 = mission-driver 下轮按本 plan P0/P1 派发清单另立 plan（输入契约 = adjudication-table.md Cycle 3 节派发清单 + roadmap I4 行随附追加）；无 P0/P1 派发时本项不触发（I4 不立 plan，直接进入 I5 或 I6 判定）。
- 新族（如有）门禁沉淀 = Cycle 4 / I1 派生候选（I6 收口按 Loop Rule 评估）。
- `HG-01` 跨 task side-output 线协议 = 人工确认待办（延续，不因本 plan 改变）。

## Closure

Status Note: （closure 时填写——裁决表零悬挂 + P0/P1 派发清单 + backlog 登记 + 独立共识审查通过，I4 移交声明在案）
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: （closure 时由独立子 agent 填写）
- Evidence: （closure 时填写：每条 Exit Criterion / Closure Gate 验证结果 + checklist 工具退出码 + Anti-Hollow 检查）

Follow-up:

- （closure 时填写：no remaining plan-owned work / 非阻塞项）
