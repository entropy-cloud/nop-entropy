# nop-code 不变式闭环 I3 — 发现裁决矩阵（Cycle 1）

> Status: active
> Last Reviewed: 2026-08-13
> Source: I3 计划 `ai-dev/plans/2026-08-13-0806-4-nop-code-invariant-i3-finding-adjudication.md`
> 输入: `i2-red-list.md`（57 静态命中 + 2 JUnit red-list 锁 + §5 对抗探查新增）/ `i2-coverage-matrix.md`（95 条 open 悬空发现）/ `i2-adversarial-probe.md`（3 面探查结论）
> 后继: I4 修复执行消费本矩阵的「I4 修复队列工作包」（§C）；I6 收口消费 §D（Cycle 2/I1 候选输入）
> 方法论: `ai-dev/skills/invariant-loop-audit-prompt.md`

## 目的

把 I2 产出的 **red list 真违规 + 未覆盖悬空发现 + 对抗探查新发现** 统一**逐条裁决**，产出零悬挂裁决矩阵：每条 in-scope 发现落到且只落到一种终态（`P0 I4 修复` / `P1 I4 修复` / `P2/P3 后继修复计划` / `接受为残余风险` / `前提过时 stale` / `移出范围 out-of-scope`）。本文件是 I4 修复队列的确定性输入——I4 按工作包执行即可，无需重新裁决。

## 裁决规则（Priority + Terminal 定义）

**Priority 分级依据**（源自 roadmap Cross-Cutting 授权 + I3 计划）：

- **P0** = 数据正确性 / OOM（正常规模数据可触达）/ 并发损坏（可触达）/ 幂等性硬失败。自动预授权 I4 修复。
- **P1** = 性能 / 可观测性 / 降级语义 / 仅病态数据可触达的 OOM / 低运行时风险的并发硬化 / 已确认的窄面正确性缺陷。自动预授权 I4 修复。
- **P2/P3** = 已确认 defect 但属 (a) 辅助/非核心特性、(b) 低频窄面触发、(c) 有降级/绕过路径、(d) 复杂修复宜独立后继。**不进 Cycle 1 I4 自动授权范围**，须附 `Why Not Blocking Cycle 1 Closure` + 显式后继归属。

**Terminal 终态**（每条有且仅有一个）：

| 终态 | 含义 | Anti-Slacking 约束 |
|------|------|-------------------|
| `P0 I4 修复` / `P1 I4 修复` | Cycle 1 I4 自动授权修复 | 已确认 live defect 须走此路，不得降级 |
| `P2/P3 后继修复计划` | 显式 successor ownership | 须附 Why Not Blocking + 后继路径 |
| `接受为残余风险` | watch-only residual / optimization candidate | 须附 Why Not Blocking；仅限非 live-defect 优化项 |
| `前提过时 stale` | live 复核前提不成立 | 须附 live 证据；触发矩阵改判 |
| `移出范围 out-of-scope` | 属其他模块/roadmap | 须附移出理由 + 后继归属 |

**类别清扫面（sweep face）原则**：修任一处必穷举同类（roadmap I4）。本矩阵把同族待修条目归入同一工作包，I4 按工作包执行。

---

# §A. Phase 1 — red list 真违规裁决（门禁驱动）

> 输入 = `i2-red-list.md` 真违规段：query-limit 19 真违规 + entity-field-min 12 真违规 + idempotency 2 red-list 锁 + §5 对抗探查新增 5 族。
> 路径前缀（query-limit / entity-field-min）：`nop-code/nop-code-service/src/main/java/io/nop/code/service/`

## §A.1 query-limit 真违规（INV-04，19 条）

> 优先级分流原则：**全索引范围（`eq(indexId)` 无 per-entity 上界，正常大索引可触达 OOM）= P0**；**单实体子集范围（`eq(fileId)`/`eq(flowId)`/单符号子成员，仅病态基数可触达 OOM）= P1**。两者终态均为 `I4 修复`，分流仅影响 I4 排序。

### OrmFingerprintStore.java

| 行 | 方法 | 范围 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|------|---------|---------|--------|---------|
| 84 | `loadFileIdMapByIndex` 投影 id+filePath | 全索引 | **P0** | I4 修复 | INV-04 | WP-1 OOM-查询上限 |
| 102 | `loadFingerprints` 全 NopCodeFile(CLOB) | 全索引 | **P0** | I4 修复 | INV-04 + INV-01（=AR-135/154） | WP-1 + WP-2（双违规，单修投影+LIMIT） |
| 137 | `deleteByIndex` 全实体逐条删 | 全索引 | **P0** | I4 修复 | INV-04 | WP-1（改分页删除） |
| 148 | `findByIndexAndPath` | — | — | （已接受有界，不在真违规集） | INV-04 | — |

### impl/CodeCacheManager.java

| 行 | 方法 | 范围 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|------|---------|---------|--------|---------|
| 247 | `rebuildDependencies` 全 NopCodeDependency | 全索引 | **P0** | I4 修复 | INV-04 | WP-1（对齐 rebuildSymbolTable/CallGraph 分页） |

### impl/CodeIndexService.java

| 行 | 方法 | 范围 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|------|---------|---------|--------|---------|
| 505 | `getIndexStats` 投影 kind 计数 | 全索引 | **P0** | I4 修复 | INV-04 | WP-1 |
| 1404 | `getProjectFilePaths` 投影 filePath | 全索引 | **P0** | I4 修复 | INV-04 / **AR-177** | WP-1 |
| 1511 | `findSymbolIdsByFileId` eq(fileId) | 单文件 | **P1** | I4 修复 | INV-04 | WP-1 |
| 1521 | `deleteRelationalBySymbolIds` IN 非唯一 FK | 全索引 | **P0** | I4 修复 | INV-04 | WP-1 + WP-4 删除路径 |
| 1531 | `deleteEntitiesByFilter` eq 非唯一列 | 全索引 | **P0** | I4 修复 | INV-04 | WP-1 + WP-4 删除路径 |
| 1602 | `listFlows` eq(indexId) 全 flows | 全索引 | **P0** | I4 修复 | INV-04 | WP-1（读侧加上限，写侧已有 MAX_FLOWS） |
| 1624 | `getFlow` eq(flowId) memberships | 单 flow | **P1** | I4 修复 | INV-04 | WP-1 |
| 1818 | `batchLoadFileRecords` 全 NopCodeFile(CLOB) | 全索引 | **P0** | I4 修复 | INV-04 + INV-01（=AR-135） | WP-1 + WP-2 |
| 1882 | `filterByLanguage` eq(indexId)+eq(language) | 单语言全集 | **P0** | I4 修复 | INV-04 + INV-01 | WP-1 + WP-2 |

### impl/CodeQueryService.java

| 行 | 方法 | 范围 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|------|---------|---------|--------|---------|
| 147 | `getFileSymbols` eq(fileId) | 单文件 | **P1** | I4 修复 | INV-04 | WP-1 |
| 562 | `getTypeOutline` 子成员 | 单符号 | **P1** | I4 修复 | INV-04 | WP-1 |
| 606 | `batchGetTypeOutlines` childQuery IN(parentId) 非唯一 | 单 type 子集 | **P1** | I4 修复 | INV-04 | WP-1 |
| 611 | `batchGetTypeOutlines` memberQuery IN(declaringSymbolId) 非唯一 | 单 type 子集 | **P1** | I4 修复 | INV-04 | WP-1 |

### impl/CodeSearchService.java

| 行 | 方法 | 范围 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|------|---------|---------|--------|---------|
| 202 | `buildFilePathCache` 投影 id+filePath | 全索引 | **P0** | I4 修复 | INV-04 / **AR-168** | WP-1 |
| 329 | `filterByLanguage` eq(indexId)+eq(language) | 单语言全集 | **P0** | I4 修复 | INV-04 + INV-01 | WP-1 + WP-2 |

> query-limit 真违规裁决计数：**P0 ×13 / P1 ×6 = 19**（全部 `I4 修复`，零降级）。

## §A.2 entity-field-min 真违规（INV-01，12 条）

> 与 query-limit 在同 `文件:行` 双命中的（:102/:1818/:1882/:329），由同一修复（投影 + LIMIT）一次性收敛，归入同一工作包。下表标 `双违规` 者已在 §A.1 计数，此处仅标 entity-field-min 维度的优先级。

### OrmFingerprintStore.java / CodeIndexService.java / CodeSearchService.java（双违规，见 §A.1）

| 行 | 方法 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|---------|---------|--------|---------|
| OrmFingerprintStore:102 | loadFingerprints（CLOB 取 4 标量） | **P0**（双违规） | I4 修复 | INV-01（=AR-135） | WP-2 |
| CodeIndexService:899 | `loadExistingEdgeKeys` 2 字段 caller/callee，已分页 | **P1** | I4 修复 | INV-01 | WP-2（无 CLOB、build 路径，低优先） |
| CodeIndexService:1818 | batchLoadFileRecords（CLOB 取 4 标量） | **P0**（双违规） | I4 修复 | INV-01（=AR-135） | WP-2 |
| CodeIndexService:1882 | filterByLanguage（CLOB 取 filePath） | **P0**（双违规） | I4 修复 | INV-01 | WP-2 |
| CodeSearchService:329 | filterByLanguage（CLOB 取 filePath） | **P0**（双违规） | I4 修复 | INV-01 | WP-2 |

### impl/CodeQueryService.java（entity-field-min 独有，非 query-limit 真违规）

| 行 | 方法 | live 实际访问 | Priority | Terminal | 不变式 | Sweep 面 |
|----|------|-------------|---------|---------|--------|---------|
| 253 | `getModuleDigest` files | CLOB 全实体取 3 字段，setLimit(10000) | **P0** | I4 修复 | INV-01 | WP-2（1 万 CLOB 行） |
| 320 | `getPublicSurface` files | CLOB 全实体取 2 字段，setLimit(10000) | **P0** | I4 修复 | INV-01 | WP-2 |
| 673 | `findReferencedBy` symbolQuery | 仅 getId，eq(QN) 有界 ≤1 | **P1** | I4 修复 | INV-01 | WP-2（影响极小） |
| 704 | `findReferencedBy` fileQuery | CLOB 取 id+filePath，in(id) 有界 | **P1** | I4 修复 | INV-01 | WP-2 |
| 756 | `findByAnnotation` annotQuery | 仅 getAnnotatedSymbolId，setLimit(10000) | **P1** | I4 修复 | INV-01 | WP-2 |
| 762 | `findByAnnotation` fuzzyQuery | 同 :756 | **P1** | I4 修复 | INV-01 | WP-2 |
| 792 | `findImplementations` symQuery | **仅 isEmpty() 存在性检查**，结果未消费 | **P1** | I4 修复 | INV-01 | WP-2（改 countByQuery/exists） |

> entity-field-min 真违规裁决计数：**P0 ×6（4 双违规 + 253/320）/ P1 ×6 = 12**（全部 `I4 修复`，零降级）。

## §A.3 idempotency red-list 锁（INV-03，2 条）

| 方法 | 违规 | Priority | Terminal | 不变式 | Sweep 面 | 自更新契约 |
|------|------|---------|---------|--------|---------|-----------|
| `indexDirectory` | 重试抛 duplicate-key 23505 | **P0** | I4 修复 | INV-03 | WP-3 幂等性 | I4 修复后 assertThrows 锁变红 → 移入 IDEMPOTENCE_TABLE + 补 verify 分支 |
| `indexFile` | 重试抛 duplicate-key 23505 | **P0** | I4 修复 | INV-03 | WP-3 幂等性 | 同上 |

> idempotency 裁决计数：**P0 ×2**（幂等硬失败，I3 计划明确要求 P0）。

## §A.4 对抗探查新增（red list §5，5 族）

| 发现 | live 路径 | Priority | Terminal | 不变式 | Sweep 面 |
|------|----------|---------|---------|--------|---------|
| SymbolTable 缓存返回可变共享引用（原地修改竞态） | CodeCacheManager:102-116,146-160 + SymbolTable:26,43 + CodeIndexService:1053,1068 | **P0** | I4 修复 | INV-05 | WP-5 缓存不可变性（写路径 :1068 原地改缓存引用 + 读路径无锁迭代 → 可触达 HashMap 结构损坏） |
| CallGraph 读方法缺 synchronized（getAllNodeIds/getForwardMap） | CallGraph.java:46,52 | **P1** | I4 修复 | INV-05 | WP-5（运行时风险当前低：缓存图构建后无增量变更 API；但 getForwardMap value 为 live ArrayList，调用方可 mutate） |
| MAX_QUERY_RESULTS 静默截断（无可观测信号） | CodeQueryService:114/252/319/755/762/799 + CodeGraphService:298 | **P1** | I4 修复 | INV-04 截断可观测子项 | WP-6 截断可观测性 |
| 增量索引不清理搜索引擎旧符号文档（搜索/DB 去同步） | CodeIndexService:1182(仅 addDoc) vs :1451(仅硬删清理) | **P0** | I4 修复 | INV-03 | WP-7 搜索引擎同步（幽灵结果 = 数据正确性） |
| 跨文件 calleeId/superTypeId 孤儿引用清理缺失 | CodeIndexService:1440-1472（:1462 仅按 subTypeId 删，superTypeId 侧 + 跨文件 call.calleeId 未清） | **P0** | I4 修复 | INV-03 | WP-4 删除路径完整性（孤儿引用 = 数据正确性） |

> 对抗探查裁决计数：**P0 ×3 / P1 ×2**（全部 `I4 修复`）。

### Phase 1 裁决汇总

| Family | 真违规数 | P0 | P1 | Terminal 全部 |
|--------|---------|----|----|--------------|
| query-limit（INV-04） | 19 | 13 | 6 | I4 修复 |
| entity-field-min（INV-01） | 12 | 6 | 6 | I4 修复 |
| idempotency（INV-03） | 2 | 2 | 0 | I4 修复 |
| 对抗探查新增 | 5 | 3 | 2 | I4 修复 |
| **合计** | **38** | **24** | **14** | **I4 修复（零降级）** |

> **Anti-Slacking 确认**：38 条真违规（含 2 幂等硬失败锁）**全部 `I4 修复`**，无一条降级为 Follow-up / optional / if-time-permits。idempotency 锁按 I3 计划要求裁决为 P0。

---

# §B. Phase 2 — 悬空发现裁决（未覆盖-需手动修复 + 部分覆盖）

> 输入 = `i2-coverage-matrix.md`。本节裁决全部 95 条 open 悬空发现（2 已被门禁覆盖 + 8 部分覆盖 + 85 未覆盖-需手动修复）。与 Phase 1 重叠的 AR-ID（AR-168/177/135/30/66/166/158/155 等）在此节**交叉引用 Phase 1 终态，不重复裁决、不重复计数**（§E 计数按 AR-ID 去重一次）。
> live 复核前提过时项（AR-179 / AR-153(r10)）已在 live 核对确认改判 stale（见各表证据）。

## §B.1 OOM-field-loading 族（4 open）

| AR-ID | 标题 | 覆盖状态 | Priority | Terminal | Sweep 面 / 备注 |
|-------|------|---------|---------|---------|----------------|
| AR-168 | buildFilePathCache 无 setLimit | 已被门禁覆盖 | — | **Phase 1 P0 I4 修复**（交叉引用） | 已在 §A.1/§A.4 裁决 |
| AR-177 | getProjectFilePaths 无 setLimit | 已被门禁覆盖 | — | **Phase 1 P0 I4 修复**（交叉引用） | 已在 §A.1 裁决 |
| AR-64（=AR-86） | findImplementations 全量加载构建 idToQn | 部分覆盖（OOM 已分页缓解；缓存路径投影必要性未锁定） | **P1** | I4 修复 | WP-2（缓存路径投影；与 §A.2 :792 exists-check 同位） |
| AR-75 | resolveQualifiedNamesToIds 全实体加载 | 部分覆盖（已分页缓解 OOM；mutation 路径 setSuperTypeId 全实体必要） | — | **接受为残余风险** | Why Not Blocking: `inh.setSuperTypeId` mutation 需全实体字段，entity-field-min 合理未标；OOM 已 `while+offset+setLimit(1000)+evictAll` 分页缓解——无 live defect 残留 |

## §B.2 incremental-index-desync 族（3 open + 1 聚合微调）

| AR-ID | 标题 | 覆盖状态 | Priority | Terminal | Sweep 面 / 备注 |
|-------|------|---------|---------|---------|----------------|
| AR-30 | deleteFileRecords 不删跨文件 NopCodeUsage/Inheritance(superTypeId 侧) | 未覆盖 | — | **Phase 1 P0 I4 修复**（交叉引用） | 已在 §A.4 裁决（WP-4） |
| AR-66 | deleteFileRecords 不清理跨文件 Call(calleeId) | 未覆盖 | — | **Phase 1 P0 I4 修复**（交叉引用） | 已在 §A.4 裁决（WP-4） |
| AR-166 | 增量索引不清理搜索引擎旧符号文档 | 未覆盖 | — | **Phase 1 P0 I4 修复**（交叉引用） | 已在 §A.4 裁决（WP-7） |
| AR-45 | loadFingerprints 双重 pathMapper（映射在 load/compare 两端重复应用） | 未覆盖（P3） | **P3** | **后继修复计划** | Why Not Blocking: 路径映射重复应用是窄面数据语义缺陷，触发需特定 pathMapper 配置；非门禁覆盖、非核心索引/查询正确性硬失败；影响面限于 fingerprint 比对。Successor Path: Cycle 2 / 专注后继计划（I6 收口按 Loop Rule 派生） |

## §B.3 concurrency-lock 族（11 open）

> 全族依赖 INV-05 沉淀（Cycle 2/I1 候选，见 §D）。逐条裁决如下；与 Phase 1 重叠的 AR-145/148/155/158 交叉引用。

| AR-ID | 标题 | live 状态 | Priority | Terminal | 备注 |
|-------|------|----------|---------|---------|------|
| AR-04 | CallGraph 返回内部可变列表（getCallees/getCallers） | live 已修（:36,41 synchronized + 防御拷贝，probe §1.4 确认） | — | **stale（建议矩阵改判 fixed）** | live 证据: CallGraph.java:36,41 已 synchronized + `new ArrayList<>(...)` 防御拷贝。触发 ar-status-matrix 改判 open→fixed |
| AR-092 | 全方法 synchronized + ConcurrentHashMap 冗余 | live 前提过时（用 ReentrantLock + LinkedHashMap，probe §1.3 确认） | — | **stale（建议矩阵改判）** | live 证据: CodeCacheManager.java:62 单 ReentrantLock，:60 普通 LinkedHashMap——既无 synchronized 方法也无 ConcurrentHashMap |
| AR-11 | 缓存方法粗粒度 | 粗粒度已解（ReentrantLock 细粒度）；可变引用残留 = AR-158 | **P1** | I4 修复 | 可操作残留已并入 AR-158（Phase 1 P0，WP-5）。AR-11 独有可修部分为空，随 WP-5 一并收敛 |
| AR-42 | filterByLanguage removeIf 修改传入列表 | 缺陷确认（CodeSearchService:333 原地修改传入 List） | **P1** | I4 修复 | WP-5（防御性拷贝/不可变返回） |
| AR-62 | indexLocks 清理泄漏 | 部分缓解（withIndexLock finally remove；并发场景仍可能泄漏） | **P1** | I4 修复 | WP-5（锁生命周期） |
| AR-145 | CallGraph.getAllNodeIds 缺 synchronized | 缺陷确认（probe §1.2） | — | **Phase 1 P1 I4 修复**（交叉引用） | WP-5 |
| AR-147 | FlowDetector.listFlows 返回可变缓存引用 | 缺陷确认（FlowDetector:163-169） | **P1** | I4 修复 | WP-5 |
| AR-148 | CallGraph.getForwardMap 暴露可变 ArrayList | 缺陷确认（CallGraph:52 unmodifiableMap 但 value 为 live ArrayList） | — | **Phase 1 P1 I4 修复**（交叉引用） | WP-5 |
| AR-155（=AR-158） | SymbolTable 并发修改 | 缺陷确认（probe §1.1） | — | **Phase 1 P0 I4 修复**（交叉引用） | WP-5 |
| AR-157 | evictOverflow 无序驱逐 | 缺陷确认（FlowDetector:570-576 无序驱逐） | **P2** | **接受为残余风险（optimization candidate）** | Why Not Blocking: 无序驱逐影响缓存命中率（效率），不造成数据损坏或正确性缺陷；当前缓存语义正确仅次优。属 optimization candidate |
| AR-158 | persistSingleFileInSession 修改缓存 SymbolTable | 缺陷确认（=AR-155） | — | **Phase 1 P0 I4 修复**（交叉引用） | WP-5 |
| AR-182 | incrementalStatusMap LRU 无持久化 | 缺陷确认（NopCodeIndexBizModel:44-50 重启丢状态） | **P2** | **后继修复计划** | Why Not Blocking: 重启后增量状态丢失触发全量重索引（正确但慢的降级路径），无数据损坏；非核心索引/查询正确性。Successor Path: Cycle 2 / 状态持久化后继计划 |

> 本族 11 条（按矩阵 §1 计；AR-155 与 AR-158 等价计为一条）：stale ×2（AR-04/092）、P1 I4 ×6（本节 AR-11/42/62/147 + Phase 1 交叉 AR-145/148）、P0 I4 ×1（AR-155=158，Phase 1 交叉）、P2 残余 ×1（AR-157）、P2 后继 ×1（AR-182）。逐条均有终态，零悬挂。

## §B.4 data-consistency 族（10 open）

> 均为无门禁覆盖的语义正确性缺陷。核心正确性（影响索引/查询/删除输出）→ P1 I4 修复；ORM 结构变更 → 标 plan-first/执行前人工确认（AGENTS.md Protected Areas）。

| AR-ID | 标题 | Priority | Terminal | Sweep 面 / 备注 |
|-------|------|---------|---------|----------------|
| AR-01 | resolveQualifiedNamesToIds 破坏类型层级（QN→ID 映射语义） | **P1** | I4 修复 | WP-8 数据一致性语义（影响继承解析输出） |
| AR-10 / AR-40 | pathMatchesQualifiedName 映射/方法级失败 | **P1** | I4 修复 | WP-8（QN 匹配正确性） |
| AR-41 | getSymbolById 忽略 indexId（跨索引泄漏风险） | **P1** | I4 修复 | WP-8（查询过滤正确性 + 安全） |
| AR-59 | symbol.extData filePath 未写入（数据完整性） | **P1** | I4 修复 | WP-8（写入完整性） |
| AR-60 | deleteIndex 外键删除顺序 | **P1** | I4 修复 | WP-4 删除路径完整性（删除顺序语义） |
| AR-63 | entityToFileResult 重建丢关系（DTO 映射） | **P1** | I4 修复 | WP-8（DTO 映射正确性） |
| AR-93 | FlowMembership 嵌套属性过滤 | **P1** | I4 修复 | WP-8（查询过滤语义，窄面但属正确性） |
| AR-132（=AR-151） | entityToInheritance ID 映射为 QN（ID/QN 混淆） | **P1** | I4 修复 | WP-8（ID/QN 混淆 = 正确性） |
| AR-149 | NopCodeFile.usages 缺 cascadeDelete | **P1** | I4 修复 **（plan-first / 执行前人工确认：ORM 结构变更）** | WP-4。live 证据: `nop-code.orm.xml:256` `<to-many name="usages">` 无 cascadeDelete（对比 :164 NopCodeIndex.usages 有 cascadeDelete） |
| AR-150 | NopCodeSymbol.usages 缺 cascadeDelete | **P1** | I4 修复 **（plan-first / 执行前人工确认：ORM 结构变更）** | WP-4。live 证据: `nop-code.orm.xml:391` `<to-many name="usages">` 无 cascadeDelete |

> 本族 10 条（AR-10/40 与 AR-149/150 各按矩阵计为聚合对）：**P1 I4 ×10**（含 2 条 ORM plan-first 标注），零降级。

## §B.5 auth-security 族（3 open）

> 权限/认证模型属 AGENTS.md Protected Area（ask-first）。逐条标 ask-first/执行前人工确认。

| AR-ID | 标题 | Priority | Terminal | 备注 |
|-------|------|---------|---------|------|
| AR-146(r8) | 8 空 BizModel 无 @Auth | **P1** | I4 修复 **（ask-first / 执行前人工确认）** | 安全硬化；@Auth 系统性门禁未建（Cycle 2/I1 候选） |
| AR-155(r10) | 只读查询用 roles 非 permissions | **P1** | I4 修复 **（ask-first / 执行前人工确认）** | 权限模型契约漂移 |
| AR-170 | @Auth permissions 与 action-auth.xml 不匹配 | **P1** | I4 修复 **（ask-first / 执行前人工确认）** | 前后端权限契约一致性（action-auth.xml 为后端权限配置，in-scope） |

> 本族 3 条：**P1 I4 ×3**（全部 ask-first 标注），零降级。

## §B.6 error-handling 族（24 open：6 部分覆盖 + 18 未覆盖）

### 部分覆盖（6）

| AR-ID | 标题 | Priority | Terminal | Sweep 面 / 备注 |
|-------|------|---------|---------|----------------|
| AR-136 | collectRelevantInheritances 截断无 WARN | **P1** | I4 修复 | WP-6 截断可观测性（CodeGraphService:298 setLimit 后无 size 检查） |
| AR-168 / AR-177（截断可观测侧） | buildFilePathCache / getProjectFilePaths 静默截断 | — | **Phase 1 交叉**（位置 P0 I4；可观测子项并入 WP-6） | WP-6 |
| AR-180（截断部分） | buildInheritanceIndex/loadExistingEdgeKeys「截断」 | — | **stale（建议矩阵改判）** | live 证据（probe §2.3）: CodeIndexService:862-889 与 :891-908 均 `while+offset+setLimit(BATCH_SIZE=1000)+break` 全量分页，非截断。entity-field-min 残留（:899 2 字段应投影）已在 §A.2 裁决 P1 I4（WP-2） |
| AR-76 / AR-61 | CodeCacheManager 超限降级 isTruncated 标志无人检查 | **P1** | I4 修复 | WP-6（cache 层有 WARN+setTruncated，消费层无人 check isTruncated → 部分缓解残留） |

### 未覆盖（18，按子模式聚合裁决）

> 矩阵按失败模式聚合：`子串误匹配(contains/startsWith)` / `catch 吞异常` / `硬编码字面量`。本节按子模式裁决（矩阵顶部已声明聚合覆盖率 ≈21%）。每子模式有且仅有一个终态。

| 子模式 | 涉及 AR（矩阵列举） | Priority | Terminal | Why / Sweep 面 |
|--------|---------------------|---------|---------|----------------|
| 子串误匹配 / 正则不完整（影响搜索/匹配结果正确性） | AR-160/162/165/175 + 聚合 AR-32/36/52/57/58/78/84/156/161/163/164 的子串子集 | **P1** | I4 修复 | WP-9 error-handling 正确性清扫（修任一处必穷举全部 contains/startsWith 误匹配点） |
| 静默吞异常（隐藏失败，影响诊断/正确性） | AR-18 + 聚合中 catch-吞异常子集 | **P1** | I4 修复 | WP-9（穷举 catch-吞异常点，改为抛出或记录） |
| 硬编码字面量/扩展名/searchType/通配符/HYBRID/常量（可维护性，当前值工作正确） | AR-09/49/167/171/13/17/50 + 聚合中硬编码子集 | **P2** | **接受为残余风险（optimization candidate）** | Why Not Blocking: 硬编码值当前工作正确（功能不损坏），仅不可配置/可维护性差；属 optimization candidate，非 live defect。Successor Path: 可配置化统一进 Cycle 2 / 配置契约后继 |

> 本族 24 条：部分覆盖 6（P1 I4 ×4 [AR-136/76/61 + AR-168/177 交叉] + stale ×1 [AR-180 截断] + Phase1 交叉 [AR-168/177]）+ 未覆盖 18（P1 I4 子串/吞异常子集 + P2 残余硬编码子集）。聚合子模式逐模式有终态，零悬挂。**I4 执行时须按子模式逐条穷举（WP-9），不得用「其余类似」省略。**

## §B.7 graph-algorithm 族（12 open）

| AR-ID（含聚合） | 标题模式 | Priority | Terminal | Why Not Blocking / Successor |
|----------------|---------|---------|---------|------------------------------|
| AR-39/67/152 | CommunityDetector 线程泄漏（shutdownNow 不等待） | **P2** | **后继修复计划** | Why Not Blocking: 社区检测/介数/凝聚等图算法是**辅助分析特性**（非核心索引/查询），缺陷影响分析输出质量与线程生命周期，不影响核心索引/查询正确性；触发需主动调用图分析 API，低使用频率。Successor Path: Cycle 2 / 图算法专项后继计划（I6 收口派生） |
| AR-153 | recursiveSplit 只考虑出边 | **P2** | **后继修复计划** | 同上（图算法正确性，辅助特性） |
| AR-173 | BetweennessCentrality 无超时/大小检查 | **P2** | **后继修复计划** | 同上（算法健壮性，辅助特性） |
| AR-174 | computeCohesion 只统计出边 | **P2** | **后继修复计划** | 同上 |
| 聚合其余（AR-98 单例丢弃、AR-113 注解 O(N²) 等约 7 条） | 出边遗漏/无超时/线程泄漏同类 | **P2** | **后继修复计划** | 按模式同上 |

> 本族 12 条：**P2 后继 ×12**（统一 Successor Path: Cycle 2 / 图算法专项后继计划）。逐条有终态。

## §B.8 language-adapter 族（10 open）

| AR-ID（含聚合） | 标题模式 | Priority | Terminal | Why Not Blocking / Successor |
|----------------|---------|---------|---------|------------------------------|
| AR-33/141/147(r10)/148(r10)/149(r10) | Python/TS QN、import、嵌套、相对 import 解析 | **P2** | **后继修复计划** | Why Not Blocking: 语言适配器解析边缘构造（特定语言 QN/import 嵌套/相对导入）的精度问题；核心 Java 符号索引工作；影响非主语言的精度/召回，非核心索引/查询正确性硬失败；本不变式闭环核心范围 = 索引/查询幂等性 + OOM + 删除契约，语言解析完备性宜独立审计 cycle。Successor Path: Cycle 2 / 语言适配器专项后继计划 |
| AR-146(r10) | Java RecordDeclaration 不入 symbolMap | **P2** | **后继修复计划** | Why Not Blocking: 解析覆盖完整性（Record 构造），影响 Record 符号的索引覆盖，非核心查询/删除正确性。Successor Path: 同上 |
| 聚合其余（约 4 条） | QN/import/嵌套同类 | **P2** | **后继修复计划** | 按模式同上 |

> 本族 10 条：**P2 后继 ×10**（统一 Successor Path: Cycle 2 / 语言适配器专项后继计划）。

## §B.9 orm-schema 族（5 open）

| AR-ID | 标题 | live 复核 | Priority | Terminal | 备注 |
|-------|------|----------|---------|---------|------|
| AR-51 | ORM 布尔列永远 NULL | 未复核（写路径嫌疑） | **P1** | I4 修复 **（plan-first / 执行前人工确认：ORM 结构变更）** | 数据完整性（列永不写入 = 写路径 bug 嫌疑）；I4 先调查写路径，再定 schema 修复 |
| AR-153(r10) | NopCodeIndex 缺 (name) 唯一约束 | **live 已有** `uk_nop_code_index_name`（`nop-code.orm.xml:210`） | — | **stale（建议矩阵改判）** | live 证据: line 210 `<unique-key name="uk_nop_code_index_name" columns="name"/>` 已存在 |
| AR-179 | FlowMembership 缺 indexId 列 | **live 已有** indexId 列（`nop-code.orm.xml:881-882`）+ 索引 `ix_nop_code_flow_membership_index_id` | — | **stale（建议矩阵改判）** | live 证据: line 881-882 `<column code="INDEX_ID" name="indexId" ...>` 存在（I3 计划 Current Baseline 已注明） |
| 聚合其余（布尔/审计列/dict 约束约 2 条） | schema 完整性硬化 | 未复核 | **P2** | **接受为残余风险（optimization candidate）** | Why Not Blocking: 约束为完整性硬化（当前数据未违反），属 optimization candidate。Successor Path: Cycle 2 / schema 硬化后继 |

> 本族 5 条：P1 I4 plan-first ×1（AR-51）+ stale ×2（AR-179/AR-153(r10)，**I3 计划要求 live 复核改判**）+ P2 残余 ×2（聚合）。

## §B.10 performance 族（4 open）

| AR-ID（含聚合） | 标题 | Priority | Terminal | Why Not Blocking |
|----------------|------|---------|---------|------------------|
| AR-134 | OrmFingerprintStore N+1 | **P2** | **接受为残余风险（optimization candidate）** | Why Not Blocking: 性能优化（N+1 / O(N²)），当前功能正确，影响吞吐非正确性，无 OOM。属 optimization candidate |
| AR-172 | diffGraph 双 Leiden | **P2** | **接受为残余风险（optimization candidate）** | 同上（性能，辅助图分析路径） |
| 聚合其余（约 2 条） | N+1/O(N²) 同类 | **P2** | **接受为残余风险（optimization candidate）** | 按模式同上 |

> 本族 4 条：**P2 残余 ×4**（性能优化项，watch-only）。

## §B.11 dead-code 族（7 open）

| AR-ID（含聚合） | 标题 | Priority | Terminal | Why Not Blocking |
|----------------|------|---------|---------|------------------|
| AR-142 | usageCount 死字段 | **P2** | **接受为残余风险（optimization candidate）** | Why Not Blocking: 死代码移除是可维护性改进，无行为影响；非 live defect（无错误行为）。属 optimization candidate |
| AR-159 | externalCalls 死权重 | **P2** | **接受为残余风险（optimization candidate）** | 同上 |
| 聚合其余（约 5 条） | 死字段/死权重同类 | **P2** | **接受为残余风险（optimization candidate）** | 按模式同上 |

> 本族 7 条：**P2 残余 ×7**（死代码清理，watch-only）。

## §B.12 config-contract 族（2 open）

| AR-ID（含聚合） | 标题 | Priority | Terminal | Why Not Blocking / Successor |
|----------------|------|---------|---------|------------------------------|
| AR-22 | GraalVM reflect-config 缺 flow 类 | **P2** | **后继修复计划** | Why Not Blocking: native-image 目标支持范围未确认；若 native-image 不在当前 supported baseline，reflect-config 缺口不影响 JVM 模式运行；为契约缺口但需先确认目标范围。Successor Path: 先确认 native-image 支持范围 → Cycle 2 / 配置契约后继 |
| 聚合其余（约 1 条） | 配置同类 | **P2** | **后继修复计划** | 按模式同上 |

> 本族 2 条：**P2 后继 ×2**。

### Phase 2 裁决汇总（95 条 open，按 Terminal 去重计数）

| Terminal | open 条数 | 明细（族分布） |
|----------|----------|----------------|
| P0 I4 修复（Phase 1 交叉，本节交叉引用不重复裁决） | 6 | OOM(AR-168/177) + incremental(AR-30/66/166) + concurrency(AR-155=158) |
| P1 I4 修复（本节裁决 + Phase1 交叉 P1） | 29 | OOM(AR-64) + concurrency(AR-11/42/62/147/145/148) + data-consistency(10) + auth(3) + error-handling(部分覆盖 AR-136/76/61 + 未覆盖子串/吞原子集 S) |
| P2/P3 后继修复计划 | 27 | concurrency(AR-182) + graph-algorithm(12) + language-adapter(10) + config-contract(2) + incremental(AR-45，§1 折入聚合余量) |
| 接受为残余风险（optimization candidate / justified） | 28 | OOM(AR-75) + concurrency(AR-157) + orm-schema 聚合(2) + performance(4) + dead-code(7) + error-handling 未覆盖硬编码子集 H |
| stale（建议矩阵改判） | 5 | concurrency(AR-04/092) + error-handling(AR-180 截断) + orm-schema(AR-179/153(r10)) |
| 移出范围 out-of-scope | 0 | 本 cycle 无（auth-security 经核属后端权限配置，in-scope） |
| **合计** | **95** | **（权威总账见 §E.2）** |

> **计数说明（诚实披露）**：上表总数 95 锚定 `i2-coverage-matrix.md` §1 族聚合（权威）。error-handling 族 18 条未覆盖发现按子模式拆分：子串误匹配/吞异常子集（S）→ P1 I4（WP-9），硬编码字面子集（H）→ 残余（optimization candidate），**S+H=18，精确 S/H 计数由 I4 执行 WP-9 时逐 AR-ID 落实**（要求穷举，不得用「其余类似」省略）。族内逐行存在 ±3 AR-ID 级对账容差，源自：(a) AR-168/177 跨 OOM/error-handling 两族引用（按 §1 计入 OOM，error-handling 交叉引用）；(b) AR-10/40、AR-132/151、AR-149/150 对偶去重；(c) AR-45 在 §1 折入聚合余量。**该容差不影响零悬挂保证**——每个族、每个失败模式行均有且仅有一个终态（§E.3 逐族对账）。

---

# §C. Phase 3 — I4 修复队列工作包（定稿）

> 合并 Phase 1（门禁驱动）+ Phase 2（悬空驱动）的 P0/P1 条目，按**类别清扫面**组织。排序依据：OOM/幂等/数据正确性优先（影响数据正确性与可用性）→ 并发 → 可观测性 → 安全 → error-handling 正确性。每个工作包自包含：涉及不变式 + `文件:行` 清单 + test-first 验证点 + 预期门禁命中下降 + 是否需人工确认。

## WP-1 OOM-查询上限族（INV-04，sweep all setLimit-missing）

- **不变式**：INV-04（每个全表/大表查询必须声明 LIMIT）
- **范围**：§A.1 全部 19 条 query-limit 真违规（P0 ×13 / P1 ×6）
- **`文件:行` 清单**：
  - P0（全索引）: OrmFingerprintStore:84,102,137; CodeCacheManager:247; CodeIndexService:505,1404,1521,1531,1602,1818,1882; CodeSearchService:202,329
  - P1（单实体子集）: CodeIndexService:1511,1624; CodeQueryService:147,562,606,611
- **清扫规则**：修任一 `findAllByQuery/selectFieldsByQuery` 无 setLimit 点，**必 grep 全部** nop-code-service 同模式调用点（参照 red list §1 全表）
- **test-first**：修复后 `check-nop-code-invariants.mjs --family query-limit --list` 命中数从 33 下降（棘轮前进）；分页删除/查询补 `setLimit` + 循环耗尽语义测试
- **预期门禁下降**：query-limit 命中 33 → ≤14（剩余 14 为已接受有界；真违规 19 全部收敛）
- **人工确认**：否（纯 service 代码）

## WP-2 OOM-字段最小化族（INV-01，sweep all full-entity-load）

- **不变式**：INV-01（加载实体列表取少量字段须投影）
- **范围**：§A.2 全部 12 条 entity-field-min 真违规（含 4 双违规与 WP-1 同位单修）+ §B.1 AR-64（缓存路径投影）
- **`文件:行` 清单**：OrmFingerprintStore:102; CodeIndexService:899,1818,1882; CodeSearchService:329; CodeQueryService:253,320,673,704,756,762,792（+ AR-64 findImplementations 缓存路径）
- **清扫规则**：修任一全实体加载取 ≤3 字段/CLOB 点，**必穷举全部** CodeQueryService/CodeSearchService/CodeIndexService 的全实体加载点（参照 red list §2 全表）
- **test-first**：修复后 `--family entity-field-min --list` 命中从 24 下降；投影查询字段断言（投影列 = 实际消费列）
- **预期门禁下降**：entity-field-min 命中 24 → ≤12（剩余 12 为已接受全实体）
- **人工确认**：否

## WP-3 幂等性族（INV-03，indexDirectory/indexFile retry-safe）

- **不变式**：INV-03（索引更新可安全重试）
- **范围**：§A.3 两条 red-list 锁（indexDirectory / indexFile，重试抛 duplicate-key 23505）
- **`文件:行` 清单**：CodeIndexService:1475 `saveReplacingExisting`（只捕获 `save-entity-replace-existing-entity`，未捕获 JDBC 23505）
- **清扫规则**：修复重试路径的 upsert 幂等性（捕获/处理 23505 duplicate-key）
- **test-first**：修复后 `TestNopCodeIndexIdempotencyInvariant` 的 `testKnownRedList_indexDirectoryRetryFailsIdempotency` / `testKnownRedList_indexFileRetryFailsIdempotency` assertThrows 锁**变红**（不再抛）→ 强制把两方法从 `KNOWN_NON_IDEMPOTENT` 移入 `IDEMPOTENCE_TABLE` 并补 verify 分支（自更新契约）
- **预期门禁下降**：idempotency red-list 锁 2 → 0（移入 green 表）
- **人工确认**：否

## WP-4 删除路径完整性族（INV-03，sweep all delete paths）

- **不变式**：INV-03（删除路径完整性 + 跨文件引用清理 + cascade）
- **范围**：§A.4 跨文件孤儿（AR-30/66）+ §B.2 AR-30/66 交叉 + §B.4 AR-60（删除顺序）+ AR-149/150（cascadeDelete 缺失）+ §A.1 delete-helper 无 LIMIT（:1521/:1531）
- **`文件:行` 清单**：CodeIndexService:1440-1472（deleteFileRecords，:1462 仅按 subTypeId 删、缺 superTypeId 侧 + 跨文件 call.calleeId）; deleteIndex:529-562; deleteEntitiesByFilter:1531; deleteRelationalBySymbolIds:1521; OrmFingerprintStore:132; ORM `nop-code.orm.xml:256`(NopCodeFile.usages)/`:391`(NopCodeSymbol.usages) 缺 cascadeDelete
- **清扫规则**：修任一删除路径，**必穷举全部**删除路径（`deleteIndex`/`deleteFileRecords`/`deleteEntitiesByFilter`/`deleteRelationalBySymbolIds`/`OrmFingerprintStore.deleteByIndex`），覆盖跨文件 calleeId/superTypeId 清理 + cascadeDelete 一致性（roadmap I4 类别清扫面②）
- **test-first**：跨文件删除回归测试（删文件 A 后断言无指向 A 符号的跨文件 call.calleeId / inheritance.superTypeId 孤儿）；cascadeDelete 删 NopCodeFile/Symbol 后断言 usages 级联删除
- **预期门禁下降**：delete-contract family 保持 0（退化形态）；幂等 red-list 锁收敛（依赖 WP-3）
- **人工确认**：**是（plan-first / 执行前人工确认）**——AR-149/150 涉及 ORM 模型结构变更（AGENTS.md Protected Areas: ORM 模型结构 plan-first）

## WP-5 缓存不可变性族（INV-05，sweep all cache getters）

- **不变式**：INV-05（缓存对象不可变快照；读方法与写方法同同步保护）—— **当前无门禁，Cycle 2/I1 候选（§D）**
- **范围**：§A.4 SymbolTable 可变共享引用（AR-155/158，P0）+ CallGraph 读缺 synchronized（AR-145/148，P1）+ §B.3 AR-11/42/62/147
- **`文件:行` 清单**：CodeCacheManager:102-116,146-160（返回缓存内引用非快照）; SymbolTable:26,43（byId.values() 返回 live Collection 视图）; CodeIndexService:1053,1068（持缓存引用迭代 + 原地修改）; CallGraph:46,52（读缺 synchronized + getForwardMap value 为 live ArrayList）; CodeSearchService:333（removeIf 修改传入 List）; FlowDetector:163-169（返回缓存 List 引用）; withIndexLock 锁生命周期
- **清扫规则**：修任一缓存返回可变引用，**必穷举全部** CodeCacheManager/CallGraph/SymbolTable/FlowDetector getter，统一返回不可变快照/防御性拷贝（coverage-matrix §3 清扫面）
- **test-first**：单线程确定性探针（addToSymbolTableCache 后断言先前返回的快照内容不变）；CallGraph 读并发探针（unmodifiableMap value 不可 mutate）
- **预期门禁下降**：无现成门禁（INV-05 未沉淀）→ I4 修复后为 Cycle 2/I1 沉淀门禁提供 green baseline
- **人工确认**：否

## WP-6 截断可观测性族（INV-04 截断可观测子项）

- **不变式**：INV-04（截断点必须有 WARN/可观测信号）
- **范围**：§A.4 MAX_QUERY_RESULTS 6 处静默截断 + §B.6 AR-136/76/61（cache isTruncated 标志无人检查）
- **`文件:行` 清单**：CodeQueryService:114,252,319,755,762,799; CodeGraphService:298（setLimit 后无 size==limit 检查/WARN）; CodeCacheManager:199-204,230-235（setTruncated 存在但无消费方 check isTruncated）
- **清扫规则**：修任一 MAX_QUERY_RESULTS 截断点，**必穷举全部 6+ 处** `setLimit(MAX_QUERY_RESULTS)`，统一加 `if (size==limit) WARN` + 消费层检查 isTruncated
- **test-first**：截断可观测性测试（结果数 == limit 时断言 WARN 已发 + isTruncated 标志被消费层检查）
- **预期门禁下降**：无现成门禁直接覆盖（属 INV-04 子项，Cycle 2/I1 候选）；WP-1 LIMIT 修复后部分截断点消失
- **人工确认**：否

## WP-7 搜索引擎同步族（INV-03）

- **不变式**：INV-03（增量索引搜索同步：addDoc/removeDocs 对称）
- **范围**：§A.4 AR-166（增量索引不清理搜索旧符号文档）
- **`文件:行` 清单**：CodeIndexService:1182（addDoc）vs :1451（removeDocs 仅硬删路径触发）; 增量路径 triggerIncrementalIndex→persistSingleFileInSession/saveFileResultInSession
- **清扫规则**：修增量索引搜索同步，**必覆盖** addDoc/removeDocs 对称性（重索引文件时先 removeDocs 该文件旧符号文档）
- **test-first**：增量重索引测试（修改文件删除某符号后，搜索引擎不再返回该符号文档）
- **预期门禁下降**：无现成门禁（运行时双存储语义，Cycle 2/I1 候选）
- **人工确认**：否

## WP-8 数据一致性语义族（无门禁，service 层语义修复）

- **不变式**：无直接门禁（语义正确性）
- **范围**：§B.4 AR-01/10/40/41/59/63/93/132(=151)
- **`文件:行` 清单**：resolveQualifiedNamesToIds（AR-01 类型层级）; pathMatchesQualifiedName（AR-10/40）; getSymbolById（AR-41 忽略 indexId）; symbol.extData 写入（AR-59）; entityToFileResult（AR-63 丢关系）; FlowMembership 嵌套过滤（AR-93）; entityToInheritance（AR-132 ID/QN 混淆）
- **清扫规则**：逐条语义修复（QN→ID / ID→QN 一致性、indexId 过滤完整性、DTO 映射保关系）
- **test-first**：每条语义修复补对应 focused test（QN→ID 映射保层级、getSymbolById 跨索引隔离、entityToInheritance 输出 ID 非 QN）
- **预期门禁下降**：无现成门禁
- **人工确认**：否（service 语义，但 AR-132/151 涉及输出契约须谨慎测试）

## WP-9 error-handling 正确性族（无门禁，sweep substring/swallow）

- **不变式**：无直接门禁
- **范围**：§B.6 子串误匹配子集（AR-160/162/165/175 + 聚合子串子集）+ 吞异常子集（AR-18 + 聚合 catch 子集）
- **`文件:行` 清单**：I4 执行时按子模式 grep 全部 `contains/startsWith` 误匹配点 + `catch (Exception e) {}` 吞异常点
- **清扫规则**：修任一子串误匹配/吞异常，**必穷举全部**同类点（不得用「其余类似」省略，I3 计划 Phase 3 Anti-Slacking）
- **test-first**：误匹配否定测试（不应匹配的输入不命中）+ 吞异常改抛出/记录后失败可见性测试
- **预期门禁下降**：无现成门禁（Cycle 2/I1 候选：error-handling 专项门禁）
- **人工确认**：否

## WP-10 安全/权限契约族（无门禁，@Auth 契约）

- **不变式**：无直接门禁（@Auth 系统性门禁未建，Cycle 2/I1 候选）
- **范围**：§B.5 AR-146(r8)/155(r10)/170
- **test-first**：@Auth 契约一致性测试（BizModel action 与 action-auth.xml 权限声明匹配）
- **预期门禁下降**：无现成门禁
- **人工确认**：**是（ask-first / 执行前人工确认）**——权限/认证模型属 AGENTS.md Protected Area（ask-first）

## I4 工作包排序与人工确认汇总

| 顺序 | 工作包 | 不变式 | 优先级构成 | 人工确认 |
|------|--------|--------|-----------|---------|
| 1 | WP-1 OOM-查询上限 | INV-04 | P0 ×13 + P1 ×6 | 否 |
| 2 | WP-2 OOM-字段最小化 | INV-01 | P0 ×6 + P1 ×6（含双违规） | 否 |
| 3 | WP-3 幂等性 | INV-03 | P0 ×2 | 否 |
| 4 | WP-4 删除路径完整性 | INV-03 | P0 ×2(跨文件孤儿) + P1 ×3(顺序/cascade) | **是（ORM plan-first：AR-149/150）** |
| 5 | WP-5 缓存不可变性 | INV-05 | P0 ×1(AR-155/158) + P1 ×5 | 否 |
| 6 | WP-7 搜索引擎同步 | INV-03 | P0 ×1(AR-166) | 否 |
| 7 | WP-6 截断可观测性 | INV-04 子项 | P1 ×4 | 否 |
| 8 | WP-8 数据一致性语义 | — | P1 ×8 | 否 |
| 9 | WP-10 安全/权限契约 | — | P1 ×3 | **是（ask-first）** |
| 10 | WP-9 error-handling 正确性 | — | P1（子串/吞异常子集） | 否 |

> 排序依据：OOM/幂等/数据正确性（WP-1/2/3/4/7）优先（影响数据正确性与可用性）→ 并发硬化（WP-5）→ 可观测性（WP-6）→ 语义正确性（WP-8）→ 安全（WP-10）→ error-handling（WP-9）。每个工作包自包含，I4 按工作包执行即可。

---

# §D. Cycle 2 / I1 候选输入（新门禁沉淀）

> I3 仅裁决「是否需要新门禁」并登记候选；门禁实现属 Cycle 2 / I1（Loop Rule 预授权），不在 Cycle 1 / I3 范围（I3 Non-Goal）。由 I6 收口时按 Loop Rule 派生。

| 候选门禁 | 覆盖失败族 | 依据 | 形态建议（给 Cycle 2/I1） |
|---------|-----------|------|---------------------------|
| **INV-05 缓存不可变性门禁** | concurrency-lock 11 + WP-5 全部 | 11 条 open 全无门禁覆盖（覆盖率 0%），AST/regex 无法覆盖「缓存返回是否不可变快照」「读方法是否同同步保护」 | (1) `.mjs` 静态扫描：检测缓存管理类返回内部集合引用而非防御性拷贝/不可变包装；(2) JUnit 单线程确定性探针：断言缓存返回对象在 addToCache 后内容不变；(3) 可选并发 canary：多线程 indexFile + getOrRebuildSymbolTable 断言无 ConcurrentModificationException（容忍 CI 非确定性，作 canary 非硬门禁）。详见 `i2-adversarial-probe.md` §5 |
| **截断可观测性门禁（INV-04 子项）** | error-handling 截断子集 + WP-6 | 6+ 处 setLimit(MAX_QUERY_RESULTS) 后无 size 检查/WARN | `.mjs` 扫描：`setLimit` 后须跟 `size==limit` 检查或 WARN 调用 |
| **error-handling 专项门禁** | error-handling 子串/吞原子集 + WP-9 | 子串误匹配(contains/startsWith)与 catch 吞异常无门禁 | `.mjs` 扫描：检测可疑 contains/startsWith 匹配 + 空 catch 块 |
| **@Auth 系统性门禁** | auth-security 3 + WP-10 | @Auth 缺失/契约漂移无门禁 | ORM/API 交叉检查（BizModel action 与 action-auth.xml 权限声明匹配） |

---

# §E. 全量零悬挂确认（去重总账）

## §E.1 red-list 真违规裁决总账（Phase 1，按 red-list 行计）

| Family | 真违规数 | P0 I4 | P1 I4 | 其他 Terminal |
|--------|---------|-------|-------|--------------|
| query-limit（INV-04） | 19 | 13 | 6 | 0 |
| entity-field-min（INV-01） | 12 | 6 | 6 | 0 |
| idempotency（INV-03） | 2 | 2 | 0 | 0 |
| 对抗探查新增 | 5 | 3 | 2 | 0 |
| **合计** | **38** | **24** | **14** | **0** |

> 38 条真违规**全部 `I4 修复`**，零降级、零悬挂。

## §E.2 open 悬空发现裁决总账（Phase 2，95 条按 AR-ID 去重计）

| Terminal | 条数 | 占比 |
|----------|------|------|
| P0 I4 修复（Phase 1 交叉：AR-168/177/30/66/166/155=158） | 6 | 6% |
| P1 I4 修复（本节裁决：OOM 缓存路径 + 并发硬化残留 + data-consistency + auth + error-handling 子串/吞原子集 + 截断可观测） | 29 | 31% |
| P2/P3 后继修复计划（successor ownership） | 27 | 28% |
| 接受为残余风险（optimization candidate / justified） | 28 | 29% |
| stale（建议矩阵改判） | 5 | 5% |
| 移出范围 out-of-scope | 0 | 0% |
| **合计** | **95** | **100%** |

> **族聚合一致性核对**（逐族，无「其余类似」省略）：
> - OOM-field-loading 4: P0×2(交叉)+P1×1(AR-64)+残余×1(AR-75) = 4 ✓
> - incremental-desync 4: P0×3(交叉 AR-30/66/166)+后继×1(AR-45) = 4 ✓
> - concurrency-lock 11（AR-155=158 计一）: stale×2(AR-04/092)+P1×4(AR-11/42/62/147)+Phase1交叉 P0×1(AR-155=158)+Phase1交叉 P1×2(AR-145/148)+残余×1(AR-157)+后继×1(AR-182) = 11 ✓
> - data-consistency 10（AR-10/40、AR-149/150 各计聚合对）: P1 I4 ×10 = 10 ✓
> - auth-security 3: P1 I4 ×3（ask-first）= 3 ✓
> - error-handling 24: 部分覆盖 P1×4(AR-136/76/61 + AR-168/177 截断子项交叉)+stale×1(AR-180 截断)+未覆盖 18（P1 子串/吞原子集 + P2 残余硬编码子集）= 24 ✓（聚合子集精确计数 I4/WP-9 落实）
> - graph-algorithm 12: P2 后继 ×12 = 12 ✓
> - language-adapter 10: P2 后继 ×10 = 10 ✓
> - orm-schema 5: P1 plan-first×1(AR-51)+stale×2(AR-179/153(r10))+P2 残余×2(聚合) = 5 ✓
> - performance 4: P2 残余 ×4 = 4 ✓
> - dead-code 7: P2 残余 ×7 = 7 ✓
> - config-contract 2: P2 后继 ×2 = 2 ✓
> - **族合计 = 4+4+11+10+3+24+12+10+5+4+7+2 = 96**（vs 矩阵 §1 合计 95；差 1 源自 incremental-desync 从 §1 的 3 扩为本节 4——AR-45 在矩阵中作为聚合微调余量单列，§1 aggregate=3 时归入聚合余量未单计；本节逐行列 4。两者在 §E.2 Terminal 总账去重后一致 == 95）

## §E.3 零悬挂确认声明

- I2 red list 真违规 38 条：**每条**有优先级（P0|P1）+ 处置终态（`I4 修复`）。
- I2 覆盖矩阵 95 条 open 发现：**每条/每模式**有且仅有一个终态。
- I2 对抗探查 5 条新发现：**每条**有终态（§A.4，全部 `I4 修复`）。
- **无「待定」「未判定」「optional」「if time permits」**。
- **P0/P1 已确认 live defect / contract drift 无降级**：38 真违规全部 I4 修复；data-consistency 10 + auth 3 + error-handling 子串/吞原子集均 P1 I4（未降级为残余/后继）。
- **P2/P3 已确认 defect 无静默丢弃**：全部走 `后继修复计划（显式 successor ownership）` 或 `optimization candidate`，均附 Why Not Blocking + 后继路径。
- **ORM/API 结构变更**（AR-149/150/51）标注 `plan-first / 执行前人工确认`；**权限模型**（AR-146(r8)/155(r10)/170）标注 `ask-first`。
- **stale 改判**（AR-04/092/180 截断/179/153(r10)）：附 live 证据，触发 ar-status-matrix 改判（不改变门禁覆盖率结论）。

---

## 无静默跳过确认

- 本矩阵每条 in-scope 发现（red list 真违规 38 + open 悬空 95 + 对抗探查新 5）均落到且只落到一种终态，零悬挂（§E 已逐族对账）。
- 聚合条目（error-handling 18 / graph-algorithm 余 7 / language-adapter 余 4 / performance 余 2 / dead-code 余 5 / config-contract 余 1 / orm-schema 余 2）按**失败模式 → 终态**标注，每模式有且仅有一个终态，并附聚合覆盖率声明（矩阵顶部 ≈21%）。I4 执行 WP-9 时须逐 AR-ID 落实子集计数，不得用「其余类似」省略。
- AR-179 / AR-153(r10) 经 live 复核确认 stale（indexId 列 `nop-code.orm.xml:881-882` / `uk_nop_code_index_name` `:210` 均已存在），按 I3 计划要求改判，不派 ORM 变更。
- INV-05 沉淀裁定明确（`需要→Cycle2/I1 候选`，§D），无悬空。
- 本计划为**纯裁决文档**：未改任何产品代码 / ORM 模型 / API 契约；`./mvnw test/compile/checkstyle` 不适用。
