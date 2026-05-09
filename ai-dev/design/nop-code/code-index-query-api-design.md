# nop-code 扩展功能设计

**日期**：2026-05-09
**范围**：`nop-code` 模块功能扩展
**灵感来源**：ast-grep（`~/ai/ast-grep`）、ast-outline（`~/ai/ast-outline-rs`、`~/ai/ast-outline-py`）
**目标**：参考 ast-grep 和 ast-outline 的核心能力，识别 nop-code 的功能缺口，设计高价值扩展

---

## 一、现状对比

### 1.1 nop-code 已有能力

| 能力 | 实现位置 | 说明 |
|------|----------|------|
| 符号提取 | `ICodeFileAnalyzer.analyze()` | 类/接口/枚举/方法/字段/注解/函数 + 位置 + 签名 + 文档 |
| 调用图 | `CodeMethodCall` | 方法级 caller → callee |
| 继承图 | `CodeInheritance` | extends / implements |
| 注解使用 | `CodeAnnotationUsage` | annotated_by 关系 |
| 社区检测 | `CommunityDetector` | Leiden + LabelPropagation |
| 入口点评分 | `EntryPointScorer` | God Node / Entry Point 识别 |
| 影响分析 | `ImpactAnalyzer` | BFS 变更影响范围 |
| 增量索引 | `IncrementalDetector` | fingerprint + manifest |
| 多语言 | Java / Python / TypeScript | `CodeFileAnalyzer` 适配器模式 |
| 项目级分析 | `ProjectAnalyzer` | 全项目扫描 + 跨文件解析 |
| 持久化 + API | `CodeIndexService` + BizModel | ORM + GraphQL |

### 1.2 ast-grep 核心能力（nop-code 缺失）

| 能力 | 说明 | 对 nop-code 的价值 |
|------|------|-------------------|
| **AST 模式搜索** | 写代码模式（如 `$A && $A()`）搜索匹配的 AST 节点 | 🔴 高：结构化代码搜索是 AI 代码分析的基础能力 |
| **代码重写/变换** | 基于模式自动替换代码（`--rewrite`） | 🟡 中：自动化重构、批量修改 |
| **规则系统** | YAML 规则 + 约束（follows, inside, has, not 等） | 🟡 中：可复用的代码规则库，linting |
| **多语言** | 基于 Tree-sitter，支持 20+ 语言 | 🟢 低：nop-code 已有 Java/Python/TS，扩展成本高 |

### 1.3 ast-outline 核心能力（nop-code 缺失）

| 能力 | 说明 | 对 nop-code 的价值 |
|------|------|-------------------|
| **文件大纲（outline）** | 快速获取文件结构概览（签名 + 行范围，无方法体） | 🔴 高：AI agent 导航代码库的关键入口，节省 95% token |
| **符号定位（show）** | 按名称获取特定方法/类的源码 | 🔴 高：精确读取，替代全文扫描 |
| **模块摘要（digest）** | 一页纸展示模块内所有类的公共 API | 🔴 高：快速理解模块职责 |
| **公共 API Surface** | 解析 `pub use` / `__all__` / re-exports，发现真实公共 API | 🟡 中：理解模块边界 |
| **依赖图** | 文件级 import 依赖图 + 反向依赖 + 循环检测（Tarjan SCC） | 🔴 高：影响分析的输入，架构治理 |
| **implements 查询** | 精确查找实现/继承某接口的所有类 | 🔴 高：已提取继承数据，只差查询 API |
| **语义搜索** | BM25 + dense embedding 混合搜索 | 🟢 低：依赖向量基础设施，优先级低于结构化查询 |
| **find-related** | 结构相似代码查找 | 🟢 低：同上 |

---

## 二、扩展方案

### 优先级排序原则

1. **数据已有，只差查询 API** → P0（投入低，价值高）
2. **核心新能力，AI 代码分析必需** → P1
3. **增强型能力，锦上添花** → P2
4. **需要新基础设施** → P3（规划但暂不实施）

---

### P0：已有数据的查询扩展

#### 2.1 文件大纲（File Outline）

**现状**：`CodeFileAnalysisResult.symbols` 已包含每个符号的 line/endLine/signature，但无 API 直接返回大纲。

**方案**：在 `NopCodeIndexBizModel` 或新建查询 BizModel 中增加 GraphQL query：

```graphql
type Query {
  """获取文件大纲：符号列表 + 签名 + 行范围，不含方法体"""
  nopCode__fileOutline(
    indexId: String!
    filePath: String!
    includePrivate: Boolean = false
    includeFields: Boolean = true
  ): [NopCodeOutlineEntry!]!
}

type NopCodeOutlineEntry {
  kind: NopCodeSymbolKind!
  name: String!
  signature: String
  accessModifier: NopCodeAccessModifier
  line: Int!
  endLine: Int!
  documentation: String
  children: [NopCodeOutlineEntry!]   # 嵌套展示（类→方法/字段）
}
```

**实现要点**：
- 从 `nop_code_symbol` 表按 `FILE_ID + INDEX_ID` 查询，`FILE_ID` 通过 `nop_code_file.filePath` 关联查找
- **查询策略**：单次 SQL 查询获取该文件所有符号 → 内存中按 `parent_id` 组装嵌套树（避免 N+1 问题，500+ 符号的文件也能一次性处理）
- `includePrivate=false` 时过滤 `accessModifier = PRIVATE`

#### 2.2 模块摘要（Module Digest）

**方案**：批量查询一个目录下所有文件的公共符号，压缩为一页摘要。

```graphql
type Query {
  """获取目录摘要：每个文件只保留公共 API 符号"""
  nopCode__moduleDigest(
    indexId: String!
    dirPath: String!
    includePrivate: Boolean = false
    maxMembersPerFile: Int = 50
  ): [NopCodeFileDigest!]!
}

type NopCodeFileDigest {
  filePath: String!
  packageName: String
  symbols: [NopCodeOutlineEntry!]!   # 只含 kind in [CLASS,INTERFACE,ENUM,METHOD,PUBLIC_FIELD]
}
```

#### 2.3 符号源码定位（Symbol Show）

**方案**：按符号名查找并返回源码片段。

```graphql
type Query {
  """获取符号的源码"""
  nopCode__showSymbol(
    indexId: String!
    qualifiedName: String!
    includeBody: Boolean = true       # false 时只返回签名
  ): NopCodeSymbolSource
}

type NopCodeSymbolSource {
  filePath: String!
  qualifiedName: String!
  startLine: Int!
  endLine: Int!
  sourceCode: String                  # 对应行的源码文本
}
```

**实现要点**：
- 从 `nop_code_symbol` 查 `qualified_name`
- **SourceCodeProvider 接口定义**：

```java
public interface ISourceCodeProvider {
    /** 按文件路径和行号范围读取源码片段。行号约定：1-based，包含 startLine 和 endLine */
    String readSourceRange(String filePath, int startLine, int endLine);
}
```

- **主实现：文件系统读取**（直接按行号读取源文件对应行）
- **DB 源码存储暂不可用**：`nop_code_file.SOURCE_CODE` 列虽存在于 ORM 模型中，但当前 `CodeIndexService.saveFileResultInSession()` 未写入该列（`entityToFileResult()` 注释 `// sourceCode not stored in DB`）。未来优化需：(a) 修正列 domain 从 `jsonContent` 改为纯文本类型，(b) 确保精度支持大文件（当前 64KB 可能不足），(c) 修改 `saveFileResultInSession()` 实际持久化源码
- `SourceCodeProvider` 抽象接口保留，文件系统读取为主实现，DB 读取为未来优化

#### 2.4 implements 查询

**现状**：`nop_code_inheritance` 表已有 extends/implements 数据。

**方案**：

```graphql
type Query {
  """查找所有实现/继承指定类型的类"""
  nopCode__findImplementations(
    indexId: String!
    qualifiedName: String!
    directOnly: Boolean = false        # true 只查直接实现
    direction: NopCodeHierarchyDirection = SUB
    maxDepth: Int = 10                 # 传递闭包最大深度，防止广泛继承（如 Object）导致爆炸
    limit: Int = 500                   # 结果数量上限
  ): [NopCodeSymbol!]!
}
```

```graphql
enum NopCodeHierarchyDirection {
  SUB       # 查找子类型（实现类/子类）
  SUPER     # 查找父类型（父类/接口）
}
```

**实现要点**：
- 递归查询 `nop_code_inheritance` 表
- `directOnly=false` 时 BFS/DFS 传递闭包

#### 2.5 反向引用（Reverse References）

**现状**：`nop_code_usage` 表已有引用数据，但只有正向查找。

**方案**：

```graphql
type Query {
  """查找谁引用了指定符号（反向引用）"""
  nopCode__findReferencedBy(
    indexId: String!
    qualifiedName: String!
    kind: NopCodeReferenceKind        # READ/WRITE/CALL/TYPE_REFERENCE/...
  ): [NopCodeUsage!]!
}
```

---

### P1：核心新能力

#### 2.6 AST 模式搜索（Pattern Search）

**灵感**：ast-grep 的 `$A && $A()` 模式搜索。

**挑战**：ast-grep 基于 Tree-sitter，其核心是 Rust 实现的高性能 AST 匹配引擎。Java 生态没有等价物。

**方案**：分阶段实现。

**Phase A（简单文本模式 + 语义增强）**：

先不追求 AST 级别的模式匹配，而是利用已有符号索引提供增强搜索：

```graphql
type Query {
  """结构感知的代码搜索"""
  nopCode__searchCode(
    indexId: String!
    query: String!                    # 搜索查询
    searchType: NopCodeSearchType = COMBINED
    language: String                  # 可选语言过滤
    filePattern: String              # 可选文件 glob
    limit: Int = 50
  ): [NopCodeSearchResult!]!
}

enum NopCodeSearchType {
  SYMBOL_NAME     # 按符号名搜索（fuzzy）
  SIGNATURE       # 按签名模式搜索
  FULL_TEXT       # 全文搜索
  COMBINED        # 综合：先符号匹配，再全文补充
}

type NopCodeSearchResult {
  filePath: String!
  matchedSymbol: NopCodeSymbol       # 匹配到的符号（可能为空）
  matchType: NopCodeSearchType!
  line: Int!
  context: String                     # 匹配行周围的代码片段
  score: Float                        # 相关度
}
```

**Phase B（Tree-sitter 集成，未来）**：

如果需要真正的 AST 模式匹配，可通过 JNI 或子进程调用 ast-grep：

```java
public interface IPatternMatcher {
    List<PatternMatch> match(Path projectRoot, String pattern, String language);
}

// 子进程实现
public class AstGrepPatternMatcher implements IPatternMatcher {
    // 调用 ast-grep CLI：sg -p '$A && $A()' -l java
}
```

#### 2.7 依赖图（Dependency Graph）

**现状**：`CodeFileAnalysisResult.imports` 已提取每个文件的 import 列表，但未持久化为图结构。

**方案**：

**数据模型**：

```java
@DataBean
public class CodeFileDependency {
    private String indexId;              // 所属索引 ID（与其他表保持一致，支持多索引隔离）
    private String sourceFilePath;       // 谁导入了
    private String targetFilePath;       // 导入了谁
    private String importStatement;      // 原始 import 语句
    private boolean resolved;            // 是否解析到项目内文件
}
```

**数据来源**：`nop_code_file.IMPORTS` 列已存储 JSON 格式的 import 列表（`["java.util.List", "com.foo.Bar"]`），无需重新解析源码。依赖图构建直接消费该列数据，通过 `JsonTool.parseBeanFromText()` 解析后按语言规则映射到文件路径。

**持久化**：新增 `nop_code_dependency` 表或在现有结构中扩展。

**查询 API**：

```graphql
type Query {
  """正向依赖：这个文件导入了什么"""
  nopCode__deps(
    indexId: String!
    filePath: String!
    depth: Int = 1                    # 传递闭包深度
  ): NopCodeDepGraph!

  """反向依赖：谁导入这个文件（重构影响范围）"""
  nopCode__reverseDeps(
    indexId: String!
    filePath: String!
    depth: Int = 1
    limit: Int = 200
  ): NopCodeDepGraph!

  """查找循环依赖"""
  nopCode__findCycles(
    indexId: String!
    minSize: Int = 2
  ): [[String!]!]!                   # 每个数组是一个循环

  """完整依赖图"""
  nopCode__depGraph(
    indexId: String!
    includeExternal: Boolean = false
  ): NopCodeDepGraph!
}

type NopCodeDepGraph {
  nodes: [NopCodeDepNode!]!
  edges: [NopCodeDepEdge!]!
}

type NopCodeDepNode {
  filePath: String!
  inDegree: Int!
  outDegree: Int!
}

type NopCodeDepEdge {
  source: String!
  target: String!
}
```

**实现要点**：
- 在 `ProjectAnalyzer.analyzeProject()` 中，解析 import → 文件路径的映射
- 使用 Tarjan SCC 算法检测循环依赖
- 缓存依赖图到 `nop_code_dependency` 表

**import → 文件路径解析策略**（按语言区分）：

| 语言 | 解析规则 | 第三方判断 |
|------|----------|-----------|
| Java | `package → src/main/java/` 或 `src/test/java/` 映射，全限定名 → 路径 | 不以项目 base package 开头的为第三方 |
| Python | `from foo.bar import Baz` → `foo/bar.py` 或 `foo/bar/__init__.py` | 不在项目目录下的为第三方 |
| TypeScript | `import { X } from './foo'` → 相对路径解析；`import { X } from '@/foo'` → tsconfig paths 解析 | 非 `./`、`../` 开头且无 path mapping 的为第三方 |

**通配符处理**：`com.foo.*` / `from foo import *` → 标记为 `resolved=false`，不参与依赖图构建（精确度不够）。

**分阶段策略**：
1. Phase A：仅解析项目内 import（已确认在项目文件列表中的），第三方标记为 `resolved=false`
2. Phase B：支持 barrel file / re-export 解析（`index.ts` → 目录、`__init__.py` → 包）

---

### P2：增强型能力

#### 2.8 代码重写/变换

**方案**：基于 P1 的 AST 模式搜索结果，提供重写建议。初期不自动应用修改。

```graphql
type Mutation {
  """预览代码重写（不实际修改）"""
  nopCode__previewRewrite(
    indexId: String!
    pattern: String!
    replacement: String!
    language: String!
    filePattern: String
  ): [NopCodeRewritePreview!]!
}

type NopCodeRewritePreview {
  filePath: String!
  originalCode: String!
  rewrittenCode: String!
  startLine: Int!
  endLine: Int!
}
```

#### 2.9 公共 API Surface

**方案**：基于已有符号数据 + 访问修饰符，计算模块的真实公共 API。

```graphql
type Query {
  """获取目录的公共 API Surface"""
  nopCode__publicSurface(
    indexId: String!
    dirPath: String!
  ): [NopCodePublicAPI!]!
}

type NopCodePublicAPI {
  filePath: String!
  symbolName: String!
  qualifiedName: String!
  kind: NopCodeSymbolKind!
  signature: String!
  documentation: String
  isReExported: Boolean               # 是否通过 re-export 暴露
}
```

---

### P3：远期规划

#### 2.10 代码规则系统（Code Rule System）

**灵感**：ast-grep 的 YAML 规则 + 约束系统。

**价值**：可复用的代码规则库，用于 lint、架构约束检查、迁移辅助。Nop 平台已有 delta 定制机制，但缺少基于 AST 结构的代码规则。此能力与现有 lint 工具（Checkstyle/Pylint/ESLint）互补，聚焦于跨语言的结构化规则。

**方案**（仅规划，暂不实施）：
- 规则定义在 YAML/JSON 中，类似 ast-grep 的 rule 格式
- 利用已有符号索引 + 源码定位实现规则匹配
- 可通过 `nop-code-service` 的 GraphQL mutation 提交规则，通过 query 获取匹配结果

#### 2.11 语义搜索（Semantic Search）

需要向量存储基础设施（embedding model + vector store），不在当前范围内。可通过 `nop-ai` 模块集成。

#### 2.12 LSP 集成

提供 Language Server Protocol 接口，支持编辑器实时反馈。需要独立的设计文档。

---

## 三、错误处理约定

所有新增查询 API 统一遵循以下约定：

| 场景 | 行为 |
|------|------|
| 查询返回空结果 | 返回空列表 `[]`（不抛异常） |
| 无效 `indexId` | 抛 `NopException` + `NopCodeCoreErrors.ERR_CODE_INDEX_NOT_FOUND`，`.param("indexId", indexId)` |
| `filePath` 未找到 | 返回 `null` |
| `qualifiedName` 未找到 | 返回 `null` |
| 参数格式错误（如空字符串） | 抛 `NopException` + 通用参数校验错误 |

---

## 四、实现路径

### Phase 1：P0 查询扩展（预估 3-5 天）

| 步骤 | 任务 | 涉及文件 |
|------|------|----------|
| 1 | 文件大纲 API | BizModel + GraphQL schema |
| 2 | 模块摘要 API | BizModel + GraphQL schema |
| 3 | 符号源码定位 API | BizModel + SourceCodeProvider |
| 4 | implements 查询 | BizModel + 递归查询 |
| 5 | 反向引用查询 | BizModel + 索引优化 |

**技术要点**：
- 所有 P0 功能只需新增 GraphQL query 方法 + 必要的数据库查询
- 不需要修改分析器核心代码
- 利用已有的 `nop_code_symbol` / `nop_code_usage` / `nop_code_inheritance` 表

### Phase 2：P1 核心新能力（预估 5-8 天）

| 步骤 | 任务 | 涉及文件 |
|------|------|----------|
| 1 | 依赖图数据模型 + 持久化 | 新增 `CodeFileDependency` + ORM 表 |
| 2 | import → 文件路径解析 | 修改 `ProjectAnalyzer` |
| 3 | 依赖图查询 API | GraphQL schema + Tarjan SCC |
| 4 | 结构感知搜索（Phase A） | 新增搜索 BizModel + 索引 |

### Phase 3：P2 增强能力（预估 3-5 天）

| 步骤 | 任务 |
|------|------|
| 1 | 代码重写预览 |
| 2 | 公共 API Surface |
| 3 | Tree-sitter 集成评估（ast-grep 桥接） |

---

## 五、与现有设计的关系

### 4.1 与 GraphQL 接口设计文档的关系

`nop-code/design/ai-code-index-graphql-design.md` 已定义了完整的 GraphQL schema（~1500 行），包含 `NopCodeIndex` / `NopCodeSymbol` 等类型。本设计中的新 query 方法应**补充**到该 schema 中，复用已定义的类型。

### 4.2 与多语言索引设计的关系

`ai-dev/design/nop-code/language-agnostic-code-index-design.md` 定义了语言无关的代码模型。本设计的所有功能基于该通用模型，不引入语言特定逻辑。

### 4.3 与语义边设计的关系

`ai-dev/design/nop-code/semantic-edge-design.md` 定义了语义边（INFERRED/AMBIGUOUS）。本设计的依赖图（P1）和搜索（P1）与语义边互补：依赖图是结构化的 EXTRACTED 边，语义边是推断关系。

---

## 六、技术决策

### 5.1 为什么不直接集成 ast-grep？

| 因素 | 分析 |
|------|------|
| 架构 | ast-grep 是 Rust 实现，Java 集成需要 JNI（graal-sdk）或子进程调用 |
| 复杂度 | 引入一个 Rust 工具链依赖增加部署复杂度 |
| 覆盖度 | nop-code 已有 Java/Python/TS 分析器，覆盖了主要场景 |
| 替代方案 | Phase A 先用已有索引提供结构搜索，Phase B 按需桥接 ast-grep |

### 5.2 为什么不直接使用 ast-outline？

| 因素 | 分析 |
|------|------|
| 数据已有 | nop-code 已提取符号、位置、签名，缺少的只是查询 API |
| 生态 | ast-outline 是独立 CLI，与 Nop 平台的 GraphQL 服务架构不匹配 |
| 扩展性 | nop-code 已有 BizModel + ORM + 增量索引，在此基础上扩展更自然 |
| 增量 | ast-outline 不支持增量索引，每次全量扫描 |

### 5.3 查询 API 的归属

所有新 query 方法归属到 `NopCodeIndexBizModel`（聚合根），不新建 BizModel。查询操作天然属于索引实体的职责范围。

---

## 七、风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| import → 文件路径解析不准确 | 中 | 支持模糊匹配 + 按语言自定义解析策略 |
| 大项目依赖图性能 | 中 | 按目录分区 + 缓存 + 分页查询 |
| 符号名搜索模糊度 | 低 | 先精确匹配 → 前缀匹配 → fuzzy，分阶段 |
| SourceCodeProvider 需要访问源文件 | 低 | VFS 抽象层已支持，本地路径也已兼容 |
