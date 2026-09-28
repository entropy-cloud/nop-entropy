# 23 N4.2 向量嵌入生产实现（ITextEmbedding SPI + nop-ai 后端桥接）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N4.2；上游前置 `ai-dev/plans/knowledge-rag/01-k1-embedding-api-client.md`（K1 已完成，`IEmbeddingModel` 生产实现 `EmbeddingServiceImpl` 在档）；live 核对 2026-09-28
> Related: `ai-dev/plans/nop-code/14-n4-1-search-engine-default-assembly.md`（N4.1，已定装配机制：autoconfig + by-type 注入）

## Purpose

落地 roadmap N4.2：实现 nop-search 的 `ITextEmbedding` SPI 的首个平台生产实现——桥接 nop-ai 的 `IEmbeddingModel`（K1 已落地），使 `LuceneSearchEngine` 的向量字段索引与 VECTOR/HYBRID 查询路径从"hash 模拟（仅测试）"升级为真实嵌入。这是 N4.3（混合搜索 RRF）的硬前置。

## Current Baseline

- `ITextEmbedding`（nop-search-api）：`embed(String): float[]`（javadoc 允许返回 null 表示不可计算）、`embedAll`、`embedAsync/embedAllAsync` 默认包装、`getDimension()` 默认 -1。全仓 main 零实现（live grep 确认，仅 LuceneSearchEngine 消费）。
- `LuceneSearchEngine`（bean `nopSearchEngine`，nop-search autoconfig 注册）：
  - **索引侧**：`buildDocument` 中 `doc.isAutoGenerateEmbedding() && textEmbedding != null` 时经 `textEmbedding.embed(buildTextForEmbedding(doc))` 生成 `KnnFloatVectorField`；单文档嵌入失败 WARN 后继续（文本-only 入索引，索引侧降级为既有行为）。
  - **查询侧**：`parseQueryVector`：JSON 数组字面量 → `textEmbedding.embed(query)` → hash 模拟兜底（`generateSimpleEmbedding`，注释明示"仅用于测试"）。
  - **装配缺口**：`setTextEmbedding` 是**普通 setter（无 `@Inject`）**，`search-defaults.beans.xml` 的 `nopSearchEngine` 也无该 property——即使 classpath 上有 `ITextEmbedding` bean 也不会被注入，`textEmbedding` 恒 null。
- K1（已提交）：`EmbeddingServiceImpl`（bean `nopAiEmbeddingModel`，`ioc:type=IEmbeddingModel`）为 `IEmbeddingModel` 唯一生产实现；provider 未配置时调用 fail-loud（`ERR_AI_EMBEDDING_NO_PROVIDER`）。
- `nop-ai-core` 当前**不依赖** nop-search 任何模块；`nop-search-api` 仅依赖 `nop-api-core`（轻 api jar）。
- N4.1 装配机制（实证）：实现 jar 上 classpath + `@Inject` by-type 注入即完成装配；`@Nullable` 使注入可选（`ChatServiceImpl.setCredentialResolver` 平台先例）。
- `SearchableDoc`：`embedding`（float[]）+ `autoGenerateEmbedding`（boolean，默认 false）字段在档。
- 向量维度契约：同一 Lucene 索引内 `KnnFloatVectorField` 维度必须一致（Lucene 原生约束），维度漂移由 Lucene 报错（fail-loud）。

## Goals

- G1：`AiModelTextEmbedding implements ITextEmbedding`（nop-ai-core），委托 `IEmbeddingModel`：`embed`/`embedAll` 真实调用 + `VectorData.double[] → float[]` 转换 + 维度/批量路径。失败语义 = 异常上抛（fail-loud，不用接口允许的 null 静默路径——静默 null 会让索引侧悄悄退化为文本-only、查询侧落 hash 模拟，违反 No Silent No-Op）。
- G2：注入接线：`LuceneSearchEngine.setTextEmbedding` 加 `@Inject` + `@Nullable`（可选注入；无实现 bean 时保持 null → 既有 hash 模拟路径零回归）；`nop-ai-core` 注册 `nopAiTextEmbedding` bean。
- G3：端到端验证（Anti-Hollow）：容器级测试证明 `IEmbeddingModel` bean（stub）经 `AiModelTextEmbedding` 被注入进 `LuceneSearchEngine`，且 `addDocs(autoGenerateEmbedding=true)` → `SearchType.VECTOR` 文本查询命中正确文档（嵌入真实进入索引与查询路径）。
- G4：owner doc + roadmap 状态同步。

## Non-Goals

- `SearchType.HYBRID` 的 nop-code 查询面暴露与 RRF 端到端（N4.3 承接）。
- `CodeIndexService`/`CodeSearchService` 的任何改动（N4.3 承接）。
- pgvector/外部向量库（K2 承接）。
- 索引侧嵌入失败的批量重试/断点续嵌（索引侧 WARN 降级为 Lucene 引擎既有语义，本计划不改）。
- embedding 维度漂移的迁移工具（Lucene 原生 fail-loud 已足够，运维重建索引即可）。

## Scope

### In Scope

- `nop-ai/nop-ai-core`：新 `io.nop.ai.core.search.AiModelTextEmbedding`；pom 加 `nop-search-api` 依赖 + `nop-search-lucene` **test scope** 依赖（wiring/e2e 测试需要引擎类与 `search-defaults.beans.xml` 上 test classpath；无模块环——nop-search-lucene 不依赖 nop-ai-core）；`ai-defaults.beans.xml` 注册 bean。
- `nop-search/nop-search-lucene`：`LuceneSearchEngine.setTextEmbedding` 加 `@Inject` + `@Nullable`（含 jakarta annotation import）。
- 测试：nop-ai-core 单测（委托/转换/批量/fail-loud）+ 容器级 wiring + 引擎级 e2e（stub IEmbeddingModel）。
- 文档：`ai-dev/design/nop-ai/embedding.md` 增桥接节；`docs-for-ai/03-modules/nop-ai.md` 同步；`ai-dev/design/nop-code/search-integration-design.md` 同步 textEmbedding 可选注入语义；roadmap N4.2 todo→done。

### Out Of Scope

- nop-search-api/nop-search-core 的接口改动（SPI 零改动）。
- nop-code 各模块 pom 变更（桥接 bean 随 nop-ai-core 上 classpath，由部署组合决定——N4.1 已确立该装配哲学）。

## Execution Plan

### Phase 1 - 桥接实现与注入接线

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/search/AiModelTextEmbedding.java`（新）、`nop-ai/nop-ai-core/pom.xml`、`nop-search/nop-search-lucene/src/main/java/io/nop/search/lucene/LuceneSearchEngine.java`、`ai-defaults.beans.xml`

- Item Types: `Fix`

- [x] `nop-ai-core` pom 增加 `nop-search-api` 依赖（compile）与 `nop-search-lucene` 依赖（test scope，B1 审查修复：wiring/e2e 测试编译前提，无模块环）
- [x] `AiModelTextEmbedding`：构造/setter 注入 `IEmbeddingModel`（必填，null 拒绝）；`embed(text)` → `embedAllAsync(List.of(doc))` 取首项，`VectorData.getVector()` double[] → float[] 逐位转换；模型调用失败/返回 null vector → 异常上抛（不返回 null）；`embedAll(texts)` → **模型侧** `IEmbeddingModel.embedAllAsync` 单次批量委托（勿回调本接口默认 `embedAllAsync`——其默认实现包装 `embedAll`，会无限递归）；`getDimension()` → 委托模型首次成功结果的长度（不可得时 -1）
- [x] `LuceneSearchEngine.setTextEmbedding` 加 `@Inject` + `@Nullable`（jakarta.annotation），javadoc 注明"无实现 bean 时注入 null → hash 模拟兜底（仅测试语义），生产应装配嵌入实现"
- [x] `ai-defaults.beans.xml` 注册 `nopAiTextEmbedding`（`ioc:default="true"` + `ioc:type=ITextEmbedding`，`embeddingModel` ref `nopAiEmbeddingModel`）

Exit Criteria:

- [x] `./mvnw compile -pl nop-ai/nop-ai-core,nop-search/nop-search-lucene -am -T 1C -o` 通过
- [x] 无静默跳过：嵌入失败上抛异常，无 null placeholder / 吞异常
- [x] No owner-doc update required（Phase 2 统一覆盖）；`ai-dev/logs/` 执行日条目可随 Phase 2 一并写入（Phase 2 Exit Criteria 显式包含）

### Phase 2 - 测试（单测 + wiring + 引擎 e2e）与文档

Status: completed
Targets: `nop-ai-core/src/test/java/io/nop/ai/core/search/`（新）、`ai-dev/design/nop-ai/embedding.md`、`docs-for-ai/03-modules/nop-ai.md`、`ai-dev/design/nop-code/search-integration-design.md`、`ai-dev/backlog/nop-code-feature-completion-roadmap.md`

- Item Types: `Fix`、`Proof`

- [x] 单测（stub `IEmbeddingModel`）：embed 委托 + double→float 转换值断言；embedAll 批量单次委托（断言调用次数=1）；null vector → 异常；模型抛异常 → 上抛；构造 null 模型拒绝
- [x] wiring 测试（容器级，最小 beans 集：`search-defaults.beans.xml` + `ai-defaults.beans.xml` + http client；stub `IEmbeddingModel` 经**测试 beans 文件以 `ioc:allow-override="true"` 同 id 覆盖 `nopAiEmbeddingModel`**——NopIoC 硬性校验重复 bean 定义，先例 nop-task-ext test-reliability.beans.xml）：容器内 `LuceneSearchEngine` 的 `textEmbedding` 字段为 `AiModelTextEmbedding` 实例且持有所述 stub 模型（字段无 getter，经反射断言；Rule #23 接线验证）
- [x] 引擎 e2e：**必须以 `@NopTestProperty(name="nop.search.index-dir", value="./target/...")` 覆盖 indexDir**（N4.1 实证陷阱：默认 `/nop/search/indices` 以 `/` 开头解析到文件系统根，建目录失败 `ERR_LUCENE_OPEN_INDEX_FAIL`；先例 `TestVectorSearch`）；`SearchRequest` 显式设 `limit`（默认 0 → kNN 的 k=0 行为不可靠）；`addDocs`（2 文档，`autoGenerateEmbedding=true`，stub 模型返回可区分向量）→ `search(SearchType.VECTOR, 文本 query)` → stub 模型对 query 的向量命中预期文档（断言命中 + stub 的 embed 调用计数 ≥ 文档数+1）
- [x] 零回归断言：无 `ITextEmbedding` bean 的容器中 `LuceneSearchEngine.textEmbedding` 为 null 且 TEXT 搜索正常（既有 hash 路径不受 @Inject 可选化影响）——以既有 nop-search-lucene 测试全绿承载
- [x] `ai-dev/design/nop-ai/embedding.md` 增「ITextEmbedding 桥接」节（类名、bean 名、失败语义、维度契约、可选注入语义）
- [x] `docs-for-ai/03-modules/nop-ai.md` Embedding 节补桥接一句；`ai-dev/design/nop-code/search-integration-design.md`（搜索集成设计权威）同步 textEmbedding 可选注入语义与桥接 bean 落位
- [x] roadmap N4.2 todo→done + 汇总计数更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 执行日条目更新（当日 `ai-dev/logs/2026/MM-DD.md`）

Exit Criteria:

- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-search/nop-search-lucene -am -T 1C -o` 全绿（含新增测试）
- [x] **接线验证**：wiring 测试断言引擎字段持有所述桥接实例（非仅类型存在）
- [x] **端到端验证**：引擎 e2e 从 `addDocs`（自动嵌入）到 `VECTOR` 查询命中完整走通
- [x] roadmap/owner docs 已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] N4.2 交付词逐项：`ITextEmbedding` 生产实现（全仓 0→1）、后端接 nop-ai `IEmbeddingModel`、实现 + 测试齐备
- [x] `scan-hollow-implementations --module nop-ai/nop-ai-core --severity high`：K1/N4.2 新增代码零 high finding（pre-existing 豁免如实记录）
- [x] `check-plan-checklist --strict` 退出码 0
- [x] `check-doc-links --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 完成并记录证据（含 Anti-Hollow）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-search/nop-search-lucene -am -T 1C -o` 全绿
- [x] 代码风格核查：import 分组（io.nop.* → jakarta/third-party → java.*）、4 空格缩进、与同文件既有注释密度一致

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- `ITextEmbedding.embedAsync/embedAllAsync` 在桥接类中已覆盖为模型侧真异步委托（`.thenApply` 链）；剩余优化空间仅在大批量场景的分片并发调度（当前索引/查询调用方均为同步语义，无实际需求）。Classification: `optimization candidate`。

## Closure

Status Note: N4.2 全部交付面（ITextEmbedding 全仓 0→1 生产实现、IEmbeddingModel 桥接、引擎可选注入接线、端到端 VECTOR 路径）落地且有 focused 测试。独立 closure audit（agent_202e19e0，2026-09-28）裁定 APPROVE（有条件）——全部技术 Gate PASS、Anti-Hollow 抽查通过（无空壳/无静默 no-op/零回归）；两项流程条件（evidence 回填本节、完成 commit）已随即执行。审计发现 1 项 Minor（follow-up 文本过时——异步覆盖实际已实现）已顺手修正。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_202e19e0-e228-40d6-8cb0-8a06cf2b3f9a（fresh session，与本会话实现者无关）
- Evidence:
  - Phase 1 Exit Criteria 3/3 PASS（compile 全 reactor SUCCESS；fail-loud 路径 L31-33/40-43/100-104 + 4 个单测钉住）
  - Phase 2 Exit Criteria 4/4 PASS（焦点 9/9 复跑绿 + 双模块 `-am -T 1C` 全绿；wiring 反射断言整链实例级连通 + assertSame；e2e addDocs→VECTOR rank-1 双向验证；4 处 owner docs 同步 + roadmap done 23·todo 16）
  - Closure Gates 8/8 技术 PASS：交付词 0→1（全仓 grep `implements ITextEmbedding` 唯一命中）；scan-hollow 唯一命中为 pre-existing `NoOpProviderFailoverQueue.java:34`（f6dcf2239c，2026-08-15 W2 裁定豁免，非 K1/N4.2 新增）；check-plan-checklist exit 0；check-doc-links exit 0；nop-search-lucene 18 tests 零回归；风格核查 PASS
  - Anti-Hollow 检查：委托真实（递归陷阱有单测断言 embedAllCalls=1/embedCalls=0）、转换真实（逐位断言）、注入整链连通（容器反射 + assertSame）、端到端 VECTOR rank-1
  - Deferred 项分类检查：Deferred 为空；唯一 follow-up 为 optimization candidate，无 in-scope defect 降级（audit 确认）

Follow-up:

- 大批量嵌入的分片并发调度优化（当前调用方均为同步语义，无实际需求；见 Non-Blocking Follow-ups）。no remaining plan-owned work。
