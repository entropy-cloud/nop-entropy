# 深度审核汇总报告

## 基本信息

- **审核模块**: nop-stream（流处理引擎框架模块）
- **审核日期**: 2026-05-28
- **执行维度**: 21 个维度中 15 个适用（6 个 N/A）
- **目标范围**: nop-stream 全部 9 个子模块（core, cep, connector, runtime, fraud-example + 4 个占位模块）

## 执行统计

| 维度 | 深挖轮次 | 初审发现数 | 保留 | 降级 | 驳回 | 状态 |
|------|---------|-----------|------|------|------|------|
| 01 依赖图 | 1 | 2 | 2 | 0 | 0 | 待复核 |
| 02 模块职责 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 03 API 表面积 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 04 ORM 模型 | N/A | 0 | 0 | 0 | 0 | 不适用 |
| 05 生成管线 | N/A | 0 | 0 | 0 | 0 | 不适用 |
| 06 Delta 定制 | N/A | 0 | 0 | 0 | 0 | 不适用 |
| 07 BizModel | N/A | 0 | 0 | 0 | 0 | 不适用 |
| 08 IoC/Beans | 1 | 0 | 0 | 0 | 0 | 零发现 |
| 09 错误处理 | 1 | 6 | 6 | 0 | 0 | 待复核 |
| 10 XDSL/XLang | 1 | 1 | 1 | 0 | 0 | 待复核 |
| 11 XMeta/BizModel | N/A | 0 | 0 | 0 | 0 | 不适用 |
| 12 GraphQL | N/A | 0 | 0 | 0 | 0 | 不适用 |
| 13 安全 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 14 异步/事务 | 1 | 7 | 7 | 0 | 0 | 待复核 |
| 15 类型安全 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 16 测试覆盖 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 17 代码风格 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 18 文档一致性 | 1 | 5 | 5 | 0 | 0 | 待复核 |
| 19 命名一致性 | 1 | 4 | 4 | 0 | 0 | 待复核 |
| 20 跨模块契约 | 1 | 5 | 5 | 0 | 0 | 待复核 |
| 21 测试有效性 | 1 | 2 | 2 | 0 | 0 | 待复核 |

**总计**: 21 维度执行，15 适用，初审判定 56 个发现，待独立复核。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 8 | 安全漏洞(1)、资源泄漏(1)、并发竞态(3)、测试缺失(1)、测试无效(1)、契约漂移(1) |
| P2 | 31 | 错误处理(4)、模块职责(4)、安全(2)、并发(5)、API设计(3)、测试覆盖(3)、代码风格(4)、文档(3)、跨模块(3) |
| P3 | 17 | 依赖(2)、模块职责(1)、错误处理(2)、安全(1)、并发(2)、类型安全(3)、测试(1)、文档(1)、命名(2)、跨模块(2) |

## 关键发现摘要

### P1 发现（8 项）

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| 13-01 | 安全 | LocalFileCheckpointStorage.java:233-239 | 路径遍历漏洞：savepointPath/targetPath 未净化直接拼入文件路径 |
| 14-01 | 异步 | GraphModelCheckpointExecutor.java:81-94 | TaskExecutor 未关闭，线程池泄漏 |
| 14-02 | 异步 | CheckpointCoordinator.java:94-125 | startCheckpointScheduler() 竞态条件 |
| 14-03 | 异步 | PendingCheckpoint.java:104-118 | acknowledgeTask() CompletableFuture 完成竞态 |
| 14-04 | 异步 | CheckpointCoordinator.java:196-246 | completePendingCheckpoint 与超时 abort 非互斥 |
| 16-01 | 测试 | PartitionRouter.java 等 | PartitionRouter 及 3 个实现零单元测试 |
| 21-01 | 测试 | TestOneInputTransformation 等 | Transformation 测试 1505 行 getter/setter 往返，保护力极弱 |
| 20-01 | 跨模块 | CepOperator.java:178-200 | 状态后端 fallback 绕过统一 checkpoint |

### P2 发现（31 项，重点列出）

| 编号 | 维度 | 摘要 |
|------|------|------|
| 09-01 | 错误处理 | ~20 处 IllegalStateException 裸抛 |
| 09-02 | 错误处理 | MemoryKeyedStateBackend IOException 裸抛 |
| 09-03 | 错误处理 | checkTaskFailures 只报告首个失败 |
| 09-04 | 错误处理 | CheckpointType.fromName() 用 IllegalArgumentException |
| 13-02 | 安全 | 反序列化枚举值未做异常处理 |
| 13-03 | 安全 | storeCheckPoint finally 块 deleteIfExists 在锁外执行 |
| 14-05 | 异步 | TaskExecutor 线程池未设置 daemon |
| 14-06 | 异步 | MemoryKeyedStateBackend 完全非线程安全 |
| 14-07 | 异步 | CompletedCheckpoint 返回可变内部 Map |
| 02-02 | 模块职责 | GraphModelCheckpointExecutor 上帝方法类 |
| 02-03 | 模块职责 | 两个功能重叠的窗口算子 |
| 15-04 | 类型安全 | KeyedStreamImpl sum/min/max unchecked cast |
| 03-01 | API | StreamComponents Map<String,Object> 公共 API |
| 16-02 | 测试 | operators 包 34 源文件仅 13 测试 |
| 18-01 | 文档 | component-roadmap.md 引用不存在的 ExecutionPlan |
| 18-02 | 文档 | architecture.md 包含不存在的 RuntimeTopology |
| 20-02 | 跨模块 | BatchConsumerSinkFunction 生命周期不一致 |

## 总评

nop-stream 是一个功能丰富的流处理引擎框架，核心架构设计合理（五层执行管线、CEP NFA 引擎、checkpoint 协调器、分布式执行）。模块间依赖方向正确，XDSL 生成链路完整，测试覆盖面广（186 个测试文件，38,245 行测试代码）。

**主要优势**：
- 依赖图清晰，无循环依赖，core 层边界干净
- 异常类体系设计良好（双构造器模式支持两档策略）
- 异常链保留优秀，无吞异常的空 catch 块
- XDSL 生成链路完整，CepPatternBuilder 桥接正确
- 关键 E2E/集成测试质量高（checkpoint lifecycle、exactly-once、window operator）

**主要风险**：
1. **安全**：LocalFileCheckpointStorage 路径遍历漏洞是可利用的 P1 问题
2. **资源管理**：GraphModelCheckpointExecutor 未关闭 TaskExecutor，导致线程泄漏
3. **并发**：CheckpointCoordinator 存在多个竞态条件（scheduler 启动、pending checkpoint 完成、超时 abort 互斥）
4. **测试**：PartitionRouter 核心路由逻辑零测试，Transformation 测试保护力极弱
5. **契约**：CepOperator 状态后端 fallback 绕过统一 checkpoint

## 优先修复建议

### 立即修复（P1）

1. **路径遍历修复** (13-01)：在 LocalFileCheckpointStorage 中对所有外部输入路径做 normalize + startsWith(baseDir) 校验
2. **线程池泄漏修复** (14-01)：在 GraphModelCheckpointExecutor.shutdown() 中关闭 TaskExecutor
3. **并发竞态修复** (14-02/03/04)：CheckpointCoordinator.startCheckpointScheduler 使用 synchronized；PendingCheckpoint.acknowledgeTask 加同步；completePendingCheckpoint 引入状态机
4. **测试补充** (16-01)：为 PartitionRouter 添加单元测试，覆盖边界值
5. **CEP 状态对齐** (20-01)：移除 CepOperator fallback，强制使用标准 IKeyedStateBackend

### 排期修复（P2）

6. 统一错误处理（09-01/02/04）：将 IllegalStateException/IOException 改为 StreamException
7. 多 task 失败聚合（09-03）：使用 addSuppressed 收集所有失败
8. 资源管理改进（13-03, 14-05, 14-06/07）
9. 文档与代码对齐（18-01/02/03）
10. 低价值测试清理与测试补充（21-01, 16-02/03/04）

## 本次审核盲区自评

1. **深挖轮次**：本次仅执行了第 1 轮初审，未进行追加深挖轮次。部分维度可能存在第 2 轮深挖才能发现的深层问题。
2. **独立复核**：未执行独立复核阶段，所有发现均基于初审。建议对 P1 发现逐条独立验证后再制定修复计划。
3. **运行时行为**：未执行 `./mvnw test -pl nop-stream` 验证测试是否全部通过。基线命令仅提供了静态分析结果。
4. **性能审计**：未覆盖性能相关维度（如 MemoryKeyedStateBackend 的内存使用效率、SharedBuffer 的缓存策略等）。
5. **Flink 兼容性**：大量代码从 Apache Flink 移植，未评估与上游 Flink 版本的同步状态和许可证合规性。

## 审计结论

**存在 P0/P1 问题，需要修复后才能认为模块健康。**

最紧迫的是安全漏洞（路径遍历）和资源泄漏（线程池未关闭），建议优先处理。
