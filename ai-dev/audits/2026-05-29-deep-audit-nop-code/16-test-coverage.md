# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] NopCodeIndexBizModel 多个核心 BizQuery 方法缺少测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:127-238`
- **证据片段**:
  ```java
  @BizQuery
  public DepGraphDTO getDepGraph(...) { ... }
  @BizQuery
  public GraphDiffDTO diffGraph(...) { ... }
  @BizQuery
  public CriticalNodeResultDTO getCriticalNodes(...) { ... }
  ```
- **严重程度**: P2
- **现状**: 20+ 个 @BizQuery/@BizMutation 方法中，TestNopCodeIndexBizModel 仅测试了 indexDirectory 和 getStats。getDepGraph、findCycles、exportGraph、diffGraph、findDependentFiles、getCriticalNodes、getKnowledgeGaps 等方法无测试。
- **风险**: 图算法集成、依赖分析等核心功能无回归保护。
- **建议**: 为关键方法添加集成测试。
- **信心水平**: 90%
- **误报排除**: 不是"覆盖率数字"问题，是核心业务逻辑无测试保护。
- **复核状态**: 未复核

### [维度16-02] deleteIndex 缺少对数据清理完整性的测试

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1300-1364`
- **证据片段**:
  ```java
  usageDao.batchDeleteEntities(usageDao.findAllByQuery(usageQuery));
  // ... 10 种实体类型 ...
  daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
  ```
- **严重程度**: P2
- **现状**: deleteIndex 删除 10 种实体类型，但无测试验证删除后关联数据确实被清理。
- **建议**: 添加测试验证 deleteIndex 后所有子表数据为零。
- **信心水平**: 85%
- **复核状态**: 未复核

### [维度16-03] CodeIndexService 中多个错误路径无测试覆盖

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:361-373`
- **证据片段**:
  ```java
  if (fileAnalyzer == null) {
      throw new NopException(ERR_NO_ANALYZER_FOR_FILE).param(ARG_FILE_PATH, filePath);
  }
  ```
- **严重程度**: P2
- **现状**: ERR_NO_ANALYZER_FOR_FILE、ERR_INCREMENTAL_FAILED、ERR_CODE_INVALID_PATH 等错误码无测试覆盖。
- **建议**: 添加边界条件测试。
- **信心水平**: 80%
- **复核状态**: 未复核

# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] 测试代码中使用 System.out.println（27 处）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestIndexNopEntropyProject.java:80-279`
- **证据片段**:
  ```java
  System.out.println("\n=== INDEX: " + count + " Java files ===\n");
  System.out.println("  Symbol count: " + stats.getSymbolCount());
  ```
- **严重程度**: P2
- **现状**: TestIndexNopEntropyProject 23 处、TestNopCodeFlowBizModel 3 处、TestNopSearchIntegration 1 处。TestNopCodeFlowBizModel 中有 System.out.println 后 return 的静默跳过模式。
- **建议**: 替换为 LOG.debug() 或删除。
- **信心水平**: 90%
- **误报排除**: TestNopCodeFlowBizModel 的 System.out.println + return 模式降低了测试有效性。
- **复核状态**: 未复核

### [维度17-02] 多个 BizModel 文件构造器大括号前缺少空格

- **文件**: 7 个 BizModel 文件（NopCodeFlowBizModel.java:11-14 等）
- **证据片段**:
  ```java
  public class NopCodeFlowBizModel extends CrudBizModel<NopCodeFlow> implements INopCodeFlowBiz{
      public NopCodeFlowBizModel(){
  ```
- **严重程度**: P3
- **现状**: `class Name{` 和 `Constructor(){` 大括号前缺少空格。出现在 7 个文件中。
- **建议**: 添加空格。
- **信心水平**: 95%
- **复核状态**: 未复核

# 维度 18：文档-代码一致性

## 第 1 轮（初审）

**零实质性发现**。docs-for-ai 中 nop-code 的文档覆盖与代码一致。module-groups.md 正确描述了 nop-code 为 "WIP 实验模块"。

### [维度18-01] ORM dict 状态值与代码实际使用不一致

- **文件**: `nop-code/model/nop-code.orm.xml:64-70`
- **证据片段**:
  ```xml
  <dict name="code/index_status">
      <option code="CREATED" value="10"/>
      <option code="INDEXING" value="20"/>
      <option code="READY" value="30"/>
      <option code="ERROR" value="40"/>
  </dict>
  ```
- **严重程度**: P3
- **现状**: CodeIndexService 使用 INDEXING 和 COMPLETED，但 COMPLETED 不在 dict 中，READY 在 dict 中但从未被代码使用。
- **建议**: 对齐 dict 定义与代码实际状态值。
- **信心水平**: 85%
- **复核状态**: 未复核

# 维度 19：命名与术语一致性

## 第 1 轮（初审）

### [维度19-01] superTypeId 列名与实际存储的 qualifiedName 语义不一致

- **文件**: `nop-code/model/nop-code.orm.xml:517-518` + `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2088`
- **证据片段**:
  ```xml
  <column name="superTypeId" displayName="父类型ID" domain="codeId"/>
  ```
  ```java
  inhEntity.setSuperTypeId(inh.getSuperTypeQualifiedName());
  ```
- **严重程度**: P2
- **现状**: superTypeId 列名暗示存储 ID，实际存储全限定名。annotationTypeId 同类问题。
- **建议**: 改名为 superTypeQualifiedName 或确保存储真正的 ID。
- **信心水平**: 90%
- **误报排除**: 与维度04-01同源问题，但此处关注命名不一致而非关系失效。
- **复核状态**: 未复核

### [维度19-02] 错误码前缀命名风格不统一

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/NopCodeCoreErrors.java` + `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java`
- **证据片段**:
  ```java
  // NopCodeErrors: 有些带 code 前缀，有些不带
  define("nop.err.code.index-directory-failed", ...);
  define("nop.err.code.index-id-required", ...);
  ```
- **严重程度**: P3
- **建议**: 统一命名风格。
- **信心水平**: 80%
- **复核状态**: 未复核

# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

**零发现**。nop-code 对外暴露的接口稳定。NopCodeConfigs/NopCodeConstants 为空（无 @InjectValue 配置项，无配置不一致问题）。CodeIndexApi 与 ICodeIndexService 的差异已在维度01-01中记录。

# 维度 21：单元测试有效性

## 第 1 轮（初审）

### [维度21-01] TestNopCodeFlowBizModel 三个测试在失败时静默跳过，保护力为零（反模式 P-3+P-8）

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java:68-119`
- **证据片段**:
  ```java
  if (!response.isOk()) {
      System.out.println("detectFlows returned status=" + response.getStatus() + ", likely BizModel not registered. Skipping.");
      return;  // 静默跳过
  }
  ```
- **严重程度**: P2
- **现状**: 三个方法（testDetectFlows、testListFlows、testDetectDeadCode）都有此模式。即使功能完全坏掉，测试也通过。
- **建议**: 移除静默跳过，改为 assert response.isOk() 或使用 @Disabled 注解。
- **信心水平**: 95%
- **误报排除**: 按"把核心逻辑改成错误实现后测试是否仍通过"标准，这三个测试保护力为零。
- **复核状态**: 未复核

### [维度21-02] TestCodeSymbol 为纯 Getter/Setter 往返测试（反模式 P-1）

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/model/TestCodeSymbol.java:9-31`
- **证据片段**:
  ```java
  assertFalse(symbol.isDeprecated());  // 测试 Java boolean 默认值
  assertEquals("com.example.User", a.getQualifiedName());  // set 什么 get 什么
  ```
- **严重程度**: P2
- **现状**: 两个方法都是 P-1 反模式。CodeSymbol 是 @DataBean，getter/setter 由框架保证。
- **建议**: 删除这两个低价值测试。
- **信心水平**: 90%
- **复核状态**: 未复核

### [维度21-03] TestCodeAccessModifier 测试常量值排序关系而非行为（反模式 P-4）

- **文件**: `nop-code/nop-code-core/src/test/java/io/nop/code/core/model/TestCodeAccessModifier.java:10-36`
- **证据片段**:
  ```java
  assertEquals(CodeAccessModifier.NO_MODIFIER.getValue(), maxValue);  // 断言常量值
  ```
- **严重程度**: P2
- **现状**: 测试枚举 int 值分配和排序关系，与实现高度耦合。
- **建议**: 通过排序函数行为来测试，而非直接断言数值。
- **信心水平**: 85%
- **复核状态**: 未复核

### [维度21-04] TestNopCodeAnalysisBizModel.testDetectCommunitiesOnEmptyIndex 过度宽容

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeAnalysisBizModel.java:124-136`
- **证据片段**:
  ```java
  if (response.isOk()) { assertNotNull(response.getData()); }
  // If not OK, that's also acceptable
  ```
- **严重程度**: P2
- **现状**: 无论成功或失败都通过，不验证任何具体行为。保护力为零。
- **建议**: 明确断言期望行为（应返回错误还是空结果）。
- **信心水平**: 90%
- **复核状态**: 未复核
