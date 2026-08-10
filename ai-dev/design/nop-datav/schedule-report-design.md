# nop-datav 定时报告与告警设计 (D5)

> Status: **final** — D5-1（定时报告）最终结论。D5-2（轻量告警）有独立 plan，复用本文档的调度/通知抽象。
> Last Updated: 2026-08-10
> Owner plan: `ai-dev/plans/nop-datav/2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`

本文档为 D5-1 定时报告能力的设计契约（最终结论，非 Proposed vs Current 形式）。覆盖通知 provider 裁定、渲染时机、调度集成、实体契约、grace 语义、权限矩阵、模板渲染机制、取数契约、被拒方案及理由。

---

## 1. 通知 provider 裁定：走 nop-integration + nop-sys，不走 nop-message

**裁定**：定时报告通知走 `nop-integration-api` 的 `IEmailSender.sendEmail(EmailMessage)`（邮件实现 `JavaEmailSender` 等），配合 `nop-sys-dao` 的 `NopSysNoticeTemplate`（模板存储 + CRUD）。模板渲染由调用方编排（`NopSysNoticeTemplateBizModel` 仅 CRUD，无 render-and-dispatch 引擎）。

**拒用 `nop-message` 的理由**：`nop-message` 是 pub-sub 基础设施（Kafka/Pulsar/Debezium），用于服务间异步消息总线，与「向用户发送通知（邮件/IM）」的语义不符。roadmap 防重建表中将通知渠道记为「nop-message 复用」属措辞偏差——pub-sub 总线不提供邮件 SMTP 发送、模板渲染、收件人列表处理等用户通知语义。本计划修正 roadmap 措辞：通知能力实际在 `nop-integration`（发送）+ `nop-sys`（模板）。

**邮件为端到端打通基线**：`EmailMessage(subject, from, to, cc, text, html, attachments)` 支持 `List<IResourceReference>` 附件，满足报告文件附件送达需求。

**IM 渠道（飞书/钉钉/企微/webhook）裁定 deferred**：`IChannelMessageService.sendToUser` 实现在 `nop-ai-gateway`（依赖 AI engine transport + channel binding），引入成本重且需独立 channel binding 配置。本计划以邮件为端到端打通基线，IM 渠道在 `NotificationSender` 预留扩展位（notifyChannels 含 `im` 时显式抛 `UnsupportedOperationException`，非静默/空壳）。列为 `out-of-scope improvement`，successor required（见 §10）。

---

## 2. 渲染时机裁定：render-at-execution（调度触发时渲染）

**裁定**：每次 cron 触发 → 加载当前已发布看板配置 → 复用 `PanelDataExporter.exportDashboard(dashboardId, params, maxRows)` 逐面板取数生成导出文件 → 即时送达 → 写一条交付历史。

**拒「持久化已渲染数据快照表」的理由**：当前无「离线浏览历史渲染结果」用例；交付物即文件，历史只需元信息 + 文件引用。持久化渲染 JSON 属过度设计。列为 follow-up（出现明确离线回看需求再做）。

---

## 3. 调度集成裁定：beanMethod invoker + 可空注入 IJobScheduler

**裁定**：在 `nop-datav-service` 新增 `NopDatavReportScheduler` 普通 IoC bean（非 `@BizModel`），镜像 `MetaQualityCheckpointScheduler`（`nop-metadata-service`）：

- `@Inject setScheduler(@Nullable IJobScheduler scheduler)`——宿主 app 未注册调度器时 `scheduler == null`，`init()` 只 INFO 日志不抛错，任务仍可经 `triggerReportNow` 手动执行。
- `@PostConstruct init()`：防御性 `scheduler.activate()`（幂等）→ 扫描所有 `status=ENABLED` 报告任务 → 逐个 `addJob(JobSpec{jobInvoker=beanMethod, ...}, allowUpdate=true)`。单任务注册失败 try/catch 隔离，不抛崩启动。
- `registerTask(reportTaskId)` / `unregisterTask(reportTaskId)`：save/enable/disable/delete 时调用，使配置变更即时生效。
- 执行方法 `executeScheduledReport(Map<String,Object> params)`：单 `Map` 参数（适配 `BeanMethodJobInvoker` 的 singleMapFn 路径，规避 `-parameters` 编译标志依赖），委托 `ReportDeliveryExecutor.execute(taskId, triggerSource=schedule)`。

**LocalJobScheduler FAILED-brick 坑与约定**：`LocalJobScheduler` 下执行方法抛异常会把 job 永久置 `FAILED`（`addJob(allowUpdate=true)` 仅对 WAITING/SUSPENDED 重排程，FAILED 不复活，唯一恢复手段是重启 JVM）。故 `executeScheduledReport` 必须 **吞业务错误返回正常结果对象**（catch 业务异常 → 记 delivery failed → 返回正常结果，不抛）。仅基础设施错误（如 OOM）才向外传播。该约定与 `MetaQualityCheckpointScheduler.executeScheduledCheckpoint` 一致。

**拒「DB 持久化 NopJobSchedule + rpc invoker」作为首版的理由**：beanMethod 嵌入式调度更轻、与既有元数据调度范式一致；满足单机/嵌入式场景。集群形态需切 `executorKind=rpc` + `NopJobSchedule` 持久化 + worker 回调，属部署形态升级，列为 follow-up（optimization candidate，successor not required）。

**JobSpec 构造**：
- `jobName` = `nop-datav-report-` + `reportTaskId`（前缀避免与其它模块冲突）
- `jobGroup` = `nop-datav`
- `jobInvoker` = `beanMethod`
- `jobParams` = `{beanName: "nopDatavReportScheduler", methodName: "executeScheduledReport", reportTaskId: <id>}`
- `triggerSpec` = `{cronExpr: <task.cronExpr>}`

---

## 4. 实体契约

### 4.1 NopDatavReportTask（报告任务）

表 `nop_datav_report_task`，看板 owner 可管理对象（沿用 D3 owner RLS）。

| 字段 | 类型 | 说明 |
|------|------|------|
| reportTaskId | VARCHAR(32) PK | 报告任务 ID（seq） |
| taskName | VARCHAR(100) | 任务名 |
| displayName | VARCHAR(200) | 显示名 |
| dashboardId | VARCHAR(32) | 外键关联看板 |
| cronExpr | VARCHAR(100) | crontab 表达式 |
| format | VARCHAR(10) | 导出格式 csv/xlsx（复用 `datav/export-format` dict） |
| recipients | CLOB JSON | 收件人数组，如 `["a@b.com","c@d.com"]` |
| notifyChannels | CLOB JSON | 通知渠道数组，如 `["email"]` |
| params | CLOB JSON | 报告参数（透传给 PanelDataExporter） |
| status | INTEGER | 任务状态（`datav/report-task-status`：0=DISABLED, 10=ENABLED） |
| graceMinutes | INTEGER | misfire grace 期（默认 60） |
| templateKey | VARCHAR(100) | 模板键 → `NopSysNoticeTemplate.name` |
| lastRunTime | TIMESTAMP | 最后执行时间 |
| lastRunStatus | VARCHAR(20) | 最后执行状态（succeeded/failed/skipped） |
| lastRunError | VARCHAR(1000) | 最后执行错误 |
| delFlag / version / createdBy / createTime / updatedBy / updateTime / remark | 标准审计列 | 沿用既有惯例 |

**recipients/notifyChannels 存储格式 = CLOB JSON 数组**（与 `layoutConfig`/`paramConfig` 既有 json 列惯例一致；解析为 `List<String>` 构造 `EmailMessage.to`）。

**关系**：`to-one dashboard` → `NopDatavDashboard`（外键 dashboardId）。
**索引**：`IX_NOP_DATAV_REPORT_TASK_DASHBOARD(dashboardId)`、`IX_NOP_DATAV_REPORT_TASK_OWNER(createdBy)`、`IX_NOP_DATAV_REPORT_TASK_STATUS(status)`。

### 4.2 NopDatavReportDelivery（交付历史）

表 `nop_datav_report_delivery`，只记元信息 + 文件引用，不记渲染 JSON。

| 字段 | 类型 | 说明 |
|------|------|------|
| deliveryId | VARCHAR(32) PK | 交付记录 ID（seq） |
| reportTaskId | VARCHAR(32) | 外键关联报告任务 |
| status | INTEGER | 交付状态（`datav/delivery-status`：0=PENDING, 10=RUNNING, 20=SUCCEEDED, 30=FAILED, 40=SKIPPED） |
| generatedFileRecordId | VARCHAR(64) | 生成的文件记录 ID（IFileStore） |
| deliveredChannels | VARCHAR(200) | 已送达渠道（CSV：email） |
| rowCount | BIGINT | 导出行数 |
| errorMessage | VARCHAR(1000) | 错误信息 |
| triggeredBy | VARCHAR(20) | 触发来源（`datav/report-trigger-source`：schedule/manual） |
| startTime | TIMESTAMP | 开始时间 |
| endTime | TIMESTAMP | 结束时间 |
| delFlag / version / createdBy / createTime / updatedBy / updateTime / remark | 标准审计列 | 沿用既有惯例 |

**关系**：`to-one reportTask` → `NopDatavReportTask`（外键 reportTaskId）。
**索引**：`IX_NOP_DATAV_REPORT_DELIVERY_TASK(reportTaskId)`、`IX_NOP_DATAV_REPORT_DELIVERY_STATUS(status)`。

### 4.3 dict 定义

- `datav/report-task-status`（int）：DISABLED=0, ENABLED=10
- `datav/delivery-status`（int）：PENDING=0, RUNNING=10, SUCCEEDED=20, FAILED=30, SKIPPED=40
- `datav/report-trigger-source`（string）：SCHEDULE=schedule, MANUAL=manual
- `datav/notify-channel`（string）：EMAIL=email, IM=im
- `format` 复用 `datav/export-format`（csv/xlsx，与导出共用）

---

## 5. grace 语义

参考 Superset grace：配置项 `CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES`（默认 60）。触发时若距预定时间（nextFireTime）超过 grace 则跳过本轮（misfire 兜底），**写一条 `status=SKIPPED` 的交付记录**（errorMessage 记 "skipped due to misfire grace exceeded"，**显式记录，非静默跳过**）。

实现约定：`ReportDeliveryExecutor.execute` 接收 `scheduledFireTime` 参数，若 `now - scheduledFireTime > graceMinutes*60*1000` 则写 skipped 记录并返回（不取数、不送达）。cron 触发时 `scheduledFireTime` 取 `System.currentTimeMillis()`（LocalJobScheduler 触发即近似预定时间，无 Quartz misfire 语义，grace 主要兜底手动/恢复场景的延迟执行）。

---

## 6. 模板渲染机制

**裁定**：渲染用 `StringHelper.renderTemplate(content, transformer)`（`{var}` 占位符替换，平台既有消息渲染已用），**不用 XPL 引擎**。

- `NopSysNoticeTemplate.tplType` 取值集新增 `rpt-deliv`（约定值，本计划定义；受 `TPL_TYPE` 列 precision=10 限制取简短形式）。
- 模板键映射：`NopDatavReportTask.templateKey` → `NopSysNoticeTemplate.name`。缺失（按 name 查不到）抛 `ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND`。
- 变量绑定上下文 = `{reportName, dashboardName, generatedTime, fileName, fileSize, rowCount, deliveryStatus}`。
- 模板内容同时用作 subject（首行）与 body（剩余行），或约定 `NopSysNoticeTemplate.content` 整体为 body，subject 由配置项 `CFG_DATAV_REPORT_DEFAULT_SUBJECT`（默认 `"Report: {reportName}"`）渲染。本计划采后者（subject 配置项 + body 模板，简化模板结构）。

**拒「XPL 引擎」的理由**：简单 `{var}` 占位符替换满足通知文本需求；XPL 引擎（条件/循环/宏）对邮件正文过度复杂，且引入 XPL 解析依赖。列 follow-up（若需条件渲染再评估）。

---

## 7. 取数契约：基于当前已发布看板（实时数据）

**裁定**：报告渲染复用 `PanelDataExporter.exportDashboard(dashboardId, params, maxRows)`，该方法内部按 `dashboardId` 查询当前 `NopDatavPanel` 表逐面板取数（`PanelDataExporter.java:107`），数据始终为触发时的**实时数据**（符合「定时报告」语义——定时对当前数据做快照并发送）。

**校验**：`ReportDeliveryExecutor` 调 `NopDatavDashboardBizModel.getPublishedDashboard(dashboardId)` 校验看板已发布（有发布快照，`publishStatus=PUBLISHED`）；无发布快照抛 `ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD`。

**拒「固定历史版本快照渲染」的理由**：`exportDashboard` 不接受 `snapshotVersion`，固定版本需从 `NopDatavDashboardSnapshot.snapshotContent` JSON 自行重建面板查询（解析 layout/panels/datasetRefs → 逐面板重新绑定数据集引用 → 求值参数 → 执行查询），属过度设计。列 follow-up（按需评估，当前无用例）。

---

## 8. 权限矩阵

沿用 D3-1/D3-3 看板 `@Auth` + owner 行级 RLS 模式。报告任务属看板 owner 可管理对象。

| 操作 | 权限 |
|------|------|
| 报告任务 CRUD（save/update/delete） | 看板 owner/admin（经 `NopDatavDashboardOwnerGuard.requireDashboardOwnership` 校验 dashboardId 的 owner，或经 `nop-datav.data-auth.xml` 的 `NopDatavReportTask` role-auth） |
| enableReportTask / disableReportTask / triggerReportNow | owner/admin |
| getReportDeliveryHistory | owner/admin |
| cron 触发执行（无用户上下文） | 系统级（scheduler bean 直接调 executor，不经 BizProxy/Auth） |

**data-auth**：`nop-datav.data-auth.xml` 增 `NopDatavReportTask` role-auth（admin 全量；user 限 `createdBy == userName`）。`NopDatavReportDelivery` 通过 `reportTaskId → reportTask.createdBy` 间接归属（查询时 join 校验，或经 BizModel 层 `requireOwner` 显式校验）。

**action-auth**：`nop-datav.action-auth.xml` 增 `NopDatavReportTask` / `NopDatavReportDelivery` 的 query/mutation + 自定义 action（enable/disable/triggerNow/getDeliveryHistory）权限点 + 角色绑定（admin 全量；user 查询 + triggerNow）。

---

## 9. 执行方法签名约定

`NopDatavReportScheduler.executeScheduledReport(Map<String,Object> params)`：
- **单 `Map<String,Object>` 参数**（适配 `BeanMethodJobInvoker` 的 singleMapFn 路径，规避 `-parameters` 编译标志反射形参名依赖，与 `MetaQualityCheckpointScheduler.executeScheduledCheckpoint` 一致）。
- **吞业务错误返回正常结果对象**：catch 所有 `Exception` → 记 delivery failed + ERROR 日志 → 返回 `Map{reportTaskId, status: "failed", error: <msg>}`。不向外抛（规避 LocalJobScheduler FAILED-brick）。
- **仅基础设施错误（Error/RuntimeException 非业务异常）才抛**：如 `OutOfMemoryError`。业务异常（`NopException`、`RuntimeException` 含明确业务原因）一律吞。

返回值结构（正常 + 业务失败均返回此结构，区别于抛异常）：
```
{
  reportTaskId: <id>,
  status: "succeeded" | "failed" | "skipped",
  deliveryId: <id>,
  error: <errorMessage or null>
}
```

---

## 10. 渠道范围与显式失败约定

| 场景 | 行为 |
|------|------|
| 邮件渠道（notifyChannels 含 email） | 解析 recipients JSON 数组 → 构造 `EmailMessage` → `IEmailSender.sendEmail` |
| 未配置邮件发件人（`CFG_DATAV_REPORT_DEFAULT_SENDER` 为空且 EmailMessage.from 未设） | 抛 `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED`（显式失败，不静默跳过） |
| notifyChannels 为空 JSON 数组 `[]` | 抛 `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL`（显式失败） |
| IM 渠道（notifyChannels 含 im） | 抛 `UnsupportedOperationException("IM channel not yet implemented: ...")`（非静默/空壳，明确告知未实现） |
| grace 超期 | 写 `status=SKIPPED` 交付记录（显式记录） |

**约定（Minimum Rules #24）**：所有失败路径显式化（抛错/UnsupportedOperationException/显式状态记录），**无静默跳过/continue/空返回**。

---

## 11. 被拒方案汇总

| 方案 | 拒用理由 | 分类 |
|------|----------|------|
| 通知走 nop-message（pub-sub） | nop-message 是 Kafka/Pulsar pub-sub 总线，与用户通知语义不符；邮件 SMTP/模板渲染/收件人列表不在 pub-sub 语义内 | 修正 roadmap 措辞偏差 |
| 持久化已渲染数据快照表 | 当前无离线浏览用例；交付物即文件，历史只需元信息；过度设计 | follow-up |
| DB 持久化 NopJobSchedule + rpc invoker 调度形态 | 首版嵌入式 beanMethod 更轻；集群形态列 follow-up | optimization candidate |
| 固定历史版本快照渲染 | exportDashboard 不接受 snapshotVersion；重建成本高；过度设计 | follow-up |
| XPL 引擎渲染模板 | 简单 {var} 占位符满足需求；XPL 过重 | follow-up |
| IM/渠道推送端到端落地 | IChannelMessageService 实现在 nop-ai-gateway 较重 + 需 channel binding | out-of-scope improvement（successor required） |

---

## 12. 实现组件清单

| 组件 | 职责 |
|------|------|
| `NopDatavReportTaskBizModel`（`@BizModel`） | CRUD + enable/disable/triggerNow/getDeliveryHistory action，经 `@Auth` + owner RLS |
| `INopDatavReportTaskBiz`（dao 层接口） | action 签名 |
| `NopDatavReportScheduler`（普通 bean） | `@PostConstruct init()` 注册 cron job；registerTask/unregisterTask；executeScheduledReport(Map) 吞业务错误 |
| `ReportDeliveryExecutor`（普通 bean） | 插 pending 交付记录 → 异步执行（GlobalExecutors + runInNewSession）→ 取数（PanelDataExporter.exportDashboard）→ 文件落盘（IFileStore）→ 通知送达（NotificationSender）→ 更新交付记录 |
| `NotificationSender`（普通 bean） | 渲染模板（StringHelper.renderTemplate + NopSysNoticeTemplate）→ 按 notifyChannels 分发（email → IEmailSender.sendEmail 带附件；im → UnsupportedOperationException） |
| `NopDatavReportDeliveryRecovery`（普通 bean） | `@PostConstruct` 幂等扫描 stale running 交付记录 → failed（reason=interrupted by process restart），镜像 `NopDatavExportTaskRecovery` |
| `NopDatavConfigs`（增配置项） | `CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES`(60) / `CFG_DATAV_REPORT_MAX_ROWS`(100000) / `CFG_DATAV_REPORT_DEFAULT_SENDER`("") / `CFG_DATAV_REPORT_DEFAULT_SUBJECT`("Report: {reportName}") |
| `NopDatavErrors`（增错误码） | `ERR_DATAV_REPORT_TASK_NOT_FOUND` / `ERR_DATAV_REPORT_CRON_INVALID` / `ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD` / `ERR_DATAV_REPORT_DELIVERY_FAILED` / `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL` / `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED` / `ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND` / `ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER` |

**职责分离**：`NopDatavReportScheduler.init()` 只负责重注册 cron job；`NopDatavReportDeliveryRecovery.init()` 只负责清理 stale running 交付记录；二者不混入对方职责（镜像 `NopDatavExportTaskRecovery` 独立于 BizModel 的模式）。
