# 维度 11：XMeta 与 BizModel 对齐 — nop-code 模块

## 第 1 轮（初审）

### [维度11-01] NopCodeFileBizModel @BizLoader 属性无对应 xmeta prop

- **文件**: `NopCodeFileBizModel.java:58-97`
- **证据片段**: 4 个 `@BizLoader(forType = CodeFileAnalysisResult.class)` 无 xmeta prop 定义。
- **严重程度**: P3
- **现状**: 框架运行时通过 Java 注解解析正常工作，但 GraphQL schema 文档不含这些字段。
- **建议**: 考虑为 CodeFileAnalysisResult DTO 添加 xmeta 声明。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度11-02] NopCodeSymbolBizModel @BizLoader 属性（usages, sourceCode）未在 xmeta 定义

- **文件**: `NopCodeSymbolBizModel.java:101-127`
- **严重程度**: P3
- **现状**: `usages` 和 `sourceCode` BizLoader forType=SymbolDTO.class 无 xmeta prop。
- **建议**: 与 11-01 同。
- **信心水平**: 很可能
- **复核状态**: 未复核

### 正面发现

- 7 个 dict 引用均有对应 dict.yaml 文件
- 所有 11 个 BizModel 有对应 xmeta 文件
- 字典定义与 xmeta 引用对齐正确

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 11-01 | P3 | NopCodeFileBizModel.java | @BizLoader forType 无 xmeta |
| 11-02 | P3 | NopCodeSymbolBizModel.java | @BizLoader forType 无 xmeta |
