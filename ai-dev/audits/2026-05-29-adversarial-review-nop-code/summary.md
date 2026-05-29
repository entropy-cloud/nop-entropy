# Adversarial Review: nop-code — Summary

> **日期**: 2026-05-29
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查

## 总体评价

nop-code 模块架构设计清晰，分层合理，但实现质量处于**功能原型到生产就绪的过渡阶段**。核心索引管线能工作，但存在导致功能静默失效的 Bug、原生内存泄漏、性能瓶颈，以及 nop-code-flow 子模块的多处未完成实现。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 2    | 数据一致性破坏、原生内存泄漏 |
| P1      | 4    | 功能缺失、缓存不一致、映射逻辑缺陷 |
| P2      | 12   | 性能、静默失败、线程安全、GraalVM |
| P3      | 2    | 硬编码指标、内存缓慢增长 |

## Top 3 关键发现

1. **AR-01 (P0)**: `resolveQualifiedNamesToIds` 将 `superTypeId` 从 qualified name 替换为 UUID，导致 `getTypeHierarchy` 对所有项目内部继承链返回空结果
2. **AR-02 (P0)**: Python/TypeScript 适配器的 `TSTree` 原生对象未关闭，大规模索引时会导致进程 OOM
3. **AR-06 (P1)**: TypeScript 调用提取完全未实现（`walkNodeForCalls` 是死代码），TypeScript 项目的调用图永远为空

## 已知未修复问题

CRG 对比分析（2026-05-25）确认的 7 个 Bug 中，5 个仍然存在（NopCodeUsage 未填充、Python/TS 适配器未注册、sourceCode 返回 null、每次图查询全量重建、File ID 碰撞风险）。

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（22 条发现）

## 审查方法

代码驱动的开放探索，起始视角：异常路径侦探 + 死代码清道夫 + 10x 规模运维者。覆盖 10 个子模块的核心源码、ORM 模型、BizModel、语言适配器、图分析算法、flow 子模块。
