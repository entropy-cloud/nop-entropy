# Cycle 2 / I2+I3 — nop-metadata silent-wrong-result 不变式驱动审计与裁决

> Plan Status: completed
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

Status: completed
Targets: `formal-red-list-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）、`adversarial-probing-notes-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）（均新建）

- Item Types: `Proof`

- [x] **A1 正式运行**：I1' 全部新门禁跨完整审计目标集运行，正式 red list 逐条记录（`文件:行` + 子族 + 复现命令）至 `formal-red-list-cycle2`；与 I1' 初始快照（`initial-red-list-cycle2`）做漂移核对（零漂移，或逐条解释——如期间发生结构性变更）→ `formal-red-list-cycle2.md` 交付：67 命中/61 键（locale 40 / narrowing 0 / contains 19 / delim 6 / bigdec 2），漂移核对三层证据（baseline 对账 61 键内 0 超出 exit 0；git 溯源快照→正式轮 main 代码零 commit；JSON 双跑 hits 逐字段一致）→ **零漂移**
- [x] **A2 对抗探查**：探查方向数 = max(3, watch-only/无门禁子族数)；分母 = 5 个 silent-wrong-result 子族中 I1' 裁定为 watch-only/不可机械化者 **+ I1' D2 重估后维持 watch-only 的候选族**（类型/方言兼容性、并发竞态）。其中**每个该类子族必须有至少 1 个专属探查方向**（人工 rg/读码核查该族已知模式在全模块的兄弟站点），或附显式排除理由；其余方向从 Current Baseline 候选清单选或自行发现。每方向记录探查方法、覆盖面、发现（0 发现也须记录"已探查、未发现"，含证据）→ `adversarial-probing-notes-cycle2.md` 交付：**10 方向**（核算：分母 = 0 子族 watch-only + 2 候选族维持 watch-only = 2，要求 ≥ max(3,2)=3，实做 10）；2 个 watch-only 族各 1 专属方向（方向 1 方言族 / 方向 2 竞态族，均 0 兄弟命中）；Current Baseline 候选清单 6 项**全部探查、零排除**（a=方向 3、b/c/d/f=方向 4、e=方向 6）；catalog watch 边界 3 项（方向 5/7/8/9）；跨子模块（方向 10）；结论 0 live 新命中 + 1 latent-form 观察（`MetaTableProfiler.toLong:548`，调用面 COUNT-only 不可达，登记 backlog watch-only）
- [x] **A3 新发现归并**：对抗探查新发现并入 red list（标注来源 = 对抗探查 + 方向）→ **0 条并入**（10 方向无 live 新命中；正式 red list §6 显式记录；latent 观察不构成 live 命中，入 backlog 而非 red list）

Exit Criteria:

- [x] 正式 red list 每条含可复现证据（rg/扫描器输出），与 I1' 快照（`initial-red-list-cycle2`）漂移已核对并记录 → 67 条逐条含复现命令（§0 命令清单 + 逐族权威扫描器命令）；§一致性核对含三层证据，零漂移
- [x] 对抗探查方向数达标且每个 watch-only / 无门禁子族有专属方向或显式排除理由；每方向有方法 + 覆盖面 + 结果记录（含 0 发现方向的证据）→ 10 ≥ 3 达标；2 watch-only 族专属方向在位；每方向含复现 rg 命令与实测计数
- [x] **无静默跳过**：探查方向不得因"麻烦"而省略——每个候选方向要么探查要么显式写明排除理由 → 候选清单 6/6 全探查，无排除项，无遗留复探候选
- [x] 本 Phase 零产品代码变更（`git diff` 证实）→ `git status --porcelain` 仅 ai-dev/ 文件；`git diff --stat -- nop-metadata/` = 0 行
- [x] No owner-doc update required（审计工件属 `ai-dev/audits/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — I3' 裁决（零悬挂）与 Backlog 同步

Status: completed
Targets: `adjudication-table-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）、`ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`

- Item Types: `Decision`

- [x] **B1 逐条裁决**：red list 全量逐条裁决，每条落四态之一——P1（confirmed live defect → 派 `2026-08-15-0820-3`）/ false positive（附理由：如 display-only 语义、恒 ASCII 输入上下文等）/ 已知族优化候选（重新确认或推翻 plan `2026-08-14-1133-2` closure 的 optimization-candidate 旧裁定，如 MemoryOrderByComparator/MemoryFilterEvaluator 2 站点；**推翻者转 P1；维持者作为豁免条目进终态 baseline**，见 0820-1 约束 B 终态衔接 / 0820-3 V2）/ 新族登记（由 B2 承载）→ `adjudication-table-cycle2.md` §2/§4/§5/§6 逐条 67/67：locale 40 全 P1（40/40 机器比较语义，逐条附 tr-TR 可达性标注 + 逐点读码理由；含 2 处安全语义缺陷：blocklist 常量 `allowLoadLocalInfile` 含 I 的绕过机理、sandbox 关键字 INSERT 含 I 的注入探测绕过机理）；contains 1 P1（isStringType = AR-05 兄弟）+ 18 FP（fail-closed 安全探测 ×5 命中 / 定界符精确语义 ×4 / AR-06 既裁消息线索 ×7 / 合成标记 ×2 / 方言家族 containment ×2，各附读码理由）；delim 3 P1（正则 pattern 与 SQL 派生标识符分量不受控）+ 3 FP（entityType=Java 标识符字符集 + entityId=sys ID，碰撞数学不可达）；bigdec 2 P1（旧裁定**推翻**：正确性家族定义非影响度评估 + String 静默跳过缺陷对完整携带 + AR-10 修复形态在库 + 修实例不修类别实证）
- [x] **B1b false-positive 标注方式裁定（带下游联动约束，二选一并写明）**：→ **裁定方式 (a) baseline 驻留**（§0 四条理由：零/最小源码扰动；FP 理由与检测规则强耦合与代码位置弱耦合；棘轮不弱化——键漂移即重裁、增殖即红；I5' 收口路径兼容——baseline 终态重写为 21 键已批准豁免清单 = FP 18 + FP 3 + 优化候选维持 0）。裁定记录已写入裁决表头部 §0
- [x] **B2 新族判定**：对抗探查发现若构成新失败族（超出 5 已知子族），登记为候选不变式（Cycle 3 / I1 评估）或 watch-only，写明理由；**随新族登记为 watch-only 的具体站点**必须在裁决表中逐条落终态（归"新族登记"项）并附 Why Not Blocking 理由 → **0 新族（显式）**：§7 十方向逐项结论表；不触发 Cycle 3/I1；无随新族 watch-only 站点需落终态（B2 尾款不适用）；唯一 latent 观察（toLong:548）非新族登记，按 roadmap watch 条目处置并附 Why Not Blocking（调用面 COUNT-only 不可达 + 约束条件：queryLong 接入可空/非整数聚合前须先 ErrorCode 化）
- [x] **B3 backlog 同步**：roadmap Follow-up Backlog 增补本轮新条目（含源审计路径）；P1 项汇总移交 `2026-08-15-0820-3`（该 plan Phase 1 以本裁决表为唯一 P1 输入）→ roadmap 新增 OBS-01 backlog 条目（含源审计路径 + Why Not Blocking）+ Work Item Status 表新增 "Cycle 2 / I2+I3（invariant-loop）" ✅ 行；裁决表 §10 P1 移交清单（5 批次 46 项，含修复形态与测试要求）供 0820-3 Phase 1 消费

Exit Criteria:

- [x] 裁决表（`adjudication-table-cycle2`）零悬挂：每条 red list 有唯一终态（`rg` 可复核计数一致：red list 条数 = P1 + false-positive + 优化候选 + 新族登记 之和；复核命令限定于 cycle2 文件，无 Cycle 1 混入）→ 恒等式 67 = 46 P1 + 21 FP + 0 维持 + 0 新族，§1 含族级分解表与 rg 复核命令（限定 formal-red-list-cycle2 / adjudication-table-cycle2 文件路径）
- [x] 每条 false-positive 有书面理由 + 标注方式裁定（a/b 之一，含下游联动处置——a 驻留 baseline / b 注释放行且能力由 0820-1 预实现），且与 I5' 收口路径兼容 → 21 FP 逐条书面理由（§4/§5 表格理由列）；§0 全局裁定方式 (a)；终态豁免清单 = 21 键，I5'（0820-3 V2）baseline 重写路径未被卡死
- [x] 新族（如有）已登记且含理由；随新族 watch-only 的站点逐条落终态并附 Why Not Blocking；无新族则显式写"0 新族" → §7 显式 "0 新族" + 十方向结论表；latent 观察按非新族路径处置（backlog watch + Why Not Blocking）
- [x] roadmap backlog 与裁决表一致（新条目均含源路径）→ OBS-01 含 `adversarial-probing-notes-cycle2.md`（方向 6）源路径；无其他新条目
- [x] **无静默降级**：confirmed live defect 不得归入 false-positive/优化候选/watch-only 而无理由；已确认缺陷只能进 P1 → 全部 P1 零 deferred；FP 均为语义级理由（非"暂不处理"）；优化候选旧裁定推翻为**升级**方向（维持→P1），无降级方向项；latent 观察经调用面可达性核查证明非 live defect（非降级）
- [x] 本 Phase 零产品代码变更（标注方式 (a) 不改源码；方式 (b) 的源码注释属 I4' 修复时动作，本 Phase 仅裁定）→ `git status --porcelain` 仅 ai-dev/ 文件
- [x] No owner-doc update required（裁决属审计工件）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1~2 全部 Exit Criteria 勾选，各 Phase Status = completed
- [x] 正式 red list 定稿且与 I1' 快照漂移已核对（零漂移：67 命中/61 键三层证据——baseline 对账 0 超出 / git 零 commit / 双跑一致）
- [x] 裁决零悬挂（计数恒等式 67 = 46 P1 + 21 FP + 0 优化候选维持 + 0 新族，复核命令限定 cycle2 工件）
- [x] 对抗探查方向数达标（10 ≥ max(3,2)）且每个 watch-only / 无门禁子族有专属方向或排除理由（2 专属方向，候选清单 6/6 全探查零排除）
- [x] 无 confirmed live defect 被降级为 follow-up/false-positive（FP 均语义级理由；优化候选旧裁定推翻为升级方向；latent 观察经调用面核查证明非 live）
- [x] 独立子 agent closure-audit 已完成并记录证据（session `ses_ffc9d8be3ffeJLLv9lbZSZc1C0`，verdict **approved**，0 Blocker / 0 Major / 3 Minor——3 Minor 均已在收口同批处置，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) red list 条目可在 live repo 逐条复现（独立复跑 TOTAL 67 + 逐规则 40/0/19/6/2 + 11+ 条目抽查与 live 源码逐一吻合），(b) 裁决表无"待定"状态（rg 命中 2 处均为否定句/规则陈述本身，实际悬挂状态 0），(c) 对抗探查记录非占位（每方向含 rg 命令 + 实测计数 + 结论）
- [x] 零产品代码变更（本计划性质：审计与裁决——`git diff --stat -- nop-metadata/` = 0 行；audit 期间唯一非 ai-dev 工作区改动（nop-format 样例 .rels 的 CRLF-only 副作用）已还原，最终 `git status --porcelain` 仅 ai-dev/ 文件）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 前后差分：本次修改文件 0 新增 broken link（16 errors 与 0820-1 收口基线完全一致，全部为 nop-ai/nop-code/nop-credential/nop-stream roadmap 等既有无关项；本次新建/修改文件 0 出现在错误清单）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无。新族登记属 catalog 候选节管辖，不属 plan-level deferred。）

## Non-Blocking Follow-ups

- 对抗探查候选方向清单中未被选入本轮"其余方向"的剩余条目，登记为下轮复探候选方向（watch-only，不阻塞；注意：watch-only/无门禁子族的专属方向是本轮强制项，不可延迟）。
- 全仓同类审计 —— 归各自 mission。

## Closure

Status Note: Cycle 2 / I2'+I3' 收口——正式 red list 67 命中/61 键与 I1' 快照零漂移定稿（`formal-red-list-cycle2`，三层证据）；对抗探查 10 方向 0 新族 0 live 新命中（`adversarial-probing-notes-cycle2`，候选清单 6/6 全探查）；裁决零悬挂（`adjudication-table-cycle2`：67 = 46 P1 + 21 FP + 0 优化候选维持 + 0 新族；bigdec 旧 optimization-candidate 裁定推翻转 P1；FP 标注方式 (a) baseline 驻留，终态豁免清单 21 键，I5' 收口路径兼容）。零产品代码变更；P1 移交清单（5 批次 46 项）就绪，后继 `2026-08-15-0820-3` Phase 1 以裁决表为唯一 P1 输入开工。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（fresh session，review-only 零文件修改），session `ses_ffc9d8be3ffeJLLv9lbZSZc1C0`
- Evidence:
  - Phase 1 Exit Criteria 6/6 PASS（独立复跑 TOTAL 67 + 逐规则 40/0/19/6/2 + baseline 对账 exit 0 / 61 键内 0 超出；git log 快照→正式轮 main 零 commit 独立核实；10 方向 ≥ max(3,2)、2 专属方向、候选 6/6、每方向方法+证据具体；A3 显式 0 并入；日志条目在位）
  - Phase 2 Exit Criteria 8/8 PASS（恒等式行级复核：locale 40×P1、contains 1+18（多命中行 3/2/2/2 计入）、delim 3+3、bigdec 2×P1，合计 67 ✓；21 FP 均有读码理由 + §0 方式 (a) 唯一裁定含 I5' 兼容；§7 显式 0 新族 + toLong 可达性独立复核（queryLong 5 调用点全 COUNT 族）；roadmap 行 + OBS-01 含源路径；§10 移交 2+38+1+3+2=46；7 处裁决抽查与 live 源码逐一吻合——含 isNumericType exact-match vs isStringType substring 双标准实证）
  - Closure Gates 11/11 PASS（含本 audit 自身；Anti-Hollow (a)(b)(c) PASS；"待定"字串 rg 2 处均为否定句/规则陈述，实际悬挂 0）
  - Anti-Hollow：(a) red list 11+ 条目 live 复现吻合 + 扫描器独立复跑一致；(b) 裁决表零悬挂（行级计数复核）；(c) 探查笔记每方向含 rg 命令与实测计数，非占位
  - Deferred 项分类检查：§9 零 deferred；Non-Blocking Follow-ups 无 confirmed defect；唯一观察 OBS-01 为 watch-only 且附 Why-Not-Blocking 与约束条件
  - checklist 工具复跑 exit 0（见下）
  - Minor 3 项收口同批处置：① equalsIgnoreCase 计数 25/10 → **27/12**（probing notes 方向 4 已更正，漏 MemoryOrderByComparator:142 / LocalReconciliationProcessor:111，结论不变）；② 工作区 nop-format 样例 .rels CRLF-only 测试副作用已 `git checkout` 还原（最终 git status 仅 ai-dev/）；③ "待定"字串 2 处均为否定句语义，无需修改
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-15-0820-2-nop-metadata-silent-wrong-result-audit-adjudication.md --strict` 退出码 0

Follow-up:

- 后继：`2026-08-15-0820-3`（I4'~I6'：46 P1 类别清扫修复 + 门禁终态 + Cycle 2 收口——Phase 1 以 `adjudication-table-cycle2` §10 为唯一 P1 输入）。
- OBS-01（toLong latent-form，watch-only，非本计划工作项）——归 roadmap backlog 周期复探。
- 全仓同类审计 —— 归各自 mission。
