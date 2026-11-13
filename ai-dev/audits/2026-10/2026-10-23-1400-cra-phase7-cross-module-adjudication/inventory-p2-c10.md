# CRA Phase 7.1 盘点 — P2 实体全量清单与 C10 缺保护映射（子代理 B）

- Status: open（7.1 跨模块裁定输入；权威来源 = 各单元 `merge-adjudication.md` 工单级清单 + `C10-test-protection.md` 缺保护登记/映射表）
- 日期：2026-10-23。工作仓库：`nop-entropy-concurrency-audit`。
- 口径：**P2 实体 = 工单级 P2 裁定项**（合并裁定后的族工单或独立工单；被并成员不在此列，其判级随主工单）。C10 条目 = 各单元缺保护登记（与缺陷本体分开计数）。5.5/6.5 另注实体级口径。
- 读取清单：32 个单元目录全部 `merge-adjudication.md` + `C10-test-protection.md`（编号化登记节/映射节）+ `cra-index.md` 报告清单与计数口径块。

---

## §一 P2 实体全量清单（按单元 32 节，共 560 工单级；实体级 565）

> 列：Finding ID（工单号+主发现 ID）｜一句话（≤60 字）｜所属族｜判级｜特殊标记。族工单的「所属族」= 自身族名；独立条目 = —。特殊标记缩写：**条化**=条件化激活/回升条款；**移交**=修复归属跨单元；**钉**=反向钉死/假保护改写义务；**watch**=watch-only 候选；**dormant**=休眠/零生产接线；**C10 前置**=工单绑定 C10 验收/护栏义务。

### WI 1.1 nop-stream-core-runtime（27）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-003 | BasicTypeInfo 静态 HashMap of() 无同步 CTA | — | P2 | |
| C1-004 | TaskManager 两阶段分配缺 epoch 守卫（重复槽位/installInvokable） | — | P2 | |
| C1-005 | finishCommit contains→commit→remove TOCTOU 双重提交（←C2-007） | — | P2 | C10-011 零判别力钉 |
| C1-007 | InputGate 水位裸数组无同步读取 | — | P2 | |
| C1-008 | reader 非 volatile+跨监视器判空入队（←C2-002） | — | P2 | |
| C2-003 | commit-fanout CallerRuns 饱和拉回监视器（←C3-003） | — | P2 | C10-012 |
| C2-004 | registry 写侧持锁读路径裸迭代 | — | P2 | |
| C2-005 | BarrierTracker 持监视器直通快照序列化 | — | P2 | C10-013 |
| C3-009 | queryable-state 跨线程无同步快照 CME | — | P2 | C10-014 |
| C4-005 | per-node barrier fanout 无界队列静默失效 | — | P2 | |
| C4-006 | LOCAL 取消不对称：中断后不 cancel 子任务自旋 | — | P2 | C10-015 |
| C4-012 | stop 竞速窗口静默丢 2PC 通知 | — | P2 | C10-016 |
| C5-002 | checkpoint 序列化峰值 ≈ 状态 4–8× | — | P2 | |
| C5-003 | join/over-window 工作视图全量驻堆 | — | P2 | |
| C5-004 | /jobs 端点无上限 readAllBytes（←C8-008） | — | P2 | C10-017 |
| C5-005 | timer mail 无去抖+邮箱无界（←C7-004/C8-004） | — | P2 | C10-021 |
| C5-006 | failedCommitParticipants 持续失败无上界无出口 | — | P2 | C10-018 |
| C5-012 | MemoryBudget write-only 死配置零执行者（←C8-015） | — | P2 | |
| C7-005 | 持续失败路径错误日志无抑制放大 | — | P2 | |
| C7-007 | releaseHolder 置空不删 meter 永驻 | — | P2 | C10-023（关联 C1-009） |
| C7-012 | 算子 counter identityHashCode 标签无移除（←C5-008） | — | P2 | |
| C8-005 | poll 失败行 skip+cursor 前移静默丢记录（←C3-004） | — | P2 | |
| C8-009 | duplicate 静默吞没+拒绝路径许可/登记不清理 | — | P2 | C10-019 |
| C9-001 | 聚合热路径每记录全量反射拷贝 | — | P2 | C10-024（BG-3） |
| C9-003 | 驱逐路径热路径 O(n) 全表扫描 | — | P2 | |
| C9-004 | FLIP-27 空闲 yield 自旋烧核 | — | P2 | C10-025（BG-4） |
| FAM-2 | SideOutputRegistry 作用域/生命周期族（C1-002+C5-007+C7-006） | 族工单 FAM-2 | P2 | C10-022 改指 FAM-2 |

### WI 1.2 nop-stream-state-connectors（26）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-09（C2-011） | gauge 空 tags 进程级注册无摘除（永久 NaN） | MA-09 | P2 | |
| MA-10（C7-006） | readerSchemaFor 静默回落+无负缓存退避 | MA-10 | P2 | |
| MA-11（C4-002） | Avro HttpClient 无关闭 selector 线程泄漏（←C7-013） | MA-11 | P2 | |
| MA-12（C2-004） | reader 并发入口无同步（launch 守卫+交付字段） | MA-12 | P2 | **条化**（接线/DISTRIBUTED 回升 P1） |
| MA-13（C5-005） | 2PC sink 状态无上界旋钮（epoch 缓冲+pendingCommits） | MA-13 | P2 | |
| MA-14（C5-004） | 迁移 helper 全 CF 双列表堆内物化 | MA-14 | P2 | |
| MA-15（C2-005） | manifest 跨 JVM 双级锁无超时不可中断 | MA-15 | P2 | |
| MA-16（C5-001） | reconciledTimerKeys 每 key 单调增长永不清理 | MA-16 | P2 | |
| MA-17（C7-012） | gauge WARN 无一次性守卫「告警一次」未实现（←C2-008） | MA-17 | P2 | P3 异议留档 |
| MA-18（C8-010） | checkpoint 时长与固定预算无耦合守卫（+增量重哈希） | MA-18 | P2 | |
| C3-005 | Kafka cancel 无 wakeup 挂起至 max.block.ms | — | P2 | |
| C3-006 | Debezium 失败监听静态表按键覆盖串号 | — | P2 | |
| C3-010 | BatchLoader cancel 无中断响应点 | — | P2 | |
| C3-011 | Debezium run() 重入守卫静默 return 假 EOS | — | P2 | C10-015 锚定 |
| C3-012 | 桥 reader close 超时先抛后 clear 无 interrupt | — | P2 | |
| C6-005 | openDB 失败泄漏全部 native 句柄 | — | P2 | |
| C6-006 | restoreRangeInto 先关 DB 后关 handles | — | P2 | |
| C6-007 | File 2PC epoch 临时文件永久孤儿 | — | P2 | |
| C6-011 | Files.walk 未 TWR 关闭 fd 泄漏 | — | P2 | |
| C7-003 | 增量恢复临时目录跨 id 残留无清扫 | — | P2 | |
| C7-010 | RocksDB TTL 清理仅挂 snapshotState doc-code 分歧 | — | P2 | |
| C8-002 | CEP 匹配缓冲无过载行为/包络声明 | — | P2 | 升 P1 驳回维持 |
| C8-008 | connector 边界积压深度零遥测（限缩后） | — | P2 | |
| C8-009 | JDBC 2PC 无语句级超时+checkpointTimeout 盲区 | — | P2 | |
| C8-011 | message sink 每记录同步 send 无 opt-out | — | P2 | 判级取向维持 P2 |
| C8-012 | push→pull 桥仅条数界无字节界 | — | P2 | |

### WI 1.3 nop-stream-orchestration-thin（10）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-02（C1-004） | sideInput「冻结快照」契约零强制（写污染/UOE/撕裂） | MA-02 | P2 | |
| MA-03（C3-001） | ReplayEngine 失败伪装 EOS 绕开 pendingError（←C6-001/C7-001） | MA-03 | P2 | C10-005 收口门 |
| MA-04（C6-002） | ReplaySubscription 取消回调三重偏离 Cancellable（←C3-004） | MA-04 | P2 | |
| MA-05（C6-004） | 进程级静态缓存无上界无逐出双载体（←C3-006） | MA-05 | P2 | |
| C1-002 | ROOT_SCOPE 静态共享 eval 根作用域无写守卫 | — | P2 | |
| C1-003 | CAS 单读者守卫非粘性：槽位替换窗口零发射（与 C6-007 联调） | — | P2 | C10-006 trip-wire 反向约束 |
| C1-005 | build() 就地改写调用方持有 StreamModel | — | P2 | |
| C3-005 | GETTER_CACHE computeIfAbsent 锁内反射（类加载锁倒置） | — | P2 | |
| C6-003 | StreamScalarFunctionResolver default 只注册不清除 | — | P2 | |
| C6-005 | ReplayEngine.subscribe 无重复订阅防护 | — | P2 | |

### WI 1.4 nop-job（23）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-02（C1-001） | getJobDetail 无锁读受监视器保护状态（←C2-002） | MA-02 | P2 | |
| MA-03（C1-005） | addJob raced 分支并发 remove 静默丢注册（←C2-003） | MA-03 | P2 | 与 C2-004 同分支共设计 |
| MA-04（C1-002） | SimpleExecutionContext 逃逸 live jobParams/state（←C2-008） | MA-04 | P2 | |
| MA-05（C4-004） | Error 吞没族：JDK 周期抑制+whenComplete 吞 Error（←C4-012） | MA-05 | P2 | F9 主题束 |
| MA-06（C4-009） | 派发 promise 无超时：容量账本侵蚀+RUNNING 滞留（←C7-002） | MA-06 | P2 | **钉**（checker 侧被 plan340 契约测试+注释钉死，修复位 worker 侧）；C10-002 |
| MA-07（C5-003） | CronExpression ThreadLocal 无 remove×planner 重建（←C1-006） | MA-07 | P2 | P3 异议留档 |
| C1-003 | public scanOnce 无并发守卫逃逸单线程契约 | — | P2 | |
| C1-004 | register 复合注册非原子 REE 零回滚 | — | P2 | |
| C2-004 | raced 分支绕过 allowUpdate=false 契约 | — | P2 | |
| C2-007 | running 占位滞后：fireNow 重入/SOE/awaitIdle 误报 | — | P2 | 与 C2-001 共设计 |
| C4-002 | 定时触发与 job 执行共用 4 线程池无隔离 | — | P2 | |
| C4-003 | scanner 重排链 Throwable/拒绝永久停摆 running 说谎 | — | P2 | 载体 nop-commons BindScheduledExecutor；**移交**候选（平台级）；C10-005 |
| C4-005 | worker 任务与 scanner 共享平台 globalWorker 无上限 | — | P2 | 跨单元关联 WI1.2 MA-06 |
| C5-002 | reportTaskLog 批量端点无条目数/体积上限 | — | P2 | C10-008 |
| C5-004 | max-concurrency=0+capacity 退化 MAX_VALUE 单周期 10 万认领 | — | P2 | |
| C7-003 | SUSPICIOUS 单向棘轮+空集放大误杀在途任务 | — | P2 | |
| C7-004 | 三表无保留/归档且 delete 显式阻断 | — | P2 | |
| C7-005 | reconciler 固定排序窗口饥饿 | — | P2 | |
| C7-006 | 本地模式单次执行异常永久 FAILED 无自愈 | — | P2 | |
| C7-007 | 扫描线程 DB 挂起无 stall 检测零日志 | — | P2 | |
| C7-009 | 触发器计算持久失败无隔离双环 | — | P2 | |
| C7-010 | planner fence 过期窗口同一触发槽双执行 | — | P2 | |
| C7-011 | CLAIMED 态零回收（isRunning 门矛盾+stale 仅 WAITING） | — | P2 | C10-006（R7 时钟字段 startTime） |

### WI 1.5 nop-wf-nop-task（29）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-06（C7-002） | 扫描失败/跳过项无收敛不可见（毒步骤/DEBUG 静默） | MA-06 | P2 | |
| MA-07（C5-002） | transferActorsAsync 批量转办无条数上限（←C8-003） | MA-07 | P2 | |
| MA-09（C7-008） | 全局闸门可观测性缺口（warn-once+stats 零消费） | MA-09 | P2 | |
| MA-10（C8-008） | 闸门配置冻结零失效路径（cacheKey 无版本无 remove） | MA-10 | P2 | |
| MA-11（C2-005） | throttle 非重入信号量嵌套同 key 耗尽 | MA-11 | P2 | C10-002 |
| MA-12（C8-007） | throttle 参数构建期校验缺口（←C2-013） | MA-12 | P2 | |
| MA-13（C8-006） | catch(Exception) 漏 Error：许可泄漏/promise 悬挂 | MA-13 | P2 | C10-013 |
| MA-14（C3-005） | timeout/kill 与 body 生命周期解耦（幽灵/钉死/早释） | MA-14 | P2 | |
| MA-15（C1-004） | step 级 first-terminal-wins 守卫非原子 | MA-15 | P2 | |
| MA-16（C7-012） | sleep 同步阻塞等待点+无上限校验 | MA-16 | P2 | |
| MA-17（C1-003） | WorkflowImpl 实例封闭契约未声明 javadoc 失真 | MA-17 | P2 | 文档/契约级 |
| MA-18（C5-004） | 计数类边界缺失（fork-n/loop-n 无上界） | MA-18 | P2 | |
| MA-20（C1-007） | addStepCleanup/runStepCleanups 锁纪律不对称（←C2-004） | MA-20 | P2 | P3 异议留档 |
| MA-22（C3-007） | wf retry Thread.sleep 阻塞请求事务+封顶缺失（←C7-005） | MA-22 | P2 | |
| C1-006 | saveTaskState 不在条带锁内（与 saveStepState 不对称） | — | P2 | cross-ref MA-01 |
| C3-003 | 图步骤 enter∩wait 双调度无守卫 body 双跑 | — | P2 | |
| C3-004 | executor 并行步骤体无上下文重投递 | — | P2 | |
| C4-003 | 扫描 job yaml invoker.bean 失配启用即 FAILED | — | P2 | 已知未修复（2026-09 P2-3.1-001 同案）；C10-010 |
| C4-004 | executor bean 解析保留名遮蔽+无类型强转 | — | P2 | cross-ref MA-03 |
| C4-007 | wf 扫描 job 零失败韧性（形态 B） | — | P2 | 形态 A=nop-job-C7-006 跨单元单列；C10-012 |
| C5-005 | loop/fork items 全量物化+每迭代重序列化 O(N²) | — | P2 | |
| C5-006 | wf 步骤读路径全集物化+账本无归档 | — | P2 | |
| C7-004 | dueAction 阻塞挂死扫描线程级联耗尽共享池 | — | P2 | |
| C7-006 | dueTime/到期扫描全链墙钟敏感 | — | P2 | |
| C7-007 | 双引擎 DB 账本无保留/归档路径 | — | P2 | |
| C7-009 | 崩溃后 ACTIVE 滞留无看门狗 workerId 空置 | — | P2 | |
| C7-010 | SUSPENDED 滞留无自动续跑与监控区分 | — | P2 | |
| C7-014 | task 并发 resume 无执行级 fencing 双执行 | — | P2 | 与 C1-009 分立；C10-014 |
| C8-015 | nop-wf 全模块零指标注册 | — | P2 | |

### WI 1.6 nop-retry-nop-tcc（22）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-002 | OVERWRITE saveRecord 无 UK 冲突兜底裸约束暴露 | OVERWRITE 族 | P2 | C10-005 |
| C1-004 | TccTransactionRegistry instance() 身份分裂+叠放互踩（←C3-007） | — | P2 | C10-009 |
| C1-007 | 取消认领后分支仍可创建（守卫读陈旧副本）（←C7-013） | — | P2 | |
| C3-004 | 扫描批 allOf().join() 无超时无取消扫描冻结（←C6-008/C7-002/C8-002） | F5 | P2 | **条化**（P1 降级条件化：非默认形态才永久冻结） |
| C3-008 | 恢复 .get(expireGap) 超时弃链不取消幽灵链（←C6-006） | — | P2 | 条件面捆绑 CL-004 |
| C3-009 | finishCancelAsync (null,null) NPE+future 无人完成（←C6-007） | — | P2 | |
| C3-010 | confirmAllAsync 越界分支无逐分支隔离可误报成功 | — | P2 | |
| C6-005 | TCC 过期扫描/清理无生产驱动恢复未交付 | — | P2 | dormant（条件面捆绑 CL-004：补驱动即同批复核判级） |
| C6-015 | endAsync CANCELLING 落库失败短路 TRYING 残留被 CONFIRM | — | P2 | |
| C7-006 | 补偿预算耗尽静默永久排除无终态标记（←C8-005/C6-009） | — | P2 | dormant 同上 |
| C7-007 | tcc 期限/租约全链原始墙钟恢复风暴 | — | P2 | |
| C7-008 | 回调策略成环无护栏自增殖（←C8-009） | — | P2 | |
| C7-010 | tcc 过期扫描整轮线性放大 100×expireGap | — | P2 | |
| C7-011 | retry 首提租约混用本地墙钟与 DB 时钟 | — | P2 | |
| C7-014 | scanner 批内单点同步异常整批饿死 | — | P2 | |
| C7-015 | 扫描整轮排水式串行心跳无上限退化 | — | P2 | |
| C8-003 | tcc 清理端点无界批处理全量物化巨型 IN | — | P2 | CL-005 同位点主处置 |
| C8-004 | 恢复取数 beginTime DESC 最老事务饿死 | — | P2 | dormant 同 CL-004 捆绑 |
| C8-007 | 补偿全分支一次性扇出无上限不可取消 | — | P2 | dormant 同上 |
| C8-008 | 两模块过载信号零指标伞形缺口 | — | P2 | |
| C8-013 | 批量/速率参数零护栏（0 值空转/负值/无界批） | — | P2 | |
| C8-017 | 提交面失败交付三通道不对称（future-only 契约缺口） | — | P2 | 与 C8-012 修复同批 |

### WI 1.7 nop-batch（26）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-001 | BatchTaskGlobals TL provide/remove 无 save-restore | — | P2 | |
| C1-002 | BatchGenState 共享游标多 chunk 无锁驱动 | generator 共享状态族 | P2 | C10-008 |
| C1-003 | 生命周期回调 swap-on-fire 守卫误判丢弃注册 | — | P2 | C10-009 |
| C1-006 | SequentialBatchRequestGenerator 实例字段跨线程无同步 | generator 共享状态族 | P2 | C10-008 |
| C5-003 | useBatchRequestGenerator 路径 chunk 输出集无上界 | — | P2 | C10-008 |
| C5-004 | provider loader 返回 List 全量常驻无守卫 | 读侧包络族 A1 | P2 | |
| C6-003 | exp destroy 窗口不含 beforeExport/Import（OS 面空） | exp 生命周期族 | P2 | 理论；C10-004 |
| C6-004 | exp destroy=shutdown 无 awaitTermination 后台续跑 | exp 生命周期族 | P2 | C10-004 |
| C6-007 | ImportDbTool Hikari 建于 try 外失败整池泄漏 | exp 生命周期族 | P2 | C10-004 |
| C6-008 | saveState+AsyncFetch 回调挂一次性 ctx 永不触发 | — | P2 | C10-009 |
| C6-009 | onTaskComplete 经 whenComplete saveTaskState 失败被吞 | RUNNING 死区族 A2 | P2 | 修复排期紧随 P1；C10-002 |
| C6-010 | executeChunkLoop try 守卫起点过晚单改 catch 不闭合 | 终态化阻断族 M1 | P2 | 与 C6-001 同 diff；C10-001 |
| C7-004 | maxRetryCount 负值无限重试+双链 off-by-one | retry 边界族 | P2 | C10-010 |
| C7-005 | skipPolicy 缺省无限跳过+跳过不入账本 | skip 静默丢数族 A5 | P2 | C10-010 |
| C7-006 | completedIndex 断点推进仅 file/excel 生效 | — | P2 | C10-006/007 |
| C7-007 | orm-reader keyset 锚点持活实体游标漂移跳行 | — | P2 | C10-006 |
| C7-008 | jdbc-reader 缺省 offset 翻页 O(N²/2B) 退化 | — | P2 | C10-005 |
| C7-009 | jdbc 流式 loader 整任务持单条连接瞬断即败 | 连接持有族 | P2 | C10-005 |
| C7-011 | sys-event 链 saveState 未配置守卫结构性不覆盖 | A2 注记 | P2 | C10-002 |
| C7-012 | KILLED 永久吸收态+恢复无 fencing | — | P2 | C10-002 |
| C8-003 | 兜底全局 cached 池无上界+xdef 声明失真 | — | P2 | 跨单元关联平台池族；C10-010 |
| C8-004 | BatchTaskMetricsImpl 零生产接线 | 可观测性族 | P2 | C10-010 |
| C8-007 | metrics.retry 零数据源+getLoadItemCount 错源 | 可观测性族 | P2 | 与 C8-004 绑定修复 |
| C8-008 | 积压/池水位无编程出口（queue 闭包不可达） | 可观测性族 | P2 | C8-004 前置 |
| C8-009 | loadBatchMultiplyFactor/maxLockQueueCountPerThread 幽灵旋钮 | dispatch 参数族 A4 | P2 | 修复次序后置于 C8-001 |
| C8-010 | core/exp 无界 vs biz 双标；biz 钳制无下界穿透复现 C8-002 | batchSize 护栏族 A3 | P2 | C10-010 |

### WI 2.1 nop-kernel-concurrency（63）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-003 | DefaultScheduledExecutor destroy TOCTOU 六入口（←C2-006） | 退役代际族/globalTimer 族 | P2 | C10-001/N5 |
| C1-004 | debounce 四段非原子+holder 不安全发布（←C2-005/C3-020） | debounce 组 | P2 | C10-006 |
| C1-006 | ReactiveObjectMap 失败毒化三镜头（←C2-015/C7-017） | — | P2 | 死代码删除优先；C10-011/N4 |
| C1-007 | DeferredMessageRegistry 无界（deferred） | — | P2 | C10-011 |
| C1-009 | 非 volatile 静态槽 13 站点+this-escape（←C2-009） | — | P2 | C10-011/N4 |
| C1-010 | CycleSynchronizer 锁内回调子面（主处置 C2-004） | 锁设施族 | P2 | 子面去重注记 |
| C1-011 | 请求上下文锁内重活（deferred） | 锁设施族 | P2 | C10-007 |
| C1-014 | VFS destroy/refresh 共享 ZipFile 无退役代际（←C2-021） | 退役代际族 | P2 | **钉** N2 修复阻断预警 |
| C2-002 | ResourceCacheEntry 快路径无锁读×clear 锁内销毁 | ResourceCacheEntry 族 | P2 | C10-002 |
| C2-003 | bin 锁内重活纪律（锁内 HTTP/类加载族） | — | P2 | |
| C2-004 | CycleSynchronizer 锁内回调主处置（←C1-010/C7-011/C9-005 子面） | — | P2 | C10-011 |
| C2-007 | LocalResourceLockManager 桶级锁面 | 锁设施族 | P2 | C10-007 |
| C2-008 | LRLM await/signal 吞吐形态 | 锁设施族 | P2 | C10-007 |
| C2-010 | DedupBlockingQueue send 两步无回滚（←C7-006） | — | P2 | 零生产调用方；C10-011 |
| C2-016 | ContextTaskQueue.syncGet drain→await 丢唤醒窗 | **api-core 移交包** | P2 | **移交** api-core 治理 plan（plan-first）；C10-009 |
| C2-017 | LazyCompiledFunction 监视器内配置 I/O（与 C2-001 同变更集） | 死锁族 | P2 | 同变更集绑定 |
| C2-019 | CLASS_MODELS bin 锁内类加载+全量构建 | XLang 族 | P2 | |
| C3-002 | fixedDelay 手工重排链死（←C7-014(a)） | 调度器族 | P2 | C10-003 |
| C3-004 | bindCancel 单向取消不传播（RetryEngineImpl:407） | — | P2 | api-core bindCancel 协同点 |
| C3-005 | completeAfterTask catch Exception 宽度（api-core 半边移交） | — | P2 | **移交** 半边入 api-core 包 |
| C3-007 | executeWithThrottling 幻影 release（api-core 半边移交） | — | P2 | **移交** 半边 |
| C3-008 | SequentialTaskExecutor 忽略 offer 静默丢任务（五 CL 别名） | — | P2 | 零生产调用方 |
| C3-009 | retry scheduler 复用面修复同批注记 | — | P2 | |
| C3-010 | LocalCache executor 接线固化 commonPool（←C4-009/C3-019） | — | P2 | C4-009 条件化随主标注 |
| C3-011 | BlockingSourceConsumeTask 消费线程死亡三机制（←C7-012） | — | P2 | C10-014 |
| C3-012 | BlockingSource 整批丢弃面 | — | P2 | C10-014 |
| C3-013 | detach 卫生清单随批 | — | P2 | |
| C3-015 | C3-001 失效面扩展站点表（同变更集绑定不并） | 绝缘层族 | P2 | 同变更集绑定 |
| C3-016 | submit/schedule 成功路径误 cancel（bindCancel 守卫半边） | — | P2 | api-core 移交包协同点 |
| C3-018 | FutureTool 提交-取消面 | — | P2 | C10-013 |
| C4-002 | waitForTasksToCompleteOnShutdown 死配置+destroy 不等待（←C6-009/C1-002） | 死配置族/destroy 族 | P2 | C10-004/C10-010 |
| C4-003 | newExecutor/ThreadPoolConfig 工厂零校验 | 池工厂族 | P2 | 跨单元双登记（WI1.7 C4-003 处置归此）；C10-010 |
| C4-004 | nop-cached-thread-pool 按名共享无上界无配额 | — | P2 | WI1.7 C8-003 设施映射 |
| C4-005 | GlobalExecutors 生命周期所有权未定义（←C6-002/C1-015） | destroy 族 | P2 | C10-004 |
| C4-006 | updateThreadPool 收缩 IAE 缩容不可用（←C1-016） | 池工厂族 | P2 | C10-010 |
| C4-011 | destroy 路径 prestart 死旋钮 | 死配置族 | P2 | |
| C4-012 | re-init 顶替销毁借出引用（注记归 C4-005 组） | destroy 族 | P2 | 去重注记 |
| C5-001 | XLang CLASS_MODELS 无界 CHM 零清理 | XLang 族 | P2 | **条化**（运行期编译后端落地回升 P1）；C10-012 |
| C5-002 | 模型缓存容量治理面 | — | P2 | C10-012 |
| C5-003 | 租户容器不对称销毁链 | 销毁链族④ | P2 | C10-008 |
| C5-008 | XLang 缓存联合设计面（weakKeys 无效） | XLang 族 | P2 | 与 C5-001 联合 |
| C6-001 | ExecutorHelper wrapper 完成点在任务体×shutdownNow 丢排队（←C7-014(b)） | 调度器族 | P2 | C10-003 |
| C6-003 | 全局池零生命周期编排+destroy→re-init 僵尸窗 | destroy 族 | P2 | |
| C6-004 | metrics 不解绑对称主口径 | destroy 族 | P2 | |
| C6-005 | 模型缓存销毁链修复杂族④ | 销毁链族 | P2 | |
| C6-006 | 文件监关注册表不对称 | — | P2 | C7-CL-032 同批 |
| C6-007 | awaitTermination 面随 C6-009 接线批 | — | P2 | |
| C6-010 | initializer Cancellable 复用（休眠） | — | P2 | dormant |
| C6-011 | ShutdownHook CAS 反转（休眠，←C5-CL-029） | — | P2 | dormant |
| C6-012 | 销毁链租户面（与 C5-003 互列） | 销毁链族④ | P2 | |
| C7-001 | Bind fixedRate 直通委托拒绝杀链（←C3-003） | 调度器族 | P2 | **条化**（条件化）；WI1.6 C8-001 平台统一；C10-003 |
| C7-002 | Default fixedRate 补偿突发+共享 timer 挤压 | 调度器族 | P2 | C10-003 |
| C7-003 | scheduleWithRandomDelay ghost-chain 三段（←C1-005/C3-017） | 调度器族 | P2 | **条化**（条件化）；C10-011 |
| C7-004 | 墙钟敏感（部分保护） | — | P2 | C7-CL-034 随批 |
| C7-005 | 周期任务 catch Exception（N1 降级注释清理） | — | P2 | C10-011 |
| C7-008 | 销毁链清理迟滞面 | 销毁链族④ | P2 | C10-012 |
| C7-009 | LocalCache 清理迟滞本体 | — | P2 | C10-012 |
| C7-010 | throttleExecute 不可中断双登记（←C3-006） | 锁设施族 | P2 | nop-web 3 站点；C10-007 |
| C7-011 | 锁内回调子面注记 | — | P2 | 子面去重 |
| C7-015 | IRateLimiter 不可中断（与 C7-010 同批） | — | P2 | WI1.6 C3-004 映射 |
| C9-001 | ResourceCacheEntry 命中路径全依赖图 walk+stat 税 | ResourceCacheEntry 族 | P2 | C10-005 |
| C9-004 | 生成代码静态调用每次 Class.forName | XLang 族 | P2 | 与 C2-019 同批 |
| C9-010 | 直接使用方硬编码 checkChanged 绕门（6 使用方） | ResourceCacheEntry 族 | P2 | 与 C9-001/C7-007 单点修复 |

### WI 2.2 nop-core-framework（21）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-001 | 容器生命周期状态复合同步缺位（←C2-003） | — | P2 | C10-016 |
| C1-004 | subscriptionCleanup 锁外 CTA 监听泄漏 | 订阅族 | P2 | |
| C1-005 | makeContainerScope check-then-act | — | P2 | |
| C1-008 | applyChange 双 volatile 代际撕裂 | 配置刷新族 | P2 | C10-009 |
| C1-014 | VTE config 逃逸/stats null/refreshConfig 空体 | VTE 治理族 | P2 | 零测试模块结构性 |
| C1-018 | namesToPluginIds 判空摘键三步非原子门控失效 | 插件生命周期族 | P2 | |
| C1-019 | reloadPlugin remove→put 窗口双活定义 | 插件生命周期族 | P2 | |
| C2-005 | jar 轨 computeIfAbsent 映射内 HTTP+类加载长临界区 | — | P2 | CL-002 落格；C10-004 |
| C2-007 | subscribeChange 清理闭包 CTA 孤儿 list 失聪 | 配置订阅族 | P2 | C10-009 |
| C2-008 | applyChange 无锁复合操作 | 配置订阅族 | P2 | |
| C2-009 | init Error 逃逸无失败广播后续永久 wait | IoC 启动图族 | P2 | C10-002 |
| C2-013 | AOP 拦截器边不入拓扑图半成品注入 | 死锁族相邻 | P2 | C10-017 复核补充 |
| C4-001 | 文件遍历/无超时 JDBC 直连 globalTimer 占用 | globalTimer 两面 | P2 | WI2.1 C4-001 设施面映射 |
| C4-007 | 用户 refreshConfig 与监听器串行搁浅单线程执行器 | 配置刷新族 | P2 | C10-012 |
| C4-009 | restart 代际竞态旧图回调自毁新生代 | IoC 启动图族 | P2 | C10-002 |
| C4-010 | BeanScopeImpl 哈希序销毁依赖先亡（←C6-006） | 销毁链族 | P2 | C10-010 |
| C6-007 | destroy 链异常聚合缺失含 Error 穿透 | 销毁链族 | P2 | |
| C6-008 | 停机解析双重失效 | 销毁链族 | P2 | |
| C6-009 | onContainerStop 关闭不摘条目 | 销毁链族 | P2 | |
| C6-011 | IPluginManager 无聚合关闭（仓内无装配点） | 插件生命周期族 | P2 | **条化**（条件性） |
| C6-012 | GitRepository close 责任外置（理论） | — | P2 | **条化**（降 P3 留 7.1 协调方）；C10-015 |

### WI 2.3 nop-service-framework-hotpath（39）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-001 | UserContext 混合同步+dirty 发布契约缺位（←C2-004） | M1 设施工单 | P2 | **条化**（第三种装配回升 P1） |
| C1-002 | WS 会话 attributes 非线程安全容器 | — | P2 | |
| C1-003 | 订阅注册表 registerSubscription CTA 幽灵条目（←C8-004/C3-014） | 订阅注册表工单 | P2 | dormant 注记；C10-009 |
| C1-004 | 单行/小修监视器失配 | — | P2 | |
| C1-005 | WS 注册 check-then-act（CL-005 同位点归并） | 订阅注册表工单相邻 | P2 | |
| C1-006 | verifiedAt volatile 单行 | — | P2 | |
| C1-009 | ExtensibleBean 旁路面（随 C1-001 工单） | M1 相邻 | P2 | |
| C1-010 | DaoUserContextCache 条件 UPDATE+刷新单飞 | — | P2 | **移交** 修复归属 nop-auth-service 单元 |
| C2-001 | dispatchAll 监视器内同步批 I/O（←C9-007） | dispatchAll 工单 | P2 | CL-019 随单 |
| C2-003 | loadByProp 锁内只查缓存改造 | — | P2 | C10-012 |
| C2-005 | 双 map 二级索引复合不变式无锁 | 订阅注册表工单相邻 | P2 | **条化**（依赖 ws 调用方假设可降档） |
| C3-004 | 同 publisher 重试钳制缺失 | 网关流式缓冲族 G-D | P2 | C10-011 |
| C3-005 | 订阅终态机 cancel 补 context 终态 | — | P2 | |
| C3-006 | WS 错误语义五分支 | WS 设施族 G-A | P2 | |
| C3-008 | TccContextInvoker 写回+fail-fast（随 C3-007） | — | P2 | |
| C3-009 | gRPC onCancel 接线 | — | P2 | C10-017 |
| C3-010 | 取消传播面（C4-003 幽灵重试子面归此） | 网关重试链族 G-B | P2 | CL-025 平台原语前置 |
| C3-011 | Auth 会话 flush whenComplete 日志 | — | P2 | |
| C3-012 | 入口 context 统一（RPC 风格 binder） | — | P2 | |
| C3-015 | TransformFetcher thenCompose 无挂接 | 上下文传播工单相邻 | P2 | |
| C3-017 | 裸附着/条件 detach 入口 context 统一同批 | — | P2 | |
| C4-001 | defaultWorkExecutor 默认 bean/启动期预检 | — | P2 | |
| C4-002 | WS keepalive 载体治理+生命周期不对称（CL-004 并入） | WS 设施族 G-A | P2 | 阻塞子面修复单点在 C8-001 |
| C4-003 | 429 满额挂起≈90s+双层重试协调 | 网关重试链族 G-B | P2 | 幽灵重试子面归 C3-010 |
| C4-004 | executeBlocking 载体/无隔离池 | auth 认证链族 G-C | P2 | |
| C4-005 | 临时 zip 延迟任务清理绑定 CL-006/007 | — | P2 | |
| C4-008 | JWKS 定位器同型钉线（缺陷代码在单元外） | auth 认证链族 G-C | P2 | **移交** 跨模块验收（C10-014） |
| C8-003 | OpenAiDeltaAccumulator 聚合缓冲无上界 | 网关流式缓冲族 G-D | P2 | |
| C8-005 | 令牌桶墙钟回填/时钟回拨（←CL-003 深化） | — | P2 | |
| C8-006 | 零指标家族 gateway 子面 | — | P2 | |
| C8-007 | per-connection 聚合上界 | WS 设施族 G-A | P2 | |
| C8-008 | DefaultFlowControlRunner 单全局桶无 keying | — | P2 | |
| C8-009 | 会话活性检测面 | WS 设施族 G-A | P2 | |
| C9-001 | 选择树缓存化 | — | P2 | C10-018 |
| C9-002 | GatewayHttpFilter handlerCache O(N) stat（WI2.1 C9-010 实例面） | — | P2 | **移交** 修复单点 WI 2.1 C9-010 |
| C9-003 | volatile 快检+DCL 急切初始化 | — | P2 | |
| C9-004 | gRPC MAP/ANY 单次序列化共用 | — | P2 | |
| C9-005 | 逐元素往返+监视器内回调（←CL-010 深化） | 网关流式缓冲族 G-D | P2 | |
| C9-006 | INFO 全量 query 日志对齐 runRest 口径 | — | P2 | |

### WI 2.4 nop-persistence-rw（31）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-002 | session 写路径守卫族可见性 | — | P2 | CL-405 互引 |
| C1-004 | LoadedOrmModel incRef/doClose TOCTOU | 模型代发布协议切片 | P2 | |
| C1-005 | interceptors/daoListeners 可变面 | — | P2 | |
| C1-006 | 静态槽收紧切片 | 静态槽切片 | P2 | |
| C1-007 | OrmBatchLoadQueue 半 volatile | — | P2 | |
| C1-008 | reloadModel 不失效 queryPlanCache（面 c，←C2-008） | — | P2 | CL-410 相邻独立 |
| C1-012 | EqlParseTreeParser 有状态单例 | — | P2 | watch 兄弟 CL-411 |
| C1-013 | CompiledSql 可变 POJO | — | P2 | |
| C1-016 | 运行期 topo 重建写冻结模型读侧 HB 缺失 | 写冻结模型族 | P2 | 全单元唯一有钉条目 |
| C1-017 | 冻结守卫族 20 处缺口 | 写冻结模型族 | P2 | |
| C1-018 | putEntityModel 半注册 | — | P2 | 同族 C1-021 |
| C1-019 | init/发布契约守卫 | — | P2 | |
| C1-021 | anyEntityUseTenant 派生标志构造期一次求值 | — | P2 | **条化**（升 P1 条件标注保留） |
| C1-024 | 静态槽切片主案 | 静态槽切片 | P2 | |
| C1-025 | dao 事务状态机可见性面（open 守卫） | CAS 状态机切片 | P2 | 面级同 C2-016(a) |
| C1-027 | 缓存失效竞态世代号面 | — | P2 | 与 C2-004 两个 work item |
| C2-007 | 模型代交换协议缺三段式（面 3 与 C1-004 同点） | 模型代发布协议切片 | P2 | |
| C2-009 | flush 静默跳过守卫族（触发前提 CL-401） | — | P2 | C10-016 |
| C2-014 | listeners 惰性 init 丢注册（复核降级） | 监听器聚合工单 | P2 | 与 C9-001 同工单 |
| C2-016 | 事务状态机 CAS 化修复点 | CAS 状态机切片 | P2 | C10-017 现状容忍钉 |
| C2-018 | 锁内动态实体加载（=CL-200） | LazyLoadOrmModel 设施 | P2 | 与 C1-016 同批设计 |
| C3-002 | 双分支异常传播契约 | — | P2 | C10-013 |
| C3-003 | delayTasks 局部变量遮蔽 | — | P2 | |
| C3-004 | flushAsync 拓扑顺序依赖 | — | P2 | |
| C4-001 | 伪异步载体契约缺失 | 异步会话生命周期族 | P2 | C10-013 |
| C4-002 | 完成线程执行模型 | 异步会话生命周期族 | P2 | |
| C4-003 | 连接获取执行上下文（治理登记） | — | P2 | |
| C5-001 | findAll 全量物化+L1 双重驻留 | findAll 物化族 | P2 | F3⑦ 勘误受累 |
| C5-002 | cacheRef 单条目整结果集物化 | findAll 物化族 | P2 | |
| C5-005 | findAll 族⑦-⑩+limit 静默丢弃 | findAll 物化族 | P2 | C10-014 |
| C9-001 | O(n²) 事务监听器注册 | 监听器聚合工单 | P2 | 与 C2-014 同工单；C10-015 |

### WI 2.5 nop-persistence-peri（20）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-07（C7-007） | VERIFY 全量 checksum 双侧驻堆+深分页二次方 IO（←C8-002） | MA-07 | P2 | C10-017 |
| MA-08（C1-013） | 写-TTL 两步写非原子无 TTL 永久键（←C3-007/C7-011） | MA-08 | P2 | 收口 D03-02 前案；C10-028 |
| MA-09（C1-012） | computeIfAbsent GET-then-SET 违反 ICache 契约（←C3-006） | MA-09 | P2 | C10-027 |
| MA-11（C8-003） | 驱动层批写无 chunk 上界 tdengine/rpc（←C8-004） | MA-11 | P2 | C10-037/038 |
| C1-001 | RPC 传输线程变更 ORM 会话/实体状态 | — | P2 | C10-036 |
| C1-002 | MigrationEngine 三张裸 HashMap 注册表无守卫 | — | P2 | C10-020 |
| C1-008 | 编排器五个惰性组件 plain CTA | — | P2 | 与 MA-05 同文件同 diff；C10-013 |
| C1-010 | 写门瞬时检查无保持机制 | — | P2 | 门可见性半边归 WI2.2 C1-007；C10-014 |
| C3-010 | lag 门禁 ledger 观测 qs 错位（loud/silent） | — | P2 | C10-007 |
| C3-011 | FullCopyBatchJob taskKey 丢 querySpace 批身份串用 | — | P2 | C10-008 |
| C3-012 | P0 位点/订阅面/源表全集三方无对齐校验 | — | P2 | C10-009 |
| C6-005 | refreshConfig 缩容热关共享连接取用窗失败 | — | P2 | 已知未修复；**双轨**（设施主责随 WI2.1 C1-001）；C10-026 |
| C7-005 | NosqlCache 写路径不落地 expireAfterWrite 键无界 | — | P2 | 零生产实例化降档 P1→P2；C10-029 |
| C7-006 | abort 中止面不完整 FULL 不停批复制 | — | P2 | C10-016 |
| C7-008 | lag 静默窗墙钟+ledger 无 namespace 过滤 | — | P2 | C3-008 推翻其健康子断言；C10-018 |
| C7-009 | PubSub stop→start 后消息面永久死亡 | — | P2 | C10-024 |
| C7-013 | 限流令牌桶客户端墙钟跨节点共享 | — | P2 | 家族关联 WI2.3 C8-005；C10-031 |
| C7-015 | LettuceLock 无续期/fencing isHeld 本地假阳性 | — | P2 | 零生产调用方维持；C10-032 |
| C8-005 | forEachItemAsync LRANGE 0 -1 全量物化 | — | P2 | 零生产调用方落点；C10-033 |
| C8-007 | forEachEntry SCAN 无 MATCH/无总量上限 | — | P2 | C10-035 |

### WI 3.1 nop-ai-core-nop-ai-agent（29）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-07 | 周期任务 Error 逃逸绝缘层移交族（6 站点） | MA-07 | P2 | **移交** 整族 WI 2.1 C3-001 平台绝缘层；C10-008 护栏保留 |
| MA-10（C1-021） | 会话存储锁外装载+误 heal 删 delta | MA-10 | P2 | C10-004 |
| MA-12（C1-003） | 运行期换装 7 static 非 final+旧调度器不回收 | MA-12 | P2 | C10-013 |
| MA-13（C1-001） | PromptTemplateManager 非原子惰性记忆化（←C1-002） | MA-13 | P2 | C10-013/014 |
| MA-14（C1-023） | 配额检查-然后-行动突破 | MA-14 | P2 | C10-012 |
| MA-15（C3-015） | cancelSession 租户清除不恢复（←C3-026） | MA-15 | P2 | C10-019 |
| MA-16（C4-008） | 租约续期单线程调度器队头阻塞（←C7-019） | MA-16 | P2 | 不得指向 globalTimer 批次约束 |
| MA-17（C1-007） | Docker 沙箱超时不 destroy+interrupt 对管道无效（←C7-020） | MA-17 | P2 | 判级分歧 vs 3.2 移交 7.1；C10-011 |
| MA-18（C3-018） | orphan 恢复 fire-and-forget 无预算（←C7-016） | MA-18 | P2 | C10-018 |
| MA-20（C5-004） | 文件操作符窗口/限额未作用于读取面（←C7-010） | MA-20 | P2 | C10-015 |
| MA-21（C5-003） | 聊天日志逐请求双文件落盘无保留期（←C7-009） | MA-21 | P2 | |
| C1-005 | PermissionProvider 共享单例 per-session 写副作用 | — | P2 | **条化**（激活即回 P1）；dormant |
| C1-010 | cancelSession 装配窗口 TOCTOU 取消意图丢失 | — | P2 | CL-023 并入；C10-005 |
| C3-005 | 同步抛出逃逸包络契约 | — | P2 | C10-019 |
| C3-006 | 缓存读写语义不对称 | — | P2 | |
| C3-029 | 审计/记账副作用中止主流程 | — | P2 | |
| C4-004 | 重试调度器无关闭路径 | — | P2 | globalTimer 批次先后约束 |
| C4-006 | call-agent 同池嵌套（有界池注入条件下面 P1） | — | P2 | **条化**（条件升档口径留 7.1） |
| C4-009 | SingleTurnExecutor 缺省 commonPool（←CL-064） | — | P2 | 生产主路径不受影响注记 |
| C4-015 | spawn 池 stop 无 drain 丢已提交工作 | — | P2 | 与 MA-04 同批 |
| C5-002 | AI 响应缓存磁盘无界（TTL 缺省关） | — | P2 | CL-041 安全面随单 |
| C5-017 | DeferredAckMailbox deadLetter 无界永驻 | — | P2 | CL-010/037 归并 |
| C7-003 | Retry-After 无上限 | — | P2 | C10-018 |
| C7-004 | 熔断/冷却墙钟敏感 | — | P2 | |
| C7-005 | breaker probe 逃逸无身份令牌 | — | P2 | C10-018 |
| C7-006 | AiCommand 零退避重试+绕过限流 | — | P2 | |
| C7-007 | 失败记忆表无界（条件面 model 名可达） | — | P2 | C10-018 |
| C7-014 | heartbeat wakeup 无预算+永久入史 | — | P2 | 与 R4 反向钉死改写同批 |
| C7-015 | restoreSession 锁前变更共享状态 | — | P2 | |

（watch-only 候选（P2 级）：**C3-008 callStream 晚订阅竞态**——不占 29 工单，附转立案触发条件，护栏随 C10-001。）

### WI 3.2 nop-ai-toolkit-tools-shell（19）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-05（C8-002） | shell EOF 非阻塞 offer 队满静默丢失 take 永挂（←C6-005/C1-003） | MA-05 | P2 | **条化**（投产即回 P1）；**钉** 反向钉-1 ShellIOTest:294-300；C10-002 |
| MA-07（C8-006） | pathQueues 只增不删 F4+无容量/溢出策略（←C6-006） | MA-07 | P2 | 生产接线不降档；C10-003 |
| MA-08（C1-002） | shell 会话状态无同步契约无守卫（←C1-009/C1-008） | MA-08 | P2 | **条化**（降档条件解除即恢复 P1）；C10-007 |
| C1-004 | UpdateTodosExecutor static todoLists 复合竞态+F4 面 | — | P2 | CL-014/015 并入；C10-017 |
| C1-005 | DeltaVFS delta 目录非原子写+扩展面 4 写点 | — | P2 | 扩展面可达 |
| C1-006 | 两公共注册表非线程安全 Map 热路径读 | — | P2 | **条化**（零生产写方）；7.1 裁修复方向；C10-019 条件性 |
| C1-007 | ToolPathAffinity 单源读 XML 派发绕过串行化 | — | P2 | **条化** 附注；C10-023 |
| C4-004 | pipeline 阶段与全平台任务共池无隔离（←CL-002） | — | P2 | 判级差异已论证 |
| C4-006 | `&` job body join 阻塞 sink 线程同池自死锁 | — | P2 | dormant（零生产接线条件面）；C10-012 |
| C4-007 | BashExecutor 后台 run 无回滚 taskId 滞留 RUNNING | — | P2 | F2；C10-013 |
| C4-008 | 取消链覆盖面：排队 future 断链+16/17 executor 不读 cancelToken（←CL-012/017） | — | P2 | api-core bindCancel 协同点；C10-014 |
| C6-007 | ThoughtStorage 会话文件无删除路径无界增长 | — | P2 | headerless 放大面留 7.1 注记；C10-021 |
| C6-009 | BashExecutionTracker cancel remove-before-act+无守卫 | — | P2 | 兜底语义变更留 7.1；C10-022 |
| C8-004 | 消费线程中断映射「干净 EOF」输出静默截断 | — | P2 | dormant 条件面登记；C10-015 |
| C8-005 | executeParallel 信号量 permit 永久泄漏 | — | P2 | dormant（强休眠面零调用方）；C10-016 |
| C8-007 | http/graphql 响应体无上界全量物化 | — | P2 | F3 一致性缺口；C10-009 |
| C8-008 | 文件类工具输出上界可被参数绕过 7 失效面 | — | P2 | C10-010 |
| C8-010 | `>` 重定向绕过 1024 chunk 上界无界 StringBuilder | — | P2 | 与 C8-012 同批 |
| C8-012 | 输出边界仅计数有界字节无界 | — | P2 | CL-010 同源不同面 |

（MA-06 为 P1 工单（成员 C6-004/C8-001/C8-003/C8-011 均 P2 条件化、C4-003 P1），判级分歧移交 7.1，不占本表。）

### WI 3.3 nop-ai-gateway（14）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-02（C4-001） | 渠道 fan-out 池生命周期断链 DCL/复活池/REE（6 条） | MA-02 | P2 | **条化**（投产接线回升 P1） |
| MA-03（C6-004） | 会话映射「半个成功」链孤儿化（←C1-005/C2-005/C3-006） | MA-03 | P2 | **条化**（回升 P1） |
| MA-04（C4-002） | fan-out 无界队列×非协作挂死（←C7-004） | MA-04 | P2 | **条化**（回升 P1）；C8-002 独立不吸收 |
| MA-05（C3-004） | 终态回调异常护栏缺失 future 永不完成（←C7-001） | MA-05 | P2 | live 无休眠条款 |
| C1-010 | 装配守卫不对称 adapter routerFor 无 fail-fast | — | P2 | 装配漂移条件面 |
| C2-002 | StreamSubscription 监视器内投递 | — | P2 | |
| C2-009 | BufferedSubscription 监视器内重试全链 | — | P2 | |
| C3-003 | 取消误分类记账污染 | — | P2 | nop-ai-core 分类器跨模块协调 |
| C3-007 | 降级路径绕过窗口丢弃脏输出伪装成功 | — | P2 | |
| C4-004 | 连接器生命周期断链 destroy-method 缺失 | — | P2 | **条化**（回升 P1） |
| C4-005 | WS 接收线程内联 DB+引擎+同步回复 | — | P2 | **条化**（回升 P1）；2.1 CL-021 载体轮 |
| C7-002 | 流式窗口/时长指标墙钟 | — | P2 | 时钟口径批 |
| C7-005 | 选择面绕过 allowCall 容量单调流失 | — | P2 | |
| C8-002 | 超时锚定提交时刻+取消后半执行（与 MA-04 同批） | — | P2 | **条化**（回升 P1）；deadline 随 2.3 CL-022 移交轮 |

### WI 3.4 nop-ai-rest（6）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| IND-04（C1-004） | ExtensionLanguageMapper static HashMap 公开 mutator×无同步读者 | — | P2 | dormant（零调用者）；C10-004 覆盖面 |
| IND-05（C3-001） | embedAsync/embedAllAsync 无输入校验+裸 NPE 混入 stage | — | P2 | **条化**（接线即升 P1）；O-1 选型留 7.1；C10-005 |
| IND-06（C3-003） | 五站点 sync-over-async 有界长阻塞+null 取消令牌幽灵任务 | — | P2 | **条化**（O-3 裁决留 7.1）；O-2 SPI 跨模块 |
| IND-07（C6-002） | JavaMethodReplacer 非原子覆写损毁源文件 | — | P2 | **条化**（接线即升 P1）；与 IND-08 同批；C10-006 |
| IND-08（C6-004） | exists()-skip×截断写半写 .info.json 永久豁免 | — | P2 | **条化**（接管线升 P1）；C10-006 |
| IND-09（C6-003） | 双层记账注册 JarTypeSolver 无释放生命周期 | — | P2 | **条化**（零 main 消费者）；C10-007 |

### WI 4.1 nop-message（15）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-01（C3-001） | getResult→syncGet 消费等待无上界五等待点（←C8-001） | MA-01 | P2 | 判级分歧 C8-001 P1↔P2 留 7.1；WI3.1 MA-11 契约对齐 |
| MA-03（C5-001） | REGISTRY 生命周期族账本单调增长 stop 不回收（←C6-004） | MA-03 | P2 | 契约一次裁决（与 C1-002 共享） |
| MA-04（C5-003） | Local 重投单线程调度器三重职责（←C6-001/C8-003） | MA-04 | P2 | 不采 globalTimer 方案 |
| MA-06（C1-003） | destroy/stop 窗口守卫失效族 5 站点（←C1-004/005/007/C6-003） | MA-06 | P2 | |
| MA-07（C8-006） | Kafka reply 发送预算族串行+单点阻塞（←C8-009） | MA-07 | P2 | |
| MA-10（C1-001） | FAILURE_LISTENERS 注册协议无世代校验（←C5-002） | MA-10 | P2 | 与 WI1.2 C3-006 双轨互挂；判级 P1↔P2 留 7.1 |
| C1-002 | REGISTRY 摘除竞态 clearConnector 无条件 remove | — | P2 | 软钉 #5 纳入裁决面 |
| C1-012 | 配置 bean 集合字段活引用外泄（理论面） | — | P2 | **钉** 反向钉死 #3 先改测试 |
| C3-004 | Pulsar sendAsync 无同步异常包装违约 CompletionStage | — | P2 | **钉** 反向钉死 #1 改写断言 |
| C3-005 | Pulsar 批量 ack/reply 并发推进语义分叉 | — | P2 | C10-008 挂靠 |
| C3-006 | Local 红投 suspended 即永久丢弃延迟消息 | — | P2 | 与 MA-04 同文件批次 |
| C3-009 | 全模块 7 处异常屏障 Exception-only Error 逃逸 | — | P2 | **移交** 修复归属 WI 2.1 绝缘层批次；升格可能留 7.1 |
| C6-006 | Debezium 以 action 身份作订阅键 cancel 旧停新 | — | P2 | 与 MA-05 同 map 改造 |
| C8-005 | Kafka ConsumeLater 无预算闭环 delay≤0 热旋 | — | P2 | 三后端同批 |
| C8-007 | Pulsar getProducer computeIfAbsent 锁内建连 | — | P2 | 与 C1-004 异机制互不遮蔽 |

（C8-002 为 P1 独立工单，MA-02/MA-05/MA-08/MA-09 中 P1 判级者不占本表。）

### WI 4.2 nop-cluster（13）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-02（C1-002） | cleanupTasks 写读锁不对称+事件线程内联执行 | MA-02 | P2 | **条化**（零生产调用方接线回升 P1）；C10-006 |
| MA-03（C3-005） | Nacos 订阅泄漏+instanceCache 无淘汰（←C8-005） | MA-03 | P2 | C10-005 |
| MA-04（C3-002） | BroadcastRpcClient 半个成功+扇出无上界+幽灵腿（←C8-004/C8-003/C3-012） | MA-04 | P2 | **条化**（零接线回升 P1）；C10-002 |
| MA-05（C3-007） | Sentinel runAsync entry 泄漏+同步逃逸（←C3-008/C8-008） | MA-05 | P2 | **钉** 反向钉死 #1 同 diff 改写；C10-009 |
| MA-08（C1-006） | 零权重守护缺失+Assigner 改写 0→100 排流失效（←C8-009） | MA-08 | P2 | **钉** 反向钉死 #3 同 diff 改写；owner 裁决 |
| MA-09（C3-003） | 无主返回 null 违约裸 NPE 未类型化（←C8-010） | MA-09 | P2 | |
| C1-005 | destroy 与懒初始化复活竞态 3 站点泄漏 | — | P2 | shutdown 懒重建族关联 WI2.2 C1-009；CL-006 随单 |
| C2-002 | computeIfAbsent 映射内两次远程调用热路径 bin 锁 | — | P2 | C10-005 |
| C4-003 | 裸 globalTimer 交给 PollingRpcClient 无整体截止 | — | P2 | globalTimer 族平台统一裁决；C10-013 |
| C4-004 | 两条自续调度链单点断链无监督 | — | P2 | 自续链断链族关联 WI2.1 C3-001；CL-011/014 随单 |
| C8-001 | LeastActive 活跃数恒 0 过载感知路由失效 | — | P2 | 结构性假保护（adapter 恒 0）；owner 裁决 |
| C8-006 | PartitionResolver 失败无负缓存依赖故障退化远端查询 | — | P2 | C10-008 |
| C8-007 | SentinelRuleConfig 热更新单向棘轮旧规则滞留 | — | P2 | C10-010 |

（C1-008 为 P3 条件化独立工单，不占本表；watch C2-004 为 P3。）

### WI 4.3 nop-network-integration（32）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-08（C3-008） | oauth enhancer 锁内 token HTTP 往返+三入口绕过注入（←C1-002/C6-008） | MA-08 | P2 | 工单主键 C3-008；C10-009 |
| MA-09（C1-012） | MQTT dispatcher 注册配对破损重复 add/摘除死码（←C6-026/C3-020） | MA-09 | P2 | |
| MA-10（C1-013） | NettyTcpClient stop×reconnect 竞态 channel 复活（←C6-022） | MA-10 | P2 | C10-011 |
| MA-11（C1-015） | feishu bind ticket 双次 HTTP 后 remove 非原子（←C3-031） | MA-11 | P2 | C10-013 |
| MA-12（C3-006） | ApiContextInterceptor 同步抛 binder 永不 close（←C6-009） | MA-12 | P2 | C10-012 |
| MA-13（C3-010） | RpcTaskMonitor 跨轮竞态 checkFailCount 丢更新（←C1-004） | MA-13 | P2 | CL-025 修复先决关系维持 |
| MA-14（C6-023） | VertxMqttServer doStart 不观察 listenFuture（←C3-019） | MA-14 | P2 | 与 C6-028 同提交收敛 |
| MA-15（C3-011） | CompletionStage 同步抛逃逸族 5+ 站点（←C3-021/C3-029） | MA-15 | P2 | **钉** RP-1 断言级反向钉死 2 处先改测试；vertx-mqtt 面**条化**；C10-014/006 |
| MA-16（C6-007） | stop 不关底层 HttpClient selector 线程累积（←C6-025） | MA-16 | P2 | C10-004 |
| C1-003 | RpcChannelState prepareSend 容量守卫 TOCTOU | — | P2 | C10-012（A1 补登） |
| C1-005 | SimpleRpcClient 回调槽位双写者覆盖 | — | P2 | rpc-simple 批次 |
| C1-010 | FeishuClient.stop 清空与 WS 回调线程无 HB | — | P2 | feishu 批；CL-021 载体注记 |
| C1-011 | stop→start 后 scheduleAtFixedRate 拒绝半启动死态 | — | P2 | feishu 批；CL-021 载体行为面 |
| C1-014 | NettySslEngineFactory 缓存 CTA+非 volatile 发布 | — | P2 | CL-027 闭环 |
| C1-016 | SocketClient 单读线程契约无守卫帧流交织 | — | P2 | socket 批次 |
| C1-018 | acceptEndpoint 回调挂载时序倒置 close 回调丢失 | — | P2 | CL-010/018 协同 |
| C1-019 | SocketServer 重启 stopped 复位×旧 accept 退出竞态泄漏线程 | — | P2 | 先决=测试注入缝 |
| C3-007 | binder.close 在异步完成线程执行 ThreadLocal 串号 | — | P2 | CL-011 平台根因协同 |
| C3-009 | SimpleRpcClient 发送失败完成派生 future 条目滞留 | — | P2 | 对齐 MessageRpcClient 先例 |
| C3-012 | cancelToken 监听器只增不删 10 站点 | — | P2 | api-core bindCancel 协同；C10-010 |
| C3-022 | MqttConnection 跨线程触发 nextMessageId 非原子碰撞 | — | P2 | vertx-mqtt 面**条化** |
| C3-023 | PacketCodecHandler 吞编解码异常静默丢帧（←CL-008） | — | P2 | netty 批次 |
| C3-025 | TencentEmailSender 吞全部发送异常正常返回 | — | P2 | CL-009/029 三登记一处置；C10-013 |
| C3-026 | SocketClient.recv 重连耗尽静默死亡 | — | P2 | CL-028 并入；C10-011 |
| C3-033 | SSE 订阅终态后无终止隔离迟到事件 | — | P2 | 与 C6-004 本体区分；C10-003 |
| C6-003 | JDK/OkHttp 416 整体重传携带陈旧 Range 恢复必败 | — | P2 | 已知未修复（2026-09 3.9 前案并轨 cross-ref） |
| C6-010 | SimpleRpcClientFactory 通道无回收路径 | — | P2 | rpc-simple 批次；C10-008 |
| C6-011 | RpcTaskMonitor.activeTasks 条件性只增不删账本 | — | P2 | 零生产消费方；C10-012 |
| C6-012 | PollingRpcClient 轮询无总时长/次数上限 | — | P2 | C10-012 |
| C6-013 | file+resume=false 直写最终路径失败残留截断文件 | — | P2 | 下载链批（C6-001/C3-005 同批）；C10-001 |
| C6-027 | SocketClient onChannelOpen 回调异常 socket 锁死 | — | P2 | socket 批次 |
| C6-028 | VertxMqttServer doStop 不等待+sessionManager 不清空 | — | P2 | 与 MA-14 同提交；前案 P2-15 相邻面同批 |

（P1 判级族/独立工单 MA-01~07、C3-001/C3-005/C3-024/C6-004/C6-005/C6-017/C6-018/C6-019 不占本表；watch-only C1-020 P3。）

### WI 4.4 nop-host（10）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-03（C6-008） | servlet 流式错误 complete() 洗白截断响应 | MA-03 | P2 | 错误交付族传输层成员；家族判级对齐留 7.1；C10-018 |
| MA-04（C6-003） | Spring onClose 无归属守卫拆在役上下文（←C6-002） | MA-04 | P2 | 双宿主镜像对；C10-012/013 |
| MA-05（C1-002） | g_serverContext runOnContext 不重设静默降级 | MA-05 | P2 | 家族修复归 2.3 轨道；C10-002 |
| C1-003 | 两宿主 WS basicRemote 并发写丢帧 | — | P2 | 2.3 C8-001 同组件异机制注记 |
| C1-004 | 懒初始化 check-then-put 族（dialect+MCP callbacks） | — | P2 | 惰性 pin 良性约束 |
| C4-001 | 过滤链 syncGet 阻塞 servlet 工作线程至流结束 | — | P2 | 4.3 MA-03 家族判级议程并档（7.1-2） |
| C4-002 | WS keepalive 构造即调度共享 globalTimer 滞留窗（←CL-007） | — | P2 | **钉** RP-2/RP-4 先于修复；C10-008 |
| C4-004 | spring-delta 注册回调平台初始化前静默空发现 | — | P2 | **钉** RP-3 须新增不预初始化上下文 |
| C6-004 | servlet startAsync 无 setTimeout 无 AsyncListener（←CL-005b） | — | P2 | 搭车 C1-005 volatile 同 diff |
| C6-005 | 数据源重建后 17 个 Gauge 绑旧池可观测失效 | — | P2 | 2.5 双轨同名异站 |

（watch-only：**C1-005 P2 理论风险**——不占 10 工单，volatile 修复搭车 C6-004；RP-1 反向钉死挂 CL-008 线索。P1 判级 C1-001/C1-006/C6-001/C6-007 不占本表。）

### WI 4.5 nop-search-runner-autotest（6）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-01 | Lucene 三 CHM 生命周期无协调 4 成员（C1-001/002+C6-002/003） | MA-01 | P2 | 修复不得拆分（S-04 正向约束）；CL-010 随案 |
| MA-02 | watch 注册返回 ICancellable 丢弃注销通道（C4-007+C6-007） | MA-02 | P2 | 修复陷阱：禁 watcher.stop() 统一（CL-011 footgun）；C10-004 |
| MA-03 | watch-zip 产物写-写/写-读并发损坏（C1-004+C6-008） | MA-03 | P2 | C10-005 |
| MA-04 | MockScheduledExecutor 替身契约系统性缺失 4 成员（C1-003/C4-003/C4-006/C6-004） | MA-04 | P2 | 测试基建本体 |
| I-C4-002 | watch 脚本异常静默不可见三类处理不对称 | — | P2 | C10-004 异常腿 |
| I-C6-001 | watch-zip 双重 init 覆盖泄漏非守护线程挂进程 | — | P2 | **条化**（CDI-only 回升/降格条款 7.1 确认） |

（watch C4-005 P3 随 MA-01 治理观察。）

### WI 5.1 nop-auth-credential（5）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-03（C2-001） | JWKS 刷新链锁内同步 HTTP+失败推进节流锚（←C2-002） | MA-03 | P2 | 与 I-01 同 PR 批 |
| MA-04（C2-004） | 可信设备配额/豁免/撤销全链无串行化点（←C2-005/C1-005） | MA-04 | P2 | CL-017 watch 搭车 |
| I-01（C6-004） | OAuth/JWKS 外呼无调用点请求超时 | — | P2 | JWK 站随 MA-03 同批 |
| I-02（C6-005） | OAuth 刷新半个成功丢轮换 token 凭证永久失效 | — | P2 | **钉** C10-007 反向钉死须先改 refreshFailureFailsClosed |
| I-06（CL-004=CL-014） | SSO doLogout IdP 反向登出逃逸 waitAll 单点登出半成功 | — | P2 | 契约对照留 7.1 |

（MA-05/MA-06/MA-07/MA-08/I-03/I-04/I-05 为 P3；MA-05 携 CL-016 条件化回升条款。）

### WI 5.2 nop-sys-dyn（8）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| MA-02（C2-003） | 动态模型生成锁=全局限速器临界区含 DB I/O+用户钩子（←C2-006/C2-101） | MA-02 | P2 | **钉** 反向钉死 TestInMemoryCodeCacheContract 修饰符断言同 diff 改写 |
| MA-03（C6-002） | 租户缓存 evict/init/destroy 三转变点无对称治理（←C6-102/C1-101） | MA-03 | P2 | 冲突裁定推翻复核建议归族 |
| MA-04（C6-001） | sys_lock/sys_change_log 零保留 F4+逐列写放大（←C6-003） | MA-04 | P2 | 留存语义留 7.1 |
| MA-05（C2-005） | 序列回退路径共享锁 item+锁内 DB 往返（←C1-007） | MA-05 | P2 | |
| MA-06（C1-003） | 懒初始化守卫缺失+双 volatile 快照发布（←C1-005；C1-004 watch 搭车） | MA-06 | P2 | |
| I-03（C1-008） | generateByAI moduleName TOCTOU 并发双建重复模块 | — | P2 | **唯一索引方案 plan-first**；选型留 7.1 |
| I-04（C6-101） | 动态模块/实体删除后零缓存失效已删模型继续服务 | — | P2 | 照抄函数级先例 |
| I-05（C8-001） | invokeConsumer syncGet 无超时消费静默停摆 F5 | — | P2 | 与 MA-01 双向放大批次协同 |

（P1：MA-01/I-01/I-02；watch 2 条 P3。C2-002（MA-01 成员）携激活回升条款。）

### WI 5.3 nop-file-datav（13）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| CL-001 | cancelFlags 取消-完成竞态 | W-02（MA-01） | P2 | 假保护矩阵行；C10-001 |
| CL-002 | 发布版本号竞态 | W-03（MA-02） | P2 | |
| CL-003 | 终态无 fencing face(b) FAILED→RUNNING 复活 | W-02（MA-01） | P2 | **钉** 假保护（反向钉死）修复同 diff 改写 testSlowExecutionRaceWorkerSuccessOverwritesScanFailed；跨单元 fencing 族 |
| CL-004 | 告警状态行并发评估 | W-03（MA-02） | P2 | |
| CL-005 | 限额 TOCTOU | W-03（MA-02） | P2 | 假保护（顺序测试伪装竞态保护） |
| CL-007 | Semaphore 占池跨工作负载影响 | W-07 附带评估 | P2 | **watch 候选**（依赖负载画像先量化后立项）；跨单元关联 1.2 C4-004 |
| CL-010 | 会话历史全量加载 | W-04（MA-03） | P2 | 同族 1.5 C5-001 |
| CL-015 | 导出临时文件泄漏 | W-05（MA-04） | P2 | |
| CL-016 | 孤儿导出文件状态测试掩盖 | W-02（MA-01） | P2 | 假保护；C10-001 配套 |
| CL-019 | 调度器注销回滚窗口 | W-07（MA-06） | P2 | |
| CL-020 | 注册失败停摆无自愈 | W-07（MA-06） | P2 | 平台级修复归 2.1 C3-001，本单仅接入点登记 |
| CL-021 | 快照历史全量物化 | W-04（MA-03） | P2 | 同族 1.5 C5-001 |
| CL-023 | SMTP 半程窗口 | W-07（MA-06） | P2 | 弱同族 1.6 C3-005 |

### WI 5.4 nop-report-format（9）

| ID | 一句话 | 族 | 级 | 标记 |
|---|---|---|---|---|
| C1-001 | DocumentConverterManager 内层裸 HashMap/TreeSet | G1 注册表族 | P2 | 条件性 P2（启动期注册+公开再注册 API） |
| C1-002 | FontManager addFontAlias 裸写数据竞争 | G4 FontManager 站点 | P2 | 生产零调用方条件性；**单设计联合闭合**（与 C9-002 修复方向相反） |
| C1-003 | ExcelFormatHelper public addFormat 无运行时守卫 | G5 站点 | P2 | **单设计联合闭合**（与 C9-001） |
| C1-005 | RandomAccessFileBinaryDataReader detach 契约违背静默错位 | G2 detach 族主 | P2 | |
| C5-004 | Excel 流式接口全表物化批量导入主路径可达 | G6 | P2 | **跨单元双轨**（=WI1.7 C5-001 同缺陷第二实例，独立登记修复联动） |
| C5-R2-001 | rawVarName peek+hex 4-5× 放大驻留至请求结束 | G3 同 diff | P2 | 与 C5-001 同 diff |
| C6-001 | 构造异常路径 fd 泄漏生产可达 | 构造泄漏族第 5 例 | P2 | 家族先例 4.3 C6-018 等 |
| C9-001 | 每单元格 5-7 次 replaceAll+格式器构造 | G5 站点 | P2 | 与 C1-003 联合闭合 |
| C9-002 | 首次 CJK 回退 JVM 单例锁内秒级字体 I/O 阻塞并发导出 | G4 站点 | P2 | 锁内 I/O 族第 4 例；校准注记防误升格 |

### WI 5.5 nop-rule-metadata（9 工单级 / 13 实体级）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| W-04（C1-102 主） | ensure* 惰性初始化 11 字段×8 载体（←C1-001/C4-001） | W-04 | P2 | 懒初始化守卫族新成员 |
| W-05（C1-002） | 构造器无条件写 JVM 全局 DriverManager.loginTimeout | W-05 | P2 | **钉** 反向钉定 TestMetaDataSourceConnectionSecurity:776-792 同 diff 改写 |
| W-06（C1-003） | RuleRuntime 契约不对称并发写时序不可具体化 | W-06 | P2 | 理论级；CL-202 注记 |
| W-07（C7-003 主） | 逐规则建连零复用+建连风暴（←C4-R2-002） | W-07 | P2 | G1 同链批次协同 |
| W-08（C7-006 主） | 对账全池重载 R×P×O(L²)（←C5-104） | W-08 | P2 | CL-401 随迁 |
| W-09（C5-001） | 外部 catalog 无控输入无 setLimit 闸 | W-09 | P2 | |
| W-10（C5-101） | 公开 @BizQuery 正值 limit 不封顶「截断层」不存在 | W-10 | P2 | 契约漂移 |
| W-11（C7-002） | Error→job 永久 FAILED 无复活路径 | W-11 | P2 | WI2.1 C3-001 具体化站点，模块侧 catch Throwable 可先行 |
| W-12（C7-R3-001） | wf.start save-then-finish 半程残留（降级终态） | W-12 | P2 | F2 半个成功族新成员 |

（实体级 P2×13：上述 9 工单 + 吸收成员 C1-001/C4-001（W-04 内）、C4-R2-001（W-01 内，P1/P2 分歧登记 7.1）、C7-101（W-02 内）、C5-104（W-08 内）。P3 带 C5-103/C5-105 激活回升条款；W-26 watch。）

### WI 5.6 nop-graph-duckdb（4）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| W-05（C6-003 主） | xlsx→CSV 失败路径临时文件不删 deleteOnExit 族（←CS-003） | W-05 | P2 | C10-P2-2 |
| W-06（C1-003） | Edge.attrs 可见性缺口 3 读点 | W-06 | P2 | **Protected Area plan-first**（唯一触碰 nop-graph-api 叶工单） |
| W-07（CS-004 主） | 邻接分配放大每入边整表拷贝×d̄（←CS-R2-003） | W-07 | P2 | 双路径同测；CL-204 根因附注 |
| W-08（CS-R2-002） | Betweenness 无超时中断同步占住请求线程 | W-08 | P2 | 与 W-04 族 cross-ref 不并单 |

### WI 6.1 nop-tools-parallel（4）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| W-01（C1-001 主） | SearchCommand patternBytes 惰性可变字段双写+未安全发布（←C4-003/CL-301） | W-01 | P2 | 先红后绿（P2-01）；与 W-05 聚合层联动 |
| W-03（C1-R2-001） | FFM 映射存活期×外部截断 TOCTOU 双机实测复现 | W-03 | P2 | 先红后绿（P2-02）；与 W-05 修法联动 |
| W-05（C4-002） | invokeAll 首错弃全+cause 路径不可见 | W-05 | P2 | 先红后绿（P2-03）；聚合弃全族对偶成员 |
| W-14（C9-002） | read-back 逐 artifact 重开 JarFile 30k-entry ≈30s（←CL-202 闭环） | W-14 | P2 | 对照 collectJar 正确形态；BG-5 |

### WI 6.2 nop-tools-parse（7）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| W-03（C1-105） | RuleResultCache 固定名 .tmp 整件 load-modify-save 跨进程无互斥 | W-03 | P2 | 姊妹案 C6-102 面拆分；与 W-10 同点落地 |
| W-04（C1-104） | LanguageRegistry 裸 HashMap 违反 fails-closed 契约 | W-04 | P2 | **承接 2026-09 G10-01-02**（超集立案）；CL-501 归并 |
| W-05（C1-001） | TSParser provider 缓存 get→load→put 复合窗口 | W-05 | P2 | 修复一行 computeIfAbsent |
| W-06（C1-103） | TscTypeResolver.bridge 非 volatile 探针失真（在编未接线） | W-06 | P2 | dormant；CL-502 归并 |
| W-07（C6-001） | EditPlanApplier 写盘先于守卫+回滚二写 | W-07 | P2 | 修复 owner=nop-lint；F6 写盘序族「校验后置」新形态 |
| W-08（C6-002） | RefactorOperationRunner「lands atomically」契约三点矛盾 | W-08 | P2 | **钉** 反向保护 #8 同 diff 改写；(a)/(b) 收敛留工单 |
| W-09（CS-R2-001） | error 帧无 correlation 校验+BAD_FRAME 不回收+协议无版本 | W-09 | P2 | 与 C6-101 生产侧互补守卫独立立案 |

（P1：W-01（C6-101 主，吸 C1-102/CS-001 转引用）、W-02（C1-101，**钉** 反向保护 #2 TestLintDeadlineExecutor:84-85；Route-B 触 nop-core plan-first 可选项）。反向保护另 #9/#11 落 P3 工单。）

### WI 6.3 nop-code-index（16）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| W-04（C6-001 主） | git 读阶段 4/4 站点无超时形同虚设（←C7-003/CL-101） | W-04 | P2 | 外部进程桥第 6 例；TP-004 |
| W-05（C6-002） | 半克隆毒化链 rev-parse 永久失败零清理 | W-05 | P2 | dormant 激活计级；TP-005 |
| W-06（C6-003 主） | deleteIndex 11 实体清单缺 ledger+null 守卫击穿（←C7-006(c)） | W-06 | P2 | 与 W-20/W-35 并列挂接接线前置工单 |
| W-07（C6-004） | findSymbolIdsByFileId setLimit 无 offset 循环 3 调用点放大 | W-07 | P2 | 同类标准例外 |
| W-08（C7-005 主） | 搜索双写 removeDocs 先于 DB 删除双向吞异常（←C6-005） | W-08 | P2 | TP 相应 |
| W-09（C7-004） | deleteFileRecords 仅清 source 侧 2-hop 放大 | W-09 | P2 | source 侧 scoping 已有保护补 target 断言 |
| W-10（C7-002） | 每 (repo,revision=SHA) 全量 clone 零 retention | W-10 | P2 | dormant 激活计级 |
| W-11（C1-001） | SymbolTable synchronized add×裸读 36 站点（吸 G11-15-02） | W-11 | P2 | **承接 2026-09 G11-15-02**（超集/扩面复活防双计）；TP-004 |
| W-12（C1-002） | CodeCacheManager 6 失效点锁外先行/提交前撕裂 | W-12 | P2 | TP-005 |
| W-13（C5-003） | 双提取器输出侧无上限+保存循环无 flush | W-13 | P2 | C5 族 |
| W-14（C5-004） | rebuildDependencies 常量组无上限无截断 | W-14 | P2 | CL-202=CL-303 归并 |
| W-15（C5-005） | replaceRows 删除侧 flush 插入侧无不对称 | W-15 | P2 | 与 W-01 同栈叠加 |
| W-16（C5-006） | mergedTable 每变更文件全量复制持 indexLock 巨事务 | W-16 | P2 | |
| W-17（C5-R2-001） | findByMetric 无 LIMIT+三装配路径全量符号物化 | W-17 | P2 | 与 W-01 失效层分立 |
| W-18（C5-R2-002） | symQuery 无条件无 LIMIT 17 查询唯一 | W-18 | P2 | |
| W-19（C5-R2-003） | ≤10000 实体×sourceCode CLOB 无 lazy | W-19 | P2 | 惯例偏离 |

（P1：W-01（C5-001，TP-001 前置）、W-02（C5-002，TP-002 前置+部署包络边界注）、W-03（C7-001，TP-003 前置）。W-20 带「接线后升 P1」条款（P3）。）

### WI 6.4 nop-code-rest（0）

零立案单元（campaign 首例）：P0/P1/P2/P3 全零、工单 0。无 P2 实体。CL 5 条域留档/watch（CL-101 watch 附升 P1 条款）。

### WI 6.5 nop-peripheral（4 工单级 / 5 实体级）

| ID | 一句话 | 族（工单） | 级 | 标记 |
|---|---|---|---|---|
| W-03（C1-004 主） | ProjectFileHelper 静态 Map 以 Project 为键无移除（←C6-003 别名） | W-03 | P2 | IDE 插件项目泄漏族首例；**验证通道=Gradle/harness**（CL-301）；C10-006 |
| W-04（C1-001） | XLangDebugger 挂起交接非原子幽灵/吞挂起 | W-04 | P2 | C10-003（假保护候选经复核证伪修正为无测试） |
| W-05（C1-003） | NopProjectService 项目级生命周期驱动全局注册表交叉销毁 | W-05 | P2 | 伴生 CL-204；验证通道约束；C10-005 |
| W-06（C6-002） | suspendedThreads 只进不出一次性 30s 调度死线程永驻（←CL-003） | W-06 | P2 | F4 清理族；C10-004 |

（实体级 P2×5 = 上 4 工单 + 吸收别名 C6-003。P1×2：W-01（C1-002 JavaScriptWorker 生命周期族，**C10-001 验收前置**，future 族第 6 例）、W-02（C6-001 IDEA 调试 socket 泄漏，**C10-002 验收前置**，验证通道=Gradle/harness[CL-301]）。demo 降口径 P3×2：W-09/W-11。CL-203 移交 WI 2.1 C4-003。）

---

## §二 C10 缺保护 → 缺陷本体映射表（按单元，合计 505）

> 格式：`C10 条目 → 本体工单（本体判级）`。族归组式单元（1.4/1.6/1.7/2.1/2.5/3.1/3.2/4.3）按 C10 文件/MA 映射节的权威对应转录；逐条式单元按本体↔C10 一一对应转录。

**1.1（25）**：C10-001→C1-001(P1)；002→C4-001(P1)；003→C4-002(P1)；004→C4-009(P1)；005→FAM-1(C5-001/C8-001,P1，含假保护 TestResultPartitionOverflowBypass)；006→C5-010(P1)；007→C8-003(P1)；008→C8-011(P1)；009→C7-011(P1)；010→C1-004(P2)；011→C1-005(P2)；012→C2-003(P2)；013→C2-005(P2)；014→C3-009(P2)；015→C4-006(P2)；016→C4-012(P2)；017→C5-004(P2)；018→C5-006(P2)；019→C8-009(P2)；020→C1-006(P1)；021→C5-005(P2)；022→FAM-2(P2)；023→C7-007(P2，关联 C1-009 P3)；024→C9-001(P2)；025→C9-004(P2)。

**1.2（16）**：C10-001→MA-01(P0，先红后绿)；002→MA-02(P1)；003→MA-03(P1，条件会红：仅合并修复经 :43 反转)；004→MA-03(P1)；005→跨单元 C2-003（挂 WI1.1-C1-001）；006→跨单元 C2-010（挂 WI1.1-C3-009）；007→MA-05(P1)；008→MA-04(P1)；009→C3-004(P1)；010→MA-06(P1)；011→MA-07(P1，同盖族内三面)；012→MA-08(P1)；013→C8-001(P1)；014→C8-006(P1)；015→C3-011(P2)；016→测试自身缺陷（独立，非缺陷族，无本体工单）。

**1.3（8）**：C10-001→MA-01(P1)；002→C3-008(P1)；003→C3-009(P1，跨单元回归锚挂 core 工单)；004→C6-007(P1)；005→MA-03(P2)；006→C1-003(P2，全矩阵唯一 trip-wire 反向约束)；007→解耦登记（规模/边界包络缺口，无本体工单，建议随 C3-008/C6-007 批）；008→测试基建缺口（关联 C3-007/MA-04/C6-005，无单一本体）。

**1.4（9）**：C10-001→MA-01(P1)；002→MA-06(P2，**钉死约束**：checker 侧被 plan340 契约测试+注释双向钉死)；003→C2-001(P1)；004→C2-007(P2)；005→C4-003(P2)；006→C7-011(P2)；007→C2-004+MA-03（同分支两族一套并发注册测试）；008→C5-002(P2)；009→无宿主缺陷族（时序敏感测试本体登记，随批次 2 加固不独立立项）。

**1.5（14）**：C10-001→MA-01(P1，**假保护**串行化测试)；002→MA-11(P2)；003→MA-02(C3-001 面，顺序假保护)；004→MA-02(C3-011 面)；005→MA-02(C3-012 面)；006→MA-03(P1)；007→MA-04(P1，fake 复刻缺陷拓扑)；008→MA-05(P1)；009→MA-08(P1，**反向钉死** globalGates_strongRefRegistry 修复前必须先改写)；010→C4-003(P2)；011→MA-23(P1)；012→C4-007(P2，部分假保护)；013→MA-13(P2)；014→C7-014(P2)。覆盖结论：7 个 P1 族全部有登记；MA-08 修复前必须先改反向钉死测试。

**1.6（10）**：C10-001→C8-012(P1)/C3-006(P1)/C1-003(已解体)；002→C3-004(P2)/C8-001(P1)/C7-015(P2)/C7-014(P2)/C6-013(P3)；003→C1-001(P1)/C3-002(P1)/C3-010(P2)/C3-012(P1)/C6-015(P2)；004→C3-008(P2)/C8-007(P2)/C7-006(P2)/C7-010(P2)/C8-004(P2)；005→C6-003(P1)/C1-002(P2)/C6-002(P1)/C3-005(P1)；006→C3-003(P1)/C3-013(P3)/C3-015(P3)/C7-011(P2)/C7-012(P1)；007→C7-001(P1)/C7-003(P1)/C7-005(P1)/C6-005(P2)/C8-003(P2)/C8-004(P2)；008→C7-009(P3)/C8-016(P3)/C8-008(P2)/C8-010(P3)/C8-013(P2)/C8-015(P3)/C8-017(P2)/C6-012(P3 部分)；009→C1-004(P2)/C1-005(P3)/C1-006(P3)/C3-009(P2)/C3-011(P3)/C3-014(P3)/C6-012(P3)；010→复核补充登记（见 C10 文件 R3 节）。

**1.7（12）**：C10-001→C6-001/C6-002/C6-010（终态化族 M1，含被并 C7-002+CL-024 断言）；002→C7-001(P1)/C6-009/C7-011/C7-012（RUNNING 死区族）；003→C8-001(P1，←C5-005)/C8-009；004→C6-003/C6-004/C6-007/C8-002(P1)；005→C6-005(P3)/C6-011(P3)/C7-008/C7-009；006→C7-006/C7-007/C7-014(P3)；007→C7-003(P1)/C7-015(P3)/C7-016(P3)；008→C1-002/C1-006/C5-003；009→C1-003/C1-007(P3)/C6-008；010→C7-004/C7-005/C7-013(P3)/C8-003/C8-004/C8-007/C8-008/C8-010/C5-009(P3)；011→C5-001(P1，**必补回归钉**)+C5-006(P3 顺带)；012→C8-005(P3)/C8-006(P3)。套件注记：N1→C7-001、N2→C5-002、N3→C8-001 修复 diff 必携同步评估。

**2.1（14，家族归组）**：C10-001+N5→退役代际族（C1-001 主/C1-003/C1-014←C2-021/C4-005 组）；002→ResourceCacheEntry 族死锁面（C2-001/C2-017）；003→调度器族（C3-001(P1)/C3-002/C7-001/C7-002/C7-003/C6-001/C3-016）；004→退役代际族（C4-005 组）+C4-002；005+N3→ResourceCacheEntry 族失效链（C7-007(P1)/C9-001/C9-010，N3 @Disabled 恢复）；006→debounce 组（C1-004）；007→锁设施族（C2-007/C2-008/C7-010 组/C3-007/C1-011）；008→模型缓存销毁链（C6-005/C6-012/C6-013/C5-003/C7-008）；009→api-core（C2-016 移交包）；010→池工厂族（C4-003/C4-006 组/C4-011/C4-012/C5-007）+C4-002；011+N1→零调用潜伏族（C1-006/C1-007/C1-009/C1-010/C2-004/C2-010/C7-003/C7-005/C7-011/C7-016 等）；012→缓存容量治理（C5-001/C5-002/C5-008/C7-008/C7-009/C9-003）；013→提交-取消面（C3-008/C3-018/C2-022）；014→BlockingSource 组（C3-011/C3-012/C3-014）。

**2.2（18）**：C10-001→C1-009(P1)/C1-021(P1)/C6-005/C4-002(并)/C6-003(jdbc 面)；002→C4-003(P1)/C4-008/C4-009/C4-011/C2-009/C2-001 并发面；003→C2-001(P1)/C2-002(P1)/C1-002(并)/C2-017；004→C1-010(P1 族)/C1-018/C1-019/C2-004(并)/C2-005/C2-006/C6-011；005→C6-002(P1)/C2-010(P3)；006→C6-001(P1)；007→C6-004(P1)/C6-014/C4-005(P3)；008→C6-003(P1)/C4-001；009→C1-007(P1 族)/C1-008/C2-007/C2-008/C2-016(P3)/C1-020(P3)；010→C4-010(P2)/C6-006(并)/C6-007/C6-008/C6-009；011→C1-014/C4-004(P3)/C6-010/C1-015(P3)；012→C4-001/C4-006(P3)/C4-007/C1-004；013→C1-016(P3)；014→C6-013(P3)；015→C6-012(P2 理论)；016→C2-003(并)/C1-001/C1-005；017（复核补充）→IoC 拓扑不可见边族（C2-013 等）；018（复核补充）→C1-006(P3 良性)。矩阵总账：有钉 0/部分 20/无保护 38；11 条 P1 中 0 有钉。

**2.3（17，C10-016 不存在）**：C10-001→C3-015+C3-002 出口泵/递归泵面（C3-002(P1)）；002→C3-003(P1)/C3-004/C8-003（G-D 回归网）；003→C3-007(P1)/C3-008/C3-012/C3-017/C3-015（上下文传播面）；004→C4-007(P1)/C4-008/C4-004/C9-011（G-C 回归网，N4 前置）；005→WS 设施族 G-A（C8-001(P1)/C4-002/C3-006/C8-009/C8-007/C1-005，N5 前置）；006→网关重试链族 G-B（C3-010/C4-003/C8-002(P1)，CL-025 先决）；007→C1-001(P2)/C2-004(并)/C1-002/C1-009（UserContext 面，C10-008 涉 C8-004→随 C1-003 工单验收）；008→C2-004(并→随 C1-001 工单验收)；009→C1-003/C8-004(并)/C3-014(并)（订阅注册表面）；010→G-B 组合级回归（C3-010/C4-003/C8-002）；011→G-D 流式缓冲（C3-004/C8-003/C9-005）；012→C2-003 loadByProp；013→C1-008(P3，N3 联动)；014→C2-002(P3)/C1-010（跨模块验收约定）；015→C8-009/C3-009；017→C1-007(P3)/C9-001/C9-003（一致性回归网）；018→C1-008 补充登记。

**2.4（17）**：C10-001→C1-001(P1 工单，←C1-023/C2-003)；002→C2-002(P1)/C2-003(并)/C3-001(P1)（异步入口全裸奔族）；003→C1-003(P1)/C1-004/C2-007/C1-008(c)+C2-008(并)/C6-003；004→C2-001(P1)/C1-002；005→C2-004(P1)/C1-027/C1-010；006→C2-005(P1)；007→C2-013(P1)/C2-015(P1)；008→C2-017(P1)/C1-026(并)；009→C3-005(P1)/C2-006(并)（**钉错相位**：现有测试绕开缺陷窗口）；010→C6-001(P1)/C3-006(并)；011→静态槽/可见性族（C1-006/C1-024/C1-029/C1-028+C2-020(并)/C1-011/C1-025/C1-018/C1-019/C1-021，含已落修复无钉项）；012→运行期可变注册面（C1-005/C2-011(P3)/C1-012/C1-013/C1-014/C1-017/C1-022(P3)）；013→C4-001/C4-002/C4-003/C3-004；014→C5-001/C5-002/C5-005（含 F3 勘误⑥）；015→C9-006/C9-003/C9-001/C9-002（热路径结构面）；016→C2-009（已落修复零回归钉）；017→C2-016（保护力降级+现状容忍行为钉 testMultipleCommit）。

**2.5（39，随本体全映射）**：C10-001→MA-03(P0)；002→MA-10(P1)；003→MA-04(P1)；004→MA-06(P1，含 RP-2 弱反向钉死)；005→独立#7 C3-008(P1)；006→独立#8 C3-009(P1)；007→独立#9 C3-010(P2)；008→独立#10 C3-011(P2)；009→独立#11 C3-012(P2)；010→MA-04(P1，C1-004)；011→MA-05(P1)；012→独立#4 C1-007(P1)；013→独立#5 C1-008(P2)；014→独立#14 C7-004(P1)+独立#6 C1-010(P2)；015→独立#13 C7-002(P1)；016→独立#16 C7-006(P2)；017→MA-07(P2)；018→独立#17 C7-008(P2)；019→跨单元随案（WI1.2 MA-05 回归面，C7-012 P3）；020→独立#2 C1-002(P2)；021→独立#3 C1-003(P1，含 RP-1 弱反向钉死)；022→MA-01(P1)；023→MA-02(P1)；024→独立#18 C7-009(P2)；025→独立#20 C7-014(P1)；026→独立#12 C6-005(P2)；027→MA-09(P2)；028→MA-08(P2)；029→独立#15 C7-005(P2)；030→MA-12(P3)；031→独立#19 C7-013(P2)；032→独立#21 C7-015(P2)；033→独立#22 C8-005(P2)；034→watch-only C8-006(P3，升格随附)；035→独立#23 C8-007(P2)；036→独立#1 C1-001(P2)；037→MA-11(P2，C8-004)；038→MA-11(P2，C8-003)；039→MA-12(P3)。35 张工单全部有 C10 覆盖。

**3.1（19）**：C10-001→MA-01(P1)+独立 C7-001/C7-002(P1)（含 C3-008 watch 面/C3-009 承载面）；002→MA-09(P1)；003→MA-03(P1，gate3 钉不完整清单)；004→MA-10(P2)+C5-016@MA-03；005→MA-02(P1)+独立 C1-010(P2)/C7-015(P2)；006→MA-08(P1)+独立 C4-009(P2)；007→MA-04(P1)/MA-05(P1)+独立 C4-015(P2)；008→MA-07(P2 移交单，护栏保留本单元)；009→MA-11(P1)；010→MA-19(P1)+MA-16(P2)；011→MA-17(P2)；012→MA-14(P2)+C1-026@MA-03 i；013→MA-12(P2)/MA-13(P2)+独立 C4-004(P2)；014→MA-13(P2)/MA-03 h/watch C1-027；015→MA-20(P2)/MA-21(P2)+独立 C5-002/C5-006(P3)/C7-008(P3)/C7-011(P3)；016→独立 C7-014(P2)+MA-03 e（C3-017 P2）；017→MA-06(P1)；018→独立 C7-003/004/005/006/007(P2)/C7-021(P3)+MA-18(C7-016 P2)；019→独立 C3-005/006/029(P2)/C4-006/011(P2)/C5-005/007/014/017(P2/P3)+MA-15(C3-015)+MA-03 g（C5-018 P2）。

**3.2（23）**：C10-001→MA-06(P1)；002（含**反向钉-1 改写义务** ShellIOTest:294-300）→MA-05(P2)；003→MA-07(P2)；004→MA-01(P1)；005→MA-02(P1)；006→MA-03(P1)+MA-04(P1)（共享 CommandRunner 注入接缝一缝两单）；007→MA-08(P2)；008→独立#16 C8-010+#17 C8-012(P2)；009→独立#14 C8-007(P2)；010→独立#15 C8-008(P2，ApplyDelta 已修无测补测义务)；011→独立#6 C4-004(P2)；012→独立#7 C4-006(P2)；013→独立#8 C4-007(P2)；014→独立#9 C4-008(P2)；015→独立#12 C8-004(P2)；016→独立#13 C8-005(P2)；017→独立#1 C1-004(P2)；018→独立#2 C1-005(P2)；019（条件性）→独立#3 C1-006(P2，随 7.1 裁决联动)；020（条件性）→独立#5 C1-010(P3)；021→独立#10 C6-007(P2)；022→独立#11 C6-009(P2)；023→独立#4 C1-007(P2)。

**3.3（18）**：C10-001→独立#3 C8-001(P1)；002→独立#2 C3-001+独立#1 C1-001（P1×2 同批复 A 回归面）；003→MA-07(P1)+独立#22 C1-009(P3)；004→独立#8 C2-002+独立#9 C2-009(P2)；005→MA-05(P2)；006→独立#5 C3-007(P2，含修复方案弱钉死注记)；007→独立#4 C3-003+独立#24 C3-008(P3)+独立#27 C8-004(P3)；008→MA-02(P2，随本体回升)；009→MA-04(P2)+独立#13 C8-002(P2，随本体回升)；010→独立#11 C4-004/#17 C6-001/#18 C6-002(P2)+独立#16 C2-004(P3)/#15 C2-003(P3)/#14 C1-002(P3)/#21 C1-007(P3)；011（**唯一假保护+反向钉死型**）→独立#12 C4-005(P2)+独立#20 C8-003(P2)+独立#25 C4-007(P3)；012→MA-03(P2，随本体回升)；013→独立#6 C7-002+独立#19 C7-003(P3)；014→独立#7 C7-005(P2)+独立#23 C3-005(P3)；015→独立#10 C1-010(P2)；016→MA-06(P3，SPI 接入回升)；017→MA-01(P3)；018→独立#26 C6-005(P3)。C10-001/002/003 随 P1 修复 roadmap 同批（7.1-D）。

**3.4（8，P2×4/P3×4）**：C10-001→MA-01(P1)；002→IND-02(P1)；003→IND-03(P1 主)+IND-06(P2 共享)；004→IND-01(P1 主)+覆盖 IND-04/IND-10/IND-12；005→IND-05(P2)；006→IND-07+IND-08(P2)；007→IND-09(P2)；008→IND-11(P3，**反向钉死**：intern assertSame 解除为同工单强制测试改造项，不解除则 C1-008 不可修)。

**4.1（12，P2×9/P3×3）**：C10-001→MA-02(P1)+MA-05(P1)+C6-006(P2)（TestDebeziumMessageSource 四场景随批）；002→MA-10(P2)+MA-09(P3)+C3-009(P2 Debezium 面)；003→MA-03(P2)+C1-002(P2)；004→MA-06(P2)+C8-007(P2)+覆盖闭合挂靠 C3-004（含反向钉死 #1 改写）；005→MA-01(P2)+C3-009(P2)；006→C8-002(P1)+MA-04+C3-006(P2)+C1-008(P3，**反向钉死 #4 触雷预警**)；007→C8-005(P2)；008→MA-07(P2)+C8-008(P3)+覆盖闭合挂靠 C3-005；009→MA-08(P3，**改写反向钉死 #2**)；010→C1-012(P2)+覆盖闭合挂靠 C5-006(P3)（**反向钉死 #3** 先改 4 处 setup）；011→C3-008/C3-010/C1-010(P3)+C3-007 残留①；012→C1-009(P3)。C10-001 为缺保护升档首选候选（被保护本体含 P1×3）。

**4.2（14，P2×4/P3×10）**：C10-001→MA-07(P1)+MA-09(P2)；002→MA-04(P2，随本体回升)；003→MA-01(P1 全员)；004→独立#6 C4-004(P2)+MA-01（C4-002 注记）；005→独立#3 C1-005/独立#2 C1-003/MA-03/独立#4 C2-002/独立#13 C3-011；006→独立#1 C1-001(P1)+MA-02(P2)；007→MA-08(P2)+独立#7 C8-001/独立#11 C1-009(P3)（含反向钉死 #3 解除）；008→独立#8 C8-006(P2)；009→MA-05(P2，含反向钉死 #1 解除)；010→独立#9 C8-007(P2)；011→MA-06(P3 全员)；012→独立#10 C1-008(P3，随本体回升)；013→独立#5 C4-003(P2)；014→独立#12 C1-010(P3)+watch C2-004。修复批门禁：优先 C10-001/003/006（P1 承载面）；两处硬反向钉死同 diff 强制。

**4.3（15，立项 14+watch 1）**：C10-001→C6-001(P0 即时通道)+C3-005(P1)+C6-013(P2)（下载链批）；002→MA-02(P1)+C6-003(P2)；003→MA-02(C3-003)+C6-004(P1)+C3-033(P2)（SSE 生命周期批）；004→C6-005/C6-017/C6-018/C6-019(P1)+MA-03(P1)+MA-16(P2)+C3-024(P1)；005→MA-04/MA-05/MA-06(P1，feishu 批)+C1-010/C1-011(P2)；006→MA-01(P1)+MA-09(P2)+C1-018/C3-022(P2)+A2 补登 C3-021(MA-15 P2，RP-1 改写义务随此)；007→MA-14(P2)+C6-028(P2)；008→MA-07(P1)+C1-005/C1-007/C3-009/C6-010/C6-015(P2/P3)；009→MA-08(P2)；010→C3-012(P2)；011→MA-10(P2)+C1-014/C1-016/C1-019/C3-023/C3-026/C3-027(P3)/C3-028(P3)+C6-027(P2)+MA-15 成员 C3-029(P3)；012→MA-12(P2)+MA-13(P2)+C3-007/C6-011/C6-012(P2)+C3-013(P3)+A1 补登 C1-003(P2)；013→C3-025(P2)+C3-030/C3-032(P3)+MA-11(P2)；014→C3-001(P1)+MA-15(P2)；015（watch-only）→MA-17(P3)+watch C1-020+C3-013/C3-014/C6-016(P3)。先行失败测试 16 处；RP-1 断言级反向钉死 2 处随 MA-15 先改测试。

**4.4（19，P2×16/P3×3）**：C10-001（强制）→C1-001(P1)；002→MA-05(P2)；003→C1-003(P2)；004→C1-004(P2)；005→watch C1-005(P2，搭车 C6-004)；006（强制）→MA-01(P1)；007→C4-001(P2)；008→C4-002(P2，**RP-2/RP-4** stub 可抛错+取消断言先于修复)；009→MA-01(P1)；010→C4-004(P2，**RP-3** 不预初始化测试上下文)；011（强制）→C6-001(P1)；012→MA-04(P2)；013→MA-04(P2)；014→C6-004(P2)；015→C6-005(P2)；016→C6-006(P3)；017（强制）→MA-02(P1，future.get(timeout) 断言)；018→MA-03(P2)；019→C6-009(P3，plan-first 随批)。反向钉死总约束 4 处（RP-1→CL-008 线索；RP-2/RP-4→C4-002；RP-3→C4-004）。

**4.5（9）**：C10-001→MA-01(P2)+watch C4-005(P3)；002→I-C4-001(P1，唯一 P1 零保护最优先)；003→MA-04(P2)；004→MA-02(P2)+I-C4-002(P2)；005→I-C6-001(P2)+MA-03(P2)；006→MA-05(P3)+I-C1-007(P3)；007→MA-01/I-C6-001/I-C6-005/I-C6-006/MA-02（横切回归锚，交叠不重复）；008→I-C1-006/I-C4-004/I-C6-005/I-C6-006(P3)；009→I-C1-008(P3)+CL-007 契约面。矩阵：会红 0/假保护 12/无测试 12；硬反向钉死 0、正向修复约束 1（S-04）。

**5.1（7）**：C10-001→MA-01(P1，C6-001)；002→MA-03(P2，C2-001)；003→MA-04(P2，C2-004)；004→MA-01(P2 面，C6-002)；005→MA-01(P2 面，C6-003)；006→I-01(P2，C6-004)；007→I-02(P2，C6-005，**反向钉死**——修复须先改 refreshFailureFailsClosed)。矩阵：会红 0/反向钉死 1/无测试 13；会红=0 第 5 例。

**5.2（15，本体工单→C10 倒排）**：I-01→C1-001；I-02→C1-002；I-03→C1-008（假保护转正）；I-04→C6-101（假保护镜像）；I-05→C8-001；MA-01→C2-001、C2-102、C2-002、C2-004；MA-02→C2-003（**反向钉死**：修饰符断言同 diff 改写；I-01 COW 不触红）；MA-03→C6-002；MA-04→C6-001、C6-003；MA-05→C2-005；MA-06→C1-003；MA-07→C8-004（假保护随案，P3）。矩阵 16 行会红=0（P1 4/4 无测试）——第 6 例。

**5.3（5，缺保护立项建议）**：缺保护 1→CL-014/W-01(P1，无测试最高优先)；缺保护 2→CL-009/W-04(P1)；缺保护 3→CL-003/W-02(P2，**反向钉死** testSlowExecutionRaceWorkerSuccessOverwritesScanFailed 修复同 diff 改写)；缺保护 4→CL-016/W-02(P2，假保护)；缺保护 5→CL-001/W-02(P2)。矩阵 18 行：会红 0/假保护 4（含顺序伪装）/部分 1/无测试 13——P1×2 全部无保护；反向钉死 1。

**5.4（25，实体级一一对应）**：25 条保留发现每条 1 项缺保护登记（P1×1=TP(C5-001)；P2×9=TP(C1-001/002/003/005、C5-004、C5-R2-001、C6-001、C9-001、C9-002)；P3×15）。矩阵会红 0/25——第 7 例（closure audit 裁定序数：4.5=4/5.1=5/5.2=6/5.4=7）；12 行相邻功能面保护（假保护形态）/13 行完全无测试。修复准入：每条修复须附使其变红的回归测试；C5-001 lengthExpr 恶意长度测试为 P1 修复准入门槛。

**5.5（38，实体级全量单列）**：缺保护 38 全登记（实体级 P1×4+P2×13+P3×21），随 29 工单落地。工单级 P2 映射：W-04→C10 行(C1-102/C1-001/C4-001)；W-05→行(C1-002，**反向钉定** TestMetaDataSourceConnectionSecurity:776-792)；W-06→行(C1-003)；W-07→行(C7-003/C4-R2-002)；W-08→行(C7-006/C5-104)；W-09→行(C5-001，含 C5-106 并入)；W-10→行(C5-101)；W-11→行(C7-002)；W-12→行(C7-R3-001)。矩阵会红 0/38——第 8 例；现状分布：无保护 27/邻接 10/反向钉定 1；形态升级：本单元有 3 个真并发测试类但保护目标全为历史修复回归面（「测试基建只向后看」）。

**5.6（12）**：P10-P1-1→W-01(P1，C10 矩阵 #4/#5/#8/#12)；P10-P1-2→W-02(P1，#2/#6)；P10-P1-3→W-03(P1，#9)；P10-P1-4→W-04(P1，#11/#16)；P10-P2-1→W-06(P2，#3)；P10-P2-2→W-05(P2，#7/#13)；P10-P2-3→W-07(P2，#14/#18)；P10-P2-4→W-08(P2，#17)；P3 四面→W-09/W-10/W-11/W-12（#1/#10/#15/#19）。矩阵会红 0/19——第 9 例；反向钉定 0（#19 为假保护/固化形态注记非反向钉定）。

**6.1（10）**：P2-01→W-01(P2，先红后绿)；P2-02→W-03(P2，先红后绿)；P2-03→W-05(P2，先红后绿)；P2-04→W-14(P2)；AG-P3-01→W-04+W-06(P3，取消半边先红后绿)；AG-P3-02→W-07+W-09+W-10；AG-P3-03→W-13+W-15+W-16+W-17；AG-P3-04→W-08；AG-P3-05→W-11；AG-P3-06→W-02；复核补记→W-12（写中断回归先红后绿）。矩阵会红 0/20——第 10 例；部分保护 7/20 本单元特色；反向钉定 0。

**6.2（19，复核修正终态）**：M-P1-1→W-01(P1，**hang-once-then-ok 假保护**：时序敏感、分支 A 零覆盖)；M-P1-2→W-02(P1)；M-P2-1→W-09(CS-R2-001)；M-P2-2→W-05(C1-001)；M-P2-3→W-06(C1-103)；M-P2-4→W-04(C1-104)；M-P2-5→W-03(C1-105)；M-P2-6→W-07(C6-001，复核补登)；M-P2-7→W-08(C6-002，复核自 P3 表归位)；P3 聚合 9 项→W-10~W-19。**反向保护 4 处**（#2 W-02 锁定 P1 修复路径/#8 W-08/#9 W-13(P3)/#11 W-15(P3)）修复必须捆绑测试改写。微缺口：#20 CS-003 无缺保护条目（MA 登记，C10 复核终态 19 项含 CS-003 补登口径——两文件表述差 1，见 §三）。

**6.3（41）**：TP-001→W-01(P1 前置)；TP-002→W-02(P1 前置)；TP-003→W-03(P1 前置)；TP-004~019→W-04~W-19（16 张 P2 工单逐条一一对应，其中 W-11 吸并 G11-15-02）；P3 聚合 22 面→W-20~W-40。矩阵会红 0/44——第 12 例；7 行完全无相关测试+37 行相邻覆盖零缺陷面断言；3 个受保护子面（C7-004 source 侧/C7-005 removeDocs 次序/C7-006 发布触发物化）+1 个断开的现成契约测试（C7-009→W-38，一行接入即红，补测最优先）；反向钉定 0（TestRebuildFromCommit 为掩蔽形态非反向钉定；TestProjectAnalyzerConcurrency 为 watch-only 资产）。「41 项唯一登记」=矩阵 44 行折算 3 别名行。

**6.4（0）**：零缺陷面盘点形态，无缺保护矩阵；契约测试缺口（assertNotSame 0 命中）登记 watch-only W-01~05（非工单）；campaign 会红序数链止于 6.3 第 12 例，本单元不计序。

**6.5（6）**：C10-001→W-01(P1，**验收前置**)；C10-002→W-02(P1，**验收前置**)；C10-003→W-04(P2)；C10-004→W-06(P2)；C10-005→W-05(P2)；C10-006→W-03(P2，同站点合并登记口径)。watch-only W-1~W-5→P3 工单 W-07~W-11（修复同 diff 顺带）。矩阵：会红 0/假保护 0/无测试 6——第 13 例（C10-003「假保护」经复核变异重放证伪修正为「无测试」）；全单元零钉死力零检出通道。

---

## §三 逐单元数字对账与已知差登记

| WI | 单元 | P2 数格（cra-index 报告行） | 口径块/裁定 P2（工单级） | 本盘点 P2 | C10 数格 | 本盘点 C10 | 对账结论 |
|---|---|---|---|---|---|---|---|
| 1.1 | nop-stream-core-runtime | 27+25 | 27（P1×10/P2×27/P3×24） | 27 ✓ | 25 | 25 ✓ | 一致 |
| 1.2 | nop-stream-state-connectors | 26+16 | 26（P0×1/P1×10/P2×26/P3×9） | 26 ✓ | 16 | 16 ✓ | 一致（C10-016 测试自身缺陷无本体） |
| 1.3 | nop-stream-orchestration-thin | 10+8 | 10（P1×4/P2×10/P3×6） | 10 ✓ | 8 | 8 ✓ | 一致（C10-007/008 无单一本体） |
| 1.4 | nop-job | 23+9 | 23（P1×2/P2×23/P3×9） | 23 ✓ | 9 | 9 ✓ | 一致（C10-009 无宿主） |
| 1.5 | nop-wf-nop-task | 29+14 | 29（P1×7/P2×29/P3×18；54 工单） | 29 ✓ | 14 | 14 ✓ | 一致 |
| 1.6 | nop-retry-nop-tcc | 22+10 | 22（P1 19→14/P2 38→22/P3 17→14） | 22 ✓ | 10 | 10 ✓ | 一致 |
| 1.7 | nop-batch | 26+12 | 26（P1×8/P2×26/P3×17；51 条） | 26 ✓ | 12 | 12 ✓ | 一致 |
| 2.1 | nop-kernel-concurrency | 63+14 | 63（P1 9→5/P2×63/P3×28；96 条） | 63 ✓ | 14 | 14 ✓ | 一致 |
| 2.2 | nop-core-framework | 21+18 | 21（P1×11/P2×21/P3×13；45 条） | 21 ✓ | 18 | 18 ✓ | 一致（16 初审+2 复核补充） |
| 2.3 | nop-service-framework-hotpath | 39+17 | 39（P1×7/P2×39/P3×12；59 条，承担修复 58） | 39 ✓ | 17 | 17 ✓ | 一致（C10-016 不存在，实有 001~015+017/018） |
| 2.4 | nop-persistence-rw | 31+17 | 31（P1 有效工单 13/P2×31/P3×23；67 条） | 31 ✓ | 17 | 17 ✓ | 一致 |
| 2.5 | nop-persistence-peri | 20+39 | 20（P0×1/P1×13/P2×20/P3×1；35 工单，MA-02 暂计 P1） | 20 ✓ | 39 | 39 ✓ | 一致 |
| 3.1 | nop-ai-core-nop-ai-agent | 29+19 | 29（P1×13/P2×29/P3×8；50 工单） | 29 ✓ | 19 | 19 ✓ | 一致（另有 watch C3-008 P2 级不计数） |
| 3.2 | nop-ai-toolkit-tools-shell | 19+23 | 19（P1×5/P2×19/P3×1；25 工单） | 19 ✓ | 23 | 23 ✓ | 一致 |
| 3.3 | nop-ai-gateway | 14+18 | 14（P1×4/P2×14/P3×16；34 工单） | 14 ✓ | 18 | 18 ✓ | 一致 |
| 3.4 | nop-ai-rest | 6+8 | 6（P1×4/P2×6/P3×5；15 工单+watch 1） | 6 ✓ | 8 | 8 ✓ | 一致 |
| 4.1 | nop-message | 15+12 | 15（P1×3/P2×15/P3×10；28 工单） | 15 ✓ | 12 | 12 ✓ | 一致 |
| 4.2 | nop-cluster | 13+14 | 13（P1×4/P2×13/P3×5；22 工单+watch 1） | 13 ✓ | 14 | 14 ✓ | 一致（R1 并入执行后 P2×4/P3×10 为 C10 条目级判级口径） |
| 4.3 | nop-network-integration | 32+15 | 32（P0×1/P1×15/P2×32/P3×10；58 工单） | 32 ✓ | 15 | 15 ✓ | 一致（立项 14+watch 1） |
| 4.4 | nop-host | 10+19 | 10（P1×4/P2×10/P3×2；16 工单+watch 1） | 10 ✓ | 19 | 19 ✓ | 一致 |
| 4.5 | nop-search-runner-autotest | 6+9 | 6（P1×1/P2×6/P3×7；15 工单） | 6 ✓ | 9 | 9 ✓ | 一致 |
| 5.1 | nop-auth-credential | 5+7 | 5（P1×2/P2×5/P3×7；14 工单+watch 4） | 5 ✓ | 7 | 7 ✓ | 一致 |
| 5.2 | nop-sys-dyn | 8+**6** | 8（P1×3/P2×8/P3×6；17 工单+watch 2）；**口径块 C10=15** | 8 ✓ | **6 vs 15** | 15（C10 文件/口径块） | **已知差 #1（预登记）**：报告行 C10=6 vs 口径块与 C10 文件 15 条单列（15 条全部有本体映射，§二 5.2 节）。campaign 总账按报告行 6 计（本盘点 §四 双轨登记）。差异性质：报告行计数口径未随裁定文件更新，属回填滑差，非登记缺失 |
| 5.3 | nop-file-datav | 13+5 | 13（P1×2/P2×13/P3×10；7 修复工单+1 测试保护工单） | 13 ✓ | 5 | 5 ✓ | 一致（矩阵 18 行折 5 项立项建议） |
| 5.4 | nop-report-format | 9+25 | 9（P1×1/P2×9/P3×15；25 条未组族） | 9 ✓ | 25 | 25 ✓ | 一致（C10 实体级一一对应） |
| 5.5 | nop-rule-metadata | **9**+38 | 工单级 9（P1×3/P2×9/P3×17）；**实体级 P2×13**（P1×4/P3×21） | 9（工单级）/13（实体级） | 38 | 38 ✓ | **已知差 #2（预登记，非错误）**：工单级 9 vs 实体级 13，差 4 = 吸收成员（C1-001/C4-001→W-04、C4-R2-001→W-01、C7-101→W-02、C5-104→W-08），口径块已双载 |
| 5.6 | nop-graph-duckdb | 4+4+12 | 4（P1×4/P2×4/P3×4；12 工单） | 4 ✓ | 12 | 12 ✓ | 一致（数格=P1+P2+C10） |
| 6.1 | nop-tools-parallel | 4+10 | 4（P1×0/P2×4/P3×13；17 工单） | 4 ✓ | 10 | 10 ✓ | 一致 |
| 6.2 | nop-tools-parse | 7+10+**19** | 7（P1×2/P2×7/P3×10；19 工单）；**口径块 C10=19 项** | 7 ✓ | 19 | 19 ✓ | 一致（数格=P2+P3+C10；C10 终态 19 项为复核补登 CS-003/C6-001/C6-002 归位后口径，MA §三.5 的「18 项+微缺口」为补登前时点，两文件合计一致） |
| 6.3 | nop-code-index | 16+21+**41** | 16（P1×3/P2×16/P3×21；40 工单） | 16 ✓ | 41 | 41 ✓ | 一致（数格=P2+P3+C10） |
| 6.4 | nop-code-rest | 0+0 | 0（零立案单元） | 0 ✓ | 0 | 0 ✓ | 一致（零缺陷面盘点形态） |
| 6.5 | nop-peripheral | **4+5**+6 | 工单级 4（P1×2/P2×4/P3×5）；**实体级 P2×5**（P1×2/P3×5） | 4（工单级）/5（实体级） | 6 | 6 ✓ | **已知差 #3（预登记，非错误）**：数格 4+5+6 = 工单级 P2×4 + 实体级 P2×5 + C10×6，口径块已双载；差 1 = 别名 C6-003→C1-004 |

**对账总表**：32 单元中 29 个报告行 P2/C10 数与本盘点完全一致；3 个预登记差（5.2 报告行 C10=6 vs 口径块 15；5.5 工单级 9 vs 实体级 13；6.5 工单级 4 vs 实体级 5）均已在 cra-index 计数口径块双载或属回填滑差，无实体遗漏。

### 附：其他口径备注（非差异）

1. **数格格式三种**：`P2+C10`（多数单元）、`P1+P2+C10`（5.6）、`P2+P3+C10`（6.2/6.3）、`P2工单+P2实体+C10`（6.5）——读取时须按单元口径块区分，勿按统一格式误读。
2. **watch-only 候选（P2 级）不入工单计数**：3.1 C3-008（callStream 晚订阅，转立案条件随 C10-001）、4.4 C1-005（volatile 理论，搭车 C6-004）、5.3 CL-007（池占用，随 W-07 附带评估）。P3 级 watch 若干（4.2 C2-004、4.3 C1-020、4.5 C4-005、5.2 W-01/W-02、5.6 W-12、6.1 W-06、5.5 W-26 等）。
3. **P2 判级分歧/开放项登记 7.1（非差异）**：4.1 MA-01（C8-001 P1↔P2）、MA-10（C1-001 P1↔P2 双轨）、4.3 MA-03（apache P1/jdk+okhttp P2 双判级单工单）、4.4 MA-03 家族判级对齐、2.2 C6-012（降 P3 留协调方）、3.4 O-3（四张条件化 P2 裁决）、5.5 W-01（C4-R2-001 P2 vs P1 三登记点统一）、6.5（P2 逐项裁决台账待建）。
4. **跨单元同号防混**（台账引用须带单元前缀）：C7-001（1.5 WfTaskScanner / 1.6 三账本零保留 / 1.7 RUNNING 死区 / 2.1 Bind fixedRate / 3.1 流式聚合 / 5.5 质量监控停摆后果层）六处同名异站；C4-003（1.7 batch 工厂 / 2.1 kernel 设施——同设施同面双登记处置归 2.1）；C5-001（1.5 / 1.7 / 5.4 / 6.3 同号异站）；C3-008（1.3 / 2.5 / 4.3 同号异缺陷）；C1-010（2.3 / 2.4 同号异单元互补）；C6-001（4.3 P0 已修 / 4.4 P1 / 5.4 P2 / 5.6 P1 同号异站）；C6-005（2.5 双轨 / 3.3 P3 / 4.3 P1 / 5.1 P2 同号异站）。

---

## §四 总计

### P2 实体总数

- **工单级 P2 = 560**（32 单元合计；6.4 零立案）。
- **实体级 P2 = 565**（5.5 工单级 9→实体 13（+4 吸收成员）、6.5 工单级 4→实体 5（+1 别名实体））。
- 分 WI（工单级）：WI1=163（27+26+10+23+29+22+26）、WI2=174（63+21+39+31+20）、WI3=68（29+19+14+6）、WI4=76（15+13+32+10+6）、WI5=48（5+8+13+9+9+4）、WI6=31（4+7+16+0+4）。
- 复核：163+174+68+76+48+31 = **560** ✓（工单级）。实体级：WI5=52（5.5 实体 13）、6.5 实体 5 → 全 campaign 实体级 = 560 + 4 + 1 = **565** ✓。

### C10 缺保护总数

- **合计 505**（按 cra-index 报告行口径，5.2 计 6）。
- 分 WI：WI1=94、WI2=105、WI3=68、WI4=69、WI5=93、WI6=76 → 94+105+68+69+93+76 = **505** ✓。
- **双轨登记**：若按各单元 C10 文件/口径块实有登记口径（5.2 计 15），合计 = **514**。两口径差 9 = 5.2 报告行回填滑差（§三 已知差 #1）。7.1 对账与 9.1 终验建议采用 514 为物理登记数、505 为台账行口径数，并以 §二 5.2 节 15 条映射为对账底账。

### 结构性速览（供 7.1 宣读）

- 条件化激活条款工单（P2 级为主）：1.2 MA-12、2.1 C5-001/C7-001/C7-003、2.2 C6-011/C6-012、2.3 C1-001 工单、2.4 C1-021、3.1 C1-005/C4-006、3.2 MA-05/MA-08/C1-006/C4-006/C8-004/C8-005、3.3 MA-02/MA-03/MA-04+C4-004/C4-005/C8-002（13 张渠道栈潮）、3.4 IND-05/06/07/08/09、4.2 MA-02/MA-04、4.3 C3-021/C3-022（vertx-mqtt 面）、4.5 I-C6-001、5.4 C1-002、6.3 W-05/W-10。
- 反向钉死/假保护改写义务（修复 diff 必须先改/同改测试）：1.2 C10-003（条件会红）、1.5 C10-009、2.1 C1-014（N2）、3.2 MA-05（反向钉-1）、3.3 C10-011（唯一假保护+反向钉死型）、4.1 #1/#2/#3（+软钉 #5）、4.2 MA-05/MA-08、4.3 MA-15（RP-1×2）、4.4 C4-002/C4-004（RP-2/3/4）、5.2 MA-02、5.3 CL-003、5.5 W-05、6.2 W-02/W-08。
- P2 跨单元移交/双轨：3.1 MA-07 整族→WI 2.1 C3-001；4.1 C3-009 修复归属→WI 2.1 绝缘层批次；2.3 C1-010→nop-auth-service 单元、C9-002→WI 2.1 C9-010；2.1 C2-016 api-core 移交包；2.5 C6-005 设施/使用双轨；5.4 C5-004↔1.7 C5-001 同缺陷双轨。

---

*盘点产物（子代理 B）。只读 32 个审计单元目录与 cra-index 产出；未修改任何源文件。判级与映射以各单元 merge-adjudication.md（工单级清单/三态表）与 C10-test-protection.md（缺保护登记/映射节）为权威；本文件为 7.1 跨模块裁定的机械对账底账。*
