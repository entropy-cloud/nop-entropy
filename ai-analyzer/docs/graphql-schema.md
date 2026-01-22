# GraphQL数据模型设计

## 核心数据模型

### 1. 项目级别 (Project)
```graphql
type Project {
  id: ID!
  name: String!
  path: String!
  modules: [Module!]!
  dependencies: [Dependency!]!
  createdAt: DateTime!
  updatedAt: DateTime!
}

type Module {
  id: ID!
  name: String!
  path: String!
  package: String!
  classes: [Class!]!
  interfaces: [Interface!]!
  dependencies: [Dependency!]!
  description: String
  semanticSummary: SemanticSummary
}
```

### 2. 代码结构级别 (Class/Interface)
```graphql
type Class {
  id: ID!
  name: String!
  package: String!
  fullName: String!
  modifiers: [String!]!
  extends: String
  implements: [String!]!
  annotations: [Annotation!]!
  fields: [Field!]!
  methods: [Method!]!
  constructors: [Constructor!]!
  innerClasses: [Class!]!
  
  # AI增强字段
  semanticRole: SemanticRole!
  responsibility: String!
  designPatterns: [DesignPattern!]!
  complexity: ComplexityLevel!
  usageExamples: [UsageExample!]!
  relationships: ClassRelationships!
}

type Interface {
  id: ID!
  name: String!
  package: String!
  fullName: String!
  extends: [String!]!
  methods: [MethodSignature!]!
  annotations: [Annotation!]!
  
  # AI增强字段
  contractPurpose: String!
  implementationCount: Int!
  usageContexts: [String!]!
}
```

### 3. 方法级别 (Method)
```graphql
type Method {
  id: ID!
  name: String!
  returnType: String!
  parameters: [Parameter!]!
  modifiers: [String!]!
  annotations: [Annotation!]!
  exceptions: [String!]!
  
  # AI增强字段
  semanticFunction: String!
  businessLogic: String
  technicalPurpose: String
  complexity: ComplexityLevel!
  callChain: CallChain!
  dataFlow: DataFlow!
  usagePatterns: [UsagePattern!]!
  testExamples: [TestExample!]!
  performanceCharacteristics: PerformanceInfo
}

type Parameter {
  name: String!
  type: String!
  annotations: [Annotation!]!
  semanticRole: String
  validationRules: [String!]
}
```

### 4. 语义关系级别 (Semantic)
```graphql
type SemanticSummary {
  id: ID!
  entityType: EntityType!
  entityId: ID!
  summary: String!
  keyConcepts: [String!]!
  relatedPatterns: [String!]!
  commonUsage: String
  bestPractices: [String!]
  potentialIssues: [String!]
  improvementSuggestions: [String!]
}

type ClassRelationships {
  inheritance: [InheritanceRelation!]!
  composition: [CompositionRelation!]!
  aggregation: [AggregationRelation!]!
  dependency: [DependencyRelation!]!
  association: [AssociationRelation!]!
  implementation: [ImplementationRelation!]!
}

type CallChain {
  callers: [MethodCall!]!
  callees: [MethodCall!]!
  recursive: Boolean!
  depth: Int!
  criticalPath: Boolean!
}

type DataFlow {
  inputSources: [DataInput!]!
  outputDestinations: [DataOutput!]!
  transformations: [Transformation!]!
  sideEffects: [SideEffect!]!
}
```

### 5. 查询接口设计
```graphql
type Query {
  # 项目级别查询
  projects: [Project!]!
  project(id: ID!): Project
  
  # 模块级别查询
  modules(projectId: ID!): [Module!]!
  module(id: ID!): Module
  
  # 类级别查询
  classes(moduleId: ID, name: String, package: String): [Class!]!
  class(id: ID!): Class
  
  # 方法级别查询
  methods(classId: ID, name: String): [Method!]!
  method(id: ID!): Method
  
  # 语义搜索
  semanticSearch(query: String!, entityType: EntityType, limit: Int): [SemanticResult!]!
  
  # 关系查询
  relationships(sourceId: ID!, relationshipType: RelationshipType): [Relationship!]!
  callGraph(methodId: ID!, depth: Int): CallGraph!
  dependencyGraph(entityId: ID!): DependencyGraph!
  
  # 模式识别
  findDesignPatterns(patternType: DesignPatternType): [PatternInstance!]!
  findUsagePatterns(pattern: String): [UsagePattern!]!
  
  # 代码示例
  getUsageExamples(entityId: ID!): [UsageExample!]!
  getBestPractices(entityType: EntityType): [BestPractice!]!
}

# 复杂查询输入类型
input SearchFilter {
  entityTypes: [EntityType!]
  packages: [String!]
  modifiers: [String!]
  complexity: ComplexityLevel
  patterns: [String!]
  keywords: [String!]
}

input SemanticSearchInput {
  query: String!
  filters: SearchFilter
  similarityThreshold: Float
  maxResults: Int
}

# 订阅接口
type Subscription {
  analysisProgress(projectId: ID!): AnalysisProgress!
  indexingStatus: IndexingStatus!
}

# 变更接口
type Mutation {
  analyzeProject(path: String!): Project!
  rebuildIndex(projectId: ID!): Boolean!
  updateSemanticData(entityId: ID!, data: SemanticUpdateInput!): Boolean!
}
```

### 6. 枚举和标量类型
```graphql
enum EntityType {
  PROJECT
  MODULE
  CLASS
  INTERFACE
  METHOD
  FIELD
  PACKAGE
}

enum RelationshipType {
  INHERITANCE
  IMPLEMENTATION
  COMPOSITION
  AGGREGATION
  DEPENDENCY
  ASSOCIATION
  CALL
  REFERENCE
}

enum ComplexityLevel {
  SIMPLE
  MODERATE
  COMPLEX
  VERY_COMPLEX
}

enum DesignPatternType {
  SINGLETON
  FACTORY
  STRATEGY
  OBSERVER
  DECORATOR
  ADAPTER
  # ... 更多设计模式
}

scalar DateTime
scalar JSON
```

## 数据模型特点

### 丰富性
- **多层次结构**：项目→模块→类→方法→字段的完整层次
- **语义增强**：每个实体都包含AI生成的语义信息
- **关系网络**：完整的调用链、依赖关系、数据流

### 灵活性
- **GraphQL查询**：客户端可以精确指定需要的数据字段
- **组合查询**：支持复杂的关系和模式查询
- **实时订阅**：支持分析进度和索引状态的实时更新

### 扩展性
- **插件式设计**：支持新的分析维度和语义类型
- **版本控制**：支持不同版本的分析结果对比
- **多项目支持**：可以同时分析多个相关项目