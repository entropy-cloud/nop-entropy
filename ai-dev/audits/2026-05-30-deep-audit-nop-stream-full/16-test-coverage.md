# 维度 16：测试覆盖与质量

## 审计范围

nop-stream 全部 5 个有代码的子模块，共 195 个测试文件，约 39,872 行测试代码。

## 第 1 轮（初审）

### [维度16-01] CheckpointBarrierTracker "Known Issue" 测试断言过弱，无法验证修复

- **文件**: `nop-stream-core/.../execution/TestCheckpointBarrierTrackerConcurrency.java:89-112`
- **证据片段**:
```java
assertTrue(callbackCount.get() >= 1);  // guard 已修复，应始终为 1，>=1 永远通过
```
- **严重程度**: P2
- **现状**: acknowledgeOperator() 的 guard `if (operatorsToAck.get() <= 0) return` 已修复重复 ACK 问题。断言 `>= 1` 无法检测 guard 是否被意外移除。测试名和消息声称是 "Known Issue" 但实际已修复。
- **建议**: 将断言改为 assertEquals(1, callbackCount.get())，并更新测试名。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——断言过弱意味着回归无法被检测。
- **复核状态**: 未复核

### [维度16-02] TestFlowControl 仅测试默认配置值，未覆盖实际流控行为

- **文件**: `nop-stream-core/.../execution/flow/TestFlowControl.java`（全文26行）
- **严重程度**: P2
- **现状**: 仅验证 EdgeConfig 和 MemoryBudget 的默认值硬编码常量。没有测试背压传播、队列溢出、阻塞行为等实际流控语义。
- **建议**: 添加测试验证队列满时生产者是否阻塞等流控行为。
- **信心水平**: 确定
- **误报排除**: 流控是流处理引擎的核心基础设施，缺少行为测试是真实覆盖缺口。
- **复核状态**: 未复核

### [维度16-03] 测试中存在大量 Thread.sleep，影响可靠性和速度

- **文件**: 多个文件，共 41 处
- **严重程度**: P3
- **现状**: 部分使用 Thread.sleep 等待异步操作完成，在高负载 CI 环境中可能 flaky。项目中已大量使用 CountDownLatch.await()，应统一。
- **建议**: 将 Thread.sleep + 状态检查替换为 Awaitility.await() 或 CountDownLatch.await()。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——41 处 Thread.sleep 在 CI 中可能导致 flaky tests。
- **复核状态**: 未复核

### 正面发现

- **无 Mock 框架依赖**: 不使用 Mockito，通过匿名内部类构建测试替身
- **并发测试质量高**: CountDownLatch 同步启动、@RepeatedTest(10) 验证稳定性
- **错误路径和安全测试到位**: 路径遍历攻击、flush 失败后 buffer 清理、存储失败状态一致性
- **E2E 测试覆盖面广**: 7 种分布式不变量、完整 checkpoint-then-restore 流程
- **回归测试与 bug 修复绑定**: 多个测试文件名直接关联 bug fix
- **OperatorTestHarness 设计良好**: 完整的算子测试基础设施
