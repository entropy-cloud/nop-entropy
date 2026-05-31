# 对抗性审查汇总 — nop-stream Round 11

## 基本信息

- **审核模块**: nop-stream（流处理引擎）
- **审核日期**: 2026-05-30
- **审查类型**: 开放式对抗性审查
- **审查范围**: Round 11 — 全模块聚焦信号量生命周期、序列化正确性、并发安全、内存泄漏
- **去重范围**: 10 轮对抗性审查 + 3 轮深度审计

## Round 10 修复确认

11 个 Round 10 高优先级发现（AR-1 ~ AR-11）已全部在当前代码中修复。关键修复包括：
- CheckpointCoordinator retryFailedCommits 硬编码 `true` → 使用 checkpointSuccessMap
- TaskManager 信号量双重释放/泄漏 → AtomicBoolean semaphoreReleased 保护
- WindowAgg 三重正确性问题（Long 溢出、activeWindowsPerKey 未重建、watermark 转发）→ 全部修复
- BatchConsumerSinkFunction flush 数据丢失 → buffer.clear 移入 try 内
- DebeziumCdcSourceFunction draining 不重置 → run() 开头重置
- HeapInternalTimerService timer 重注册被丢弃 → snapshot + removeAll 模式
- InputGate AT_LEAST_ONCE 多次触发 → barrierEmitted 标记

## 发现摘要

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | —       |
| P1      | 2    | 信号量泄漏 + 反序列化 NPE |
| P2      | 3    | 并发安全 + CEP/Window 内存泄漏 |
| P3      | 3    | 原子性 + 防御编码 + 状态一致性 |

## 最关键发现

1. **AR-1** (P1): `TaskManager.receiveAssignment` 在重复分配和 `putIfAbsent` 竞态两个 early-return 路径中不释放信号量，导致 HA 场景下节点逐渐不可用
2. **AR-2** (P1): `MessageSourceFunction` 的 transient `CountDownLatch` 反序列化后为 null，分布式部署启动时 NPE 崩溃

## 评估结论

Round 11 对 Round 10 报告的全部 11 个高优先级发现进行了验证确认——均已修复。这表明开发团队对审计反馈的响应是高效的。

本轮新发现集中在两个方向：(1) TaskManager 信号量管理的 patch-by-case 模式仍有盲区（receiveAssignment 的 early-return 路径未覆盖 semaphoreReleased 保护）；(2) Connector 层的序列化生命周期管理不完整（transient 字段缺少反序列化后的 re-initialization）。前者需要系统性重构信号量管理模式，后者需要为所有 connector source function 建立统一的 transient 字段初始化规范。

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
