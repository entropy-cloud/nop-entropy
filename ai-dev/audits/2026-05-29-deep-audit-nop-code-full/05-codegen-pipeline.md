# 维度05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] GraphExporter 内联 ErrorCode 定义违反集中管理原则

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java`
- **行号**: L21-22
- **证据片段**:
  ```java
  private static final ErrorCode ERR_GRAPH_EXPORT_FAILED =
      ErrorCode.define("nop.err.code.graph-export-failed", "Graph export failed");
  ```
- **严重程度**: P3
- **现状**: GraphExporter 在类内部直接调用 ErrorCode.define()，而非在模块级 Errors 接口中集中定义。nop-code-core 有 NopCodeCoreErrors，nop-code-service 有 NopCodeErrors，但 nop-code-graph 模块没有。
- **风险**: ErrorCode 散落在业务类中，不利于全局检索和维护。
- **建议**: 在 nop-code-graph 模块创建 NopCodeGraphErrors 接口，将此 ErrorCode 移入其中。
- **信心水平**: 很可能
- **误报排除**: 如果 graph 模块仅此一个 ErrorCode，内联可接受。但与其他模块惯例不一致。
- **复核状态**: 未复核

## 通过项

1. 源模型 model/nop-code.orm.xml 存在且格式正确，11 个实体定义完整
2. codegen 脚本正确引用源模型，使用标准模板路径
3. DAO 产物完整：_app.orm.xml、11 个 Entity、11 个 IBiz 接口
4. meta 产物完整：11 个 xmeta、6 个 dict.yaml、i18n 文件齐全
5. web 产物完整：11 个 view.xml + 5 个自定义页面
6. Maven 插件配置正确
