# Semantic Dataset Roadmap — BI 语义数据集层（datav × metadata 连接件）

> Last Updated: 2026-10-28
> Source: Gitee GVP 调研 2026-10-28（对标 DataEase：SQL 数据集 + 计算字段 + 行列权限）+ live repo 基线核对（nop-metadata / nop-datav ORM 模型，2026-10-28）

## Purpose

本 roadmap 追踪 BI 语义层最后一块缺口的收口：**指标建模已在 nop-metadata 完成**（`NopMetaEntityMeasure`：aggFunc/expression/format/currencyUnit；`NopMetaEntityDimension`：type/granularity/format；`NopMetaEntityJoin`；SemanticType/Glossary/Catalog/BusinessDomain），**但 nop-datav 未消费它**——datav 的数据集当前是手写的预注册 SQL 数据集（`dsType=sql`、`${param}` 占位），datav-service 代码中没有任何对 Measure/MetaEntity 的引用。本 roadmap 做**数据集定义权收敛**：SQL 数据集不再在 datav 侧定义，而是收敛到 metadata 侧作为**视图实体定义**（对齐 nop-orm 活跃的 `tableView` + `noPrimaryKey` 视图路径——带字段定义、可经 relation 关联实体、无主键合法），语义数据集同样从 metadata 的度量/维度/join 派生。datav 彻底零定义、纯消费：编译执行（duckdb 或数据源下推）、运行时行列权限注入，并让 Panel / 透视表面板 / ChatBI 以"拖指标、拖维度"方式消费。一句话：**建模（指标 + 视图）归 metadata，执行归 datav**。

Does not contain implementation details. Each `todo`/`planned` work item is owned by its execution plan.

## Work Item Status

> **This is the only dynamic state block. Update status only here.**
> Humans set items and their order; AI takes the first `todo` item, drafts/executes
> plans, and writes the item back to `done` when closure audit passes.

### Milestone 1: 调研（语义层数据集方案与对标盘点）

- 1.1 对标盘点与差距矩阵（DataEase 数据集/计算字段/行列权限、Looker headless 语义层、Superset dataset；对照 nop-metadata/nop-datav 现状）: `todo`
- 1.2 调研阶段深度审计（对照 live repo 核对 nop-metadata 语义实体、nop-datav 数据集/ChatBI 现状与本文 contract drift）: `todo`
- 1.3 调研阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

### Milestone 2: 设计

- 2.1 数据集模型设计（视图数据集：metadata 侧视图实体定义，对齐 nop-orm entity+`tableView`/`noPrimaryKey` 契约；语义数据集：来源 entity、维度/度量清单、参数、计算字段；datav 侧零定义纯消费的契约）: `todo`
- 2.2 执行引擎与权限设计（视图实体 + 语义数据集统一编译：duckdb 目标 + NopMetaDataSource 下推；运行时行列权限注入对接 nop-auth）: `todo`
- 2.3 设计阶段深度审计（设计文档 vs live repo 契约核对：metadata 实体字段、datav DatasetRef/Panel/ChatBI 工具签名）: `todo`
- 2.4 设计阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

### Milestone 3: 开发

- 3.1 视图数据集：metadata 侧视图实体定义与注册（对齐 nop-orm `entity`+`tableView`/`noPrimaryKey`/`sqlText` 契约：字段定义、relation 关联实体、无主键合法；`entity-kind=SQL_VIEW/EXTERNAL` 落地。注意：`view-entity.xdef` 为孤立契约已被该路径取代，不得使用）: `todo`
- 3.2 语义数据集模型与注册服务（从 NopMetaEntity 的 measures/dimensions/joins 派生数据集定义；`dsType=semantic`；与视图数据集共用同一消费契约）: `todo`
- 3.3 统一编译器（语义数据集的维度+度量+过滤器+join 与视图数据集实体 → SQL；聚合按 measure.aggFunc/expression 展开；目标后端：nop-duckdb / 源数据源下推，可配置）: `todo`
- 3.4 运行时行列权限注入（字段级可见性 + 行级过滤条件，按用户身份从权限面注入编译管线，不烧进数据集定义）: `todo`
- 3.5 计算字段与派生指标（基于 measure.expression 的校验、循环依赖检测、编译缓存与失效）: `todo`
- 3.6 datav Panel/透视表接入 metadata 数据集（datav 数据集定义收敛下线：DatasetRef 改为引用 metadata 侧视图/语义数据集；面板字段选择器列出维度/指标清单）: `todo`
- 3.7 ChatBI 接入（扩展 datav-list/describe/query-dataset AI 工具支持视图/语义数据集；NL2Query 以指标口径约束生成）: `todo`
- 3.8 datav-web 自助分析（拖维度/拖指标的分析视图配置，复用 Panel 的 PIVOT_TABLE/METRIC 类型）: `todo`
- 3.9 存量 SQL 数据集迁移与兼容（既有 `dsType=sql` 定义迁移为 metadata 侧视图实体，或经兼容适配期读取；迁移前后查询结果等价的判别性测试）: `todo`
- 3.10 开发阶段深度审计（对照 live repo 逐工作项核对实现与 contract drift）: `todo`
- 3.11 开发阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

### Milestone 4: 验证

- 4.1 端到端验证（metadata 定义指标 → 语义数据集 → 面板/透视表/ChatBI 三条消费路径 + 行列权限用例 + SQL 数据集回归）: `todo`
- 4.2 验证阶段深度审计（对照 live repo 核对验证覆盖与 contract drift）: `todo`
- 4.3 验证阶段开放审计（独立 fresh sub-agent 开放评审，不复用执行者上下文）: `todo`

> Milestones carry no status of their own. A milestone is considered reached when
> all of its work items are `done`. Only work items carry todo/ready/done status.

## Status values

| Status | Meaning |
| --- | --- |
| `todo` | Not started, no plan |
| `ready` | Has execution plan, passed draft review |
| `done` | Complete, passed closure audit |

## Framework / platform reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| 指标/维度/join/语义类型建模 | nop-metadata（已完成） | `NopMetaEntityMeasure`（aggFunc/expression/format/currencyUnit）、`NopMetaEntityDimension`、`NopMetaEntityJoin`、`NopMetaSemanticType`——本 roadmap **不新增**建模实体 |
| 视图对象契约 | nop-orm（entity + `tableView`/`noPrimaryKey`/`sqlText`） | 视图数据集的落地形态：字段定义、relation 关联实体、无主键合法；`view-entity.xdef` 为孤立契约（模型类零运行时消费者、未挂入 orm.xdef），已被 entity+noPrimaryKey 路径取代，禁用 |
| 实体类型字典 | nop-metadata `entity-kind`（PHYSICAL/SQL_VIEW/EXTERNAL） | 视图实体在 metadata 侧的落位 |
| 分析查询引擎 | nop-duckdb | duckdb 本地聚合执行后端 |
| 多源数据源注册 | nop-metadata `NopMetaDataSource`（jdbc/http/rest/file） | 编译下推目标 |
| ChatBI 工具与参数化绑定 | nop-datav（`datav-query-dataset` 等 AI 工具、DatasetRef、Panel、PIVOT_TABLE/METRIC 面板类型、`${param}` 防注入绑定） | 工具改造为消费 metadata 侧数据集；maxRows 钳制沿用 |
| 认证与数据权限 | nop-auth（RBAC + 数据权限 + 多租户） | 行列权限注入的权限来源 |
| ORM 元模型 | nop-orm | metadata entity 挂接 ormModelId；编译器字段类型解析 |
| 前端呈现 | nop-datav-web + @nop-chaos/flux（pivot/dashboard 渲染器） | 自助分析界面 |

## Current baseline

**Already shipped（live repo 核对，2026-10-28）：**

- nop-metadata（3670 行 ORM 模型）已含完整语义建模面：`NopMetaEntityMeasure`（measureName/entityFieldId/aggFunc/expression/format/currencyUnit）、`NopMetaEntityDimension`（dimensionType 分类/时间/地理 + granularity + format）、`NopMetaEntityJoin`（joinType/side）、`NopMetaSemanticType`、`NopMetaGlossary`、`NopMetaCatalog`、`NopMetaBusinessDomain`；治理面（lineage/quality/contract/profiling/tag/data product）齐备
- nop-datav（1316 行 ORM 模型）已含呈现/分析面：`NopDatavDashboard/DashboardTab/Panel`（panel-type 含 METRIC/PIVOT_TABLE）、`NopDatavScreen/ScreenWidget`（大屏）、`NopDatavDatasetRef`、`NopDatavFilterState`、`NopDatavAlertRule`、`NopDatavChatSession/ChatMessage`（ChatBI）、`NopDatavExportTask/ReportTask/ReportDelivery`
- ChatBI AI 工具已注册：`datav-list-datasets` / `datav-describe-dataset` / `datav-query-dataset`（仅支持 `dsType=sql` 的预注册 SQL 数据集，`${param}` 参数化绑定防注入，maxRows 服务端钳制）
- **nop-orm 视图对象走 entity+tableView/noPrimaryKey（活跃路径）**：entity 支持 `tableView`（是否视图）与 `noPrimaryKey`（注释原文："视图对象有可能没有主键，此时不支持延迟加载，所有字段都是 eager 字段，而且在 session 缓存里也没有记录"）；列级 `sqlText`（sql 视图字段，只读表达式列）。该路径深度接线：OrmEntityModelInitializer（noPrimaryKey→强制 eager、禁 lazy）、OrmSessionImpl、CollectionPersisterImpl、EQL（EntityTableMeta/EqlTransformVisitor）、OrmModelTopEntryBuilder（tableView 关联）。**`view-entity.xdef`（`OrmViewEntityModel`）为孤立契约**：模型类在 model 包外零消费者、未挂入 orm.xdef、无 loader 加载——已被 entity+noPrimaryKey 路径取代，不得使用
- nop-metadata `entity-kind` 字典已预留 `SQL_VIEW` / `EXTERNAL` 实体类型——metadata 侧视图实体的落位是设计预期而非新发明

**Main gaps:**

- nop-datav-service 代码中无任何对 Measure/MetaEntity 的消费——metadata 定义的指标/维度对 datav 完全不可见
- 数据集定义权错位：SQL 数据集定义在 datav 侧且无字段级 schema、不挂实体——应收敛为 metadata 侧视图实体定义（对齐 nop-orm entity+tableView/noPrimaryKey）
- 无语义数据集类型：无"从度量/维度派生"的语义轨
- 无统一编译器：聚合表达式、join 路径、维度粒度、视图实体到 SQL 的查询生成机制缺失
- 无运行时行列权限注入：数据集查询没有按用户身份的字段可见性/行级过滤管线
- ChatBI 工具仅认识 SQL 数据集，NL2Query 无指标口径约束
- 无

## Milestones

| # | Milestone | Work items | Deps | Reuse focus |
| --- | --- | --- | --- | --- |
| M1 | 调研 | 1.1-1.3 | — | DataEase/Looker 对标 |
| M2 | 设计 | 2.1-2.4 | M1 done | nop-metadata 语义实体、nop-datav 数据集契约 |
| M3 | 开发 | 3.1-3.11 | M2 done | nop-metadata, nop-orm(tableView/noPrimaryKey), nop-duckdb, nop-auth, nop-datav |
| M4 | 验证 | 4.1-4.3 | M3 done | nop-autotest / e2e |

> M3 里程碑内的两个审计工作项（3.10 深度审计、3.11 开放审计）依赖 3.1-3.9 全部 `done` 后执行。

## Work Item Details

### 1.1 对标盘点与差距矩阵

**Goal:** 盘点 DataEase（SQL 数据集/计算字段/行列权限/数据集关联）、Looker（headless 语义层：measure/dimension 定义与查询分离）、Superset（dataset 抽象）的语义层数据集机制，产出能力矩阵；对照 nop-metadata/nop-datav 现状列出本 roadmap 的取舍（哪些已由 metadata 覆盖、哪些是 datav 新增、哪些明确不做）。

**Deliverables:** 调研报告（对标矩阵、数据集定义收敛结论、执行后端选型建议）。

**Owner Doc:** 调研报告（本工作项产出） | **依赖:** — | **复用:** 外部开源方案

### 1.2 调研阶段深度审计

**Goal:** 对照 live repo 深度审计：核对本文 Current baseline 中关于 `NopMetaEntityMeasure/Dimension/Join` 字段、datav 数据集契约、ChatBI 工具签名的每条声称；核对 datav-service 无 metadata 消费的判断；drift 登记并回写。

**Owner Doc:** 审计报告 | **依赖:** 1.1 | **复用:** 本文 Current baseline 块

### 1.3 调研阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；仅输入"调研报告 + 基线摘要"，开放质疑"建模（指标 + 视图）归 metadata、执行归 datav"的分层结论与定义收敛边界。未通过则 1.1 返工。

**Owner Doc:** 开放审计意见 | **依赖:** 1.1, 1.2 | **复用:** —（上下文隔离是强制要求）

### 2.1 语义数据集模型设计

**Goal:** 设计数据集定义模型，两轨统一在 metadata 侧：①视图数据集——视图实体定义（对齐 nop-orm entity+`tableView`/`noPrimaryKey` 契约：字段定义、relation 关联实体、无主键合法；`entity-kind=SQL_VIEW/EXTERNAL` 落位；不使用孤立的 view-entity.xdef）；②语义数据集——来源（NopMetaEntity 引用）、暴露的维度/度量清单（可裁剪/别名）、join 路径、参数、计算字段。两轨 describe/query 契约同构，DatasetRef 统一引用，datav 侧零定义；明确 Non-Goal（不做指标建模——已在 metadata；不做物化/加速缓存，列为后续优化）。

**Deliverables:** 视图/语义数据集模型设计文档 + 统一消费契约。

**Owner Doc:** `ai-dev/design/semantic-dataset/`（设计文档目录，本阶段产出） | **依赖:** M1 done | **复用:** nop-metadata 语义实体、nop-datav 数据集契约

### 2.2 执行引擎与权限设计

**Goal:** 语义→SQL 编译器设计：维度+度量+过滤+join → SQL 的生成规则（measure.aggFunc/expression 展开、维度粒度、时间粒度）、执行后端选择（nop-duckdb 本地执行 vs 经 NopMetaDataSource 下推，可配置与降级）；运行时行列权限设计：字段级可见性裁剪、行级过滤条件注入点、与 nop-auth 数据权限/多租户的对接契约。

**Deliverables:** 编译器设计文档 + 权限注入设计文档。

**Owner Doc:** 同上设计文档目录 | **依赖:** 2.1 | **复用:** nop-duckdb, NopMetaDataSource, nop-auth, nop-orm

### 2.3 设计阶段深度审计

**Goal:** 对照 live repo 核对设计文档引用的每一处契约：metadata 语义实体字段名、datav DatasetRef/Panel/AI 工具签名、nop-auth 数据权限接入点；确认设计未与 2.1 Non-Goal 冲突。contract drift 登记并回写。

**Owner Doc:** 审计报告 | **依赖:** 2.1, 2.2 | **复用:** live repo

### 2.4 设计阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；仅输入"设计文档 + diff 摘要"。未通过则回到对应设计工作项修订。

**Owner Doc:** 开放审计意见 | **依赖:** 2.1-2.3 | **复用:** —

### 3.1 视图数据集：metadata 侧视图实体定义与注册

**Goal:** 落地 metadata 侧视图实体定义：走 nop-orm 活跃路径 entity+`tableView`/`noPrimaryKey`/`sqlText`——视图带字段定义、可经 relation 关联实体、`noPrimaryKey` 合法（对应强制 eager、无延迟加载的 nop-orm 语义）；`entity-kind=SQL_VIEW/EXTERNAL` 类型落地为可注册、可版本化的视图定义；定义查询服务读取视图实体产出数据集 schema。**禁止使用 `view-entity.xdef`**（孤立契约：模型类零运行时消费者、未挂入 orm.xdef，已被 entity+noPrimaryKey 路径取代）。

**Out of scope:** 编译器（3.3）、权限（3.4）。

**Owner Doc:** 2.1 设计 | **依赖:** M2 done | **复用:** nop-orm entity+tableView/noPrimaryKey/sqlText, nop-metadata entity-kind, nop-metadata

### 3.2 语义数据集模型与注册服务

**Goal:** 实现语义数据集定义模型与注册/查询服务（`dsType=semantic`）：从 NopMetaEntity 的 measures/dimensions/joins 派生数据集 schema；支持维度/度量裁剪与别名；describe 输出与视图数据集同构（下游无感）。

**Owner Doc:** 2.1 设计 | **依赖:** 3.1 | **复用:** nop-metadata, nop-datav-core/dao

### 3.3 统一编译器

**Goal:** 实现编译管线：视图实体与语义数据集统一编译——语义侧维度/度量/过滤/join → SQL（聚合按 measure.aggFunc/expression 展开、时间维度粒度聚合），视图侧按实体映射生成查询；参数沿用 `${param}` 参数化绑定；执行后端 nop-duckdb 与数据源下推双实现 + 不可下推时降级策略；生成 SQL 的白盒测试（固定输入→固定 SQL 断言）。

**Owner Doc:** 2.2 设计 | **依赖:** 3.2 | **复用:** nop-duckdb, NopMetaDataSource, nop-orm

### 3.4 运行时行列权限注入

**Goal:** 编译管线中的权限注入点：字段级可见性（无权限度量/维度/视图字段从查询与结果中裁剪）、行级过滤（按用户身份/租户注入 WHERE 条件）；对接 nop-auth 数据权限；权限拒绝路径有明确错误语义与测试。

**Owner Doc:** 2.2 设计 | **依赖:** 3.3 | **复用:** nop-auth, nop-credential

### 3.5 计算字段与派生指标

**Goal:** 基于 measure.expression 的计算字段：表达式校验、循环依赖检测、编译结果缓存与 metadata 变更失效（挂接 NopMetaModelChangedEvent）。

**Owner Doc:** 2.1/2.2 设计 | **依赖:** 3.3 | **复用:** nop-metadata 变更事件、nop-core 表达式设施

### 3.6 datav Panel/透视表接入 metadata 数据集

**Goal:** `NopDatavDatasetRef` 改为引用 metadata 侧视图/语义数据集（datav 侧数据集定义收敛下线）；Panel 字段选择器列出维度/指标/视图字段清单（区分可聚合度量与可分组维度）。

**Owner Doc:** 2.1 设计 | **依赖:** 3.2 | **复用:** nop-datav-service, nop-datav-web

### 3.7 ChatBI 接入

**Goal:** 扩展 `datav-list-datasets` / `datav-describe-dataset` / `datav-query-dataset` 三工具支持视图/语义数据集：describe 输出指标口径（aggFunc/格式/单位），NL2Query 生成受指标口径约束（只允许引用已注册度量/维度/视图字段）；权限经 3.4 管线生效。

**Owner Doc:** 2.2 设计 | **依赖:** 3.4, 3.6 | **复用:** nop-datav ChatBI 工具、nop-ai-core

### 3.8 datav-web 自助分析

**Goal:** 前端自助分析视图：拖维度/拖指标的配置界面，落到 Panel 的 PIVOT_TABLE/METRIC 类型；字段清单来自数据集 describe；与 @nop-chaos/flux 的 pivot/dashboard 渲染器对接。

**Owner Doc:** 2.1 设计 | **依赖:** 3.6 | **复用:** nop-datav-web, @nop-chaos/flux（pivot/dashboard）

### 3.9 存量 SQL 数据集迁移与兼容

**Goal:** 既有 `dsType=sql` 定义迁移为 metadata 侧视图实体（字段定义补齐、可关联实体），或经兼容适配期读取；迁移前后查询结果等价的判别性测试（同参数同结果集）。

**Owner Doc:** 2.1 设计 | **依赖:** 3.3 | **复用:** nop-metadata, nop-orm entity+tableView/noPrimaryKey

### 3.10 开发阶段深度审计

**Goal:** 对照 live repo 逐工作项核对实现与 contract drift：视图实体契约、语义数据集契约、编译器 SQL 语义、权限注入点、ChatBI 工具签名是否与 2.x 设计一致；drift 登记并回写设计文档。

**Owner Doc:** 审计报告 | **依赖:** 3.1-3.9 all done | **复用:** live repo

### 3.11 开发阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；输入"任务计划 + diff 摘要 + 验证输出"三件套，开放评审实现质量与设计一致性。未通过则定向返工。

**Owner Doc:** 开放审计意见 | **依赖:** 3.10 | **复用:** —

### 4.1 端到端验证

**Goal:** 全链路回归：metadata 定义指标与视图实体 → 数据集注册 → 三条消费路径（面板/透视表自助分析、ChatBI 对话查数、导出）→ 行列权限用例（字段裁剪 + 行过滤 + 租户隔离）→ 存量 SQL 数据集迁移后查询等价回归。Pass/fail 验证必须程序化断言。

**Owner Doc:** 2.2 设计（验收标准小节） | **依赖:** M3 done | **复用:** nop-autotest, e2e 测试设施

### 4.2 验证阶段深度审计

**Goal:** 对照 live repo 核对端到端验证覆盖完整性（三消费路径、权限用例、迁移等价回归）与 contract drift；核对验证断言可复现。

**Owner Doc:** 审计报告 | **依赖:** 4.1 | **复用:** live repo

### 4.3 验证阶段开放审计

**Goal:** 独立 fresh sub-agent 开放评审，不复用执行者上下文；输入"验证报告 + 审计材料"。通过后整条 roadmap 收口，★ **BI 语义层 1.0**（指标建模 metadata + 语义数据集执行 datav + 自助分析）达成。

**Owner Doc:** 开放审计意见 | **依赖:** 4.1, 4.2 | **复用:** —

★ **Milestone: BI 语义层 1.0**（unlocks when M1-M4 all done）

## Cross-cutting concerns

| Concern | Notes |
| --- | --- |
| 分层边界 | 指标/维度口径与视图（含 SQL 数据集）定义只在 metadata；datav 零定义、纯消费（派生、编译与执行）；禁止在 datav 重复定义指标或数据集 schema |
| 定义收敛 | datav 侧 `dsType=sql` 数据集定义下线，迁移为 metadata 侧视图实体（对齐 nop-orm entity+tableView/noPrimaryKey：字段定义、可关联实体、无主键合法）；describe/query 契约迁移前后同构，下游无感 |
| 视图路径单一 | 视图数据集统一走 entity+`tableView`/`noPrimaryKey`/`sqlText`；`view-entity.xdef` 为孤立契约禁用（若未来平台复活该契约需先重启本 roadmap 评审） |
| 执行后端风险 | duckdb 本地执行与数据源下推双实现；不可下推场景须有明确降级与告警 |
| 权限即运行时 | 行列权限在编译管线运行时注入，不烧进数据集定义；权限拒绝须有可测错误语义 |
| 缓存失效 | 计算字段/编译缓存挂接 metadata 变更事件（NopMetaModelChangedEvent），防口径漂移 |
| 上下文隔离 | 开放审计工作项强制 fresh sub-agent，不复用执行者上下文（1.3/2.4/3.11/4.3） |
| 测试策略 | Must automate: 统一编译器（固定输入→固定 SQL）、行列权限注入路径、存量数据集迁移等价性；Should have: Panel 接入、ChatBI 工具、自助分析界面 |

## Rules

- This file is a state index and coarse decomposition, not an execution plan.
- Each `ready`/`done` work item is owned by its execution plan.
- Status changes happen only in the Work Item Status block at the top.
- Milestones carry no status; a milestone is reached when all its work items are `done`.
- Every stage milestone MUST contain exactly two audit work items: 阶段深度审计 (deep audit vs live repo, contract drift) and 阶段开放审计 (independent fresh sub-agent, no executor context reuse).
