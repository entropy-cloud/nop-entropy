# Wave 7 — nop-ib 性能实验最大化（实验驱动）

> Plan Status: completed
> Last Reviewed: 2026-11-16
> Source: [nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md) Wave 7
> Related: [wave6-audit-and-fixes.md](wave6-audit-and-fixes.md)（已完成）、[external-project-support-matrix.md](external-project-support-matrix.md) 实测 Runbook

## Purpose

把 campaign 实测口径固化为可重复 bench harness，定位 top-3 瓶颈，逐项裁决 E1–E6 实验矩阵（采纳需 ≥20% 提升且零假绿；拒绝项登记 Deferred 不留半实现）。

## Current Baseline

> 2026-11-16 live repo

- 模块并行 sweep 已存在（design 06 WR-D6：moduleTestPool + workerPermits，`nop.ib.workerBudget` 可调）；E4 的"激活条件"（Deferred 27）事实上已部分落地，实验内容改为串行-vs-并行的实测对照。
- 差分重跑为模块级全量 fresh fork（design 06 WR-D4）；`nop.ib.moduleRerunTimeoutSeconds` 唯一调节钮。E1 的"仅失败类子集重跑"模式不存在。
- worker 批大小 `nop-ib.config.json test.workerMaxClasses`（默认 20）；worker 数=公平份额（budget/slots）。E2 网格可用现有钮。
索引刷新为全量重扫（UnitIndexer.scan 全产物目录）；E3 增量刷新不存在。跨运行失败类子集重跑机制已存在（--rerun-failed，TestCommand），E1 的 delta 是 rerun-gate 内的 mode 开关+裁定语义，非新机制（对抗审查 Minor 修订）。
- zeebe-workflow-engine/spring-batch-core 的阶段耗时数据散在今日日志（/tmp/w6-*），无结构化分解。
- bench 命令存在（BenchCommand：noop/singleMethod/singleAbi/affectedTests 四指标）但无跨项目 harness。

## Goals

- Phase 1: bench harness 固化（repo 内可重复脚本 + 结果表）+ 五项目基线表。
- Phase 2: 瓶颈画像（spring-batch-core + zeebe 最重模块的阶段分解，top-3 瓶颈数据结论）。
- Phase 3: E1–E6 逐项实验（实现开关→实测→数据表→采纳/拒绝裁定）。
- Phase 4: 文档收口（design 05/06/roadmap）+ closure audit。

## Non-Goals

- 未达采纳门槛（≥20% + 零假绿）的实验不并入默认行为（开关保持默认关闭）。
- JUnit4-only 项目不承接。
- 不改 CI（Deferred 30 维持）。

## Execution Plan

### Phase 1 — bench harness 固化 + 基线表

Status: completed
Targets: `nop-tools/nop-ib/tools/bench-e2e.sh`、`_tmp/bench/`

Item Types: `Feature | Proof`

- [x] 可重复 bench 脚本：项目 × {mvn test, ib test 主路径, ib 回退路径} × 冷/温双态，输出 JSON 结果表（`tools/bench-e2e.sh`，入 repo）
- [x] 五项目（roadmap 口径：zeebe/shardingsphere/spring-batch/flink-agents/jsr352）≥1 模块基线实测（jsr352 跳过 test-deployment 的 provision 绑定失败模块，换 jberet-core/se 先例模块；flink-agents 以 fallback 路径为 oracle——fork-134 环境缺陷），结果表入库（`_tmp/bench/baseline.json` + analysis 文档表）
- [x] `ai-dev/logs/` 条目

Exit Criteria:

- [x] harness 脚本在 repo，重跑两次结果可复现（±15%）
- [x] 五项目基线表入库
- [x] daily log 更新

### Phase 2 — 瓶颈画像

Status: completed
Targets: `ai-dev/analysis/`

Item Types: `Proof`

- [x] spring-batch-core 与 zeebe-gateway-grpc（或 workflow-engine，以可测性为准）的阶段耗时分解：selection/index/discovery/执行/rerun-gate（从 RunLog JSON + debug 输出解析）
- [x] top-3 瓶颈有数据结论（写成 analysis 文档，供 E1–E6 优先级参考）

Exit Criteria:

- [x] 阶段分解表（两模块）+ top-3 瓶颈结论入库
- [x] daily log 更新

### Phase 3 — E1–E6 实验矩阵

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/**`（实验开关）、`ai-dev/analysis/`

Item Types: `Feature | Proof`

- [x] E1 分档重跑：`nop.ib.moduleRerunMode=failed-only`（仅失败类子集重跑）。**裁定语义（对抗审查 Major-a）**：子集复现失败 ⇒ FAILED（全量 rerun 跳过，节省=实测）；子集通过 ⇒ **verdict 仍由全量 fresh fork 决定**（fail-safe；Activiti 顺序敏感先例），仅记录 divergence 标记与子集耗时——采纳候选=divergence=0 时的时间节省，默认开关关闭
- [x] E2 worker 批调度：maxClasses/worker 数在 1/5/10/20 网格的吞吐-隔离曲线（含 fork abort 类模块可跳过，标注）
- [x] E3 索引增量刷新：pom/类变更后只重扫受影响产物目录 vs 全量重扫的正确性对照 + 耗时
- [x] E4 并行 sweep：-pl 多模块并行 vs 串行墙钟对照。现有 MODULE_SLOTS 为 workerBudget 派生值无独立钮（对抗审查 Major-b）→ 实验新增 `nop.ib.moduleSlots` 覆盖（默认不变，串行=1）+ 单元钉；产物互斥已有证明（6A-5 B-6）
- [x] E5 大模块 fast-path 分批：重模块 rerun 单 fork 大批崩溃/超时的分批 fresh fork 合并 vs 回退对照
- [x] E6 类级 fork 缓存：未变更类 discovery 结果缓存（测试面=f(类哈希)）的正确性对照
- [x] 每项：数据表 + 采纳/拒绝裁定；采纳项端到端实测 ≥20% 且漏测 oracle 抽查零假绿；拒绝项登记 Deferred

Exit Criteria:

- [x] E1–E6 各有数据表 + 显式裁定（采纳/拒绝 + 证据）
- [x] 采纳项（若有）：默认开关关闭、实测 ≥20%、零假绿抽查记录
- [x] 新增开关各有单元测试或最小验证钉
- [x] daily log 更新

### Phase 4 — 文档收口 + closure

Status: completed
Targets: design 05/06、roadmap Wave 7

- [x] design 05/06 增补实验结论（采纳项契约；拒绝项一句话理由）；design 06 增补 E1 裁定语义段（对抗审查 Major-a）
- [x] roadmap Wave 7 checklist 勾选 + Status
- [x] 独立子 agent closure audit APPROVED，Evidence 落本 plan
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿；提交

Exit Criteria:

- [x] bench harness 可重复、五项目基线表入库；top-3 瓶颈有数据结论
- [x] E1–E6 每项有采纳/拒绝裁定与证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出 0
- [x] closure audit APPROVED
- [x] daily log + 提交

## Closure Gates

- [x] bench harness 在 repo 且可复现
- [x] 五项目基线表 + 两模块瓶颈分解 + top-3 结论入库
- [x] E1–E6 全部有显式裁定与证据，无半实现残留
- [x] 采纳项（若有）≥20% + 零假绿 + 默认关闭
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [x] 独立 closure audit APPROVED（evidence 落 plan）
- [x] daily log + 提交

## Deferred But Adjudicated

（执行中填充）

## Non-Blocking Follow-ups

- （空）

## Closure

Status Note: Wave 7 收口：harness 入 repo 且五项目基线实测在档（小测试重模块 ib 1.6–5.5x，大模块失败路径 0.39x 诚实数据）；瓶颈画像 top-3 有数据结论；E1/E2/E3/E5 拒绝（数据背书，开关默认关闭+钉）、E4 确认既有默认、E6 裁定已被单元索引覆盖——采纳为新默认：无。nop-ib 77/77 绿。
Completed: 2026-11-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session, read-only）
- Evidence:
  - G1 三份 analysis 文档在档含数据表；harness 可执行；PASS
  - G2 roadmap 勾选逐条可溯；premature audit tick（Blocker）已修：roadmap Wave 7/8 audit 行反勾、Status 改 in audit→done（本 audit 后）
  - G3 代码抽查：E1 子集通过仍由全量裁定（TestCommand ~L611）、E5 merge 任一批红则红、E4 moduleSlots floor 1；PASS
  - G4 `./mvnw test -pl nop-tools/nop-ib` 76/76→修复后 77/77（含新增 E5 merge 钉）exit 0；PASS
  - G5 拒绝裁定全部数据背书；"采纳为默认：无"与 ≥20% 门槛条款一致；PASS
  - Audit findings 1-4 已修复：roadmap 反勾、E4 item 勾选、E5 merge 钉新增（executeRerun 重构 mergeBatches+钉）、daily log 项勾选
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出 0（completed 后验证）
