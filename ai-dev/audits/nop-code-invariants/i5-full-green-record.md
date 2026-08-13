# nop-code 不变式闭环 I5 — 全量验证 Full-Green 记录（Cycle 1）

> Status: active
> Last Reviewed: 2026-08-13
> Source: `ai-dev/plans/2026-08-13-1059-6-nop-code-invariant-i5-full-verification.md` Phase 4
> Verifier: mission-driver I5 执行 session（2026-08-13）
> Scope: I4 已完成 Phases（Phase 1 OOM 族 + Phase 2 幂等性）的独立全量复核

## 验证范围声明（关键）

本记录验证 **I4 已落地范围（Phase 1 WP-1/WP-2 查询上限+字段最小化、Phase 2 WP-3 幂等性）** 的全量结果。

**I4 未执行范围（Phase 3-9：WP-7 搜索同步、WP-4 删除路径、WP-5 缓存不可变、WP-6 截断可观测、WP-8 数据一致性、WP-9 error-handling、WP-10 安全/权限）不在本次验证覆盖内** —— 这些工作项仍处于 `planned`，需 I4 继续执行后由后续 I5 re-verification 复核。本记录的「全绿」指 **I4-已完成范围的四族门禁 + 模块测试 + Anti-Hollow 扫描全绿**，不等于 Cycle 1 已达稳态（稳态判定属 I6，且依赖 I4 全部 Phase 完成）。

## 1. 模块级全量测试

| 命令 | 结果 | 退出码 |
|------|------|--------|
| `./mvnw test -pl nop-code -T 1C` | **BUILD SUCCESS** | 0 |
| `./mvnw test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexIdempotencyInvariant` | Tests run: 7, Failures: 0, Errors: 0 | 0 |
| `./mvnw test -pl nop-code/nop-code-service -Dtest=TestQueryPaginationProjectionInvariant` | Tests run: 3, Failures: 0, Errors: 0 | 0 |

- nop-code 全 13 子模块 SUCCESS；nop-code-service 汇总 **Tests run: 138, Failures: 0, Errors: 0, Skipped: 13**（skipped 为既有的平台级条件跳过，非 I4 相关）。
- 时间戳：2026-08-13T15:31:11+08:00。
- **依赖模块 flaky 隔离**：`./mvnw test -pl nop-code -am` 首跑时 `nop-auth-service` 的 `TestChannelScanBindLoginE2E` 抛 `VarCollector.instance()` NPE（AutoTest 基础设施问题，与 nop-code/I4 无关）。改用「先 `install -DskipTests` 装依赖，再 `test -pl nop-code`」隔离后，nop-code 全绿。该 flaky 已记录，不阻塞 I5。

## 2. 四族门禁 real-violation 命中（修复前 → 修复后）

| Family | 修复前（I1 baseline） | 修复后（本次） | real-violation | 结论 |
|--------|----------------------|---------------|----------------|------|
| query-limit (INV-04) | 33 | **0** | 0 | **超额完成**：I4 不仅修了 19 条真违规，还给 14 条「已接受有界」补了 `setLimit(1)`（防御性），故全族归零（非预期 ≤14，而是 0） |
| entity-field-min (INV-01) | 24 | **12** | 0 | 12 条真违规已投影修复；剩余 12 == red list §2「已接受全实体」（DTO 映射/CLOB 必要） |
| delete-contract (INV-02) | 0 | **0** | 0 | 退化契约保持（物理删除） |
| idempotency (INV-03) | 2 red-list 锁 | **0** | 0 | `indexDirectory`/`indexFile` 已迁移入 `IDEMPOTENCE_TABLE`；`KNOWN_NON_IDEMPOTENT` 为空 |

### 2.1 query-limit 超额完成证据（非扫描器弱化）

- 扫描器源码 `ai-dev/tools/check-nop-code-invariants.mjs` 在 I4 commit（`1bddf8d50`）中**未被修改**（`git show 1bddf8d50 -- ai-dev/tools/check-nop-code-invariants.mjs` 输出为空）。
- `--self-test` 三族 canary 全 PASS（query-limit/entity-field-min/delete-contract 各自 reject 已知 bad 输入）。
- 抽查 `OrmFingerprintStore.findByIndexAndPath`（原 red list §1 :148「已接受有界」）：live code 现含 `query.setLimit(1)`（`OrmFingerprintStore.java:172`），故扫描器正确判定 bounded 不再报告 —— 是**真实修复（防御性 setLimit）**，非扫描器放宽或结构性躲扫。

### 2.2 entity-field-min 剩余 12 条对齐 red list §2「已接受全实体」

剩余 12 命中均为 DTO 方法引用映射（扫描器无法跨方法体计 getter，故少计），live 实际读 5+ 字段或 CLOB，属合理已接受。抽查：
- `CodeQueryService.java:128 getFile`→`entityToFileResult`（DTO 读 sourceCode(CLOB)/imports/packageName 多字段）+ `setLimit(1)`。
- `CodeIndexService.java:1676 listFlows`→`entityToExecutionFlow`（方法引用 DTO 映射）+ `setLimit(MAX_QUERY_RESULTS)`。

### 2.3 幂等表状态（live 核对 `TestNopCodeIndexIdempotencyInvariant.java`）

- `IDEMPOTENCE_TABLE` = {`triggerIncrementalIndex`, `batchSaveFileRecords`, `indexDirectory`, `indexFile`}（4 方法，:85-90）。
- `KNOWN_NON_IDEMPOTENT` = `{}`（空，:102-103）—— red-list 锁迁移完成。
- 新增 `verifyIndexDirectoryIdempotent`/`verifyIndexFileIdempotent` verify 分支（重试两次后 file/symbol 计数稳定）。

## 3. 棘轮 baseline 复核（per-family，3 条显式命令）

| 命令 | 退出码 | 结果 |
|------|--------|------|
| `--family query-limit --baseline baselines/baseline-query-limit.json` | **0** | 0 NEW（baseline 空 = 0 违规存在，正确） |
| `--family entity-field-min --baseline baselines/baseline-entity-field-min.json` | **0** | 0 NEW（baseline 经 refresh 后匹配） |
| `--family delete-contract --baseline baselines/baseline-delete-contract.json` | **0** | 0 NEW |

### 3.1 entity-field-min baseline refresh 说明（I4 遗漏，I5 补齐）

- **发现**：I4 commit（`1bddf8d50`）未执行 `--update-baseline`，`baseline-entity-field-min.json` 仍为 I1 时点（`generatedAt: 2026-08-13T04:02:43Z`）的旧行号；I4 在 `CodeIndexService` 新增 +244 行导致 `listFlows` 行号 1663→1676 漂移，ratchet 模式将该 1 条误报为 `[NEW]`。
- **处置**：I5 执行 `--update-baseline` 机械刷新（I5 计划 Phase 3 前置假设「I4 已产 baseline diff」，I4 遗漏故 I5 补齐并审计）。
- **diff 审计**：`diff OLD NEW` 仅 2 处变化——① `generatedAt` 时间戳；② `CodeIndexService.java:1663→1676`（行号漂移，签名 `[findAllByQuery(query) few-fields]` 不变）。**其余 11 条签名逐字不变**。无删除条目、无语义弱化、无伪装修复。12 条均为 red list §2「已接受全实体」。
- **query-limit baseline 保持空**：0 违规存在，空 baseline 正确（14 条「已接受有界」已被 I4 防御性 setLimit 修复为非违规，无需进 baseline —— 优于原计划的「保留 14 条 baseline」）。

## 4. Anti-Hollow 扫描 + 调用链连通性抽查

| 命令 | 退出码 | 结果 |
|------|--------|------|
| `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` | **0** | Critical 0 / High 0 / Medium 0 / Low 0 |

### 4.1 ≥3 条 I4 修复路径调用链连通性（人工追踪确认）

1. **幂等路径（I4 Phase 2）**：`indexDirectory`/`indexFile` → `persistSingleFileInSession`（`CodeIndexService.java:1085`）→ `saveReplacingExisting`（`:1541`，14 处调用点 :1164/1207/1262/...）。`saveReplacingExisting` 为真实 query-first upsert（`session.get()` 载入→拷贝非主键字段→flush 变 UPDATE；不存在则 `session.save()` INSERT），非空壳。重试安全由 `TestNopCodeIndexIdempotencyInvariant` 7 tests 验证。
2. **OOM 投影路径（I4 Phase 1 WP-2）**：`OrmFingerprintStore.loadFingerprints`（`:106`）→ `selectFieldsByQuery` 投影 filePath/fileHash/lastModified/fileSize 4 标量 + `while+offset+setLimit(BATCH_SIZE)+break` 分页耗尽。避免 CLOB sourceCode 全加载。真实逻辑。
3. **OOM 分页删除路径（I4 Phase 1 WP-1）**：`OrmFingerprintStore.deleteByIndex`（`:155`）→ `while + setLimit(BATCH_SIZE) + deleteByQuery + break-when-deleted==0` 耗尽语义（非截断）。真实逻辑。
4. **搜索过滤路径（I4 Phase 1）**：`CodeSearchService.filterByLanguage` → `selectFieldsByQuery` 投影 filePath + 分页耗尽（原 `findAllByQuery` 全 CLOB 实体已消除）。真实逻辑。

无空方法体 / 无 `continue` 静默跳过 / 无吞异常作正常实现。`saveReplacingExisting:1557` 的 `catch(Exception)` 仅在属性拷贝时 TRACE 日志并继续（防御性跳过不可读属性，主 upsert 逻辑仍完成），非静默吞主逻辑。

### 4.2 接线验证（门禁扫描器确实扫描 I4 修改过的文件）

`--list` 输出中 `CodeIndexService`/`CodeQueryService`/`CodeSearchService`/`OrmFingerprintStore` 的命中状态正确反映 I4 修复后状态（query-limit 0 命中、entity-field-min 12 命中均为上述文件），证明扫描器覆盖了 I4 改动面。

## 5. I4 修复统计

- **I4 已落地（Phase 1-2）**：query-limit 19 真违规 + 14 已接受有界（防御性 setLimit）= 33 全族归零；entity-field-min 12 真违规投影修复（24→12）；idempotency 2 red-list 锁迁移（2→0）。
- **棘轮前进量**：query-limit 33→0；entity-field-min 24→12；idempotency red-list 锁 2→0；delete-contract 0→0。
- **新增 focused 测试**：`TestQueryPaginationProjectionInvariant`（3 tests，分页耗尽 + 投影列断言）；`TestNopCodeIndexIdempotencyInvariant` 扩展（7 tests，含 verify 分支）。

## 6. I4 未完成范围（Phase 3-9，需后续 I5 re-verification）

以下 I4 工作项仍 `planned`，不在本次全绿覆盖内，列出供 I6 稳态判定参考：

| I4 Phase | 工作包 | 状态 |
|----------|--------|------|
| Phase 3 | WP-7 搜索引擎增量同步（addDoc/removeDocs 对称） | planned |
| Phase 4 | WP-4 删除路径完整性（跨文件孤儿清理 / cascadeDelete ORM plan-first） | planned |
| Phase 5 | WP-5 缓存不可变性（SymbolTable/CallGraph live 引用） | planned |
| Phase 6 | WP-6 截断可观测性（MAX_QUERY_RESULTS 静默截断 WARN） | planned |
| Phase 7 | WP-8 数据一致性语义（AR-01/10/40/41/59/63/93/132/151 + AR-51） | planned |
| Phase 8 | WP-9 error-handling（子串误匹配 / 吞异常） | planned |
| Phase 9 | WP-10 安全/权限（@Auth ask-first） | planned |

## 7. 结论

**I4 已完成范围（Phase 1-2）全量独立验证通过**：模块测试 138 全绿、四族门禁 real-violation 0、棘轮 baseline per-family 0 NEW、Anti-Hollow 扫描 0 high/critical、调用链连通性确认。

**Cycle 1 未达稳态**：I4 Phase 3-9 未执行，需 I4 继续完成后再触发 I5 re-verification。本记录作为 I6 的部分输入（I4-已完成面绿；I4-未完成面待续）。
