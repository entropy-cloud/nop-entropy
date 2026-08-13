# nop-code 不变式闭环 I5 — 全量验证 Full-Green 记录（Cycle 1，全范围 Phase 1-9）

> Status: active
> Last Reviewed: 2026-08-13
> Source: `ai-dev/plans/2026-08-13-1930-1-nop-code-invariant-i5-reverify-full-scope.md`（I5 re-verify 全量验证）
> Verifier: mission-driver I5 full-scope 执行 session（2026-08-13）
> Scope: **I4 全部 Phase 1-9 的独立全量复核**（覆盖 Phase 1 OOM 族 + Phase 2 幂等性 + Phase 3 搜索同步 + Phase 4 删除路径 + Phase 5 缓存不可变 + Phase 6 截断可观测 + Phase 7 数据一致性 + Phase 8 error-handling；Phase 9 @Auth 为 gated 阻塞项，验证已落地范围）

## 验证范围声明（关键）

本记录验证 **I4 全部已落地范围（Phase 1-9 已 completed 部分）** 的全量结果。前序 I5（`2026-08-13-1059-6`）仅独立验证 Phase 1-2，声明 Phase 3-9 为 `planned`、不在覆盖内；**该声明现已 stale** —— Phase 3-9 代码/测试 artifact 已落地并经本次 full-scope 独立验证全绿。

本记录的「全绿」指 **I4-全落地范围（Phase 1-9）的四族门禁 + 模块测试 + Anti-Hollow 扫描 + Phase 3-9 调用链连通性全绿**。

**两个 gated 阻塞项不在 I5 验证覆盖内**（I4 已落地 service-layer 降级 / 调查清单，I5 验证的是「已落地部分」）：
- Phase 9 WP-10 @Auth ×8 空 BizModel —— 阻塞待人工确认（权限模型 ask-first gate）
- Phase 4 AR-149/150 ORM cascadeDelete —— 阻塞待人工确认（ORM 结构变更 plan-first gate；service-layer `deleteRelationalBySymbolIds(NopCodeUsage.class,...)` 显式删除已覆盖 correctness）

## 1. 模块级全量测试（全范围 Phase 1-9）

| 命令 | 结果 | 退出码 |
|------|------|--------|
| `./mvnw test -pl nop-code -T 1C` | **BUILD SUCCESS**（13 子模块全 SUCCESS） | 0 |
| `./mvnw test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexIdempotencyInvariant` | Tests run: 7, Failures: 0, Errors: 0 | 0 |
| `./mvnw test -pl nop-code/nop-code-service -Dtest=TestQueryPaginationProjectionInvariant` | Tests run: 3, Failures: 0, Errors: 0 | 0 |

- **全范围 surefire 汇总：Tests run: 383, Failures: 0, Errors: 0, Skipped: 13**（skipped 为既有平台级条件跳过，非 I4 相关）。
- 子模块分布：nop-code-core 120r/0f/0e/0s；nop-code-flow 42r/0f/0e/0s；nop-code-service 144r/0f/0e/13s；nop-code-web 1r/0f/0e/0s。
- 时间戳：2026-08-13T21:22:04+08:00（test 起始）；BUILD SUCCESS at 2026-08-13T21:22:35+08:00。
- **依赖模块 flaky 隔离**：先 `./mvnw install -DskipTests -pl nop-code -am -T 1C` 装依赖，再 `test -pl nop-code` 隔离 `nop-auth-service TestChannelScanBindLoginE2E` VarCollector NPE（AutoTest 基础设施问题，与 nop-code/I4 无关），nop-code 全绿。

### 1.1 Phase 3-9 focused tests 在全量运行中全绿（非孤立单跑）

| Phase | Test 类 | Tests run / Failures / Errors |
|-------|---------|-------------------------------|
| Phase 3 | `TestIncrementalSearchSync` | 1 / 0 / 0 |
| Phase 4 | `TestDeletePathIntegrity` | 1 / 0 / 0 |
| Phase 5 | `TestSymbolTable`（含 `testGetAllReturnsDefensiveSnapshot`） | 6 / 0 / 0 |
| Phase 5 | `TestCallGraphImmutability`（含 `testGetForwardMapValuesAreImmutableSnapshot`） | 7 / 0 / 0 |
| Phase 6 | `TestTruncationObservability` | 2 / 0 / 0 |
| Phase 7 | `TestGetSymbolByIdIsolation` | 1 / 0 / 0 |
| Phase 7 | `TestChangeAnalyzerPathMatching`（含 `testNoFalsePositiveOnPrefixedPackageSegment`） | 6 / 0 / 0 |
| Phase 8 | `TestPackageFilterBoundary` | 1 / 0 / 0 |

Phase 3-9 focused tests 合计 25 tests，全量运行中全绿。Phase 1-2 既有 test（`TestNopCodeIndexIdempotencyInvariant` 7/0/0、`TestQueryPaginationProjectionInvariant` 3/0/0）在同一运行中 green。特定方法 `testGetAllReturnsDefensiveSnapshot`/`testGetForwardMapValuesAreImmutableSnapshot`/`testNoFalsePositiveOnPrefixedPackageSegment` 经 surefire XML report 确认存在且通过。

## 2. 四族门禁 real-violation 命中（修复前 → 修复后）

| Family | 修复前（I1 baseline） | 修复后（本次全范围） | real-violation | 结论 |
|--------|----------------------|---------------|----------------|------|
| query-limit (INV-04) | 33 | **0** | 0 | **超额完成**：I4 修了 19 条真违规 + 给 14 条「已接受有界」补了 `setLimit(1)`（防御性），故全族归零 |
| entity-field-min (INV-01) | 24 | **12** | 0 | 12 条真违规已投影修复；剩余 12 == red list §2「已接受全实体」（DTO 映射/CLOB 必要） |
| delete-contract (INV-02) | 0 | **0** | 0 | 退化契约保持（物理删除） |
| idempotency (INV-03) | 2 red-list 锁 | **0** | 0 | `indexDirectory`/`indexFile` 已迁移入 `IDEMPOTENCE_TABLE`；`KNOWN_NON_IDEMPOTENT` 为空 |

**Phase 3-9 未引入新违规**：全范围复核确认 query-limit 0、entity-field-min 12（均为既有 red list §2）、delete-contract 0，与 I4 Phase 1-2 收口时的命中集合一致。Phase 3-9 新增方法（`removeStaleSymbolDocsForFile`/`deleteRelationalBySymbolIds`/`warnIfCapped`/`warnIfCacheTruncated`/`getSymbolById`/`pathSegmentMatch`/`packagePrefix`）未出现在任何门禁命中中。

### 2.1 query-limit 超额完成证据（非扫描器弱化）

- 扫描器源码 `ai-dev/tools/check-nop-code-invariants.mjs` 在 I4 commit（`1bddf8d50`）中**未被修改**（`git show 1bddf8d50 -- ai-dev/tools/check-nop-code-invariants.mjs` 输出为空）。
- `--self-test` 三族 canary 全 PASS。
- 抽查 `OrmFingerprintStore.findByIndexAndPath`（原 red list §1 :148「已接受有界」）：live code 现含 `query.setLimit(1)`，故扫描器正确判定 bounded 不再报告 —— 是**真实修复（防御性 setLimit）**，非扫描器放宽或结构性躲扫。

### 2.2 entity-field-min 剩余 12 条对齐 red list §2「已接受全实体」

剩余 12 命中均为 DTO 方法引用映射（扫描器无法跨方法体计 getter，故少计），live 实际读 5+ 字段或 CLOB，属合理已接受。全范围 `--list` 命中分布：`CodeIndexService.java:1704`（listFlows DTO）、`CodeQueryService.java:155/166/178/314/650/667/673/755/877`（多 DTO 映射点）、`CodeSearchService.java:131/154`。

### 2.3 幂等表状态（live 核对 `TestNopCodeIndexIdempotencyInvariant.java`）

- `IDEMPOTENCE_TABLE` = {`triggerIncrementalIndex`, `batchSaveFileRecords`, `indexDirectory`, `indexFile`}（4 方法，:85-90）。
- `KNOWN_NON_IDEMPOTENT` = `{}`（空，:102-103）—— red-list 锁迁移完成。
- 新增 `verifyIndexDirectoryIdempotent`/`verifyIndexFileIdempotent` verify 分支（重试两次后 file/symbol 计数稳定）。

## 3. 棘轮 baseline 复核（per-family，3 条显式命令，完整路径）

| 命令 | 退出码 | 结果 |
|------|--------|------|
| `--family query-limit --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-query-limit.json` | **0** | 0 NEW（baseline 空 = 0 违规存在，正确） |
| `--family entity-field-min --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-entity-field-min.json` | **0** | 0 NEW（baseline 匹配，12 条全 KNOWN） |
| `--family delete-contract --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-delete-contract.json` | **0** | 0 NEW |

> **路径要求**：必须用完整路径 `ai-dev/audits/nop-code-invariants/baselines/baseline-*.json`（前序 record 使用了相对路径 `baselines/...`，从项目根目录执行时工具 `loadBaseline` 对不存在路径静默 fall back 到空 Set，会制造假阳性）。本次全范围复核一律使用完整路径，3 条命令各自退出码 0。

### 3.1 entity-field-min baseline refresh 说明（I4 遗漏，前序 I5 补齐，本次保持）

- **历史**：I4 commit（`1bddf8d50`）未执行 `--update-baseline`，行号漂移导致 ratchet 模式误报 `[NEW]`。前序 I5（`2026-08-13-1059-6`）执行 `--update-baseline` 机械刷新并审计 diff（仅 `generatedAt` 时间戳 + `CodeIndexService.java:1663→1676` 行号漂移，签名逐字不变，无语义弱化）。
- **本次全范围复核**：3 条 baseline per-family 均 0 NEW，行号已匹配，**无需再次 refresh**。

## 4. Anti-Hollow 扫描 + 调用链连通性追踪

| 命令 | 退出码 | 结果 |
|------|--------|------|
| `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` | **0** | Critical 0 / High 0 / Medium 0 / Low 0 |

### 4.1 Phase 3-9 特定调用链连通性（人工追踪确认，6 条路径 ≥4 要求）

1. **Phase 3 搜索同步（WP-7）**：`saveFileResultInSession`（`CodeIndexService.java:1149` 调用）→ `removeStaleSymbolDocsForFile`（`:1593` 定义）。先 `findSymbolIdsByFileId` 查旧 symbol IDs → 非空则 `searchEngine.removeDocs(topic, oldSymbolIds)`；`catch(Exception)` 仅 WARN 日志（defense-in-depth，主路径 addDoc 仍执行）。`searchEngine==null` 是可选依赖的 null-guard，非静默跳过。**真实 defense-in-depth**，由 `TestIncrementalSearchSync` 验证。

2. **Phase 4 跨文件孤儿清理（WP-4）**：`deleteFileRecords`（`:1508` 定义，从 :311/:334/:741/:742 调用）→ `:1543 deleteRelationalBySymbolIds(NopCodeUsage.class,"symbolId",symbolIds)` 显式删跨文件孤儿（AR-149/150 service-layer 降级）。`deleteRelationalBySymbolIds`（`:1605`）为 `while+setLimit(DELETE_BATCH_SIZE)+break-when-empty+flushSession` 真实耗尽语义。`batchDeleteFileRecords`（`:1946`）包 `withIndexLock→runInTransaction(REQUIRED)→runInSession` 真实事务/会话包裹。**真实跨文件清理**，由 `TestDeletePathIntegrity` 验证。

3. **Phase 5 缓存不可变（WP-5）**：`SymbolTable.getAll()`（`SymbolTable.java:45`）返回 `new ArrayList<>(byId.values())` synchronized 防御快照（AR-155/158）；`CallGraph.getForwardMap()`（`CallGraph.java:56`）返回 `Collections.unmodifiableMap(copy)` 且每值 `Collections.unmodifiableList(new ArrayList<>(...))`（AR-148）。**真实不可变快照**，由 `TestSymbolTable.testGetAllReturnsDefensiveSnapshot` + `TestCallGraphImmutability.testGetForwardMapValuesAreImmutableSnapshot` 验证。

4. **Phase 6 截断可观测（WP-6）**：`warnIfCapped`（`CodeQueryService.java:52` 定义）在 9 个有界查询点（:142/179/291/315/366/756/834/843/863）检查 `isCapped` 并 WARN；`warnIfCacheTruncated`（`CodeGraphService.java:71` 定义）在 CodeGraphService 入口（:87/98）消费 `symbolTable.isTruncated()`/`callGraph.isTruncated()`。**真实可观测**，由 `TestTruncationObservability` 验证。

5. **Phase 7 数据一致性（WP-8）**：`getSymbolById`（`CodeQueryService.java:444`）双过滤 `eq("indexId")+eq("id")+setLimit(1)`（AR-41 跨 index 隔离）；`ChangeAnalyzer.pathSegmentMatch`（`ChangeAnalyzer.java:274`）双侧边界 `startOk(idx==0||prev=='/') && endOk(end==len||next=='.'/'/')`（AR-10/40/51 防 `mycom/example/User.java` 假匹配 `com/example/User`）。**真实语义修复**，由 `TestGetSymbolByIdIsolation` + `TestChangeAnalyzerPathMatching.testNoFalsePositiveOnPrefixedPackageSegment` 验证。

6. **Phase 8 error-handling（WP-9）**：`packagePrefix`（`CodeQueryService.java:63`）`endsWith(".")?name:name+"."` 用于 :484/:518 `startsWith("qualifiedName",packagePrefix(...))` 边界（AR-160/162/165 防 `com.example` 跨包匹配 `com.exampleFoo`）。**真实 error-handling 修复**，由 `TestPackageFilterBoundary` 验证。

### 4.2 Phase 1-2 调用链连通性（前序 I5 已追踪，本次复核保持）

1. **幂等路径（I4 Phase 2）**：`indexDirectory`/`indexFile` → `persistSingleFileInSession`（`:1085`）→ `saveReplacingExisting`（`:1541`，14 处调用点）。真实 query-first upsert，重试安全由 `TestNopCodeIndexIdempotencyInvariant` 7 tests 验证。
2. **OOM 投影路径（I4 Phase 1 WP-2）**：`OrmFingerprintStore.loadFingerprints` → `selectFieldsByQuery` 投影 + `while+offset+setLimit(BATCH_SIZE)+break` 分页耗尽。
3. **OOM 分页删除路径（I4 Phase 1 WP-1）**：`OrmFingerprintStore.deleteByIndex` → `while + setLimit(BATCH_SIZE) + deleteByQuery + break-when-deleted==0` 耗尽语义。
4. **搜索过滤路径（I4 Phase 1）**：`CodeSearchService.filterByLanguage` → `selectFieldsByQuery` 投影 filePath + 分页耗尽。

无空方法体 / 无 `continue` 静默跳过 / 无吞异常作正常实现。

### 4.3 接线验证（门禁扫描器确实扫描 I4 Phase 3-9 修改过的文件）

`--list` 输出中 `CodeIndexService`/`CodeQueryService`/`CodeSearchService` 的命中状态正确反映 I4 修复后状态（query-limit 0 命中、entity-field-min 12 命中均为上述文件）；`CodeGraphService`/`ChangeAnalyzer`/`SymbolTable`/`CallGraph` 因无违规未出现在命中列表（正确状态）。证明扫描器覆盖了 I4 Phase 3-9 改动面。

### 4.4 Phase 9 @Auth 阻塞状态确认（ask-first gate 合规）

- `grep -rn '@Auth' nop-code/nop-code-web/src/main/java`（排除 _gen）**计数=0** —— autonomous 未越权添加权限注解。
- I4 plan（`2026-08-13-1059-5` Phase 9 :260）已交付调查清单：8 个空 CRUD BizModel 已识别（NopCodeFlowBizModel / NopCodeAnnotationUsageBizModel / NopCodeFlowMembershipBizModel / NopCodeUsageBizModel / NopCodeDependencyBizModel / NopCodeCallBizModel / NopCodeSemanticEdgeBizModel / NopCodeInheritanceBizModel）。
- 补 @Auth 阻塞待人工确认（新增 @Auth 会使先前无保护的 action 要求权限 → 用户可见行为变更，须 ask-first）。

## 5. I4 修复统计（Phase 1-9 全部落地）

- **I4 已落地（Phase 1-9）**：
  - Phase 1-2：query-limit 19 真违规 + 14 已接受有界（防御性 setLimit）= 33 全族归零；entity-field-min 12 真违规投影修复（24→12）；idempotency 2 red-list 锁迁移（2→0）。
  - Phase 3：搜索同步 `removeStaleSymbolDocsForFile` defense-in-depth 落地 + `TestIncrementalSearchSync`。
  - Phase 4：跨文件孤儿清理 `deleteRelationalBySymbolIds(NopCodeUsage.class,...)` service-layer 降级落地 + `TestDeletePathIntegrity`（ORM cascadeDelete gated 阻塞待确认）。
  - Phase 5：`SymbolTable.getAll()` 防御快照 + `CallGraph.getForwardMap()` 不可变快照落地 + `TestSymbolTable`/`TestCallGraphImmutability`。
  - Phase 6：`warnIfCapped`（9 调用点）+ `warnIfCacheTruncated` 落地 + `TestTruncationObservability`。
  - Phase 7：`getSymbolById` 双过滤隔离 + `ChangeAnalyzer.pathSegmentMatch` 双侧边界修复 + `TestGetSymbolByIdIsolation`/`TestChangeAnalyzerPathMatching`。
  - Phase 8：`packagePrefix()` 包边界修复 + `TestPackageFilterBoundary`。
  - Phase 9：@Auth 调查清单交付（8 个空 BizModel 已识别），@Auth 实施阻塞待人工确认（ask-first gate）。
- **棘轮前进量**：query-limit 33→0；entity-field-min 24→12；idempotency red-list 锁 2→0；delete-contract 0→0。
- **新增 focused 测试（Phase 1-9 共 10 类）**：`TestQueryPaginationProjectionInvariant`（3）、`TestNopCodeIndexIdempotencyInvariant`（扩展至 7）、`TestIncrementalSearchSync`（1）、`TestDeletePathIntegrity`（1）、`TestSymbolTable`（6）、`TestCallGraphImmutability`（7）、`TestTruncationObservability`（2）、`TestGetSymbolByIdIsolation`（1）、`TestChangeAnalyzerPathMatching`（6）、`TestPackageFilterBoundary`（1）。

## 6. I4 Phase 3-9 落地结论（全范围，不再含 planned）

| I4 Phase | 工作包 | 落地结论 |
|----------|--------|---------|
| Phase 3 | WP-7 搜索引擎增量同步（addDoc/removeDocs 对称） | **fixed + test**：`removeStaleSymbolDocsForFile` 落地，`TestIncrementalSearchSync` 全绿 |
| Phase 4 | WP-4 删除路径完整性 | **fixed + test（service-layer）+ gated**：跨文件孤儿清理 `deleteRelationalBySymbolIds(NopCodeUsage.class,...)` 落地，`TestDeletePathIntegrity` 全绿；ORM cascadeDelete（AR-149/150）阻塞待人工确认（plan-first gate），correctness 已由 service 层保证 |
| Phase 5 | WP-5 缓存不可变性 | **fixed + test**：`SymbolTable.getAll()`/`CallGraph.getForwardMap()` 不可变快照落地，`TestSymbolTable`/`TestCallGraphImmutability` 全绿 |
| Phase 6 | WP-6 截断可观测性 | **fixed + test**：`warnIfCapped`/`warnIfCacheTruncated` 落地，`TestTruncationObservability` 全绿 |
| Phase 7 | WP-8 数据一致性语义 | **fixed + test**：`getSymbolById` 双过滤 + `pathSegmentMatch` 双侧边界落地，`TestGetSymbolByIdIsolation`/`TestChangeAnalyzerPathMatching` 全绿 |
| Phase 8 | WP-9 error-handling | **fixed + test**：`packagePrefix()` 包边界落地，`TestPackageFilterBoundary` 全绿 |
| Phase 9 | WP-10 安全/权限（@Auth） | **gated（阻塞待人工确认）**：8 个空 BizModel 调查清单已交付，@Auth 注解未 autonomously 添加（ask-first gate 合规）；service-layer 无降级需求（空 CRUD BizModel 当前行为正确） |

## 7. 结论

**I4 全部 Phase 1-9 全量独立验证通过**：

- 模块级全量测试 **383 tests 全绿**（0 failures / 0 errors），Phase 3-9 新增 8 个 focused test 类（25 tests）+ Phase 1-2 既有 test 在同一运行中 green。
- 四族门禁 **real-violation = 0**（query-limit 0 / entity-field-min 12 全为已接受全实体 / delete-contract 0 / idempotency `KNOWN_NON_IDEMPOTENT` = ∅）。
- 棘轮 baseline per-family **3 条命令各自退出码 0**（0 NEW）。
- Anti-Hollow 扫描 **0 high/critical**；Phase 3-9 特定调用链 **6 条已追踪确认连通**（≥4 要求），无空方法体/静默跳过/no-op。
- Phase 9 @Auth / Phase 4 ORM cascadeDelete 两项 **gated 阻塞待人工确认**（ask-first / plan-first），I4 已为两者落地 service-layer 降级路径 / 调查清单，I5 验证的是「已落地部分」全绿。

**本次验证覆盖完整 I4 scope（Phase 1-9），供 I6-revisit（`2026-08-13-1930-2`）做确定性稳态判定**。前序 I5（`2026-08-13-1059-6`）声明「Phase 3-9 不在覆盖内、Cycle 1 未达稳态」的记录已 stale，本记录为 Phase 1-9 全范围的全量独立验证结论。
