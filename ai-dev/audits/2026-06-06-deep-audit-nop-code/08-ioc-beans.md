# 维度 08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] nop-vfs-index.txt 缺失 _lang-typescript.beans.xml 和 _lang-python.beans.xml 条目

- **文件**: `nop-code/nop-code-app/src/main/resources/nop-vfs-index.txt:255-258`
- **证据片段**:
```
255: /nop/code/beans/_dao.beans.xml
256: /nop/code/beans/_lang-java.beans.xml
257: /nop/code/beans/_service.beans.xml
258: /nop/code/beans/app-service.beans.xml
```
而 `_lang-typescript.beans.xml` 和 `_lang-python.beans.xml` 不在该文件中。
- **严重程度**: P1
- **现状**: `app-service.beans.xml` 显式 import 了 `_lang-typescript.beans.xml` 和 `_lang-python.beans.xml`，这两个文件确实存在于 VFS 中。但 `nop-vfs-index.txt` 中只索引了 `_lang-java.beans.xml`，遗漏了 TypeScript 和 Python 两个语言适配器。
- **风险**: 在 Quarkus native-image 模式下，`nop-vfs-index.txt` 是资源发现的依据，缺失条目会导致 `PythonLanguageAdapter` 和 `TypeScriptLanguageAdapter` 的 bean 无法被发现。Python 和 TypeScript 文件将不会被索引。
- **建议**: 将 `/nop/code/beans/_lang-typescript.beans.xml` 和 `/nop/code/beans/_lang-python.beans.xml` 添加到 `nop-vfs-index.txt`。
- **信心水平**: 确定
- **误报排除**: VFS 索引中确实缺少两个实际存在的 beans 文件条目，而同类的 `_lang-java.beans.xml` 已正确索引。
- **复核状态**: 未复核

### [维度08-02] CodeIndexService 程序化 BeanContainer 查找绕过 IoC 配置

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:208-218`
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
- **现状**: `LanguageAdapterRegistry` 有 `setAdapters(List<ILanguageAdapter>)` 方法可用于 IoC 注入适配器列表，但代码绕过了 IoC 配置，在 setter 中使用 `BeanContainer.instance().getBeansOfType()` 程序化查找。
- **风险**: 注册时机依赖 setter 在所有语言适配器 bean 初始化之后被调用，如果未来拆分 bean 定义或调整加载顺序，可能导致适配器缺失。
- **建议**: 将语言适配器注册移入 `LanguageAdapterRegistry` 的 IoC 配置中，使用 `<property name="adapters">` 注入。
- **信心水平**: 很可能
- **误报排除**: 不是功能性 bug——当前代码在正常 IoC 启动流程中可以正确工作。问题在于架构层面的关注点分离。
- **复核状态**: 未复核

### [维度08-03] 子服务延迟初始化与 @Nullable 注入安全

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:150-156`
- **证据片段**:
```java
private synchronized void ensureSubServices() {
    if (searchService == null && daoProvider != null) {
        searchService = new CodeSearchService(daoProvider, searchEngine, cacheManager);
        graphService = new CodeGraphService(daoProvider, cacheManager);
        queryService = new CodeQueryService(daoProvider, cacheManager, ormTemplate);
    }
}
```
- **严重程度**: P2
- **现状**: `CodeSearchService`、`CodeGraphService`、`CodeQueryService` 没有通过 IoC 管理，而是在 `ensureSubServices()` 中手动 `new`。`searchEngine` 标注 `@Nullable`，可能为 null。
- **风险**: 子服务没有独立的 bean 生命周期管理。`searchEngine` 为 null 时 `CodeSearchService` 的搜索功能可能出错。`synchronized` 在高并发场景下可能成为瓶颈。
- **建议**: 评估是否将子服务声明为 IoC bean。如保持当前模式，应在构造时对 null `searchEngine` 做防御性检查。
- **信心水平**: 很可能
- **误报排除**: 延迟初始化模式本身不是误报，问题在于 `@Nullable` 注入与子服务构造的安全性。
- **复核状态**: 未复核

### [维度08-04] 语言适配器 bean 缺少 ioc:default 与设计意图文档化

- **文件**: `nop-code/nop-code-lang-java/src/main/resources/_vfs/nop/code/beans/_lang-java.beans.xml:7-9`（及 typescript、python 同类文件）
- **证据片段**:
```xml
<bean id="io.nop.code.lang.java.JavaLanguageAdapter"
      class="io.nop.code.lang.java.JavaLanguageAdapter"
      ioc:type="io.nop.code.core.analyzer.ILanguageAdapter"/>
```
- **严重程度**: P3
- **现状**: 三个语言适配器 bean 声明了 `ioc:type` 但没有 `ioc:default`。当前代码使用 `getBeansOfType()` 收集所有实现，因此不触发问题。
- **风险**: 如果未来有代码通过 `@Inject ILanguageAdapter adapter` 单例注入，IoC 容器将无法确定注入哪个实现。
- **建议**: 确认不使用 `ioc:default` 是有意设计（多实现共存），添加注释说明。
- **信心水平**: 很可能
- **误报排除**: 当前使用 `getBeansOfType()` 而非单例注入，没有运行时错误。
- **复核状态**: 未复核

### [维度08-05] 空 _dao.beans.xml 的无用 import

- **文件**: `nop-code/nop-code-dao/src/main/resources/_vfs/nop/code/beans/_dao.beans.xml:1-5`
- **证据片段**:
```xml
<beans x:schema="/nop/schema/beans.xdef" .../>
```
- **严重程度**: P3
- **现状**: `_dao.beans.xml` 是空文件，但 `app-service.beans.xml` 仍 import 它。
- **风险**: 无功能风险，但增加配置文件的认知负载。
- **建议**: 保留当前状态——因为是生成文件，不应手动修改。
- **信心水平**: 确定
- **误报排除**: 空文件是 codegen 产物的默认行为。
- **复核状态**: 未复核

### [维度08-06] _service.beans.xml 与 app-service.beans.xml 的隐含配置耦合

- **文件**: `nop-code/nop-code-service/src/main/resources/_vfs/nop/code/beans/_service.beans.xml:8-13`
- **严重程度**: P3
- **现状**: BizModel bean 在 `_service.beans.xml` 中定义，但其依赖的 `ICodeIndexService` 在 `app-service.beans.xml` 中定义。如果只加载 `_service.beans.xml` 而不加载 `app-service.beans.xml`，BizModel 的注入将失败。
- **风险**: 配置层面的隐含耦合，但这是 Nop 平台 codegen 的标准模式。
- **建议**: 维持现状，这是平台标准模式。
- **信心水平**: 确定
- **误报排除**: 平台 codegen 的标准输出格式。
- **复核状态**: 未复核
