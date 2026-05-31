# Audit Dimension 01: Dependency Graph and Module Boundary — nop-code

## Complete Internal Dependency Graph

```
                          nop-code (parent POM)
                                 │
    ┌────────────┬───────────────┼───────────────┬─────────────┐
    │            │               │               │             │
    ▼            ▼               ▼               ▼             ▼
 nop-code-api  nop-code-core  nop-code-codegen nop-code-dao  nop-code-meta
  (ORPHAN)        │               │               │             │
  │               │               │               │             │
  └─nop-api-core ├──nop-api-core ├──nop-ooxml──├──nop-api-core├─nop-code-codegen(test)
                  ├──nop-commons  ├──nop-orm    └──nop-orm    └─nop-code-dao(test)
                  ├──nop-core     ├──nop-graphql    │
                  └──slf4j        └──nop-xlang   nop-code-codegen(test)
                      │                debugger
                      │
          ┌───────────┼───────────────────┐
          │           │                   │
          ▼           ▼                   ▼
   nop-code-graph nop-code-flow    nop-code-lang-java
    │             │                 │
    ├─nop-code── ├─nop-code-core   ├─nop-code-core
    │  core      ├─nop-code-graph  ├─javaparser-core
    ├─jgrapht    └─slf4j           ├─javaparser-symbol-solver
    └─networkana                   └─nop-commons
          lysis
    │
    │       nop-code-lang-python       nop-code-lang-typescript
    │        │                          │
    │        ├─nop-code-core            ├─nop-code-core
    │        ├─tree-sitter              ├─tree-sitter
    │        └─tree-sitter-python       └─tree-sitter-typescript
    │
    ▼
  nop-code-service
    ├──nop-code-dao ──────────────────────┐
    ├──nop-code-core                       │
    ├──nop-code-graph                      │
    ├──nop-code-flow                       │
    ├──nop-code-lang-java                  │
    ├──nop-code-lang-python                │
    ├──nop-code-lang-typescript            │
    ├──nop-code-meta                       │
    ├──nop-biz                             │
    ├──nop-config                          │
    ├──nop-ioc                             │
    ├──nop-search-api (optional)           │
    └──nop-code-codegen (test)             │
    │                                      │
    ▼                                      │
  nop-code-web                             │
    ├──nop-code-meta                       │
    ├──nop-code-service ◄──────────────────┘
    ├──nop-web
    ├──nop-code-codegen (test)
    └──test deps
    │
    ▼
  nop-code-app
    ├──nop-quarkus-web-orm-starter
    ├──nop-code-service
    ├──nop-code-web
    ├──nop-auth-web
    ├──nop-auth-service
    ├──nop-web-site
    ├──quarkus-jdbc-mysql
    └──quarkus-jdbc-h2
```

## Circular Dependency Check

**Result: No circular dependencies detected.**

---

## Findings

### [维度01-01] nop-code-api Is an Orphaned Empty Module With No Source Code and No Dependents

- **File**: `nop-code/nop-code-api/pom.xml:1-45` and directory listing showing no `src/` directory
- **Evidence Snippet**:
  ```
  nop-code-api/
  ├── pom.xml
  └── target/    ← no src/ directory at all
  ```
  pom.xml (lines 23-29):
  ```xml
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
          <version>${nop-entropy.version}</version>
      </dependency>
  </dependencies>
  ```
- **Severity**: P2
- **Current State**: The nop-code-api module is declared in the reactor but contains zero source files. No other module in nop-code depends on it. No Java code imports from `io.nop.code.api.*`.
- **Risk**: Dead artifact increasing build time, confusing developers. ~30 DTOs and ICodeIndexService that should be in api are embedded in nop-code-service.
- **Recommendation**: Either populate nop-code-api with ICodeIndexService and DTOs, or remove it from the reactor.
- **Confidence**: Certain
- **False Positive Exclusion**: The module has literally no source code and zero dependents while service carries 30+ DTOs.
- **Review Status**: Not reviewed

---

### [维度01-02] nop-code-api Missing Parent POM Declaration

- **File**: `nop-code/nop-code-api/pom.xml:1-45`
- **Evidence Snippet**:
  ```xml
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <properties>
      <nop-entropy.version>2.0.0-SNAPSHOT</nop-entropy.version>
      <java.version>11</java.version>
  </properties>
  ```
- **Severity**: P3
- **Current State**: nop-code-api is the only submodule that does not declare `nop-code` as its parent POM. It defines its own groupId, version, and properties independently.
- **Risk**: Module misses parent's dependencyManagement and plugin configuration. Low impact currently since module is empty.
- **Recommendation**: Add parent declaration matching other submodules.
- **Confidence**: Certain
- **False Positive Exclusion**: This is a structural issue — one module breaks the parent-child relationship that all 12 sibling modules follow.
- **Review Status**: Not reviewed

---

### [维度01-03] ICodeIndexService and ~30 DTOs Placed in Service Instead of API Module

- **File**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:1-178` and `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/`
- **Evidence Snippet**:
  ```java
  package io.nop.code.service.api;
  import io.nop.code.core.incremental.FileFingerprint;
  import io.nop.code.core.model.*;
  import io.nop.code.flow.ChangeAnalysisResult;
  import io.nop.code.flow.DeadCodeReport;
  import io.nop.code.flow.ExecutionFlow;
  import io.nop.code.service.api.dto.*;
  public interface ICodeIndexService {
  ```
- **Severity**: P2
- **Current State**: The service layer API contract lives under `io.nop.code.service.api` inside nop-code-service. The nop-code-api module sits completely empty. Any external consumer must depend on nop-code-service, pulling in the full implementation.
- **Risk**: External consumers of code analysis API must take heavy transitive dependency closure.
- **Recommendation**: Move ICodeIndexService + DTOs to nop-code-api. The api module would depend on nop-code-core + nop-code-flow, decoupled from nop-orm/nop-dao.
- **Confidence**: Likely
- **False Positive Exclusion**: The api module EXISTS in the reactor but was never populated. The package name `io.nop.code.service.api` confirms this was meant to eventually move.
- **Review Status**: Not reviewed

---

### [维度01-04] Inconsistent Sibling Dependency Version Declarations (Hardcoded vs ${project.version})

- **File**: Multiple pom.xml files across sub-modules
- **Evidence Snippet**:
  Hardcoded in nop-code-graph/pom.xml:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-code-core</artifactId>
      <version>1.0.0-SNAPSHOT</version>
  </dependency>
  ```
  Using `${project.version}` in nop-code-flow/pom.xml:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-code-core</artifactId>
      <version>${project.version}</version>
  </dependency>
  ```
- **Severity**: P3
- **Current State**: 7 of 10 submodules with sibling dependencies hardcode `1.0.0-SNAPSHOT` while 3 use `${project.version}`.
- **Risk**: Version changes require individual updates for hardcoded references.
- **Recommendation**: Standardize to `${project.version}` for all intra-module references.
- **Confidence**: Certain
- **False Positive Exclusion**: Same pattern (sibling dependency) uses two different mechanisms.
- **Review Status**: Not reviewed

---

### [维度01-05] nop-code-lang-java Declares Unused nop-commons Dependency

- **File**: `nop-code/nop-code-lang-java/pom.xml:27-29`
- **Evidence Snippet**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-commons</artifactId>
  </dependency>
  ```
- **Severity**: P3
- **Current State**: nop-code-lang-java declares compile-scope dependency on nop-commons but no source file imports from it. Already transitively available through nop-code-core.
- **Risk**: Minimal — redundant but harmless.
- **Recommendation**: Remove the direct nop-commons declaration.
- **Confidence**: Likely
- **False Positive Exclusion**: No main-source Java file imports from `io.nop.commons.*`.
- **Review Status**: Not reviewed

---

## Compliant Module List

| Module | Compliance | Notes |
|--------|-----------|-------|
| nop-code-core | COMPLIANT | Pure algorithmic library |
| nop-code-graph | COMPLIANT | Depends only on nop-code-core + jgrapht |
| nop-code-flow | COMPLIANT | Depends on nop-code-core + nop-code-graph |
| nop-code-lang-java | COMPLIANT | Depends on nop-code-core + javaparser |
| nop-code-lang-python | COMPLIANT | Depends on nop-code-core + tree-sitter |
| nop-code-lang-typescript | COMPLIANT | Depends on nop-code-core + tree-sitter |
| nop-code-codegen | COMPLIANT | Code generation scripts |
| nop-code-dao | COMPLIANT | ORM entities + I*Biz interfaces |
| nop-code-meta | COMPLIANT | Pure resource module |
| nop-code-web | COMPLIANT | Depends on meta + service + nop-web |
| nop-code-app | COMPLIANT | Quarkus deps confined to app layer |

## Summary

**Overall grade: Good with one structural gap.** No circular dependencies, no reverse dependencies, no cross-layer violations. The empty nop-code-api module is the main gap (findings 01-01/01-02/01-03).

**No P0 or P1 findings.** Five findings: 2x P2, 3x P3.

## 维度复核结论

（待复核）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 01-01 | P2 | nop-code-api/pom.xml | 空的 api 模块，无源码、无依赖方 |
| 01-02 | P3 | nop-code-api/pom.xml | 缺少 parent POM 声明 |
| 01-03 | P2 | nop-code-service/.../api/ | ICodeIndexService + 30 DTOs 应在 api 模块 |
| 01-04 | P3 | 多个 pom.xml | 兄弟依赖版本声明不一致 |
| 01-05 | P3 | nop-code-lang-java/pom.xml | 声明了未使用的 nop-commons 依赖 |
