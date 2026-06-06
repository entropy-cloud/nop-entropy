# 维度 03+12：API 表面积 + GraphQL API 层

## 维度 03：API 表面积与契约一致性

### [维度03-01] ICodeIndexService 三个 batch 方法无调用者

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`
- **行号**: 144-157
- **证据片段**:
  ```java
  void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints);
  List<FileFingerprint> batchLoadFileRecords(String indexId);
  void batchDeleteFileRecords(String indexId, List<String> filePaths);
  ```
- **严重程度**: P3
- **现状**: 接口方法存在但 BizModel 层和模块内部均无调用。
- **建议**: 评估是否需要，移除或暴露为 BizMutation。
- **复核状态**: 未复核

### [维度03-02] getIndexStats 使用 Map<String, Object> 中间结构

- **严重程度**: P3
- **现状**: selectFieldsByQuery 返回 List<Map<String, Object>>，依赖字段名字符串匹配。
- **复核状态**: 未复核

## 维度 12：GraphQL 与 API 层

### 零发现

检查范围：所有 BizModel 方法签名、分页实现、SQL 查询方式。

- 分页查询正确使用 PageBean + countByQuery + findPageByQuery
- 所有查询使用 QueryBean + FilterBeans 参数化构建，无硬编码 SQL
- 未使用 FieldSelectionBean（使用 @BizLoader DataLoader 模式替代）
