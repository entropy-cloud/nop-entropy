# 维度 16+21：测试覆盖与质量 + 单元测试有效性

## 维度 16：测试覆盖与质量

### [维度16-01] CodeIndexService（1999行）核心持久化逻辑零直接单元测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 全文（重点 L822-1427 持久化逻辑）
- **证据片段**:
  ```java
  // 核心持久化方法无直接测试覆盖：
  // saveFileResultInSession (~300行): 符号/调用/继承/注解/路由/依赖/用法持久化
  // saveReplacingExisting: 冲突处理逻辑
  // resolveQualifiedNamesToIds: QN→ID解析
  // synthesizeAndPersistHeuristicEdges: 启发式边合成
  ```
- **严重程度**: P1
- **现状**: TestCodeIndexService 实际测试的是 ProjectAnalyzer，不涉及 CodeIndexService 任何方法。
- **风险**: 持久化层 bug（实体重复、ID冲突、级联删除遗漏）只能通过集成测试发现。
- **建议**: 为 saveReplacingExisting、deleteFileRecords、resolveQualifiedNamesToIds 编写单元测试。
- **信心水平**: 确定
- **误报排除**: 不是"测试数量不够"的审美判断——核心服务1999行零直接测试是结构性缺陷。
- **复核状态**: 未复核

### [维度16-02] CodeQueryService（844行）和 CodeSearchService（306行）无直接单元测试

- **严重程度**: P2
- **现状**: 两个核心服务均无独立测试文件，仅通过 BizModel 集成测试间接覆盖。
- **复核状态**: 未复核

### [维度16-03] validatePath/validateLocalPath 安全校验缺少直接单元测试

- **严重程度**: P2
- **现状**: allowedLocalRoot 为 null 时的行为未在单元级验证。
- **复核状态**: 未复核

## 维度 21：单元测试有效性

### [维度21-01] TestFlowDetector.testCriticalityHighWhenNoTestFiles 无断言

- **文件**: `nop-code/nop-code-flow/src/test/java/io/nop/code/flow/TestFlowDetector.java`
- **行号**: 149-192
- **证据片段**:
  ```java
  // 构建了两个场景，计算了 flowsNoTest 和 flowsWithTest
  // 但从未对它们进行任何断言比较
  // 方法在 line 192 处结束，无 assertTrue/assertEquals/assertThat
  ```
- **严重程度**: P1
- **现状**: 构建测试场景但不做断言。将 FlowDetector 关键性计算改为始终返回 0.0，测试仍通过。命中 P-3 反模式（只测 happy path）+ P-5 反模式（过度使用 assertNotNull——此处连 assertNotNull 都没有）。
- **建议**: 添加 assertTrue(flowNoTest.getCriticality() > flowWithTest.getCriticality())。
- **信心水平**: 确定
- **误报排除**: 验证了核心逻辑改为错误实现后测试确实仍通过。
- **复核状态**: 未复核

### [维度21-04] TestBizModelErrorPaths 恒真断言

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestBizModelErrorPaths.java`
- **行号**: 63-72
- **证据片段**:
  ```java
  assertTrue(response.isOk() || !response.isOk(), "..."); // 恒真表达式
  ```
- **严重程度**: P2
- **现状**: response 要么 ok 要么不 ok，断言恒真。命中 P-8 反模式（无效的负面测试）。
- **复核状态**: 未复核

### [维度21-02] TestCodeIndexService 测试名称误导

- **严重程度**: P2
- **现状**: 类名暗示测试 CodeIndexService，实际测试 ProjectAnalyzer。所有10个方法与 CodeIndexService 无关。
- **复核状态**: 未复核

### [维度21-03] TestNopCodeFlowBizModel 测试过于浅薄

- **严重程度**: P2
- **现状**: 3个测试仅验证 response.isOk() 和结果非 null。detectFlows 返回空列表测试仍通过。
- **复核状态**: 未复核

### [维度21-07] TestNopSearchIntegration 使用 assumeTrue 弱化测试

- **严重程度**: P2
- **现状**: 搜索功能返回错误时测试被静默跳过而非报告失败。
- **复核状态**: 未复核

### [维度21-05] TestCodeCacheManager 反射修改 private 字段

- **严重程度**: P2
- **现状**: P-4 反模式（测试与实现高度耦合）。但 TTL 过期行为难以其他方式测试。
- **复核状态**: 未复核

### [维度21-06] 枚举元数据属性测试价值有限

- **严重程度**: P2
- **现状**: TestLanguageFamily/TestCodeAccessModifier/TestCodeSymbolKind 测试枚举排序约束。价值有限但可保留。
- **复核状态**: 未复核
