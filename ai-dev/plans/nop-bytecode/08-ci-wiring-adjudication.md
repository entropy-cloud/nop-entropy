# 09 CI 接线裁定 — report-only 进 CI 的数据驱动决策（roadmap item 9, Wave 4, M3）

> Plan Status: active
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 4 item 9 + [dual-run-convergence.md](../../../nop-bytecode/docs/dual-run-convergence.md)（item 8 误报数据）+ HC6 硬门禁不对称纪律
> Related: [07-spotbugs-dual-run.md](07-spotbugs-dual-run.md)

## Purpose

完成 item 9 的**接线裁定**（决策项，非必接线项）：基于 item 8 的对照收敛数据，裁定通道发现流是否/何时进入 CI（report-only 起步），落正式记录。完成后 M3 达成（专项进 CI[report-only]，并行期对照收敛——按裁定结果如实标注）。

## Current Baseline

- item 8 数据（dual-run-convergence.md）：通道 nop-jq 全语料 2791 条 nullflow findings——抽检 21 条 TP12/FP9，**主导 FP 面 = 字段/静态保守 MAYNULL（无构造器非空推导）**，全量 FP 率推算 30–60%，噪音量级千条级/模块；资源面 0（语料零资源）。
- HC6：report-only 起步；升 hard gate 须独立 plan + 对照期误报数据背书。
- 通道 CLI 已可本地运行（plan 04），无任何 CI 接线存在。
- roadmap item 9 = `todo`。

## Goals

- 数据驱动裁定落正式记录：基于 item 8 噪音数据裁定 **暂缓 CI 接线**（report-only 千条级噪音无消费价值），定义接线触发条件（FP 收敛后重评——字段/静态非空推导落地 + 重跑双跑确认噪音降至可消费量级），记录于 dual-run-convergence.md（§六 CI 裁定节）与 roadmap item 9。
- roadmap item 9 状态流转（done = 裁定完成，非必接线）+ M3 达成标注（如实措辞：对照收敛 + 接线裁定在档）。

## Non-Goals

- 任何 CI workflow 文件变更（裁定为暂缓即无变更；若未来触发则随触发 plan 落地）。
- 通道 FP 收敛实现本身（字段/静态非空推导归后续独立 plan——本 plan 只定义其触发地位）。
- hard gate 升级（HC6：本 plan 的数据恰恰不支持）。

## Scope

### In Scope

- `nop-bytecode/docs/dual-run-convergence.md`（追加 §六 CI 接线裁定节）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（item 9 + M3 动态块）
- `ai-dev/logs/{执行当日}.md`

### Out Of Scope

- `.github/workflows/**`；通道代码；nop-lint；SpotBugs 接线

## Execution Plan

### Phase 1 — 裁定落档与收口

Status: completed
Targets: `dual-run-convergence.md` §六、roadmap

- Item Types: `Decision`

- [x] draft review 通过后：roadmap item 9 `todo`→`planned` + `Last updated` 刷新
- [x] §六 CI 接线裁定节落档：**裁定 = 暂缓接线**，四要素齐备（(a) 裁定 + 理由 = item 8 噪音数据千条级/模块且主导 FP 面为已声明保守推定（无消费价值）；(b) 机制面证据 = dual-run 数据 + 抽检 21 条；(c) 触发条件 = 字段/静态非空推导落地 + 重跑双跑抽检 FP 率降至 ≤20% 或绝对量级降至百条级；(d) 重估触发 = 上述触发达成或 item 8 语料扩展发现资源面）；report-only CLI 保留本地/按需消费面（plan 04 交付不变）
- [x] roadmap item 9 `planned`→`done`（裁定指针；done 语义 = 裁定完成而非已接线——措辞如实）；M3 标注（对照收敛 + 接线裁定在档）
- [x] 文本一致性核对；三门禁（check-plan-checklist / check-doc-links / scan-hollow --module nop-bytecode）
- [x] 独立子代理 closure audit + evidence 写入
- [x] 单提交（选择性 add）

Exit Criteria:

- [x] §六 裁定节四要素齐备且与 item 8 数据一致
- [x] roadmap item 9 done（裁定语义如实）+ M3 标注
- [x] 三门禁 0
- [x] closure evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目

## Closure Gates

- [x] 裁定四要素齐备（裁定+理由+证据+触发）且引用 item 8 数据准确
- [x] HC6 语义守住（未升 hard gate；CI 文件零改动）
- [x] roadmap item 9 done 带指针与 audit id（裁定语义如实）；M3 标注
- [x] 独立 closure audit 完成并记录证据
- [x] 三门禁 0

## Deferred But Adjudicated

### CI report-only 接线

- Classification: `out-of-scope improvement`（相对本 plan——裁定即暂缓）
- Why Not Blocking Closure: 裁定本身是 item 9 的交付物；接线以 FP 收敛为触发，接入时机由触发 plan 承载
- Successor Required: `yes`
- Successor Path: 字段/静态非空推导 plan（后续立项）落地后随触发评估另立接线 plan

## Non-Blocking Follow-ups

- 字段/静态非空推导（FP 收敛主手）——接线触发条件的第一优先项
- 双跑对照语料扩展——触发评估时同跑

## Closure

Status Note: item 9 收口：CI 接线裁定落正式记录（暂缓接线——数据驱动：item 8 噪音千条级/模块且主导 FP 面为已声明保守推定；触发 = 字段/静态非空推导落地 + 双跑重跑 FP 率达标）。HC6 守住（无 hard gate、CI 文件零改动）。M3 达成（对照收敛在档 + 接线裁定在档——done 语义如实为裁定完成）。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_9c67970d-c3f1-43ec-b6e1-223a6b263182（独立 fresh-session 子代理，含裁定合理性反证）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_9c67970d
- Evidence:
  - 纯裁定计划（plan 24 先例）：无代码变更；dual-run-convergence.md §六 四要素齐备（裁定/理由/证据引用 item 8/触发+重估）——审计确认数据一致性（2791/21 条/TP12-FP9 与 §一/§二对齐、无虚增）与裁定合理性（反证站得住）
  - **首轮 closure audit REJECT**（收尾落盘断裂，非裁定缺陷）：roadmap item 8/9 状态行历次翻转静默失败（行文空格+粗体与替换串不匹配——item 8 为 plan 07 遗留、item 9 为本 plan）+ daily log 缺条目 + 零提交；M3 UNLOCKED 建立在 todo 矛盾上
  - 修复：roadmap item 8/9 行按文件实际文本精确落盘 done（item 8 附 plan 07 指针+audit id reconciliation；item 9 附裁定语义+plan 08 指针+audit id）；daily log 收口条目补写；选择性提交
  - 终门禁：check-plan-checklist --strict 0 / check-doc-links --strict 0 / scan-hollow --module nop-bytecode 0（最终回填后复跑）

Follow-up:

- 字段/静态非空推导（FP 收敛主手，接线触发条件）——后续独立 plan
- CI report-only 接线 plan——触发达成后另立

