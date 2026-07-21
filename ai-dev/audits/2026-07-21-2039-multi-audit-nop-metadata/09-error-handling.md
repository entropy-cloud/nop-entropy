# 维度09：错误处理与错误码 — 第1轮（初审）

> 审计模块: nop-metadata

## 发现清单

### [维度09-01] AggregationContext.safeProductName 吞异常无日志

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationContext.java:1413-1418`
- **证据片段**:
  ```java
  public static String safeProductName(DatabaseMetaData metaData) {
      try {
          return metaData.getDatabaseProductName();
      } catch (SQLException e) {
          return null;   // exception swallowed, no log
      }
  }
  ```
- **严重程度**: P2
- **现状**: 静默返回 null，不记录任何日志。同模块 `MetaTableBizModel` 和 `TableReferenceExecutor` 中相同功能正确记录 `LOG.warn`。
- **风险**: 调用方无法判断是数据库名未知还是连接异常，排查困难。
- **建议**: 加 `LOG.warn("getDatabaseProductName failed", e);`
- **信心水平**: 确定
- **误报排除**: 不是 JDBC 类型转换兜底，同文件同功能的其他实现已正确 log。
- **复核状态**: 未复核

### [维度09-02] 5 处 catch + 空体静默吞异常

- **文件**: `MetaTableProfiler.java:484`, `MetaQualityRuleExecutor.java:599,606`, `MetaDataSourceConnectionProcessor.java:285`, `CheckpointActionDispatcher.java:323`
- **证据片段** (MetaTableProfiler.java:484):
  ```java
  } catch (SQLException ignore) {
      // 非数值列类型，回退按字符串解析
  }
  ```
- **严重程度**: P2
- **现状**: 5 处 catch 块捕获 SQLException/NumberFormatException 后完全静默丢弃。
- **风险**: 运维时无法诊断数据格式异常，排查找不到线索。
- **建议**: 每处加 `LOG.warn("...", e)`。
- **信心水平**: 确定
- **误报排除**: 文档要求"discard before must evidence"，项目规范不允许异常静默。
- **复核状态**: 未复核

### [维度09-03] NopMetaTagLabelBizModel.getWfNameFromMeta 吞异常

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaTagLabelBizModel.java:81-88`
- **证据片段**:
  ```java
  catch (Exception e) {
      return null;  // swallowed, no log
  }
  ```
- **严重程度**: P2
- **现状**: catch 所有异常后返回 null，无日志。同文件 `trySubmitForApproval` 正确记录了 `LOG.warn`。
- **建议**: 加 `LOG.warn("Failed to get wfName from meta", e);`
- **信心水平**: 确定
- **误报排除**: 非预期系统调用异常，必须留证。
- **复核状态**: 未复核

### [维度09-04] .param() 使用字符串字面量而非 ARG 常量

- **文件**: 37 处匹配，分布于 MetaAggregationExecutor, AggregationContext, MetaJoinExecutor, ExpressionMeasureValidator 等
- **严重程度**: P3
- **现状**: 大量 `.param("error", ...)` 使用字面量而非 `NopMetadataErrors.ARG_ERROR` 常量。
- **风险**: 重构时重命名常量不会同步更新字面量位置。
- **建议**: 统一替换为 `NopMetadataErrors.ARG_ERROR` 等常量。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-05] ErrorCode key 中子域出现点号而非连字符

- **文件**: `NopMetadataErrors.java:998-1000,1004-1006`
- **严重程度**: P3
- **现状**: `manifest.module-null` 和 `manifest.orm-model-null` 在子域部分使用点号，其他 100+ 个 ErrorCode 使用连字符。
- **建议**: 统一为 `manifest-module-null` 和 `manifest-orm-model-null`。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-06] NopMetaDataContractBizModel 使用静态导入

- **文件**: `NopMetaDataContractBizModel.java:20`
- **严重程度**: P3
- **现状**: 使用 `import static` 引入 `ERR_CONTRACT_NOT_FOUND`，模块中其他 ~40+ 文件使用全限定引用。
- **建议**: 删除静态导入，统一使用全限定引用。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度09-07] NopMetadataException String 构造器 @Deprecated 与文档矛盾

- **文件**: `NopMetadataException.java:35-53`
- **严重程度**: P3
- **现状**: String 构造器标记 `@Deprecated` 但无使用方，文档推荐"同时提供 (String) 和 (ErrorCode) 构造器"。
- **建议**: 删除或取消 @Deprecated 并添加 forRemoval。
- **信心水平**: 很可能
- **复核状态**: 未复核

### [维度09-08] Objects.requireNonNull 抛裸 NPE

- **文件**: `MetaQueryContext.java:51-57`, `MetaAggregationExecutor.java:62`, `AggregationContext.java:72-73`
- **严重程度**: P3
- **现状**: 10 处使用 `Objects.requireNonNull` 抛出裸 NullPointerException 而非 NopException。
- **建议**: 替换为 NopException 校验模式。
- **信心水平**: 很可能
- **复核状态**: 未复核

## 总结

| 编号 | 严重程度 | 描述 |
|------|---------|------|
| 09-01 | P2 | AggregationContext.safeProductName 吞异常 |
| 09-02 | P2 | 5 处 catch + 空体 |
| 09-03 | P2 | NopMetaTagLabelBizModel 吞异常 |
| 09-04 | P3 | .param() 字面量而非常量 |
| 09-05 | P3 | ErrorCode 命名不一致 |
| 09-06 | P3 | 静态导入 |
| 09-07 | P3 | @Deprecated 与文档矛盾 |
| 09-08 | P3 | Objects.requireNonNull 抛裸 NPE |
