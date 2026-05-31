# Audit Dimension 04: ORM Model and Entity Design — nop-code

**Source Model**: `nop-code/model/nop-code.orm.xml` (883 lines)
**Total Entities**: 11

---

### [维度04-01] Missing Reverse to-many Relations — Orphaned refPropName References

- **File**: `nop-code/model/nop-code.orm.xml:440-446,501-514`
- **Evidence Snippet**:
```xml
<!-- NopCodeUsage points to NopCodeFile with refPropName="usages", but NopCodeFile has no "usages" to-many -->
<to-one name="file" refDisplayName="引用" refEntityName="io.nop.code.dao.entity.NopCodeFile"
        refPropName="usages" tagSet="pub">
    <join><on leftProp="fileId" rightProp="id"/></join>
</to-one>

<!-- NopCodeCall points to NopCodeSymbol with refPropName="callees", but NopCodeSymbol has no "callees" to-many -->
<to-one name="caller" refDisplayName="被调用者" refEntityName="io.nop.code.dao.entity.NopCodeSymbol"
        refPropName="callees" tagSet="pub">
    <join><on leftProp="callerId" rightProp="id"/></join>
</to-one>
```
- **Severity**: P1
- **Current State**: 8 refPropName references across child entities point to relation properties that do not exist on their target entities.
- **Risk**: Any bidirectional navigation (e.g., `symbol.getCallees()`, `file.getCalls()`) will fail at runtime.
- **Recommendation**: Add missing to-many relations on NopCodeSymbol and NopCodeFile.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified by exhaustive search — relation names used as refPropName values are never declared as `<to-many>` on target entities.
- **Review Status**: Not reviewed

---

### [维度04-02] Dict `code/index_status` Missing Values Used at Runtime

- **File**: `nop-code/model/nop-code.orm.xml:64-70` vs `nop-code-service/.../CodeIndexService.java:1960,2058,2615`
- **Evidence Snippet**:
```xml
<dict label="索引状态" name="code/index_status" valueType="int">
    <option code="CREATED" label="已创建" value="10"/>
    <option code="INDEXING" label="索引中" value="20"/>
    <option code="READY" label="就绪" value="30"/>
    <option code="ERROR" label="错误" value="40"/>
</dict>
```
```java
indexEntity.setStatus("COMPLETED");      // line 1960, 2058
flowEntity.setStatus("DETECTED");        // line 2615
```
- **Severity**: P1
- **Current State**: Service code stores status values "COMPLETED" and "DETECTED" that do not exist in the dict.
- **Risk**: Auto-generated CRUD UI shows blank/unknown labels for these statuses.
- **Recommendation**: Add missing dict options or change service code to use existing codes.
- **Confidence**: Certain
- **False Positive Exclusion**: Cross-referenced 3 setStatus() sites against all 4 dict options — "COMPLETED" and "DETECTED" definitively absent.
- **Review Status**: Not reviewed

---

### [维度04-03] Dict valueType="int" but Service Stores String Enum Names

- **File**: `nop-code/model/nop-code.orm.xml:23` vs `nop-code-service/.../CodeIndexService.java:2100,2188`
- **Evidence Snippet**:
```xml
<dict label="符号类型" name="code/symbol_kind" valueType="int">
    <option code="CLASS" label="类" value="10"/>
    <option code="METHOD" label="方法" value="50"/>
</dict>
```
```java
symEntity.setKind(sym.getKind() != null ? sym.getKind().name() : null);  // stores "CLASS"
```
- **Severity**: P2
- **Current State**: All 6 dicts declare `valueType="int"` but entity columns are VARCHAR and service code stores enum names.
- **Risk**: Generated int constants are dead code; dual truth problem if auto-generated forms store int values.
- **Recommendation**: Change dicts to `valueType="string"` matching current runtime behavior.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified generated constants (int), entity getters (String), and service code (enum.name()).
- **Review Status**: Not reviewed

---

### [维度04-04] cascadeDelete Missing on NopCodeFlow → NopCodeFlowMembership

- **File**: `nop-code/model/nop-code.orm.xml:134-139,749-754`
- **Evidence Snippet**:
```xml
<!-- NopCodeIndex cascades delete to NopCodeFlow -->
<to-many ... cascadeDelete="true">

<!-- NopCodeFlow does NOT cascade delete to NopCodeFlowMembership -->
<to-many displayName="成员" name="memberships" ...>
    <join><on leftProp="id" rightProp="flowId"/></join>
</to-many>
```
- **Severity**: P1
- **Current State**: Deleting a NopCodeIndex leaves orphaned NopCodeFlowMembership rows (no indexId on FlowMembership for Index cascade).
- **Risk**: Data integrity corruption — orphaned records.
- **Recommendation**: Add `cascadeDelete="true"` to NopCodeFlow.memberships.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified NopCodeFlowMembership has only flowId and symbolId (no indexId), so Index-level cascade cannot reach it.
- **Review Status**: Not reviewed

---

### [维度04-05] Inconsistent Audit Field Naming and Missing Entity-Level Audit Attributes

- **File**: `nop-code/model/nop-code.orm.xml:726-733,841-842`
- **Evidence Snippet**:
```xml
<!-- NopCodeFlow uses non-standard naming -->
<column code="CREATED_TIME" name="createdTime" .../>  <!-- should be createTime -->
<column code="MODIFIED_TIME" name="modifiedTime" .../> <!-- should be updateTime -->
```
- **Severity**: P2
- **Current State**: Three entities have partial audit columns with inconsistent naming. None use standard entity-level attributes.
- **Risk**: Framework won't auto-populate audit fields; naming inconsistency hinders maintenance.
- **Recommendation**: Standardize using entity-level attributes: `createTimeProp`, `updateTimeProp`, etc.
- **Confidence**: Certain
- **False Positive Exclusion**: Compared with nop-auth reference module using entity-level audit attributes.
- **Review Status**: Not reviewed

---

### [维度04-06] NopCodeSemanticEdge delFlag Without Entity-Level Logical Delete Configuration

- **File**: `nop-code/model/nop-code.orm.xml:813-815,843-844`
- **Evidence Snippet**:
```xml
<entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" ...>
    <!-- No useLogicalDelete or deleteFlagProp -->
    <column code="DEL_FLAG" name="delFlag" stdDataType="boolean" stdSqlType="TINYINT" stdDomain="boolFlag"/>
```
- **Severity**: P2
- **Current State**: Entity has delFlag column but lacks `useLogicalDelete="true"` and `deleteFlagProp="delFlag"`.
- **Risk**: `dao.deleteEntity()` will physically DELETE instead of soft-deleting.
- **Recommendation**: Add `deleteFlagProp="delFlag" useLogicalDelete="true"` to entity.
- **Confidence**: Certain
- **False Positive Exclusion**: Confirmed nop-auth entities use these same attributes for delFlag.
- **Review Status**: Not reviewed

---

### [维度04-07] NopCodeIndex Missing Database Indexes on Queryable Fields

- **File**: `nop-code/model/nop-code.orm.xml:91-170`
- **Evidence Snippet**:
```xml
<entity ... tableName="nop_code_index">
    <columns>...</columns>
    <relations>...</relations>
    <!-- NO <indexes> section -->
</entity>
```
- **Severity**: P2
- **Current State**: NopCodeIndex is the root entity with 9 cascade-delete children but has zero secondary indexes. All other entities have at least 2 indexes.
- **Risk**: Queries filtering by name, rootPath, or status cause full table scans.
- **Recommendation**: Add indexes for common query combinations.
- **Confidence**: Certain
- **False Positive Exclusion**: 10 of 11 entities have indexes sections. NopCodeIndex is the sole exception.
- **Review Status**: Not reviewed

---

### [维度04-08] NopCodeSemanticEdge Missing Comment Element

- **File**: `nop-code/model/nop-code.orm.xml:813-881`
- **Evidence Snippet**:
```xml
<entity ...>
    <columns>...</columns>
    <indexes>...</indexes>
    <!-- No <comment> element -->
```
- **Severity**: P3
- **Current State**: Only entity out of 11 that lacks a `<comment>` element.
- **Risk**: Auto-generated DDL lacks table comment.
- **Recommendation**: Add `<comment>语义边关系</comment>`.
- **Confidence**: Certain
- **False Positive Exclusion**: Grep confirmed all other 10 entities have comment elements.
- **Review Status**: Not reviewed

---

### [维度04-09] English i18n File Has All Null Values

- **File**: `nop-code-meta/src/main/resources/_vfs/i18n/en/_nop-code.i18n.yaml:1-235`
- **Evidence Snippet**:
```yaml
entity:
  label:
    NopCodeAnnotationUsage: null
    NopCodeCall: null
    NopCodeFile: null
```
- **Severity**: P3
- **Current State**: English i18n file has 235 lines with all values null.
- **Risk**: English-locale UI shows raw property names.
- **Recommendation**: Add English translations via `i18n-en:displayName` in ORM model or populate i18n file.
- **Confidence**: Certain
- **False Positive Exclusion**: Verified complete file — every key has null value.
- **Review Status**: Not reviewed

---

### [维度04-10] NopCodeCall.callType and NopCodeSemanticEdge.relationType Without Dict Reference

- **File**: `nop-code/model/nop-code.orm.xml:488-489,827-828`
- **Evidence Snippet**:
```xml
<column code="CALL_TYPE" displayName="调用类型" name="callType" stdSqlType="VARCHAR"/>
<column code="RELATION_TYPE" displayName="关系类型" name="relationType" stdSqlType="VARCHAR"/>
```
- **Severity**: P3
- **Current State**: Constrained string fields without dict definitions. Corresponding Java enums define finite value sets.
- **Risk**: Auto-generated UI shows raw text input instead of dropdown.
- **Recommendation**: Create dicts and add ext:dict references.
- **Confidence**: Likely
- **False Positive Exclusion**: Java enums exist with known values stored as .name() strings.
- **Review Status**: Not reviewed

---

### [维度04-11] NopCodeDependency.resolved Field Typed as SMALLINT/int Instead of Boolean

- **File**: `nop-code/model/nop-code.orm.xml:666-667`
- **Evidence Snippet**:
```xml
<column code="RESOLVED" displayName="是否已解析" name="resolved"
        stdDataType="int" stdSqlType="SMALLINT"/>
```
- **Severity**: P3
- **Current State**: Boolean-semantic field ("是否已解析") uses int/SMALLINT instead of boolean/TINYINT/boolFlag.
- **Risk**: UI renders numeric input instead of checkbox.
- **Recommendation**: Change to `stdDataType="boolean" stdSqlType="TINYINT" stdDomain="boolFlag"`.
- **Confidence**: Likely
- **False Positive Exclusion**: Every other boolean field uses boolean/TINYINT.
- **Review Status**: Not reviewed

---

## Summary by Severity

| Severity | Count | Finding IDs |
|----------|-------|-------------|
| P1 | 3 | 04-01, 04-02, 04-04 |
| P2 | 4 | 04-03, 04-05, 04-06, 04-07 |
| P3 | 4 | 04-08, 04-09, 04-10, 04-11 |

## 维度复核结论

（待复核）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 04-01 | P1 | model/nop-code.orm.xml | 8个 refPropName 指向不存在的反向关系 |
| 04-02 | P1 | model/nop-code.orm.xml + CodeIndexService.java | dict 缺少运行时使用的 COMPLETED/DETECTED |
| 04-04 | P1 | model/nop-code.orm.xml | Flow→FlowMembership 缺少 cascadeDelete |
| 04-03 | P2 | model/nop-code.orm.xml | dict valueType=int 但实际存储 string enum |
| 04-05 | P2 | model/nop-code.orm.xml | 审计字段命名不一致、缺少实体级属性 |
| 04-06 | P2 | model/nop-code.orm.xml | SemanticEdge 有 delFlag 但缺 useLogicalDelete |
| 04-07 | P2 | model/nop-code.orm.xml | NopCodeIndex 缺少数据库索引 |
| 04-08 | P3 | model/nop-code.orm.xml | SemanticEdge 缺少 comment |
| 04-09 | P3 | nop-code-meta/i18n/en/ | English i18n 全部为 null |
| 04-10 | P3 | model/nop-code.orm.xml | callType/relationType 缺少 dict |
| 04-11 | P3 | model/nop-code.orm.xml | resolved 字段类型应为 boolean |
