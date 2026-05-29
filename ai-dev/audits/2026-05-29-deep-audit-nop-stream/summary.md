# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-stream
- **审核日期**: 2026-05-29
- **执行维度**: 01, 02, 03, 08, 09, 10, 13, 14, 15, 16, 17, 18, 19, 20, 21（共 15 个维度；04/05/06/07/11/12 不适用于非 CRUD 基础设施模块）
- **目标范围**: nop-stream 全部 9 个子模块（api, core, checkpoint, flink, flow, cep, connector, runtime, fraud-example），607 个 Java 文件，约 88K 行代码

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 追加发现数 | 保留 | 降级 | 驳回 |
|------|---------|-----------|-----------|------|------|------|
| 01 依赖图 | 1 | 3 | - | 待复核 | - | - |
| 02 模块职责 | 1 | 2 | - | 待复核 | - | - |
| 03 API 表面积 | 1 | 3 | - | 待复核 | - | - |
| 08 IoC | 1 | 0 | - | 0 | - | - |
| 09 错误处理 | 1 | 6 | - | 待复核 | - | - |
| 10 XDSL | 1 | 0 | - | 0 | - | - |
| 13 安全 | 1 | 0 | - | 0 | - | - |
| 14 异步事务 | 1 | 5 | - | 待复核 | - | - |
| 15 类型安全 | 1 | 6 | - | 待复核 | - | - |
| 16 测试覆盖 | 1 | 5 | - | 待复核 | - | - |
| 17 代码风格 | 1 | 4 | - | 待复核 | - | - |
| 18 文档一致 | 1 | 2 | - | 待复核 | - | - |
| 19 命名一致 | 1 | 0 | - | 0 | - | - |
| 20 跨模块契约 | 1 | 5 | - | 待复核 | - | - |
| 21 测试有效性 | 1 | 0 | - | 0 | - | - |
| **合计** | - | **41** | **0** | - | - | - |

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 4 | 错误处理(2), 并发安全(1), 测试覆盖(1) |
| P2 | 22 | 跨模块契约(3), 错误处理(3), 异步事务(2), 类型安全(2), 测试覆盖(2), 代码风格(2), 依赖图(2), 文档一致(1), 模块职责(1), API表面(1), 跨模块(1) |
| P3 | 15 | 类型安全(2), 代码风格(2), 测试覆盖(2), 异步事务(2), 跨模块(2), 错误处理(1), 命名(1), API表面(1), 文档(1), 模块职责(1), 依赖图(1) |

## 关键发现摘要

### P1 发现（4 个）

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| 09-02 | 错误处理 | StreamExecutionEnvironment.java:280,288 | execute() 主入口核心路径使用 String 构造器，丢失 ErrorCode 结构化信息 |
| 09-04 | 错误处理 | ClassNameValidator.java:30-40 | 安全边界使用裸 SecurityException，绕过 StreamException 体系，上层 catch 无法捕获 |
| 14-01 | 异步事务 | TwoPhaseCommitSinkFunction.java:76-95 | finishCommit() 对 synchronizedMap 的复合操作缺少外部同步，可导致 ConcurrentModificationException |
| 16-01 | 测试覆盖 | CepOperator.java:294-321 | processing-time 代码路径完全无测试，核心控制流分支零覆盖 |

### P2 发现（22 个）

| 编号 | 维度 | 摘要 |
|------|------|------|
| 01-01 | 依赖图 | connector 的 nop-message-core 依赖 scope 不匹配（optional → 应为 test） |
| 01-02 | 依赖图 | nop-bom 缺失 runtime(40文件) 和 connector(6文件) 的版本管理条目 |
| 02-01 | 模块职责 | GraphModelCheckpointExecutor 混合 4 类职责，815 行 static 工具类 |
| 03-01 | API 表面 | execute() 双重包装异常，已结构化的 StreamException 被 catch-all 重新包装 |
| 03-02 | API 表面 | ICheckpointExecutorFactory default 方法 Javadoc 说"委托"但实际 throws UnsupportedOperationException |
| 09-01 | 错误处理 | connector 公共 API 12 处使用 String 构造器，已有对应 ErrorCode 未使用 |
| 09-03 | 错误处理 | runtime 公共 API 8 处使用 String 构造器 |
| 09-05 | 错误处理 | 关键路径上 55 处裸 JDK 异常（IllegalStateException 等） |
| 14-02 | 异步事务 | numPendingCheckpoints check-then-act 竞态可导致并发数超限 |
| 14-03 | 异步事务 | PendingCheckpoint.dispose() 未 synchronized，与 acknowledgeTask() 竞态 |
| 15-01 | 类型安全 | NFACompiler 中 Pattern/GroupPattern 使用 Raw Type |
| 15-02 | 类型安全 | HeapInternalTimerService key 类型被擦除为 Object |
| 15-04 | 类型安全 | getKeyedStateBackend() 不安全泛型窄化 |
| 16-02 | 测试覆盖 | NFA 条件异常路径无测试，SharedBuffer 清理无保障 |
| 16-03 | 测试覆盖 | CheckpointBarrierTracker extra-ACK 重复回调 bug 已知但未修复 |
| 17-01 | 代码风格 | 95 个未使用 import（36 个 NopStreamErrors 双重 import 模式） |
| 17-02 | 代码风格 | 4 个文件 import 分组顺序违规 |
| 17-03 | 代码风格 | FraudDetectionDemo 17 处 System.out 替代已声明的 Logger |
| 18-01 | 文档一致 | module-groups.md 遗漏 nop-stream 模块分组 |
| 20-01 | 跨模块 | SinkFunction.finish() 死契约 — 生产生命周期从未调用 |
| 20-02 | 跨模块 | CheckpointConfig.checkpointEnabled 默认 true 但无 factory 时静默跳过 |
| 20-03 | 跨模块 | storageType="jdbc" 配置已公布但运行时必定抛异常，无 SPI |

## 总评

nop-stream 是一个体量较大（88K 行）的流处理基础设施模块，整体架构清晰，模块分层合理。核心引擎（core）零业务依赖，cep/runtime/connector 作为功能扩展层单向依赖 core，无循环依赖和反向依赖。

**主要优势**：
1. 依赖方向合规，无循环依赖和反向依赖
2. 已建立完整的 ErrorCode 体系（NopStreamErrors 30 个 + NopCepErrors 8 个）
3. 测试数量充足（198 个测试文件），核心 happy-path 覆盖面好
4. 异常链保留良好，无吞掉异常的情况

**主要风险**：
1. **并发安全**：TwoPhaseCommitSinkFunction 的 synchronizedMap 使用违反 JDK 契约，PendingCheckpoint 的 dispose/acknowledgeTask 竞态，CheckpointBarrierTracker 的 extra-ACK bug 未修复。这三个并发问题组合起来可能在 exactly-once 语义下导致数据不一致。
2. **错误处理一致性**：ErrorCode 体系已建立但公共 API 中 String 构造器和裸 JDK 异常仍大量存在（约 95 处），安全边界 ClassNameValidator 的异常类型不匹配导致上层 catch 链断裂。
3. **API 契约可靠性**：SinkFunction.finish() 死契约、ICheckpointExecutorFactory Javadoc 与实现矛盾、CheckpointConfig 默认值与实际行为不匹配，这些组合起来构成"接口契约与实际行为偏差"的系统性风险。
4. **测试盲区**：CepOperator processing-time 路径完全无测试是最大的覆盖缺陷，NFA 异常路径和 CheckpointBarrierTracker 已知 bug 未修复是中等风险。

## 优先修复建议

### 立即修复（P1）

1. **[14-01]** TwoPhaseCommitSinkFunction.finishCommit() 添加 synchronized — 2 行改动
2. **[09-04]** ClassNameValidator 替换为 StreamException + ErrorCode — 4 行改动
3. **[09-02]** StreamExecutionEnvironment.execute() 错误路径使用 ErrorCode — 6 行改动
4. **[16-01]** 添加 CepOperator processing-time 测试 — 新增测试类

### 短期排期（P2 核心项）

5. **[14-03]** PendingCheckpoint.dispose() 添加 synchronized — 1 行改动
6. **[14-02]** tryTriggerPendingCheckpoint 使用 CAS 替代 check-then-act — 5 行改动
7. **[16-03]** CheckpointBarrierTracker 修复 extra-ACK bug（`<=0` → `==0`）— 1 行改动
8. **[20-01]** SubtaskTask.run() 添加 finish() 调用 — 3 行改动
9. **[20-02]** checkpointEnabled=true 但无 factory 时输出 WARN 日志 — 2 行改动
10. **[03-01]** execute() catch 块区分已结构化异常 — 3 行改动

### 中期排期（P2 改进项）

11. **[09-01/03/05]** 系统性收敛 String 构造器和裸 JDK 异常
12. **[01-02]** nop-bom 补充 runtime/connector 条目
13. **[17-01]** 批量清理未使用 import
14. **[18-01]** module-groups.md 添加 nop-stream 分组
15. **[20-03]** ICheckpointStorage 引入 SPI 工厂模式

## 本次审核盲区自评

1. **性能和内存分析**：未对 StreamTaskInvokable.invoke() 的热路径做性能剖析，无法评估 Watermark 传播和 operator chain 的吞吐量瓶颈。
2. **分布式 transport 层**：`RemoteInputChannel`、`RemoteResultPartition`、`RemoteGraphExecutionPlanBuilder` 的网络通信正确性未深入验证（代码存在但可能尚未完全启用）。
3. **状态后端正确性**：MemoryKeyedStateBackend 的序列化/反序列化逻辑（1398 行）未逐方法验证，仅检查了并发安全性。
4. **Flink 兼容层**：nop-stream-flink 是空占位模块，未来迁移时的兼容性未评估。
5. **集成测试端到端验证**：未运行实际的 `./mvnw test -pl nop-stream` 验证所有测试是否通过。
