# 维度 16：测试覆盖与质量 — nop-code 模块

## 第 1 轮（初审）

约 60 个测试文件，涵盖 core、service、flow、graph、lang-java、lang-python、lang-typescript。

### [维度16-01] TestEdgeProvenance 大量纯 getter/setter 往返测试和 assertNotNull 枚举遍历

- **文件**: `nop-code-core/.../model/TestEdgeProvenance.java:14-95`
- **严重程度**: P2
- **现状**: 命中 P-1 反模式（枚举值数量检查、getter/setter 往返测试）。
- **建议**: 移除纯往返测试和 assertNotNull 枚举测试。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度16-02] TestIncrementalDetector 包含纯 getter/setter 往返测试

- **文件**: `nop-code-core/.../incremental/TestIncrementalDetector.java:37-62`
- **严重程度**: P3
- **复核状态**: 未复核

### [维度16-03] TestPhase1BugFixes 直接实例化 BizModel 未通过 IoC — codeIndexService 为 null

- **文件**: `nop-code-service/.../TestPhase1BugFixes.java:157-176`
- **证据片段**: `new NopCodeSymbolBizModel()` 无 IoC 注入，仅因 null 检查提前返回而通过。
- **严重程度**: P1
- **现状**: 测试仅验证了 null 检查守卫，非核心逻辑。重构 null 检查将导致测试崩溃。
- **建议**: 使用 JunitAutoTestCase + IoC 或注入 mock codeIndexService。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度16-04] Mock analyzer 在 3 个测试文件中重复

- **文件**: TestProjectAnalyzer.java、TestProjectAnalyzerConcurrency.java、TestProjectAnalyzerIncremental.java
- **严重程度**: P2
- **建议**: 提取到共享 TestCodeAnalyzerFactory。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度16-05] rpcQuery/rpcMutation 辅助方法在 4-5 个文件中重复

- **严重程度**: P3
- **建议**: 提取到共享测试基类。
- **复核状态**: 未复核

### [维度16-06] TestModuleDigest 包含冗余 instanceof 检查（死代码分支）

- **文件**: `TestNopCodeSymbolBizModel.java:119-141`
- **严重程度**: P2
- **现状**: `if (responseData instanceof List) ... else if (responseData instanceof List)` 第二分支为死代码。
- **复核状态**: 未复核

### [维度16-07] NopCodeConfigs 和 NopCodeConstants 为空接口

- **文件**: `NopCodeConfigs.java`、`NopCodeConstants.java`
- **严重程度**: P3
- **现状**: 空接口占位符，但 CodeIndexService 有硬编码常量（BATCH_SIZE、MAX_QUERY_RESULTS）。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 16-01 | P2 | TestEdgeProvenance.java | P-1 枚举/getter 往返测试 |
| 16-02 | P3 | TestIncrementalDetector.java | P-1 getter 往返测试 |
| 16-03 | P1 | TestPhase1BugFixes.java | 直接实例化 BizModel 无 IoC |
| 16-04 | P2 | 3 个 TestProjectAnalyzer*.java | Mock analyzer 重复 |
| 16-05 | P3 | 4-5 个服务测试文件 | rpcQuery 辅助方法重复 |
| 16-06 | P2 | TestNopCodeSymbolBizModel.java | 死代码 instanceof 分支 |
| 16-07 | P3 | NopCodeConfigs/Constants.java | 空接口占位符 |
