# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService 是职责混合的 God Class (1904行)

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **证据片段**:
  ```
  行 231-268: Entity-to-Model 转换 (~38行)
  行 298-609: 索引管理入口 + 删除索引 (~310行)
  行 788-1059: ORM 持久化逻辑 (~270行)
  行 1061-1376: 单文件持久化 + import 解析 (~315行)
  行 1555-1710: Flow 分析编排 (~155行)
  行 1798-1904: 工具方法 (~106行)
  ```
- **严重程度**: P2
- **现状**: CodeIndexService 混合了 6+ 职责域，特别是 ORM 持久化逻辑（约 585 行）完全内联。
- **风险**: 维护成本高，任何修改持久化逻辑的变更都需要理解整个 1904 行文件。
- **建议**: 将 ORM 持久化逻辑提取到独立的 `CodePersistenceService` 类中，使 CodeIndexService 降为 ~800 行的纯编排层。
- **信心水平**: 确定
- **误报排除**: 1904 行的 God Class 有明确的职责混合证据，不是"看起来不优雅"。
- **复核状态**: 未复核

### [维度02-02] ICodeIndexService 接口和 3 个 DTO 放置在错误的模块

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`, `dto/FileAnalysisDTO.java`, `dto/SymbolDTO.java`, `dto/AnnotationUsageDTO.java`
- **证据片段**:
  ```java
  // NopCodeSymbolBizModel.java 导入两个不同包的 DTO
  import io.nop.code.service.api.dto.SymbolDTO;     // 来自 nop-code-service（错）
  import io.nop.code.api.dto.CallHierarchyDTO;       // 来自 nop-code-api（正确位置）
  ```
- **严重程度**: P2
- **现状**: `ICodeIndexService` 和 3 个 DTO 位于 `nop-code-service` 的 `service.api` 包，而其余 31 个 DTO 正确放在 `nop-code-api` 模块。
- **风险**: 违反分层一致性；外部消费者需依赖 -service 模块才能使用接口。
- **建议**: 迁移 `ICodeIndexService` 和 3 个 DTO 到 `nop-code-api` 模块。
- **信心水平**: 确定
- **误报排除**: 同模块内 31 个 DTO 已在正确位置，这 3 个是遗漏。
- **复核状态**: 未复核

### [维度02-03] 工具方法跨类复制粘贴

- **文件**: `CodeIndexService.java`, `CodeQueryService.java`, `CodeGraphService.java`
- **证据片段**:
  ```
  extractLines() → CodeIndexService:1850, CodeQueryService:68
  generateFileId() → CodeIndexService:1798, CodeQueryService:82
  entityToInheritance() → CodeIndexService:260, CodeGraphService:353
  ```
- **严重程度**: P3
- **现状**: 3 个方法在多个类中完全相同实现重复。
- **风险**: 修改一处时遗忘同步其他处。
- **建议**: 提取到共享工具类 `CodeServiceHelper`。
- **信心水平**: 确定
- **误报排除**: 代码完全相同，确认是复制粘贴。
- **复核状态**: 未复核

### [维度02-04] CodeCacheManager 对 IFlowDetector 做向下转型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:121-123`
- **证据片段**:
  ```java
  if (flowDetector instanceof FlowDetector) {
      ((FlowDetector) flowDetector).invalidateCache(indexId);
  }
  ```
- **严重程度**: P3
- **现状**: CodeCacheManager 接受 IFlowDetector 接口参数却 instanceof 检查具体实现类。
- **风险**: 其他 IFlowDetector 实现的缓存失效逻辑将静默失效。
- **建议**: 将 `invalidateCache(String indexId)` 方法提升到 `IFlowDetector` 接口。
- **信心水平**: 确定
- **误报排除**: 明确的 LSP 违反，不是风格偏好。
- **复核状态**: 未复核

### [维度02-05] 常量在多类中重复定义

- **文件**: `FlowDetector.java`, `ChangeAnalyzer.java`, `DeadCodeDetector.java`, `CodeIndexService.java`
- **证据片段**:
  ```
  SECURITY_PATTERN (FlowDetector) vs SECURITY_KEYWORDS (ChangeAnalyzer)
  TEST_FILE_INDICATORS (FlowDetector) vs 内联检测 (CodeIndexService)
  SPRING_ENTRY_ANNOTATIONS (FlowDetector) vs DEFAULT_FRAMEWORK_ANNOTATIONS (DeadCodeDetector)
  ```
- **严重程度**: P3
- **现状**: 同一领域概念的常量集合在多个类中独立维护、粒度不同。
- **风险**: 长期导致不一致。
- **建议**: 在 nop-code-core 或 nop-code-flow 中创建共享常量类。
- **信心水平**: 很可能
- **误报排除**: 语义确实重叠但各自场景略有不同。
- **复核状态**: 未复核

### [维度02-06] CodeIndexService 硬编码导入具体语言 ImportResolver

- **文件**: `CodeIndexService.java:40-42, 209-218`
- **证据片段**:
  ```java
  import io.nop.code.lang.java.JavaImportResolver;
  import io.nop.code.lang.python.PythonImportResolver;
  import io.nop.code.lang.typescript.TypeScriptImportResolver;

  private void registerImportResolvers() {
      IImportResolver[] resolvers = {
          new JavaImportResolver(),
          new PythonImportResolver(),
          new TypeScriptImportResolver()
      };
  ```
- **严重程度**: P3
- **现状**: CodeIndexService 直接 `new` 了三个具体语言模块的 ImportResolver，绕过了 SPI 机制。
- **风险**: 每新增一种语言支持，必须修改 nop-code-service。
- **建议**: 将 ImportResolver 发现纳入 LanguageAdapterRegistry 的 SPI 自动发现机制。
- **信心水平**: 确定
- **误报排除**: 模块内已有 LanguageAdapterRegistry 的 SPI 模式，此处未遵循。
- **复核状态**: 未复核

### [维度02-07] CodeGraphService 每次调用重复创建无状态算法实例

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java`
- **证据片段**:
  ```
  行 53:  new CommunityDetector()
  行 65:  new EntryPointScorer()
  行 108: new ImpactAnalyzer()
  行 118: new CriticalNodeAnalyzer()
  行 134: new CommunityDetector()  (同方法内再次创建)
  行 154: new GraphExporter()
  行 164,172: new CommunityDetector() x2 (diffGraph)
  ```
- **严重程度**: P3
- **现状**: 无状态算法类在每次方法调用时都 `new` 新实例，CommunityDetector 在单次 diffGraph 调用中创建了 2 次。
- **风险**: 不必要的 GC 压力，微小的性能浪费。
- **建议**: 作为字段级单例或通过 @Inject 注入复用。
- **信心水平**: 确定
- **误报排除**: CommunityDetector 无实例状态（892行审计确认），确认可复用。
- **复核状态**: 未复核

## 检查通过项

- CommunityDetector (892行): 算法逻辑高度内聚，大小合理
- FlowDetector (567行) / ChangeAnalyzer (446行): 大小可接受
- JavaFileAnalyzer (915行) / ProjectAnalyzer (794行): 领域复杂度支撑
- _gen 文件无手写修改痕迹
- 子模块依赖关系清晰、层次合理
