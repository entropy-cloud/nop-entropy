> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 04: ORM Model & Entity Design

### [Dimension04-01] NopMetaTableJoin Dual FK System Lacks Declarative Constraint Enforcement

- **File**: `nop-metadata/model/nop-metadata.orm.xml:1658-1772`
- **Evidence**:
  ```xml
  <!-- NopMetaTableJoin has two independent FK sets:
      Entity-level: leftEntityId / rightEntityId → NopMetaEntity
      Table-level:  leftTableId  / rightTableId  → NopMetaTable
      These FKs are mutually exclusive: for a given row, EITHER the entity-level
      pair is set OR the table-level pair is set, but never both. -->
  ```
- **Severity**: P2
- **Status**: The ORM entity NopMetaTableJoin has two complete sets of foreign key columns that are mutually exclusive. The invariant cannot be expressed declaratively in the ORM.
- **Risk**: If business logic fails to enforce the mutual exclusion, data integrity is silently corrupted. No database-level CHECK constraint or ORM-level validation exists.
- **Suggestion**: Add a `@OnWrite` or `@OnValidate` hook in the BizModel that enforces exactly-one-pair semantics at the service layer.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension04-02] Cross-Module Dict Reference `wf/approve-status` Creates Hard Runtime Dependency

- **File**: `nop-metadata/model/nop-metadata.orm.xml:2500,3276`
- **Evidence**:
  ```xml
  <column code="APPROVE_STATUS" ... ext:dict="wf/approve-status"/>
  ```
- **Severity**: P2
- **Status**: Two entities (NopMetaDataContract and NopMetaTagLabel) reference `wf/approve-status` dict. The dict definition exists only in `nop-wf-meta`. Neither a local definition nor alias exists in nop-metadata.
- **Risk**: If nop-metadata is deployed without nop-wf, dict resolution fails at runtime. Breaks label fields, form dropdown rendering, and dict-dependent business logic.
- **Suggestion**: Move the dict to a shared module, or define it locally, or document the hard dependency.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension04-03] Inconsistent FK Column Name `entityTableId` on NopMetaDataContract

- **File**: `nop-metadata/model/nop-metadata.orm.xml:2455-2456,2511,1398-1404`
- **Evidence**:
  ```xml
  <column code="ENTITY_TABLE_ID" displayName="关联数据表ID" name="entityTableId" .../>
  <!-- All other entities reference NopMetaTable using column name `metaTableId` -->
  ```
- **Severity**: P2
- **Status**: The FK column in NopMetaDataContract pointing to NopMetaTable is named `entityTableId`. All other entities use `metaTableId`. This is the sole exception among 14 relation pairs.
- **Risk**: Misleading to developers - the name suggests it references NopMetaEntity rather than NopMetaTable. Query building and introspection tooling relying on naming conventions will miss this relationship.
- **Suggestion**: Rename `entityTableId` to `metaTableId` in the source ORM model.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension04-04] Mixed Unique Key Naming Convention on NopMetaDataSource

- **File**: `nop-metadata/model/nop-metadata.orm.xml:406-411`
- **Evidence**:
  ```xml
  <unique-keys>
      <unique-key name="UK_NOP_META_DS_QUERY_SPACE" columns="querySpace" .../>
      <unique-key name="uk_meta_datasource_name" columns="name" .../>
  </unique-keys>
  ```
- **Severity**: P3
- **Status**: NopMetaDataSource defines two unique keys using different naming conventions. 40 out of 41 UKs use `UK_{TABLE}_{COLS}` uppercase pattern. Only `uk_meta_datasource_name` deviates.
- **Risk**: May collide with naming conventions in code generation templates and database migration tooling.
- **Suggestion**: Rename to `UK_NOP_META_DATA_SOURCE_NAME`.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension04-05] NopMetaModelChangedEvent Dual Audit Field Sets Create Ambiguity

- **File**: `nop-metadata/model/nop-metadata.orm.xml:2816-2887`
- **Evidence**:
  ```xml
  <!-- Entity-level audit fields -->
  <column code="VERSION" domain="version" .../>
  <column code="CREATED_BY" domain="createdBy" .../>
  <column code="UPDATE_TIME" domain="updateTime" .../>
  <!-- Event-specific audit fields -->
  <column code="CHANGED_BY" name="changedBy" .../>
  <column code="CHANGE_TIME" name="changeTime" .../>
  ```
- **Severity**: P3
- **Status**: NopMetaModelChangedEvent has two concurrent audit field sets. Entity-level fields track when the event record is inserted/updated. Event-specific fields track the original change. Neither set explains the distinction.
- **Risk**: Developers may confuse the two audit dimensions. `updatedBy/updateTime` have no semantic meaning for an event table.
- **Suggestion**: Add inline comments clarifying the semantic difference. Consider making `updatedBy/updateTime` non-mandatory.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension04-06] NopMetaReconciliationEntity Has Zero ORM Relations Defined

- **File**: `nop-metadata/model/nop-metadata.orm.xml:2695-2748`
- **Evidence**:
  ```xml
  <!-- Complete entity definition - no <relations> block at all -->
  <entity className="...NopMetaReconciliationEntity" ...>
      <columns>...15 columns...</columns>
      <!-- No <relations> section -->
  </entity>
  ```
- **Severity**: P3
- **Status**: NopMetaReconciliationEntity is the only entity in the entire ORM model with no `<relations>` block. No FK to any parent entity exists.
- **Risk**: All queries must be written manually. No GraphQL `props` traversal can reach it. Cascade deletes do not operate on it.
- **Suggestion**: Add a `metaTableId` FK if reconciliation entities are scoped to a logical table, or document the design decision.
- **Confidence**: likely
- **Review Status**: unreviewed

---

### [Dimension04-07] Duplicate `ext:icon="database"` on NopMetaReconciliationEntity Conflicts with Root ORM Icon

- **File**: `nop-metadata/model/nop-metadata.orm.xml:2,2699`
- **Evidence**:
  ```xml
  <orm ext:icon="database" ...>
  <entity ... ext:icon="database" .../>
  ```
- **Severity**: P4
- **Status**: Root `<orm>` uses `ext:icon="database"` and entity NopMetaReconciliationEntity also uses `ext:icon="database"`. No other entity uses this icon value.
- **Suggestion**: Change entity icon to a distinct value such as `git-compare` or `list-checks`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

## Summary

| Severity | Count |
|----------|-------|
| P2 | 3 |
| P3 | 3 |
| P4 | 1 |
| **Total** | **7** |
