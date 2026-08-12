# Cycle 2 / I6 — 循环收口与下一轮触发判定（Closure And Next-Cycle Trigger Determination）

> Plan Status: active
> Last Reviewed: 2026-08-12
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：2 Major（C2-PR-5 行号 stale + I5 空体行号 stale）+ 8 Minor，全部修复；round 2：11/11 修复验证 PASS、无 Blocker/Major、verdict 可转 active，3 个非阻塞 Minor 已顺手修复）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I6（统计/稳态判定；`HG-01` 线协议支持如获人工批准另立 plan（Successor）；closure 独立 fresh session）+ Loop Rule；I5 全量验证结果 `ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`（唯一落点）；I3 派生登记 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` §9（C2-PR-2/5 扩展候选移交）与 §7-§10 裁决表；mission `nop-stream-invariant-loop.json`（Cross-Cutting 授权边界）
> Related: 前置 `2026-08-12-1217-12-nop-stream-invariants-cycle2-I5-full-verification.md`（I5，硬串行依赖，须已 `completed`）；后继 = 依本 plan 裁定：Cycle 3 / I1（若派生）或 `HG-01` 人工确认后的线协议 plan（若人工批准）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 2 / I6. 循环收口与下一轮触发判定

## Purpose

收口 Cycle 2 并裁定下一轮触发：确认 I5 全量验证统计（门禁数 / red list / 新族数，唯一落点 `cycle2-I6-input.md`）、确认 red list 零悬挂（C2-RL-1/2 修复 + C2-RL-3 P3 已裁决）、按 Loop Rule 评估 I3 移交的 2 个扩展候选（C2-PR-2 扫描器形态覆盖 / C2-PR-5 控制面陈述扩展）并裁定「派生 Cycle 3 / I1 或维持稳态」、复核 `HG-01` 人工确认门状态（未批准 = 维持待办登记；已批准 = 另立 Successor plan）、登记复触发条件、roadmap Work Item I6 状态流转 `todo`→`planned`→`done`、独立 fresh session closure。本 plan 是纯文档 / 裁决计划：不写代码、不沉淀门禁、不修复缺陷。

## Current Baseline

> 已核对 live repo 与裁决档案（2026-08-12）。

- **I1–I5 前置状态**：I1（plan `2026-08-12-1217-8-...`）、I2（`2026-08-12-1217-09-...`）、I3（`2026-08-12-1217-10-...`）、I4（`2026-08-12-1217-11-...`）全部 `completed`；I5（`2026-08-12-1217-12-...`）为本 plan 硬前置。
- **red list 零悬挂预期（I4 已修复 + I3 已裁决）**：C2-RL-1/2（RWO/BRWO 跨 task no-op）I4 已修复（commit `88bc0270c`，fail-fast + 注册表分类迁移 + pin 移除）；C2-RL-3（注册表措辞过 claim）= P3 backlog（I3 §7.2/§10，与 C2-PR-4 合并条目）；C2-PR-1 = 关闭（非 defect，I3 §7.2）；C2-PR-2 / C2-PR-5 = 关闭 + **显式移交 I6**（I3 §7.2/§9：扩展候选，Loop Rule 评估）；C2-PR-3 = P3 backlog（I3 §7.2/§10）。
- **扩展候选 2 个（I3 移交，本 plan 核心裁定输入）**：
  - **C2-PR-2（扫描器静默跳过形态）**：`check-nop-stream-invariants.mjs` parseTypeStructure :622-652（仅框定 `class|interface|enum`）+ V4 声明 regex :795-799（要求 `OutputTag<...>` 泛型形态）——匿名类 `new Output<X>(){}` / `record ... implements Output` / raw `OutputTag tag` 三形态静默漏检（当前 0 实例，grep 实测）；显式 fail 五路径已实现 + self-test 覆盖。扩展方向 = scan-output-contract 增加匿名类 / record 形态显式 fail 或枚举 + raw OutputTag 声明覆盖。
  - **C2-PR-5（控制面方法陈述扩展）**：`StreamTaskInvokable.java:644-646/:662-664`（RWO emitWatermarkStatus/emitLatencyMarker 空体，注释文档化）/ `:713-714/:730-731`（BRWO 空体无注释）——跨 task 控制面方法（WatermarkStatus / LatencyMarker）静默丢弃；**非用户数据丢失**（控制面遥测 / 空闲检测降级）；修复需 RecordWriter 线协议扩展（同 `HG-01` 门——人工确认）；不变式 #6 陈述扩展候选 =「跨 task 部署下 Output 控制面方法不得静默丢弃，或显式 fail-fast / 文档化」。（注：I2 探查报告与 I3 §7.2 引用的 `:640-642/:650-652/:701-702/:709-710` 为 pre-I4-fix 旧行号，live 以本节为准）
- **`HG-01` 状态（live）**：`pending human confirmation`——roadmap Follow-up Backlog 条目在案（含 6 发射点证据 / 修复方向 / 触发条件 / 四重保护性覆盖）；I3 §8 显式排除（不在自动信封内）；I4 修复落地后 pin 已移除（`HG-01` 线协议支持 = 增强，不阻塞 pin 移除）。**本 plan 执行时需复核是否已获人工批准**。
- **I5 为硬前置**：本 plan 执行前 `2026-08-12-1217-12-...` 必须已 `completed`（全绿 + `cycle2-I6-input.md` 落档）。若执行时 I5 未 completed，本 plan 不得开始任何 Phase——**立即返回 blocked 状态**。
- **真正剩余的 gap**：Cycle 2 收口裁定（稳态判定 = 派生 Cycle 3 / I1 或稳态暂停）、C2-PR-2/5 扩展候选的 Loop Rule 裁定、`HG-01` 状态复核与处置登记、复触发条件登记、roadmap I6 行状态流转、独立 closure。

## Goals

- 确认 Cycle 2 收口统计与 `cycle2-I6-input.md` 一致，且 red list 零悬挂（C2-RL-1/2 修复 + C2-RL-3 P3 已裁决）。
- 按 Loop Rule 裁定 2 个扩展候选（C2-PR-2 / C2-PR-5）：**派生 Cycle 3 / I1**（预授权自动追加 roadmap work item，附触发证据）或**维持稳态**（零新族且 red list 零悬挂 → 稳态暂停 + 登记复触发条件）；裁定必须显式记录依据（I3 §9 移交要求）。
- 复核 `HG-01` 状态：未批准 → 维持 `pending human confirmation` 待办登记（保护性覆盖在案）；已批准 → 另立 Successor plan 登记（线协议设计 + 实现 + E2E）。
- 登记复触发条件；roadmap Work Item I6 状态流转 `todo`→`planned`→`done`。
- 独立 fresh session closure audit，evidence 写入本 plan Closure 段。

## Non-Goals

- **不修复任何代码**：C2-PR-5 控制面修复 = `HG-01` 线协议门（人工确认，不在本 plan 亦不在自动修复范围）；C2-PR-2 门禁形态覆盖 = 若派生 Cycle 3 / I1 则属下轮 I1 的建设范围，本 plan 不实现。
- **不沉淀新门禁 / 新不变式**（属 Cycle 3 / I1，若派生）。
- **不执行 Cycle 3 / I2 审计**（若派生，属下一轮 work item）。
- **不弱化 / 删除 / 豁免既有门禁**（棘轮规则只增不减；本 plan 无门禁改动）。
- **不重跑 I5 全量验证**（I5 已实测，本 plan 仅核对记录一致性）。
- **不裁决 I3 已关闭条目**（C2-PR-1 关闭、C2-PR-3/4 P3 backlog 均维持，不回改）。

## Scope

### In Scope

- 收口统计核对（`cycle2-I6-input.md` ↔ on-disk surefire 报告；red-list.md §1；mjs-pins.json；output-contract-registry.json）。
- 稳态判定（零新族 + red list 零悬挂 → 稳态暂停；任一扩展候选裁定派生 → Cycle 3 派生）。
- C2-PR-2 / C2-PR-5 扩展候选 Loop Rule 裁定（显式依据，I3 §9 移交收口）。
- `HG-01` 状态复核与处置登记（未批准 = 维持待办；已批准 = Successor 登记）。
- roadmap 回写（派生时追加 Cycle 3 work item / 维持时更新复触发条件与 backlog 状态）+ Follow-up Backlog 状态核对。
- 复触发条件登记；roadmap I6 行状态流转；`ai-dev/logs/` 记录。
- 独立 fresh session closure audit + evidence。

### Out Of Scope

- 任何代码 / 门禁 / 不变式变更（属 Cycle 3 / I1，若派生）。
- Cycle 3 的执行（I1–I6 各 plan 另行拟制，本 plan 仅派生登记）。
- 对既有 completed 计划（I1–I5）的正文回写（Minimum Rules #20）。
- C2-PR-3 / C2-PR-4 / C2-RL-3 的重新裁决（I3 已裁定 P3 backlog，维持；`cycle2-I6-input.md` 覆盖事实表供其后续触发评估引用）。

## Execution Plan

### Phase 1 - 收口统计确认与稳态判定

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`、`red-list.md`、`mjs-pins.json`、`output-contract-registry.json`、nop-stream 模块组 surefire 报告（`nop-stream/*/target/surefire-reports/TEST-*.xml`，限定 nop-stream 模块组，不含 `-am` 连带的上游模块）、`ai-dev/logs/2026/08-12.md`

- Item Types: `Proof`

- [x] 逐条核对 `cycle2-I6-input.md` 统计与 on-disk surefire 报告一致（解析 nop-stream 模块组 TEST-*.xml 汇总 tests/failures/errors；**确认报告 mtime 与 I5 执行窗口一致**——若 mtime 晚于 I5 记录时间，说明报告已被后续构建覆盖，须以 I5 `ai-dev/logs/` 记录为准并在本 plan 记录差异；门禁 10 类 / 102 tests 与 `-Dtest='Test*Invariant*'` 报告一致；pin 0 与 `mjs-pins.json` pinnedViolations 空一致）。**出入处置**：以 surefire 实测为准，差异记录到 `ai-dev/logs/` + 本 plan 裁定记录与 closure evidence；**不回写 `cycle2-I6-input.md`**（I5 落点唯一性原则——统计落点归 I5，数据差异只记录不回写；差异不影响本 plan 裁定）
  - **实测记录（2026-08-12 I6 执行，live 复核）**：448 份 TEST-*.xml 汇总 = 2833 tests / 0 failures / 0 errors / 10 skipped（core 1428/0/0/1、runtime 805/0/0/8、cep 320/0/0/0、rocksdb 83、connector 35、connector-jdbc 32、connector-batch 35、connector-debezium 19/0/0/1、flow 51、fraud-example 25）——与 `cycle2-I6-input.md` 逐模块精确一致；门禁 10 类（`Test*Invariant*`）surefire 实测 = 102 tests / 0 failures / 0 errors / 0 skipped（CheckpointIDCounter 8 / SynchronizedCollection 12 / OutputContract 10 / core 表完备 10 / WindowRoundTrip 9 / ClusterRegistry 10 / MergingCleanup 4 / runtime 表完备 11 / CepRelease 21 / cep 表完备 7）——与落档一致。**mtime 窗口核对**：全部 448 份报告 mtime = 2026-08-12 23:15-23:16（I5 执行窗口 23:07 起跑 + 门禁子集 / e2e 复跑收尾）；git log 显示 I5 之后唯一提交 = 本 plan 建档 `2286ddc2d`（无后续构建），工作树干净；I5 closure audit（fresh session `ses_0097998bfffebu39LYN7QUxH0G`）已独立重算同一 448 份报告与落档精确一致 → **无出入，无需按「出入处置」路径处理，不回写 I5 落点**。pin 0：`mjs-pins.json` pinnedViolations = [] 与落档一致；mjs `all` exit 0 实测（I6 执行时点复跑，inventory/sync/scan-iterations/scan-output-contract/self-test 全绿）。
- [x] 确认 red list 零悬挂：red-list.md §1 C2-RL-1/2 修复证据在案（commit `88bc0270c` 可溯源）+ C2-RL-3 P3 裁决在案（I3 §7.2/§10，backlog 条目）；C2-PR-1..5 处置一一对应（关闭 / backlog / 移交本 plan）
  - **实测记录**：`git log` 实测 `88bc0270c fix(nop-stream): Cycle 2 / I4 — cross-task side-output interim fail-fast (RWO/BRWO collect(OutputTag) no-op -> ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER) + registry reclassification + transition pin removal` 在案；red-list.md §1 C2-RL-1/2 修复状态 = 已修复（fail-fast 落地 + 注册表迁移 + pin 移除 + 断言翻转 + E2E 第 4 用例）；注册表 live 复核 RWO/BRWO `collect(OutputTag)` :649-659/:717-727 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`（含 ARG_OUTPUT_TAG / ARG_DETAIL）。C2-RL-3 = P3 backlog（adjudication-table.md §7.2 + roadmap「输出契约族审计证据准确性 + E2E 覆盖扩展」条目，Status: todo 附触发条件）。C2-PR-1..5 处置一一对应：C2-PR-1 关闭（检查后无问题）/ C2-PR-2 关闭 + 移交 I6 / C2-PR-3 P3 backlog / C2-PR-4 P3 backlog（与 C2-RL-3 合并条目）/ C2-PR-5 关闭 + 移交 I6（adjudication §7.2/§7.4 复核表 1:1）。**零悬挂达成**。
- [x] 确认新族数 = 0：I2/I3 显式「无新独立族」（C2-PR-2/5 = 已知族扩展候选，非新失败类）；I5 零新失败 → 无新增族
  - **实测记录**：red-list.md §3/§4「无新独立族——全部发现属已知族（不变式 #6 输出契约族）或其后继扩展候选」；adjudication §7.2/§9「无 PD-16 派生登记（非新失败类）」。I5 全量 2833 + 门禁 102 + e2e（4/4 + 6/6 + 7/7）零新失败 → 新族数 = 0。
- [x] **`HG-01` 状态复核**：检查 roadmap Follow-up Backlog 条目状态 + 是否有人工批准记录（daily log / backlog 条目变更）；未批准 → 维持 `pending human confirmation`；已批准 → Phase 2 登记 Successor
  - **实测记录**：roadmap Follow-up Backlog `HG-01` 条目状态 = `pending human confirmation`（四重保护性覆盖留痕完整：① in-task fail-fast `88bc0270c` 前置 + `b20fcd0e1`；② Cycle 2 / I1 门禁（类级枚举 / call-site 注册表 / 三态分类）；③ 过渡 pin 移除留痕（`mjs-pins.json` PIN REMOVAL TRACE，`HG-01` 不阻塞 pin 移除）；④ backlog 待办条目）；全仓 grep daily log / backlog / plans 无任何人工批准记录 → **未批准 → 维持 `pending human confirmation` 登记，Phase 2 不登记 Successor**。
- [x] **稳态判定（初步）**：零新族 + red list 零悬挂 → 稳态暂停候选；C2-PR-2/5 扩展候选的派生与否按 Phase 2 裁定——本 Phase 记录「无新失败类 → Loop Rule『新族强制沉淀』不触发；扩展候选派生属 I6 自由裁定范围」的判定边界（**论证**：roadmap §Loop Rule「零新族且 red list 零 → 稳态暂停」的强制分支仅针对「新失败类」的派生义务（「新族强制沉淀」句），C2-PR-2/5 已被 I3 §7.2/§9 显式裁定为**非新失败类**（已知族门禁表达扩展候选），故不触发强制派生分支，派生与否 = I6 显式评估（I3 §9 原文「派生与否 = I6 Loop Rule 显式评估」）——本计划为该裁定提供可执行路径）
  - **实测记录**：零新族（见上）+ red list 零悬挂（见上）→ **稳态暂停候选成立**；C2-PR-2/5 派生与否 = Phase 2 显式裁定（本 plan 为该裁定提供可执行路径）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 核对记录在案：`cycle2-I6-input.md` 全部统计项与 on-disk surefire 一致（无出入或出入已修正记录）
- [x] red list 零悬挂确认记录（C2-RL-1/2 修复 + C2-RL-3 P3 裁决；C2-PR-1..5 处置一一对应）
- [x] 新族数 = 0 确认记录（无新失败类，Loop Rule 强制派生不触发）
- [x] `HG-01` 状态复核结论记录（未批准 / 已批准）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 本 Phase 为纯核对/文档工作，无代码变更 → `No owner-doc update required`（`docs-for-ai/` 不涉及；`ai-dev/` 记录归本 plan 各 Phase）

### Phase 2 - 扩展候选裁定与 Cycle 3 / 稳态处置

Status: completed
Targets: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`（Work Item 表 + Follow-up Backlog）、`ai-dev/audits/nop-stream-invariants/adjudication-table.md`（I6 裁定记录，§11 节追加——顺延既有 §6「Cycle 1 / I6」/ §7-§10「Cycle 2 / I3」编号，避免与 §6 歧义）

- Item Types: `Decision | Follow-up`

- [x] **C2-PR-2 裁定（扫描器静默跳过形态）**：按 Loop Rule 评估——扩展方向 = scan-output-contract 增加匿名类 / record Output 实现形态显式 fail 或枚举 + raw OutputTag 声明覆盖（触发证据 = `check-nop-stream-invariants.mjs` parseTypeStructure :622-652 / V4 声明 regex :795-799；当前 0 实例）。裁定二选一：**(a) 派生 Cycle 3 / I1**（追加 work item，附触发证据，预授权自动追加，同 PD-n 先例链）或 **(b) 维持稳态**（登记复触发条件 =「main 代码出现匿名 / record Output 实现或 raw OutputTag 声明时」触发 Cycle 3）；裁定必须显式记录依据（成本 / 收益 / 触发面 / 棘轮影响）
  - **裁定：(b) 维持稳态**。依据（live 复核 + 成本收益 + 触发面 + 棘轮影响）：
    - **触发面（0 实例）**：grep 实测 main 无匿名 `new Output<>(){}` / 无 `record ... implements Output` / 无 raw `OutputTag` 声明——三形态均为**未来代码**的预防性风险，非当前 live defect（与 I3 §7.2/§9「非新失败类、无失败实例」裁定一致）。
    - **现有防护面**：显式 fail 五路径全部实现 + self-test 覆盖（未识别方法体形态 hard error = fail-loud，不静默归类）；V1/V3/V4 对已识别形态 fail-loud；false-positive 方向安全（多报不放过）。门禁对当前与近期代码形态的全覆盖未被削弱。
    - **成本**：派生 Cycle 3 = I1–I6 六份 plan + 六轮执行的完整循环机器，为一个**零实例**的门禁表达扩展——机器重量与收益不匹配。
    - **收益**：扫描器未来捕获新形态（匿名 / record / raw tag）。等价收益可通过确定性复触发登记以远低得多的成本达成（周期复探 + 审计即可捕捉）。
    - **棘轮影响**：维持 = 门禁零削弱（棘轮「只增不减」不受影响）；派生 = 纯增强（扫描器 regex 扩展），非强制。
    - **复触发登记**：main 出现匿名 / record Output 实现或 raw OutputTag 声明 → 触发 Cycle 3（Phase 3 落档 roadmap）。
- [x] **C2-PR-5 裁定（控制面陈述扩展）**：按 Loop Rule 评估——扩展方向 = 不变式 #6 陈述扩展「跨 task 部署下 Output 控制面方法（emitWatermarkStatus / emitLatencyMarker）不得静默丢弃，或显式 fail-fast / 文档化」（触发证据 = `StreamTaskInvokable.java:644-646/:662-664/:713-714/:730-731`）；**修复依赖 `HG-01` 线协议人工确认门**。裁定二选一：**(a) 派生 Cycle 3 / I1**（含门禁 + 过渡 pin 或 fail-fast 预授权信封评估）或 **(b) 维持稳态**（与 `HG-01` 同门登记：复触发 = 人工批准线协议变更后；当前影响 = 控制面遥测降级非数据丢失，RWO 两处已注释文档化、BRWO 两处空体无注释（code-style 级观察，I2 探查已登记））；裁定必须显式记录依据
  - **裁定：(b) 维持稳态**。依据：
    - **影响面（非数据丢失）**：emitWatermarkStatus / emitLatencyMarker 跨 task 空体 = 控制面遥测 / 空闲检测降级，**非用户数据丢失**（I2 探查 C2-PR-5 + I3 §7.2 裁定一致）。live 复核（I6 实测）：RWO `emitWatermarkStatus` :644 / `emitLatencyMarker` :662 空体 + 注释文档化（「Not forwarded across task boundaries」/「Latency markers not forwarded across task boundaries」——行为意图可追溯，Rule #24 合规）；BRWO `emitWatermarkStatus` :713 / `emitLatencyMarker` :730 空体无注释（code-style 级观察，I2 探查已登记在案）。
    - **修复依赖 = 执行门未过**：真正修复需 RecordWriter 线协议结构性扩展（跨 task 控制面通道），与 `HG-01` 同一人工确认门（mission Cross-Cutting「结构性重构执行前人工确认」）——执行门未过。**若派生 Cycle 3 / I1**：其 I4 修复同样卡在 `HG-01` 门 → 循环在 I4 停滞，派生态产出一条「门禁 + 过渡 pin」等于把现状（注释文档化 + 观察登记）转写成门禁语言，无实际行为收益 → 不派生，保持与 `HG-01` 同门登记。
    - **复触发登记**：`HG-01` 人工批准跨 task 线协议变更后，在 Successor / Cycle 3 中评估不变式 #6 陈述扩展（跨 task 控制面方法不得静默丢弃或显式文档化）与 RWO/BRWO 控制面处理（Phase 3 落档 roadmap）。
- [x] **稳态 / 派生总裁定**：综合 Phase 1 统计 + 两条候选裁定——若均维持 → **稳态暂停**（Cycle 2 收口完成，循环暂停，登记复触发三选一）；若任一派生 → **Cycle 3 / I1 work item 追加**（roadmap 表追加，附触发证据 + PD-n 先例链引用）
  - **裁定：稳态暂停**。依据：C2-PR-2（维持）+ C2-PR-5（维持）→ 两条候选均未派生；Phase 1 零新族（0）+ red list 零悬挂 + 门禁全绿（10 类 / 102 tests / mjs all exit 0）→ Loop Rule「零新族且 red list 零 → 稳态暂停」成立；「新族强制沉淀」强制分支未触发（无新失败类）。**Cycle 3 不派生，roadmap 不追加 work item**；复触发条件登记见 Phase 3。
- [x] **`HG-01` 处置登记**：未批准 → 维持 backlog 待办条目（四重保护性覆盖复核在案：in-task fail-fast / 门禁 / pin 已随 I4 移除留痕 / backlog 条目）；已批准 → 登记 Successor plan 需求（跨 task 侧输出线协议设计 + 实现 + E2E），并在 roadmap Follow-up Backlog 标注「已批准，Successor 计划中」
  - **处置：未批准 → 维持待办登记**（Phase 1 复核结论：无任何人工批准记录）。四重保护性覆盖复核（I6 实测）：
    ① **in-task fail-fast**：`88bc0270c`（跨 task interim fail-fast）+ `b20fcd0e1`（in-task ChainingOutput 转发 + fail-fast）git log 在案；② **门禁**：Cycle 2 / I1 输出契约族门禁（类级枚举完备性 + call-site 注册表 6 发射点 + 行为三态分类）全绿（mjs all exit 0 实测）；③ **pin 移除留痕**：`mjs-pins.json` PIN REMOVAL TRACE 在案（`HG-01` 线协议支持落地属增强，不阻塞 pin 移除）；④ **backlog 条目**：roadmap Follow-up Backlog `HG-01` 条目状态 `pending human confirmation`，Successor 标注「人工确认后另立 plan」。→ **不登记 Successor plan**（门未过）。
- [x] **roadmap 回写**：依总裁定追加 Cycle 3 / I1 work item（若派生）或更新复触发条件登记（若稳态）；Follow-up Backlog 条目状态核对（C2-RL-3/C2-PR-4/C2-PR-3 维持 P3；`HG-01` 状态更新）
  - **回写记录**：总裁定 = 稳态暂停 → **roadmap 不追加 Cycle 3 / I1 行**；§Loop Rule 追加「Cycle 2 / I6 稳态登记（2026-08-12）」（稳态暂停 + 复触发条件，见 Phase 3）；Follow-up Backlog 状态核对：C2-RL-3/C2-PR-4 合并条目 + C2-PR-3 条目维持 `todo`（P3，触发条件不变）、`HG-01` 条目维持 `pending human confirmation`（无变更，不标「已批准」）。
- [x] **I6 裁定记录落档**：adjudication-table.md 追加 §11 裁定节（Cycle 2 / I6 收口裁定记录：收口统计确认、稳态/派生裁定、C2-PR-2/5 逐条依据、`HG-01` 处置、复触发登记摘要）——供后续 cycle 引用
  - **落档**：`ai-dev/audits/nop-stream-invariants/adjudication-table.md` §11（Cycle 2 / I6 收口裁定记录）已追加。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] C2-PR-2 裁定记录在案（派生 or 维持稳态 + 显式依据）
- [x] C2-PR-5 裁定记录在案（派生 or 维持稳态 + 显式依据，含 `HG-01` 依赖说明）
- [x] 稳态 / 派生总裁定记录在案（roadmap 已回写：Cycle 3 行或复触发登记）
- [x] `HG-01` 处置登记在案（未批准维持 / 已批准 Successor）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 无静默跳过：扩展候选未以「后续再说」/「watch-only」形式降级——已确认事项全部有显式裁定记录（派生 or 维持 + 依据 + 复触发条件）
- [x] I6 裁定记录落档（adjudication-table.md §11）

### Phase 3 - 复触发登记与 roadmap 状态流转

Status: completed
Targets: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`、`ai-dev/logs/2026/08-12.md`

- Item Types: `Decision | Follow-up`

- [x] **复触发条件登记**（roadmap §Loop Rule / §I6 Phase Details）：继续登记三选一复触发（① CI 任一不变式门禁变红；② nop-stream 核心类结构变更（新增/重命名 Operator/SinkFunction/Checkpoint 机制/Output 实现类）；③ 周期复探）；叠加本 plan 新增触发（若 C2-PR-2 维持：main 出现匿名/record Output 实现或 raw OutputTag 声明；若 C2-PR-5 维持：`HG-01` 人工批准）；人工确认待办的触发 = 人工批准跨 task 线协议变更
  - **落档**：roadmap §Loop Rule 新增「Cycle 2 / I6 稳态登记（2026-08-12）」行 + §I6 Phase Details 新增「Cycle 2 / I6 裁定摘要」段——三选一 + C2-PR-2 形态出现（main 匿名 / record Output 实现 / raw OutputTag 声明）+ C2-PR-5 = `HG-01` 人工批准（含不变式 #6 陈述扩展评估）+ 人工确认待办触发（`HG-01`）。
- [x] roadmap Work Item I6 行状态流转 `todo`→`planned`（本 plan 激活时）→`done`（closure audit 通过后，不得提前）
  - **落档**：roadmap I6 行 `todo` → `planned`（2026-08-12 执行中补记「本 plan 激活时」登记）；`done` 待独立 closure audit 通过后执行（Plan Status completed 时同步）。
- [x] `ai-dev/logs/2026/08-12.md` 顶部追加 I6 条目（统计确认 + 裁定摘要 + Cycle 3 派生或稳态暂停 + 复触发登记）
  - **落档**：日志顶部追加三条目（Phase 1 统计确认 / Phase 2 裁定摘要 + 稳态暂停 + `HG-01` 处置 / Phase 3 复触发登记 + 流转）。
- [x] 文本一致性核对：Plan Status、Phase Status、Exit Criteria、Closure Gates、roadmap 行、daily log 六处一致后方可进入 closure
  - **核对**：closure 前中间态一致（Plan Status `active` + Phase 1/2/3 `completed` 全 [x] + roadmap I6 行 `planned` + 日志三条目）；Closure Gates 与 Closure 段 = closure audit 触发项（closure 时勾选），不为不一致。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 复触发条件登记在案（三选一 + 本 plan 新增触发）
- [x] roadmap I6 行已流转（`planned`；`done` 在 closure 后由独立审计确认）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 文本一致性核对完成（无「顶部已 completed、内部未勾选」矛盾态）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：本 plan 不涉及任何代码变更（仅修改 `ai-dev/` 下文档），`./mvnw test`、`./mvnw lint` 等构建验证条目已按 guide 删除；保留文档与 checklist 门禁。

- [ ] 收口统计确认（与 `cycle2-I6-input.md` 一致）且 red list 零悬挂（C2-RL-1/2 修复 + C2-RL-3 P3 裁决）
- [ ] 扩展候选裁定完成（C2-PR-2 / C2-PR-5 逐条依据在案）→ Cycle 3 派生（roadmap 追加行 + 触发证据）或稳态暂停（复触发登记）已落档
- [ ] `HG-01` 处置登记在案（未批准维持待办 + 保护性覆盖复核 / 已批准 Successor 登记）
- [ ] 复触发条件登记在案
- [ ] 无 in-scope live defect / contract drift 被静默降级到 deferred / follow-up（C2-PR-5 控制面 = 非数据丢失 + `HG-01` 人工确认门 + RWO 已文档化 / BRWO 空体已登记（code-style 观察）；C2-PR-2 = 0 实例门禁表达扩展候选；均非已确认 live defect，且均有显式裁定）
- [ ] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [ ] **Anti-Hollow Check**：本 plan 纯文档，无代码接线面；closure audit 验证（a）roadmap 回写与 I5 实测记录 / 裁决档案一致，（b）扩展候选裁定与 live 代码（扫描器 :622-652/:795-799、`StreamTaskInvokable.java:644-646/:662-664/:713-714/:730-731`）一致，（c）无「记录在案但实际未发生」的空壳断言
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### C2-PR-5 — 跨 task 控制面方法（emitWatermarkStatus / emitLatencyMarker）静默丢弃（若裁定维持稳态）

- Classification: `watch-only residual`（非数据丢失；控制面遥测 / 空闲检测降级；修复依赖 `HG-01` 人工确认门）——若 Phase 2 裁定派生 Cycle 3 / I1，本条目不适用（显式声明）；若维持稳态，登记为 watch-only + 复触发条件
- Why Not Blocking Closure: ① 影响 = 控制面遥测 / 空闲检测降级，**非用户数据丢失**；② 修复需 RecordWriter 线协议扩展（同 `HG-01` 门），mission Cross-Cutting「结构性重构执行前人工确认」→ 执行门未过；③ RWO 两处已显式注释文档化（非静默丢弃，行为意图可追溯）、BRWO 两处空体无注释（code-style 级观察，已登记）；④ 复触发登记在案（`HG-01` 人工批准即评估）。
- Successor Required: `conditional`（人工批准线协议变更后评估；若 Phase 2 派生则直接并入 Cycle 3 / I1）
- Successor Path: `HG-01` 线协议 plan 或 Cycle 3 / I1

### C2-PR-2 — 扫描器静默跳过形态（若裁定维持稳态）

- Classification: `watch-only residual`（门禁表达扩展候选；当前 0 实例，非 defect）
- Why Not Blocking Closure: ① 当前 main 代码 0 实例（grep 实测：无匿名 Output、无 record implements Output、无 raw OutputTag 声明）；② 显式 fail 五路径已实现 + self-test 覆盖（未识别形态 hard error）；③ false-positive 方向 fail-loud 安全；④ 复触发条件登记在案（main 出现对应形态即触发）。
- Successor Required: `conditional`（若 Phase 2 派生则并入 Cycle 3 / I1）
- Successor Path: Cycle 3 / I1（若派生）

### `HG-01` — 跨 task side-output 线协议结构性变更（人工确认待办，若未批准）

- Classification: `Fix`（已确认契约缺口，执行门 = 人工确认）——**非** watch-only residual；结构性重构，mission 授权边界
- Why Not Blocking Closure: ① 执行门 = 人工确认（mission Cross-Cutting「结构性重构执行前人工确认」），AI 自动执行信封不含此项；② 裁决仍是「必须修」——处置 = 维持登记 + 四重保护性覆盖（in-task fail-fast `88bc0270c` 前置 + 门禁（类级枚举 / call-site 注册表 / 三态分类）+ pin 已移除留痕（`HG-01` 不阻塞 pin 移除）+ backlog 待办条目）；③ 复触发登记在案（人工批准 + 跨 task side-output 需求出现 / CI 门禁红暴露新实例）。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan（跨 task 侧输出线协议设计 + 实现 + E2E）

## Non-Blocking Follow-ups

- Cycle 3 / I1–I6 各 phase 的执行 plan 另行拟制（若本 plan 裁定派生——本 plan 只派生登记，不代执行）。
- C2-RL-3 / C2-PR-4（P3 backlog，措辞修订或 E2E 覆盖扩展）：触发条件 = I4 类别清扫或复探时评估；`cycle2-I6-input.md` 覆盖事实表已落档供后续引用。
- C2-PR-3（重复注册 last-wins 语义，P3 优化）：触发条件 = 多消费者接线需求出现或类别清扫时评估。
- ArchUnit 架构约束门禁（Cycle 1 / I6 延续：`optimization candidate`，引入涉及依赖变更，触发时另立 Decision）。

## Closure

Status Note: 待 I6 执行完成后填写（Cycle 2 收口统计确认 + red list 零悬挂 + C2-PR-2/5 扩展候选裁定 + `HG-01` 处置登记 + 复触发登记 + roadmap I6 行流转；独立 closure audit 完成）。
Completed: 待定

Closure Audit Evidence:

- Reviewer / Agent: 待独立子 agent closure audit 完成后填写
- Evidence: 待填写（Phase Exit Criteria 逐条 PASS、Closure Gates 逐条 PASS、Anti-Hollow 结果、Deferred 分类检查）

Follow-up:

- 依裁定：Cycle 3 / I1（若派生，另立 plan）或 稳态暂停（复触发条件已登记）
- `HG-01`（若人工批准）→ 线协议 Successor plan
- no other remaining plan-owned work
