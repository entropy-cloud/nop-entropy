# 对抗性审查汇总 — nop-stream Round 17

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-31
- **审查类型**: 开放式对抗性审查
- **审查范围**: Round 17 — 全模块，聚焦前 16 轮盲区（SourceEnumerator、空占位模块验证、MemoryStateSerDe 序列化往返、fraud-example 运行路径、WindowOperator namespace 序列化一致性）
- **去重范围**: 16 轮对抗性审查 + 6 轮深度审计

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 1    | ConcurrentLinkedQueue iterator.remove() 确定性崩溃 |
| P2      | 1    | WindowOperator namespace 非确定性（latent） |
| P3      | 2    | 文档矛盾 + 资源泄漏 |

## 最关键发现

1. **AR-1** (P1): `SourceEnumerator.assignSplits()` 在 `ConcurrentLinkedQueue` 的迭代器上调用 `remove()`，必定抛出 `UnsupportedOperationException`。该方法当前可能无调用者（否则早已暴露），是确定性死代码 bug。

2. **AR-2** (P2): `WindowOperator.windowNamespace()` 对非 TimeWindow 的自定义 Window 类型使用 `System.identityHashCode()`，checkpoint/restore 后 namespace 不匹配导致窗口状态丢失。当前无实际影响（无自定义 Window 类型），但构成地雷。

## 与前轮关系

Round 16 的盲区自评列出了 5 个未覆盖区域，本轮逐一追踪：
- ✅ MemoryStateSerDe 序列化往返：已验证，AggregatingState 的 ACC 类型通过 `StateDescriptor<ACC>` 正确传递，序列化/反序列化类型一致。**非 bug**。
- ✅ fraud-example 完整运行路径：已阅读，NFA 使用模式正确，Demo 类为示例代码，无生产级 bug。
- ⚠️ BarrierAligner vs InputGate 一致性：仍为盲区（BarrierAligner 标注为未使用）。
- ✅ 空占位模块验证：nop-stream-flow/checkpoint/flink 确认为空模块。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
