# nop-code 功能补齐实现计划

> Plan Status: **near completion** — Phase 0-4 全部实现，仅剩 P2/P3 扩展功能未实现
> Last Reviewed: 2026-05-11
> Source: graphify 对比分析、design/ai-code-index-graphql-design.md
> Prerequisite: 05-nop-code-multi-language-index-plan.md (已完成)
> Binding Decision: io.github.tree-sitter:jtreesitter (官方 Panama/FFM, JDK 22+)

## 目的

补齐 nop-code 相对于 graphify 的功能差距，并实现已设计但未交付的 GraphQL 自定义查询层。

## 当前状态

| 模块 | 状态 | 说明 |
|------|------|------|
| nop-code-core | ✅ 完成 | 通用模型（5 枚举 + 5 @DataBean + 6 接口 + 2 图结构 + 1 Registry）、4 个分析算法 |
| nop-code-lang-java | ✅ 完成 | JavaCodeFileAnalyzer + JavaLanguageAdapter，31 tests pass |
| nop-code-lang-python | ✅ 完成 | PythonCodeFileAnalyzer + PythonLanguageAdapter，15 tests pass (tree-sitter) |
| nop-code-lang-typescript | ✅ 完成 | TypeScriptCodeFileAnalyzer + TypeScriptLanguageAdapter，15 tests pass (tree-sitter) |
| nop-code-service | ✅ 完成 | 10+ @BizQuery 自定义查询 + @BizLoader 关联加载 + 图分析 BizModel |
| nop-code.orm.xml | ✅ 完成 | 8 表（含 NopCodeDependency），扩展字典 |
| GraphQL 设计 | ✅ 已实现 | design/ai-code-index-graphql-design.md 设计 → CodeIndexService + BizModel 实现 |

### 已有 GraphQL 能力澄清

CrudBizModel 已自动暴露 GraphQL CRUD 操作。7 个 BizModel **已经可以通过 GraphQL 进行基础 CRUD**：
- `NopCodeSymbol__get`、`NopCodeSymbol__findPage`、`NopCodeSymbol__save` 等
- 同理 NopCodeFile、NopCodeCall、NopCodeInheritance 等均有标准 CRUD

缺失的是（已全部实现）：
1. ~~**@BizQuery 自定义查询**~~（符号搜索、调用链、继承树、outline 等）→ ✅ 已实现
2. ~~**@BizLoader 关联字段**~~（嵌套加载 symbols、callers、superTypes 等）→ ✅ 已实现

### ORM 实体与 GraphQL 设计文档的差异

设计文档定义了 NopCodeClass/NopCodeInterface/NopCodeMethod 等独立类型，但**实际 ORM 只有一张 nop_code_symbol 表**，通过 `kind` 字段（CLASS/INTERFACE/ENUM/METHOD/FIELD/...）区分。GraphQL 查询不需要独立表，在 @BizLoader 中按 kind 过滤即可。

现有 ORM 实体关系：
- `NopCodeIndex` → to-many → NopCodeFile, NopCodeSymbol
- `NopCodeFile` → to-one → NopCodeIndex; to-many → NopCodeSymbol
- `NopCodeSymbol` → to-one → NopCodeIndex, NopCodeFile, parent; 字段：kind, name, qualifiedName, accessModifier, signature, returnType, fieldType 等
- `NopCodeCall` → callerId, calleeId, fileId, line
- `NopCodeInheritance` → parentId, childId, relationType (EXTENDS/IMPLEMENTS)
- `NopCodeUsage` → symbolId, usageSymbolId, usageKind
- `NopCodeAnnotationUsage` → symbolId, annotationName

### 残留清理项

1. `nop-utils/nop-java-parser/pom.xml` 仍依赖 jgrapht-core:1.5.2 和 networkanalysis:1.3.0，但 src/main/java 中已无任何 import（4 个算法类已移到 nop-code-core）。**应删除这两个残留依赖**。
2. `nop-ai/nop-ai-skills/nop-ai-code-analyzer` 依赖 nop-java-parser，但未使用 4 个算法类。**不受影响**。

## 成功标准

- [SC1] `nop-code-lang-python` 模块编译，能解析 Python 文件生成 CodeFileAnalysisResult
- [SC2] `nop-code-lang-typescript` 模块编译，能解析 TypeScript 文件生成 CodeFileAnalysisResult
- [SC3] GraphQL 自定义查询可用：符号搜索、按全限定名查找、文件 outline、调用链、继承树
- [SC4] @BizLoader 关联字段按需加载，避免 N+1
- [SC5] 全量 `mvn install` 通过（含 nop-java-parser）
- [SC6] nop-java-parser 残留依赖已清理
- [SC7] 每个 Task 均有配套单元测试覆盖核心功能路径
- [SC8] 单文件代码行数 ≤ 500 行（超限时拆分为多个类）
- [SC9] 增量索引可用：文件变化检测 + 只重新解析变更文件 + 过期数据清理
- [SC10] 跨文件调用解析覆盖所有已实现语言（Java + Python + TypeScript）

## 全局约束

### C-TEST: 单元测试覆盖

- 每个 Task（P1/T1/G1-G4）完成后必须附带单元测试，测试与代码同 Task 交付
- 测试覆盖核心功能路径：正常输入、边界输入（空/null/无效语法）
- 测试类与源码类 1:1 对应（如 `PythonCodeFileAnalyzer` → `TestPythonCodeFileAnalyzer`）
- 不依赖外部服务或数据库（单元测试级别），集成测试在 G5 中单独处理

### C-500: 单文件 ≤ 500 行

- 任何 `.java` 文件不超过 500 行（含 import、注释）
- 超限时按职责拆分：
  - Facade 类拆为 `XxxFacade`（AST 遍历）+ `XxxSymbolExtractor`（符号提取）+ `XxxRelationExtractor`（关系提取）
  - BizModel 类拆为 `XxxBizModel`（@BizQuery）+ `XxxBizLoader`（@BizLoader），通过 `@BizLoader` 注解可分文件
- pom.xml、beans.xml 等配置文件不受此限

## Non-Goals

- [NG1] 非 AST 内容索引（PDF/图片/视频）— graphify 多模态特性，不适用于企业级代码索引
- [NG2] MCP Server — nop-code 通过 GraphQL 服务暴露
- [NG3] HTML 可视化导出 — 前端展示层
- [NG4] C/C++/Go/Rust 等 15+ 语言 — 后续扩展
- [NG5] Neo4j/GraphML 导出 — 可选增强

---

## Closure Gates

> All gates must be `[x]` before `Plan Status` can change to `completed`.

- [ ] All in-scope language adapters (Python, TypeScript) compile and pass tests
- [ ] GraphQL custom queries (@BizQuery/@BizMutation) are functional and tested
- [ ] Incremental indexing works end-to-end: full index → modify file → incremental update → only changed files processed
- [ ] Cross-file call resolution covers all implemented languages (Java + Python + TypeScript)
- [ ] All applicable build/test gates pass (`mvn install` for nop-code module group)
- [ ] nop-java-parser residual dependencies cleaned up
- [ ] Affected `docs-for-ai/` docs synced to live baseline, or `No doc update required`
- [ ] No in-scope item was silently downgraded to deferred / follow-up

## Deferred But Adjudicated

### Neo4j/GraphML export (NG5)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG5. Not required for core code index functionality.
- Successor Required: `no`
- Successor Path: N/A

### C/C++/Go/Rust language support (NG4)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG4. 15+ language support is future expansion beyond current scope (Java + Python + TypeScript).
- Successor Required: `no`
- Successor Path: N/A

### MCP Server (NG2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG2. nop-code exposes via GraphQL, not MCP.
- Successor Required: `no`
- Successor Path: N/A

### HTML visualization export (NG3)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG3. Frontend display concern, not backend.
- Successor Required: `no`
- Successor Path: N/A

### Non-AST content indexing — PDF/image/video (NG1)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG1. graphify multimodal feature, not applicable to enterprise code index.
- Successor Required: `no`
- Successor Path: N/A

## Non-Blocking Follow-ups

- Semantic edge extraction (LLM-assisted, designed in `ai-dev/design/nop-code/semantic-edge-design.md`) — Phase 2 of semantic edge design, depends on nop-ai integration
- AST result caching — graphify has dual-layer caching (AST + semantic) because LLM extraction is expensive; nop-code is pure AST parsing with low overhead, no cache needed
- File watching / git hooks — nop-code uses GraphQL mutation trigger, caller (CI/CD or IDE plugin) decides when to re-index

## Phase 0: 残留清理

### Task C1: 清理 nop-java-parser 残留依赖

Status: completed

Instructions:

1. 编辑 `nop-utils/nop-java-parser/pom.xml`，删除以下依赖（src/main 中已无 import）：
   ```xml
   <dependency>
       <groupId>org.jgrapht</groupId>
       <artifactId>jgrapht-core</artifactId>
       <version>1.5.2</version>
   </dependency>
   <dependency>
       <groupId>nl.cwts</groupId>
       <artifactId>networkanalysis</artifactId>
       <version>1.3.0</version>
   </dependency>
   ```

2. 验证 `cd nop-utils/nop-java-parser && ../../mvnw compile` 仍然通过

Exit Criteria:
- [ ] pom.xml 中无 jgrapht/networkanalysis 依赖
- [ ] nop-java-parser 编译通过

---

## Phase 1: Python 语言适配器（tree-sitter）

### Task P1: 创建 nop-code-lang-python 模块

Status: completed
Depends On: nop-code-core (已完成), C1

Instructions:

1. 创建 `nop-code/nop-code-lang-python/` 目录结构：
   ```
   pom.xml
   src/main/java/io/nop/code/lang/python/
       TreeSitterPythonFacade.java      (~120行: Parser创建、Tree生命周期、Node遍历工具方法)
       PythonSymbolExtractor.java       (~150行: class_definition→CodeSymbolInfo, 位置信息)
       PythonRelationExtractor.java     (~100行: import→usage, inheritance, decorator)
       PythonLanguageAdapter.java       (~30行)
       PythonCodeFileAnalyzer.java      (~60行: 组合上述3个类)
   src/main/resources/_vfs/nop/code/beans/
       _lang-python.beans.xml
   src/test/java/io/nop/code/lang/python/
       TestPythonLanguageAdapter.java
       TestPythonCodeFileAnalyzer.java
       TestPythonSymbolExtractor.java
       TestPythonRelationExtractor.java
   ```

2. `pom.xml`:
   - parent: `io.github.entropy-cloud:nop-code`
   - artifactId: `nop-code-lang-python`
   - **JDK 22+**（覆盖父 pom，使用 Panama/FFM API）：
     ```xml
     <properties>
         <maven.compiler.source>22</maven.compiler.source>
         <maven.compiler.target>22</maven.compiler.target>
         <maven.compiler.release>22</maven.compiler.release>
     </properties>
     ```
   - dependencies:
     ```xml
     <dependency>
         <groupId>io.github.entropy-cloud</groupId>
         <artifactId>nop-code-core</artifactId>
     </dependency>
     <dependency>
         <groupId>io.github.tree-sitter</groupId>
         <artifactId>jtreesitter</artifactId>
         <version>0.26.0</version>
     </dependency>
     <dependency>
         <groupId>io.github.tree-sitter</groupId>
         <artifactId>tree-sitter-python</artifactId>
         <version>0.23.4</version>
     </dependency>
     ```

3. `TreeSitterPythonFacade.java` — 封装 tree-sitter Python 解析（~120行）：
   - `parse(String sourceCode)` → 返回 tree-sitter `Tree`
   - `findNodesByType(Node root, String type)` → 递归查找特定类型节点
   - `getNodeText(Node node, String source)` → 提取节点源文本
   - 位置信息工具：`getLine(Node)`, `getEndLine(Node)` 封装 `node.getStartPoint().row()`

4. `PythonSymbolExtractor.java` — 符号提取（~150行）：
   - `extractSymbols(Node root, String filePath)` → `List<CodeSymbolInfo>`
   - 节点类型映射：
     - `class_definition` → CodeSymbolKind.CLASS（name, body 中嵌套 method）
     - `function_definition` → CodeSymbolKind.FUNCTION / METHOD（在 class 内为 METHOD）
   - 提取 name、startLine、endLine、accessModifier（Python 默认 PUBLIC，_前缀为 PRIVATE）

5. `PythonRelationExtractor.java` — 关系提取（~100行）：
   - `extractRelations(Node root, List<CodeSymbolInfo> symbols)` → relationships
   - `decorator` → CodeSymbolInfo.annotationUsage
   - `argument_list`（class 继承）→ 解析 class argument_list 中的父类名
   - `import_statement` / `import_from_statement` → usage 记录

6. `PythonLanguageAdapter.java` — 实现 `ILanguageAdapter`（~30行）:
   - `getLanguage()` → `CodeLanguage.PYTHON`
   - `getFileExtensions()` → `[".py"]`
   - `getExcludePatterns()` → `["**/__pycache__/**", "**/venv/**", "**/.venv/**"]`
   - `getFileAnalyzer()` → new PythonCodeFileAnalyzer()

7. `PythonCodeFileAnalyzer.java` — 实现 `ICodeFileAnalyzer`（~60行）:
   - `analyze(filePath, sourceCode)` → `CodeFileAnalysisResult`
   - 内部组合：TreeSitterPythonFacade → PythonSymbolExtractor → PythonRelationExtractor

8. `_lang-python.beans.xml` — 注册 PythonLanguageAdapter

9. 更新 `nop-code/pom.xml` 添加 `<module>nop-code-lang-python</module>`

Exit Criteria:
- [ ] `cd nop-code/nop-code-lang-python && ../../mvnw compile` 成功
- [ ] PythonLanguageAdapter 实现了 ILanguageAdapter
- [ ] PythonCodeFileAnalyzer.analyze() 返回 CodeFileAnalysisResult
- [ ] tree-sitter Python 语法库正确加载（Parser 可 parse Python 源码）

### Task P2: Python 适配器单元测试

Status: completed
Depends On: P1

测试类与源码 1:1 对应：

1. `TestPythonLanguageAdapter`（~30行）：
   - getLanguage() == PYTHON, getFileExtensions() == [".py"], getExcludePatterns() 包含 "__pycache__"
   - getFileAnalyzer() 返回非 null

2. `TestPythonSymbolExtractor`（~80行）：
   - 解析简单 class → 1 个 CLASS symbol + 嵌套 METHOD symbols
   - 解析 class 继承（class Bar(Foo)）→ CLASS with superTypes
   - 解析 function + nested function → FUNCTION symbol
   - 解析带装饰器的 class → decorator 记录

3. `TestPythonRelationExtractor`（~60行）：
   - import statement → usage 记录
   - from-import → usage 记录
   - class inheritance → inheritance 关系
   - decorator → annotationUsage 记录

4. `TestPythonCodeFileAnalyzer`（~50行）：
   - 完整 Python 文件（class + methods + imports + decorator）→ 完整 CodeFileAnalysisResult
   - 空源码 → null/empty result
   - 无效 Python → parser 不崩溃（tree-sitter 容错），返回部分结果或空

Exit Criteria:
- [ ] 4 个测试类全部通过
- [ ] 覆盖：class、function、import、inheritance、decorator、空输入、无效输入
- [ ] `cd nop-code/nop-code-lang-python && ../../mvnw test` 全部通过

---

## Phase 1.5: 增量索引基础设施

> 参考 graphify 的增量更新机制，为 nop-code 实现生产可用的增量索引能力。
>
> **graphify 参考实现**：
> - `~/ai/graphify/graphify/detect.py` — `detect_incremental()` (L658-717): mtime + MD5 两级检测
> - `~/ai/graphify/graphify/detect.py` — `_md5_file()` (L623-633): 流式 MD5 计算
> - `~/ai/graphify/graphify/detect.py` — `load_manifest()` / `save_manifest()` (L636-655): manifest 持久化
> - `~/ai/graphify/graphify/cache.py` — `file_hash()` (L32-56): SHA-256 + 路径归一化
> - `~/ai/graphify/graphify/watch.py` — `_rebuild_code()` (L36-151): 增量重建（保留语义节点 + prune 旧文件）
> - `~/ai/graphify/graphify/build.py` — `build_merge()` (L181-233): `prune_sources` 删除指定文件的节点

### Task I1: ORM 模型增加增量索引字段

Status: completed
Depends On: nop-code-dao (已完成)

Instructions:

1. 编辑 `nop-code/model/nop-code.orm.xml`，为 `NopCodeIndex` 增加列：
   ```xml
   <!-- 在 STATUS 之后，propId=8 开始 -->
   <column code="LAST_INDEXED" displayName="最后索引时间" name="lastIndexed"
           propId="8" stdDataType="long" stdSqlType="BIGINT"/>
   <column code="INDEX_VERSION" displayName="索引版本" name="indexVersion"
           propId="9" stdDataType="int" stdSqlType="INTEGER"/>
   ```

2. 为 `NopCodeFile` 增加列：
   ```xml
   <!-- 在 SOURCE_CODE 之后，propId=9 开始 -->
   <column code="FILE_HASH" displayName="文件内容哈希" name="fileHash"
           precision="64" propId="9" stdDataType="string" stdSqlType="VARCHAR"/>
   <column code="LAST_MODIFIED" displayName="最后修改时间" name="lastModified"
           propId="10" stdDataType="long" stdSqlType="BIGINT"/>
   <column code="FILE_SIZE" displayName="文件大小" name="fileSize"
           propId="11" stdDataType="long" stdSqlType="BIGINT"/>
   ```

3. 在 `nop-code/model/nop-code-dict.xml` 中增加字典（如需要）

4. 重新生成 DAO：
   ```bash
   cd nop-code && ../mvnw install -pl nop-code-codegen -DskipTests
   cd nop-code-dao && ../mvnw install -DskipTests
   ```

Exit Criteria:
- [ ] NopCodeIndex 包含 lastIndexed、indexVersion 字段
- [ ] NopCodeFile 包含 fileHash、lastModified、fileSize 字段
- [ ] DAO 重新生成成功，编译通过

### Task I2: 增量检测与文件指纹工具

Status: completed
Depends On: I1, nop-code-core (已完成)

参考 graphify 实现：
- `detect.py` `detect_incremental()` (L658-717): 先比 mtime（快速），mtime 变了再比 MD5 hash
- `detect.py` `_md5_file()` (L623-633): 64KB 分块流式读取计算 MD5，避免大文件 OOM
- `detect.py` `save_manifest()` (L644-655): JSON 格式 {filePath: {mtime, hash}} 持久化
- `cache.py` `file_hash()` (L32-56): SHA-256 哈希 + 路径归一化（跨平台一致性）

Instructions:

1. 在 `nop-code-core` 中创建 `io.nop.code.core.incremental` 包：

   `FileFingerprint.java`（~30行）— 文件指纹 @DataBean：
   ```java
   @DataBean
   public class FileFingerprint {
       private String filePath;
       private String contentHash;    // SHA-256 hex
       private long lastModified;     // mtime millis
       private long fileSize;
   }
   ```

   `IncrementalDetector.java`（~150行）— 增量变化检测：
   - `computeFingerprint(Path file)` → `FileFingerprint`（SHA-256 + mtime）
   - `detectChanges(Path projectRoot, List<FileFingerprint> previous, List<Path> currentFiles)`
     → `ChangeSet`（新增/修改/删除/未变化文件列表）
   - 两级检测：先比 mtime（快速路径），mtime 变了再比 SHA-256（确认变化）

   `ChangeSet.java`（~40行）— 变更集 @DataBean：
   ```java
   @DataBean
   public class ChangeSet {
       private List<Path> addedFiles;
       private List<Path> modifiedFiles;
       private List<Path> deletedFiles;
       private List<Path> unchangedFiles;
   }
   ```

   `ManifestStore.java`（~80行）— manifest 持久化：
   - `save(Path manifestFile, List<FileFingerprint> fingerprints)` → JSON 文件
   - `load(Path manifestFile)` → `List<FileFingerprint>`
   - manifest 存储位置：`{projectRoot}/.nop-code/manifest.json`

2. 所有文件 ≤ 200 行，无需拆分

单元测试（`TestIncrementalDetector.java`，~80行）：
- 计算单个文件的 SHA-256 指纹
- 新增文件检测（previous 为空，current 有文件）
- 修改文件检测（同一文件，hash 不同）
- 未变化文件检测（同一文件，hash 相同）
- 删除文件检测（previous 有，current 无）
- mtime 变但内容不变 → 不报告为 modified（hash 一致）

Exit Criteria:
- [ ] IncrementalDetector 正确检测新增/修改/删除/未变化
- [ ] mtime 变但内容不变时不误报
- [ ] ManifestStore 可持久化和加载
- [ ] 单元测试通过

### Task I3: ProjectAnalyzer 增量分析实现

Status: completed
Depends On: I2

参考 graphify 实现：
- `watch.py` `_rebuild_code()` (L36-151): 核心增量重建逻辑——检测变更文件、提取 AST、合并到现有图、prune 旧节点
- `watch.py` `_rebuild_code()` (L66-91): 保留 semantic 节点的策略——从现有 graph.json 中保留非 AST 节点
- `build.py` `build_merge()` (L181-233): 合并新旧结果 + `prune_sources` 参数删除指定文件的节点
- `build.py` shrink guard: 拒绝节点数大幅缩减（防止误删）

Instructions:

1. 在 `ProjectAnalyzer.java` 中实现增量分析方法（~120行，当前 377 行，总共约 500 行）：

   ```java
   public IncrementalResult analyzeIncremental(Path projectRoot, ChangeSet changeSet)
       throws IOException {
       // 1. 加载上一次的 manifest
       List<FileFingerprint> previous = manifestStore.load(manifestPath(projectRoot));

       // 2. 扫描当前文件，计算指纹
       List<Path> currentFiles = findSourceFiles(projectRoot);
       List<FileFingerprint> currentFingerprints = computeFingerprints(currentFiles);

       // 3. 检测变化
       ChangeSet changes = IncrementalDetector.detectChanges(projectRoot, previous, currentFiles);

       // 4. 只解析变更文件（added + modified）
       List<CodeFileAnalysisResult> newResults = analyzeFiles(projectRoot, changes.getAddedAndModified());

       // 5. 返回增量结果（新增/更新/删除的文件分析结果）
       return new IncrementalResult(changes, newResults);
   }

   private List<CodeFileAnalysisResult> analyzeFiles(Path projectRoot, List<Path> files) {
       // 复用现有 analyzeProject 中的单文件分析逻辑，抽取为独立方法
   }
   ```

2. 将现有 `analyzeProject` 中的单文件分析逻辑（第 77-110 行）抽取为私有方法 `analyzeFile(Path, Path)`（~40行）

3. `IncrementalResult.java`（~30行）— @DataBean：
   ```java
   @DataBean
   public class IncrementalResult {
       private ChangeSet changeSet;
       private List<CodeFileAnalysisResult> updatedResults;
       private int filesProcessed;
       private int filesSkipped;
   }
   ```

4. 更新 `IProjectAnalyzer` 接口的 `analyzeIncremental` 签名（如需要匹配实际参数类型）

单元测试（`TestProjectAnalyzerIncremental.java`，~100行）：
- 使用临时目录创建测试文件
- 首次分析 → 全量结果
- 修改一个文件 → 增量分析只处理该文件
- 新增一个文件 → 增量分析处理新文件
- 删除一个文件 → 增量结果标记删除
- 不变更任何文件 → filesProcessed=0, filesSkipped=all

Exit Criteria:
- [ ] `analyzeIncremental()` 只解析变更文件
- [ ] 增量分析跳过未变化文件
- [ ] `analyzeFile()` 私有方法可复用
- [ ] ProjectAnalyzer.java ≤ 500 行
- [ ] 单元测试通过

### Task I4: 增量索引 BizModel 与 GraphQL Mutation

Status: completed
Depends On: I3, G1

Instructions:

1. 在 `nop-code-core` 中创建 DTO：

   `IncrementalUpdateResult.java`（~30行）— @DataBean：
   ```java
   @DataBean
   public class IncrementalUpdateResult {
       private int addedFiles;
       private int modifiedFiles;
       private int deletedFiles;
       private int unchangedFiles;
       private int symbolsUpdated;
       private long processingTimeMs;
   }
   ```

   `IndexStatsBean.java`（~40行）— @DataBean（供 G4 使用）：
   ```java
   @DataBean
   public class IndexStatsBean {
       private int totalFiles;
       private int totalSymbols;
       private Map<String, Integer> symbolCountByKind;  // kind → count
       private Map<String, Integer> symbolCountByLanguage;  // language → count
       private int totalCalls;
       private int resolvedCalls;
       private double resolutionRate;
   }
   ```

2. 在 `NopCodeIndexBizModel` 中增加 `@BizMutation` 方法：

   ```java
   @BizMutation
   public IncrementalUpdateResult incrementalUpdate(@Name("indexId") String indexId) {
       // 1. 加载 NopCodeIndex，获取 rootPath
       // 2. 从 NopCodeFile 加载上次指纹（fileHash + lastModified）
       // 3. 调用 ProjectAnalyzer.analyzeIncremental()
       // 4. 更新变更文件的符号（delete old → insert new）
       // 5. 删除已移除文件的所有关联符号
       // 6. 更新 NopCodeFile 的 fileHash/lastModified
       // 7. 更新 NopCodeIndex 的 lastIndexed/indexVersion
       // 8. 返回 IncrementalUpdateResult
   }
   ```

3. 文件拆分策略：
   - `NopCodeIndexBizModel.java` — @BizQuery + @BizMutation（含 incrementalUpdate）≤ 500 行
   - `NopCodeIndexIndexer.java`（~200行）— 实际索引逻辑（从 BizModel 委托），包括：
     - `fullIndex(String rootPath)` — 全量索引，返回 `IncrementalUpdateResult`
     - `incrementalIndex(String indexId)` — 增量索引，返回 `IncrementalUpdateResult`
     - `syncFileResults(...)` — 将分析结果同步到数据库
     - `removeFileAndSymbols(String fileId)` — 删除文件及其符号

4. 过期数据清理：
   - 删除文件时级联删除：NopCodeSymbol（by fileId）、NopCodeCall（by fileId）、NopCodeInheritance（by fileId）、NopCodeUsage（by fileId）、NopCodeAnnotationUsage（by fileId）
   - 修改文件时：先删除旧符号再插入新符号（同一 fileId）

5. Shrink guard：如果变更集会删除超过 50% 的文件，抛出 NopException 拒绝执行

单元测试（`TestNopCodeIndexBizModelIncremental.java`，~100行）：
- 全量索引 → 增量更新（修改 1 个文件）→ 验证只有该文件的符号更新
- 全量索引 → 增量更新（新增 1 个文件）→ 验证新文件符号插入
- 全量索引 → 增量更新（删除 1 个文件）→ 验证该文件符号全部删除
- 无变更 → 增量更新 → IncrementalUpdateResult.unchangedFiles == all
- Shrink guard → 删除 >50% 文件 → 抛出异常

Exit Criteria:
- [ ] `incrementalUpdate` mutation 返回 IncrementalUpdateResult DTO
- [ ] 只更新变更文件的符号，不触及未变化文件
- [ ] 删除文件时级联清理所有关联数据
- [ ] Shrink guard 生效
- [ ] 单文件 ≤ 500 行
- [ ] 单元测试通过

---

## Phase 2: TypeScript 语言适配器（tree-sitter）

### Task T1: 创建 nop-code-lang-typescript 模块

Status: completed
Depends On: nop-code-core (已完成), C1

Instructions:

与 P1 对称，使用 tree-sitter TypeScript 语法：

1. `nop-code/nop-code-lang-typescript/`:
   - `pom.xml` — JDK 22+, dependencies: jtreesitter + tree-sitter-typescript
   - `TreeSitterTypeScriptFacade.java` — (~120行: Parser创建、Tree生命周期、Node遍历工具)
   - `TypeScriptSymbolExtractor.java` — (~180行: class/interface/enum/type/function/property → CodeSymbolInfo)
   - `TypeScriptRelationExtractor.java` — (~100行: import/export/extends/implements/decorator → relationships)
   - `TypeScriptLanguageAdapter.java` — (~30行)
   - `TypeScriptCodeFileAnalyzer.java` — (~60行: 组合上述3个类)
   - `_lang-typescript.beans.xml`
   - 测试类：TestTypeScriptLanguageAdapter, TestTypeScriptCodeFileAnalyzer,
     TestTypeScriptSymbolExtractor, TestTypeScriptRelationExtractor

2. tree-sitter-typescript 节点类型映射：
   - `class_declaration` → CodeSymbolKind.CLASS
   - `interface_declaration` → CodeSymbolKind.INTERFACE
   - `type_alias_declaration` → CodeSymbolKind（新增 TYPE_ALIAS 或复用）
   - `enum_declaration` → CodeSymbolKind.ENUM
   - `function_declaration` / `arrow_function` / `method_definition` → FUNCTION / METHOD
   - `property_signature` / `property_declaration` → FIELD
   - `decorator` → annotationUsage
   - `extends_clause` / `implements_clause` → inheritance
   - `import_statement` / `export_statement` → usage

3. TSX 支持：tree-sitter-typescript 包含 TSX 语法，通过文件扩展名区分

4. 更新 `nop-code/pom.xml` 添加 module

Exit Criteria:
- [ ] 模块编译通过（JDK 22+ Panama/FFM）
- [ ] TypeScriptCodeFileAnalyzer.analyze() 返回 CodeFileAnalysisResult
- [ ] tree-sitter TypeScript 语法库正确加载

### Task T2: TypeScript 适配器单元测试

Status: completed
Depends On: T1

测试类与源码 1:1 对应：

1. `TestTypeScriptLanguageAdapter`（~30行）：
   - getLanguage() == TYPESCRIPT, getFileExtensions() == [".ts", ".tsx"]
   - getFileAnalyzer() 返回非 null

2. `TestTypeScriptSymbolExtractor`（~100行）：
   - interface → INTERFACE symbol
   - class → CLASS + 嵌套 METHOD/FIELD
   - type alias → symbol
   - enum → ENUM symbol
   - function + arrow function → FUNCTION symbol
   - access modifiers（public/private/protected/readonly）

3. `TestTypeScriptRelationExtractor`（~60行）：
   - import/export → usage 记录
   - extends/implements → inheritance
   - decorator → annotationUsage

4. `TestTypeScriptCodeFileAnalyzer`（~50行）：
   - 完整 TS 文件 → 完整 CodeFileAnalysisResult
   - 空源码、无效 TS
   - TSX 文件解析

Exit Criteria:
- [ ] 4 个测试类全部通过
- [ ] 覆盖：class、interface、enum、function、import/export、extends/implements、decorator
- [ ] `cd nop-code/nop-code-lang-typescript && ../../mvnw test` 全部通过

---

## Phase 3: GraphQL 自定义查询层

### Task G1: NopCodeSymbolBizModel — 符号搜索与查找

Status: completed
Depends On: nop-code-dao (已完成)

在现有 `NopCodeSymbolBizModel` 上添加 @BizQuery 方法：

文件拆分策略（≤500行）：
- `NopCodeSymbolBizModel.java` — 保留现有 CrudBizModel 基础 + @BizQuery 方法（findByQualifiedName, findByKind, searchSymbols）
- 若 @BizQuery + @BizLoader 合计超 500 行，将 @BizLoader 拆至 `NopCodeSymbolBizLoader.java`

```java
@BizModel("NopCodeSymbol")
public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> {

    @BizQuery
    public NopCodeSymbol findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("qualifiedName", qualifiedName));
        query.addFilter(FilterBeans.eq("indexId", indexId));
        return dao().findFirst(query);
    }

    @BizQuery
    public List<NopCodeSymbol> findByKind(
            @Name("kind") String kind,
            @Name("indexId") String indexId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("kind", kind));
        query.addFilter(FilterBeans.eq("indexId", indexId));
        return doFindList(query, null, null);
    }

    @BizQuery
    public List<NopCodeSymbol> searchSymbols(
            @Name("query") String queryStr,
            @Name("indexId") String indexId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.like("name", "%" + queryStr + "%"));
        query.addFilter(FilterBeans.eq("indexId", indexId));
        return doFindList(query, null, null);
    }
}
```

单元测试（`TestNopCodeSymbolBizModel.java`）：
- 使用内存数据库（H2）+ Nop AutoTest 框架
- 测试 findByQualifiedName 精确匹配、无匹配返回 null
- 测试 findByKind 过滤 CLASS/METHOD 等
- 测试 searchSymbols 模糊搜索、空结果

Exit Criteria:
- [ ] findByQualifiedName 按 qualifiedName 精确查找
- [ ] findByKind 按 kind 过滤
- [ ] searchSymbols 模糊搜索
- [ ] 单文件 ≤ 500 行
- [ ] 单元测试通过

### Task G2: NopCodeFileBizModel — 文件查询与 Outline

Status: completed
Depends On: G1

```java
// NopCodeFileBizModel 中添加
@BizQuery
public NopCodeFile getByPath(
        @Name("filePath") String filePath,
        @Name("indexId") String indexId) { ... }
```

@BizLoader 关联字段（对齐现有 ORM 关系）：

```java
// NopCodeFile 的关联加载
@BizLoader("symbols")
public List<NopCodeSymbol> symbols(@ContextSource NopCodeFile file) {
    // ORM 已有 file.toMany("symbols") 关系，直接用或 QueryBean
}

@BizLoader("types")
public List<NopCodeSymbol> types(@ContextSource NopCodeFile file) {
    // 按 fileId + kind IN (CLASS, INTERFACE, ENUM, ANNOTATION_TYPE) 过滤
}

@BizLoader("outline")
public FileOutlineBean outline(@ContextSource NopCodeFile file) {
    // 返回 FileOutlineBean DTO（name, kind, line, endLine 列表）
}
```

单元测试（`TestNopCodeFileBizModel.java`）：
- 测试 getByPath 精确匹配
- 测试 symbols BizLoader 返回文件的符号列表
- 测试 types BizLoader 只返回 CLASS/INTERFACE/ENUM
- 测试 outline BizLoader 返回结构概览

Exit Criteria:
- [ ] getByPath 按路径获取文件
- [ ] symbols/types/outline BizLoader 按需加载
- [ ] 单文件 ≤ 500 行
- [ ] 单元测试通过

### Task G3: 调用链与继承关系查询

Status: completed
Depends On: G1

利用现有 `nop_code_call`（callerId/calleeId）和 `nop_code_inheritance`（parentId/childId/relationType）表。

文件拆分策略（≤500行）：
- `NopCodeSymbolBizModel.java` — 保留 @BizQuery（findCallChain, findHierarchy, findUsages）
- `NopCodeSymbolBizLoader.java` — 拆出 @BizLoader（callers, callees, superTypes, subTypes, usages, annotations, children）

@BizQuery（NopCodeSymbolBizModel）：

```java
@BizQuery
public List<NopCodeCall> findCallChain(
        @Name("symbolId") String symbolId,
        @Name("direction") String direction,  // INCOMING, OUTGOING, BOTH
        @Name("maxDepth") int maxDepth) {
    // 递归查 nop_code_call 表
    // INCOMING: WHERE calleeId = ?
    // OUTGOING: WHERE callerId = ?
}

@BizQuery
public List<NopCodeInheritance> findHierarchy(
        @Name("symbolId") String symbolId,
        @Name("direction") String direction,  // SUPER, SUB, BOTH
        @Name("maxDepth") int maxDepth) {
    // 递归查 nop_code_inheritance 表
    // SUPER: WHERE childId = ?
    // SUB: WHERE parentId = ?
}

@BizQuery
public List<NopCodeUsage> findUsages(
        @Name("symbolId") String symbolId) {
    // 查 nop_code_usage WHERE symbolId = ? OR usageSymbolId = ?
}
```

@BizLoader（NopCodeSymbolBizLoader）：

```java
@BizLoader("callers")
public List<NopCodeCall> callers(@ContextSource NopCodeSymbol symbol) {
    // WHERE calleeId = symbol.id
}

@BizLoader("callees")
public List<NopCodeCall> callees(@ContextSource NopCodeSymbol symbol) {
    // WHERE callerId = symbol.id
}

@BizLoader("superTypes")
public List<NopCodeInheritance> superTypes(@ContextSource NopCodeSymbol symbol) {
    // WHERE childId = symbol.id
}

@BizLoader("subTypes")
public List<NopCodeInheritance> subTypes(@ContextSource NopCodeSymbol symbol) {
    // WHERE parentId = symbol.id
}

@BizLoader("usages")
public List<NopCodeUsage> usages(@ContextSource NopCodeSymbol symbol) {
    // WHERE symbolId = symbol.id
}

@BizLoader("annotations")
public List<NopCodeAnnotationUsage> annotations(@ContextSource NopCodeSymbol symbol) {
    // WHERE symbolId = symbol.id
}

@BizLoader("children")
public List<NopCodeSymbol> children(@ContextSource NopCodeSymbol symbol) {
    // WHERE parentId = symbol.id (类包含方法/字段)
}
```

单元测试：
- `TestNopCodeSymbolBizModel.java`（扩展 G1 测试类，增加 findCallChain/findHierarchy/findUsages）
  - findCallChain INCOMING/OUTGOING 方向
  - findHierarchy SUPER/SUB/BOTH
  - findUsages 返回所有使用处
  - maxDepth 边界：depth=0, depth=1, depth>实际深度
- `TestNopCodeSymbolBizLoader.java`
  - 测试每个 BizLoader 独立加载
  - 测试 BizLoader 嵌套查询（symbol → callers → caller 的 callees）

Exit Criteria:
- [ ] findCallChain 支持方向和深度
- [ ] findHierarchy 支持向上/向下/双向
- [ ] 所有 @BizLoader 可嵌套查询
- [ ] BizModel + BizLoader 各 ≤ 500 行
- [ ] 单元测试通过

### Task G4: 索引触发与统计查询

Status: completed
Depends On: G3, P2, T2（需要新语言适配器注册后才可触发多语言索引）

```java
// NopCodeIndexBizModel 中添加

@BizMutation
public IncrementalUpdateResult triggerIndex(
        @Name("rootPath") String rootPath) {
    // 调用 NopCodeIndexIndexer.fullIndex() 构建全量索引
}

@BizQuery
public NopCodeIndex findByPath(
        @Name("rootPath") String rootPath) { ... }

@BizQuery
public IndexStatsBean getIndexStats(
        @Name("indexId") String indexId) {
    // 统计：totalFiles, totalSymbols by kind, language distribution, call resolution rate
    // 返回 IndexStatsBean DTO（已在 I4 中定义）
}
```

单元测试（`TestNopCodeIndexBizModelStats.java`，~60行）：
- 测试 triggerIndex 触发索引（使用测试用 Java 源文件目录）→ 返回 IncrementalUpdateResult
- 测试 findByPath 查找已索引项目
- 测试 getIndexStats 返回 IndexStatsBean（totalFiles > 0, symbolCountByKind 非空）
- 测试重复索引幂等性

Exit Criteria:
- [ ] triggerIndex 返回 IncrementalUpdateResult DTO
- [ ] getIndexStats 返回 IndexStatsBean DTO
- [ ] 单文件 ≤ 500 行
- [ ] 单元测试通过

### Task G5: 集成测试

Status: completed
Depends On: G4

使用 Nop AutoTest 框架（参考 docs-for-ai/03-runbooks/write-integration-test-with-noptestconfig.md）：

1. 测试 GraphQL 符号搜索 query
2. 测试调用链查询（递归）
3. 测试继承关系查询
4. 测试 @BizLoader 嵌套加载（避免 N+1）
5. 测试文件 outline
6. 端到端：索引 Java 项目 → 查询符号 → 获取调用链

Exit Criteria:
- [ ] 所有 GraphQL 集成测试通过
- [ ] BizLoader 批量加载无 N+1 问题

---

## Phase 3.5: 跨文件调用解析与图分析

> 参考 graphify 的 cross-file import resolution、community detection、god nodes、cohesion scoring。
>
> **graphify 参考实现**：
> - `~/ai/graphify/graphify/extract.py` — `_resolve_cross_file_imports()` (L2803-2933): 两阶段跨文件解析（全局索引 + import 匹配）
> - `~/ai/graphify/graphify/extract.py` — raw_calls (L1183-1190): 未解析调用存储用于延迟解析
> - `~/ai/graphify/graphify/analyze.py` — `god_nodes()` (L33-70): 按 degree 排序排除 file/concept 节点
> - `~/ai/graphify/graphify/analyze.py` — `_surprise_score()` (L80-140): 置信度权重 + 跨社区/跨文件加分
> - `~/ai/graphify/graphify/analyze.py` — `_cross_language()` (L22-30): 语言族映射
> - `~/ai/graphify/graphify/cluster.py` — `cohesion_score()` (L11-20): 社区内聚度 = actual_edges / max_possible
> - `~/ai/graphify/graphify/serve.py` — `_tool_graph_stats()`: 节点/边/社区数 + confidence 百分比
> - `~/ai/graphify/graphify/serve.py` — `_tool_god_nodes()`: top-N 最连接节点
> - `~/ai/graphify/graphify/serve.py` — `_tool_shortest_path()`: BFS 最短路径

### Task R1: 跨文件调用解析（全语言）

Status: completed
Depends On: P1, T1, I3

背景：当前 ProjectAnalyzer 的第二遍 resolve 只在内存中工作，且仅限 Java。
graphify 的做法：per-file 提取 → 全局符号索引 → cross-file resolve → 标记 EXTRACTED/INFERRED 置信度。

参考 graphify 实现：
- `_resolve_cross_file_imports()` (extract.py L2803-2933): 先建全局 {module: {ClassName: nodeId}} 索引，再逐文件匹配 import 语句
- raw_calls (extract.py L1183-1190): callee 未在本文件找到时存入 raw_calls 列表，等全局 resolve
- `_cross_language()` (analyze.py L22-30): `_LANG_FAMILY` 字典映射扩展名到语言族，跨族调用降级 INFERRED
- import handlers: `_import_python` (L141-181), `_import_js` (L184-226), `_import_java` (L229-264): 每种语言独立处理 import 语法

Instructions:

1. 在 `nop-code-core` 中增强 `CodeMethodCall`：增加 `confidence` 字段
   ```java
   // CodeMethodCall.java 新增
   private String confidence;  // EXTRACTED, INFERRED
   ```

2. 在 `ProjectAnalyzer` 的 `analyzeFile()` 私有方法中，为每个解析到的调用设置 `confidence`：
   - callee 在同一文件中 → `EXTRACTED`
   - callee 通过全局 SymbolTable 解析到 → `EXTRACTED`
   - callee 无法解析 → 保留 `calleeQualifiedName`，标记 `INFERRED`

3. 在 NopCodeCall ORM 实体增加 `confidence` 列：
   ```xml
   <column code="CONFIDENCE" displayName="置信度" name="confidence"
           precision="20" propId="10" stdDataType="string" stdSqlType="VARCHAR"/>
   <column code="CALLEE_QUALIFIED_NAME" displayName="被调用者全限定名" name="calleeQualifiedName"
           precision="500" propId="11" stdDataType="string" stdSqlType="VARCHAR"/>
   ```
   保存 `calleeQualifiedName` 以支持后续增量解析（graphify 的 raw_calls deferred resolution 模式）。

4. 语言特定的 qualified name 格式：
   - Java: `com.example.UserService.saveUser(User)` — 已实现
   - Python: `module.ClassName.method_name` — PythonCodeFileAnalyzer 需构建
   - TypeScript: `module/ClassName.methodName` — TypeScriptCodeFileAnalyzer 需构建
   - **关键**：每个语言适配器的 TreeSitterFacade/Extractor 必须生成一致的 qualified name

5. 跨语言族检测（参考 graphify `_cross_language`）：
   - `LanguageFamily.java`（~20行）— 枚举映射：JAVA→JVM, KOTLIN→JVM, TS→JS_FAMILY, JS→JS_FAMILY, PY→PYTHON
   - 跨语言族的调用自动降低 confidence 为 INFERRED

单元测试（`TestCrossFileResolution.java`，~80行）：
- Java 文件 A 调用 Java 文件 B 的方法 → calleeId 解析成功, confidence=EXTRACTED
- Python 文件 A 调用 Python 文件 B 的函数 → 解析成功
- 调用不存在的方法 → calleeId=null, calleeQualifiedName 保留, confidence=INFERRED
- 跨语言调用 → confidence=INFERRED

Exit Criteria:
- [ ] 全语言的 qualified name 格式一致
- [ ] 跨文件调用解析覆盖 Java + Python + TypeScript
- [ ] EXTRACTED/INFERRED 置信度标记正确
- [ ] NopCodeCall 持久化包含 confidence + calleeQualifiedName
- [ ] 单元测试通过

### Task R2: 社区检测 BizModel（接入已有 CommunityDetector）

Status: completed
Depends On: G1, R1

背景：nop-code-core 已有完整的 CommunityDetector（Leiden + LabelPropagation + cohesion scoring），
但未接入 GraphQL。需要创建 BizModel 暴露。

参考 graphify 实现：
- `cluster.py` — Leiden (via graspologic) + Louvain fallback
- `cluster.py` — `cohesion_score(G, community_nodes)` (L11-20): `actual_edges / (n*(n-1)/2)`
- `cluster.py` — 社区拆分：>25% 图大小的社区递归再分
- `serve.py` — `_tool_get_community()`: 按 community_id 返回所有节点
- `export.py` — `COMMUNITY_COLORS`: 10 色方案用于可视化
- `wiki.py` — `to_wiki()`: 每个社区生成一篇 wiki 文章

Instructions:

1. DTO 类（`nop-code-core`）：

   `CommunityDetectionResultBean.java`（~40行）— @DataBean：
   ```java
   @DataBean
   public class CommunityDetectionResultBean {
       private List<CommunityBean> communities;
       private int totalSymbols;
       private int totalCommunities;
       private double averageCohesion;
       private String algorithmUsed;  // LEIDEN or LABEL_PROPAGATION
       private double modularity;
       private long processingTimeMs;
   }
   ```

   `CommunityBean.java`（~30行）— @DataBean：
   ```java
   @DataBean
   public class CommunityBean {
       private String id;
       private String label;
       private List<String> symbolIds;
       private int symbolCount;
       private double cohesion;  // 0.0-1.0
       private String dominantPackage;
   }
   ```

2. BizModel：`NopCodeAnalysisBizModel.java`（~200行）— 新 BizModel，非 CrudBizModel：
   ```java
   @BizModel("NopCodeAnalysis")
   public class NopCodeAnalysisBizModel {
   
       @BizQuery
       public CommunityDetectionResultBean detectCommunities(
               @Name("indexId") String indexId) {
           // 1. 从 DB 加载 NopCodeCall → 构建 CallGraph
           // 2. 从 DB 加载 NopCodeSymbol → 构建 SymbolTable
           // 3. 调用 CommunityDetector.detectCommunities()
           // 4. 转换为 CommunityDetectionResultBean 返回
       }
   
       @BizQuery
       public CommunityDetectionResultBean detectCommunitiesWithConfig(
               @Name("indexId") String indexId,
               @Name("algorithm") String algorithm,  // LEIDEN, LABEL_PROPAGATION
               @Name("resolution") Double resolution,
               @Name("minCommunitySize") Integer minSize) { ... }
   
       @BizQuery
       public List<CommunityBean> getCommunitiesForSymbol(
               @Name("symbolId") String symbolId) { ... }
   }
   ```

3. 服务桥接：`AnalysisBridgeService.java`（~150行）— 从 DAO 加载数据构建内存图结构：
   - `buildCallGraph(String indexId)` → CallGraph
   - `buildSymbolTable(String indexId)` → SymbolTable
   - `toResultBean(CommunityDetectionResult)` → CommunityDetectionResultBean

单元测试（`TestNopCodeAnalysisBizModel.java`，~80行）：
- detectCommunities 返回有效的社区划分
- 每个社区的 cohesion > 0
- symbolId 对应的社区查找正确

Exit Criteria:
- [ ] detectCommunities 可通过 GraphQL 调用
- [ ] 返回 CommunityDetectionResultBean DTO
- [ ] 社区内 cohesion 值合理
- [ ] 单文件 ≤ 500 行
- [ ] 单元测试通过

### Task R3: 图分析指标（God Nodes + Cohesion + Impact）

Status: completed
Depends On: R2

背景：graphify 的核心图分析能力。nop-code-core 已有 ImpactAnalyzer 和 EntryPointScorer，
但同样未接入 GraphQL。

参考 graphify 实现：
- `analyze.py` — `god_nodes(G, top_n)` (L33-70): `dict(G.degree())` 排序，排除 file/concept 节点
- `analyze.py` — `_surprise_score()` (L80-140): confidence_weight(AMBIGUOUS=3,INFERRED=2,EXTRACTED=1) + cross_community(+1) + cross_file_type(+2)
- `analyze.py` — `surprising_connections()` (L143-190): 排名非 import/contains 的跨文件边
- `serve.py` — `_tool_graph_stats()`: 返回 node_count, edge_count, communities, {EXTRACTED/INFERRED/AMBIGUOUS}% 
- `export.py` — `_community_reach(node_id)`: 节点连接的跨社区数量（bridge node 指标）
- `analyze.py` — `betweenness_centrality(G, k=sample_size)`: 桥接节点检测（大图采样计算）

Instructions:

1. DTO 类：

   `GraphAnalysisResultBean.java`（~50行）— @DataBean：
   ```java
   @DataBean
   public class GraphAnalysisResultBean {
       private List<GodNodeBean> godNodes;           // 最核心节点
       private CohesionBreakdownBean cohesionBreakdown;  // 置信度分布
       private List<String> isolatedSymbols;         // 孤立节点（degree≤1）
   }
   ```

   `GodNodeBean.java`（~20行）— @DataBean：
   ```java
   @DataBean
   public class GodNodeBean {
       private String symbolId;
       private String qualifiedName;
       private String kind;          // CLASS, METHOD, etc.
       private int degree;           // 连接数
       private int callerCount;      // 被调用次数（fan-in）
       private int calleeCount;      // 调用次数（fan-out）
   }
   ```

   `CohesionBreakdownBean.java`（~20行）— @DataBean：
   ```java
   @DataBean
   public class CohesionBreakdownBean {
       private int extractedCount;   // EXTRACTED 调用数
       private int inferredCount;    // INFERRED 调用数
       private double extractedPercent;
       private double inferredPercent;
   }
   ```

2. 在 `NopCodeAnalysisBizModel` 中增加 @BizQuery：

   ```java
   @BizQuery
   public GraphAnalysisResultBean getGraphAnalysis(
           @Name("indexId") String indexId,
           @Name("topN") @Optional Integer topN) {
       // 1. God nodes: 从 NopCodeCall 聚合 callerId/calleeId 计数
       // 2. Cohesion breakdown: 从 NopCodeCall 按 confidence 分组统计
       // 3. Isolated symbols: NopCodeSymbol 中 id NOT IN (callerId UNION calleeId)
   }
   
   @BizQuery
   public List<GodNodeBean> getGodNodes(
           @Name("indexId") String indexId,
           @Name("topN") @Optional Integer topN) { ... }
   
   @BizQuery
   public ImpactResultBean getImpactAnalysis(
           @Name("symbolId") String symbolId,
           @Name("depth") @Optional Integer depth) {
       // 委托已有的 ImpactAnalyzer
   }
   ```

单元测试（`TestGraphAnalysis.java`，~60行）：
- getGodNodes 返回按 degree 降序排列的核心节点
- getCohesionBreakdown 返回 EXTRACTED/INFERRED 百分比
- 孤立节点检测正确
- getImpactAnalysis 返回上下游影响

Exit Criteria:
- [ ] getGodNodes 返回 DTO 列表
- [ ] getCohesionBreakdown 统计正确
- [ ] getImpactAnalysis 委托 ImpactAnalyzer
- [ ] 所有方法返回 @DataBean DTO（非 Map）
- [ ] 单元测试通过

---

## Phase 4: 构建验证

### Task V1: 全量构建与测试

Status: completed
Depends On: C1, P2, T2, G5

```bash
cd nop-code && ../mvnw clean install -T 1C
cd ../../nop-utils/nop-java-parser && ../../mvnw test
```

Exit Criteria:
- [ ] nop-code 全部 12+ 模块 BUILD SUCCESS
- [ ] nop-code-core: 56+ tests + 增量检测/分析 tests (JDK 11)
- [ ] nop-code-lang-java: 31+ tests (JDK 11)
- [ ] nop-code-lang-python: 全部 tests (JDK 22+)
- [ ] nop-code-lang-typescript: 全部 tests (JDK 22+)
- [ ] nop-code-service: 集成 tests + 增量索引 tests
- [ ] nop-java-parser: 编译通过（残留依赖已清理）
- [ ] 全量构建使用 JDK 22+ 运行时执行（确保 Panama/FFM native 加载正常）
- [ ] 所有 .java 文件 ≤ 500 行
- [ ] 增量索引端到端验证：全量索引 → 修改文件 → 增量更新 → 只处理变更文件

---

## 执行顺序与依赖

```
Phase 0    Phase 1        Phase 1.5           Phase 2     Phase 3       Phase 3.5        Phase 4
  C1 ──  P1──P2         I1──I2──I3──I4     T1──T2     G1──G2──G3──G4──G5  R1──R2──R3       V1
          │                │                   │           │                   │
          └────────────────┴───────────────────┴───────────┴───────────────────┘
```

- C1 优先执行（简单，5 分钟）
- Phase 1（Python）和 Phase 2（TS）和 Phase 3（GraphQL G1-G3）可**并行**
- Phase 1.5（增量索引 I1-I4）：I1 可并行启动，I4 依赖 G1
- Phase 3.5（R1-R3）：R1 依赖 P1+T1（语言适配器），R2 依赖 G1+R1，R3 依赖 R2
- G5 集成测试 → 最后在 V1 前

**推荐执行顺序**：
1. C1（清理）→ 立即开始
2. I1 + P1 + T1 + G1 **并行**
3. I2 + P2 + T2 + G2-G3 **并行**
4. I3 + G4 + R1 **并行**（R1 需 P1+T1 完成）
5. I4（依赖 I3 + G1）
6. R2-R3（依赖 R1 + G1）
7. G5（集成测试）
8. V1（全量验证）

**全局 DTO 规范**（参考 Nop 平台模式）：
- 所有 @BizQuery/@BizMutation 返回值必须是 `@DataBean` DTO，禁止 `Map<String, Object>`
- 命名规范：`XxxResult`（mutation 结果）、`XxxBean`（query 数据）、`XxxInfo`（信息类）
- 放置位置：`io.nop.code.core.dto`（核心 DTO）或 `io.nop.code.dao.dto`（需要 DAO 依赖的 DTO）
- GraphQL schema 自动生成，无需手动注册

## 关键设计决策

### D1: 解析引擎选择 — tree-sitter 官方 Java 绑定

**决策**：使用 tree-sitter 官方 Java 绑定 `io.github.tree-sitter:jtreesitter`。

Maven 坐标：
```xml
<!-- 核心 API（JDK 22+ Panama/FFM） -->
<dependency>
    <groupId>io.github.tree-sitter</groupId>
    <artifactId>jtreesitter</artifactId>
    <version>0.26.0</version>
</dependency>

<!-- Python 语法（各语言模块单独引入） -->
<dependency>
    <groupId>io.github.tree-sitter</groupId>
    <artifactId>tree-sitter-python</artifactId>
    <version>0.23.4</version>
</dependency>

<!-- TypeScript 语法 -->
<dependency>
    <groupId>io.github.tree-sitter</groupId>
    <artifactId>tree-sitter-typescript</artifactId>
    <version>0.23.2</version>
</dependency>
```

API 用法（参考 java-tree-sitter 官方 repo）：
```java
// 加载语言
Language pythonLang = new Language(TreeSitterPython.language());

// 创建解析器
try (Parser parser = new Parser(pythonLang)) {
    Tree tree = parser.parse(sourceCode);
    Node root = tree.getRootNode();
    // 遍历 AST：root.getChild(i), node.getType(), node.getChildByFieldName("name") 等
}
```

选型理由：
1. **官方维护**：tree-sitter 组织直接维护，与 tree-sitter C 核心同步更新
2. **Panama/FFM**：使用 JDK 22 Foreign Function & Memory API，无需 JNI 手动编译
3. **25+ 语言统一方案**：Python、TypeScript、Go、Rust 等均有语法包
4. **Maven Central 可用**：所有包直接从 Central 获取，无需自定义仓库
5. **用户明确指示**：除 Java 外全部使用 tree-sitter，不手写解析器

文件组织模式（每个语言适配器模块）：
```
XxxFacade.java          (~120行) — Parser 生命周期、Tree 创建、Node 遍历工具
XxxSymbolExtractor.java (~150行) — AST 节点 → CodeSymbolInfo 转换
XxxRelationExtractor.java(~100行) — import/inheritance/decorator → relationships
XxxLanguageAdapter.java  (~30行) — ILanguageAdapter 实现
XxxCodeFileAnalyzer.java (~60行) — ICodeFileAnalyzer 实现，组合上述类
```
所有文件均 ≤ 200 行，远低于 500 行上限。

JDK 兼容性：
- nop-code 根 pom 编译目标 JDK 11，但 `nop-code-api` 已定义 `<java.version>11</java.version>`
- 新语言模块需设置为 JDK 22+（与 nop-ai-skills 的 JDK 21 模式类似，可覆盖父 pom 设置）
- nop-code-core / nop-code-service 等现有模块保持 JDK 11 不变

### D2: @BizLoader vs @BizQuery 的边界

- **@BizQuery**：顶级查询入口（findByXxx, searchXxx, findCallChain, findHierarchy）
- **@BizLoader**：实体关联字段按需加载（symbols, callers, superTypes, usages, children, annotations）
- 遵循 Nop 规范：BizLoader 用于已有实体关联字段，BizQuery 用于新查询入口

### D3: 递归查询深度控制

findCallChain 和 findHierarchy 需要 `maxDepth` 防止无限递归。默认 maxDepth=3，最大 10。

### D4: GraphQL 设计文档中的类型映射

设计文档中 NopCodeClass/NopCodeInterface 等独立类型 → 映射为 NopCodeSymbol + kind 过滤
- `NopCodeClass` → `NopCodeSymbol` where `kind = "CLASS"`
- `NopCodeInterface` → `NopCodeSymbol` where `kind = "INTERFACE"`
- `NopCodeMethod` → `NopCodeSymbol` where `kind = "METHOD"`
- 不需要修改 ORM，在 @BizLoader 中按 kind 过滤即可

### D5: 增量索引策略（参考 graphify）

采用与 graphify 相同的两级变化检测策略：
1. **快速路径**：比较文件 mtime — mtime 未变则文件未变（零开销）
2. **确认路径**：mtime 变了才计算 SHA-256 — 防止 touch/同步工具导致的误报

与 graphify 的差异：
- **不实现 AST 缓存**：graphify 有 AST/semantic 双层缓存（因为 LLM 提取昂贵）。nop-code 是纯 AST 解析，开销低，不需要缓存层
- **不实现 file watching / git hooks**：nop-code 通过 GraphQL mutation 触发增量更新，由调用方（CI/CD 或 IDE 插件）决定何时触发
- **不实现 semantic 缓存**：不涉及 LLM 提取，无需此层

Manifest 存储位置：`{projectRoot}/.nop-code/manifest.json`（与 graphify 的 `graphify-out/manifest.json` 对称）

### D6: 过期数据清理策略

文件删除/修改时的级联清理规则：
- 修改文件：DELETE NopCodeSymbol WHERE fileId = ? → 重新插入新符号
- 删除文件：DELETE NopCodeFile + 级联 DELETE NopCodeSymbol/NopCodeCall/NopCodeInheritance/NopCodeUsage/NopCodeAnnotationUsage WHERE fileId = ?
- 安全检查：如果变更集会删除超过 50% 的文件，拒绝执行并要求确认（类似 graphify 的 shrink guard）

### D7: 跨文件调用解析策略

参考 graphify 的两阶段解析：
1. **Per-file 提取**：每个语言适配器在解析时记录 raw calls（calleeQualifiedName）
2. **全局 resolve**：ProjectAnalyzer 第二遍用 SymbolTable 按 qualifiedName 查找 calleeId
3. **置信度标记**：resolved → EXTRACTED，unresolved → INFERRED
4. **持久化**：NopCodeCall 同时保存 calleeId（可能 null）和 calleeQualifiedName（始终保留）
5. **语言族检测**：跨语言族调用自动降级为 INFERRED（Java→Python 调用不常见）

与 graphify 的差异：
- graphify 使用 file stem（模块名）做解析，nop-code 使用 qualifiedName（更精确）
- graphify 有 `_cross_language` 降低置信度，nop-code 用 `LanguageFamily` 枚举实现等价逻辑

### D8: BizModel 返回值规范

所有 @BizQuery/@BizMutation 必须返回 @DataBean DTO，禁止 Map<String, Object>。
参考 Nop 平台模式（LoginResult, SiteMapBean, UploadResponseBean 等）：
- `XxxResult` — mutation 操作结果
- `XxxBean` — query 返回的数据结构
- `XxxInfo` — 信息类
- 放置位置：`io.nop.code.core.dto`
- GraphQL schema 自动从 @DataBean 生成，无需手动注册

## 风险与待确认

1. **tree-sitter JDK 22+ 模块兼容性**：jtreesitter 使用 Panama FFM API（JDK 22+）。新语言模块需设为 JDK 22 编译目标，确保不与现有 JDK 11 模块冲突。已确认 nop-code 现有模块继承父 pom 的 JDK 11 设置，新模块可独立覆盖。
2. **tree-sitter 语法库 native library 加载**：jtreesitter 通过 Panama FFM 加载 .so/.dll/.dylib。需确认 CI 环境和本地开发环境的 native library 可用性。Maven Central 上的语法包应包含预编译的 native libraries。
3. **Python 动态特性**：Python 的函数调用无法静态分析（duck typing），Phase 1 不实现调用提取
4. **TypeScript 装饰器语法**：TypeScript 有多种装饰器提案（legacy/experimental/TC39），tree-sitter-typescript 应已覆盖主流语法
5. **@BizLoader 批量加载**：Nop 框架的 BizLoader 默认是逐条加载还是批量？需确认避免 N+1（参考 docs-for-ai/03-runbooks/add-bizloader-field.md）
6. **增量索引的跨文件引用一致性**：修改文件 A 后，如果文件 B 引用了 A 中被删除的符号，B 中的引用（NopCodeCall/NopCodeUsage）也需要清理。需要在增量更新后执行一轮"悬挂引用清理"。

## 任务总览

| Phase | Task | 描述 | 行数估计 | JDK | 返回类型 |
|-------|------|------|----------|-----|----------|
| 0 | C1 | 清理残留依赖 | ~0 | 11 | — |
| 1 | P1 | Python 适配器（tree-sitter） | ~460 | 22 | — |
| 1 | P2 | Python 单测 | ~220 | 22 | — |
| 1.5 | I1 | ORM 增量字段 + confidence | ~30 (XML) | 11 | — |
| 1.5 | I2 | 增量检测工具 | ~300 | 11 | — |
| 1.5 | I3 | ProjectAnalyzer 增量 | ~200 | 11 | IncrementalResult |
| 1.5 | I4 | 增量 BizModel + DTO | ~300 | 11 | IncrementalUpdateResult |
| 2 | T1 | TypeScript 适配器（tree-sitter） | ~490 | 22 | — |
| 2 | T2 | TypeScript 单测 | ~240 | 22 | — |
| 3 | G1 | 符号搜索 BizModel | ~150 | 11 | List\<NopCodeSymbol\> |
| 3 | G2 | 文件查询 BizModel | ~150 | 11 | NopCodeFile + BizLoader |
| 3 | G3 | 调用链/继承查询 | ~400 | 11 | List DTO |
| 3 | G4 | 索引触发/统计 | ~200 | 11 | IncrementalUpdateResult, IndexStatsBean |
| 3 | G5 | 集成测试 | ~200 | 11 | — |
| 3.5 | R1 | 跨文件调用解析（全语言） | ~200 | 11 | — |
| 3.5 | R2 | 社区检测 BizModel | ~400 | 11 | CommunityDetectionResultBean |
| 3.5 | R3 | 图分析指标（God Nodes + Cohesion） | ~300 | 11 | GraphAnalysisResultBean |
| 4 | V1 | 全量构建验证 | ~0 | 22 | — |

**总计：18 个 Task**，7 个 Phase

### DTO 清单（全部 @DataBean）

| DTO | 定义位置 | 用途 |
|-----|---------|------|
| `FileFingerprint` | nop-code-core | 文件指纹（SHA-256 + mtime） |
| `ChangeSet` | nop-code-core | 增量变更集 |
| `IncrementalResult` | nop-code-core | 增量分析结果（内部） |
| `IncrementalUpdateResult` | nop-code-core | 增量更新 GraphQL 返回值 |
| `IndexStatsBean` | nop-code-core | 索引统计 GraphQL 返回值 |
| `CommunityDetectionResultBean` | nop-code-core | 社区检测 GraphQL 返回值 |
| `CommunityBean` | nop-code-core | 单个社区 |
| `GraphAnalysisResultBean` | nop-code-core | 图分析 GraphQL 返回值 |
| `GodNodeBean` | nop-code-core | 核心节点 |
| `CohesionBreakdownBean` | nop-code-core | 置信度分布 |
| `LanguageFamily` | nop-code-core | 语言族枚举 |

## Closure

Reviewed By:
Reviewed At:
Completed At:

Status Note: Plan not yet executed. All phases pending.

Audit Evidence:

Follow-Ups:

None — all work is captured in this plan's phases.
