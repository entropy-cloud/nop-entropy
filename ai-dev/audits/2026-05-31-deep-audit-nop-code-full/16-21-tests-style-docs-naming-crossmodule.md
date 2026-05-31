# 维度16-21：测试/风格/文档/命名/跨模块/测试有效性

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 维度16：测试覆盖与质量

### [维度16-01] TestNopCodeFlowBizModel 使用 assumeTrue 静默跳过核心测试

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java:73-74,90-91,102-103`
- **证据片段**:
  ```java
  org.junit.jupiter.api.Assumptions.assumeTrue(response.isOk(),
      "detectFlows BizModel action not registered in test context, skipping");
  ```
- **严重程度**: P1
- **现状**: 3 个测试方法在关键断言前调用 assumeTrue，BizModel action 未注册时测试直接跳过（绿色）。同文件 TestNopSearchIntegration 有同样问题。
- **风险**: 可能在 CI 中永远被跳过，掩盖注册失败或接口变更。
- **建议**: 改为 assertTrue 或确保 BizModel 注册。
- **信心水平**: 高
- **误报排除**: assumeTrue 导致跳过的测试不是有效测试。
- **复核状态**: 未复核

---

### [维度16-02] rpcQuery/rpcMutation 在 7 个测试类中重复

- **文件**: 7 个 Test*.java 文件
- **严重程度**: P2
- **现状**: 7 个测试类各自包含相同的 rpcQuery/rpcMutation 方法（共约 210 行重复）。
- **建议**: 提取为测试基类或工具方法。
- **信心水平**: 高
- **复核状态**: 未复核

---

## 维度17：代码风格与规范

### [维度17-01] CodeIndexService.java 两个 import 语句合并在同一行

- **文件**: `CodeIndexService.java:7`
- **证据片段**: `import java.util.concurrent.ConcurrentHashMap;import java.util.concurrent.locks.ReentrantLock;`
- **严重程度**: P2
- **建议**: 拆为两行。
- **信心水平**: 高
- **复核状态**: 未复核

---

### [维度17-02] CodeGraphService.java 通配符 import 与显式 import 混用

- **文件**: `CodeGraphService.java:3-8`
- **证据片段**:
  ```java
  import java.util.*;            // wildcard
  import java.util.ArrayDeque;   // explicit
  ```
- **严重程度**: P2
- **建议**: 删除通配符 import。
- **信心水平**: 高
- **复核状态**: 未复核

---

## 维度18：文档-代码一致性

### [维度18-01] docs-for-ai/ 中无 nop-code 专项文档

- **文件**: `docs-for-ai/`（无 nop-code 相关文档）
- **严重程度**: P2
- **现状**: nop-code 模块（12 子模块、1570 行核心服务、40+ API）在 docs-for-ai/ 中无使用指南。
- **风险**: 新开发者/AI 无法通过文档路由机制了解 nop-code。
- **建议**: 新增 `docs-for-ai/02-core-guides/nop-code-index.md`。
- **信心水平**: 高
- **复核状态**: 未复核

---

## 维度19：命名与术语一致性

### [维度19-01] ErrorCode 命名前缀不一致

- **文件**: `NopCodeErrors.java:12-16`
- **证据片段**: `ERR_NO_ANALYZER_FOR_FILE`（无前缀）vs `ERR_CODE_INVALID_PATH`（有前缀）
- **严重程度**: P3
- **建议**: 统一为 `ERR_CODE_` 前缀。
- **信心水平**: 高
- **复核状态**: 未复核

---

## 维度20：跨模块契约一致性

### [维度20-01] ICodeIndexService 接口方法 51 个

- **文件**: `ICodeIndexService.java:1-177`
- **严重程度**: P2
- **现状**: 无外部消费者，风险可控。
- **建议**: 按领域拆分为窄接口。
- **信心水平**: 高
- **复核状态**: 未复核

---

## 维度21：单元测试有效性

### [维度21-01] TestNopCodeAnalysisBizModel 中 assertNotNull 不验证业务语义

- **文件**: `TestNopCodeAnalysisBizModel.java:91-99`
- **证据片段**: `assertNotNull(godNodes)` 后无内容验证
- **严重程度**: P2
- **现状**: 算法返回空列表时测试仍通过。
- **建议**: 至少断言 godNodes 结构或 extractedCount + inferredCount > 0。
- **信心水平**: 高
- **复核状态**: 未复核

---

### [维度21-02] TestNopCodeSymbolBizModel.moduleDigest 存在死代码分支

- **文件**: `TestNopCodeSymbolBizModel.java:119-141`
- **证据片段**:
  ```java
  if (responseData instanceof List) { ... }
  else if (responseData instanceof List) { ... } // 死代码
  ```
- **严重程度**: P2
- **现状**: 完全重复的 instanceof 条件，第二个分支永不执行。
- **建议**: 删除死代码或修复条件判断。
- **信心水平**: 高
- **复核状态**: 未复核
