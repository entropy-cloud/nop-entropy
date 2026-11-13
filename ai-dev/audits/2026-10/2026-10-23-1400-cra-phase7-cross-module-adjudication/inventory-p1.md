# CRA Phase 7.1 盘点 · P1 台账提取（inventory-p1）

> 提取人：Phase 7.1 盘盘子代理 A（P1 台账提取）
> 提取时点：2026-10-23；来源：`ai-dev/audits/cra-index.md`「P1 发现汇总（待 Phase 8 批量修复）」全表 + 各单元计数口径块（只读，未改动源文件）
> 列说明：**Finding ID** = 发现编号（源表 Finding ID 去除括号注记）；**工单号** = 批量修复登记工单号（族主 MA/W/FAM/复合工单号；无族号时即 Finding ID 本身）；**判级** = 源表口径（全部 P1，含行内暂计/留裁标注）；**吸收成员** = 行内 `←X`/`成员 X` 内联提取；**验证通道约束** = 行内 Gradle/harness 类通道约束；**PA 标注** = 行内 Protected Area（plan-first/ask-first）标注。行号即源表顺序。

## §一 P1 台账提取表（179 行全量）

| # | Finding ID | 单元 | 工单号 | 判级 | 一句话（≤60字） | 吸收成员（←） | 验证通道约束 | PA 标注 |
|---|-----------|------|--------|------|----------------|---------------|--------------|---------|
| 1 | C1-001 | nop-stream-core-runtime | C1-001 | P1 | SplitEnumerator 被任务线程无锁直调，快照撕裂/双份分配 | ←C2-001、C3-001 | | |
| 2 | C1-006 | nop-stream-core-runtime | C1-006 | P1 | 任务分配账本只增不删，removeTaskAssignment 零调用方 | ←C7-002 | | |
| 3 | C4-001 | nop-stream-core-runtime | C4-001 | P1 | LOCAL 执行固定池=核数+有界输出队列互等死锁 | ←C8-013 | | |
| 4 | C4-002 | nop-stream-core-runtime | C4-002 | P1 | CachingTableLookup 刷新池无界+无 in-flight 去重 | ←C3-002、C8-014 | | |
| 5 | C4-009 | nop-stream-core-runtime | C4-009 | P1 | OpsJobManager.submit 失败窗口孤儿 coordinator+同 jobId 双活 | | | |
| 6 | C5-010 | nop-stream-core-runtime | C5-010 | P1 | PollingJdbcMessageService 无 LIMIT 全量物化 backlog OOM | ←C8-002 | | |
| 7 | C7-011 | nop-stream-core-runtime | C7-011 | P1 | standby 降级不取消在途 checkpoint persist，僵尸第二写者 | | | |
| 8 | C8-003 | nop-stream-core-runtime | C8-003 | P1 | msg_queue INSERT-only 无界账本+WI6 门控不可达 | ←C7-003 | | |
| 9 | C8-011 | nop-stream-core-runtime | C8-011 | P1 | launch 共享 2 线程 poller：数据面积压垄断→控制面饥饿 | ←C4-004 | | |
| 10 | C5-001（主） | nop-stream-core-runtime | FAM-1 | P1 | materialization store 无界+overflow-bypass 背压旁路 | ←C7-001、C8-001 | | |
| 11 | C1-002 | nop-stream-state-connectors | MA-02 | P1 | RocksDB gauge 锁外取 dbRef→native UAF（SIGSEGV 窗口） | ←C2-001、C6-004 | | |
| 12 | C1-001 | nop-stream-state-connectors | MA-03 | P1 | CEP copyForSubtask 引用透传共享：NFA 图/ctx 槽位/udf 生命周期三面 | ←C1-005、C1-006；注：C1-001 已知未修复←r4 R5-CEP-05 | | |
| 13 | C2-002 | nop-stream-state-connectors | MA-04 | P1 | Avro registry 无请求超时×computeIfAbsent 锁内 HTTP（挂死+锁毒化） | ←C3-003、C4-003、C6-008、C8-004、C7-006① | | |
| 14 | C3-002 | nop-stream-state-connectors | MA-05 | P1 | 2PC 持久化登记无保留策略：JDBC 台账只增不删+file manifest O(E) 放大 | ←C5-006、C7-001、C8-007、C7-007① | | |
| 15 | C4-004 | nop-stream-state-connectors | MA-06 | P1 | Debezium 引擎无限阻塞任务常驻平台共享 globalWorker 池 | ←C3-009 | | |
| 16 | C3-014 | nop-stream-state-connectors | MA-07 | P1 | Debezium 停止/启动契约无终结保证：幽灵引擎/重启交叠/排队窗口 | ←C4-005、C4-006 | | |
| 17 | C5-002 | nop-stream-state-connectors | MA-08 | P1 | RocksDB 默认全量 JSON checkpoint 整库物化进堆 | ←C8-005 | | |
| 18 | C3-004 | nop-stream-state-connectors | C3-004 | P1 | Debezium 枚举器恢复双登记→同表双引擎重复消费 | | | |
| 19 | C8-001 | nop-stream-state-connectors | C8-001 | P1 | checkpoint offset 领先已发射→恢复静默丢 ≤256 条/检查点 | | | |
| 20 | C8-006 | nop-stream-state-connectors | C8-006 | P1 | Kafka 2PC 事务存活期与 checkpoint 间隔零裕度→触界失败循环 | | | |
| 21 | C1-001 | nop-stream-orchestration-thin | MA-01 | P1 | UDF 注册表非线程安全：无锁读×任意线程写+运行期静默换绑（含 sql.md §3a docs bug） | ←C3-002、C2-001 | | |
| 22 | C3-008 | nop-stream-orchestration-thin | C3-008 | P1 | region 重启兄弟 initializeState REPLACE 清零已推进游标→重放已反映窗口（编排面关联 runtime） | | | |
| 23 | C3-009 | nop-stream-orchestration-thin | C3-009 | P1 | 共享 UDF×open() 覆写共享 RuntimeContext→键控状态收敛单一 backend（根因载体 core，跨单元） | | | |
| 24 | C6-007 | nop-stream-orchestration-thin | C6-007 | P1 | cancelled volatile 跨 run 永不复位→区域重启零事件静默 EOS 假成功 | | | |
| 25 | C4-001 | nop-job | MA-01 | P1 | rpcPoll 条目无终结驱动：注册表/句柄永久泄漏+无退避重试风暴+零观测（修复同盖五面） | 成员 C5-001/C7-001/C5-005 | | |
| 26 | C2-001 | nop-job | C2-001 | P1 | per-job 监视器内调用外部 invoker：停机无限期阻塞+可构造 ABBA 死锁 | | | |
| 27 | C1-001 | nop-wf-nop-task | MA-01 | P1 | task SUSPENDED 转移游离监视器×cancel 竞态（kill 静默丢失/KILLED 后复活） | 成员 C2-001/C7-016 | | |
| 28 | C3-001 | nop-wf-nop-task | MA-02 | P1 | wf 跨请求快照竞态五站点：组迁移丢失/终结滞留/join 双实例/action 双跑/子流回滚 | 五站点族 | | |
| 29 | C3-002 | nop-wf-nop-task | MA-03 | P1 | sync+executor 同池嵌套死锁与无界 sync 等待 | 成员 C4-002/C2-010 | | |
| 30 | C4-001 | nop-wf-nop-task | MA-04 | P1 | retry/delay 业务体+saveState 恒跑共享 globalTimer（4 线程），全平台定时语义失准 | 成员 C4-008/C3-013 | | |
| 31 | C5-001 | nop-wf-nop-task | MA-05 | P1 | 到期/提醒扫描无 LIMIT 全量载入+毒步骤不收敛 | 成员 C8-001 | | |
| 32 | C5-003 | nop-wf-nop-task | MA-08 | P1 | 全局闸门强引用注册表×动态 keyExpr 键空间持久无界（plan 364 之上；owner 表态出口） | 成员 C8-004 | | |
| 33 | C7-001 | nop-wf-nop-task | MA-23 | P1 | WfTaskScanner 多节点/重叠扫描无 fencing：due 双跑先于守卫+remindCount 丢失更新 | 成员 C1-005 | | |
| 34 | C8-012 | nop-retry-nop-tcc | C8-012 | P1 | 返回 future ≠ 回调挂接 future，同步 DB 异常下扫描器 join 永久冻结（C10-001 覆盖） | ←C6-001/C1-008/C1-003A | | |
| 35 | C1-001 | nop-retry-nop-tcc | C1-001 | P1 | TCC 全局/分支状态 CAS 失败不阻断补偿 RPC，守卫基于私有过期内存副本 | ←C3-001 | | |
| 36 | C3-002 | nop-retry-nop-tcc | C3-002 | P1 | 分支级 CAS 失败不回写内存，聚合折叠 SUCCESS→全局误报终态 | ←C3-016 | | |
| 37 | C3-003 | nop-retry-nop-tcc | C3-003 | P1 | retry 调用链全程不续租，租约到期扫描器接管在途任务双执行 | ←C1-009 | | |
| 38 | C3-005 | nop-retry-nop-tcc | C3-005 | P1 | OVERWRITE 删行不取消在途异步链：幽灵任务与双执行 | | | |
| 39 | C3-006 | nop-retry-nop-tcc | C3-006 | P1 | doExecute 簿记写内联结果回调，簿记异常覆盖业务结果→成功改判失败重试 | ←C6-016 | | |
| 40 | C3-012 | nop-retry-nop-tcc | C3-012 | P1 | 全局首跳 CAS 异常→分支零驱动→聚合折叠 SUCCESS→陈旧 from 直写终态 | | | |
| 41 | C6-002 | nop-retry-nop-tcc | C6-002 | P1 | 崩溃/挂死后 RUNNING attempt 永久残留，UK 碰撞将记录永久砖化 | | | |
| 42 | C6-003 | nop-retry-nop-tcc | C6-003 | P1 | OVERWRITE delete+insert 非原子：半个成功后任务蒸发且无死信 | ←C8-006 | | |
| 43 | C7-001 | nop-retry-nop-tcc | C7-001 | P1 | retry 三张账本表零保留机制，成功即沉淀永久行 | ←C6-004 | | |
| 44 | C7-003 | nop-retry-nop-tcc | C7-003 | P1 | 解析/同步异常路径永不终止僵尸记录+每租约周期全栈 ERROR 放大 | | | |
| 45 | C7-005 | nop-retry-nop-tcc | C7-005 | P1 | tcc 账本清理 API 零生产调用方+耗尽行双重永久排除+全表零索引 | | | |
| 46 | C7-012 | nop-retry-nop-tcc | C7-012 | P1 | 扫描器只服务单一 namespace：非 default 记录永久 PENDING（default-only 部署不触发） | | | |
| 47 | C8-001 | nop-retry-nop-tcc | C8-001 | P1 | 扫描循环被拒/Error 即（近）静默永久停摆：无自防御无观测无重臂（跨单元关联 nop-job C4-003，修复须平台级统一） | ←C7-004/C6-014 | | |
| 48 | C8-001 | nop-batch | C8-001 | P1 | dispatch 容量全链零护栏：≤0→全表加载 Semaphore(0) 挂死/静默空任务（修复先于 C8-009） | ←C5-005 | | |
| 49 | C8-002 | nop-batch | C8-002 | P1 | ExportDbTool 缺省 batchSize=0 静默钳位 1 每行一 chunk：写放大磁盘耗尽（biz 链穿透同批 C8-010） | ←CL-008 | | |
| 50 | C5-001 | nop-batch | C5-001 | P1 | excel-reader 全 sheet 物化 List：50 万行 1.5–3GB，先物化后截断无兜底（C10-011 补回归钉义务） | | | |
| 51 | C5-002 | nop-batch | C5-002 | P1 | jdbc-reader fetchSize 死配置：门恒 false+非 MySQL 无 streaming→GB 级全量缓冲（修复须改写测试 N2） | ←CL-038 事实 | | |
| 52 | C6-001 | nop-batch | C6-001 | P1 | executeChunkLoop 仅 catch Exception：Error 逃逸 future 永不完成→资源泄漏+永久 RUNNING（须与 C6-010 同 diff） | ←C7-002B/CL-026/CL-024 验证断言 | | |
| 53 | C6-002 | nop-batch | C6-002 | P1 | executor.execute REE 裸逃逸：句柄泄漏+永久 RUNNING（默认装配不可达已裁定） | ←C7-002A | | |
| 54 | C7-001 | nop-batch | C7-001 | P1 | 任务行永久 RUNNING 死区：无 stale 判定无 fencing，cron 每 5s 错误风暴（须先改写测试 N1；关联 1.5 C7-001/1.6 C8-001） | 平台级 fencing 对齐：WI 1.5 C7-001/WI 1.6 C8-001 | | |
| 55 | C7-003 | nop-batch | C7-003 | P1 | 三张账本表零保留：清理代码不存在，UUID taskKey 每触发 +1 行单调增长（Phase 8 批次对齐 1.6 账本保留族） | 跨单元关联 WI 1.6 C7-001 账本保留族 | | |
| 56 | C1-001 | nop-kernel-concurrency | C1-001 | P1 | RoundRobinSupplier.resize 收缩锁外关旧数组尾段：并发 get 取得已关连接（Lettuce+IoC 刷新链可达） | 退役代际族吸收 C2-021 | | |
| 57 | C2-001 | nop-kernel-concurrency | C2-001 | P1 | ResourceCacheEntry 持 entry 监视器加载模型：跨 entry 嵌套监视器死锁 | C2-017 同变更集绑定 | | |
| 58 | C3-001 | nop-kernel-concurrency | C3-001 | P1 | 周期任务设施无统一异常绝缘层：一次 Throwable 静默永久停摆（29 生产站点；与 1.6 C8-001 平台级统一） | 与 WI 1.6 C8-001 平台级统一 | | |
| 59 | C4-001 | nop-kernel-concurrency | C4-001 | P1 | globalTimer 死配置（maxPoolSize=0）+4 线程全平台共享定时器阻塞放大（28 消费方；与 1.5 MA-04 统一） | 与 WI 1.5 MA-04 隔离方案统一 | | |
| 60 | C7-007 | nop-kernel-concurrency | C7-007 | P1 | 模型缓存 check-changed 双漏检窗口（旧内容+新 mtime 固化；ResourceTimestampCache 死代码） | 与 C9-001/C9-010 同链三面绑定单点修复 | | |
| 61 | C1-007 | nop-core-framework | C1-007 | P1 | 配置发布协议三字段裸写裸读+两步写序撕裂（7 写点×8 读站；2.1 C1-001 刷新链上游） | 族案含 C1-017 | | |
| 62 | C1-009 | nop-core-framework | C1-009 | P1 | JdbcConfigSource.close 竞态：停机后在途刷新重建永不关闭的连接 | ←C4-002 同位点并案 | | |
| 63 | C1-010 | nop-core-framework | C1-010 | P1 | 插件 unload×reconcile 交错活容器永久泄漏（lifecycleLock 全迁移统一修复批次） | 含 C1-011/012 并案、←C2-004 | | |
| 64 | C1-021 | nop-core-framework | C1-021 | P1 | tryTriggerChange 在 try 外：回调异常永久静默杀死 DB 配置热更新全链 | 来源 CL-001 终裁升格；修复归 WI 2.1 C3-001 平台绝缘层吸收 | | |
| 65 | C2-001 | nop-core-framework | C2-001 | P1 | getBean0 锁序环依赖边不可见跨线程永久死锁（历史 check 审计 P2-6 同源回访升 P1） | ←C2-012 | | |
| 66 | C2-002 | nop-core-framework | C2-002 | P1 | ProducedBeanInstance 跨实例 monitor 等待环 wait 无超时 | ←C1-002 跨维度并案 | | |
| 67 | C4-003 | nop-core-framework | C4-003 | P1 | concurrent-start 三复合面：无 join 点+失败吞入+死容器 exit-0 伪装/quarkus 三绿假活 | ←C4-008；同 PR 批次 C4-009/011+CL-019 | | |
| 68 | C6-001 | nop-core-framework | C6-001 | P1 | loadPluginFromJar 失败点 try 外：ClassLoader+JAR 句柄线性累积泄漏（与测试 N2 设计冲突须消解） | | | |
| 69 | C6-002 | nop-core-framework | C6-002 | P1 | 插件状态机永久卡死 DEACTIVATING/ACTIVATING 无恢复 API | | | |
| 70 | C6-003 | nop-core-framework | C6-003 | P1 | config source 先调度后加载：初始失败泄漏永久 globalTimer 刷新任务（启动期降档不适用论证成立） | | | |
| 71 | C6-004 | nop-core-framework | C6-004 | P1 | ShellRunner 非 timeout 异常不销毁子进程+stderr 线程 pin（修复须外层 try finally） | ←C6-014 | | |
| 72 | C3-002 | nop-service-framework-hotpath | C3-002 | P1 | invokeOperation 出口泵缺 thenOnContext：OrmBatchLoader 完成线程 requireSession→NOT_IN_SESSION | | | |
| 73 | C3-003 | nop-service-framework-hotpath | C3-003 | P1 | BufferedStreamingPublisher 背压断裂：越窗后元素进无界 ArrayDeque，慢客户端内存失控（AI 路由默认形态） | | | |
| 74 | C3-007 | nop-service-framework-hotpath | C3-007 | P1 | callActionAsync 裸 executor.execute：IContext 不传播，租户/用户跨任务残留（全单元唯一站点） | | | |
| 75 | C4-007 | nop-service-framework-hotpath | C4-007 | P1 | SSO token 刷新在过滤器载体线程无超时阻塞 IdP HTTP：brownout 确定性服务级停摆 | | | |
| 76 | C8-001 | nop-service-framework-hotpath | C8-001 | P1 | WS 订阅推送同步阻塞发送（无写超时）钉死 globalTimer 全池（实为 4 线程，ρ=N·L/40） | | | |
| 77 | C8-002 | nop-service-framework-hotpath | C8-002 | P1 | Gateway 429 重试放大×7 无准入上界：在途×90s 挂起确定性 OOM 轨迹 | | | |
| 78 | C9-012 | nop-service-framework-hotpath | C9-012 | P1 | LocalDateTimeFieldMarshaller computeSize=20 vs 实写 23-29 字节 protobuf 越界（移交功能正确性维度） | | | |
| 79 | C1-001 | nop-persistence-rw | C1-001 | P1 | IContext 会话/事务注册表 get-or-create 竞态：僵尸会话/事务表覆盖/双一级缓存 | ←C1-023/C2-003；ContextAttrs.getOrCreate 单修复 | | |
| 80 | C1-003 | nop-persistence-rw | C1-003 | P1 | ormModel 非 volatile：reload 后旧模型无限期可见+非同步 setOrmInterceptor | | | |
| 81 | C1-015 | nop-persistence-rw | C1-015 | P1 | OrmComponentModel 先发布引用后填充：getColumnPropIdMap 热路径竞态（无需 JMM 假设） | | | |
| 82 | C2-001 | nop-persistence-rw | C2-001 | P1 | session 全生命周期零互斥：flush 重入守卫静默跳过（并发丢刷写/双刷写） | | | |
| 83 | C2-002 | nop-persistence-rw | C2-002 | P1 | runInNewSessionAsync 完成线程执行 flush/registry 恢复/close 无同步 | | | |
| 84 | C2-004 | nop-persistence-rw | C2-004 | P1 | 全局实体缓存 read-repair：迟到 put 覆盖并发 evict，陈旧值无界驻留（实体级 opt-in 可达） | | | |
| 85 | C2-005 | nop-persistence-rw | C2-005 | P1 | DaoEntityBlockingSource CAS 抢占退化无条件 UPDATE：任务重复投递 | | | |
| 86 | C2-013 | nop-persistence-rw | C2-013 | P1 | 事务注册协议无原子性：半个成功 ERR_TXN_NOT_REGISTERED（REQUIRED 组语义退化） | | | |
| 87 | C2-015 | nop-persistence-rw | C2-015 | P1 | subTransactions 裸 LinkedHashMap：组提交丢写无感知（最重后果） | | | |
| 88 | C2-017 | nop-persistence-rw | C2-017 | P1 | JdbcTransaction 连接生命周期无互斥：双取池连接泄漏+僵尸连接丢写 | ←C1-026 | | |
| 89 | C3-001 | nop-persistence-rw | C3-001 | P1 | runInNewSessionAsync 上游永不完成：session 永不关闭+注册表滞留 | | | |
| 90 | C3-005 | nop-persistence-rw | C3-005 | P1 | flush 先清 dirty 再执行 SQL：非事务路径半个成功残留 | ←C2-006 | | |
| 91 | C6-001 | nop-persistence-rw | C6-001 | P1 | drainTo 事务横跨等待窗口：K≥8 消费者先于生产者即连接池耗尽 | ←C3-006 单工单 | | |
| 92 | C6-004 | nop-persistence-peri | MA-01 | P1 | PubSub 订阅管理竞态：cancel/suspend/resume 绕过监视器，订阅静默失效或复活双投递 | ←C1-011/C3-004；已知未修复 check/nosql-cdc 2026-08-19 前案 | | |
| 93 | C8-001 | nop-persistence-peri | MA-02 | P1（开放裁定暂计，7.1 终裁） | PubSub 消费回调在共享 Redis IO 线程内联：慢消费者队头阻塞→断链丢消息，ack 契约被整体丢弃 | ←C3-005/C7-010 | | |
| 94 | C6-003 | nop-persistence-peri | MA-04 | P1 | 链停止协议不可验证：thread 晚于 start 发布 join 空转、abort 读 stale-null、stopAll 仅 cancel 不等待 | ←C1-004/CL-009 | | |
| 95 | C7-003 | nop-persistence-peri | MA-05 | P1 | 状态行全列覆盖无版本谓词+7 编排入口零单飞/fencing：abort×confirm 背离终态、多节点双跑双链 | ←C1-005/006/009 | | |
| 96 | C7-001 | nop-persistence-peri | MA-06 | P1 | 链死亡不可见：getFailure 零调用方，lag=0 门禁把死链判追平，VERIFY 放行缺数（先修 C3-008） | ←C3-002/CL-006/CL-018/CL-023 | | |
| 97 | C3-001 | nop-persistence-peri | MA-10 | P1 | startFallbackChains 部分启动无补偿+jobName 丢 querySpace 覆盖：孤儿链不可停且双写同目标表 | ←C6-001 | | |
| 98 | C1-003 | nop-persistence-peri | C1-003 | P1 | 迁移历史表全守卫非原子 check-then-act：多节点 DDL 重复执行+FAILED 脏行覆盖→永久重试循环 | | | |
| 99 | C1-007 | nop-persistence-peri | C1-007 | P1 | rebuildRouting 非 volatile 热替换 shardSelector：SWITCH 后无限期读旧路由，业务写静默落旧分片 | 平台侧同案 nop-core-framework-C1-007 | | |
| 100 | C3-008 | nop-persistence-peri | C3-008 | P1 | lag 探测 connector 名与链注册名 base-i 错位：lag=0 门禁观测量恒空平凡通过（MA-06 修复顺序前置） | MA-06 修复顺序前置 | | |
| 101 | C3-009 | nop-persistence-peri | C3-009 | P1 | relocate 路由 querySpace 硬编码空串：增量链 filter 恒 false 100% 丢事件（与 C3-008 非同根双立） | | | |
| 102 | C7-002 | nop-persistence-peri | C7-002 | P1 | 五状态机 ALLOWED 零自环：javadoc 承诺的 fix-forward 路径自身走不通，一次瞬时故障全量报废 | ←CL-007/CL-008 | | |
| 103 | C7-004 | nop-persistence-peri | C7-004 | P1 | 写门 updateConfigValue 进程内裸写无持久化：多实例门状态不收敛，VERIFY 资格判定失真 | | | |
| 104 | C7-014 | nop-persistence-peri | C7-014 | P1 | PubSub 断线自动重连后 Redis 侧订阅全丢无重订阅无检测：静默失联直至重启（lettuce 7.5.2 字节码核验） | | | |
| 105 | C3-001 | nop-ai-core-nop-ai-agent | MA-01 | P1 | 流式主链族：取消只传上游、publisher 永不关闭、聚合计无界——永久无终止信号、单请求 OOM | ←C3-002/009+C4-001/002+C5-001 | | |
| 106 | C1-008 | nop-ai-core-nop-ai-agent | MA-02 | P1 | 引擎注入目标登记窗口：resume/wake/restore 零注册+暂存分支裸 ArrayList——CME/撕裂持久化/注入丢失 | ←C3-014 | | |
| 107 | C5-015 | nop-ai-core-nop-ai-agent | MA-03 | P1 | F4 引擎终态清理族：session store put-only+checkpoint/账本无终态释放——历史会话堆内永驻（族 29 站点） | ←C5-009/012/013/020+C1-006/011/014/016/017/018/026+C7-013/017/028/030/031 | | |
| 108 | C4-010 | nop-ai-core-nop-ai-agent | MA-04 | P1 | spawn 编排池泄漏：per-invocation orchestrator 全仓无 close——每次工具调用泄漏固定池常驻线程 | ←C7-032 三重登记统一 P1（源行另含自引 ←C4-010，见 §三异-2） | | |
| 109 | C7-022 | nop-ai-core-nop-ai-agent | MA-05 | P1 | 成员/spawn 超时取消契约 F3 漏修族：5 路径仅 2 实现 cancelSession——僵尸化+RECLAIM 双执行 | ←C7-023/C3-025/C4-014 | | |
| 110 | C5-008 | nop-ai-core-nop-ai-agent | MA-06 | P1 | 长会话归档/spill/journal 无界累积+恢复全量物化 4× 放大 | ←C5-010/011 | | |
| 111 | C3-011 | nop-ai-core-nop-ai-agent | MA-08 | P1 | 超时取消传播断链：进程级 kill 通道仅 heartbeat 一处、cancelCall 契约无实现——幽灵 bash/容器/HTTP 运行 | | | |
| 112 | C3-028 | nop-ai-core-nop-ai-agent | MA-09 | P1 | 方言流式协议族：三站点 tool_call 截断+流式 usage 失活+错误通道缺失——stream=true 主路径丢数据 | ←C3-030/031/032 | | |
| 113 | C3-021 | nop-ai-core-nop-ai-agent | MA-11 | P1 | DBMessageService 单线程 poller 内联消费：join 120s 期间全模块消息冻结+毒消息无 DLQ 停摆 | ←C7-025/C4-CL-040 | | |
| 114 | C7-012 | nop-ai-core-nop-ai-agent | MA-19 | P1 | 接管锁续期异常无限容忍：SQLException 仅 WARN 无限重试——永不自 abort，fencing 失效双执行窗口 | | | |
| 115 | C4-011 | nop-ai-core-nop-ai-agent | C4-011 | P1 | AgentDispatchExecutor 经 runAsync 落 commonPool：阻塞工具体（300s cap）占满 JVM 全局池（与 1.2 C4-004/2.3 C8-001 同族判级） | ←CL-025 | | |
| 116 | C7-001 | nop-ai-core-nop-ai-agent | C7-001 | P1 | 流式聚合无全程期限/静默检测：SSE 中途挂死 future 永不完成且对健康检测不可见 | | | |
| 117 | C7-002 | nop-ai-core-nop-ai-agent | C7-002 | P1 | 流式中途干净断连被 onComplete 误判成功：终止信号未校验——截断内容以成功交付，重试/守卫失效 | | | |
| 118 | C1-001 | nop-ai-toolkit-tools-shell | MA-01 | P1 | ToolManagerImpl 路径串行队列：bin 锁内同步执行+executeNow.get() 抛出时 future 永不完成——排队分支静默悬挂 | ←C4-005 | | |
| 119 | C6-001 | nop-ai-toolkit-tools-shell | MA-02 | P1 | 沙箱 reader 泄漏族：孪生后台子进程持 stdout 管道→reader/fd 永久泄漏+活 StringBuilder 终态竞态 | ←C4-001/C6-008；Host killTree 已修 Docker 未同修 F3 残留 | | |
| 120 | C6-002 | nop-ai-toolkit-tools-shell | MA-03 | P1 | docker 清理族：超时不 destroyForcibly+killContainer 失败被吞+取消句柄已注销——容器/进程/线程无界残留 | ←C8-009 | | |
| 121 | C6-003 | nop-ai-toolkit-tools-shell | MA-04 | P1 | runShortCommand 先无限期 drain 后才 waitFor(5s)：daemon 挂起时清理线程自身永久阻塞 | | | |
| 122 | C4-003 | nop-ai-toolkit-tools-shell | MA-06 | P1（判级分歧暂计，留 7.1） | shell 管道死锁族案：阶段提交 REE 无回滚吞线程+≥1024 行挂死+下游死亡上游阻塞+0<&1 自引用 | ←C8-001/003/011；C4 P1 vs C6/C8 条件化 P2 分叉留 7.1 | | |
| 123 | C2-001 | nop-ai-gateway | MA-07 | P1 | FailoverStreamFlow 取消×重订阅 TOCTOU：acquire 无 release 路径——per-账号计数永久 +1 幽灵，无 gauge | ←C1-008 一行闭合 | | |
| 124 | C1-001 | nop-ai-gateway | C1-001 | P1 | 窗口内失败清理与 request() 冲刷交错——已缓冲内容泄漏进输出或 ArrayDeque 撕裂 onNext(null)（同文件批 A） | 同文件修复批 A | | |
| 125 | C3-001 | nop-ai-gateway | C3-001 | P1 | 终态冲刷受 demand 约束——剩余元素静默丢弃后仍发 onComplete（pendingComplete 为同仓正确先例；批 A） | 同文件修复批 A | | |
| 126 | C8-001 | nop-ai-gateway | C8-001 | P1 | 越窗后零需求缓冲无上界+上游恒 request(MAX)——下游慢/停滞整条补全流无界积压（同文件批 A） | 吸收 CL-001/003/005/012/014；同族对齐 2.3 C3-003/3.1 C5-001 | | |
| 127 | C6-001 | nop-ai-rest | MA-01 | P1 | PgVectorStore schemaReady 实例级旗标 vs per-collection DDL 错配：多 index 第二个起永久 SQL 失败 | 三登记一处修复闭合 | | |
| 128 | C1-001 | nop-ai-rest | C1-001 | P1（条件化保留） | getAllJavaSourceFiles parallelStream 向 ArrayList 并发 add——静默丢文件或 AIOOBE | 零外部消费者条件化保留 P1 | | |
| 129 | C1-003 | nop-ai-rest | C1-003 | P1 | InMemoryVectorStore removeIf+add 复合 upsert 非原子——并发同 docId 重复条目（触发需显式并发 upsert） | | | |
| 130 | C3-005 | nop-ai-rest | C3-005 | P1（升格自 P2） | RagSynthesizeService 不检查 isSuccess——LLM 错误响应静默折叠空串 200 返回（可达性三步闭合） | 家族关联 3.1 C7-002/3.3 C3-001 | | |
| 131 | C3-002+C8-004 | nop-message | MA-02 | P1（判级分歧留 7.1） | Debezium 事件回调无异常屏障+dispatchEvent 吞异常推进 offset——屏障存活/投递两极性 | 同 diff 配对 | | |
| 132 | C6-002 | nop-message | MA-05 | P1 | subscribe 先 put 后启动引擎：启动失败幽灵订阅+拿不到 ICancellable 永不可注销——双投递 | | | |
| 133 | C8-002 | nop-message | C8-002 | P1 | LocalMessageService 消费失败静默丢弃从不派发 onConsumeFailure——三层失败架构 Local 整体断链 | | | |
| 134 | C1-004 | nop-cluster | MA-01 | P1 | 选主 doStop 复活竞态：无锁 stop 与 synchronized 竞交——leaderEpoch 复活+监听器乱序 | ←C4-001/C2-003/C3-004/C2-005/C4-002 六条族 | | |
| 135 | C1-001 | nop-cluster | C1-001 | P1 | Nacos 事件线程原地改写共享 ServiceInstance——撕裂读+_host 惰性缓存固化陈旧端点（12 读者站点） | | | |
| 136 | C1-003 | nop-cluster | C1-003 | P1 | 订阅初始化旧快照无版本守护——事件 B 落在 getAllInstances 与 initInstances 之间即回退新事件态 | | | |
| 137 | C3-001 | nop-cluster | MA-07 | P1 | callAsync 重试回调内同步异常被吞——future 永不完成调用方永久挂起+Sentinel entry 泄漏 | ←C3-008/C8-008 同 diff | | |
| 138 | C1-008+C3-018+C6-020 | nop-network-integration | MA-01 | P1 | MQTT 会话接管订阅抹除：removeConnection 无属主校验——设备重连后下行路由静默失效 | 三登记一工单（C1-008 失效面①被异步 auth 前提证伪修正） | | |
| 139 | C3-002 | nop-network-integration | MA-02 | P1 | JDK 取消语义失效：cancelToken 只绑 headers 阶段——幽灵下载继续写盘、读取线程按次泄漏 | 成员 C3-003+C6-002 | | |
| 140 | C3-004 | nop-network-integration | MA-03 | P1（双判级 apache P1/jdk,okhttp P2，留 7.1） | sidecar 校验和探测同步 get(30s) 跑 apache I/O reactor 回调线程——30s 全连接停摆 | 成员 C6-006 | | |
| 141 | C3-015 | nop-network-integration | MA-04 | P1 | FeishuClient send 无界 get() 跑心跳+重连唯一单线程 scheduler——半开连接即整体死锁 | 成员 C6-024 升格 P1；载体 raw 线程属 2.1 CL-021 点名 | | |
| 142 | C1-009 | nop-network-integration | MA-05 | P1（判级留 7.1） | FeishuClient 重连无单飞/世代守卫：双入口双调度+started 跨代复用——双活/三活 WS | 成员 C3-017 | | |
| 143 | C6-021 | nop-network-integration | MA-06 | P1（判级留 7.1） | FeishuClient.stop() 与在途 attemptReconnect 竞态——stop 后新建 WS 孤儿连接 | 成员 C3-016；同构竞态 C6-022 另立 P2 | | |
| 144 | C1-001 | nop-network-integration | MA-07 | P1 | SimpleRpcClientFactory.init 挂 newInstance 非幂等——拦截器无界累积+定时器泄漏（2.1 CL-024 原始站点） | 成员 C6-014 | | |
| 145 | C3-001 | nop-network-integration | C3-001 | P1 | OkHttp onResponse 异常吞没：断连 IOException 被吞 future 永不完成——同步调用方永久阻塞（future 族第 4 例） | 同文件 doDownloadAsync 已有保护 F3 漏修 | | |
| 146 | C3-005 | nop-network-integration | C3-005 | P1 | 无校验源跳过 verifyAndRename——报成功但目标文件永不产生（错误以成功交付族第 4 例） | 与 C6-001 P0 同批设计；Apache 无条件 move 跨实现分歧 | | |
| 147 | C3-024 | nop-network-integration | C3-024 | P1 | SftpClient 全链路无超时（jsch 默认 0=无限）——网络故障下业务线程与 SSH 会话无限挂起 | | | |
| 148 | C6-004 | nop-network-integration | C6-004 | P1 | SSE 反压无超时阻塞等待——挂起 I/O reactor 线程/JDK readLine 不可中断 cancel 无效 | 与 3.1 C7-001 本体面两侧同批 | | |
| 149 | C6-005 | nop-network-integration | C6-005 | P1 | BASE64_FORM 上传 IoHelper.readBytes 不关闭输入流——三实现共漏 | 与 CL-012 OOM 峰值面同站点不同本体，修复批协同 | | |
| 150 | C6-017 | nop-network-integration | C6-017 | P1 | NettyTcpServer doStart bind 失败不关 boss/worker 事件循环组——每次失败泄漏整组 I/O 线程 | 对照 C6-014 族正确兄弟写法 | | |
| 151 | C6-018 | nop-network-integration | C6-018 | P1 | SftpClient 构造期「session 已连、channel 未开」失败——SSH 会话无人回收 | 与 C3-024 同文件同批 | | |
| 152 | C6-019 | nop-network-integration | C6-019 | P1 | TencentEmailSender 每次 sendEmail 新建 SesClient 从不 shutdown——连接池随发送次数无界累积 | 保护面 C10-004/C10-013 | | |
| 153 | C1-006 | nop-host | MA-01 | P1 | 双宿主启动窗口空链固化：facade 窗口内 filter 全落空链——认证旁路不可自愈 | ←C4-003 启动窗口无就绪门禁 | | |
| 154 | C6-007 | nop-host | MA-02 | P1 | ContextTaskQueue 吞同步异常 future 永不完成——Quarkus 认证请求永久悬挂（future 族第 5 例） | 平台根因绝缘层移交 2.3 CL-018+2.1 C3-001 | | |
| 155 | C6-001 | nop-host | C6-001 | P1 | 上传流「先校验后消费」失败路径永不关闭——每被拒上传泄漏 1 fd，外部可稳定触发 | 随族：C6-008 判级维持 P2 留 7.1 | | |
| 156 | C1-001 | nop-host | C1-001 | P1 | SpringWebProxy 凭据对 Reactor 线程写、请求线程读无 volatile——陈旧 token 重发+撕裂凭据对 | | | |
| 157 | C4-001 | nop-search-runner-autotest | C4-001 | P1 | CliRunCommand scheduleWithFixedDelay Future 丢弃——周期任务任一次异常即静默停摆且进程挂 stdin | | | |
| 158 | MA-01（族主，无 C 号） | nop-auth-credential | MA-01 | P1 | 六族 DB 状态存储惰性删除+零回收：Session/Mfa/Sms/Email/RateLimit/LoginAttempt 单调增长（F4） | 六族清理配对族主 | | |
| 159 | MA-02（族主，无 C 号） | nop-auth-credential | MA-02 | P1 | Redis 六处 INCR+setTimeoutAsync 非原子——TTL 附着失败致 LoginAttemptStore 永久锁号 | Redis TTL 附着族主 | | |
| 160 | MA-01（族主，无 C 号） | nop-sys-dyn | MA-01 | P1 | 过期分布式锁行删除缺 holderId 互斥校验+事件认领租约不续期且完成写无 fencing | DB 行协调原语 fencing 纪律族主，P1 站点×2 | | |
| 161 | I-01 | nop-sys-dyn | I-01 | P1 | 已发布 mergedStore 并发结构修改——TreeMap 撕裂读 | | | |
| 162 | I-02 | nop-sys-dyn | I-02 | P1 | 订阅 cancel×subscribe 竞态孤儿化→消息置 PROCESSED 静默丢弃 | | | |
| 163 | W-01 | nop-file-datav | W-01 | P1 | 文件去重竞态：isUniqueRef 判-删窗口→悬挂引用 | CL-014 | | |
| 164 | W-04 | nop-file-datav | W-04 | P1 | dashboard 主查询路径 rowLimit=null 全量物化无行上界 | CL-009 | | |
| 165 | C5-001 | nop-report-format | C5-001 | P1 | record 反序列化数据驱动长度无界物化：先 new byte[n] 后读——文件/netty 双生产路径 OOM 面 | 同族三处有界保护反衬；5.4 口径「移交 1=C5-001 归 7.1」 | | |
| 166 | W-01 | nop-rule-metadata | W-01 | P1（暂计，C4 侧 P2 分歧留 7.1） | 外部 JDBC 全链无超时——外部源挂起→质量监控静默停摆+checkpoint 永久锁死+调度池锁死 | 族主 C7-102+C7-001 次+C4-R2-001 跨维关联 | | |
| 167 | W-02 | nop-rule-metadata | W-02 | P1 | 7 张 append-only 时序表零保留路径——cron+auto-score 驱动无界增长（F4） | 族主 C7-004+C7-101 | | |
| 168 | W-03 | nop-rule-metadata | W-03 | P1 | 规则热更新失效传播缺口：节点级编辑不触发缓存失效，陈旧规则无限期服务无日志无告警 | C7-R2-001 | | |
| 169 | W-01 | nop-graph-duckdb | W-01 | P1 | OPEN_FILES+connections 账本仅 engine.close() 释放——长驻期每任务净增永不回收（F4）+指纹误拒重开 | 族主 C6-001，吸 C6-R2-001+CS-002+C1-R2-001 | | |
| 170 | W-02 | nop-graph-duckdb | W-02 | P1（判级终裁已定） | open/close 竞态：close() 窗口早退，迟到连接进已关闭引擎——native 句柄+单写者文件锁泄漏 | 族主 C6-002，吸 C1-002 | | |
| 171 | W-03 | nop-graph-duckdb | W-03 | P1 | materialize CTAS 无 table-exists 守卫——失败留孤儿表，持久 dbPath 重试/恢复永久必败（F2+F3） | C6-R2-002 | | |
| 172 | W-04 | nop-graph-duckdb | W-04 | P1 | LeidenDetector runWithTimeout 超时幽灵线程：CWTS 全库零 interrupt，shutdownNow 必然无效 | 族主 CS-001，吸 CS-R2-001+2.1 C6-CL-031 别名 | | |
| 173 | W-01 | nop-tools-parse | W-01 | P1 | NodeTscBridge 重启无代际栅栏：迟到帧竞态——stale EOF 永久不可用/杀健康新进程/孤儿进程（现有测试假保护） | 族主 C6-101，吸 C1-102/CS-001 转引用；挂 C6-103/104 失效面 | | |
| 174 | W-02 | nop-tools-parse | W-02 | P1 | LintDeadlineExecutor.install 每请求写非 volatile 全局槽无卸载——deadline/深度护栏静默失效 | C1-101；现有测试反向钉定全局槽 | | plan-first（Route-B 触 nop-core） |
| 175 | W-01 | nop-code-index | W-01 | P1 | 公开图查询三动作每请求全量物化 4 关系表（自述 Deliberately unbounded）+findByMetric 无 LIMIT | C5-001 | | |
| 176 | W-02 | nop-code-index | W-02 | P1 | ProjectAnalyzer 输入面守卫全失效：MAX_FILE_SIZE/COUNT 死常量+消费端全量累积——全仓 sourceCode 驻留巨事务 | C5-002 | | |
| 177 | W-03 | nop-code-index | W-03 | P1 | 读路径自愈物化无单飞/无退避——并发首触 Leiden 重算风暴（6 触发点+4 守卫空洞） | C7-001；与 5.6 W-04 独立+交叉引用 | | |
| 178 | C1-002 | nop-peripheral | W-01 | P1 | 关闭/死亡路径无拒绝语义：检查-入队非原子+死亡无监督+队列无界——future 永不完成（future 族第 6 例） | 吸 CL-001/002，伴生 CL-201；工厂校验面本体归 2.1 C4-003；C10-001 验收前置 | | |
| 179 | C6-001 | nop-peripheral | W-02 | P1 | IDEA 插件调试 socket 从不关闭——每会话泄漏 1 fd+Disconnect 钉死 1 recv 线程 | C10-002 验收前置；修复参照 TestRemoteDebugger.tearDown 配对 | 验证通道=Gradle/harness 非 ./mvnw [CL-301] | |

## §二 B1 留 7.1 终裁待裁项清单

### B1-A 判级分歧 / 开放裁定（10 项，均已在 P1 表暂计 P1 或登记随族）

| 项 | 来源行/单元 | 分歧双方判级或开放点 | 单元裁定理由原文摘录 |
|----|------------|---------------------|---------------------|
| B1-01 | P1 表第 67 行；nop-core-framework C4-003 | 门控 P1 vs P2 双口径（现暂计 P1） | 「concurrent-start 三复合面：无 join 点+失败吞入+死容器 exit-0 伪装/quarkus 三绿假活（←C4-008；与 C4-009/011+CL-019 同 PR 批次；**门控 P1/P2 双口径留 7.1 裁**）」 |
| B1-02 | P1 表第 93 行 + 2.5 计数口径；nop-persistence-peri MA-02（C8-001 主） | 开放裁定：严重度未定（现暂计 P1） | 计数口径：「**MA-02 开放裁定暂计 P1 留 7.1 终裁**」；行内：「PubSub 消费回调在共享 Redis IO 线程内联执行……（**严重度 7.1 终裁**）」 |
| B1-03 | P1 表第 122 行 + 3.2 计数口径；nop-ai-toolkit-tools-shell MA-06（C4-003 主） | 族内分叉：C4 侧 P1 vs C6/C8 侧条件化 P2 | 计数口径：「**MA-06 判级分歧暂计 P1 留 7.1**」；行内：「shell 管道死锁族案……（←C8-001/003/011；**C4 P1 vs C6/C8 条件化 P2 分叉留 7.1**）」；关联：3.2 六张 shell 零生产接线族条件化降档（C1-002/C6-004/005/C8-001/002/003——投产即回升） |
| B1-04 | 3.2 计数口径块（跨单元）；沙箱族 | 3.1 MA-17 判 P2 vs 3.2 同病实现判 P1 | 3.2 计数口径：「**沙箱族跨单元判级分歧（3.1 MA-17 P2 vs 本单元 P1 两套实现同病）与 shell 族 P1/P2 分叉移交 7.1**」 |
| B1-05 | P1 表第 131 行 + 4.1 计数口径；nop-message MA-02 + 判级三案 | 生产主路径结构性免疫 vs 直接嵌入方受损面（嵌入方口径未裁；三案=MA-02 配对/MA-05 幽灵订阅/C8-002 Local 断链） | 计数口径：「MA-02 Debezium 屏障两极性同 diff 配对/MA-05 幽灵订阅/C8-002 Local 失败断链——**后两类生产主路径结构性免疫标注、嵌入方受损面判级分歧登记 7.1**」；「**判级三案嵌入方口径留 7.1**」；行内：「屏障存活/投递两极性……（**判级分歧留 7.1**）」 |
| B1-06 | P1 表第 140 行；nop-network-integration MA-03（C3-004 主） | 双判级：apache P1 vs jdk,okhttp P2 | 行内：「sidecar 校验和探测同步 get(30s) 跑 apache I/O reactor 回调线程……（成员 C6-006；**双判级 apache P1/jdk,okhttp P2 留 7.1**）」 |
| B1-07 | P1 表第 142 行；nop-network-integration MA-05（C1-009 主） | 判级待裁（成员 C3-017） | 行内：「FeishuClient 重连无单飞/世代守卫……双活/三活 WS（成员 C3-017；**判级留 7.1**）」 |
| B1-08 | P1 表第 143 行；nop-network-integration MA-06（C6-021 主） | 判级待裁（成员 C3-016；同构 C6-022 已另立 P2） | 行内：「FeishuClient.stop() 与在途 attemptReconnect 竞态……stop 后新建 WS 孤儿连接（成员 C3-016；**判级留 7.1**；同构竞态 NettyTcpClient C6-022 另立 P2）」 |
| B1-09 | P1 表第 155 行行内随族注记；nop-host C6-008 | C6-008 判级维持 P2，随族（C6-001 上传流族）留 7.1 | 行内：「上传流『先校验后消费』失败路径永不关闭……（**C6-008 判级维持 P2 随族留 7.1**）」 |
| B1-10 | P1 表第 166 行 + 5.5 计数口径；nop-rule-metadata W-01（超时族） | C7 侧暂计 P1 vs C4 侧 P2 | 计数口径：「W-01 外部 JDBC 无超时族[C7-102 主+C7-001 次+C4-R2-001 跨维关联，**判级暂计 P1、C4 侧 P2 分歧登记 7.1**]」；行内：「……（判级暂计 P1，C4 侧 P2 分歧登记 7.1）」 |

### B1-B 条件化工单（2 组，携激活回升条款）

| 项 | 来源单元 | 条件/激活条款 | 单元裁定理由原文摘录 |
|----|---------|--------------|---------------------|
| B1-11 | 3.3 nop-ai-gateway：渠道栈休眠条件化潮（13 张条件化工单，未入 P1 表） | 激活条款：startAll/stopAll/dispatchInbound 出现非测试调用或 beans.xml 加 init/destroy 即回升；回升构成 P1×6/P2×7 | 3.3 计数口径：「**渠道栈休眠条件化潮**：startAll/stopAll/dispatchInbound 全仓非测试零调用、beans.xml 无 init/destroy——C4 全部 P1 条件化挂起、C6-004 链主 P1→P2 等，**13 张条件化工单携激活回升条款回升 P1×6/P2×7**」 |
| B1-12 | 3.4 nop-ai-rest：四张条件化 P2 | 4 张条件化 P2 的裁决权留 7.1 | 3.4 计数口径：「P0×0 显式空执行/P1×4/P2×6/P3×5；**4 张条件化 P2 裁决留 7.1**」 |

### B1-C 其他 7.1 留档/宣读项（非判级分歧，登记备查）

| 项 | 来源 | 内容 | 原文摘录 |
|----|------|------|---------|
| S1 | 6.4 计数口径 + 6.4 CL 小节 | CL-001/002/102 域留档 7.1 聚类候选 | 「CL-001/002/102 域留档 7.1 聚类候选」 |
| S2 | 6.4 计数口径 | 反向约束注记供 7.1 宣读（6.3 W-02 引入缓存须先满足 6.4 W-01 测试先置） | 「反向约束注记（6.3 W-02 引入缓存须先满足本单元 W-01 测试先置）供 7.1 宣读」 |
| S3 | 6.4 CL 小节 | CL-101 watch-only 附缓存化即升 P1 条款 | 「CL-101 watch-only 附缓存化即升 P1 条款」 |
| S4 | 5.4 计数口径 | C5-001 移交归 7.1（正常 P1 批处理移交，非判级分歧；已在 P1 表第 165 行） | 「25 复核保留（维持 25/跨维度调整 0/**移交 1=C5-001 归 7.1**）」 |
| S5 | 6.5 计数口径 + CL-301 | idea-plugin Gradle 构建隔离=测试门禁盲区：4 站点修复验证不得依赖 ./mvnw test（6.5 两张 P1 行的验证通道约束来源） | 「idea-plugin Gradle 构建隔离=测试门禁盲区[CL-301，**4 站点修复验证不得依赖 ./mvnw test**]」 |
| S6 | 3.2 计数口径 | shell 零生产接线族 6 张条件化降档（投产即回升），挂 B1-03 一并裁 | 「shell 零生产接线族条件化：C1-002/C6-004/005/C8-001/002/003——**投产即回升**」 |
| S7 | 5.6 计数口径 | 已裁项备查（不入待裁清单）：5.6 W-02 判级终裁 P1 已定 | 「W-02 竞态族[C6-002 吸 C1-002，**判级终裁 P1**]」 |

## §三 数字核对

### 按单元计数（报告清单 P1 列 vs P1 表行数）

| 单元 | 报告清单 P1 | P1 表行数 | 差 | | 单元 | 报告清单 P1 | P1 表行数 | 差 |
|------|-----------|----------|----|---|------|-----------|----------|----|
| 1.1 nop-stream-core-runtime | 10 | 10 | 0 | | 4.1 nop-message | 3 | 3 | 0 |
| 1.2 nop-stream-state-connectors | 10 | 10 | 0 | | 4.2 nop-cluster | 4 | 4 | 0 |
| 1.3 nop-stream-orchestration-thin | 4 | 4 | 0 | | 4.3 nop-network-integration | 15 | 15 | 0 |
| 1.4 nop-job | 2 | 2 | 0 | | 4.4 nop-host | 4 | 4 | 0 |
| 1.5 nop-wf-nop-task | 7 | 7 | 0 | | 4.5 nop-search-runner-autotest | 1 | 1 | 0 |
| 1.6 nop-retry-nop-tcc | 14 | 14 | 0 | | 5.1 nop-auth-credential | 2 | 2 | 0 |
| 1.7 nop-batch | 8 | 8 | 0 | | 5.2 nop-sys-dyn | 3 | 3 | 0 |
| 2.1 nop-kernel-concurrency | 5 | 5 | 0 | | 5.3 nop-file-datav | 2 | 2 | 0 |
| 2.2 nop-core-framework | 11 | 11 | 0 | | 5.4 nop-report-format | 1 | 1 | 0 |
| 2.3 nop-service-framework-hotpath | 7 | 7 | 0 | | 5.5 nop-rule-metadata | 3 | 3 | 0 |
| 2.4 nop-persistence-rw | 13 | 13 | 0 | | 5.6 nop-graph-duckdb | 4 | 4 | 0 |
| 2.5 nop-persistence-peri | 13 | 13 | 0 | | 6.1 nop-tools-parallel | 0 | 0 | 0 |
| 3.1 nop-ai-core-nop-ai-agent | 13 | 13 | 0 | | 6.2 nop-tools-parse | 2 | 2 | 0 |
| 3.2 nop-ai-toolkit-tools-shell | 5 | 5 | 0 | | 6.3 nop-code-index | 3 | 3 | 0 |
| 3.3 nop-ai-gateway | 4 | 4 | 0 | | 6.4 nop-code-rest | 0 | 0 | 0 |
| 3.4 nop-ai-rest | 4 | 4 | 0 | | 6.5 nop-peripheral | 2 | 2 | 0 |

**合计：报告清单 P1 列求和 = 179；P1 表数据行 = 179；差 = 0。** 零 P1 单元 2 个（6.1、6.4），表中无行，一致。

### 口径注记（计数口径块 vs 表行数，均无冲突）

- 2.4：「P1 台账 17→有效工单 13」——表 13 行为**有效工单口径**（17 为合并前台账数），与报告清单 13 一致。
- 2.1：「P1 9→5」、1.6：「P1 19→14」、2.3：「存量 12−6 降级+1 升格→7」——表行数均为合并后口径，一致。
- 5.5：工单级 P1×3 vs 实体级 P1×4——表为工单级口径（3 行）。6.5：工单级=实体级=P1×2，一致。
- 3.1 计数口径中「P1×30」指 C10 缺保护侧（假保护 23+无测试 7），与 P1 工单 13 不冲突。
- 2.5：MA-02 开放裁定暂计 P1（→B1-02），表计入 13 行口径一致。

### 异常登记

- **异-1 表序断裂**：4.3 的 15 行被 4.4/4.5/5.1 的 7 行（表第 153–159 行区间）隔断为两段（MA-01~MA-07 一段、C3-001~C6-019 一段）。无重复行，仅排序非连续。
- **异-2 吸收成员自引用**：3.1 C4-010（MA-04，表第 108 行）行内「←C4-010/C7-032 三重登记统一 P1」含自引 C4-010，疑站点号复用或笔误；吸收成员按 C7-032 提取。
- **异-3 Finding ID 格式偏离使用说明**：使用说明规定格式 `{单元标识}-C{N}-{序号}`；实际 5.1（MA-01/MA-02）、5.2（MA-01/I-01/I-02）以族号/独立工单号直接作 Finding ID（无 C 号）；1.1 用 FAM-1；6.2/6.3/6.5 以 W-xx 工单号为主 ID 附 C 号。提取表已在 Finding ID 列注明。
- **异-4 复合 ID 行**：4.1「C3-002+C8-004」（同 diff 配对）与 4.3「C1-008+C3-018+C6-020」（三登记一工单）为多 finding 单工单；本表行数按**工单行**计（与源表一致），非按 finding 个数计。
- **异-5 移交仍留表**：2.3 C9-012 标注「移交功能正确性维度」，修复状态 open（接收轨落名），仍保留在 P1 批量表并计入 179——7.1 需明确其接收轨落名后是否移出批量修复面。
- **异-6 条件化工单未列工单号**：3.3 十三张条件化工单、3.4 四张条件化 P2 在 cra-index 仅给计数未逐张列工单号（B1-11/B1-12），7.1 展开时须回各报告目录 merge-adjudication 取明细——信息缺口登记。
- **异-7 跨单元引用非 P1 行**：nop-job C4-003（1.6 C8-001 行关联）、WI 1.7 C4-003↔2.1 C4-003（双登记处置归 2.1）、6.5 CL-203→WI 2.1 C4-003 等引用目标在 P1 表中无对应行——均为 P2/设施面登记于他处或 cross-ref 关闭，非悬空错误，备查。
- **异-8 已裁项混存**：5.6 W-02「判级终裁 P1」为已裁定案（S7），不属于待裁项；提取时与 7.1 待裁项严格区分。
