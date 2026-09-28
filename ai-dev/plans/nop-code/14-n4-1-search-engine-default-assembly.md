# 14 N4.1 搜索引擎默认装配 + 双路径端到端验证

> Plan Status: completed(R1 1B/1M/1m 修订 + R2 复审 APPROVE + 独立 closure audit APPROVE，agent_22fd3802)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N4.1；`ai-dev/design/nop-code/search-integration-design.md`（设计权威，双路径已实现、默认未注入引擎）；live 核对（2026-09-28，含 IoC 装配链路实证）
> Related: N4.2/N4.3（向量/混合，外部依赖 rag 路线图，本 plan 不触及）

## Purpose

收口搜索集成的最后缺口：生产部署默认装配 `LuceneSearchEngine`（当前全链路零装配，`searchCode` 恒走 DB LIKE 降级），并以端到端测试钉住引擎路径与降级路径两条行为。

## Current Baseline（live 核对 2026-09-28）

- **双路径已实现**：`CodeSearchService`（package-private，nop-code-service impl 包）持可空 `ISearchEngine`，非空走 `searchViaEngine`（`SearchType.TEXT`，language→tag），空走 LIKE 三分支（SYMBOL_NAME/FULL_TEXT/COMBINED）。`CodeIndexService.setSearchEngine(@Nullable)` 为 `@Inject` setter。
- **引擎实现与注册**：`LuceneSearchEngine`（nop-search-lucene）已由该模块 autoconfig 注册——`_vfs/nop/autoconfig/nop-search.beans` → `search-defaults.beans.xml`（bean `nopSearchEngine`，`ioc:default=true`；`nopLuceneConfig` 前缀 `nop.search`，默认 indexDir `/nop/search/indices`）。**nop-code 全模块（service/web/app）仅依赖 nop-search-api，引擎 impl 不在任何 nop-code classpath 上** → 生产装配缺口 = 依赖缺失。
- **装配机制（实证）**：`NopJunitExtension`/`CoreInitialization.initialize()` 走标准容器；`AppBeanContainerLoader.getAutoConfigResources()` 收集全部 `/nop/autoconfig/*.beans`（默认无过滤）；`@Inject` 按类型注入（平台先例：nop-ai credential resolver "消费 app 含 nop-ai-service 即完成接线"）。**推论：impl jar 上 classpath 即完成 by-type 装配；同时意味着把它加进 nop-code-service test classpath 会使全部 service 测试容器自动注入引擎**——本 plan 显式利用该语义并验证其后果。
- **indexDir 陷阱（R1 Blocker 实证）**：`LuceneConfig.indexDir` 默认 `/nop/search/indices`，`FileHelper.resolveFile` 对 `/` 开头取**文件系统根绝对路径**——macOS 下 mkdirs 失败→引擎抛错→`searchViaEngine` catch 静默回退 LIKE（测试空转假绿）；Linux CI 则污染根目录。测试必须以 `@NopTestConfig(testConfigFile=...)` 覆盖 `nop.search.index-dir` 到模块 target 下的 per-class 目录（先例：nop-datav-service `testConfigFile="classpath:...yaml"`）。
- **静默回退判别器（R1 Major 实证）**：`searchViaEngine` 任何异常回退 LIKE；引擎路径唯一判别器是结果 `matchType="SEARCH_ENGINE"`——引擎 e2e 必须显式断言该值。
- **执行期发现（confirmed live defect，已修复）**：`LuceneSearchEngine.getDirectory` 以 `StringHelper.isValidSimpleVarName` 校验 topic，而 `CodeIndexService` 的 topic 前缀 `"nop-code-"` 含连字符——**任何 indexId 的引擎同步（addDoc/removeDocs/removeTopic）都被守卫拒绝且被 fail-soft 吞掉**，即基线所称"双路径已实现"的引擎侧从未真正工作。修复：`isValidTopicName`（字母/数字/下划线/连字符，禁点开头与 `..`，topic 实际是索引目录名）替换守卫 + `TestLuceneSearchEngine` 回归 2 例（连字符 topic 可同步、路径穿越 topic 被拒）。
- **既有测试**：`TestNopSearchIntegration`（GraphQL `searchCode`，无引擎 context = 事实上的降级路径）、`TestIncrementalSearchSync`（显式 `setSearchEngine` 注入 RecordingSearchEngine 假引擎，钉 addDoc/removeDocs 同步语义）。真 Lucene 引擎从未被任何测试执行过。

## Goals

- **G1 生产默认装配**：`nop-code-app` pom 增 `nop-search-lucene` 依赖（版本经 nop-bom 管理）；装配机制 = autoconfig 注册 + by-type 注入（平台先例模式，零 XML）。
- **G2 装配证据 + 引擎路径 e2e**：nop-code-service 增 `nop-search-lucene` **test 依赖**；新增集成测试断言：(a) 容器内 `CodeIndexService` 的 engine 字段被自动注入为 `LuceneSearchEngine` 实例（接线验证规则 #23）；(b) 索引后经 GraphQL `searchCode` 命中引擎结果；(c) 增量删除符号后引擎结果同步消失（真 Lucene removeDocs）。
- **G3 降级路径钉住**：新增测试直接以 `searchEngine=null` 构造 `CodeSearchService`（同包测试），钉住 LIKE 三分支行为——不依赖容器注入状态，结构性防"降级路径无覆盖"。
- **G4 既有测试适配裁定**：test classpath 引入引擎后，原"事实降级"测试（TestNopSearchIntegration 等）自动切引擎路径——全量回归必须绿；任何需要修改期望的测试逐个记录裁定理由（行为断言不放松）。
- **G5 docs/roadmap 同步**：search-integration-design.md 状态更新（默认装配已落地）；缺口矩阵 N4.1 done；roadmap N4.1 todo→done + 计数。

## Non-Goals

- 不实现向量嵌入/混合搜索（N4.2/N4.3，外部依赖 rag 路线图）。
- 不改 `CodeSearchService` 双路径逻辑与 `SearchType.TEXT` 契约。
- 不在 nop-code-service 引入 nop-search-lucene **compile/main** 依赖（保持设计决策"仅接口层，实现由部署注入"；test scope 是验证需要，且其装配副作用被 G2/G4 显式接纳）。
- 不为 nop-code-app 新建测试基建（首个 app 模块测试的基建成本与收益不成比例；装配证据由 G2 的容器级断言承载——同一 autoconfig+by-type 机制）。

## Scope

### In Scope

- `nop-code/nop-code-app/pom.xml`（+nop-search-lucene）、`nop-code/nop-code-service/pom.xml`（test scope +nop-search-lucene）
- 新测试：引擎装配/e2e（service test）、降级路径钉住（impl 同包测试）
- 既有测试期望适配（如有，逐个记录）
- owner docs：search-integration-design.md、`docs-for-ai/03-modules/nop-code.md`（如搜索章节需同步）、缺口矩阵、roadmap

### Out Of Scope

- nop-search 自身改动（引擎/config 均已存在且被其他消费方使用）。
- 向量/混合、搜索结果排序语义调优。

## Execution Plan

### Phase 1 - 装配 + 引擎路径 e2e（G1/G2）

Status: completed
Targets: `nop-code/nop-code-app/pom.xml`、`nop-code/nop-code-service/pom.xml`、`nop-code/nop-code-service/src/test/java/`

- Item Types: `Fix`（装配缺口为 confirmed gap，roadmap N4.1 登记）

- [x] nop-code-app pom 增 nop-search-lucene 依赖（生产默认装配）；nop-code-service pom 增 test scope 依赖
- [x] 测试配置：`testConfigFile` 指向 test resources yaml，覆盖 `nop.search.index-dir` 到 `./target/<per-class>-indices`（隔离 + mvn clean 可清理）
- [x] 修复 `LuceneSearchEngine` topic 守卫缺陷（执行期发现，confirmed live defect）：`isValidTopicName` 替换 `isValidSimpleVarName`（允许连字符、保留路径穿越防护）+ nop-search-lucene 回归测试 2 例
- [x] 集成测试 A（装配证据）：JunitAutoTestCase 容器内反射断言 `CodeIndexService.searchEngine` 字段非 null 且为 `LuceneSearchEngine` 实例
- [x] 集成测试 B（引擎 e2e 正向）：临时项目 indexDirectory → GraphQL `NopCodeSymbol__searchCode` 返回命中且 **`matchType="SEARCH_ENGINE"`**（防静默回退假绿）
- [x] 集成测试 C（引擎 e2e 增量同步）：indexFile 删除符号 → searchCode 不再命中且先前命中带 `matchType="SEARCH_ENGINE"`（真 Lucene removeDocs）
- [x] 全量回归 `./mvnw test -pl nop-code/nop-code-service -am` 绿；被自动切路径的既有测试逐个核对（期望变化须记录裁定）
- [x] 测试无磁盘残留于文件系统根（indexDir 覆盖生效验证：断言 target 下目录创建或引擎 docs 命中即可证明写入了受控目录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试 A/B/C 全绿；nop-code-app 构建含 nop-search-lucene（`./mvnw compile -pl nop-code/nop-code-app`）
- [x] **接线验证**（规则 #23）：引擎注入经容器装配断言（非手工 set），autoconfig→by-type 链路被测试钉住
- [x] **端到端验证**（规则 #22）：indexDirectory（写）→ searchCode（读）→ indexFile 删除（变）→ searchCode（读）全链路断言
- [x] 既有测试回归全绿，期望若有调整逐条记录理由
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 降级路径钉住 + docs/roadmap 同步（G3/G4/G5）

Status: completed
Targets: `nop-code/nop-code-service/src/test/java/io/nop/code/service/impl/`、`ai-dev/design/nop-code/search-integration-design.md`、缺口矩阵、roadmap

- Item Types: `Fix | Proof`

- [x] 降级钉住测试（impl 同包，先例 TestCodeSearchServiceGlobFilter）：容器 `@Inject IDaoProvider`（localDb）后手工构造 `CodeSearchService(daoProvider, null, cacheManager)`，断言 SYMBOL_NAME（contains 匹配+前缀加分，以 live 语义为准）/FULL_TEXT/COMBINED（并集去重排序）三行为——engine=null 与新 classpath 结构性隔离
- [x] search-integration-design.md 头部状态更新：默认装配已落地（app 依赖 + autoconfig + by-type），双路径测试在档
- [x] `docs-for-ai/03-modules/nop-code.md` 搜索小节核对/同步（如已描述双路径则补默认装配一句）
- [x] 缺口矩阵 N4.1 行 done；roadmap N4.1 todo→done + 汇总计数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 降级三分支测试全绿且不依赖容器注入状态（直接构造）
- [x] roadmap/缺口矩阵/baseline 三处一致（N4.1 done、计数正确）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 生产默认装配落地且有容器级测试钉住（nop-code-app 含引擎依赖）
- [x] 双路径端到端验证完成：引擎路径（真 Lucene e2e）+ 降级路径（同包直接构造钉住）
- [x] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿 + nop-code-app compile）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap
- [x] 受影响 owner docs 已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 装配链路（autoconfig→by-type→字段）运行时真实连通（非仅依赖存在），(b) 引擎 e2e 从 GraphQL 入口到结果断言，(c) 无静默跳过
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `./mvnw compile -pl nop-code/nop-code-app` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 生产默认装配落地（nop-code-app 依赖 nop-search-lucene，autoconfig+by-type 注入，容器级测试钉住）；引擎路径 e2e 以 matchType=SEARCH_ENGINE 判别器防静默回退假绿；降级三分支以同包直接构造结构性钉住；执行期发现并修复 Lucene topic 守卫 confirmed live defect（连字符 topic 致引擎同步静默全灭）。独立 closure audit APPROVE。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_22fd3802）
- Evidence:
  - Phase 1/2 Exit Criteria 全 PASS（pom 落地、装配/引擎 e2e/降级 9 测试全绿、设计文档+矩阵+roadmap 一致、doc-links 0、hollow 0）
  - Anti-Hollow 三项 PASS：(a) 装配链 autoconfig→by-type→字段经容器单例反射断言（测试从不手工 set）；(b) 引擎 e2e 从 GraphQL searchCode 入口断言 matchType=SEARCH_ENGINE（LIKE 回退不可能产生该标记，静默回退不可能假绿）；(c) isValidTopicName 拒绝 ../x、.x 与非法字符，回归钉住
  - 影响面复核 PASS：LuceneSearchEngine 仅 2 hunk（守卫替换），收紧面仍全部拒绝
  - 实跑 PASS：nop-search-lucene 全量 exit 0（含新守卫 2 例）、nop-code-service -am 233/0、nop-code-app compile exit 0
  - 既有测试零期望改动（git 仅新增 2 个测试文件）
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- no remaining plan-owned work
