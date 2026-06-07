# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] extractLines 重复实现缺乏测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1838-1850` 和 `CodeQueryService.java:91-103`
- **证据片段**:
  ```java
  // 两处完全相同的实现
  private String extractLines(String source, int startLine, int endLine) {
      if (source == null || startLine < 1 || endLine < startLine) return null;
      String[] lines = source.split("\n", -1);
      int start = Math.max(1, startLine) - 1;
      int end = Math.min(lines.length, endLine);
      if (start >= end) return null;
      ...
  }
  ```
- **严重程度**: P2
- **现状**: extractLines 在 CodeIndexService 和 CodeQueryService 中有完全相同的实现，无单元测试覆盖边界行为。
- **风险**: 任一副本修改边界行为时另一个不会同步更新。
- **建议**: 提取为 SourceCodeUtils.extractLines 工具方法，添加边界条件测试。
- **信心水平**: 高
- **误报排除**: 两处实现完全相同且无测试。
- **复核状态**: 未复核

### [维度16-02] 关系实体 BizModel 缺乏独立测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeUsageBizModel.java` 等
- **严重程度**: P3
- **现状**: 多个关系实体 BizModel 是空壳 CRUD 模型，无测试。核心逻辑在其他 BizModel 中有覆盖。
- **风险**: 低。后续添加自定义逻辑时需同步添加测试。
- **建议**: 添加自定义逻辑时同步添加测试。
- **信心水平**: 高
- **误报排除**: 空壳 BizModel 不测试是合理的。
- **复核状态**: 未复核

### [维度16-03] saveReplacingExisting 方法缺乏单元测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1431-1461`
- **严重程度**: P2
- **现状**: saveReplacingExisting 是持久化核心逻辑的 upsert 实现，通过捕获特定 OrmException 反射式复制属性，复杂且有多个分支，但无直接单元测试。
- **风险**: ORM 层异常码变更或 orm_initedValues 返回值变化时行为可能静默退化。
- **建议**: 编写专项集成测试验证首次 save、重复 save 覆盖更新、属性复制异常时的 fallback。
- **信心水平**: 中高
- **误报排除**: 包含复杂异常处理和反射属性复制，不是简单 CRUD。
- **复核状态**: 未复核
