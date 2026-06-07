# Adversarial Review: nop-code — Summary

> **日期**: 2026-06-06（第 10 轮）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查（并发安全 + BizModel 安全 + ORM 级联 + 数据完整性 + 资源泄漏）

## 总体评价

自上一轮审查（r9, 2026-06-06b）以来，nop-code 模块修复了 14 个问题（包括 P0 级增量索引路径失效 AR-124），显示团队在积极跟进审计发现。

本轮从 5 个全新视角切入，发现了 12 个新问题。最严重的是 **CallGraph 线程安全不一致**（AR-145, P0）——`addEdge()`/`getCallees()`/`getCallers()` 都有 `synchronized`，但 `getAllNodeIds()` 和 `getForwardMap()` 没有同步，在缓存重建期间可能导致 HashMap 数据竞争和 JVM 挂起。

安全层面发现 8 个空 BizModel 暴露完整 CRUD 端点且无 @Auth（AR-146），任何已认证用户可通过 GraphQL 直接删除调用图、篡改依赖关系。

ORM 层面发现 `NopCodeFile` 和 `NopCodeSymbol.usages` 缺少 `cascadeDelete`（AR-149/150），通过 BizModel CRUD 端点删除文件/符号时子实体成为孤儿。

数据完整性层面发现 AR-132 的修复不完整——CodeGraphService 的 `entityToInheritance` 仍将 `superTypeId`（可能是 UUID）直接映射为 `superTypeQualifiedName`（AR-151），导致类型层级查询在 `resolveQualifiedNamesToIds` 后无法正确展开父类。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | CallGraph 线程安全不一致（AR-145） |
| P1      | 6    | BizModel 安全缺口（AR-146）、FlowDetector 可变引用（AR-147）、CallGraph 可变内部（AR-148）、ORM 级联缺失（AR-149/150）、ID/QN 混淆残留（AR-151） |
| P2      | 4    | 线程泄漏（AR-152）、算法缺陷（AR-153）、CLOB 加载残留（AR-154）、SymbolTable 线程安全（AR-155）、风险评级失效（AR-156） |
| P3      | 1    | evictOverflow 无序驱逐残留（AR-157） |

## Top 5 关键发现

1. **AR-145 (P0)**: CallGraph.getAllNodeIds()/getForwardMap() 缺少 synchronized——与 addEdge 的数据竞争
2. **AR-146 (P1)**: 8 个空 BizModel 暴露完整 CRUD 接口且无 @Auth——任意数据篡改风险
3. **AR-149 (P1)**: NopCodeFile ORM 缺少 cascadeDelete 到子实体——单文件删除导致孤儿记录
4. **AR-150 (P1)**: NopCodeSymbol.usages 缺少 cascadeDelete——符号删除后 NopCodeUsage 孤儿
5. **AR-151 (P1)**: CodeGraphService.entityToInheritance 仍将 superTypeId 映射为 QN——AR-132 修复不完整

## 已修复确认（自 r9 以来）

14 个问题已修复：AR-124（路径比较）、AR-125（字段名）、AR-126（统计更新）、AR-128（锁保护）、AR-129（findReferencedBy）、AR-130（模块摘要）、AR-131（字典缺失）、AR-133（单文件符号表）、AR-137（Tarjan 递归）、AR-138（搜索大小写）、AR-139（唯一约束）、AR-140（TS getNodeText）、AR-143（batchOutline）、AR-144（statusMap 驱逐）。

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（12 条发现）

## 结论

<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>
