# 维度 21：单元测试有效性 — nop-code 模块

## 第 1 轮（初审）

参考：`ai-dev/skills/unit-test-antipatterns.md`（P-1 到 P-8 反模式清单）

### [维度21-01] P-1: TestCodeAccessModifier 测试枚举值唯一性 — 镜像实现

- **文件**: `TestCodeAccessModifier.java:13-36`
- **严重程度**: P2
- **现状**: 测试枚举 int 值唯一性和排序，但这些是编译时常量，Java 编译器保证。
- **建议**: 移除。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-02] P-1: TestCodeSymbolKind 镜像枚举排序

- **文件**: `TestCodeSymbolKind.java:29-33`
- **严重程度**: P3（边界价值，作为排序约束文档）
- **复核状态**: 未复核

### [维度21-03] P-3: TestChangeAnalyzer 仅用不存在的 git refs 测试 — 核心逻辑完全未测

- **文件**: `TestChangeAnalyzer.java:57-130`
- **证据片段**: 所有测试使用 `"nonexistent~1"`, `"nonexistent~2"` 作为 git refs，git diff 始终返回空结果。
- **严重程度**: P1
- **现状**: ChangeAnalyzer 最重要的逻辑路径（变更影响传播、安全敏感性评分）完全未测试。
- **风险**: 核心业务逻辑无测试保护。
- **建议**: 添加模拟 git diff 输出的测试，验证受影响符号和风险级别正确识别。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-04] P-5: TestCallGraph 仅测试空图

- **文件**: `TestProjectAnalyzer.java:196-201`
- **严重程度**: P3
- **现状**: Mock analyzer 不生成调用，图始终为空。
- **建议**: 添加生成调用的 mock 测试。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度21-05] P-6: testProgressCallback 误导性命名

- **严重程度**: P3
- **现状**: 方法名暗示测试进度回调，实际只测试文件计数。
- **建议**: 重命名或移除。
- **复核状态**: 未复核

### [维度21-06] P-7: TestIncrementalIndexWithDb 依赖 Thread.sleep 时序

- **文件**: `TestIncrementalIndexWithDb.java:84,108-112`
- **严重程度**: P2
- **现状**: Thread.sleep(50) 确保文件时间戳差异，CI 慢机器可能不足。
- **建议**: 使用 Files.setLastModifiedTime 或验证哈希变化。
- **信心水平**: 很可能
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 21-01 | P2 | TestCodeAccessModifier.java | P-1 枚举值唯一性测试 |
| 21-02 | P3 | TestCodeSymbolKind.java | P-1 枚举排序镜像 |
| 21-03 | P1 | TestChangeAnalyzer.java | P-3 核心变更分析逻辑未测 |
| 21-04 | P3 | TestProjectAnalyzer.java | P-5 仅空图测试 |
| 21-05 | P3 | TestFlowDetector.java | P-6 误导性方法名 |
| 21-06 | P2 | TestIncrementalIndexWithDb.java | P-7 Thread.sleep 时序依赖 |
