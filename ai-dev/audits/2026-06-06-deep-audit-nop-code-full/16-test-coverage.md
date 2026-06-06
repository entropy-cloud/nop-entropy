# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] 多个 BizModel 方法缺少测试覆盖

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/entity/`
- **证据片段**:
  ```
  缺少测试的方法:
  NopCodeIndexBizModel: triggerFullIndex, indexFile, deleteIndex, findCycles, getDepGraph, getDeps, getReverseDeps, findDependentFiles
  NopCodeSymbolBizModel: getBySymbolId, findPage_symbols, getTypeHierarchy, getCallHierarchy, fileOutline, searchCode, findByAnnotation, findImplementations
  ```
- **严重程度**: P2
- **现状**: 公开 GraphQL API 无端到端验证。
- **建议**: 为关键方法添加 GraphQL RPC 测试。
- **信心水平**: 确定
- **误报排除**: 确认 grep 无测试调用。
- **复核状态**: 未复核

### [维度16-02] 错误路径覆盖不足

- **文件**: 整个 nop-code-service
- **严重程度**: P2
- **现状**: NopCodeErrors 定义 6 个 ErrorCode，无测试验证异常抛出行为。validatePath 路径遍历检查未测试。
- **建议**: 添加异常路径测试。
- **信心水平**: 确定
- **误报排除**: ErrorCode 定义存在但无对应测试。
- **复核状态**: 未复核

### [维度16-03] TestBuildHierarchyCycleProtection 和 TestBfsReverseTraversalDepth 内联了被测算法

- **文件**: `TestBuildHierarchyCycleProtection.java:82-113`, `TestBfsReverseTraversalDepth.java:106-125`
- **严重程度**: P2
- **现状**: 测试中重新实现了 BFS 和层次遍历算法，而非调用产品代码。
- **风险**: 测试验证的是测试自身实现，不是产品代码的正确性。
- **建议**: 改为调用产品代码。
- **信心水平**: 确定
- **误报排除**: 代码比较确认测试方法和产品方法实现不同。
- **复核状态**: 未复核

### [维度16-04] 测试 helper 方法在 10+ 文件中重复

- **文件**: `TestFlowAnalysisE2E.java` 等 10 个测试文件
- **严重程度**: P3
- **现状**: rpcQuery(), rpcMutation(), indexTestProject(), TEST_PROJECT_PATH 在每个文件中重复。
- **建议**: 抽取公共基类。
- **信心水平**: 确定
- **误报排除**: 代码完全相同。
- **复核状态**: 未复核

### [维度16-05] 真实集成测试被 @Disabled

- **文件**: `TestIndexNopEntropyProject.java:37`
- **严重程度**: P3
- **现状**: 12 个测试方法被 @Disabled，永不运行于 CI。
- **建议**: 改为 CI 中可选运行的 integration test profile。
- **信心水平**: 确定
- **误报排除**: 30 秒耗时在 CI 中可接受。
- **复核状态**: 未复核

## 检查通过项

- 核心算法测试扎实（CommunityDetector, ImpactAnalyzer, DeadCodeDetector 等）
- Bug 回归测试（AR-68, AR-84）质量高
- 并发安全测试存在
- ORM 关系导航测试覆盖
- 确定性 ID 测试和不可变性测试
