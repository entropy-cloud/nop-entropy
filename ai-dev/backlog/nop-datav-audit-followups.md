# nop-datav Audit Follow-up Backlog (P2)

> Status: backlog（P2-only items，不发起独立 remediation plan）
> Sources: `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md`（multi，P2×10）、`ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md`（open，P2×7 + nits）、`ai-dev/audits/nop-datav/2026-08-15-1913-multi-audit-nop-datav.md`（multi，P2×48，#18-65）、`ai-dev/audits/nop-datav/2026-08-15-1913-open-audit-nop-datav.md`（open，P2×4，#66-69）
> Rules: 每条标注 source audit 路径与 finding ID 以保持可追溯；P2 非降解项，但优先级低于 P0/P1（已进 plan）。
> Resolved: #1 ✅ #2 ✅ #3 ✅ #4 ✅ #5 ✅ #6 ✅ #7 ✅ #8 ✅ #9 ✅ #10 ✅ #11 ✅ #12 ✅ #13 ✅ #14 ✅ #15 ✅ #16 ✅ #17 ✅（plan `ai-dev/plans/nop-datav/2026-08-14-0950-1-p2-audit-backlog-cleanup.md` 已 completed 2026-08-14）

## From `2026-08-10-1516-multi-audit-nop-datav.md`

| # | Finding | Source | Note |
|---|---------|--------|------|
| 1 | ✅ Dim14-03 — `AlertEvaluator` 持久化 TRIGGERED+lastNotifiedTime 在 `sendAlertNotification` 之前；SMTP 失败时告警被记为「已通知无错」且无通知发出（drift vs design §269-273） | `ai-dev/audits/nop-datav/2026-08-10-1516-multi-audit-nop-datav.md` §Dim14-03 | **已修复** plan `2026-08-14-0937-2` Phase 1：通知成功后再设 lastNotifiedTime；失败留 lastNotifiedTime=null 立即重试 |
| 2 | ✅ Dim14-04 — 调度器 `catch (Throwable)` 含 OOM/Error，违背 design §3/§9「基础设施错误应传播」 | `…multi-audit…` §Dim14-04 | **已修复** plan `2026-08-14-0937-2` Phase 2：`catch (Exception)` 让 `Error` 传播 |
| 3 | ✅ Dim09-02 — `AlertThresholdComparator`/`AlertAggregator` 配置错误用裸 `IllegalArgumentException` 到达公共 `evaluateAlertNow` | `…multi-audit…` §Dim09-02 | **已修复** plan `2026-08-14-0937-2` Phase 3：换 `NopException(ERR_DATAV_ALERT_VALUE_REQUIRED/UNSUPPORTED_OPERATOR/UNSUPPORTED_AGGREGATION)` |
| 4 | ✅ Dim09-03 — `createExportTask` 空 sourceType/sourceId 用 `ERR_DATAV_EXPORT_TASK_NOT_FOUND` + ARG mismatch | `…multi-audit…` §Dim09-03 | **已修复** plan `2026-08-14-0950-1` Phase 2：定义 `ERR_DATAV_EXPORT_MISSING_SOURCE`；空源检查前移到 `validateFormat` 之前 |
| 5 | ✅ Dim09-04 — `ERR_DATAV_EXPORT_FAILED` / `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` 定义但无引用 | `…multi-audit…` §Dim09-04 | **已修复** 导出侧由 Plan {2}（Dim07-01）退役 `ERR_DATAV_EXPORT_FAILED`；chatbi 侧由 plan `2026-08-14-0937-3` Phase 1 接线 `ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED`（`.join()` try/catch 抛出） |
| 6 | ✅ Dim09-05 — 调度器缺 job 参数抛裸 `IllegalArgumentException`（被外层 catch 吞） | `…multi-audit…` §Dim09-05 | **已修复** plan `2026-08-14-0937-2` Phase 2：换 `NopException(ERR_DATAV_ALERT_RULE_NOT_FOUND).param(ARG_ALERT_RULE_ID, "(absent from job params)")` |
| 7 | ✅ Dim04-01 — `NopDatavScreenSnapshot.snapshotContent` 缺 `mandatory="true"`（screen-design §1.3） | `…multi-audit…` §Dim04-01 | **已修复** plan `2026-08-14-0950-1` Phase 1：ORM 加 `mandatory="true"`，生成物已同步 |
| 8 | ✅ Dim04-02 — `NopDatavScreenSnapshot` UK 名 `SCR_VER` 与设计 `SCREEN_VER` 不符（screen-design §84） | `…multi-audit…` §Dim04-02 | **已修复** plan `2026-08-14-0950-1` Phase 1：重命名 UK 为 `SCREEN_VER`，生成物已同步 |
| 9 | ✅ Dim16-02 — 告警「panel 存在但 queryPanelData 抛错」分支无独立测试 | `…multi-audit…` §Dim16-02 | **已修复** plan `2026-08-14-0937-2` Phase 4：testPanelQueryFailureRecordsErrorAndPreservesState |
| 10 | ✅ Dim16-03 — 报告交付「导出中途失败」路径无测试 | `…multi-audit…` §Dim16-03 | **已修复** plan `2026-08-14-0937-2` Phase 4：testReportDeliveryExportFailureRecordsError（异步轮询） |

## From `2026-08-10-1516-open-audit-nop-datav.md`

| # | Finding | Source | Note |
|---|---------|--------|------|
| 11 | ✅ AR-1 — `nop-datav-core` 孤儿模块（261 行生成常量），service 层手写重复状态常量，single-source-of-truth 已侵蚀 | `ai-dev/audits/nop-datav/2026-08-10-1516-open-audit-nop-datav.md` §AR-1 | **已修复** plan `2026-08-14-0950-1` Phase 3：裁定删除目录（codegen 产出无消费者） |
| 12 | ✅ AR-2 — `nop-datav-chart` 空壳模块 + 绕过 nop-bom 的硬编码 `poi:5.4.0` | `…open-audit…` §AR-2 | **已修复** plan `2026-08-14-0950-1` Phase 3：从 `<modules>` 删除 + 删目录 |
| 13 | ✅ AR-3 — `DatavGenerateScreenExecutor.isUniqueConstraintViolation` 子串过宽（`constraint`/`uk_`），把任意约束错误误报为重名 | `…open-audit…` §AR-3 | **已修复** plan `2026-08-14-0937-3` Phase 3：收窄为仅 `unique`/`duplicate`，移除 `constraint`/`uk_`（三方言 UK 仍正确命中） |
| 14 | ✅ AR-4 — `DatavQueryDatasetExecutor` 复用 `ERR_DATAV_QUERY_FAILED` 但 param `panelId` 误填 datasetSid（ChatBI 无 panel） | `…open-audit…` §AR-4 | **已修复** plan `2026-08-14-0937-3` Phase 2：新增 `ERR_DATAV_CHATBI_DATASET_QUERY_FAILED`（ARG_DATASET_SID + ARG_REASON），保留 `.cause(e)` |
| 15 | ✅ AR-5 — 两 ChatBI generate-executor 内联 `SYSTEM_OPERATOR="system"` 并附「避免循环依赖」假理由，真常量在同模块 `NopDatavOperatorResolver` | `…open-audit…` §AR-5 | **已修复** plan `2026-08-14-0937-3` Phase 2：两 executor 引用 `NopDatavOperatorResolver.SYSTEM_OPERATOR`，删 `NopOperatorFallback` 内联类 |
| 16 | ✅ AR-6 — `ChatBiToolCallingLoop.callTool().join()` 可泄漏 `CompletionException`；handler 失败双层静默吞；`ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED` 未用 | `…open-audit…` §AR-6 | **已修复** plan `2026-08-14-0937-3` Phase 1：`.join()` try/catch（CompletionException+CancellationException）解包后抛结构化 NopException（含 ARG_TOOL_NAME+ARG_REASON）；两处 handler-swallow 加 `LOG.debug`；新增 Logger 字段 |
| 17 | ✅ AR-7 — `DatavListDatasetsExecutor.findAll()` 全列（含 dsText/dsMeta CLOB）加载后内存过滤 keyword | `…open-audit…` §AR-7 | **已修复** plan `2026-08-14-0950-1` Phase 2：`status=1` 下推 SQL（QueryBean+FilterBeans），keyword 仍在内存过滤活跃集 |

## Minor nits (open-audit)

- ✅ `DatavGenerateDashboardExecutor.generateId(prefix)`（`:392-397`）`prefix` 参数未使用 — 用或删。来源：`…open-audit…` Minor nits。**已修复** plan `2026-08-14-0950-1` Phase 2：删除 prefix 参数，3 调用点同步。
- ✅ `ChatBiToolCallingLoop.QUERY_HANDLER`（`:203-204`）`parseNonStrict` 同三元里调两次 — 解析一次入局部。来源：同上。**已修复** plan `2026-08-14-0950-1` Phase 2：提取 `parsedObj` 局部变量。
- `nop-datav-api` 空模块（`gen-crud-api.xgen` 全注释）— 作为 `nop-datav-dao` 占位依赖合法；若永久弃用 CRUD-API 生成可删依赖。来源：同上。

## From `ai-dev/audits/nop-datav/2026-08-15-1913-multi-audit-nop-datav.md`（2026-08-15 批次，P2×48）

> P0/P1 已入 remediation plans `ai-dev/plans/nop-datav/2026-08-15-2146-1-panel-subentity-auth-rbac-closure.md`（P0-01/02、P1-01/02/03/10，**已 completed 2026-08-16**）、`2026-08-15-2146-2-transaction-boundary-async-resource-bounds.md`（P1-04/05/06 对应项，**已 completed 2026-08-16**）、`2026-08-15-2146-3-ddl-integrity-cascade-hygiene.md`（P0-03、P1-07/08/09/11/12）。以下为 P2 backlog。

### 结构与构建

| # | Finding | Source | Note |
|---|---------|--------|------|
| 18 | P2-01 — nop-datav-core 孤儿目录（271 行 dict 常量，无 pom、不在 `<modules>`、零引用，codegen 每次构建再生；根因 `model/nop-datav.orm.xml:6` `ext:useCoreModule="true"`） | `ai-dev/audits/nop-datav/2026-08-15-1913-multi-audit-nop-datav.md` §P2-01 | 改 `false` 删目录或补 pom 接线（对照 nop-job-core 先例） |
| 19 | P2-02 — dao 对空 api 模块的 compile 依赖空转（`nop-datav-dao/pom.xml:26-29`） | 同上 §P2-02 | 移除依赖或加说明 |
| 20 | P2-03 — 11 个手写 DTO 与 17 个 I*Biz 混放 dao 的 `io.nop.datav.biz` 包 | 同上 §P2-03 | 排期迁 `dao.dto` |
| 21 | P2-04 — dict 值常量四份平行定义（`NopDatavDashboardBizModel.java:85-91` 等） | 同上 §P2-04 | 以 NopDatavReportTaskStatus 模式收敛单一来源 |
| 22 | P2-05 — model-design.md 声称与不存在的 nop-datav-chart 共存于同一父 pom（`ai-dev/design/nop-datav/model-design.md:78-80`） | 同上 §P2-05 | 文档漂移修正 |

### 权限/安全加固（非阻塞残余）

| # | Finding | Source | Note |
|---|---------|--------|------|
| 23 | P2-06 — 外部跳转 URL 无 scheme 白名单、模板值未编码（`linkage/LinkageExecutor.java:122-126,184-193`） | 同上 §P2-06 | javascript: 面 + 参数注入；与 plan 1 收口后风险降级 |
| 24 | P2-07 — 分享限流单节点 LocalCache + 容量驱逐可冲掉锁定 + `nop-client-addr` 可伪造（`share/NopDatavShareAccessGuard.java:31-34` 等） | 同上 §P2-07 | 设计文档 R1/R2 已声明为接受残余/部署边界 |
| 25 | P2-08 — CSV 导出无公式注入防护（`export/PanelDataExporter.java:195-221`） | 同上 §P2-08 | `=cmd|`/`=HYPERLINK` 形式扩散 |
| 26 | P2-09 — docs-for-ai 全目录零收录 nop-datav（module-groups/INDEX/source-anchors 均 0 命中） | 同上 §P2-09 | 17 实体 48 action 模块无 AI 路由锚点，按 Mandatory Updates 补录 |

### ORM 细项

| # | Finding | Source | Note |
|---|---------|--------|------|
| 27 | P2-10 — 三个外键列无 relation 定义（Panel.tabId、Panel.datasetRefId、ScreenWidget.datasetRefId；`model/nop-datav.orm.xml:201-205,727-729`） | 同上 §P2-10 | 后者跨聚合，Dashboard 删 DatasetRef 时悬挂 |
| 28 | P2-11 — AlertState 冗余索引 + UK 命名前缀不一致（UQ_ vs UK_；`orm.xml:1183-1193`） | 同上 §P2-11 | **随 plan `2026-08-15-2146-3` Phase 2（D3）顺手收敛**，非独立 backlog 处理 |
| 29 | P2-12 — 快照内容列 mandatory 不一致（DashboardSnapshot 可空 vs ScreenSnapshot 非空；`orm.xml:387-389` vs `798-800`） | 同上 §P2-12 | |
| 30 | P2-13 — 17 实体全量声明 delFlag 列但均未接线 deleteFlagProp | 同上 §P2-13 | 物理删除策略下形同虚设 |
| 31 | P2-14 — dashboardName/screenName 全局唯一键作用域待裁定（`orm.xml:171-174,695-698`） | 同上 §P2-14 | **随 plan `2026-08-15-2146-3` Phase 1（D1）作为 P0-03 前置裁定消化**，非独立 backlog 处理 |

### 错误处理/类型卫生

| # | Finding | Source | Note |
|---|---------|--------|------|
| 32 | P2-15 — existsTable 辅助方法 catch(Exception)→return false 静默吞异常 ×4（`alert/NopDatavAlertScheduler.java:143-152` 等） | 同上 §P2-15 | |
| 33 | P2-16 — 容错解析路径丢异常不留证 ×8（5 处零日志 + 2 处仅 DEBUG；`NopDatavChatBiBizModel.java:248-250` 等） | 同上 §P2-16 | AlertEvaluator.parseParams 非法 JSON 降级有错误触发风险 |
| 34 | P2-17 — codec/parser rethrow 丢失异常链 ×7（无 `.cause(e)`；`DashboardFilterUrlCodec.java:99-115` 等） | 同上 §P2-17 | |
| 35 | P2-18 — 4 个 ErrorCode 死码 + 2 处 javadoc 错误码漂移 + 1 处 ARG 死声明（`NopDatavErrors.java:185-189` 等） | 同上 §P2-18 | 可与 plan 3 Phase 4 错误码改造顺带排查，但不在其 scope |
| 36 | P2-19 — getDashboardData 链 4 处 IllegalStateException 防御性包装（`NopDatavDashboardBizModel.java:597,620,632,655`） | 同上 §P2-19 | |
| 37 | P2-20 — PanelSqlBuilder 占位符缺参错误不带 paramName 且错误码语义错配（`PanelSqlBuilder.java:52-55`） | 同上 §P2-20 | 与 plan 3 Phase 5 单测补写相邻，可顺带但不在 scope |
| 38 | P2-21 — 导出任务非 NopException 失败无堆栈日志（`NopDatavExportTaskBizModel.java:276-291`） | 同上 §P2-21 | |
| 39 | P2-22 — findAllByQuery 系统性冗余强转 + @SuppressWarnings ×11（`NopDatavAlertScheduler.java:126-127` 等） | 同上 §P2-22 | |

### IoC/API 层

| # | Finding | Source | Note |
|---|---------|--------|------|
| 40 | P2-23 — app-service.beans.xml 使用 `ioc:` 属性但未声明 xmlns:ioc（20 处；`app-service.beans.xml:2-13`） | 同上 §P2-23 | 运行时无影响，偏离仓库惯例 |
| 41 | P2-24 — 2 个 bean 缺 ioc:default + 4 个通用名 bean 无模块前缀（`app-service.beans.xml:21,28,45-46,110-111`） | 同上 §P2-24 | |
| 42 | P2-25 — refreshPanel 纯读操作标记 @BizMutation（`NopDatavPanelBizModel.java:107-118`） | 同上 §P2-25 | 与 plan 1 Phase 2 触及同方法但注解语义不在其 scope |
| 43 | P2-26 — 5 个自定义列表 action 无分页无上限（`NopDatavReportTaskBizModel.java:150-153` 等） | 同上 §P2-26 | |
| 44 | P2-27 — publish/rollback 主表 JDBC 直更不递增乐观锁 VERSION（`NopDatavDashboardBizModel.java:971-990`、`NopDatavScreenBizModel.java:435-456` vs `:295-300`） | 同上 §P2-27 | |
| 45 | P2-28 — 交互式面板查询路径无行数上限（`query/PanelDataBinder.java:74-86,174-180`） | 同上 §P2-28 | javadoc 明示为设计决定，防护不对称 |

### BizModel/契约细项

| # | Finding | Source | Note |
|---|---------|--------|------|
| 46 | P2-29 — 9 个 dao 层 GraphQL 返回 DTO 缺 @DataBean（`nop-datav/nop-datav-dao/src/main/java/io/nop/datav/biz/DashboardDataResult.java` 等） | 同上 §P2-29 | |
| 47 | P2-30 — saveDashboardLayout 等弱类型 Map 契约（5 返回 + 8 参数） | 同上 §P2-30 | |
| 48 | P2-31 — rollbackDashboard 契约表漂移（恢复 paramConfig，model-design.md:39 漏列） | 同上 §P2-31 | |
| 49 | P2-32 — parseFilterFromUrl 丢弃 requireEntity 返回值二次加载（`NopDatavDashboardBizModel.java:305-317`） | 同上 §P2-32 | |
| 50 | P2-33 — ExportTask 实体直接 new 而非 dao.newEntity()（`NopDatavExportTaskBizModel.java:446`） | 同上 §P2-33 | |
| 51 | P2-34 — 3 个大 BizModel 全限定注入注解/字段类型 | 同上 §P2-34 | 风格噪音 |
| 52 | P2-35 — chatToDashboard/chatToScreen 错误 param 名 question 承载 description（`NopDatavChatBiBizModel.java:210-214,268-272`） | 同上 §P2-35 | |
| 53 | P2-36 — triggerReportNow 返回裸 String 无 javadoc；getAlertState 可返回 null 未标注 | 同上 §P2-36 | |
| 54 | P2-37 — 6 个调度类 action 零测试零消费者（enable/disable×4、getAlertState、getReportDeliveryHistory） | 同上 §P2-37 | 有设计记录的「待接线」 |
| 55 | P2-38 — NotificationSender 直接 import nop-integration-api 5 类型但 pom 未显式声明 | 同上 §P2-38 | 契约卫生 |
| 56 | P2-39 — dashboardType 列 + dict 零消费零文档（`orm.xml:10-13,134-136`） | 同上 §P2-39 | 死配置位 |

### 测试细项

| # | Finding | Source | Note |
|---|---------|--------|------|
| 57 | P2-40 — 4 个调度注册测试静默跳过模式（`if==null return`；`TestNopDatavAlertE2E.java:762-782` 等） | 同上 §P2-40 | |
| 58 | P2-41 — 5 处 E2E 错误路径断言未锚定错误码 | 同上 §P2-41 | |
| 59 | P2-42 — 报告邮件附件内容零验证（`TestNopDatavReportE2E.java:171-172`） | 同上 §P2-42 | |
| 60 | P2-43 — TestPanelComponentRegistry 元数据镜像断言（`TestPanelComponentRegistry.java:63-111`） | 同上 §P2-43 | |
| 61 | P2-44 — TestChatBiToolCallingLoop 纯逻辑测试继承重量级集成基类 | 同上 §P2-44 | 违反 testing.md 基类选择规则 |

### 文档漂移

| # | Finding | Source | Note |
|---|---------|--------|------|
| 62 | P2-45 — permission-sharing-design.md 错误码漂移（承诺 ERR_DATAV_PANEL_NOT_FOUND，实际 ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS；`:375`） | 同上 §P2-45 | 与 plan 1 的 §53-65 修正不同处，可顺带但不在 scope |
| 63 | P2-46 — permission-sharing-design.md 重启清理机制漂移（文档说 BizModel 实现 IInitializer，实际为独立 Recovery bean；`:337`） | 同上 §P2-46 | |
| 64 | P2-47 — permission-sharing-design.md 测试配置路径漂移（/nop/datav/auth/* vs 实际 /test/datav/auth/*；`:267-269`） | 同上 §P2-47 | |
| 65 | P2-48 — 设计目录 README 状态表全量 stale（7 份文档全标 stub，实际全 final；`ai-dev/design/nop-datav/README.md:5-13`） | 同上 §P2-48 | |

## From `ai-dev/audits/nop-datav/2026-08-15-1913-open-audit-nop-datav.md`（2026-08-15 批次，P2×4）

> AR-1（P0）/AR-2/AR-3（P1）已入 plan `2026-08-15-2146-2-transaction-boundary-async-resource-bounds.md`（**已 completed 2026-08-16**）。以下为 P2 backlog。

| # | Finding | Source | Note |
|---|---------|--------|------|
| 66 | AR-4 — ChatBiSessionManager.nextSeq 每轮全量加载会话全部消息（含 CLOB）只求 max(seq)+1（`chatbi/ChatBiSessionManager.java:253-259`） | `ai-dev/audits/nop-datav/2026-08-15-1913-open-audit-nop-datav.md` §AR-4 | 改 `setLimit(1)+findFirstByQuery`（同文件 findLatestSnapshot 先例） |
| 67 | AR-5 — 多轮 chatToQuery @BizQuery 三段写库无事务原子性 + 写路径逃过 mutation 审计（`NopDatavChatBiBizModel.java:110-142`、`ChatBiSessionManager.java:137-159`） | 同上 §AR-5 | appendTurn 包单事务 + 审计 pattern 评估 |
| 68 | AR-6 — 审计 mutation pattern 覆盖不对称：Screen/ChatBI 生成/ReportTask/AlertRule/ExportTask 全缺位（`nop-datav-app/application.yaml:25-26`） | 同上 §AR-6 | 补 `NopDatavScreen__*` 等 pattern |
| 69 | AR-7 — DashboardPanelQueryCache 缓存键无用户维度：当前正确，但与 plan 1 P1-03 修复形成定时耦合（`query/DashboardPanelQueryCache.java:57-61`） | 同上 §AR-7 | **已作为 plan 1 Phase 4 Exit Criteria 的强制裁定项登记**；键前提 javadoc 锚点仍待补 |
