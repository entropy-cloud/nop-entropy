# 对抗性审查汇总 — nop-stream Round 8 + Round 9 + Round 10

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-30
- **审查类型**: 开放式对抗性审查
- **重点区域**: Round 8: connector + 分布式协调; Round 9: CEP SharedBuffer + Window 算子 + Checkpoint 持久化; Round 10: StreamGraph/JobGraph 生成逻辑 + DataStream API + Window timer 持久化 + NFA Pending 状态语义
- **去重范围**: 10 轮对抗性审查 + 4 轮深度审计（详见 01-open-findings.md）

## 发现摘要

| 严重程度 | Round 8 | Round 9 | Round 10 | 合计 |
|---------|---------|---------|----------|------|
| P0 | 0 | 3 | 0 | 3 |
| P1 | 4 | 4 | 6 | 14 |
| P2 | 8 | 6 | 4 | 18 |
| P3 | 2 | 3 | 1 | 6 |

## Round 10 最关键新发现

1. **AR-34** (P1): `ForwardPartitionRouter` 固定返回 channel 0，FORWARD + parallelism 不对等时所有数据发往 target subtask 0
2. **AR-37** (P1): `WindowOperatorTimerService` 的 timer 队列完全不做 checkpoint，恢复后窗口永远无法触发
3. **AR-36** (P1): `KeyedStreamImpl` 通过 parentStream 构造时，所有操作丢失 keySelector，keyed state 无法工作
4. **AR-38** (P1): `mergeWindowContents` 对非累加器值静默覆盖，Session Window 合并丢失 N-1 个窗口的数据
5. **AR-33** (P1): `JobGraphGenerator.createJobVertex()` 双重调用 `createOperatorFromFactory`，浪费序列化且有状态泄漏风险
6. **AR-39** (P1): NFA `advanceTime()` Pending 状态跳过 `handleTimeout`，NOT_FOLLOW 模式超时回调永不触发

## 已修复的早期发现

- AR-1（TaskManager catch Error）→ 已修复为 `catch (Throwable t)`
- AR-3（processBarrier 不转发 barrier）→ 已修复，try-catch 包裹 snapshotState
- AR-24（InputGate Thread.sleep）→ 已修复为 `LockSupport.parkNanos`

## 评估结论

Round 10 深挖了之前审查明确标记为"盲区"的三个区域：StreamGraph/JobGraph 生成逻辑、DataStream API 的 KeyedStream 路径、Window 算子的 timer 持久化。发现了 6 个 P1 级新问题，其中最严重的是 Window timer 不做 checkpoint（AR-37）和 FORWARD 分区路由错误（AR-34）。

关键观察：之前 9 轮审查关注了 checkpoint 的 barrier 机制、序列化格式、coordinator 逻辑，但遗漏了 timer 持久化这一关键环节。这说明即使对同一子系统（checkpoint）进行了多轮审查，对完整数据流路径的端到端追踪仍然不足。timer 是连接"窗口内容"（通过 keyed state 已保存）和"窗口触发"的桥梁，这座桥断了，整个恢复链路就断了。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
