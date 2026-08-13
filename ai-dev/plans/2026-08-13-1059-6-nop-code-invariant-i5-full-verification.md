# nop-code 不变式闭环 I5 — 全量验证（Cycle 1）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I5. 全量验证
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I5）；I4 修复产出；门禁 baseline `ai-dev/audits/nop-code-invariants/baselines/`
> Related: 前驱 `2026-08-13-1059-5-nop-code-invariant-i4-fix-execution.md`（I4，提供修复产出）；后继 `2026-08-13-1059-7-nop-code-invariant-i6-cycle-closure.md`（I6，消费本计划验证结论做稳态判定）

## Purpose

在 I4 修复全部 P0/P1 缺陷后，执行一次**全量独立验证**：模块级全绿 + 四族门禁 real-violation 零命中（棘轮前进）+ 幂等 red-list 锁迁移完成 + Anti-Hollow 扫描零 high/critical + full-green 记录。本计划是 I6 稳态判定的确定性输入——只有 I5 全绿才能判定 Cycle 1 是否达稳态。

## Current Baseline

> I4 完成后的预期状态（本计划以 I4 已 closing 为前提）。

- **I4 修复产出（预期）**：73 条 P0/P1 I4 修复项已落地；门禁 baseline 已棘轮前进（query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0）。
- **门禁工具已存在**：`check-nop-code-invariants.mjs`（三 family + 聚合入口 + 棘轮 baseline）；`TestNopCodeIndexIdempotencyInvariant`（幂等表 + red-list 锁 + 表完备性门禁）。
- **Anti-Hollow 工具已存在**：`scan-hollow-implementations.mjs`。
- **Checklist 工具已存在**：`check-plan-checklist.mjs`。
- **真正剩余 gap**：I4 修复后的全量独立验证尚未执行（I4 的 closure audit 是 plan 级自查，I5 是 cycle 级全量独立复核）。

## Goals

- `./mvnw test -pl nop-code -am -T 1C` 全绿（模块级 + 依赖模块）。
- 四族门禁 real-violation 零命中：query-limit 真违规 0（剩余 ≤14 全部已接受有界）/ entity-field-min 真违规 0（剩余 ≤12 全部已接受全实体）/ delete-contract 0 / idempotency red-list 锁 0。
- 棘轮 baseline diff 审阅完成（无静默弱化）。
- Anti-Hollow 扫描零 high/critical。
- full-green 记录写入 `ai-dev/audits/nop-code-invariants/i5-full-green-record.md`。

## Non-Goals

- 不修复任何缺陷（I4 范围）—— I5 发现的 regression 退回 I4。
- 不做稳态判定 / 复触发条件登记（I6 范围）。
- 不沉淀新门禁（Cycle 2 / I1）。
- 不跑全仓 `./mvnw clean install`（I5 聚焦 nop-code 模块组 + 依赖）。

## Scope

### In Scope

- 模块级测试全量运行（`./mvnw test -pl nop-code -am -T 1C`）。
- 四族门禁 strict 模式 + 棘轮 baseline 模式复核。
- 幂等表迁移复核（KNOWN_NON_IDEMPOTENT 为空）。
- Anti-Hollow 扫描。
- baseline JSON diff 审阅。
- full-green 记录产出。

### Out Of Scope

- 缺陷修复（I4）。
- 稳态判定与 Cycle 2 触发（I6）。
- 新门禁实现（Cycle 2 / I1）。

## Execution Plan

### Phase 1 - 模块级全量测试

Status: planned
Targets: `nop-code` 模块组（`nop-code-api` / `nop-code-dao` / `nop-code-service` / `nop-code-web`）

- Item Types: `Proof`

- [ ] 执行 `./mvnw test -pl nop-code -am -T 1C`，记录完整输出
- [ ] 若有失败：定位失败用例，判定是 I4 regression（退回 I4 修复）还是既有 flaky（记录并重跑）
- [ ] 全绿后记录 full-green 命令 + 退出码 + 时间戳

Exit Criteria:

- [ ] `./mvnw test -pl nop-code -am -T 1C` 退出码 0
- [ ] 失败用例（若有）已分类（I4 regression / flaky / 既有），regression 退回 I4
- [ ] **端到端验证**：I4 各 Phase 的 focused test 全部在本次全量运行中通过（非孤立单跑）
- [ ] `No owner-doc update required`（I5 为纯验证，不改产品代码）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 四族门禁 real-violation 零命中复核

Status: planned
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`；baseline `baselines/baseline-*.json`；`TestNopCodeIndexIdempotencyInvariant`

- Item Types: `Proof`

- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list`（注意：`--list` 退出码非 0，仅用于人工巡检展示）：剩余命中**签名集合**须**精确等于** red list §1 的 14 条「已接受有界」签名集（按 `file:line[method(var)]` 对齐），real-violation 0；任何消失的签名须附「已修复」证据，任何签名漂移（改名/移行）须附说明
- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min --list`：剩余命中签名集合须精确等于 red list §2 的 12 条「已接受全实体」签名集，real-violation 0
- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract --list`：命中 0
- [ ] `./mvnw test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexIdempotencyInvariant`：全绿，`KNOWN_NON_IDEMPOTENT` 为空（两方法已迁移 IDEMPOTENCE_TABLE）
- [ ] **棘轮 baseline 模式复核（per-family + per-baseline，3 条显式命令）**：`--family query-limit --baseline baselines/baseline-query-limit.json`、`--family entity-field-min --baseline baselines/baseline-entity-field-min.json`、`--family delete-contract --baseline baselines/baseline-delete-contract.json` 各自退出码 0（无 `[NEW]` 违规）。注意：`--baseline` 接单个文件，不可用 glob；aggregate 模式（无 `--family`）不可用单 baseline（会跨 family 误匹配）

Exit Criteria:

- [ ] query-limit real-violation = 0（剩余命中签名集合 == red list §1 的 14 条「已接受有界」，逐条对齐无 delta 无漂移）
- [ ] entity-field-min real-violation = 0（剩余命中签名集合 == red list §2 的 12 条「已接受全实体」）
- [ ] delete-contract = 0
- [ ] idempotency `KNOWN_NON_IDEMPOTENT` = ∅（red-list 锁迁移完成）
- [ ] 棘轮 baseline per-family 复核：3 条命令各自退出码 0（无 `[NEW]` 违规）
- [ ] **接线验证**：门禁扫描器确实扫描了 I4 修改过的文件（抽查 ≥3 个修复点的文件在 `--list` 输出中状态正确）
- [ ] `No owner-doc update required`（I5 为纯验证，不改产品代码）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - baseline diff 审阅 + Anti-Hollow 扫描

Status: planned
Targets: `baselines/baseline-*.json`（I4 `--update-baseline` 后的 diff）；`scan-hollow-implementations.mjs`

- Item Types: `Proof | Decision`

- [ ] 审阅 I4 `--update-baseline` 产生的 baseline diff：仅移除已修复 real-violation 条目，无静默弱化（无删除「已接受有界/全实体」条目伪装成修复）
- [ ] **审阅 I4 全量 git diff（防扫描器弱化）**：特别检查 `ai-dev/tools/check-nop-code-invariants.mjs` 的白名单/正则是否被放宽、baseline JSON 是否被静默删条目——计数下降须来自真实修复（投影/分页/setLimit/exists），不可来自扫描器放宽或结构性躲扫（改名/移行）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high`：退出码 0（扫描范围限定 nop-code-service——I4 改动面；nop-code-core 的 5 条 `UnsupportedOperationException` 是 Rule #24 合规实现，非 I4 范围，预先裁定 accepted 不阻塞本 gate）
- [ ] 抽查 I4 新增代码路径：从入口点到出口点追踪调用链连通性（Anti-Hollow），无空方法体/静默跳过/no-op 作为正常实现

Exit Criteria:

- [ ] baseline diff 审阅记录：移除的条目全部对应已修复 real-violation，无静默弱化证据
- [ ] I4 git diff 审阅：扫描器源码（白名单/正则）未被放宽，计数下降来自真实修复
- [ ] `scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0（限定 nop-code-service，不含 nop-code-core 合规 hollow）
- [ ] Anti-Hollow 抽查：≥3 条 I4 修复路径的调用链连通性已人工追踪确认（从入口到出口）
- [ ] **端到端验证**：增量索引路径（addDoc/removeDocs 对称）、删除路径（跨文件孤儿清理）、幂等路径（重试安全）各有一条端到端测试在 Phase 1 全量运行中通过
- [ ] `No owner-doc update required`（I5 为纯验证）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - full-green 记录产出

Status: planned
Targets: `ai-dev/audits/nop-code-invariants/i5-full-green-record.md`（新建）

- Item Types: `Proof`

- [ ] 产出 `i5-full-green-record.md`：记录全量测试命令 + 退出码 + 四族门禁命中数（修复前→修复后对比）+ 幂等表状态 + Anti-Hollow 扫描结果 + baseline diff 审阅结论
- [ ] 记录 I4 修复统计：73 条 P0/P1 修复项落地数、38 条 red-list 真违规归零数、棘轮前进量

Exit Criteria:

- [ ] `i5-full-green-record.md` 存在，含 full-green 证据（命令 + 退出码 + 时间戳）
- [ ] 修复前→修复后门禁命中对比表完整（query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0 / delete-contract 0→0）
- [ ] 本 Phase 为纯文档记录：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **前置条件**：I5 以 I4 fully closed 为前提。若 I4 的 ORM plan-first（AR-149/150）或 ask-first（WP-10）项被人工阻塞，I5 须显式声明这些项的 deferred 状态（有降级路径的，见 I4 Deferred But Adjudicated），不可将 I4 的 in-scope deferred 当作 I5 验证通过。

- [ ] `./mvnw test -pl nop-code -am -T 1C` 退出码 0
- [ ] query-limit real-violation = 0 / entity-field-min real-violation = 0 / delete-contract = 0
- [ ] idempotency `KNOWN_NON_IDEMPOTENT` = ∅
- [ ] 棘轮 baseline per-family 复核：3 条命令各自退出码 0（无 `[NEW]` 违规）
- [ ] baseline diff 审阅无静默弱化 + I4 git diff 审阅无扫描器弱化（白名单/正则未放宽）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0
- [ ] Anti-Hollow 抽查 ≥3 条修复路径调用链连通
- [ ] `i5-full-green-record.md` 产出
- [ ] 不存在被静默降级的 in-scope 验证项
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（本计划为纯验证，预计无 deferred 项；若发现 I4 regression，退回 I4 修复而非在本计划 deferred）

## Non-Blocking Follow-ups

- 稳态判定与 Cycle 2 触发条件 —— I6 范围

## Closure

Status Note: （待执行完成后填写）
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit 填写）
- Audit Session: 
- Evidence: （待填写）
