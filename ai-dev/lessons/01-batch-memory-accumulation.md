# 01 - 分批处理 ≠ 流式处理：累积后再持久化仍会 OOM

**日期**：2026-05-10
**来源模块**：nop-code / `ProjectAnalyzer` + `CodeIndexService`
**严重度**：P1（大项目 OOM 风险）
**状态**：已识别，待修复

## 错误认知

审计时一度认为 `ProjectAnalyzer` "分批处理，没有内存问题"，将其标记为非问题。理由是：
- 使用了 `BatchQueue`，默认 batchSize=1000
- `CodeIndexService` 持久化后只返回统计数据
- DB 读回时 `setSourceCode(null)`

**这是错的。**

## 实际行为

```java
// ProjectAnalyzer.analyzeProject()
List<CodeFileAnalysisResult> fileResults = new ArrayList<>();

// 所有批次结果全部累积
for (Future<List<CodeFileAnalysisResult>> future : futures) {
    fileResults.addAll(future.get());  // ← 全部加到同一个 list
}

// 全部分析完才返回
return new ProjectAnalysisResult(fileResults, globalSymbolTable, stats);
```

```java
// CodeIndexService.indexDirectory()
ProjectAnalysisResult result = analyzer.analyzeProject(...);  // ← 全量在内存
persistInSession(indexId, vfsPath, result, session);           // ← 然后才写 DB
return result.getFileResults().size();                          // ← 只需要统计数字
```

**10000 个文件 → 10000 个 `CodeFileAnalysisResult`（含完整 sourceCode）同时在内存中。**

## 正确做法

每完成一个批次 → 立即持久化到 DB → 释放该批次内存：

```
batch1 → 持久化 → 释放
batch2 → 持久化 → 释放    ← 内存中永远只有一个批次
batch3 → 持久化 → 释放
...
最后只返回统计数字
```

实现方式：`ProjectAnalyzer` 提供 `onBatchComplete` 回调，`CodeIndexService` 在回调中即时持久化。

## 判定规则

> **"分批提交"不等于"流式处理"。** 如果所有批次的输出仍然累积在内存中（list/map），那分批只是并行度优化，不是内存优化。
>
> 判断标准：**内存中同时存在的业务数据是否超过一个批次？** 如果超过，就是内存累积问题。

## 审计检查模式

在审计"分批处理"代码时，必须追踪数据流向：

1. ✅ 分批读取/计算
2. ❓ 每批次的结果**立即消费**（持久化/传输/丢弃），还是**累积到集合**？
3. ❓ 最终返回的是**全量数据**还是**聚合统计**？

如果第 2 步累积、第 3 步返回全量 → 这是内存累积，不是真正的流式处理。

## 适用场景

- 批量数据分析后入库
- 文件批量解析后存储
- 任何"分批处理 + 全量返回"的模式
