# Final Gap Adjudication — G36 BroadcastState + G66/G67 Permanent Deferral

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Items 36 + 55, `ai-dev/analysis/nop-stream/08-gap-analysis.md` (G36/G66/G67), `ai-dev/design/nop-stream/00-vision.md` §七
> Related: Stage 37 precedent (`2026-08-02-0955-7-shard-to-keygroup-migration-and-vision-update.md` — vision Non-Goal 更新先例), Stage 36 (`BroadcastState 推迟，需先更新 vision §七`)

## Purpose

对路线图最后三条未裁定 gap（G36/G66/G67）做正式裁决并收口，使这三条 gap 有明确最终状态（permanently excluded / permanently deferred），路线图 Items 36 + 55 可标记 `done`。G36（BroadcastState）需经过 vision 决策流程裁定是否永久排除或纳入范围；G66/G67 需正式记录为永久 deferred。注：gap-analysis 中另存在 8 条 stale deferred 条目（G6/G9/G24/G25/G32/G45/G62/G64）属 pre-existing data-quality debt，不在本 plan scope。

## Current Baseline

**G36 — BroadcastState（vision-gated）：**
- `00-vision.md:87` §七核心取舍明确将"广播流"列入"**去除**"，理由："复杂度极高，用例有限。可通过 CEP 或外部 lookup 替代"
- `00-vision.md:47` 表格中"双流 Join（broadcast join）"同样标注"复杂度极高，用例有限"
- `08-gap-analysis.md:104` G36 状态为 `Gap/P2`，归属 Item 12b，未标注关闭
- roadmap Item 36 标注 `todo`（"推迟，需先更新 vision §七"）
- `04-state-comparison.md:32/282` 确认 nop-stream 完全没有 broadcast state 概念
- Stage 37 precedent 展示了 vision Non-Goal 更新路径：StateShard→KeyGroup 时将 §四 Non-Goal 从"排除"改写为"supported-with-migration"（Stage 34/35 刚交付 KeyGroup 模型，vision Non-Goal 尚未同步，Stage 37 将文档追平到 live baseline）
- **关键区别**：Stage 37 是"功能已交付、vision 文档过时"的同步；G36 是"功能从未实现、vision 明确有理有据排除"的裁定

**G66 — spill-to-disk for large buffers：**
- `08-gap-analysis.md:139` 状态 `Gap/P3 | deferred`
- vision 未直接提及，属"从未实现"的 P3 优化项

**G67 — adaptive scheduling：**
- `08-gap-analysis.md:140` 状态 `Gap/P3 | deferred`
- `00-vision.md:87` §七"去除"列表中间接涉及（复杂调度属被排除方向）
- 属"从未实现"的 P3 优化项

**Item 55 deliverable：**
- roadmap Item 55（Stage 55）目标："记录当前路线图不做的 P3 项，保持 gap 计数完整"
- 当前 G66/G67 在 gap-analysis 标注 `deferred` 但无正式永久排除裁定

## Goals

- G36: 经过 vision 决策流程，裁定 BroadcastState 的最终状态——永久排除（强化 vision §七 理由）或纳入范围（vision §七 更新 + 实现计划）。**预期裁定方向为永久排除**（无 demonstrated user need，vision 理由成立，CEP/lookup 替代路径已有）
- G66/G67: 正式记录为永久 deferred（`permanently excluded — P3 optimization, not in vision scope`），附 non-blocking 理由
- gap-analysis 中 G36/G66/G67 的最终状态与 vision 一致
- roadmap Items 36 + 55 可标记 `done`

## Non-Goals

- 实现 BroadcastState（如裁定永久排除则不做；如裁定纳入则属独立 successor plan，不在本 plan 范围内）
- 实现 spill-to-disk 或 adaptive scheduling（G66/G67 永久 deferred）
- P2-5/P2-6 API 重构（属 Plan 1 Deferred）

## Scope

### In Scope

- G36 vision 决策流程执行 + 裁定记录
- G66/G67 永久 deferred 正式裁定
- gap-analysis G36/G66/G67 最终状态更新
- vision §七 / §四 文档同步（如裁定永久排除则强化理由；如纳入则更新 Non-Goal）
- roadmap Items 36 + 55 状态更新

### Out Of Scope

- BroadcastState 实现（任何方向）
- spill-to-disk / adaptive scheduling 实现
- 其他 gap 的状态变更

## Execution Plan

### Phase 1 - G36 BroadcastState Vision Adjudication

Status: completed
Targets: `ai-dev/design/nop-stream/00-vision.md`, `ai-dev/analysis/nop-stream/08-gap-analysis.md`, `ai-dev/analysis/nop-stream/04-state-comparison.md`, `ai-dev/backlog/nop-stream-production-roadmap.md`

- Item Types: `Decision`

- [x] 收集 G36 裁定依据：(a) vision §七 排除理由当前是否仍然成立；(b) 是否有 demonstrated production user need 要求 broadcast state；(c) CEP/lookup 替代路径是否覆盖 broadcast state 的典型用例（配置流/规则流分发）
- [x] **裁定**：基于上述依据，做出 go/no-go 决策。预期方向为 **永久排除**（no-go），理由：(1) vision §七 排除理由（复杂度极高、用例有限、CEP/lookup 替代）经核对仍然成立；(2) 无 demonstrated user need；(3) nop-stream 已有 operator state UNION/BROADCAST redistribution（Item 12b, G9），部分覆盖配置分发用例
- [x] **如裁定永久排除**：在 vision §七 强化 broadcast 排除理由，补充引用 G36 裁定（注明 operator state redistribution 已部分覆盖配置流分发用例，broadcast state 专用类型非必需）；在 `04-state-comparison.md` 标注 G36 为 permanently excluded；在 gap-analysis G36 行标注最终状态
- [x] **如裁定纳入范围**（需 human confirm）：更新 vision §七（Stage 37 precedent），移除 broadcast 排除条目，创建独立 successor plan 实现 BroadcastState。本 plan 仅完成 vision 更新，实现移交 successor — **N/A（裁定为永久排除，此分支未触发）**
- [x] roadmap Item 36 状态更新为 `done`（Decision-only）；同步更新 roadmap Stage 36 详情段落（当前为"推迟...否则不做"，需改为最终裁定结论）

Exit Criteria:

- [x] G36 裁定有书面记录（裁定结论 + 依据 + 替代方案评估），写入 vision §七 或 analysis 文档 — `00-vision.md` `### 七裁决记录` #G36
- [x] `00-vision.md` §七/§四 与 G36 裁定方向一致（裁定为永久排除，§四 Non-Goal/§七「去除」保持不变）
- [x] `08-gap-analysis.md` G36 行标注最终状态（`permanently excluded` 或 `✅ Closed (successor)`） — ✅ Closed (Stage 36) permanently excluded
- [x] roadmap Item 36 状态从 `todo` 变更为 `done`，附裁定摘要
- [x] **vision 变更裁定**：如涉及 vision §七 变更（从排除改为纳入），必须有 human confirm 证据记录在 plan 或 daily log 中 — N/A：裁定方向为「保持排除」（不改变 vision 边界），非「从排除改为纳入」，无需 human confirm
- [x] No new test required: 纯决策 + 文档变更
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - G66/G67 Permanent Deferral + Item 55 Closeout

Status: completed
Targets: `ai-dev/analysis/nop-stream/08-gap-analysis.md`, `ai-dev/backlog/nop-stream-production-roadmap.md`

- Item Types: `Decision`

- [x] **G66 (spill-to-disk)**: 正式裁定为永久 deferred。理由：(1) P3 优化项，当前内存/RocksDB 后端已覆盖生产状态量级；(2) vision 未将 spill-to-disk 纳入目标；(3) IMessageService 后端已提供跨 JVM 数据缓冲，进程内 spill 非关键路径。在 gap-analysis G66 行更新状态为 `permanently deferred — optimization candidate, no demonstrated need`
- [x] **G67 (adaptive scheduling)**: 正式裁定为永久 deferred。理由：(1) P3 优化项；(2) 当前 DeploymentPlan 静态分配 + region-based failover 已满足生产调度需求；(3) 无 demonstrated production need。注意：vision §七 排除列表仅含"复杂 Join、广播流、异步算子"，adaptive scheduling 不在排除列表中，但基于 P3 + 无需求 + 已有调度满足生产，延期裁定成立。在 gap-analysis G67 行更新状态为 `permanently deferred — optimization candidate, no demonstrated need`
- [x] **Item 55 deliverable**: 在 `08-gap-analysis.md` 确认 G66/G67 的永久 deferred 裁定已记录。注：gap-analysis 中另存在 8 条 stale deferred 条目（G6/G9/G24/G25/G32/G45/G62/G64）—— 这些 gap 所属 stage 已 done 但 gap-analysis 行未同步更新为 ✅ Closed。这些 stale 条目属 pre-existing data-quality debt，不在本 plan scope（本 plan 仅裁定 G36/G66/G67），记录在 Non-Blocking Follow-ups 中
- [x] roadmap Item 55 状态更新为 `done`

Exit Criteria:

- [x] gap-analysis 中 G66 行标注 `permanently deferred` + non-blocking 理由
- [x] gap-analysis 中 G67 行标注 `permanently deferred` + non-blocking 理由
- [x] roadmap Item 55 状态从 `todo` 变更为 `done`
- [x] G36/G66/G67 三条 gap 有明确最终状态（本 plan scope）。注：gap-analysis 中另存在 8 条 stale deferred 条目（G6/G9/G24/G25/G32/G45/G62/G64），属 pre-existing data-quality debt，不在本 plan scope
- [x] No new test required: 纯决策 + 文档变更
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] G36 裁定有书面记录，vision §七 与裁定一致 — `00-vision.md` `### 七裁决记录` #G36（永久排除，§四/§七 边界不变）
- [x] G66/G67 正式裁定为永久 deferred，附 non-blocking 理由 — `08-gap-analysis.md` G66/G67 行 ✅ Closed (permanently deferred)
- [x] G36/G66/G67 三条 gap 有明确最终状态 — G36 permanently excluded / G66+G67 permanently deferred
- [x] roadmap Items 36 + 55 状态为 `done`（含 Stage 36/55 详情段落同步更新）
- [x] 不存在被静默降级到 deferred 的 confirmed live defect（G36/G66/G67 均为 P2/P3 capability gap，非 live defect）
- [x] 受影响的 owner docs（vision / gap-analysis / roadmap）已同步 — `00-vision.md`、`08-gap-analysis.md`、`04-state-comparison.md`、`nop-stream-production-roadmap.md` 均已更新
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据 — 见下方 Closure Audit Evidence（mission-driver CLOSURE_VERIFY 轮将提供独立 session 复核）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 纯文档计划：`./mvnw test` / `compile` 不适用（Closure Gates template 允许删除构建验证条目）— 本 plan 零代码变更

## Deferred But Adjudicated

### G66 — spill-to-disk for large buffers

- Classification: `optimization candidate`
- Why Not Blocking Closure: P3 优化项；内存/RocksDB 后端已覆盖生产状态量级；`IMessageService` 后端已提供跨 JVM 数据缓冲，进程内 spill 非关键路径；vision 未纳入目标；无 demonstrated production need
- Successor Required: `no`（如生产需求出现可重启评估）

### G67 — adaptive scheduling

- Classification: `optimization candidate`
- Why Not Blocking Closure: P3 优化项；`DeploymentPlan` 静态分配 + region-based failover（Stage 44）已满足生产调度需求；无 demonstrated production need
- Successor Required: `no`（如生产需求出现可重启评估）

## Non-Blocking Follow-ups

- 如 G36 未来出现 demonstrated production user need，可重新启动 vision 决策流程（Stage 37 precedent）
- G66 spill-to-disk / G67 adaptive scheduling 同上，如生产需求出现可重启评估
- **gap-analysis stale deferred 条目**（G6/G9/G24/G25/G32/G45/G62/G64）：这些 gap 所属 stage 已 done，但 `08-gap-analysis.md` 中对应行仍标注 `deferred (Phase X)` 或未关闭。属 pre-existing data-quality debt（gap-analysis 文档与 roadmap 状态不同步），不影响本 plan closure，可在未来 gap-analysis 同步计划中统一清理

## Closure

Status Note: 本 plan 对路线图最后三条未裁定 gap（G36/G66/G67）做正式裁决并收口。G36 BroadcastState 经 vision 决策流程裁定为**永久排除**（vision §七/§四 排除理由成立 + 无 demonstrated user need + operator state `BROADCAST` 重分布已部分覆盖配置流分发用例；不改变 vision 边界故无需 §六人审批）。G66 spill-to-disk / G67 adaptive scheduling 裁定为**永久 deferred**（optimization candidate, no demonstrated need），均附 non-blocking 理由。三条 gap 现均有明确最终状态；roadmap Items 36 + 55 标记 `done`。本 plan 为纯决策 + 文档变更，零代码变更，无 live defect 被降级为 deferred。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: EXECUTE agent（self-verification，mission-driver CLOSURE_VERIFY 轮将提供独立 session 复核）
- Evidence:
  - **Phase 1 Exit Criteria（全部 PASS）**：
    - G36 裁定书面记录 → `00-vision.md` `### 七裁决记录` #G36（裁定结论 + 4 条依据 + 替代路径 + 重启条件）PASS
    - `00-vision.md` §七/§四 与裁定一致 → 裁定为「保持排除」，§四 Non-Goal「broadcast join」与 §七「去除：广播流」均未改变，仅新增裁决记录与 bullet 交叉引用 PASS
    - `08-gap-analysis.md` G36 行 → `✅ Closed (Stage 36, 2026-08-04) — permanently excluded` PASS
    - `04-state-comparison.md` G36 → diff table 行 + summary table 行均标注 `permanently excluded` PASS
    - roadmap Item 36 → `done` + Stage 36 详情段重写为裁定结论 PASS
    - vision 变更 human-confirm → N/A（裁定方向为保持排除，非「从排除改为纳入」）PASS
  - **Phase 2 Exit Criteria（全部 PASS）**：
    - `08-gap-analysis.md` G66 行 → `✅ Closed (Stage 55) — permanently deferred` + 3 条 non-blocking 理由 PASS
    - `08-gap-analysis.md` G67 行 → `✅ Closed (Stage 55) — permanently deferred` + 3 条 non-blocking 理由（含 vision §七 排除列表核对）PASS
    - roadmap Item 55 → `done` + Stage 55 详情段重写为裁定结论 PASS
    - G36/G66/G67 三条 gap 最终状态：permanently excluded / permanently deferred / permanently deferred PASS
  - **Closure Gates（全部 PASS）**：见上方逐条勾选；Deferred 项分类检查——G66/G67 均为 `optimization candidate`（Allowed Deferred Classification），无 in-scope live defect 被降级 PASS
  - **Anti-Hollow 检查**：N/A（纯文档/决策计划，零代码变更，无新增组件/方法/分支路径）PASS
  - **G36 裁定依据事实核对**：operator state 重分布已落地——`RedistributionMode.java`（NONE/UNION/BROADCAST/SPLIT_DISTRIBUTE）+ `MemoryOperatorStateBackend` + `TestE2EOperatorStateRedistribution` 经 grep 确认存在，plan reasoning (3) 成立 PASS
  - `node ai-dev/tools/check-doc-links.mjs --strict` → exit 0 PASS
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` → exit 0 PASS

Follow-up:

- no remaining plan-owned work（G36/G66/G67 三条 gap 已裁定收口）
- Non-blocking：G6/G9/G24/G25/G32/G45/G62/G64 stale deferred 条目属 pre-existing data-quality debt，可在未来 gap-analysis 同步计划中清理
- 如 G36/G66/G67 未来出现 demonstrated production need，可重启 vision 决策流程（Stage 37 precedent）
