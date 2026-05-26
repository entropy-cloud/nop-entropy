# 维度02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] CodeIndexService 承担过多职责（God Class）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:92-2784`
- **证据片段**:
```java
public class CodeIndexService implements ICodeIndexService {
    private final Map<String, AnalysisCache> analysisCacheMap = new HashMap<>();
    protected final LanguageAdapterRegistry registry;
    protected final ProjectAnalyzer analyzer;
    @Inject protected IDaoProvider daoProvider;
    @Inject protected IOrmTemplate ormTemplate;
    // 70+方法：索引管理、文件查询、符号搜索、类型层级、调用链、图分析、依赖图、执行流、变更分析、ORM持久化、DTO转换
}
```
- **严重程度**: P2
- **现状**: 2784行巨型类，承载7+个功能域。
- **风险**: 任何单一功能域变更可能影响其他功能域；难以独立测试。
- **建议**: 按功能域拆分为独立服务类。
- **误报排除**: 基线已确认该类体量。
- **复核状态**: 未复核

---

### [维度02-02] 语言特定 ImportResolver 放置在 nop-code-core 而非对应 lang 模块

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/resolver/JavaImportResolver.java`, `PythonImportResolver.java`, `TypeScriptImportResolver.java`
- **证据片段**:
```java
// JavaImportResolver 硬编码 "JAVA" 语言标识，处理 Java import 语法
// 但 nop-code-lang-java 模块已存在，是语言特定代码的自然归属地
```
- **严重程度**: P2
- **现状**: 接口 `IImportResolver` 在 core 中正确，但三个具体实现放在 core 中。
- **风险**: 添加新语言需修改 core 模块，违反 OCP。
- **建议**: 移至各自 lang 模块，通过 IoC 注册。
- **误报排除**: 三个类有明确的语言特属性。
- **复核状态**: 未复核

---

### [维度02-03] CodeIndexService 绕过 IoC 硬编码实例化语言适配器和 ImportResolver

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:156-179`
- **证据片段**:
```java
// 构造函数直接 new，绕过 IoC
registry = new LanguageAdapterRegistry();
registry.registerAdapter(new JavaLanguageAdapter());
registry.registerAdapter(new PythonLanguageAdapter());
registry.registerAdapter(new TypeScriptLanguageAdapter());
registerImportResolvers();
```
- **严重程度**: P2
- **现状**: `LanguageAdapterRegistry` 的 `@Inject setAdapters()` IoC 机制被绕过。
- **风险**: 新增语言适配器需修改 CodeIndexService 源码。
- **建议**: 通过 IoC 注入替代直接实例化。
- **误报排除**: 框架提供了正确的 IoC 扩展点但未被使用。
- **复核状态**: 未复核

---

### [维度02-04] nop-code-lang-typescript beans.xml 注册缺少 ioc:bean-type

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:5-7`
- **严重程度**: P2
- **现状**: TypeScript 使用 `ioc:default` 而非 `ioc:bean-type`，与 Java/Python 不一致。
- **建议**: 添加 `ioc:bean-type="io.nop.code.core.analyzer.ILanguageAdapter"`。
- **复核状态**: 未复核

---

### [维度02-05] 33 个 DTO 定义在 service 模块而非 api 模块

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/`
- **严重程度**: P3
- **现状**: `nop-code-api` 实质上是未激活的 stub 模块，所有 DTO 在 service 层。
- **建议**: 长期规划将 DTO 迁移至 api 模块。
- **复核状态**: 未复核

---

### [维度02-06] nop-code-api 模块 POM 配置与项目不一致

- **文件**: `nop-code/nop-code-api/pom.xml`
- **严重程度**: P3
- **现状**: 无 parent 引用，java.version=11 vs 项目 Java 21。
- **建议**: 补充 `<parent>` 引用。
- **复核状态**: 未复核

---

## 维度06：Delta 定制合规性

**检查结果：0 个问题。** 所有 Delta 式定制（x:extends + x:override）在模块内部正确实现。不存在 _delta 目录（作为源模块是正确的）。
