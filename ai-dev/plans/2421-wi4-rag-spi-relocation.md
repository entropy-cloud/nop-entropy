# 2421 WI4 RAG SPI 归位 nop-ai-rag（vectorstore/classifier/search 迁移 + embedding 留守裁定）

> Plan Status: completed
> Last Reviewed: 2026-10-11（round 1 对抗审查 2 Blocker + 1 Major + 5 Minor 修复；round 2 复核仅剩 1 行计数修复后可执行）
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI4）
> Related: 2414/2419（WI1/WI2 已完成）；deps: WI1（已完成）

## Purpose

把 RAG 域的 SPI 与实现归位到 nop-ai-rag：vectorstore 契约族、向量数学工具、classifier 族、search 桥接迁入 rag，core 的 search/ 目录清空；同 plan 裁定 roadmap 点名的三个「随迁或保留」面（splitter / AiDocument / VectorData）与两个 roadmap 未点名的归属问题（embedding 接口、classifier 族），使 rag→core 依赖面收敛且无分层倒置。

## Current Baseline

（2026-10-11 执行时 HEAD 实测，Explore 子代理普查 + 主会话复核。）

- **迁移候选清单**：`core/api/vectorstore/` 4 文件 441 行（IVectorStore 103——抽象类非 interface、VectorStoreResult 24、VectorStoreOptions 143、VectorQueryBean 171）；`core/api/embedding/` 4 文件 192 行（IEmbeddingModel 35、EmbeddingOptions 50、CosineSimilarity 84、RelevanceScore 23）；core/search/AiModelTextEmbedding.java（已迁移至 nop-ai-rag，同包名）113 行（search/ 目录唯一 main 文件）；`core/api/classifier/` 6 文件 302 行（roadmap 未点名，scope 裁定项）。
- **消费者普查（import 口径，排除 core 自身与 target/_tmp/_dump）**：vectorstore 族 main 消费者仅 rag 5 文件；test 仅 rag 5 文件（与 roadmap「5 main + 5 test」一致）。CosineSimilarity/RelevanceScore 的唯一逻辑消费者是 classifier（`EmbeddingModelBasedClassifier.java:21,24` **import 级**依赖）。classifier 族 core 外零消费者、零测试。AiModelTextEmbedding 的 bean 定义在 core `ai-defaults.beans.xml:18`（nopAiTextEmbedding，ioc:type=io.nop.search.api.ITextEmbedding，constructor-arg ref=nopAiEmbeddingModel）；两 bean（含 :12 nopAiEmbeddingModel=EmbeddingServiceImpl）的 id 引用全部是 core 自身测试 + `nop-code-service` 测试 beans 的一处 javadoc 提及（其 stub 以 primary 压制 nopAiTextEmbedding）。
- **依赖方向硬约束（roadmap 原方案的排除性证据）**：`EmbeddingServiceImpl`（core/service/，446 行，:77 implements IEmbeddingModel）重度依赖 core LLM 内核（dialect/model/reliability/AiCoreConfigs/NopAiCoreErrors 10 个错误码）——roadmap 未点名迁移它；若 IEmbeddingModel 迁 rag，则 core 的 Impl 编译需要 rag → **core→rag 分层倒置**。故 embedding 接口 + Impl 必须同留 core（LLM 内核输出 SPI，与 ChatServiceImpl 同域），这是本 plan 对 roadmap 迁移清单的核心偏差裁定。
- **防误报复核**：`nop-code-service TestCodeSearchHybridVector.java:78-84` 为 javadoc 提及（无代码依赖）；`nop-search-lucene LuceneSearchEngine.java:152-156` 为 javadoc（代码仅 import nop-search-api 的 ITextEmbedding，:160-163 @Inject @Nullable by-type 可选注入，无 bean 时回退 hash 模拟）。`_tmp/nop-head-p2417-check/` 下的 .tool.json 等为并行会话 scratch，非源。
- **rag 现状**：pom 仅依赖 nop-ai-core（compile，注释自证「仅依赖 SPI 契约」）+ postgresql(test/optional) + junit；main 7 文件（Errors/BizModel/Ingest/Search/Synthesize/InMemoryVectorStore/PgVectorStore）；资源 `rag-defaults.beans.xml`（nopAiRagVectorStore=InMemoryVectorStore，ioc:type 引用 io.nop.ai.core.api.vectorstore.IVectorStore FQ 名）+ autoconfig 单行；README 使用约束「只允许实现 nop-ai-core 的 SPI 契约」需随迁移修订。测试 7 文件 847 行：PgVector E2E（`TestPgVectorIntegration.java:22` @EnabledIfEnvironmentVariable NOP_TEST_PGVECTOR_JDBC_URL，非 Docker 检测，环境变量缺失整类跳过）；rag 测试全部用内嵌 stub 自实现 IEmbeddingModel，不触碰 Impl/桥接。
- **rag 对 core 的当前依赖 9 类**：AiDocument、IEmbeddingModel、VectorData、IVectorStore、VectorQueryBean、VectorStoreOptions、VectorStoreResult、SimpleTextSplitter、IAiTextSplitter（+经方法签名/继承链触达 EmbeddingOptions/Metadata）。
- **splitter 保留证据（roadmap 裁定点）**：core 外真实消费者——`nop-ai-code-analyzer JavaFileSplitter.java:6-7`（main）、coder 2 个 task-xml XPL 运行时 import（ai-file-summary:47、ai-code-summary:48）、translate task-xml:74（MarkdownTextSplitter）+ translate 测试。迁 rag 会迫使 coder/skills（底层模块）依赖 rag（末端功能模块）——分层倒置，保留 core。
- **AiDocument/VectorData 保留证据（roadmap 裁定点）**：AiDocument 消费者 = IEmbeddingModel（留 core）+ EmbeddingServiceImpl（留 core）+ classifier（本 plan 随迁——但其迁移后经 rag→core 合法引用）；VectorData 消费者 = IVectorStore（随迁，rag→core 合法引用）+ Impl（留 core）+ classifier。二者是 embedding SPI 的数据载体，与留 core 的 IEmbeddingModel 同进退；VectorData extends Metadata（support 族整体不动）。
- **support/Media**：Media 零业务消费者（仅 `TestNopAiCoreErrorsContract.java` 错误码契约引用）——本 plan 不动，登记 WI12 顺带评估。
- **包名裁定基础**：迁移后 core 对 vectorstore/classifier/search 三包零引用（grep 实证）；rag 内部 10 文件 + rag-defaults beans 1 处 ioc:type 字符串 + README 是全部波及。保包名（io.nop.ai.core.api.* 不变）= 零 API 破坏、零 import 改动（迁移集内部相互引用不变）、beans 字符串不变；改包名 = 纯组织学收益 + 公共 API 破坏。裁定**保包名**，登记「模块归属变更、包名不变」+ 重启条件（未来 major 版本 API 清理时一并改，动因含消除将产生的全仓首个跨模块 split package：io.nop.ai.core.api.embedding 将跨 core/rag 两 jar——round 1 审查 A1，classpath 模式下无技术风险）。
- **测试面**：随迁测试 = `core/src/test/.../api/embedding/TestCosineSimilarityAndRelevanceScore.java`（79 行）+ `core/src/test/.../search/` 4 文件（TestAiModelTextEmbedding 111、TestTextEmbeddingWiring 75、TestAiTextEmbeddingVectorSearchE2E 118、StubEmbeddingModel 57——后两者依赖 nop-search-lucene，rag pom 需补 test-scope 依赖）。留 core 测试 = TestEmbeddingServiceImpl 328 + TestEmbeddingModelBeanAssembly 67 + test-embed*.llm.xml（原位不动；n42-test-embedding.beans.xml 随测试迁 rag——见迁移清单）。
- **纯 core 部署行为登记**：nopAiTextEmbedding bean 定义随类迁 rag beans 后，仅部署 core+search-lucene 而无 rag 的部署将失去 ITextEmbedding 生产实现（LuceneSearchEngine 回退 hash 模拟——文档化回退路径）；nop-code-service 测试 javadoc 的「core 的 nopAiTextEmbedding」措辞需同步。

## Goals

- rag 获得 RAG 域完整垂直：vectorstore SPI 4 文件 + 数学工具 2 文件 + classifier 族 6 文件 + search 桥接 + InMemory/PgVector 双实现（原有）+ 迁移测试 5 文件。
- core 的 search/ 目录清空、api/vectorstore 与 api/classifier 目录清空；core 保留 embedding 接口 + Impl（LLM 内核输出 SPI，依赖方向排除性证据见 Baseline）。
- rag→core 依赖面从 9 类收敛到 6 类：IEmbeddingModel、EmbeddingOptions（经 IEmbeddingModel 方法签名触达）、AiDocument、VectorData（+support 继承链）、splitter 2 类、NopAiCoreErrors（桥接类静态 import ERR_AI_CORE_INVALID_ARGUMENT，round 1 审查 M3；WI9 错误码归位时顺带复核归属）——全部有留守裁定依据。
- 包名不变（io.nop.ai.core.api.vectorstore/classifier、io.nop.ai.core.search 随模块迁移），零公共 API 破坏；「模块归属变更、包名不变」登记重启条件。

## Non-Goals

- 不迁 EmbeddingServiceImpl（LLM 内核关注点，M2 里程碑「core 仅剩 LLM 内核关注点」的正向符合；roadmap 迁移清单亦未点名）。
- 不迁 IEmbeddingModel/EmbeddingOptions（core→rag 倒置排除，见 Baseline）。
- 不迁 splitter/AiDocument/VectorData/support 族（各有留守证据）。
- 不动 nop-search-lucene/nop-search-api（依赖方向已正确）。
- 不改 rag 的 PgVector 实现语义与 E2E 门控。

## Scope

### In Scope

- nop-ai-core：删除 api/vectorstore/、api/classifier/、search/ 三目录，ai-defaults.beans.xml 移除 nopAiTextEmbedding bean（nopAiEmbeddingModel 保留），相关测试迁出。
- nop-ai-rag：接收迁移文件（包名不变）、rag-defaults.beans.xml 增加 nopAiTextEmbedding bean 定义、pom 补 test-scope nop-search-lucene、README 约束文本修订。
- nop-code-service：测试 beans javadoc 措辞同步（bean 来源 core→rag）。
- owner docs：`ai-dev/design/nop-ai/vector-store.md`（如存在）与 `docs-for-ai` 相关锚点、迁移说明。

### Out Of Scope

- PgVectorStore/InMemoryVectorStore 实现语义、splitter/AiDocument/VectorData/support、nop-search-* 模块、EmbeddingServiceImpl 及其测试。

## Execution Plan

### Phase 1 - 迁移落地

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/`、`nop-ai/nop-ai-core/src/test/resources/_vfs/nop/ai/beans/`、`nop-ai/nop-ai-rag/`

- Item Types: `Fix`

- [x] git mv 迁移 13 个 main 文件到 nop-ai-rag（保包名；round 1 审查 B2：13 = vectorstore 4 + 数学工具 2 + classifier 6 + search 1）：api/vectorstore/ 4、api/embedding/CosineSimilarity + RelevanceScore、api/classifier/ 6、search/AiModelTextEmbedding；core 侧三目录清空（embedding/ 仅剩 IEmbeddingModel + EmbeddingOptions）
- [x] git mv 迁移 5 个测试文件 + 1 个测试资源：TestCosineSimilarityAndRelevanceScore + search/ 4 文件（TestAiModelTextEmbedding、TestTextEmbeddingWiring、TestAiTextEmbeddingVectorSearchE2E、StubEmbeddingModel）+ `core/src/test/resources/_vfs/nop/ai/beans/n42-test-embedding.beans.xml` → rag test resources（round 1 审查 B1：其唯二消费者与全部 class 引用均在迁移集，core test resources 对 rag test classpath 不可见）；rag pom 补 test-scope nop-search-lucene 与显式 test-scope nop-search-api（审查 A4：实际经 core 传递已可得，显式声明防漂移）
- [x] beans 迁移：ai-defaults.beans.xml 删除 nopAiTextEmbedding bean（含 N4.2/K1 注释中桥接部分；nopAiEmbeddingModel 保留）；rag-defaults.beans.xml 增加 nopAiTextEmbedding bean 定义（class FQ 名不变，constructor-arg ref=nopAiEmbeddingModel——跨模块 ref 经 autoconfig 合并解析）；确认 rag pom compile 依赖 nop-ai-core 使 ref 目标在运行时可见
- [x] rag README 约束文本修订（SPI 契约现位于本模块 io.nop.ai.core.api.vectorstore——登记包名保留裁定）；`nop-code/nop-code-service/src/test/resources/_vfs/nop/test/beans/test-text-embedding.beans.xml` javadoc 措辞同步（bean 现由 rag 提供）
- [x] 编译 + 测试：`./mvnw test -pl nop-ai/nop-ai-rag,nop-ai/nop-ai-core -am`（rag 含 PgVector Sql/RowMapping 测试；E2E 环境变量门控不变）

Exit Criteria:

- [x] core 的 api/vectorstore/、api/classifier/、search/ 目录不存在；rag 含全部迁移文件（13 main + 5 test + 1 测试资源）
- [x] rag→core import 面收敛：rag 源码 import io.nop.ai.core.* 仅剩 IEmbeddingModel/EmbeddingOptions/AiDocument/VectorData/Metadata 及继承链/splitter 族/NopAiCoreErrors（round 1 审查 M3 允许清单，grep 留档）
- [x] core 侧零 rag 引用（grep io.nop.ai.rag 零命中——依赖方向单向）
- [x] owner-doc 更新于本 plan Phase 2 内完成（guide Rule 17）；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 回写与迁移说明

Status: completed
Targets: `ai-dev/design/nop-ai/vector-store.md`、`docs-for-ai/03-modules/nop-ai.md`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Decision` + `Proof`

- [x] 迁移说明（公共 API 面，Protected Areas）：模块归属变更清单（包名不变裁定 + 重启条件）、embedding 留守裁定依据（依赖方向排除性证据）、纯 core 部署行为登记（ITextEmbedding 回退 hash 模拟）
- [x] `docs-for-ai/03-modules/nop-ai.md` K1/N4.2 相关节（:147 Embedding 客户端节等）核对更新；`docs-for-ai/01-repo-map/module-groups.md` nop-ai-rag 描述核对；`docs-for-ai/04-reference/source-anchors.md` 受影响锚点核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三处 doc 更新在位且与 live code 一致；迁移说明显式声明 roadmap WI4 子清单「core/api/embedding/（IEmbeddingModel 等 4 文件）」被本 plan 偏差裁定部分豁免（IEmbeddingModel/EmbeddingOptions 留守），供 WI21 roadmap 收口审计对照
- [x] check-doc-links --strict 0 error

## Closure Gates

- [x] 13 main + 5 test + 1 测试资源（n42 beans）迁移完成，core 三目录清空
- [x] rag→core import 面收敛（6 类，含 splitter 2 类）且 core→rag 零引用
- [x] 不存在被静默降级的 in-scope live defect；纯 core 部署行为偏差已登记（非 defect——文档化回退路径）
- [x] owner docs 已同步 + 迁移说明在位
- [x] 独立子 agent closure-audit 已完成并记录证据（初判 REJECT 单点阻断→补修→转 APPROVE，见 Closure Audit Evidence）
- [x] Anti-Hollow Check：nopAiTextEmbedding bean 在 rag 装配后可解析（TestTextEmbeddingWiring 迁入后仍绿即证据）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-ai/nop-ai-rag,nop-ai/nop-ai-core -am`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-rag --severity high` 无新增 high 发现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2421-wi4-rag-spi-relocation.md --strict` 退出码 0（自指验证）

## Deferred But Adjudicated

### IEmbeddingModel/EmbeddingOptions 留守 core（roadmap 迁移清单偏差）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 依赖方向排除性证据（EmbeddingServiceImpl implements IEmbeddingModel 且重度依赖 core LLM 内核；接口迁 rag 即 core→rag 倒置）。若未来 embedding 客户端与 LLM 内核解耦（如 Impl 下沉独立模块），可重开裁定。
- Successor Required: `no`
- Successor Path: 无（登记于 owner doc 重启条件）

### Media（support 族）零消费者处置

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅错误码契约测试引用，属 core API 卫生而非 RAG 归位议题。
- Successor Required: `no`
- Successor Path: roadmap WI12 顺带评估

## Non-Blocking Follow-ups

- rag README 的包名保留裁定与重启条件（未来 major API 清理时改 io.nop.ai.rag.*）。
- `ai-dev/design/nop-ai/vector-store.md:57` §四「复用 nop-ai-core CosineSimilarity」旧锚点确定性存在（round 1 审查 B9/M7），Phase 2 一并改写。

## Closure

Status Note: WI4 两 Phase 完成：13 main + 5 test + 1 测试资源迁入 rag（保包名），core 三目录清空，nopAiTextEmbedding bean 随迁（纯 core 部署回退 hash 已登记），rag→core 收敛至 6 类 + splitter、core→rag 零引用；IEmbeddingModel/EmbeddingOptions 留守裁定（依赖方向排除性证据）+ 三个 roadmap 裁定点全部登记。closure audit 初判 REJECT 的唯一 Blocker（README 修订静默失配——python replace 无匹配不报错）已补修并经复核点确认。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11；初判 REJECT 单点阻断，补修后转 APPROVE）
- Evidence:
  - A 迁移完成度 PASS：三目录 ABSENT、19 个 git mv（13 main + 5 test + 1 资源）全 R 态、core 保留面/beans/pom 逐项核对
  - B 依赖方向 PASS：core→rag 零命中；rag→core 跨模块引用全集甄别（含 static import 专项）无超清单命中
  - C 测试 PASS：12 测试文件在位、RowMapping IoC 禁用配置、StubHttpClient fail-loud 三件套在位；core 442 / rag 48（1 skipped）绿留档
  - D owner docs：初判 README Blocker（replace 静默失配）已补修——SPI 迁入表述 + 包名保留裁定 + 重启条件 + 留守裁定全部在位；javadoc 文件名词（ai-defaults→rag-defaults）一并修正；check-doc-links --strict exit 0
  - scan-hollow（nop-ai-rag, severity high）exit 0 留档（签核时实跑）
  - E 文本一致性 PASS：Phase 全 completed、Deferred 两项、豁免声明、log 条目（三处执行修正）在位

Follow-up:

- deepwiki/rag 的 StubHttpClient 模式三处复制（deepwiki/rag/code-web）——WI7/WI12 收敛候选
- bridge 类静态 import core 错误码（ERR_AI_CORE_INVALID_ARGUMENT 等）归属复核归 WI9
- （承袭）Media 零消费者 → WI12 顺带评估
