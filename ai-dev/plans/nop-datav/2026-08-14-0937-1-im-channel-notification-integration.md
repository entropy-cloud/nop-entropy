# IM 渠道通知接入（报告交付 + 告警通知 successor）

> Plan Status: completed
> Last Reviewed: 2026-08-14
> Mission: nop-datav
> Work Item: D5-1 / D5-2 deferred successor — IM/渠道推送（飞书/钉钉/企微/webhook）
> Source: D5-1 plan `2026-08-10-1230-1` Deferred But Adjudicated「IM/渠道推送端到端」+ D5-2 plan `2026-08-10-1230-2` 同一 successor；触发条件 `IChannelMessageService` 实现已落地
> Related: `ai-dev/plans/nop-datav/2026-08-10-1230-1-scheduled-report-generation-and-delivery.md`、`ai-dev/plans/nop-datav/2026-08-10-1230-2-lightweight-alert-threshold-rearm-notification.md`；设计 `ai-dev/design/nop-datav/schedule-report-design.md`；信道集成设计 `ai-dev/design/nop-ai-channel-integration-design.md`

## Purpose

把 D5-1（定时报告）和 D5-2（轻量告警）中 deferred 的 IM 渠道通知从「显式抛 `UnsupportedOperationException`」收口为「经 `IChannelMessageService.sendToUser` 真实发送」，使 `notifyChannels=["im"]` 的报告交付与告警通知端到端可用（仅出站主动通知文本/Markdown，不含文件附件——见 Deferred）。

触发条件核对（已满足，故 re-trigger）：D5-1/D5-2 deferred 项的 `Successor Path` 为「nop-ai-gateway channel binding 落地后，独立 plan 接入 `IChannelMessageService`」。经 live repo 核对，该条件已全部满足：

- 接口 `IChannelMessageService`（`sendToUser(userId, OutboundChannelMessage)→SendResult`、`subscribeInbound`）落 `nop-integration-api`（`io.nop.integration.api.channel`）。
- 实现 `ChannelMessageServiceImpl` 落 `nop-ai-gateway`（`ChannelMessageServiceImpl.java` resolver→connector→sendOutbound 真实路径，非 stub），bean `channelMessageService`（`ioc:default=true`）已注册于 `ai-gateway-defaults.beans.xml`。
- 飞书首信道（`nop-integration-feishu` + `FeishuConnector`）、扫码绑定（`NopAuthExtLogin` `(loginType,extId)` 唯一索引 + `IChannelBindProvider`）、E2E 主动通知（`TestChannelProactiveNotifyE2E` 断言 `feishuClient.sendMessageCount==1`）均已落地。

**部署前提（非本计划实现）**：生产环境 IM 可用要求宿主 app 装配 `nop-ai-gateway`（提供 `channelMessageService` bean）。`nop-datav-service` 仅依赖接口 `nop-integration-api`（已传递在类路径），不依赖 `nop-ai-gateway`；未装配时 `IChannelMessageService` 注入 null → IM 渠道显式失败（见 Decision B）。

## Pre-Design Decisions（draft 阶段定稿，执行前置）

> 以下两项 Decision 经独立审阅对抗性审查确认为 Blocker 级前置设计冲突，已在 draft 阶段定稿。执行时不得重新发明，仅按此裁定落地，并把结论誊抄进 `schedule-report-design.md`。

### Decision A — 收件人身份模型：格式检测分区（format-detection partition）

**裁定**：`recipients`（clobJson 字符串数组）为所有渠道共享。按条目**格式**分区路由：

- 匹配邮箱正则 `^\S+@\S+\.\S+$` 的条目 → **email 收件人**（email 渠道消费）。
- 其余非空条目 → **平台 userId**（im 渠道消费，传给 `sendToUser(userId, ...)`）。

**实现约束（分区先于投递调用）**：在 `sendReport`/`sendAlert` 入口先按上述规则把 `recipients` 分区为 `emailAddrs` / `userIds` 两组，再按渠道投递：
- email 渠道：**仅当 `emailAddrs` 非空时**调用 `deliverViaEmail(emailAddrs)`；`emailAddrs` 为空（该渠道无可寻址收件人）→ **跳过该渠道**（不调用 `deliverViaEmail`，从而不触发其现有的空收件人防御性 throw），不计入 `delivered`。`deliverViaEmail` 内部的空收件人 throw 保留作为防御性兜底，但在分区流程下不再被触发。
- im 渠道：对 `userIds` 中每个条目调 `sendToUser`；`userIds` 为空 → 跳过该渠道，不计入 `delivered`。
- 若**所有**被请求渠道经分区后可寻址收件人均为 0（即 `delivered` 最终为空）→ 显式失败 `ERR_DATAV_*_NO_NOTIFIABLE_CHANNEL`（沿用既有错误码，errorMsg 含无可用收件人）。

**向后兼容性**：「行为不变」指纯邮箱任务（recipients 全为邮箱格式 + channels=["email"]）：分区后 `emailAddrs`=原列表、`userIds`=空，email 照常投递，`deliverViaEmail` 收到与今天相同的列表——行为完全不变。混合任务 `recipients=["a@example.com","u1","u2"]` + `channels=["email","im"]` → email 发给 a@example.com、im 发给 u1/u2。

**拒绝的替代方案**（记入设计文档）：
- 结构化 recipients `[{"channel":"email","addr":...}]` — 与既有 `["a@example.com"]` 格式不向后兼容，需数据迁移。
- 新增 `imRecipients` 列 — ORM 结构变更（Protected Area plan-first），v1 不必要。
- 「IM 收件人恒为任务 owner」— 过窄，不符合通知列表语义。

### Decision B — SendResult 处理：渠道聚合语义（channel-aggregate）

**裁定**：`sendToUser` 不为 NO_BINDING/UNSUPPORTED 抛异常（接口契约），IM 渠道按**渠道级聚合**判定投递结果：

- 逐 userId 调 `sendToUser`，**逐用户 try/catch**：某用户发送抛异常（网络/连接器错误）→ **catch `Exception`（非 `Throwable`，JVM 级 `Error` 向外传播）**、WARN 日志（userId + 异常）、计为该用户非 SENT，**不中断循环**。
- 渠道聚合结果：**≥1 用户返回 SENT → 该渠道 delivered（"im" 加入 delivered 列表，写入 `delivery.deliveredChannels`）**；全部用户 NO_BINDING/UNSUPPORTED/抛异常 → 该渠道未投递（"im" 不入 delivered）。
- 投递级判定：`delivered` 为空（无渠道成功）→ 抛 `NopException`（新增 `ERR_DATAV_REPORT_ALL_NOTIFY_FAILED` / `ERR_DATAV_ALERT_ALL_NOTIFY_FAILED`，英文消息列出失败渠道 + NO_BINDING 计数），被 executor 捕获 → delivery FAILED，errorMsg 记录原因。

**repo-observable 契约**（Exit Criteria 据此验证，非模糊"记录"）：
- "im" ∈ `delivery.deliveredChannels` ⟺ ≥1 用户 SENT。
- 全失败 → `delivery.status=FAILED` 且 `delivery.errorMsg` 提及 NO_BINDING/UNSUPPORTED。
- 单用户 NO_BINDING/UNSUPPORTED → WARN 日志（运维可见），不进 delivery 字段（无单收件人结果列，避免 ORM 变更）。

**为何非静默跳过**：(1) 渠道聚合结果是确定的（delivered 或不 delivered）；(2) 零成功是硬失败；(3) 单用户未绑定有 WARN 日志。三者共同满足 Minimum Rules #24。

### Decision C — IM 附件：v1 不投递文件附件（out-of-scope）

**裁定**：IM 渠道**仅发送文本/Markdown 通知**（报告摘要：reportName/dashboardName/rows/fileName；告警：ruleName/state/currentValue/threshold），**不携带生成的导出文件作为附件**。理由（经 live repo 核对）：
- `IFileRecord` 无 `getUrl()`，无可派生的外部可访问下载 URL（需下载端点 + base path + 鉴权，属更大集成关切）。
- `FeishuConnector` 仅消费 `Attachment.url`，忽略 `Attachment.content`(byte[])。
- 文件经 email 渠道附件已可达（email 渠道行为不变）。

列入 Deferred「IM 渠道文件附件投递」（out-of-scope improvement，successor required：下载 URL 方案落地后）。

## Current Baseline

- `NotificationSender`（`nop-datav-service/.../report/NotificationSender.java`）当前对 IM 渠道在两处显式抛 `UnsupportedOperationException`：
  - `sendReport`（约 line 130-134）：`notifyChannels` 含 `im` → throw
  - `sendAlert`（约 line 193-196）：`notifyChannels` 含 `im` → throw
- 渠道字典 `datav/notify-channel`（`model/nop-datav.orm.xml` line 64-66）已含 `IM → value="im"` 选项，无需改 dict。
- 邮件渠道已端到端打通（`IEmailSender.sendEmail`，报告带附件、告警无附件），是本计划接入 IM 的对照范式。
- `recipients` 列（`NopDatavReportTask.recipients` / `NopDatavAlertRule.recipients`，`domain="clobJson"`）存 JSON 字符串数组，邮件渠道解释为邮箱地址。
- `nop-integration-api` 已在 `nop-datav-service` 编译类路径上（经 `IEmailSender`/`EmailMessage` 传递引入），`IChannelMessageService`/`OutboundChannelMessage`/`SendResult` 同包可 import。
- bean `notificationSender` 注册于 `_vfs/nop/datav/beans/app-service.beans.xml`（`ioc:default=true`），采用 setter 注入 `IEmailSender`（`@Nullable`）。
- `ReportDeliveryExecutor.runDelivery`：session 内取数/落盘/SUCCEEDED 提交 → session 关闭后 out-of-session 送达；`sendReport` 抛异常被捕获 → delivery 回补 FAILED。
- 测试 mock beans `test-report-mock.beans.xml`（或同类）当前仅注册 `MockEmailSender`，需新增 mock `IChannelMessageService`。
- 现有测试 `TestNopDatavReportE2E.testImChannelThrowsUnsupported` 断言 IM → delivery FAILED；本计划改为断言真实发送。
- 设计文档 `schedule-report-design.md` §1/§10/§11 当前记「IM deferred（UnsupportedOperationException）」。

## Goals

- `sendReport` 与 `sendAlert` 对 `im` 渠道经 `IChannelMessageService.sendToUser` 真实发送（文本/Markdown），`UnsupportedOperationException` 被取代。
- Decision A（收件人格式分区）+ B（渠道聚合语义）落地并 repo-observable 可验证。
- 端到端验证：报告交付链路与告警链路各至少一条端到端测试跑通（含 `sendToUser` 调用断言）。
- 设计文档 `schedule-report-design.md` IM 章节从 deferred 收口为最终结论。

## Non-Goals

- 不实现 IM 入站（`subscribeInbound`）；仅出站主动通知。
- 不做跨信道降级（飞书失败→短信）；接口 v1 non-goal。
- 不投递 IM 文件附件（Decision C，out-of-scope）。
- 不新增 IM 管理前端 UI（flux）。
- 不改 ORM 模型结构（`recipients`/`notifyChannels` 列复用；Decision A 不新增列）。
- 不实现新信道连接器（钉钉/企微等属 nop-ai-gateway 侧）。
- 不改邮件渠道现有行为。

## Scope

### In Scope

- `NotificationSender` setter 注入 `IChannelMessageService`（`@Nullable`，未注册 → IM 显式失败，新增 IM 专用错误码）。
- `sendReport`/`sendAlert` 的 IM 分支：按 Decision A 分区收件人 → 构造 `OutboundChannelMessage`（文本，无附件）→ 逐 userId `sendToUser` → 按 Decision B 聚合。
- 新增 IM 专用错误码（`ERR_DATAV_REPORT_CHANNEL_SERVICE_NOT_CONFIGURED` / `ERR_DATAV_ALERT_CHANNEL_SERVICE_NOT_CONFIGURED` / `ERR_DATAV_*_ALL_NOTIFY_FAILED`，英文消息）。
- 单元/端到端测试（含 mock `IChannelMessageService` 注册到测试 beans）。
- 设计文档与 Javadoc 同步。

### Out Of Scope

- IM 入站、跨信道降级、文件附件（Decision C）、新信道连接器、前端 UI、ORM 结构变更（见 Non-Goals）。
- 多面板组合告警、告警历史持久化等 D5-2 既已 deferred 且无 successor 的项。

## Execution Plan

### Phase 1 — IM 接线 + 报告交付渠道（Decision A/B 落地 + 测试）

Status: completed
Targets: `NotificationSender.java`、`NopDatavErrors.java`、`_vfs/nop/datav/beans/app-service.beans.xml`、测试 mock beans（注册 mock `IChannelMessageService`）、`schedule-report-design.md`（§1/§10 增 Decision A/B/C）

- Item Types: `Decision`、`Fix`、`Proof`

- [x] **Decision** — 将 Pre-Design Decisions A/B/C 结论誊抄进 `schedule-report-design.md`（§1 IM 收口 + §10 渠道范围表新增 IM 行 + 拒绝替代方案表）。
- [x] **Fix — 新增 IM 专用错误码**：`NopDatavErrors` 增 `ERR_DATAV_REPORT_CHANNEL_SERVICE_NOT_CONFIGURED`（IChannelMessageService 未注入）、`ERR_DATAV_REPORT_ALL_NOTIFY_FAILED`（全渠道零成功）；英文消息，含 `.param(...)` 上下文。
- [x] **Fix — 注入 IChannelMessageService**：`NotificationSender` 新增 `@Inject @Nullable` setter（镜像 `setEmailSender`）；null 时 IM 渠道抛 `ERR_DATAV_REPORT_CHANNEL_SERVICE_NOT_CONFIGURED`（非静默）。
- [x] **Fix — sendReport IM 分支**：移除 `UnsupportedOperationException`；按 Decision A 分区收件人 → 构造 `OutboundChannelMessage`（text=subject+body+报告摘要，无附件）→ 逐 userId `sendToUser`（逐用户 try/catch + WARN）→ 按 Decision B 聚合（≥1 SENT → "im" 入 delivered；全失败 → 抛 `ERR_DATAV_REPORT_ALL_NOTIFY_FAILED`）。
- [x] **Proof — 测试 mock beans**：测试 beans 注册 mock `IChannelMessageService`（可编程返回 SENT/NO_BINDING/抛异常）。
- [x] **Proof — 报告 IM 测试**：覆盖 (1) IM SENT（mock verify `sendToUser` 调用次数 + userId 参数 + message.text 含报告摘要 + delivered 含 im）；(2) 全 NO_BINDING → delivery FAILED + errorMsg 提及；(3) channelMessageService 未注入 → 显式失败；(4) 混合 email+im（recipients 含邮箱+userId）两渠道各投递。
- [x] **Proof — 既有测试调整**：`testImChannelThrowsUnsupported` 改为断言真实发送（注入 mock 返回 SENT）。**注意 recipients 必须含至少一个非邮箱格式条目（userId）**，例如 `["u1"]` 或 `["a@example.com","u1"]`——否则按 Decision A 分区后 im 无可寻址收件人，`sendToUser` 不会被调用，mock verify 会失败。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `sendReport` 对 `im` 渠道不再抛 `UnsupportedOperationException`，改为经 `sendToUser` 发送（live code 可验证）
- [x] Decision A/B/C 已誊抄进 `schedule-report-design.md`（§1/§10 + 拒绝替代方案）
- [x] IM 专用错误码已新增（CHANNEL_SERVICE_NOT_CONFIGURED / ALL_NOTIFY_FAILED），消息英文、未复用 email 错误码
- [x] channelMessageService 未注入时 IM 渠道显式失败（非静默/非空壳）
- [x] **repo-observable**：mock SENT 时 `delivery.deliveredChannels` 含 "im"；全 NO_BINDING 时 `delivery.status=FAILED` 且 errorMsg 提及
- [x] **接线验证**：测试 mock verify 断言 `sendToUser` 在运行时被调用（含次数 + userId 参数）
- [x] **无静默跳过**：IM 分支无空方法体/continue/吞异常；单用户异常 catch + WARN（非吞掉）
- [x] **新增功能测试覆盖**：报告 IM SENT / 全 NO_BINDING / 未注册 sender / 混合渠道 四路径有 focused 测试
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 告警交付渠道 + 端到端 + 设计收口

Status: completed
Targets: `NotificationSender.java`（sendAlert IM 分支）、`NopDatavErrors.java`（告警 IM 错误码）、告警侧测试、`schedule-report-design.md` 收口、`NotificationSender` Javadoc

依赖：Phase 1 已建立 IM 注入范式与 Decision A/B。

- Item Types: `Fix`、`Proof`

- [x] **Fix — 告警 IM 错误码**：`NopDatavErrors` 增 `ERR_DATAV_ALERT_CHANNEL_SERVICE_NOT_CONFIGURED` / `ERR_DATAV_ALERT_ALL_NOTIFY_FAILED`。
- [x] **Fix — sendAlert IM 分支**：移除 `UnsupportedOperationException`；按 Decision A 分区 → 构造 `OutboundChannelMessage`（短文本：ruleName/state/currentValue/threshold，无附件）→ 逐 userId `sendToUser`（逐用户 try/catch）→ 按 Decision B 聚合。
- [x] **Proof — 告警 IM 单元测试**：trigger（TRIGGERED）与 recover（TRIGGERED→OK）两通知经 `sendToUser` 发送，断言 text 含告警语义字段 + mock verify 调用。
- [x] **Proof — 告警端到端测试**：`AlertEvaluator` 评估 → 状态机转换（OK↔TRIGGERED + rearm）→ `sendAlert` → `sendToUser` SENT 全链路（注入 mock，断言被调用 + 参数含当前值/阈值）。
- [x] **Proof — 报告端到端测试**：cron/手动触发 → 取数落盘 → `sendReport` → `sendToUser` SENT 全链路（断言 message.text 含报告摘要；不验证附件——Decision C）。
- [x] **Fix — 设计收口**：`schedule-report-design.md` §11「IM/渠道推送」从 `out-of-scope improvement（successor required）` 改为已落地；§10 IM 行从「抛 UnsupportedOperationException」改为 Decision A/B 语义。
- [x] **Fix — Javadoc 同步**：`NotificationSender` 类/方法 Javadoc「IM 显式抛 UnsupportedOperationException」更新为真实发送 + Decision A/B 语义。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `sendAlert` 对 `im` 渠道不再抛 `UnsupportedOperationException`，改为经 `sendToUser` 发送
- [x] **端到端验证（告警）**：评估→状态机→IM 通知→SENT 完整路径已验证（Minimum Rules #22）
- [x] **端到端验证（报告）**：触发→取数落盘→IM 送达→SENT 完整路径已验证
- [x] **接线验证**：告警端到端测试 mock verify `sendToUser` 在运行时被调用
- [x] **新增功能测试覆盖**：告警 trigger/recover IM 路径有 focused 测试
- [x] `schedule-report-design.md` §10/§11 IM 从 deferred 收口为最终结论，与 live 实现一致
- [x] `NotificationSender` Javadoc 与 live 行为一致（无「not yet implemented」误导残留）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `sendReport` 与 `sendAlert` 的 IM 渠道均经 `sendToUser` 真实发送，无 `UnsupportedOperationException` 残留
- [x] Decision A（格式分区）/ B（渠道聚合）/ C（无附件）已落地并记入设计文档，向后兼容、无 ORM 结构变更
- [x] repo-observable：mock SENT → deliveredChannels 含 "im"；全 NO_BINDING → FAILED + errorMsg；未注入 service → 显式失败
- [x] 单用户 NO_BINDING/异常非吞掉（WARN 日志 + 计入聚合）；零成功显式失败
- [x] 报告端到端 + 告警端到端各至少一条测试跑通（含 `sendToUser` mock verify）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope 项（IM 附件按 Decision C 显式 adjudicated，非 in-scope live defect）
- [x] `schedule-report-design.md` 与 live 实现一致（§1/§10/§11）
- [x] 受影响 owner docs 已同步（`schedule-report-design.md`；`docs-for-ai/` 无需更新——IM 接入是模块内部通知渠道扩展）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证 NotificationSender→`sendToUser` 运行时调用链连通（端到端 mock verify），无空方法体/静默跳过
- [x] `./mvnw compile -pl nop-datav -am`
- [x] `./mvnw test -pl nop-datav/nop-datav-service -am`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### IM 渠道文件附件投递

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `IFileRecord` 无 `getUrl()`，无可派生的外部下载 URL；`FeishuConnector` 忽略 `Attachment.content`(byte[])，仅消费 `url`。文件经 email 渠道附件已可达。Decision C 明确 v1 仅文本/Markdown。非 in-scope live defect（IM 文本通知端到端可用即满足 successor 触发条件）。
- Successor Required: `yes`
- Successor Path: 报告导出文件下载 URL 方案（端点 + base path + 鉴权）落地后，或连接器支持 byte[] content 上传后，独立 plan 接入 IM 附件。

## Non-Blocking Follow-ups

- 钉钉/企微/webhook 新信道连接器（nop-ai-gateway 侧；飞书首信道已证明接入范式）
- 报告/告警管理前端 UI（flux）
- 跨信道降级（飞书失败→短信），接口 v1 non-goal
- 宿主部署文档：生产 IM 可用需装配 nop-ai-gateway（提供 channelMessageService bean）

## Closure

Status Note: IM 渠道通知接入完成。sendReport/sendAlert 对 notifyChannels=["im"] 经 IChannelMessageService.sendToUser 真实发送（Decision A 格式分区 + Decision B 渠道聚合 + Decision C 无附件），UnsupportedOperationException 已移除。报告 4 条 IM 测试 + 告警 3 条 IM 测试 + 既有 email 全量回归（403 tests, 0 failures）通过，独立子 agent closure audit APPROVE_CLOSURE。
Completed: 2026-08-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task ses_001a3533affemWcP3GfvURWE9x，general 类型，fresh session）
- Evidence:
  - 每条 Exit Criterion / Closure Gate 验证结果（全部 PASS）：
    - sendReport/sendAlert IM 经 sendToUser 发送（NotificationSender.java:172-176 / 252-255 → deliverReportViaIm:309 / deliverAlertViaIm:330 → sendToUsersAggregated:364 → channelMessageService.sendToUser:368），无 UnsupportedOperationException 残留。
    - Decision A 分区（partitionRecipients:565-577，EMAIL_PATTERN:86，sendReport:156/sendAlert:238 调用）；Decision B 聚合（sendToUsersAggregated:364-382，catch Exception 非 Throwable，WARN 日志，≥1 SENT → delivered，全失败抛 ERR_DATAV_*_ALL_NOTIFY_FAILED）；Decision C 无附件（buildChannelMessage:348-355，测试 TestNopDatavReportE2E:356 断言 attachments 空）。
    - channelMessageService null → ERR_DATAV_*_CHANNEL_SERVICE_NOT_CONFIGURED（deliverReportViaIm:310 / deliverAlertViaIm:331），测试 testImChannelServiceNotConfiguredFailsExplicitly 验证。
    - 4 个新错误码（NopDatavErrors.java:367/373/435/441）。
    - MockChannelMessageService 注册 primary=true（test-report-mock.beans.xml:13-14）。
    - 报告 IM 4 测试（SENT/all-NO_BINDING/service-not-configured/mixed）；告警 IM 3 测试（trigger-SENT/trigger+recover/e2e-via-scheduler）。
  - `./mvnw test -pl nop-datav/nop-datav-service`：Tests run: 403, Failures: 0, Errors: 0, Skipped: 0（BUILD SUCCESS）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）。
  - Anti-Hollow 检查结果：调用链 sendReport→deliverReportViaIm→sendToUsersAggregated→sendToUser 运行时连通（mock verify 调用次数+userId+message.text 断言），无空方法体/continue/吞异常；`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（唯一 high 发现为 pre-existing 误报：deliverViaEmail 附件注释，代码实际已实现）。
  - Deferred 项分类检查：IM 文件附件（Decision C）为 out-of-scope improvement，已显式 adjudicated，非 in-scope live defect。

Follow-up:

- IM 渠道文件附件投递（out-of-scope improvement，successor required：下载 URL 方案落地后）
- 钉钉/企微/webhook 新信道连接器（nop-ai-gateway 侧）
- 宿主部署文档：生产 IM 可用需装配 nop-ai-gateway（提供 channelMessageService bean）
