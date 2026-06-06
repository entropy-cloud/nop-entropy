# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 10 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 多语言适配器审计者 + 图算法语义审计者 + ORM 模型考古学家 + BizModel API 契约审计者
> **审查范围**: Java/Python/TypeScript 语言适配器、图算法语义边、ORM 模型完整性、BizModel 授权一致性、核心框架死代码

## 去重确认

已审阅以下历史报告：
- r9 (2026-06-06b): AR-124~AR-144（增量索引路径失效、view 字段名不匹配、并发锁不完整等）
- r8 (2026-06-06): AR-94~AR-123（零事务→已修复、Leiden directed→已修复、Python TSNode→已修复、glob 匹配→已修复等）
- r7 (2026-06-02): AR-88~AR-93
- deep-audit-2026-06-06: 21 维度全覆盖

**已修复确认（自 r9 以来）**：
- AR-94（零事务）→ **已修复**。所有写操作使用 `transactionTemplate.runInTransaction`。
- AR-95（Leiden directed=true）→ **已修复**。现在为 `new Network(nNodes, false, ...)`。
- AR-96（Python TSNode.equals）→ **已修复**。使用 `TSNode.eq(child, defNode)`。
- AR-97（SpringEventSynthesizer publisher 全关联）→ **已修复**。现在通过 `matchPublisherEventType` 精确匹配具体事件类型。
- AR-112（glob Pattern.quote 失效）→ **已修复**。改用直接 replace。

**仍存在的问题（未修复确认）**：
- AR-98（单例节点丢弃）、AR-99（startsWith 错配）、AR-100（cascadeDelete 缺失）
- AR-101~AR-111（persistFlows OOME、线程泄漏、缓存截断、依赖图重复加载等）
- AR-113~AR-123（注解 O(N²)、imports 丢失、ID 不一致等）
- AR-124（增量索引路径失效）、AR-127/128（并发锁）、AR-129~AR-144

本轮发现均为新视角切入的新问题。

---

### [AR-145] SpringEventSynthesizer 的 listener 映射使用 `Map<String, CodeSymbol>` 覆盖同事件类型的多个监听器

- **文件**: `nop-code/nop-code-graph/.../heuristic/SpringEventSynthesizer.java:29-39, 54`
- **证据片段**:
  ```java
  // Line 29: 单值 Map，同 key 覆盖
  Map<String, CodeSymbol> listenerByEventType = new LinkedHashMap<>();
  for (CodeSymbol sym : symbolTable.getAll()) {
      ...
      if (eventType != null) {
          listenerByEventType.put(eventType, sym);  // 后者覆盖前者
      }
  }

  // Line 54: 只取一个 listener
  CodeSymbol listener = listenerByEventType.get(eventType);
  ```
- **严重程度**: P1
- **现状**: 当多个 `@EventListener` 方法监听同一事件类型（如 `OrderCreatedEvent`）时，`listenerByEventType.put(eventType, sym)` 使后者覆盖前者。最终只为最后一个监听器生成合成边，之前的监听器完全丢失。这是 AR-97 的修复引入的新维度：AR-97 修复了 publisher 端的"全关联"问题，但 listener 端的"覆盖"问题仍然存在。
- **风险**: 在典型的 Spring 应用中，一个事件通常有多个监听器（如 `OrderCreatedEvent` 可能触发库存检查、邮件通知、日志记录），但图上只保留最后一个监听器的合成调用边，其余监听器从图中消失。
- **建议**: 改为 `Map<String, List<CodeSymbol>> listenerByEventType`，为每个事件类型收集所有监听器，然后在合成边时遍历所有监听器。
- **信心水平**: 确定
- **发现来源视角**: 图算法语义审计者

---

### [AR-146] Java RecordDeclaration 不加入 symbolMap 且缺少 parentId——record 类型对跨文件引用不可见

- **文件**: `nop-code/nop-code-lang-java/.../analyzer/JavaFileAnalyzer.java:269-298`
- **证据片段**:
  ```java
  // RecordDeclaration visitor (line 269-298):
  public void visit(RecordDeclaration decl, Void arg) {
      CodeSymbol symbol = new CodeSymbol();
      symbol.setId(UUID.randomUUID().toString());
      symbol.setName(decl.getNameAsString());
      if (currentTypeSymbol != null) {
          symbol.setDeclaringSymbolId(currentTypeSymbol.getId());
          symbol.setQualifiedName(currentTypeSymbol.getQualifiedName() + "." + decl.getNameAsString());
      } else {
          symbol.setQualifiedName(decl.getNameAsString());  // 顶层 record 无包名!
      }
      ...
      result.getSymbols().add(symbol);
      // ← 缺少 symbolMap.put(qualifiedName, symbol)
      // ← 缺少 symbol.setParentId(currentTypeSymbol.getId())
      ...
  }

  // 对比 createSymbolFromTypeDecl (line 692-693):
  result.getSymbols().add(symbol);
  symbolMap.put(qualifiedName, symbol);  // ← 其他类型有此调用
  ```
- **严重程度**: P1
- **现状**: Java 14+ 的 record 类型在 `visit(RecordDeclaration)` 中有三个缺陷：
  1. **不加入 `symbolMap`**：`ClassOrInterfaceDeclaration`、`EnumDeclaration`、`AnnotationDeclaration` 都在 `createSymbolFromTypeDecl` 中调用 `symbolMap.put(qualifiedName, symbol)`（line 693），但 `RecordDeclaration` 的 visitor 完全跳过了这一步。`ObjectCreationExpr` 的查找（`new FooRecord()`）依赖 `symbolMap`，所以 record 的构造调用永远不会被解析。
  2. **不设置 `parentId`**：嵌套 record 的 `parentId` 未设置（对比 line 689），破坏了父子符号层级。
  3. **顶层 record 缺少全限定名**：无父类型时 `qualifiedName` 退化为简单名（如 `MyRecord`），缺少包名前缀（对比 `decl.getFullyQualifiedName()` 在其他类型中的使用）。
- **风险**: (a) 包含 Java record 的项目中，所有对 record 的 `new` 表达式调用链断裂；(b) record 类型的 `findSymbolByShortName` 查找失效；(c) 顶层 record 的 qualifiedName 不唯一，可能导致符号冲突。
- **建议**: 将 `RecordDeclaration` 的处理统一到 `createSymbolFromTypeDecl` 方法中，或在 visitor 末尾添加 `symbolMap.put(symbol.getQualifiedName(), symbol)` 和 `symbol.setParentId(currentTypeSymbol != null ? currentTypeSymbol.getId() : null)`，并使用 `decl.getFullyQualifiedName().orElse(decl.getNameAsString())` 设置 qualifiedName。
- **信心水平**: 确定
- **发现来源视角**: 多语言适配器审计者

---

### [AR-147] Python `__init__.py` 文件产生 `foo.bar.__init__.ClassName` 而非 `foo.bar.ClassName` 的错误全限定名

- **文件**: `nop-code/nop-code-lang-python/.../PythonCodeFileAnalyzer.java:471-482`
- **证据片段**:
  ```java
  private String pathToModuleName(String filePath) {
      if (filePath == null) return "";
      String module = filePath.replace('\\', '/');
      if (module.endsWith(".py")) {
          module = module.substring(0, module.length() - 3);
      }
      module = module.replace('/', '.');
      while (module.startsWith(".")) {
          module = module.substring(1);
      }
      return module;
  }
  ```
  对于 `foo/bar/__init__.py`：
  1. 去后缀 → `foo/bar/__init__`
  2. 替换 `/` → `foo.bar.__init__`
  3. 返回 `foo.bar.__init__`（错误）
  
  正确结果应为 `foo.bar`（Python 包的惯用模块名）。
- **严重程度**: P1
- **现状**: `pathToModuleName` 从不处理 `__init__` 尾缀。`__init__.py` 是 Python 包的主要入口文件，其中定义的类（如 `class UserService`）的 qualifiedName 变为 `foo.bar.__init__.UserService` 而非正确的 `foo.bar.UserService`。影响所有包含 `__init__.py` 文件的 Python 项目。
- **风险**: (a) Python 包级别定义的符号的 qualifiedName 与 Java/TS 适配器约定的命名不一致；(b) `findSymbolByQualifiedName("foo.bar.UserService")` 永远找不到实际存在的符号；(c) 跨语言索引中，Python 包级别类型与其他语言的同类名符号无法关联。
- **建议**: 在 `.py` 后缀去除后，添加：
  ```java
  if (module.endsWith("/__init__")) {
      module = module.substring(0, module.length() - 9);
  }
  ```
- **信心水平**: 确定
- **发现来源视角**: 多语言适配器审计者

---

### [AR-148] Python 相对 import 解析剥离前导点号——`from ..sibling import X` 被当作绝对 import 处理

- **文件**: `nop-code/nop-code-lang-python/.../PythonImportResolver.java:44-67`
- **证据片段**:
  ```java
  private String extractModuleName(String importStmt) {
      ...
      trimmed = trimmed.trim();
      while (trimmed.startsWith(".")) {
          trimmed = trimmed.substring(1);  // 剥离所有前导点号
      }
      return trimmed.isEmpty() ? null : trimmed;
  }
  ```
  `from ..sibling.module import Foo` → 提取 `..sibling.module` → 剥离前导 `..` → `sibling.module`
  
  但 `..sibling.module` 表示"从父包的 sibling 子包"，而非顶层 `sibling.module`。
- **严重程度**: P2
- **现状**: Python 的相对 import（`from . import X`、`from ..pkg import Y`、`from ... import Z`）是项目内最常用的 import 模式之一。`extractModuleName` 无条件剥离所有前导点号，将相对路径转为绝对路径，导致：
  - `from ..service import UserService` → 解析为 `service.py`（可能匹配到错误的文件）
  - `from .models import User` → 解析为 `models.py`（在同一目录下搜索，而非正确的相对位置）
  - 大部分项目内的 import 都会被错误解析
  
  `resolveImports` 方法在 `projectFiles` 中查找 `moduleName.replace('.', '/') + ".py"` 和 `+ "/__init__.py"`，对被错误转换的路径可能匹配到错误的文件，或完全找不到。
- **风险**: Python 项目的依赖图大量缺失或错误。对于主要使用相对 import 的项目（如 Django 应用、Flask 项目），依赖关系几乎全部失效。
- **建议**: 将相对 import 的点号转换为相对路径计算：根据 `sourceFilePath` 和点号数量（`.` = 同级，`..` = 上级一层，`...` = 上级两层）计算出正确的 `targetFilePath`。或参考 TypeScript 适配器的 `resolveRelativePath()` 实现。
- **信心水平**: 确定
- **发现来源视角**: 多语言适配器审计者

---

### [AR-149] Python walkBlockChildren 不遍历控制流子块——if/for/while/with/try 内的嵌套定义被静默丢弃

- **文件**: `nop-code/nop-code-lang-python/.../PythonCodeFileAnalyzer.java:339-356`
- **证据片段**:
  ```java
  private void walkBlockChildren(TSNode block, String source, CodeSymbol parentSymbol,
                                  CodeFileAnalysisResult result) {
      for (int i = 0; i < block.getChildCount(); i++) {
          TSNode child = block.getChild(i);
          String type = child.getType();
          if ("class_definition".equals(type)) {
              visitClassDefinition(child, source, parentSymbol, result);
          } else if ("function_definition".equals(type)) {
              visitFunctionDefinition(child, source, parentSymbol, result);
          } else if ("decorator".equals(type)) {
              visitDecorator(child, source, parentSymbol, result);
          } else if ("expression_statement".equals(type)) {
              walkExpressionStatement(child, source, parentSymbol, result);
          }
          // ← 无 else 分支，所有控制流块被跳过
      }
  }
  ```
- **严重程度**: P2
- **现状**: `walkBlockChildren` 只处理 `class_definition`、`function_definition`、`decorator`、`expression_statement` 四种节点类型。Python 允许在 `if/for/while/with/try` 块内定义类和函数（如条件导入、平台特定实现、context manager 内的回调定义），这些定义会被静默跳过。
  
  对比 `walkNode` 入口方法（line 85-110）会遍历所有子节点，而 `walkBlockChildren` 只处理这四种。这意味着 `visitClassDefinition` 和 `visitFunctionDefinition` 中的递归调用 `walkBlockChildren` 会丢失控制流块内的嵌套定义。
- **风险**: 条件定义的类/函数（如 `if sys.platform == 'win32': class WinHandler: ...`）从索引中消失。虽然这不是最常见的 Python 模式，但在跨平台库和测试代码中很常见。
- **建议**: 在 `walkBlockChildren` 中添加对 `if_statement`、`for_statement`、`while_statement`、`with_statement`、`try_statement` 的递归遍历，递归调用 `walkBlockChildren` 处理其 body 子块。
- **信心水平**: 确定
- **发现来源视角**: 多语言适配器审计者

---

### [AR-150] Java 注解属性值通过 `toString()` 提取，导致 JSON 中的值带有多余引号

- **文件**: `nop-code/nop-code-lang-java/.../analyzer/JavaFileAnalyzer.java:717, 721`
- **证据片段**:
  ```java
  // NormalAnnotationExpr:
  nae.getPairs().forEach(pair -> attrs.put(pair.getNameAsString(), pair.getValue().toString()));
  // SingleMemberAnnotationExpr:
  usage.setAttributes(toJson(Map.of("value", smae.getMemberValue().toString())));
  ```
  `pair.getValue().toString()` 对字符串字面量 `"/api/v1"` 返回 `"/api/v1"`（包含引号），经 `toJson` 序列化后变为 `{"path":"\"/api/v1\""}`。
- **严重程度**: P2
- **现状**: JavaParser 的 `Expression.toString()` 对 `StringLiteralExpr` 返回带引号的源码表示。注解属性值（如 `@RequestMapping(path = "/api/v1")`）被存储为 `{"path":"\"/api/v1\""}` 而非 `{"path":"/api/v1"}`。所有下游消费者（前端 UI、注解查询、语义边提取器）需要对值做额外的去引号处理。
- **风险**: (a) `findByAnnotation` 查询 `/api/v1` 无法匹配，因为实际存储的是 `"/api/v1"`（带引号）；(b) `extractSpringRoutes` 的 `extractRoutePath` 调用 `replaceAll("^\"|\"$", "")` 试图去除引号，但只去除一层；(c) 前端显示注解属性时显示多余引号。
- **建议**: 使用 `pair.getValue().isStringLiteralExpr() ? ((StringLiteralExpr) pair.getValue()).getValue() : pair.getValue().toString()` 提取值，或使用 JavaParser 的 `LiteralExpr.getValue()` 方法获取去引号的值。
- **信心水平**: 确定
- **发现来源视角**: 多语言适配器审计者

---

### [AR-151] GraphExporter.escapeJson 缺少 `\t`、`\b`、`\f` 和控制字符转义——可产生无效 JSON

- **文件**: `nop-code/nop-code-graph/.../export/GraphExporter.java:258-261`
- **证据片段**:
  ```java
  private String escapeJson(String s) {
      if (s == null) return "";
      return s.replace("\\", "\\\\").replace("\"", "\\\"")
              .replace("\n", "\\n").replace("\r", "\\r");
  }
  ```
- **严重程度**: P2
- **现状**: `escapeJson` 只转义 `\`、`"`、`\n`、`\r` 四种字符。JSON 规范要求转义所有控制字符（U+0000 到 U+001F），包括 `\t`（U+0009）、`\b`（U+0008）、`\f`（U+000C）。如果符号的 qualifiedName 或 documentation 中包含 tab 或其他控制字符，`exportJson` 生成的输出不是合法 JSON。代码库中 `JsonTool` 类可用，但未在此处使用。
- **风险**: JSON 导出功能对包含 tab 或控制字符的数据产生无效输出。消费者（如可视化工具、数据分析管线）可能解析失败。
- **建议**: 使用项目已有的 `JsonTool.stringify(value)` 替代手动 JSON 构建，或补全转义：
  ```java
  return s.replace("\\", "\\\\").replace("\"", "\\\"")
          .replace("\n", "\\n").replace("\r", "\\r")
          .replace("\t", "\\t").replace("\b", "\\b").replace("\f", "\\f");
  ```
- **信心水平**: 确定
- **发现来源视角**: 图算法语义审计者

---

### [AR-152] InterfaceImplSynthesizer 仅按方法名匹配实现——重载方法产生缺失边

- **文件**: `nop-code/nop-code-graph/.../heuristic/InterfaceImplSynthesizer.java:51-53`
- **证据片段**:
  ```java
  String implMethodQName = implType.getQualifiedName()
          + "." + calleeSymbol.getName();
  CodeSymbol implMethod = symbolTable.getByQualifiedName(implMethodQName);
  ```
- **严重程度**: P2
- **现状**: 当接口方法 `process(String)` 被调用时，synthesizer 构建 `ImplClass.process` 并在 `symbolTable` 中查找。如果实现类有多个重载 `process(String)` 和 `process(String, int)`，`symbolTable.getByQualifiedName` 只能返回其中一个（取决于 `SymbolTable.add` 的覆盖行为），丢失另一个重载的合成边。
  
  对 Spring 应用中常见的 `Service` 接口多实现模式，每个重载方法应该产生独立的合成边。
- **风险**: 接口→实现的调用图中，重载方法的部分边丢失。影响分析结果的完整性。
- **建议**: 使用 `symbolTable.getAll()` 过滤出所有匹配 `implType.getQualifiedName() + "." + calleeSymbol.getName()` 前缀的符号（包括带参数签名的），或改进 `SymbolTable` 提供按前缀查找的方法。
- **信心水平**: 很可能（取决于重载方法在 symbolTable 中的存储方式）
- **发现来源视角**: 图算法语义审计者

---

### [AR-153] NopCodeIndex 缺少 `(name)` 唯一约束——允许同名索引

- **文件**: `nop-code/model/nop-code.orm.xml:28-65`（NopCodeIndex entity）
- **证据片段**:
  ```xml
  <entity name="NopCodeIndex" tableName="nop_code_index" ...>
      <columns>
          <column code="NAME" name="name" ... mandatory="true" precision="100"/>
          ...
      </columns>
      <indexes>
          <index name="idx_nop_code_index_status"><column name="status"/></index>
          <index name="idx_nop_code_index_root_path"><column name="rootPath"/></index>
          ...
      </indexes>
      <!-- 注意：无 <unique-keys> -->
  </entity>
  ```
- **严重程度**: P2
- **现状**: `NopCodeIndex` 没有任何唯一约束（除了主键 `id`）。`name` 字段是 `mandatory="true"` 且是 `tagSet=disp`（显示名），但没有唯一约束。可以创建多个同名索引（如两个 `name="nop-entropy"` 的索引）。BizModel 层的 `triggerFullIndex`、`triggerIncrementalIndex` 等方法按 `indexId` 操作，不检查 name 唯一性。
- **风险**: 用户可能无意创建重复索引，导致磁盘空间浪费和混淆。Dashboard 页面按名称列表时出现重复项。
- **建议**: 添加 `<unique-key name="uk_nop_code_index_name" columns="name"/>`。
- **信心水平**: 确定
- **发现来源视角**: ORM 模型考古学家

---

### [AR-154] NopCodeSemanticEdge ORM 审计列不一致——缺少 UPDATE_TIME，CREATE_TIME vs CREATED_TIME

- **文件**: `nop-code/model/nop-code.orm.xml`（NopCodeSemanticEdge entity，约 line 880-970）
- **证据片段**:
  ```xml
  <!-- NopCodeSemanticEdge -->
  <column code="CREATE_TIME" .../>  <!-- 注意：其他实体用 CREATED_TIME -->
  <column code="CREATED_BY" .../>   <!-- 无 precision 属性，其他实体用 precision="50" -->
  <!-- 无 UPDATE_TIME 列 -->
  <!-- 无 UPDATED_BY 列 -->
  <!-- 无 i18n-en:displayName -->
  ```
  对比 NopCodeFlow：
  ```xml
  <column code="CREATED_TIME" .../>
  <column code="UPDATE_TIME" .../>
  <column code="CREATED_BY" precision="50" .../>
  <column code="UPDATED_BY" precision="50" .../>
  ```
- **严重程度**: P2
- **现状**: `NopCodeSemanticEdge` 与其他实体的审计列不一致：
  1. `CREATE_TIME`（非 `CREATED_TIME`）——命名不统一
  2. 缺少 `UPDATE_TIME` 和 `UPDATED_BY`——无法追踪最后修改时间
  3. `CREATED_BY` 缺少 `precision` 属性
  4. 实体级缺少 `i18n-en:displayName`
  5. 该实体使用 `useLogicalDelete="true"`，但 `NopCodeIndex` 到它的 `cascadeDelete="true"` 关系会用物理删除覆盖逻辑删除机制
- **风险**: (a) 审计追踪不完整——无法知道语义边何时被修改；(b) 逻辑删除与级联物理删除的冲突可能导致数据不一致；(c) `CREATE_TIME` vs `CREATED_TIME` 命名不一致可能影响通用查询框架的行为。
- **建议**: 统一为 `CREATED_TIME`/`UPDATE_TIME`/`CREATED_BY`（precision=50）/`UPDATED_BY`（precision=50），添加 `i18n-en:displayName`。
- **信心水平**: 确定
- **发现来源视角**: ORM 模型考古学家

---

### [AR-155] BizModel 10 个只读查询方法使用 `@Auth(roles = "admin")` 而非 `@Auth(permissions = "code-query")`

- **文件**: `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java:124-264`
- **证据片段**:
  ```java
  // 只读分析查询，但用 admin role:
  @BizQuery
  @Auth(roles = "admin")
  public CommunityDetectionResultDTO detectCommunities(...)

  @BizQuery
  @Auth(roles = "admin")
  public GraphAnalysisResultDTO getGraphAnalysis(...)

  @BizQuery
  @Auth(roles = "admin")
  public ImpactResultDTO getImpactAnalysis(...)

  // 同文件中的其他只读查询，正确使用 permissions:
  @BizQuery
  @Auth(permissions = "code-query")
  public IndexStatsDTO getStats(...)
  ```
- **严重程度**: P2
- **现状**: `NopCodeIndexBizModel` 中有 10 个 `@BizQuery` 方法使用 `@Auth(roles = "admin")` 进行授权（`detectCommunities`、`getGraphAnalysis`、`getImpactAnalysis`、`findCycles`、`getDepGraph`、`getAffectedFlows`、`analyzeChanges`、`getCriticalNodes`、`getKnowledgeGaps`、`diffGraph`），而同文件中的其他只读查询（`getStats`、`getDeps`、`getReverseDeps`、`listFlows`、`getFlow`、`findDependentFiles`）使用 `@Auth(permissions = "code-query")`。
  
  这意味着：拥有 `code-query` 权限的非 admin 用户可以查询依赖关系和流程列表，但不能查询社区检测、影响分析、关键节点分析等功能。
- **风险**: 权限模型不一致。如果意图是让所有分析功能仅限 admin，则其他查询也应使用 `roles = "admin"`。如果意图是让 `code-query` 权限持有者访问所有查询功能，则分析类方法应改用 `permissions = "code-query"`。
- **建议**: 统一所有 `@BizQuery` 方法的授权模型。如果分析功能确实需要更高权限，至少使用一个新的 permission（如 `"code-analysis"`）而非 `roles = "admin"`。
- **信心水平**: 确定
- **发现来源视角**: BizModel API 契约审计者

---

### [AR-156] ProjectAnalyzer 构造函数接收 ExecutorService 但从未使用——并行分析未实现

- **文件**: `nop-code/nop-code-core/.../analyzer/ProjectAnalyzer.java:57-74`
- **证据片段**:
  ```java
  private final ExecutorService executor;  // line 58

  public ProjectAnalyzer(LanguageAdapterRegistry registry, ExecutorService executor) {
      this(registry, executor, DEFAULT_BATCH_SIZE);  // 传递但不使用
  }

  public ProjectAnalyzer(LanguageAdapterRegistry registry, ExecutorService executor, int batchSize) {
      this.registry = registry;
      this.executor = executor;  // 存储但从未读取
      this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
  }
  ```
  全文搜索 `executor` 的使用：仅在构造函数中赋值，无任何读取。
- **严重程度**: P3
- **现状**: `ProjectAnalyzer` 的两个构造函数接受 `ExecutorService` 参数并存储到 `executor` 字段，但该字段从未被任何方法使用。所有文件分析在 `analyzeProject` 中通过 `resourceLoader.depthIterator` 的惰性迭代串行执行。代码注释暗示曾计划并行分析但从未实现。
- **风险**: 调用方可能误以为传入 `ExecutorService` 会使分析并行化，实际行为始终串行。对大型项目，分析性能无法通过增加线程数提升。
- **建议**: 要么实现并行分析（使用 executor 并行处理 BatchQueue 中的文件），要么移除 executor 参数和字段以避免误导。
- **信心水平**: 确定
- **发现来源视角**: BizModel API 契约审计者

---

### [AR-157] ProjectAnalyzer.analyzeProject 忽略 languages 参数和 ProgressCallback——接口契约被破坏

- **文件**: `nop-code/nop-code-core/.../analyzer/ProjectAnalyzer.java:96-109`
- **证据片段**:
  ```java
  @Override
  public ProjectAnalysisResult analyzeProject(String projectRoot, Set<CodeLanguage> languages) {
      return analyzeProject(projectRoot);  // languages 被完全忽略
  }

  public ProjectAnalysisResult analyzeProject(String projectRoot, ProgressCallback progressCallback) {
      return analyzeProject(VirtualFileSystem.instance(), projectRoot, (String) null);
      // progressCallback 被完全忽略
  }
  ```
- **严重程度**: P3
- **现状**: `IProjectAnalyzer` 接口声明了 `analyzeProject(String, Set<CodeLanguage>)` 方法，暗示调用方可以指定只分析特定语言。但实现直接忽略 `languages` 参数，始终分析所有注册语言。`ProgressCallback` 也被静默忽略。调用方无法得知这些参数被忽略。
- **风险**: 调用方可能只期望分析 Java 文件，实际 Python 和 TypeScript 文件也被分析，浪费时间和资源。进度回调不生效，调用方无法跟踪大型项目的分析进度。
- **建议**: 要么实现参数功能（在文件遍历时按语言过滤），要么在 Javadoc 中明确标注参数被忽略，或者从接口中移除这些方法。
- **信心水平**: 确定
- **发现来源视角**: BizModel API 契约审计者

---

## 总评

本轮审查从多语言适配器、图算法语义边、ORM 模型完整性和 BizModel 授权一致性四个全新视角切入，发现了三个影响较大的正确性问题。

**最值得关注的 3 个方向**：

1. **Java RecordDeclaration 处理缺陷**（AR-146）——Java 14+ 的 record 类型完全被排除在符号映射之外，对使用 record 的现代 Java 项目影响显著。这是一个容易被忽视的"新语法旧代码"问题。

2. **Python `__init__.py` 全限定名错误**（AR-147）——`__init__.py` 是 Python 包的核心入口文件，此 bug 影响几乎所有 Python 项目的包级别符号。与 AR-141（TypeScript 全限定名问题）属于同一模式：非 Java 语言适配器的 qualified name 构建缺少语言特定的语义理解。

3. **SpringEventSynthesizer listener 覆盖**（AR-145）——AR-97 修复了 publisher 端的问题后，listener 端的"多对一覆盖"问题暴露出来。这是一个典型的"修了 A 面、漏了 B 面"的修复不完整问题。

多语言适配器的共同模式问题值得关注：Java 适配器最完善（使用 JavaParser 的符号求解器），TypeScript 和 Python 适配器使用 tree-sitter CST，缺少语义分析能力。两者的 qualified name 构建（AR-141、AR-147）、import 解析（AR-148）都存在因缺少语言语义理解而导致的正确性问题。

## 本次审查的盲区自评

1. **nop-code-web 前端页面**：未审查 XView 和前端组件的完整性和交互逻辑。
2. **beans.xml IoC 注入链路**：未系统验证所有 beans.xml 的注入配置是否与实际代码一致。
3. **端到端运行验证**：所有发现基于静态代码分析，未实际运行测试验证（特别是 AR-147 `__init__.py` 和 AR-146 Record 的问题）。
4. **nop-code-core 测试覆盖**：未评估测试的充分性和边界条件覆盖。
5. **搜索引擎集成**：`ISearchEngine` 接口和实现未深入审查。
6. **Delta 定制层**：未检查 Delta 文件是否覆盖了本报告涉及的关键代码路径。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 3    | SpringEvent listener 覆盖（AR-145）、Java Record 未入 symbolMap（AR-146）、Python `__init__.py` 全限定名（AR-147） |
| P2      | 8    | Python 相对 import（AR-148）、Python 控制流块（AR-149）、Java 注解引号（AR-150）、escapeJson 缺陷（AR-151）、接口实现重载（AR-152）、索引名无唯一约束（AR-153）、SemanticEdge 列不一致（AR-154）、BizModel 授权不一致（AR-155） |
| P3      | 2    | ExecutorService 死代码（AR-156）、接口参数被忽略（AR-157） |
