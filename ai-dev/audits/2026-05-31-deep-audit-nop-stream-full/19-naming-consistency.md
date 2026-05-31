# 维度 19：命名与术语一致性

## 发现（10个，均为 P2-P3）

### P2 发现
1. **TimerService 重复接口**: core（@Deprecated）和 cep 各有一个同名接口
2. **ShardPrefixedKey 重复类**: backend.memory 和 shard 包各有一个
3. **Task/Subtask/SubtaskTask 概念重叠**: 三个类承担近乎相同职责
4. **"State" 概念过载**: 在 CEP 和执行层中有 5 种不同含义
5. **错误码 key 与常量名不匹配**: ERR_CEP_NOT_CONDITION_DOES_NOT_SUPPORT_GROUP 的 key 为 "follow-not-does-support-group"
6. **"Plan" 概念增殖**: GraphExecutionPlan/DeploymentPlan/PartitionedPlan/CheckpointPlan 四个 Plan 类

### P3 发现
7. **错误码前缀扁平**: nop.err.cep.* 而非嵌套 nop.err.stream.cep.*
8. **StreamException 继承 StreamRuntimeException**: 与 Java 命名惯例相反
9. **SourceSplit vs SourceWorkUnit**: 同一概念不同名称
10. **DeploymentPlanGenerator 与 DefaultDeploymentPlanProvider 逻辑重复**
