# 维度07：BizModel 规范遵循

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度07-01] NopCodeIndexBizModel.detectFlows 是 @BizMutation 但缺少 @Auth

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:190-193`
- **证据片段**:
  ```java
  @BizMutation
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {
      return codeIndexService.detectFlows(indexId);
  }
  ```
- **严重程度**: P2
- **现状**: `detectFlows` 是 `@BizMutation`（会触发写入操作），但没有 `@Auth` 注解。同文件中其他 `@BizMutation` 均标注了 `@Auth(roles = "admin")`。
- **风险**: 非 admin 用户可触发开销很大的 flow 检测与数据库写入。
- **建议**: 添加 `@Auth(roles = "admin")`，与同文件其他 `@BizMutation` 保持一致。
- **信心水平**: 确定
- **误报排除**: 其他 5 个 `@BizMutation` 方法均有 `@Auth`，此方法是唯一遗漏的。
- **复核状态**: 未复核

---

### [维度07-02] NopCodeIndexBizModel 中 18 个 @BizQuery 方法中 17 个缺少 @Auth

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:83-257`
- **证据片段**:
  ```java
  @BizQuery
  public IncrementalStatus getIncrementalStatus(@Name("indexId") String indexId) {
      return incrementalStatusMap.get(indexId);
  }
  // 其余 16 个 @BizQuery 同样没有 @Auth

  @BizQuery
  @Auth(permissions = "code-source-read")  // 仅此 1 个有 Auth
  public String exportGraph(...) { ... }
  ```
- **严重程度**: P3
- **现状**: 18 个 `@BizQuery` 中仅 `exportGraph` 标注了权限保护。其余 17 个无权限控制。
- **风险**: 所有已认证用户可查询完整代码图谱、符号、依赖关系等数据。
- **建议**: 评估是否应统一添加 `@Auth(permissions = "code-source-read")`。
- **信心水平**: 很可能
- **误报排除**: `NopCodeSymbolBizModel` 中已有方法使用了 `@Auth(permissions = "code-source-read")`，说明确有权限控制需求。
- **复核状态**: 未复核

---

### [维度07-03] IncrementalStatus 内部类缺少 @DataBean 且未注册为 xmeta 类型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:259-314`
- **证据片段**:
  ```java
  public static class IncrementalStatus {
      private String indexId;
      private String mode;
      // ... 手写 getter/setter ...
  }
  ```
- **严重程度**: P3
- **现状**: `IncrementalStatus` 是方法返回类型但无 `@DataBean` 注解，手写了 getter/setter。同模块 45 个其他 DTO 均使用了 `@DataBean`。
- **风险**: 与模块内 DTO 命名惯例不一致。
- **建议**: 提取为独立 DTO 类并添加 `@DataBean`。
- **信心水平**: 很可能
- **误报排除**: 模块内所有其他 DTO 类均使用了 `@DataBean`，此内部类是唯一例外。
- **复核状态**: 未复核

---

### [维度07-04] incrementalStatusMap 是 JVM 内存状态，多实例部署不共享

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:37`
- **证据片段**:
  ```java
  private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();
  ```
- **严重程度**: P2
- **现状**: `incrementalStatusMap` 是 `ConcurrentHashMap`，存储索引操作的进度状态。完全在 JVM 内存中，没有持久化。
- **风险**: 多实例部署时状态不可见；JVM 重启后丢失；evict 逻辑非确定性。
- **建议**: 添加注释说明限制。中期考虑使用 `NopCodeIndex` 扩展字段或 `ICache` 替代。
- **信心水平**: 很可能
- **误报排除**: `CodeIndexService` 中的 `CodeCacheManager` 使用了独立的缓存管理类，而非直接挂 `ConcurrentHashMap`。
- **复核状态**: 未复核

---

### [维度07-05] findPage_symbols 使用多个 @Name 参数而非 @RequestBean，且静默吞异常

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:64-95`
- **证据片段**:
  ```java
  @BizQuery
  public PageBean<SymbolDTO> findPage_symbols(
          @Name("query") @Optional String query,
          @Name("kinds") @Optional List<String> kinds, ...) {
      if (kinds != null) {
          kindList = kinds.stream()
                  .map(k -> {
                      try { return Enum.valueOf(CodeSymbolKind.class, k); }
                      catch (IllegalArgumentException e) { return null; }
                  })
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());
      }
  }
  ```
- **严重程度**: P3
- **现状**: 6 个 `@Name` 参数，手动 String→Enum 转换，静默忽略无效 kind 值。
- **风险**: 调用者传入无效值时静默忽略而非报错，导致查询结果不符合预期且难以调试。
- **建议**: 将 `IllegalArgumentException` 改为抛出 `NopException`，考虑使用 `@RequestBean`。
- **信心水平**: 很可能
- **误报排除**: 静默吞异常是真实的代码质量缺陷。
- **复核状态**: 未复核

---

### [维度07-06] CodeIndexService 1570 行——职责过多的上帝类

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1-1570`
- **证据片段**:
  ```java
  public class CodeIndexService implements ICodeIndexService {
      private CodeSearchService searchService;
      private CodeGraphService graphService;
      private CodeQueryService queryService;
      // 直接包含 6+ 种职责：索引管理、增量索引、entity→model 转换、
      // 批量持久化、Flow 管理、路径校验等
  }
  ```
- **严重程度**: P2
- **现状**: 1570 行的类已拆分出 3 个子服务，但自身仍保留 6+ 种职责。
- **风险**: 维护成本高，任何修改需理解 1570 行上下文。
- **建议**: 提取 entity-to-model 转换为 `CodeModelConverter`；Flow 管理为 `CodeFlowService`；批量持久化为 `CodePersistenceService`。
- **信心水平**: 确定
- **误报排除**: 已拆分 3 个子服务但未完全迁移，是半完成重构。
- **复核状态**: 未复核

---

### [维度07-07] NopCodeIndexBizModel 承担了不属于聚合根的 Flow/Graph 方法

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:190-257`
- **证据片段**:
  ```java
  @BizMutation
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) { ... }
  @BizQuery
  public List<ExecutionFlow> listFlows(@Name("indexId") String indexId) { ... }
  @BizQuery
  public CommunityDetectionResultDTO detectCommunities(...) { ... }
  // ... 约 20 个 Flow/Graph 方法
  ```
- **严重程度**: P2
- **现状**: `NopCodeIndexBizModel` 聚合根是 `NopCodeIndex`，但承载约 20 个不属于 Index 实体的 Flow/Graph 分析方法。`NopCodeFlowBizModel` 仅 12 行空壳。
- **风险**: 违反聚合根边界，GraphQL API 层面所有操作都以 `NopCodeIndex__xxx` 形式暴露。
- **建议**: Flow 方法迁移到 `NopCodeFlowBizModel`；Graph 方法考虑独立的 `NopCodeGraphBizModel`。
- **信心水平**: 很可能
- **误报排除**: 平台标准模式是每个聚合根管理自己的业务方法。NopCodeFlowBizModel 是空壳（12 行）而 NopCodeIndexBizModel 有 20+ 不属于 Index 的方法。
- **复核状态**: 未复核

---

### [维度07-08] NopCodeSymbolBizModel 和 NopCodeFileBizModel 的 @BizLoader 绑定在非 ORM 实体类型上

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java:97-122`
- **证据片段**:
  ```java
  @BizLoader(forType = SymbolDTO.class)
  public List<AnnotationUsageDTO> usages(@ContextSource SymbolDTO symbol, ...) { ... }
  ```
- **严重程度**: P3
- **现状**: `@BizLoader` 的 `forType` 指向 DTO 而非聚合根 ORM 实体。功能上可工作，但概念上混淆了聚合根归属。
- **风险**: 当 DTO 被其他 BizModel 返回时，@BizLoader 字段可解析性取决于上下文。
- **建议**: 当前可工作，建议添加注释说明归属原因。
- **信心水平**: 有趣的猜测
- **误报排除**: 平台允许 `forType` 指向任何 `@DataBean` 类型。更多是架构清晰度问题而非功能错误。
- **复核状态**: 未复核
