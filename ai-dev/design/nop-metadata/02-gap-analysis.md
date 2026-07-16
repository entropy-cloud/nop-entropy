# nop-metadata Gap Analysis

> Status: open
> Date: 2026-07-15
> Scope: nop-metadata 设计完善
> Goal: 对比五个平台的源码级设计模式，识别 nop-metadata 现有设计的缺失和可完善点
> Based on: metadata-survey 深度分析报告

## Context

基于对 DataHub、OpenMetadata、Apache Atlas、Amundsen、Marquez 的源码深度分析，对比 nop-metadata 现有设计（`00-vision.md` + `01-architecture-baseline.md`），识别需要补充和完善的内容。

## 现有设计覆盖范围

| 能力 | 现有设计 | 状态 |
|------|----------|------|
| 模块 ORM 模型导入 | ✅ MetaOrmModel + Delta 版本 | 已设计 |
| 结构化实体拆解 | ✅ MetaEntity/Field/Relation/Domain/Dict | 已设计 |
| 逻辑表 (BI 语义层) | ✅ MetaTable + Measure + Join | 已设计 |
| **Delta 版本管理** | ✅ MetaModule 版本 + isDelta + baseModuleId | 已设计（模块级） |
| 模块发现 | ✅ 配置式/自动扫描 | 已设计 |
| **数据血缘** | ✅ MetaLineageEdge + MetaPipeline | 已设计 |
| **数据质量** | ✅ MetaQualityRule + MetaQualityResult | 已设计 |
| **域定义** | ✅ MetaDomain（isGlobal + sourceModuleId） | 已设计 |
| **字典定义** | ✅ MetaDict + MetaDictItem | 已设计 |
| **数据契约** | ✅ MetaDataContract（SLA + 质量承诺） | 已设计（04-data-governance.md） |
| **Nop ORM extConfig** | ✅ 每个实体自带 JSON 扩展属性 | 已有能力 |
| **ISearchEngine** | ✅ Lucene 实现，支持 TEXT/VECTOR/HYBRID | 已有能力 |
| **SearchableDoc** | ✅ 支持 embedding 向量、tagSet | 已有能力 |

## 缺失分析 (Gap Analysis)

### Gap 1: 元数据搜索索引策略 ⚠️ 已有基础设施，需集成

**现状:** Nop 已有完整的搜索引擎抽象，但 nop-metadata 设计中未定义如何利用

**Nop 已有能力:**
- `ISearchEngine` 接口 + `LuceneSearchEngine` 实现（支持 TEXT/VECTOR/HYBRID 三种搜索模式）
- `SearchableDoc`: 支持 id, name, title, summary, content, tagSet, embedding 向量
- `SearchRequest`: 支持 topic, query, tags, filter, similarityThreshold
- `SearchType`: TEXT (BM25), VECTOR (kNN cosine), HYBRID (RRF 融合)
- Nop ORM 每个实体自带 `extConfig`（JSON 字符串），可灵活扩展属性

**需要补充的设计:**
```
MetaSearchConfig                 — 搜索索引配置（存储在 extConfig 中）
  ├── searchFields[]             — 搜索字段配置
  │    ├── fieldName
  │    ├── fieldType             — TEXT | KEYWORD | TAG
  │    └── boostScore            — 搜索权重
  └── indexStrategy              — FULL_TEXT | EXACT_MATCH
```

实现路径:
1. MetaEntity/MetaTable 导入时，构建 `SearchableDoc` 写入 ISearchEngine
2. 搜索配置存放在实体的 `extConfig` JSON 中，无需新表
3. 利用 ISearchEngine 的 topic 机制按实体类型分组索引

---

### Gap 2: 事件模型 (变更通知) ❌

**现状:** 设计中未定义元数据变更事件模型

**其他平台做法:**
- **DataHub**: MCP → MCL 两阶段（提案 → 已提交日志）
- **OpenMetadata**: ChangeEvent (ENTITY_CREATED/UPDATED/DELETED)
- **Marquez**: 双路径异步（原始事件存储 + 规范化模型更新）

**建议补充:**
```java
// 元数据变更事件
public class MetaModelChangedEvent {
    String entityType;        // "MetaEntity" | "MetaTable" | "MetaOrmModel"
    String entityId;
    ChangeType changeType;    // CREATE | UPDATE | DELETE | RESTORE
    String changeSource;      // IMPORT | UI | API | SYNC
    Map<String, Object> before;  // 变更前快照
    Map<String, Object> after;   // 变更后快照
    Instant changeTime;
    String changedBy;
}
```

实现路径: 利用 Nop 的 EventBus 机制，在 MetaEntityRepository 写入后发布事件。

---

### Gap 3: 数据血缘 ✅ 已设计

**现状:** 已在 01-architecture-baseline.md 中定义 MetaLineageEdge + MetaPipeline

**数据模型:**
- `MetaLineageEdge`: 表级/列级血缘边，支持 direct/derived/aggregated 转换类型
- `MetaPipeline`: 数据处理管道，支持 etl/sql/api/manual 类型
- `lineageSource`: 支持 manual/sql_parse/open_lineage/hook 多种来源

**查询能力:**
- 向上追溯（查找上游源表）
- 向下追踪（查找下游消费者）
- 影响分析（列级变更影响范围）
- 路径查找（两表间血缘路径）

---

### Gap 4: 数据分类/标签体系 ⚠️ 已有基础，需增强

**现状:** MetaEntityField 有 `tagSet` 字段，Nop ORM 实体普遍支持 extConfig 扩展

**Nop 已有能力:**
- 所有 ORM 实体都有 `extConfig`（JSON 字符串），可存储自定义标签
- ISearchEngine 支持按 tagSet 过滤搜索

**需要补充的设计:**
- 标签定义表（MetaTag）支持层级标签和颜色配置
- 标签与实体的关联表（MetaEntityTag）支持标签来源追踪
- 参考 OpenMetadata 的 Classification 带属性模型

---

### Gap 5: 数据质量/测试 ✅ 已设计

**现状:** 已在 01-architecture-baseline.md 中定义 MetaQualityRule + MetaQualityResult

**数据模型:**
- `MetaQualityRule`: 质量规则定义，支持 7 种内置规则类型
- `MetaQualityResult`: 时序执行结果，支持趋势分析和异常告警

**内置规则类型:**
- `not_null` / `unique` / `range` / `regex` — 字段级约束
- `freshness` / `volume` / `custom_sql` — 表级约束

**设计优势:**
- 规则挂载在字段/表级别，结构清晰
- 时序结果存储，支持趋势查看和异常告警
- severity 分级（error/warning/info），支持渐进式治理

---

### Gap 6: 访问控制 ✅ Nop 框架已有

**现状:** Nop 平台已有完整的权限框架（`nop-auth`），包括用户、角色、权限、数据权限，GraphQL 层通过 `@Auth` 注解声明式鉴权。nop-metadata 直接复用即可，无需新设计。

---

### Gap 7: 数据源注册 ✅ 已设计

**现状:** `01-architecture-baseline.md` §2.2 已定义 `MetaDataSource`，描述 `querySpace` 对应的物理数据源，不参与运行时查询路由（ORM 承担此职责）。

---

### Gap 8: API 层设计 ✅ Nop 框架已有

**现状:** Nop 框架基于 ORM 模型自动生成 GraphQL CRUD API（`CrudBizModel` + `nop-graphql`），nop-metadata 的 ORM 实体同样自动获得 GraphQL 服务，无需额外设计。

**Nop 已有能力:**
- `CrudBizModel` 自动提供 find/findPage/findList/get/save/update/delete 等标准 CRUD 操作
- GraphQL schema 从 ORM 模型声明式生成（`BizMaker.gql`）
- 复杂查询通过 DQL（`QueryBean`）透传
- 参考 OpenMetadata 的 EntityRepository 模板方法模式（可选增强）

---

### Gap 9: 语义搜索/向量搜索 ✅ 已有基础设施

**现状:** Nop 已有完整的向量搜索能力，无需额外开发

**Nop 已有能力:**
- `ISearchEngine` 支持 `SearchType.VECTOR`（kNN cosine similarity）
- `ISearchEngine` 支持 `SearchType.HYBRID`（文本 + 向量 RRF 融合）
- `SearchableDoc` 支持 `embedding` 向量字段
- `LuceneSearchEngine` 实现了 `KnnFloatVectorField` 索引
- `SearchRequest` 支持 `similarityThreshold` 参数

**实现路径:**
- MetaEntity/MetaTable 导入时自动生成 embedding（调用 LLM 生成摘要 → 向量化）
- 利用 ISearchEngine 的 HYBRID 模式实现语义搜索
- 无需额外开发，直接复用 nop-search 模块

---

### Gap 10: 元数据采集 Hook 机制 ❌

**现状:** ORM 模型导入是手动触发或构建时执行，无运行时 Hook

**其他平台做法:**
- **Atlas**: Hive Hook (命令分派 + Strategy + Factory 模式)
- **DataHub**: Python Ingestion Framework (80+ 连接器)

**建议补充:**
```
MetaImportHook                   — 导入钩子（预留）
  ├── hookName
  ├── hookType                   — BUILD_TIME | RUNTIME | SCHEDULED
  ├── sourceType                 — ORM | SQL | EXTERNAL_DB
  ├── handlerClass               — 钩子处理器类
  └── config                     — JSON 配置
```

---

## 优先级排序

| 优先级 | Gap | 建议 |
|--------|-----|------|
| **P0** | Gap 1: 搜索索引 | 已有 ISearchEngine，需设计索引构建流程 |
| **P0** | Gap 2: 事件模型 | Phase 1 建议实现，支撑 UI 实时更新 |
| **✅** | Gap 3: 数据血缘 | 已设计 MetaLineageEdge + MetaPipeline |
| **✅** | Gap 4: 标签体系 | extConfig + tagSet 已满足基础需求 |
| **✅** | Gap 5: 数据质量 | 已设计 MetaQualityRule + MetaQualityResult |
| **✅** | Gap 6: 访问控制 | Nop 框架已有（nop-auth），无需设计 |
| **✅** | Gap 7: 数据源注册 | 已设计 MetaDataSource §2.2 |
| **✅** | Gap 8: API 层 | Nop 框架已有（CrudBizModel + GraphQL），无需设计 |
| **✅** | Gap 9: 语义搜索 | 已有 ISearchEngine VECTOR/HYBRID，直接复用 |
| **P3** | Gap 10: 采集 Hook | Phase 3+ |

## 设计文档索引

| 文档 | 内容 | 状态 |
|------|------|------|
| 00-vision.md | 定位、解决的问题、成功标准 | draft |
| 01-architecture-baseline.md | 核心对象、Delta 版本管理、模型导入 | draft |
| 02-gap-analysis.md | metadata-survey 对比分析、Gap 识别 | open |
| **03-version-management.md** | 模块级版本管理、Maven 对齐 | **draft** |
| **04-data-governance.md** | 域定义、字典、数据契约、血缘、质量 | **draft** |
| **05-metadata-import.md** | 元数据采集、导入、Manifest、Catalog | **draft** |
| **06-data-quality-extended.md** | 数据质量扩展、数据剖析、验证执行 | **draft** |
| **07-ai-integration.md** | AI 集成、MCP Server、Agent 代理 | **draft** |

## 对现有设计的具体修改建议

### 修改 1: 01-architecture-baseline.md 补充搜索和事件

在"二、核心对象"中增加：
```
MetaSearchConfig                 — 搜索索引配置（存储在 extConfig 中）
MetaModelChangedEvent            — 变更事件
```

### 修改 2: ✅ 已完成 — 已添加入 §2.2

### 修改 3: 00-vision.md 补充 Non-goals 边界

明确"不是数据血缘采集引擎"但"预留血缘数据模型"。

## Nop 平台独特优势

相比五个外部平台，nop-metadata 有以下独特优势：

1. **extConfig 扩展属性**: Nop ORM 每个实体自带 JSON 扩展属性，无需新表即可添加自定义元数据（类似 DataHub 的 structuredProperties，但更轻量）
2. **ISearchEngine 搜索抽象**: 已有 Lucene 实现，支持 TEXT/VECTOR/HYBRID 三种搜索模式，包含向量搜索能力（OpenMetadata 需要额外开发 VectorIndexService）
3. **Delta 版本管理**: 独特的模块级 Delta + Full 双存储模式，支持模块间继承和版本对比（其他平台无此设计）。版本对齐 Maven 打包/发布粒度。
4. **XDef 声明式模型**: 可直接利用 XDef 注解驱动搜索索引构建（类似 DataHub 的 PDL 注解，但基于现有基础设施）
5. **Domain 跨模块引用**: 支持通用域（isGlobal）的引用和拷贝机制，模块可以继承和覆盖通用域定义
6. **Maven/Git 溯源**: MetaModule 包含 Maven 坐标和 Git 信息，支持从元数据到源码的完整追溯链

## References

- [DataHub Design Patterns](../../analysis/metadata-survey/2026-07-15-datahub-deep-analysis.md)
- [OpenMetadata Design Patterns](../../analysis/metadata-survey/2026-07-15-openmetadata-deep-analysis.md)
- [Atlas Design Patterns](../../analysis/metadata-survey/2026-07-15-atlas-deep-analysis.md)
- [Marquez Design Patterns](../../analysis/metadata-survey/2026-07-15-marquez-deep-analysis.md)
- [Platform Comparison](../../analysis/metadata-survey/2026-07-15-metadata-platforms-comparison.md)