# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] CodeIndexService import 分组违反约定

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:84-95`
- **严重程度**: P3
- **现状**: `io.nop.*` 组内部存在乱序（commons/core 插在 code/dao 之间，api 出现在 dao/orm 之后）。
- **建议**: 重新排序为 `io.nop.api` -> `io.nop.code` -> `io.nop.commons` -> `io.nop.core` -> `io.nop.dao` -> `io.nop.orm` -> `io.nop.search`。
- **复核状态**: 未复核

### [维度17-02] 8 个 CRUD BizModel 类声明缺少空格

- **文件**: NopCodeUsageBizModel.java 等 8 个文件
- **证据片段**:
  ```java
  public class NopCodeUsageBizModel extends CrudBizModel<NopCodeUsage> implements INopCodeUsageBiz{
  ```
- **严重程度**: P3
- **现状**: `{` 前缺少空格。
- **建议**: 添加空格。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度17-01] | P3 | CodeIndexService.java | import 分组乱序 |
| [维度17-02] | P3 | 8 个 CRUD BizModel | 类声明缺少空格 |
