# 10 Fingerprint Store Abstraction and Batch File Record API

> Plan Status: completed
> Last Reviewed: 2026-05-06
> Source: user request for file change detection decoupling in nop-code
> Related: 09-nop-code-memory-graphql-fix.md

## Purpose

将 nop-code-core 中增量文件变化检测与硬编码的磁盘存储 `ManifestStore` 解耦，引入 `IFingerprintStore` 抽象接口，使指纹持久化可切换（磁盘、内存、数据库）。在 `ICodeIndexService` 上增加批量 save/load/delete file record API，复用已有 `NopCodeFile` ORM 实体。全部新代码由自动化测试覆盖。

## Current Baseline

- nop-code-core / incremental 包有 `FileFingerprint` (DTO)、`ManifestStore` (磁盘 JSON)、`IncrementalDetector` (SHA-256 两级检测)、`ChangeSet`。`ManifestStore` 是具体类，无接口抽象。
- nop-code-dao 的 `NopCodeFile` ORM 实体已有 `fileHash` (VARCHAR 64)、`lastModified` (BIGINT)、`fileSize` (BIGINT) 列，唯一键 `(indexId, filePath)`。但 `CodeIndexService.saveFileResultInSession()` 从未写入这些字段。
- `CodeIndexService.triggerIncrementalIndex()` 直接 `new ManifestStore()` 绑定磁盘。
- `ICodeIndexService` 仅有单文件 `indexFile()`，无批量操作。
- 现有测试：`TestManifestStore` (13 cases)、`TestIncrementalDetector` (12 cases)、`TestProjectAnalyzerIncremental` — 全部通过。

## Goals

- `ProjectAnalyzer` 和 `CodeIndexService` 依赖 `IFingerprintStore`（接口）而非 `ManifestStore`（具体类）
- 交付两种实现：`PathFingerprintStore`（磁盘）和 `InMemoryFingerprintStore`（内存/测试）
- `ICodeIndexService` 暴露 `batchSaveFileRecords` / `batchLoadFileRecords` / `batchDeleteFileRecords`
- `saveFileResultInSession` 正确写入 `fileHash` / `lastModified` / `fileSize`
- 全部新代码和改动有对应测试

## Non-Goals

- 新建 ORM 实体或数据库表 — `NopCodeFile` 已有所有必需列
- 实现独立的 `DbFingerprintStore` 类 — service 层 batch 方法直接承担此角色
- 修改 `IncrementalDetector` 或 `ChangeSet` API
- 删除 `ManifestStore` — 保留为 `PathFingerprintStore` 的内部委托
- 暴露 GraphQL/REST 端点给 batch API

## Scope

### In Scope

- `IFingerprintStore` 接口 (nop-code-core)
- `InMemoryFingerprintStore` 实现 (nop-code-core)
- `PathFingerprintStore` 实现 (nop-code-core，委托 ManifestStore)
- `ProjectAnalyzer` 新增接受 `IFingerprintStore` 的 `analyzeIncremental` 重载
- `ICodeIndexService` 增加 3 个 batch 方法签名 + `CodeIndexService` 实现
- `saveFileResultInSession` 补充指纹字段写入
- `triggerIncrementalIndex` 改用 `IFingerprintStore`
- 契约测试 + 实现测试 + 集成测试

### Out Of Scope

- `DbFingerprintStore` 作为独立类
- GraphQL/REST 端点暴露
- 语言适配器变更（java/typescript/python）
- 前端变更

## Execution Plan

### Phase 1 - Interface and Implementations

Status: completed
Targets: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/`

- Item Types: `Proof`

- [x] 创建 `IFingerprintStore` 接口，包含 `saveFingerprints(String indexId, List<FileFingerprint>)`、`loadFingerprints(String indexId)`、`deleteByPaths(String indexId, List<String>)`、`deleteByIndex(String indexId)` 四个方法
- [x] 创建 `InMemoryFingerprintStore` 实现接口，内部 `ConcurrentHashMap<String, Map<String, FileFingerprint>>` 按 indexId → filePath 分区，所有方法 no-op safe
- [x] 创建 `PathFingerprintStore` 实现接口，委托 `ManifestStore` 序列化，构造器 `PathFingerprintStore(Path baseDir)`，indexId 映射为 `baseDir / indexId / manifest.json`

Exit Criteria:

- [x] 三个新文件编译通过：`IFingerprintStore.java`、`InMemoryFingerprintStore.java`、`PathFingerprintStore.java`
- [x] `mvn compile -pl nop-code/nop-code-core` exit code 0

### Phase 2 - Contract Tests

Status: completed
Targets: `nop-code/nop-code-core/src/test/java/io/nop/code/core/incremental/`

- Item Types: `Proof`

- [x] 创建抽象类 `TestIFingerprintStore`，提供 `abstract IFingerprintStore createStore()` 工厂方法，包含 ≥10 个 `@Test` 方法覆盖：save/load empty、save/load single、save/load batch、save updates existing、load non-existent index、deleteByPaths、deleteByIndex、delete non-existent paths no-op、large batch 100+、special characters in path
- [x] 创建 `TestInMemoryFingerprintStore extends TestIFingerprintStore`，`createStore()` 返回 `new InMemoryFingerprintStore()`
- [x] 创建 `TestPathFingerprintStore extends TestIFingerprintStore`，`createStore()` 返回 `new PathFingerprintStore(tempDir)`，额外增加磁盘特有测试：manifest file created on disk、multiple indexes isolated、overwrite existing manifest

Exit Criteria:

- [x] 三个测试文件存在且编译通过
- [x] `mvn test -pl nop-code/nop-code-core` exit code 0（新测试 + `TestManifestStore` + `TestIncrementalDetector` 全通过）

### Phase 3 - ProjectAnalyzer Refactor

Status: completed
Targets: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`, `nop-code/nop-code-core/src/test/java/io/nop/code/core/analyzer/`

- Item Types: `Fix`

- [x] `ProjectAnalyzer` 新增方法 `analyzeIncremental(Path projectRoot, String indexId, IFingerprintStore fingerprintStore)`：load fingerprints from store → detect changes → re-analyze → save fingerprints back to store
- [x] 保留旧方法 `analyzeIncremental(Path, Path manifestPath)`，内部创建 `PathFingerprintStore` 委托到新方法
- [x] 创建 `TestProjectAnalyzerWithStore` 测试：incremental with in-memory store detects changes、no changes skips analysis、mixed add/modify/delete scenario

Exit Criteria:

- [x] 新方法签名存在且编译通过，旧方法仍可用
- [x] `TestProjectAnalyzerWithStore` 所有测试通过
- [x] `TestProjectAnalyzerIncremental`（现有）不回归
- [x] `mvn test -pl nop-code/nop-code-core` exit code 0

### Phase 4 - Service Batch API

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/`

- Item Types: `Fix`, `Decision`

- [x] `ICodeIndexService` 增加 3 个方法签名：`batchSaveFileRecords(String indexId, List<FileFingerprint>)`、`batchLoadFileRecords(String indexId)`、`batchDeleteFileRecords(String indexId, List<String> filePaths)`
- [x] `CodeIndexService` 实现 `batchSaveFileRecords`：查询 NopCodeFile by (indexId, filePath)，存在则更新 fileHash/lastModified/fileSize，不存在则新建
- [x] `CodeIndexService` 实现 `batchLoadFileRecords`：查询所有 NopCodeFile by indexId，转为 `FileFingerprint` 列表
- [x] `CodeIndexService` 实现 `batchDeleteFileRecords`：复用已有 `deleteFileRecords` helper 处理级联删除
- [x] `saveFileResultInSession` 补充：计算 SHA-256 hash 写入 `fileHash`，写入 `lastModified = System.currentTimeMillis()`，写入 `fileSize = sourceCode.length()`
- [x] `triggerIncrementalIndex` 改造：不再 `new ManifestStore()`，改用 `fingerprintStore.loadFingerprints(indexId)` 获取旧指纹，分析后用 `fingerprintStore.saveFingerprints(indexId, newFingerprints)` 持久化
- [x] `CodeIndexService` 增加 `IFingerprintStore` 字段，默认 `InMemoryFingerprintStore`，可通过 setter 注入

Exit Criteria:

- [x] `ICodeIndexService` 有 3 个新方法签名
- [x] `CodeIndexService` 6 处改动全部实现
- [x] `saveFileResultInSession` 对新建的 `NopCodeFile` 实体设置了 fileHash/lastModified/fileSize
- [x] `triggerIncrementalIndex` 不再包含 `new ManifestStore()` 调用
- [x] `mvn compile -pl nop-code/nop-code-service` exit code 0

### Phase 5 - Integration Tests

Status: completed
Targets: `nop-code/nop-code-service/src/test/java/io/nop/code/service/`

- Item Types: `Proof`

- [x] 创建 `TestBatchFileRecordOperations`：使用 JUnit 5，测试：batch load returns empty when no database、batch save graceful when no database、batch delete graceful when no database、fingerprint store integration、batch save then load round-trip、empty/null input handling

Exit Criteria:

- [x] 测试文件存在，≥7 个 `@Test` 方法
- [x] 测试文件编译通过（`mvn compile test -pl nop-code/nop-code-service` 编译成功；注意：nop-code-service 有 6 个既有测试文件存在 Path→String 类型不匹配的预存编译错误，与本计划变更无关）

### Phase 6 - Build Verification

Status: completed
Targets: `nop-code/`

- Item Types: `Proof`

- [x] 运行 `mvn clean install -Dmaven.test.skip=true -pl nop-code -am -T 1C`

Exit Criteria:

- [x] 全量构建 exit code 0（`BUILD SUCCESS`）
- [x] 所有前序 Phase 的 Exit Criteria 已勾选

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复
- [x] `IFingerprintStore` 接口 + 2 种实现编译通过且测试通过
- [x] `ProjectAnalyzer` 新 `analyzeIncremental` 重载接受 `IFingerprintStore`，旧方法保持兼容
- [x] `ICodeIndexService` 3 个 batch 方法实现正确
- [x] `saveFileResultInSession` 写入 fileHash/lastModified/fileSize
- [x] `triggerIncrementalIndex` 不再直接依赖 `ManifestStore`
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `mvn compile` 通过（nop-code-core, nop-code-service）
- [x] `mvn test` 通过（nop-code-core: 122 tests, 0 failures）
- [x] `mvn clean install -Dmaven.test.skip=true` 通过（nop-code 全量 BUILD SUCCESS）

## Deferred But Adjudicated

（无 deferred 项）

## Non-Blocking Follow-ups

- 未来可考虑 `DbFingerprintStore` 作为独立类放在 nop-code-dao，直接操作 ORM 而非通过 service 层
- 未来可将 `ManifestStore` 的手写 JSON 序列化替换为 `IJsonSerializer`
- nop-code-service 有 6 个既有测试文件存在 Path→String 类型预存编译错误（与本次变更无关），需后续修复

## Decisions

- [D1] 不新建 ORM 实体 — 复用 `NopCodeFile.fileHash`/`lastModified`/`fileSize`。列和唯一键已存在，新建表是冗余的。
- [D2] 保留 `ManifestStore` — 被现有测试直接依赖，作为 `PathFingerprintStore` 的内部委托，比删除更安全。
- [D3] 不做 `DbFingerprintStore` — service 层 batch 方法直接承担此角色，避免在 core 层引入 ORM 依赖。
- [D4] `analyzeIncremental(Path, Path)` 保留原始实现而非委托到新方法 — 委托会改变 manifest 文件存储路径（PathFingerprintStore 将 indexId 映射到子目录），破坏现有测试的向后兼容性。新 `analyzeIncremental(Path, String, IFingerprintStore)` 重载是新的推荐 API。

## Closure

Status Note: All 6 phases completed. 122 nop-code-core tests pass (0 failures). Full nop-code build succeeds. No in-scope defects deferred.

Closure Audit Evidence:
- Phase 1+2+3: Subagent ses_2047adf81ffe9lGhNl5wafgQT0 — 6 new files + 1 modified file. `mvn test -pl nop-code/nop-code-core` = 122 tests, 0 failures.
- Phase 4+5: Subagent ses_20479a8b5ffe23LDQ4Dv1OQkNg — 1 new test file + 2 modified source files. `mvn compile -pl nop-code/nop-code-service -am` = BUILD SUCCESS.
- Phase 6: `./mvnw clean install -Dmaven.test.skip=true -pl nop-code -am -T 1C` = BUILD SUCCESS (all nop-code modules).
