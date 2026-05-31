# Adversarial Review: nop-code — Summary

> **日期**: 2026-05-31（第 4 轮刷新审查）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查

## 总体评价

nop-code 模块自第 3 轮审查（2026-05-31 首次）以来无显著代码变更。本次审查从零开始重新阅读全部核心源文件，重点关注异常路径连环效应、代码生成正确性和未来可扩展性。

**本轮发现 2 个新问题**，均为前几轮审查（含 21 维度 deep audit）未触及的新模式。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 1    | 全量加载实体计数 OOME（AR-77） |
| P2      | 1    | getKind().name() NPE 补充发现（AR-78） |

## 仍存在的 P0 问题

| # | 问题 | 来源 |
|---|------|------|
| AR-63 | bfsCollect 反向遍历只走深度 1 | AR-29 未修复 |
| AR-64 | VFS 路径跳过语义边和 resolve | AR-47 未修复 |
| AR-68 | DeadCodeDetector 排除逻辑全死代码 | AR-48 未修复 |

## Top 3 关键发现

1. **AR-77 (P1)**: `getIndexStats` 和 `updateIndexStats` 使用 `findAllByQuery().size()` 而非 `countByQuery()` 全量加载实体仅用于计数，大型索引下内存浪费严重
2. **AR-78 (P2)**: `buildTypeHierarchy` 和 `buildCallHierarchy` 中 `symbol.getKind().name()` 缺 null 保护，是前次 deep audit 15-01 的遗漏补充（2 处）
3. **AR-63 (P0)**: 反向依赖 BFS 仍然只走深度 1——这是连续 4 次审查均确认的未修复 Bug

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（2 条新发现 + 全部已知未修复确认）
