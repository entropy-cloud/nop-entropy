# Cycle 2 / I5 — 全量验证与门禁零命中（Full Verification And Gate Zero-Hit）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 2 轮独立子 agent 对抗性审查通过（round 1：2 Major（stale 行号 ×2）+ 8 Minor，全部修复；round 2：11/11 修复验证 PASS、无 Blocker/Major、verdict 可转 active，3 个非阻塞 Minor 已顺手修复）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I5（`./mvnw test -pl nop-stream -am -T 1C` + 门禁零命中 + 相关 e2e；full-green 记录 + 下轮输入落档）；I4 修复结果（`2026-08-12-1217-11-...`）；mission `nop-stream-invariant-loop` 验证命令
> Related: 前置 `2026-08-12-1217-11-nop-stream-invariants-cycle2-I4-fix-execution.md`（I4，硬串行依赖）；后续 `2026-08-12-1217-13-nop-stream-invariants-cycle2-I6-closure-and-trigger-determination.md`（I6，以本 plan full-green 记录 + `cycle2-I6-input.md` 为输入）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 2 / I5. 全量验证与门禁零命中

## Purpose

I4 修复落地后，执行 Cycle 2 的全量验证收口：`./mvnw test -pl nop-stream -am -T 1C` 全绿 + 不变式门禁零命中（JUnit 10 个门禁类 0 failure、mjs `all` exit 0、pin 0）+ 相关 e2e 复跑绿，产出 full-green 记录（`ai-dev/logs/`），为 I6 收口提供「修复后基线 + 门禁统计」输入（唯一落点 = `ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`）。本 plan 是纯验证计划：不写修复代码、不新增门禁、不改行为。

## Current Baseline

> 已核对 live repo（2026-08-12）：I4 修复、注册表、pin、E2E 第 4 用例全部实测存在。

- **I4 为硬前置**：本 plan 执行前 `2026-08-12-1217-11-nop-stream-invariants-cycle2-I4-fix-execution.md` 必须已 `completed`（commit `88bc0270c` 落地：RWO/BRWO `collect(OutputTag)` 空体 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`；注册表分类迁移；pin 移除）。若执行时 I4 未 completed，本 plan 不得开始任何 Phase——**立即返回 blocked 状态**（防 I5 在修复未落地时误报全绿，Anti-Hollow 纪律）。
- **I4 产出基线（live 实测）**：原 `StreamTaskInvokable.java:645/:705` 空体处已替换为 fail-fast（现 RWO :649-659 / BRWO :717-727，抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` + `ARG_OUTPUT_TAG` / `ARG_DETAIL`，镜像 `ChainingOutput.java:119` 语义）；`output-contract-registry.json` RWO/BRWO `classification` = `fail-fast`（disposition 注明 commit `88bc0270c`；`HG-01` 关联保留为「线协议支持属增强」）；`mjs-pins.json` pinnedViolations = **0**（2 条过渡 pin 已随 I4 移除，移除留痕在文件头 note）。
- **JUnit 门禁基线（I4 复跑实测）**：10 个门禁类 / 102 tests / 0 failures（含 I4 翻转后的 `TestOutputContractInvariant` 断言与透传目标敏感迁移用例；门禁子集命令 = `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`，**注意 `-pl` 必须用嵌套路径或 `:artifactId` 选择器**，裸模块名从仓库根会报 project does not exist）。
- **e2e 基线（I4 实测）**：`TestSideOutputChainingE2E`（nop-stream-runtime）**4/4 绿**（in-task 3 用例 + I4 新增跨 task fail-fast 第 4 用例 = 1 writer → RWO / 2 writers → BRWO 全路径）；`TestWindowOperatorUnificationE2E`（runtime，6/6 历史基线）/ `TestWindowEndToEnd`（core，7/7 历史基线）为相关 e2e 复跑载体。
- **全量回归基线（I4 实测）**：core 1428 / runtime 805 全量回归绿（行为变更面 = 跨 task 部署下 side-output 从静默 no-op 翻为 fail-fast，无既有测试依赖旧行为）；全量 `-am` 视角 Cycle 1 / I5 实测 2822 tests 全绿（本 plan 以本次实跑为准重新统计）。
- **red list / backlog 状态（I6 输入相关）**：C2-RL-1/2（RWO/BRWO 跨 task no-op）I4 已修复；C2-RL-3（注册表 disposition 措辞过 claim）+ C2-PR-4（E2E 覆盖缺口）= P3 backlog（I3 裁决 §7.2/§10，附依据即合规，不驱动独立修复计划——本 plan 不修复，仅如实记录实际 E2E 覆盖事实供 I6 引用）。
- **真正剩余的 gap**：一次独立的、在 Cycle 2 全部修复落地后的全量验证执行 + full-green 记录 + 门禁零命中确认；I6 输入统计（门禁数 / red list 处置状态 / 新族数 / E2E 覆盖事实）。

## Goals

- `./mvnw test -pl nop-stream -am -T 1C` 一次通过全绿（0 failures / 0 errors，nop-stream 模块组）。
- 不变式门禁零命中：JUnit 门禁 10 个测试类全绿（102 tests，含 I4 翻转/新增用例）；mjs `all` exit 0（unpinned=0、stale=0、pin=0）。
- 相关 e2e 复跑绿（`TestSideOutputChainingE2E` 4/4 + Window E2E）。
- full-green 记录写入 `ai-dev/logs/`；门禁统计（门禁数 / 测试数 / pin 数 / red list 状态 / 新族数）写入 `ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`（**唯一落点**，新建）。
- 为 I6 提供收口输入：red list 零悬挂确认（C2-RL-1/2 修复 + C2-RL-3 P3 已裁决）、新族数（预期 0）、E2E 实际覆盖事实（C2-RL-3/C2-PR-4 背景）。
- roadmap Work Item I5 状态流转（`todo`→`planned`→`done`）。

## Non-Goals

- **不修复任何缺陷**（验证中若发现新失败：记录证据、判定类别——I4 遗漏 / 新族 / 上游无关失败——按纪律移交（I4 补修、I6 派生 Cycle 3、或上游处置）；本 plan 自身不做修复）。
- **不修订 C2-RL-3 注册表措辞 / 不扩展 E2E 覆盖**（P3 backlog，I3 已裁决「不驱动独立修复计划」；本 plan 仅如实记录实际覆盖事实，评估归 I6）。
- **不新增门禁 / 不变式**（属 Cycle 3 / I1，若 I6 裁定派生）。
- **不做 I6 收口裁定**（稳态判定、C2-PR-2/5 扩展候选评估、Cycle 3 派生、`HG-01` 处置均属 I6）。
- **不重跑 I4 的逐项门禁复跑过程**（I4 Phase 3 已执行；本 plan 是全量视角复核）。

## Scope

### In Scope

- 全量测试执行与结果记录（`./mvnw test -pl nop-stream -am -T 1C`）。
- 不变式门禁零命中确认（mjs `all` + JUnit 门禁子集 10 类 / 102 tests）。
- 相关 e2e 复跑与记录（`TestSideOutputChainingE2E` 4/4 + Window E2E）；**E2E 发射点实际覆盖事实核实**（6 发射点逐一标注：有 / 无 E2E 覆盖——供 I6 引用 C2-RL-3/C2-PR-4 处置，不修改注册表）。
- full-green 记录（`ai-dev/logs/`）+ I6 输入统计（**唯一落点 = `ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`**，新建）。
- 验证中发现的新失败：证据记录 + 分类（I4 遗漏 / 新族 / 上游无关失败）+ 移交路径记录；本 plan 保持不关闭直至补修后全绿（不越权修复、不关闭）。

### Out Of Scope

- 任何代码修复（含 I4 遗漏的补修——记证据移交，不本 plan 动手）。
- C2-RL-3 / C2-PR-4 措辞修订或 E2E 覆盖扩展（P3 backlog 处置）。
- 新门禁 / 新不变式建设（Cycle 3 / I1）。
- I6 的稳态判定 / C2-PR-2/5 评估 / Cycle 3 派生 / `HG-01` 处置登记。

## Execution Plan

### Phase 1 - 全量验证执行与记录

Status: completed
Targets: `nop-stream/` 全模块；`ai-dev/logs/`；`ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`（唯一落点，新建）

- Item Types: `Proof`

- [x] **前置检查**：确认 I4（`2026-08-12-1217-11-...`）Plan Status: completed（含 `88bc0270c` 修复 + 注册表迁移 + pin 移除留痕）；若未完成，本 Phase 标 `blocked` 并返回（不开始任何验证执行；Plan Status 保持 active，不得关闭）
- [x] 跑 `./mvnw test -pl nop-stream -am -T 1C`，记录结果（**统计口径 = nop-stream 模块组内 surefire 汇总**：reactor 输出中 `nop-stream*` 模块的 tests/failures/errors 数，不混入 `-am` 连带的上游模块；0 failures / 0 errors）
- [x] 跑 `node ai-dev/tools/check-nop-stream-invariants.mjs all`，确认 exit 0（全绿模式下无 stdout 输出属正常——以「exit 0 且无 violation 输出」判绿；如需要逐命令输出可分别运行 inventory / sync / scan-iterations / scan-output-contract / self-test 子命令）；确认 `mjs-pins.json` pinnedViolations = 0（无 unpinned、无 stale）
- [x] 跑 JUnit 门禁子集 `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`，确认 0 failure（**门禁类 10 个**：五族 5 + 3 表完备性 + `TestOutputContractInvariant`（I1 新增）+ `TestWindowOperatorMergingCleanupInvariant`（RL-6）；含 I4 翻转/新增用例）。**计数处置**：预期 102 tests——若实际计数与 102 不符，即作为 anomaly 记录（差异数值 + 涉及类）并回查 I4 门禁基线 / 测试类变更，不得硬凑 102，不得以「0 failure」为由忽略计数差异；查明原因后如实记录于 `cycle2-I6-input.md` 与 `ai-dev/logs/`。**依赖前提**：带 `-Dtest` 的显式命令不带 `-am`（`-am` + 过滤会在无匹配测试模块触发 failIfNoSpecifiedTests），依赖本地仓库已有 nop-stream SNAPSHOT；遇依赖不可解析先跑 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装
- [x] 复跑相关 e2e 并记录结果：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestSideOutputChainingE2E`（**4/4，含跨 task fail-fast 第 4 用例**）+ `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestWindowOperatorUnificationE2E` + `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestWindowEndToEnd`（后二者亦含于全量 `-am` 运行，本项为显式复跑记录）
- [x] **E2E 发射点覆盖事实核实（C2-RL-3/C2-PR-4 背景，Proof 不修复）**：对照 `TestSideOutputChainingE2E` 源码与注册表 6 发射点（`ProcessOperator.java:111/:134`、`WindowOperator.java:1030/:1860`、`CepOperator.java:483/:777`），逐一记录实际 E2E 覆盖状态（预期：仅 `WindowOperator.java:1030` 有 E2E 覆盖，其余 5 个发射点无；I4 第 4 用例覆盖跨 task fail-fast 出口路径而非新发射点）；**不修改注册表措辞**——结论写入 `cycle2-I6-input.md` 供 I6 引用（P3 backlog 处置不变）
- [x] 新失败处置（如有）：**本 plan 不修复、不关闭**——记录证据 + 分类（I4 遗漏 / 新族 / 上游无关失败）+ 移交路径（**I4 遗漏类的补修载体 = 经 I3 裁决流程重开 I4 或新立 mini-plan，不得本 plan 代修**；新族 = I6 派生 Cycle 3；上游无关失败 = 上游处置）；本 plan Phase 1 标 `blocked`（Plan Status 保持 active 不可关闭），待补修落地后**重跑本 Phase 全量验证**全绿才可关闭（confirmed live defect 不得以「已分类移交」形式留在 Non-Blocking 区；**上游模块失败**与 I4 修复无关时记录证据 + 移交上游处置，不算 nop-stream 组新失败，但全绿门禁仍不满足、本 plan 仍保持不关闭直至 reactor 全绿）
- [x] full-green 记录写入 `ai-dev/logs/` 对应日期条目（测试统计 + 门禁统计 + e2e 结果 + 新失败处置 + E2E 覆盖事实）
- [x] **I6 输入统计落档（唯一落点）**：新建 `ai-dev/audits/nop-stream-invariants/cycle2-I6-input.md`，内容 = 门禁总数（10 个门禁类，各测试类计数以 surefire 实际输出为准）/ JUnit 测试总数（nop-stream 模块组）/ pin 数（预期 0）/ red list 状态（C2-RL-1/2 修复确认 + C2-RL-3 P3 已裁决零悬挂）/ 新族数（预期 0）/ e2e 结果摘要 / E2E 发射点覆盖事实表——供 I6 收口直接引用
- [x] **roadmap 行流转**：roadmap Work Item I5 行状态 `todo`→`planned`（本 plan 激活时）→`done`（closure audit 通过后，不得提前）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（nop-stream 模块组 0 failures / 0 errors），结果已记录
- [x] mjs `all` exit 0（无 unpinned 违规、无 stale pin、pin=0），结果已记录
- [x] JUnit 门禁子集 10 类 / 102 tests 0 failure，结果已记录
- [x] 相关 e2e 复跑绿（`TestSideOutputChainingE2E` 4/4 + `TestWindowOperatorUnificationE2E` + `TestWindowEndToEnd` 显式复跑记录），结果已记录
- [x] E2E 发射点覆盖事实表在案（6 发射点逐一标注，供 I6 引用；注册表措辞未改——P3 backlog 处置不变）
- [x] 新失败处置在案：**零新失败**（正常关闭路径）；若存在新失败，本 Phase 保持 `blocked`（Plan Status 保持 active 不可关闭，不因移交而关闭）
- [x] full-green 记录已写入 `ai-dev/logs/`；`cycle2-I6-input.md` 已落档（唯一落点，含计数口径 + 覆盖事实表）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 本 Phase 为纯验证，无代码变更 → `No owner-doc update required`（`docs-for-ai/` 不涉及；`ai-dev/` 记录归本 plan）

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] I4 已完成（硬前置）；本 plan 未在 I4 未完成时误执行验证
- [x] 全量测试全绿（`./mvnw test -pl nop-stream -am -T 1C`，mission 验证命令）
- [x] 门禁零命中（mjs `all` exit 0 + JUnit 门禁子集 10 类 0 failure）
- [x] 相关 e2e 绿（含 `TestSideOutputChainingE2E` 4/4）
- [x] full-green 记录存在（`ai-dev/logs/`），`cycle2-I6-input.md` 落档（门禁数 / red list 状态 / 新族数 / E2E 覆盖事实，含计数口径）
- [x] 无静默跳过：验证结果如实记录，失败未被吞掉或忽略（Rule #24）；新失败不得伪装成「已分类移交」关闭
- [x] 无 in-scope live defect / contract drift 被静默降级（C2-RL-1/2 已修复在案；C2-RL-3/C2-PR-4 = P3 backlog 已裁决附依据，非本 plan 处置项）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 本 plan 为纯验证 / 文档计划（无代码变更）：`scan-hollow-implementations.mjs` 与 checkstyle 不适用，已按 guide「纯文档计划」删除

## Deferred But Adjudicated

无 in-scope deferred 项：验证中发现的新失败不属 deferral——已确认 live defect 必须保持 Phase 1 `blocked`（Plan Status 保持 active）并移交补修（I4 补修 / I6 派生 Cycle 3 / 上游处置），不得降级为「后续再说」，也不得以「已分类移交」替代全绿门禁关闭。

### C2-RL-3 + C2-PR-4（注册表 disposition 措辞过 claim + E2E 覆盖缺口，P3）

- Classification: `optimization candidate`（I3 裁决 §7.2/§10 = P3 backlog，附依据即合规）
- Why Not Blocking Closure: 措辞过 claim 不改变门禁行为（mjs 不消费 disposition 字段，V4 注册表判定不受影响），无运行时正确性影响；I3 已显式裁定「不驱动独立修复计划」；本 plan 仅如实记录实际覆盖事实（Proof），处置归 I6 引用。
- Successor Required: `no`（backlog 已登记，触发条件 = I4 类别清扫或复探时评估——本 plan E2E 覆盖事实核实即评估动作之一，结论移交 I6）

## Non-Blocking Follow-ups

- 验证中发现的新失败（I4 遗漏 / 新族 / 上游无关失败）的处置路径 = Phase 1「新失败处置」规则（记录证据 + 分类 + 移交；本 plan 不关闭直至补修后全绿）。**本清单不承载任何已确认 live defect**。
- C2-RL-3 / C2-PR-4（P3 backlog）的措辞修订或 E2E 扩展：I3 已裁决不驱动独立修复计划，本 plan 仅记录事实；若 I6 判定需要处置，由 I6 或后继计划承接。
- e2e 之外的边缘场景扩展验证（如极端并发压力）：由后续周期视需要覆盖，不阻塞本 plan 关闭。

## Closure

Status Note: 纯验证计划完成——I4 硬前置（`1217-11` completed，`88bc0270c` 落地）满足后执行全量验证：`./mvnw test -pl nop-stream -am -T 1C` nop-stream 模块组 **2833 tests / 0 failures / 0 errors / 10 skipped** 全绿；mjs `all` exit 0（pin = 0，无 unpinned / 无 stale）；JUnit 门禁子集 **10 类 / 102 tests / 0 failures**（与 I4 后基线精确一致）；相关 e2e 复跑绿（`TestSideOutputChainingE2E` 4/4 含跨 task fail-fast 第 4 用例 + `TestWindowOperatorUnificationE2E` 6/6 + `TestWindowEndToEnd` 7/7）；零新失败（无 I4 遗漏 / 新族 / 上游无关失败）；E2E 发射点覆盖事实核实在案（仅 `WindowOperator.java:1030` 有 E2E 覆盖，其余 5 发射点无——P3 backlog 处置不变，注册表措辞未改）；full-green 记录写入 `ai-dev/logs/2026/08-12.md`；`cycle2-I6-input.md` 新建落档（唯一落点，含计数口径 + 覆盖事实表）；roadmap I5 行 `todo`→`planned`→`done`。独立 fresh-session closure audit 通过（verdict: ready for completed）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，audit-only fresh session `ses_0097998bfffebu39LYN7QUxH0G`）
- Evidence:
  - Exit Criteria 逐条 PASS（live 证据）：① 全量 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（03:32 min，2026-08-12 23:07）——surefire 独立重算 448 份 TEST-*.xml 汇总 = 2833 tests / 0 failures / 0 errors / 10 skipped（core 1428 / runtime 805 / cep 320 / rocksdb 83 / connector 35 / connector-jdbc 32 / connector-batch 35 / connector-debezium 19 / flow 51 / fraud-example 25）；② mjs `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0 + `mjs-pins.json` pinnedViolations = []；③ JUnit 门禁子集 `-Dtest='Test*Invariant*'` BUILD SUCCESS——10 门禁类 102 tests / 0 failures / 0 errors / 0 skipped（surefire 逐类 8/12/10/10 + 9/10/4/11 + 21/7）；④ e2e 显式复跑 `TestSideOutputChainingE2E` 4/4 + `TestWindowOperatorUnificationE2E` 6/6 + `TestWindowEndToEnd` 7/7（surefire 0 failures）；⑤ E2E 发射点覆盖事实表（6 发射点 live 行号核对在案：ProcessOperator.java:111/:134、WindowOperator.java:1030/:1860、CepOperator.java:483/:777；`TestSideOutputChainingE2E` 仅实例化 TestableWindowOperator（extends WindowOperator）+ ChainingOutput/StreamTaskInvokable/RecordWriter/StreamSinkOperator，无 ProcessOperator/CepOperator → 仅 WindowOperator.java:1030 late-data 路径有 E2E 覆盖，其余 5 发射点无；注册表措辞未改）；⑥ 零新失败（全量 + 门禁 + e2e 全绿，无失败被吞掉——全部 TEST-*.xml failures=0/errors=0 复核）；⑦ full-green 记录 = `ai-dev/logs/2026/08-12.md` 顶部条目（测试统计 + 门禁统计 + e2e + 覆盖事实）；`cycle2-I6-input.md` 落档（唯一落点，门禁 10 类 / 102 tests / 全量 2833 / pin 0 / red list 零悬挂 / 新族 0 / e2e 摘要 / 发射点覆盖事实表，计数口径在案）；⑧ 日志条目已更新；⑨ 纯验证无代码变更 → `No owner-doc update required`（git status 确认 nop-stream/ 零改动，last nop-stream commit 仍为 `88bc0270c`）。
  - Closure Gates 逐条 PASS：I4 硬前置 completed（`1217-11` `> Plan Status: completed` + `88bc0270c` 在 git log + 注册表 fail-fast 分类 + pin 移除留痕）；全量全绿（2833/0）；门禁零命中（mjs exit 0 + JUnit 102/0）；e2e 绿（4/4 含跨 task fail-fast 第 4 用例）；full-green 记录 + `cycle2-I6-input.md` 落档（含计数口径）；无静默跳过（Rule #24——零新失败，无失败吞掉/忽略）；无 in-scope live defect / contract drift 静默降级（C2-RL-1/2 修复在案；C2-RL-3/C2-PR-4 = P3 backlog 已裁决附依据，本 plan 仅记录覆盖事实不处置）；独立子 agent closure-audit 通过（本段证据，fresh session `ses_0097998bfffebu39LYN7QUxH0G`，verdict: ready for completed，0 Blocker / 0 Major / 0 Minor）；`node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0（Passed: 1，closure 后 final 复跑）；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0（3 条 BROKEN_LINK 为 credential plan 既有基线）；scan-hollow-implementations.mjs / checkstyle 不适用（纯文档/验证计划，已按 guide「纯文档计划」删除）。
  - Anti-Hollow 检查：全量 448 份 surefire 报告零 failures/errors 独立重算 + 门禁 102 逐类计数与落档一致 + e2e 三份报告 0 failures + `TestCepSkipStrategyE2E` 仅 import OutputTag（匿名 collect 为 mock 非发射点）——无空壳/静默跳过/no-op 作为正常实现，验证结果全部真实运行记录，无虚构数字。
  - Deferred 分类检查：Deferred But Adjudicated 仅 C2-RL-3 + C2-PR-4（optimization candidate，P3 backlog 已裁决附依据）；Non-Blocking Follow-ups 不承载任何已确认 live defect（零新失败）；无 in-scope defect 被降级。

Follow-up:

- I6（plan `2026-08-12-1217-13-...`）为直接 successor：收口统计确认（输入 = `cycle2-I6-input.md` 唯一落点）+ C2-PR-2/5 扩展候选评估 + 稳态判定 + `HG-01` 处置登记 + 复触发登记
- no other remaining plan-owned work
