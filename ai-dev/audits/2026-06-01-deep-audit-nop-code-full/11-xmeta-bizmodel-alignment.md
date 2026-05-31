# 维度 11：XMeta 与 BizModel 对齐 + 维度 19：命名一致性 + 维度 20：跨模块契约

## 第 1 轮（初审）

### [维度19-01] "Usage" 一词在模块内承载多种语义

- **文件**: `NopCodeUsage` vs `NopCodeAnnotationUsage` vs `ICodeIndexService.getSymbolUsages()` vs `NopCodeSymbolBizModel.usages()` BizLoader
- **证据片段**:
  - NopCodeUsage = 符号引用关系
  - NopCodeAnnotationUsage = 注解应用到符号
  - getSymbolUsages() 返回 CodeAnnotationUsage
  - BizLoader usages() 返回 AnnotationUsageDTO
  - xmeta prop usages 映射到 NopCodeUsage
- **严重程度**: P1
- **现状**: 同一词在 ORM/xmeta/Java 方法中指向不同概念，极易混淆。
- **建议**: getSymbolUsages→getSymbolAnnotations，BizLoader usages→annotations。
- **信心水平**: 确定
- **误报排除**: 这是一个真实的命名歧义问题，影响 API 使用者理解。
- **复核状态**: 未复核

### [维度20-01] nop-code-api 子模块为空，API 契约无独立模块边界

- **文件**: `nop-code-api/`（无 Java 文件）
- **证据片段**: 所有 DTO 和 ICodeIndexService 定义在 nop-code-service 中。
- **严重程度**: P2
- **现状**: 外部模块需依赖 nop-code-service 才能使用 DTO。API 契约与实现耦合。
- **建议**: 将 DTO 和接口移入 nop-code-api。
- **信心水平**: 确定
- **误报排除**: nop-code 可能仅内部使用，但模块边界是架构基本要求。
- **复核状态**: 未复核

### [维度11-01] NopCodeSymbol BizLoader usages 与 xmeta prop usages 语义冲突

- **文件**: `NopCodeSymbolBizModel.java:98-109`, `_NopCodeSymbol.xmeta:190-194`
- **证据片段**: BizLoader usages() 返回 AnnotationUsageDTO（注解），xmeta usages 映射到 NopCodeUsage（引用）。
- **严重程度**: P2
- **现状**: 同名 prop 在不同层代表不同概念。
- **建议**: BizLoader 改名为 annotations 或 annotationUsages。
- **信心水平**: 确定
- **误报排除**: 与 19-01 关联。
- **复核状态**: 未复核

### [维度19-02] NopCodeCall ORM refDisplayName 反向标注有误

- **文件**: `nop-code/model/nop-code.orm.xml:506-523`
- **证据片段**:
  ```xml
  <to-one name="caller" refDisplayName="被调用者" ...>
  <to-one name="callee" refDisplayName="调用者" ...>
  ```
  caller 是调用方不是被调用者，callee 是被调用方不是调用者。
- **严重程度**: P2
- **现状**: refDisplayName 标注与实际语义相反。
- **建议**: 修正为 caller→"调用者"，callee→"被调用者"。
- **信心水平**: 确定
- **误报排除**: 这是明确的标注错误。
- **复核状态**: 未复核

### [维度19-03] 布尔字段命名模式层级间不一致

- **文件**: ORM: isAbstract/isFinal vs CodeSymbol: abstractFlag/finalFlag vs CodeSymbol: deprecated
- **严重程度**: P3
- **现状**: ORM 用 is- 前缀，核心模型用 Flag 后缀，deprecated 例外。
- **建议**: 统一为一种模式。
- **信心水平**: 确定
- **误报排除**: 这是跨层命名约定不一致。
- **复核状态**: 未复核

### [维度19-04] 审计时间字段命名 CREATE_TIME vs CREATED_TIME 不一致

- **文件**: `nop-code.orm.xml`（NopCodeSemanticEdge vs NopCodeFlow/NopCodeFlowMembership）
- **严重程度**: P2
- **现状**: 同义字段使用不同列名和属性名。
- **建议**: 统一为 CREATED_TIME/createdTime。
- **信心水平**: 确定
- **误报排除**: 与维度04-02 关联。
- **复核状态**: 未复核

### [维度20-02] ICodeIndexService 返回类型混合核心模型/DTO/外部模块类型

- **文件**: `ICodeIndexService.java`（177 行）
- **严重程度**: P2
- **现状**: 方法返回 CodeSymbol（core）、ExecutionFlow（flow）、SymbolDTO（service DTO）等多种类型。
- **建议**: 统一使用 DTO 作为接口返回类型。
- **信心水平**: 很可能
- **误报排除**: 内部服务接口返回 core 层模型在平台中是合理的，但 ICodeIndexService 是主要 API 契约。
- **复核状态**: 未复核
