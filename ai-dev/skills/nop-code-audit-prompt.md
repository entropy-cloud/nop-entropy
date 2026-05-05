# Nop-Code 架构与实现审计提示词

使用此提示词对 nop-code 模块进行系统性审计。审计应从多个维度出发，跳出当前实现的限制，识别设计层面和实现层面的问题。

## 审计范围

nop-code 是 Nop 平台的代码分析与索引模块，提供符号搜索、类型层级、调用链、图分析（社区检测、影响分析）等能力。

**模块组成**：12个子模块，129个Java文件，约17,600行代码
- `nop-code-core`：核心分析层（图算法、符号模型、增量分析）
- `nop-code-dao`：ORM实体与数据访问
- `nop-code-meta`：xmeta元数据定义
- `nop-code-service`：业务逻辑层（BizModel + CodeIndexService）
- `nop-code-web`：AMIS页面（view.xml + action-auth.xml）
- `nop-code-app`：Quarkus应用入口
- `nop-code-api`：API接口定义（当前为空）
- `nop-code-codegen`：代码生成
- `nop-code-lang-java/python/typescript`：多语言解析器
- `nop-code-e2e`：Playwright E2E测试

---

## 审计维度

### 维度一：架构分层与模块边界

#### 1.1 模块依赖合理性

**审计要点**：检查依赖方向是否严格单向（高层→低层），是否存在循环依赖或不当耦合。

| 检查项 | 预期状态 | 实际问题 |
|--------|---------|---------|
| api层是否为纯接口 | api应只定义ICodeIndexService和DTO | **nop-code-api模块完全为空**——接口和DTO实际放在nop-code-service中 |
| service是否只依赖api+core+dao | service层不应依赖web层 | ✅ 无违规 |
| web层是否只依赖service | web不应包含业务逻辑 | ✅ 无违规 |
| Quarkus依赖是否隔离到app | 非app模块不应引入Quarkus | ✅ 隔离正确 |
| nop-auth耦合程度 | nop-code应可独立运行 | ⚠️ app层强依赖nop-auth-web/nop-auth-service，但service层无auth耦合 |

**关键发现**：
- `nop-code-api` 模块存在但完全为空——违背Nop标准分层约定（api模块应定义对外接口和DTO）
- `ICodeIndexService` 和所有 DTO 都放在 `nop-code-service/api/` 下，而非独立的 `nop-code-api` 模块
- 如果其他模块需要引用 nop-code 的接口，必须依赖整个 service 模块

**改进建议**：
1. 将 `ICodeIndexService`、所有 DTO、Biz 接口（`INopCodeIndexBiz` 等）迁移到 `nop-code-api`
2. 或者删除空的 `nop-code-api` 模块，明确承认当前不需要独立API层

#### 1.2 层级职责划分

**审计要点**：每层是否只做自己该做的事。

| 层级 | 职责 | 实际问题 |
|------|------|---------|
| core | 纯计算：解析、图算法、模型 | ✅ 无框架依赖，职责清晰 |
| dao | ORM实体、数据访问 | ⚠️ 7个ORM实体完整定义但**完全未使用** |
| service | BizModel业务编排 + CodeIndexService | ❌ **CodeIndexService完全绕过DAO层**，只用内存Map |
| web | AMIS页面 + 权限配置 | ⚠️ 5个实体页面为空壳（stub），4个自定义页面缺结果展示 |

---

### 维度二：数据架构与持久化

#### 2.1 双存储架构断裂（🔴 严重）

**问题描述**：nop-code存在两套完全独立的数据存储，互不连通。

```
设计文档预期的架构：
  ProjectAnalyzer → CodeIndexService → DAO(ORM) → Database
                                       ↓
                                 GraphQL/RPC API

实际实现的架构：
  ProjectAnalyzer → CodeIndexService → ConcurrentHashMap（纯内存）
                                       ↓
                                 GraphQL/RPC API

  DAO(ORM) → Database（完全孤立，从未被写入或读取）
```

**具体证据**：
- `CodeIndexService` 用4个 `ConcurrentHashMap` 存储所有数据
- 7个 ORM 实体（`NopCodeIndex`, `NopCodeSymbol`, `NopCodeFile`, `NopCodeUsage`, `NopCodeCall`, `NopCodeInheritance`, `NopCodeAnnotationUsage`）有完整的表结构、xmeta、关系定义
- 但 `CodeIndexService` 中**无任何一处**引用这些 ORM 实体
- 应用重启后，所有索引数据丢失

**影响**：
- 无法持久化索引结果
- 重启后需全量重建索引（大项目耗时数十秒）
- 无法利用数据库做复杂查询
- ORM 实体和 xmeta 成为死代码

**改进方向**（三选一）：

| 方案 | 描述 | 适用场景 |
|------|------|---------|
| A. 启用持久化 | CodeIndexService 分析完成后写入 ORM，查询走数据库 | 需要长期存储、重启恢复 |
| B. 移除 ORM | 删除 dao/meta/codegen 模块，简化为纯内存架构 | 仅用于临时分析、demo |
| C. 混合模式 | 热数据内存、冷数据持久化，类似缓存层 | 兼顾性能与持久化 |

#### 2.2 ORM 实体设计问题

**审计要点**：实体是否完整、规范。

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 主键设计 | ✅ | VARCHAR(36)，UUID风格 |
| 审计字段 | ❌ | 配置了 `ext:useStdFields="true"` 但7个实体均无 createTime/updateTime/createdBy/updatedBy |
| 关系定义 | ✅ | 有完整的 to-one/to-many 关系 |
| xmeta覆盖 | ✅ | 7个实体全部有 xmeta，有 displayName 和 dict |
| 设计文档对齐 | ❌ | 设计文档中预期 NopCodeClass/Interface/Enum/Method/Field/Constructor 各自独立表，实际合并为单一 NopCodeSymbol 表 |

**改进建议**：
1. 明确选择单表（当前）还是多表（设计文档）方案，更新设计文档保持一致
2. 如保留单表方案，给 NopCodeCall.callType 和 NopCodeInheritance.relationType 增加 dict 定义
3. 确认是否需要标准审计字段，如不需要则移除 `ext:useStdFields="true"`

---

### 维度三：API 设计与 BizModel 规范

#### 3.1 核心模型泄漏到 API 层（🟡 重要）

**问题描述**：BizModel 方法直接返回 `nop-code-core` 的内部模型类，而非 DTO。

| 返回类型 | 来源模块 | 使用的BizModel方法 | 问题 |
|----------|---------|-------------------|------|
| `CodeSymbol` | nop-code-core | getById, findByQualifiedName, findSymbols | GraphQL schema 绑定到内部实现 |
| `CodeFileAnalysisResult` | nop-code-core | indexFile, getByPath, findFiles | 暴露文件解析细节 |
| `CodeAnnotationUsage` | nop-code-core | usages (BizLoader) | 注解模型直接暴露 |

**影响**：
- API 消费者（前端、AI客户端）与内部实现强耦合
- 修改 core 层模型会破坏 API 契约
- 无法独立版本化 API

**改进建议**：
1. 创建 `CodeSymbolDTO`（或扩展现有 `SymbolInfoDTO`），在 BizModel 层做转换
2. 所有 GraphQL/RPC 返回值使用 DTO，core 模型不对外暴露
3. 参考 `TypeHierarchyDTO`、`CallHierarchyDTO`、`ImpactResultDTO` 的正确模式

#### 3.2 接口抽象被绕过（🟡 重要）

**问题描述**：`NopCodeIndexBizModel` 中的图分析方法绕过 `ICodeIndexService` 接口，直接转型到实现类。

```java
// NopCodeIndexBizModel.java
private CallGraph getCallGraph(String indexId) {
    if (codeIndexService instanceof CodeIndexService) {
        return ((CodeIndexService) codeIndexService).getCallGraph(indexId);
    }
    return null;
}
```

同样的模式出现在：
- `getSymbolTable()`
- `triggerIncrementalIndex()`（访问 `getAnalyzer()`）

**问题**：
- `ICodeIndexService` 接口缺少 `detectCommunities`、`getGraphAnalysis`、`getImpactAnalysis` 方法
- 无法替换 `CodeIndexService` 实现为其他实现
- `instanceof` 检查 + 强转是经典的反模式

**改进建议**：
1. 在 `ICodeIndexService` 中补充缺失的方法
2. 将 `getCallGraph()`/`getSymbolTable()` 内联到服务方法中，不暴露内部数据结构
3. 移除所有 `instanceof` + 强转代码

#### 3.3 错误处理不符合规范

**审计要点**：所有异常应使用 `NopException` + `ErrorCode`，不应抛出 `RuntimeException`。

**违规位置**：
| 文件 | 行 | 当前写法 | 应改为 |
|------|-----|---------|-------|
| CodeIndexService | - | `throw new RuntimeException("Failed to index...")` | `NopException(ERR_INDEX_FAILED)` |
| NopCodeIndexBizModel | 72 | `throw new RuntimeException("Incremental indexing requires...")` | `NopException(ERR_INCREMENTAL_NOT_SUPPORTED)` |
| NopCodeIndexBizModel | 102 | `throw new RuntimeException("Incremental index failed: ...")` | `NopException(ERR_INCREMENTAL_FAILED).cause(e)` |

#### 3.4 参数一致性

| 检查项 | 状态 | 说明 |
|--------|------|------|
| indexId 始终为必要参数 | ⚠️ | 部分方法用 `@Optional` 标注 indexId（usages, sourceCode），部分未标注 |
| 分页参数命名 | ✅ | offset/limit 命名一致 |
| 方向参数命名 | ✅ | direction 统一 |
| @Optional 使用一致性 | ❌ | getTypeHierarchy/getCallHierarchy 的 direction/maxDepth 缺少 @Optional |

---

### 维度四：核心分析引擎

#### 4.1 Java 解析能力

| Java 特性 | 支持状态 | 说明 |
|-----------|---------|------|
| 类/接口/枚举/注解 | ✅ | JavaParser 3.26.3 完整支持 |
| 泛型类型 | ✅ | Symbol Solver 解析泛型 |
| Lambda 表达式 | ✅ | 方法调用捕获 |
| 注解处理 | ✅ | 包括注解属性提取 |
| Record 类 | ❌ | **JavaFileAnalyzer 无 RecordDeclaration 处理器**，record 被静默忽略 |
| module-info.java | ❌ | 无模块声明处理 |
| sealed class | ❌ | 未检查 |
| pattern matching | ❌ | 未检查 |
| 文本块 | ✅ | 语法层面支持 |

#### 4.2 类型系统缺失

**当前状态**：所有类型信息以字符串存储（returnType="List\<String\>"），无结构化的类型表示。

**缺失能力**：
- 无法程序化解析泛型参数
- 无法追踪类型间的继承/实现关系（只有 superClassName 字符串）
- 无法区分 `List<String>` 和 `List<Integer>`
- 无法解析类型参数的边界（`<T extends Comparable<T>>`）

**改进建议**：创建 `CodeTypeReference` 模型，包含 qualifiedName、typeArguments、isArray、wildcard 等字段。

#### 4.3 图算法质量

| 算法 | 复杂度 | 大图优化 | 潜在问题 |
|------|-------|---------|---------|
| CommunityDetector (Leiden) | O(n log n) | ✅ 自动检测大图，降级策略 | ⚠️ 边去重哈希可能溢出（`minIdx * maxIdx`） |
| CommunityDetector (LabelPropagation) | O(n) | ✅ 同上 | 无 |
| EntryPointScorer | O(V+E) | 不需要 | 无 |
| ImpactAnalyzer | O(V+E) | 有 maxDepth/maxNodes 限制 | ⚠️ BFS 用 String[] 存深度，效率低 |

#### 4.4 内存管理（🔴 严重）

**数据冗余**：`CodeIndexService` 用4个 Map 存储同一份数据的不同视图，内存浪费约50%。

```
fileResultsMap    ──┐
symbolTableMap   ──┤── 实际都来自同一个 ProjectAnalysisResult
callGraphMap     ──┤
analysisResultsMap ──┘ （已包含前三者的全部数据）
```

**无驱逐机制**：
- 索引数据只增不减
- 无 LRU/TTL/大小限制
- 多个大项目索引可导致 OOM

**源码常驻内存**：
- `CodeFileAnalysisResult.sourceCode` 存储完整文件内容
- 分析完成后不再需要，但未释放
- 10000个文件 × 平均20KB = ~200MB 纯源码占用

**改进建议**：
1. 移除 `fileResultsMap`、`symbolTableMap`、`callGraphMap`，仅保留 `analysisResultsMap`
2. 使用 Caffeine 缓存替代 ConcurrentHashMap，配置 maxSize 和 expireAfterAccess
3. 分析完成后清理 `sourceCode` 字段，仅在需要时按需读取

---

### 维度五：前端页面与用户体验

#### 5.1 页面功能完整度

| 页面 | 状态 | 问题 |
|------|------|------|
| 概览仪表盘 (dashboard) | ⚠️ 部分可用 | statsForm 缺少 cells/domain，依赖 layout-only |
| 代码浏览 (code-browser) | ❌ **不可用** | queryForm 用了 editMode="query"（会加 filter_ 前缀），无 cells，无结果展示 |
| 类型层级 (type-hierarchy) | ⚠️ 部分可用 | 表单正常，API正确，但提交后**无结果展示区域** |
| 调用链 (call-hierarchy) | ⚠️ 部分可用 | 同上，initApi 加载数据但无可视化 |
| 符号搜索 (NopCodeSymbol) | ✅ 可用 | 最完整的页面，有搜索、分页、详情查看 |
| 索引管理 (NopCodeIndex) | ✅ 可用 | 有 CRUD、触发索引、查看统计 |
| NopCodeFile | ❌ 空壳 | 仅继承生成页面，无自定义 |
| NopCodeUsage | ❌ 空壳 | 仅继承生成页面，无自定义 |
| NopCodeCall | ❌ 空壳 | 仅继承生成页面，无自定义 |
| NopCodeInheritance | ❌ 空壳 | 仅继承生成页面，无自定义 |
| NopCodeAnnotationUsage | ❌ 空壳 | 仅继承生成页面，无自定义 |

#### 5.2 缺失的关键页面

| 缺失页面 | 后端API已有 | 优先级 |
|----------|-----------|--------|
| 图分析仪表盘（社区检测、God Node、孤立符号） | ✅ detectCommunities, getGraphAnalysis | 高 |
| 影响分析页面 | ✅ getImpactAnalysis | 高 |
| 代码浏览器（文件树 + 语法高亮 + 符号大纲） | ✅ getByPath, fileTree | 高 |
| 类型/调用层级可视化（树形组件） | ✅ getTypeHierarchy, getCallHierarchy | 中 |

#### 5.3 E2E 测试覆盖

**已覆盖**：符号搜索（5个测试）、类型层级（5个测试）
**未覆盖**：仪表盘、代码浏览、调用链页面、索引管理、图分析、符号详情查看

---

### 维度六：行业对标与能力差距

与成熟代码智能工具对比，识别 nop-code 的定位和差距。

#### 6.1 LSP 能力对照

| LSP 操作 | nop-code 状态 | 差距 |
|----------|-------------|------|
| documentSymbol | ✅ getFileSymbols | 完整 |
| references | ✅ getSymbolUsages | 完整 |
| definition | ✅ 通过 qualifiedName 查找 | 需要精确的 ID 映射 |
| typeDefinition | ✅ getTypeHierarchy | 完整 |
| hover | ✅ getSymbolSourceCode + documentation | 缺少类型推断信息 |
| rename | ❌ | 不支持，需语义分析 |
| codeAction | ❌ | 不支持 |
| diagnostics | ❌ | 不支持（无代码检查） |
| completion | ❌ | 不支持 |
| implementation | ❌ | 不支持（查找接口的所有实现类） |
| documentHighlight | ❌ | 不支持 |
| formatting | ❌ | 不支持 |
| signatureHelp | ❌ | 不支持 |

#### 6.2 静态分析能力对照（vs SonarQube）

| 能力 | nop-code | SonarQube | 差距 |
|------|---------|-----------|------|
| 代码异味检测 | ❌ | ✅ 丰富的规则库 | 完全缺失 |
| Bug模式检测 | ❌ | ✅ 基于模式匹配 | 完全缺失 |
| 安全漏洞检测 | ❌ | ✅ OWASP/SANS Top 10 | 完全缺失 |
| 圈复杂度 | ❌ | ✅ McCabe | 完全缺失 |
| 认知复杂度 | ❌ | ✅ SonarWay | 完全缺失 |
| 重复代码检测 | ❌ | ✅ CPD | 完全缺失 |
| 测试覆盖率 | ❌ | ✅ JaCoCo集成 | 完全缺失 |
| 质量门禁 | ❌ | ✅ Quality Gates | 完全缺失 |

**定位建议**：nop-code 定位为**代码索引与结构分析**工具，而非静态分析工具。不建议补齐 SonarQube 的能力，但应考虑：
- 增加**基础度量**：行数、方法数、类数、包数（部分已有）
- 增加**架构度量**：耦合度、内聚性（EntryPointScorer 已部分覆盖）
- 增加**依赖分析**：模块间依赖、循环依赖检测

#### 6.3 架构分析能力对照（vs ArchUnit/Structure101）

| 能力 | nop-code | ArchUnit | 差距 |
|------|---------|---------|------|
| 依赖结构矩阵 | ❌ | ✅ | 完全缺失 |
| 层级违规检测 | ❌ | ✅ | 有 CallGraph 但未做规则检查 |
| 循环依赖检测 | ❌ | ✅ | 有社区检测可间接发现 |
| 耦合度量 | ⚠️ | ✅ | EntryPointScorer 部分 |
| 包依赖可视化 | ❌ | ✅ | 缺可视化 |

**建议**：在现有 CallGraph + CommunityDetector 基础上，增加循环依赖检测和包级依赖分析，这是投入产出比最高的改进方向。

#### 6.4 工程化差距（vs 成熟工具的设计模式）

| 设计模式 | nop-code | Sourcegraph/SonarQube | 差距 |
|----------|---------|----------------------|------|
| 增量分析 | ✅ SHA-256指纹 | ✅ LSIF/SCIP | 有基础但不够精细 |
| 持久化存储 | ❌ 纯内存 | ✅ PostgreSQL/SQLite | **最大差距** |
| 查询优化 | ❌ 全量遍历 | ✅ 索引+缓存 | 100k+符号时性能堪忧 |
| 语言无关IR | ❌ 每语言独立 | ✅ SCIP/LSIF | 跨语言分析受限 |
| 分布式分析 | ❌ 单机 | ✅ 可水平扩展 | 大项目受限 |

---

### 维度七：可扩展性与多语言支持

#### 7.1 多语言架构

**设计评价**：`LanguageAdapterRegistry` + `ILanguageAdapter` 接口设计良好，新增语言只需实现接口并注册。

**已实现**：
- Java（JavaParser 3.26.3，最完整）
- Python（Tree-sitter，基础支持）
- TypeScript（Tree-sitter，基础支持）

**限制**：
- 无跨语言分析（Java 调用 JavaScript 无法追踪）
- Python/TypeScript 解析器功能远弱于 Java（无符号解析、无类型推断）
- 无统一的语言无关中间表示（IR）

#### 7.2 增量分析

**已实现**：SHA-256 文件指纹 + mtime/size 两阶段检测
**局限**：增量分析后仍需全量重建全局符号表

#### 7.3 外部符号引用

**完全缺失**：无法索引 JDK 类库或第三方依赖（如 Spring Framework）。
- `ReflectionTypeSolver` 只能解析 JDK 类，无法解析项目依赖
- 调用链在到达外部库边界时中断
- 影响分析在依赖密集的代码中精度不足

---

### 维度八：测试质量

| 测试类型 | 覆盖范围 | 质量 | 缺口 |
|----------|---------|------|------|
| 单元测试（core） | 图算法、增量检测、模型 | ✅ 良好（60-70%） | 缺 Record/lambda 测试 |
| 单元测试（lang-java） | JavaFileAnalyzer | ⚠️ 基础（~30%） | 缺复杂场景 |
| 集成测试（service） | CodeIndexService, BizModel | ⚠️ 基础 | 缺错误场景测试 |
| E2E 浏览器测试 | 符号搜索、类型层级 | ✅ 良好（10个测试） | 只覆盖2个页面 |
| 性能测试 | 无 | ❌ 完全缺失 | 需要基准测试 |
| 并发测试 | 无 | ❌ ConcurrentHashMap 无并发测试 | 需要验证线程安全 |

---

## 优先改进路线图

### P0 — 架构断裂（影响正确性和可用性）

| # | 改进项 | 工作量 | 影响 |
|---|--------|-------|------|
| 1 | **决定持久化策略**：选择方案 A/B/C 并执行 | 大 | 消除双存储断裂 |
| 2 | **修复 code-browser 页面**：editMode + cells + 结果展示 | 小 | 恢复页面可用性 |
| 3 | **添加层级/调用链结果可视化** | 中 | 两个页面从不可用到可用 |

### P1 — 代码质量（影响可维护性和规范合规）

| # | 改进项 | 工作量 | 影响 |
|---|--------|-------|------|
| 4 | DTO 隔离：BizModel 返回 DTO 而非 core 模型 | 中 | API 契约稳定化 |
| 5 | 补全 ICodeIndexService 接口，移除 instanceof 强转 | 中 | 消除抽象泄漏 |
| 6 | 统一错误处理：RuntimeException → NopException + ErrorCode | 小 | 符合项目规范 |
| 7 | 内存优化：移除冗余 Map，添加驱逐机制 | 中 | 防止 OOM |

### P2 — 能力补齐（影响功能完整性）

| # | 改进项 | 工作量 | 影响 |
|---|--------|-------|------|
| 8 | 添加 Record 类支持 | 小 | Java 17+ 完整性 |
| 9 | 创建图分析仪表盘页面 | 中 | 解锁社区检测/影响分析 |
| 10 | 添加循环依赖检测功能 | 中 | 高价值的架构分析能力 |
| 11 | 补充 E2E 测试覆盖 | 中 | 覆盖所有关键页面 |

### P3 — 架构演进（影响长期可扩展性）

| # | 改进项 | 工作量 | 影响 |
|---|--------|-------|------|
| 12 | 添加结构化类型系统（CodeTypeReference） | 大 | 类型感知分析的基础 |
| 13 | 外部符号引用注册表 | 大 | 完整调用链追踪 |
| 14 | 重构 api 模块或明确废弃 | 小 | 消除架构混乱 |
| 15 | 性能基准测试 | 中 | 量化可扩展性上限 |

---

## 审计方法说明

此文档应结合以下方式使用：

1. **每次重大迭代前**，对照 P0/P1 列表检查是否有新增违规
2. **PR 审查时**，检查是否违反维度三（API规范）和维度四（core层质量）
3. **季度回顾时**，评估 P2/P3 路线图进展

**审计数据来源**：2026-05-05 通过 5 个并行 explore/librarian agent 对 nop-code 全模块进行的深度分析。
