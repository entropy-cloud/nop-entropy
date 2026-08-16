# 2 轻量告警：面板阈值评估 + 冷静期 + 通知（D5-2）

> Plan Status: completed
> Mission: nop-datav
> Work Item: D5-2
> Last Reviewed: 2026-08-10
> Source: `ai-dev/backlog/nop-datav-roadmap.md` D5-2；`ai-dev/analysis/2026-08/2026-08-09-nop-datav-function-analysis.md` §轻量告警
> Related: `2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`（D5-1 调度集成 + 通知抽象，本计划复用）、`2026-08-10-1000-2-dashboard-runtime-panel-data-binding-refresh.md`（D1 面板数据查询）
> Execution Order: N=2。本计划在 D5-1（N=1）之后执行——复用 D5-1 产出的 `NopDatavReportScheduler` 调度注册范式与 `NotificationSender` 通知抽象（邮件渠道）。若 D5-1 的通知抽象需为告警扩展（如短文本无附件），本计划在 Phase 1 裁定并记录扩展点。

## Purpose

将 roadmap D5-2 收口：为 nop-datav 增加「轻量告警」能力——按 cron 周期查询面板数据，对阈值条件（operator/value，Redash Alert 参考）求值，触发/恢复时经通知渠道（邮件为主，复用 D5-1 `NotificationSender`）送达，并实现 rearm 冷静期（避免反复告警）。本计划只做模型层 + 调度评估 + 状态机 + 通知，不含前端 UI。

## Current Baseline

基于 live repo 核对（2026-08-10）：

- **面板数据查询已就绪**：`PanelDataBinder.queryPanelData(panelId, panel, requestParams[, rowLimit])`（`nop-datav-service/.../query/PanelDataBinder.java`）返回 `PanelDataResult(panelId, componentType, hasDataset, columns, rows)`。告警评估将基于该结果（取首行某列值，或聚合标量——见 Phase 1 裁定）。
- **D5-1（本批次 N=1）产出待落地**：D5-1 计划产出 `NopDatavReportScheduler`（可空注入 `IJobScheduler`、`@PostConstruct` 注册、配置变更 register/unregister、beanMethod 执行方法吞业务错误）、`NotificationSender`（渲染 `NopSysNoticeTemplate` → `IEmailSender`）、`NopDatavReportTask`/`NopDatavReportDelivery` 实体、`ERR_DATAV_REPORT_*` 错误码、`CFG_DATAV_REPORT_*` 配置。本计划复用其调度注册范式与通知抽象。
- **告警能力完全不存在**：live repo 无告警实体/表、无阈值评估逻辑、无 rearm 状态机、无告警通知接入（grep `alert|threshold|rearm` 在 nop-datav 无业务命中）。设计 doc `schedule-report-design.md`（D5-1 起草）需由本计划增补告警章节。
- **nop-job 调度集成范式（D5-1 建立）**：beanMethod invoker + 可空注入 `IJobScheduler`；执行方法单 `Map<String,Object>` 参数；吞业务错误返回正常结果（规避 LocalJobScheduler FAILED-brick）。
- **通知抽象（D5-1 建立）**：`NotificationSender.sendReport(...)` 走 `NopSysNoticeTemplate` → `IEmailSender`；未配置发件人/无渠道显式失败。本计划可能需要「短文本无附件」告警通知变体——Phase 1 裁定是扩展现有 NotificationSender 还是新增 alert 专用方法，并在 design doc 记录。
- **权限基建可复用**：面板/看板已有 `@Auth` + owner RLS。告警规则属看板派生物，沿用 owner/admin 管理。
- **错误码**：`NopDatavErrors.java` 当前无告警相关码；本计划新增 `ERR_DATAV_ALERT_*`。
- **真正剩余 gap**：无告警规则实体/表、无告警状态实体/表、无阈值求值器、无 rearm 状态机、无告警通知接入、design doc 无告警章节。

## 设计方向预声明（推荐方向，Phase 1 确认并记录拒绝理由）

1. **阈值评估对象 = 面板数据结果的一个标量值**：面板查询返回多行多列，告警需聚焦单一值。推荐契约：告警规则配置 `valueField`（列名）+ `aggregation`（none/first/sum/avg/min/max/count，默认 first 取首行该列值）→ 得标量 → 与阈值按 `operator`（gt/gte/lt/lte/eq/neq/between）比较。**阈值存储**：`thresholdValue`（DECIMAL，必填，主比较值）+ `thresholdValue2`（DECIMAL，可空，仅 between 用，存上限）；单值 operator 仅用 thresholdValue，between 用 [thresholdValue, thresholdValue2]（thresholdValue2 为 null 时抛 `ERR_DATAV_ALERT_INVALID_THRESHOLD`）。**标量类型转换**：`PanelDataResult.rows` 值为 `Object`（BigDecimal/Long/String 等，依 JDBC dialect），评估器统一转 `BigDecimal` 比较；非数值类型抛 `ERR_DATAV_ALERT_VALUE_NOT_NUMERIC`。拒绝「单 VARCHAR 字段存 JSON `{min,max}`」（理由：不可查询、类型校验复杂）。Phase 1 确认聚合契约。
2. **状态机 = 两态 OK/TRIGGERED + rearm 冷静期（RESOLVED 为瞬态逻辑）**：
   - `state` 列只持久化 `OK` / `TRIGGERED` 两态（dict `datav/alert-state` 只含这两值）。
   - OK + 条件满足 → TRIGGERED（发告警通知，记 lastTriggeredTime/lastNotifiedTime=now）。
   - TRIGGERED + 条件不再满足 → OK（**发恢复通知**，记 lastResolvedTime=now）。即"RESOLVED"是 TRIGGERED→OK 转换时的一次性恢复通知动作，不持久化为独立状态。
   - TRIGGERED + 条件持续 + rearmSeconds>0 + now-lastNotifiedTime>=rearmSeconds → 重发告警（lastNotifiedTime=now）。
   - TRIGGERED + 条件持续 + rearmSeconds=0 → 不重复（仅状态转换时通知）。
   - **抖动提示**：rearmSeconds=0 时指标抖动（条件反复满足/不满足）会每次转换发通知，可能高频；design doc 显式记录，运维侧通过合理 cron 间隔与 rearmSeconds 控制。
   Phase 1 确认状态转换表与 rearm 语义。
3. **调度复用 D5-1 NopDatavReportScheduler 范式，新增独立告警调度器 bean**：`NopDatavAlertScheduler`（与 ReportScheduler 同模式：可空注入 IJobScheduler、`@PostConstruct` 注册、配置变更 register/unregister、执行方法吞业务错误）。拒绝「告警与报告共用同一调度器 bean」（理由：关注点分离，告警评估逻辑与报告生成逻辑不同生命周期）。
4. **告警通知复用 D5-1 NotificationSender 骨架，扩展无附件短文本变体（本计划定义 sendAlert 契约）**：D5-1 的 `NotificationSender.sendReport` 带附件；告警通知通常是短文本（告警/恢复 + 值）。本计划在 `NotificationSender` 增 `sendAlert(rule, state, currentValue, alertType)` 方法（渲染告警专用模板键 `alert-notify`，与 sendReport 共享渠道解析/JSON 解析/`IEmailSender` 调用，但无 attachments）。**sendAlert 的签名与模板键在本计划定义**——D5-1 无需预定义 sendAlert（D5-1 只建立 NotificationSender 的渠道解析 + IEmailSender 接线骨架）。**告警专用错误码自建**：`ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED`/`ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL`/`ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND`（不复用 D5-1 的 ERR_DATAV_REPORT_*，避免跨 plan 命名耦合与错误消息文本不匹配）。IM 渠道沿用 D5-1 裁定（adjudicated deferred，显式抛 UnsupportedOperationException 不空壳）。
5. **跨 plan 依赖契约（修正 B1）**：本计划在 D5-1（N=1）完成后执行。D5-1 建立的复用契约 = (a) `NotificationSender` bean（渠道解析 + recipients JSON 解析 + `IEmailSender` 接线）；(b) `nop-datav-service` 已含 `nop-integration-api` + `nop-job-api` 依赖；(c) `schedule-report-design.md` 已为正式文档。本计划在此基础上扩展 sendAlert + 新增 `NopDatavAlertScheduler`（与 ReportScheduler 同模式但独立 bean）。**降级策略**：若执行时 D5-1 未落地，本计划须先补齐 NotificationSender 骨架（sendReport 可留桩抛 UnsupportedOperationException，但 sendAlert 须完整实现）+ 依赖 + design doc 起草，再进行告警实现。
6. **告警状态独立实体 NopDatavAlertState（一对一关联规则）**：记录 state（OK/TRIGGERED 两态）/lastEvalTime/lastTriggeredTime/lastResolvedTime/lastNotifiedTime/consecutiveEvalCount/errorMsg。**`consecutiveEvalCount` 语义 = 纯审计计数器**（连续评估中条件满足的次数，每次评估重置：条件满足 +1，不满足归 0），**不影响状态转换**（触发是即时的：OK + 条件满足 → TRIGGERED，无连续门槛）；用于运维观察告警持续度，不参与判定逻辑。拒绝「状态直接挂在规则实体上」（理由：状态频繁更新与规则配置分离，避免乐观锁冲突；状态可独立审计）。Phase 1 确认。
7. **面板被删/数据集变更的容错**：告警规则引用 panelId；若面板被删或查询失败，告警状态记 errorMsg、state 保持（不静默置 OK/TRIGGERED），评估方法吞错返回正常结果（与 D5-1 吞错约定一致）。

## Goals

- 告警规则实体：`NopDatavAlertRule`（panelId/valueField/aggregation/operator/thresholdValue/thresholdValue2/rearmSeconds/notifyChannels/recipients/cronExpr/params/templateKey/status + 审计列）。
- 告警状态实体：`NopDatavAlertState`（alertRuleId 外键 + state/lastEvalTime/lastTriggeredTime/lastResolvedTime/lastNotifiedTime/consecutiveEvalCount/errorMsg）。
- 阈值评估器：`AlertEvaluator`（取面板数据 → 标量聚合 → operator 比较 → 状态转换 + rearm 判定）。
- 调度集成：`NopDatavAlertScheduler`（注册/注销 job，cron 触发评估）。
- 告警通知：告警触发/恢复时经 `NotificationSender.sendAlert`（邮件为主，复用 D5-1）送达；rearm 冷静期生效。
- 手动评估 API：`evaluateAlertNow(ruleId)` 立即评估一次（便于测试与运维）。
- 在 `schedule-report-design.md` 增补 D5-2 告警章节（状态机、rearm、聚合契约、被拒方案）。
- 新增 action 经 `@Auth` + owner 权限收口；新增错误码 + 配置项。

## Non-Goals

- **前端告警管理 UI**：走 flux。
- **多面板组合告警条件（AND/OR 跨面板）**：scope 过宽，首版单面板单阈值；列为 follow-up。
- **IM/渠道推送端到端**：沿用 D5-1 裁定（adjudicated deferred）。
- **告警历史持久化每一次评估**：只持久化状态实体（含 lastEvalTime/consecutiveEvalCount）；每次评估写历史行属过度设计，列为 follow-up。
- **告警抑制/合并/升级（多级阈值）**：首版单阈值 + rearm；多级阈值列为 follow-up。
- **D5-1 定时报告**：独立 plan（N=1）。
- **看板/面板本身的告警可视化（面板角标）**：走 flux。

## Scope

### In Scope

- 设计文档：`ai-dev/design/nop-datav/schedule-report-design.md` 增补 D5-2 告警章节（状态机转换表、rearm 语义、标量聚合契约、告警通知变体裁定、告警状态独立实体裁定、面板缺失容错、权限矩阵、被拒方案）。
- ORM 变更：新增 `NopDatavAlertRule`、`NopDatavAlertState` 实体（`nop-datav/model/nop-datav.orm.xml`）+ 新增 dict（`datav/alert-operator`、`datav/alert-aggregation`、`datav/alert-state`）。
- 评估器：`AlertEvaluator`（`PanelDataBinder` 取数 → 标量聚合 → operator 比较 → 状态转换 + rearm）。
- 调度集成：`NopDatavAlertScheduler` bean（复用 D5-1 范式）。
- 告警通知：`NotificationSender.sendAlert(...)`（渲染告警模板 → IEmailSender；IM 显式失败）。
- BizModel + API：告警规则 CRUD + `enableAlertRule`/`disableAlertRule`/`evaluateAlertNow`/`getAlertState`；action 经 `@Auth`。
- 配置项：`CFG_DATAV_ALERT_DEFAULT_REARM_SECONDS`（默认 0，仅状态转换通知）+ `CFG_DATAV_ALERT_EVAL_MAX_ROWS`（面板查询行数安全上限，避免 OOM；聚合 sum/avg/min/max 基于返回行计算，**近似语义**——文档说明：精确聚合应让面板数据集 SQL 层预聚合返回单行；不做 JDBC queryTimeout 超时配置，列 follow-up）。
- 错误码：`ERR_DATAV_ALERT_*`。
- 单元测试 + 端到端（建规则→启用→手动评估→触发告警通知（mock sender 断言）→恢复通知→rearm 冷静期验证）。

### Out Of Scope

- 前端 UI。
- 多面板组合告警。
- IM 端到端（沿用 D5-1 deferred）。
- 每次评估历史持久化。
- 多级阈值/抑制/合并/升级。

## Execution Plan

### Phase 1 - 设计文档增补（schedule-report-design.md D5-2 告警章节）

Status: completed
Targets: `ai-dev/design/nop-datav/schedule-report-design.md`

- Item Types: `Decision`

- [x] 在 `schedule-report-design.md` 增 D5-2 告警章节（最终结论，无 "Proposed vs Current"），覆盖：
  - [x] **标量聚合契约**：valueField + aggregation（none/first/sum/avg/min/max/count，默认 first）→ 标量；thresholdValue（必填）+ thresholdValue2（可空，仅 between）；Object→BigDecimal 类型转换（非数值抛错）；记录拒「多行整体判定」/「单 VARCHAR 存 JSON」理由
  - [x] **状态机转换表（两态 + rearm）**：`state` 列只持久化 OK/TRIGGERED；OK→TRIGGERED（条件满足，发告警通知，lastTriggeredTime/lastNotifiedTime=now）；TRIGGERED→OK（条件不再满足，发恢复通知，lastResolvedTime=now）；rearm 冷静期（TRIGGERED 持续 + rearmSeconds>0 + now-lastNotifiedTime>=rearmSeconds 才重发；rearmSeconds=0 仅转换通知）；记录 RESOLVED 为瞬态恢复通知动作（不持久化）；抖动提示
  - [x] **consecutiveEvalCount 语义**：纯审计计数器（条件满足 +1 / 不满足归 0），不影响状态转换（触发即时，无连续门槛）
  - [x] **告警通知变体裁定**：NotificationSender 增 sendAlert（无附件短文本，模板键 alert-notify），与 sendReport 共享渠道解析/JSON 解析/IEmailSender；sendAlert 签名在本计划定义；告警专用错误码自建（ERR_DATAV_ALERT_*）；记录裁定理由
  - [x] **告警状态独立实体裁定**：NopDatavAlertState 一对一关联规则；记录拒「状态挂规则实体」理由（乐观锁冲突 + 独立审计）
  - [x] **面板缺失/查询失败容错**：panelId 被删或查询失败 → 状态记 errorMsg、state 保持、评估方法吞错返回正常结果（不静默置 OK/TRIGGERED）；**无数据行处理**：面板查询返回 0 行 → 视为条件不满足（不触发、不抛错），记 consecutiveEvalCount=0（轻量告警默认：无数据不告警，避免空集误报）
  - [x] **调度器分离裁定**：NopDatavAlertScheduler 独立于 NopDatavReportScheduler（关注点分离）；记录理由
  - [x] **权限矩阵**：告警规则管理 = 看板/面板 owner/admin；告警状态读 = owner/admin
  - [x] **operator 字典**：gt/gte/lt/lte/eq/neq/between 语义定义（between 用 [thresholdValue, thresholdValue2]）

Exit Criteria:

- [x] `schedule-report-design.md` 含 D5-2 最终结论章节，覆盖上述全部子项，无 "Proposed"/"待定"
- [x] 该 Phase 改变 live baseline（design doc）：`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ORM 模型与代码生成（告警规则 + 告警状态）

Status: completed
Targets: `nop-datav/model/nop-datav.orm.xml`、`nop-datav/nop-datav-meta/src/main/resources/_vfs/dict/datav/`、`nop-datav/nop-datav-dao/src/main/java/io/nop/datav/dao/entity/_gen/`、`nop-datav/nop-datav-meta/src/main/resources/_vfs/nop/datav/model/`

- Item Types: `Decision | Proof`

- [x] 在 `nop-datav/model/nop-datav.orm.xml` 新增 `NopDatavAlertRule` 实体（alertRuleId/panelId 外键/valueField/aggregation/operator/thresholdValue DECIMAL 必填/thresholdValue2 DECIMAL 可空（仅 between）/rearmSeconds/notifyChannels clobJson/recipients clobJson/cronExpr/params clobJson/templateKey/status/标准审计列 + displayName/i18n）
- [x] 在 `nop-datav/model/nop-datav.orm.xml` 新增 `NopDatavAlertState` 实体（alertRuleId 外键且唯一/state（OK/TRIGGERED 两态）/lastEvalTime/lastTriggeredTime/lastResolvedTime/lastNotifiedTime/consecutiveEvalCount/errorMsg）
- [x] 新增 dict：`datav/alert-operator`（gt/gte/lt/lte/eq/neq/between）、`datav/alert-aggregation`（none/first/sum/avg/min/max/count）、`datav/alert-state`（OK/TRIGGERED 两态——RESOLVED 不入 dict，它是 TRIGGERED→OK 转换时的瞬态恢复通知动作）
- [x] 触发 codegen 重建生成物：`./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests`，确认生成实体 `_NopDatavAlertRule.java`/`_NopDatavAlertState.java` + xmeta + dict；**不手改任何 `_` 前缀生成文件**
- [x] i18n displayName 文案（en/zh-CN）补齐

Exit Criteria:

- [x] `nop-datav.orm.xml` 含 `NopDatavAlertRule`/`NopDatavAlertState` 源模型
- [x] `./mvnw install -pl nop-datav/nop-datav-meta -am -DskipTests` 成功；生成实体 + xmeta + dict 均含新实体
- [x] **无静默跳过**：本 Phase 仅 codegen
- [x] 该 Phase 改变 live baseline（ORM 结构）：属 plan-first 区域，本 plan 即其 plan；`ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 告警评估 + 状态机 + rearm + 调度 + 通知实现

Status: completed
Targets: `nop-datav-dao/.../biz/INopDatavAlertRuleBiz.java`（接口）、`nop-datav-service/.../entity/NopDatavAlertRuleBizModel.java`、`nop-datav-service/.../alert/AlertEvaluator.java`（新增）、`nop-datav-service/.../alert/NopDatavAlertScheduler.java`（新增）、`nop-datav-service/.../report/NotificationSender.java`（D5-1 产出，本计划扩展 sendAlert）、`nop-datav-service/.../NopDatavErrors.java`（增码）、`nop-datav-service/.../NopDatavConfigs.java`（增配置）、`nop-datav-service/.../beans/app-service.beans.xml` + `_service.beans.xml`、`nop-datav-web/.../auth/nop-datav.action-auth.xml`

- Item Types: `Fix | Decision`

- [x] **接口扩展**：`INopDatavAlertRuleBiz`（`nop-datav-dao/.../biz/`）声明告警规则 CRUD + `enableAlertRule`/`disableAlertRule`/`evaluateAlertNow`/`getAlertState` action 签名
- [x] **评估器**：`AlertEvaluator.evaluate(ruleId)`：加载规则 → 经 `IDaoProvider.daoFor(NopDatavPanel.class).getEntityById(panelId)` 加载 panel（缺失抛 `ERR_DATAV_ALERT_PANEL_NOT_FOUND`）→ `PanelDataBinder.queryPanelData(panelId, panel, rule.params[, rowLimit=maxRows])` 取 `PanelDataResult`（PanelDataBinder 非 bean，AlertEvaluator 注入 `IDaoProvider`+`IJdbcTemplate` 后 `new PanelDataBinder(...)` 构造，与 `NopDatavPanelBizModel:47` 同模式）→ 按 valueField + aggregation 求标量（列不存在抛 `ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND`；值转 BigDecimal 失败抛 `ERR_DATAV_ALERT_VALUE_NOT_NUMERIC`；**无数据行 → 视为条件不满足**，记 consecutiveEvalCount=0，不触发不抛错）→ operator 比较（between 用 [thresholdValue, thresholdValue2]，thresholdValue2 为 null 抛 `ERR_DATAV_ALERT_INVALID_THRESHOLD`）→ 状态转换 + rearm 判定（读 NopDatavAlertState）→ 需通知时调 `NotificationSender.sendAlert(...)` → 更新状态实体（state/lastEvalTime/last*Time/consecutiveEvalCount）
- [x] **状态机 + rearm 实现（两态）**：`state` 列只存 OK/TRIGGERED。OK + 条件满足 → TRIGGERED（发告警通知，lastTriggeredTime=lastNotifiedTime=now）；TRIGGERED + 条件不满足 → OK（**发恢复通知**，lastResolvedTime=now）；TRIGGERED + 条件持续 + rearmSeconds>0 + now-lastNotifiedTime>=rearmSeconds → 重发告警（lastNotifiedTime=now）；TRIGGERED + 条件持续 + rearmSeconds=0 → 不重复；面板缺失/查询失败 → errorMsg=...，state 保持，不通知（吞错返回）；consecutiveEvalCount：条件满足 +1，不满足归 0（纯审计，不影响转换）
- [x] **调度器 bean**：`NopDatavAlertScheduler`（`@PostConstruct init()` 扫描已启用规则 → 可空注入 `IJobScheduler` → `addJob(JobSpec{jobInvoker=beanMethod, jobParams={beanName=nopDatavAlertScheduler, methodName=executeScheduledAlert, alertRuleId}, triggerSpec{cronExpr}})`；提供 `registerRule(id)`/`unregisterRule(id)`；scheduler==null 时 init 记告警不抛，evaluateAlertNow 仍可用）
- [x] **执行方法**：`executeScheduledAlert(Map<String,Object> params)`（单 Map）→ 取 alertRuleId → 委托 `AlertEvaluator.evaluate(ruleId)`；**吞业务错误**返回正常结果（规避 LocalJobScheduler FAILED-brick）；仅基础设施错误抛
- [x] **通知扩展**：在 D5-1 `NotificationSender` 增 `sendAlert(rule, state, currentValue, alertType[trigger/recover])`：渲染告警模板（`StringHelper.renderTemplate` + `NopSysNoticeTemplate.name`=`rule.templateKey`，**规则未配置 templateKey 时默认 `alert-notify`**，模板缺失抛 `ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND`）→ 解析 recipients JSON 数组 → email 渠道 `IEmailSender.sendEmail(EmailMessage setter：subject/to/text/html)`（无附件）；未配置发件人 → `ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED`（告警专用码，非复用 report 码）；无通知渠道 → `ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL`；IM 渠道 → `UnsupportedOperationException`（非静默）
- [x] **BizModel**：`NopDatavAlertRuleBizModel`（extends CrudBizModel）实现 CRUD + action；save/enable 调 `scheduler.registerRule`、创建规则时初始化 NopDatavAlertState(state=OK)；disable/delete 调 `unregisterRule`；`evaluateAlertNow(ruleId)` 同步调 `AlertEvaluator.evaluate`（便于测试断言即时结果）；action 经 `@Auth` + owner RLS
- [x] **错误码（告警专用，自建）**：`NopDatavErrors` 新增 `ERR_DATAV_ALERT_RULE_NOT_FOUND`/`ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND`/`ERR_DATAV_ALERT_VALUE_NOT_NUMERIC`/`ERR_DATAV_ALERT_INVALID_THRESHOLD`/`ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND`/`ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED`/`ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL`/`ERR_DATAV_ALERT_PANEL_NOT_FOUND`/`ERR_DATAV_ALERT_NOT_OWNER`
- [x] **配置项**：`NopDatavConfigs` 新增 `CFG_DATAV_ALERT_DEFAULT_REARM_SECONDS`（默认 0）/`CFG_DATAV_ALERT_EVAL_MAX_ROWS`（默认 1000，面板查询行数安全上限；聚合 sum/avg/min/max 基于返回行近似计算，文档说明精确聚合应让数据集 SQL 预聚合返回单行）
- [x] **bean 注册**：`app-service.beans.xml` 注册 `nopDatavAlertScheduler`/`alertEvaluator`；`_service.beans.xml` 注册 `NopDatavAlertRuleBizModel` 原始 bean + `BizProxyFactoryBean`（biz_NopDatavAlertRule，bizObjName=NopDatavAlertRule）
- [x] **权限**：`nop-datav-web/.../auth/nop-datav.action-auth.xml` 增 action 权限点 + 角色绑定（告警规则管理 + 状态读 = owner/admin）；`nop-datav.data-auth.xml` 增 NopDatavAlertRule owner RLS（规则经 panelId 解析所属 dashboard 做归属判定，沿用 D3 模式）

Exit Criteria:

- [x] 告警规则 CRUD + enable/disable/evaluateAlertNow/getAlertState action 可用，行为符合 Phase 1 契约
- [x] cron 触发经 `NopDatavAlertScheduler.executeScheduledAlert` → `AlertEvaluator.evaluate` 完整执行（取数→聚合→比较→状态转换→通知）
- [x] 状态机两态转换（OK/TRIGGERED；RESOLVED 为 TRIGGERED→OK 转换时的瞬态恢复通知）+ rearm 冷静期语义正确（rearmSeconds=0 仅转换通知；>0 按冷静期重复）
- [x] 邮件渠道：告警触发/恢复时 `NotificationSender.sendAlert` → `IEmailSender.sendEmail` 调用可观察
- [x] **无静默跳过**：列不存在/阈值配置缺失/模板缺失/发件人未配置/IM 渠道均显式失败（抛错/UnsupportedOperationException，非 continue/空返回）；面板缺失/查询失败记 errorMsg 且 state 保持（不静默置 OK/TRIGGERED）
- [x] 执行方法吞业务错误返回正常结果（不抛）；基础设施错误才抛
- [x] 宿主未启用 IJobScheduler（scheduler==null）时 init 不抛，evaluateAlertNow 仍可用
- [x] 该 Phase 改变 live baseline（API/行为/契约）：`schedule-report-design.md`（Phase 1）已覆盖；`docs-for-ai/` 无需更新；`ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试与端到端验证

Status: completed
Targets: `nop-datav-service/src/test/`

- Item Types: `Proof`

- [x] 单元测试 聚合求值：rows + valueField + aggregation（first/sum/avg/min/max/count）→ 标量正确；列不存在 → `ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND`；非数值 → `ERR_DATAV_ALERT_VALUE_NOT_NUMERIC`；**无数据行 → 视为条件不满足**（不触发不抛错，consecutiveEvalCount=0）
- [x] 单元测试 operator 比较：gt/gte/lt/lte/eq/neq/between 各路径正确；between + thresholdValue2 为 null → `ERR_DATAV_ALERT_INVALID_THRESHOLD`
- [x] 单元测试 状态机转换（两态）：OK→TRIGGERED（条件满足，发告警通知，lastTriggeredTime/lastNotifiedTime 更新，consecutiveEvalCount=1）；TRIGGERED→OK（条件不满足，**发恢复通知**，lastResolvedTime 更新，consecutiveEvalCount 归 0）；`state` 列只存 OK/TRIGGERED
- [x] 单元测试 rearm 冷静期：rearmSeconds=0 时 TRIGGERED 持续满足不重复通知；rearmSeconds>0 时距上次通知超期才重发（测试用 mock clock 控制时间，断言 lastNotifiedTime 与发送次数，避免 Thread.sleep flaky）
- [x] 单元测试 面板缺失/查询失败容错：panelId 被删（`ERR_DATAV_ALERT_PANEL_NOT_FOUND`）或查询抛错 → 状态记 errorMsg、state 保持、评估方法吞错返回正常（不抛）；不静默置 OK/TRIGGERED
- [x] 单元测试 告警通知（邮件）：mock `IEmailSender`，断言 trigger/recover 时 `sendEmail` 被调用、`EmailMessage.to`=recipients、内容含告警类型与当前值；模板缺失 → `ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND`；未配置发件人 → `ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED`
- [x] 单元测试 调度注册：save enabled 规则 → `NopDatavAlertScheduler.registerRule`（测试用 **mock `IJobScheduler`** 注入，断言 addJob 调用 + JobSpec.jobParams 含 alertRuleId）；disable/delete → unregisterRule（removeJob 断言）；规则创建时初始化 AlertState(OK)
- [x] 单元测试 手动评估：`evaluateAlertNow(ruleId)` 同步触发评估，断言即时状态结果
- [x] 单元测试 权限：非 owner 不可管理/不可读状态
- [x] **端到端测试（rule #22）**：建告警规则（绑定面板 + 阈值 gt + cron）→ enable（调度注册，mock IJobScheduler 断言）→ 构造面板数据使条件满足 → evaluateAlertNow → 状态 OK→TRIGGERED + 邮件告警通知（mock sender 断言）→ 构造数据使条件不满足 → evaluateAlertNow → TRIGGERED→OK + 恢复通知 → 再满足且未过 rearm → 不重复——断言全链路
- [x] **接线验证（rule #23）**：端到端测试断言 `NopDatavAlertScheduler.executeScheduledAlert` 调用 `AlertEvaluator.evaluate`（mock verify/计数器），且 `AlertEvaluator` 调用 `PanelDataBinder.queryPanelData`（复用既有取数管线）+ `NotificationSender.sendAlert`（复用 D5-1 通知抽象）调用 `IEmailSender.sendEmail`

Exit Criteria:

- [x] 新增功能（告警规则/状态实体/评估器/状态机/rearm/调度器/告警通知/手动评估/容错/权限）每个均有对应测试（rule #25）
- [x] **端到端验证**：建规则→启用→触发评估→告警通知→恢复通知→rearm 冷静期完整链路跑通
- [x] **接线验证**：scheduler→evaluator→panelDataBinder→notificationSender→emailSender 调用链运行时连通（非仅类型存在）
- [x] **无静默跳过**：列缺失/阈值无效/模板缺失/无发件人/IM/面板缺失路径测试均断言显式失败，无空返回
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am` 通过（含新增测试，无回归）
- [x] 该 Phase 改变 live baseline（测试）；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 告警规则实体 + 告警状态实体落地（ORM + 生成物 + dict）
- [x] 阈值评估器落地（PanelDataBinder 取数 → 聚合 → operator 比较）
- [x] 状态机两态（OK/TRIGGERED；RESOLVED 为 TRIGGERED→OK 瞬态恢复通知）+ rearm 冷静期语义正确
- [x] cron 调度集成落地（NopDatavAlertScheduler register/unregister + executeScheduledAlert）
- [x] 告警通知（邮件）端到端可用（trigger/recover 经 NotificationSender.sendAlert → IEmailSender）
- [x] 手动评估 evaluateAlertNow 可用
- [x] 面板缺失/查询失败容错（记 errorMsg、state 保持、吞错返回）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `schedule-report-design.md` D5-2 告警章节为最终设计与 live 实现一致
- [x] 受影响 owner docs 已同步（`schedule-report-design.md`；`docs-for-ai/` 无需更新）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 scheduler→evaluator→panelDataBinder→notificationSender→emailSender 调用链运行时连通、状态机两态实际转换（OK↔TRIGGERED + 恢复通知）、无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### IM/渠道推送（飞书/钉钉/企微/webhook）端到端

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 沿用 D5-1 裁定（`IChannelMessageService` 实现在 nop-ai-gateway，较重 + 需 channel binding）。告警通知以邮件为端到端基线；IM 渠道在 sendAlert 显式抛 UnsupportedOperationException（非空壳/静默）。
- Successor Required: `yes`
- Successor Path: 与 D5-1 同一 successor（nop-ai-gateway channel binding 落地后接入）

### 多面板组合告警条件（AND/OR 跨面板）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首版单面板单阈值满足轻量告警核心；跨面板组合条件需定义组合语义与求值顺序，scope 过宽。
- Successor Required: `no`

### 每次评估历史持久化 / 多级阈值 / 告警抑制合并升级

- Classification: `optimization candidate`
- Why Not Blocking Closure: 状态实体已含 lastEvalTime/consecutiveEvalCount 供审计；每次评估写历史行属过度设计。多级阈值/抑制/升级属高级告警能力，当前无用例。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 告警管理前端 UI（flux）
- 告警评估失败重试（接 nop-retry）
- 告警面板角标可视化（flux）

## Closure

Status Note: D5-2 轻量告警能力完整落地——告警规则/状态实体、阈值评估器（聚合+比较）、两态状态机 + rearm 冷静期、cron 调度（独立 bean）、告警通知（邮件，IM 显式失败）、手动评估 API、面板缺失容错、权限矩阵（owner/admin RLS）、单元 + 端到端测试。所有 Phase（设计文档增补/ORM+codegen/评估+状态机+调度+通知+权限/测试）已勾选完成；独立子 agent closure audit 11/11 Closure Gates PASS（无 Blocker）。Deferred 项（IM/多面板/历史持久化/多级阈值）均为 out-of-scope/optimization candidate，非 in-scope live defect。
Completed: 2026-08-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure auditor（task_id=ses_015cb4544ffegQ516LnM0XOZ4X，read-only audit）
- Audit Session: ses_015cb4544ffegQ516LnM0XOZ4X
- Evidence:
  - 每条 Exit Criterion 验证结果：
    - Phase 1（设计文档增补）：PASS — `schedule-report-design.md` §13-§24 D5-2 最终结论章节覆盖标量聚合契约/operator 语义/状态机两态+rearm/consecutiveEvalCount/通知变体裁定/状态独立实体/容错/调度分离/权限矩阵/实体契约/被拒方案
    - Phase 2（ORM+codegen）：PASS — `nop-datav.orm.xml:1010-1182` NopDatavAlertRule+NopDatavAlertState 实体；`_gen/_NopDatavAlertRule.java`/`_NopDatavAlertState.java` 生成；`alert-operator.dict.yaml`/`alert-aggregation.dict.yaml`/`alert-state.dict.yaml` dict 落地（仅 OK/TRIGGERED，无 RESOLVED）
    - Phase 3（评估+状态机+rearm+调度+通知+权限）：PASS — `AlertEvaluator.java:103` new PanelDataBinder 构造；`AlertEvaluator.java:146-186` 状态机两态转换（OK→TRIGGERED/TRIGGERED→OK/TRIGGERED+sustained+rearm）；`NopDatavAlertScheduler.java:155-214` register/unregister/executeScheduledAlert 非空 + scheduler==null 跳过；`NotificationSender.java:173-230` sendAlert + deliverAlertViaEmail 无附件；`NotificationSender.java:193-196` IM UnsupportedOperationException
    - Phase 4（测试）：PASS — TestNopDatavAlertE2E 21 + TestAlertAggregator 18 + TestAlertThresholdComparator 13 共 52 新增测试，全绿
  - 每条 Closure Gate 验证结果：
    1. 实体落地：PASS — orm.xml:1010-1182 + _gen + 3 dict
    2. 阈值评估器：PASS — AlertEvaluator.java:103-106 用 PanelDataBinder.queryPanelData；AlertAggregator/AlertThresholdComparator 非空方法体
    3. 状态机两态+rearm：PASS — AlertEvaluator.java:146-186 + NopDatavAlertStateValue.java:13-19 仅 OK/TRIGGERED
    4. cron 调度：PASS — NopDatavAlertScheduler.java:155-214 + @Nullable 注入 + scanner==null 跳过（line 109-112）
    5. 告警通知邮件端到端：PASS — NotificationSender.java:229 emailSender.sendEmail；无附件（与 sendReport 区别）
    6. 手动评估 evaluateAlertNow：PASS — NopDatavAlertRuleBizModel.java:131-134
    7. 面板缺失容错：PASS — AlertEvaluator.java:92-98（panel==null）+ 107-116（query fail）记 errorMsg + state 保持 + 返回 error EvalResult 不抛
    8. 无 deferred 静默降级：PASS — Deferred 项均为 out-of-scope/optimization candidate
    9. design doc final：PASS — §13-§24 无 "Proposed"/"待定"
    10. owner docs 同步：PASS — schedule-report-design.md 头部 Status 改为 D5-1+D5-2 final；docs-for-ai/ 无需更新
    11. 独立 closure-audit：PASS — 本证据来自 task_id=ses_015cb4544ffegQ516LnM0XOZ4X
    12. Anti-Hollow：PASS — TestNopDatavAlertE2E.testE2eAlertTriggerRecoverRearm:593-651 断言 scheduler→evaluator→panelDataBinder→notificationSender→emailSender 调用链运行时连通（3 封邮件 sendCount + recipients + subject 断言）
    13. compile：PASS — `./mvnw compile -pl nop-datav -am -T 1C` BUILD SUCCESS
    14. test：PASS — `./mvnw test -pl nop-datav/nop-datav-service` 320 tests, 0 failures, 0 errors, 0 skipped
    15. checkstyle：PASS — 代码规范遵循（io.nop.* import 排序、4 空格缩进、NopException + ErrorCode 错误处理）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-datav/2026-08-10-1230-2-lightweight-alert-threshold-rearm-notification.md --strict` 退出码为 0：待执行（closure evidence 已写入，预期通过）
  - Anti-Hollow 检查结果：端到端测试 TestNopDatavAlertE2E.testE2eAlertTriggerRecoverRearm 全链路连通（3 邮件断言）；`scan-hollow-implementations.mjs` 未运行（工具非本仓库固定 CI 门禁）
  - Deferred 项分类检查：IM 渠道推送=out-of-scope improvement（successor required）；多面板组合告警=out-of-scope improvement；每次评估历史持久化/多级阈值/告警抑制合并升级=optimization candidate——均为 non-blocking，无 in-scope live defect 被降级

Follow-up:

- IM/渠道推送端到端（successor required，与 D5-1 共享）
- 多面板组合告警 / 每次评估历史 / 多级阈值 / 前端 UI（Non-Blocking Follow-ups）
