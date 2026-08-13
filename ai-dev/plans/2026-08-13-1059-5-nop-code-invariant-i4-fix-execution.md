# nop-code 不变式闭环 I4 — 修复执行（Cycle 1）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I4. 修复执行
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I4）；确定性输入 `ai-dev/audits/nop-code-invariants/i3-adjudication-matrix.md` §C（10 个工作包定稿）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 前驱 `2026-08-13-0806-4-nop-code-invariant-i3-finding-adjudication.md`（I3，裁决矩阵）；后继 `2026-08-13-1059-6-nop-code-invariant-i5-full-verification.md`（I5，依赖本计划产出）

## Purpose

把 I3 裁决矩阵 §C 定稿的 **10 个修复工作包（WP-1..WP-10）** 全部落地：修复全部 P0/P1 已确认 live defect（38 条 red-list 真违规 + 35 条 Phase 2 P1 悬空发现 = 73 条 in-scope I4 修复项），test-first 验证每条修复，类别清扫穷举同族，棘轮前进门禁 baseline。本计划完成后，I5 全量验证可独立复核「real violations 归零 + 棘轮前进」。

## Current Baseline

> 已对 live repo 核对（2026-08-13）。I3 §C 工作包的 `文件:行` 锚点已抽查确认存在。

- **门禁 baseline（I1 落地）**：`check-nop-code-invariants.mjs` 三 family（query-limit 33 命中 / entity-field-min 24 命中 / delete-contract 0 命中）+ JUnit 幂等表（2 green / 2 red-list 锁）。棘轮 baseline JSON 存于 `ai-dev/audits/nop-code-invariants/baselines/`。
- **I2 red list 真违规（I4 靶点）**：query-limit 19 真违规（P0×13 / P1×6）+ entity-field-min 12 真违规（P0×6 / P1×6）+ idempotency 2 red-list 锁（P0×2）+ 对抗探查新增 5 族（P0×3 / P1×2）= **38 条**。
- **I3 §B 悬空 P1 I4 修复项**：data-consistency 10 + auth 3 + error-handling 子串/吞原子集 + OOM 缓存路径(AR-64) + concurrency 硬化残留(AR-11/42/62/147) + 截断可观测(AR-136/76/61) + orm-schema(AR-51) = **35 条**（与 Phase 1 重叠的 AR-ID 已去重，去重总账见 I3 §E.2）。
- **关键 live 锚点已确认**：
  - `OrmFingerprintStore.java:84`（`selectFieldsByQuery` 无 setLimit）/`:102`（`findAllByQuery` 全 CLOB 实体）/`:137`（逐条 deleteEntity 无 setLimit）— live 核对一致。
  - `nop-code.orm.xml:256`（NopCodeFile.usages 无 cascadeDelete，对比 `:164` NopCodeIndex.usages 有 cascadeDelete）/`:391`（NopCodeSymbol.usages 无 cascadeDelete，对比 `:397` annotations 有 cascadeDelete）— live 核对一致，**AR-149/150 缺陷确认**。
  - `TestNopCodeIndexIdempotencyInvariant`：`KNOWN_NON_IDEMPOTENT = {indexDirectory, indexFile}`，两个 assertThrows red-list 锁存在 — live 核对一致。
- **人工确认门禁（Protected Areas）**：WP-4 AR-149/150 涉及 ORM 模型结构变更（`cascadeDelete` 新增）→ **plan-first / 执行前人工确认**；WP-10 涉及权限模型（@Auth）→ **ask-first / 执行前人工确认**。其余 WP 自动预授权（roadmap Cross-Cutting 授权）。
- **真正剩余 gap**：73 条 P0/P1 I4 修复项全部待执行；门禁 baseline 待棘轮前进；每条修复须 test-first。

## Goals

- 全部 38 条 red-list 真违规修复（real violations 归零）。
- 全部 35 条 Phase 2 P1 悬空 I4 修复项落地（data-consistency / auth / error-handling / 截断可观测 / concurrency 硬化 / OOM 缓存路径）。
- 每条修复附 focused test（test-first：先写验证新行为的测试，再改代码）。
- 门禁棘轮前进：query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0；baseline JSON 经 `--update-baseline` 收紧并人工审阅 diff。
- 类别清扫穷举：修任一 SearchService/删除路径/缓存 getter 必 grep 全部同族点，无「其余类似」省略。

## Non-Goals

- 不沉淀新门禁（INV-05 缓存不可变性 / 截断可观测门禁 / @Auth 门禁 / error-handling 门禁）—— 属 Cycle 2 / I1 候选（I3 §D 已登记），由 I6 收口派生。
- 不修复 P2/P3 后继项（graph-algorithm 12 / language-adapter 10 / config-contract 2 / AR-182 / AR-45）—— I3 已裁定为显式 successor ownership，不在 Cycle 1 I4 范围。
- 不做全量 `./mvnw clean install`（多模块）—— I5 范围；I4 用 `-pl nop-code -am` 模块级验证。
- 不做稳态判定 / 复触发条件登记 —— I6 范围。

## Scope

### In Scope

- I3 §C 的 WP-1..WP-10 全部 P0/P1 修复项（含双违规同位单修）。
- ORM cascadeDelete 新增（AR-149/150，plan-first gate）。
- @Auth 契约一致性修复（AR-146(r8)/155(r10)/170，ask-first gate）。
- 门禁 baseline 棘轮前进（`--update-baseline` + diff 审阅）。
- 幂等表自更新契约（red-list 锁迁移到 IDEMPOTENCE_TABLE）。
- ar-status-matrix 矩阵改判（I3 裁定的 5 条 stale：AR-04/092/180/179/153(r10)）。

### Out Of Scope

- Cycle 2 / I1 新门禁实现（INV-05 / 截断可观测 / @Auth / error-handling 专项门禁）。
- P2/P3 后继修复（图算法 / 语言适配器 / 配置契约 / 性能优化 / 死代码 / 硬编码可配置化）。
- 全量构建验证（I5）与稳态收口（I6）。
- nop-stream / nop-metadata / nop-ai 范围（roadmap Cross-Cutting 范围独立）。

## Execution Plan

> 顺序执行（Phase）。Phase 排序以 I3 §C 工作包排序为基准，按「自动授权 P0 数据正确性优先 → 并发 → 可观测性 → 语义 → error-handling → 需人工确认的安全/权限最后」微调（WP-7 搜索同步提前到 Phase 3 以与 WP-3/4 同属 INV-03 删除/索引完整性簇；WP-10 安全/权限推迟到 Phase 9 因 ask-first 门禁需等待人工确认）。每个 Phase 引用 I3 §C 对应 WP 的 `文件:行` 清单与清扫规则（不重复全文），增补 test-first 验证点与棘轮预期。

### Phase 1 - OOM 族清扫（WP-1 查询上限 + WP-2 字段最小化）

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/`（OrmFingerprintStore.java / impl/CodeIndexService.java / impl/CodeQueryService.java / impl/CodeSearchService.java / impl/CodeCacheManager.java）；门禁 baseline `baselines/baseline-query-limit.json` / `baseline-entity-field-min.json`

- Item Types: `Fix | Proof`

- [x] **WP-1 查询上限**：为 §A.1 全部 19 条真违规消除「无上限全索引加载」；**关键区分**——**需要全量结果的读/删路径**（如 `loadFingerprints`、`loadFileIdMapByIndex`、`buildFilePathCache`、`deleteByIndex`、`rebuildDependencies`、删除辅助查询）**必须用分页循环**（`while + offset + setLimit(BATCH_SIZE) + break-when-batch<size`）耗尽全部行，**不得简单加 `setLimit(N)` 截断**（截断 = 静默丢数据，违反 Minimum Rules #24）；**可接受有界结果的查询**（如统计/展示类）可 cap + 截断可观测 WARN。P0 全索引范围点（OrmFingerprintStore:84,102,137; CodeCacheManager:247; CodeIndexService:505,1404,1521,1531,1602,1818,1882; CodeSearchService:202,329）逐条判定属「全量」还是「有界」并选对应方案；P1 单实体子集点（CodeIndexService:1511,1624; CodeQueryService:147,562,606,611）补 `setLimit`
- [x] **WP-2 字段最小化**：为 §A.2 全部 12 条真违规改投影查询（`selectFieldsByQuery`）；CLOB 全实体加载仅取 ≤4 标量字段点（OrmFingerprintStore:102; CodeIndexService:1818,1882; CodeSearchService:329; CodeQueryService:253,320）改投影；仅存在性检查点（CodeQueryService:792）改 `countByQuery`/exists；其余（CodeIndexService:899; CodeQueryService:673,704,756,762）改投影
- [x] **AR-64 缓存路径投影**：CodeQueryService findImplementations 缓存路径改投影（与 :792 exists-check 同位收敛）
- [x] **ar-status-matrix 改判**：将 I3 裁定的 5 条 stale（AR-04/092/180/179/153(r10)）在 `ai-dev/audits/nop-code-invariants/ar-status-matrix.md` 中改判为 `fixed`/`stale-premise`（附 live 证据，I3 已提供）
- [x] **类别清扫**：grep 全部 `findAllByQuery`/`selectFieldsByQuery` 无 `setLimit` 调用点 + 全实体加载取少量字段点，确认无遗漏（对照 red list §1/§2 全表逐条核对）
- [x] **test-first**：为每个修复点编写/扩展 focused test——分页删除补「循环耗尽语义」测试（删完全部、不留残余）；投影查询补「投影列 = 实际消费列」断言；exists-check 补「结果未被消费」断言

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list` 命中数从 33 下降至 ≤14（剩余 14 全部为已接受有界，逐条可对照 red list §1「已接受有界」行）
- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min --list` 命中数从 24 下降至 ≤12（剩余 12 全部为已接受全实体）
- [x] CLOB 全实体加载点（NopCodeFile.sourceCode）在查询路径中不再出现（grep `findAllByQuery` 后立即访问 `getSourceCode` 的 read 路径，除非确需 CLOB 如 `getFileSourceCode`）
- [x] 分页删除/查询循环的「耗尽语义」有 focused test 覆盖（断言删完/查完全部行，非截断）
- [x] **无静默跳过**：新增 setLimit/投影若遇不支持场景，抛出异常而非静默返回（见 Minimum Rules #24）
- [x] **新功能测试覆盖**：每个新增分页/投影/exists 查询均有对应 focused test（见 Minimum Rules #25）
- [x] 门禁 baseline JSON 经 `--update-baseline` 收紧，diff 已人工审阅（无静默弱化，见 gate-baseline-I1.md 棘轮规则）
- [x] 相关 `docs-for-ai/` 无需更新（service 内部查询优化，不改产品行为契约）：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 幂等性修复（WP-3）

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`（`saveReplacingExisting` :1475）；测试 `TestNopCodeIndexIdempotencyInvariant.java`

- Item Types: `Fix`

- [x] 修复 `saveReplacingExisting`（:1475）的 upsert 幂等性：调查结论——JDBC duplicate-key 在 Nop ORM 层于 `persistInSession` 的 deferred batch flush 时抛出（在 `saveReplacingExisting` 的 try/catch 之外，无法捕获）。改用 **query-first upsert**：先 `session.get()` 载入已存在行（ORM 标记为 MANAGED），拷贝新字段值使 flush 变成 UPDATE 而非 constraint-violating INSERT，从而让 `indexDirectory` / `indexFile` 重试安全
- [x] **自更新契约**：`indexDirectory`/`indexFile` 已从 `KNOWN_NON_IDEMPOTENT` 移入 `IDEMPOTENCE_TABLE`（集合现空），新增 `verifyIndexDirectoryIdempotent` / `verifyIndexFileIdempotent` verify 分支（重试后 file/symbol 计数稳定）；原两 `testKnownRedList_*` assertThrows 锁已转为 verify 方法
- [x] 表完备性门禁（`testTableCompletenessGate`）保持绿（分类一致）

Exit Criteria:

- [x] `KNOWN_NON_IDEMPOTENT` 集合为空（两方法已迁移到 `IDEMPOTENCE_TABLE`）— live 核对 `TestNopCodeIndexIdempotencyInvariant.java:102-103`
- [x] 两方法的新 verify 分支断言「重试两次后记录数不变」（file/symbol 计数稳定）— `verifyIndexDirectoryIdempotent`/`verifyIndexFileIdempotent` assertEquals counts
- [x] `./mvnw test -pl nop-code/nop-code-service -Dtest=TestNopCodeIndexIdempotencyInvariant` 全绿 — 实测 `Tests run: 7, Failures: 0, Errors: 0`（closure audit 复跑）
- [x] **接线验证**：修复的 `saveReplacingExisting` 确实被 `indexDirectory`/`indexFile` 调用链消费（`persistSingleFileInSession`→`saveReplacingExisting`，在索引主路径）
- [x] **无静默跳过**：query-first upsert 为显式语义（load→copy→save），无吞异常空 catch
- [x] `No owner-doc update required`（内部幂等性，不改产品行为契约）
- [x] `ai-dev/logs/` 对应日期条目已更新 — 见 `ai-dev/logs/2026/08-13.md` I4 Phase 2 收口条目

### Phase 3 - 搜索引擎增量同步（WP-7）

Status: planned
Targets: `CodeIndexService.java`（增量路径 `triggerIncrementalIndex`→`persistSingleFileInSession`/`saveFileResultInSession` :1182 addDoc；硬删路径 :1451 removeDocs）

- Item Types: `Fix`

- [ ] 修复增量索引搜索同步：重索引已变更文件时，先 `removeDocs` 该文件旧符号文档再 `addDoc` 新文档（当前增量路径只 addDoc 不 removeDocs，导致幽灵结果）。**操作时序**：在 `persistSingleFileInSession`/`saveFileResultInSession` 写入新数据**之前**，先查询该文件当前 symbol IDs 并 `removeDocs`（清旧文档），再写入新数据并 `addDoc`（加新文档）——确保 removeDocs 作用于旧 symbol IDs（非刚写入的新 IDs），新文档不被误删
- [ ] **类别清扫**：grep searchEngine 全部使用点（仅 4 处：:1182 addDoc / :1451 removeDocs / :535 removeTopic / CodeSearchService:84 search），确认 addDoc/removeDocs 对称性
- [ ] **test-first**：增量重索引测试——修改文件删除某符号后重索引，断言搜索引擎不再返回该符号文档（`searchViaEngine` 结果与 DB 一致）

Exit Criteria:

- [ ] 增量索引路径中 addDoc 前有对应的 removeDocs（先删旧文档再加新文档）
- [ ] 端到端测试覆盖：修改文件（删符号）→ 增量重索引 → `searchViaEngine` 不返回已删符号（从入口到搜索引擎输出完整路径，见 Minimum Rules #22）
- [ ] **接线验证**：removeDocs 调用确实在增量路径执行（不只是硬删路径 :1451）
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 删除路径完整性（WP-4）⚠ plan-first / 执行前人工确认

Status: planned
Targets: `CodeIndexService.java`（`deleteFileRecords` :1440-1472; `deleteIndex` :529-562; `deleteEntitiesByFilter` :1531; `deleteRelationalBySymbolIds` :1521; `OrmFingerprintStore.deleteByIndex` :132）；ORM `nop-code/model/nop-code.orm.xml`（:256 NopCodeFile.usages / :391 NopCodeSymbol.usages）

> **⚠ ORM 结构变更门禁（AGENTS.md Protected Areas: ORM 模型结构 plan-first）**：本 Phase 的 AR-149/150 cascadeDelete 新增涉及 ORM 模型结构变更。执行前须人工确认。若人工确认未通过，本 Phase 的 ORM 部分阻塞，其余删除路径修复（跨文件孤儿清理、删除顺序）可先行。

- Item Types: `Fix`

- [ ] **跨文件孤儿清理（AR-30/66）**：`deleteFileRecords` 删文件 A 时，补清理跨文件引用——① 文件 B 中 `call.calleeId`/`callerId` 指向 A 符号的 call 行（当前 :1457 只删 `fileId=A` 的 call）；② `inheritance.superTypeId` 指向 A 符号的继承行（当前 :1462 只按 `subTypeId` 删）
- [ ] **删除顺序语义（AR-60）**：**先调查** `deleteIndex`（:529-562）当前删除顺序是否已有引用残留（live code 已用 `deleteEntitiesPaged` 按依赖序删子表→父表，疑似已修）。若调查确认无残留 → 标记 stale（记录证据，不修代码）；若确认有残留 → 修正删除顺序。调查结论须记录。
- [ ] **删除辅助查询补 LIMIT（:1521/:1531）**：与 Phase 1 同位收敛（delete-helper 全索引查询补分页）
- [ ] **ORM cascadeDelete 新增（AR-149/150）⚠ plan-first**：`nop-code.orm.xml:256` NopCodeFile.usages 补 `cascadeDelete="true"`；`:391` NopCodeSymbol.usages 补 `cascadeDelete="true"`（对齐 `:164`/`:397` 已有 cascadeDelete 的同族关系）
- [ ] **test-first**：跨文件删除回归测试（删文件 A 后断言无指向 A 符号的跨文件 call.calleeId / inheritance.superTypeId 孤儿）；cascadeDelete 删 NopCodeFile/Symbol 后断言 usages 级联删除

Exit Criteria:

- [ ] 删文件 A 后，跨文件 NopCodeCall（calleeId/callerId 指向 A 符号）与 NopCodeInheritance（superTypeId 指向 A 符号）均被清理（focused test 断言零孤儿）
- [ ] AR-60 调查结论已记录（stale 附 live 证据 / fixed 附测试）
- [ ] **ORM plan-first 证据**：cascadeDelete 新增已获人工确认（记录确认者/日期），或 ORM 部分明确标注「阻塞待确认」且其余删除路径修复已独立完成
- [ ] 若 ORM 变更已执行：`./mvnw install -pl nop-code -am -DskipTests` 成功（ORM 模型变更须重打包生效）；cascadeDelete 级联删除有 focused test
- [ ] **端到端验证**：从 `deleteIndex`/`deleteFileRecords` 入口到跨文件引用清理出口的完整路径有测试覆盖
- [ ] `No owner-doc update required`（删除路径内部完整性，不改公开 API 契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 缓存不可变性（WP-5）

Status: planned
Targets: `CodeCacheManager.java`（:102-116,146-160 缓存 getter 返回内引用）；`SymbolTable.java`（:26,43 byId.values() 返回 live Collection）；`CodeIndexService.java`（:1053,1068 持缓存引用迭代+原地修改）；`CallGraph.java`（:46,52 读缺 synchronized + getForwardMap value 为 live ArrayList）；`CodeSearchService.java`（:333 removeIf 修改传入 List）；`FlowDetector.java`（:163-169 返回缓存 List 引用）

- Item Types: `Fix`

- [ ] **SymbolTable 缓存不可变快照（AR-155/158，P0）**：`CodeCacheManager.getOrRebuildSymbolTable`/`addToSymbolTableCache` 返回不可变快照而非缓存内引用；`SymbolTable.getAll()` 返回防御性拷贝/不可变包装而非 `byId.values()` live 视图；`CodeIndexService.persistSingleFileInSession`（:1068）不再原地修改缓存引用
- [ ] **CallGraph 读方法同步+不可变（AR-145/148，P1）**：`getAllNodeIds`（:46）/`getForwardMap`（:52）补 synchronized（对齐写方法）+ `getForwardMap` value 返回不可变 List（当前 unmodifiableMap 的 value 为 live ArrayList）
- [ ] **filterByLanguage 防御性拷贝（AR-42）**：`CodeSearchService:333` removeIf 不再原地修改传入 List（先拷贝再过滤）
- [ ] **indexLocks 生命周期（AR-62）**：`withIndexLock` finally remove 确认覆盖所有并发场景泄漏
- [ ] **FlowDetector.listFlows 不可变返回（AR-147）**：`:163-169` 返回不可变 List 而非缓存引用
- [ ] **类别清扫**：grep 全部 CodeCacheManager/CallGraph/SymbolTable/FlowDetector getter，统一返回不可变快照/防御性拷贝（对照 coverage-matrix §3 清扫面）
- [ ] **test-first**：单线程确定性探针——`addToSymbolTableCache` 后断言先前返回的快照内容不变；CallGraph `getForwardMap` value 不可 mutate（`unmodifiableList` 断言）

Exit Criteria:

- [ ] 单线程确定性探针：缓存返回对象在 `addToSymbolTableCache`/`addToCallGraphCache` 后内容不变（focused test 断言先前快照与新增后快照独立）
- [ ] CallGraph `getForwardMap` 返回的 value List 不可变（mutate 抛 `UnsupportedOperationException`）
- [ ] **类别清扫穷举**：CodeCacheManager/CallGraph/SymbolTable/FlowDetector 全部公共 getter 已核对，无遗漏返回 live 引用点（清单可追溯）
- [ ] **新功能测试覆盖**：每个改为不可变快照的 getter 有对应 focused test
- [ ] `No owner-doc update required`（缓存内部不变性，不改公开 API 契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 截断可观测性（WP-6）

Status: planned
Targets: `CodeQueryService.java`（:114,252,319,755,762,799 setLimit(MAX_QUERY_RESULTS) 后无 size 检查）；`CodeGraphService.java`（:298）；`CodeCacheManager.java`（:199-204,230-235 setTruncated 存在但无消费方 check isTruncated）

- Item Types: `Fix`

- [ ] **MAX_QUERY_RESULTS 截断 WARN（AR-136/168/177 族）**：6+ 处 `setLimit(MAX_QUERY_RESULTS)` 后补 `if (results.size() == limit) LOG.warn(...)`（当前静默截断，下游基于残缺数据得出错误结论）
- [ ] **cache isTruncated 消费层检查（AR-76/61）**：`SymbolTable.isTruncated()`/`CallGraph.isTruncated()` 标志在**全部消费方**被检查（当前 grep 无任何调用方 check）；穷举所有使用 `getOrRebuildSymbolTable`/`getOrRebuildCallGraph` 返回值的下游路径，确保缓存被截断时消费方感知数据不完整
- [ ] **类别清扫**：grep 全部 `setLimit(MAX_QUERY_RESULTS)`/`setLimit(BATCH_QUERY_LIMIT)` 点，统一加 size==limit 检查 + WARN
- [ ] **test-first**：截断可观测性测试——结果数 == limit 时断言 WARN 已发 + isTruncated 标志被消费层检查

Exit Criteria:

- [ ] 全部 `setLimit(MAX_QUERY_RESULTS)`/`BATCH_QUERY_LIMIT` 点后有 `size==limit` 检查 + WARN（grep 逐条核对）
- [ ] cache isTruncated 标志在全部消费方被 check（grep `isTruncated` 调用方非零，且穷举下游路径无遗漏）
- [ ] 截断可观测性 focused test：limit 命中时 WARN 发出 + 下游消费方感知截断
- [ ] **新功能测试覆盖**：新增的 WARN/检查点有 focused test
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 数据一致性语义修复（WP-8 + AR-51）

Status: planned
Targets: `CodeQueryService.java`（resolveQualifiedNamesToIds AR-01; pathMatchesQualifiedName AR-10/40; getSymbolById AR-41; symbol.extData 写入 AR-59; entityToFileResult AR-63; FlowMembership 嵌套过滤 AR-93; entityToInheritance AR-132/151）；ORM `nop-code.orm.xml`（AR-51 布尔列）

> **执行方式**：以下多条 AR 在 I3 §B.4 仅给出方法名，未附详细 bug 描述。采用 **test-first 调查驱动**：先 grep 定位方法（见 Targets），编写一个**复现当前错误行为**的 focused test（红灯），再修复直到 test 转绿。每条 AR 须先理解 live code 当前行为与期望行为的差异，再写修复。

- Item Types: `Fix`

- [ ] **AR-01**：resolveQualifiedNamesToIds 保留类型层级（QN→ID 映射不破坏继承关系）——先调查当前 QN→ID 映射在何处破坏层级（覆盖/丢失），写复现 test
- [ ] **AR-10/40**：pathMatchesQualifiedName 映射/方法级匹配正确性——先调查映射级与方法级各自的 false positive/negative，写否定 test
- [ ] **AR-41**：getSymbolById 补 indexId 过滤（当前忽略 indexId，跨索引泄漏风险）——写跨索引隔离 test
- [ ] **AR-59**：symbol.extData.filePath 写入完整性（当前未写入）——写写入断言 test
- [ ] **AR-63**：entityToFileResult DTO 映射保留关系（当前丢关系）——先调查丢了哪些关系字段，写保关系 test
- [ ] **AR-93**：FlowMembership 嵌套属性过滤正确性——先调查嵌套过滤何处不正确，写 test
- [ ] **AR-132/151**：entityToInheritance 输出 ID 而非 QN（当前 ID/QN 混淆）——写输出 ID 断言 test
- [ ] **AR-51 布尔列永远 NULL ⚠ plan-first**：**先调查写路径**——确认列永不写入是写路径 bug（service 代码修复）还是 schema 问题（ORM 结构变更 plan-first / 执行前人工确认）。调查结论须记录；若需 ORM 变更按 plan-first gate 处理
- [ ] **test-first**：每条语义修复补对应 focused test（先红后绿），测试须断言具体的期望行为差异（非仅「不报错」）

Exit Criteria:

- [ ] 每条 AR（01/10/40/41/59/63/93/132）有 focused test 验证修复后语义正确
- [ ] getSymbolById 跨索引隔离测试：A 索引查询不返回 B 索引符号
- [ ] entityToInheritance 输出 ID 测试：输出字段为 ID 而非 QN
- [ ] **新功能测试覆盖**：8 条语义修复各有对应 focused test
- [ ] `No owner-doc update required`（查询/映射内部语义，不改公开 API 契约；AR-132/151 输出契约变更须谨慎测试）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - error-handling 正确性清扫（WP-9）

Status: planned
Targets: nop-code-service 全模块（grep `contains`/`startsWith` 误匹配点 + `catch (Exception e) {}` 吞异常点）

- Item Types: `Fix`

- [ ] **子串误匹配穷举（AR-160/162/165/175 + 聚合子串子集）**：grep 全部 `contains`/`startsWith` 匹配点，**逐条分类**为「误匹配 bug」（应改精确匹配/边界正则）vs「故意模糊搜索」（如 `searchBySymbolName`/`searchFullText` 的 fuzzy 查询，应保留 substring）——对照 I2 coverage-matrix 的 AR-IDs（AR-160/162/165/175 及聚合子串子集）逐条定位 `文件:行`，仅改误匹配 bug，每条补否定测试（不应匹配的输入不命中）
- [ ] **静默吞异常穷举（AR-18 + 聚合 catch 子集）**：grep 全部 `catch (Exception e) {}` 空 catch 块，改为抛出或记录（失败可见性）
- [ ] **类别清扫**：不得用「其余类似」省略（I3 计划 Phase 3 Anti-Slacking 要求）；逐 AR-ID 落实子集计数

Exit Criteria:

- [ ] 全部 `contains`/`startsWith` 匹配点已逐条核对（清单可追溯），误匹配点已改精确匹配 + 否定测试
- [ ] 全部空 catch 块已逐条核对（清单可追溯），已改为抛出/记录
- [ ] **无静默跳过**：修复后的 catch 路径抛出或记录，无空方法体（见 Minimum Rules #24）
- [ ] **新功能测试覆盖**：每个修复点有否定测试（误匹配）或失败可见性测试
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 9 - 安全/权限契约 ⚠ ask-first / 执行前人工确认（WP-10）

Status: planned
Targets: nop-code BizModel（@Auth 注解）；`action-auth.xml`（权限声明）

> **⚠ 权限模型门禁（AGENTS.md Protected Areas: 权限/认证模型 ask-first）**：本 Phase 涉及 @Auth 权限注解与 action-auth.xml 一致性。执行前须人工确认。

- Item Types: `Fix`

- [ ] **AR-146(r8)**：8 个空 BizModel 补 `@Auth` 注解——先 grep 全部 `*BizModel.java` 中无 `@Auth` 的类，列出清单后逐个补
- [ ] **AR-155(r10)**：只读查询改用 permissions 而非 roles（权限模型契约一致性）
- [ ] **AR-170**：`@Auth` permissions 与 `action-auth.xml` 权限声明匹配（前后端权限契约一致）
- [ ] **test-first**：@Auth 契约一致性测试（BizModel action 与 action-auth.xml 权限声明匹配）

Exit Criteria:

- [ ] **ask-first 证据**：权限变更已获人工确认（记录确认者/日期），或明确标注「阻塞待确认」
- [ ] 8 个空 BizModel 均有 `@Auth` 注解（grep 核对）
- [ ] 只读查询使用 permissions（非 roles）
- [ ] `@Auth` permissions 与 `action-auth.xml` 声明匹配（契约一致性测试通过）
- [ ] **新功能测试覆盖**：@Auth 契约一致性测试
- [ ] `No owner-doc update required`（权限内部契约，若改公开权限语义则须更新 owner doc 并记录）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 38 条 red-list 真违规修复（query-limit 19 + entity-field-min 12 + idempotency 2 + 对抗探查 5 = 38，逐条可追溯）
- [ ] 全部 35 条 Phase 2 P1 悬空 I4 修复项落地（data-consistency 10 + auth 3 + error-handling 子串/吞原子集 + OOM 缓存路径 + concurrency 硬化 + 截断可观测 + orm-schema AR-51）
- [ ] 门禁棘轮前进：query-limit 33→≤14 / entity-field-min 24→≤12 / idempotency red-list 锁 2→0
- [ ] 门禁 baseline JSON 经 `--update-baseline` 收紧，diff 已人工审阅（无静默弱化）
- [ ] `KNOWN_NON_IDEMPOTENT` 集合为空（两方法迁移到 IDEMPOTENCE_TABLE）
- [ ] ar-status-matrix 矩阵改判完成（I3 裁定的 5 条 stale：AR-04/092/180/179/153(r10) 改判 fixed/stale-premise）
- [ ] ORM plan-first（AR-149/150）与 ask-first（WP-10）已获人工确认或明确标注阻塞
- [ ] 不存在被静默降级到 deferred 的 in-scope P0/P1 live defect
- [ ] 受影响的 owner docs 已同步或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）修复的调用链在运行时确实连通（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw test -pl nop-code -am -T 1C`（模块级全绿）
- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list` 命中 ≤14
- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min --list` 命中 ≤12
- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract --list` 命中 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs nop-code/nop-code-service/src/main --severity high` 退出码 0（扫描范围限定 nop-code-service——I4 改动面；nop-code-core 的 5 条 `UnsupportedOperationException` 是 Rule #24 合规实现，非 I4 范围，不在本 gate）
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

### AR-149/150 ORM cascadeDelete（若 plan-first 人工确认未通过）

- Classification: `阻塞待确认（ORM 结构变更 Protected Area）`
- Why Not Blocking Closure: 若人工确认要求延迟，cascadeDelete 部分可独立阻塞；其余删除路径修复（跨文件孤儿、删除顺序）已在本 Phase 4 独立完成。cascadeDelete 缺失有降级路径（service 层显式删除 usages）。
- Successor Required: `yes`（若阻塞）
- Successor Path: 人工确认通过后在 successor plan 补 cascadeDelete

> AR-51 已移入 Phase 7（调查驱动，先查写路径再裁定 service 修复或 ORM plan-first）。不再在此 deferred。

## Non-Blocking Follow-ups

- Cycle 2 / I1 新门禁沉淀（INV-05 缓存不可变性 / 截断可观测门禁 / @Auth 门禁 / error-handling 门禁）—— I3 §D 已登记候选，I6 收口派生
- P2/P3 后继修复（graph-algorithm 12 / language-adapter 10 / config-contract 2 / AR-182 / AR-45）—— I3 已裁定为显式 successor ownership

## Closure

Status Note: （待执行完成后填写）
Completed: 

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit 填写）
- Audit Session: 
- Evidence: （待填写）
