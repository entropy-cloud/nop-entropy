# 维度16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] CodeIndexService (3006 行) 缺少直接单元测试

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestCodeIndexService.java`
- **行号**: L20-143
- **证据片段**:
  ```java
  class TestCodeIndexService {
      @BeforeEach
      void setUp() {
          LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
          registry.registerAdapter(new JavaLanguageAdapter());
          ProjectAnalyzer analyzer = new ProjectAnalyzer(registry);
          analysisResult = analyzer.analyzeProject(projectRoot);
      }
  ```
- **严重程度**: P1
- **现状**: 文件名为 TestCodeIndexService 但实际只测试了 ProjectAnalyzer。CodeIndexService 的 40+ 个 public 方法从未被直接测试。
- **风险**: DB 持久化、缓存失效、batch 操作等核心行为出现回归无法发现。
- **建议**: 重命名为 TestProjectAnalyzerIntegration，新增 TestCodeIndexServiceUnit 针对 core 方法编写测试。
- **信心水平**: 确定
- **误报排除**: BizModel 测试通过 RPC 路径间接调用了部分方法，但覆盖率不完整。
- **复核状态**: 未复核

### [维度16-02] 部分集成测试使用 assumeTrue 静默跳过

- **文件**: `nop-code/nop-code-service/src/test/java/io/nop/code/service/TestNopCodeFlowBizModel.java`
- **行号**: L73-74
- **证据片段**:
  ```java
  org.junit.jupiter.api.Assumptions.assumeTrue(response.isOk(),
      "detectFlows BizModel action not registered in test context, skipping");
  ```
- **严重程度**: P2
- **现状**: 核心功能（如 detectFlows）因配置错误消失时，测试静默跳过而非失败。
- **风险**: 关键功能可能静默缺失，CI 不报警。
- **建议**: 核心功能应断言成功而非 assumeTrue。仅 optional 功能使用 assumeTrue。
- **信心水平**: 确定
- **误报排除**: nop-search 未部署场景下 assumeTrue 合理，但 detectFlows 不属此类。
- **复核状态**: 未复核

### [维度16-03] 测试样板代码大量重复

- **文件**: 6+ 个测试文件
- **证据片段**: rpcQuery/rpcMutation/indexTestProject 方法在每个文件中重复，约 60-70 行。
- **严重程度**: P3
- **现状**: 每个测试文件复制粘贴了相同的基础设施方法。
- **建议**: 提取公共基类或 JUnit 5 扩展。
- **信心水平**: 确定
- **误报排除**: 测试代码风格问题，不影响正确性。
- **复核状态**: 未复核
