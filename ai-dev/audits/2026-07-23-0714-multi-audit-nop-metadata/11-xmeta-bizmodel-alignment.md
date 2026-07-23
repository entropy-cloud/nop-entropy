> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 11: XMeta & BizModel Alignment

### [Dimension11-01] NopMetaDataSource connectionConfigComponent remains exposed when parent connectionConfig is restricted

- **File**: `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/nop/metadata/model/NopMetaDataSource/_NopMetaDataSource.xmeta:80-83`
- **Evidence**:
  ```xml
  <!-- Retention NopMetaDataSource.xmeta restricts connectionConfig -->
  <prop name="connectionConfig" published="false" insertable="false" updatable="false" .../>
  <!-- But connectionConfigComponent is NOT restricted -->
  ```
- **Severity**: P2
- **Status**: Retention xmeta restricts `connectionConfig` (JDBC credentials JSON) but the companion `connectionConfigComponent` retains default `insertable="true"` + `updatable="true"`.
- **Risk**: Credential data may leak through the component editor path via explicit GraphQL selection.
- **Suggestion**: Add override restricting `connectionConfigComponent` with the same flags.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension11-02] Duplicate core constant STATUS_MANUAL in NopMetaReconciliationResultBizModel

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaReconciliationResultBizModel.java:59`
- **Evidence**:
  ```java
  private static final String STATUS_MANUAL = "MANUAL";  // also in _NopMetadataCoreConstants:374
  ```
- **Severity**: P3
- **Status**: Duplicates `_NopMetadataCoreConstants.RECONCILIATION_STATUS_MANUAL`.
- **Suggestion**: Import core constants and replace usage.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension11-03] 38/42 retention xmeta files have empty `<props/>`

- **File**: All `NopMeta*.xmeta` retention files except `NopMetaDataSource`, `NopMetaTagLabel`, `NopMetaDataContract`, and one more
- **Evidence**:
  ```xml
  <meta x:extends="_NopMeta*.xmeta">
      <props/>
  </meta>
  ```
- **Severity**: P3
- **Status**: Retention xmeta files provide no field-level access control overrides for 38 entities. Relies entirely on generated defaults.
- **Suggestion**: Add explicit retention overrides for entities with sensitive fields.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension11-04] computeQualityScore bypasses xmeta insertable validation

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityScoreBizModel.java:62-84`
- **Evidence**:
  ```java
  NopMetaQualityScore row = dao().newEntity();
  row.setMetaTableId(metaTableId);
  ...
  dao().saveEntity(row);  // Direct DAO save, bypasses xmeta validation
  ```
- **Severity**: P2
- **Status**: Creates and persists entity via `dao().saveEntity()` instead of `CrudBizModel.save()`, bypassing xmeta field-level validation.
- **Risk**: If xmeta insertable flags are tightened, this method silently fails at ORM flush rather than failing fast at validation.
- **Suggestion**: Route through `bizObjectManager().invoke("save", ...)` or add explicit validation.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension11-05] Redundant @GraphQLReturn on NopMetaGlossaryTermBizModel.update()

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaGlossaryTermBizModel.java:51-53`
- **Evidence**:
  ```java
  @GraphQLReturn(bizObjName = "NopMetaGlossaryTerm")
  public NopMetaGlossaryTerm update(...)
  ```
- **Severity**: P3
- **Status**: `@GraphQLReturn` is redundant because return type is already resolvable from method signature and `@BizModel` class annotation.
- **Suggestion**: Remove the redundant annotation.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension11-06] sourceSql/buildSql sensitivity not flagged for event redaction

- **File**: `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/nop/metadata/model/NopMetaTable/NopMetaTable.xmeta`
- **Evidence**: NopMetaTable retention xmeta does not mark `sourceSql` or `buildSql` with tagSet="sensitive" for event redaction.
- **Severity**: P2
- **Status**: Save/delete overrides publish full entity snapshots. `sourceSql` and `buildSql` contain SQL queries that may expose internal schema names and business logic.
- **Suggestion**: Mark `sourceSql` and `buildSql` with `tagSet="sensitive"` in retention xmeta for event redaction.
- **Confidence**: likely
- **Review Status**: unreviewed

---

## Summary

| ID | Finding | Severity | Confidence |
|----|---------|----------|------------|
| 11-01 | connectionConfigComponent not restricted | P2 | certain |
| 11-02 | Duplicate core constant | P3 | certain |
| 11-03 | 38/42 retention xmeta empty `<props/>` | P3 | likely |
| 11-04 | computeQualityScore bypasses xmeta validation | P2 | likely |
| 11-05 | Redundant @GraphQLReturn | P3 | certain |
| 11-06 | sourceSql/buildSql sensitivity not flagged | P2 | likely |
