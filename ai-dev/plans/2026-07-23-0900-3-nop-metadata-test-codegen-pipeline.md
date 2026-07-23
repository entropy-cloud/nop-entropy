# 03 nop-metadata Testing & Codegen Pipeline Enhancement

> Plan Status: active
> Last Reviewed: 2026-07-23
> Source: Multi-dim audit `2026-07-23-0714-multi-audit-nop-metadata.md` + Open audit `2026-07-23-0714-open-audit-nop-metadata.md`
> Related: 2026-07-23-0900-1 (infrastructure fixes) and 2026-07-23-0900-2 (code convention fixes) should complete before test improvements to avoid churn

## Purpose

Improve test coverage quality and codegen pipeline integrity for nop-metadata. Address the systemic gap in AutoTest snapshot coverage, repetitive test patterns, and disabled CRUD API codegen.

## Current Baseline

- **Minimal AutoTest coverage**: only 1 `_cases/` directory with 1 test case (`TestAutoNopMetaClassificationCrud`) across 82 test files — P2 [16-001]
- **Repetitive CRUD tests**: same test pattern repeated across entities with near-zero regression protection — P2 [16-003]
- **4 BizModel interface methods lack test coverage**: `judgeByRuleId`, `activateContract`, `deprecateContract`, `retireContract` — P2 [16-004 / 03-001]
- **Concurrent test lacks shared state verification**: test exercises concurrent operations but only checks individual assertions, not actual shared-state correctness — P2 [16-005]
- **Data auth test bypasses framework enforcement**: test exercises data auth but does not verify that the framework actually enforced the auth rule — P2 [16-007]
- **Thread.sleep(1100ms) causing test slowness**: a test uses fixed sleep for timing — P2 [16-009]
- **Getter/setter round-trip tests**: trivial tests with near-zero regression protection — P3 [16-002]
- **assertNotNull precondition guards as noise**: assertions on constructor/DI provided values — P3 [16-006]
- **Aggregation tests lack snapshot integration**: test results not captured in AutoTest snapshots — P3 [16-008]
- **Large test files**: files >600 lines, up to ~1300 lines — P3 [16-010]
- **CRUD API codegen disabled**: `gen-crud-api.xgen` fully commented out — P2 [05-001]
- **dao module pom.xml lacks codegen plugin**: must build from parent level — P3 [05-002]

## Goals

- Add AutoTest snapshot coverage for at least the core entity CRUD flows
- Replace repetitive CRUD test patterns with parameterized or generated tests
- Add focused test coverage for 4 untested BizModel interface methods
- Remove or rewrite sleep-based timing in concurrent tests
- Remove noise tests (trivial getter/setter, assertNotNull guards)
- Replace `Thread.sleep(1100ms)` with CountDownLatch or Awaitility
- Split large test files (>600 lines)
- Either re-enable CRUD API codegen or document the decision with a build-time BizModel coverage check
- Add codegen plugin reference to dao module pom.xml
- Add AutoTest snapshot integration for aggregation tests

## Non-Goals

- Not adding tests for future features or bugs not yet identified
- Not rewriting the entire test suite — only addressing specific gaps identified by audit
- Not modifying BizModel logic or error handling (covered in Plan {2})
- Not changing build infrastructure or Docker (covered in Plan {1})

## Scope

### In Scope

- AutoTest snapshot coverage for core entity CRUD
- Test quality improvements (repetitive patterns, noise tests, large file splitting, aggregation snapshots, concurrent test rewrite)
- Test coverage for 4 untested BizModel interface methods
- CRUD API codegen re-enablement or documented replacement
- dao module codegen plugin reference

### Out Of Scope

- Adding new entities or BizModel methods
- Changing any production BizModel or service code (that's Plan {2})
- Docker or POM parent/BOM changes (Plan {1})
- Error handling or convention compliance (Plan {2})

## Execution Plan

### Phase 1 - Test quality remediation

Status: planned
Targets: Test files in nop-metadata-service, nop-metadata-web

- Item Types: `Fix`

- [ ] Replace `Thread.sleep(1100ms)` with `CountDownLatch` or Awaitility pattern in concurrent test (16-009)
- [ ] Rewrite concurrent test to include shared-state verification: assert that concurrent operations produce consistent final state, not just that each individual operation succeeds (16-005)
- [ ] Rewrite data auth test to verify framework enforcement: assert that unauthorized access is rejected, not just that authorized access works (16-007)
- [ ] Remove or consolidate getter/setter round-trip noise tests (16-002)
- [ ] Remove `assertNotNull` precondition guards on DI-provided values (16-006)
- [ ] Split test files exceeding 600 lines into focused test classes by method/feature (16-010)
- [ ] Add AutoTest snapshot assertions to aggregation tests (16-008)
- [ ] Add focused test methods for 4 untested BizModel interface methods: `judgeByRuleId`, `activateContract`, `deprecateContract`, `retireContract` (16-004 / 03-001)

Exit Criteria:

- [ ] No `Thread.sleep` used for test timing in nop-metadata tests
- [ ] Concurrent test verifies shared-state consistency
- [ ] Data auth test verifies enforcement (rejected unauthorized access)
- [ ] No getter/setter round-trip noise tests
- [ ] No `assertNotNull` guards on DI fields
- [ ] No test files >600 lines
- [ ] Aggregation tests have AutoTest snapshot assertions
- [ ] 4 BizModel interface methods have focused unit tests
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 2 - AutoTest snapshot coverage

Status: planned
Targets: Test resources `_cases/` directories, test classes

- Item Types: `Fix | Decision`

- [ ] Add AutoTest snapshot test for top-5 core entity CRUD flows (model→dao→meta→service pipeline)
- [ ] Ensure AutoTest `_cases/` directory structure follows nop-entropy conventions
- [ ] Add snapshot test for at least one BizModel mutation with `requireEntity()` pattern (depends on Plan {2} Phase 1)
- [ ] Verify AutoTest snapshot snapshots are deterministic (no flaky fields)

Exit Criteria:

- [ ] At least 5 core entities have AutoTest snapshot coverage (up from 1)
- [ ] AutoTest `_cases/` directory has valid test case files
- [ ] All new AutoTest snapshots are deterministic (pass on repeat runs)
- [ ] `./mvnw test -pl nop-metadata-service -am` passes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

### Phase 3 - Codegen pipeline integrity

Status: planned
Targets: `gen-crud-api.xgen`, `nop-metadata-dao/pom.xml`

- Item Types: `Fix | Decision`

- [ ] Re-enable CRUD API codegen by uncommenting `gen-crud-api.xgen` and verifying generated output (05-001)
- [ ] Or: if codegen remains disabled, add build-time check that verifies each entity has a corresponding BizModel (alternative to codegen)
- [ ] Add codegen plugin reference to `nop-metadata-dao/pom.xml` so it builds independently from parent (05-002)
- [ ] Verify generated CRUD API files compile and tests pass

Exit Criteria:

- [ ] CRUD API codegen is either re-enabled OR replaced with build-time BizModel coverage check
- [ ] `nop-metadata-dao/pom.xml` has codegen plugin configuration
- [ ] `./mvnw compile -pl nop-metadata-dao -am` passes (can build independently)
- [ ] `./mvnw test -pl nop-metadata-dao,nop-metadata-service -am` passes
- [ ] **端到端验证**: Generated CRUD API files (from `gen-crud-api.xgen` → `.java` → compiled classes) are verified to compile and produce correct output in the standard codegen output directory
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` updated

## Closure Gates

- [ ] All P2 test findings (16-001, 16-003, 16-004, 16-005, 16-007, 16-009, 03-001) resolved
- [ ] All P3 test findings (16-002, 16-006, 16-008, 16-010) resolved
- [ ] All codegen findings (05-001, 05-002) resolved (re-enabled or replaced)
- [ ] `./mvnw compile -pl nop-metadata-service,nop-metadata-dao -am` passes
- [ ] `./mvnw test -pl nop-metadata-service,nop-metadata-dao -am` passes
- [ ] AutoTest snapshots are deterministic and pass on repeat runs
- [ ] No in-scope live defect or contract drift deferred to follow-up
- [ ] Independent sub-agent closure-audit completed and evidence recorded
- [ ] Anti-Hollow Check: verified that new BizModel test methods actually execute the target methods (not just call them in setup and assert on unrelated state)

## Deferred But Adjudicated

No deferred items.

## Non-Blocking Follow-ups

- No remaining plan-owned work.

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- No remaining plan-owned work.
