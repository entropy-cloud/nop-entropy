# 审核维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] LanguageAdapterRegistry 的 @Inject 注解是死代码

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/adapter/LanguageAdapterRegistry.java:19-23`
- **证据片段**:
  ```java
  @Inject
  public void setAdapters(List<ILanguageAdapter> adapters) {
      ...
  }
  ```
  同时 `CodeIndexService.java:166` 通过构造器硬编码：
  ```java
  this.registry = new LanguageAdapterRegistry();
  this.registry.registerAdapter(new JavaLanguageAdapter());
  this.registry.registerAdapter(new PythonLanguageAdapter());
  this.registry.registerAdapter(new TypeScriptLanguageAdapter());
  ```
- **严重程度**: P3
- **现状**: LanguageAdapterRegistry 不在 beans.xml 中注册为 IoC bean，其 @Inject 注解永远不会被处理。构造器中硬编码了三种适配器。
- **风险**: 新增语言适配器需同时修改 beans.xml 和 CodeIndexService 构造器两处。
- **建议**: (A) 将 LanguageAdapterRegistry 注册为 IoC bean，删除硬编码。(B) 删除 @Inject 注解。
- **信心水平**: 90%
- **误报排除**: 已确认 beans.xml 中有 ioc:bean-type 注册 lang adapter，但两条路径并存不交互。
- **复核状态**: 未复核

### [维度08-02] lang 模块缺少 _module 文件

- **文件**: nop-code-lang-java, nop-code-lang-python, nop-code-lang-typescript 的 _vfs 目录
- **严重程度**: P3
- **现状**: 三个 lang 模块无 _module 文件，与 nop-code-service/dao/meta/web 不一致。
- **风险**: 不影响功能（与 service/dao 共享 /nop/code/ 路径），但如果 lang 模块需独立使用则 beans 不被发现。
- **建议**: 添加 _module 文件以保持一致性。
- **信心水平**: 85%
- **误报排除**: 当前不影响功能。
- **复核状态**: 未复核

### 合规项确认

| 检查项 | 结果 |
|--------|------|
| @Inject 字段可见性 | 合规 — 全部 protected 或 setter |
| Spring 注解误用 | 合规 — 无 @Autowired/@Value |
| _service.beans.xml 未被手写修改 | 合规 |
| app-service.beans.xml 手写配置 | 合规 |
| BizModel-BizInterface 映射 | 合规 — 11 对 11 |
| bean 命名约定 | 合规 |
