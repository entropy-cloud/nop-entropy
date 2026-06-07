# 维度 16-21 合并审计报告 — nop-code 模块

---

## 维度 16：测试覆盖与质量

### [维度16-01] NopCodeConfigs 和 NopCodeConstants 空接口无测试覆盖（死代码）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConfigs.java`（全文5行）, `NopCodeConstants.java`（全文5行）
- **证据片段**:
  ```java
  public interface NopCodeConfigs{
  }
  ```
- **严重程度**: P3
- **现状**: 空接口，全项目无任何代码引用。
- **建议**: 删除死代码。
- **信心水平**: 高（95%）
- **误报排除**: 经全局搜索确认无引用。
- **复核状态**: 未复核

---

## 维度 17：代码风格与规范

### [维度17-01] CodeIndexService import 行合并了两个 import 在一行

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:6`
- **证据片段**:
  ```java
  import java.util.concurrent.ConcurrentHashMap;import java.util.concurrent.locks.ReentrantLock;
  ```
- **严重程度**: P2
- **现状**: 两个 import 语句被合并到了同一行。
- **建议**: 拆分为两行。
- **信心水平**: 高
- **误报排除**: 不适用。
- **复核状态**: 未复核

### [维度17-02] CodeIndexService import 顺序混乱

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:72-91`
- **严重程度**: P2
- **现状**: io.nop.api/dao/orm 子包穿插交替而非严格按子包分组。
- **建议**: 按规范严格排序。
- **复核状态**: 未复核

### [维度17-03] CodeIndexService 冗余 import（通配符+显式重复）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:34-38`
- **证据片段**:
  ```java
  import io.nop.code.core.model.*;
  import io.nop.code.core.model.HeuristicContext; // 冗余
  import io.nop.code.core.model.IHeuristicEdgeSynthesizer; // 冗余
  ```
- **严重程度**: P2
- **建议**: 删除冗余显式 import 或改为全部使用显式 import。
- **复核状态**: 未复核

### [维度17-04] CodeIndexService 重复注释块

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:275-284`
- **严重程度**: P2
- **现状**: "Rebuild-from-DB Helpers" 分隔注释块出现了两次。
- **建议**: 删除重复分隔注释。
- **复核状态**: 未复核

---

## 维度 18：文档-代码一致性

### [维度18-01] docs-for-ai 中缺少 nop-code 模块使用文档

- **文件**: `docs-for-ai/` 目录全局
- **严重程度**: P2
- **现状**: nop-code 模块（13子模块、~267 Java文件、11 BizModel）在 docs-for-ai/ 中无任何使用文档。
- **风险**: 新开发者或 AI 代理修改 nop-code 时缺乏导航信息。
- **建议**: 创建 nop-code 使用指南，覆盖 API 列表、索引流程、配置说明。
- **信心水平**: 高
- **误报排除**: 不适用。
- **复核状态**: 未复核

---

## 维度 19：命名与术语一致性

### [维度19-01] detectIndexLanguage 返回 "Java" 而非 ORM dict 定义的 "JAVA"

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1027-1044`
- **证据片段**:
  ```java
  private String detectIndexLanguage(ProjectAnalysisResult result) {
      if (result.getFileResults() == null || result.getFileResults().isEmpty()) {
          return "Java";        // ← 应为 "JAVA"
      }
      ...
      return "MIXED";
  }
  ```
  对比 ORM dict:
  ```xml
  <dict label="编程语言" name="code/language" valueType="string">
      <option code="JAVA" label="Java" value="10"/>
  ```
- **严重程度**: P1
- **现状**: `detectIndexLanguage()` 在默认/空场景返回 `"Java"`（PascalCase），但 ORM 模型的 `code/language` 字典定义了 `"JAVA"`（UPPER_CASE）。
- **风险**: 数据库中可能同时存在 `"JAVA"` 和 `"Java"` 两种值。按语言过滤的逻辑使用不同大小写会导致过滤失败。
- **建议**: 统一使用 `CodeLanguage.JAVA.name()` 即 `"JAVA"` 作为默认值。
- **信心水平**: 高（95%）
- **误报排除**: `CodeLanguage` 枚举和 ORM dict 都明确使用 `"JAVA"`。
- **复核状态**: 未复核

### [维度19-02] NopCodeSemanticEdge 的 confidence 存 int 值而非枚举名称

- **文件**: `nop-code/model/nop-code.orm.xml:914-915`
- **证据片段**:
  ```xml
  <column code="CONFIDENCE" displayName="置信度级别" mandatory="true" name="confidence"
          propId="7" stdDataType="int" stdSqlType="INTEGER"/>
  ```
- **严重程度**: P2
- **现状**: `confidence` 字段存储 `EdgeConfidence.getValue()` 的 int 值，没有关联 dict。其他语义字段都通过 `ext:dict` 关联了枚举字典。
- **风险**: 数据库中其他字段存枚举名称（如 `"CLASS"`），而 `confidence` 存数字（如 `20`），查询和调试时需要反向映射。
- **建议**: 改为存储枚举名称字符串并关联 dict，或在 ORM comment 中明确说明。
- **信心水平**: 高
- **误报排除**: 不适用。
- **复核状态**: 未复核

---

## 维度 20：跨模块契约一致性

### [维度20-01] ICodeIndexService 接口膨胀（35+ 方法）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`
- **严重程度**: P2
- **现状**: 接口承担了索引管理、文件查询、符号查询、类型查询、层级查询、图分析、流分析、增量索引、批量操作等 9 个不同职责领域。
- **风险**: 任何消费者只需一小部分功能却被迫依赖全部方法签名。
- **建议**: 拆分为多个细粒度接口。
- **信心水平**: 中（75%）
- **复核状态**: 未复核

### [维度20-02] BizModel 间 indexId 传递行为不一致

- **文件**: `NopCodeSymbolBizModel.java:52-54`, `NopCodeFileBizModel.java:36-40`
- **严重程度**: P3
- **现状**: 几乎每个 @BizQuery 方法都需要 indexId 参数，但无效 indexId 的处理不一致（返回 null/emptyList/零值 DTO）。
- **建议**: 统一处理无效 indexId（抛异常或统一返回）。
- **复核状态**: 未复核

---

## 维度 21：单元测试有效性

### [维度21-01] TestNopCodeFlowBizModel 三个测试只检查 assertNotNull（P-5 反模式）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java:66-107`
- **证据片段**:
  ```java
  List<Map<String, Object>> flows = (List<Map<String, Object>>) response.getData();
  assertNotNull(flows);  // 无法区分正确实现和空实现
  ```
- **严重程度**: P1
- **现状**: 三个测试方法（testDetectFlows, testListFlows, testDetectDeadCode）核心断言全部是 `assertNotNull`。把实现改为始终返回空列表，测试仍然通过。
- **反模式命中**: P-5（过度使用 assertNotNull）, P-6（方法名不表达预期）
- **建议**: 增加对 flows 内容的验证。
- **信心水平**: 高
- **误报排除**: 不适用。
- **复核状态**: 未复核

### [维度21-02] TestNopCodeIndexBizModel 仅 2 个测试覆盖 16+ 方法

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeIndexBizModel.java`
- **严重程度**: P2
- **现状**: NopCodeIndexBizModel 暴露了 16+ 个 @BizQuery/@BizMutation，但只有 testIndexDirectory 和 testGetStats 有测试。
- **反模式命中**: P-3（只测 Happy Path）
- **建议**: 至少为 deleteIndex、detectCommunities、exportGraph 增加基本测试。
- **复核状态**: 未复核

### [维度21-03] TestNopCodeSymbolBizModel.testFindReferencedBy 只检查 assertNotNull

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeSymbolBizModel.java:256-261`
- **严重程度**: P2
- **建议**: 添加内容验证。
- **复核状态**: 未复核

### [维度21-04] TestNopCodeSymbolBizModel.testModuleDigest 包含不可达分支

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeSymbolBizModel.java:135,241`
- **严重程度**: P2
- **现状**: 两个连续的 `else if (responseData instanceof List)` 分支，第二个永远不会执行。
- **建议**: 去掉 instanceof 分支判断，直接按实际返回类型编写断言。
- **复核状态**: 未复核

### [维度21-05] 测试中 rpcQuery/rpcMutation 辅助方法跨 5 个文件重复定义

- **文件**: 5 个测试文件
- **严重程度**: P3
- **建议**: 抽取到公共基类或测试工具类。
- **复核状态**: 未复核
