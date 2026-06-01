# 对抗性审查汇总：nop-code（第 4 轮）

**审查日期**: 2026-06-01
**审查类型**: 开放式对抗性审查（第 4 轮）
**去重基线**: 第 1-3 轮对抗性审查（AR-01~AR-58）+ 2 次深度审计

## 总体评估

第 4 轮审查发现了 1 个 P0、3 个 P1、5 个 P2，共 9 个新问题。其中 **AR-59（extData filePath 从未写入）** 是一个影响 4 个核心分析功能的系统性缺陷，之前 3 轮和 2 次深度审计均未发现。

## 关键发现

| 编号 | 严重程度 | 一句话摘要 |
|------|---------|-----------|
| AR-59 | P0 | symbol.extData 从未写入 filePath，4 个下游分析组件的文件路径关联全部失效 |
| AR-60 | P1 | deleteIndex 外键删除顺序错误，SemanticEdge 在 Symbol 之后删除 |
| AR-61 | P1 | CodeCacheManager 超限静默返回不完整数据，图分析结果不可信 |
| AR-66 | P1 | deleteFileRecords 不清理跨文件 Call 引用，增量索引产生孤儿记录 |
| AR-62 | P2 | indexLocks ConcurrentHashMap 永不清理 |
| AR-63 | P2 | entityToFileResult 数据重建丢失所有关系数据 |
| AR-64 | P2 | findImplementations 全量加载所有符号 |
| AR-65 | P2 | NopCodeSymbol.xmeta 虚拟查询属性是死配置 |
| AR-67 | P2 | CommunityDetector.runWithTimeout 线程池泄漏（等同 AR-39，仍未修复） |

## 已确认修复的问题

AR-02（TSTree 内存泄漏）、AR-03（增量分析退化）、AR-29（BFS 深度）、AR-31（缓存失效）、AR-47（VFS 语义边）、AR-48（DeadCodeDetector 排除逻辑）。

## 仍存在的已知问题

AR-09（HashMap 线程不安全）、AR-28（TypeScript 调用分析死代码）、AR-32（extractFileKey 包名）、AR-39/AR-67（线程池泄漏）、AR-49（testGap 常量膨胀）。

## 最值得关注的 3 个方向

1. **extData 数据管线断裂**（AR-59）— 写入方和读取方之间的隐性契约未表达
2. **增量索引数据完整性**（AR-60 + AR-66）— 跨文件引用清理和删除顺序
3. **大规模数据静默截断**（AR-61）— 缓存超限后分析结果不可信但无错误信号

## 报告文件

- [01-open-findings.md](./01-open-findings.md) — 完整发现详情
