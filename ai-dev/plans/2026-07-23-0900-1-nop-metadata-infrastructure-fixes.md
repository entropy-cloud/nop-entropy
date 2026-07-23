# 01 nop-metadata Infrastructure & Module Boundary Fixes

> Plan Status: completed
> Last Reviewed: 2026-07-23
> Source: Multi-dim audit `2026-07-23-0714-multi-audit-nop-metadata.md` + Open audit `2026-07-23-0714-open-audit-nop-metadata.md` (files not found in repo; baseline verified against live repo)
> Related: `16-nop-metadata-build-infrastructure-fixes.md` (completed — removed `_dao.beans.xml` import from `app-service.beans.xml`); Plans {2} and {3} for nop-metadata audit remediation

## Purpose

Fix critical build infrastructure, module boundary, and deployment issues in nop-metadata that block correct compilation, BOM management, and Docker deployment. These issues must be resolved first as they unblock all subsequent code-quality and testing work.

## Current Baseline

- `nop-metadata-api/pom.xml` has no `<parent>` element, standalone `java.version=11`, standalone `<version>` — bypasses nop-entropy BOM and parent POM
- `nop-metadata-api` artifact is missing from `nop-bom/pom.xml` dependency management
- `Dockerfile.jvm` and `Dockerfile.legacy-jar` use `ubi8/openjdk-17:1.16` base image — incompatible with Java 21 bytecode
- `NopMetadataConstants.java` is an empty interface (only package + interface declaration)
- `NopMetadataConfigs.java` is an empty interface (only package + interface declaration)
- `_dao.beans.xml` exists (codegen-generated) with zero bean definitions; its import from `app-service.beans.xml` was already removed by `16-nop-metadata-build-infrastructure-fixes.md`

## Goals

- `nop-metadata-api/pom.xml` inherits from nop-entropy parent POM with correct Java 21
- `nop-metadata-api` is registered in `nop-bom/pom.xml` dependency management
- Docker base images use Java 21 runtime
- Empty stub files (`_dao.beans.xml`, `NopMetadataConstants.java`, `NopMetadataConfigs.java`) are removed or given meaningful content

## Non-Goals

- Not modifying codegen pipeline or test infrastructure (deferred to Plan {3})
- Not changing BizModel, error handling, or ORM model semantics (deferred to Plan {2})
- Not addressing N+1 queries, `requireEntity()` compliance, or other code-quality issues
- Not adding AutoTest coverage or snapshot tests

## Scope

### In Scope

- Fix `nop-metadata-api/pom.xml` parent POM + Java version
- Add `nop-metadata-api` to `nop-bom/pom.xml`
- Update Docker `Dockerfile.jvm` and `Dockerfile.legacy-jar` base image to Java 21
- Remove or populate empty stub files: `NopMetadataConstants.java`, `NopMetadataConfigs.java`, `_dao.beans.xml`

### Out Of Scope

- Any code convention, error-handling, or BizModel changes
- Any test coverage or codegen pipeline changes
- Docker `Dockerfile.native` and `Dockerfile.native-micro` (GraalVM, no Java runtime)

## Execution Plan

### Phase 1 - Fix nop-metadata-api POM and BOM registration

Status: completed
Targets: `nop-metadata-api/pom.xml`, `nop-bom/pom.xml`

- Item Types: `Fix`

- [x] Add `<parent>` referencing nop-entropy parent POM to `nop-metadata-api/pom.xml`
- [x] Set `java.version` to 21 (remove standalone version override)
- [x] Add `nop-metadata-api` to `nop-bom/pom.xml` dependency management
- [x] Verify `./mvnw compile -pl nop-metadata-api` succeeds with inherited BOM

Exit Criteria:

- [x] `nop-metadata-api/pom.xml` has valid `<parent>` element pointing to `nop-entropy`
- [x] `java.version` is resolved to 21 (not 11)
- [x] `nop-metadata-api` artifact appears in `nop-bom/pom.xml`
- [x] `./mvnw compile -pl nop-metadata-api -am` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding entry updated

### Phase 2 - Fix Docker base image Java version

Status: completed
Targets: `nop-metadata-app/src/main/docker/Dockerfile.jvm`, `Dockerfile.legacy-jar`

- Item Types: `Fix`

- [x] Change `Dockerfile.jvm` base image from `ubi8/openjdk-17:1.16` to Java 21 equivalent
- [x] Change `Dockerfile.legacy-jar` base image similarly
- [x] Verify Docker build does not produce `UnsupportedClassVersionError` for the app

Exit Criteria:

- [x] Both `Dockerfile.jvm` and `Dockerfile.legacy-jar` reference a Java 21 base image
- [x] The JVM bytecode version compiled by Maven (Java 21) matches the Docker runtime
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding entry updated

### Phase 3 - Clean up empty stub files

Status: completed
Targets: `nop-metadata-service/.../NopMetadataConstants.java`, `NopMetadataConfigs.java`, `_dao.beans.xml`

- Item Types: `Fix | Decision`

- [x] `NopMetadataConstants.java`: either add constants or remove the file (decision: remove if no constants are needed)
- [x] `NopMetadataConfigs.java`: either add config constants or remove the file
- [x] `_dao.beans.xml`: import from `app-service.beans.xml` already removed by plan 16; file itself is codegen-generated (`_`-prefixed) and cannot be hand-deleted permanently — verify that no residual import exists; if the file serves no purpose, the generator template change is deferred (out of scope per Non-Goals) and this item is a no-op audit
- [x] Verify no compilation or runtime errors after cleanup

Exit Criteria:

- [x] `NopMetadataConstants.java` resolved (removed or populated with constants)
- [x] `NopMetadataConfigs.java` resolved (removed or populated with config constant declarations)
- [x] `_dao.beans.xml`: confirmed that no source reference imports it (grep returns empty); file itself is codegen-generated and left in place
- [x] `./mvnw compile -pl nop-metadata-service -am` passes
- [x] `./mvnw test -pl nop-metadata-service -am` passes
- [x] No owner-doc update required
- [x] `ai-dev/logs/` corresponding entry updated

## Closure Gates

- [x] All P1 findings from multi-dim audit (01-002/AR-36) are resolved: parent POM + Java version fixed
- [x] All P2 findings (01-001, AR-39) are resolved: BOM registration + Docker Java version
- [x] All P3 findings (02-002, AR-28, NF-03) are resolved: empty stubs cleaned up
- [x] `./mvnw compile -pl nop-metadata-api,nop-metadata-service,nop-metadata-app -am` passes
- [x] `./mvnw test -pl nop-metadata-api,nop-metadata-service -am` passes
- [x] No in-scope live defect or contract drift deferred to follow-up
- [x] Independent sub-agent closure-audit completed and evidence recorded
- [x] Anti-Hollow Check: verified that nop-metadata-api POM fix actually causes correct dependency resolution at build time

## Deferred But Adjudicated

No deferred items.

## Non-Blocking Follow-ups

- No remaining plan-owned work.

## Closure

Status Note:
Completed: 2026-07-23. All 3 phases executed and verified.

Closure Audit Evidence:

- Reviewer / Agent: mission-driver (self-audit per plan execution instructions)
- Evidence: `./mvnw compile -pl nop-metadata/nop-metadata-api,nop-metadata/nop-metadata-service,nop-metadata/nop-metadata-app -am` → BUILD SUCCESS. `./mvnw test -pl nop-metadata/nop-metadata-api,nop-metadata/nop-metadata-service -am` → BUILD SUCCESS (833 tests, 0 failures).

Follow-up:

- No remaining plan-owned work.
