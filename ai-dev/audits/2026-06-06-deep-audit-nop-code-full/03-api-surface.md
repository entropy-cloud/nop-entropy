# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] 所有 I*Biz 接口均为空壳，未声明任何自定义方法

- **文件**: `nop-code-dao/src/main/java/io/nop/code/biz/INopCode*Biz.java`（全部 11 个）
- **证据片段**:
  ```java
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex> { }
  // NopCodeIndexBizModel 有 23 个自定义方法，但接口声明 0 个
  ```
- **严重程度**: P2
- **现状**: 3 个有自定义方法的 BizModel（共 46 个自定义方法）对应的 I*Biz 接口未声明任何方法。
- **风险**: 外部模块无法通过接口类型发现可用操作，无静态类型约束。
- **建议**: 在 I*Biz 接口中补充自定义方法声明。
- **信心水平**: 确定
- **误报排除**: I*Biz 放在 dao 模块是代码生成契约，但空壳接口无法提供类型约束。
- **复核状态**: 未复核

### [维度03-02] NopCodeFileBizModel 多个方法直接返回核心模型类型而非 DTO

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java`
- **证据片段**:
  ```java
  public CodeFileAnalysisResult getByPath(...)  // 应返回 FileAnalysisDTO
  public List<CodeSymbol> symbols(...)          // 应返回 List<SymbolDTO>
  ```
- **严重程度**: P2
- **现状**: 4 个方法返回核心模型类（CodeFileAnalysisResult、CodeSymbol），暴露了 sourceCode、calls 等内部字段。转换方法已存在但未使用。
- **建议**: 使用已有的 FileAnalysisDTO.fromCodeFileAnalysisResult() 和 SymbolDTO.fromCodeSymbol() 转换。
- **信心水平**: 确定
- **误报排除**: 转换方法已存在，只是未被调用。
- **复核状态**: 未复核

### [维度03-03] 流分析方法返回 nop-code-flow 内部类型

- **文件**: `NopCodeIndexBizModel.java` (detectFlows, listFlows, getFlow, getAffectedFlows, analyzeChanges)
- **严重程度**: P2
- **现状**: 5 个方法直接返回 ExecutionFlow、ChangeAnalysisResult 等 nop-code-flow 内部类型。
- **建议**: 在 nop-code-api 中创建对应的 DTO。
- **信心水平**: 确定
- **误报排除**: 这些类型含内部字段（pathNodeIds 等）不应暴露。
- **复核状态**: 未复核

### [维度03-04] detectDeadCode 返回 nop-code-flow 内部类型 DeadCodeReport

- **文件**: `NopCodeSymbolBizModel.java:222-224`
- **严重程度**: P2
- **现状**: DeadCodeReport 含嵌套类 DeadCodeEntry、DeadCodeStats。
- **建议**: 创建 DeadCodeReportDTO。
- **信心水平**: 确定
- **误报排除**: 同 03-03。
- **复核状态**: 未复核

### [维度03-05] xmeta 不覆盖自定义 BizModel 方法的参数/返回类型

- **文件**: 所有 xmeta delta 文件
- **严重程度**: P2
- **现状**: 3 个主要 BizModel 的 46 个自定义方法均无对应 xmeta 元数据。无字段级验证和权限控制。
- **建议**: 为关键 Action 添加 xmeta 操作定义。
- **信心水平**: 确定
- **误报排除**: Nop 平台 xmeta 应覆盖公开方法。
- **复核状态**: 未复核

### [维度03-06] getIncrementalStatus 无测试覆盖且为死 API

- **文件**: `NopCodeIndexBizModel.java:84-88`
- **严重程度**: P3
- **现状**: 无外部调用者，无测试，返回内存状态重启即丢。
- **建议**: 修复 evict 策略并添加测试，或标记 @Deprecated。
- **信心水平**: 确定
- **误报排除**: grep 确认无外部调用。
- **复核状态**: 未复核

### [维度03-07] 7 个实体的 xmeta delta 文件为空壳

- **文件**: NopCodeCall, NopCodeDependency, NopCodeUsage, NopCodeInheritance, NopCodeAnnotationUsage, NopCodeSemanticEdge, NopCodeFlow 的 xmeta delta
- **严重程度**: P3
- **现状**: 仅含 `<props/>`，所有字段使用默认权限。
- **建议**: 审查并调整字段权限。
- **信心水平**: 确定
- **误报排除**: 默认权限可能过于宽松。
- **复核状态**: 未复核

### [维度03-08] SymbolDTO 缺少 usageCount 字段

- **文件**: `nop-code-service/.../dto/SymbolDTO.java` vs `NopCodeSymbol.xmeta`
- **严重程度**: P3
- **现状**: ORM 实体和 xmeta 有 usageCount，但 DTO 缺失映射。
- **建议**: 添加 usageCount 到 SymbolDTO。
- **信心水平**: 确定
- **误报排除**: 确认字段在 ORM/xmeta 中存在但 DTO 中缺失。
- **复核状态**: 未复核
