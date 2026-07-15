# 2 nop-metadata 血缘采集 + 图遍历查询

> Plan Status: active
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-5）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6 数据血缘；`ai-dev/design/nop-metadata/04-data-governance.md` §2.4 血缘治理
> Mission: nop-metadata
> Work Item: P2-5 — 血缘采集（MetaLineageEdge 填充机制 + 向上追溯/向下追踪/影响分析/路径查找）
> Related: `2026-07-16-0225-2-nop-metadata-external-table-metadata-sync.md`（外部表字段 A2 JSON → 本 plan 裁定列级血缘范围）、`2026-07-16-0225-3-nop-metadata-manifest-snapshot.md`（其 Deferred 项"血缘依赖作为依赖图来源"指定 P2-5 为 successor）

## Purpose

让 nop-metadata 的血缘从"只有 CRUD 表结构"推进到"可采集、可遍历、可做影响分析"。具体：提供 MetaLineageEdge 的**填充机制**（manual 手工录入 + sql_parse 从 SQL 视图自动抽取表级血缘），以及架构基线 §2.6 列出的**图遍历查询**（向上追溯 / 向下追踪 / 影响分析 / 路径查找）。本 plan 使血缘成为可用的治理能力，并收口 P2-2 Deferred（外部表字段级单独寻址）与 P2-3 Deferred（血缘作为依赖图来源）的归属裁定。

## Current Baseline

- **NopMetaLineageEdge 实体已建模**（`nop-metadata/model/nop-metadata.orm.xml:1357-1416`）：列 `lineageEdgeId`(PK) / `sourceTableId`(mandatory) / `targetTableId`(mandatory) / `sourceColumn`(nullable) / `targetColumn`(nullable) / `transformType`(dict `meta/lineage-transform`：`direct`/`derived`/`aggregated`) / `transformExpr` / `lineageSource`(dict `meta/lineage-source`：`manual`/`sql_parse`/`open_lineage`/`hook`) / `pipelineId`(→NopMetaPipeline, nullable) / `confidence`(double) / `extConfig` + 审计。索引 `IX_NOP_META_LINEAGE_SOURCE`(sourceTableId) / `IX_NOP_META_LINEAGE_TARGET`(targetTableId)。**无 to-one 关系**——sourceTableId/targetTableId 是 plain string（存 NopMetaTable.metaTableId），无 ORM relation，遍历时按列值查询。
- **NopMetaPipeline 实体已建模**（`orm.xml:1293-1351`）：`pipelineId`(PK) / `metaModuleId`(mandatory) / `pipelineName` / `pipelineType`(dict `meta/pipeline-type`：`etl`/`sql`/`api`/`manual`) / `sourceSql`(mediumtext) / `schedule` / `extConfig` + 审计。to-one metaModule。**无 outputTable 列**——pipeline 产出表未在模型中固定。
- **BizModel 现状**：`NopMetaLineageEdgeBizModel`、`NopMetaPipelineBizModel` 存在，仅 CrudBizModel 自动生成的 findPage/findList/get/save/delete。**无任何自定义 action**——无填充机制、无遍历查询。
- **NopMetaTable 现状**：`tableType` 含 `entity`/`sql`/`external`。`sql` 类型表有 `sourceSql`（SQL 文本）+ `buildSql`，可作为 sql_parse 的输入（视图消费源表 → 产出自身）。`external` 类型表列结构存 `buildSql` JSON（A2 方案，P2-2），**列无单独寻址实体**。
- **设计契约**：`01-architecture-baseline.md` §2.6 定义血缘边模型 + 四类遍历查询（向上追溯/向下追踪/影响分析/路径查找）。`04-data-governance.md` §2.4 定义治理用途。dict `meta/lineage-source`/`meta/lineage-transform` 已存在。
- **SQL 解析能力（Decision 待定）**：sql_parse 需从 SQL 文本抽取源表引用（FROM/JOIN 子句中的表名）。平台的 SQL AST（`SqlFrom`/`SqlJoinTableSource`/`SqlTableName` 含 `name`+`owner` schema 前缀）位于 **`nop-orm-eql`**（`nop-persistence/nop-orm-eql/.../eql/ast/`），**不是 `nop-dao`**。**关键依赖缺口**：`nop-metadata-service` → `nop-metadata-dao` → `nop-orm`，**未依赖 `nop-orm-eql`**，且 `nop-orm-eql` 通常依赖 `nop-orm`（反向），故该解析器**不在当前 classpath**。此外 EQL 解析器可能与 ORM session 绑定（解析表名到实体），不一定支持对任意用户 SQL 的纯语法解析。**列级 SQL 血缘（解析 SELECT 列映射）复杂度极高，首版范围与解析器选型（含依赖可用性 + 纯语法 vs session 绑定验证）为硬前置门禁（item 1.1）**。
- **Deferred 项归属（需本 plan 裁定）**：
  1. P2-2 Deferred "外部表字段级单独寻址（血缘/质量引用）"——external 表列以 JSON 存 buildSql，无字段实体。列级血缘如何引用 external 列？
  2. P2-3 Deferred "血缘依赖作为依赖图来源"（Successor: P2-5）——Manifest parentMap/childMap 是否纳入血缘边？
- **测试基建**：Nop AutoTest 可用。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（P2-3 后 25 tests 全绿）。

## Goals

- `recordLineage` GraphQL mutation action 可用：手工录入一条或多条 MetaLineageEdge（支持表级 + 列级，sourceColumn/targetColumn 可选），`lineageSource=manual`
- `extractLineageFromSql` GraphQL mutation action 可用：对 `tableType=sql` 的 NopMetaTable，解析其 `sourceSql`，从 FROM/JOIN 子句抽取源表引用 → 匹配目录中 NopMetaTable → 创建**表级**血缘边（`lineageSource=sql_parse`，target=该 sql 表自身）
- 四类图遍历查询 action 可用，基于 MetaLineageEdge 边做 BFS：
  - `getUpstream(metaTableId)` 向上追溯（给定目标表，所有上游源表）
  - `getDownstream(metaTableId)` 向下追踪（给定源表，所有下游消费者）
  - `getLineagePath(sourceTableId, targetTableId)` 路径查找（两表之间血缘路径）
  - `getImpactAnalysis(metaTableId, columnName?)` 影响分析（列级变更影响范围；有列级边用列级，否则回退表级）
- 抽取/遍历对"未解析到目录表"的引用不静默丢弃（显式标记 unresolved，对齐 Manifest D4 dangling 策略）

## Non-Goals

- 外部表结构同步（P2-2，前置）/ Catalog 运行时统计（P2-4，并行）
- 质量规则执行（P2-6）/ 数据剖析（P2-7）
- **列级 SQL 血缘解析**（解析 SELECT 列→源列映射，复杂度极高，首版 sql_parse 仅表级；列级随手动录入或后续增量）
- **open_lineage / hook 血缘来源**（外部集成，首版仅 manual + sql_parse；其余 dict 值保留但无自动填充，显式标为未实现）
- **Pipeline sourceSql 自动抽取**（pipeline 无 outputTable 列，目标端歧义；首版 pipeline 仅通过手动录入边的 `pipelineId` 字段引用，pipeline 自动抽取为 follow-up）
- 血缘写入 Manifest 依赖图（P2-3 Deferred 项，本 plan 裁定为 non-blocking follow-up，见 Deferred But Adjudicated）
- **getLineagePath 返回全部简单路径**（循环图中成本/复杂度过高，首版仅单条最短 BFS）
- 定时自动抽取调度（首版手动 action）

## Design Decisions

> D1/D2 为硬前置门禁，须在 item 1.1 裁定并写入 `01-architecture-baseline.md` §2.6 后实现。

### D1. sql_parse 范围 + 解析器选型（待 item 1.1 裁定）

- **范围**：首版 sql_parse **仅表级**——从 `NopMetaTable.sourceSql`（tableType=sql）的 FROM/JOIN 子句抽取源表引用，匹配目录 `NopMetaTable.tableName`，创建表级边（sourceColumn/targetColumn 留空）。target = 该 sql 表自身。
- **列级 SQL 解析**：明确**不在首版**（解析 SELECT 列→源列映射复杂度极高且易错）。列级血缘首版仅由 `recordLineage` 手工录入支持。
- **解析器选型**（item 1.1 研究）：优先复用平台已有 SQL 解析能力（**`nop-orm-eql` 的 SQL AST，但需验证**：(a) 是否可作为依赖引入 `nop-metadata-service`（当前未依赖）；(b) 是否支持对**任意用户 SQL 的纯语法解析**（非 ORM session 绑定）——若 session 绑定则不可用于解析未导入的外部 SQL）；若 `nop-orm-eql` 不可用或不适合纯语法解析，则用**文档化的语用 tokenizer**（识别 `FROM <table>` / `JOIN <table>` / 含/不含 schema 前缀），并在设计文档**显式记录其限制**（不解析 CTE 别名展开、子查询别名、动态 SQL；未匹配到目录表的引用记 unresolved）。解析器选型裁定写入 §2.6。
- **幂等**：重复对同一 sql 表抽取，按 `(sourceTableId, targetTableId, lineageSource='sql_parse')` 去重 upsert（更新而非无限追加），对齐 P2-2 幂等模式。

### D2. lineageSource 支持范围 + 列级血缘对 external 表的裁定

- **首版支持**：`manual`（recordLineage）+ `sql_parse`（extractLineageFromSql，表级）。`open_lineage`/`hook` 保留 dict 值但首版**无自动填充入口**；若通过 recordLineage 手工指定这两个来源，允许（不拒绝），但无专用 action。
- **external 表列级血缘裁定**（收口 P2-2 Deferred）：external 表列以 JSON 存 buildSql（A2，无字段实体）。列级血缘对 external 列**以列名字符串引用**（sourceColumn/targetColumn 存列名），**不引入** external 字段实体（避免 ORM 结构变更 Protected Area）。即血缘边的列引用是"软引用"（列名字符串），不强制 FK 到字段实体——这对所有 tableType 一致（entity 列虽可关联 MetaEntityField，但血缘边不建硬 FK）。结论写入 `01-architecture-baseline.md` §2.6。
- **未解析引用 dangling 策略**（不得静默丢弃）：sql_parse 抽取的表引用若匹配不到目录表，**该引用放入返回 `unresolved: [...]` 列表（原表名显式保留）+ edge 暂不创建**。**注：`sourceTableId` 为 `mandatory="true"`（`orm.xml:1366`），ORM 层无法创建 null-source 边，故 dangling 一律进 unresolved 列表、不建悬空边**——此约束由 schema 决定，非可选。日志记录 unresolved 计数；不静默跳过、不丢引用名。item 1.1 最终确认。

### D3. 图遍历语义

- 遍历基于 MetaLineageEdge 边的有向图：边方向 `source → target`（数据从 source 流向 target）。
- `getUpstream(T)`：反向 BFS，收集所有能到达 T 的 source（T 的上游）。
- `getDownstream(S)`：正向 BFS，收集所有 S 能到达的 target（S 的下游）。
- `getLineagePath(S, T)`：S 到 T 的**单条最短路径**（BFS 最短路径，含 visited 环检测防死循环）。返回全部简单路径为 Non-Goal（循环图中"全部路径"成本/复杂度过高，首版不做）。无路径返回空（显式空，不报错）。
- `getImpactAnalysis(T, col?)`：变更影响 = T 的下游；若提供 col 且存在列级边（sourceColumn/targetColumn），按列过滤；否则回退表级（该表所有下游表）。
- **环检测**：BFS 用 visited 集合防环（血缘图可能含环），不静默死循环。

## Scope

### In Scope

- `NopMetaLineageEdgeBizModel`：新增 `recordLineage`（manual）+ `extractLineageFromSql`（sql_parse，表级）+ 四类遍历查询 action
- sql_parse 源表抽取器（按 D1 选型）
- 幂等 upsert（sql_parse 按 `(sourceTableId, targetTableId, lineageSource)` 去重）
- AutoTest：手工录入 + sql_parse 抽取 + 四类遍历 + unresolved 标记 + 幂等

### Out Of Scope

- P2-2/P2-4（前置/并行）
- 列级 SQL 解析（首版仅表级 sql_parse）
- pipeline 自动抽取（pipeline 无 outputTable，歧义）
- open_lineage/hook 自动填充（首版无专用 action）
- 血缘写入 Manifest（non-blocking follow-up）
- P2-6/P2-7（质量/剖析）

## Execution Plan

### Phase 1 - 血缘填充机制（recordLineage manual + extractLineageFromSql 表级）

Status: planned
Targets: `nop-metadata/nop-metadata-service/.../entity/NopMetaLineageEdgeBizModel.java`、新增 SQL 源表抽取器、`TestNopMetaLineageEdgeBizModel.java`

- Item Types: `Decision`（D1 sql_parse 范围/解析器选型 + D2 lineageSource 范围/external 列级裁定，硬前置）+ `Proof`（新功能：血缘填充）

> **硬前置门禁（item 1.1）**：D1/D2 必须先裁定（只裁定不写代码）——尤其 SQL 解析器选型与列级/external 裁定，写入设计文档后再实现填充逻辑。

- [ ] 1.1 **sql_parse 范围 + 解析器选型 + external 列级 + dangling 决策（硬前置门禁，Decision only）**：基于 live repo 核查并裁定 D1/D2——研究平台可用 SQL 解析能力：**验证 `nop-orm-eql` 的 SQL AST（`SqlFrom`/`SqlJoinTableSource`/`SqlTableName`）能否作为依赖引入 `nop-metadata-service`，以及是否支持对任意用户 SQL 的纯语法解析（非 ORM session 绑定）**；裁定 sql_parse 表级范围 + 解析器（复用 `nop-orm-eql` AST or 文档化 tokenizer，含限制说明 + 依赖裁定）；裁定 external 列级血缘软引用（列名字符串，不引实体）；裁定 dangling 策略（**注：`sourceTableId` 为 `mandatory="true"`（`orm.xml:1366`），无法创建 null-source 边，故 dangling 一律进返回 unresolved 列表、不建边**；不静默丢）；裁定 getLineagePath 返回形态（**默认单条最短路径 BFS**，全部简单路径为 Non-Goal 以控制复杂度）。**只裁定不写代码**。结论写入 `01-architecture-baseline.md` §2.6（sql_parse 范围/解析器/限制 + lineageSource 支持范围 + external 列级软引用 + 遍历语义/环检测 + dangling 由 mandatory 约束强制）
- [ ] 1.2 新增 SQL 源表抽取器：输入 sql 文本 → 按 D1 选型抽取 FROM/JOIN 表引用（含/不含 schema 前缀）→ 返回表名列表（在 BizModel 内匹配目录 NopMetaTable.tableName）。抽取器无目录依赖（纯文本→表名），匹配在 BizModel 层做
- [ ] 1.3 在 `NopMetaLineageEdgeBizModel` 新增 `@BizMutation recordLineage(...)`：录入一条或多条 MetaLineageEdge（支持表级 + 列级，sourceColumn/targetColumn 可选），`lineageSource=manual`（或调用方指定 open_lineage/hook 但首版无专用 action）。sourceTableId/targetTableId 校验存在（不存在抛 inline ErrorCode，不静默建悬空边）
- [ ] 1.4 在 BizModel 新增 `@BizMutation extractLineageFromSql(@Name("metaTableId") String id, IServiceContext context)` → 返回 `Map{extractedEdgeCount: int, unresolved: [...], errors: [...]}`：加载 tableType=sql 的 NopMetaTable → 抽取器解析 sourceSql → 匹配目录表 → 按 `(sourceTableId, targetTableId, lineageSource='sql_parse')` 幂等 upsert 表级边（target=该 sql 表自身，sourceColumn/targetColumn 空）→ 未匹配表名进 unresolved（不丢、不静默）→ 返回结果。非 sql 类型表抛 inline ErrorCode（快速失败，不静默）
- [ ] 1.5 错误码按现有模式在 BizModel 内 inline 定义（参考 `NopMetaModuleBizModel`/`NopMetaDataSourceBizModel` inline ErrorCode 用法）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。

- [ ] D1/D2 决策已裁定并写入 §2.6；解析器选型与限制在设计文档显式记录
- [ ] `recordLineage` 可通过 GraphQL mutation 调用，录入表级 + 列级边并可在 `NopMetaLineageEdge__findPage` 查到
- [ ] `extractLineageFromSql` 对 tableType=sql 表抽取后，目录中生成表级边（target=该 sql 表，sourceColumn/targetColumn 空，lineageSource=sql_parse）
- [ ] 重复抽取同一 sql 表不重复追加（按 `(sourceTableId, targetTableId, lineageSource)` 幂等）
- [ ] sourceSql 中引用但目录无对应表的表名，显式出现在返回 `unresolved` 列表（不静默丢弃）
- [ ] sourceTableId/targetTableId 不存在（recordLineage）/ 非 sql 类型表（extractLineageFromSql）：显式失败，不静默
- [ ] **接线验证**：extractLineageFromSql 运行时确实调用了 SQL 抽取器并匹配目录表写入边（返回 extractedEdgeCount>0 + edge 可查证明），非空壳
- [ ] **无静默跳过**：未匹配引用进 unresolved；不存在 ID/非 sql 类型显式失败；无空方法体或吞异常
- [ ] **新功能测试**：新增测试覆盖 recordLineage（表级+列级）+ extractLineageFromSql 抽取 + 幂等 + unresolved 标记 + 非 sql 类型/不存在 ID 显式失败，全绿
- [ ] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6（sql_parse 范围/解析器/限制 + lineageSource 支持范围 + external 列级软引用 + dangling 策略）已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 图遍历查询（向上追溯 / 向下追踪 / 路径查找 / 影响分析）

Status: planned
Targets: `NopMetaLineageEdgeBizModel.java`（新增查询 action）、`TestNopMetaLineageEdgeBizModel.java`

- Item Types: `Proof`（新功能：图遍历查询）

- [ ] 2.1 在 BizModel 新增 `@BizQuery getUpstream(@Name("metaTableId") String id)`：反向 BFS（沿 targetTableId→sourceTableId）收集所有上游源表，返回表级列表（含层数/路径可选）。含 visited 环检测
- [ ] 2.2 在 BizModel 新增 `@BizQuery getDownstream(@Name("metaTableId") String id)`：正向 BFS（沿 sourceTableId→targetTableId）收集所有下游表。含 visited 环检测
- [ ] 2.3 在 BizModel 新增 `@BizQuery getLineagePath(@Name("sourceTableId") String s, @Name("targetTableId") String t)`：返回 S→T **单条最短路径**（BFS 最短路径，按 D1/item 1.1 裁定），BFS + visited 环检测；无路径返回空（显式空，不静默报错）
- [ ] 2.4 在 BizModel 新增 `@BizQuery getImpactAnalysis(@Name("metaTableId") String id, @Name("columnName") String col)`：变更影响 = 该表下游；若提供 col 且存在列级边按列过滤，否则回退表级（所有下游表）。返回受影响表/列清单

Exit Criteria:

- [ ] 四类查询 action 可通过 GraphQL query 调用
- [ ] `getUpstream`/`getDownstream` 对多跳血缘图返回正确的传递闭包（BFS 正确性）
- [ ] `getLineagePath` 返回 S→T 路径；无路径返回显式空（不报错）；含环图不死循环（visited 生效）
- [ ] `getImpactAnalysis` 有列级边时按列过滤、无列级边时回退表级（回退逻辑正确，不静默返回空当存在表级下游时）
- [ ] **端到端验证**：从 `recordLineage`/`extractLineageFromSql` 建边 → `getUpstream`/`getDownstream`/`getLineagePath`/`getImpactAnalysis` 返回正确结果的完整路径已验证（建一条 A→B→C 链，断言 getDownstream(A) 含 B,C；getUpstream(C) 含 A,B；getLineagePath(A,C) 含路径）
- [ ] **接线验证**：遍历查询运行时确实查询了 MetaLineageEdge（多跳结果证明），非空壳
- [ ] **无静默跳过**：无路径显式空；环图不死循环；无空方法体
- [ ] **新功能测试**：新增测试覆盖 多跳 upstream/downstream + 路径查找（含无路径 + 环图不死循环）+ 影响分析（列级过滤 + 表级回退），全绿
- [ ] No owner-doc update required（遍历语义/环检测/路径形态在 Phase 1 item 1.1 已写入 `01-architecture-baseline.md` §2.6；Phase 2 仅实现，不新增设计契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + 每个 Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] 血缘填充可用（manual recordLineage + 表级 sql_parse extractLineageFromSql）
- [ ] 四类图遍历查询可用且语义正确（upstream/downstream/path/impact）
- [ ] 幂等成立（sql_parse 按 `(sourceTableId, targetTableId, lineageSource)` 去重）
- [ ] unresolved/dangling 不静默丢弃；不存在 ID/非 sql 类型显式失败
- [ ] 环图不死循环（visited 生效）
- [ ] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [ ] 必要 focused verification 已完成（填充 + 幂等 + unresolved + 四类遍历测试全绿）
- [ ] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.6 sql_parse 范围/解析器/限制 + lineageSource 支持 + external 列级软引用 + 遍历语义）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证填充 action 运行时确实抽取/写入边、遍历 query 运行时确实查询 MetaLineageEdge 多跳（端到端连通）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0420-2-nop-metadata-lineage-collection-and-traversal.md --strict` 退出码 0

## Deferred But Adjudicated

### 列级 SQL 血缘解析（SELECT 列→源列映射）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 列级 SQL 解析复杂度极高且易错；首版表级 sql_parse + 手工列级录入（recordLineage）已满足"血缘可采集可遍历"的核心结果面。列级自动解析为后续增量，不阻塞
- Successor Required: yes（列级 sql_parse 作为后续 plan；P2-3 Deferred "列级随 P3 SQL 视图解析"）

### 血缘写入 Manifest 依赖图（P2-3 Deferred successor）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 设计 `05-metadata-import.md` §1.3 明确 Catalog 与 Manifest 独立；同理血缘（物理数据流）与 Manifest（逻辑元数据快照）独立。本 plan 的遍历查询（getUpstream 等）**直接读 MetaLineageEdge**，不依赖 Manifest parentMap/childMap。Manifest 是否纳入血缘边是"依赖图来源扩展"，不影响血缘采集与遍历结果面成立
- **继承关系裁定**：本项覆盖 P2-3（`2026-07-16-0225-3:142`）的 `Successor Required: yes（血缘随 P2-5）` 期望，**降级为 watch-only**。理由：roadmap P2-5 结果面不含 Manifest 集成，遍历直接读 MetaLineageEdge，Manifest 与血缘独立。原 successor=yes 记录于此显式对齐，便于审计追溯
- Successor Required: no（可作为 generateManifest 的可选来源扩展，non-blocking）

### Pipeline sourceSql 自动抽取

- Classification: `optimization candidate`
- Why Not Blocking Closure: pipeline 无 outputTable 列，自动抽取的目标端歧义（无法确定产出表）。首版 pipeline 通过手动录入边的 `pipelineId` 字段引用即可。pipeline 自动抽取需先解决 outputTable 建模，超出本 plan 结果面
- Successor Required: no

### open_lineage / hook 血缘来源

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 这两者是外部集成（OpenLineage 协议消费、运行时 hook 注入），与平台内部血缘采集机制独立。首版 manual + sql_parse 已满足内部血缘结果面。dict 值保留，recordLineage 允许手工指定
- Successor Required: no

## Non-Blocking Follow-ups

- 定时自动抽取调度（当前手动 action）
- 血缘可视化数据结构（前端图渲染所需的 node/edge JSON 聚合 action）
- **sql_parse 陈旧边清理**：幂等 upsert 防重复追加，但若某源表从目录删除后再抽取会进入 unresolved，而旧 edge 仍残留（无 prune 步骤）。首版外部表稳定时非问题，列为本 follow-up
- **BFS 查询策略**：首版可一次性 `findAllByQuery` 全量 MetaLineageEdge 在内存建图遍历（边数小，元数据目录量级），避免 per-hop 查询过度设计
- Manifest 依赖图纳入血缘边 / Pipeline 自动抽取：归类与理由见 `Deferred But Adjudicated`（不在此重复裁定）

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应 live code path 或 test name）
  - 每条 Closure Gate 的验证结果
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果：<<填充+遍历端到端调用链追踪>>
  - `scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
  - Deferred 项分类检查：<<确认 P2-2/P2-3 Deferred 项已诚实裁定，无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up>>
