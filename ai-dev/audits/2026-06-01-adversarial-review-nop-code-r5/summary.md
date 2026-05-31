# Adversarial Review: nop-code — Summary

> **日期**: 2026-06-01（第 5 轮对抗性审查）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查

## 总体评价

nop-code 模块自上次审查以来无代码变更。本次审查从 BizModel 安全面切入，发现 `detectFlows` 缺少 `@Auth` 保护——这是一个连续 4 轮对抗性审查和 2 次 deep audit 都未触及的安全面缺陷。继续追踪后发现了 N+1 查询模式和多语言支持缺口。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 2    | BizMutation 缺少 @Auth（AR-82）、N+1 查询（AR-83） |
| P2      | 3    | 死代码检测假阴性（AR-84）、测试检测多语言缺口（AR-85）、全量加载（AR-86） |

## 仍存在的 P0 问题

| # | 问题 | 来源 |
|---|------|------|
| AR-78 | bfsCollect 反向遍历只走深度 1 | AR-29→AR-63→AR-78，连续 5 次审查确认 |

## Top 3 关键发现

1. **AR-82 (P1)**: `detectFlows` 是唯一未加 `@Auth` 的 `@BizMutation`，允许任何已认证用户触发高开销的符号表/调用图构建+数据库写入
2. **AR-83 (P1)**: `getModuleDigest` 对每个文件单独查询符号（N+1 模式），数千文件时性能灾难
3. **AR-84 (P2)**: `DeadCodeDetector.isPotentiallyDynamic` 使用 `signature.contains("Bean")` 等宽泛子串匹配，导致 Spring 项目中死代码检测近乎失效

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（5 条新发现 + 11 条已知未修复确认）
