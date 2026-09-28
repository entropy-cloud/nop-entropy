# 24 N4.3 混合搜索 RRF（searchType → SearchType.HYBRID/VECTOR 查询面贯通）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N4.3；`ai-dev/design/nop-code/search-integration-design.md`；live 核对 2026-09-28
> Related: `ai-dev/plans/nop-code/23-n4-2-text-embedding-production.md`（N4.2 已完成——引擎向量索引/查询路径已接真实嵌入桥接）

## Purpose

落地 roadmap N4.3：把 nop-code 的 `searchCode` 查询面贯通到 nop-search 引擎的 `SearchType.HYBRID`/`VECTOR`（RRF 融合）。引擎侧 HYBRID（文本 + 向量 RRF，k=60）已在 `LuceneSearchEngine` 实现并有自有测试；缺口仅在 nop-code 的 `CodeSearchService.searchViaEngine` 硬编码 `SearchType.TEXT`，`searchType` 参数在引擎路径被丢弃。

## Current Baseline

- `LuceneSearchEngine.search` 按 `request.getSearchType()` 分派 TEXT/VECTOR/HYBRID；HYBRID = 文本查询 + `KnnFloatVectorQuery`（向量来自 `parseQueryVector`：JSON 数组 → `ITextEmbedding.embed` → hash 兜底）→ `mergeWithRRF`（k=60）；自有测试在 nop-search-lucene。
- `CodeSearchService.searchViaEngine`（nop-code-service）`req.setSearchType(SearchType.TEXT)` 硬编码（L75）；DB LIKE 降级路径的 `searchType` 语义 = SYMBOL_NAME/FULL_TEXT/COMBINED（私有于 DB 路径）。
- `NopCodeSymbolBizModel.searchCode` 把 `searchType`（缺省 "COMBINED"）透传给 `CodeIndexService.searchCode` → `CodeSearchService.searchCode`。
- 索引同步侧 `CodeIndexService` 已 `doc.setAutoGenerateEmbedding(true)`（L1765 区域）——N4.2 桥接在 classpath 时符号自动嵌入入库。
- N4.1 e2e 范式：`TestCodeSearchEngineAssembly`（JunitAutoTestCase + localDb + GraphQL `searchCode` 入口 + `matchType=SEARCH_ENGINE` 判别 + index-dir 覆盖 `./target/nop-code-search-engine-test-indices`）。
- `search-integration-design.md` 头部状态：N4.2 已落地、混合搜索查询面暴露为待增强项（N4.3）。
- **向量字段索引侧无 hash 兜底**（B1 审查修正）：`buildDocument` 生成 `KnnFloatVectorField` 的条件是 `autoGenerateEmbedding && textEmbedding != null`；hash 模拟仅存在于查询侧 `parseQueryVector`。nop-code-service 测试类路径无任何 `ITextEmbedding` bean → 同步的符号 doc 无向量字段 → `KnnFloatVectorQuery` 返回空 TopDocs（Lucene 9.7 不抛异常）。故 e2e 必须在测试容器注册确定性测试 `ITextEmbedding` bean（见 Phase 2），否则 VECTOR 断言必败、HYBRID 将以"向量腿死亡"空壳通过。

## Goals

- G1：`CodeSearchService` 引擎路径的 `searchType` 映射：`TEXT`/`VECTOR`/`HYBRID`（大小写不敏感）→ 对应 `SearchType`；其余值（含 legacy SYMBOL_NAME/FULL_TEXT/COMBINED）与 null → `SearchType.TEXT`（引擎路径零回归）。
- G2：端到端验证：经 GraphQL `searchCode(searchType="HYBRID"/"VECTOR")` 引擎路径可执行、结果 `matchType=SEARCH_ENGINE`、非空命中（N4.1 测试范式扩展）。
- G3：owner doc（query-api-design + search-integration-design + docs-for-ai）与 roadmap 同步。

## Non-Goals

- `LuceneSearchEngine` 的 RRF 参数（k=60）调整或阈值语义变更（引擎自有域，已有测试）。
- nop-code 查询面新增返回字段（RRF 融合分数经既有 `score` 字段透传，无需 DTO 变更）。
- 真实嵌入排序质量的 nop-code 层验收（属 N9 验收范畴）。

## Scope

### In Scope

- `nop-code/nop-code-service`：`CodeSearchService` 映射方法 + 调用点；同包单测；e2e 测试类（N4.1 范式）。
- 文档：`query-api-design.md`、`search-integration-design.md`、`docs-for-ai/03-modules/nop-code.md`、roadmap。

### Out Of Scope

- nop-search 各模块改动（零改动）。
- BizModel/xmeta 签名变更（`searchType` 已是透传 String，无契约变更）。

## Execution Plan

### Phase 1 - searchType 映射 + 单测

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeSearchService.java`、`nop-code-service/src/test/java/io/nop/code/service/impl/`（映射单测——被测类为包私有，测试须同包 `io.nop.code.service.impl`，先例 `TestCodeSearchFallbackLike`）

- Item Types: `Fix`

- [x] 提取包级静态方法 `mapSearchType(String searchType)`：TEXT/VECTOR/HYBRID（大小写不敏感）→ 对应枚举；其余/null → `SearchType.TEXT`；javadoc 写明 legacy DB 值的归一语义
- [x] `searchViaEngine` 用 `mapSearchType` 替换硬编码 `SearchType.TEXT`
- [x] 同包单测：三引擎值 + 大小写变体 + null + 三个 legacy 值 + 未知值的映射断言

Exit Criteria:

- [x] 单测全绿；`./mvnw test-compile -pl nop-code/nop-code-service -o -q` 通过
- [x] 无静默跳过：映射为全值域显式分支（switch/else 链），无魔法行为
- [x] No owner-doc update required（Phase 2 统一覆盖）；`ai-dev/logs/` 条目随 Phase 2 一并写入（Phase 2 EC 显式包含）

### Phase 2 - HYBRID/VECTOR e2e + 文档 + roadmap

Status: completed
Targets: `nop-code-service/src/test/java/io/nop/code/service/TestCodeSearchHybridVector.java`（新）、`ai-dev/design/nop-code/query-api-design.md`、`ai-dev/design/nop-code/search-integration-design.md`、`docs-for-ai/03-modules/nop-code.md`、`ai-dev/backlog/nop-code-feature-completion-roadmap.md`

- Item Types: `Fix`、`Proof`

- [x] 测试嵌入 bean：`src/test/resources/_vfs/nop/autoconfig/` 增 `aaa-test-text-embedding.beans` 指向测试 beans 文件，注册确定性 `StubTestTextEmbedding implements ITextEmbedding`（dim=16，词项 hash → 位桶累加 + 归一化；同词同向量，索引侧/查询侧同源语义）；stub 类放 `src/test/java` nop-code-service。注入机理：bean 定义全量合并后由 `BeanContainerBuilder.buildAll` 统一构建，by-type 解析针对全部已注册定义——与 autoconfig 文件收集顺序无关；`aaa-` 前缀仅为可读性约定（可选）
- [x] e2e（N4.1 范式：JunitAutoTestCase + `@NopTestConfig(localDb=...)` + index-dir 覆盖 + GraphQL `searchCode` 入口）：索引含已知符号（如 `Zebra.gallop`）的 Java 文件后，`searchType="VECTOR"` 断言命中该符号（向量腿真实贡献——同名次查询的词项向量与 doc 向量同源）且 `matchType=SEARCH_ENGINE`；`searchType="HYBRID"` 断言命中 + `matchType=SEARCH_ENGINE`（文本腿 + 向量腿双活）；`searchType="TEXT"` 对照组不回归
- [x] 零回归确认：注册测试嵌入 bean 后，模块既有引擎/降级测试（`TestIncrementalSearchSync`/`TestCodeSearchEngineAssembly`/`TestCodeSearchFallbackLike`）全绿
- [x] `query-api-design.md` §searchCode：补 searchType 引擎路径值域（TEXT/VECTOR/HYBRID；legacy 值引擎路径归一 TEXT；向量腿成立条件 = classpath 有 `ITextEmbedding` 实现，缺席时 VECTOR 空结果、HYBRID 仅文本腿；引擎异常时既有降级路径返回 DB COMBINED 结果，与请求 searchType 无关）
- [x] `search-integration-design.md` 头部状态更新（N4.3 落地）+ 查询面小节
- [x] `docs-for-ai/03-modules/nop-code.md` searchCode 说明同步
- [x] roadmap N4.3 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 执行日条目更新

Exit Criteria:

- [x] `./mvnw test -pl nop-code/nop-code-service -am -T 1C -o` 全绿（含新增 e2e）
- [x] **端到端验证**：GraphQL 入口 → CodeSearchService 映射 → 引擎 VECTOR（kNN）与 HYBRID（RRF）→ matchType=SEARCH_ENGINE 结果完整走通（HYBRID 双腿为合证：VECTOR 用例证明向量腿经同一请求路径工作 + 引擎自有 hybridSearch 测试）
- [x] roadmap/owner docs 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] N4.3 交付词逐项：引擎路径 HYBRID/VECTOR 查询面贯通、RRF 融合结果可达、实现 + 测试齐备
- [x] `scan-hollow-implementations --module nop-code/nop-code-service --severity high`：N4.3 新增代码零 high finding（pre-existing 命中如实记录）
- [x] `check-plan-checklist --strict` 退出码 0
- [x] `check-doc-links --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 完成并记录证据（含 Anti-Hollow）
- [x] 代码风格核查：import 分组、4 空格缩进、与同文件注释密度一致
- [x] `./mvnw test -pl nop-code/nop-code-service -am -T 1C -o` 全绿（模块级门禁）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- （无）

## Closure

Status Note: N4.3 交付真实：映射实现 + 全值域单测、wiring 反射断言、VECTOR/HYBRID rank-1 e2e、模块全量 267 tests 零回归、四份 owner doc + roadmap + daily log 同步、审计工具 exit 0、R2 机理修正（aaa- 前缀仅为可读性约定）已落位且无残留错误表述。独立 closure audit（agent_a4bca640）裁定 APPROVE，两项条件（evidence 回填、scope 隔离 commit）已随即执行。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_a4bca640-487b-4b15-918a-f5b81af9ee04（fresh session，与本会话实现者无关）
- Evidence:
  - Phase 1 全部 Exit Criteria PASS（mapSearchType L68-87 全值域显式分支 + javadoc legacy 归一语义；searchViaEngine L96 真实消费；同包单测 3/3 实跑绿）
  - Phase 2 全部 Exit Criteria PASS（测试嵌入四件套在档；e2e VECTOR rank-1/HYBRID rank-1/TEXT+COMBINED 对照全 SEARCH_ENGINE；零回归三点名测试实跑全绿 + 全量 63 suites/267 tests/0 failures；四处文档 + roadmap done 24·todo 15 + daily log）
  - **向量腿真实性（Anti-Hollow 重点）**：VECTOR 用例三重钉住——wiring 反射断言 stub 注入引擎 + 索引侧 KnnFloatVectorField 真实生成（autoGenerateEmbedding && textEmbedding!=null）+ 查询/索引同源 stub（kNN 腿死亡则 rank-1 必败）；HYBRID 合证措辞在 plan L85 如实登记（未夸大）
  - Closure Gates：`check-plan-checklist --strict` exit 0；`check-doc-links --strict` exit 0；`scan-hollow --module nop-code/nop-code-service --severity high` exit 0（0 findings）；代码风格 PASS；R2 机理修正落位核对 PASS（plan 与 beans 注释无残留错误表述）
  - Deferred 项分类检查：Deferred 为空、Follow-ups 为空——无 in-scope live defect 被降级（audit 确认）

Follow-up:

- no remaining plan-owned work。
