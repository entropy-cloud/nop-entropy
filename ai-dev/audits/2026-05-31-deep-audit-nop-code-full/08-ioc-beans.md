# 维度08：IoC 与 Bean 配置

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度08-01] 语言模块缺少 `_module` 文件

- **文件**: `nop-code/nop-code-lang-python/src/main/resources/_vfs/nop/code/`（及 lang-java, lang-typescript 同理）
- **证据片段**: 目录下仅有 `beans/` 子目录，无 `_module` 文件。对比 nop-code-meta/service/dao/web 均有 `_module` 文件。
- **严重程度**: P2
- **现状**: 三个 lang 模块在 VFS 目录下没有 `_module` 文件。目前 beans.xml 通过 VFS 路径合并被拾取，功能正常。
- **风险**: 若部署采用按需加载或模块隔离策略，语言适配器 bean 可能无法被自动发现。
- **建议**: 补充空 `_module` 文件与其他子模块保持一致。
- **信心水平**: 70%
- **误报排除**: 当前 VFS 合并机制可能不依赖 `_module` 文件即可发现 beans/。需确认平台 VFS 加载策略。
- **复核状态**: 未复核

---

### [维度08-02] LanguageAdapterRegistry 的 @Inject 注解无效（死代码）

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/adapter/LanguageAdapterRegistry.java:19-24`
- **证据片段**:
  ```java
  @Inject
  public void setAdapters(List<ILanguageAdapter> adapterList) {
      for (ILanguageAdapter adapter : adapterList) {
          adapters.put(adapter.getLanguage(), adapter);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `LanguageAdapterRegistry` 不是 IoC 管理的 bean，在 CodeIndexService 构造函数中通过 `new` 直接创建。`@Inject` 永远不会被处理。
- **风险**: 误导性代码；各 lang 模块 beans.xml 注册的 ILanguageAdapter bean 不会被此 `@Inject` 消费。
- **建议**: 移除 `@Inject` 和 `setAdapters` 方法，或将 `LanguageAdapterRegistry` 注册为 IoC bean。
- **信心水平**: 90%
- **误报排除**: 当前是死代码，功能通过构造函数中的 registerAdapter() 手动注册。
- **复核状态**: 未复核

---

### [维度08-03] CodeIndexService 构造函数硬编码适配器，与 IoC 注册机制冲突

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:165-173`
- **证据片段**:
  ```java
  public CodeIndexService() {
      this.registry = new LanguageAdapterRegistry();
      this.registry.registerAdapter(new JavaLanguageAdapter());
      this.registry.registerAdapter(new PythonLanguageAdapter());
      this.registry.registerAdapter(new TypeScriptLanguageAdapter());
  }
  ```
- **严重程度**: P2
- **现状**: 硬编码了三种语言适配器实例化，同时三个 lang 模块 beans.xml 也注册了同类型 bean（死 bean）。
- **风险**: 新增语言时需改两处；lang 模块 beans.xml 中的 bean 被实例化但无消费者。
- **建议**: 通过 IoC `@Inject setAdapters(List<ILanguageAdapter>)` 自动收集，移除硬编码。
- **信心水平**: 85%
- **误报排除**: 当前功能正确，仅架构上存在双重注册。
- **复核状态**: 未复核

---

## 无问题项确认

| 检查项 | 结果 |
|--------|------|
| @Inject 字段可见性 | 所有主代码 @Inject 字段均为 protected，无 private |
| 无 Spring 注解误用 | 未发现 @Autowired、@Value 使用 |
| _service.beans.xml 未被手写修改 | 标准代码生成产物格式 |
| bean 命名约定 | 遵循 Nop 平台约定 |
| 无循环依赖 | 所有 bean 为 lazy-init |
| 4 个 _module 文件存在 | meta/service/dao/web |
