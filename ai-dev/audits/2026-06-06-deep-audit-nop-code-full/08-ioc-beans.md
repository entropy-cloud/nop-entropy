# 维度 08：IoC 与 Bean 配置 — nop-code 模块审计报告

## 第 1 轮（初审）

### [维度08-01] CodeIndexService.setRegistry 在 IoC setter 中调用 BeanContainer.instance()，初始化时序脆弱

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:179-190`
- **证据片段**:
  ```java
  @Inject
  public void setRegistry(LanguageAdapterRegistry registry) {
      this.registry = registry;
      Map<String, ILanguageAdapter> adapterMap = BeanContainer.instance().getBeansOfType(ILanguageAdapter.class);
      for (ILanguageAdapter adapter : adapterMap.values()) {
          registry.registerAdapter(adapter);
      }
      this.analyzer = new ProjectAnalyzer(registry);
      registerSemanticExtractors();
      registerImportResolvers();
      registerHeuristicSynthesizers();
  }
  ```
- **严重程度**: P2
- **现状**: `setRegistry()` 是 `@Inject` setter，在 IoC 初始化时被调用。方法内部通过 `BeanContainer.instance().getBeansOfType()` 拉取所有 ILanguageAdapter bean。正确性依赖于 XML 文件中 bean 定义的物理顺序。
- **风险**: 将正确性依赖于 XML 中 bean 定义顺序，属于隐式耦合。未来调整 bean 定义顺序可能导致 adapter 丢失。
- **建议**: 将 adapter 注册逻辑移到 `@PostConstruct` 方法或让 `LanguageAdapterRegistry` 直接由 IoC 注入 adapter 列表。
- **信心水平**: 高（85%）
- **误报排除**: 问题是架构脆弱性而非运行时故障。当前因 bean 定义顺序正确可正常工作。
- **复核状态**: 未复核

### [维度08-02] setFlowDetector/setChangeAnalyzer 交叉赋值有隐式顺序依赖

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:136-154`
- **证据片段**:
  ```java
  @Inject
  public void setFlowDetector(@Nullable IFlowDetector flowDetector) {
      this.flowDetector = flowDetector;
      if (flowDetector != null && this.changeAnalyzer != null) {
          this.changeAnalyzer.setFlowDetector(flowDetector);
      }
  }

  @Inject
  public void setChangeAnalyzer(@Nullable IChangeAnalyzer changeAnalyzer) {
      this.changeAnalyzer = changeAnalyzer;
      if (changeAnalyzer != null && this.flowDetector != null) {
          changeAnalyzer.setFlowDetector(this.flowDetector);
      }
  }
  ```
- **严重程度**: P3
- **现状**: `CodeIndexService` 在两个 setter 中都做了交叉赋值逻辑，虽然不是循环依赖，但对 setter 调用顺序有隐式依赖。
- **风险**: 低。两个 setter 中都有兜底逻辑，结果正确。但增加维护负担。
- **建议**: 将交叉赋值逻辑移到统一的 `@PostConstruct` 方法中。
- **信心水平**: 高（85%）
- **误报排除**: 不是循环依赖。FlowDetector 和 ChangeAnalyzer 之间没有双向引用。
- **复核状态**: 未复核

## 合规确认项

- ✅ 所有 `@Inject` 字段均使用 `protected` 可见性
- ✅ 无 `@Autowired` 或 Spring `@Value` 误用
- ✅ `_service.beans.xml` 未被手写修改
- ✅ `_module` 文件正确存在于需要的模块中
- ✅ beans.xml 语法正确，x:schema 声明完整
- ✅ bean id 使用全限定类名
- ✅ 无循环依赖
- ✅ import 路径正确
