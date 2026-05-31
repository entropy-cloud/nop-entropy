# Adversarial Review: nop-code — Summary

> **日期**: 2026-06-01（第 4 轮对抗性审查）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查

## 总体评价

nop-code 模块自 2026-05-31 审查以来修复了 `deleteFileRecords` 的实体清理缺失（AR-60）和锁粒度问题（AR-11→ReentrantLock）。但新引入的 ReentrantLock 实现存在竞态条件（AR-77），且 3 个 P0 问题仍未修复。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 1    | indexLocks 竞态条件（AR-77） |
| P2      | 3    | 线程泄漏（AR-79）、全量加载 OOME（AR-80/AR-81） |

## 仍存在的 P0 问题

| # | 问题 | 来源 |
|---|------|------|
| AR-78 | bfsCollect 反向遍历只走深度 1 | AR-29→AR-63→AR-78，连续 4 次审查确认 |

## Top 3 关键发现

1. **AR-77 (P1)**: `indexDirectory` 的 `indexLocks` 在 `finally` 中移除锁，允许后续线程创建不同锁实例，绕过互斥保护
2. **AR-79 (P2)**: `CommunityDetector.runWithTimeout` 超时后 `shutdownNow()` 不等待线程终止，CPU-bound Leiden 算法线程泄漏
3. **AR-80 (P2)**: `getIndexStats` 全量加载所有符号和文件到内存仅做计数——比 AR-75 更危险，因为此方法是 UI 常规操作

## 已修复确认

| # | 问题 | 修复方式 |
|---|------|---------|
| AR-60/AR-73 | deleteFileRecords 不删除 NopCodeUsage | 现在完整删除 8 种关联实体（:1173-1197）✅ |
| AR-11 | 粗粒度 synchronized | 改为 ReentrantLock + ConcurrentHashMap（:98, 273-304）✅ |
| AR-12 | deleteIndex 全量加载 | 改为分页删除 deleteEntitiesPaged（:513-560）✅ |

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（5 条新发现 + 12 条已知未修复确认）
