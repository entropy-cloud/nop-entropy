# file 去重引用计数原子化/datav 终态 fencing 与取消竞态/查询上界与调度生命周期并发契约（R3-03 5.3 file-datav 批 14 成员+测试缝裁定）

> Status: draft（plan 2526 Phase 1 产出物，待独立 draft review；评审通过转 active 后为该批实施唯一设计裁决权威）
> Source: 批次成员表、PA 判定、勘误登记与红绿判据以执行计划 `ai-dev/plans/2526-cra-phase8.33-r3-03-file-datav.md` 为准（本文档为最终状态文档，勘误不落此处）；成员发现详情 = WI 5.3 审计目录 `ai-dev/audits/2026-10/2026-10-21-1227-deep-audit-nop-file-datav-concurrency/`（merge-adjudication §三 缺陷族 :25-34 / §四 冲突裁定 3 项 :36-40 / §五 跨单元关联 :42-52 / §六 权威表 :54-66 / §七 结构性发现 :68-73）；行号 = live @ `b724110569` 全文亲读核实值（plan 基线 `9ab4a0f4de` 后仅 plan 立项提交，零成员面漂移——§八勘误 1）
> 实现锚：nop-file-dao `DaoResourceFileStore`（detachFileInSession :373-390[:381 deleteEntity / :382 isUniqueRef / :383 removeResource 判-删序列] / isUniqueRef :392-401 / copyFile :442-460[:444 requireEntityById 读记录 / :449 setOriginFileId / :453 setFilePath 共享路径] / removeTempFileByOwner :156-171[:165 deleteEntity / :167 isUniqueRef / :168 removeResource 同模式] / removeResource :262-270[CL-017 P3 站点零触碰] / saveFile :184-245[新路径新 fileId 无 dedup 无涉] / detachFile :362-371 与 attachFile :421-439[plan 2386 权限钉零回退]）；nop-datav-service `NopDatavExportTaskBizModel`（cancelFlags :119 / executionStartHook :138 / createExportTask :149-184[:166 checkConcurrencyLimit / :174 saveEntityDirectly] / cancelExportTask :222-235[:225 isTerminal 读 / :228 put / :230-233 CANCELLED 写] / submitExecutionAfterCommit :261-268[AR-2 afterCommit 先例] / executeTask :284-370[:301-303 RUNNING 写 / :308 seam / :335 saveExportFile / :339-342 cancel-pre-SUCCEEDED fileId 丢弃 / :344-349 SUCCEEDED 无条件写 / :350-365 catch FAILED / :367 finally remove] / markCancelled :419-426 / markFailedSafe :428-445[isTerminal 守卫同族已修对照] / checkConcurrencyLimit :497-513[:507 countByQuery]）、`ReportDeliveryExecutor`（runDelivery :201-209[Part A :203-204 / Part B :207] / runDeliveryInSession :215-293[:249-251 RUNNING 加载后无终态复验 / :266-268 saveReportFile+fileId / :271-275 SUCCEEDED 无条件写 / :280-292 catch FAILED] / sendNotificationOutOfSession :299-316[:302 sendReport / :309 rollbackSucceededToFailed] / rollbackSucceededToFailed :340-363[有意覆盖终态——设计声明内豁免]）、`NopDatavDashboardBizModel`（handleDeleteCascade :126-162[:134 disableReportTasksForDashboard 内嵌 :159 unregisterTask / :135 disableAlertRulesForPanels / :136 revokeShares / :138-142 deleteByQuery] / disableAlertRulesForPanels 第二调用面 saveDashboardLayout :772 / newPanelQueryTask :583[rowLimit=null] / calculateNextVersion :920-928[:926 findFirstByQuery 读 max]）、`NopDatavScreenBizModel`（publishScreen :108 / getScreenSnapshotHistory :221-233[:225 findAllSnapshots] / calculateNextVersion :356-364 / findAllSnapshots :385-390 无 LIMIT 整行含 SNAPSHOT_CONTENT）、`AlertEvaluator`（evaluate :121 / Dim14-03 NULL 注释 :195-197 / rearm 判定 :236-248 / createInitialState :262 / loadOrCreateState :278-289[:283 findFirstByQuery / :288 createInitialState] / saveState :291 / notifyWithRearmContract :314 / runDeferredWriteback :343[REQUIRES_NEW fresh 重载后无条件回写] / shouldRearm :380[null→true]）、`ChatBiSessionManager`（listSessions :96-101[CL-022 零触碰] / loadMessages :124-129[:128 findAllByQuery 无 LIMIT，seq 升序] / appendTurn :138-160）、`NopDatavChatBiBizModel`（chatToQuery :296-304[:297 buildHistoryContext 裁剪后置于全量加载] / getChatSessionHistory :352-358 全量直接返回）、`PanelDataBinder`（javadoc「null 表示不限制」:81 / 重载 :83-85/:100-101 / rowLimit==null 分支 :175 / extractRows :207-214 全量物化）、`PanelDataExporter`（checkCancelled :90 / writeCsv :215-241[:218 getTempResource / :229 checkCancelled 抛点 / :235 NopException.adapt / :237-238 finally 只关流] / writeXlsx(singleSheet) :243-246 委托 / writeXlsx(workbook) :249-258[:250 getTempResource / :253-255 throw 无 delete] / maxRows+1 探测先例 :117-123/:195-199[ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED]）、`NopDatavPanelBizModel`（getPanelData/refreshPanel :136/:150 传 null）、`NopDatavReportTaskBizModel`（afterEntityChange :71-79[register/unregister 在事务内] / doDeleteEntity :93-110[:96-98 unregister 后 :99-102 deleteByQuery]）、`NopDatavReportScheduler`（init :116-147[:141 register-failed catch] / registerTask :152-168 / unregisterTask :185-190）、`NopDatavStuckTaskScanner`（init :91-110[:103 activate / :104 addJob / :108 catch 仅 LOG.error]）、`NopDatavAlertScheduler`（init :110-141[:135 catch]）、recovery 双 bean（export :58-65 init catch warn / doScanStuck :112-133 无条件 updateEntityDirectly；report :58-65 / doScanStuck :108-131[状态过滤仅 pending/running——SUCCEEDED 跳过]）；源注册面 `NopDatavConfigs`（四限额先例 :17/:21/:35/:53/:67 + 缓存准入 :127-129）与 `NopDatavErrors`（导出限额错误码 :355）；状态机枚举 `NopDatavExportTaskStatus`（PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED，isTerminal=后三）与 `NopDatavReportDeliveryStatus`（PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED，isTerminal=后三，无 CANCELLED——交付无用户取消路径）；平台设施 live 核实：`IEntityDao.lockEntity`（nop-dao :129-134 javadoc「使用 select for update 来锁定单条记录」）→ `OrmSessionImpl.lock`（nop-orm :654 → persister.lock）——**dao 层 FOR UPDATE 语义在案**（in-repo 先例 `SysSequenceGenerator.doSyncFromDb` :287 `session.lock(seq)`）；条件更新平台原生形态 = `orm_disableVersionCheckError(true)` + `updateEntityDirectly`（genUpdateSql 恒带 `AND version=?` 隐式 CAS）+ `orm_readonly()` 0 行判定（plan 2525 已落地先例 `SysDaoLeaderElector.tryRenewLease` :118-128 / `changeLeader` :203-213）；datav 全实体 `versionProp="version"` 在案（nop-datav.orm.xml :120/:183/:252/:314/:375/:441/:502/:576/:641/:709/:786/:854/:949/:1027/:1130/:1203/:1256）；`IResourceLockManager`（nop-commons，getLock/callWithLock/runWithLock）、`ISemaphore`（io.nop.commons.concurrent.semaphore，tryAcquire(int,long)/release(int)）、`ITransactionTemplate.afterCommit`（nop-dao :87，模块内先例 submitExecutionAfterCommit :261-267）；`IFileStore`（nop-biz-file-core）公共面 = saveFile/saveFileAsync/getFileLink/getFile 四方法，**无删除/detach 方法**（裁定⑤ Option B 申报面事实源）；nop-datav-service pom 直接依赖 nop-file-dao（:43-48，NopFileRecord dao 实体可达——裁定⑤ Option A 可行性事实源）；`TestNopDatavErrorsI18n` 在案（新错误码须带 i18n 条目）
> Related: [sys-dao-coordination-fencing-dyn-model-cache-and-sequence-concurrency.md](sys-dao-coordination-fencing-dyn-model-cache-and-sequence-concurrency.md)（plan 2525——本文结构模板与密度基准 + R12 条件更新纪律的直接先例：elector `orm_disableVersionCheckError` CAS 形态即裁定③④的条件更新载体）；[ledger-retention-policy.md](ledger-retention-policy.md)（R01 族 SSOT——CL-012/CL-013 保留族先例，裁定⑪快照保留数上界不扩张的归属注记）；plan 2386（completed——P1-3.8-001/002 attach/detach 权限钉，W-01 修复零回退底座）；plan 2501（completed——ChatBI 工具执行器 20-red 面成因批，本批 Non-Goals 零触碰）；R1-01/R2-11/R2-13（CL-020 平台绝缘层归属批 / W-04 R02 三范式对齐批——引用不重开）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 5.3-W-01（P1，CL-014；C10 缺保护-1 同 diff） | **两段式延迟清理**：isUniqueRef 命中后不在请求路径立即 removeResource——登记待清理条目（filePath+originFileId）→ 延迟任务（进程内单线程调度，宽限间隔默认 60s）复验「同 originFileId 零引用」后删物理文件，复验时引用>0 即取消删除；removeTempFileByOwner 同族同修；copyFile 侧零改；延迟清理失败面 = 删除失败重试上限 3 次→结构化 WARN（path 可 grep）+ 进程重启残留显式接受（对账兜底登记 Non-Blocking Follow-up）；removeResource :262-270 与 saveFile :238 失败回滚零触碰；plan 2386 权限钉零回退（§3.1） |
| ② | 5.3-W-04（P1，CL-009 主；缺保护-2 同 diff；CL-010/CL-021 对齐成员归裁定⑩⑪） | **主查询路径行上界补齐惯例**：新配置 `nop.datav.dashboard-query.max-rows-per-panel`（默认 10000）+ 三站点（Dashboard :583 / Panel :136/:150）传入 `cfgValue+1` 探测（对齐导出 maxRows+1 先例）→ 超限显式抛新错误码 `ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED`（NopDatavErrors 注册+i18n，非静默截断）；R02 行上界范式成文；大面板用户兼容声明落 nop-datav.md（§3.2） |
| ③ | 5.3-CL-003（P2，W-02 主；反向钉死 :308 同 diff 改写；R12 族对齐） | **终态写收口 = 实体版本 CAS 条件更新**（`orm_disableVersionCheckError(true)` + updateEntityDirectly + `orm_readonly()` 0 行判定，2525 elector 形态；谓词载体=version 列，对状态域全枚举生效）：执行体加载后终态短路（镜像 markFailedSafe）+ RUNNING 写 CAS 失败短路 + 终态写（SUCCEEDED/FAILED/CANCELLED/SKIPPED/扫描标记）CAS 0 行放弃+结构化冲突 WARN；**0 行放弃分支的 fileId 处置[R1 Major-1] = 一律按裁定⑤回收（一次定夺）**；反向钉死改写目标 = 结构断言预设（scanner FAILED 保持 + worker 0 行冲突可观测 + 无孤儿），禁纯删；face a 语义反转为「scanner 标记粘滞」并声明（§3.3） |
| ④ | 5.3-CL-001（P2；缺保护-5 同 diff） | **cancel 收口并入裁定③单形态**：cancel 侧 putIfAbsent + CANCELLED 写同 CAS 形态（同文件禁两套 fencing）；CAS 失败（任务已被定终态）→ 补偿 remove 自置标志（putIfAbsent 保证只摘自置项）+ 返回当前终态；CAS 成功 → 标志留给 worker finally remove（:367 保持）——残留全闭合（CAS 失败分支 cancel 自摘 / CAS 成功分支 worker 必经 finally / 进程重启内存清零）；**flag 挂 DB 行被拒**（orm 变更触 PA 条款）（§3.4） |
| ⑤ | 5.3-CL-016（P2；假保护[状态掩盖孤儿]同 diff 修正；R1 Major-1/2 合流） | **预设 Option A = 模块内回收**：`daoFor(NopFileRecord)` 删行 + `fileStore.getFile(fileId).getResource()` 删物理文件（镜像 detachFileInSession 行删→物删序列；导出文件 originFileId=fileId 无共享引用，删除安全）；回收条件不变式 = 「fileId 不被任何可达持久化行引用」；回收自身失败 = 显式 WARN+path 结构化日志（镜像 removeResource best-effort，不引入重试新面——不触 CL-017 修复域）；Option B（IFileStore 加公共方法）被拒（§3.5）；交付侧同族（行引用在则不回收）；ExportE2E cancel 场景补 NopFileRecord 行数守恒+物理文件不存在断言 |
| ⑥ | 5.3-CL-015（P2，W-05） | **catch→delete 后 rethrow**：writeCsv 与 writeXlsx(workbook) 两本体 catch 面扩为 Exception（覆盖 checkCancelled 抛 NopException 穿过 IOException catch 的路径）→ `resource.delete()` 后原样 rethrow；writeXlsx(singleSheet) 委托自动覆盖；泄漏钉 = PanelDataExporter 测试 @AfterEach temp 目录无 `datav-export*` 残留断言（模块泄漏钩子首例）+ cancel/IOException 注入用例（§3.6） |
| ⑦ | 5.3-CL-002（P2，W-03；Dashboard+Screen 同族同 diff） | **行锁形态**：publishDashboard/publishScreen 在快照版本分配前对父行（dashboard/screen）执行 dao 层 FOR UPDATE 行锁（lockEntity，事务尾释放）——同目标发布串行化，版本号分配读 max+1 在锁内线性化；**FOR UPDATE 可行性核实成立**（IEntityDao.lockEntity :129-134 契约 + OrmSessionImpl.lock :654 实装 + SysSequenceGenerator :287 in-repo 先例）；唯一约束形态被拒（orm 变更触 PA 四件证据链，预设不选）；条件更新+冲突重读重试形态被拒（§3.7） |
| ⑧ | 5.3-CL-004（P2，W-03；R1 Minor-5） | **组合 = 进程内 per-ruleId 互斥（IResourceLockManager 使用面）+ rearm 回写版本 CAS**：互斥覆盖三段（loadOrCreateState 建行段[含 AlertRuleBizModel.initializeStateIfAbsent 同族]/状态机决策与 saveState 段/通知执行与回写段——通知在锁内串行）→ 单冷静期至多一次通知 + 建行竞态闭合；回写谓词载体 = 版本 CAS（**否决 lastNotifiedTime 等值谓词形态——NULL→值首通知迁移在 SQL 等值谓词下恒 0 行[Minor-5 陷阱]，方言 IS NULL 特判引入方言耦合；版本谓词对 NULL 列天然免疫**），shouldRearm 决策谓词 :380 的 null 分支显式保留；跨节点残余窗口显式声明（§3.8） |
| ⑨ | 5.3-CL-005（P2；假保护[:221 顺序伪装]同 diff 修正） | **per-user 进程内 ISemaphore 叠加 DB count 校验**：createExportTask 校验点先 tryAcquire（许可数=CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER，超时快速失败抛既有 ERR_DATAV_EXPORT_CONCURRENCY_LIMIT）→ DB count 校验保持（跨节点近似第二层）；许可释放点 = createExportTask 失败 finally + executeTask 收尾（:367 同一收口段）；进程重启许可随内存清零（与 recovery 标 FAILED 一致）；:221 改写为 4 线程并发提交断言 ≥1 拒绝（信号量原子性使断言确定性），禁纯删（§3.9） |
| ⑩ | 5.3-CL-010（P2，W-04；R02 对齐） | **loadMessages 加载侧 LIMIT**：按 `2×maxTurns+1` 推导（seq 降序取最近 N 条后按原升序语义返回），chatToQuery 注入路径经有界重载；**getChatSessionHistory 保留全量**（用户可见用途包络声明落 owner doc——分页参数形态被拒：GraphQL schema 用户可见 API 面变更非本批目标，且与 CL-022 successor 演进耦合）；listSessions :96-101 零触碰（§3.10） |
| ⑪ | 5.3-CL-021（P2，W-04；R02 对齐） | **列投影**：getScreenSnapshotHistory 历史查询改投影查询（仅取 snapshotVersion/publishedBy/publishedTime 三列，QueryBean 声明面零模型变更），返回形态 List\<ScreenSnapshotHistory\> 不变；**分页形态被拒**（改用户可见 API 面，同裁定⑩理由）；快照保留数上界可选子项 = **不采纳**（保留策略归 W-06/CL-013 族 successor——ledger-retention-policy.md 族 SSOT，本批不扩张）；Dashboard 对照查询 setLimit(1) 零触碰（§3.11） |
| ⑫ | 5.3-CL-019（P2，W-07） | **副作用后置 afterCommit**（模块内 AR-2 先例 submitExecutionAfterCommit :261-268 形态，含 isTransactionOpened 守护）：register/unregister 调用从事务体内移至 afterCommit 回调；失效面五站点同族同修一次成文（DashboardBizModel.disableReportTasksForDashboard :159 / disableAlertRulesForPanels 两调用面 :135 与 :772 / ReportTaskBizModel.doDeleteEntity :96-98 / afterEntityChange :71-79）+ AlertRuleBizModel 同族；回滚即不注销——「DB 行 ENABLED 而 job 已消失」窗口闭合；失败补偿形态被拒（§3.12） |
| ⑬ | 5.3-CL-020（P2，W-07；平台修复归 R1-01 接入点登记） | **启动延迟重试（一次）**：注册失败后经平台定时器延迟 30s 重试一次，再失败显式 WARN（结构化、可 grep）——重试上限一次定夺，禁无限重试/禁抛崩启动；**五站点接入点清单随批登记供 R1-01 消费**（scanner :104 / report :141 / alert :135 / recovery 双 bean init）；健康位指标形态被拒（仅可观测不自愈；平台级绝缘层本体归 R1-01，禁触 nop-kernel）（§3.13） |
| ⑭ | 5.3-CL-023（P2，W-07；非 orm 预设） | **最简对账案**：复用 recovery 双 bean 启动扫描扩展对账输出——「SUCCEEDED 且 deliveredChannels 为空」行列结构化 WARN 对账清单（deliveryId/reportTaskId/endTime 可 grep），零模型变更；对账触发点 = 启动 recovery（崩溃窗口的物理时点决定重启后必经）；存量行兼容 = WARN 级人工/脚本消费，不自动重发（auto-resend 被拒：SMTP at-least-once 重复投递风险 + 终态行自动改写面）；notifyState 两态列形态被拒（orm+DDL+存量迁移触 PA 四件证据链，预设不选）（§3.14） |
| ⑮ | 测试缝与零回退钉（全批） | 红①–红⑭驱动器表（§5.1：构造性当前即红 九条红①③④⑤⑥⑦⑨⑫⑬ / 反转钉死一条红② / 变异论证锚定四条红④'⑧⑩⑪——红④双面）；零回退钉 = 勘误 4 基线（nop-file 17 + nop-datav 非 ChatBI 面 610）+ 既有钉族点名 + ChatBI 20-red 面恒红维持声明；C10 收口门（缺保护 5 + 假保护 3）同 diff（§5.2） |

## 二、背景与动机

R3-03 批 14 项（P1×2 + P2×12）覆盖 nop-file 与 nop-datav 两域六类缺陷面：**去重引用计数域**（W-01——DaoResourceFileStore 物理文件去重的判-删序列与 copyFile 插入并发交错，引用计数过期判删致新记录永久悬挂，附件数据丢失不可恢复）、**终态多写者域**（W-02——导出/交付状态行 worker/cancel/scanner/recovery 四方 last-writer-wins：scanner 误标被复活、积压交付复活执行致重复发送邮件、cancel 终态覆盖与 cancelFlags 永久残留、cancel/fail-after-save 孤儿文件双持久化泄漏）、**check-then-act 域**（W-03——发布版本号读 max+1 无原子机制、告警状态行并发评估双通知+建行分叉、导出并发限额 TOCTOU 越界）、**内存上界域**（W-04——主查询路径 rowLimit=null 全量物化确定性 OOM 通道，与模块四个限额先例不对称；ChatBI 会话历史全量加载后裁剪；快照历史全列全量仅消费三元数据）、**资源清理域**（W-05——导出器临时文件失败路径泄漏，temp 目录线性积累）、**调度生命周期域**（W-07——注销非事务副作用回滚后静默停摆、注册失败仅记日志进程生命周期停摆、交付两段式 SMTP 半程窗口静默丢发）。本文档一次裁定 14 成员 + 测试缝（裁定⑮），使实施只有一种合法形态；三处条件申报边界（裁定⑦⑧⑭的 orm 备选形态）按 plan 头部 Protected Area 条款成文且预设全部不触发。

## 三、核心设计契约

### 3.1 裁定①：文件去重两段式延迟清理（W-01）

- **形态**：detachFileInSession 与 removeTempFileByOwner 的「isUniqueRef 命中 → removeResource 立即删」改为「isUniqueRef 命中 → 登记待清理条目（filePath+originFileId+登记时刻）」；进程内单线程延迟任务按宽限间隔（默认 60s，静态常量）逐条复验「同 originFileId 引用计数仍为零」——为零则删物理文件，>0 则取消删除（copy 竞态输家的记录已落库，文件保留正确）。两调用面同族同修；copyFile 侧零改（两段式单独闭合竞态，插入侧无需配合）；saveFile :238 失败回滚（新路径新 fileId 无 dedup）零涉。
- **竞态闭合论证**：交错窗 = [T1 isUniqueRef 查询， T2 新记录提交]——延迟复验把物理删除推迟到宽限间隔之后，T2 的提交（事务边界在调用方 OrmFileComponent 实体复制流内）必然先于复验对 DB 可见，复验读到的引用计数包含 T2 记录 → 取消删除；反序交错（T2 先提交）isUniqueRef 本就返回 false，无删除登记。
- **延迟清理任务失败面（plan 勘误 9 条款）**：①物理删除失败 → 重试上限 3 次（同宽限间隔步进）→ 放弃并输出结构化 WARN（path+originFileId 可 grep——供对账兜底消费，不引入 CL-017 修复域的重试/登记新语义）；②进程重启 → 内存注册表丢失，竞态窗内未删成的物理文件成为零引用残留——**显式接受**，兜底归已登记的 Non-Blocking Follow-up「导出产物对账任务」（与 CL-016 回收失败、CL-017 孤儿对账同池）；③宽限间隔的取值论证：覆盖管理端实体复制事务的最长常规时长（导出/复制流分钟级），60s 为保守下限，可配置化留 owner doc 候选不入本批。
- **originFileId 互斥案否决（一次定夺禁第三形态）**：①锁范围不闭合——copyFile 的插入提交发生在调用方事务边界（OrmFileComponent 透传流），方法级互斥锁在 copyFile 返回即释放，覆盖不到其 commit 时点，交错窗残留；②闭合需把平台锁生命周期挂到调用方事务（跨模块编排改造）；③IResourceLockManager 的 provider 装配在宿主侧非默认保证（nop-sys 的 DB 实现属另一聚合器），两段式为纯模块内零平台依赖形态。**removeResource :262-270 零触碰边界声明**：其 best-effort 语义（catch+log+return false）为 CL-017 P3 站点，本裁定两段式不改其失败处理；plan 2386 attach/detach 权限钉（checkAttachPermission :408-419）零回退。

### 3.2 裁定②：主查询路径行上界（W-04，R02 行上界范式）

- **形态**：新配置键 `nop.datav.dashboard-query.max-rows-per-panel`（默认 10000，Integer）落 NopDatavConfigs 源注册面；三站点把 `cfgValue+1` 作为 rowLimit 传入 binder（Dashboard newPanelQueryTask :583 批量路径 / Panel getPanelData :136 与 refreshPanel :150 经既有重载链）；结果行数 > cfgValue 即抛新错误码 `ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED`（NopDatavErrors 注册 + i18n 条目——TestNopDatavErrorsI18n 既有测试面要求），语义对齐导出 `ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED`（取数阶段防 OOM，非取数后校验）。`+1` 探测形态沿 PanelDataExporter :117-123/:195-199 既有先例——rowLimit 经 LongRangeBean 在数据集层限行（跨方言 paging，不手写 LIMIT SQL），超限判定在模块侧显式抛错，**禁静默截断**（advisory 化被 plan Risks 明文禁止）。
- **默认值论证（plan 勘误 9 条款）**：主查询路径是交互式看板渲染（50 面板 × 并行度 4），非导出批处理——交互画像的行数量级由既有缓存准入上界（`cache.max-rows-per-entry` 默认 1000，NopDatavConfigs :127-129）佐证，单面板 10k 行已是渲染可用上限的保守放宽；对齐导出/报告的 100k 会把批处理包络误作交互包络（50 面板 × 100k 行物化正是 P1 缺陷画像）；alert/chatbi 的 1k 为单规则/单会话窄域不宜直接套用。10k 为交互包络与批处理包络之间的显式选点。
- **兼容声明（owner doc 落点）**：默认上界为行为变更——既有超过 10k 行的面板查询将显式拒绝（错误码可辨识，非截断）；既有大面板用户经配置键调大兼容；声明落 Phase 3 新建的 nop-datav owner doc 上界配置族节（统一落账，文件不存在至 Phase 3 兑现——plan E5 缺口声明）。
- **R02 对齐点（与 1.5-MA-05/1.7-C5-001 批次，引用不重开）**：行上界作为显式配置键 + 超限显式错误 + 数据集层限行三要素同构；本裁定是「补齐模块惯例」而非新造机制（merge-adjudication §七#1——四个限额先例在案唯主路径缺位，不对称性即缺陷画像）。

### 3.3 裁定③：终态行版本 CAS fencing（CL-003，反向钉死同 diff）

- **形态（条件更新载体 = 实体版本 CAS，2525 elector 先例形态）**：导出/交付状态行的全部状态写点改统一条件更新形态——写前 `orm_disableVersionCheckError(true)`，updateEntityDirectly 的 UPDATE 由 ORM 恒带 `AND version=?` 隐式条件，写后 `orm_readonly()` 判定：false = 写入成功；true = 0 行（已有并发写者推进版本）→ **放弃写 + 结构化冲突 WARN（taskId/deliveryId+期望语义可观测）**。该形态覆盖任意并发写者（不限终态转换），强于 `where status in non-terminal` 等值谓词面且零新增查询机器；谓词以 version 列为界，对状态域全枚举生效——SKIPPED 态交付等合法终态推进由 fresh 加载自然放行（plan Risks「谓词过窄误拒」随之消解）。执行体三短路点：①加载后 isTerminal 短路（镜像 markFailedSafe :433 既有守卫——face b 复活修复点：runDeliveryInSession :219 加载后即查）；②RUNNING 写 CAS 失败短路（scanner 已标 FAILED → 放弃重活）；③终态写 CAS 失败放弃（fileId 按裁定⑤回收）。
- **状态域枚举（失效面闭合核对）**：导出行 PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED（isTerminal=后三）；交付行 PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED（isTerminal=后三，无 CANCELLED——交付无用户取消路径，scanner/markSkipped 写点同 CAS 形态）；**有意覆盖终态豁免枚举**：rollbackSucceededToFailed :340-363（SMTP 失败回补，新 session fresh 加载无冲突，设计声明内豁免零改）与 recordDeliveredChannels :319-334（cosmetic 回写，fresh 加载+SUCCEEDED 判定在案）。
- **scanner/recovery 面**：NopDatavStuckTaskScanner 标记路径与 recovery 双 bean doScanStuck（export :112-133 / delivery :108-131）的无条件 updateEntityDirectly 同改 CAS 形态——0 行（worker 已终态）→ 跳过该行 + 冲突计数，标记幂等语义保持。
- **0 行放弃分支 fileId 处置（R1 Major-1，一次定夺禁第三形态）**：**一律按裁定⑤回收**。条件更新 0 行 = 任务已被 cancel/scanner 抢先定终态，此时已落盘 fileId 必然不落到 fileRecordId 关联（终态行非 SUCCEEDED 或 SUCCEEDED 已被他人写入）→ 不回收即孤儿——回收是唯一无第三形态的闭合分支；「带 fileId 落 FAILED」形态被拒：对已被定终态（CANCELLED/FAILED）的行做二次 FAILED 改写本身是需要新 fencing 的终态覆盖，循环自指。
- **反向钉死同 diff 改写口径（TestNopDatavStuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed :308）**：默认预设 = **结构断言**（沿 2525 裁定④先例）——期望终态反转为「scanner FAILED 保持 + worker 条件写 0 行冲突可观测（结构化 WARN 断言）+ 0 行分支无孤儿文件断言（与红④合流）」；禁纯删，改写后断言集合必须覆盖「fencing 摘除后 worker 无条件覆盖回潮」回归。**face a 语义反转声明**：scanner 假阳性标记由「可被 worker 覆盖」改为「粘滞（worker 放弃+回收）」——60min 阈值误杀在途慢任务的代价从「状态漂移+重复执行」转为「任务标 FAILED+产物回收」，两态均为已声明语义，取粘滞侧为 fencing 一致性让步；owner doc 导出状态机契约节登记。
- **R12 族对齐矩阵**：条件更新纪律沿 cra-index WI1.5/WI1.7 C7-001 / WI1.6 C8-001 平台族与 plan 2511/2525 先例引用不重开；载体形态与 2525 elector（orm_disableVersionCheckError + readonly 判定）同物；站点细节（状态域枚举含 CANCELLED/SKIPPED 差异、cancel 语义、fileId 联动）禁照抄先例实现。

### 3.4 裁定④：cancel 收口并入单形态（CL-001）

- **形态**：cancelExportTask :222-235 改为——requireTask 加载后 isTerminal 短路保持 → `cancelFlags.putIfAbsent(taskId, TRUE)`（返回非 null = 已有并发 cancel，直接返回）→ CANCELLED 写走裁定③同一 CAS 形态。CAS 成功：标志留给 worker（:367 finally remove 保持——CAS 成功蕴含任务此前沿为 RUNNING、worker 必然在途，其 finally 必经）；CAS 失败（orm_readonly）：任务已被定终态（worker SUCCEEDED / scanner FAILED）→ **补偿 remove 自置标志**（putIfAbsent 保证只摘自置项，不误摘他请求的在途信号）+ 重载返回当前终态（cancel 输给完成=既有「保留终态」语义的镜像兑现）；终态写竞态极窄窗（两次连续失败）上限 2 次后按冲突抛出（用户可见重试，禁静默）。
- **残留闭合矩阵**：CAS 失败分支 → cancel 自摘；CAS 成功分支 → worker finally 摘；worker 已退出（终态已写）→ 必然落入 CAS 失败分支自摘；进程重启 → cancelFlags 内存 CHM 清零。map 单调增长面消解。
- **与裁定③同收口通道声明（禁两套 fencing）**：cancel 的终态写与 worker/scanner 的终态写为同文件同形态（版本 CAS + readonly 判定 + 显式冲突日志）；**flag 挂 DB 行被拒**：新增列触 `nop-datav.orm.xml`（PA 条款），且 DB 行作取消信号要求 worker 轮询 DB（行循环内 DB 往返不可接受）或依赖 status 轮询（等于放弃内存快速通道，纯劣化）。executionStartHook seam（:138）本体零触碰（CL-008 P3 域），红③消费该 seam 为交错同步点。

### 3.5 裁定⑤：孤儿导出文件模块内回收（CL-016，R1 Major-1/2 合流）

- **形态（预设 Option A = 模块内零公共接口变更）**：回收动作 = `daoFor(NopFileRecord)` 删行 + `fileStore.getFile(fileId).getResource()` 删物理文件——镜像 detachFileInSession :381-383 的「行删→物删」序列（IFileRecord.getResource 返回存储侧 IResource，与 DaoResourceFileStore.removeResource 同删除物）。可行性事实源：nop-datav-service pom 直接依赖 nop-file-dao（NopFileRecord 实体与 nop-file orm 模型在 vfs 注册）；导出产物为 saveFile 新建记录（originFileId=fileId 唯一引用），无共享引用删除安全。
- **回收条件不变式**：「fileId 不被任何可达持久化行引用」。导出侧三触发点（saveExportFile 成功后的全部非关联出口）：cancel-pre-SUCCEEDED 分支 :339-342 / catch-FAILED 分支（FAILED 行不写 fileRecordId）:352-365 / 终态写 CAS 0 行放弃分支（裁定③合流）——一律回收；交付侧同族 :266-292——交付行 FAILED 落库成功且 generatedFileRecordId 已随行持久化（行引用在，文件可达）则**不回收**，仅当行写自身失败（fileId 无行可达）时回收。
- **回收自身失败的显式处理（plan 勘误 9 条款）**：删行或物删失败 → 显式 WARN + path/fileId 结构化日志（镜像 removeResource best-effort 语义，单次不重试——重试/对账归 Non-Blocking Follow-up 对账任务池；**不引入 CL-017 修复域的新语义**）。
- **Option B 否决（R1 Major-2 申报边界）**：IFileStore 加删除方法 = nop-biz-file-core 公共接口叶变更（该接口现面仅 saveFile/saveFileAsync/getFileLink/getFile 四方法）→ 触四件证据链追加申报 + 全 impl 回归面；Option A 以既有公共面组合达成同等闭合，公共接口形态仅在 Option A 论证不成立时经申报采纳（本裁定论证其成立）。
- **假保护同 diff 修正**：ExportE2E cancel 场景补 NopFileRecord 行数守恒断言 + 物理文件不存在断言（与缺保护-4/-5 钉合流）——状态断言掩盖孤儿的伪保护转正；removeTempFileByOwner 仅清 TEMP_BIZ_OBJ_ID 的不可枚举面零触碰（孤儿的对账兜底归 Follow-up）。

### 3.6 裁定⑥：导出器临时文件失败路径收口（CL-015）

- **形态**：writeCsv 与 writeXlsx(workbook) 两本体 catch 面扩为 Exception（覆盖 checkCancelled :229 抛 NopException 穿过 catch(IOException) 的路径与磁盘满 IOException）→ `resource.delete()` 后原样 rethrow（NopException.adapt 包装语义保持）；finally 关流保持。**职责内无条件 finally delete 形态被拒**：成功路径的临时资源恰是返回给调用方 persist 的产物（saveExportFile 输入），无条件 finally 删除直接破坏成功路径——该形态按字面不可实施，按「成功标志位守卫的 finally」实施则与 catch→delete 等价而多一套状态，取最简形态。
- **同修面**：writeXlsx(singleSheet) :243-246 委托前者自动覆盖；调用方 saveExportFile/saveReportFile 的 finally（成功产物删除）已修对照零触碰——收口后失败路径与成功路径清理对称。泄漏钉 = PanelDataExporter 测试类 @AfterEach temp 目录 `datav-export*` 零残留断言（模块泄漏检测钩子首例，C10 :61-63 缺口兑现）+ cancel 注入与 IOException 注入用例（红⑪）。

### 3.7 裁定⑦：发布版本号行锁分配（CL-002）

- **形态（唯一预设）**：publishDashboard/publishScreen 在 calculateNextVersion 之前对父行（NopDatavDashboard/NopDatavScreen，requireEntity 已加载）执行 dao 层 FOR UPDATE 行锁——锁在当前事务内持有至提交，同目标（同 dashboardId/screenId）的并发发布在锁上串行化，后行者读到的 max(version) 含先行者已提交快照 → 版本号唯一；不同目标零竞争。锁序单一（父行 → 快照插入），无多锁交错。
- **FOR UPDATE 可行性核实记录（plan 勘误 9 条款，Phase 1 前置）**：`IEntityDao.lockEntity`（nop-dao :129-134）契约明文「使用 select for update 来锁定单条记录，不存在则抛 UnknownEntityException」；实装链 OrmEntityDao.lockEntity :265-268 → OrmSessionImpl.lock :654（persister.lock 执行行锁 SELECT）；in-repo 先例 SysSequenceGenerator.doSyncFromDb :287 `session.lock(seq)`（plan 2525 MA-05 已落地面）。**核实结论：行锁形态可实施，无需降级条件更新**。
- **条件更新+冲突重读重试形态被拒**：快照插入无 version 可 CAS（新行无既有版本），条件更新只能落在父行——以「父行版本推进」为序列化令牌需顺手改写父行业务字段语义（insert-only 快照流被注入父行写放大），且冲突重读重试是活锁面（需重试上限与退避，超出最小面）；行锁单次获取无重试、语义即「分配临界区」。**唯一约束形态被拒**（orm 变更）：(dashboardId, snapshotVersion) 唯一索引触 `nop-datav.orm.xml` + 存量 DDL + 重复行预检（对照 2525 裁定⑧的全套申报面），非 orm 预设条款下四件证据链不启动。Dashboard+Screen 同族同 diff（calculateNextVersion 两处同构，禁只修其一）。

### 3.8 裁定⑧：告警评估进程内互斥 + 回写版本 CAS（CL-004，R1 Minor-5）

- **形态（组合一次定夺）**：①**进程内 per-ruleId 互斥**（IResourceLockManager 使用面，resourceId=alertRuleId）覆盖三段——建行段（loadOrCreateState :278-289 的 check-then-insert 全程 + AlertRuleBizModel.initializeStateIfAbsent :114 同族，建行竞态闭合）/ 决策段（状态机转换判定 + saveState :291 持久化）/ 通知段（notifyWithRearmContract :314 的同步发送与 afterCommit 回调内的发送统一经同一互斥入口，释放在回写完成）——同进程内「判定+通知+回写」线性化，单冷静期窗口至多一次通知；②**rearm 回写版本 CAS**（runDeferredWriteback :343 的 fresh 重载后回写改裁定③同族条件更新形态，0 行放弃+WARN——多节点残余的第二道防线）。
- **谓词载体裁定（R1 Minor-5 兑现）**：回写条件更新的谓词载体 = **实体版本 CAS，否决 `where last_notified_time=旧值` 等值谓词形态**——首通知迁移为 NULL→值（Dim14-03 :195-197 注释自证失败保持 null），SQL 三值逻辑下等值谓词对 NULL 恒不命中，首通知后条件更新恒 0 行（Minor-5 指认的陷阱）；规避需方言 IS NULL 特判 = 引入方言行为耦合。版本谓词对 NULL 列天然免疫且与裁定③④同族单形态；**决策谓词** shouldRearm :380-384 的 `lastNotifiedTime==null → true` 分支显式保留（失败重试语义，Dim14-03 契约不回退——本裁定不改通知先发时序，只串行化通知执行）。
- **残余窗口声明**：进程内互斥不闭合跨节点（cron 节点 × 手动节点）并发——版本 CAS 使状态行收敛、双通知残余窗口显式登记 owner doc（DB 侧唯一约束形态为标准闭合，因 orm 预设条款不启动，留条件申报边界）；对照 ChatBiSessionManager.nextSeq UK 先例（CL-008 同族对照在案）作为后续 orm 申报时的形态参照。

### 3.9 裁定⑨：导出并发限额 ISemaphore 叠加（CL-005，假保护同 diff）

- **形态**：checkConcurrencyLimit :497-513 增进程内 per-user ISemaphore（io.nop.commons.concurrent.semaphore 使用面——PA 表第 9 行明文豁免）前置 tryAcquire（许可数 = CFG_DATAV_EXPORT_MAX_CONCURRENT_PER_USER，超时快速失败抛既有 ERR_DATAV_EXPORT_CONCURRENCY_LIMIT），DB count 校验 :507 保持为跨节点近似第二层；许可持有期 = 任务在途期（pending+running），释放点 = createExportTask 校验后失败 finally + executeTask 收尾（与 :367 cancelFlags.remove 同一收口段——与裁定④的兜底链共享位置）；进程重启许可随内存清零，与 recovery 启动标 FAILED 语义自洽。
- **:221 假保护同 diff 修正**：testConcurrencyLimitRejects 改并发形态——4 线程并发提交、断言成功数 ≤ max 且 ≥1 拒绝；信号量 tryAcquire 的原子性使断言确定性（无时序敏感）——当前代码无信号量，4 并发全部通过 → 断言失败**当前即红**；改写禁纯删（保留限额拒绝语义断言）。**显式非原子上界声明形态被拒**：限额是防 globalWorker 池耗尽的背压手段（audit 判级依据），advisory 声明使背压名存实亡，与「上界禁 advisory 化」批纪律同构。

### 3.10 裁定⑩：ChatBI 会话历史加载侧 LIMIT（CL-010，R02 加载侧范式）

- **形态**：loadMessages 增有界形态——按 `2×maxTurns+1` 从配置推导 LIMIT（每轮 2 条消息 +1 容错），seq 降序取最近 N 条后按升序语义返回（现实现 :128 seq 升序全量，调用方 buildHistoryContext 与 getChatSessionHistory 均消费升序——排序契约不变，物化集有界）；chatToQuery :296-304 注入路径消费有界形态（注入侧双上界 max-turns/max-chars 裁剪从「全量加载后」前移到「加载集有界」，双上界本体保留为第二层）。appendTurn :138-160（UK 先例面）零触碰。
- **getChatSessionHistory 定夺（一次定夺禁第三形态）：保留全量**。理由：①该 action 是用户显式请求历史页（单次、用户触发），与 chatToQuery 的「每轮必经」成本放大画像不同；②分页参数 = 用户可见 GraphQL schema 变更（接口面演进应由前端需求驱动），owner doc 以包络声明承担告知义务；③与 CL-022（listSessions 分页，W-04 尾部 successor）同族演进应一并设计，本批单点引入分页参数制造族内不一致。**listSessions :96-101 零触碰边界**（CL-022 去向登记）。

### 3.11 裁定⑪：快照历史列投影（CL-021，R02 投影范式）

- **形态**：getScreenSnapshotHistory :221-233 的快照装载改投影查询——仅声明 snapshotVersion/publishedBy/publishedTime 三列（QueryBean 声明面，零模型变更——PA 表第 11 行在案），SNAPSHOT_CONTENT 大列不入物化集；返回形态（List\<ScreenSnapshotHistory\> 三元组）与排序（版本倒序）不变，调用方零感知。**分页形态被拒**（用户可见 API 面变更，同裁定⑩②③理由）。**快照保留数上界可选子项：不采纳**——保留策略归 W-06/CL-013 族（SSOT=ledger-retention-policy.md），本批零扩张（plan 对齐纪律条款）。**Dashboard 对照查询零触碰**（findLatestSnapshot/findSnapshotByVersion 已 setLimit(1)——族内不对称的本侧即缺陷面，对侧零涉）。restoreScreenFromSnapshot 等按版本单条读 content 的路径零触碰（内容消费是合法整行用途）。

### 3.12 裁定⑫：调度注销副作用 afterCommit 后置（CL-019）

- **形态（唯一预设）**：register/unregister 调用从事务体内移至 `transactionTemplate.afterCommit` 回调（模块内 AR-2 先例 submitExecutionAfterCommit :261-268 的完整形态移植——含 isTransactionOpened 守护：无事务上下文直接执行，回滚即不触发）；回滚时 job 保留 → DB 行回滚为 ENABLED 而 job 仍在 → 调度继续，静默停摆窗口闭合。失效面五站点同族同修一次成文（禁同族两形态）：disableReportTasksForDashboard 内嵌 unregister :159 / disableAlertRulesForPanels 两调用面（handleDeleteCascade :135、saveDashboardLayout :772）/ ReportTaskBizModel.doDeleteEntity :96-98 / ReportTaskBizModel.afterEntityChange :71-79（save 路径的 register/unregister 同在事务内，同窗口镜像）+ AlertRuleBizModel 同族（unregisterRule/initializeStateIfAbsent 联动面）。
- **afterCommit 内异常处理契约**：回调内 register/unregister 失败 catch + 显式 WARN（调度器运行时 registerTask 既有 catch-log 语义 :158-162 保持）——**不回滚 DB 行**（commit 已完成，无回滚可用；失败处置归裁定⑬同款自愈语义，运行时注册失败按日志可见）。**失败补偿形态被拒**：补偿需在回滚路径重放 register/unregister，平台事务模板无 afterRollback 钩子（afterCommit 为唯一挂点），手写回滚侦测 = 重造事务语义（脆弱面）；merge-adjudication §七#2「同模块范式一致性是低成本高收益修复面」即指向 afterCommit 先例复用。**回滚恢复钉**：stub scheduler 记录注销调用 + 级联中途注入失败 → 断言回滚后注销未发生（红⑫）；TestNopDatavAsyncSubmitTransactionPath 既有 seam 零回退。

### 3.13 裁定⑬：注册失败启动延迟重试（CL-020，最小面 + R1-01 接入点登记）

- **形态（两案一次定夺）**：init 注册失败（catch 分支）后经平台定时器**延迟 30s 重试一次**（固定延迟、上限一次——重试仍失败输出结构化 WARN，禁无限重试/禁抛崩启动——多租户宿主不抛崩的既有选择保持）；重试执行器带测试缝（可同步触发，沿 executionStartHook package-private seam 惯例——红⑬不依赖真实 30s 等待）。**scanRegistered 健康位形态被拒**：健康位只可观测不自愈，主导触发场景（启动期 DB/调度器瞬态故障窗）恰是单次延迟重试可闭合的；健康位指标面（CoreMetrics 接线）为新增公共可观测面且仍需人工介入——留给 R1-01 平台绝缘层的统一观测方案。
- **五站点接入点清单（R1-01 交付物，随批登记）**：①NopDatavStuckTaskScanner.init :103-108（基础设施级——stuck 扫描全局停摆面）；②NopDatavReportScheduler.init per-task 循环 :141（失败隔离正确但无补注册）；③NopDatavAlertScheduler.init per-task :135（同族）；④NopDatavExportTaskRecovery.init :58-65（重启恢复单点）；⑤NopDatavReportDeliveryRecovery.init :58-65（同族）。**边界声明**：平台级统一绝缘层（29 站点 catch Throwable + completeExceptionally + 重臂）归 R1-01（2.1-C3-001），本单禁实现平台级方案、禁触 nop-kernel——本裁定的延迟重试为模块内最小自愈面，是 R1-01 接入点的就地收敛候选而非其替代。

### 3.14 裁定⑭：交付半程窗口启动对账（CL-023，非 orm 预设）

- **形态（两案一次定夺）**：最简对账案——recovery 双 bean 启动扫描扩展对账输出：状态过滤之外增对账查询「SUCCEEDED 且 deliveredChannels 为空/NULL」的交付行，逐行输出结构化 WARN 对账清单（deliveryId/reportTaskId/endTime 可 grep，零模型变更零自动改写）；**对账触发点 = 复用启动 recovery @PostConstruct**（不设独立周期调度）——半程窗口的物理成因是进程死亡（Part A 提交后 Part B 前崩溃/重启），崩溃必经重启、重启必经 recovery，触发点与窗口成因一一对应；运行中进程的 SMTP 失败已有进程内补偿（rollbackSucceededToFailed :340-363，已修对照零触碰）。
- **存量行兼容论证**：对账谓词对存量行误报面 = 历史上「SUCCEEDED 但 deliveredChannels 回写失败/缺失」的 cosmetic 行（:312 注释自证 cosmetic 语义）——对账输出为 WARN 级人工/脚本消费清单，不自动重发不改终态，误报代价为一条可忽略的核对项；消费面（运维处置指引）随 owner doc 落账（plan Risks「无人消费则停摆不可见性未消除」条款兑现）。
- **notifyState 两态列形态被拒**（orm 条件申报）：新列触 `nop-datav.orm.xml` + 存量 DDL + 存量行迁移预检，非 orm 预设条款下四件证据链不启动；且自动重发形态引入 SMTP at-least-once 重复投递与终态行自动改写两个新语义面。**rearm 自愈对照语义取舍**：告警侧自愈成立的前提是状态行在通知成功前保持非终态（lastNotifiedTime 未写→下轮 rearm）；交付侧 Part A 已提交 SUCCEEDED 终态，自愈需改写终态语义或引入中间态列——两案（对账 WARN vs 中间态列）中取零模型侧，放弃自动自愈、保留显式对账，语义取舍随 owner doc 声明。

## 四、对齐纪律条款

1. **CL-003 反向钉死同 diff + face a/b 边界**：:308 改写与 fencing 修复同一 diff，结构断言默认预设（§3.3），禁纯删；face a 反转为「scanner 标记粘滞」并 owner doc 声明，face b（FAILED→RUNNING 复活）由加载后终态短路闭合；R12 族纪律引用不重开，站点细节禁照抄 2525 实现。
2. **CL-001/CL-003 同收口单形态 + CL-016 同 diff 交付**：cancel 终态写与 worker/scanner 终态写同一版本 CAS 形态（同文件禁两套 fencing）；CL-016 回收与 CL-001 的 cancel 分支改造同 diff（同方法族）；裁定③ 0 行分支的 fileId 处置与裁定⑤合流一次定夺。
3. **C10 同变更集绑定（E2）**：缺保护 5 条（1=CL-014 / 2=CL-009 / 3=CL-003 配套 / 4=CL-016 / 5=CL-001——3/4/5 为批次验收前置）+ 假保护 3 条（CL-003 :308 反向钉死 / CL-005 :221 顺序伪装 / CL-016 ExportE2E 状态掩盖）与各自本体同 diff，禁先修本体后补测拆分；本批无时序敏感搭车项（勘误 4 口径）。
4. **W-04 R02 三范式批内一次成文**：行上界（裁定②）+ 加载侧 LIMIT（裁定⑩）+ 列投影（裁定⑪）同批交付；与 1.5-MA-05/1.7-C5-001 对齐为范式引用不重开；CL-022 listSessions 与 Dashboard 对照 setLimit(1) 查询零触碰。
5. **CL-002/CL-004/CL-023 非 orm 预设 + 条件申报边界**：三裁定的唯一约束/新列备选形态触 `nop-datav.orm.xml`，预设全部不启动四件证据链；如实施期改选 orm 形态，须按 plan 头部 Protected Area 条款（plan-first 四件证据链：plan 追加申报 + 设计裁定专节 + draft review 记录 + 回归测试）重新申报，禁静默扩权。
6. **CL-020 R1-01 边界**：本单最小面（延迟重试一次）+ 五站点接入点清单登记；禁实现平台级绝缘层、禁触 nop-kernel、禁新增平台可观测面。
7. **CL-019 AR-2 先例复用**：afterCommit 形态沿 submitExecutionAfterCommit 完整移植（含 isTransactionOpened 守护），五站点同族同修禁两形态；afterCommit 内失败不回滚 DB 行（§3.12 契约）。
8. **零触碰边界**：removeResource :262-270（CL-017）/ listSessions :96-101（CL-022）/ executionStartHook seam 本体（CL-008）/ rollbackSucceededToFailed 与 recordDeliveredChannels（有意覆盖终态豁免面）/ Dashboard 对照 setLimit(1) 查询 / appendTurn UK 面 / nop-orm OrmFileComponent 透传面 / ChatBI 工具执行器 20-red 面（plan 2501 域 successor）——全部零触碰；plan 2386 attach/detach 权限钉零回退。
9. **硬序与软序**：无跨批硬依赖；批内软序 = 裁定③ 先行于裁定④（同文件同收口通道基线清楚）→ 裁定⑤（依赖 0 行分支形态）→ 裁定⑭（同文件分裁定）；组 D 三成员（②→⑩→⑪）R02 范式同批成文；同文件多成员（ExportTaskBizModel×4 / DashboardBizModel×3 / ReportDeliveryExecutor×3）diff 分组登记防混，红钉逐成员独立可跑防归因混淆。

## 五、裁定⑮：测试缝与零回退钉

### 5.1 红①–红⑭驱动器表（先红后绿或变异/回退论证，E2）

| 红 | 成员 | 缺陷态红判据 → 修复态绿 | 形态 |
|----|------|------------------------|------|
| ① | W-01 | T2 copyFile 读记录后 latch × T1 detach 完成（登记待清理）→ T2 提交 → 同步冲刷待清理队列（测试缝）→ 断言物理文件仍存在且引用>0 取消删除；**当前即红**（现实现立即删物理文件，断言文件存在失败） | 构造性交错（latch）+ 待清理注册表同步冲刷 seam（package-private，沿 executionStartHook 惯例）；交错窗无挂钩点时降级变异论证（摘延迟登记→立即删→红证复现，沿 2525 红③先例登记） |
| ② | CL-003 | 反转钉死改写断言本体：scanner FAILED **保持** + worker 条件写 0 行冲突 WARN 可观测 + 0 行分支无孤儿（fileId 回收断言）+ face b 复活钉（delivery 已 FAILED 后积压出队不执行——加载后终态短路）；**当前即红**（现实现 worker 覆盖 SUCCEEDED / 复活执行） | 反转钉死（同 diff 改写 :308）+ 结构断言默认预设（禁纯删） |
| ③ | CL-001 | executionStartHook seam 交错：worker RUNNING 写后 latch × cancel 完成 → 放行 → 断言终态不被覆盖（CANCELLED 或 SUCCEEDED 唯一胜者按 CAS 时序）+ cancelFlags 空 + 无孤儿；**当前即红**（终态覆盖 + 标志残留） | 构造性交错（既有 seam 消费，seam 本体零触碰） |
| ④ | CL-016 | cancel-after-save 面：seam 交错使 saveExportFile 后命中 cancel 分支 → 断言 NopFileRecord 零残留行 + 物理文件不存在；catch-after-save 面：异常注入使终态写失败 → 同断言；**当前即红**（双分支均弃 fileId） | 构造性（seam）+ 变异论证（异常注入面）；与红② fileId 处置断言合流 |
| ⑤ | CL-002 | barrier 双线程并发 publish 同一 dashboard/screen × N 轮 → 断言 snapshotVersion 无重复；**缺陷态高概率红**（无原子机制） | 压力构造 + 变异锚定（摘行锁→红证——无 seam 可用，降级形态沿 2525 红③先例登记） |
| ⑥ | CL-004 | 双入口（cron+manual）barrier 交错 + stub 通知计数 → 断言单冷静期窗口恰一次通知 + NopDatavAlertState 单行；**缺陷态压力红**（双通知/双行） | 压力构造 + stub 计数 + 变异锚定（摘互斥→红证） |
| ⑦ | CL-005 | 4 线程并发 createExportTask（max=3）→ 断言成功 ≤3 且 ≥1 拒绝（ERR_DATAV_EXPORT_CONCURRENCY_LIMIT）；**当前即红**（无信号量全部通过）；:221 假保护同 diff 改写（并发形态禁纯删） | 构造性（信号量原子性使断言确定性，无时序敏感） |
| ⑧ | CL-009 | 配置小上界 + 种子大结果集 → 断言显式抛 ERR_DATAV_QUERY_ROW_LIMIT_EXCEEDED（错误码断言）；变异锚定 = 摘 rowLimit 传入 → 返回全量不断言红（防「上界存在但未接线」退化） | 配置构造 + 变异论证（大结果集注入面） |
| ⑨ | CL-010 | 种子 >2×maxTurns+1 条消息 → 断言注入路径装载消息数有界（LIMIT 真下推——返回消息数断言）；**当前即红**（全量装载） | 构造性 |
| ⑩ | CL-021 | 投影结构断言：历史查询字段声明面不含 snapshotContent（现走整行 findAllSnapshots → 断言失败**当前即红**）；变异锚定 = 还原整行查询 → 结构断言红 | 结构断言（沿 2525 红④先例形态） |
| ⑪ | CL-015 | cancel 注入（checkCancelled 抛点）与 IOException 注入 → writeCsv/writeXlsx 抛出后断言 temp 目录无 `datav-export*` 残留；**当前即红**（失败路径不删） | 构造性（异常注入）+ @AfterEach 泄漏钩子（模块首例） |
| ⑫ | CL-019 | stub scheduler 记录注销调用 + 级联中途注入失败（stub unregisterRule 抛出）→ 事务回滚 → 断言注销未发生（stub 空）+ dashboard 行存活；**当前即红**（注销先于失败已发生） | 构造性（stub scheduler + 注入失败） |
| ⑬ | CL-020 | 注入 init 失败（stub addJob 首抛）→ 重试缝同步触发 → 断言注册成功；二次失败断言 WARN 显式；**当前即红**（无重试，注册保持失败） | 构造性（失败注入 + 重试同步触发 seam） |
| ⑭ | CL-023 | 种子「SUCCEEDED 且 deliveredChannels 空」行 → 触发 recovery 扫描 → 断言对账清单输出（结构化 WARN 捕获或对账计数返回断言）；**当前即红**（恢复跳过 SUCCEEDED，无清单） | 构造性 |

### 5.2 零回退钉清单与 C10 映射

- **基线零回退**：勘误 4 口径——nop-file 17 tests（dao 2 类 9 + service 2 类 7 + web 1 类 1）+ nop-datav 非 ChatBI 面 610 tests 0F0E，Phase 2/3 全绿零回退；ChatBI 20-red 面（8 测试类，勘误 3 签名）**恒红维持声明**——修复期前后均应恒红，变绿/变红须登记归因禁静默。
- **点名既有钉族零回退**：TestDaoResourceFileStoreFileHash / TestDaoResourceFileStoreAttachPermission（plan 2386 权限钉）/ TestNopDatavDashboardParallelQuery / TestNopDatavDashboardQueryCache / TestNopDatavAsyncSubmitTransactionPath（AR-2 seam——裁定⑫形态参照）/ TestNopDatavShareRateLimit / TestNopDatavStuckTaskScan（既有用例族保持；:308 反转钉死改写仅裁定③同 diff 且行为等价断言在位）/ TestNopDatavExportE2E（cancel/限额用例改写仅裁定④⑤⑨同 diff）/ TestNopDatavAlertNotifyTransactionBoundary（P1-06 契约钉——裁定⑧零回退）/ TestNopDatavChatBiTransactionBoundary（非工具链用例维持）/ TestPanelDataExporter ×2 / TestNopDatavReportE2E / TestNopDatavChatBiSessionManagement / TestNopDatavDeleteLifecycle。
- **C10 映射同 diff（SSOT=C10 矩阵 :23-49 + 补测清单 :65-71）**：W-01→缺保护-1（补测#1）；CL-009→缺保护-2（补测#2）；CL-003→反向钉死改写+补测#3；CL-016→假保护修正+补测#4；CL-001→缺保护-5（补测#5）；CL-005→假保护修正（:221）；CL-015→@AfterEach 泄漏钩子首例；载体命名沿 `Test*_<工单>` 后缀惯例（W01/CL003/CL001/CL016/CL015/CL002/CL004/CL005/CL009/CL010/CL021/CL019/CL020/CL023）。
- **端到端/接线/无静默跳过义务**（rule 22/23/24）：去重链 = copyFrom→flush→detach→物理文件存在性全链钉；导出链 = create→execute→cancel/失败→终态+文件守恒（ExportE2E 入口到 fileStore 出口）；交付链 = runDelivery→SMTP 失败→补偿/对账出口；发布链 = 并发 publish→版本唯一；查询链 = getDashboardData 大结果集→ERR 出口；调度链 = 删除→afterCommit 注销→回滚恢复出口。接线断言：CAS 0 行 WARN 真可观测 / afterCommit 真挂点（回滚不触发）/ 重试真注册 / LIMIT 真下推 / 上界真抛 ERR / 回收真执行（残留守恒）；显式 WARN 面：CAS 冲突、回收失败、对账清单、重试终败——禁空方法体/静默吞。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① originFileId 互斥（IResourceLockManager） | 方法级锁覆盖不到 copy 侧调用方事务 commit 时点，交错窗残留（§3.1）；事务边界锁需跨模块编排；provider 装配非宿主默认保证 |
| ② rowLimit 静默截断/advisory 声明 | 上界是防 OOM 硬防护，plan Risks 明文禁 advisory 化；导出先例即显式抛错语义 |
| ② 默认值对齐导出 100k | 交互路径与批处理包络混淆（缓存准入 1000 行佐证交互量级）；50 面板×100k 正是缺陷画像 |
| ③ `where status in non-terminal` 手写等值谓词条件更新 | 零新增查询机器的版本 CAS 同族形态更强（任意并发写者均拦截）且为 2525 已落地先例；谓词过窄有 SKIPPED 态误拒面 |
| ③⑤ 0 行放弃分支「带 fileId 落 FAILED」 | 对已终态行的二次改写自身需要新 fencing，循环自指；回收是唯一无第三形态闭合分支 |
| ④ flag 挂 DB 行 | 新列触 orm PA 条款；DB 行作取消信号使 worker 轮询退化为 DB 往返 |
| ⑤ Option B（IFileStore 加删除方法） | nop-biz-file-core 公共接口叶变更须四件证据链+全 impl 回归；Option A 既有公共面组合已闭合 |
| ⑥ 无条件 finally delete | 删除成功路径返回给调用方 persist 的产物，按字面不可实施；成功标志守卫版与 catch→delete 等价而多状态 |
| ⑦ 条件更新+冲突重读重试 | 快照插入无 version 可 CAS；父行写放大注入 insert-only 流；重试是活锁面 |
| ⑦⑧⑭ 唯一约束/新列 orm 形态 | 触 `nop-datav.orm.xml`+存量 DDL+预检，非 orm 预设条款下四件证据链不启动（条件申报边界在案） |
| ⑧ lastNotifiedTime 等值谓词条件更新 | NULL→值首通知迁移在 SQL 等值谓词下恒 0 行（Minor-5 陷阱）；IS NULL 特判=方言耦合；版本 CAS 免疫且同族 |
| ⑧ 唯一约束+冲突重读 | 同 ⑦⑧⑭ orm 拒绝面；跨节点闭合留条件申报边界 |
| ⑨ 显式非原子上界声明 | 背压手段名存实亡，与上界禁 advisory 化同构 |
| ⑩⑪ 分页参数 | 用户可见 GraphQL schema 变更非本批目标；与 CL-022 successor 同族演进应一并设计 |
| ⑪ 快照保留数上界 | 保留策略归 W-06/CL-013 族 SSOT，本批不扩张 |
| ⑫ 失败补偿（回滚重放 register） | 平台事务模板无 afterRollback 钩子；手写回滚侦测=重造事务语义；afterCommit 先例在案 |
| ⑬ scanRegistered 健康位 | 只可观测不自愈；主导场景（启动瞬态窗）单次重试可闭合；指标面归 R1-01 统一方案 |
| ⑬ 无限重试/抛崩启动 | 重试风暴面（plan Risks）；多租户宿主不抛崩为既有选择 |
| ⑭ notifyState 两态列+自动重发 | orm+DDL+迁移申报面；SMTP at-least-once 重复投递+终态自动改写新语义面；对账 WARN 零模型闭合 |
| ⑭ 独立周期对账调度 | 窗口成因=进程死亡→重启必经 recovery，启动触发点与成因一一对应；新增周期任务反增裁定⑬同族停摆面 |

## 七、失效面核对总表与 PA 零触碰声明

- **失效面核对（grep 同族写点逐一登记）**：DaoResourceFileStore 物理删除点 = detachFileInSession :383（修→延迟登记）/ removeTempFileByOwner :168（修→延迟登记）/ saveFile :238（零触碰——非 dedup 面）；copyFile :442-460 零改。导出/交付状态行全部写点 = executeTask RUNNING :301-303（修：CAS）/ SUCCEEDED :344-349（修：CAS+0 行回收）/ catch FAILED :358-361（修：CAS）/ markCancelled :419-426（修：CAS）/ markFailedSafe :428-445（isTerminal 守卫已修对照——CAS 形态对齐后零语义变更）；cancelExportTask :230-233（修：CAS+补偿 remove）；runDeliveryInSession RUNNING :249-251（修：加载后终态短路+CAS）/ SUCCEEDED :271-275（修：CAS+0 行回收）/ catch FAILED :283-287（修：CAS）；recovery doScanStuck ×2 :112-133/:108-131（修：CAS+0 行跳过计数）；rollbackSucceededToFailed :340-363 与 recordDeliveredChannels :319-334（豁免零触碰）；markSkipped（grace 面，随 CAS 形态统一）。版本分配写点 = publishDashboard/publishScreen 快照插入（修：父行锁）；告警写点 = createInitialState :262 / initializeStateIfAbsent（修：互斥）/ saveState :291 / runDeferredWriteback :343（修：互斥+CAS）；查询写点 = PanelDataBinder 三调用方 :583/:136/:150（修：rowLimit 传入）/ loadMessages :128（修：LIMIT）/ findAllSnapshots :389（修：投影）；资源写点 = writeCsv :215-241 / writeXlsx :249-258（修：catch→delete）/ saveExportFile 出口三分支（修：回收）；调度写点 = 五站点 register/unregister（修：afterCommit）/ init catch ×5（修：延迟重试）；对账点 = recovery 扫描扩展（修：SUCCEEDED+空渠道清单）。
- **PA 零触碰声明**：头部 14/14 成员零 api 叶触碰（nop-file-api 3 文件零触碰 / nop-datav-api 零触碰）、零 orm 模型触碰（`nop-file.orm.xml`/`nop-datav.orm.xml`——裁定⑦⑧⑭三处条件申报条款在案且预设全部不启动）、零 `_` 生成物触碰、零平台设施修改（lockEntity/orm_disableVersionCheckError/IResourceLockManager/ISemaphore/transactionTemplate.afterCommit/IJobScheduler/CoreMetrics 均为使用面引用）；裁定⑤预设 Option A 零公共接口变更（R1 Major-2 缺省形态），Option B 申报面不启动。载体文件 17 个（nop-file-dao 1 + nop-datav-service 16，逐名清单见 plan Current Baseline），分属两 impl 叶聚合器，E3 双通道命令 `./mvnw test -pl nop-file -am` / `./mvnw test -pl nop-datav -am` 直达（阻断面处置沿 plan 勘误 5 判据）；新增配置键/错误码落 `NopDatavConfigs.java`/`NopDatavErrors.java` 源注册面（非生成物），新错误码须带 i18n 条目（TestNopDatavErrorsI18n 既有面）。

## 八、勘误（live 亲读核实登记，live @ `b724110569`）

1. **行号零漂移声明**：plan 成员表全部站点锚点经本轮 live 亲读逐一核对（DaoResourceFileStore / ExportTaskBizModel / ReportDeliveryExecutor / DashboardBizModel / ScreenBizModel / AlertEvaluator / ChatBiSessionManager / ChatBiBizModel / PanelDataBinder / PanelDataExporter / PanelBizModel / ReportTaskBizModel / ReportScheduler / StuckTaskScanner / AlertScheduler / recovery 双 bean / Configs / Errors / 反向钉死与假保护两测试锚点）——**与 plan 基线 `9ab4a0f4de` 亲读值零漂移**（`b724110569` 相对基线仅 plan 立项提交，零源码变更）；缺陷机制与行段全部一致，无新增勘误项。
2. **plan 勘误 9 条款履行登记（Phase 1 前置核实）**：①裁定⑦ FOR UPDATE 可行性**核实成立**——`IEntityDao.lockEntity`（nop-dao :129-134 select for update 契约）→ `OrmEntityDao` :265 → `OrmSessionImpl.lock`（nop-orm :654）实装链完整，in-repo 先例 `SysSequenceGenerator.doSyncFromDb` :287；行锁形态采纳，无需降级条件更新。②条件更新平台原生形态核实：`orm_disableVersionCheckError(true)` + `updateEntityDirectly`（genUpdateSql 恒带 `AND version=?`）+ `orm_readonly()` 0 行判定为 plan 2525 已落地形态（`SysDaoLeaderElector` :118-128/:203-213 live 亲读在案）——裁定③④⑧的 CAS 载体据此成文。③datav 全实体 versionProp 在案（orm.xml 17 处亲读）——CAS 载体可用性前提成立。④`IFileStore` 公共面四方法核实（无删除/detach 面）——裁定⑤ Option B 申报面事实源；nop-datav-service 对 nop-file-dao 的直接 pom 依赖（:43-48）核实——Option A 可行性事实源。
3. **裁定⑧谓词载体修正（plan Minor-5 示例形态的设计裁定细化）**：plan 裁定⑧行示例谓词「`where last_notified_time=旧值` 乐观 fencing」经亲读核实存在 Minor-5 指认的 NULL 迁移陷阱（AlertEvaluator :195-197 Dim14-03 注释自证首通知 lastNotifiedTime 为 null，SQL 等值谓词恒不命中）——本设计改载**版本 CAS 谓词**（对 NULL 列免疫且与裁定③④同族单形态），Minor-5 的规范性要求（谓词枚举显式覆盖 NULL→已通知迁移、首通知后条件更新不得恒 0 行）由「否决等值谓词+版本谓词免疫」兑现，决策谓词 shouldRearm 的 null 分支保持显式；此为裁定域内形态定夺，非 plan 事实错误勘误。
4. **裁定①互斥案否决的补充论证（亲读补强）**：plan 裁定①域两案定夺的「originFileId 互斥」否决理由，除 audit 原始论证外，补强为锁范围论证——copyFile 插入的提交时点在调用方事务边界（OrmFileComponent 实体复制透传流，plan 勘误 8 亲读在案），方法级互斥无法覆盖 commit 时点，竞态窗结构性残留；两段式延迟清理为唯一不依赖锁生命周期编排的闭合形态。
5. **状态域枚举亲读补全**：`NopDatavExportTaskStatus`（isTerminal=SUCCEEDED/FAILED/CANCELLED）与 `NopDatavReportDeliveryStatus`（isTerminal=SUCCEEDED/FAILED/SKIPPED，无 CANCELLED）两枚举 live 亲读在案——plan Risks「谓词过窄误拒 SKIPPED 态交付」的枚举核对以本登记为核对底账（裁定③版本 CAS 形态下该风险随之消解，枚举登记供 owner doc 状态机契约节消费）。
