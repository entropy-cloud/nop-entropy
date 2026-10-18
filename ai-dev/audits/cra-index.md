# CRA 审计-修复报告索引（concurrency-resource-audit）

> Campaign：全模块并发·异步·资源专项审计与修复（C1–C10 口径首次全模块滚动）
> Roadmap：`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md`
> 启动时间：2026-10-14
> 绿色基线 commit：b3a9c76bed（`./mvnw clean install -T 1C` BUILD SUCCESS，2026-10-14）

本索引是 CRA campaign 全部审计报告与发现台账的唯一入口，是 roadmap 9.1 对账的输入。纪律：报告产出即更新本索引；修复完成即回填；P0 走即时通道（发现即修，不入批量修复）；P2 逐项裁决后回填。

## 报告清单

| 报告目录 | 工作项 | 审计单元 | 维度 | P0 数 | P1 数 | P2 数 | P3 数 | 状态 |
|---------|--------|---------|------|-------|-------|-------|-------|------|
| `2026-10/2026-10-14-0130-deep-audit-nop-stream-core-runtime-concurrency` | 1.1 | nop-stream-core-runtime | C1–C5 C7–C10 | 0 | 10 | 27+25（含 C10 缺保护登记） | 24 | done（closure audit PASS，plan 2461） |
| `2026-10/2026-10-14-0330-deep-audit-nop-stream-state-connectors-concurrency` | 1.2 | nop-stream-state-connectors | C1–C8 C10 | 1 | 10 | 26+16（含 C10 缺保护登记） | 9 | done（closure audit PASS，plan 2462） |
| `2026-10/2026-10-14-1440-deep-audit-nop-stream-orchestration-thin-concurrency` | 1.3 | nop-stream-orchestration-thin | C1 C3 C6 全维 + C2/C4/C5/C7/C8/C9 抽查 + C10 | 0 | 4 | 10+8（含 C10 缺保护登记） | 6 | done（closure audit PASS，plan 2463） |
| `2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency` | 1.4 | nop-job | C1 C2 C4 C5 C7 + C10 | 0 | 2 | 23+9（含 C10 缺保护登记） | 9 | done（closure audit PASS，plan 2464） |
| `2026-10/2026-10-15-0930-deep-audit-nop-wf-nop-task-concurrency` | 1.5 | nop-wf-nop-task | C1–C5 C7 C8 + C10 | 0 | 7 | 29+14（含 C10 缺保护登记） | 18 | done（closure audit PASS，plan 2465） |
| `2026-10/2026-10-15-1400-deep-audit-nop-retry-nop-tcc-concurrency` | 1.6 | nop-retry-nop-tcc | C1 C3 C6 C7 C8 + C10（C2/C4/C5/C9 线索 overlay） | 0 | 14 | 22+10（含 C10 缺保护登记） | 14 | done（closure audit PASS，plan 2466） |
| `2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency` | 1.7 | nop-batch | C1 C5 C6 C7 C8 + C10（C2/C3/C4/C9 线索 overlay） | 0 | 8 | 26+12（含 C10 缺保护登记） | 17 | done（closure audit PASS，plan 2467） |

> 1.5 计数口径：合并裁定后 54 个工作项（23 缺陷族 MA-01~23 + 31 独立）；P0=0（即时通道显式空执行）；C10 缺保护 14 条单列；与 WI 1.1–1.4 台账零重复立项，跨单元关联 3 处单列（nop-job-C7-006、2026-09 P2-3.1-001、AU-10 同案对齐）。

> 1.6 计数口径：合并裁定后 50 条（14 并组吸收 24 条 + 独立条目；P1 19→14、P2 38→22、P3 17→14）；P0=0（即时通道显式空执行）；C10 缺保护 10 条单列；维度集外线索 CL-001~007 全部确认（见「维度集外线索关联」节，不计入 74）；与 WI 1.1–1.5 台账零重复立项，跨单元关联 2 处单列（C8-001↔nop-job C4-003、CL-006 留 rpc/资源单元）。

> 1.7 计数口径：合并裁定后 51 条（53 保留全含 1 降级；并入 2 条 C5-005→C8-001、C7-002→C6-001/C6-002；P1×8/P2×26/P3×17）；P0=0（即时通道显式空执行）；C10 缺保护 12 条单列；CL 编号 39 条、驳回 4（CL-007/009/015/038）、确认台账 35 条（见「维度集外线索关联」节 nop-batch 小节，不计入 51）；关键事实修正：C8-001 ORM 子面「LIMIT 0 静默空任务」证伪→全表加载挂死（挂死面扩大）；与 WI 1.1–1.6 台账零重复立项，跨单元关联 5 处单列（三表零保留族/扫描守卫停摆族/平台池占用族/零生产装配对照/CL-006 留 rpc 单元）。
| `2026-10/2026-10-16-2316-deep-audit-nop-kernel-concurrency-concurrency` | 2.1 | nop-kernel-concurrency | C1–C7 C9 + C10（C8 线索 overlay） | 0 | 5 | 63+14（含 C10 缺保护登记） | 28 | done（closure audit PASS，plan 2468） |
| `2026-10/2026-10-17-1725-deep-audit-nop-core-framework-concurrency` | 2.2 | nop-core-framework | C1 C2 C4 C6 + C10（C3/C5/C7/C8/C9 线索 overlay） | 0 | 11 | 21+18（含 C10 缺保护登记） | 13 | done（closure audit PASS，plan 2469） |
| `2026-10/2026-10-17-2353-deep-audit-nop-service-framework-hotpath-concurrency` | 2.3 | nop-service-framework-hotpath | C1 C2 C3 C4 C8 C9 + C10（C5/C6/C7 线索 overlay） | 0 | 7 | 39+17（含 C10 缺保护登记） | 12 | done（closure audit PASS，plan 2470） |

> 2.3 计数口径：合并裁定后 59 条（64 初审+深挖 → 63 复核保留全含降级 9（P1×6 + P2→P3×3）+ 1 撤案（C2-006 转正面样例）→ 并入 4 条 + 跨单元并入 1（C9-002→2.1 C9-010）；P1 存量 12（初审 10 + 深挖 2）−6 降级（C1-001/C2-004/C3-001/005/013/C4-003 全有实测或部署面证据）+1 升格（C9-012）→ 7；P1×7/P2×39/P3×12 + 量化记录 1；本单元承担修复 58）；P0=0（即时通道显式空执行）；C10 缺保护 17 条单列；CL 编号段 25 实占 24（CL-021 空号勘误）：升格 3（CL-003→C8-005、CL-010→C9-005、CL-015→C9-012）、并入 5、驳回 1（CL-011）、纯移交（CL-016/017/022/023/025）；跨单元映射：C9-012 移交功能正确性维度（接收轨落名）、C9-002→2.1 C9-010 同链第三面、C1-010 Dao flush 修复归 nop-auth-service 单元、CL-018→nop-kernel（ContextTaskQueue 承重原语）、CL-025 平台原语前置（C3-010/C4-003 修复先决）。

> 2.2 计数口径：合并裁定后 45 条（63 初审+深挖 → 59 复核保留 + 4 驳回 → 并入 14 条；P1×11/P2×21/P3×13）；P0=0（即时通道显式空执行）；C10 缺保护 18 条单列；CL 编号 21 条（升格 1→C1-021、落格 2、同位点并入设施 2、零立案 1、关联保留 8、纯移交 7；见「维度集外线索关联」节 2.2 小节）；跨单元映射表 11 行（C1-007 族案↔2.1 C1-001 刷新链上下游、C1-021 吸收进 2.1 C3-001 平台方案、CL-008/020 同位点主处置在 2.1、C6-010 三重登记设施归 2.1、Jdk21CommonsCoreInitializer 注册链为 2.1 C6-002 可达性终审输入）；C2-001 与历史 check 审计 P2-6 同源（本轮 P1 裁定留档）。

> 2.1 计数口径：合并裁定后 96 条（120 初审+深挖 → 118 复核保留含 7 降级 + 2 驳回 → 并入 22 条；P1 9→5、P2×63、P3×28）；P0=0（即时通道显式空执行）；C10 缺保护 14 条单列；CL 编号 36 条、驳回 1（C1-CL-001）、确认台账 35（含 11 别名归并、4 移交 nop-graph/nop-stream/rpc/api-core；见「维度集外线索关联」节 2.1 小节）；**本单元为唯一设施本体审计**——跨单元映射表 15 行（使用方↔设施两面，非重复立项）见其 merge-adjudication.md §五；1 处跨单元双登记（WI 1.7 C4-003↔2.1 C4-003 同设施同面，处置归 2.1，1.7 侧 cross-ref 关闭）；api-core 移交包 5 项单列（Protected Area 另行裁定）。

> 1.4 计数口径：合并裁定后 34 项（7 缺陷族 MA-01~07 + 27 独立）；P0=0（即时通道显式空执行）；C10 缺保护 9 条单列；与 WI 1.1–1.3 台账零重复立项，跨单元关联 3 条单列。

> 1.3 计数口径：合并裁定后 20 项（6 缺陷族 MA-01~06 + 14 独立）；P0=0（即时通道显式空执行）；C10 缺保护 8 条单列；与 WI 1.1/1.2 台账零重复立项，4 条跨单元关联单列。

## P0 发现追踪（即时通道）

> P0 永不进入 Phase 8 批量修复：发现即就地修复（审计 plan 内加修复 Phase）或异步注入独立修复 plan。

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| nop-stream-state-connectors-C6-001（MA-01，成员 C3-001/C4-001/C7-002） | 1.2 | KafkaTwoPhaseCommitSink transactional producer 全终止路径（DRAIN/CANCEL/FAILED）无 close——每子任务每次作业终止泄漏 producer+网络线程+缓冲池 | plan 2462 Phase 7 就地修复：implements AutoCloseable + 幂等 close()（abort 开启事务 → producer.close(30s)），经 StreamSinkOperator.close:175 钩子全终态触发；回归测试 3 个（TestKafkaTwoPhaseCommitSinkLifecycle）；`./mvnw test -pl nop-stream -am` BUILD SUCCESS | 已修复（commit 8dcfa4cd0c） |
| （1.1/1.3/1.4/1.5/1.6/1.7/2.1/2.2/2.3 单元无 P0 发现，已按各 plan Phase 7 显式空执行记录） | | | | |

## P1 发现汇总（待 Phase 8 批量修复）

> 7.1 合并裁定后按批次展开；每条 P1 必须在 9.1 对账前变为「已修 / deferred+successor」。

| Finding ID | 报告 | 单元 | 一句话描述 | 裁定批次 | 修复状态 |
|-----------|------|------|-----------|---------|---------|
| C1-001 | 1.1 | nop-stream-core-runtime | SplitEnumerator 被任务线程无锁直调，快照撕裂/双份分配（←C2-001、C3-001） | 待 7.1 | open |
| C1-006 | 1.1 | nop-stream-core-runtime | 任务分配账本只增不删（removeTaskAssignment 零调用方；←C7-002） | 待 7.1 | open |
| C4-001 | 1.1 | nop-stream-core-runtime | LOCAL 执行固定池=核数+有界输出队列互等死锁（←C8-013） | 待 7.1 | open |
| C4-002 | 1.1 | nop-stream-core-runtime | CachingTableLookup 刷新池无界+无 in-flight 去重（←C3-002、C8-014） | 待 7.1 | open |
| C4-009 | 1.1 | nop-stream-core-runtime | OpsJobManager.submit 失败窗口孤儿 coordinator+同 jobId 双活 | 待 7.1 | open |
| C5-010 | 1.1 | nop-stream-core-runtime | PollingJdbcMessageService 无 LIMIT 全量物化 backlog OOM（←C8-002） | 待 7.1 | open |
| C7-011 | 1.1 | nop-stream-core-runtime | standby 降级不取消在途 checkpoint persist，僵尸第二写者 | 待 7.1 | open |
| C8-003 | 1.1 | nop-stream-core-runtime | msg_queue INSERT-only 无界账本+WI6 门控不可达（←C7-003） | 待 7.1 | open |
| C8-011 | 1.1 | nop-stream-core-runtime | launch 共享 2 线程 poller：数据面积压垄断→控制面饥饿（←C4-004） | 待 7.1 | open |
| FAM-1 | 1.1 | nop-stream-core-runtime | materialization store 无界+overflow-bypass 背压旁路（C5-001 主←C7-001、C8-001） | 待 7.1 | open |
| C1-002（MA-02） | 1.2 | nop-stream-state-connectors | RocksDB gauge 锁外取 dbRef→native UAF（SIGSEGV 窗口；←C2-001、C6-004） | 待 7.1 | open |
| C1-001（MA-03） | 1.2 | nop-stream-state-connectors | CEP copyForSubtask 引用透传共享：NFA 条件图/ctx 槽位/udf 生命周期三面（←C1-005、C1-006；C1-001 已知未修复←r4 R5-CEP-05） | 待 7.1 | open |
| C2-002（MA-04） | 1.2 | nop-stream-state-connectors | Avro registry 无请求超时 × computeIfAbsent 锁内 HTTP（挂死+锁毒化；←C3-003、C4-003、C6-008、C8-004、C7-006①） | 待 7.1 | open |
| C3-002（MA-05） | 1.2 | nop-stream-state-connectors | 2PC 持久化登记无保留策略：JDBC 台账只增不删 + file manifest O(E) 放大（←C5-006、C7-001、C8-007、C7-007①） | 待 7.1 | open |
| C4-004（MA-06） | 1.2 | nop-stream-state-connectors | Debezium 引擎无限阻塞任务常驻平台共享 globalWorker 池（←C3-009） | 待 7.1 | open |
| C3-014（MA-07） | 1.2 | nop-stream-state-connectors | Debezium 停止/启动契约无终结保证：幽灵引擎/重启交叠/排队窗口三面（←C4-005、C4-006） | 待 7.1 | open |
| C5-002（MA-08） | 1.2 | nop-stream-state-connectors | RocksDB 默认全量 JSON checkpoint 整库物化进堆（←C8-005） | 待 7.1 | open |
| C3-004 | 1.2 | nop-stream-state-connectors | Debezium 枚举器恢复双登记→同表双引擎重复消费 | 待 7.1 | open |
| C8-001 | 1.2 | nop-stream-state-connectors | Debezium reader checkpoint offset 领先已发射→恢复静默丢 ≤256 条/检查点 | 待 7.1 | open |
| C8-006 | 1.2 | nop-stream-state-connectors | Kafka 2PC 事务存活期与 checkpoint 间隔零裕度→触界失败循环 | 待 7.1 | open |
| C1-001（MA-01） | 1.3 | nop-stream-orchestration-thin | UDF 注册表非线程安全：逐记录 call() 无锁读 × 公开 register() 任意线程写 + 运行期静默换绑（←C3-002、C2-001；含 sql.md §3a docs bug） | 待 7.1 | open |
| C3-008 | 1.3 | nop-stream-orchestration-thin | region 重启 Phase-3 流水线下兄弟 initializeState REPLACE 清零已推进游标→恢复重放已反映窗口（编排面关联 runtime） | 待 7.1 | open |
| C3-009 | 1.3 | nop-stream-orchestration-thin | 共享 UDF × 各子任务 open() 覆写共享 RuntimeContext→并行 ≥2 键控状态收敛单一 backend（根因载体 core ProcessOperator，跨单元关联） | 待 7.1 | open |
| C6-007 | 1.3 | nop-stream-orchestration-thin | cancelled volatile 跨 run 永不复位→区域重启共享实例零事件静默 EOS 假成功 | 待 7.1 | open |
| C4-001（MA-01，成员 C5-001/C7-001/C5-005） | 1.4 | nop-job | rpcPoll 条目无终结驱动：注册表/句柄永久泄漏+无退避重试风暴+WARN 放大+零观测（修复须同盖失败预算/deadline 兜底/DB 回传/PollEntry 精简/gauge 五面） | 待 7.1 | open |
| C2-001 | 1.4 | nop-job | per-job 监视器内调用外部 invoker：管理面/停机无限期阻塞+可构造 ABBA 死锁 | 待 7.1 | open |
| C1-001（MA-01，成员 C2-001/C7-016） | 1.5 | nop-wf-nop-task | task SUSPENDED 转移游离监视器 × cancel 竞态（kill 静默丢失/KILLED 后复活双向） | 待 7.1 | open |
| C3-001（MA-02，五站点族） | 1.5 | nop-wf-nop-task | wf 跨请求快照竞态：组迁移丢失/终结滞留/join 双实例/action 双跑/子流回滚 | 待 7.1 | open |
| C3-002（MA-03，成员 C4-002/C2-010） | 1.5 | nop-wf-nop-task | sync+executor 同池嵌套死锁与无界 sync 等待 | 待 7.1 | open |
| C4-001（MA-04，成员 C4-008/C3-013） | 1.5 | nop-wf-nop-task | retry/delay 业务体+saveState 簿记恒跑共享 globalTimer（4 线程），全平台定时语义失准 | 待 7.1 | open |
| C5-001（MA-05，成员 C8-001） | 1.5 | nop-wf-nop-task | 到期/提醒扫描无 LIMIT 全量载入+毒步骤不收敛 | 待 7.1 | open |
| C5-003（MA-08，成员 C8-004） | 1.5 | nop-wf-nop-task | 全局闸门强引用注册表 × 动态 keyExpr 键空间持久无界（已知未修复 plan 364 之上；owner 表态出口） | 待 7.1 | open |
| C7-001（MA-23，成员 C1-005） | 1.5 | nop-wf-nop-task | WfTaskScanner 多节点/重叠扫描无 fencing：due 双跑副作用先于守卫+remindCount 丢失更新 | 待 7.1 | open |
| C8-012（←C6-001/C1-008/C1-003A） | 1.6 | nop-retry-nop-tcc | executeImmediateRetry 返回 future ≠ 回调挂接 future，同步 DB 异常下扫描器 join 永久冻结、零日志（C10-001 覆盖） | 待 7.1 | open |
| C1-001（←C3-001） | 1.6 | nop-retry-nop-tcc | TCC 全局/分支状态 CAS 失败不阻断补偿 RPC，守卫基于私有过期内存副本 | 待 7.1 | open |
| C3-002（←C3-016） | 1.6 | nop-retry-nop-tcc | 分支级 CAS 失败（0 行/异常）不回写内存，聚合折叠 SUCCESS→全局误报终态 | 待 7.1 | open |
| C3-003（←C1-009） | 1.6 | nop-retry-nop-tcc | retry 调用链全程不续租，租约到期扫描器接管在途任务双执行 | 待 7.1 | open |
| C3-005 | 1.6 | nop-retry-nop-tcc | OVERWRITE 删行不取消在途异步链：幽灵任务与双执行 | 待 7.1 | open |
| C3-006（←C6-016） | 1.6 | nop-retry-nop-tcc | doExecute 簿记写内联结果回调，簿记异常覆盖业务结果→成功改判失败并重试 | 待 7.1 | open |
| C3-012 | 1.6 | nop-retry-nop-tcc | 全局首跳 CAS 异常→分支零驱动→聚合折叠 SUCCESS→陈旧 from 直写终态 | 待 7.1 | open |
| C6-002 | 1.6 | nop-retry-nop-tcc | 崩溃/挂死后 RUNNING attempt 永久残留，UK 碰撞将记录永久砖化 | 待 7.1 | open |
| C6-003（←C8-006） | 1.6 | nop-retry-nop-tcc | OVERWRITE delete+insert 非原子：半个成功后任务蒸发且无死信 | 待 7.1 | open |
| C7-001（←C6-004） | 1.6 | nop-retry-nop-tcc | retry 三张账本表零保留机制，成功即沉淀永久行 | 待 7.1 | open |
| C7-003 | 1.6 | nop-retry-nop-tcc | 解析/同步异常路径产生永不终止僵尸记录 + 每租约周期全栈 ERROR 放大 | 待 7.1 | open |
| C7-005 | 1.6 | nop-retry-nop-tcc | tcc 账本清理 API 零生产调用方 + 耗尽行双重永久排除 + 全表零索引 | 待 7.1 | open |
| C7-012 | 1.6 | nop-retry-nop-tcc | 扫描器只服务单一 namespace：非 default 记录永久 PENDING、零日志零接管（条件面：default-only 部署不触发） | 待 7.1 | open |
| C8-001（←C7-004/C6-014） | 1.6 | nop-retry-nop-tcc | 扫描循环被拒/Error 即（近）静默永久停摆：无自防御、无观测、无重臂（跨单元关联 nop-job C4-003，修复须平台级统一） | 待 7.1 | open |
| C8-001（←C5-005） | 1.7 | nop-batch | dispatch 容量全链零护栏：loadBatchSize≤0 → orm-reader 全表加载 Semaphore(0) 永久挂死 / jdbc-file 族静默空任务丢数（参数护栏矩阵三层完整者 0；修复先于 C8-009） | 待 7.1 | open |
| C8-002（←CL-008） | 1.7 | nop-batch | ExportDbTool 缺省 batchSize=0 静默钳位 1 每行一 chunk：日志/状态文件写放大磁盘耗尽（与 Import `>0` 守卫 F3 不对称；biz 链 batchSize=0 穿透同批 C8-010） | 待 7.1 | open |
| C5-001 | 1.7 | nop-batch | excel-reader beforeRead 全 sheet 物化 List：50 万行 1.5–3GB，maxCountExpr 接线禁用且先物化后截断无兜底（C10-011 补回归钉义务） | 待 7.1 | open |
| C5-002（←CL-038 事实） | 1.7 | nop-batch | jdbc-reader fetchSize 死配置：门条件恒 false + 非 MySQL dialect 无 streamingFetchSize + autocommit 未关 → GB 级全量缓冲（修复须同步改写测试 N2） | 待 7.1 | open |
| C6-001（←C7-002B/CL-026/CL-024 验证断言） | 1.7 | nop-batch | executeChunkLoop 仅 catch Exception：Error 逃逸 future 永不完成 → 资源永不关闭+任务行永久 RUNNING+调用方挂死（修复必须与 C6-010 同 diff：try 上移+catch Throwable） | 待 7.1 | open |
| C6-002（←C7-002A） | 1.7 | nop-batch | executor.execute REE 裸逃逸：句柄泄漏+永久 RUNNING（注入有界池饱和/停机竞态；默认装配不可达已裁定） | 待 7.1 | open |
| C7-001 | 1.7 | nop-batch | 任务行永久 RUNNING 死区：tryMarkTaskRunning 无 stale 判定无 fencing 无专用运维端点，cron 每 5s 错误风暴（跨单元关联 WI 1.5 C7-001/WI 1.6 C8-001 平台级 fencing 对齐；修复前须参数化改写测试 N1） | 待 7.1 | open |
| C7-003 | 1.7 | nop-batch | 三张账本表零保留：清理代码完全不存在（非零装配），UUID taskKey 每触发 +1 行单调增长（跨单元关联 WI 1.6 C7-001 账本保留族，Phase 8 批次对齐） | 待 7.1 | open |
| nop-kernel-concurrency-C1-001 | 2.1 | nop-kernel-concurrency | RoundRobinSupplier.resize 收缩路径锁外关闭旧数组尾段：并发 get 取得已关连接（Lettuce 生产链+IoC 刷新派发链可达；退役代际族吸收 C2-021） | 待 7.1 | open |
| nop-kernel-concurrency-C2-001 | 2.1 | nop-kernel-concurrency | ResourceCacheEntry 持 entry 监视器加载模型：跨 entry 嵌套监视器循环依赖死锁（C2-017 同变更集绑定；单线程循环 StackOverflow 新面） | 待 7.1 | open |
| nop-kernel-concurrency-C3-001 | 2.1 | nop-kernel-concurrency | 周期任务设施无统一异常绝缘层：一次 Throwable 静默永久停摆（29 处生产站点；30 点绝缘状态表；与 WI 1.6 C8-001 平台级统一） | 待 7.1 | open |
| nop-kernel-concurrency-C4-001 | 2.1 | nop-kernel-concurrency | globalTimer 死配置（newScheduledExecutor 副作用改写 maxPoolSize=0）+4 线程全平台共享定时器阻塞放大（28 消费方分类终表；与 WI 1.5 MA-04 隔离方案统一） | 待 7.1 | open |
| nop-kernel-concurrency-C7-007 | 2.1 | nop-kernel-concurrency | 模型缓存 check-changed 双漏检窗口（旧内容+新 mtime 固化；ResourceTimestampCache 死代码；与 C9-001/C9-010 同链三面绑定单点修复） | 待 7.1 | open |
| nop-core-framework-C1-007 | 2.2 | nop-core-framework | 配置发布协议三字段裸写裸读+两步写序撕裂（族案含 C1-017；api-core 单一不可变 holder 修复，7 写点×8 读站；WI 2.1 C1-001 刷新链上游） | 待 7.1 | open |
| nop-core-framework-C1-009 | 2.2 | nop-core-framework | JdbcConfigSource.close 竞态：停机后在途刷新重建永不关闭的连接（←C4-002 同位点并案） | 待 7.1 | open |
| nop-core-framework-C1-010 | 2.2 | nop-core-framework | 插件 unload×reconcile 交错活容器永久泄漏（含 C1-011/012 并案、←C2-004；lifecycleLock 全迁移统一修复批次） | 待 7.1 | open |
| nop-core-framework-C1-021 | 2.2 | nop-core-framework | tryTriggerChange 在 try 外：回调异常永久静默杀死 DB 配置热更新全链（CL-001 终裁升格；WI 2.1 C3-001 平台绝缘层吸收） | 待 7.1 | open |
| nop-core-framework-C2-001 | 2.2 | nop-core-framework | getBean0 锁序环依赖边不可见跨线程永久死锁（←C2-012；allow-cycle 默认 true 附注；历史 check 审计 P2-6 同源回访升 P1） | 待 7.1 | open |
| nop-core-framework-C2-002 | 2.2 | nop-core-framework | ProducedBeanInstance 跨实例 monitor 等待环 wait 无超时（←C1-002 跨维度并案） | 待 7.1 | open |
| nop-core-framework-C4-003 | 2.2 | nop-core-framework | concurrent-start 三复合面：无 join 点+失败吞入+死容器 exit-0 伪装/quarkus 三绿假活（←C4-008；与 C4-009/011+CL-019 同 PR 批次；门控 P1/P2 双口径留 7.1 裁） | 待 7.1 | open |
| nop-core-framework-C6-001 | 2.2 | nop-core-framework | loadPluginFromJar 失败点 try 外：ClassLoader+JAR 句柄线性累积泄漏（与测试 N2 设计冲突须消解） | 待 7.1 | open |
| nop-core-framework-C6-002 | 2.2 | nop-core-framework | 插件状态机永久卡死 DEACTIVATING/ACTIVATING 无恢复 API | 待 7.1 | open |
| nop-core-framework-C6-003 | 2.2 | nop-core-framework | config source 先调度后加载：初始失败泄漏永久 globalTimer 刷新任务（启动期降档不适用论证成立） | 待 7.1 | open |
| nop-core-framework-C6-004 | 2.2 | nop-core-framework | ShellRunner 非 timeout 异常不销毁子进程+stderr 线程 pin（←C6-014；修复须外层 try finally） | 待 7.1 | open |
| nop-service-framework-hotpath-C3-002 | 2.3 | nop-service-framework-hotpath | invokeOperation 出口泵缺 thenOnContext：OrmBatchLoader 在 future 完成线程 requireSession→NOT_IN_SESSION/错 ORM 会话 | 待 7.1 | open |
| nop-service-framework-hotpath-C3-003 | 2.3 | nop-service-framework-hotpath | BufferedStreamingPublisher 背压断裂：映射链自驱动 request(1) 越窗后元素进无界 ArrayDeque，慢客户端长流内存失控（AI 路由默认形态） | 待 7.1 | open |
| nop-service-framework-hotpath-C3-007 | 2.3 | nop-service-framework-hotpath | BizActionService.callActionAsync 裸 executor.execute：调用方 IContext 不传播，租户/用户跨任务残留（全单元唯一站点，10 形态 grep） | 待 7.1 | open |
| nop-service-framework-hotpath-C4-007 | 2.3 | nop-service-framework-hotpath | SSO 半衰期 token 刷新在认证过滤器载体线程无超时阻塞 IdP HTTP：brownout 确定性服务级停摆（修复面：ILoginService 异步化+timeout API+thenCompose 领先 next.get） | 待 7.1 | open |
| nop-service-framework-hotpath-C8-001 | 2.3 | nop-service-framework-hotpath | WS 订阅推送同步阻塞发送（无写超时）钉死 globalTimer 全池（实为 4 线程，ρ=N·L/40；平台 27 处消费方） | 待 7.1 | open |
| nop-service-framework-hotpath-C8-002 | 2.3 | nop-service-framework-hotpath | Gateway 429 重试放大 ×7（加法）无准入上界：在途×90s 挂起确定性 OOM 轨迹（与 C4-003 降级事由独立） | 待 7.1 | open |
| nop-service-framework-hotpath-C9-012 | 2.3 | nop-service-framework-hotpath | LocalDateTimeFieldMarshaller computeSize=20 vs 纳秒实写 23-29 字节 protobuf 越界（双模式硬失效实测；**移交功能正确性维度**） | 待 7.1 | open（接收轨落名） |

## 维度集外线索关联（CL 台账，不计入各单元发现数）

> 线索按各审计单元 overlay 指令登记（C 形态 P0/P1 候选、超出该单元维度集），经该单元 Phase 4 复核逐条确认/驳回后在此登记；终裁严重度与修复归属由 7.1 协调方裁定。WI 1.4 的 4 条 CL 已在其 plan 裁定节处置（未入台账），自 1.6 起集中登记。

| 线索 ID | 报告 | 归属维度/建议严重度 | 描述 | 关联条目 | 状态 |
|--------|------|-------------------|------|---------|------|
| nop-retry-nop-tcc-CL-001 | 1.6 | C5 / P1 | 完成写失败副作用重复（handleExecutionSuccess updateRecord 抛出→记录滞留 PENDING/RETRYING→租约到期重拾再执行） | C3-006、C8-012（完成期记账失败族） | confirmed（7.1 终裁） |
| nop-retry-nop-tcc-CL-002 | 1.6 | C4 / P2（C3-004 条件下 P1） | doScan 体（含 join+排水）经 BindScheduledExecutor 实占 globalWorker（默认 30）线程 | C3-004 | confirmed |
| nop-retry-nop-tcc-CL-003 | 1.6 | C2 / P2 | TccContext thread-local（实为 IContext 属性）在 future 永不完成时残留，ContextBinder 复用路径误 join 旧事务 | C6-012、C3-009 | confirmed |
| nop-retry-nop-tcc-CL-004 | 1.6 | C5 / P1（建议） | tcc 过期扫描/清理零生产装配：缺省部署恢复能力未交付；条件面捆绑 6 条（C6-005/C7-005/C7-006/C8-004/C8-007/C3-008 若补驱动须同批复核判级） | C6-005、C7-005 | confirmed（终裁留协调方） |
| nop-retry-nop-tcc-CL-005 | 1.6 | C5·C9 / P2 | removeCompletedRecords 无 limit 全量物化 + txnIds 无界 in 列表（与 C8-003 同位点，主处置项 C8-003） | C8-003 | confirmed |
| nop-retry-nop-tcc-CL-006 | 1.6 | 留 rpc/资源单元终裁 | 完成回调内同步 REQUIRES_NEW DB 事务占用进程级共享 RPC 完成线程池（JDK HttpClient builder.executor() 恒挂） | — | confirmed（跨单元） |
| nop-retry-nop-tcc-CL-007 | 1.6 | 功能覆盖/协调方 | retryFromDeadLetter 死信重放入口零生产装配，与 C3-015 修复同批 | C3-015 | confirmed |

（WI 1.1/1.2/1.3/1.5 单元无确认线索登记；WI 1.4 CL 处置见 plan 2464 维度集外线索处置裁定节。）

### WI 1.7 nop-batch（CL-001~039：编号 39、驳回 4、确认台账 35；全表见 merge-adjudication.md §四）

**并入发现条目（随主条目处置，不独立立案）**：CL-008→C8-002（P1）、CL-026→C6-001（P1）、CL-024→C6-001 修复族验证断言、CL-022→C5-007、CL-016=CL-012、CL-017=CL-013、CL-027→CL-006、CL-021→CL-009 不立案口径。

**独立行动项（27 条，归属/严重度/一句话见审计目录 merge-adjudication.md §四）**：CL-001（C2/P2 DaoBatchStateStore 全局锁内 DB I/O——锁内 I/O 族族首 CL-012/016/020/036）、CL-002（C2/P3 computeIfAbsent 锁内 setup）、CL-003（C3/P3 GraphQL loader 阻塞折叠）、CL-004（C5/C4/P2 exp 线程池无界队列+cachedThreadPool 切换）、CL-005（C3/F9/P3 fireChunkEnd 吞异常不对称）、CL-006（C9/P3 六站点 synchronized(state) 串行点）、CL-010（C6/P2 jdbc 连接关闭唯一出口挂 afterComplete）、CL-011（C9/P3 removeAll O(n×m)+OFFSET O(n²)）、CL-012（C2/P2 候选 =CL-016 全局锁 DB I/O）、CL-013（C6/P2 候选 =CL-017 幽灵 async processor，自 P1 候选降档）、CL-014（C6/P2 候选 cancel 与 future 解耦）、CL-018（C6/P3 导出临时文件 timer 孤儿）、CL-019（C7/P3 EtlTaskStateStore rename 无 fsync——声明模型 kill 内成立）、CL-020（C7/P3 loader 锁内 DB I/O 设计权衡）、CL-023（C9/P3 dispatch 模式 synchronized(state) 串行化）、CL-025（C3/P3 takeBatch 环不查 cancelled）、CL-028（C5/P3 Export 非 MySQL fetchSize 残余）、CL-029（C7/P3 全键拼接日志放大）、CL-030（C9/P3 removeAll 第三处同族）、CL-031（C2/C8/P2~P3 DSL tag 无匹配静默丢数——归属留协调方）、CL-032（C9/P3 每 chunk 全量 JSON 序列化）、CL-033（C8/P2 候选 load 侧部分读失败重试丢行——at-least-once 张力）、CL-034（C6/P3 fireLoadEnd 双触发）、CL-035（C6/P3 fireTaskBegin 半初始化）、CL-036（C7/P3 锁内 INFO 日志）、CL-037（C9/P3 SplitBatchConsumer 快路径过锁）、CL-039（C5/C9/P3 partitionFn=random 顺序承诺失效+32767 队列）。

**驳回/不立案（4）**：CL-007（live 已闭合，commit 7defae0e69）、CL-009（低危不立案）、CL-015（误读控制流）、CL-038（与 C5-002 同点重复，事实并入备注）。

### WI 2.1 nop-kernel-concurrency（CL-001~036：编号 36、驳回 1、确认台账 35；全表见 merge-adjudication.md §CL 台账）

**别名归并（11 条）**：C2-CL-004/C5-CL-009/C7-CL-012→C3-008 主处置项；C1-CL-001（驳回）/C5-CL-008/C9-CL-014 三登记族；C1-CL-002/C2-CL-004/C3-008/C7-CL-012 五登记 SequentialTaskExecutor；C1-CL-003/C2-CL-006/C3-011/C7-012 BlockingSourceConsumeTask；C5-CL-025/C9-CL-036 JsonWhitelist（主登记 C5-CL-025，转功能/安全域）；C5-CL-029 并入 C6-011；C3-CL-017 并入 C7-003。

**独立行动项（24 条，归属/严重度见审计目录 merge-adjudication.md）**：CL-003（GraphQL loader 阻塞折叠）、CL-005（fixedRate 转发面，升格立案为 C3-003 后并入 C7-001）、CL-006（六站点串行点）、CL-007（TaskQueue 弹性扩容纠偏——C8 面）、CL-008/010（容量 0/有界观测残余）、CL-011（exp 无界队列——与 C4-003 同位点随同 diff）、CL-013/033（FileWatcher 无界队列+debounce 竞态——与 C5-004 同位点）、CL-015（HighWatermarkSemaphore 吞吐形态）、CL-016（DedupBlockingQueue m×timeout 契约违约）、CL-017（setEnabled 运行期无效）、CL-018（unregister 不销毁）、CL-020（IEvalScope computeIfAbsent 契约注释）、CL-021（12+ 处 raw JDK/Netty 载体绕过设施——nop-stream/ai-agent/feishu 移交）、CL-022（长驻 worker detach 卫生清单）、CL-023（nop-stream 13+ 处裸建池移交）、CL-024（per-client/topic 池线性累计——nop-cluster 移交）、CL-026（ForbiddenObjectMethods 非线程安全）、CL-027（CacheEntryManagement.remove 实为 clear）、CL-030（GlobalMeterRegistry 非原子替换）、CL-031（nop-graph LeidenDetector 非 daemon 池移交）、CL-032（NioFileWatchService OVERFLOW 事件丢失——与 C6-006 同文件不同机制面，同批修复）、CL-034（TimeOut 墙钟 API 预警）、CL-035（ClassModel.beanModel 双检锁缺 volatile）、CL-036→C5-CL-025。

**移交包**：api-core 5 项（C2-016、C2-CL-019、C3-016/C3-007/C3-005 各自的 api-core 半边——Protected Area 另行裁定，协同点 FutureHelper.bindCancel 单点改造）；nop-graph 1（CL-031）；nop-stream 2（CL-021/023）；nop-cluster 1（CL-024）。

**驳回/不立案（1）**：C1-CL-001（OverflowBlockingQueue 容量 0 自旋证伪——ArrayBlockingQueue 构造期 IAE fail-fast；四参失配构造残余面并入 CL-008）。

### WI 2.3 nop-service-framework-hotpath（CL 编号段 25 实占 24（CL-021 空号勘误）；升格 3、并入 5、驳回 1；全表见 merge-adjudication.md §CL 台账）

**升格立案**：CL-003→C8-005（令牌桶墙钟回填深化）、CL-010→C9-005（SSE 每元素双重转换深化）、CL-015→C9-012（线格式越界 P1 移交功能线）。**并入**：CL-008→C8-001、CL-002→C1-003（归并维持）、CL-005→C1-003/C2-005 工单、CL-007→C4-005/C3-CL-006 三方同一修复单元、CL-019→C2-001 工单。**驳回/不成立**：CL-011（DefaultFlowControlRunner 阻塞式限流——maxWaitMillis 默认 0 无生产配置点，非阻塞；另立 C8-008）。**纯移交**：CL-016→C4（refreshToken syncGet 在 runOnContext 队列线程——终裁并入 C4-007）、CL-017→C4/C5（putAsync("sys",null) NPE 面）、CL-022→nop-ai/nop-message（JDK Delayer orTimeout 11 站点共享面）、CL-023→认证/安全线（LoginApi__refreshToken 语义背离——修复不得当刷新通道）、CL-025→平台原语前置（IHttpResponse 断连观测缺失——C3-010/C4-003 修复先决）。**关联保留**：CL-001/004/006/009/012/013/014/020——归属见审计目录 merge-adjudication.md。**编号勘误**：CL-021 空号（dispatch-log 曾误记「CL-021→安全线」系与 WI 2.2 同号混写——本单元无安全线移交案）。

### WI 2.2 nop-core-framework（CL-001~021：编号 21、升格 1、落格 2、同位点并入 2、零立案 1、关联保留 8、纯移交 7；全表见 merge-adjudication.md §CL 台账）

**升格立案**：CL-001→C1-021（P1）。**落格**：CL-002→C2-005（同站点 computeIfAbsent bin 锁内 HTTP 下载/类加载/start）、CL-018→C4-010（BeanScopeImpl 哈希序销毁同站点）。**同位点并入设施**：CL-008→WI 2.1 C6-011、CL-020→WI 2.1 C6-003。**零立案**：CL-009（PluginManager syncGet 调用线程内联终判）。**纯移交（7）**：CL-013/CL-019→nop-kernel（CL-013 为 2.1 未登记新面 registerCleanup）、CL-021→安全线（OsUserHelper 明文口令入进程参数）、CL-016→api-core 功能缺陷轨（PlaceholderConfigReference 未替换占位符返回）、CL-010→插件功能线（子容器不经 PluginClassLoader 加载——热卸载隔离失效）、CL-011→C5 面（DefaultShellOutputCollector 无界收集）、CL-012→C7 面（ChangeSubscriptions 空条目不摘）。**关联保留（8）**：CL-003/005/007/014/015/017 及其余——一句话归属见审计目录 merge-adjudication.md。

## P2 裁决台账（7.1 逐项裁决）

> P2 默认不在修复范围；7.1 逐项裁决为「修（in-scope）/ deferred（必须带 Why Not Blocking + successor 指针）」；已确认 live 的 must-fix P2 不得降级 deferred。

| Finding ID | 报告 | 单元 | 一句话描述 | 裁决 | Why Not Blocking / 批次 |
|-----------|------|------|-----------|------|----------------------|
| （无） | | | | | |

## 使用说明

- Finding ID 全局唯一，格式 `{单元标识}-C{N}-{序号}`，如 `nop-stream-core-runtime-C1-001`；单元标识取 roadmap 工作项中的单元名（归档目录名一致）。
- 报告目录命名：`ai-dev/audits/{YYYY}-{MM}/{YYYY-MM-DD}-{HHMM}-deep-audit-{单元标识}-concurrency/`，每维度一个文件（`{C编号}-{英文短名}.md`）+ `summary.md`。
- 审计工作项的 done 门控 = 审计目录完整 + summary.md 归档 + 本索引已更新 + 独立 closure audit PASS。
- 与其他 campaign（security / full-code-review / nop-stream-independent-audit）发现重叠时，先完成修复方为准，另一方在此登记 cross-reference 后关闭，不重复修复。
