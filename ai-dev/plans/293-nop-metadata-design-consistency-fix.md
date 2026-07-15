# 293 nop-metadata 设计文档一致性修复

> Plan Status: completed
> Last Reviewed: 2026-07-15
> Source: `ai-dev/analysis/2026-07/2026-07-15-grill-with-docs-nop-metadata-design-review.md`（grill-with-docs 审查）
> Related: `ai-dev/plans/292-nop-metadata-implementation-roadmap.md`（Phase 1 导入引擎）
> Draft Review: R1 已完成（独立 explore 子 agent，8 Major + 11 Minor 已修复）

## Purpose

修复 grill-with-docs 审查发现的 nop-metadata 设计文档一致性问题。审查发现 3 个 HIGH 矛盾（会让实施者按文档编码出错）和 4 个 MEDIUM 缺口。本计划将这 7 项收口到"文档间无直接矛盾、Open Questions 已收敛、未建模实体已标注"状态。

## Current Baseline

- **设计文档**：`ai-dev/design/nop-metadata/` 下 12 份设计文档（全部 `Status: draft`，同日产出）
- **ORM 模型**：`nop-metadata/model/nop-metadata.orm.xml`，21 实体已落地
- **已知矛盾**（grill 报告 G1-G7）：
  - G1：`00-vision.md` 有 **5 处**承诺跨源查询/Driver 抽象（行10/22/23/24/50），但 `01-architecture-baseline.md` §七（行448-460）和 §设计结论 9（行18）明确拒绝
  - G2：`03-version-management.md` §4.2（行171）写"检查 MetaOrmModel.status"，但 ORM 模型确认 NopMetaOrmModel 无 status 字段；NopMetaModule.status 存在（int 类型，dict `meta/module-status`：0/10/20）
  - G3：7 个设计文档引用的实体不在 ORM 模型中（详见下方清单）
  - G4：baseModuleId 示例用业务名（§2.2 行89），ORM 类型是 VARCHAR(32) surrogate UUID
  - G5：7 份文档有 Open Questions 节（01/03/04/05/07/08/10），其中已解决项未标注，跨文档重复项未合并
  - G6：`07-ai-integration.md` §2.2（行50-92）API 示例含已实现和未实现 action 混合，命名不符合 Nop 规范
  - G7：版本发布流程无 action 契约；§1.3 说"version=lastVersion+1"（导入时递增），代码 OrmModelImporter.java:47 硬编码 version=1

## Goals

- 消除 3 个 HIGH 文档矛盾（G1/G2/G3），使设计文档不再误导实施者
- 收敛 4 个 MEDIUM 缺口（G4/G5/G6/G7），使设计文档达到"可被独立审阅者信任"状态
- grill 报告中每个 HIGH/MEDIUM 发现都有明确的文档修改落地

## Non-Goals

- 补建 7 个未建模实体到 ORM 模型（Phase 2 successor，本计划只标注状态）
- 实现版本发布 action 的 Java 代码（本计划只补 action 契约设计，代码实现留 successor plan 294+）
- 修改 ORM 模型结构（G10 MetaTableJoin 加 to-one、G4 baseModuleId 加 to-one，均划为 Deferred）
- 修改 Java 代码逻辑（OrmModelImporter version 硬编码修复留 successor，本计划在文档中标注代码现状）

## Scope

### In Scope

- `00-vision.md`、`01-architecture-baseline.md`、`03-version-management.md`、`04-data-governance.md`、`05-metadata-import.md`、`07-ai-integration.md`、`08-reconciliation.md`、`10-event-model.md`、`README.md` 的文本修改

### Out Of Scope

- ORM 模型变更（`nop-metadata/model/nop-metadata.orm.xml`）
- Java 代码变更（OrmModelImporter / BizModel）
- 新增设计文档

## 未建模实体清单（G3 固化，N=7）

| 实体 | 所属文档 | vision Phase | 标注内容 |
|------|---------|-------------|---------|
| MetaDataContract | `04-data-governance.md` | Phase 4（数据治理扩展） | `> Implementation Phase: Phase 4 (尚未建模)` |
| MetaManifest | `05-metadata-import.md` | Phase 2（外部数据源注册 + Catalog 收集） | `> Implementation Phase: Phase 2 (尚未建模)` |
| MetaCatalog | `05-metadata-import.md` | Phase 2 | `> Implementation Phase: Phase 2 (尚未建模)` |
| MetaReconciliationConfig | `08-reconciliation.md` | Phase 4（Reconciliation） | `> Implementation Phase: Phase 4 (尚未建模)` |
| MetaReconciliationResult | `08-reconciliation.md` | Phase 4 | `> Implementation Phase: Phase 4 (尚未建模)` |
| MetaReconciliationEntity | `08-reconciliation.md` | Phase 4 | `> Implementation Phase: Phase 4 (尚未建模)` |
| MetaModelChangedEvent | `10-event-model.md` | 未列入 vision 5 阶段（事件基础设施） | `> Implementation Phase: 未定 (尚未建模)` |

## Execution Plan

### Phase 1 - 消除 HIGH 矛盾（G1/G2/G3）

Status: completed
Targets: `00-vision.md`

- Item Types: `Fix`（已确认的文档矛盾）

- [x] 1.1 **G1 修复**：修正 `00-vision.md` 中承诺跨源查询/Driver 抽象的全部 5 处：
  - 行10：保留"外部系统的元数据"（元数据目录不矛盾），但移除隐含的跨源查询承诺
  - 行22：将"业务数据通过 QuerySpace 路由到对应引擎"改为"业务数据通过 ORM querySpace 路由（单源，不引入 Driver 抽象）"
  - 行23：将"任意引擎"改为"Nop ORM 支持的数据库引擎"
  - 行24：将"跨源查询 | QuerySpace 多路由 + Driver 抽象"改为"单源元数据管理（跨源查询留后续阶段）"
  - 行50：成功标准 5 加"（Phase 2+：外部数据源注册；跨源查询为后续阶段）"前缀
  - 在 vision 定位段加脚注：引用 `01-architecture-baseline.md` §设计结论 9（行18）和 §七（行448-460）的拒绝决策

- [x] 1.2 **G2 修复**：修正 `03-version-management.md` §4.2（行171），将"检查所有 MetaOrmModel 的 status = released"改为"检查 MetaModule.status（NopMetaOrmModel 无独立 status 字段，跟随 MetaModule）"。同步修正 §2.1（行62）status 类型描述：从字符串枚举 `'drafting' | 'released' | 'deprecated'` 改为 int dict 值（0=DRAFTING, 10=RELEASED, 20=DEPRECATED），对齐 ORM 模型。

- [x] 1.3 **G3 修复**：
  - 为 4 份文档头部按"未建模实体清单"表的 vision Phase 标注加 `> Implementation Phase: Phase X (尚未建模)` 行（注意：05 标 Phase 2，04/08 标 Phase 4，10 标"未定"）
  - `05-metadata-import.md` 行432 现有标注"MetaManifest / MetaCatalog 实体尚未纳入..."保留，头部新增标注与之呼应（不删除行432）
  - 在 `README.md` 补充"已建模 21 实体 + 待建模 7 实体"清单，列出 7 个待建模实体名

Exit Criteria:

- [x] G1：`00-vision.md` 行10/22/23/24/50 全部不再将跨源查询/Driver 抽象作为当前能力承诺；有脚注引用 architecture-baseline §设计结论 9 + §七
- [x] G2：`03-version-management.md` §4.2 不再引用 MetaOrmModel.status；§2.1 status 类型描述与 ORM int dict 对齐
- [x] G3：4 份文档头部有 Implementation Phase 标注（按各自 vision Phase，非统一）；README 有 21+7 实体清单
- [x] `ai-dev/design/nop-metadata/README.md` 状态已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收敛 MEDIUM 缺口（G4/G5/G6/G7）

Status: completed
Targets: `03-version-management.md`、`01-architecture-baseline.md`、`04-data-governance.md`、`05-metadata-import.md`、`07-ai-integration.md`、`08-reconciliation.md`、`10-event-model.md`

- Item Types: `Fix`（已确认的文档缺口）

- [x] 2.1 **G4 修复**：在 `03-version-management.md` **§2.1**（行61）明确 baseModuleId 字段说明为"引用 MetaModule.metaModuleId（surrogate PK，VARCHAR(32) UUID）"；修正 **§2.2**（行89）示例从"baseModuleId = nop-auth v1"改为"baseModuleId = {nop-auth v1 的 metaModuleId UUID}"。ORM to-one relation 的补充列入 Deferred（见 G4-deferred）。

- [x] 2.2 **G5 修复**：逐条审查**全部 7 份文档**（01 §八 / 03 §六 / 04 §六 / 05 Open Questions / 07 Open Questions / 08 Open Questions / 10 Open Questions）的 Open Questions：
  - 已解决的（如 01 行465 isDelta 已用单表+列解决）标记 `[已解决：决策 + ORM 证据]`
  - 引用错误实体的（如 03 行209 sourceContent 应在 MetaOrmModel 上）修正
  - 与 G1 关联的（如 01 行467 MetaTableJoin 跨源路由）标注"G1 修复后：Driver 抽象被拒，跨源 join 走现有 ORM 层；剩余待设计"
  - 跨文档重复的（如 01 行468 和 04 行309 通用 Domain 来源）合并为一条，另一处改为交叉引用
  - 未解决的保留并标注优先级
  - 在 README 加"决策日志"节集中记录已解决的决策

- [x] 2.3 **G6 修复**：修正 `07-ai-integration.md` §2.2（行50-92）GraphQL API 示例：
  - 已实现的 action（findPage/findList/get/save/delete/importOrmModel）改为 Nop 命名规范（`NopMetaEntity__findPage`、`NopMetaModule__importOrmModel`），加注"action 名由 @BizModel + @BizMutation/@BizQuery 自动生成"
  - 未实现的 action（getUpstream/getDownstream/getQualityRules/searchMetadata/getMetadataContext/executeQualityCheckpoint）保留为概念性示例，每个加 `（概念性，待实现）` 标注
  - 加一段说明："以下 API 分为两类：自动生成（CrudBizModel 默认 CRUD + 已实现的 @BizMutation）和概念性（后续 Phase 实现）"

- [x] 2.4 **G7 修复**：在 `03-version-management.md` 补充版本发布设计：
  - 新增 §4.4"版本发布 action 契约"：`@BizMutation releaseModule(@Name("metaModuleId") String id)` 签名 + 行为描述（校验 status=drafting → 设 status=RELEASED → 发布 MetaModelChangedEvent）
  - 修正 §1.3（行46）版本号策略：从"version = lastVersion + 1（导入时递增）"改为"version 在 release 时递增（查询当前 moduleId 下 max(version)+1）；drafting 阶段 version 可为初始值 1"
  - 在 §4.1（行158-165）导入流程后加注："当前实现（OrmModelImporter.java:47）硬编码 moduleVersion=1L、status=0（DRAFTING）。version 递增和 release action 的代码实现留 successor plan 294+"

Exit Criteria:

- [x] G4：`03-version-management.md` §2.1 字段说明和 §2.2 示例均明确 baseModuleId 引用 surrogate PK
- [x] G5：全部 7 份文档的 Open Questions 已逐条标注状态；跨文档重复项已合并；README 有决策日志
- [x] G6：`07-ai-integration.md` §2.2 已实现 action 用 Nop 命名，未实现 action 有"概念性，待实现"标注
- [x] G7：`03-version-management.md` 有 releaseModule action 契约 + §1.3 version 递增时机已修正 + 导入流程有代码现状标注
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯文档计划：`./mvnw test`、`./mvnw lint` 等构建验证条目不适用。

- [x] 3 个 HIGH 矛盾（G1/G2/G3）已消除——文档间无直接矛盾
- [x] 4 个 MEDIUM 缺口（G4/G5/G6/G7）文档侧已收敛
- [x] G7 代码侧（OrmModelImporter version 硬编码）已显式标 successor，未静默降级
- [x] 不存在被静默降级到 deferred 的 in-scope 发现
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 无 nop-metadata 相关新增断链
- [x] 文档中的 action 名（`NopMetaModule__importOrmModel` 等）与代码 `@BizModel`/`@BizMutation` 一致性已人工核对
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/293-nop-metadata-design-consistency-fix.md --strict` 退出码 0

## Deferred But Adjudicated

### G4-baseModuleId-to-one: baseModuleId 缺少 to-one relation

- Classification: `optimization candidate`
- Why Not Blocking Closure: 需要改 ORM 模型（NopMetaModule 加 baseModule 自引用 to-one relation）+ 重新 codegen，属于代码变更。当前文档侧已明确引用目标（surrogate PK），手工查询可替代 ORM join
- Successor Required: yes
- Successor Path: successor plan 294+（nop-metadata Phase 2 ORM 迭代）

### G8: Vision Phase 划分与 plan 292 不一致

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: vision 的 5 阶段是愿景级路线图，plan 292 是实施级。本计划 slice 1.3 已按 vision phase 标注未建模实体，phase 编号一致性问题降为"vision 加一句说明"的锦上添花
- Successor Required: no

### G9: 事件 entityType 命名与 ORM 实体名不一致

- Classification: `watch-only residual`
- Why Not Blocking Closure: `10-event-model.md` 的 entityType 用"MetaEntity"，ORM 实体名是"NopMetaEntity"。事件模型尚未实现（标注为"未定 Phase"），实现时自然对齐
- Successor Required: no

### G10: MetaTableJoin 缺少 to-one 关系

- Classification: `optimization candidate`
- Why Not Blocking Closure: 需要改 ORM 模型（NopMetaTableJoin 加 2 个 to-one relation）+ 重新 codegen，属于代码变更。与 G4-baseModuleId-to-one 同类问题，一并留 successor
- Successor Required: yes
- Successor Path: successor plan 294+（nop-metadata Phase 2 ORM 迭代）

### G7-code: OrmModelImporter version 硬编码

- Classification: `optimization candidate`
- Why Not Blocking Closure: OrmModelImporter.java:47 `setModuleVersion(1L)` 和行48 `setStatus(0)` 硬编码。文档侧已设计 release action 契约和 version 递增策略（§4.4 + §1.3 修正），代码实现需要新增 releaseModule BizModel 方法 + 修改 OrmModelImporter，属于代码变更
- Successor Required: yes
- Successor Path: successor plan 294+（nop-metadata Phase 2 版本管理实现）

## Non-Blocking Follow-ups

- G8（vision phase 标注）：Phase 2 顺手在 vision 加一句"Phase 编号为愿景级，实施级 plan 见 292/293"
- G9（事件命名对齐）：Phase 4 实现事件模型时统一 entityType 为 NopMetaEntity 全名

## Closure

Status Note: 设计文档一致性修复已完成。3 个 HIGH 矛盾（G1/G2/G3）已消除，4 个 MEDIUM 缺口（G4/G5/G6/G7）已收敛。5 个 deferred 项诚实标注，无 in-scope 发现被静默降级。
Completed: 2026-07-15

Closure Audit Evidence:

独立子 agent（fresh session）closure audit 于 2026-07-15 执行（`ses_09a4eeb41ffemXVOLA3buaZEWp`）：
- Phase 1 Exit Criteria（G1/G2/G3）：全部 PASS（7+3+5 项子验证）
- Phase 2 Exit Criteria（G4/G5/G6/G7）：全部 PASS（3+3+3+4 项子验证）
- Anti-Hollow：全部为实质性语义修改，无 placeholder
- Deferred 诚实性：5 项均真实 out-of-scope
- Doc links：0 nop-metadata 相关断链（--strict 退出码 0）
- 文本一致性：Plan Status/Phase Status/Exit Criteria/Closure Gates/日志 全部一致
