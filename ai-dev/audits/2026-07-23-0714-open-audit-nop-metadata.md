> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-metadata

# Open-Ended Adversarial Audit: nop-metadata (Deep Probe — New Findings)

**Auditor**: opencode adversarial agent  
**Date**: 2026-07-23 (after Round 7 open audit)  
**Previous audits consulted**: All 7 rounds of open audits (2026-07-19 through 2026-07-23-0714), multi-dim audit (36 findings), and all prior deep audits.  
**Deduplication**: Cross-referenced against ~70+ previously reported issues. Where a previously-reported issue is confirmed still present with new evidence or status change, it is marked with status. New findings are labeled **[NEW]**.

---

## Remediation Verification (Status Changes Since Round 7)

I verified all findings from the most recent open audit (Round 7: 2026-07-23-0714-open-audit) against live code at HEAD.

| Round 7 ID | Issue | Round 7 Status | Live Code Status | Evidence |
|-----------|-------|---------------|-----------------|----------|
| AR-31 | application.yaml dev config risk | STILL OPEN (P3) | **FIXED** | `graphql.schema-introspection.enabled: false` (not true), `enc-key: change-me-in-production` (not `bf8433e383424f6dbc19d47a5138875d`). Only `allow-create-default-user: true` remains. Config has been hardened since Round 7 snapshot. |
| AR-26 | NopMetaSearchBizModel missing @BizModel | FIXED | ✅ CONFIRMED | `@BizModel("NopMetaSearch")` present. `@BizMutation`/`@BizQuery` present. Null guards on `searchEngine` present. |
| AR-25 | N+1 upsert in lineage edge | STILL OPEN (P2) | ✅ STILL OPEN | Confirmed — same pattern in all 3 upsert methods. |
| AR-27 | nop-metadata-api empty module | STILL OPEN (P2) | **UPGRADED — see [AR-36]** | Deeper structural issue found: no parent POM. |
| AR-30 | RuntimeException pass-through | STILL OPEN (P3) | ✅ STILL OPEN | Same pattern verified. |

**Key observation**: The application.yaml hardening between Round 7 and now suggests an active remediation was applied that the Round 7 audit missed. This is a positive signal — the module's security posture is being progressively improved.

---

## New Findings

### [AR-36] **[NEW]** nop-metadata-api/pom.xml Has No Parent POM — Standalone Module with Wrong Java Version

- **File**: `nop-metadata/nop-metadata-api/pom.xml`
- **Evidence**:
  ```xml
  <!-- nop-metadata-api/pom.xml — NO parent element -->
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-metadata-api</artifactId>
  <version>2.0.0-SNAPSHOT</version>
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
      <java.version>11</java.version>          <!-- Rest of project: Java 21 -->
      ...
  </properties>
  ```
  Every other nop-metadata submodule has:
  ```xml
  <!-- e.g. nop-metadata-core/pom.xml, nop-metadata-dao/pom.xml, etc. -->
  <parent>
      <artifactId>nop-metadata</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </parent>
  ```
  The parent POM `nop-metadata/pom.xml` declares `nop-metadata-api` as a submodule (line 29), but the submodule itself does not reciprocate with a `<parent>` declaration.
- **Severity**: P1
- **Status**: This is a structural build configuration defect that has been overlooked by all prior audits (which only noted the module was "empty"). The standalone POM:
  1. **Wrong Java version**: `java.version=11` while the rest of nop-entropy uses Java 21. If built independently, the module would target Java 11 bytecode, creating a binary compatibility mismatch for consumers expecting Java 21.
  2. **No dependency management inheritance**: Won't inherit `nop-bom` managed versions. If dependencies other than `nop-api-core` are ever added, their versions won't be managed.
  3. **No plugin management inheritance**: Won't inherit compiler settings, surefire config, or codegen exec plugin config from the reactor.
  4. **Build isolation**: If built standalone (e.g., `mvn -pl nop-metadata-api compile`), it compiles with Java 11 and no BOM. If built in the reactor, it inherits nothing from parent anyway.
- **Risk**: A developer adding a new dependency to this module must hardcode the version (defeating BOM-based version management). The Java 11 target could cause `UnsupportedClassVersionError` when loaded alongside Java 21-compiled classes from sibling modules. In a CI/CD pipeline that builds modules individually, this module is effectively misconfigured.
- **Confidence**: Certain
- **Suggestion**: Add the missing `<parent>` element:
  ```xml
  <parent>
      <artifactId>nop-metadata</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </parent>
  ```
  Remove the duplicate `<groupId>`, `<version>`, and overridden `<properties>` (let parent and BOM manage them). The only retained property should be `maven.compiler.release` if different from 21, or remove it entirely.
- **Discovery perspective**: Build system detective — a POM that lives in the reactor but doesn't declare its parent violates Maven's implicit convention. All other 7 submodules (and all 200+ modules in nop-entropy) have parents.

---

### [AR-37] **[NEW]** 3 Workflow Definitions Shipped as Production Resources, but Workflow Engine Is Test-Scoped

- **Files**:
  - `nop-metadata/nop-metadata-service/pom.xml:41-43` (`nop-wf-service` scope=test)
  - `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/wf/metaDataContractApproval/v1.xwf`
  - `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/wf/qualityBreachApproval/v1.xwf`
  - `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/wf/tagLabelConfirmApproval/v1.xwf`
- **Evidence**:
  ```xml
  <!-- pom.xml:41-43 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-wf-service</artifactId>
      <scope>test</scope>                      <!-- <-- test only -->
  </dependency>
  ```
  Workflow files reference `wf-approval:notifyResult` and `x:extends="/nop/wf/base/oa.xwf"`:
  ```xml
  <workflow x:extends="/nop/wf/base/oa.xwf"  <!-- from nop-wf, compile scope -->
            wfName="metaDataContractApproval" wfVersion="1">
      <listeners>
          <listener id="onEndNotify" eventPattern="*end">
              <source>
                  <wf-approval:notifyResult .../>  <!-- from nop-wf-service, TEST scope -->
              </source>
          </listener>
      </listeners>
  </workflow>
  ```
- **Severity**: P2
- **Status**: Three workflow definition files are packaged in `src/main/resources` (production classpath) but the runtime implementation they depend on (`nop-wf-service`) is `test` scope. At runtime:
  - `x:extends="/nop/wf/base/oa.xwf"` comes from `nop-wf-core` which is compile-scope (line 33-34) — this resolves OK.
  - But `wf-approval:notifyResult` requires `nop-wf-service` which is NOT on the production classpath. The tag library `wf-approval` would fail to resolve.
  - If `NopMetaQualityCheckpointScheduler` or any other runtime component triggers a workflow, the `IWorkflowService` implementation is unavailable.
  - The `QualityAlertWorkflowService` bean (registered in `app-service.beans.xml` line 36) likely tries to create workflow instances but would get a NoClassDefFoundError or NPE when the workflow engine is absent.
- **Risk**: Any code path that attempts to start one of these workflows at runtime would fail with a class resolution error. The workflows work in tests (where nop-wf-service is on the classpath) but break in production. This is the opposite of the usual pattern — normally test-scoped deps don't ship production resources that depend on them.
- **Confidence**: Certain
- **Suggestion**: Either:
  1. Move `nop-wf-service` to compile scope (if workflow execution is a required feature).
  2. Move the 3 workflow definition files from `src/main/resources` to `src/test/resources` (if workflow execution is only for testing).
  3. Add a startup guard in `QualityAlertWorkflowService` that fails gracefully with a clear ErrorCode when `IWorkflowService` is not available.
- **Discovery perspective**: Module boundary detective — production resources depending on test-scoped libraries is an inverted dependency pattern.

---

### [AR-38] **[NEW]** Empty `_dao.beans.xml` Imported by `app-service.beans.xml` — No-Op Import

- **File**: `nop-metadata/nop-metadata-dao/src/main/resources/_vfs/nop/metadata/beans/_dao.beans.xml`
- **Evidence**:
  ```xml
  <!-- _dao.beans.xml — 5 lines, only root element -->
  <beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:ioc="ioc"
         xmlns="http://www.springframework.org/schema/beans" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://www.springframework.org/schema/beans
           http://www.springframework.org/schema/beans/spring-beans-2.5.xsd"/>
  ```
  Import site (`app-service.beans.xml:8`):
  ```xml
  <import resource="_dao.beans.xml"/>
  ```
- **Severity**: P3
- **Status**: The DAO module's beans.xml exists but is structurally empty (no `<bean>` children, no `x:extends`, no properties). It is imported by `app-service.beans.xml` as the first import. Every time NopIoC processes `app-service.beans.xml`, it resolves and parses this empty file, consuming startup time for zero benefit.
- **Risk**: Minimal individually. But as a pattern, it suggests the generated `_dao.beans.xml` was intended to contain ORM entity registrations or DAO bean definitions that were never added. If the DAO module ever needs IoC beans (e.g., custom DAO implementations), the file must be populated. Currently, any bean defined in the dao module would not be auto-discovered because:
  - There's no `ioc:default` bean registration mechanism in the empty file.
  - The `nop-metadata-dao` module has no other beans.xml (no `app-dao.beans.xml` retention layer).
- **Confidence**: Certain
- **Suggestion**: Either (a) add the actual DAO-level beans (e.g., ORM entity registrations if needed), or (b) if the DAO module truly needs no IoC configuration, remove the import from `app-service.beans.xml` and delete the empty file to reduce startup overhead and remove a misleading artifact.
- **Discovery perspective**: Dead code detective — a generated file that served its purpose (marking the dao module's IoC presence) but now has no content.

---

### [SC-04] Application.yaml Hardening Confirmed — AR-31 Partially Addressed

- **File**: `nop-metadata/nop-metadata-app/src/main/resources/application.yaml`
- **Status**: The Round 7 audit reported AR-31 as "STILL OPEN" with `graphql.schema-introspection.enabled: true` and hardcoded JWT key `bf8433e383424f6dbc19d47a5138875d`. Live code at HEAD shows both have been fixed:
  - `graphql.schema-introspection.enabled: false` (both default and `%prod` profile)
  - `jwt.enc-key: change-me-in-production` (placeholder, with doc comment "Set via environment variable NOP_AUTH_JWT_ENC_KEY in production")
- **Remaining**: `allow-create-default-user: true` is still present with comment "如果用户表为空". This is non-critical if the production database is pre-seeded, but is a hardening recommendation for secure deployments.
- **Confidence**: Certain
- **Severity**: ✅ FIXED (AR-31 closed; remaining `allow-create-default-user` is P3 if considered)
- **Note**: This corrective action was applied between the Round 7 audit and this probe, suggesting parallel remediation work was in progress.

---

## De-Dup Summary for Prior Open Issues

| Prior ID | Short Description | Status After This Probe |
|----------|------------------|------------------------|
| AR-25 | N+1 upsert lineage | Still open (P2) — acknowledged |
| AR-27 | nop-metadata-api empty module | Still open — upgraded with [AR-36] parent POM finding |
| AR-28 | NopMetadataConstants empty | Still open (P3) |
| AR-30 | RuntimeException pass-through | Still open (P3) |
| AR-31 | Dev config risk | **Closed** — application.yaml hardened |
| NF-03 | NopMetadataConfigs empty | Still open (P3) |
| All P0 from R1-R6 | SQL injection, SSRF, etc. | All fixed and confirmed |
| 07-002 | Redundant txn() wrapping | Still open (multi-dim, unreviewed) |
| 09-07 | ErrorCode naming hyphens | Still open (P2, systemic) |

---

## Total Assessment

### Most notable directions

1. **Build structure blind spot**: The `nop-metadata-api/pom.xml` missing parent POM ([AR-36], P1) was missed by six prior open audits and one multi-dim audit. This suggests the module boundary reviews consistently overlooked the Maven POM structure, focusing on Java code, resources, and tests. The finding itself is mechanically fixable (add 6 lines to the POM), but its presence after so many audits indicates the "module boundary" audit dimension needs an explicit POM checklist item.

2. **Resource vs. dependency inversion**: The `nop-wf-service` test-scope vs. production workflow definitions ([AR-37], P2) is the opposite of the common pattern (production code depending on test deps). It's likely a transitional state — the workflows were built and tested before the dependency scoping was finalized. But as-is, shipping non-functional workflow definitions in the production artifact creates a reliability risk: workflows that appear available (they're in the VFS) but fail at runtime with opaque errors.

3. **Remediation velocity is accelerating**: The application.yaml hardening between Round 7 (hours ago) and now shows that the module's security posture is being actively improved. Of the 5 remaining open issues from previous audits, one (AR-31) has been resolved. This suggests the audit pipeline is effectively driving remediation.

### Blind spots

- **Maven POM structure**: This audit found the parent POM issue precisely because I intentionally looked at the build system, not just Java code. Prior audits consistently focused on code, config, and resources. The `pom.xml` files were not audited for structural correctness.
- **Dependency scope vs. resource deployment**: The wf-service test-scope issue required cross-referencing dependency declarations in POM with resource files in `src/main/resources`. Most audits look at code logic or test coverage separately — the cross-boundary analysis of "what code/resources are shipped vs. what dependencies are available" was missed.
- **Did not verify the `nop-bom/pom.xml` for the nop-metadata-api omission** found in multi-dim dimension 01 (which was a different issue — BOM registration, not parent POM). These are distinct concerns.
- **Did not run `mvnw test`** — no runtime verification.
- **Did not audit the hand-written entity retention classes vs. generated bases** for field drift.
- **Did not inspect the `nop-metadata-web` precompile2 templates** for codegen correctness.
- **Did not trace the `nop-metadata-service` test application.yaml** to verify test isolation.

### Severity distribution

| Severity | Count | Main categories |
|----------|-------|----------------|
| P0 | 0 | — |
| P1 | 1 | Missing parent POM (AR-36) |
| P2 | 1 | Workflow resources vs test-scoped dependency (AR-37) |
| P3 | 1 | Empty _dao.beans.xml import (AR-38) |
| Status change | 1 | AR-31 closed (dev config hardened) |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
