# Cycle 2 / I3 — 发现裁决与工作项拟制（含跨 task interim fail-fast 预授权确认）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：2 Major（新族 P2/P3 处置规则缺失 / 信封确认失败路径未定义）+ 6 Minor，全部修复；round 2：1 Major（信封失败路径未贯通 Phase 2/Closure Gates）+ 2 Minor，修复；round 3：0 Blocker / 0 Major / 0 Minor，verdict 可转 active）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 2 / I3 行（red list 逐条裁决 → P0/P1 派 I4（含跨 task interim fail-fast 预授权确认，I6 预裁决输入，见 adjudication-table.md §6.2）；新族派 Cycle 3 / I1（Loop Rule）；P2/P3 入 Follow-up Backlog；裁决表零悬挂）；前置 I2 产出 `ai-dev/audits/nop-stream-invariants/red-list.md`（Cycle 2 / I2 权威版）；mission `nop-stream-invariant-loop` 授权声明；`ai-dev/audits/nop-stream-invariants/adjudication-table.md` §6（I6 双层裁决：interim fail-fast 预授权 + `HG-01` 人工确认门）
> Related: 前置 `2026-08-12-1217-09-nop-stream-invariants-cycle2-I2-invariant-driven-audit.md`（I2，硬串行）；后续 `2026-08-12-1217-11-nop-stream-invariants-cycle2-I4-fix-execution.md`（I4 修复，以本 plan 的 P0/P1 派发清单为输入）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 2 / I3. 发现裁决与工作项拟制

## Purpose

对 Cycle 2 / I2 权威 red list 逐条裁决（P0/P1/P2/P3），P0/P1 派发为 I4 修复工作项（按族组织 + 类别清扫范围 + 测试要求）；**确认 I6 §6.2 预裁决的跨 task interim fail-fast 派发**（RWO/BRWO `collect(OutputTag)` 空体 → fail-fast，自动修复信封内）并纳入 I4 派发清单；新族（如有）登记 Cycle 3 / I1 派生输入（Loop Rule 预授权）；P2/P3 入 Follow-up Backlog；**裁决表零悬挂**。本 plan 是纯决策计划：不写代码、不跑门禁、不修复。

## Current Baseline

> **重要：Cycle 2 / I2 red list 权威版尚不存在**（`ai-dev/audits/nop-stream-invariants/red-list.md` 目前为 Cycle 1 / I2 权威版 + I4 修复状态）——本 plan 的 Current Baseline 对 I2 产出只作"前置依赖声明"，不声称已核对不存在的文件；I2 completed 后、本 plan 执行前，须按 Cycle 2 / I2 权威版内容复核本 plan 的假设（red list 条目集、pin 裁定、探查发现）。

- **前置依赖（硬串行）**：Cycle 2 / I2（`2026-08-12-1217-09-...`）产出的权威 `red-list.md` 为本 plan 的输入；本 plan 执行前 I2 必须已 `completed`。
- **已知裁决输入（I6 预裁决 + I1 登记，I2 权威版可能增减）**：
  - **跨 task interim fail-fast（预授权派发，I6 §6.2 层次 1）**：`RecordWriterOutput.collect(OutputTag)`（`StreamTaskInvokable.java:645` 空体 no-op）/ `BroadcastingRecordWriterOutput.collect(OutputTag)`（:705 空体 no-op；注：I6 §6.2 原文记 :706，以 live / pin / 注册表 :705 为准）→ fail-fast（抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常，先例 `ChainingOutput.java:119`）——已确认契约缺口 P1；类内部行为修复（private 嵌套类，`Output` 接口零变更），同 RL-7 修复先例（自动信封）。**预裁决分派 Cycle 2**：经本 plan（I3）确认 → Cycle 2 / I4 执行；I1 门禁以过渡 pin 全绿落地（2 条：`RWO-cross-task-noop` / `BRWO-cross-task-noop`），I4 修复后移除 pin + 注册表分类更新。
  - **过渡 pin 2 条**：`mjs-pins.json`（key = 扫描器 V3 违规串精确匹配；removalTrigger = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新后移除，禁静默移除）。
  - **注册表分类**：`output-contract-registry.json` — RWO/BRWO 当前 `pinned-known-violation`，interim fail-fast 落地后迁移为 `fail-fast`（分类迁移须同步注册表 + JUnit 断言 + pin 移除，三处一致）。
  - **`HG-01` 线协议（人工确认门，不在自动信封内）**：跨 task side-output 真正支持 = RecordWriter 线协议结构性重构（公共内部机制变更）→ 执行门 = 人工确认（I6 登记待办；本 plan 不涉及，不裁决其去留，仅确认其不在 I4 派发范围）。
  - 其他 I1 门禁全绿基线（10 门禁类 / 102 tests / 0 failures，`cycle2-I1-input.md`）；I2 可能新增 red list 条目（门禁红、pin 裁定、探查发现）。
- **裁决规则来源**：roadmap I3（red list 逐条裁决 → P0/P1 派 I4；新族派 Cycle 3 / I1；P2/P3 入 Follow-up Backlog；裁决表零悬挂）；mission 授权（P0/P1 自动修复预授权，同 audit-remediation 先例；结构性重构——公共 API、模块边界、Operator 接口变更——执行前人工确认）；I0 catalog §1 历史严重度仅作参考，以本 plan 裁决分析为准。
- **分类诚实性基准（guide Minimum Rules #16 + Anti-Slacking Rule）**：允许的是**已裁定**为 non-blocking 的 residual / 优化项（附依据）；禁止的是**未裁定**的 deferral 与**无依据/静默**降级。
- **I4 输入契约**：P0/P1 派发须按族组织并标注类别清扫范围（roadmap「类别清扫强制」），I4 计划以裁决表为输入起草。

## Goals

- 裁决表：Cycle 2 / I2 red list 每条 → 严重度（P0/P1/P2/P3）+ 族归属 + 处置（派 I4 / 入 backlog / 关闭），**零悬挂**。
- **interim fail-fast 预授权确认**：按 I6 §6.2 预裁决确认派发（含自动信封边界复核：private 嵌套类、`Output` 接口零变更、Rule #24 合规），纳入 I4 派发清单。
- P0/P1 → I4 工作项派发清单（按族组织、含类别清扫范围与测试要求）。
- 新族（如有）→ Cycle 3 / I1 派生登记（不变式陈述 + 触发证据，PD-n 先例链编号）。
- P2/P3 → Follow-up Backlog 登记。
- roadmap Work Item Cycle 2 / I3 状态流转（`todo`→`planned`→`done`）。

## Non-Goals

- **不修复任何项**（属 I4；裁决后未派发的项绝不修复）。
- **不跑门禁 / 不探查 / 不补验证**（属 I2；裁决遇信息不足时记录"需 I2 补充"并返回，不自行验证）。
- **不写新门禁**（新族门禁属 Cycle 3 / I1）。
- **不正式追加 Cycle 3 work item 到 roadmap**（追加动作属 I6 收口；I3 只登记派生输入记录）。
- **不裁决 `HG-01` 线协议去留**（人工确认门，I6 已登记；本 plan 仅确认其不在 I4 自动信封内）。
- **信封复核边界（显式声明）**：interim fail-fast 信封复核 = 阅读 I2 权威版证据 + live 代码声明（private 嵌套类 / 接口零变更）+ 边界核对，属轻量静态阅读；**不运行门禁 / 不探查 / 不写验证代码**（与「不补验证」Non-Goal 一致）。

## Scope

### In Scope

- 裁决表拟制（Cycle 2 / I2 red list 逐条：严重度 / 族 / 处置）。
- interim fail-fast 预授权确认（I6 §6.2 层次 1）+ I4 派发清单（按族 + 类别清扫范围 + 测试要求 + 门禁复跑要求）。
- 新族派生登记（如有）+ P2/P3 Follow-up Backlog。
- 独立共识审查（裁决表分类诚实性 + interim fail-fast 信封复核）。
- `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 2 节）+ `ai-dev/logs/` 更新。

### Out Of Scope

- 修复（I4）、门禁运行（I2）、Cycle 3 派生（I6）、新门禁（Cycle 3 / I1）、`HG-01` 线协议设计。
- 结构性重构方案的拟定（若某 P0/P1 项需要公共 API / 模块边界变更，只标注"需人工确认"门，具体方案由 I4 前置人工确认流程处理，不在本 plan 内设计）。

## Execution Plan

### Phase 1 - 裁决表拟制

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 2 节，追加到现有文件）

- Item Types: `Decision`
- [x] 与 Cycle 2 / I2 权威版对照复核本 plan 假设（red list 条目集、pin 裁定、探查发现、注册表行号），差异逐条记录在案（本 plan 执行前 I2 必须已 completed，差异不应出现；出现则按 I2 权威版修正基线）——**复核表在案（adjudication-table.md §7.1）：5 项对照无实质差异（#1 预期内新增 C2-RL-3 按权威版纳入；#4 BRWO 行号按 live/pin/注册表 :705 口径）；无信息不足条目**
- [x] 逐条裁决 Cycle 2 / I2 red list，**裁决表主键 = finding-ID / pin-ID / 注册表条目（fqcn，稳定标识）**，每条：严重度 + 族归属 + 处置 + 依据。严重度标尺：**P0** = 数据丢失 / 损坏或核心恢复语义破坏；**P1** = 现实场景正确性 / 并发安全缺陷；**P2** = 健壮性 / 资源泄漏 / 边界场景；**P3** = 次要治理或优化。参考历史 finding 严重度（catalog §1）+ 影响面（数据正确性 / 泄漏 / 并发安全 / 恢复语义）+ 修复成本——**裁决表在案（§7.2）：C2-RL-1/2 = P1 派 I4；C2-RL-3 = P3 backlog；C2-PR-1/2/5 = 关闭（非 defect）；C2-PR-3/4 = P3 backlog**
- [x] **interim fail-fast 预授权确认（I6 §6.2 层次 1）**：确认派发 = 自动修复信封内——复核边界：目标为 private 嵌套类方法体行为（RWO/BRWO `collect(OutputTag)`），`Output` 公共接口零变更、RecordWriter 线协议零变更、模块边界零变更；Rule #24 合规（空体 → 显式 fail-fast 而非静默）；同 RL-7 先例（`b20fcd0e1`）。确认结论写入裁决表（含"信封内"证据链）。**失败路径（必须闭合）**：若信封复核不通过（如 I2 权威版显示 `Output` 接口已被改动、修复需动模块边界、或 RWO/BRWO 非 private 嵌套类）→ 该条目标「需人工确认」门（同 `HG-01` 登记机制），不派自动信封，Phase 2 派发清单标注结构性重构，升级等待人工确认——确认动作必须是真实检查，不能只能成功——**真实检查在案（§7.3）：private 嵌套类 live 确认（:611/:660）+ Output 接口 :41/:59 零变更 + RecordWriter 线协议零变更 + 模块边界零变更（单文件内）+ Rule #24 先例 ChainingOutput.java:119（RL-7 b20fcd0e1）+ I2 权威版证据核对 → 信封通过，无「需人工确认」门；失败分支未触发（显式记录）**
- [x] 处置枚举：**P0/P1 → 派 I4**（已知族直接派发；新族 P0/P1 确认 defect 走**双轨**：I4 修复 + Cycle 3 派生登记（Phase 2 执行），仍逐条定严重度）；**P2/P3 → 入 Follow-up Backlog**（已裁定处置，附依据即合规）；**已 verified 条目 → 记录关闭**（转述 I2 裁定不重裁）。**新族 P2/P3 显式规则**：新族条目的 P2/P3 在入 Follow-up Backlog 的同时**必须登记 Cycle 3 派生输入**（不变式陈述 + 触发证据，供 I6 按 Loop Rule 评估是否正式派生）——或显式裁定"不派生"并附依据（如影响面不足以构成新失败类）；不允许只入 backlog 不裁定派生归属（Loop Rule「任一新失败类 → 派生」与严重度无关，派生与否必须显式裁定）——**无新族（I2 §3/§4 复核一致）；C2-PR-2/5 已知族扩展候选显式裁定「不派生 PD-16」+ 依据 + 移交 I6（§7.2/§9）；C2-RL-3/C2-PR-3/C2-PR-4 = P3 入 backlog 附依据（§7.2/§10）**
- [x] 每条裁决依据逐条记录（历史严重度参考 + 影响面分析 + I2 验证 / 探查结论引用），禁止无依据裁决——**§7.2 每条含依据（I2 结论引用 + 影响面分析 + 严重度标尺定位）**
- [x] 裁决表写入 `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（新增 Cycle 2 节，保留 Cycle 1 历史节；每条：标识 / 位置 / 族 / 严重度 / 处置 / 依据 / I2 结论引用）——**§7-§10 追加完成，Cycle 1 §0-§6 历史节保留**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁决表 Cycle 2 节存在且 I2 red list 每条一行，**零悬挂**（无未裁决条目）——**§7.2 裁决表 8 行（C2-RL-1/2/3 + C2-PR-1..5）+ §7.4 零悬挂复核表在案**
- [x] 每条裁决依据可追溯（引用 I2 red list 条目 + 影响面分析）——**§7.2 每条含 I2 结论引用 + 影响面 + 严重度定位**
- [x] interim fail-fast 预授权确认结论在案（信封边界复核证据：private 嵌套类 / 接口零变更 / Rule #24 / RL-7 先例；**通过或失败分支均有显式结论**——失败分支 = 「需人工确认」门处置记录）——**§7.3 结论 = 信封通过（通过分支显式）；失败分支未触发显式记录**
- [x] 假设复核记录存在（与 I2 权威版无差异，或差异已按权威版修正）——**§7.1 复核表在案（无实质差异，#1 按权威版纳入）**
- [x] 信息不足处置：裁决遇信息不足的条目标 `NEEDS_I2_SUPPLEMENT` + 记录提交人 / 日志并升级阻塞（预期罕见——I2 Phase 4 契约保证"每条含裁决输入，I3 可直接逐条裁决"）；**阻塞解除 = I2 补充产出后重开本 plan Phase 1**；有此类条目则该 Phase 不标 completed——**无 NEEDS_I2_SUPPLEMENT 条目（I2 权威版每条含裁决输入，I2 Phase 4 契约满足），无阻塞**
- [x] No owner-doc update required（裁决为过程产出，不改变行为契约）——**已裁定（本 plan 零代码变更，无行为契约变更）**
- [x] `ai-dev/logs/` 对应日期条目已更新——**`ai-dev/logs/2026/08-12.md` Phase 1 条目在案**

### Phase 2 - 工作项派发

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 2 节）；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 的 `## Follow-up Backlog` 节

- Item Types: `Decision | Follow-up`
- [x] **interim fail-fast 工作项（预授权派发确认，I6 §6.2 层次 1 → I4）**：目标类 = `StreamTaskInvokable$RecordWriterOutput` / `$BroadcastingRecordWriterOutput`（`collect(OutputTag, X)` :645/:705 空体；注：adjudication-table.md §6.2 原文记 BRWO `:706`，以 live / pin / 注册表 :705 为准）；预期行为 = 无注册消费者时抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常（先例 `ChainingOutput.java:119`，含 `ARG_OUTPUT_TAG` / `ARG_DETAIL` 参数），禁止静默丢弃（Rule #24）；类别清扫范围 = 全部 main `Output` 实现类（4 个）+ 全部 `collect(OutputTag` call-site + 接线链（`GraphExecutionPlan.java:454-458` → `StreamTaskInvokable.wireOperators :239/:245` / `wireTailToRecordWriter :352`）；测试要求 = test-first 先红后绿（`TestOutputContractInvariant` pin 断言翻转 + 跨 task 端到端 fail-fast 用例）；**收尾 = 注册表分类更新（pinned-known-violation → fail-fast，含 TimestampedCollector disposition 同步）+ 过渡 pin 2 条移除 + 门禁复跑零命中**。结构性重构标注：**信封通过 → 无「需人工确认」门（信封内，显式声明）；信封不通过（Phase 1 失败路径）→ 标「需人工确认」门随 `HG-01` 待办延续，不派自动信封**；`HG-01` 线协议支持不属本项（明确边界）。**兜底**：仅当 I2 权威版仍含该条目（pin 未 verified）时派发；若 I2 裁定 verified（违规已消失）→ 记录关闭不派发，Phase 2 显式声明；**部分 verified 边缘**：若仅 1 条 pin verified、另 1 条仍红 → 仍派发剩余未 verified 条目，收尾按剩余 pin 数移除——**WI-C2-1 在案（adjudication-table.md §8，六要素 + 收尾三连 + 信封边界 = 通过分支 + HG-01 排除）；兜底核对：I2 权威版仍含 C2-RL-1/2（§2 pin 裁定均维持，未 verified）→ 派发成立，无「部分 verified」边缘**
- [x] 其余 P0/P1 派发清单：按族组织为 I4 工作项，**落点 = `ai-dev/audits/nop-stream-invariants/adjudication-table.md` Cycle 2 节「P0/P1 派发清单」小节**（每条：目标类 + 缺陷描述 + 预期行为 + **类别清扫范围**（grep 全类兄弟清单）+ 测试要求（test-first 先红后绿）+ 门禁复跑要求）；结构性重构项（公共 API / 模块边界 / Operator 接口）单独标注"需人工确认"——**§8「其余 P0/P1」显式声明「无其他 P0/P1 项」**
- [x] 新族派生登记（如有）：**落点 = `ai-dev/audits/nop-stream-invariants/adjudication-table.md` Cycle 2 节「Cycle 3 派生登记」小节**，每条含不变式陈述 + 触发证据（`文件:行`）+ PD-n 编号（接续既有编号：本仓已铸 PD-15；下次 = **PD-16**；编号规则 = `max(ai-dev/lessons/ 最高编号, 已铸 PD 号最大值) + 1`（当前 = max(14, 15) + 1 = 16），live grep 复核）；供 I6 按 Loop Rule 正式追加 Cycle 3 / I1 work item——**§9：显式声明「无新独立族」→ 无 PD-16 派生登记（编号规则复核：lessons 最高 14 / 已铸 PD-15 → 16，live grep 无已铸 PD-16，编号保留供 I6）；C2-PR-2（扫描器形态覆盖）/ C2-PR-5（控制面陈述扩展）已知族扩展候选显式移交 I6（不变式陈述候选 + 触发证据 `文件:行` 在案），显式裁定「不派生 PD-16」附依据**
- [x] P2/P3 处置：入 roadmap `## Follow-up Backlog` 节（记录位置 / 严重度 / 未来触发条件），与 roadmap 裁决规则及既有条目格式一致——**roadmap Follow-up Backlog 新增 2 条（2026-08-12）：C2-RL-3+C2-PR-4（P3 审计证据准确性 + E2E 覆盖扩展）/ C2-PR-3（P3 重复注册语义），含 Source / Description / Recommendation / Status / 触发条件，格式与既有条目一致**
- [x] 零悬挂复核：裁决表每条处置与派发清单一一对应（无"已裁决但无处可去"项）——**§7.4 复核表：8 条 ↔ 处置一一对应（1 个 I4 工作项 + 2 条 backlog + 4 条关闭/移交 I6），零悬挂达成**

Exit Criteria:

- [x] interim fail-fast 工作项在派发清单内（六要素齐全：目标类 / 缺陷 / 预期 / 类别清扫 / test-first / 门禁复跑 + 收尾三连：注册表更新 / pin 移除 / 复跑），且信封边界显式声明——**通过分支**：无「需人工确认」门（信封内）；**失败分支**：标「需人工确认」门随 `HG-01` 待办延续 + I4 派发清单标注结构性重构（两分支均与 Phase 1 信封复核结论一致）；`HG-01` 明确排除——**§8 WI-C2-1 六要素 + 收尾三连齐全；信封边界 = 通过分支（与 §7.3 一致）；HG-01 明确排除**
- [x] 其余 P0/P1 派发清单存在（按族 + 类别清扫范围 + 测试要求 + 门禁复跑要求）或显式声明"无其他 P0/P1 项"——**§8 显式声明「无其他 P0/P1 项」**
- [x] 新族派生登记存在（如有；PD-n 编号 + 触发证据 + 落点）或显式声明"无新族"，**或显式裁定"不派生"（附依据，记录于裁决表行内，供 I6 评估时可见）**——**§9 显式声明「无新独立族」+ C2-PR-2/5 显式裁定「不派生 PD-16」（依据在案，供 I6 可见）**
- [x] Follow-up Backlog 已更新（或显式声明"无 P2/P3 项"）——**roadmap 新增 2 条（2026-08-12）**
- [x] 零悬挂复核通过：处置 ↔ 派发一一对应——**§7.4 复核表在案，零悬挂达成**
- [x] No owner-doc update required（派发为过程产出）——**已裁定（本 plan 零代码变更）**
- [x] `ai-dev/logs/` 对应日期条目已更新——**`ai-dev/logs/2026/08-12.md` Phase 2 条目在案**

### Phase 3 - 共识审查与移交

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/adjudication-table.md`（Cycle 2 节）；`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`

- Item Types: `Decision | Follow-up`
- [x] 独立子 agent（fresh session，review-only 禁改文件）审查裁决表：**分类诚实性**（已确认 live defect 不得**无依据/静默**降级至 P2/P3 / non-blocking 区——guide Minimum Rules #16 + Anti-Slacking Rule）、零悬挂、严重度合理性（对照 P0-P3 标尺）、派发完整性（含新族双轨）、**interim fail-fast 信封复核**（自动信封边界：private 嵌套类 / 接口零变更 / 无结构性重构混入）——**独立审查在案（fresh session `ses_009c92234ffedLoBh1nxuahzop`，2026-08-12）：8/8 项 PASS——分类诚实性 / 零悬挂 / 严重度合理性 / 派发完整性 / 信封复核 / roadmap 一致性 / 行号准确性 / Anti-Hollow，全部附 live 证据；0 Blocker / 0 Major / 3 Minor**
- [x] 修复审查发现的 Blocker / Major 问题，必要时复审（每轮 fresh session）——**无 Blocker / Major 需修复；3 Minor 已全部修复（adjudication-table.md：C2-RL-1/2 补 P0/P1 边界显式裁定句 + C2-RL-2 补 BRWO 独立影响面；§7.1 补非族候选追溯行）；Minor 级无需复审轮（0 Major）**
- [x] roadmap Work Item Cycle 2 / I3 状态流转记录（本 plan 转 active 时 `todo`→`planned`；closure audit 通过后 `planned`→`done`）——**`todo`→`planned` 已于 plan 转 active 时完成；`planned`→`done` 已由 closure audit 通过后执行（roadmap Cycle 2 / I3 行 `done`，含执行结果回写）**

Exit Criteria:

- [x] 独立审查记录存在（含审查结论与问题清单）；全部 Blocker 已解决——**审查记录在案（8/8 PASS + 问题清单）；0 Blocker；3 Minor 修复在案**
- [x] 无 in-scope live defect 被无依据降级至 backlog / watch-only（分类诚实性复核结论在案；已裁定处置附依据即合规）——**审查 PASS：C2-RL-1/2 = P1 派 I4（未降级）；全部 8 条目处置附依据**
- [x] interim fail-fast 信封复核结论在案（无结构性重构混入自动信封）——**审查 PASS：信封通过（§7.3 真实检查，无结构性重构混入）**
- [x] `ai-dev/logs/` 对应日期条目已更新——**`ai-dev/logs/2026/08-12.md` Phase 3 条目（审查记录）+ Closure 收口条目在案**

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 裁决表零悬挂（Cycle 2 / I2 red list 全部条目有处置）——**adjudication-table.md §7.4 复核表：8 条目（C2-RL-1/2/3 + C2-PR-1..5）↔ 处置 1:1，零悬挂达成**
- [x] interim fail-fast 预授权确认 + I4 派发完整（含类别清扫范围 + 测试要求 + 收尾三连）；**信封失败分支**（如触发）已按「需人工确认」门处置（随 `HG-01` 待办延续），派发清单标注结构性重构——**§7.3 信封通过（失败分支未触发显式记录）；§8 WI-C2-1 六要素 + 收尾三连齐全，无「需人工确认」门；`HG-01` 明确排除**
- [x] 其余 P0/P1 派发 I4 清单完整（如有）或显式"无"——**§8 显式声明「无其他 P0/P1 项」**
- [x] 新族派生登记齐全（如有）或显式"无新族"，或显式裁定"不派生"（附依据在案）；P2/P3 backlog 在案或显式"无"——**§9 显式「无新独立族」+ C2-PR-2/5「不派生 PD-16」裁定附依据；roadmap Follow-up Backlog 新增 2 条（C2-RL-3+C2-PR-4 / C2-PR-3）**
- [x] 无已确认 live defect 被无依据/静默降级到 non-blocking 区（分类诚实性：已裁定处置附依据即合规；未裁定 deferral 即违规）——**C2-RL-1/2 = P1 派 I4 未降级（含 P0/P1 边界显式裁定句）；独立审查 8/8 PASS 确认**
- [x] 独立子 agent closure-audit 已完成并记录证据（`ai-dev/logs/`）——**fresh session `ses_009c5e1aafferNHFD7SKkkXMde`（2026-08-12）8/8 PASS，verdict ready for completed；证据见 Closure 段 + `ai-dev/logs/2026/08-12.md`**
- [x] **Anti-Hollow Check**：派发清单的每条与裁决表一一对应（非空壳派发）；backlog 条目不伪装成已修复；无空方法体 / 静默跳过——**§7.4 1:1 复核表 + §10 ↔ roadmap backlog 2 条对应；backlog 条目状态均为 todo 未伪装已修复；closure audit 8/8 复核 §7-§10 无空节无 TBD**
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0——**实测 exit 0（Passed: 1，2026-08-12；final 复跑见 Closure 段）**
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0——**实测 exit 0（0 errors；3 条 BROKEN_LINK 为 credential plan `2026-08-12-0615-3-...` 既有基线，非本 plan 引入）**
- [x] 本 plan 为纯决策 / 文档计划（无代码变更）：`./mvnw compile/test` 构建门禁与 `scan-hollow-implementations.mjs` 不适用，按 guide「纯文档计划」删除——**git status 实测仅 4 个 ai-dev 文档文件变更（adjudication-table.md / roadmap / logs / plan 本体），nop-stream/ 代码零改动 → 构建门禁与 hollow 扫描按「纯文档计划」删除**

## Deferred But Adjudicated

### `HG-01` 跨 task side-output 线协议结构性变更（人工确认待办延续）

- Classification: `Fix`（已确认契约缺口 P1，执行门 = 人工确认）——**已确认 live defect，必须修（线协议支持），不属 deferral**，仅执行门未过
- Why Not Blocking Closure: 修复需 RecordWriter 线协议结构性重构，人工确认门未过（I6 登记在案：`HG-01`，含证据 / 修复方向 / 触发条件）；interim fail-fast（自动信封内）先落地消除静默丢弃，`HG-01` 支持属增强不阻塞；保护性覆盖四重留痕继续生效。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan（跨 task 侧输出线协议设计 + 实现 + E2E）；触发条件 = 人工批准 + 跨 task side-output 需求出现（或 CI 门禁红暴露新实例）

## Non-Blocking Follow-ups

- Cycle 2 / I4 计划（`2026-08-12-1217-11-...`）以本 plan 的 P0/P1 派发清单为输入起草（interim fail-fast 工作项 + 其余派发项）。
- Cycle 3 派生登记（如有）**显式移交 I6**：I6 按 Loop Rule 以 `adjudication-table.md` Cycle 2 节「Cycle 3 派生登记」小节为输入正式追加 roadmap work item。
- 若 I2 探查记录了"需更长周期验证"的场景（如极端并发压力），由 I4 / I5 视情况覆盖，不阻塞本 plan 关闭。

## Closure

Status Note: Cycle 2 / I3 发现裁决与工作项拟制全部 3 Phase 完成——裁决表 Cycle 2 节落档（adjudication-table.md §7-§10，8 条目零悬挂：C2-RL-1/2 = P1 派 I4（interim fail-fast，§7.3 信封复核通过 = 真实检查，无人工确认门）/ C2-RL-3 = P3 backlog / C2-PR-1/2/5 = 关闭（非 defect，C2-PR-2/5 显式移交 I6）/ C2-PR-3/4 = P3 backlog）；interim fail-fast 预授权确认 + WI-C2-1 派发完整（六要素 + 收尾三连）；无新族显式声明 + C2-PR-2/5 扩展候选移交 I6；Follow-up Backlog 新增 2 条；独立共识审查 8/8 PASS（0 Blocker / 0 Major / 3 Minor 已修）+ 独立 closure-audit 8/8 PASS。本 plan 为纯决策 / 文档计划：零代码变更，构建门禁按「纯文档计划」删除。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）
- Audit Session: `ses_009c5e1aafferNHFD7SKkkXMde`
- Evidence:
  - 每条 Exit Criterion / Closure Gate 验证结果（8/8 PASS）：
    1. Plan 文本一致性：Phase 1/2 completed + 全 [x]；Phase 3 items 1-2 + Exit Criteria 1-3 [x]（item 3 / exit criterion 4 为 closure 触发项，已随本次收口勾选）；closure audit 前唯一未勾 = plan 自保留项，无越界
    2. 裁决表质量：§7.1 假设复核 5 行无 NEEDS_I2_SUPPLEMENT；§7.2 恰好 8 行（C2-RL-1/2/3 + C2-PR-1..5）每条含 族/严重度/处置/依据（I2 red-list + 探查报告引用）；§7.3 信封结论显式（通过 + 失败分支未触发）；§7.4 8 行 1:1 零悬挂；§8 WI-C2-1 六要素 + 收尾三连（含 TimestampedCollector disposition 同步）+ 显式「无其他 P0/P1」；§9 显式「无新族」+ C2-PR-2/5 不派生裁定 + 移交 I6；§10 ↔ roadmap backlog 2 条一致；Cycle 1 §0-§6 历史节保留（仅头部 4 行元数据更新）
    3. roadmap 一致性：Follow-up Backlog 恰好 2 条新条目（含 Source/Description/Recommendation/Status/触发条件）；Cycle 2 / I3 行在 audit 前仍 planned（未提前 done）
    4. 分类诚实性：C2-RL-1/2 = P1 派 I4 未降级（P0/P1 边界显式裁定）；全部处置附依据，无静默降级
    5. 零代码变更：`git status --short` = 仅 4 个 ai-dev 文件（adjudication-table.md / roadmap / 08-12.md / plan 本体），nop-stream/ src 零改动
    6. 工具门禁：`check-plan-checklist.mjs --strict` exit 0（Passed: 1）；`check-doc-links.mjs --strict` exit 0（3 条 BROKEN_LINK 为 credential plan 既有基线，非本 plan 引入）；mjs-pins.json 2 条 pin 仍存（I4 移除，符合派发语义）；注册表 RWO/BRWO 仍 pinned-known-violation（I4 迁移，符合派发语义）
    7. Anti-Hollow：§7-§10 全填充无 TBD；live 代码抽查全部匹配（RWO :645-647 / BRWO :705-706 空体、private 嵌套类 :611/:660、Output 接口 :41/:59、ChainingOutput :119 fail-fast）；日志 Phase 1/2/3 条目在案
    8. Deferred 分类检查：Deferred 节仅 `HG-01`（人工确认门，非 deferral）；interim fail-fast 派发未延期；follow-up 均非阻塞
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-1217-10-nop-stream-invariants-cycle2-I3-adjudication.md --strict` 退出码 0（Passed: 1；completed 状态 final 复跑在案）
  - Anti-Hollow 检查结果：独立审查 + closure audit 双 fresh session 复核 §7-§10 无空节无 TBD；backlog 条目状态 todo 未伪装已修复；git status 零代码变更
  - Deferred 项分类检查：`HG-01` = 人工确认门（已确认契约缺口 P1，执行门未过，Successor 在案）；interim fail-fast = 派发 I4（自动信封）；E2E/注册表措辞/重复注册 = P3 backlog（已裁定附依据）——分类诚实，无 in-scope live defect 被降级
  - 额外复核：信封复核行号全部 live 核对（RWO :645-647 / BRWO :705-706 / Output.java :41/:59 / ChainingOutput.java:111-126 / GraphExecutionPlan :454-461 / wireOperators :239/:245 / wireTailToRecordWriter :352）；PD-16 编号规则（max(14,15)+1=16）live grep 复核无已铸 PD-16

Follow-up:

- Cycle 2 / I4 计划（`2026-08-12-1217-11-...`）以 §8 P0/P1 派发清单（WI-C2-1）为输入执行 interim fail-fast 修复（注册表迁移 + pin 移除 + 门禁复跑）。
- C2-PR-2 / C2-PR-5 扩展候选显式移交 I6 收口：I6 按 Loop Rule 评估是否正式追加 Cycle 3 / I1 work item（adjudication-table.md §9 为输入）。
- C2-RL-3+C2-PR-4 / C2-PR-3 backlog 条目：I4 类别清扫或复探触发时评估。

## Optional Sections

## Outdated Note

无（Cycle 2 / I2 为唯一前置，未失效）
