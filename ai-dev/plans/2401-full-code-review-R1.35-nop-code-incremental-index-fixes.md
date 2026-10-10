# 2401 full-code-review R1.35 — nop-code 增量索引修复批（P1-6.7-009/010/011 + P1-7.4-001 状态勘正）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.35；findings P1-6.7-009/010/011（`6.7-check-p1-batch-c.md:107-129`）+ P1-7.4-001（`7.4-nop-code-gated-incremental-audit.md`）
> Related: plan 2360（7.4 增量巡检）、plan `nop-code/28-n7-1-llm-semantic-edge.md`（P1-7.4-001 载体）、`ai-dev/backlog/nop-code-feature-completion-roadmap.md` N7.1
> Owner Doc: `docs-for-ai/03-modules/nop-code.md`
> Plan Review: agent_0526467c 首轮对抗审查 **NOT READY**（B1 fileResult==null 分支遗漏/B2 hollow scan 模块名空扫/M1 状态 token 不可观测/M2 delete-before-analyze 窗口未裁定/M3 注入接缝未指明/M4 Rule #17 文档项外加/M5 plan 28 未对账 + m1-m6）→ 全修；agent_a9882851 聚焦复审 **READY TO EXECUTE**（0 Blocker/0 Major，8 Minor 全修：`:48→:51`、`:143→:140`、理由列→Work Item 文本、successor path 去占位、deleteEntitiesByFilter 多 filter 重载、注释漂移项、CodeLanguage map key 约束、N3.1-s 收窄理由勘正）。

## Purpose

收口 nop-code 增量索引三条 P1 与一条台账误导：①`analyzeChanges` 以持久化的 `file:` 前缀 rootPath 作 git 工作目录 → 进程必败、异常被折叠成「无变更」；②`triggerIncrementalIndex` 先删旧记录、分析失败文件不再重分析却被写入新指纹 → 失败文件从索引永久消失；③`deleteFileRecords` 的依赖边删除缺 `indexId` 作用域 → 双索引覆盖同一仓库时跨索引互删；④`LlmSemanticEdgeExtractor` 生产不可达（roadmap N7.1 `done` 与 plan 28 `draft` 脱钩、G1-b 接线静默缺失）——本计划按 audit 预授权路径做状态双向勘正与显式 successor 归属。

## Current Baseline

- **P1-6.7-009**（live）：`CodeIndexService.analyzeChanges:2593-2613` 取 `indexEntity.getRootPath()` 作 `workingDirectory`；rootPath 由 `persistInSession:1212/:1221` 经 `resolveVfsPath:2941-2959` 持久化，通常为 `file:/abs/path` 形态（`ensureIndexEntity:1492` 与 null 兜底可存 `"/"`，故读取侧规范化必须对普通路径/`"/"` 无副作用）。`ChangeAnalyzer.parseGitDiff:131-221`（nop-code-flow）在该路径下 `pb.directory(new File("file:/..."))` 不存在 → `start()` 抛 IOException → 仅 `LOG.warn` + 返回空 `DiffResult(truncated=false)`；且未检查 `process.exitValue()`——非零退出同样静默折叠为「无变更」。
- **P1-6.7-010**（live）：`CodeIndexService.triggerIncrementalIndex:1117-1118` **先**删 deleted+changed 记录，`1131-1156` per-file 分析 `catch(Exception)→LOG.warn`（`:1135` analyzer null、`:1138` resource null 亦静默 `continue`；`:1144` `analyze` 返回 null 亦被静默丢弃），`:1177` 对**全部** current resources 计算指纹并按 `:1180` 保存（`OrmFingerprintStore.saveFingerprints` 为 upsert 到 `NopCodeFile` 行，指纹与文件行同源：删行即删指纹）——失败/null 文件被标记为已同步，下轮判 unchanged 永不重试；**且旧记录已在分析前删除，若后续不再触发增量则索引数据直接丢失**（delete-before-analyze 窗口）。该分支的 null 结果在半数实现里可达，须与异常/无 analyzer 同列。
- **P1-6.7-011**（live）：`CodeIndexService.deleteFileRecords:2366` `deleteEntitiesByFilter(NopCodeDependency.class, "sourceFilePath", filePath)` 无 indexId 过滤；同方法 `:2363-2365` 的 call/symbol/usage 删除（按 `generateFileId(indexId, filePath)`）与 `:2383-2388` 的 file 删除均已带索引作用域；写侧 `:1985` 带 indexId。`NopCodeDependency.indexId` 为 mandatory 列（`nop-code.orm.xml:766`）。
- **P1-7.4-001**（live）：`CodeIndexService.discoverSemanticExtractors:285-291` 硬编码 3 个确定性提取器；`LlmSemanticEdgeExtractor`（`semantic/LlmSemanticEdgeExtractor.java`）存在且有单测（`TestLlmSemanticEdgeExtractor` 7 用例），但无 bean、无调用点；`ProjectAnalyzer.runSemanticExtractors:289-290` 与 `runFileResultExtractors:306-307` 对 `requiresLlm()` 无条件跳过；`LlmSemanticEdgeExtractor.requiresLlm()=true`。`ai-dev/plans/nop-code/28-n7-1-llm-semantic-edge.md` 仍 `draft`（Phase 1 的 G1-b「service 接线」未勾选，而 G1/G2/G3 类与测试已 live），而 `nop-code-feature-completion-roadmap.md:119` N7.1 标 `done`、`:51` 汇总「done 34 · deferred 5 · todo 0」——台账误导 + in-scope 项静默缺失。
- **接线可行性**：`nop-code-service` 现依赖 `nop-ai-api`/`nop-ai-rag`（pom），但仓内无 `IChatService` 功能 bean（nop-ai-core 才有 `ChatServiceImpl`）；plan 28 的 G1-b「可选注入提取器」需先产出 bean 定义与预算/缓存配置面——属新能力装配（Feature），非本批缺陷修复面。
- **测试基建**：`nop-code-flow/TestChangeAnalyzer:117-130`（parseGitDiff 无效 ref 用例）；`nop-code-service/TestIncrementalIndexWithDb`（`@NopTestConfig(localDb=true)` 全装配增量测试）、`TestIncrementalDependencyPropagation`、`TestIncrementalCalleeResolution`、`TestDeletePathIntegrity`、`TestDependencyPersistence` 在位。

## Goals

- `analyzeChanges` 把持久化的 vfs rootPath 规范化为本地绝对路径后再交给 git；git 进程失败（目录不存在/非零退出/IOException）**不再静默折叠为「无变更」**——至少置 `truncated=true` 并 WARN。
- 增量索引对分析失败/无 analyzer/资源缺失的文件**不写入新指纹**（保留旧指纹或保持缺失），使下轮增量重新检出并重试；失败不再被指纹机制掩盖。
- `deleteFileRecords` 的依赖边删除限定 `indexId`；核对同类删除调用点作用域。
- P1-7.4-001 状态双向勘正：roadmap N7.1 `done`→反映 G1-b 缺失；plan 28 G1-b 显式移入 successor 归属（Feature/装配面），不再静默缺失。
- 判别测试：三条 P1 各有可判别回归（stash/失败注入）；nop-code 受影响模块测试全绿。
- owner doc `docs-for-ai/03-modules/nop-code.md` 同步两条契约（rootPath 规范化、失败文件指纹语义）。

## Non-Goals

- `LlmSemanticEdgeExtractor` 的生产接线实现（Feature 装配：`IChatService` bean/预算配置/边持久化）——移入 successor（见 Deferred But Adjudicated），本计划只做状态勘正。
- nop-code-core `ProjectAnalyzer` 的 `requiresLlm` 语义改动（plan 28 明确 v1 不改 core）。
- P2-6.7-001..004（P2 裁决台账通道）；`CodeIndexService` 六域拆分（P2-4.3-001）。

## Scope

### In Scope

- `nop-code/nop-code-service/.../impl/CodeIndexService.java`：rootPath 规范化、`triggerIncrementalIndex` 失败文件指纹语义、`deleteFileRecords` indexId 作用域
- `nop-code/nop-code-flow/.../ChangeAnalyzer.java`：git 进程失败 fail-loud（truncated/WARN）与工作目录存在性校验
- `nop-code/nop-code-service/src/test/**` + `nop-code-flow` 测试：判别回归
- `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N7.1 行；`ai-dev/plans/nop-code/28-n7-1-llm-semantic-edge.md`；`docs-for-ai/03-modules/nop-code.md`

### Out Of Scope

- `LlmSemanticEdgeExtractor`/`LlmEdgeBudget`/`LlmEdgeCache` 类实现（已存在且单测在位）；nop-code-service beans 装配；nop-code-core 改动

## Execution Plan

### Phase 1 - P1-6.7-009 analyzeChanges rootPath 规范化 + git 失败 fail-loud

Status: completed
Targets: `CodeIndexService.java`、`ChangeAnalyzer.java`、`nop-code-flow/src/test/**`、`nop-code-service/src/test/**`

- Item Types: `Fix`

- [x] `CodeIndexService`：新增 vfs→本地路径规范化（剥 `file:` 前缀；剥后形如 `/C:/...` 时去掉盘符前导 `/`；`"/"`/普通路径幂等无操作），`analyzeChanges` 取 `rootPath` 后规范化再传 `workingDirectory`；持久化格式保持不变（仅读取侧规范化）
- [x] `ChangeAnalyzer.parseGitDiff`：`workingDirectory` 非空时先校验存在且为目录，否则 WARN + 返回 `truncated=true`（不启动进程）；`process.waitFor(30s)` 返回 true 后再读 `exitValue()`（**false 时不得调 `exitValue()`，否则 IllegalThreadStateException**）——非零置 `truncated=true` 并 WARN；IOException/Interrupted 分支同样 `truncated=true`
- [x] 测试：①`analyzeChanges` 传入 `file:` 形态 rootPath 时 git 实际以本地路径运行（可用真实临时 git 仓库或断言工作目录解析结果）；②`ChangeAnalyzer` 工作目录不存在/非零退出 → `truncated=true`；③IOException 分支（不可创建目录/无法启动 git）→ `truncated=true`（既有无效 ref 用例断言保持）
- [x] `docs-for-ai/03-modules/nop-code.md`：本 Phase 内的 owner-doc 更新项（rootPath 规范化 + git 失败 `truncated` 语义）
- [x] **判别验证（stash 腿）**：修复前新用例 FAIL（truncated=false / 工作目录含 `file:`），恢复后 PASS
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-code/nop-code-flow,nop-code/nop-code-service -am -T 1C`

Exit Criteria:

- [x] rootPath 规范化后 git 使用本地绝对路径（new 用例在案）
- [x] git 失败（目录不存在/非零退出/IOException）显式 `truncated=true`，不静默「无变更」
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] Phase 内 owner-doc 更新项已落地
- [x] nop-code-flow + nop-code-service 测试全绿

执行证据（Phase 1）：
- `TestChangeAnalyzer` 7/7（新增 2：`testParseGitDiffProcessCleanupOnInvalidRefs` 加 `truncated=true` 断言、`testParseGitDiffMissingWorkingDirectoryIsTruncated`）；`TestAnalyzeChangesFileRootPath` 真实 git 仓库端到端 1/1；`TestCodeIndexServiceLocalWorkingDirectory` 4/4。
- **判别 stash 腿**：临时把 `workingDirectory = toLocalWorkingDirectory(...)` 还原为 `indexEntity.getRootPath()` → `TestAnalyzeChangesFileRootPath` 恰 1 FAIL，日志逐字 `Git diff working directory does not exist or is not a directory: file:/var/folders/.../repo` + `expected: <false> but was: <true>`（`_tmp/r135-stash-009.log`）；恢复后全绿。

### Phase 2 - P1-6.7-010 失败文件数据/指纹保留（删写重排）+ P1-6.7-011 依赖删除 indexId 作用域

Status: completed
Targets: `CodeIndexService.java`、`nop-code-service/src/test/**`

- Item Types: `Fix`

- [x] `triggerIncrementalIndex` 结构重排（消除 delete-before-analyze 数据丢失窗口，`Maintainer note`：`snapshotDependentCalls:1114`/`computeAffectedFiles:1100` 仍在删除前不变）：先对全部 changedFiles 做分析并收集结果，**此阶段不删旧记录**；`failedFiles` = analyzer null ∪ resource null ∪ `analyze` 抛异常 ∪ **`analyze` 返回 null**（四类，对应 `:1135/:1138/:1153/:1144`）
- [x] 分析完成后：`deleteFileRecords(indexId, deletedFiles)`；`deleteFileRecords(indexId, changedFiles − failedFiles)`（失败文件旧记录**不删**，保留符号/边/指纹 → 无数据丢失且下轮必然重新检出）；随后持久化 `changedResults`
- [x] **N3.1-s 快照/重绑作用域随重排收窄**：`snapshotSymbolQualifiedNames`/`snapshotDependentCalls` 的输入与 `excludeFileIds`/`resolveReanalyzedCalls` 的 `excludeFilePaths` 改为「successfulChangedFiles ∪ deletedFiles」——失败文件符号与边未删，若仍纳入快照会丢失失败文件指向「本轮被重分析符号」的旧出边（`restoreDependentCalls` 仅按快照重建，失败文件未被重分析、其符号/边不应进入重建集合）；`computeAffectedFiles` 的 seed 仍保持 changedFiles ∪ deletedFiles（仅影响上报面，不改变处理面）。既有 `TestIncrementalCalleeResolution`/`TestIncrementalDependencyPropagation` 为 N3.1-s 回归护栏，必须保持全绿
- [x] 注释漂移修正：`CodeIndexService:364`/`:400` 的「mirrors triggerIncrementalIndex's delete-before-reindex pattern」随删写重排已失真，改为描述「先分析后删（失败文件不删）后再持久化」的新形态
- [x] 指纹：新指纹集合只由**成功分析**的 resources 计算（`mappedResources` 扣除 failedFiles）；失败文件不写新指纹（`NopCodeFile` 旧行/fingerprint 原样保留，下轮按 modified 检出）；无旧行者保持缺失（下轮按 added 检出）
- [x] 返回/日志口径：`triggerIncrementalIndex` 返回**成功持久化数**（`changedResults.size()`；无失败时与旧 `changedFiles.size()` 等值，既有 happy-path 断言不受影响，`TestCacheInvalidationAlignment:140` 的 `assertEquals(0, changed)` 语义保持）；失败文件逐个 WARN、汇总一条 INFO（`persisted=N failed=M`），不静默
- [x] `deleteFileRecords:2366`：`NopCodeDependency` 删除增 `indexId` 过滤——注意 `deleteEntitiesByFilter(Class,String,String)`（:2460）现为单 filter 签名，需新增多 filter 重载（或改调 `findAllByQuery`+`batchDeleteEntities`，同 :2385 形态）；逐一核对该方法及其它删除 helper 的索引作用域——已确认 call/symbol/usage 按 `generateFileId(indexId,path)`、relational 按 UUID symbolId 天然隔离，唯此点缺口
- [x] 测试（localDb 装配，沿 `TestIncrementalIndexWithDb` 形态；**注入接缝**：`((CodeIndexService) codeIndexService).setRegistry(fresh)` 后 `fresh.registerAdapter(fakeAdapter)` 覆盖目标扩展名——`ILanguageAdapter`/`ICodeFileAnalyzer` 假件可在首次成功索引后切换为「抛异常」或「返回 null」；`setRegistry:258-269` 会重注册 BeanContainer adapter，故假件须在其后注册，且**必须复用目标 adapter 的 `CodeLanguage` 作为 map key**（`registerAdapter` 按语言覆盖；`getAnalyzer` 按 HashMap value 迭代顺序命中，换语言不保证胜出））：
  - [x] ①「首次成功索引 → 切换为抛异常 analyzer → 改文件 → 增量」：该文件旧符号/指纹保留、下轮增量仍报 changed 并重试；②同型用例覆盖 `analyze` 返回 null 分支
  - [x] ③双索引写入同 filePath 依赖后，经公开入口 `batchDeleteFileRecords(:2795)` 触发索引 A 删除，索引 B 的 `NopCodeDependency` 行存活
- [x] `docs-for-ai/03-modules/nop-code.md`：本 Phase 内的 owner-doc 更新项（失败文件指纹/数据保留语义 + 依赖删除索引作用域）
- [x] **判别验证（stash 腿）**：修复前①②③ FAIL（指纹被覆盖/依赖被跨索引删除），恢复后 PASS
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] `./mvnw test -pl nop-code/nop-code-service -am -T 1C`

Exit Criteria:

- [x] 失败/null 文件不被标记为已同步且旧记录不被删（指纹语义测试在案）
- [x] 依赖边删除仅作用于本索引（双索引隔离测试经公开入口在案）
- [x] 无失败时返回计数与旧行为等值（既有断言全绿）
- [x] 判别 stash 腿 FAIL→PASS 记录在 plan
- [x] Phase 内 owner-doc 更新项已落地
- [x] nop-code-service 测试全绿

执行证据（Phase 2）：
- `TestIncrementalFailedFileRetention` 2/2（throw 与 null 两分支：失败文件保留旧指纹 + 下轮重试成功）；`TestDeleteFileRecordsIndexScoping` 1/1（经 `batchDeleteFileRecords` 公开入口，索引 B 依赖行存活）。
- **判别 stash 腿**：①注释掉 `newFingerprints.removeIf(...)`（等价于不排除失败文件指纹）→ 2 用例 FAIL，逐字 `the failed file must keep its previous fingerprint ... expected: <3bfc...> but was: <841f...>`（`_tmp/r135-stash-010.log`）；②把 null 分支还原为静默丢弃 → 恰 null 用例 1 FAIL（`_tmp/r135-stash-010b.log`）；③还原无作用域的 `deleteEntitiesByFilter(NopCodeDependency, "sourceFilePath", filePath)` → 恰 1 FAIL `index B's dependency row must survive ... expected: not <null>`（`_tmp/r135-stash-011.log`）。恢复后全绿。
- 全量回归：`./mvnw test -pl nop-code/nop-code-flow,nop-code/nop-code-service -am -T 1C` BUILD SUCCESS（flow 27/0/0；service 289/0/0, skipped 14）。〔closure audit 2026-10-10 复核勘误：surefire 实测 flow 模块 **43/0/0**（5 类），原文 27 为并行 reactor 输出误读；service 289/0/0 skipped 14 复核一致，全绿结论不变。〕

### Phase 3 - P1-7.4-001 状态双向勘正 + successor 归属

Status: completed
Targets: `ai-dev/backlog/nop-code-feature-completion-roadmap.md`、`ai-dev/plans/nop-code/28-n7-1-llm-semantic-edge.md`

- Item Types: `Decision`

- [x] `nop-code-feature-completion-roadmap.md:119` N7.1 状态 token `done` → **`deferred`**（原因写入该行 Work Item 文本，表无独立理由列：「G1–G4 提取器/预算/缓存与测试已交付；G1-b 生产接线未落地（Feature/装配面）→ successor」）；同步 `:51` 汇总 `done 34 · deferred 5 · todo 0` → **`done 33 · deferred 6 · todo 0`**
- [x] plan 28：G1-b 从 Phase 1 checklist 移入 `Deferred But Adjudicated`（Classification: `out-of-scope improvement`；Successor Required: yes；Successor Path: successor plan 尚未创建，仅在 roadmap N7.1 行登记归属），plan 状态 `draft` → **`deferred`**；Current Baseline 勘正（pom 现依赖 `nop-ai-api`，与 plan :20「当前不依赖」不符）
- [x] plan 28 其余 Phase 1 项对账：`LlmEdgeBudget`/`LlmEdgeCache`/`LlmSemanticEdgeExtractor` + 单测已 live，逐项勾选并加「delivered-but-unchecked 回填」注记（消除 M5 的静默缺失）；Phase 2 文档项按 live 现状勾选或显式移出
- [x] 不修改 `LlmSemanticEdgeExtractor` 代码；不改 nop-code-core `requiresLlm` 语义
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] roadmap N7.1 token = `deferred` 且汇总计数同步（`done 33 · deferred 6`）
- [x] plan 28 状态 = `deferred`，G1-b 落入显式 successor，无未对账的 delivered-but-unchecked 项
- [x] doc-links 退出码 0

### Phase 4 - owner doc 终检 + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-code.md`、roadmap、index、daily log

- Item Types: `Fix | Proof`

- [x] `nop-code.md` 终检：Phase 1/2 已落地的三节（rootPath 规范化与 git 失败 `truncated` 语义；失败文件指纹/数据保留；依赖删除 indexId 作用域）与 live 行为逐条一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.35 翻 done；index P1-6.7-009/010/011 → `fixed`、P1-7.4-001 → `fixed`（状态勘正处置，注记「ledger 误导面消除 + successor 归属」）；daily log 条目（Lead 2026-10-10 同步完成）
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Lead 同步 roadmap/index 后重跑通过）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] roadmap/index/log 三方一致（roadmap R1.35=done、index P1 四行=fixed + R1.35 行=done、daily log 条目在位）
- [x] 独立 closure audit 完成并落盘

## Risks And Rollback

- **删写重排（Phase 2）**：把 `deleteFileRecords(changedFiles)` 从分析前移到分析后、且扣除 failedFiles，改变既有事务内操作顺序。风险=既有依赖 N3.1-s 快照语义的测试回归；缓解=快照（`snapshotDependentCalls`/`computeAffectedFiles`）仍在删除前不变，删除仍在持久化前；回滚=单点恢复原顺序（`git revert` 本 Phase 提交即可）。
- **返回计数语义（Phase 2）**：无失败时等值；有失败时由「检出数」变为「成功持久化数」。风险=消费方（`IncrementalStatus.fileCount`）口径变化；缓解=失败时 WARN/INFO 明确，且失败场景原本就是缺陷态。
- **P1-6.7-009 rootPath 规范化**：仅读取侧转换，持久化格式不变（避免影响 VFS 语义）；对 `"/"`/普通绝对路径为幂等无操作。


## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.7-009/010/011）
- [x] P1-7.4-001 误导面已消除（roadmap/plan 状态双向勘正 + successor 归属）
- [x] 行为/契约结果已达成（判别测试在案）
- [x] 必要 focused verification 已完成（nop-code-flow/service 测试全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（001 的 G1-b 为显式 Feature successor，非缺陷降级）
- [x] 受影响 owner docs 已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证修复路径运行时连通（git 工作目录真实本地路径；失败文件真实重试；依赖删除真实索引隔离），无空方法体/静默跳过作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Lead 同步后重跑通过）

## Deferred But Adjudicated

### LlmSemanticEdgeExtractor 生产接线（plan 28 G1-b）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本批是缺陷修复面；G1-b 是新能力装配（需 `IChatService` 功能 bean、预算/缓存配置面、边持久化与开关语义设计），plan 28 自身 baseline 已过时（pom 依赖面变化），须以独立 plan 重做设计后实施。roadmap N7.1 状态勘正后不再误导后续开发，故不阻塞本批 closure。
- Successor Required: `yes`
- Successor Path: successor 计划尚未创建；当前仅在 roadmap N7.1 行登记 successor 归属，待起草 `nop-code-llm-semantic-edge-wiring` plan（由 plan 28 的 deferred 项承接）

## Non-Blocking Follow-ups

- P2-6.7-001..004（P2 裁决台账）；`CodeIndexService` 六域拆分（P2-4.3-001）

## Closure

Status Note: P1-6.7-009（`analyzeChanges` 将持久化 `file:` 形态 rootPath 经 `toLocalWorkingDirectory` 规范化为本地路径后再交给 git；`ChangeAnalyzer.parseGitDiff` 对工作目录缺失/非目录、git 非零退出、IOException/InterruptedException 一律置 `truncated=true`）、P1-6.7-010（`triggerIncrementalIndex` 先分析全部 changedFiles 再删写；analyzer null / resource null / 分析抛异常 / 分析返回 null 四类失败文件不删旧记录、不写新指纹，下轮重试；返回成功持久化数）、P1-6.7-011（`deleteFileRecords` 的 `NopCodeDependency` 删除改用 `deleteEntitiesByFilters`，同时按 `sourceFilePath`+`indexId` 过滤）经独立 closure audit 对 live 代码逐条复核：入口到出口调用链连通、判别测试确为行为载体、owner doc 与 live 一致、focused verification 全绿；P1-7.4-001 按 audit 原处方「补接线**或**双向状态勘正」（`7.4-nop-code-gated-incremental-audit.md:36`）取后者处置（roadmap N7.1 `done`→`deferred` + 汇总 33/6；plan 28 `draft`→`deferred`、G1-b 显式移入 Feature successor），无 in-scope live defect 被静默降级。唯一未勾选项属 Lead 处置域的 roadmap R1.35 / audit index 记账同步（`ai-dev/backlog/full-code-review-roadmap.md:166` 仍 `todo`；`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md:160-162,172` 仍 `open`）——本 audit 按约束不修改该两文件，故对应 Phase 4 记账项、其 Exit Criterion 与 `check-plan-checklist --strict` Closure Gate 留 `[ ]`，待 Lead 完成后补勾；不影响本 plan 已交付的技术契约。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: independent closure audit (subagent)，未参与实现
- Audit Session: 独立 closure-audit 子会话（parent session-9601d130-1bfd-4608-86de-471049fd6c0e）
- Evidence:
  - **P1-6.7-009 PASS（rootPath 规范化 + git 失败 fail-loud）**：
    - live：`CodeIndexService.analyzeChanges:2653-2660` 读 `indexEntity.getRootPath()` 后经 `toLocalWorkingDirectory` 再传 `workingDirectory`；helper `CodeIndexService.java:2995-3005` 剥 `file:` 前缀并对 `/C:/...` 还原盘符，`"/"`/普通/相对路径幂等。`ChangeAnalyzer.parseGitDiff:138-143` 工作目录非 null/非空且非目录 → WARN + `DiffResult(result, true)` 不启动进程；`:214-225` 仅 `waitFor(30s)` 为 true 才读 `exitValue()`，非零置 `truncated=true`；`:229-235` IOException/InterruptedException 分支 `truncated=true`。持久化格式未改（`resolveVfsPath:3009-3027` 仍产 `file:`）。
    - 判别测试：`TestCodeIndexServiceLocalWorkingDirectory`（4/4：strip、Windows 盘符、幂等、null/empty）；`TestChangeAnalyzer`（7/7，含 `testParseGitDiffMissingWorkingDirectoryIsTruncated`、`testParseGitDiffProcessCleanupOnInvalidRefs` 的 `truncated=true` 断言）；`TestAnalyzeChangesFileRootPath`（1/1，真实临时 git 仓库端到端：`indexDirectory` → `analyzeChanges` 断言 `isTruncated()==false` 且 `changedFiles` 含 `A.java`）。
    - 判别性：无 `toLocalWorkingDirectory` 时 `workingDirectory="file:/..."` 命中缺失目录检查 → `assertFalse(result.isTruncated())` 必 `expected:<false> but was:<true>`（在案 `_tmp/r135-stash-009.log` 逐字命中）；无 `exitValue()` 检查时 `assertTrue(diff.truncated)` 必失败；无缺失目录守卫时 `pb.start()` 抛 IOException 走旧分支返回 `truncated=false`。
  - **P1-6.7-010 PASS（失败文件数据/指纹保留）**：
    - live：`triggerIncrementalIndex:1110-1153` 先对全部 changedFiles 分析并收集四类 `failedFiles`（analyzer null `:1123`、resource null `:1130`、`analyze` 返回 null `:1145`、抛异常 `:1149`），此阶段不删记录；`:1157-1162` 派生 `successfulChangedFiles`；`:1164-1177` N3.1-s 快照/exclude 收窄到 successfulChangedFiles ∪ deletedFiles（失败文件行未删，其边不进入重建集合）；`:1179-1180` 才删除 deleted + successful；`:1201-1212` 新指纹扣除 `failedFiles` 后 `saveFingerprints`（旧 `NopCodeFile` 行原样保留）；`:1222` 返回 `changedResults.size()`。`resolveReanalyzedCalls:2161-2193` 的 DB 回查 `excludeFilePaths` = successful ∪ deleted，失败文件符号仍可解析，收窄未丢失「引用失败文件符号」的边。
    - 判别测试：`TestIncrementalFailedFileRetention`（2/2：`analyzerExceptionKeepsFingerprintAndIsRetried`、`analyzerNullResultKeepsFingerprintAndIsRetried`——首轮成功索引 → 切 THROW/NULL → 改文件增量断言 `changed==0` 且旧指纹不变 → 恢复后重试 `>=1` 且指纹更新）。
    - 判别性：在案 `_tmp/r135-stash-010.log`（还原「不排除失败文件指纹」→ 2 FAIL，逐字 `the failed file must keep its previous fingerprint ... expected:<3bfc...> but was:<841f...>`）、`_tmp/r135-stash-010b.log`（null 分支静默丢弃 → 恰 null 用例 1 FAIL）。逻辑核验：删写重排前旧记录先删、指纹全量照存，`assertEquals(hashV1, fileHash)` 与 `assertEquals(0, changed)` 必失败。N3.1-s 护栏 `TestIncrementalCalleeResolution`/`TestIncrementalDependencyPropagation` 在 service 289 全绿。
  - **P1-6.7-011 PASS（依赖删除 indexId 作用域）**：
    - live：`deleteFileRecords:2400-2406` 改调 `deleteEntitiesByFilters(NopCodeDependency.class, FilterBeans.and(eq("sourceFilePath", filePath), eq("indexId", indexId)))`；新多 filter 重载 `:2504-2519`（分页删除 + 每轮 `flushSession`）；`NopCodeDependency.indexId` 为 mandatory 列（`nop-code/model/nop-code.orm.xml:766`）。同类删除作用域核对：`:908` 依赖按 indexId；call/symbol/usage 经 `generateFileId(indexId,path)`（`:2397-2399`）；relational 经本索引 symbolId（`:2407-2421`）；`CodeCacheManager.rebuildDependencies:254-268` 按 indexId 读——唯 `:2404` 缺口已补。
    - 判别测试：`TestDeleteFileRecordsIndexScoping`（1/1，经公开入口 `batchDeleteFileRecords("idx-a", ...)`：depA 删除、跨索引 depB 存活）。
    - 判别性：在案 `_tmp/r135-stash-011.log`（还原无作用域删除 → 恰 1 FAIL，逐字 `index B's dependency row must survive (indexId scoping) ==> expected: not <null>`）。
  - **P1-7.4-001 PASS（状态双向勘正 + successor 归属）**：
    - audit 原处方允许「补 LLM 提取器接线 **或** roadmap/plan 双向状态勘正」（`7.4-nop-code-gated-incremental-audit.md:36`），本批取后者，属授权处置而非降级。
    - live 事实核实：`CodeIndexService.discoverSemanticExtractors:285-291` 仍硬编码 3 个确定性提取器；`LlmSemanticEdgeExtractor` 仅 `src/main` 定义 + 单测引用，全仓 beans.xml 零 bean；`nop-code/nop-code-service/pom.xml:73,78` 依赖 `nop-ai-api`/`nop-ai-rag`，`<IChatService>` 功能 bean 仅在 `nop-ai/nop-ai-core/.../ai-defaults.beans.xml:9`（classpath 未引入）——G1-b 为未落地的新能力装配（Feature），非既有行为缺陷。
    - 台账勘正：`ai-dev/backlog/nop-code-feature-completion-roadmap.md:119` N7.1 `done`→`deferred`（原因入该行）、`:51` 汇总 `done 33 · deferred 6 · todo 0`（实点：33 done / 6 deferred / 0 todo）；`ai-dev/plans/nop-code/28-n7-1-llm-semantic-edge.md` `draft`→`deferred`、G1-b 落入 `Deferred But Adjudicated`（Successor Required: yes，登记 roadmap N7.1）、G1/G2/G3/测试 delivered-but-unchecked 回填；`ai-dev/design/nop-code/semantic-edge-design.md:106-108` §4.3 状态注；owner doc `docs-for-ai/03-modules/nop-code.md:164-170` 新增契约节。`LlmSemanticEdgeExtractor` 源码与 nop-code-core `requiresLlm` 未改。
  - **focused verification PASS**：`./mvnw test -pl nop-code/nop-code-flow,nop-code/nop-code-service -am -T 1C` → BUILD SUCCESS，exit 0（2026-10-10 16:20）。独立复核 surefire：`nop-code-flow` **43/0/0**（5 类：TestCacheEviction 4、TestChangeAnalyzer 7、TestChangeAnalyzerPathMatching 6、TestDeadCodeDetector 17、TestFlowDetector 9）；`nop-code-service` **289/0/0, skipped 14**（与作者执行证据一致）。**勘误**：Phase 2 执行证据 `:114` 原文「flow 27/0/0」为并行 reactor 输出误读，实测 flow 模块 43/0/0；全绿结论不变。
  - **owner doc PASS**：`docs-for-ai/03-modules/nop-code.md` 四节契约（rootPath 规范化 + git `truncated`；失败文件指纹/数据保留；依赖删除 indexId 作用域；N3.1-s 收窄）与 live 行为逐条一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors，6 条既有/relative-prose warnings，不阻塞）。
  - **Anti-Hollow PASS**：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 0（Critical/High/Medium/Low 全 0）；调用链追踪：`analyzeChanges(:2657)`→`ChangeAnalyzer.parseGitDiff` 真启 git 进程判 exitValue；`triggerIncrementalIndex(:1119-1212)` 真分析真落盘真保留失败指纹；`deleteFileRecords(:2404)` 真按 indexId 过滤。无空方法体/静默 continue/no-op 作为正常实现（失败分支均 WARN + 保留）。
  - **Deferred 分类检查 PASS**：`Deferred But Adjudicated` 仅 G1-b（`out-of-scope improvement`，Feature/装配面 + Successor Required yes + successor path 登记）；`Non-Blocking Follow-ups` 仅 P2 台账与六域拆分，无 in-scope live defect/contract drift/owner-doc drift 被降级。
  - **Closure Gates**：除 `check-plan-checklist --strict 退出码 0` 阻塞于 Lead 记账项外，其余全部 PASS（见上）。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2401-full-code-review-R1.35-nop-code-incremental-index-fixes.md --strict` 退出码 1（原因唯一：Phase 4 的 Lead roadmap/index 记账项、其 Exit Criterion「roadmap/index/log 三方一致」及依赖它们的 `check-plan-checklist` 项/Closure Gate 未勾选）；非代码/契约失败。

Follow-up:

- Lead 处置域：`ai-dev/backlog/full-code-review-roadmap.md:166` R1.35 翻 done；`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md:160-162` P1-6.7-009/010/011 → `fixed`、`:172` P1-7.4-001 → `fixed`（注记「ledger 误导面消除 + successor 归属」）；完成后勾选 Phase 4 记账项 / Exit Criterion / `check-plan-checklist` 项与 Closure Gate，重跑 `check-plan-checklist --strict` 应收敛为 0。属记账治理，不影响已交付技术契约（本 audit 不修改该两文件）。
- `nop-code-llm-semantic-edge-wiring` successor plan：承接 plan 28 G1-b（`IChatService` bean / 预算缓存配置 / 边持久化 / `nop.code.llm-semantic.enabled` 开关 + docs-for-ai nop-code.md 文档节）。
