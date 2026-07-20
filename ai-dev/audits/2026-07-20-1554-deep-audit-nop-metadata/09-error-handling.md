# 维度 09：错误处理与错误码 — 审计报告

> 初审结果（待复核）

## 发现条目

### [维度09-01] 100+ 个错误码缺少 nop.err. 前缀

- **文件**: 20+ 个文件，分布在整个 nop-metadata-service 中
- **证据**: 大量 inline ErrorCode 定义使用 `metadata.xxx` 格式而非 `nop.err.metadata.xxx`：
  ```java
  // MetaAggregationExecutor.java
  throw new NopException(ERR_METADATA_AGGR_NO_MEASURE)
      .param("metaTableId", metaTableId);
  ```
  其中 `ERR_METADATA_AGGR_NO_MEASURE` 定义为 `ErrorCode.define("metadata.aggr-no-measure", ...)`。
  集中化的 `NopMetadataErrors.java` 已正确定义了 9 个 `nop.err.metadata.xxx` 格式的错误码，但 100+ 个 inline 定义尚未迁移。
- **严重程度**: P2
- **现状**: 大量错误码缺少 `nop.err.` 前缀，这会导致前端 i18n 框架无法识别和国际化。
- **风险**: 错误消息无法国际化；通过 `ErrorCode.getErrorCode()` 返回的不是标准格式；与集中化的 `NopMetadataErrors.java` 分支。
- **建议**: 将全部 `metadata.xxx` 格式错误码逐步迁移至 `NopMetadataErrors.java`，改为 `nop.err.metadata.xxx` 格式。
- **信心水平**: 高

### [维度09-02] MetaQualityScorer 静默吞噬异常

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityScorer.java:270-271`
- **证据**:
  ```java
  } catch (Exception e) {
      return null;
  }
  ```
- **严重程度**: P2
- **现状**: catch 块捕获 Exception 后直接返回 null，无日志、无重新抛出。
- **风险**: 异常被完全隐藏，故障诊断困难。违反错误处理规范中"catch + 用返回值表达失败，且既不 rethrow 也没 LOG 以上 → 禁止"的规定。
- **建议**: 至少添加 `LOG.warn("Failed to parse extConfig dimension", e);` 或显式重新抛出 ErrorCode。
- **信心水平**: 高

### [维度09-03] MetaContractChecker 中包含8处硬编码中文业务消息

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/contract/MetaContractChecker.java`
- **证据**:
  ```java
  // 第 105 行
  "契约无可检查项（qualityExpectations 为空且 sla 为空）"
  // 第 109 行
  "质量路径存在错误，详见 qualitySummary"
  // 第 118 行
  "契约检查通过"
  // 第 394 行
  "质量失败规则数="
  // 第 402 行
  "采集过期"
  // 第 408 行
  "数据过期"
  // 第 412 行
  "无 Catalog 记录"
  ```
- **严重程度**: P3
- **现状**: 8 处硬编码中文消息用于业务结果状态和 SLA 评估输出。
- **风险**: 在混合语言环境中，硬编码中文会妨碍潜在的国际消费者。
- **建议**: 改为英文，或通过 i18n 键查找（如 `@i18n:metadata.contract.no-checks`）替换。
- **信心水平**: 高

### 合规确认

| 检查项 | 结果 |
|--------|------|
| 模块异常类存在（NopMetadataException） | ✅ |
| 提供 (String) 和 (ErrorCode) 构造器 | ✅ |
| 所有 throw 使用 NopException 或其子类 | ✅ |
| 无裸 RuntimeException / IllegalArgumentException | ✅ |
| ErrorCode 命名遵循 nop.err.{模块}.{子域} | ❌ 仅 9/100+ 正确 |
| .param() 正确传递上下文 | ✅ |
| 异常链正确保留 | ✅ |
| SLF4J 日志使用（非 System.out/err） | ✅ |
