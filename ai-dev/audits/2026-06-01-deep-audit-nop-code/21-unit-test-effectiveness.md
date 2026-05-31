# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestDocKeywordExtractor.testSymbolLimitPreventsExplosion 断言与预期矛盾

- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/semantic/TestDocKeywordExtractor.java:69-83`
- **证据片段**:
  ```java
  @Test
  void testSymbolLimitPreventsExplosion() {
      int count = 100;
      // ...
      assertEquals(count * (count - 1) / 2, edges.size(), "All symbols share same docs, should produce all-pairs edges");
  }
  ```
- **严重程度**: P2
- **现状**: 测试名称是"limitPreventsExplosion"但断言全量计算（4950 条边）。下一个测试 testTruncationAboveMaxSymbols 断言 isEmpty 是因为关键词阈值而非数量截断。两个测试合起来未验证 MAX_SYMBOLS 截断。
- **风险**: 截断逻辑如果有 bug 也不会被发现。
- **建议**: 澄清测试意图，补充验证 MAX_SYMBOLS 截断的测试。
- **信心水平**: 确定
- **误报排除**: 测试名称与断言语义矛盾。
- **复核状态**: 未复核

### [维度21-02] TestNopCodeSymbolBizModel.testFindReferencedBy 只做 assertNotNull（反模式 P-5）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeSymbolBizModel.java:256-261`
- **证据片段**:
  ```java
  @Test
  void testFindReferencedBy() {
      List<ReferenceDTO> refs = codeIndexService.findReferencedBy("test",
              "com.example.domain.User", null, 50);
      assertNotNull(refs);
  }
  ```
- **严重程度**: P3
- **现状**: User 类被 UserService 使用、被 AdminUser 继承，应有引用结果。但测试仅 assertNotNull，空列表也通过。
- **建议**: 断言至少 1 条引用并验证来源。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-5（过度使用 assertNotNull）。
- **复核状态**: 未复核

### [维度21-03] TestNopSearchIntegration 使用 assumeTrue 跳过验证

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopSearchIntegration.java:96-108`
- **证据片段**:
  ```java
  @Test
  void testSearchEmptyQuery_doesNotThrow() {
      // ...
      org.junit.jupiter.api.Assumptions.assumeTrue(response.isOk(), ...);
  }
  ```
- **严重程度**: P3
- **现状**: assumeTrue 导致服务端拒绝空查询时测试静默跳过（绿色通过），不报告行为。
- **建议**: 改为 assert 或使用 assertDoesNotThrow。
- **信心水平**: 确定
- **误报排除**: 测试本意是验证"不抛异常"但实际跳过了验证。
- **复核状态**: 未复核

### [维度21-04] TestIncrementalDetector.testFileFingerprintDefaults 纯 getter/setter 往返（反模式 P-1）

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/incremental/TestIncrementalDetector.java:46-62`
- **严重程度**: P3
- **现状**: setXxx 然后 getXxx 验证赋值语义，@DataBean 的 getter/setter 由编译器保证。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-1。
- **复核状态**: 未复核

### [维度21-05] TestImpactAnalyzer.testImpactConfig 纯 getter/setter 往返（反模式 P-1）

- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/impact/TestImpactAnalyzer.java:143-152`
- **严重程度**: P3
- **现状**: 断言默认值（与实现常量耦合）再 set/get 验证。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-1 + P-4。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 21-01 | P2 | TestDocKeywordExtractor.java:69-83 | 测试名称与断言矛盾，未验证 MAX_SYMBOLS 截断 |
| 21-02 | P3 | TestNopCodeSymbolBizModel.java:256-261 | 只做 assertNotNull（反模式 P-5） |
| 21-03 | P3 | TestNopSearchIntegration.java:96-108 | assumeTrue 跳过验证 |
| 21-04 | P3 | TestIncrementalDetector.java:46-62 | 纯 getter/setter 往返（反模式 P-1） |
| 21-05 | P3 | TestImpactAnalyzer.java:143-152 | 纯 getter/setter 往返（反模式 P-1） |
