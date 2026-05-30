# 对抗性审查汇总 — nop-stream Round 8 + Round 9

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-30
- **审查类型**: 开放式对抗性审查
- **重点区域**: Round 8: connector + 分布式协调; Round 9: CEP SharedBuffer + Window 算子 + Checkpoint 持久化
- **去重范围**: 8 轮对抗性审查 + 4 轮深度审计（详见 01-open-findings.md）

## 发现摘要

| 严重程度 | Round 8 | Round 9 追加 | 合计 |
|---------|---------|-------------|------|
| P0 | 0 | 3 | 3 |
| P1 | 4 | 4 | 8 |
| P2 | 8 | 6 | 14 |
| P3 | 2 | 3 | 5 |

## 最关键发现（Round 9 新增）

1. **AR-15** (P0): `SharedBufferAccessor.releaseNode` 使用 `break` 而非 `continue`，导致引用计数泄漏和 CEP 内存无限增长
2. **AR-16** (P0): `WindowAggregationOperator` 未实现 Session Window 合并，session window 语义完全损坏
3. **AR-17** (P0): `NFAState.equals` 对非 Comparable 的 `ComputationState` 调用 `Arrays.sort`，运行时 ClassCastException
4. **AR-18** (P1): `EventTimeTrigger` 返回 FIRE 而非 FIRE_AND_PURGE，窗口状态无限增长
5. **AR-19** (P1): Checkpoint 完成后存储失败，`CompletableFuture` 不可回滚导致幽灵 checkpoint

## 已修复的 Round 8 发现

- AR-1（TaskManager catch Error）→ 已修复为 `catch (Throwable t)`
- AR-3（processBarrier 不转发 barrier）→ 已修复，try-catch 包裹 snapshotState

## 评估结论

Round 9 深挖了之前 8 轮审查明确标记为"盲区"的三个区域：CEP SharedBuffer 内部、Window 算子完整生命周期、Checkpoint 持久化原子性。发现了 3 个 P0 级别问题，其中 SharedBuffer 的 `break` vs `continue`（AR-15）和 Session Window 未合并（AR-16）是之前所有审查都未触及的深层问题。这表明即使经过 8 轮审查，对底层引擎核心逻辑的覆盖仍有显著盲区。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
