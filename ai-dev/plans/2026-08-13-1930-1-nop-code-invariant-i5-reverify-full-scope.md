# nop-code 不变式闭环 I5-reverify — 全量验证（Cycle 1，I4 Phase 3-9 补验）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I5. 全量验证（re-verification，覆盖 I4 全部 Phase 1-9）
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（header 下一行动 = I5 re-verify；Loop Rule T0）；前序 I5 `2026-08-13-1059-6`（仅验 Phase 1-2，Phase 3-9 deferred）
> Related: 前驱 `2026-08-13-1059-5`（I4，Phase 1-9 全部 completed，closure evidence 383 tests green）；前序 I5 `2026-08-13-1059-6`（Phase 1-2 验证完成，Phase 3-9 deferred 到本计划）；后继 `2026-08-13-1930-2`（I6-revisit，消费本计划 full-scope 全绿结论做确定性稳态判定）

## Purpose

I4 Phase 3-9 已全部执行并落地（I4 plan `completed`，9 Phase 全 `[x]`），但前序 I5（`2026-08-13-1059-6`）仅独立验证了 I4 Phase 1-2（WP-1/WP-2/WP-3），其 `i5-full-green-record.md` §6/§7 显式声明 Phase 3-9 为 `planned`、不在验证覆盖内、Cycle 1 未达稳态。本计划对 **I4 全部 Phase 1-9** 执行一次完整的全量独立验证：模块级全绿 + 四族门禁 real-violation 零命中 + Phase 3-9 新增 focused tests 在全量运行中通过 + Anti-Hollow 调用链连通性覆盖 Phase 3-9 路径 + full-green 记录更新为全范围。本计划是 I6-revisit 确定性稳态判定的确定性输入。

## Current Baseline

> 已对 live repo 核对（2026-08-13，独立 explore agent 验证）。

- **I4 Phase 1-9 全部 completed**：I4 plan（`2026-08-13-1059-5`）`Plan Status: completed`，9 Phase 均 `Status: completed` + 全 checklist `[x]`。closure evidence 记录 nop-code 383 tests, 0 failures, 0 errors。
- **Phase 3-9 代码/测试 artifact 已落地（live 核对确认存在）**：
  - Phase 3：`removeStaleSymbolDocsForFile`（`CodeIndexService.java:1593` 定义，`:1149` 调用）+ `TestIncrementalSearchSync`（`nop-code-service/src/test/.../TestIncrementalSearchSync.java`）
  - Phase 4：跨文件孤儿清理 `deleteRelationalBySymbolIds(NopCodeUsage.class,...)`（`CodeIndexService.java:1543`）+ `TestDeletePathIntegrity`
  - Phase 5：`SymbolTable.getAll()` 防御快照 + `CallGraph.getForwardMap()` 不可变快照 + `filterByLanguage` 拷贝；测试 `TestSymbolTable.testGetAllReturnsDefensiveSnapshot`（`nop-code-core/.../graph/TestSymbolTable.java:71`）+ `TestCallGraphImmutability.testGetForwardMapValuesAreImmutableSnapshot`（`nop-code-core/.../graph/TestCallGraphImmutability.java:47`）
  - Phase 6：`warnIfCapped`（`CodeQueryService.java:52`，9 调用点）+ `warnIfCacheTruncated`（`CodeGraphService.java:71`）+ `TestTruncationObservability`
  - Phase 7：`getSymbolById` 双过滤隔离修复 + `ChangeAnalyzer.pathSegmentMatch` 边界修复；测试 `TestGetSymbolByIdIsolation` + `TestChangeAnalyzerPathMatching.testNoFalsePositiveOnPrefixedPackageSegment`（`nop-code-flow/.../TestChangeAnalyzerPathMatching.java:52`）
  - Phase 8：`packagePrefix()`（`CodeQueryService.java:63`，用于 `:484`/`:518`）+ `TestPackageFilterBoundary`
  - Phase 9：@Auth 调查清单交付（8 个空 BizModel 已识别），@Auth 实施阻塞待人工确认（ask-first gate）
- **幂等表状态（live 核对）**：`KNOWN_NON_IDEMPOTENT = {}`（空），`IDEMPOTENCE_TABLE = {triggerIncrementalIndex, batchSaveFileRecords, indexDirectory, indexFile}`（`TestNopCodeIndexIdempotencyInvariant.java:85-103`）
- **四族门禁命中（live 核对，无 `--baseline` 的 `--list` 视角——所有 finding 标 NEW）**：query-limit **0 total** / entity-field-min **12 total**（均为 red list §2「已接受全实体」DTO 映射；带 `--baseline` 时这 12 条全为 KNOWN）/ delete-contract **0 total**
- **前序 I5 状态**：`2026-08-13-1059-6` `Plan Status: completed`，但验证范围限于 Phase 1-2。`i5-full-green-record.md` §6 列 Phase 3-9 全部 `planned`；§7 明确「Cycle 1 未达稳态：I4 Phase 3-9 未执行」。**该记录现在 stale**——Phase 3-9 已执行落地。
- **真正剩余 gap**：I4 Phase 3-9 代码已落地但 **未经 I5 级全量独立验证**——前序 I5 的验证覆盖不含 Phase 3-9 的 focused tests 全量运行、Phase 3-9 的 Anti-Hollow 调用链追踪、以及 full-green 记录的全范围更新。

## Goals

- `./mvnw test -pl nop-code -T 1C` 全绿——重点确认 Phase 3-9 新增 focused tests 在全量运行中通过（非孤立单跑）。
- 四族门禁 real-violation 零命中复核（query-limit 0 / entity-field-min real-violation 0 / delete-contract 0 / idempotency red-list 锁 0）——确认 Phase 3-9 未引入新违规。
- Phase 3-9 新增 Anti-Hollow 调用链连通性追踪（≥4 条 Phase 3-9 特定路径），无空壳/静默跳过。
- `i5-full-green-record.md` 更新为全范围（Phase 1-9）：移除「Phase 3-9 不在覆盖内」声明，补充 Phase 3-9 focused tests 列表 + 调用链追踪 + 统计。
- 棘轮 baseline per-family 复核（3 条命令各自 exit 0）。

## Non-Goals

- 不修复任何缺陷——若 I5 re-verify 发现 regression，退回 I4 修复（I4 已 completed，需新 successor plan 或 reopen）。
- 不做稳态判定 / 复触发条件登记——I6-revisit（`2026-08-13-1930-2`）范围。
- 不沉淀新门禁——Cycle 2 / I1 范围。
- 不执行 Phase 9 @Auth 变更——阻塞待人工确认（ask-first gate），不在验证范围。
- 不执行 Phase 4 ORM cascadeDelete 变更——阻塞待人工确认（plan-first gate），service-layer 降级路径已覆盖。

## Scope

### In Scope

- 模块级测试全量运行（`./mvnw test -pl nop-code -T 1C`），重点确认 Phase 3-9 新增 8 个 focused test 类在全量运行中通过。
- 四族门禁 strict 模式 + 棘轮 baseline per-family 复核（3 条命令）。
- Phase 3-9 新增 Anti-Hollow 调用链连通性追踪（≥4 条特定路径）。
- `i5-full-green-record.md` 更新为 Phase 1-9 全范围。
- 独立 closure audit。

### Out Of Scope

- 缺陷修复（I4 successor / reopen）。
- 稳态判定与 Cycle 2 触发（I6-revisit）。
- 新门禁实现（Cycle 2 / I1）。
- Phase 9 @Auth / Phase 4 ORM cascadeDelete（阻塞待人工确认）。

## Execution Plan

### Phase 1 - 模块级全量测试（含 Phase 3-9 focused tests）

Status: completed
Targets: `nop-code` 模块组（`nop-code-api` / `nop-code-core` / `nop-code-dao` / `nop-code-service` / `nop-code-flow` / `nop-code-web`）

- Item Types: `Proof`

- [x] 执行 `./mvnw test -pl nop-code -T 1C`，记录完整输出（总 tests/failures/errors 数 + 时间戳）。若依赖模块 flaky（前序 I5 已知 `nop-auth-service TestChannelScanBindLoginE2E` VarCollector NPE），用「先 `install -DskipTests` 装依赖，再 `test -pl nop-code`」隔离
- [x] 确认 Phase 3-9 新增 focused tests 在全量运行中通过（非孤立单跑），至少覆盖以下 8 个 test 类：`TestIncrementalSearchSync`（Phase 3）/ `TestDeletePathIntegrity`（Phase 4）/ `TestSymbolTable`（Phase 5，含 `testGetAllReturnsDefensiveSnapshot`）/ `TestCallGraphImmutability`（Phase 5，含 `testGetForwardMapValuesAreImmutableSnapshot`）/ `TestTruncationObservability`（Phase 6）/ `TestGetSymbolByIdIsolation`（Phase 7）/ `TestChangeAnalyzerPathMatching`（Phase 7，含 `testNoFalsePositiveOnPrefixedPackageSegment`）/ `TestPackageFilterBoundary`（Phase 8）
- [x] 若有失败：定位失败用例，判定是 I4 Phase 3-9 regression（退回 I4 successor / reopen）还是既有 flaky（记录并重跑）

> 实测结果（2026-08-13T21:22:04+08:00 起）：先 `./mvnw install -DskipTests -pl nop-code -am -T 1C` 装依赖隔离 flaky，再 `./mvnw test -pl nop-code -T 1C` → **BUILD SUCCESS**，13 子模块全 SUCCESS。surefire 汇总：**Tests run: 383, Failures: 0, Errors: 0, Skipped: 13**。8 个 Phase 3-9 focused 类全绿（逐类：TestIncrementalSearchSync 1/0/0、TestDeletePathIntegrity 1/0/0、TestSymbolTable 6/0/0、TestCallGraphImmutability 7/0/0、TestTruncationObservability 2/0/0、TestGetSymbolByIdIsolation 1/0/0、TestChangeAnalyzerPathMatching 6/0/0、TestPackageFilterBoundary 1/0/0）。Phase 1-2 既有类同运行全绿（TestNopCodeIndexIdempotencyInvariant 7/0/0、TestQueryPaginationProjectionInvariant 3/0/0）。特定方法 `testGetAllReturnsDefensiveSnapshot`/`testGetForwardMapValuesAreImmutableSnapshot`/`testNoFalsePositiveOnPrefixedPackageSegment` 经 XML report 确认存在且通过。无失败用例需分类。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-code -T 1C` 退出码 0
- [x] Phase 3-9 的 8 个 focused test 类在全量运行中全绿（逐类确认 tests run / failures / errors）
- [x] **端到端验证**：I4 各 Phase 的 focused test 全部在本次全量运行中通过（非孤立单跑）——Phase 1-2 既有 test（`TestNopCodeIndexIdempotencyInvariant` / `TestQueryPaginationProjectionInvariant`）+ Phase 3-9 新增 test 均在同一运行中 green
- [x] 失败用例（若有）已分类（I4 regression / flaky / 既有），regression 已标注退回 I4（无失败用例）
- [x] `No owner-doc update required`（I5 为纯验证，不改产品代码）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 四族门禁 real-violation 零命中复核 + 棘轮 baseline per-family

Status: completed
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`；baseline `ai-dev/audits/nop-code-invariants/baselines/baseline-*.json`；`TestNopCodeIndexIdempotencyInvariant`

- Item Types: `Proof`

- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list`：命中 0（real-violation 0）
- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min --list`：命中 12（全部为 red list §2「已接受全实体」DTO 映射，real-violation 0）
- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract --list`：命中 0
- [x] `./mvnw test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexIdempotencyInvariant`：全绿，`KNOWN_NON_IDEMPOTENT` 为空
- [x] 棘轮 baseline per-family 复核（3 条显式命令，各接单文件 baseline，**必须用完整路径**——工具 `loadBaseline` 对不存在路径静默返回空 Set）：`--family query-limit --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-query-limit.json` / `--family entity-field-min --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-entity-field-min.json` / `--family delete-contract --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-delete-contract.json` 各自退出码 0（无 `[NEW]` 违规）
- [x] 若 entity-field-min baseline 行号漂移（Phase 3-9 新增代码导致行移）：执行 `--update-baseline` 机械刷新，审计 diff 仅行号 + 时间戳变化，签名集合不变，无语义弱化（本次无需 refresh：3 条 baseline per-family 均 0 NEW，行号已匹配）

Exit Criteria:

- [x] query-limit real-violation = 0（命中 0）
- [x] entity-field-min real-violation = 0（命中 12 全为已接受全实体）
- [x] delete-contract = 0
- [x] idempotency `KNOWN_NON_IDEMPOTENT` = ∅
- [x] 棘轮 baseline per-family：3 条命令各自退出码 0（无 `[NEW]` 违规）
- [x] **接线验证**：门禁扫描器覆盖了 I4 Phase 3-9 修改过的文件（抽查 `CodeIndexService`/`CodeQueryService`/`CodeGraphService`/`CodeSearchService` 在 `--list` 输出中状态正确）
- [x] 若 baseline refresh：diff 审阅记录（仅行漂移 + 时间戳，无弱化）（本次无需 refresh）
- [x] `No owner-doc update required`（I5 为纯验证）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Phase 3-9 Anti-Hollow 调用链连通性追踪

Status: completed
Targets: `scan-hollow-implementations.mjs`；Phase 3-9 新增代码路径

- Item Types: `Proof`

- [x] `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high`：退出码 0（Critical/High 0）
- [x] 追踪 Phase 3-9 特定调用链连通性（≥4 条），确认从入口点到出口点完整连通、无空方法体/静默跳过/no-op。建议追踪路径（从 I4 plan Phase 3-9 checklist 选取）：
  - **Phase 3 搜索同步**：`saveFileResultInSession` → `removeStaleSymbolDocsForFile`（`:1149` 调用 → `:1593` 定义，先查旧 symbol IDs → removeDocs，再 addDoc）——defense-in-depth，幂等 no-op when caller 已删
  - **Phase 4 跨文件孤儿清理**：`deleteFileRecords` → `deleteRelationalBySymbolIds(NopCodeUsage.class,...)`（`:1543`）+ batchDeleteFileRecords 事务包裹
  - **Phase 5 缓存不可变**：`SymbolTable.getAll()` 返回 `new ArrayList<>(byId.values())` 防御快照；`CallGraph.getForwardMap()` 返回 unmodifiableMap + 每值 unmodifiableList 拷贝
  - **Phase 6 截断可观测**：`CodeQueryService` 单次有界查询点经 `warnIfCapped` 检查；`CodeGraphService` 入口经 `warnIfCacheTruncated` 消费 `isTruncated()`
  - **Phase 7 数据一致性**：`getSymbolById` 双过滤 `indexId + id`；`ChangeAnalyzer.pathSegmentMatch` 双侧边界检查
  - **Phase 8 error-handling**：`findSymbols`/`findSymbolsPage` 经 `packagePrefix()` 补 "." 边界（不再跨包匹配）
- [x] 追踪 Phase 9 @Auth 阻塞状态：确认 8 个空 BizModel 清单已交付（调查产出），@Auth 注解**未**被 autonomous 添加（ask-first gate 合规）

> Anti-Hollow 追踪结果（6 条 Phase 3-9 路径，超过 ≥4 要求）：
> 1. **Phase 3**：`saveFileResultInSession`(:1149) → `removeStaleSymbolDocsForFile`(:1593 def)。`findSymbolIdsByFileId` 查旧 symbol IDs → 非空则 `searchEngine.removeDocs(topic, oldSymbolIds)`；`catch(Exception)` 仅 WARN 日志（defense-in-depth，主路径 addDoc 仍执行）。`searchEngine==null` 是可选依赖的 null-guard，非静默跳过。**真实 defense-in-depth**。
> 2. **Phase 4**：`deleteFileRecords`(:1508) → `:1543 deleteRelationalBySymbolIds(NopCodeUsage.class,"symbolId",symbolIds)` 显式删跨文件孤儿（AR-149/150 service-layer 降级）。`deleteRelationalBySymbolIds`(:1605) 为 `while+setLimit(DELETE_BATCH_SIZE)+break-when-empty+flushSession` 真实耗尽语义。`batchDeleteFileRecords`(:1946) 包 `withIndexLock→runInTransaction→runInSession` 真实事务/会话包裹。**真实跨文件清理**。
> 3. **Phase 5**：`SymbolTable.getAll()`(:45) 返回 `new ArrayList<>(byId.values())` synchronized 防御快照（AR-155/158）；`CallGraph.getForwardMap()`(:56) 返回 `Collections.unmodifiableMap(copy)` 且每值 `unmodifiableList(new ArrayList<>(...))`（AR-148）。**真实不可变快照**。
> 4. **Phase 6**：`warnIfCapped`(:52 def) 在 9 个查询点(:142/179/291/315/366/756/834/843/863) 检查 `isCapped` 并 WARN；`warnIfCacheTruncated`(:71 def) 在 CodeGraphService 入口(:87/98) 消费 `symbolTable.isTruncated()`/`callGraph.isTruncated()`。**真实可观测**。
> 5. **Phase 7**：`getSymbolById`(:444) 双过滤 `eq("indexId")+eq("id")`（AR-41 跨 index 隔离）；`pathSegmentMatch`(:274) 双侧边界 `startOk(idx==0||prev=='/') && endOk(end==len||next=='.'/'/')`（AR-10/40/51）。**真实语义修复**。
> 6. **Phase 8**：`packagePrefix`(:63) `endsWith(".")?name:name+"."` 用于 :484/:518 `startsWith("qualifiedName",...)` 边界（AR-160/162/165）。**真实 error-handling 修复**。
>
> Phase 9 @Auth：`grep -rn '@Auth' nop-code/nop-code-web/src/main/java`（排除 _gen）**计数=0**——autonomous 未越权添加权限注解。I4 plan(`2026-08-13-1059-5` Phase 9 :260) 已交付调查清单：8 个空 CRUD BizModel 已识别（NopCodeFlowBizModel/NopCodeAnnotationUsageBizModel/NopCodeFlowMembershipBizModel/NopCodeUsageBizModel/NopCodeDependencyBizModel/NopCodeCallBizModel/NopCodeSemanticEdgeBizModel/NopCodeInheritanceBizModel），补 @Auth 阻塞待人工确认（ask-first gate 合规）。

Exit Criteria:

- [x] `scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0
- [x] Phase 3-9 特定调用链 ≥4 条已人工追踪确认连通（从入口到出口），每条记录起点方法 → 终点方法 + 连通证据（实际追踪 6 条）
- [x] **无静默跳过**：Phase 3-9 新增方法在未实现分支抛异常或做显式语义（非空方法体/continue/吞异常）
- [x] Phase 9 @Auth 阻塞状态确认（autonomous 未越权改权限语义）
- [x] `No owner-doc update required`（I5 为纯验证）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - full-green 记录更新为全范围（Phase 1-9）

Status: completed
Targets: `ai-dev/audits/nop-code-invariants/i5-full-green-record.md`（更新）

- Item Types: `Proof`

- [x] 更新 `i5-full-green-record.md` 验证范围声明：从「I4 已落地范围（Phase 1-2）」改为「I4 全部 Phase 1-9」
- [x] 更新 §1 模块级测试：记录全范围测试命令 + 退出码 + 总 tests 数 + 时间戳
- [x] 更新 §2 四族门禁命中：保持修复前→修复后对比表，补充确认 Phase 3-9 未引入新违规
- [x] 更新 §4 Anti-Hollow：补充 Phase 3-9 特定调用链追踪记录（≥4 条路径）
- [x] 更新 §5 I4 修复统计：从「Phase 1-2 已落地」改为「Phase 1-9 全部落地」
- [x] **重写 §6**：从「I4 未完成范围（Phase 3-9 planned）」改为「I4 全部 Phase 1-9 已完成」——列出 Phase 3-9 各 WP 的落地结论（fixed+test / stale 附证据 / by-design / gated 阻塞待确认）
- [x] **重写 §7 结论**：从「Cycle 1 未达稳态：I4 Phase 3-9 未执行」改为「I4 全部 Phase 1-9 全量独立验证通过」——声明本次验证覆盖完整 I4 scope，供 I6-revisit 做确定性稳态判定
- [x] 同步刷新 §3 棘轮 baseline 表：命令改为完整路径 `ai-dev/audits/nop-code-invariants/baselines/baseline-*.json`（前序 record 使用了相对路径，从项目根目录执行时工具 `loadBaseline` 会静默 fall back 到空 Set 导致假阳性）

> 记录已重写：标题改为「全范围 Phase 1-9」；验证范围声明改为 I4 全部已落地范围 + 两个 gated 阻塞项声明；§1 增 Phase 3-9 focused tests 全量表（8 类 25 tests）+ 子模块分布；§2 增「Phase 3-9 未引入新违规」确认；§3 改完整路径 + refresh 历史说明；§4 重写为 6 条 Phase 3-9 路径 + Phase 1-2 复核 + 接线验证 + @Auth 阻塞状态；§5 改 Phase 1-9 全部落地 + 10 个 focused test 类清单；§6 改「落地结论」表（fixed+test / gated）；§7 改「Phase 1-9 全量独立验证通过」。

Exit Criteria:

- [x] `i5-full-green-record.md` 验证范围声明已更新为 Phase 1-9 全范围
- [x] §1 测试记录含全范围命令 + 退出码 + 时间戳
- [x] §6 不再含 `planned` 状态的 Phase 3-9 工作项（全部更新为 completed / stale / by-design / gated）
- [x] §7 结论声明「I4 全部 Phase 1-9 全量独立验证通过」（不再说「未达稳态」）
- [x] 本 Phase 为纯文档记录：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **前置条件**：I4 全部 Phase 1-9 已 completed（I4 plan closure evidence 确认）。本计划验证 I4 全部 scope。
>
> **Phase 9 @Auth / Phase 4 ORM cascadeDelete 阻塞项**：这两项阻塞待人工确认（ask-first / plan-first gate），不在 I5 验证范围。I4 已为两者落地 service-layer 降级路径 / 调查清单。I5 验证的是「已落地部分」全绿，不验证「阻塞未执行部分」。

- [x] `./mvnw test -pl nop-code -T 1C` 退出码 0
- [x] Phase 3-9 的 8 个 focused test 类在全量运行中全绿
- [x] query-limit real-violation = 0 / entity-field-min real-violation = 0 / delete-contract = 0
- [x] idempotency `KNOWN_NON_IDEMPOTENT` = ∅
- [x] 棘轮 baseline per-family：3 条命令各自退出码 0
- [x] `scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0
- [x] Phase 3-9 特定调用链 ≥4 条已追踪确认连通
- [x] `i5-full-green-record.md` 已更新为 Phase 1-9 全范围（§6/§7 不再含 planned / 未达稳态 声明）
- [x] 不存在被静默降级的 in-scope 验证项（Phase 9 @Auth / Phase 4 ORM 为 gated 阻塞，非 I5 验证项降级）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### Phase 9 @Auth ×8 BizModel（阻塞待人工确认，ask-first gate）

- Classification: `阻塞待确认（权限模型 Protected Area）`
- Why Not Blocking Closure: @Auth 注解新增会使先前无保护的 action 要求权限，属用户可见行为变更，须人工确认。I4 已交付调查清单（8 个空 BizModel 已识别），service-layer 无降级需求（空 CRUD BizModel 当前行为正确，@Auth 是硬化而非修缺陷）。I5 验证的是已落地范围，不验证阻塞未执行部分。
- Successor Required: `yes`
- Successor Path: 人工确认通过后在 successor plan 补 @Auth + action-auth.xml 一致性

### Phase 4 AR-149/150 ORM cascadeDelete（阻塞待人工确认，plan-first gate）

- Classification: `阻塞待确认（ORM 结构变更 Protected Area）`
- Why Not Blocking Closure: ORM 结构变更须人工确认。I4 已落地 service-layer 降级路径（`deleteRelationalBySymbolIds(NopCodeUsage.class,...)` 显式删除），correctness 已由 service 层保证，ORM cascadeDelete 为 declarative belt-and-suspenders。I5 验证的是已落地降级路径全绿。
- Successor Required: `yes`
- Successor Path: 人工确认通过后在 successor plan 补 cascadeDelete

## Non-Blocking Follow-ups

- 工具硬化：`check-nop-code-invariants.mjs` 的 `loadBaseline` 对不存在路径静默返回空 Set，应改为显式 WARN 或 exit non-zero（系统性风险，非本 plan 范围）
- 稳态判定（I6-revisit `2026-08-13-1930-2`）—— 消费本计划 full-scope 全绿结论
- Phase 9 @Auth / Phase 4 ORM cascadeDelete successor plan —— 阻塞待人工确认

## Closure

Status Note: I5-reverify 全量验证完成。对 I4 全部 Phase 1-9 执行了 full-scope 独立验证：模块测试 383 全绿（含 Phase 3-9 的 8 个 focused test 类 25 tests + Phase 1-2 既有 test 在同一运行中 green）、四族门禁 real-violation 0、棘轮 baseline per-family（完整路径）3 条命令各自退出码 0、Anti-Hollow 扫描 0 high/critical + 6 条 Phase 3-9 调用链人工追踪确认连通、Phase 9 @Auth + Phase 4 ORM cascadeDelete 两项 gated 阻塞项诚实裁定（I5 验证已落地部分，不验证阻塞未执行部分）。`i5-full-green-record.md` 已更新为 Phase 1-9 全范围（§6/§7 不再含 planned / 未达稳态）。独立 fresh session 子 agent closure-audit 返回 CAN CLOSE（10 项全 PASS）。本计划是 I6-revisit（`2026-08-13-1930-2`）确定性稳态判定的确定性输入。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh session closure-audit 子 agent（explore，read-only）
- Audit Session: ses_004afe1caffeATk8iclbOXONsT
- Evidence:
  - **ITEM 1 模块测试 PASS**：聚合 76 testsuite XML（7 个 nop-code surefire dir）= Tests run: 383, Failures: 0, Errors: 0, Skipped: 13（skipped 为既有平台条件跳过）。
  - **ITEM 2 Phase 3-9 focused tests PASS**：8 个 test 类各自 surefire `.txt` 报告 0 failures/0 errors（TestIncrementalSearchSync 1/0/0、TestDeletePathIntegrity 1/0/0、TestSymbolTable 6/0/0、TestCallGraphImmutability 7/0/0、TestTruncationObservability 2/0/0、TestGetSymbolByIdIsolation 1/0/0、TestChangeAnalyzerPathMatching 6/0/0、TestPackageFilterBoundary 1/0/0；合计 25/0/0）。
  - **ITEM 3 四族门禁 + 幂等表 PASS**：`TestNopCodeIndexIdempotencyInvariant.java:102-103` `KNOWN_NON_IDEMPOTENT = new HashSet<>(Arrays.asList())` 字面空；`IDEMPOTENCE_TABLE`（:85-90）含 4 方法（triggerIncrementalIndex/batchSaveFileRecords/indexDirectory/indexFile）。
  - **ITEM 4 Anti-Hollow 工具 PASS**：`scan-hollow-implementations.mjs` 存在；`nop-code/nop-code-service/src/main --severity high` 退出码 0。
  - **ITEM 5 Phase 3-9 调用链（6 条）PASS**：逐条 live 源码确认非空壳——Phase 3 `removeStaleSymbolDocsForFile`(:1593←:1149) findSymbolIdsByFileId→removeDocs；Phase 4 `deleteRelationalBySymbolIds(NopCodeUsage.class,...)`(:1543) + `batchDeleteFileRecords`(:1946) runInTransaction+runInSession；Phase 5 `SymbolTable.getAll()`(:45) `new ArrayList<>(byId.values())` + `CallGraph.getForwardMap()`(:56) unmodifiableMap/unmodifiableList；Phase 6 `warnIfCapped`(:52)/`warnIfCacheTruncated`(:71) isCapped/isTruncated；Phase 7 `getSymbolById`(:444) eq(indexId)+eq(id)+setLimit(1) + `pathSegmentMatch`(:274) startOk&&endOk 双侧边界；Phase 8 `packagePrefix`(:63) endsWith?name:name+"."。
  - **ITEM 6 Phase 9 @Auth gate PASS**：`grep -rn '@Auth' nop-code/nop-code-web/src/main/java`（排除 _gen）= 0 匹配，autonomous 未越权改权限语义。
  - **ITEM 7 full-green 记录 PASS**：`i5-full-green-record.md` 验证范围声明为 Phase 1-9（非 1-2）；§6 无 `planned` 条目（全 fixed+test / gated）；§7「I4 全部 Phase 1-9 全量独立验证通过」（非「未达稳态」）。
  - **ITEM 8 无静默降级 PASS**：Phase 9 @Auth + Phase 4 ORM cascadeDelete 在 record + plan「Deferred But Adjudicated」显式分类为 gated 阻塞，非静默 dropped。
  - **ITEM 9 deferred 诚实性 PASS**：两项 deferred 均有「Why Not Blocking Closure」+「Successor Required: yes」。
  - **ITEM 10 文本一致性 PASS**：4 Phase 均 `Status: completed`，Phase 1-4 sections 内零 `[ ]`。
  - **checklist 工具**：`node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - **Anti-Hollow 扫描**：`scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0（Critical 0 / High 0 / Medium 0 / Low 0）。
- **Deferred 项分类检查**：两项 deferred（Phase 9 @Auth ask-first、Phase 4 AR-149/150 ORM cascadeDelete plan-first）均为「阻塞待人工确认」+「Successor Required: yes」，非 in-scope live defect 降级。

Follow-up:

- I6-revisit（`2026-08-13-1930-2`）：消费本计划 full-scope 全绿结论做确定性稳态判定
- Phase 9 @Auth ×8 BizModel + AR-155(r10)/170 successor plan：阻塞待人工确认（ask-first gate）
- Phase 4 AR-149/150 ORM cascadeDelete successor plan：阻塞待人工确认（plan-first gate）
- 工具硬化（non-blocking）：`check-nop-code-invariants.mjs` 的 `loadBaseline` 对不存在路径静默返回空 Set，应改为显式 WARN 或 exit non-zero
