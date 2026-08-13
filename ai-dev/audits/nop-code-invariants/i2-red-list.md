# nop-code 不变式闭环 I2 — Red List（门禁命中逐条分类）

> Status: active
> Last Reviewed: 2026-08-13
> Source: I2 计划 `ai-dev/plans/2026-08-13-0806-3-nop-code-invariant-i2-invariant-driven-audit.md` Phase 1
> 输入: I1 门禁 `ai-dev/tools/check-nop-code-invariants.mjs`（strict 模式 `--list` 原始命中）+ `gate-baseline-I1.md` + JUnit `TestNopCodeIndexIdempotencyInvariant`
> 后继: I3 裁决（`ai-dev/plans/2026-08-13-0806-4-nop-code-invariant-i3-finding-adjudication.md`）消费本 red list

## 目的

把 I1 四族门禁的全部命中逐条分类为 `真违规`（待修，I4 靶点）/ `已接受有界`（附有界证据）/ `已接受全实体`（附全实体必要理由）/ `门禁误报`（附误报理由），每条可追溯到 `文件:行` + AR-ID + 归属不变式。本文件是 I3 裁决的确定性输入。

## 复跑命令与原始命中

```bash
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list     # 33
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min --list # 24
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract --list  # 0
```

- `query-limit`：33 命中（退出码 1）。
- `entity-field-min`：24 命中（退出码 1）。
- `delete-contract`：0 命中（退出码 0）。
- `idempotency`（JUnit）：2 green 锁 + 2 red-list 可执行锁（见 §5）。
- **UNDETERMINED 调用点**：当前 baseline 下 `query-limit` 扫描器报告 0 个 `[UNDETERMINED]` 调用点（全部查询变量可解析；扫描器对内联/复杂表达式会显式报告 `[UNDETERMINED]` 而非静默 `continue`，见 `check-nop-code-invariants.mjs:29,106,367`）。

---

## 分类计数汇总表

> 计数规则：同一 `文件:行` 在 query-limit 与 entity-field-min 两个 family 各计一次（它们是两条独立不变式）。分类按 family 内的命中条目计数。

### query-limit（INV-04，33 条）

| 分类 | 计数 | 占比 |
|------|------|------|
| 真违规（无 setLimit，结果集不可证明有界） | 19 | 58% |
| 已接受有界（等值过滤单条 / IN 主键列结果 ≤ 输入基数） | 14 | 42% |
| 门禁误报 | 0 | 0% |
| **合计** | **33** | 100% |

### entity-field-min（INV-01，24 条）

| 分类 | 计数 | 占比 |
|------|------|------|
| 真违规（≤3 字段投影缺失 / CLOB 实体全加载 / 仅存在性检查） | 12 | 50% |
| 已接受全实体（DTO 多字段映射 / 删除语义必要 / 需 CLOB） | 12 | 50% |
| 门禁误报 | 0 | 0% |
| **合计** | **24** | 100% |

### delete-contract（INV-02，0 条）

| 分类 | 计数 |
|------|------|
| 真违规 | 0 |
| **退化理由** | live ORM 11 实体均无 `useLogicalDelete`；service 删除路径（`deleteIndex`→`deleteEntitiesPaged`→`batchDeleteEntities`、`deleteFileRecords`、`deleteRelationalBySymbolIds`、`deleteEntitiesByFilter`）统一物理删除。`delete-contract` family 在 strict 模式下退出码 0。若未来某实体引入 `useLogicalDelete` 或删除方法改用逻辑删除 setter，门禁退出非零（强制裁定）。 |

### idempotency（INV-03，2 red-list 锁）

| 方法 | 分类 |
|------|------|
| `indexDirectory` | 已锁定的真违规（I4 靶点）— 重试抛 duplicate-key 23505 |
| `indexFile` | 已锁定的真违规（I4 靶点）— 重试抛 duplicate-key 23505 |

> 总计：query-limit 33 + entity-field-min 24 + delete-contract 0 + idempotency 2 = **59 条命中**（其中静态门禁 57 + JUnit red-list 锁 2）。

---

## LIMIT 豁免裁定（落地）

**裁定来源**：`gate-baseline-I1.md`「LIMIT 豁免裁定」。投影查询（`selectFieldsByQuery`）**不豁免** `setLimit`——投影仍把全部结果行物化进内存。**唯一豁免 = 可证明结果集有界**：

1. **等值过滤单条**（eq on 主键 / 唯一标识列，结果 ≤ 1）—— 本次落地于：`OrmFingerprintStore:148`（indexId+filePath）、`CodeIndexService:1471`（indexId+id 主键）、`CodeIndexService:1788`（indexId+filePath）、`CodeQueryService:126/136/407/509/673/792`（indexId+filePath 或 indexId+qualifiedName）。
2. **IN 主键/唯一列，结果 ≤ 输入基数**——本次落地于：`CodeQueryService:590`（in qualifiedName[唯一]）、`704/714/780/853`（in id[主键]）。证据：`SELECT ... WHERE id IN (list)` 至多返回 |list| 行（id 唯一）。

**不豁免**（仍为真违规）：IN 在**非唯一外键列**上（如 `in(parentId,...)`、`in(annotatedSymbolId,...)`、`in(sourceSymbolId,...)`），结果不受输入基数约束（一个 parent 可有任意多 children）→ 不可证明有界 → 真违规。本次落地于：`CodeIndexService:1521`、`CodeQueryService:606/611`。

---

## §1 query-limit 逐条分类（INV-04，33 条）

> 路径前缀：`nop-code/nop-code-service/src/main/java/io/nop/code/service/`

### OrmFingerprintStore.java（4）

| 行 | 方法(var) | 分类 | 证据 / 有界理由 | AR / INV |
|----|-----------|------|----------------|----------|
| 84 | `loadFileIdMapByIndex` selectFieldsByQuery(query) | **真违规** | 投影 id+filePath，`eq(indexId)`，无 setLimit；投影不豁免 LIMIT，全索引文件路径物化进内存 | INV-04 |
| 102 | `loadFingerprints` findAllByQuery(query) | **真违规** | 全 `NopCodeFile`（含 CLOB sourceCode）加载，`eq(indexId)`，无 setLimit；仅读 filePath/fileHash/lastModified/fileSize 4 标量字段，应投影 | INV-04（=AR-135/154） |
| 137 | `deleteByIndex` findAllByQuery(query) | **真违规** | 全实体加载后逐条 `deleteEntity`，`eq(indexId)`，无 setLimit；应改用分页删除（参照 `deleteEntitiesPaged` batch 500） | INV-04 |
| 148 | `findByIndexAndPath` findAllByQuery(query) | **已接受有界** | `eq(indexId)+eq(filePath)`，(indexId,filePath) 唯一标识→结果 ≤ 1（取 `results.get(0)`） | INV-04 |

### impl/CodeCacheManager.java（1）

| 行 | 方法(var) | 分类 | 证据 | AR / INV |
|----|-----------|------|------|----------|
| 247 | `rebuildDependencies` findAllByQuery(q) | **真违规** | 全 `NopCodeDependency`，`eq(indexId)`，无 setLimit；**对比**：同类 `rebuildSymbolTable`/`rebuildCallGraph`（:191/:221）均用 `while+offset+setLimit(BATCH_SIZE=5000)` 分页，唯独 dependencies 未分页 | INV-04 |

### impl/CodeIndexService.java（11）

| 行 | 方法(var) | 分类 | 证据 / 有界理由 | AR / INV |
|----|-----------|------|----------------|----------|
| 505 | `getIndexStats` selectFieldsByQuery(kindQuery) | **真违规** | 投影 kind（按 kind 计数），`eq(indexId)`，无 setLimit；AR-77 的 count 部分已修为 `countByQuery`（:495/:500），但 kind-breakdown 投影仍无上限 | INV-04 |
| 1404 | `getProjectFilePaths` selectFieldsByQuery(q) | **真违规** | 投影 filePath，`eq(indexId)`，无 setLimit；投影已修但无 LIMIT——**AR-177 明确裁定为真违规** | INV-04 / **AR-177** |
| 1471 | `deleteFileRecords` findAllByQuery(q) | **已接受有界** | `eq(indexId)+eq(id)`（id 为主键），结果 ≤ 1；用于 upsert 前定位单条 | INV-04 |
| 1511 | `findSymbolIdsByFileId` findAllByQuery(q) | **真违规** | `eq(fileId)`，单文件符号集（非单条），无 setLimit；delete-helper，应加分页或 setLimit | INV-04 |
| 1521 | `deleteRelationalBySymbolIds` findAllByQuery(q) | **真违规** | `in(field, symbolIds)`，field 为 annotatedSymbolId/subTypeId/sourceSymbolId/targetSymbolId/symbolId 等**非唯一外键**；结果不受 |symbolIds| 约束（一个 symbol 可有任意多 usage/edge），无 setLimit | INV-04 |
| 1531 | `deleteEntitiesByFilter` findAllByQuery(q) | **真违规** | `eq(field, value)`（fileId/sourceFilePath 等非唯一列），单文件/路径范围（非单条），无 setLimit；delete-helper，应改分页删除 | INV-04 |
| 1602 | `listFlows` findAllByQuery(query) | **真违规** | `eq(indexId)`，全 flows，无 setLimit；`MAX_FLOWS_PER_INDEX=5000` 仅在写侧 `persistFlows` 生效，读侧无上限 | INV-04 |
| 1624 | `getFlow` findAllByQuery(membershipQuery) | **真违规** | `eq(flowId)`，单 flow 的 memberships（长 flow 可很多），无 setLimit；低风险但无上限 | INV-04 |
| 1788 | (upsert 前定位) findAllByQuery(query) | **已接受有界** | `eq(indexId)+eq(filePath)`，(indexId,filePath) 唯一→结果 ≤ 1 | INV-04 |
| 1818 | `batchLoadFileRecords` findAllByQuery(query) | **真违规** | 全 `NopCodeFile`（含 CLOB），`eq(indexId)`，无 setLimit；仅读 4 标量字段，应投影（镜像 OrmFingerprintStore:102） | INV-04（=AR-135） |
| 1882 | `filterByLanguage` findAllByQuery(fq) | **真违规** | `eq(indexId)+eq(language)`，单语言文件集（非单条），无 setLimit | INV-04 |

### impl/CodeQueryService.java（15）

| 行 | 方法(var) | 分类 | 证据 / 有界理由 | AR / INV |
|----|-----------|------|----------------|----------|
| 126 | `getFile` findAllByQuery(query) | **已接受有界** | `eq(indexId)+eq(filePath)`→≤1（取 `get(0)`） | INV-04 |
| 136 | `getFileSourceCode` findAllByQuery(query) | **已接受有界** | `eq(indexId)+eq(filePath)`→≤1（取 `get(0).getSourceCode()`） | INV-04 |
| 147 | `getFileSymbols` findAllByQuery(query) | **真违规** | `eq(indexId)+eq(fileId)`，单文件符号集（非单条），无 setLimit | INV-04 |
| 407 | `findSymbolByQualifiedName` findAllByQuery(query) | **已接受有界** | `eq(indexId)+eq(qualifiedName)`→≤1（取 `get(0)`） | INV-04 |
| 509 | `showSymbolSource` findAllByQuery(query) | **已接受有界** | `eq(indexId)+eq(qualifiedName)`→≤1（取 `get(0)`） | INV-04 |
| 562 | `getTypeOutline` findAllByQuery(childQuery) | **真违规** | `eq(indexId)+or(eq(parentId),eq(declaringSymbolId))`，一个符号的子成员（非单条，可数百），无 setLimit | INV-04 |
| 590 | `batchGetTypeOutlines` findAllByQuery(qnQuery) | **已接受有界** | `eq(indexId)+in(qualifiedName, list)`，qualifiedName 唯一→结果 ≤ |list|（输入基数约束） | INV-04 |
| 606 | `batchGetTypeOutlines` findAllByQuery(childQuery) | **真违规** | `eq(indexId)+in(parentId, typeIds)`，parentId **非唯一**（一个 type 任意多 children），结果不受 |typeIds| 约束，无 setLimit | INV-04 |
| 611 | `batchGetTypeOutlines` findAllByQuery(memberQuery) | **真违规** | `eq(indexId)+in(declaringSymbolId, typeIds)`，declaringSymbolId **非唯一**，同 :606，无 setLimit | INV-04 |
| 673 | `findReferencedBy` findAllByQuery(symbolQuery) | **已接受有界** | `eq(indexId)+eq(qualifiedName)`→≤1 | INV-04 |
| 704 | `findReferencedBy` findAllByQuery(fileQuery) | **已接受有界** | `in(id, fileIds)`，id 主键→结果 ≤ |fileIds| | INV-04 |
| 714 | `findReferencedBy` findAllByQuery(encQuery) | **已接受有界** | `in(id, enclosingSymbolIds)`，id 主键→结果 ≤ |enclosingSymbolIds| | INV-04 |
| 780 | `findByAnnotation` findAllByQuery(symQuery) | **已接受有界** | `eq(indexId)+in(id, symbolIds)`，id 主键→结果 ≤ |symbolIds| | INV-04 |
| 792 | `findImplementations` findAllByQuery(symQuery) | **已接受有界** | `eq(indexId)+eq(qualifiedName)`→≤1 | INV-04 |
| 853 | `findImplementations` findAllByQuery(allSymQuery) | **已接受有界** | `eq(indexId)+in(id, resultIds)`，id 主键→结果 ≤ |resultIds| | INV-04 |

### impl/CodeSearchService.java（2）

| 行 | 方法(var) | 分类 | 证据 | AR / INV |
|----|-----------|------|------|----------|
| 202 | `buildFilePathCache` selectFieldsByQuery(fq) | **真违规** | 投影 id+filePath，`eq(indexId)`，无 setLimit；投影已修但无 LIMIT——**AR-168 明确裁定为真违规** | INV-04 / **AR-168** |
| 329 | `filterByLanguage` findAllByQuery(fq) | **真违规** | `eq(indexId)+eq(language)`，单语言文件集（非单条），无 setLimit | INV-04 |

---

## §2 entity-field-min 逐条分类（INV-01，24 条）

> 子模式：A=`.size()`→countByQuery；B=≤3 getter→投影。**门禁精度备注**：扫描器对 `this::entityToXxx`/`CodeSymbolConverter::toCodeSymbol` 等方法引用无法跨方法体计 getter，故 DTO 映射类命中普遍**少计** getter（实读 5+ 字段）；标 `★多计` 的为扫描器把多循环/多用途的字段访问并入即时循环导致计数偏差。下表分类依据 **live code 实际字段访问**（非扫描器字面计数）。

### OrmFingerprintStore.java（1）

| 行 | 扫描器声称 | 分类 | live 实际访问 / 证据 | AR / INV |
|----|-----------|------|---------------------|----------|
| 102 | .size()→countByQuery | **真违规**（扫描器理由误标） | live 迭代读 filePath/fileHash/lastModified/fileSize 4 字段，`entities.size()` 仅用于容量；真实缺陷=全 `NopCodeFile`(CLOB) 加载仅取 4 标量字段，应投影。扫描器「.size()→countByQuery」建议错误（数据被消费非仅计数） | INV-01（=AR-135） |

### impl/CodeIndexService.java（5）

| 行 | 扫描器声称 | 分类 | live 实际访问 / 证据 | AR / INV |
|----|-----------|------|---------------------|----------|
| 899 | .size()→countByQuery | **真违规**（扫描器理由误标） | `loadExistingEdgeKeys`：迭代读 `call.getCallerId()`+`getCalleeId()` 2 字段构建去重 key；无 CLOB；`while+setLimit(BATCH_SIZE=1000)` 全分页（**AR-180「截断于 MAX_QUERY_RESULTS」前提过时**——本方法全量分页非截断）。真实缺陷=2 字段应投影；低优先级（无 CLOB、分页、build 路径） | INV-01 |
| 1602 | 2 getter→投影 | **已接受全实体**（扫描器少计） | `listFlows`→`entityToExecutionFlow`（方法引用），DTO 映射读 name/qn/entryPoint/depth/score/status 等多字段；扫描器只看见即时循环 | INV-01 |
| 1698 | 3 getter→投影 | **已接受全实体**（扫描器多计） | `persistFlows` 删除循环：`deleteQuery.setLimit(DELETE_BATCH_SIZE)` 分页，加载结果随后 `batchDeleteEntities`（删除语义必要全实体）；仅读 `getId()` 做 cascade 子删除 | INV-01（delete-path） |
| 1818 | .size()→countByQuery | **真违规**（扫描器理由误标） | `batchLoadFileRecords`：迭代读 4 标量字段，`entities.size()` 仅容量；真实缺陷=全 `NopCodeFile`(CLOB) 加载仅取 4 字段，应投影（镜像 OrmFingerprintStore:102） | INV-01（=AR-135） |
| 1882 | 1 getter→投影 | **真违规** | `filterByLanguage`：仅读 `getFilePath()` 1 字段，全 `NopCodeFile`(CLOB) 加载，应投影 filePath | INV-01 |

### impl/CodeQueryService.java（15）

| 行 | 扫描器声称 | 分类 | live 实际访问 / 证据 | AR / INV |
|----|-----------|------|---------------------|----------|
| 126 | 1 getter | **已接受全实体**（少计） | `getFile`→`entityToFileResult`，CodeFileAnalysisResult DTO 需 sourceCode(CLOB)/imports/packageName 等多字段 | INV-01 |
| 136 | 1 getter | **已接受全实体** | `getFileSourceCode` 读 `getSourceCode()`(CLOB)，全实体必要 | INV-01 |
| 147 | 1 getter | **已接受全实体**（少计） | `getFileSymbols`→`CodeSymbolConverter::toCodeSymbol`，完整 CodeSymbol DTO | INV-01 |
| 253 | 1 getter | **真违规**（少计） | `getModuleDigest` files：跨多循环读 getId/getFilePath/getPackageName 3 字段，全 `NopCodeFile`(CLOB)，setLimit(10000) 仍加载 1 万 CLOB 行；应投影 3 字段 | INV-01 |
| 271 | 2 getter | **已接受全实体**（少计） | `getModuleDigest` symbols：跨循环读 fileId/name/qn/kind/accessModifier 5+ 字段，`NopCodeSymbol` 无 CLOB | INV-01 |
| 320 | 2 getter | **真违规** | `getPublicSurface` files：读 getId+getFilePath 2 字段，全 `NopCodeFile`(CLOB)，setLimit(10000)；应投影 | INV-01 |
| 590 | 2 getter | **已接受全实体**（少计） | `batchGetTypeOutlines` qnQuery：跨用途读 qn/id + 后续 name/kind/accessModifier 5 字段 | INV-01 |
| 606 | 2 getter | **已接受全实体**（少计） | 同上 childQuery：读 parentId + 后续 5 字段 | INV-01 |
| 611 | 2 getter | **已接受全实体**（少计） | 同上 memberQuery：读 declaringSymbolId + 后续 5 字段 | INV-01 |
| 673 | 1 getter | **真违规** | `findReferencedBy` symbolQuery：仅读 getId 收集 symbolIds；应投影 id（但 eq(QN) 有界≤1，影响极小） | INV-01 |
| 689 | 2 getter | **已接受全实体**（少计） | `findReferencedBy` usage：跨用途读 fileId/enclosingSymbolId + kind/line/column/context 6 字段 | INV-01 |
| 704 | 1 getter | **真违规** | `findReferencedBy` fileQuery：仅读 getId+getFilePath 2 字段，全 `NopCodeFile`(CLOB)；应投影 | INV-01 |
| 756 | 1 getter | **真违规** | `findByAnnotation` annotQuery：仅读 getAnnotatedSymbolId，setLimit(10000)；应投影 | INV-01 |
| 763 | 1 getter | **真违规** | `findByAnnotation` fuzzyQuery：同 :756 | INV-01 |
| 792 | 3 getter | **真违规** | `findImplementations` symQuery：结果**仅用于 isEmpty() 存在性检查**（targets 后续未被消费），应改 `countByQuery` 或 exists | INV-01 |

### impl/CodeSearchService.java（3）

| 行 | 扫描器声称 | 分类 | live 实际访问 / 证据 | AR / INV |
|----|-----------|------|---------------------|----------|
| 129 | 3 getter | **已接受全实体**（少计） | `searchBySymbolName`→`toSearchResult`(方法引用) + 评分读 name/qn；setLimit(limit*2) | INV-01 |
| 152 | 3 getter | **已接受全实体**（少计） | `searchFullText`→`toSearchResult` + 评分读 signature/documentation；setLimit(limit*2) | INV-01 |
| 329 | 1 getter | **真违规** | `filterByLanguage`：仅读 getFilePath 1 字段，全 `NopCodeFile`(CLOB)；应投影（与 query-limit:329 同位双违规） | INV-01 |

---

## §3 delete-contract（INV-02，0 命中）

`--family delete-contract --list` 退出码 0，0 命中。

**退化理由**：grep `useLogicalDelete|logicalDelete|delFlag` 全 nop-code 模块零命中；ORM 11 实体均无逻辑删除标记列。删除路径统一物理删除：
- `deleteIndex`（:529）→ `deleteEntitiesPaged`（:562，batch 500 + evictAll）覆盖 10 子实体 + NopCodeIndex。
- `deleteFileRecords`（:1440）→ `deleteEntitiesByFilter` / `deleteRelationalBySymbolIds` / `batchDeleteEntities`。
- `OrmFingerprintStore.deleteByIndex`（:132）→ `deleteEntity`。

**非默认跳过**：本节显式记录「0 命中 + 退化理由」，门禁退化为「删除路径统一物理删除契约一致性」——若未来引入 `useLogicalDelete` 或逻辑删除 setter，门禁退出非零。

---

## §4 idempotency（INV-03，2 red-list 可执行锁）

> 来源：`TestNopCodeIndexIdempotencyInvariant`。2 green 锁（`triggerIncrementalIndex`/`batchSaveFileRecords`）+ 表完备性门禁（反射核验 `ICodeIndexService` 全公共方法）均绿。下表为 2 个 **已锁定的真违规**（I4 靶点）。

| 方法 | 违规 | 根因 | 锁定测试 | 分类 |
|------|------|------|---------|------|
| `indexDirectory` | 重试（不清空再索引同目录）抛 `JdbcException` duplicate-key 23505 | `saveReplacingExisting`（:1475）只捕获 `nop.err.orm.save-entity-replace-existing-entity`，未捕获 JDBC 23505 | `testKnownRedList_indexDirectoryRetryFailsIdempotency`（assertThrows） | **已锁定真违规（I4 靶点）** |
| `indexFile` | 重试（再索引同文件）抛 duplicate-key 23505 | 同上 | `testKnownRedList_indexFileRetryFailsIdempotency`（assertThrows） | **已锁定真违规（I4 靶点）** |

**自更新契约**：I4 修复后 assertThrows 锁变红（不再抛）→ 强制把方法从 `KNOWN_NON_IDEMPOTENT` 移入 `IDEMPOTENCE_TABLE` 并补 verify 分支。

---

## §5 对抗探查新增段（占位，见 adversarial-probe 报告）

对抗探查（Phase 2）发现的、四族静态门禁未覆盖的 live defect 追加进本段。详见 `i2-adversarial-probe.md`。摘要：

| 发现 | live 路径 | 归属不变式 | AR-ID | 详见 |
|------|----------|-----------|-------|------|
| 缓存返回可变共享引用（SymbolTable 原地修改竞态） | `CodeCacheManager:153` + `SymbolTable:26,43` | INV-05 | AR-158/155 | adversarial-probe §1 |
| CallGraph 读方法缺 synchronized | `CallGraph.java:46,52` | INV-05 | AR-145/148 | adversarial-probe §1 |
| MAX_QUERY_RESULTS 静默截断（无可观测信号） | `CodeQueryService:114/252/319/755/762/799` | INV-04（截断可观测子项） | AR-136/168/177 | adversarial-probe §2 |
| 增量索引不清理搜索引擎旧符号文档 | `CodeIndexService:1182`(仅 addDoc) vs `:1451`(仅硬删清理) | INV-03 | AR-166 | adversarial-probe §3 |
| 跨文件 calleeId/孤儿引用清理缺失 | `CodeIndexService:1440-1472` | INV-03 | AR-30/66 | adversarial-probe §3 |

---

## 无静默跳过确认

- 每条命中均有且仅有一个分类（`真违规` | `已接受有界/全实体` | `门禁误报`），本 red list **0 条 `门禁误报`**——扫描器命中的位置在 live code 中均存在真实状态（真违规或合理的已接受）；扫描器的**理由标签**在 3 处（:102/:899/:1818 的「.size()」）与 live 不符，已在对应行标注「扫描器理由误标」，但位置本身仍为真违规（CLOB/投影缺陷），未省略。
- `[UNDETERMINED]` 调用点：当前 baseline 0 个；扫描器机制保证未来内联表达式不会被静默 `continue`。
- AR-168（buildFilePathCache）与 AR-177（getProjectFilePaths）均**明确裁定为真违规**（投影已修但无 setLimit），无悬空。
- delete-contract 0 命中已附退化理由，非默认跳过。
