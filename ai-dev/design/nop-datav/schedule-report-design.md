# nop-datav 定时报告与告警设计 (D5)

> Status: **final** — D5-1（定时报告）+ D5-2（轻量告警）最终结论。
> Last Updated: 2026-08-10
> Owner plans: `ai-dev/plans/nop-datav/2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`、`ai-dev/plans/nop-datav/2026-08-10-1230-2-lightweight-alert-threshold-rearm-notification.md`

本文档为 D5-1 定时报告 + D5-2 轻量告警的设计契约（最终结论，非 Proposed vs Current 形式）。D5-1 章节覆盖通知 provider 裁定、渲染时机、调度集成、实体契约、grace 语义、权限矩阵、模板渲染机制、取数契约、被拒方案及理由；D5-2 章节覆盖告警标量聚合、状态机、rearm、告警通知变体、告警状态独立实体、面板容错、权限矩阵。

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
| `ReportDeliveryExecutor`（普通 bean） | 插 pending 交付记录 → 异步执行（GlobalExecutors）→ session 内（runInNewSession）：取数（PanelDataExporter.exportDashboard）+ 文件落盘（IFileStore）+ 交付记录 SUCCEEDED 提交 → session 关闭后通知送达（NotificationSender，SMTP 不持 JDBC 连接，Dim14-02）→ 成功回写 deliveredChannels / 失败回补 FAILED |
| `NotificationSender`（普通 bean） | 渲染模板（StringHelper.renderTemplate + NopSysNoticeTemplate）→ 按 notifyChannels 分发（email → IEmailSender.sendEmail 带附件；im → UnsupportedOperationException） |
| `NopDatavReportDeliveryRecovery`（普通 bean） | `@PostConstruct` 幂等扫描 stale running 交付记录 → failed（reason=interrupted by process restart），镜像 `NopDatavExportTaskRecovery` |
| `NopDatavConfigs`（增配置项） | `CFG_DATAV_REPORT_DEFAULT_GRACE_MINUTES`(60) / `CFG_DATAV_REPORT_MAX_ROWS`(100000) / `CFG_DATAV_REPORT_DEFAULT_SENDER`("") / `CFG_DATAV_REPORT_DEFAULT_SUBJECT`("Report: {reportName}") |
| `NopDatavErrors`（增错误码） | `ERR_DATAV_REPORT_TASK_NOT_FOUND` / `ERR_DATAV_REPORT_CRON_INVALID` / `ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD` / `ERR_DATAV_REPORT_DELIVERY_FAILED` / `ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL` / `ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED` / `ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND` / `ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER` |

**职责分离**：`NopDatavReportScheduler.init()` 只负责重注册 cron job；`NopDatavReportDeliveryRecovery.init()` 只负责清理 stale running 交付记录；二者不混入对方职责（镜像 `NopDatavExportTaskRecovery` 独立于 BizModel 的模式）。

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

| 当前态 | 条件 | 目标态 | 通知动作 | 时间戳更新 |
|--------|------|--------|----------|-----------|
| OK | 条件满足 | TRIGGERED | 发告警通知（trigger） | `lastTriggeredTime = now`，`lastNotifiedTime = now` |
| TRIGGERED | 条件不再满足 | OK | 发恢复通知（recover） | `lastResolvedTime = now` |
| TRIGGERED | 条件持续 + `rearmSeconds > 0` + `now - lastNotifiedTime >= rearmSeconds` | TRIGGERED | 重发告警通知（trigger，rearm） | `lastNotifiedTime = now` |
| TRIGGERED | 条件持续 + `rearmSeconds == 0` | TRIGGERED | 不重复 | （无） |
| OK | 条件不满足 | OK | 不通知 | （无） |

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
- 渠道：`email → IEmailSender.sendEmail`（无附件）；`im → UnsupportedOperationException`（非静默，沿用 D5-1 §1/§10）。

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
| `NotificationSender.sendAlert`（D5-1 bean 扩展方法） | 渲染告警模板 → IEmailSender.sendEmail 无附件；IM → UnsupportedOperationException |
| `NopDatavConfigs`（增配置项） | `CFG_DATAV_ALERT_DEFAULT_REARM_SECONDS`(0) / `CFG_DATAV_ALERT_EVAL_MAX_ROWS`(1000) / `CFG_DATAV_ALERT_DEFAULT_SUBJECT`("Alert: {ruleName}") |
| `NopDatavErrors`（增错误码） | `ERR_DATAV_ALERT_RULE_NOT_FOUND` / `ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND` / `ERR_DATAV_ALERT_VALUE_NOT_NUMERIC` / `ERR_DATAV_ALERT_INVALID_THRESHOLD` / `ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND` / `ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED` / `ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL` / `ERR_DATAV_ALERT_PANEL_NOT_FOUND` / `ERR_DATAV_ALERT_NOT_OWNER` |

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
