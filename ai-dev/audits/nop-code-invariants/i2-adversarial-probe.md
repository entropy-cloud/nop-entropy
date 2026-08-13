# nop-code 不变式闭环 I2 — 对抗探查报告（并发 + OOM 盲区）

> Status: active
> Last Reviewed: 2026-08-13
> Source: I2 计划 `ai-dev/plans/2026-08-13-0806-3-nop-code-invariant-i2-invariant-driven-audit.md` Phase 2
> 探查面: INV-05 缓存不可变性（并发路径）+ INV-04 截断静默化（OOM 盲区）+ INV-03 跨文件孤儿 / 搜索引擎去同步
> 后继: 新发现已追加进 `i2-red-list.md` §5；I3 裁决消费本报告

## 目的

四族门禁均为 AST/regex 静态扫描 + JUnit 幂等穷举，**不覆盖**运行时并发竞态、跨文件清理语义、搜索引擎与 DB 去同步、截断静默化。本报告用 live code 追踪 + 针对性证据暴露这些盲区，每条新发现附 `文件:行` 路径证据。

## 探查方法

- **静态 code 追踪**为主：对每个 open 锚点，从入口追到出口，标注当前 live 状态。
- **JUnit 探针**：本报告全部结论均可由静态 code 追踪确定性判定（数据流无歧义），故 **No new test required: 静态 code 追踪足以判定**。并发竞态（HashMap race）本质上是非确定性的，确定性单线程探针无法可靠复现；截断静默化与跨文件孤儿均为静态可判定的语义缺陷。INV-05 若需运行时锁定，应由 Cycle 2 / I1 沉淀的并发门禁覆盖（见 §1 裁定）。

---

## §1 并发路径探查（INV-05 面）

### 1.1 SymbolTable 缓存返回可变共享引用 — **缺陷确认（AR-158/155 OPEN）**

**数据流追踪**：
1. `CodeCacheManager.getOrRebuildSymbolTable`（`CodeCacheManager.java:102-116`）：在 `lock` 内返回 `entry.cache.symbolTable`（缓存内**同一个** `SymbolTable` 引用，非快照副本）。
2. `SymbolTable.getAll()`（`SymbolTable.java:43-45`）：`return byId.values();` —— 返回内部 HashMap 的** live Collection 视图**（非拷贝）。
3. `SymbolTable.add()`（`SymbolTable.java:26-33`）：`byQualifiedName.put(...)` / `byId.put(...)` —— 原地修改内部 HashMap。
4. `CodeCacheManager.addToSymbolTableCache`（`CodeCacheManager.java:146-160`）：在 `lock` 内对缓存的 `SymbolTable` 调用 `entry.cache.symbolTable.add(sym)` —— **原地修改已被外部持有的缓存引用**。
5. 调用链 `persistSingleFileInSession`（`CodeIndexService.java:1053,1068`）：先 `getOrRebuildSymbolTable` 拿到缓存引用并在 `:1056` 迭代 `globalTable.getAll()`（此时**已释放 cache lock**），随后 `:1068` `addToSymbolTableCache` 原地修改同一对象。

**竞态窗口**：indexFile 写路径（持 `withIndexLock`）在 `:1056` 无锁迭代缓存 SymbolTable 并在 `:1068` 原地修改；而只读查询路径（如 `CodeQueryService.findImplementations:802-810` 经 `getOrRebuildSymbolTable` 拿同一引用后迭代 `symbolTable.getAll()`）**不持 `withIndexLock`**，只短暂持 cache lock。两条路径并发迭代/修改同一 HashMap → `ConcurrentModificationException` / HashMap 结构损坏 / resize 死循环。

**裁定**：缺陷确认。归属 INV-05。`SymbolTable` 全类无 `synchronized`，缓存契约「不可变快照」未成立。

### 1.2 CallGraph 读方法缺 synchronized — **缺陷确认（AR-145/148 OPEN）**

**live 状态**（`CallGraph.java`）：
| 方法 | 行 | synchronized | 防御性拷贝 | 状态 |
|------|----|------------|-----------|------|
| `addEdge` | 27 | ✓ | n/a | 已修 |
| `getCallees` | 36 | ✓ | ✓ `new ArrayList<>(callees)` | **已修**（AR-04 关联） |
| `getCallers` | 41 | ✓ | ✓ `new ArrayList<>(callers)` | **已修** |
| `getAllNodeIds` | 46 | **✗** | 新建 HashSet 但读 keySet 无锁 | **缺陷确认（AR-145）** |
| `getForwardMap` | 52 | **✗** | `unmodifiableMap` 包装但 **value 仍为可变 ArrayList**（live 引用） | **缺陷确认（AR-145/148）** |

**运行时风险评估**：CallGraph 由 `rebuildCallGraph`（`CodeCacheManager.java:212-241`）在 cache lock 内新建并构建完成后才存入缓存；缓存后无 `addToCallGraphCache` 式增量变更 API。故**实际并发修改概率低**（缓存实例构建后不被原地变更）。但**不变式层面**违反「读方法须与写方法同同步保护」——一旦未来出现缓存图增量变更（如增量 addEdge），即触发数据竞争。`getForwardMap` 返回的 unmodifiableMap 的 value 是 live ArrayList，调用方经 `map.get(k)` 拿到后可 `.add()` 改变缓存内部态（AR-148 残留）。

**裁定**：缺陷确认（code-level 不变式违反；运行时风险当前低但无防御）。归属 INV-05。

### 1.3 AR-092 前提复核 — **stale-premise（建议改判）**

`ar-status-matrix.md` 记 AR-092「CodeCacheManager 全方法 synchronized + ConcurrentHashMap 冗余」为 open。**live 复核**：`CodeCacheManager.java` 当前用**单个 `ReentrantLock`**（`:62`，非方法级 synchronized），`analysisCacheMap` 是**普通 `LinkedHashMap`**（`:60`，非 ConcurrentHashMap）。故 AR-092 的前提（「全方法 synchronized + ConcurrentHashMap 冗余」）**全部过时**——既无 synchronized 方法也无 ConcurrentHashMap。建议 I3 将 AR-092 改判为 `stale-premise`。

### 1.4 其余 concurrency-lock open 锚点 live 状态

| AR-ID | 锚点 | live 状态 | 结论 |
|-------|------|----------|------|
| AR-04 | CallGraph 返回内部可变列表（getCallees/getCallers） | `:36,41` 已 synchronized + 防御性拷贝 | **已修** |
| AR-11 | getOrRebuildSymbolTable 等缓存方法粗粒度 | live 用 ReentrantLock 细粒度（per-call lock），不再是粗 synchronized；但缓存返回可变引用（见 §1.1）是另一残留 | 部分修（粗粒度已解；可变引用残留） |
| AR-42 | filterByLanguage 用 removeIf 修改传入列表 | `CodeSearchService.java:333` `results.removeIf(...)` 原地修改传入 List | **缺陷确认（OPEN）** |
| AR-62 | indexLocks ConcurrentHashMap 永不清理 | `withIndexLock` finally 中 `remove`（部分缓解），并发场景仍可能泄漏 | **缺陷确认（部分缓解）** |
| AR-147 | FlowDetector.listFlows 返回可变缓存引用 | `FlowDetector.java:163-169` 返回缓存 List 引用 | **缺陷确认（OPEN）** |
| AR-157 | FlowDetector.evictOverflow 无序驱逐 | `FlowDetector.java:570-576` 驱逐策略无序 | **缺陷确认（OPEN）** |
| AR-182 | incrementalStatusMap LRU 无持久化 | `NopCodeIndexBizModel.java:44-50` 重启后状态丢失 | **缺陷确认（OPEN）** |

---

## §2 OOM 盲区探查（截断静默化）

### 2.1 MAX_QUERY_RESULTS 硬截断 + 无可观测信号 — **缺陷确认（AR-136/168/177 族）**

**常量**：`CodeIndexService.MAX_QUERY_RESULTS = 10000`（`CodeIndexService.java:106`）；`CodeGraphService.BATCH_QUERY_LIMIT = 10000`（`CodeGraphService.java:58`）。

**使用点 + 截断可观测性**：
| 位置 | 用途 | setLimit | 命中上限时 WARN/标志？ |
|------|------|---------|----------------------|
| `CodeQueryService:114` getFiles | 全文件列表 | MAX_QUERY_RESULTS | **无**（静默截断） |
| `CodeQueryService:252` getModuleDigest files | 模块摘要文件 | MAX_QUERY_RESULTS | **无** |
| `CodeQueryService:319` getPublicSurface files | 公共 API 文件 | MAX_QUERY_RESULTS | **无** |
| `CodeQueryService:755/762` findByAnnotation | 注解搜索 | MAX_QUERY_RESULTS | **无** |
| `CodeQueryService:799` findImplementations inhQuery | 实现类继承 | MAX_QUERY_RESULTS | **无** |
| `CodeGraphService:298` collectRelevantInheritances | 类型层次收集 | BATCH_QUERY_LIMIT | **无** |

**裁定**：6+ 处 `setLimit(10000)` 后均**未检查 `results.size() == limit` 也无 WARN**。大型索引（>1 万符号/文件/继承）静默返回不完整数据，下游分析（类型层次、公共 API、实现类）基于残缺数据得出错误结论且无告警。归属 INV-04「截断点必须有 WARN/可观测信号」子项。**新发现**（red list §5）。

### 2.2 CodeCacheManager 超限降级 — **部分可观测（残留：调用方不检查标志）**

`CodeCacheManager.rebuildSymbolTable`（`:199-204`）/ `rebuildCallGraph`（`:230-235`）：超 `MAX_CACHE_SYMBOLS=100000`/`MAX_CACHE_EDGES=500000` 时 `LOG.warn(...)` + `setTruncated(true)`。**截断非静默**（有 WARN + 标志）。

**残留缺陷**：`SymbolTable.isTruncated()` / `CallGraph.isTruncated()` 标志存在，但 grep 全模块**无任何调用方检查 `isTruncated()`**。即下游分析在缓存被截断时仍把部分数据当完整数据处理。AR-61/76 的「静默降级」在 cache 层已部分解（有 WARN），但在消费层仍未解（标志被忽略）。**缺陷确认（部分缓解）**。

### 2.3 AR-180 前提复核 — **stale-premise（建议改判）**

`ar-status-matrix.md` 记 AR-180「`buildInheritanceIndex`/`loadExistingEdgeKeys` 截断于 MAX_QUERY_RESULTS(10000)」为 open。

**live 复核**：
- `buildInheritanceIndex`（`CodeIndexService.java:862-889`）：`while(true)` + `setOffset` + `setLimit(BATCH_SIZE=1000)` + `if (batch.size() < BATCH_SIZE) break` —— **全量分页扫描，非截断**。
- `loadExistingEdgeKeys`（`:891-908`）：同样 `while+offset+setLimit(BATCH_SIZE=1000)` 全分页。

**裁定**：AR-180「截断」前提**过时**——两方法均全量分页（无 MAX_QUERY_RESULTS 截断）。建议 I3 将 AR-180 改判 `stale-premise`（截断部分）；其 entity-field-min 残留（2 字段应投影，见 red list §2 CodeIndexService:899）另计。

---

## §3 跨文件孤儿 + 搜索引擎去同步探查（INV-03 面）

### 3.1 deleteFileRecords 跨文件孤儿清理缺失 — **缺陷确认（AR-30/66 OPEN）**

**live 追踪**（`CodeIndexService.deleteFileRecords:1440-1472`）：删除文件 A 时清理：
- `NopCodeCall` by `fileId=A`（:1457）✓ — **仅本文件 call**。
- `NopCodeSymbol` by `fileId=A`（:1458）✓
- `NopCodeUsage` by `fileId=A`（:1459）✓
- `NopCodeDependency` by `sourceFilePath=A`（:1460）✓
- `NopCodeAnnotationUsage` by `annotatedSymbolId IN A.symbols`（:1461）✓
- `NopCodeInheritance` by `subTypeId IN A.symbols`（:1462）—— **仅删 A 作为 sub 的继承**；A 作为 **super**（`superTypeId IN A.symbols`）的继承行**未删** → 跨文件孤儿。
- `NopCodeSemanticEdge` by `sourceSymbolId`/`targetSymbolId IN A.symbols`（:1463-1464）✓
- `NopCodeFlowMembership` by `symbolId IN A.symbols`（:1465）✓

**孤儿残留**：
1. **跨文件 NopCodeCall**：文件 B 中的 call（`fileId=B`）其 `calleeId`/`callerId` 指向文件 A 已删符号 → 孤儿 call（:1457 只删 `fileId=A` 的 call，不删引用 A 符号的 B 文件 call）。**缺陷确认（AR-66）**。
2. **跨文件 NopCodeInheritance**：A 符号作为 super（被其他文件 sub 继承）的继承行（`superTypeId` 指向 A）未删（:1462 只按 subTypeId 删）。**缺陷确认（AR-30 关联）**。

**裁定**：两处跨文件引用清理缺失。归属 INV-03「deleteFileRecords 不清理跨文件引用」。新发现已进 red list §5。

### 3.2 增量索引不清理搜索引擎旧符号文档 — **缺陷确认（AR-166 OPEN）**

**live 追踪**（searchEngine 使用点全模块仅 4 处）：
| 位置 | 操作 | 路径 |
|------|------|------|
| `CodeIndexService:1182` | `searchEngine.addDoc(topic, doc)` | 索引/持久化符号时**添加** |
| `CodeIndexService:1451` | `searchEngine.removeDocs(topic, symbolIds)` | `deleteFileRecords`（硬删文件）时**删除** |
| `CodeIndexService:535` | `searchEngine.removeTopic(...)` | `deleteIndex` 整索引删除 |
| `CodeSearchService:84` | `searchEngine.search(req)` | 查询 |

**缺陷**：增量索引路径（`triggerIncrementalIndex` → 对**已变更文件**重索引 → `persistSingleFileInSession`/`saveFileResultInSession`）只 `addDoc`（:1182）新符号文档，**不 `removeDocs` 该文件中已不存在的旧符号文档**（`removeDocs` 仅在硬删路径 `deleteFileRecords` 触发）。结果：修改一个文件后，被删除/重命名的符号的搜索引擎文档**残留**，搜索引擎与 DB 去同步，`searchViaEngine` 返回指向已不存在符号的幽灵结果。

**裁定**：缺陷确认。归属 INV-03「搜索引擎增量同步」。新发现已进 red list §5。

---

## §4 探查结论汇总

| 探查面 | open 锚点数 | 缺陷确认 | 已修 | stale-premise（建议改判） | 需运行时探针 |
|--------|-----------|---------|------|------------------------|------------|
| INV-05 并发 | 11 | AR-158/155, AR-145/148, AR-42, AR-62, AR-147, AR-157, AR-182 | AR-04, AR-20 等 | AR-092 | 0（静态充分） |
| OOM 截断静默化 | 5 | MAX_QUERY_RESULTS 6 处静默截断；isTruncated 标志无人检查 | cache 层 WARN（部分） | AR-180 | 0 |
| INV-03 跨文件/搜索 | 3 | AR-30/66（跨文件孤儿）, AR-166（搜索去同步） | AR-124/03/31/181 等 | 0 | 0 |

**新发现（门禁未覆盖的 live defect，已进 red list §5）**：
1. SymbolTable 缓存可变共享引用（AR-158/155）→ INV-05。
2. CallGraph 读方法缺 synchronized（AR-145/148）→ INV-05。
3. MAX_QUERY_RESULTS 6 处静默截断（AR-136/168/177）→ INV-04 截断可观测子项。
4. 增量索引不清理搜索旧文档（AR-166）→ INV-03。
5. 跨文件 calleeId/superTypeId 孤儿（AR-30/66）→ INV-03。

**Test-Mandated 裁定**：本 Phase **不新增 JUnit 探针**。理由（`No new test required: 静态 code 追踪足以判定`）：全部 5 条新发现的数据流在 live code 中无歧义（缓存返回 live 引用、setLimit 后无 size 检查、removeDocs 仅硬删路径触发），静态追踪即可确定性确认缺陷。并发 HashMap 竞态是非确定性的，确定性单线程探针无法可靠复现，运行时锁定应交给 Cycle 2 / I1 的 INV-05 并发门禁（见下）。

---

## §5 INV-05 沉淀门禁裁定（给 I3）

**裁定：需要沉淀为新门禁（→ Cycle 2 / I1 候选输入）。**

理由：INV-05（缓存对象不可变性）有 7 条确认 open 锚点（AR-158/155/145/148/147/157/42），且四族静态门禁（AST/regex）**无法覆盖**「缓存返回是否为不可变快照」「读方法是否与写方法同同步保护」这类语义。建议 Cycle 2 / I1 落地两类检测：
1. `.mjs` 静态扫描：检测缓存管理类返回内部集合引用而非防御性拷贝/不可变包装（`return this.xxxMap` / `return xxx.values()` 在被缓存类中）。
2. JUnit 并发探针：多线程并发 `indexFile` + `getOrRebuildSymbolTable`，断言无 `ConcurrentModificationException`（容忍 CI 非确定性，作 canary 而非硬门禁）+ 单线程确定性探针断言「缓存返回的对象在 addToSymbolTableCache 后内容不变」（锁定不可变快照契约）。

**不在本计划实现**（Cycle 1 / I2 Non-Goal），仅产出裁定输入。
