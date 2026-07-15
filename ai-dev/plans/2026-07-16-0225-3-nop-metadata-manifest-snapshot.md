# 3 nop-metadata Manifest 元数据快照

> Plan Status: active
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P2（P2-3）；`ai-dev/design/nop-metadata/05-metadata-import.md` §三 Manifest 元数据模型 + §五 依赖图计算；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §4.1
> Mission: nop-metadata
> Work Item: P2-3 — MetaManifest 快照
> Related: `292-nop-metadata-implementation-roadmap.md`、`295-nop-metadata-delta-expansion-and-version-release.md`（P1/P1+ 提供可快照的逻辑元数据）

## Purpose

参考 dbt Manifest 模式，为 nop-metadata 已导入的模块生成完整元数据快照（模块/模型/实体/字段/关系 + 依赖图 parentMap/childMap），使依赖分析、影响分析、搜索增强、AI 集成能基于一份自包含的快照工作。本 plan 只覆盖**逻辑元数据快照**（基于 P1 已导入的 ORM 元数据），不涉及外部库运行时统计（P2-4 Catalog）。

## Current Baseline

- **可快照的逻辑元数据已存在**（P1/P1+ done）：NopMetaModule / NopMetaOrmModel(isDelta) / NopMetaEntity / NopMetaEntityField / NopMetaEntityRelation / NopMetaEntityUniqueKey / NopMetaEntityIndex / NopMetaDomain / NopMetaDict / NopMetaDictItem / NopMetaTable(tableType=entity) 全部已建模并可通过 importOrmModel 填充。
- **NopMetaManifest 实体不存在**：`nop-metadata.orm.xml` 当前 21 实体中**无** NopMetaManifest（确认：实体列表无该 className）。`未建模实体` 表将其列为 P2 待建模。
- **依赖图计算不存在**：无 parentMap/childMap 计算逻辑。`NopMetaEntityRelation` 存在，其 `refEntityName`（约 orm.xml:571，precision=200，**非 mandatory**）存的是被引用实体的 **className**（如 `io.nop.metadata.dao.entity.NopMetaModule`），不是 entityName —— 推导边时需 className→entity→module 反查（见 D4）。`NopMetaEntity` 无直接 metaModuleId，需经 `metaOrmModelId → NopMetaOrmModel.metaModuleId` 二跳获取模块。
- **设计契约**：`05-metadata-import.md` §三 定义了 Manifest 结构（metadata / nodes{entity,table,measure} / sources / exposures / parentMap / childMap）+ JSON 示例（注意 §3.2 示例的边是 `table→source`，首版按 D3 只做 entity→entity，table→source 不纳入）。§五 定义了依赖图计算。该 doc 当前 `Status: draft` 且含 Python 伪码 + Open Questions（item 1.1 须顺带重写为最终设计状态）。
- **快照粒度（Decision 待定）**：设计文档示例按 moduleId 生成；但"全局 vs 每模块版本"未裁定。**item 1.1 为硬前置门禁**。
- **存储形态（Decision 待定）**：Manifest 是存为单行 JSON CLOB（snapshot），还是规范化多表？设计示例是单 JSON 文档。**item 1.2 裁定**。
- **生成时机**：设计 §2.1 列出导入时自动 / 手动 / 定时。本 plan 首版做手动 action + 可选导入时触发。
- **测试基建**：Nop AutoTest 可用，可导入 nop-metadata 自身 orm.xml 后生成 Manifest 并断言。`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS。

## Goals

- 新建 `NopMetaManifest` 实体（按 item 1.2 存储形态落地），含模块关联 + 生成时间 + Manifest 内容
- `generateManifest` GraphQL mutation action 可用：按 moduleId（或全局，按 item 1.1 粒度）从已导入元数据构建 nodes/sources + parentMap/childMap，写入 NopMetaManifest
- 依赖图（parentMap/childMap）至少覆盖表级依赖（来自 MetaEntityRelation），可被 `getAncestors/getDescendants` 风格查询验证
- AutoTest 验证：导入模块 → 生成 Manifest → 断言 nodes 非空 + parentMap/childMap 含至少一条表级依赖边

## Non-Goals

- 外部库运行时统计（行数/大小/索引/分区）—— P2-4 Catalog，本 plan successor
- 血缘边（MetaLineageEdge）作为依赖图来源 —— P2-5 血缘采集；本 plan 依赖图来源限定 MetaEntityRelation + MetaTableMeasure，不含 sql_parse/open_lineage
- SQL 视图节点的 SELECT 解析 —— P3
- 定时自动生成调度（首版手动 action；导入时触发为可选 follow-up）
- Manifest 版本化 / 增量更新 / 循环依赖检测（设计 Open Questions，首版不做）

## Design Decisions

> D1/D2/D4 为硬前置门禁，须在 item 1.1/1.2 裁定并写入 `05-metadata-import.md` / `01-architecture-baseline.md` 后实现。

### D1. 快照粒度（待 item 1.1 裁定）

- 候选：每模块版本一条 Manifest（`metaModuleId` 关联）/ 全局一条 Manifest。
- **推荐**：每模块版本一条（与模块版本管理粒度对齐，base 版本删除后该模块 Manifest 仍可查，符合架构 §3.3 版本不变量）。item 1.1 基于该推荐确认。

### D2. 存储形态（待 item 1.2 裁定）

- 候选 A：单行 NopMetaManifest，内容为 JSON CLOB（nodes/sources/parentMap/childMap 序列化为一个 JSON 文档）。
- 候选 B：规范化多实体（ManifestNode / ManifestEdge）。
- **推荐 A**：与 dbt manifest.json 对齐，自包含、易导出/喂给 AI，查询频率低。item 1.2 确认后落地 NopMetaManifest 列定义（至少：manifestId / metaModuleId / manifestVersion / generatedAt / nopMetadataVersion / content + 审计）。**content 列**：仓库无 `domain="json"`，`json-4000`(VARCHAR 4000) 对整模块 Manifest 远不够；item 1.2 须裁定 content 用 `domain="mediumtext"` + `stdDomain="json"`（能装下大 JSON），不得用 json-4000。

### D3. 依赖图来源与边语义（首版范围）

- parentMap/childMap 首版仅来自 **MetaEntityRelation**。列级依赖（SQL 解析）、血缘（MetaLineageEdge）、指标依赖（MetaTableMeasure）留待各自 Phase，不阻塞本 plan 结果面。
- **边连接的节点类型**：首版 relation 推导的边为 **`entity` 节点 → `entity` 节点**（实体级依赖图）。设计 `05-metadata-import.md` §3.2 示例里的 `table → source` 边属于另一类（表依赖数据源），**首版不纳入**（不在 D3 来源范围内），避免节点层混淆。

### D4. 节点 id 与边 resolution 规格（硬前置，须 item 1.1 一并裁定并写入 `05-metadata-import.md` §五）

- **uniqueId 中模块标识取值**：取 **`NopMetaModule.moduleId`**（业务标识，如 `nop/auth`，在唯一键 `UK_NOP_META_MODULE_ID_VER` 内），**不取** `moduleName`（显示名，不在唯一键）也不取 seq PK `metaModuleId`。
- **slash→dot 归一化**：`moduleId` 含 `/`（如 `nop/auth`），代入点分隔 uniqueId 前须将 `/` 归一化为 `.`（→ `nop.auth`），使 uniqueId 与 design §3.2 示例 `entity.nop.auth.ErpAcctScheme` 一致、且按 `.` split 不碎。
- **`<name>` 取值**：取 `NopMetaEntity.entityName`。
- **节点 uniqueId**：`entity.<moduleId 归一化>.<entityName>`（首版仅 entity 节点）。例：moduleId=`nop/auth`、entityName=`NopMetaUser` → `entity.nop.auth.NopMetaUser`。
- **relation → 边 resolution**：`NopMetaEntityRelation.refEntityName` 存的是被引用实体的 **className**（如 `io.nop.metadata.dao.entity.NopMetaModule`）。resolution 算法：className → 全局反查 NopMetaEntity(className) → 其所属 module(moduleId) → 归一化 → 目标 uniqueId。（注：`NopMetaEntity` 无 className 索引，首版全表反查可接受，性能不足后续加 IX。）
- **跨模块/未导入引用（dangling）降级策略（不得静默丢弃）**：被引用实体无法解析到节点时，该边的目标端记为 `unresolved:<className>` 显式保留在 parentMap/childMap 中（带标记），并在生成日志中记录 unresolved 计数；不静默跳过、不丢边。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：新增 `NopMetaManifest` 实体（按 D2 形态）+ to-one metaModule
- 新增 Manifest 构建服务：从已导入元数据聚合 nodes + sources + parentMap/childMap（来源 MetaEntityRelation）→ 序列化 JSON
- `NopMetaModuleBizModel`（或新 BizModel）：新增 `generateManifest` action
- AutoTest：导入 → 生成 → 断言 nodes/parentMap/childMap

### Out Of Scope

- P2-4 Catalog 运行时统计
- P2-5 血缘作为依赖来源
- P3 SQL 视图解析
- Manifest 增量/版本化/循环检测/定时调度

## Execution Plan

### Phase 1 - Manifest 实体建模 + 依赖图计算 + generateManifest action

Status: planned
Targets: `nop-metadata/model/nop-metadata.orm.xml`、新增 Manifest 构建服务、`NopMetaModuleBizModel.java`（或新 BizModel）、`TestNopMetaModuleBizModel.java`

- Item Types: `Decision`（D1 粒度 / D2 存储形态 / D4 节点 id 与边 resolution，硬前置）+ `Proof`（新功能：Manifest 生成）

> **硬前置门禁（item 1.1/1.2）**：快照粒度、存储形态、节点 id 与边 resolution 规格必须先裁定并落地 ORM 实体，再实现构建逻辑。

- [ ] 1.1 **快照粒度 + 节点 id/边 resolution 决策（硬前置门禁）**：按 D1 裁定快照粒度（推荐每模块版本一条）；按 D4 裁定节点 uniqueId（取 `moduleId` 并 slash→dot 归一化 → `entity.<归一化moduleId>.<entityName>`）+ relation→边 resolution（className 反查）+ dangling 策略。结论写入 `05-metadata-import.md` §三/§五 + `01-architecture-baseline.md`。**顺带把 `05-metadata-import.md` 从 draft 重写为最终设计状态**（去掉 Python 伪码、收敛相关 Open Questions，满足 Rule 14）
- [ ] 1.2 **存储形态决策（硬前置门禁）**：按 D2 裁定（推荐 JSON CLOB 单行）。在 `nop-metadata.orm.xml` 新增 `NopMetaManifest` 实体（列：manifestId(PK) / metaModuleId(→MetaModule) / manifestVersion / generatedAt / nopMetadataVersion / **content(domain="mediumtext", stdDomain="json"，不得用 json-4000)** + 审计）+ to-one metaModule 关系。运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码确认 BUILD SUCCESS
- [ ] 1.3 新增 Manifest 构建服务：输入 metaModuleId → 查询该模块的 MetaOrmModel(full,isDelta=false) → MetaEntity/MetaEntityField → 构建 nodes（**首版仅 entity 节点**，uniqueId 按 D4：`entity.<moduleId 归一化>.<entityName>`）+ sources（来自 MetaDataSource / querySpace）
- [ ] 1.4 依赖图计算：遍历 MetaEntityRelation → 按 **D4 resolution 规格**（refEntityName=className → 反查 entity → module → uniqueId）构建 parentMap/childMap（**entity→entity 边**，按 D3）。无关系的节点 parentMap/childMap 为空数组（不静默跳过，显式空）。跨模块/未导入引用按 D4 dangling 策略记为 `unresolved:<className>`（不丢边、不静默跳过），并在日志记录 unresolved 计数
- [ ] 1.5 序列化 nodes/sources/parentMap/childMap 为 JSON（content），写入 NopMetaManifest（generatedAt=now, nopMetadataVersion 取平台版本）
- [ ] 1.6 在 BizModel 新增 `@BizMutation generateManifest(@Name("metaModuleId") String id, IServiceContext context)` → 返回生成的 NopMetaManifest（或 manifestId）。moduleId 不存在抛 inline ErrorCode（快速失败）
- [ ] 1.7 在 TestNopMetaModuleBizModel 新增测试 `testGenerateManifest`：导入 nop-metadata orm.xml → generateManifest → 解析 content JSON 断言：① nodes 非空且含 entity 节点（uniqueId 形如 `entity.<归一化moduleId>.<entityName>`）；② parentMap/childMap 至少含一条由本模块内 MetaEntityRelation 推导的 entity→entity 边——**钉死一条预期样例边**（执行时先查 nop-metadata 自身 orm.xml 中某条 to-one relation，给出其两端解析后的 uniqueId，断言该边存在）；③ unresolved 边若有则带 `unresolved:` 标记

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。

- [ ] `NopMetaManifest` 实体已建模（按 D2 形态，content 用 mediumtext+stdDomain json），`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS，生成代码含实体类
- [ ] `generateManifest` 可通过 GraphQL mutation 调用，返回/写入 NopMetaManifest（content 非空 JSON）
- [ ] content JSON 含 nodes（entity 节点，uniqueId 形如 `entity.<归一化moduleId>.<entityName>`，如 `entity.nop.auth.NopMetaUser`）+ sources
- [ ] parentMap/childMap 至少含一条由 MetaEntityRelation 推导的 entity→entity 边（按 D4 resolution）
- [ ] moduleId 不存在时抛异常（快速失败，不静默返回空 Manifest）
- [ ] **端到端验证**：从 `importOrmModel` → `generateManifest` → content JSON 可解析且含节点与依赖边的完整路径已验证
- [ ] **接线验证**：generateManifest 运行时确实查询了 MetaEntityRelation 并写入 parentMap/childMap（测试 testGenerateManifest 断言特定 entity→entity 边存在证明），非空壳
- [ ] **无静默跳过**：无关系的节点显式空数组；跨模块/未导入引用记为 `unresolved:` 不丢边；moduleId 缺失抛异常；无空方法体或吞异常
- [ ] **新功能测试**：新增 testGenerateManifest 覆盖 生成 + nodes/parentMap 断言（含预期边两端 uniqueId）+ 缺失 moduleId 快速失败，全绿
- [ ] `ai-dev/design/nop-metadata/05-metadata-import.md` §三 快照粒度/存储形态（按 D1/D2）+ §五 节点 id 与边 resolution 规格（按 D4）已更新；**该 doc 顺带从 draft 重写为最终设计状态**（去掉 Python 伪码、收敛相关 Open Questions，满足 Rule 14）；`01-architecture-baseline.md` Manifest 实体说明已更新
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：所有条目 + Phase Exit Criteria 全部 `[x]` 后才能 `completed`。

- [ ] NopMetaManifest 实体已建模并重新生成代码
- [ ] generateManifest 端到端可用（导入 → 生成 → content JSON 含 nodes + 依赖图）
- [ ] 依赖图（parentMap/childMap）来自 MetaEntityRelation 且可验证
- [ ] 不存在空壳实现（无空方法体 / 静默跳过 / 吞异常）
- [ ] 必要 focused verification 已完成（生成 + 断言 + 缺失快速失败测试全绿）
- [ ] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [ ] 受影响的 owner docs 已同步（`05-metadata-import.md` §三 + `01-architecture-baseline.md` Manifest 实体/契约）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证 generateManifest 运行时确实聚合元数据并计算依赖图（content 含真实节点/边），非空壳
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-16-0225-3-nop-metadata-manifest-snapshot.md --strict` 退出码 0

## Deferred But Adjudicated

### 列级依赖 / 血缘依赖 / 指标依赖作为依赖图来源

- Classification: `optimization candidate`
- Why Not Blocking Closure: 依赖图首版来源限定 MetaEntityRelation（表/实体级）即满足 Manifest 核心结果面。列级（SQL 解析，依赖 P3）、血缘（P2-5）、指标依赖各自有归属 Phase，不阻塞本 plan
- Successor Required: yes（列级随 P3；血缘随 P2-5）

## Non-Blocking Follow-ups

- 导入时自动触发 generateManifest（当前手动 action）
- Manifest 增量更新 / 版本化 / 循环依赖检测（设计 Open Questions）
- Manifest 全局聚合视图（跨模块 nodes 合并）

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
