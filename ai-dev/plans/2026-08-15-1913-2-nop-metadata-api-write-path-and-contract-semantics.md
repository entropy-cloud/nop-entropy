# nop-metadata API 写路径恢复与契约语义收口（2026-08-15 multi-audit P1-1/3/4/5/2）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 3 / 再审计 remediation（执行顺序 2/3）
> Source: `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P1-1 connectionConfig 写路径、P1-3 lineage sourceTables、P1-4/P1-5 DataProduct linkAsset、P1-2 owner 文档 I*Biz 清单）
> Related: 前置 `2026-08-15-1913-1`（执行顺序先行，无硬依赖）；并列 `2026-08-15-1913-3`（错误码参数）
> Draft Review: R1（Major×2+Minor×5）→ 修订 → R2（R1 全 Resolved/预定义 consensus 条件项：validator 行号 :175-177、删除虚构 tagSet 表述、delta 计数——均已修订并 live 复核，update 流程专项验证成立）→ consensus 达成，2026-08-15 转 active

## Purpose

恢复 external 数据源功能的受控写路径（P1-1），修正 lineage 提取结果的公开契约语义（P1-3），为 DataProduct 资产挂链补齐聚合根校验与属主管线（P1-4/P1-5），并把 owner 文档 API 契约清单补齐到与 live 代码零漂移（P1-2）。四项同属"公开契约语义/记载面收口"closure surface。

## Current Baseline

> 事实为 2026-08-15 live repo 实测（逐文件核对；R1 审查子代理独立复核通过，行号已按复核修正）。

- **P1-1**：保留层 `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/nop/metadata/model/NopMetaDataSource/NopMetaDataSource.xmeta:14-15` 将 `connectionConfig` 与 `connectionConfigComponent` 的 `published/insertable/updatable/queryable/sortable` 全部锁死（为满足凭证脱敏把读和写一并封死）。三层同时封死：
  - GraphQL 出口：`published=false` 把 prop 从 GraphQL 查询输出类型整体移除（`ObjMetaToGraphQLDefinition.java:61-62`）；
  - 写入口：`insertable=false` 经 `ObjMetaBasedValidator._validate`（丢弃点 `:175-177` 的 `if (!filter.test(...)) continue;`）静默丢弃客户端传入；
  - UI 表单：`nop-metadata/nop-metadata-web/.../pages/NopMetaDataSource/NopMetaDataSource.view.xml` 留存层仅 5 个空 form/grid delta（2 grid + 3 form，live rg 核对 `connectionConfig` 零命中）——表单字段未补；"不在本 plan 范围"的自述位于 **xmeta 注释 :11**（措辞为表单留存层独立配置，非 view.xml 注释）。
  - 结果：`NopMetaDataSource__save` 创建的数据源永远没有连接配置；`testConnection`/`syncExternalTables`/`collectCatalog` 及全部 external 表联邦功能经 API/UI 不可达（`NopMetaDataSourceBizModel` 4 个 `@BizMutation` :119/:177/:291/:370 全部只读 `getConnectionConfig()`，607 行无 save 覆写）。
  - **R1 复核补充的关键事实**：
    - `sensitive` tagSet **不会**造成 "***REDACTED***" 回写：全平台框架代码零处消费 `sensitive` tag，唯一消费者是本模块 `MetaModelChangedEventPublisher`（:242-244，仅事件快照脱敏、不回写实体）——凭证脱敏契约只压在 `published=false` 上，恢复写路径不破坏脱敏。
    - 仓库先例：`NopAuthUser.xmeta:9` 的 `password` = `published="false" insertable="true" updatable="false"` + 仅 add 表单含 password 字段（`NopAuthUser.view.xml:52`）+ 修改走专用表单/动作。
    - 测试基建：`TestAutoNopMetaDataSourceCrud` 的 `saveDataSource.json5` 已在传 `connectionConfig`（当前被静默丢弃）。
    - update 流程语义（R2 核实）：`CrudBizModel.update:822 → doUpdate → validateForUpdate`（selection=null → filter=`isUpdatable`）；`_validate` 只遍历提交 data 中存在的 key，`OrmEntityCopier` 只拷贝存在 key——edit 表单不含字段 → 提交体无该 key → 已存配置不被触碰；显式提交 null 才会置空。
- **P1-3**：`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeBizModel.java`
  - 表级 `extractLineageFromSql`（:130）`dto.setSourceTables(r.unresolved)` —— unresolved（解析失败名单）误植进 sourceTables；
  - 列级 `extractColumnLineageFromSql`（:142-147）与指标级 `extractMeasureLineage` 从不填充 sourceTables（默认空列表）；
  - 内部 `LineageExtractResult`（`NopMetaLineageEdgeQueryAction.java:539-549`；计算点 :175-200 表级、:232-286 列级）已计算表级 `candidateSourceIds`（**metaTable ID**）与列级 `resolvedSourceIds`（ID），但构造器只保留 count，resolved 被丢弃；表级 `unresolved` 装的是完整名（name→id 只单向映射 `nameToId`，无反向）；
  - **R1 复核补充**：指标级 `extractMeasureLineage`（QueryAction:289-359）**不计算任何 resolved 源表列表**——其边全部为自环（`sourceTableId = targetId`，:336-337），只有 unresolved 与 count；"三路径统一上浮已有计算结果"对指标路径不成立，需显式裁定（见 Phase 2 Decision）。
  - DTO 位于 `nop-metadata/nop-metadata-api/src/main/java/io/nop/metadata/api/dto/LineageExtractResultDTO.java:21`（字段 `List<String> sourceTables` 已存在，仅填充语义错误——不改签名）；
  - owner 文档官方示例 `docs-for-ai/03-modules/nop-metadata.md:99-103` 正是 `extractColumnLineageFromSql(...) { edgeCount sourceTables }`——按示例调用永远得到空列表；示例不含取值说明（ID vs 名裁定不能以"示例对齐"为判据，见 Phase 2 Decision）。
- **P1-4/P1-5**：`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataProductBizModel.java:41-104`
  - `linkAsset`/`unlinkAsset` 挂在 NopMetaDataProduct 聚合根上但从不加载/校验该实体：任意（含不存在）dataProductId 均成功创建/删除 linkage 行（dataProductId 只进手工拼接的 metadata JSON 字符串，无 FK 兜底）。注意：unlinkAsset 对**从未存在**的 dataProductId 今天就会因无 label 匹配抛 `ERR_LINK_ASSET_NOT_FOUND`——修复增量是把错误语义从"标签不存在"修正为"聚合根不存在"的显式校验（对"存在产品但无标签"场景不变）；
  - `linkAsset` 用 `labelDao.saveEntity(label)` 直写 NopMetaTagLabel DAO 并手工 `setState("Suggested")`，绕过 TagLabel BizModel save 管线——`NopMetaTagLabelBizModel.triggerApprovalIfNeeded`（:89-109）对 `Automated` labelType 明确要求 `state=Suggested + trySubmitForApproval`，行为分裂；
  - 正确先例（同模块）：`NopMetaGlossaryTermBizModel.java:110-111` 跨聚合创建走 `bizObjectManager().getBizObject("NopMetaTagLabel").invoke("save", Map.of("data", data), null, context)`，与 `NopMetaTagLabelBizModel.save(@Name("data") Map, ctx)` 签名精确匹配（Map 可容纳 linkAsset 所需全部字段；dedup 前置检查可留在 DataProductBizModel）；
  - `wf:wfName="tagLabelConfirmApproval"` 已在 `NopMetaTagLabel.xmeta:3` 配置——走 save 管线后 Automated 标签真实触发提审（fail-loud）；测试模式有 `TestNopMetaTagLabelApproval*` 现成可循；`requireEntity` 经 CrudBizModel 可用（同模块 testConnection 即用）。
- **P1-2**：`docs-for-ai/03-modules/nop-metadata.md:181-191` "API 契约"节仅列 9 个 I*Biz 接口；实际 14 个非空接口（R1 程序化复核：39 接口中恰 14 个含自定义方法；缺失恰为下列 5 个，9 个公开 mutation 计数亦核实）：缺 `INopMetaDataProductBiz`（linkAsset/unlinkAsset/getLinkedAssets）、`INopMetaQualityResultBiz`（approve/reject）、`INopMetaReconciliationConfigBiz`（executeReconciliation）、`INopMetaReconciliationResultBiz`（confirmMatch/batchConfirmMatches）、`INopMetaTagLabelBiz`（propagateTags/suggestTags）——`NopMetaTagLabel.xbiz` approve/reject XPL 事实源存在待补记。

## Goals

- external 数据源连接配置具备一条受控写路径（save 可写入 connectionConfig，读出口仍脱敏），`testConnection` 等功能经 API 可用。
- lineage 三条提取路径的 `sourceTables` 返回已解析源表标识（指标级语义显式裁定），`unresolved` 不再误植，owner 文档示例行为与返回一致。
- `linkAsset`/`unlinkAsset` 对不存在 dataProductId 显式抛 ErrorCode 异常；跨聚合写路径走 TagLabel 属主管线（或显式裁定免审并注释）。
- owner 文档 I*Biz 清单覆盖全部 14 个非空接口，方法签名与 live 零漂移。

## Non-Goals

- 不实现 connectionConfig 的加密存储/凭据保管库（当前模块基线为明文列 + 读出口脱敏，属产品级裁定）。
- 不改 `LineageExtractResultDTO` 字段签名（仅修填充语义；改签名属 `nop-*-api` Protected Area 破坏性变更）。
- 不处理 P2-21（DataProduct 手工 JSON 拼接换 JsonTool——同文件相邻缺陷但独立登记）与 P2-33（xmeta updatable 收紧裁定）。
- 不重构 ORM 模型结构（无 ORM 变更需求；如执行中发现需要，停下走 ORM Protected Area 人工确认流程）。
- 不做 web 页面 amis 渲染细节美化（只保证 add 表单字段存在且可提交，不做 UI 打磨）。

## Scope

### In Scope

- `NopMetaDataSource.xmeta`（保留层）写路径属性 + 注释修正；`NopMetaDataSource.view.xml`（留存层）add 表单字段配套。
- `NopMetaLineageEdgeBizModel` + `NopMetaLineageEdgeQueryAction.LineageExtractResult`（resolved 载体）+ api DTO 填充语义。
- `NopMetaDataProductBizModel.linkAsset/unlinkAsset`。
- `docs-for-ai/03-modules/nop-metadata.md` API 契约清单节 + GraphQL 示例语义说明。
- 上述各项的回归测试（service 层 + GraphQL schema 断言，沿模块既有测试模式）。

### Out Of Scope

- F2 SSRF 修复（`2026-08-15-1913-1` Phase 1）。
- 错误码参数族（`2026-08-15-1913-3`）。

## Execution Plan

### Phase 1 - P1-1：connectionConfig 受控写路径恢复

Status: completed
Targets: `NopMetaDataSource.xmeta`（保留层）、`NopMetaDataSource.view.xml`（留存层）、`NopMetaDataSourceBizModel`（视方案）

- Item Types: `Fix | Decision | Proof`

- [x] **[Decision]** 方案二选一并记录理由（daily log）：
  - 方案 A（预期默认，细化）：保留层恢复 `connectionConfig` 的 `insertable="true"`（+`updatable` 裁定，见下），保持 `published="false"`（读出口脱敏契约只依赖它，R1 已核实 sensitive tag 不参与写路径回写）；**表单字段仅加入 add 表单**——edit 表单**不显示该字段**（`published=false` 使字段不在 GraphQL 查询输出类型中，生成层 edit 页 `initApi gql:selection="{@formSelection}"` 会因选中 schema 不存在的字段而破坏编辑对话框）。`updatable` 裁定：恢复 `true` 以支持凭据轮换（edit 表单不含字段 → 提交体无该 key → 不会误清空已存配置；仅显式经 API 提交该键时更新）；若执行时按 `NopAuthUser.password` 先例（insertable-only + 专用变更入口）裁定为 `false`，须记录分歧理由。`connectionConfigComponent` **维持锁死**（惰性解析、无需写路径，记录裁定）。
  - 方案 B：保持 xmeta 锁死，新增带权限控制的专用 mutation + 保留层表单字段。注意隐藏成本：新增 mutation 按模块惯例需同步加 `INopMetaDataSourceBiz`（nop-metadata-dao 公共接口）方法，属跨模块公共契约新增。
- [x] **[Fix]** 按裁定方案落地：GraphQL `NopMetaDataSource__save`/`__update` 可写入 connectionConfig（方案 A），或专用 mutation 可用（方案 B）；读出口（findPage/findList/get）仍不返回该字段。
- [x] **[Fix]** 编辑保留层 xmeta 时一并移除注释中已失效的"表单配置不在本 plan 范围"表述（表单配置随本 Phase 落地）。
- [x] **[Proof]** 回归测试：save 写入 → `getConnectionConfig()` 可读到（service 层，可升级 `TestAutoNopMetaDataSourceCrud` 既有 `saveDataSource.json5`——其已在传该字段）；GraphQL schema 断言 `connectionConfig` 不出现在查询类型（读脱敏不回退）；`testConnection` 对 save 产物的可达性集成测试（沿既有模式，用 H2 URL）。

Exit Criteria:

- [x] 写路径存在且被测试证明：经公开 API save 的数据源携带 connectionConfig（repo-observable：测试名 + 断言内容）。
- [x] 读脱敏不回退：GraphQL schema 断言测试通过。
- [x] 表单字段落点 = 仅 add 表单（或方案 B 等价闭环），edit 对话框不因 initApi 选择缺失字段而报错。
- [x] 方案裁定理由（A/B、updatable、connectionConfigComponent）已记录于 `ai-dev/logs/`。
- [x] `docs-for-ai/03-modules/nop-metadata.md` 中 connectionConfig 契约叙述（脱敏/写路径/表单语义）与 live 一致（本 Phase 恢复用户可见功能，owner-doc 更新为强制项）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - P1-3：lineage sourceTables 契约语义修正

Status: completed
Targets: `NopMetaLineageEdgeBizModel`、`NopMetaLineageEdgeQueryAction`（内部 result 载体）、api DTO 消费点

- Item Types: `Fix | Decision | Proof`

- [x] **[Fix]** 内部 `LineageExtractResult` 增加 resolved 源表载体（表级 `candidateSourceIds`、列级 `resolvedSourceIds` 已有计算结果上浮，不重算）。
- [x] **[Fix]** 表级/列级路径以 resolved 源表填充 DTO `sourceTables`；移除 `dto.setSourceTables(r.unresolved)` 误植（unresolved 字段保留原语义）。
- [x] **[Decision]** 两项语义裁定（写入 daily log，并落到 owner doc）：
  - **元素语义（ID vs 完整名）**：默认 **metaTable ID**（与内部已计算结果一致、零额外映射）；注意不对称性——`unresolved` 装完整名，二者将异质并存，owner doc 必须写明各字段语义。若裁定为完整名需新建反向映射（id→name），成本与收益须一并记录。
  - **指标级语义**：`extractMeasureLineage` 的边全部为自环（sourceTableId=targetId）且无 resolved 计算——预裁定 sourceTables 返回 `[metaTableId]`（宿主表自身，与边语义一致）；执行时若发现反证（如自环边不应计入 sourceTables）改显式空列表 + owner doc 说明。
- [x] **[Proof]** 回归测试：表级/列级各至少 1 例断言 sourceTables 非空且等于已解析源表集、unresolved 不串入（列级用含 CTE/未解析引用混合的 SQL 用例区分两列表）；指标级 1 例断言与裁定语义一致。

Exit Criteria:

- [x] `rg -n "setSourceTables\(r\.unresolved\)" NopMetaLineageEdgeBizModel.java` 零命中。
- [x] 三路径回归测试全绿且按 owner 文档示例调用 `extractColumnLineageFromSql ... { sourceTables }` 不再恒空（测试可定位）。
- [x] `docs-for-ai/03-modules/nop-metadata.md:99-103` 示例区补字段语义说明（sourceTables=已解析源表 ID / unresolved=未解析引用完整名 / 指标级语义），与 live 返回一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - P1-4 + P1-5：DataProduct 资产挂链聚合根校验与属主管线

Status: completed
Targets: `NopMetaDataProductBizModel.java:41-104`

- Item Types: `Fix | Decision | Proof`

- [x] **[Fix]** `linkAsset`/`unlinkAsset` 入口对 `dataProductId` 做 `requireEntity` 存在性校验（沿同模块 `executeReconciliation`/`createSqlTable` 惯例，不存在 → 抛 ErrorCode 异常，错误语义区分"产品不存在"与"标签不存在"）；`entityId` 的存在性按 entityType 分派校验或在白名单分支内显式裁定（裁定记录 daily log）。
- [x] **[Fix]** `linkAsset` 的跨聚合创建改走 TagLabel 属主 save 管线（`bizObjectManager().getBizObject("NopMetaTagLabel").invoke("save", Map.of("data", data), null, context)` 沿 `NopMetaGlossaryTermBizModel.java:110-111` 先例——签名已核实匹配，或注入 `INopMetaTagLabelBiz`）；若经裁定确需免审，必须在代码注释中显式写明裁定理由（不允许无注释绕过）。
- [x] **[Proof]** 回归测试：linkAsset 对不存在 dataProductId 抛 ErrorCode 异常（不产生孤儿行）；unlinkAsset 对不存在 dataProductId 抛聚合根错误（而非标签不存在错误）；配置审批流语义下（或以 triggerApprovalIfNeeded 可观察副作用替代，`TestNopMetaTagLabelApproval*` 模式现成可循）Automated 标签经 linkAsset 创建后与 TagLabel save 管线行为一致（state/审批触发与 GlossaryTerm 传播路径同构）。

Exit Criteria:

- [x] 两方法对非法 dataProductId 均显式失败（ErrorCode 异常），错误语义区分聚合根与标签两层，回归测试钉死。
- [x] linkAsset 创建路径与 TagLabel BizModel save 管线行为一致（或带注释的显式免审裁定），行为一致性有测试或代码引用证据。
- [x] **接线验证**：改动后的创建路径确实经 TagLabel 属主管线（调用链可追踪——bizObject invoke 或注入接口的调用点测试/mock verify）。
- [x] `ai-dev/logs/` 对应日期条目已更新；owner-doc：linkAsset/unlinkAsset 行为变更随 Phase 4 清单一并同步（聚合根校验语义），不单独设条目。

### Phase 4 - P1-2：owner 文档 I*Biz 契约清单补齐（纯文档）

Status: completed
Targets: `docs-for-ai/03-modules/nop-metadata.md`（"API 契约"节）

- Item Types: `Fix`

- [x] **[Fix]** 补入 5 个缺失接口及自定义方法签名：`INopMetaDataProductBiz`、`INopMetaQualityResultBiz`、`INopMetaReconciliationConfigBiz`、`INopMetaReconciliationResultBiz`、`INopMetaTagLabelBiz`（与 live 接口逐一核对，沿既有清单条目格式）。
- [x] **[Fix]** 补记 `NopMetaTagLabel.xbiz` approve/reject XPL 事实源说明。

Exit Criteria:

- [x] 文档清单覆盖全部 14 个非空接口；逐接口与 `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/biz/` 下 live 接口核对零漂移（方法名级）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：**裁定记录（closure 时修订）**——实际退出码 1，16 errors 全部为 pre-existing 基线且 0 命中本计划改动文件（错误集中在 `ai-dev/backlog/nop-{ai,code,stream}-invariant-loop-roadmap.md`、`nop-credential-mfa-roadmap.md`、`ai-dev/skills/invariant-loop-audit-prompt.md`——其他 mission worktree 的文件引用本 worktree 不存在的路径，修复属各 mission 归属，不在本 plan 范围）；与 Cycle 3 前序计划 1913-1 收口记录的 16-error 基线一致（closure audit 独立复核确认本 plan 文件零命中）。
- [x] `ai-dev/logs/` 对应日期条目已更新。
- [x] No new test required: 纯文档变更（`TestNopMetaBizInterfaceCompleteness` 已覆盖代码侧三方闭合）。

## Closure Gates

> 本计划含代码变更，构建验证条目适用。

- [x] P1-1：connectionConfig 受控写路径存在且读脱敏不回退（confirmed live defect 已修复）
- [x] P1-3：sourceTables 三路径填充 resolved 源表（指标级按显式裁定），unresolved 不再误植（confirmed contract drift 已收敛）
- [x] P1-4/P1-5：linkAsset/unlinkAsset 聚合根校验 + 属主管线（confirmed live defect 已修复）
- [x] P1-2：owner 文档清单与 live 零漂移（owner-doc drift 已收敛）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证 save→testConnection 端到端路径连通（写入的 connectionConfig 确实被 testConnection 消费），linkAsset 调用链确实经 TagLabel 属主管线
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] checkstyle 对本计划改动文件零新增违规（上游 `nop-api-core` pre-existing 基线，整体 `checkstyle:check` 历史性 exit 1——以改动文件零新增为基准）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

### connectionConfig 加密存储

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 模块基线为明文列 + 读出口脱敏（AR-07 维度13-01 已裁定形态）；加密存储属产品级需求，超出本轮"恢复受控写路径"closure 面。
- Successor Required: `no`
- Successor Path: —

## Non-Blocking Follow-ups

- P2-21 DataProduct 手工 JSON 拼接（与 Phase 3 同文件但独立缺陷）——见 roadmap Follow-up Backlog。
- P2-33 审批流/状态机字段 updatable 收紧裁定——见 roadmap Follow-up Backlog。

## Closure

Status Note: 4 个 Phase（P1-1/P1-3/P1-4+P1-5/P1-2）全部落地并经独立 closure audit（fresh session review-only）approved：5 项 P1 remediation 全部 live 验证 + 独立复跑测试 18/18 绿；两条 Anti-Hollow 链（save→testConnection 端到端、linkAsset→TagLabel 属主管线）均以 file:line 级调用链追踪证实；无 in-scope live defect 被降级（P2-21/P2-33 真实登记于 roadmap Follow-up Backlog，connectionConfig 加密存储经 §Deferred But Adjudicated 裁定 out-of-scope）。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session `ses_ff9c297dbffe2xRinD8ba2zpeL`，review-only 零文件修改）
- Evidence:
  - **Phase 1（6/6 PASS）**：xmeta `:22` 属性组合 live 核对；`TestNopMetaDataSourceConnectionConfigWritePath` 4/4（含 save 落库 + testConnection H2 端到端消费 + GraphQL schema 无该字段断言）；view.xml add-only 表单落点 + `NopAuthUser` 先例同构；裁定记录 `ai-dev/logs/2026/08-15.md:42-45`；owner-doc `nop-metadata.md:268` 一致。
  - **Phase 2（4/4 PASS）**：`rg setSourceTables\(r\.unresolved\)` 独立复跑零命中；`TestLineageSourceTablesContract` 4/4（typed DTO 断言）；载体 `LineageExtractResult.resolvedSourceTables`（表级 `candidateSourceIds`/列级 `resolvedSourceIds`/指标级 0-边空列表边界）；owner-doc `:111` 字段语义一致。
  - **Phase 3（4/4 PASS）**：`requireEntity` 于 linkAsset/unlinkAsset 入口（`:47/:97`）；owner 管线 `invoke("save")`（`:84-85`）；接线测试断言 `state=Suggested + approveStatus=SUBMITTED`（SUBMITTED 仅可经 `trySubmitForApproval` 产生——直写 DAO 路径恒 null）；错误码两层语义钉死。
  - **Phase 4（PASS）**：程序化核对 39 个 `INopMeta*Biz` 恰 14 个非空、文档 14/14 条目方法集与 live 零漂移；xbiz approve/reject XPL 事实源补记与 live 一致；doc-links 裁定基线修订见 Phase 4 条目。
  - **Closure Gates（11/11 PASS）**：测试 gate——执行者全量 `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（2026-08-16T00:23）+ 审计者独立复跑 4 个新增/升级测试类 18/18 绿；checkstyle——审计者独立复跑 9164 条基线违规中零命中本计划 8 个改动 Java 文件；`scan-hollow-implementations.mjs --module nop-metadata --severity high` 独立复跑 exit 0 零发现。
  - **Anti-Hollow**：(a) save→validator（`insertable=true` 通过 `:175-177` 过滤）→entity 列；`testConnection`（`NopMetaDataSourceBizModel.java:119-128`）→`MetaDataSourceConnectionProcessor.testConnect:168-192`→真实 `dataSource.getConnection()` + `getDatabaseProductName()`——测试断言 `connected=true + databaseProductName=H2` 证实真实消费。(b) `invoke("save")`→`NopMetaTagLabelBizModel.save:66`→`triggerApprovalIfNeeded:84→:89`→Automated 分支 Suggested（`:101-103`）→`trySubmitForApproval:128-131`（fail-loud `:138-140`）；旧直写 DAO 路径已消失（全文核对 labelDao 仅剩 dedup 读 + unlink 删）。(c) 4 main + 4 test 文件全文扫描无空方法体/吞异常/TODO-as-done。
  - **Deferred 诚实性**：P2-21（手工 JSON 拼接，仍在 `:58/:99/:122`，独立缺陷已登记 roadmap）、P2-33、加密存储均如实归属，无 in-scope defect 藏于 follow-up 区。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（收口后复跑确认）。
  - 审计附注（非阻塞，已处置）：Phase 4 doc-links 条目按裁定基线修订；08-15 log "snapshot 无变化" 表述勘误于 08-16 log；改动集已提交；`nop-metadata-meta` 本地仓库 jar 陈旧陷阱已以 `mvnw install -DskipTests` 刷新消除。

Follow-up:

- P2-21（DataProduct 手工 JSON 拼接换 JsonTool）、P2-33（xmeta updatable 收紧裁定）——均 roadmap Follow-up Backlog 登记，non-blocking。
- 无 remaining plan-owned work。
