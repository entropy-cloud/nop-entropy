# 29 WI22 指标与不变量钉入

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI22 行）、`ai-dev/design/nop-stream/observability-design.md`（五层指标规范）、`ai-dev/audits/nop-stream-invariants/`（gate-inventory / output-contract-registry / wiring-registry）
> Related: `ai-dev/plans/nop-stream-sql/25-wi17-sql-compiler.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

新算子接入 observability 五层指标面（operator 层语义计数，随 task 层自动覆盖）；OverWindowOperator/EquiJoinOperator 登记进 gate-inventory.json modules 映射与 invariant-catalog §4；重钉 WI17 audit MIN-1 在案的 invariants stale 漂移（output-contract V4/V5 + wiring V3 共 8 处）。

## Current Baseline（2026-10-03 实测）

- gate-inventory.json modules 三模块均无两新算子；invariant-catalog.md §4 人类表同缺；sync 只查 catalog→gate 方向，缺登记不会被比对发现（roadmap WI22 行明文）。
- output-contract-registry.json：V4 新发射点 4 处未登记（CepOperator :875/:1356、WindowOperator :1327/:1926——WI12-WI20 行号漂移）+ V5 stale 3 处（WindowOperator :1284/:2128、CepOperator :711、:1113 语义已迁移）。WI17 audit MIN-1 在案：父提交同等失败，非 WI17 引入，归本 WI。
- wiring-registry.json：V3 stale 7 处（StreamTaskInvokable :536/:537/:323/:329/:500/:509、GMCE :730）——WI12-WI20 行号漂移；GMCE 的 setStateBackend 调用点已随 WI14 重构迁入 TaskCheckpointWiring :175。
- 五层指标规范：operator 层关注算子记录数，真实挂点在 StreamTaskInvokable 数据面（自动覆盖所有算子）；算子专属语义计数先例 = WindowOperator numLateRecordsDropped / CepOperator late-drop（StreamMetricsRegistries + operator/subtask scope，W-M1 模式，location-less 回退实例身份）。

## Goals

- 两新算子 operator 层语义计数（W-M1 模式）：EquiJoinOperator numJoinOutputsEmitted（配对+补齐）、OverWindowOperator numOverRowsEmitted（rn+帧聚合行）。
- gate-inventory.json modules 登记两算子（机械分类器全量方法清单）+ invariant-catalog.md §4 人类表同步。
- output-contract-registry V4/V5 重钉归零；wiring-registry V3 重钉归零（IStateBackend GMCE 点迁移至 TaskCheckpointWiring :175 并注记）。
- WI17 audit MIN-1 关闭。

## Non-Goals

- engine/task/state 层新指标（已在 EngineMetrics/TaskNodeMetrics/CheckpointCoordinator 覆盖，新算子自动随 task 层）；修改五层命名规范；docs-for-ai/nop-stream.md 指标名表增量（无新层名）。

## Scope

### In Scope

- `nop-stream/nop-stream-core`：EquiJoinOperator 计数器 + gate-inventory/catalog 登记
- `nop-stream/nop-stream-runtime`：OverWindowOperator 计数器
- `ai-dev/audits/nop-stream-invariants/`：gate-inventory / output-contract-registry / wiring-registry 重钉
- 日志

### Out Of Scope

- WI23 quickstart；WI24 收口；per-key 指标维度。

## Execution Plan

### Phase 1 - 指标接入与登记

Status: completed
Targets: core/runtime/invariants

- Item Types: `Fix`

- [x] EquiJoinOperator/OverWindowOperator 语义计数器（W-M1 模式：open() 注册、instance-identity scope、copyForSubtask 新实例重注册）
- [x] gate-inventory.json + invariant-catalog.md §4 登记两算子（机械分类器方法清单）
- [x] output-contract-registry V4/V5 重钉 + wiring-registry V3 重钉（IStateBackend 迁移注记）
- [x] InvariantTableCompleteness（TestOutputContractInvariant）+ 全量门禁绿

Exit Criteria:

- [x] `check-nop-stream-invariants.mjs`（无参全量）0 violations——output-contract V4/V5 八处重钉 + wiring V3 七处重钉（IStateBackend GMCE 点迁至 TaskCheckpointWiring:175 并注记）+ 两算子登记后归零
- [x] `sync` OK；TestOutputContractInvariant 14/14 绿（双向精确等价含新登记）
- [x] core 1669 / sql 78 零退化；runtime `-am` 全量 1233/0F/0E 绿（audit 四象限矩阵：no-am 构件解析模式 1F——双层 local repo 缺 runtime/connector 构件致异构时间戳，与提交态零相关；FU-10 登记排查方向为 no-am 类路径完整性）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session）：**PASS**（1 Major MAJ-1 runtime 失败归因叙事失实——audit 四象限矩阵钉死为 no-am 构件解析模式问题与提交态零相关，四处文本随收口改写；3 Minor 注记清理随手完成）；证据落 ai-dev/audits/nop-stream-sql/wi22-closure-audit.md
- [x] audit 通过后 roadmap WI22 `todo` → `done`；解析器断言成立：items=31/milestones=7/done=29/WI22=done
- [x] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI22 = done + 解析器断言成立
- [x] 双门禁退出码 0

## Closure Gates

- [x] 两新算子 operator 层计数器落地（W-M1 模式）
- [x] gate-inventory + catalog §4 登记齐备（sync OK + TestOutputContractInvariant 14/14 绿）
- [x] invariants 三注册表 stale 漂移归零（无参全量 0 violations）
- [x] `./mvnw test` core/runtime/sql 全绿（audit 四象限矩阵：runtime no-am 1F 为构件解析模式问题与提交态零相关——详见 Exit Criteria 同条与 FU-10）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/29-wi22-metrics-and-invariants.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Current Baseline

（见上——登记与重钉为本 WI 缺口。）

## Closure

Status Note: 两新算子 operator 层计数器 + invariants 三注册表登记/重钉全量归零（WI17 audit MIN-1 关闭）；MAJ-1 runtime 失败归因按 audit 四象限矩阵改写为构件模式问题（与提交态零相关）。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Evidence: ai-dev/audits/nop-stream-sql/wi22-closure-audit.md——PASS；TestOutputContractInvariant 14/14 双向精确等价；无参全量 0 violations；core 1669/sql 78/runtime `-am` 1233 全绿

Follow-up:

- FU-10（no-am 构件解析模式类路径完整性排查）已登记 Follow-up Backlog
