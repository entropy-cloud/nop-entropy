# 2 nop-datav 事务边界、异步正确性与资源上界收口

> Plan Status: completed
> Mission: nop-datav
> Execution Order: 2 of 3（本批 3 份 remediation plan；权限面先行（plan 1），本 plan 收口「生产事务装饰器路径」系统性缺陷与资源上界缺口）
> Last Reviewed: 2026-08-15
> Draft Review: 两轮独立子 agent 对抗性审查（含想象性分析）达成共识，Blocker/Major 全部修复后转 active；审查记录见 `ai-dev/logs/2026/08-15.md`
> Source: `ai-dev/audits/nop-datav/2026-08-15-1913-open-audit-nop-datav.md` AR-1（P0）/ AR-2（P1）/ AR-3（P1）；`ai-dev/audits/nop-datav/2026-08-15-1913-multi-audit-nop-datav.md` P1-04 / P1-05 / P1-06
> Related: `ai-dev/plans/nop-datav/2026-08-14-0937-2-d5-report-alert-reliability-defects.md`（ReportDeliveryExecutor 事务拆分先例，已完成）；`ai-dev/plans/nop-datav/2026-08-15-0004-3-batch-query-parallel-execution-and-cache.md`（资源上界体系先例）

## Purpose

收口 nop-datav「测试直调 bean vs 生产事务装饰器」系统性盲区产出的 6 个缺陷：toggleShare/revokeShare 事务 commit 时脏 flush 抹除分享密码哈希（AR-1，P0，已实证复现）、异步任务在未提交事务内提交导致导出/报告竞态性静默不执行（AR-2）、导出路径独缺面板数上界（AR-3）、ChatBI maxRows 无服务端钳制（P1-04）、AI tool-calling 循环整体运行在数据库事务内（P1-05）、evaluateAlertNow 事务内同步 SMTP 远程发送（P1-06）。修复的同时建立「经 graphQLEngine mutation 事务路径」的固定回归测试资产，使该类缺陷今后无法在全绿测试下存活。

## Current Baseline

以下事实 2026-08-15 已对照 live repo 核实（open-audit 对 AR-1 另有平台机制链五环逐行核实 + 生产等价路径实证复现）：

- AR-1：`NopDatavDashboardShareBizModel.doToggleShare`（toggleShare/revokeShare 共用）先 `updateEntityDirectly(share)` 再 `share.setPasswordHash(null)`（mask-on-return 改写 attached 实体）后返回实体——@BizMutation 事务 commit 时 `OrmTransactionListener.onBeforeCommit → flushSession` 对重新变脏的 MANAGED 实体补发 `UPDATE ... SET PASSWORD_HASH=NULL`。带密码分享经任意 toggle 后哈希被物理抹除，`verifySharePassword` 对空哈希直接放行 → 凭 token 匿名访问原受密码保护看板；无重设密码 API，损坏不可自愈。`createShare` 同款 mask（`:127-128`）因 `saveDirectly` 不挂 session 缓存而侥幸不触发，但依赖平台内部实现细节；`listShares`（@BizQuery 无事务）同款改写属侥幸无害。现有测试全部经 `@Inject` 直调裸 bean（无事务装饰器），损坏路径在测试中物理不可达。
- AR-2：`NopDatavExportTaskBizModel.createExportTask`（@BizMutation）在事务提交前 INSERT pending 任务后立即 `submitExecution(taskId, operator)` 提交到 `GlobalExecutors.globalWorker()`（`:150-158`）；worker 新 session 首查 `task == null → return null` 零日志（`:216-221`）。`ReportDeliveryExecutor` 同模式（`:123 saveEntityDirectly → :127 globalWorker().submit`；`:173-176` null 零日志）。READ_COMMITTED 下 worker 读不到未提交行 → 竞态命中时导出永停 PENDING（占 `max-concurrent-per-user=3` 配额，60min 后被 stuck 扫描器误标 FAILED）、报告交付静默不执行。
- AR-3：`PanelDataExporter.exportDashboard`（`:148-191` 附近，`:172 for (NopDatavPanel panel : exportable)`）遍历全部 needsDataset 面板无数量上限；对照 `NopDatavDashboardBizModel` getDashboardData 的 `CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS`（默认 50）与 saveDashboardLayout 上限（50）均有闸，导出路径（每面板 1 条 SQL + `maxRows=100000` 行取数 + 全量内存 workbook）独缺。
- P1-04：`DatavQueryDatasetExecutor.java:104-108` maxRows 由 LLM 入参直取（配置 `CFG_DATAV_CHATBI_MAX_ROWS` 默认 1000 只是缺省值而非上限），`:159` `maxRows > 0` 才限行 → 传 0/负数时 range=null 全表物化；`datav-query-dataset.tool.xml` schemaJson 无 maximum。对照：导出/报告/告警路径的 max-rows 均为服务端硬上限（`NopDatavConfigs`：export 100000 / report 100000 / alert 1000）。
- P1-05：`NopDatavChatBiBizModel.chatToDashboard`（`:208`）/`chatToScreen`（`:266`）为 @BizMutation（平台机制 mutation 默认 transactional=REQUIRED），方法体内 `ChatBiToolCallingLoop.run` 循环多次远程 LLM 调用（`toolManager.callTool(...).join()` 阻塞调用线程）全程运行在事务内——单轮 LLM 可达数十秒 × maxIterations，并发请求可耗尽连接池。
- P1-06：`NopDatavAlertRuleBizModel.evaluateAlertNow`（`:150` @BizMutation，user 可调）链路内 `AlertEvaluator.evaluate` 做面板 SQL 查询 + `sendAlertNotification` 同步 SMTP/IM 远程发送（典型 30-60s 超时）+ 多次状态写，全程事务内。cron 路径不受影响（`BeanMethodJobInvoker` 纯反射无事务装饰）。正确先例：ReportDeliveryExecutor 的 Part A/B 拆分（SMTP 送达在 session 关闭后执行）。
- 测试基建：`AbstractNopDatavTest`/`AbstractNopDatavAuthTest` + `IGraphQLEngine` 注入模式已存在（`TestNopDatavRbacAuth` 等经真实 GraphQL 引擎调用）；`NopDatavConfigs` 配置注册模式已有。
- **事务回调 API 真实形态（审查已核实，注意无模块内先例）**：nop-datav 全模块 `afterCommit`/`ITransactionTemplate` 零使用；真实可用形态是 `@Inject ITransactionTemplate`（bean `nopTransactionTemplate`）+ `afterCommit(txnGroup, Runnable)`（平台接口 `nop-dao/txn/ITransactionTemplate.java:87` default 方法）。两个关键细节：(a) 无事务上下文时注册 listener **直接抛 `ERR_TXN_NOT_IN_TRANSACTION`**（`ITransactionTemplate.java:67`）——cron/恢复等无事务路径必须以 `isTransactionOpened` 分支守护后再注册，否则引入新缺陷；(b) 事务回滚时 onAfterCommit 不触发（语义恰好正确：回滚则不提交异步任务）。本 plan「先例」指平台接口定义与 nop-auth 等模块用法，非 nop-datav 模块内。

## Goals

- toggleShare/revokeShare/createShare/listShares 的出参不再改写 attached 实体（DTO/副本出参），事务 commit 不再产生任何针对 PASSWORD_HASH 的补发 UPDATE；经 graphQLEngine mutation 路径回归证明哈希在 toggle 后仍为 BCrypt、错误密码仍被拒（AR-1）。
- 异步执行提交移出未提交事务（afterCommit 或裁定等价机制）；worker 首查 null 分支从静默 no-op 变为可观测失败（ERROR 日志 + markFailedSafe 或短退避重查，按裁定）（AR-2）。
- `exportDashboard` 入口有面板数前置上限（复用 `CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS` 或独立 export 侧配置，按裁定）（AR-3）。
- ChatBI `datav-query-dataset` maxRows 钳制到 `[1, CFG_DATAV_CHATBI_MAX_ROWS]`（<=0/null 落配置缺省），tool schema 补 maximum（P1-04）。
- chatToDashboard/chatToScreen 的 LLM 循环移出数据库事务（实体落库收敛到独立短事务，或裁定等价 propagation 方案）；行为语义（生成结果、写库结果）不变（P1-05）。
- evaluateAlertNow 的通知发送移出事务（afterCommit/异步，对齐 ReportDeliveryExecutor 拆分模式）；评估+状态写语义不变（P1-06）。
- 沉淀至少一类「经 graphQLEngine mutation 真实事务路径 + 裸 JDBC/新 session 断言持久层副作用」的回归测试模式并至少有 2 条常驻测试（AR-1、AR-2 各一）。

## Non-Goals

- 不改平台 nop-orm/nop-biz 的事务/flush 机制（缺陷在模块用法层修复）。
- 不重构整个测试体系为全量 GraphQL 路径（只补关键写路径的事务回归资产）。
- 交互式面板查询路径无界行数（P2-28，有 javadoc 设计决定记录）与 nextSeq 全量加载（AR-4/P2）→ backlog，不随本 plan。
- 权限收口（plan 1）、DDL 物化/级联/错误码/测试卫生（plan 3）。

## Scope

### In Scope

- `NopDatavDashboardShareBizModel`（doToggleShare/createShare/listShares 出参改造）。
- `NopDatavExportTaskBizModel`（submitExecution 时序 + null 分支可观测）、`ReportDeliveryExecutor`（同模式）。
- `NopDatavChatBiBizModel`（chatToDashboard/chatToScreen 事务边界）、`NopDatavAlertRuleBizModel`/`AlertEvaluator`（通知发送时序）。
- `DatavQueryDatasetExecutor`（maxRows 钳制）、`datav-query-dataset.tool.xml`（schema maximum）、`PanelDataExporter`（面板数上界）、（按裁定）`NopDatavConfigs` 新增配置。
- 上述全部修复的事务路径/行为回归测试。
- `ai-dev/design/nop-datav/` 对应 owner doc 的事务边界契约增补（runtime-design / schedule-report-design / ai-design 按触及面）。

### Out Of Scope

- Non-Goals 列出的全部方向。

## Execution Plan

### Phase 1 - 分享密码哈希 dirty-flush 修复（AR-1）

Status: completed
Targets: `NopDatavDashboardShareBizModel.java`、新增事务路径回归测试

- Item Types: `Fix | Proof`

- [x] Fix：`doToggleShare`（toggleShare/revokeShare）、`createShare`、`listShares` 出参不再改写 attached 实体——**安全路径=detached 副本**：new 一个仅携带可暴露字段的实体副本（不挂 session、不影响持久层）返回，接口 `INopDatavDashboardShareBiz` 返回实体类型无需变更；注意引入全新 DTO 类型会改变 GraphQL 出参 schema（与「API 响应字段不变」冲突），除非 Phase 内核实 schema 影响可控否则不采用；实体本体的 passwordHash 不动；消除对「saveDirectly 不挂缓存」「查询无 flush」两处平台实现细节的隐式依赖
- [x] Proof：新增回归测试——经 `IGraphQLEngine` 以 mutation 调用 `toggleShare`（真实事务装饰器路径），事务提交后以裸 JDBC/新 session 断言 `NOP_DATAV_SHARE.PASSWORD_HASH` 仍为原 BCrypt 哈希；随后 `getSharedDashboard` 错误密码仍被拒、正确密码放行
- [x] 回归：既有 share 测试套件（TestNopDatavSharePasswordHashMasking/TestNopDatavShareManagementBizModel/TestNopDatavShareE2E 等）全绿；出参 DTO 化后 API 响应字段不变（mask 语义保留——响应不含哈希）

Exit Criteria:

- [x] `doToggleShare/createShare/listShares` 无任何对 attached 实体 mask 字段的 setter 改写（代码级可核查）
- [x] 事务路径回归测试存在且断言持久层 PASSWORD_HASH 不变 + 密码校验语义不变（测试名可引用）
- [x] 既有 share 测试全绿；`./mvnw test -pl nop-datav -am` 通过
- [x] **接线验证**：回归测试经 graphQLEngine mutation（真实事务装饰器）而非裸 bean 直调
- [x] owner-doc 裁定：若 `runtime-design.md` 等记载 mask-on-return 机制则同步更新；否则显式记录 No owner-doc update required（裁定：`permission-sharing-design.md`「密码哈希策略」增补出参副本契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 异步提交时序与可观测性（AR-2）

Status: completed
Targets: `NopDatavExportTaskBizModel.java`、`ReportDeliveryExecutor.java`、新增竞态回归测试

- Item Types: `Fix | Proof`

- [x] Fix：`createExportTask` 与 `triggerReportNow`→`ReportDeliveryExecutor` 的异步提交改为事务提交后执行——注入 `ITransactionTemplate`（bean `nopTransactionTemplate`），`isTransactionOpened()` 时 `afterCommit(...)` 注册 `submitExecution`，无事务上下文（cron/恢复路径）保持立即提交（注册前守护，避免 `ERR_TXN_NOT_IN_TRANSACTION`；形态见 Baseline API 事实）
- [x] Fix：worker 首查 null 分支（`executeTask` 与 `ReportDeliveryExecutor.runDelivery` 两侧）从静默 `return null` 改为可观测：ERROR 日志（含 taskId/deliveryId）+ `markFailedSafe` 或裁定采用的短退避重查后仍 null 才判定失败——禁止静默 no-op
- [x] Proof：新增回归测试，断言采用可观察口径（graphQLEngine mutation 返回时事务已提交、worker 消费时行必然可见——「可见性」本身无法构成缺陷锚定）：(a) afterCommit 注册语义断言——经 mutation 路径调用后 `submitExecution` 仅在事务提交后被触发（测试 seam/计数器验证注册与触发时序）；或 (b) 受控延迟提交的竞态 seam 测试（在提交前阻塞 worker 首查）；或 (c) 无事务守护分支回归——直接调用（无事务上下文）不抛 `ERR_TXN_NOT_IN_TRANSACTION` 且任务正常推进。三选一或组合，测试设计落档
- [x] 回归：既有导出/报告 E2E 测试全绿；stuck 扫描器行为不变

Exit Criteria:

- [x] 两处异步提交均发生在事务提交之后（或裁定等价机制），代码级可核查；cron/无事务路径行为不变且有守护分支（不因注册 listener 抛错）
- [x] 两处 null 分支有 ERROR 级日志与失败标记路径，无静默返回（No Silent No-Op 规则）
- [x] 回归测试采用可观察断言口径（afterCommit 注册/触发时序 seam、受控延迟竞态、或无事务守护分支——三选一或组合，测试设计已落档），非「mutation 后行可见」这类修复前也全绿的断言
- [x] `./mvnw test -pl nop-datav -am` 通过
- [x] **若改变行为契约**：`ai-dev/design/nop-datav/runtime-design.md`（导出）/ `schedule-report-design.md`（报告）事务边界小节已增补；否则明确 No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 长事务收敛（P1-05 + P1-06）

Status: completed
Targets: `NopDatavChatBiBizModel.java`、`NopDatavAlertRuleBizModel.java`、`AlertEvaluator.java`、owner docs

- Item Types: `Decision | Fix`

- [x] Decision P1-06（先行裁定，修复方案不可与 rearm 契约冲突）：现状契约（plan 2026-08-14-0937-2 Dim14-03 修复）是「`saveState(interim)` → 同步发送 → **通知成功后**才写 `lastNotifiedTime`（失败留 null 立即重试）」——`lastNotifiedTime` 只在通知成功后写是 rearm 重试语义的核心。**「通知移出事务」与「状态写在事务内不变」不可同时字面成立**（事务内代码无法感知 commit 后发送的成败），必须三选一并落档：(a) 主案：事务内写 interim 状态（不含 lastNotifiedTime）→ afterCommit 发送 → 发送成功后**新短事务**回写 lastNotifiedTime（契约保持：失败则 null 待重试；`evaluateAlertNow` 同步返回值为通知发送前的中间态——接受的语义微调须显式落档）；(b) 发送移独立异步线程（不等 commit，但脱离调用方事务），配合 (a) 的两段状态写；(c) 维持现状并记录不修的理由（默认不可接受，P1-06 是已确认缺陷）。裁定需评估 `evaluateAlertNow` 返回值消费方与 cron 路径（cron 无事务装饰，行为不变）
- [x] Decision P1-05（先行裁定，三个真正的设计决策点）：(a) 工具 `DatavGenerateDashboardExecutor` 在循环**内**即时落库（dashboard+refs+panels 多实体）并向 LLM 返回 dashboardId——循环移出事务后，多轮（maxIterations>1）间前一轮 dashboardId 是否需对后续轮可读/可查；(b) 当前 REQUIRED 事务使循环任何一轮异常全部回滚（「失败不落库」承诺）——重构成「先跑循环、落库收敛到独立短事务」隐含把 executor 从即时写改为缓冲 spec，多轮工具间原子性丢失后失败语义如何兑现（全量补偿删除 / 接受部分落库并显式标注 / 每工具独立短事务+失败补偿）；(c) 备选 SUPPORTS/无事务方案下工具内多实体写失去原子性（半成品 dashboard 风险），且无事务上下文时 dao 直写行为需核实。三点评定后选定重构形态（含「结构拆分」「传播行为调整」「最小改动=仅把整个循环+落库留在单事务但消除循环内远程等待」等候选均可考虑，以「远程调用不进事务 + 失败语义明确」为验收）
- [x] Fix P1-05：按裁定实现——AI tool-calling 循环（远程 LLM 调用）不再运行于 REQUIRED 事务内；对外行为（入参、ChatBiResult、成功路径生成落库结果）不变；失败路径语义按裁定结论显式化（不得静默部分落库）
- [x] Fix P1-06：按裁定实现——`AlertEvaluator` 手动触发路径的通知发送不在事务内；rearm 契约（lastNotifiedTime 仅在通知成功后写入）保持；cron 路径行为不变；通知失败不被静默吞（失败语义沿用既有 lastNotifiedTime 契约）
- [x] 回归：ChatBI 生成路径既有测试 + 告警手动触发/cron 既有测试全绿；按裁定补充语义断言（如 afterCommit 回写后 lastNotifiedTime 正确、失败路径 null 待重试、ChatBI 失败路径不残留半成品——按裁定结论选定）
- [x] 事务边界契约落档：触及的 owner doc（`ai-dev/design/nop-datav/ai-design.md`、`schedule-report-design.md`）增补「远程调用不进事务」裁定与两项语义裁定结论

Exit Criteria:

- [x] chatToDashboard/chatToScreen 循环内无数据库事务持有（机制可核查：方法注解/传播行为/结构拆分）；成功路径行为与修复前等价（既有测试+新增断言）；失败路径语义按裁定显式化且被测试锚定，无静默部分落库
- [x] evaluateAlertNow 链路中远程发送不在事务内；rearm 契约保持（lastNotifiedTime 仅在通知成功后写入，测试锚定）；`evaluateAlertNow` 返回值语义变化（如裁定为主案 (a)）已落档；cron 路径不回归
- [x] afterCommit 回调内的失败路径有 ERROR 级可观测锚定（平台 `invokeListener(ignoreError=true)` 会吞 listener 异常且仅打通用日志——「通知失败不被静默吞」必须由 fix 实现内的显式 ERROR 日志/回写失败记录兑现，非依赖异常传播）
- [x] 两项 Decision 的裁定结论连同拒绝方案已落档 owner doc（无未声明的设计悬空）
- [x] `./mvnw test -pl nop-datav -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 资源上界补齐（P1-04 + AR-3）

Status: completed
Targets: `DatavQueryDatasetExecutor.java`、`datav-query-dataset.tool.xml`、`PanelDataExporter.java`、（按裁定）`NopDatavConfigs.java`

- Item Types: `Fix`

- [x] Fix P1-04：maxRows 服务端钳制——入参 null/<=0 落 `CFG_DATAV_CHATBI_MAX_ROWS` 缺省，>0 取 `min(入参, 配置)`；`datav-query-dataset.tool.xml` schemaJson 补 `maximum`（与配置缺省一致）与合理 `minimum`；注释与 tool 描述同步（配置语义从「缺省值」明确为「硬上限」）。**schemaJson 与运行时配置的漂移裁定**：schema 对 LLM 仅为提示（非强制），服务端钳制才是硬防线——在 tool.xml 或 owner doc 显式声明「schema 值为缺省快照，运行时以配置钳制为准」，防止配置变更后 schema 静默漂移被误当契约
- [x] Fix AR-3：`exportDashboard` 入口面板数前置上限——复用 `CFG_DATAV_DASHBOARD_QUERY_MAX_PANELS` 或新增独立 export 侧配置（裁定后落档）；超限抛结构化错误（显式失败，非静默截断）
- [x] 回归：maxRows 传 0/负数/巨值 → 实际限行 = 配置值（断言 LongRangeBean 参数或结果行数）；exportDashboard 超限面板数 → 结构化错误；边界值（=上限）放行
- [x] 既有导出/ChatBI 测试全绿

Exit Criteria:

- [x] ChatBI 查询路径的行数上限不可被 LLM 入参绕过（测试锚定 0/负数/巨值三态）
- [x] 导出路径面板数有显式上限校验，超限结构化失败；与其他三路径（查询/布局/ChatBI 生成）防护对称性落档 owner doc（runtime-design.md）
- [x] `./mvnw test -pl nop-datav -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] AR-1/AR-2/AR-3/P1-04/P1-05/P1-06 六项 live defect 全部修复且各有事务路径/行为回归锚定
- [x] 至少 2 条常驻「graphQLEngine mutation 真实事务路径 + 持久层副作用断言」回归测试（AR-1、AR-2 各一）
- [x] 全部修复行为语义与修复前等价（除缺陷行为本身），既有测试套件全绿
- [x] 无任何静默 no-op 分支被引入或保留在修复面内（null 分支可观测化）
- [x] owner docs（runtime-design/schedule-report-design/ai-design）事务边界与上界契约已同步
- [x] 独立子 agent closure audit 已完成并写入 Closure 段落（含 Anti-Hollow 检查：afterCommit/事务边界重构真实生效而非注解摆设）
- [x] `./mvnw test -pl nop-datav -am` 通过
- [x] `./mvnw clean install -pl nop-datav -am -DskipTests` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（无——本 plan 范围内无允许延期的已确认缺陷）

## Non-Blocking Follow-ups

- AR-2 竞态的「人为延迟 commit」确定性复现基建若成本过高，允许以 afterCommit 注册时序断言替代（Phase 2 已列），完整竞态基建 → `ai-dev/backlog/nop-datav-audit-followups.md`。
- AR-4/AR-5/AR-6/AR-7（open-audit P2）→ follow-up backlog。
- Closure audit Minor 观察（非缺陷，无行动项）：ChatBI 结果提取 handler 对 content 解析失败沿用既有容忍语义（content 为本模块 executor 自产 JSON，实际不可达失败）。

## Closure

Status Note: 六项缺陷（AR-1 P0 / AR-2 P1 / AR-3 P1 / P1-04 / P1-05 / P1-06）全部修复并各有事务路径/行为回归锚定；两项先行裁定（P1-05 三决策点、P1-06 主案 (a)）连同拒绝方案落档 owner docs；「graphQLEngine mutation 真实事务路径 + 持久层副作用断言」测试资产沉淀 2 条（AR-1 toggleShare / AR-2 createExportTask）；实现期捕获两项平台机制事实（afterCommit listener 期间外层事务仍注册 → 回写/补偿必须 REQUIRES_NEW；子类 shadowing @Inject 字段被基类 setter 注入绕过）已随修复落档。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，research-only）task_id `ses_ff754b301ffewfjGhKGvEm4oMH`
- Evidence:
  - **Phase 1（AR-1）9/9 项 PASS**：`NopDatavDashboardShareBizModel.java:263-277`（doToggleShare 仅业务列写入 + `toSanitizedView` detached 副本返回；createShare :128 / listShares :145-149 同款）；`TestNopDatavShareToggleTransactionPath.java:90-124`（裸 JDBC BCrypt 字节相等断言 + 错误密码拒绝/正确密码放行）；mutation 接线经 `GraphQLTransactionOperationInvoker`（:29-30）实证；`permission-sharing-design.md:255` 出参副本契约落档。
  - **Phase 2（AR-2）7/7 项 PASS**：`NopDatavExportTaskBizModel.java:260-267`（`txn()` 复用 CrudBizModel 注入、无影子字段）+ `:286-293`（null 分支 ERROR+markFailedSafe）+ `:376-388`（短退避）；`ReportDeliveryExecutor.java:162-170/:219-225/:442-455` 对称；`TestNopDatavAsyncSubmitTransactionPath.java`：事务内 seam==0 / commit 后 +1 / 回滚不触发（export :117-165 + report :200-246）、无事务立即提交 + SUCCEEDED（:174-189）、graphQLEngine mutation E2E 裸 JDBC 轮询至 SUCCEEDED（:255-289）。
  - **Phase 3（P1-05+P1-06）6/6 项 PASS**：`NopDatavChatBiBizModel.java:171-176`（runWithoutTransaction 挂起）+ :406-413/:483-490（try/catch → 补偿 → 原样上抛）；executor 短事务包裹（Dashboard :189-195 / Screen :369-375）；`AlertEvaluator.java:128/:313-335/:342-369`（defer 判定 / afterCommit 发送失败 ERROR 不回写 / REQUIRES_NEW 重载回写）；cron 路径零事务机械（rg 实证）；Anti-Hollow 测试断言 (i)-(iv) 逐条核对（`TestNopDatavChatBiTransactionBoundary.java:112-121/:136-227/:233-254`）；`TestNopDatavAlertNotifyTransactionBoundary.java:101-190/:200-212`。
  - **Phase 4（P1-04+AR-3）5/5 项 PASS**：`DatavQueryDatasetExecutor.java:175-185`（clampMaxRows）+ `:194`（range 恒非 null）；tool.xml schemaJson minimum/maximum + 漂移措辞（XML well-formed 实证）；`PanelDataExporter.java:178-187`（上界先于任何面板取数，:192-195 为取数循环）；`runtime-design.md` §十一 四路径对称表；钳制/上界测试（0/负/巨/缺省 + 边界）全数断言。
  - **Closure Gates 8/9 PASS + audit 本项**：599/0/0（`./mvnw test -pl nop-datav -am` BUILD SUCCESS）；`./mvnw clean install -pl nop-datav -am -T 1C -DskipTests` BUILD SUCCESS；`check-doc-links.mjs --strict` 退出码 0（0 errors，6 条均为无关 plan 338 的既有 warning）；`scan-hollow-implementations.mjs --module nop-datav --severity high` 退出码 0（0 findings）。
  - **Anti-Hollow 检查结论 GENUINE**：四条调用链（createExportTask→afterCommit、triggerReportNow→execute→afterCommit、evaluateAlertNow→evaluate→afterCommit→REQUIRES_NEW 回写、chatToDashboard/chatToScreen→runWithoutTransaction→补偿删除）全链路 live 追踪连通，且有 seam/计数器/裸 JDBC 断言证明运行时真实触发（非注解摆设）。
  - **生成物零触碰**：`git status` 无 `/_gen/`、`_*.xml`、`_app.orm.xml` 等生成物路径（仅 nop-datav src/main+test、ai-dev docs/plans/logs、手写 `_vfs/nop/ai/tools/datav-query-dataset.tool.xml` 源文件）。
  - Findings：Blocker 0 / Major 0 / Minor 2（测试文件目录-包不一致已当场修复迁移；handler 解析容忍为既有语义观察项，无行动）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（本 Closure 段填写后复跑确认）。

Follow-up:

- no remaining plan-owned work（Non-Blocking Follow-ups 所列三项均为已裁定 backlog/观察项，非本 plan 残留）
