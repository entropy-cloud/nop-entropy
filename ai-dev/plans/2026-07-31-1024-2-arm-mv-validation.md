# 2 MV — 全量验证与独立 Closure Audit

> Plan Status: active
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §MV，MR1-MR4 plans，`ai-dev/audits/arm-index.md`
> Mission: audit-remediation
> Work Item: MV

## Purpose

对 nop-ai 模块组执行全量 build + test，由独立子 agent 对整条审计-修复链路（MA1-MA6 审计、MR1-MR4 修复）做 closure audit，并验证所有 P0/P1 finding 可追溯至修复证据或明确裁定，从而把 roadmap 的 MV 里程碑收口。

## Current Baseline

- MA1-MA6 审计报告全部 done；MR1/MR2/MR3 修复 plan 全部 completed；MR4 计划承接跨维度裁决与索引一致性（本 plan 依赖 MR4 完成）。
- 模块组范围：nop-ai 下 18 个子模块（排除 `nop-ai-mcp-server`、`nop-spring-mcp-server`、`nop-spring-mcp-server-support`），~1275 main Java，~426 test。
- `arm-index.md` 是 P0/P1 finding 的唯一权威索引；MR4 完成后应无状态漂移。
- 绿色基线命令（roadmap M0.3）：`./mvnw clean install -DskipTests -pl nop-ai -am -T 1C` + `./mvnw test -pl nop-ai -am -T 1C`。

## Goals

- V.1：nop-ai 模块组全量 build + test 通过，产出可复现的绿色基线记录。
- V.3：所有 P0/P1 finding（含 P0-MA2-01、P0-MA6-01 与 P1 表全部行）逐条可追溯至修复提交/测试文件或明确裁决记录，产出追溯矩阵。
- V.2：独立子 agent（fresh session）对审计-修复链路做 closure audit，发现的问题全部关闭或显式裁决。
- 更新 roadmap：MR4、MV 标记 done。

## Non-Goals

- 不修复新发现的问题之外的代码缺陷（新发现缺陷就地记录并裁决：属于 scope 的修复，不属于的移交）。
- 不做 MG（lessons/skills/docs 沉淀）— 由下一个 plan 承接。
- 不对被排除的 MCP 模块做验证收口。
- 不重写历史审计报告。

## Scope

### In Scope

- 全量 build + test（V.1）
- P0/P1 finding 可追溯性矩阵（V.3）
- 独立子 agent closure audit（V.2）
- roadmap 状态更新与收口

### Out Of Scope

- MG 沉淀工作
- MCP 模块验证
- P2/P3 修复

## Execution Plan

### Phase 1 — V.1 全量 build + test

Status: planned
Targets: `nop-ai/` 全部子模块（排除 MCP）

- Item Types: `Proof`

- [ ] 前置检查：确认 MR4 plan（`ai-dev/plans/2026-07-31-1024-1-arm-mr4-adjudication.md`）已 `completed` 且 roadmap R4.1 = done；未满足则阻塞本 plan，不开始 build/test
- [ ] 运行 `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`，记录结果
- [ ] 运行 `./mvnw test -pl nop-ai -am -T 1C`，记录结果
- [ ] 任一失败项：定位根因，区分「修复引入的回归」与「预先存在的 flaky/环境问题」，修复回归或记录裁定
- [ ] 将 build/test 结果（命令、时长、失败项处理、最终状态）写入 `ai-dev/logs/`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 全量 build（skipTests install）退出码 0
- [ ] 全量 test 退出码 0
- [ ] 失败项全部有处置结论（已修复并有证据，或已记录裁定理由）
- [ ] No owner-doc update required（全量 build+test 不改变 live baseline 与公开契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — V.3 P0/P1 finding 可追溯性矩阵

Status: planned
Targets: `ai-dev/audits/arm-index.md`、各审计报告、MR1-MR4 plans

- Item Types: `Proof | Fix`

- [ ] 枚举 arm-index 中全部 P0（2 条）与 P1 finding（按 MR4 校正后的表）
- [ ] 对每条 finding 定位修复证据：提交哈希、测试文件、或裁定记录（含 MR4 裁决）；无证据的标记为 open
- [ ] 在 arm-index 中新增/更新「可追溯性」列或小节，生成追溯矩阵
- [ ] 对 open 条目做出处置：修复（附测试）或显式裁定移出 scope（需符合 Anti-Slacking 规则）

Exit Criteria:

- [ ] 每条 P0/P1 finding 均有修复证据或明确裁定，open 条目数为 0
- [ ] 追溯矩阵已写入 `arm-index.md`
- [ ] 处置过程中产生的代码修复（如有）附带测试且通过
- [ ] `./mvnw test -pl nop-ai -am -T 1C` 通过
- [ ] No owner-doc update required（追溯矩阵属 ai-dev 范畴）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — V.2 独立子 agent closure audit

Status: planned
Targets: `ai-dev/backlog/audit-remediation-roadmap.md`、`ai-dev/audits/arm-index.md`、live repo

- Item Types: `Proof | Fix`

- [ ] 启动独立子 agent（fresh session，不复用本 plan 执行会话），按 `closure-audit-prompt.md` 对整条链路做 closure audit
- [ ] 审计范围：MA1-MA6 审计产出、MR1-MR4 修复落地、MV 自身
- [ ] 子 agent 发现的问题逐条关闭或记录裁定
- [ ] 将 audit 结果与证据写入本 plan 的 Closure 段和 `ai-dev/logs/`

Exit Criteria:

- [ ] 独立子 agent 完成 closure audit 且无 Blocker 级未决项
- [ ] 发现的问题全部有处置结论
- [ ] audit 证据（task id、PASS/FAIL 明细）已写入本 plan Closure 段
- [ ] No owner-doc update required（closure audit 属 ai-dev 范畴）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — roadmap 收口与关闭

Status: planned
Targets: `ai-dev/backlog/audit-remediation-roadmap.md`

- Item Types: `Fix | Follow-up`

- [ ] 更新 roadmap：R4.1 应为 done（Phase 1 前置检查已确认）、V.1/V.2/V.3 标记 done
- [ ] 更新 `arm-index.md` 状态汇总
- [ ] 独立 closure audit 完成（承接 Phase 3 证据），本 plan 关闭

Exit Criteria:

- [ ] roadmap 中 MR4、MV 全部工作项标记 done
- [ ] `arm-index.md` 状态汇总与 roadmap 一致
- [ ] 本 plan 的 Closure 段含完整独立 audit 证据
- [ ] No owner-doc update required（roadmap/arm-index 属 ai-dev 范畴）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全量 build + test 通过且有记录
- [ ] 所有 P0/P1 finding 可追溯至修复证据或明确裁定
- [ ] 独立子 agent closure audit 完成且证据写入本 plan
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步，或明确写明 No owner-doc update required
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）修复链路上组件间调用在运行时连通，（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1024-2-arm-mv-validation.md --strict` 退出码 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0（无 high/critical 空壳发现）
- [ ] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [ ] `./mvnw test -pl nop-ai -am -T 1C`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 排除模块（MCP 三个模块）的验证

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 明确排除 MCP 协议集成模块（独立发布周期，需单独审计）；本计划不覆盖不构成 closure 阻塞。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MG（lessons + skills + docs 沉淀）由下一个 plan 承接

## Closure

Status Note: 待关闭时填写。
Completed: 待关闭时填写。

Closure Audit Evidence:

- Reviewer / Agent: 待关闭时由独立子 agent 填写
- Evidence: 待关闭时填写

Follow-up:

- 待关闭时填写

## Optional Sections

## Risks And Rollback

- 全量 test 若发现 flaky 测试（非修复引入）：记录、重跑确认，不静默跳过；确认的 flaky 缺陷按 live defect 处理并修复。
- 若 closure audit 发现 only-partial landing，本 plan 保持 `in progress` 并补充工作项，不勉强关闭。
