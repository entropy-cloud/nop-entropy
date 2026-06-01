# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] 集成测试套件整体被 @Disabled，10 个真实场景测试永远不执行

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestIndexNopEntropyProject.java:37`
- **证据片段**:
  ```java
  @Disabled("手动启用：索引整个 nop-entropy 项目耗时约 30 秒")
  ```
- **严重程度**: P1
- **现状**: 10 个端到端测试方法被 @Disabled 跳过，CI 中永不执行。覆盖了唯一真实大型项目场景。
- **风险**: 核心查询功能在真实规模数据上的回归完全不受保护。
- **建议**: 拆分为 smoke test（CI 可运行）和性能基准测试。
- **信心水平**: 确定
- **误报排除**: @Disabled 在 JUnit 5 中完全跳过测试。
- **复核状态**: 未复核

### [维度16-02] NopCodeIndexBizModel 14/24 方法无测试覆盖

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: 43, 83, 99, 109, 169, 181, 211, 219, 227, 236, 245, 251, 261, 269
- **证据**: findCycles、getReverseDeps、getCriticalNodes、exportGraph、diffGraph 等 14 个方法无任何测试。
- **严重程度**: P1
- **现状**: 最大 BizModel 的 58% 方法无直接测试。
- **风险**: 图分析/依赖核心功能回归风险极高。
- **建议**: 优先补充 findCycles、getReverseDeps、indexFile、exportGraph 测试。
- **信心水平**: 确定
- **误报排除**: grep 确认测试文件中不存在这些方法名。
- **复核状态**: 未复核

### [维度16-03] NopCodeSymbolBizModel 6 个方法/Loader 无测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java`
- **行号**: 50, 101, 116, 187, 226, 236
- **严重程度**: P2
- **现状**: getBySymbolId、usages、sourceCode、fileOutline、findByAnnotation、findImplementations 无测试。
- **建议**: 至少为 findByAnnotation 和 findImplementations 补充 happy path 测试。
- **信心水平**: 确定
- **误报排除**: grep 确认。
- **复核状态**: 未复核

### [维度16-04] 错误路径覆盖极弱，6 个 ErrorCode 均无测试触发

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java:12-29`
- **证据**: 整个 service 模块 94 个测试中，0 处 assertThrows。
- **严重程度**: P1
- **现状**: null indexId、非法路径、索引不存在目录等错误路径完全无覆盖。
- **风险**: 安全校验、资源清理、ErrorCode 可能变成死代码。
- **建议**: 新建 TestNopCodeBizModelErrorPaths 覆盖基本错误路径。
- **信心水平**: 确定
- **误报排除**: grep assertThrows 无命中。
- **复核状态**: 未复核

### [维度16-05] 精确计数断言导致测试脆弱

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeIndexBizModel.java:47`
- **证据片段**:
  ```java
  assertEquals(6, count, "Should index exactly 6 Java files in test-project, got " + count);
  ```
- **严重程度**: P3
- **现状**: 断言依赖 test-project 目录恰好 6 个 .java 文件。
- **建议**: 改为 assertTrue(count >= 6)。
- **信心水平**: 确定
- **误报排除**: 确认当前有 6 个文件。
- **复核状态**: 未复核

### [维度16-06] testDetectDeadCode 仅 assertNotNull，对核心逻辑无约束

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java:96-106`
- **严重程度**: P2
- **现状**: 集成测试只验证返回非 null，不验证 deadSymbols/stats 等关键字段。
- **建议**: 至少断言 deadSymbols 键存在且 stats.total >= 0。
- **信心水平**: 很可能
- **误报排除**: DeadCodeDetector 单元测试充分，但集成层缺少断言。
- **复核状态**: 未复核

### [维度16-07] evictStatusMap 非确定性行为无测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:335-342`
- **严重程度**: P2
- **现状**: ConcurrentHashMap 的随机淘汰策略和 getIncrementalStatus 方法均无测试。
- **建议**: 补充边界条件测试（>20 条、已淘汰返回 null）。
- **信心水平**: 很可能
- **误报排除**: 无。
- **复核状态**: 未复核

### [维度16-08] 5 个 service 层测试未使用 JunitAutoTestCase（合理设计）

- **严重程度**: INFO（正面）
- **现状**: 纯单元测试不依赖 IoC 容器，使用纯 JUnit 5 合理。

### [维度16-09] 重复 instanceof 分支导致死代码测试

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeSymbolBizModel.java:120-141`
- **严重程度**: P3
- **现状**: 两个 `instanceof List` 分支，第二个永远不执行。
- **建议**: 移除死代码分支。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核

### 正面发现

- TestIncrementalDetector (446行, 17方法) 覆盖优秀，可作为标杆
- 底层单元测试（core/flow/graph/lang）质量较好，共 268 个测试方法
