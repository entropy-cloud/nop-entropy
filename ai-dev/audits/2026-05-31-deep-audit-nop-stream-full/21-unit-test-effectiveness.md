# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-13] P-8: testFencing_OldAttemptRejected fencing token 未使用——测试无效

- **文件**: `nop-stream-runtime/.../TestDistributedExactlyOnce.java:523-569`
- **证据片段**:
  ```java
  String oldToken = "fencing-old";  // L525 - 声明了
  String newToken = "fencing-new";  // L526 - 但从未使用
  // ...
  boolean staleAccepted = recovered.acknowledgeTask(loc0, 9999L, staleSnap);  // L565
  assertFalse(staleAccepted, "ACK for unknown/stale checkpoint should be rejected");
  ```
- **严重程度**: P0
- **现状**: 测试声明了 fencing token 变量但从未使用，实际测试的是未知 checkpoint ID 被拒绝
- **风险**: fencing 安全机制的测试给开发者虚假安全感
- **建议**: 重写测试，创建两个不同 fencing token 的 coordinator 实例验证互斥
- **信心水平**: 确定
- **误报排除**: 变量声明但未使用是明确的代码错误
- **复核状态**: 未复核

### [维度21-01] P-2: NFA 测试仅验证匹配数量不验证内容

- **文件**: `nop-stream-cep/.../TestNFAExtended.java:130-147,332-357,451-476,503-523`
- **严重程度**: P1
- **现状**: 多个测试只 `assertEquals(N, matches.size())`，不验证具体匹配内容
- **风险**: NFA 产生错误匹配但数量正确时测试仍通过
- **建议**: 使用 assertMatches() 验证每个匹配的具体事件
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-14] P-3: testDistributedRecovery 恢复验证是手动的

- **文件**: `TestDistributedExactlyOnce.java:279-410`
- **严重程度**: P1
- **现状**: source2 硬编码从 index 5 开始，不使用 checkpoint 恢复的 offset
- **风险**: exactly-once 核心语义未被真正测试
- **建议**: 恢复后应从 checkpoint 中读取 offset 自动继续
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-07] P-4: Thread.sleep 代替同步

- **文件**: `TestCheckpointRecovery.java` (5 处)
- **严重程度**: P2
- **现状**: 使用 Thread.sleep(200) 等待异步完成
- **建议**: 使用 CompletableFuture.get(timeout) 替代
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-04] P-3: skipPastLast 与 noSkip 期望值相同

- **文件**: `TestNFAExtended.java:567,624`
- **严重程度**: P2
- **现状**: 两种不同 skip 策略对相同输入期望结果相同（都是 2）
- **建议**: 使用两种策略会产生不同结果的输入场景
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-05] P-4: 反射访问私有字段

- **文件**: `TestCepOperatorBasic.java` (via CepTestUtils), `TestWindowAggregationOperatorLateData.java:483`
- **严重程度**: P2
- **现状**: 通过反射设置/读取私有字段
- **建议**: 在 AbstractStreamOperator 中添加测试友好的 setter
- **信心水平**: 确定
- **复核状态**: 未复核

## 维度复核结论

测试体量大（1,514 个测试方法），但有效性存在结构性问题。P0 级的 fencing 测试无效是最严重的发现，应立即修复。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 21-13 | P0 | TestDistributedExactlyOnce.java | fencing 测试无效 |
| 21-01 | P1 | TestNFAExtended.java | 弱断言只验证数量 |
| 21-14 | P1 | TestDistributedExactlyOnce.java | 恢复验证是手动硬编码 |
| 21-04 | P2 | TestNFAExtended.java | skip 策略差异未测试 |
| 21-07 | P2 | TestCheckpointRecovery.java | Thread.sleep 代替同步 |
| 21-05 | P2 | CepTestUtils/TestWindowAgg | 反射访问私有字段 |
