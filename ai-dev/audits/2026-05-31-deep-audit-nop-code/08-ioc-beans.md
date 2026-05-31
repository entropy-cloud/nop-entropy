# Audit Dimension 08: IoC and Bean Configuration — nop-code

---

### [维度08-01] Stale VFS Index Missing Lang Beans and Model Entries

- **File**: `nop-code-app/src/main/resources/nop-vfs-index.txt:255-258`
- **Evidence Snippet**:
```
/nop/code/beans/_dao.beans.xml
/nop/code/beans/_lang-java.beans.xml
/nop/code/beans/_service.beans.xml
/nop/code/beans/app-service.beans.xml
```
- **Severity**: P1
- **Current State**: VFS index missing `_lang-typescript.beans.xml`, `_lang-python.beans.xml`, and entries for SemanticEdge/Flow/FlowMembership models and pages.
- **Risk**: Runtime discovery failures for TypeScript/Python adapters and missing CRUD UI for 3 entities.
- **Recommendation**: Rebuild nop-code-app to regenerate VFS index.
- **Confidence**: Certain
- **False Positive Exclusion**: Source files exist on disk but VFS index is objectively incomplete.
- **Review Status**: Not reviewed

---

### [维度08-02] Lang Module Beans Never Imported — Dead IoC Definitions

- **File**: `nop-code-service/src/main/resources/_vfs/nop/code/beans/app-service.beans.xml:7-9`
- **Evidence Snippet**:
```xml
<import resource="_dao.beans.xml"/>
<import resource="_service.beans.xml"/>
<!-- NO import of _lang-*.beans.xml -->
```
- **Severity**: P2
- **Current State**: app-service.beans.xml does not import any _lang-*.beans.xml. Adapters are manually registered in CodeIndexService constructor instead.
- **Risk**: IoC injection of ILanguageAdapter will receive empty list.
- **Recommendation**: Add imports or remove dead beans files.
- **Confidence**: Certain
- **False Positive Exclusion**: Traced full import chain — lang beans never loaded.
- **Review Status**: Not reviewed

---

### [维度08-03] @Inject on LanguageAdapterRegistry.setAdapters Is Dead Code

- **File**: `nop-code-core/src/main/java/io/nop/code/core/adapter/LanguageAdapterRegistry.java:19-24`
- **Evidence Snippet**:
```java
@Inject
public void setAdapters(List<ILanguageAdapter> adapterList) {
    for (ILanguageAdapter adapter : adapterList) {
        adapters.put(adapter.getLanguage(), adapter);
    }
}
```
- **Severity**: P2
- **Current State**: @Inject annotation never processed; class instantiated via `new` in CodeIndexService.
- **Risk**: Misleading code suggests IoC injection drives registration.
- **Recommendation**: Either make it an IoC bean or remove @Inject.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified LanguageAdapterRegistry not in any beans.xml.
- **Review Status**: Not reviewed

---

### [维度08-04] Dual Registration: Manual Constructor vs. IoC Beans Redundancy

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:171-178`
- **Evidence Snippet**:
```java
public CodeIndexService() {
    this.registry = new LanguageAdapterRegistry();
    this.registry.registerAdapter(new JavaLanguageAdapter());
    this.registry.registerAdapter(new PythonLanguageAdapter());
    this.registry.registerAdapter(new TypeScriptLanguageAdapter());
```
- **Severity**: P2
- **Current State**: Manual constructor registration and IoC bean definitions both exist.
- **Risk**: If adapter classes add @Inject fields, IoC instances work but manual instances won't.
- **Recommendation**: Choose one approach exclusively.
- **Confidence**: Certain
- **False Positive Exclusion**: Code clearly uses `new` bypassing IoC.
- **Review Status**: Not reviewed

---

### [维度08-05] _lang-typescript.beans.xml Missing xsi:schemaLocation

- **File**: `nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml:3`
- **Severity**: P3
- **Current State**: Missing xmlns:xsi and xsi:schemaLocation that sibling lang beans files have.
- **Recommendation**: Add for consistency.
- **Confidence**: Certain
- **Review Status**: Not reviewed

---

### [维度08-06] Lang Modules Lack _module Files

- **File**: nop-code-lang-java/typescript/python VFS paths
- **Severity**: P3
- **Current State**: No _module files in lang modules; all other nop-code sub-modules have them.
- **Recommendation**: Add empty _module files for consistency.
- **Confidence**: Likely
- **Review Status**: Not reviewed

---

## Positive Findings

- No @Inject on private fields (all use protected)
- No Spring annotation misuse (@Autowired/@Value)
- No circular bean dependencies
- Correct bean naming conventions
- Correct x:schema declarations

## Summary

| Severity | Count | IDs |
|----------|-------|-----|
| P1 | 1 | 08-01 |
| P2 | 3 | 08-02, 08-03, 08-04 |
| P3 | 2 | 08-05, 08-06 |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 08-01 | P1 | nop-code-app/.../nop-vfs-index.txt | VFS 索引过时，缺少 lang beans 和 3 个实体 |
| 08-02 | P2 | app-service.beans.xml | lang beans 未被导入 |
| 08-03 | P2 | LanguageAdapterRegistry.java | @Inject 死代码 |
| 08-04 | P2 | CodeIndexService.java | 手动注册与 IoC 双重冗余 |
| 08-05 | P3 | _lang-typescript.beans.xml | 缺少 xsi:schemaLocation |
| 08-06 | P3 | lang 模块 | 缺少 _module 文件 |
