# 维度 21：单元测试有效性

## 第 1 轮（初审）

### 检查范围

207 个测试文件。参照 ai-dev/skills/unit-test-antipatterns.md 的反模式清单。

### [维度21-01] 部分低价值测试未标记 @Tag("low-value")

- **文件**: `TestCheckpointBarrier.java`、`TestProcessingGuarantee.java`
- **证据片段**:
```java
// TestCheckpointBarrier — 测试硬编码 return true 的方法
@Test
void testSnapshot() {
    CheckpointBarrier barrier = new CheckpointBarrier(1L, 0L, CheckpointType.CHECKPOINT);
    assertTrue(barrier.snapshot());  // snapshot() 硬编码 return true，永远为真
}

// TestProcessingGuarantee — 镜像枚举的硬编码返回值
@Test
void testAtLeastOnce() {
    assertTrue(ProcessingGuarantee.AT_LEAST_ONCE.isBarrierAlignment());  // 镜像常量
}
```
- **严重程度**: P3
- **现状**: 项目已建立 `@Tag("low-value")` 标记机制（18 个文件已标记），但 TestCheckpointBarrier 和 TestProcessingGuarantee 未被标记。
- **风险**: 这些测试的保护力为零（改了实现也不会失败），但未被识别为低价值测试。
- **建议**: 补充 `@Tag("low-value")` 标记。
- **信心水平**: 确定
- **误报排除**: 项目已有自标记机制，此处是遗漏而非设计选择。
- **复核状态**: 未复核

---

### 整体评价

核心测试保护力强：窗口算子（snapshot/restore/late data）、CEP（NFA/SharedBuffer/Skip 策略）、Checkpoint（协调器/恢复/E2E/分布式 ExactlyOnce）覆盖充分。约 60 个测试使用 assertThrows 覆盖错误路径。

正面示例：TestBatchConsumerSinkFunctionFailure 专门测试 Bug N53 回归，TestWindowAggregationOperatorSnapshotRestore 覆盖版本不匹配/watermark 边界/allowedLateness。

## 维度复核结论

（待复核）

## 最终保留项

（待复核后填写）
