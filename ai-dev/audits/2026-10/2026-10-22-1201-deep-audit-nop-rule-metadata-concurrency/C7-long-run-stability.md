# C7 长时运行稳定性 — 初审报告（盲审批次 1/2 · 批 B 主战场）

- **审计单元**: nop-rule-metadata（nop-metadata + nop-rule）
- **维度**: C7 长时运行稳定性（单调增长 / 挂死可检测性 / 重试风暴 / 自愈语义 / 时钟与期限 / 慢性病）
- **批次范围**: 批 B = nop-metadata-service 主战场 22f——quality 8f（MetaQualityCheckpointScheduler / MetaQualityCheckpointExecutor / CheckpointActionDispatcher / QualityResultWriter / MetaQualityRuleExecutor / MetaQualityScorer / QualityAlertWorkflowProcessor / QualityRuleJudgment）+ reconciliation 3f（ReconciliationExecutor / LocalReconciliationProcessor / IReconciliationProcessor）+ event（MetaModelChangedEventPublisher）/ sync（ExternalTableStructureReader / ExternalTableInfo / ExternalColumnInfo）/ profiling（MetaEntityProfiler / ProfilingColumnStats / ProfilingSnapshot）/ catalog（MetaCatalogCollector / CatalogTableStats）/ connection（MetaDataSourceConnectionProcessor / IMetaDataSourceConnectionProcessor）
- **盲审声明**: 本报告为初审（盲审批次 1/2）。按盲审纪律，撰写过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何已落盘文件；全部判定仅基于 live code（commit 81a7c228e7 基线）。`_gen/` 与 `_` 前缀生成物不作发现对象。本审计为只读审计，除本报告外未修改任何文件。
- **发现编号前缀**: C7-001 起；维度集外线索以 `nop-rule-metadata-CL-501` 起附记。
- **计数口径纪律**: 本报告涉及的数量结论均注明 grep 模式与排除项，可复现。

---

## 一、长时运行风险清单表（路径 × 失效形态 × 判定）

判定取值：**立案**（立发现条目）/ **已防护**（核实有有效防护，记表不立案）/ **已声明边界**（owner 注释/设计明文声明的限制）/ **平台边界**（平台基础设施承担，不重审）/ **低风险记录**（真实但瞬态/可自愈，不立案）。

| # | 路径 | 失效形态（C7 检查项） | 判定 |
|---|---|---|---|
| R1 | MetaQualityRuleExecutor / MetaEntityProfiler / MetaCatalogCollector 全部查询点 + MetaDataSourceConnectionProcessor.withConnection | 外部数据源查询无 statement/query timeout，cron 检查点线程可无限期阻塞（挂死不可检测，F5 面） | **立案 C7-001** |
| R2 | MetaQualityCheckpointScheduler.executeScheduledCheckpoint:216 | `catch (Exception)` 不捕 Error——Error 逃逸 → JobFireResult.ERROR → LocalJobScheduler 永久 FAILED（重启 JVM 才恢复），击穿 MA7.5-01「job 存活按 cron 继续触发」自愈契约 | **立案 C7-002** |
| R3 | MetaQualityCheckpointExecutor.executeSingleRule:319 → TableReferenceExecutor.executeOnExternalConnection → withConnection | 检查点逐规则建连：N 条 external 规则 = 每次运行 N 次 TCP+认证+拆连（不池化为已声明，逐规则重复未声明） | **立案 C7-003** |
| R4 | QualityResultWriter.append（insert-only）+ MetaModelChangedEventPublisher.publishEvent + QualityScore/Catalog/ProfilingResult/ReconciliationResult 写入点 | 6 张 append-only 时序表零 retention/清理/归档，cron + auto-score 驱动持续增长（F4 面） | **立案 C7-004** |
| R5 | CheckpointActionDispatcher.dispatch:197-202 | webhook/notify 投递失败仅日志 + DTO errors，无重投/无持久化待发队列——目标不可达窗口内的通知永久丢失 | **立案 C7-005** |
| R6 | LocalReconciliationProcessor.reconcile:60-90 / loadCandidates:93-103 | 对账逐行全池重载候选表 + 每候选每行重复 JSON parse + levenshtein O(R×P×L²)，候选池增长后二次劣化（N+1/重复计算面） | **立案 C7-006** |
| R7 | QualityAlertWorkflowProcessor.createAlertWorkflow:67-98 | 告警工作流每次 FAIL 执行新建实例，无 open-alert 去重；创建失败仅日志（告警丢失） | **立案 C7-007** |
| R8 | MetaQualityCheckpointScheduler.doRegister:302-319 | cron 配置漂移（运维改为非法值）→ addJob 失败时移除旧 job 防过期调度残留（MA7.5-03），removeJob 失败独立留证 | 已防护 |
| R9 | MetaQualityCheckpointScheduler.executeScheduledCheckpoint:216-233 | checkpoint 级业务异常被捕获、记 ERROR/WARN、返回带类型化 errors 的正常结果——job 不被永久置 FAILED（MA7.5-01） | 已防护（Exception 面；Error 残余面见 C7-002） |
| R10 | MetaQualityCheckpointScheduler.readScheduleCron:329-343 | extConfig 不可解析 → WARN 留证 + 视为无 schedule，不静默伪造 | 已防护 |
| R11 | NopMetaQualityCheckpointBizModel.checkpointRunLocks:171,203-206,266-269 | per-checkpoint 运行标记 putIfAbsent 获取 / 方法体最外层 finally 释放——异常与 Error 路径均释放，无泄漏链；键空间=checkpointId 有界 | 已防护 |
| R12 | MetaQualityCheckpointExecutor.execute:129-186 | 单规则失败隔离（per-rule try/catch + clearSession）+ AR-14 失败规则补写 ERROR 结果行防 findLatestResult 取旧行——部分成功语义显式、可对账 | 已防护 |
| R13 | ReconciliationExecutor.execute + NopMetaReconciliationConfigBizModel reconFetchLimit（P2-07） | 对账取数有上限（config 或 DEFAULT_MAX_QUERY_LIMIT），truncated 显式标记；候选序列化 per-row limit=50（AR-13）；中途失败整批不落库（全或无，无半程状态） | 已防护 |
| R14 | MetaEntityProfiler.loadSortedDoubles:361-379 | in-app 排序统计 MAX_IN_APP_SORT_ROWS=10000 上限 + 到限停读释放游标 + unavailable 降级（防 OOM） | 已防护 |
| R15 | MetaDataSourceConnectionProcessor | 建连超时 DriverManager.setLoginTimeout(5s)；withConnection finally 关连接（成功/异常路径对称）；按需建连不池化为类 javadoc 明文声明 | 已防护（全局副作用见 CL-501） |
| R16 | CheckpointActionDispatcher.dispatchWebhook:243-249 | webhook 显式 timeout（默认 30s）+ method/protocol/host 白名单 + 重定向 fail-closed——投递挂死面有界 | 已防护 |
| R17 | CheckpointActionDispatcher 类 javadoc:37-46 | webhook 在 store flush 后、框架事务提交前投递——提交阶段失败时 webhook 宣告的结果行实际未落库（半个成功） | 已声明边界（check2 P3-10 暂缓决策点，代码内 javadoc 明文；不立案） |
| R18 | MetaModelChangedEventPublisher 类 javadoc:37（「首版无订阅者」） | 事件表无消费者 | 已声明边界（但无 retention 未声明，并入 C7-004） |
| R19 | MetaQualityCheckpointScheduler.buildJobSpec:349-366 | TriggerSpec 仅设 cronExpr，未配置 misfireThreshold——错过的触发窗口重放策略完全由平台 LocalJobScheduler 默认值承担 | 平台边界（nop-job-api 提供misfireThreshold 能力，模块未调参；记录） |
| R20 | MetaQualityRuleExecutor.judgeFreshness:292-297（AR-15）+ ageMinutesFromNow:852-857 | freshness 负年龄（DB 时钟超前/未来时间戳）fail-loud 显式 FAIL + rawAgeMinutes 留证——时钟偏移不掩盖 | 已防护 |
| R21 | MetaQualityScorer.findLatestResult:316-321 | findLatestResult 按 executeTime DESC 取首——系统时钟回拨后新写入行时间戳早于旧行，评分短暂读到陈旧「最新」行；真实时间越过回拨点后自愈 | 低风险记录（瞬态、可自愈、无持久损坏；理论风险未具体化为真实触发链） |
| R22 | MetaQualityRuleExecutor.queryLong:709 等（每次查询 INFO 日志） | 日志放大：每规则每次运行 1-N 条 INFO；cron 高频 + 大量规则时日志量线性增长——级别可配置，无结构性缺陷 | 低风险记录 |
| R23 | MetaQualityCheckpointScheduler.init:130-157 | 启动 scanner 全量注册，单检查点失败隔离不抛崩；DB 整体不可用时 @PostConstruct 抛出=启动 fail-fast（低频路径粗失败可接受）；重启后 init 重新扫描 DB 注册——调度注册面自愈成立 | 已防护 |
| R24 | 全批次 | 重试风暴盘点：webhook/notify/对账/质量查询均无自动重试机制（无指数退避需求面） | 已防护（无重试即无风暴；通知丢失代价见 C7-005） |

---

## 二、发现条目

### [C7-001] 外部数据源质量/剖析/目录查询无 statement 超时——cron 检查点线程可无限期阻塞且不可检测

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityRuleExecutor.java:706-726`（同族：`queryTimestamp:728-743`、`querySingleValue:752-782`；`profiling/MetaEntityProfiler.java:517-588`；`catalog/MetaCatalogCollector.java:101-111`；`connection/MetaDataSourceConnectionProcessor.java:198-212`）
- **证据片段**:
  ```java
  private static long queryLong(Connection conn, String sql) {
      LOG.info("qualityRule SQL executed: sqlHash={}", sqlHashOf(sql));
      LOG.debug("qualityRule SQL: {}", sql);
      try (PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
          if (!rs.next()) {
  ```
  连接侧仅设建连超时（`MetaDataSourceConnectionProcessor.java:124` `DEFAULT_LOGIN_TIMEOUT_SECONDS = 5`，经 `DriverManager.setLoginTimeout` 只约束建连握手），Statement 层零超时设置。
- **严重程度**: P1
- **并发场景**: LocalJobScheduler 的 job 执行线程（cron 触发链）+ 同一数据源上的人工 GraphQL 请求线程（executeQualityRule / profileEntity / collectCatalog 同样无超时）
- **触发时序**: T1=cron tick 触发 `executeScheduledCheckpoint` → `executeCheckpoint` 获取运行标记 → 规则循环中某条 external 规则执行 `SELECT COUNT(*) FROM <千万级外部表>`（或 PG 上 SELECT 排队在未锁行后——PG 默认无 statement timeout / lock_timeout，驱动默认 socketTimeout=0）；T2=外部库负载升高或网络黑洞（连接半开）；后果：job 线程在 `rs.next()`/`executeQuery()` 无限期阻塞 → 运行标记持续被占 → 后续每次 cron tick 全部命中 `ERR_CHECKPOINT_ALREADY_RUNNING` fail-fast 仅记 WARN（`MetaQualityCheckpointScheduler.java:221-226`）→ 质量监控实质停摆，无任何超时/看门狗/stall 检测能发现该阻塞（唯一可见信号是被拒 tick 的 WARN 日志）。自愈手段仅剩重启 JVM。
- **现状**: 建连有 5s login timeout，但查询执行阶段（含网络半开等待）无任何超时；模块内 main 源码 `grep -rn "setQueryTimeout" --include="*.java" nop-metadata/` 仅命中 1 处测试桩（`TestMetaEntityProfilerSecurity.java:395`），main 面 0 命中。
- **风险**: 外部表规模不受平台控制（本仓库 check2 P1-2 注释已自认「外部表行数不受平台控制（可达千万级）」）；DB 锁等待/网络分区下 cron 管道单线程静默挂死数小时以上；挂死期间该 checkpoint 的运行标记持有使手动执行也被拒——F5「挂死生产者对健康检测不可见」形态。
- **建议**: 优先复用平台设施：(1) `MetaDataSourceConnectionProcessor.buildDataSource` 增加可配置的 queryTimeout/socketTimeout（如 `nop.metadata.datasource.query-timeout-seconds`，默认有界值），在 `withConnection` 内对传入 action 前置 `Connection.setNetworkTimeout`；(2) `MetaQualityRuleExecutor`/`MetaEntityProfiler`/`MetaCatalogCollector` 的 Statement 统一 `setQueryTimeout`（三处 query helper 已集中，单点可覆盖）。
- **失效面枚举**: 无超时查询点全清单——MetaQualityRuleExecutor.queryLong / queryTimestamp / querySingleValue；MetaEntityProfiler.queryLong / queryNullableDouble / queryNullableLong / queryString / loadSortedDoubles / topValues / readColumns（DatabaseMetaData.getColumns）/ probeNumeric；MetaCatalogCollector.countRows / countIndexes（getIndexInfo）；ExternalTableStructureReader.read / readColumns（getTables/getColumns）——14 处全部同一形态，无一设超时；ConnectionProcessor 侧集中修复可一次覆盖（F3 同族清扫结论：同族全未修）。
- **信心水平**: 确定
- **误报排除**: 不属于「有界声明存在的缓存/队列/池」误报——超时缺失不是声明边界：类 javadoc 只声明 5s login timeout，未声明查询无界；也不属于「平台基础设施自身不重审」——连接与 Statement 由本模块代码创建，超时策略属模块使用方式。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P1（次登记，与 C7-102 同缺陷合并裁定，见文末维度复核结论）**。零超时事实独立复跑确认（`setQueryTimeout`/`setNetworkTimeout`/`socketTimeout` 全单元 main 面 grep 0 命中）；login timeout 仅界建连（MetaDataSourceConnectionProcessor:124,140-154,199-212）、queryLong/queryTimestamp/querySingleValue 无超时（MetaQualityRuleExecutor:706-782）、运行标记全程持有（NopMetaQualityCheckpointBizModel:203,266-269）逐项重演；W12 信号面修正经 LocalJobScheduler.java:301-311,379 独立复核成立（RUNNING 挂死期间 scheduleNext 仅由 handleResult 调用，cron tick 静默跳过零日志）。

### [C7-002] executeScheduledCheckpoint 仅 catch Exception——Error 逃逸致 cron job 永久 FAILED，击穿调度自愈契约

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointScheduler.java:216-233`
- **证据片段**:
  ```java
            return checkpointBizModel.executeCheckpoint(checkpointId, null, null);
        } catch (Exception e) {
            // MA7.5-01：checkpoint 级执行错误不向外抛——否则 invoker 转 JobFireResult.ERROR 后
            // LocalJobScheduler 将 job 永久置 FAILED（addJob(allowUpdate=true) 仅对 WAITING/SUSPENDED
            // 重排程，FAILED 不复活，唯一恢复手段是重启 JVM）。记录日志并返回正常结果，job 存活按 cron
            // 继续触发；配置修复后下一次触发即恢复。
  ```
- **严重程度**: P2
- **并发场景**: cron job 线程单线程路径；Error 可源自 executeCheckpoint 链路任意点（如规则循环中 OOM、驱动类 NoClassDefFoundError、StackOverflowError）
- **触发时序**: T1=任一 `Error`（非 Exception）在 `checkpointBizModel.executeCheckpoint` 内抛出 → T2=`catch (Exception)` 不命中，Error 沿 beanMethod invoker 传播 → T3=invoker 转 `JobFireResult.ERROR` → LocalJobScheduler 按 javadoc 自述语义将 job **永久置 FAILED**（代码注释原文：「FAILED 不复活，唯一恢复手段是重启 JVM」）→ 该 checkpoint 的 cron 调度停摆且运维改配置无法复活。 MA7.5-01 的设计意图恰是消除这一停摆形态，但只覆盖了 Exception 半边。
- **现状**: MA7.5-01 防护面 = `Exception`；`Error`/`Throwable` 残余面未覆盖，与其注释声明的自愈契约存在缺口。
- **风险**: 低频（Error 罕见）但后果与 MA7.5-01 要消除的停摆完全一致，且静默——无「job 已死」的外部告警点；一次 OOM 波及即可能永久杀死多个 checkpoint 调度。
- **建议**: `catch (Exception)` 收窄改为 `catch (Throwable t)` 后按 Error/Exception 分支处理（Error 记 FATAL 级日志并仍返回错误结果 DTO，保持 job 存活）；或至少在 catch 外补 `catch (Throwable)` 兜底分支，防 invoker 层永久 FAILED。
- **失效面枚举**: 同族 catch 点全清单——本文件 `executeScheduledCheckpoint:216`（唯一 job 入口包装）；`MetaQualityCheckpointExecutor.execute:155` 的 per-rule `catch (Exception)`（Error 在此路径会被外层调度入口同样漏接，同根因）；`CheckpointActionDispatcher.dispatch:197` per-action `catch (Exception)`（Error 会中断后续 action 投递循环，但后果止于单次运行，不永久化）。同族中仅调度入口具有「永久停摆」后果。
- **信心水平**: 很可能（FAILED 不复活语义引自本文件注释对 LocalJobScheduler 的描述，平台侧行为未独立复核——按盲审平台边界纪律不展开）
- **误报排除**: 不是「低频路径粗粒度 synchronized」类合理豁免——这是失败路径的自愈契约缺口，且模块自身注释已把「job 永久 FAILED = 必须避免的停摆形态」钉为设计目标；报告的是契约与实现的不一致，非优雅性问题。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P2**。Error→永久 FAILED 链不再依赖注释转述、经平台源码独立复核成立：LocalJobScheduler.handleResult:352-361（err!=null→FAILED 且不再 scheduleNext）+ addJob→rescheduleAfterUpdate:256-264（仅 WAITING 重排）+ resumeJob:168-175（仅 SUSPENDED 复活）+ fireNow:218（拒绝 FAILED）——FAILED 无复活路径确认；Error 低频，P2 判级维持。

### [C7-003] 检查点执行逐规则建连：N 条 external 规则 = 每次运行 N 次 TCP+认证+拆连

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointExecutor.java:308-324`（配合 `tableref/TableReferenceExecutor.java:117-123`、`connection/MetaDataSourceConnectionProcessor.java:42-44,198-212`）
- **证据片段**:
  ```java
        // MetaQualityCheckpointExecutor.executeSingleRule —— 每条规则独立走 tableRefExecutor.execute
        return tableRefExecutor.execute(ref,
                (conn, metaData, productName) -> ruleExecutor.judge(conn, ref, effectiveSchema,
                        rule.getRuleType(), rule.getEntityType(),
                        rule.getParams(), rule.getSqlExpression(),
                        rule.getThreshold(), productName));
  ```
  `TableReferenceExecutor.executeOnExternalConnection` 每次 `execute` 调一次 `withConnection`；`MetaDataSourceConnectionProcessor` javadoc：「每次调用从 connectionConfig 构建一个 SimpleDataSource（**不池化**……）建连 → 执行 → finally 关闭」。
- **严重程度**: P2
- **并发场景**: cron job 线程（每次检查点运行串行执行全部规则）；多 checkpoint 并行时对外部源的建连压力叠加
- **触发时序**: 非竞态，为重复计算面：T1=cron 每 tick 触发检查点（如每 5 分钟）→ T2=该检查点解析出 N 条指向同一数据源的 external 规则 → T3=循环内逐规则 `withConnection`：同一数据源被完整执行 N 次「构建 SimpleDataSource + TCP 握手 + TLS + 认证 + 元数据读取 + 拆连」。长时运行下：每 tick 的连接建立次数 = 全部 active 检查点规则数之和，随规则配置增长线性放大（50 规则 × 每 5 分钟 = 14,400 次/日建连）。
- **现状**: 「按需建连、不池化」是类级已声明设计（针对调用粒度）；但「同一检查点同一数据源逐规则重复建连」未被声明为边界——检查点编排层完全可以在数据源维度复用一次连接执行整批规则。
- **风险**: 外部数据源连接风暴（认证/审计日志放大、max_connections 占用）；每次运行时长被 N 次建连 RTT 拉长，与 C7-001 叠加时扩大运行标记持有窗口（并发拒检概率上升）。
- **建议**: 在 `MetaQualityCheckpointExecutor.execute` 内按 `ref.getDataSource()` 分组，对同组规则复用单次 `withConnection`（callback 内循环执行 judge）；连接获取仍收敛在 `IMetaDataSourceConnectionProcessor` 单点，不引入新池化设施。
- **失效面枚举**: 逐次建连消费点全清单——MetaQualityCheckpointExecutor.executeSingleRule（检查点路径，本条主面）；NopMetaQualityRuleBizModel.executeQualityRule（单规则路径——单规则一次建连合理）；executeQualityRulesForDataSource（批量路径已在 withConnection callback 内循环全部规则，**形态正确**，可作修复模板）；QualityAlertWorkflowProcessor.reJudge（单次合理）；MetaEntityProfiler/MetaCatalogCollector 调用方（每次剖析/收集一表一建连，批量收集是否同批次复用取决于调用方，登记同族观察）。同族中批量路径已示范正确形态，检查点路径为漏修兄弟。
- **信心水平**: 确定
- **误报排除**: 不属于「不池化为已声明边界」误报——已声明的是 processor 层调用语义（每次调用一连接），本条报告的是编排层对同一数据源的调用次数缺乏归并，属调用方结构性重复，非 processor 实现问题。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P2**。检查点循环逐规则 executeSingleRule（MetaQualityCheckpointExecutor:129-186,319）→ TableReferenceExecutor.executeOnExternalConnection:107-134 每规则一次 withConnection，重复建连机制确认；批量路径 executeQualityRulesForDataSource（NopMetaQualityRuleBizModel:219-279）确在 withConnection callback 内循环全部规则，可作修复模板；与 C4-R2-002（同族跨维发现）Phase 6 一并合并裁量。

### [C7-004] 六张 append-only 时序表零 retention：cron + auto-score 驱动的无界持久化增长（F4）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityResultWriter.java:16-19,57-68`（主锚）；同族锚 `event/MetaModelChangedEventPublisher.java:123-141`
- **证据片段**:
  ```java
  /**
   * 质量结果写入共享 helper（架构基线 §2.7.3 D3）。把单规则判定结果（{@link QualityRuleJudgment}）追加为一行
   * 新的 {@link NopMetaQualityResult}（时序语义：executeTime=now，不覆盖旧行）。
   ...
        row.setDetails(JsonTool.stringify(judgment.getDetails()));
        resultDao.saveEntity(row);
        return row;
  ```
  ORM 模型侧（`nop-metadata/model/nop-metadata.orm.xml:2097-2100`）明文：「**无 cascadeDelete = 检查点删除后结果行保留（历史事实语义）**……唯一写位点 = QualityResultWriter（**insert-only**）」。
- **严重程度**: P1
- **并发场景**: cron job 线程（检查点周期执行，每规则每 run 一行 QualityResult + 每 affected 表一行 QualityScore）+ 请求线程（每次元数据 save/delete 一行 NopMetaModelChangedEvent，含 mediumtext 前后双快照）+ 人工触发的 profiling/catalog/reconciliation 运行
- **触发时序**: 非竞态，为单调增长：T1=检查点配置 cron（如每分钟）且规则持续 FAIL → T2=每 tick 追加 `规则数` 行 nop_meta_quality_result（details 为 json-4000）+ 每张 affectedTable 一行 nop_meta_quality_score（dimensionScores/ruleSummary/trend 三个 json-4000 列）→ T3=并行的每次元数据写操作追加一行 nop_meta_model_changed_event（beforeSnapshot/afterSnapshot 均 **mediumtext**，模块导入/SYNC 路径批量放大：NopMetaModuleBizModel 10 处、NopMetaDataSourceBizModel 10 处、NopMetaEntityBizModel 7 处发布点）。数月运行后六表行数与容量只增不减。
- **现状**: 清理机制核查（可复现口径：`grep -rn "daoFor(NopMetaQualityResult|NopMetaModelChangedEvent|NopMetaQualityScore|NopMetaProfilingResult|NopMetaCatalog|NopMetaReconciliationResult" --include="*.java" nop-metadata-service/src/main | grep -i "delete|remove"` → **0 命中**；全单元 `grep -rln "...删表名..."` 排除 target/_gen/test 后仅 UI/auth/beans 装配文件，无任何清理 job、TTL、归档路径）。六表全清单：nop_meta_quality_result、nop_meta_quality_score、nop_meta_catalog（「时序语义：collectedAt=now，不覆盖旧行」）、nop_meta_profiling_result（「per-execution 时序行」）、nop_meta_reconciliation_result（每次运行一行，details JSON 含全量行+候选）、nop_meta_model_changed_event。「首版无订阅者」是已声明边界，但「无 retention」未见任何声明。
- **风险**: F4 无界账本：天/周级运行后表容量线性增长（event 表 mediumtext 双快照最重）；QualityResult 增长同步放大 `MetaQualityScorer.findLatestResult` 的索引扫描基数字（虽有 IX_NOP_META_QRESULT_RULE(qualityRuleId,executeTime) 兜底查询形态）与 BaseX/列表页查询负载；磁盘耗尽后写入失败将反过来打断 cron 检查点执行链。
- **建议**: 引入 retention 机制（三选一或组合）：(1) 平台 nop-job 注册定期清理 job（按 changeTime/executeTime/collectedAt 滚动窗口 deleteByQuery，复用 `NopMetaLineageEdgeQueryAction.java:486-488` 已有 deleteByQuery 单语句删除先例）；(2) 各 BizModel 提供 `@BizMutation purgeBefore(timestamp)` 运维动作 + owner doc 声明保留策略默认值；(3) 至少对 nop_meta_model_changed_event（mediumtext 快照、无消费者）设最长保留窗口。
- **失效面枚举**: F3 同族清扫——六张 append-only 表 + 1 个衍生产：① nop_meta_quality_result（QualityResultWriter，insert-only，cron 驱动）② nop_meta_quality_score（NopMetaQualityScoreBizModel.doSave，每 checkpoint 运行每表一行）③ nop_meta_catalog（NopMetaDataSourceBizModel:878-891，收集即追加）④ nop_meta_profiling_result（剖析时序行）⑤ nop_meta_reconciliation_result（每运行一行）⑥ nop_meta_model_changed_event（每变更一行，mediumtext 双快照，无消费者）⑦ 衍生：qualityBreachApproval 工作流实例（见 C7-007，每次手动 FAIL 执行一实例）。七处全部无清理路径——同族全未修，无一豁免。
- **信心水平**: 确定
- **误报排除**: 不是「标准 CRUD 实体可手动删除」的误报——六表均有标准 CRUD delete 面，但 cron/自动链路以机器速率写入、人工清理以人速进行，速率差即无界增长；ORM 注释钉死 insert-only 与历史事实语义，证明追加是设计意图而 retention 缺失是未覆盖面。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P1**。七表清理路径 grep（六表+NopMetaManifest 的 daoFor(...).delete|remove|purge|prune，扫 nop-metadata/nop-job/nop-batch/nop-sys main 面）0 命中复核成立；ORM insert-only/历史事实语义（nop-metadata.orm.xml:2095-2098）与 event 双快照 mediumtext（orm.xml:2812-2815）确认；发布点实测 Module 9+DataSource 9+Entity 6=24 处（原报告 10/10/7 略高估，实质不变）；cron 检查点为模块设计主路径、机器速率增长现实，P1 维持。

### [C7-005] webhook/notify 投递失败无重投与持久化死信——通知单次机会、永久丢失

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/CheckpointActionDispatcher.java:185-204,224-281`
- **证据片段**:
  ```java
            } catch (Exception e) {
                LOG.error("action dispatch failed: errorCode={} actionType={} checkpointId={}",
                        NopMetadataErrors.ERR_CHECKPOINT_ACTION_DISPATCH_ISOLATED.getErrorCode(),
                        actionType, cp.getCheckpointId(), e);
                errors.add(buildDispatchError(actionType, e));
            }
        }
  ```
- **严重程度**: P3
- **并发场景**: cron job 线程 / 请求线程单次投递；外部 webhook 目标服务独立可用性
- **触发时序**: T1=检查点运行完成进入 dispatch → T2=webhook 目标恰在维护窗口（连接拒绝/超时 30s/5xx）→ T3=异常被 per-action catch 吞为 errors 条目 + ERROR 日志 → 本次运行的通知**永久丢失**（无待发队列、无重试计数、无 dead letter；cron 下一 tick 若规则转 PASS，消费方永远收不到此前的 FAIL 告知）。
- **现状**: 投递结果有三个可见面（ERROR 日志、summary.errors/DTO errors、store 结果行仍落库），但恢复面为零——失败即终态。
- **风险**: 告警型 webhook（质量违约通知）在目标短暂不可达时静默缺报，消费方无感知；运维只能靠翻日志发现漏发。
- **建议**: 最小修复：投递失败条目落一行持久化待发记录（复用既有 append-only 模式或 errors 面），配合手动/定时重投动作；或 owner doc 明文声明「投递至多一次、失败不重投」的能力边界，使消费方可按需自建轮询兜底。
- **失效面枚举**: 投递失败处理点全清单——dispatchWebhook（HTTP 面本条主锚）；dispatchNotify（IMessageService.send 同为单次机会，同族同形态）；`NopMetaQualityRuleBizModel:173-178` 告警工作流创建失败仅 ERROR 日志（告警丢失同族，另见 C7-007）。三处全部「失败留日志不留恢复面」——同族全未修。
- **信心水平**: 确定
- **误报排除**: 不是 C7-004/R17 已声明边界的重复报告——R17 是「提交前投递」时序残留（javadoc 明文暂缓）；本条是「投递失败无恢复面」，javadoc 只声明了隔离语义（「投递失败/超时不可能回滚 store」），未声明单次机会语义。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P3**。dispatch 循环 per-action catch（CheckpointActionDispatcher:197-202）仅 LOG.error + errors 条目、无重投/待发队列/死信复核成立；webhook 显式 30s 超时（:249）与 3xx/非 2xx fail-closed 确认；「单次机会」语义未被任何声明覆盖，P3 维持。

### [C7-006] 对账逐行全池重载 + 每候选重复解析——候选池增长后对账时长二次劣化（N+1/重复计算）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/reconciliation/LocalReconciliationProcessor.java:60-103`（配合 `ReconciliationExecutor.java:76-111`）
- **证据片段**:
  ```java
        List<NopMetaReconciliationEntity> pool = loadCandidates(targetType, identifierSpace);
        if (pool.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReconciliationCandidate> matched = new ArrayList<>();
        for (NopMetaReconciliationEntity entity : pool) {
            double score = score(value, entity.getEntityName(), matchStrategy);
  ```
  `loadCandidates` 每次 `reconcile` 调用执行一次无分页 `dao.findAllByQuery(q)`；`ReconciliationExecutor.execute` 的行循环内逐行调用 `reconcile`（:89-91）。
- **严重程度**: P2
- **并发场景**: 对账在请求线程同步执行（executeReconciliation BizModel action）；并发对账请求各自重载同一候选池
- **触发时序**: 非竞态，为重复计算面：T1=对账配置目标表取回 R 行（fetch-limit 有界，默认对齐 DEFAULT_MAX_QUERY_LIMIT）→ T2=循环 R 次，每次 `reconcile` 重新 `findAllByQuery` 载入**同一份**候选池（P 行）→ T3=每行对 P 个候选逐个做 levenshtein（O(L²)）+ `parseProperties` 重复 JSON.parse 同一批不可变 properties。总代价 R×P×(一次 DB 全池查询 + O(L²) 评分 + JSON 解析)；候选缓存表随元数据增长到万行级、R=1000 时单次对账即千万次编辑距离计算 + 1000 次全池查询。
- **现状**: 取数行数有界（P2-07 fetch-limit 已防护），但候选池加载与评分无任何复用：同一池在同一对账运行内被完整重建 R 次，跨运行零缓存。
- **风险**: 候选池自然增长（元数据越积累越多）后对账请求时长超线性劣化，占用请求线程；C7 关注的「N+1/重复计算面」直接命中。
- **建议**: `ReconciliationExecutor.execute` 开头一次性取候选池（经 processor 暴露 `loadCandidates` 或批接口），行循环内复用；`exact` 策略可下沉为 DB 等值查询替代全池扫描；properties 解析结果可随池加载一次缓存。
- **失效面枚举**: 同族全池重载点全清单——LocalReconciliationProcessor.loadCandidates（唯一实现，本条主锚）；ReconciliationExecutor 是唯一调用方（行循环内）；`MetaQualityScorer.findLatestResult`（每规则一查询的 N+1 形态，但有索引兜底且单次评分规则数有界，记观察不入案）；`MetaQualityCheckpointExecutor.resolveRules:256-276`（tableIds 逐个 getEntityById，配置项有界，记观察）。同族中仅对账路径达到可量化劣化规模。
- **信心水平**: 确定
- **误报排除**: 不是「热路径默认 P2/P3 候选、无量化依据不报告」的违反——本条给出量化链（R×P×O(L²)，R 有界默认 1000、P 随元数据无界增长），且 N+1/重复计算面是 C7 维度正文对 reconciliation 的点名检查项，非 C9 泛化性能诉求。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P2**。行循环逐行 reconcile（ReconciliationExecutor:89-91）× 每次 reconcile 全池 findAllByQuery（LocalReconciliationProcessor:93-103）× 每候选 parseProperties 重解析，R×P 重复计算结构逐项确认；候选池随元数据增长后对账时长二次劣化，P2 维持。

### [C7-007] 告警工作流无去重：持续 FAIL 的 ERROR 规则每次执行新建工作流实例

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityAlertWorkflowProcessor.java:67-98`（触发点 `entity/NopMetaQualityRuleBizModel.java:170-179`，批外证据）
- **证据片段**:
  ```java
        IWorkflow wf = wfManager.newWorkflow("qualityBreachApproval", 1L);
        ...
        wf.start(vars, ctx);
        return wf.getWfReference();
  ```
  调用点：`executeQualityRule` 在每次判定 FAIL 且 severity=ERROR 时无条件调用 `createAlertWorkflow`，无「该规则已有未决告警实例」查重。
- **严重程度**: P3
- **并发场景**: 请求线程（手动单规则执行）；并发双击同一 FAIL 规则会创建两个并行告警实例（无幂等键）
- **触发时序**: T1=规则持续 FAIL（如外部源数据持续违约）→ T2=运维/调度反复执行 `executeQualityRule` 验证 → T3=每执行一次新建一个 qualityBreachApproval 工作流实例（bizObjId 指向不同 result 行）→ 实例数随执行次数线性累积，审批人收到重复告警。
- **现状**: createAlertWorkflow API 无去重契约；调用点也无查重；创建失败仅 ERROR 日志（告警丢失，与 C7-005 同族）。检查点/cron 路径不触发告警（仅手动单规则路径）——告警覆盖面本身不对称，登记观察。
- **风险**: 工作流实例表无界累积（并入 C7-004 失效面第⑦项）；审批噪音使真实违约被淹没。
- **建议**: createAlertWorkflow 增加幂等门禁：创建前查「bizObjId=规则ID 且未终结」的在途告警实例（WfReference 查询）或以 ruleId 为幂等键；至少在 owner doc 声明「每执行一告警、无去重」的语义。
- **失效面枚举**: 告警创建点全清单——QualityAlertWorkflowProcessor.createAlertWorkflow（唯一创建点）；唯一调用方 NopMetaQualityRuleBizModel:174（手动路径）；检查点编排路径（executeCheckpoint）不创建告警（覆盖面不对称，观察项）。同族无其他兄弟。
- **信心水平**: 确定
- **误报排除**: 不是「手动路径人工速率有界」的豁免——FAIL 态规则的验证-重试是质量运维的标准动作，速率由人但累积是结构性的（无任何收敛机制），且与 F4 无界账本家族同根。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P3**。createAlertWorkflow（QualityAlertWorkflowProcessor:67-98）无去重门禁、唯一调用方 NopMetaQualityRuleBizModel:171-179 无查重、创建失败仅 ERROR 日志，三点独立确认；人工速率累积 + 与 C7-R3-001（创建失败残留）/C7-005（投递丢失）构成告警通道三失效层，Phase 6 可合组裁决。

---

## 三、维度集外线索（CL 附记，强信号）

### [nop-rule-metadata-CL-501] DriverManager.setLoginTimeout 为 JVM 全局一次性副作用（C1 全局可变状态形态）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:140-154`
- **形态**: bean 构造函数将 JVM 级全局 `DriverManager.loginTimeout` 置 5s，一次性格局化（无重断言）：部署内任何其他组件（其他模块、驱动工具类）后写该全局值即静默失去本模块 5s 建连超时防护；反之本模块也改写全平台 DriverManager 用户的全局值（跨模块影响）。注释已自认「{@code SimpleDataSource#setLoginTimeout} 为 no-op，实际靠 DriverManager 全局」。
- **建议方向**: C1/C6 维度复核时应评估：连接超时下沉到 SimpleDataSource/连接属性（jdbcUrl connectTimeout 参数校验白名单内放行），去除全局副作用。
- **信心水平**: 很可能
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（移交 C1 处置）**。构造器一次性写 JVM 全局 DriverManager.loginTimeout（MetaDataSourceConnectionProcessor:140-154）确认；与 C4 维度 CL-205 同点登记，Phase 6 归并。

### [nop-rule-metadata-CL-502] 单例 BizModel 多处非同步 lazy-init check-then-act（C1/C2 形态，弱）

- **文件**: `entity/NopMetaQualityCheckpointBizModel.java:154-160`（tableRefExecutor/checkpointExecutor/actionDispatcher）、`entity/NopMetaQualityRuleBizModel.java:109`、`quality/QualityAlertWorkflowProcessor.java:56,167-172`、`entity/NopMetaQualityScoreBizModel.java:65-69`
- **形态**: singleton BizModel 实例字段经 `if (field == null) field = new ...` 惰性初始化，无同步/无 volatile。并发首调可能双构造。已核实被构造对象（TableReferenceExecutor/MetaQualityCheckpointExecutor/CheckpointActionDispatcher/MetaQualityScorer）均无状态或仅持注入依赖，双构造无正确性后果——理论竞态、无具体化损害链。C1/C2 维度复核时按「写不出交叠后果降 P2 理论风险」口径处置即可，本维度不立案。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（理论竞态，维持不立案）**。惰性初始化点（NopMetaQualityCheckpointBizModel:153-160 等）复核确认，被构造对象无状态，交叠后果写不出。

### [nop-rule-metadata-CL-503] webhook/notify 投递 payload 携带全量执行摘要（C8 边界面，弱）

- **文件**: `quality/CheckpointActionDispatcher.java:247,498-504`
- **形态**: `request.setBody(JsonTool.stringify(summary))` 与 notify envelope 携带完整 summary（含逐规则 results 数组）。payload 规模 = 规则数 × 每条目字段，规则数配置有界故当前有界；但规则数无平台侧上限校验，规则配置膨胀时 webhook body 与外部目标承受力无约束核对。C8 维度复核时按「生产-消费边界」口径评估是否需要 payload 截断/摘要化开关。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（C8 边界面，弱）**。webhook body（CheckpointActionDispatcher:247）与 notify envelope（:498-504）携带全量 summary 确认；规则数配置有界故当前有界，维持移交 C8。

---

## 四、计数与口径汇总

| 项 | 口径 | 值 |
|---|---|---|
| 清单表行数 | R1-R24 | 24 行 |
| 发现条目 | C7-001 ~ C7-007 | 7 条（P1×2：C7-001/C7-004；P2×3：C7-002/C7-003/C7-006；P3×2：C7-005/C7-007） |
| 已防护（记表不立案） | R8-R16, R20, R23, R24 | 12 行 |
| 已声明边界/平台边界/低风险记录 | R17-R19, R21-R22 | 5 行 |
| CL 线索 | nop-rule-metadata-CL-501 ~ 503 | 3 条（C1 形态×2、C8 形态×1） |
| 无超时查询点（C7-001 失效面） | `grep -rn "setQueryTimeout" --include="*.java" nop-metadata/` main 面 | 0（仅测试桩 1 处） |
| append-only 表清理路径（C7-004 失效面） | 六表 daoFor(...) delete/remove grep main 面 | 0 命中 |


---

# C7 初审追加（批 A+批 C+批 D）

> 审计日期: 2026-10-22 | 子代理: C7 维度盲审批次 2/2 | 基线: `_tmp/cra-baseline-nop-rule-metadata.txt` @ 81a7c228e7
> 批次范围: 批 A = nop-rule main 全量（71f/5,205 行，剔除 `_` 前缀生成物）；批 C = nop-metadata-service `entity` 包 42f；批 D = nop-metadata-service `query` 27f + `lineage`/`field`/`search`/`sqlview`/`tableref`/`contract`/`datasource`/`manifest`/`security` + service 根散文件 + `connection`（重点面点名）+ nop-metadata-api(30f, plan-first 只读)/core/dao/app。
> 盲审纪律: 未读 ai-dev/audits|plans|bugs|lessons|backlog 任何文件（含本维度目录既有内容）；live code 为准；只读审计。`connection`/`quality`/`reconciliation`/`profiling`/`event`/`catalog`/`sync` 包文件仅按重点面点名或同族清扫（F3）引用，不做整包初申——整包归属前一批次。

## 一、长时运行风险清单

### 1.1 单调增长源清单（C7-1 口径）

| # | 增长源 | 位置 | 触发频率 | 清理/TTL 路径 | 判定 |
|---|---|---|---|---|---|
| G1 | NopMetaCatalog 快照行（每表每收集追加 1 行） | `NopMetaDataSourceBizModel.appendCatalogRow`（L881-892，调用点 L769/L838） | 每次 collectCatalog / collectCatalogForTable mutation（外部可循环调用） | **无**（全仓 grep delete/clean/prune/purge/retention = 0 命中） | 缺陷 → C7-101 |
| G2 | NopMetaManifest 版本行（每次 generateManifest 追加 1 行，version 单调递增） | `NopMetaModuleBizModel.generateManifest` L600-607 | 每次生成 manifest | **无** | 缺陷 → C7-101 |
| G3 | NopMetaProfilingResult 快照行（每次 profileEntity / profiling 规则执行追加） | `NopMetaEntityBizModel.appendProfilingResult` L395-405；`NopMetaProfilingRuleBizModel` L113/L155-161 | 每次 profiling mutation；可被规则调度反复触发 | **无** | 缺陷 → C7-101 |
| G4 | NopMetaQualityResult 结果行（每规则每 checkpoint run 追加） | `MetaQualityCheckpointExecutor` L132/L177、`QualityResultWriter` L57/67（此两文件属前批次包，此处仅 F3 同族登记） | 每次 checkpoint 执行（cron 周期触发） | **无** | 同族 → C7-101 失效面 |
| G5 | NopMetaModelChangedEvent 事件账本行（每次 save/delete/publish 追加） | `event` 包 publisher（前批次文件，F3 登记） | 每次 CRUD/导入/凭证操作 | **无** | 同族 → C7-101 失效面 |
| G6 | `EXTERNAL_TABLE_UPSERT_LOCKS` static ConcurrentHashMap（键 = (moduleId,tableName,schema)） | `NopMetaDataSourceBizModel` L1053/L1070 | 每次 syncExternalTables 单表 upsert | **无 remove**；代码内注释声明"key 集合有界（受元数据规模约束），长期持有可接受"（L1050-1052） | 声明边界 + 陈旧键残余 → C7-103（P3） |
| G7 | nop-rule 执行面累积 | `RuleRuntime.logMessages/outputLists` 等 | 每请求 | 请求级实例，随请求丢弃 | 无缺陷 |

### 1.2 挂死点清单（C7-2 / F5 口径）

| # | 挂死点 | 位置 | 超时/看门狗 | 判定 |
|---|---|---|---|---|
| H1 | 外部库语句执行（COUNT/ profiling 扫描 / 质量规则 SQL / 跨库取数 / 字段类型推断）无 queryTimeout | `MetaCatalogCollector` L104、`MetaEntityProfiler` L364/479/523/537、`MetaQualityRuleExecutor` L559/618/711/732/757、`MetaJoinExecutor` L648+、`AggregationHelper` L87+、`MetaEntityQueryExecutor` L102+、`SqlViewFieldTypeInferrer` L122 | 无（`grep setQueryTimeout nop-metadata --include="*.java"` main 面 0 命中）；建连超时仅靠全局 `DriverManager.setLoginTimeout(5)` | 缺陷 → C7-102 |
| H2 | `DriverManager.setLoginTimeout` 全局可变状态作为唯一建连超时载体 | `MetaDataSourceConnectionProcessor` L140-154 | 构造器一次性设置；宿主内任何其他组件重置即静默失效，无再断言 | 缺陷（并入 C7-102） |
| H3 | checkpoint 挂死后 `checkpointRunLocks` 永久占用 | `NopMetaQualityCheckpointBizModel` L203-206/L266-269 | finally 释放依赖线程存活；挂死线程永远到不了 finally | 后果面 → C7-102 |
| H4 | 无限等待/自旋/Thread.sleep | 本批次全部 main 面 | `grep "Thread.sleep|while (true)"` 0 命中；BFS/解析循环全部有界（visited 集/maxDepth/maxProbe/maxEdges） | 无缺陷 |

### 1.3 重点面核验结论（无发现面，登记为清扫证据）

| 重点面 | 结论 | 证据 |
|---|---|---|
| 规则编译缓存陈旧性/热更新一致性 | 无缺陷。编译产物缓存在 RuleModel 实例字段上（`RuleModel.executableRule`），模型经 `ResourceComponentManager` 加载并以 `traceDepends(DaoEntityResource)` 建立实体依赖（`DaoRuleModelLoader` L84）；热更新 = 资源失效重建新 RuleModel 实例，旧编译产物随旧实例丢弃，无跨版本陈旧面。懒编译 check-then-act 由 `RuleManager.getExecutableRule` 的 `synchronized(ruleModel)` 全程守卫（唯一写点 RuleManager L55-65 + RuleModelCompiler L72，同锁可重入） | `RuleManager.java:55-65` |
| nop-rule 执行计数累积 | 无计数器、无 static 可变执行状态；RuleRuntime 全部状态为请求级 | `RuleRuntime.java:33-75` |
| 连接处理器池化/泄漏 | 声明"不池化、按需建连、finally 关闭"（类注释 L43-50）；`withConnection`/`testConnect` 两路径均 finally `IoHelper.safeCloseObject(conn)`（L209-211/L252-254）；超时缺口见 C7-102 | `MetaDataSourceConnectionProcessor.java:198-255` |
| 查询面缓存退化 | query 包无任何跨请求缓存（无 ICache/MapCache/static 可变容器，grep 0 命中），无退化面；重复计算面见 CL-604 | grep -E "ICache|MapCache|ConcurrentHashMap" query/ = 0 命中 |
| BizModel 导入导出中断恢复 | `importOrmModels` per-path REQUIRES_NEW + 索引反向对账（AR-08）；`migrateDataSourcesCredential` per-row REQUIRES_NEW + 确定性排序 + 幂等反查（中断重跑收敛，注释 L408-416）；`syncExternalTables` per-key 锁 + per-table REQUIRES_NEW 部分持久化契约（AR-17）——三者中断恢复语义完整 | `NopMetaModuleBizModel.java:254-299`、`NopMetaDataSourceBizModel.java:407-546` |
| 游标/分页状态残留 | 无服务端游标；queryData/queryJoinData/queryAggregation 均为无状态 limit/offset，负值拒绝/缺省封顶（MA7.4-03/AR-09） | `NopMetaEntityBizModel.java:445-470` |
| lineage/search/manifest 全量重建 | 三者均有显式 fail-fast 上界（lineage maxEdges/maxTables L419-447、search PURGE_ENUMERATION_LIMIT L37、manifest 全量加载注释声明可接受 L613-615）；重建失败残留已有对账（lineage 先清后建 P2-04 L190/L262、search purge 失败写入 IndexResult AR-23②③ L122-133） | 见左 |

## 二、发现条目

### [C7-101] 时序快照/结果/事件类表全部只增不删，长时运行单调膨胀且无任何保留策略

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:881-892`（主证据）；同族：`NopMetaModuleBizModel.java:600-607`、`NopMetaEntityBizModel.java:395-405`、`NopMetaProfilingRuleBizModel.java:113,155-161`
- **证据片段**:
  ```java
  // NopMetaDataSourceBizModel#appendCatalogRow —— 时序语义：collectedAt=now，不覆盖旧行
  private void appendCatalogRow(String metaEntityId, CatalogTableStats stats, IServiceContext context) {
      NopMetaCatalog row = catalogBiz.newEntity();
      row.setMetaEntityId(metaEntityId);
      ...
      row.setCollectedAt(CoreMetrics.currentTimestamp());
      row.setDetails(buildDetailsJson(stats));
      catalogBiz.saveEntity(row, null, context);
  }
  ```
  javadoc 明示追加语义（`NopMetaDataSourceBizModel.java:731`）：「时序语义：每表每收集一次追加新的时序快照行写入 NopMetaCatalog」「不覆盖旧行」。
- **严重程度**: P2
- **并发场景**: 不依赖并发；单线程周期性 mutation 即可累积。触发方：GraphQL 手工调用 + 质量检查点/采集类调度（如部署方将 collectCatalog/profiling 挂 cron，则按 cron 周期 × 表数线性增长）。
- **触发时序**: 非竞态、无交错序列——T1 每次 collectCatalog 对 N 张 external 表各 append 1 行；天/周级运行下行数 = N × 调用次数，单调递增。
- **现状**: NopMetaCatalog（G1）、NopMetaManifest（G2）、NopMetaProfilingResult（G3）三张表在整仓（含 nop-job/nop-batch/nop-sys）无任何 delete/cleanup/retention/prune 路径。计数口径（可复现）：`grep -rn "NopMetaCatalog" --include="*.java" . | grep -iE "delete|clean|prune|purge|retention"` → 0 命中；NopMetaManifest、NopMetaProfilingResult 同口径 0 命中。代码注释只声明了「追加」语义，未声明保留/清理对应物。
- **风险**: F4 族（无界账本只增不删）。元数据目录规模虽然有界，但快照行按调用次数无界增长：collectCatalog 每次对每张表追加一行，profiling/checkpoint 可被调度反复触发；数周级运行后这些表成为最大表，拖慢 buildGlobalClassNameToModuleId 之外的所有按表查询与备份，且旧行从未被任何读路径消费（无按 collectedAt 取最新之外的用途声明）。
- **建议**: 为三表引入统一保留策略（按 collectedAt/generatedAt/snapshotTime 的 TTL 或 per-entity keep-last-N 清理 action），复用平台 `IJobScheduler` 注册低峰清理 job；或至少在 owner doc 显式声明「保留策略由部署方 DB 侧负责」的边界（当前两者皆无）。event 账本（G5）属 event 包 owner，同族缺陷移交前批次文件 owner 合并裁定。
- **失效面枚举**: NopMetaCatalog（本条主证据，未修复）；NopMetaManifest（`NopMetaModuleBizModel.generateManifest`，未修复）；NopMetaProfilingResult（`NopMetaEntityBizModel.appendProfilingResult` + `NopMetaProfilingRuleBizModel` 两处写点，未修复）；NopMetaQualityResult（`QualityResultWriter.append`，文件属前批次包，同族未修复）；NopMetaModelChangedEvent（event 包，同族未修复）。G6 锁 map 见 C7-103，不属本条。
- **信心水平**: 确定
- **误报排除**: 非「有界声明存在的缓存/队列」误报——代码只声明了追加语义（时序快照意图），没有声明任何保留上界或外部清理责任；也非低频误报——collectCatalog/profiling/manifest 生成是面向部署方自动化的 mutation 面（cron 可挂），增长与调用次数成正比而非与元数据规模成正比。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P2（并入 C7-004 retention 家族 Phase 6 并裁）**。appendCatalogRow（NopMetaDataSourceBizModel:880-892）/generateManifest（NopMetaModuleBizModel:600-607）/appendProfilingResult（NopMetaEntityBizModel:395-405）三写点追加语义确认，三表清理路径 grep 0 命中复核成立。

### [C7-102] 外部库语句执行全链无 queryTimeout，挂死不可检测且可致 checkpoint 永久锁死

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:140-154,198-212`；语句执行点：`catalog/MetaCatalogCollector.java:104`、`profiling/MetaEntityProfiler.java:364,479,523,537`、`quality/MetaQualityRuleExecutor.java:559,618,711,732,757`（后两文件属前批次包，作 F3 同族登记）、`query/MetaJoinExecutor.java:648+`、`query/AggregationHelper.java:87+`、`query/MetaEntityQueryExecutor.java:102+`、`sqlview/SqlViewFieldTypeInferrer.java:122`
- **证据片段**:
  ```java
  // MetaDataSourceConnectionProcessor —— 建连超时唯一载体是全局 DriverManager 状态；语句级无任何超时
  void setGlobalLoginTimeout() {
      try {
          DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS);   // 全局 JVM 状态，构造器一次性设置
      } catch (SecurityException se) { ... }
  }
  ...
  public void withConnection(String datasourceType, String connectionConfig,
                             BiConsumer<Connection, DatabaseMetaData> action) {
      ...
      conn = dataSource.getConnection();
      DatabaseMetaData metaData = conn.getMetaData();
      action.accept(conn, metaData);        // callback 内 Statement.executeQuery 无 setQueryTimeout
  ```
  ```java
  // MetaCatalogCollector:104 —— COUNT/元数据查询无超时
  try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
  ```
- **严重程度**: P1
- **并发场景**: GraphQL worker 线程（queryData/queryJoinData/queryAggregation/collectCatalogForTable）、checkpoint 调度线程（cron → executeCheckpoint）与外部库之间的同步 JDBC 读。无多线程竞态，属单线程永久阻塞 + 全局守卫状态被连锁占死的 F5 形态。
- **触发时序**: T1 外部库完成 TCP 建连（loginTimeout=5s 仅覆盖建连段）→ T2 `Statement.executeQuery` 发出 COUNT/规则 SQL → T3 外部库网络分区/行锁等待/大表全扫描，socket 读无限挂起（`grep -rn "setQueryTimeout" nop-metadata --include="*.java"` main 面 0 命中）→ T4 调用线程永久阻塞于 H1 任一语句点 → T5 若阻塞点在 `executeCheckpoint` 调用链内，`checkpointRunLocks.putIfAbsent`（`NopMetaQualityCheckpointBizModel.java:203`）的标记因 finally（L266-269）永不执行而永久残留，此后同一 checkpoint 全部执行（含 cron 触发）fail-fast `ERR_CHECKPOINT_ALREADY_RUNNING`，直至进程重启；GraphQL 侧则按调用次数线性泄漏 worker 线程。全过程无 stall 检测/心跳可见。
- **现状**: 建连超时（5s）与语句执行超时被混为一谈：注释自认「SimpleDataSource#setLoginTimeout 为 no-op，实际靠 DriverManager.setLoginTimeout」，而 login timeout 不覆盖语句执行段；全部 11 个外部语句执行点均未调用 `setQueryTimeout`，也无 socketTimeout/connectTimeout 的 JDBC URL 参数强制。
- **风险**: F5 族（挂死生产者对健康检测不可见）。外部库停滞是长时运行系统最典型的环境故障；一旦发生，checkpoint 面从「单次执行挂起」恶化为「该检查点功能永久不可用（锁死）+ 每次 cron tick 打 ERROR」，查询面则持续吞噬 worker 线程。另：全局 `DriverManager.setLoginTimeout` 是宿主 JVM 共享状态，其他组件（或驱动重置）可静默移除这唯一 5s 保障，守卫无所有权与再断言。
- **建议**: (1) `withConnection`/`TableReferenceExecutor` 分派层统一对 callback 内 Statement 设置 `setQueryTimeout(n)`（配置化，如 `nop.metadata.datasource.query-timeout-seconds`），或在 AR-02 校验层强制注入 socketTimeout/connectTimeout URL 参数；(2) login timeout 改为每次建连前显式设置或迁出全局状态并文档化全局副作用；(3) checkpoint 运行标记增加时限兜底（如标记带获取时间戳，超时允许强收）以恢复自愈性。
- **失效面枚举**: collectCatalog（MetaCatalogCollector L104，未修复）；collectCatalogForTable（同 collector，未修复）；profileEntity/profiling 规则（MetaEntityProfiler 4 处，未修复）；质量规则执行（MetaQualityRuleExecutor 5 处，文件属前批次包，同族未修复）；queryJoinData/queryAggregation 跨库取数（MetaJoinExecutor/AggregationHelper/MetaEntityQueryExecutor，未修复）；resolveEntityFields/createSqlView 类型推断（SqlViewFieldTypeInferrer L122，未修复）；entity 端点走平台事务连接（TableReferenceExecutor L70-103），受平台连接池超时治理约束，不在本条失效面内。
- **信心水平**: 确定（无超时配置为直接事实；「外部库停滞」触发条件是长时运行下的常态故障而非代码竞态）
- **误报排除**: 非「有界声明的池/队列」误报（此处恰是无界等待）；非低频路径误报——collectCatalog/executeCheckpoint/profileEntity 是声明可被调度周期触发的主路径；「声明的不池化」边界只豁免连接池管理，不豁免语句超时缺口。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P1（建议主登记）**。全链无超时（grep 0 命中）+ buildDataSource 仅透传四参（MetaDataSourceConnectionProcessor:269-297）+ 15 语句点闭环（W1）+ checkpointRunLocks 永久残留链（NopMetaQualityCheckpointBizModel:203,266-269 × LocalJobScheduler 单 fire 语义）逐项重演；与 C7-001 判为同一缺陷不同后果层、与 C4-R2-001 同根因，主/次登记与判级统一建议见文末维度复核结论。

### [C7-103] EXTERNAL_TABLE_UPSERT_LOCKS 静态锁表无驱逐：声明边界内的陈旧键残余

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:1053,1062-1079`
- **证据片段**:
  ```java
  private static final Map<String, Object> EXTERNAL_TABLE_UPSERT_LOCKS = new ConcurrentHashMap<>();
  ...
  private void upsertExternalTableGuarded(String metaModuleId, NopMetaDataSource dataSource, ExternalTableInfo info, ...) {
      String lockKey = tableLockKey(metaModuleId, info.getTableName(), normalizeSchemaForMatch(info.getSchema()));
      Object lock = EXTERNAL_TABLE_UPSERT_LOCKS.computeIfAbsent(lockKey, k -> new Object());
      synchronized (lock) { ... }
  }
  ```
- **严重程度**: P3
- **并发场景**: 多个并发 syncExternalTables（不同数据源/同数据源多线程）按 (moduleId,tableName,schema) 键互斥；单实例 baseline（R4.3 已裁定）。
- **触发时序**: 非竞态缺陷。T1 任意时刻某 (module,table,schema) 三元组首次出现 → 锁对象永久驻留；T2 该表后续从外部库删除/改名 → 键失去对应元数据行但仍驻留 map。增量 = 历史出现过的全部键基数（含已消失表），慢速单调。
- **现状**: 键集合无 remove/驱逐路径。代码注释（L1050-1052）已显式裁定「key 集合有界（受元数据规模约束），长期持有可接受」——该裁定覆盖了「现存元数据规模」上界，但未覆盖「曾出现已消失的表键」残余：陈旧键不随元数据行删除回收。
- **风险**: 弱单调增长（每键仅一个空 Object，约数十字节），实际风险为可忽略级的内存驻留 + 裁定口径与真实上界的细微偏差（登记为 watch-only 残余，与既有「多实例残余面见 plan Deferred But Adjudicated」并列）。
- **建议**: 如后续引入元数据行删除路径，可顺带 `EXTERNAL_TABLE_UPSERT_LOCKS.remove(lockKey, lock)`（持有锁内末尾清理）；或维持现状并在注释补一句「含已消失表键残余，可接受」即可闭合裁定。
- **失效面枚举**: 本类 `EXTERNAL_TABLE_UPSERT_LOCKS`（本条）；`EXTERNAL_MODULE_ENSURE_LOCK`（L1060，单键常量锁，有界，无问题）；`NopMetaQualityCheckpointBizModel.checkpointRunLocks`（L171，putIfAbsent + finally remove 对称，无问题）。同族扫描完毕，仅本条命中。
- **信心水平**: 确定
- **误报排除**: 不按「无界账本」P1/P2 报告，正因为代码内已有显式裁定声明边界（审计纪律第 6 条：已声明边界不是缺陷）；本条仅登记裁定未覆盖的陈旧键残余，降 P3 watch。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P3（watch）**。EXTERNAL_TABLE_UPSERT_LOCKS computeIfAbsent 无 remove（NopMetaDataSourceBizModel:1053,1070-1078）、注释有界裁定在场（:1050-1052）确认；陈旧键残余为裁定未覆盖的微弱增量，watch 维持。

## 三、维度集外强信号线索（CL）

### [CL-601]（C1 形态）CrossDbConfigHolder 公开 static 可变配置字段，读取点快照/活性不一致

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbConfigHolder.java:5`
- **证据片段**:
  ```java
  public class CrossDbConfigHolder {
      /** 跨库拼接单侧结果集行数上限（防 OOM，超限显式失败）。 */
      public static int maxCrossDbRows = 10000;
  ```
- **线索**: public static 非 final 可变 int 是全模块唯一的跨请求全局可变状态（`NopMetaDataSourceBizModel.EXTERNAL_TABLE_UPSERT_LOCKS` 为容器但键值语义不同）。仓库内无生产写点，但快照/活性读取不一致已固化：`MetaJoinExecutor` 构造器（L74）把值快照进 `CrossDbJoinMerger`（final），而 L404/L436 每次请求活性读取——运行期任何赋值（测试/宿主代码/未来配置化）会造成「合并不变上限 + 取数上限已变」的静默错位。C1 维度应裁定：改 final 常量或走 `@InjectValue` 配置面。
- **严重程度**: P3 | **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（P3）**。public static 非 final 可变字段 + 构造器快照（MetaJoinExecutor:74）vs 活性读取（:404,:436）错位固化确认。

### [CL-602]（C1/C2 形态）manifest/module 版本号 find-then-increment 竞态，UK 兜底转为并发假失败

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java:657-680`
- **证据片段**:
  ```java
  private long computeNextManifestVersion(String metaModuleId, IServiceContext context) {
      ...
      NopMetaManifest latest = manifestBiz.findFirst(q, null, context);
      if (latest == null || latest.getManifestVersion() == null)
          return 1L;
      return latest.getManifestVersion() + 1;
  }
  ```
- **线索**: 两个并发 generateManifest（同模块）均读到 latest=N → 均写 N+1 → 后者 flush 命中 `UK_NOP_META_MANIFEST_MODULE_VER`（`nop-metadata/model/nop-metadata.orm.xml` 已核实存在）抛约束违反，mutation 失败。无损坏（UK 兜底），但并发管理员操作得到无重试的假失败；`computeNextModuleVersion`（L667-680）同族。对照同文件 `ensureExternalSystemModule`/`upsertExternalTableGuarded` 已有的 per-key 锁先例，属 F3「同族兄弟漏修」形态。C1/C2 维度应裁定是否纳入既有锁先例。
- **严重程度**: P3 | **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（P3）**。find-then-increment（NopMetaModuleBizModel:657-667，computeNextModuleVersion 同族 :669-680）+ UK_NOP_META_MANIFEST_MODULE_VER（nop-metadata.orm.xml:2179）确认，UK 兜底转并发假失败成立。

### [CL-603]（C6 形态）nop-rule Excel 导入临时文件 detachFile 不在 finally，失败路径残留

- **文件**: `nop-rule/nop-rule-service/src/main/java/io/nop/rule/service/entity/NopRuleDefinitionBizModel.java:125-144`
- **证据片段**:
  ```java
  RuleModel ruleModel = (RuleModel) ExcelHelper.loadXlsxObject(RuleConstants.IMP_PATH_RULE, resource);
  ...
  new DaoRuleModelSaver().saveRuleModel(ruleModel, entity);

  fileStore.detachFile(fileId, getBizObjName(), objId, NopRuleConstants.PROP_IMPORT_FILE);  // 仅成功路径到达
  ```
- **线索**: `loadXlsxObject`/`saveRuleModel` 抛错时 `detachFile` 不执行，`__TEMP__` 上传文件残留于 fileStore；本模块内无清扫路径（平台侧是否有 TTL 清理未在本次范围核实——C6 维度复核时需先核实 nop-biz-file-core 的临时文件生命周期再定性）。与 C7-102 同为「异常/中断路径清理不对称」（F8）形态，但触发为管理员保存失败，频率低。
- **严重程度**: P3 | **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（P3）**。detachFile 仅成功路径到达（NopRuleDefinitionBizModel:142），loadXlsxObject/saveRuleModel 抛错即残留临时文件，确认。

### [CL-604]（C9 形态）lineage BFS 每查询全量重建边图，四端点重复支付同一成本

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaLineageEdgeQueryAction.java:417-438`（调用点 L55-56/77-78/99-101/137-143）
- **证据片段**:
  ```java
  private LineageGraph buildLineageGraph(IEntityDao<NopMetaLineageEdge> dao) {
      QueryBean q = new QueryBean();
      q.setLimit(maxEdges + 1);
      List<NopMetaLineageEdge> allEdges = dao.findAllByQuery(q);   // 每次调用全量装载至 10 万行上界
  ```
- **线索**: getUpstream/getDownstream/getLineagePath/getImpactAnalysis 四个查询端点每次各自执行 `buildLineageGraph`（全表装载 + 建图），UI 一次血缘浏览常连发多个端点。量化口径：单端点成本 = O(min(边数,100k) 次行装载 + 建图)；四端点 × 前端并发调用 = 4 倍重复。已有 maxEdges fail-fast 声明边界（OOM 面已闭合），本条仅为重复计算/缓存退化候选（请求级或短 TTL 图缓存）。C9 维度复核时定级。
- **严重程度**: P3 | **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（P3）**。四端点（NopMetaLineageEdgeQueryAction:56,78,101,139）各自全量重建边图（buildLineageGraph:417-438，maxEdges+1 fail-fast 在场），重复计算面确认。

## 四、误报校准记录（本轮主动排除项）

1. **规则热更新编译产物陈旧**——核为平台 ResourceComponentManager 依赖失效机制 + 模型实例级编译缓存，无陈旧面（见 1.3 节）。
2. **`searchIndexFailOpen` 默认 false 导致搜索引擎故障阻断元数据保存**——`NopMetaSearchProcessor` L27-32 显式声明可配置 fail-open/fail-closed 契约（默认 fail-loud 为设计选择），非缺陷。
3. **RuleModel.getInputSchema 惰性构建无同步**——双构建产出等值 Schema，属良性竞态，非 C7 面（C1 如复核可裁 P3）。
4. **SqlViewFieldTypeInferrer 每次推断新建外部连接**——连接处理器类注释已声明「不池化、按需建连」为设计边界（L43-45），连接生命周期由 withConnection finally 保证；不重复报告。
5. **AutoClassification/LineageTagPropagation 标签幂等守卫的 check-then-act**（`hasExistingAutomatedLabel`/`hasExistingPropagatedLabel` 查重后插入）——并发窗口可产生重复标签行，但两处理器均为用户触发的管理动作、无调度自动放大，属 C1 低频理论竞态，不满足本维度立案标准（C1 维度可酌情登记）。
6. **`DriverManager.setLoginTimeout` 全局副作用**——不单独立案，并入 C7-102 的超时架构缺陷一并裁定。

## 五、批次统计

- 清单行数：单调增长源 7 行（G1-G7）+ 挂死点 4 行（H1-H4）+ 重点面核验 7 行
- 发现数：3（P1×1：C7-102；P2×1：C7-101；P3×1：C7-103）
- CL 线索数：4（CL-601/602/603/604）
- 主动排除（误报校准）：6 项


---

# C7 深挖第 2 轮

> 审计日期: 2026-10-22 | 子代理: C7 维度深挖第 2 轮 | 基线: live code（commit 81a7c228e7 同基线工作区）
> 扩大面（任务书）: ① C7-001/C7-102 无超时面全部外部访问点穷举放大核查 ② QualityResultWriter 写失败中间态与 run 状态机 ③ 调度配置热更新面（孤儿 job）④ 告警工作流长时运行面 ⑤ nop-rule 规则热更新链（在执行规则的版本一致性）⑥ catalog/sync 定时同步面（增量同步失败补偿）
> 盲审纪律: 本轮可读本维度文件（第 1 轮产物）；未读其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/ 下任何文件。live code 为准；只读审计（除本文件追加外未修改任何文件）。`_` 前缀生成物不作发现对象。

## 一、发现条目

### [C7-R2-001] 规则热更新失效传播不完整：NopRuleNode 子行独立 CRUD 不触发 RuleModel 缓存失效，陈旧规则无限期服务

- **文件**: `nop-rule/nop-rule-dao/src/main/java/io/nop/rule/dao/model/DaoRuleModelLoader.java:84`（主锚：唯一依赖钉定点）；同链：`nop-rule/nop-rule-service/src/main/java/io/nop/rule/service/entity/NopRuleNodeBizModel.java:29`、`nop-rule/nop-rule-service/src/main/resources/_vfs/nop/rule/model/NopRuleNode/_NopRuleNode.xbiz`（`DefaultBizGenExtends forEntity="true"` 生成全量标准 CRUD mutation）、`nop-persistence/nop-orm/src/main/java/io/nop/orm/resource/DaoEntityResource.java:104-125`（失效判据 = 定义行 updateTime）
- **证据片段**:
  ```java
  // DaoRuleModelLoader.loadRule —— 依赖钉定只有定义行一条；规则内容主体（判定树谓词/输出）在 NopRuleNode 子行
  public RuleModel loadRule(String ruleName, Long ruleVersion) {
      NopRuleDefinition entity = loadRuleDefinition(ruleName, ruleVersion);
      ResourceComponentManager.instance().traceDepends(DaoEntityResource.makeDaoResourcePath(entity));
      RuleModel model = buildRuleModel(entity);   // buildRuleModelNode: entity.getRuleNodes() 构建判定树
  ```
  ```java
  // DaoEntityResource.loadEntity —— 缓存失效判据 = 该实体行自己的 updateTime 列
  IEntityModel entityModel = entity.orm_entityModel();
  if (entityModel.getUpdaterPropId() < 0)
      throw new NopException(ERR_ORM_ENTITY_NO_UPDATE_TIME_COL)...;
  Timestamp time = ConvertHelper.toTimestamp(entity.orm_propValue(entityModel.getUpdateTimePropId()));
  ```
  `NopRuleNodeBizModel extends CrudBizModel<NopRuleNode>` 且 xbiz 经 `DefaultBizGenExtends` 生成标准 save/delete/update——节点行存在独立的 GraphQL/RPC 写入面，写节点行不触碰 `NopRuleDefinition.updateTime`。
- **严重程度**: P1
- **并发场景**: 非竞态缺陷，为热更新失效传播缺口。请求线程（`RuleServiceImpl.executeRule` → `RuleManager.getRule` → `ResourceComponentManager.loadComponentModel`）与规则编辑 mutation 线程（`NopRuleNodeBizModel.save/delete`）跨时间交叠。
- **触发时序**: T1=规则 R 首次执行，`loadComponentModel("rule:" + R)` 缓存 RuleModel，`ResourceDependencySet` 记录 depends=`dao:io.nop.rule.dao.entity.NopRuleDefinition/<id>` 的 lastModified=定义行 updateTime（检查间隔 `nop.orm.dao-resource-check-interval` 默认 5000ms，`OrmConfigs.java:74-76`）→ T2=通过 `NopRuleNode` 标准 save mutation 修改某节点谓词/输出（definition 行不动，updateTime 不变）→ T3=5s 检查间隔后任意 executeRule 复查 depends：`DaoEntityResource.lastModified()` 重读定义行 updateTime——未变 → `DefaultResourceChangeChecker.checkChanged` 判定无修改 → 缓存不失效 → T4=旧编译规则无限期继续执行（T2 的编辑对执行行为零影响），直到定义行被任意 save 触碰或 JVM 重启。全程无任何日志/告警。
- **现状**: 定义级保存路径（`NopRuleDefinitionBizModel` → `DaoRuleModelSaver.saveRuleModel` 设置 modelText）会更新定义行 → 失效正常触发；缺口仅在节点级独立写路径——该路径无任何守卫（无禁用、无 parent-touch 补偿、无文档声明）。
- **风险**: 规则引擎核心契约「保存即生效」静默失效：平台用户经一等公民 CRUD 面编辑判定树后，线上决策持续走旧逻辑，且无任何信号可察觉（配置看似已保存、读接口能读到新节点行，仅执行面陈旧）——长时运行下表现为「规则改了但不生效」的不可诊断漂移，直接违反热更新一致性（C7 自愈语义面）。
- **建议**: 三选一或组合：(1) `DaoRuleModelLoader.loadRule` 对 `entity.getRuleNodes()` 逐节点补 `traceDepends(DaoEntityResource.makeDaoResourcePath(node))`（失效判据精确到子行）；(2) `NopRuleNodeBizModel` save/delete/deleteBatch override 在持久化后 touch 父定义行（或转发到 `DaoRuleModelSaver` 聚合保存路径）；(3) 至少在 NopRuleNode xbiz 移除写 mutation（`x:override` 裁剪 `DefaultBizGenExtends` 的 save/delete 面）并在 owner doc 声明「节点仅随定义聚合保存」边界。方案 (1) 与平台失效机制对齐、改动最小。
- **失效面枚举**: 全仓 `traceDepends` 家族（F3 清扫，grep 可复现：`grep -rn "traceDepends" --include="*.java" | grep -v target | grep -v _gen`）——单元内仅 `DaoRuleModelLoader:84` 一处 DaoEntityResource 钉定（本条，命中）；`nop-wf-dao DaoWorkflowModelLoader:53` 同为 DaoEntityResource 单行钉定，但工作流 XML 整体存于定义行 modelText（无子行结构），单行钉定充分，**无同族缺口**；其余 traceDepends 均为文件资源（PageProvider/DynamicJsLoader/DynamicCssLoader/XplLibTagCompiler/DeltaJsonLoader 等），失效判据为文件 lastModified，天然覆盖内容变更。单元内无其他 DaoEntityResource 消费点。
- **信心水平**: 确定（机制全链可代码验证：依赖钉定单点 + 判据 = 定义行 updateTime + 节点独立写面存在）
- **误报排除**: 不是「平台基础设施自身不重审」误报——缺口在 DaoRuleModelLoader 的依赖钉定调用方式（单元内手写代码），非 nop-kernel 失效机制缺陷；也不与第 1 轮 1.3 节「规则热更新无缺陷」结论冲突——该结论核实的是「定义级保存的失效链 + 懒编译锁」，未覆盖节点级独立写路径的失效传播（本轮新暴露面）。为什么这次不是高频误报：`NopRuleNode` 是经 xbiz 显式暴露写面的完整 BizModel 实体，不是内部实现细节。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P1**。机制全链独立复核成立：依赖钉定单点（DaoRuleModelLoader:84）+ 失效判据=定义行 updateTime（DaoEntityResource:104-125，check-interval 默认 5000ms=OrmConfigs.java:75）+ RuleModel 内容取自子行（buildRuleModelNode:156-177）+ 子行一等 CRUD 写面（NopRuleNodeBizModel extends CrudBizModel 无 save/delete override + _NopRuleNode.xbiz DefaultBizGenExtends forEntity="true"）——节点级编辑不触定义行 updateTime、缓存不失效，P1 维持。

### [C7-R2-002] 检查点 run 无持久化状态机：崩溃留下的部分 runId 行与完整 run 不可区分，AR-14 补偿行自身写失败时回退陈旧行

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/MetaQualityCheckpointExecutor.java:129-186`（主锚：run 循环逐规则 append，无 run 级持久化记录）；同链：`entity/NopMetaQualityCheckpointBizModel.java:171,203-206,266-269`（运行标记仅内存 map）、`MetaQualityCheckpointExecutor.java:176-184`（AR-14 ERROR 补写行失败的兜底分支仅日志）；ORM 模型核实：`nop-metadata/model/nop-metadata.orm.xml`（1948 行起 NopMetaQualityResult 等 40+ entity 清单中**无任何 run 级实体**，grep QualityRun/CheckpointRun 0 命中）
- **证据片段**:
  ```java
        // executeCheckpoint: runId 仅作为内存态幂等键 + 结果行分组列，无持久化 run 记录
        if (checkpointRunLocks.putIfAbsent(checkpointId, Boolean.TRUE) != null) { ... }
        String runId = StringHelper.generateUUID();
        try {
            Map<String, Object> summary = ensureCheckpointExecutor().execute(cp, runId, schemaPattern);
            ...
        } finally {
            checkpointRunLocks.remove(checkpointId);   // 运行标记仅在内存
        }
  ```
  ```java
        // executor AR-14 分支：ERROR 补写行自身失败 → 仅日志，该规则本轮无任何新行
        } catch (Exception appendEx) {
            // ERROR 行落盘失败不掩盖原始规则异常——errors 已含原始失败，此处留证即可
            LOG.error("checkpoint failed to append ERROR result row for rule: ...", appendEx);
        }
  ```
- **严重程度**: P3
- **并发场景**: 非竞态。单线程 run 内部分失败/中断；触发方为 cron job 线程或手动 mutation 线程。
- **触发时序**: 主形态（非竞态）：T1=run R 执行到第 3/N 条规则，已 append 3 行（flush 落库）→ JVM 崩溃/kill -9（或 Error 逃逸，见 C7-002 残余面）→ T2=进程内无任何持久化痕迹标记 run R 未完成（运行标记随进程消失，无 run 行可查）→ T3=消费方按 `(checkpointId, runId=R)` 查询结果行得到 3 行——与「已完成 run 但 totalRuleCount=3」在持久化面上不可区分（executedCount/totalRuleCount 对账信息只存在于瞬时返回 DTO）。变体形态：T1'=metadata DB 写路径闪断 → 每条规则 judge 失败进 catch，AR-14 的 ERROR 补写行也逐条失败（:176-184 仅日志）→ T2'=该规则本轮**无新行** → T3'=`MetaQualityScorer.findLatestResult`（:316-321，executeTime DESC 取首）回退上一轮旧行（可能旧 PASS）→ autoScore 在检查点连续失败期间持续显示健康——恰为 AR-14 注释自认要堵的洞，在其补偿行自身写失败分支重现。
- **现状**: per-rule 失败隔离 + AR-14 补偿覆盖了主失败面（单规则异常）；run 级语义 = 「无状态机可停」——run 的存在性仅由结果行的 runId 列隐式承载，无完结标记、无部分 run 检测面。
- **风险**: 观测与对账面缺失：监控/BI 按 runId 聚合时把崩溃残留的部分 run 当完整 run 消费；连续 ERROR-补写失败窗口内评分失真且无持久化证据（仅日志）。不造成数据损坏，属 F2「半个成功」家族的观测面残余。
- **建议**: (1) run 完结标记：executor 正常收尾时按 (checkpointId, runId) 落一行 run 摘要（或复用 checkpoint.extConfig 之外的最小 run 表），消费方以「run 行存在」为完整判据；(2) 最小改法：提供按 runId 聚合行数 vs totalRuleCount 的对账查询 action（完整性可事后验算）；(3) AR-14 补写失败分支至少累计进 summary.errors（当前仅日志，DTO errors 不含该条）。
- **失效面枚举**: run 状态载体全清单——内存运行标记 `checkpointRunLocks`（finally 对称释放，无泄漏，见 R11）；结果行 runId 列（本条主面，无完结标记）；返回 DTO summary/executedCount（瞬态）；webhook/notify 摘要（瞬态，丢失面归 C7-005）；ORM 模型（无 run 实体，0 命中）。无其他兄弟载体。
- **信心水平**: 确定（无 run 实体与内层 catch 仅日志均为直接代码事实；崩溃/DB 闪断触发条件是长时运行常态故障而非竞态）
- **误报排除**: 不与 C7-004（append-only 无 retention）重复——本条不是行数增长问题，而是 run 完结性不可判定；不与 C7-002 重复——C7-002 是 Error 逃逸致 job 永久 FAILED，本条是 run 中断后的持久化语义（即使 job 存活，被中断 run 的残留行也无标记）；AR-14 已被第 1 轮 R12 记为「已防护」，本轮报告的是其补偿行的失败分支残余，属新失效面而非重复报告。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P3**。run 级实体 0 命中（nop-metadata.orm.xml grep QualityRun/CheckpointRun=0）、AR-14 补写失败分支仅日志不入 errors（MetaQualityCheckpointExecutor:176-184）确认；观测面残余、无数据损坏，P3 维持。

### [C7-R2-003] 告警工作流 reJudge 原地改写时序结果行：破坏 insert-only 语义，运行证据链被就地覆盖

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityAlertWorkflowProcessor.java:105-138`（主锚 :130-135）
- **证据片段**:
  ```java
        // 工作流 agree 路径（verify 步骤）调用的 re-judge：原地更新历史结果行
        result.setStatus(judgment.getStatus());
        result.setActualValue(judgment.getActualValue());
        result.setExpectedValue(judgment.getExpectedValue());
        result.setMessage(judgment.getMessage());
        result.setDetails(JsonTool.stringify(judgment.getDetails()));
        daoFor(NopMetaQualityResult.class).updateEntity(result);   // executeTime 不变，内容被覆盖
  ```
  对照 ORM 模型钉死语义（`nop-metadata/model/nop-metadata.orm.xml:2097-2100`，第 1 轮 C7-004 引证）：「唯一写位点 = QualityResultWriter（**insert-only**）」「时序语义：executeTime=now，不覆盖旧行」。
- **严重程度**: P3
- **并发场景**: 工作流引擎线程（verify 步骤同步执行）单线程改写；与评分/查询线程无竞态（executeTime 排序键未动）。
- **触发时序**: T1=规则 FAIL 产生结果行 L，触发 qualityBreachApproval 告警实例 → T2=owner agree → verify 步骤执行 `reJudgeFailClosed` → reJudge 对外部表重判 → T3=行 L 的 status/actualValue/expectedValue/message/details 被本次判定覆盖（可能是 PASS）→ T4=事后审计/趋势分析读到的 L 已非「告警触发时刻的运行证据」；该规则的结果时间线在 T1 时点的真实 FAIL 数据点被就地抹除（时间线上少一个真实观测，而非追加修正行）。
- **现状**: reJudge 是第 4 个结果行写点（前三个：QualityResultWriter.append、单规则路径 appendQualityResult、checkpoint 补写——均 append），唯一 update 型写点，与 ORM 注释声明的「唯一写位点 = QualityResultWriter（insert-only）」直接冲突；调用链：`qualityBreachApproval/v1.xwf` verify 步骤 c:script → `reJudgeFailClosed`（MA7.6-03 fail-closed 包装，异常转 reject）→ `reJudge`。
- **风险**: 质量审计语义违约：结果表被声明为时序事实（历史不覆盖），但告警处置流程会静默改写历史行；跨 run 趋势/复盘以行集为证据时失真。无正确性损坏（findLatestResult 排序稳定，UK 不受影响）。
- **建议**: reJudge 结果改为追加新行（复用 `QualityResultWriter.append`，details 标注 `originResultId` + `source=re-judge`），保留原 FAIL 行；或在 ORM 模型/owner doc 修订声明，把 reJudge 增补为已声明的第二写点（二者取一，消除「注释钉死 insert-only」与实现的结构性矛盾）。
- **失效面枚举**: NopMetaQualityResult 写点全清单——QualityResultWriter.append（insert-only，合规）；NopMetaQualityRuleBizModel.appendQualityResult（经 QualityResultWriter，合规）；checkpoint AR-14 补写（经 QualityResultWriter，合规）；**reJudge（update，本条，唯一违约点）**；onDisagree listener 标记 isFalsePositive=1（update，但属业务标记字段的显式设计意图，幂等守卫齐备，不计）；NopMetaQualityResult 标准 CRUD delete/update 面（平台面，第 1 轮 C7-004 已登记速率差观察）。
- **信心水平**: 确定
- **误报排除**: 不是「告警处置状态机正常写业务字段」的误报——isFalsePositive 是处置标记字段，本条报告的是 status/actualValue/message/details 四个**运行判定字段**被改写；也不是维度集 22 的重复——第 1 轮对本文件只登记了 createAlertWorkflow 无去重（C7-007）与创建失败仅日志（C7-005 同族），reJudge 写语义未被任何前轮覆盖。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P3**。reJudge 原地覆盖 status/actualValue/expectedValue/message/details + updateEntity（QualityAlertWorkflowProcessor:130-135）确认，与 ORM 注释 insert-only 声明结构性矛盾成立；作为 NopMetaQualityResult 写语义子面，Phase 6 与 retention 家族并裁。

### [C7-R2-004] syncExternalTables 只增不删：外部已删除表永久残留为幽灵元数据行，目录与物理 schema 漂移单调累积

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:598-701`（主锚 :628 方法、:648 全量 read + upsert 循环）
- **证据片段**:
  ```java
            connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                    (Connection conn, DatabaseMetaData metaData) -> {
                        List<ExternalTableInfo> tables = structureReader.read(conn, metaData, schemaPattern);
                        for (ExternalTableInfo table : tables) {
                            try {
                                upsertExternalTableGuarded(externalModuleId, dataSource, table, context);
                                syncedCount.incrementAndGet();
  ```
  javadoc（:611-616）：「幂等 upsert：按 (metaModuleId, schema, tableName) 复合键去重……重复同步更新而非追加」——全方法无任何对「外部源已不存在于 read 结果中的表」的标记/删除/对账分支。
- **严重程度**: P3
- **并发场景**: 非竞态。单线程周期性 sync；漂移随外部源演化单向累积。
- **触发时序**: T1=外部库删表 T（或改名/迁移 schema）→ T2=后续每次 `syncExternalTables` 的 `structureReader.read` 结果不含 T → upsert 循环不触碰 T 行（upsert 只对「read 到的表」收敛）→ T3=T 行及其 NopMetaEntityField 子行、其上挂载的 NopMetaQualityRule/NopMetaProfilingRule 永续存在 → T4=质量规则/剖析以 T 为目标每次运行失败（外部表不存在）→ 每轮 ERROR 结果行 + 错误日志持续累积（并入 C7-004 增长面），元数据目录与物理 schema 的差集随时间单调增大。
- **现状**: 同步失败补偿面本身防护完整（AR-17 部分持久化契约：per-table REQUIRES_NEW 独立提交 + scan 级失败 fail-loud 且 REQUIRES_NEW 发布事件，:672-708）；缺口仅在**收敛语义**——upsert-only 无删除对账、无 stale 标记、无 removedTables 报告；javadoc 也未声明「同步不删除」边界。
- **风险**: 长时运行下幽灵表纯增不减（与 C7-101 同为「随外部世界演化单向增长」形态，但增长源是外部 schema 演化而非调用次数）；幽灵表挂载的规则持续产生失败噪音，淹没真实质量违约信号。
- **建议**: (1) sync 结果 DTO 增加 `removedTables`（read 结果集与现有 external 行集的差集，dry-run 报告，不自动删除——删除走显式确认 action）；(2) 或对差集行打 `deprecated` 标记并在质量规则解析时跳过+WARN；(3) 最低成本：owner doc 声明「同步仅 upsert、外部删除不回收」边界，并说明清理责任归属。
- **失效面枚举**: 同步写路径全清单——syncExternalTables upsert（本条，无删除对账）；collectCatalog（append 快照行，retention 面归 C7-101，与删除对账无关）；ensureExternalSystemModule/upsertExternalTableGuarded（辅助，无删除语义）；`ExternalTableStructureReader.read/readColumns`（纯读，无写面）。同族无其他兄弟入口。
- **信心水平**: 确定
- **误报排除**: 不是「标准 CRUD 实体可手动删除」误报——与第 1 轮 C7-004 同一校准逻辑的反向应用：sync 是面向部署方自动化的机器速率面，人工清理以人速进行，且 sync 自身无任何信号提示差集存在；也不与 C7-101 重复——C7-101 是快照行无限追加，本条是主数据行（NopMetaEntity）与外部源的收敛性缺口，二者增长源与修复面不同。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留 P3**。syncExternalTables upsert 循环（NopMetaDataSourceBizModel:646-660）只收敛 read 到的表、无删除对账/stale 标记，幽灵表单调累积机制确认；AR-17 per-table REQUIRES_NEW 部分持久化契约独立确认完好。

## 二、本轮扩大面清扫表（终止信号载体）

| # | 扩大面 | 核查口径与证据 | 结论 |
|---|---|---|---|
| W1 | C7-001/C7-102 无超时面全部外部访问点穷举 | 外部连接获取点：`dataSource.getConnection()` 仅 MetaDataSourceConnectionProcessor:204（withConnection）/239（testConnect）两处；SimpleDataSource 构建单点 buildDataSource（:269-297），无任何旁路。withConnection 调用点 13 处 + testConnect 1 处 = 14（与 processor javadoc:267 自述吻合）：NopMetaQualityRuleBizModel:245、NopMetaDataSourceBizModel:646/758、NopMetaEntityQueryAction:108/130、TableReferenceExecutor:128、SqlViewFieldTypeInferrer:122、MetaJoinExecutor:344/433、ExternalExternalJoinAggregationProcessor:88、MixedSameDbJoinAggregationProcessor:121/176、ExternalAggregationProcessor:67、testConnect:NopMetaDataSourceBizModel:189。Statement/PreparedStatement 创建点 15 处（MetaQualityRuleExecutor×5:559/618/711/732/757、MetaEntityProfiler×6:364/479/523/537/560/582、MetaCatalogCollector:104、MetaJoinExecutor:656、AggregationHelper:90、MetaEntityQueryExecutor:113、SqlViewFieldTypeInferrer:158）全部落在 C7-001/C7-102 已登记失效面内；DatabaseMetaData 查询面（getTables/getColumns/getIndexInfo）已含于 C7-001 readColumns 面；`setQueryTimeout` main 面 0 命中（复核维持） | **无第三/第四查询入口**；无新立案；C7-001/C7-102 失效面闭合成立且收敛点（buildDataSource 单点）支撑其「单点修复可全覆盖」建议 |
| W2 | QualityResultWriter 写失败中间态 / checkpoint 状态机停在什么态 | execute→append→flushSession 循环（MetaQualityCheckpointExecutor:129-186）+ 运行标记（BizModel:203/266-269）+ ORM 模型 run 实体 0 命中 | 部分行写入+异常的中间态 = **无 run 状态机可停**：run 仅由结果行 runId 列隐式承载，崩溃残留部分 run 无标记 → **立案 C7-R2-002**；per-rule 隔离与 AR-14 主面维持 R12「已防护」判定 |
| W3 | 调度配置热更新面（孤儿 job） | save→notifySchedulerRegister / delete→notifySchedulerUnregister 全路径接线（BizModel:284-336）；addJob 失败清旧 job（MA7.5-03，Scheduler:302-319）；非 ACTIVE/空 cron/已删除均 removeJob（:169-173,291-300）；AR-23① 顺序修正 + commit 残余显式裁定（BizModel:291-299,330-335 注释）；init() 重启重注册为恢复路径（:130-157） | **已防护/已声明边界**；无新立案。残余（直接改 DB 绕过 BizModel、commit 失败窗口）已有裁定与重启自愈路径 |
| W4 | 告警工作流长时运行面 | createAlertWorkflow 去重缺失=C7-007 已案；xwf verify fail-closed（MA7.6-03）+ *end listener 幂等守卫核实；reJudge 写语义核查 | **立案 C7-R2-003**（reJudge 原地改写时序行）；watch-only：onDisagree listener 对已删 result 行 `requireEntity` 无守卫（v1.xwf:86，前置条件=标准 CRUD 删除结果行而告警实例未终结；平台 *end listener 异常语义未核实，移交工作流语义维度复核）；xwf 无超时/升级配置，实例滞留面已并入 C7-007 累积面 |
| W5 | nop-rule 规则热更新链（在执行规则的版本一致性） | 在执行规则持旧 RuleModel 引用至执行结束 = 平台 copy-on-reload 语义（第 1 轮 1.3 已核，维持）；懒编译 synchronized(ruleModel) 全程守卫（RuleManager:55-65，维持）；依赖钉定与失效传播放大核查 | **立案 C7-R2-001**（节点级独立 CRUD 不触发失效——第 1 轮 1.3 结论「无陈旧面」仅覆盖定义级保存路径，本轮证伪其完备性） |
| W6 | catalog/sync 定时同步面（增量同步失败的重试/补偿） | AR-17 部分持久化契约 + scan 级失败 REQUIRES_NEW 事件（NopMetaDataSourceBizModel:672-708）防护完整；单元内 IJobScheduler 消费点仅 checkpoint 调度器（grep 复核），sync/catalog/profiling 定时化由部署方承担；自动重试机制 0 处（与 R24 同口径：无重试即无风暴） | **立案 C7-R2-004**（外部已删除表无对账、幽灵元数据单调累积）；失败补偿主面维持第 1 轮「已防护」判定 |
| W7 | metrics 标签基数（C7 步骤 1） | `grep -rn "IMetric\|MetricRegistry\|IMeter\|newCounter" --include="*.java" nop-metadata/nop-metadata-service/src/main nop-rule/*/src/main` → 0 命中 | 无该失效面 |
| W8 | 时钟与期限（C7 步骤 5） | R20（freshness 负年龄 fail-loud）/R21（findLatestResult 时钟回拨瞬态）已覆盖；本轮新增面无时钟依赖点 | 无新增 |
| W9 | 慢性病：listener 累加 / 缓存只加不减（C7 步骤 6） | EXTERNAL_TABLE_UPSERT_LOCKS（C7-103 已案）；checkpointRunLocks（R11 putIfAbsent/finally 对称，维持）；query 包跨请求缓存 0 命中（第 1 轮已核）；ResourceDependencySet 随缓存条目存亡（无独立增长） | 无新增长点 |

## 三、本轮统计

- 新发现：**4 条**（P1×1：C7-R2-001；P3×3：C7-R2-002 / C7-R2-003 / C7-R2-004）
- 清扫表：9 行（W1-W9）；watch-only 移交项：1 条（W4 onDisagree listener 无守卫）
- 与前轮关系：C7-001/C7-102 失效面穷举闭合（W1）；R12 AR-14「已防护」判定补充残余面（C7-R2-002 变体）；第 1 轮 1.3「规则热更新无缺陷」结论被 C7-R2-001 部分证伪（限节点级写路径）
- P1 级新发现集中在 nop-rule 热更新失效传播——与前轮 P1（超时缺失 ×2、retention 缺失 ×1）不同根因族


---

# C7 深挖第 3 轮

> 审计日期: 2026-10-22 | 子代理: C7 维度深挖第 3 轮（第 2 轮新增 P1 触发的必派轮） | 基线: live code（commit 81a7c228e7 同基线工作区）
> 重点面（任务书）: ① C7-R2-001 同族放大——缓存失效依赖声明面全查（实体行进内存缓存/可计算物 + traceDepends/依赖声明的全部组合；声明粒度 vs 内容行粒度错配站点）② 平台依赖面——LocalJobScheduler job 状态持久化 / 重启自愈 / 配置行删除残留 job ③ QualityResultWriter / QualityAlertWorkflowProcessor 失败重放面 ④ 长事务/长游标残留——批量导入导出中途失败半程状态（批 C 面第 1 轮未深挖角落）
> 盲审纪律: 本轮可读本维度文件（前两轮产物）；未读其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/ 下任何文件。live code 为准；只读审计（除本文件追加外未修改任何文件）。`_` 前缀生成物不作发现对象。

## 一、发现条目

### [C7-R3-001] wf.start 半程失败残留 CREATED 孤儿工作流实例：引擎先落库后完成、调用方吞异常，每次重试累积一个不可见半程实例

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityRuleBizModel.java:170-179`（主锚：调用方吞异常点）；同链：`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityAlertWorkflowProcessor.java:89-97`（newWorkflow→start）、`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java:213-226`（先 saveWfRecord(CREATED) 后完成其余启动步骤的窗口）、`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WfActorAssignSupport.java:113-120`（start 落库**之后**才解析起始步 actor，解析失败即孤儿）
- **证据片段**:
  ```java
  // NopMetaQualityRuleBizModel.executeQualityRule —— 吞异常，事务不回滚
  if ("FAIL".equals(judgment.getStatus())
          && _NopMetadataCoreConstants.QUALITY_SEVERITY_ERROR.equals(rule.getSeverity())) {
      try {
          alertWorkflowService.createAlertWorkflow(row, context);
      } catch (Exception e) {
          LOG.error("Failed to create alert workflow for quality rule: {}, errorCode={}", ...);
      }
  }
  ```
  ```java
  // WorkflowEngineImpl.start —— save-then-finish 窗口：213 行已持久化 CREATED 记录，
  // 之后 218/220/224/226 任一步抛错，半程记录已入当前事务会话
  initCreateStatus(wfRt);                                    // 213: saveWfRecord(WF_STATUS_CREATED) → wfDao().saveOrUpdateEntity
  wfRt.triggerEvent(NopWfCoreConstants.EVENT_BEFORE_START);  // 218
  runXpl(wfModel.getStart().getSource(), wfRt);              // 218
  List<WfActorWithWeight> actors = getActors(wfModel.getStartStep().getAssignment(), null, wfRt);  // 220
  if (!newSteps(null, wfModel.getStartStep(), NopWfCoreConstants.SYS_ACTION_START, actors, wfRt))
      throw wfRt.newError(ERR_WF_START_WF_FAIL);             // 223-224
  saveStarted(wfRt);                                         // 226
  ```
- **严重程度**: P1
- **并发场景**: 非竞态，为失败重放面（F2「半个成功」家族）。`executeQualityRule` 为 `@BizMutation`（请求事务）；`wf.start` 的 `initCreateStatus` 经 `DaoWorkflowStore.saveWfRecord → wfDao().saveOrUpdateEntity`（DaoWorkflowStore.java:130-132）写入同一 ORM 会话——调用方 catch 吞掉异常后事务照常提交，`NopWfInstance` 行（status=CREATED、零 step 实例）随事务持久化。
- **触发时序**: T1=规则判定 FAIL + severity=ERROR，进入 `createAlertWorkflow` → T2=`wf.start` 内 `initCreateStatus` 已把 NopWfInstance 以 WF_STATUS_CREATED 持久化（:213）→ T3=起始步 actor 解析失败（确定性触发：`WfActorAssignSupport.getAssignmentActors:116-120` 对 xwf owner-investigate 步骤声明的 `actorType="role" actorId="metadata-admin"` 调用 `resolveActor`，部署未配置该角色时返回 null → `ERR_WF_ACTOR_NOT_EXISTS`；或起始步落库 DB 故障 → `ERR_WF_START_WF_FAIL`）→ T4=异常沿 `wf.start → createAlertWorkflow` 传播到调用方 catch（:175）→ 仅 LOG.error → T5=事务提交：**结果行 + 孤儿 CREATED 实例**同时落库，告警静默丢失且无审批人可见的在途流 → T6=规则持续 FAIL 期间每次重试执行 `executeQualityRule` 都重复 T1-T5，孤儿实例逐次 +1。孤儿实例无任何清理路径（全仓 `WF_STATUS_CREATED` 消费点仅 `IWorkflow.isStarted` 判定与常量定义，无 job/迁移/启动清扫；NopWfInstanceBizModel 为标准 CRUD，人工清理以人速进行）。
- **现状**: MA7.6-02 修复了 null ctx → 确定性 NPE 的启动失败主因（`QualityAlertWorkflowProcessor.java:91-95` 三级 ctx 兜底），本条报告的是该修复之后的**残余半程窗口**：任何 start 后半程异常仍会留下已落库的 CREATED 记录。引擎侧 `newWorkflow` 仅构造内存对象（`DaoWorkflowStore.newWfRecord:82-94`），孤儿窗口严格始于 `initCreateStatus` 的 save、终于 `saveStarted`。
- **风险**: 「半个成功」无恢复语义（本领域判级 P1 第 6 项）+ 确定性触发条件下的长时运行无界增长：角色未配置/actor 注册缺失是常见部署漂移，一旦命中，每次 FAIL+ERROR 执行新增一个孤儿 NopWfInstance（含 params JSON），审批噪音为零（从未进入起始步，无 step 记录、无待办），运维只能靠翻 biz 调用方 ERROR 日志察觉告警创建失败——与 C7-005/C7-007 的告警丢失面叠加后，质量告警通道的失效形态为「丢失 + 半程残留双轨」。
- **建议**: 单元内单点修复优先：(1) `NopMetaQualityRuleBizModel:174` 的 catch 内补半程清理——catch 到创建失败时回滚该工作流记录（引擎侧 `DaoWorkflowStore.removeWfRecord:135-137` 已存在对应能力，可经 wfManager/store 暴露或在 `QualityAlertWorkflowProcessor.createAlertWorkflow` 内 try/catch 包裹 `wf.start` 失败时自清理 `wf` 记录）；(2) 或反转语义：`createAlertWorkflow` 异常向调用方传播、由 `executeQualityRule` 整体失败回滚（结果行随事务回滚，重跑幂等重建判定）——告警创建失败显式化，代价是单次执行结果丢失（owner doc 需声明）；(3) 引擎侧把 CREATED 持久化延后至 start 成功路径属 nop-wf 平台面（Protected Area：框架核心引擎），移交平台维度复核，不在本单元立案修复面内。最低成本兜底：为 CREATED 且无 step 的存量/增量孤儿提供对账查询 action。
- **失效面枚举**: 单元内 `wfManager.newWorkflow` / `wf.start` 程序化调用点全清单（F3 清扫，grep 口径 `grep -rn "newWorkflow\|\.start(vars" --include="*.java" nop-metadata/nop-metadata-service/src/main`）——**仅 QualityAlertWorkflowProcessor:89/96 一处**；`metaDataContractApproval`/`tagLabelConfirmApproval` 两个 xwf 无程序化 start 调用方（经平台标准 WfWorkflowBiz 启动，异常传播 → 事务回滚 → 无孤儿面）。nop-wf 引擎侧同构站点（WorkflowCoordinatorImpl:43 子流程等）属平台维度，不重复立案。**同族结论：单元内 1 处，全部命中本条**。
- **信心水平**: 确定（save-then-finish 窗口、同事务持久化、调用方吞异常、无清理路径四点均为直接代码事实；确定性触发条件「metadata-admin 角色未配置」属部署环境，但机制链不依赖该特定触发——任意 post-save 异常均命中）
- **误报排除**: 不与 C7-005（投递失败无重投/死信——成功创建后的投递面）、C7-007（无去重——成功创建后的重复面）、C7-R2-003（reJudge 原地改写——成功创建后的处置面）重复：三者均以「工作流创建成功」为前提，本条是**创建本身失败**的持久化残留，为第 4 个独立失效层。也不构成对 MA7.6-02 修复的重复报告——该修复消除的是「恒传 null ctx → NPE」这一具体失败原因，本条报告的是失败发生后的残留语义，MA7.6-02 修复后窗口依然存在（其 javadoc 自证当前代码为修复后形态）。
- **复核状态**: 已复核（2026-10-22 C7 独立复核）：**保留立案，降级 P1→P2**。机制全链重演确认：saveWfRecord=普通 wfDao().saveOrUpdateEntity（DaoWorkflowStore.java:130-132，无 REQUIRES_NEW）入调用方 @BizMutation 同一 ORM 会话；先 initCreateStatus 后起始步 actor 解析（WorkflowEngineImpl:213-226；起始步 assignment=role:metadata-admin，v1.xwf:30）未配置即 ERR_WF_ACTOR_NOT_EXISTS；调用方 catch 吞异常（NopMetaQualityRuleBizModel:175-178）→ 事务照常提交 → CREATED 孤儿持久化——**孤儿恰因 catch 免于同事务回滚而成立，驳回不成立**；WF_STATUS_CREATED 无清理消费点（grep 仅 isStarted 比较）。降级理由：① 触发依赖部署漂移/瞬态故障而非主路径输入；② 累积为人工速率（仅手动 executeQualityRule 路径创建告警）；③ 角色配置修复即恢复；④ 对齐 C4-R2-001 复核裁定的「环境触发+可恢复→P2」校准先例。判级裁量随 Phase 6 与告警通道家族（C7-005/007）合并复核。

## 二、本轮扩大面清扫表（终止信号载体）

| # | 扩大面 | 核查口径与证据 | 结论 |
|---|---|---|---|
| W10 | C7-R2-001 同族放大：全仓 `traceDepends`/`DaoEntityResource` 依赖声明面 | `grep -rn "traceDepends" --include="*.java"`（排除 target/_gen/test）全仓唯一 DaoEntityResource 钉定点 = DaoRuleModelLoader:84（R2-001 已案）；`DaoWorkflowModelLoader:53` 单行钉定充分（工作流 XML 整体存定义行 modelText，R2-001 已裁定无同族缺口，维持）。nop-rule 内 RuleModel 内容面复核：判定树子行 = NopRuleNode（R2-001 已案）；`NopRuleRole` **不参与** RuleModel 构建（`DaoRuleModelLoader.buildRuleModelNode:147-177` 仅消费 definition 行 modelText + getRuleNodes；`getRoleIds` 全单元 main 面 0 调用方）；matrix 型规则内容存 definition 行 modelText（buildRuleModelNode:166-175 仅 removeChildByTag，矩阵体不落子行）→ 定义行 updateTime 判据已覆盖 | **R2-001 同族在 nop-rule 内无第二兄弟**；失效面闭合维持 |
| W11 | 「缓存键=父行、内容变更在子行」站点全查（nop-metadata 侧） | static 跨请求容器复扫（`grep -E "static (final )?(Map|ConcurrentHashMap|Set|List|ICache|Cache)"`）仅 `EXTERNAL_TABLE_UPSERT_LOCKS`（C7-103 已案，键有界）；`buildGlobalClassNameToModuleId`（NopMetaModuleBizModel:616）为 generateManifest 请求内计算、无常驻缓存；query 包跨请求缓存 0 命中（第 2 轮 W9 维持）；`NopRuleNodeBizModel.predicateLabel:42` 的 `IServiceContext.getCache().computeIfAbsent` 为请求级缓存（随请求丢弃） | 无「实体行→常驻内存可计算物」第二站点；无新立案 |
| W12 | LocalJobScheduler 平台依赖面（job 状态持久化/重启自愈/配置行删除残留） | LocalJobScheduler 为纯内存调度器（`jobs = new ConcurrentHashMap`，LocalJobScheduler.java:48；类 javadoc:39「轻量级内存调度器」）——**无任何 job 状态持久化**：无持久化残留态可清理；重启自愈 = nop-metadata `init()` DB 重扫重注册（第 1 轮 R23 维持）+ FAILED 永久态止于 JVM 生命周期（C7-002 维持）。配置行删除残留 job（绕过 BizModel 直改 DB 面，第 2 轮 W3 已裁定）：`executeScheduledCheckpoint:216-232` catch 后 `buildErrorResult` 返回正常结果 → job 存活，每 tick 一条 ERROR 日志直至重启——W3「有重启自愈路径」裁定维持。**C7-001 触发链修正（加强证据，不改变判定）**：RUNNING 挂死期间调度器**无排队 fire**（`scheduleNext` 仅在 `handleResult` 完成后调用，LocalJobScheduler.java:301-311,379——单 job 任意时刻至多一个排队触发）→ cron tick 静默跳过、**零日志**；`ERR_CHECKPOINT_ALREADY_RUNNING` WARN 仅出现在手动执行路径。挂死可见性比 C7-001 所述「唯一可见信号是 WARN 日志」更差（cron 面无任何信号），F5 判定加强、C7-001 立案维持 | 无新立案；C7-001 信号面描述修正 |
| W13 | QualityResultWriter 失败重放面 | 单规则路径：`appendQualityResult` 写失败 → 异常传播 → mutation 回滚 → 判定行丢弃；重跑 = 重新 judge（幂等重建）→ 自愈成立。checkpoint 路径：AR-14 补偿 + C7-R2-002 run 级残余维持。批量路径 `executeQualityRulesForDataSource:266-277`：per-rule `flushSession + clearSession` 显式部分持久化契约（代码自注释「隔离失败：清理未刷出的脏实体，不影响已 flush 的规则」）→ F2 语义已声明 | 无新立案；C7-R3-001 为唯一重放面缺陷（wf 半程实例） |
| W14 | 长事务/长游标残留：批量导入/重建中途失败半程态（批 C 面补挖） | ① lineage 先清后建：`deleteSqlParseEdges` + `batchSaveEntities` 同一 mutation 事务（NopMetaLineageEdgeQueryAction.java:190-204 表级 / :262-282 列级）→ 失败整体回滚，无「已清未建」半程；② importOrmModels / migrateDataSourcesCredential / syncExternalTables：per-item REQUIRES_NEW + 幂等重跑收敛（第 1 轮 1.3 维持）；③ tag 标签批量（AutoClassification/LineageTagPropagation）：单 mutation 原子；④ collectCatalog 循环：每行独立时序快照事实，部分成功无语义损坏（retention 面归 C7-101）；⑤ 游标面：无服务端游标（第 1 轮维持），loadSortedDoubles 10k 上限维持 | 无新立案 |
| W15 | CL-603 同族（临时文件清理不对称）兄弟清扫 | `grep -rn "detachFile\|attachFile\|uploadFile\|loadXlsxObject"` 单元 main 面：仅 nop-rule `NopRuleDefinitionBizModel:135,142` 一处（CL-603 已案）；nop-metadata main 面 0 命中 | 同族收敛；CL-603 维持 |
| W16 | NopRuleLog 执行日志持久化面（G7 判定补强） | `RuleServiceImpl.executeRule:76-91`：落库受 `saveLogMessage` 开关 + `IRuleLogMessageSaver` 注入双门控；全单元 beans.xml 无 saver bean 装配、无 saveLogMessage 属性设置（`rule-defaults.beans.xml` 仅注册 nopRuleManager）→ 默认不落库，无 F4 面 | G7「无缺陷」维持，补装配级证据 |
| W17 | metrics 标签基数 / 时钟期限 / 慢性病复扫（C7 步骤 1/5/6 收尾） | metrics：0 命中（第 2 轮 W7 维持）；时钟：R20/R21 维持，本轮新增面（wf 时间戳、job state）无时钟回拨敏感点；慢性病：实例字段惰性初始化（CL-502 维持）、ResourceDependencySet 随缓存条目存亡（W9 维持） | 无新增 |

## 三、本轮统计

- 新发现：**1 条**（P1×1：C7-R3-001）
- 清扫表：8 行（W10-W17）；C7-001 触发链信号面修正 1 处（W12，立案不变）
- 与前轮关系：C7-R2-001 同族面闭合（W10/W11——nop-rule 无第二兄弟、nop-metadata 无同构站点）；C7-005/C7-007/C7-R2-003 之外的第四失效层立案（C7-R3-001 创建失败残留面）；第 1 轮 G7/R23、第 2 轮 W3/W9 判定全部维持并补强证据
- 第 3 轮终止判定：四个重点面均已闭合——依赖声明面（W10/W11）、平台调度依赖面（W12）、失败重放面（W13/C7-R3-001）、批量半程面（W14-W16）。同族放大未发现 R2-001 的第二个兄弟站点，剩余线索均为已案发现的面修正或证据补强。**本轮后无新增必派触发项（本轮唯一 P1 为新根因族，如按纪律需第 4 轮放大核查，其同族面 W17 已在本轮一并闭合）**


---

# C7 深挖第 4 轮（窄域收尾）

> 审计日期: 2026-10-22 | 子代理: C7 维度深挖第 4 轮（第 3 轮新增 P1 触发的必派轮，窄域收尾） | 基线: live code（commit 81a7c228e7 同基线工作区）
> 窄域（任务书）: ① C7-R3-001 同族放大——「外部编排面（workflow/消息/HTTP 回调）start 之后、本方事务提交之前」的半程残留站点在 nop-metadata 内还有无第二处；核实 webhook 提交前投递（CL-404，任务书给定编号）与本族的异同，同一根因不同站点则按 CL 线索关联不重复立案 ② P1 族（C7-001/C7-102/C7-R2-001/C7-R3-001）共同根因层（外部依赖无超时 / 失效传播缺口 / 半程残留）在本单元是否还有未盘点的第四形态（规则执行 SQL 沙箱内无超时面、调度池线程其他阻塞原语等）
> 盲审纪律: 本轮可读本维度文件（前三轮产物）；未读其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/ 下任何文件（CL-404 为任务书给定的跨维度登记编号，本轮仅按任务书描述做异同判定，未读取其原文）。live code 为准；只读审计（除本文件追加外未修改任何文件）。`_` 前缀生成物不作发现对象。

## 一、窄域 1：C7-R3-001 同族放大——外部编排面站点全量清点

清点口径：全单元 main 面 grep 全部外部系统接口族——`IWorkflowManager|newWorkflow|wfManager`、`IMessageService`、`IHttpClient|HttpClient|HttpURLConnection|RestClient|WebClient`、`ISearchEngine`、`IEventBus|IQueueProducer|IMessageBus|ICluster|IRpcService`、`IOrmEntityFileStore|fileStore`、`txn().afterCommit|ITransactionListener`——逐一判定是否构成「本方事务提交前启动外部动作、失败残留无补偿」站点。

| # | 外部面 | 站点清单（grep 复现） | 提交前窗口 | 判定 |
|---|---|---|---|---|
| E1 | IWorkflowManager / wf.start | 仅 `QualityAlertWorkflowProcessor.java:89,96`（单元内唯一程序化 start，与 C7-R3-001 失效面枚举一致） | 是（wf.start 内 initCreateStatus 落库于调用方 @BizMutation 事务） | **已立案 C7-R3-001**；无第二站点 |
| E2 | IHttpClient（webhook） | 仅 `CheckpointActionDispatcher.dispatchWebhook`（per-action dispatch 循环 :185-204） | 是（`NopMetaQualityCheckpointBizModel.java:511-514` 在 `runWithoutTransaction` 内 dispatch——store 已 flush、外层框架事务未提交） | **CL-404 已登记站点**：本文件 R17 同点，类 javadoc（R17 引证 :37-46）明文「已知残余（check2 P3-10 暂缓项）」→ 按任务书口径关联 CL-404，不重复立案 |
| E3 | IMessageService（notify） | 仅 `CheckpointActionDispatcher.dispatchNotify`（与 E2 **同一 dispatch 循环、同一时点**） | 是（同 E2） | **与 CL-404 同一站点**（非第二站点）：同一循环、同一 javadoc 声明边界覆盖 → 并入 E2 关联 |
| E4 | ISearchEngine（搜索索引） | `search/NopMetaSearchProcessor.java:47-86`（addToIndex/removeFromIndex）← 6 个 BizModel 消费点：`NopMetaEntityBizModel.java:133,153`、`NopMetaEntityFieldBizModel.java:51,59`、`NopMetaClassificationBizModel.java:31,39`、`NopMetaGlossaryTermBizModel.java:51,60,68`、`NopMetaTagBizModel.java:62,70`、`NopMetaModuleBizModel.java:300,304,322` | 是（各 save/delete 方法体内、框架提交前同步写/删索引文档） | **同根因不同站点** → 登记 CL-504（见下），按任务书口径不重复立案 |
| E5 | IOrmEntityFileStore（nop-rule 导入临时文件） | 仅 `NopRuleDefinitionBizModel.java:129-142`（CL-603 已案，第 3 轮 W15 同族收敛维持） | 是（attach/detach 与 DB 事务不同原子域） | 已登记 CL-603；无新面 |
| E6 | MetaModelChangedEventPublisher | 直接 DB 行写入（类 javadoc:37「主路径 = 直接 DB 写入事件行，不依赖 IMessageService 订阅者注册机制」） | — | 非外部编排面（任务书排除口径经代码核实成立） |
| E7 | EventBus / 消息队列 / 集群广播 / RPC 生产者 | `grep -rnE "IEventBus|IQueueProducer|IMessageBus|ICluster|IRpcService"` → 0 命中 | — | 无该失效面 |

### [nop-rule-metadata-CL-504]（CL-404 同族、不同站点）搜索索引在提交前窗口写入/删除：事务内失败面已防护，提交阶段失败面无补偿、依赖人工 rebuildSearchIndex 收敛

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/search/NopMetaSearchProcessor.java:47-86`（写点）；代表消费方 `entity/NopMetaEntityBizModel.java:133,138-157`
- **证据片段**:
  ```java
  // NopMetaEntityBizModel.save —— addToIndex 为方法体最后一个操作，随后框架提交
  eventPublisher.publishEventWithSnapshots(...);
  searchService.addToIndex("MetaEntity", saved.getMetaEntityId(), toSearchableDoc(saved));
  return saved;
  ```
  ```java
  // NopMetaEntityBizModel.delete —— removeDocs best-effort（check2 P2-06 注释明文取舍）
  // check2 P2-06：主实体清理与子实体清理统一 best-effort（fail-closed 会回滚 DB 删除 →
  // "实体留存、索引已删"分裂）
  safeRemoveFromIndex("MetaEntity", id);          // :153
  ```
- **形态**: 索引文档写入/删除发生在调用方 @BizMutation 事务提交前（与 CL-404 webhook 同根因：未提交窗口内外部副作用逃逸事务原子性）。三态核查：① 事务内失败面——`importOrmModels` 已有 AR-08 完整反向对账（`NopMetaModuleBizModel.java:256,279,295-310`：任一 addToIndex 失败 → 已写文档 reverse removeDocs + REQUIRES_NEW 回滚，「三态一致回滚」）；单实体 save 的 addToIndex 为方法体末操作，fail-closed 默认（`NopMetaSearchProcessor.searchIndexFailOpen=false`，:32）异常 → 整个 mutation 回滚，无残留，自洽；单实体 delete 的 removeDocs 失败 → best-effort WARN（P2-06 声明的取舍）。② **提交阶段失败面**（索引写成功后框架 commit 失败 → 幽灵/缺失索引文档）：任何事务内对账都无法覆盖——AR-08 的反向清理仅在事务 lambda 内异常时运行（:308 注释口径），commit 阶段失败发生在 lambda 返回之后；该面与 CL-404 的「提交阶段失败」残余同构，且在 search 面无明文声明。③ 修复面——`NopMetaSearchBizModel.rebuildSearchIndex`（:52-58，全量重建 action，`NopMetaIndexBuilder.buildFullIndex`）存在，分歧可由管理员显式重建收敛；触发条件为提交阶段 DB 故障（罕见、瞬态、无自动检测信号）。
- **线索**: 与 CL-404 同一根因、不同站点、不同失效方向（CL-404=外部世界被告知未提交状态；本条=外部系统保留了已回滚状态的投影）。按任务书口径「同一根因不同站点按 CL 线索关联、不重复立案」，登记为 CL-404 同族线索（P3 watch）。**联动提示**：若 CL-404 的 check2 P3-10 暂缓决策点（投递改 `ITransactionListener.onAfterCommit` 化）将来落地，search 面的 addToIndex/removeFromIndex 应同批迁移，否则同一决策点将留存两个不一致的残余面。
- **严重程度**: P3（watch）| **信心水平**: 确定（站点、时序、防护面、rebuild 修复面均为直接代码事实）| **复核状态**: 已复核（2026-10-22 C7 独立复核）：**确认（P3 watch）**。searchIndexFailOpen 默认 false（NopMetaSearchProcessor:31-32）、save 末位 addToIndex（NopMetaEntityBizModel:133）、delete best-effort safeRemoveFromIndex（:153，P2-06 注释在场）逐点确认；与 CL-404 同根因不同站点关联判定维持。

## 二、窄域 2：P1 四案共同根因层第四形态核查

| # | 候选第四形态 | 核查口径与证据 | 结论 |
|---|---|---|---|
| F1 | SQL 沙箱内未盘点无超时面（CallableStatement/prepareCall/executeBatch/addBatch/setFetchSize/setNetworkTimeout 逃逸） | `grep -rnE "CallableStatement|prepareCall|executeBatch|addBatch|setFetchSize|setNetworkTimeout" --include="*.java" nop-metadata/*/src/main nop-rule/*/src/main` → **0 命中**；Statement/PreparedStatement 创建点全部落于第 2 轮 W1 已闭合的 15 点清单（抽查 `MetaQualityRuleExecutor.java:559,618,711,732,757` 维持）；security 包仅 `HostSecurityUtil`（纯 host 字符串校验，无 I/O、无独立连接面） | **无第四形态**；C7-001/C7-102 失效面闭合维持（W1 结论不变） |
| F2 | 调度池线程上未盘点阻塞原语（Future.get/join、CompletableFuture、Thread.sleep/wait/join、CountDownLatch.await、parallelStream、Semaphore.acquire、lock()、awaitTermination/invokeAll/FutureTask、executor submit/execute） | 两轮互补 grep（`.get()/.join()/Thread.sleep/.await(/CountDownLatch/parallelStream/CompletableFuture/.acquire(/lock.lock(/ForkJoin` + `awaitTermination/invokeAll/FutureTask/.wait(/Thread.join/tryLock/lockInterruptibly/Semaphore/.submit(/.execute(`）→ 仅命中业务方法 `execute()` 调用（TableReferenceExecutor/processor.execute 语义，非 executor 提交）；单元内无线程池创建（第 1 轮 C4 基线维持） | **无第四形态**：cron/请求线程的全部阻塞面 = 外部 JDBC（C7-001/C7-102 已案）+ webhook（30s 已声明超时，R16）+ notify（平台 IMessageService 实现边界）+ wf.start（DB 内，C7-R3-001 面），均已盘点 |
| F3 | 失效传播缺口（C7-R2-001 根因层）第四站点 | 第 3 轮 W10/W11 已闭合（traceDepends 全仓唯一 DaoEntityResource 钉定点；无「父行键→常驻可计算物」第二站点）；本轮复扫无新增 static 容器/跨请求缓存 | 闭合维持，无新站点 |
| F4 | 半程残留（C7-R3-001 根因层）第四站点 | 本轮窄域 1 全量外部面清点（E1-E7）：站点全集 = wf.start（已案）+ webhook/notify（CL-404 已登记，同一站点）+ 搜索索引（CL-504 新登记 watch）+ 文件库（CL-603 已登记）+ 事件表（非外部面）+ 其余 0 命中 | **无未盘点的 P 级第四站点**；站点全集收敛 |

## 三、终局清扫结论与终止信号

- **窄域 1 闭合**：外部编排面站点全集清点完成——单元内「提交前窗口外部动作」站点共 4 族（workflow / webhook+notify / 搜索索引 / 文件库），其中 3 族已在前三轮立案或登记，本轮新增 1 条 CL 线索（CL-504，P3 watch，同根因关联不立案）。**CL-404 与 C7-R3-001 异同判定**：同根因族（未提交窗口内外部交互无补偿语义）、不同失效层（R3-001 = 外部编排半程残留随本方事务提交落库为本地孤儿行；CL-404 = 外部世界被告知未提交状态、本地无残留）——按任务书口径关联 CL-404，不重复立案；notify 与 webhook 为**同一站点**（同一 dispatch 循环、同一时点、同一 javadoc 声明边界覆盖），无独立登记面。
- **窄域 2 闭合**：P1 四案共同根因层的候选第四形态（SQL 沙箱逃逸无超时面、调度线程未盘点阻塞原语、失效传播第四站点、半程残留第四站点）经互补 grep + 站点清点**全部 0 命中/已登记**，四案失效面在本单元内收敛闭合。
- **本轮终止：无新增发现**（无 C7-R4 编号立案条目；新增 CL 线索 1 条：CL-504，P3 watch，按窄域 1 口径关联 CL-404 不立案）。

## 四、本轮统计

- 新发现（立案）：**0 条**；CL 线索：**1 条**（CL-504，P3 watch，CL-404 同族不同站点）
- 清扫表：窄域 1 外部面清点 7 行（E1-E7）+ 窄域 2 第四形态核查 4 行（F1-F4）
- 与前轮关系：W1（C7-001/C7-102 失效面闭合）、C7-R3-001 失效面枚举（wf.start 唯一站点）、CL-603、R17 判定全部维持；CL-404 异同判定完成并新增同族站点关联（CL-504）
- 终止判定：两族窄域均闭合，无新增必派触发项。**未发现新的问题。深挖结束。**


---

# C7 维度复核结论（CRA WI 5.5 独立复核）

> 复核日期: 2026-10-22 | 复核人: C7 维度独立复核子代理（与初审/深挖轮不同 task）
> 方法: 15 条发现逐条重读引用源码 file:line 重演证据链；P1 逐条深核（含 LocalJobScheduler / nop-wf / nop-orm 平台侧独立复核）；关键计数口径全仓 grep 复跑；批次覆盖痕迹核验。只读复核（除本节追加与各发现「复核状态」回填外未修改任何文件）；本节为派生视图，证据以各发现条目原文为准。

## 一、逐条判定表

| 发现 | 原判 | 复核判定 | 关键复核证据（file:line） |
|---|---|---|---|
| C7-001 | P1 | 保留 P1 · 次登记（与 C7-102 同缺陷合并） | grep setQueryTimeout/setNetworkTimeout/socketTimeout main 面 0 命中（复跑）；login timeout 仅界建连（MetaDataSourceConnectionProcessor:124,140-154,199-212）；queryLong 族无超时（MetaQualityRuleExecutor:706-782）；标记全程持有（NopMetaQualityCheckpointBizModel:203,266-269）；W12 修正成立（LocalJobScheduler.java:301-311,379——RUNNING 挂死期间无排队 fire，cron tick 零信号） |
| C7-002 | P2 | 保留 P2 | Error→FAILED 不再依赖注释转述：LocalJobScheduler.handleResult:352-361 + rescheduleAfterUpdate:256-264（仅 WAITING 重排）+ resumeJob:168-175（仅 SUSPENDED）+ fireNow:218（拒 FAILED） |
| C7-003 | P2 | 保留 P2 | 逐规则建连（MetaQualityCheckpointExecutor:129-186,319 → TableReferenceExecutor:107-134）；批量路径形态正确（NopMetaQualityRuleBizModel:219-279） |
| C7-004 | P1 | 保留 P1 | 七表清理 grep 0 命中（含 nop-job/nop-batch/nop-sys）；orm.xml:2095-2098 insert-only；mediumtext 双快照 orm.xml:2812-2815；发布点实测 24 处（原 27 略高估，实质不变） |
| C7-005 | P3 | 保留 P3 | per-action catch 仅日志（CheckpointActionDispatcher:197-202）；webhook 30s 超时（:249） |
| C7-006 | P2 | 保留 P2 | 行循环 × 全池重载（ReconciliationExecutor:89-91 × LocalReconciliationProcessor:93-103） |
| C7-007 | P3 | 保留 P3 | 无去重门禁（QualityAlertWorkflowProcessor:67-98；唯一调用方 NopMetaQualityRuleBizModel:171-179） |
| C7-101 | P2 | 保留 P2（并入 retention 家族 Phase 6 并裁） | 三写点追加（DataSource:880-892 / Module:600-607 / Entity:395-405）；清理 grep 0 命中 |
| C7-102 | P1 | 保留 P1 · 主登记 | buildDataSource 仅透传四参（:269-297）；15 语句点闭环（W1）；checkpointRunLocks 永久残留链重演（NopMetaQualityCheckpointBizModel:203,266-269 × LocalJobScheduler 单 fire 语义） |
| C7-103 | P3 | 保留 P3（watch） | computeIfAbsent 无 remove（NopMetaDataSourceBizModel:1053,1070-1078）；声明边界在场（:1050-1052） |
| C7-R2-001 | P1 | 保留 P1 | 钉定单点（DaoRuleModelLoader:84）+ 判据=定义行 updateTime（DaoEntityResource:104-125；间隔 5000ms=OrmConfigs.java:75）+ 内容取子行（buildRuleModelNode:156-177）+ 子行一等 CRUD（_NopRuleNode.xbiz DefaultBizGenExtends forEntity="true"；NopRuleNodeBizModel 无 save/delete override） |
| C7-R2-002 | P3 | 保留 P3 | run 实体 0 命中（orm.xml grep）；AR-14 补写失败仅日志（MetaQualityCheckpointExecutor:176-184） |
| C7-R2-003 | P3 | 保留 P3 | reJudge 原地 update（QualityAlertWorkflowProcessor:130-135）vs ORM insert-only 声明矛盾 |
| C7-R2-004 | P3 | 保留 P3 | upsert 循环无删除对账（NopMetaDataSourceBizModel:646-660）；AR-17 契约独立确认完好 |
| C7-R3-001 | P1 | **保留立案、降级 P2** | 同事务确认（DaoWorkflowStore.java:130-132 无 REQUIRES_NEW）+ 先 save 后 actor 解析（WorkflowEngineImpl:213-226；v1.xwf:30 role:metadata-admin）+ catch 吞异常致无回滚自愈（NopMetaQualityRuleBizModel:175-178）——孤儿不因同事务回滚自愈恰因 catch 存在，驳回不成立；降级理由见下 |

**C7-R3-001 降级理由（P1→P2，保留立案）**：① 触发依赖部署漂移（起始步角色未配置）或瞬态 DB 故障，非主路径输入；② 累积为人工速率——告警仅在手动 `executeQualityRule` 路径创建，cron 检查点路径不触发；③ 角色配置修复即恢复功能（存量孤儿需人工清理）；④ 对齐 C4-R2-001 独立复核已裁定的「环境触发 + 可恢复 → P2」校准先例。判级裁量可随 Phase 6 与告警通道家族（C7-005/007）合并复核。

## 二、P1 超时族同族裁定（C7-001 / C7-102 / C4-R2-001）

**判定：同一缺陷（外部 JDBC 全链无任何超时预算）、三个后果层，非三个独立缺陷。** 三案共享同一物理根因——buildDataSource 仅透传 url/username/password/driverClassName（MetaDataSourceConnectionProcessor:269-297）、login timeout 仅界建连段（:124,148-154）、`setQueryTimeout`/`setNetworkTimeout`/`socketTimeout` 全单元 main 面 grep 0 命中（本轮独立复跑确认）：

| 登记 | 维度 | 后果层 |
|---|---|---|
| C4-R2-001（P2，C4 复核已裁定维持） | C4 | 调度池线程占用 + per-job 监视器锁死 → 控制面（suspend/cancel/state）不可干预 |
| C7-001 | C7 | cron 检查点链静默停摆（标记持久 + tick 零信号跳过，W12 修正）→ 质量监控职能停摆不可检测 |
| C7-102 | C7 | 全消费面（14 withConnection + 15 语句点）无界阻塞 → checkpoint 功能锁死 + worker 线程线性泄漏 |

**主/次登记建议**：主登记 = **C7-102**（失效面为三案并集，W1 已闭合，单点修复位置最明确：withConnection/buildDataSource 收口）；次登记 = **C7-001**（质量监控停摆后果层，W12 信号面修正随迁主登记）；**C4-R2-001** 维持 C4 维度登记作控制面后果层跨维关联。合并裁定归 Phase 6。

**判级建议**：合并实体建议统一 **P1**——合并视角（主职能静默永久停摆 + 控制面锁死 + 线程泄漏，零自愈零信号）超过任一单维视角；C4 复核维持 P2 的校准（环境触发、重启可恢复、建连段有界）登记在案供 Phase 6 裁量。**无论取 P1 还是 P2，三个登记点必须同步统一**，不应出现同一缺陷跨维双判级。

## 三、批次覆盖核验

| 批次 | 覆盖痕迹 | 结论 |
|---|---|---|
| 批 B（主战场：quality 8f / reconciliation 3f / event / sync / profiling / catalog / connection） | 初审 R1-R24 + C7-001~007 + R2/R3 扩大面回扫（W1/W6/W12/W13） | 有痕迹，闭合 |
| 批 A（nop-rule main） | 追加 1.1 G7 / 1.2 H4 / 1.3 热更新链与 RuleRuntime / W16 NopRuleLog + C7-R2-001 + W10/W15 + CL-603 | 有痕迹，闭合 |
| 批 C（entity 包 42f） | 追加 G1/G2/G3/G6 + C7-103 / C7-R2-004 + CL-602 + W14 长事务面 | 有痕迹，闭合 |
| 批 D（query / lineage / search / sqlview / tableref 等其余包 + api/core/dao/app） | 追加 H1（query 四执行器）+ 1.3 查询面缓存 + CL-601/CL-604 + W1/W11/F1 | 有痕迹，闭合 |

**缺口登记（不补审）**：
1. nop-metadata-api（30f）：批 D 头部仅声明「plan-first 只读」，正文无该 30f 的逐面核查痕迹（无发现亦无清扫表行）——api 面覆盖深度未留证。
2. nop-rule 文件计数 72f（审计单元口径）vs 批 A 71f/5,205 行——1f 差异未注明口径（疑统计时点差），不影响任何发现实体。

## 四、CL 线索判定（8/8 确认，0 驳回）

CL-501 确认（构造器写 JVM 全局 DriverManager.loginTimeout，:140-154；与 C4 维度 CL-205 同点，Phase 6 归并）；CL-502 确认（惰性初始化 check-then-act 理论竞态，维持不立案）；CL-503 确认（webhook :247 / notify :498-504 全量 summary payload，移交 C8）；CL-504 确认（P3 watch，与 CL-404 同根因不同站点关联维持）；CL-601 确认（构造器快照 :74 vs 活性读取 :404,:436 错位固化）；CL-602 确认（find-then-increment :657-667 + UK orm.xml:2179）；CL-603 确认（detachFile :142 仅成功路径）；CL-604 确认（四端点 :56/78/101/139 全量重建）。

## 最终保留项

**15/15 保留（0 驳回、1 降级：C7-R3-001 P1→P2）；CL 8/8 确认。**

| 严重度 | 发现 |
|---|---|
| P1×4 条（3 个独立缺陷） | C7-102（超时族主登记）、C7-001（超时族次登记，与主登记同缺陷）、C7-004（retention 族主登记）、C7-R2-001（热更新失效传播） |
| P2×5 | C7-002、C7-003、C7-006、C7-101（retention 族）、C7-R3-001（复核降级） |
| P3×6 | C7-005、C7-007、C7-103（watch）、C7-R2-002、C7-R2-003、C7-R2-004 |

**Phase 6 合并建议**：① 超时族三案合并为一缺陷（C7-102 主 + C7-001 次 + C4-R2-001 跨维关联，判级三登记点统一）；② retention 族（C7-004 主 + C7-101 + C7-R2-003 写语义子面）按表归并裁定；③ 告警通道族（C7-005/007/R3-001）合组裁决修复优先级。
