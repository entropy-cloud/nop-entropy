# Cycle 3 / I6 — 循环收口与下一轮触发判定（Closure And Next-Cycle Trigger Determination）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：1 Major（M1，本 plan 无关）+ 3 Minor（M2-M4），全部修复；round 2：M1/M2/M4 FIXED + M3 残留（In Scope gate-inventory.json）与 N2（陈旧「I3 §8」引用）修复；round 3：6/6 修复 FIXED 验证、0 Blocker / 0 Major、verdict APPROVE-WITH-MINORS（1 Minor = Phase 1 注册表零漂移核对缺执行锚点）——本 plan 已补 checklist 项 + Exit Criteria 后合入，Minor 处置在案）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I6（统计/稳态判定；`HG-01` 线协议支持如获人工批准另立 plan（Successor）；closure 独立 fresh session）+ Loop Rule；I5 全量验证结果 `ai-dev/audits/nop-stream-invariants/cycle3-I6-input.md`（唯一落点，I5 新建）；I3 裁决登记 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` §12-§16（Cycle 3 / I3）与 §14（无新独立族显式声明）；mission `nop-stream-invariant-loop.json`（Cross-Cutting 授权边界）
> Related: 前置 `2026-08-13-1040-1-nop-stream-invariants-cycle3-I5-full-verification.md`（I5，硬串行依赖，须已 `completed`）；后继 = 依本 plan 裁定：Cycle 4 / I1（若派生）或 `HG-01` 人工确认后的线协议 plan（若人工批准）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 3 / I6. 循环收口与下一轮触发判定

## Purpose

收口 Cycle 3 并裁定下一轮触发：确认 I5 全量验证统计（门禁数 / red list / 新族数，唯一落点 `cycle3-I6-input.md`）、确认 red list 零悬挂（C3-RL-1..10 全裁决在案 + C3-PR-1..8 全处置）、按 Loop Rule 评估是否派生 Cycle 4 / I1（I3 §14 显式声明无新独立族 → 预期稳态暂停，但须以 I5 证据复核）、复核 `HG-01` 人工确认门状态（未批准 = 维持待办登记；已批准 = 另立 Successor plan）、登记复触发条件、roadmap Work Item Cycle 3 / I6 行追加 + 状态流转 `todo`→`planned`→`done`、独立 fresh session closure。本 plan 是纯文档 / 裁决计划：不写代码、不沉淀门禁、不修复缺陷。

## Current Baseline

> 已核对 live repo 与裁决档案（2026-08-13）。

- **I1–I3 前置状态**：I1（plan `2026-08-13-0805-1-...`）、I2（`2026-08-13-0805-2-...`）、I3（`2026-08-13-0805-3-...`）全部 `completed`；I5（`2026-08-13-1040-1-...`）为本 plan 硬前置。
- **I4 不立 plan（I3 显式声明，Cycle 3 执行路径事实）**：I3 §13「无 P0/P1 项（I4 不立 plan，直接进入 I5 或 I6 判定）」；roadmap I4 行不追加。**Cycle 3 执行路径 = I1 → I2 → I3 → I5 → I6**（无 I4 修复面——与 Cycle 1/2 的 I4 修复 cycle 不同，I6 统计口径须反映该事实：Cycle 3 门禁数 = 11 类（10 → 11，I1 新增 wiring 族）+ red list = C3-RL-1..10 全裁决零悬挂 + 新族数 = 0）。
- **red list 零悬挂预期（I3 已裁决）**：C3-RL-1/2/3 = 记录性关闭（门禁全绿 / 注册表 9/9 维持 / 组合面恢复面无缺口）；C3-RL-4..9 = **P2 → Follow-up Backlog 升级**（open-audit 批 8 条 finding：P2-01/02/03（checkpoint/cluster 面）→ C3-RL-4、P2-04/05/06/07（窗口面）→ C3-RL-5/6/7、P2-08（CEP 面）→ C3-RL-8；multi-audit 批 4 条 finding：P2-11/P2-01/P2-03/P2-05 → C3-RL-9；C3-RL-8 经独立共识审查修正 P1→P2，触发条件 = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探）；C3-RL-10 = 关闭 + backlog 修订（open-audit P2-09 claim 过期）；C3-PR-1 = 关闭（仅测试注入复探零新实例）；C3-PR-2 = 维持 watch-only（`TimestampsAndWatermarksOperator` 守卫 residual）；C3-PR-3/4/5/6/7/8 = 关闭 / 并入裁决 / 评估表（§16 零悬挂表 18 行 1:1）。权威升级口径 = I3 §15（open-audit 8 条 + multi-audit 4 条升级 + 1 条修订），本 plan 以该口径引用，不重新计数。
- **新族数 = 0（I3 §14 显式声明）**：「无新独立族——全部发现属已知族（#7 wiring 族兄弟实例 / open-audit P2 批次 / multi-audit P2 批次）或既有 backlog 批次」→ 无 PD-16 派生登记（编号保留，由 I6 按需铸造）。
- **`HG-01` 状态（live）**：`pending human confirmation`——roadmap Follow-up Backlog 条目在案（6 发射点证据 / 修复方向 / 触发条件 / 四重保护性覆盖）；I3 plan Non-Goals「不裁决 `HG-01` 线协议去留（人工确认门，已登记待办；仅确认其不在 I4 自动信封内）」+ Non-Blocking Follow-ups 第 3 条「`HG-01` = 人工确认待办（延续，不因本 plan 改变）」显式排除（不在自动信封内）；Cycle 2 / I6 已裁定维持。**本 plan 执行时需复核是否已获人工批准**。
- **I5 为硬前置**：本 plan 执行前 `2026-08-13-1040-1-...` 必须已 `completed`（全绿 + `cycle3-I6-input.md` 落档）。若执行时 I5 未 completed，本 plan 不得开始任何 Phase——**立即返回 blocked 状态**。
- **真正剩余的 gap**：Cycle 3 收口裁定（稳态判定 = 派生 Cycle 4 / I1 或稳态暂停）、`HG-01` 状态复核与处置登记、复触发条件登记（三选一 + Cycle 3 新增触发）、roadmap Cycle 3 / I6 行追加 + 状态流转、独立 closure。

## Goals

- 确认 Cycle 3 收口统计与 `cycle3-I6-input.md` 一致，且 red list 零悬挂（C3-RL-1..10 全裁决 + C3-PR-1..8 全处置）。
- 按 Loop Rule 裁定 Cycle 4 / I1 派生与否：**派生**（预授权自动追加 roadmap work item，附触发证据）或**维持稳态**（零新族且 red list 零悬挂 → 稳态暂停 + 登记复触发条件）；裁定必须显式记录依据（I3 §14 移交要求）。
- 复核 `HG-01` 状态：未批准 → 维持 `pending human confirmation` 待办登记（保护性覆盖在案）；已批准 → 另立 Successor plan 登记（线协议设计 + 实现 + E2E）。
- 登记复触发条件（三选一 + Cycle 3 新增触发：C3-RL-8 CEP 面触发条件 / C2-PR-2 形态出现 / `HG-01` 人工批准 / P2 批次触发评估表既有触发条件）；roadmap Work Item Cycle 3 / I6 行追加 + 状态流转 `todo`→`planned`→`done`。
- 独立 fresh session closure audit，evidence 写入本 plan Closure 段。

## Non-Goals

- **不修复任何代码**：C3-RL-4..9 已裁决 P2 入 backlog（触发条件在案，非本 plan 处置面）；C3-RL-8 修复方向候选（NFA 语义统一基准）待触发条件满足时评估，不在本 plan。
- **不沉淀新门禁 / 新不变式**（属 Cycle 4 / I1，若派生）。
- **不执行 Cycle 4 / I2 审计**（若派生，属下一轮 work item）。
- **不弱化 / 删除 / 豁免既有门禁**（棘轮规则只增不减；本 plan 无门禁改动）。
- **不重跑 I5 全量验证**（I5 已实测，本 plan 仅核对记录一致性）。
- **不裁决 I3 已关闭条目**（C3-RL-1/2/3/10 关闭、C3-PR-1/3/4/7/8 关闭、C3-PR-2 watch-only 均维持，不回改）。
- **不重写 I1–I5 已完成计划正文**（Minimum Rules #20）。

## Scope

### In Scope

- 收口统计核对（`cycle3-I6-input.md` ↔ on-disk surefire 报告；red-list.md Cycle 3 权威版；mjs-pins.json；`wiring-registry.json` / `output-contract-registry.json` 注册表零漂移事实核对——沿 Cycle 2 I6 先例列出本 cycle I1 新增注册表）。
- 稳态判定（零新族 + red list 零悬挂 → 稳态暂停；任一扩展候选裁定派生 → Cycle 4 派生）。
- Cycle 4 / I1 派生裁定（I3 §14 移交 + Loop Rule 显式评估；无新族 → 显式裁定稳态 + 登记复触发）。
- `HG-01` 状态复核与处置登记（未批准 = 维持待办；已批准 = Successor 登记）。
- roadmap 回写（Cycle 3 / I6 行追加 + 状态流转；维持时更新复触发条件；派生时追加 Cycle 4 work item）+ Follow-up Backlog 状态核对。
- 复触发条件登记；`ai-dev/logs/` 记录。
- 独立 fresh session closure audit + evidence。

### Out Of Scope

- 任何代码 / 门禁 / 不变式变更（属 Cycle 4 / I1，若派生）。
- Cycle 4 的执行（I1–I6 各 plan 另行拟制，本 plan 仅派生登记）。
- 对既有 completed 计划（I1–I5）的正文回写（Minimum Rules #20）。
- C3-RL-4..9 / C2-RL-3 / C2-PR-3 等 backlog 条目的重新裁决（I3 已裁定 P2/P3 backlog，维持；触发条件评估归类别清扫/复探时）。

## Execution Plan

### Phase 1 - 收口统计确认与稳态判定输入

Status: planned
Targets: `ai-dev/audits/nop-stream-invariants/cycle3-I6-input.md`、`red-list.md`（Cycle 3 权威版）、`mjs-pins.json`、`wiring-registry.json`、`output-contract-registry.json`、nop-stream 模块组 surefire 报告（`nop-stream/*/target/surefire-reports/TEST-*.xml`，限定 nop-stream 模块组，不含 `-am` 连带的上游模块）、`ai-dev/logs/2026/08-13.md`

- Item Types: `Proof`

- [ ] **I5 硬前置预检（机械门，先于任何统计动作）**：逐一验证 (a) `2026-08-13-1040-1-...` Plan Status == `completed`；(b) `cycle3-I6-input.md` 存在（I5 唯一落点）；(c) `red-list.md` 头部为「Cycle 3 / I2 权威版」（I3 裁决输入一致性）；(d) `adjudication-table.md` 无既有 Cycle 3 / I6 裁定节（防中止的旧 run 残留被叠加——如有残留 → 记录并按权威证据重建该节，不沿用残留）；任一不满足 → **立即返回 `blocked`**（不开始任何 Phase），记录缺项
- [ ] 逐条核对 `cycle3-I6-input.md` 统计与 on-disk surefire 报告一致（解析 nop-stream 模块组 TEST-*.xml 汇总 tests/failures/errors；**确认报告 mtime 与 I5 执行窗口一致**——若 mtime 晚于 I5 记录时间，说明报告已被后续构建覆盖，须以 I5 `ai-dev/logs/` 记录为准并在本 plan 记录差异；门禁 11 类 / 112 tests 与 `-Dtest='Test*Invariant*'` 报告一致；pin 0 与 `mjs-pins.json` pinnedViolations 空一致）。**出入处置**：以 surefire 实测为准，差异记录到 `ai-dev/logs/` + 本 plan 裁定记录与 closure evidence；**不回写 `cycle3-I6-input.md`**（I5 落点唯一性原则——统计落点归 I5，数据差异只记录不回写；差异不影响本 plan 裁定）
- [ ] 确认 red list 零悬挂：red-list.md Cycle 3 权威版 C3-RL-1..10 每条 I3 裁决在案（§12.2 裁决表 10 行 + §16 零悬挂表 18 行 1:1 对应）；C3-PR-1..8 处置一一对应（关闭 / watch-only / 并入裁决 / 评估表）；backlog 升级登记（roadmap 2026-08-13）与 I3 §15 一致
- [ ] **注册表零漂移事实核对（Proof）**：`wiring-registry.json`（7 服务注入 API × 9 生产接线点 × 消费方 3 类）与 `output-contract-registry.json`（6 发射点）live 抽查与 C3-RL-2 记录一致（Cycle 3 无代码变更，预期零漂移；抽查方式 = 注册表条目 live 行号核对或引用 I2 复核表结论 + I5 全量验证无接线相关失败）——结论一行记录写入 `ai-dev/logs/`
- [ ] 确认新族数 = 0：I3 §14 显式声明（「无新独立族 → 无 PD-16 派生登记」，编号规则复核 = max(lessons 最高 14, 已铸 PD 最大值 15) + 1 = 16，live grep 无已铸 PD-16）；I5 零新失败 → 无新增族；I2 探查 C3-PR-3/4/8 无新族候选（组合面 / 恢复路径 / 非族候选全部不升格）
- [ ] **Cycle 3 执行路径事实确认**：roadmap Cycle 3 / I1–I3 行 done + I4 行不追加（无 P0/P1）+ I5 行 done（I5 已追加）→ 统计口径按「Cycle 3 门禁 = 11 类 / 112 tests、red list = C3-RL-1..10、新族 = 0、I4 无修复面」记录
- [ ] **`HG-01` 状态复核**：检查 roadmap Follow-up Backlog 条目状态 + 是否有人工批准记录（daily log / backlog 条目变更）；未批准 → 维持 `pending human confirmation`；已批准 → Phase 2 登记 Successor
- [ ] **稳态判定（初步）**：零新族 + red list 零悬挂 → 稳态暂停候选；Cycle 4 派生与否按 Phase 2 裁定——本 Phase 记录「无新失败类 → Loop Rule『新族强制沉淀』不触发；Cycle 4 派生属 I6 显式评估范围」的判定边界（**论证**：roadmap §Loop Rule「零新族且 red list 零 → 稳态暂停」的强制分支仅针对「新失败类」的派生义务（「新族强制沉淀」句），I3 §14 已显式裁定无新独立族 → 不触发强制派生分支；派生与否 = I6 显式评估）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 核对记录在案：`cycle3-I6-input.md` 全部统计项与 on-disk surefire 一致（无出入或出入已修正记录）
- [ ] red list 零悬挂确认记录（C3-RL-1..10 全裁决 + C3-PR-1..8 全处置；backlog 升级登记一致）
- [ ] 注册表零漂移事实核对记录在案（wiring 7 API × 9 接线点 / output-contract 6 发射点，与 C3-RL-2 一致）
- [ ] 新族数 = 0 确认记录（I3 §14 显式声明 + I5 零新失败；Loop Rule 强制派生不触发）
- [ ] Cycle 3 执行路径事实确认记录（I1→I2→I3→I5→I6，I4 不立 plan）
- [ ] `HG-01` 状态复核结论记录（未批准 / 已批准）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] 本 Phase 为纯核对/文档工作，无代码变更 → `No owner-doc update required`（`docs-for-ai/` 不涉及；`ai-dev/` 记录归本 plan 各 Phase）

### Phase 2 - 稳态 / 派生裁定与处置登记

Status: planned
Targets: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`（Work Item 表 + Follow-up Backlog + §Loop Rule）、`ai-dev/audits/nop-stream-invariants/adjudication-table.md`（I6 裁定记录，§17 节追加——顺延既有 §12-§16「Cycle 3 / I3」编号）

- Item Types: `Decision | Follow-up`

- [ ] **Cycle 4 / I1 派生裁定**：按 Loop Rule 评估——输入 = I3 §14 显式「无新独立族」（零新族）+ Phase 1 red list 零悬挂 + I5 全绿。裁定二选一：**(a) 派生 Cycle 4 / I1**（追加 work item，附触发证据——需存在新族候选，本 plan 预期无）或 **(b) 维持稳态**（登记复触发条件）；裁定必须显式记录依据（成本 / 收益 / 触发面 / 棘轮影响）。**预期裁定：(b) 维持稳态**——依据（执行时 live 复核）：I3 §14 显式无新独立族（C3-PR-3/4/8 全部不升格 + 理由在案）；Cycle 3 无新失败类（I5 预期全绿）；C3-PR-2 watch-only residual（接线后自然失效，语义不破坏）不构成派生输入；I1 已沉淀不变式 #7 一等门禁（11 类 / 112 tests + scan-wiring V1–V5）→ 门禁面完整；派生 Cycle 4 = 六份 plan + 六轮执行的完整循环机器，为**零新族**的预防性派生——机器重量与收益不匹配；复触发登记（三选一 + Cycle 3 新增触发）可覆盖未来风险
- [ ] **C3-RL-8 等 backlog 触发条件登记（Follow-up 级）**：C3-RL-8（CEP 面 P2）触发条件 = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探时评估——登记为 Cycle 3 新增复触发条件；C3-RL-4..9 其余 backlog 条目维持既有触发条件（类别清扫 / 复探 / 配置需求出现），不逐一重裁
- [ ] **`HG-01` 处置登记**：未批准 → 维持 backlog 待办条目（四重保护性覆盖复核在案：in-task fail-fast `88bc0270c` + 门禁（类级枚举 / call-site 注册表 / 三态分类）+ pin 移除留痕（`mjs-pins.json` PIN REMOVAL TRACE）+ backlog 条目）；已批准 → 登记 Successor plan 需求（跨 task 侧输出线协议设计 + 实现 + E2E），并在 roadmap Follow-up Backlog 标注「已批准，Successor 计划中」
- [ ] **roadmap 回写**：依总裁定追加 Cycle 4 / I1 work item（若派生）或更新复触发条件登记（若稳态）；Follow-up Backlog 条目状态核对（C3-RL-4..9 维持 P2 backlog；C2-RL-3/C2-PR-3 维持 P3；`HG-01` 状态更新）
- [ ] **I6 裁定记录落档**：adjudication-table.md 追加 §17 裁定节（Cycle 3 / I6 收口裁定记录：收口统计确认、稳态/派生裁定、Cycle 3 执行路径事实、`HG-01` 处置、复触发登记摘要）——供后续 cycle 引用

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Cycle 4 / I1 派生裁定记录在案（派生 or 维持稳态 + 显式依据；预期维持稳态——若意外出现派生输入，须 live 证据支撑）
- [ ] C3-RL-8 等 Cycle 3 新增触发条件登记在案
- [ ] `HG-01` 处置登记在案（未批准维持 / 已批准 Successor）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] 无静默跳过：扩展候选未以「后续再说」/「watch-only」形式降级——已确认事项全部有显式裁定记录（派生 or 维持 + 依据 + 复触发条件）
- [ ] I6 裁定记录落档（adjudication-table.md §17）

### Phase 3 - 复触发登记与 roadmap 状态流转

Status: planned
Targets: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`、`ai-dev/logs/2026/08-13.md`

- Item Types: `Decision | Follow-up`

- [ ] **复触发条件登记**（roadmap §Loop Rule / §I6 Phase Details）：继续登记三选一复触发（① CI 任一不变式门禁变红；② nop-stream 核心类结构变更（新增/重命名 Operator/SinkFunction/Checkpoint 机制/Output 实现类）；③ 周期复探（默认每 major release 或季度，取早））；叠加本 plan 新增触发（若稳态维持：C3-RL-8 CEP 面触发条件 = I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探；C2-PR-2 形态出现 = main 出现匿名/record Output 实现或 raw OutputTag 声明；`HG-01` 人工批准 = 跨 task 线协议变更）；人工确认待办的触发 = 人工批准跨 task 线协议变更
- [ ] roadmap Work Item Cycle 3 / I6 行追加 + 状态流转 `todo`→`planned`（本 plan 激活时）→`done`（closure audit 通过后，不得提前）；I5 行执行结果段已由 I5 填充（核对）
- [ ] `ai-dev/logs/2026/08-13.md` 顶部追加 I6 条目（统计确认 + 裁定摘要 + Cycle 4 派生或稳态暂停 + 复触发登记）
- [ ] 文本一致性核对：Plan Status、Phase Status、Exit Criteria、Closure Gates、roadmap 行、daily log 六处一致后方可进入 closure

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 复触发条件登记在案（三选一 + 本 plan 新增触发：C3-RL-8 / C2-PR-2 / `HG-01`）
- [ ] roadmap Cycle 3 / I6 行已追加 + 流转（`planned`；`done` 在 closure 后由独立审计确认）
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] 文本一致性核对完成（无「顶部已 completed、内部未勾选」矛盾态）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：本 plan 不涉及任何代码变更（仅修改 `ai-dev/` 下文档），`./mvnw test`、`./mvnw lint` 等构建验证条目已按 guide 删除；保留文档与 checklist 门禁。

- [ ] 收口统计确认（与 `cycle3-I6-input.md` 一致）且 red list 零悬挂（C3-RL-1..10 全裁决 + C3-PR-1..8 全处置）
- [ ] Cycle 4 / I1 派生裁定完成（显式依据在案）→ Cycle 4 派生（roadmap 追加行 + 触发证据）或稳态暂停（复触发登记）已落档
- [ ] `HG-01` 处置登记在案（未批准维持待办 + 保护性覆盖复核 / 已批准 Successor 登记）
- [ ] 复触发条件登记在案（三选一 + C3-RL-8 / C2-PR-2 / `HG-01` 触发）
- [ ] 无 in-scope live defect / contract drift 被静默降级到 deferred / follow-up（C3-RL-4..9 = P2 backlog 已裁决附依据；C3-PR-2 = watch-only residual 有显式裁定；`HG-01` = 人工确认门）
- [ ] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [ ] **Anti-Hollow Check**：本 plan 纯文档，无代码接线面；closure audit 验证（a）roadmap 回写与 I5 实测记录 / 裁决档案一致，（b）裁定与 live 代码（注册表 / red-list / backlog）一致，（c）无「记录在案但实际未发生」的空壳断言
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### C3-RL-8 — CepOperator STEP-5 超时基准错误 + 绕过 TimedOutPartialMatchHandler（P2，若裁定维持稳态）

- Classification: `watch-only residual`（I3 §12.2 裁决 = P2 backlog；独立共识审查修正 P1→P2，触发面分析：size==1 ⟹ start state（NFA.java:736-747 无条件重建）、start state 按 NFA 语义无超时通知义务 → 无用户可见丢失场景；真实影响面 = 恢复快照 skip-strategy 裁剪后单非 start 匹配 + per-state window 边角）
- Why Not Blocking Closure: ① 已裁决 P2 backlog（附依据即合规，mission 规则「P2/P3 不驱动独立修复计划」）；② 触发条件登记在案（I4/I5 类别清扫（CEP 面）或 per-state windowTimes 使用面扩展或复探）；③ 非数据丢失（对齐 I2「潜伏地雷」定性）。
- Successor Required: `conditional`（触发条件满足时评估修复，修复方向候选 = 以 `NFA.isStateTimedOut` 语义统一 STEP-5 基准 + 清理前评估走超时通知路径）
- Successor Path: 触发条件满足时的修复 plan（backlog 评估）

### C3-PR-2 — `TimestampsAndWatermarksOperator` 静默守卫形态（watch-only residual 维持）

- Classification: `watch-only residual`（I3 §12.3 维持：:82-84 守卫接线后 PTS 恒非 null、守卫分支生产不可达；plan `2026-08-13-0132-1` 裁定「接线后自然失效，语义不破坏」成立）
- Why Not Blocking Closure: ① 生产路径守卫不可达（接线后恒非 null）；② 语义不破坏（自然失效）；③ 触发 = 接线面结构变更时复探（已登记）。
- Successor Required: `no`

### `HG-01` — 跨 task side-output 线协议结构性变更（人工确认待办，若未批准）

- Classification: `Fix`（已确认契约缺口，执行门 = 人工确认）——**非** watch-only residual；结构性重构，mission 授权边界
- Why Not Blocking Closure: ① 执行门 = 人工确认（mission Cross-Cutting「结构性重构执行前人工确认」），AI 自动执行信封不含此项；② 裁决仍是「必须修」——处置 = 维持登记 + 四重保护性覆盖（in-task fail-fast `88bc0270c` 前置 + 门禁（类级枚举 / call-site 注册表 / 三态分类）+ pin 移除留痕（`mjs-pins.json` PIN REMOVAL TRACE）+ backlog 待办条目）；③ 复触发登记在案（人工批准 + 跨 task side-output 需求出现 / CI 门禁红暴露新实例）。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan（跨 task 侧输出线协议设计 + 实现 + E2E）

## Non-Blocking Follow-ups

- Cycle 4 / I1–I6 各 phase 的执行 plan 另行拟制（若本 plan 裁定派生——本 plan 只派生登记，不代执行；预期稳态暂停，不派生）。
- C3-RL-4..9（P2 backlog 升级登记，roadmap 2026-08-13）：触发条件在案（类别清扫 / 复探 / 使用面扩展），不驱动独立修复计划。
- C2-RL-3 / C2-PR-4（P3 backlog，措辞修订或 E2E 覆盖扩展）：触发条件 = I4 类别清扫或复探时评估；`cycle2-I6-input.md` 覆盖事实表已落档供后续引用。
- C2-PR-3（重复注册 last-wins 语义，P3 优化）：触发条件 = 多消费者接线需求出现或类别清扫时评估。
- ArchUnit 架构约束门禁（Cycle 1 / I6 延续：`optimization candidate`，引入涉及依赖变更，触发时另立 Decision）。

## Closure

Status Note: 待 closure（本 plan 为 draft，未执行）。
Completed: 未完成

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写独立子 agent 标识）
- Evidence: （closure 时填写：每条 Exit Criterion / Closure Gate 验证结果 + `check-plan-checklist.mjs --strict` 退出码 + Anti-Hollow 检查结果）

Follow-up:

- 依裁定：Cycle 4 / I1 派生（若出现新族候选，预期无）或稳态暂停（预期路径，复触发条件已登记 roadmap §Loop Rule）
- `HG-01`（人工批准后）→ 线协议 Successor plan（跨 task 侧输出线协议设计 + 实现 + E2E）
- no other remaining plan-owned work
