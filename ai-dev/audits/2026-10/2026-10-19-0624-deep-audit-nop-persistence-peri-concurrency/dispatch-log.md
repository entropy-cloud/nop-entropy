# Dispatch Log — CRA WI 2.5 nop-persistence-peri 并发审计

- **Plan**: `ai-dev/plans/2472-cra-wi2.5-persistence-peri.md`（active）
- **基线**: commit 458897d249，分支 concurrency-audit-remediation；基线文件 `_tmp/cra-baseline-nop-persistence-peri.txt`（17 命令组 × 10 源码根）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`（含本单元目录）；仅 Phase 4 复核允许读历史审计与 cra-index 台账
- **串行约束**: 运营者要求——全部子代理严格串行派发，无并行批次

## 源码根实测（Phase 1 回填，手写口径不含 `_` 前缀与 target/）

| 源码根 | 规模 | 备注 |
|--------|------|------|
| nop-dbtool/nop-dbtool-core/src/main/java | 7f/1,832 | nop-dbtool-web 纯 pom |
| nop-db-migration/src/main/java | 72f/3,877 | 另有 31 个 `_` 前缀文件 6,683 行排除（`_gen/` 内无非下划线残留） |
| nop-orm-migration/src/main/java | 20f/3,462 | 五状态机 + orchestrator + 裸线程 IncrementalJobManager（L176） |
| nop-cdc/nop-cdc-core/src/main/java | 1f/11 | CdcConstants 空接口占位符；nop-cdc-debezium 纯 pom——**零审计面裁定** |
| nop-nosql/nop-nosql-core/src/main/java | 19f/1,083 | |
| nop-nosql/nop-nosql-lettuce/src/main/java | 18f/2,377 | |
| nop-orm-drivers/nop-orm-tdengine/src/main/java | 6f/726 | es/mongo/redis 纯 pom 空壳 |
| nop-orm-geo/src/main/java | 12f/700 | |
| nop-orm-pdm/src/main/java | 9f/1,654 | |
| nop-orm-rpc/src/main/java | 1f/913 | RpcEntityPersistDriver 单文件 |
| **合计** | **165f/16,635** | 与 plan 2472 一致 |

## 预注锚点核实（Phase 1，grep 实测）

| 锚点 | 实测 | 结论 |
|------|------|------|
| 五状态机（MigrationPhase/MigrationJobState/MigrationJobStateStore/OrmMigrationOrchestrator/OrmMigrationJobStatusBizModel） | 全在 nop-orm-migration | 预注成立；owner doc 标题《数据搬迁管道（nop-orm-migration）》一致，无漂移 |
| db-migration 状态关键词 PREPARE/INCREMENTAL/VERIFY | 0 命中 | db-migration = Liquibase 风格 DDL 引擎，非五状态机 |
| 差异链（OrmModelDiffer/OrmDbDiffer/DataBaseUpgrader/DataBaseUpgradeInitializer） | 全在 nop-dbtool-core | 预注成立 |
| IncrementalJobManager 线程模型 | `new Thread`（L176）；volatile Thread（L214）；全文件无 Executor/pool | R1 的「ExecutorService」说法订正为裸线程 |
| write-gate/debezium 引用 | OrmMigrationOrchestrator/IncrementalLagProbe/MigrationConfigs/CdcOffsetSeedBuilder/ShardChangeEventRouter | 运行期多线程流水线形态证实 |

## owner doc 漂移登记

- `docs-for-ai/02-core-guides/data-migration.md`：标题与五状态机归属和实测一致——**无漂移**。
- 该 doc 范围仅覆盖 nop-orm-migration；db-migration/dbtool/nosql/drivers/geo/pdm/rpc 无专门 owner doc——按 plan 预注「声明契约以 javadoc 为准」执行。

## 维度级派发记录

（Phase 2 起逐条追加）

### C1 初审（2026-10-19）

| 批次 | 范围 | 编号 | 结果 |
|------|------|------|------|
| C1 批 1 | dbtool+cdc+drivers+geo+pdm+rpc（36f/5,836） | C1-001；CL-001/002 | P2×1 + CL×2；static 状态人工复现全 0 |
| C1 批 2 | db-migration（72f/3,877） | C1-002/003；CL-003/004/005 | P1×1（迁移历史表 check-then-act）+ P2×1 + CL×3 |
| C1 批 3 | orm-migration（20f/3,462） | C1-004…010；CL-006…010 | **P1×5 + P2×2 + CL×5**（运行期流水线，降档不适用） |
| C1 批 4 | nosql-core + lettuce（37f/3,460） | C1-011…014；CL-011…014 | P1×1 + P2×2 + P3×1 + CL×4 |
| **C1 合计** | 全源码根 | C1-001…014、CL-001…014 | **P1×7/P2×6/P3×1 + CL×14** |

- 基线缺陷记录：初版基线脚本 zsh glob 错误致各节为空（C1 批 1 发现并人工复现兜底）；主 agent 已于 C1 批 1 后修复重生成（798 命中行），批 2–4 在修复后基线上工作，全部批次按口径纪律人工复现，不影响发现有效性。

### C3 初审（2026-10-19，单批次全源码根）

- C3-001…007：**P1×4 + P2×3**；CL-015/016/017（归 C5/C9/C4）
- 主题：orm-migration 孤儿 CDC 链/失败契约无实现/DONE 后幽灵写者（P1×3）+ nosql PubSub 无锁 cancel 竞态（P1×1）
- 终止信号：**无**（存在 P1——深挖阶段必派第 2 轮）
- cdc 零审计面已登记；dbtool/db-migration/geo/pdm 零异步站点；orm-rpc 7 处 async-to-sync 桥接合规

### C6 初审（2026-10-19，单批次全源码根）

- C6-001…005：**P0×1 + P1×3 + P2×1**；CL-018/019（归 C9/C5）
- **P0 = C6-002**：搬迁成功路径（VERIFY/SWITCH/DONE）从不调用 stopAll（唯一调用点在 abort），与 IncrementalJobManager javadoc 矛盾——DONE 后链线程/binlog/JDBC 连接永久滞留；写门只拦本 JVM DML，域外写入经存活链持续回写已晋级目标表
- 终止信号：无（P0/P1 在场）；dbtool/db-migration/cdc/geo/pdm/rpc/tdengine/nosql-core 八根登记干净

### C7 初审（2026-10-19，单批次全源码根）

- C7-001…012：**P1×5 + P2×6 + P3×1**；CL-020/021/022
- 主题：CDC 链死亡不可观测（lag=0 盲区→VERIFY 放行缺数目标）、五状态机零自环（一次瞬时故障即全量报废）、状态行无版本守卫 + 7 入口无 fencing
- 终止信号：无（P1 在场）

### C8 初审（2026-10-19，单批次全源码根，Phase 2 收官）

- C8-001…006：**P1×1 + P2×4 + P3×1**；CL-023/024
- 主题：PubSub 消费回调 IO 线程内联（队头阻塞→断链→丢消息）、full-checksum 双侧全量驻内存 + 深分页 O(N²)、tdengine/rpc 驱动无 chunk 上界
- 终止信号：无（P1 在场）

### Phase 2 汇总（主 agent）

- **初审累计 44 条**：C1×14（P1×7/P2×6/P3×1）、C3×7（P1×4/P2×3）、C6×5（**P0×1**/P1×3/P2×1）、C7×12（P1×5/P2×6/P3×1）、C8×6（P1×1/P2×4/P3×1）——P0×1/P1×20/P2×20/P3×3
- **CL×24**（CL-001…024：C1×14/C3×3/C6×2/C7×3/C8×2）
- 格式抽检：5 文件全部含触发时序+失效面+file:line，无退回
- 继续条件判定：5 维度均含 P1 → 全部必派第 2 轮深挖

### Phase 3 深挖（2026-10-19）

| 轮次 | 范围 | 新增 | 判定 |
|------|------|------|------|
| R2（合并批） | C1/C3/C6/C7/C8 | +6（P1×2/P2×3/P3×1）：C1-015、C3-008 P1、C7-013/014 P1/015、C8-007；CL-025 | C1/C6/C8 终止；C3/C7 需续 |
| R3（收敛） | C3+C7 | C3+2（C3-009 **P1**/C3-010 P2）；C7+0；CL-026 | C7 终止（15 条收官）；C3 需续 |
| R4（收敛） | C3 | +2（C3-011/012 P2）+ 扩展 C3-001 | 终止（矩阵 32 站点穷举闭合） |

- **深挖后累计 54 条**：C1×15（P1×7/P2×6/P3×2）、C3×12（P1×6/P2×6）、C6×5（P0×1/P1×3/P2×1）、C7×15（P1×6/P2×8/P3×1）、C8×7（P1×1/P2×5/P3×1）→ P0×1/P1×23/P2×26/P3×4；CL×26
- 轮次文件独立落盘（round2/3/4），未改前轮内容

### Phase 4 独立复核（2026-10-19，5 维度串行）

| 维度 | 判定 | 最终严重度 | 现状标注 | 关键裁定 |
|------|------|-----------|---------|---------|
| C1 | 保留 15/驳回 0 | P1×7/P2×6/P3×2 | 新×14/已知未修×1 | C1-007 反证假说被逐层证伪维持 P1；附口径 3a 勘误（门翻转可见性归平台同案） |
| C3 | 保留 12/驳回 0 | P1×6/P2×6 | 新×11/已知未修×1 | C3-001 触发链修正维持 P1；C3-008/009 非同根双立 |
| C6 | 保留 5/驳回 0 | **P0×1**/P1×3/P2×1 | 新×3/已知未修×2 | **C6-002 P0 维持（四环穷举）**；C6-002↔C3-003 同案合并取 P0 |
| C7 | 保留 15/降 1 | P1×5/P2×9/P3×1 | 新×13/已知未修×2 | C7-005 P1→P2（零生产实例化）；PubSub 四面非同案；C7-002 javadoc 矛盾属实 |
| C8 | 保留 7/驳回 0 | P1×1/P2×5/P3×1 | 新×7 | C8-001 四重登记建议 7.1 裁 P1；C8-003/004 触发时序修正（会话层分块）不改级 |

- **复核后最终保留 54 条：P0×1/P1×22/P2×27/P3×4**；已知未修复×6、新发现×48、已修复×0

### Phase 5 C10 测试保护力（2026-10-19）

- 保护力矩阵 54 行：**会红 0 / 假保护 30 / 无测试 24**；缺保护登记 C10-001…039（全 P2，4 条随本体可降 P3）
- 弱反向钉死 RP-1（TestMigrationHistoryManager 钉住「冲突必须覆盖」）、RP-2（TestIncrementalLagProbe 钉住「静止=追平」）
- 独立复核（返回消息被配额中断吞没，工件盘上完整）：54/54 维持、12 行回码抽查零误判、39 登记全保留、5 项叙事勘误（含新发现 48/已知未修复 6 统计修正）
- 配额中断记录：C10 复核 subagent 返回时命中 5h 限额（12:59 重置），按盘上工件完整性计入结果

### Phase 6 单元内合并裁定（2026-10-19）

- 独立裁定子代理串行派发（未参与五维初审/复核通道）；输入 = 五维度 `维度复核结论`+`最终保留项` + round 条目 + cra-index WI 1.1–2.4 台账 91 条 P1 + 历史前案
- 指针冲突裁定：X-1 C1-005 双主张裁归 MA-05（C3 复核「子项③归 C1-005」让渡）；X-2 线索 C1-CL-007 主处置归独立 #13（C7-002）；其余已定指针无冲突
- 裁定员新增合并 3 组：C6-003+C1-004（停止协议族→MA-04）、C8-003+C8-004（驱动批写同批→MA-11）、C1-014+C1-015（P3 族→MA-12）
- 产物：merge-adjudication.md（54 = 29 族成员 + 23 独立 + 1 watch + 1 跨单元随案 → 35 工单：P0×1/P1×13/P2×20/P3×1）+ summary.md（附录 B 格式）+ cra-index 登记（报告行/P0 行/13 P1 行/CL 2.5 小节；P1 累计 91→104）
- 开放裁定 O-1：MA-02（PubSub 投递 IO 内联）严重度单元内建议 P1、终裁移交 7.1
- closure audit 断点补记：本节为 audit FAIL 阻断项 #1 的修复（audit 其余 10 项 PASS）

### Phase 7 P0 即时通道（2026-10-19，主 agent 执行）

- **P0 = C6-002/MA-03**（成员 C3-003 同案合并）：修复 `OrmMigrationOrchestrator`
  - 新增 `stopIncrementalChains`（字段直读，不惰性建管理器——重启后字段空即链已亡）
  - `confirmSwitch` 翻转+路由重建成功后停链（消除幽灵写窗口）；`completeSwitch` 开门前兜底幂等停链
  - 新增 `setIncrementalManager` 注入 seam（与 setStateStore 同型）
- 回归测试 `TestMigrationSwitchStopsChains`（2 例）：收敛停链断言 + 非 incremental 配置不动管理器；**语义回退实测 Failures: 1 必红**
- 模块套件 52 测试全绿；scan-hollow（--severity high）退出码 0
- owner doc：data-migration.md SWITCH 节补停链步骤；cra-index P0 行 → 已修复
