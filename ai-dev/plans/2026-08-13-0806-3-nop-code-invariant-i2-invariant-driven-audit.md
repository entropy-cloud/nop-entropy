# nop-code 不变式闭环 I2 — 不变式驱动审计（Cycle 1）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I2. 不变式驱动审计
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I2 + Phase Details I2–I6）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 前驱 `2026-08-13-0709-2-nop-code-invariant-i1-first-batch-gates.md`（I1，本计划依赖其门禁 + gate-baseline-I1 产出）；后继 `2026-08-13-0806-4-nop-code-invariant-i3-finding-adjudication.md`（I3，消费本计划 red list + coverage matrix）

## Purpose

把 I1 落地的四族可执行门禁**确定性跑一遍**，产出三类确定性产物作为 I3 裁决输入：

1. **red list**：门禁命中清单逐条分类（真违规 / 已接受有界查询 / 门禁误报），每条附 `文件:行` + 关联 AR-ID + 归属不变式。
2. **对抗探查报告**：聚焦增量索引并发路径与 OOM 潜在点（门禁静态扫描覆盖不到的盲区），用 live code 追踪 + 针对性 JUnit 探针暴露隐藏缺陷。
3. **悬空发现覆盖矩阵**：盘点 `ar-status-matrix.md` 的 95 条 `open` 发现，逐条标注「已被某不变式门禁覆盖」还是「仍需手动修复（无门禁覆盖）」。

本计划只产出审计/分析文档与（如对抗探查需要）探针测试，不改产品业务逻辑。

## Current Baseline

> 已对 live repo 核对（2026-08-13）。I1 已 `completed`，本计划消费其产出。

- **I1 门禁已落地且可运行**（`ai-dev/tools/check-nop-code-invariants.mjs`）：
  - `--self-test` 三 family canary 全 PASS（已复跑：query-limit / entity-field-min / delete-contract 各抓植入违背并定位行）。
  - 聚合 strict 模式（`--module nop-code` 无 baseline）报 57 条违规（33 query-limit + 24 entity-field-min + 0 delete-contract），退出码 1。
- **I1 baseline 命中（I2 red list 起点，见 `gate-baseline-I1.md`）**：
  - `query-limit`：33 命中。已知锚点 `CodeSearchService.java:202`（buildFilePathCache，AR-168）、`CodeIndexService.java:1404`（getProjectFilePaths，AR-177）均「投影已修但无 setLimit」。其余 31 条散布在 `CodeIndexService`（删除辅助/流程查询/findImplementations/batch）、`CodeQueryService`（14 处）、`CodeSearchService.java:329`（filterByLanguage）、`OrmFingerprintStore`（4 处）、`CodeCacheManager.java:247`。**LIMIT 豁免裁定**：投影查询不豁免 setLimit；唯一豁免 = 可证明结果集有界（等值过滤单条 / 分页删除 `setLimit(BATCH_SIZE)`）。
  - `entity-field-min`：24 命中。子模式：`.size()`→countByQuery（`OrmFingerprintStore`/`CodeIndexService`）、≤3 getter→投影（`CodeQueryService`/`CodeIndexService`/`CodeSearchService`）。
  - `delete-contract`：0 命中（退化形态：live 11 实体均无 useLogicalDelete）。
  - `idempotency`（JUnit）：2 green 锁（`triggerIncrementalIndex`、`batchSaveFileRecords`）+ 2 red-list 可执行锁（`indexDirectory`、`indexFile` 重试抛 duplicate-key 23505，`KNOWN_NON_IDEMPOTENT`）。
- **悬空发现 95 条 `open`**（`ar-status-matrix.md` 族聚合表）：分布在 concurrency-lock(11) / data-consistency(10) / error-handling(24) / graph-algorithm(12) / language-adapter(10) / OOM-field-loading(4) / incremental-index-desync(3) / orm-schema(5) / auth-security(3) / performance(4) / dead-code(7) / config-contract(2)。
- **门禁静态扫描盲区**（I2 对抗探查聚焦点）：四族门禁均为 AST/regex 静态扫描 + JUnit 幂等穷举，**不覆盖**：① 运行时并发竞态（CallGraph/SymbolTable 线程安全、缓存引用泄漏——INV-05 仍未沉淀为门禁）；② 跨文件孤儿清理语义（deleteFileRecords 的跨文件 calleeId/targetSymbolId 引用）；③ 搜索引擎与 DB 去同步（增量索引不清理旧符号文档 AR-166）；④ 截断静默化（MAX_QUERY_RESULTS 处是否有 WARN/可观测信号）。
- **INV-05（缓存对象不可变性）未在 I1 落地为门禁**：catalog 列为 P1 附加不变式，I1 只沉淀 INV-01..04。INV-05 的 open 锚点（`CallGraph.java:46,52`、`CodeIndexService.java:1030-1073`、`CodeCacheManager.java:146-160`）需 I2 对抗探查覆盖，是否沉淀新门禁由 I3 裁决 → Cycle 2 / I1（Loop Rule 预授权）。

## Goals

- 产出 `ai-dev/audits/nop-code-invariants/i2-red-list.md`：四族门禁全部命中逐条分类（真违规 / 已接受有界 / 门禁误报），每条可追溯到 `文件:行` + AR-ID + 归属不变式。
- 产出 `ai-dev/audits/nop-code-invariants/i2-adversarial-probe.md`：增量索引并发路径 + OOM 潜在点的 live code 追踪 + 针对性探针发现，新发现追加进 red list。
- 产出 `ai-dev/audits/nop-code-invariants/i2-coverage-matrix.md`：95 条 open 悬空发现逐条标注「门禁覆盖状态」+「是否仍需手动修复」，作为 I3 裁决的悬空发现处置矩阵。
- 确保对抗探查不是空壳：每条新发现附 live code 路径证据，能被 I3 裁决或被门禁锁定。

## Non-Goals

- 不裁决 red list 条目的 P0/P1 优先级 / 修复方案 —— 那是 I3。
- 不修复任何违规 —— 那是 I4。
- 不沉淀新不变式门禁（如 INV-05 落地为 JUnit/.mjs）—— 新族沉淀属 Cycle 2 / I1（Loop Rule 预授权），I2 只产出「是否需要新门禁」的裁定输入给 I3。
- 不改产品业务逻辑（对抗探查如需 JUnit 探针，探针为测试代码，不改 `src/main`；若探针发现 live defect，记录到 red list 而非在本计划修）。
- 不重跑 I0 的 13 轮历史审计 —— 本计划只盘点其产出的当前覆盖状态。

## Scope

### In Scope

- 用 I1 门禁 strict 模式跑 nop-code 全方法，产出原始命中清单。
- 对 33 + 24 + 2 条命中逐条分类：真违规（待修）/ 已接受有界查询（附有界证据）/ 门禁误报（附误报理由）。
- 对抗探查：增量索引并发路径（CallGraph/SymbolTable/CodeCacheManager 线程安全 + 缓存引用泄漏）+ OOM 潜在点（截断静默化、未门禁覆盖的全量加载）+ 跨文件孤儿清理 + 搜索引擎去同步。
- 盘点 95 条 open 悬空发现的门禁覆盖状态。
- 若对抗探查需 JUnit 探针验证运行时行为，编写探针测试（`src/test`），探针结论写入 adversarial-probe 报告。

### Out Of Scope

- I3 的裁决（P0/P1 优先级 + 修复方案）。
- I4 的修复执行。
- INV-05 等新族的门禁实现（Cycle 2 / I1）。
- 其他模块（nop-stream/nop-metadata/nop-ai）的不变式审计（范围独立）。

## Execution Plan

### Phase 1 - 跑门禁 + red list 逐条分类

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i2-red-list.md`（新建）；输入 = I1 门禁（`check-nop-code-invariants.mjs`）+ `gate-baseline-I1.md` + JUnit `TestNopCodeIndexIdempotencyInvariant`（`KNOWN_NON_IDEMPOTENT` 锁）

- Item Types: `Proof | Decision`

- [x] 跑 `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list`，捕获全部 33 条命中的 `文件:行 + 方法(var)` 原始清单
- [x] 跑 `--family entity-field-min --list`，捕获全部 24 条命中原始清单
- [x] 确认 `--family delete-contract --list` = 0 命中（I1 退化形态复核），在 red list 中显式记录「0 命中 + 退化理由」而非默认跳过
- [x] 汇总 JUnit `KNOWN_NON_IDEMPOTENT` 锁（`indexDirectory`/`indexFile` duplicate-key 23505）进 red list，标注为「已锁定的真违规（I4 靶点）」
- [x] 对 query-limit 33 条逐条分类：`真违规`（无 setLimit 且非有界白名单）/ `已接受有界`（附有界证据：等值单条 / 分页 BATCH_SIZE / countByQuery）/ `门禁误报`（附误报理由）；I0 已知锚点（AR-168 buildFilePathCache、AR-177 getProjectFilePaths）须明确裁定为真违规
- [x] 对 entity-field-min 24 条逐条分类：`真违规`（全实体加载仅读 ≤3 字段，应投影）/ `已接受全实体`（附理由：删除语义必要加载如 deleteEntitiesPaged / 需 CLOB 以外的多字段）/ `门禁误报`（附理由）
- [x] LIMIT 豁免裁定逐条落地：凡标「已接受有界」的条目，须在 red list 写明具体有界证据（哪一行证明了结果集有界），不允许只写「有界」无证据

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `i2-red-list.md` 存在，含四族门禁的全部命中（query-limit 33 + entity-field-min 24 + delete-contract 0 + idempotency 2 red-list 锁）
- [x] 每条命中有且仅有一个分类（`真违规` | `已接受有界/全实体` | `门禁误报`），真违规/已接受附 `文件:行` 证据，误报复误报理由
- [x] red list 顶部有分类计数汇总表（真违规 N / 已接受 M / 误报 K，N+M+K == 命中总数），与逐条计数一致
- [x] AR-168（buildFilePathCache）与 AR-177（getProjectFilePaths）明确裁定为真违规（无 setLimit），无悬空
- [x] delete-contract 0 命中有显式「退化理由」记录（非默认跳过）
- [x] **无静默跳过**：分类为「门禁误报」的条目必须附理由，不得用「忽略」无说明略过；扫描器 `UNDETERMINED` 调用点须显式标注
- [x] 本 Phase 为纯分析文档（不改产品代码）：`No owner-doc update required`（red list 落 `ai-dev/audits/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 对抗探查（增量索引并发 + OOM 盲区）

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i2-adversarial-probe.md`（新建）；探查面 = INV-05 缓存不可变性 + INV-03 跨文件孤儿/搜索引擎去同步 + INV-04 截断静默化

- Item Types: `Proof`

- [x] **并发路径探查（INV-05 面）**：live code 追踪 `CallGraph` 同步一致性——注意区分已修方法（`getCallees`/`getCallers` 已 `synchronized` + 防御性拷贝）与仍 open 方法（`getAllNodeIds`/`getForwardMap` 无 `synchronized`，AR-145/148）；追踪 `SymbolTable` 缓存（`addToSymbolTableCache` 原地修改，AR-158）、`CodeCacheManager` 锁粒度（AR-92 前提部分过时：live 用 ReentrantLock 非 synchronized，需复核冗余指控）；逐条标注 `open`(AR-04/11/42/62/92/145/147/148/155/157/158/182) 的当前 live 状态
- [x] **OOM 盲区探查**：核对截断常量与静默化——`MAX_QUERY_RESULTS=10000`（仅 `CodeQueryService` 7 处）、`BATCH_QUERY_LIMIT=10000`（`CodeGraphService.collectRelevantInheritances`，AR-136）、`BATCH_SIZE=1000` 全分页（`CodeIndexService.buildInheritanceIndex`/`loadExistingEdgeKeys`，**AR-180 对此二方法前提疑似过时**——它们用 while+offset 全分页非截断，须 live 复核是否仍有截断）；核对截断点是否有 WARN 日志或可观测信号；核对 `CodeCacheManager` 超限降级（AR-61/76）是否静默返回不完整数据
- [x] **跨文件孤儿 + 搜索引擎去同步探查（INV-03 面）**：live code 追踪 `deleteFileRecords`（跨文件 calleeId/targetSymbolId 孤儿，AR-30/66）+ 增量索引不清理搜索引擎旧符号文档（AR-166）的当前实现状态
- [x] 若静态追踪不足以判定运行时行为：编写针对性 JUnit 探针（`nop-code-service/src/test`，如并发 indexFile + getOrRebuildSymbolTable 断言无 ConcurrentModificationException / 断言截断有 WARN），探针结论（PASS=行为已正确 / FAIL=确认缺陷）写入报告
- [x] 对抗探查新发现（门禁未覆盖的 live defect）追加进 `i2-red-list.md` 的「对抗探查新增」段，每条附 live code 路径 + 关联 AR-ID（如有）+ 建议归属不变式

Exit Criteria:

- [x] `i2-adversarial-probe.md` 存在，覆盖三个探查面（并发路径 / OOM 截断静默化 / 跨文件孤儿+搜索引擎去同步）
- [x] 每个探查面的每条 open 锚点有当前 live 状态结论（`缺陷确认` | `已修` | `需运行时探针验证`），缺陷确认附 `文件:行`
- [x] 若编写了 JUnit 探针：探针测试在 `./mvnw test -pl nop-code/nop-code-service -am -T 1C` 全绿（探针 PASS=行为正确）或明确记录 FAIL=确认缺陷（缺陷进 red list，不在本计划修）
- [x] **Test-Mandated**（若新增探针）：明确列出探针验证的行为 + 预期结果；若不新增探针（静态追踪充分）：注明 `No new test required: 静态 code 追踪足以判定`
- [x] 对抗探查新发现已追加进 red list，每条可追溯到 live code 路径（非空泛描述）
- [x] **无静默跳过**：探查面不得因「难判定」而省略；无法静态判定的须用运行时探针或在报告中显式标 `需运行时探针验证`
- [x] 本 Phase 新增探针为测试代码（不改 `src/main` 产品行为）：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 悬空发现覆盖矩阵

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i2-coverage-matrix.md`（新建）；输入 = `ar-status-matrix.md` 的 95 条 `open` 发现 + I1 四族门禁 + Phase 2 对抗探查结论

- Item Types: `Proof | Decision`

- [x] 对 `ar-status-matrix.md` 全部 95 条 `open` 发现，逐条标注门禁覆盖状态：`已被门禁覆盖`（注明 INV-XX + family）| `部分覆盖`（注明覆盖部分 + 缺口）| `未覆盖-需手动修复`（注明原因：运行时语义/跨文件语义/无对应门禁）
- [x] **枚举完备性处理**：`ar-status-matrix.md` 中约 75 条 open 为逐条单列，约 20 条在「选列」段以聚合形式描述（error-handling/graph-algorithm/language-adapter/orm-schema/其余族标注「约 N 条...等」）。对聚合条目，按 AR-ID 或失败模式（pattern → 覆盖状态）标注，并在矩阵顶部注明「聚合覆盖率」（聚合条目数 / 总 open 数），不得因无法逐条展开而省略
- [x] 按失败族聚合覆盖统计（OOM / incremental-desync / concurrency-lock / data-consistency / error-handling / graph-algorithm / language-adapter / orm-schema / auth-security / performance / dead-code / config-contract），每族标注「门禁覆盖率」
- [x] 对「未覆盖-需手动修复」的发现，标注其是否属于 I4 类别清扫面（修任一 SearchService 的全实体加载必 grep 全部 SearchService；修任一删除路径必穷举全部删除路径）
- [x] 对 INV-05（缓存不可变性）族：明确裁定「是否需要沉淀为新门禁」，若需要则记录为 Cycle 2 / I1 候选输入（不在本计划实现）

Exit Criteria:

- [x] `i2-coverage-matrix.md` 存在，覆盖全部 95 条 open 发现，每条有且仅有一个覆盖状态
- [x] 覆盖状态分类无悬挂（不允许「未判定」）；族聚合覆盖率与逐条计数一致
- [x] 「未覆盖-需手动修复」清单可作为 I3 裁决的悬空发现处置输入（每条注明为何无门禁覆盖）
- [x] INV-05 是否沉淀新门禁有明确裁定（`需要→Cycle2/I1 候选` | `不需要→理由`），无悬空
- [x] **无静默跳过**：不得用「待定」「后续再说」省略任何条目的覆盖状态裁定
- [x] 本 Phase 为纯分析文档：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档/分析计划**：本计划不改 `src/main` 产品代码（仅可能新增 `src/test` 探针）。若未新增探针，`./mvnw test`/`compile` 条目可从本节删除；若新增探针，须保留探针测试全绿条目。

- [x] 三份产出文件均存在：`i2-red-list.md` / `i2-adversarial-probe.md` / `i2-coverage-matrix.md`
- [x] red list 覆盖四族门禁全部命中，每条有分类 + 证据，分类计数与逐条一致
- [x] 对抗探查覆盖三个探查面，每条 open 锚点有 live 结论
- [x] 覆盖矩阵覆盖全部 95 条 open 发现，覆盖状态零悬挂
- [x] AR-168/AR-177 + delete-contract 退化 + INV-05 沉淀裁定 均无悬空
- [x] 若新增 JUnit 探针：`./mvnw test -pl nop-code/nop-code-service -am -T 1C` 全绿
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：本计划新增文档零断链（预存断链裁定记录在 Closure Evidence）

## Deferred But Adjudicated

### INV-05 门禁实现（缓存不可变性）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: I2 只产出「INV-05 是否需要沉淀为新门禁」的裁定输入；门禁实现属 Cycle 2 / I1（Loop Rule 预授权，roadmap Cross-Cutting 授权），不在 Cycle 1 / I2 范围。
- Successor Required: yes
- Successor Path: Cycle 2 / I1（由 I3 裁决 + I6 收口时按 Loop Rule 派生）

## Non-Blocking Follow-ups

- I3 裁决 red list + 悬空发现的 P0/P1 优先级与修复方案（属 I3）
- 对抗探查发现的 live defect 由 I4 修复（属 I4）

## Closure

Status Note: 三份产出文档已落地（`i2-red-list.md` / `i2-adversarial-probe.md` / `i2-coverage-matrix.md`），覆盖四族门禁全部命中逐条分类 + 三探查面对抗发现 + 95 条 open 悬空发现覆盖矩阵。纯分析计划，未改 `src/main`（Test-Mandated：No new test required，静态追踪充分）。独立 closure audit 已 PASS（仅程序性缺口，本次补齐）。I3 可消费 red list + 覆盖矩阵。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure 子 agent（fresh session，task `ses_00724ccfeffeqNsqF885m3agP0`，explore 类型）
- Audit Session: ses_00724ccfeffeqNsqF885m3agP0
- Evidence:
  - Phase 1 Exit（8/8 PASS）：red list 含 query-limit 33 + entity-field-min 24 + delete-contract 0 + idempotency 2；逐条分类计数对账一致（QL 19真违规+14已接受=33；EFM 12真违规+12已接受=24）；AR-168/AR-177 明确真违规；3 处 `.size()` 误标（:102/:899/:1818）显式标注；delete-contract 0 附退化理由；UNDETERMINED=0 + 机制说明。
  - Phase 2 Exit（8/8 PASS，2 N/A）：三探查面齐全；每 open 锚点有 live 结论（缺陷确认/已修/stale-premise）；5 条新发现进 red list §5；`No new test required: 静态 code 追踪足以判定`（并发 race 非确定性→Cycle 2 INV-05 门禁）。
  - Phase 3 Exit（7/7 PASS）：覆盖矩阵 95 条逐条/按模式标注，2 已覆盖+8 部分+85 未覆盖=95 零悬挂；INV-05 裁定「需要→Cycle2/I1 候选」。
  - Live-code spot-check（2/2 PASS）：`CodeSearchService.java:196-212` 投影无 setLimit→真违规正确；`CodeIndexService.java:1468-1471` eq(indexId)+eq(id 主键)→已接受有界正确。
  - Closure Gates（10/10，1 N/A）：3 文件存在；计数一致；INV-05 实现按 `out-of-scope improvement` 显式裁定 deferred（非静默）；C6 JUnit 探针 N/A（未新增）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（50/50 checkbox 已勾选 + Closure Evidence 已写入）。
  - `node ai-dev/tools/check-doc-links.mjs --strict`：本计划 3 份新文档**零断链**（全局退出码 1 源自其他文件的 20 条预存断链——`docs-for-ai/INDEX.md`、5 个 backlog roadmap、I3 后继 plan 前向引用 `i3-adjudication-matrix.md`、credential plan、skills doc——均非本计划引入，预存条件）。
  - Deferred 项分类检查：INV-05 门禁实现 = `out-of-scope improvement`（Why Not Blocking: I2 只产裁定输入；Successor: Cycle 2/I1）——非 in-scope live defect 降级。

Follow-up:

- INV-05 门禁实现（缓存不可变性）→ Cycle 2 / I1（已裁定 `需要沉淀`，见 adversarial-probe §5 + coverage-matrix §4）
- I3 裁决 red list 真违规 + 85 条未覆盖 open 发现的 P0/P1 优先级与修复方案（属 I3）
- 对抗探查 5 条新发现的 live defect 由 I4 修复（属 I4）
- 矩阵状态与 live 不符 4 处（AR-04 实测已修 / AR-092 / AR-180 / AR-153(r10) 实测 stale）建议 I3 触发 `ar-status-matrix.md` 改判
