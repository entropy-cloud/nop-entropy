# Adversarial Review: nop-code — Summary

> **日期**: 2026-06-06（第 9 轮）
> **模块**: nop-code
> **审查类型**: 开放式对抗性审查（增量索引链路 + 前端契约 + 并发安全 + 查询正确性 + 性能模式）

## 总体评价

自上一轮审查（2026-06-06 r8）以来，nop-code 模块修复了 6 个关键问题（AR-88/89 @Auth、AR-90 detectDeadCode、AR-94 零事务、AR-95 Leiden directed、AR-96 Python TSNode、AR-112 glob 匹配），显示团队在积极跟进审计发现。

本轮从 4 个全新视角切入，发现了 21 个新问题。最严重的是 **增量索引路径比较完全失效**（AR-124, P0）——`loadFingerprints` 返回的相对路径与 `collectResourcesFromVfs` 返回的 VFS 绝对路径永远不匹配，导致每次增量索引退化为全量重建。这是一个从 Day-1 就存在的架构级 bug。

并发安全层面发现 `indexLocks` 的保护范围严重不完整：3 个写入方法中只有 `indexDirectory` 使用锁，且 `deleteIndex` 在事务完成后移除锁对象存在竞态（AR-127 + AR-128）。

前端-后端契约层面发现 view.xml 中的 GraphQL selection 字段名与 DTO 属性名不匹配（AR-125），以及引用了不存在的字典文件（AR-131），导致 UI 功能静默失效。

## 新发现统计

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | 增量索引路径比较失效（AR-124） |
| P1      | 5    | view 字段名不匹配（AR-125）、indexFile 统计漂移（AR-126）、锁竞态（AR-127）、无锁保护（AR-128）、findReferencedBy 只取首条（AR-129） |
| P2      | 12   | getModuleDigest 全量加载、字典缺失、ID/QN 混淆、单文件跨引用、指纹 N+1、搜索加载 CLOB、继承截断、Tarjan 递归、搜索大小写、依赖无唯一约束、TS getNodeText、TS 全限定名 |
| P3      | 3    | usageCount 死字段、batchOutline N+1、statusMap 驱逐 |

## Top 5 关键发现

1. **AR-124 (P0)**: 增量索引路径比较失效——每次退化为全量重建，增量功能形同虚设
2. **AR-127 (P1)**: deleteIndex 移除锁对象允许并发写入
3. **AR-128 (P1)**: triggerIncrementalIndex/indexFile 无锁保护
4. **AR-125 (P1)**: view.xml GraphQL selection 字段名不匹配——isStatic/isAbstract 静默丢失
5. **AR-126 (P1)**: indexFile 不更新索引统计——Dashboard 数据漂移

## 已修复确认（自 r8 以来）

AR-88/89 @Auth 系统性缺失、AR-90 detectDeadCode 分类、AR-94 零事务、AR-95 Leiden directed、AR-96 Python TSNode、AR-112 glob 匹配 — **全部已修复**。

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情（21 条发现）

## 结论

`<ADVERSARIAL_RESULT>issues</ADVERSARIAL_RESULT>`
