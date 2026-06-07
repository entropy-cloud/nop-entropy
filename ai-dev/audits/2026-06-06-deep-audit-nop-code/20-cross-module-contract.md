# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] NopCodeConfigs 和 NopCodeConstants 为空接口，配置值硬编码

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConfigs.java` 和 `NopCodeConstants.java`
- **证据片段**:
  ```java
  public interface NopCodeConfigs { }  // 空
  public interface NopCodeConstants { }  // 空
  
  // 但 CodeIndexService 中有硬编码配置值：
  static final int MAX_QUERY_RESULTS = 10000;
  private static final int BATCH_SIZE = 1000;
  private static final int DELETE_BATCH_SIZE = 500;
  private static final int MAX_FLOWS_PER_INDEX = 5000;
  ```
- **严重程度**: P2
- **现状**: 关键可调参数（MAX_QUERY_RESULTS、BATCH_SIZE 等）硬编码在 CodeIndexService 中，未通过 @InjectValue 暴露为可配置项。
- **风险**: 管理员无法通过配置文件调整阈值。
- **建议**: 将关键可调参数定义到 NopCodeConfigs 中，通过 @InjectValue 注入。
- **信心水平**: 高
- **误报排除**: 已确认两个接口确实为空且无 @InjectValue 使用。
- **复核状态**: 未复核

### [维度20-02] globToRegex 在 CodeSearchService 和 TestSecurityFixes 中重复

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeSearchService.java:297-312` 和 `TestSecurityFixes.java:11-26`
- **证据片段**: 两个文件中有完全相同的 globToRegex 实现。
- **严重程度**: P2
- **现状**: 测试文件复制了生产代码的私有方法进行测试。
- **风险**: 生产版本修改时测试不会感知。且 globToRegex 未处理 ** 递归通配符。
- **建议**: 提取为 FilePatternUtils.globToRegex 公共方法，测试直接调用生产方法。
- **信心水平**: 高
- **误报排除**: 两个实现逐字符相同。
- **复核状态**: 未复核

### [维度20-03] ICodeIndexService.getAffectedFlows 文档中未记录

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:135`
- **证据片段**:
  ```java
  List<ExecutionFlow> getAffectedFlows(String indexId, List<String> changedFilePaths);
  ```
- **严重程度**: P2
- **现状**: getAffectedFlows 是公开接口方法且有 BizModel 暴露，但文档 API 清单中未列出。
- **建议**: 在文档 Flow Analysis 区域补充此方法。
- **信心水平**: 高
- **误报排除**: 已确认有 BizModel 暴露和 E2E 测试。
- **复核状态**: 未复核
