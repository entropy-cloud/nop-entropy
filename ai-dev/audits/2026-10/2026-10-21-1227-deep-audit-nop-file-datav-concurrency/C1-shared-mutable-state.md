# C1 共享可变状态与可见性 — nop-file / nop-datav 初审（批 1）

- 审计单元：nop-file/nop-file-api（3f/492）+ nop-file-dao（6f/597）+ nop-file-service（4f/57）+ nop-datav-core（2f/276）
- 方法：五类载体逐文件盘点（singleton 实例字段 / static 可变 / 全局注册表 / ThreadLocal / 参数逃逸），盲审（未读 audits/plans/bugs/lessons/backlog）
- CL 前缀：`nop-file-datav-CL-0NN`（本文件从 CL-001 起，批 2 续编）

## 批 1 共享可变状态清单（file 全部 + datav core）

| 字段/载体 | 文件:行 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| `daoProvider`/`ormTemplate`/`resourceStore`/`keepFileExt` | DaoResourceFileStore.java:82-127 | IoC 容器 init（setXxx） | 全部请求线程 | NopIoC bean 构造完成后的安全发布 | 无缺陷（启动期写、运行期只读；`keepFileExt` 非 volatile 但 init 后不变） |
| static final `ERR_*` ErrorCode | DaoResourceFileStore.java:76-81 | 类初始化 | 全部线程 | final + 类初始化语义 | 无缺陷 |
| `record`/`resource`（DaoFileRecord final 字段） | DaoFileRecord.java:17-18 | 构造器 | 下载线程 | final 字段安全发布 | 无缺陷（注意：持有 detached ORM 实体，只读使用） |
| `_NopDatavCoreConstants` / `_NopFileDaoConstants` | 常量接口 | — | — | static final | 无缺陷 |
| NopFileRecordInputBean/OutputBean | nop-file-api beans | 请求作用域构造 | 响应序列化 | 无跨线程共享 | 无缺陷 |
| `NopFileRecordBizModel`（CrudBizModel 子类） | NopFileRecordBizModel.java | 注入协作者 | 请求线程 | IoC 安全发布；自身无新增可变字段 | 无缺陷 |

批 1 结论：file 三模块 + datav core 无 static 可变状态、无 ThreadLocal（基线 C1c 零命中实测一致）、无自建并发原语。C1 实体发现集中在批 2（datav service）。批 1 唯一的跨线程资源竞争点在 DaoResourceFileStore 的物理文件去重路径，按「资源生命周期」归类登记为 **C6-001（CL-014，见 C6-resource-lifecycle.md）**，此处不重复编号。

---

# C1 共享可变状态与可见性 — 初审（批 2：nop-datav-service）

- 审计单元：82f/15,509（逐文件：20 个并发相关核心文件全文精读 + 62 个其余文件实例字段/构造全扫描确认无状态）
- 五类载体盘点结论：
  - **static 可变**：零命中（全部 static final 常量/Logger/不可变注册表）。
  - **ThreadLocal**：零命中（与基线 C1c 一致）。
  - **全局注册表**：`PanelComponentRegistry.INSTANCE`（:27）构造期填充、`componentsView` 不可变视图——无缺陷；`GlobalExecutors.globalWorker()` 为平台共享池（使用方式问题归 C1-007）。
  - **singleton 实例可变字段**：全部为 `@Inject` 协作者（启动期注入、运行期只读），见下表。
  - **参数逃逸**：`PanelDataBinder`（无状态，final 字段）跨 worker 线程共享——安全；`resolvedParams`/`PanelDataResult.rows`（unmodifiableList 包装、只读约定）跨线程共享——安全。

## 批 2 singleton 实例字段清单（判定为无缺陷项）

| 字段 | 文件:行 | 说明 |
|---|---|---|
| `panelQueryTaskDecorator` / `panelQueryExecutorOverride` | NopDatavDashboardBizModel.java:364/:370 | volatile 测试 seam，安全发布 |
| `queryResultCache` | :456 | volatile + synchronized(this) 双重检查锁，volatile 写回——DCL 正确（对照 F10 检查通过） |
| `cancelFlags`（ConcurrentHashMap） | NopDatavExportTaskBizModel.java:119 | 原语安全；**复合操作有竞态 → C1-001** |
| `executionStartHook` | :138 | 非 volatile、非 final 测试 seam → C1-008（P3） |
| `clock`（LongSupplier volatile） | NopDatavShareAccessGuard.java:29 | 测试 seam，volatile 安全 |
| `states`（LocalCache + per-key synchronized） | NopDatavShareAccessGuard.java:27 | KeyState 经 CHM computeIfAbsent 安全发布；6 处 synchronized(state) 全部锁同一 per-key 对象。**驱逐导致的 split-brain 计数分裂已在类 javadoc 声明（"被驱逐键的限流状态归零（等效窗口重开）"）**——已声明边界，不编号 |
| `scheduler`（IJobScheduler，非 volatile） | NopDatavReportScheduler.java:76 / NopDatavAlertScheduler.java:75 / NopDatavStuckTaskScanner.java:60 | 启动期注入、运行期只读，IoC 安全发布 |

## 批 2 发现条目

### [C1-001] cancelFlags 取消-完成竞态：终态覆盖 + 永久残留泄漏

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:222-235, 339-368`
- **证据片段**:
  ```java
  // cancelExportTask :225-233
  if (NopDatavExportTaskStatus.isTerminal(task.getStatus())) {   // T2 读：RUNNING
      return task;
  }
  cancelFlags.put(taskId, Boolean.TRUE);                          // T2 写：晚于 worker 终态
  task.setStatus(NopDatavExportTaskStatus.CANCELLED);             // T2 覆盖 SUCCEEDED
  // executeTask finally :367
  cancelFlags.remove(taskId);                                     // T1 已执行过，不再有兜底清理
  ```
- **严重程度**: P2
- **并发场景**: HTTP 请求线程（cancelExportTask）与 globalWorker 线程（executeTask 收尾段）共享 `cancelFlags` CHM 与任务状态行。
- **触发时序**: T1（worker）执行到 :344-349 写 SUCCEEDED 并在 :367 `cancelFlags.remove(taskId)`；T2（cancel 请求）在 T1 写终态**之前**执行到 :225 读到 RUNNING（非终态，通过检查）→ T1 完成终态写与 remove → T2 执行 :228 `cancelFlags.put(taskId, TRUE)` → T2 :230-233 把 SUCCEEDED 覆盖为 CANCELLED。后果一：任务终态语义违约（已成功交付文件的任务显示为已取消）；后果二：`cancelFlags` 中该 taskId 条目**永久残留**——worker 已退出，无任何路径再 remove，map 单调增长。
- **现状**: cancel 标志位用内存 CHM 承担跨线程取消信号，读（:225 terminal 检查）与写（:228 put + :230 状态写）之间无原子性，worker 侧 remove 与 cancel 侧 put 无序。
- **风险**: 长时运行下残留条目按竞态发生频次线性累积（F4 无界账本家族的内存变体，单条目小但只增不删）；终态覆盖使用户可见状态与已产生交付物（文件已落盘、fileRecordId 已写）不一致。
- **建议**: （a）cancel 侧改用 `cancelFlags.putIfAbsent` + 终态写改为条件更新（`update ... where status not in (terminal)`，平台 `IEntityDao.updateEntityDirectly` 前重读校验或 SQL 条件更新）；（b）残留兜底：executeTask 入口 `waitForTaskRow` 返回前对己方 taskId 做一次 `cancelFlags.remove` 语义收口，或把 flag 改为挂 DB 行（与 status 同事务）。
- **失效面枚举**（F3 同族清扫）: `cancelExportTask`（本条）；`ReportDeliveryExecutor` 无 cancel API——交付无用户取消路径，无同族写点；`AlertEvaluator` 状态行为 last-writer-wins（另立 C1-004）；`cancelFlags.remove` 全模块唯一调用点即 :367。
- **信心水平**: 确定
- **误报排除**: 非原语误用（CHM 本身线程安全）；竞态在复合操作层，且有时序具体的终态覆盖与泄漏双后果，不属"理论竞态"。
- **复核状态**: 已保留（回码验证：:225 terminal 读与 :228 put 的非原子序列属实，:367 remove 唯一调用点属实；现状：新发现——本单元无前序并发审计，cra-index 台账无同条目）

### [C1-002] 发布版本号分配 check-then-act：并发发布产生重复 snapshotVersion

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardBizModel.java:229, 920-928`；同族 `NopDatavScreenBizModel.java:229-241, 356-364`
- **证据片段**:
  ```java
  // DashboardBizModel.calculateNextVersion :920-927
  NopDatavDashboardSnapshot latest = daoProvider()
          .daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);  // 读 max(version)
  return latest == null ? 1L : latest.getSnapshotVersion() + 1;              // 无唯一约束保护地 +1
  ```
- **严重程度**: P2
- **并发场景**: 两个管理端请求线程并发对同一 dashboard 调 `publishDashboard`（@BizMutation，各自事务）。
- **触发时序**: T1 执行到 :925 读 latest=5（未提交）→ T2 执行到 :925 读 latest=5 → T1 插入 snapshot v6 并提交 → T2 插入 snapshot v6 并提交。结果：同 dashboard 两条 snapshotVersion=6；`rollbackDashboard(:282 findSnapshotByVersion)` 与 `getPublishedDashboard(:266 findLatestSnapshot)` 按 limit(1) 取行，回滚目标不确定。
- **现状**: 版本号分配为纯"读最大值+1"，无数据库唯一约束（dashboardId+snapshotVersion）兜底，无锁。
- **风险**: 版本轴是回滚/发布读取的主键语义载体，重复版本使"按版本回滚"与"最新版本"读数歧义；低频管理操作，触发面窄。
- **建议**: 快照表加 (dashboardId, snapshotVersion) 唯一约束 + 插入冲突重读重试；或对 dashboard 行 SELECT ... FOR UPDATE 后再分配版本。
- **失效面枚举**: `publishDashboard`（本条）；`NopDatavScreenBizModel.publishScreen` 同构 calculateNextVersion（:356）——**同族未修**，与本条同一修复批次处理；`saveDashboardLayout` 不走版本分配，无涉。
- **信心水平**: 确定
- **误报排除**: 非低频路径豁免范畴——后果是主键语义歧义而非仅性能；写出了具体 T1/T2 交错。
- **复核状态**: 已保留（回码验证：calculateNextVersion 读 max+1 无守卫属实；同族 ScreenBizModel.calculateNextVersion:356 实存；现状：新发现）

### [C1-003] 终态行多写者无 fencing：worker / cancel / stuck-scanner / recovery 四方竞争同一状态行

- **文件**: `NopDatavExportTaskBizModel.java:284-370`；`NopDatavReportDeliveryExecutor`→`ReportDeliveryExecutor.java:215-293`；`NopDatavExportTaskRecovery.java:103-133`；`NopDatavReportDeliveryRecovery.java:99-131`
- **证据片段**:
  ```java
  // executeTask 主成功路径 :344-349 —— 写 SUCCEEDED 前仅检查 cancelFlags，不检查 DB 当前状态
  if (Boolean.TRUE.equals(cancelFlags.get(taskId))) { ... return null; }
  task.setStatus(NopDatavExportTaskStatus.SUCCEEDED);
  dao.updateEntityDirectly(task);
  // NopDatavReportDeliveryRecovery.doScanStuck :121-127 —— scanner 直接覆盖 RUNNING→FAILED
  delivery.setStatus(NopDatavReportDeliveryStatus.FAILED);
  dao.updateEntityDirectly(delivery);
  ```
- **严重程度**: P2
- **并发场景**: globalWorker 线程（交付/导出执行体）、HTTP cancel 线程、stuck 扫描 job 线程（默认 60min 阈值）、重启恢复路径，四方以 last-writer-wins 写同一非终态→终态转换。
- **触发时序**（scanner vs worker 变体）: 长导出（50 面板 × 100k 行上限）运行 61 分钟 → T1 stuck 扫描按 createTime 阈值将其标 FAILED → T2 worker 在 62 分钟完成取数，:344 直接写 SUCCEEDED（无状态重验）→ FAILED 记录"复活"为 SUCCEEDED。交付侧更严重：`runDeliveryInSession` :219-251 加载 delivery 后**不检查已为终态**即置 RUNNING——被 scanner 已标 FAILED 的积压交付出队后照常执行并写 SUCCEEDED。
- **现状**: 终态转换无常量条件守卫（无 `update where status=...` 条件更新、无乐观锁显式版本比对后的语义分支）；仅 catch 分流与 markFailedSafe 检查 isTerminal。
- **风险**: 恢复语义与执行语义互相覆盖（F2 家族）：误杀的"FAILED"被成功覆盖后，错误信息丢失、审计不一致；积压交付复活导致重复发送邮件（详见 C5-005 积压场景）。
- **建议**: 统一终态写收口为条件更新（`where status in (pending,running)`）并检查更新行数；worker 执行体加载记录后对终态行短路退出（镜像 markFailedSafe 的 isTerminal 检查）。
- **失效面枚举**: `executeTask` SUCCEEDED 写（本条）；`runDeliveryInSession` RUNNING/SUCCEEDED 写（同族未修，本条覆盖）；`markFailedSafe`/`markCancelled`（已做 isTerminal 守卫——同族已修对照）；`rollbackSucceededToFailed`（**有意**覆盖终态，设计声明内，豁免）；scanner 两处 doScanStuck（无守卫写方）。
- **信心水平**: 确定（时序具体）；scanner 误杀在途长任务的概率由"60min 阈值 + 保守默认"压低，但积压队列场景（C5-005）使其变为可达。
- **误报排除**: 非平台 ORM 职责缺位——updateEntityDirectly 语义是按主键直接更新，条件更新能力平台可用（错误处理先例中 updateEntityDirectly 均配合应用层守卫）。
- **复核状态**: 已保留（回码验证：executeTask :344 写 SUCCEEDED 前仅查 cancelFlags 属实；runDeliveryInSession RUNNING 写 :249-251 在 try 外、加载后无终态检查属实；现状：**部分已知**——scanner-vs-worker 面由 TestNopDatavStuckTaskScan.testSlowExecutionRaceWorkerSuccessOverwritesScanFailed 反向钉死为已接受语义（face a 豁免），保留面收敛为 FAILED→RUNNING 复活（face b，积压场景可达）；跨单元关联 cra-index WI1.7 C7-001 / WI1.5 C7-001 终态 fencing 平台族）

### [C1-004] 告警状态行并发评估：重复通知 + loadOrCreateState 建行竞态

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/alert/AlertEvaluator.java:121-257, 278-289`
- **证据片段**:
  ```java
  // :278-289
  NopDatavAlertState state = dao.findFirstByQuery(q);   // T1/T2 同时读 null
  if (state != null) return state;
  return createInitialState(rule);                       // 各自 insert → 双行（无 UK 声明）
  // :236-248 rearm 判定读 lastNotifiedTime —— 双评估各自基于旧值判定 shouldRearm=true
  ```
- **严重程度**: P2
- **并发场景**: 同一 alertRule 的 cron 触发线程（executeScheduledAlert）与用户手动触发线程（evaluateAlertNow @BizMutation）并发评估同一规则。
- **触发时序**（重复通知变体）: 规则 TRIGGERED、rearmSeconds>0、冷静期已过 → T1 cron 读 state（lastNotified=T-1h）→ T2 manual 读同一 state（lastNotified 仍=T-1h）→ T1 发通知 → T2 发通知 → 各自回写 lastNotifiedTime。结果：单次冷静期窗口发出 2 封告警通知。建行变体：规则刚创建未初始化状态行时双路径并发 → 两条 NopDatavAlertState 行，状态机分叉（一行 OK 一行 TRIGGERED，findFirst 取行不定）。
- **现状**: 状态行无行锁/条件更新；`loadOrCreateState` 为 check-then-act 建行（ChatBiSessionManager.appendTurn 的 seq 用 UK 显式失败兜底——对照先例此处无兜底）。
- **风险**: 告警重复发送（值班疲劳/置信度损失）；状态行分叉后每次评估随机取行，转换历史不可追溯。
- **建议**: NopDatavAlertState 对 alertRuleId 建唯一约束 + insert 冲突重读；rearm 回写改条件更新（`where last_notified_time = 旧值`）实现乐观 fencing；或评估入口按 ruleId 取进程内互斥（平台 IResourceLockManager）。
- **失效面枚举**: `evaluate`（本条，cron+manual 双入口共享）；`createInitialState`（AlertRuleBizModel.initializeStateIfAbsent :114 同调——建行竞态同族）；`loadStateByRuleId`（只读）；`runDeferredWriteback`（REQUIRES_NEW 重载后回写，单线程 afterCommit 内，无竞态）。
- **信心水平**: 很可能（双触发入口真实存在：fireScheduledForTest/evaluateAlertNow 与 cron；生产并发窗口取决于 cron 与手动操作重叠）
- **误报排除**: 方法调用链两个入口均对外暴露（GraphQL action + scheduler），非"仅测试可达"；后果是用户可见的重复通知。
- **复核状态**: 已保留（回码验证：loadOrCreateState check-then-insert 无 UK 兜底属实，对照 ChatBiSessionManager.nextSeq 有 UK 先例；并发窗口窄但真实；现状：新发现）

### [C1-005] 导出并发限额 TOCTOU：同用户并发提交可越过 max-concurrent-per-user

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:166, 497-513`
- **证据片段**:
  ```java
  // checkConcurrencyLimit :507-511
  long current = dao.countByQuery(query);        // COUNT(pending+running)
  if (current >= max) { throw ...; }             // 校验与后续 INSERT 无原子性
  // createExportTask :174 随后 saveEntityDirectly(task)
  ```
- **严重程度**: P2
- **并发场景**: 同一用户多请求线程并发调用 createExportTask（各自治事务）。
- **触发时序**: max=3，当前 running=2 → T1、T2 并发执行 :507 count 都读到 2（<3 通过）→ 各自插入 pending → 在飞任务数达 4 > 3。突发 N 个并发提交可至多超限 N-1。
- **现状**: 限额校验为纯读-判-插，无信号量/无 DB 条件插入/无唯一约束可依赖（注：类 javadoc 声明"并发限额校验（请求线程同步）"仅说明执行线程，未声明原子性）。
- **风险**: 限额是防 globalWorker 池耗尽的背压手段（每任务可运行数分钟），超限削弱背压；后果有界（每任务行数受 CFG_DATAV_EXPORT_MAX_ROWS 约束），故 P2 而非 P1。
- **建议**: per-user 进程内 `ISemaphore`（平台 io.nop.commons.concurrent.semaphore）与 DB count 校验叠加；或接受近似语义并在 javadoc 显式声明"非原子上界"。
- **失效面枚举**: `createExportTask`（唯一导出创建入口）；报告任务无 per-user 限额（cron 驱动，触发频率由 cron 决定——无同族写点）；`checkConcurrencyLimit` 无第二调用方。
- **信心水平**: 确定
- **误报排除**: 非平台 DB 计数精度问题——是应用层 check-then-act 结构缺陷；写出了具体交错与超限后果。
- **复核状态**: 已保留（回码验证：countByQuery 与 saveEntityDirectly 之间无原子机制属实；现状：新发现）

### [C1-006] parseFilterFromUrl 双重加载 TOCTOU：中途删除致 NPE

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardBizModel.java:314-321`
- **证据片段**:
  ```java
  requireEntity(id, "parseFilterFromUrl", context);              // 第一次读（校验存在）
  Map<String, Object> parsed = DashboardFilterUrlCodec.parseQueryString(url);
  NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class)
          .getEntityById(id);                                     // 第二次读，无 null 检查
  List<DashboardParamDefinition> definitions = DashboardParamParser.parse(dashboard.getParamConfig());
  ```
- **严重程度**: P3
- **并发场景**: 请求线程（parseFilterFromUrl）与另一请求线程（deleteDashboard 级联）并发。
- **触发时序**: T1 :314 requireEntity 通过 → T2 删除该 dashboard 并提交 → T1 :319 getEntityById 返回 null → :321 NPE（500 而非 404 语义）。
- **现状**: 同文件其他方法均单次 requireEntity 后复用实体，唯此方法二次加载且漏 null 分支（对照 :263 getPublishedDashboard 单次加载）。
- **风险**: 用户可见 500/NPE，无数据损坏；触发窗口为两次 DB 读之间，极窄。
- **建议**: 复用 requireEntity 返回的实体（删除二次加载），或对第二次加载补 null 检查抛 ERR_DATAV_DASHBOARD_NOT_FOUND。
- **失效面枚举**: `parseFilterFromUrl`（唯一双重加载点）；其余 8 个 action 均单次 requireEntity——同族已修对照。
- **信心水平**: 确定
- **误报排除**: 非 error-handling 维度问题——本质是 TOCTOU 双读结构，归 C1。
- **复核状态**: 已保留（回码验证：:319 二次 getEntityById 无 null 分支属实，同文件其余 action 单次加载对照属实；现状：新发现）

### [C1-007] Semaphore 许可在 worker 任务内获取：等待许可的任务占用共享池线程（C4 邻接）

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavDashboardBizModel.java:522-535, 620-642`
- **证据片段**:
  ```java
  futures.add(CompletableFuture.supplyAsync(
          () -> runPanelQueryTaskBounded(decorated, callerContext, semaphore), executor)); // 全部先入队
  // runPanelQueryTaskBounded :622-641 —— 在 worker 线程内 acquire
  permits.acquire();          // 阻塞等待 → 该 globalWorker 线程被占用
  ...
  } finally { permits.release(); }
  ```
- **严重程度**: P2
- **并发场景**: 每请求最多 `max-panels=50` 个任务全部提交到共享 `GlobalExecutors.globalWorker()`（默认 maxPoolSize=30，与导出/报告交付共用）；请求内 `Semaphore(parallelism=4)` 限在飞查询。
- **触发时序**: 5 个看板请求并发（各 50 面板）→ 250 个任务入队 → 30 个 worker 线程全部取出任务阻塞在 `permits.acquire()`（每请求仅 4 个许可在飞，其余 46×N 排队任务占着线程干等）→ 后续提交到同池的导出/交付任务在 FIFO 队列中被整批 dashboard 任务阻塞（队头阻塞），延迟可达整批查询时长。无死锁（许可持有者与等待者同池同请求，许可释放不依赖第三方线程），但吞吐/延迟耦合跨业务。
- **现状**: javadoc 声明"排队任务不占许可"（属实）但未声明"排队任务占用 worker 线程"； acquire 位置（提交后、worker 内）使信号量只限 SQL 并发、不限池占用。
- **风险**: 面板查询突发可把导出/报告交付延迟推高（两者同为用户可感路径）；池=30 为平台默认，宿主未显式调大时更敏感。
- **建议**: acquire 前移到提交侧（`permits.acquire()` 于请求线程，任务内 finally release；注意中断恢复），或为面板查询/导出/交付分池（平台 IThreadPoolExecutor 多实例 + ThreadPoolConfig）。
- **失效面枚举**: `executePanelQueriesInParallel`（唯一 supplyAsync 提交点）；globalWorker 其他提交方：`NopDatavExportTaskBizModel.submitExecution :272`、`ReportDeliveryExecutor.submitExecution :175`（两者不受本问题影响，是被拖累方）。
- **信心水平**: 确定（结构确定；量化后果依赖负载画像）
- **误报排除**: 非平台池实现缺陷（平台侧只审使用方式）；非"有界即安全"误报——问题恰在界放错了位置。
- **复核状态**: 已保留（回码验证：acquire 位于 worker 执行体内 :623 属实；globalWorker maxPoolSize 默认 30 实测 CommonConfigs；现状：新发现，跨单元关联 cra-index WI1.2 C4-004 globalWorker 常驻阻塞 / WI1.1 C8-011 面压垄断族）

### [C1-008] 测试 seam `executionStartHook` 非 volatile：跨线程可见性无保证

- **文件**: `nop-datav/nop-datav-service/src/main/java/io/nop/datav/service/entity/NopDatavExportTaskBizModel.java:138, 305-310, 586-588`
- **证据片段**:
  ```java
  private Runnable executionStartHook;         // 非 volatile、非 final
  // worker 线程 :308
  if (executionStartHook != null) { executionStartHook.run(); }
  ```
- **严重程度**: P3
- **并发场景**: 测试线程写 setter（:586），globalWorker 线程读（:308）。
- **触发时序**: 测试线程 `setExecutionStartHookForTest(hook)` → 提交异步任务 → worker 读字段。无 happens-before 边（任务提交经 globalWorker.submit 有 HB——实际经队列传递可达安全发布；风险仅在 setter 调用与 submit 顺序之间无屏障的原语读）。同文件 `panelQueryTaskDecorator`（DashboardBizModel :364）同型 seam 用 volatile——族内不一致。
- **现状**: 生产路径恒 null（不阻塞）；同族 seam 一处 volatile 一处裸字段。
- **风险**: 仅测试确定性（理论漏读 null hook 导致 mutate-fail 测试假绿）；生产零影响。
- **建议**: 与 `panelQueryTaskDecorator` 对齐加 volatile（一行）。
- **失效面枚举**: `executionStartHook`（本条）；`submitExecutionInvokedCount`（AtomicInteger，安全）；`panelQueryTaskDecorator`/`panelQueryExecutorOverride`/`clock`（volatile，同族已修对照）。
- **信心水平**: 确定
- **误报排除**: 不按"测试代码不计"豁免——该 seam 位于生产类且既有同族先例采用了 volatile，属一致性收口。
- **复核状态**: 已保留（回码验证：:138 裸字段 vs DashboardBizModel :364 volatile 同族不一致属实；现状：新发现）

## 邻接观察（不编号，供合并裁定引用）

1. **ShareAccessGuard 驱逐 split-brain**：`state(key)` 返回的 KeyState 可能在加锁前被 LocalCache 驱逐，后继 computeIfAbsent 新建对象 → 同 key 计数分裂。类 javadoc 已声明"被驱逐键的限流状态归零（等效窗口重开）"——分裂是同一声明的瞬态形态，按已声明边界豁免。
2. **DCL/发布面复核通过项**：`getQueryResultCache`（:459-470）双检锁正确；`PanelComponentRegistry` 类初始化安全；`PanelDataBinder` 无状态跨线程共享正确；`newTaskContext`（:645-652）每任务新建 context 防 TransactionRegistry 串号——设计正确，登记为正向证据。
3. **ChatBiToolCallingLoop `.join()` 无超时**（ChatBiToolCallingLoop.java:193）：tool future 永不完成时请求线程无限期阻塞（F5 等待点无超时家族）——该维度属 C2/C3 正文，本审计编号范围外，登记供后续轮次。

## 维度复核结论

- 复核口径：全部 8 条初审 + 2 条深挖发现逐条回码验证（复核阶段允许并已对照 cra-index 台账 162 条 P1 查重）。
- 三态判定：**保留 10 / 降级 0 / 驳回 0**。无一条因证据不成立或误报排除反转。
- 现状标注：**新发现 10**（本单元 nop-file/nop-datav 无前序并发审计直接命中；cra-index 台账无 file/datav 条目）；已知未修复 0；已修复 0。
- 跨单元关联登记：C1-003 → 终态 fencing 平台族（cra-index WI1.5 C7-001 / WI1.7 C7-001 / WI1.6 C8-001）；C1-007 → globalWorker 常驻阻塞族（WI1.2 C4-004 / WI1.1 C8-011）；C1-R2-002 → 周期任务停摆平台族（WI2.1 nop-kernel-concurrency C3-001，29 站点绝缘层统一案）。
- 复核中修正的写作疑点：LimitedInputStream 实测为超限抛错语义（非静默截断），相应疑点未立项；changePublic 落库依赖调用方 ORM 会话，无更新丢失，不立项。

## 最终保留项

| CL | 发现 | 严重度 | 现状 | 跨单元关联 |
|---|---|---|---|---|
| CL-001 | C1-001 cancelFlags 取消-完成竞态（终态覆盖+泄漏） | P2 | 新发现 | — |
| CL-002 | C1-002 发布版本号 check-then-act 重复版本 | P2 | 新发现 | — |
| CL-003 | C1-003 终态行多写者无 fencing（worker/cancel/scanner/recovery） | P2 | 新发现 | WI1.5/WI1.7 C7-001, WI1.6 C8-001 |
| CL-004 | C1-004 告警状态行并发评估（重复通知+建行竞态） | P2 | 新发现 | — |
| CL-005 | C1-005 导出并发限额 TOCTOU | P2 | 新发现 | — |
| CL-006 | C1-006 parseFilterFromUrl 双读 TOCTOU NPE | P3 | 新发现 | — |
| CL-007 | C1-007 Semaphore 在 worker 内获取占用共享池线程 | P2 | 新发现 | WI1.2 C4-004, WI1.1 C8-011 |
| CL-008 | C1-008 executionStartHook 非 volatile | P3 | 新发现 | — |
| CL-019 | C1-R2-001 调度 job 注销非事务副作用（回滚后停摆） | P2 | 新发现 | — |
| CL-020 | C1-R2-002 周期任务注册失败永久停摆（无自愈/观测） | P2 | 新发现 | WI2.1 C3-001, WI1.6 C8-001 |
