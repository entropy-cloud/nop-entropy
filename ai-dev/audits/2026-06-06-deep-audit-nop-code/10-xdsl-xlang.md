# 维度 10+11+15：XDSL/XMeta/类型安全

## 维度 10：XDSL 与 XLang 正确性

### [维度10-01] NopCodeSemanticEdge.relationType 引用错误数据字典

- **文件**: `nop-code/model/nop-code.orm.xml:916`
- **严重程度**: P2
- **现状**: ext:dict="code/relation_type" 仅有 EXTENDS/IMPLEMENTS，但实际存储 SemanticRelationType 枚举8个值。
- **建议**: 新建 code/semantic_relation_type 字典。
- **复核状态**: 未复核（同维度04-01）

### [维度10-02] NopCodeSemanticEdge 缺少 i18n-en:displayName

- **严重程度**: P3（同维度04-05）

### [维度10-03] _dao.beans.xml 为空文件

- **严重程度**: P3
- **现状**: 空的 beans 文件作为占位符，功能正常。
- **复核状态**: 未复核

## 维度 11：XMeta 与 BizModel 对齐

### [维度11-01] NopCodeIndexBizModel 23个自定义操作在 xmeta 中无声明

- **严重程度**: P3
- **现状**: xmeta delta 中 props 为空，自定义 BizQuery/Mutation 缺少元数据约束。
- **建议**: 为关键操作添加 props 声明。
- **复核状态**: 未复核

### [维度11-02] NopCodeSymbolBizModel 的 kinds/indexId 参数在 xmeta 中无 prop

- **严重程度**: P3
- **复核状态**: 未复核

### [维度11-03] NopCodeFileBizModel 返回 CodeFileAnalysisResult 而非实体

- **严重程度**: P3
- **现状**: xmeta 权限控制无法约束 BizLoader 返回的属性。
- **复核状态**: 未复核

## 维度 15：类型安全与泛型使用

### [维度15-01] 28个源文件使用 java.util.* 通配符导入

- **严重程度**: P2
- **现状**: 影响代码可读性，无法直观判断使用了哪些具体类型。
- **建议**: 替换为具体 import。
- **复核状态**: 未复核

### [维度15-02] SpringEventSynthesizer 行102 未经 @SuppressWarnings 保护的不安全转换

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/heuristic/SpringEventSynthesizer.java:102`
- **严重程度**: P2
- **现状**: `(Map<String, Object>) first` 转换缺少 instanceof 检查和 @SuppressWarnings。
- **建议**: 添加 instanceof 检查。
- **复核状态**: 未复核

### [维度15-03] CodeIndexService.getIndexStats 使用 Map<String, Object> 接收 DAO 结果

- **严重程度**: P2（信息性）
- **现状**: Nop DAO 框架 API 限制，selectFieldsByQuery 返回 List<Map<String, Object>>。
- **建议**: 无需修改，框架限制下的合理写法。
- **复核状态**: 未复核
