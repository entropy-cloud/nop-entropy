# 05 — I5+I6 nop-metadata 不变式全量验证、门禁提升与 Cycle 1 收口

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 1 / I5（全量验证与门禁零命中）+ Cycle 1 / I6（循环收口与下一轮触发判定）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I5、I6）；I1 deferred 项"门禁提升为 hard CI gate"
> Related: 前置 `2026-08-13-1930-4-...`（I4 修复 81 项 red list 至门禁零命中）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`

## Purpose

I4 把 81 项 red list 全部修复至门禁零命中后：**(I5)** 全量验证 `./mvnw test` 全绿 + 4 条门禁零命中记录为 Cycle 1 收口基准，把门禁从 advisory 提升为**阻断式 hard CI gate**（非零即红）—— 收口 I1 deferred 项；**(I6)** 统计 Cycle 1 全量指标（门禁数 / red list / 修复 / 新族）、判定稳态、登记复触发条件，由**独立 fresh session** 做 closure audit，收口 Cycle 1。

> **I5+I6 合并理由**：I6 的 closure audit 需要验证 I5 的 hard-gate 在 CI 中确实阻断（Anti-Hollow）。分开会导致 I5 一次 audit + I6 再一次 audit 重复验证同一 hard-gate 有效性。两者均为小体量（验证 + 统计），合并为一次连贯的"验证→提升→收口"流程，一次 closure audit 覆盖全部。

## Current Baseline

> 本计划依赖 I4 完成（81 项 red list 全清、4 条门禁零命中 + 门禁硬化）。I4 完成前，下列为预期基线。

- **预期 I4 交付后**：4 条门禁全部零命中（`check-silent-swallow.mjs` exit 0 含硬化后信号、`check-orm-unique-key-constraint.mjs` exit 0、`check-sensitive-literal-leak.mjs` exit 0、`TestLimitNegativeValueInvariant` 0 failures）。
- **棘轮零点**：`formal-red-list.md` 81 项（80 silent-swallow + 1 limit）—— I4 修复后应全清，I5 做最终比对。
- **I1 deferred 项**："门禁提升为阻断式 hard CI gate"（`optimization candidate`，I4 违规清零前提升会立即破坏 CI）—— 本计划执行。
- **I1 non-blocking follow-up**："门禁聚合入口（统一调用 4 条门禁）" —— 本计划搭建。
- **CI 现状**（`.github/workflows/maven.yml`）：`build` job（:32-43）只有 `checkout` + `setup-java` + `mvn -B package`，**无 Node.js 环境**（Node 仅在 `e2e` job :62）。3 条 `.mjs` 门禁依赖 Node + `ai-dev/tools/node_modules`（`@ast-grep/ast-grep` 等，见 `ai-dev/tools/package.json`）。hard-gate 接入须先在 `build` job（或新建 `invariant-gate` job）增加 `setup-node` + 安装 `ai-dev/tools` 依赖。CI 中 Maven 用 `mvn -B`（非 `./mvnw`），本计划验证命令用 `./mvnw`（本地），CI 配置中沿用现有 `mvn` 约定。
- **对抗探查结论**（I2）：5 方向 0 新族，不触发 Cycle 2 / I1。
- **候选不变式**（I0 留待 I6 裁定）：`invariant-catalog.md` §候选不变式（:131-137）已有初步评估——类型/方言兼容性族（AR-20 / AR-23⑧，"单点命中，未见同族兄弟复发，暂不沉淀"）、并发竞态/UK 幂等族（P2-MA3-03，"已裁定 watch-only residual...暂不沉淀"）。I6 在此基础上做最终裁定。

## Goals

- `./mvnw test -pl nop-metadata -am -T 1C` 全绿（含默认 surefire 集合）。
- 4 条门禁零命中记录为 Cycle 1 收口基准（与 I1 棘轮零点 81 项比对：全清确认）。
- 4 条门禁提升为阻断式 hard CI gate（非零即红）+ 聚合入口搭建 + CI Node 环境就绪。
- Cycle 1 统计文档（门禁数 / red list / 修复 / 新族）+ 稳态判定 + 复触发条件登记。
- 独立 fresh session closure audit 完成并记录证据。

## Non-Goals

- **修复 red list** —— 那是 I4（已完成）。
- **新不变式族门禁实现** —— 那是 Cycle 2 / I1（I6 复触发判定后启动；本计划只登记触发条件，不实现）。
- **重新审计 / 重新跑对抗探查** —— 那是 I2（已完成）；本计划只验证 I4 修复结果 + 统计收口。

## Scope

### In Scope

- 全量测试验证 + 4 条门禁零命中记录（I5）。
- CI Node 环境就绪 + 门禁聚合入口搭建 + hard-gate CI 接入（I5）。
- owner-doc 同步（门禁使用说明 + 棘轮规则）。
- Cycle 1 统计 + 稳态判定 + 复触发条件登记（I6）。
- 独立 fresh session closure audit（I6）。

### Out Of Scope

- red list 修复（I4 已完成）。
- 新族门禁实现（Cycle 2 / I1）。
- 候选不变式正式沉淀 —— I6 只裁定"是否派 Cycle 2"，不实现。

## Execution Plan

### Phase 1 — 全量验证与门禁零命中记录（I5）

Status: planned
Targets: 门禁运行结果记录（`formal-red-list.md` 棘轮零点比对 / daily log）

- Item Types: `Proof`

- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿记录（0 failures，含 I4 新增 focused tests + 默认 surefire 集合）
- [ ] 4 条门禁复跑零命中并记录：
  - `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata` → exit 0
  - `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` → exit 0
  - `node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata` → exit 0
  - `./mvnw test -pl nop-metadata/nop-metadata-service -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false` → 0 failures
- [ ] 与 I1 棘轮零点（81 项）比对：81 → 0 全清确认

Exit Criteria:

- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（0 failures）
- [ ] 4 条门禁全部零命中（exit 0 / 0 failures）
- [ ] 棘轮零点比对：formal-red-list.md 81 项 red list 全清（逐族核对）
- [ ] **无静默跳过**：不得因门禁仍红而从 CI 排除门禁；未清项回退 I4
- [ ] No owner-doc update required（本 Phase 为验证，不改代码/契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 门禁聚合入口 + CI Node 环境就绪 + hard-gate 接入（I5，收口 I1 deferred）

Status: planned
Targets: `ai-dev/tools/run-nop-metadata-invariants.sh`（新建）、`.github/workflows/maven.yml`（CI 配置）、`docs-for-ai/02-core-guides/invariant-guards.md`（新建 owner-doc）、`docs-for-ai/INDEX.md`（路由更新）

- Item Types: `Fix | Decision`

> **聚合入口形式**：`ai-dev/tools/run-nop-metadata-invariants.sh`（bash 包装器，参考先例 `ai-dev/tools/run-java-lint.sh`）。调用序列：
> 1. `node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata`
> 2. `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata`
> 3. `node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata`
> 4. `mvn test -pl nop-metadata/nop-metadata-service -Dtest=TestLimitNegativeValueInvariant -Dsurefire.failIfNoSpecifiedTests=false`（CI 用 `mvn`，本地用 `./mvnw`）
>
> 退出码语义：`set -e`（任一门禁非零 → 整体非零）。扫描范围 = `--module nop-metadata`（与 I1-I4 一致；全仓扩展留待后续）。
>
> **CI Node 环境**：推荐新建独立 `invariant-gate` job（`needs: build`），职责分离——build job 编译失败 vs 门禁失败定位更清晰。Node 配置参照 e2e job 先例（`.github/workflows/maven.yml` :59-69）：`pnpm/action-setup@v4` version 10 + `setup-node@v4` node 20 + `cache-dependency-path: ai-dev/tools/pnpm-lock.yaml` + `pnpm install --frozen-lockfile`（`working-directory: ai-dev/tools`）。
>
> **聚合入口 mvn 检测**：`run-nop-metadata-invariants.sh` 用环境变量切换 Maven 命令——`MVN="${MVN:-./mvnw}"`（默认本地 `./mvnw`，CI 注入 `MVN=mvn`）。
>
> **owner-doc 目标**：新建 `docs-for-ai/02-core-guides/invariant-guards.md`（门禁清单 + 运行命令 + 棘轮规则 + 表完备性说明），更新 `docs-for-ai/INDEX.md` 路由。

- [ ] 新建聚合入口 `ai-dev/tools/run-nop-metadata-invariants.sh`（参考 `run-java-lint.sh` 结构，`set -e`，4 条门禁序列）
- [ ] 在 `.github/workflows/maven.yml` 增加 Node 环境（`build` job 内 or 新建 `invariant-gate` job）：`setup-node@v4` + 安装 `ai-dev/tools` 依赖
- [ ] 接入 CI：聚合入口调用为 CI 步骤（非零即红）
- [ ] 新建 `docs-for-ai/02-core-guides/invariant-guards.md` + 更新 `docs-for-ai/INDEX.md`（门禁清单 + 运行命令 + 棘轮规则）
- [ ] 验证 hard-gate 有效性（proof，见下）

Exit Criteria:

- [ ] 聚合入口 `run-nop-metadata-invariants.sh` 存在且可一键调用 4 条门禁，退出码语义正确（任一非零 → 非零）
- [ ] CI 配置含 Node 环境（`setup-node` + `ai-dev/tools` 依赖安装），3 扫描器可在 CI 中运行
- [ ] CI 中聚合入口为显式步骤，非零即红
- [ ] **接线验证**：聚合入口确实被 CI 调用（CI YAML 中有对应 step，非孤立脚本）
- [ ] **hard-gate proof**（二选一，记录 evidence）：
  - **(A) 本地 proof（首选，低门槛）**：本地在 nop-metadata 产品代码中故意引入一条 silent-swallow catch（如临时加一个空 catch）→ 跑聚合入口 → 确认 exit ≠ 0 → 移除 → 跑聚合入口 → 确认 exit 0。记录两次运行的输出摘要。
  - **(B) CI proof（如 GitHub Actions 可用）**：在 PR 分支故意引入一条命中 → push → 确认 CI `invariant-gate`/`build` job 红 → revert → 确认 CI 绿。记录两个 run URL。
- [ ] `docs-for-ai/02-core-guides/invariant-guards.md` 已创建（门禁清单 + 运行命令 + 棘轮规则）；`docs-for-ai/INDEX.md` 已更新路由
- [ ] **无静默跳过**：门禁不得被 `|| true` / `continue-on-error` 等方式在 CI 中静默放行
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 仍全绿（hard-gate 提升不破坏默认构建；`TestLimitNegativeValueInvariant` 仍从默认 surefire 排除）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Cycle 1 收口统计与稳态判定（I6）

Status: planned
Targets: Cycle 1 收口统计文档（daily log / audit 目录）

- Item Types: `Decision | Proof`

- [ ] 统计 Cycle 1 全量指标：门禁数（4）、red list 总数（81）、修复数（81）、对抗探查新族数（0）
- [ ] 稳态判定：4 门禁零命中 + I2 对抗探查 0 新族 → 是否稳态暂停
- [ ] 候选不变式最终裁定（初步评估见 `invariant-catalog.md` §候选不变式 :131-137）：类型/方言兼容性族 + 并发竞态/UK 幂等族 → 裁定是否派 Cycle 2 / I1（附理由；已有初步评估为"暂不沉淀"，I6 确认 or 推翻）
- [ ] 登记复触发条件：CI 变红（门禁命中）/ 新增或重命名 processor / bizmodel / ORM entity / 周期复探
- [ ] 独立 fresh session closure audit：验证 I4 修复 + I5 门禁零命中 + hard-gate CI 接入 + anti-hollow + deferred 项分类诚实

Exit Criteria:

- [ ] Cycle 1 统计文档存在（门禁数 / red list / 修复 / 新族 / 候选不变式裁定）
- [ ] 稳态判定有明确结论（稳态暂停 or 需 Cycle 2 + 理由）
- [ ] 候选不变式裁定有明确归属（派 Cycle 2 / watch-only / 不沉淀 + 理由，基于 catalog 初步评估确认 or 推翻）
- [ ] 复触发条件已登记（CI 变红 / 结构变更 / 周期复探）
- [ ] **端到端验证**：从"I4 修复 81 项"→"I5 门禁零命中 + hard-gate CI 接入"→"I6 稳态判定"链条完整、无断点
- [ ] 独立 fresh session closure audit 完成并记录证据（验证全部 Phase Exit Criteria + Closure Gates）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划含 CI 配置变更 + owner-doc 新建（不改产品逻辑代码），保留构建验证。`scan-hollow-implementations.mjs` 不适用（不改产品代码）。

- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [ ] 4 条门禁全部零命中（exit 0 / 0 failures），与 I1 棘轮零点比对全清
- [ ] 门禁已提升为 hard CI gate（非零即红），聚合入口 `run-nop-metadata-invariants.sh` 可一键调用
- [ ] CI Node 环境就绪（3 扫描器可在 CI 中运行）
- [ ] hard-gate proof 已完成并记录 evidence（本地 proof or CI proof）
- [ ] `docs-for-ai/02-core-guides/invariant-guards.md` 已创建 + `docs-for-ai/INDEX.md` 已更新
- [ ] Cycle 1 统计 + 稳态判定 + 候选不变式裁定 + 复触发条件登记均已完成
- [ ] 不存在被静默降级到 deferred 的 in-scope 项（I1 deferred "hard-gate 提升"已在本计划落地）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）hard-gate 确实在 CI 中阻断（proof evidence 可核）；（b）聚合入口确实调用全部 4 条门禁（非空壳）；（c）稳态判定基于 live 门禁实测非声称
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（如产出含链接的 .md；pre-existing errors 除外，新增 owner-doc 0 broken links）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（本计划无新增 deferred。I1 deferred 项"门禁提升为 hard CI gate" + non-blocking follow-up"聚合入口"均在本计划 Phase 2 落地。）

## Non-Blocking Follow-ups

- Cycle 2 / I1 新族门禁实现（仅当 I6 复触发条件触发时启动，非本计划拥有）。
- 候选不变式（类型/方言兼容性、并发竞态）若 I6 裁定为 watch-only，登记后待周期复探。
- 全仓门禁扩展（当前 `--module nop-metadata`，其他模块门禁待各自 mission）。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立 fresh session 审阅者>>
- Evidence: <<closure 时填写：每条 Exit Criterion + Closure Gate 的验证结果>>
