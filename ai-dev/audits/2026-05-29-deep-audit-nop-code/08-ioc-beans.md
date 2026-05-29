# 维度 08：IoC 与 Bean 配置

**审计日期**: 2026-05-29
**审计范围**: nop-code 全部 beans.xml、_module 文件、Java @Inject 模式

---

## 第 1 轮（初审）

### [维度08-01] CodeIndexService 构造函数硬编码 new 语言适配器，绕过 IoC 容器

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:162-169`
- **证据片段**:
  ```java
  public CodeIndexService() {
      this.registry = new LanguageAdapterRegistry();
      this.registry.registerAdapter(new JavaLanguageAdapter());
      this.registry.registerAdapter(new PythonLanguageAdapter());
      this.registry.registerAdapter(new TypeScriptLanguageAdapter());
      this.analyzer = new ProjectAnalyzer(registry);
      registerSemanticExtractors();
      registerImportResolvers();
  }
  ```
- **严重程度**: P2
- **现状**: 构造函数直接 new 语言适配器，绕过 IoC。同时 _lang-java/_lang-python/_lang-typescript beans 文件中注册了这些 bean，LanguageAdapterRegistry 有 @Inject setAdapters(List<ILanguageAdapter>) setter，但运行时从未被使用。
- **风险**: 新增语言适配器需同时修改构造函数和 beans 文件，易不同步。beans 文件成为死配置。
- **建议**: 统一为 IoC 模式或移除 beans 文件中的死配置。
- **信心水平**: 高
- **误报排除**: 明确的 IoC 一致性问题。
- **复核状态**: 未复核

### [维度08-02] _lang-typescript.beans.xml 缺少 xsi:schemaLocation 声明

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:1-3`
- **证据片段**:
  ```xml
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc"
         xmlns="http://www.springframework.org/schema/beans">
  ```
  对比 _lang-java.beans.xml 有 xsi:schemaLocation。
- **严重程度**: P3
- **现状**: 缺少 xmlns:xsi 和 xsi:schemaLocation，与同组文件风格不一致。功能不影响运行。
- **风险**: 可能影响 XML IDE 编辑体验。
- **建议**: 补齐声明以保持一致。
- **信心水平**: 高
- **误报排除**: Nop 使用 x:schema 做校验，xsi:schemaLocation 非功能必需。
- **复核状态**: 未复核

### [维度08-03] app-service.beans.xml 未 import 三个 _lang-*.beans.xml

- **文件**: `nop-code/nop-code-service/src/main/resources/_vfs/nop/code/beans/app-service.beans.xml:7-12`
- **证据片段**:
  ```xml
  <import resource="_dao.beans.xml"/>
  <import resource="_service.beans.xml"/>
  <bean id="io.nop.code.service.impl.CodeIndexService" ioc:default="true"
        class="io.nop.code.service.impl.CodeIndexService"/>
  ```
- **严重程度**: P3
- **现状**: 三个 _lang-*.beans.xml 未被 import。与 08-01 关联。
- **风险**: 若切换到 IoC 模式，语言适配器不会被注册。
- **建议**: 若采用 IoC 模式，增加 import；若维持现状，可忽略。
- **信心水平**: 高
- **误报排除**: 缺少 import 是事实。
- **复核状态**: 未复核

### [维度08-04] 三个 lang 模块缺少 _module 文件

- **文件**: nop-code-lang-java/lang-python/lang-typescript 的 _vfs/nop/code/ 目录
- **证据片段**: 对比 nop-code-dao 有 _module 文件，三个 lang 模块没有。
- **严重程度**: P3
- **现状**: 三个 lang 模块的 VFS 目录缺少 _module 文件。
- **风险**: VFS 模块发现可能受影响（取决于 Nop VFS 加载机制）。
- **建议**: 添加空的 _module 文件以保持约定一致。
- **信心水平**: 中
- **误报排除**: 如果 Nop VFS 不依赖 _module 文件做发现，则此项可排除。
- **复核状态**: 未复核

## 合规项

- 所有 @Inject 字段均为 protected 可见性
- 无 Spring 专有注解误用（@Autowired、@Value 均未出现）
- 无 @InjectValue 使用
- _service.beans.xml 格式符合 codegen 产物模式
- bean 命名遵循 Nop 约定
