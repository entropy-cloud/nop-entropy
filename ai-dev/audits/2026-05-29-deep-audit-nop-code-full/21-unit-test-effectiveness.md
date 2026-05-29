# 维度21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestBuildHierarchyCycleProtection 测试 Java 标准库而非项目代码（命中 P-2）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestBuildHierarchyCycleProtection.java`
- **行号**: L10-37
- **证据片段**:
  ```java
  @Test
  void testMaxDepthClampedTo50() {
      int clamped = Math.min(1000, 50);
      assertEquals(50, clamped);
  }
  @Test
  void testVisitedSetBreaksCycle() {
      Set<String> visited = new HashSet<>();
      // ... 测试 HashSet 行为
  }
  ```
- **严重程度**: P2
- **现状**: 测试 Math.min() 和 HashSet 的行为，不调用任何项目代码。把 cycle protection 逻辑删掉也不会导致测试失败。
- **风险**: 给人错误的覆盖率安全感。
- **建议**: 重写测试，实际调用 CodeIndexService 的 hierarchy 方法并构造循环继承数据。
- **信心水平**: 确定
- **误报排除**: 命中 P-2 反模式（测试元数据属性而非行为）。
- **复核状态**: 未复核

### [维度21-02] TestImpactAnalyzer.testImpactConfig 是纯 getter/setter 测试（命中 P-1）

- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/impact/TestImpactAnalyzer.java`
- **行号**: L143-152
- **证据片段**:
  ```java
  @Test
  void testImpactConfig() {
      ImpactAnalyzer.ImpactConfig config = new ImpactAnalyzer.ImpactConfig();
      assertEquals(3, config.getMaxDepth());
      config.setMaxDepth(5);
      assertEquals(5, config.getMaxDepth());
  }
  ```
- **严重程度**: P3
- **现状**: 测试 getter/setter 往返。setter 由 Java 保证正确，测试价值极低。
- **建议**: 在真正使用 config 的行为测试中断言默认值。
- **信心水平**: 确定
- **误报排除**: 命中 P-1 反模式（纯 getter/setter 往返测试）。
- **复核状态**: 未复核

### [维度21-03] 多个集成测试过度使用 assertNotNull 和 assertTrue(x >= 0)（命中 P-5）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeAnalysisBizModel.java`
- **行号**: L67-72, L88-99
- **证据片段**:
  ```java
  assertTrue((Integer) result.get("totalSymbols") >= 0);
  assertNotNull(result.get("averageCohesion"));
  ```
- **严重程度**: P3
- **现状**: 断言几乎不可能失败。
- **建议**: 断言具体的预期值或合理范围。
- **信心水平**: 很可能
- **误报排除**: 命中 P-5 反模式（过度使用 assertNotNull）。
- **复核状态**: 未复核

### [维度21-04] TestCodeIndexService 全部为 happy path 测试（命中 P-3）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestCodeIndexService.java`
- **行号**: L34-143
- **严重程度**: P2
- **现状**: 10 个测试方法全部测试正常路径。无错误路径测试（不存在文件、空文件、语法错误文件、超大文件）。
- **建议**: 增加边界条件测试。
- **信心水平**: 确定
- **误报排除**: 命中 P-3 反模式（只测 happy path）。
- **复核状态**: 未复核

## 整体评估

| 子模块 | 有效测试 | 低价值测试 |
|--------|---------|-----------|
| nop-code-core | ~75% | ~25% |
| nop-code-graph | ~80% | ~20% |
| nop-code-flow | ~70% | ~30% |
| nop-code-service | ~50% | ~50% |
| nop-code-lang-* | ~60% | ~40% |

**整体有效测试比例约 65%**，低价值约 35%。

**最有效的测试文件**: TestPhase1BugFixes, TestOrmRelationNavigation, TestIncrementalIndexWithDb, TestDeadCodeDetector, TestCommunityDetector
