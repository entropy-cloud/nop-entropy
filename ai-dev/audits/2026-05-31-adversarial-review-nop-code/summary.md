# Adversarial Review: nop-code — Summary

> **日期**: 2026-05-31（刷新审查）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查

## 总体评价

nop-code 模块在 2026-05-29 审查和前次 2026-05-31 审查后，经历了 5 个 Phase 的系统性修复（安全、数据完整性、OOM 稳定性、查询修复、测试有效性）。前次审查的 16 个发现中，7 个已被修复（TypeScript 调用提取、parseGitDiff 工作目录、级联删除、sourceCode CLOB、唯一约束、JavaParser 线程安全、语言适配器注册）。

但 3 个 P0 和多个 P1 仍未修复，且最近的 OOM 保护修复引入了缓存降级策略过于激进的新问题（AR-76）。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 1    | resolveQualifiedNamesToIds OOME（AR-75） |
| P2      | 1    | 缓存超限降级为空（AR-76） |

## 仍存在的 P0 问题

| # | 问题 | 来源 |
|---|------|------|
| AR-63 | bfsCollect 反向遍历只走深度 1 | AR-29 未修复 |
| AR-64 | VFS 路径跳过语义边和 resolve | AR-47 未修复 |
| AR-68 | DeadCodeDetector 排除逻辑全死代码 | AR-48 未修复 |

## Top 3 关键发现

1. **AR-75 (P1)**: `resolveQualifiedNamesToIds` 全量加载两个大表到内存，大型索引 OOME 风险
2. **AR-76 (P2)**: `CodeCacheManager` 超限时降级为空 SymbolTable/CallGraph，大型项目图分析静默失效
3. **AR-63 (P0)**: 反向依赖 BFS 仍然只走深度 1——这是连续 3 次审查（AR-29→AR-63→本次）均确认的未修复 Bug

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（2 条新发现 + 19 条已知未修复确认）
