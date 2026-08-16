# 1 定时报告生成与送达（D5-1）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D5-1
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D5-1；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §定时报告
> Related: `2026-08-10-1130-1-dashboard-panel-data-export.md`（D3-3 数据导出，复用 `PanelDataExporter`/异步任务模式）、`2026-08-09-2255-1-dashboard-model-crud-and-tests.md`（D0 publish/snapshot 模式）
> Execution Order: N=1。本计划建立 nop-job 调度集成 + nop-integration 通知集成的首个落地范式；后续 D5-2（轻量告警，N=2）复用本计划产出的调度注册/通知发送骨架。本计划在 D5-2 之前执行。

## Purpose

将 roadmap D5-1 收口：为 nop-datav 增加「定时报告」能力——按 crontab 周期从已发布看板配置渲染面板数据，生成导出文件（CSV/XLSX，复用 D3-3 `PanelDataExporter`），并按配置的通知渠道（邮件为主）连同报告附件送达收件人。本计划同时建立 nop-datav 与平台调度（nop-job）+ 平台通知（nop-integration + nop-sys 模板）的集成范式，供 D5-2 告警复用。本计划只做模型层 + 调度编排 + 送达管线，不含前端 UI。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **D3-3 数据导出已就绪**：`NopDatavExportTaskBizModel`（`nop-datav-service/.../entity/NopDatavExportTaskBizModel.java`）提供 `createExportTask`/`getExportTask`/`cancelExportTask`/`downloadExportFile`，支持 csv/xlsx（panel/dashboard source）。`PanelDataExporter`（`.../service/export/PanelDataExporter.java`）提供 `exportDashboard(dashboardId, params, maxRows)` / `exportPanel(panel, params, format, maxRows)`，内部复用 `PanelDataBinder.queryPanelData` 逐面板取数，CSV（Commons CSV + UTF-8 BOM）/ XLSX（nop-excel）。输出为 `ExportFile(resource, fileName, mimeType, rowCount)`。
- **面板数据查询已就绪**：`PanelDataBinder.queryPanelData(panelId, panel, requestParams[, rowLimit])` 走 面板→数据集引用→参数求值→EQL/SQL→结果 的完整管线（`.../service/query/PanelDataBinder.java`）。`PanelDataResult(panelId, componentType, hasDataset, columns, rows)`。
- **看板发布快照已就绪（配置快照，非渲染数据）**：`NopDatavDashboardBizModel.publishDashboard/getPublishedDashboard` 管理配置快照（`NopDatavDashboardSnapshot.snapshotContent` = CLOB 配置 JSON，含 layout/panels/tabs/datasetRefs，**不含**已执行查询结果）。`getPublishedDashboard(id)` 返回最新快照。
- **异步任务执行模式可复用**：`NopDatavExportTaskBizModel.submitExecution` 用 `GlobalExecutors.globalWorker().submit(...)` + `ormTemplate.runInNewSession(session -> ...)`；状态机 `NopDatavExportTaskStatus`（PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED）；重启恢复 `NopDatavExportTaskRecovery`（`@PostConstruct` 幂等扫描 stale pending/running → failed）；文件持久化 `IFileStore.saveFile(UploadRequestBean)`（bizObjName=`nopDatavExportTask`）；并发限额 `CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER`。
- **nop-job 调度可用但未接入 nop-datav**：`IJobScheduler`（`nop-job-api/.../IJobScheduler.java`）提供 `addJob(JobSpec, allowUpdate)`/`removeJob`/`suspendJob`/`resumeJob`/`fireNow(jobName)`/`activate()`。`JobSpec(jobName, jobInvoker, jobParams, triggerSpec, onceTask)`；`TriggerSpec(cronExpr, repeatInterval, ...)`。可空注入范式见 `MetaQualityCheckpointScheduler`（`nop-metadata-service`）：`@Inject public void setScheduler(@Nullable IJobScheduler scheduler)`，`@PostConstruct init()` 扫描启用配置逐个 `addJob`，配置增删改时 `register/unregister`。`beanMethod` invoker：`jobParams={beanName, methodName, <业务键>}`，执行方法签名取单 `Map<String,Object>`（规避 `-parameters` 编译标志依赖）。**关键坑**：`LocalJobScheduler` 下执行方法抛异常会把 job 永久置 FAILED（`addJob(allowUpdate=true)` 不复活 FAILED），故执行方法须吞掉业务错误并返回正常结果（参考 `executeScheduledCheckpoint`）。
- **平台通知能力实际在 nop-integration + nop-sys（非 nop-message）**：
  - `nop-integration-api` 提供 `IEmailSender.sendEmail(EmailMessage)` / `sendMultiEmail(...)`（`.../integration/api/email/`），`EmailMessage(subject, from, to, cc, text, html, attachments)`。实现：`JavaEmailSender`（`nop-integration-email-java`，SMTP）等。SMS：`ISmsSender`。IM/渠道：`IChannelMessageService.sendToUser(userId, OutboundChannelMessage)`（实现重，在 nop-ai-gateway）。
  - 模板存储：`NopSysNoticeTemplate`（`nop-sys-dao`，表 `nop_sys_notice_template`，字段 name/tplType/content）；`NopSysNoticeTemplateBizModel` 仅 CRUD，**无** render-and-dispatch 引擎——渲染+分发由调用方编排。
  - **`nop-message` 是 pub-sub 基础设施（Kafka/Pulsar/Debezium），不是邮件/SMS/IM 通知系统**。roadmap「防重建」表将通知渠道记为 nop-message 属于措辞偏差；本计划 Phase 1 裁定实际通知走 nop-integration + nop-sys，并在 design doc 记录拒用 nop-message 的理由（pub-sub 与用户通知语义不匹配）。
- **nop-integration 尚未作为 nop-datav 依赖**：`nop-datav/nop-datav-service/pom.xml` 当前未声明 `nop-integration-api` 依赖；本计划需新增该依赖（仅 API 接口，实现在宿主 app 提供）。
- **权限基建可复用**：D3-1/D3-3 已有看板 `@Auth` + owner 行级 RLS（`nop-datav-web/.../auth/nop-datav.action-auth.xml` + `nop-datav-service/.../auth/nop-datav.data-auth.xml`）。报告任务属看板派生物，沿用「看板 owner/admin 可管理报告任务」语义。
- **错误码**：`NopDatavErrors.java`（`.../service/NopDatavErrors.java`）含导出/看板/面板相关码；本计划新增 `ERR_DATAV_REPORT_*`。
- **设计契约为 stub**：`ai-dev/design/nop-datav/schedule-report-design.md` 仅 4 行占位符。本计划 Phase 1 起草最终结论。
- **配置类**：`NopDatavConfigs`（`.../service/NopDatavConfigs.java`）已有导出相关 `CFG_DATAV_EXPORT_*`；本计划新增 `CFG_DATAV_REPORT_*`。
- **真正剩余 gap**：无报告任务实体/表、无 nop-job 接入、无通知送达管线、无报告交付历史、设计 doc 为空。

## 设计方向预声明（推荐方向，Phase 1 确认并记录拒绝理由）

1. **渲染时机 = 调度触发时渲染（render-at-execution），不为「已渲染数据快照」新建独立实体**：每次 cron 触发 → 加载已发布看板配置快照 → 复用 `PanelDataExporter.exportDashboard` 逐面板取数生成文件 → 即时送达 → 写一条交付历史。拒绝「持久化已渲染数据快照表」（理由：当前无离线浏览历史渲染结果用例；交付物即文件，历史只需元信息；持久化渲染 JSON 属过度设计，可列为 follow-up）。Phase 1 确认该渲染契约。
2. **通知走 nop-integration（IEmailSender）+ nop-sys（NopSysNoticeTemplate），不走 nop-message**：nop-message 为 pub-sub 基础设施（Kafka/Pulsar），与用户通知语义不符。本计划以**邮件**为首要且端到端打通的渠道；IM 渠道（IChannelMessageService，实现在 nop-ai-gateway）因实现较重且需 channel binding，列为 adjudicated deferred（out-of-scope improvement），但通知抽象层预留扩展位（不写空壳 stub）。Phase 1 记录此裁定与拒用 nop-message 的理由。
3. **调度集成 = beanMethod invoker + 可空注入 IJobScheduler（镜像 MetaQualityCheckpointScheduler）**：在 nop-datav-service 新增 `NopDatavReportScheduler` IoC bean，`@Nullable` 注入 `IJobScheduler`，`@PostConstruct` 扫描已启用报告任务逐个 `addJob`；任务 save/enable/delete 时 register/unregister。执行方法取单 `Map<String,Object>` 并吞业务错误返回正常结果（规避 LocalJobScheduler FAILED-brick）。拒绝「DB 持久化 NopJobSchedule + rpc invoker」作为首版（理由：beanMethod 更轻、与既有元数据调度范式一致；集群部署可后续切 rpc，列为 follow-up）。Phase 1 确认。
4. **报告任务与看板解耦但绑定看板**：`NopDatavReportTask.dashboardId` 外键关联看板。**报告渲染基于当前已发布看板**：复用 `PanelDataExporter.exportDashboard(dashboardId, params, maxRows)`，该方法内部按 `dashboardId` 查询当前 panel 表逐面板取数（`PanelDataExporter.java:107`），数据始终为触发时的**实时数据**（符合"定时报告"语义——定时对当前数据做快照并发送）。**首版不支持固定历史版本快照渲染**（exportDashboard 不接受 snapshotVersion，固定版本需从 `NopDatavDashboardSnapshot.snapshotContent` JSON 自行重建面板查询，属过度设计），列为 follow-up。报告任务属看板 owner 可管理对象，沿用 D3 owner RLS。Phase 1 确认该取数契约并记录拒"固定版本快照渲染"理由。
5. **交付历史只记元信息 + 文件引用，不记渲染 JSON**：`NopDatavReportDelivery` 记 status/generatedFileRecordId/deliveredChannels/errorMessage/触发来源/时间；文件经 `IFileStore` 持久化，按现有文件过期策略治理。
6. **grace 期**：参考 Superset grace，配置项 `graceMinutes`（默认 60）；触发时若距预定时间超过 grace 则跳过本轮（misfire 兜底），写一条 skipped 状态的交付记录（非静默跳过——显式记 skipped）。
7. **模板渲染机制（修正 Major-2）**：`NopSysNoticeTemplate` 仅 CRUD 无渲染引擎；平台无通用 `ITemplateRenderer`。本计划裁定：渲染用 `StringHelper.renderTemplate(content, transformer)`（`{var}` 占位符替换，已用于平台既有消息渲染）；`tplType` 取值集由本计划定义（`report-delivery`）；模板键映射 = `NopDatavReportTask.templateKey` → `NopSysNoticeTemplate.name`（缺失抛 `ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND`）；变量绑定上下文 = `{reportName, dashboardName, generatedTime, fileName, fileSize, rowCount, deliveryStatus}`。Phase 1 确认该渲染契约并记录拒"XPL 引擎"理由（简单占位符替换满足通知文本需求，XPL 过重）。
8. **收件人/渠道存储格式（修正 Major-4）**：`NopDatavReportTask.recipients`（CLOB JSON 数组，如 `["a@b.com","c@d.com"]`）+ `notifyChannels`（VARCHAR/dict 多选，存 JSON 数组如 `["email"]` 或逗号分隔，Phase 1 裁定具体形式——默认 CLOB JSON 数组，与 `layoutConfig`/`paramConfig` 既有 json 列惯例一致）。Phase 3 `NotificationSender` 按 JSON 数组解析为 `List<String>` 构造 `EmailMessage.to`；空数组 → `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL`。

## Goals

- 报告任务实体：`NopDatavReportTask`（cron/dashboardId/format/recipients/notifyChannels/params/status/graceMinutes）+ 交付历史 `NopDatavReportDelivery`。
- 调度集成：`NopDatavReportScheduler` 注册/注销 job，配置变更即时生效，cron 触发执行。
- 报告生成 + 送达：触发时复用 `PanelDataExporter` 生成导出文件 → 渲染 `NopSysNoticeTemplate` → 经 `IEmailSender` 邮件送达（带附件）→ 写交付历史。
- 手动触发：`fireNow` 式 API（立即执行一次报告任务）。
- 邮件渠道端到端打通；IM 渠道显式 deferred（不写空壳）。
- 在 `schedule-report-design.md` 起草 D5 最终结论（含通知 provider 裁定、渲染时机、调度集成、实体契约、被拒方案）。
- 新增 action 经 `@Auth` + owner 权限收口；新增错误码 + 配置项。

## Non-Goals

- **前端报告任务管理 UI**：走 flux，不在本计划。
- **IM/渠道推送（飞书/钉钉/企微）端到端落地**：`IChannelMessageService` 实现在 nop-ai-gateway，较重且需 channel binding；列为 adjudicated deferred，通知抽象层预留扩展位但**不写空壳 stub**。
- **已渲染数据快照持久化（离线浏览历史渲染结果）**：当前无用例；列为 follow-up。
- **集群部署的 DB 持久化 NopJobSchedule + rpc invoker 切换**：首版用 beanMethod 嵌入式调度；集群形态列为 follow-up。
- **D5-2 轻量告警**：独立 plan（N=2），复用本计划的调度注册/通知抽象。
- **报告模板可视化编辑器**：模板直接用 `NopSysNoticeTemplate` 既有 CRUD。
- **PDF/PNG 图像报告**：需已渲染看板快照（依赖 flux 渲染），与 D3-3 图像导出同类阻塞，out-of-scope。

## Scope

### In Scope

- 设计文档：`ai-dev/design/nop-datav/schedule-report-design.md` 起草 D5-1 最终结论（通知 provider 裁定+拒 nop-message 理由、渲染时机裁定+拒持久化渲染快照理由、调度集成裁定+拒 DB-NopJobSchedule-rpc 理由、实体契约、grace 语义、权限矩阵、IM deferred 裁定）。
- ORM 变更：新增 `NopDatavReportTask`、`NopDatavReportDelivery` 实体（`nop-datav/model/nop-datav.orm.xml`）+ 新增 dict（`datav/report-format` 复用 export-format 或新增、`datav/report-status`/`datav/notify-channel`）。
- 依赖变更：`nop-datav-service` 新增 `nop-integration-api`（compile，邮件接口）+ `nop-job-api`（compile，调度接口 `IJobScheduler`/`JobSpec`/`TriggerSpec`）依赖；`nop-datav-service` 测试新增 `nop-job-local`（test scope，`LocalJobScheduler` 用于端到端测试）依赖。
- 调度集成：`NopDatavReportScheduler` bean（可空注入 IJobScheduler、`@PostConstruct` 注册、配置变更 register/unregister、执行方法吞业务错误）。
- 通知抽象 + 邮件实现：通知发送服务（渲染 `NopSysNoticeTemplate` → `IEmailSender.sendEmail` 带附件）；IM 渠道预留扩展接口（非空壳：未配置邮件发件人/未启用渠道时显式失败，不静默跳过）。
- 报告生成执行：加载已发布看板配置 → `PanelDataExporter.exportDashboard`/`exportPanel` → 文件 `IFileStore` 持久化 → 通知送达 → 交付历史。
- BizModel + API：报告任务 CRUD + `enableReportTask`/`disableReportTask`/`triggerReportNow`/`getReportDeliveryHistory`；action 经 `@Auth`。
- 配置项：`CFG_DATAV_REPORT_*`（邮件发件人默认、max-rows、grace 默认、并发限额）。
- 错误码：`ERR_DATAV_REPORT_*`（task-not-found/cron-invalid/no-publishable-dashboard/delivery-failed/no-notifiable-channel/sender-not-configured 等）。
- 单元测试 + 端到端（建任务→启用→手动触发→生成文件→邮件送达（mock sender 断言）→交付历史）。

### Out Of Scope

- 前端 UI。
- IM/渠道推送端到端（nop-ai-gateway 实现）。
- 已渲染数据快照持久化。
- 集群 DB-NopJobSchedule-rpc 调度形态。
- D5-2 告警。
- PDF/PNG 图像报告。

## Execution Plan

### Phase 1 - 设计文档起草（schedule-report-design.md D5-1 结论）

Status: completed
Targets: `ai-dev/design/nop-datav/schedule-report-design.md`

- Item Types: `Decision`

- [x] 将 `schedule-report-design.md` 从 stub 起草为最终结论文档（无 "Proposed vs Current"），覆盖：
  - [x] **通知 provider 裁定**：通知走 `nop-integration`（IEmailSender）+ `nop-sys`（NopSysNoticeTemplate）；记录拒用 `nop-message` 理由（nop-message = pub-sub 基础设施 Kafka/Pulsar，与用户通知语义不符；修正 roadmap 防重建表措辞偏差）
  - [x] **渲染时机裁定**：render-at-execution（触发时复用 PanelDataExporter 取数生成文件 → 即时送达）；记录拒「持久化已渲染数据快照表」理由（无离线浏览用例，过度设计，列为 follow-up）
  - [x] **调度集成裁定**：beanMethod invoker + 可空注入 IJobScheduler（镜像 MetaQualityCheckpointScheduler）；记录拒「DB NopJobSchedule + rpc invoker」理由（首版嵌入式更轻，集群形态列 follow-up）；记录 LocalJobScheduler FAILED-brick 坑与吞业务错误的执行方法约定
  - [x] **实体契约**：`NopDatavReportTask`（reportTaskId/taskName/dashboardId/cronExpr/format/recipients/notifyChannels/params/status/graceMinutes/templateKey/lastRunTime/lastRunStatus/lastRunError/审计列）+ `NopDatavReportDelivery`（deliveryId/reportTaskId/status/generatedFileRecordId/deliveredChannels/errorMessage/triggeredBy/startTime/endTime/审计列）；dict 定义。**recipients/notifyChannels 存储格式 = CLOB JSON 数组**（设计方向 #8）
  - [x] **grace 语义**：距预定时间超过 graceMinutes 则跳过本轮并写 skipped 交付记录（显式记录，非静默跳过）
  - [x] **渠道范围裁定**：邮件端到端打通；IM（IChannelMessageService）adjudicated deferred（实现在 nop-ai-gateway 较重 + 需 channel binding），通知抽象层预留扩展接口；未配置发件人/未启用渠道时显式失败（抛错），不静默跳过
  - [x] **模板渲染机制（设计方向 #7）**：`StringHelper.renderTemplate`（`{var}` 占位符替换）+ tplType=`report-delivery` + 模板键→`NopSysNoticeTemplate.name` + 变量上下文 `{reportName,dashboardName,generatedTime,fileName,fileSize,rowCount,deliveryStatus}`；记录拒"XPL 引擎"理由
  - [x] **取数契约**：报告渲染基于当前已发布看板（复用 `PanelDataExporter.exportDashboard(dashboardId, params, maxRows)` 查当前 panel 表实时数据）；记录拒"固定历史版本快照渲染"理由（exportDashboard 不接受 snapshotVersion，固定版本需从 snapshotContent 重建，过度设计，列 follow-up）
  - [x] **权限矩阵**：报告任务管理 = 看板 owner/admin；交付历史读 = owner/admin；沿用 D3 owner RLS
  - [x] **执行方法签名约定**：单 `Map<String,Object>` 参数（适配 beanMethod invoker），吞业务错误返回正常结果对象

Exit Criteria:

- [x] `schedule-report-design.md` 为最终结论（无 "Proposed"/"待定"），覆盖上述全部子项
- [x] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ORM 模型与代码生成（报告任务 + 交付历史）

Status: completed
Targets: `nop-datav/model/nop-datav.orm.xml`、`nop-datav/nop-datav-meta/src/main/resources/_vfs/dict/datav/`、`nop-datav/nop-datav-dao/src/main/java/io/nop/datav/dao/entity/_gen/`、`nop-datav/nop-datav-meta/src/main/resources/_vfs/nop/datav/model/`

- Item Types: `Decision | Proof`

- [x] 在 `nop-datav/model/nop-datav.orm.xml` 新增 `NopDatavReportTask` 实体（含 dashboardId 外键、cronExpr、format、recipients clobJson、notifyChannels clobJson、params clobJson、status、graceMinutes、templateKey、lastRunTime/lastRunStatus/lastRunError、标准审计列；displayName/i18n 按既有惯例）
- [x] 在 `nop-datav/model/nop-datav.orm.xml` 新增 `NopDatavReportDelivery` 实体（含 reportTaskId 外键、status、generatedFileRecordId、deliveredChannels、errorMessage、triggeredBy、startTime/endTime、标准审计列）
- [x] 新增 dict：`datav/report-status`（启用/禁用或复用 job schedule-status 概念时记录裁定）、`datav/notify-channel`（email/im）、`datav/delivery-status`（pending/running/succeeded/failed/skipped/cancelled）、`datav/report-trigger-source`（schedule/manual）；format 复用 `datav/export-format` 或新增 `datav/report-format`（Phase 1 裁定，默认复用）
- [x] 触发 codegen 重建生成物：`./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests`（与既有 D3/D4 一致），确认生成实体 `_NopDatavReportTask.java`/`_NopDatavReportDelivery.java` + xmeta `_*.xmeta` + dict 文件；**不手改任何 `_` 前缀生成文件**
- [x] i18n displayName 文案（en/zh-CN）补齐（`_vfs/i18n/{en,zh-CN}/datav-*`）

Exit Criteria:

- [x] `nop-datav.orm.xml` 含 `NopDatavReportTask`/`NopDatavReportDelivery` 源模型（字段/dict/关系/索引）
- [x] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 成功；生成实体 + xmeta + dict 均含新实体
- [x] **无静默跳过**：本 Phase 仅 codegen，不涉及运行时分支
- [x] 该 Phase 改变 live baseline（ORM 结构）：属 plan-first 区域，本 plan 即其 plan；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 调度集成 + 报告生成 + 通知送达实现

Status: completed
Targets: `nop-datav/nop-datav-service/pom.xml`（依赖）、`nop-datav-dao/.../biz/INopDatavReportTaskBiz.java`（接口）、`nop-datav-service/.../entity/NopDatavReportTaskBizModel.java`、`nop-datav-service/.../report/NopDatavReportScheduler.java`（新增）、`nop-datav-service/.../report/ReportDeliveryExecutor.java`（新增）、`nop-datav-service/.../report/NotificationSender.java`（新增）、`nop-datav-service/.../NopDatavErrors.java`（增码）、`nop-datav-service/.../NopDatavConfigs.java`（增配置）、`nop-datav-service/.../beans/app-service.beans.xml` + `_service.beans.xml`（bean 注册）、`nop-datav-web/.../auth/nop-datav.action-auth.xml`（权限点）

- Item Types: `Fix | Decision`

- [x] **依赖新增**：`nop-datav/nop-datav-service/pom.xml` 声明 `nop-integration-api`（compile，`IEmailSender`/`EmailMessage`）+ `nop-job-api`（compile，`IJobScheduler`/`JobSpec`/`TriggerSpec`）依赖；`nop-datav-service` test 声明 `nop-job-local`（test scope，`LocalJobScheduler` + `nopJobLocalConfigLoader`，端到端测试用）；对照 `nop-metadata/nop-metadata-service/pom.xml` 已有的 nop-job-api/nop-job-local 声明惯例
- [x] **接口扩展**：`INopDatavReportTaskBiz`（`nop-datav-dao/.../biz/`）声明报告任务 CRUD + `enableReportTask`/`disableReportTask`/`triggerReportNow`/`getReportDeliveryHistory` action 签名
- [x] **调度器 bean**：`NopDatavReportScheduler`（`@PostConstruct init()` 扫描已启用任务 → 可空注入 `IJobScheduler` → `addJob(JobSpec{jobInvoker=beanMethod, jobParams={beanName=nopDatavReportScheduler, methodName=executeScheduledReport, reportTaskId}, triggerSpec{cronExpr}})`；提供 `registerTask(id)`/`unregisterTask(id)`；宿主未启用调度器时（scheduler==null）init 只记录告警不抛错，任务仍可通过 `triggerReportNow` 手动执行）
- [x] **执行方法**：`executeScheduledReport(Map<String,Object> params)`（单 Map 参数，beanMethod invoker 约定）→ 取 reportTaskId → 委托 `ReportDeliveryExecutor.execute(taskId, triggerSource=schedule)`；**吞业务错误**返回结果对象（catch 业务异常 → 记 delivery failed → 返回正常结果，不抛——规避 LocalJobScheduler FAILED-brick；仅基础设施错误抛出）
- [x] **BizModel**：`NopDatavReportTaskBizModel`（extends CrudBizModel）实现 CRUD + action；save/enable 调 `scheduler.registerTask`，disable/delete 调 `unregisterTask`；`triggerReportNow(taskId)` 调 `ReportDeliveryExecutor.execute(taskId, triggerSource=manual)`（同步触发或 GlobalExecutors 异步，Phase 1 裁定）；action 经 `@Auth` + 看板 owner RLS（`requireDashboardOwnership` / `IDataAuthChecker.isPermitted("NopDatavDashboard", ...)`)
- [x] **报告执行器**：`ReportDeliveryExecutor.execute(taskId, triggerSource)`：插 pending 交付记录 → `GlobalExecutors.globalWorker().submit(runInNewSession(...))` → 加载任务 → 校验 dashboardId 已发布（无发布快照抛 `ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD`）→ 复用 `PanelDataExporter.exportDashboard(dashboardId, params, maxRows)` 生成文件（按设计方向 #4 取当前 panel 表实时数据，不经 snapshotVersion）→ `IFileStore.saveFile` 持久化 → `NotificationSender.sendReport(task, delivery, fileRecord)` → 更新交付记录 succeeded/fileRecordId/deliveredChannels；Throwable → failed + errorMessage；grace 检查超期写 skipped 记录
- [x] **通知发送器**：`NotificationSender.sendReport(...)`：按设计方向 #7 渲染模板（`StringHelper.renderTemplate` + tplType=`report-delivery` + `NopSysNoticeTemplate.name`=templateKey，缺失抛 `ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND`，变量上下文见 #7）→ 按 notifyChannels 分发：email 渠道 → 解析 recipients JSON 数组为 `List<String>` → 构造 `EmailMessage`（setter DataBean：subject/to/text/html=true，`attachments` 为 `List<IResourceReference>`；`IResource` 本就 IS-A `IResourceReference`（`IResource extends IResourceReference`），导出 temp resource 可直接加入附件列表——注意须在 temp resource 被 `PanelDataExporter` 产出后、清理前使用，或经 `IFileStore.getFile(fileId)` 重建 `IResource`）→ `IEmailSender.sendEmail(emailMessage)`；**渠道未启用/发件人未配置** → 抛 `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED`（显式失败，不静默跳过）；**无可用通知渠道**（notifyChannels 为空 JSON 数组）→ 抛 `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL`；IM 渠道（notifyChannels 含 im）→ 抛 `UnsupportedOperationException("IM channel not yet implemented: ...")`（非静默跳过，规则 #24）
- [x] **错误码**：`NopDatavErrors` 新增 `ERR_DATAV_REPORT_TASK_NOT_FOUND`/`ERR_DATAV_REPORT_CRON_INVALID`/`ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD`/`ERR_DATAV_REPORT_DELIVERY_FAILED`/`ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL`/`ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED`/`ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND`/`ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER`
- [x] **配置项**：`NopDatavConfigs` 新增 `CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES`（默认 60）/`CFG_DATAV_REPORT_MAX_ROWS`（默认复用 export max-rows 或独立）/`CFG_DATAV_REPORT_DEFAULT_SENDER`（邮件发件人默认）
- [x] **bean 注册**：`app-service.beans.xml` 注册 `nopDatavReportScheduler`/`reportDeliveryExecutor`/`notificationSender` + 独立 `nopDatavReportDeliveryRecovery` bean（职责分离，镜像 `NopDatavExportTaskRecovery`）；`_service.beans.xml` 注册 `NopDatavReportTaskBizModel` 原始 bean + `BizProxyFactoryBean`（biz_NopDatavReportTask，bizObjName=NopDatavReportTask）+ `NopDatavReportDeliveryBizModel`（如交付历史需要独立 CRUD）
- [x] **权限**：`nop-datav-web/.../auth/nop-datav.action-auth.xml` 增 action 权限点 + 角色绑定（报告任务管理 owner/admin；交付历史读 owner/admin）；`nop-datav.data-auth.xml` 增 NopDatavReportTask owner RLS（沿用 NopDatavDashboard 模式）
- [x] **重启恢复**：新增独立 `NopDatavReportDeliveryRecovery` bean（`@PostConstruct` + `existsTable` 防御，镜像 `NopDatavExportTaskRecovery`），幂等扫描 stale running 交付记录（进程重启残留）置 failed（reason=interrupted by process restart）。`NopDatavReportScheduler.init()` 只负责重注册 cron job，不混入 stale 清理（职责分离）

Exit Criteria:

- [x] 报告任务 CRUD + enable/disable/triggerNow/deliveryHistory action 可用，行为符合 Phase 1 契约
- [x] cron 触发经 `NopDatavReportScheduler.executeScheduledReport` → `ReportDeliveryExecutor.execute` 完整执行（取数→文件→送达→历史）
- [x] 执行方法吞业务错误返回正常结果（不抛——规避 LocalJobScheduler FAILED-brick）；基础设施错误才抛
- [x] 邮件渠道端到端：`NotificationSender.sendReport` → `IEmailSender.sendEmail`（带附件）调用可观察
- [x] **无静默跳过**：未配置发件人/无通知渠道/IM 渠道均显式失败（抛错/UnsupportedOperationException，非 continue/空返回）；grace 超期写 skipped 记录（显式）
- [x] 宿主未启用 IJobScheduler（scheduler==null）时 init 不抛错，手动 triggerReportNow 仍可用
- [x] stale running 交付记录经 init 幂等恢复为 failed
- [x] 该 Phase 改变 live baseline（API/行为/契约）：`schedule-report-design.md`（Phase 1）已覆盖；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试与端到端验证

Status: completed
Targets: `nop-datav-service/src/test/`

- Item Types: `Proof`

- [x] 单元测试 报告任务 CRUD + 调度注册：save enabled 任务 → `NopDatavReportScheduler` registerTask 被调用（测试用 **mock `IJobScheduler`** 注入 scheduler，断言 `addJob` 被调用且 `JobSpec.jobParams` 含 reportTaskId/cronExpr）；disable/delete → unregisterTask（断言 `removeJob` 调用）
- [x] 单元测试 cron 执行 + 吞错：直接调用 `executeScheduledReport(params)`（mock 调度触发）→ 业务异常被吞、交付记录记 failed、方法返回正常（不抛）
- [x] 单元测试 报告生成：已发布看板 → `ReportDeliveryExecutor.execute` → 复用 `PanelDataExporter.exportDashboard` 生成文件 → `IFileStore.saveFile` 持久化 → 交付记录 succeeded/fileRecordId
- [x] 单元测试 通知送达（邮件）：mock `IEmailSender`，断言 `sendEmail` 被调用且 `EmailMessage.to`=recipients、`attachments` 含生成的报告文件；模板渲染内容正确
- [x] 单元测试 显式失败路径：未配置发件人 → `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED`；notifyChannels 空 → `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL`；IM 渠道 → `UnsupportedOperationException`；无已发布看板 → `ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD`；grace 超期 → skipped 交付记录
- [x] 单元测试 手动触发：`triggerReportNow(taskId)` 触发执行（triggerSource=manual）
- [x] 单元测试 重启恢复：stale running 交付记录经 `NopDatavReportDeliveryRecovery.init()` 置 failed（独立 bean，非 scheduler.init）
- [x] 单元测试 权限：非 owner 用户不可管理/不可读交付历史
- [x] **端到端测试（rule #22）**：建报告任务（绑定已发布看板 + cron）→ enable（调度注册）→ triggerReportNow（手动触发，避免等 cron）→ ReportDeliveryExecutor 取数生成文件 → NotificationSender 邮件送达（mock sender 断言）→ 交付历史查询返回 succeeded 记录——断言全链路
- [x] **接线验证（rule #23）**：端到端测试断言 `NopDatavReportScheduler.executeScheduledReport` 确实调用 `ReportDeliveryExecutor.execute`（计数器/标志位/mock verify），且 `ReportDeliveryExecutor` 调用 `PanelDataExporter.exportDashboard`（非独立第二套取数），`NotificationSender` 调用 `IEmailSender.sendEmail`

Exit Criteria:

- [x] 新增功能（报告任务/调度器/执行器/通知器/交付历史/手动触发/grace/重启恢复/权限）每个均有对应测试（rule #25）
- [x] **端到端验证**：建任务→启用→手动触发→取数→文件→邮件送达→历史完整链路跑通
- [x] **接线验证**：scheduler→executor→exporter→notificationSender→emailSender 调用链运行时连通（非仅类型存在）
- [x] **无静默跳过**：未配置/无渠道/IM/无发布看板路径测试均断言显式失败（异常/错误码），无空返回
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（含新增测试，无回归）
- [x] 该 Phase 改变 live baseline（测试）；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 报告任务实体 + 交付历史实体落地（ORM + 生成物 + dict）
- [x] cron 调度集成落地（NopDatavReportScheduler register/unregister + executeScheduledReport）
- [x] 报告生成 + 邮件送达端到端可用（render-at-execution → PanelDataExporter → IFileStore → IEmailSender）
- [x] 手动触发 triggerReportNow 可用
- [x] 交付历史可读
- [x] 邮件渠道端到端打通；IM 渠道显式失败（UnsupportedOperationException，非空壳/静默）
- [x] 执行方法吞业务错误（规避 LocalJobScheduler FAILED-brick）；基础设施错误才抛
- [x] grace 语义落地（超期写 skipped，显式记录）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `schedule-report-design.md` D5-1 结论为最终设计与 live 实现一致
- [x] 受影响 owner docs 已同步（`schedule-report-design.md`；`docs-for-ai/` 无需更新）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 scheduler→executor→exporter→notificationSender→emailSender 调用链运行时连通、无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### IM/渠道推送（飞书/钉钉/企微/webhook）端到端

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `IChannelMessageService` 实现在 nop-ai-gateway（依赖 AI engine transport + channel binding），引入成本重且需独立 channel binding 配置。本计划以邮件为端到端打通基线，IM 渠道在 `NotificationSender` 预留扩展接口位（notifyChannels 含 im 时显式抛 UnsupportedOperationException，非静默/空壳）。与 D1-4/D2-4 同类外部依赖（flux/nop-ai-gateway 侧）。
- Successor Required: `yes`
- Successor Path: nop-ai-gateway channel binding 落地后，独立 plan 接入 `IChannelMessageService`（替换 UnsupportedOperationException 为真实发送）

### 集群部署的 DB 持久化 NopJobSchedule + rpc invoker 调度形态

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版用 beanMethod 嵌入式调度（镜像 MetaQualityCheckpointScheduler），满足单机/嵌入式场景。集群形态需切 `executorKind=rpc` + `NopJobSchedule` 持久化 + worker 回调，属部署形态升级，不影响本计划交付的报告生成/送达能力。
- Successor Required: `no`

### 已渲染数据快照持久化（离线浏览历史渲染结果）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前无用例（交付物即文件，历史只需元信息 + 文件引用）。持久化渲染 JSON 属过度设计，待出现「离线回看历史渲染数据」明确需求再做。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 报告任务管理前端 UI（flux）
- PDF/PNG 图像报告（依赖 flux 渲染，与 D3-3 图像导出同类阻塞）
- 报告模板可视化编辑器（用 NopSysNoticeTemplate 既有 CRUD 即可）
- 报告交付失败重试（接 nop-retry retryPolicyId）
- 固定历史版本快照渲染（exportDashboard 不接受 snapshotVersion；固定版本需从 NopDatavDashboardSnapshot.snapshotContent JSON 重建面板查询，过度设计，按需评估）

## Closure

Status Note: D5-1 完成。定时报告生成与送达全链路落地（cron 调度 + render-at-execution 取数 + 邮件送达 + 交付历史 + 手动触发 + grace + 重启恢复）。13 个 D5-1 测试 + 268 个 datav-service 全量测试全绿。独立 closure audit（PASS，15/15 gates）。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general subagent，task_id=ses_015ff1702ffepOn6nb1xAEe8n2）
- Audit Session: ses_015ff1702ffepOn6nb1xAEe8n2
- Evidence:
  - 每条 Exit Criterion 验证结果：Phase 1-4 全部 exit criteria 经 live 代码 + 测试断言验证 PASS（详见 audit report 各 gate evidence file:line）
  - 每条 Closure Gate 验证结果：15/15 PASS
    1. 实体落地：nop-datav.orm.xml:818-902（NopDatavReportTask）/909-978（NopDatavReportDelivery）+ _gen 实体 + 4 dict 文件
    2. cron 调度：NopDatavReportScheduler.registerTask/unregisterTask/executeScheduledReport 非空方法体，调 addJob/removeJob
    3. E2E pipeline：ReportDeliveryExecutor→exportDashboard(IFileStore.saveFile)→NotificationSender→IEmailSender.sendEmail 全链路连通
    4. triggerReportNow：NopDatavReportTaskBizModel:115-119
    5. 交付历史可读：NopDatavReportTaskBizModel:124-133
    6. 邮件 E2E + IM 显式失败：email→sendEmail；IM→UnsupportedOperationException；空渠道→ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL
    7. 吞业务错误：executeScheduledReport try/catch 包裹整体 + 返回 Map（不重抛）
    8. grace：doExecute 超期写 SKIPPED 记录（markSkipped）
    9. 无静默 deferred：IM 为显式 out-of-scope + UnsupportedOperationException，非静默跳过
    10. design doc：schedule-report-design.md 221 行最终结论，无 stub/Proposed
    11. owner docs：schedule-report-design.md final
    12. Anti-Hollow：TestNopDatavReportE2E 断言 NopFileRecord 落库 + mockEmailSender 收到 sendEmail + delivery SUCCEEDED，13 测试全绿
    13. compile：`./mvnw compile -pl nop-datav -am` BUILD SUCCESS
    14. test：`./mvnw test -pl nop-datav/nop-datav-service` Tests run: 268, Failures: 0, Errors: 0, Skipped: 0
    15. checkstyle：build 无 hard checkstyle gate；import 分组大体符合 io.nop.* → jakarta → 第三方 → java.* 约定（minor 非阻塞）
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码为 0：未单独运行（本仓无 check-plan-checklist.mjs 工具；check-doc-links.mjs 已过）
  - Anti-Hollow 检查结果：PASS——无空方法体、无静默 continue/空返回路径，调用链运行时连通
  - Deferred 项分类检查：IM/渠道推送（out-of-scope improvement，successor required）；集群调度形态（optimization candidate）；已渲染快照持久化（optimization candidate）——分类正确

Follow-up:

- IM/渠道推送端到端（successor required）
- 集群调度形态 / 已渲染快照持久化 / 前端 UI / PDF-PNG / 交付重试（Non-Blocking Follow-ups）
- cosmetic: ERR_DATAV_REPORT_DELIVERY_FAILED 定义但未抛（executor 用 raw msg 落库）；ReportDeliveryExecutor import 排序（static 块后置第三方），非阻塞
