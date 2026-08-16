# nop-datav 定时报告与告警设计 (D5)

> Status: **final** — D5-1（定时报告）+ D5-2（轻量告警）+ IM 渠道通知接入 + stuck-task 周期恢复最终结论。
> Last Updated: 2026-08-14
> Owner plans: `ai-dev/plans/nop-datav/2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`、`ai-dev/plans/nop-datav/2026-08-10-1230-2-lightweight-alert-threshold-rearm-notification.md`、`ai-dev/plans/nop-datav/2026-08-14-0937-1-im-channel-notification-integration.md`、`ai-dev/plans/nop-datav/2026-08-14-1510-1-periodic-stuck-task-recovery-scanner.md`

本文档为 D5-1 定时报告 + D5-2 轻量告警的设计契约（最终结论，非 Proposed vs Current 形式）。D5-1 章节覆盖通知 provider 裁定、渲染时机、调度集成、实体契约、grace 语义、权限矩阵、模板渲染机制、取数契约、被拒方案及理由；D5-2 章节覆盖告警标量聚合、状态机、rearm、告警通知变体、告警状态独立实体、面板容错、权限矩阵；§25 覆盖交付/导出记录的 stuck-task 周期恢复。

---

## 1. 通知 provider 裁定：走 nop-integration + nop-sys，不走 nop-message

**裁定**：定时报告通知走 `nop-integration-api` 的 `IEmailSender.sendEmail(EmailMessage)`（邮件实现 `JavaEmailSender` 等），配合 `nop-sys-dao` 的 `NopSysNoticeTemplate`（模板存储 + CRUD）。模板渲染由调用方编排（`NopSysNoticeTemplateBizModel` 仅 CRUD，无 render-and-dispatch 引擎）。

**拒用 `nop-message` 的理由**：`nop-message` 是 pub-sub 基础设施（Kafka/Pulsar/Debezium），用于服务间异步消息总线，与「向用户发送通知（邮件/IM）」的语义不符。roadmap 防重建表中将通知渠道记为「nop-message 复用」属措辞偏差——pub-sub 总线不提供邮件 SMTP 发送、模板渲染、收件人列表处理等用户通知语义。本计划修正 roadmap 措辞：通知能力实际在 `nop-integration`（发送）+ `nop-sys`（模板）。

**邮件为端到端打通基线**：`EmailMessage(subject, from, to, cc, text, html, attachments)` 支持 `List<IResourceReference>` 附件，满足报告文件附件送达需求。

**IM 渠道（飞书/钉钉/企微/webhook）已接入**：`NotificationSender` 经 `nop-integration-api` 的 `IChannelMessageService.sendToUser(userId, OutboundChannelMessage)` 发送文本/Markdown 主动通知（出站，不含文件附件）。`IChannelMessageService` 接口落 `nop-integration-api`（仅依赖 `nop-api-core`，任何业务模块可发送，不依赖 AI engine），实现 `ChannelMessageServiceImpl` 落 `nop-ai-gateway`（resolver→connector→sendOutbound 真实路径）。`NotificationSender` 仅依赖接口，bean `channelMessageService`（`ioc:default=true`）由宿主 app 装配 `nop-ai-gateway` 提供；未装配时注入 null → IM 渠道显式失败（见 §10）。IM 渠道收件人身份模型、聚合语义、附件范围见 Decision A/B/C（§1.1/§1.2/§1.3）。

**部署前提（非本设计实现）**：生产环境 IM 可用要求宿主 app 装配 `nop-ai-gateway`（提供 `channelMessageService` bean）。

### 1.1 Decision A — 收件人身份模型：格式检测分区（format-detection partition）

`recipients`（clobJson 字符串数组）为所有渠道共享。按条目**格式**分区路由：

- 匹配邮箱正则 `^\S+@\S+\.\S+$` 的条目 → **email 收件人**（email 渠道消费）。
- 其余非空条目 → **平台 userId**（im 渠道消费，传给 `sendToUser(userId, ...)`）。

实现约束（分区先于投递调用）：在 `sendReport`/`sendAlert` 入口先按上述规则把 `recipients` 分区为 `emailAddrs` / `userIds` 两组，再按渠道投递：

- email 渠道：**仅当 `emailAddrs` 非空时**调用 `deliverViaEmail(emailAddrs)`；`emailAddrs` 为空 → **跳过该渠道**（不调用，从而不触发其空收件人防御性 throw），不计入 `delivered`。
- im 渠道：对 `userIds` 中每个条目调 `sendToUser`；`userIds` 为空 → 跳过该渠道，不计入 `delivered`。
- 若**所有**被请求渠道经分区后可寻址收件人均为 0（即 `delivered` 最终为空）→ 显式失败 `ERR_DATAV_*_ALL_NOTIFY_FAILED`。

**向后兼容性**：纯邮箱任务（recipients 全为邮箱格式 + channels=["email"]）：分区后 `emailAddrs`=原列表、`userIds`=空，email 照常投递——行为完全不变。混合任务 `recipients=["a@example.com","u1","u2"]` + `channels=["email","im"]` → email 发给 a@example.com、im 发给 u1/u2。

**拒绝的替代方案**：结构化 recipients `[{"channel":"email","addr":...}]`（与既有 `["a@example.com"]` 格式不向后兼容，需数据迁移）；新增 `imRecipients` 列（ORM 结构变更）；「IM 收件人恒为任务 owner」（过窄，不符合通知列表语义）。

### 1.2 Decision B — SendResult 处理：渠道聚合语义（channel-aggregate）

`sendToUser` 不为 NO_BINDING/UNSUPPORTED 抛异常（接口契约），IM 渠道按**渠道级聚合**判定投递结果：

- 逐 userId 调 `sendToUser`，**逐用户 try/catch**：某用户发送抛异常（网络/连接器错误）→ catch `Exception`（非 `Throwable`，JVM 级 `Error` 向外传播）、WARN 日志（userId + 异常）、计为该用户非 SENT，**不中断循环**。
- 渠道聚合结果：**≥1 用户返回 SENT → 该渠道 delivered（"im" 加入 delivered 列表）**；全部用户 NO_BINDING/UNSUPPORTED/抛异常 → 该渠道未投递（"im" 不入 delivered）。
- 投递级判定：`delivered` 为空（无渠道成功）→ 抛 `NopException`（`ERR_DATAV_REPORT_ALL_NOTIFY_FAILED` / `ERR_DATAV_ALERT_ALL_NOTIFY_FAILED`，英文消息列出失败渠道），被 executor 捕获 → delivery FAILED。
- channelMessageService 未注入（null）→ IM 渠道显式抛 `ERR_DATAV_*_CHANNEL_SERVICE_NOT_CONFIGURED`（非静默/非空壳）。

**repo-observable 契约**："im" ∈ `delivery.deliveredChannels` ⟺ ≥1 用户 SENT；全失败 → `delivery.status=FAILED` 且 errorMsg 提及 NO_BINDING/UNSUPPORTED；单用户 NO_BINDING/UNSUPPORTED → WARN 日志（运维可见），不进 delivery 字段。

### 1.3 Decision C — IM 附件：v1 不投递文件附件（out-of-scope）

IM 渠道**仅发送文本/Markdown 通知**（报告摘要：reportName/dashboardName/rows/fileName；告警：ruleName/state/currentValue/threshold），**不携带生成的导出文件作为附件**。理由：`IFileRecord` 无 `getUrl()`，无可派生的外部可访问下载 URL（需下载端点 + base path + 鉴权）；`FeishuConnector` 仅消费 `Attachment.url`，忽略 `Attachment.content`(byte[])；文件经 email 渠道附件已可达（email 渠道行为不变）。列为 Deferred「IM 渠道文件附件投递」（out-of-scope improvement，successor required：下载 URL 方案落地后）。

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
- **吞业务错误返回正常结果对象**：`catch (Exception)` → 记 delivery failed + ERROR 日志 → 返回 `Map{reportTaskId, status: "failed", error: <msg>}`。不向外抛（规避 LocalJobScheduler FAILED-brick）。
- **仅基础设施错误（`Error`）才抛**：如 `OutOfMemoryError`、`StackOverflowError`。实现契约为 `catch (Exception)`（非 `catch (Throwable)`），使 `Error` 子类不被捕获而向外传播。业务异常（`NopException`、`RuntimeException` 含明确业务原因）一律吞。此约定同时适用于 `NopDatavAlertScheduler.executeScheduledAlert`（Dim14-04），以及交付执行器与导出执行器：`ReportDeliveryExecutor`（`execute` 异步 submit 边界、`runDeliveryInSession`、`sendNotificationOutOfSession`、`executeSyncForTest`）和 `NopDatavExportTaskBizModel`（`submitExecution` 异步 submit 边界、`executeTask`）。异步 submit 边界 Error 传播后，交付/导出任务记录可能停留在 SCHEDULED/RUNNING——此类为 JVM 级严重故障（OOM/StackOverflow），需人工介入，不通过 catch(Throwable) 掩盖。

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
| 邮件渠道（notifyChannels 含 email） | 按 Decision A 分区 recipients → 仅 emailAddrs 非空时构造 `EmailMessage` → `IEmailSender.sendEmail`；emailAddrs 为空 → 跳过该渠道（不计入 delivered） |
| 未配置邮件发件人（`CFG_DATAV_REPORT_DEFAULT_SENDER` 为空且 EmailMessage.from 未设） | 抛 `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED`（显式失败，不静默跳过） |
| notifyChannels 为空 JSON 数组 `[]` | 抛 `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL`（显式失败） |
| IM 渠道（notifyChannels 含 im） | 按 Decision A 分区 → 仅 userIds 非空时构造 `OutboundChannelMessage`（文本，无附件）→ 逐 userId `sendToUser` → 按 Decision B 渠道聚合（≥1 SENT → delivered；全失败 → 未投递）。channelMessageService 未注入 → 抛 `ERR_DATAV_*_CHANNEL_SERVICE_NOT_CONFIGURED`（显式失败）。IM 仅文本/Markdown，不带文件附件（Decision C） |
| 所有被请求渠道经分区后零可寻址收件人 / 全失败（delivered 为空） | 抛 `ERR_DATAV_*_ALL_NOTIFY_FAILED`（英文消息列出失败渠道 + NO_BINDING 计数），被 executor 捕获 → delivery FAILED |
| grace 超期 | 写 `status=SKIPPED` 交付记录（显式记录） |

**约定（Minimum Rules #24）**：所有失败路径显式化（抛错/显式状态记录），**无静默跳过/continue/空返回/吞异常**。单用户 NO_BINDING/UNSUPPORTED/异常 → catch + WARN 日志（非吞掉），计入渠道聚合。

---

## 11. 被拒方案汇总

| 方案 | 拒用理由 | 分类 |
|------|----------|------|
| 通知走 nop-message（pub-sub） | nop-message 是 Kafka/Pulsar pub-sub 总线，与用户通知语义不符；邮件 SMTP/模板渲染/收件人列表不在 pub-sub 语义内 | 修正 roadmap 措辞偏差 |
| 持久化已渲染数据快照表 | 当前无离线浏览用例；交付物即文件，历史只需元信息；过度设计 | follow-up |
| DB 持久化 NopJobSchedule + rpc invoker 调度形态 | 首版嵌入式 beanMethod 更轻；集群形态列 follow-up | optimization candidate |
| 固定历史版本快照渲染 | exportDashboard 不接受 snapshotVersion；重建成本高；过度设计 | follow-up |
| XPL 引擎渲染模板 | 简单 {var} 占位符满足需求；XPL 过重 | follow-up |
| 结构化 recipients `[{"channel":"email","addr":...}]`（IM 收件人身份模型） | 与既有 `["a@example.com"]` 格式不向后兼容，需数据迁移 | Decision A 替代方案（拒） |
| 新增 `imRecipients` 列（IM 收件人身份模型） | ORM 结构变更（Protected Area plan-first），v1 不必要 | Decision A 替代方案（拒） |
| 「IM 收件人恒为任务 owner」（IM 收件人身份模型） | 过窄，不符合通知列表语义 | Decision A 替代方案（拒） |
| IM 文件附件投递（v1） | `IFileRecord` 无 `getUrl()`，无可派生外部下载 URL；`FeishuConnector` 忽略 `Attachment.content`；文件经 email 附件已可达 | Decision C（out-of-scope improvement，successor required） |

---

## 12. 实现组件清单

| 组件 | 职责 |
|------|------|
| `NopDatavReportTaskBizModel`（`@BizModel`） | CRUD + enable/disable/triggerNow/getDeliveryHistory action，经 `@Auth` + owner RLS |
| `INopDatavReportTaskBiz`（dao 层接口） | action 签名 |
| `NopDatavReportScheduler`（普通 bean） | `@PostConstruct init()` 注册 cron job；registerTask/unregisterTask；executeScheduledReport(Map) 吞业务错误 |
| `ReportDeliveryExecutor`（普通 bean） | 插 pending 交付记录 → 异步执行（GlobalExecutors）→ session 内（runInNewSession）：取数（PanelDataExporter.exportDashboard）+ 文件落盘（IFileStore）+ 交付记录 SUCCEEDED 提交 → session 关闭后通知送达（NotificationSender，SMTP 不持 JDBC 连接，Dim14-02）→ 成功回写 deliveredChannels / 失败回补 FAILED |
| `NotificationSender`（普通 bean） | 渲染模板（StringHelper.renderTemplate + NopSysNoticeTemplate）→ 按 notifyChannels 分发（email → IEmailSender.sendEmail 带附件；im → IChannelMessageService.sendToUser 文本/Markdown 无附件，按 Decision A 分区 + Decision B 渠道聚合）。IEmailSender/IChannelMessageService 均 @Nullable 注入，未注入时对应渠道显式失败 |
| `NopDatavReportDeliveryRecovery`（普通 bean） | `@PostConstruct` 幂等扫描 stale running 交付记录 → failed（reason=interrupted by process restart），镜像 `NopDatavExportTaskRecovery` |
| `NopDatavConfigs`（增配置项） | `CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES`(60) / `CFG_DATAV_REPORT_MAX_ROWS`(100000) / `CFG_DATAV_REPORT_DEFAULT_SENDER`("") / `CFG_DATAV_REPORT_DEFAULT_SUBJECT`("Report: {reportName}") |
| `NopDatavErrors`（增错误码） | `ERR_DATAV_REPORT_TASK_NOT_FOUND` / `ERR_DATAV_REPORT_CRON_INVALID` / `ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD` / `ERR_DATAV_REPORT_DELIVERY_FAILED` / `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL` / `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED` / `ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND` / `ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER` / `ERR_DATAV_REPORT_CHANNEL_SERVICE_NOT_CONFIGURED`（IM service 未注入）/ `ERR_DATAV_REPORT_ALL_NOTIFY_FAILED`（全渠道零成功） |

**职责分离**：`NopDatavReportScheduler.init()` 只负责重注册 cron job；`NopDatavReportDeliveryRecovery.init()` 只负责清理 stale running 交付记录；二者不混入对方职责（镜像 `NopDatavExportTaskRecovery` 独立于 BizModel 的模式）。

---

## 12b. 报告异步提交事务边界（AR-2，plan `2026-08-15-2146-2` Phase 2）

**裁定**：`ReportDeliveryExecutor.execute` 的 worker 提交与导出路径对称——事务上下文内
（`triggerReportNow` 为 @BizMutation，经 GraphQL 事务装饰器持 REQUIRED 事务）经
`ITransactionTemplate.afterCommit` 注册 `globalWorker().submit`，pending 交付行 commit 后才被消费；
无事务上下文（cron 路径 `NopDatavReportScheduler.executeScheduledReport`，`BeanMethodJobInvoker`
纯反射无事务装饰）保持立即提交（`isTransactionOpened` 守护分支，不抛
`ERR_TXN_NOT_IN_TRANSACTION`）；事务回滚时 onAfterCommit 不触发（回滚则不提交异步任务）。

worker 首查 null 分支（`runDeliveryInSession`）从静默 `return null` 改为：短退避重查
（3 次 × 200ms）后仍缺行 → ERROR 日志（含 reportTaskId/deliveryId）+ `markFailedSafe`
显式失败（No Silent No-Op；正常情况下 afterCommit 时序保证行必然可见，该分支只剩基础设施异常）。

时序回归锚定：`TestNopDatavAsyncSubmitTransactionPath`（afterCommit 注册语义 seam 断言 +
graphQLEngine mutation 真实事务路径 E2E）。

---

## 12c. 告警通知事务边界（P1-06，plan `2026-08-15-2146-2` Phase 3，裁定主案 (a)）

**缺陷**：`evaluateAlertNow`（@BizMutation，user 可调）链路内 `AlertEvaluator.evaluate` 的
`sendAlertNotification` 同步 SMTP/IM 远程发送（典型 30-60s 超时）+ 多次状态写全程运行在数据库
事务内。cron 路径不受影响（`BeanMethodJobInvoker` 纯反射无事务装饰）。

**裁定（主案 (a)，与 rearm 契约不冲突的形态）**：

- **事务内**：写 interim 状态（TRIGGERED/OK 状态转换 + lastTriggeredTime/consecutiveEvalCount 等
  审计列，**不含** lastNotifiedTime/lastResolvedTime）并随事务提交；
- **commit 后**（`ITransactionTemplate.afterCommit`）：发送通知（远程调用不进事务）；
- **发送成功后**：**REQUIRES_NEW 独立短事务**重载状态行回写 lastNotifiedTime/lastResolvedTime
  （事务内的 attached 实体 commit 后已失效，不作回写载体）。REQUIRES_NEW 而非 REQUIRED：afterCommit
  listener 执行期间外层事务仍在线程注册表（其 cleanup 在 `commit()` 返回后才执行），REQUIRED 会
  「加入已提交的外层事务」导致回写 SQL 悬空丢失（实现期实证捕获）；
- **发送失败**：实现内显式 **ERROR 日志**（不静默吞——平台 `invokeListener(ignoreError=true)` 会吞
  listener 异常仅打通用日志，可观测锚定由实现内 ERROR 兑现）且**不回写**：lastNotifiedTime 保持
  null/旧值 → rearm 契约保持（仅通知成功后写入；失败留 null 立即重试）。

**语义微调（显式落档）**：

| 面 | 修复前 | 修复后（主案 (a)） |
|----|--------|-------------------|
| 事务内通知发送失败 | 异常上抛 → mutation 整体回滚（状态不变，用户见 GraphQL 错误） | commit 后发送失败：interim 状态已提交（客观事实），ERROR 日志，调用方无异常；rearm 下次重试 |
| `evaluateAlertNow` 同步返回值 | 通知已发送后的终态（lastNotifiedTime 已写） | 通知发送前的中间态（lastNotifiedTime 尚未回写，异步补写） |
| `EvalResult.notified`（事务路径） | 已送达 | 「已注册待发」（该结果仅 cron 路径消费，cron 路径语义不变） |
| cron 路径（无事务） | 同步发送 + 成功后立即回写 | **不变**（逐条等价，`TestNopDatavAlertE2E` 全量锚定） |

**语义锚定**：`TestNopDatavAlertNotifyTransactionBoundary`——事务内未发送 + interim 状态可见 +
commit 后发送与回写（TRIGGER/RECOVER 双路径）；发送失败不回写 + rearm 重试闭环；无事务路径同步
（守护回归）。

**拒绝的替代方案**：

| 方案 | 拒绝理由 |
|------|---------|
| (b) 发送移独立异步线程（不等 commit） | 交付记录（此处为状态行）未提交时发送可能引用将回滚的数据；且仍需两段状态写，复杂度高于 (a) 无额外收益 |
| (c) 维持现状 | P1-06 为已确认缺陷（远程调用进事务），不可接受 |
| 回写用 REQUIRED 短事务 | afterCommit 期间外层事务仍注册，REQUIRED 加入已提交事务 → 回写丢失（实证） |
| 发送失败时反向补偿（回滚 interim 状态） | interim 状态是客观事实（条件确已满足/恢复）；回滚会伪造历史；rearm 重试语义已足够 |

---

# D5-2 轻量告警设计

## 13. 标量聚合契约

**裁定**：告警评估对象 = 面板查询结果的一个标量值。契约：

- `valueField`（VARCHAR，必填）：面板结果列名。列不存在抛 `ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND`（显式失败，非静默跳过）。
- `aggregation`（VARCHAR，dict `datav/alert-aggregation`）：none/first/sum/avg/min/max/count，默认 `first`（取首行该列值）。
- 标量计算规则：
  - `none`/`first`：取首行 `valueField` 列值；无数据行 → 视为条件不满足（不触发、不抛错），`consecutiveEvalCount` 归 0。
  - `sum`/`avg`/`min`/`max`：对返回的所有行 `valueField` 列聚合；基于返回行近似计算（受 `CFG_DATAV_ALERT_EVAL_MAX_ROWS` 行数安全上限约束），**精确聚合应让面板数据集 SQL 层预聚合返回单行**（不做 JDBC queryTimeout 超时配置，列 follow-up）。
  - `count`：返回行数（不读 `valueField` 列值）。
- **类型转换**：`PanelDataResult.rows` 值为 `Object`（BigDecimal/Long/String 等，依 JDBC dialect），评估器统一转 `BigDecimal` 比较；非数值类型抛 `ERR_DATAV_ALERT_VALUE_NOT_NUMERIC`（显式失败）。
- **阈值存储**：`thresholdValue`（DECIMAL，必填，主比较值）+ `thresholdValue2`（DECIMAL，可空，仅 `between` 用，存上限）；单值 operator 仅用 `thresholdValue`，`between` 用 `[thresholdValue, thresholdValue2]`（`thresholdValue2` 为 null 时抛 `ERR_DATAV_ALERT_INVALID_THRESHOLD`）。

**拒「多行整体判定」的理由**：多行判定需定义多行→单布尔的聚合语义（任一满足/全部满足/计数），scope 过宽，首版单标量聚合满足轻量告警核心。

**拒「单 VARCHAR 字段存 JSON `{min,max}`」的理由**：不可查询、类型校验复杂；DECIMAL 双列结构化存储便于 SQL 直查与类型校验。

---

## 14. operator 字典与语义

dict `datav/alert-operator`（string）：gt/gte/lt/lte/eq/neq/between。

| code | value | 语义（currentValue 为评估后的标量） |
|------|-------|------------------------------------|
| GT | gt | `currentValue > thresholdValue` |
| GTE | gte | `currentValue >= thresholdValue` |
| LT | lt | `currentValue < thresholdValue` |
| LTE | lte | `currentValue <= thresholdValue` |
| EQ | eq | `currentValue == thresholdValue`（BigDecimal `.compareTo() == 0`） |
| NEQ | neq | `currentValue != thresholdValue` |
| BETWEEN | between | `thresholdValue <= currentValue <= thresholdValue2`（thresholdValue2 为 null → `ERR_DATAV_ALERT_INVALID_THRESHOLD`） |

---

## 15. 状态机：两态 OK/TRIGGERED + rearm 冷静期

**裁定**：`state` 列只持久化 `OK` / `TRIGGERED` 两态（dict `datav/alert-state` 只含这两值）。`RESOLVED` 不入 dict——它是 `TRIGGERED → OK` 转换时的一次性恢复通知动作（瞬态），不持久化为独立状态。

### 转换表

> **通知顺序语义（Dim14-03）**：所有含通知动作的转换均遵循「先持久化 interim state → 发通知 →
> 成功后才回写通知时间戳」的顺序。`lastNotifiedTime` / `lastResolvedTime` 仅在通知成功后设置。
> 通知失败时异常向上抛（由调度器层吞错返回正常结果），时间戳保持原值——下次评估可重试通知。

| 当前态 | 条件 | 目标态 | 通知动作 | 时间戳更新 |
|--------|------|--------|----------|-----------|
| OK | 条件满足 | TRIGGERED | 发告警通知（trigger） | `lastTriggeredTime = now`（interim save 前置）；`lastNotifiedTime = now`（仅通知成功后设置） |
| TRIGGERED | 条件不再满足 | OK | 发恢复通知（recover） | `lastResolvedTime = now`（仅通知成功后设置） |
| TRIGGERED | 条件持续 + `rearmSeconds > 0` + `now - lastNotifiedTime >= rearmSeconds` | TRIGGERED | 重发告警通知（trigger，rearm） | `lastNotifiedTime = now`（仅通知成功后设置） |
| TRIGGERED | 条件持续 + `rearmSeconds == 0` | TRIGGERED | 不重复 | （无） |
| OK | 条件不满足 | OK | 不通知 | （无） |

**重启安全（restart safety）**：若 JVM 在 interim save 与通知后 save 之间崩溃，`NopDatavAlertState`
为 `TRIGGERED` + `lastNotifiedTime = null`。`rearmSeconds > 0` 时下次评估经 `shouldRearm(null) = true`
重新通知；`rearmSeconds == 0` 时 `rearmSeconds > 0` guard 短路进入不重复通知分支（无重试机制，by design）。

### rearm 语义

- `rearmSeconds == 0`：仅状态转换时通知（OK→TRIGGERED 或 TRIGGERED→OK），TRIGGERED 持续满足不重复。
- `rearmSeconds > 0`：TRIGGERED 持续满足时，距上次通知超过 `rearmSeconds` 才重发告警通知。
- 抖动提示：`rearmSeconds == 0` 时指标抖动（条件反复满足/不满足）会每次转换发通知，可能高频；运维侧通过合理 cron 间隔与 `rearmSeconds` 控制。

---

## 16. consecutiveEvalCount 语义

**裁定**：`consecutiveEvalCount` 是纯审计计数器，**不影响状态转换**（触发是即时的：OK + 条件满足 → TRIGGERED，无连续门槛）。

- 条件满足：`consecutiveEvalCount += 1`。
- 条件不满足：`consecutiveEvalCount = 0`。
- 用途：运维观察告警持续度，不参与判定逻辑。

---

## 17. 告警通知变体裁定：NotificationSender.sendAlert

**裁定**：在 D5-1 `NotificationSender` 增 `sendAlert(rule, state, currentValue, alertType)` 方法，与 `sendReport` 共享渠道解析/recipients JSON 解析/`IEmailSender` 接线，但**无附件**（告警通知通常是短文本：告警/恢复 + 当前值）。

- 模板键：`rule.templateKey` 未配置时默认 `alert-notify`；按 `NopSysNoticeTemplate.name` 查找，缺失抛 `ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND`。
- 渲染：`StringHelper.renderTemplate` + `NopSysNoticeTemplate.content`；subject 由配置项 `CFG_DATAV_ALERT_DEFAULT_SUBJECT` 渲染（默认 `"Alert: {ruleName}"`）。
- 模板变量：`{ruleName, panelId, state, currentValue, thresholdValue, thresholdValue2, operator, alertType}`。
- 渠道：`email → IEmailSender.sendEmail`（无附件）；`im → IChannelMessageService.sendToUser`（文本，无附件，按 Decision A 分区 + Decision B 渠道聚合，沿用 §1/§10）。

**告警专用错误码自建**（不复用 D5-1 的 `ERR_DATAV_REPORT_*`，避免跨 plan 命名耦合与错误消息文本不匹配）：`ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED` / `ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL` / `ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND`。

**拒「新增独立 AlertNotificationSender 类」的理由**：渠道解析/recipients JSON 解析/`IEmailSender` 接线逻辑与 `sendReport` 同构，复用同一 bean 的不同方法避免重复；方法签名差异（短文本 vs 带附件）经方法重载隔离。

---

## 18. 告警状态独立实体裁定：NopDatavAlertState

**裁定**：`NopDatavAlertState` 一对一关联告警规则（`alertRuleId` 外键且唯一），记录 `state`/`lastEvalTime`/`lastTriggeredTime`/`lastResolvedTime`/`lastNotifiedTime`/`consecutiveEvalCount`/`errorMsg`。

**拒「状态直接挂在规则实体上」的理由**：状态频繁更新（每次 cron 评估都更新 `lastEvalTime`/`consecutiveEvalCount`）与规则配置分离，避免乐观锁冲突；状态可独立审计（运维查询状态不必锁规则配置行）。

**初始化**：规则创建时插入一条 `state = OK`、所有 `last*Time = null`、`consecutiveEvalCount = 0` 的初始状态行。

---

## 19. 面板缺失/查询失败容错

**裁定**：告警规则引用 `panelId`；若面板被删或查询失败，告警状态记 `errorMsg`、`state` 保持（不静默置 OK/TRIGGERED），评估方法吞错返回正常结果（与 D5-1 §9 吞错约定一致）。

- `panelId` 对应的 `NopDatavPanel` 查不到 → 抛 `ERR_DATAV_ALERT_PANEL_NOT_FOUND`（评估方法吞错落库 `errorMsg`，不传播）。
- `PanelDataBinder.queryPanelData` 抛任何异常 → 评估方法吞错落库 `errorMsg`，`state` 保持。
- **无数据行处理**：面板查询返回 0 行 → 视为条件不满足（不触发、不抛错），记 `consecutiveEvalCount = 0`（轻量告警默认：无数据不告警，避免空集误报）。

---

## 20. 调度器分离裁定：NopDatavAlertScheduler 独立 bean

**裁定**：`NopDatavAlertScheduler` 独立于 `NopDatavReportScheduler`（两者均为普通 IoC bean，同模式：可空注入 `IJobScheduler`、`@PostConstruct init()` 注册、配置变更 register/unregister、执行方法吞业务错误返回正常结果对象）。

**拒「告警与报告共用同一调度器 bean」的理由**：关注点分离——告警评估逻辑（取数→聚合→比较→状态机→通知）与报告生成逻辑（取数→文件落盘→带附件通知）生命周期不同；共用 bean 会让 `executeScheduledXxx` 方法签名与 jobParams 键耦合。两者共用 `beanMethod` invoker 范式与 `IJobScheduler` 可空注入约定，但各自有独立的 `jobName` 前缀（`nop-datav-alert-` vs `nop-datav-report-`）与 `BEAN_NAME`/`SCHEDULED_METHOD_NAME` 常量。

### JobSpec 构造

- `jobName` = `nop-datav-alert-` + `alertRuleId`
- `jobGroup` = `nop-datav`
- `jobInvoker` = `beanMethod`
- `jobParams` = `{beanName: "nopDatavAlertScheduler", methodName: "executeScheduledAlert", alertRuleId: <id>}`
- `triggerSpec` = `{cronExpr: <rule.cronExpr>}`

---

## 21. 权限矩阵

沿用 D3/D5-1 看板 `@Auth` + owner 行级 RLS 模式。告警规则属看板 owner 可管理对象（规则经 `panelId → panel.dashboardId → dashboard` 间接归属看板）。

| 操作 | 权限 |
|------|------|
| 告警规则 CRUD（save/update/delete） | 看板 owner/admin |
| enableAlertRule / disableAlertRule / evaluateAlertNow | owner/admin |
| getAlertState | owner/admin |
| cron 触发执行（无用户上下文） | 系统级（scheduler bean 直接调 evaluator，不经 BizProxy/Auth） |

**data-auth**：`nop-datav.data-auth.xml` 增 `NopDatavAlertRule` role-auth（admin 全量；user 限 `createdBy == userName`）。`NopDatavAlertState` 通过 `alertRuleId → alertRule.createdBy` 间接归属（BizModel 层 `requireRuleWithOwnership` 显式校验）。

**action-auth**：`nop-datav.action-auth.xml` 增 `NopDatavAlertRule` / `NopDatavAlertState` 的 query/mutation + 自定义 action（enable/disable/evaluateNow/getState）权限点 + 角色绑定（admin 全量；user 查询 + evaluateNow）。

---

## 22. D5-2 实体契约

### 22.1 NopDatavAlertRule（告警规则）

表 `nop_datav_alert_rule`，看板 owner 可管理对象（经 `panelId → dashboard` 间接归属）。

| 字段 | 类型 | 说明 |
|------|------|------|
| alertRuleId | VARCHAR(32) PK | 告警规则 ID（seq） |
| ruleName | VARCHAR(100) | 规则名 |
| displayName | VARCHAR(200) | 显示名 |
| panelId | VARCHAR(32) | 外键关联面板 |
| valueField | VARCHAR(100) | 面板结果列名 |
| aggregation | VARCHAR(20) | 聚合方式（`datav/alert-aggregation`：none/first/sum/avg/min/max/count） |
| operator | VARCHAR(20) | 比较运算符（`datav/alert-operator`：gt/gte/lt/lte/eq/neq/between） |
| thresholdValue | DECIMAL(20,4) | 阈值主值（必填） |
| thresholdValue2 | DECIMAL(20,4) | 阈值上限（可空，仅 between 用） |
| rearmSeconds | INTEGER | 冷静期秒数（0=仅转换通知） |
| notifyChannels | CLOB JSON | 通知渠道数组 |
| recipients | CLOB JSON | 收件人数组 |
| cronExpr | VARCHAR(100) | crontab 表达式 |
| params | CLOB JSON | 面板查询参数（透传给 PanelDataBinder） |
| templateKey | VARCHAR(100) | 模板键（默认 alert-notify） |
| status | INTEGER | 规则状态（复用 `datav/report-task-status`：0=DISABLED, 10=ENABLED） |
| delFlag / version / createdBy / createTime / updatedBy / updateTime / remark | 标准审计列 | 沿用既有惯例 |

**关系**：`to-one panel` → `NopDatavPanel`（外键 panelId）。
**索引**：`IX_NOP_DATAV_ALERT_RULE_PANEL(panelId)`、`IX_NOP_DATAV_ALERT_RULE_OWNER(createdBy)`、`IX_NOP_DATAV_ALERT_RULE_STATUS(status)`。

### 22.2 NopDatavAlertState（告警状态）

表 `nop_datav_alert_state`，一对一关联规则。

| 字段 | 类型 | 说明 |
|------|------|------|
| alertStateId | VARCHAR(32) PK | 状态 ID（seq） |
| alertRuleId | VARCHAR(32) | 外键关联规则（唯一） |
| state | VARCHAR(20) | 告警状态（`datav/alert-state`：OK/TRIGGERED 两态） |
| lastEvalTime | TIMESTAMP | 最后评估时间 |
| lastTriggeredTime | TIMESTAMP | 最后触发时间 |
| lastResolvedTime | TIMESTAMP | 最后恢复时间 |
| lastNotifiedTime | TIMESTAMP | 最后通知时间 |
| consecutiveEvalCount | INTEGER | 连续评估中条件满足次数（审计） |
| errorMsg | VARCHAR(1000) | 错误信息（面板缺失/查询失败时记录） |
| delFlag / version / createdBy / createTime / updatedBy / updateTime / remark | 标准审计列 | 沿用既有惯例 |

**关系**：`to-one alertRule` → `NopDatavAlertRule`（外键 alertRuleId）。
**索引**：`IX_NOP_DATAV_ALERT_STATE_RULE(alertRuleId)`、`UQ_NOP_DATAV_ALERT_STATE_RULE(alertRuleId, unique=true)`。

### 22.3 dict 定义（新增）

- `datav/alert-operator`（string）：gt/gte/lt/lte/eq/neq/between
- `datav/alert-aggregation`（string）：none/first/sum/avg/min/max/count
- `datav/alert-state`（string）：OK/TRIGGERED（两态——RESOLVED 不入 dict，它是 TRIGGERED→OK 转换时的瞬态恢复通知动作）
- `status` 复用 `datav/report-task-status`（DISABLED=0/ENABLED=10，与报告任务共用）

---

## 23. D5-2 实现组件清单

| 组件 | 职责 |
|------|------|
| `NopDatavAlertRuleBizModel`（`@BizModel`） | CRUD + enable/disable/evaluateAlertNow/getAlertState action，经 `@Auth` + owner RLS |
| `INopDatavAlertRuleBiz`（dao 层接口） | action 签名 |
| `NopDatavAlertScheduler`（普通 bean） | `@PostConstruct init()` 注册 cron job；registerRule/unregisterRule；executeScheduledAlert(Map) 吞业务错误 |
| `AlertEvaluator`（普通 bean） | 取数（PanelDataBinder）→ 聚合 → operator 比较 → 状态机转换 + rearm 判定 → 通知（NotificationSender.sendAlert）→ 更新状态 |
| `NotificationSender.sendAlert`（D5-1 bean 扩展方法） | 渲染告警模板 → IEmailSender.sendEmail 无附件；IM → IChannelMessageService.sendToUser 文本无附件（Decision A/B） |
| `NopDatavConfigs`（增配置项） | `CFG_DATAV_ALERT_DEFAULT_REARM_SECONDS`(0) / `CFG_DATAV_ALERT_EVAL_MAX_ROWS`(1000) / `CFG_DATAV_ALERT_DEFAULT_SUBJECT`("Alert: {ruleName}") |
| `NopDatavErrors`（增错误码） | `ERR_DATAV_ALERT_RULE_NOT_FOUND` / `ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND` / `ERR_DATAV_ALERT_VALUE_NOT_NUMERIC` / `ERR_DATAV_ALERT_INVALID_THRESHOLD` / `ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND` / `ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED` / `ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL` / `ERR_DATAV_ALERT_PANEL_NOT_FOUND` / `ERR_DATAV_ALERT_NOT_OWNER` / `ERR_DATAV_ALERT_CHANNEL_SERVICE_NOT_CONFIGURED`（IM service 未注入）/ `ERR_DATAV_ALERT_ALL_NOTIFY_FAILED`（全渠道零成功） |

**职责分离**：`NopDatavAlertScheduler` 只负责 cron job 注册/注销与吞错入口；`AlertEvaluator` 只负责评估逻辑（取数→聚合→比较→状态机→通知→更新状态）；两者不混入对方职责（镜像 D5-1 `NopDatavReportScheduler` / `ReportDeliveryExecutor` 分离）。

---

## 24. D5-2 被拒方案汇总

| 方案 | 拒用理由 | 分类 |
|------|----------|------|
| 状态机三态（OK/TRIGGERED/RESOLVED）持久化 | RESOLVED 是 TRIGGERED→OK 转换时的瞬态恢复通知动作，持久化为独立状态会引入状态机复杂度（三态转换表）与语义模糊（RESOLVED 何时回到 OK） | follow-up（无明确用例） |
| 多行整体判定（任一满足/全部满足/计数） | 需定义多行→单布尔聚合语义，scope 过宽；首版单标量聚合满足轻量告警核心 | follow-up |
| 单 VARCHAR 字段存 JSON `{min,max}` 阈值 | 不可查询、类型校验复杂；DECIMAL 双列结构化存储便于 SQL 直查与类型校验 | follow-up |
| 状态直接挂在规则实体上 | 状态频繁更新与规则配置分离，避免乐观锁冲突；状态可独立审计 | follow-up |
| 告警与报告共用同一调度器 bean | 关注点分离——告警评估与报告生成生命周期不同；共用会让方法签名与 jobParams 键耦合 | follow-up |
| 新增独立 AlertNotificationSender 类 | 渠道解析/recipients JSON 解析/IEmailSender 接线逻辑与 sendReport 同构，复用同一 bean 不同方法避免重复 | follow-up |
| 多面板组合告警条件（AND/OR 跨面板） | 需定义组合语义与求值顺序，scope 过宽；首版单面板单阈值 | follow-up |
| 每次评估历史持久化 | 状态实体已含 `lastEvalTime`/`consecutiveEvalCount` 供审计；每次评估写历史行属过度设计 | optimization candidate |
| 多级阈值/告警抑制/合并/升级 | 高级告警能力，当前无用例 | optimization candidate |
| JDBC queryTimeout 超时配置 | 首版用 `CFG_DATAV_ALERT_EVAL_MAX_ROWS` 行数安全上限防 OOM；精确聚合应让 SQL 预聚合返回单行 | follow-up |

---

## 25. stuck-task 周期恢复（交付 + 导出）

**裁定**：新增 `NopDatavStuckTaskScanner` 普通 IoC bean（`ioc:default=true`），`@Inject @Nullable IJobScheduler` + `@PostConstruct` 注册固定间隔 job（`TriggerSpec.setRepeatInterval`，间隔 `nop.datav.stuck-scan.interval-minutes` 默认 10 min，jobName=`nop-datav-stuck-task-scan`，beanMethod invoker），周期调用 `scanStuck()`——读 `nop.datav.stuck-scan.timeout-minutes`（默认 60 min）并委托 `NopDatavReportDeliveryRecovery.scanStuck(int)` 与 `NopDatavExportTaskRecovery.scanStuck(int)`，仅标记 status ∈ {PENDING, RUNNING} 且时间基准超过阈值的记录为 FAILED（reason=`stuck beyond timeout threshold (Xm)`）。配置开关 `nop.datav.stuck-scan.enabled`（默认 true）。

**解决的 gap**：throwable-sweep（plan `2026-08-14-1452-1`）把交付/导出执行体的 `catch (Throwable)` 收窄为 `catch (Exception)` 后，JVM Error（OOM/StackOverflow）传播出 `GlobalExecutors.globalWorker()` worker 线程致其死亡，但进程存活（线程池可建新线程）——该记录停留在 RUNNING/PENDING 直到下次进程重启才被 `@PostConstruct` 全量恢复。周期扫描把 stuck 窗口从「直到重启」（可达数天）收敛到有界时间窗口（阈值 + 间隔，最坏 ≈ timeout + interval）。

**时间基准字段裁定：交付用 `startTime`、导出用 `createTime`**（实体时间字段不一致）：
- `NopDatavReportDelivery` 有 `startTime`（执行开始时刻，PENDING 插入时即写入）——stuck 判定精确。
- `NopDatavExportTask` 无 startTime，只有 `createTime`（提交时刻；ORM 审计 insert 时强制填入）。提交后经 globalWorker 异步延迟执行，故 createTime = 排队等待 + 执行耗时。并发上限（max-concurrent-per-user=3）下若排队超过阈值，一条刚开始执行的导出可能被误标——保守高默认阈值（60 min）+ 低并发上限使该场景极罕见；导出量大时为导出单独调高阈值或后续补 startTime 列。
- 两者均不用 `updateTime`：每次状态转换（touchDelivery/touchUpdate）都会刷新，任何无关写重置时钟，对 stuck 判定不可靠。

**默认值裁定**：timeout=60 min（保守高值：正常报告生成/导出分钟级完成，60 min 内未终态极大概率是 stuck；同时抑制慢执行假阳性与 createTime 含排队等待的误标）、interval=10 min（stuck 最坏暴露窗口 ≈ 70 min，远优于「直到重启」；扫描为两条索引查询 + 少量更新，10 min 周期无负载顾虑）。

**与重启恢复的关系（正交，不变式）**：重启 `@PostConstruct` 路径（`recoverInterruptedDeliveries()`/`recoverInterruptedTasks()`）**保持无阈值全量清理**——重启时所有非终态记录的执行体必然已丢失，全量标 FAILED 是精确的；周期 `scanStuck(int)` 是带阈值的运行时增量路径，两者是同 bean 内并存的独立方法，不合并、不替换。

**与 Dim14-01 per-request 拒绝的关系**：Dim14-01 拒绝的是「请求路径无阈值全量恢复」（会把所有用户/同用户的在途任务误标 FAILED）。周期扫描以时间阈值区分「真正 stuck」与「正常在途」，不受该裁定约束；阈值内记录不动是 ①–④ 边界测试的硬契约。

**与 deferred retry plan 的正交性**：retry plan（`2026-08-14-1510-2`，当前 blocked）处理 FAILED 终态记录的重新投递；本节处理 stuck 非终态记录的终态化。两者组合语义：stuck →（扫描）FAILED →（未来 retry）重投。

**慢执行竞态裁定（良性，已接受）**：若记录合法执行超过阈值（如超大看板导出），扫描在阈值到达时标 FAILED，但 worker 线程仍在运行；worker 最终完成写 SUCCEEDED 时覆盖扫描的 FAILED——交付场景这是**正确终态**（报告确实送达，文件已完整生成），扫描的 FAILED 是被 worker 修正的假阳性。审计轨迹丢失该次扫描干预记录，但终态正确。不做 worker 写前状态校验（sticky-FAILED）：仅在 retry plan 落地后（FAILED 触发重试 → 可能重复送达）才成为必要，届时作为该 plan 的前置项处理（watch-only residual）。

**被拒替代方案**：

| 方案 | 拒用理由 | 分类 |
|------|----------|------|
| per-request 恢复（无阈值） | Dim14-01 已拒绝：误杀所有在途记录 | 已拒（audit 裁定） |
| stuck 记录自动重投（FAILED → re-queue） | 依赖 retry 能力（deferred plan `2026-08-14-1510-2`）或手动；与重启恢复语义一致仅标 FAILED | deferred（successor: retry plan） |
| sticky-FAILED worker 写前校验 | 仅 retry 落地后必要（防重复送达）；当前竞态良性（终态正确） | watch-only residual |
| 告警记录纳入 stuck 扫描 | `NopDatavAlertState` 每次 cron tick 同步即时写回，无独立长期 RUNNING 执行体记录，不适用 | 已拒（不适用） |
| 由 interval-minutes 合成 cron 表达式触发 | `TriggerSpec.setRepeatInterval` 固定间隔已满足需求且更简单；cron 表达式合成引入解析/校验复杂度无收益 | 已拒（简化） |

**FAILED-brick 规避**：`scanStuck()` 吞业务错误返回正常结果 Map（单类扫描异常 → WARN 日志 → 继续另一类），仅 JVM Error 传播——与 §3/§9 `executeScheduledReport` 约定一致，防 LocalJobScheduler 将周期 job 永久置 FAILED。scheduler==null（宿主未注册调度器）时 INFO 日志跳过注册，重启恢复路径不受影响。

## 26. 删除联动停用与即时注销

> 来源：plan `ai-dev/plans/nop-datav/2026-08-14-2020-1-dashboard-screen-delete-cascade-lifecycle.md`（D3 裁定 + Gap #3 修复结论落地）。

### 调度消费者停用语义裁定（D3）

删除看板（连带面板）/ 删除面板时，关联调度消费者处置为「**双动作**」：

1. **置 `status=DISABLED`**（dict `datav/report-task-status` 既有值 `DISABLED=0`，AlertRule.status 复用同一 dict，无 ORM 变更）——持久化停用，重启后 init scanner 只装载 `status=ENABLED`，自然不再注册。
2. **即时注销**：复用既有运行时增量 API `NopDatavReportScheduler.unregisterTask(reportTaskId)` / `NopDatavAlertScheduler.unregisterRule(alertRuleId)`（内部即 `scheduler.removeJob(jobName(id))`），使停用**即时生效**（无需重启）。

关联定位规则：ReportTask 按 `dashboardId` 直接定位；AlertRule 经 `panel.dashboardId`（删看板）或 `panelId`（删面板）定位。

### 事务边界与注销失败处理

级联在 biz 层 `delete(id)` 的事务内执行（挂接机制裁定见 `permission-sharing-design.md`「D5 级联挂接机制裁定」）：

- **执行顺序**：先持久化 `status=DISABLED`（参与 ORM session/事务），再调 `unregister*`（非事务性内存操作）。
- **注销失败 → 回滚整个删除**：`removeJob` 抛错时异常向上传播，删除事务回滚（fail-fast，无静默跳过）。不允许「删了主表但 job 还在触发」的中间态。
- **反方向不对称是安全的**：若 DB 停用落库后、注销前进程崩溃，残留的内存 job 至多触发到 `requirePublishableDashboard` 失败（报告，`LAST_RUN_ERROR` 噪音）或 AlertEvaluator 容错路径（面板缺失记 errorMsg），且进程重启后 scanner 只装载 ENABLED，残留 job 自愈。若注销后、事务提交前崩溃，job 已不在内存，行仍是 ENABLED——下次 save/enable 或重启 scanner 会重新注册，无丢失执行。
- `scheduler == null`（宿主未注册 IJobScheduler）时注销为 no-op（既有 `unregister*` 行为），停用语义仍完整成立。

### Gap #3 修复结论：删除规则/任务的标准 delete 路径即时注销

历史缺陷：`NopDatavAlertRuleBizModel`/`NopDatavReportTaskBizModel` 仅覆写 3 参 `afterEntityChange(entity, action, context)`，而标准 `delete(id)` 路径只调 2 参 deprecated `afterEntityChange(entity, context)`（`CrudBizModel.java:1211`，默认空实现）——删除规则/任务**不触发注销**，cron job 进程内残留触发直至重启，与类 javadoc「delete 调 unregister*」声明不符。

修复结论：两个 BizModel 覆写 4 参 `doDeleteEntity`，在 `super` 之后调用对应 `unregister*`；save/update 路径的注册联动（3 参 `afterEntityChange`）保持不变。javadoc 同步修正为实际行为。回归契约：直接 `delete(id)` 后调度器注册表（`getRegisteredJobNames()`）不含该 job。

### 保留与不级联对象

- 删除 ReportTask：`NopDatavReportDelivery` 交付历史**保留**（一次性执行历史记录，类比 `NopDatavExportTask`，见 plan Non-Goals）。
- 删除 AlertRule：`NopDatavAlertState` 生命周期跟随规则存续（规则被直接删除时状态行保留为历史记录；看板/面板级联路径只停用不删规则，状态行自然保留）。
