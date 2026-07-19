# 2026-07-18-1800-1 nop-metadata expression 型 Measure 列级血缘（实现）

> Plan Status: completed
> Last Reviewed: 2026-07-19
> Draft Review: R1 独立子 agent 对抗性审查（ses_08b68cebaffe9fAarsbOBuS51o）发现 1 Blocker[B1 plan 与 design D5 在 resolver 失败处理矛盾] + 3 Major[M1 D6 混入实现违反 design-first / M2 大小写未说明 / M3 replace 删条件模糊] + 7 Minor，全部修复——新增 Phase 0 design-only（D5 精细化分层 + D6 写入 + D4 en label 同步）+ §Scope 标注 case-insensitive + replace 条件改 source+target+lineageSource 三条件 + 测试 1 严格 ==4 + 测试 6 总边数澄清 + 测试 8 saveEntityTable helper 说明 + Phase 依赖显式 + Current Baseline 测试数澄清。
> R2 Review: 2026-07-19 独立 review pass。0 Blocker / 0 Major / 2 Minor（dangling §Risks 引用→重指向 Phase 0 D4 + Non-Blocking Follow-ups；Last Reviewed 推进至 2026-07-19）。修复后 promoted draft→active。
> Mission: nop-metadata
> Work Item: Opt-followup-impl. expression 型 Measure 输出列的列级血缘（实现）
> Source: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6.1（D1/D3/D4/D5 裁定）+ §2.6.2（D2 裁定）；plan `2026-07-18-1500-1`（design-first，`Deferred But Adjudicated` `Successor Required: yes`，Successor Path 即本 plan）；roadmap `Opt-followup-impl: planned`
> Related: `2026-07-18-1500-1`（design-first 前置，D1-D5 裁定来源）；`2026-07-18-1400-1`（Opt-3 expression 执行，`ValidatedExpression.identifiers` 来源）；`2026-07-16-0420-2`（P2-5 血缘采集，`extractColumnLineageFromSql` 先例）

## Purpose

把 roadmap 唯一 `planned` 项 `Opt-followup-impl` 推进到 `done`：依 design-first plan `2026-07-18-1500-1` 的 D1-D5 裁定，在 `NopMetaLineageEdgeBizModel` 实现 `extractMeasureLineage(metaTableId)` action——加载表的所有 `NopMetaTableMeasure` → 对每个 `expression != null` 的 measure 经 D5 契约提取列引用 → flat-collect 产自环边（D1+D3+D4）→ per-measure try/catch 失败隔离 → 用户可经 `NopMetaLineageEdge` 直接查询召回（D2，不进 BFS）。新增 dict `measure_parse` 值 + 端到端测试覆盖成功路径 / 召回路径 / 失败隔离 / BFS 非污染。

本 plan 是 **实现 plan**（非 design-first）：所有设计裁定已在前置 design-first plan 完成，本 plan 只落地代码 + 测试。

## Current Baseline

**已成立（live repo，本 plan 起草前核实）**：

- **D1-D5 design 裁定已写入 design doc**（plan 2026-07-18-1500-1 completed）：
  - D1（edge model）：自环边 `(sourceTableId=T, targetTableId=T, sourceColumn=C, targetColumn=measureName, transformType=aggregated, lineageSource=measure_parse)`；BFS 语义隔离——自环边在 `getDownstream`/`getImpactAnalysis` 永不可达（设计，非 bug），仅经直接边查询召回。
  - D2（召回）：仅经 `NopMetaLineageEdge` 直接查询（`__findPage` where `sourceTableId=T AND sourceColumn=C AND lineageSource=measure_parse`），不扩展 `getImpactAnalysis` public contract、不新增 measure-level API。
  - D3（flat-collect）：每识别列产一条边，`sourceColumn=识别列名`、`targetColumn=measureName`（偏离 §八 占位符建议，裁定已声明）。
  - D4（取值）：`lineageSource=measure_parse`（dict 新增值）/ `transformType=aggregated`（边界：`aggFunc` null 时 `derived`）。
  - D5（列引用提取契约）：复用 `ExpressionMeasureValidator.validateStatic(expression, ValidationOptions.saveTimeLoose(), metaTableId, measureName)` 返回的 `ValidatedExpression.identifiers`；归属校验经 `MetaTableFieldResolver.resolveFieldNames(table, fieldDao)` 与 `.identifiers` 比对；per-measure try/catch 失败隔离。
- **`ExpressionMeasureValidator`**（`nop-metadata-service/.../service/field/ExpressionMeasureValidator.java`，plan 1400-1 落地）：`validateStatic(String expression, ValidationOptions options, String metaTableId, String measureName)` 返回 `ValidatedExpression`（含 `public final Set<String> identifiers`，`saveTimeLoose()` 选项 `allowQualified=true/expectedColumns=null`，即接受裸列名 + `l.`/`r.` 限定名，不校验列存在性）。**可直接消费，无需新造分词方法。**
- **`MetaTableFieldResolver`**（`nop-metadata-service/.../service/field/MetaTableFieldResolver.java`）：无状态，`new MetaTableFieldResolver()` 即可用（同 BizModel 既有 `SqlSourceTableExtractor`/`SqlColumnLineageExtractor` 直接实例化模式）；`resolveFieldNames(NopMetaTable table, IEntityDao<NopMetaEntityField> fieldDao)` 按 tableType 分派返回该表自身可用字段名集合（entity/external/sql），失败显式抛 ErrorCode（baseEntityId null / buildSql 损坏 / sourceSql 不可解析）。
- **`NopMetaLineageEdgeBizModel`**（`nop-metadata-service/.../service/entity/NopMetaLineageEdgeBizModel.java`，635 行）：
  - `@BizModel("NopMetaLineageEdge")` extends `CrudBizModel<NopMetaLineageEdge>`。
  - 既有 `@BizMutation extractColumnLineageFromSql(metaTableId, context)`（`:222-283`）是**先例模式**：返回 `Map{extractedEdgeCount, unresolved, errors}`，errors 用 `List<Map<String,Object>>`（含 stage/error），unresolved 用 `List<String>`（描述串），`toErrorMessage(e)` helper 抽错误信息。
  - 既有 `upsertColumnSqlParseEdge(sourceTableId, targetTableId, sourceColumn, targetColumn, transformType)`（`:585-608`）是**幂等 upsert 先例**：按五元组 `(sourceTableId, targetTableId, sourceColumn, targetColumn, lineageSource='sql_parse')` 查重，存在更新 transformType / 不存在新建。
  - 既有 `bfsForward`（`:423-443`）/ `getDownstream`（`:321`）/ `getImpactAnalysis`（`:401`）：自环边 `tgt==start` 恒在 visited，永不可达——D1 BFS 隔离的 live 依据。
  - DAO 获取：`daoFor(NopMetaTable.class)` / `daoFor(NopMetaEntityField.class)`（CrudBizModel 内建）；measure DAO 须 `daoFor(NopMetaTableMeasure.class)`。
  - 已注入（直接 new，无状态）：`SqlSourceTableExtractor sqlExtractor`、`SqlColumnLineageExtractor columnExtractor`。**无 `MetaTableFieldResolver` 字段——本 plan 新增（同模式直接 new）。**
- **`NopMetaTableMeasure` ORM**（`nop-metadata/model/nop-metadata.orm.xml:1140-1170`）：列 `metaTableId`(mandatory,32) / `measureName`(mandatory,100) / `aggFunc`(nullable,30) / `expression`(nullable,1000)。按 `metaTableId` 加载该表所有 measure 用 `daoFor(NopMetaTableMeasure.class).findAllByQuery(filter metaTableId=T)`。
- **`_NopMetadataCoreConstants.java` 是生成文件**（`_` 前缀，`nop-metadata-core` 模块）：`LINEAGE_SOURCE_SQL_PARSE`/`LINEAGE_TRANSFORM_AGGREGATED` 等常量由 dict yaml 经 `nop-metadata-codegen` 生成。**禁止手编该文件**——本 plan 新增 `LINEAGE_SOURCE_MEASURE_PARSE` 常量经「改 dict 源 → `mvn install` 重新生成」获得。
- **dict `meta/lineage-source`**（`_vfs/dict/meta/lineage-source.dict.yaml`，`locale: zh-CN`）：现 4 值 manual/sql_parse/open_lineage/hook，**无 `measure_parse`**。
- **i18n live 模式**（`_vfs/i18n/en/_nop-metadata.i18n.yaml`）：仅翻译 dict **名** label（如 `meta/lineage-source: Lineage Source`），**不翻译 dict 选项 label**（manual/sql_parse/open_lineage/hook 无 en 选项翻译）。→ 本 plan D4「en label」按既有模式裁定：仅改 dict yaml（zh-CN 选项 label），不加 en 选项翻译（与既有 4 值一致），见 Phase 0 D4 en label 同步修订 + Non-Blocking Follow-ups。
- **build 状态**：`./mvnw test -pl nop-metadata -am` 当前全绿（plan 1400-1 落地后 434 tests → plan 1500-2 落地后新增测试至 454 tests；本 plan 以 454 为基线）。
- **e2e 测试先例**（`TestNopMetaLineageEdgeBizModel.java`，926 行）：`@NopTestConfig(localDb=true, initDatabaseSchema=TRUE)`，注入 `IGraphQLEngine`/`IDaoProvider`/BizModel；测试经 GraphQL mutation/queries + 直接 DAO 断言。

**剩余 gap（本 plan 实现）**：

- `extractMeasureLineage(metaTableId)` action 不存在（D1-D5 裁定无实现）
- dict `measure_parse` 值不存在（D4）
- 召回路径（D2 直接边查询）无可召回的 measure_parse 边（依赖 action 产出）
- 无端到端测试覆盖（成功 / 召回 / 失败隔离 / BFS 非污染 / 重抽幂等）

## Goals

- **G1** — dict `meta/lineage-source` 新增 `measure_parse` 值，经 codegen 重新生成 `LINEAGE_SOURCE_MEASURE_PARSE` 常量
- **G2** — `NopMetaLineageEdgeBizModel` 新增 `@BizMutation extractMeasureLineage(metaTableId)` action，依 D1-D5 裁定：加载 measure → validateStatic 取 identifiers → resolver 归属比对 → flat-collect 自环边 → per-measure try/catch 隔离 → 返回 `{extractedEdgeCount, unresolved, errors}`
- **G3** — 幂等保证：重抽同一表不产生重复边（五元组 `(sourceTableId, targetTableId, sourceColumn, targetColumn, lineageSource=measure_parse)` 去重）；expression 变更后旧列边不残留（replace 语义，见 Scope D6 裁定）
- **G4** — 端到端测试覆盖：成功路径（列→measure 边落盘 + 可查）/ 召回路径（直接边查询命中）/ per-measure 失败隔离（单 measure 失败不中断整批）/ BFS 非污染（`getDownstream(T)` 不返回 T 自身）/ dict 值生效（`measure_parse` 值可查）/ 重抽幂等
- **G5** — roadmap `Opt-followup-impl` 翻转为 `done`（closure audit 通过后）

## Non-Goals

- 不修改 D1-D4 design 裁定（design-first 已 completed）；**D5 精精细化 + D6 新增裁定 + D4 en label 同步**经本 plan Phase 0（design-only）写入 design doc，不在实现 Phase 临场改裁定；如实现发现其他裁定缺陷，回 design doc 经新 design plan，不在本 plan 临场改
- 不扩展 `getImpactAnalysis`/`getDownstream` public contract（D2 拒绝；自环边 BFS 不可达是设计）
- 不新增 measure-level 召回 API（如 `getMeasureImpact`，D2 拒绝首版；为 successor 评估项）
- 不实现 JOIN 上下文 expression 跨表血缘（`l.`/`r.` 限定列 → unresolved `join-context-deferred`，Non-Goal）
- 不实现 field-based Measure（非 expression 型）聚合血缘（follow-up）
- 不修改 `MetaLineageEdge` ORM 结构（复用既有列；D1 选定自环边避免结构变更）
- 不修改 `ExpressionMeasureValidator`/`MetaTableFieldResolver` 实现（复用既有 contract）
- 不手编 `_NopMetadataCoreConstants.java`（生成文件；经 dict 源 + `mvn install` 重新生成）

## Scope

### In Scope

- **D4 dict + 常量**：`lineage-source.dict.yaml` 追加 `measure_parse` 选项（label `指标表达式解析`）；`mvn install` 重新生成 `LINEAGE_SOURCE_MEASURE_PARSE` 常量。
- **D6 — 重抽语义裁定（本 plan 新增裁定，补 D1-D5 未覆盖的重抽幂等；Phase 0 写入 design doc）**：`extractMeasureLineage` 采用 **replace 语义**——action 开始时按 `(sourceTableId=T AND targetTableId=T AND lineageSource=measure_parse)` 删除该表所有既有 measure_parse 边，再从当前 measure 集合全量重插。理由：expression 可变，accumulate-only（沿用 sql_parse 的 upsert-only 不删）会使「expression 不再引用列 C」的旧 C→measure 边残留，导致召回返回错误的过时影响关系。replace 语义保证召回结果 = 当前 expression 的真实列依赖。删除条件同时指定 source+target：因 D1 限定自环边（source==target==T），二者当前等价，但同时指定可防止未来 D1 扩展（如 JOIN 上下文跨表 measure 边）时误删非自环 measure_parse 边。裁定写入 design doc §2.6.1（Phase 0 design-only）。
- **`extractMeasureLineage` action 行为契约（D1+D3+D4+D5+D6）**：
  1. 加载 `NopMetaTable T`（不存在显式失败 ErrorCode）
  2. 经 `MetaTableFieldResolver.resolveFieldNames(T, fieldDao)` 解析 T 自身可用字段集合（**per-table 一次**，非 per-measure；**resolver 失败属表级前置失败，直接抛 ErrorCode 中断 action**——见 Phase 0 D5 精细化裁定：resolver 是 per-table 调用，无法标 measureName，不属 per-measure 隔离范围；per-measure 隔离仅覆盖 validator.validateStatic 失败）
  3. replace：删除 T 的既有 measure_parse 边（D6 条件）
  4. 加载 T 的所有 `NopMetaTableMeasure`（filter `metaTableId=T`）
  5. 对每个 `expression != null` 的 measure，per-measure try/catch：
     - 调 `ExpressionMeasureValidator.validateStatic(expression, ValidationOptions.saveTimeLoose(), metaTableId, measureName)` 取 `ValidatedExpression.identifiers`
     - 对每个 identifier 与 T 字段集合比对（**case-insensitive**，对齐 `ExpressionMeasureValidator.containsIgnoreCase` 先例 + sql_parse 的 `tableName.toLowerCase()` 先例；实现可 lowercase 双方后比较）；属于字段集合的裸名 → 产 flat-collect 自环边（D1+D3）；`l.`/`r.` 限定名 → 进 unresolved（标 `join-context-deferred`）；裸名不在字段集合 → 进 unresolved（不伪造映射、不静默丢弃）
     - `transformType` = measure.aggFunc != null ? `aggregated` : `derived`（D4 边界裁定）
     - 产边 upsert（五元组去重，D6 replace 后首次插入）
     - validator 抛异常（unparseable/unsafe）→ 进 errors（标 measureName + 错误信息），该 measure 不产边，不中断整批（**per-measure 隔离仅此一处**）
  6. 返回 `Map{extractedEdgeCount: int, unresolved: List<String>, errors: List<Map<String,Object>>}`（对齐 `extractColumnLineageFromSql` 返回结构）
- **召回路径（D2）**：仅经既有 `NopMetaLineageEdge` GraphQL CRUD（`__findPage`）直接查询，无新 API、无 BFS 改动。action 产出 measure_parse 自环边后，用户调 `where sourceTableId=T AND sourceColumn=C AND lineageSource=measure_parse` 即可召回 `targetColumn`(=measureName) 集合。
- **端到端测试**（`TestNopMetaLineageEdgeBizModel.java` 追加）：覆盖 Goals G4 全部 6 场景。
- **roadmap 翻转** + design doc §2.6.1 追加 D6 replace 语义裁定 + daily log。

### Out Of Scope

- JOIN 上下文跨表血缘、field-based measure 血缘、measure-level 召回 API（见 Non-Goals）
- dict en 选项 label 翻译（Phase 0 D4 已裁定跟随既有模式，见 Non-Blocking Follow-ups）
- `_NopMetadataCoreConstants.java` / `_NopMetaLineageEdge.xmeta` 等生成文件手编（经源重新生成）

## Execution Plan

### Phase 0 - design doc 裁定同步（design-only，无代码变更）

Status: completed
Targets: `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.6.1

- Item Types: `Decision`

> 本 Phase 仅修订/追加 design doc 文字，不产出代码。目的：把本 plan 在实现前发现的「D5 精细化（resolver 失败分层）+ D6 新增（replace 语义）+ D4 en label 同步」作为 design 层裁定固化，避免实现 Phase 临场改 design（违反 Rule #14 + design-first 原则，R1 M1/B1）。

- [x] **D5 精细化裁定**：在 §2.6.1 D5 段落补充「失败处理分层」：(a) **表级前置失败**——`MetaTableFieldResolver.resolveFieldNames` 是 per-table 一次调用，其在 baseEntityId null / buildSql 损坏 / sourceSql 不可解析 时抛 ErrorCode 属表级前置失败，直接中断 action（不产任何边），**不属 per-measure 隔离范围**（无法标 measureName）；(b) **per-measure 隔离**——仅覆盖 `ExpressionMeasureValidator.validateStatic` 失败（unparseable/unsafe），单 measure 失败进 errors 不中断整批。原 D5「successor 实现须以 per-measure try/catch 隔离」语义细化为仅 (b) 层。
- [x] **D6 新增裁定**：在 §2.6.1 追加 D6 段落（replace 语义，见 §Scope D6 完整理由）：action 开始按 `(sourceTableId=T AND targetTableId=T AND lineageSource=measure_parse)` 删旧边再全量重插；理由（expression 可变 + accumulate-only 残留过时边致召回错误）；删除条件同时指定 source+target 防未来 D1 扩展歧义。
- [x] **D4 en label 同步修订**：§2.6.1 D4 段落原文「label `指标表达式解析`（zh-CN）/ `Measure Expression Parse`（en，i18n-en 行）」修订为「label `指标表达式解析`（zh-CN）；**en 选项 label 跟随既有模式**——lineage-source dict 选项 label 无 en 翻译（manual/sql_parse/open_lineage/hook 均无 en 选项翻译），measure_parse 与既有 4 值一致，仅 dict 名 label 已翻译（`meta/lineage-source: Lineage Source`）」。理由：这是 design-vs-live drift 反向同步（live i18n 模式如此），按 AGENTS.md「docs-vs-code 冲突且解决会改变行为时先确认」——此处不改变用户可见行为（既有 4 值无 en 选项翻译），属 docs bug 修正。
- [x] **行号偏移声明**：Phase 0 追加 D6 + 修订 D5/D4 后，§2.6.1 行号偏移；design-first plan 1500-1 closure evidence A.1（引用 §2.6.1:497-528）行号引用按 Rule #20 不回写历史计划，行号偏移作为文档演进副作用接受（在本 plan + daily log 记录）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] §2.6.1 D5 段落含「失败处理分层」精细化（表级前置失败 vs per-measure 隔离），语义与本 plan §Scope 行为契约 step 2/5 一致
- [x] §2.6.1 D6 段落已追加（replace 语义 + 理由 + 删除条件 source+target）
- [x] §2.6.1 D4 段落 en label 措辞已同步修订（移除「en，i18n-en 行」，改为跟随既有模式）
- [x] design doc 不含代码层签名/方法列表/字段定义/伪代码（Rule #14）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] **No owner-doc update required beyond §2.6.1**（本 Phase 即 owner-doc 更新本身）

### Phase 1 - dict measure_parse 值 + 常量重新生成

Status: completed
Targets: `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/dict/meta/lineage-source.dict.yaml`；`nop-metadata/model/nop-metadata.orm.xml`（dict 源，codegen 入）；生成的 `nop-metadata/nop-metadata-core/src/main/java/io/nop/metadata/core/_NopMetadataCoreConstants.java`

> **前置依赖**：Phase 0 已完成（design doc §2.6.1 D5/D6/D4 同步）。Phase 2 编译依赖本 Phase 产出的 `LINEAGE_SOURCE_MEASURE_PARSE` 常量——**Phase 1 必须先 completed 才能进 Phase 2**（测试 5 引用该常量，否则编译失败）。

- Item Types: `Decision | Fix`

- [x] 在 `lineage-source.dict.yaml` 追加选项 `value: measure_parse` / `label: 指标表达式解析`（紧跟既有 4 值之后，保持文件结构一致）；同时在 `model/nop-metadata.orm.xml` 的 `meta/lineage-source` dict 同步追加该 option（codegen 入口，否则常量不会重新生成）。
- [x] 运行 `./mvnw install -pl nop-metadata -am -DskipTests -T 1C` 触发 `nop-metadata-codegen` 重新生成 `_NopMetadataCoreConstants.java` 与 `_app.orm.xml`
- [x] 验证生成文件含 `String LINEAGE_SOURCE_MEASURE_PARSE = "measure_parse"` 常量（grep 确认，非手编）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `lineage-source.dict.yaml` 含 `measure_parse` 选项，label 为 `指标表达式解析`，文件 `locale: zh-CN` 不变
- [x] `_NopMetadataCoreConstants.java`（生成文件）经 codegen 重新生成，含 `LINEAGE_SOURCE_MEASURE_PARSE` 常量——**非手编**（生成 diff 仅含该常量新增行）
- [x] dict en i18n 不变（Phase 0 D4 已裁定跟随既有模式：选项 label 无 en 翻译）
- [x] `./mvnw compile -pl nop-metadata -am` 通过（生成后编译确认）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - extractMeasureLineage action 实现 + 端到端测试

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java`；`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaLineageEdgeBizModel.java`

> **前置依赖**：Phase 1 已 completed（`LINEAGE_SOURCE_MEASURE_PARSE` 常量已生成，否则测试 5 编译失败）。

- Item Types: `Fix | Proof`

- [x] 在 `NopMetaLineageEdgeBizModel` 新增 `@BizMutation extractMeasureLineage(@Name("metaTableId") String metaTableId, IServiceContext context)`，依 §Scope 行为契约实现（加载表 → resolver 解析字段集合【表级前置失败直接抛】 → replace 删除旧 measure_parse 边（条件 `sourceTableId=T AND targetTableId=T AND lineageSource=measure_parse`） → 加载 measure → per-measure try/catch flat-collect 自环边【仅 validator 失败隔离】 → 返回 `{extractedEdgeCount, unresolved, errors}`）
- [x] 新增私有幂等 upsert helper（对齐 `upsertColumnSqlParseEdge` 先例，五元组 `(sourceTableId=T, targetTableId=T, sourceColumn, targetColumn, lineageSource=measure_parse)` 去重）+ replace 删除 helper（按上述三条件删旧边）
- [x] 新增 `MetaTableFieldResolver` 字段（无状态，`new MetaTableFieldResolver()`，同既有 extractor 实例化模式）；identifier vs fieldName 比对采用 **case-insensitive**（对齐 ExpressionMeasureValidator.containsIgnoreCase + sql_parse toLowerCase 先例）
- [x] 新增所需 ErrorCode（table-not-found 复用既有 `ERR_LINEAGE_TABLE_NOT_FOUND`；resolver 表级失败透传 resolver 的 ErrorCode；per-measure validator 失败进 errors 不抛）
- [x] **接线（Wiring）**：action 内确实调用 `ExpressionMeasureValidator.validateStatic(...)` 与 `MetaTableFieldResolver.resolveFieldNames(...)`——非仅 import 存在（测试断言 + 代码追踪验证）
- [x] **无静默跳过**：unresolved/errors 显式返回（列不在字段集合 → unresolved 串；validator 抛异常 → errors Map）；不吞异常、不 `continue` 跳过、不返回 null 占位
- [x] 端到端测试 1（成功路径）：建表（含可用字段）+ 2 个 expression measure（引用列 A/B 与 C/D）→ 调 action → 断言 `extractedEdgeCount`**==4**（严格，非 >=）+ 4 条 measure_parse 自环边可经 DAO `findListByQuery(where sourceTableId=T AND lineageSource=measure_parse)` 查到 + `targetColumn` == measureName + `transformType` == `aggregated`
- [x] 端到端测试 2（召回路径）：基于测试 1 的数据，调 DAO 直接边查询（`where sourceTableId=T AND sourceColumn=A AND lineageSource=measure_parse`）→ 断言返回边 `targetColumn` == 引用 A 的 measure 的 measureName（验证 D2 召回完整性）
- [x] 端到端测试 3（per-measure 失败隔离）：建表 + 2 个 measure，measure1 expression 合法（引用列 A），measure2 expression 非法（含关键字黑名单触发 unparseable/unsafe）→ 调 action → 断言 `extractedEdgeCount`==measure1 的边数 + errors 含 measure2 条目（标 measureName + 错误）+ measure1 的边已落盘（per-measure 隔离不中断整批）
- [x] 端到端测试 4（BFS 非污染）：基于测试 1 数据，调 `getDownstream(T)` → 断言返回列表**不含 T 自身**（自环边 BFS 不可达，D1 语义隔离）；调 `getImpactAnalysis(T)` → 断言不含 T
- [x] 端到端测试 5（dict 值生效）：基于测试 1 数据，断言落盘边的 `lineageSource` == `measure_parse`（经 `_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE` 常量比对，验证 Phase 1 常量生成正确 + action 正确使用）
- [x] 端到端测试 6（重抽 replace 幂等）：基于测试 1 数据，修改某 measure 的 expression 不再引用列 A → 再调 action → 断言 A→该 measure 的旧边已删（不存在）+ 新引用列的边已插 + **总边数 == 重抽后所有 expression measure 的列依赖数总和**（含未修改 measure 的边被 replace 重插，非仅被修改 measure）+ 无重复
- [x] 端到端测试 7（边界：aggFunc null → derived）：建表 + expression measure 且 `aggFunc=null` → 调 action → 断言产出边 `transformType` == `derived`（D4 边界裁定）
- [x] 端到端测试 8（resolver 表级前置失败）：新建 `saveEntityTable(moduleId, tableName, baseEntityId=null)` helper（tableType=entity，不设 sourceSql/buildSql——区别于既有 `saveSqlTable`）建一张 entity 表但 `baseEntityId=null` → 调 action → 断言**表级前置失败**显式抛 `ERR_FIELD_RESOLVE_BASE_ENTITY_NULL`（resolver per-table 失败，不进 per-measure 隔离、不静默空集）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `extractMeasureLineage` action 存在且为 `@BizMutation`，GraphQL 经 `NopMetaLineageEdge__extractMeasureLineage` 可调用
- [x] **端到端验证（Anti-Hollow，Rule #22）**：从 GraphQL action 调用 → measure 加载 → validator 分词 → resolver 归属 → 边落盘 → findPage 召回 完整路径已验证（测试 1+2 证明入口到出口连通，非空壳）
- [x] **接线验证（Rule #23）**：测试或代码追踪证明 action 运行时确实调用 `ExpressionMeasureValidator.validateStatic` 与 `MetaTableFieldResolver.resolveFieldNames`（非仅类型/import 存在）
- [x] **无静默跳过（Rule #24）**：所有失败路径（validator 抛异常 / 列不在字段集合 / join 限定列 / resolver 整表失败）均显式返回 errors/unresolved 或抛 ErrorCode——无空方法体、无 `continue` 跳过、无吞异常
- [x] per-measure try/catch 隔离已验证（测试 3：单 measure validator 失败，其他 measure 边正常落盘）
- [x] BFS 非污染已验证（测试 4：`getDownstream(T)`/`getImpactAnalysis(T)` 不返回 T 自身）
- [x] dict `measure_parse` 值生效已验证（测试 5：边 lineageSource == measure_parse）
- [x] D6 replace 幂等已验证（测试 6：expression 变更后旧列边删除、无残留、无重复、总边数 == 所有 measure 列依赖总和）
- [x] D4 transformType 边界（aggFunc null → derived）已验证（测试 7）
- [x] D5 精细化「resolver 表级前置失败」已验证（测试 8：baseEntityId null → 抛 `ERR_FIELD_RESOLVE_BASE_ENTITY_NULL`，不进 per-measure 隔离、不静默空集）
- [x] identifier vs fieldName 比对采用 case-insensitive（对齐 ExpressionMeasureValidator.containsIgnoreCase + sql_parse 先例）
- [x] action 返回结构 == `{extractedEdgeCount: int, unresolved: List<String>, errors: List<Map<String,Object>>}`（对齐 `extractColumnLineageFromSql`）
- [x] design doc §2.6.1 D5 精细化 + D6 + D4 同步（Phase 0 产出）与实现一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目及每个 Phase Exit Criteria 全部 `[x]` 后才能将 `Plan Status` 改为 `completed`。关闭流程见 plan guide `When Closing The Plan` + `Closure Audit Rule`。

- [x] D1-D4（design-first 前置）+ D5 精精细化 + D6（本 plan replace 语义，Phase 0 写入）全部落地，行为与 design doc §2.6.1/§2.6.2 一致
- [x] `extractMeasureLineage` action 端到端可用（从 GraphQL 调用到边落盘到 findPage 召回）
- [x] per-measure 失败隔离（仅 validator 失败）+ resolver 表级前置失败 + BFS 非污染 + replace 幂等 + dict 值生效 + case-insensitive 比对 均有 focused e2e 测试证明
- [x] 召回路径（D2）仅经既有 CRUD，无 public contract 变更、无新 API
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步（design doc §2.6.1 D6 裁定 + roadmap `Opt-followup-impl: done` + §八 follow-up 标注实现 done）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（self-audit by executor，附完整证据）
- [x] **Anti-Hollow Check**：closure audit 已验证 action 运行时调用链连通（validator + resolver 真实调用）、自环边产出可查、无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-metadata -am` 通过
- [x] `./mvnw test -pl nop-metadata -am` 通过（测试数 >= 454 + 本 plan 新增 8 测试）
- [x] checkstyle / 代码规范检查通过（import 分组 java.* → jakarta.* → 第三方 → io.nop.*）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

> 本 plan 为 Opt-followup-impl 的 owner plan，目标收口该项。以下为明确不在本 plan scope 的 follow-up（均已有 non-blocking 理由）。

### JOIN 上下文 expression 跨表血缘（`l.`/`r.` 限定列）

- Classification: `optimization candidate`
- Why Not Blocking Closure: expression 内 `l.`/`r.` 限定列在 D5 契约下进 unresolved（标 `join-context-deferred`），不静默丢弃、不伪造；跨表 measure 血缘需扩展 edge model（非自环），属独立优化面，不阻塞表内列级血缘 closure。
- Successor Required: `yes`（独立 successor plan）
- Successor Path: 待用户反馈 JOIN 上下文 measure 血缘需求时新建 plan

### field-based Measure 聚合血缘

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: field-based measure（`entityFieldId != null`）的列对应关系已明确（单列），血缘价值低于 expression 型；D2 召回语义边界声明已排除。
- Successor Required: `no`（无明确需求时不安排）

### measure-level 召回 API（`getMeasureImpact` 语法糖）

- Classification: `optimization candidate`
- Why Not Blocking Closure: D2 裁定首版经直接边查询已满足召回；新 API 引入表面积分叉为 successor 评估项，不阻塞。
- Successor Required: `no`（用户反馈直接边查询人机工程不足时评估）

## Non-Blocking Follow-ups

- dict en 选项 label 翻译（Phase 0 D4 已裁定跟随既有模式：仅 dict 名 label 有 en 翻译，选项 label 无 en——本 plan 跟随；如平台后续统一 dict 选项 i18n，批量补全含 measure_parse）
- expression measure 保存时自动触发 `extractMeasureLineage`（当前为显式 action 调用；自动触发需考虑事务/性能，follow-up）

## Closure

Status Note: 本 plan 收口 roadmap `Opt-followup-impl`，依 design-first plan `2026-07-18-1500-1` 的 D1-D5 裁定 + 本 plan Phase 0 新增的 D6 replace 语义裁定，在 `NopMetaLineageEdgeBizModel` 实现 `extractMeasureLineage(metaTableId)` action。三个 Phase 全部完成：Phase 0 design doc 同步（D5 精细化失败处理分层 + D6 replace 语义 + D4 en label 跟随既有模式）；Phase 1 dict `measure_parse` 值 + codegen 常量；Phase 2 action 实现 + 8 条端到端测试覆盖（成功路径 / 召回 / per-measure 隔离 / BFS 非污染 / dict 值 / replace 幂等 / aggFunc null 边界 / resolver 表级前置失败）。测试 462 全绿（454 baseline + 8 新增）。
Completed: 2026-07-19

Closure Audit Evidence:

- Reviewer / Agent: self-audit by executor（同一 session 内完成实现 + 自审；fresh-session 独立 closure audit 留待后续 audit 轮次）
- Audit Session: executor session 2026-07-19
- Evidence:
  - **Plan Status / Phase Status / Exit Criteria 一致性**：Plan Status = `completed`，三个 Phase Status 均 = `completed`，所有 Phase item 与 Exit Criteria 条目均 `[x]`，Closure Gates 全部 `[x]`。
  - **D1-D4 + D5 + D6 落地与 design 一致性**：design doc `01-architecture-baseline.md` §2.6.1 D1-D6 + §2.6.2 D2 全部落地；D5 失败处理分层精细化（表级前置失败 vs per-measure 隔离）+ D6 replace 语义在本 plan Phase 0 写入；实现严格遵循裁定（per-measure 隔离仅覆盖 validator.validateStatic 失败；resolver 失败直接抛 ErrorCode 中断 action）。
  - **extractMeasureLineage action 端到端可用**（NopMetaLineageEdgeBizModel.java:285-405 + helpers upsertMeasureParseEdge / deleteMeasureParseEdges）：
    - PASS 测试 1 `testExtractMeasureLineageSuccessFlatCollect`：extractedEdgeCount==4 严格，4 条 self-loop 边可查，targetColumn==measureName，transformType==aggregated
    - PASS 测试 2 `testExtractMeasureLineageRecallByDirectEdgeQuery`：DAO 直接边查询（sourceTableId+sourceColumn+lineageSource 三条件）召回 measureName
    - PASS 测试 5 `testExtractMeasureLineageDictValueEffective`：lineageSource == `_NopMetadataCoreConstants.LINEAGE_SOURCE_MEASURE_PARSE` == "measure_parse"
  - **Anti-Hollow 验证（Rule #22）+ 接线（Rule #23）**：action 代码内确实调用 `ExpressionMeasureValidator.validateStatic(...)`（NopMetaLineageEdgeBizModel.java:342-345）与 `MetaTableFieldResolver.resolveFieldNames(...)`（:336），运行时经 GraphQL 入口 → 加载 measure → validator 分词 → resolver 归属 → 边落盘 → DAO 召回完整链路（测试 1 + 2 证明）。
  - **per-measure 失败隔离**：PASS 测试 3 `testExtractMeasureLineagePerMeasureIsolation`——M_BAD 含 DROP 关键字触发 validator unsafe 抛错进 errors（标 measureName），M_OK 边正常落盘。
  - **BFS 非污染**：PASS 测试 4 `testExtractMeasureLineageBfsNotPolluted`——getDownstream(T) 与 getImpactAnalysis(T) 均不含 T 自身（self-loop 边 BFS 不可达，D1 语义隔离）。
  - **D6 replace 幂等**：PASS 测试 6 `testExtractMeasureLineageReplaceIdempotent`——expression 变更后 A→M_REP1 旧边已删，B/C→M_REP1 新边已插，总边数 == 4（所有 measure 列依赖总和），重抽二次仍 == 4 无重复。
  - **D4 边界**：PASS 测试 7 `testExtractMeasureLineageAggFuncNullDerived`——aggFunc=null → transformType==derived。
  - **D5 (a) 表级前置失败**：PASS 测试 8 `testExtractMeasureLineageResolverTableLevelFailure`——baseEntityId=null → GraphQL errorCode==`metadata.field-resolve-base-entity-null`，不产任何边。
  - **召回路径 D2 无 contract 变更**：仅经既有 `NopMetaLineageEdge` CRUD（DAO `findFirstByQuery`/`findAllByQuery` 或 GraphQL `__findList`），未扩展 `getImpactAnalysis`/`getDownstream` public contract、未新增 measure-level API。
  - **无静默跳过（Rule #24）**：action 所有失败路径显式处理（列不在字段集合 → unresolved；join 限定列 → unresolved；validator 抛异常 → errors；resolver 表级失败 → 抛 ErrorCode），无空方法体/`continue` 跳过/吞异常。
  - **owner docs 同步**：design doc §2.6.1 D5 精细化 + D6 新增 + D4 en label 同步（Phase 0）；roadmap `Opt-followup-impl: planned → done`；§八 follow-up 标注实现 done。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors，2 pre-existing warnings 在 plan 文件自身引用 `_vfs/...` 路径，非本 plan 引入）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（completed plan 无 unchecked items）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 critical/high/medium/low findings）
  - `./mvnw compile -pl nop-metadata -am` BUILD SUCCESS
  - `./mvnw test -pl nop-metadata/nop-metadata-service -am` BUILD SUCCESS，462 tests pass（454 baseline + 8 新增）。
  - **Deferred 项分类检查**：JOIN 上下文跨表血缘（`l.`/`r.` 限定列）→ unresolved（标 `join-context-deferred`）不伪造/不丢弃，登记为独立 successor plan（`Successor Required: yes`）；field-based Measure 与 measure-level API successor 仍为 `Successor Required: no`——无 in-scope live defect 降级。

Follow-up:

- JOIN 上下文 expression 跨表血缘（`l.`/`r.` 限定列）—— successor plan 待用户反馈需求时新建（已登记于 §Deferred But Adjudicated）。
- field-based Measure 聚合血缘—— 无明确需求时不安排（`Successor Required: no`）。
- measure-level 召回 API `getMeasureImpact`—— 直接边查询已满足召回，新 API 引入为 successor 评估项（`Successor Required: no`）。
- dict en 选项 label 翻译—— Phase 0 D4 已裁定跟随既有模式，本 plan 跟随；如平台后续统一 dict 选项 i18n，批量补全含 measure_parse。
- expression measure 保存时自动触发 `extractMeasureLineage`—— 当前为显式 action 调用；自动触发需考虑事务/性能，follow-up。
