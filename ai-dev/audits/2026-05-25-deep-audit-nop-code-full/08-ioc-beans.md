# 维度08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] _lang-typescript.beans.xml 缺少 xmlns:ioc 命名空间声明

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:7`
- **证据片段**:
```xml
<!-- 根元素缺少 xmlns:ioc="ioc" -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       xmlns="http://www.springframework.org/schema/beans">
    <bean id="io.nop.code.lang.typescript.TypeScriptLanguageAdapter"
          class="io.nop.code.lang.typescript.TypeScriptLanguageAdapter"
          ioc:default="true"/>
</beans>
```
- **严重程度**: P1
- **现状**: `<bean>` 元素使用了 `ioc:default="true"` 属性，但根元素未声明 `xmlns:ioc="ioc"`。同模块的 `_lang-java.beans.xml` 和 `_lang-python.beans.xml` 均正确声明。
- **风险**: 严格 XML 校验下报错。NopIoC 宽松解析可能掩盖此问题。
- **建议**: 添加 `xmlns:ioc="ioc"` 声明。
- **误报排除**: 不是平台标准模式，是命名空间声明遗漏。
- **复核状态**: 未复核

---

### [维度08-02] app-service.beans.xml 缺少 xmlns:ioc 命名空间声明

- **文件**: `nop-code/nop-code-service/src/main/resources/_vfs/nop/code/beans/app-service.beans.xml:11`
- **证据片段**:
```xml
<!-- 根元素缺少 xmlns:ioc="ioc" -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       xmlns="http://www.springframework.org/schema/beans">
    <bean id="codeIndexService" class="io.nop.code.service.impl.CodeIndexService"
          ioc:default="true"/>
</beans>
```
- **严重程度**: P1
- **现状**: 手写 beans 文件使用了 `ioc:default="true"` 但缺少 `xmlns:ioc="ioc"` 声明。参考项目 nop-wf 正确声明了该命名空间。
- **风险**: 同维度08-01。
- **建议**: 添加 `xmlns:ioc="ioc"` 声明。
- **误报排除**: 参考模块正确声明了该命名空间。
- **复核状态**: 未复核

---

### [维度08-03] TypeScriptLanguageAdapter Bean 配置与 Java/Python 不一致

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:5-7`
- **证据片段**:
```xml
<!-- TypeScript: 缺少 ioc:bean-type -->
<bean id="...TypeScriptLanguageAdapter" class="...TypeScriptLanguageAdapter" ioc:default="true"/>
<!-- Java: 有 ioc:bean-type -->
<bean id="...JavaLanguageAdapter" class="...JavaLanguageAdapter"
      ioc:bean-type="io.nop.code.core.analyzer.ILanguageAdapter"/>
```
- **严重程度**: P2
- **现状**: TypeScript 使用 `ioc:default` 而非 `ioc:bean-type`，注册策略与 Java/Python 不一致。
- **风险**: IoC 类型收集注入时 TypeScript 适配器不会出现在 `List<ILanguageAdapter>` 中。
- **建议**: 统一为 `ioc:bean-type` 模式。
- **误报排除**: 当前无运行时影响（手动注册），但设计不一致。
- **复核状态**: 未复核

---

### [维度08-04] LanguageAdapterRegistry 的 @Inject setAdapters 是死代码

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/adapter/LanguageAdapterRegistry.java:19-24`
- **证据片段**:
```java
@Inject
public void setAdapters(List<ILanguageAdapter> adapterList) {
    for (ILanguageAdapter adapter : adapterList) {
        registerAdapter(adapter);
    }
}
```
- **严重程度**: P3
- **现状**: `CodeIndexService` 通过 `new LanguageAdapterRegistry()` 创建实例，IoC 不会对非托管对象执行 `@Inject`。所有适配器都是手动注册的。
- **风险**: 死代码，可能误导开发者。
- **建议**: 移除 `@Inject` 注解，或改为 IoC 管理。
- **误报排除**: `@Inject` 在非 IoC 管理对象上永远不会触发。
- **复核状态**: 未复核

---

## 审计通过项

| 检查项 | 结果 |
|--------|------|
| 无手写修改生成文件 | ✅ |
| @Inject 字段为 protected | ✅ |
| 无 Spring 注解误用 | ✅ |
| _module 文件 | ✅ 4个 |
| BizModel bean 命名 | ✅ |
| import 路径 | ✅ |
| 无循环依赖 | ✅ |
