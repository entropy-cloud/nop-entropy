# AI代码索引GraphQL接口设计

## 设计目标

为AI自动生成代码和自动分析代码提供一个辅助索引工具，相比逐个文件读取文本更快地掌握项目信息。

**核心能力**：
- 符号查找：按名称、类型、模式搜索符号
- 类Outline：获取类的完整结构（方法、字段、注解等）
- 引用查找：查找符号在哪里被使用
- 调用关系：方法的调用者和被调用者
- 继承关系：类的基类和派生类
- 文件结构：获取文件的大纲和符号列表

**设计原则**：
- **GraphQL风格**：返回丰富对象类型，客户端按需选择字段
- **NopCode前缀**：所有类型使用 `NopCode` 前缀，避免与其他模块冲突
- 只读接口，不修改代码
- 支持增量索引
- 优化AI查询场景（批量查询、上下文获取）
- 遵循Nop GraphQL命名规范

---

## 数据库表映射

| GraphQL类型 | 数据库表 | 说明 |
|------------|----------|------|
| NopCodeIndex | nop_code_index | 代码索引 |
| NopCodeSymbol | nop_code_symbol | 符号基表 |
| NopCodeClass | nop_code_class | 类 |
| NopCodeInterface | nop_code_interface | 接口 |
| NopCodeEnum | nop_code_enum | 枚举 |
| NopCodeMethod | nop_code_method | 方法 |
| NopCodeField | nop_code_field | 字段 |
| NopCodeConstructor | nop_code_constructor | 构造器 |
| NopCodeAnnotationType | nop_code_annotation_type | 注解类型 |
| NopCodeUsage | nop_code_usage | 符号引用 |
| NopCodeCall | nop_code_call | 方法调用关系 |
| NopCodeInheritance | nop_code_inheritance | 继承关系 |
| NopCodeFile | nop_code_file | 文件信息 |

---

## 一、GraphQL Schema 定义

```graphql
# ========================================
# AI CODE INDEX - GRAPHQL SCHEMA
# 用于AI辅助代码分析和生成的索引服务
# 所有类型使用 NopCode 前缀
#
# 数据库表映射：
# - NopCodeIndex → nop_code_index
# - NopCodeClass → nop_code_class
# - NopCodeMethod → nop_code_method
# - NopCodeField → nop_code_field
# - NopCodeUsage → nop_code_usage
# ========================================

# ========================================
# 枚举类型
# ========================================

"""
符号类型
"""
enum NopCodeSymbolKind {
  CLASS
  INTERFACE
  ENUM
  ANNOTATION
  METHOD
  CONSTRUCTOR
  FIELD
  PARAMETER
  LOCAL_VARIABLE
  TYPE_PARAMETER
  PACKAGE
  MODULE
}

"""
访问修饰符
"""
enum NopCodeAccessModifier {
  PUBLIC
  PROTECTED
  PRIVATE
  PACKAGE_PRIVATE
}

"""
引用类型
"""
enum NopCodeReferenceKind {
  READ
  WRITE
  CALL
  TYPE_REFERENCE
  EXTENDS
  IMPLEMENTS
  ANNOTATES
  IMPORTS
  OVERRIDES
}

"""
层级查询方向
"""
enum NopCodeHierarchyDirection {
  SUPER
  SUB
  BOTH
}

"""
调用关系查询方向
"""
enum NopCodeCallDirection {
  INCOMING
  OUTGOING
  BOTH
}

# ========================================
# 核心实体类型
# ========================================

"""
代码索引 - 代表一个项目的代码索引
"""
type NopCodeIndex {
  id: ID!
  name: String!
  rootPath: String!
  language: String!
  symbolCount: Int!
  fileCount: Int!
  createTime: String
  updateTime: String
  status: String
  
  "索引中的所有文件"
  files(limit: Int = 100): [NopCodeFile!]!
  
  "按路径获取文件"
  file(filePath: String!): NopCodeFile
  
  "索引中的所有包"
  packages: [String!]!
}

"""
文件 - 代表一个源代码文件
"""
type NopCodeFile {
  filePath: String!
  packageName: String!
  language: String!
  lineCount: Int!
  lastModified: String
  indexId: String!
  
  "导入列表"
  imports: [String!]!
  
  "文件Outline（结构概览）"
  outline: NopCodeFileOutline!
  
  "文件中的所有符号"
  symbols: [NopCodeSymbol!]!
  
  "文件中的顶层类型（类、接口、枚举等）"
  types: [NopCodeType!]!
  
  "完整源代码"
  sourceCode: String!
  
  "指定行范围的源代码"
  sourceCodeRange(startLine: Int!, endLine: Int): String!
}

"""
文件Outline - 文件的结构概览
"""
type NopCodeFileOutline {
  filePath: String!
  packageName: String!
  imports: [String!]!
  lineCount: Int!
  
  "顶层类型概览"
  types: [NopCodeTypeOutline!]!
}

"""
类型Outline - 类型（类/接口/枚举）的概览
"""
type NopCodeTypeOutline {
  name: String!
  qualifiedName: String!
  kind: NopCodeSymbolKind!
  signature: String!
  documentation: String
  line: Int!
  endLine: Int!
}

# ========================================
# 符号类型（使用interface和union）
# ========================================

"""
符号接口 - 所有代码符号的通用属性
"""
interface NopCodeSymbol {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  
  "定义位置"
  location: NopCodeLocation!
  
  "访问修饰符"
  accessModifier: NopCodeAccessModifier
  
  "是否已废弃"
  deprecated: Boolean!
  
  "文档注释"
  documentation: String
  
  "注解"
  annotations: [NopCodeAnnotationUsage!]!
  
  "使用次数"
  usageCount: Int!
  
  "源代码片段"
  sourceCode(linesBefore: Int = 0, linesAfter: Int = 5): String!
  
  "此符号的引用位置"
  usages(limit: Int = 20): [NopCodeUsage!]!
}

"""
类型（类/接口/枚举/注解）的通用接口
"""
interface NopCodeType {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  
  "类型参数"
  typeParameters: [NopCodeTypeParameter!]!
  
  "Outline（结构概览）"
  outline: NopCodeClassOutline!
  
  "此类型的引用位置"
  usages(limit: Int = 20): [NopCodeUsage!]!
}

"""
类
"""
type NopCodeClass implements NopCodeSymbol & NopCodeType {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  typeParameters: [NopCodeTypeParameter!]!
  outline: NopCodeClassOutline!
  usages(limit: Int): [NopCodeUsage!]!
  
  "=== 类特有属性 ==="
  
  isAbstract: Boolean!
  isFinal: Boolean!
  
  "父类"
  superClass: NopCodeClass
  
  "所有父类（继承链）"
  superClasses: [NopCodeClass!]!
  
  "直接实现的接口"
  interfaces: [NopCodeInterface!]!
  
  "所有实现的接口"
  allInterfaces: [NopCodeInterface!]!
  
  "直接子类"
  subClasses(limit: Int = 50): [NopCodeClass!]!
  
  "所有子类"
  allSubClasses: [NopCodeClass!]!
  
  "字段"
  fields: [NopCodeField!]!
  
  "方法（不包括继承的）"
  methods: [NopCodeMethod!]!
  
  "所有方法（包括继承的）"
  allMethods: [NopCodeMethod!]!
  
  "构造器"
  constructors: [NopCodeConstructor!]!
  
  "内部类"
  innerClasses: [NopCodeClass!]!
  
  "继承层级"
  hierarchy(direction: NopCodeHierarchyDirection = BOTH, maxDepth: Int = 5): NopCodeTypeHierarchy!
}

"""
接口
"""
type NopCodeInterface implements NopCodeSymbol & NopCodeType {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  typeParameters: [NopCodeTypeParameter!]!
  outline: NopCodeClassOutline!
  usages(limit: Int): [NopCodeUsage!]!
  
  "=== 接口特有属性 ==="
  
  "继承的接口"
  extendsInterfaces: [NopCodeInterface!]!
  
  "所有父接口"
  allExtendsInterfaces: [NopCodeInterface!]!
  
  "实现此接口的类"
  implementors(limit: Int = 50): [NopCodeClass!]!
  
  "所有实现类"
  allImplementors: [NopCodeClass!]!
  
  "方法签名"
  methods: [NopCodeMethod!]!
}

"""
枚举
"""
type NopCodeEnum implements NopCodeSymbol & NopCodeType {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  typeParameters: [NopCodeTypeParameter!]!
  outline: NopCodeClassOutline!
  usages(limit: Int): [NopCodeUsage!]!
  
  "=== 枚举特有属性 ==="
  
  "枚举常量"
  constants: [NopCodeEnumConstant!]!
  
  "枚举方法"
  methods: [NopCodeMethod!]!
  
  "枚举字段"
  fields: [NopCodeField!]!
  
  "实现的接口"
  interfaces: [NopCodeInterface!]!
}

"""
注解类型
"""
type NopCodeAnnotationType implements NopCodeSymbol & NopCodeType {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  typeParameters: [NopCodeTypeParameter!]!
  outline: NopCodeClassOutline!
  usages(limit: Int): [NopCodeUsage!]!
  
  "=== 注解特有属性 ==="
  
  "注解属性"
  attributes: [NopCodeAnnotationAttribute!]!
  
  "是否可重复"
  isRepeatable: Boolean!
  
  "目标（可以用在哪里）"
  targets: [String!]!
  
  "保留策略"
  retention: String!
}

"""
方法
"""
type NopCodeMethod implements NopCodeSymbol {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  usages(limit: Int): [NopCodeUsage!]!
  
  "=== 方法特有属性 ==="
  
  "完整签名"
  signature: String!
  
  "返回类型"
  returnType: NopCodeTypeReference!
  
  "参数列表"
  parameters: [NopCodeParameter!]!
  
  "异常列表"
  exceptions: [NopCodeTypeReference!]!
  
  isStatic: Boolean!
  isAbstract: Boolean!
  isFinal: Boolean!
  isSynchronized: Boolean!
  isNative: Boolean!
  
  typeParameters: [NopCodeTypeParameter!]!
  
  "所属类"
  declaringClass: NopCodeClass!
  
  "重写的方法"
  overrides: [NopCodeMethod!]
  
  "被哪些方法重写"
  overriddenBy: [NopCodeMethod!]
  
  "=== 调用关系 ==="
  
  "调用此方法的方法（callers）"
  callers(maxDepth: Int = 1, limit: Int = 50): [NopCodeMethodCall!]!
  
  "此方法调用的方法（callees）"
  callees(maxDepth: Int = 1, limit: Int = 50): [NopCodeMethodCall!]!
  
  "调用层级"
  callHierarchy(direction: NopCodeCallDirection = BOTH, maxDepth: Int = 3): NopCodeCallHierarchy!
}

"""
构造器
"""
type NopCodeConstructor implements NopCodeSymbol {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  usages(limit: Int): [NopCodeUsage!]!
  
  signature: String!
  parameters: [NopCodeParameter!]!
  exceptions: [NopCodeTypeReference!]!
  declaringClass: NopCodeClass!
}

"""
字段
"""
type NopCodeField implements NopCodeSymbol {
  id: ID!
  kind: NopCodeSymbolKind!
  name: String!
  qualifiedName: String!
  filePath: String!
  indexId: String!
  location: NopCodeLocation!
  accessModifier: NopCodeAccessModifier
  deprecated: Boolean!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
  usageCount: Int!
  sourceCode(linesBefore: Int, linesAfter: Int): String!
  usages(limit: Int): [NopCodeUsage!]!
  
  "=== 字段特有属性 ==="
  
  type: NopCodeTypeReference!
  isStatic: Boolean!
  isFinal: Boolean!
  isVolatile: Boolean!
  isTransient: Boolean!
  defaultValue: String
  declaringClass: NopCodeClass!
}

# ========================================
# 辅助类型
# ========================================

"""
代码位置
"""
type NopCodeLocation {
  filePath: String!
  line: Int!
  column: Int
  endLine: Int
  endColumn: Int
  display: String!
}

"""
类型引用
"""
type NopCodeTypeReference {
  qualifiedName: String!
  isArray: Boolean!
  arrayDimensions: Int
  isGeneric: Boolean!
  typeArguments: [NopCodeTypeReference!]
  isWildcard: Boolean!
  wildcardBound: NopCodeTypeReference
  isTypeParameter: Boolean!
  
  "解析到的符号（如果已索引）"
  resolvedSymbol: NopCodeType
}

"""
类型参数
"""
type NopCodeTypeParameter {
  name: String!
  bounds: [NopCodeTypeReference!]!
}

"""
参数
"""
type NopCodeParameter {
  name: String!
  type: NopCodeTypeReference!
  annotations: [NopCodeAnnotationUsage!]!
  isVarArgs: Boolean!
}

"""
枚举常量
"""
type NopCodeEnumConstant {
  name: String!
  location: NopCodeLocation!
  documentation: String
  annotations: [NopCodeAnnotationUsage!]!
}

"""
注解属性定义
"""
type NopCodeAnnotationAttribute {
  name: String!
  type: NopCodeTypeReference!
  defaultValue: String
  documentation: String
}

"""
注解使用
"""
type NopCodeAnnotationUsage {
  annotationType: NopCodeAnnotationType!
  attributes: [NopCodeAnnotationAttributeValue!]!
  location: NopCodeLocation!
}

"""
注解属性值
"""
type NopCodeAnnotationAttributeValue {
  name: String!
  value: String!
  valueType: String!
}

# ========================================
# 类Outline类型
# ========================================

"""
类Outline - 类的完整结构概览
"""
type NopCodeClassOutline {
  className: String!
  packageName: String!
  superClassName: String
  interfaceNames: [String!]!
  classAnnotations: [String!]!
  lineCount: Int!
  fullSourceCode: String!
  
  fields: [NopCodeFieldOutline!]!
  methods: [NopCodeMethodOutline!]!
  constructors: [NopCodeConstructorOutline!]!
  innerClasses: [String!]!
}

type NopCodeFieldOutline {
  name: String!
  type: String!
  accessModifier: NopCodeAccessModifier!
  isStatic: Boolean!
  isFinal: Boolean!
  annotations: [String!]!
  documentation: String
  line: Int!
}

type NopCodeMethodOutline {
  name: String!
  signature: String!
  returnType: String!
  parameters: [NopCodeParameterOutline!]!
  accessModifier: NopCodeAccessModifier!
  isStatic: Boolean!
  isAbstract: Boolean!
  annotations: [String!]!
  exceptions: [String!]!
  documentation: String
  line: Int!
}

type NopCodeConstructorOutline {
  signature: String!
  parameters: [NopCodeParameterOutline!]!
  accessModifier: NopCodeAccessModifier!
  annotations: [String!]!
  exceptions: [String!]!
  documentation: String
  line: Int!
}

type NopCodeParameterOutline {
  name: String!
  type: String!
  annotations: [String!]!
}

# ========================================
# 引用和使用类型
# ========================================

"""
符号使用
"""
type NopCodeUsage {
  id: ID!
  location: NopCodeLocation!
  kind: NopCodeReferenceKind!
  symbol: NopCodeSymbol!
  context(before: Int = 2, after: Int = 2): String!
  filePath: String!
  indexId: String!
  
  "使用此符号的方法"
  enclosingMethod: NopCodeMethod
  
  "使用此符号的类"
  enclosingClass: NopCodeClass
}

"""
方法调用
"""
type NopCodeMethodCall {
  method: NopCodeMethod!
  location: NopCodeLocation!
  caller: NopCodeMethod!
  callType: String!
  context(before: Int = 2, after: Int = 2): String!
}

# ========================================
# 层级类型
# ========================================

"""
类型层级
"""
type NopCodeTypeHierarchy {
  root: NopCodeTypeHierarchyNode!
  direction: NopCodeHierarchyDirection!
  maxDepth: Int!
}

type NopCodeTypeHierarchyNode {
  symbol: NopCodeClass!
  superClass: NopCodeTypeHierarchyNode
  interfaces: [NopCodeTypeHierarchyNode!]!
  subTypes: [NopCodeTypeHierarchyNode!]!
  depth: Int!
}

"""
调用层级
"""
type NopCodeCallHierarchy {
  root: NopCodeCallHierarchyNode!
  direction: NopCodeCallDirection!
  maxDepth: Int!
}

type NopCodeCallHierarchyNode {
  symbol: NopCodeMethod!
  callers: [NopCodeCallHierarchyNode!]!
  callees: [NopCodeCallHierarchyNode!]!
  depth: Int!
}

# ========================================
# 分页类型
# ========================================

type PageBean_NopCodeSymbol {
  totalCount: Int!
  offset: Int!
  limit: Int!
  items: [NopCodeSymbol!]!
}

type PageBean_NopCodeClass {
  totalCount: Int!
  offset: Int!
  limit: Int!
  items: [NopCodeClass!]!
}

type PageBean_NopCodeMethod {
  totalCount: Int!
  offset: Int!
  limit: Int!
  items: [NopCodeMethod!]!
}

type PageBean_NopCodeUsage {
  totalCount: Int!
  offset: Int!
  limit: Int!
  items: [NopCodeUsage!]!
}

type PageBean_NopCodeFile {
  totalCount: Int!
  offset: Int!
  limit: Int!
  items: [NopCodeFile!]!
}

# ========================================
# Query - 精简的入口点
# ========================================

type Query {
  # ========== 索引 ==========
  
  "获取代码索引"
  NopCodeIndex__get(id: ID!): NopCodeIndex
  
  "通过路径获取索引"
  NopCodeIndex__findByPath(rootPath: String!): NopCodeIndex
  
  "所有索引"
  NopCodeIndex__findList: [NopCodeIndex!]!
  
  # ========== 符号 ==========
  
  "通过ID获取任意符号"
  NopCodeSymbol__get(id: ID!): NopCodeSymbol
  
  "通过全限定名获取符号"
  NopCodeSymbol__findByQualifiedName(qualifiedName: String!, indexId: String): NopCodeSymbol
  
  "搜索符号（分页）"
  NopCodeSymbol__findPage(
    query: String
    kinds: [NopCodeSymbolKind!]
    packageName: String
    accessModifier: NopCodeAccessModifier
    indexId: String
    offset: Int = 0
    limit: Int = 20
  ): PageBean_NopCodeSymbol!
  
  "批量获取符号"
  NopCodeSymbol__batchGet(ids: [ID!]!): [NopCodeSymbol]!
  
  # ========== 类型（类/接口/枚举/注解）==========
  
  "通过ID获取类型"
  NopCodeType__get(id: ID!): NopCodeType
  
  "通过全限定名获取类型"
  NopCodeType__findByQualifiedName(qualifiedName: String!, indexId: String): NopCodeType
  
  "搜索类型（分页）"
  NopCodeType__findPage(
    query: String
    kind: NopCodeSymbolKind
    packageName: String
    superClassName: String
    interfaceName: String
    hasAnnotation: String
    indexId: String
    offset: Int = 0
    limit: Int = 20
  ): PageBean_NopCodeClass!
  
  "批量获取类型的Outline"
  NopCodeType__batchGetOutlines(qualifiedNames: [String!]!, indexId: String): [NopCodeClassOutline!]!
  
  # ========== 文件 ==========
  
  "获取文件（包含outline、symbols等嵌套字段）"
  NopCodeFile__get(filePath: String!, indexId: String!): NopCodeFile
  
  "搜索文件"
  NopCodeFile__findPage(
    pathPattern: String
    packageName: String
    indexId: String
    offset: Int = 0
    limit: Int = 20
  ): PageBean_NopCodeFile!
  
  # ========== 注解 ==========
  
  "获取带有指定注解的符号"
  NopCodeSymbol__findByAnnotation(
    annotationName: String!
    symbolKind: NopCodeSymbolKind
    indexId: String
    limit: Int = 50
  ): [NopCodeSymbol!]!
  
  # ========== 层级查询 ==========
  
  "获取类型层级"
  NopCodeTypeHierarchy__get(
    typeId: ID!
    direction: NopCodeHierarchyDirection = BOTH
    maxDepth: Int = 5
  ): NopCodeTypeHierarchy!
  
  "获取调用层级"
  NopCodeCallHierarchy__get(
    methodId: ID!
    direction: NopCodeCallDirection = BOTH
    maxDepth: Int = 3
  ): NopCodeCallHierarchy!
}

# ========================================
# Mutation
# ========================================

type Mutation {
  "创建索引"
  NopCodeIndex__create(name: String!, rootPath: String!, language: String): NopCodeIndex!
  
  "索引文件"
  NopCodeIndex__indexFiles(filePaths: [String!]!, indexId: ID!): Boolean
  
  "索引目录"
  NopCodeIndex__indexDirectory(
    directoryPath: String!
    indexId: ID!
    recursive: Boolean = true
    filePattern: String = "**/*.java"
  ): Boolean
  
  "重新索引"
  NopCodeIndex__reindex(indexId: ID!): Boolean
  
  "增量更新"
  NopCodeIndex__incrementalUpdate(indexId: ID!): Boolean
  
  "删除索引"
  NopCodeIndex__delete(indexId: ID!): Boolean
}
```

---

## 二、GraphQL风格说明

### 2.1 核心设计原则

**正确的GraphQL设计：**
- **一个入口，丰富对象**：`NopCodeFile__get` 返回 `NopCodeFile` 对象
- **客户端选择字段**：不需要的数据不查询
- **嵌套访问关联**：通过对象字段访问关联数据

```graphql
# ✅ GraphQL风格：客户端按需选择
query GetFileInfo {
  NopCodeFile__get(filePath: "path/to/File.java", indexId: "main") {
    packageName
    outline {
      types {
        name
        kind
      }
    }
    symbols {
      name
      kind
    }
  }
}

# ❌ REST风格：每个资源一个endpoint
query GetFileInfo {
  NopCodeFile__get(filePath: "path/to/File.java") { packageName }
  NopCodeFile__getOutline(filePath: "path/to/File.java") { ... }
  NopCodeFile__getSymbols(filePath: "path/to/File.java") { ... }
}
```

### 2.2 利用嵌套字段访问关联

```graphql
# 获取类，并通过嵌套字段访问其所有关联信息
query GetClassWithAllRelations {
  NopCodeType__findByQualifiedName(qualifiedName: "io.nop.biz.crud.CrudBizModel") {
    name
    qualifiedName
    documentation
    
    # 嵌套：父类
    superClass {
      name
      qualifiedName
    }
    
    # 嵌套：接口
    interfaces {
      name
      qualifiedName
    }
    
    # 嵌套：所有方法
    methods {
      name
      signature
      returnType {
        qualifiedName
      }
      documentation
    }
    
    # 嵌套：字段
    fields {
      name
      type {
        qualifiedName
      }
    }
    
    # 嵌套：子类
    subClasses(limit: 10) {
      name
      qualifiedName
    }
    
    # 嵌套：Outline
    outline {
      className
      superClassName
      methods {
        name
        signature
      }
    }
  }
}
```

### 2.3 深度嵌套查询

```graphql
# 分析调用链：从方法 -> 调用者 -> 调用者的类 -> 类的父类
query AnalyzeCallChainDeep {
  NopCodeSymbol__findByQualifiedName(qualifiedName: "io.nop.biz.crud.CrudBizModel.save") {
    ... on NopCodeMethod {
      name
      signature
      
      # 调用此方法的方法
      callers(maxDepth: 2) {
        caller {
          name
          qualifiedName
          
          # 调用者的所属类
          declaringClass {
            name
            qualifiedName
            
            # 类的父类
            superClass {
              name
              qualifiedName
            }
          }
        }
        location {
          filePath
          line
        }
      }
    }
  }
}
```

---

## 三、典型使用场景

### 场景1：AI了解类的完整结构

```graphql
query GetClassOutline {
  NopCodeType__findByQualifiedName(qualifiedName: "io.nop.biz.crud.CrudBizModel") {
    ... on NopCodeClass {
      outline {
        className
        packageName
        superClassName
        interfaceNames
        
        fields {
          name
          type
          accessModifier
          documentation
        }
        
        methods {
          name
          signature
          returnType
          parameters { name type }
          documentation
          annotations
        }
      }
    }
  }
}
```

### 场景2：AI查找符号使用位置

```graphql
query FindUsages {
  NopCodeSymbol__findByQualifiedName(
    qualifiedName: "io.nop.api.core.annotations.biz.BizQuery"
  ) {
    id
    name
    
    # 直接访问 usages 字段
    usages(limit: 30) {
      location {
        filePath
        line
        display
      }
      kind
      context(before: 2, after: 2)
      
      enclosingMethod {
        name
        qualifiedName
      }
      
      enclosingClass {
        name
        qualifiedName
      }
    }
  }
}
```

### 场景3：AI分析方法调用关系

```graphql
query AnalyzeMethodCalls {
  NopCodeSymbol__findByQualifiedName(
    qualifiedName: "io.nop.biz.crud.CrudBizModel.save"
  ) {
    ... on NopCodeMethod {
      name
      signature
      
      # 谁调用了这个方法
      callers(maxDepth: 1, limit: 20) {
        caller {
          name
          qualifiedName
          declaringClass {
            name
            qualifiedName
          }
        }
        location {
          filePath
          line
        }
        context(before: 2, after: 2)
      }
      
      # 这个方法调用了谁
      callees(maxDepth: 1, limit: 20) {
        method {
          name
          qualifiedName
          declaringClass {
            name
            qualifiedName
          }
        }
        location {
          filePath
          line
        }
      }
    }
  }
}
```

### 场景4：AI分析继承关系

```graphql
query AnalyzeInheritance {
  NopCodeTypeHierarchy__get(
    typeId: "class_id"
    direction: BOTH
    maxDepth: 3
  ) {
    root {
      symbol {
        name
        qualifiedName
      }
      
      superClass {
        symbol { name qualifiedName }
        superClass {
          symbol { name qualifiedName }
        }
      }
      
      interfaces {
        symbol { name qualifiedName }
      }
      
      subTypes {
        symbol { name qualifiedName }
        subTypes {
          symbol { name qualifiedName }
        }
      }
    }
  }
}
```

### 场景5：AI批量获取多个类信息

```graphql
query BatchGetOutlines {
  NopCodeType__batchGetOutlines(
    qualifiedNames: [
      "io.nop.biz.crud.CrudBizModel",
      "io.nop.api.core.annotations.biz.BizModel",
      "io.nop.api.core.annotations.biz.BizQuery"
    ]
  ) {
    className
    packageName
    superClassName
    interfaceNames
    methods {
      name
      signature
      returnType
      documentation
    }
  }
}
```

### 场景6：AI获取文件结构

```graphql
query GetFileStructure {
  NopCodeFile__get(
    filePath: "nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java"
    indexId: "main"
  ) {
    packageName
    imports
    
    outline {
      types {
        name
        qualifiedName
        kind
        signature
        line
      }
    }
    
    types {
      name
      ... on NopCodeClass {
        superClass { name }
        interfaces { name }
        methods { name signature }
      }
    }
  }
}
```

---

## 四、Java BizModel 实现示例

```java
@BizModel("NopCodeFile")
public class NopCodeFileBizModel {

    @Inject
    private INopCodeIndexService indexService;

    /**
     * 获取文件信息
     * GraphQL客户端可以选择需要的字段：outline, symbols, types, sourceCode等
     */
    @BizQuery
    @GraphQLReturn(bizObjName = "NopCodeFile")
    public NopCodeFile get(
            @Name("filePath") String filePath,
            @Name("indexId") String indexId) {
        return indexService.getCodeFile(filePath, indexId);
    }
}

@BizModel("NopCodeType")
public class NopCodeTypeBizModel {

    @Inject
    private INopCodeIndexService indexService;

    @BizQuery
    @GraphQLReturn(bizObjName = "NopCodeClass")
    public NopCodeType get(@Name("id") String id) {
        return indexService.getTypeById(id);
    }

    @BizQuery
    @GraphQLReturn(bizObjName = "NopCodeClass")
    public NopCodeType findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        return indexService.findTypeByQualifiedName(qualifiedName, indexId);
    }

    @BizQuery
    public List<NopCodeClassOutline> batchGetOutlines(
            @Name("qualifiedNames") List<String> qualifiedNames,
            @Name("indexId") String indexId) {
        return indexService.batchGetClassOutlines(qualifiedNames, indexId);
    }
}

/**
 * NopCodeFile实体类 - 通过@BizLoader加载关联字段
 */
@BizModel("NopCodeFile")
public class NopCodeFile {

    private String filePath;
    private String packageName;
    private String indexId;
    
    // 基本属性直接返回
    public String getFilePath() { return filePath; }
    public String getPackageName() { return packageName; }
    
    // 关联字段通过BizLoader按需加载
    @BizLoader
    @GraphQLReturn(bizObjName = "NopCodeFileOutline")
    public NopCodeFileOutline outline(@ContextSource NopCodeFile file) {
        return indexService.getFileOutline(file.getFilePath(), file.getIndexId());
    }

    @BizLoader
    @GraphQLReturn(bizObjName = "NopCodeSymbol")
    public List<NopCodeSymbol> symbols(@ContextSource NopCodeFile file) {
        return indexService.getFileSymbols(file.getFilePath(), file.getIndexId());
    }

    @BizLoader
    @GraphQLReturn(bizObjName = "NopCodeClass")
    public List<NopCodeType> types(@ContextSource NopCodeFile file) {
        return indexService.getFileTypes(file.getFilePath(), file.getIndexId());
    }

    @BizLoader
    public String sourceCode(@ContextSource NopCodeFile file) {
        return indexService.getFileSourceCode(file.getFilePath(), file.getIndexId());
    }
}
```

---

## 五、类型命名规范

### 5.1 统一前缀

所有GraphQL类型使用 `NopCode` 前缀：

| 类型 | 命名 |
|------|------|
| 索引 | `NopCodeIndex` |
| 文件 | `NopCodeFile` |
| 符号接口 | `NopCodeSymbol` |
| 类型接口 | `NopCodeType` |
| 类 | `NopCodeClass` |
| 接口 | `NopCodeInterface` |
| 枚举 | `NopCodeEnum` |
| 方法 | `NopCodeMethod` |
| 字段 | `NopCodeField` |
| 构造器 | `NopCodeConstructor` |
| 注解类型 | `NopCodeAnnotationType` |
| 引用 | `NopCodeUsage` |
| 调用 | `NopCodeMethodCall` |
| 位置 | `NopCodeLocation` |
| 类型引用 | `NopCodeTypeReference` |
| Outline | `NopCodeClassOutline` |
| 层级 | `NopCodeTypeHierarchy` |

### 5.2 枚举命名

| 枚举 | 命名 |
|------|------|
| 符号类型 | `NopCodeSymbolKind` |
| 访问修饰符 | `NopCodeAccessModifier` |
| 引用类型 | `NopCodeReferenceKind` |
| 层级方向 | `NopCodeHierarchyDirection` |
| 调用方向 | `NopCodeCallDirection` |

### 5.3 分页类型命名

遵循Nop规范：`PageBean_{TypeName}`

| 分页类型 | 命名 |
|----------|------|
| 符号分页 | `PageBean_NopCodeSymbol` |
| 类分页 | `PageBean_NopCodeClass` |
| 方法分页 | `PageBean_NopCodeMethod` |
| 引用分页 | `PageBean_NopCodeUsage` |
| 文件分页 | `PageBean_NopCodeFile` |

### 5.4 Query命名

遵循Nop规范：`{BizObjName}__{Action}`

| Query | 说明 |
|-------|------|
| `NopCodeIndex__get` | 获取索引 |
| `NopCodeSymbol__findByQualifiedName` | 按全限定名查找符号 |
| `NopCodeType__findPage` | 分页搜索类型 |
| `NopCodeFile__get` | 获取文件 |
| `NopCodeTypeHierarchy__get` | 获取类型层级 |

---

## 六、总结

### GraphQL vs REST 风格对比

| 方面 | REST风格（错误） | GraphQL风格（正确） |
|------|-----------------|-------------------|
| 文件Outline | `NopCodeFile__getOutline()` | `NopCodeFile { outline { ... } }` |
| 文件符号 | `NopCodeFile__getSymbols()` | `NopCodeFile { symbols { ... } }` |
| 类方法 | `NopCodeClass__getMethods()` | `NopCodeClass { methods { ... } }` |
| 方法调用者 | `NopCodeMethod__callers()` | `NopCodeMethod { callers { ... } }` |
| 符号引用 | `NopCodeUsage__findPage()` | `NopCodeSymbol { usages { ... } }` |

### 核心Query入口

- `NopCodeIndex__get/findByPath` → 返回 NopCodeIndex
- `NopCodeSymbol__get/findByQualifiedName` → 返回 NopCodeSymbol
- `NopCodeType__get/findByQualifiedName` → 返回 NopCodeType
- `NopCodeFile__get` → 返回 NopCodeFile

### 设计要点

1. **返回丰富对象**：Query只返回实体，不返回"切片"
2. **嵌套访问关联**：通过字段访问关联数据
3. **客户端选择**：不需要的字段不查询
4. **使用BizLoader**：关联字段按需加载，避免N+1
5. **统一前缀**：所有类型使用 `NopCode` 前缀
