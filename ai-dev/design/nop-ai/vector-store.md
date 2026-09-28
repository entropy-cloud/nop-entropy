# 向量库后端驱动设计（K2）

**日期**：2026-09-28
**范围**：`nop-ai/nop-ai-rag`（`InMemoryVectorStore` + `PgVectorStore`）
**状态**：active（K2 已落地）
**来源**：`ai-dev/backlog/knowledge-rag-roadmap.md` K2；执行计划 `ai-dev/plans/knowledge-rag/02-k2-vector-store-driver.md`
**相关裁定**：P1-MA5-003 / P3-MA3-003（SPI 契约与落点，`04-rag-module-position.md` §四/§七/§八）

---

## 一、设计结论

**裁定 = `IVectorStore` SPI 双实现落于 nop-ai-rag：`PgVectorStore`（JDBC 驱动，生产后端）+ `InMemoryVectorStore`（参考实现，K3 管线测试与无 pgvector 环境开发后端）。两实现均不注册 IoC default bean——装配归 K3 plan（保 SPI 边界语义）。**

1. **落点 = nop-ai-rag**（04-rag-module-position §四裁定）：K3（RAG 管线）是队列内第一个真实消费方，触发"InMemory 或存储后端实现"迁移条件；原"拒绝 InMemory"裁定的两个前提——零消费方投机代码、InMemory 掩盖 SPI 边界——分别由"K3 触发达成"与"不注册 default bean"解除（§八 登记生效后）。
2. **仅依赖 nop-ai-core SPI 契约 + JDK**（javax.sql.DataSource JDK 内置；README 使用约束保持——不引入 nop-dao 耦合）；pgvector JDBC 驱动为 test+optional scope（门控集成测试专用，不进传递依赖）。
3. **id 约定**：`vectorData.getMetadata("id")` 为向量唯一标识（tenant 内唯一）；缺失时 store 自动生成 UUID 并回写 Metadata。

## 二、A1-A4 契约裁定（决定类签名与 schema）

| 裁定 | 内容 |
|------|------|
| A1 search 语义 | `query.vector` 必填——缺失即 fail-loud `ERR_AI_RAG_QUERY_VECTOR_REQUIRED`（nop-ai-rag `NopAiRagErrors`）。text→embedding 转换归 K3 管线（检索前 embed）。**偏离** `VectorQueryBean` javadoc 的"store 自动转换文本"表述——集成方注意。`withVector`/`outputVector`：结果 VectorData 始终带向量（v1 忽略关闭请求——pgvector SELECT 恒含 embedding 列，InMemory 天然） |
| A2 租户隔离 | pgvector：`tenant_id text NOT NULL`（空租户归一 `_default_` 哨兵）+ `UNIQUE(tenant_id, id)` 复合唯一；store upsert `ON CONFLICT(tenant_id,id)`；delete/update/search 全带 `tenant_id = ?` 谓词。InMemory：tenant+collection 组合键。两实现语义对称：跨租户不可见/不可删 |
| A3 dimension | 构造注入 `dimension`（>=1 校验），惰性 ensureCollection；DDL 用无 typmod 的 `vector` 列；dim 用于插入向量长度校验 fail-loud（mismatch 抛错） |
| A4 condition | v1 两实现均忽略 `query.condition` TreeBean——InMemory 静默忽略（测试后端不引日志噪声），PgVector 记 WARN（查询不崩）。下推为方言扩展点 |

## 三、pgvector schema 与操作符

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS <collection> (
    id text NOT NULL,
    embedding vector,
    content text,
    metadata jsonb,
    tenant_id text NOT NULL,
    UNIQUE(tenant_id, id)
);
-- store (upsert)
INSERT INTO <collection> (id, embedding, content, metadata, tenant_id)
VALUES (?, ?::vector, ?, ?::jsonb, ?)
ON CONFLICT(tenant_id, id) DO UPDATE SET ...;
-- search (cosine distance)
SELECT embedding FROM <collection>
WHERE tenant_id = ? AND (1 - (embedding <=> ?::vector)) >= ?
ORDER BY embedding <=> ?::vector LIMIT ?;
```

- collection 名（= 表名）白名单 `[a-zA-Z_][a-zA-Z0-9_]*`，fail-loud（表名拼接不可参数化）。
- `nop-db-migration` 偏离裁定：运行时幂等 DDL 替代 migration 表项（CREATE TABLE IF NOT EXISTS 语义等价且免部署耦合）；生产部署如需 migration 纳管可在 K13 调整。
- pgvector 驱动（org.postgresql:postgresql）为 test+optional scope，集成方生产部署需自带驱动依赖。

## 四、InMemory 定位

- 消费方：K3 管线测试、本计划测试、无 pgvector 环境开发。
- 复用 nop-ai-core `CosineSimilarity`（其 reserved 解除登记于 §八），前置零向量守卫 fail-loud（比 utility 的 EPSILON 归零更严）。
- collectionName 必填（每次调用 fail-loud，与 PgVector 的构造期默认 collection 不同——差异登记于此）。

## 五、拒绝的替代方案

- **默认注册 IoC bean**：两实现均不注册——保 SPI 边界（P3-MA3-003 第二理由），K3 显式装配。
- **`id text PRIMARY KEY` 全局唯一 schema**：跨租户覆盖/删除漏洞，改复合唯一。
- **引入 nop-dao**：README 使用约束禁止，保持 SPI 纯度。
- **condition SQL 下推 v1**：TreeBean 谓词求值引擎工作量一个数量级，v1 忽略 + 扩展点登记。

## 六、与消费方的关系

- **K3**：摄取-检索管线的向量存取即本计划两实现；管线测试用 InMemory。
- **nop-code N7.2**：GraphRAG 集成契约裁定的 K3 管线经此获得向量存取。
- 集成方生产部署：配 pgvector + JDBC 驱动 + 显式构造装配。
