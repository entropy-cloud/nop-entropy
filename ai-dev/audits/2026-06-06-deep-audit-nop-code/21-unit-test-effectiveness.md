# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestSecurityFixes 测试的是复制代码而非生产代码（命中 P-4 反向变体）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/impl/TestSecurityFixes.java:11-26`
- **证据片段**:
  ```java
  class TestSecurityFixes {
      // 复制了 CodeSearchService.globToRegex 的实现
      private String globToRegex(String glob) { /* identical */ }
      
      @Test
      void testFilterByFilePattern_noReDoS() {
          String regex = globToRegex(filePattern); // 调用的是测试自己的复制版本
      }
  }
  ```
- **严重程度**: P2
- **现状**: 测试文件完整复制被测方法的实现，测试复制版本。生产代码修改时测试不会感知。
- **风险**: 生产代码 glob 转换行为退化时测试无法提供回归保护。
- **建议**: 将 globToRegex 提取为公共方法后测试直接调用。
- **信心水平**: 高
- **误报排除**: 已确认是手动复制而非 import 引用。
- **复核状态**: 未复核

### [维度21-02] TestServiceLayerErrorPaths 仅验证 ErrorCode 字符串，不验证异常传播路径（命中 P-2）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/impl/TestServiceLayerErrorPaths.java:1-52`
- **证据片段**:
  ```java
  @Test
  void testNopCodeErrors_invalidPath_hasCorrectCode() {
      NopException ex = new NopException(NopCodeErrors.ERR_CODE_INVALID_PATH)
              .param("path", "../etc/passwd");
      assertEquals("nop.err.code.invalid-path", ex.getErrorCode());
  }
  // 6 个方法全部是同一模式：手动构造异常 → 断言 error code 字符串
  ```
- **严重程度**: P2
- **现状**: 6 个测试方法全部手动构造 NopException 然后断言 ErrorCode 字符串值，不验证异常在实际业务流程中的传播。
- **风险**: 如果 CodeIndexService.validatePath() 被修改为不再抛出此异常，测试仍然通过。
- **建议**: 补充集成级测试通过公开方法触发实际异常路径。
- **信心水平**: 高
- **误报排除**: TestBizModelErrorPaths 已提供部分 GraphQL 级测试，但与 error code 级测试互补。
- **复核状态**: 未复核

### [维度21-03] TestEdgeProvenance 中包含多个与 EdgeProvenance 无关的测试（命中 P-6）

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/model/TestEdgeProvenance.java:23-31`
- **证据片段**:
  ```java
  @Test
  void testExportedModifier() {  // 方法名暗示测试 exported modifier
      CodeSymbol sym = new CodeSymbol();        // 但实际测试 CodeSymbol
      assertFalse(sym.isExportedFlag());         // 的新增属性
  }
  ```
- **严重程度**: P3
- **现状**: testExportedModifier 等多个测试方法位于 TestEdgeProvenance 中但与 EdgeProvenance 无关。
- **风险**: 开发者寻找相关测试时不会查看此文件。
- **建议**: 将这些测试移至对应的测试文件。
- **信心水平**: 高
- **误报排除**: 已确认这些方法与 EdgeProvenance 枚举无关。
- **复核状态**: 未复核
