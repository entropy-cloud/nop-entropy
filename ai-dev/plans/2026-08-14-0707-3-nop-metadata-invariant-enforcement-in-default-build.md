# 03 — nop-metadata INV-LIMIT 默认 surefire 防御纵深（剔除陈旧排除配置）

> Plan Status: active
> Last Reviewed: 2026-08-14
> Mission: nop-metadata-invariant-loop
> Source: `ai-dev/audits/2026-08-14-0707-multi-audit-nop-metadata-invariant-loop.md`（F3）
> Related: Cycle 1 收口 `2026-08-13-1930-5-...`（I5 hard-gate CI 接入）、`2026-08-13-1930-2-...`（Workstream B 引入该排除）

## Purpose

> **重要事实订正（独立审查 + lead agent 双重核实 live CI）**：F3 审计的 P1 定性建立在"默认 CI 不 guard INV-LIMIT"这一前提上。该前提**与 live repo 不符**：I5 已通过 `.github/workflows/maven.yml` 的 `invariant-gate` job（`:45-73`，每个 push/PR 触发）调用 `ai-dev/tools/run-nop-metadata-invariants.sh`，其 step 4 显式以 `-Dtest=TestLimitNegativeValueInvariant` fail-fast 运行该测试（非零即 CI 红）。因此 F3 审计 recommendation 的可接受档 **"OR add an explicit CI workflow step that runs it fail-fast on every PR" 已由 I5 满足**——任何重新引入 `limit<0?0:limit` / `Math.abs(limit)` 的 PR **不会**合并即绿（会被 `invariant-gate` job 拦截）。

F3 因此被重新裁定：**P1 的"门禁失效在默认 CI"前提不成立**；真实残留 = 一项防御纵深/卫生清理（INV-LIMIT 在默认 surefire 仍被排除，本地 `./mvnw test` 不覆盖；排除注释已陈旧——底层 limit 缺陷已于 I4/plan-05 Phase 0 修复，该测试现已全绿）。鉴于 F3 在审计中标为 P1（非可降级），本计划将其作为 `Fix` 项收口（而非静默降级为 follow-up），但范围严格限定为该残留清理。

## Current Baseline

> 行级读证见 Source 审计 F3 evidence 段；CI 真相由本 plan 独立审查 fresh session 核实，lead agent 已复核。

- **CI 现状（已 guard）**：`.github/workflows/maven.yml` 含 `invariant-gate` job（needs: build；on push+pull_request），运行 `MVN=mvn ./ai-dev/tools/run-nop-metadata-invariants.sh`；该脚本 step 4 运行 `TestLimitNegativeValueInvariant`（fail-fast）。`build` job 跑 `mvn -B package`（默认 surefire，含该排除）。**结论**：CI 已在每个 PR 上强制 INV-LIMIT。
- **F3 残留**：`nop-metadata/nop-metadata-service/pom.xml`（surefire `<excludes>` 段，排除 `**/invariant/TestLimitNegativeValueInvariant.java`，约 :145-147）仍在；其注释称排除意图为"保证默认构建恒绿，由单独命令调用产出 limit red list"。该前提**已失效**：底层 limit 静默 clamp 缺陷已于 I4/plan-05 Phase 0 收敛，该测试现全绿（closure evidence "limit 4 tests / 0 fail"）。
- **本地 dev 路径**：本地 `./mvnw test -pl nop-metadata -am` 因排除不运行 INV-LIMIT（仅 `invariant-gate` CI job 与显式 `run-nop-metadata-invariants.sh` 运行）。
- **mission 收口记录措辞**：roadmap 与 plan-05 closure 均写"4 门禁零命中"，由聚合入口实测背书，**从未带"opt-in"限定语**（F3 的"at minimum qualify … opt-in"是"若不修"兜底；I5 已走 route B，故限定语从未被添加）。
- **构建/测试命令**（mission 配置）：`./mvnw test -pl nop-metadata -am -T 1C`。

## Goals

- 移除陈旧的 surefire 排除，使 `TestLimitNegativeValueInvariant` 回归**默认 surefire**（与 `invariant-gate` CI job 形成 defense-in-depth：本地 dev + CI 均覆盖），并验证默认 `./mvnw test -pl nop-metadata -am` 全绿（底层缺陷已修，预期无红）。
- 消除"排除以保证默认恒绿"这一已失效前提带来的误导配置。
- 在 owner-doc（`docs-for-ai/02-core-guides/invariant-guards.md`，I5 新建）登记 INV-LIMIT 的双重运行方式（默认 surefire + CI invariant-gate）。

## Non-Goals

- 不改变 INV-LIMIT 不变式的语义或覆盖方法集（Cycle 2 / I1 范畴）。
- 不新增第 5 条不变式门禁。
- 不重做 I5 已完成的 `.mjs` 门禁 CI 接入。
- 不把 F3 升格回"门禁失效在默认 CI"的 P1 叙事——该前提经核实不成立。

## Scope

### In Scope

- 移除 `pom.xml` surefire 排除；验证默认 `./mvnw test` 全绿。
- 可选：裁定是否简化 `run-nop-metadata-invariants.sh` step 4（避免 INV-LIMIT 在 `build` job surefire 与 `invariant-gate` job 各跑一次的冗余）——保留冗余作 defense-in-depth 亦可，须记录裁定。
- owner-doc 登记 INV-LIMIT 双重运行方式。

### Out Of Scope

- INV-LIMIT 不变式语义/方法集扩展。
- 新门禁实现。
- 同批其余 P0/P1（F1/F2/AR-04 → plan `-1`；AR-01/02/03/F4 → plan `-2`）。

## Execution Plan

### Phase 1 — INV-LIMIT 回归默认 surefire（防御纵深 + 清理陈旧配置）

Status: planned
Targets: `nop-metadata/nop-metadata-service/pom.xml`、`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/TestLimitNegativeValueInvariant.java`（更新其 Javadoc 中"excluded from default surefire"陈旧表述）、`ai-dev/tools/run-nop-metadata-invariants.sh`（更新 step 4 注释中"excluded from default surefire"表述）、`docs-for-ai/02-core-guides/invariant-guards.md`

- Item Types: `Fix | Decision`

- [ ] `Fix`：移除 `pom.xml` surefire `<excludes>` 中对 `TestLimitNegativeValueInvariant` 的排除（注释一并更新/删除，消除"保证默认恒绿"的陈旧表述）
- [ ] `Proof`：验证默认 `./mvnw test -pl nop-metadata -am -T 1C` 下该测试**运行且全绿**（底层缺陷已修，预期无红；若意外有红，修复之而非重新隐藏）
- [ ] `Decision`：裁定 `run-nop-metadata-invariants.sh` step 4 是否保留（保留 = CI 双重运行 defense-in-depth；简化 = 删 step 4 以避免与 build job surefire 重复）。记录裁定理由（推荐保留作 defense-in-depth）

Exit Criteria:

- [ ] `TestLimitNegativeValueInvariant` 在默认 `./mvnw test -pl nop-metadata -am` 上运行且全绿（命令输出含该测试运行记录）
- [ ] 任一重新引入 `limit<0?0:limit` / `Math.abs(limit)` 的改动在**本地默认 `./mvnw test`** 即变红（由代码审查或临时注入验证；此前仅在 `invariant-gate` CI job 暴露，现本地亦暴露）
- [ ] **无静默跳过**：排除注释中"保证默认恒绿"的陈旧前提已消除；INV-LIMIT enforcement 不再以 surefire 排除方式 opt-out
- [ ] **端到端验证**：默认 `./mvnw test` 命令输出包含该测试运行记录
- [ ] owner-doc（`docs-for-ai/02-core-guides/invariant-guards.md`）登记 INV-LIMIT 双重运行方式（默认 surefire + CI invariant-gate）
- [ ] `ai-dev/logs/2026/08-14.md` 已追加（含 F3 P1 前提订正的记录）

## Closure Gates

- [ ] F3 残留（surefire 排除）已清理；INV-LIMIT 回归默认 surefire 并全绿
- [ ] F3 审计的 P1 前提（"默认 CI 不 guard"）已在 plan/log 中订正为"已由 I5 invariant-gate job guard；本 plan 收口本地 surefire 防御纵深残留"
- [ ] 受影响 owner-doc 已同步
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证该测试在默认 `./mvnw test` 确实运行（非仅配置改动）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（本 plan 修改 `docs-for-ai/` 下文件，AGENTS.md 强制要求）
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [ ] checkstyle / 代码规范检查通过
- [ ] 4 条不变式门禁仍零命中（INV-LIMIT 现在默认 surefire + CI 双重验证）

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- （无；F3 的 P2 化安全/硬化相关项已在 mission roadmap Follow-up Backlog）

## Closure

Status Note: （收口时填写）
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: 
- Evidence: （收口时填写：含默认 `./mvnw test` 输出含该测试运行的证据；`check-plan-checklist.mjs --strict` 退出码 0）

Follow-up:

- （仅 non-blocking）
