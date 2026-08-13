# nop-code 不变式闭环 I2 — 悬空发现覆盖矩阵

> Status: active
> Last Reviewed: 2026-08-13
> Source: I2 计划 `ai-dev/plans/2026-08-13-0806-3-nop-code-invariant-i2-invariant-driven-audit.md` Phase 3
> 输入: `ar-status-matrix.md` 的 95 条 `open` 发现 + I1 四族门禁（INV-01..04）+ `i2-adversarial-probe.md` 结论
> 后继: I3 裁决消费本矩阵作为「悬空发现处置输入」

## 目的

对 `ar-status-matrix.md` 全部 95 条 `open` 发现逐条/按模式标注门禁覆盖状态（`已被门禁覆盖` | `部分覆盖` | `未覆盖-需手动修复`），作为 I3 裁决「哪些悬空发现已被门禁锁定、哪些仍需手动修复」的确定性输入。

## 覆盖状态定义

- **已被门禁覆盖**：某 I1 门禁（INV-01..04 family）会在该缺陷回归时直接退出非零（红）。即门禁锁定了该位置。
- **部分覆盖**：门禁覆盖了缺陷的某一面（如 OOM 的分页缓解、截断位置的被标记），但另一面（如可观测性、投影必要性）未被锁定。
- **未覆盖-需手动修复**：缺陷属运行时语义 / 跨文件语义 / 无对应门禁族，四族静态门禁不触及，须 I4 手动修复或 Cycle 2 新门禁。

## 枚举完备性声明

`ar-status-matrix.md` 族聚合表 open 合计 = **95**（已交叉核对）。其中：
- **约 75 条逐条单列**（OOM / incremental-desync / concurrency-lock / data-consistency / auth-security 主体 + 各族选列代表）——本矩阵逐 AR-ID 标注。
- **约 20 条以聚合形式描述**（error-handling「约 18 条」、graph-algorithm/language-adapter/orm-schema 选列外余量）——按**失败模式 → 覆盖状态**标注，矩阵顶部注明聚合覆盖率。

**聚合覆盖率**：聚合条目数（≈20）/ 总 open（95）= ≈21%。逐条展开的 ≈75 条覆盖率与聚合条目按模式标注的覆盖率合并后，族聚合统计与逐条计数一致（见 §2）。

---

## §1 族聚合覆盖统计（门禁覆盖率）

| 失败族 | open 计 | 已被门禁覆盖 | 部分覆盖 | 未覆盖-需手动修复 | 主覆盖门禁 | 覆盖率（已覆盖+部分）/open |
|--------|--------|------------|---------|-----------------|-----------|-------------------------|
| OOM-field-loading | 4 | 2 | 2 | 0 | INV-04 query-limit / INV-01 | 100% |
| incremental-index-desync | 3 | 0 | 0 | 3 | （无；INV-03 仅覆盖幂等性） | 0% |
| concurrency-lock | 11 | 0 | 0 | 11 | （无；INV-05 未沉淀） | 0% |
| data-consistency | 10 | 0 | 0 | 10 | （无；语义正确性） | 0% |
| auth-security | 3 | 0 | 0 | 3 | （无；@Auth 门禁未建） | 0% |
| error-handling | 24 | 0 | 6 | 18 | INV-04（仅截断位置） | 25% |
| graph-algorithm | 12 | 0 | 0 | 12 | （无） | 0% |
| language-adapter | 10 | 0 | 0 | 10 | （无） | 0% |
| orm-schema | 5 | 0 | 0 | 5 | （无；INV-02 仅查 useLogicalDelete） | 0% |
| performance | 4 | 0 | 0 | 4 | （无） | 0% |
| dead-code | 7 | 0 | 0 | 7 | （无） | 0% |
| config-contract | 2 | 0 | 0 | 2 | （无） | 0% |
| **合计** | **95** | **2** | **8** | **85** | — | **11%** |

> **核心结论**：I1 四族门禁直接锁定的 open 发现仅 2 条（AR-168/177 的 no-LIMIT 残留），部分覆盖 8 条；**85 条（89%）open 发现无门禁覆盖**，须 I4 手动修复或 Cycle 2 新门禁。门禁当前覆盖面集中在 OOM/查询上限族；并发（INV-05）、数据一致性、错误处理、图算法、语言适配、ORM schema、性能、死代码、配置契约九大族尚无任何可执行门禁。

---

## §2 逐条覆盖矩阵（单列 open 发现）

### OOM-field-loading 族（4 open）

| AR-ID | 标题 | 覆盖状态 | 覆盖门禁 / 未覆盖原因 |
|-------|------|---------|---------------------|
| AR-168（=AR-135/154） | buildFilePathCache 无 setLimit | **已被门禁覆盖** | INV-04 query-limit 直接标记 `CodeSearchService.java:202`（red list 真违规）；投影已修，LIMIT 残留被门禁锁定 |
| AR-177（=AR-87） | getProjectFilePaths 无 setLimit | **已被门禁覆盖** | INV-04 query-limit 直接标记 `CodeIndexService.java:1404`（red list 真违规） |
| AR-64（=AR-86） | findImplementations 全量加载构建 idToQn | **部分覆盖** | INV-04：load 路径 `rebuildSymbolTable` 已 `setLimit(BATCH_SIZE=5000)` 分页缓解 OOM；但 findImplementations 走缓存 + `symbolTable.getAll()` 构建 idToQn，entity-field-min 未标记此缓存路径（converter 方法引用）——OOM 缓解但投影必要性未锁定 |
| AR-75 | resolveQualifiedNamesToIds 全实体加载 | **部分覆盖** | INV-04：已 `while+offset+setLimit(BATCH_SIZE=1000)+evictAll` 分页缓解 OOM；mutation 路径（`inh.setSuperTypeId`）全实体必要，entity-field-min 合理未标——OOM 缓解 |

### incremental-index-desync 族（3 open，逐列 4 条含 1 聚合微调）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 |
|-------|------|---------|-----------|
| AR-30 | deleteFileRecords 不删跨文件 NopCodeUsage | **未覆盖-需手动修复** | 跨文件清理语义（calleeId/superTypeId 引用），静态门禁无法判定跨表引用完整性；adversarial-probe §3.1 确认 |
| AR-66 | deleteFileRecords 不清理跨文件 Call 引用 | **未覆盖-需手动修复** | 同上（跨文件 NopCodeCall.calleeId 孤儿）；adversarial-probe §3.1 |
| AR-166 | 增量索引不清理搜索引擎旧符号文档 | **未覆盖-需手动修复** | 搜索引擎与 DB 去同步（运行时双存储语义），无门禁；adversarial-probe §3.2 |
| AR-45（P3） | loadFingerprints 双重 pathMapper | **未覆盖-需手动修复** | 路径映射语义（映射在 load/compare 两端重复应用），无门禁（矩阵 aggregate=3 时本条归入聚合余量） |

### concurrency-lock 族（11 open）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 / 备注 |
|-------|------|---------|-----------------|
| AR-04 | CallGraph 返回内部可变列表 | **未覆盖**（但 live 已修，建议矩阵改判 fixed） | live `getCallees/getCallers`（CallGraph.java:36,41）已 synchronized + 防御性拷贝；adversarial-probe §1.2 确认已修。矩阵标 open 与 live 不符 |
| AR-11 | 缓存方法粗粒度 | **未覆盖-需手动修复** | 粗粒度已解（ReentrantLock），可变引用残留（AR-158）；INV-05 未沉淀 |
| AR-42 | filterByLanguage removeIf 改传入列表 | **未覆盖-需手动修复** | 原地修改语义；INV-05 未沉淀 |
| AR-62 | indexLocks 清理泄漏 | **未覆盖-需手动修复** | 并发锁生命周期；无门禁 |
| AR-92 | 全方法 synchronized + ConcurrentHashMap 冗余 | **未覆盖**（stale-premise，建议改判） | live 既无 synchronized 方法也无 ConcurrentHashMap（用 ReentrantLock + LinkedHashMap）；adversarial-probe §1.3 |
| AR-145 | CallGraph.getAllNodeIds/getForwardMap 缺 synchronized | **未覆盖-需手动修复** | INV-05 未沉淀；adversarial-probe §1.2 确认 open |
| AR-147 | FlowDetector.listFlows 返回可变缓存引用 | **未覆盖-需手动修复** | INV-05 未沉淀 |
| AR-148 | CallGraph.getForwardMap 暴露可变 ArrayList | **未覆盖-需手动修复** | INV-05 未沉淀；adversarial-probe §1.2 |
| AR-155（=AR-158） | SymbolTable 并发修改 | **未覆盖-需手动修复** | INV-05 未沉淀；adversarial-probe §1.1 |
| AR-157 | evictOverflow 无序驱逐 | **未覆盖-需手动修复** | INV-05 未沉淀 |
| AR-158 | persistSingleFileInSession 修改缓存 SymbolTable | **未覆盖-需手动修复** | INV-05 未沉淀；adversarial-probe §1.1（=AR-155） |
| AR-182 | incrementalStatusMap LRU 无持久化 | **未覆盖-需手动修复** | 状态持久化；无门禁 |

> 本族 11 条全部依赖 INV-05 沉淀（Cycle 2 / I1 候选）。其中 AR-04 实测已修、AR-092 实测 stale，建议 I3 触发矩阵改判（不改变门禁覆盖率结论）。

### data-consistency 族（10 open）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 |
|-------|------|---------|-----------|
| AR-01 | resolveQualifiedNamesToIds 破坏类型层级 | **未覆盖-需手动修复** | QN→ID 映射语义正确性；无门禁 |
| AR-10 / AR-40 | pathMatchesQualifiedName 映射/方法级失败 | **未覆盖-需手动修复** | 路径匹配语义；无门禁 |
| AR-41 | getSymbolById 忽略 indexId | **未覆盖-需手动修复** | 查询过滤语义；无门禁 |
| AR-59 | symbol.extData filePath 未写入 | **未覆盖-需手动修复** | 数据写入完整性；无门禁 |
| AR-60 | deleteIndex 外键删除顺序 | **未覆盖-需手动修复** | 删除顺序语义（INV-02 仅查物理/逻辑删除，不查顺序）；adversarial-probe §3.1 关联 |
| AR-63 | entityToFileResult 重建丢关系 | **未覆盖-需手动修复** | DTO 映射语义；无门禁 |
| AR-93 | FlowMembership 嵌套属性过滤 | **未覆盖-需手动修复** | 查询过滤语义；无门禁 |
| AR-132（=AR-151） | entityToInheritance ID 映射为 QN | **未覆盖-需手动修复** | ID/QN 混淆语义；无门禁 |
| AR-149 / AR-150 | NopCodeFile/Symbol.usages 缺 cascadeDelete | **未覆盖-需手动修复** | ORM cascade 完整性（INV-02 仅查 useLogicalDelete，不查 cascade 缺失）；属 I4 删除路径穷举面 |

### auth-security 族（3 open）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 |
|-------|------|---------|-----------|
| AR-146(r8) | 8 空 BizModel 无 @Auth | **未覆盖-需手动修复** | @Auth 系统性门禁未建（独立族） |
| AR-155(r10) | 只读查询用 roles 非 permissions | **未覆盖-需手动修复** | 权限模型契约；无门禁 |
| AR-170 | @Auth permissions 与 action-auth.xml 不匹配 | **未覆盖-需手动修复** | 前后端契约一致性；无门禁 |

### error-handling 族（24 open：6 部分覆盖 + 18 未覆盖）

**部分覆盖（6，截断位置）**：

| AR-ID | 标题 | 覆盖状态 | 说明 |
|-------|------|---------|------|
| AR-136 | collectRelevantInheritances 截断 | **部分覆盖** | INV-04 query-limit 标记位置（CodeGraphService:298 setLimit）；但「截断时无 WARN」可观测性子项未被门禁检查（adversarial-probe §2.1） |
| AR-168 / AR-177（截断侧） | buildFilePathCache / getProjectFilePaths | **部分覆盖** | 位置被 query-limit 标记；静默截断可观测性未锁定 |
| AR-180（截断部分） | buildInheritanceIndex/loadExistingEdgeKeys | **部分覆盖→实际 stale** | adversarial-probe §2.3 确认两方法全分页非截断（stale-premise）；entity-field-min 残留（899）另计 |
| AR-76 / AR-61（截断降级侧） | CodeCacheManager 超限降级 | **部分覆盖** | cache 层有 WARN+setTruncated（adversarial-probe §2.2）；但消费层 isTruncated 标志无人检查——部分覆盖 |

**未覆盖（18，聚合按模式标注）**：AR-09/49/167/171（硬编码扩展名/searchType/通配符）、AR-13（硬编码 HYBRID）、AR-17/50（硬编码常量）、AR-18（静默吞异常）、AR-160/162/165/175（子串误匹配/正则不完整）、AR-32/36/52/57/58/78/84/156/161/163/164 等（均为同类：子串误匹配 / 静默吞异常 / 硬编码常量）。
- **模式覆盖状态**：**未覆盖-需手动修复**。四族门禁不检测「子串误匹配（contains/startsWith）」「catch 吞异常」「硬编码字面量」。需 I4 逐条手动修复，或 Cycle 2 考虑 error-handling 专项门禁。

### graph-algorithm 族（12 open）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 |
|-------|------|---------|-----------|
| AR-39/67/152 | CommunityDetector 线程泄漏 | **未覆盖-需手动修复** | 线程生命周期（shutdownNow 不等待）；无门禁 |
| AR-153 | recursiveSplit 只考虑出边 | **未覆盖-需手动修复** | 图算法正确性；无门禁 |
| AR-173 | BetweennessCentrality 无超时/大小检查 | **未覆盖-需手动修复** | 算法健壮性；无门禁 |
| AR-174 | computeCohesion 只统计出边 | **未覆盖-需手动修复** | 图算法正确性；无门禁 |
| 其余（AR-98 单例丢弃、AR-113 注解 O(N²) 等，约 7 条聚合） | 出边遗漏/无超时/线程泄漏同类 | **未覆盖-需手动修复** | 按模式：图算法正确性 + 线程泄漏；无门禁 |

### language-adapter 族（10 open）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 |
|-------|------|---------|-----------|
| AR-33 / AR-141 / AR-147(r10) / AR-148(r10) / AR-149(r10) | Python/TS QN、import、嵌套、相对 import | **未覆盖-需手动修复** | 语言适配器解析正确性；无门禁 |
| AR-146(r10) | Java RecordDeclaration 不入 symbolMap | **未覆盖-需手动修复** | 解析覆盖完整性；无门禁 |
| 其余（约 4 条聚合） | QN/import/嵌套同类 | **未覆盖-需手动修复** | 按模式；无门禁 |

### orm-schema 族（5 open）

| AR-ID | 标题 | 覆盖状态 | 未覆盖原因 |
|-------|------|---------|-----------|
| AR-51 | ORM 布尔列永远 NULL | **未覆盖-需手动修复** | schema 缺陷；INV-02 仅查 useLogicalDelete |
| AR-153(r10) | NopCodeIndex 缺 (name) 唯一约束 | **未覆盖-需手动修复** | schema 缺陷；无门禁（注：live 有 `uk_nop_code_index_name`，需 I3 复核是否 stale） |
| AR-179 | FlowMembership 缺 indexId 列 | **未覆盖-需手动修复** | schema 缺陷；无门禁 |
| 其余（布尔/审计列/dict 约束约 2 条） | schema 同类 | **未覆盖-需手动修复** | 按模式；无门禁 |

### performance / dead-code / config-contract 族（13 open）

| AR-ID | 标题 | 族 | 覆盖状态 | 未覆盖原因 |
|-------|------|----|---------|-----------|
| AR-134 | OrmFingerprintStore N+1 | performance | **未覆盖-需手动修复** | 性能；无门禁 |
| AR-172 | diffGraph 双 Leiden | performance | **未覆盖-需手动修复** | 性能；无门禁 |
| 其余 performance（约 2 条） | N+1/O(N²) 同类 | performance | **未覆盖-需手动修复** | 按模式 |
| AR-142 | usageCount 死字段 | dead-code | **未覆盖-需手动修复** | 死代码；无门禁 |
| AR-159 | externalCalls 死权重 | dead-code | **未覆盖-需手动修复** | 死代码；无门禁 |
| 其余 dead-code（约 5 条） | 死字段/死权重同类 | dead-code | **未覆盖-需手动修复** | 按模式 |
| AR-22 | GraalVM reflect-config 缺 flow 类 | config-contract | **未覆盖-需手动修复** | 配置契约；无门禁 |
| 其余 config-contract（约 1 条） | 配置同类 | config-contract | **未覆盖-需手动修复** | 按模式 |

---

## §3 「未覆盖-需手动修复」的 I4 类别清扫面标注

> I4 修复时应按「类别清扫」而非逐条打补丁——修任一处必穷举同类。

| 清扫面 | 涉及 open 发现 | 清扫规则（I4 必穷举） |
|--------|--------------|---------------------|
| SearchService 全实体加载 | AR-64/86（findImplementations） | 修任一 SearchService 的全实体加载，必 grep 全部 SearchService 的 findAllByQuery（entity-field-min red list §2 已列全部 CodeSearchService 命中） |
| 删除路径完整性 | AR-30/66/60/149/150 | 修任一删除路径，必穷举全部删除路径（`deleteIndex`/`deleteFileRecords`/`deleteEntitiesByFilter`/`deleteRelationalBySymbolIds`），覆盖跨文件 calleeId/superTypeId 清理 + cascadeDelete |
| 截断可观测性 | AR-136/168/177/76/61 | 修任一 MAX_QUERY_RESULTS 截断点，必穷举全部 6 处 setLimit(MAX_QUERY_RESULTS)，统一加 `if (size==limit) WARN` + 消费层检查 isTruncated |
| 搜索引擎同步 | AR-166 | 修增量索引搜索同步，必覆盖 addDoc/removeDocs 对称性（重索引文件时先 removeDocs 旧符号） |
| 缓存不可变性（INV-05） | AR-158/155/145/148/147/157/42/11/62 | 修任一缓存返回可变引用，必穷举 CodeCacheManager 全部 getter（SymbolTable/CallGraph/Dependencies）统一返回不可变快照 |

---

## §4 INV-05 沉淀门禁裁定

**裁定：需要沉淀为新门禁 → Cycle 2 / I1 候选输入。**

- 依据：concurrency-lock 族 11 条 open **全部无门禁覆盖**（覆盖率 0%），且 INV-05 的 7 条核心锚点（AR-158/155/145/148/147/157/42）涉及「缓存返回不可变快照」「读写同同步保护」语义，AST/regex 静态门禁与现有 JUnit 幂等门禁均无法覆盖。
- 详细裁定与门禁形态建议（.mjs 静态扫描 + JUnit 并发/快照探针）见 `i2-adversarial-probe.md` §5。
- **不在本计划实现**（I2 Non-Goal）；由 I3 裁决 + I6 收口时按 Loop Rule 派生 Cycle 2 / I1。

## 无静默跳过确认

- 95 条 open 发现每条/每模式有且仅有一个覆盖状态，零「未判定」。
- 聚合约 20 条按失败模式标注（非省略），顶部已声明聚合覆盖率（≈21%）。
- AR-04 / AR-092 / AR-180 / AR-153(r10) 四处矩阵状态与 live 不符（实测已修或 stale），已显式标注「建议 I3 改判」，未默认沿用矩阵 open 标记。
- INV-05 沉淀裁定明确（`需要→Cycle2/I1 候选`），无悬空。
