# 16 JDBC / File / CDC Connector External-Effect Audit (nop-stream Independent Audit)

> Plan Status: active
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 16); frozen Stage-4 outputs (`source-manifest.md` domain `e` connector-main-java-files + domain `a`, `evidence-schema.md`, `finding-corpus.md` shard 19 P0 sink findings + shard 20, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md` — T1 `qualified`/`in-process` embedded H2 + mocked Debezium, T5 `blocked` PostgreSQL, T6 `blocked` real-CDC); frozen Stage-9/10/14 evidence (checkpoint/state/data-plane, referenced not re-audited); live repo baseline of `nop-stream-connector-jdbc` + `nop-stream-connector` file sink + `nop-stream-connector-debezium`.
> Mission: nop-stream-independent-audit
> Work Item: 16. JDBC/file/CDC connector external-effect audit
> Related: Execution order `{2}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 5 (env qualification), Stage 9 (checkpoint audit), Stage 14 (data-plane/multi-JVM audit) — all `done`. Hard prerequisite for Stage 20 (Hist P0/P1 CEP/connector/runtime) and Stage 22 (Hist P2 CEP/connector/runtime). NOT on critical path. References Stage 9 checkpoint-recovery conclusions and Stage 14 transport proof without re-auditing them.

## Purpose

独立验证 nop-stream 的 **JDBC / file / CDC connector** 的 transactional external effect 与 source offset recovery 在真实 failure cut 下是否成立。每个 sink 的 two-phase-commit 外部效果（commit/abort/retry/idempotency）与 CDC source 的 offset restore 必须形成一条可复核的 evidence row：声明保证（`TWO_PHASE_COMMIT` / `REPLAYABLE` 标签）、实现锚点、运行时接线、正向证据（真实 in-process 实跑测试名）、拒绝/失败证据、lane 强度、finding 关联、disposition。

本审计验证核心 invariants：(a) JDBC sink 的 commit 在单一 JDBC transaction 内原子写入 data + ledger（idempotent guard，recover-safe re-commit）；(b) file sink 的 atomic rename + manifest commit（含 final-exists/manifest-missing edge repair）；(c) CDC source 的 offset snapshot/restore round-trip（`CDC_OFFSETS_KEY`，restore 后 engine 从 checkpointed position resume，no duplicates / no data loss）；(d) base `TwoPhaseCommitSinkFunction.restoreFromEpoch` 不再盲目 rollback 全部 pending（corpus M7-2-P0-2：durable-but-uncommitted re-commit、non-durable abort）；(e) external-system 真实集成（PostgreSQL / real CDC）因 T5/T6 lane `blocked` 如实标 `blocked`，不得用 embedded H2 / mocked offset 冒充。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。checkpoint coordinator barrier 语义（Stage 9）与 data-plane transport（Stage 14）已审计，本计划引用结论不重新审计。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 `e`（4 connector 模块 16 个 main java 文件）+ 域 `a` + 实际源码一致）：

- **2PC sink base class**：`TwoPhaseCommitSinkFunction<IN>` `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:25`（implements `SinkFunction`, `CheckpointParticipant`）。`getPendingCommits()` `:72`。**M7-2-P0-2 已修复**：`restoreFromEpoch(long epochId, TaskStateSnapshot)` `:153-196` 不再盲目 rollback 全部 pending——现分离 durable-but-not-committed（`epochId <= N` → `commit(eid)` `:169-181`，commit 失败则 retain pending 供 subsuming commit）与 non-durable in-flight（`epochId > N` → `abort(eid)` `:183-193`）。`abort(long)` 默认委托 `rollback()` `:63-64`（向后兼容 13+ 旧子类）。`finishCommit(epochId, success)` `:95` subsuming 逐 epoch commit。Javadoc `:136-146` 显式记录旧实现缺陷。
- **JDBC sink**：`JdbcTwoPhaseCommitSink<IN>` `nop-stream-connector-jdbc/.../jdbc/JdbcTwoPhaseCommitSink.java:70`（extends `TwoPhaseCommitSinkFunction<IN>`）。`getSinkConsistency()` `:283-285` 返回 `TWO_PHASE_COMMIT`。saveState-first override `:170-182`（buffer 移入 `pendingCommits[epochId]` BEFORE `super.saveState`，避免 lag-by-one-epoch）。`commit(checkpointId)` `:191-266`：开**独立 connection** `:213`，`setAutoCommit(false)` `:216`，**idempotent ledger guard**（`ledgerExists` `:219-225` skip data write），data rows + ledger entry 同一 `connection.commit()` `:228-235`，失败 `connection.rollback()` `:246-252`，finally close `:258-262`，成功后 `pendingCommits.remove` `:265`。ledger DDL + `initializeLedgerTable()` `:300-336`。`rollback()` `:269-273` 清 buffer；`abort(epochId)` `:276-280` 丢弃 pending（data 从未写入）。
- **File sink**：`FileTwoPhaseCommitSink<IN>` `nop-stream-connector/.../file/FileTwoPhaseCommitSink.java:64`（extends `TwoPhaseCommitSinkFunction<IN>`）。`getSinkConsistency()` `:109-111` 返回 `TWO_PHASE_COMMIT`。saveState-first `:130-142`（buffer 写 temp file `.{epochId}.tmp` + `FilePendingCommit` 入 pending BEFORE super）。`commit(checkpointId)` `:150-197`：**idempotent manifest guard**（`manifest.containsKey` `:169-172` skip），**final-exists/manifest-missing edge repair**（`Files.exists(finalPath)` `:174-181` 补 manifest skip rename），atomic rename `Files.move(ATOMIC_MOVE)` `:185`，atomic manifest update `updateManifestAtomically` `:193`（temp manifest + ATOMIC_MOVE + REPLACE_EXISTING `:254-255`）。`abort(epochId)` `:207-213` 删 temp file。`rollback()` `:200-204` 清 buffer。
- **CDC source**：`DebeziumCdcSourceFunction` `nop-stream-connector-debezium/.../debezium/DebeziumCdcSourceFunction.java:48`（implements `DrainableSource<ChangeEvent>`, `CheckpointedSourceFunction<ChangeEvent>`）。`getSourceConsistency()` `:185-187` 返回 `REPLAYABLE`。`CDC_OFFSETS_KEY = "cdc-offsets"` `:56`。`snapshotState(long)` `:212-227`：从 `NopStreamOffsetBackingStore.getOffsets()` 序列化入 `OperatorSnapshotResult`（store null 时写 empty TreeMap `:220-221`）。`initializeState(TaskStateSnapshot)` `:231-258`：state null → fresh store `:234`；offset entry null → fresh store `:242`；**fail-fast** 非 Map 抛 `StreamException` `:246-250`；restore offset via `fromSerializable` → `setOffsets` `:252-257`。`run()` `:126-168` 用 `createMessageSource(config, offsetStore)` `:135`（protected，测试可注入 mock `:120-123`）。drain via `truncateForDrain()` `:190-203`。`config` 已 Serializable（`DebeziumConfig implements Serializable` `:59-62`）跨 JVM recovery 存活。
- **测试语料**（manifest 域 `g`；JDBC/file/CDC connector 测试）：
  - JDBC：`TestJdbcTwoPhaseCommitSinkSkeleton`（19 @Test，**embedded H2** `jdbc:h2:mem ...;MODE=MySQL` via HikariDataSource，T1 lane）、`TestJdbcTwoPhaseCommitSinkDeep`（共 2 文件）。
  - Debezium：`TestDebeziumCdcCheckpoint`（7 @Test，**mocked** offset-config，T1 lane）、`TestDebeziumCdcSourceFunction`、`TestDebeziumResourceManagement`、`TestDebeziumCdcSourceCompletion`（**`@Disabled("Genuinely broken: DebeziumCdcSourceFunction.run() loops until cancel()...")`**——genuinely disabled test）。
  - File：`TestFileTwoPhaseCommitSink`、`TestFileSourceCheckpointRestore`（connector 模块）。
  - **全部 unit/in-process embedded/mock**——无真实 PostgreSQL sink 测试（T5 blocked）、无真实 source-DB CDC engine 测试（T6 blocked）。
- **Corpus 交叉**：M7-2-P0-2（TwoPhaseCommitSinkFunction.restoreFromEpoch 盲目 rollback 全部 pending，**FIXED**，domain `checkpoint/state`，shard 19）、M7-2-P0-3（StreamSinkOperator.restoreState 调 restoreFromEpoch(-1,null)，compounds P0-2，domain `checkpoint/state`，shard 19）。两者 formal disposition 属 shard 19（core/state/window）→ Stage 19 owner；本计划审计 sink external-effect 时 live 复验并 cross-ref，不替代 Stage 19 disposition。
- **guarantee-tier 校验机制**（roadmap "exact label validation for strict/effectively/at-least-once" 映射）：`ProcessingGuarantee` 枚举 `nop-stream-core/.../checkpoint/ProcessingGuarantee.java:10-14`（`STRICT_EXACTLY_ONCE(true,true)` `:12`、`AT_LEAST_ONCE(false,false)` `:13`、`EFFECTIVELY_ONCE(false,true)` `:14`）。`StreamRequirementValidator.validateConnectorConsistency(guarantee, sourceCaps, sinkCaps)` `nop-stream-core/.../model/StreamRequirementValidator.java:84-114`：仅当 `STRICT_EXACTLY_ONCE` 时强制 source ≥ `REPLAYABLE`（`:94-100`）+ sink ≥ `TWO_PHASE_COMMIT`（`:102-108`），不满足抛 `StreamException`（`:110-113`）；非 STRICT tier 直接 return（`:88-90`）。JDBC/file sink 声明 `TWO_PHASE_COMMIT`、CDC source 声明 `REPLAYABLE`——本计划须验证这些 per-connector 标签在 STRICT tier 下被 `validateConnectorConsistency` 正确接受，并裁定 EFFECTIVELY_ONCE/AT_LEAST_ONCE tier 对本域 connector 的外部效果差异。
- **真实 gap**：(1) 没有 JDBC/file 2PC commit/abort/retry/idempotency 的成套 evidence row；(2) CDC offset snapshot/restore round-trip 缺独立 evidence row 冻结（虽有 `TestDebeziumCdcCheckpoint` 7 个 mocked）；(3) M7-2-P0-2 restoreFromEpoch FIXED 缺 sink-external-effect 视角的复验 evidence row（Stage 9 checkpoint 视角已审）；(4) external-system 真实集成（PostgreSQL / real CDC）缺 honest `blocked` 标注（T5/T6 blocked）；(5) `TestDebeziumCdcSourceCompletion` `@Disabled` 缺 disposition（Stage 17 test-effectiveness 亦关注）；(6) exactly-once 标签（`TWO_PHASE_COMMIT` / `REPLAYABLE`）与真实行为一致性缺显式裁定；(7) **strict/effectively/at-least-once tier label validation 缺显式裁定**——`ProcessingGuarantee` + `StreamRequirementValidator.validateConnectorConsistency` 未在 connector external-effect 视角验证。

## Goals

- 产出一份 **JDBC/file/CDC connector external-effect 支持/拒绝矩阵**（per-connector sink/source × 声明保证 × commit/abort/retry/idempotency/offset-restore × lane），每能力一条 evidence row，`environment_class` 据 frozen lane 词表裁定（embedded H2/mock → T1 `in-process`；真实 PostgreSQL/real-CDC → `blocked`）。
- 为 **JDBC sink 2PC external-effect** 产出 evidence row：commit（data+ledger atomic single txn `:213-265`）、idempotent ledger guard（`:219-225`）、abort（`:276-280`）、retry/recover（saveState-first `:170-182` + base `restoreFromEpoch` durable re-commit）。`positive_proof` 引用 `TestJdbcTwoPhaseCommitSinkSkeleton`/`TestJdbcTwoPhaseCommitSinkDeep` 对应方法（embedded H2）。
- 为 **file sink 2PC external-effect** 产出 evidence row：atomic rename + manifest（`:150-197`）、final-exists/manifest-missing edge repair（`:174-181`）、abort 删 temp（`:207-213`）。`positive_proof` 引用 `TestFileTwoPhaseCommitSink`/`TestFileSourceCheckpointRestore`。
- 为 **CDC source offset restore** 产出 evidence row（端到端）：`snapshotState` → `initializeState` round-trip（`:212-258`）；`positive_proof` 引用 `TestDebeziumCdcCheckpoint`（mocked offset round-trip）；fail-fast 非 Map state（`:246-250`）rejection 证据。
- 产出 **M7-2-P0-2 复验** evidence row（sink external-effect 视角）：`source_anchor` 指向 `TwoPhaseCommitSinkFunction.java:153-196`（durable re-commit / non-durable abort）；`finding_id: M7-2-P0-2`；`disposition: e2e-proved`（FIXED）+ cross-ref Stage 19 formal disposition owner。
- 产出 **external-system 限制** evidence row：PostgreSQL sink `disposition: blocked` + cross-ref T5 `@@LANE`；real-CDC `disposition: blocked` + cross-ref T6 `@@LANE` + Rule S5-1（embedded H2 / mocked offset 非 PostgreSQL/real-CDC 冒充）。
- 产出 **exactly-once 标签裁定** evidence row：`TWO_PHASE_COMMIT`（JDBC/file sink）与 `REPLAYABLE`（CDC source）标签与真实行为一致——2PC sink 须验证 ledger/manifest idempotency 真能防 dup，CDC 须验证 offset restore 真能 resume no-dup。
- 产出 **strict/effectively/at-least-once tier label validation** evidence row（roadmap 命名交付物）：验证 JDBC/file（`TWO_PHASE_COMMIT`）+ CDC（`REPLAYABLE`）connector 组合在 `ProcessingGuarantee.STRICT_EXACTLY_ONCE` tier 下被 `StreamRequirementValidator.validateConnectorConsistency`（`:84-114`）正确接受（source ≥ REPLAYABLE + sink ≥ TWO_PHASE_COMMIT）；裁定 EFFECTIVELY_ONCE / AT_LEAST_ONCE tier 对本域 connector（均声明最强标签）不产生额外外部效果差异，显式 cross-ref `StreamRequirementValidator`。
- 对 **`TestDebeziumCdcSourceCompletion` `@Disabled`** 记录 disposition（cross-ref Stage 17 test-effectiveness；本计划记录其 disabled 状态与 reason，不修复）。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- batch/message connector 能力（`MessageSourceFunction`/`BatchConsumerSinkFunction` 等）——属 Stage 15。
- checkpoint coordinator barrier 语义 / state backend 编码 / savepoint rescale——属 Stage 9/10（本计划只验证 sink external-effect 与 offset restore，引用 Stage 9 checkpoint 结论）。
- data-plane transport（record/barrier/watermark 传输）——属 Stage 14。
- 修复本审计发现的 confirmed live defect（指派 remediation plan）。
- provision PostgreSQL / real CDC source DB（gate 未解除前如实 `blocked`）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-16-jdbc-file-cdc-connector.evidence.md`（domain evidence rows，manifest 域 `e`（4 connector 模块 main java）+ 域 `a`（public types，含 `TwoPhaseCommitSinkFunction` base）+ 域 `g`（test lane）范围内的 JDBC/file sink 2PC + CDC source offset + external-system 限制）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- JDBC/file/CDC external-effect 支持/拒绝矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- batch/message connector（Stage 15）。
- checkpoint coordinator barrier 语义 / state backend 编码（Stage 9/10）。
- data-plane transport（Stage 14）。
- 修复 confirmed live defect。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。
- provision 外部 PostgreSQL / real CDC source DB。

## Execution Plan

### Phase 1 - JDBC Two-Phase-Commit Sink External-Effect Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-16-jdbc-file-cdc-connector.evidence.md`

- Item Types: `Proof`

- [ ] 产出 JDBC commit atomicity evidence row：`source_anchor` 指向 `JdbcTwoPhaseCommitSink.java:191-266`（commit，data+ledger 同一 `connection.commit()`）；`implementation_anchor` `:228-235`；`positive_proof` 引用 `TestJdbcTwoPhaseCommitSinkSkeleton`/`TestJdbcTwoPhaseCommitSinkDeep` commit 方法（embedded H2）；`runtime_wiring: wired`；`environment_class: in-process`（T1）；`required_lane: in-process`。
- [ ] 产出 JDBC idempotent ledger guard evidence row：`source_anchor` 指向 `:219-225`（ledgerExists skip data write）+ `:340-347`（ledgerExists SQL）；`positive_proof` 引用验证 recover-safe re-commit / no-dup 的测试方法；`disposition` 据 in-process 裁定。
- [ ] 产出 JDBC saveState-first / recover evidence row：`source_anchor` 指向 `:170-182`（saveState override，buffer 入 pending BEFORE super）；`positive_proof` 引用验证 saveState-first 不 lag-by-one-epoch 的测试方法；`disposition` 据 in-process 裁定。
- [ ] 产出 JDBC abort/rollback evidence row：`source_anchor` 指向 `:276-280`（abort discard pending）+ `:269-273`（rollback clear buffer）+ `:246-252`（commit failure rollback）；`positive_proof` 引用验证 abort 路径的测试方法；`disposition` 据 in-process 裁定。
- [ ] 产出 JDBC TWO_PHASE_COMMIT label evidence row：`source_anchor` 指向 `:283-285`；`disposition` 据标签与真实行为一致性裁定（ledger idempotency 真能防 dup）。
- [ ] 冻结 **JDBC sink external-effect 支持/拒绝矩阵**文本（写入证据文件头部）。

Exit Criteria:

- [ ] ≥4 条 JDBC external-effect evidence row，Stage 16 文件本身被 `check-nop-stream-audit-manifest.mjs evidence --strict` 解析到 ≥4 行 `@@EVIDENCE`（exit 0；注意：validator 跨全审计目录计行，须单独确认 Stage 16 文件非空——不得靠其他 stage 的行空过）
- [ ] **端到端验证（Rule #22）**：至少一条 row 的 `positive_proof` 是真实 in-process 实跑测试名（`ClassName#method`，embedded H2），`environment_class >= in-process`，`disposition` 合理；不得用 metadata-only 测试充数
- [ ] **接线验证（Rule #23）**：JDBC commit row 的 `runtime_wiring` 据 in-process 实跑裁定（`saveState` → pendingCommits → `commit` → data+ledger atomic → `pendingCommits.remove` 确实连通），非仅方法存在
- [ ] **无静默跳过（Rule #24）**：JDBC idempotency/recover 若有未覆盖路径，row `disposition` 标 `unverified`/`residual-risk` + 注明（不得静默当 `e2e-proved`）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - File Two-Phase-Commit Sink External-Effect Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-16-jdbc-file-cdc-connector.evidence.md`

- Item Types: `Proof`

- [ ] 产出 file commit atomic-rename + manifest evidence row：`source_anchor` 指向 `FileTwoPhaseCommitSink.java:150-197`（commit，ATOMIC_MOVE + manifest）；`implementation_anchor` `:184-194`；`positive_proof` 引用 `TestFileTwoPhaseCommitSink` commit 方法；`runtime_wiring: wired`；`environment_class` 据 in-process 裁定。
- [ ] 产出 file final-exists/manifest-missing edge repair evidence row：`source_anchor` 指向 `:174-181`（edge repair，补 manifest skip rename）；`positive_proof` 引用验证 edge-case 的测试方法（若有；否则标 gap）。
- [ ] 产出 file saveState-first / abort evidence row：`source_anchor` 指向 `:130-142`（saveState-first temp file）+ `:207-213`（abort 删 temp）；`positive_proof` 引用 `TestFileSourceCheckpointRestore` 对应方法。
- [ ] 产出 file atomic manifest update evidence row：`source_anchor` 指向 `:237-256`（updateManifestAtomically，temp manifest + ATOMIC_MOVE + REPLACE_EXISTING）；`positive_proof` 引用验证 manifest atomicity 的测试方法。
- [ ] 产出 file TWO_PHASE_COMMIT label evidence row：`source_anchor` 指向 `:109-111`；`disposition` 据标签与真实行为一致性裁定（manifest idempotency 真能防 dup）。
- [ ] 冻结 **file sink external-effect 支持/拒绝矩阵**文本（写入证据文件头部）。

Exit Criteria:

- [ ] ≥4 条 file external-effect evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [ ] **端到端验证（Rule #22）**：file commit row 的 `positive_proof` 引用真实 in-process 实跑测试名，`environment_class >= in-process`
- [ ] **接线验证（Rule #23）**：file commit row 的 `runtime_wiring` 据 in-process 实跑裁定（`saveState` temp file → pending → `commit` ATOMIC_MOVE → manifest → pending remove 确实连通）
- [ ] **无静默跳过（Rule #24）**：edge-repair 若无专门测试须标 `residual-risk`/`unverified` + 注明 gap（不得静默当 `e2e-proved`）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - CDC Source Offset Restore, Backend Limitation & Historical P0 Revalidation Evidence

Status: planned
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-16-jdbc-file-cdc-connector.evidence.md`

- Item Types: `Proof | Decision`

- [ ] 产出 CDC offset snapshot/restore round-trip evidence row（端到端）：`source_anchor` 指向 `DebeziumCdcSourceFunction.java:212-227`（snapshotState）+ `:231-258`（initializeState restore）；`positive_proof` 引用 `TestDebeziumCdcCheckpoint`（mocked offset round-trip，7 @Test）；`environment_class: in-process`（T1 mocked）；`required_lane: in-process`；`disposition` 据 mocked lane 裁定。
- [ ] 产出 CDC fail-fast non-Map state evidence row：`source_anchor` 指向 `:246-250`（非 Map 抛 StreamException）；`rejection_proof` 引用验证 fail-fast 的测试方法（若有；否则标 gap）。
- [ ] 产出 CDC REPLAYABLE label + drain evidence row：`source_anchor` 指向 `:185-187`（REPLAYABLE）+ `:190-203`（truncateForDrain）；`disposition` 据标签与真实行为一致性裁定（offset restore 真能 resume no-dup）。
- [ ] 产出 M7-2-P0-2 复验 evidence row（sink external-effect 视角）：`source_anchor` 指向 `TwoPhaseCommitSinkFunction.java:153-196`（durable re-commit `:169-181` / non-durable abort `:183-193`）；`finding_id: M7-2-P0-2`；`environment_class: unit`、`required_lane: unit`（restoreFromEpoch 是组件级不变量，`TestTwoPhaseCommitSinkFunction` 为单类无 operator-chain unit 测试）；`disposition` 据 lane 裁定（`e2e-proved` 仅当 environment_class ≥ required_lane，否则 `component-only`）；`declared_guarantee` 写明 "sink-external-effect revalidation (formal disposition owned by Stage 19)"；cross-ref Stage 19 formal disposition owner（shard 19 core/state/window）。
- [ ] 产出 strict/effectively/at-least-once tier label validation evidence row：`source_anchor` 指向 `StreamRequirementValidator.java:84-114`（validateConnectorConsistency）+ `ProcessingGuarantee.java:10-14`；`positive_proof` 引用覆盖 STRICT_EXACTLY_ONCE 接受 JDBC/file sink + CDC source 的测试方法（若有；否则 `manual-trace:StreamRequirementValidator.java:84-114`）；`disposition` 据 in-process 裁定；裁定 EFFECTIVELY_ONCE/AT_LEAST_ONCE tier 对本域 connector non-applicable（均声明最强标签）并 cross-ref。
- [ ] 产出 external-system 限制 evidence row：PostgreSQL sink `disposition: blocked` + cross-ref T5 `@@LANE` `blocked`（no gated PostgreSQL test，JDBC tests 用 embedded H2 → T1）；real-CDC `disposition: blocked` + cross-ref T6 `@@LANE` `blocked`（only mocked offset test → T1）+ Rule S5-1（embedded/mock 非 PostgreSQL/real-CDC 冒充）。
- [ ] 记录 `TestDebeziumCdcSourceCompletion` `@Disabled` disposition：注明 disabled reason（"Genuinely broken: run() loops until cancel()"），cross-ref Stage 17 test-effectiveness successor；不在本计划修复。
- [ ] 全 evidence 文件回归校验 + corpus 交叉标注核对 + JDBC/file/CDC 矩阵最终冻结。

Exit Criteria:

- [ ] ≥6 条 CDC/backend/M7-2-P0-2/tier-label evidence row，Stage 16 文件本身被 validator 解析到 ≥6 行 `@@EVIDENCE`（exit 0，非靠其他 stage 空过）
- [ ] **端到端验证（Rule #22）**：CDC offset round-trip row 的 `positive_proof` 引用真实 in-process 实跑测试（`TestDebeziumCdcCheckpoint` mocked offset round-trip），`environment_class >= in-process`（mocked → T1）
- [ ] **接线验证（Rule #23）**：CDC row 的 `runtime_wiring` 据 in-process 实跑裁定（`snapshotState` CDC_OFFSETS_KEY → `initializeState` fromSerializable → setOffsets → engine resume 确实连通）
- [ ] **无静默跳过（Rule #24）**：external-system PostgreSQL/real-CDC 不得静默 `e2e-proved`——须 `blocked` + cross-ref T5/T6 + Rule S5-1；`TestDebeziumCdcSourceCompletion` `@Disabled` 须显式记录（不得默默跳过）；M7-2-P0-2 FIXED 须有复验（不得静默当已修复而不验证）；CDC fail-fast 若无 rejection 测试须标 gap
- [ ] external-system blocked row 命名 unqualified lane（T5/T6）+ Rule S5-1 引用
- [ ] JDBC/file/CDC external-effect 支持/拒绝矩阵在证据文件头部有显式文本
- [ ] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 全部合法（ID 在 frozen corpus 内或 `none`）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [ ] JDBC sink 2PC external-effect（commit atomicity、idempotent ledger、saveState-first、abort/rollback）各有 evidence row（embedded H2 in-process 实跑或如实标注缺覆盖）
- [ ] file sink 2PC external-effect（atomic-rename+manifest、edge repair、saveState-first/abort、atomic manifest）各有 evidence row
- [ ] CDC source offset snapshot/restore round-trip 有端到端 evidence row（mocked in-process 实跑）
- [ ] external-system PostgreSQL/real-CDC 真实集成如实 `blocked`（cross-ref T5/T6 + Rule S5-1），无静默冒充
- [ ] exactly-once 标签（`TWO_PHASE_COMMIT` / `REPLAYABLE`）与真实行为一致性有显式裁定
- [ ] strict/effectively/at-least-once tier label validation 有显式 evidence row（`ProcessingGuarantee` + `StreamRequirementValidator.validateConnectorConsistency`）
- [ ] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**（Stage 16 文件本身被解析到 ≥1 行）
- [ ] inventory_id 用 `EVID-S16-NNN` 前缀（全局唯一）
- [ ] M7-2-P0-2 restoreFromEpoch 有 sink-external-effect 视角复验 evidence row（FIXED）+ cross-ref Stage 19
- [ ] `TestDebeziumCdcSourceCompletion` `@Disabled` 有显式 disposition 记录
- [ ] 支持/拒绝矩阵显式成文
- [ ] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**
- [ ] 不存在被静默降级到 deferred 的 in-scope 审计项（external-system blocked 为合法终态；edge-repair/fail-fast gap 标 `residual-risk`/`unverified`；均为合法终态）
- [ ] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [ ] `No owner-doc update required`（不改 `docs-for-ai/`）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）in-process row 的 `positive_proof` 确为实跑测试名（非 metadata-only 充数），（b）`runtime_wiring=wired` 确经接线验证，（c）external-system blocked 无静默放行，（d）exactly-once 标签裁定无虚标，（e）CDC offset round-trip 路径连通
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0（Minimum Rule #26）

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：external-system PostgreSQL/real-CDC 因 T5/T6 lane `blocked`——此类 row 标 `disposition: blocked` + cross-ref T5/T6 + Rule S5-1，是本计划合法终态并由 blocked-gate 规则承担后果（block Stage 23 ready verdict），非 deferred。CDC fail-fast rejection 测试缺覆盖——标 `residual-risk` + Stage 17 successor。file edge-repair 专门测试缺覆盖——标 `residual-risk` + Stage 17 successor。`TestDebeziumCdcSourceCompletion` `@Disabled`——记录 disabled reason + cross-ref Stage 17，不在本计划修复。confirmed still-live defect 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- CDC fail-fast non-Map state rejection 测试覆盖 → Stage 17（test effectiveness）successor。
- file sink final-exists/manifest-missing edge-repair 专门测试 → Stage 17 successor。
- `TestDebeziumCdcSourceCompletion` `@Disabled`（genuinely broken run() loop）修复 → successor remediation plan 或 Stage 17。
- provision PostgreSQL / real CDC source DB 后 external-system row 从 `blocked` 升级 → connector successor plan（rerun T5/T6 lane）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
