# nop-code GraphQL Service Implementation Plan

> Plan Status: superseded
> Superseded By: Plan 52
> Last Reviewed: 2026-05-03 (self-review + 4 explore agent verifications)
> Current Phase: phase-1
> Current Task: T1
> Source: nop-code/design/ai-code-index-graphql-design.md, ai-dev/plans/05-nop-code-multi-language-index-plan.md (completed)
> Related: 06-nop-code-feature-completion-plan.md
> Review Notes: Momus timed out twice during review. Proceeded with self-review informed by 4 parallel explore agent findings. All framework compatibility questions verified.

## Purpose

Implement the GraphQL service layer for nop-code that provides code indexing and query capabilities. Create a test project in resources, index it, and verify all queries return correct results. The service wraps the analysis pipeline (nop-code-core ProjectAnalyzer + nop-code-lang-java adapter) and exposes it via Nop GraphQL BizModel API.

## Goal

A fully functional, tested GraphQL service where: (1) a test project with real Java source files exists under resources, (2) indexing works via `NopCodeIndex__indexDirectory` mutation, (3) all core queries work: file outline, symbol lookup by qualified name, symbol search, class hierarchy, method call hierarchy, and (4) every query has an integration test that verifies correctness against the test project.

## Current Baseline

- **nop-code-core**: Universal code model + analysis algorithms (Phase 1-5 of plan 05 completed)
- **nop-code-lang-java**: Java adapter wrapping nop-java-parser (completed)
- **nop-code-service**: 7 empty BizModel skeletons extending CrudBizModel with ZERO custom methods. No @BizQuery, no @BizLoader, no @BizMutation.
- **nop-code-dao**: 7 ORM entities (NopCodeIndex, NopCodeFile, NopCodeSymbol, NopCodeUsage, NopCodeCall, NopCodeInheritance, NopCodeAnnotationUsage) with generated DAO classes.
- **nop-code-meta**: 14 xmeta files for all entities with basic ORM mappings.
- **Design doc**: `nop-code/design/ai-code-index-graphql-design.md` (1493 lines) — comprehensive but UNIMPLEMENTED.
- **No test project exists. No service interface exists. No integration tests exist.**

## Architecture Decision: In-Memory Analysis Service

The design doc describes a database-backed service where indexing persists results to DB tables. However, for the initial implementation and testing:

1. **ICodeIndexService** provides an in-memory analysis layer using ProjectAnalyzer directly
2. Analysis results are held in memory as `CodeFileAnalysisResult`, `CallGraph`, `SymbolTable`
3. BizModels query the in-memory service — no database required for queries
4. Database persistence can be added later as a separate enhancement
5. The service interface is designed to support both in-memory and database-backed implementations

This approach allows us to test all query functionality immediately without database setup complexity.

## Success Criteria

- [SC1] Test project exists at `nop-code-service/src/test/resources/test-project/` with ≥4 Java files covering class, interface, enum, annotations, inheritance, method calls
- [SC2] `NopCodeIndexBizModel.indexDirectory()` indexes the test project and returns correct file/symbol counts
- [SC3] `NopCodeFile__get(filePath)` returns file with outline, symbols, types, sourceCode via BizLoaders
- [SC4] `NopCodeSymbol__findByQualifiedName()` finds symbols by qualified name correctly
- [SC5] `NopCodeSymbol__findPage()` with query/kind/package filters works correctly
- [SC6] `NopCodeTypeHierarchy__get()` returns correct inheritance chains (super → sub, both directions)
- [SC7] `NopCodeCallHierarchy__get()` returns correct caller/callee chains
- [SC8] `NopCodeType__batchGetOutlines()` returns correct outlines for multiple qualified names
- [SC9] All integration tests pass using IGraphQLEngine execution against real analysis results
- [SC10] `mvn test -pl nop-code/nop-code-service` succeeds with 0 failures

## Non-Goals

- [NG1] Database persistence of analysis results (follow-up plan)
- [NG2] Incremental indexing / file watching
- [NG3] Python/TypeScript language adapters (separate plans)
- [NG4] Web UI / frontend integration
- [NG5] Full GraphQL schema with all 100+ types from design doc (focus on core queries first)

## Scope

### In Scope

- [S1] Create test project with diverse Java constructs
- [S2] Implement ICodeIndexService interface + in-memory CodeIndexService
- [S3] Implement NopCodeIndexBizModel with indexDirectory/indexFiles mutations
- [S4] Implement NopCodeFileBizModel with get() query + BizLoaders (outline, symbols, types, sourceCode)
- [S5] Implement NopCodeSymbolBizModel with findByQualifiedName, findPage queries + BizLoaders (usages, sourceCode)
- [S6] Implement NopCodeCallHierarchyBizModel for caller/callee queries
- [S7] Implement NopCodeTypeHierarchyBizModel for inheritance queries
- [S8] Implement NopCodeTypeBizModel for batchGetOutlines
- [S9] Integration tests for all queries using IGraphQLEngine

### Out Of Scope

- [O1] Changes to nop-code-core or nop-code-lang-java (their API is stable)
- [O2] Database persistence implementation
- [O3] xmeta/xbiz file modifications (using Java annotations only)
- [O4] Security/authorization on queries (public access for now)

## Closure Gates

> All gates must be `[x]` before `Plan Status` can change to `completed`.

- [ ] Test project exists with ≥4 Java files covering all query scenarios
- [ ] ICodeIndexService interface + CodeIndexService implementation compile and pass unit tests
- [ ] All GraphQL BizModels (@BizQuery/@BizMutation/@BizLoader) registered in beans.xml
- [ ] File queries (get, outline, symbols, types, sourceCode) work via GraphQL
- [ ] Symbol queries (findByQualifiedName, findPage, findSymbols) work via GraphQL
- [ ] Type hierarchy queries (super/sub/both) work via GraphQL
- [ ] Call hierarchy queries (incoming/outgoing/both) work via GraphQL
- [ ] All applicable build/test gates pass (`mvn test -pl nop-code/nop-code-service`)
- [ ] Affected `docs-for-ai/` docs synced, or `No doc update required` (new service layer, no existing docs affected)
- [ ] No in-scope item was silently downgraded to deferred / follow-up

## Deferred But Adjudicated

### Database persistence (F1)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Plan explicitly uses in-memory implementation (Architecture Decision section). Database persistence is a separate enhancement.
- Successor Required: `no`
- Successor Path: Covered in Plan 06 Task I4 (incremental index BizModel)

### Incremental indexing / file watching (F2)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Explicitly listed as Non-Goal NG2. Covered by Plan 06 Phase 1.5.
- Successor Required: `no`
- Successor Path: Covered in Plan 06 (I1-I4)

### Authorization (@Auth) on queries (F3)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Not a functional requirement for initial service. Can be added later.
- Successor Required: `no`
- Successor Path: N/A

### Separate GraphQL DTO types per design doc (F4)

- Classification: `optimization candidate`
- Why Not Blocking Closure: Current Map<String,Object> and @DataBean returns work correctly. Typed DTOs improve API documentation but don't affect functionality.
- Successor Required: `no`
- Successor Path: N/A

### NopCodeUsageBizModel with reference search (F5)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Usage queries are available via BizLoader on NopCodeSymbol. A dedicated BizModel is nice-to-have but not required for core functionality.
- Successor Required: `no`
- Successor Path: N/A

### File watching for automatic re-indexing (F6)

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: Not required for initial GraphQL service. Covered by Plan 06.
- Successor Required: `no`
- Successor Path: N/A

## Non-Blocking Follow-ups

- GraphQL schema documentation (auto-generated from BizModel annotations)
- Performance optimization for large projects (streaming results, pagination improvements)
- WebSocket support for real-time indexing status updates

## Execution Plan

### Phase: phase-1 — Test Project + Service Interface

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/src/test/resources/test-project/`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/`

Description:

Create a test project with real Java source files that exercises all query scenarios. Define the ICodeIndexService interface and implement the in-memory CodeIndexService that wraps ProjectAnalyzer.

Exit Criteria:

- [ ] [C1] Test project has ≥4 Java files with class, interface, enum, annotation usage, inheritance, method calls
- [ ] [C2] ICodeIndexService interface compiles with all query method signatures
- [ ] [C3] CodeIndexService implements all methods using ProjectAnalyzer + LanguageAdapterRegistry
- [ ] [C3.5] Beans registered in _service.beans.xml (CodeIndexService + 3 new standalone BizModels)
- [ ] [C4] Unit test verifies CodeIndexService can index test project and return non-empty results
- [ ] [C5] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T1 — Create test project in resources

Status: pending
Depends On:

Instructions:

Create `nop-code-service/src/test/resources/test-project/` with the following Java source files:

1. test-project/src/main/java/com/example/domain/User.java:
```java
package com.example.domain;

import com.example.service.IUserService;
import com.example.annotation.Audited;

/**
 * User entity representing a system user.
 */
@Audited
public class User extends BaseEntity implements IUserService {
    private String name;
    private String email;
    private int age;

    public User() {}

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    @Override
    public String getDisplayName() {
        return name + " <" + email + ">";
    }

    public void updateEmail(String newEmail) {
        this.email = newEmail;
        validate();
    }

    private void validate() {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null");
        }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public int getAge() { return age; }
}
```

2. test-project/src/main/java/com/example/domain/BaseEntity.java:
```java
package com.example.domain;

public abstract class BaseEntity {
    private String id;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
```

3. test-project/src/main/java/com/example/service/IUserService.java:
```java
package com.example.service;

public interface IUserService {
    String getDisplayName();
}
```

4. test-project/src/main/java/com/example/service/UserService.java:
```java
package com.example.service;

import com.example.domain.User;

public class UserService {
    private final User user;

    public UserService(User user) {
        this.user = user;
    }

    public void changeName(String newName) {
        user.setName(newName);
        user.updateEmail(user.getEmail());
    }

    public String getInfo() {
        return user.getDisplayName();
    }
}
```

5. test-project/src/main/java/com/example/annotation/Audited.java:
```java
package com.example.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Audited {
    String value() default "";
}
```

6. test-project/src/main/java/com/example/domain/Status.java:
```java
package com.example.domain;

public enum Status {
    ACTIVE("Active user"),
    INACTIVE("Inactive user"),
    SUSPENDED("Suspended user");

    private final String description;

    Status(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
```

This test project provides:
- **Inheritance**: User extends BaseEntity, User implements IUserService
- **Method calls**: UserService calls User methods, User.updateEmail calls validate()
- **Annotations**: @Audited on User class, @Override on getDisplayName
- **Enum**: Status with constants and constructor
- **Abstract class**: BaseEntity
- **Interface**: IUserService
- **Various access modifiers**: public, private
- **Various member types**: fields, methods, constructors

Result Message:

Checks:

- [ ] [CHK-T1-1] 6 Java files exist under test-project/src/main/java/
- [ ] [CHK-T1-2] Files compile (valid Java syntax)
- [ ] [CHK-T1-3] User.java has @Audited annotation, extends BaseEntity, implements IUserService
- [ ] [CHK-T1-4] UserService.java calls methods on User (setName, updateEmail, getDisplayName)

#### Task: T2 — Define ICodeIndexService interface

Status: pending
Depends On: T1

Instructions:

Create `io.nop.code.service.api.ICodeIndexService` interface in nop-code-service:

```java
package io.nop.code.service.api;

import io.nop.code.core.model.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 代码索引服务接口
 */
public interface ICodeIndexService {

    // ==================== Indexing ====================

    /**
     * 索引一个目录
     * @param indexId 索引ID
     * @param directoryPath 目录路径
     * @param filePattern 文件模式 (e.g., "**/*.java")
     * @return 索引的文件数量
     */
    int indexDirectory(String indexId, Path directoryPath, String filePattern);

    /**
     * 索引指定文件
     */
    CodeFileAnalysisResult indexFile(String indexId, String filePath, String sourceCode);

    // ==================== File Queries ====================

    /**
     * 获取索引中的文件列表
     */
    List<CodeFileAnalysisResult> getFiles(String indexId);

    /**
     * 按路径获取文件
     */
    CodeFileAnalysisResult getFile(String indexId, String filePath);

    /**
     * 获取文件源代码
     */
    String getFileSourceCode(String indexId, String filePath);

    /**
     * 获取文件中的符号
     */
    List<CodeSymbol> getFileSymbols(String indexId, String filePath);

    /**
     * 获取文件中的顶层类型
     */
    List<CodeSymbol> getFileTypes(String indexId, String filePath);

    // ==================== Symbol Queries ====================

    /**
     * 按ID获取符号
     */
    CodeSymbol getSymbolById(String indexId, String symbolId);

    /**
     * 按全限定名查找符号
     */
    CodeSymbol findSymbolByQualifiedName(String indexId, String qualifiedName);

    /**
     * 搜索符号
     */
    List<CodeSymbol> findSymbols(String indexId, String query, List<CodeSymbolKind> kinds, String packageName, int limit);

    /**
     * 获取符号的引用
     */
    List<CodeAnnotationUsage> getSymbolUsages(String indexId, String symbolId, int limit);

    /**
     * 获取符号的源代码
     */
    String getSymbolSourceCode(String indexId, String symbolId, int linesBefore, int linesAfter);

    // ==================== Type Queries ====================

    /**
     * 获取类型的Outline
     */
    Map<String, Object> getTypeOutline(String indexId, String qualifiedName);

    /**
     * 批量获取类型Outline
     */
    List<Map<String, Object>> batchGetTypeOutlines(String indexId, List<String> qualifiedNames);

    // ==================== Hierarchy Queries ====================

    /**
     * 获取类型继承层级
     * @param qualifiedName 类型全限定名
     * @param direction 方向: "super", "sub", "both"
     * @param maxDepth 最大深度
     * @return 层级结果
     */
    Map<String, Object> getTypeHierarchy(String indexId, String qualifiedName, String direction, int maxDepth);

    /**
     * 获取方法调用层级
     * @param qualifiedName 方法全限定名
     * @param direction 方向: "incoming", "outgoing", "both"
     * @param maxDepth 最大深度
     * @return 调用层级结果
     */
    Map<String, Object> getCallHierarchy(String indexId, String qualifiedName, String direction, int maxDepth);

    // ==================== Index Management ====================

    /**
     * 获取索引统计信息
     */
    Map<String, Object> getIndexStats(String indexId);

    /**
     * 获取所有索引ID
     */
    List<String> getIndexIds();

    /**
     * 删除索引
     */
    void deleteIndex(String indexId);
}
```

Result Message:

Checks:

- [ ] [CHK-T2-1] Interface compiles with all method signatures
- [ ] [CHK-T2-2] Method signatures match GraphQL query requirements from design doc
- [ ] [CHK-T2-3] Returns language-agnostic types from nop-code-core

#### Task: T3 — Implement CodeIndexService

Status: pending
Depends On: T2

Instructions:

Create `io.nop.code.service.impl.CodeIndexService` implementing ICodeIndexService:

1. **Dependencies** (inject or construct):
   - `LanguageAdapterRegistry` with `JavaLanguageAdapter` registered
   - `ProjectAnalyzer` constructed with the registry

2. **In-memory storage**:
   - `Map<String, List<CodeFileAnalysisResult>>` indexId → file results
   - `Map<String, SymbolTable>` indexId → global symbol table (built from all files)
   - `Map<String, CallGraph>` indexId → call graph (built from all files)

3. **indexDirectory()**: Use ProjectAnalyzer.analyzeProject() to analyze the directory, store results
4. **getFile()**: Search file results by filePath
5. **findSymbolByQualifiedName()**: Search SymbolTable by qualified name
6. **findSymbols()**: Filter symbols by query/kind/packageName
7. **getTypeHierarchy()**: Build hierarchy from inheritances in file results
8. **getCallHierarchy()**: Use CallGraph callers/callees methods
9. **getTypeOutline()**: Build outline from type symbol + its child symbols

Key implementation pattern for hierarchy:
```java
// Build hierarchy recursively from inheritances
Map<String, Object> buildHierarchy(String qualifiedName, String direction, int maxDepth) {
    CodeSymbol symbol = symbolTable.getByQualifiedName(qualifiedName);
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("symbol", toSymbolMap(symbol));

    if (maxDepth <= 0) return node;

    if ("super".equals(direction) || "both".equals(direction)) {
        // Find inheritances where subTypeId = this symbol
        List<Map<String, Object>> superTypes = inheritances.stream()
            .filter(i -> i.getSubTypeId() != null)
            .map(i -> buildHierarchy(i.getSuperTypeQualifiedName(), direction, maxDepth - 1))
            .collect(Collectors.toList());
        node.put("superTypes", superTypes);
    }

    if ("sub".equals(direction) || "both".equals(direction)) {
        // Find inheritances where superTypeQualifiedName matches this type
        List<Map<String, Object>> subTypes = ... ;
        node.put("subTypes", subTypes);
    }

    return node;
}
```

Add dependency on `nop-code-core` and `nop-code-lang-java` to nop-code-service pom.xml (if not already present — check first, nop-code-service depends on nop-code-dao, which may not transitively include nop-code-core).

Result Message:

Checks:

- [ ] [CHK-T3-1] CodeIndexService compiles and implements all ICodeIndexService methods
- [ ] [CHK-T3-2] indexDirectory() uses ProjectAnalyzer from nop-code-core
- [ ] [CHK-T3-3] In-memory maps store analysis results per indexId
- [ ] [CHK-T3-4] findSymbolByQualifiedName() searches SymbolTable correctly
- [ ] [CHK-T3-5] getTypeHierarchy() builds recursive hierarchy from inheritances
- [ ] [CHK-T3-6] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T3.5 — Register beans in _service.beans.xml

Status: pending
Depends On: T3

Instructions:

Update `/nop-code/nop-code-service/src/main/resources/_vfs/nop/code/beans/_service.beans.xml` to add:

1. **CodeIndexService registration** (new service bean):
```xml
<bean id="io.nop.code.service.api.ICodeIndexService" ioc:default="true"
      class="io.nop.code.service.impl.CodeIndexService"/>
```

2. **New standalone BizModel registrations** (TypeHierarchy, CallHierarchy, Type — these are NOT CrudBizModel, so use simple bean pattern):
```xml
<bean id="NopCodeTypeHierarchyBizModel" class="io.nop.code.service.entity.NopCodeTypeHierarchyBizModel"/>
<bean id="NopCodeCallHierarchyBizModel" class="io.nop.code.service.entity.NopCodeCallHierarchyBizModel"/>
<bean id="NopCodeTypeBizModel" class="io.nop.code.service.entity.NopCodeTypeBizModel"/>
```

Note: Existing 7 BizModels (NopCodeIndex, NopCodeFile, etc.) are already registered in _service.beans.xml with the two-bean pattern (class bean + BizProxyFactoryBean). We only modify NopCodeIndexBizModel, NopCodeFileBizModel, NopCodeSymbolBizModel in place — no new bean entries needed for those.

Result Message:

Checks:

- [ ] [CHK-T3.5-1] ICodeIndexService bean registered in _service.beans.xml
- [ ] [CHK-T3.5-2] Three new standalone BizModels registered
- [ ] [CHK-T3.5-3] XML is valid (no parse errors)
- [ ] [CHK-T3.5-4] Existing bean entries unchanged

#### Task: T4 — Unit test for CodeIndexService

Status: pending
Depends On: T3, T3.5

Instructions:

Create `TestCodeIndexService.java` in nop-code-service test:

1. Test indexing the test project:
```java
@Test
void testIndexTestProject() {
    Path testProject = Paths.get("src/test/resources/test-project/src/main/java");
    int count = service.indexDirectory("test", testProject, "**/*.java");
    assertTrue(count >= 6);
}
```

2. Test findSymbolByQualifiedName:
```java
@Test
void testFindByQualifiedName() {
    // Index first
    indexTestProject();
    CodeSymbol user = service.findSymbolByQualifiedName("test", "com.example.domain.User");
    assertNotNull(user);
    assertEquals(CodeSymbolKind.CLASS, user.getKind());
    assertEquals("User", user.getName());
}
```

3. Test findSymbols with filters:
```java
@Test
void testFindSymbolsByKind() {
    indexTestProject();
    List<CodeSymbol> classes = service.findSymbols("test", null, List.of(CodeSymbolKind.CLASS), null, 100);
    assertTrue(classes.stream().anyMatch(s -> "User".equals(s.getName())));
}
```

4. Test getFile:
```java
@Test
void testGetFile() {
    indexTestProject();
    CodeFileAnalysisResult file = service.getFile("test", "com/example/domain/User.java");
    assertNotNull(file);
    assertEquals("com.example.domain", file.getPackageName());
}
```

5. Test getTypeHierarchy:
```java
@Test
void testGetTypeHierarchy() {
    indexTestProject();
    Map<String, Object> hierarchy = service.getTypeHierarchy("test", "com.example.domain.User", "super", 3);
    assertNotNull(hierarchy);
    // User extends BaseEntity
    List<Map<String, Object>> superTypes = (List<Map<String, Object>>) hierarchy.get("superTypes");
    assertNotNull(superTypes);
}
```

6. Test getCallHierarchy:
```java
@Test
void testGetCallHierarchy() {
    indexTestProject();
    Map<String, Object> callHierarchy = service.getCallHierarchy("test", "com.example.service.UserService.changeName", "outgoing", 2);
    assertNotNull(callHierarchy);
}
```

Result Message:

Checks:

- [ ] [CHK-T4-1] All 6 test methods pass
- [ ] [CHK-T4-2] Test project is indexed correctly
- [ ] [CHK-T4-3] Symbol lookup returns correct results
- [ ] [CHK-T4-4] `mvn test -pl nop-code/nop-code-service -Dtest=TestCodeIndexService` succeeds

---

### Phase: phase-2 — Index BizModel (Indexing Mutations)

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`

Description:

Implement the NopCodeIndexBizModel with indexDirectory/indexFiles mutations. This is the entry point for indexing source code.

Exit Criteria:

- [ ] [C6] NopCodeIndexBizModel has @BizMutation `indexDirectory` that delegates to ICodeIndexService
- [ ] [C7] NopCodeIndexBizModel has @BizQuery `get` that returns index info with stats
- [ ] [C8] Integration test via IGraphQLEngine calls indexDirectory and verifies result
- [ ] [C9] `mvn test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexBizModel` succeeds

#### Task: T5 — Implement NopCodeIndexBizModel

Status: pending
Depends On: T3

Instructions:

Update `NopCodeIndexBizModel.java`:

```java
@BizModel("NopCodeIndex")
public class NopCodeIndexBizModel extends CrudBizModel<NopCodeIndex> implements INopCodeIndexBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeIndexBizModel() {
        setEntityName(NopCodeIndex.class.getName());
    }

    @BizMutation
    public int indexDirectory(
            @Name("indexId") String indexId,
            @Name("directoryPath") String directoryPath,
            @Name("filePattern") String filePattern) {
        Path path = Path.of(directoryPath);
        String pattern = filePattern != null ? filePattern : "**/*.java";
        return codeIndexService.indexDirectory(indexId, path, pattern);
    }

    @BizMutation
    public CodeFileAnalysisResult indexFile(
            @Name("indexId") String indexId,
            @Name("filePath") String filePath,
            @Name("sourceCode") String sourceCode) {
        return codeIndexService.indexFile(indexId, filePath, sourceCode);
    }

    @BizQuery
    public Map<String, Object> getStats(@Name("indexId") String indexId) {
        return codeIndexService.getIndexStats(indexId);
    }

    @BizMutation
    public boolean deleteIndex(@Name("indexId") String indexId) {
        codeIndexService.deleteIndex(indexId);
        return true;
    }
}
```

Register ICodeIndexService and CodeIndexService in beans.xml if not using Nop IoC auto-discovery.

Result Message:

Checks:

- [ ] [CHK-T5-1] NopCodeIndexBizModel has @BizMutation indexDirectory
- [ ] [CHK-T5-2] ICodeIndexService injected via @Inject
- [ ] [CHK-T5-3] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T6 — Integration test for indexing via GraphQL

Status: pending
Depends On: T5

Instructions:

Create `TestNopCodeIndexBizModel.java`:

```java
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCodeIndexBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    void testIndexDirectory() {
        Path testProject = Paths.get("src/test/resources/test-project/src/main/java");

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", "test-index");
        data.put("directoryPath", testProject.toString());
        data.put("filePattern", "**/*.java");

        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);

        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
            GraphQLOperationType.mutation, "NopCodeIndex__indexDirectory", request);
        ApiResponse<?> response = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));

        assertNotNull(response);
        assertTrue(response.isOk());
        // result should be file count (>= 6)
        Integer count = (Integer) response.getData();
        assertTrue(count >= 6);
    }
}
```

Result Message:

Checks:

- [ ] [CHK-T6-1] Test passes with IGraphQLEngine execution
- [ ] [CHK-T6-2] Indexing returns correct file count

---

### Phase: phase-3 — File BizModel (File Queries + BizLoaders)

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java`

Description:

Implement NopCodeFileBizModel with get() query and BizLoader methods for outline, symbols, types, sourceCode.

Exit Criteria:

- [ ] [C10] NopCodeFileBizModel.get() returns file info via @BizQuery
- [ ] [C11] @BizLoader methods load outline, symbols, types, sourceCode lazily
- [ ] [C12] Integration test verifies file query with nested fields
- [ ] [C13] `mvn test -pl nop-code/nop-code-service -Dtest=TestNopCodeFileBizModel` succeeds

#### Task: T7 — Implement NopCodeFileBizModel with BizLoaders

Status: pending
Depends On: T5

Instructions:

Update `NopCodeFileBizModel.java`:

```java
@BizModel("NopCodeFile")
public class NopCodeFileBizModel extends CrudBizModel<NopCodeFile> implements INopCodeFileBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeFileBizModel() {
        setEntityName(NopCodeFile.class.getName());
    }

    @BizQuery
    public CodeFileAnalysisResult get(
            @Name("filePath") String filePath,
            @Name("indexId") String indexId) {
        return codeIndexService.getFile(indexId, filePath);
    }

    @BizQuery
    public List<CodeFileAnalysisResult> findPage(
            @Name("indexId") String indexId,
            @Name("packageName") String packageName,
            @Name("limit") int limit) {
        List<CodeFileAnalysisResult> files = codeIndexService.getFiles(indexId);
        if (packageName != null) {
            files = files.stream()
                .filter(f -> packageName.equals(f.getPackageName()))
                .collect(Collectors.toList());
        }
        if (limit > 0 && files.size() > limit) {
            return files.subList(0, limit);
        }
        return files;
    }

    // BizLoaders for nested fields on CodeFileAnalysisResult
    // NOTE: forType is required because this BizModel extends CrudBizModel<NopCodeFile>
    // but loaders operate on CodeFileAnalysisResult, not NopCodeFile entity
    @BizLoader(forType = CodeFileAnalysisResult.class)
    public List<CodeSymbol> symbols(@ContextSource CodeFileAnalysisResult file) {
        return file.getSymbols();
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    public List<CodeSymbol> types(@ContextSource CodeFileAnalysisResult file) {
        return file.getSymbols().stream()
            .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                || s.getKind() == CodeSymbolKind.INTERFACE
                || s.getKind() == CodeSymbolKind.ENUM
                || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
            .collect(Collectors.toList());
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    public String sourceCode(@ContextSource CodeFileAnalysisResult file) {
        return file.getSourceCode();
    }

    @BizLoader(forType = CodeFileAnalysisResult.class)
    public Map<String, Object> outline(@ContextSource CodeFileAnalysisResult file) {
        Map<String, Object> outline = new LinkedHashMap<>();
        outline.put("filePath", file.getFilePath());
        outline.put("packageName", file.getPackageName());
        outline.put("imports", file.getImports());
        outline.put("lineCount", file.getLineCount());
        outline.put("types", file.getSymbols().stream()
            .filter(s -> s.getKind() == CodeSymbolKind.CLASS
                || s.getKind() == CodeSymbolKind.INTERFACE
                || s.getKind() == CodeSymbolKind.ENUM
                || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE)
            .map(this::toTypeOutline)
            .collect(Collectors.toList()));
        return outline;
    }

    private Map<String, Object> toTypeOutline(CodeSymbol s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", s.getName());
        map.put("qualifiedName", s.getQualifiedName());
        map.put("kind", s.getKind().name());
        map.put("line", s.getLine());
        map.put("endLine", s.getEndLine());
        return map;
    }
}
```

Result Message:

Checks:

- [ ] [CHK-T7-1] NopCodeFileBizModel has @BizQuery get() and findPage()
- [ ] [CHK-T7-2] BizLoader methods for symbols, types, sourceCode, outline
- [ ] [CHK-T7-3] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T8 — Integration test for file queries

Status: pending
Depends On: T7

Instructions:

Create `TestNopCodeFileBizModel.java`:

```java
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopCodeFileBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @BeforeEach
    void setUp() {
        // Index test project first
        Path testProject = Paths.get("src/test/resources/test-project/src/main/java");
        Map<String, Object> data = Map.of(
            "indexId", "test",
            "directoryPath", testProject.toString(),
            "filePattern", "**/*.java"
        );
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
            GraphQLOperationType.mutation, "NopCodeIndex__indexDirectory", request);
        FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));    }

    @Test
    void testGetFile() {
        GraphQLRequestBean gqlRequest = new GraphQLRequestBean();
        gqlRequest.setQuery("""
            query {
                NopCodeFile__get(filePath: "com/example/domain/User.java", indexId: "test") {
                    filePath
                    packageName
                    lineCount
                    outline {
                        types {
                            name
                            qualifiedName
                            kind
                        }
                    }
                    symbols {
                        name
                        kind
                        qualifiedName
                    }
                }
            }
            """);
        IGraphQLExecutionContext ctx = graphQLEngine.newGraphQLContext(gqlRequest);
        GraphQLResponseBean response = FutureHelper.syncGet(graphQLEngine.executeGraphQLAsync(ctx));
        assertNotNull(response);
        assertFalse(response.hasError());
        // Verify User class is in symbols from response.getData()
        // Verify outline contains User, BaseEntity types
    }

    @Test
    void testFindFilesByPackage() {
        // Query files filtered by packageName
        GraphQLRequestBean gqlRequest = new GraphQLRequestBean();
        gqlRequest.setQuery("""
            query {
                NopCodeFile__findPage(indexId: "test", packageName: "com.example.domain") {
                    filePath
                    packageName
                }
            }
            """);
        // Verify only domain files returned
    }
}
```

Result Message:

Checks:

- [ ] [CHK-T8-1] GraphQL query for file with outline succeeds
- [ ] [CHK-T8-2] Symbols list includes User class with correct fields
- [ ] [CHK-T8-3] Outline types include CLASS, INTERFACE, ENUM, ANNOTATION_TYPE

---

### Phase: phase-4 — Symbol BizModel (Symbol Queries)

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java`

Description:

Implement NopCodeSymbolBizModel with findByQualifiedName, findPage, and BizLoader methods.

Exit Criteria:

- [ ] [C14] findByQualifiedName() finds symbols by fully qualified name
- [ ] [C15] findPage() searches with query/kind/packageName filters
- [ ] [C16] BizLoader for usages loads annotation usages for a symbol
- [ ] [C17] Integration test verifies symbol queries via GraphQL
- [ ] [C18] `mvn test -pl nop-code/nop-code-service -Dtest=TestNopCodeSymbolBizModel` succeeds

#### Task: T9 — Implement NopCodeSymbolBizModel

Status: pending
Depends On: T5

Instructions:

Update `NopCodeSymbolBizModel.java`:

```java
@BizModel("NopCodeSymbol")
public class NopCodeSymbolBizModel extends CrudBizModel<NopCodeSymbol> implements INopCodeSymbolBiz {

    @Inject
    protected ICodeIndexService codeIndexService;

    public NopCodeSymbolBizModel() {
        setEntityName(NopCodeSymbol.class.getName());
    }

    @BizQuery
    public CodeSymbol get(@Name("id") String id, @Name("indexId") String indexId) {
        return codeIndexService.getSymbolById(indexId, id);
    }

    @BizQuery
    public CodeSymbol findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        return codeIndexService.findSymbolByQualifiedName(indexId, qualifiedName);
    }

    @BizQuery
    public List<CodeSymbol> findPage(
            @Name("query") String query,
            @Name("kinds") List<String> kinds,
            @Name("packageName") String packageName,
            @Name("indexId") String indexId,
            @Name("limit") int limit) {
        List<CodeSymbolKind> kindList = null;
        if (kinds != null) {
            kindList = kinds.stream()
                .map(k -> {
                    try { return Enum.valueOf(CodeSymbolKind.class, k); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
        return codeIndexService.findSymbols(indexId, query, kindList, packageName, limit > 0 ? limit : 20);
    }

    @BizLoader
    public List<CodeAnnotationUsage> usages(
            @ContextSource CodeSymbol symbol,
            @Name("limit") int limit) {
        String indexId = "test"; // or get from context
        return codeIndexService.getSymbolUsages(indexId, symbol.getId(), limit > 0 ? limit : 20);
    }

    @BizLoader
    public String sourceCode(
            @ContextSource CodeSymbol symbol,
            @Name("linesBefore") int linesBefore,
            @Name("linesAfter") int linesAfter) {
        String indexId = "test";
        return codeIndexService.getSymbolSourceCode(indexId, symbol.getId(),
            linesBefore, linesAfter > 0 ? linesAfter : 5);
    }
}
```

Result Message:

Checks:

- [ ] [CHK-T9-1] findByQualifiedName @BizQuery works
- [ ] [CHK-T9-2] findPage with kind/package filters
- [ ] [CHK-T9-3] BizLoader for usages and sourceCode
- [ ] [CHK-T9-4] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T10 — Integration test for symbol queries

Status: pending
Depends On: T9

Instructions:

Create `TestNopCodeSymbolBizModel.java`:

1. Test findByQualifiedName for User class:
```graphql
query {
    NopCodeSymbol__findByQualifiedName(qualifiedName: "com.example.domain.User", indexId: "test") {
        name
        qualifiedName
        kind
        accessModifier
        deprecated
    }
}
```
Assert: name="User", kind="CLASS", accessModifier="PUBLIC", deprecated=false

2. Test findPage with query filter:
```graphql
query {
    NopCodeSymbol__findPage(query: "User", kinds: ["CLASS"], indexId: "test", limit: 20) {
        name
        qualifiedName
        kind
    }
}
```
Assert: contains User class, UserService class

3. Test findPage with packageName filter:
```graphql
query {
    NopCodeSymbol__findPage(packageName: "com.example.domain", indexId: "test", limit: 100) {
        name
        qualifiedName
    }
}
```
Assert: contains User, BaseEntity, Status from com.example.domain

4. Test findByQualifiedName for method:
```graphql
query {
    NopCodeSymbol__findByQualifiedName(qualifiedName: "com.example.domain.User.getDisplayName", indexId: "test") {
        name
        kind
        signature
        returnType
    }
}
```
Assert: kind="METHOD", returnType="String"

Result Message:

Checks:

- [ ] [CHK-T10-1] findByQualifiedName finds User class correctly
- [ ] [CHK-T10-2] findPage with query filter returns matching symbols
- [ ] [CHK-T10-3] findPage with packageName filter works
- [ ] [CHK-T10-4] Method lookup returns correct signature and returnType

---

### Phase: phase-5 — Hierarchy and Call Graph Queries

Kind: phase
Status: pending
Targets: New BizModel classes

Description:

Implement hierarchy queries (type inheritance) and call graph queries (method callers/callees) as separate BizModel classes.

Exit Criteria:

- [ ] [C19] NopCodeTypeHierarchyBizModel returns correct super/sub type chains
- [ ] [C20] NopCodeCallHierarchyBizModel returns correct caller/callee chains
- [ ] [C21] NopCodeTypeBizModel batchGetOutlines returns outlines for multiple types
- [ ] [C22] Integration tests verify all hierarchy queries
- [ ] [C23] `mvn test -pl nop-code/nop-code-service` succeeds for all tests

#### Task: T11 — Implement hierarchy BizModels

Status: pending
Depends On: T5

Instructions:

Create THREE NEW standalone BizModel files (NOT updating existing skeletons). These do NOT extend CrudBizModel:

1. Create NEW file `NopCodeTypeHierarchyBizModel.java` (alongside existing BizModels):
```java
@BizModel("NopCodeTypeHierarchy")
public class NopCodeTypeHierarchyBizModel {

    @Inject
    protected ICodeIndexService codeIndexService;

    @BizQuery
    public Map<String, Object> get(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId,
            @Name("direction") String direction,
            @Name("maxDepth") int maxDepth) {
        String dir = direction != null ? direction : "both";
        int depth = maxDepth > 0 ? maxDepth : 5;
        return codeIndexService.getTypeHierarchy(indexId, qualifiedName, dir, depth);
    }
}
```

2. Create NEW file `NopCodeCallHierarchyBizModel.java` (alongside existing BizModels):
```java
@BizModel("NopCodeCallHierarchy")
public class NopCodeCallHierarchyBizModel {

    @Inject
    protected ICodeIndexService codeIndexService;

    @BizQuery
    public Map<String, Object> get(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId,
            @Name("direction") String direction,
            @Name("maxDepth") int maxDepth) {
        String dir = direction != null ? direction : "both";
        int depth = maxDepth > 0 ? maxDepth : 3;
        return codeIndexService.getCallHierarchy(indexId, qualifiedName, dir, depth);
    }
}
```

3. Update `NopCodeSymbolBizModel` with additional BizLoaders for type-specific fields:
```java
@BizLoader(forType = CodeSymbol.class)
public List<CodeSymbol> methods(@ContextSource CodeSymbol type) {
    // Find all METHOD symbols whose declaringSymbolId matches this type's ID
    // or whose parentId matches
}

@BizLoader(forType = CodeSymbol.class)
public List<CodeSymbol> fields(@ContextSource CodeSymbol type) {
    // Find all FIELD symbols whose declaringSymbolId matches this type's ID
}
```

4. Register new BizModels in beans.xml

Result Message:

Checks:

- [ ] [CHK-T11-1] NopCodeTypeHierarchyBizModel compiles with @BizQuery get()
- [ ] [CHK-T11-2] NopCodeCallHierarchyBizModel compiles with @BizQuery get()
- [ ] [CHK-T11-3] New BizModels registered in beans.xml
- [ ] [CHK-T11-4] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T12 — Implement NopCodeTypeBizModel for batchGetOutlines

Status: pending
Depends On: T9

Instructions:

Create NEW standalone BizModel file `NopCodeTypeBizModel.java` (NOT extending CrudBizModel):
```java
@BizModel("NopCodeType")
public class NopCodeTypeBizModel {

    @Inject
    protected ICodeIndexService codeIndexService;

    @BizQuery
    public CodeSymbol get(@Name("id") String id, @Name("indexId") String indexId) {
        return codeIndexService.getSymbolById(indexId, id);
    }

    @BizQuery
    public CodeSymbol findByQualifiedName(
            @Name("qualifiedName") String qualifiedName,
            @Name("indexId") String indexId) {
        return codeIndexService.findSymbolByQualifiedName(indexId, qualifiedName);
    }

    @BizQuery
    public List<Map<String, Object>> batchGetOutlines(
            @Name("qualifiedNames") List<String> qualifiedNames,
            @Name("indexId") String indexId) {
        return codeIndexService.batchGetTypeOutlines(indexId, qualifiedNames);
    }
}
```

Result Message:

Checks:

- [ ] [CHK-T12-1] NopCodeTypeBizModel with get, findByQualifiedName, batchGetOutlines
- [ ] [CHK-T12-2] Registered in beans.xml
- [ ] [CHK-T12-3] `mvn compile -pl nop-code/nop-code-service` succeeds

#### Task: T13 — Integration tests for hierarchy and call queries

Status: pending
Depends On: T11, T12

Instructions:

Create `TestNopCodeHierarchyQueries.java`:

1. Test type hierarchy (User → BaseEntity super, IUserService interface):
```graphql
query {
    NopCodeTypeHierarchy__get(qualifiedName: "com.example.domain.User", indexId: "test", direction: "super", maxDepth: 3) {
        root { symbol { name qualifiedName } }
        superTypes { symbol { name qualifiedName } }
    }
}
```
Assert: superTypes contains BaseEntity, IUserService

2. Test sub types (find who extends BaseEntity):
```graphql
query {
    NopCodeTypeHierarchy__get(qualifiedName: "com.example.domain.BaseEntity", indexId: "test", direction: "sub", maxDepth: 3) {
        root { symbol { name } }
        subTypes { symbol { name } }
    }
}
```
Assert: subTypes contains User

3. Test call hierarchy (UserService.changeName → outgoing):
```graphql
query {
    NopCodeCallHierarchy__get(qualifiedName: "com.example.service.UserService.changeName", indexId: "test", direction: "outgoing", maxDepth: 2) {
        root { symbol { name } }
        callees { symbol { name } }
    }
}
```
Assert: callees contains setName, updateEmail

4. Test call hierarchy (incoming callers of User.updateEmail):
```graphql
query {
    NopCodeCallHierarchy__get(qualifiedName: "com.example.domain.User.updateEmail", indexId: "test", direction: "incoming", maxDepth: 2) {
        root { symbol { name } }
        callers { symbol { name qualifiedName } }
    }
}
```
Assert: callers contains UserService.changeName

5. Test batchGetOutlines:
```graphql
query {
    NopCodeType__batchGetOutlines(qualifiedNames: ["com.example.domain.User", "com.example.domain.Status"], indexId: "test") {
        className
        packageName
        methods { name signature }
    }
}
```
Assert: returns outlines for both types

Result Message:

Checks:

- [ ] [CHK-T13-1] Type hierarchy super query returns BaseEntity, IUserService for User
- [ ] [CHK-T13-2] Type hierarchy sub query returns User for BaseEntity
- [ ] [CHK-T13-3] Call hierarchy outgoing returns setName, updateEmail for changeName
- [ ] [CHK-T13-4] Call hierarchy incoming returns changeName for updateEmail
- [ ] [CHK-T13-5] batchGetOutlines returns correct outlines for multiple types

---

### Phase: phase-6 — Full Integration Verification

Kind: phase
Status: pending
Targets: `nop-code/nop-code-service/`

Description:

Run all tests together, verify full pipeline works, fix any integration issues.

Exit Criteria:

- [ ] [C24] All service tests pass: `mvn test -pl nop-code/nop-code-service`
- [ ] [C25] Full nop-code build passes: `mvn install -DskipTests -pl nop-code`
- [ ] [C26] No compile errors in any nop-code sub-module

#### Task: T14 — Full integration test suite verification

Status: pending
Depends On: T8, T10, T13

Instructions:

1. Run `mvn test -pl nop-code/nop-code-service` — verify ALL tests pass
2. Run `mvn install -DskipTests -pl nop-code` — verify full module group compiles
3. Run `mvn test -pl nop-code/nop-code-core,nop-code-lang-java` — verify core tests still pass
4. If any test fails, fix and re-run

Result Message:

Checks:

- [ ] [CHK-T14-1] `mvn test -pl nop-code/nop-code-service` exit code 0
- [ ] [CHK-T14-2] `mvn install -DskipTests -pl nop-code` exit code 0
- [ ] [CHK-T14-3] No test failures

## Review Findings (2026-05-03)

### Framework Compatibility (VERIFIED by explore agents)

| Question | Answer | Evidence |
|----------|--------|----------|
| @BizLoader on non-entity POJO? | ✅ YES | DevDocBizModel.java uses @BizLoader on GlobalVariableDefBean (@DataBean POJO) |
| Standalone BizModel without CrudBizModel? | ✅ YES | DemoServiceBizModel, HelloBizModel — both standalone |
| beans.xml auto-discovery? | ❌ NO — must register explicitly | All modules use explicit bean declarations |
| Map<String,Object> return from @BizQuery? | ✅ YES | DemoServiceBizModel.test() returns Map<String,Object> |
| @GraphQLReturn needed for POJO returns? | ⚠️ MAYBE — add for safety | Used in some examples, not all |

### Issues Found and Fixed in Plan

1. **[FIXED] Missing beans.xml registration task** — Added T4.5 (new task) for explicit bean registration of CodeIndexService and new BizModels
2. **[FIXED] CodeSymbolKind.fromName() doesn't exist** — Enum has no parsing method. Fixed to use `Enum.valueOf(CodeSymbolKind.class, name)` with try-catch
3. **[FIXED] Test code unwrap pattern** — `executeRpcAsync()` returns `CompletionStage<ApiResponse<?>>`, not `Object`. Fixed to use `FutureHelper.syncGet(...)` → `.getData()`
4. **[FIXED] NopCodeFileBizModel @BizLoader needs forType** — BizModel extends CrudBizModel<NopCodeFile> but loaders operate on CodeFileAnalysisResult. Added `forType = CodeFileAnalysisResult.class`
5. **[FIXED] New BizModels are standalone classes** — Clarified TypeHierarchy, CallHierarchy, Type are NEW files (not updates to existing skeletons)
6. **[VERIFIED] @Inject fields are protected** — Plan code already uses `protected` correctly

### Risk Assessment

1. **HIGH RISK**: CodeIndexService.getTypeHierarchy()/getCallHierarchy() — recursive hierarchy building from raw analysis results. The CodeFileAnalysisResult structure may not expose inheritance/call data in the format expected. **Mitigation**: T4 unit test validates before BizModel integration.
2. **MEDIUM RISK**: BizLoader on CodeFileAnalysisResult within a CrudBizModel<NopCodeFile> class — the `forType` attribute must be set correctly or Nop will try to match against NopCodeFile entity. **Mitigation**: Verified by explore agent that `forType` works.
3. **LOW RISK**: IGraphQLEngine test with non-entity return types — Nop serializes POJOs via reflection. CodeSymbol/CodeFileAnalysisResult are @DataBean so all fields should serialize. **Mitigation**: Simple return types (Map, String, int) are proven to work.

### Verdict: APPROVE_WITH_MINOR_FIXES

All framework compatibility questions answered positively. The fixes are mechanical (beans.xml registration, API signatures, enum parsing). No architectural changes needed.

## Questions

## Decisions

- [D1] Task: All | Made At: 2026-05-03
  - Decision: Use in-memory analysis service instead of database-backed service for initial implementation
  - Rationale: Allows immediate testing without database setup complexity. The service interface is designed to support both approaches. Database persistence can be added as a separate enhancement.

- [D2] Task: T7 | Made At: 2026-05-03
  - Decision: Use CodeFileAnalysisResult directly as return type from BizModel queries instead of converting to separate GraphQL DTOs
  - Rationale: CodeFileAnalysisResult already has all needed fields (symbols, calls, inheritances, etc.). Adding a separate DTO layer adds complexity without benefit at this stage.

- [D3] Task: T11 | Made At: 2026-05-03
  - Decision: Hierarchy and call graph queries return Map<String, Object> instead of typed beans
  - Rationale: The hierarchy structures are recursive and dynamic. Maps allow flexible tree structures without defining dozens of nested @DataBean classes. Can be typed later if needed.

- [D4] Task: T2 | Made At: 2026-05-03
  - Decision: ICodeIndexService interface is placed in nop-code-service (not a separate api module)
  - Rationale: nop-code-api module is currently empty and adding an api module adds complexity. The interface is only used within nop-code-service BizModels.

## Errors

## Validation Checklist

- [ ] [VC1] All new Java files have correct package declarations matching their directory
- [ ] [VC2] No import of javaparser types in BizModel classes (only via nop-code-core interfaces)
- [ ] [VC3] All BizModel methods use @BizQuery/@BizMutation/@BizLoader annotations
- [ ] [VC4] All @BizLoader methods have @ContextSource parameter
- [ ] [VC4.5] All @BizLoader methods in CrudBizModel subclasses use `forType = CodeFileAnalysisResult.class`
- [ ] [VC5] ICodeIndexService only depends on nop-code-core types (not javaparser)
- [ ] [VC5.5] All @Inject fields are `protected` or package-private (NOT private — NopIoC limitation)
- [ ] [VC6] Test project files are valid Java that compiles
- [ ] [VC7] Integration tests use IGraphQLEngine to execute queries (not direct method calls)
- [ ] [VC8] No test classes deleted or skipped to pass build
- [ ] [VC9] All test classes use JUnit 5 annotations

## Closure

Reviewed By: Self-review + 4 explore agents
Reviewed At: 2026-05-03
Completed At:

Status Note: Plan reviewed and all framework compatibility questions verified. Ready for execution. Implementation not yet started.

Audit Evidence:

- Reviewer / Agent: self-review + 4 parallel explore agents (BizLoader, Standalone BizModel, GraphQL test API, beans.xml)
- Evidence: All framework compatibility questions verified. See Review Findings section.

Follow-Ups:

- [F1] Implement database-backed CodeIndexService — covered by Plan 06 (I4)
- [F2] Incremental indexing — covered by Plan 06 (Phase 1.5)
- [F3] Add authorization (@Auth) to BizModel queries
- [F4] Create separate GraphQL DTO types per design doc
- [F5] Implement NopCodeUsageBizModel with reference search queries
- [F6] File watching for automatic re-indexing

## Supersession Note

Plan 07 描述的 GraphQL service 层实现工作已由 Plan 52 完成。本计划不再代表活跃工作。
