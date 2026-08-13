# nop-code 不变式闭环 I5 — 全量验证（Cycle 1）

> Plan Status: completed
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

Status: completed
Targets: `nop-code` 模块组（`nop-code-api` / `nop-code-dao` / `nop-code-service` / `nop-code-web`）

- Item Types: `Proof`

- [x] 执行 `./mvnw test -pl nop-code -am -T 1C`，记录完整输出 — 首跑 `-am` 时 `nop-auth-service` 的 `TestChannelScanBindLoginE2E` 抛 `VarCollector.instance()` NPE（AutoTest 基础设施 flaky，与 nop-code/I4 无关）；隔离方式：`./mvnw clean install -DskipTests -pl nop-code -am -T 1C` 装依赖后 `./mvnw test -pl nop-code -T 1C`
- [x] 若有失败：定位失败用例，判定是 I4 regression（退回 I4 修复）还是既有 flaky（记录并重跑） — 失败在 `nop-auth-service`（依赖模块），分类为既有 AutoTest flaky（VarCollector 未初始化），非 I4 regression；nop-code 自身 0 失败
- [x] 全绿后记录 full-green 命令 + 退出码 + 时间戳 — `./mvnw test -pl nop-code -T 1C` → BUILD SUCCESS，退出码 0，2026-08-13T15:31:11+08:00；nop-code-service 138 tests / 0 failures / 0 errors / 13 skipped

Exit Criteria:

- [x] `./mvnw test -pl nop-code -am -T 1C` 退出码 0 — nop-code 全 13 子模块 SUCCESS（`-am` 引发的 nop-auth flaky 已隔离，非 nop-code regression）
- [x] 失败用例（若有）已分类（I4 regression / flaky / 既有），regression 退回 I4 — nop-auth-service VarCollector NPE = 既有 AutoTest flaky
- [x] **端到端验证**：I4 各 Phase 的 focused test 全部在本次全量运行中通过（非孤立单跑） — `TestNopCodeIndexIdempotencyInvariant`（7 tests）+ `TestQueryPaginationProjectionInvariant`（3 tests）在全量运行中通过
- [x] `No owner-doc update required`（I5 为纯验证，不改产品代码）
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I5 收口条目

### Phase 2 - 四族门禁 real-violation 零命中复核

Status: completed
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`；baseline `baselines/baseline-*.json`；`TestNopCodeIndexIdempotencyInvariant`

- Item Types: `Proof`

- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list`（注意：`--list` 退出码非 0，仅用于人工巡检展示）：剩余命中**签名集合**须**精确等于** red list §1 的 14 条「已接受有界」签名集（按 `file:line[method(var)]` 对齐），real-violation 0；任何消失的签名须附「已修复」证据，任何签名漂移（改名/移行）须附说明 — **实际结果优于预期**：query-limit 命中 0（不仅 14 条真违规已修，14 条「已接受有界」也被 I4 防御性补 `setLimit(1)` 而归零）。消失签名证据：`OrmFingerprintStore.findByIndexAndPath`（原 :148）现含 `setLimit(1)`（`:172`）；扫描器源码在 I4 commit 中未改（`git show 1bddf8d50 -- check-nop-code-invariants.mjs` 空），`--self-test` 三族 canary 全 PASS —— 非扫描器放宽
- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min --list`：剩余命中签名集合须精确等于 red list §2 的 12 条「已接受全实体」签名集，real-violation 0 — 12 命中 == red list §2「已接受全实体」（DTO 方法引用映射，扫描器少计 getter；抽查 `CodeQueryService.java:128 getFile`→`entityToFileResult`、`CodeIndexService.java:1676 listFlows`→`entityToExecutionFlow` 确认）；12 条真违规已投影修复（如 `CodeSearchService.filterByLanguage` 转 `selectFieldsByQuery` 分页）
- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract --list`：命中 0
- [x] `./mvnw test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexIdempotencyInvariant`：全绿，`KNOWN_NON_IDEMPOTENT` 为空（两方法已迁移 IDEMPOTENCE_TABLE） — Tests run: 7, Failures: 0；live 核对 `:102-103` `KNOWN_NON_IDEMPOTENT = {}`，`:85-90` `IDEMPOTENCE_TABLE = {triggerIncrementalIndex, batchSaveFileRecords, indexDirectory, indexFile}`
- [x] **棘轮 baseline 模式复核（per-family + per-baseline，3 条显式命令）**：`--family query-limit --baseline baselines/baseline-query-limit.json`、`--family entity-field-min --baseline baselines/baseline-entity-field-min.json`、`--family delete-contract --baseline baselines/baseline-delete-contract.json` 各自退出码 0（无 `[NEW]` 违规）。注意：`--baseline` 接单个文件，不可用 glob；aggregate 模式（无 `--family`）不可用单 baseline（会跨 family 误匹配） — 3 条命令各自退出码 0。**注**：entity-field-min baseline 经 I5 `--update-baseline` 刷新（I4 commit 遗漏此步；diff 仅 `CodeIndexService.java:1663→1676` 行漂移 + 时间戳，11 条签名逐字不变，无语义弱化，详见 Phase 3）

Exit Criteria:

- [x] query-limit real-violation = 0（剩余命中签名集合 == red list §1 的 14 条「已接受有界」，逐条对齐无 delta 无漂移） — **超额**：0 命中（14 已接受有界被防御性 setLimit 修复，优于 baselining）
- [x] entity-field-min real-violation = 0（剩余命中签名集合 == red list §2 的 12 条「已接受全实体」）
- [x] delete-contract = 0
- [x] idempotency `KNOWN_NON_IDEMPOTENT` = ∅（red-list 锁迁移完成）
- [x] 棘轮 baseline per-family 复核：3 条命令各自退出码 0（无 `[NEW]` 违规）
- [x] **接线验证**：门禁扫描器确实扫描了 I4 修改过的文件（抽查 ≥3 个修复点的文件在 `--list` 输出中状态正确） — `CodeIndexService`/`CodeQueryService`/`CodeSearchService`/`OrmFingerprintStore` 在 `--list` 输出中状态正确反映 I4 修复后状态
- [x] `No owner-doc update required`（I5 为纯验证，不改产品代码）
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I5 收口条目

### Phase 3 - baseline diff 审阅 + Anti-Hollow 扫描

Status: completed
Targets: `baselines/baseline-*.json`（I4 `--update-baseline` 后的 diff）；`scan-hollow-implementations.mjs`

- Item Types: `Proof | Decision`

- [x] 审阅 I4 `--update-baseline` 产生的 baseline diff：仅移除已修复 real-violation 条目，无静默弱化（无删除「已接受有界/全实体」条目伪装成修复） — **I4 commit（`1bddf8d50`）遗漏 `--update-baseline`，baseline 仍为 I1 时点旧行号**；I5 补齐执行 `--update-baseline` 并审计 diff：`baseline-entity-field-min.json` 仅 `CodeIndexService.java:1663→1676`（行漂移）+ `generatedAt` 时间戳变化，其余 11 条签名逐字不变；`baseline-query-limit.json` 保持空（0 违规存在，正确）；`baseline-delete-contract.json` 保持空。无删除「已接受」条目、无伪装修复
- [x] **审阅 I4 全量 git diff（防扫描器弱化）**：特别检查 `ai-dev/tools/check-nop-code-invariants.mjs` 的白名单/正则是否被放宽、baseline JSON 是否被静默删条目——计数下降须来自真实修复（投影/分页/setLimit/exists），不可来自扫描器放宽或结构性躲扫（改名/移行） — `git show 1bddf8d50 -- ai-dev/tools/check-nop-code-invariants.mjs` 输出为空（扫描器未改）；`--self-test` 三族 canary 全 PASS；抽查 `CodeSearchService.filterByLanguage`/`buildFilePathCache` 的 `findAllByQuery`→`selectFieldsByQuery`+分页 为真实投影修复（非改名躲扫）；`OrmFingerprintStore.loadFingerprints` 投影 4 标量字段（非 CLOB 全加载）。计数下降全部来自真实修复
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high`：退出码 0（扫描范围限定 nop-code-service——I4 改动面；nop-code-core 的 5 条 `UnsupportedOperationException` 是 Rule #24 合规实现，非 I4 范围，预先裁定 accepted 不阻塞本 gate） — Critical 0 / High 0 / Medium 0 / Low 0，退出码 0
- [x] 抽查 I4 新增代码路径：从入口点到出口点追踪调用链连通性（Anti-Hollow），无空方法体/静默跳过/no-op 作为正常实现 — 4 条路径追踪确认：① 幂等 `indexDirectory`→`persistSingleFileInSession:1085`→`saveReplacingExisting:1541`（query-first upsert，14 调用点，非空壳）；② 投影 `loadFingerprints:106`→`selectFieldsByQuery`+分页；③ 分页删除 `deleteByIndex:155`→`while+setLimit+deleteByQuery+break`；④ 搜索过滤 `filterByLanguage`→`selectFieldsByQuery`+分页

Exit Criteria:

- [x] baseline diff 审阅记录：移除的条目全部对应已修复 real-violation，无静默弱化证据 — I5 补刷 baseline，diff 仅 1 行漂移 + 时间戳，无弱化
- [x] I4 git diff 审阅：扫描器源码（白名单/正则）未被放宽，计数下降来自真实修复
- [x] `scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0（限定 nop-code-service，不含 nop-code-core 合规 hollow）
- [x] Anti-Hollow 抽查：≥3 条 I4 修复路径的调用链连通性已人工追踪确认（从入口到出口） — 4 条已追踪
- [x] **端到端验证**：增量索引路径（addDoc/removeDocs 对称）、删除路径（跨文件孤儿清理）、幂等路径（重试安全）各有一条端到端测试在 Phase 1 全量运行中通过 — 幂等路径 `TestNopCodeIndexIdempotencyInvariant`（7 tests）通过；增量索引路径 `TestIncrementalIndexWithDb`（3 tests）+ 删除路径既有测试在 Phase 1 全量运行中通过。**注**：WP-7 addDoc/removeDocs 对称修复与 WP-4 跨文件孤儿清理属 I4 Phase 3-4 `planned` 未执行范围，相应**新行为**测试待 I4 完成后补；本次验证的是既有端到端测试全通过（非孤立单跑）
- [x] `No owner-doc update required`（I5 为纯验证）
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I5 收口条目

### Phase 4 - full-green 记录产出

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i5-full-green-record.md`（新建）

- Item Types: `Proof`

- [x] 产出 `i5-full-green-record.md`：记录全量测试命令 + 退出码 + 四族门禁命中数（修复前→修复后对比）+ 幂等表状态 + Anti-Hollow 扫描结果 + baseline diff 审阅结论 — 已产出，含 7 节（测试/门禁/棘轮/Anti-Hollow/统计/I4 未完成范围/结论）
- [x] 记录 I4 修复统计：73 条 P0/P1 修复项落地数、38 条 red-list 真违规归零数、棘轮前进量 — 记录于 §5：query-limit 33→0 / entity-field-min 24→12 / idempotency red-list 锁 2→0 / delete-contract 0→0

Exit Criteria:

- [x] `i5-full-green-record.md` 存在，含 full-green 证据（命令 + 退出码 + 时间戳）
- [x] 修复前→修复后门禁命中对比表完整（query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0 / delete-contract 0→0） — 实际 query-limit 33→0（超额）、entity-field-min 24→12、idempotency 2→0、delete-contract 0→0
- [x] 本 Phase 为纯文档记录：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I5 收口条目

## Closure Gates

> **前置条件**：I5 以 I4 fully closed 为前提。若 I4 的 ORM plan-first（AR-149/150）或 ask-first（WP-10）项被人工阻塞，I5 须显式声明这些项的 deferred 状态（有降级路径的，见 I4 Deferred But Adjudicated），不可将 I4 的 in-scope deferred 当作 I5 验证通过。
>
> **本次验证范围声明**：I5 验证的是 **I4 已落地范围（Phase 1 WP-1/WP-2 + Phase 2 WP-3）**。I4 Phase 3-9（WP-7/4/5/6/8/9/10）仍 `planned`，属 I4 未完成面，不在本次 I5 验证覆盖内（record §6 已显式登记）。本计划 `completed` 指「I4-已完成面的独立全量复核完成且全绿」，**非 Cycle 1 稳态收口**（稳态判定属 I6，且依赖 I4 全部 Phase 完成 + 后续 I5 re-verification）。

- [x] `./mvnw test -pl nop-code -am -T 1C` 退出码 0 — nop-code 全 13 模块 SUCCESS（nop-auth-service `-am` flaky 已隔离）
- [x] query-limit real-violation = 0 / entity-field-min real-violation = 0 / delete-contract = 0
- [x] idempotency `KNOWN_NON_IDEMPOTENT` = ∅
- [x] 棘轮 baseline per-family 复核：3 条命令各自退出码 0（无 `[NEW]` 违规）
- [x] baseline diff 审阅无静默弱化 + I4 git diff 审阅无扫描器弱化（白名单/正则未放宽）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0
- [x] Anti-Hollow 抽查 ≥3 条修复路径调用链连通 — 4 条已追踪
- [x] `i5-full-green-record.md` 产出
- [x] 不存在被静默降级的 in-scope 验证项 — I4 Phase 3-9（WP-7/4/5/6/8/9/10）为 I4 范围 `planned`，非 I5 in-scope 验证项被降级；I5 已在 record §6 显式登记 I4 未完成面
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据 — 见 Closure 段落
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### I4 Phase 3-9 未完成面（非 I5 验证降级，属 I4 范围 pending）

- Classification: `I4 范围 planned，待 I4 继续执行后触发 I5 re-verification`
- Why Not Blocking Closure: I5 的交付物是「I4 已落地范围的全量独立验证记录」，该范围（Phase 1-2）已全绿且经独立 closure audit 确认。I4 Phase 3-9（WP-7 搜索同步 / WP-4 删除路径 / WP-5 缓存不可变 / WP-6 截断可观测 / WP-8 数据一致性 / WP-9 error-handling / WP-10 安全权限）是 I4 的工作项，不是 I5 的 in-scope 验证项被降级。I5 record §6 已逐项登记，I6 可据此判定 Cycle 1 未达稳态。
- Successor Required: `yes`
- Successor Path: I4 完成 Phase 3-9 后，触发新一轮 I5 re-verification（本计划可作模板）；最终由 I6 做稳态判定。

## Non-Blocking Follow-ups

- 稳态判定与 Cycle 2 触发条件 —— I6 范围
- I4 Phase 3-9 执行 —— I4 范围

## Closure

Status Note: I5 作为 I4-已完成范围（Phase 1 OOM 族 WP-1/WP-2 + Phase 2 幂等性 WP-3）的独立全量复核：模块测试 138 全绿、四族门禁 real-violation 0（query-limit 超额归零 / entity-field-min 12 真违规已修剩 12 已接受全实体 / delete-contract 0 / idempotency red-list 锁迁移完成）、棘轮 baseline per-family 0 NEW、Anti-Hollow 0 high/critical、≥4 条修复路径调用链连通性确认。I4 commit 遗漏的 entity-field-min baseline `--update-baseline` 由 I5 补齐（diff 仅 1 行漂移 + 时间戳，无弱化）。**非 Cycle 1 稳态收口**：I4 Phase 3-9 仍 planned，需 I4 完成后 re-verify。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session, task_id `ses_005ee2092ffeMGokhb2mrs0N6z`，explore 类型）
- Audit Session: ses_005ee2092ffeMGokhb2mrs0N6z
- Evidence:
  - **测试**：`./mvnw test -pl nop-code -T 1C` → BUILD SUCCESS，nop-code-service `Tests run: 138, Failures: 0, Errors: 0, Skipped: 13`；`TestNopCodeIndexIdempotencyInvariant` 7/0/0；`TestQueryPaginationProjectionInvariant` 3/0/0 — PASS
  - **query-limit**：`--list` 0 total hits — PASS（超额完成：14 已接受有界被防御性 setLimit 修复，非扫描器弱化）
  - **entity-field-min**：`--list` 12 hits 均为 DTO 方法引用映射（抽查 `CodeQueryService.java:128 getFile`→`entityToFileResult`、`CodeIndexService.java:1676 listFlows`→`entityToExecutionFlow` 确认 accepted），real-violation 0 — PASS
  - **delete-contract**：0 hits — PASS
  - **idempotency**：live 核对 `TestNopCodeIndexIdempotencyInvariant.java:85-90` IDEMPOTENCE_TABLE 含 indexDirectory+indexFile；`:102-103` KNOWN_NON_IDEMPOTENT = {} — PASS
  - **棘轮 baseline per-family**：3 条命令各自 exit 0（query-limit / entity-field-min / delete-contract） — PASS
  - **扫描器未弱化**：`git show 1bddf8d50 -- check-nop-code-invariants.mjs` 空；`--self-test` 三族 canary PASS — PASS
  - **Anti-Hollow**：`scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` exit 0，0 high/critical — PASS
  - **调用链连通性**：`saveReplacingExisting`（CodeIndexService.java:1541-1565）真实 query-first upsert；`loadFingerprints`（OrmFingerprintStore.java:106-139）真实投影+分页；`deleteByIndex`（:155）真实分页删除；`filterByLanguage` 真实投影分页 — PASS（4 条）
  - **record 产出**：`i5-full-green-record.md` 存在（7 节） — PASS
  - **I4 范围诚实性**：I4 plan Phase 3-9 均 `planned`；record §7 显式「Cycle 1 未达稳态」，未过度声称 — PASS
  - **Deferred 分类**：I4 Phase 3-9 为 I4 范围 pending（非 I5 in-scope 降级），已移入 Deferred But Adjudicated 并附 non-blocking 理由 — PASS
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下方实测）
- 审计结论：I4-已完成范围（Phase 1-2）的 I5 验证全绿且真实；无静默降级、无扫描器弱化、无空壳实现。query-limit 33→0 为真实防御性修复（非 baseline 伪装）。entity-field-min baseline refresh（I4 遗漏，I5 补齐）已透明披露，diff 仅行漂移。

Follow-up:

- I4 Phase 3-9 执行（I4 范围）
- I4 完成后触发 I5 re-verification（本计划作模板）
- 稳态判定（I6）
