> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 05: Codegen Pipeline Integrity

### [Dimension05-01] Source ORM Model Valid: 39 Entities, Well-Structured
- **File**: `nop-metadata/model/nop-metadata.orm.xml`
- **Severity**: Informational
- **Status**: Valid XML with 29 dicts, 11 domains, 39 entities. Proper audit fields.

### [Dimension05-02] Codegen Script Source References: Correct Relative Paths
- **File**: `nop-metadata/nop-metadata-codegen/postcompile/gen-orm.xgen:1-7`
- **Severity**: Informational
- **Status**: Script references resolve correctly from codegen base directory.

### [Dimension05-03] Generated _app.orm.xml Consistent with Source Model
- **File**: `nop-metadata/nop-metadata-dao/src/main/resources/_vfs/nop/metadata/orm/_app.orm.xml`
- **Severity**: Informational
- **Status**: 39:39 entity match between source and generated _app.orm.xml.

### [Dimension05-04] Dao Entity Java Files: _gen + Retention Pair Count Matches (39:39:39)
- **File**: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/entity/`
- **Severity**: Informational
- **Status**: Perfect 39:39:39 match between source model, _gen entities, and retention entities.

### [Dimension05-05] Meta Codegen (xmeta): Correct Output (78 files)
- **File**: `nop-metadata/nop-metadata-meta/precompile/gen-meta.xgen:1-4`
- **Severity**: Informational
- **Status**: 39 entity xmeta pairs (_gen + retention) = 78 files.

### [Dimension05-06] Postcompile i18n Codegen: Correct Output (4 files)
- **File**: `nop-metadata/nop-metadata-meta/postcompile/gen-i18n.xgen:1-4`
- **Severity**: Informational
- **Status**: English and Chinese i18n files correctly generated.

### [Dimension05-07] Web Codegen (Page Generation): Complete 39-Page Set (78 files)
- **File**: `nop-metadata/nop-metadata-web/precompile/gen-page.xgen:1-5`
- **Severity**: Informational
- **Status**: All entities have view.xml files.

---

### [Dimension05-08] CRUD API Codegen INTENTIONALLY DISABLED

- **File**: `nop-metadata/nop-metadata-meta/postcompile/gen-crud-api.xgen:1-10`
- **Evidence**:
  ```
  codeGenerator.withTplDir('/nop/templates/crud-api')
      .withTargetDir("../nop-metadata-service/src/main/java")
      .execute("/", { moduleId: "nop/metadata", ... });
  ```
  Entire script is commented out with a `/* ... */` block.
- **Severity**: P2
- **Status**: The gen-crud-api.xgen script is entirely commented out. No auto-generated CRUD service stubs are produced. All 39+ BizModel classes are hand-written.
- **Risk**: Any new entity added to the source ORM model requires manual creation of a corresponding BizModel. No automated verification that all entities have BizModel coverage.
- **Suggestion**: Either uncomment and enable gen-crud-api.xgen, or add a build-time test that verifies every entity has a corresponding `*BizModel.java`.
- **Confidence**: certain
- **Review Status**: unreviewed

---

### [Dimension05-09] Maven Plugin Phase Configuration: Correct
- **Severity**: Informational
- **Status**: Standard Nop exec-maven-plugin config with correct phase bindings.

### [Dimension05-10] Pipeline Module Dependency Order: Correct
- **Severity**: Informational
- **Status**: Module order in parent pom.xml follows pipeline flow.

### [Dimension05-11] nop-metadata-dao pom.xml: No Codegen Plugin
- **Severity**: P3
- **Status**: dao module is a passive consumer of generated output. Build must be run from parent level to trigger regeneration.

---

## Summary

| ID | Finding | Severity |
|----|---------|----------|
| 05-08 | CRUD API codegen disabled | P2 |
| 05-11 | dao module has no codegen plugin | P3 |

All pipeline stages from model→dao→meta→web are correctly closed with 39:39:39:39 entity consistency. The only pipeline gap is the intentionally disabled CRUD API generation.
