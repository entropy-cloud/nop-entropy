# 单元内跨维度合并裁定 — nop-job（7.1 终版）

- 裁定人：独立合并裁定子代理（与各维度初审/深挖/复核均不同会话）
- 裁定日期：2026-10-15
- 仓库：/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit（分支 concurrency-audit-remediation，HEAD 335e77caec）
- 输入：本审计目录 C1/C2/C4/C5/C7 五维度文件 + C10-test-protection.md 的**最终保留项节 + 维度复核结论节**（疑重合条目另读条目正文与复核状态行）；跨单元对账基准 = `ai-dev/audits/cra-index.md`（WI 1.1/1.2/1.3 台账）
- 口径：**同一缺陷** = 同一根因同一失效层 → 取一为主条目（证据链最完整、判级最准确者），其余消号交叉引用；**同族相邻** = 同一根因不同失效层 → 合并单一缺陷族/单一修复工单，验收须同盖全部成员失效面；**不同** = 各自立案（可 cross-ref）。推复核提议须给出证据。

---

## 1. 裁定方法与输入清单

### 1.1 各维度保留条目 × 严重度（复核后终版权威）

| 维度 | 保留 | P0 | P1 | P2 | P3 | 备注 |
|---|---|---|---|---|---|---|
| C1 共享可变状态 | 6 | 0 | 0 | 5（001~005） | 1（006） | 第 2 轮零新增；复核 6 保留 0 降级 0 驳回 |
| C2 锁与同步 | 8 | 0 | 1（001） | 4（002/003/004/007） | 3（005/006/008） | 第 2 轮新增 007/008；复核 8 保留 |
| C4 线程池与执行器 | 12 | 0 | 1（001） | 6（002/003/004/005/009/012） | 5（006/007/008/010/011） | 第 2 轮新增 009~012；复核 12 保留 |
| C5 内存有界性 | 5 | 0 | 1（001） | 4（002/003/004/005） | 0 | 第 2 轮新增 005；复核 5 保留 |
| C7 长时运行 | 12 | 0 | 1（001） | 9（002~007/009/010/011） | 2（008/012） | 第 2 轮新增 009~012；复核 12 保留 |
| **合计（缺陷条目）** | **43** | **0** | **4** | **28** | **11** | 全部「新发现」现状 |

另：C10 缺保护登记 9 条（全部 P2，非缺陷条目，见 §4.4）。本单元 P0 = 0（无即时通道项）。

### 1.2 维度复核已登记的归并提议（裁定起点）

① C4-001 = C5-001 = C7-001 同一机制三维度视角（三方复核均 P1，修复须覆盖失败预算/deadline 兜底/DB 终态联动三面，受 C4-010 cancelToken 恒空前置约束）；② C5-005 = C5-001 影响放大器随主条目闭合；③ C4-009 = C7-002 同根因异维度；④ C2-002 = C1-001、⑤ C2-003 = C1-005、⑥ C2-008 = C1-002（各为同代码点/同泄漏点归并）；⑦ C4-012 = C4-004 同族同修法（同维已合并）；⑧ C7-011 与 C4-009 相邻——失效层不同，**同族相邻或不同由本裁定裁决**；⑨ C4-012 与 C4-003 的 F9 族关联口径以 C4 文件归并声明为准（C10 复核 R6 备注）。全部提议独立复核，可推翻（裁决见 §2/§3）。

---

## 2. 合并裁定表

### 2.1 合并组（缺陷族 MA-01 ~ MA-07）

| 族 ID | 判定 | 成员条目 | 主条目 | 终裁严重度 | 根因（一句） | 修复验证须覆盖的失效面 |
|---|---|---|---|---|---|---|
| **MA-01** | 同一缺陷→并入（C4-001 与 C5-001 为同根因同失效层〔资源累积〕双视角；C7-001 为同根因附加失效面；C5-005 为族内放大器面） | C4-001（主）、C5-001、C7-001、C5-005（放大器） | C4-001 | **P1**（三方复核一致，采纳） | RpcPollTaskManager 条目在默认装配下无终结驱动：四终结条件（future 终态/deadline/cancelToken/whenComplete）中三个失效（无失败预算、computeDeadline 不读全局 execution-timeout-ms、DB 终态不回传、cancelToken 恒 null），唯一活性来源 future 在远程失联时永不完成 | ①entries 注册表 + 周期句柄无界累积（内存 + nopRpcPollScheduledExecutor 队列，天-周级单调）②5s 固定间隔无失败预算/无退避的重试风暴（RPC churn）③WARN 日志放大（每条目每日 ~1.7 万条）④零观测（无 entries gauge，activeCount package-private）⑤PollEntry 钉住三实体 JSON payload 对象图，单条目驻留 O(payload)（C5-005；量级为静态推断，PR 附驻留体积断言）。修复组合须同盖：失败预算/退避、deadline 纳入全局超时兜底、DB 终态→future 回传联动、PollEntry 精简视图、activeCount gauge。**前置约束**：C4-010（cancelToken 内建装配恒 null——「补 cancelToken」式修复无效）；**同点协调**：C7-008（computeDeadline 重写时钟源）、C1-004（register 回滚面）、MA-05（pollOnce 站点 Throwable 加固） |
| **MA-02** | 同一缺陷→并入 | C1-001（主）、C2-002 | C1-001 | **P2**（两维一致） | LocalJobScheduler.getJobDetail（:64-67）是 6 个公开读方中唯一不持 `synchronized(job)` 者，无锁读受监视器保护的 spec/plain long 计数（写方 :496-507/:510-519 全部持锁）——JMM 数据竞争，观测面可见过期/错位快照 | ①可见性过期读 ②spec/计数撕裂快照（long 撕裂子论点在 64 位 HotSpot 下不可达，不作为验收面）。修复 = getJobDetail 纳入 per-job 监视器（与其余 5 读方对齐）或锁内快照；验收对照 getJobState 持锁正例 |
| **MA-03** | 同一缺陷→并入 | C1-005（主）、C2-003 | C1-005 | **P2**（两维一致） | LocalJobScheduler.addJob raced 分支（:110-120）在 raced 条目已被并发 remove 时静默 `return`（无异常无日志），与 existing 分支（:96-97）对同一竞态的 fall-through 重建语义相反——removeJob 先 `jobs.remove` 后持锁使锁内复查失效 | ①并发 add×remove 下作业注册无痕丢失 ②与 existing 分支语义对齐（fall-through 重试或至少 WARN 留痕）。**同分支共设计**：与 C2-004（同一 raced 分支，跨族关联）一次重构同批验收；测试为概率性形态（C10-007），建议包内 seam |
| **MA-04** | 同族相邻→缺陷族 | C1-002（主）、C2-008 | C1-002 | **P2**（族内最高规则；C2-008 原 P3 面随之闭合） | SimpleExecutionContext 将 live 可变 jobParams Map 与 SimpleJobState（plain long getter）逃逸给 invoker 用户代码，本地路径为同族唯一无防御拷贝者（:317/:559-595） | ①完整性面（C1-002）：用户代码可变异 jobParams/内部 state，分布式路径有防御拷贝而本地路径无（不对称）②可见性面（C2-008）：自定义异步 invoker 跨线程延迟读 plain long 与持锁写方构成 JMM 竞态（内置 4 invoker 全部不触发）。单一修复（本地路径防御性快照/不可变化）同盖两面 |
| **MA-05** | 同族相邻→缺陷族 | C4-004（主）、C4-012 | C4-004 | **P2**（两维一致） | 异步续延边界的错误处理只捕 Exception/whenComplete 动作异常被 future 框架吞掉：Error 逃逸使续延死亡且零日志（F9 族） | 三站点同修法（catch Throwable + 日志 + 终态 fail-safe）逐一验收：①C4-004 = RpcPollTaskManager.pollOnce 抛 Error → JDK 周期任务静默抑制，entry 僵尸化零日志（勘误：滞留物为 entries 内 PollEntry+死句柄引用，非 DelayedWorkQueue）②C4-012 = JobWorkerScannerImpl whenComplete 两站点（:302/:379-381）衍生 future 无人消费，task 滞留 RUNNING（勘误：「fireNow/awaitIdle 永假」过宽——running 已于 Error 逃逸前置 null，实际为 state.internal 停留 RUNNING + 永不再排程 + done 悬空）③C4-012 关联面 = LocalJobScheduler whenComplete 区域（:281-290/:335-344）。与 C4-003（链死带日志无 re-arm）判「不同」，仅同主题（见 §2.3） |
| **MA-06** | 同族相邻→缺陷族 | C4-009（主）、C7-002 | C4-009 | **P2**（两维一致） | 同一站点同一根因：JobWorkerScannerImpl 派发 promise（invokeAsync 后仅挂 whenComplete，:296-306）无超时包装——永不终结的 invoker future 无终态驱动 | ①容量账本面（C4-009）：DB task 永久 RUNNING → cost 永久计入 sumReservedCost（RESERVED 状态集含 RUNNING，NopJobCoreConstants:40-44；JobTaskStoreImpl:166-169）侵蚀容量至 worker 静默停止认领 ②状态/观测面（C7-002）：fire 永久 RUNNING、DISCARD 块策略下 schedule 静默停摆、无 in-flight 年龄 metric。**修复通道钉死约束**（C10-002/R5）：checker 侧默认兜底被 plan 340 声明契约测试 + JobTimeoutCheckerImpl:497-501 声明注释双向钉死——修复唯一有效位在 worker 侧（orTimeout 式看门狗 + in-flight 年龄 gauge），除非先修订契约测试与注释。验收须同盖 sumReservedCost 释放方向与 RUNNING 滞留可观测两面 |
| **MA-07** | 同一缺陷→并入（**新裁定，复核双向缺登**，见 §3.1） | C5-003（主）、C1-006 | C5-003 | **P2**（族内最高规则，记录 C1 维度 P3 异议，见 §3.2） | CronExpression（vendored）每实例内嵌 `ThreadLocal<GregorianCalendar>`（L87/两构造器/L194）且无 remove，触发器链在 planner 每周期经 `TriggerBuilder:33` 重建实例 → 长寿命扫描线程 ThreadLocalMap 遗留 stale entry + 分配 churn | ①planner 重建面（C5-003 = C1-006 路径 a，逐点同一）：每 schedule×每 scan 周期新建实例，batchSize(100)×maxScanLoops(1000) 量级 churn ②本地长 lifecycle 面（C1-006 路径 b，附加）：每 job 每线程驻留 1 个 Calendar，有界。单一修复（getTimeAfter 栈局部化 Calendar；C5-003 方案为准——该 vendored 类已有 72b770b094 修改先例，C1-006「维持 vendored 不动」立场被族裁定覆盖，仅作修复阶段备选）；惰性 expunge 保证最终回收，验收按「反模式消除 + churn 断言」而非无界增长断言 |

### 2.2 独立立案（判定：不同，27 条）

| ID | 严重度 | 标题 | 独立性说明（与疑似重合组的根因/失效层差异与 cross-ref） |
|---|---|---|---|
| C2-001 | **P1** | per-job 监视器内调用外部 invoker：同步执行型 invoker 使业务方法体全程持锁——管理 API/停机无限期阻塞 + 可构造 ABBA 死锁 | 与 C2-007 同方法不同根因（锁范围 vs 占位赋值时序）、不同失效层（管理面阻塞/死锁 vs 重入递归/静默死亡）→ 各自立案；**修复强制共设计**（同一段 executeJob 锁范围重构，见 §2.3）。测试锚点 C10-003 |
| C1-003 | P2 | AbstractBatchScanner.public scanOnce 无并发守卫逃逸「scheduleWithFixedDelay 单线程」契约 | 与 C2-006 同类不同缺陷：本条是 scanOnce 在途守卫缺失（cursor 重置重复处理），C2-006 是 cursor 注册/迭代守卫缺失（CME）——不同代码点不同失效层。src/main 仅 doScan 调用（暴露面判断准确） |
| C1-004 | P2 | RpcPollTaskManager.register 复合注册非原子：REE 中途异常在 whenComplete 动作内静默消失，条目泄漏 + promise 永不完成 | 与 MA-01 不同根因（注册原子性/回滚缺失 vs 已注册条目无终结驱动）、触发不同（停机窗口 REE，稳态不可达 → P2）。泄漏面已被 C5-001 链路枚举为次级窗口——MA-01 的年龄兜底 reaper 会收敛其泄漏后果，但回滚缺失、cancel 监听器滞留与零日志仍需本条修复。cross-ref MA-01（同注册表）、MA-05（whenComplete 吞异常同载体，修复加固 pass 同批） |
| C2-004 | P2 | addJob raced 分支绕过 allowUpdate=false 契约：并发首注册静默改写对方 spec，应抛 ERR_JOB_ALREADY_EXISTS 而未抛 | 与 MA-03 同一 raced 分支不同根因（契约校验缺失 vs 竞态恢复缺失）、不同失效层（静默 spec 改写 vs 静默丢注册）；修复互不覆盖（补 throw ≠ 补 fall-through）→ 分立。同分支共设计（§2.3）；测试锚点 C10-007 |
| C2-007 | P2 | job.running 占位赋值滞后（:321 vs :334）：fireNow 可重入递归（catch(Exception) 放行 SOE 逃逸 → 该 job 调度静默永久死亡）、awaitIdle 全程误报空闲、update 运行中守卫失效 | 与 C2-001 同方法不同根因不同失效层（见 C2-001 行）→ 分立共设计。精确性备注：SOE 逃逸后 ExecutorHelper:187 记无 jobName 通用 error 日志，「模块内无日志」表述精确。测试锚点 C10-004 |
| C4-002 | P2 | 本地模式定时触发与 job 执行共用同一 4 线程调度池，阻塞 job 无隔离拖垮全部作业触发 | 与 C2-001 同为「同步 bean 方法执行模型」后果但失效层不同（池隔离缺失 vs 锁范围）；与 C4-005 不同池不同模式（本地专用池 vs 平台 globalWorker）→ 分立 |
| C4-003 | P2 | scanner 重排链遇 Throwable/提交拒绝即永久停摆：running 仍 true 状态说谎，Throwable 路径仅一条日志、拒绝路径完全无日志（复核强化） | 与 MA-05 同 F9 主题不同缺陷：本条根因是 BindScheduledExecutor 重排链无 re-arm + running 标志失真（载体 nop-commons BindScheduledExecutor:88-116 catch Throwable→completeExceptionally 无重排），MA-05 是 Error 吞没零日志——修复互不覆盖（re-arm/生命周期告警 ≠ catch 加固）→ 分立。与 C7-007（挂起 stall）不同失效层（链死 vs 链活但停滞）→ cross-ref。测试锚点 C10-005 |
| C4-005 | P2 | worker 业务任务与全部 scanner 扫描体共享平台 globalWorker（30 线程/10000 队列），max-concurrency=0 无 JVM 内并发上限 | 与 C5-004 不同根因（架构性共享池选择 vs 并发闸门缺省旁路）、与 C4-002 不同池 → 分立；跨单元关联见 §4.5（与 WI1.2 MA-06 共租同一平台池） |
| C4-006 | P3 | 关闭路径无等待语义：池 destroy 为 shutdownNow 无 awaitTermination，scanner stop 为 cancel(false) 不等在途批次（F8 对称性合格，仅缺宽限期） | 生命周期收尾面，与他组无根因重合；载体 DefaultScheduledExecutor（nop-commons）按使用边界立案 |
| C4-007 | P3 | 调度池 maxPoolSize 配置位无效：ExecutorHelper 强制置 0，poll-threads 的 max 语义名不副实 | 配置诚实性缺陷，根因载体 ExecutorHelper（nop-commons，平台级语义，见 §4.5 关联 2）；与 MA-01 同池不同面（配置语义 vs 条目终结） |
| C4-008 | P3 | RpcPollTaskManager 无 stop 生命周期：fallback 到 globalTimer 时在途句柄挂永不销毁平台池，manager 销毁后空转至 JVM 退出（仅非默认装配） | 与 MA-01 同组件不同根因（manager 自身生命周期缺失 vs 条目终结机制缺失）、不同触发（仅非默认装配）→ 分立 |
| C4-010 | P3 | cancelToken 在全部内建装配下恒 null（default 方法唯一来源，三实现均未供 token）：「取消即时感知」特性整体不可达 | 死特性立案；作为 MA-01 修复空间的前置约束存在（「补 cancelToken」式修复无效），但缺陷本体（特性不可达）独立于 MA-01 的终结缺失 → 分立，cross-ref MA-01 |
| C4-011 | P3 | 两声明池非守护线程（ThreadPoolConfig.java:29 默认 false）×同步执行模型×shutdownNow 单次中断：显式停止后不可中断任务使 JVM 无法退出 | 载体 nop-commons ThreadPoolConfig（平台默认值，见 §4.5 关联 2）；与 C4-006 同属关闭路径不同面（daemon 属性 vs 等待语义）→ 分立 |
| C5-002 | P2 | reportTaskLog 批量端点无条目数/单条体积上限：单请求全量物化 + 单事务逐条双实体加载 | 与 C7-004 同「无界增长」主题不同根因（请求期上限校验缺失 vs DB 保留策略缺失）、不同失效层（请求线程峰值/长事务 vs DB 账本单调增长）→ 分立，cross-ref。测试锚点 C10-008 |
| C5-004 | P2 | worker 默认 max-concurrency=0 + capacity 未声明退化 MAX_VALUE：双闸门全旁路，单周期可认领至多 10 万任务并持未决 promise | 与 C4-005/MA-06 同 drain 循环区域不同根因（缺省闸门旁路 vs promise 无终结驱动）→ 分立；修复同区域需排批协调（§5 批次 4） |
| C7-003 | P2 | SUSPICIOUS 单向棘轮不复查存活 + 注册表「全不健康空集」放大：瞬态健康抖动误杀在途任务并丢弃迟到真实结果 | 自愈机制误伤面，与 C7-011（回收缺口）方向相反（误杀 vs 不回收）不同根因 → 分立 |
| C7-004 | P2 | nop_job_fire/task/task_log 三表无保留/归档/清理机制且 delete 被显式阻断：DB 账本单调增长 + 扫描查询劣化 | DB 侧政策缺陷；C5 覆盖度节的 DB 单独标注（nop_job_task_log 无 TTL）折叠入本条上下文，C5 未重复立案 → 无双计。与 C5-002 分立（见上）；与 MA-01 的 DB 联动面无重合（本条是账本保留策略） |
| C7-005 | P2 | JobScheduleCounterReconciler 固定排序窗口饥饿：活跃 schedule > batch-size 时窗口外计数漂移永不收敛 | 自愈机制可达性缺陷，与 C7-003/C7-011 不同机制 → 分立 |
| C7-006 | P2 | 本地模式单次执行异常即永久 FAILED：无重试/无下次调度，resumeJob/fireNow 均不生效 | 与 C2-007 不同机制（声明终态语义无自愈 vs SOE 逃逸静默死亡）、与 MA-03 同文件不同代码点 → 分立，cross-ref C2-007（同为本地作业停摆面） |
| C7-007 | P2 | 扫描器线程阻塞 DB 查询时 fixed-delay 链静默停摆：无 stall 检测/心跳/日志，调度与自愈链同时失效 | 与 C4-003 不同失效层（链活但无限期停滞 vs 链死）：触发（挂起 vs 抛异常/拒绝）、修复（stall 检测/JDBC 超时配置 vs re-arm）均不同 → 分立，cross-ref C4-003 |
| C7-008 | P3 | rpcPoll 轮询期限使用 JVM 墙钟，与协调器其余期限判定的 DB 估计时钟不一致 | 与 MA-01 同 computeDeadline 站点不同根因（时钟源选择 vs 终结缺失）：即使 MA-01 补入 deadline 兜底，墙钟回拨/前跳偏移仍独立存在 → 分立；修复协调（MA-01 重写 computeDeadline 时直接落 DB 时钟可一并闭合，见 §5） |
| C7-009 | P2 | 触发器计算持久失败无隔离：completion 侧 5s 无退避重试环（fire 永久 RUNNING）+ planner 侧 60s 环（调度静默停摆） | 与 MA-01 不同组件不同环（completion/planner vs rpcPoll 轮询）、stuck fire 有界不判 P1 论证成立 → 分立 |
| C7-010 | P2 | planner 乐观锁 fence 过期窗口：滞后 planner 插入重复 fire，hasWaitingFire 精确匹配与三列 UK 均不拦——同一触发槽双执行 | 数据正确性缺陷（fencing 窗口），与他组无根因重合 → 分立 |
| C7-011 | P2 | CLAIMED 态零回收：liveness 要求 worker 消失、执行超时被 isRunning 门排除（与认领 javadoc 矛盾）、stale 重置仅 WAITING——fire 永久 RUNNING + 并发/资源额度永久占用 | **对 §1.2-⑧ 开放问题的裁定：判「不同」，各自立案。** 与 MA-06 后果面相邻（RESERVED/IN_FLIGHT 额度侵蚀 + RUNNING 滞留）但：根因不同（checker isRunning 门排除 CLAIMED 肯定性回收 + stale 重置仅 WAITING vs 派发 promise 无超时包装）、站点不同（JobTimeoutCheckerImpl/JobTaskStoreImpl vs JobWorkerScannerImpl）、修复通道互不覆盖（本条修 checker 门与 stale 重置路径；MA-06 修 worker 侧 promise——checker 侧 RUNNING 兜底被 plan 340 契约钉死，修本条不闭合 MA-06，反之亦然）→ 分立 + cross-ref MA-06。测试锚点 C10-006（注意 R7：认领时钟字段为 startTime） |
| C7-012 | P3 | JobLogReporter 循环路径无采样/无抑制 WARN 站点（resolve-workers-failed 每批、report-failed 按业务速率） | 与 MA-01 的 WARN 放大不同组件不同根因（采样策略缺失 vs 泄漏驱动的洪泛）→ 分立；MA-01 修复移除其一种洪泛源，本条仍独立成立 |
| C2-005 | P3 | addJob 更新分支重排程前不复查 active：与 deactivate 并发可滞留 1 条存活 timer（空转自愈，有界） | addJob 三分支族（raced 丢注册 MA-03 / raced 契约 C2-004 / 更新分支本条）第三面：不同分支不同根因不同失效层（孤儿 timer）→ 分立；同方法共批次 |
| C2-006 | P3 | registeredCursors ArrayList 迭代无守卫 + newCursor 无 running fail-fast（现网仅 JobTimeoutCheckerImpl 注册 3 cursor，其余 4 子类零注册——复核修正后「现网不可触发」更强） | 与 C1-003 同类不同缺陷（见 C1-003 行）→ 分立；扩展点守卫同主题共批次 |

### 2.3 簇与共设计关联（非合并，验收约束）

1. **LocalJobScheduler executeJob/addJob 束**：C2-001（P1）+ C2-007 + MA-02 + MA-03 + C2-004 + C2-005 全部落于同文件两段区域。C2 复核已声明 C2-001/C2-007 修复须合并设计并回归 ：330-332 既有防护（已完成 future 同步回调覆盖 running）——本裁定升格为批次约束：六项一次重构、双向验收（锁外移不得重引 C2-007 占位滞后；占位前移不得重引锁内 invokeAsync）。
2. **F9 主题束**：MA-05（三站点吞 Error）+ C4-003（链死无 re-arm）+ C1-004（注册回滚缺失）三者判「不同」各自立案，但共享「异步续延边界 Throwable 加固」修复主题，建议同批实施、各自独立验收。
3. **rpcPoll 站点束**：MA-01（主）+ C1-004 + C7-008 + C4-008 + C4-010（前置约束）落于同一组件，MA-01 修复设计须一次性核对五个关联面，但仅 MA-01 计入本批验收。
4. **worker 容量束**：MA-06 + C7-011 + C5-004 + C4-005 共享容量账本/并发闸门区域，修复互不覆盖但须联合推演验收（防止修一处使另一处的后果面被掩盖）。

---

## 3. 独立裁定与复核提议不一致处

1. **MA-07 新裁定：C5-003 = C1-006 同一缺陷（复核提议双向缺登）**。C1 与 C5 盲审互不可读，两条为**同一文件同一字段同一机制**（CronExpression.threadLocalCal 每实例 ThreadLocal 无 remove）：C1-006 的集成路径 (a)（TriggerBuilder:33 planner 每周期新建）与 C5-003 的触发链逐点同一（同锚点 L87/L130-137/L146-156/L194）；C1-006 的路径 (b)（本地长 lifecycle 持有）为 C5-003 明示不受影响的附加有界面。主条目取 **C5-003**（重建点证据链最完整、对 P1 的反驳论证最严格）。**证据强度**：两复核状态行互相印证同一组件事实无分歧，仅视角切片不同——属盲审边界的系统性漏并，与本 campaign WI1.2 §3.1（C2-008=C7-012 同型漏并）同构。
2. **MA-07 终裁严重度 P2（记录 C1 维度 P3 异议）**：族内最高规则。C5 复核以「反模式修复价值 + 理论风险标注」维持 P2（并给出拒绝 P1 的完整论证）；C1 复核以「有界 + JDK 弱引用自愈、写不出具体危害时序」判 P3。终裁采 P2：planner 主循环的分配 churn 与 ThreadLocalMap expunge 开销是可具体化的劣化面，且修复成本极低（栈局部化）；C1 的有界性口径作为异议留档——若 7.1 裁决阶段按「理论风险降档」政策处理，可降 P3，不改变修复内容。
3. **C7-011 与 MA-06 判「不同」（对交办开放问题的裁定，非推翻）**：C7 复核注记 C4 复核未声明该对归并、交由 7.1 裁决。裁定依据见 §2.2 C7-011 行：根因（checker 门排除 vs promise 无超时）、站点、修复通道三者均不同且互不覆盖；共享的仅是后果面（额度侵蚀）。C10 映射相应保持分离（C10-002↔MA-06、C10-006↔C7-011），与 C10 复核登记一致。
4. **C2-001 与 C2-007 判「不同」+ 共设计约束升格**：与 C2 复核「两项修复须合并设计、单独子项复核」一致，本裁定确认立案层分立（根因/失效层不同），并将「合并设计」从建议升格为批次约束与双向验收面（§2.3-1）——属细化非推翻。
5. **MA-01 采纳并细化**：三方复核归并方向全部采纳；细化两点——(a) C4-001 与 C5-001 严格口径下为「同一缺陷」（同根因同失效层·资源累积双视角），C7-001 为「同根因附加失效面」（重试风暴/WARN 放大/零观测），族内统一验收面枚举如 §2.1；(b) 主条目取 C4-001（四终结条件枚举是根因的规范陈述，且 C4-010 前置约束同维相邻；C5-001 携带最完整触发链、C7-001 携带增长算术，均作为族内验收面折叠）。主条目选择系复核未指定事项，属补全非推翻。

**统计**：推翻复核提议 0 项；超出/补全复核提议的裁定 2 项（MA-07 新并 + 其严重度异议记录）；开放问题裁定 1 项（C7-011 判「不同」）；细化 2 项（MA-01 面枚举与主条目、C2-001/C2-007 共设计升格）。

---

## 4. 合并后统计

### 4.1 总量与自洽核对

- **原始缺陷条目**：43（C1×6 + C2×8 + C4×12 + C5×5 + C7×12）；C10 缺保护登记 9 条单列（非缺陷基数）。
- **合并后裁定项**：**34 = 7 缺陷族（MA-01~07）+ 27 独立立案**。
- **自洽核对**：7 族吸纳成员 16 条目（MA-01×4、MA-02~07 各×2）+ 27 独立 = **43，每条目恰好落入一处，无遗漏、无重复计数** ✓。
- **族内消号数**：9 = 16 − 7（每族保留主条目一个编号，其余 9 个 ID 消号为 cross-ref：C5-001/C7-001/C5-005、C2-002、C2-003、C2-008、C4-012、C7-002、C1-006）。
- **严重度收支**：原 P1×4 → MA-01 吸纳 3（C4-001/C5-001/C7-001）+ C2-001 独立 = 2 个 P1 裁定项；原 P2×28 → 23（6 个 P2 族吸纳 9 条 P2 + C5-005 随 MA-01 折叠；17 个 P2 独立）；原 P3×11 → 9（C2-008 随 MA-04、C1-006 随 MA-07 折叠）。2 + 23 + 9 = 34 ✓。

### 4.2 逐严重度裁定项清单（缺陷族/立案 ID × 主条目 × 标题 × 涉及子模块）

**P0**：无。

**P1（2 项）**

| ID | 主条目 | 标题 | 子模块 |
|---|---|---|---|
| MA-01 | C4-001（+C5-001/C7-001/C5-005） | rpcPoll 条目无终结驱动：注册表/句柄永久泄漏 + 无退避重试风暴 + WARN 放大 + 零观测 | nop-job-coordinator（关联 nop-job-api、nop-persistence） |
| C2-001 | C2-001 | per-job 监视器内调用外部 invoker：管理面/停机无限期阻塞 + 可构造 ABBA 死锁 | nop-job-local |

**P2（23 项）**

| ID | 主条目 | 标题 | 子模块 |
|---|---|---|---|
| MA-02 | C1-001（+C2-002） | getJobDetail 无锁读受监视器保护的可变状态（可见性竞态/撕裂快照） | nop-job-local |
| MA-03 | C1-005（+C2-003） | addJob raced 分支并发 remove 下静默丢注册 | nop-job-local |
| MA-04 | C1-002（+C2-008） | SimpleExecutionContext 逃逸 live jobParams/state（完整性 + 延迟读两面） | nop-job-local |
| MA-05 | C4-004（+C4-012） | Error 吞没族：JDK 周期抑制 + whenComplete 回调吞 Error（三站点） | nop-job-coordinator、nop-job-worker、nop-job-local |
| MA-06 | C4-009（+C7-002） | 派发 promise 无超时：容量账本永久侵蚀 + RUNNING 滞留零观测 | nop-job-worker（checker 钉死面关联 nop-job-coordinator） |
| MA-07 | C5-003（+C1-006） | CronExpression 每实例 ThreadLocal 无 remove × planner 每周期重建（stale entry + churn） | nop-job-core（vendored） |
| C1-003 | C1-003 | public scanOnce 无并发守卫逃逸单线程契约 | nop-job-core |
| C1-004 | C1-004 | register 复合注册非原子：REE 中途异常零回滚零日志 | nop-job-coordinator |
| C2-004 | C2-004 | raced 分支绕过 allowUpdate=false 契约（静默改写 spec） | nop-job-local |
| C2-007 | C2-007 | running 占位滞后：fireNow 重入递归/SOE 静默死亡/awaitIdle 误报/update 守卫失效 | nop-job-local |
| C4-002 | C4-002 | 定时触发与 job 执行共用 4 线程池无隔离 | nop-job-local |
| C4-003 | C4-003 | scanner 重排链 Throwable/拒绝即永久停摆，running 标志说谎 | nop-job-core（载体 nop-commons BindScheduledExecutor） |
| C4-005 | C4-005 | worker 任务与全部 scanner 共享平台 globalWorker 无并发上限 | nop-job-core、nop-job-worker |
| C5-002 | C5-002 | reportTaskLog 批量端点无条目数/体积上限 | nop-job-service |
| C5-004 | C5-004 | max-concurrency=0 + capacity 退化 MAX_VALUE：单周期 10 万认领高水位 | nop-job-worker、nop-job-core |
| C7-003 | C7-003 | SUSPICIOUS 单向棘轮 + 全不健康空集放大误杀在途任务 | nop-job-coordinator |
| C7-004 | C7-004 | 三表无保留/归档/清理且 delete 显式阻断 | nop-job-service（关联 nop-job-dao） |
| C7-005 | C7-005 | reconciler 固定排序窗口饥饿（DISCARD 停摆不可见） | nop-job-coordinator |
| C7-006 | C7-006 | 本地模式单次执行异常永久 FAILED 无自愈 | nop-job-local |
| C7-007 | C7-007 | 扫描线程 DB 挂起无 stall 检测零日志 | nop-job-core |
| C7-009 | C7-009 | 触发器计算持久失败无隔离（completion 5s 环 + planner 60s 环） | nop-job-coordinator |
| C7-010 | C7-010 | planner fence 过期窗口同一触发槽双执行 | nop-job-dao |
| C7-011 | C7-011 | CLAIMED 态零回收（isRunning 门矛盾 + stale 重置仅 WAITING） | nop-job-coordinator（关联 nop-job-dao） |

**P3（9 项）**

| ID | 主条目 | 标题 | 子模块 |
|---|---|---|---|
| C2-005 | C2-005 | addJob 更新分支不复查 active：滞留 1 条存活 timer | nop-job-local |
| C2-006 | C2-006 | registeredCursors 迭代无守卫 + newCursor 无 fail-fast | nop-job-core |
| C4-006 | C4-006 | 关闭路径无等待语义（shutdownNow 无 await / cancel(false)） | nop-job-local、nop-job-coordinator（载体 nop-commons） |
| C4-007 | C4-007 | maxPoolSize 配置位无效（ExecutorHelper 强制置 0） | nop-job-coordinator（载体 nop-commons） |
| C4-008 | C4-008 | manager 无 stop 生命周期，fallback 句柄空转至 JVM 退出 | nop-job-coordinator |
| C4-010 | C4-010 | cancelToken 全内建装配恒 null：取消即时感知不可达 | nop-job-api（关联 nop-job-worker） |
| C4-011 | C4-011 | 两声明池非守护线程 × shutdownNow 单次中断：JVM 无法退出 | nop-job-local、nop-job-coordinator（载体 nop-commons） |
| C7-008 | C7-008 | rpcPoll 期限用 JVM 墙钟与其余判定 DB 时钟不一致 | nop-job-coordinator |
| C7-012 | C7-012 | JobLogReporter 循环 WARN 无采样/无抑制 | nop-job-api |

计数核对：P1×2 + P2×23 + P3×9 = 34 ✓（P0×0）。

### 4.3 严重度迁移记录（相对原判级）

- **C1-006：P3 → 族 MA-07 终裁 P2**（族内最高规则，C1 维度 P3 口径作为异议留档，见 §3.2）。唯一判级迁移。
- 其余全部裁定项终裁严重度 = 主条目原判级（含 C2-008 P3 随 MA-04 取 P2、C5-005 P2 随 MA-01 折叠——两例为归并吸收而非独立迁移）。

### 4.4 C10 缺保护登记 → 缺陷族映射（修哪族需补哪测试）

| C10 登记 | 宿主（映射后） | 修复该族须补的测试面 |
|---|---|---|
| C10-001 | **MA-01** | getJobStatus 失败注入（现零注入）、activeCount()/entries 断言、三终结面（失败预算/deadline 兜底/DB 回传）各自回归；含 C5-005 驻留体积断言 |
| C10-002 | **MA-06** | 未决 promise 用例（现零用例）、sumReservedCost 释放方向断言（现仅占用方向）；钉死面处理前置：plan 340 契约测试 + JobTimeoutCheckerImpl:497-501 注释修订（若走 checker 侧） |
| C10-003 | C2-001 | 同步阻塞型 invoker × 管理 API 响应性（getJobState/getJobDetail/awaitIdle/deactivate 锁外行为锚点）；裁定子代理抽验：可落为单元测试（invoker 构造器注入形态） |
| C10-004 | C2-007 | fireNow 自重入递归/SOE 逃逸链零测试 → 补占位前移双向锚点（含新窗口：占位已置但 invokeAsync 抛异常） |
| C10-005 | C4-003 | startScanning/stopScanning 生命周期烟测 + Error 注入 running 真实性断言 + 满载拒绝注入（抽验可落单元测试）；MA-05 三站点的 Throwable 注入测试另计（F9 主题束内各站点独立验收） |
| C10-006 | C7-011 | CLAIMED 肯定性回收断言（worker 存活 × 执行超时 → TIMEOUT；时钟字段为 **startTime**，R7 勘误） |
| C10-007 | C2-004 + **MA-03** | 并发双注册窗口（raced 分支 allowUpdate 契约 + 静默丢注册），概率性形态建议包内 seam——一套并发注册测试同盖同分支两族 |
| C10-008 | C5-002 | 上限三面断言：超限拒绝 / 默认限值不误伤（batch=2 为最小对照）/ 单条超长 |
| C10-009 | （无宿主缺陷族——测试质量本体登记） | 两处「固定 sleep 窗口 + 缺席断言」（TestLocalJobScheduler :142/:163/:193）位于批次 2 触达面内：随 LocalJobScheduler 束修复改为 deadline 轮询/有界忙等，不以独立工单立项 |

### 4.5 跨单元关联与 WI 1.1–1.3 台账对账结论

**对账结论：零重复立项。** 逐条比对 cra-index.md WI 1.1（10 P1）+ WI 1.2（10 P1 + P0 MA-01）+ WI 1.3（4 P1）全部 24 条 P1 及其 P2/C10 登记：本单元 34 个裁定项无一同缺陷重复。结构性原因：审计代码空间不相交（nop-stream-\* vs nop-job/\*）；nop-job 的 rpcPoll 特性 2026-08-28（commit 5310efaf97）引入，晚于 WI 1.1–1.3 审计窗口与全部 nop-job 历史审计，无跨单元同族先例。

**真实跨单元关联（3 条，单列，不重复立项、不计入本单元统计）**：

1. **平台共享 globalWorker 池共租**：本单元 C4-005（+C4-003 扫描体、C5-004 认领爆发）与 WI1.2 **MA-06**（Debezium 引擎无限阻塞任务常驻 globalWorker，cra-index P1 台账 open）为同一平台池（core=4/max=30/queue=10000）的两个独立共租方使用面缺陷，各自立案正确；修复须协调——任一共租方的无界占用互相放大，WI1.2 MA-06 的「引擎线程不落 globalWorker + 占池 fail-fast」修复设计应将 nop-job 列为共租方回归面，或合并评估平台级专属执行器拆分（归 §5 批次 4 与 WI1.2 批次协同）。
2. **nop-commons 平台类为根载体（3 处，均按「nop-job 使用面立案、修复可上提平台」处理）**：C4-003 链死机制载体 `BindScheduledExecutor`（nop-kernel/nop-commons，catch Throwable→completeExceptionally 无 re-arm——平台级修复将惠及全部 fixed-delay 使用方）；C4-007 `ExecutorHelper` 强制 maxPoolSize=0（平台级配置语义，其他经此建池模块同受「max 配置位无效」影响）；C4-006/C4-011 `DefaultScheduledExecutor`/`ThreadPoolConfig`（等待语义与 daemon 默认值）。WI 1.1–1.3 三个单元均未登记这四个类（grep 零命中），无重复；上提平台修复时需另立跨单元工作项。
3. **台账编号撞名澄清（非关联缺陷）**：WI1.1-C7-011（standby 降级不取消 checkpoint persist）≠ nop-job-C7-011（CLAIMED 零回收）；WI1.1-C4-009（OpsJobManager submit 孤儿 coordinator）≠ nop-job-C4-009（派发 promise 无超时）。单元前缀命名规范预期内，9.1 对账时防误并。

---

## 5. 修复批次划分建议（仅建议不做决定）

| 批次 | 建议内容 | 理由与验收要点 |
|---|---|---|
| 批次 1（P1 先行） | **MA-01**（rpcPoll 终结族全失效面） | 唯一机制性 P1 缺陷族；设计须一次核对 §2.3-3 四个同点关联面（C4-010 前置约束、C7-008 时钟源、C1-004 回滚、MA-05 pollOnce 站点）；测试 C10-001 先红后绿 |
| 批次 2（LocalJobScheduler 束） | C2-001（P1）+ C2-007 + MA-02 + MA-03 + C2-004 + C2-005 + C10-009 加固 | 同文件 executeJob/addJob 两段区域一次重构（§2.3-1 共设计约束、双向验收）；测试 C10-003/004/007 同批落地；概率性测试建议包内 seam |
| 批次 3（异步续延加固束） | MA-05（三站点）+ C4-003 + C1-004 | F9 主题统一实施（§2.3-2），三者独立立案独立验收（吞 Error 加固 / re-arm+running 真实性 / 注册回滚+兜底 complete）；测试 C10-005 |
| 批次 4（worker/容量账本束） | MA-06 + C7-011 + C5-004 + C4-005 | 容量账本与并发闸门同区域联合推演（§2.3-4）；MA-06 修复位在 worker 侧（钉死约束）、C7-011 修复位在 checker 门——分立验收；C4-005 与 WI1.2 MA-06 跨单元协调；测试 C10-002/006 |
| 批次 5（coordinator 自愈正确性束） | C7-003 + C7-005 + C7-009 + C7-010 | 自愈机制自身的正确性四缺陷（误杀/饥饿/无隔离重试环/fencing 窗口），同组件可共享测试基建 |
| 批次 6（边界上限与扩展点守卫束） | C1-003 + C2-006 + C5-002 + C7-004（政策项） | 「声明契约无运行时防线 + 上限护栏」主题；C7-004 涉及保留策略/DDL/owner doc，可能需单独立项 |
| 批次 7（资源卫生与配置诚实性束，P3 为主） | MA-07 + C4-006/007/008/010/011 + C7-008 + C7-012 | 低风险卫生项；C4-006/007/011 若上提 nop-commons 修复则转跨单元工作项（§4.5 关联 2）；C7-008 建议随批次 1 的 computeDeadline 重写一并落 DB 时钟 |

---

## 附录：裁定抽验记录（本裁定新增证据）

1. **C5-003 与 C1-006 同一性**：两文件条目正文比对——锚点行号集合（CronExpression L87/两构造器/L194）完全一致；C1-006 路径 (a)「TriggerBuilder.buildTrigger 每周期 new CronExpression」= C5-003 重建点「TriggerBuilder:33 ← JobPlannerScannerImpl:223-227」；分歧仅在路径 (b)（本地长 lifecycle）与判级口径（P3 有界 vs P2 反模式）。live 归属核实：`nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`。
2. **C7-011 vs MA-06 修复互不覆盖**：C10-002 复核已独立验证 checker 侧 RUNNING 兜底被 plan 340 契约测试（testLongRunningTaskWithoutTimeoutNotKilled，:614-639）+ JobTimeoutCheckerImpl:497-501 声明注释双向钉死；C7-011 的修复面是 isRunning 门（L470-472）对 **CLAIMED** 态的排除——两修复分别作用于 checker 的不同状态门与 worker 派发点，无覆盖关系。
3. **跨单元 grep**：`globalWorker` 在 WI1.1–1.3 审计目录仅 WI1.2 命中（其 MA-06 即本裁定关联 1 对端）；`ExecutorHelper`/`BindScheduledExecutor` 在三个 stream 单元目录零命中；`BindScheduledExecutor.java`/`ExecutorHelper.java`/`ThreadPoolConfig.java`/`DefaultScheduledExecutor.java` 实测位于 `nop-kernel/nop-commons`（平台模块，非 nop-job）。
