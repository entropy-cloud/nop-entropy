# 维度 07：BizModel 规范遵循 — nop-code-service 模块

审计范围: 11 个 @BizModel 类、4 个内部辅助类、32 个 DTO 类

## 第 1 轮（初审）

### [维度07-01] IncrementalStatus 内部类缺少 @DataBean 注解

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:259-314`
- **证据片段**:
  ```java
  public static class IncrementalStatus {
      private String indexId;
      private String mode;
      // ...
  }
  ```
  被 `@BizQuery getIncrementalStatus()` 方法作为返回值。
- **严重程度**: P2
- **现状**: IncrementalStatus 作为 @BizQuery 方法的返回类型暴露给 GraphQL，但没有 @DataBean 注解，也未实现 Serializable。
- **风险**: GraphQL 引擎可能无法为该类型生成正确的 schema。
- **建议**: 添加 @DataBean 注解并实现 Serializable，或提取为独立 DTO。
- **信心水平**: 确定
- **误报排除**: 已确认该方法有 @BizQuery 注解且返回此类型。
- **复核状态**: 未复核

### [维度07-02] 4 个辅助类使用 *Service 命名违反平台规范

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:86`, `CodeGraphService.java:33`, `CodeQueryService.java:23`, `CodeSearchService.java:24`
- **证据片段**:
  ```java
  public class CodeIndexService implements ICodeIndexService
  class CodeGraphService
  class CodeQueryService
  class CodeSearchService
  ```
- **严重程度**: P3
- **现状**: 4 个辅助类使用 *Service 后缀命名。service-layer.md 规定"不要在 Nop 模块中创建 *Service 类"。这些类承担的是 Processor 职责。
- **风险**: 可能误导新开发者以为这是 Spring 式 Service 层。
- **建议**: 重命名为 Processor 风格：CodeIndexProcessor, CodeGraphProcessor 等。
- **信心水平**: 确定
- **误报排除**: 这些不是 @BizModel 类，但文档规则是模块级别的。
- **复核状态**: 未复核

### [维度07-03] NopCodeIndexBizModel 方法过多，职责过宽

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:28-325`
- **证据片段**:
  20+ 个 @BizQuery/@BizMutation 方法覆盖：索引管理、图分析、依赖分析、流程检测、变更分析、图谱导出等六大类操作。
- **严重程度**: P2
- **现状**: 单个 BizModel 承担了 6 大类操作，325 行且还在增长。部分方法（如 detectCommunities）操作的对象主要是 Symbol/Call 而非 Index 聚合根。
- **风险**: 可维护性差，不利于独立测试。
- **建议**: 将图谱分析、流程检测等拆为独立 Processor，BizModel 仅保留入口调度。
- **信心水平**: 很可能
- **误报排除**: 文档允许编排入口 BizModel 包含跨实体操作，但当前规模已超出合理范围。
- **复核状态**: 未复核

### [维度07-04] BizModel 方法返回 core 层模型对象（无 xmeta 支持）

- **文件**: `NopCodeFileBizModel.java:35,42`, `NopCodeIndexBizModel.java:191,196,201,215`, `NopCodeSymbolBizModel.java:209`
- **证据片段**:
  ```java
  public CodeFileAnalysisResult getByPath(...)     // core 模型
  public List<ExecutionFlow> detectFlows(...)      // core 模型
  public ChangeAnalysisResult analyzeChanges(...)  // core 模型
  public DeadCodeReport detectDeadCode(...)        // core 模型
  ```
- **严重程度**: P1
- **现状**: 7 个 @BizQuery/@BizMutation 方法直接返回 core 层模型对象，这些类型没有 xmeta 定义。
- **风险**: GraphQL schema 缺失导致请求失败，客户端无法使用 selection 控制返回字段。
- **建议**: 为这些返回类型创建 @DataBean DTO 或在 xmeta 中注册。
- **信心水平**: 确定
- **误报排除**: 这些是 @BizQuery 对外暴露的方法，不是内部服务接口。文档明确区分了这两种场景。
- **复核状态**: 未复核

### [维度07-05] 多参数方法使用多个 @Name 而非 @RequestBean

- **文件**: `NopCodeSymbolBizModel.java:186-196`, `NopCodeIndexBizModel.java:63-67`
- **证据片段**:
  ```java
  searchCode(@Name("indexId")..., @Name("query")..., @Name("searchType")...,
             @Name("language")..., @Name("filePattern")..., @Name("limit")...)  // 6 个 @Name
  ```
- **严重程度**: P3
- **现状**: 多个方法使用 3-6 个 @Name 参数，未使用 @RequestBean。
- **风险**: 可读性和可维护性降低。功能不受影响。
- **建议**: 参数 >= 4 个的方法改用 @RequestBean。
- **信心水平**: 确定
- **误报排除**: 文档建议而非强制要求。
- **复核状态**: 未复核

### [维度07-06] @BizLoader 注册在非 ORM 实体类型上

- **文件**: `NopCodeFileBizModel.java:55,60,70,76`, `NopCodeSymbolBizModel.java:97,111`
- **证据片段**:
  ```java
  @BizLoader(forType = CodeFileAnalysisResult.class)  // core 模型类型
  @BizLoader(forType = SymbolDTO.class)               // DTO 类型
  ```
- **严重程度**: P2
- **现状**: 6 处 @BizLoader 的 forType 指向非 ORM 实体类型。
- **风险**: 没有对应 xmeta，GraphQL 引擎可能无法正确处理这些数据加载器。
- **建议**: 如果需要作为 GraphQL 返回类型，创建对应 xmeta 或转为 DTO；否则改为普通 @BizQuery 方法。
- **信心水平**: 很可能
- **误报排除**: @BizLoader 技术上可注册在任意类型上，但无 xmeta 时行为不确定。
- **复核状态**: 未复核

### [维度07-07] 所有查询方法绕过 CrudBizModel 的 doFindPage/doFindList

- **文件**: `NopCodeIndexBizModel.java`, `NopCodeSymbolBizModel.java`, `NopCodeFileBizModel.java`
- **证据片段**:
  所有 @BizQuery 方法均委托给 codeIndexService.*() 而非调用 doFindList()/doFindPage()。
- **严重程度**: P3
- **现状**: 绕过 CrudBizModel 的查询预处理管道（权限过滤等）。但 nop-code 的查询场景都是索引范围查询，标准 CRUD 不完全适用。
- **风险**: 绕过安全管道，但索引范围查询确实需要自定义实现。
- **建议**: 可适配 QueryBean 的简单查询考虑通过 doFindPage() 实现。
- **信心水平**: 很可能
- **误报排除**: 文档允许边界场景直接使用 dao，本模块查询模式确实不适合标准 CRUD。
- **复核状态**: 未复核

### [维度07-08] NopCodeFileBizModel 返回 core 模型而非使用已有的 FileAnalysisDTO

- **文件**: `NopCodeFileBizModel.java:35,42`
- **证据片段**:
  ```java
  public CodeFileAnalysisResult getByPath(...)               // core 模型
  public PageBean<CodeFileAnalysisResult> findPage_files(...) // core 模型
  // 但 indexFile() 正确使用了 FileAnalysisDTO (@DataBean)
  ```
- **严重程度**: P2
- **现状**: 同一 BizModel 中 indexFile() 正确返回 FileAnalysisDTO，但 getByPath() 和 findPage_files() 返回未带 @DataBean 的 core 模型。
- **风险**: 返回类型策略不一致，GraphQL 端行为不一致。
- **建议**: 改为使用 FileAnalysisDTO，与 indexFile() 保持一致。
- **信心水平**: 确定
- **误报排除**: 已确认 FileAnalysisDTO 存在且已正确使用 @DataBean。
- **复核状态**: 未复核
