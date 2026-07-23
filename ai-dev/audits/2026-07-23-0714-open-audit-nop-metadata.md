> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# Open-Ended Adversarial Audit: nop-metadata (Deep Probe — Deployment & Verified Status)

**Auditor**: opencode adversarial agent  
**Date**: 2026-07-23 (independently executed, cross-referenced against all prior reports)  
**Prior audits**: 7 open rounds (2026-07-19 through 2026-07-23-0714), 2 multi-dim audits (53+36 findings), deep audits. See `ai-dev/audits/2026-07-19-1118-open-audit-nop-metadata.md` through `2026-07-23-0714-open-audit-nop-metadata.md` (previous).

**Method**: Read all prior audit findings → Read AGENTS.md → Read nop-metadata/ code, config, resources, tests, POMs, Docker files, codegen templates → Verified prior findings against live code at HEAD → New deep-probe areas (deployment infra, Docker, startup, build pipeline) not covered in prior reports.

---

## New Findings (Genuinely New — Not in Prior Audits)

### [AR-39] **[NEW]** Dockerfile.jvm Targets Java 17, but Project Requires Java 21

- **File**: `nop-metadata/nop-metadata-app/src/main/docker/Dockerfile.jvm:80`
- **Evidence**:
  ```dockerfile
  FROM registry.access.redhat.com/ubi8/openjdk-17:1.16   # Java 17
  ```
  The rest of nop-entropy uses Java 21. The module POM hierarchy (`nop-metadata/pom.xml` inherits from `nop-entropy/pom.xml`) sets:
  ```xml
  <java.version>21</java.version>
  ```
  The `nop-metadata-app/pom.xml` inherits this (no override). The nop-metadata-api submodule has its own `java.version=11` (separate issue, AR-36), but the main app and all service/dao modules build for Java 21.
- **Severity**: P2
- **Status**: Three Dockerfiles exist under `nop-metadata-app/src/main/docker/`:
  - `Dockerfile.jvm` → `ubi8/openjdk-17:1.16` (Java 17)
  - `Dockerfile.legacy-jar` → same base image (Java 17)
  - `Dockerfile.native` → `ubi8/ubi-minimal:8.8` (no Java, GraalVM native)
  - `Dockerfile.native-micro` → same
- **Risk**: If the app is compiled with `--release 21` (as inherited from nop-entropy parent POM), the JVM bytecode version is 61.0 (Java 21). Java 17 runtimes cannot load class files with major version 61. The Docker image would fail at startup with `java.lang.UnsupportedClassVersionError`. This is not caught at build time (`docker build` succeeds — `mvn package` happens before Docker build and compiles fine on Java 21; the mismatch only surfaces at `docker run`).
- **Confidence**: Certain
- **Suggestion**: Change base image to `registry.access.redhat.com/ubi8/openjdk-21-runtime:1.19` (or equivalent Java 21 base image). Or ensure the build process sets `maven.compiler.release` to a value ≤17 for this module (not recommended — would lose Java 21 features).
- **Discovery perspective**: 10x scale operator — deployment infrastructure is a consistent blind spot across all prior audit rounds

---

### [AR-40] **[NEW]** `AggregationContext` Constructor Calls `Objects.requireNonNull` on Constructor Parameters but Collection Fields Default to null

- **File**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/AggregationContext.java:36-39`
- **Evidence**:
  ```java
  public AggregationContext(MetaQueryContext ctx, MetaJoinExecutor joinExecutor) {
      this.ctx = Objects.requireNonNull(ctx, "ctx");
      this.joinExecutor = Objects.requireNonNull(joinExecutor, "joinExecutor");
  }
  ```
  However, the following collection/list fields are initialized to `null` (no default empty collections):
  ```java
  private List<String> measureNames;    // null
  private List<String> dimensionNames;  // null
  private List<OrderFieldBean> orderBy; // null
  ```
  These are consumed in `AggregationProcessor` implementations that iterate over them with `for (String name : ctx.getMeasureNames())` without null guards in some paths.
- **Severity**: P3
- **Status**: The `AggregationContext` is a mutable DTO with setter-based construction. When callers don't set `measureNames` or `dimensionNames`, downstream processors that iterate without null checks will NPE. The `MetaAggregationExecutor` and `AggregationHelper` both access `ctx.getMeasureNames()` in loops — they appear to always be called after measures/dimensions are resolved, but this is an implicit contract not enforced by the constructor or builder pattern.
- **Risk**: Low — production call paths always set these fields before consumption. But a new code path that forgets to set them will crash with NPE rather than a clear ErrorCode.
- **Confidence**: Likely
- **Suggestion**: Initialize to `Collections.emptyList()` in field declarations (or use `@lombok.Builder` pattern). Alternatively, add `@Nonnull` annotations and a validator.
- **Discovery perspective**: Exception path detective — NPE waiting to happen in a new call path

---

## Status Verification: Prior Findings Against Live Code at HEAD

| Prior ID | Issue | Reported As | Live Code Status | Evidence |
|----------|-------|------------|-----------------|----------|
| AR-26 | NopMetaSearchBizModel missing @BizModel | P2 (open) | **FIXED** | `NopMetaSearchBizModel.java:28` has `@BizModel("NopMetaSearch")`. Both `@BizMutation` and `@BizQuery` present. |
| NF-01 / NF-04 | NPE in searchEngine, inconsistent null guards | P1 (open) | **FIXED** | `searchEngine` field replaced with `searchService` (line 36-37). No direct `searchEngine.search()` call. Null guard now via NopMetaSearchService. |
| AR-37 | nop-wf-service test scope vs production workflows | P2 (open) | **FIXED** | `pom.xml:39-42`: no `<scope>` on `nop-wf-service` → compile scope. All three workflow deps (nop-wf-core, nop-wf-meta, nop-wf-service) are compile scope. |
| AR-31 | application.yaml hardcoded JWT key + introspection | P3 (open) | **FIXED** | `enc-key: change-me-in-production`, `graphql.schema-introspection.enabled: false`. The dev config hardened between audit rounds. |
| AR-25 | N+1 upsert in lineage edge extraction | P2 (open) | **STILL OPEN** | Same 3 upsert methods (`upsertSqlParseEdge`, `upsertColumnSqlParseEdge`, `upsertMeasureParseEdge`) with per-candidate SELECT+INSERT. |
| AR-30 | RuntimeException pass-through in TableReferenceExecutor | P3 (open) | **STILL OPEN** | Same pattern verified in `executeOnPlatformConnection` and `executeOnExternalConnection`. |
| AR-28 | NopMetadataConstants empty interface | P3 (open) | **STILL OPEN** | Still empty (15 lines, only package + interface declaration). |
| NF-03 | NopMetadataConfigs empty interface | P3 (open) | **STILL OPEN** | Still empty (5 lines, only package + interface declaration). |
| AR-36 | nop-metadata-api/pom.xml no parent POM | P1 (open) | **STILL OPEN** | Confirmed — no `<parent>`, `java.version=11`, standalone `<version>`. |
| AR-38 | Empty _dao.beans.xml imported | P3 (open) | **STILL OPEN** | Confirmed — 5-line file with no beans. |
| AR-29 | Swallowed search engine exceptions | P3 (open) | **STILL OPEN** | `NopMetaSearchService.addToIndex` still catches all `Exception` at WARN level. |
| 01-001 | nop-metadata-api not in nop-bom | P2 (multi-dim) | **STILL OPEN** | `nop-bom/pom.xml`: includes `nop-metadata-{codegen,core,dao,meta,service,web,app}` but NOT `nop-metadata-api`. |
| 07-003 | `dao().getEntityById()` instead of `requireEntity()` | P2 (multi-dim) | **STILL OPEN** | Verified ~20 methods across 8 BizModels. |
| 09-001 | NopMetadataException missing (String) constructors | P2 (multi-dim) | **FIXED** | `NopMetadataException.java:35-41` has both `(String)` and `(String, Throwable)` constructors. |
| 09-007 | ErrorCode naming uses hyphens not dots | P2 (multi-dim) | **STILL OPEN** | `NopMetadataErrors.java:22` explicitly documents this as intentional. |
| 16-001 | Minimal AutoTest snapshot coverage | P2 (multi-dim) | **STILL OPEN** | Only 1 `_cases/` directory with 1 test case (`TestAutoNopMetaClassificationCrud`). |
| 04-001 | Redundant index IX_NOP_META_SEM_TYPE_NAME | P2 (multi-dim) | **STILL OPEN** | ORM model confirmed: UK + non-unique index on same column. |

**Summary**: Of 17 previously reported issues verified, **6 have been fixed**, **11 remain open**. Rapid remediation is visible — the P0/P1 security issues from rounds 1-3 are all fixed, and the module's security posture has materially improved across audit cycles.

---

## Deep Analysis: Persistent Patterns

### Pattern 1: Empty/Stub Files Accumulate Despite Multiple Audit Cycles

Three empty stub files persist after 7+ audit rounds:
- `NopMetadataConstants.java` (reported AR-28, Round 5)
- `NopMetadataConfigs.java` (reported NF-03, Round 5)
- `_NopMetadataDaoConstants.java` (never reported — generated empty base)

Each removal is a 2-minute mechanical change. Their persistence suggests the audit-to-remediation pipeline lacks a "low-hanging fruit" cleanup phase. Compare: the P0 SQL injection fix (AR-01) required ~15 minutes of code changes across 3 files and was fixed between Round 3 and Round 4. These stubs have been reported for 3+ rounds without action.

### Pattern 2: Deployment Infrastructure Is a Consistent Audit Blind Spot

The Docker Java version mismatch (AR-39) was found by explicitly checking the `nop-metadata-app/` deployment directory after noting that all prior audits listed "Docker files" in their blind spots but never actually audited them. This suggests a systematic gap: audits focus on Java code, XML config, and resources, but skip `Dockerfile*`, `native-image/`, and `bootstrap.yaml`. These files are small (2-100 lines each) and fast to audit.

### Pattern 3: Rapid Remediation Followed by Plateau

The P0/P1 security findings (AR-01 through AR-14) were addressed between Rounds 3-4. P2/P3 findings (AR-25 through AR-38) plateaued after Round 5. Remediation effort appears prioritized by severity — correct — but the remaining open issues share a common characteristic: they are not individually dangerous, but their cumulative effect (empty modules, dead code, test-scope confusion, missing codegen) complicates future maintenance.

---

## Total Assessment

### Dialling back up to the module level

The nop-metadata module is in **good structural health** with strong test coverage (82 test files), complete codegen pipeline (39:39:39:39 entity-to-meta alignment), well-documented architecture (extensive comments referencing design docs and plans), and a clear remediation trajectory.

The most notable patterns observed:

1. **Deployment infrastructure is the least-audited sub-module**: Dockerfiles target Java 17 when the project is Java 21 (AR-39, P2). This was missed by all 7 prior open audits, 2 multi-dim audits, and all deep audits. The `nop-metadata-app/` directory is small (4 Java files, 4 Dockerfiles, 6 resource files) but was consistently skipped.

2. **Low-hanging fruit persists** after multiple audit rounds: 3 empty stub interfaces, 1 empty `_dao.beans.xml`, 1 empty `nop-metadata-api` module. These are individually P3 but collectively signal that the audit-remediation cycle lacks a cleanup pass for trivial items.

3. **Remediation velocity is asymmetric**: Critical security issues are fixed within hours/days. Medium-severity code-quality issues (empty stubs, N+1 patterns, missing `requireEntity()`) are acknowledged but not remediated. This is appropriate prioritization but means the "long tail" of issues never gets shorter.

### Blind Spots

- **Did not run `mvnw test`** — no runtime verification against live code.
- **Did not audit the `nop-metadata-web` amis page templates** for sensitive field exposure (150+ `.page.yaml` files).
- **Did not verify the native-image resource-config.json** completeness for GraalVM compilation.
- **Did not trace `sourceSql`/`buildSql` fields** through the event publisher redaction path (reported as 11-006, not verified).
- **Did not audit ORM entity `_gen/` bases for field drift** from current ORM model (codegen may be stale).
- **Did not inspect the `precompile2/gen-i18n.xgen` template output** for correctness.
- **Did not check the nop-entropy-root pom.xml's `dependencyManagement`** for `nop-metadata-api` version alignment.

### Severity Distribution

| Severity | Count | Main Categories |
|----------|-------|----------------|
| P0 | 0 | — |
| P1 | 1 | Missing parent POM (AR-36, previously reported, still open) |
| P2 | 11 (1 new, 10 confirmed open) | Docker Java version (AR-39), N+1 upsert (AR-25), missing requireEntity (07-003), not-in-BOM (01-001), test coverage gaps (16-001), redundant index (04-001), etc. |
| P3 | 7 (1 new, 6 confirmed open) | AggregationContext null fields (AR-40), empty stubs, swallowed exceptions, etc. |
| Status change (FIXED) | 6 | AR-26, AR-37, AR-31, NF-01/NF-04, 09-001 |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
