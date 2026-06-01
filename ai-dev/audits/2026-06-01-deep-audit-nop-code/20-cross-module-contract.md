# 维度20：跨模块契约一致性 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度20-01] nop-code-api 为空壳，接口定义错放在 nop-code-service（已在维度01报告）

详见 `01-dependency-graph.md` [维度01-01]。作为跨模块契约维度重申：外部模块若只依赖 `nop-code-api`，无法访问任何服务接口或 DTO。

- **严重程度**: P2（从跨模块契约视角评估）
- **信心水平**: 95%

### [维度20-02] 语言适配器硬编码在 CodeIndexService 构造函数中

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:167-174`
- **证据片段**:
  ```java
  public CodeIndexService() {
      this.registry = new LanguageAdapterRegistry();
      this.registry.registerAdapter(new JavaLanguageAdapter());
      this.registry.registerAdapter(new PythonLanguageAdapter());
      this.registry.registerAdapter(new TypeScriptLanguageAdapter());
      this.analyzer = new ProjectAnalyzer(registry);
  }
  ```
- **严重程度**: P2
- **现状**: CodeIndexService 直接 `new` 了所有语言适配器，绕过了 beans.xml 中注册的 ILanguageAdapter bean。
- **风险**: (1) 无法通过配置禁用/启用特定语言。(2) 无法通过 delta 定制替换实现。(3) 第三方无法注册新语言适配器。
- **建议**: 将 LanguageAdapterRegistry 和适配器通过 IoC 注入。
- **信心水平**: 90%
- **误报排除**: lang 子模块的 beans.xml 确实定义了 ILanguageAdapter bean 但从未被使用。
- **复核状态**: 未复核

### [维度20-03] `NopCodeConfigs` 为空接口 -- 无可配置项

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeConfigs.java`
- **证据片段**:
  ```java
  public interface NopCodeConfigs {
  }
  ```
  对比 CodeIndexService 中的硬编码常量：
  ```java
  private static final int MAX_QUERY_RESULTS = 10000;
  private static final int BATCH_SIZE = 1000;
  ```
- **严重程度**: P3
- **现状**: 配置接口存在但为空，关键常量全部硬编码。
- **风险**: 无法通过配置调整运行时行为。
- **建议**: 将关键常量迁移到 `NopCodeConfigs` 并通过 `@InjectValue` 注入。
- **信心水平**: 85%
- **误报排除**: 这是 WIP 模块常见情况。
- **复核状态**: 未复核

### [维度20-04] ICodeIndexService 接口过大（35+ 方法）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java`
- **严重程度**: P2
- **现状**: 承担 7 个不同职责域：索引管理、文件查询、符号查询、类型系统、图分析、依赖图、流分析。
- **风险**: 接口过大违反接口隔离原则。
- **建议**: 考虑拆分为多个专注接口。
- **信心水平**: 80%
- **误报排除**: WIP 模块可能尚未到重构时机。
- **复核状态**: 未复核
