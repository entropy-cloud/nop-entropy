# 2514 CRA Phase 8.20 — R2-11 1.5 task/wf 批（6 项 P1：五站点快照竞态 / SUSPENDED 游离写 / sync+executor 嵌套死锁 / 扫描无界与毒步骤 / 闸门注册表无界 / WfTaskScanner fencing）

> Design Review: R2 agent_5d14a125 @ 2026-10-30（第 1 轮 FAIL[4 Major/4 Minor]：Major-1 条带锁载体依赖方向反向→IWorkflowStore.withWfLock 接线；Major-2 ORM PA 申报与 plan 冲突+迁移未成文→plan PA 行批内申报修订[协调方]+§3.6 迁移三行；Major-3 逐出×获取竞态→同 key computeIfAbsent 内联+引用一致 remove；Major-4 runXpl 持锁 ABBA→action 锁段收窄 runXpl 不持锁+迁移段重取锁终检[probe (b) 终检成立]；Minor×4 闭合——第 2 轮 PASS[余 3 Minor[N1 外置表 DDL 方向写反必改/N2 plan 零触碰行同步/N3 措辞降格]协调方修入]）
> Plan Status: active
> Draft Review: R1 agent_f5593d05 @ 2026-10-30（PASS，仅 4 Minor——13 站点基线亲读全吻合、fencing 三方协调[谁建谁引用]/MA-08 owner 表态出口/C10-009 反向钉先改写均核验成立；Minor×4[勘误 6 补登 3 处行号/裁定①站点④默认分支/owner 表态机制成文/前向引用 warning 知悉]由协调方修入）
> Last Reviewed: 2026-10-30
> Source: roadmap 工作项 8.20（R2-11，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L162，Status=todo[本 plan 立项时回写为「todo（plan 2514 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-11 行（L85 全文：finding ID 清单 6 项[1.5-C1-001；1.5-C3-001；1.5-C3-002；1.5-C5-001；1.5-C5-003；1.5-C7-001] / 模块与验证命令 nop-task `./mvnw test -pl nop-task -am`；nop-wf `./mvnw test -pl nop-wf -am` / Owner Doc=`docs-for-ai/03-modules/nop-task.md`；`docs-for-ai/03-modules/nop-wf.md` / PA=无 / 依赖「无前置（可并行）；MA-23 fencing 对齐 R1-01（C8-001 平台方案）+R2-13（1.7-C7-001）——避免两套 fencing；MA-05 R02；MA-08 R01」/ exit criteria 增补：**五站点快照竞态修复（组迁移/终结滞留/join 双实例/action 双跑/子流回滚）；sync+executor 嵌套死锁解除；闸门注册表上限（R01，plan 364 之上 owner 表态出口）；WfTaskScanner fencing（holderId+单飞+remindCount 原子）**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.5 审计目录 `ai-dev/audits/2026-10/2026-10-15-0930-deep-audit-nop-wf-nop-task-concurrency/`（C1-shared-mutable-state.md C1-001 :26-75 + 复核 :333/:352/:359；C3-async-chains.md C3-001 :55-88 + 复核 :371、C3-002 :83-111 + 复核 :110-111；C5-memory-bounds.md C5-001 :66-91 + 复核 :92、C5-003 :130-160 + 复核 :161；C7-long-running.md C7-001 :60-95 + 复核 :469/:485/:508；merge-adjudication.md §2.1 MA-01/MA-02/MA-03/MA-05/MA-08/MA-23 六族行 :50-58/:72 + §3-C 判级统一 :137；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` §二 WI 1.5 节 :122-150 + 小结行 :43[**1.5（14）** 全映射]）；执行基线 @ `83052aa1ff`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.19 已 done，plan 2495–2513 均已完成或收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-30 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2496（R1-01 周期任务异常绝缘层批，completed——**MA-23 fencing 对齐的「C8-001 平台方案」出处**：`ai-dev/design/concurrency/periodic-task-insulation.md` 为周期任务链绝缘/重臂/观测语义的唯一权威；WfTaskScanner 扫描链 riding 该设施语义，本批 fencing 修复不得回归链存活/失败可见契约）；plan 2502（R1-07，completed——R01 族级 SSOT `ai-dev/design/concurrency/ledger-retention-policy.md`，本批 MA-08 为 R01 族成员，对齐条款见 Baseline 对齐纪律节）；plan 2498（R1-03，completed——**1.5-C4-001/MA-04 已修**：retry/delay 隔离 executor 已 landed，live `TaskStepEnhancer.java:115-118` 注释在案，本批零触碰）；plan 364（nop-task 引擎契约前案，completed——[05-02] 条带锁/[05-03] SUSPENDED cancel 驱动/[05-04] 强引用注册表裁定均在其上，MA-01/MA-08 为 plan 364 声明边界之上的残留面）；**下游**：R2-13（8.22，1.7 batch 批）——fencing 对齐依赖本批（batch-proposal 附录 L313「R2-13 依赖 R2-11 + R1-01」；R2-13 行 Deps 列「C7-001 fencing 对齐 R1-01+R2-11」），**本批为 R12 族平台 fencing 语义的建立批、R2-13 为引用批（谁建谁引用，见对齐纪律节）**；R4-08（8.48，1.5 闸门/throttle 束 G14 12 项）软依赖本批；R4-09（8.49，1.5 图执行/扫描 infra 束 G15 13 项）软依赖本批/R4-08——批末须回写就绪注记（含 fencing 平台语义引用指向、MA-08 观测面/MA-09 联动边界、MA-05 毒步骤接缝、C10 处置边界）；**并行协同**：无同门禁在飞批，本批可独立开跑
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-11 行授权列）。本批六成员站点分别位于 `nop-task/nop-task-core`（MA-01/MA-03/MA-08）、`nop-wf/nop-wf-core`（MA-02）、`nop-wf/nop-wf-dao`（MA-02/MA-05）、`nop-wf/nop-wf-scheduler`（MA-05/MA-23）——**均非 Protected Area**（AGENTS.md Protected Areas 表所列 `nop-core/`/`nop-xlang/`/`nop-api-core`/`nop-auth`/orm 模型结构/生成管线均不涉及；nop-task/nop-wf 为业务模块）。live 回读勘误申报（含 draft review R2 Major-2 修订）：本批不做 `_` 前缀生成物手改；**MA-23 裁定⑥经 Phase 1 评审触发 ORM 结构 plan-first 批内申报——`nop-wf/model/nop-wf.orm.xml` 为 NopWfStepInstance 新增 scan_holder_id/scan_lease_time 两列 + 迁移脚本（非 `_` 前缀新建脚本落 `nop-wf/deploy/sql/postgresql/`），申报载体=本 plan 本条+设计文档 §3.6+独立评审闭环，沿 2510 先例「新增列须先裁定 DDL migration 方案并在 plan 登记后方可采」口径；其余站点不触 `model/*.orm.xml`**；**MA-23 fencing 若 Phase 1 裁定需上收平台层（nop-kernel/nop-api-core 新原语）即触发框架核心 plan-first，须在该裁定项内批内申报后方可实施**——预设默认分支 = fencing 落 nop-wf 模块内（条件 UPDATE 抢占 + 模块内注册表），零平台层触碰（见对齐纪律节 MA-23 条款）；旁证站点 `nop-ai/nop-ai-agent/.../TeamTaskFlowOrchestrator.java:655`（MA-01 跨线程驱动源，只读引用不改码）。本批不触碰 R2-13/R4-08/R4-09 的批外成员本体。

## Purpose

关闭 R2-11 批的 6 项已确认 P1（均 open，cra-index 台账行 L161–L167 中批次列=R2-11 的六行）。核心结果面有四：①**wf 跨请求快照竞态五站点一次闭合**（MA-02，主条目 1.5-C3-001 吸收 C3-011/C3-012/C1-002/C3-015）——`getWorkflow` 每请求新建 `WorkflowImpl`，步骤快照随实例固化，组完成判定（读快照）→组迁移（写库）间无跨请求互斥，and/seq 组双提交各自「成功」而组迁移永久丢失（流程滞留中间态无补救出口）；修复须同盖五站点：组迁移、流程终结滞留（checkEnd）、join 双实例（and+or 两型）、用户 action 双跑、子流结束回滚重放。②**task 引擎两处正确性 P1**：MA-01——`TaskImpl.java:159` SUSPENDED 转移游离于 taskState 监视器之外，与 `cancel()` 的「SUSPENDED 判定→KILLED 驱动」构成非原子 check-then-act（kill/超时取消静默丢失 + C1-009 复活面：KILLED 被 SUSPENDED 裸覆写）；MA-03——`sync=true` 外层包装 × `executor` 内层包装同池嵌套时调用线程阻塞等待池内任务，池饱和即全体永久互等（syncGet 无超时、不响应取消唤醒）。③**资源包络两处 P1**：MA-05——`findDueActivatedSteps/findRemindActivatedSteps` 无 LIMIT 全量载入单 ORM 会话 + 失败毒步骤 dueTime 永不清致扫描集不收敛；MA-08——全局闸门强引用注册表（plan 364 [05-04] 裁定不驱逐）× 动态 keyExpr 键空间持久无界（plan 364 之上残留面，含 owner 表态出口）。④**WfTaskScanner fencing**（MA-23，吸收 C1-005）——多节点部署/单节点重叠扫描无任何 fencing：due action source 双跑副作用先于状态守卫（近确定性双跑）+ remindCount 读-改-写丢失更新；修复 = 条件 UPDATE 抢占（holderId+单飞）+ remindCount DB 原子自增，且本批裁定为 R12 族平台 fencing 语义的建立载体（R2-13 引用不重建）。批末 roadmap 8.20 → done，cra-index 六行收敛，R2-13/R4-08/R4-09 依赖/软依赖就绪。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-11 = 6 项成员（P1×6）；cra-index 台账六行独立（L161 C1-001[MA-01，成员 C2-001/C7-016]、L162 C3-001[MA-02，五站点族]、L163 C3-002[MA-03，成员 C4-002/C2-010]、L165 C5-001[MA-05，成员 C8-001]、L166 C5-003[MA-08，成员 C8-004]、L167 C7-001[MA-23，成员 C1-005]，均 open、批次列均=R2-11）；roadmap 8.20 行 Status=todo。前置：无前置（可并行），plan 2495–2513 前序批均收口——基线门禁已满足。**同单元 P1 的 MA-04（1.5-C4-001）已随 R1-03（plan 2498）fixed**（cra-index L164，live `TaskStepEnhancer.java:115-118` RetryTaskStepWrapper 已带 executor 隔离），1.5 单元全部 7 个 P1 族中 6 个入本批、1 个已修——批次覆盖口径完整。
- **吸收成员（随主条目同 diff，不单列工单）**：MA-01←C2-001（同点锁正确性视角）/C7-016（同点长时运行视角）/C1-009（相反交错复活面，族内双向交错必须单一原子修复）；MA-02←C3-011（末段并发完成实例滞留）/C3-012（join 双实例 and+or）/C1-002（同 step action 副作用先于保护双跑）/C3-015（兄弟子流结束乐观锁回滚输家整事务）；MA-03←C4-002（池治理 grow-then-queue 永不拒绝视角）/C2-010（通用无超时 sync 等待面）；MA-05←C8-001（背压口径同点）；MA-08←C8-004（声明边界残留面视角，WARN-once 不持续）；MA-23←C1-005（remindCount RMW+扫描入口无重叠互斥视角）——六族吸收明细以各单元 merge-adjudication §2.1 族行为准；cra-index 回填时以主 ID 收敛、吸收成员括注。**C4-002 为 P1 成员但吸收面=池治理视角**（MA-03 修复方向②syncGet 时限+取消接入同时闭合其挂死面；TaskQueue grow-then-queue 池参数本体治理面归 MA-03 裁定②一并成文，不另开 R4 工单——merge-adjudication MA-03 行成员列表在案）。
- **nop-task/nop-wf 域先序批状态（live 形态登记，2026-10-30 亲读）**：plan 2498（R1-03，MA-04 隔离 executor）已 landed（`TaskStepEnhancer.java:115-118` 注释「1.5-C4-001/C4-008，plan 2498 Phase 2」在案）；plan 364（引擎契约前案）四终态 driver 监视器收敛（`TaskImpl.java:186-192/202-208/237-243/252-258`，live 亲读一致）与 [05-03] SUSPENDED cancel 驱动（`TaskRuntimeImpl.java:90-105`）、[05-04] 强引用注册表（`TaskFlowManagerImpl.java:59-66` 注释在案）均为既有裁定，本批为其声明边界之上的残留面修复；plan 349（SUSPEND 契约）owner doc `nop-task.md` §挂起与恢复（:59-71）在案。
- **C10 验收前置入批集（8 条本体收口门，随本体同 diff 落地[E2]；映射 SSOT = `inventory-p2-c10.md` §二 WI 1.5 节 + 小结行「1.5（14）」）**：C10-001（MA-01 收口门——**假保护串行化测试**：既有测试串行执行掩盖交错窗，须真并发交错注入）、C10-003（MA-02 组迁移面——顺序假保护改真并发）、C10-004（MA-02 C3-011 末段并发完成面）、C10-005（MA-02 C3-012 join 双实例面）、C10-006（MA-03 收口门——pool-size=1 小池死锁回归，方案 b）、C10-008（MA-05 收口门——查询形状[limit/orderBy]+毒步骤收敛性断言）、C10-009（MA-08 收口门——**反向钉死**：`TestPhase4TerminalAndConcurrency.globalGates_strongRefRegistry_sameInstanceAcrossManyKeys`（live :175-194）钉定「永不驱逐」契约，修复前必须先改写——保留同 key 同实例断言[permit 池不分裂语义] + 新增上界/淘汰断言）、C10-011（MA-23 收口门——双 scanner 竞速：触发次数恒==1、remindCount 终值恒==轮数）。**不在本批的 C10 处置**：C10-002（宿主 MA-11，R4-08）、C10-007（宿主 MA-04，已随 plan 2498 闭合）、C10-010（宿主 C4-003 已知未修复，R4-09 按同案对齐一处修复）、C10-012（宿主 C4-007，R4-09）、C10-013（宿主 MA-13，R4-08）、C10-014（宿主 C7-014，R4-09——与 MA-01 的 C1-009 分立裁定，merge-adjudication §3-B 在案，本批零触碰）。

### 对齐纪律显式条款

- **MA-23 × R1-01 + R2-13 fencing 对齐（单一平台方案，谁建谁引用一次写清）**：R12 族纪律（family-clustering :56 + inventory-ledgers §二#11 + 附录 L313）=「平台级统一 fencing 语义，避免两套 fencing」。三方角色裁定：**R1-01（plan 2496）供「平台方案」= 周期任务绝缘层**（`ai-dev/design/concurrency/periodic-task-insulation.md`：catch Throwable+重臂+观测+拒绝终态）——WfTaskScanner 扫描链的链存活/失败可见面消费该语义，fencing 修复不得回归（扫描体改造后仍不得因单条失败杀链、失败仍须经绝缘管线可见）；**R2-11（本批）为 fencing 语义的建立批**——Phase 1 设计文档定稿平台 fencing 语义（holderId 互斥 + 条件 UPDATE 抢占/租约 + 单飞 + remindCount 原子自增 + 与引擎 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 现役兜底的层级关系），**R2-13（1.7-C7-001，RUNNING 死区）到达时引用本设计文档对齐，不重建第二套 fencing**（R2-13 行 Deps「C7-001 fencing 对齐 R1-01+R2-11」+ 附录 L313「R2-13 依赖 R2-11 + R1-01」为该边界文字源）。若 Phase 1 裁定将 fencing 原语上收 nop-kernel/nop-api-core 平台层（如通用扫描抢占器），即触发框架核心/nop-api-core plan-first，须在该裁定项内申报设计文档+回归面后方可实施；预设默认分支 = fencing 全部落 nop-wf 模块内（nop-wf-dao 条件 UPDATE + nop-wf-scheduler holderId/单飞），零平台层触碰、零 PA 申报。
- **MA-08 × R01 对齐（plan 364 之上 owner 表态出口：登记该表述并按 live owner doc 现状裁定落点）**：MA-08 为 R01 族成员（family-clustering R01 行 P1 列「1.5-MA-08(←C8-004)」），族级纪律 =「上限+逐出+fail-fast 包络声明（4.5 口径）」；R01 族级 SSOT（`ai-dev/design/concurrency/ledger-retention-policy.md`）§3.6 家族传导清单列 R2-07/R2-08/R2-12/R3-05/R4-03/R4-07/R4-10/R4-29，**未列 R2-11**——本批不消费其 DB 表保留参数集（MA-08 本体为进程内强引用注册表，非 DB 账本表，形态菜单 A–D 之外），对齐投影 = 族级纪律三要素落进程内形态：上限（键准入或容量治理）+ 逐出（空闲淘汰，须不破坏 permit 池完整性[plan 364 [05-04] 驱逐即分裂 permit 池裁定为约束前提]）+ fail-fast 包络声明（owner doc 成文）。**owner 表态出口**（merge-adjudication MA-08 行 + cra-index L166「plan 364 之上；owner 表态出口」原文登记）：「若裁定『动态 global key 为不支持用法』并落地构建期拒绝，本族降为文档声明缺口、可降级」。live owner doc 现状裁定（2026-10-30 亲读 `docs-for-ai/03-modules/nop-task.md` 289 行全文：无 dynamic global keyExpr 支持声明、无键空间包络声明、无 throttle/rate-limit global 面契约节——§模型与加载/§步骤怎么写/§源码锚点均无相关行）→ **预设默认分支 = owner 未表态「不支持」、动态 key 系 global throttle 特性主流用法（audit C5-003 字段 11），落键空间治理（准入约束或 lastAccess+空闲逐出）；Phase 1 设计文档须显式呈现两分支（治理 vs 构建期拒绝+文档声明缺口降级）并以 owner 表态一次定夺；若裁定引入保留/淘汰参数键，须回 R01 SSOT 修订登记（附理由），不得模块内发明**。联动：MA-09 观测面（registry.size gauge 升周期指标）随 MA-08 键空间治理一并落地其本批内半面（merge-adjudication MA-08 条款④），stats 分页/聚合摘要全量治理归 MA-09 本体（R4-08 注记接缝）。
- **MA-05 × R02 对齐（三范式选型）**：R02 族纪律（family-clustering :46 + inventory-ledgers §二#17）=「三范式：setLimit+fail-fast / 分批 / 列投影」。C5-001 选型预设默认分支 = **分批范式**（QueryBean 设 limit + orderBy(dueTime) 分页游标推进，nop-job C5-002 先例修法；单轮批次上限/时间预算随裁定成文）——扫描面按批消费、无全量物化；毒步骤不收敛半边（失败计数/退避/dueTime 推进）按 merge-adjudication MA-05 条款④与 MA-06 联合修复：**本批落使扫描集收敛的最小面**（失败条目退出本轮扫描集的机制：失败计数+退避推迟 dueTime 或隔离标记），MA-06 本体四失效面（ERROR 抑制/skip 升 WARN/due-backlog gauge/模型加载期校验，P2×4）归 R4-08 不代做——接缝在本批设计文档一次写清（失败计数/下次重试时间字段与语义由本批定，MA-06 消费同一字段）。
- **MA-02 × plan 364 条带锁先例 + MA-17 契约节**：修复方向预设默认分支 = wfInstanceId 级进程内条带锁（对齐 `DaoTaskStateStore.rowLock` 既有范式，plan 364 [05-02] 先例引用不重开）串行化同一 wf 实例的判定-迁移段 + 锁内重读兄弟步骤最新已提交状态；跨进程残留面（多实例部署）在裁定文档显式声明边界（DB 级 `SELECT ... FOR UPDATE` 为备选分支，Phase 1 定夺）；MA-17（C1-003，P2）「WorkflowImpl 实例线程封闭」契约声明是 MA-02 设计文档必备组成（merge-adjudication MA-17 条款①），随 MA-02 裁定一并成文落 owner doc，MA-17 台账行本体归 R4-08/R4-09 不代做。
- **MA-03 × plan 364 [02-06] 硬失败先例**：构建期校验落 `TaskStepEnhancer` 既有校验链（live `:124-131` timeout 硬失败、`:147-152` rateLimit 硬失败在案），`sync && executor` 组合的 fail-fast 形态与其同码同位（ERR_TASK_STEP_CONFIG_INVALID 族）；`syncGet` 带时限并接入 cancel token 同时闭合 C2-010 通用挂死面（merge-adjudication MA-03 条款②）；「timeout 包装位于 sync 内侧」的模型级缓解不文档化不改变判定（条款④）。
- **MA-01 × plan 364 [05-03] 零回退**：`TaskRuntimeImpl.cancel` 对 SUSPENDED 任务驱动 KILLED 的既有语义（live :90-105 注释钉 + `TestPhase4TerminalAndConcurrency` :165-171 钉）零回退；修复 = SUSPENDED 转移纳入同一 `synchronized(taskState)` 监视器（与四终态 driver `:186-192/202-208/237-243/252-258` 同监视器），不引入新同步结构（若裁定引入须 C2 锁序复核——merge-adjudication MA-01 条款⑤）；连带裁定 round3 §2.4-1「挂起任务被 timeout-cancel 记 KILLED 而非 TIMEOUT」分类问题（条款④，Phase 1 成文）；C1-009 复活面（resume 非终态确认后显式重置 ACTIVE 落盘）随同 diff（条款②）。

### 6 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `83052aa1ff` 2026-10-30 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.5-C1-001（P1；MA-01 主，←C2-001/C7-016/C1-009[C1-009 为族内复活半边]） | `TaskImpl.java`（nop-task-core `impl`）；旁证 `TaskRuntimeImpl.java`（同包，cancel KILLED 驱动+resume 路径）、`TeamTaskFlowOrchestrator.java:655`（nop-ai-agent，orTimeout 线程跨线程驱动源，只读） | SUSPENDED 游离写 :159-160（`thenCompose` 挂起分支内，**不在任何监视器内**）；四终态 driver（监视器内）：COMPLETED :186-192 / FAILED :202-208 / KILLED :237-243 / TIMEOUT :252-258（`skipTerminalOverwrite` 守卫 + `synchronized(taskState)`）；cancel 判定块 `TaskRuntimeImpl.java:97-105`（`synchronized(state)` 内读 status==SUSPENDED→驱动 KILLED，plan 364 [05-03] 注释 :90-93） | task 级 SUSPENDED 转移是第五个 taskStatus 写者且游离于 taskState 监视器外：执行线程进入挂起分支未写 SUSPENDED 的窗口内另一线程 cancel 读到 ACTIVE→只设 token 不驱动 KILLED 即返回——kill/超时取消静默丢失，任务停留 SUSPENDED 可被 resume 复活（AI 编排层已按取消上报，zombie 工作复现）；反向交错（C1-009）：KILLED 后被未守卫的 SUSPENDED 裸覆写复活 | 裁定（batch exit criteria 之一，MA-01 族四条款一次闭合）：①SUSPENDED 转移（:159-160 写+落盘）纳入 `synchronized(taskState)` + 终态前置检查（isTerminal 已成立则不写 SUSPENDED，堵 C1-009 复活面），与四终态 driver 同监视器；②cancel 监视器块对「刚转入 SUSPENDED 尚在保存中」的状态同样驱动 KILLED（判定-写入原子化，零回退 [05-03] 语义）；③resume 非终态确认后显式重置 ACTIVE 落盘（C1-009 半边）；④「挂起任务被 timeout-cancel 记 KILLED 而非 TIMEOUT」分类裁定 + cancel 放弃路径 debug 留痕（audit 建议采纳）；**不引入新同步结构**（若裁定引入须锁序复核）；C10-001 假保护改真并发收口门随同 diff |
| 2 | 1.5-C3-001（P1；MA-02 五站点族主，←C3-011/C3-012/C1-002/C3-015） | `WorkflowEngineImpl.java`（nop-wf-core `engine`）；旁证 `WorkflowManagerImpl.java`（nop-wf-core `impl`）、`ExecGroupSupport.java`（同 engine 包）、`WorkflowStepImpl`（步骤快照读 :329-340）、`WfRuntime`/`WorkflowImpl`（delayExecute 对象级串行化） | `getWorkflow` 每请求新建 `WorkflowImpl` `WorkflowManagerImpl.java:78-87`；组完成判定 `isExecGroupComplete` 调用点 :1267（判定读同组兄弟步骤快照）；迁移写点 `doTransition` 调用 :1280；action source 执行 `runXpl` :1255（先于守卫）；`checkEnd` 触发 :1316/:1535；`shouldExecGroupComplete`/`isVoteGroupComplete` `ExecGroupSupport.java:20-35/:37-63`；join 实例创建点（C3-012，and+or 两型，orm 无 (wfId,stepName,joinGroup) 唯一约束）；子流结束回滚面（C3-015） | wf 跨请求快照竞态五站点：每请求 WorkflowImpl 步骤快照互不可见、判定（读快照）→迁移（写库）间无跨请求互斥、各事务写不同 step 行乐观锁无冲突——and/seq 组双提交各自「成功」而组迁移永久丢失（滞留中间态，runAutoTransitions 只遍历 ACTIVATED 无补救）、流程终结滞留、join 并发到达双实例、同 step action 副作用先于保护双跑、兄弟子流结束回滚输家丢整个事务 | 裁定五站点同根因单一设计（MA-02 族六条款，Phase 1 一次定夺）：预设默认分支 = wfInstanceId 级进程内条带锁（对齐 `DaoTaskStateStore.rowLock` 范式）串行化判定-迁移段 + 锁内重读兄弟步骤最新已提交状态；五站点逐一处置表：①组完成判定+迁移原子化（vote 快照口径 :37-63 已复核修正零回退）；②流程自动终结 checkEnd 原子化；③join 实例创建原子化（同盖 and+or 两型，唯一约束/占位条件更新两分支 Phase 1 定夺）；④用户 action 路径占位/唯一约束或声明幂等边界；⑤子流结束回滚重放面；⑥覆盖约束——wf 级守卫补齐须覆盖 `saveWfRecord` 全部 6 个调用点（C1-010 覆盖约束）；跨进程残留面显式声明（SELECT FOR UPDATE 备选分支）；MA-17 实例线程封闭契约声明随裁定成文；C10-003/004/005 三收口门随同 diff |
| 3 | 1.5-C3-002（P1；MA-03 主，←C4-002/C2-010） | `TaskStepEnhancer.java`（nop-task-core `builder`）；旁证 `SyncTaskStepWrapper.java:15-18`、`ExecutorTaskStepWrapper.java:30-70`、`task.xdef:115/:121`（keyExpr 面，MA-08 共享） | executor 包装（内层）:118-121；sync 包装（外层）:161-162——**sync 恒在 executor 之外，调用线程阻塞等待池内任务**；`SyncTaskStepWrapper.execute` :15-18 `.sync()`→`FutureHelper.syncGet` 无超时无取消唤醒点；`ExecutorTaskStepWrapper` 取消兜底 :51-63（只 complete ret，不唤醒阻塞等待者的同池父续延场景见 audit 复核字段 7 校正） | `sync=true` × `executor` 同池嵌套：N 个父实例池线程各自 `.sync()` 阻塞等待自己派生的同池子任务，池饱和即全体永久互等（syncGet 无超时、不响应取消，kill 无外部取消源时无法解围），同池所有其他任务被殃及 | 裁定（MA-03 族四条款）：①构建期 `sync×executor` 组合互斥/同池嵌套检测 fail-fast（落 `TaskStepEnhancer` 既有校验链 :124-152 同码同位，ERR_TASK_STEP_CONFIG_INVALID 族，plan 364 [02-06] 先例投影）；②`syncGet` 带时限并接入 cancel token（同时闭合 C2-010 通用挂死面；超时缺省值/是否可配置 Phase 1 成文）；③pool-size=1 小池死锁回归测试（C10-006 方案 b）；④模型级缓解不文档化不改变判定；C4-002 池治理视角随②闭合挂死面，TaskQueue grow-then-queue 本体治理边界随裁定成文（不另开工单） |
| 4 | 1.5-C5-001（P1；MA-05 主，←C8-001） | `DaoWorkflowStore.java`（nop-wf-dao `store`）；旁证 `WfTaskScanner.java`（nop-wf-scheduler，全量驻留消费方）、`wf-due-task-scan.job.yaml:1-9`/`wf-remind-task-scan.job.yaml:1-9`（cron 1min/5min，enabled 缺省 false） | `findDueActivatedSteps` :180-186、`findRemindActivatedSteps` :188-194——QueryBean 无 limit、orderBy，`findAllByQuery` 平台侧无隐式上限；消费 `WfTaskScanner.scanDueTasksInCurrentSession` :78-119（单 ORM 会话 foreach 全量驻留）；失败分支 :108-118（仅 LOG.error，失败步骤 dueTime 永不清、状态停留 ACTIVATED——毒步骤每轮重入扫描集永不收敛）；remind 同构 :122-141 | 到期/提醒扫描无 LIMIT 全量载入单 ORM 会话（峰值驻留 O(due 集合)）+ 失败毒步骤不退出扫描集（每分钟重入、集合单调不收敛）——长时运行内存/时长必然失控 | 裁定（MA-05 族五条款，R02 分批范式选型见对齐纪律）：①查询族 limit+orderBy(dueTime) 分页游标推进（due 与 remind 同构同修）；②单轮批次上限/时间预算（配置键与缺省值 Phase 1 成文）；③扫描会话内存峰值消除（分批后单会话驻留有界）；④毒步骤不收敛半边——失败计数+退避推迟 dueTime（或隔离标记）使扫描集可收敛（**与 MA-06 联合修复接缝：失败计数/下次重试时间字段语义由本批定，MA-06 本体四失效面归 R4-08 不代做**）；⑤C10-008 查询形状+收敛性测试随同 diff；精度备注：remindTime 引擎侧无写入点（remind 半区当前恒空集），P1 由 due 半区独立支撑，remind 侧同构同修不豁免 |
| 5 | 1.5-C5-003（P1；MA-08 主，←C8-004；R01 族成员，owner 表态出口） | `TaskFlowManagerImpl.java`（nop-task-core `impl`）；旁证 `ThrottleTaskStepWrapper.java:55-65`/`RateLimitTaskStepWrapper.java`（key 动态求值）、`task.xdef:115/:121`（keyExpr 属性）、`TaskStepExecution.java`（initInputs 输入直通面） | 强引用注册表 :63-66（plan 364 [05-04] 注释在案「never evicted」）；`getRateLimiter` :178-197（computeIfAbsent + frozen WARN :188-192）、`getSemaphore` :199-207；`warnIfOverCap` WARN-once :209-215；key 求值 `ThrottleTaskStepWrapper` :55-65（keyExpr 缺省回落 stepPath，动态 EL 无基数约束）；输入直通面 `TaskStepExecution`（`keyExpr="${input.*}"` 请求字段直通永久键） | 全局闸门强引用注册表 × 动态 keyExpr：键基数=历史取值域无界（`${userId}`/`${entityId}` 直通），每 entry 常驻只增不删——高基数动态键模型长时运行必然无界增长（数百 MB 级），WARN-once 不可操作（plan 364 [05-04] 声明边界之上的残留面） | 裁定（MA-08 族四条款 + owner 表态出口两分支，Phase 1 一次定夺）：预设默认分支 = **键空间治理**——(a) 准入约束（global=true 时 keyExpr 须解析为声明式低基数键/或禁止动态键回落 stepPath）或 (b) lastAccess+空闲逐出（仅逐出 permits==0 且无在用持有者实例，不破坏 permit 池完整性——plan 364 [05-04] 驱逐裁定为约束前提）；输入直通面（initInputs→`keyExpr="${input.*}"`）必须在处置覆盖内；WARN-once 升周期性指标（registry.size gauge，MA-09 本批内半面；stats 分页/聚合归 MA-09 本体 R4-08 注记）；**owner 表态出口**：若裁定「动态 global key 为不支持用法」→ 构建期拒绝 + owner doc 声明缺口（本族降文档缺口）；两分支均须 fail-fast 包络声明成文（R01 族级纪律投影，见对齐纪律）；C10-009 反向钉死先改写（保留同 key 同实例断言+新增上界断言）随同 diff |
| 6 | 1.5-C7-001（P1；MA-23 主，←C1-005；R12 fencing 族） | `WfTaskScanner.java`（nop-wf-scheduler）；旁证 `WorkflowEngineImpl.java`（runXpl :1255 先于 doTransition :1280——副作用先于守卫）、`app-scheduler.beans.xml`（LocalJobScheduler 节点本地装配）、job yaml 两份（无节点过滤/分布式锁入口；nop-wf 全部 pom 无 nop-cluster 依赖） | due 扫描 `scanDueTasksInCurrentSession` :78-119（读到 ACTIVATED→`invokeActionAsync` :96→引擎内 runXpl 先于状态守卫，双节点同秒 cron 双跑近确定性）；remind 扫描 :122-141（listener 副作用 + remindCount 读-改-写 :130-135 丢失更新）；race skip :98-103（只兜后到者迁移被拒，不防 source 双跑） | WfTaskScanner 多节点/重叠扫描无 fencing：dueAction source 在两节点各执行一次（超时升级/通知类副作用翻倍，成功侧双跑完全静默）+ remindCount 双会话各读 0 各写 1 丢失更新——守卫（ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS）只发生在副作用之后 | 裁定（MA-23 族六条款，fencing 平台语义建立批——R2-13 引用不重建，见对齐纪律）：①扫描抢占 = 条件 UPDATE 把 due 步骤置「处理中+holderId+期限」（CAS 语义抢占，候选分支：分布式调度锁/幂等键 wfId+stepId+dueTime 去重——Phase 1 一次定夺；**预设落 nop-wf 模块内，上收平台层须批内申报**）；②覆盖 due+remind 两路径与单节点重叠形态；③remindCount 改 DB 原子自增；④修复不得破坏引擎 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 现役兜底（:98-103 race skip 零回退）；⑤C10-011 双 scanner 竞速测试（触发次数恒==1、remindCount 终值恒==轮数）；⑥与 nop-job 部署形态（LocalJobScheduler 无集群锁）联动声明 + 扫描链 riding R1-01 绝缘层语义零回退（链存活/失败可见）；C1-005 remindCount RMW/重叠互斥面随③②闭合 |

### 平台设施与测试资产盘点（live 实读 @ `83052aa1ff`）

- **先例范式可引用不重开**：`DaoTaskStateStore.rowLock` wfInstanceId/taskInstanceId 条带锁范式（plan 364 [05-02]，MA-02 直接投影）；plan 364 [02-06] 构建期硬失败校验链（`TaskStepEnhancer.java:124-152` timeout/rateLimit 在案，MA-03 同码同位）；plan 364 [05-04] 强引用裁定与驱逐分裂 permit 池约束（MA-08 处置约束前提）；nop-job C5-002 分页游标修法先例（MA-05 R02 分批范式）；plan 2496 绝缘层契约（MA-23 扫描链存活面）；merge-adjudication §5「先落 C10 缺保护测试（缺陷态红）→ 修复（转绿）」次序纪律；plan 2513 三联裁定模式（环境抖动 stash 基线同红/solo 绿/复跑全绿）。
- **既有测试资产（回归门禁与改写/补测对象）**：`TestPhase4TerminalAndConcurrency`（nop-task-core，含 [05-03] suspended-cancel KILLED 钉 :159-171——**MA-01 零回退钉**；[05-04] `globalGates_strongRefRegistry_sameInstanceAcrossManyKeys` :175-194——**MA-08 反向钉死，修复前必须先改写**[C10-009：保留同 key 同实例断言+新增上界断言]）；`TestWfTaskScanner`（nop-wf-scheduler，6+ 测试，含 `testScanDueTasksIgnoresCurrentStepStatusRace` :112——**MA-23 现役兜底零回退钉**[merge-adjudication MA-06 条款⑥同款义务]；proxy 注入体系 :447-592 为 C10-011 双 scanner 竞速测试载体）；nop-wf-core 测试树（`engine/mock/model/store/support` 五包，MA-02 五站点并发测试扩展面）；nop-task-core 测试树（`builder/impl/reflect/state/step/utils` 六包，MA-01/MA-03/MA-08 补测落点）；`BaseTestCase`（nop-wf-scheduler 测试基类，DB 集成载体）。
- **owner doc 现状**（2026-10-30 亲读）：`docs-for-ai/03-modules/nop-task.md`（289 行）：§挂起与恢复 :59-71（plan 349/364 契约——MA-01 修复后补「SUSPENDED 转移与 cancel 原子化」契约句）；§模型与加载/§步骤怎么写 :93-198（**无 sync×executor 组合约束声明**——MA-03 fail-fast 契约句落点候选）；**无 throttle/rate-limit global 键空间声明节**（MA-08 owner 表态裁定的现状依据，见对齐纪律节）；§源码锚点 :269-280（TaskFlowManagerImpl 已列 :274——MA-08 落地后补键空间治理声明行）。`docs-for-ai/03-modules/nop-wf.md`（70 行，薄）：无 dueAction/扫描/fencing 契约节——**MA-23 fencing 语义声明主落点候选 + 补节**；`docs-for-ai/02-core-guides/workflow-configuration.md` :469-474（dueAction 系统触发节——MA-23 多节点语义声明落点候选，按 E5 一并更新）。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `83052aa1ff`，漂移主因 = plan 364/2498/349 落地插入注释与守卫块，机制面零实质漂移）**：C1-001 SUSPENDED 游离写 live :159-160（审计 :159-160 ✓）、cancel 判定块 live :97-105（审计 :97-105 ✓，plan 364 [05-03] 注释扩至 :90-93）、四终态 driver live :186-192/202-208/237-243/252-258（审计同值 ✓）；C3-001 `getWorkflow` live :78-87（审计 :81-87，+3 粒度）✓、组完成判定调用 live :1267（审计 :1264-1275 区间，复核勘误已在审计内登记 ✓）、`doTransition` 调用 live :1280（审计 :1280 ✓）、`runXpl` live :1255（审计 :1255 ✓）、`shouldExecGroupComplete` live `ExecGroupSupport.java:20-35`（审计 :27-33，±5 粒度）✓、vote 判定 :37-63（审计 :52 单点，区间粒度）✓；C3-002 executor 包装 live :118-121（审计 :117-141 区间 ✓）、sync 包装 live :161-162（审计 :159-160，**plan 364 [02-06] rateLimit 校验块插入致 +2**）、SyncTaskStepWrapper :15-18 ✓、ExecutorTaskStepWrapper 取消兜底 live :51-63（审计 :59-66，plan 349 Phase 4 取消兜底注释扩块致粒度漂移，机制一致 ✓）；C5-001 `findDueActivatedSteps` live :180-186（审计 :180-186 ✓）、`findRemindActivatedSteps` live :188-194（审计 :188-194 ✓）、due 消费循环 live :78-119（审计 :78-119 ✓）、失败分支 live :108-118（审计 :112-118，race skip INFO 分支插入致粒度扩 ✓）、remindCount RMW live :130-135（审计 :128-135，±2）✓；C5-003 注册表 live :63-66（审计 :63-66 ✓）、getRateLimiter live :178-197（审计 :174-207 区间内，**frozen WARN 块 :188-192 为 plan 349 Phase 5 新增**）、warnIfOverCap live :209-215（审计 :209-215 ✓）、ThrottleTaskStepWrapper.getKey live :55-65（审计 :55-65 ✓）、task.xdef :115/:121 ✓。
2. **MA-04 已修勘误**：1.5 单元 P1 族 7 项中 MA-04（C4-001 retry/delay globalTimer 错池）已随 R1-03（plan 2498）fixed（cra-index L164「已修（R1-03，plan 2498 closure audit PASS）」；live `TaskStepEnhancer.java:115-118` RetryTaskStepWrapper 已带 executor 隔离）；本批 Non-Goal 零触碰，C10-007（MA-04 收口门）已随 2498 闭合不在本批前置集。
3. **C3-002 复核校正登记（audit 复核字段 7）**：「kill 也无法解围」过强——`ExecutorTaskStepWrapper` 取消兜底可 complete ret 唤醒 syncGet 等待者，但平台无任务级 kill/cancel API，无外部取消源时滞留实际永久；缺陷表述按校正口径登记，修复方向②（syncGet 时限+cancel 接入）不受影响。
4. **R2-13 依赖列勘误申报**：roadmap 8.22 行 Deps 列现只写「硬依赖 8.2（R1-01）」，未列对 8.20 的 fencing 依赖——batch-proposal 附录 L313 与 R2-13 行 Deps 列内文「C7-001 fencing 对齐 R1-01+R2-11」为准；本批 Phase 4 就绪注记时提请 roadmap 行修订（本批不改 8.22 行）。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-task -am` / `./mvnw test -pl nop-wf -am` 触达上游模块（nop-kernel/nop-persistence/nop-orm 等）——上游环境抖动（含 debezium fork timeout、负载时序类 flake、fork 启动崩溃类环境项）按 plan 2512/2513 先例**「stash 基线同红 / solo 绿 / 恢复复跑全绿」三联裁定**处理并留痕，不得静默重跑或跳过。

## Goals

- Phase 1 设计裁定文档一次裁决六项裁定（独立 draft review 通过），无临场发明空间：①MA-02 五站点原子化单一设计（条带锁+锁内重读预设分支、五站点逐一处置表、跨进程边界、MA-17 契约节）；②MA-01 监视器收敛（SUSPENDED 纳入同监视器+C1-009 复活半边+TIMEOUT 分类裁定）；③MA-03 sync×executor 构建期互斥+syncGet 时限/取消接入；④MA-05 分批游标+毒步骤收敛接缝（MA-06 字段语义定界）；⑤MA-08 键空间治理（owner 表态出口两分支呈现+R01 SSOT 对齐投影）；⑥MA-23 fencing 平台语义（R12 族建立批，holderId+条件 UPDATE 抢占+单飞+remindCount 原子，R2-13 引用边界）——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 6 项成员全部修复并各带回归测试（rule 25）；C10 八条收口门（C10-001/003/004/005/006/008/009/011）随本体同 diff 落地，其中 C10-009 为反向钉死先改写（修复 diff 必须先改测试）。
- 关键工单先红后绿或变异/回退论证留痕（E2）：红① MA-01 交错窗红（latch 控制挂起分支在写 SUSPENDED 前挂起 × 并发 cancel——缺陷态 KILLED 不驱动红→修复后 KILLED 即时驱动绿；C1-009 复活面对称红）；红② MA-02 组迁移红（双线程并发 action 同 and 组——缺陷态组迁移丢失[下游步骤未创建]红→修复后迁移恰好一次绿；join 双实例对称红）；红③ MA-03 死锁红（pool-size=1 嵌套——缺陷态互等超时红→修复后构建期拒绝或时限完成绿）；红④ MA-05 收敛红（毒步骤注入——缺陷态集合不收敛红→修复后退出扫描集绿+查询形状断言）；红⑤ MA-08 上界红（动态键注入 N 键——缺陷态 registry.size 无界红→修复后有界/被拒绿；反向钉死先改写记录在案）；红⑥ MA-23 双跑红（双 scanner 竞速——缺陷态触发次数==2/remindCount 丢失红→修复后恒==1/终值==轮数绿）。
- 联调纪律显式守约（本批四条）：**MA-23 fencing 单一平台方案**（R1-01 绝缘层零回退 + R2-13 引用边界，R12 族语义在本批设计文档定稿）；**MA-08 R01 对齐**（族级纪律进程内投影 + owner 表态出口两分支 + SSOT 修订触发条件）；**MA-05×MA-06 接缝**（失败计数/退避字段语义本批定、MA-06 本体归 R4-08）；**MA-02×MA-17 契约节**（实例线程封闭声明随裁定成文）。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-task.md`（§挂起与恢复 原子化契约句、sync×executor 约束、MA-08 键空间治理/包络声明、源码锚点补行）+ `docs-for-ai/03-modules/nop-wf.md`（扫描/fencing/组语义契约补节）+ `docs-for-ai/02-core-guides/workflow-configuration.md`（dueAction 多节点语义句，按实际）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R2-13（8.22）回写 fencing 语义引用注记（含 roadmap 8.22 行 Deps 勘误申报[Baseline 勘误 4]）；向 R4-08（8.48）/R4-09（8.49）回写软依赖就绪注记（含 MA-09/MA-10 联动边界、MA-06 毒步骤字段接缝、C10-010/012/014 处置边界确认）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-08 成员**（G14 十二项：1.5-MA-06/07/10/11/12/13/14/15/16/18/20/22）及其 C10-002/C10-013——软依赖本批，本批零承接零顺手修；特别地 **MA-06 本体**（失败无抑制四失效面：ERROR 抑制/skip 升 WARN/due-backlog gauge/模型加载期校验）为 MA-05 同扫描面相邻工单——本批只落毒步骤收敛最小面并定字段语义接缝，MA-06 本体归 R4-08 不代做；**MA-09 本体**（stats 分页/聚合摘要/只读端点全量治理）随 MA-08 注记接缝归 R4-08；**MA-17 本体**（C1-003 台账行）只随 MA-02 落契约声明半边。
- **不做 R4-09 成员**（G15 十三项：1.5-C1-006/C3-003/C3-004/C4-003/C4-004/C4-007/C5-005/C5-006/C7-004/C7-007/C7-009/C7-010/C7-014）及其 C10-010/C10-012/C10-014——特别地 **C7-014**（task 执行级 fencing 双执行）与 MA-01 的 C1-009 为**分立裁定**（merge-adjudication §3-B：根因不同——无执行级互斥 vs SUSPENDED 写者无守卫），本批零触碰；**C4-003**（扫描 job yaml invoker.bean 失配，已知未修复 2026-09 P2-3.1-001 同案）归 R4-09 按同案对齐一处修复。
- **不做 R2-13 成员**（1.7 八项，含 C7-001 RUNNING 死区本体）——C7-001 到达时引用本批 fencing 设计文档对齐，不重建第二套 fencing；本批不代做其测试 N1 参数化改写前置。
- **不做 MA-04 面**（plan 2498 已闭合）——`RetryTaskStepWrapper` executor 隔离（:115-118）零触碰零回退。
- 不触碰 plan 364/349/2496/2498 已修面（零回退引用）、`model/*.orm.xml`（join 唯一约束维持应用层占位零 DDL；**唯一例外=裁定⑥ MA-23 两列批内申报，见头部 PA 行**）、`_` 前缀生成物；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-task/nop-task-core`：`impl/TaskImpl`（MA-01）+ `impl/TaskRuntimeImpl`（MA-01 resume 半边）+ `impl/TaskFlowManagerImpl`（MA-08）+ `builder/TaskStepEnhancer`（MA-03）+ `step/SyncTaskStepWrapper`/相关（MA-03）+ 测试（C10-001/006/009 补测或改写 + `TestPhase4TerminalAndConcurrency` 零回退钉[05-04 行按 C10-009 同 diff 改写]）。
- `nop-wf/nop-wf-core`：`engine/WorkflowEngineImpl`（MA-02 五站点）+ `engine/ExecGroupSupport`（零回退）+ 测试（C10-003/004/005）。
- `nop-wf/nop-wf-dao`：`store/DaoWorkflowStore`（MA-02 行锁载体/MA-05 查询族/MA-23 条件 UPDATE）+ 测试。
- `nop-wf/nop-wf-scheduler`：`WfTaskScanner`（MA-05 消费面/MA-23 fencing）+ 测试（C10-008/011，`TestWfTaskScanner` 零回退钉）。
- 设计文档：`ai-dev/design/concurrency/wf-task-scanner-fencing-and-task-wf-concurrency.md`（新建；裁定①–⑥+测试缝+零回退钉，最终状态写法；**R12 族平台 fencing 语义权威载体**）。Owner doc：`docs-for-ai/03-modules/nop-task.md`、`docs-for-ai/03-modules/nop-wf.md`、`docs-for-ai/02-core-guides/workflow-configuration.md`（按实际）、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R2-13/R4-08/R4-09 及后续批次工单；nop-kernel/nop-api-core 平台设施本体（除非 MA-23/MA-08 裁定上收且批内申报）；nop-cluster 集群锁接入（MA-23 备选分支若裁定需依赖 nop-cluster，Phase 1 评估 pom 变更面并申报）；`model/*.orm.xml`（裁定⑥申报两列除外）与 `_` 前缀生成物手改（含 `_vfs` 下 job yaml 除非裁定触发批内申报；迁移脚本为非 `_` 前缀新建）。

## Execution Plan

### Phase 1 - task/wf 并发设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/wf-task-scanner-fencing-and-task-wf-concurrency.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威 + R12 族 fencing 语义载体）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下六项裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① MA-02 五站点原子化**：wfInstanceId 条带锁（`DaoTaskStateStore.rowLock` 范式投影）+ 锁内重读的预设分支；五站点逐一处置表（组迁移[含 vote 快照口径零回退]/checkEnd 终结/join 双实例 and+or 两型[占位 vs 唯一约束两分支，零 DDL 前提]/action 双跑[默认分支=条带锁直接覆盖（同 wf 实例串行化后双跑仅剩跨进程残留面）+跨进程幂等边界声明；占位/唯一约束仅在锁方案不足时呈 Phase 1 备选]/子流回滚重放）；`saveWfRecord` 6 调用点覆盖约束；跨进程残留边界显式声明（SELECT FOR UPDATE 备选）；MA-17 实例线程封闭契约节（落 owner doc 句）
  - [x] **裁定② MA-01 监视器收敛**：SUSPENDED 转移纳入 `synchronized(taskState)` + 终态前置检查；C1-009 复活半边（resume 非终态确认+ACTIVE 显式重置落盘）；[05-03] 零回退声明；「timeout-cancel 记 KILLED 非 TIMEOUT」分类裁定；不引入新同步结构声明（或锁序复核义务）；C10-001 假保护→真并发交错注入测试形态
  - [x] **裁定③ MA-03 sync×executor**：构建期互斥/同池嵌套检测 fail-fast（校验链同码同位）；syncGet 时限+cancel 接入（缺省值/可配置性成文）；C2-010 闭合声明；C10-006 pool-size=1 死锁回归形态
  - [x] **裁定④ MA-05 分批+毒步骤接缝**：limit+orderBy 分页游标（due/remind 同构同修）；批次上限/时间预算配置键与缺省值；失败计数/退避字段语义（**MA-06 接缝：字段由本批定、MA-06 本体归 R4-08**）；C10-008 查询形状+收敛性断言口径
  - [x] **裁定⑤ MA-08 键空间治理**：预设分支=治理（准入约束或 lastAccess+空闲逐出，permit 池完整性约束前提）；**owner 表态出口两分支呈现**（治理 vs 构建期拒绝+文档缺口降级）+ 表态记录机制（owner=nop-task owner doc 维护方，表态落设计文档 Decision 记录；未表态→默认治理分支兜底，不阻塞）；R01 SSOT 对齐投影（族级纪律三要素落进程内形态；引入保留/淘汰参数键须回 SSOT 修订）；输入直通面覆盖；registry.size gauge 半面（MA-09 全量治理归 R4-08 注记）；C10-009 反向钉死改写契约（保留同 key 同实例断言+新增上界断言）
  - [x] **裁定⑥ MA-23 fencing 平台语义**（R12 族建立批）：条件 UPDATE 抢占（holderId+期限）预设分支 vs 分布式调度锁/幂等键备选一次定夺；due+remind 双路径+单节点重叠覆盖；remindCount DB 原子自增；引擎 `ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS` 兜底零回退；R1-01 绝缘层 riding 零回退声明；**R2-13 引用边界条款**（本文档为族内 fencing 语义权威，R2-13 到达引用不重建）；平台层上收申报触发条件成文；C10-011 双 scanner 竞速形态
  - [x] **裁定⑦ 测试缝与零回退钉清单**：红①–红⑥ 测试形态逐条（含缺陷态红判据）；零回退钉 = `TestPhase4TerminalAndConcurrency`（[05-03] 钉全列；[05-04] 钉按 C10-009 改写后形态）、`TestWfTaskScanner`（含 `testScanDueTasksIgnoresCurrentStepStatusRace` :112）、`ExecGroupSupport` vote 快照口径、`RetryTaskStepWrapper` 隔离注释面（2498）全列；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑦全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定⑤含 owner 表态出口两分支与 R01 SSOT 对齐投影；裁定⑥含 R2-13 引用边界与 R1-01 零回退、平台层上收申报触发条件；裁定①含五站点处置表与 MA-17 契约节；裁定④含 MA-06 接缝定界
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 6 成员实现 + 回归测试（按 task/wf 两组）

Status: planned
Targets: `nop-task/nop-task-core/src/`、`nop-wf/nop-wf-core/src/`、`nop-wf/nop-wf-dao/src/`、`nop-wf/nop-wf-scheduler/src/`——各自 main + test 树

- Item Types: `Fix`（6 项均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [ ] 组 task（nop-task-core）：MA-01（C1-001 监视器收敛，按裁定②）+ C10-001 收口门；MA-03（C3-002 sync×executor，按裁定③）+ C10-006 收口门；MA-08（C5-003 键空间治理，按裁定⑤）+ C10-009 反向钉死先改写收口门
- [ ] 组 wf（nop-wf-core/dao/scheduler）：MA-02（C3-001 五站点，按裁定①）+ C10-003/004/005 三收口门；MA-05（C5-001 分批+毒步骤，按裁定④）+ C10-008 收口门；MA-23（C7-001 fencing，按裁定⑥）+ C10-011 收口门
- [ ] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕；C10-009 为先改测试再修本体的强制前置）：
  - [ ] MA-01/C10-001 红①：真并发交错注入（latch 挂起分支写前挂起 × cancel 线程）——缺陷态 KILLED 不驱动红 → 修复后 KILLED 即时驱动绿；C1-009 复活面对称绿；[05-03] 既有钉零回退
  - [ ] MA-02/C10-003/004/005 红②：双线程并发 action 同 and 组（组迁移恰好一次）+ 末段并发完成（实例不滞留 ACTIVATED）+ join 并发到达（单实例）——缺陷态各自红 → 修复后绿
  - [ ] MA-03/C10-006 红③：pool-size=1 sync×executor 嵌套——缺陷态互等超时红 → 修复后构建期拒绝绿 + syncGet 时限/取消路径绿
  - [ ] MA-05/C10-008 红④：毒步骤注入 + 查询形状断言（limit/orderBy 存在）——缺陷态集合不收敛红 → 修复后退出扫描集绿
  - [ ] MA-08/C10-009 红⑤：动态高基数键注入——缺陷态 registry.size 无界红 → 修复后有界/被拒绿；`globalGates_strongRefRegistry` 改写记录（保留断言+新增断言清单）在案
  - [ ] MA-23/C10-011 红⑥：双 scanner 竞速（DB 集成或 proxy 注入）——缺陷态触发次数==2/remindCount 丢失红 → 修复后触发恒==1、remindCount 终值==轮数绿
- [ ] **端到端验证**（rule 22）：≥1 条 task 全链（模型构建[fail-fast 拒绝面]→执行→挂起→cancel→终态落盘，覆盖裁定②③；DB 持久化形态）；≥1 条 wf 全链（审批流 and 组双人提交→组迁移→流程终结，覆盖裁定①；due 扫描 job 启用→抢占→action 恰好一次→dueTime 推进收敛，覆盖裁定④⑥——`TestWfTaskScanner`/`BaseTestCase` DB 载体扩展）
- [ ] **接线验证**（rule 23）：裁定②SUSPENDED 写点真实经新监视器路径断言（非仅 synchronized 关键字存在）；裁定①条带锁真实被 `invokeAction`/`checkEnd` 链消费断言；裁定⑤注册表治理真实被 `getRateLimiter/getSemaphore` computeIfAbsent 路径消费断言；裁定⑥条件 UPDATE 真实被 scanner 循环调用+抢占失败分支可见断言（非仅 SQL 存在）
- [ ] **无静默跳过**（rule 24）：裁定③构建期拒绝显式抛 ERR_TASK_STEP_CONFIG_INVALID 族（非静默忽略组合）；裁定⑥抢占失败显式 skip 日志路径（复用 race skip INFO 形态，非吞）；裁定④失败计数路径显式可见（非空 catch）；新增公共分支无空方法体/TODO
- [ ] **提交纪律**：设计文档先行单独提交；按组提交——task / wf，C10 测试与对应本体同一 commit（C10-009 改写与 MA-08 本体同一变更集）；组间无耦合时可独立提交序列
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] 6 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 八条收口门同 diff 落地（C10-009 先改写记录在案）
- [ ] 必红证据（E2）：红①–红⑥均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [ ] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；[05-03] KILLED 钉、vote 快照口径、`testScanDueTasksIgnoresCurrentStepStatusRace`、`RetryTaskStepWrapper` 隔离面为关键钉）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-task -am` 与 `./mvnw test -pl nop-wf -am` 均 BUILD SUCCESS（上游环境抖动按 Baseline 勘误 5 三联裁定处理并留痕）；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-task/nop-wf 聚合全量测试；`docs-for-ai/03-modules/nop-task.md`；`docs-for-ai/03-modules/nop-wf.md`；`docs-for-ai/02-core-guides/workflow-configuration.md`（按实际）；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2513 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-task -am` 与 `./mvnw test -pl nop-wf -am` 均 BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `model/*.orm.xml`（裁定⑥申报两列与 `IWorkflowStore.withWfLock` 接口新增除外）、`_` 前缀生成物手改、R2-13/R4-08/R4-09 批外成员本体（MA-06/MA-09 本体/C7-014/C4-003/MA-04 等）、plan 364/349/2496/2498 已修面零 diff 核对登记
- [ ] Owner doc 更新：`nop-task.md`——§挂起与恢复补 MA-01 原子化契约句、sync×executor 构建期约束（MA-03）、throttle/rate-limit global 键空间治理/包络声明（MA-08，按裁定⑤分支成文）、源码锚点表补行；`nop-wf.md`——扫描/fencing/组完成语义契约补节（MA-02/MA-23，含「以设计文档为准」归属声明）；`workflow-configuration.md` dueAction 多节点语义句（按实际）；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 与 `--module nop-wf --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 两条批行验证命令全部 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-task.md/nop-wf.md 落点 + workflow-configuration.md（按实际）+ source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（orm.xml/生成物/R2-13/R4-08/R4-09 批外成员本体/2496·2498·364·349 已修面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.20 行、cra-index 台账、R2-13/R4-08/R4-09 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（五站点原子化/监视器收敛/fail-fast/分批游标/键空间治理/fencing 抢占运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：L161/L162/L163/L165/L166/L167 六行状态收敛（open → fixed，注记 plan 2514；吸收成员随各主行括注）；C10 八条收敛注记；roadmap 8.20 行 Status → done；R2-13（8.22）fencing 引用注记（含 roadmap 行 Deps 勘误申报）；R4-08（8.48）/R4-09（8.49）软依赖就绪注记（沿 2513 R4-06 先例——含 MA-06 毒步骤字段接缝、MA-09/MA-10 联动边界、C10-010/012/014 处置边界确认、C7-014 分立声明）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 6 行收敛 + C10 注记、roadmap 8.20 = done、R2-13/R4-08/R4-09 就绪注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 6 项成员全部修复：MA-02（五站点快照竞态一次闭合）、MA-01（SUSPENDED 监视器收敛+复活半边）、MA-03（sync+executor 嵌套死锁解除）、MA-05（扫描分批+毒步骤收敛）、MA-08（闸门注册表键空间治理/准入，含 owner 表态出口落定）、MA-23（WfTaskScanner fencing）
- [ ] batch-proposal R2-11 行 exit criteria 增补四段逐条达成并留证：①**五站点快照竞态修复（组迁移/终结滞留/join 双实例/action 双跑/子流回滚）**；②**sync+executor 嵌套死锁解除**；③**闸门注册表上限（R01，plan 364 之上 owner 表态出口落定并留痕）**；④**WfTaskScanner fencing（holderId+单飞+remindCount 原子）**
- [ ] 对齐纪律守约：MA-23 fencing 单一平台方案（R1-01 绝缘层零回退 + R2-13 引用边界成文，R12 族语义在设计文档定稿；平台层上收如发生已批内申报）；MA-08 R01 对齐（族级纪律投影 + owner 表态记录 + SSOT 修订触发条件）；MA-05×MA-06 接缝（字段语义定界，MA-06 本体未代做）；MA-02×MA-17 契约节落 owner doc
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑥均有先红后绿或变异/回退论证（E2）；C10 八条同 diff 落地（C10-009 反向钉死先改写留痕）
- [ ] 设计文档裁定①–⑦成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 两条批行验证命令 `./mvnw test -pl nop-task -am`、`./mvnw test -pl nop-wf -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：nop-task.md + nop-wf.md（+workflow-configuration.md/source-anchors.md 按实际）（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）五站点原子化/监视器收敛/死锁解除/分批收敛/键空间治理/fencing 抢占运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-task/nop-wf 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 6 行收敛 + roadmap 8.20 → done + R2-13/R4-08/R4-09 就绪注记（E6）

## Deferred But Adjudicated

### R4-08 成员（G14 十二项：1.5-MA-06/07/10/11/12/13/14/15/16/18/20/22）及其 C10-002/C10-013

- Classification: `out-of-scope improvement`（batch-proposal R4-08 行显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-08 批 plan（roadmap 8.48，软依赖 8.20——本批 done 后就绪）
- Why Not Blocking Closure: 判级对齐零重复——cra-index 台账 G14 行批次列=R4-08；本批已收敛 1.5 全部 6 个 open P1，R4-08 实施以本批后的 live 形态为基线，且可引用本批裁定④（MA-06 毒步骤字段接缝）与裁定⑤（MA-09 观测面接缝）为既有裁定不重开体。

### R4-09 成员（G15 十三项，含 C7-014/C4-003）及其 C10-010/C10-012/C10-014

- Classification: `out-of-scope improvement`（batch-proposal R4-09 行显式安排）；Successor Required: `yes`；Successor Path: R4-09（roadmap 8.49，软依赖 8.20/8.48）
- Why Not Blocking Closure: C7-014 与 MA-01 为分立裁定（merge-adjudication §3-B：无执行级互斥 vs 写者无守卫，修复工单不同），本批不代做；C4-003 按同案对齐一处修复（2026-09 P2-3.1-001）归 R4-09。

### R2-13 成员（1.7 八项，含 C7-001 RUNNING 死区本体）

- Classification: `out-of-scope improvement`（batch-proposal R2-13 行显式安排）；Successor Required: `yes`；Successor Path: R2-13（roadmap 8.22，fencing 对齐依赖 8.20+8.2——本批 done 后就绪）
- Why Not Blocking Closure: C7-001 到达时按本批设计文档裁定⑥（R12 族 fencing 语义权威载体）引用对齐，不重建第二套 fencing；其测试 N1 参数化改写前置随该批执行。

## Non-Blocking Follow-ups

- **R2-13/R4-08/R4-09 对接注记**：本批落地后向 roadmap 8.22/8.48/8.49 行作就绪注记（8.22 含 Deps 列勘误申报；8.48/8.49 软依赖解除落点）；三批实施引用本设计文档为既有裁定。
- **MA-08 观测面的运维告警阈值**：registry.size gauge 与准入/逐出计数的生产告警阈值归运维观测（owner doc 留观测指引即可，不阻塞 closure）。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
