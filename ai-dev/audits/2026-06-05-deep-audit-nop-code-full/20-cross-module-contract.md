# 维度 20：跨模块契约一致性 — nop-code 模块

## 第 1 轮（初审）

### [维度20-01] ICodeIndexService 接口泄漏内部实现类型

- **文件**: `ICodeIndexService.java:27-38,146`
- **证据片段**: 公共 API 返回 CodeFileAnalysisResult 和 CodeSymbol（core 层模型），暴露 FileFingerprint（内部增量类型）。
- **严重程度**: P2
- **建议**: 面向外方法使用 DTO 类型，将 batchSaveFileRecords 移到内部接口。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度20-02] CodeIndexService 直接依赖具体类而非接口

- **文件**: `CodeIndexService.java:62-70`
- **严重程度**: P2
- **现状**: 直接 import 和实例化具体类（ChangeAnalyzer、FlowDetector、各种 Extractor）而非通过 IoC 注入。
- **建议**: 通过 NopIoC 注入或使用工厂方法。
- **信心水平**: 很可能
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 20-01 | P2 | ICodeIndexService.java | 接口泄漏内部实现类型 |
| 20-02 | P2 | CodeIndexService.java:62 | 直接依赖具体类 |
