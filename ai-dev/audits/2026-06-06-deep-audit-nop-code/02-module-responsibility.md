# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-1] CodeIndexService.java 严重职责膨胀（1999行，跨越 7 个功能域）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 全文 1999 行
- **证据片段**:
  ```java
  // 7 个功能域混在一个类中：
  // 1. 索引构建 indexDirectory (L332-500)
  // 2. 增量索引 triggerIncrementalIndex (L717-804)
  // 3. ORM 持久化 persistInSession/saveFileResultInSession (L822-1427)
  // 4. 启发式边合成 synthesizeAndPersistHeuristicEdges (L885-966)
  // 5. 流分析编排 detectFlows/listFlows/persistFlows (L1606-1773)
  // 6. 变更/死代码分析 analyzeChanges/detectDeadCode (L1675-1702)
  // 7. 路径校验 validatePath/validateLocalPath (L1934-1967)
  ```
- **严重程度**: P1
- **现状**: CodeIndexService 同时承担索引构建、增量索引、ORM 持久化、启发式边合成、流分析编排、变更/死代码分析、路径校验 7 个不同功能域。
- **风险**: 修改任一域需理解 2000 行代码；持久化逻辑(saveFileResultInSession ~300行)与业务逻辑紧密混合。
- **建议**: 拆分为 CodePersistenceService（持久化）、路径校验工具类、流分析移到 CodeGraphService。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——单类1999行跨越7个不相关功能域，有明确的可量化维护成本。
- **复核状态**: 未复核

### [维度02-3] CodeGraphService.java 同时承担图分析和依赖图两个子域（752行）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java`
- **行号**: 全文 752 行
- **证据片段**:
  ```java
  // 调用图分析（基于 CallGraph + SymbolTable）: detectCommunities, getGraphAnalysis, getImpactAnalysis...
  // L49-450: 约400行调用图逻辑
  // 依赖图分析（基于 NopCodeDependency）: getDeps, getReverseDeps, findCycles, getDepGraph
  // L595-750: 约150行依赖图逻辑，含 Tarjan SCC 算法
  ```
- **严重程度**: P2
- **现状**: 两个子域数据源不同（CallGraph vs NopCodeDependency）、算法不同（Louvain/PageRank vs Tarjan/BFS），但合并在同一类。
- **风险**: Tarjan SCC 算法应位于 nop-code-graph 模块而非 nop-code-service。
- **建议**: 拆分依赖图逻辑为 CodeDependencyService，Tarjan 算法迁移到 nop-code-graph 模块。
- **信心水平**: 确定
- **误报排除**: 不是风格偏好——两个子域使用完全不同的数据结构和算法。
- **复核状态**: 未复核

### [维度02-4] DTO 散落在 service 模块，与 api 模块重叠

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/SymbolDTO.java` (272行), `AnnotationUsageDTO.java` (76行), `FileAnalysisDTO.java` (72行)
- **行号**: 各文件全文
- **证据片段**:
  ```java
  // NopCodeSymbolBizModel 同时导入两个 dto 包：
  import io.nop.code.service.api.dto.SymbolDTO;      // service 模块
  import io.nop.code.api.dto.SymbolInfoDTO;           // api 模块
  ```
- **严重程度**: P2
- **现状**: 3 个 DTO（共420行）放在 service 模块，与 api 模块的 31 个 DTO 形成重复定位。
- **风险**: 新增 DTO 时无明确规则判断放在哪；外部模块使用这些 DTO 需依赖 service 实现层。
- **建议**: 迁移到 nop-code-api 模块。
- **信心水平**: 确定
- **误报排除**: 不是平台标准模式——api 模块已有 31 个 DTO，3 个在 service 确属位置错误。
- **复核状态**: 未复核

### [维度02-5] Biz 接口放在 dao 模块而非 api 模块

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/` 下 11 个 INopCode*Biz.java
- **行号**: 各文件 7 行
- **证据片段**:
  ```java
  package io.nop.code.biz;
  import io.nop.code.dao.entity.NopCodeIndex;
  import io.nop.orm.biz.ICrudBiz;
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex> { }
  ```
- **严重程度**: P2
- **现状**: 11 个 Biz 接口放在 dao 模块的 io.nop.code.biz 包中。
- **风险**: 其他模块引用 Biz 接口需依赖整个 nop-code-dao（含 entity 和 ORM 传递依赖）。
- **建议**: 迁移到 nop-code-api 模块。
- **信心水平**: 很可能
- **误报排除**: I*Biz 接口放在 dao 模块在标准 Nop 生成中是常见模式，但 nop-code 是手写设计模块，且有独立的 api 子模块，应遵循分层原则。
- **复核状态**: 未复核

### [维度02-6] NopCodeIndexBizModel 30 个 GraphQL 方法全挂一个实体

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: 全文 341 行
- **严重程度**: P2
- **现状**: 图分析、流分析、依赖图等功能的 GraphQL 入口全部挂在 NopCodeIndex 实体上。
- **建议**: 拆分为 CodeGraphBizModel 和 CodeFlowBizModel。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度02-7] NopCodeSymbolBizModel 包含不属于 Symbol 实体的查询方法

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java`
- **行号**: 全文 248 行
- **严重程度**: P2
- **现状**: moduleDigest、publicSurface、fileOutline、searchCode、getTypeHierarchy、detectDeadCode 等方法以 indexId 为核心参数而非 symbolId，不属于 Symbol 聚合根。
- **建议**: 按功能迁移到对应的 BizModel。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度02-2] CodeQueryService.java 职责模糊但实际合理（844行）

- **严重程度**: P3
- **现状**: 包私有类，是 CodeIndexService 的内部分拆，对模块外部无 API 暴露影响。
- **复核状态**: 未复核

### [维度02-8] CommunityDetector.java 内嵌数据类导致文件膨胀（927行）

- **严重程度**: P3
- **现状**: CommunityDetectionResult 和 Community 两个内部类占约 200 行，干扰算法阅读。
- **建议**: 提取为顶层类。
- **复核状态**: 未复核

### [维度02-10] JavaFileAnalyzer.java 接近单文件职责上限（915行）

- **严重程度**: P3
- **现状**: Java 语言特性最丰富导致分析器行数大，尚在合理范围。MethodCallFilter 已成功拆出。
- **复核状态**: 未复核

### [维度02-9] 生成文件目录结构正确，未发现手写修改痕迹

- **严重程度**: 无问题
- **确认**: _gen/ 目录下文件均为自动生成，保留文件为标准空壳扩展类。
