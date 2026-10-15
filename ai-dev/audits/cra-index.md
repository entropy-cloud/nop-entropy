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

> 1.5 计数口径：合并裁定后 54 个工作项（23 缺陷族 MA-01~23 + 31 独立）；P0=0（即时通道显式空执行）；C10 缺保护 14 条单列；与 WI 1.1–1.4 台账零重复立项，跨单元关联 3 处单列（nop-job-C7-006、2026-09 P2-3.1-001、AU-10 同案对齐）。

> 1.4 计数口径：合并裁定后 34 项（7 缺陷族 MA-01~07 + 27 独立）；P0=0（即时通道显式空执行）；C10 缺保护 9 条单列；与 WI 1.1–1.3 台账零重复立项，跨单元关联 3 条单列。

> 1.3 计数口径：合并裁定后 20 项（6 缺陷族 MA-01~06 + 14 独立）；P0=0（即时通道显式空执行）；C10 缺保护 8 条单列；与 WI 1.1/1.2 台账零重复立项，4 条跨单元关联单列。

## P0 发现追踪（即时通道）

> P0 永不进入 Phase 8 批量修复：发现即就地修复（审计 plan 内加修复 Phase）或异步注入独立修复 plan。

| Finding ID | 报告 | 描述 | 修复路径 | 修复状态 |
|-----------|------|------|---------|---------|
| nop-stream-state-connectors-C6-001（MA-01，成员 C3-001/C4-001/C7-002） | 1.2 | KafkaTwoPhaseCommitSink transactional producer 全终止路径（DRAIN/CANCEL/FAILED）无 close——每子任务每次作业终止泄漏 producer+网络线程+缓冲池 | plan 2462 Phase 7 就地修复：implements AutoCloseable + 幂等 close()（abort 开启事务 → producer.close(30s)），经 StreamSinkOperator.close:175 钩子全终态触发；回归测试 3 个（TestKafkaTwoPhaseCommitSinkLifecycle）；`./mvnw test -pl nop-stream -am` BUILD SUCCESS | 已修复（commit 8dcfa4cd0c） |
| （1.1/1.3/1.4/1.5 单元无 P0 发现，已按各 plan Phase 7 显式空执行记录） | | | | |

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
