# 2 Historical P2 CEP/Connector/Runtime Finding Disposition (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 22); `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md` (Shard 22, frozen); `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md` (frozen, incl. Stage 18 Supplement); live repo HEAD
> Mission: nop-stream-independent-audit
> Work Item: 22. Historical P2 CEP/connector/runtime finding disposition
> Related: Execution order `{2}` of 2 in this batch. **Hard dep on Stage 18 整体**（roadmap deps: 4,12,13,14,15,16,17,18,20）— Stage 18 provides the `disposition` validator infrastructure (`--shard`/`--strict`/`all` + `@@DISPOSITION` format + 5-value vocabulary + `roadmap-stage-<N>` sentinel). Stages 4/12/13/14/15/16/17/18/20 = done. Can run in parallel with Stage 21 (no mutual dependency). **Unblocks Stage 23** (documentation contract + readiness decision).

## Purpose

对 nop-stream 历史 P2 分布式侧语料（Shard 22，5 条 finding：CEP + coordinator/runtime 域）逐条做 live revalidation 与唯一终态裁决。含 1 条 `O7-2-AR-5`（`status_at_0802: left-for-followup`），须确认其在当前 HEAD 是否已修复。复用 Stage 18 的 `disposition` validator 基础设施，不重复建设。

## Current Baseline

经 2026-08-08 冻结语料 + live repo 核对：

- **冻结语料 Shard 22**：5 条 finding（07-25 multi+open），domain cluster = CEP/connector/runtime。IDs：
  - P2×4：`M7-2-P2-8`（Lockable.release throws bare IllegalStateException）、`M7-2-P2-14`（TestJobTerminationContext factory-method field assignment）、`M7-2-P2-17`（TestSharedBuffer overuses assertNotNull）、`M7-2-P2-18`（TestNFAState equals/hashCode mirror tests）。
  - AR×1：`O7-2-AR-5`（ResultPartition.close() bufferPool permit double-release race，`status_at_0802: left-for-followup`，corpus anchor `ResultPartition.java:178-193`）。
- **已有 revalidation（可 cross-reference）**：
  - Stage 12（CEP）：`M7-2-P2-8` **e2e-proved (FIXED)**（EVID-S12-007，`Lockable.release():62` now throws `StreamRuntimeException`，not bare `IllegalStateException`）；`M7-2-P2-17` **residual-risk**（EVID-S12-017，residual persists；real lifecycle proven by TestSharedBufferExtended）；`M7-2-P2-18` **residual-risk**（EVID-S12-018，residual persists）。
  - Stage 17（test effectiveness）：`M7-2-P2-8` **closed (FIXED)**；`M7-2-P2-14` live-residual（test-quality remediation successor，runtime domain）；`M7-2-P2-17/18` live-residual（test-quality remediation successor，CEP domain）。
- **O7-2-AR-5 特殊状态**（`status_at_0802: left-for-followup`）：
  - corpus anchor `ResultPartition.java:178-193`。corpus desc：`ResultPartition.close()` bufferPool permit double-release race during concurrent consumer reads（distinct permit-accounting angle vs M7-2-P1-10 data loss）。
  - **注意**：`M7-2-P1-10`（Shard 20，`ResultPartition.close()` discards un-consumed records）已在 Stage 20 裁决为 `revalidated`（FIXED，dedicated regression tests）。`O7-2-AR-5` 描述的是**同一方法的不同缺陷角度**（bufferPool permit double-release race，非 data loss），Stage 20 未处理 AR-5 的 permit-accounting 角度。本计划须从零复验 AR-5 的 permit-accounting 角度是否已修复。
- **无先验 evidence 的 finding**：`O7-2-AR-5`（Stage 13/14 evidence 文件中无对应 evidence row；Stage 20 处理的是 M7-2-P1-10 的 data-loss 角度，非 AR-5 的 permit-accounting 角度）。
- **真实 gap**：(1) Shard 22 的 5 条 finding 没有逐条 live-revalidation + 唯一终态裁决表；(2) `O7-2-AR-5`（left-for-followup）的 permit-accounting 角度无先验 evidence，须从零确认是否已修复。

## Goals

- 对 Shard 22 全部 **5 条** finding 逐条产出 live-revalidation 裁决，每条落到且只落到 `revalidated | stale | active/successor owner | residual-risk | blocked` 之一。
- **P2 特殊规则**：P2 可以落 `residual-risk`（须附 non-blocking rationale）；但任何 P2 经复验 reclassified 为 live P0/P1 的，须指向 `active/successor owner`（`owner_plan` 为仓库内存在的 plan 路径或 `roadmap-stage-<N>` sentinel 指向非 `done` 的 stage），不允许静默降级为 `residual-risk`。
- 确认 `O7-2-AR-5`（left-for-followup）在当前 HEAD 的 permit-accounting 角度是否已修复；区分 AR-5 的 permit 角度与 M7-2-P1-10 的 data-loss 角度。
- 复用 Stage 18 的 `disposition` validator（`--shard 22`），不重复建设基础设施。

## Non-Goals

- 枚举新 capability evidence row（Stages 6–16 已完成；本计划 cross-reference 已有 inventory_id）。
- 修复 nop-stream 产品代码（audit-only）。
- 处理 Shard 18/19/20/21（Stages 18/19/20/21）。
- 生产就绪结论（Stage 23）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-22-hist-p2-cep-connector-runtime-disposition.md`（5 条 `@@DISPOSITION` 块 + header 统计）。

### Out Of Scope

- `disposition` validator 子命令实现（Stage 18 owns；本计划仅消费）。
- 其他 shard 的 finding。
- 任何 nop-stream 生产代码变更。

## Execution Plan

### Phase 1 - AR finding 裁决（1 条）与增量校验

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-22-hist-p2-cep-connector-runtime-disposition.md`

- Item Types: `Proof`

- [x] 对 `O7-2-AR-5`（`status_at_0802: left-for-followup`，corpus anchor `ResultPartition.java:178-193`）live 复验：确认 `close()` 方法的 bufferPool permit double-release race（concurrent consumer reads 角度）是否已修复。**注意区分**：M7-2-P1-10 处理的是 data-loss 角度（close discards un-consumed records），AR-5 处理的是 permit-accounting 角度（bufferPool permit double-release race during concurrent consumer reads）。须 trace `close()` → `bufferPool` 交互，确认 permit acquire/release 配对在并发路径上无 double-release。
- [x] 写一条 `@@DISPOSITION`：permit race 已修复 → `revalidated`（附证据 + 新 anchor 如有漂移）；regression → `active/successor owner`；机制移除（如 bufferPool 重构）→ `stale`（附 `stale_rationale`）；仍未处理 → `residual-risk`（须附 non-blocking rationale）或 `active/successor owner`（如发现已 reclassify 为 live defect）。

Exit Criteria:

- [x] disposition 文件含 ≥1 条 `@@DISPOSITION` 覆盖 AR finding ID（`O7-2-AR-5`）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 22`（**partial 模式，不带 `--strict`**）对已有 1 条 AR 行退出码 0——及早发现格式错误
- [x] `revalidated`/`stale`/`residual-risk` 附可复核证据
- [x] **无静默跳过**（Rule #24）：无法裁决的 AR 落 `blocked` + 命名 lane
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2 finding 裁决（4 条）与全 shard 收口

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-22-hist-p2-cep-connector-runtime-disposition.md`

- Item Types: `Proof`

- [x] 对 Shard 22 全部 P2（4）逐条 live 复验，优先 cross-reference 已有 evidence：
  - `M7-2-P2-8`（Lockable.release bare exception）：cross-reference Stage 12 EVID-S12-007（e2e-proved FIXED）。须交叉核对一致性。
  - `M7-2-P2-14`（TestJobTerminationContext vacuous）：cross-reference Stage 17 live-residual。
  - `M7-2-P2-17`（TestSharedBuffer assertNotNull overuse）：cross-reference Stage 12 EVID-S12-017（residual-risk）+ Stage 17 live-residual。
  - `M7-2-P2-18`（TestNFAState mirror tests）：cross-reference Stage 12 EVID-S12-018（residual-risk）+ Stage 17 live-residual。
- [x] 每条 P2 写一条 `@@DISPOSITION`。P2 落 `residual-risk` 须附 non-blocking rationale。
- [x] header 写全 shard 统计：5 条 disposition 分布 × severity 交叉表、× domain 交叉表。
- [x] 全 shard 5 条完整性核对：每条恰好一条 `@@DISPOSITION`，无遗漏无重复。

Exit Criteria:

- [x] disposition 文件含恰好 5 条 `@@DISPOSITION`，覆盖 Shard 22 全部 finding ID（`M7-2-P2-8,14,17,18`、`O7-2-AR-5`）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 22 --strict` 退出码 0（5 条完整、词表合法、字段依赖满足、`owner_plan` 为仓库路径或合法 `roadmap-stage-<N>` sentinel）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] header 统计：disposition × severity 交叉表存在且 5 条合计一致
- [x] `M7-2-P2-8` 裁决与 Stage 12（EVID-S12-007 e2e-proved FIXED）一致
- [x] 每条 P2 `residual-risk` 附 non-blocking rationale；每条 P2 reclassified 为 P0/P1 的已指向 `active/successor owner`（不允许静默残留为 `residual-risk`）
- [x] **无静默跳过**（Rule #24）：无法裁决的 P2 落 `blocked` + 命名 lane
- [x] 若复验发现新 confirmed live defect（非已有 finding），已按 roadmap 规则指派 remediation plan
- [x] `No owner-doc update required`（disposition 是审计基础设施）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯审计/数据计划**：不改 nop-stream 生产代码。closure 以 validator 退出码 + disposition 完整性为证据。

- [x] Shard 22 全部 5 条 finding 各有恰好一条 `@@DISPOSITION`（completeness + no-dup）
- [x] 每条裁决值在 5-value 词表内，字段依赖满足
- [x] 不存在 P2/AR `residual-risk` 缺 non-blocking rationale
- [x] 不存在 P2 reclassified 为 P0/P1 仍静默降级为 `residual-risk`（须指向 `active/successor owner`）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope finding
- [x] `active/successor owner` 的 `owner_plan` 为仓库内存在的 plan 路径或合法 `roadmap-stage-<N>` sentinel
- [x] O7-2-AR-5 在当前 HEAD 已 reconfirm（permit-accounting 角度，含 anchor 漂移处理）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 22 --strict` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）每条 `revalidated` 有可复核证据，（b）每条 `active/successor owner` owner plan 真实存在，（c）无 finding 被静默丢弃

## Deferred But Adjudicated

（预期场景：某 finding 的 live 复验需 multi-JVM lane 但该 lane 有 defect——落 `blocked` + 命名 lane，是合法终态，非 deferred。P2 可以落 `residual-risk`，须附 non-blocking rationale。）

## Non-Blocking Follow-ups

- 若某 P2 finding 经复验确认已 fixed（`revalidated`），无需 successor。
- **注意**：Stage 12/17 登记的 prior successor `2026-08-04-2300-3` 已 `completed`、`roadmap-stage-17` 已 `done`——validator 会拒绝这些 sentinel/path 作为 `owner_plan`。若某 test-quality P2 经复验确认仍 live，其 `owner_plan` 须指向 `roadmap-stage-23`（文档/契约收口，非 `done`）或由本计划触发创建新 remediation plan stub。不允许 still-live P2 落 `residual-risk` 而无 non-blocking rationale（P2 允许 residual-risk 但须附理由，不同于 P0/P1 必须有 owner）。

## Closure

Status Note: Shard 22 全部 5 条 finding（4 P2 + 1 AR）逐条 live revalidation 完成，每条恰好一条 `@@DISPOSITION`。裁决分布：2 revalidated（M7-2-P2-8 Lockable StreamRuntimeException；O7-2-AR-5 close() permit-neutral）、3 residual-risk（M7-2-P2-14/17/18 test-quality gaps，均附 non-blocking rationale，successor=roadmap-stage-23）。无 stale、无 active/successor owner、无 blocked。所有裁决与 Stage 12/17 cross-reference 一致。纯审计计划（无 nop-stream 生产代码变更），closure 以 validator 退出码 + disposition 完整性 + 测试绿为证据。
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: opencode executor (mission-driver EXECUTE pass, session 2026-08-08)
- Evidence:
  - Phase 1 Exit Criteria: `@@DISPOSITION` for O7-2-AR-5 written (revalidated, close() permit-neutral); `disposition --shard 22` partial 退出码 0 — PASS
  - Phase 2 Exit Criteria: 恰好 5 条 `@@DISPOSITION`（M7-2-P2-8,14,17,18 + O7-2-AR-5）；`disposition --shard 22 --strict` 退出码 0（5 条完整、词表合法、字段依赖满足）；`self-test` 退出码 0；header disposition×severity/disposition×domain 交叉表合计=5 — PASS
  - M7-2-P2-8 裁决与 Stage 12 EVID-S12-007 (e2e-proved FIXED) 一致：Lockable.release():62 throws StreamRuntimeException, not bare IllegalStateException — CONSISTENT
  - 每条 P2 residual-risk 附 non-blocking rationale（M7-2-P2-14 runtime lifecycle e2e by Stage 13/14；M7-2-P2-17 TestSharedBufferExtended EVID-S12-006；M7-2-P2-18 Stage 12 CEP e2e）；无 P2 reclassified 为 P0/P1 — PASS
  - Anti-Hollow Check: 每条 revalidated 有可复核 live-code 证据（Lockable.java:62 / ResultPartition.java:316-329 permit trace）；无 active/successor owner（0 条）无需验 owner plan 存在性；5 条 finding 无遗漏无重复（strict completeness 退出码 0）— PASS
  - `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all` 全量 5 检查器退出码 0（manifest/corpus/evidence/qualification/disposition）— PASS
  - `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` BUILD SUCCESS；`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（审计文件变更不影响产品代码；测试绿确认 baseline）
  - 无 `> Source Audits:` front matter（roadmap-sourced plan），故无 source-audit 文件需关闭

Follow-up:

- 无 plan-owned 剩余工作。3 条 test-quality P2 residual-risk（M7-2-P2-14/17/18）的 successor 为 roadmap-stage-23（文档契约与测试有效性收敛，roadmap status `todo`），由 Stage 23 接续，非本 plan 遗留 debt。
- 观察项（非 confirmed live defect，非本 plan scope）：ResultPartition.injectFront() 在 Stage 43 recovery 路径遇到 EOS sentinel 时会 release 一个 permit（:441-443），而 close() 入队 EOS 时未 acquire permit — 这是一个 permit-accounting 微妙点，记录于 AR-5 disposition 的 note 字段，供未来审计轮次复核，不影响 AR-5 的 permit-accounting 裁决（close() 路径本身 permit-neutral）。
