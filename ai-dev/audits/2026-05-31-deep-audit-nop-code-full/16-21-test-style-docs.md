# Dimensions 16-21: Test, Style, Docs, Naming, Cross-Module, Test Effectiveness

## Module: nop-code

---

## Dimension 16: Test Coverage and Quality

### [维度16-01] TestCacheEviction 包含空洞断言 assertTrue(true)
- **文件**: `nop-code/nop-code-flow/src/test/java/io/nop/code/flow/TestCacheEviction.java:25`
- **证据片段**:
  ```java
  @Test
  void testFlowDetectorEvictionAfterMaxEntries() {
      FlowDetector detector = new FlowDetector();
      for (int i = 0; i < 25; i++) {
          detector.invalidateCache("idx_" + i);
      }
      assertTrue(true, "Multiple invalidate calls should not throw");
  }
  ```
- **严重程度**: P2
- **现状**: `assertTrue(true)` 是空洞断言，即使方法崩溃也能通过。无法检测缓存驱逐逻辑是否正确。
- **建议**: 验证缓存内容是否被驱逐。
- **信心水平**: 确定
- **误报排除**: 命中反模式 P-5（过度使用 assertNotNull 类）。
- **复核状态**: 未复核

---

## Dimension 17: Code Style

### [维度17-01] CodeIndexService.java 导入顺序混乱
- **文件**: `CodeIndexService.java:3-85`
- **证据片段**: 86行导入中 io.nop.* 包分组未按字母顺序维护，io.nop.commons 出现两次。
- **严重程度**: P2
- **建议**: 按 AGENTS.md 规范重新排序。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度17-02] NopCodeConstants 和 NopCodeConfigs 为空接口
- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConstants.java:1-5`
- **严重程度**: P3
- **现状**: 完全空的接口，无引用，属于死代码。
- **建议**: 删除或添加注释说明保留原因。
- **信心水平**: 确定
- **复核状态**: 未复核

---

## Dimension 18: Documentation-Code Consistency

### [维度18-01] ORM 字典状态值与代码实际使用不匹配
- **文件**: `nop-code/model/nop-code.orm.xml:64-70` vs `CodeIndexService.java:736,834,1391`
- **证据片段**:
  ```xml
  <dict name="code/index_status">
      <option code="CREATED" value="10"/>
      <option code="INDEXING" value="20"/>
      <option code="READY" value="30"/>
      <option code="ERROR" value="40"/>
  </dict>
  ```
  ```java
  indexEntity.setStatus("COMPLETED");  // 不在字典中
  flowEntity.setStatus("DETECTED");    // 不在字典中
  ```
- **严重程度**: P1
- **现状**: 代码使用 "COMPLETED" 和 "DETECTED" 但字典只定义了 CREATED/INDEXING/READY/ERROR。
- **风险**: 前端下拉菜单和验证规则无法识别这些状态值。
- **建议**: 更新字典定义或代码使用一致。
- **信心水平**: 确定
- **误报排除**: 字符串 "COMPLETED" 和 "DETECTED" 从未出现在任何字典定义中，可 grep 验证。
- **复核状态**: 未复核

---

## Dimension 19: Naming Consistency

No issues found. Entity names consistent across ORM/BizModel/interfaces/DTOs. Field naming consistent.

---

## Dimension 20: Cross-Module Contract Consistency

### [维度20-01] nop-code-api 模块为空，ICodeIndexService 在 service 模块中
- **文件**: `nop-code/nop-code-api/pom.xml` vs `ICodeIndexService.java`
- **严重程度**: P2 (已记录在维度01-01/01-03，此处确认为跨模块契约问题)
- **现状**: 外部消费者必须依赖 nop-code-service + nop-code-core + nop-code-flow 的全部传递依赖才能使用 ICodeIndexService。
- **复核状态**: 未复核

---

## Dimension 21: Unit Test Effectiveness

### [维度21-01] TestCohesionConsistency 通过反射测试私有方法 (P-4)
- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/community/TestCohesionConsistency.java:59-61`
- **严重程度**: P2
- **信心水平**: 确定

### [维度21-02] TestDeadCodeDetector 中过度使用 assertNotNull (P-5)
- **文件**: `nop-code/nop-code-flow/src/test/java/io/nop/code/flow/TestDeadCodeDetector.java:49-51`
- **严重程度**: P3
- **信心水平**: 很可能

### [维度21-03] TestImpactAnalyzer 中 testImpactConfig 是纯 getter/setter 往返测试 (P-1)
- **文件**: `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/impact/TestImpactAnalyzer.java:143-152`
- **严重程度**: P3
- **信心水平**: 确定
