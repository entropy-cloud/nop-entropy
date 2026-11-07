# task 闸门/throttle、错误边界、生命周期取消与状态锁并发契约（R4-08 批十二工单裁定 A–F）

> Status: active
> Last Reviewed: 2026-11-07（plan 2541 Phase 1 产出；设计评审 R1 **PASS-with-revisions**[agent_dce02a69 fresh session 只读，60+ 锚点亲验，0 Blocker+3 Major+5 Minor——R1-M1 inherited 键形态/R1-M2 W-02 重载投影/R1-M3 gauge 埋点边界三项 Major 修订已落盘，Minor A-2/B-1/B-2/D-2/F-1 同批落盘，评审记录见第十三节]；本批唯一设计裁决权威——[plan 2541](../../plans/2541-cra-phase8.48-r4-08-task-gate-throttle.md)「工单→裁定映射与分组登记」节为分组权威载体）
> Source: plan 2541（CRA 批次 8.48 R4-08，12 工单全 P2 MA 族）Current Baseline 十二工单表 + Baseline 勘误 10（含 Draft Review R1/R2 修订落款）；发现事实源 = 1.5 单元审计目录 `ai-dev/audits/2026-10/2026-10-15-0930-deep-audit-nop-wf-nop-task-concurrency/`（merge-adjudication §2.1 族表十二行 + §3-D throttle 整体裁决建议 + §4.3 C10 映射 + §五 修复批次建议 7/8/9）；行号 = live `7d0ec4bf22`（分支 `concurrency-audit-remediation`）2026-11-07 亲读回读值，执行期漂移以 live 为准并登记于 plan 勘误
> 实现锚（live 亲读）：nop-task-core `step/ThrottleTaskStepWrapper`（:36-53 execute / :55-65 getKey）、`impl/TaskFlowManagerImpl`（:216-249 getRateLimiter frozen WARN / :252-277 getSemaphore permitsFull 逐出 / :284-294 warnIfOverCap 周期化）、`builder/TaskStepEnhancer`（:130-135 timeout / :140-147 sync×executor / :148-152 syncWaitTimeoutMs / :154-159 throttle 零校验 / :161-166 rateLimit）、`step/ExecutorTaskStepWrapper`（:48 catch Exception / :59-67 取消兜底 / :68 bindCancelToken）、`step/RunOnContextTaskStepWrapper`（:29 catch Exception）、`utils/TaskStepHelper`（:186-246 timeout 竞速 / :248-341 retry / :283-300 延迟重试 schedule / :317/:325/:385 step 终态写点）、`state/TaskStepStateBean`（:44-52 succeed / :54-63 fail / :184-195 setStepStatus）、`step/TaskStepExecution`（:349 区 succeed 驱动 / :386-400 driveStepFailure）、`impl/TaskStepRuntimeImpl`（:53 stepCleanups / :180-187 addStepCleanup synchronized / :189-195 runStepCleanups 非同步 drain / :141-166 newStepRuntime）、`step/SleepTaskStep`（:31-45 execute / :38 waitUntil / :42 checkNotCancelled）、`step/ForkNTaskStep`（:36-45 count 求值+即时预分配）、`step/LoopNTaskStep`（:87-148 LoopStateBean.shouldContinue / :153-172 初始化 / :190-195 每迭代 saveState）、`step/CallTaskStep`（:80-98 newChildRuntime 独立子 runtime）、`step/CallStepTaskStep`（:62-77 同 stepRt 执行 lib step）、`ITaskRuntime`（:109-134 attribute API 亲证：getAttribute/setAttribute/removeAttribute/computeAttributeIfAbsent——taskRt 级单命名空间）、`ITaskStepRuntime`（亲证：无 attribute API、无父 stepRt 引用）、`impl/TaskRuntimeImpl`（:177-189 newChildRuntime——子 runtime 为独立实例，仅取消传播 listener 相连，无父引用字段）；nop-wf-core `service/impl/WorkflowServiceImpl`（:211-255 transferActorsAsync）、`engine/WorkflowEngineImpl`（:983-1012 runSourceWithRetry / :1014-1027 isRetryableException / :1029-1040 computeRetryDelay / :942 调用点）、`store/IWorkflowStore`（:115 findActivatedStepsByOwner 声明）+ `store/AbstractWorkflowStore`（:224-227 缺省空实现）；nop-wf-dao `store/DaoWorkflowStore`（:207-215 findActivatedStepsByOwner 无 limit / :218 区 buildDueScanQuery/buildRemindScanQuery 分批形状）；nop-wf-scheduler `WfTaskScanner`（:150-190 scanDueTasksInCurrentSession failCounts / :192-260 scanDueTask fencing+DEBUG skip+postpone / :283-320 scanRemindTasksInCurrentSession / :322-345 scanRemindTask catch 仅 LOG.error）；平台事务检测原语亲证 = nop-dao `ITransactionTemplate`（`isTransactionOpened(txnGroup)`/`getRegisteredTransaction(txnGroup)`——活动事务判定可行）
> Related：零重开边界权威 = [wf-task-scanner-fencing-and-task-wf-concurrency.md](wf-task-scanner-fencing-and-task-wf-concurrency.md)（plan 2514 裁定①–⑦：§3.4 MA-06 字段接缝 / §3.5 MA-08 键空间治理+MA-09 观测接缝 / §3.1 锁序声明）；延迟重试载体裁定 = plan 2498 §3.1.1+MA-04（timer 只触发+载体执行——引用不重建）；调度槽纪律 = plan 2497 §七；历史裁定 = plan 364 [02-06][05-01] / plan 349 Phase 2/4（引用不重建）；owner doc = `docs-for-ai/03-modules/nop-task.md` / `docs-for-ai/03-modules/nop-wf.md`

## 一、工单→裁定映射与分组登记（分组权威）

本节为 plan 2541 裁定分组的**唯一权威**。plan Goals 节建议的 A–F 分组经 live 亲读核对后**零调整采纳**（命名与文件名同 plan 登记形态）。

| 裁定 | 工单（finding ID 族） | 域 | 站点（live 行号） |
|------|----------------------|----|------------------|
| A | W-03（MA-10←C8-008+C7-017）/ W-04（MA-11←C2-005+C2-011；C10-002 宿主）/ W-05（MA-12←C8-007+C2-013） | throttle 闸门域（merge-adjudication §3-D 整体裁决批次层面兑现） | TaskFlowManagerImpl / ThrottleTaskStepWrapper / TaskStepEnhancer / CallTaskStep / CallStepTaskStep |
| B | W-06（MA-13←C8-006+C8-013+C8-016；C10-013 宿主）/ W-07（MA-14←C3-005+C7-011+C8-014+C4-009）/ W-09（MA-16←C7-012+C8-010(a)） | 取消/清理契约域 | ThrottleTaskStepWrapper / ExecutorTaskStepWrapper / RunOnContextTaskStepWrapper / TaskStepHelper / TaskStepExecution / SleepTaskStep |
| C | W-08（MA-15←C1-004+C2-003+C3-006）/ W-11（MA-20←C1-007+C2-004） | 状态与锁纪律域 | TaskStepStateBean / TaskStepHelper 写点 / TaskStepExecution 写点 / TaskStepRuntimeImpl |
| D | W-02（MA-07←C5-002+C8-003）/ W-10（MA-18←C5-004+C5-007） | 边界计数与批量域 | WorkflowServiceImpl / DaoWorkflowStore / IWorkflowStore / AbstractWorkflowStore / ForkNTaskStep / LoopNTaskStep |
| E | W-01（MA-06←C7-002+C8-002+C7-003+C8-011——2514 裁定④接缝承接） | 扫描治理残留域 | WfTaskScanner（diff 分组禁交叉）/ DaoWorkflowStore（查询面零触碰——2514 已收敛） |
| F | W-12（MA-22←C3-007+C7-005） | wf retry 域 | WorkflowEngineImpl retry 区 |
| F 附 | 红①–红⑫ 驱动器总表 + 零回退钉全列 + C10 关联面注记 | 测试缝 | 测试树（`*R408` 后缀） |

同变更集绑定（沿 plan 对齐纪律条款，此处登记实施组划分）：组 A=裁定 A 三工单同 diff 域；组 B=裁定 B 三工单；组 C=裁定 C 两工单+裁定 D 两工单+裁定 E+裁定 F；C10-002 与 W-04 同 diff 绑定、C10-013 与 W-06 同 diff 绑定（禁拆分）；W-05/W-09/W-10 校验清单一次成文禁分批散落；W-01 与 2514 产物同文件 diff 分组禁交叉；W-02 查询族修法对齐 2514 分批范式禁另起炉灶。

## 二、既有裁定引用核对（起草期义务——引用不重建）

1. **2514 §3.4 MA-06 字段接缝兑现核对（W-01 零 DDL 落款）**：2514 设计文档 §3.4 明文「显式失败计数列/隔离标记列/due-backlog gauge 等 MA-06 本体四失效面归 R4-08——R4-08 若需独立失败计数字段，由其自带 ORM 结构申报」。本裁定 E 兑现判定：**零 DDL 延续**——remind 半区退避载体裁定为 remindTime 持久化推进（列已存在，见裁定 E），**不申报任何 ORM 新列，条件申报①预设不触发**；gauge 以扫描器内存计数+GlobalMeterRegistry 注册实现（零 ORM 面）。接缝指向兑现，无字段申报。
2. **2514 §3.5 MA-09 观测接缝兑现判定**：registry-size gauge 两枚已随 2514 裁定⑤落地（TaskFlowManagerImpl :97-107 亲证）=MA-09 本批内半面已兑现；MA-09 残余[stats 生产消费/分页聚合]归 G16 deferred→R-G，本批零触碰零顺手修（getGlobalSemaphoreStats/getGlobalRateLimiterStats :297-312 只读锚不动）。
3. **C10-002/013 承接闭环**：merge-adjudication §4.3 :236/:247 与 C10 SSOT 双权威一致（C10-002→MA-11=W-04、C10-013→MA-13=W-06 均本批成员）；plan 2514 C10 处置注记「宿主归 R4-08」由本批裁定 A/裁定 B 的测试设计兑现（见裁定 F 附注记表），Phase 4 以兑现括注回填 cra-index。
4. **键空间治理约束联动落款（W-03/W-04）**：2514 裁定⑤产物（GlobalGate lastAccess/idle 逐出/permitsFull 判据/引用一致 remove/无独立清扫位/周期 WARN/gauge）零重开零弱化。**W-03 失效路径裁定为文档声明形态**（见裁定 A）→ 不触注册表替换语义，permitsFull 联动推演**不触发**（无「在用计数归零后替换」分支，permit 池分裂风险面结构排除）；**W-04 重入检测作用于 wrapper 执行面**（held-key 集与判定均在 stepRt 链），不读不写 globalSemaphores/globalRateLimiters 注册表，不改变 getSemaphore 键形态（`taskName+":"+key`）与逐出语义。
5. **2514 裁定②监视器锁序评审联动（W-08）**：2514 §3.2 task `synchronized(taskState)` 监视器为既有锁域。裁定 C 锁序唯一分支与其衔接：见裁定 C「锁序评审」——stepState 监视器方法体内零回调零 taskRt 访问，无反向获取面，无 ABBA。
6. **2498 MA-04 载体裁定对齐落款**：W-07 延迟重试撤回与 W-12 事务解耦形态均引用「timer 只触发+载体执行」契约（owner doc nop-task.md :163 契约句+`TestTaskStepHelperRetryDelayOffload_C4_001` 钉零回退），不重建第二套载体形态。
7. **2497 §七调度槽纪律落款**：W-09 裁定为文档强指引形态（不采定时器改造）；W-12 裁定为事务检测降级形态（不采定时器改造）——两工单均**不新增任何定时器调度面**，2497 §七 timer 只触发约束以「不触发」方式守约落款（若实施期确需定时器，须先补 2497 §七符合性核验）。
8. **364/349 产物零弱化落款**：364 [02-06] 校验链（timeout 负值 :130-135/rateLimit :161-166 同码同位）=W-05 新分支落点邻接零回退；364 [05-01] fail() 终态守卫与 349 Phase 2 succeed/setStepStatus 守卫=W-08 收敛对象（**语义保留仅收敛互斥非删除**）；349 Phase 4 ExecutorTaskStepWrapper 取消兜底（:59-67）=W-06 catch 收宽的叠加基座（取消路径语义零变化）。
9. **MA-09/C2-012/C8-017 顺带评审记录（零本体）**：C2 复核 throttle 整体裁决建议（C2-012 throttle permit 横跨 retry 链/C8-017 等待点零取消唤醒）在本批设计层面评审结论：**两条均不随本批修复改变其风险画像**——W-04 重入检测缩短 permit 持有时长（重入路径从 maxWait 阻塞变为确定性失败），对 C2-012「permit 横跨 retry 链」为无害化方向但不闭合其本体；C8-017 的等待点取消唤醒面随 W-07 延迟重试撤回腿部分覆盖（延迟段可取消），等待点本体归 R-G。两者登记零立案零改码。
10. **条件申报①②③④核对**：①（W-01 ORM 新列）预设不触发（第 1 条落款）；②（xdef 触碰）预设不触发——W-05 校验为静态 int 构建期校验、W-09/W-10 采**运行期上限路线 (β)**（见裁定 B/D），零 xdef 改动；③（平台载体上提）禁选——W-04/W-06 全部落 nop-task-core 使用面；④（`_` 生成物/pom）零触发——W-02 接口签名变更=普通源码零申报、W-11 javadoc/队列改造=普通源码。

## 三、裁定 A：throttle 闸门域（W-03/W-04/W-05）

### 3.1 W-04 重入检测唯一形态（C10-002 宿主）

**唯一结论 = 执行链 held-key 集检测，检测时点在 tryAcquire 成功之后，移除点与 permit release 同点，重入即硬失败。**

- **记录位裁定（亲证后定夺）**：ITaskRuntime attribute API（:109-134 亲证）为 taskRt 级**单命名空间**（fork 兄弟分支共享同一 taskRt）——attribute 记录 key 集会把「同 task fork 两分支并发同 key」的合法并发误报为重入，**否决 attribute 直录形态**。唯一形态 = **执行链持有集**：
  - `TaskStepRuntimeImpl` 新增 impl 内部字段 `throttleHeldKeys`（`ConcurrentHashMap.newKeySet()` 惰性创建）+ `parentStepRt`（同任务内父 stepRt 引用，`newStepRuntime` :141-166 建立子 runtime 时赋值——fork 分支各有独立子 stepRt，兄弟分支 held 集天然隔离）；
  - 跨任务链传播：`CallTaskStep`（:80-98 亲证——子任务经 `newChildRuntime` 产生**独立 runtime**，无父引用）在 `task.execute(subRt, ...)` 前以 `subRt.setAttribute("nop.task.throttle-inherited-keys", 当前链已持 global key 快照)` 显式传递（快照元素=**taskName 限定键** `taskName+":"+rawKey`——R1-M1 落款，见下）；
  - 判定算法（`TaskStepHelper` 新增 `acquireThrottleKey(stepRt, key, global)`/`releaseThrottleKey(stepRt, key, global)` 辅助对，ThrottleTaskStepWrapper 调用）：**重入 iff key ∈ (自 stepRt 沿 parentStepRt 上溯的全部祖先 held 集) ∪ (global=true 时 taskRt inherited-keys 属性)**。global=false 时仅查同任务链（非 global 信号量为 taskRt attribute 私有——跨任务同名字符串不构成真实重入，查 inherited 集会造成跨任务误报）；global=true 时 inherited 集快照存 **taskName 限定键** `taskName+":"+rawKey`（R1-M1 落款：与注册表键形态逐字一致——同任务名递归 call-task 检测不受影响[同注册表键、真实同 permit 池嵌套]；**异名任务 A→B 嵌套共配同 raw global key 场景**注册表键为 `A:S`/`B:S` 两个独立 permit 池、无真实嵌套死锁，raw-key 检测会造成跨任务误报，限定键检测精确排除该误报类。初稿「raw key 即可跨同名字任务识别同注册表键」论证仅覆盖同名情形，未覆盖异名情形，已勘正）。
- **检测时点契约（勘误 10 R2 Major-4 三款落款）**：
  1. **检测时点 = tryAcquire(1, maxWait) 成功之后**——禁在获取前判定（获取前判会把「合法并发兄弟恰好同时持同 key」误报为重入；先获取后检测保证检测语义只覆盖「同执行链嵌套」）。成员资格判定键形态（R1-M1 联动落款）：祖先链 held 集存 rawKey（同任务内 taskName 恒同），inherited 集存限定键——判定时 global=true 须同时检查 rawKey∈祖先链集 与 `taskName+":"+rawKey`∈inherited 集；
  2. **key 集移除点 = whenComplete/catch release 同点**——held 集成员资格严格镜像 permit 持有权：`whenComplete((ret, err) -> { releaseThrottleKey(...); semaphore.release(1); })`，同步抛出路径 catch 块内 `releaseThrottleKey(...); semaphore.release(1);` 两动作同位成对，禁拆分；
  3. **合法并发兄弟钉随 C10-002 同 diff**——同 task fork 两分支并发同 key 均成功获取（held 集分支隔离的结构性证明）。
- **重入处置 = 确定性硬失败**：抛 `ERR_TASK_STEP_CONFIG_INVALID` 族（同码，param 补 `key`/`stepPath`/`nested=true` 语义参数），先 release+移除再抛（不泄漏 permit）。对称 plan 364 [02-06] 构建期硬失败风格。**可观测性前置定夺（红④ 测试与 C10-002 共用）**：硬失败语义在 **maxConcurrency≥2** 时可观测（外层持 permit 后内层同 key 重入检测命中即抛）；**maxConcurrency=1 时内层先在 tryAcquire 处以既有 `ERR_TASK_THROTTLE_TIMEOUT` 超时失败**（permit 额为 1，外层已耗尽，重入检测点不可达——该路径为既有信号量语义的正确呈现，非缺陷，E5 不另行声明）；红④/C10-002 测试设计显式登记 **maxConcurrency≥2 前置**。
- **「有意嵌套」语义裁定**：显式共享 keyExpr 的有意嵌套用法（外层限总量+内层同 key 自限流）**不支持**——重入一律硬失败，owner doc 升级注记（E5）：存量模型若存在共享 key 嵌套，升级后运行期确定性报错，须改用不同 key 或移除内层 throttle。不做 key 集作用域分级（分级引入「几层算有意」的不可判定语义，且嵌套限流本身语义存疑——内层在外层 permit 已扣减后再次扣减，语义即「按深度耗 permit」，从未是合法契约）。
- **缺省双路径覆盖亲证**：路径 A（global×自递归 call-task——CallTaskStep :80-98 独立子 runtime，key 同为 `taskName:S` 注册表键）由 inherited-keys 传播覆盖；路径 B（call-step 同 stepRt——CallStepTaskStep :62-77 亲证 lib step 以**同一 stepRt** 执行，缺省 key=stepPath）由祖先链含自身的 held 集覆盖（同 stepRt 二次进入同名 key，外层 wrapper 尚未移除）。C10-002 至少覆盖两形态+合法并发兄弟腿（见裁定 F 附红④）。

### 3.2 W-05 参数校验唯一分支

**唯一结论 = 构建期硬失败，同码同位。** `TaskStepEnhancer.wrap` throttle 装配分支（:154-159）前置：

- `maxConcurrency <= 0` → `invalidStepConfig(stepModel, "throttle.maxConcurrency", value, "must be > 0 when throttle is configured")`；
- `maxWait < 0` → `invalidStepConfig(stepModel, "throttle.maxWait", value, "must be >= 0")`。

`ERR_TASK_STEP_CONFIG_INVALID` 同码同位（timeout 负值 :130-135/sync×executor :140-147/rateLimit :161-166 同链新分支，非重开）。maxConcurrency/maxWait 为模型静态 int（亲证），构建期校验可判定——不适用 R2 Major-1 路线义务。**升级注记义务**：存量模型 maxConcurrency<=0/maxWait<0 升级后装配即 fail-fast，须先修正再升级（owner doc E5，沿 2514 sync×executor 先例形态）。

### 3.3 W-03 失效路径二选一落款 + 告警对称

- **失效路径唯一结论 = 文档声明「global 闸门限流参数变更需重启生效」**（owner doc nop-task.md :33 节扩展 + E5 句）。钩子清除分支否决理由：模型缓存失效钩子需接入 `ResourceComponentManager` 平台模型缓存失效面（跨模块平台设施触碰=条件申报③方向），且「在用计数归零后替换」须与 2514 permitsFull 判据/引用一致 remove 联动改造注册表——破坏零重开边界且 permit 池分裂风险真实（plan 364 驱逐裁定红线）；文档声明+既有 frozen WARN（本条去重强化后成为可操作信号）在治理收益上充分。**permitsFull 联动推演落款：因不采替换分支，联动推演不触发，注册表零改动。**
- **frozen WARN 去重唯一结论 = 全局周期化**（复用 2514 `warnIfOverCap` AtomicLong 60s 间隔形态，CAP_WARN_INTERVAL_MS 同源）：getRateLimiter :240-243 的配置固化告警经新增 `AtomicLong rateLimiterFrozenWarnAt` 周期化（60s 内同 registry 仅一次）。**否决 per-key 一次性去重**（R2 Minor 落款：per-key 去重=新动态 key 无界增长面，与 2514 键空间治理同构矛盾；per-key 如裁须附带状态有界声明——本裁定直接不采）。
- **semaphore 侧对称告警唯一结论**：getSemaphore 补对称「配置固化」告警——同 key 二次配置 maxPermits 不同值且命中既有 entry 时，经新增 `AtomicLong semaphoreFrozenWarnAt` 全局周期化 WARN（`nop.task.global-semaphore-config-frozen:cacheKey=...,configuredPermits=...,activePermits=...`），与 rate-limiter 侧形态逐字对称。

### 3.4 裁定 A 约束落款

2514 治理产物（逐出判据/permit 池完整性/引用一致 remove/gauge/warnIfOverCap）git diff 零触碰；W-04 检测面与注册表零耦合；红③/红④/红⑤ 见裁定 F 附。

## 四、裁定 B：取消/清理契约域（W-06/W-07/W-09）

### 4.1 W-06 catch(Throwable) 收宽唯一分支（C10-013 宿主）

**唯一结论 = 同步路径 catch(Throwable)+rethrow 必然，回调路径 promise 对 Error 必然终结；349 取消兜底零弱化（叠加非替换）。**

- **ThrottleTaskStepWrapper（:43-52）**：`catch (Exception e)` → `catch (Throwable t)`，块内先 `releaseThrottleKey`（裁定 A 引入）+`semaphore.release(1)` 再 rethrow——**rethrow 必然性**：`t instanceof Error` 时原样 `throw (Error) t`（不 adapt 包装，防 OOM/SOE 期再分配；禁吞致命错误——OOM/StackOverflowError 转业务失败=把致命降级为可捕获，禁选），其余 `throw NopException.adapt(t)`。 Error 注入时 permit 归还+重入集移除+Error 直达上层——泄漏面闭合。
- **ExecutorTaskStepWrapper（:48）**：`catch (Exception e)` → `catch (Throwable t)`，`ret.completeExceptionally(t)` 后 **Error 原样 rethrow**（池 worker 线程语义：Error 属 JVM 级信号，complete 仅保证 promise 终结，不豁免线程侧传播）；ret 从此无悬挂路径（349 取消兜底 :59-67 继续——兜底补取消残余，本条补 Error 残余，两腿叠加语义独立零冲突）。
- **RunOnContextTaskStepWrapper（:29）**：同形态收宽——`future.completeExceptionally(t)` + Error rethrow（context.execute 的 runnable 语义同上）。
- **C8-016 清理矩阵抽取腿裁定**（至少一腿，本裁定取两腿）：(i) `TaskStepHelper.timeout` 同步抛出路径 :211-218 的 `catch (Exception e)` 收宽为 `catch (Throwable)`（保证 timer.cancel(false) 与 removeOnCancel 对 Error 亦执行——防超时定时器滞留触发）；(ii) `TaskStepExecution` sync 出口 :360 `catch (Exception e)` 收宽为 `catch (Throwable)`（R1-B-1 勘正——初稿误写「:386 区」，:386 为 driveStepFailure 内 `state.fail` 行；Error 亦进 `driveStepFailure` 既有通道——meter/seam cleanup/终态 driver 对 Error 可达；driveStepFailure 内部取消分类逻辑零改动）。其余矩阵面（fork 派发 Error 等）随上述两腿在调用链上游自然闭合，不再单开站点。

### 4.2 W-07 timeout/kill 与 body 生命周期唯一形态（四面）

- **(a) 取消传导唯一结论 = 协作式取消传导补全**：`TaskStepHelper.timeout` winner 终结回调（:240-244 whenComplete）追加 `cancellable.cancel(ICancellable.CANCEL_REASON_KILL)`（幂等——timeout 路径 timer 已取消，此腿保证**任何 winner 完成路径**（含正常完成/异常完成）后 body seam 均收到取消信号，幽灵 body 在下个协作检查点停止推进 scope/状态行写入）。外部 kill 传导既有链亲证在位（:193-194 appendOnCancel）零改动。**不引入中断式取消**（线程 interrupt 对不可安全取消段[DB 事务中段]有副作用风险——plan Risks 面采纳，传导边界=协作检查点）。
- **(b) 同步体契约唯一结论 = 文档契约句**（E5）：「不配合取消的同步步骤体会钉死调用线程直到自身完成——同步体必须自行响应 cancel token 或自带超时」。XPL loop 边界 token 检查注入归 Non-Blocking Follow-up（plan 已登记候选），本批零改码。
- **(c) permit 归还时机唯一结论 = 维持「promise 终结即归还」（早释）**：winner 终结即归还 permit 是 throttle 语义（保护进入并发度），不改为 body 存活绑定——绑定会把 permit 持有窗延长到僵尸 body 存活期（过载窗口变宽+释放点复杂化，与 2514 permit 池完整性口径冲突）。僵尸 body 占线程击穿「在途线程数」的残余面以 E5 声明承接：`maxConcurrency` 保护的是步骤进入并发度，不是 executor 线程占用上界；executor 线程上界由池自身 max 配置承接。红⑦(ii) 检出力 = seam 结构钉+变异论证（见裁定 F 附）。
- **(d) 延迟重试撤回唯一结论 = schedule 段接 cancelToken**：`TaskStepHelper.retry` 延迟路径（:283-300）对 schedule 返回的延迟段施 `FutureHelper.bindCancelToken(cancelToken, delayedStage)` 使用面接线（平台原语使用，不改本体）——cancel/timeout 触发时延迟段以 `NopTaskCancelledException` 终结、载体上的步骤体不执行；已开始执行则协作取消兜底。2498 载体裁定（timer 只触发+载体执行）零回退——本条只加取消接线，不改执行载体。C4-009 面闭合。

### 4.3 W-09 sleep 治理唯一分支（含校验路线落款）

- **校验路线二选一落款 = (β) 运行期上限路线**（条件申报②预设不触发——零 xdef 改动）：`SleepTaskStep.execute` **求值后、:34 短路口（`sleep <= 0 → CONTINUE`）之前**插入校验 `sleep > nop.task.step.max-sleep-ms`（新键 `nop.task.step.max-sleep-ms`，缺省 **600000**）即抛 `ERR_TASK_STEP_CONFIG_INVALID` 族（param sleepMillis/maxSleepMs/stepPath——同码族运行期拒绝，错误消息显式声明「sleep 时长为运行期表达式，超上限在执行期拒绝」）。**负值/零值语义定夺 = 维持现行为（`<=0` 走 :34-36 短路 CONTINUE）不拒绝**——校验仅承载超上界拒绝单语义，避免额外行为变更面（负值/零值 sleep 为既有无害 no-op，拒绝它属无关行为收缩）。插入位单一形态落款（R1-B-2 勘正）：校验位于 :34 短路口之前为唯一成文形态——由于校验仅拒绝正值超限、负值/零值本就不被拒绝，**插入在短路口之后行为上完全等价**（初稿「插在其后校验成死分支、禁与维持现行为并存」的理由不成立）；取短路口之前为单一规定形态以免 Phase 2 歧义，非行为分叉。R2 Major-1 路线义务兑现：sleep 时长为运行期表达式（`sleepMillisExpr.invoke(stepRt)` 亲证 :32），构建期同码同位校验对表达式值不可判定，故采 (β)——红⑨/E5 口径同步为运行期超限拒绝形态（仅登记超限语义）。
- **异步化二选一落款 = 文档强指引形态**（不改码）：owner doc E5 句「长时等待应优先使用 `<delay>`（异步释放线程，2498 正例）；`<sleep>` 为同步轮询占线程形态（100ms 粒度），仅限短时等待」。sleep→delay 异步化改造归 Non-Blocking Follow-up（plan 已登记）。:38 waitUntil 轮询机制、:40-42 取消显式失败语义（349 产物）零触碰。C8-010(a) 对照面由本句承接；C8-010(b) 归 R-G 零本体。
- **同步体契约句**（W-07(b) 共享落点）随本句同节成文。

### 4.4 裁定 B 约束落款

promise 必然终结不变量 = **5s 断言口径**（C10-013 断言三件套之一：Error 注入后 promise 须在 5s 内 exceptionally 完成——latch 等待上界 30s 防测试自身挂死）；349 取消兜底钉族/364 守卫产物零弱化；全部 catch 收宽为叠加（取消路径语义逐点不变）。

## 五、裁定 C：状态与锁纪律域（W-08/W-11）

### 5.1 W-08 step 终态守卫原子化唯一分支

**唯一结论 = `TaskStepStateBean` 三守卫方法体内 `synchronized(this)` 收敛（守卫判定+写入原子化在 bean 内部完成），不触碰 8 个写点调用侧。**

- 8 写点全部经 `succeed()`/`fail()`/`setStepStatus()` 三方法进入（`TaskStepHelper` :317/:325/:385 + `TaskStepExecution` :349 区/:386-400 + 其余写点亲证均走该三方法），**在 bean 内部加监视器即结构性覆盖全部写点**——调用侧零 diff，最小可逆。
- 唯一形态细则：`succeed`（:46-52）锁内 `isDone()` 复查→`setResultValue`→`setStepStatus`；`fail`（:54-63）锁内 `isDone()` 复查→`exception(exception)`；`setStepStatus`（:189-195）锁内守卫判定→写。`exception()` 写随 `fail` 入锁（C3-006「COMPLETED 行携带 exception」交错窗闭合）；`fail()` 的 exception 写在锁内且先于任何终态标注读取面。364 [05-01]/349 守卫**语义保留仅收敛互斥非删除**（first-terminal-wins 判定逐字保留）。
- **两驱动验证设计**：retry do/while 驱动（TaskStepHelper retry :304-341 同步/异步完成路径）与 delay-schedule 回调驱动（:283-300 延迟轮 doRetry 链）各自一条交错断言（红⑧ 覆盖矩阵逐驱动登记）。
- **锁序评审（与 2514 裁定②联动——引用不重建）**：唯一锁序分支 = **taskState → stepState 单向**。成立依据（live 亲证）：(i) `TaskStepStateBean` 三守卫方法体内零回调、零 taskRt/taskState 访问（:46-63/:189-195 亲证）——持 stepState 锁期间无获取 taskState 锁的路径；(ii) 2514 裁定② `synchronized(taskState)` 段（TaskImpl 四终态 driver+SUSPENDED 写）内若触 step 写点，获取序为 taskState→stepState，同向无环；(iii) 本裁定不新增任何回调/监听器进锁内。**禁则**：后续任何在 stepState 锁内调用 taskRt/taskState 方法的改动即破坏本锁序，closure audit 以「守卫方法体零新增调用」为核对锚。
- **性能口径**：热点写点锁竞争评估——三守卫方法临界区为纯字段读写（无 IO 无回调），监视器偏粗粒度风险可忽略；红⑧ 交错断言以 latch 对齐 check-then-act 窗（检出力见裁定 F 附）。

### 5.2 W-11 cleanup 锁纪律三选一落款

**三选一裁定取 (b) = `stepCleanups` 改 `ConcurrentLinkedQueue<Runnable>`，`addStepCleanup` 去 synchronized（`offer` 原子），`runStepCleanups` 改 drain（`poll` 循环至空）逐个执行。** 理由：与 `TaskRuntimeImpl.cleanups`（Cancellable 正例 :56/:266-275）防护水平对齐——无锁并发容器+单消费者 drain，零锁序新增零 monitor 引入；生产者出现即触达安全（公开扩展点语义）。(a) runStepCleanups 同步化否决（引入第二 monitor 面+drain 期间持锁执行外部 cleanup=长持锁）；(c) javadoc 契约否决（公开扩展点以文档约束并发是隐式契约，与 MA-20 P2 判级依据「生产者出现即触达」矛盾）。`runCleanup` 的 catch(Exception) 包裹语义零改动。红⑪ 以测试内并发 add×drain 真实交错构造（行为红成立，总册须显式标注「非 live 可达缺陷态」）。

## 六、裁定 D：边界计数与批量域（W-02/W-10）

### 6.1 W-02 转办批量上界唯一形态

- **查询族分页唯一结论 = 游标分页投影 2514 findDueActivatedSteps 先例，接口改采「保留旧签名 + 新增四参 default 重载」投影形态（R1-M2 修订落款，取代初稿「签名改为四参」）**：`DaoWorkflowStore.findActivatedStepsByOwner` 新增四参重载 `findActivatedStepsByOwner(String ownerId, Set<String> wfIds, int batchSize, String cursorStepId)`，实现 `QueryBean.limit(batchSize)` + `orderBy(stepId asc)` + 游标谓词（`stepId > cursor`）——与 :218 区 buildScanQuery 形态同构（transfer 场景无时间序字段，以稳定主键 stepId 作游标）。**接口投影细则**：`IWorkflowStore`（:115 声明）**保留既有三参签名零改动**，新增四参 `default` 方法缺省委派三参形态（`return findActivatedStepsByOwner(ownerId, wfIds);`）——`AbstractWorkflowStore`（:224-227 三参缺省空实现）**零 diff**（经接口 default 继承，无需触碰）；全仓另有两个三参 `@Override` 测试桩 `nop-wf-core/src/test/.../mock/InMemoryWfStore.java:243` 与 `nop-wf-service/src/test/.../mock/MockWorkflowStore.java:217`（R1-M2 亲证登记）——旧签名保留使两桩**编译面零触碰**（MockWorkflowStore 位于批外模块 nop-wf-service 测试树，初稿签名变更形态会破坏其编译并使批外零触碰声明失实——重载投影消解该波及面）；Phase 2 核对项：两测试桩文件 git diff 为空亲证。`WorkflowServiceImpl.transferActorsAsync` :224 改调四参形态（唯一生产调用点，同 diff 改造；**禁触 buildDueScanQuery/findRemindActivatedSteps 形状**——2514 C10-008 查询形状钉 `TestWfScanQueryShape` 点名零改写）。
- **maxBatch 配置键唯一结论**：`nop.wf.transfer.max-batch`，缺省 **200**，`<=0` 装配期 fail-fast。**超限错误码归属**：`ERR_WF_TRANSFER_BATCH_EXCEED`（新增于 nop-wf-core `NopWfCoreErrors` 模块内错误码族——非 api 叶零申报；param ownerId/total/maxBatch）。语义：`findActivatedStepsByOwner` 首批返回满 batchSize 且存在后续页且累计超过 maxBatch 时显式报错（分页消费至 maxBatch 为止、发现超限即 typed 拒绝——不静默截断：截断外步骤被静默滞留比全量载入更危险，沿 2514 §五「setLimit 一次截断否决」同理由）。
- **防重入取舍**：本批**不做**（同一 fromUser 并发转办各自分页消费为有界面——maxBatch+游标已消除无界物化与单请求长事务；互斥候选归 Non-Blocking Follow-up，plan 已登记）。
- **部分成功语义保留落款**：`successCount`+`failedItems` 返回契约逐字保留（:251-254），分页改造零回退该语义；每条执行仍独立 try/catch（:229-248 结构不动）。

### 6.2 W-10 计数上界唯一分支（含校验路线落款）

**校验路线二选一落款 = (β) 运行期上限路线**（fork-n count/loop-n 表达式为运行期求值——R2 Major-1 同款义务；零 xdef 改动，条件申报②预设不触发）：

- **fork-n**：`ForkNTaskStep.execute` count 求值后（:38 之后）仅校验上界 `count > nop.task.step.max-fork-count`（新键缺省 **1000**）即抛 `ERR_TASK_STEP_CONFIG_INVALID` 族（param count/maxForkCount）。**count<=0 语义定夺 = 维持现行为（:59-63 空 fork no-op，`FutureHelper.success(null)`）不拒绝**——负值/零值为既有无害语义，校验仅承载超上界单语义（零额外行为变更）。**预分配改增量**：`new ArrayList<>(Math.max(count, 0))` → `new ArrayList<>()`（增量 add，OOM 通道闭合——count 合法上限内预分配收益可忽略）。
- **loop-n**：`LoopNTaskStep` 初始化段（:159-169）求值后统一校验清单：`step == 0` 既有拒绝保留（:163-165 零改动）；新增 (i) 迭代数上界校验——迭代数公式 = **`floor(|end - begin| / |step|) + 1`**（R1-D-2 勘正：live 语义 `current <= end` 含端点，begin=0/end=9/step=1 实跑 10 次——初稿 `ceil(|d|/|s|)` 相对 live 语义恒少 1 且把「`|end-begin|==0` 为零迭代」写失实[begin=end 实跑 1 次]；初稿「取 ceil 免登记/取 floor 须登记」的指导倒置已勘正），long 域计算防 int 溢出误判，`> nop.task.step.max-loop-count`（新键缺省 **10000**）超限硬失败（param begin/end/step/maxLoopCount）；`|end-begin|==0` 即 begin=end 实跑 1 次迭代，`floor(0/s)+1=1 ≤ 上界` 天然放行，无需特殊分支；(ii) 负 step 反向计数为合法语义**保留**（`shouldContinue` :102-106 对负 step 已自洽——`current >= end` 反向收尾；仅防下溢：`incStep` 后 `current` 符号翻转（跨越 int 边界）时硬失败，实际由 (i) 的迭代数上界前置拦截，运行期兜底断言登记）。**不新增 maxCount xdef 属性**（(β) 路线零 xdef；LoopTaskStep.maxCount 对称先例引用为理由支撑非实现依赖）。
- **校验清单同位成文**：W-05（静态构建期）+W-09/W-10（运行期 (β)）清单在 `TestFirstClassConfigValidation` 同类扩展中一次成文（同批实施禁散落）；每迭代 saveState 写放大（:190-195）登记为既有持久点契约零触碰（写放大治理归 R-G，非本批缺陷面）。

### 6.3 裁定 D 约束落款

新配置键三枚（`nop.wf.transfer.max-batch`/`nop.task.step.max-fork-count`/`nop.task.step.max-loop-count`）键常量归属所在模块 Configs 持有类——`nop.task.step.*` 两枚落既有 `TaskConfigs`（nop-task-core），`nop.wf.transfer.max-batch` 落 nop-wf-core **新建 Configs 持有类**（R1-A-2 勘正：初稿所引「WfConfigs」不存在——nop-wf-core 现无 IConfigReference 持有类，先例=nop-task-core `TaskConfigs` 与 nop-wf-scheduler `WfSchedulerConfigs`）；红②/红⑩ 见裁定 F 附。

## 七、裁定 E：扫描治理残留域（W-01）

- **(a) remind 半区退避唯一分支 + 退避载体定名（R2 Minor-5 义务）**：**载体 = remindTime 持久化推进**（`remindTime` 列已存在亲证——2514 查询族即按其 orderBy；零 DDL 零 ORM 申报）。失败时 `UPDATE ... SET remindTime = now + min(60s×2^n, 1h)` 直发语句（与 2514 裁定⑥ remindCount 直发同形态、实体写路径列集约束守约——只 SET remindTime 单列）；**语义与 due 半区等价**（真正退出扫描集，非仅内存抑制——否决内存 failCounts-only 分支，其只抑制重复 ERROR 不退出工作集，与 due 半区收敛语义不等价）。failCounts 计数 map 为 scanRemindTasksInCurrentSession 局部同构（有界 cap=10000 整体清空 WARN 一次——2514 §3.4 有界策略同款）。**`remindTime` 引擎侧从此新增 scanner 退避写入点** → `:287` javadoc「remindTime 引擎侧无写入点恒空集」**必须同步更新**（改为「remindTime 唯一写入点=scanner 退避推进」）+E5 同步义务（owner doc/runbook 同句）——禁留失实 javadoc。
- **(b) backlog gauge 唯一形态**：两枚 gauge 注册于 WfTaskScanner（GlobalMeterRegistry，未初始化静默跳过——2514 gauge 通道同形态）：`nop.wf.scheduler.due-scan-scanned`（最近一轮 due 扫描消费条数，AtomicLong 载体）+ `nop.wf.scheduler.due-scan-failures`（最近一轮失败条数）。命名沿 `nop.task.global-gate.registry-size` 的「前缀.对象.指标」纪律；MA-09 全量观测治理归 R-G 零触碰（本两枚为 C8-015 协同半面最小落地，不扩 stats 面）。
- **(c) 模型校验二选一落款 = WARN（非硬失败）**：due 步骤扫描期 `getDueAction` 空白分支 :224-228 `LOG.debug` 升级 `LOG.warn`（周期性语义——每轮该步骤 WARN 一次可接受，量级受分批上界约束；不做 per-key 去重防新键空间）；校验语义 =「dueTimeExpr 配置存在而 dueAction 缺失」在 WARN 消息中显式成对提示（堵 VFS 热更新漂移路径 + 部署盲区显式化）。**否决硬失败**：模型加载期校验触 `nop-wf-meta`/模型解析面（批外模块）且存量模型升级破坏风险真实（W-05 先例的升级注记负担在此不必要——缺 dueAction 步骤本身无副作用，WARN 可操作性充分）。
- **(d) job 缺省 disabled 盲区声明落点**：`docs-for-ai/03-modules/nop-wf.md` 补节（扫描 job 缺省 disabled 的部署盲区 runbook 句：启用扫描 job 为运维动作，未启用时 due/remind 扫描零执行）。
- **零 DDL 落款**：条件申报①不触发（第 2 节第 1 条）；**2514 产物 diff 分组禁交叉落款（R1-M3 措辞消解——初稿「:39-58/:102/:173-188/:206-281 零 diff」与 gauge 埋点自相矛盾已勘正）**：W-01 diff 语义边界 = **due 半区退避/fencing/分批的 2514 判据、常量与既有逻辑零改写零重开**（failCounts/2^n 退避/dueTime 推进/fencing 抢占/分批游标逐字保留），**gauge 观测埋点为纯新增行允许**——两枚 gauge 的值载体只能埋在 due 扫描区（消费计数点=scanDueTasksInCurrentSession 消费循环尾部计数、失败计数点=postponeFailedDueTask/scanDueTask 失败路径邻接新增 `scannedGauge`/`failureGauge` 增量行）与 remind 半区退避分支；闭包核对标准 = 「2514 判据/常量/既有分支逻辑零改写 + gauge 埋点行纯新增」，closure audit 以此为准（初稿按字面行区间零 diff 则红①「gauge 值反映」不可达成——矛盾已消解）；remind 半区新增（scanRemindTasksInCurrentSession 退避+scanRemindTask 失败退避）与 WARN 分支升级不受本边界约束。`testScanDueTasksIgnoresCurrentStepStatusRace`+`TestWfTaskScannerFencing` 零改写。红① 测试缝 = remindTime 注入行（模型缝/直发 UPDATE 预置）驱动 remind 半区真实触发（恒空集前置突破）。

## 八、裁定 F：wf retry 域（W-12）

- **封顶唯一分支**：`computeRetryDelay`（:1029-1040）对**固定 delay（非 exponential）分支同样应用 maxRetryDelay 封顶**——`if (maxRetryDelay > 0) delay = Math.min(delay, maxRetryDelay)` 提升出 exponential 分支、对两分支统一生效（对齐 task 侧 RetryPolicy 两分支均封顶先例）。**maxRetryDelay=0 缺省语义定夺 = 维持不封顶**（`<=0` 即未配置语义，不改变存量长退避行为——用户可见面零变更；owner doc E5 句显式声明「maxRetryDelay<=0 时不封顶」）。
- **事务解耦二选一落款 = 检测活动事务时降级不重试**（定时器异步退避否决——:942 调用点在 executeStep 同步段内，异步化波及引擎整个步骤迁移链，破坏最小 diff 原则且 2497 §七 合规成本高）。**可行性亲证**：平台事务检测原语在位 = nop-dao `ITransactionTemplate.isTransactionOpened(txnGroup)`/`getRegisteredTransaction(txnGroup)`；nop-wf-core 不依赖 nop-dao → 沿 **2514 裁定①接口投影先例**（core 定义/dao 实现，依赖方向合法）：`IWorkflowStore` 新增 `boolean hasOpenedTransaction(String txnGroup)`，`DaoWorkflowStore` 实现（R1-F-1 txnGroup 定名规则落款：实参语义=**querySpace**——平台先例 `JdbcEntityPersistDriver:163` 即以 `txn().isTransactionOpened(sql.getQuerySpace())` 判定，实体所属 querySpace 与其 txnGroup 一一对应；`DaoWorkflowStore` 以既有注入 `IOrmTemplate`（:66-69 亲证）经 `ormTemplate.getSessionFactory().txn()` 取 `ITransactionTemplate` 零新增注入，txnGroup 解析为 wf 持久化实体的 querySpace（`stepDao()` 实体模型 `getQuerySpace()`），实现细节归 DaoWorkflowStore 内部）；`runSourceWithRetry` 在 `delay > 0` 且 `workflowStore.hasOpenedTransaction(...)` 为真时**不 sleep 不重试**，LOG.warn 显式留痕（`nop.wf.retry-degraded-in-transaction:wfId=...,stepName=...,maxRetryCount=...`）后抛原业务异常。语义声明（E5）：**wf step retry 的延迟等待仅在无活动事务上下文生效**——事务内 sleep 持连接/行锁等待为更劣形态，降级不重试为正确语义；升级注记：存量依赖事务内重试的模型升级后该场景改为显式失败（重试语义需在无事务上下文或引擎外编排实现）。中断响应路径 :1001-1005 零改动。
- **2497 §七约束落款**：本裁定不引入定时器面（第 2 节第 7 条同源落款）。
- **回归设计**：autoTransition 触发面全链回归（runSourceWithRetry 调用点 :942 所在 executeStep 链）+红⑫ 两腿（封顶行为钉+事务降级行为钉）。

## 九、裁定 F 附：红钉总表、零回退钉全列、C10 关联面注记

### 9.1 红①–红⑫ 逐钉形态（确定性标注/构造路径/伪绿防线/闩带上界）

| 红 | 工单 | 形态与确定性 | 构造路径 | 伪绿防线 | 闩带上界 |
|----|------|-------------|---------|---------|---------|
| ① | W-01 | 行为红（确定性——remindTime 注入行后失败注入稳定触发） | 注入 remindTime 行（直发 UPDATE 预置）+listener 持续失败；断言失败条目 remindTime 推进退出工作集+两 gauge 注册且值反映 | 接线断言（gauge 真实注册非字段存在）+退避推进真实改写查询集 | 30s |
| ② | W-02 | 行为红（确定性——N>maxBatch 全量执行当前即成功） | 注入 N=maxBatch+1 行 owner 步骤；断言 ERR_WF_TRANSFER_BATCH_EXCEED typed 拒绝+部分成功字段在超限前场景保留 | 部分成功语义对照腿（N<=maxBatch 时 successCount==N） | 30s |
| ③ | W-03 | (i)(ii) 行为红（确定性——WARN 计数受控断言当前即超量打印）；(iii) 结构钉（文档声明形态——2537 红⑥ 诚实留痕禁伪绿） | 同 key 双次不同速率/不同 permits 注入→周期化计数断言；semaphore 侧对称告警存在性断言；(iii) E5 句落账核对 | WARN 计数以 appender 捕获断言上界 | 30s |
| ④ | W-04 | 行为红（确定性——pre-fix 嵌套获取 maxWait 阻塞超时；post-fix 重入硬失败） | C10-002：显式共享 keyExpr 递归嵌套+call-task 自递归+call-step 同 stepRt 三腿；检出力=maxWait 调小 seam | **合法并发兄弟腿**（fork 两分支并发同 key 均成功）防「一刀切注册表级拒绝」伪绿 | 30s |
| ⑤ | W-05 | 行为红（确定性——装配当前即成功） | maxConcurrency=0/负值/maxWait<0 装配断言 ERR_TASK_STEP_CONFIG_INVALID | `TestFirstClassConfigValidation` 同类扩展零回退基座 | 同步 |
| ⑥ | W-06 | 行为红（确定性——pre-fix permit 泄漏/promise 悬挂） | C10-013 三断言一套：AssertionError 注入（throttle body+executor 回调两形态）+permit 回满断言+promise 5s 终结断言；RunOnContext 第二腿 | permit 回满以 availablePermits==maxPermits 断言（非仅 release 调用存在） | 5s 断言/30s latch |
| ⑦ | W-07 | (i) 行为红（seam 确定性——幽灵 body 协作检查点拦截断言）；(ii) 结构钉+变异论证（permit 早释维持为裁定语义，非缺陷修复）；(iii) 结构钉（契约文档——诚实留痕）；(iv) 行为红（确定性——终态后 schedule 体不执行） | timeout winner→body seam 取消信号断言；延迟段 onCancel→NopTaskCancelledException 断言 | (ii) 变异：摘除早释腿→占用断言变化即证检出力 | 30s |
| ⑧ | W-08 | 行为红（交错窗窄——latch/seam 对齐 check-then-act 窗确定性构造；未命中时概率循环+变异论证诚实登记） | 双线程 latch 对齐 succeed/fail 交错→断言终态行无混合状态；8 写点覆盖矩阵+两驱动（retry/delay-schedule）逐驱动登记 | 守卫语义保留断言（364 [05-01] 语义对照）零弱化 | 30s |
| ⑨ | W-09 | 行为红（确定性——运行期 (β) 路线：超限 sleep 当前即执行） | sleep 求值超 `nop.task.step.max-sleep-ms` → 运行期 ERR_TASK_STEP_CONFIG_INVALID 断言 | 取消显式失败语义钉（:40-42）零回退对照 | 同步 |
| ⑩ | W-10 | 行为红（确定性——超限计数当前即执行/预分配当前即发生） | fork-n count 超限拒绝+loop-n |end-begin|/|step| 超限拒绝+增量改造内存形态断言（预分配消失以反射/行为腿承载） | 预分配腿结构钉诚实登记 | 30s |
| ⑪ | W-11 | 行为红=测试内并发 add×drain 真实交错构造（**总册显式标注：非 live 可达缺陷态——防 closure audit 误读为接线验证证据**） | 测试内多线程 offer×drain 交错→drain 后队列空+无丢失断言 | 2537 红⑥ 结构钉诚实留痕先例 | 30s |
| ⑫ | W-12 | (a) 行为红（确定性——固定 delay>maxRetryDelay 当前即未封顶实际 delay==retryDelay）；(b) 行为钉（事务降级：注入 opened-transaction store stub→降级 WARN+不重试断言） | 固定 delay 配置注入→computeRetryDelay 返回值断言；事务腿经 IWorkflowStore stub（接口投影可桩化） | task 侧 RetryPolicy 两分支对照回归 | 30s |

概率腿行为红未命中时诚实登记以结构钉+变异腿承载（2539 总册先例）；全部钉落 `_tmp/red-green-ledger-2541.md` 总册+逐 trial 日志存证。

### 9.2 零回退钉全列

- 勘误 7 双签名（nop-task solo 309 tests 0F0E 0S / nop-wf solo 245 tests 0F0E 5S——签名维持为新钉比对基线）。
- R211 钉族：`TestPhase4TerminalAndConcurrency`（[05-03] suspended-cancel+[05-04] globalGates 改写后形态——W-03/W-04 零回退基座）/`TestWfTaskScanner` 全列（含 `testScanDueTasksIgnoresCurrentStepStatusRace`）/`TestWfTaskScannerFencing`/`TestFirstClassConfigValidation`。
- 2514 C10 面零改写清单：C10-001/003/004/005/006/008/009/011 八条；**`TestWfScanQueryShape`（C10-008 查询形状钉）点名零改写**（W-02 同查询族修法——R2 Major-3）。
- 364/349/2498 钉：`TestTaskStepHelperRetryDelayOffload_C4_001`（2498 载体钉）/364 校验链钉族/349 取消兜底钉族/`TestGraphErrorSuccessRace` 族/`TestSuspendSemantics`/`TestExecutorTaskStepWrapperAsyncBranch`/`TestChildRuntimeCancelPropagation`/`TestAsyncSuspendPropagation`/`TestGraphDrainRace`。

### 9.3 C10 关联面注记表

| C10 | 宿主 | 本批处置 |
|-----|------|---------|
| C10-002 | W-04（同 diff 绑定） | 裁定 A §3.1 测试设计兑现：三腿（显式共享 key/call-task 自递归/call-step 同 stepRt）+合法并发兄弟腿+确定性硬失败断言 |
| C10-013 | W-06（同 diff 绑定） | 裁定 B §4.1 兑现：Error 注入三断言一套禁拆分 |
| C10-001/003/004/005/006/008/009/011 | 2514 已兑现 | 零改写（含 C10-009 反向钉改写后形态与 C10-008 TestWfScanQueryShape） |
| C10-007 | plan 2498 已兑现 | 零触碰 |
| C10-010/012/014 | R4-09 | 零触碰禁提前 |

## 十、E5 落点清单（owner doc 逐处——Phase 3 统一落账）

`docs-for-ai/03-modules/nop-task.md`：

1. :33 global 闸门节——W-03 失效路径声明句（「限流参数变更需重启生效」）+semaphore 对称告警句+frozen WARN 周期化句（2514 落账形态零矛盾扩展）；
2. 同节或 throttle 专节——W-04 嵌套禁用声明（重入硬失败语义+有意嵌套不支持升级注记+双缺省路径说明）；
3. :159-161 可靠性属性校验链节——W-05 throttle 校验句（maxConcurrency<=0/maxWait<0 装配 fail-fast+升级注记）+W-09 sleep 运行期上限句（`nop.task.step.max-sleep-ms` 键+运行期拒绝语义）+同步体契约句（W-07(b)）+sleep/delay 强指引句+`nop.task.step.max-fork-count`/`nop.task.step.max-loop-count` 运行期上限句（W-10）——sync×executor 邻接句保持零矛盾；
4. 取消/清理契约新句（W-06/W-07）——catch(Throwable) 边界+promise 必然终结+permit 早释口径声明（maxConcurrency=进入并发度非线程占用上界）；
5. W-11 ITaskStepRuntime 扩展点契约句（drain 语义+并发安全声明）；
6. :269-281 源码锚点表行号刷新。

`docs-for-ai/03-modules/nop-wf.md`：W-02 转办上限句（maxBatch 键+超限错误）+W-12 retry 语义句（封顶统一+maxRetryDelay<=0 不封顶+事务内降级声明）+W-01 job disabled 盲区声明节。

`docs-for-ai/02-core-guides/workflow-configuration.md`：dueAction 节 WARN 校验语义句（按需）。

`docs-for-ai/04-reference/source-anchors.md`：按实际刷新（归 Phase 3 统一落账）。

## 十一、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| W-04 taskRt attribute 直录 key 集 | attribute 为 taskRt 级单命名空间，fork 兄弟分支误报（合法并发兄弟腿直接否证） |
| W-04 注册表级「key 在用即拒绝」 | 合法并发（跨任务同 key）全被拒绝；重入语义=同执行链嵌套，非全局占用 |
| W-04 key 集作用域分级（几层算有意嵌套） | 「有意」不可判定；嵌套扣减语义本身不成立（按深度耗 permit 从未是契约） |
| W-03 模型缓存失效钩子清除闸门 | 平台设施触碰（ResourceComponentManager 失效面）+「在用计数归零替换」与 permitsFull/引用一致 remove 联动改造=破坏 2514 零重开边界；permit 分裂风险真实 |
| W-03 frozen WARN per-key 一次性去重 | 新动态 key 无界增长面（R2 Minor），与 2514 键空间治理同构矛盾 |
| W-06 catch(Throwable) 吞 Error 不 rethrow / Error 经 adapt 包装 | 致命错误转业务失败禁选；adapt 包装在 OOM/SOE 期再分配风险——Error 原样 rethrow |
| W-07 permit 归还改 body 存活绑定 | 持有窗延长+过载窗口变宽+释放点复杂化；与 2514 permit 池口径冲突 |
| W-07 中断式取消（thread interrupt） | 不可安全取消段（DB 事务中段）副作用；协作式检查点为平台既有取消语义 |
| W-09/W-10 静态属性路线 (α)（xdef 新上限属性） | 条件申报②转触发+模型面破坏性升级；运行期 (β) 零 xdev 零升级面即可闭合 |
| W-09 sleep 异步化改造 | 波及 DelayTaskStep 语义邻接面；文档强指引已消除误用面——改造归 Follow-up |
| W-11 runStepCleanups 同步化 / 删 synchronized+javadoc | 第二 monitor+长持锁执行外部 cleanup；隐式并发契约与公开扩展点矛盾 |
| W-02 setLimit 一次截断不报错 | 截断外步骤静默滞留比全量载入更危险（2514 §五同理由）；typed 超限拒绝保操作可见性 |
| W-02 同一 fromUser 防重入互斥 | maxBatch+游标已消除无界物化；互斥为增量优化归 Follow-up |
| W-12 maxRetryDelay=0 收敛为封顶 | 用户可见行为面变更（存量长退避改变）；维持不封顶+文档声明零破坏 |
| W-12 定时器异步退避 | :942 同步段异步化波及引擎迁移链+2497 §七合规成本；事务检测降级最小且可行（ITransactionTemplate 亲证） |
| W-01 模型加载期硬失败校验 | 触批外模型解析面+存量升级破坏；扫描期 WARN 可操作且零破坏 |
| W-01 remind 内存 failCounts-only 抑制 | 只抑制 ERROR 不退出工作集，与 due 半区持久化收敛语义不等价（R2 Minor-5 定名义务否决） |

## 十二、实施留给 Phase 2 的注意事项

1. **W-04 held-key 集与 permit release 的成对性是硬约束**：`releaseThrottleKey`+`semaphore.release(1)` 必须在 whenComplete 与 catch 两路径同位成对执行（先移除后释放），漏任何一侧即复刻泄漏或残留幽灵重入态；C10-002 合法并发兄弟腿与红④ 三腿必须同 diff 落地。
2. **diff 分组纪律**：WfTaskScanner 的 W-01 diff 禁触 due 半区（:39-58/:173-188/:206-281 零 diff）；TaskFlowManagerImpl 的 W-03 diff 禁触 2514 逐出/gauge/warnIfOverCap 语义；TaskStepEnhancer 校验分支为同链新分支非重开——closure audit 逐文件 git 过滤核对。
3. **W-12 接口投影与 W-02 重载投影同在 IWorkflowStore**：两处接口面新增须一次规划（`findActivatedStepsByOwner` 四参 default 重载[R1-M2 形态——三参签名零改动，`AbstractWorkflowStore` 零 diff，两个既有测试桩 InMemoryWfStore/MockWorkflowStore 编译面零触碰] + `hasOpenedTransaction` 新增），`TestWfScanQueryShape` 与 2514 withWfLock 接口面零改写核对先行。

## 十三、设计评审记录

### 评审 R1（agent_dce02a69 fresh session 只读 @ `c4aafeb481`，2026-11-07，60+ 锚点亲验）

**Verdict：PASS-with-revisions——0 Blocker + 3 Major + 5 Minor，修订全部随轮落盘，放行 Phase 2。**

锚点质量结论：14 个站点文件行数与关键行号绝大多数逐字节精确（8 处 off-by-N 级漂移、0 处机制失实）；零重开边界与 live 一致；引用产物（2514 §3.4/§3.5、merge-adjudication §3-D/:236/:247、2498、2497、owner doc 各锚）全部亲证存在；条件申报①②零触发亲证成立；12 个零回退钉测试类全部亲证存在。

| # | 级别 | 发现 | 落款 |
|---|------|------|------|
| R1-M1 | Major | W-04 inherited 集存 raw key 对异名任务 A→B 嵌套共配同 raw global key 场景误报（两任务注册表键独立、无真实嵌套死锁），与裁定自身 global=false 论证逻辑矛盾 | §3.1：inherited 快照改存 taskName 限定键 `taskName+":"+rawKey`，与注册表键形态逐字一致；判定键形态细则落款（祖先链查 rawKey、inherited 查限定键） |
| R1-M2 | Major | W-02 四参签名变更波及两个未登记测试桩：InMemoryWfStore.java:243（nop-wf-core 测试树）+ MockWorkflowStore.java:217（**批外 nop-wf-service 测试树**）——初稿形态 Phase 2 首次编译即红且批外零触碰声明失实 | §6.1：改采「保留三参签名 + 新增四参 default 重载」投影——`AbstractWorkflowStore` 零 diff、两测试桩编译面零触碰（Phase 2 核对项：两桩 git diff 为空亲证） |
| R1-M3 | Major | 裁定 E 两枚 gauge 值载体只能埋在 due 扫描区（消费计数 :189 邻接+失败路径 :250-283），与「:173-188/:206-281 零 diff」字面边界自相矛盾——红① 与 closure audit diff 分组核对互斥 | §七：零 diff 边界改写为语义口径「due 半区退避/fencing/分批的 2514 判据、常量与既有逻辑零改写零重开，gauge 埋点纯新增行允许」，闭包核对标准同步 |
| R1-A-2 | Minor | §6.3「WfConfigs/TaskConfigs 先例」——WfConfigs 不存在（nop-wf-core 无 IConfigReference 持有类） | §6.3：先例勘正为 TaskConfigs/WfSchedulerConfigs，`nop.wf.transfer.max-batch` 落 nop-wf-core 新建 Configs 持有类 |
| R1-B-1 | Minor | C8-016 腿 (ii) 锚「sync 出口 :386 区」失准——catch(Exception) 实际在 :360（:386 为 driveStepFailure 内 state.fail 行） | §4.1：锚勘正为 :360 |
| R1-B-2 | Minor | §4.3「校验须插在 :34 短路口之前否则死分支」理由不成立（校验仅拒绝正值超限，两种插入位行为等价） | §4.3：插入位理由勘正——短路口之前为唯一成文形态（免歧义），非行为分叉 |
| R1-D-2 | Minor | loop-n 迭代数公式 `ceil(|d|/|s|)` 相对 live 含端点语义恒少 1（正确公式=floor+1），且「begin=end 零迭代」失实（实跑 1 次）；「ceil 免登记/floor 须登记」指导倒置 | §6.2：公式勘正为 long 域 `floor(|end-begin|/|step|)+1`，`|end-begin|==0`=1 次迭代天然放行 |
| R1-F-1 | Minor | `hasOpenedTransaction(txnGroup)` 实参取值未定名，防 Phase 2 各自发明 | §八：txnGroup 定名=querySpace（JdbcEntityPersistDriver :163 先例），DaoWorkflowStore 经 ormTemplate.getSessionFactory().txn() 取模板零新增注入，querySpace 由 stepDao 实体模型解析 |

三风险专项核证：W-04 双缺省路径（AbstractForkTaskStep :87-90 每分支独立 newStepRuntime=兄弟 held 集隔离结构性证明）/W-06 Error 原样 rethrow+adapt 重载在位/W-12 事务降级可行性（ITransactionTemplate 逐行在位+nop-wf-core 零 nop-dao 依赖亲证+红⑫ store stub 可桩化）全部 PASS。红钉总表逐钉可落地性评估 PASS（红⑩ 反射腿偏脆已诚实登记结构钉——可接受）。
