# T2 Capability-Gap Evidence Reclassification & Readiness Re-Decision (Successor Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-09
> Draft Review: independent sub-agent adversarial review passed (2 rounds; round-1 found 1 Blocker [unjustified roadmap item-1 flip citing draft-review session as closure evidence] + 2 Majors [omitted residual rows EVID-S14-015/S13-021; stale matrix/narrative surface] + 1 Major [exactly-once wording overstatement] — all fixed; round-2 CONSENSUS REACHED, APPROVED, 0 Blockers / 0 Majors, 1 Minor [readiness report §3:73 theme narrative] addressed). Sessions ses_01a5090b4ffedZzaXrLlL6GjYB (round-1) + ses_01a482f8bffeVmgjzmpvArrkF5 (round-2).
> Mission: nop-stream-independent-audit
> Work Item: T2 capability-gap evidence reclassification + readiness re-decision (discharges the deferred successor-audit work recorded in plan `2026-08-09-1252-1-multi-jvm-capability-gap-remediation.md` Non-Blocking Follow-ups, and in Stage 23 readiness report §6 "Blockers to Blanket-Ready" item 2)
> Source: `ai-dev/plans/nop-stream-independent-audit/2026-08-09-1252-1-multi-jvm-capability-gap-remediation.md` (completed; Closure Audit Evidence ses_01a5ee90cffeo3m9d1Yv6fP07X); `ai-dev/audits/nop-stream-independent-audit/stage-23-readiness-report.md` §2b + §6; `stage-13-control-plane-ha-fencing.evidence.md`; `stage-14-data-plane-multi-jvm-recovery.evidence.md`
> Related: Successor to remediation plan `2026-08-09-1252-1` (code fix) and to readiness-decision plan `2026-08-09-1253-1` (frozen bounded decision). This plan is audit/doc-only: it consumes the now-proven T2 capability and re-derives the readiness decision.

## Purpose

把独立审计的冻结证据语料与刚完成的 T2 multi-JVM 修复对齐：将 4 条因 capability-gap 缺陷而 `blocked` 的证据行（EVID-S13-015、EVID-S13-016、EVID-S14-013、EVID-S14-014）依现在 PASS 的 T2 lane 测试重新分类为 `e2e-proved`（或诚实的更弱分类），连带重评引用 §2b 阻断作为 residual 理由的 cross-JVM residual-risk 行，并同步消除两份 evidence 文件中与重分类矛盾的叙述性表述（matrix 行、cross-reference notes、coverage gaps），重跑 readiness gate，并据此更新 Stage 23 readiness report 的有界判定与 roadmap ★ 状态。本计划不改 nop-stream 生产代码（修复已在 `2026-08-09-1252-1` 落地）；它是审计语料的事实对齐与判定更新。**本计划不 flip roadmap item 1**（其 `done` 前置为独立 closure-audit，见 Non-Goals）。

## Current Baseline

经 2026-08-09 live repo + 冻结证据语料核对（锚点均已二次确认）：

### 修复已落地且经独立 closure-audit（前置条件已满足）

- Plan `2026-08-09-1252-1`（`Plan Status: completed`，Closure Audit Evidence `ses_01a5ee90cffeo3m9d1Yv6fP07X`，`Overall verdict: CLOSURE APPROVED`）。两个 T2 multi-JVM 测试在 T2 lane（gate on）全方法 PASS：
  - `TestMultiJvmExactlyOnceRecovery`：`Tests run: 1, Failures: 0, Errors: 0`（elapsed 67.02s）。
  - `TestMultiJvmCoordinatorFailover`：`Tests run: 2, Failures: 0, Errors: 0`（elapsed 8.293s）。
- 产品代码根因修复：`AbstractPollingLeaderElector.scheduleCheck()` `TimeUnit.MICROSECONDS` → `MILLISECONDS`（`nop-cluster/nop-cluster-core`，`AbstractPollingLeaderElector.java:28`）。test harness 修复：`COORDINATOR_LABEL = "coordinator-0"` 常量化 + coordinator-0 leadership 前置确认。回归测试：`TestJdbcLeaderElector` 新增 `testPollingCadenceIsMillisecondsNotMicroseconds` + `testMultiThreadedExecutorTakeoverGuardsConcurrentCheckElection`。

### 4 条 §2b capability-gap 证据行仍为 `blocked`（事实滞后）

冻结的 `*.evidence.md` 中这 4 行仍描述已修复的缺陷并标 `disposition: blocked`，与 live baseline 冲突：

- `stage-13-control-plane-ha-fencing.evidence.md:255` `EVID-S13-015`：`disposition: blocked`，`positive_proof: none`，`environment_class: none`，`runtime_wiring: unwired`，`declared_guarantee` 仍描述 `TestMultiJvmExactlyOnceRecovery` 读裸 `"coordinator"` log label。
- `stage-13-control-plane-ha-fencing.evidence.md:269` `EVID-S13-016`：`disposition: blocked`，`positive_proof: none`，`environment_class: none`，`runtime_wiring: unwired`，`declared_guarantee` 仍描述 `TestMultiJvmCoordinatorFailover:129` takeover 断言失败。
- `stage-14-data-plane-multi-jvm-recovery.evidence.md:240` `EVID-S14-013`：同 `EVID-S13-015`（cross-ref），`disposition: blocked`，`positive_proof: none`。
- `stage-14-data-plane-multi-jvm-recovery.evidence.md:254` `EVID-S14-014`：同 `EVID-S13-016`（cross-ref），`disposition: blocked`，`positive_proof: none`。

### 关联的 cross-JVM residual-risk 行（可能随 §2b 解除而升级或需更新 rationale）

Stage 13/14 中若干 `residual-risk` 行的 residual 理由直接引用了 §2b 阻断。经逐行核对 evidence 文件 `declared_guarantee` 文本，确认 **10 行**需在本计划重评（readiness report §3 的 47 行扁平清单是超集，以下为其中 rationale 直接依赖 §2b 的子集）：

- Stage 13：`EVID-S13-012`（globalRecovery distributed mutex residual，引用 EVID-S13-015）、`EVID-S13-013`（zombie task cross-JVM，引用 EVID-S13-016）、`EVID-S13-017`、`EVID-S13-019`、`EVID-S13-021`（`:339-349` declared_guarantee 明示 "DISTRIBUTED recovery via JobCoordinator.globalRecovery (cross-JVM reassignment) needs the multi-jvm lane"）。
- Stage 14：`EVID-S14-015`（`:268-279` declared_guarantee 明示 "true process-boundary recovery ... is blocked by the two T2 deeper defects (EVID-S14-013/014), so the boundary as a recovery claim is a residual-risk, NOT e2e-proved"）、`EVID-S14-016`、`EVID-S14-017`、`EVID-S14-020`、`EVID-S14-021`。

这 10 行需逐条重评：若现在 PASS 的 T2 测试恰好提供了其所缺的 cross-JVM 证明，则升级为 `e2e-proved`；否则保留 `residual-risk` 并更新 rationale（注明 §2b 已解除但该行所断言的更强/更窄的分布式不变量仍未被当前测试直接覆盖）。**EVID-S14-015 / EVID-S13-021 当前 `declared_guarantee` 明文断言"被 EVID-S14-013/014 阻断 / 需 multi-jvm lane"——重分类后这两行的 rationale 文本必然失效，必须更新（不可静默保留陈旧 rationale）。**

### 叙述性表述的陈旧面（matrix / cross-reference notes）

两份 evidence 文件除 `@@EVIDENCE` 行外，还含多处叙述性表述直接断言 §2b 缺陷/blocked 状态。validator 仅解析 `@@EVIDENCE` 行，但这些叙述若不更新会与重分类后的行自相矛盾（closure audit "逐行可核对" 会失败）。需在本计划同步消除的陈旧叙述位置（已核对行号）：

- `stage-13...evidence.md`：`:5` lane-policy 头部（"its two deeper capability tests have defects owned here as `blocked`"）、`:24` matrix **C6**（"PARTIALLY SUPPORTED — deeper capability BLOCKED"）、`:35` matrix **F6**（"deeper capability has known defect"）、`:356-358` cross-reference notes、`:370`、`:378` coverage gaps。
- `stage-14...evidence.md`：`:5` lane-policy 头部、`:43` matrix **M2**（"BLOCKED — deeper test defect"）、`:44` matrix **M3**、`:57`、`:371-372`、`:389`、`:393`。

### Readiness 决策当前状态（事实滞后）

- `stage-23-readiness-report.md` §1 聚合：219 row，`e2e-proved` 126 / `blocked` 8 / `residual-risk` 47。
- §2 blocked = §2a（4 lane-blocked：EVID-S14-009/010、EVID-S15-011、EVID-S16-016，外部 backend 不可用）+ §2b（4 capability-gap：EVID-S13-015/016、EVID-S14-013/014）。
- §6 判定：`ready only for enumerated e2e-proved capability/environment pairs`（因 8 blocked 禁止 blanket ready）。
- validator `node ai-dev/tools/check-nop-stream-audit-manifest.mjs readiness` 从 live 冻结文件实时聚合计数（不硬编码 plan 估计），故重分类后计数会自动变化。

### Roadmap 状态

- `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` Work Items：item 1（Coordinator、运行时并发与恢复缺陷收口）仍标 `planned`。其 referenced production plan `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` 已 `completed`，但该 plan 的 `Closure Audit Evidence`（`:161-171`）是一次 **EXECUTE self-pass**，明示 "独立 closure-audit（roadmap work-item-1 `done` flip 的前置）属后续 CLOSURE_VERIFY mission step，非 EXECUTE 范围"，Follow-up（`:175`）同样把 flip 列为 deferred CLOSURE_VERIFY step。因此 roadmap 规则 "done only after independent closure-audit evidence" 的前置**尚未满足**，本计划**不 flip item 1**（见 Non-Goals）。item 1 的 flip 由独立的 CLOSURE_VERIFY 步骤承载，不属本审计重分类。
- ★ production-readiness 判定行需随本计划 readiness 重决策更新。

### 工具与冻结约束

- `ai-dev/tools/check-nop-stream-audit-manifest.mjs` 子命令 `evidence | readiness | all | self-test`（已有，本计划复用）。
- `evidence-schema.md` 冻结 11 字段 + 7-value 词表（`e2e-proved|component-only|unverified|fail-fast|non-goal|residual-risk|blocked`）。本计划只改 `*.evidence.md` 中具体行的字段值与 readiness report，不改 schema 词表语义。
- 两份 evidence 文件的顶层头为 `> Status: produced by Stage 13/14 audit`，其 matrix 子标题与 readiness report / schema 标注 "frozen"。Stage 23 readiness report §6（`:138-143`）明确把 §2b 列为"必须 resolve 并 re-audit 才能 blanket ready"的 blocker——即设计上就预期 §2b 解除后会有一次 re-audit（本计划即该 re-audit）；schema Rule S5-2 允许在 frozen 7-value 词表内重新分类。`2026-08-09-1252-1` Non-Goals 与 Non-Blocking Follow-ups 均把 evidence 重分类 + readiness 重跑显式指派给 successor audit。故本计划在词表内编辑 frozen evidence 行不违反冻结约束。

## Goals

- 依现在 PASS 的 T2 lane 测试，将 4 条 §2b capability-gap 证据行重新分类为 `e2e-proved`（或经裁定后诚实的更弱分类），每行更新 `disposition`/`positive_proof`/`implementation_anchor`/`runtime_wiring`/`environment_class`/`declared_guarantee`/`rejection_proof` 使其与 live baseline 一致。
- 重评 10 条引用 §2b 阻断作为 residual 理由的 cross-JVM residual-risk 行（含 EVID-S14-015、EVID-S13-021 这两条 rationale 文本必然失效的行），逐条裁定升级或保留（附 rationale）。
- 消除两份 evidence 文件中与重分类矛盾的叙述性表述（matrix 行 C6/F6/M2/M3、lane-policy 头部、cross-reference notes、coverage gaps），使文件内部自洽。
- 重跑 readiness gate（`readiness` validator），更新 `stage-23-readiness-report.md` 的聚合计数（含 §1 environment-class 子表与 §4 per-stage 算术）与有界判定（§2b blocker 类解除；§2a 仍存故仍 bounded，不达 blanket ready）。
- 更新 roadmap ★ 判定状态随 readiness 重决策更新。

## Non-Goals

- 修复 nop-stream 生产代码（已由 `2026-08-09-1252-1` 完成；本计划仅消费其能力证明）。
- **flip roadmap item 1（Coordinator、运行时并发与恢复缺陷收口）为 `done`**：其 `done` 前置为独立 closure-audit evidence，而 production plan `2026-08-04-2300-1` 的 closure evidence 是 EXECUTE self-pass 且明示 deferred 到 CLOSURE_VERIFY mission step。item 1 的 flip 由独立 CLOSURE_VERIFY 步骤承载，**不属本审计重分类**。
- §2a 的 4 行 lane-blocked（Kafka/Pulsar/PostgreSQL/Debezium 外部 backend 供应）：基础设施供应，out of audit scope。故本计划**不**追求 blanket `ready` 判定——§2a 仍存，blanket ready 仍被 readiness gate 禁止。
- 新增 evidence row / finding 裁决（Stages 6-22 已完成；本计划只重分类已存在行 + 重聚合）。
- 修改冻结 schema 词表语义（`evidence-schema.md` 的 7-value 词表不变；本计划只改具体行字段值）。
- `TestMultiJvmExactlyOnceRecovery` Javadoc 标注的 "Stage 43+ follow-up"（完整 source→keyBy→sink cross-JVM 共享 sink exactly-once 断言）：独立功能增强，不属本审计重分类。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`（重分类 EVID-S13-015、EVID-S13-016；重评 EVID-S13-012/013/017/019/021；消除 §"叙述性表述的陈旧面" 中 stage-13 的 matrix C6/F6、lane-policy 头部、cross-reference notes、coverage gaps）。
- `ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`（重分类 EVID-S14-013、EVID-S14-014；重评 EVID-S14-015/016/017/020/021；消除 stage-14 的 matrix M2/M3、lane-policy 头部、cross-reference notes、coverage gaps）。
- `ai-dev/audits/nop-stream-independent-audit/stage-23-readiness-report.md`（重跑聚合计数 + §1 environment-class 子表 + §4 per-stage 算术 + 更新有界判定）。
- `ai-dev/backlog/nop-stream-independent-audit-roadmap.md`（★ 判定状态更新；**不改 item 1 状态**）。
- 在 T2 lane 独立重跑两个 multi-JVM 测试，产出本审计自有的 fresh PASS 证据（不直接转述 remediation plan 的断言），并将 surefire 输出记录到本 plan `Closure` 段与对应 `ai-dev/logs/` 条目。

### Out Of Scope

- 任何 nop-stream 生产代码变更。
- §2a lane provisioning / 外部 backend 供应。
- 新增 evidence row 或 finding 裁决。
- blanket `ready` 判定（被 §2a 阻止）。
- roadmap item 1 的 `planned`→`done` flip（独立 CLOSURE_VERIFY 步骤承载）。

## Execution Plan

### Phase 1 - Reclassify the 4 §2b capability-gap evidence rows (blocked → e2e-proved)

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`（EVID-S13-015、EVID-S13-016）；`ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`（EVID-S14-013、EVID-S14-014）

- Item Types: `Proof | Decision`

- [x] 在 T2 lane（gate on）独立重跑两个 multi-JVM 测试，产出本审计自有的 fresh PASS 证据：
  - `./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmExactlyOnceRecovery -Dsurefire.failIfNoSpecifiedTests=false`
  - `./mvnw test -pl nop-stream/nop-stream-runtime -Dnop.stream.test.multi-jvm.enabled=true -Dtest=TestMultiJvmCoordinatorFailover -Dsurefire.failIfNoSpecifiedTests=false`
  - 将每次的 `Tests run / Failures / Errors / elapsed` surefire 摘要行**写入本 plan 的 `Closure` 段与 `ai-dev/logs/` 对应日期条目**（命名 fresh-run artifact 的持久位置，供 closure audit 核对，符合 Gated-Evidence Rule S5-1）。
  - 若任何方法非 PASS：停止本 Phase 重分类，把对应行保留 `blocked` 并更新 `declared_guarantee` 记录 live blocker 成因，Phase 2 的 blocked 计数预期相应调整（见 Phase 2 contingency 说明）。
- [x] 重分类 `EVID-S13-015` / `EVID-S14-013`（TestMultiJvmExactlyOnceRecovery log-label defect）：`disposition: blocked` → `e2e-proved`；`positive_proof` 填入本审计 fresh 重跑的方法名（`multiJvmDeployKillRecoverFencing`）+ PASS 摘要（断言 initial epoch > 0、kill/restart 后 recovered epoch 严格 > initial、recovered assignment count >= 2、coordinator 日志含 recovery 事件）；`environment_class: multi-jvm`；`runtime_wiring: wired`（真实 spawn 2 TaskManager JVM + 1 coordinator JVM，经共享 H2 DB + JDBC lease 表 + PollingJdbcMessageService RPC 连通）；**`declared_guarantee` 精确措辞为已证明的 "cross-JVM recovery fencing/redeploy enabling infrastructure（cross-JVM deployTask RPC + recovery redeploy + fencing epoch 轮转）"——不写 "完整 cross-JVM exactly-once recovery"**（完整 source→keyBy→sink 共享 sink exactly-once 是 Stage 43 follow-up，见 `2026-08-09-1252-1` Non-Goals）；注明原 log-label defect 已由 `2026-08-09-1252-1` 的 `COORDINATOR_LABEL` 常量化修复；`rejection_proof` 维持 `none`（与语料中既有 e2e-proved 行如 EVID-S13-014 的 convention 一致；validator 不强制 e2e-proved 行必填 rejection_proof）。
- [x] 重分类 `EVID-S13-016` / `EVID-S14-014`（TestMultiJvmCoordinatorFailover HA-fencing takeover）：`disposition: blocked` → `e2e-proved`；`positive_proof` 填入 `testCoordinatorKillTriggersStandbyTakeover` + `testBrainSplitFencingBoundary` 两方法的 fresh PASS 摘要（断言 lease `leader_id` 翻转为 coordinator-1 且 `leader_epoch` 严格递增）；`environment_class: multi-jvm`；`runtime_wiring: wired`（2 coordinator JVM 共享 JDBC lease 表协调 leadership）；`declared_guarantee` 更新为已证明的 cross-JVM HA-fencing takeover capability；注明原 `MICROSECONDS`→`MILLISECONDS` 根因已修复；`rejection_proof` 维持 `none`（同上 convention）。
- [x] **诚实门（强制）**：对上述每行，逐条核对"测试实际覆盖的 capability"是否窄于"行声明 capability"。若窄（如 EVID-S13-015 测试仅证明 enabling infrastructure 而非完整 exactly-once），则 `declared_guarantee` 必须如实收窄到测试覆盖面，不得用 `e2e-proved` 配一个过宽的 `declared_guarantee` 虚高分类。裁定理由写入该行 `declared_guarantee` 或 plan `Closure`。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 4 行（EVID-S13-015/016、EVID-S14-013/014）各有更新后的 `disposition`（非 `blocked`），且 `positive_proof`/`environment_class`/`runtime_wiring`/`declared_guarantee`/`rejection_proof` 与 live baseline 一致；逐行可在仓库中核对
- [x] `declared_guarantee` 措辞经诚实门核对：不宽于测试实际覆盖的 capability（无虚高 exactly-once 断言）
- [x] 本审计 fresh 重跑的 T2 lane 测试 surefire 摘要已写入本 plan `Closure` 段 + `ai-dev/logs/`（命名 artifact 持久位置，非转述 remediation plan 断言）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence` 退出码 0（重分类后行字段合法、无词表外值、字段依赖满足）
- [x] **无静默跳过（Rule #24）**：任何裁定为更弱分类或保留 blocked 的行附显式 rationale；不允许默默不更新 `declared_guarantee`
- [x] **接线验证（Rule #23）**：重分类行的 `positive_proof` 指向真实运行的测试方法（fresh 重跑 PASS），证明 coordinator JVM 与 TaskManager JVM 在运行时确实经 RPC/共享 lease 表连通——非仅类型存在
- [x] `No owner-doc update required`：本 Phase 只改审计 evidence 文件（事实对齐），不改 owner docs（`docs-for-ai/`、`ai-dev/design/`）的 contract 表述
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Re-evaluate cross-JVM residual rows + reconcile stale narrative + re-run readiness gate + update readiness report

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-13-control-plane-ha-fencing.evidence.md`（EVID-S13-012/013/017/019/021 + matrix/narrative）；`ai-dev/audits/nop-stream-independent-audit/stage-14-data-plane-multi-jvm-recovery.evidence.md`（EVID-S14-015/016/017/020/021 + matrix/narrative）；`ai-dev/audits/nop-stream-independent-audit/stage-23-readiness-report.md`；`ai-dev/backlog/nop-stream-independent-audit-roadmap.md`

- Item Types: `Decision | Proof | Fix`

- [x] 逐条重评 10 条 cross-JVM residual-risk 行（Stage 13: EVID-S13-012/013/017/019/021；Stage 14: EVID-S14-015/016/017/020/021）：对每行裁定——(a) 若现在 PASS 的 T2 测试恰好覆盖该行所断言的 cross-JVM 不变量，则升级为 `e2e-proved` 并更新 `positive_proof`；(b) 否则保留 `residual-risk` 并更新 rationale（注明 §2b 已解除，但该行所断言的更强/更窄分布式不变量未被当前测试直接覆盖）。每行裁定有显式理由。
  - **EVID-S14-015 / EVID-S13-021 必须更新**：其当前 `declared_guarantee` 明文断言"被 EVID-S14-013/014 阻断 / 需 multi-jvm lane"——Phase 1 重分类后该 rationale 失效，必须改写为新 rationale（升级或保留均需新理由），不得静默保留陈旧文本。
- [x] 消除两份 evidence 文件中与重分类矛盾的叙述性表述（matrix 行、lane-policy 头部、cross-reference notes、coverage gaps），使文件内部自洽。需更新的位置（行号已在 Current Baseline 核对）：stage-13 的 `:5`/`:24` matrix C6/`:35` matrix F6/`:356-358`/`:370`/`:378`；stage-14 的 `:5`/`:43` matrix M2/`:44` matrix M3/`:57`/`:371-372`/`:389`/`:393`。更新方式：把"BLOCKED / deeper defect / blocked by EVID-S14-013/014"等表述改为反映重分类后的状态（如 "e2e-proved on T2 lane (re-audited 2026-08-09-1300-1)"），或如该 matrix 项确属仍未覆盖的更窄能力则如实标注。
- [x] 同步更新 readiness report §3 的**主题叙述段**（`stage-23-readiness-report.md:73`，当前文为 "the true process-boundary segment is **blocked by the 4 T2 capability gaps (§2b)** — classified residual..."）：§2b 解除后该因果表述对保留 residual 的行不再成立，须改写为新因果（如 "T2 capability gaps (§2b) resolved 2026-08-09-1300-1; remaining residuals stay because the passing T2 tests do not directly prove this specific invariant"），避免 report 内部 §2（§2b 已解除）与 §3:73（归因 §2b）自相矛盾。
- [x] 重跑 readiness gate：`node ai-dev/tools/check-nop-stream-audit-manifest.mjs readiness`，记录新的聚合计数与判定输出。预期若 Phase 1 全部 4 行升级为 e2e-proved：`blocked` 由 8 → 4（仅 §2a lane-blocked），`e2e-proved` 相应上升。**contingency**：若 Phase 1 有行因诚实门保留 `blocked`，则 blocked 计数 >4，本项改为记录实际 blocked 行清单（逐行注明保留原因），判定仍 bounded。
- [x] 更新 `stage-23-readiness-report.md`：§1 主聚合表 + §1 "e2e-proved rows by environment class" 子表（multi-jvm 列由 1 上升）、§2 blocked 表（移除 §2b 4 行，仅留 §2a 4 行）、§3 residual-risk 行清单（移除已升级行 / 注明 rationale 更新）、§4 per-stage "minus N blocked" 算术 + e2e-proved 枚举（新增行）、§6 有界判定。判定仍为 `ready only for enumerated e2e-proved capability/environment pairs`（因 §2a 4 lane-blocked 仍存）——显式声明：§2b blocker 类已解除，§2a 仍为唯一剩余 blocker 类。
- [x] 更新 readiness report 头部 `Frozen at` 与状态注记（标注本次 re-audit 由 `2026-08-09-1300-1` 触发，消费 `2026-08-09-1252-1` 的修复）。
- [x] 更新 `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` ★ production-readiness 判定行为 re-audit 后的有界判定（注明 §2b 解除、§2a 仍存）。**不改 item 1 状态**（其 `done` 前置独立 closure-audit 未完成，见 Non-Goals）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 10 条 cross-JVM residual-risk 行各有裁定（升级或保留 + rationale），逐行可核对；EVID-S14-015 / EVID-S13-021 的陈旧 rationale 已改写
- [x] 两份 evidence 文件中 §"叙述性表述的陈旧面" 列出的 matrix/narrative 位置已更新，文件内部不再与重分类行矛盾（closure audit "逐行可核对" 成立）
- [x] readiness report §3:73 主题叙述段已改写（不再把 residual 归因于已解除的 §2b）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs readiness` 退出码 0，且判定与 report §6 一致；`blocked` 仅含 §2a 行（若 Phase 1 有行保留 blocked，则逐行注明保留原因，计数与之相符）
- [x] `stage-23-readiness-report.md` §1（含 environment-class 子表）/§2/§3/§4（含 per-stage 算术）/§6 与重跑聚合计数一致；判定显式声明 §2b 已解除、§2a 仍为唯一 blocker 类、blanket ready 仍被禁止（readiness gate 被遵守）
- [x] roadmap ★ 判定行已更新（与 readiness report 一致）；item 1 状态未被改动
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（report/roadmap/evidence 改动未引入断链）
- [x] **端到端验证（Rule #22）**：从修复（`2026-08-09-1252-1` PASS）→ 证据重分类（Phase 1）→ 关联 residual 重评 + 叙述对齐（Phase 2）→ readiness gate 重跑 → report/roadmap 更新，整条 re-audit 链可追溯
- [x] **无静默跳过（Rule #24）**：任何保留 `residual-risk`/`blocked` 的行附显式 rationale；任何陈旧叙述已更新或如实标注；不允许默默不更新
- [x] 若本 Phase 改变了 owner-doc baseline（roadmap ★ 判定状态）：`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档/审计计划**：本计划不改 nop-stream 生产代码（仅改审计 evidence/report 文件 + roadmap 状态）。`./mvnw test`/`./mvnw compile` 不强制作为 closure 证据；改为以 validator 退出码、T2 lane fresh 重跑结果与 doc-link 检查为 closure 证据。（T2 lane 重跑在 Phase 1 执行，作为本审计独立证据，非生产代码变更。）

- [x] 4 条 §2b capability-gap 证据行已依 fresh 重跑结果重分类（非 `blocked`），字段与 live baseline 一致；`declared_guarantee` 经诚实门核对不宽于测试覆盖面
- [x] 10 条关联 cross-JVM residual-risk 行已逐条裁定（升级或保留 + rationale）；EVID-S14-015 / EVID-S13-021 陈旧 rationale 已改写
- [x] 两份 evidence 文件的 matrix/narrative 陈旧面已对齐（文件内部自洽，无 "BLOCKED" 与 e2e-proved 自相矛盾）
- [x] readiness gate 重跑：`blocked` 仅含 §2a 行（若 Phase 1 有行保留 blocked 则逐行注明保留原因，计数相符），判定仍 bounded 且显式声明 §2b 已解除
- [x] `stage-23-readiness-report.md`（含 §1 environment-class 子表与 §4 per-stage 算术）与重跑聚合计数一致
- [x] roadmap ★ 判定行已更新；**item 1 状态未被改动**（其 `done` 前置独立 closure-audit 未完成）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（§2a 显式 out-of-scope；item-1 flip 显式 out-of-scope 归独立 CLOSURE_VERIFY，非降级）
- [x] 受影响的 owner docs：`No owner-doc update required`（本计划只改审计 evidence/report + roadmap ★ 状态，不改 `docs-for-ai/`/`ai-dev/design/` contract 表述）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）重分类行的 `positive_proof` 指向本审计 fresh 重跑的真实 PASS 测试（非转述、非空壳）；（b）readiness 判定与重跑聚合的 blocked/e2e-proved 分布一致（非凭空判定）；（c）residual 重评每行有可核对的 rationale；（d）matrix/narrative 与 `@@EVIDENCE` 行一致
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（执行中如出现经裁定的 non-blocking residual 再记入此处。预期无延期——§2a lane provisioning 显式 out-of-scope，非本计划降级项。）

## Non-Blocking Follow-ups

- **roadmap item 1（Coordinator、运行时并发与恢复缺陷收口）`planned`→`done` flip**：production plan `2026-08-04-2300-1` 已 `completed` 但其 closure evidence 是 EXECUTE self-pass，明示 deferred 到独立 CLOSURE_VERIFY mission step。本审计重分类不承载该 flip；需独立 closure-audit（fresh session）验证 `2026-08-04-2300-1` 的实现后才能 flip。**Why Not Blocking Closure of THIS plan**：item-1 flip 与 T2 证据重分类是两件独立的事，前者不阻塞后者的 readiness 重决策。
- §2a 的 4 行 lane-blocked（Kafka/Pulsar/PostgreSQL/Debezium 外部 backend 供应）：基础设施供应，out of audit scope；本计划 readiness 判定仍 bounded 即因 §2a 仍存。解决后可触发另一次 readiness re-audit 追求 blanket ready。
- `TestMultiJvmExactlyOnceRecovery` Javadoc 标注的 "Stage 43+ follow-up"（完整 source→keyBy→sink cross-JVM 共享 sink exactly-once 断言）：独立功能增强，不属本审计重分类。

## Closure

Status Note: 纯审计/文档计划完成。消费 plan `2026-08-09-1252-1` 的 T2 multi-JVM 修复证明，对冻结证据语料做事实对齐：4 条 §2b 行 `blocked`→`e2e-proved`，2 条 cross-JVM residual 行升级（EVID-S13-021/EVID-S14-015），8 条保留 residual-risk 并更新 rationale，叙述对齐，readiness gate 重跑，report/roadmap 更新。§2b capability-gap blocker 类解除；§2a lane-blocked 仍为唯一 blocker 类，故 readiness 判定仍 bounded `ready only for enumerated e2e-proved capability/environment pairs`（132 e2e-proved rows / 4 blocked rows）。**未 flip roadmap item 1**（其 `done` 前置独立 closure-audit 未完成，见 Non-Goals）。不改 nop-stream 生产代码。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session `ses_01a33c6d8ffeadFuQFbNt2KJ15`，OPENCODE_MODEL `glm-5.2`，未参与实现）
- Audit Session: `ses_01a33c6d8ffeadFuQFbNt2KJ15`
- Evidence:
  - **Fresh T2 lane 重跑（本审计独立证据）**：
    - `TestMultiJvmExactlyOnceRecovery`：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 67.38 s`（surefire 报告 `nop-stream/nop-stream-runtime/target/surefire-reports/io.nop.stream.runtime.multijvm.TestMultiJvmExactlyOnceRecovery.txt`，fresh run 2026-08-09 17:00:21 +08:00）
    - `TestMultiJvmCoordinatorFailover`：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.201 s`（surefire 报告 `…/io.nop.stream.runtime.multijvm.TestMultiJvmCoordinatorFailover.txt`，fresh run 2026-08-09 17:00:40 +08:00）
  - **每条 Exit Criterion 验证结果**：Phase 1 8/8 PASS、Phase 2 11/11 PASS（逐条经独立审计 task 1-6 核对 live repo）
  - **每条 Closure Gate 验证结果**：13/13 PASS（独立审计 task 1-10 + 3 validator）
  - **Anti-Hollow 检查结果**：(a) 重分类行的 `positive_proof` 指向 fresh 重跑的真实 PASS 测试方法——surefire artifact 存盘且数字匹配 evidence row（`multiJvmDeployKillRecoverFencing` @ TestMultiJvmExactlyOnceRecovery.java:89-90；`testCoordinatorKillTriggersStandbyTakeover` @ :53-54；`testBrainSplitFencingBoundary` @ :107-108）；(b) readiness 判定与重跑聚合一致（e2e-proved 132 / blocked 4，validator 从 live 文件实时重算）；(c) 8 条保留 residual-risk 行各有显式可核对 rationale（"RATIONALE UPDATE" + 具体原因：mutex 需 racing coordinators / zombie 需 kept-alive producer）；(d) matrix/narrative 与 `@@EVIDENCE` 行一致（grep "BLOCKED — deeper" 0 matches in-scope）
  - **Deferred 项分类检查**：无 deferred 项；§2a lane-provisioning 显式 out-of-scope；item-1 flip 显式 out-of-scope 归独立 CLOSURE_VERIFY；Stage 43+ full exactly-once 显式 out-of-scope
  - `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all --strict` 退出码 0（7 checker 全 PASS）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2 warning 为 plan 文件内 `stage-13...evidence.md` 简写，pre-existing，0 error）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

Follow-up:

- **roadmap item 1（Coordinator、运行时并发与恢复缺陷收口）`planned`→`done` flip**：独立 CLOSURE_VERIFY 步骤承载，不属本审计重分类（见 Non-Blocking Follow-ups）。
- §2a 的 4 行 lane-blocked（Kafka/Pulsar/PostgreSQL/Debezium 外部 backend 供应）：基础设施供应，out of audit scope。
- `environment-qualification.md:85` 仍含 present-tense "Two deeper tests have defects" 陈旧文本（frozen Stage 5 record，显式 out-of-scope；validator 不 flag 因为 T2 `@@LANE` 行正确标 `qualified`）。建议后续 Stage 5 lane-qualification refresh 审计更新。
- `TestMultiJvmExactlyOnceRecovery` Javadoc 标注的 "Stage 43+ follow-up"（完整 source→keyBy→sink cross-JVM 共享 sink exactly-once 断言）：独立功能增强。
