# 1 Current Production Finding Disposition (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 18); `ai-dev/audits/nop-stream-independent-audit/finding-corpus.md` (Shard 18, frozen); `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md` (frozen); live repo HEAD
> Mission: nop-stream-independent-audit
> Work Item: 18. Current production finding disposition
> Related: Execution order `{1}` of 3 in this batch. Unblocks Stages 19–22 (all depend on 18). Depends on Stages 1–4：Stage 4 = `done`（corpus/schema frozen）；Stages 2/3 = `done`（roadmap）。Stage 1 roadmap item 仍标 `planned`，但其 plan 文件 `2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md` 已 `completed`（含 closure-audit evidence）；本计划将逐条 live 复验确认其 owned finding 是否真已修复（plan-completed ≠ finding-fixed），不假定 roadmap 状态。3 份 remediation plan `2026-08-04-2300-{1,2,3}` 均 `completed`。Consumes revalidation evidence already produced by Stages 6–17 (all `done`).

## Purpose

冻结并对 nop-stream 当前生产审计语料（Shard 18，42 条 finding）逐条做出唯一裁决。每条 finding 必须针对 live code 重新验证，并落到且只落到以下一种终态：`revalidated`（缺陷已修复/不再成立）、`stale`（锚点/上下文已消失）、`active/successor owner`（仍为活缺陷且有 owner plan）、`residual-risk`（接受为非阻塞残留）、`blocked`（因所需 lane 未认定而无法裁决）。同时建立 disposition 校验基础设施（`@@DISPOSITION` 格式 + validator `disposition` 子命令 + 阳性对照），供 Stages 19–22 复用。

## Current Baseline

经 2026-08-08 live repo + 冻结语料核对：

- **冻结语料**：`finding-corpus.md` Shard 18 含 **42 条** finding — P0×2（`M8-2-P0-1` JobCoordinator 恢复竞态、`M8-2-P0-2` TestTaskManagerDaemon vacuous）、P1×13（`M8-2-P1-1..13`）、P2×23（`M8-2-P2-1..23`）、AR×4（`O8-2-AR-1..4`）。每条带 anchor `file:line`、severity、domain、desc。
- **Deferred-P2 owner-plan 交叉引用**（corpus 已冻结）：Plan 1 owns M8-2-P2-9/10/15；Plan 2 owns M8-2-P2-1/5/6/7 + O8-2-AR-3；Plan 3 owns M8-2-P2-11/12/13/14/16/17/23。共 15 条 P2/AR 已有 owner-plan 跟踪（3+5+7=15）。
- **3 份 remediation plan 已 `completed`**：`2026-08-04-2300-1-coordinator-runtime-concurrency-recovery-hardening.md`、`2026-08-04-2300-2-checkpoint-state-backend-cep-correctness.md`、`2026-08-04-2300-3-contract-drift-config-test-integrity.md` 均为 `completed`（含 closure-audit evidence）。其 owned P0/P1/AR/P2 finding 是否真已修复，须由本计划逐条 live 复验确认（plan-completed ≠ finding-fixed）。
- **Stages 6–17 已产出的 revalidation**（可 cross-reference，避免重复劳动）：
  - Stage 6（Java/local）：`M7-2-P0-1` RESOLVED、`M7-2-P1-5` RESOLVED、`M7-2-P2-3` STALE（注：这些属 07-25 历史分片，但部分 08-02 finding 有 recurrent 对应）。
  - Stage 9（checkpoint/barrier/recovery）：`M8-2-P0-1` residual-risk（EVID-S9-016）、`M8-2-P1-2` residual-risk（EVID-S9-017）、`M8-2-P1-5` e2e-proved（EVID-S9-018，InputGate 多线程集合已修复）、`M8-2-P1-6` residual-risk（EVID-S9-019）。
  - Stage 12（CEP）：`O8-2-AR-1` e2e-proved（FIXED，EVID-S12-006）、`O8-2-AR-2` residual（size==1 proved / size>1 residual）、`O8-2-AR-3` residual-risk、`O8-2-AR-4` residual-risk。
  - Stage 14（data-plane）：`M8-2-P0-1` residual-risk、`M8-2-P1-6` residual-risk（EVID-S14-016/017/020/021）。
  - Stage 15（batch/message）：`M8-2-P2-12` non-goal、`M7-2-P1-9` FIXED。
  - Stage 16（JDBC/file/CDC）：`M7-2-P0-2` FIXED（cross-ref Stage 19 formal owner）。
  - Stage 17（test effectiveness）：22 条 test-quality finding 已登记 successor（含 shard-18 的 M8-2-P2-20/21/22/23、O8-2-AR-4）。
- **无 disposition 校验基础设施**：`check-nop-stream-audit-manifest.mjs` 现有子命令 `manifest | corpus | evidence | qualification | self-test`，**无** `disposition` 子命令。finding 级 5-value 裁决词表尚未有 validator 守护。
- **真实 gap**：(1) Shard 18 的 42 条 finding 没有逐条 live-revalidation + 唯一终态裁决表；(2) 没有 disposition validator（completeness/vocabulary/字段依赖/阳性对照）；(3) 3 份 remediation plan 的 owned finding 修复状态未在统一裁决表中确认。

## Goals

- 对 Shard 18 全部 **42 条** finding 逐条产出 live-revalidation 裁决，每条落到且只落到 `revalidated | stale | active/successor owner | residual-risk | blocked` 之一。
- 每条裁决附带可复核证据：`revalidated` 须有 revalidation 证据（测试名 / manual-trace / cross-ref 已有 evidence row inventory_id）；`stale` 须有 stale 理由（锚点消失/上下文重构）；`active/successor owner` 须有 owner（仓库内存在的 plan 路径，或 `roadmap-stage-<N>` sentinel 指向一个非 `done` 的 roadmap stage 作为 successor——用于仍活但 owner 尚无 plan 文件的 finding，如 doc-drift 的自然 owner Stage 23 尚无 plan）；`residual-risk` 须有 non-blocking 理由；`blocked` 须命名未认定 lane。
- 产出一个 validator `disposition` 子命令（扩展现有 `check-nop-stream-audit-manifest.mjs`），校验：每个 shard finding 恰好一条 `@@DISPOSITION`、词表合法、字段依赖满足（disposition 值 → 必填字段非空）、owner_plan 路径在仓库存在、阳性对照（已知坏输入被拒绝）。
- 确认 3 份 remediation plan owned finding 的修复是否在 live code 中成立；仍为活缺陷的 P0/P1 必须指向 active/successor plan（不允许静默降级为 residual）。

## Non-Goals

- 枚举新的 capability evidence row（那是 Stages 6–16 的工作；本计划只 cross-reference 已有 inventory_id，不新造 evidence row，除非复验中发现新活缺陷需指派 plan）。
- 修复任何 nop-stream 产品代码缺陷（audit-only 计划；若复验发现新 confirmed live defect，按 roadmap 规则指派 remediation plan，不在本计划修复）。
- 处理 Shard 19–22 的 historical finding（那是 Stages 19–22 的工作）。
- 做生产就绪（readiness）结论（Stage 23）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-18-current-production-disposition.md`（42 条 `@@DISPOSITION` 块 + header 统计）。
- `ai-dev/tools/check-nop-stream-audit-manifest.mjs` 新增 `disposition` 子命令（含 `--shard`/`--strict`/`all` 接线 + 阳性对照）。
- `ai-dev/audits/nop-stream-independent-audit/evidence-schema.md` 追加 "Stage 18 Supplement" 段落（`@@DISPOSITION` 格式 + 5-value 词表 + 字段依赖规则 + 两套词表关系说明）。
- disposition 汇总统计写入 `stage-18-*.md` 文件 header（**不回写** frozen 的 `finding-corpus.md`，避免破坏其 frozen 语义；如需 corpus 侧汇总，由 Stage 23 文档收口时统一处理）。

### Out Of Scope

- Shard 19/20/21/22 的 finding 裁决（Stages 19–22）。
- 任何 nop-stream 生产代码变更。
- 新增 capability evidence row（Stages 6–16 已完成；本计划只裁决 finding 终态）。

## Execution Plan

### Phase 1 - Disposition 格式与 validator 基础设施

Status: completed
Targets: `ai-dev/tools/check-nop-stream-audit-manifest.mjs`（新增 `disposition` 子命令）

- Item Types: `Decision | Proof`

- [x] 在 `evidence-schema.md` 追加 "Stage 18 Supplement — Finding-Disposition Schema" 段落（additive，不改 11 字段/7-value evidence-row 词表），冻结 `@@DISPOSITION` 块格式与 5-value finding 裁决词表及字段依赖规则。**显式说明两套词表的关系**：evidence-row 7-value 词表（`e2e-proved|component-only|unverified|fail-fast|non-goal|residual-risk|blocked`）裁定**能力**；finding-disposition 5-value 词表（`revalidated|stale|active/successor owner|residual-risk|blocked`）裁定**finding 终态**。二者共享 `residual-risk`/`blocked` 值名但语义层不同，不可混用。
- [x] 实现 `disposition` 子命令，支持 `--shard <N>` 与 `--strict` 参数，语义明确定义如下：
  - **不带 `--strict`（partial 模式）**：只校验**已有** `@@DISPOSITION` 行的合法性（词表、字段依赖、`finding_id`/`severity`/`source_anchor` 与 corpus 一致、no-dup）；**不做 completeness**（不要求 shard N 全部 finding 都有 disposition）。用于 Phase 2 中间检查。
  - **带 `--strict`（completeness 模式）**：在 partial 模式基础上，额外要求 `--shard N` 内**每个** finding ID 恰好有一条 `@@DISPOSITION`（completeness + no-dup）。用于 Phase 3 / Closure Gates。
  - **无 `--shard`（如 `all` 模式调用）**：只校验已有行合法性（同 partial），不跨 shard 强制 completeness。
  - 校验规则：(a) `disposition` 值在 `revalidated | stale | active/successor owner | residual-risk | blocked` 内；(b) 字段依赖 — `active/successor owner`→`owner_plan` 非空且为仓库内存在的 plan 路径 **或** `roadmap-stage-<N>` sentinel（指向一个非 `done` 的 roadmap stage 作为 successor owner；validator 读取 `nop-stream-independent-audit-roadmap.md` Work Items 验证该 stage 非 `done`）；`residual-risk`→`residual_rationale` 非空；`blocked`→`blocked_lane` 非空且为已注册 lane；`revalidated`→`revalidation_evidence` 非空；`stale`→`stale_rationale` 非空；(c) `finding_id`/`severity`/`source_anchor` 与冻结 corpus 一致。
- [x] 定义 disposition 文件发现机制：validator 扫描 `ai-dev/audits/nop-stream-independent-audit/stage-*-disposition.md`（与 evidence checker 扫描 `*.evidence.md` 同模式）。
- [x] 修改 `all` 子命令，使其包含 `disposition` checker（现有 `all` 仅跑 manifest+corpus+evidence+qualification 4 个 checker；`all` 模式下 disposition checker 以无 `--shard` 的 partial 模式运行，不跨 shard 强制 completeness）。
- [x] 阳性对照：`self-test` 扩展覆盖 `disposition` 子命令（已知坏输入：缺 finding、词表外值、`active/successor owner` 缺 owner_plan、`residual-risk` 缺 rationale、重复 ID、owner_plan 路径不存在、`roadmap-stage-N` 指向已 done 的 stage、`--strict` 模式下 shard N 缺 finding — 均须非零退出码失败）。

Exit Criteria:

- [x] `evidence-schema.md` 含 "Stage 18 Supplement" 段落，`@@DISPOSITION` 格式与 5-value 词表及字段依赖规则有显式文本，且显式说明与 evidence-row 7-value 词表的关系
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 18`（不带 `--strict`，partial 模式）可执行：此时无 disposition 文件应退出码 0（无行可校验）并打印"0 disposition rows"
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 18 --strict`（completeness 模式）此时退出码非 0（42 条 finding 全缺）——确认 `--strict` 确实触发 completeness
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0（阳性对照覆盖 disposition，确认非空壳）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all --strict` 包含 disposition checker 且退出码 0（`all` 模式下 disposition 以 partial 模式运行，无 disposition 文件时不阻塞）
- [x] **无静默跳过**（Rule #24）：validator 遇到缺字段/词表外值/依赖不满足时显式报错退出，不静默修复或忽略
- [x] **接线验证**：`disposition --shard <N> --strict` 可被本 plan Phase 2/3 及 Stages 19–22 Closure Gates 直接调用
- [x] `No owner-doc update required`（schema supplement 是审计基础设施；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P0/P1/AR finding 裁决（19 条）

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-18-current-production-disposition.md`

- Item Types: `Proof`

- [x] 对 Shard 18 全部 P0（2）/P1（13）/AR（4）共 **19 条** finding 逐条 live 复验：(a) 核对 anchor `file:line` 在当前 HEAD 是否仍存在；(b) 若 anchor 存在，核对缺陷描述的活行为是否仍成立（读代码/跑引用测试/trace 调用链）；(c) 优先 cross-reference Stages 6–17 已产出的 evidence row（inventory_id），避免重复劳动；(d) 对 3 份 remediation plan owned 的 P0/P1/AR，确认 plan 的 closure-audit evidence 是否覆盖该 finding 的修复。
- [x] 每条 P0/P1/AR finding 写一条 `@@DISPOSITION` 块（finding_id、severity、source_anchor、disposition、对应必填字段）。
- [x] 确认的 still-live P0/P1 finding 必须落到 `active/successor owner`（指向仓库内存在的 plan 路径）；不允许 P0/P1 still-live defect 落到 `residual-risk`（cross-cutting concern: confirmed live P0/P1 require an active/successor plan）。
- [x] `revalidated`（已修复）的 finding 须附 revalidation 证据（测试名 / manual-trace `file:line` / cross-ref evidence row inventory_id）；`stale` 须注明锚点消失/上下文重构。

Exit Criteria:

- [x] disposition 文件存在，含 ≥19 条 `@@DISPOSITION` 块覆盖全部 P0/P1/AR finding ID（无遗漏、无重复）
- [x] 每条 still-live P0/P1 落 `active/successor owner` 且 `owner_plan` 为仓库 plan 路径或合法 `roadmap-stage-<N>` sentinel；不存在 P0/P1 still-live defect 静默降级为 `residual-risk`
- [x] 每条 `revalidated` 附可复核 revalidation 证据（非空泛描述）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 18`（**partial 模式，不带 `--strict`**）对已有 19 条退出码 0（只校验合法性，不要求 P2 completeness）
- [x] **无静默跳过**（Rule #24）：无法在当前 lane 裁决的 P0/P1 须落 `blocked` + 命名 lane，不可静默标 `revalidated`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2 finding 裁决（23 条）与全 shard 收口

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-18-current-production-disposition.md`

- Item Types: `Proof`

- [x] 对 Shard 18 全部 P2（23）finding 逐条 live 复验（同 Phase 2 方法），优先 cross-reference Stage 17 test-effectiveness 登记的 successor 归属。
- [x] 每条 P2 finding 写一条 `@@DISPOSITION` 块。P2 落 `residual-risk` 须附 non-blocking 理由（cross-cutting concern: P2 requires explicit non-blocking rationale before residual acceptance）。
- [x] 在 disposition 文件 header 写全 shard 统计：42 条 finding 的 disposition 分布（`revalidated`/`stale`/`active/successor owner`/`residual-risk`/`blocked` 各几条）、与 severity 交叉表、与 domain 交叉表。
- [x] 全 shard 42 条 finding 完整性核对：每条恰好一条 `@@DISPOSITION`，无遗漏无重复。

Exit Criteria:

- [x] disposition 文件含恰好 42 条 `@@DISPOSITION` 块，覆盖 Shard 18 全部 finding ID（`M8-2-P0-1`..`M8-2-P2-23`、`O8-2-AR-1`..`O8-2-AR-4`）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 18 --strict` 退出码 0（42 条完整、词表合法、字段依赖满足、owner_plan 路径存在）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0（阳性对照覆盖 disposition）
- [x] header 统计：disposition × severity 交叉表存在且 42 条合计一致
- [x] 每条 P2 `residual-risk` 附 non-blocking 理由；每条 still-live P2 reclassified 为 P0/P1 的（若复验发现）已指向 active/successor plan（不允许静默残留）
- [x] **无静默跳过**（Rule #24）：无法裁决的 P2 落 `blocked` + 命名 lane
- [x] 若复验中发现**新的** confirmed live defect（非已有 finding），已按 roadmap 规则指派 remediation plan（不在本 audit-only 计划修复，但记录指派）
- [x] `No owner-doc update required`（disposition 是审计基础设施；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯审计/数据计划**：本计划不改 nop-stream 生产代码（仅新增 disposition 数据文件 + validator 子命令）。`./mvnw test`/`./mvnw compile` 不强制；closure 以 validator 退出码 + disposition 完整性为证据。validator 脚本须通过 `node --check`。

- [x] Shard 18 全部 42 条 finding 各有恰好一条 `@@DISPOSITION` 裁决（completeness + no-dup）
- [x] 每条裁决值在 5-value 词表内，字段依赖满足
- [x] 不存在 P0/P1 still-live defect 静默降级为 `residual-risk`（confirmed live P0/P1 → `active/successor owner`）
- [x] 不存在 P2 `residual-risk` 缺 non-blocking 理由
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope finding
- [x] `active/successor owner` 的 `owner_plan` 路径在仓库存在
- [x] 3 份 remediation plan owned finding 的 live 修复状态已在裁决表中确认
- [x] `node --check ai-dev/tools/check-nop-stream-audit-manifest.mjs` 通过
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs disposition --shard 18 --strict` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs self-test` 退出码 0
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs all --strict` 退出码 0（manifest+corpus+evidence+qualification+disposition 全 PASS）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）每条 `revalidated` 裁决有可复核证据（非空泛"已修复"），（b）每条 `active/successor owner` 的 owner plan 真实存在，（c）无 finding 被静默丢弃或跳过

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：某 finding 的 live 复验需 multi-JVM lane 但 T2 有已知 defect——此类应落 `blocked` + 命名 T2 lane，是合法终态（阻塞 Stage 23 ready verdict），非 deferred。confirmed still-live P0/P1 defect 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- 若某 `residual-risk` P2 在后续被 reclassify 为 P0/P1，由 successor remediation plan 处理。
- `docs-for-ai/01-repo-map/module-groups.md` 子模块清单 drift 收敛由独立 owner-doc plan 负责（不阻塞本计划）。

## Closure

Status Note: Shard 18 全部 42 条 finding 逐条 live-revalidated 并裁决。15 条 revalidated（缺陷已修复，附测试名/manual-trace/cross-ref inventory_id），2 条 stale（锚点消失：_module 已存在、TestTaskExecutorDaemonThreads 已删除），25 条 residual-risk（22 P2 + 3 AR，每条附 non-blocking rationale + owner_plan）。零 P0/P1 落到 residual-risk。disposition validator 基础设施（`@@DISPOSITION` 格式 + 5-value 词表 + 字段依赖 + owner_plan path/sentinel 校验 + completeness + 阳性对照）已实现并接线到 `all` 子命令。3 份 remediation plan owned finding 的 live 修复状态已逐条确认。
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent (fresh session, task_id ses_02150cfa7ffeySzFUTueEAF0Il)
- Audit Session: ses_02150cfa7ffeySzFUTueEAF0Il (2026-08-08)
- Evidence:
  - Gate 1 (completeness): `disposition --shard 18 --strict` exit 0 — 42 rows validated, 42 unique finding_ids, 0 dups. PASS.
  - Gate 2 (vocabulary + field deps): 5-value vocab enforced; spot-checked revalidated/stale/residual-risk blocks all have required conditional fields. PASS.
  - Gate 3 (no P0/P1 silent downgrade): P0 residual-risk=0, P1 residual-risk=0. All P0×2 revalidated, P1×11 revalidated + P1×2 stale. PASS.
  - Gate 4 (P2 residual rationale): 22 P2 residual-risk blocks all have non-empty residual_rationale. PASS.
  - Gate 5 (no silent drop): all 42 findings have exactly one @@DISPOSITION. PASS.
  - Gate 6 (active/successor owner_plan): 0 such dispositions (vacuously true). PASS.
  - Gate 7 (3 remediation plans' 15 owned findings): all 15 present with @@DISPOSITION blocks + owner_plan paths. PASS.
  - Gate 8 (`node --check`): exit 0. PASS.
  - Gate 9 (`disposition --shard 18 --strict`): exit 0. PASS.
  - Gate 10 (`self-test`): exit 0 — positive control covers disposition (9 known-bad inputs rejected + good block accepted + completeness/no-dup/sentinel checks). PASS.
  - Gate 11 (`all --strict`): exit 0 — manifest+corpus+evidence+qualification+disposition all PASS. PASS.
  - Gate 12 (Anti-Hollow): (a) all 15 revalidated blocks cite concrete evidence (test names + file:line + cross-ref inventory_ids; 3 deep-verified against live code); (b) no finding dropped (42/42); (c) counts: 15+2+25+0+0=42. PASS.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`: exit 0 after closure bookkeeping completed (all checkboxes ticked, closure evidence written).
  - `scan-hollow-implementations.mjs --module nop-stream --severity high`: exit 1 — reports pre-existing hollow patterns in nop-stream PRODUCTION code; OUT OF SCOPE for this audit-only plan (zero production-code changes). Informational only; does not block closure.
  - Deferred 项分类检查: no deferred items in this plan; all 42 findings disposed to terminal states.

Follow-up:

- No remaining plan-owned work. All 42 Shard 18 findings have terminal dispositions.
- Non-blocking follow-up: residual-risk P2/AR findings have owner_plan paths pointing to completed remediation plans (2026-08-04-2300-1/2/3). If any residual-risk finding is later reclassified as P0/P1, the successor remediation plan (or roadmap Stage 23 readiness decision) will handle it.
- `scan-hollow-implementations.mjs` findings in nop-stream production code are pre-existing and owned by the nop-stream-production remediation track, not this audit-only plan.
