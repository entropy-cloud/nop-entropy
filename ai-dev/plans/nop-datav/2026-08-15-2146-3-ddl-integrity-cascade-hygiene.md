# 3 nop-datav 数据契约物化、级联完整性与卫生收口

> Plan Status: active
> Mission: nop-datav
> Execution Order: 3 of 3（本批 3 份 remediation plan；权限面 plan 1、事务/资源面 plan 2 先行，本 plan 收口模型-物理数据契约与遗留卫生项；与 plan 1/2 无文件级冲突，可并行推进）
> Last Reviewed: 2026-08-15
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析）达成共识，Blocker/Major 全部修复后转 active；审查记录见 `ai-dev/logs/2026/08-15.md`
> Source: `ai-dev/audits/nop-datav/2026-08-15-1913-multi-audit-nop-datav.md` P0-03 / P1-07 / P1-08 / P1-09 / P1-11 / P1-12（「模型声明的数据契约未物化」系统性主题 + 测试/错误码卫生）；前置决策 P2-14 随 P0-03 消化
> Related: `ai-dev/plans/nop-datav/2026-08-14-2020-1-dashboard-screen-delete-cascade-lifecycle.md`（Dashboard/Screen 级联先例，已完成）；`ai-dev/design/nop-datav/model-design.md`

## Purpose

把 ORM 模型声明但物理库零物化的数据契约落地：8 个 unique-key 补 constraint 物化到全部建表路径（P0-03）、22 个索引补迁移脚本（P1-08）、四处级联删除/解绑缺口补齐（P1-09）；同时收口三项卫生缺陷：80+ 错误码描述语言违反平台契约（P1-07）、IM 部分成功分支零测试（P1-11）、PanelSqlBuilder 注入屏障零直接单测（P1-12）。完成后，模型声明的唯一性/索引契约在数据库层真实强制，并发兜底路径（upsert、版本号、1:1 初始化）不再裸奔，安全关键纯函数有注入回归锚点。

## Current Baseline

以下事实 2026-08-15 已对照 live repo 核实：

- `nop-datav/model/nop-datav.orm.xml` 声明 8 个 `<unique-key>`（`:172` dashboardName、`:423` (dashboardId,snapshotVersion)、`:485` (userName,dashboardId)、`:558` shareToken、`:696` screenName、`:834` (screenId,snapshotVersion)、`:1184` alertRuleId[UQ_ 前缀]、`:1306` (sessionId,seq)）与 22 个 `<index>`（`:240/:301/:361/:427/:489/:562/:624/:627/:772/:838/:928/:931/:934/:1007/:1010/:1107/:1110/:1113/:1188` 等）；全部 UK 均无 `constraint` 属性。
- `nop-datav/deploy/sql/{mysql,oracle,postgresql}/_create_nop-datav.sql` 全文 0 个 UNIQUE 约束、0 个 CREATE INDEX；根因：平台 `nop-orm` `ddl.xlib:81-91` 仅当 `uniqueKey.constraint` 属性存在才输出约束，CreateTable 路径从不输出索引。open-audit 补充确认：`nop-datav-app/application.yaml:32` `nop.orm.init-database-schema: true` auto-DDL 路径与测试基类 `AbstractNopDatavTest.createAllTables` 走同一 `DdlSqlCreator`——**所有建表路径产出的表都没有 UK/索引**。
- 正确先例：`nop-auth/model/nop-auth.orm.xml:176/233/290` `constraint="UK_NOP_AUTH_..."` → 三方言 DDL 正确生成约束；存量迁移脚本先例 `nop-auth/deploy/sql/mysql/_add_ext_login_unique.sql`（ALTER TABLE ADD CONSTRAINT UNIQUE）。
- 代码对 UK 的依赖（P0-03 风险面）：`shareToken` 唯一（匿名 token 定位分享行）、`(sessionId,seq)`（`ChatBiSessionManager.java:32,134` javadoc 明言 UK 兜底并发追加冲突显式失败——兜底实际不存在）、`(userName,dashboardId)`（FilterState find-then-insert upsert）、`(dashboardId/screenId,snapshotVersion)`（max+1 版本号竞态，`findLatestSnapshot` 解析歧义）、`alertRuleId`（AlertState 1:1 find-then-insert 竞态）。
- **前置决策缺口**：dashboardName/screenName 唯一作用域未裁定（multi-audit P2-14）——当前未生效暂无线上影响，物化后立刻变成「全系统看板/大屏名不可重名」的用户可见行为变更，修复 P0-03 前必须先裁定（全表唯一 vs 组合键）。**重名处理现状（审查已核实）**：rename/重名兜底只存在于 `DatavGenerateScreenExecutor`（`existsScreenByName` 预检查 `:210-216` + `isUniqueConstraintViolation` `:521-531` + LLM rename-retry）；`DatavGenerateDashboardExecutor` **无任何重名处理**（直接 setDashboardName 落库）——dashboardName UK 物化会使 ChatBI 生成看板遇重名从「可自由创建」变为裸 DB 异常，修复本身引入新回归，dashboard 侧预检查/rename-retry 是否入 scope 必须随 D1 显式裁定。
- 级联删除缺口（P1-09）：`NopDatavReportTaskBizModel.doDeleteEntity`（`:88-96`）仅 `unregisterTask` 不删 ReportDelivery；`NopDatavAlertRuleBizModel.doDeleteEntity`（`:98-107`）同模式不删 AlertState；`NopDatavChatSessionBizModel`（`:11` 裸 CrudBizModel）标准 `__delete` 产生孤儿 ChatMessage（仅自定义 `deleteChatSession` 级联——双路径语义不一致）；`NopDatavDashboardTabBizModel`（`:11` 裸 CrudBizModel）标准 delete 致 `Panel.tabId` 悬挂。正确先例：Dashboard/Screen 删时级联子表（plan 2026-08-14-2020-1 模式）；Panel 删时停用 AlertRule（`NopDatavPanelBizModel.doDeleteEntity:63-70`）。
- 错误码（P1-07）：`NopDatavErrors.java` 80+ 错误码描述全英文（如 `:68` `"Dashboard not found: {dashboardId}"`）；平台契约 `docs-for-ai/02-core-guides/error-handling.md:119-121` 规定 ErrorCode.define 描述用中文（英文白名单仅 nop-ai-* 六个模块的转换类错误码，nop-datav 不在其中）；对照 nop-auth 实际代码为中文描述。
- 测试缺口（P1-11/P1-12）：`MockChannelMessageService.setNoBindingUsers`（mock 能力，`mock/MockChannelMessageService.java:65-67`）全仓零调用——`NotificationSender`（`report/NotificationSender.java:364-382`）IM 渠道 anySent 部分成功分支（u1 NO_BINDING + u2 SENT → deliveredChannels 含 im）无测试，mutate 验证证明改错实现测试仍通过；`query/PanelSqlBuilder.java:42-62` 空 SQL（分支A）与未声明占位符（分支B）两错误分支零覆盖、无恶意参数值（`north' OR '1'='1`）绑定回归断言（现有 3 个集成测试仅间接锚定参数化主干）。

## Goals

- 8 个 `<unique-key>` 全部带 `constraint` 属性并物化到三方言 deploy DDL + auto-DDL + 测试建表路径；存量库获得 `ALTER TABLE ... ADD CONSTRAINT ... UNIQUE` 迁移脚本（三方言，参照 nop-auth 先例）；dashboardName/screenName 作用域在物化前显式裁定并落档（P0-03）。
- 22 个 `<index>` 经迁移脚本物化（`_add_index_nop-datav.sql` 三方言）；FK 列（dashboardId/screenId/panelId/reportTaskId/sessionId/alertRuleId）级联删除/常用查询不再全表扫描（P1-08）。
- 四级联缺口补齐：删 ReportTask 清 ReportDelivery、删 AlertRule 清 AlertState、ChatSession 标准 delete 与 deleteChatSession 语义一致、删 Tab 不留 Panel.tabId 悬挂（P1-09）。
- `NopDatavErrors` 全部错误码描述改中文并补 en i18n 键（zh 描述 + en 翻译，用户可见语言行为对 zh/en 均正确）（P1-07）。
- IM anySent 部分成功分支有回归测试；PanelSqlBuilder 两错误分支 + 恶意参数绑定回归有纯 JUnit 单测（P1-11/P1-12）。

## Non-Goals

- 不引入 per-tenant 唯一性框架——作用域裁定只在既有列组合内选择。
- 不重建 deploy SQL 生成管线（沿用 ddl.xlib 既有机制：constraint 属性驱动 + 手工迁移脚本补差）。
- 不处理 P2 ORM 细项（P2-10 无 relation 外键列、P2-12 mandatory 不一致、P2-13 delFlag 未接线、P2-39 dashboardType 死配置）→ follow-up backlog；P2-11（UQ_ 前缀不一致 + AlertState 冗余索引）因与 UK 物化同一编辑面，随 Phase 2 顺手收敛并在 backlog 标注。
- 权限收口（plan 1）、事务/资源（plan 2）。

## Scope

### In Scope

- `nop-datav/model/nop-datav.orm.xml`（8 UK constraint 属性 + 作用域裁定落地 + P2-11 顺手收敛）。
- `nop-datav/deploy/sql/{mysql,oracle,postgresql}/`：`_create_nop-datav.sql` 重建（UK 约束）+ 新增 `_add_unique_nop-datav.sql` / `_add_index_nop-datav.sql` 存量迁移脚本。
- `NopDatavReportTaskBizModel` / `NopDatavAlertRuleBizModel` / `NopDatavChatSessionBizModel` / `NopDatavDashboardTabBizModel` 的 doDeleteEntity 级联/解绑。
- `NopDatavErrors.java` + i18n 资源文件（en 键）。
- 新增测试：级联删除回归 ×4、UK 并发兜底行为（按 Phase 2 裁定的可测面）、IM anySent、PanelSqlBuilder 单测。
- `ai-dev/design/nop-datav/model-design.md`（唯一键作用域与索引/迁移契约落档）。

### Out Of Scope

- Non-Goals 列出的全部方向；生成物 `_gen/`/`_*.xml` 只经 `mvn install` 重建，不手改。

## Execution Plan

### Phase 1 - 唯一作用域与物化策略裁定

Status: planned
Targets: `ai-dev/design/nop-datav/model-design.md`

- Item Types: `Decision`

- [ ] D1 dashboardName/screenName 唯一作用域裁定（multi-audit P2-14，P0-03 前置）：全表唯一 vs `(tenantId,createdBy,name)` 组合键 vs 维持声明但暂缓物化（须给出理由）——裁定必须评估：(a) 用户可见行为变更（同名看板创建被拒）；(b) ChatBI 生成路径兼容性——重名兜底现状不对称（Screen executor 有 `existsScreenName` 预检查 + rename-retry，Dashboard executor 无任何重名处理，见 Baseline），若裁定物化 dashboardName UK，**dashboard 侧镜像 Screen 预检查/rename-retry 是否随本 plan 实施**必须显式裁定（入 scope 或显式移出并记录理由——禁止 scope 悬空）；(c) 组合键选项的落地约束——`tenantId` 不在基础 orm 模型列内（租户列仅由 `_add_tenant_nop-datav.sql` 部署脚本按 `getColumnModelsWithTenant` 织入），组合键能否在 `<unique-key columns>` 中表达需先核实平台机制，不可表达时该选项降为不可选
- [ ] D2 物化路径裁定：确认三条建表路径（deploy SQL 三方言、`init-database-schema` auto-DDL、测试基类 createAllTables）在补 `constraint` 属性后全部生效（同一 `ddl.xlib` 模板）；存量迁移脚本形态（单一 `_add_unique` + `_add_index` vs 合并）与 nop-auth `_add_ext_login_unique.sql` 先例对齐
- [ ] D3 索引物化范围裁定：22 个 `<index>` 全量物化 vs 裁剪低价值索引（评估依据落档）；AlertState 既有冗余（UK 物化后 `(alertRuleId)` 唯一约束与 `IX_NOP_DATAV_ALERT_STATE_RULE` 普通索引物理重复）的处理结论——P2-11 一并收敛（UQ_ 前缀统一为 UK_ 或反之，选定单一惯例）
- [ ] 裁定结论落档 `model-design.md`（唯一键矩阵：键/列组合/作用域/物化形态；索引矩阵与迁移策略）

Exit Criteria:

- [ ] D1-D3 均有明确裁定并落档 `model-design.md`，每条裁定引用 live 代码核实事实
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新
- [ ] No new test required: 纯 Decision/文档 Phase，行为验证落在 Phase 2-3

### Phase 2 - UK/索引物化与迁移脚本（P0-03 + P1-08）

Status: planned
Targets: `nop-datav/model/nop-datav.orm.xml`、`nop-datav/deploy/sql/{mysql,oracle,postgresql}/`

- Item Types: `Fix`

- [ ] Fix P0-03：按 D1 裁定为 8 个 `<unique-key>` 补/改 `constraint="UK_..."` 属性（名字键按 D1 作用域调整列组合）；按 D3 收敛 UQ_ 前缀与 AlertState 冗余索引声明
- [ ] 存量重复数据策略（UK 零强制期间完全可能已有重复 dashboardName/screenName 等）：迁移脚本执行前审计存量重复（`GROUP BY ... HAVING count>1`），裁定去重/失败策略（预检脚本拒绝执行并报告重复行 vs 自动去重后缀）并写入迁移脚本或 model-design.md；同步排查测试 fixture（既有测试若创建同名实体将批量变红，须逐套件排查修正）
- [ ] 重建三方言 `_create_nop-datav.sql`（经 `mvn install` 生成管线再生，禁止手改）；新增存量迁移脚本（`_add_unique_nop-datav.sql` + `_add_index_nop-datav.sql` 或 D2 裁定形态）三方言齐全，语法对照 nop-auth 先例
- [ ] Fix P1-08：22（或 D3 裁定子集）个索引经迁移脚本物化
- [ ] 验证：`./mvnw clean install -pl nop-datav -am -DskipTests` 重建生成物后，测试基类建表（同一 DdlSqlCreator）产出的表含全部 UK——至少一条测试断言 UK 存在（如直接对测试库 `INSERT` 重复 shareToken/(sessionId,seq)/(dashboardId,snapshotVersion) 断言约束拒绝，锚定 P0-03 五类并发兜底中的可测面）；迁移脚本经测试库执行验证语法（或裁定人工验证并记录）
- [ ] `model-design.md` 物化结果与 D1-D3 裁定一致（无漂移）

Exit Criteria:

- [ ] 三方言 `_create_nop-datav.sql` 各含全部 UK 约束；迁移脚本三方言齐全且覆盖 8 UK + 裁定索引集；存量重复数据策略已裁定并落地（脚本预检/去重），测试 fixture 已排查修正
- [ ] 至少一条测试证明数据库层唯一性强制生效（重复插入被拒，非应用层检查；测试库为 H2，补 constraint 后建表即含约束，机制成立）
- [ ] auto-DDL/测试建表路径与 deploy SQL 产出一致（同一模板机制核实落档）
- [ ] 若 D1 裁定物化 dashboardName UK：dashboard 侧重名处理裁定已执行（入 scope 实施完成，或显式移出并记录 successor 归属——无 scope 悬空）
- [ ] `ai-dev/backlog/nop-datav-audit-followups.md` 已标注 P2-11/P2-14 由本 plan 消化（P2-11 随 Phase 2 收敛、P2-14 随 D1 裁定）
- [ ] `./mvnw test -pl nop-datav -am` 与 `./mvnw clean install -pl nop-datav -am -DskipTests` 通过；生成物无手改
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 级联删除/解绑补齐（P1-09）

Status: planned
Targets: `NopDatavReportTaskBizModel.java`、`NopDatavAlertRuleBizModel.java`、`NopDatavChatSessionBizModel.java`、`NopDatavDashboardTabBizModel.java`

- Item Types: `Fix`

- [ ] Fix：`NopDatavReportTaskBizModel.doDeleteEntity` 在既有 `unregisterTask` 基础上级联删除 ReportDelivery 子行（先注销调度再删子行，与 Dashboard/Screen 级联模式一致）
- [ ] Fix：`NopDatavAlertRuleBizModel.doDeleteEntity` 级联删除 AlertState 子行（`unregisterRule` 保持）
- [ ] Fix：`NopDatavChatSessionBizModel` 覆写 `doDeleteEntity` 级联删除 ChatMessage——标准 `__delete` 与自定义 `deleteChatSession` 语义收敛为单一路径；注意 `ChatBiSessionManager.deleteSession` 含 owner 校验（`requireSession`）与 session 行自删，**不可在 doDeleteEntity 内整体调用**（会双重删除 + 把 owner-scope 校验注入 admin 标准路径），只复用其消息级联逻辑片段
- [ ] Fix：`NopDatavDashboardTabBizModel` 覆写 `doDeleteEntity`：删除前解绑引用该 tab 的 `Panel.tabId`（置 null）或按仓库惯例拒绝删除非空 tab（裁定后落档，禁止悬挂）
- [ ] 回归 ×4：各实体标准 delete 后无孤儿/悬挂（新 session 断言子表行数/tabId 引用），调度注销副作用保持；既有 Dashboard/Screen/Panel 级联测试不回归

Exit Criteria:

- [ ] 四处级联/解绑全部有代码路径 + 回归测试（子表/引用断言，非仅无异常）
- [ ] 标准路径与自定义路径删除语义一致（ChatSession）；调度注销（unregisterTask/unregisterRule）不回归
- [ ] `./mvnw test -pl nop-datav -am` 通过
- [ ] 级联裁定（Tab 处理方式等）落档 `model-design.md` 或对应 owner doc
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 错误码语言契约收敛（P1-07）

Status: planned
Targets: `NopDatavErrors.java`、i18n 资源文件

- Item Types: `Fix`

- [ ] Fix：80+（实测 96 个 define）错误码描述全部改为中文（对齐 `error-handling.md` 契约与 nop-auth 惯例——nop-auth 先例仅覆盖 zh 描述半边）；同步补 en i18n 键：机制为 `I18nMessageManager` 按错误码字符串查 `_vfs/i18n/{locale}/*.i18n.yaml`，en 键缺失时回退 define() 默认描述（改中文后 en 用户将看到中文，故 en 键是 load-bearing）——**目标文件与键格式**：键=错误码全串（如 `nop.err.datav.dashboard-not-found`），全仓唯一先例 `nop-cli-core` 的 `nop-cli-errors.i18n.yaml`；nop-datav 侧 i18n 脚手架在 nop-datav-meta（`nop-datav/nop-datav-meta/src/main/resources/_vfs/i18n/en/nop-datav.i18n.yaml` retention 文件），Phase 内核实后落位（新增 en 错误码 i18n 文件 nop-datav-errors.i18n.yaml，或并入既有 retention 文件）；ARG 声明与错误码字符串格式占位符保持不变（不破坏既有 `.param(...)` 调用）
- [ ] 若执行中发现个别错误码属「转换类、逐字保留历史消息」的分界情形（error-handling.md 来源驱动分界），按契约逐条裁定并记录，不得整体沿用英文
- [ ] 回归：既有测试中锚定错误码 message 文本的断言全部同步更新并通过；无错误码字符串键（`nop.err.datav.*`）变更（保持 wire 兼容）

Exit Criteria:

- [ ] `NopDatavErrors.java` 无英文业务错误码描述残留（转换类裁定例外逐条记录）；en i18n 键齐备
- [ ] 错误码字符串键与 ARG 集合零变更（wire 兼容，测试锚定）
- [ ] `./mvnw test -pl nop-datav -am` 通过
- [ ] No owner-doc update required: `error-handling.md` 白名单无需变更（nop-datav 走默认中文规则）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 测试缺口补齐（P1-11 + P1-12）

Status: planned
Targets: 新增 `TestNotificationSender*.java`（或扩展既有）、新增 `TestPanelSqlBuilder.java`

- Item Types: `Fix | Proof`

- [ ] Fix P1-11：新增 IM 部分成功分支测试——`MockChannelMessageService.setNoBindingUsers(Set.of("u1"))` + recipients `["u1","u2"]` → 断言 `deliveredChannels` 含 im（anySent 聚合语义被测试锚定；mock 能力已存在，`mock/MockChannelMessageService.java:65-67`）
- [ ] Fix P1-12：新增 `TestPanelSqlBuilder` 纯 JUnit 单测（无容器依赖）：空 SQL 分支 A、未声明占位符分支 B（均断言 NopException + panelId param）、多占位符顺序绑定、恶意参数值（`north' OR '1'='1`）进 `getParams()` 且不出现在 SQL 文本（注入回归锚点）
- [ ] 两测试入位后运行 mutate 自检（可选但建议）：临时反转 anySent 语义/改拼接实现确认测试能红（验证测试保护力），随后恢复

Exit Criteria:

- [ ] 两项新测试存在、命名可检索、断言覆盖上述分支与注入面，全绿
- [ ] `./mvnw test -pl nop-datav -am` 通过
- [ ] No owner-doc update required: 纯测试补充
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] P0-03/P1-07/P1-08/P1-09/P1-11/P1-12 六项缺陷全部修复且各有回归锚定（UK 物化有数据库层强制证明、级联有子表断言、错误码有 wire 兼容锚定、两测试缺口有新测试）
- [ ] dashboardName/screenName 作用域经显式裁定后才物化（无未裁定的用户可见行为变更）
- [ ] 三方言 DDL 与迁移脚本一致完备；`_gen`/`_*.xml` 生成物仅经构建重建，无手改
- [ ] 既有测试套件全绿（错误码描述变更的断言同步完成）
- [ ] 不存在被降级为 follow-up 的 in-scope 缺陷
- [ ] 独立子 agent closure audit 已完成并写入 Closure 段落（含 Anti-Hollow 检查：迁移脚本真实可执行、级联真实删除子行）
- [ ] `./mvnw test -pl nop-datav -am` 通过
- [ ] `./mvnw clean install -pl nop-datav -am -DskipTests` 通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（无——本 plan 范围内无允许延期的已确认缺陷）

## Non-Blocking Follow-ups

- P2 ORM 细项（P2-10/P2-12/P2-13/P2-39 等）与文档漂移组（P2-45~48）→ `ai-dev/backlog/nop-datav-audit-followups.md`。
- 迁移脚本的多方言执行验证若测试库仅覆盖 H2/MySQL，Oracle/PostgreSQL 语法验证方式以 Phase 2 裁定记录（人工核对或 docker 执行），不阻塞关闭。

## Closure

Status Note: <<待关闭时填写>>
Completed: <<待关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<待关闭时填写>>
- Evidence: <<待关闭时填写>>

Follow-up:

- <<待关闭时填写>>
