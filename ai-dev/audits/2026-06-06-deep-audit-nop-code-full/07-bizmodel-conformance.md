# 维度 07：BizModel 规范遵循 — nop-code 模块审计报告

## 审计范围

**目标模块**: nop-code（nop-code-service 子模块）
**审计对象**: 11 个 `@BizModel` 注解类 + 4 个 impl/ 内部实现类

---

## 第 1 轮（初审）

### [维度07-01] impl/ 内部类使用 `*Service` 命名违反 Nop 命名规范

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:93`, `CodeQueryService.java:26`, `CodeSearchService.java:24`, `CodeGraphService.java:35`
- **证据片段**:
  ```java
  // CodeIndexService.java:93
  public class CodeIndexService implements ICodeIndexService {
  
  // CodeQueryService.java:26
  class CodeQueryService {
  
  // CodeSearchService.java:24
  class CodeSearchService {
  
  // CodeGraphService.java:35
  class CodeGraphService {
  ```
- **严重程度**: P2
- **现状**: 4 个内部实现类均以 `*Service` 命名。`service-layer.md` 明确规定"不要在 Nop 模块中创建 `*Service` / `*Controller` 类——这些职责由 BizModel 和 `I*Biz` 接口承担"。其中 CodeIndexService 是 IoC 托管 Bean，其余 3 个是 package-private 辅助类。
- **风险**: `*Service` 命名在 Spring 中有特定含义，在 Nop 上下文中容易造成架构理解混乱。这些类实际上是 BizModel 的 Processor/Helper。
- **建议**: 重命名为 Processor 风格（如 `CodeIndexProcessor`、`CodeQueryProcessor`、`CodeSearchProcessor`、`CodeGraphProcessor`），或将辅助类重命名为 `*Helper`/`*Analyzer`。
- **信心水平**: 高 (85%)
- **误报排除**: `service-layer.md` 中有明确的禁止条目，且 4 个类形成了一个隐含的 Service Layer，与 Nop 平台 BizModel + Processor 设计意图结构性偏离。
- **复核状态**: 未复核

### [维度07-02] NopCodeIndexBizModel 方法过度集中——依赖/流分析方法应归属各自聚合根 BizModel

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:170-276`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:170-198 — 6个依赖分析方法
  @BizQuery
  @Auth(permissions = "code-query")
  public DepGraphDTO getDeps(
          @Name("indexId") String indexId,
          @Name("filePath") String filePath,
          @Name("depth") @Optional Integer depth) {
      int maxDepth = depth != null && depth > 0 ? depth : 3;
      return codeIndexService.getDeps(indexId, filePath, maxDepth);
  }
  
  // NopCodeIndexBizModel.java:200-234 — 5个流分析方法
  @BizMutation
  @Auth(roles = "admin")
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) { ... }
  ```
- **严重程度**: P2
- **现状**: `NopCodeIndexBizModel` 拥有 24 个方法，其中至少 11 个直接操作非 NopCodeIndex 聚合根的实体（6 个依赖分析方法、5 个流分析方法）。而对应的 `NopCodeDependencyBizModel` 和 `NopCodeFlowBizModel` 均为空壳，只有构造函数 + setEntityName()。
- **风险**: 违反 `service-layer.md` 的归属规则。`NopCodeIndexBizModel` 成为 God Class（344 行、24 个方法），增加维护成本和理解难度。
- **建议**: 将依赖分析方法迁移到 `NopCodeDependencyBizModel`，流分析方法迁移到 `NopCodeFlowBizModel`。注意迁移后 GraphQL API 路径会变化，需同步更新前端调用。
- **信心水平**: 中高 (75%)
- **误报排除**: `service-layer.md` 有明确的归属判断表，11 个方法集中在错误 BizModel 中有结构性维护成本。但所有方法都以 `indexId` 作为入参，作为编排入口也有一定合理性。
- **复核状态**: 未复核

### [维度07-03] NopCodeIndexBizModel 在单例中维护可变内存状态

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:38,336-343`
- **证据片段**:
  ```java
  // NopCodeIndexBizModel.java:38
  private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();
  
  // NopCodeIndexBizModel.java:336-343
  private void evictStatusMap() {
      while (incrementalStatusMap.size() > MAX_STATUS_ENTRIES) {
          String key = incrementalStatusMap.keySet().iterator().next();
          if (key != null) {
              incrementalStatusMap.remove(key);
          }
      }
  }
  ```
- **严重程度**: P3
- **现状**: BizModel 单例中维护 `ConcurrentHashMap<String, IncrementalStatus>`，用于跟踪增量索引操作的状态。不持久化、不共享（多实例部署时状态不一致）、有淘汰机制（MAX=20）。
- **风险**: 多实例部署时 `getIncrementalStatus()` 在非执行索引的实例上返回 null。状态为临时进度数据，丢失不影响核心功能。
- **建议**: 单实例部署可接受。未来需多实例时考虑持久化到 `NopCodeIndex` 扩展字段或使用分布式缓存。
- **信心水平**: 高 (90%)
- **误报排除**: BizModel 单例中的可变状态在多实例部署场景下会产生不一致行为。但因为上限已控制且数据为临时状态，严重程度降低为 P3。
- **复核状态**: 未复核
