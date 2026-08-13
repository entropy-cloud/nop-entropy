# nop-code 不变式目录（首批候选）

> Status: active
> Last Reviewed: 2026-08-13
> Source: I0 盘点（`ai-dev/plans/2026-08-13-0709-1-nop-code-invariant-i0-inventory-and-baseline.md` Phase 2）
> 依赖: `ar-status-matrix.md`（Phase 1 产出）
> 后继: I1 门禁将基于本目录落地可执行检测规则（JUnit / `.mjs` / ArchUnit）

## 目的

从 Phase 1 的失败族归纳不变式陈述，作为 I1 门禁沉淀的确定性输入。每条不变式含四要素 + live 锚点。

## 不变式总览

| ID | 不变式简称 | 覆盖族 | 检测方法 | I1 优先级 |
|----|-----------|--------|---------|-----------|
| INV-01 | 实体加载字段最小化 | OOM-field-loading | `.mjs` 静态扫描 + AST | P0 |
| INV-02 | 删除路径统一物理删除契约 | logical-delete-contract（退化形态） | ORM + service 交叉检查 | P1 |
| INV-03 | 增量索引幂等性 | incremental-index-desync / idempotency | JUnit `@ParameterizedTest` | P1 |
| INV-04 | 查询结果上限声明 | OOM-field-loading / error-handling | `.mjs` 静态扫描 | P0 |
| INV-05 | 缓存对象不可变性 | concurrency-lock | `.mjs` 静态扫描 + JUnit | P1 |

---

## INV-01 — 实体加载字段最小化（投影查询门禁）

**陈述**：当加载实体集合仅为读取少量字段（≤3 个非主键字段）时，必须使用投影查询（`selectFieldsByQuery` + `addField`）而非全实体加载（`findAllByQuery`）。尤其禁止在循环/缓存构建路径中加载含 CLOB/BLOB 列（如 `sourceCode`、`imports`）的全实体。

**覆盖失败族**：OOM-field-loading（实体加载字段最小化）

**历史 audit-finding-ID 证据**：
- AR-135 / AR-154(r8) / AR-168：`buildFilePathCache` 加载含 `sourceCode` CLOB 的全 `NopCodeFile` 实体，仅为取 `id`+`filePath`。出处：`2026-06-06b-adversarial-review-nop-code/01-open-findings.md`、`2026-06-06-adversarial-review-nop-code/01-open-findings.md`、`2026-06-06d-adversarial-review-nop-code/01-open-findings.md`。
- AR-87 / AR-177：`getProjectFilePaths` 加载全 CLOB 实体仅为取 `filePath`。出处：`2026-06-01-adversarial-review-nop-code-r6/01-open-findings.md`、`2026-06-06f-adversarial-review-nop-code/01-open-findings.md`。
- AR-64 / AR-86：`findImplementations` 全量加载所有符号构建 idToQn。出处：`2026-06-01-adversarial-review-nop-code/01-open-findings.md`。
- AR-75：`resolveQualifiedNamesToIds` 全实体加载继承/注解记录。出处：`2026-05-31-adversarial-review-nop-code/01-open-findings.md`。
- AR-77(r4)：`getIndexStats`/`updateIndexStats` 全量加载仅 `.size()`（已修为 `countByQuery`，作为正面先例）。出处：`2026-05-31-adversarial-review-nop-code-r4/01-open-findings.md`。

**检测方法**：`ai-dev/tools/*.mjs` 静态扫描（AST 级别）——检测 `findAllByQuery` 调用，若其结果仅用于读取 ≤3 字段或紧随 `.size()`，则告警。配合 CLOB 列名清单（从 ORM 模型提取）做交叉检查：任何加载含 CLOB 列实体的 `findAllByQuery` 均为高优先级违规。

**live 锚点**（I1 门禁对接点）：
- `nop-code-service/.../impl/CodeSearchService.java:196-212`（buildFilePathCache，已修为投影，作为正面锚点）
- `nop-code-service/.../impl/CodeIndexService.java:1398-1413`（getProjectFilePaths，已修为投影）
- `nop-code-service/.../impl/CodeQueryService.java:785-856`（findImplementations，仍违规 ✗）
- `nop-code-service/.../impl/CodeIndexService.java:920-968`（resolveQualifiedNamesToIds，仍违规 ✗）

---

## INV-02 — 删除路径统一物理删除契约一致性

**陈述**：nop-code 的所有实体删除路径（`deleteIndex`、`deleteFileRecords`、`deleteEntitiesPaged`、`batchDeleteEntities`、`deleteEntitiesByFilter`、`deleteRelationalBySymbolIds`）必须统一使用物理删除（`DELETE` SQL）。ORM 模型中不得引入 `useLogicalDelete`（或任何软删除标记列），除非删除代码同步适配逻辑删除语义。

> **退化形态说明**：AR-176 / AR-54 的原始前提（「11 实体中仅 1 个使用 `useLogicalDelete`」）**已过时**——live grep `useLogicalDelete|logicalDelete|delFlag` 在整个 nop-code 模块零命中，ORM 11 实体均无逻辑删除。因此本不变式从「逻辑删除 vs 物理删除契约一致性」退化为「删除路径统一物理删除契约一致性」。若未来某实体引入 `useLogicalDelete`，本不变式自动恢复原始约束。

**覆盖失败族**：logical-delete-contract（退化形态）；data-consistency（级联删除缺失子项）

**历史 audit-finding-ID 证据**：
- AR-176：NopCodeSemanticEdge `useLogicalDelete` 但代码物理删除——前提过时（stale-premise）。出处：`2026-06-06f-adversarial-review-nop-code/01-open-findings.md`。
- AR-54：NopCodeSemanticEdge 唯一使用 delFlag 软删除——前提过时。出处：`2026-05-29-adversarial-review-nop-code-r3/01-open-findings.md`。
- AR-149(r8) / AR-150(r8)：NopCodeFile / NopCodeSymbol.usages 缺 `cascadeDelete`（删除路径契约的另一面）。出处：`2026-06-06-adversarial-review-nop-code/01-open-findings.md`。
- AR-60：deleteIndex 外键删除顺序错误。出处：`2026-06-01-adversarial-review-nop-code/01-open-findings.md`。

**检测方法**：ORM + service 交叉检查（`.mjs` 或 ArchUnit）——①扫描 `nop-code.orm.xml`，断言无 `useLogicalDelete` 属性（或若有，则验证删除代码使用 `update set delFlag` 而非 `batchDeleteEntities`）；②扫描全部删除方法，断言使用物理删除 API（`batchDeleteEntities` / `deleteEntityById`）而非逻辑删除模式；③验证级联删除完整性（每个有子关系的实体，删除路径覆盖全部子实体）。

**live 锚点**：
- `nop-code/model/nop-code.orm.xml`（11 实体，当前均无 useLogicalDelete ✓）
- `nop-code-service/.../impl/CodeIndexService.java:529-578`（deleteIndex → deleteEntitiesPaged → batchDeleteEntities，物理删除 ✓）
- `nop-code-service/.../impl/CodeIndexService.java:1440`（deleteFileRecords）
- `nop-code-service/.../impl/CodeIndexService.java:1516,1527`（deleteRelationalBySymbolIds / deleteEntitiesByFilter）
- `nop-code/model/nop-code.orm.xml:247-264`（NopCodeFile 缺 cascadeDelete ✗ — AR-149(r8) open）

---

## INV-03 — 增量索引幂等性

**陈述**：每个增量索引操作（`triggerIncrementalIndex`、`indexFile`、`resolveQualifiedNamesToIds`、`persistSingleFileInSession`）必须可安全重试——对同一输入重复执行不得产生重复记录、重复处理、或累积副作用。具体：①`resolveQualifiedNamesToIds` 必须跳过已解析的记录（幂等保护）；②增量索引不得因路径比较失效而退化为全量重建；③缓存失效必须在 DB 提交的同一锁区域内完成（无竞态窗口）。

**覆盖失败族**：incremental-index-desync / idempotency

**历史 audit-finding-ID 证据**：
- AR-178：`resolveQualifiedNamesToIds` 对已解析记录无幂等保护——增量索引重复处理全部继承/注解记录。出处：`2026-06-06f-adversarial-review-nop-code/01-open-findings.md`。（live 已修：`isLikelyResolvedId` 守卫）
- AR-124：增量索引路径比较失效——每次退化为全量重建。出处：`2026-06-06b-adversarial-review-nop-code/01-open-findings.md`。（live 已修：`MappedPathResource`）
- AR-03 / AR-31：`indexFile` 不刷新分析缓存。出处：`2026-05-29-adversarial-review-nop-code/01-open-findings.md`。（live 已修：`invalidateAnalysisCache` 在锁内）
- AR-181：`invalidateAnalysisCache` 在锁释放后执行——竞态窗口。出处：`2026-06-06f-adversarial-review-nop-code/01-open-findings.md`。（live 已修：在 `withIndexLock` 内）
- AR-166：增量索引不清理搜索引擎旧符号文档——搜索引擎与 DB 去同步。出处：`2026-06-06d-adversarial-review-nop-code/01-open-findings.md`。（仍 open ✗）
- AR-30 / AR-66：deleteFileRecords 不清理跨文件引用——孤儿记录累积。出处：`2026-05-29-adversarial-review-nop-code-r2/01-open-findings.md`、`2026-06-01-adversarial-review-nop-code/01-open-findings.md`。（仍 open ✗）

**检测方法**：JUnit `@ParameterizedTest`——对每个增量索引操作构造「执行两次，断言结果一致、无重复记录、无累积副作用」的参数化测试。配合 `.mjs` 静态扫描检测路径比较两端格式一致性。

**live 锚点**：
- `nop-code-service/.../impl/CodeIndexService.java:920-968`（resolveQualifiedNamesToIds，`isLikelyResolvedId` 守卫 ✓）
- `nop-code-service/.../impl/CodeIndexService.java:651-737`（triggerIncrementalIndex，`MappedPathResource` ✓）
- `nop-code-service/.../impl/CodeIndexService.java:314-332`（indexFile，`invalidateAnalysisCache` 在锁内 ✓）
- `nop-code-service/.../impl/CodeIndexService.java:757-797,1192-1224`（搜索引擎增量同步，仍 open ✗）
- `nop-code-service/.../impl/CodeIndexService.java:1204-1228`（跨文件 Call 引用清理，仍 open ✗）

---

## INV-04 — 查询结果上限声明（防 OOM）

**陈述**：每个全表 / 大表查询（`findAllByQuery`、`selectFieldsByQuery`）必须显式声明结果上限（`setLimit` 或等价机制），除非调用方可证明结果集有界（如已被 `indexId` + 外键过滤到单条记录）。无上限的全表扫描在大规模索引（10 万+ 符号 / 文件）时可导致 OOM。

**覆盖失败族**：OOM-field-loading（查询结果上限子项）/ error-handling（静默截断子项）

**历史 audit-finding-ID 证据**：
- AR-168：`buildFilePathCache` 有 `MAX_QUERY_RESULTS=10000` 硬限制但静默截断（大型索引丢失文件路径）。出处：`2026-06-06d-adversarial-review-nop-code/01-open-findings.md`。live 投影查询已修，但**仍无 setLimit**。
- AR-177：`getProjectFilePaths` 无任何限制——10 万文件 OOM。出处：`2026-06-06f-adversarial-review-nop-code/01-open-findings.md`。live 投影查询已修，但**仍无 setLimit**。
- AR-136：`collectRelevantInheritances` 在 `MAX_QUERY_RESULTS` 处静默截断——类型层次不完整。出处：`2026-06-06b-adversarial-review-nop-code/01-open-findings.md`。
- AR-180：`buildInheritanceIndex`/`loadExistingEdgeKeys` 截断于 `MAX_QUERY_RESULTS(10000)`——启发式边去重失效。出处：`2026-06-06f-adversarial-review-nop-code/01-open-findings.md`。
- AR-61 / AR-76：`CodeCacheManager` 超限时静默返回不完整数据 / 降级为空。出处：`2026-06-01-adversarial-review-nop-code/01-open-findings.md`、`2026-05-31-adversarial-review-nop-code/01-open-findings.md`。

**检测方法**：`ai-dev/tools/*.mjs` 静态扫描——检测 `findAllByQuery` / `selectFieldsByQuery` 调用，若查询对象上无 `setLimit` / `setMaxResults` 调用且不在已知有界白名单中，则告警。截断点（`MAX_QUERY_RESULTS`）必须有显式 WARN 日志或可观测信号（非静默截断）。

**live 锚点**：
- `nop-code-service/.../impl/CodeSearchService.java:196-212`（buildFilePathCache，投影已修但无 setLimit ✗）
- `nop-code-service/.../impl/CodeIndexService.java:1398-1413`（getProjectFilePaths，投影已修但无 setLimit ✗）
- `nop-code-service/.../impl/CodeGraphService.java:218-226`（collectRelevantInheritances，MAX_QUERY_RESULTS 截断，有 WARN 但仍截断 ⚠）
- `nop-code-service/.../impl/CodeIndexService.java:865-896`（buildInheritanceIndex，截断 ✗）
- `nop-code-service/.../impl/CodeCacheManager.java:75-137`（超限降级 ⚠）

---

## INV-05 — 缓存对象不可变性（附加，非 roadmap 首批但高优先级）

**陈述**：`CodeCacheManager` 缓存的 `SymbolTable` / `CallGraph` 对象必须作为不可变快照返回给调用方。缓存更新（如 `persistSingleFileInSession` 增量追加）不得原地修改已被外部持有的缓存引用；读取方法（如 `CallGraph.getAllNodeIds`、`getForwardMap`）必须与写入方法（`addEdge`）使用相同的同步保护。

**覆盖失败族**：concurrency-lock

**历史 audit-finding-ID 证据**：
- AR-158 / AR-155(r8)：`persistSingleFileInSession` 修改缓存 SymbolTable——`addToSymbolTableCache` 原地 `symbolTable.add(sym)`，并发场景 HashMap 损坏。出处：`2026-06-06d-adversarial-review-nop-code/01-open-findings.md`、`2026-06-06-adversarial-review-nop-code/01-open-findings.md`。
- AR-145(r8)：`CallGraph.getAllNodeIds`/`getForwardMap` 缺 `synchronized`——与 `addEdge` 数据竞争。出处：`2026-06-06-adversarial-review-nop-code/01-open-findings.md`。
- AR-147(r8) / AR-148(r8)：FlowDetector / CallGraph 返回可变内部集合引用。出处：同上。

**检测方法**：`.mjs` 静态扫描（检测缓存管理类返回内部集合引用而非防御性拷贝/不可变包装）+ JUnit 并发测试（多线程并发 indexFile + getOrRebuildSymbolTable，断言无 ConcurrentModificationException / HashMap 死循环）。

**live 锚点**：
- `nop-code-core/.../graph/CallGraph.java:27,36,41,46,52`（addEdge/getCallees/getCallers 有 synchronized；getAllNodeIds/getForwardMap 无 ✗）
- `nop-code-service/.../impl/CodeIndexService.java:1030-1073`（persistSingleFileInSession 修改缓存 ✗）
- `nop-code-service/.../impl/CodeCacheManager.java:146-160`（addToSymbolTableCache 原地修改 ✗）

---

## 备注

- 逻辑删除族不变式（INV-02）已反映 live 现状（退化为物理删除契约一致性），未沿用 AR-176/AR-54 的过时前提。
- 每条不变式的 live 锚点在 live repo 中真实存在（已抽查：CodeSearchService.java:196、CodeIndexService.java:529/1398/920、CallGraph.java:46、CodeCacheManager.java:146 均可定位）。
- I1 门禁的具体检测规则细节（regex/AST 模式）属 I1 范围，非本目录阻塞项。
