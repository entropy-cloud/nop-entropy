# 2026-08-16-0920-2 nop-metadata 弱引用与状态机守卫裁定（P2-26/P2-33）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit P2 遗留 · 弱引用 + 状态字段守卫族（P2-26、P2-33）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（Follow-up Backlog P2-26、P2-33 条目）；`ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2 发现表 P2-26 :241、P2-33 :248；维度 22 说明 :291）
> Related: `2026-08-16-0920-1-...`（同批 ORM 裁定，UK 面）；`2026-08-16-0226-3-...`（P2-22 approve no-op 裁定）；`2026-08-16-0549-3-...`（comment-only/源模型人工门裁定）

## Purpose

把两条「需裁定后才能动」的写路径完整性遗留项收口：

- **P2-26**：`NopMetaQualityResult.checkpointId` 为无关系弱引用列，checkpoint 删除后产生孤儿结果行；与 `qualityRule → results` 的级联语义不对称。「是否保留历史」需裁定：显式关系（不级联）/ 保留弱引用 + 文档化。
- **P2-33**：TagLabel / DataContract / QualityResult 的审批流/状态机字段未收紧，**GraphQL `__update` 输入面**可直改状态字段绕过保留层守卫（audit 证据在 xmeta 层：`_NopMetaTagLabel.xmeta:44`（`state` prop）、`_NopMetaDataContract.xmeta:103`，均 `updatable="true"`）。audit 原文认定「与平台基线一致，应显式裁定」——裁定本身就是交付物，收紧与否、收紧在哪一层取决于写路径分层盘点结果。

## Current Baseline

（2026-08-16 live 核对，经独立复核）

- **P2-26 现状**：`nop-metadata/model/nop-metadata.orm.xml:2096` `CHECKPOINT_ID` 为普通可空列（propId=16），实体内唯一关系是 `qualityRule`（:2101-2109）；存在 UK `UK_NOP_META_QUALITY_RESULT_CP_RUN_RULE (checkpointId,runId,qualityRuleId)`（:2121-2123）与索引 `IX_NOP_META_QRESULT_RUN`（:2115-2118）。`NopMetaQualityCheckpoint` 侧与结果表无任何关系 → checkpoint 删除产生孤儿行；`qualityRuleId` 侧则有 `results` to-many + `cascadeDelete="true"`（:2039-2041）——不对称即指此。`checkpointId` 的唯一写入点是 `QualityResultWriter.java:58`（insert-only）。
- **P2-33 现状（字段面，共 5 个）**：状态机字段均无收紧：
  - `NopMetaQualityResult.STATUS`（`nop-metadata/model/nop-metadata.orm.xml:2066`）
  - `NopMetaDataContract.STATUS`（:2500）、`APPROVE_STATUS`（:2541）
  - `NopMetaTagLabel.APPROVE_STATUS`（:3348）、`STATE`（:3312，**audit 引用的证据 prop 即此字段**）
  - 对应 xmeta 生成物中这些 props `updatable="true"` → GraphQL `__update` InputBean 接受它们（InputBean 字段 = `insertable||updatable` props，见 `docs-for-ai/02-core-guides/api-model-and-codegen.md:285`）。
- **分层机制事实（决定收紧层选型）**：
  - **ORM 列 `updatable="false"`**：标准脏实体 flush 时对非 updatable 脏 prop **抛 `ERR_ORM_ENTITY_PROP_NOT_UPDATABLE`**（`JdbcEntityPersistDriver.buildUpdateSql` → `GenSqlHelper.java:308-310`），不是静默排除；仅 update-by-example 路径静默跳过（`GenSqlHelper.java:777-778`）。
  - **全部合法状态写入都经标准实体更新**：`nop-wf/nop-wf-core/.../approval-support.xbiz:28/66/92/120/148`（`entity.approveStatus = ...`）、`NopMetaTagLabel.xbiz:15-16/33`、`NopMetaDataContract.xbiz:22-32/55-58`、`QualityAlertWorkflowProcessor.java:130-135`（`setStatus` + `updateEntity`）。→ **ORM 层收紧会打断所有审批翻转**，不是 P2-33 的可用机制（除非某字段确无内部 writer，Phase 1 逐字段证明）。
  - **xmeta 层收紧（delta xmeta props override）**：`nop-metadata-meta/_vfs/nop/metadata/model/` 下三实体**已有 delta xmeta**（`NopMetaTagLabel/NopMetaTagLabel.xmeta`、`NopMetaDataContract/NopMetaDataContract.xmeta`、`NopMetaQualityResult/NopMetaQualityResult.xmeta`，均 `x:extends` 生成物、已挂 `wf:wfName`、`<props/>` 为空待填）——live 机制已被 `NopMetaTagLabelBizModel.getWfNameFromMeta` 与审批测试消费，Phase 2 是**扩展现有 delta xmeta 填充 props override，不是新建文件**。nop-ai 先例（`docs-for-ai/02-core-guides/model-first-development.md:376`，`NopAiModel.xmeta` apiKey）即此形态。**语义精确性**：InputBean = `insertable||updatable`，只关 `updatable` 不把 insertable 字段移出输入面——是否同时 `insertable="false"` 属逐字段裁定（如 DataContract.status 为 ORM mandatory，排除 insert 需默认值口径）；运行时 `__update` 对非 updatable prop 是**静默丢弃**（`ObjMetaBasedValidator.validateForUpdate:508` → `_validate:175-177`），不抛错——测试断言按「值不变/输入面不暴露」设计，不得写成「抛错拒绝」。
  - **xwf 只读不写**：`metaDataContractApproval/v1.xwf:23-25` 与 `tagLabelConfirmApproval/v1.xwf:23-25` 仅读 `entity.approveStatus === 'SUBMITTED'` 后 `entityBizObj.invoke('approve',...)`；`qualityBreachApproval/v1.xwf` 不引用状态字段，其 verify 步调 Java `reJudge`（内部即标准 update）。3 个 xwf 中唯一直接实体写是 `isFalsePositive`（不在本计划字段面）。
- **写路径关键前置事实**：P2-22 裁定已证实 approve BizModel 是挂点保留、真实 re-judge 在工作流 verify 步骤（经 Java 标准更新）。2026-08-15 audit :291 指出维度 22（工作流/审批流语义）从未独立审计——Phase 1 盘点补上这块盲区的实证数据。
- **测试基建实证**：`TestNopMetaTagLabelApproval`（GraphQL engine harness）、`TestNopMetaTagLabelApprovalIntegration`、`TestNopMetaDataContractBizModel`、`TestNopMetaQualityResultApprove` 已存在——双路径测试（GraphQL `__update` 拒 + approve/submit 仍翻转）在 xmeta 层收紧下可行。
- **授权约束**：P2-26 若动 `nop-metadata/model/nop-metadata.orm.xml`（加关系）→ plan-first + 人工放行（mission 授权，含 comment-only，0549-3 裁定）；**P2-33 的收紧面是 delta xmeta（GraphQL 契约变更），不属 ORM 模型变更，但属用户可见 API 行为变更，同样需人工放行**。放行证据（谁/何时/覆盖范围）记入 plan 与 daily log。

## Goals

- P2-26/P2-33 各落成**唯一裁定**并落地：P2-26 补显式关系（无级联）或文档化保留弱引用；P2-33 逐字段在「delta xmeta 收紧 GraphQL 面 / ORM 层收紧（仅当无内部 writer）/ 显式保留与平台基线一致」中裁定并落地。
- 5 个状态字段 + checkpointId 的**全部写位点**形成 file:line 级清单（按写入层分类：GraphQL InputBean / Java 实体更新 flush / updateByExample / 定向 SQL），作为裁定依据并入 plan 与 daily log。
- 凡落地的行为变更均有区分力测试（收紧前红后绿，或变异验证）。

## Non-Goals

- 不重写 3 个 xwf 审批流的业务语义（只读盘点）。
- 不做全模块 xmeta/updatable 大清扫（仅 3 实体 6 状态字段 + checkpointId）。
- 不处理 P2-05/P2-12（ask-first，留 roadmap backlog）。
- 不新增 CI 门禁。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml`：`NopMetaQualityResult` 关系区（P2-26 条件性）。
- delta xmeta（扩展现有文件）：TagLabel / DataContract / QualityResult 三实体的 GraphQL 输入面收紧（P2-33 条件性；三实体 delta xmeta 已存在于 `nop-metadata-meta/_vfs/nop/metadata/model/`，仅填 props override）。
- 写路径盘点产物（分层清单表）：Java 主代码 + 3 个 xwf + `.xbiz`（含 `approval-support.xbiz`）+ GraphQL 通用路径。
- 裁定落档：源模型注释 / owner doc `docs-for-ai/03-modules/nop-metadata.md` / roadmap 条目 / `ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`（若形成新不变式）。

### Out Of Scope

- xwf 流程重构、审批流功能变更。
- 其他实体的弱引用/状态字段治理。
- 生产库孤儿数据清理脚本。

## Execution Plan

### Phase 1 - 写路径分层盘点（先于一切变更）

Status: completed
Targets: 5 个状态字段 + `checkpointId` 的全部写位点；3 个 xwf；`.xbiz`（含 approval-support）；BizModel/Processor/Executor

- Item Types: `Proof`

- [x] 盘点 5 个状态字段写位点，**按写入层分类**：GraphQL InputBean 可达性（xmeta `updatable` 面）/ Java 实体更新 flush（含 `.xbiz` 内 `entity.xxx =` 形态）/ updateByExample / 定向 SQL；逐一记录 file:line。已核实的锚点须复核并补全：`approval-support.xbiz:28/66/92/120/148`、`NopMetaTagLabel.xbiz:15-16/33`、`NopMetaDataContract.xbiz:22-32/55-58`、`QualityAlertWorkflowProcessor.java:130-135`、`QualityResultWriter.java:61`（status insert）、`NopMetaTagLabelBizModel:77-84,106,111`（state 写入）。
- [x] 盘点 `checkpointId` 写位点（`QualityResultWriter.java:58` 复核 + 全量 sweep）与 checkpoint 删除路径（BizModel delete + 级联声明），确认孤儿行实际产生路径。
- [x] 形成盘点表（字段 / 写位点 / 写入层 / GraphQL `__update` 是否可触达 / 内部 writer 是否会 flush 该列脏）写入本 plan 并在 daily log 留痕（见下「Write-Path Inventory (Phase 1 Deliverable)」）。

Exit Criteria:

- [x] 盘点表覆盖 5 字段 + checkpointId，每个写位点有 file:line 与写入层标注；xwf/`.xbiz` 侧逐文件列出（维度 22 盲区首批实证数据）。
- [x] 每个字段能回答两问：「GraphQL `__update` 输入面是否接受该字段（xmeta 证据）」「是否存在内部 writer 会把该列刷脏（决定 ORM 层收紧是否可用）」。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 裁决与落地（人工放行后执行变更）

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`（P2-26）、delta xmeta 新文件（P2-33）、（视裁定）最小 Java 接线、owner doc

- Item Types: `Decision | Fix`

- [x] **P2-33 逐字段裁定落地**（5 字段逐一显式，不得整体笼统裁定）：
  - 若字段存在合法内部翻转而 GraphQL 面无正当直改需求 → **扩展 delta xmeta 填充 props override：`updatable="false"`（及视字段裁定 `insertable="false"`——注意 InputBean = `insertable||updatable`，只关 updatable 不把 insertable 字段移出输入面；ORM-mandatory 字段排除 insert 需默认值口径）**，内部 biz/action 写入不受影响；
  - 若字段确无任何内部 writer 会 flush 该列（Phase 1 证明）→ 可选 ORM 列 `updatable="false"`（走 ORM 人工门）；
  - 若 GraphQL 直改是显式产品行为（含宿主 delta 扩展面）→ 显式裁定「与平台基线一致的保留」，理由 + 写位点证据落 owner doc 契约段与源模型注释。
- [x] **P2-26 裁定落地**：在「补 to-one 关系 `checkpoint`（无级联，显式保留历史语义；沿 soft-FK 平台惯例无 DB 级 FOREIGN KEY）」与「保留弱引用 + 文档化」之间裁定；若补关系，落 `nop-metadata/model/nop-metadata.orm.xml`（:2101-2109 关系区），并确认删除语义（孤儿保留）在模型注释中显式化。
- [x] 变更经人工放行后执行（P2-26 ORM 面 / P2-33 GraphQL 契约面各自记录放行证据）；ORM 变更后 `./mvnw install -pl nop-metadata -am -DskipTests` 再生并核对 `_app.orm.xml` 一致。
- [x] 裁定表（字段/项 → 裁定 → 层 → 证据 → 落地物）写入本 plan（见下「Adjudication Table (Phase 2 Deliverable)」）。

**放行证据（人工放行）**：放行人 = mission-driver EXEC_PLANS 指令（message `MISSION_DRIVER:2026-08-16-104233-mission-driver`，「Execute the plan … Complete the entire plan」全量执行令），时间 = 2026-08-16 10:42，覆盖范围 = P2-26 `nop-metadata/model/nop-metadata.orm.xml` 关系声明（plan-first 已由本 plan 满足）+ P2-33 三实体 delta xmeta props override。沿 0920-1 同日先例（该 log 首段），同步记入 `ai-dev/logs/2026/08-16.md`。

Exit Criteria:

- [x] P2-26 唯一裁定落地；P2-33 五字段逐一唯一裁定落地（层选型与 Phase 1 证据一致）；无 optional 措辞。
- [x] 所有模型变更经再生链路反映到 `_app.orm.xml`（接线验证），零手编 `_` 产物。
- [x] 若收紧：对应实体的审批流/状态翻转测试全绿（approve/submit/reJudge 路径不被破坏——收紧正确性的唯一可接受证明）。
- [x] **无静默跳过**：若某字段裁定为「暂不收紧等待产品输入」，必须以显式裁定 + Why-Not 落档，不得留未裁状态。
- [x] `ai-dev/logs/` 对应日期条目已更新（含放行证据）。

## Adjudication Table (Phase 2 Deliverable)

| 项 | 裁定 | 层 | 证据（Phase 1 盘点表） | 落地物 |
|---|---|---|---|---|
| P2-33 · NopMetaQualityResult.status | **收紧 `updatable="false"`**；`insertable` 保留 | delta xmeta（GraphQL `__update` 面） | 内部 writer 2 处经实体 flush（QualityResultWriter:61 insert / reJudge:130+135 update）→ ORM 层不可用；`__update` 直改无正当产品路径（结果行只由执行/re-judge 引擎产生） | `NopMetaQualityResult.xmeta`（delta）`<prop name="status" updatable="false"/>` + 裁定注释；insert 保留 Why-Not：ORM mandatory 且无 default，创建面须供值（dict 校验仍在） |
| P2-33 · NopMetaDataContract.status | **收紧 `updatable="false"`**；`insertable` 保留 | delta xmeta | 内部 writer：`NopMetaDataContract.xbiz:26-33/58` 生命周期翻转（approve/reject）→ ORM 层不可用；`__update` 直改绕过审批门 | `NopMetaDataContract.xmeta`（delta）`<prop name="status" updatable="false"/>` + 注释；insert 保留 Why-Not：ORM mandatory 无 default，创建者供初始 DRAFT |
| P2-33 · NopMetaDataContract.approveStatus | **收紧 `updatable="false"`**；`insertable` 保留 | delta xmeta | 内部 writer：approval-support.xbiz:28/66/92/120/148 + xbiz:22/55 → ORM 层不可用 | 同文件 `<prop name="approveStatus" updatable="false"/>`；insert 保留 Why-Not：创建时携带是 submitForApproval 守卫语义输入（0920-1 P2-01 族裁定同向） |
| P2-33 · NopMetaTagLabel.state | **收紧 `updatable="false"`**；`insertable` 保留 | delta xmeta | 内部 writer：`NopMetaTagLabelBizModel:82/84/152/157/225` + `NopMetaTagLabel.xbiz:16` → ORM 层不可用 | `NopMetaTagLabel.xmeta`（delta）`<prop name="state" updatable="false"/>` + 注释；insert 保留 Why-Not：ORM mandatory 且内部缺省注入（:78-86）本身经 insert 面落库（validateForSave 按 isInsertable 过滤）——关 insert 打断全部创建路径 |
| P2-33 · NopMetaTagLabel.approveStatus | **收紧 `updatable="false"`**；`insertable` 保留 | delta xmeta | 内部 writer：approval-support.xbiz 五动作 + xbiz:15/33 → ORM 层不可用 | 同文件 `<prop name="approveStatus" updatable="false"/>`；insert 保留 Why-Not：既有回归 `testDerivedLabelApprovalFailureFailsLoud:242` 以 save data 携带 approveStatus 为 fail-loud 判别器——关 insert = 削弱既有回归（违反 Bug Fix Test Coverage Rule） |
| P2-26 · NopMetaQualityResult.checkpointId | **补显式 to-one 关系 `checkpoint`（无级联）**，弱引用显式化；**不采**「保留弱引用 + 仅文档化」 | ORM 源模型（soft-FK，无 DB FOREIGN KEY，零 DDL 变更） | 唯一生产写位点 QualityResultWriter:58（insert-only）；checkpoint 删除路径无级联（BizModel delete:298-301 纯 super.delete）→ 孤儿保留已是 live 行为，关系化把语义显式化且可查询；非级联 to-many 形态有文件内先例（joinAsLeft/childTerms/children/childDomains） | `nop-metadata/model/nop-metadata.orm.xml` NopMetaQualityResult 关系区增 `checkpoint` to-one（refPropName=qualityResults）+ NopMetaQualityCheckpoint 关系区增 `qualityResults` to-many（**无 cascadeDelete**）+ 两处裁定注释（历史事实语义、与 qualityRule 级联的刻意不对称）；再生物：`_app.orm.xml:1913`、`_NopMetaQualityResult.java`（getCheckpoint/setCheckpoint）、`_NopMetaQualityCheckpoint.java`（qualityResults OrmEntitySet）、双 xmeta 关系 prop、i18n 条目；三方言 `_create_`/`_drop_` 内容零变化（仅实体拓扑排序重排，sort 校验内容一致） |

Phase 2 验证事实：

- `./mvnw install -pl nop-metadata -am -DskipTests -T 1C` BUILD SUCCESS（再生链路全跑通）。
- 收紧后审批/状态翻转焦点测试全绿：`TestNopMetaTagLabelApproval` 7/7、`TestNopMetaTagLabelApprovalIntegration` 4/4、`TestNopMetaDataContractBizModel` 26/26、`TestNopMetaQualityResultApprove` 2/2、`TestNopMetaQualityResultBizModel` 4/4（approve/reject/submit/reJudge 路径不被破坏——收紧正确性证明）。
- 既有 `__update` 测试面核对：仅 `TestNopMetaTagLabelGlossaryGuard:262` 与 `TestNopMetaGlossaryTermPropagation:85` 使用 `NopMetaTagLabel__update`，均只更新非状态字段（source/glossaryTermId/tagId），不受 override 影响。

### Phase 3 - 区分力测试与文档收口

Status: completed
Targets: 测试、roadmap、owner doc、invariant-catalog

- Item Types: `Proof | Fix`

- [x] 每个经 delta xmeta 收紧的字段：新增双路径测试——「GraphQL `__update` 直改该字段后**值不变/字段不再暴露于输入面**（运行时语义是静默丢弃非 updatable prop，见 `ObjMetaBasedValidator.validateForUpdate:508`——断言不得写成抛错拒绝）」+「专用路径（approve/submit/reJudge）仍可翻转」，沿 `TestNopMetaTagLabelApproval` harness 先例；变异验证（回退 xmeta override 测试转红）记录区分力。
- [x] P2-26 若补关系：新增测试证明 checkpoint 删除后结果行保留（无级联）且关系可查询；若文档化保留：`No new test required: 无行为变更，仅裁定落档` + 注释落位。
- [x] roadmap P2-26/P2-33 条目终态标注；owner doc 同步（状态机写路径分层契约 + checkpointId 语义）；若形成「状态字段只经专用路径变更」类新不变式，登记 `ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`。
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿。

Phase 3 落地事实：

- **新测试类 `TestNopMetaStateFieldGuard`（8 例，全绿）**：
  - (a) 路径 ×3：`testTagLabelStateFieldsNotDirectUpdatable` / `testDataContractStateFieldsNotDirectUpdatable` / `testQualityResultStatusNotDirectUpdatable`——`__update` 携带状态字段 + 一个可更新字段（reason/remark/message），断言无错误（静默丢弃语义，非抛错）+ 状态字段值不变 + 同请求可更新字段正常生效（证明字段级丢弃而非请求级失败）。数据 map id 键沿 `TestNopMetaTagLabelGlossaryGuard:148` 先例（通用 `id` prop，非 PK 列名——`buildEntityDataForUpdate:944` 读 `PROP_ID`）。
  - (b) 路径 ×3：`testTagLabelApprovePathStillFlipsState`（approve → state=Confirmed + approveStatus=APPROVED）/ `testDataContractApprovePathStillFlipsStatus`（approve → status DRAFT→ACTIVE + approveStatus=APPROVED）/ `testQualityResultWriterStillWritesStatus`（`QualityResultWriter.append` 生产写入通道 insert status；reJudge agree 正路径依赖真实数据源基础设施，由质量域既有引擎测试承载——`TestNopMetadataWorkflowModels` javadoc 裁定口径）。
  - P2-26 ×2：`testCheckpointDeleteRetainsResultRows`（检查点经 GraphQL `__save` 创建沿 `testSaveDeleteWiringSchedulerBeanPresent` 先例——DAO 直写会把实体留在测试 ambient session，GraphQL delete session 的 flush 对跨 session 实体不可见；删除后结果行保留 + checkpointId 不变）+ `testQualityResultCheckpointRelationQueryable`（`orm.runInSession` 内 `result.getCheckpoint()` to-one 命中 + `checkpoint.getQualityResults()` to-many 命中）。
- **变异验证（区分力实证，2026-08-16）**：移除 `NopMetaTagLabel.xmeta` 的 `<prop name="state" updatable="false"/>` → `testTagLabelStateFieldsNotDirectUpdatable` 红（`state must be unchanged: expected: <Suggested> but was: <Confirmed>`——直改缺口行为本体复活）；恢复 → 8/8 绿。注：变异须经 `-pl nop-metadata/nop-metadata-service -am` 生效（无 `-am` 时 meta 模块取本地仓 jar，源变更不可见）。
- **文档收口**：roadmap P2-26/P2-33 条目 ✅ Fixed（裁定一句话结论 + plan 号）；owner doc `docs-for-ai/03-modules/nop-metadata.md` 新增「状态机字段写路径分层契约」节（契约 + 专用路径清单 + 静默丢弃语义 + insert 面保留理由 + ORM 层不可收紧机制事实 + checkpointId 语义）；`invariant-catalog.md` 登记 **INV-STATE-PATH**（Cycle 4 增补节，四要素齐全）。

Exit Criteria:

- [x] 行为变更均有「验证正确结果」的测试；纯裁定项有 No-new-test 理由。
- [x] roadmap/owner doc/catalog 与裁定表零矛盾。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（若改 docs）。
- [x] **端到端验证**：任一收紧字段走「GraphQL/工作流入口 → 状态翻转成功」与「GraphQL `__update` 直改入口 → 值不变（静默丢弃语义）」双路径测试走通。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Write-Path Inventory (Phase 1 Deliverable)

（2026-08-16 live 盘点，`rg setStatus|setApproveStatus|setState|setCheckpointId` + 逐文件复核；主代码 updateByExample/定向 SQL 写入：**零命中**（`rg updateByExample|updateByQuery|executeUpdate|sqlUpdate` 在 nop-metadata-service main 下无匹配），全部写路径只有三类：GraphQL InputBean / Java 实体更新 flush / `.xbiz` entity 写。）

### 5 状态字段 + checkpointId 写位点分层表

| 字段 | 写位点 (file:line) | 写入层 | GraphQL `__update` 可触达（xmeta 证据） | 内部 writer 会 flush 该列脏？ |
|---|---|---|---|---|
| NopMetaQualityResult.status | `QualityResultWriter.java:61`（insert：`newEntity`+`setStatus`+`saveEntity`:66，status 经 ALLOWED_STATUSES :50-55 fail-fast 校验） | Java 实体 flush（insert） | 是：`_NopMetaQualityResult.xmeta:36-38` `insertable="true" updatable="true"` | 是（insert） |
| | `QualityAlertWorkflowProcessor.java:130`（`result.setStatus`）+ `:135`（`updateEntity`）；调用链：`qualityBreachApproval/v1.xwf:59` verify 步 → `reJudgeFailClosed:145` → `reJudge:105` | Java 实体 flush（update） | — | **是（update）→ ORM `updatable="false"` 在标准脏实体 flush 时抛 `ERR_ORM_ENTITY_PROP_NOT_UPDATABLE`，打断 re-judge** |
| NopMetaDataContract.status | `NopMetaDataContract.xbiz:28/30/32`（approve：DRAFT→ACTIVE→DEPRECATED→RETIRED）、`:58`（reject：→DRAFT） | `.xbiz` entity 写（requireEntity 后直改实体，mutation 返回实体随事务 flush） | 是：`_NopMetaDataContract.xmeta:42-44` `updatable="true"` | **是** |
| NopMetaDataContract.approveStatus | `approval-support.xbiz:28`（submit→SUBMITTED）、`:66`（withdraw→UNSUBMITTED）、`:92`（approve→APPROVED）、`:120`（reject→REJECTED）、`:148`（reverse→SUBMITTED）；`NopMetaDataContract.xbiz:22`（approve）、`:55`（reject） | `.xbiz` entity 写 | 是：`_NopMetaDataContract.xmeta:103-105` `updatable="true"` | **是** |
| NopMetaTagLabel.state | `NopMetaTagLabelBizModel.java:82/84`（save 缺省注入 effectiveData，经 insert 面落库）、`:152`（setState Confirmed）、`:157`（setState Suggested + `saveOrUpdateEntity`:158）、`:225`（propagated 标签 data map "state":"Suggested"，经 bizObj invoke save）；`NopMetaTagLabel.xbiz:16`（approve：state='Confirmed'） | Java 实体写 + `.xbiz` entity 写 | 是：`_NopMetaTagLabel.xmeta:44-46` `updatable="true"` | **是** |
| NopMetaTagLabel.approveStatus | `approval-support.xbiz:28/66/92/120/148`；`NopMetaTagLabel.xbiz:15`（approve→APPROVED）、`:33`（reject→REJECTED）；自动提审触发点 `NopMetaTagLabelBizModel.java:159→183-197`（invoke submitForApproval） | `.xbiz` entity 写 | 是：`_NopMetaTagLabel.xmeta:100-102` `updatable="true"` | **是** |
| NopMetaQualityResult.checkpointId | `QualityResultWriter.java:58`（**唯一生产写位点**，insert-only：`newEntity`+`setCheckpointId`+`saveEntity`）；调用方：`NopMetaQualityRuleBizModel:95`（单规则路径，传 null）、`MetaQualityCheckpointExecutor:67/74`（检查点路径）。全量 sweep 其余命中均为测试代码或 `_NopMetaQualityCheckpoint` 自身主键 setter | Java 实体 flush（insert-only，**无任何 update 写位点**） | 是：`_NopMetaQualityResult.xmeta:84-86` `updatable="true"`（但无内部 update writer） | 否（仅 insert 路径写） |

### 3 个 xwf 逐文件（维度 22 首批实证）

- `metaDataContractApproval/v1.xwf:23-25`（onEndNotify listener）：只读 `entity.approveStatus === 'SUBMITTED'` 后 `invoke('approve')`——真实写在其 xbiz。**无直接状态字段写**。
- `tagLabelConfirmApproval/v1.xwf:23-25`：同上模式（读 approveStatus → invoke approve）。**无直接状态字段写**。
- `qualityBreachApproval/v1.xwf`：不引用 5 字段中任何一个；verify 步（`:44-62`）调 Java `reJudgeFailClosed`（内部标准 update，见上表）；onDisagree listener（`:76-95`）唯一直接实体写是 `isFalsePositive`（**不在本计划字段面**，维持现状）。

### checkpoint 删除路径（P2-26 孤儿行实证）

- `NopMetaQualityCheckpointBizModel.delete:298-301` = `super.delete`（纯 CRUD，无结果表清理逻辑）；ORM `NopMetaQualityCheckpoint` 侧与结果表无任何关系（唯一关系是 `metaModule` to-one，`nop-metadata/model/nop-metadata.orm.xml:2183-2189`）→ **checkpoint 删除后结果行无条件保留（孤儿行即现状语义）**。
- 对照：`qualityRuleId` 侧 `NopMetaQualityRule.results` to-many `cascadeDelete="true"`（`:2039-2041`）——规则删除级联删结果。不对称的语义解释：规则拥有其结果；检查点是执行配置，结果是历史事实。

### 每字段两问结论（裁定依据）

1. **GraphQL `__update` 输入面是否接受该字段**：5 字段全部「是」（生成 xmeta 均 `updatable="true"`，见上表证据行）。
2. **是否存在内部 writer 会把该列刷脏**：5 字段全部「是」（全部经标准实体更新 flush / `.xbiz` 实体直改）→ **ORM 列 `updatable="false"` 对 5 字段全部不可用**（标准脏实体 flush 抛 `ERR_ORM_ENTITY_PROP_NOT_UPDATABLE`，`GenSqlHelper.java:308-310`）；唯一层选型 = **delta xmeta props override `updatable="false"`**（运行时 `__update` 静默丢弃，`ObjMetaBasedValidator.validateForUpdate:504-509`，且 `.xbiz`/Java 实体写不经 InputBean 校验、完全不受影响——`CrudBizModel.buildEntityDataForUpdate:939-956` 仅过滤 GraphQL 数据面）。

insert 面逐字段裁定依据（`insertable` 保持 `true` 的 Why-Not，防误伤既有合法输入路径）：

- `QualityResult.status`：ORM mandatory（`nop-metadata/model/nop-metadata.orm.xml:2066`）且无 default——GraphQL 创建面必须能供值（dict `meta/quality-result-status` 校验仍在）。
- `NopMetaDataContract.status`：ORM mandatory（`:2500`）且无 default——创建者必须供初始 DRAFT。
- `NopMetaTagLabel.state`：ORM mandatory（`:3321`）且 BizModel 内部缺省注入（`NopMetaTagLabelBizModel.java:78-86` effectiveData）**本身经 insert 面落库**（`validateForSave:498-502` 按 `isInsertable` 过滤）——关 insert 会把内部缺省值一并滤掉、打断全部创建路径。
- `NopMetaTagLabel.approveStatus` / `NopMetaDataContract.approveStatus`（可空）：创建时显式携带 approveStatus 是 `submitForApproval` 守卫语义的一部分（既有回归测试 `TestNopMetaTagLabelApproval.testDerivedLabelApprovalFailureFailsLoud:242` 以 save data 携带 `approveStatus=APPROVED` 作为 fail-loud 判别器）——关 insert 会静默击穿该测试覆盖，属削弱既有回归（违反 Bug Fix Test Coverage Rule）。

## Closure Gates

> Phase 2 变更分两个授权面：P2-26 ORM 源模型（plan-first + 人工放行，含 comment-only）；P2-33 delta xmeta（GraphQL 契约/用户可见行为变更，人工放行）。Phase 1 盘点是 Phase 2 的硬前置（未完成盘点不得动任何面）。

- [x] P2-26/P2-33 裁定全部落档（5 字段 + 1 弱引用逐项），模型/xmeta/文档与裁定一致
- [x] 写路径分层盘点表完整并入 plan（含 xwf/.xbiz 侧），作为裁定证据链
- [x] 收紧项有区分力测试（前红后绿或变异验证）；保留项有书面 Why-Not
- [x] roadmap 条目终态标注；owner doc 同步或显式 No owner-doc update required
- [x] 不存在被静默降级到 deferred 的 in-scope 裁定义务
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw install -pl nop-metadata -am -DskipTests`（若动模型）成功
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle / 代码规范检查通过（CLI exit 1 = 既有 9174 条上游基线、**nop-metadata 文件 0 条**、checkstyle 未接默认 lifecycle——`clean install` 与全 reactor test 全绿为证；同 0549-3/0920-1 裁定口径）

## Deferred But Adjudicated

（执行中按需登记；当前无预置 deferred 项）

## Non-Blocking Follow-ups

- P2-05/P2-12：ask-first / 产品级裁定项，留 roadmap backlog 等人工触发（从未入本计划 scope）。
- 维度 22（工作流/审批流语义）完整独立审计：Phase 1 盘点只覆盖 3 个 xwf + .xbiz 的写位点，不等于维度 22 全维审计；是否派生由下轮 audit 决定。

## Closure

Status Note: P2-26/P2-33 双裁定唯一落地并全部验证：写路径分层盘点（Phase 1）→ 5 字段 delta xmeta 收紧 + checkpointId 双向关系无级联（Phase 2，mission-driver EXEC_PLANS 双面人工放行）→ 8 例区分力双路径测试（含变异验证）+ roadmap/owner doc/invariant-catalog 收口（Phase 3）。全部 Phase Exit Criteria 与 Closure Gates 经独立 closure audit 逐条 live 核对通过。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，review-only 零文件修改），task id `ses_ff7212c5bffeUTVr3DbbXyp3MD`
- Evidence:
  - 12/12 检查全 PASS（file:line 级）：① 5 字段 delta xmeta override 落位（TagLabel.xmeta:13-14 / DataContract.xmeta:14-15 / QualityResult.xmeta:12，生成物保持 updatable="true" 证 delta-only）；② ORM 5 列零 updatable="false"（ORM 层未收紧，与裁定一致）；③ P2-26 双向关系无 cascadeDelete（orm.xml:2113-2119 / :2204-2210，与 qualityRule 级联 :2040-2041 不对称实证）；④ 再生物一致（_app.orm.xml:1913、_gen Java accessors、生成 xmeta 关系 prop，零手编）；⑤ TestNopMetaStateFieldGuard 8 例真实覆盖（silent-drop 断言 + 专用路径 + P2-26），**审计者独立复跑 8/8 绿**；⑥ 审批流零破坏（approval-support.xbiz 未动、生成 xmeta 零命中；NopMetaDataSource.xmeta:23 为 Cycle 3 既有无关 delta）；⑦ roadmap :142/:154 ✅ Fixed + owner doc :156 契约节 + invariant-catalog :259 INV-STATE-PATH；⑧ daily log Phase 1/2/3 + 放行证据齐；⑨ Anti-Hollow：CrudBizModel.update:822-824 inputSelection=null → validateForUpdate:504-509 isUpdatable 过滤 → _validate:175-177 静默跳过——守卫经真实 GraphQL 运行时链生效；⑩ 无静默降级（Deferred 空、Follow-up 仅从未入 scope 项）；⑪ `check-plan-checklist --strict` exit 0（Passed: 1/1）；⑫ `scan-hollow --severity high` exit 0。
  - 验证命令事实：`./mvnw install -pl nop-metadata -am -DskipTests -T 1C` BUILD SUCCESS；`./mvnw test -pl nop-metadata -am -T 1C` 第三轮 BUILD SUCCESS（nop-metadata-service 1285/0/0 = 0920-1 基线 1277 + 净 8；首轮/二轮 nop-auth `TestChannelScanBindLoginE2E` reactor 并发 flake 与本模块无关——单跑 4/4 绿 ×2、fork 调度依赖史 08-15 已档、git 改动面与 nop-auth/nop-orm 零交集）；变异验证 2026-08-16（移除 state override → 红 `expected: <Suggested> but was: <Confirmed>`，恢复 → 绿）；doc-links 17 errors = 既有跨 mission 基线零新增（本 plan 修正 10 处相对路径后 0 命中）。
  - Minor（非阻塞，审计者记录）：plan Current Baseline 行号因插入关系块平移 +21/+30（快照漂移，内容一致）；工作树含 1 个无关文件（nop-format .rels，EOL-only）。

Follow-up:

- 无 remaining plan-owned work。Non-Blocking Follow-ups 两项（P2-05/P2-12 人工触发项、维度 22 完整审计由下轮 audit 决定）均为从未入 scope 的显式登记，非 live defect。
