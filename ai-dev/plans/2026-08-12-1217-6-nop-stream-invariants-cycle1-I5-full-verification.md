# Cycle 1 / I5 — 全量验证与门禁零命中（Full Verification And Gate Zero-Hit）

> Plan Status: active
> Last Reviewed: 2026-08-12
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：1 Blocker（`-pl` 裸模块路径）+ 2 Major（「新失败不阻塞关闭」自相矛盾 / I6 输入落点歧义）+ 5 Minor；round 2：8/8 修复验证 PASS、无 Blocker/Major、verdict 可转 active；round 3：F1-F8 修复验证 PASS、无新问题、verdict approved）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I5；I4 修复结果（`2026-08-12-1217-5-...`）；mission `nop-stream-invariant-loop` 验证命令
> Related: 前置 `2026-08-12-1217-5-nop-stream-invariants-cycle1-I4-fix-execution.md`（I4，硬串行依赖）；后续 I6 收口（以本 plan full-green 记录 + 门禁统计为输入）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I5. 全量验证与门禁零命中

## Purpose

I4 修复落地后，执行本 Cycle 的全量验证收口：`./mvnw test -pl nop-stream -am -T 1C` 全绿 + 不变式门禁零命中（JUnit 五族 + 3 表完备性 0 failure、mjs `all` exit 0）+ 相关 e2e 复跑绿，产出 full-green 记录（`ai-dev/logs/`），为 I6 收口提供「修复后基线 + 门禁统计」输入。本 plan 是纯验证计划：不写修复代码、不新增门禁、不改行为。

## Current Baseline

- **I4 为硬前置**：本 plan 执行前 `2026-08-12-1217-5-nop-stream-invariants-cycle1-I4-fix-execution.md` 必须已 `completed`（7 条 RL + RL-3 已修复、pin 注册表已同步、门禁已复跑绿）。若执行时 I4 未 completed，本 plan 不得开始任何 Phase——**立即返回 blocked 状态**（防 I5 在修复未落地时误报全绿，Anti-Hollow 纪律）。
- **I4 产出基线（预期）**：RL-1..7（含触发闭合的 RL-3）修复落地；`mjs-pins.json` 无 stale pin；JUnit pin 断言全部为「已修复」方向；red-list.md 全部 RL 标注修复证据；gate-inventory.json 已更新。
- **验证命令（mission 配置）**：`./mvnw test -pl nop-stream -am -T 1C`（test）；`node ai-dev/tools/check-nop-stream-invariants.mjs all`（门禁）；JUnit 门禁子集 `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`（**注意：nop-stream 子模块嵌套于 `nop-stream/` 聚合 pom，`-pl` 必须用嵌套路径或 `:artifactId` 选择器；`-pl nop-stream-core` 从仓库根会报 project does not exist（已实测）**）。
- **e2e 载体（live 确认存在）**：`TestWindowOperatorUnificationE2E`（nop-stream-runtime）、`TestWindowEndToEnd`（nop-stream-core）；cluster 测试（`TestClusterRegistryConsistencyInvariant` 已含于门禁子集；`TestJdbcClusterRegistry` / `TestInMemoryClusterRegistry` / `TestNodeDiscoveryConsistencyChecker` 等含于全量运行）。
- **真正剩余的 gap**：一次独立的、在全部修复落地后的全量验证执行 + full-green 记录 + 门禁零命中确认；I6 输入统计（门禁数 / red list 处置状态 / 新族数）。

## Goals

- `./mvnw test -pl nop-stream -am -T 1C` 一次通过全绿（0 failures / 0 errors，nop-stream 模块组）。
- 不变式门禁零命中：JUnit 门禁 9 个测试类（五族 5 + 3 表完备性 + I4 新增 `TestWindowOperatorMergingCleanupInvariant`）全绿；mjs `all` exit 0（unpinned=0、stale=0）。
- 相关 e2e 复跑绿（Window E2E 等）。
- full-green 记录写入 `ai-dev/logs/`；门禁统计（门禁数 / 测试数 / pin 数）写入 `ai-dev/audits/nop-stream-invariants/cycle1-I6-input.md`（唯一落点）。
- 为 I6 提供收口输入：red list 7 条 RL 修复状态确认（零悬挂）、新族数（PD-15 在案）。
- roadmap Work Item I5 状态流转（`todo`→`planned`→`done`）。

## Non-Goals

- **不修复任何缺陷**（验证中若发现新失败：记录证据、判定类别——I4 遗漏 / 新族 / 上游无关失败——按纪律移交（I4 补修、I6 派生 Cycle 2、或上游处置）；本 plan 自身不做修复）。
- **不新增门禁 / 不变式**（属 Cycle 2 / I1）。
- **不做 I6 收口裁定**（稳态判定、Cycle 2 正式追加、复触发登记属 I6）。
- **不重跑 I4 的逐族聚焦门禁过程**（I4 Phase 5 已执行；本 plan 是全量视角复核）。

## Scope

### In Scope

- 全量测试执行与结果记录（`./mvnw test -pl nop-stream -am -T 1C`）。
- 不变式门禁零命中确认（mjs `all` + JUnit 门禁子集）。
- 相关 e2e 复跑与记录。
- full-green 记录（`ai-dev/logs/`）+ I6 输入统计（**唯一落点 = `ai-dev/audits/nop-stream-invariants/cycle1-I6-input.md`**；`gate-inventory.json` 仅作门禁过程记录，不承担 I6 统计落点）。
- 验证中发现的新失败：证据记录 + 分类（I4 遗漏 / 新族 / 上游无关失败）+ 移交路径记录；本 plan 保持不关闭直至补修后全绿（不越权修复、不关闭）。

### Out Of Scope

- 任何代码修复（含 I4 遗漏的补修——记证据移交，不本 plan 动手）。
- 新门禁 / 新不变式建设。
- I6 的稳态判定 / Cycle 2 正式追加 / 复触发登记。

## Execution Plan

### Phase 1 - 全量验证执行与记录

Status: planned
Targets: `nop-stream/` 全模块；`ai-dev/logs/`；`ai-dev/audits/nop-stream-invariants/cycle1-I6-input.md`（唯一落点，新建）

- Item Types: `Proof`

- [ ] **前置检查**：确认 I4（`2026-08-12-1217-5-...`）Plan Status: completed；若未完成，本 Phase 标 `blocked` 并返回（不开始任何验证执行；Plan Status 保持 active，不得关闭）
- [ ] 跑 `./mvnw test -pl nop-stream -am -T 1C`，记录结果（**统计口径 = nop-stream 模块组内 surefire 汇总**：reactor 输出中 `nop-stream*` 模块的 tests/failures/errors 数，不混入 `-am` 连带的上游模块（nop-kernel 等）；0 failures / 0 errors）
- [ ] 跑 `node ai-dev/tools/check-nop-stream-invariants.mjs all`，确认 exit 0（**全绿模式下无 stdout 输出属正常**——`all` 分支不打印逐命令 OK；unpinned/stale 计数仅非零时打印；如需要逐命令输出可分别运行 inventory / sync / scan-iterations / self-test 四子命令；以「exit 0 且无 violation 输出」判绿）
- [ ] 跑 JUnit 门禁子集 `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`，确认 0 failure（**门禁类 9 个**：五族 5 + 3 表完备性 + I4 新增 `TestWindowOperatorMergingCleanupInvariant`；含 I4 翻转/新增用例）。**依赖前提**：带 `-Dtest` 的显式命令不带 `-am`（`-am` + 过滤会在无匹配测试模块触发 failIfNoSpecifiedTests），依赖本地仓库已有 nop-stream SNAPSHOT（历史 `mvn install` 提供）；遇依赖不可解析先跑 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装
- [ ] 复跑相关 e2e 并记录结果：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestWindowOperatorUnificationE2E` + `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestWindowEndToEnd`（二者亦已含于全量 `-am` 运行，本项为显式复跑记录）
- [ ] 新失败处置（如有）：**本 plan 不修复、不关闭**——记录证据 + 分类（I4 遗漏 / 新族 / 上游无关失败）+ 移交路径；本 plan Phase 1 标 `blocked`（Plan Status 保持 active 不可关闭），待补修落地后**重跑本 Phase 全量验证**全绿才可关闭（confirmed live defect 不得以「已分类移交」形式留在 Non-Blocking 区；**上游模块失败**（如 nop-kernel）与 I4 修复无关时记录证据 + 移交上游处置，不算 nop-stream 组新失败，但全绿门禁仍不满足、本 plan 仍保持不关闭直至 reactor 全绿）
- [ ] full-green 记录写入 `ai-dev/logs/` 对应日期条目（测试统计 + 门禁统计 + e2e 结果 + 新失败处置）
- [ ] **I6 输入统计落档（唯一落点）**：新建 `ai-dev/audits/nop-stream-invariants/cycle1-I6-input.md`，内容 = 门禁总数（9 个门禁类，各测试类计数以 surefire 实际输出为准）/ JUnit 测试总数（nop-stream 模块组）/ pin 数（预期 0）/ red list 状态（7 条 RL 修复确认 + RL-3 闭合）/ 新族数（PD-15 在案 = 1）/ e2e 结果摘要——供 I6 收口直接引用

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw test -pl nop-stream -am -T 1C` 全绿（nop-stream 模块组 0 failures / 0 errors），结果已记录
- [ ] mjs `all` exit 0（无 unpinned 违规、无 stale pin），结果已记录
- [ ] JUnit 门禁子集 0 failure，结果已记录
- [ ] 相关 e2e 复跑绿（`TestWindowOperatorUnificationE2E` / `TestWindowEndToEnd` 显式复跑记录），结果已记录
- [ ] 新失败处置在案：**零新失败**（正常关闭路径）；若存在新失败，本 Phase 保持 `blocked`（Plan Status 保持 active 不可关闭，不因移交而关闭）——已确认 live defect 必须移交补修后重跑全绿，不得以「已分类移交」替代全绿门禁
- [ ] full-green 记录已写入 `ai-dev/logs/`；`cycle1-I6-input.md` 已落档（唯一落点，含计数口径）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] I4 已完成（硬前置）；本 plan 未在 I4 未完成时误执行验证
- [ ] 全量测试全绿（`./mvnw test -pl nop-stream -am -T 1C`，mission 验证命令）
- [ ] 门禁零命中（mjs `all` exit 0 + JUnit 门禁子集 0 failure）
- [ ] 相关 e2e 绿
- [ ] full-green 记录存在（`ai-dev/logs/`），`cycle1-I6-input.md` 落档（门禁数 / red list 状态 / 新族数，含计数口径）
- [ ] 无静默跳过：验证结果如实记录，失败未被吞掉或忽略（Rule #24）；新失败不得伪装成「已分类移交」关闭（Phase 1 标 blocked、Plan Status 保持 active 不可关闭，移交补修后重跑全绿）
- [ ] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 本 plan 为纯验证 / 文档计划（无代码变更）：`scan-hollow-implementations.mjs` 与 checkstyle 不适用，已按 guide「纯文档计划」删除

## Deferred But Adjudicated

无 in-scope deferred 项：验证中发现的新失败不属 deferral——已确认 live defect 必须保持 Phase 1 `blocked`（Plan Status 保持 active）并移交补修（I4 补修 / I6 派生 Cycle 2 / 上游处置），不得降级为「后续再说」，也不得以「已分类移交」替代全绿门禁关闭；本 plan 的 Non-Goals 声明了「不修复」，移交路径即处置，非延期。

## Non-Blocking Follow-ups

- 验证中发现的新失败（I4 遗漏 / 新族 / 上游无关失败）的处置路径 = Phase 1「新失败处置」规则（记录证据 + 分类 + 移交；本 plan 不关闭直至补修后全绿）。**本清单不承载任何已确认 live defect**——confirmed live defect 的归属在 Phase 1 处置规则中定义，此处仅作指针说明。
- e2e 之外的边缘场景扩展验证（如极端并发压力）：由后续周期视需要覆盖，不阻塞本 plan 关闭。

## Closure

Status Note: <<closure audit 完成后填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent，closure audit 后填写>>
- Evidence: <<closure audit 后填写：每条 Exit Criterion / Closure Gate 的 PASS/FAIL + 验证命令输出引用；check-plan-checklist 退出码；无静默跳过核对>>

Follow-up:

- <<closure 后填写：只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
