# nop-code 多语言代码索引设计

**日期**：2026-05-02
**范围**：`nop-code/` 模块重构 + `nop-java-parser` 角色调整
**目标**：建立与 Java 无关的通用代码结构模型，支持 Java/Python/TypeScript 等多语言代码索引，所有索引核心逻辑集中在 `nop-code` 模块

---

## 一、设计结论

将 `nop-code` 从「Java 专用代码索引」重构为「多语言代码索引服务」：

1. **`nop-java-parser` 缩减为纯适配器**：只保留 JavaParser 依赖和 AST → 通用模型的转换逻辑，不再包含社区检测、入口点评分等分析算法
2. **`nop-code` 包含一切**：通用代码模型、核心分析接口、分析算法实现、语言适配器入口、持久化、GraphQL API 全部在 `nop-code` 内
3. **语言无关的通用代码模型**：SymbolKind / AccessModifier / RelationType 等枚举设计覆盖 Java/Python/TypeScript 三种语言
4. **分析算法与语言解耦**：CommunityDetector / EntryPointScorer / ImpactAnalyzer 基于 CallGraph 抽象接口，不依赖任何语言特定的 AST 类型

---

## 二、现状分析

### 2.1 当前结构

```
nop-utils/nop-java-parser/              ← Java 专用分析引擎
  analyzer/
    JavaFileAnalyzer.java               ← JavaParser AST → SymbolInfo/MethodCall
    ProjectAnalyzer.java                ← 全局符号表 + 跨文件调用解析
    CommunityDetector.java              ← Leiden + LabelPropagation 社区检测
    EntryPointScorer.java               ← 入口点/God Node 识别
    ImpactAnalyzer.java                 ← 变更影响范围 BFS 分析
    SymbolInfo.java                     ← 符号模型（Java 特有字段：synchronizedFlag 等）
    SymbolKind.java                     ← CLASS/INTERFACE/ENUM/METHOD/FIELD...
    AccessModifier.java
    MethodCall.java
    ...

nop-code/                               ← 索引服务（含 ORM + GraphQL 设计）
  nop-code-dao/                         ← 7 张表 ORM 实体
  nop-code-service/                     ← BizModel 骨架（未实现）
  nop-code-api/
  nop-code-app/
  model/nop-code.orm.xml                ← 表结构定义
  design/ai-code-index-graphql-design.md ← GraphQL 接口设计（1493 行，已完整）
```

### 2.2 问题

| 问题 | 位置 | 影响 |
|------|------|------|
| 分析引擎绑定 Java | `nop-java-parser/analyzer/SymbolInfo` 含 `synchronizedFlag`, `nativeFlag` 等 Java 特有字段 | 无法复用于 Python/TypeScript |
| 分析算法在错误位置 | `CommunityDetector`, `EntryPointScorer`, `ImpactAnalyzer` 在 `nop-java-parser` | 与语言无关的算法不应依赖 Java 模块 |
| 模型不是通用的 | `SymbolKind` 只有 `ENUM_CONSTANT` 等 Java 概念 | TypeScript 的 `TYPE_ALIAS`、Python 的 `DECORATOR` 无处放 |
| 两层模型重复 | `SymbolInfo`（Java parser 层）和 `NopCodeSymbol`（DAO 层）字段重叠 | 数据转换无意义，维护成本高 |
| GraphQL 设计文档仅限 Java | `ai-code-index-graphql-design.md` 的 `NopCodeIndex.language` 是单值 | 多语言项目（如前端 TS + 后端 Java）无法表达 |

---

## 三、模块职责划分

### 3.1 重构后的结构

```
nop-utils/nop-java-parser/              ← Java 适配器（精简）
  src/main/java/io/nop/javaparser/
    JavaFileAnalyzer.java               ← 保留：JavaParser AST → 通用 CodeFileAnalysisResult
    JavaParseTool.java                  ← 保留：已有工具类
    JavaParserErrors.java               ← 保留：错误定义
    parse/                              ← 保留：解析结果
    delta/                              ← 保留：增量合并（与索引无关，独立功能）
    simplifier/                         ← 保留：代码简化
    format/                             ← 保留：代码格式化
    utils/                              ← 保留：工具类
  ❌ 删除 analyzer/ 下的：
    CommunityDetector.java              ← 移到 nop-code-core
    EntryPointScorer.java               ← 移到 nop-code-core
    ImpactAnalyzer.java                 ← 移到 nop-code-core
    SymbolInfo/SymbolKind/...           ← 替换为 nop-code-core 通用模型
    ProjectAnalyzer.java                ← 移到 nop-code-core（通用化）

nop-code/
  nop-code-core/                        ← 【新增子模块】通用代码模型 + 分析接口 + 分析算法
    src/main/java/io/nop/code/core/
      model/                            ← 通用代码模型
        CodeSymbol.java                 ← 替代 SymbolInfo，语言无关
        CodeFileAnalysisResult.java     ← 替代 JavaFileAnalysisResult
        CodeMethodCall.java             ← 替代 MethodCall
        CodeInheritance.java            ← 替代 InheritanceInfo
        CodeAnnotationUsage.java        ← 替代 AnnotationUsage
        CodeAccessModifier.java         ← 枚举：PUBLIC/PROTECTED/PRIVATE/PACKAGE_PRIVATE/INTERNAL（+Python）/NO_MODIFIER（+TS）
        CodeSymbolKind.java             ← 枚举：覆盖 Java/Python/TS（见 3.3）
        CodeRelationType.java           ← 枚举：EXTENDS/IMPLEMENTS/MIXIN（+Python）
        CodeUsageKind.java              ← 枚举：READ/WRITE/CALL/TYPE_REFERENCE/...
        CodeLanguage.java               ← 枚举：JAVA/PYTHON/TYPESCRIPT/...
      analyzer/                         ← 分析算法（语言无关）
        ICodeFileAnalyzer.java          ← 接口：(filePath, sourceCode) → CodeFileAnalysisResult
        IProjectAnalyzer.java           ← 接口：(projectRoot) → ProjectAnalysisResult
        ICommunityDetector.java         ← 接口：(callGraph, symbolTable) → CommunityResult
        IEntryPointScorer.java          ← 接口：(callGraph, reverseCallGraph) → List<EntryPointScore>
        IImpactAnalyzer.java            ← 接口：(target, callGraph) → ImpactResult
        CommunityDetector.java          ← Leiden + LabelPropagation（从 nop-java-parser 移入）
        EntryPointScorer.java           ← 入口点评分（从 nop-java-parser 移入）
        ImpactAnalyzer.java             ← BFS 影响分析（从 nop-java-parser 移入）
        ProjectAnalyzer.java            ← 通用项目分析器（从 nop-java-parser 移入并泛化）
      graph/                            ← 图数据结构
        CallGraph.java                  ← callerId → [calleeId] 邻接表
        SymbolTable.java                ← qualifiedName → CodeSymbol
      adapter/                          ← 语言适配器注册
        LanguageAdapterRegistry.java    ← 注册/查找 ILanguageAdapter
        ILanguageAdapter.java           ← 接口：getLanguage() / getFileAnalyzer() / getFilePatterns()

  nop-code-lang-java/                   ← 【新增子模块】Java 语言适配器
    src/main/java/io/nop/code/lang/java/
      JavaLanguageAdapter.java          ← 实现 ILanguageAdapter
      JavaCodeFileAnalyzer.java         ← 实现 ICodeFileAnalyzer（包装 nop-java-parser）
    pom.xml                             ← 依赖 nop-java-parser + nop-code-core

  nop-code-lang-python/                 ← 【新增子模块】Python 语言适配器
    src/main/java/io/nop/code/lang/python/
      PythonLanguageAdapter.java        ← 实现 ILanguageAdapter
      PythonCodeFileAnalyzer.java       ← 实现 ICodeFileAnalyzer（基于 tree-sitter-python 或 javaluator）
    pom.xml                             ← 依赖 nop-code-core

  nop-code-lang-typescript/             ← 【新增子模块】TypeScript 语言适配器
    src/main/java/io/nop/code/lang/typescript/
      TypeScriptLanguageAdapter.java    ← 实现 ILanguageAdapter
      TypeScriptCodeFileAnalyzer.java   ← 实现 ICodeFileAnalyzer（基于 tree-sitter-typescript）
    pom.xml                             ← 依赖 nop-code-core

  nop-code-dao/                         ← 持久化层（调整表结构适配多语言）
    model/nop-code.orm.xml              ← 调整：增加 language 列，调整字段覆盖 TS/Python

  nop-code-service/                     ← 服务层（实现 BizModel + BizLoader）
    ← 连接 nop-code-core 分析引擎 + nop-code-dao 持久化 + GraphQL API

  nop-code-app/                         ← 应用入口

  nop-code-api/                         ← API 接口定义

  nop-code-codegen/                     ← 代码生成

  nop-code-web/                         ← Web 前端（如有）
```

### 3.2 依赖关系

```
nop-code-lang-java ──depends──→ nop-code-core ←──depends── nop-code-lang-python
        │                              ↑                        │
        │                              │                        │
        └──→ nop-java-parser           │                        └─→ tree-sitter-python（可选）
                                       │
        nop-code-lang-typescript ──────┘
                │
                └──→ tree-sitter-typescript（可选）

nop-code-service ──depends──→ nop-code-dao
                              nop-code-core
                              nop-code-lang-java（运行时发现）
                              nop-code-lang-python（运行时发现）
                              nop-code-lang-typescript（运行时发现）
```

---

## 四、通用代码模型设计

### 4.1 CodeSymbolKind（符号类型枚举）

```java
public enum CodeSymbolKind {
    // ===== 所有语言通用 =====
    CLASS(10, "类/类型"),              // Java class, Python class, TS class
    INTERFACE(20, "接口"),             // Java interface, TS interface
    ENUM(30, "枚举"),                  // Java enum, TS enum, Python Enum subclass
    METHOD(50, "方法/函数"),           // Java method, Python def, TS function/method
    CONSTRUCTOR(60, "构造器"),         // Java constructor, Python __init__, TS constructor
    FIELD(70, "字段/属性"),            // Java field, Python class variable, TS property
    FUNCTION(55, "函数（顶层）"),      // Python 顶层函数, TS 独立 function
    CONSTANT(80, "常量"),             // Java enum constant, TS const, Python UPPER_VAR
    NAMESPACE(90, "命名空间/模块"),    // Java package, Python module, TS namespace/module

    // ===== Java 特有 =====
    ANNOTATION_TYPE(40, "注解类型"),   // Java @interface

    // ===== TypeScript 特有 =====
    TYPE_ALIAS(45, "类型别名"),        // TS type X = ...
    MIXIN(46, "Mixin"),               // TS mixin pattern

    // ===== Python 特有 =====
    DECORATOR(47, "装饰器"),           // Python @decorator

    // ===== 通用辅助 =====
    PARAMETER(95, "参数"),            // 方法参数
    LOCAL_VARIABLE(96, "局部变量"),
    TYPE_PARAMETER(97, "类型参数"),    // Java <T>, TS <T>
    IMPORT(98, "导入"),               // import 语句
    ;
}
```

**设计原则**：
- 每种语言的分析器只使用自己需要的 Kind，忽略其他的
- `METHOD` vs `FUNCTION`：Java 没有顶层函数用 METHOD；Python/TS 顶层函数用 FUNCTION，类方法用 METHOD
- GraphQL 查询时通过 `kind` 过滤：`NopCodeSymbol__findPage(kinds: [METHOD, FUNCTION])` 等效于"所有函数"

### 4.2 CodeAccessModifier（访问修饰符）

```java
public enum CodeAccessModifier {
    PUBLIC(10, "公开"),
    PROTECTED(20, "受保护"),
    PRIVATE(30, "私有"),
    PACKAGE_PRIVATE(40, "包私有"),      // Java: default, Python: _
    INTERNAL(41, "内部"),              // TS: internal（罕见）, C#: internal
    NO_MODIFIER(50, "无修饰符"),       // Python/TS 默认情况
    ;
}
```

**语言映射**：

| 语言 | PUBLIC | PROTECTED | PRIVATE | PACKAGE_PRIVATE | INTERNAL | NO_MODIFIER |
|------|--------|-----------|---------|-----------------|----------|-------------|
| Java | `public` | `protected` | `private` | _(default)_ | - | - |
| Python | - | `_name` | `__name` | - | - | 默认 |
| TypeScript | `public` | `protected` | `private` | - | `internal` | 默认 |

### 4.3 CodeSymbol（通用符号模型）

```java
/**
 * 通用代码符号模型 - 语言无关
 * 替代 nop-java-parser 中的 SymbolInfo
 */
@DataBean
public class CodeSymbol {
    private String id;
    private CodeSymbolKind kind;
    private String name;
    private String qualifiedName;
    private CodeAccessModifier accessModifier;
    private boolean deprecated;
    private String documentation;

    // ===== 位置信息 =====
    private int line;
    private int column;
    private int endLine;
    private int endColumn;

    // ===== 层级关系 =====
    private String parentId;             // 嵌套类/内部类
    private String declaringSymbolId;    // 方法所属类，字段所属类

    // ===== 类型相关（CLASS/INTERFACE/ENUM/TYPE_ALIAS） =====
    private String superClassName;
    private boolean abstractFlag;
    private boolean finalFlag;

    // ===== 方法/函数相关 =====
    private String signature;
    private String returnType;
    private boolean staticFlag;
    private boolean asyncFlag;           // Python async def, TS async function

    // ===== 字段/属性相关 =====
    private String fieldType;
    private boolean readonlyFlag;        // TS readonly, Python @property

    // ===== 语言特有扩展（JSON） =====
    private String extData;
    // Java: synchronizedFlag, nativeFlag, transientFlag, volatileFlag
    // Python: isClassmethod, isStaticmethod, decoratorList
    // TS: isArrowFunction, isGenerator, hasOptionalParams
}
```

### 4.4 CodeLanguage（语言枚举）

```java
public enum CodeLanguage {
    JAVA("java", ".java"),
    PYTHON("python", ".py"),
    TYPESCRIPT("typescript", ".ts", ".tsx"),
    JAVASCRIPT("javascript", ".js", ".jsx"),
    ;
}
```

### 4.5 CodeFileAnalysisResult（通用文件分析结果）

```java
@DataBean
public class CodeFileAnalysisResult {
    private String filePath;
    private String sourceCode;
    private int lineCount;
    private CodeLanguage language;
    private String packageName;          // Java: package, Python: module path, TS: namespace
    private List<String> imports = new ArrayList<>();
    private List<CodeSymbol> symbols = new ArrayList<>();
    private List<CodeMethodCall> calls = new ArrayList<>();
    private List<CodeInheritance> inheritances = new ArrayList<>();
    private List<CodeAnnotationUsage> annotationUsages = new ArrayList<>();
}
```

---

## 五、核心接口设计

### 5.1 ICodeFileAnalyzer（文件分析器接口）

```java
/**
 * 语言无关的文件分析器接口
 * 每种语言提供一个实现
 */
public interface ICodeFileAnalyzer {
    /**
     * 获取支持的语言
     */
    CodeLanguage getLanguage();

    /**
     * 分析单个源代码文件
     *
     * @param filePath   文件相对路径
     * @param sourceCode 源代码内容
     * @return 分析结果，null 表示无法解析
     */
    CodeFileAnalysisResult analyze(String filePath, String sourceCode);

    /**
     * 该分析器支持的文件扩展名
     * 例：Java → [".java"], TypeScript → [".ts", ".tsx"]
     */
    List<String> getFileExtensions();
}
```

### 5.2 ILanguageAdapter（语言适配器接口）

```java
/**
 * 语言适配器 - 注册到 LanguageAdapterRegistry
 * 提供该语言的分析器和文件匹配规则
 */
public interface ILanguageAdapter {
    CodeLanguage getLanguage();
    ICodeFileAnalyzer getFileAnalyzer();
    List<String> getFileExtensions();
    List<String> getExcludePatterns();   // 例：Python → ["__pycache__/", ".venv/"]
}
```

### 5.3 IProjectAnalyzer（项目分析器接口）

```java
/**
 * 项目级分析器接口
 * 扫描项目目录，自动识别语言，调度对应分析器
 */
public interface IProjectAnalyzer {
    /**
     * 分析整个项目（自动检测语言）
     */
    ProjectAnalysisResult analyzeProject(Path projectRoot);

    /**
     * 分析项目（指定语言）
     */
    ProjectAnalysisResult analyzeProject(Path projectRoot, Set<CodeLanguage> languages);

    /**
     * 增量分析（只处理变更文件）
     */
    ProjectAnalysisResult analyzeIncremental(Path projectRoot, Set<String> changedFilePaths);
}
```

### 5.4 分析算法接口

```java
/**
 * 社区检测 - 基于调用图的图算法，与语言无关
 */
public interface ICommunityDetector {
    CommunityDetectionResult detect(
        CallGraph callGraph,
        SymbolTable symbolTable,
        CommunityConfig config);
}

/**
 * 入口点评分 - 基于调用图的度数分析
 */
public interface IEntryPointScorer {
    List<EntryPointScore> score(
        CallGraph callGraph,
        CallGraph reverseCallGraph,
        SymbolTable symbolTable);
}

/**
 * 影响范围分析 - BFS 遍历调用图
 */
public interface IImpactAnalyzer {
    ImpactResult analyze(
        String targetQualifiedName,
        CallGraph callGraph,
        CallGraph reverseCallGraph,
        SymbolTable symbolTable,
        int maxDepth);
}
```

---

## 六、数据库表调整

### 6.1 nop_code_index 调整

现有 `language` 字段从 `VARCHAR(20)` 单值改为**支持多语言**：

| 字段 | 类型 | 调整说明 |
|------|------|----------|
| `LANGUAGE` | VARCHAR(100) | 存储逗号分隔的语言列表：`"JAVA,PYTHON"` 或 `"TYPESCRIPT"` |

或者更好的方案：`LANGUAGE` 保留为默认语言，新增 `LANGUAGES` JSON 字段存储已索引的语言列表。

### 6.2 nop_code_file 调整

| 字段 | 类型 | 说明 |
|------|------|------|
| `LANGUAGE` | VARCHAR(20) | 单个文件的语言：`JAVA` / `PYTHON` / `TYPESCRIPT` |

### 6.3 nop_code_symbol 调整

新增字段覆盖多语言：

| 字段 | 类型 | 说明 |
|------|------|------|
| `ASYNC_FLAG` | BOOLEAN | Python async / TS async |
| `READONLY_FLAG` | BOOLEAN | TS readonly |
| `EXT_DATA` | VARCHAR(65535) | JSON：语言特有属性 |

`EXT_DATA` 示例：

```json
// Java 方法
{"synchronized": true, "native": false}

// Python 方法
{"isClassmethod": true, "decorators": ["@staticmethod", "@Inject"]}

// TypeScript 函数
{"isArrowFunction": true, "hasOptionalParams": true, "generics": ["T", "K"]}
```

### 6.4 nop_code.orm.xml dict 调整

```xml
<dict label="符号类型" name="code/symbol_kind" valueType="int">
    <!-- 通用 -->
    <option code="CLASS" label="类/类型" value="10"/>
    <option code="INTERFACE" label="接口" value="20"/>
    <option code="ENUM" label="枚举" value="30"/>
    <option code="ANNOTATION_TYPE" label="注解类型" value="40"/>
    <option code="TYPE_ALIAS" label="类型别名" value="45"/>
    <option code="MIXIN" label="Mixin" value="46"/>
    <option code="DECORATOR" label="装饰器" value="47"/>
    <option code="METHOD" label="方法" value="50"/>
    <option code="FUNCTION" label="函数" value="55"/>
    <option code="CONSTRUCTOR" label="构造器" value="60"/>
    <option code="FIELD" label="字段/属性" value="70"/>
    <option code="CONSTANT" label="常量" value="80"/>
    <option code="NAMESPACE" label="命名空间" value="90"/>
    <option code="PARAMETER" label="参数" value="95"/>
    <option code="LOCAL_VARIABLE" label="局部变量" value="96"/>
    <option code="TYPE_PARAMETER" label="类型参数" value="97"/>
    <option code="IMPORT" label="导入" value="98"/>
</dict>

<dict label="编程语言" name="code/language" valueType="int">
    <option code="JAVA" label="Java" value="10"/>
    <option code="PYTHON" label="Python" value="20"/>
    <option code="TYPESCRIPT" label="TypeScript" value="30"/>
    <option code="JAVASCRIPT" label="JavaScript" value="40"/>
</dict>
```

---

## 七、语言适配器实现方案

### 7.1 Java 适配器（nop-code-lang-java）

```java
package io.nop.code.lang.java;

/**
 * Java 语言适配器
 * 包装 nop-java-parser 的 JavaFileAnalyzer
 */
public class JavaLanguageAdapter implements ILanguageAdapter {
    private final JavaCodeFileAnalyzer analyzer = new JavaCodeFileAnalyzer();

    @Override
    public CodeLanguage getLanguage() { return CodeLanguage.JAVA; }

    @Override
    public ICodeFileAnalyzer getFileAnalyzer() { return analyzer; }

    @Override
    public List<String> getFileExtensions() { return List.of(".java"); }

    @Override
    public List<String> getExcludePatterns() {
        return List.of("**/target/**", "**/build/**", "**/.git/**");
    }
}
```

```java
package io.nop.code.lang.java;

/**
 * Java 文件分析器
 * 将 nop-java-parser 的 JavaFileAnalysisResult 转换为通用 CodeFileAnalysisResult
 */
public class JavaCodeFileAnalyzer implements ICodeFileAnalyzer {
    private final io.nop.javaparser.analyzer.JavaFileAnalyzer delegate = 
        new io.nop.javaparser.analyzer.JavaFileAnalyzer();

    @Override
    public CodeLanguage getLanguage() { return CodeLanguage.JAVA; }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        var javaResult = delegate.analyze(filePath, sourceCode);
        if (javaResult == null) return null;
        return convertResult(javaResult);
    }

    @Override
    public List<String> getFileExtensions() { return List.of(".java"); }

    private CodeFileAnalysisResult convertResult(JavaFileAnalysisResult javaResult) {
        CodeFileAnalysisResult result = new CodeFileAnalysisResult();
        result.setFilePath(javaResult.getFilePath());
        result.setSourceCode(javaResult.getSourceCode());
        result.setLineCount(javaResult.getLineCount());
        result.setLanguage(CodeLanguage.JAVA);
        result.setPackageName(javaResult.getPackageName());
        result.setImports(javaResult.getImports());

        // SymbolInfo → CodeSymbol
        for (var sym : javaResult.getSymbols()) {
            result.getSymbols().add(convertSymbol(sym));
        }
        // MethodCall → CodeMethodCall
        for (var call : javaResult.getCalls()) {
            result.getCalls().add(convertCall(call));
        }
        // ... inheritance, annotationUsages 类似
        return result;
    }

    private CodeSymbol convertSymbol(SymbolInfo sym) {
        CodeSymbol cs = new CodeSymbol();
        cs.setId(sym.getId());
        cs.setKind(convertKind(sym.getKind()));    // SymbolKind → CodeSymbolKind
        cs.setName(sym.getName());
        cs.setQualifiedName(sym.getQualifiedName());
        cs.setAccessModifier(convertAccess(sym.getAccessModifier()));
        // ... 其他字段
        // Java 特有字段放入 extData
        Map<String, Object> ext = new HashMap<>();
        if (sym.isSynchronizedFlag()) ext.put("synchronized", true);
        if (sym.isNativeFlag()) ext.put("native", true);
        if (sym.isVolatileFlag()) ext.put("volatile", true);
        if (sym.isTransientFlag()) ext.put("transient", true);
        if (!ext.isEmpty()) cs.setExtData(toJson(ext));
        return cs;
    }

    private CodeSymbolKind convertKind(SymbolKind kind) {
        return switch (kind) {
            case CLASS -> CodeSymbolKind.CLASS;
            case INTERFACE -> CodeSymbolKind.INTERFACE;
            case ENUM -> CodeSymbolKind.ENUM;
            case ENUM_CONSTANT -> CodeSymbolKind.CONSTANT;
            case ANNOTATION_TYPE -> CodeSymbolKind.ANNOTATION_TYPE;
            case METHOD -> CodeSymbolKind.METHOD;
            case CONSTRUCTOR -> CodeSymbolKind.CONSTRUCTOR;
            case FIELD -> CodeSymbolKind.FIELD;
        };
    }
}
```

### 7.2 Python 适配器（nop-code-lang-python）

Python 解析方案选择：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **tree-sitter-python** | 快速、增量解析、无外部依赖 | 需要 JNI 或 GraalVM |
| **Jython** | 纯 Java 调用 Python | Python 2.7，已废弃 |
| **ast 模块 + subprocess** | 最简单，用系统 Python | 需要 Python 环境 |
| **手动实现 Python ast 解析** | 纯 Java，无外部依赖 | 工作量大 |

**推荐方案**：tree-sitter-python（通过 tree-sitter 的 Java 绑定 `org.tree-grammar:tree-sitter-java`）。如果 tree-sitter 不可用，降级为正则表达式提取基础结构（类名、函数名、import）。

```java
package io.nop.code.lang.python;

public class PythonCodeFileAnalyzer implements ICodeFileAnalyzer {
    @Override
    public CodeLanguage getLanguage() { return CodeLanguage.PYTHON; }

    @Override
    public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
        // 方案1: tree-sitter-python 解析（完整）
        // 方案2: 正则提取（降级）
        // 返回通用 CodeFileAnalysisResult
    }

    @Override
    public List<String> getFileExtensions() { return List.of(".py"); }
}
```

**Python 符号映射**：

| Python 概念 | CodeSymbolKind | 说明 |
|-------------|---------------|------|
| `class Foo` | CLASS | 类定义 |
| `def foo()` (顶层) | FUNCTION | 模块级函数 |
| `def foo(self, ...)` (类内) | METHOD | 实例方法 |
| `def __init__` | CONSTRUCTOR | 构造器 |
| `x = ...` (类级) | FIELD | 类属性 |
| `@property` 方法 | FIELD | 属性（只读） |
| `UPPER_CASE` (模块级) | CONSTANT | 常量 |
| `@decorator` | DECORATOR | 装饰器（作为独立符号） |
| `import x` | IMPORT | 导入 |

### 7.3 TypeScript 适配器（nop-code-lang-typescript）

TypeScript 解析方案：

| 方案 | 优点 | 缺点 |
|------|------|------|
| **tree-sitter-typescript** | 快速、完整 AST | 需要 JNI |
| **调用 tsc --noEmit + API** | 完整类型信息 | 需要 Node.js 环境 |
| **正则提取** | 纯 Java、简单 | 信息不全 |

**推荐方案**：tree-sitter-typescript。降级方案同 Python。

**TypeScript 符号映射**：

| TS 概念 | CodeSymbolKind | 说明 |
|---------|---------------|------|
| `class Foo` | CLASS | 类 |
| `interface Foo` | INTERFACE | 接口 |
| `enum Foo` | ENUM | 枚举 |
| `type Foo = ...` | TYPE_ALIAS | 类型别名 |
| `function foo()` | FUNCTION | 顶层函数 |
| `method()` (类内) | METHOD | 类方法 |
| `constructor()` | CONSTRUCTOR | 构造器 |
| `property` / `field` | FIELD | 属性/字段 |
| `const x` (模块级) | CONSTANT | 常量 |
| `namespace Foo` | NAMESPACE | 命名空间 |

---

## 八、语言适配器注册机制

### 8.1 Nop IoC 自动发现

利用 Nop IoC 的 `@Inject` 和自动发现机制：

```xml
<!-- nop-code-core 的 beans.xml -->
<bean id="languageAdapterRegistry" class="io.nop.code.core.adapter.LanguageAdapterRegistry">
    <inject />
</bean>
```

```java
/**
 * 语言适配器注册表
 * 通过 Nop IoC 自动发现所有 ILanguageAdapter 实现
 */
public class LanguageAdapterRegistry {
    private final Map<CodeLanguage, ILanguageAdapter> adapters = new HashMap<>();

    @Inject
    public void setAdapters(List<ILanguageAdapter> adapterList) {
        for (ILanguageAdapter adapter : adapterList) {
            adapters.put(adapter.getLanguage(), adapter);
        }
    }

    public ILanguageAdapter getAdapter(CodeLanguage language) {
        return adapters.get(language);
    }

    public ICodeFileAnalyzer getAnalyzer(Path filePath) {
        String name = filePath.getFileName().toString();
        for (ILanguageAdapter adapter : adapters.values()) {
            for (String ext : adapter.getFileExtensions()) {
                if (name.endsWith(ext)) return adapter.getFileAnalyzer();
            }
        }
        return null;
    }

    public Set<CodeLanguage> getSupportedLanguages() {
        return adapters.keySet();
    }
}
```

### 8.2 模块化依赖

```xml
<!-- nop-code-service 的 beans.xml 只依赖 core -->
<!-- nop-code-lang-java 的 beans.xml 注册 JavaLanguageAdapter -->
<bean id="javaLanguageAdapter" class="io.nop.code.lang.java.JavaLanguageAdapter" />
```

运行时：`nop-code-app` 的 pom.xml 包含需要的语言模块即可。

---

## 九、nop-java-parser 精简方案

### 9.1 保留在 nop-java-parser 的

```
nop-java-parser/
  src/main/java/io/nop/javaparser/
    JavaFileAnalyzer.java         ← 保留（核心：JavaParser AST → SymbolInfo）
    JavaParseTool.java            ← 保留
    JavaParserErrors.java         ← 保留
    parse/                        ← 保留
    delta/                        ← 保留（DeltaJavaMerger 等是独立的增量合并功能）
    simplifier/                   ← 保留（JavaFileSimplifier）
    format/                       ← 保留（JavaParserCodeFormatter）
    utils/                        ← 保留
    analyzer/
      JavaFileAnalysisResult.java ← 保留（Java 专用结果，供 JavaCodeFileAnalyzer 转换）
      SymbolInfo.java             ← 保留（Java 专用符号模型）
      SymbolKind.java             ← 保留（Java 专用枚举）
      AccessModifier.java         ← 保留
      MethodCall.java             ← 保留
      MethodCallFilter.java       ← 保留
      AnnotationUsage.java        ← 保留
      InheritanceInfo.java        ← 保留
      RelationType.java           ← 保留
      UsageKind.java              ← 保留
```

### 9.2 移到 nop-code-core 的

```
CommunityDetector.java            ← 移到 io.nop.code.core.analyzer.CommunityDetector
EntryPointScorer.java             ← 移到 io.nop.code.core.analyzer.EntryPointScorer
ImpactAnalyzer.java               ← 移到 io.nop.code.core.analyzer.ImpactAnalyzer
ProjectAnalyzer.java              ← 移到 io.nop.code.core.analyzer.ProjectAnalyzer（泛化）
```

### 9.3 移动后的改动

1. **CommunityDetector** — 把 `SymbolInfo` 参数改为 `SymbolTable` 接口
2. **EntryPointScorer** — 同上
3. **ImpactAnalyzer** — 同上
4. **ProjectAnalyzer** — 把 `JavaFileAnalyzer` 改为 `LanguageAdapterRegistry`，自动识别文件语言

---

## 十、实现优先级

### Phase 1：核心模型 + Java 通路（优先）

1. **创建 `nop-code-core` 子模块**，定义通用模型和接口
2. **从 `nop-java-parser` 移出分析算法**到 `nop-code-core`
3. **创建 `nop-code-lang-java` 子模块**，实现 Java 适配器
4. **修改 `nop-code-dao`** 表结构适配多语言（增加 language 列和 dict）
5. **验证 Java 通路**：Java 文件 → JavaCodeFileAnalyzer → CodeFileAnalysisResult → 写入 DB → GraphQL 查询

### Phase 2：TypeScript 适配器

6. **创建 `nop-code-lang-typescript` 子模块**
7. **实现 TypeScript 文件分析器**（tree-sitter 或正则降级）
8. **验证 TS 通路**：前端 TS 项目 → TypeScriptCodeFileAnalyzer → 写入 DB → GraphQL 查询

### Phase 3：Python 适配器

9. **创建 `nop-code-lang-python` 子模块**
10. **实现 Python 文件分析器**
11. **验证 Python 通路**

### Phase 4：GraphQL 服务层完善

12. **实现 BizModel / BizLoader**（按已有设计文档 `ai-code-index-graphql-design.md`）
13. **实现增量索引**（基于文件 SHA256 哈希）
14. **集成测试**

---

## 十一、与已有设计文档的关系

`nop-code/design/ai-code-index-graphql-design.md` 的 GraphQL Schema 设计**大部分保持不变**：

| 需要调整 | 内容 |
|----------|------|
| `NopCodeIndex.language: String!` | 改为 `languages: [String!]!`（多语言） |
| `NopCodeSymbolKind` 枚举 | 增加 `FUNCTION`、`TYPE_ALIAS`、`MIXIN`、`DECORATOR`、`NAMESPACE`、`CONSTANT` |
| `NopCodeFile.language: String!` | 保留（单个文件只有一个语言） |
| `NopCodeMethod.isSynchronized` | 移到 `extData`，改为 `NopCodeMethod.extData: String` |
| 新增 `NopCodeMethod.isAsync: Boolean!` | Python async / TS async |
| 新增 `NopCodeField.isReadonly: Boolean!` | TS readonly / Python @property |
| `Mutation.NopCodeIndex__indexDirectory` 的 `filePattern: String = "**/*.java"` | 改为 `"**/*"` 或删除默认值，由语言适配器自动识别 |

**不需要调整的**：
- 所有 Query/Mutation 入口和命名
- NopCodeClass / NopCodeInterface / NopCodeEnum 结构
- NopCodeTypeHierarchy / NopCodeCallHierarchy
- 分页和 BatchGet 接口
- BizModel/BizLoader 实现模式

---

## 十二、文件模式匹配

### 自动语言检测

```java
// ProjectAnalyzer 中的语言检测逻辑
public class LanguageDetector {
    private static final Map<String, CodeLanguage> EXT_MAP = Map.of(
        ".java", CodeLanguage.JAVA,
        ".py", CodeLanguage.PYTHON,
        ".ts", CodeLanguage.TYPESCRIPT,
        ".tsx", CodeLanguage.TYPESCRIPT,
        ".js", CodeLanguage.JAVASCRIPT,
        ".jsx", CodeLanguage.JAVASCRIPT
    );

    public static CodeLanguage detect(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        return EXT_MAP.get(name.substring(dot));
    }
}
```

### 排除规则

每种语言的适配器提供默认排除规则：

| 语言 | 排除模式 |
|------|----------|
| Java | `**/target/**`, `**/build/**`, `**/.git/**` |
| Python | `**/__pycache__/**`, `**/.venv/**`, `**/venv/**`, `**/*.pyc` |
| TypeScript | `**/node_modules/**`, `**/dist/**`, `**/.git/**`, `**/*.d.ts` |

---

## 十三、关键设计决策

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 通用模型放哪里 | nop-code-core / 新模块 | **nop-code-core** | 减少模块数量，与 DAO 同模块 |
| 分析算法放哪里 | nop-java-parser / nop-code-core | **nop-code-core** | 算法与语言无关 |
| 语言特有字段怎么处理 | 子类化 / extData JSON | **extData JSON** | 避免类爆炸，GraphQL 端直接透传 |
| Python/TS 解析方案 | tree-sitter / 正则 / 外部进程 | **tree-sitter 优先，正则降级** | 平衡功能与依赖 |
| 适配器发现机制 | 手动注册 / Nop IoC 自动 | **Nop IoC 自动** | 遵循 Nop 框架风格 |
| 多语言索引 | 一个索引一个语言 / 一个索引多语言 | **一个索引多语言** | 现代项目多是多语言（Java+TS） |

---

## 附录 A：设计验证（2026-05-02）

### A.1 已验证的事实

| 项 | 验证结果 |
|----|----------|
| nop-code-core 不存在 | ✅ 确认，完全新建 |
| nop-code-service 仅有骨架 | ✅ 7 个 BizModel 全部 `extends CrudBizModel<T>`，无自定义逻辑 |
| nop-code-api 为空 | ✅ 无 Java 文件，仅有 pom.xml |
| nop-code-dao 实体已定义 | ✅ NopCodeIndex/File/Symbol/Usage/Call/Inheritance/AnnotationUsage 实体+关系已生成 |
| nop-code-service 不依赖 nop-java-parser | ✅ 无依赖，可安全添加 |
| nop-java-parser 位于 `nop-utils/` 下 | ⚠️ **不在 `nop-code/` 下**，设计中 `nop-code-lang-java` 需要 `compile io.github.entropy-cloud:nop-java-parser` |

### A.2 nop-java-parser 当前依赖

```xml
<!-- nop-utils/nop-java-parser/pom.xml -->
com.github.javaparser:javaparser-core
com.github.javaparser:javaparser-symbol-solver-core
io.github.entropy-cloud:nop-xlang
org.jgrapht:jgrapht-core:1.5.2          ← 图算法库
nl.cwts:networkanalysis:1.3.0            ← Leiden 社区检测
```

**注意**：jgrapht-core 和 networkanalysis 目前在 nop-java-parser 中。移出分析算法到 nop-code-core 时，这两个依赖也需移到 nop-code-core 的 pom.xml。

### A.3 nop-code-service 当前 BizModel 清单

| BizModel | 位置 | 状态 |
|----------|------|------|
| NopCodeIndexBizModel | `service/entity/` | 骨架，仅 CrudBizModel |
| NopCodeSymbolBizModel | `service/entity/` | 骨架，仅 CrudBizModel |
| NopCodeFileBizModel | `service/entity/` | 骨架，仅 CrudBizModel |
| NopCodeCallBizModel | `service/entity/` | 骨架，仅 CrudBizModel |
| NopCodeUsageBizModel | `service/entity/` | 骨架，仅 CrudBizModel |
| NopCodeInheritanceBizModel | `service/entity/` | 骨架，仅 CrudBizModel |
| NopCodeAnnotationUsageBizModel | `service/entity/` | 骨架，仅 CrudBizModel |

每个 BizModel 对应的 Biz 接口（如 `INopCodeIndexBiz`）在 nop-code-dao 中定义，仅继承 `ICrudBiz<T>`。

### A.4 Tree-sitter Java 绑定（确认可用）

**推荐方案：Tree-sitter NG (`io.github.bonede`)**

```xml
<!-- nop-code-lang-python 依赖 -->
<dependency>
    <groupId>io.github.bonede</groupId>
    <artifactId>tree-sitter</artifactId>
    <version>0.25.3</version>
</dependency>
<dependency>
    <groupId>io.github.bonede</groupId>
    <artifactId>tree-sitter-python</artifactId>
    <version>0.25.3</version>
</dependency>

<!-- nop-code-lang-typescript 依赖 -->
<dependency>
    <groupId>io.github.bonede</groupId>
    <artifactId>tree-sitter-typescript</artifactId>
    <version>0.23.2</version>
</dependency>
```

**评估**：
- JDK 8+ 兼容（与 Nop 平台要求一致）
- Maven Central 直接可用，无需手动加载 native library
- 20+ 语言解析器，包含 Python、TypeScript、JavaScript
- 活跃维护（最新版本 2026-03）
- ANTLR 备选：Python3 grammar 可用但 TypeScript grammar 没有 Java target
