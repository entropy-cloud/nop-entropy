> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata

# Dimension 01: Dependency Graph & Module Boundaries

## Complete Internal Dependency Graph (compile scope only)

```
nop-metadata-api ──→ nop-api-core (external/framework)

nop-metadata-core ──→ nop-api-core (external/framework)

nop-metadata-codegen ──→ nop-ooxml-xlsx (external)
                     ──→ nop-orm (external)
                     ──→ nop-graphql-core (external)
                     ──→ nop-xlang-debugger (external)

nop-metadata-dao ──→ nop-api-core (external/framework)
                 ──→ nop-metadata-core (INTERNAL)    ← VIOLATION
                 ──→ nop-orm (external/framework)

nop-metadata-meta ──→ (no compile-scope dependencies)

nop-metadata-service ──→ nop-metadata-core (INTERNAL)
                      ──→ nop-metadata-dao (INTERNAL)
                      ──→ nop-metadata-meta (INTERNAL)
                      ──→ nop-wf-core (external)
                      ──→ nop-wf-meta (external)
                      ──→ nop-biz (external)
                      ──→ nop-http-api (external)
                      ──→ nop-biz-file-core (external)
                      ──→ nop-config (external)
                      ──→ nop-ioc (external)
                      ──→ nop-search-api (external)
                      ──→ nop-search-lucene (external, optional)
                      ──→ nop-job-api (external)

nop-metadata-web ──→ nop-metadata-service (INTERNAL)
                  ──→ nop-web (external/framework)

nop-metadata-app ──→ nop-metadata-service (INTERNAL)
                  ──→ nop-metadata-web (INTERNAL)
                  ──→ nop-quarkus-web-orm-starter (external, Quarkus)
                  ──→ nop-auth-web (external)
                  ──→ nop-auth-service (external)
                  ──→ nop-web-amis-editor (external)
                  ──→ nop-web-site (external)
                  ──→ quarkus-jdbc-mysql (external, Quarkus)
                  ──→ quarkus-jdbc-h2 (external, Quarkus)
```

**Internal-only DAG** (ignoring external/framework modules):

```
                      nop-metadata-api
                           │
                     (no consumers)

                      nop-metadata-core ◄────┐
                           │                  │
                           ▼                  │
                      nop-metadata-dao ───────┘
                           │
                           ▼
                      nop-metadata-meta
                           │
                           ▼
                      nop-metadata-service
                           │
                           ▼
                      nop-metadata-web
                           │
                           ▼
                      nop-metadata-app
```

**No circular dependencies detected.** The graph is a strict DAG.

---

### [Dimension01-01] nop-metadata-dao depends on nop-metadata-core at compile scope (violates Rule #2)

- **File**: `nop-metadata/nop-metadata-dao/pom.xml:21-24`
- **Evidence**:
  ```xml
  <dependency>
      <artifactId>nop-metadata-core</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
- **Severity**: P1
- **Status**: Compile-scope dependency from `nop-metadata-dao` to `nop-metadata-core`. Rule #2 states: "dao layer depends only on api and nop-persistence framework". `nop-metadata-core` is neither the module's own `-api` nor a persistence framework. Confirmed by 22 Java import statements across 11 Biz interfaces and 1 model class in dao that directly reference `io.nop.metadata.core.dto.*` classes.
- **Risk**: Any change to core DTOs can break dao interfaces at compile time. If core is intended as a "pure domain" layer, this reverse dependency from the data access layer corrupts the abstraction boundary. Prevents extracting core as an independent reusable library.
- **Suggestion**: Move the shared DTOs currently in `nop-metadata-core` (34 files, all DTO) to either `nop-metadata-dao/src/main/java/.../dto/` or `nop-metadata-api`.
- **Confidence**: certain
- **False Positive Exclusion**: The 22 import statements across 11 files leave zero doubt this is a real compile-time dependency, not a transitive artifact. The user's Rule #2 is unambiguous.

---

### [Dimension01-02] nop-metadata-service does not depend on nop-metadata-api (violates Rule #4)

- **File**: `nop-metadata/nop-metadata-service/pom.xml:15-130`
- **Evidence**:
  ```xml
  <!-- nop-metadata-api is absent from all dependencies -->
  <dependencies>
      <dependency><artifactId>nop-metadata-core</artifactId>...</dependency>
      <dependency><artifactId>nop-metadata-dao</artifactId>...</dependency>
      <dependency><artifactId>nop-metadata-meta</artifactId>...</dependency>
  ```
- **Severity**: P2
- **Status**: Rule #4 states "service layer depends on api + core + dao". The `api` part (`nop-metadata-api`) is missing. This is consistent with nop-auth and nop-wf patterns, making it a project-wide convention deviation from the stated rule.
- **Risk**: If `nop-metadata-api` ever acquires classes that service needs, the compiler will not catch missing imports.
- **Suggestion**: Either add the dependency or document the exception in project convention docs.
- **Confidence**: likely
- **False Positive Exclusion**: This is a deliberate project-wide pattern.

---

### [Dimension01-03] nop-metadata-core does not depend on nop-metadata-api (violates Rule #3)

- **File**: `nop-metadata/nop-metadata-core/pom.xml:14-19`
- **Evidence**:
  ```xml
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
      </dependency>
  </dependencies>
  ```
- **Severity**: P3
- **Status**: Core currently only needs `nop-api-core`. Missing own `-api` dependency.
- **Risk**: Low. Core currently only needs `nop-api-core`. No immediate compilation risk.
- **Suggestion**: Resolve together with Finding #01-01.
- **Confidence**: speculative
- **False Positive Exclusion**: The rule is ambiguous on whether "api" means module's own api or framework-level API.

---

### [Dimension01-04] nop-metadata-service depends on nop-metadata-meta (not listed in Rule #4)

- **File**: `nop-metadata/nop-metadata-service/pom.xml:46-49`
- **Evidence**:
  ```xml
  <dependency>
      <artifactId>nop-metadata-meta</artifactId>
      <groupId>io.github.entropy-cloud</groupId>
      <version>2.0.0-SNAPSHOT</version>
  </dependency>
  ```
- **Severity**: P3
- **Status**: Rule #4 lists only "api + core + dao" as allowed service dependencies. The `-meta` module is not listed but is a standard project pattern.
- **Suggestion**: Update Rule #4 to include meta.
- **Confidence**: speculative

---

### [Dimension01-05] nop-metadata-api is disconnected from the dependency graph

- **File**: cross-module analysis
- **Evidence**: No module in nop-metadata depends on nop-metadata-api. The module has no src/ directory.
- **Severity**: P3
- **Status**: Orphaned module with zero consumers and zero source files.
- **Suggestion**: Either populate it with shared DTOs or remove it from the module list.
- **Confidence**: certain

---

## Violation Summary

| ID | Severity | Finding | Module | Rule |
|---|---|---|---|---|
| 01-01 | **P1** | dao → core compile dependency | nop-metadata-dao | Rule #2 |
| 01-02 | P2 | service missing -api dependency | nop-metadata-service | Rule #4 |
| 01-03 | P3 | core missing -api dependency | nop-metadata-core | Rule #3 |
| 01-04 | P3 | service → meta not in rules | nop-metadata-service | Rule #4 |
| 01-05 | P3 | api module orphaned | nop-metadata-api | Convention |

## Compliant Modules
- nop-metadata-api: COMPLIANT (Rule #1)
- nop-metadata-core: COMPLIANT (Rule #3 framework part)
- nop-metadata-codegen: COMPLIANT (Rule #7)
- nop-metadata-meta: COMPLIANT (Rule #8)
- nop-metadata-web: COMPLIANT (Rule #5)
- nop-metadata-app: COMPLIANT (Rules #6, #10)

## Summary Assessment
The module structure follows the standard Nop business-module skeleton. No circular dependencies. The single P1 issue (dao→core) is structural. The P2 and P3 findings are pattern-based, not functional risks.
