# 维度 21：单元测试有效性

## 审计日期
2026-06-06

## 第 1 轮（初审）

### [维度21-01] P-1 反模式：TestEdgeProvenance 中纯 getter/setter 测试

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/model/TestEdgeProvenance.java`
- **证据片段**:
  ```java
  @Test
  void testCodeSymbolFilePathAndLanguage() {
      CodeSymbol sym = new CodeSymbol();
      sym.setFilePath("src/main/java/Foo.java");
      assertEquals("src/main/java/Foo.java", sym.getFilePath());
  }
  ```
- **严重程度**: P3（命中 P-1：纯 getter/setter 往返测试）
- **现状**: 验证 Java 赋值工作而非业务逻辑。
- **建议**: 替换为验证业务行为的测试。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-02] P-2 反模式：TestEdgeProvenance.testAllValues 测试枚举元数据

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/model/TestEdgeProvenance.java:14-21`
- **严重程度**: P3（命中 P-2：测试元数据属性而非行为）
- **现状**: 测试枚举成员数量和 not-null，不测试行为。
- **建议**: 替换为序列化往返测试。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-03] P-5 反模式：服务集成测试过度使用 assertNotNull

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java:68-106`
- **严重程度**: P2（命中 P-5：过度使用 assertNotNull）
- **现状**: 多个服务测试仅验证响应非 null，不验证内容。
- **风险**: 返回空列表而非检测到的流，测试仍通过。
- **建议**: 添加内容断言。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度21-04] P-6 反模式：测试方法名不表达预期行为

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java`
- **严重程度**: P3（命中 P-6：方法名不表达意图）
- **现状**: 名如 testDetectFlows_returnsFlowList 描述返回类型而非预期行为。
- **建议**: 使用如 testDetectFlows_findsFlowsInProjectWithMethodCalls 的名称。
- **信心水平**: 可能
- **复核状态**: 未复核

### [维度21-05] P-7 反模式：测试间隐式依赖

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java:36-48`
- **严重程度**: P3（命中 P-7：测试间隐式依赖）
- **现状**: testListFlows 依赖 @BeforeEach 索引和自身 detectFlows 变更。
- **建议**: 低优先级，测试方法内自包含。
- **信心水平**: 可能
- **复核状态**: 未复核

### [维度21-06] P-4 反模式：Map<String,Object> 降低测试有效性

- **文件**: 多个测试文件（TestNopCodeFlowBizModel, TestPhase1BugFixes, TestNopSearchIntegration）
- **严重程度**: P2（命中 P-4：测试与无类型结构高度耦合）
- **现状**: 所有 GraphQL 测试使用 Map<String,Object> 加未检查转换验证响应。
- **风险**: 字段重命名导致测试静默通过（null 断言）。
- **建议**: 使用类型化 DTO 或添加显式 null 检查。
- **信心水平**: 确定
- **复核状态**: 未复核
