# 02 K2 向量库后端驱动（pgvector + InMemory 参考实现）

> Plan Status: draft
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/knowledge-rag-roadmap.md` K2；用户 2026-09-28 指示（knowledge-rag 前置纳入执行队列）；K1 已完成（`ai-dev/plans/knowledge-rag/01-k1-embedding-api-client.md`）；`ai-dev/design/nop-ai/04-rag-module-position.md`（实现落点裁定——第一个真实 RAG 消费方 K3 已在队列，触发"InMemory 或存储后端实现"迁移条件）
> Related: K3（下游消费方）；nop-code N7.2（K3 下游）

## Purpose

落地 knowledge-rag K2：实现 `IVectorStore` SPI 的生产后端驱动——**pgvector（JDBC）**为主实现，辅以 **InMemoryVectorStore** 参考实现（K3 RAG 管线测试与无 pgvector 环境的确定性验证落点；04-rag-module-position §四的迁移触发条件"出现第一个真实消费方"已由 K3 达成，解除原"拒绝 InMemory"裁定的前提）。落点 = `nop-ai-rag`（04-rag-module-position 裁定的实现落点，首次启用该空模块）。Owner doc `ai-dev/design/nop-ai/vector-store.md` 新建。

## Current Baseline

- `IVectorStore<T extends VectorData>`（nop-ai-core，abstract class，MA4.5-002 保留 I 前缀）：`store(List, options)` / `delete(ids, options)` / `update(List, options)` / `search(query, options)` 四抽象方法；单条变体有 default 委托。全仓零实现（SPI 裁定 + 04-rag-module-position reserved 登记，K1 触发条件已由 audit §七登记解除 IEmbeddingModel 侧；IVectorStore 侧由本计划解除）。
- `VectorStoreOptions`：collectionName/indexName/partitionNames/embeddingOptions/**tenantId**（租户隔离命名空间的落点）；`VectorStoreResult`：ids 列表；`VectorQueryBean extends VectorData`：text/maxResults/minScore/withVector/condition(TreeBean)/outputFields。
- `VectorData`：double[] vector + Metadata（id 经 Metadata？——VectorData 无 id 字段，**id 约定 = Metadata key `id`**，由本计划定义并写入 owner doc）。
- nop-ai-rag：空占位模块（仅 pom + README，P3-MA3-003 保留）；nop-ai/pom.xml modules 含它。
- JDBC 设施：平台 `nop-dao` 提供DataSource 抽象，但 nop-ai-rag 保持"只实现 nop-ai-core SPI"约束（README 使用约束）——**驱动用裸 `javax.sql.DataSource` + java.sql**（集成方注入其平台 DataSource），不引入 nop-dao 耦合。
- pgvector 访问协议：`CREATE EXTENSION IF NOT EXISTS vector`；列 `embedding vector`（无 typmod，对齐 A3）；相似度 `embedding <=> ?::vector`（cosine distance）；向量字面量 `'[1,2,3]'` 经参数传递。（schema 完整裁定见下方 Adjudications A2——baseline 此处仅协议示意。）
- 无 pgvector 的环境：H2 等内存库不支持 vector 类型——**pgvector 集成测试须门控**（env `NOP_TEST_PGVECTOR_JDBC_URL` 存在才跑）；单元测试用 SQL 生成断言 + InMemory 实现行为测试。

## Adjudications（审查 B1/B2/M1/M3 裁定——决定类签名与 schema）

- **A1（search 语义）**：v1 要求 `query.vector` 必填——**vector 缺失即 fail-loud**（text 仅为 K3 语义预留，v1 不参与检索依据）错误码 `ERR_AI_RAG_QUERY_VECTOR_REQUIRED`；text→embedding 转换归 K3 管线（检索前 embed）。此裁定**偏离** `VectorQueryBean` javadoc 的"store 自动转换文本"表述——偏离记录于 owner doc，并在两个实现的 javadoc 注明。`withVector=true`/`outputVector`：结果 VectorData 始终带向量（内存实现天然；pgvector SELECT 含 embedding 列），`withVector=false` 时 v1 忽略（结果仍带）——owner doc 登记。
- **A2（租户隔离）**：pgvector schema = 复合唯一键 `UNIQUE(tenant_id, id)`（tenant_id text NOT NULL，空租户统一写 `"_default_"` 哨兵值），store upsert ON CONFLICT(tenant_id,id)、update/delete/search 全带 `tenant_id = ?` 谓词；InMemory 同语义（tenant+collection 组合键）。NULL tenantId 统一归一为 `_default_`。
- **A3（dim 来源）**：`PgVectorStore` 构造注入 `dimension`（int，>=1 校验）；ensureCollection 在**首次 store/search 前**惰性执行（volatile 标志）；DDL 用无 typmod 的 `vector` 列（pgvector 单列维度由插入值决定，dim 参数用于校验插入向量长度 fail-loud）。
- **A4（condition）**：v1 两实现对 `query.condition` 均**忽略**——InMemory 静默忽略，PgVector 记 WARN（查询不崩）；用例各一。owner doc 登记为方言扩展点。

## Goals

- G1：`InMemoryVectorStore`（nop-ai-rag）：线程安全（ConcurrentHashMap 按 collection 分桶）、cosine 相似度、maxResults/minScore、tenantId 隔离（tenant + collection 组合键）、delete/update/search 全语义——K3 与本计划测试的确定性后端。
- G2：`PgVectorStore`（nop-ai-rag）：JDBC 驱动，ensureSchema（DDL 幂等）/store(upsert ON CONFLICT)/update/delete/search（`<=>` cosine，LIMIT/minScore 过滤）/tenant_id 列隔离；向量字面量序列化（`[1,2,3]`）；DataSource 注入；SQL 只经 PreparedStatement 参数化（标识符白名单化防注入）。
- G3：owner doc `ai-dev/design/nop-ai/vector-store.md`（NEW）：id 约定、租户隔离语义、pgvector DDL、InMemory 定位（K3 测试后端 + 触发条件已达成说明）、方言扩展点。
- G4：测试：InMemory 全行为 + PgVector SQL 生成单测（fake Connection 捕获 SQL/参数）+ 门控 pgvector 集成测试（env 存在才跑）。

## Non-Goals

- Milvus 驱动（roadmap 备选，无当前环境）；HNSW/IVFFlat 索引调优（DDL 留扩展点）；K3 管线本身（另立 plan）；nop-dao/ORM 耦合（README 约束）；metadata TreeBean condition 的 SQL 下推与内存谓词求值（A4：v1 两实现均忽略——InMemory 静默、PgVector WARN）。

## Scope

### In Scope

- `nop-ai/nop-ai-rag`：pom 增依赖（nop-ai-core + test junit）；`InMemoryVectorStore`/`PgVectorStore`/pgvector SQL 常量；测试。
- `ai-dev/design/nop-ai/vector-store.md`（NEW）；`docs-for-ai/03-modules/nop-ai.md` 增向量库节；knowledge-rag roadmap K2 todo→done。

### Out Of Scope

- nop-dao/ORM/migration 模块改动；pgvector 服务器环境搭建；Milvus。

## Execution Plan

### Phase 1 - InMemoryVectorStore + PgVectorStore

Status: planned
Targets: `nop-ai/nop-ai-rag/pom.xml`、`src/main/java/io/nop/ai/rag/vector/InMemoryVectorStore.java`、`PgVectorStore.java`

- Item Types: `Fix`

- [ ] pom：nop-ai-core 依赖 + junit-jupiter test
- [ ] `InMemoryVectorStore`：collection+tenant 组合键分桶；store 生成 id（Metadata 无 id 时 UUID）并回写；cosine 相似度复用 nop-ai-core `CosineSimilarity`（前置守卫：零向量 fail-loud——比 utility 的 EPSILON 归零语义更严，裁定登记 owner doc）解除其 reserved；search 排序/maxResults/minScore；delete/update 按 ids
- [ ] `PgVectorStore`：构造注入 `DataSource` + dimension（A3）+ 默认 collection；`ensureCollection` 惰性幂等 DDL（CREATE TABLE IF NOT EXISTS：id text NOT NULL、embedding vector、content text、metadata jsonb、tenant_id text NOT NULL、UNIQUE(tenant_id,id)）；store = INSERT .. ON CONFLICT(tenant_id,id) DO UPDATE；update 同 store；delete/update/search 全带 `tenant_id = ?` 谓词（A2，空租户归一 `_default_`）；search = `WHERE tenant_id = ? AND (1 - (embedding <=> ?::vector)) >= ? ORDER BY embedding <=> ?::vector LIMIT ?`；向量字面量经参数传递；标识符白名单 `[a-zA-Z_][a-zA-Z0-9_]*` fail-loud；query.vector 缺失 fail-loud（A1）；condition 记 WARN（A4）
- [ ] 错误码载体：nop-ai-rag 新建 `NopAiRagErrors`（`ErrorCode.define` 内联模式）+ `ERR_AI_RAG_QUERY_VECTOR_REQUIRED`（A1）；id 约定：`vectorData.getMetadata("id")` 读取，缺失时 store 自动生成并回写 Metadata

Exit Criteria:
- [ ] `./mvnw compile -pl nop-ai/nop-ai-rag -am -o` 通过
- [ ] 无静默跳过：零向量 fail-loud；PgVector 构造注入默认 collection（缺省= fail-loud 构造校验），InMemory 不设默认（每次调用必须带 collectionName，缺失 fail-loud）——两实现缺省语义差异写入 owner doc；SQLException 上抛不吞
- [ ] No owner-doc update required（Phase 2 统一）

- [ ] 测试（与实现同 Phase）：
  - InMemory：store/search 排序与 topK/minScore 过滤/update 后 search 反映/delete 后不再命中/tenant 隔离互不可见/零向量 fail-loud/id 回写 Metadata/单条 default 委托（store(T)/update(T)）/condition 静默忽略（A4）/withVector 语义（A1）
  - PgVector SQL 单测（JDK 动态代理 fake Connection 捕获 SQL/参数）：DDL 幂等、upsert ON CONFLICT(tenant_id,id)、search `<=>`+LIMIT+tenant 谓词、向量字面量参数化、标识符校验拒绝非法名、query.vector 缺失 fail-loud、condition WARN
  - pgvector 集成测试：`@EnabledIfEnvironmentVariable(named = "NOP_TEST_PGVECTOR_JDBC_URL")` 门控（pom 加 test+optional 的 org.postgresql:postgresql 驱动——test scope 不进传递依赖；本环境缺省 skip 为显式门控非静默）
- [ ] `./mvnw test -pl nop-ai/nop-ai-rag -am -T 1C` 全绿（集成测试无 env 时显式 skip）
- [ ] **端到端验证**：InMemoryVectorStore store→search→update→delete 全生命周期走通（K3 可直接消费）
- [ ] 无静默跳过：fail-loud 路径全覆盖
- [ ] `ai-dev/logs/` 条目已更新

### Phase 2 - owner doc + 文档同步 + roadmap

Status: planned
Targets: `ai-dev/design/nop-ai/vector-store.md`（NEW）、`nop-ai/nop-ai-rag/README.md`、`04-rag-module-position.md`、`docs-for-ai/03-modules/nop-ai.md`、roadmap、vectorstore 族 javadoc

- Item Types: `Fix`

- [ ] owner doc `vector-store.md`：SPI 契约→两实现映射表、A1-A4 裁定全文、id 约定、租户隔离契约（A2）、pgvector DDL/操作符、InMemory 定位（K3 触发达成 + P3-MA3-003 两理由的解除论证——投机代码前提解除 + **不注册 default bean 保 SPI 边界**，M6 裁定）、condition 忽略裁定（A4）、`nop-db-migration` 偏离裁定（运行时幂等 DDL 替代，m8）、方言扩展点（Milvus）
- [ ] `nop-ai-rag/README.md` 重写（空占位描述失效 + 修正 `io.nop.ai.core.api.embedding.IVectorStore` 笔误为 `api.vectorstore`）
- [ ] `04-rag-module-position.md` 新增 §八 K2 触发登记（reserved 解除：VectorData/VectorStoreOptions/VectorQueryBean/VectorStoreResult/CosineSimilarity）
- [ ] vectorstore 族类头部 RESERVED javadoc 修订（K1 对 IEmbeddingModel 同款）
- [ ] `docs-for-ai/03-modules/nop-ai.md` 向量库节 + nop-ai-rag 模块表行同步
- [ ] knowledge-rag roadmap：K2 todo→ready（本次草案审查通过后），**todo→done 移至 closure audit 后**（§8 状态纪律）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`ai-dev/logs/` 条目更新

Exit Criteria:

- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [ ] 四处 reserved/javadoc/README 同步齐备
- [ ] roadmap K2 = ready（done 留待 closure）
- [ ] `ai-dev/logs/` 条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] K2 交付词逐项：pgvector 驱动（upsert/search/delete + tenantId 隔离命名空间）+ InMemory 参考实现 + 测试
- [ ] `scan-hollow-implementations --module nop-ai/nop-ai-rag --severity high` 零 high finding
- [ ] `check-plan-checklist --strict` 对本 plan 退出码 0
- [ ] `check-doc-links --strict` 退出码 0
- [ ] 代码风格：import 分组、4 空格
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证两实现的调用链连通（端到端测试断言）+ 无空方法体/静默跳过
- [ ] `./mvnw compile -pl nop-ai/nop-ai-rag -am -o` 通过
- [ ] `./mvnw test -pl nop-ai/nop-ai-rag -am -T 1C` 全绿（新 test 依赖首次解析，不用 -o）

## Deferred But Adjudicated

### pgvector 实库集成验证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 本环境无 pgvector 服务器；驱动 SQL 经 fake-Connection 单测钉住，实库验证由门控集成测试承载（env 提供即跑）。
- Successor Required: `no`（门控测试即 successor 载体）

## Non-Blocking Follow-ups

- Milvus 驱动、HNSW 索引选项、metadata condition 下推（optimization candidate）。

## Closure

Status Note: （closure 时填写）
Completed: （closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: （closure 时填写）
- Evidence: （closure 时填写）

Follow-up:

- （closure 时填写）
