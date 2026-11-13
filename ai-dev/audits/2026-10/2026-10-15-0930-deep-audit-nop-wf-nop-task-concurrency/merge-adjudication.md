# nop-wf + nop-task 并发审计 — 跨维度合并裁定（7.1）

- 审计单元：`nop-wf`（10 jar 子模块）+ `nop-task`（10 jar 子模块）
- 裁定人：独立合并裁定子代理（与各维度初审/复核均不同角色）
- 日期：2026-10-15；分支：concurrency-audit-remediation；HEAD `be0cbbb2a3`
- 输入：本目录 C1/C2/C3/C4/C5/C7/C8 七个维度文件最终保留项 + C10 复核后保留的 14 条缺保护登记；各维度复核结论节及其预登记 cross-ref；跨单元对账基准 `ai-dev/audits/cra-index.md`（WI 1.1–1.4 台账）
- 裁定口径：
  - **同一缺陷**：同一根因 + 同一失效层 → 取一为主条目，其余交叉引用；
  - **同族相邻**：同一根因 + 不同失效层 → 合并为单一缺陷族/单一修复工单，修复验证须同时覆盖各失效面；
  - **不同**：保留各自立案（可 cross-reference）；
  - 同点跨维度判级差：合并取族内最高，记录异议；
  - 推复核提议须给出证据（见 §3）。

---

## 1. 裁定方法与输入清单

### 1.1 输入：各维度最终保留条目 × 严重度

| 维度 | 保留条目数 | P1 | P2 | P3 | 备注 |
|------|-----------|----|----|----|------|
| C1 共享可变状态 | 10 | 2（C1-001/009） | 6（C1-002~007） | 2（C1-008/010，C1-010 复核降级 P2→P3） | |
| C2 锁与同步 | 13 | 1（C2-001） | 3（C2-003/005/011） | 9（C2-002/004/006 复核降级，C2-007/008/009/010/012/013） | |
| C3 异步链路 | 15 | 4（C3-001/002/011/012） | 6（C3-003~007/013） | 5（C3-008/009/010/014/015） | |
| C4 线程池 | 9 | 3（C4-001/002/008） | 3（C4-003/004/007） | 3（C4-005/006/009） | |
| C5 内存有界性 | 12 | 2（C5-001/003） | 5（C5-002/004~007） | 5（C5-008~012） | |
| C7 长时运行 | 17 | 1（C7-001） | 12（C7-002~012/014） | 4（C7-013/015/016/017） | 勘误：C7 文件判定计数行写「P2×11、P3×5」，与其最终保留项表（P2×12、P3×4）不一致；表与任务书一致（C7-002~012/014 为 P2），本裁定按表口径 |
| C8 背压流控 | 17 | 0 | 11（C8-001~008/013/014/015） | 6（C8-009~012/016/017） | |
| **合计（缺陷本体）** | **93** | **13** | **46** | **34** | |
| C10 测试保护（缺保护登记） | 14 | 0 | 14（全部 P2 缺保护，与缺陷本体分开计数） | 0 | 覆盖 13 个 P1 缺陷 ID + 3 个高价值 P2 同批族 |

P0：0（全部 8 个维度文件一致，无 P0；即时通道显式空执行）。

### 1.2 裁定方法

1. 逐条提取 93 条缺陷本体 + 14 条 C10 登记，建立「根因 × 失效层/站点 × 代码点」三元组；
2. 以各维度复核预登记的 cross-ref 为起点，独立重验每一组疑似重合（读条目正文比对代码点、根因表述与修复方向），可推翻；
3. 追加扫描复核盲区（各维度互为盲审，跨维度同点未被登记处）——共补出 4 组跨维度同点归并（见 §3）；
4. 按 §0 口径输出三态判定；每条保留条目恰好落入一个缺陷族或独立立案（§2、§4 自洽核对）；
5. 对照 cra-index.md WI 1.1–1.4 台账逐条核对无跨单元同缺陷重复立项（§4.4）。

---

## 2. 合并裁定表

### 2.1 缺陷族（MA-01 ~ MA-23，共 23 族，吸收 62 条保留条目）

| 族 ID | 判定 | 成员条目（原判级） | 主条目 | 族最终严重度 | 修复验证须覆盖的失效面 |
|-------|------|-------------------|--------|-------------|----------------------|
| **MA-01** | 同族相邻（同一对写者 TaskImpl.java:159 SUSPENDED 游离写 × TaskRuntimeImpl.cancel 监视器判定的相反交错；修复必须单一原子设计） | C1-001（P1，主，kill 静默丢失）、C2-001（P1，同点锁正确性视角）、C7-016（P3，同点长时运行视角）、C1-009（P1，相反交错：KILLED 后被 SUSPENDED 裸覆写复活） | C1-001 | **P1**（同点判级差：C7-016 独立校准线为 P2 下限，异议记录，族取最高） | ①SUSPENDED 转移纳入 `synchronized(taskState)` + 终态前置检查（kill 丢失面）；②resume 非终态确认后显式重置 ACTIVE 落盘（复活面）；③不回归四个既有终态 driver（TaskImpl.java:186-258）监视器语义；④连带裁定 round3 §2.4-1「挂起任务被 timeout-cancel 记 KILLED 而非 TIMEOUT」分类问题；⑤C2 复核锁序图复核新同步结构 |
| **MA-02** | 同族相邻（同根因：每请求新建 WorkflowImpl/快照读→判定→写、无跨请求互斥、仅保存点乐观锁；五个站点） | C3-001（P1，主，exec-group 组迁移丢失）、C3-011（P1，末段并发完成实例滞留 ACTIVATED）、C3-012（P1，join 并发到达双实例 and+or）、C1-002（P2，同 step 并发 action 副作用先于保护双跑）、C3-015（P3，兄弟子流结束乐观锁回滚输家整个事务） | C3-001 | **P1** | ①and/seq/vote 组完成判定+迁移原子化（vote 快照口径已复核修正）；②流程自动终结 checkEnd 原子化；③join 实例创建原子化（须同盖 and+or 两型，orm 无 (wfId,stepName,joinGroup) 唯一约束）；④用户 action 路径占位/唯一约束或声明幂等边界；⑤子流结束回滚重放面；⑥C1-010 覆盖约束——wf 级守卫补齐须覆盖 `saveWfRecord` 全部 6 个调用点 |
| **MA-03** | 同族相邻（同根因：SyncTaskStepWrapper 阻塞等待无超时无取消响应 + sync×executor 组合无构建期互斥；饱和死锁为其具体化实例） | C3-002（P1，主，sync+executor 同池嵌套死锁）、C4-002（P1，同点池治理视角，TaskQueue grow-then-queue 永不拒绝）、C2-010（P3，通用无超时 sync 等待面，F5 唯一不达标项） | C3-002 | **P1** | ①构建期 sync×executor 互斥/同池嵌套检测（fail-fast）；②syncGet 带时限并接入 cancel token（同时闭合 C2-010 通用挂死面）；③pool-size=1 小池死锁回归测试（C10-006 方案 b）；④注意 timeout 包装位于 sync 内侧的模型级缓解不文档化不改变判定 |
| **MA-04** | 同族相邻（同池面：业务体+簿记恒上共享 globalTimer，实际 4 线程、DelayedWorkQueue 无界静默降级） | C4-001（P1，主，retry 延迟体调度 globalTimer）、C4-008（P1，executor 隔离覆盖不了延迟路径+saveState 簿记 ORM 写上 timer）、C3-013（P2，delay 续延递归 execute 在 timer 线程） | C4-001 | **P1**（判级差 C3-013 P2 异议记录：C3 自限 delay 半边，族按使用侧错池 P1） | ①延迟到期后重试体/续延改投隔离执行器（globalWorker 或步骤显式 executor），schedule 仅做定时唤醒；②saveState 簿记移出 timer 线程；③须覆盖 SequentialTaskStep delay 续延半边；④与平台单元 globalTimer 容量发现（maxPoolSize 旋钮无效残留边界）协同；⑤TestRetryDelayExecutorIsolation（C10-007）断言执行线程身份 |
| **MA-05** | 同一缺陷（同点：findDueActivatedSteps/findRemindActivatedSteps 无 LIMIT 全量载入单会话） | C5-001（P1，主，无 LIMIT+毒步骤 dueTime 永不清）、C8-001（P2，背压口径同点） | C5-001 | **P1**（判级差 C8-001 P2 异议记录：C8 侧以不死锁/不溢出/不丢数辩护 P2；按同点取最高规则定 P1） | ①查询族 limit+orderBy 分页游标（due 与 remind 同构同修）；②单轮批次上限/时间预算；③扫描会话内存峰值（单 ORM 会话持有全量行）；④毒步骤不收敛半边与 MA-06 联合修复（失败计数/退避/dueTime 推进）；⑤C10-008 查询形状+收敛性测试 |
| **MA-06** | 同族相邻（同根因：扫描失败/跳过项不退出工作集且信号不可见；四个失效面） | C7-002（P2，主，持续失败无抑制每周期重放 ERROR）、C8-002（P2，失败无退出+DEBUG 静默跳过，触发面已收窄为 VFS 热更新漂移）、C7-003（P2，DEBUG 跳过+job 缺省 disabled 双重盲区；复核精化：纯缺 dueAction 因 :389 双条件门控根本不入结果集）、C8-011（P3，remind 失败无退避；当前 remindTime 无引擎写入点、触发面不可达） | C7-002 | **P2** | ①失败计数/下次重试时间+指数退避+连续 N 次失败升级终态/告警态（due+remind 两半区）；②skip 分支升 WARN；③due-backlog gauge（与 C8-015 协同落地）；④模型加载期校验 dueTimeExpr∧dueAction 成对（堵 VFS 漂移路径）；⑤job 缺省 disabled 的部署盲区以 runbook/文档声明；⑥修复不得破坏 testScanDueTasksIgnoresCurrentStepStatusRace 既有容错 |
| **MA-07** | 同一缺陷（同点：transferActorsAsync 无条数上限；C5 内存视角与 C8 背压视角判级一致均 P2） | C5-002（P2，主）、C8-003（P2，同点） | C5-002 | **P2** | ①findActivatedStepsByOwner limit/分页（与 MA-05 共享 DaoWorkflowStore 查询族修法）；②请求级 maxBatch 配置超限显式报错；③防重入（同一 fromUser 并发转办各自物化全集）；④部分成功语义（successCount+failedItems）为已声明边界，保留不回退 |
| **MA-08** | 同一缺陷（同点：global 闸门强引用注册表 × 动态 keyExpr 键空间持久无界） | C5-003（P1，主，已知未修复——plan 364 [05-04] 声明边界之上的残留面）、C8-004（P2，声明边界残留面视角，WARN-once 不持续） | C5-003 | **P1**（判级差 C8-004 P2 异议记录；**前置 owner 表态**：若裁定「动态 global key 为不支持用法」并落地构建期拒绝，本族降为文档声明缺口、可降级） | ①键空间治理（准入拒绝或 lastAccess+空闲淘汰，不破坏 permit 池完整性）；②注册键含输入直通面（TaskStepExecution.initInputs → `keyExpr="${input.*}"`）；③修复必须同步改写 `globalGates_strongRefRegistry` 反向钉死测试（C10-009，保留同 key 同实例断言+新增上界断言）；④与 MA-09 观测面一并落地 |
| **MA-09** | 同族相邻（同根因：闸门注册表无任何持续可观测通路） | C7-008（P2，主，warn-once 不可操作+stats 零生产消费）、C8-005（P2，stats API 零消费+meter 无闸门维度+acquire 成败计数无人读）、C5-010（P3，stats 全量 HashMap 拷贝随动放大，当前无生产调用者） | C7-008 | **P2** | ①warn-once 升级为周期性指标（registry.size gauge）；②SemaphoreStats/RateLimiterStats 注册进 GlobalMeterRegistry 或只读端点；③stats 导出改分页/聚合摘要（闭合 C5-010 放大面）；④随 MA-08 键空间治理一并收敛 |
| **MA-10** | 同族相邻（同根因：闸门参数首配置固化、零失效路径；参数静默失效与其告警面两侧） | C8-008（P2，主，cacheKey 不含 version+无 remove API+模型热更新不触注册表）、C7-017（P3，frozen WARN 每调用无抑制+semaphore 侧对称零信号） | C8-008 | **P2** | ①模型缓存失效钩子同步清除闸门（或在用计数归零后替换）或文档声明「限流参数变更需重启」；②frozen WARN 去重（按 cacheKey 一次性/计数型）+semaphore 侧补对称告警；③C2-013 的 maxPermits 固化半面并入本族验证 |
| **MA-11** | 同族相邻（同根因：非重入信号量嵌套同 key 获取按深度耗 permit；显式共享 key 与缺省配置两条暴露面） | C2-005（P2，主，显式共享 keyExpr 递归嵌套）、C2-011（P2，零显式配置可达：global×自递归 call-task（key 形如 `taskName:@main`）+call-step 同 stepRt 默认 key=stepPath） | C2-005 | **P2** | ①执行期 key 重入检测（构建期或 stepRt attribute 记录持有 key 集，重入即确定性报错）；②覆盖 CallTaskStep/CallStepTaskStep 两条缺省路径（重入检测 key 以实际 `taskName:@main[/S]` 形成为准）；③措辞校准：maxWait 有限，后果为确定性超时/长阻塞非严格永久死锁；④C10-002 嵌套获取测试 |
| **MA-12** | 同一缺陷（同点：throttle maxConcurrency/maxWait 无构建期校验，与 rateLimit/timeout/retry 硬失败清单不对称） | C8-007（P2，主，maxConcurrency=0 静默误配先等待后必拒绝；精度修正：负值崩在首次执行期、maxWait<0 有空位仍成功）、C2-013（P3，同点校验缺口+xdef 仅 `!int`；其 maxPermits 固化半面 cross-ref MA-10） | C8-007 | **P2** | ①`maxConcurrency<=0`/`maxWait<0` 构建期硬失败（ERR_TASK_STEP_CONFIG_INVALID 同码），xdef 注释 maxWait 单位毫秒；②与 MA-11 重入检测、MA-10 固化告警一次设计（C2 复核建议的 throttle 闸门整体裁决在批次层面执行，见 §3-D）；③顺带覆盖 rateLimit 已修面回归 |
| **MA-13** | 同族相邻（同根因：catch(Exception) 边界放行 Error，清理/终结/释放全部旁路；三个站点） | C8-006（P2，主，throttle 许可永久泄漏→该 key 永久全拒）、C8-013（P2，executor/runOnContext 回调 Error→promise 悬挂、任务永驻非终态、清理/终态 driver 全跳过；实例 A 有平台层断连日志但悬挂零信号）、C8-016（P3，Error 穿透清理残留矩阵：seam cleanup/meter/timer/fork 派发） | C8-006 | **P2** | ①ThrottleTaskStepWrapper 同步路径改 catch(Throwable)/finally 归还；②Executor/RunOnContext 回调 promise 对 Error 必然终结；③task/step seam 清理与 timer 取消的 Error 面矩阵逐项闭合；④C10-013 Error 注入测试（AssertionError 注入 + permit 回满断言 + promise 5s 终结断言） |
| **MA-14** | 同族相邻（同根因：timeout/kill 信号与 body 执行生命周期解耦——协作式取消无中断点、竞速 winner 与被竞速 promise 无生命周期绑定；四个失效面） | C3-005（P2，主，超时后幽灵 body 续延写 scope/状态行）、C7-011（P2，不合作同步体钉死线程、timeout() 不返回、TIMEOUT driver 永不驱动）、C8-014（P2，winner 终结即归还 permit、僵尸 body 继续占线程下游资源击穿上界）、C4-009（P3，已排程延迟重试体不可撤回、终态后仍完整执行一次副作用） | C3-005 | **P2** | ①timeout/kill 对 executor 底层 Future 施加取消/中断语义传导（bindCancelToken 到 body 内层链）；②同步体契约文档「必须自行响应 cancel 或自带超时」+XPL loop 边界 token 检查注入（可选）；③permit 归还时机与 body 存活解耦评估；④延迟重试 schedule 接 bindCancelToken（对齐 DelayTaskStep 正例）；⑤验证须覆盖 async 幽灵/sync 钉死/许可早释/重试不撤四面 |
| **MA-15** | 同一缺陷（同点：TaskStepStateBean succeed/fail/setStepStatus 无锁 check-then-act，与 task 级监视器收敛不对称） | C1-004（P2，主，KILLED/EXPIRED 行携带 resultValue 混合状态）、C2-003（P2，同点锁正确性视角）、C3-006（P2，COMPLETED 行携带 exception 诊断矛盾；fail 的 exception() 写无二次校验） | C1-004 | **P2** | ①step 终态驱动收敛到 `synchronized(stepState)` 原子序列（或 fail() 锁内复查 isDone）；②覆盖全部 8 个写点（TaskStepExecution:349/386-389/399-400、TaskStepHelper:301/309/352）；③retry 路径 do/while 与 delay-schedule 回调两驱动均须验证；④与 MA-01 修复共享监视器设计评审（C2 锁序图） |
| **MA-16** | 同一缺陷（同点：SleepTaskStep 同步轮询占线程；C8-010 的 (b) 半面 cross-ref C2-012 wrap 栈序族） | C7-012（P2，主，sleep 占线程+时长无构建期上限校验）、C8-010（P3，(a) sleep 阻塞对照 delay 非阻塞同点；(b) throttle 排队在 timeout 计时之外→cross-ref C2-012） | C7-012 | **P2** | ①构建期 sleep 上限校验（对齐 timeout/retry/rate-limit 硬失败清单）；②sleep 改造为 delay 异步形态或文档强指引；③(b) 半面随 C2-012（wrap 栈序）一并评估，不重复立案 |
| **MA-17** | 同族相邻（同根因：WorkflowImpl 实例并发契约未声明且 javadoc 按「工作流实例」表述与对象级实现失真） | C1-003（P2，主，executing/commandQueue/steps 零同步、封闭前提未声明）、C2-007（P3，同点视角，纯隐式约束）、C3-010（P3，delayExecute javadoc 实例级表述 vs 对象级实现——MA-02 的认知根源） | C1-003 | **P2**（判级差 C2-007 P3 异议记录） | ①类 javadoc 与 `IWorkflowManager.getWorkflow` 契约处声明「实例线程封闭、禁止缓存共享」，修正 WfRuntime.delayExecute javadoc 为对象级语义并指向 MA-02 缺口；②若未来引入实例缓存须改并发容器+CAS 抢占（设计红线登记）；③本族为文档/契约级修复，是 MA-02 设计文档的必备组成 |
| **MA-18** | 同族相邻（同根因：计数类表达式无构建期上界校验，与 LoopTaskStep.maxCount / timeout 负值硬失败先例不对称） | C5-004（P2，主，fork-n count 无上限+ArrayList 按值即时预分配=单请求 OOM 通道）、C5-007（P2，loop-n 无 maxCount+负 step 反向计数/整数回绕无防护+每迭代 2 条 SQL 写放大） | C5-004 | **P2** | ①统一构建期计数校验清单：fork-n count 上限、loop-n max-count 新增、`|end-begin|/|step|` 超限硬失败、防 step 下溢；②预分配改增量；③与 [02-06] 硬失败先例同码 |
| **MA-19** | 同族相邻（同根因：nop-task「队列支持」声明未接线；死 bean 与死模块两个残留物，一个清理决策覆盖） | C4-005（P3，主，nopTaskExecutionQueue 死 bean+专属 ThreadPoolConfig 无效果）、C4-006（P3，nop-task-queue 空模块）、C8-012（P3，同点= C4-006：5 行 ITaskQueue 零消费者+owner doc「队列支持」声明失真） | C4-005 | **P3** | ①删除或落地（对接 IBlockingQueue+QueueOverflowPolicy）；②owner doc nop-task.md L90 声明修正；③queueTask 前置 active 校验（若保留） |
| **MA-20** | 同一缺陷（同点：addStepCleanup synchronized 写 × runStepCleanups 非同步 drain 同一 ArrayList；addStepCleanup 生产零调用、消费侧 live） | C1-007（P2，主）、C2-004（P3，同点视角，降级理由=零生产调用方与 C2-009 校准一致） | C1-007 | **P2**（判级差 C2-004 P3 异议记录：公开扩展点 + 消费侧 live，生产者出现即触达） | ①runStepCleanups 同步化或 stepCleanups 改 ConcurrentLinkedQueue+drain；或删 synchronized+接口 javadoc 钉死单线程契约；②与 TaskRuntimeImpl.cleanups（Cancellable 正例）防护水平对齐说明 |
| **MA-21** | 同一缺陷（同点：taskVars 懒初始化 LinkedHashMap+setTaskVar 无同步；生产零调用） | C1-008（P3，主）、C2-009（P3，同点视角，对照 TaskRuntimeImpl:69 并发容器惯例缺口） | C1-008 | **P3** | ①直接初始化 ConcurrentHashMap（对齐 :69 惯例）；②与 taskVars 持久化暂缓裁定联动处理 |
| **MA-22** | 同一缺陷（同点：WorkflowEngineImpl runSourceWithRetry `Thread.sleep`（:893）内联于 @BizMutation 请求事务 + computeRetryDelay（:922-933）封顶语义缺失。**两组复核均未互登 cross-ref，系本裁定补出的同点归并**） | C3-007（P2，主，maxRetryDelay 对固定 delay 不封顶+事务持有）、C7-005（P2，同点，exponential 退避 maxRetryDelay=0 缺省不封顶+javadoc 半声明） | C3-007 | **P2** | ①computeRetryDelay 对固定 delay 同样应用 maxRetryDelay 封顶（对齐 task 侧 RetryPolicy 两分支均封顶）；②检测活动事务时降级不重试或改定时器异步退避（对齐 task 侧 scheduledExecutor 形态）；③回归 wf 步骤重试全链（autoTransition 触发面） |
| **MA-23** | 同一缺陷（同扫描面：WfTaskScanner 无任何 fencing/互斥；C7-001 的多节点双跑机制已subsume C1-005 的 remindCount 丢失更新与单节点重叠面） | C7-001（P1，主，多节点 due source 双跑副作用先于状态守卫+remind 双发）、C1-005（P2，remindCount RMW+扫描入口无重叠互斥视角） | C7-001 | **P1**（判级差 C1-005 P2 异议记录：C7 论证更强——多节点同秒 cron 下双跑窗口为整个 action 执行时长，接近确定性） | ①扫描抢占（条件 UPDATE 置处理中+owner+期限）或分布式调度锁或幂等键（wfId+stepId+dueTime）去重；②覆盖 due+remind 两路径与单节点重叠形态；③remindCount 改 DB 原子自增；④修复不得破坏引擎 ERR_WF_NOT_ALLOW_ACTION_IN_CURRENT_STEP_STATUS 现役兜底；⑤C10-011 双 scanner 竞速测试（触发次数恒==1、remindCount 终值恒==轮数）；⑥与 nop-job 部署形态（local scheduler 无集群锁）联动声明 |

### 2.2 独立立案（31 条，无同根因可归并对象；cross-reference 已注明）

| 条目 | 严重度 | 一句话摘要 | 模块/簇 | 关联 |
|------|--------|-----------|---------|------|
| C1-006 | P2 | saveTaskState 不在条带锁内（与 saveStepState 不对称），跨 runtime 并发终态保存仅 DB 乐观锁收敛，裸 ORM 错误冒泡+内存态漂移 | nop-task-dao | cross-ref MA-01（同 saveTaskState 链，修 MA-01 时不得回归）/C7-014 相邻 |
| C1-010 | P3 | wf 级 transitToStatus 零回退守卫（2/4 实现零守卫），live 无回退驱动路径，防御纵深缺失 | nop-wf-dao | cross-ref MA-02（修复覆盖约束：saveWfRecord 6 调用点） |
| C2-002 | P3 | taskState/metrics 非 volatile 且 onCancel 注册先于 setTaskState 写——latent 可见性缺口，live 无可具体化触发 | nop-task-core | cross-ref MA-01（同 cancel 路径） |
| C2-006 | P3 | 条带锁/taskState 监视器临界区内含 DB 往返+模型组件加载+JSON 序列化，锁持有时长由最慢 I/O 决定（主体属已声明设计边界） | nop-task-dao/core | cross-ref C7-013、C5-008（同 save 路径成本面） |
| C2-008 | P3 | CHM computeIfAbsent mapping 内 LOG.warn，同 bin 注册被日志 I/O 拖带（低频一次性） | nop-task-core | — |
| C2-012 | P3 | wrap 栈序使 throttle permit 横跨整个 retry 链（含退避与 timeout 预算），global 闸门额度被单实例重试蚕食 | nop-task-core | cross-ref MA-16（C8-010(b) 半面）、MA-11/MA-12（throttle 设计批次） |
| C3-003 | P2 | 图步骤 enter∩wait 双调度无一次性守卫与构建期校验，body 副作用双跑、结果覆写 | nop-task-core | — |
| C3-004 | P2 | executor 并行步骤体无上下文重投递，ThreadLocal 依赖静默失效或串号（runOnContext 缺省 false，thenOnContext 零调用方） | nop-task-core | — |
| C3-008 | P3 | wf source 返回未决 CompletionStage 被原样透传为值，异步异常无消费者 | nop-wf-core | — |
| C3-009 | P3 | due 扫描整批单 session/事务横跨任意业务动作，长事务持连接；双触发由引擎守卫兜底 | nop-wf-scheduler | cross-ref MA-05/MA-06（扫描治理同一重构面：动作移出扫描事务） |
| C3-014 | P3 | svcCtx=null 时主 stepRt 取消令牌为 null，取消传播链断裂：任务不可取消且以 COMPLETED 收场 | nop-task-core | — |
| C4-003 | P2 | wf 扫描 job.yaml `invoker.bean=wfTaskScanner` 与实际 bean id 失配，启用即每次触发 ERR_JOB_BEAN_NOT_FOUND→FAILED 永久停摆 | nop-wf-scheduler | **已知未修复**（2026-09 code-review-full P2-3.1-001/AU-01/SM-10 同案，见 §4.4；修复验证 C10-010） |
| C4-004 | P2 | executor bean 解析链保留名遮蔽+无 instanceof 强转——静默错池或迟到 CCE | nop-task-core | cross-ref MA-03（错池放大死锁不可见性） |
| C4-007 | P2 | wf 扫描 job 链路一次失败即 FAILED 永久停摆、一次挂死即 RUNNING 永久占位；扫描集无界+逐项内联无超时 | nop-wf-scheduler | **形态 A = nop-job-C7-006 跨单元同面（单列不重复计数）**；形态 B/使用面本单元新发现；修复验证 C10-012 |
| C5-005 | P2 | loop/fork items 全量物化驻留整个循环期+每迭代 saveState 全量重序列化（O(N²) 序列化量） | nop-task-core/dao | cross-ref C7-013、C5-008 |
| C5-006 | P2 | wf 步骤记录读路径 ORM 全集物化（无 SQL 侧过滤/下推），单 wf 账本跨请求无归档无上界 | nop-wf-dao/core | cross-ref MA-02（同全集读物理面，失效类不同）、MA-05/MA-07（放大面）、C7-007 |
| C5-008 | P3 | 状态序列化先全量物化再判列宽+secret 过滤二次 parse/serialize（瞬态 3× 载荷） | nop-task-dao | cross-ref C5-005、C2-006 |
| C5-009 | P3 | 参与者解析按角色/部门/组 ORM 关系全量物化用户集合 | nop-wf-service | — |
| C5-011 | P3 | getActionRecords 单步无 LIMIT 全量载入+wf 级 N+1（无生产调用者，模型脚本可达） | nop-wf-dao | cross-ref MA-05/MA-07：同查询族 LIMIT 修法一并覆盖 |
| C5-012 | P3 | WfAiHelper 将 LLM 输出任意键直写 wf 全局变量 KV 表，键空间无界无 schema 门禁 | nop-wf-ai | **已知未修复**（2026-09 AU-10/P2-3.1-011 同点位安全面，键空间维度新增，见 §4.4） |
| C7-004 | P2 | dueAction 同步阻塞可挂死扫描线程并级联耗尽 pool-size=4 共享本地调度池，无健康上报 | nop-wf-scheduler | cross-ref MA-05/MA-06（扫描治理：deadline/熔断同批设计） |
| C7-006 | P2 | dueTime/到期扫描全链墙钟：回拨延迟升级、前跳集中触发无防护 | nop-wf-core/dao | cross-ref MA-23（风暴放大其双跑面） |
| C7-007 | P2 | 双引擎 DB 账本只增不删，无内置保留/归档路径（F4 DB 侧） | nop-wf-dao/nop-task-dao | cross-ref C5-006（读放大联动） |
| C7-009 | P2 | 崩溃后在途任务无恢复看门狗：ACTIVE 行永久滞留、workerId 从不赋值、无 fencing 基础 | nop-task-dao/core | cross-ref C7-014（共享 workerId/租约修复基建，缺陷分立）、C7-010 |
| C7-010 | P2 | SUSPENDED 任务（含 resume-when 已满足）无自动续跑与滞留区分监控 | nop-task-core | cross-ref C7-009（滞留可观测同族面） |
| C7-013 | P3 | 循环体每迭代 saveState 的 DB 写放大+每次 save 重复模型解析查找（断点契约内优化候选） | nop-task-core | cross-ref C5-005、C2-006 |
| C7-014 | P2 | task 引擎无执行级 fencing：同一 taskInstanceId 并发 resume 双执行（监视器随实例对象失效） | nop-task-core | **与 C1-009（MA-01）裁定分立**（根因不同：本条=无执行级互斥，C1-009=SUSPENDED 写者无守卫；见 §3-C）；cross-ref C7-009（共享 workerId/租约基建）；修复验证 C10-014 |
| C7-015 | P3 | global 限流器/信号量实为 per-JVM，多节点限额按节点数线性放大，语义未声明 | nop-task-core | cross-ref MA-08/MA-09（同注册表） |
| C8-009 | P3 | call-task 自递归无深度护栏（wf 侧同族有 10_000 护栏），模型环配置下 SOE/事务内无界写 | nop-task-core | cross-ref MA-13（SOE 连锁许可泄漏） |
| C8-015 | P2 | nop-wf 全模块零指标注册：扫描积压深度/时长/成败速率均无 gauge | nop-wf-scheduler | cross-ref MA-06（backlog gauge 协同落地）、MA-09（task 侧对应面） |
| C8-017 | P3 | kill/取消不覆盖 throttle/rate-limit 等待点，等待线程不受取消唤醒（有界于 maxWait） | nop-task-core | cross-ref MA-03（syncGet 接入 cancel 同型）、MA-14（取消语义契约） |

**自洽核对**：62（族成员）+ 31（独立）= 93 = 保留条目总数；每条恰好落入一处，无遗漏、无重复计数。

---

## 3. 独立裁定与复核提议不一致处

**推翻复核明示裁定：0 处。** 全部 8 个维度复核的保留/降级/驳回结论均维持；同点判级差按预定规则「合并取族内最高并记录异议」执行（MA-01/04/05/08/17/20/23 共 7 处，异议均已记录）。

**A. 超出复核提议的新增归并组成（11 处）**——复核为盲审，跨维度同点/同根因未被登记，本裁定补出：

1. **MA-22（C3-007 = C7-005）**：同代码点同失效层（同文件 :893 Thread.sleep + :922-933 computeRetryDelay），C3/C7 两组复核各自保留且未互登 cross-ref。证据：标题、位置、修复方向几乎逐字重合。按「同一缺陷」归并。
2. **MA-17（C1-003 = C2-007，并 C3-010）**：同一点（WorkflowImpl executing/commandQueue/steps），两复核各保留（P2/P3）未互登；C3-010 为同一契约失真的文档面（且自认是 C3-001 认知根源）。判级差记录异议。
3. **MA-20（C1-007 = C2-004）**：同一点（addStepCleanup/runStepCleanups），C2 复核仅对内校准 C2-009 未对 C1 侧。判级差记录异议。
4. **MA-19（C4-006 = C8-012，并 C4-005）**：同一点（ITaskQueue 5 行空接口），C4/C8 盲审未互登；C4-005（死 bean）为同根因相邻面。
5. **MA-03 吸收 C2-010**：C2-010 复核保留为独立 P3；其「sync 等待无超时无取消响应」正是 C3-002 复核字段 8 自列的两根因之一，修复方向（syncGet 带时限+cancel 接入）重合，按同族相邻并入，族取 P1。
6. **MA-06 组成（C7-002 + C8-002 + C7-003 + C8-011）**：C7 复核已在 C7-003 条内注明与 C7-002 同面；C8-002/C8-011 为 C8 盲审侧同点（失败无退出+静默跳过+remind 同构），本裁定归并为单一族（同根因：失败/跳过项不退出工作集且信号不可见）。
7. **MA-09 组成（C7-008 + C8-005 + C5-010）**：C7-008 复核已 cross-ref 增长面（C5-003）并限缩本条为监控出口缺口；C8-005（stats 零消费）与 C5-010（stats 全量拷贝）为同一「无可观测通路」根因的两侧，归并。
8. **MA-10（C8-008 + C7-017）**：同根因（首配置固化、零失效路径）的两个失效层（参数静默失效 / 告警噪声+semaphore 侧零信号），未被我以外的任何一方关联。
9. **MA-12（C8-007 + C2-013）**：校验缺口同点，盲审未互登；C2-013 的 maxPermits 固化半面划归 MA-10。
10. **MA-14（C3-005 + C7-011 + C8-014 + C4-009）**：四个条目分属四个维度、从未被关联；根因表述互证（C3-005「竞速与被竞速 promise 无生命周期绑定」/C7-011「超时只完成兜底 promise 无消费者」/C8-014「winner 终结即归还 permit 而僵尸 body 继续」/C4-009「已排程重试体不可撤回」）——同为「取消/超时信号与 body 执行生命周期解耦」。修复为同一契约设计（中断传导+绑定+撤单）。
11. **MA-15 吸收 C3-006**：C2 复核已提议 C2-003=C1-004 归并；C3-006 为同一失效面的 C3 视角（COMPLETED 携带 exception 交错），补入。

**B. 开放问题裁定（任务书显式移交本裁定的 2 处）**：

- **C1-002 与 C3-001/011/012 的关系 → 同族相邻，并入 MA-02**。证据：五者根因同构（每请求新建 WorkflowImpl/快照读→判定→写、无跨请求互斥、仅保存点乐观锁），差异仅在站点（组迁移/流程终结/join 创建/用户 action/子流结束）；修复方向收敛于同一设计（wfId 级进程内条带锁对齐 `DaoTaskStateStore.rowLock` + 锁内重读，或 DB 级 `SELECT FOR UPDATE`）。C1-002 的占位条件更新备选方案作为站点级补强并入族修复评审，不单独立案。
- **C7-014 与 C1-009 → 分立**。证据：根因不同——C7-014 是「无执行级 fencing」（getTaskRuntime 每次新建 runtime、监视器随实例失效，两路并发执行），C1-009 是「SUSPENDED 写者游离无终态守卫」（单执行体内的写者交错）；修复工单不同（fencing/租约 vs 状态机原子化），失效面不重叠。C1-009 已入 MA-01；C7-014 独立立案，两者 cross-reference。

**C. 判级统一执行记录（复核已预约、本裁定落地，非分歧）**：MA-01 取 P1（C7-016 独立校准线 P2 下限的异议记录在案，不构成覆盖缺口）；MA-04 取 P1（C3-013 P2 异议记录）；MA-05 取 P1（C8-001 P2 异议记录）；MA-08 取 P1（C8-004 P2 异议记录 + owner 表态出口：若裁定动态 global key 不支持，本族降文档缺口）；MA-23 取 P1（C1-005 P2 异议记录）。

**D. 与复核提议的组分分歧（1 处）**：C2 复核建议「C2-005 + C2-011 + C2-013 作为单一修复项整体裁决」（throttle 闸门族）。本裁定按「根因」口径将 C2-013 划入 MA-12（校验缺口，且其与 C8-007 为同点必须归并）而非并入 MA-11（非重入嵌套，根因不同）；C2 的整体裁决意图在**修复批次层面**保留——MA-09/10/11/12 + C2-012 + C8-017 建议作为同一 throttle 闸门设计批次处置（§5）。组分分歧不影响修复完整性。

---

## 4. 合并后统计

### 4.1 总量

| 项 | 数值 |
|----|------|
| 原始保留条目（7 维度缺陷本体） | 93（P1×13、P2×46、P3×34） |
| C10 缺保护登记（独立计数） | 14（全部 P2 缺保护） |
| 合并后缺陷族 | **23**（吸收 62 条） |
| 独立立案 | **31** |
| 合并后工作项合计 | **54** |
| P0 | **0**（即时通道显式空执行） |

### 4.2 逐严重度缺陷族/立案清单

**P0：无。**

**P1（7 项，全部为缺陷族）**

| 族 | 主条目 | 标题 | 模块/簇 |
|----|--------|------|---------|
| MA-01 | C1-001 | task SUSPENDED 转移游离监视器 × cancel 竞态（kill 静默丢失 / KILLED 后复活双向） | nop-task-core |
| MA-02 | C3-001 | wf 跨请求快照竞态：组迁移丢失/终结滞留/join 双实例/action 双跑/子流回滚（五站点） | nop-wf-core（+dao） |
| MA-03 | C3-002 | sync+executor 同池嵌套死锁与无界 sync 等待（sync 包装器治理） | nop-task-core |
| MA-04 | C4-001 | retry/delay 业务体+saveState 簿记恒跑共享 globalTimer（实际 4 线程），全平台定时语义静默失准 | nop-task-core |
| MA-05 | C5-001 | 到期/提醒扫描无 LIMIT 全量载入+无批次上限（毒步骤不收敛半边联动 MA-06） | nop-wf-dao + nop-wf-scheduler |
| MA-08 | C5-003 | 全局闸门强引用注册表 × 动态 keyExpr 键空间持久无界（已知未修复：plan 364 [05-04] 声明边界之上） | nop-task-core |
| MA-23 | C7-001 | WfTaskScanner 多节点/重叠扫描无 fencing：due source 双跑副作用先于守卫+remindCount 丢失更新 | nop-wf-scheduler |

**P2（29 项 = 14 族 + 15 独立）**

| 族/条目 | 主条目 | 标题 | 模块/簇 |
|---------|--------|------|---------|
| MA-06 | C7-002 | 扫描失败/跳过项无收敛不可见（毒步骤/DEBUG 静默/job 缺省 disabled/remind 半区） | nop-wf-scheduler |
| MA-07 | C5-002 | transferActorsAsync 批量转办无条数上限 | nop-wf-core + dao |
| MA-09 | C7-008 | 全局闸门可观测性缺口（warn-once 不可操作+stats 零消费+全量拷贝） | nop-task-core |
| MA-10 | C8-008 | 全局闸门配置冻结零失效路径（cacheKey 无版本/无 remove/热更新不触注册表） | nop-task-core |
| MA-11 | C2-005 | throttle 非重入信号量嵌套同 key（显式共享 key+零配置缺省两面） | nop-task-core |
| MA-12 | C8-007 | throttle 参数构建期校验缺口（与 rateLimit/timeout/retry 硬失败清单不对称） | nop-task-core + xdef |
| MA-13 | C8-006 | catch(Exception) 漏 Error：许可泄漏/promise 悬挂/清理残留（三站点） | nop-task-core |
| MA-14 | C3-005 | timeout/kill 与 body 生命周期解耦（幽灵续延/同步体钉死/许可早释/重试不撤） | nop-task-core |
| MA-15 | C1-004 | step 级 first-terminal-wins 守卫非原子（三视角同点） | nop-task-core |
| MA-16 | C7-012 | sleep 同步阻塞等待点（占线程+无上限校验） | nop-task-core |
| MA-17 | C1-003 | WorkflowImpl 实例封闭/串行化契约未声明且 javadoc 失真 | nop-wf-core |
| MA-18 | C5-004 | 计数类边界缺失（fork-n count/loop-n 迭代数无上界） | nop-task-core |
| MA-20 | C1-007 | addStepCleanup/runStepCleanups 锁纪律不对称 | nop-task-core |
| MA-22 | C3-007 | wf retry Thread.sleep 阻塞请求事务+封顶语义缺失 | nop-wf-core |
| C1-006 | C1-006 | saveTaskState 无条带锁（与 saveStepState 不对称） | nop-task-dao |
| C3-003 | C3-003 | 图步骤 enter∩wait 双调度无守卫 | nop-task-core |
| C3-004 | C3-004 | executor 并行步骤体无上下文重投递 | nop-task-core |
| C4-003 | C4-003 | 扫描 job yaml invoker.bean 失配（已知未修复，2026-09 P2-3.1-001 同案） | nop-wf-scheduler |
| C4-004 | C4-004 | executor bean 解析保留名遮蔽+无类型强转 | nop-task-core |
| C4-007 | C4-007 | wf 扫描 job 零失败韧性（形态 A=nop-job-C7-006 跨单元） | nop-wf-scheduler |
| C5-005 | C5-005 | loop/fork items 全量物化+每迭代重序列化 O(N²) | nop-task-core/dao |
| C5-006 | C5-006 | wf 步骤读路径全集物化+账本无归档 | nop-wf-dao/core |
| C7-004 | C7-004 | dueAction 阻塞挂死扫描线程级联耗尽共享调度池 | nop-wf-scheduler |
| C7-006 | C7-006 | dueTime/到期扫描全链墙钟敏感 | nop-wf-core/dao |
| C7-007 | C7-007 | 双引擎 DB 账本无保留/归档路径 | nop-wf-dao + nop-task-dao |
| C7-009 | C7-009 | 崩溃后 ACTIVE 滞留无看门狗、workerId 空置 | nop-task-dao/core |
| C7-010 | C7-010 | SUSPENDED 滞留无自动续跑与监控区分 | nop-task-core |
| C7-014 | C7-014 | task 并发 resume 无执行级 fencing（双执行） | nop-task-core |
| C8-015 | C8-015 | nop-wf 全模块零指标注册 | nop-wf-scheduler |

**P3（18 项 = 2 族 + 16 独立）**

| 族/条目 | 主条目 | 标题 | 模块/簇 |
|---------|--------|------|---------|
| MA-19 | C4-005 | 死队列基础设施（死 bean+空模块+owner doc 声明失真） | nop-task-core beans + nop-task-queue |
| MA-21 | C1-008 | taskVars 懒初始化 LinkedHashMap（零生产调用方） | nop-task-core |
| C1-010 | C1-010 | wf 级 transitToStatus 零回退守卫（防御纵深，降级后） | nop-wf-dao |
| C2-002 | C2-002 | taskState/metrics 非 volatile 潜在可见性缺口（latent） | nop-task-core |
| C2-006 | C2-006 | 锁临界区内含 DB/模型加载/序列化 I/O（优化候选） | nop-task-dao/core |
| C2-008 | C2-008 | computeIfAbsent mapping 内 LOG.warn | nop-task-core |
| C2-012 | C2-012 | throttle wrap 栈序：permit 横跨 retry 链 | nop-task-core |
| C3-008 | C3-008 | wf source 未决 CompletionStage 透传 | nop-wf-core |
| C3-009 | C3-009 | due 扫描整批单事务 | nop-wf-scheduler |
| C3-014 | C3-014 | svcCtx=null 取消传播链断裂 | nop-task-core |
| C5-008 | C5-008 | 序列化先物化后判长+secret 二次 parse | nop-task-dao |
| C5-009 | C5-009 | 参与者解析全量物化 | nop-wf-service |
| C5-011 | C5-011 | getActionRecords 无 LIMIT+N+1（休眠面） | nop-wf-dao |
| C5-012 | C5-012 | WfAiHelper LLM 键直写 wf KV（已知未修复，2026-09 AU-10 同点位安全面） | nop-wf-ai |
| C7-013 | C7-013 | 循环每迭代 saveState DB 写放大 | nop-task-core |
| C7-015 | C7-015 | global 闸门 per-JVM 语义未声明 | nop-task-core |
| C8-009 | C8-009 | call-task 自递归无深度护栏 | nop-task-core |
| C8-017 | C8-017 | kill/取消不覆盖 throttle 等待点 | nop-task-core |

**判级迁移汇总**：原始 P1×13 全部落入 7 个 P1 族（MA-01×3、MA-02×3、MA-03×2、MA-04×2、MA-05×1、MA-08×1、MA-23×1）；无任何条目因合并改变其本体判级，族严重度=族内最高（7 处同点判级差已记录异议）。

### 4.3 C10 缺保护登记 ↔ 缺陷族对应

| C10 登记 | 对应缺陷族/立案 | 备注 |
|----------|----------------|------|
| C10-001 | MA-01 | 假保护（串行化测试）；建议 TestTaskSuspendCancelRace |
| C10-002 | MA-11 | 无嵌套测试 |
| C10-003 | MA-02（C3-001 面） | 顺序假保护 |
| C10-004 | MA-02（C3-011 面） | 无并发终结测试 |
| C10-005 | MA-02（C3-012 面） | join 全顺序到达 |
| C10-006 | MA-03 | 无 sync×executor 校验/死锁测试 |
| C10-007 | MA-04 | 线程身份零断言（fake 复刻缺陷拓扑） |
| C10-008 | MA-05 | 查询形状+收敛性零断言 |
| C10-009 | MA-08 | **反向钉死**：修复落地必须同步改写 `globalGates_strongRefRegistry` |
| C10-010 | C4-003（独立立案） | 无配置装配测试 |
| C10-011 | MA-23 | 无双 scanner 竞速测试 |
| C10-012 | C4-007（独立立案） | 批内隔离有保护、job 生命周期零覆盖（部分假保护） |
| C10-013 | MA-13 | 无 Error 注入用例 |
| C10-014 | C7-014（独立立案） | resume 全串行 |

覆盖结论：7 个 P1 族全部有 C10 登记；2 个高价值 P2 族（MA-11、MA-13）+3 个高价值独立立案（C4-003、C4-007、C7-014）有登记。**修复验收门禁：每族/立案的 C10 建议测试随修复交付，MA-08 修复前必须先改造反向钉死测试。**

### 4.4 跨单元关联与 WI 1.1–1.4 台账对账

对照 `ai-dev/audits/cra-index.md`：WI 1.1（10 P1，nop-stream-core-runtime）+ WI 1.2（10 P1，connectors）+ WI 1.3（4 P1，orchestration-thin）+ WI 1.4（2 P1，nop-job）共 26 条 P1 台账逐条核对：

1. **零跨单元同缺陷重复立项**。本单元 7 个 P1 族与 26 条在库 P1 无同根因同站点重合。（模式级相似不立项：如本单元 MA-05 的「无 LIMIT 全量载入」与 WI 1.1 C5-010 PollingJdbc 无 LIMIT 为同族模式 F7 在不同模块的复发，各自成立，不重复计数。）
2. **已知例外（单列，不重复计数）**：C4-007 形态 A（wf 扫描 job fire 失败 FAILED 永久停摆/挂死 RUNNING 占位的宿主机制）= nop-job-C7-006（WI 1.4 P2，open）。跨单元关联单列：宿主机制修复归 nop-job 单元工单，本单元 C4-007 形态 B（WfTaskScanner 使用面：无界扫描集+逐项内联无超时）与本单元 MA-05/MA-23/MA-06/C7-004 同批设计。
3. **命名空间提示（防混淆，非缺陷）**：WI 1.4 报告内亦有「MA-01」（rpcPoll 族）与「C2-001」（per-job 监视器内调外部 invoker）；Finding ID 均带单元前缀（`nop-job-*` vs `nop-wf-nop-task-*`）、族 ID 为报告内作用域，无冲突；后续交叉引用必须写全单元前缀。
4. **平台设施协同（非重复立项）**：MA-04 的宿主 globalTimer 容量残留边界（maxPoolSize 旋钮无效，ExecutorHelper 按 core 建池）已在本单元 C4 残留边界登记、移交平台审计单元；MA-04 立案对象是本单元使用侧错池，与 nop-job 侧自审的 globalTimer 回退面（WI 1.4）分属不同使用方，不重复。
5. **跨 campaign 同案对齐（2 条）**：C4-003 = 2026-09-22 code-review-full P2-3.1-001/AU-01/SM-10（已知未修复，同级 P2）——按 cra-index 使用说明「重叠发现先修复方为准、另一方 cross-reference 后关闭」，本单元保留立案指针、修复只做一次；C5-012 = 2026-09-22 AU-10/P2-3.1-011 同点位（安全维度未修复，本单元 C5 键空间维度为新增面）——同上对齐。
6. **本单元入台账**：本报告合并后口径为 54 项（P0=0、P1×7、P2×29、P3×18）+ C10 缺保护 14 条单列；cra-index.md 报告清单 WI 1.5 行由收口阶段回填（非本裁定文件职责）。

---

## 5. 对修复批次划分的建议（仅建议不做决定）

### 批次 R1（P1，7 族；建议顺序）

1. **MA-01**（task 状态机原子化）——面最小、无跨模块依赖、C10-001 测试方案现成；连带裁定 timeout-cancel 分类问题。建议最先。
2. **MA-03**（sync 包装器治理）——构建期守卫+syncGet 超时为低风险快赢；顺带闭合 C4-004 的可观测性诉求（错池告警）可评估同批。
3. **MA-04**（globalTimer 隔离）——执行器管线改动；与平台单元 globalTimer 容量结论协同后实施；C10-007 隔离断言随交付。
4. **扫描面工单簇（建议合并为一个设计批次）**：C4-003（一行 yaml 修复，可先行热修）→ MA-05 + MA-06（LIMIT/分页+失败退避+可观测，同一重构）→ C4-007 形态 B + C7-004（deadline/韧性，与 nop-job-C7-006 修复联动）→ C8-015（backlog gauge 随批落地）。
5. **MA-23**（scanner fencing）——与 MA-02 共享「DB 级互斥/租约」设计结论，建议在其后实施；需先裁定部署边界（声明单实例排程 vs 实施租约）。
6. **MA-02**（wf 快照族）——最大改动面（引擎级串行化），建议最后实施、先行独立设计审计（含 MA-17 契约文档作为设计前置）；验证须同盖五站点 + C1-010 覆盖约束。
7. **MA-08**（闸门键空间）——**前置 owner 表态**（动态 global key 支持与否决定修法）；先改写 C10-009 反向钉死测试，再实施修复；与 MA-09 观测面同批。

### 批次 R2（P2，29 项；按簇组织）

- **throttle 闸门设计批次**：MA-09 + MA-10 + MA-11 + MA-12（+ C2-012、C8-017 顺带评审）——一次性设计重入检测/参数校验/失效路径/观测通路。
- **task 状态与 fencing 批次**：MA-15、MA-20、C1-006（随 MA-01 修复顺手收敛）、C7-014 + C7-009（共享 workerId/租约基建，一个设计两个工单）。
- **协作式取消契约批次**：MA-13 + MA-14 + MA-16（Error 边界 + body 生命周期 + sleep 治理同属「取消/清理契约」一次设计）。
- **wf 引擎批次**：MA-17（MA-02 前置）、MA-22、C3-003、C3-004。
- **边界与规模批次**：MA-18、C5-005（+ C7-013/C5-008/C2-006 同路径优化顺带）、C5-006（+ C7-007 归档策略联动）、MA-07（与 MA-05 共享查询族 LIMIT 修法，可提前至 R1 扫描簇）、C5-011 同面顺带。
- **观测与部署面**：C7-006、C7-010、C8-015（若未随 R1）、C7-015（文档声明）。

### 批次 R3（P3，18 项）

- 可独立先行：MA-19（死队列清理）、MA-21（taskVars 容器替换，一行级）。
- 随 refactor 顺带：C1-010（MA-02 修复时补守卫）、C2-002、C2-006、C2-008、C2-012、C3-008、C3-009（MA-05/06 重构顺带）、C3-014、C5-008、C5-009、C5-011（MA-05 查询族顺带）、C5-012（与安全维度同案修复）、C7-013、C7-015、C8-009、C8-017。

---

## 附：裁定自洽声明

- 93 条保留条目：62 条入 23 族、31 条独立立案，每条恰好一处（§2/§4.2 核对）；
- 原始 P1×13 全部位于 P1 族，无 P1 降级、无 P1 遗漏于 P2/P3 族；
- 7 处同点判级差全部「取族内最高 + 记录异议」；
- 推翻复核明示裁定 0 处；超出复核提议的新增归并 11 处（§3-A）；开放问题裁定 2 处（§3-B）；组分分歧 1 处（§3-D）；
- 与 WI 1.1–1.4 台账零跨单元同缺陷重复立项；跨单元关联 2 条单列（C4-007 形态 A、MA-04 平台协同），跨 campaign 同案对齐 2 条（C4-003、C5-012）。
