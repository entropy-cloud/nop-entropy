# 对抗性审查汇总 — nop-stream Round N+1

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-06-05
- **审查类型**: 开放式对抗性审查
- **重点区域**: CEP 模型层 + SharedBuffer 序列化 + Watermark 线程模型 + Source 生命周期 + Checkpoint 序列化一致性 + Connector 线程安全 + JDBC 存储 + 窗口算子合并
- **去重范围**: ~17 轮对抗性审查 + 深度审计（详见 01-open-findings.md）

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 6    | 序列化(1), 生命周期管理(2), 线程安全(2), Barrier语义(1) |
| P2      | 9    | 检查点(4), 状态管理(2), 广播(1), 窗口(1), 连接器(1) |
| P3      | 3    | 死代码(1), 性能(1), 类型安全(1) |

## 最关键新发现

1. **AR-4** (P1): `StreamSourceOperator` 从不调用 `sourceFunction.cancel()` — 所有 connector 的资源泄漏根因
2. **AR-3** (P1): `TimestampsAndWatermarksOperator` 的 Timer 线程与算子线程并发访问非线程安全状态
3. **AR-1** (P1): `PatternStreamBuilder.build()` 硬编码 `inputSerializer=null` — CEP 在持久化状态后端上崩溃
4. **AR-5** (P1): `AbstractStreamOperator.processBarrier()` 快照失败后仍传播 barrier — 部分检查点不一致
5. **AR-2** (P1): `TaskExecutor.awaitCompletion()` 在 TimeoutException 后返回 true
6. **AR-8** (P1): `MessageSourceFunction` 回调在消息服务线程上调用非同步的 SourceContext

## 评估结论

经过 ~17 轮审计，nop-stream 的核心引擎（分布式执行、Timer、Checkpoint 协调器、窗口算子）已经被充分覆盖。本轮发现的 6 个 P1 级问题集中在两个此前覆盖不足的交叉领域：(1) **算子生命周期管理**——source/sink 函数的 cancel/finish 调用缺失是框架级契约问题，影响所有 connector 实现；(2) **线程模型正确性**——Watermark 生成器的 Timer 线程和 MessageSourceFunction 的回调线程都违反了流处理引擎的"算子状态单线程访问"假设。

这两个问题都是"横切"性质的：不是单个文件的 bug，而是影响了所有使用特定框架契约的代码。修复需要框架级别的变更。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
