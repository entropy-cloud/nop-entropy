# Cycle 1 / I2 — 不变式驱动审计（Invariant-Driven Audit）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：1 Major（门禁转红无处置路径）+ 9 Minor，全部修复；round 2：10/10 修复到位，遗留 1 Major（scan-hollow 判据不可达）+ 6 Minor，修复；round 3：7/7 验证 PASS、verdict approved，仅 1 条非阻塞措辞建议已顺手修正）
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I2；前置 I1 产出 `ai-dev/audits/nop-stream-invariants/{invariant-catalog.md,red-list.md,gate-inventory.json,mjs-pins.json}`；`ai-dev/skills/open-ended-adversarial-review-prompt.md`
> Related: 前置 `2026-08-12-1217-2-nop-stream-invariants-cycle1-I1-first-gates.md`（I1，硬串行）；后续 `2026-08-12-1217-4-nop-stream-invariants-cycle1-I3-adjudication.md`（I3 裁决，依赖本 plan 的权威 red list）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 1 / I2. 不变式驱动审计

## Purpose

把 I1 沉淀的首批门禁跑向五族全部变更型方法，产出**确定性 red list**；对 I0 登记的 3 个 watch-only residual 做**动态验证**并逐项裁定状态；对门禁未表达的盲区做**聚焦对抗探查**（新交错组合、refactor 引入新方法、跨 Operator 参数遗漏），并评估 4 个非族候选是否升格；**每条发现标注已知族（F1-F5）或新族**。产出权威版 `red-list.md` 作为 I3 裁决的唯一输入。本 plan 只审计（含 watch-only 状态裁定），不修复、不做 P0-P3 严重度裁决（严重度与派发属 I3）。

## Current Baseline

> 已核对 live repo（2026-08-12）：门禁工具、测试类、red-list、CI 配置全部实测存在。

- **I1 已完成（前置硬串行）**：五族门禁 + 表完备性 + mjs 扫描器 + CI 接入全部落地且绿（`2026-08-12-1217-2-...` completed；closure audit 记录于 `ai-dev/logs/2026/08-12.md`）。
- **门禁现状（live 实测）**：`node ai-dev/tools/check-nop-stream-invariants.mjs all` 退出码 0（inventory / sync / scan-iterations / self-test 全绿；scan-iterations 对 nop-stream-core/cep/runtime 的 src/main 扫描恰好 1 个违规 = `TwoPhaseCommitSinkFunction.java:83` saveState 无锁 copy，被 `mjs-pins.json` 唯一 pin 吸收：unpinned=0、stale=0）。JUnit 五族门禁（`TestWindowRoundTripInvariant` 9 / `TestSynchronizedCollectionInvariant` 10 / `TestCheckpointIDCounterInvariant` 8 / `TestCepReleaseSymmetryInvariant` 21 / `TestClusterRegistryConsistencyInvariant` 8）+ 3 模块表完备性测试（core 10 / runtime 11 / cep 7）全绿。CI `.github/workflows/maven.yml:44-49` 已含 node step 运行 mjs 扫描器。
- **red-list.md（I1 pin-and-record 版）存在**：4 条已知 residual——AR-1（`TwoPhaseCommitSinkFunction.java:83` saveState 无锁 copy）、AR-11（`TwoPhaseCommitSinkFunction.java:76-78` setPendingCommits 接受任意 Map）、AR-9（`JdbcClusterRegistry.java:112-115` registerNode 写 lease_expire_at=0L + `:172-174` getActiveNodes 按 `> now` 过滤）、AR-18（`InMemoryClusterRegistry.java:68-81` renewLease 忽略 leaseTimeoutMs）；+ 3 条 watch-only——AR-15（`LocalFileCheckpointStorage.java:116-138` 按文件名 ID 排序）、R15-AR-8（`WindowOperator.java:773-783` onEventTime cleanup 分支未见 retireWindow）、R15-AR-9（`InputGate.java:625-674` per-id inFlightAlignments，maxConcurrentCheckpoints>1 语义待验证）。每条已登记 I2 处理路径。
- **门禁盲区（已知）**：门禁只覆盖已沉淀的 5 条不变式；门禁外仍可能存在——新交错组合（并发 / barrier / timer / restore 交错未表达）、refactor 引入新方法的行为盲区（表完备性只保证"新方法入表"，不保证"语义正确"）、8 参数元组之外的跨 Operator 参数/配置传递链、非五族但同族复发模式（I0 catalog §6 登记 4 个候选：R13-AR-9 ResultPartition.close 死锁类、R16-AR-19/AR-20 BatchConsumerSinkFunction buffer 增长与序列化、R8-AR-59 SimpleStreamOperatorFactory 共享模板、R16-AR-13 WatermarkOutputMultiplexer 停滞）。
- **探查工具与证据在册**：`ai-dev/skills/open-ended-adversarial-review-prompt.md`；各轮审计证据 `ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r{8,9,16}` 与 `2026-05-30-adversarial-review-nop-stream-r{10,11,12,13}`（目录日期前缀按此分布，R16 头部含 R15-AR-1..5 修复确认表，作门禁命中对照来源；证据索引以 invariant-catalog §0 为准）；I0 catalog §1 含 74 条 finding-ID → 失败族映射（F1=20 / F2=9 / F3=25 / F4=18 / F5=2）。
- **范围边界**：本 plan 与 `nop-stream-production` / `nop-stream-independent-audit` / `nop-stream-flink-comparison` roadmap 范围独立（roadmap「范围独立」条款）——探查为聚焦式，非全仓漫游式深度审计。

## Goals

- 跨五族全部变更型方法跑门禁（mjs + JUnit），生成**确定性 red list**：unpinned 违规入 red list、stale pin 提示更新、4 条已知 residual 行号与 pin 状态复核。
- 3 个 watch-only residual 动态验证并逐项裁定（verified / watch-only 保留 / 升格 red list），以 committed focused test 为主载体。
- 聚焦对抗探查门禁盲区（新交错组合、refactor 引入新方法、跨 Operator 参数遗漏）+ 4 个非族候选升格评估。
- 每条发现标注族归属：已知族（F1-F5 兄弟实例，可追溯历史 finding-ID）或新族（含不变式陈述候选 + 触发证据，供 I6 按 Loop Rule 派生 Cycle 2 / I1）。
- 产出权威版 `red-list.md`（合并门禁结果 + watch-only 裁定 + 探查发现），零悬挂移交 I3。

## Non-Goals

- **不修复任何 red list 项**（属 I4，经 I3 裁决后执行）。
- **不做 P0/P1/P2/P3 严重度裁决与派发**（属 I3）。
- **不新增门禁**（新族门禁属 Cycle 2 / I1；本 plan 只标注新族，不写门禁）。
- **不派生 Cycle 2 work item / 不改 roadmap work item 表**（派生属 I6 收口，按 Loop Rule 预授权执行）。
- **不做全仓漫游式深度审计**（范围 = 五族变更型方法 + 登记盲区 + 4 个非族候选；与 `nop-stream-independent-audit-roadmap.md` 不重叠）。
- **不改被测类代码**（即使发现缺陷，也只入 red list / 探查报告，不移改）。

## Scope

### In Scope

- 门禁全量运行与确定性 red list 生成（mjs all + 五族 JUnit + 表完备性 + pin 复核 + residual 行号复核）。
- 3 个 watch-only residual 的动态验证（focused test 或代码路径追踪 + 结论裁定）。
- 聚焦对抗探查（盲区清单 + 4 非族候选评估 + R16 修复确认表对照）。
- 发现族标注（已知族兄弟实例 / 新族）与 red-list.md 权威化。
- `ai-dev/logs/` 更新。

### Out Of Scope

- 修复（I4）、裁决（I3）、Cycle 2 派生（I6）、新门禁（Cycle 2 / I1）。
- ArchUnit 引入（I0/I1 已裁定 optimization candidate，未触发）。
- 任何公共 API / 被测类代码变更。

## Execution Plan

### Phase 1 - 门禁全量运行与确定性 red list 生成

Status: completed
Targets: `ai-dev/tools/check-nop-stream-invariants.mjs`；`ai-dev/audits/nop-stream-invariants/{mjs-pins.json,gate-inventory.json,red-list.md}`；五族 JUnit 门禁测试类

- Item Types: `Proof | Decision`
- [x] 运行 `node ai-dev/tools/check-nop-stream-invariants.mjs all`（inventory / sync / scan-iterations / self-test），逐命令记录输出；**处置二分**：a) 新增 **pre-existing residual**（此前未 pin 的已知行为，无行为漂移）→ 入 red list + 追加 pin 记录（I1 pin AR-1 同款 pin-and-record 语义，违规串可直接从扫描输出复制）→ mjs 恢复 exit 0；b) **行为漂移**（pin 描述与 live 行为不符，或出现门禁红）→ 入 red list + 显式升级标记（意味着被测代码在 I1 后被改动，超出 I2 处置权，plan 转 blocked 移交升级）——不允许通过静默忽略恢复绿色
- [x] **stale pin 二分（与行为漂移的边界）**：a) 违规消失 = residual 已真实解决 → 移除 pin + red-list 对应条目标记 verified（mjs-pins.json note 语义："the residual was fixed — remove the pin"）；b) pin key 行号漂移但违规仍在（scanner 报新行号的 unpinned 违规）→ 重 pin 新行号 + 留痕，red list 条目保留——属 pre-existing residual 移动，非行为漂移，不升级 blocked
- [x] 运行五族 JUnit 门禁 + 3 模块表完备性测试（`./mvnw test -pl nop-stream-core,nop-stream-runtime,nop-stream-cep -Dtest='Test*Invariant*'`），记录测试数 / 失败数；任何红项 → 同处置二分（确认是 pin 断言与实际行为不符 → 行为漂移升级路径；新增未覆盖行为 → 入 red list 并说明；**如需恢复 JUnit 绿：按 I1 先例把新行为显式 pin 进断言（如 `testRegisterNodeVisibilityIsPinnedPerImpl` 同款 pin 语义）并在 red list 记录——不允许改断言掩盖漂移**）。（执行前确认本地仓库已 install 上游依赖：`-pl` 不带 `-am` 是为避免 `-Dtest` 误作用于上游模块；若本地仓库缺依赖导致命令失败，回退到 Closure Gate 的全量 `./mvnw test -pl nop-stream -am -T 1C`）
- [x] 复核 4 条已知 residual 的 live 位置与 pin 状态（`TwoPhaseCommitSinkFunction.java:76-78/:83`、`JdbcClusterRegistry.java:112-115/:172-174`、`InMemoryClusterRegistry.java:68-81`）：行号是否漂移（±3 行内视为无漂移）、pin 描述是否仍精确、行为是否仍匹配 I1 的 pin 断言
- [x] 合并生成确定性 red list 初稿（写入 red-list.md：每条含 `文件:行` + 关联不变式 + 关联 finding + 门禁/pin 来源）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] mjs `all` 退出码 0 且四命令输出记录在案；unpinned / stale 情况全部显式处置（新增 pre-existing residual → 入 red list + 追加 pin 留痕；stale pin → 移除并留痕）
- [x] 行为漂移处置路径已执行或确认无漂移：漂移项入 red list + 显式升级标记（plan 转 blocked 移交），不存在"为恢复绿色而静默吞掉差异"的路径
- [x] 五族 JUnit 门禁 + 表完备性测试运行记录在案（测试名 + 数量 + 0 failures；如有失败 → 同上二分处置）
- [x] 4 条已知 residual 复核表存在（每条：live 行号、pin 状态、是否仍成立）
- [x] 确定性 red list 初稿已写入 red-list.md
- [x] **无静默跳过**：任何门禁红项 / 复核不一致都显式进入 red list 或处置记录，无吞掉差异的路径
- [x] No owner-doc update required（纯审计运行，无行为契约变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - watch-only 3 项动态验证

Status: completed
Targets: `LocalFileCheckpointStorage.java:116-138`；`WindowOperator.java:773-783`；`InputGate.java:625-674`；对应测试目录（nop-stream-runtime / nop-stream-core src/test）

- Item Types: `Proof | Decision`
- [x] **AR-15 动态验证**：`LocalFileCheckpointStorage` 按文件名 ID 排序路径（getLatestCheckpoint）——构造乱序写入多个 checkpoint 的混合 ID 场景，动态验证排序选取是否仍正确；**级联解除的论证链 = 单调恢复（`CheckpointCoordinator.java:896-900` 单调推进守卫，证据 = I0 catalog §3 live 核实 + 门禁③ 机制测试 `TestCheckpointIDCounterInvariant` 佐证）+ 排序选取正确（本次验证）**；裁定：verified / watch-only 保留 / 升格
- [x] **R15-AR-8 动态验证**：`WindowOperator` onEventTime cleanup 路径（:773-783）——构造 merging window 场景（trigger 后 merge + cleanup timer 触发），动态验证 MergingWindowSet 是否随 cleanup 收敛（无窗口状态泄漏）；裁定同上
- [x] **R15-AR-9 动态验证**：`InputGate` per-id `inFlightAlignments`（:625-674）——构造 `maxConcurrentCheckpoints>1` 重叠 barrier 场景（两个 checkpoint ID 交错到达各 channel），验证各 id 独立对齐、无串扰、无静默丢弃；裁定同上
- [x] 动态验证以 **git-committed focused test** 为主载体（测试绿 = 结论可复现，并成为后续回归基线）；无法低成本测试化的场景允许代码路径追踪，但结论必须附 `文件:行` 证据链
- [x] 3 项裁定结论回写 red-list.md（verified → 移除 watch-only 标注并写明依据；watch-only 保留 → 写明 Why Not Blocking；升格 → 入 red list 主体）

Exit Criteria:

- [x] 3 项 watch-only 每项存在动态验证记录（场景描述 + 验证方式 + 结果 + 裁定结论）
- [x] 用测试验证的项：测试 git-committed 且绿（repo-observable）；用代码追踪的项：证据链 `文件:行` 可复核——每项二选一，不允许"口头确认"
- [x] 裁定结论已回写 red-list.md（状态明确：verified / watch-only / red list）
- [x] **端到端验证**（适用项）：R15-AR-9 的验证从 InputGate 入口（channel write/read 驱动 barrier 注入）到出口（`read()` 返回对齐完成的结果）的完整对齐路径走通（非仅组件级孤立断言）；若该项采用代码路径追踪验证，本项降级为调用链追踪证据（`文件:行` 链）
- [x] **无静默跳过**：每项裁定的理由显式写明（不允许"应该没问题吧"式结论）
- [x] No owner-doc update required（验证不改变行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 聚焦对抗探查（门禁盲区 + 非族候选评估）

Status: completed
Targets: nop-stream 五族 `src/main`（gate-inventory.json 目标集）；`ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r{9,16}` 与 `2026-05-30-adversarial-review-nop-stream-r13` 证据；探查报告新文件 `ai-dev/audits/nop-stream-invariants/`

- Item Types: `Proof | Decision`
- [x] 按 `open-ended-adversarial-review-prompt.md` 对五族变更型方法做**聚焦**对抗探查（范围 = gate-inventory.json 目标集 + 盲区清单，不做全仓漫游），盲区清单：a) 新交错组合（并发交错、barrier 交错、timer 交错、restore 与处理并发）；b) refactor 引入的新方法 / 新 call-site 的行为语义（表完备性保证"入表" ≠ 保证"语义正确"）；c) 8 参数元组之外的跨 Operator 参数 / 配置传递链；d) 各轮「修复确认表」（R16 头部 R15-AR-1..5）对照复核
- [x] 4 个非族候选升格评估（每条裁定：升格为新不变式族候选 / 不升格 + 理由）：R13-AR-9（ResultPartition.close 死锁类）、R16-AR-19/AR-20（BatchConsumerSinkFunction buffer 增长 / 序列化）、R8-AR-59（SimpleStreamOperatorFactory 共享模板）、R16-AR-13（WatermarkOutputMultiplexer 停滞）
- [x] 每条探查发现标注族归属：已知族兄弟实例（F1-F5，标注对应历史 finding-ID 或"新发现"）/ 新族（给出不变式陈述候选 + 触发证据 `文件:行`）
- [x] 探查报告写入 `ai-dev/audits/nop-stream-invariants/I2-probing-report.md`（待产出，I1 先例同款前向引用标注），格式对齐既有审计报告（finding 含 位置 / 场景 / 影响 / 族标注）

Exit Criteria:

- [x] 探查报告存在，每条发现含 `文件:行` + 场景 + 族标注（repo-observable）
- [x] 4 个非族候选评估结论在案（升格 / 不升格 + 理由）
- [x] 已知族新实例与历史 finding 的对应关系可追溯（finding-ID）；新族发现（如有）显式标注不变式陈述候选 + 触发证据
- [x] 探查范围声明记录（覆盖类清单），证明是聚焦探查而非全仓漫游
- [x] **逐盲区处置声明**：盲区 a-d 每项至少一句结论（含"检查后无问题"），不允许空洞的零发现报告
- [x] No owner-doc update required（探查为审计产出，不改行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - red list 权威化与移交 I3

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/red-list.md`

- Item Types: `Proof | Follow-up`
- [x] 合并 Phase 1（门禁 red list）+ Phase 2（watch-only 裁定）+ Phase 3（探查发现）全部结论，red-list.md 更新为 **I2 权威版**（每条：位置 / 关联不变式 / 关联 finding / 族标注 / 验证或探查结论 / 裁决输入就绪）
- [x] 与 I0 catalog §6 对照复核：5 个 red-list 候选（AR-1 / AR-11 / AR-9×2 / AR-18）与 3 个 watch-only（AR-15 / R15-AR-8 / R15-AR-9）逐项有处置（在表 / 已裁定移除并说明），**零悬挂**；顺带同步 catalog §3 的 R15-AR-8 行号引用（:776-783 → :773-783，live 行号以本次复核为准）
- [x] 移交声明写入 red-list.md：I3 裁决输入就绪（每条含足够裁决信息：位置、族、严重度参考、验证/探查结论）
- [x] roadmap Work Item I2 状态流转（本 plan 转 active 时 `todo`→`planned`；closure audit 通过后 `planned`→`done`，由本 plan Closure 流程记录）

Exit Criteria:

- [x] red-list.md 为 I2 权威版（Phase 1-3 结论合并齐全、零悬挂：catalog §6 全部 8 项有处置）
- [x] 每条含裁决输入（位置 / 族 / 结论），I3 可直接逐条裁决
- [x] 门禁保持全绿（pin-and-record 语义下；本 plan 未引入新的 CI 红）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 确定性 red list 已生成且与 catalog §6 零悬挂（5 候选 + 3 watch-only 全部有处置）
- [x] 3 个 watch-only 动态验证完成并回写 red-list.md
- [x] 聚焦对抗探查完成（盲区 + 4 非族候选评估），全部发现带族标注
- [x] 新族发现（如有）显式标注（不变式陈述候选 + 触发证据），供 I6 按 Loop Rule 派生 Cycle 2 / I1——未被静默遗漏
- [x] 无 in-scope confirmed live defect 被静默降级或遗漏（全部红项 / 发现都在 red-list.md 或探查报告中）
- [x] 门禁全绿复验（mjs `all` 退出码 0 + 五族 JUnit 门禁 0 failures）；本 plan 未改动被测代码；**若 Phase 1 发现行为漂移：plan 以 blocked 状态移交升级，不执行正常 closure**
- [x] **Anti-Hollow Check**：closure audit 验证门禁 / 验证测试真实运行（surefire 或 mjs 输出记录），无空方法体 / 静默跳过作为"验证完成"的证据
- [x] 独立子 agent closure-audit 已完成并记录证据（`ai-dev/logs/`）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 运行记录在案：输出与执行前基线一致（既有 14 项 high findings 全部为既有 fail-fast 类型——`UnsupportedOperationException` / not-yet-implemented 显式抛错，或注释说明的有意 no-op（非空实现），Rule #24 合规），**无新增 finding**（该工具无豁免/pin 机制、既有 findings 非零属基线事实，判据 = findings 集与基线一致而非退出码 0）
- [x] 新增验证测试（如有）随 `./mvnw test -pl nop-stream -am -T 1C` 全绿；若 Phase 2 全部用代码追踪验证（无新增测试），此项显式写明 `No new test required: <reason>`

## Deferred But Adjudicated

### 4 个非族候选若裁定"不升格"

- Classification: `watch-only residual`
- Why Not Blocking Closure: 升格评估在 Phase 3 内完成并记录理由；裁定不升格 = 该候选不进入门禁沉淀范围，不影响 I2 的 red list 移交 I3 成立；CI 门禁变红 / 核心类结构变更等复触发条件仍可重新评估。
- Successor Required: `no`（复触发机制兜底）

## Non-Blocking Follow-ups

- 探查报告中的优化级发现（非 defect）交由 I3 裁决后按其处置（P2/P3 → Follow-up Backlog）。
- 若探查发现需要更长周期验证的场景（如极端并发压力、需要专门 harness），记录为后续候选，不阻塞 I2 移交。

## Closure

Status Note: I2（不变式驱动审计）全部 4 个 Phase 完成，独立 fresh-session closure audit（ses_00abf7359ffed6a3oVffMV2wfw）逐条 PASS，verdict CLOSED。门禁全绿 + 动态验证测试全绿 + red list 权威版零悬挂移交 I3；本 plan 未改动任何被测代码。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session `ses_00abf7359ffed6a3oVffMV2wfw`，非实现会话）
- Evidence:
  - Phase 1（门禁全量）：mjs `all` 实跑 exit 0（inventory/sync/scan-iterations/self-test）；五族 JUnit + 表完备性 surefire 实测 84 tests 0 failures（Window 9 / SyncedCol 10 / CheckpointID 8 / CepRelease 21 / ClusterReg 8 / 表完备 core 10 + runtime 11 + cep 7）；4 条已知 residual 复核表 live 行号零漂移（2PC:76-78/:83、JDBC:112-115/:172-174、InMemory:68-81）。
  - Phase 2（watch-only 动态验证）：red-list §2 三项逐项裁定在案——WO-1 AR-15 verified（乱序混合 ID 排序测试 13/13 绿）、WO-2 R15-AR-8 升格 RL-6（TestWindowOperatorMergingCleanupInvariant 2/2 绿，PIN 断言泄漏）、WO-3 R15-AR-9 verified（TestInputGateMultiEpochBarrier 5/5 绿，端到端 per-id 对齐路径）。
  - Phase 3（聚焦探查）：I2-probing-report.md 在案（PR-1 → RL-7 新族候选 + 盲区 a-d 逐项结论 + 4 非族候选全部不升格附理由）；R15-AR-1..5 修复确认表对照复核（4 fixed + 1 live）。
  - Phase 4（权威化移交）：red-list.md = I2 权威版（RL-1..7 + WO 裁定 + 移交声明）；catalog §6 零悬挂（8 项全部有处置）；catalog §3 R15-AR-8 行号同步 :773-783；roadmap I2 `planned`→`done`。
  - `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0（全部 checklist 已勾选 + Closure Evidence 已写入）。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（12 个 BROKEN_LINK warning 全部位于其他 plan 文件——credential plan 3 个 pre-existing + I3 plan 9 个对自身未来产物 adjudication-table.md 的前向引用；本 plan 拥有的 5 个文件零 issue）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 运行记录在案：findings 集 = 既有 14 项基线（11× UnsupportedOperationException fail-fast + 3× 注释说明的有意 no-op，全在 src/main），与执行前基线一致、无新增（判据 = findings 集一致而非退出码，工具无豁免机制）。
  - Anti-Hollow：门禁/验证测试真实运行（surefire XML + mjs 输出记录在案）；TestWindowOperatorMergingCleanupInvariant 断言真实行为（getStateWindow PIN + sum 输出断言，非空方法体）；端到端路径（InputGate channel write → read()）走通。
  - 新增验证测试随 `./mvnw test -pl nop-stream -am -T 1C` 全绿（797 tests 0 failures；nop-stream-rocksdb benchmark 单次偶发超时在隔离复跑中通过，模块未被本 plan 触碰）。
  - Deferred 项分类检查：无 in-scope live defect 被降级——RL-6/RL-7 均显式入 red list 主体；4 个非族候选裁定不升格均有 live 代码理由（3 个 live 已修复或无生产接线、1 个 fail-fast + 模块范围外）。

Follow-up:

- no remaining plan-owned work（red list 移交 I3 裁决，I3 plan `2026-08-12-1217-4-...` 已就绪）
- 探查报告中的优化级发现（无）交由 I3 裁决后按其处置。
