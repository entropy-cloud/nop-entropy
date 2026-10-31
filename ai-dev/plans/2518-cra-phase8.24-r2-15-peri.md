# 2518 CRA Phase 8.24 — R2-15 2.5 peri 批（PubSub 断链 + 链编排 fencing，11 项：订阅管理监视器化 + IO 线程内联消费外移 + 重连重订阅 + 停止协议可验证 + 状态行版本谓词/单飞 fencing + 死链 fail 门禁 + 部分启动补偿 + 历史表原子守卫 + lag 键派生 + relocate querySpace + 状态机受控自环）

> Plan Status: active
> Draft Review: 独立子 agent draft review 已完成（R1 见下），修订转 active
> Draft Review R1: agent_2e87dfd0 @ 2026-10-31 —— 无 Blocker；1 Major（M-1 成员#8 站点行号 +10 漂移）+2 Minor（C10-022 七钉计数、裁定⑪错字）——协调方机械核验修复闭环，修订转 active
> Last Reviewed: 2026-10-31
> Source: roadmap 工作项 8.24（R2-15，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L166，Status=todo[本 plan 立项时回写为「todo（plan 2518 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-15 行（L89 全文：finding ID 清单 11 项[2.5-C6-004；2.5-C8-001；2.5-C7-014；2.5-C6-003；2.5-C7-003；2.5-C7-001；2.5-C3-001；2.5-C1-003；2.5-C3-008；2.5-C3-009；2.5-C7-002] / 模块与验证命令 nop-persistence `./mvnw test -pl nop-persistence -am` / Owner Doc=`docs-for-ai/02-core-guides/model-first-development.md`（迁移 owner 面）；`docs-for-ai/04-reference/source-anchors.md` / PA=无（批内条件申报条款见头部 PA 行）/ 依赖「无前置（可并行）；**批内次序：C3-008 先于 MA-06**（键派生修复顺序前置）；R19 前案 cross-ref（check/nosql-cdc 2026-08-19）；MA-02 携反向钉死改写」/ exit criteria 增补：**B1-02 终裁 P1 落账（严重度采 2.5 O-1 建议）；PubSub 属主校验/停机对账/重订阅检测；IO 线程内联消费外移+ack 契约恢复；链状态行版本谓词+单飞/fencing；迁移历史表原子守卫；relocate querySpace 修复；写门持久化半边归 R2-04 注记**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 2.5 审计目录 `ai-dev/audits/2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`（C6-resource-lifecycle.md + C6-round2.md[C6-003/004]、C8-backpressure.md + C8-round2.md[C8-001]、C7-long-running.md + C7-round2~3.md[C7-001/002/003/014]、C3-async-chains.md + C3-round3~4.md[C3-001/008/009]、C1-shared-mutable-state.md + C1-round2.md[C1-003/004]、merge-adjudication.md §一 MA-01~06/MA-10 + §三 X-1/X-2/X-3 + 附录 O-1[MA-02 严重度终裁] + 台账 :288-324、C10-test-protection.md[C10-002/003/004/005/006/010/011/015/021/022/023/025 + RP-1/RP-2 弱反向钉死 :104-107]）；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` §二 WI 2.5 节 :386 起 + 小结行 :787[**2.5（39，随本体全映射）**——本批仅承载 11 主 ID 对应成员面，其余 28 条归 R4-20/R4-32/deferred 各本体]；P1 台账 = cra-index `ai-dev/audits/cra-index.md` :226–238（live 2026-10-31 回读：R2-15 open 十一行 = :226[C6-004]/:227[C8-001]/:228[C6-003]/:229[C7-003]/:230[C7-001]/:231[C3-001]/:232[C1-003]/:234[C3-008]/:235[C3-009]/:236[C7-002]/:238[C7-014]；:233[C1-007] 与 :237[C7-004] 已 fixed[R2-04/plan 2507] 不属本批）；执行基线 @ `eb18eed4a0`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.23 已 done，plan 2495–2517 均已完成或收口；**本 Baseline 全部行号为该基线 live 回读值，2026-10-31 亲读，执行前如再漂移以 live 为准并登记**）
> Related: plan 2472（completed——2.5 P0 C6-002 停链先例：`OrmMigrationOrchestrator.stopIncrementalChains` :401-407 字段直读不惰性建管理器 + `TestMigrationSwitchStopsChains` 双钉在案——MA-04 与 MA-03 同 diff 修复的收敛点骨架，零回退钉）；plan 2507（completed——R2-04 写门租约 fail-open + `shardSelector` volatile 发布先例（source-anchors `ORM-MIG-001`），`TestWriteGateWindowRecheck`/`TestShardSelectorHotSwapVisibility` 双钉零触碰）；plan 2514（completed——WfTaskScanner 条件 UPDATE 抢占/fencing 形态菜单裁定⑥[条件 UPDATE/rowsAffected==1/租约/列集约束]，MA-05 状态行版本谓词引用不重建）；plan 2516（completed——batch fencing 先例：restartTime stale 接管消费 2514 裁定⑥引用不重建的范式样板）；plan 2517（completed——R2-14 2.4 ORM 批结构模板先例：PA 判定表/勘误登记/红绿判据/C10 半边口径）；**R19 前案**：check/nosql-cdc 2026-08-19 [P2]（C6-004 同站点已知未修复前案，本批收口时在 cra-index 注记消案）；**下游**：R4-20（8.60，G47 13 项，**软依赖本批**）与 R4-32（C7-013）——批末须回写就绪注记（**G47 行注记：C7-008 修复对齐 P1 C3-008[其健康子断言被本批⑨推翻]，键派生裁定为下游 SSOT**）；**R4-24（8.5x G62）REGISTRY 契约域**：C7-014/C6-004 与 4.1 REGISTRY 契约（`ai-dev/design/concurrency/registry-lifecycle-contract.md` 裁定⑥ WI 2.5 消费面互斥分层表态）为对齐注记零触碰关系（沿 2511/2512 先例口径）
> Protected Area 授权：批次 PA=**无**（batch-proposal R2-15 行 PA 列显式「无」）。**批内条件申报条款（两处，条件触发制）**：① **C8-001 消费外移 executor 落点**——预设 = 复用 nop-commons/nop-core 现有 executor 设施或 nop-nosql-lettuce 模块内私有执行器，`IMessageService`/`IMessageConsumer` SPI（nop-api-core）**零签名变更**；若 Phase 1 裁定确需在 `io.nop.api.core.*` 新增平台工具/接口，触发 AGENTS.md「nop-api-core plan-first」，须在本 plan PA 行 + 设计文档 + draft review 完成申报并列全 SPI 回归面（IMessageService 全实现：lettuce/本地/memory 等 live grep 全列）方可实施；② **C7-003 fencing 形态**——预设 = 状态行 UPDATE 版本/相位谓词 + 进程内单飞 + （跨进程面）条件 UPDATE/租约语义（引用 plan 2514 裁定⑥形态菜单不重建），全部为自治表 `nop_orm_migration_job` JDBC 面（**非 ORM 模型结构**，`MigrationJobStateStore` DDL 字符串，不触 `model/*.orm.xml`）；若裁定演进为触任何 orm 源模型须先 plan-first 申报。**头部预设：11/11 成员零 orm.xml 触碰**（PA 判定表逐一核实）。

## Purpose

关闭 R2-15 批的 11 项已确认缺陷（**全 P1**[C8-001 严重度 7.1 终裁、档位采 2.5 O-1 建议统一 P1——B1-02 落账义务]；cra-index open 十一行 :226–232/:234–236/:238 批次列=R2-15，live 2026-10-31 回读全 open；WI 2.5 单元 54 复核 35 工单中本批 = 修复优先级 P1 全集 11 张工单，其余 P2×20/P3×1 归 R4-20/R4-32/G49 deferred 各本体）。核心结果面有六：①**PubSub 订阅链断链三单统一修复**（C6-004 + C8-001 + C7-014，增补段「PubSub 属主校验/停机对账/重订阅检测」+「IO 线程内联消费外移+ack 契约恢复」）——cancel/suspend/resume 绕过监视器三步变更共享 entry/注册表/通道使订阅静默失效或复活双投递；消费回调在共享 Redis IO 线程内联执行使慢消费者队头阻塞→服务端断链→静默丢消息且 ack 契约被整体丢弃；断线自动重连后 Redis 侧订阅全丢且无重订阅无检测。②**链停止协议可验证**（C6-003←C1-004，与 MA-03[plan 2472 已修 stopIncrementalChains] 同 diff）——thread 字段晚于 start() 发布使 join 空转、abort 读 stale-null、stopAll 仅 cancel 不等待。③**链编排 fencing**（C7-003←C1-005/006/009 + C7-002←C1-CL-007/008，增补段「链状态行版本谓词+单飞/fencing」）——状态行全列覆盖无版本谓词使 abort×confirmSwitch 背离终态、7 编排入口零单飞使多节点双跑双链；五状态机 ALLOWED 零自环使 javadoc fix-forward 承诺不可达、一次瞬时故障全量报废。④**链死亡可见**（C7-001←C3-002 等，**先修 C3-008**，增补段内「键派生修复顺序前置」）——getFailure 全仓零调用方、lag=0 门禁把死链判追平、VERIFY 放行缺数目标。⑤**启动补偿与键族**（C3-001←C6-001 + C3-009 + C3-008）——startFallbackChains 部分启动无补偿、jobName 丢 querySpace 句柄 put 覆盖、lag 探测 connector 名与链注册名 base-i 错位、relocate 路由 querySpace 硬编码空串 100% 丢事件。⑥**迁移历史表原子守卫**（C1-003，增补段「迁移历史表原子守卫」）——全守卫非原子 check-then-act 使多节点并发 DDL 重复执行 + FAILED 脏行覆盖 success 行永久重试循环。批末 roadmap 8.24 → done，cra-index 十一行收敛 + C10 成员面注记，R4-20/R4-32 软依赖就绪注记（含 C3-008 键派生裁定指向），R19 前案（check/nosql-cdc 2026-08-19）消案注记。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-15 = 11 项成员（**全 P1**；C8-001 判级 = MA-02 族 O-1 终裁：统一 P1、严重度 7.1 记录保留——merge-adjudication §附录 O-1 四点[字节码独立验证/onMessage 契约无条件子面/已声明边界抗辩不覆盖消费停摆/P1 锚点命中]；P1 修复顺序硬约束 **C3-008 先于 MA-06[C7-001]**——C7 复核明文「先修 C3-008 键派生错位，否则键错位面使修复无效」）；cra-index live 2026-10-31 回读 R2-15 批次列 open 十一行 = :226–232、:234–236、:238（C1-007 :233 与 C7-004 :237 已 fixed 不属本批）；roadmap 8.24 行 Status=todo。前置：**无前置（可并行）**——roadmap 8.1–8.23 已 done，基线门禁满足。
- **同单元批外面登记（Non-Goal 零触碰）**：WI 2.5 其余 24 张工单归 R4-20（8.60，G47 13 项：MA-07/MA-08/MA-09/MA-11/C1-001/C1-002/C1-008/C3-010/C3-011/C3-012/C7-006/C7-008/C7-009）、R4-32（C7-013）、G49 deferred 束（C7-005/C7-015/C8-005/C8-007 零调用方）、已 fixed 的 C6-002/C1-007/C7-004/C6-005 及 watch-only C8-006——本批 diff 不触碰其任何站点。**G47 行义务预登记**：C7-008（P2）修复时对齐本批 C3-008 键派生裁定（其「offset 指纹健康」子断言已被 C3-008 推翻）——Phase 4 就绪注记转达；C1-008 与 MA-05 同文件同 diff 义务属 R4-20（本批 MA-05 修复落在 OrmMigrationOrchestrator/MigrationJobStateStore 编排层，R4-20 C1-008 站点为 full_task_name 写点，Phase 1 裁定⑤须登记文件级接缝不代做）。
- **吸收成员（随主条目同 diff，不单列工单；merge-adjudication §一 + §三 X 裁定）**：C6-004←C1-011+C3-004（MA-01 闭三角 X-3，PubSub 失效面①；前案 check/nosql-cdc 2026-08-19 [P2] 同站点已知未修复——收口消案义务）；C8-001←C3-005+C7-010（MA-02 闭三角，失效面④，与 MA-01/C7-009/C7-014 共享连接拓扑统一设计）；C6-003←C1-004（MA-04 族判定 ：53——站点与证据行完全重合，C1-004 另贡献 thread 晚于 start() 发布序面）+CL-009；C7-003←C1-005/C1-006/C1-009（MA-05 并集裁定）；C7-001←C3-002+CL-006/CL-018/CL-023（MA-06）；C3-001←C6-001（MA-10 同单指针）+ round4 relocate 扩展面；C7-002←C1-CL-007/C1-CL-008（X-2：CL 主处置归 C7-002 工单）。**X-1 边界裁定**：C1-005 归 MA-05（C7-003 工单）**不归 MA-10**——MA-10 保留部分启动无补偿 + jobName 非单射两面；MA-05 与 MA-10 共享 `runningJobs` 注册表站点，**登记同 diff 协调（单飞 putIfAbsent 改造与回滚回收一批设计），家族关联不并案**。
- **族登记（family-clustering）**：C3-001 入 MA-10 与 C3-008/C3-009/C3-010/C3-011「qs 身份键模式族」（C3 复核裁**非同根不并案、统一设计**——本批承载 008/009 两个键族成员 + C3-001 jobName 面，C3-010/C3-011 归 R4-20 只登记键派生 SSOT 接缝）；C8-001 与 WI 2.3 C8-001（WS 推送阻塞钉死共享池 P1 在账）同族先例、与 WI 1.7 C8-001/002 批量护栏族站点不同不并案；C7-002 fix-forward 与 MA-03/MA-04 统一设计（merge-adjudication :252 注记）。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` :787 小结行 2.5（39，随本体全映射）+ `C10-test-protection.md` 逐条；随本体同 diff 落地[E2]）**：
  - C10-022→C6-004（并发订阅管理护栏零覆盖；现有 7 PubSub 测试全串行[R1 勘误：沿抄 C10 源 6 为笔误，live TestLettuceNosqlService 七钉 :558-803]、全类 docker-gated 默认不跑；修复同 diff 补并发压力钉[mock listener 层非 docker 可跑]）；
  - C10-023→C8-001（投递线程/慢消费者阻塞/扇出完整性零断言；MA-02 反向钉死改写义务随批[batch-proposal :273]——若消费外移使「发送后立即断言收到」时序面改变，须同 diff 改写并以 await 型断言替代，登记语义对照）；
  - C10-025→C7-014（断线重连重订阅零覆盖；C7 round3 回归面 5 项为设计输入；与 MA-01 竞次面登记）；
  - C10-003+C10-010→C6-003（MA-04 停止协议全枚举零测试；「completeSwitch 空 runningJobs 门禁」修复可无障碍落地，plan 2472 双钉保持绿）；
  - C10-011→C7-003（MA-05 abort×confirmSwitch 背离/双节点双跑零钉）；
  - C10-004→C7-001（**含 RP-2 弱反向钉死**：`TestIncrementalLagProbe#testLagZeroWhenNothingAdvances` :42-47 与 `testLagZeroWithStaticOffsetsAndLedger` :90-100 把「冻结=追平」钉为正确语义——修复按复核裁定顺序**在编排器层消费 failure**，probe 层零改动即两钉保持绿；若裁定确需 probe 层活性句柄则同 diff 改写并登记）；
  - C10-002→C3-001（MA-10 部分启动孤儿链零钉）；
  - C10-021→C1-003（**含 RP-1 弱反向钉死**：`TestMigrationHistoryManager#testRecordMigrationReplacesExistingRecord` :253-278 显式钉「重试成功必须替换已存在行（UPDATE 覆盖），不拒绝」——修复须按「跨进程互斥（锁/租约）+ 保留 upsert 语义」方向实施，**不得走「冲突即拒绝/insert-only」方向**）；
  - C10-005→C3-008、C10-006→C3-009、C10-015→C7-002（无 X→X 自环断言、反向钉死排查通过——受控自环修复不触红）。
- **2.5 批前已收口面（live 形态登记）**：C6-002（P0，plan 2472）——`stopIncrementalChains` :401-407 + `setIncrementalManager` :117-118 注入缝 + `confirmSwitch` :303/:317 收敛停链已在案（MA-04 同 diff 修复的既有骨架）；C7-004+C1-010（plan 2507）——写门租约 fail-open + `requireGateStillClosed` :565-568 窗口复核在案（本批零触碰）；C1-007（plan 2507）——`shardSelector` volatile 在案（本批零触碰）；C6-005（plan 2504 双轨）已收敛。
- **平台设施与测试资产盘点（live 实读 @ `eb18eed4a0`）**：
  - **先例范式可引用不重开**：plan 2514 裁定⑥条件 UPDATE/租约形态菜单（MA-05 fencing 引用不重建）；plan 2516 restartTime stale 接管（fencing 先例样板）；plan 2507 `ORM-MIG-001`（写门租约/窗口复核/shardSelector volatile，同模块先例）；plan 2509/2510 R03「出锁投递」范式（C8-001 消费外移引用形态，引用不重开）；`ai-dev/design/concurrency/registry-lifecycle-contract.md` 裁定⑥（4.1 REGISTRY 契约域对齐注记零触碰）；`DaoTaskStateStore.rowLock` 条带锁范式（2514，C1-003 跨进程互斥备选形态引用）。
  - **owner doc 现状**（2026-10-31 亲读）：`docs-for-ai/02-core-guides/model-first-development.md`——含 plan 2517 新增「ORM 会话与事务并发契约」节与 plan 2507 写门/shardSelector 声明；**无 ORM 迁移编排并发契约节（阶段迁移 fencing/停止协议/死链门禁）、无迁移历史表守卫契约句、无 PubSub 消息投递线程纪律契约节**——裁定④⑤⑥⑦⑧⑪ owner doc 补节主落点（迁移 owner 面）。`docs-for-ai/04-reference/source-anchors.md`——已有 `ORM-MIG-001`（OrmMigrationOrchestrator 锚点）；本批补锚点落点（IncrementalJobManager/LettucePubSubService/MigrationJobStateStore/MigrationPhase 按实际）。
  - **测试资产**：`TestLettuceNosqlService`（nop-nosql-lettuce，**全类 docker-gated** :57-58 默认不跑——新增并发护栏钉须 mock listener 层非 docker 可跑）；`TestMigrationSwitchStopsChains`（plan 2472 双钉）；`TestIncrementalLagProbe`（RP-2 双钉）；`TestMigrationHistoryManager`（RP-1 钉 + `testRecordMigrationKeepsExistingRowWhenWriteFails` :299）；`TestMigrationPhase`（状态矩阵 6 边+非法 6 边钉）；`TestMigrationPipelineE2E`/`AbstractMigrationE2ETestCase`（E2E 载体）；`TestCdcOffsetSeedBuilder`/`TestSourcePositionPinner`/`TestConsistencyCanonical`（邻接钉）。
- **模块聚合形态**：`nop-persistence` 为 Maven 聚合器；本批站点分布于三模块：`nop-persistence/nop-nosql/nop-nosql-lettuce`（PubSub 三单）、`nop-persistence/nop-orm-migration`（编排七单）、`nop-persistence/nop-db-migration`（C1-003）——E3 命令 `./mvnw test -pl nop-persistence -am` 触达三模块及上游链。**勘误注记**：「nop-persistence-peri」为审计单元虚拟标签（覆盖 peri 域多模块），非 repo 实际模块目录；repo 实际以 `nop-persistence/` 聚合器下子模块为准（live find 核实：无 `nop-persistence-peri` 目录，基线清单 `_tmp/cra-baseline-nop-persistence-peri.txt` ROOT 覆盖 nop-dbtool/nop-db-migration/nop-orm-migration/nop-nosql 等）。

### 对齐纪律显式条款

- **批内修复顺序硬约束（C3-008 先于 MA-06）**：裁定⑨（键派生）必须先于/同变更集先行落地于裁定⑥（死链 fail 门禁）——否则 M>1 下 lag 观测量恒空使门禁修复无效（C7 复核明文）。执行次序写死：组 B 内 ⑨→⑥；Phase 2 提交序列 ⑨ 单独先行或与⑥同变更集且 ⑨ 变更在前。
- **MA-04 × MA-03 同变更集**：停止协议修复（发布序+有界 join+interrupt）与既有 `stopIncrementalChains` 收敛点（plan 2472 :401-407）同一变更集承载——原语与调用点一批修（merge-adjudication :105「均绑定 MA-03 同 diff」）；`TestMigrationSwitchStopsChains` 双钉保持绿为同变更集约束面。
- **MA-05 × MA-10 同 diff 协调（X-1，不并案）**：`runningJobs` 注册表共享站点——MA-05 的单飞/putIfAbsent fail-fast 改造与 MA-10 的回滚回收/jobName 键派生一批设计；两工单台账独立收敛、cra-index 各行括注「同 diff 协调（X-1）」。
- **PubSub 三单统一设计（C6-004/C8-001/C7-014）**：共享连接拓扑，Phase 1 收敛为单一连接/订阅生命周期裁定（失效面①②③④全枚举）；**C7-014 重订阅与 face① 竞次面**（C7 round3 回归面 #1/#2）为裁定③设计输入——重订阅不得与订阅管理监视器产生复活窗口；与 4.1 REGISTRY 契约域对齐注记零触碰。
- **RP-1/RP-2 修复方向约束**：C1-003 保留 upsert 语义（互斥方向修复）；C7-001 编排器层消费 failure（probe 层零改动）——两约束为裁定⑥⑧的硬边界，违反即反向钉死改写义务触发（若裁定确需改钉，同 diff 改写 + 语义对照留痕，参照 C10-009 先例口径）。
- **跨单元同号防混**（inventory-p2-c10 :875/876）：C7-001（1.5/1.6/1.7/2.1/3.1/5.5 六处同名异站）——本批成员恒带单元前缀 2.5；C3-008（1.3/2.5/4.3 同号异缺陷）、C6-001/C6-005、C1-003 与 2.4-C1-003（plan 2517 已修，模型发布 volatile 面，与本批历史表 C1-003 同号异站零关联）——cra-index 回填时逐行带 2.5 前缀核对。

### 11 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `eb18eed4a0` 2026-10-31 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.5-C6-004（P1；←C1-011/C3-004 双并入[MA-01 闭三角]；R19 前案 check/nosql-cdc 2026-08-19 [P2]；C10-022） | `LettucePubSubService.java`（nop-nosql-lettuce `impl`） | `subscribe` synchronized `:72-94`；`PubSubSubscription.cancel` 无同步 check-then-act `:190-200`（`entry.removeListener` :192 → `entry.isEmpty()` :193 → `unsubscribe`+`subscriptions.remove` :196/:198）；`suspend` `:213-218`；`resume` `:221-226`；`SubscriptionEntry.addListener/removeListener` `:127-139`（listeners.size()==1 门非原子） | PubSub 订阅管理竞态：cancel/suspend/resume 绕过 subscribe 的同一监视器三步变更共享 entry/listeners/注册表/通道——订阅静默失效或复活双投递；并发 addListener×removeListener 使 size==1 门误判 addListener(this) 泄漏 | 裁定①（C10-022 并发钉随同 diff；增补段「PubSub 属主校验/停机对账」）：cancel/suspend/resume 入与 subscribe 相同的监视器（synchronized(this) 收敛单锁域）+ cancelled 重入守卫 + 锁内空判复查（isEmpty 判定与 unsubscribe/remove 原子化）；订阅属主校验（cancel 仅属主 listener 生效）；doStop 停机对账（stop 时全订阅 unsubscribe 对齐——与裁定③对账面共享）；6 个既有串行钉零弱化 |
| 2 | 2.5-C8-001（P1[B1-02 终裁，严重度 7.1 记录保留]；←C3-005/C7-010[MA-02 闭三角]；MA-02 反向钉死改写义务随批；C10-023） | `LettucePubSubService.java`（同 #1）；`IMessageConsumer.java`（nop-api-core，SPI 零签名变更面） | `SubscriptionEntry.message` IO 线程内联 `:146-161`（lettuce `notifyListeners` 直迭代无 executor 卸载——lettuce-core 7.5.2 字节码核验；`consumer.onMessage` :154 同步调用、异常仅 onConsumeFailure :157-158）；`onMessage` 返回值（含 ack 形态）被整体忽略 :152-160 | PubSub 消费回调在共享 Redis IO 线程内联执行：慢消费者队头阻塞→服务端断链→静默丢消息；消费回调返回值契约被整体丢弃（无等待/无 ack 语义） | 裁定②（C10-023 随同 diff + 反向钉死改写义务履行）：消费外移独立执行器（预设=nop-nosql-lettuce 模块内私有 executor[对齐 R03 出锁投递范式引用不重开]；若裁定需 nop-api-core 平台设施须批内 PA 申报①）；ack/返回值契约一次定夺（等待完成或有界超时告警）；慢消费者隔离与执行器有界化（队列上界/拒绝策略显式，禁静默丢弃——rule 24）；**MA-02 反向钉死改写**：`TestLettuceNosqlService` PubSub 串行钉若因异步投递时序化须同 diff 改写为 await 型断言（语义对照留痕）；SPI 回归面 = IMessageService 全实现零签名变更 |
| 3 | 2.5-C7-014（P1；独立#20；C10-025） | `LettucePubSubService.java`（同 #1/#2 站点族） | `doStop` 仅 close `:112-117`；连接重建路径零重订阅（`getOrCreateConnection` :96-105 仅首次建连）；Redis 侧订阅注册无对账无检测（subscribe :82 成功后无活性跟踪） | PubSub 断线自动重连后 Redis 侧订阅全丢且无重订阅无检测：静默失联直至重启（lettuce-core 7.5.2 字节码核验——F5 完整形态，长时运行必然触发） | 裁定③（C10-025 随同 diff；增补段「PubSub 重订阅检测」；与裁定①②同一连接生命周期设计）：连接重建侦听/首次订阅对账一次定夺——(a) 预设=以 subscriptions 注册表为 SSOT 的重订阅对账（连接状态回调触发 re-subscribe 全量）+ 检测告警（对账失败显式 WARN/计量）；(b) 备选=每次 message 收发心跳对账；**竞次面约束**（C7 round3）：重订阅与裁定①监视器共享锁域或显式次序，禁复活已 cancel 订阅 |
| 4 | 2.5-C6-003（P1；←C1-004[MA-04 族判定]+CL-009；**与 MA-03[plan 2472] 同变更集**；C10-003/C10-010） | `IncrementalJobManager.java`（nop-orm-migration）；`OrmMigrationOrchestrator.stopIncrementalChains`（:401-407，plan 2472 已修骨架） | thread 发布序：`thread.start()` :184 **先于** `running.thread = thread` :185（volatile 字段 :214 晚发布）；`stopAll` 仅 cancel 不等待 `:201-206`（`job.stop()`=source.cancel :236 后立即 `runningJobs.clear()` :205）；`join` null 短路 `:239-243`（`t != null` 判定在 stale-null 时静默跳过）；`getFailure` 观测面 :230-233（零调用方，见 #6） | 链停止协议不可验证：thread 晚于 start 发布使 join 空转、abort/stopAll 读 stale-null 跳过停链、stopAll 仅 cancel 不等待——「停止完成」不可观测，链线程/binlog 连接滞留窗口不可界定 | 裁定④（C10-003/C10-010 随同 diff；**与 MA-03 同变更集**）：(a) 发布序修正（构造期注入 thread 或 start 前赋值——发布先于启动）；(b) `stop()` 改 cancel→有界 join→超时 interrupt→句柄保留至线程退出（`stopAll` 汇聚逐链 join，等待上界配置化）；(c) join null 短路改显式（未启动态 fail-fast 或登记跳过原因——禁静默）；plan 2472 双钉零弱化 |
| 5 | 2.5-C7-003（P1；←C1-005/C1-006/C1-009[MA-05 并集，X-1]；C10-011；**与 MA-10 同 diff 协调**） | `MigrationJobStateStore.java` + `OrmMigrationOrchestrator.java`（nop-orm-migration）；`MigrationPhase.java`（自环面归 #11） | `update` 全列覆盖无版本/相位谓词 `:100-118`（`WHERE job_id=` 单条件 :114；updated!=1 仅查行缺失 :116-117）；编排入口零单飞/fencing：`runIncremental` `:193-214`、`runVerify` `:218-`、`confirmSwitch` `:277-`、`completeSwitch` `:310-`、`abort` `:329-`（五入口无并发防护；resume 分派 :161-176 同域）；`incrementalManager()` 惰性访问器无同步 `:521-530`（C1-005/C1-009 站点）；`runningJobs` 注册表 `IncrementalJobManager.java:50`（put 覆盖面归 #7） | 状态行全列覆盖无版本谓词 + 编排入口零单飞：abort×confirmSwitch 并发背离终态（abort 落账被 confirmSwitch 全列覆盖回 VERIFY）；多节点双跑双链；惰性管理器双实例 | 裁定⑤（C10-011 随同 diff；增补段「链状态行版本谓词+单飞/fencing」；**引用 plan 2514 裁定⑥形态菜单不重建**；与裁定⑦同 diff 协调）：(a) 状态行 UPDATE 加版本/相位谓词（`WHERE job_id=? AND phase=?`（期望相位）或乐观版本列——自治表 DDL 面裁定，非 orm.xml）；(b) 编排 API 进程内单飞（per-jobId 锁/AtomicReference putIfAbsent fail-fast）+ 跨进程面条件 UPDATE fencing（多节点双跑以谓词拒绝，第二写 updated==0 显式失败）；(c) `incrementalManager()` synchronized/double-check（C1-005/C1-009 吸收面）；文件级接缝登记：C1-008（R4-20）full_task_name 写点同文件不代做 |
| 6 | 2.5-C7-001（P1；←C3-002+CL-006/018/023[MA-06]；**修复顺序前置 = 裁定⑨先落地**；RP-2 弱反向钉死；C10-004） | `IncrementalJobManager.java` + `OrmMigrationOrchestrator.java`（同 #4/#5 站点族）；`IncrementalLagProbe.java`（**零改动面**——RP-2） | `getFailure` 全仓零调用方（live grep `.getFailure(` 0 命中 @ 2026-10-31；字段 `failure` AtomicReference :215、写点 :180）——链死亡不可见；lag=0 门禁 `runVerify` :231 `probe.awaitLagZero(connectorName, ...)` 死链冻结位点被判追平；VERIFY 放行缺数目标（:238 起 verify 流程） | 链死亡不可见：getFailure 无消费方、lag=0 门禁把死链判追平、VERIFY 放行缺数目标（fail-fast 契约 javadoc 有、实现无） | 裁定⑥（C10-004 随同 diff；**硬前置：裁定⑨键派生先行**；**RP-2 约束：修复在编排器层消费，probe 层零改动**）：编排链路闭合一次定夺——(a) 预设=runIncremental 后台守卫/VERIFY 前置检查消费 `getFailure()`（failure 非空即 gateFailed 类型化拒绝）+ stopIncrementalChains 后残留 failure 复核；(b) 备选=链活性句柄注入 probe（触发 RP-2 改写义务，须申报）；死链即 fail 门禁语义 owner doc 落账 |
| 7 | 2.5-C3-001（P1；←C6-001[MA-10 同单]+round4 relocate 扩展面；**与 MA-05 同 diff 协调[X-1]**；C10-002） | `IncrementalJobManager.java`（同 #4）；`OrmMigrationOrchestrator`（qs 键面） | `startFallbackChains` 部分启动无补偿 `:121-141`（循环 :134-139 中途 `startChain` 抛异常——已启动链滞留，`started` 未回收）；`jobName` 丢 querySpace `:174`（`"orm-migration-incremental-" + jobId + "-" + target.getTableName()`，多 qs 同名目标表撞键）；`runningJobs.put` 覆盖 `:186`（同 jobName 二次启动静默顶替旧句柄→孤儿链不可停且双写同目标表） | startFallbackChains 部分启动无补偿 + jobName 非单射 put 覆盖：中途失败孤儿链不可停；多 querySpace 下句柄互踩双写同目标表 | 裁定⑦（C10-002 随同 diff；**与裁定⑤同 diff 协调[runningJobs 共享站点]**；qs 身份键模式族统一设计[与裁定⑨⑩同族]）：(a) 部分启动补偿：循环 catch 后回收已启动链（逐链 stop+join，复用裁定④原语）+ 相位后翻（失败落 ABORT/FAIL 态显式）；(b) jobName 键派生纳入 querySpace（与 C3-009 route 的 qs 派生同一单点函数）；(c) `runningJobs.put` 放 `putIfAbsent` fail-fast（同名重入显式拒绝——MA-05 单飞面协同） |
| 8 | 2.5-C1-003（P1；独立#3；吸收线索 CL-022[同位点]；RP-1 弱反向钉死；C10-021） | `MigrationHistoryManager.java` + `MigrationEngine.java`（nop-db-migration `core`） | `ensureHistoryTableExists` check-then-act `:72-86`（`tableExists()` :73 → CREATE :77-85，无跨进程互斥）[R1 勘误：原审计 :82-93/:96-133 行号 +10 漂移订正 @ 2026-10-31 live 回读]；`recordMigration` UPDATE-then-INSERT 复合 `:88-135`（UPDATE :96 起 → updated==0 → INSERT :120 起；多节点并发 UPDATE 双双 0 行→双双 INSERT 主键冲突/FAILED 行覆盖对方 success 行）；启动期调用 `MigrationEngine.java:124-129`（`localHistoryManager.ensureHistoryTableExists`）+ `getExecutedVersions` :144 读面 | 迁移历史表全守卫非原子 check-then-act：多节点并发启动 DDL 重复执行 + FAILED 脏行覆盖对方 success 行致永久重试循环 | 裁定⑧（C10-021 随同 diff；**RP-1 硬约束：保留 upsert 语义**；fencing 设施与 WI 1.5 MA-23/WI 1.6 租约族对齐选型[引用不重建]）：跨进程互斥一次定夺——(a) 预设=互斥锁原语（DB 咨询锁/租约行/条件 INSERT）串行化 DDL 守卫 + recordMigration 复合原子化（条件 INSERT 捕获唯一冲突回落 UPDATE 保留覆盖语义）；(b) 备选=每版本行 `INSERT ... ON CONFLICT/ON DUPLICATE KEY UPDATE` 方言化（方言矩阵核实在案）——两形态均不得走「冲突即拒绝」方向；`testRecordMigrationKeepsExistingRowWhenWriteFails` :299 写失败保留面零弱化 |
| 9 | 2.5-C3-008（P1；独立#7；**MA-06 修复顺序前置**；qs 身份键模式族；C10-005） | `OrmMigrationOrchestrator.java` + `IncrementalJobManager.java` + `IncrementalLagProbe.java`（nop-orm-migration） | 探测键 `runVerify` :228 `config.getIncrementalConnectorName()`（原始 base 名直传）vs 链注册键 `startFallbackChains` :136 `connectorName = targets.size()==1 ? baseConnector : baseConnector + "-" + i`（base-i 派生）——`IncrementalLagProbe.offsetsFingerprint` :81-90 `NopStreamOffsetBackingStore.forConnector(connectorName)` 按名取键 | lag 探测 connector 名与链注册名 base-i 错位：M>1 主场景 offsets 指纹恒空→lag=0 门禁观测量平凡通过（结构性必然触发） | 裁定⑨（C10-005 随同 diff；**全批最早落地项**[先于裁定⑥]；qs 身份键 SSOT——R4-20 C7-008/C3-010/C3-011 消费）：键派生单点化——链注册名派生函数（base/base-i 枚举）抽单点，runIncremental 返回/登记实际 connector 名清单，runVerify 按清单逐链探测（M>1 全链指纹聚合判据一次定夺）；派生函数与裁定⑦ jobName 键、裁定⑩ route qs 同族统一设计 |
| 10 | 2.5-C3-009（P1；独立#8；与 C3-008 非同根双立；qs 身份键模式族；C10-006） | `ShardChangeEventRouter.java`（nop-orm-migration） | `route` relocate 分支 `:41-43` `new PhysicalTable("", mapping.getBaseTableName())`——querySpace 硬编码空串；`belongsTo` :59-61 filter 谓词按此 route 比对（IncrementalJobManager :172 消费） | relocate 路由 querySpace 硬编码空串：增量链 filter 恒 false 100% 丢事件、FULL upsert 落回源侧 default 表（多源 qs 下 FULL 跨源行合并写为复核新证） | 裁定⑩（C10-006 随同 diff；qs 身份键模式族与裁定⑦⑨统一设计）：`PhysicalTable` querySpace 从 mapping/config 正确派生（TargetShardMapping 携带 qs 或 router 构造期注入——一次定夺）；filter 谓词回归钉穿透 relocate 主场景；FULL 侧写点核对登记（复核新证面：跨源行合并写——如超出 router 修复面则登记边界不扩批） |
| 11 | 2.5-C7-002（P1；独立#13；吸收线索 C1-CL-007/C1-CL-008[X-2]；与 MA-03/MA-04 统一设计；C10-015） | `MigrationPhase.java` + `OrmMigrationOrchestrator.completeSwitch`（nop-orm-migration） | `ALLOWED` 零自环 `:35-41`（`INCREMENTAL→{VERIFY,ABORT}`、`VERIFY→{SWITCH,ABORT}`、`SWITCH→{DONE}`、`DONE/ABORT→∅`——X→X 全禁）；`checkTransition` :54-61 非法即抛（fix-forward 路径自身不可达）；javadoc 承诺面（设计 §4.1 fix-forward）；`completeSwitch` 收敛门 `:310-` gateFailed 校验 | 五状态机 ALLOWED 零自环：INCREMENTAL/VERIFY/SWITCH 中途失败即卡死——javadoc 承诺的 fix-forward 路径走不通，一次瞬时故障全量报废 | 裁定⑪（C10-015 随同 diff；吸收 CL-007/008 时序面[相位重入/受控自环]；**与裁定④⑤⑥统一设计**；与 MA-10 部分启动卡死面同 diff 协调[X-2]）：受控自环一次定夺——(a) 预设=失败相位白名单自环（INCREMENTAL→INCREMENTAL 重入重试/VERIFY→VERIFY，仅限显式 fix-forward API 且带重试计数/上界——非全开）；(b) 备选=新增 RECOVERY 相位边；**既有钉兼容硬约束**：`TestMigrationPhase#testIllegalTransitionsFail` :50 与 `testAbortReachableFromEveryActivePhase` :40 保持绿（受控自环仅限失败相位白名单，ABORT 可达性不减）；fix-forward API 相位后翻与裁定⑤版本谓词协同（防自环写覆盖竞态） |

### PA 判定表（11 成员逐一，orm/_生成物/跨模块公共 API/nop-api-core 触碰与否；live 2026-10-31 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 触 `_` 生成物？ | 触跨模块公共 API / nop-api-core？ | 判定依据与条件申报 |
|---|---|---|---|---|---|---|---|
| 1 | C6-004 | nop-nosql-lettuce main java | 订阅监视器化 | 否 | 否 | 否（IMessageService impl 内部） | 纯 Java 同步修复；IMessageSubscription impl 私有类 |
| 2 | C8-001 | nop-nosql-lettuce main java | 消费外移执行器 | 否 | 否 | **SPI 零签名变更面**（IMessageService/IMessageConsumer 消费不改签名） | 预设模块内私有 executor；**若需 nop-api-core 新增设施→批内 PA 申报①（列全 SPI 回归面）** |
| 3 | C7-014 | nop-nosql-lettuce main java | 重连对账 | 否 | 否 | 否 | 连接生命周期内部 |
| 4 | C6-003 | nop-orm-migration main java | 停止协议 | 否 | 否 | 否 | 线程发布/join 内部 |
| 5 | C7-003 | nop-orm-migration main java | 状态行谓词+单飞 | 否（自治表 DDL 字符串非 ORM 模型） | 否 | 否 | **若裁定演进触 orm 源模型→批内 PA 申报②**；owner doc 行为变更随 Phase 3 |
| 6 | C7-001 | nop-orm-migration main java | 门禁消费 failure | 否 | 否 | 否 | 编排器层修复（RP-2 零 probe 改动） |
| 7 | C3-001 | nop-orm-migration main java | 启动补偿+键派生 | 否 | 否 | 否 | 键派生模块内单点 |
| 8 | C1-003 | nop-db-migration main java | 历史表互斥 | 否 | 否 | 否 | DDL 守卫模块内；租约形态引用 WI 1.6 族零触碰 |
| 9 | C3-008 | nop-orm-migration main java | 键派生单点 | 否 | 否 | 否 | 模块内派生函数（NopStreamOffsetBackingStore 只读消费） |
| 10 | C3-009 | nop-orm-migration main java | route qs 派生 | 否 | 否 | 否 | PhysicalTable 值对象模块内 |
| 11 | C7-002 | nop-orm-migration main java | 状态机自环 | 否 | 否 | 否 | MigrationPhase 枚举模块内 |

**结论：头部零 orm 触碰声明成立**（11/11 = `src/main/java` 纯代码修复；站点分布于 nop-nosql-lettuce/nop-orm-migration/nop-db-migration 三模块，均无产品 orm 结构模型——live find 核实）。两处条件申报条款见头部 PA 行（C8-001 nop-api-core 形态 / C7-003 orm 源模型演进形态）。

### Baseline 勘误（输入不一致登记）

1. **模块定位勘误**：「nop-persistence-peri」为审计单元虚拟标签非实际目录（repo 无 `nop-persistence-peri` 模块；live find 核实，基线 ROOT 清单 `_tmp/cra-baseline-nop-persistence-peri.txt` 覆盖 nop-dbtool/nop-db-migration/nop-orm-migration/nop-nosql 等多模块）——本批站点实际落 nop-nosql-lettuce（PubSub 三单）/nop-orm-migration（七单）/nop-db-migration（C1-003）三模块；E3 命令 `./mvnw test -pl nop-persistence -am` 与 batch-proposal 一致。
2. **C1-005/C1-009 惰性访问器行号域跨文件订正**：审计引 `IncrementalJobManager :444-454 无同步惰性访问器`——live `IncrementalJobManager.java` 仅 260 行，该站点实际在 `OrmMigrationOrchestrator.incrementalManager()` :521-530（plan 2472 前后重构/审计基线差异）；机制（无同步惰性构造→双实例）live 复核成立，行号域订正入成员 #5。
3. **停止协议行号**：审计 :176/:184-186/:201-206/:235-243 → live thread 发布 :184-185（start 先于赋值成立）、stopAll :201-206（吻合）、join :239-243（±4）；机制零实质漂移。**「abort 读 stale-null 跳过 stopAll」表述订正**：plan 2472 已将编排器停链改字段直读不惰性建管理器（:401-407），stale-null 残余面 = thread 晚发布（MA-04 本体）——机制表述以成员 #4 为准。
4. **PubSub 站点行号**：审计 :72-94/:189-226/:145-161 → live :72-94/:190-226（±1）/:146-161；机制零漂移。
5. **C8-001 ack 契约表述订正**：审计「CompletionStage ack 契约被整体丢弃」——live `IMessageConsumer.onMessage` 返回类型为 `Object`（nop-api-core :26，CompletionStage 为其合法取值形态）；契约面 = 返回值（含 CompletionStage ack 形态）被整体忽略 :152-160——裁定②以「返回值契约」为准核实与成文，审计表述登记为口径订正。
6. **cra-index 行号基线**：R2-15 open 十一行 = :226–232、:234–236、:238（:233/:237 已 fixed 行不属本批）；cra-index 若在执行前被其他批回填致行号漂移，以「批次列=R2-15 + finding ID」定位为准并登记。
7. **C10 映射口径**：`inventory-p2-c10.md` :787 小结行 2.5（39）为全量映射；本批仅承载 11 主 ID 对应成员面（C10-002/003/004/005/006/010/011/015/021/022/023/025），其余 28 条归 R4-20/R4-32/G49/已 fixed 各本体——半边口径以本节清单为准，Phase 4 收敛注记按此书写。
8. **环境项预登记（批前既有）**：`./mvnw test -pl nop-persistence -am` 触达上游模块链（nop-kernel/nop-api-core/nop-core/nop-commons/nop-auth/nop-stream/nop-message 等）——上游环境抖动（nop-auth MFA flake、debezium fork timeout、nop-task-core 逐出测试负载间歇误红、nop-core `TestResourceCacheInvalidationChain` 间歇红[2517 执行期留痕先例]、surefire fork 启动崩溃类，沿 plan 2512–2517 已知面）按「stash 基线同红 / solo 绿 / 恢复复跑全绿」**三联裁定**处理并留痕，不得静默重跑或跳过。
9. **docker-gated 测试面登记**：`TestLettuceNosqlService` 全类 docker-gated（:57-58），默认构建不跑——本批 PubSub 修复的回归钉须 mock `RedisPubSubListener` 层非 docker 可跑（C10-022 建议口径）；docker 环境在案时补跑全类并留痕，不在案时以 mock 层钉 + 三联口径登记。

## Goals

- Phase 1 设计裁定文档一次裁决 11+1 项裁定（裁定①–⑫，含六个吸收面与三组同变更集/顺序绑定）+ 测试缝（独立 draft review 通过），无临场发明空间：①订阅管理监视器化+属主校验/停机对账（C6-004）②IO 线程消费外移+ack 契约恢复（C8-001）③重连重订阅对账与检测（C7-014）④停止协议发布序+有界 join（C6-003，与 MA-03 同变更集）⑤状态行版本谓词+编排单飞/fencing（C7-003，引用 2514 裁定⑥形态菜单）⑥死链 fail 门禁（C7-001，硬前置⑨；RP-2 编排器层约束）⑦部分启动补偿+jobName qs 键（C3-001，与⑤同 diff 协调）⑧历史表跨进程互斥+upsert 保留（C1-003，RP-1 约束）⑨connector 键派生单点化（C3-008，**全批最早落地**）⑩relocate querySpace 派生（C3-009）⑪状态机受控自环（C7-002）⑫测试缝与零回退钉清单——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。设计文档拟名 `ai-dev/design/concurrency/pubsub-link-lifecycle-and-chain-orchestration-fencing.md`（Phase 1 产出物，前向引用——落盘前 doc-link warning 3 条为预期已登记；命名裁定：PubSub 订阅/连接生命周期三单 + 链编排 fencing 七单两域，与现有 19 份命名风格一致；如内容演进更适可调，调整时在本 plan 登记命名裁定）。
- 11 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-002/003/004/005/006/010/011/015/021/022/023/025 十二条成员面）随本体同 diff 落地（E2）；MA-02 反向钉死改写义务随批履行并留痕。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红⑪逐成员登记：红① 并发订阅管理交错红（N 线程 subscribe×cancel/suspend/resume 压力断言无孤儿订阅/无复活双投递——缺陷态竞态红[压力窗]→监视器串行绿；mock listener 层非 docker 可跑）；红② 投递线程与慢消费者红（投递线程名断言 + 慢消费者阻塞时其他 topic 继续投递——缺陷态内联阻塞红→外移绿；返回值消费断言）；红③ 重连重订阅红（连接重建模拟后消息仍达——缺陷态订阅丢失红→对账重订阅绿；已 cancel 订阅不复活断言）；红④ 停止协议红（start 前置句柄观测 + stopAll 后线程存活断言——缺陷态 join 空转红→有界 join+interrupt 绿；发布序以构造断言钉死）；红⑤ 终态背离红（abort×confirmSwitch 并发后状态行终态断言——缺陷态全列覆盖回退红→版本谓词拒绝绿；双写 fencing 第二写 updated==0 显式失败）；红⑥ 死链门禁红（failure 置位后 runVerify 拒绝断言——缺陷态 lag=0 放行红→gateFailed 绿；probe 层双钉保持绿）；红⑦ 孤儿链红（第 i 链启动失败后已启动链回收断言——缺陷态滞留红→回收绿；同名重入 putIfAbsent 拒绝绿）；红⑧ 历史表并发红（并发 ensureHistoryTableExists DDL 幂等 + 并发 recordMigration 无脏行覆盖——缺陷态竞态红→互斥绿；RP-1 钉保持绿）；红⑨ 键派生红（M=2 场景 offsets 指纹非空断言——缺陷态恒空平凡通过红→逐链聚合绿）；红⑩ relocate 路由红（relocate 场景 route 返回含正确 querySpace 物理表 + filter 非恒 false——缺陷态空串丢事件红→派生绿）；红⑪ fix-forward 红（INCREMENTAL 失败→受控自环重入成功路径——缺陷态卡死红→白名单自环绿；非法自环仍拒绝绿）。
- 联调纪律显式守约（本批六条）：**C3-008 先于 MA-06 修复顺序**（裁定⑨先行或同变更集在前）；**MA-04×MA-03 同变更集**（plan 2472 收敛点骨架，双钉零弱化）；**MA-05×MA-10 同 diff 协调不并案**（X-1，runningJobs 共享站点）；**PubSub 三单统一设计 + C7-014 竞次面约束**；**RP-1/RP-2 修复方向约束**（upsert 保留/编排器层消费）；**R19 前案消案义务**（check/nosql-cdc 2026-08-19 收口注记）。
- Owner doc 随批更新：`docs-for-ai/02-core-guides/model-first-development.md`（迁移 owner 面——新增「ORM 迁移编排并发契约」节：阶段迁移 fencing/停止协议可验证性/死链 fail 门禁语义/fix-forward 受控自环契约/qs 身份键派生契约 + 迁移历史表守卫契约句 + PubSub 消息投递线程纪律契约节[Redis PubSub fire-and-forget 边界邻接]、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-20（8.60）回写软依赖就绪注记（**含 C3-008 键派生裁定指向[qs 身份键 SSOT，C7-008/C3-010/C3-011 消费不重建] + C1-008 文件级接缝转达**）与 R4-32 注记；R19 前案消案注记落 cra-index。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-20 成员**（G47 13 项：MA-07/MA-08/MA-09/MA-11/C1-001/C1-002/C1-008/C3-010/C3-011/C3-012/C7-006/C7-008/C7-009）——特别地 C3-010/C3-011 只登记键派生 SSOT 接缝不代做；C1-008（full_task_name 写点，与裁定⑤同文件）只登记接缝；C7-008 修复对齐义务转达不代做；C7-009（PubSub 失效面②）不并入本批三单（merge-adjudication MA-01/MA-02 族内联动注记为「统一设计」非「同批实施」——裁定①②③设计文档须为其登记接缝）。
- **不做 C7-013**（R4-32 拆出）；不做 G49 deferred 束（C7-005/C7-015/C8-005/C8-007，触发条款=调用方接线）；不做 watch-only C8-006。
- 不做平台层改码：`nop-api-core`（IMessageService/IMessageConsumer/BaseContext 语义）零触碰（R1-04 已收口面；C8-001 平台设施申报条款仅条件触发）；nop-commons/nop-core executor 设施本体零触碰（只消费）；4.1 REGISTRY 契约域零触碰（对齐注记关系）。
- 不做 WI 2.4/2.5 已 fixed 面（C6-002/C1-007/C7-004/C6-005）回改；不做 2.4 R2-14 批成员（plan 2517 已收口）；不做 CL 线索立案展开面（CL-009/CL-022 随主单吸收，CL-021 随 C10-022 面）。
- 不触碰 `_` 前缀生成物；R4-20/R4-32/deferred 批外成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-persistence/nop-nosql/nop-nosql-lettuce/src/`：`impl/LettucePubSubService`（裁定①②③）+ 测试（mock listener 层并发钉/线程钉/重连钉 + 既有 6 串行钉保持）。
- `nop-persistence/nop-orm-migration/src/`：`IncrementalJobManager`（裁定④⑤⑦面）、`OrmMigrationOrchestrator`（裁定④⑤⑥⑦⑨ + :521-530 惰性访问器 + 编排入口单飞）、`MigrationJobStateStore`（裁定⑤谓词）、`MigrationPhase`（裁定⑪）、`ShardChangeEventRouter`（裁定⑩）、`TargetShardMapping`（裁定⑩ qs 载体，按实际）+ 测试（红④–红⑪ + C10-002/003/004/005/006/010/011/015）。
- `nop-persistence/nop-db-migration/src/`：`core/MigrationHistoryManager`（裁定⑧）+ 测试（红⑧ + C10-021；RP-1 钉保持）。
- 设计文档：`ai-dev/design/concurrency/pubsub-link-lifecycle-and-chain-orchestration-fencing.md`（新建；裁定①–⑫+测试缝+零回退钉+qs 身份键 SSOT 条款，最终状态写法；命名调整须登记裁定）。Owner doc：`docs-for-ai/02-core-guides/model-first-development.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-20/R4-32/dormant/G49/已 fixed 批次工单；nop-api-core/nop-commons/nop-core 平台设施本体（条件申报条款除外）；`model/*.orm.xml`（预设零触碰，条件申报②见头部 PA 行）；`_` 前缀生成物手改。

## Execution Plan

### Phase 1 - PubSub 生命周期与链编排 fencing 设计裁定文档 + 独立评审

Status: planned
Targets: `ai-dev/design/concurrency/pubsub-link-lifecycle-and-chain-orchestration-fencing.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [ ] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [ ] **裁定① C6-004 订阅管理监视器化**：单锁域收敛（subscribe 既有 synchronized 与 cancel/suspend/resume 统一）+ cancelled 重入守卫 + 锁内空判复查；属主校验（cancel 仅属主 listener）；doStop 停机对账（与裁定③对账面共享）；C1-011/C3-004 吸收面闭环声明；R19 前案消案注记
  - [ ] **裁定② C8-001 消费外移与 ack 契约**：执行器落点（模块内私有 executor[预设] vs nop-api-core 平台设施[须 PA 申报①]）一次定夺；返回值契约形态（等待完成/有界超时告警）一次定夺；执行器有界化与拒绝策略显式（禁静默丢弃，rule 24）；慢消费者隔离判据；R03 出锁投递范式引用声明；**MA-02 反向钉死改写义务履行方案**（串行钉 await 化改写清单 + 语义对照）
  - [ ] **裁定③ C7-014 重连重订阅**：注册表 SSOT 对账重订阅（预设）vs 心跳对账（备选）一次定夺；检测告警面（对账失败 WARN/计量）；**竞次面约束**（重订阅×裁定①监视器次序，禁复活已 cancel 订阅——C7 round3 回归面设计输入成文）；与 4.1 REGISTRY 契约域对齐注记零触碰声明
  - [ ] **裁定④ C6-003 停止协议**：thread 发布序修正形态（构造期注入[预设] vs start 前赋值）；stop() cancel→有界 join→超时 interrupt→句柄保留四段式；stopAll 汇聚等待上界配置化；join null 短路显式化；**MA-03 同变更集绑定**（plan 2472 stopIncrementalChains 收敛点零回退）
  - [ ] **裁定⑤ C7-003 状态行谓词+单飞/fencing**：版本谓词形态（期望相位谓词[预设] vs 乐观版本列——DDL 面一次定夺）；编排入口单飞（per-jobId 锁 + AtomicReference putIfAbsent）；跨进程双跑 fencing（第二写 updated==0 显式失败语义）；`incrementalManager()` double-check；**plan 2514 裁定⑥形态菜单引用不重建声明**；C1-005/006/009 吸收面；C1-008 文件级接缝登记（R4-20 不代做）；MA-10 同 diff 协调条款
  - [ ] **裁定⑥ C7-001 死链 fail 门禁**：编排器层消费 getFailure 形态（VERIFY 前置检查+后台守卫[预设] vs probe 层活性句柄[触发 RP-2 改写须申报]）一次定夺；**硬前置声明（裁定⑨先行）**；死链即 fail 门禁语义成文；C3-002/CL-006/018/023 吸收面；RP-2 双钉零改动承诺
  - [ ] **裁定⑦ C3-001 启动补偿与键族**：循环 catch 回收已启动链（复用裁定④原语）+ 相位后翻；jobName 键派生纳入 querySpace（qs 身份键单点函数——与裁定⑨⑩同族 SSOT）；runningJobs putIfAbsent fail-fast；C6-001 吸收面；X-1 不并案声明
  - [ ] **裁定⑧ C1-003 历史表互斥**：跨进程互斥形态（DB 咨询锁/租约行/条件 INSERT[预设 one-of] vs 方言化 upsert）一次定夺；**RP-1 硬约束：保留 upsert 覆盖语义（禁「冲突即拒绝」方向）**；DDL 守卫串行化；CL-022 吸收面；WI 1.5/1.6 租约族对齐选型引用声明
  - [ ] **裁定⑨ C3-008 键派生单点化**：connector 名派生函数单点（base/base-i 枚举清单）+ runVerify 逐链探测聚合判据（M>1 全链指纹）一次定夺；**qs 身份键 SSOT 声明**（R4-20 C7-008/C3-010/C3-011 消费不重建）；MA-06 顺序前置条款成文
  - [ ] **裁定⑩ C3-009 relocate querySpace**：PhysicalTable qs 派生形态（mapping 携带[预设] vs router 构造注入）一次定夺；FULL 侧跨源行合并写复核新证面核对边界登记
  - [ ] **裁定⑪ C7-002 受控自环**：失败相位白名单自环（预设，仅 fix-forward API + 重试计数上界）vs RECOVERY 相位边一次定夺；**既有钉兼容硬约束**（testIllegalTransitionsFail/testAbortReachableFromEveryActivePhase 保持绿）；CL-007/008 吸收面；fix-forward×裁定⑤谓词协同（防自环写覆盖）
  - [ ] **裁定⑫ 测试缝与零回退钉清单**：红①–红⑪ 测试形态逐条（含缺陷态红判据 + docker-gated 规避[mock listener 层]）；零回退钉 = `TestLettuceNosqlService` PubSub 七钉（:558/:586/:614/:705/:739/:768/:803——唯一允许改写 = MA-02 反向钉死 await 化[登记语义对照]）、`TestMigrationSwitchStopsChains` 双钉（:76/:104）、`TestIncrementalLagProbe` 双钉（RP-2，:42/:90）、`TestMigrationHistoryManager#testRecordMigrationReplacesExistingRecord`（RP-1，:253）与 `#testRecordMigrationKeepsExistingRowWhenWriteFails`（:299）、`TestMigrationPhase` 矩阵钉（:25/:34/:40/:50/:66/:76）、`TestWriteGateWindowRecheck`/`TestShardSelectorHotSwapVisibility`（2507 钉零触碰）、`TestCdcOffsetSeedBuilder`/`TestSourcePositionPinner`/`TestConsistencyCanonical`/`TestMultiSinkTopologyProbe`（邻接钉）；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [ ] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑫全部项且每条有唯一预设默认分支，独立 draft review 记录在案（文件命名如调整已登记裁定）
- [ ] 裁定②含执行器落点双形态与 nop-api-core 申报条款；裁定④含 MA-03 同变更集绑定；裁定⑤含 2514 形态菜单引用与 C1-008 接缝；裁定⑥含顺序硬前置与 RP-2 约束；裁定⑧含 RP-1 约束；裁定⑨含 qs 身份键 SSOT；裁定⑪含既有钉兼容硬约束；裁定①②③含 PubSub 三单统一设计与竞次面约束
- [ ] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 11 成员实现 + 回归测试（按子域分组）

Status: planned
Targets: `nop-persistence/nop-{nosql/nosql-lettuce,orm-migration,db-migration}/src/`（各自 main + test 树）

- Item Types: `Fix`（11 项 P1 均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [ ] 组 A PubSub 生命周期（nop-nosql-lettuce）：C6-004（裁定①，含 C1-011/C3-004 吸收面 + R19 前案消案注记）+ C8-001（裁定②，含 C3-005/C7-010 吸收面 + MA-02 反向钉死改写履行）+ C7-014（裁定③）+ C10-022/C10-023/C10-025
- [ ] 组 B 链停止与编排 fencing（nop-orm-migration）：**C3-008（裁定⑨，全组最先）**→ C7-001（裁定⑥，硬前置⑨，RP-2 约束）+ C6-003（裁定④，与 MA-03 同变更集）+ C7-003（裁定⑤，引用 2514 形态菜单）+ C7-002（裁定⑪，与⑤谓词协同）+ C10-003/004/005/010/011/015
- [ ] 组 C 启动补偿与 qs 键族（nop-orm-migration）：C3-001（裁定⑦，与裁定⑤同 diff 协调[X-1]，C6-001 吸收面）+ C3-009（裁定⑩，与⑨键族统一）+ C10-002/C10-006
- [ ] 组 D 迁移历史表（nop-db-migration）：C1-003（裁定⑧，RP-1 约束，CL-022 吸收面）+ C10-021
- [ ] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红⑪ 按组 A–D 落位（Goals 节逐条判据）；红绿快照落 `_tmp/` 并登记；**变异探针纪律**：触碰恢复路径/停止协议的改动（裁定④）收口前必须运行含完整停止-恢复流程的端到端测试（恢复动作以断言方式确认目标可执行行真实存在）
- [ ] **端到端验证**（rule 22）：≥1 条编排全链场景（runIncremental[键派生清单登记]→链 failure 注入→死链 fail 门禁拒绝→fix-forward 受控自环重入→confirmSwitch 收敛停链[有界 join 断言]→completeSwitch 版本谓词拒绝越权写，覆盖裁定④⑤⑥⑨⑪）；≥1 条 PubSub 生命周期场景（subscribe→慢消费者并发→外移投递不断链→断线重连重订阅→cancel 属主校验→doStop 对账，覆盖裁定①②③）；≥1 条守卫场景（并发迁移启动 DDL 幂等 + recordMigration 无脏行覆盖 + startFallbackChains 中途失败回收，覆盖裁定⑦⑧）
- [ ] **接线验证**（rule 23）：裁定②外移 executor 真实承载投递断言（投递线程名/计数证据，非仅 executor 存在）；裁定⑤版本谓词真实拦截越权 UPDATE 断言（updated==0 路径）；裁定⑥getFailure 真实被门禁消费断言（零调用方现状反转）；裁定⑨派生函数真实被 runVerify 与 startFallbackChains 双入口消费断言（非仅函数存在）
- [ ] **无静默跳过**（rule 24）：裁定④join null 短路显式化（禁静默）；裁定⑤第二写失败显式抛错（禁吞）；裁定⑥死链 gateFailed 类型化拒绝；裁定⑦同名重入 putIfAbsent 显式拒绝；裁定②执行器拒绝策略显式（禁静默丢弃）；新增公共分支无空方法体/TODO
- [ ] **提交纪律**：设计文档先行单独提交；按组提交（A/B/C/D 四组，裁定④与 plan 2472 收敛点同变更集、裁定⑨先于⑥、裁定⑤⑦同变更集[X-1]）；C10 测试与对应本体同一 commit；组间无耦合时可独立提交序列
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 11 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（12 条成员面）同 diff 落地；MA-02 反向钉死改写履行留痕（或裁定申报不触改写面并登记）
- [ ] 必红证据（E2）：红①–红⑪均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；唯一允许的钉改写 = MA-02 await 化语义适配[旧断言语义对照留痕]，登记在案）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-persistence -am` BUILD SUCCESS；上游环境抖动按 Baseline 勘误 8 三联裁定处理并留痕；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-persistence 聚合全量测试；`docs-for-ai/02-core-guides/model-first-development.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2517 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-persistence -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`（PA 零触碰声明兑现，或条件申报②下已申报面逐项登记）、不含 nop-api-core/nop-commons/nop-core 平台设施（条件申报①除外）、不含 R4-20/R4-32/G49 批外成员本体、2507 双钉（TestWriteGateWindowRecheck/TestShardSelectorHotSwapVisibility）与 plan 2472 双钉零 diff 核对登记
- [ ] Owner doc 更新：`model-first-development.md`——新增「ORM 迁移编排并发契约」节（阶段迁移 fencing 语义/停止协议可验证性/死链 fail 门禁/fix-forward 受控自环契约/qs 身份键派生契约[SSOT 指向]、迁移历史表守卫契约句、「以设计文档为准」归属声明）+ PubSub 消息投递线程纪律契约节（Redis PubSub fire-and-forget 能力边界邻接、消费外移与 ack 契约、重连重订阅对账）+ §源码锚点补行；`source-anchors.md` 新增锚点（按实际）；check-doc-links --strict 退出码 0
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence --severity high` 退出码 0（批前既有发现逐条裁定登记[2517 先例：nop-orm-tdengine/nop-orm-geo 三条 guard 式占位已裁定，本批零触碰]）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] model-first-development.md 迁移 owner 面 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（生成物/orm 模型/平台设施/R4-20/R4-32/G49 批外成员本体/2507 与 2472 钉面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.24 行、cra-index P1 台账、R4-20/R4-32 就绪注记、R19 前案消案注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（监视器串行/消费外移/重订阅对账/停止协议四段式/版本谓词/门禁消费/键派生单点/历史表互斥/受控自环运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-15 十一行状态收敛（open→fixed，注记 plan 2518；吸收成员[C1-011/C3-004/C3-005/C7-010/C1-004/C1-005/C1-006/C1-009/C3-002/C6-001/CL-006/018/022/023/CL-007/008]随各主行括注）；C10 成员面收敛注记（12 条按勘误 7 口径 + RP-1/RP-2 履行注记 + MA-02 反向钉死改写注记）；roadmap 8.24 行 Status → done；R4-20（8.60）/R4-32 软依赖就绪注记（**含 C3-008 键派生 SSOT 指向[消费不重建] + C1-008 接缝转达 + C7-008 健康子断言被推翻对齐义务**）；R19 前案（check/nosql-cdc 2026-08-19 [P2]）消案注记落 C6-004 行；B1-02 终裁 P1 落账确认
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 十一行收敛 + 吸收成员括注 + C10/RP/反向钉死注记 + R19 消案注记、roadmap 8.24 = done、R4-20/R4-32 就绪注记完成、B1-02 落账确认
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 11 项成员全部修复：C6-004（订阅管理监视器化+属主校验/停机对账）、C8-001（IO 线程消费外移+ack 契约恢复）、C7-014（重连重订阅对账与检测）、C6-003（停止协议发布序+有界 join+interrupt）、C7-003（状态行版本谓词+编排单飞/fencing）、C7-001（死链 fail 门禁，⑨前置兑现）、C3-001（部分启动补偿+jobName qs 键）、C1-003（历史表跨进程互斥+upsert 保留）、C3-008（connector 键派生单点化）、C3-009（relocate querySpace 派生）、C7-002（状态机受控自环）
- [ ] batch-proposal R2-15 行 exit criteria 增补逐条达成并留证：B1-02 终裁 P1 落账；PubSub 属主校验/停机对账/重订阅检测；IO 线程内联消费外移+ack 契约恢复；链状态行版本谓词+单飞/fencing；迁移历史表原子守卫；relocate querySpace 修复；写门持久化半边归 R2-04 注记确认（2507 已收口面零触碰）
- [ ] 对齐纪律守约：C3-008 先于 MA-06 修复顺序兑现；MA-04×MA-03 同变更集；MA-05×MA-10 同 diff 协调（X-1 不并案）；PubSub 三单统一设计+竞次面约束；RP-1/RP-2 修复方向约束兑现；R19 前案消案落账；PA 条款闭环（零 orm 触碰声明兑现或条件申报面落地）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑪均有先红后绿或变异/回退论证（E2）；C10 收口门（12 条成员面）同 diff 落地；MA-02 反向钉死改写义务履行留痕
- [ ] 设计文档裁定①–⑫成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-persistence -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：model-first-development.md（迁移 owner 面）+ source-anchors.md 按实际（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）监视器串行/消费外移/重订阅/停止协议/谓词/门禁/键派生/互斥/自环运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-persistence 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index R2-15 十一行收敛 + roadmap 8.24 → done + R4-20/R4-32 就绪注记 + R19 消案注记（E6）

## Deferred But Adjudicated

### R4-20/R4-32 成员（G47 13 项 + C7-013，含 C7-009 PubSub 失效面②与 C10 载体面）

- Classification: `out-of-scope improvement`（batch-proposal L135/§二 R4-32 显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-20（roadmap 8.60，软依赖 8.24）/R4-32
- Why Not Blocking Closure: 判级对齐零重复——cra-index G47 行批次列=R4-20；本批已收敛 2.5 单元全部 11 项 open P1（修复优先级全集），下游实施以本批后的 live 形态为基线，且可引用本批裁定③⑨接缝（PubSub 连接生命周期对账/qs 身份键 SSOT）为既有裁定不重开体。

### G49 deferred 束（C7-005/C7-015/C8-005/C8-007）与 watch-only C8-006

- Classification: `watch-only residual`（G49 行 deferred 裁定在案：零生产实例化/零调用方）；Successor Required: `yes`（触发条款=调用方接线即复核）；Successor Path: 触发条款所在批
- Why Not Blocking Closure: 批前裁定零调用方降档；不构成本批 11 成员的 in-scope 缺陷面。

## Non-Blocking Follow-ups

- **R4-20/R4-32 对接注记**：本批落地后向 roadmap 8.60 行作就绪注记（软依赖解除落点；含裁定③⑨接缝引用指向、C1-008 文件级接缝与 C7-008 健康子断言对齐义务转达）。
- **PubSub 长线治理**：Redis PubSub fire-and-forget 能力边界（无持久化/无消费组）在 owner doc 的使用侧警示强化（如需可靠投递应选 nop-message 持久化通道）——使用侧知识项，非本批缺陷面。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit）
- Evidence: （待填）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）

## Risks And Rollback

- **裁定②消费外移的时序语义风险**：内联改异步使既有「发送→立即断言收到」测试语义变化（MA-02 反向钉死面）且端到端投递次序不再保证——设计文档须给出有界化/拒绝策略与 await 型断言改写清单；回滚 = revert 裁定②变更集（executor 外移单点可逆）。
- **裁定③重订阅与裁定①竞次面风险**：重订阅若绕过监视器将复活已 cancel 订阅（C7 round3 预警回归面）——竞次面约束在设计文档成文为硬边界，红③含「已 cancel 不复活」断言；closure audit 抽查锁域边界。
- **裁定⑤ fencing 的多节点残余风险**：进程内单飞不覆盖跨节点双跑的 DDL/链启动窗口——跨进程面以状态行条件 UPDATE fencing 拒绝第二写，残余窗（两节点同时通过谓词）显式登记为 residual（与 C1-003 互斥面互补）；回滚 = 谓词退化为仅告警需 plan 修订不得静默。
- **裁定⑧跨进程互斥的方言风险**：DB 咨询锁/条件 INSERT 形态在 mysql/postgresql/H2 三方言矩阵行为不一——设计文档须给方言矩阵核实表；回滚 = 互斥原语 revert 后 DDL 幂等性仍由 CREATE TABLE IF NOT EXISTS 承载（现状不劣化）。
- **裁定④停止协议的中断风险**：interrupt 链线程可能使Debezium engine 处于半终止态——有界 join+interrupt 后句柄保留、失败面经 getFailure 可观测（裁定⑥消费）；变异探针纪律适用（注释/删除目标行→跑钉子→恢复→再跑，恢复以断言方式确认）。
