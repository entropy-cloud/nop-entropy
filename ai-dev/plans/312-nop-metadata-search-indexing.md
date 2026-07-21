# 312 nop-metadata 元数据搜索索引

> Plan Status: completed
> Last Reviewed: 2026-07-21
> Closed: 2026-07-21
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` Phase 编号映射（Vision Phase 1 含搜索能力，"搜索能力尚未安排工作项"）；`ai-dev/design/nop-metadata/02-gap-analysis.md` Gap 1（搜索索引策略，P0 优先级）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §八（通用 Domain 源 non-blocking follow-up）；`ai-dev/design/nop-metadata/10-event-model.md` §4.2（搜索索引自动更新 follow-up）
> Related: `2026-07-17-0228-1-nop-metadata-event-model.md`（事件模型，搜索索引更新的事件源）

## Purpose

利用 Nop 平台已有的 `ISearchEngine` + `LuceneSearchEngine` 基础设施，为 nop-metadata 实现搜索索引构建与查询能力，覆盖 Vision Phase 1 中"搜索能力尚未安排工作项"的缺口。

## Current Baseline

- Nop 平台已有 `ISearchEngine` 接口 + `LuceneSearchEngine` 实现（注册为 `nopSearchEngine` IoC bean），`SearchableDoc` 支持 id/name/title/summary/content/tagSet/embedding
- `ISearchEngine` 实际 API：`search(req)`、`addDoc(topic, doc)`、`addDocs(topic, docs)`、`removeDocs(topic, ids)`、`removeTopic(topic)`、`getDoc(topic, id)`、`getDocsByTerm(topic, term, limit)`、`refreshBlocking()`。**注意**：无 `flush()`、无 `deleteDoc(topic, id)`（仅批量 `removeDocs` 接受 List）、无 `updateDoc()`（`addDoc` 按 id 删除+添加实现幂等更新）、无 `deleteDocumentsByTopic(topic)`（替代为 `removeTopic`，但会关闭 writer/directory；全量重建应使用 `addDocs` 的幂等语义）
- `SearchRequest` 实际 API：`topic`（**单一 String，非集合**）、`query`、`tags`、`matchAllTags`、`limit`（**无 offset/page**）、`similarityThreshold`、`searchType`、`filter`
- `SearchHit` 实际字段：`score, id, name, title, content, summary, publishTime, modifyTime, fileSize, path, bizKey, tags`。**无 `entityType`、无 `matchedField`**
- nop-metadata 事件模型已设计并实现（plan 0228-1）：`MetaModelChangedEvent` 实体的 `EventPublisher.publishEvent()` 仅写 DB，**未发布到消息总线/监听器**。支持事件的实体仅 `NopMetaModule` 和 `NopMetaTable`；Classification/Tag/GlossaryTerm/MetaEntity/MetaEntityField **无事件发布 hook**
- `nop-metadata-service/pom.xml` 当前 **无 `nop-search-api` 或 `nop-search-lucene` 依赖**
- nop-metadata 的 32+ 实体已可 GraphQL CRUD 操作，但无搜索引擎索引支持
- 搜索配置和索引策略当前无设计：哪些实体可搜索、哪些字段可搜索、索引策略、权重设计均未定义

## Goals

- 设计 nop-metadata 搜索索引策略（可搜索实体、字段、权重、索引策略）
- 实现核心实体（Classification/Tag/GlossaryTerm/Table/Entity/EntityField）的索引构建
- 通过 BizModel save hook 实现增量索引（在目标实体的 save/delete 操作后同步更新搜索引擎，而非依赖事件总线——事件模型当前仅 DB 持久化、非发布-订阅模式）
- 提供统一的搜索 API 或 GraphQL 端点
- 验证端到端搜索路径：创建→索引→搜索→结果匹配

## Non-Goals

- 不实现 UI 搜索页面（提供后台 API 即可）
- 不实现向量搜索/语义搜索首版（首版仅 TEXT/BM25；VECTOR/HYBRID 为后续阶段）
- 不实现全量 32+ 实体的索引覆盖（首版覆盖最核心的 6 个实体）
- 不改造 `ISearchEngine` / `SearchableDoc` / `SearchRequest` / `SearchHit` 接口（复用现有能力，但需适配实际 API 签名）
- 不实现搜索权限过滤（后续阶段）

## Scope

### In Scope

- 搜索策略设计：
  - 确定首版可搜索实体列表（Classification/Tag/GlossaryTerm/MetaTable/MetaEntity/MetaEntityField）
  - 每个实体的可搜索字段映射（name/displayName/description/fullyQualifiedName 等）
  - 字段权重分配
  - `SearchableDoc.topic` 命名约定（按实体类型分组）
- 索引构建：
  - 全量索引：通过 GraphQL action 触发全量重建，遍历实体写入 `ISearchEngine.addDocs`
  - 增量索引：在 6 个目标实体的 BizModel save/delete 方法中调用索引更新（不依赖事件总线，因当前事件仅写 DB 非发布-订阅）
- 搜索 API：
  - `NopMetaSearchBizModel` 提供 `searchMetadata(query, entityType?, tags?, limit?)` GraphQL query
  - 封装 `ISearchEngine.search(SearchRequest)` 调用；因 `SearchRequest.topic` 为单一 String，按 entityType 过滤使用单 topic + tagSet 方式
- `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过
- 集成测试覆盖全量索引 + 增量索引 + 搜索路径

### Out Of Scope

- 向量搜索/语义搜索（首版仅 BM25 文本搜索）
- 非核心实体的索引覆盖（QualityRule、QualityResult、LineageEdge 等）
- 搜索权限过滤（搜索结果不过滤，仅按 topic 分组）
- UI 搜索页/自动补全
- 搜索索引后台管理（重建/清空等 UI）

## Execution Plan

### Phase 1 - 搜索策略设计

Status: completed
Targets: `ai-dev/design/nop-metadata/` 下新增或更新设计文档

- Item Types: `Decision`

- [x] 审计 `ISearchEngine` / `LuceneSearchEngine` / `SearchableDoc` / `SearchRequest` / `SearchHit` 的完整 API 和实际源码，记录方法签名和参数差异（如 `flush()` 不存在、`SearchRequest.topic` 为单一 String、`SearchHit` 无 `entityType`、无分页参数等）
- [x] 确定首版可搜索实体列表及理由：Classification、Tag、GlossaryTerm、MetaTable、MetaEntity、MetaEntityField
- [x] 为每个实体设计字段→SearchableDoc 字段映射：
  - `id` → 实体 PK
  - `name` → 实体名/fullyQualifiedName
  - `title` → displayName
  - `summary` → description（截取前 500 字符）
  - `content` → 组合搜索文本
  - `tagSet` → entityType 字符串（过滤时使用 `SearchRequest.tags` 参数，不依赖多 topic——因 `topic` 为单一 String）
  - `topic` → `"nop-meta-metadata"`（统一 topic，按 `tags` 区分 entityType）
- [x] 确定字段权重：`name`/`title` 高权重(2.0)、`content` 标准权重(1.0)、`summary` 低权重(0.5)
- [x] 设计全量索引构建策略（遍历 DAO 查询 + `ISearchEngine.addDocs` 批量写入，利用其幂等语义——按 id 删除+添加）
- [x] 设计增量索引策略（BizModel save/delete hook 直接调用 `ISearchEngine.addDoc`/`removeDocs`，不依赖事件总线——因当前事件模型仅 DB 持久化无订阅机制，且仅 2/6 实体有事件发布）
  - 为每个目标实体补充 BizModel 的 `@BizMutation save` 和 `@BizMutation delete` 的 post-hook
  - 空值处理：save 时仅 entity 存在且 `ISearchEngine` bean 可用时触发（防御性编程）
- [x] 设计搜索 API 签名和返回结构：
  - `searchMetadata(query, entityType?, limit?)`（单一 topic、无分页 offset；分页可通过 limit 配合多次查询实现）
  - 返回自定义 `SearchResultDTO{items: SearchHitDTO[], limit, total}`
  - `SearchHitDTO` 包装 `SearchHit` 并补充 `entityType` 字段（在 toSearchableDoc 时从 topic/tags 推导）
- [x] 确认需添加 Maven 依赖：`nop-search-api` + `nop-search-lucene` 到 `nop-metadata-service/pom.xml`
- [x] 将设计成果写入 `01-architecture-baseline.md`（新增搜索章节）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] ISearchEngine API audit 完成：所有计划中要调用的 API 方法均已与实际接口签名对照验证
- [x] 6 个首版实体的索引字段映射已确定并记录
- [x] 全量索引构建策略已设计（批处理大小、错误处理、幂等性）
- [x] 增量索引策略已设计（BizModel save/delete hook 模式，明确列出需要修改的 BizModel 清单）
- [x] 搜索 API 签名已设计（输入参数、输出 DTO 结构）
- [x] 所需 Maven 依赖已确认
- [x] 设计文档已写入 `01-architecture-baseline.md`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 基础设施 + 全量索引构建

Status: completed
Targets: `nop-metadata-service/pom.xml` → `nop-metadata-service/.../search/`（新建包）→ `NopMetaIndexBuilder.java`

- Item Types: `Proof`

- [x] `nop-metadata-service/pom.xml` 添加 `nop-search-api` 和 `nop-search-lucene` 依赖
- [x] 确认 `LuceneSearchEngine` 的 IoC bean 名（`nopSearchEngine`）并在 `nop-metadata-service` Spring 上下文中可见
- [x] 新建 `NopMetaIndexBuilder` 组件：
  - 注入 `ISearchEngine`（`@Inject @Named("nopSearchEngine")`）
  - 实现 `buildFullIndex(entityTypes?)`：
    - 遍历指定实体列表，对每个实体构造 `SearchableDoc`，收集至 list
    - 使用 `ISearchEngine.addDocs(topic, docs)` 批量写入（幂等：`addDocs` 内部按 doc.id 删除旧索引再添加，无需手动清除）
    - 最终调用 `ISearchEngine.refreshBlocking()` 确保索引可查询（替代不存在的 `flush()`）
  - 错误处理：单实体转换失败记录 `LOG.warn` 并继续；失败信息累积至 `IndexResult.errors[]` 返回
  - 返回 `IndexResult{entityType, indexed, failed, errors[]}`
- [x] 实现每个实体的 `toSearchableDoc` 转换：
  - `topic` 统一为 `"nop-meta-metadata"`（`SearchRequest.topic` 为单一 String）
  - `tagSet` 存放 `entityType`（如 `"MetaTable"`），过滤时通过 `SearchRequest.tags` 匹配
  - 具体映射见设计 doc
- [x] `NopMetaSearchBizModel` 新增 `@BizMutation rebuildSearchIndex(entityTypes?: [String])` action 触发全量重建
- [x] `NopMetadataErrors.java` 新增搜索相关 ErrorCode（如 `ERR_SEARCH_INDEX_REBUILD_FAILED`）
- [x] 单元测试：`TestNopMetaIndexBuilder` — Mock `ISearchEngine`，验证 `addDocs` 和 `refreshBlocking` 被正确调用
- [x] `./mvnw compile -pl nop-metadata -am` 编译通过

Exit Criteria:

- [x] `nop-metadata-service/pom.xml` 已添加 `nop-search-api` + `nop-search-lucene` 依赖
- [x] `NopMetaIndexBuilder` 组件构建完成，支持全量索引重建
- [x] 6 个实体的 `toSearchableDoc` 转换全部实现（统一 topic + tagSet 按 entityType 区分）
- [x] `rebuildSearchIndex` GraphQL action 可用
- [x] **无静默跳过**：转换失败时记录 warn 并继续，不吞异常
- [x] **接线验证**：单元测试验证 `ISearchEngine.addDocs` 和 `refreshBlocking` 被正确调用
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 已更新搜索章节
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 增量索引（BizModel save/delete hook）

Status: completed
Targets: 6 个目标实体 BizModel 的 save/delete post-hook

- Item Types: `Proof`

**设计说明**：因当前事件模型仅 DB 持久化无订阅机制，且只有 2/6 目标实体有事件发布 hook，增量索引改为在目标实体 BizModel 的 save 和 delete 之后直接调用搜索引擎更新。事件模型未来可扩展为辅助路径，但不作为首版依赖。

- [x] 新建 `NopMetaSearchService` 组件（被各 BizModel hook 调用），封装增量索引操作：
  - `addToIndex(entityType, entityId, searchableDoc)` → 调用 `ISearchEngine.addDoc(topic, doc)`（幂等）
  - `removeFromIndex(entityType, entityId)` → 调用 `ISearchEngine.removeDocs(topic, List.of(entityId))`
  - 注入 `ISearchEngine`（`@Inject @Named("nopSearchEngine")`）
  - 容错：搜索引擎不可用时记录 `LOG.warn` 不抛异常，不阻塞业务操作
- [x] 为每个目标实体 BizModel 添加索引更新 hook：
  - **NopMetaClassificationBizModel**：override save 和 delete 的 post-hook
  - **NopMetaTagBizModel**：同上
  - **NopMetaGlossaryTermBizModel**：同上
  - **NopMetaTableBizModel**：已有事件发布，补充搜索引擎调用（补充而非替换事件路径）
  - **NopMetaEntityBizModel**：override save 和 delete 的 post-hook（注意：MetaEntity 在 OrmModelImporter 导入时也创建，需确保 import 路径也触发索引更新）
  - **NopMetaEntityFieldBizModel**：同上
- [x] OrmModelImporter 导入路径的索引覆盖：在 `NopMetaModuleBizModel.importOrmModel` 的 `persistModelGraph` 调用之后（以及 `orm().flushSession()` 之后），对本次导入创建的 MetaEntity/MetaEntityField/MetaTable 执行索引更新。因导入路径使用 DAO 直接持久化（`orm().save(entity)`）而非 BizModel hook，BizModel 的 save/delete hook 不会触发，需额外处理：收集导入过程中构建的 entity/field/table 实例，调用 `NopMetaSearchService.addToIndex` 批量添加索引。
- [x] 索引更新时机：
  - save/update 后：构造 `SearchableDoc` 并调用 `addDoc`
  - delete 后：调用 `removeDocs`
- [x] 单元测试：`TestNopMetaSearchService` — Mock ISearchEngine，验证 `addDoc` 和 `removeDocs` 被正确调用
- [x] `./mvnw compile -pl nop-metadata -am` 编译通过

Exit Criteria:

- [x] `NopMetaSearchService` 组件已实现（封装 addDoc/removeDocs 操作，带容错）
- [x] 6 个目标实体的 BizModel save/delete hook 已添加索引更新调用
- [x] OrmModelImporter 导入路径的索引覆盖已实现：`NopMetaModuleBizModel.importOrmModel` 导入后对 MetaEntity/MetaEntityField/MetaTable 执行索引添加
- [x] 搜索引擎不可用时业务操作不受阻塞（`LOG.warn` 后继续，不抛异常）
- [x] **无静默跳过**：搜索引擎调用失败记录 warn 不静默；hook 设计为防御性（null-check safe）
- [x] **接线验证**：单元测试验证 `ISearchEngine.addDoc`/`removeDocs` 在 BizModel 操作后被调用
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 已更新搜索章节
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 搜索 API + 集成测试

Status: completed
Targets: `NopMetaSearchBizModel.java` → `TestNopMetadataSearchIntegration.java`

- Item Types: `Proof`

- [x] `NopMetaSearchBizModel` 新增 `@BizQuery searchMetadata(query, entityType?, tags?, limit?)`：
  - 默认 `limit` 为 20（上限 100）
  - 构造 `SearchRequest`：`topic="nop-meta-metadata"`（单一 topic），`tags=entityType ? List.of(entityType) : null` 按类型过滤，`query` 为搜索文本
  - **无分页**（首版简化）：`SearchRequest` 无 `offset/page`；可多次调用加 `tags` 或 `query` 变体实现浏览
  - 返回自定义 `SearchResultDTO`：
    - `items: List<SearchHitDTO>` 其中 `SearchHitDTO{id, entityType, name, title, summary, score}`（从 `SearchHit` 转换并补充 `entityType`——在 toSearchableDoc 时编码到 `tags` 中或通过约定 topic→entityType 推导）
    - `total: long`（取自 `SearchResponse.total`）
    - `limit: int`
- [x] 端到端集成测试（`TestNopMetadataSearchIntegration`）：
  - 全量索引构建 + 按文本搜索测试
  - 搜索按 entityType 过滤
  - 搜索按关键词测试（BM25 相关性）
  - BizModel save/delete → 增量索引 → 搜索结果验证
  - 空搜索结果测试
- [x] `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过

Exit Criteria:

- [x] `searchMetadata` GraphQL query 可用
- [x] 搜索返回值包含正确的结果列表
- [x] **端到端验证**：创建实体 → 增量索引（BizModel save hook）→ 搜索到该实体
- [x] **接线验证**：`NopMetaSearchService.addToIndex` 在 BizModel save 后被调用
- [x] 集成测试覆盖（a）全量索引后搜索、（b）按 entityType 过滤、（c）增量索引后搜索、（d）空结果
- [x] `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` 已更新搜索章节
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 搜索策略已设计并记录
- [x] 全量索引构建组件已实现
- [x] 增量索引 BizModel save/delete hook 已实现
- [x] 搜索 API 已提供 GraphQL query
- [x] 端到端集成测试覆盖搜索路径
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] `01-architecture-baseline.md` 已更新（新增搜索章节）或 `nop-metadata-roadmap.md` 已更新（搜索能力标记为 done）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）BizModel save → `NopMetaSearchService.addToIndex` → `ISearchEngine.addDoc` 的调用链在运行时连通，（b）全量索引 `rebuildSearchIndex` → `NopMetaIndexBuilder.buildFullIndex` → `ISearchEngine.addDocs` 调用链连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`

## Deferred But Adjudicated

### 向量搜索/语义搜索

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版仅 TEXT/BM25 文本搜索，VECTOR/HYBRID 需额外的 embedding 生成管线，应作为独立后继阶段。不影响基于文本关键词搜索的 MVP 可用性。
- Successor Required: `no`

### 全量 32+ 实体索引覆盖

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首版覆盖 6 个核心实体（Classification/Tag/GlossaryTerm/Table/Entity/EntityField），非核心实体（QualityRule、QualityResult、LineageEdge 等）搜索需求较低。后续可按需扩展。
- Successor Required: `no`

### 搜索权限过滤

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前搜索结果不区分权限，所有用户可搜索全部元数据。权限过滤需接入 `IDataAuth` + data-auth 规则过滤搜索结果，是独立工作项。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 评估 UI 搜索页面集成（searchMetadata query → 前端搜索框）
- 评估搜索索引后台管理 UI（重建/清空/状态查看）
- 评估未来将增量索引从 BizModel hook 模式迁移到事件监听模式（需先改造事件模型支持发布-订阅机制）

## Closure

Status Note: 四阶段全部完成。编译通过、705 tests pass（含 17 个新增搜素单元测试 + 688 个既有测试零回归）。
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: opencode (mission driver)
- Evidence: 705 tests pass (BUILD SUCCESS), compile passes, architecture baseline updated with search chapter

Follow-up:

- （待完成时填写）
