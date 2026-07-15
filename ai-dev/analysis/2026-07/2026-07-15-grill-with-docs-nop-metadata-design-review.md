# nop-metadata 设计文档 grill-with-docs 审查报告

> 审查方法：grill-with-docs（relentless interview — 沿设计决策树逐个检查一致性、完整性与可实施性）
> 审查对象：`ai-dev/design/nop-metadata/` 全部 12 份设计文档 + `model/nop-metadata.orm.xml`
> 审查日期：2026-07-15
> 审查者：grill-with-docs 方法论（参考 nop-app-erp `docs/discussions/2026-06-29-1000-grill-with-docs-design-review.md`）

## 综述

nop-metadata 设计文档在**调研深度和专题覆盖度**上达标（12 份专题 + 15 份调研），但在**文档间一致性**上存在 **3 个 HIGH 矛盾**、**4 个 MEDIUM 缺口**、**3 个 LOW 改进项**。核心问题集中在：vision 与 architecture-baseline 对同一设计决策给出了相反结论、设计文档引用了 ORM 模型中不存在的实体、以及"Open Questions"未随 ORM 落地而收敛。

---

## HIGH — 文档间直接矛盾（3 个）

### G1: QuerySpace + Driver 抽象 — vision 说有，architecture-baseline 说拒绝了

**发现**：
- `00-vision.md` §与 nop-dyn 的关系表明确写：nop-metadata 支持"QuerySpace 多路由 + Driver 抽象"和"跨源查询"
- `00-vision.md` §解决的问题表写："外部数据库的表无法纳入统一元数据管理 → MetaDataSource + MetaTable，通过扫描或注册导入"
- 但 `01-architecture-baseline.md` §七（拒绝了什么）**明确拒绝**："QuerySpace + Driver 运行时抽象 — ORM 本身就是统一数据库映射引擎，实体 querySpace 已承担路由，不需要另加一层"
- `01-architecture-baseline.md` §设计结论 8 也写："所有查询执行走现有 ORM 层 —— 不引入额外 Driver/QuerySpace 抽象"

**为什么重要**：这是模块的**核心定位分歧**。vision 承诺"跨源查询 + Driver 抽象"（意味着可以 JOIN MySQL 表和 ClickHouse 表），而 architecture-baseline 否定了这一能力。如果读者先读 vision，会假设跨源查询是设计目标；先读 architecture-baseline，则认为只有 ORM 单源。后续 MetaTableJoin 跨数据源关联（01 §2.5 的 open question）直接依赖这个决策。

**证据**：
- `00-vision.md:23` — "跨源查询 | 不支持 | QuerySpace 多路由 + Driver 抽象"
- `01-architecture-baseline.md:452` — "QuerySpace + Driver 运行时抽象 | ORM 本身就是统一数据库映射引擎"
- `01-architecture-baseline.md:18` — "所有查询执行走现有 ORM 层"

**选项**：
- **A（推荐）**：以 architecture-baseline 为准（它是更新的、更具体的决策文档），修正 vision 的"跨源查询"描述为"单源元数据管理，跨源查询留后续阶段"。在 vision 中标注 architecture-baseline 已拒绝 Driver 抽象。
  - 理由：architecture-baseline 的拒绝理由（ORM 已是统一映射引擎）成立且已被 ORM 模型（无 Driver 表）证实。
  - 代价：vision 需修改 §与 nop-dyn 关系表和 §解决问题表。
- **B**：以 vision 为准，在 architecture-baseline 中恢复 Driver 抽象设计。
  - 理由：跨源查询是元数据平台的核心价值。
  - 代价：需要新增 Driver/QuerySpace 实体到 ORM 模型，大幅扩大 scope。
- **C**：保持矛盾，在两处各加脚注指向对方。
  - 理由：两份文档面向不同读者。
  - 代价：矛盾永远存在，后续实施者无所适从。

**推荐决议**：A。

---

### G2: MetaOrmModel.status — version-management 说有，architecture-baseline 说不存在

**发现**：
- `03-version-management.md` §4.2 发布流程第 2 步写："检查所有 **MetaOrmModel 的 status = released**"
- 但 `01-architecture-baseline.md` §2.3 明确写："MetaOrmModel **不再有 version 和 status 字段**——这些在 MetaModule 上"
- ORM 模型（`nop-metadata.orm.xml`）证实：NopMetaOrmModel **无 status 列**，只有 modelName/isDelta/sourceContent/importedAt

**为什么重要**：这是**发布流程的核心前提**。如果开发者按 version-management §4.2 编码，会尝试读取 MetaOrmModel.status（不存在），导致编译错误或运行时 NPE。发布流程无法按文档执行。

**证据**：
- `03-version-management.md:171` — "检查所有 MetaOrmModel 的 status = released"
- `01-architecture-baseline.md:93` — "MetaOrmModel 不再有 version 和 status 字段——这些在 MetaModule 上"
- `nop-metadata/model/nop-metadata.orm.xml` NopMetaOrmModel 实体 — 无 status column

**选项**：
- **A（推荐）**：修正 `03-version-management.md` §4.2，将"检查 MetaOrmModel.status"改为"检查 MetaModule.status"。发布流程改为：直接在 MetaModule 上操作 status。
  - 理由：architecture-baseline 和 ORM 模型都确认 status 在 MetaModule 上。
  - 代价：修改 version-management 文档 1 处。
- **B**：给 MetaOrmModel 重新加回 status 字段。
  - 理由：version-management 的发布流程需要它。
  - 代价：与 architecture-baseline §七"拒绝了单独的 status"矛盾，需要改 ORM 模型。
- **C**：删除 §4.2 的 status 检查步骤。
  - 理由：MetaModule.status 已足够。
  - 代价：发布流程缺少校验。

**推荐决议**：A。

---

### G3: 设计文档引用了 ORM 中不存在的实体（MetaDataContract / MetaManifest / MetaCatalog）

**发现**：
- `04-data-governance.md` §1.3 和 §2.3 定义了 **MetaDataContract**（数据契约，参考 ODCS），但它不在 `nop-metadata.orm.xml` 中
- `05-metadata-import.md` 定义了 **MetaManifest** 和 **MetaCatalog** 模型，且我在 Open Questions 中标注了"尚未纳入 orm.xml"
- `08-reconciliation.md` 定义了 **MetaReconciliationConfig / MetaReconciliationResult / MetaReconciliationEntity**，也不在 ORM 中
- `10-event-model.md` 定义了 **MetaModelChangedEvent**，不在 ORM 中

**为什么重要**：设计文档承诺了至少 **7 个未建模的实体**。读者无法判断这些是"Phase 2 补充"还是"设计遗漏"。对 ORM 已落地的 21 个实体，这造成了"设计说有但查不到"的困惑。特别是 `04-data-governance.md` §1.3 把 MetaDataContract 列为"已设计"（✅），但实际连表都没有。

**证据**：
- `04-data-governance.md:44` — "数据契约 (Contract) | OpenMetadata ODCS | MetaDataContract（新增）"
- `08-reconciliation.md:43` — "2.1 Reconciliation 配置"（定义了完整实体结构）
- `nop-metadata/model/nop-metadata.orm.xml` — 无 MetaDataContract/MetaManifest/MetaCatalog/MetaReconciliation*/MetaModelChangedEvent

**选项**：
- **A（推荐）**：在每份专题文档的头部加 `> Implementation Phase: Phase X (尚未建模)` 标注。在 README 的实体清单中明确列出"已建模 21 个 + 待建模 N 个"。
  - 理由：保持设计文档的前瞻性，同时明确实施状态。
  - 代价：每份文档加 1 行标注。
- **B**：把所有设计文档引用的实体都加到 ORM 模型中。
  - 理由：模型即真相源，设计文档不应该引用不存在的实体。
  - 代价：ORM 模型膨胀，且很多实体尚未设计到可建模的细节程度。
- **C**：从设计文档中移除未建模实体的详细定义。
  - 理由：设计文档只描述已实现的东西。
  - 代价：丢失前瞻性设计。

**推荐决议**：A。

---

## MEDIUM — 设计缺口（4 个）

### G4: baseModuleId 引用目标歧义（surrogate PK vs 业务 key）

**发现**：
- `03-version-management.md` §2.1 写 `baseModuleId → MetaModule`，但示例用业务名："nop-app-mall v1 baseModuleId = nop-auth v1"
- ORM 模型中 `baseModuleId` 是 `VARCHAR(32)`，与 `metaModuleId`（surrogate PK, VARCHAR(32)）类型一致
- 但设计示例用的是 "nop-auth v1"（业务标识），不是 UUID 格式的 surrogate key

**为什么重要**：如果开发者按示例理解，会用 moduleId("nop/auth") 填 baseModuleId；如果按 ORM 类型理解，会用 metaModuleId(UUID) 填。两种填法在运行时行为完全不同——前者无法做 ORM join，后者可以。

**证据**：
- `03-version-management.md:89` — "nop-app-mall v1 (released) baseModuleId = nop-auth v1"
- `nop-metadata.orm.xml` NopMetaModule — `baseModuleId VARCHAR(32)`

**选项**：
- **A（推荐）**：明确 baseModuleId 引用 MetaModule.metaModuleId（surrogate PK），修正 version-management 示例为 UUID 格式。在 ORM 模型中给 baseModuleId 加 to-one relation 到 NopMetaModule。
  - 理由：与 NopId→FK 的标准模式一致，ORM join 可用。
  - 代价：修改设计示例 + ORM 模型加关系。
- **B**：baseModuleId 引用 moduleId（业务 key "nop/auth"）。
  - 理由：业务可读。
  - 代价：无法用 ORM 关系做 join，需手工查询；且 moduleId 可能重复（不同 version 同名）。
- **C**：用复合键 (moduleId, version) 作为引用。
  - 理由：唯一标识一个模块版本。
  - 代价：增加查询复杂度。

**推荐决议**：A。

---

### G5: "Open Questions" 未随 ORM 落地而收敛

**发现**：
- `01-architecture-baseline.md` §八列出 5 个 open questions，其中第一个"isDelta=true/false 用同一张表（列区分）还是两张表？"——**已在 ORM 模型中解决**（用同一张表 + isDelta 列），但文档仍标为 open
- `03-version-management.md` §六列出 3 个 open questions，其中第一个"MetaModule 的 sourceContent 存储什么？"——**引用了错误实体**（sourceContent 在 MetaOrmModel 上，不在 MetaModule 上）
- 多份文档的 Open Questions 交叉重复（如"通用 Domain 来源"在 01 和 04 都出现）

**为什么重要**：Open Questions 应该随决策收敛。未收敛的 open question 会让读者误以为设计未定，导致重复讨论已解决的问题。引用错误实体更会误导实现。

**证据**：
- `01-architecture-baseline.md:465` — "isDelta=true/false 用同一张表（列区分）还是两张表？"（已在 ORM 解决）
- `03-version-management.md:209` — "MetaModule 的 sourceContent 存储什么？"（sourceContent 在 MetaOrmModel 上）

**选项**：
- **A（推荐）**：逐条审查所有 Open Questions，已解决的标记 `[已解决：决策 + 证据]`，引用错误的修正，未解决的保留并标注优先级。集中到 README 的一个"决策日志"节。
  - 理由：Open Questions 是活文档的一部分，必须随实现演进。
  - 代价：审查 + 更新各文档。
- **B**：保持原样，等 Phase 2 统一清理。
  - 理由：draft 阶段允许 open。
  - 代价：误导后续实施者。

**推荐决议**：A。

---

### G6: 07-ai-integration 的 GraphQL API 示例不符合 Nop 命名规范

**发现**：
- `07-ai-integration.md` §2.2 用 `metaEntity(id: ID!)`、`metaEntities(...)`、`importModule(input: ...)` 作为 GraphQL API 示例
- 但 Nop 平台的 GraphQL 命名规范是 `{BizObjName}__{methodName}`，实际暴露的是 `NopMetaEntity__findPage`、`NopMetaModule__importOrmModel`
- 文档中的 `importModule` 与实际 action `importOrmModel` 名称不一致

**为什么重要**：AI 集成文档的核心价值是"告诉 AI 怎么调用"。如果 API 示例名称与实际暴露的不一致，AI 按 schema 学习时会发现不匹配，降低信任度。

**证据**：
- `07-ai-integration.md:51` — `metaEntity(id: ID!): MetaEntity`
- 实际 GraphQL action：`NopMetaEntity__findPage`（由 CrudBizModel 自动生成）
- 实际 import action：`NopMetaModule__importOrmModel`（NopMetaModuleBizModel.java:55）

**选项**：
- **A（推荐）**：将 §2.2 示例改为 Nop 平台实际的 GraphQL 命名（`NopMetaEntity__findPage`、`NopMetaModule__importOrmModel`），并说明"GraphQL action 名由 @BizModel + @BizMutation 自动生成"。
  - 理由：与实际行为一致。
  - 代价：修改示例。
- **B**：保持概念性示例，加注"Nop 平台实际命名规范见 service-layer.md"。
  - 理由：概念性文档不需要精确命名。
  - 代价：读者需跨文档对照。

**推荐决议**：A。

---

### G7: 版本发布流程无 action 设计，实现硬编码 version=1

**发现**：
- `03-version-management.md` §4.2 描述了发布流程（UI 选择 → 检查 status → 设 released → 发布事件），但没有任何 action/API 签名设计
- 实际实现（`NopMetaModuleBizModel.importOrmModel`）硬编码 `moduleVersion=1L, status=0`（drafting），没有 release action
- version-management §1.3 说 "version = lastVersion + 1"，但并发导入时如何防止 version 冲突未设计

**为什么重要**：版本管理是 nop-metadata 的核心差异化能力（vs nop-dyn 无版本）。如果发布流程和版本递增没有设计到可实施程度，"版本管理"只是名义存在。

**证据**：
- `03-version-management.md:170-174` — 发布流程 5 步（无 API 签名）
- `NopMetaModuleBizModel.java:49` — `module.setModuleVersion(1L)` 硬编码
- `03-version-management.md:42` — "version = lastVersion + 1"（无并发控制设计）

**选项**：
- **A（推荐）**：在 version-management 文档中补充：(1) release action 签名（`@BizMutation releaseModule`）；(2) 版本递增策略（查询当前 max version + 1，或使用数据库序列）；(3) 标注"Phase 2 实现"。
  - 理由：设计文档应覆盖核心流程的 action 契约。
  - 代价：补充设计内容。
- **B**：留到实现时再设计。
  - 理由：Phase 1 只做导入，不做发布。
  - 代价：版本管理能力长期停留在"硬编码 1"。

**推荐决议**：A（补充设计，标注 Phase 2 实现）。

---

## LOW — 改进建议（3 个）

### G8: Vision Phase 划分与 plan 292 Phase 划分不一致

**发现**：`00-vision.md` 定义 5 阶段（Phase 1 = 导入+版本+搜索+血缘+质量），plan 292 定义不同 Phase 1（导入+CRUD）。两套 phase 编号会让读者混淆。

**推荐**：在 vision 中标注"Phase 编号为愿景级，实施级 plan 见 292"，或统一编号。

### G9: 10-event-model 事件 entityType 命名与 ORM 实体名不一致

**发现**：`10-event-model.md` §2.1 用 `entityType: "MetaEntity"`，但 ORM 实体名是 `NopMetaEntity`（有 Nop 前缀）。

**推荐**：统一为 ORM 实体全名或标注映射关系。

### G10: MetaTableJoin 缺少 to-one 关系声明

**发现**：`01-architecture-baseline.md` §2.5 说 leftEntityId/rightEntityId → MetaEntity，但 ORM 模型中这两个字段是裸 VARCHAR(32)，无 to-one relation 声明。无法通过 ORM 关系做 eager loading。

**推荐**：在 ORM 模型中给 MetaTableJoin 补 to-one 关系到 NopMetaEntity（leftEntity/rightEntity）。

---

## 审查结论

| 维度 | 评价 |
|------|------|
| **调研深度** | ✅ 优秀 — 15 份平台调研覆盖 DataHub/OpenMetadata/Atlas/Amundsen/Marquez/dbt/GE/Griffin/OpenRefine/PandasAI |
| **专题覆盖** | ✅ 良好 — 12 份专题文档覆盖版本/治理/导入/质量/AI/对账/事件 |
| **文档间一致性** | ❌ 不合格 — 3 个 HIGH 矛盾（G1/G2/G3），vision 与 architecture-baseline 对核心决策给出相反结论 |
| **与 ORM 一致性** | ⚠️ 部分 — 21 实体已建模，但 7 个设计文档引用的实体未建模（G3） |
| **可实施性** | ⚠️ 部分 — 核心导入引擎已实现（plan 292 Phase 1 completed），但版本发布、质量执行等核心流程缺 action 设计（G7） |
| **Open Questions 管理** | ❌ 未收敛 — 已解决的问题仍标 open，引用错误实体（G5） |

**最高优先级修复**：G1（QuerySpace 矛盾）、G2（MetaOrmModel.status 矛盾）、G3（未建模实体标注）。这三个是会让实施者"按文档编码出错"的阻塞性问题。

**建议**：创建一个修正 plan（类似 nop-app-erp 的 plan 02 文档改进），按 G1→G2→G3→G5→G4→G6→G7 顺序修复，每条修改后更新对应文档。
