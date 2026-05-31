# Audit Dimension 05: Code Generation Pipeline Completeness — nop-code

## Pipeline Chain Closure Verification

```
model/nop-code.orm.xml
    |
    v  [gen-orm.xgen]
nop-code-dao/_app.orm.xml + entity/_gen/*.java + biz/I*Biz.java + Constants
    |
    v  [gen-meta.xgen]
nop-code-meta/model/{Entity}/_{Entity}.xmeta + dict/code/*.dict.yaml
    |
    v  [gen-i18n.xgen]
nop-code-meta/i18n/{locale}/_nop-code.i18n.yaml
    |
    v  [gen-page.xgen]
nop-code-web/pages/{Entity}/_gen/_{Entity}.view.xml + views + pages
```

The chain is **correctly closed**. 11 entities fully propagated through all stages.

---

### [维度05-01] nop-code-api POM Missing Parent Reference and Java Version Inconsistency

- **File**: `nop-code/nop-code-api/pom.xml:1-45`
- **Evidence Snippet**:
```xml
<properties>
    <java.version>11</java.version>
</properties>
<!-- NO <parent> section -->
```
- **Severity**: P2
- **Current State**: nop-code-api has no parent reference; uses java.version=11 while siblings inherit java.version=21.
- **Risk**: Bytecode target inconsistency; dependency version drift.
- **Recommendation**: Add parent section matching siblings.
- **Confidence**: Certain
- **False Positive Exclusion**: Direct comparison with sibling poms confirms all others have parent.
- **Review Status**: Not reviewed

---

### [维度05-02] nop-code-api Module is Empty — No Source Code or Resources

- **File**: `nop-code/nop-code-api/` (directory)
- **Evidence Snippet**: No src/ directory, no _module file.
- **Severity**: P3
- **Current State**: Module in reactor but contains no source code.
- **Risk**: Dead module adds build time; confuses developers.
- **Recommendation**: Populate or remove.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified zero .java files.
- **Review Status**: Not reviewed

---

### [维度05-03] NopCodeSemanticEdge.sourceSymbol/targetSymbol Relations Lack refPropName

- **File**: `nop-code/model/nop-code.orm.xml:867-876`
- **Evidence Snippet**:
```xml
<to-one name="sourceSymbol" refEntityName="...NopCodeSymbol" tagSet="pub">
    <join><on leftProp="sourceSymbolId" rightProp="id"/></join>
</to-one>
<to-one name="targetSymbol" refEntityName="...NopCodeSymbol" tagSet="pub">
    <join><on leftProp="targetSymbolId" rightProp="id"/></join>
</to-one>
```
- **Severity**: P2
- **Current State**: No refPropName on sourceSymbol/targetSymbol, so no reverse relations on NopCodeSymbol.
- **Risk**: Cannot navigate from Symbol to its semantic edges via ORM.
- **Recommendation**: Add refPropName="sourceSemanticEdges"/"targetSemanticEdges".
- **Confidence**: Likely
- **False Positive Exclusion**: Verified _app.orm.xml confirms no reverse relations for SemanticEdge on NopCodeSymbol.
- **Review Status**: Not reviewed

---

## Summary

| Severity | Count | IDs |
|----------|-------|-----|
| P2 | 2 | 05-01, 05-03 |
| P3 | 1 | 05-02 |

Generation pipeline is structurally complete and correct for all 11 entities.

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 05-01 | P2 | nop-code-api/pom.xml | 缺少 parent 声明，Java 版本不一致 |
| 05-02 | P3 | nop-code-api/ | 空模块无源码 |
| 05-03 | P2 | model/nop-code.orm.xml | SemanticEdge 关系缺少 refPropName |
