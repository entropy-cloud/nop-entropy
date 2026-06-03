# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] 测试辅助方法 newSchedule 中 setExecutorKind 重复调用

- **文件**: `TestNopJobScheduleBizModel.java:368-369`, `TestJobCoordinatorScanner.java:468-469`
- **证据片段**:
  ```java
  schedule.setExecutorKind(EXECUTOR_KIND_TEST);  // "test"
  schedule.setExecutorKind("testInvoker");        // 立即覆盖
  ```
- **严重程度**: P2
- **现状**: `setExecutorKind` 被调用两次，`EXECUTOR_KIND_TEST` 常量值为 `"test"` 被立即覆盖为 `"testInvoker"`。不会导致测试失败但降低可读性。
- **建议**: 删除第一次调用或统一使用常量。
- **信心水平**: 高
- **误报排除**: 同一模式出现在两个测试文件中，是系统性问题。
- **复核状态**: 未复核

### [维度16-02] JobCoordinator 编排类缺少测试

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCoordinator.java`
- **严重程度**: P2
- **现状**: JobCoordinator 是 coordinator 模块的核心编排类，负责按序启动/停止四个扫描器。当前无测试验证启动顺序、null 跳过、幂等性等行为。
- **建议**: 添加集成测试覆盖编排行为。
- **信心水平**: 高
- **误报排除**: 各扫描器有独立测试，但编排组合行为未被验证。
- **复核状态**: 未复核

### [维度16-03] RpcBroadcastTaskBuilder 缺少直接测试

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`
- **严重程度**: P3
- **现状**: 该类负责为广播 RPC 场景按实例数分片创建任务，无直接测试。
- **建议**: 添加测试覆盖实例分片和 fallback 行为。
- **信心水平**: 高
- **误报排除**: DefaultJobTaskBuilder 和 TestJobPartitionResolver 有测试，但广播分片逻辑未被直接验证。
- **复核状态**: 未复核

### [维度16-04] IJobFireStore/JobFireStoreImpl 核心方法缺少独立单元测试

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`
- **严重程度**: P3
- **现状**: IJobFireStore 的核心方法（cancelFire、completeFireAndUpdateSchedule 等）只在集成测试中间接覆盖，无独立单元测试验证 SQL、乐观锁、状态机守卫等。
- **建议**: 添加独立的 store 层测试。
- **信心水平**: 高
- **误报排除**: TestJobFireStoreRace 测试了并发竞态，但核心方法的正确性无直接验证。
- **复核状态**: 未复核
