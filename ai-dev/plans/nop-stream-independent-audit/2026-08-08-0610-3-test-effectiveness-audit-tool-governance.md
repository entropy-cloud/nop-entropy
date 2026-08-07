# 17 Test Effectiveness & Audit-Tool Governance (nop-stream Independent Audit)

> Plan Status: active
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 17); frozen Stage-4 outputs (`source-manifest.md` domain `g` test lane (453 test java files / 13 fixtures / 4 multi-jvm fixtures) + `finding-corpus.md` test-quality findings across shards 18-22, `evidence-schema.md`); frozen Stage-5 outputs (`environment-qualification.md` T1-T6 lane policy); frozen Stage 6-16 evidence (critical-test references); `ai-dev/tools/check-nop-stream-audit-manifest.mjs` + `ai-dev/tools/scan-hollow-implementations.mjs`; Stage 6-16 Non-Blocking Follow-ups deferring test-effectiveness items to Stage 17.
> Mission: nop-stream-independent-audit
> Work Item: 17. Test effectiveness and audit-tool governance
> Related: Execution order `{3}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 5 (env qualification) — all `done`. Hard prerequisite for Stage 21 (Hist P2 core/state/window) and Stage 22 (Hist P2 CEP/connector/runtime). NOT on critical path. Absorbs test-effectiveness / coverage-gap follow-ups deferred by Stage 6-16 audit plans (CEP Stage 12 deferred 5 items; checkpoint/window/state/data-plane/connector audits deferred multiple).

## Purpose

独立验证 nop-stream 审计所依赖的 **测试有效性** 与 **审计工具本身的可信度**。本计划不审计某个领域能力的正确性（那是 Stage 6-16 的职责），而是审计"证据本身是否可信"与"关键行为是否有 non-vacuous 回归保护"。

具体验证四件事：(a) 从 manifest 切片派生的 **有限 critical-test registry**——每个被已完成的 domain audit（Stage 6-16）引用为 `positive_proof` 的测试必须登记，使后续 disposition（Stage 18-22）能追溯；(b) 每个 disabled/gated/hollow 测试必须有 **一个明确 disposition**（`@Disabled` `TestDebeziumCdcSourceCompletion` + 5 个 `@EnabledIfSystemProperty` gated 测试）；(c) 每个 registered critical behavior 须有 **negative 或 mutation-style control**（证明该测试真能抓 bug，而非 vacuously pass）；(d) 审计工具（`check-nop-stream-audit-manifest.mjs` **5 个 subcommand（含 `self-test`）** + `scan-hollow-implementations.mjs`）须有 **positive control**——经 `self-test`（4 个 checker）+ 新建 fixture（scan-hollow）证明零结果输出是真实结论而非工具失效。

本审计**不修复** test-quality finding（那是 active remediation plan `2026-08-04-2300-3` 及 successor 的职责），只登记 + disposition + 标注 successor。本审计**发现**的审计工具缺陷（如 validator 无法检测某类违规）按 roadmap 规则指派。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 `g` + 实际源码/测试一致）：

- **测试语料总量**（manifest 域 `g`，frozen）：`test-java-files-all` denominator **453**（10 模块 src/test 全部 *.java，refreshed 2026-08-08）；`test-resource-fixtures` **13**；`test-lane-multi-jvm-fixtures` **4**（MiniStreamCluster + TestMiniStreamClusterProcessSpawn + TestMultiJvmCoordinatorFailover + TestMultiJvmExactlyOnceRecovery）。
- **Disabled 测试**（live scan `@Disabled`/`@Ignore`）：**1 个** `TestDebeziumCdcSourceCompletion` `nop-stream-connector-debezium/.../debezium/TestDebeziumCdcSourceCompletion.java`——`@Disabled("Genuinely broken: DebeziumCdcSourceFunction.run() loops until cancel() or ...")`（genuinely disabled，reason 记录在注解）。无其他 `@Disabled`/`@Ignore`。
- **Gated 测试**（live scan `@EnabledIfSystemProperty`）：**5 个**——`TestMiniStreamClusterProcessSpawn`、`TestMultiJvmExactlyOnceRecovery`、`TestMultiJvmCoordinatorFailover`（gated `nop.stream.test.multi-jvm.enabled=true`，T2 lane）、`TestDataPlaneKafkaBackendE2E`（gated `nop.stream.test.kafka.enabled`，T3 lane）、`TestDataPlanePulsarBackendE2E`（gated `nop.stream.test.pulsar.enabled`，T4 lane）。全部在 `nop-stream-runtime` 模块。
- **审计工具**：
  - `ai-dev/tools/check-nop-stream-audit-manifest.mjs`——**5 个 subcommand**：`manifest`（校验 source-manifest `@@ENTRY`，执行 `command:` 比对 `expected_denominator`，期望 ≥7 域）、`corpus`（校验 finding-corpus，期望 exactly 5 shards 18-22）、`evidence`（校验 `@@EVIDENCE` 11 字段 + 7 disposition 词表 + environment_class ≥ required_lane 不变量）、`qualification`（校验 `@@LANE` 块，T1-T6）、**`self-test`（`cmdSelfTest` `:560-575`——positive control：对 manifest/corpus/evidence/qualification 各注入已知违规并验证拒绝，含 good-entry 反向校验防"盲目全拒"；退出 0 即证明 4 个 checker 能报错）**。`MANIFEST_FILE`/`CORPUS_FILE`/`SCHEMA_FILE`/`QUAL_FILE` **硬编码**（`:30-33`），CLI 不接受自定义文件路径（`:591-616`）——故 manifest/corpus/evidence/qualification 4 个 subcommand 的 positive control **只能通过 `self-test` 触发**，不可用临时 manifest 文件注入。另支持 `all`（聚合）。
  - `ai-dev/tools/scan-hollow-implementations.mjs`——扫描空方法体/静默跳过/no-op，接受 positional path 参数 + `--severity` 过滤。closure gates 引用其 `--severity high` 退出码。**无内置 self-test / positive control**（已核实：只有扫描逻辑），接受自定义 path 故可用注入已知空壳 fixture 做 positive control。
- **test-quality findings**（finding-corpus.md，跨 shards 18-22，test-effectiveness 类）：
  - **ZERO-test 类**（关键行为无任何测试）：M7-2-P0-5（Serializer Fingerprint recovery-compat ZERO tests，shard 19）、M7-2-P0-6（fencing-token rejection ZERO tests，shard 20）、M7-2-P0-7（Savepoint differential ZERO tests，shard 19）、M7-2-P0-8（stateShardCount rescale ZERO tests，shard 19）。
  - **misleading / vacuous 类**：M8-2-P2-20（TestWatermarkStateRobustness 误导类名，shard 18）、M8-2-P2-22（TestFlowControl hardcoded constants，shard 18）、M8-2-P2-23（TestCountTrigger/TestMapStateDescriptor/TestE2EStorageTypeRouting low-value，shard 18，recurrent M7-2-P2-9）、O8-2-AR-4（TestGeographicAnomalyPatternFix zero bug-catching，shard 18）、M7-2-P1-14（TestAfterMatchSkipStrategies 100% metadata，partial-fixed，shard 20）、M7-2-P2-9（TestCountTrigger canMerge only，shard 21）、M7-2-P2-10（TestCheckpointBarrier getter round-trip，shard 21）、M7-2-P2-11（TestTaskStateSnapshot etc no serialization fidelity，shard 21）、M7-2-P2-12（TestCheckpointType enum count，shard 21）、M7-2-P2-13（TestProcessingGuarantee constant boolean，shard 21，recurrent M8-2-P2-21）、M7-2-P2-15（TestCheckpointIDCounter AtomicLong no concurrency，shard 21）、M7-2-P2-16（TestWindowOperatorBasic geometry primitives，shard 21）、M7-2-P2-17（TestSharedBuffer assertNotNull，shard 22）、M7-2-P2-18（TestNFAState mirror tests，shard 22）、M7-2-P2-14（TestJobTerminationContext factory field，shard 22）。
  - **coupling 类**：M7-2-P1-13（TestCepOperatorStateBackendWiring couples internal accessors，shard 20）。
  - **happy-path-only 类**：M7-2-P1-15（TestBatchConsumerSinkFunction happy-path only，partial-addressed，shard 20）、M7-2-P1-12（Watermark multi-input only unit，self-exempts via Anti-Hollow exemption，shard 20）。
  - **已修复类**（仍登记供 disposition 标 closed）：M7-2-P0-4（TestCepOperatorDanglingCleanup never asserts，FIXED，shard 20）、M8-2-P2-21（TestProcessingGuarantee dup，shard 18）、M7-2-P2-8（Lockable.release bare exception，FIXED，shard 22）。
- **Stage 6-16 deferred follow-ups 指向 Stage 17**（已记录在各 audit plan Non-Blocking Follow-ups）：CEP Stage 12 deferred 5 项（dangling size>1 coverage、no-env-execute CEP coverage、O8-2-AR-4、M7-2-P1-13/14、M7-2-P2-17/18、M8-2-P2-20）；data-plane Stage 14 deferred T2 defect test-quality；其他 audit 亦有多项。
- **真实 gap**：(1) 无 finite critical-test registry（Stage 6-16 引用的 `positive_proof` 测试未集中登记）；(2) 1 disabled + 5 gated 测试缺集中 disposition（散落在各 audit，未在 test-effectiveness 视角统一裁定）；(3) 关键 ZERO-test finding（M7-2-P0-5/6/7/8）缺 negative/mutation control（即"加测试后真能抓 bug"的证明）；(4) `check-nop-stream-audit-manifest.mjs` 的 4 个 checker 的 positive control（`self-test`）**已存在但未在本治理计划中 run + freeze 为可复跑 control 记录**，`scan-hollow-implementations.mjs` **无 positive control（需新建 fixture）**；(5) test-environment evidence policy 合规性（gated test 仅在 qualified lane 实跑才算 evidence，Rule S5-1）缺集中核对。

## Goals

- 产出一份 **finite critical-test registry**：从执行时已存在的 evidence 文件（当前 stage-6..14；15/16 若已完成则纳入）的 `positive_proof` 字段提取所有被引用的测试方法（`ClassName#method`），去重登记，每条标注所属 domain audit + 验证的能力。registry 写入 `ai-dev/audits/nop-stream-independent-audit/stage-17-test-effectiveness-and-tool-governance.md`。
- 为 **每个 disabled/gated/hollow 测试**产出一个 disposition：`TestDebeziumCdcSourceCompletion`（`@Disabled` → 记录 reason + successor remediation/Stage-17）；5 个 gated 测试（逐个标注 gate property、所属 lane、`qualified`/`blocked` 状态、是否在 audit window 实跑过——Rule S5-1 合规）。
- 为 **每个 registered critical behavior**（至少覆盖 4 个 ZERO-test finding M7-2-P0-5/6/7/8 对应的关键行为）裁定 **negative/mutation control 状态**：若已有 rejection/fault-injection 测试 → 标 `has-negative-control`；若无 → 标 `missing-negative-control` + successor ownership（Stage 18-22 disposition 或 active remediation plan）。
- 为 **审计工具**产出 **positive control**：对 `check-nop-stream-audit-manifest.mjs` 的 4 个 checker（manifest/corpus/evidence/qualification）通过**运行既有 `self-test` subcommand**（`cmdSelfTest :560-575`，注入已知违规验证拒绝）并 freeze 命令+期望输出为 control 记录（注意：CLI 硬编码文件路径，不可用临时 manifest 注入）；对 `scan-hollow-implementations.mjs`（无内置 self-test，接受自定义 path）**新建一个已知空壳 fixture**，证明 `--severity high` 报 high/critical 且非零退出。
- 产出 **test-environment evidence policy 合规核对**：Rule S5-1（gated test 仅在 qualified lane 实跑才算 evidence）—— 核对执行时已存在的 evidence 文件中是否有引用 skipped gated test 作为 `positive_proof`（若有则 flag 为违规）。
- 对 **test-quality findings**（ZERO-test / misleading / coupling / happy-path-only 类）做集中登记 + disposition：每条标 `closed`(FIXED) / `live-residual`(successor) / `successor-plan-owned`；confirmed still-live test-quality finding 不得静默降级。
- 所有 governance 产出（registry + dispositions + controls + policy 核对）写入 stage-17 文件；evidence-row 形式的 control 证据经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验（若产出 evidence row）。

## Non-Goals

- 领域能力正确性审计（checkpoint/state/window/CEP/connector/control-plane/data-plane）——属 Stage 6-16（已完成），本计划只审测试与工具可信度。
- 修复 test-quality finding——属 active remediation plan `2026-08-04-2300-3` 及 successor（本计划只登记 + disposition + successor 标注）。
- 历史 finding 的正式 disposition（revalidated/stale/active-owner/residual/blocked）——属 Stage 18-22（本计划只从 test-effectiveness 视角标注 control 状态，不做 corpus disposition）。
- provision Kafka/Pulsar/PostgreSQL/real-CDC（gate 未解除前如实记录 gated 状态）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-17-test-effectiveness-and-tool-governance.md`（critical-test registry + disabled/gated disposition + negative-control 裁定 + audit-tool positive controls + evidence-policy 合规核对）。**文件名为 audit dir 直系子文件。**
- 审计工具 positive control fixture（若需新增临时 fixture，放 `_tmp/` 或 audit dir 下 `_control_*`，不改生产代码）。

### Out Of Scope

- 领域能力审计（Stage 6-16）。
- test-quality finding 修复（active remediation plan）。
- 历史 finding corpus disposition（Stage 18-22）。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。
- 修改审计工具的校验逻辑（若发现工具缺陷，指派 successor，不在本计划改工具）。
- provision 外部 backend。

## Execution Plan

### Phase 1 - Critical-Test Registry & Disabled/Gated Test Disposition

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-17-test-effectiveness-and-tool-governance.md`

- Item Types: `Proof | Decision`

- [ ] 提取并登记 **critical-test registry**：从执行时已存在的 `stage-*.evidence.md` 文件（当前为 stage-6..14；stage-15/16 若已完成则纳入）的所有 `@@EVIDENCE` 行 `positive_proof`/`rejection_proof` 字段提取测试方法名（`ClassName#method`），去重，逐条标注所属 domain stage + 验证能力 + 在测试树中是否真实存在（live 验证 file 存在）。**successor 机制**：后续 stage 15/16/18-22 完成时，由其 owner plan 回填本 registry（或本 plan 注明 registry 为 live 快照，由 Stage 23 汇总）。
- [ ] 为 `TestDebeziumCdcSourceCompletion`（`@Disabled`）产出 disposition：记录 disabled reason（"Genuinely broken: run() loops until cancel()"）、anchor、successor（remediation plan 或 Stage 17 follow-up）、是否阻塞任何 evidence row（核对无 Stage 6-16 evidence 引用其为 `positive_proof`——若有则 flag 违规）。
- [ ] 为 5 个 gated 测试逐个产出 disposition：`TestMiniStreamClusterProcessSpawn` / `TestMultiJvmExactlyOnceRecovery` / `TestMultiJvmCoordinatorFailover`（T2 lane，gate `nop.stream.test.multi-jvm.enabled`）/ `TestDataPlaneKafkaBackendE2E`（T3）/ `TestDataPlanePulsarBackendE2E`（T4）——逐个标注 gate property、lane、`qualified`/`blocked`、audit window 是否实跑过、是否被某 evidence row 引用（引用 skipped gated test 为 `positive_proof` = Rule S5-1 违规）。
- [ ] 产出 **multi-jvm lane (T2) deeper-defect test disposition**：`TestMultiJvmExactlyOnceRecovery`（log-label mismatch defect）+ `TestMultiJvmCoordinatorFailover`（HA-fencing takeover defect）——记录其为 known defect（Stage 14 已标 `blocked`），test-effectiveness 视角标注 successor remediation。

Exit Criteria:

- [ ] critical-test registry 成文，每条经 live 验证测试方法真实存在于测试树（非凭空），registry 覆盖执行时已存在的全部 evidence 文件的 `positive_proof`/`rejection_proof` 引用（并注明 successor 回填机制）
- [ ] 1 disabled + 5 gated 测试各有 disposition（reason/lane/successor/Rules-S5-1 合规）
- [ ] **无静默跳过（Rule #24）**：任一 disabled/gated 测试不得默默跳过——须有 disposition；若某 evidence row 引用 skipped gated test 为 `positive_proof`，须 flag 为 Rule S5-1 违规（不得静默当 evidence）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Negative/Mutation Controls For Critical Behaviors & Test-Quality Finding Registry

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-17-test-effectiveness-and-tool-governance.md`

- Item Types: `Proof | Decision`

- [ ] 为 **4 个 ZERO-test 关键行为**（M7-2-P0-5 Serializer Fingerprint recovery-compat、M7-2-P0-6 fencing-token rejection、M7-2-P0-7 Savepoint differential、M7-2-P0-8 stateShardCount rescale）裁定 negative-control 状态：核对 live repo 是否已补 rejection/fault-injection 测试（active remediation plan 可能已补）；有则标 `has-negative-control` + 测试名；无则标 `missing-negative-control` + successor（Stage 18-22 disposition 或 remediation plan）。
- [ ] 对 registry 中**其余 critical behavior**（被 ≥1 evidence row 依赖、且非 ZERO-test 类的测试方法）裁定 negative-control 状态。**"critical behavior" 定义裁定**（roadmap "each registered critical behavior" 的落地）：mandatory 集合 = 4 个 ZERO-test 关键行为（关键正确性无任何测试，必须逐个裁定）；其余 registry 项按"是否被 evidence row 引用为唯一 `positive_proof`"判定 criticality——被引用为唯一证据的，裁定其是否 non-vacuous（借鉴 corpus P-2/P-3/P-4 分类：metadata-only/assertNotNull-only/getter-round-trip 视为 vacuous 标 `vacuous` + successor）；未被引用或非唯一的标 `watch-only`。逐条记录判定理由，使 closure audit 可机械复核。
- [ ] 登记 **test-quality findings 集中表**（misleading/vacuous/coupling/happy-path-only 类，跨 shards 18-22）：逐条标注 finding_id、anchor、live 状态（`closed`(FIXED) / `live-residual` / `successor-plan-owned`）、successor。至少覆盖：M8-2-P2-20/22/23、O8-2-AR-4、M7-2-P1-13/14、M7-2-P2-9/10/11/12/13/14/15/16/17/18、M7-2-P1-12/15、M8-2-P2-21。
- [ ] 吸收 Stage 6-14 deferred test-effectiveness follow-ups：逐条核对 CEP Stage-12 deferred 的 5 项 + 其他 audit deferred 项是否已在本 registry 登记，未登记的补登。

Exit Criteria:

- [ ] ≥4 个 ZERO-test 关键行为有 negative-control 状态裁定（`has-negative-control` 或 `missing-negative-control` + successor）
- [ ] 其余 registry critical behavior 有 non-vacuous 判定（mandatory 逐个 + 其余按唯一-positive_proof criticality 裁定），"critical behavior" 定义显式成文
- [ ] test-quality findings 集中表成文，每条有 live 状态 + successor；confirmed still-live 不得静默降级为 non-blocking
- [ ] **无静默跳过（Rule #24）**：vacuous 测试须标 `vacuous` + successor（不得默默当有效）；missing-negative-control 须标 successor（不得默默当已覆盖）
- [ ] Stage 6-14 deferred test-effectiveness follow-ups 全部被吸收（登记或显式 cross-ref）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Audit-Tool Positive Controls & Evidence-Policy Compliance

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-17-test-effectiveness-and-tool-governance.md`

- Item Types: `Proof | Decision`

- [ ] 为 `check-nop-stream-audit-manifest.mjs` 的 4 个 checker（manifest/corpus/evidence/qualification）产出 positive control：**运行既有 `self-test` subcommand**（`node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test`，`cmdSelfTest :560-575`——对 4 个 checker 各注入已知违规并验证拒绝，含 good-entry 反向校验）；freeze 命令 + 期望输出（`[PASS] self-test (positive control)`，exit 0）为 control 记录；**核对 `self-test` 覆盖的违规类别是否充分**（逐个 checker 列出其注入的 known-bad 输入类型）——如发现检测盲区（某类违规未被 self-test 覆盖）→ 标 tool-defect + successor plan（不在本计划改工具）。
- [ ] 为 `scan-hollow-implementations.mjs` 产出 positive control（唯一无内置 self-test 的工具）：**新建一个已知空壳 fixture**（放 `_tmp/` 或 audit dir 下 `_control_hollow_fixture`，含空方法体/吞异常/`continue` 跳过等已知模式）；运行 `node ai-dev/tools/scan-hollow-implementations.mjs <fixture> --severity high`，证明报 high/critical 且非零退出；freeze fixture 路径 + 命令 + 期望输出为 control 记录。
- [ ] 产出 **evidence-policy 合规核对**：扫描 Stage 6-N（执行时已存在的 evidence 文件，当前为 6-14；15/16 若已完成则纳入）evidence 文件，核对是否有 `positive_proof` 引用 skipped gated test（Rule S5-1 违规）或引用不存在/已 `@Disabled` 的测试方法；flag 任何违规。
- [ ] 全 governance 文件回归。**注**：governance 产出文件名为 `stage-17-*.md`（非 `*.evidence.md`），`check-nop-stream-audit-manifest.mjs evidence` 只扫 `*.evidence.md`（`:304`）——故 control 证据以 governance 文本记录为准，不放入 `@@EVIDENCE` 行（若需 evidence-row 形式须另建 `*.evidence.md` 文件）。

Exit Criteria:

- [ ] 4 个 manifest checker 经 `self-test` 验证（run + freeze 命令/期望输出，exit 0），覆盖的违规类别逐个列明；任何检测盲区标 tool-defect + successor
- [ ] `scan-hollow-implementations.mjs` 有新建 positive control fixture（注入已知空壳 → `--severity high` 报 high/critical，非零退出）
- [ ] **无静默跳过（Rule #24）**：positive control 必须证明工具真能报错（`self-test` 退出 0 证明 4 checker 拒绝 known-bad；scan-hollow fixture 证明非零退出）——不得"工具退出 0 就当可信"而无注入违规证据；evidence-policy 违规必须 flag（不得默默放过）
- [ ] evidence-policy 合规核对覆盖执行时已存在的全部 evidence 文件；任何 Rule S5-1 违规 / 引用不存在测试 / 引用 `@Disabled` 测试 均被 flag
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **治理/审计计划（无生产代码变更）**：本计划产出为 registry + dispositions + controls + policy 核对文本，不改 nop-stream 生产代码与审计工具校验逻辑。`./mvnw test`/`compile` 不强制；改为以 governance 产出完整性 + 审计工具 positive control 退出码为 closure 依据。但若审计中发现审计工具缺陷，按 roadmap 规则指派 successor（不在本计划改工具）。

- [ ] critical-test registry 成文，覆盖执行时已存在的全部 evidence 文件的 `positive_proof`/`rejection_proof` 引用（当前 6-14；15/16 若已完成则纳入），每条 live 验证真实存在（并注明 successor 回填机制）
- [ ] 1 disabled + 5 gated 测试各有 disposition（含 Rule S5-1 合规）
- [ ] ZERO-test 关键行为（M7-2-P0-5/6/7/8）有 negative-control 状态裁定
- [ ] 其余 registry critical behavior 有 non-vacuous 判定（"critical behavior" 定义显式成文）
- [ ] test-quality findings 集中表成文，confirmed still-live 不静默降级
- [ ] `check-nop-stream-audit-manifest.mjs` 4 个 checker 经 `self-test` 验证（run + freeze，exit 0）；`scan-hollow-implementations.mjs` 有新建 positive control fixture（非零退出）
- [ ] evidence-policy 合规核对覆盖执行时已存在的全部 evidence 文件，违规均 flag
- [ ] Stage 6-14 deferred test-effectiveness follow-ups 全部吸收
- [ ] 不存在被静默降级到 deferred 的 in-scope 审计项（vacuous/missing-control/gated-defect/tool-defect 标 successor；均为合法终态）
- [ ] 审计工具缺陷（若有 self-test 盲区）已指派 successor
- [ ] `No owner-doc update required`（不改 `docs-for-ai/`）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）registry 中测试方法确为 live 存在（非凭空），（b）`self-test` 退出 0 确证 4 checker 拒绝 known-bad + scan-hollow fixture 非零退出（非"退出 0 即可信"），（c）evidence-policy 违规无静默放过，（d）vacuous/missing-control 标 successor 无静默降级
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0（Minimum Rule #26）

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：ZERO-test 关键行为若 active remediation plan 尚未补测试——标 `missing-negative-control` + successor（Stage 18-22 disposition 或 remediation plan），是合法终态（不阻塞本治理计划 closure，但阻塞 ready verdict 经由其 finding owner）。审计工具 positive control 若发现工具确有检测盲区——标 tool-defect + successor（不在本计划改工具）。`TestDebeziumCdcSourceCompletion` `@Disabled`——记录 reason + successor，不在本计划修复。confirmed still-live test-quality finding 不得 deferred——须标 successor。）

## Non-Blocking Follow-ups

- ZERO-test 关键行为 negative-control 补测试 → active remediation plan `2026-08-04-2300-3` / successor 或 Stage 18-22 disposition 触发。
- `TestDebeziumCdcSourceCompletion` `@Disabled` 修复 → successor remediation plan。
- 审计工具 positive control fixture 长期化（若本计划用 `_tmp/` 临时 fixture）→ 工具 successor plan。
- vacuous/misleading 测试治理 → test-quality remediation successor（分领域）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
