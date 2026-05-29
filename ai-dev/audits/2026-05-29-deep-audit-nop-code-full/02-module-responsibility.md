# 维度02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService.java 是 3005 行的上帝类

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 1-3006
- **证据片段**:
  ```java
  public class CodeIndexService implements ICodeIndexService {
      // 行 204-267: Entity-to-Model 转换
      // 行 330-361: 全量索引
      // 行 414-470: 符号查询
      // 行 632-950: 符号搜索
      // 行 965-1278: 引用/类型层次/调用层次
      // 行 1411-1540: 图分析
      // 行 1568-1797: 依赖图
      // 行 1799-1930: 增量索引
      // 行 1924-2300: ORM 持久化
      // 行 2468-2610: 执行流分析
  }
  ```
- **严重程度**: P1
- **现状**: 单一类承担了 8 类职责（索引、查询、搜索、图分析、依赖图、执行流、持久化、转换），70+ 方法。
- **风险**: 难以测试、review 和维护。saveFileResultInSession 单方法 260 行。
- **建议**: 按职责域拆分为独立服务类（SymbolQueryService, GraphAnalysisService 等）。
- **信心水平**: 确定
- **误报排除**: 3005 行单类、70+ 方法、8 类职责域，确属上帝类。
- **复核状态**: 未复核

### [维度02-02] CommunityDetector.java 包含 3 个大型内部 DTO 类

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java`
- **行号**: L31-280
- **证据片段**:
  ```java
  public static class CommunityDetectionResult { ... } // ~170 行
  public static class Community { ... } // ~60 行
  public static class CommunityConfig { ... } // ~80 行
  ```
- **严重程度**: P3
- **现状**: 3 个内部 DTO 类（约 310 行）混在 891 行的分析器中。
- **风险**: 内部 DTO 无法被其他模块引用，增加认知复杂度。
- **建议**: 提取为独立顶层类。
- **信心水平**: 确定
- **误报排除**: Nop 平台其他 graph 模块也有类似模式，但同样值得重构。
- **复核状态**: 未复核

### [维度02-03] CommunityDetector.java 存在重复 import

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/community/CommunityDetector.java`
- **行号**: L8-9
- **证据片段**:
  ```java
  import nl.cwts.networkanalysis.Network;
  import nl.cwts.networkanalysis.Network;
  ```
- **严重程度**: P3
- **现状**: Network 类被导入了两次。
- **风险**: 代码噪音。
- **建议**: 删除第 9 行重复导入。
- **信心水平**: 确定
- **误报排除**: 明确的重复 import。
- **复核状态**: 未复核

### [维度02-04] nop-code-api 和 nop-code-meta 是空 Java 模块

- **文件**: `nop-code/nop-code-api/`、`nop-code/nop-code-meta/`
- **行号**: N/A
- **证据片段**: nop-code-api 无 src/ 目录；nop-code-meta 无 Java 文件。
- **严重程度**: P3
- **现状**: api 模块空壳（ICodeIndexService 在 service 中）；meta 只有资源文件。
- **风险**: 模块存在但职责空化，增加构建复杂度。
- **建议**: 要么将 API 契约迁入 api 模块，要么移除空模块。
- **信心水平**: 确定
- **误报排除**: 可能是有意为之的占位模块。
- **复核状态**: 未复核

### [维度02-05] NopCodeConstants.java 是空接口

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConstants.java`
- **行号**: L1-5
- **证据片段**:
  ```java
  public interface NopCodeConstants{
  	
  }
  ```
- **严重程度**: P3
- **现状**: 空接口，无任何常量定义。
- **风险**: 死代码。
- **建议**: 删除或在需要时再创建。
- **信心水平**: 确定
- **误报排除**: 可能是 codegen 生成的标准脚手架文件。
- **复核状态**: 未复核
