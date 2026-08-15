# Cycle 2 / I2+I3 — nop-metadata silent-wrong-result 不变式驱动审计与裁决

> Plan Status: active
> Last Reviewed: 2026-08-15
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 2 / I2（不变式驱动审计）+ I3（发现裁决与工作项拟制）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I2–I3 步骤定义、Loop Design 7 步）；Cycle 1 先例 plan `2026-08-13-1930-3`
> Related: 前置 `2026-08-15-0820-1`（Cycle 2 / I1' 门禁沉淀，**硬前置**）；后继 `2026-08-15-0820-3`（I4'–I6' 修复与收口）

## Purpose

以前置 I1' 落地的 silent-wrong-result 门禁为探测器，对 nop-metadata 全部 service/processor/bizmodel 代码跑出**正式 red list**，并对抗性探查门禁覆盖盲区；随后对每一条 red list 发现完成**零悬挂裁决**（P1 修复派发 / false positive 显式裁定并标注 / 新族登记），为 I4' 类别清扫修复提供确定输入。

## Current Baseline

> 事实为 2026-08-15 live repo 实测；I1' 产出的精确计数以其实际交付为准。

- **前置依赖（硬）**：`2026-08-15-0820-1`（I1'）已完成——新 INV-* 条目入 catalog、门禁可运行、初始 red list 快照已记录于 `initial-red-list-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）（I1' P3 定名交付，含机器可读 baseline 层）。
  - **取消条款（显式）**：若 I1' Phase 1 裁定**全部子族不可机械化且零新门禁落地**，则本计划**取消**（Plan Status 改 `cancelled` + Supersession Note 说明 silent-wrong-result 族按 watch-only 登记、无审计面），不得空转执行。
  - **部分机械化情形（预期常态）**：Phase 1 逐子族裁定通常是部分机械化 + 部分 watch-only。此时本计划正常执行，且**审计面定义**：有门禁子族 = 门禁 red list 全量；**无门禁/watch-only 子族 = 必须由对抗探查覆盖**（A2 要求每个 watch-only / 无门禁子族至少 1 个专属探查方向或显式排除理由——"门禁未覆盖"不得等于"本轮不审"）。
- **Cycle 1 先例口径**（I2：正式 red list 与初始快照零漂移核对；对抗探查 5 方向 0 新族；I3：裁决零悬挂 81/81）——本计划沿用同一严谨度。
- **已知预期审计面**（I1' 快照的预期主分量，live 2026-08-15 实测）：
  - locale 族：41 处默认 locale case-mapping（11 文件，分布见 I1' plan Current Baseline）
  - 精度族：`MemoryOrderByComparator.java:132` / `MemoryFilterEvaluator.java:356`
  - 其余子族计数以 I1' 扫描结果为准
- **对抗盲区假设**（探查方向候选，I2' 执行时定稿）：.toUpperCase 站点是否与 .toLowerCase 同覆盖；`StringBuilder`/`String.format` 默认 locale 变体；`equalsIgnoreCase` 作 registry 键；`String.CASE_INSENSITIVE_ORDER`；`Integer.parseInt` 等静默 NFE 路径（AR-02 同族——已修，查兄弟）；`Collator` 默认 locale。
- **裁决输出物（并列新文件策略，统一口径）**：Cycle 2 审计工件一律**并列新文件**（不与 Cycle 1 文件混合、不追加混写）——`formal-red-list-cycle2` / `adjudication-table-cycle2` / `adversarial-probing-notes-cycle2`，与 `initial-red-list-cycle2` 同族命名。理由：Cycle 1 三个工件头部为单轮元数据，追加混写会使 rg 复核口径混淆（Cycle 1 的 81 项与 Cycle 2 项并存同文件）。Cycle 1 历史文件保持只读。

## Goals

- 正式 red list 定稿：I1' 门禁跨全部审计目标运行，输出与初始快照的漂移核对（零漂移或逐条解释漂移）。
- 对抗性盲区探查：≥3 个门禁外方向的人工/工具探查，新发现登记（新族或已知族兄弟）。
- 裁决零悬挂：每条 red list + 对抗发现落到唯一状态（P1→I4' / false positive 附理由与标注 / 新族→登记 successor），无"待定"。
- roadmap Follow-up Backlog 同步（新增条目标注源审计路径）。

## Non-Goals

- **修复任何 red list 条目** —— I4'。
- **修改门禁检测规则以"减少命中"** —— 棘轮只增不减；规则误报经裁决后按 false-positive 标注流程处理（记录在裁决表，不放宽规则本身；若规则确有缺陷须修正，修正后须重跑全量并记录，且修正理由独立于"命中太多"）。
- **修改产品代码** —— 本计划是审计与裁决，零代码变更。

## Scope

### In Scope

- 门禁正式运行 + red list 定稿 + 漂移核对（`formal-red-list-cycle2`）。
- 对抗性盲区探查（总方向数 = max(3, watch-only/无门禁子族数)，其中每个 watch-only / 无门禁子族至少 1 个专属方向或显式排除理由）与发现登记（`adversarial-probing-notes-cycle2`）。
- 裁决表（`adjudication-table-cycle2`）：逐条 P1 / false-positive / 新族。
- roadmap backlog 同步 + 新族登记（如有）。

### Out Of Scope

- 修复执行、全量验证、循环收口 —— `2026-08-15-0820-3`。
- 门禁规则放宽或弱化。
- nop-metadata 之外的模块。

## Execution Plan

### Phase 1 — I2' 正式审计（Red List 定稿 + 对抗探查）

Status: planned
Targets: `formal-red-list-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）、`adversarial-probing-notes-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）（均新建）

- Item Types: `Proof`

- [ ] **A1 正式运行**：I1' 全部新门禁跨完整审计目标集运行，正式 red list 逐条记录（`文件:行` + 子族 + 复现命令）至 `formal-red-list-cycle2`；与 I1' 初始快照（`initial-red-list-cycle2`）做漂移核对（零漂移，或逐条解释——如期间发生结构性变更）
- [ ] **A2 对抗探查**：探查方向数 = max(3, watch-only/无门禁子族数)；分母 = 5 个 silent-wrong-result 子族中 I1' 裁定为 watch-only/不可机械化者 **+ I1' D2 重估后维持 watch-only 的候选族**（类型/方言兼容性、并发竞态）。其中**每个该类子族必须有至少 1 个专属探查方向**（人工 rg/读码核查该族已知模式在全模块的兄弟站点），或附显式排除理由；其余方向从 Current Baseline 候选清单选或自行发现。每方向记录探查方法、覆盖面、发现（0 发现也须记录"已探查、未发现"，含证据）
- [ ] **A3 新发现归并**：对抗探查新发现并入 red list（标注来源 = 对抗探查 + 方向）

Exit Criteria:

- [ ] 正式 red list 每条含可复现证据（rg/扫描器输出），与 I1' 快照（`initial-red-list-cycle2`）漂移已核对并记录
- [ ] 对抗探查方向数达标且每个 watch-only / 无门禁子族有专属方向或显式排除理由；每方向有方法 + 覆盖面 + 结果记录（含 0 发现方向的证据）
- [ ] **无静默跳过**：探查方向不得因"麻烦"而省略——每个候选方向要么探查要么显式写明排除理由
- [ ] 本 Phase 零产品代码变更（`git diff` 证实）
- [ ] No owner-doc update required（审计工件属 `ai-dev/audits/`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — I3' 裁决（零悬挂）与 Backlog 同步

Status: planned
Targets: `adjudication-table-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）、`ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`

- Item Types: `Decision`

- [ ] **B1 逐条裁决**：red list 全量逐条裁决，每条落四态之一——P1（confirmed live defect → 派 `2026-08-15-0820-3`）/ false positive（附理由：如 display-only 语义、恒 ASCII 输入上下文等）/ 已知族优化候选（重新确认或推翻 plan `2026-08-14-1133-2` closure 的 optimization-candidate 旧裁定，如 MemoryOrderByComparator/MemoryFilterEvaluator 2 站点；**推翻者转 P1；维持者作为豁免条目进终态 baseline**，见 0820-1 约束 B 终态衔接 / 0820-3 V2）/ 新族登记（由 B2 承载）
- [ ] **B1b false-positive 标注方式裁定（带下游联动约束，二选一并写明）**：(a) **baseline 驻留**——FP 条目不改源码，作为已批准 FP 驻留 baseline（见 0820-1 关键约束 B 终态衔接；I5' 时 baseline 重写为已批准豁免清单 = 方式 a FP 条目 + 优化候选维持条目）；(b) **源码注释放行**——加 `// invariant-ok: <裁决引用>` 注释，扫描器放行能力由 0820-1 Phase 2 P1 **预实现**（含 fixture），本计划与 I4' 仅使用该机制、不改检测规则。两种方式都必须保证 I5' 收口路径不被卡死——裁定记录写入裁决表头部
- [ ] **B2 新族判定**：对抗探查发现若构成新失败族（超出 5 已知子族），登记为候选不变式（Cycle 3 / I1 评估）或 watch-only，写明理由；**随新族登记为 watch-only 的具体站点**必须在裁决表中逐条落终态（归"新族登记"项）并附 Why Not Blocking 理由——confirmed live defect 不得借"watch-only"无理由降级
- [ ] **B3 backlog 同步**：roadmap Follow-up Backlog 增补本轮新条目（含源审计路径）；P1 项汇总移交 `2026-08-15-0820-3`（该 plan Phase 1 以本裁决表为唯一 P1 输入）

Exit Criteria:

- [ ] 裁决表（`adjudication-table-cycle2`）零悬挂：每条 red list 有唯一终态（`rg` 可复核计数一致：red list 条数 = P1 + false-positive + 优化候选 + 新族登记 之和；复核命令限定于 cycle2 文件，无 Cycle 1 混入）
- [ ] 每条 false-positive 有书面理由 + 标注方式裁定（a/b 之一，含下游联动处置——a 驻留 baseline / b 注释放行且能力由 0820-1 预实现），且与 I5' 收口路径兼容
- [ ] 新族（如有）已登记且含理由；随新族 watch-only 的站点逐条落终态并附 Why Not Blocking；无新族则显式写"0 新族"
- [ ] roadmap backlog 与裁决表一致（新条目均含源路径）
- [ ] **无静默降级**：confirmed live defect 不得归入 false-positive/优化候选/watch-only 而无理由；已确认缺陷只能进 P1
- [ ] 本 Phase 零产品代码变更（标注方式 (a) 不改源码；方式 (b) 的源码注释属 I4' 修复时动作，本 Phase 仅裁定）
- [ ] No owner-doc update required（裁决属审计工件）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] Phase 1~2 全部 Exit Criteria 勾选，各 Phase Status = completed
- [ ] 正式 red list 定稿且与 I1' 快照漂移已核对
- [ ] 裁决零悬挂（计数恒等式成立并记录，复核限定 cycle2 工件）
- [ ] 对抗探查方向数达标且每个 watch-only / 无门禁子族有专属方向或排除理由
- [ ] 无 confirmed live defect 被降级为 follow-up/false-positive
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证 (a) red list 条目可在 live repo 逐条复现，(b) 裁决表无"待定"状态，(c) 对抗探查记录非占位（方法与证据具体）
- [ ] 零产品代码变更（本计划性质：审计与裁决）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 前后差分：本次修改文件 0 新增 broken link
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无。新族登记属 catalog 候选节管辖，不属 plan-level deferred。）

## Non-Blocking Follow-ups

- 对抗探查候选方向清单中未被选入本轮"其余方向"的剩余条目，登记为下轮复探候选方向（watch-only，不阻塞；注意：watch-only/无门禁子族的专属方向是本轮强制项，不可延迟）。
- 全仓同类审计 —— 归各自 mission。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / 每条 Exit Criterion 与 Closure Gate 的验证结果>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
