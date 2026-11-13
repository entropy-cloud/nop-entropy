# job 调度账本观测读面、raced 分支与执行载体包络并发契约（CRA 8.46 R4-06 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；独立评审记录见「评审记录」节）
> Last Reviewed: 2026-11-07
> Source: plan 2539（[2539-cra-phase8.46-r4-06-job-scheduler-ledger-a.md](../../plans/2539-cra-phase8.46-r4-06-job-scheduler-ledger-a.md)，CRA Phase 8.46 R4-06 1.4 调度账本束 a G13 前半，10 工单全 P2；头部 Draft Review R1 修订 **MJ-1+MN-1~8 = 裁定输入硬约束**——MJ-1 红⑧/C10-004(iii) 绿腿谓词订正「调度链存活+FAILED 终态不自愈禁断言自愈」（裁定 C.2 落款）、MN-1 红⑨ 构造纪律（裁定 G.1 前瞻落款）、MN-2~8 逐项吸收落款见「Draft Review R1 修订吸收登记」节）；发现事实源 = `ai-dev/audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/`（[merge-adjudication.md](../../audits/2026-10/2026-10-14-2230-deep-audit-nop-job-concurrency/merge-adjudication.md) §2.1 族表 [MA-02/03/04/07] + §2.2 独立案表六行 + §2.3 簇共设计 + §4.4 C10 映射 + §4.5 跨单元关联 1 + §五 批次建议 2/3/4/6[MN-R2-3 订正]）；批次定义 = batch-proposal §二.4 R4-06 行 + §一 E1–E6
> Owners: 裁定 A（读面快照域：W-01 getJobDetail 持锁快照 + W-03 收口核验与缝5 形式化钉+嵌套残余面定夺）；裁定 B（raced 分支域：W-02 fall-through 重试 + W-07 allowUpdate 补 throw 同分支一次重构 + C10-007 一套并发注册测试设计 + C2-005 P3 边界）；裁定 C（Error 兜底域：W-08 双层 catch 收宽+settleExecution 通道复用 + C10-004 三锚点 + 两调用点处置表）；裁定 D（CronExpression ThreadLocal 域：W-04 栈局部化）；裁定 E（scanOnce 在途守卫域：W-05 CAS 守卫）；裁定 F（register 回滚域：W-06 try/catch 回滚）；裁定 G（载体与包络域：W-09 契约化声明 + W-10 语义精确化+包络声明 + 跨单元 1.2-MA-06 对齐落款）。**裁定编号 = Phase 1 起草指令分组（与 plan Goals 预设 A–G 同构）；分组权威 = 本文「工单→裁定映射与分组登记」节。**
> 文件命名裁定：`job-scheduler-ledger-observation-raced-branch-and-executor-envelope-concurrency-contract.md`——与 plan Phase 1 Targets 拟名逐字一致，零调整登记（无 2538 式改名义务）。
> 行号锚 = live HEAD `193a8719f6`（工作仓 `nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`）亲读值；nop-job 产品面对 plan 起草/执行基线 `a1c8651f25` **零 diff git 亲证**（唯一交界 commit = docs(plan) 提交，零产品代码）——plan 工单表行号锚直接承接。局部子锚与 plan 行文存在 ±1~5 行同区域漂移，精确亲读值集中登记于「plan 输入勘正」节（勘误 6 漂移纪律的兑现落点）；执行前如再漂移以 live 为准并登记于 plan 勘误。与 plan 文本不一致的 live 核证事实，裁定以本文为准。

本文是 plan 2539 全部 10 工单修复的唯一设计裁决权威。每条裁定给出：决策（唯一预设默认分支）、理由（并发裁定含触发时序 T1/T2 论证）、被否决的备选、零触碰边界。三范式/既有裁定结论引用不复制——SSOT 在既有设计文档。全批**零产品侧新配置载体、零 nop-job-api diff**（错误码全部复用既有在位码：`ERR_JOB_ALREADY_EXISTS`+`ARG_JOB_NAME`（W-07，live import :35/:37 亲证）、`ERR_JOB_REMOTE_INVOKE_FAILED`（W-06 回滚 complete 通道，2513 裁定①(a) 同码）；裁定 E 拒绝面采「WARN+冲突计数」形态**零新错误码**）、零 orm、零 `_` 生成物、零平台设施本体（nop-kernel/nop-commons 载体族只读引用）。

## 既有裁定引用核对（引用不重建——硬依赖 8.2+8.19 已解除 + 同单元前批 2496/2513 已收口 + 对齐纪律「既有裁定引用条款」授权）

2513 裁定①–③（[job-rpcpoll-termination-and-invoker-lock.md](job-rpcpoll-termination-and-invoker-lock.md) §3.1–§3.8[MN-R2-6 订正]）为本批可引用既有裁定；2496 绝缘契约 / 2497 §七 载体契约 / 2498 §3.1.1 判定标准 / 2509 §3.6 R03 范式为引用边界。本批修复不得重开其体裁，修复 diff 不得触碰其已落地语义与钉内断言。**四红线落款**（两阶段四产物零回退 / rpcPoll 五面零触碰 / 2496 绝缘产物零弱化 / 2498 判定标准对齐落款）见下表红线 1/2/3/4；条件申报①②④三禁选落款见「条件申报登记」节。

| 既有裁定/产物 | 本批引用面 | 引用边界与零回退红线 |
|---|---|---|
| 2513 裁定②（两阶段 executeJob + 占位先行接缝 + ctx 真快照） | W-01/W-02/W-03/W-07/W-08 同文件基线（LocalJobScheduler :330-337/:344-365/:371-390/:398-405/:628-698） | **红线 1（两阶段四产物零回退）**：`prepareJobLocked`（占位 :335 先行）→ `startJobUnlocked`（invokeAsync :354 + catch :355）→ `settleExecution`（重取锁 + fail-safe）→ `abandonAfterInterleave`（identity 复检放弃分支）四产物零改写零回退；W-08 catch 收宽只改 :355 一行 catch 宽度，**三判据时序（占位先行 :335→:354 / settleExecution 重取锁终态迁移 / 出锁段 identity 复检+放弃分支）在修复重排下保持**（裁定 C.1 时序证明）；ctx 真快照（:628-698）零逻辑 diff（仅缝5 钉触达）；「复位先于 whenComplete 注册」既有防护（javadoc :326-328）零回退 |
| 2513 裁定①（rpcPoll 终结驱动四通道+PollEntry 白名单+gauge） | W-06 同文件基线（RpcPollTaskManager :157-181/:192-200/:202-215） | **红线 2（rpcPoll 五面零触碰）**：失败预算（pollOnce 阈值+退避）/deadline 兜底（computeDeadline）/DB 回传（resolveFromAuthority :192-200）/future whenComplete 兜底（:175）+PollEntry 最小视图+activeCount gauge 零改写零重开；**2513 §3.6「register 触达时保序不回滚」系其批内边界（其触达面=预算/回传插入），非本批边界——C1-004 回滚面为其明示预声明承接面（就绪注记「C1-004 同注册表联调边界归 R4-06」），裁定 F 落回滚分支不构成体裁重开**；稳态路径「先 put 后调度」次序零回退（裁定 F.1 保序证明） |
| 2513 裁定③（缝5 防回归缝） | W-03 核验钉的形式化依据 | 缝5 原文（SimpleExecutionContext 不得持有 ScheduledJob/SimpleJobState/JobSpec 引用，javadoc :632-633 在位亲证）——裁定 A.2 反射断言钉为其测试化承载（零语义重建）；2513 §3.7 快照字段清单为钉的白名单对照面 |
| 2496 绝缘契约（owner doc §六） | W-05/W-08/W-10 站点产物面（AbstractBatchScanner :70-91/:118-134/:184-186；LocalJobScheduler :371-390；JobWorkerScannerImpl :382-411） | **红线 3（绝缘产物零弱化）**：scanOnce 全周期体 Throwable 边界+onScanCycleFailed+计数（:119-121/:129-133/:184-186）+startScanning 拒绝真实化（:80-90）+MA-05 站点③ fail-safe（:371-390）+MA-06 observe-only 看门狗（:382-411）零改写零弱化；裁定 E 守卫加于绝缘边界**之内**（失败语义不变证明见 E.1）；`TestRpcPollTaskManagerInsulation` 2 用例/`TestAbstractBatchScanner` 9 用例/`TestJobWorkerScannerInsulation` 零回退 |
| 2497 §七 载体契约（owner doc §七） | W-09/W-10 契约锚与 E5 扩展落点 | globalTimer 调度槽纪律 + `nopJobScheduledExecutor`/`nopRpcPollScheduledExecutor` 容量语义（core 唯一旋钮）+热更 scheduled 专用路径零触碰；裁定 G 的 E5 = §1.4/§3.1/§五/§七 契约句随批更新（引用面扩展，契约本体零改写） |
| 2498 §3.1.1 专用载体判定标准 + Debezium per-engine 先例 | W-10 跨单元对齐基准（批行增补明文） | **红线 4（判定标准对齐落款）**：判定标准四要素（独立容量预算+关闭归属+不经 globalTimer 执行任务体+豁免判据）引用不重建；裁定 G.2 适用结论落款 + 共租回归面声明 + 联合推演记录（G.2.3）；2498 落地面（DebeziumEngineWrapper engineExecutor 等）零触碰零回退 |
| 2509 §3.6 R03 出锁投递范式 | W-09 分离形态投影候选 / W-02 锁结构参照 | 裁定 G.1 采契约化形态——范式零投影消费（前瞻引用落款：若后续批次升级为分离形态，startJobUnlocked 在 worker 线程的 identity 复检按 §3.6 出锁复检范式投影）；W-02 的 putIfAbsent→synchronized 复检结构零重排 |
| plan 340 checker 契约钉（TestJobTimeoutChecker :614-639） | W-06/W-08 邻接零触碰 | checker 侧 RUNNING 兜底钉死契约零触碰；W-06 回滚不触 computeDeadline/checker 写点（C7-008 时钟源结论 2513 注记零重开） |
| 2512 三联裁定模式（环境抖动处置） | E3 验证通道纪律 | stash 基线同红/solo 绿/复跑全绿三联留痕纪律沿用；2538 勘误 7 形态引用 |
| 2537/2538 红绿判据纪律 | 裁定 G 附 | orTimeout 禁令/闩带 ≤30s 且收尾必放闸/`_tmp/` 总册+逐 trial 原始日志存证（Major-D1 灭失教训）/`*R406` 后缀同号防混/结构钉+变异探针诚实留痕禁伪绿/还原-红-复修复-绿变异链断言纪律（R5 探针纪律） |

**既有裁定零重建声明**：本批零新 face token、零既有裁定文档 diff；2513/2496/2497/2498/2509 裁定结论如需修订走各自设计文档修订，不回写本批。

## plan 输入勘正（live 亲读核证 @ `193a8719f6`，裁定以本节为准）

| # | plan 行文 | live 事实（file:line @ `193a8719f6`） | 对裁定的影响 |
|---|---|---|---|
| K1 | AbstractBatchScanner 扫描区行号（「scanOnce :118-138 / :134-137 Throwable 边界 / :129 scanBatches / startScanning :81-87 / onScanCycleFailed :184-192」） | 精确亲读：`scanOnce` :118-134（计数器 :119-121/cursor reset :123-125/onCycleStart :126/scanBatches 调用 :127/**Throwable 边界 :129-133**）；`scanBatches` 方法体 :136-149；`startScanning` :70-91（绝缘钩 :80-90）；`stopScanning` :93-99；`doScan` :101-106；`onScanCycleFailed` :184-186；`newCursor` :170-174；`getExecutor` :220-222；`registeredCursors` :58；存活指标 getters :191-203 | 缺陷机制与站点集**零漂移**（plan 勘误 6 已登记该区 ±2-5 行漂移纪律）；本节落精确值供裁定 E 与红⑤ 钉内引用 |
| K2 | 「私有 isValidExpression :634」归属句 | `isValidExpression` 为 **public static**（:625）；:634 是其体内 `new CronExpression(expression, fields)` **私有构造器调用点**；私有构造器本体 :146-156（withInitial :152） | 裁定 D 的「同修面」 = 字段删除覆盖 :625 入口与 :634 调用点（私有构造器仅为 isValidExpression 服务，parse-only 路径不消费 calendar） |
| K3 | RpcPollTaskManager「activeCount :186-188 / resolveFromAuthority :191-200」 | 精确亲读：`activeCount` :183-185；`resolveFromAuthority` :192-200；`cancelEntry` :202-215（句柄取消+守卫式 removeOnCancel+`entries.remove(taskId, entry)` **两参条件移除**——幂等清理全链在位）；`onCancelToken` :217-229；register 内各步（:161 put/:163-169 appendOnCancel/:171-172 scheduleWithFixedDelay/:173 setPollHandle/:175 whenComplete/:177-179 isDone 双检）零漂移命中 | 裁定 F 回滚通道复用 cancelEntry 幂等性成立（含注册表条件移除，回滚体无需重复 remove）；两参 remove 保证回滚不误删同名新条目 |
| K4 | JobWorkerScannerImpl 看门狗登记区行号（「:337-341 看门狗登记」） | invokeAsync :341 **零漂移命中**；in-flight 登记区精确值：`inFlightInvokeStartMillis.put` :351 / `scheduleInvokeWatchdog` 调用 :354 / `remove` :357 / 方法本体 :382 / observe-only 注释 :405；CAS 复检注释区 :329；maxConcurrency setter :163-165；ceiling 判定 :184-187 | 2496 看门狗产物面零触碰边界以精确值为准（裁定 G.2 零触碰清单）；W-10 缺陷面锚（:341 在 globalWorker 线程内联）亲证维持 |
| K5 | owner doc §3.1 异步性契约句区间「:316-329」 | 异步性契约 blockquote（「**异步性契约**（plan 2513 裁定②）…建议自行真异步化」）实际落点 **:331**（§3.1 :317 起，接口 :319-327，ctx 说明 :329，blockquote :331）；其余锚点亲读命中：§1.2 :93 addJob 契约句/§1.4 :214「本地池隔离属后续批次面」句/§五 :435 `max-concurrency|0` 行/§六 :448-470/§七 :472-478/§八 :482-493/源码锚点表 :512-530（:527 行含 prepareJobLocked :330/startJobUnlocked :344 锚） | 裁定 G 的 E5 落点引用以精确值为准（:214 兑现承诺句约束不变） |

## 工单→裁定映射与分组登记

| 工单（finding ID；判级） | 裁定 | 承载节 | plan 组 | 起草指令对照 |
|---|---|---|---|---|
| W-01（1.4-MA-02；P2） | A.1 | getJobDetail 持锁快照唯一分支 + 红① 检出力口径 | 组 A（local 状态纪律域） | 指令-A 同点位 |
| W-03（1.4-MA-04；P2——2513 已收口，Proof 型） | A.2 | 收口核验结论 + 缝5 形式化钉 + 嵌套残余面定夺 + 红④ | 组 A | 指令-A 同点位（W-01/W-03 同域绑定兑现） |
| W-02（1.4-MA-03；P2） | B.1 | raced 复检失败 fall-through 重试唯一分支 + WARN 文案 + 红② | 组 A（与 W-07 同分支一次重构绑定） | 指令-B 同点位 |
| W-07（1.4-C2-004；P2） | B.2 | raced 复检通过补 throw（复用既有码）+ 交互次序 + 红⑦ | 组 A | 指令-B 同点位 |
| C10-007（验收前置——同 diff 一套测试） | B.3 | 一套并发注册测试设计（atMostOneWinner+add×remove 两腿；检出力口径定夺） | 组 A 共承 | 指令-B 同组 |
| W-08（1.4-C2-007；P2——残余面） | C.1/C.2 | catch 收宽双层唯一分支 + 2513 四产物零回退落款 + C10-004 三锚点 + 调用点处置表 + 红⑧ | 组 A | 指令-C 同点位 |
| W-04（1.4-MA-07；P2） | D | ThreadLocal 栈局部化唯一分支 + 结构钉 + 功能绿腿 + CronCalendar 关联面核对 | 组 B（core 扫描/触发器域） | 指令-D 同点位 |
| W-05（1.4-C1-003；P2） | E | CAS 在途守卫唯一分支 + 2496 绝缘零弱化落款 + 6 子类覆盖 + 红⑤ | 组 B | 指令-E 同点位 |
| W-06（1.4-C1-004；P2） | F | try/catch 回滚唯一分支 + cancelEntry 复用时序保序 + REE 缝 + 红⑥ | 组 C（载体包络与注册回滚域） | 指令-F 同点位 |
| W-09（1.4-C4-002；P2） | G.1 | 触发/执行关系契约化声明唯一分支 + owner doc :214 兑现 + 红⑨ | 组 C | 指令-G 同点位 |
| W-10（1.4-C4-005；P2——跨单元 1.2-MA-06 对齐） | G.2 | 三候选组合定夺 + 对齐落款 + 联合推演记录 + 红⑩ | 组 C | 指令-G 同点位 |
| 红①–红⑩ 总表 + 零回退钉全列 + C10 注记 | G 附 | 驱动器总表 | 全批 | 指令-G 附 |

**分组确认声明**：plan Goals 预设「裁定 A–G 按主题域分组，最终分组权威在设计文档映射表」——本文映射与 plan Goals/工单表预设**逐项一致，零调整登记**；plan 组 A（W-01/02/03/07/08→裁定 A+B+C）/组 B（W-04/05→裁定 D+E）/组 C（W-06/09/10→裁定 F+G）分组关系维持。同文件交界登记：LocalJobScheduler 六面（getJobDetail 读面 :64-68 / raced 分支 :112-121 / SimpleExecutionContext :628-698 / startJobUnlocked Error 面 :344-365 / scheduleNext/startJobUnlocked 线程归属面）**diff 分组登记禁顺带禁交叉**；W-02+W-07 同分支（:115-121 区）同一次重构为批内强制绑定（merge-adjudication §2.3-1 升格形态）——同组单 diff、分节登记。

## Draft Review R1 修订吸收登记（MJ-1+MN-1~8 逐项落款）

| 修订 | 内容 | 承载节 |
|---|---|---|
| **MJ-1** | 红⑧/C10-004(iii) 绿腿谓词订正：「FAILED 终态+占位清空+done 完结+**调度链存活（他 job 触发不受影响）**+**禁自愈断言**（FAILED 为本地模式终态语义——同名 job 不自愈不重排；自愈归 C7-006/R4-07，本批禁止断言自愈）」 | C.2 决策 4（红⑧ 绿腿谓词）+ G 附红⑧ 行 |（**done 完结实测机制[MN-R2-1 评审补强]**=反射抓取 `job.running`（持有 done 引用）后 `get(timeout)`——先例 `TestLocalJobScheduler :497` readJobsMap 反射形态；抓取未竟时降级为 awaitIdle 代理并留痕）
| MN-1 | 红⑨ 构造纪律：若行为及时性腿入钉，构造形态=占满全部调度线程或 `pool-size=1` 配置注入消除多线程噪声 | G.1 决策 4（契约化形态下行为腿不采——MN-1 登记为前瞻落款） |
| MN-2 | P3 相邻全集口径 = §4.2 全集 9 条、C4-011 载体红线另列 | 零触碰边界总表（沿 plan 订正口径逐条列名） |
| MN-3 | TestBeanMethodJobInvoker = **17 用例**（2513 设计文档 §3.8 钉清单计数陈旧（plan 2513 :43 同源，live 17 亲证）[MN-R2-6 订正]，live 17 亲证） | G 附零回退钉全列 |
| MN-4 | TestJobWorkerScanner = **18 注解/16 实跑**（2 个 @Test 位于非 @Nested static inner——预先存在死测试，本批零触碰，G91 类观察候选不扩批） | G 附零回退钉全列 |
| MN-5 | 子锚漂移注记（AbstractBatchScanner scanOnce 区/RpcPollTaskManager activeCount/cancelEntry/CronExpression 构造器/owner doc 两键等 ±2-5 行） | 「plan 输入勘正」节（K1–K5 精确值落盘兑现） |
| MN-6 | 批内 main 触碰面计数口径 = **6 个 main java 文件+1 个 `_vfs` beans 资源，跨 4 子模块** | 「零触碰边界总表」触碰面口径行 |
| MN-7 | 恢复断言纪律：缺席断言必须携带实际计数（如 `assertEquals(baseline, count, "…actual=" + count)`），「应不发生」断言随 C10-009 触达改为 deadline 轮询+计数携带 | G 附红钉总表纪律行 + C10-009 触达义务行 |
| MN-8 | 2513 钉集计数存档口径：十二钉零回退钉的成员清单以其收口档为 SSOT，本批引用不复制不重算（MN-3 类陈旧计数以 live 亲读值订正） | 「既有裁定引用核对」节 + G 附零回退钉全列声明 |

## 裁定 A 读面快照域（W-01/W-03）

### A.1 W-01：getJobDetail 纳入 per-job 监视器快照（1.4-MA-02；←C1-001 主+C2-002 吸收）

**决策**：**「`getJobDetail` 锁外 `jobs.get`（CHM 原语线程安全）→ 命中后 `synchronized (job)` 内调用 `job.toJobDetail()` 并在锁内返回」唯一分支**：

1. **形态**（对齐 `getJobState` :152-158 持锁正例的既有锁纪律，一处行级包裹）：`getJobDetail`（:64-68）改为 `ScheduledJob job = jobs.get(jobName); return job == null ? null : synchronizedSnap(job);` 语义——即 `synchronized (job) { return job.toJobDetail(); }`。null 早退留在锁外（不存在的 jobName 无锁可取，零语义变化）。
2. **读面闭合映射**：`toJobDetail`（:555-566）锁内执行——`spec` 引用读取（update :546-553 可整体替换）与三个 plain long 计数（`handleResult` :408-409 / `prepareJobLocked` :331-333 持锁写方）全部进入监视器序——可见性（写方 unlock→读方 lock 建立 happens-before）与一致性（spec+计数取自同一临界区快照）两面同点闭合。
3. **其余 5 个公开读方零触碰**：`getJobNames`（CHM keySet 拷贝）/`getJobState`/`awaitState`/`awaitIdle`/`awaitFireCount` 均已持锁（audit C1-001 F3 失效面枚举 = 漏网者唯一）——零 diff 零顺带。

**理由（触发时序论证）**：缺陷时序——T1（调度线程）`synchronized(job)` 内 `handleResult` 写 `fireCount++`/`lastEndTime`（:408-409）或 `update` 整体替换 `spec`（:546-553）；T2（观测/管理线程）`getJobDetail` 经 CHM get 无同步直达 `toJobDetail`——T1 写与 T2 读之间无 happens-before（JMM），T2 可读到过期计数（execCount 回退）、错位组合（新 fireCount+旧 lastEndTime）、或旧 spec+新计数撕裂快照。post-fix：T2′ 读面与 T1 写面同监视器串行化——每读必见全部已发生写（锁内快照单调性由「fireCount 单调递增+监视器全序」保证）；`toJobDetail` 为纯读方法，锁内执行代价微秒级（与 `getJobState` 同形既有先例）。观测面缺陷本体（误导运维的过期/错位快照）闭合，写方互斥不受影响。

**红① 检出力口径（裁定落款）**：一致性 oracle 概率钉 = 写侧高频驱动（writer 线程循环 `fireNow`+`awaitIdle`，≥200 轮真实触发，每轮持锁 `fireCount++`）+ 观测线程并发循环采样 `getJobDetail`（≥1000 次，断言 `execCount` 单调不减+spec 快照非撕裂），**声明为非确定性概率腿**（JMM 过期读窗依赖时序，短窗检出概率有限）；**变异回退补强腿必做**（确定性红证主承载：scratch trial 还原无锁直读形态→钉红→复修复→绿，留痕 `_tmp/` 逐 trial 日志，原始日志存证纪律）——禁行为伪红（概率腿不红≠修复无效，红证以变异腿为准）；闩带 ≤30s 且收尾必放闸。`TestLocalJobScheduler` 15 用例与 `TestLocalJobSchedulerManagementResponsiveness` 5 用例为同文件零回退锚。

**被否决的备选**：
- **持锁快照字段后锁外构建**（`toJobDetail` 内先逐字段拷贝再构建）：结构重排面扩大（JobDetail 构建面 + 字段清单双处维护），与 `getJobState` 同构的一行级包裹已闭合两面——额外复杂度零收益。不采。
- **`spec`/计数字段 volatile 化**：写面扩散（所有持锁写点须逐一核证发布语义），且「spec+计数一致性快照」仍无保障（volatile 只保单字段可见，不保多字段同临界区快照）；缺陷面是读方纪律缺失而非字段语义。不采。
- **doc-only**（声明 getJobDetail 为弱一致接口）：MA-02 为已确认 live 缺陷（merge-adjudication §2.1 :39），且无任何既有声明该接口弱一致（audit 边界声明核对 = 无）——声明面改写为「弱一致」反而固化管理面观测缺陷。Fix 判定成立，不采 doc-only。

**零触碰边界**：其余 5 公开读方零 diff；`toJobDetail` 方法体逻辑零改写（仅其调用点持锁）；`ScheduledJob` 字段声明零 diff；2513 两阶段四产物零触碰（红线 1）；`TestLocalJobScheduler`/`ManagementResponsiveness` 钉内断言零改写（红① 为新测试类 `*R406` 追加）。

### A.2 W-03：SimpleExecutionContext 真快照收口核验 + 缝5 形式化钉（1.4-MA-04；←C1-002 主+C2-008 吸收；Proof 型核验工单——勘误 1 机制改写形态）

**核验结论（起草期 live 亲读三点核证 @ `193a8719f6`）**：2513 ctx 真快照重构（裁定② Design Review Major 修订「活引用改真快照」）**收口成立**——(a) `jobParams` 构造时防御拷贝 `new LinkedHashMap<>(params)`（:655，恰为审计 C1-002 建议修复形态「构造时浅拷贝」）；(b) 全部 state 计数器复制为 final 字段（:656-664：fireCount/lastScheduledTime/lastEndTime/min/max/maxExecutionCount/completed/internal 快照）；(c) 零 ScheduledJob/SimpleJobState/JobSpec 引用（javadoc :632-633 防回归缝声明 = 2513 裁定③ 缝5 在位）；(d) 构造点唯一（`prepareJobLocked` :334 经 `snapshotOf` :667），每触发一次快照、零跨触发共享。审计缺陷主面（跨触发 Map 共享 + live state 延迟读 JMM 竞态）**机制性消失**——W-03 为核验+防回归钉工单，非行为修复。

**决策**：**「(a) 反射断言零 live 引用字段（缝5 形式化）+ (b) params 拷贝行为钉 + (c) 嵌套 value 共享残余面降级登记（watch-only）」唯一分支**：

1. **缝5 形式化钉（红④ 结构腿）**：反射枚举 `SimpleExecutionContext` declared fields，断言字段类型集**不含** `ScheduledJob`/`SimpleJobState`/`JobSpec`（形态同构 2513 缝4 PollEntry 字段白名单反射断言）；新增 getter/字段必须同步入 2513 §3.7 快照字段清单（javadoc :632-633 同步义务）——防「快照退化回活引用」的结构防线。
2. **params 拷贝行为钉（红④ 行为腿）**：构造快照后变异 ctx 返回的 `getJobParams()` Map（put/remove），断言原始 `spec.getJobParams()` 不受影响（跨触发隔离行为面）；反向腿——第二个触发的快照不受首触发变异影响。
3. **红④ 红证形态 = 变异回退论证**（E2；勘误 1 改写形态）：**当前即绿**（2513 已收口，钉落下即绿为合法——它钉住的是收口后契约）；红证以 scratch trial 承载——还原 live 直通形态（ctx 持 `spec.getJobParams()` 活引用+live state getter 直读）→ 两腿钉红（反射断言命中 live 引用字段+params 变异穿透 spec）→ 复修复 → 绿；scratch trial 不落 repo（PA 表行 3 口径），留痕 `_tmp/` 逐 trial 日志。
4. **嵌套 value 残余面定夺 = 降级登记（watch-only）**：快照拷贝为**浅拷贝**——嵌套 value（如 params 内层 Map/List 值）在 spec 与快照间共享引用，用户 invoker 变异内层 value 可穿透回 spec。定夺理由：分布式路径对照物（`NopJobTask.getEffectiveParams`/`DefaultJobExecutionContextBuilder.resolveJobParams` 防御拷贝链）同为**浅拷贝**（audit C1-002 失效面枚举亲证）——本批追加深拷贝将使本地路径**超越**族表对照物语义（不对称反转），且审计缺陷口径（跨触发顶层 Map 共享+live state 延迟读）已闭合，嵌套 value 面为越出审计口径的深化项——**不追加最小快照面**，以缝5 javadoc 补一句「jobParams 快照为浅拷贝——嵌套可变 value 的共享为已声明边界」（E5 同批承载）登记 watch-only。**二选一分支就此关闭（选降级登记）**。

**理由**：核验工单的价值在把「2513 收口」从一次性事实固化为防回归契约——缝5 javadoc 是文本防线，反射钉是执行防线（未来有人加回 `private final SimpleJobState state` 字段即红）；params 行为钉守住「快照语义」的另一半（拷贝发生且隔离）。残余面定夺遵循族表对照物对齐纪律（不深于分布式路径）+ 诚实边界声明（不虚高）。

**被否决的备选**：
- **嵌套 value 深拷贝快照**：超越族表对照物（分布式路径浅拷贝）；深拷贝引入非结构化值（自定义 POJO/不可 clone 对象）的失败面与递归成本；越出审计缺陷口径（C1-002 失效面 = 顶层 Map+state）。不采。
- **`Collections.unmodifiableMap` 包装替代拷贝**：阻断变异但暴露 spec 底层 Map 的实时视图（后续触发添加的 params 对旧快照可见——快照语义破坏：旧触发读到新参数）；拷贝语义才是「构造时快照」。不采。
- **跳过核验钉（2513 已收口即无需钉）**：钉的本体是防回归（2513 十二钉惯例——新收口面落钉是既有纪律）；无钉则缝5 声明无执行防线。不采。

**零触碰边界**：`SimpleExecutionContext` 本体逻辑零 diff（钉落测试树；javadoc :632-633 既有声明零改写——补句归 E5 owner doc 承载非代码面）；`prepareJobLocked`/`snapshotOf` 零触碰；2513 §3.7 快照字段清单零改写（新增字段才触发清单同步义务）。

## 裁定 B raced 分支域（W-02/W-07 同分支一次重构）

### B.1 W-02：raced 分支复检失败 fall-through 重试（1.4-MA-03；←C1-005 主+C2-003 吸收）

**决策**：**「raced 分支锁内复检 `!containsKey` 失败 → `LOG.warn` 留痕 → 退出 `synchronized(raced)` → fall-through 重试一次 putIfAbsent（与 existing 分支 fall-through 重建语义对齐）；重试后 `raced2` 复检再失败（对抗性双重 remove 窗）→ 第二次 WARN 留痕后按 remove-wins 终态返回」唯一分支**：

1. **结构形态**：addJob 的「existing 分支 + 新建排程段 + raced 分支」重构为一次可 fall-through 的单循环体——raced 复检失败与 existing 分支复检失败同构（fall-through 重建）：WARN `nop.job.add-job-raced-removed:jobName={}` → 重试 putIfAbsent；重试产物 `raced2 == null` → 走既有 `raced==null` 尾段（`!active` 回滚分支 :122-128 **零触碰**+scheduleNext）；`raced2 != null` → 进 raced 分支正常处理。
2. **重试上界定夺（一次重试+remove-wins 终态，非无界循环）**：每趟重试要求一次完整「putIfAbsent→synchronized(raced)→复检」微秒级窗口内的 remove 交错，实践一至两趟内收敛；第二趟复检仍失败 = 对抗性持续 remove 场景——与 remove-wins 终态语义重合（remove 恒胜时 job 不存在是 remove 的语义结果），第二次 WARN 留痕后返回（调用方可观测信号 = WARN 日志；「静默」面消除为「留痕面」）。**不发明新错误码、不引入无界循环**（活锁风险 + 错误面扩张零对价）。
3. **WARN 文案定夺**：`nop.job.add-job-raced-removed:jobName={}`（模块内既有日志命名风格 `nop.job.*` 对齐 `remove-job-not-exists` 先例 :141）；每次重试一条，无采样抑制（管理路径低频，无洪水面——C7-012 型采样问题不在本面）。

**理由（触发时序论证）**：缺陷时序——T1 `addJob("x")`：首查 `jobs.get` null → 构造 job1 → `putIfAbsent` 返回 raced=job0（T2 刚插入）→ T1 在 job0 监视器上等待；T3 `removeJob("x")`：`jobs.remove` 成功（:139 **先移除后持锁**——锁内复检失效的根因）+ 取消调度；T1 获锁 → 复检 `!containsKey` 为真 → **静默 return**（无异常无日志无返回值信号）——job1 丢弃、job0 已移除，addJob 正常返回但注册无痕丢失，与 existing 分支同一竞态的 fall-through 重建语义（:97-98）相反。post-fix：T1′ 复检失败 → WARN → 重试 putIfAbsent → 重试产物胜出则注册重建（与 existing 分支行为归一）；raced2 胜出方为 T2 后续 update 语义（与 existing 分支语义一致）。净效果：并发 add×remove 下**不存在无痕丢失面**——每次 add 要么成功注册（build 胜出）、要么落入 raced update 语义（insert 胜出）、要么 remove 终胜且留痕（remove-wins 语义显式化）。

**红② 钉形态**：C10-007 测试腿 2（B.3）+ **变异回退补强腿必做**（还原静默 return 形态→钉红→复修复→绿——概率交错窗在 50 趟循环下命中率高但非确定，红证以变异腿确定性承载）；闩带 ≤30s。

**被否决的备选**：
- **无界循环重试**：对抗性 remove-loop 下活锁风险；每趟 WARN 洪水面；无界循环在管理 API 内不可接受。不采。
- **typed 失败/新增错误码**（复检失败抛 typed）：无贴切既有码（`ERR_JOB_ALREADY_EXISTS` 语义不符——job 恰恰不存在）；新增码 = 错误面扩张，且该终态与 remove-wins 语义重合非调用方错误；existing 分支同一场景是 fall-through 而非抛出——typed 会制造两分支新的语义不对称（缺陷修复本身变成新缺陷）。不采。
- **仅 WARN 不重试**：不闭合「与 existing 分支语义对齐」修复面（audit 建议①原文 = 「释放锁后 fall-through 重试……至少 WARN 留痕」——重试是主修复，WARN 是可观测性配套）；丢注册行为原样存活。不采。
- **putIfAbsent 换 put+锁**（重建整个注册协议）：超工单半径，CHM 原语本身非缺陷（audit 误报排除行）。不采。

**零触碰边界**：existing 分支语义零改写（其复检/fall-through 行为逐字保持——重构结构上它与新 raced fall-through 同落循环体，但语义面零变）；`raced==null` 尾段 `!active` 回滚分支（:122-128）零触碰；`removeJob`（:135-149）「先移除后持锁」次序零改写（它是竞窗根因但改为「先持锁后移除」将引入新死锁面——C2-001 裁定②锁序约束，不在本批）；C2-005 P3 面（rescheduleAfterUpdate 的 active 复检面，:262-270）零触碰禁顺带——若重构物理触及其邻接行须在 diff 分组登记中明示边界且语义零变。

### B.2 W-07：raced 分支复检通过补 allowUpdate throw（1.4-C2-004；契约锚 owner doc :93）

**决策**：**「raced 分支复检通过后、update 之前首行补 `if (!allowUpdate) throw new NopException(ERR_JOB_ALREADY_EXISTS).param(ARG_JOB_NAME, spec.getJobName())`」唯一分支（复用既有码+既有参数——零 nop-job-api diff）**：

1. **交互次序判据（与 B.1 同一次重构的次序唯一解）**：raced 分支内次序 = **复检 → 复检失败？重试（B.1） : 复检通过 → `!allowUpdate ? throw : update`**。throw 先于任何 mutation（`raced.update` :119 零执行）——抛出时本线程 putIfAbsent 已落败（返回 raced 非 null）、未改动 map，**无需回滚**（audit 修复建议原文）；「先判 throw 后判重建」即：重建只属于复检失败路径，throw 只属于复检通过+契约拒绝路径，两者互斥无交叠窗。
2. **契约兑现**：owner doc :93「false = 已存在则抛 already-exists」在并发首注册窗口从「入口快照命中才生效」扩展为全路径不变式——`allowUpdate=false` 的 fail-fast 在 raced 分支与 existing 分支语义归一。
3. **`:471` 顺序契约钉零改写落款**：`testAddDuplicateJobThrowsAlreadyExists`（:471，顺序两次 addJob 第二次抛 already-exists——只经入口 existing 分支）零改写零移动；修复后顺序路径行为不变（入口命中路径先行于 raced 分支）；C10-007 并发测试**叠加非替代**（audit 误报排除行 + merge-adjudication §4.4 :178 口径）。

**理由（触发时序论证）**：缺陷时序——T1/T2 并发 `addJob(specA/B, false)` 双双通过入口 `jobs.get` null 检查 → T1 `putIfAbsent` 胜出注册 specA → T2 `putIfAbsent` 返回 raced=jobA → T2 复检通过（job 存在）→ **无条件 `raced.update(specB, ...)`**（:119）静默改写对方刚注册的 spec/invoker（`update` :546-553 还因 `running==null` 替换 invoker）——`allowUpdate=false` 契约被并发窗口绕过，错误配置（本应注册期暴露）推迟到触发期显形。post-fix：T2′ 复检通过后命中 `!allowUpdate` → typed `ERR_JOB_ALREADY_EXISTS`（与入口路径同码同参同语义）→ 静默改写面闭合，双首注册恰一方胜出。并发面：throw 在 `synchronized(raced)` 内，与并发 update/remove 互斥，判定原子。

**红⑦ 钉形态**：C10-007 测试腿 1（B.3，atMostOneWinner 断言恰一方抛 already-exists）+ 变异回退补强腿（还原无 throw 形态→钉红→复修复→绿）；`:471` 顺序钉并列绿锚。

**被否决的备选**：
- **复检前先判 allowUpdate**（把 throw 提到复检之前）：复检失败（job 已被 remove）时本线程的 spec 本可重建成功——提前 throw 会把「可重建窗口」误判为契约冲突（与 existing 分支 fall-through 语义冲突，且制造假阳性 already-exists）。次序唯一解 = 复检先行。不采。
- **typed 拒绝改 WARN 返回**：owner doc :93 明文契约「抛 already-exists」——W-07 是契约实现违背（audit 边界声明核对行），WARN 不兑现契约。不采。
- **update 内部守卫**（`ScheduledJob.update` 增 allowUpdate 参数）：把调用方契约下沉到被调方签名（污染内部 API 面），且 :471 顺序路径语义耦合；分支内 throw 是契约判定的自然位置。不采。

**零触碰边界**：existing 分支 allowUpdate fail-fast（:91-93）零 diff；`ScheduledJob.update`（:546-553）签名与逻辑零改写；`ERR_JOB_ALREADY_EXISTS`/`ARG_JOB_NAME` 既有定义零 diff（nop-job-api 零触碰——条件申报①零触发落款）；`:471` 顺序钉零改写；C2-005 P3 面零触碰（同 B.1）。

### B.3 C10-007：一套并发注册测试设计（验收前置同 diff——merge-adjudication §4.4 :178「一套并发注册测试同盖同分支两族」）

**决策**：**「`TestLocalJobSchedulerConcurrentRegistration`（新测试类，`*R406` 后缀+类注释归属 CRA 8.46 R4-06）——两腿概率主形态 + 变异回退补强腿确定性红证，不采生产 seam」唯一分支**：

1. **腿 1（红⑦ 载体）atMostOneWinner**：双线程 latch 同步起跑各 `addJob(specA/B, false)` → 断言恰一方成功返回、另一方抛 `ERR_JOB_ALREADY_EXISTS`（typed 实测+detail 判别）；**循环 N=50 趟**（putIfAbsent→复检窗口毫秒级，50 趟对毫秒级管理路径命中率高）。
2. **腿 2（红② 载体）add×remove 交错一致性**：三线程交错循环（add×remove×add，50 趟）后断言 `getJobNames()`（:60-62 CHM keySet 拷贝）与注册/移除调用的净结果一致 + 最终态无幽灵注册；WARN 留痕面定性核对（日志存在性不作硬断言——logback 捕获设施在 nop-job 测试面未建，2513 先例无日志断言基建，禁造空壳断言）。
3. **检出力口径定夺（概率 vs seam 一次裁决）**：**概率形态为主（50 趟 × 两腿）+ 变异回退补强腿必做**（两腿各自还原缺陷形态→钉红→复修复→绿——确定性红证主承载，破概率不红即伪绿的口子）；**包内 seam 注入（putIfAbsent 时序钩子）不采**——seam = 生产类结构改动（为测试在生产路径加钩点，零生产可见性优先纪律；2512/2513 先例的包内缝形态均为「既有可见性放宽」而非新增生产钩子），检出力由概率趟数+变异腿承载已足（诚实登记：概率腿非确定性——变异腿补确定性）。
4. **同 diff 绑定落款**：C10-007 与 W-02+W-07 同 diff（一套测试两腿同盖两族——禁拆两套禁重复计数，对齐纪律第一条 + inventory-p2-c10 :771 SSOT）；以 `:471` 顺序契约钉为基线叠加非替代。

**理由**：merge-adjudication §4.4 :178 原文「概率性形态建议包内 seam——一套并发注册测试同盖同分支两族」给出的是建议形态；裁定在「概率+变异腿」与「概率+seam」之间取前者——变异腿是 E2 纪律内建要求（本批全部红钉通用），seam 是额外生产结构成本且其确定性收益已被变异腿覆盖。两腿各自独立红证（W-02 与 W-07 修复互不覆盖——merge-adjudication :53 分立裁定，单腿红不足以证另一族）。

**零触碰边界**：`:471` 顺序钉零改写；生产类零 seam 钩点；`TestLocalJobScheduler` 既有 15 用例零改写（新测试类追加）。

## 裁定 C Error 兜底域（W-08 残余面；C10-004 同 diff）

### C.1 W-08：双层 catch 收宽 + settleExecution 既有通道复用（1.4-C2-007；←C2-round2 :11-74）

**决策**：**「`startJobUnlocked` catch `Exception`→`Throwable`（:355 一行宽度）+ `BeanMethodJobInvoker.invokeReflective` catch `Exception`→`Throwable`（:113-119 一行宽度）——Error 统一经既有 settleExecution fail-safe 通道落 FAILED 终态」唯一分支（双层互补收宽，均为审计修复建议②③原文）**：

1. **调度器层（:355）**：`catch (Exception e)` → `catch (Throwable e)`，catch 体不变（`settleExecution(job, null, e, done)`）——invokeAsync **同步抛 Error**（NoClassDefFoundError/OOM/自定义 Error）时与 Exception 同路：重取锁 → `job.running = null`（:376 占位清空）→ `handleResult` err 分支 FAILED 终态（:411-414）→ `done.completeExceptionally`（:385-388）→ 2496 MA-05 站点③ fail-safe（settleExecution 内 catch Throwable 兜底，:378-384）保持覆盖。
2. **invoker 层（BeanMethodJobInvoker :113-119）**：`catch (Exception e)` → `catch (Throwable e)`，catch 体不变（`CompletableFuture.failedFuture(e)`）——bean 方法体内 Error 转入既有异步失败通道（failedFuture → whenComplete err 路径 → settleExecution FAILED），不再穿透到 startJobUnlocked 锁外段。
3. **双层关系论证（互补非冗余）**：invoker 层收窄 Error 的逃逸面至「invokeAsync 调用本身抛 Error」（如类解析/OOM）；调度器层是**全部 invoker 实现**（公开 SPI——自定义 invoker 同步抛 Error）的兜底——单层修复都留另一族 Error 逃逸面。审计修复建议②③原文即双层。
4. **2513 四产物零回退落款（红线 1 的设计证明）**：catch 收宽为 catch 子句宽度单点改动，四判据在修复下逐条保持——①占位先行：`prepareJobLocked` :335 置位先于 :354 invokeAsync，catch 收宽不改时序；②settleExecution 重取锁终态迁移：catch 体调用的就是该通道本体；③出锁段 identity 复检+放弃分支：:347-349 在 catch 之前执行，Error 路径经复检（被交错放弃的 job 走 abandonAfterInterleave 而非 FAILED——语义正确：放弃优先）；④fail-safe（:378-384）对 catch 收宽后新增的 Error 路径同样兜底（handleResult 自身抛 Throwable 时）。
5. **FAILED 终态语义声明（MJ-1 谓词的语义基础）**：FAILED 为本地模式终态——`scheduleNext` 不重排（`handleResult` err 分支 return 前不调 scheduleNext）、`fireNow` 恒拒绝（:223 FAILED 检查）、**不自愈不复活**；绝缘层（调度链存活）与 job 终态正交（owner doc §六层级区分）：调度链继续服务**其他** job 与后续 fireNow/manual 管理面，本 FAILED job 不自愈（自愈归 C7-006/R4-07——本批禁代做禁断言）。

**理由（触发时序论证）**：残余缺陷时序——T1 timer 回调（:308-320）锁内 `prepareJobLocked`（占位 :335 置位）→ 锁外 `startJobUnlocked` identity 复检通过 → `invokeAsync` **同步抛 Error** → `catch (Exception)` 不匹配 → Error 逃逸锁外段 → 2496 绝缘层（executor 设施边界）捕获保住调度链，但该 job 无 settleExecution 调用：`internal==RUNNING`（:331 已置位）+ `running` 占位悬空（:335 置位后无人清理）+ `scheduledFire==null`（timer 回调 :313 已清）→ 该 job 周期调度静默停止、`awaitIdle` :495 永不空闲、`fireNow` :221 恒拒绝、无 FAILED 终态、模块内零日志（ExecutorHelper 周期任务 catch Throwable→通用日志（live :204-205 区[MN-R2-5 订正——平台文件锚以 live 为准]） 平台层无 jobName 通用日志不改变终态）——静默永久停摆。post-fix：Error 与 Exception 同路落 FAILED——**三判据（fireNow 重入拒绝/awaitIdle 正确等待/update 守卫正确保持）由 2513 占位前移闭合的面零回退**（占位悬空面消除后 awaitIdle 从「永不空闲」恢复「正确等待」语义）；模块内 ERROR 日志经 `handleResult` :412 既有 `nop.job.execute-failed` 通道留痕。

**C10-004 三锚点测试化设计**：见 C.2。

**被否决的备选**：
- **finally 恢复态**（catch 保持 Exception + finally 段统一清占位/置 FAILED）：需要状态判别（正常完成路径不得误置 FAILED）——finally 内再判 internal/running 组合 = 第二状态机，复杂度高于一行 catch 宽度；且 invoker 层的 failedFuture 形态无 finally 对应物（双层不对称）。不采。
- **仅调度器层收宽**：自定义 invoker 的 Error 在其 invokeAsync 内部即转同步逃逸面，与 bean-method Error 同族——invoker 层不修则 `TestBeanMethodJobInvoker` 17 用例（MN-3 口径）钉住的失败通道对 Error 保持不闭合。不采。
- **仅 invoker 层收宽**：自定义 SPI invoker 的 Error 逃逸面原样存活（调度器层是 SPI 兜底位）。不采。
- **catch Throwable + 重排调度**（Error 后走 scheduleNext 重试）：FAILED 终态语义破坏——Error 类失败按现有 handleResult err 语义为终态（本地模式无自愈，C7-006 归 R4-07）；重排=把终态决定权从 C7-006 承接面抢走。不采。

**零触碰边界**：2513 两阶段四产物（红线 1）零改写；`settleExecution`/`abandonAfterInterleave`/`handleResult` 本体零 diff（catch 宽度是唯一改动点×2）；`cancelRunning` 空语义（:449-452）零触碰；plan 340 钉（TestJobTimeoutChecker :614-639）零触碰；`TestBeanMethodJobInvoker` 17 用例零回退（MN-3 口径）；`TestLocalJobScheduler` :497 fail-safe 锚（2496 站点③）零改写；C4-010「补 cancelToken 无效」结论引用不重开（cancelToken 通道维持内建装配空路径兜底，Error 修复不扩权 token 通道）。

### C.2 C10-004 三锚点测试化设计（验收前置同 diff——红⑧ 本体即 C10-004 同 diff 钉）

**决策**：**「三锚点落 `TestLocalJobSchedulerReentrancy`（新，`*R406`）——(i)(ii) 回归锚当前即绿 + (iii) Error 注入腿先红后绿」唯一分支**：

1. **锚 (i) 自重入拒绝回归锚（当前即绿——2513 已收口面的防回归锚）**：同步 invoker 方法体内调 `fireNow(同名)`（AtomicBoolean 防御真实业务递归深度）→ 断言不递归不 SOE、fireNow 返回 false（:221 占位判据）、后续周期继续触发（:142 型哨兵改为 deadline 轮询——C10-009 触达纪律）。
2. **锚 (ii) 执行期 RUNNING 可见回归锚（当前即绿）**：invoker 内读 `getJobState`/`awaitIdle` → 断言执行期 RUNNING 可见/awaitIdle 正确等待（:335 占位先行 + :495 判据的回归锚）。
3. **锚 (iii) Error 注入腿（本批先红后绿钉 = 红⑧ 本体）**：**注入形态定夺 = 测试内自定义受控 Error**（`static final class TestInjectedError extends Error`）由 stub invoker 的 invokeAsync **同步抛出**——确定性、零栈污染、零递归构造；**StackOverflowError 受控构造不采**（深递归栈污染测试 JVM+深度阈值非确定+与锚 (i) 的递归防御面耦合）。pre-fix 断言（红）：job 卡 RUNNING（`getJobState==RUNNING`）、`awaitIdle` 超时不空闲（同步 `get(timeout)` 捕 TimeoutException——orTimeout 禁令）、无 FAILED、fireCount 停滞（无重排）、他 job 不受扰（对照 job 周期继续——MJ-1 调度链存活面的红侧基线）。post-fix 绿腿谓词（**MJ-1 订正落款**）：`getJobState==FAILED` 终态 + ERROR 日志通道存在（定性——`nop.job.execute-failed` 既有通道）+ 占位清空（`awaitIdle` 可空闲）+ `done` 完结（completeExceptionally 实测）+ **调度链存活（独立对照 job 周期触发不受影响）** + **禁自愈断言（该 FAILED job fireCount 恒定、fireNow 恒返回 false——自愈归 C7-006/R4-07，断言自愈即伪绿）**。Exception 后占位清空腿（`settleExecution` :376 路径——C10-004 原文「占位已置但 invokeAsync 抛异常」新窗口）随钉并列。
4. **两调用点处置表**：

| 调用点 | 现状（live） | 处置 |
|---|---|---|
| timer 回调 :308-320（:318 startJobUnlocked） | 锁内 prepareJobLocked→出锁 startJobUnlocked | **零 per-callsite 改动**——catch 收宽在 startJobUnlocked 体内，timer 路径自动覆盖；Error 注入断言经 timer 驱动腿覆盖 |
| fireNow :209-230（:228 startJobUnlocked） | 同形两阶段 | 零 per-callsite 改动——同上；(iii) 腿可选 fireNow 驱动变体（同断言面） |

**零触碰边界**：C10-004 钉与 W-08 同 diff（禁拆分——对齐纪律第八条）；(i)(ii) 锚内断言零改写义务自落钉起生效（成为 2513 收口面回归锚）；`:497` fail-safe 锚与 plan 340 钉零触碰。

## 裁定 D CronExpression ThreadLocal 域（W-04；1.4-MA-07；←C5-003 主+C1-006 吸收）

**决策**：**「`threadLocalCal` 实例字段与两构造器 withInitial 初始化全删除；`getTimeAfter` 首行栈局部构造 `GregorianCalendar`（逐字复刻公开构造器 initializer 形态：`new GregorianCalendar()` + `setTimeZone(this.timeZone)`）」唯一分支（merge-adjudication :44 族裁定「C5-003 方案为准」，C1-006「维持 vendored 不动」立场被族裁定覆盖留档）**：

1. **字段删除 = 双构造器同修**（`isValidExpression` 同修的自然形态）：公开构造器 :130-138（withInitial :133）与私有构造器 :146-156（withInitial :152）的初始化随字段删除一并移除——私有构造器仅为 `isValidExpression`（:625，体内 :634 调用）服务，parse-only 路径**不消费 calendar**（其 withInitial 为纯废弃分配），删除零行为变化。
2. **timeZone capture 论证**：栈局部 calendar 必须携带实例 timeZone——从 `this.timeZone`（final 字段）构造天然捕获，零共享可变态；逐字复刻公开构造器 initializer 形态（`new GregorianCalendar()`+`setTimeZone`）保证 lenient 语义与 live 公开路径一致（公开路径 initializer 不调 setLenient——默认 lenient；私有构造器 initializer 的 `setLenient(false)` 差异面不进入本修，因其 calendar 从未被消费，K2 亲证）。
3. **语义等价性证明**：live `getTimeAfter`（:178 起，`threadLocalCal.get()` ~:194）内 calendar 为**单次调用内纯 scratch**——入口 `calendar.setTimeInMillis(date)` 全量覆写（零跨调用状态依赖）→ `doNext` 就地推进 → 返回计算值；ThreadLocal 换栈局部后每次调用语义逐字等价，唯一变化 = 分配点从「每线程首访」移至「每调用」（young-gen 短命对象，churn 面由验收口径承载）。
4. **CronCalendar 关联面核对结论（闭合）**：`CronCalendar`（:37 持有/:72 构造）经 `isSatisfiedBy`（:94-98 → `cronExpression.getTimeAfter`）消费——路径 b（长 lifecycle 持有）在栈局部形态下**驻留归零**（每次计算栈内完成，零 ThreadLocal 残留），与路径 a（planner 每周期重建 churn）同点闭合；calendar 族功能绿腿覆盖其回归面。**路径 b 不需追加处置**——二选一分支就此关闭。
5. **churn 验收口径成文**：按 merge-adjudication :44「反模式消除+churn 断言」——验收 = 结构钉（ThreadLocal 字段消除）+ 功能绿腿全绿；**不做**无界增长断言（ThreadLocalMap 惰性 expunge 使残留本不单调，无界断言不可构造——audit 判级勘误行口径）。
6. **结构钉（红③）设计**：反射断言 `CronExpression` declared fields 不含任何 ThreadLocal 类型字段（pre-fix 字段在=结构红确定性；post-fix 消除=绿——2537 红⑥ 结构钉先例形态）+ **功能绿腿清单**：`TestCronExpression`（3 用例）+ `TestCronGoldenVectors` + `TestJobTriggerCalculator` + calendar 族测试全绿（timezone/lenient 边界覆盖义务）；结构钉诚实登记为结构腿（无行为红——禁伪绿即禁谎称行为红）。

**理由（触发时序论证）**：非竞态缺陷——风险形态是池化线程 ThreadLocalMap 残留与分配 churn：T1 planner 每 due cron schedule 每 scanBatch 经 `TriggerBuilder.buildTrigger`（:33 `new CronExpression(spec.getCronExpr())`——零漂移亲证）→ 每实例内嵌新 ThreadLocal → 长寿命扫描线程（globalTimer→globalWorker）ThreadLocalMap 遗留 stale entry（key 弱引用/value 强引用）直至启发式 expunge；量级 batchSize(100)×maxScanLoops(1000)×每周期。post-fix：零实例级 ThreadLocal——无残留面、无 expunge 依赖；时间复杂度面（每调用一次 Calendar 分配）与原「每线程首次 + 每实例首次」同量级（planner 重建形态下原形态每实例每线程首次即每调用一次——**原形态在 planner 路径的分配频度已等价于每调用**，栈局部化在该路径零分配增量；CronCalendar 路径从「每线程 1 个驻留」变「每调用 1 个短命」——驻留换 churn，有界换零残留，审计验收口径（反模式消除）指向后者）。

**被否决的备选**：
- **维持 ThreadLocal + finally remove**：ThreadLocal 生命周期错配本体（每实例 ThreadLocal 无对应清理点——planner 重建形态下实例即弃，remove 无落点）；与「反模式消除」验收口径相悖。不采。
- **类级共享 ThreadLocal（静态）+ remove**：跨实例共享 ThreadLocal 承载 per-instance timeZone——set 时须覆盖、并发同线程交错计算互相踩踏（非线程安全推进）；静态全局可变态引入新风险面。不采。
- **Calendar.clone 模板复用**：与栈局部构造成本同量级，多一套模板字段维护——零收益。不采。
- **维持 vendored 不动（C1-006 立场）**：merge-adjudication §2.1 :44 族裁定明文覆盖（「该 vendored 类已有 `72b770b094` 修改先例，C1-006 立场被族裁定覆盖，仅作修复阶段备选」）——本批不重开族裁定体裁。不采。

**零触碰边界**：cron 解析算法（parse/doNext 表驱动）零 diff（栈局部化只触 calendar 获取点与构造器初始化面）；`72b770b094` 先例改动面（私有构造器不变式+isValidExpression 语义）零回退；`TriggerBuilder`/`JobTriggerCalculator` 调用面零 diff；功能绿腿测试零改写（红③ 为新测试类追加）。

## 裁定 E scanOnce 在途守卫域（W-05；1.4-C1-003）

**决策**：**「`scanOnce` 入口 `AtomicBoolean scanningInFlight` CAS 守卫——CAS 失败 → `LOG.warn` + 冲突计数 + 正常返回；CAS 成功 → 既有周期体全量执行 + finally 释放」唯一分支（三选一裁断：CAS 守卫）**：

1. **守卫形态**：`private final AtomicBoolean scanningInFlight = new AtomicBoolean(false);` 入口 CAS：失败路径 = `LOG.warn("nop.job.scan-cycle-conflict:scanner={}", getClass().getSimpleName())` + `scanCycleConflictCount.incrementAndGet()`（新 AtomicLong 计数，镜像 2496 计数器家族 :63-68 形态，getter `getScanCycleConflictCount()` 同款）+ return；成功路径 = `try { ...既有周期体逐字保持... } finally { scanningInFlight.set(false); }`。
2. **拒绝形态定夺（WARN+计数 vs typed 拒绝）**：**WARN+计数，typed 不采**——typed `NopException` 会经 `doScan` 逃逸至 2496 绝缘设施边界，被记为**任务体失败**（设施级失败观测+重臂）——拒绝≠失败，把守卫拒绝混入设施失败流将污染「链健康」观测语义（误导运维：拒绝事件被读作周期故障）；WARN+计数 = 显式可观测（日志+计数 getter，rule 24 无静默——跳过有痕有数）且设施失败流零污染。**零新错误码**（拒绝面无错误码——条件申报①零触发旁证）。
3. **计数边界声明**：CAS 失败路径在周期体 try 之外——`scanCycleCount`/`scanFailureCount`/`lastScanStart` 等既有计数（:119-121/:129-133）**不被拒绝触达**（计数只反映真实周期）；冲突计数独立承载拒绝面。
4. **守卫与 2496 绝缘边界的关系（红线 3 零弱化落款——失败语义不变证明）**：守卫位于绝缘边界**之内**——`Throwable` 边界（:129-133）、`onScanCycleFailed`（:184-186）、计数器（:119-121）、`startScanning` 拒绝真实化（:80-90）逐字保持；守卫 finally 释放不改变任何 Throwable 失败语义（周期体抛 Throwable → 既有 catch → onScanCycleFailed → finally 释放 → 返回——链由设施重臂，语义与 pre-fix 唯一差异 = 在途互斥）；**守卫拒绝 ≠ 链失败**（被拒 entrant 不触 running/scanFuture/链状态任何字段）。
5. **释放时序论证（plan 风险段点名面）**：`finally` 释放覆盖全路径（周期体 Throwable 被既有 catch 全捕获——finally 必达；无早退分支遗漏）；timer tick 与手工触发合法序列行为：手工 scanOnce 在途时到点 tick 被拒（WARN+计数+返回）→ 本轮跳过、cursor 零扰动（被拒者不执行 `c.reset()` :123-125——cursor 连续性保持）→ 下轮 fixed-delay tick 正常继续——**跳过一轮的代价 = 该轮数据由下轮 cursor 扫描覆盖（cursor 语义天然支持）**，非数据丢失面。
6. **6 子类覆盖声明**：守卫落基类唯一入口 `scanOnce`（:118）——`JobPlannerScannerImpl`/`JobDispatcherScannerImpl`/`JobCompletionProcessorImpl`/`JobTimeoutCheckerImpl`/`JobScheduleCounterReconciler`/`JobWorkerScannerImpl` 六实例自动全覆盖（基类 1 处 × 6，audit F3 失效面枚举口径）；子类零 diff。
7. **红⑤ latch 交错红钉设计**：测试子类 `scanBatch` 内 latch 阻塞在途轮次 → 外部线程并发调 `scanOnce` → pre-fix：外部 reset 清零在途 cursor（重复处理/撕裂可观测断言——fetch 计数膨胀断言）= **当前即红[确定性——latch 交错可构造]**；post-fix：外部调用 WARN+冲突计数+返回，在途轮次不受扰（其 cursor 连续推进、fetch 计数无膨胀）= 绿；`TestAbstractBatchScanner` 9 用例（2496 绝缘钉）零回退。

**理由（触发时序论证）**：缺陷时序——T1（timer→globalWorker 线程）`doScan→scanOnce` 正在 `drainBatch` 内 `cursor.advance`（先写 time 后写 id 非原子——Cursor :368-371 亲证）；T2（lifecycle/测试调用方）并发公开 `scanOnce` → 入口 `c.reset()`（:123-125）清零 T1 在途 cursor → T1 下轮 drainBatch 以归零 cursor 重新 fetch 已处理窗口 → 同批记录二次处理（重复告警/重试事件）；反向交错（T2 reset 后与 T1 advance 并发）产 `(新time,旧id)` 撕裂 cursor → 静默跳批。声明的「单线程」契约（scheduleWithFixedDelay 调度语义 + Cursor javadoc「非线程安全」）对 public 入口的外部调用零运行时防线——2496 只加了 Throwable 边界与计数（并发守卫未加）。post-fix：T2′ CAS 失败即时返回——reset 零执行，T1 在途周期与 cursor 完整性受保护；「同一时刻至多一个 scanOnce」从声明契约变为运行时机制。

**被否决的备选**：
- **protected 降级+带守卫公开包装**：违背 :108-110 javadoc 明示的公开理由（「避免历史子类为同包可见性提升写空壳 override 的设计 hack」——可见性回退=重开已关闭的设计决定）；且 protected 对同包调用方仍公开，守卫面不收敛。不采。
- **实例锁 synchronized**：串行化而非拒绝——并发违例被静默吸收（调用方无从知晓），锁横跨 scanBatches 全周期（秒级临界区）阻塞 timer 线程——劣于 WARN 即返。不采。
- **typed 拒绝（NopException）**：绝缘设施误计分析（见决策 2）——拒绝混入设施失败流。不采。
- **CAS 自旋等待在途完成**：waiting-loser 形态——需等待上界与 interrupt 响应性声明（2538 S-2 纪律），且阻塞调用线程收益为零（拒绝返回已安全）。不采。

**零触碰边界**：2496 绝缘产物全集（红线 3）零弱化；C2-006 P3 面（`newCursor` 无 fail-fast :170-174 / `registeredCursors` 迭代守卫）零触碰禁顺带；既有计数器字段与 getter（:63-68/:191-203）零改写（冲突计数为**新增**字段非改写）；`drainBatch`/`Cursor` 本体零 diff；`TestAbstractBatchScanner` 9 用例零改写（红⑤ 为新测试类追加）。

## 裁定 F register 回滚域（W-06；1.4-C1-004）

**决策**：**「`register` 体（:161 put 之后全部步骤）包 try/catch(Throwable)——异常路径回滚：`cancelEntry(entry)`（幂等全链）→ `future.complete(JobFireResult.ERROR(error(ERR_JOB_REMOTE_INVOKE_FAILED)))` → `LOG.warn` 留痕」唯一分支（单层回滚，RemoteJobInvoker 零 diff）**：

1. **回滚通道复用 cancelEntry 的时序保序论证**：`cancelEntry`（:202-215）为幂等清理全链——句柄 `handle.cancel(false)`（未设置时 null 守卫跳过）+ 守卫式 `removeOnCancel`（未挂载时 no-op）+ `entries.remove(taskId, entry)` **两参条件移除**（不误删同名新条目——K3 亲证）。回滚顺序 = 先 `cancelEntry`（摘监听+摘条目——**先摘监听后 complete future** 保证无 `onCancelToken` 迟到回调干扰已回滚条目）再 `future.complete(ERROR)`（complete 触发的是 caller 侧后续链条；register 内 `future.whenComplete→cancelEntry` 尚未注册——回滚自身负责清理，complete 不触发二次 cancelEntry）。
2. **稳态路径零回退落款**：回滚分支仅存在于 catch——稳态次序 `put → appendOnCancel → scheduleWithFixedDelay → setPollHandle → whenComplete → isDone 双检`（:161-179）逐字保持，「先 put 后调度」既有次序零重排（2513 裁定① 落款边界内）；`:177-179` isDone 双检零触碰（其覆盖「future 先完成」方向与回滚分支正交）。
3. **错误码零新增落款**：`future.complete(JobFireResult.ERROR(error(ERR_JOB_REMOTE_INVOKE_FAILED)))`——复用 2513 裁定①(a) 同码（失败预算 resolve 同码先例）；`error()` helper 为 manager 既有设施（onCancelToken :225 先例）。
4. **2513 五面零触碰落款（红线 2）**：失败预算/deadline/DB 回传（`resolveFromAuthority` :192-200）/future whenComplete 兜底+PollEntry 白名单+gauge 五面零改写零重开；catch 回滚不触 `pollOnce`/`applyStatus`/`computeDeadline`。
5. **DB 回传缓解面登记**：2513 落地的 `resolveFromAuthority` 已给「已入表条目」的 promise 外部完成通道（checker 置 TIMEOUT 时），但条目/监听器泄漏与零日志面在其外（audit 现状行口径）——本回滚闭合该残余面；**回滚后语义一致性落款**：已回滚移除的条目若迟到收到 `resolveFromAuthority` → `entries.get` 未命中 → 合法 no-op（其 promise 已由回滚 complete(ERROR)——first-writer-wins 幂等，无双终态）。
6. **REE 测试缝形态（裁定落款）**：`setScheduledExecutor(stub)` 注入拒绝型 `IScheduledExecutor`（`scheduleWithFixedDelay` 抛 `RejectedExecutionException`——REE 抛出点直击，零生产改动）；装配路径复用 `TestRpcPollTaskManagerEntryTermination` 既有 `new RpcPollTaskManager()`+setter 模式（:56 区）与 2513 mock 体系。
7. **红⑥ 确定性红钉设计**：stub 注入 → register → pre-fix 断言：future 永不完成（同步 `get(timeout)` 捕 TimeoutException——orTimeout 禁令）+ `activeCount()>0`（条目滞留 :183-185 可观测）；post-fix 断言：future 完成 ERROR（`ERR_JOB_REMOTE_INVOKE_FAILED` 判别）+ `activeCount()==0` + WARN 留痕（定性）；**当前即红[确定性——REE 注入必达]**；`TestRemoteJobInvoker` :254 cancelToken 监听器摘除锚 + `TestRpcPollTaskManagerEntryTermination` 9 用例零回退。
8. **同组件 P3 零触碰**：C4-008（manager 无 stop 生命周期）/C4-010（cancelToken 恒 null——前置约束引用不重开）/C7-008（rpcPoll 墙钟——2513 注记时钟源结论零代做）零触碰。

**理由（触发时序论证）**：缺陷时序——T1（RPC 回调线程）`RemoteJobInvoker.startJob` 的 `whenComplete` **动作**内（:111 register 调用）→ `entries.put` 成功、`appendOnCancel` 成功 → `scheduleWithFixedDelay` 在停机窗口（executor 已 shutdown）抛 REE → 异常发生在 whenComplete 动作内 → future 框架吞掉（衍生 future 无人消费）→ 无 catch 无日志无处传播：条目滞留（无 whenComplete→无 cancelEntry→永不移除）、cancel 监听器滞留 token、promise 永挂、`handleExecutionResult` 永不触发、task 行滞留 RUNNING、全程零日志（仅停机窗口可达——P2 判级自洽）。post-fix：T1′ catch → 回滚（条目移除+监听器摘除）→ promise complete(ERROR) → task 行按失败终态走既有终结链；whenComplete 动作内异常不再逃逸（register 自身消化）——**异常吞没点（RemoteJobInvoker :111 动作）因 register 零抛出而结构性失效**，无需跨类兜底。

**被否决的备选**：
- **RemoteJobInvoker 动作内 try/catch 兜底 complete（候选 b）**：只闭合 promise 挂起——条目/监听器泄漏不闭合（complete 无法触发 register 内未注册的 whenComplete→cancelEntry 链）；跨类 diff 面扩张。不采。
- **双层（register 回滚+invoker 兜底）**：register 回滚已使动作零抛出——第二层零增量零触发；跨类 diff 纯成本。不采（二选一裁断：单层回滚）。
- **回滚改「先调度后 put」**（重排消除泄漏面）：违反「先 put 后调度」稳态保序落款（2513 裁定① 预声明形态）——重排后 pollOnce 首轮可能在条目入表前读不到（时序窗）。不采。
- **回滚日志用 ERROR**：plan 工单表与 Phase 1 checklist 预设「WARN/error 留痕」中 **WARN 留痕**形态（裁定落款遵预设；REJECTED 挂起面已由 future ERROR 终态承载显式信号，WARN 通道与 `remove-cancel-listener-failed` 先例 :212 同风格）。不采 ERROR 通道。

**零触碰边界**：红线 2 五面+PollEntry 白名单+gauge 零触碰；`RemoteJobInvoker` 零 diff（回滚后动作零抛出，旁证文件保持零漂移）；`cancelEntry`/`onCancelToken` 本体零改写（只复用）；`computeDeadline`/`pollOnce` 零触碰；plan 340 checker 钉零触碰；C4-008/C4-010/C7-008 零触碰；`TestRpcPollTaskManagerEntryTermination`/`TestRpcPollTaskManagerInsulation`/`TestRemoteJobInvoker` 既有用例零改写（红⑥ 为新测试追加）。

## 裁定 G 载体与包络域（W-09/W-10 同域）

### G.1 W-09：触发/执行关系契约化声明闭合（1.4-C4-002）

**决策**：**「契约化声明形态唯一分支——本地模式 job 方法体的非阻塞契约为显式 SPI/使用契约（owner doc E5 三处落点）+ 结构文档钉，不引入触发/执行分离」**：

1. **契约落点三处（E5 同批承载，Phase 3 落账）**：(a) owner doc §1.4 :214「注意：阻塞型 invoker 仍占用调度线程（本地池隔离属后续批次面，此处不声明已修）」句**如实化兑现**——改为显式契约句「本地调度器的触发与执行共用 `nopJobScheduledExecutor` 调度池：**job 方法体必须非阻塞**（毫秒级内存操作或立即返回 CompletionStage 的真异步）；阻塞/长任务体将占住调度线程、拖延全部作业触发——长任务须自行转真异步（CompletionStage 返回）或改用分布式 worker 模式」（**该句兑现即本工单修复本体——「不声明已修」承诺句由契约句取代，禁保留「属后续批次面」措辞**）；(b) owner doc §3.1 :331 异步性契约 blockquote 的「建议自行真异步化」**升格为硬性契约句**（「invokeAsync 的实现要么真异步（快速返回未完成 CompletionStage）要么方法体非阻塞——同步阻塞方法体违反本地模式触发及时性契约」）；(c) §1.4 :212 单机嵌入式定位句相邻处补「阻塞 job 场景请改用分布式模式」指引句。
2. **契约化而非分离的理由（触发时序+架构对齐论证）**：分离形态（timer 回调只做状态迁移+转投执行池）的两个落点均不成立——**globalWorker 转投**：job 业务体=时长无上界任务体，转投后成为全平台共享池的新共租占位方（30 线程/10000 队列），与 2498 §3.1.1「长任务体禁入共享调度槽」契约方向相反，且与 W-10 的收敛方向矛盾（本批一边在 worker 侧声明收敛共租、一边在 local 侧新造共租 = 自相矛盾）；**模块专用执行载体**：新增 bean 定义 = 条件申报②**预设禁选**（plan PA 段明文——优先 @InjectValue/程序化），程序化构造则引入载体生命周期归属/关闭顺序/热更契约（§七 容量与热更语义扩展）全套成本，与本地模式「单机嵌入式、轻量」产品定位（owner doc §一）不成比例；**契约化与审计建议 b 原文一致**（「文档显式声明 job 方法必须非阻塞……至少应在文档 §1.4 补充该约束」）且与 owner doc :214 既有「此处不声明已修」句自然衔接——契约句如实化是该承诺句的兑现落点（E5 义务的裁定侧落款）。
3. **契约的现实性核证（声明不是虚文）**：非阻塞契约在分布式模式已是既成现实（worker 侧 invokeAsync 为公开 SPI、`JobWorkerScannerImpl` :341 在 globalWorker 线程触发——W-10 同面）；内置 invoker 全集非阻塞（audit C4-002 残留边界行：「invoker 返回真实异步 CompletionStage 时调度线程即时释放」）；阻塞面唯一来源 = `BeanMethodJobInvoker` 同步 bean 方法（owner doc §3.1 已声明其同步形态）——契约把「事实约束」升格为「成文契约」，闭环 = 文档钉可验证。
4. **MN-1 前瞻落款（红⑨ 构造纪律）**：契约化形态下**行为及时性腿不采**（契约钉为结构/文档形态，2537 红⑥ 诚实留痕纪律）；若后续批次（R-G 治理轨/专用池候选）升级为分离形态，红⑨ 行为腿构造纪律 = **占满全部调度线程（pool-size 个 latch 阻塞 job）或 `pool-size=1` 配置注入**消除多线程噪声后断言触发延迟——本批登记为前瞻落款不实施。
5. **红⑨ 钉形态（契约化）**：结构文档钉——断言 owner doc §1.4 含非阻塞契约句（契约关键词锚断言）+ §3.1 硬性契约句在位；pre-fix「不声明已修」措辞在位=结构红；post-fix 契约句=绿；**变异腿**（还原 :214 旧句→钉红→复归→绿）诚实留痕禁伪绿；闩带 ≤5s。

**被否决的备选**：
- **触发/执行分离·globalWorker 转投**：制造跨单元新共租面（违 2498 §3.1.1 契约方向，与 W-10 收敛矛盾）；identity 复检窗口拉长（plan 风险段：放弃率上升面）。不采。
- **模块专用执行池（bean 或程序化）**：条件申报②禁选预设/生命周期与热更契约成本/定位不成比例；专用池候选归 plan Non-Blocking Follow-ups 已登记（R-G 治理轨）。不采。
- **启动期检测/提示**（静态识别阻塞方法体）：静态不可判定（方法体阻塞性需过程间分析，平台无此设施）；运行时启发式=误报面。不采。
- **维持 :214 现句不动（零文档 diff）**：C4-002 为批次行 in-scope P2 确认缺陷（merge-adjudication §2.2 :55）——零动作=缺陷面无收敛载体，违 Non-Degradable 纪律；契约化是该缺陷的 audit 建议 b 原生修复形态。不采。

**零触碰边界**：`LocalJobScheduler`/`BeanMethodJobInvoker` 产品代码零 diff（本工单为声明面修复）；beans（`app-local-scheduler.beans.xml` :7-15）零改动（条件申报②零触发）；`nopJobScheduledExecutor` 容量语义（§七 2497 契约）零触碰；C2-001 锁外移产物零回退（分离不采故无重引持锁面）；2513 十二钉零回退。

### G.2 W-10：worker 执行载体包络——语义精确化+声明闭合（1.4-C4-005；跨单元 1.2-MA-06 批次对齐）

**决策**：**「三候选组合定夺 = (c) `max-concurrency` 语义精确化 + (b) worker 执行载体包络声明；专用池候选 (a) 归 Non-Blocking Follow-ups（R-G 治理轨），本批零行为 diff」唯一分支**：

1. **(c) 语义精确化（owner doc §五 :435 E5 落点）**：`nop.job.worker.max-concurrency` 行说明从「最大并发执行数（0=不限）」精确化为「**DB 在途认领行数上限**（count-based 闸门；0=不限）——非 JVM 内执行并发上限；JVM 内无信号量，执行体并发受 invoker 实际异步性约束」+ backlog 场景指引句（「积压恢复场景应显式配置该键——audit C5-004 联动面按其自身批次承载，本句为使用指引非其修复」——C5-004 为 R4-07 成员零触碰）。
2. **(b) 包络声明（owner doc §七 E5 扩展落点）**：§七 载体契约节新增 worker 执行包络声明段——「worker 认领任务的 `invokeAsync` 在平台 `globalWorker` 池线程触发（`AbstractBatchScanner.getExecutor` = `globalTimer().executeOn(globalWorker())`，scanner 扫描体与任务执行体同池）：**invoker 实现必须非阻塞**（同 §3.1 本地模式契约的分布式投影——阻塞执行体占住 globalWorker 拖垮全平台后台任务与全部 scanner）；时长无上界的执行体须自行真异步化或经专用载体承载（模块专用载体先例 = `nopRpcPollScheduledExecutor`）」。**resolver 运行时检测/告警不采**（audit 候选 b 的检测半边）——方法体阻塞性静态不可判定，运行时启发式=误报面；声明为诚实边界不虚高（同裁定 G.1 拒绝理由 3）。
3. **(a) 专用池不采的理由**：默认关闭形态下默认部署缺陷面**零变化**（共享形态原样）——「可选配置修复默认缺陷」不成立；默认开启=行为变更面扩大（专用池容量规划/关闭归属/热更契约全套面，§七 扩展义务）+ 违「先落声明边界、治理候选归后续轮」的批次节奏；与 C5-004（R4-07，同区域认领爆发面）联合推演的前瞻义务更适合在 R-G 专用池评估时一并承载（plan Non-Blocking Follow-ups 已预留「W-10 专用池的运维观测候选」）。
4. **跨单元 1.2-MA-06 对齐落款（红线 4——批行增补明文的兑现）**：(i) 判定标准适用结论——worker 业务执行体按 §3.1.1 四要素属「独立容量预算候选」，本批以判定标准给出的**声明边界形态**承载（专用载体判定标准的四要素引用落款于 §七 声明段指引句；「引用不重建」）；(ii) 共租回归面声明——Debezium 引擎已由 R1-03（plan 2498）移出 globalWorker；现共租面 = nop-job worker 执行体+6 scanner 扫描体+全平台后台任务——本批修复零产品 diff，共租面结构不变，声明闭合即收敛动作（与 2498「使用面立案、修复不上提平台」边界一致）；(iii) **联合推演记录**（merge-adjudication §2.3-4 义务）：W-10 修复（声明面零行为 diff）**不存在掩盖面**——运行时行为零变化，R4-07 C5-004（认领爆发）/C7-011（CLAIMED 回收）的后果面与本批声明互不遮蔽；若 R-G 后续采专用池形态，须与 C5-004/C7-011 修复联合推演容量预算互涉（前瞻落款，本批不实施）。
5. **红⑩ 钉形态（声明闭合）**：结构文档钉——断言 owner doc §五 :435 语义精确化句 + §七 包络声明段在位（契约关键词锚断言）；pre-fix 旧措辞在位=结构红；post-fix=绿；变异腿（还原旧 :435 文本/删除 §七 段→钉红→复归→绿）诚实留痕；**线程名接线钉不采**（其 premiership = 专用池/转投形态——本批零行为 diff 无接线面变化，结构钉为诚实形态，2537 红⑥ 纪律）；`TestJobWorkerScanner` 18 注解/16 实跑（MN-4 口径）零回退。

**被否决的备选**：
- **(a) 单独采专用池（默认关闭可选配置）**：见决策 3（默认面零变化+成本不成比例）。不采。
- **(a) 默认开启专用池**：行为变更面扩大+容量规划/热更契约全套义务+与 2497 §七 容量语义扩展耦合——超工单半径。不采。
- **(b) 含 resolver 运行时检测/告警**：静态不可判定（决策 2）——不采检测半边，声明半边采纳。
- **(c) 单独采语义精确化（零声明）**：`:435` 一句精确化只澄清闸门语义，不承载「执行体必须非阻塞」包络契约（缺陷本体的声明面）——两段合并才是完整声明闭合。不采。

**零触碰边界**：`JobWorkerScannerImpl` 产品代码零 diff（本工单为声明面修复）；`AbstractBatchScanner.getExecutor`（:220-222）零 diff（scanner 扫描体共享形态维持——2498 §3.1.1 C 类合格准绳，零改码义务）；maxConcurrency 字段/ceiling 判定（:52/:163-165/:184-187）零 diff（C5-004 同区域 R4-07 成员零触碰）；2496 看门狗产物（:382-411）零触碰；2498 落地面（DebeziumEngineWrapper 等）零触碰零回退；nop-commons 载体族（GlobalExecutors/DefaultScheduledExecutor/ThreadPoolConfig）零触碰红线（条件申报④禁选）；`TestJobWorkerScanner`/`TestJobWorkerScannerInsulation` 零改写。

## 裁定 G 附：红①–红⑩ 驱动器总表 + 零回退钉全列 + C10 注记表

### 红绿判据纪律（先红后绿 E2——本批 2 条 C10 验收前置同 diff + E2 泛在 10 钉 + C10-009 触达）

全批测试零 mockito 新增使用（四子模块 pom 无 mockito 声明——2513 mock 体系在 `TestRemoteJobInvoker`/`TestRpcPollTaskManagerEntryTermination` 已建，W-06 注入缝复用）——观测一律 JDK 并发原语 + latch + 手工桩 + 探针 subclass + 包内可见缝（2513 惯例）。**orTimeout 假绿陷阱禁令**：future 完成性断言只允许同步 `get(timeout)` 捕 TimeoutException 判红。**闩带上界 ≤30s 且收尾必放闸**。红绿快照落 `_tmp/red-green-ledger-2539.md` 总册 + 逐 trial 原始日志（`red2539-*/green2539-*`——2536 Major-D1 灭失教训存证纪律+副本对冲）。测试类注释显式归属 CRA 8.46 R4-06（`*R406` 后缀；同号防混：8.46-W-xx ≠ 8.41~8.45-W-xx，finding ID 防混权威锚 1.4-C1-001≠1.3-C1-001）。**结构/变异腿诚实留痕禁伪绿**（红③④⑨⑩ 结构钉 + 红①②④⑦ 变异腿——禁行为伪红禁概率假绿）；**缺席断言携带实际计数**（MN-7 恢复断言纪律）。

### 红①–红⑩ 驱动器总表（每工单至少一钉 ✓）

| 钉 | 工单 | 驱动器形态 | pre-fix 红判据 | post-fix 绿判据 | 伪绿防线 | 闩带 | 确定性 |
|---|---|---|---|---|---|---|---|
| 红① | W-01 | 一致性 oracle：fireNow 循环写（≥200 轮）+观测线程采样 `getJobDetail`（≥1000 次）断言 execCount 单调不减；**变异腿必做**（还原无锁→钉红→复修→绿） | 过期读回退/撕裂快照=红 | 锁内快照单调（监视器全序保证） | 变异腿确定性红证主承载+单调断言（防「持锁存在但读面绕过」） | ≤30s | 概率主腿+变异补强 |
| 红② | W-02 | C10-007 腿 2：三线程 add×remove 交错循环 50 趟 → `getJobNames()` 终态一致性；变异腿必做（还原静默 return→红→复修→绿） | 无痕丢失（终态不一致且零留痕）=红 | 一致终态+WARN 留痕面 | 终态一致性断言+变异腿；`:471` 顺序钉并列 | ≤30s | 概率主腿+变异补强 |
| 红③ | W-04 | 结构钉：反射断言 `CronExpression` 声明字段零 ThreadLocal 类型 + 功能绿腿（TestCronExpression/GoldenVectors/TriggerCalculator/calendar 族）并列 | 字段在位=结构红（确定性） | 字段消除=绿+算法绿腿全绿 | 源文本/反射结构断言（非启发式）+功能绿腿防算法回归（vendored 深层面） | ≤5s | 结构红（诚实留痕） |
| 红④ | W-03 | 收口核验钉：缝5 反射断言（零 ScheduledJob/SimpleJobState/JobSpec 字段）+params 拷贝行为钉（变异返回 Map→原始 spec 不受影响）；红证=变异回退论证 scratch trial（还原直通形态→钉红→复修复→绿，不落 repo） | 当前即绿（2513 已收口）；红证由变异腿承载 | 两腿绿=契约钉住 | 反射断言（防快照退化）+行为钉（防拷贝缺失）+字段清单同步义务 | ≤10s | 当前即绿+变异红证 |
| 红⑤ | W-05 | latch 交错：测试子类 scanBatch 阻塞在途轮+外部并发 scanOnce → 重复处理/撕裂可观测断言（fetch 计数膨胀） | 外部 reset 清零在途 cursor=红（latch 交错可构造） | WARN+冲突计数+在途轮次不受扰（fetch 计数无膨胀） | 冲突计数断言+绝缘钉零回退并列（防「守卫存在但拒绝不生效」） | ≤10s | 确定性 |
| 红⑥ | W-06 | REE 注入：stub executor 抛 REjectedExecution → register → future 完成性+activeCount 断言 | future 永不完成（get 超时）+activeCount>0=红（确定性） | future 完成 ERROR+activeCount==0+WARN 留痕 | 错误码判别（防「complete 了但错误面错」）+`:254` 摘除锚并列（防回滚误伤监听器面） | ≤10s | 确定性 |
| 红⑦ | W-07 | C10-007 腿 1：双线程 latch 同步双首注册 allowUpdate=false ×50 趟 → atMostOneWinner（typed already-exists 判别）；变异腿必做（还原无 throw→红→复修→绿） | 静默改写（双方无异常或一方覆盖）=红 | 恰一方成功一方 typed 拒绝 | typed 实测+detail 判别+`:471` 顺序钉并列（防顺序路径回归） | ≤30s | 概率主腿+变异补强 |
| 红⑧ | W-08（=C10-004 同 diff） | 三锚点：(i) 自重入拒绝回归锚（当前即绿）/(ii) 执行期 RUNNING 可见锚（当前即绿）/(iii) Error 注入腿（TestInjectedError 同步抛）——pre-fix 卡 RUNNING/awaitIdle 永不空闲/无 FAILED/无重排=红 | (iii) pre-fix 静默停摆=红（确定性） | **MJ-1 谓词绿腿**：FAILED 终态+占位清空+done 完结+调度链存活（对照 job 不受扰）+**禁自愈断言**（fireCount 恒定+fireNow 恒 false——自愈归 C7-006/R4-07） | (i)(ii) 锚防 2513 面回归+(iii) 六断言组防「FAILED 存在但占位悬空」半修+禁自愈防伪绿 | ≤15s | 确定性 |
| 红⑨ | W-09 | 结构文档钉：owner doc §1.4 非阻塞契约句+§3.1 硬性契约句锚断言；变异腿（还原 :214 旧句→红→复归→绿） | 「不声明已修」措辞在位=结构红 | 契约句在位=绿 | 文本锚断言（非全文匹配——防措辞微调误报）+变异腿诚实留痕；MN-1 行为腿前瞻落款（本批不实施） | ≤5s | 结构红（诚实留痕） |
| 红⑩ | W-10 | 结构文档钉：owner doc §五 :435 语义精确化句+§七 包络声明段锚断言；变异腿（还原旧 :435/删 §七 段→红→复归→绿） | 旧措辞在位=结构红 | 精确化+声明在位=绿 | 文本锚断言+变异腿；线程名接线钉不采（零行为 diff 无接线面） | ≤5s | 结构红（诚实留痕） |

### 零回退钉全列（全批 diff 为空核对——closure audit 逐钉 git 过滤）

- **基线签名（plan 勘误 5，2026-11-07 亲跑；HEAD `193a8719f6` nop-job 零 diff 继续有效）**：`./mvnw test -pl nop-job` solo 通道 BUILD SUCCESS EXIT=0（43.5s，Reactor 13 项目）——api 25/core 143/local 56/dao 89/worker 49/coordinator 211 0F0E 2S（2S=容器/门控面——E3 噪声源登记）/service 49/web 1/retry-adapter 3，合计 **626 tests 0F 0E 2S**。零回退实证 = 四站点子模块签名维持（+新钉增量）+其余模块零触碰面全绿；`-am` 批行通道全量验收归 Phase 3（2513 勘误 5 三联裁定先例）。
- **2513 十二钉（MN-8 存档口径——成员清单以其收口档为 SSOT）**：`TestRpcPollTaskManagerEntryTermination` 9 用例/`TestLocalJobSchedulerManagementResponsiveness` 5 用例/`TestRemoteJobInvoker` 13 用例（含 :254 摘除锚）/`TestRpcPollTaskManagerInsulation` 2 用例/plan 340 钉（`TestJobTimeoutChecker` :614-639）/`:471`/:388/:497 既有锚/`TestBeanMethodJobInvoker` **17 用例（MN-3 live 订正值）**/其余 2513 收口档钉——钉内断言零改写。
- **2496 绝缘钉**：`TestAbstractBatchScanner` 9 用例/`TestJobWorkerScannerInsulation`/`TestRpcPollTaskManagerInsulation`/`:497` fail-safe 锚——零改写零弱化。
- **既有锚点名（本批零回退锚）**：`TestLocalJobScheduler` 15 用例全保（:471/:388/:497 关键钉）`TestLocalSchedulerResultContract`/config 两测试/`TestCronExpression`/`TestCronGoldenVectors`/`TestJobTriggerCalculator`/calendar 族/`TestJobCoordinatorScanner` 27 用例/`TestJobWorkerScanner` **18 注解 16 实跑（MN-4 口径——2 个非 @Nested static inner @Test 为预先存在死测试，本批零触碰，G91 类观察候选不扩批）**/`TestNopJobTaskLogBizModel` 4 用例（C10-008 宿主——R4-07 承接零触碰）/dao 两测试零触碰；C10-006/008 宿主测试文件零触碰；8.45 `*R405` 钉（零站点交集）零触碰。

### C10 注记表（对齐纪律第一条）

| C10 条目 | 承载面 | 本批关联工单 | 零改写/兑现核对面 |
|---|---|---|---|
| C10-004（**验收前置，同 diff 绑定**） | 三锚点测试化（(i)(ii) 回归锚+（iii) Error 注入先红后绿——MJ-1 谓词） | W-08（红⑧） | 同 diff 必落（禁拆分）；(i)(ii) 落钉即绿成为 2513 收口面回归锚（零改写义务自落钉起生效） |
| C10-007（**验收前置，同 diff 一套测试**） | 一套并发注册测试两腿（atMostOneWinner+add×remove） | W-02+W-07（红②/红⑦） | 同 diff 单测试类两腿（禁双套禁重复计数）；`:471` 基线叠加非替代 |
| C10-006/C10-008（**批间边界——勘误 3**） | 宿主 C7-011/C5-002 ∈ R4-07——随本体落 R4-07 | —（本批零触碰） | `TestJobTimeoutChecker` CLAIMED 两测与 `TestNopJobTaskLogBizModel` 零改写；Phase 4 就绪注记带 R4-07 承接指向；禁提前落红钉（造无本体红空壳——2512 勘误 4 纪律） |
| C10-009（G91 触达义务） | `TestLocalJobScheduler` :142/:163/:193 三处固定 sleep → 随束测试触达改 deadline 轮询/有界忙等（不以独立工单立项不扩测试基建面；缺席断言携带实际计数——MN-7） | 束测试触达面 | G91 :476 消号注归 Phase 4（沿 1.3-C10-007/008 搭车消号先例） |
| 既有 C10 面零改写清单 | C10-001/002/003/005（2513 十二钉内）+ C10-006/008 宿主测试 + 8.45 `*R405` 钉 | 全批 | 红线 1/2/3 全集——closure audit git diff 逐钉核对 |

## 条件申报登记（禁静默扩权——预设全部不触发，①②④三禁选落款）

①**nop-job-api（错误码/SPI javadoc）→ 预设不触发落款**：W-07 复用 `ERR_JOB_ALREADY_EXISTS`+`ARG_JOB_NAME`（live import :35/:37）；W-08 catch 收宽零新码；W-06 复用 `ERR_JOB_REMOTE_INVOKE_FAILED`；裁定 E 拒绝面 WARN+计数零错误码；`IJobInvoker`/`IJobExecutionContext` SPI 契约 javadoc 零 diff（异步性契约句落 owner doc §3.1 :331——E5 承载非 api 面）。**如裁定翻转需新增错误码/SPI javadoc 变更 → 跨模块公共 API plan-first 四件链申报后方可实施。**
②**`_vfs` beans 新增 bean 定义 → 禁选落款**：裁定 G.1 契约化形态零 beans diff（专用池候选禁选）；`app-local-scheduler.beans.xml`（:7-15）零改动。**如后续形态演进需 beans 改动 → 沿 plan 2513 PA 段先例在裁定项内批内闭环申报。**
③**`model/*.orm.xml` → 预设零触发禁选**：10 工单零 DB 承载形态。
④**nop-kernel/nop-commons 载体上提（GlobalExecutors/DefaultScheduledExecutor/ExecutorHelper/ThreadPoolConfig 本体）→ 禁选落款**：merge-adjudication §4.5 关联 2「nop-job 使用面立案、修复可上提平台——上提需另立跨单元工作项（本批零上提）[MN-R2-2 引用极性订正]」边界；裁定 G 零上提（W-09/W-10 均零产品 diff）。
⑤**`_` 生成物/`_gen/`/pom 结构 → 预设零触发**：新测试落既有测试树（`*R406` 新类）；零新依赖；站点文件零 `_` 生成物。
**任一条件申报触发 → 按 AGENTS.md 四件证据链走完方可实施；未申报前实施=禁。**

## 零触碰边界总表（8.19/8.2/8.3/8.4 产物面 / 同文件跨批面 / 批外模块 / R4-07 与 P3 相邻 / `_gen`+orm / 既有裁定文档）

- **触碰面总口径（MN-6）**：6 个 main java 文件+1 个 `_vfs` beans 申报面（裁定 G 契约化下零 diff——申报面保持名义登记）跨 4 子模块（nop-job-local 2 main/beans + nop-job-core 2 main + nop-job-coordinator 1 main + nop-job-worker 1 main）+ 四子模块测试树 + 文档面。批内同文件面：LocalJobScheduler 六面 diff 分组登记禁顺带禁交叉（W-02+W-07 同分支同组单 diff）；AbstractBatchScanner 两面（W-05 守卫 + getExecutor 关联锚——裁定 G.2 零 diff 落款）；RpcPollTaskManager 单面（W-06）；JobWorkerScannerImpl 零产品 diff（W-10 声明面）。
- **8.19/plan 2513 产物面（同文件跨批——禁顺带逐面）**：两阶段四产物（prepareJobLocked/startJobUnlocked/settleExecution/abandonAfterInterleave+占位先行 :335→:354）+ctx 真快照（:628-698）+rpcPoll 五面+PollEntry 白名单+gauge+十二钉+五处实施期偏差登记面——零重开零回退（红线 1/2）；W-03/W-08 为其预声明承接面（非重开面——2513 javadoc :327-328 与就绪注记原文）。
- **8.2/plan 2496 产物面**：绝缘层本体+MA-05 三站点 fail-safe+MA-06 observe-only 看门狗+AbstractBatchScanner 绝缘边界——零重开零弱化（红线 3）；W-05 守卫在绝缘边界内、W-08 settleExecution 站点③ fail-safe 零触碰、W-10 看门狗零触碰。
- **8.3/8.4（plans 2497/2498）产物面**：池设施产物（容量语义/热更契约/调度槽纪律）/§3.1.1 判定标准/Debezium per-engine 专用载体/九工单落地面——零触碰（W-09/W-10 引用对齐不修改；红线 4）；新配置键全集（`poll-fail-threshold`/`invoke-timeout-ms`/`global-timer.corePoolSize` 等 2496/2497/2513 键）零触碰。
- **批外模块（零 diff 红线）**：nop-commons 载体族（C4-006/C4-007/C4-011 载体——只读引用）；nop-job-api（零 diff——条件申报①）；nop-job-dao/service/web/app/meta/codegen/retry-adapter 八子模块产品源码（零站点）；nop-message/nop-stream（2498/2538 产物面零触碰红线）；`TestJobTimeoutChecker`/`TestNopJobTaskLogBizModel` 等批外测试文件零触碰；coordinator 2S 容器/门控面零触碰。
- **R4-07 十成员（C5-002/C5-004/C7-003/C7-004/C7-005/C7-006/C7-007/C7-009/C7-010/C7-011）+ C10-006/C10-008**：零触碰（W-10 联合推演为设计义务非代码触碰；C7-006 为红⑧ 禁自愈断言的归属批——引用不代做）。
- **P3 相邻 8 条（§4.2 全集 9，C4-011 载体红线另列——MN-2 口径）**：C2-005（addJob 更新分支 active 复检——W-02/W-07 同方法零触碰）/C2-006（registeredCursors 迭代守卫+newCursor fail-fast——W-05 同文件零触碰）/C4-006/C4-007/C4-011（nop-commons 载体零触碰红线）/C4-008（manager 无 stop——W-06 同组件零触碰）/C4-010（cancelToken 恒 null——引用不重开）/C7-008（rpcPoll 墙钟——2513 注记零代做）/C7-012（JobLogReporter WARN 采样——零交集）。执行期意外触及禁顺带——四件证据链申报或回退。
- **`_gen` 与 orm**：站点文件零 `_` 生成物；`nop-job/model/nop-job.orm.xml` 零 diff（条件申报③⑤预设零触发）。
- **既有裁定文档**：2513/2496/2497/2498/2509/2538 设计文档、batch-proposal、merge-adjudication、inventory-p2-c10、cra-index 台账行——零回写（收敛注记归 Phase 4）；本设计文档为唯一新增文档面（文件命名零调整——分组权威落本文映射节）。

## 端到端与接线验证义务（rule 22/23/24——随 Phase 2 落证；裁定侧落款）

- **端到端（rule 22——plan Phase 2 三链路）**：≥1 条本地模式链路测试覆盖 W-01/W-02/W-07/W-08 组合面（注册 → 触发 → 同步 invoker 执行 → 执行期管理 API 可见正确状态 → Error 注入 FAILED → 后续周期调度链存活（他 job 触发不受影响——MJ-1 谓词）——红⑧ 即链路载体+`TestLocalJobScheduler` 扩展腿）；≥1 条 scanner 链路测试覆盖 W-05 守卫与 2496 绝缘组合行为（守卫拒绝 → 绝缘存活 → 下一轮正常——`TestAbstractBatchScanner` 扩展）；≥1 条 rpcPoll 链路测试覆盖 W-06 回滚通道与 2513 四通道组合（REE 注入 → 回滚 → activeCount 归零——`TestJobCoordinatorScanner` 两既有 rpcPoll E2E 为零回退载体，回滚腿扩展）；W-09/W-10 链路面按裁定形态（契约化/声明闭合——链路载体为结构钉，无运行时链路面）。
- **接线验证（rule 23——十项全实测）**：红① 一致性 oracle 触发记录实测/红② 终态一致性断言实测/红③ 结构断言实测/红④ 反射+行为断言实测/红⑤ WARN+冲突计数+绝缘存活实测/红⑥ complete(ERROR)+activeCount 归零实测/红⑦ atMostOneWinner 异常断言实测/红⑧ FAILED+占位清空+调度链存活（他 job 不受扰——MJ-1）实测/红⑨ 契约句在位断言实测/红⑩ 精确化+声明在位断言实测——每钉断言其修复真实被消费链调用，非仅字段/分支存在。
- **无静默跳过（rule 24——十项显式）**：持锁显式（getJobDetail synchronized——非无锁读维持）/重试留痕显式（raced WARN+重试——非静默 return 维持）/快照缝显式（缝5 钉+javadoc——非活引用维持）/栈局部显式（getTimeAfter 每调用构造——非 ThreadLocal 维持）/守卫拒绝显式（WARN+冲突计数——非静默并发穿透维持）/回滚留痕显式（cancelEntry+complete(ERROR)+WARN——非吞异常维持）/契约 throw 显式（raced allowUpdate typed——非静默改写维持）/Error FAILED 显式（settleExecution FAILED 终态——非静默停摆维持）/分离或声明显式（契约化契约句——非「不声明已修」含糊维持）/载体或语义声明显式（精确化+包络声明——非 0=不限歧义维持）；新增公共分支 TODO/FIXME grep 零命中（全部站点文件全查）；回滚/拒绝/放弃分支无空 catch/静默 return。

## 批外新发现登记（亲读发现即登记，禁瞒——参照 2538 BF-1~4 形态）

- **BF-1（批外新发现——watch-only 登记，非本批缺陷面）**：`LocalJobScheduler.handleResult` 完成回调路径在 SUSPENDED 交错下存在复归窗——`suspendJob` 在执行中把 `internal` 置 SUSPENDED 后，本次执行完成经 `settleExecution→handleResult`（:407-439）走到尾部 guard：guard 只排除 COMPLETED/FAILED（:434），SUSPENDED 落入 `scheduleNext`（:438）→ `scheduleNext` 无 SUSPENDED 守卫地把 `internal` 改写回 WAITING（:306）并重排——**执行中 suspend 的效果在完成回调时被静默撤销**。check2 [P3-13] 修复（`rescheduleAfterUpdate` :262-270 注释亲证）只覆盖 update 路径，未覆盖 settleExecution 路径。同族先例方向 = C7-006（本地终态语义）但站点不同。登记归后续 R4 同单元余量轮（R4-07 及其后）或 R-G 立项裁定；本批零触碰（`handleResult` 属 W-08 面邻接区——禁顺带；W-08 catch 收宽不改变该路径语义）。
- **BF-2（信息性核证结论，非缺陷）**：`awaitState`/`awaitIdle`/`awaitFireCount`（:464-525）的 `synchronized(job)` 段为瞬时状态检查——2513 裁定②「awaitXxx 段零改动+监视器获取段恒有界」残留边界声明 live 亲证成立（锁内段微秒级，timeout 约束轮询总时长）。
- **BF-3（信息性核证结论，非缺陷）**：`settleExecution` fail-safe 顺序亲证正确——`job.running = null`（:376）先于 `handleResult` 调用（:377）、catch (Throwable) 内 FAILED 兜底（:382-383）先于 done 完结（:385-388）——2496 站点③ 形态与 W-08 复用通道的次序前提在位。
- **BF-4（信息性——JobWorkerScannerImpl 锚区行号精确化）**：in-flight 登记区精确锚 = `:351 put/:354 watchdog 调用/:357 remove/:382 方法/:405 observe-only 注释`（plan 行文 :337-341 区间漂移——已在 K4 登记）；invokeAsync :341 零漂移命中；W-10 缺陷面锚与 2496 看门狗产物零触碰边界以精确值为准。
- **核证范围声明**：10 工单站点文件全量亲读（LocalJobScheduler 全文件/AbstractBatchScanner 全文件/CronExpression 关键区/TriggerBuilder/CronCalendar 关键区/beans.xml 全文/RpcPollTaskManager register 区+cancelEntry 全链/RemoteJobInvoker 动作区/JobWorkerScannerImpl 关键区/JobCoreErrors 码表/owner doc 七节锚区）；除 BF-1 外无批外缺陷。

## 评审记录

- **设计评审 R2**（2026-11-07，agent_feecc95b fresh session 只读，live 抽验 ~160 处锚点）：**PASS-with-revisions——0 Blocker + 0 Major + 7 Minor，放行 Phase 2**。三风险专项全部 PASS（裁定 B raced 分支全空间推演[4 组合+对抗性双重 remove]/裁定 C Error 收宽 2513 四产物零回退[settleExecution 全路径+三判据 post-fix 亲证]/裁定 F register 回滚时序保序[REE stub 缝 2513 mock 体系内可实现]）。MJ-1+MN-1~8 九项 9/9 吸收落款核证；BF-1 为真发现亲证（suspend 被完成回调静默撤销）。**MN-R2-1**（红⑧ done 断言观测机制句——红⑧ 实施前落盘，本节随落）/MN-R2-2~7 随本记录回填落盘，无重审义务。
