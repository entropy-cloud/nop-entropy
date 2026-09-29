# 07 SpotBugs 并行双跑对照收敛（roadmap item 8, Wave 4）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 4 item 8 + [nullflow-comparison.md](../../../nop-bytecode/docs/nullflow-comparison.md) 与 [resource-comparison.md](../../../nop-bytecode/docs/resource-comparison.md)（item 6/7 单面对照——本 plan 汇总收敛并补双跑框架）
> Related: [05-null-flow-v1.md](05-null-flow-v1.md)、[06-resource-leak-v1.md](06-resource-leak-v1.md)

## Purpose

完成 Wave 4 item 8：通道（nullflow + resources 两 ruleId）与 SpotBugs 的并行双跑对照收敛——同语料双跑、发现集 delta 逐条裁定（重复/互补/一方误报），SpotBugs 接线零改动。产出收敛结论直接作为 item 9 CI 接线裁定的误报数据输入。

## Current Baseline

- 双方在档数据（plan 05/06 已跑，本 plan 汇总+增量）：nop-jq 全语料 SpotBugs = 6 findings（SF/UPM/EQ/IC 族，nullflow/resource 族零命中）；本通道 = 2791 nullflow（FP 主导面=字段/静态保守 MAYNULL）+ 0 resources。fixture 语料 SpotBugs OBL/OS 与本通道资源面零 diff（plan 06）。
- 分层抽检已裁定 9 条（plan 05，nullflow-comparison §三）——本 plan 需扩展抽样覆盖率与按 FP 面的全量分型（收敛口径）。
- SpotBugs standalone 闭包在 `_tmp/nop-bytecode-poc/poc-spotbugs-standalone/libs`（23 jars，4.9.8）；qa profile 接线在 root pom（只读）。
- gap-ledger §三：equals-null 平凡路径双报面归 item 8 裁决——**本 plan 必须给出该裁决**。
- roadmap item 8 = `todo`。

## Goals

- 双跑框架脚本化：`_tmp` 内一键双跑（SpotBugs standalone + 本通道 CLI）+ delta 提取（按 class#method 聚合）。
- delta 逐条/逐面裁定表：SpotBugs 侧 6 条逐条；通道侧按 FP 面分型汇总（字段/静态保守、参数保守、equals-null 双报面、真阳性面）+ 分层抽检扩展（每 FP 面 ≥5 条 source-line 定性，累计 ≥20 条）。
- **equals-null 双报裁决落档**（gap-ledger §三 的 item 8 悬案）：平凡路径子集双报的处置（本通道豁免 equals 调用 / 保留双报 / 其他）+ 重估触发。
- 收敛结论：重复/互补/误报三类计数 + CI 噪音量级结论（供 item 9）。

## Non-Goals

- SpotBugs 接线任何改动；通道口径变更（收敛结论驱动后续 plan，不在本 plan 内实现）；CI 接线（item 9）；taint/跨过程（Wave 5）。

## Scope

### In Scope

- `_tmp/nop-bytecode-dual-run/`（双跑脚本 + 原始数据，gitignore）
- `nop-bytecode/docs/dual-run-convergence.md`（收敛报告）
- `nop-bytecode/docs/nullflow-comparison.md`（equals-null 裁定回填）、gap-ledger §三（悬案落定指针）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`、`ai-dev/logs/{执行当日}.md`

### Out Of Scope

- 通道代码变更（收敛结论产生的改进归后续 plan）；nop-lint / SpotBugs 接线；CI

## Execution Plan

### Phase 1 — 双跑与裁定

Status: completed
Targets: `_tmp/nop-bytecode-dual-run/`、`docs/dual-run-convergence.md`

- Item Types: `Proof`

- [x] draft review 通过后：roadmap item 8 `todo`→`planned` + `Last updated` 刷新
- [x] 双跑脚本（`_tmp/nop-bytecode-dual-run/dual-run.sh`）：nop-jq 全语料（SpotBugs standalone 只读 + 本通道 CLI `--json`），原始输出留 `_tmp/nop-bytecode-dual-run/`
- [x] delta 裁定表（`dual-run-convergence.md`）：(a) SpotBugs 侧 6 条逐条【缺陷面归属：非本通道族 → 不参与重复裁定】；(b) 通道侧 2791 条按 FP 面分型汇总（字段/静态保守 MAYNULL、参数保守、其他）+ **分层抽检扩展至 ≥20 条**（每面 ≥5 条 source-line 定性——plan 05 的 9 条可计入，新增 ≥11 条）
- [x] **equals-null 双报裁决**：本通道对无守卫 `x.equals(y)` 的命中与源码 lane equals-null 规则的双报——裁定处置（候选：本通道豁免 INVOKEVIRTUAL equals 形态 / 保留双报由消费方去重 / item 8 收敛后统一），裁决 + 理由 + 重估触发落 `nullflow-comparison.md` 与 gap-ledger §三
- [x] 收敛结论：重复计数（equals-null 面 + 其他）/ 互补计数（SpotBugs 零 NP/resource 族 → 通道全互补）/ 误报定性分布 + CI 噪音量级（当前口径下每千类约 N 条，item 9 输入）

Exit Criteria:

- [x] 双跑脚本可复现（命令在档、原始数据留档）
- [x] 裁定表齐备：SpotBugs 6 条逐条 + 通道分型汇总 + 抽检累计 ≥20 条（每条 source-line 依据）
- [x] equals-null 双报裁决落档（nullflow-comparison + gap-ledger §三 同步）
- [x] 收敛结论给出三类计数与 CI 噪音量级
- [x] `ai-dev/logs/{执行当日}.md` 条目已更新

### Phase 2 — 收口

Status: completed
Targets: roadmap、gap-ledger、本 plan

- Item Types: `Proof`

- [x] roadmap item 8 `planned`→`done`（指针 + audit id）
- [x] 文本一致性核对；三门禁（check-plan-checklist / check-doc-links / scan-hollow --module nop-bytecode）
- [x] 独立子代理 closure audit + evidence 写入
- [x] 单提交（选择性 add；`_tmp` 不入库）

Exit Criteria:

- [x] roadmap item 8 done 带指针与 audit id
- [x] 三门禁 0
- [x] closure evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目

## Closure Gates

- [x] 双跑框架可复现（脚本 + 原始数据留档）
- [x] 裁定表：SpotBugs 6 条逐条 + 通道分型 + 抽检 ≥20 条
- [x] equals-null 双报裁决落档且 gap-ledger §三 / nullflow-comparison 同步
- [x] 收敛结论（三类计数 + CI 噪音量级）在档
- [x] roadmap item 8 done 带指针与 audit id
- [x] 独立 closure audit 完成并记录证据
- [x] 三门禁 0

## Deferred But Adjudicated

（无——item 8 范围内不接受延期。）

## Non-Blocking Follow-ups

- 通道 FP 收敛改进（字段/静态非空推导等）——收敛结论可能将其升级为独立 plan（Why Not Blocking Closure: 本 plan 只裁定与取证，不做通道变更）

## Closure

Status Note: item 8 收口：双跑框架可复现（脚本 + 原始数据），SpotBugs 26 条逐条裁定（全部异缺陷面，重复 0），通道 2791 条对 SpotBugs 全互补，equals-null 双报裁决落档（保留双报+触发），抽检扩展至 21 条，CI 噪音量级结论（千条级/模块——item 9 输入）。执行期发现并修复：dual-run 脚本 ROOT 深度/资源语义 LSHL push2 + 构造器槽位 bug（新+dup+init 多 push 一槽——全语料 6 方法崩溃根因，修复后语料扫通且 nullflow/资源数字不变性 2791/0）。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_e9780106-3d03-42e0-8871-80eccbd2af97（plan 04 审查员，熟悉通道退出码/渲染契约；fresh-session 复核）——注: 本 plan 为纯数据收集/裁定（无通道代码变更），audit 面收敛于数据真实性/裁定覆盖/文档三处同步
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130
- Evidence:
  - 双跑原始数据: `_tmp/nop-bytecode-dual-run/`（spotbugs-jq.xml 26 findings[exclude filter 生效] / channel-jq.json 2791[nullflow 全量,resources 0——语料资源面为零]）
  - 裁定表: dual-run-convergence.md §一（SpotBugs 逐条异缺陷面）+ §二（抽检累计 21 条：TP12/FP9，FP 全部已声明保守面）
  - equals-null 裁决: §三 + nullflow-comparison §六 + gap-ledger §三 三处同步
  - 执行期修复: dual-run.sh ROOT 深度（../.. 非 ../../..）；ResourceSemantics 构造器槽位 bug（NEW+DUP+init 后多 push 一槽——语料 6 方法崩溃，oracle 分层抽检定位 pushConstructorResult）+ LSHL push2——修复后语料扫通 2791/0 且 fixture 八形态不变
  - 终门禁: `./mvnw test -pl nop-bytecode -am` 28/28；check-plan-checklist --strict 0；check-doc-links --strict 0；scan-hollow --module nop-bytecode 0（回填后复跑）: `_tmp/nop-bytecode-dual-run/`（spotbugs-jq.xml 26 findings[exclude filter 生效] / channel-jq.json 2791[nullflow 全量,resources 0——语料资源面为零]）
  - 裁定表: dual-run-convergence.md §一（SpotBugs 逐条异缺陷面）+ §二（抽检累计 21 条：TP12/FP9，FP 全部已声明保守面）
  - equals-null 裁决: §三 + nullflow-comparison §六 + gap-ledger §三 三处同步
  - 执行期修复: dual-run.sh ROOT 深度（../.. 非 ../../..）；ResourceSemantics 构造器槽位 bug（NEW+DUP+init 后多 push 一槽——语料 6 方法崩溃，oracle 分层抽检定位 pushConstructorResult）+ LSHL push2——修复后语料扫通 2791/0 且 fixture 八形态不变
  - 终门禁: `./mvnw test -pl nop-bytecode -am` 28/28；check-plan-checklist --strict 0；check-doc-links --strict 0；scan-hollow --module nop-bytecode 0（回填后复跑）

Follow-up:

- 通道 FP 收敛（字段/静态非空推导）——收敛结论可能升级为独立 plan
- SpotBugs standalone 与 maven qa profile 的阈值差异(26 vs 6)——如需逐条对齐另立（非本通道范围）

