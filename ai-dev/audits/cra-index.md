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
| `2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency` | 2.4 | nop-persistence-rw | C1 C2 C3 C4 C5 C6 C9 + C10（C7/C8 线索 overlay） | 0 | 13 | 31+17（含 C10 缺保护登记） | 23 | done（closure audit PASS，plan 2471） |
| `2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency` | 2.5 | nop-persistence-peri | C1 C3 C6 C7 C8 + C10（C2/C4/C5/C9 线索 overlay） | 1 | 13 | 20+39（含 C10 缺保护登记） | 1 | done（closure audit PASS，plan 2472） |
| `2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency` | 3.1 | nop-ai-core-nop-ai-agent | C1 C3 C4 C5 C7 + C10（C2/C6/C8/C9 线索 overlay） | 0 | 13 | 29+19（含 C10 缺保护登记） | 8 | done（closure audit PASS，plan 2473） |
| `2026-10/2026-10-20-0829-deep-audit-nop-ai-toolkit-tools-shell-concurrency` | 3.2 | nop-ai-toolkit-tools-shell | C1 C4 C6 C8 + C10（C2/C3/C5/C7/C9 线索 overlay） | 0 | 5 | 19+23（含 C10 缺保护登记） | 1 | done（closure audit PASS，plan 2474） |

> 2.4 计数口径：合并裁定后 67 条（71 初审 + 3 深挖 = 74 复核保留全含 1 降级（C2-014）→ 维度内并入 7；P1 台账 17→有效工单 13、P2×31、P3×23）；P0=0（即时通道显式空执行）；C10 缺保护 17 条单列；CL 33 条全部确认（承接 8/收敛 5/独立 10/watch 6/移交 4——CL-700→nop-commons）；**结构性发现：nop-orm/nop-dao 事务设施 JVM 级锁为 0，并发正确性全押 session/事务同 context 单线程契约，平台语义多处破口**；跨单元映射 7 行（X1 与 2.3 C1-010 同号异单元互补、X4 CL-401↔2.2 CL-018 同站点、X7 对 2.1 零重复）；对 WI 2.1 仅误报校准引用。

> 2.5 计数口径：合并裁定后 35 张工单（54 复核保留全含 1 降级（C7-005 P1→P2 零生产实例化）→ 12 缺陷族 MA-01~12 吸收 17 条覆盖 29 条 + 23 独立 + watch 1（C8-006）+ 跨单元随案 1（C7-012→WI 1.2 MA-05）；P0×1/P1×13/P2×20/P3×1——MA-02 开放裁定暂计 P1 留 7.1 终裁）；**P0×1 = C6-002 成功路径不停链（本 campaign 第 2 个 P0，即时通道）**；C10 缺保护 39 条单列（矩阵会红 0/假保护 30/无测试 24）；CL 26 条全部落位（并入工单 16/独立线索 10/驳回 0）；nop-cdc 零审计面（空接口占位）与 drivers 三纯 pom 显式登记；结构性发现：nop-orm-migration 运行期多线程流水线对并发零防护（五状态机零自环/零单飞/裸线程），标识符派生键族错位（C3-008/009/010/011/012 五站点同根）；跨单元台账级同案仅 2 处（C7-012 随案、C6-005 双轨）。

> 3.1 计数口径：合并裁定后 50 张工单（129 初审+深挖 → 128 复核保留全含 3 降级（C1-005/C5-022 opt-in 装配面、C7-013 收敛）+ 1 驳回（C5-019 初审 grep 失误证伪——releaseSession 实有 4 出口接线）→ 21 缺陷族 MA-01~21 吸收 97 + 29 独立 + watch 2；P0×0 显式空执行/**P1×13**/P2×29/P3×8）；C10 缺保护 19 条单列（矩阵会红 0/假保护 103/无测试 25；P1×30 = 假保护 23+无测试 7；反向钉死 4 项——gate3 测试字面钉 remove no-op）；CL 77 条全部落位（并入工单 46/独立线索 29/watch 2/驳回 0）；**MA-07（周期任务 Error 逃逸 6 条）单列移交 WI 2.1 C3-001 平台绝缘层**，C3-009/012 为 WI 2.1 CL-021 移交面具体化站点，C7-012↔WI 1.6 fencing 族家族关联——零重复立项；结构性发现：agent 执行链对并发/异步零防护（流式主链五 P1 轴保护力为零、方言流式协议 vs 非流式系统性分叉、超时取消契约 5 路径仅 2 实现、F4 终态清理族 22+ 站点）；C3 R3 口径偏差记录在案（矩阵穷举闭合裁定终止，供 closure audit 裁定）。

> 3.2 计数口径：合并裁定后 25 张工单（39 初审+深挖 → 39 复核保留全含 6 降档（shell 零生产接线族条件化：C1-002/C6-004/005/C8-001/002/003——投产即回升）+ 1 升档（C4-001 并案校准 P2→P1）→ 8 缺陷族 MA-01~08 吸收 22 + 17 独立 + watch 0；P0×0 显式空执行/**P1×5**/P2×19/P3×1——MA-06 判级分歧暂计 P1 留 7.1）；C10 缺保护 23 条单列（矩阵会红 0/假保护 21/无测试 18；**9/9 P1 全部无测试**；反向钉死 ShellIOTest:294-300 队满 close）；CL 20 条全部落位（并入工单 16/独立线索 4/驳回 0）；**结构性发现：工具执行链故障注入维度整体缺失（9/9 P1 零保护的结构性成因）、shell 1024 设计参数在测试树零出现、修实例不修类别 F3 残留（Host killTree 已修 Docker 未修）**；跨单元：与 WI 1.1–3.1 台账零同站点重复立项，沙箱族跨单元判级分歧（3.1 MA-17 P2 vs 本单元 P1 两套实现同病）与 shell 族 P1/P2 分叉移交 7.1。

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
| nop-persistence-peri-C6-002（MA-03 主登记，成员 C3-003） | 2.5 | 搬迁成功路径（VERIFY/SWITCH/DONE）从不调用 stopAll（全模块唯一调用点在 abort，与 IncrementalJobManager javadoc 矛盾）——DONE 后链线程/binlog 连接/engine/offset store/checkpoint 执行器永久滞留；写门仅拦本 JVM ORM DML，源库域外写入经存活链 keyed upsert 持续回写已晋级目标表 | plan 2472 Phase 7 就地修复：OrmMigrationOrchestrator 新增 stopIncrementalChains（字段直读不惰性建管理器）——confirmSwitch 收敛点停链 + completeSwitch 开门前兜底幂等停链；新增 setIncrementalManager 注入 seam；回归测试 TestMigrationSwitchStopsChains 2 例（收敛停链断言回退必红 + 非 incremental 配置不动管理器）；模块套件 52 测试全绿；scan-hollow --severity high 退出码 0；owner doc data-migration.md SWITCH 节已同步 | 已修复（本轮提交） |
| （1.1/1.3/1.4/1.5/1.6/1.7/2.1/2.2/2.3/2.4/3.1/3.2 单元无 P0 发现，已按各 plan Phase 7 显式空执行记录） | | | | |

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
| nop-persistence-rw-C1-001 | 2.4 | nop-persistence-rw | IContext 会话/事务注册表 get-or-create 竞态：僵尸会话/事务表覆盖/双一级缓存（←C1-023/C2-003；ContextAttrs.getOrCreate 单修复） | 待 7.1 | open |
| nop-persistence-rw-C1-003 | 2.4 | nop-persistence-rw | ormModel 非 volatile：reload 后旧模型无限期可见+非同步 setOrmInterceptor | 待 7.1 | open |
| nop-persistence-rw-C1-015 | 2.4 | nop-persistence-rw | OrmComponentModel 先发布引用后填充：getColumnPropIdMap 热路径竞态（无需 JMM 假设） | 待 7.1 | open |
| nop-persistence-rw-C2-001 | 2.4 | nop-persistence-rw | session 全生命周期零互斥：flush 重入守卫为静默跳过（同上下文并发丢刷写/双刷写） | 待 7.1 | open |
| nop-persistence-rw-C2-002 | 2.4 | nop-persistence-rw | runInNewSessionAsync 完成线程执行 flush/registry 恢复/close 无同步 | 待 7.1 | open |
| nop-persistence-rw-C2-004 | 2.4 | nop-persistence-rw | 全局实体缓存 read-repair：迟到 put 覆盖并发 evict，陈旧值无界驻留（实体级 opt-in 启用上可达） | 待 7.1 | open |
| nop-persistence-rw-C2-005 | 2.4 | nop-persistence-rw | DaoEntityBlockingSource CAS 抢占退化无条件 UPDATE：任务重复投递 | 待 7.1 | open |
| nop-persistence-rw-C2-013 | 2.4 | nop-persistence-rw | 事务注册协议无原子性：半个成功 ERR_TXN_NOT_REGISTERED（REQUIRED 组语义退化） | 待 7.1 | open |
| nop-persistence-rw-C2-015 | 2.4 | nop-persistence-rw | subTransactions 裸 LinkedHashMap：组提交丢写无感知（最重后果） | 待 7.1 | open |
| nop-persistence-rw-C2-017 | 2.4 | nop-persistence-rw | JdbcTransaction 连接生命周期无互斥：双取池连接泄漏+eagerRelease 后僵尸连接丢写（←C1-026） | 待 7.1 | open |
| nop-persistence-rw-C3-001 | 2.4 | nop-persistence-rw | runInNewSessionAsync 上游永不完成：session 永不关闭+注册表滞留 | 待 7.1 | open |
| nop-persistence-rw-C3-005 | 2.4 | nop-persistence-rw | flush 先清 dirty 再执行 SQL：非事务路径半个成功残留（←C2-006） | 待 7.1 | open |
| nop-persistence-rw-C6-001 | 2.4 | nop-persistence-rw | drainTo 事务横跨等待窗口：K≥8 消费者先于生产者即连接池耗尽（←C3-006 单工单） | 待 7.1 | open |
| nop-persistence-peri-C6-004（MA-01 主） | 2.5 | nop-persistence-peri | PubSub 订阅管理竞态：cancel/suspend/resume 绕过监视器三步变更共享 entry/注册表/通道，订阅静默失效或复活双投递（←C1-011/C3-004；已知未修复 check/nosql-cdc 2026-08-19 前案） | 待 7.1 | open |
| nop-persistence-peri-C8-001（MA-02 主，开放裁定暂计 P1） | 2.5 | nop-persistence-peri | PubSub 消费回调在共享 Redis IO 线程内联执行：慢消费者队头阻塞→服务端断链→静默丢消息，CompletionStage ack 契约被整体丢弃（←C3-005/C7-010；严重度 7.1 终裁） | 待 7.1 | open |
| nop-persistence-peri-C6-003（MA-04 主） | 2.5 | nop-persistence-peri | 链停止协议不可验证：thread 字段晚于 start 发布使 join 空转、abort 读 stale-null 跳过 stopAll、stopAll 仅 cancel 不等待（←C1-004/CL-009） | 待 7.1 | open |
| nop-persistence-peri-C7-003（MA-05 主） | 2.5 | nop-persistence-peri | 状态行全列覆盖无版本谓词 + 7 编排入口零单飞/fencing：abort×confirmSwitch 背离终态、多节点双跑双链（←C1-005/006/009） | 待 7.1 | open |
| nop-persistence-peri-C7-001（MA-06 主） | 2.5 | nop-persistence-peri | 链死亡不可见：getFailure 全仓零调用方，lag=0 门禁把死链判追平，VERIFY 放行缺数目标（←C3-002/CL-006/CL-018/CL-023；先修 C3-008 键派生） | 待 7.1 | open |
| nop-persistence-peri-C3-001（MA-10 主） | 2.5 | nop-persistence-peri | startFallbackChains 部分启动无补偿 + jobName 丢 querySpace 句柄 put 覆盖：孤儿链不可停且双写同目标表（←C6-001） | 待 7.1 | open |
| nop-persistence-peri-C1-003 | 2.5 | nop-persistence-peri | 迁移历史表全守卫非原子 check-then-act：多节点并发启动 DDL 重复执行 + FAILED 脏行覆盖对方 success 行致永久重试循环 | 待 7.1 | open |
| nop-persistence-peri-C1-007 | 2.5 | nop-persistence-peri | rebuildRouting 非 volatile 热替换 shardSelector：SWITCH 开门后请求线程无限期读旧路由，业务写静默落旧分片（平台侧同案 nop-core-framework-C1-007） | 待 7.1 | open |
| nop-persistence-peri-C3-008 | 2.5 | nop-persistence-peri | lag 探测 connector 名与链注册名 base-i 错位：rescale 主场景 lag=0 门禁观测量恒空平凡通过（MA-06 修复顺序前置） | 待 7.1 | open |
| nop-persistence-peri-C3-009 | 2.5 | nop-persistence-peri | relocate 路由 querySpace 硬编码空串：增量链 filter 恒 false 100% 丢事件、FULL upsert 落回源侧 default 表（与 C3-008 非同根双立） | 待 7.1 | open |
| nop-persistence-peri-C7-002 | 2.5 | nop-persistence-peri | 五状态机 ALLOWED 零自环：javadoc 承诺的 fix-forward 路径自身走不通，一次瞬时故障全量报废（←CL-007/CL-008） | 待 7.1 | open |
| nop-persistence-peri-C7-004 | 2.5 | nop-persistence-peri | 写门 updateConfigValue 进程内裸写无持久化：多实例拓扑门状态不收敛，VERIFY 资格判定失真 | 待 7.1 | open |
| nop-persistence-peri-C7-014 | 2.5 | nop-persistence-peri | PubSub 断线自动重连后 Redis 侧订阅全丢且无重订阅无检测：静默失联直至重启（lettuce-core 7.5.2 字节码核验） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-001（MA-01 主） | 3.1 | nop-ai-core-nop-ai-agent | 流式主链族：callStream 取消只传上游、publisher 永不关闭、聚合计无界——取消后订阅者永久无终止信号、单请求 OOM（←C3-002/009+C4-001/002+C5-001） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C1-008（MA-02 主） | 3.1 | nop-ai-core-nop-ai-agent | 引擎注入目标登记窗口：resume/wake/restore 零注册 + 暂存分支裸 ArrayList——CME/撕裂持久化/注入丢失（←C3-014） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C5-015（MA-03 族首） | 3.1 | nop-ai-core-nop-ai-agent | F4 引擎终态清理族：session store put-only + checkpoint/账本/注册表无终态释放——历史会话堆内永驻（族 29 站点，remove 已实现纯接线；←C5-009/012/013/020+C1-006/011/014/016/017/018/026+C7-013/017/028/030/031） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C4-010（MA-04 主） | 3.1 | nop-ai-core-nop-ai-agent | spawn 编排池泄漏：per-invocation orchestrator 全仓无 close——每次工具调用泄漏 max(2,NCPU) 固定池常驻线程（←C4-010/C7-032 三重登记统一 P1） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-022（MA-05 主） | 3.1 | nop-ai-core-nop-ai-agent | 成员/spawn 超时取消契约 F3 漏修族：5 路径仅 2 实现 cancelSession——挂死成员僵尸化 + RECLAIM 同任务双执行（←C7-023/C3-025/C4-014） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C5-008（MA-06 主） | 3.1 | nop-ai-core-nop-ai-agent | 长会话归档/spill/journal 无界累积 + 恢复全量物化 4× 放大（←C5-010/011） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-011（MA-08 主） | 3.1 | nop-ai-core-nop-ai-agent | 超时取消传播断链：机械超时/中断不向底层工具执行传播，进程级 kill 通道仅 heartbeat 一处调用、cancelCall javadoc 契约无实现——幽灵 bash/容器/HTTP 持续运行 | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-028（MA-09 主） | 3.1 | nop-ai-core-nop-ai-agent | 方言流式协议族（单修复批）：三站点 tool_call 截断 + 流式 usage 全链失活 + Ollama 空串抢占 + 流式错误通道缺失——缺省 stream=true 主路径确定性丢数据（←C3-030/031/032） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C3-021（MA-11 主） | 3.1 | nop-ai-core-nop-ai-agent | DBMessageService 单线程 poller 内联消费：call-agent join 120s 期间全模块消息冻结 + 毒消息无 DLQ 停摆（←C7-025/C4-CL-040） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-012（MA-19 主） | 3.1 | nop-ai-core-nop-ai-agent | 接管锁续期异常无限容忍：SQLException 仅 WARN 无限重试——非对称分区期间永不自 abort，fencing 语义整体失效双执行窗口 | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C4-011 | 3.1 | nop-ai-core-nop-ai-agent | AgentDispatchExecutor 经 runAsync 落 commonPool：阻塞工具体（300s cap）占满 JVM 全局池（与 WI 1.2 C4-004/WI 2.3 C8-001 同族判级一致；←CL-025） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-001 | 3.1 | nop-ai-core-nop-ai-agent | 流式聚合无全程期限/静默检测：SSE 中途挂死 future 永不完成且对健康检测不可见（缺省装配挂死链补证） | 待 7.1 | open |
| nop-ai-core-nop-ai-agent-C7-002 | 3.1 | nop-ai-core-nop-ai-agent | 流式中途干净断连被 onComplete 误判成功：五方言终止信号在聚合端未校验——截断内容以成功响应交付，重试/守卫全部失效 | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C1-001（MA-01 主） | 3.2 | nop-ai-toolkit-tools-shell | ToolManagerImpl 路径串行队列：CHM bin 锁内同步执行 + executeNow.get() 同步抛出时 result future 永不完成——排队分支全部静默悬挂（JDK 实测证伪 loud-fail 对照；←C4-005） | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C6-001（MA-02 主） | 3.2 | nop-ai-toolkit-tools-shell | 沙箱 reader 泄漏族：孪生后台子进程持 stdout 管道→reader 线程/fd 永久泄漏 + finally 发布活 StringBuilder 终态竞态（←C4-001/C6-008；Host killTree 已修 Docker 未同修的 F3 残留） | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C6-002（MA-03 主） | 3.2 | nop-ai-toolkit-tools-shell | docker 清理族：超时路径不 destroyForcibly CLI + killContainer 失败被吞 + 取消句柄已注销——容器/进程/线程无界残留（←C8-009） | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C6-003（MA-04） | 3.2 | nop-ai-toolkit-tools-shell | runShortCommand 先无限期 drain 后才 waitFor(5s)：daemon 挂起时清理线程自身永久阻塞，墙钟预算失效 | 待 7.1 | open |
| nop-ai-toolkit-tools-shell-C4-003（MA-06 主，判级分歧暂计 P1） | 3.2 | nop-ai-toolkit-tools-shell | shell 管道死锁族案：阶段提交 REE 无回滚级联吞线程 + ≥1024 行输出确定性挂死 + 下游死亡上游阻塞 + `0<&1` 自引用（←C8-001/003/011；C4 P1 vs C6/C8 条件化 P2 分叉留 7.1） | 待 7.1 | open |

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

### WI 2.4 nop-persistence-rw（CL 33 条全部确认：承接 8/收敛 5/独立 10/watch 6/移交 4；编号段 CL-001~002/100/200/300~302/400~408/410~415/500~504/600~604/700，**CL-409 空号**；权威全表见 merge-adjudication.md §六）

**承接（8）**：CL-001（evictGlobalCache 监听器膨胀→C2-010/C5-004/C9-001 承接）、CL-002（flush 重入语义→C2-001 覆盖）、CL-100（bin 锁内构造→C2-012 落格）、CL-200（锁内 DB+codegen→C2-018 落格）、CL-404（监听器线程安全双裁→C2-014 立案）、CL-405（完成线程读实体数组→C1-002 互引）、CL-406（COW 非 volatile→C1-003/C1-005 覆盖）、CL-502（单槽缓存良性→C9-003/C9-006 同族关联）。

**收敛（5）**：CL-400（drainTo REQUIRES_NEW 横跨窗口→C6-001+C3-006 合并工单事务语义面）、CL-407（flush 落完成线程事务→随 C2-002/C3-001 修复核对）、CL-601（cancelToken 不反注册→C6-002）、CL-603（addListener 每加必 sort→随 C9-001/C2-014 收敛）、CL-604（clearCache 旧模型不 close→**并入 C6-003**）。

**独立行动（10）**：CL-300（JdbcBatcher 回调异常残留 commands+sql=null 复用混批）、CL-301（DaoMetricsImpl SQL 全文为键基数不受控）、CL-302（JdbcDialectProvider 并发首查 N 次嗅探——与 C1-028 邻接，优选启动期预热）、CL-403（cacheGet computeIfAbsent 内用户 loader）、CL-408（batchLoadAsync future==null 死代码）、CL-410（TenantCachedQueryPlan 整实例覆盖多租户永不收敛——同缓存相邻独立缺陷不并入 M2）、CL-500（session.close 静默丢弃未 flush 批动作）、CL-501（batchLoadCollectionAsync 失败无状态恢复）、CL-503（runDelayTasks 只捕 Exception）、CL-504（afterFlushCallbacks 失败静默丢弃）。

**watch（6）**：CL-411（ANTLR _sharedContextCache 无同步——所有权在 antlr4-runtime，根治入口 nop-kernel/nop-antlr4 包装层）、CL-412（propExprMetas 活 map 逃逸）、CL-413（SqlExprMetaCache 双键双实例）、CL-414（Snowflake 单 monitor 全实体串行——需 JMH 量化后立案）、CL-415（SqlDataTypeMapping.register 并发无同步——dev 热加载窗口）、CL-602（租户上限 100 编译抖动）。

**移交（4）**：CL-401（ContextTaskQueue.syncGet 隐藏重入通道 5 等待点→api-core/nop-kernel 修复轮，跨单元映射 X4；与本单元 WI 2.2 CL-018 同站点）、CL-402（executeWithContext 同上下文并发仅 warn→api-core 批次；C2-001/002/003 可达性平台级根因）、CL-600（DaoEntityBlockingSource 同形态跨模块复核→nop-batch/nop-job）、CL-700（IntArray 无值 equals→nop-commons 修复轮，5 根内 6 点回归面）。


### WI 2.5 nop-persistence-peri（CL-001~026：编号 26、驳回 0、并入工单 16（缺陷族 8 + 非族工单 8）、独立线索台账 10；全表见 merge-adjudication.md §八）

**并入缺陷族（8）**：CL-006→MA-06、CL-011→MA-02、CL-018→MA-06、CL-019→MA-07、CL-021→MA-01、CL-023→MA-06（join 半边随 MA-04 同 diff）、CL-009→MA-04、CL-007/008 均并入独立 #13（C7-002，X-2 主处置）。

**并入非族工单（8）**：CL-013/CL-016/CL-026→独立 #18（C7-009，CL-026 为顺带收口）、CL-015→独立 #22（C8-005 主登记）、CL-020→独立 #12（C6-005 分账）、CL-022→独立 #3（C1-003 分账）、CL-025→MA-07（扩展面，站点不同非族成员）、CL-012 注→与 MA-02 修复统一评估 lettuce 线程模型治理（台账联动）。

**独立线索台账（10，不计工单）**：CL-001（C4/C5 Rpc 同步阻塞无取消——与独立 #1 同文件联动）、CL-002（F5 loadAsync 无超时 future 注入 flush 链）、CL-003（F2 failOnError=false 半个成功）、CL-004（F2 rollback 反向部分成功无簿记）、CL-005（F5 迁移线程内联 XPL 无超时）、CL-010（REGISTRY 只增不清 watch 候选）、CL-012（computeIfAbsentAsync mappingFunction 调度到完成线程）、CL-014（GETEX 读路径写副作用 TTL 语义）、CL-017（C4 Td 驱动伪异步无池化）、CL-024（C9 Rpc N+1 加载）。

### WI 3.1 nop-ai-core-nop-ai-agent（CL-001~077：编号 77、驳回 0、并入工单 46、独立线索台账 29、watch 2；全表见 merge-adjudication.md §八）

**并入工单（46）**：流式主链族 CL-001/011/013/025/029/041 等随 MA-01/C7-001；F4 族 CL-008/010/018/024/037/050 等随 MA-03 站点面；池治理族 CL-036/040/044/064 等随 MA-04/08/C4-004/009；DB 治理族 CL-022/048/049/072 等随 MA-11；其余随各站点工单（§八全表）。**独立线索台账（29，含 4 归并桶）**：DB 保留策略族（CL-008/030/049/073 等）、javadoc 契约失真族（CL-009/037/059/073 等）、正则重编译族（CL-011/012/027/076）、超时等待点族（CL-002/022/047/063 等）；watch 2（CL-031 StreamAggregator 双通道、CL-035 FileBackedSessionStore 摘锁窗口）。**驳回 0**。

### WI 3.2 nop-ai-toolkit-tools-shell（CL-001~020：编号 20、驳回 0、并入工单 16、独立线索台账 4；全表见 merge-adjudication.md §八）

**并入工单（16）**：pathQueues 三登记收敛（CL-001/C4-CL-005/C8-006 → C8-006 主登记随 MA-07）、exportedEnv 三登记（CL-006/CL-009/C8-CL-016 → CL-009 主登记随 MA-08）、CL-008 拆两子面分落 MA-06/C8-004、其余随各站点工单（§八全表）。**独立线索台账（4）**：CL-007（GraphqlQueryExecutor 调用线程同步 DNS）、CL-010（FileShellInput 构造期无上界全量读入）、CL-019（progressSnapshot 每心跳全量拷贝）、CL-020（`>` 重定向写失败被 safeClose 吞、命令 0 退出输出无痕丢）。

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
