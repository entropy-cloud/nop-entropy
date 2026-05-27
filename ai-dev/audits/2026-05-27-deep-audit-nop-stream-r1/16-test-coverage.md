# 维度 16 审计报告：nop-stream 测试覆盖与质量

> 审计日期: 2026-05-27
> 指标: 183测试文件/1,302方法, ~45%文件覆盖率

## 发现

### [16-01] WindowedStream DSL 测试使用 assertDoesNotThrow 弱断言
- **文件**: `nop-stream-core/.../TestWindowedStreamAggregation.java:52,73,94`
- **严重程度**: P2
- **建议**: 改为验证 transformation 名称和 DAG 结构。

### [16-02] Checkpoint 超时测试使用 Thread.sleep
- **文件**: `nop-stream-runtime/.../TestCheckpointIntegration.java:189`
- **严重程度**: P2
- **建议**: 用 Awaitility.await() 替代 Thread.sleep。

### [16-03] Connector 测试大量使用 Thread.sleep
- **文件**: TestDebeziumCdcSourceFunction.java, TestMessageAdapters.java 等
- **严重程度**: P2
- **建议**: 用 CountDownLatch.await() 或 Awaitility 替代。

### [16-04] CEP Operator 测试通过反射注入 processingTimeService
- **文件**: TestCepOperatorBasic.java, TestCepOperatorStateRecovery.java 等4个文件
- **严重程度**: P3
- **建议**: 抽取为共享测试工具方法。

### [16-05] ProcessingTimeoutTrigger 零测试覆盖
- **文件**: `nop-stream-core/.../triggers/ProcessingTimeoutTrigger.java`
- **严重程度**: P2
- **建议**: 新增测试覆盖超时触发、清空窗口、状态恢复。

### [16-06] CoMapFunction / CoFlatMapFunction 零测试覆盖
- **严重程度**: P3
- **建议**: 如不使用则标注 @Deprecated。

### [16-07] PrintSinkFunction / PrintSink 零测试覆盖
- **严重程度**: P3

### [16-08] FunctionUtils 零测试覆盖
- **严重程度**: P3

### [16-09] TestCheckpointParticipant 未集成 IKeyedStateBackend
- **严重程度**: P2
- **现状**: 使用 stub 测试接口契约但不验证状态恢复。

### [16-13] TestFingerprintAndTerminationMode 使用6次300ms Thread.sleep
- **严重程度**: P3

### [16-14] SourcePullBarrier 测试 Thread.sleep 合理但可优化
- **严重程度**: P3

## 正面发现
- Checkpoint 恢复测试覆盖面优秀（12+专门测试）
- CEP 测试体系成熟（25文件/158方法）
- Exactly-once 分布式测试充分（8个测试方法）
- 无测试顺序依赖问题

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 16-01 | P2 | TestWindowedStreamAggregation.java | assertDoesNotThrow 弱断言 |
| 16-02 | P2 | TestCheckpointIntegration.java | Thread.sleep 等待超时 |
| 16-03 | P2 | connector 测试 | 大量 Thread.sleep |
| 16-04 | P3 | CEP operator 测试 | 反射注入脆弱 |
| 16-05 | P2 | ProcessingTimeoutTrigger | 零测试覆盖 |
| 16-06 | P3 | CoMapFunction等 | 零测试覆盖 |
| 16-07 | P3 | PrintSinkFunction | 零测试覆盖 |
| 16-08 | P3 | FunctionUtils | 零测试覆盖 |
| 16-09 | P2 | TestCheckpointParticipant | 未集成状态后端 |
| 16-13 | P3 | TestFingerprintAndTerminationMode | 6次Thread.sleep |
| 16-14 | P3 | TestSourcePullBarrierInjection | Thread.sleep |
