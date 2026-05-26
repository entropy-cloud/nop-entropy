# 维度10：XDSL 与 XLang 正确性 + 维度11：XMeta 与 BizModel 对齐

## 维度10 第 1 轮（初审）

### [维度10-01] TypeScript 语言适配器 beans 缺少 ioc:bean-type 注册

- **文件**: `nop-code/nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml`
- **证据片段**:
```xml
<!-- TypeScript: 缺少 ioc:bean-type -->
<bean id="...TypeScriptLanguageAdapter" class="...TypeScriptLanguageAdapter" ioc:default="true"/>
<!-- Java: 有 ioc:bean-type -->
<bean id="...JavaLanguageAdapter" class="...JavaLanguageAdapter"
      ioc:bean-type="io.nop.code.core.analyzer.ILanguageAdapter"/>
```
- **严重程度**: P2
- **现状**: TypeScript 适配器缺少类型注册，与 Java/Python 不一致。
- **风险**: IoC 自动发现场景下 TypeScript 适配器缺失。
- **建议**: 添加 `ioc:bean-type="io.nop.code.core.analyzer.ILanguageAdapter"`。
- **复核状态**: 未复核

---

### [维度10-02] _lang-typescript.beans.xml 和 app-service.beans.xml 缺少 xmlns:ioc 命名空间

- **文件**: `_lang-typescript.beans.xml`, `app-service.beans.xml`
- **严重程度**: P3
- **现状**: 使用 `ioc:default="true"` 但未声明 `xmlns:ioc="ioc"`。
- **建议**: 补充命名空间声明。
- **复核状态**: 未复核

---

## 维度11 第 1 轮（初审）

### [维度11-01] NopCodeSymbolBizModel 的 @BizLoader usages() 与 xmeta usages 语义冲突

- **文件**: `NopCodeSymbolBizModel.java:96-108` 和 `_NopCodeSymbol.xmeta:190-195`
- **证据片段**:
```java
// BizModel: 返回 List<AnnotationUsageDTO>
@BizLoader
public List<AnnotationUsageDTO> usages(@ContextSource SymbolDTO symbol, ...) { ... }
```
```xml
<!-- xmeta: usages 定义为 to-many 关系，返回 NopCodeUsage 实体集合 -->
<prop name="usages" ext:kind="to-many" ...>
    <schema><item bizObjName="NopCodeUsage"/></schema>
</prop>
```
- **严重程度**: P2
- **现状**: xmeta 声明返回 NopCodeUsage 实体，@BizLoader 实际返回 AnnotationUsageDTO，类型完全不同。
- **风险**: GraphQL schema 声明类型与实际返回类型不一致。
- **建议**: 在 xmeta 中将 usages 改为计算字段，或重命名 @BizLoader 方法避免冲突。
- **复核状态**: 未复核

---

### [维度11-02] @BizLoader 使用非实体类型 SymbolDTO 作为 @ContextSource

- **文件**: `NopCodeSymbolBizModel.java:96-120`
- **严重程度**: P2
- **现状**: CrudBizModel<NopCodeSymbol> 的 @BizLoader 接收 SymbolDTO（非 ORM 实体）。
- **风险**: 运行时类型转换异常。
- **建议**: 改为 `@ContextSource NopCodeSymbol` 或使用 `forType = SymbolDTO.class`。
- **复核状态**: 未复核

---

### [维度11-03] NopCodeFileBizModel 的 types/outline @BizLoader 缺少 xmeta 定义

- **文件**: `NopCodeFileBizModel.java:60-90`
- **严重程度**: P3
- **现状**: `CodeFileAnalysisResult` 没有 xmeta，types/outline 字段不在 GraphQL schema 中。
- **建议**: 为 CodeFileAnalysisResult 建立 xmeta。
- **复核状态**: 未复核

---

### [维度11-04] IncrementalStatus 内部类无 xmeta 定义

- **文件**: `NopCodeIndexBizModel.java:248-303`
- **严重程度**: P3
- **现状**: @BizQuery 返回类型无 xmeta schema。
- **建议**: 为 IncrementalStatus 定义 xmeta。
- **复核状态**: 未复核
