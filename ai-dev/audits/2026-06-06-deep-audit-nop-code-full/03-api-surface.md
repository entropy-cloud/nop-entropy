# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] I*Biz 接口未声明 BizModel 的任何自定义方法

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java:5-7`（全部 11 个 I*Biz 接口）
- **证据片段**:
  ```java
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex>{}
  ```
- **严重程度**: P2
- **现状**: NopCodeIndexBizModel 有 24 个自定义方法，但 INopCodeIndexBiz 仅 extends ICrudBiz，无任何自定义方法声明。接口作为公共 API 契约的作用丧失。
- **风险**: 外部调用者无法通过接口类型发现这些方法，需要强制类型转换。
- **建议**: 至少对 NopCodeIndexBizModel、NopCodeSymbolBizModel、NopCodeFileBizModel 补全接口声明。
- **信心水平**: 高
- **误报排除**: 运行时功能不受影响（GraphQL 通过反射发现），但接口契约完整性是真实问题。
- **复核状态**: 未复核

### [维度03-02] ICodeIndexService 存在多个未被 BizModel 调用的方法

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:31-39, 84, 67-68, 166, 146-156`
- **严重程度**: P3
- **现状**: 约 10 个方法未被任何 BizModel 调用（getFiles, getFileSourceCode, getFileSymbols, getFileTypes, getTypeOutline, findSymbols 非分页版本, getIndexIds, batchSaveFileRecords 等）。
- **风险**: 死代码膨胀 API 接口。
- **建议**: 确认这些方法是内部辅助方法还是预留 API，分别处理。
- **信心水平**: 高
- **误报排除**: 经 grep 确认未被 BizModel 调用。部分可能被测试使用。
- **复核状态**: 未复核

### [维度03-03] CodeIndexService.getIndexStats 内部使用 Map<String, Object> 非类型安全

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:505-512`
- **证据片段**:
  ```java
  List<Map<String, Object>> kindResults = symbolDao.selectFieldsByQuery(kindQuery);
  for (Map<String, Object> row : kindResults) {
      String kind = row.get("kind") != null ? row.get("kind").toString() : "UNKNOWN";
  ```
- **严重程度**: P3
- **现状**: ORM 层 selectFieldsByQuery 返回 Map，通过字符串 key 访问。此为内部实现，不暴露到 API 层。
- **风险**: ORM 字段名变更时不会编译报错。
- **建议**: 低优先级，考虑使用常量定义字段名。
- **信心水平**: 高
- **误报排除**: Nop 框架 ORM 层的标准 API，外层有 DTO 做类型转换。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度03-01] | P2 | INopCodeIndexBiz.java 等 | I*Biz 接口未声明 BizModel 自定义方法 |
| [维度03-02] | P3 | ICodeIndexService.java | 约 10 个方法未被 BizModel 调用 |
| [维度03-03] | P3 | CodeIndexService.java | getIndexStats 使用 Map<String, Object> |
