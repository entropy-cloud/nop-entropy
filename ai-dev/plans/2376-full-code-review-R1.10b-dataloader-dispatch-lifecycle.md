# 2376 full-code-review R1.10b — GraphQL DataLoader 调度生命周期重构（P1-2.6-004 承接）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: finding `P1-2.6-004`（`2.6-biz-graphql-deep-audit.md` dim12-07）；从 plan 2375（R1.10）拆出——见「拆分缘由」
> Related: plan 2375（R1.10，P1-2.6-001 事务修复，已独立交付）
> Plan Review Log 见文末

## Purpose

修复 DataLoader 唯一调度点时序缺陷（异步 operation+批量 @BizLoader 组合静默挂死）。本缺陷涉及 GraphQL 执行核心的异步调度生命周期，独立 plan 承载。

## 拆分缘由（R1.10 执行记录）

plan 2375 初版拟定的修复锚点（fetchNext 同步返回后 dispatch）在实测中触发测试挂死（graphql-core 测试套件 2 分钟无输出）——该锚点对「异步 op body 内注册 loader 并 await 其结果」的形态不成立：body 完成本身依赖 load future，任何「完成后 dispatch」形态都晚于挂死点。正确修复需重设计调度生命周期。原 :98/:179 提前 dispatch 已恢复（挂死消失，既有测试绿）。

## Current Baseline（2026-10-10 live 亲读 + 审查实码走查）

- **缺陷 live 复核**：`GraphQLExecutor.java` :98（executeOneAsync）/:120（fetchResult）/:179（executeAsync）三处 `dispatchAll` 在 `invokeOperation(s)` **发起后立即**执行。异步 operation body（biz 方法 await loader 结果前）注册的批量 loader 错过 dispatch → 永不执行 → await 侧静默挂死（探针 `_tmp/fcr26-probe/DataLoaderDispatchProbe.java` B 用例）。
- **dispatchAll 语义**：`GraphQLExecutionContext.dispatchAll()`（:151-165）`synchronized`——快照并**清空** loader 注册表 → 逐个 `loader.dispatch()` → 返回 `waitAll`（注册表空返回 null）。清空语义意味着 dispatch 后同 key 再 get 会**重新注册新 loader 实例**（`getDataLoader` 返回 null；`ERR_GRAPHQL_DUPLICATED_LOADER` 守卫仅在未清空时触发）。
- **executor 递归**：`GraphQLExecutor.dispatchAll`（:183-188）promise 完成后 re-dispatch——承接「上一周期 dispatch 完成后 fetchNext 新注册」的 loader；**链条断裂条件=某次 dispatchAll 返回 null（注册表空）**——恰是异步 body 场景（:98 时注册表为空），此后再无任何调度来源 → 死锁本质。
- **fetcher 面**：`BeanMethodBatchFetcher.get()`（:47-76）`synchronized(context)` 内 check-then-register，`loader.load(source)` 在 **:75、锁外**；**load 仅入队，dispatch 才执行**（org.dataloader 语义）；batch load 经 `FutureHelper.futureCall`（:122-129）**内联在 dispatch 调用线程执行**——批量 biz 方法运行线程=dispatch 周期线程，故任何新调度来源必须绑定请求上下文（thread-local）。
- **上下文设施（审查实码走查依据）**：`IServiceContext`（IServiceContext.java:34）`extends IExecutionContext, ISecurityContext`；`IExecutionContext`（:20）`extends IEvalContext, IAttributeSet, ICancellable`——**均不继承 `io.nop.api.core.context.IContext`**。IContext 的获取路径：`IGraphQLExecutionContext.getContext()`（default 方法）→ `ServiceContextImpl.getContext()` 返回构造时捕获的 `IContext`（默认 `BaseContext`）。**`BaseContext.execute(Runnable)`（BaseContext.java:273-280）语义**：`isRunningOnContext()` 为 false 时 `runOnContext` → `ContextTaskQueue.enqueue`——若 biz 线程正在泵队列（`syncing>0`）则入队由泵线程执行；若无人泵（挂死形态：fetch 链停摆、无阻塞泵线程）**enqueue 返回 false → 调用线程内联绑定 context 并 flush 执行**。两条路都闭合，**不存在「投递到永不驱动的队列」**——这是本设计成立的根基。`IContext.execute` 契约声明于 IContext.java:143。
- **调度设施**：`GlobalExecutors`（nop-commons 并发包，graphql-core pom 直接依赖）——`NOP_GLOBAL_WORKER` worker 池（`getExecutor`）承担周期执行；不使用 `globalTimer`（见关键设计约束 4）。
- **既有测试**：`TestBeanMethodBatchFetcher` 5 用例（:117 裸构造 `new GraphQLExecutionContext()`〔serviceContext 兜底 `new ServiceContextImpl()`，:50-52〕+ 手动 `context.dispatchAll()`）。其中两用例在引入周期后**需要确定性化**（见 Phase 1 第 4 项）。
- **已否定修复形态**：①完成后 dispatch（死锁——body 完成依赖 load）；②fetchNext 同步返回后 dispatch（plan 2375 实测挂死）。

## 方案定稿（Candidate Directions 裁决）

**采纳：方向 1′「注册触发的异步调度周期（registration-triggered scheduled dispatch cycle）」**——新 loader 注册时，若无周期在途/已排定，则向 `GlobalExecutors.NOP_GLOBAL_WORKER` 池提交一个 dispatch 周期任务；周期任务经 `getServiceContext().getContext().execute(...)` 绑定请求上下文后执行 `dispatchAll()`（快照+清空+逐 loader dispatch），周期结束按「先清调度标志 → 再查注册表 → 非空则续调」的顺序不变式收敛直至静默。

否定其余候选的理由（留档）：
- **per-load 即时 dispatch**（方向 1 原始形态）：`dispatchAll` 快照+清空语义下，首次 load 即 dispatch 单 key 批 → 同 loader 后续 get 因注册表已清而重注册新实例 → 各自成批——**批量加载被彻底摧毁**（1000 元素列表 = 1000 次单 key 批），不可采纳。
- **定时兜底 dispatch（固定 50ms 延迟）**（方向 2）：引入人为时延（挂死场景 body 至少卡 50ms）且与周期簿记无本质差异，劣于 0 延迟提交，不采纳。
- **operationInvoker 完成前锚点**（方向 3）：需 invokeAsync 语义级支持且 body await 点对引擎不可见（await 发生在 biz 方法内部），不可达，不采纳。
- **单体 dispatch（仅 dispatch 新注册的 loader 实例，不走 context.dispatchAll）**（审查补充分析）：变体 A（仅注册时 dispatch 一次）修好单 key 挂死，但多 source 列表场下 get#2..N 复用已注册 loader、`load()` 入新批缓冲后**无人再调 dispatch**（注册不再触发、:98/:179 已错过）→ 按 source 数量>1 的数据形态**选择性挂死**，比现状更隐蔽；变体 B（每次 get 都 dispatch 共享实例）批退化为 1 key/批——与方向 1 同样的批摧毁结局。均不可采纳。

**关键设计约束（行为语义，实现必须满足）**：
1. **批保护**：同一线程同步 burst 内的多次 load 尽可能落在同一批——周期任务不得在注册线程内联执行（异步提交到 worker 池，burst 后运行）；burst 期间注册表未被清空 → 后续 get 复用同一 loader 实例累积 key。
2. **调度去重与终止不变式**：同一时刻至多一个在途/已排定周期（原子标志）；注册落在在途窗口内不重复提交，由周期末续调承接。**周期结束顺序不变式：先清调度标志 → 再查注册表判空 → 非空则续调下周期**（顺序颠倒会在清标志前查空停机，窗口内注册方因标志仍为真而放弃提交 → 丢唤醒 → 挂死复现）。
3. **上下文绑定**：周期任务经 `getServiceContext().getContext().execute(...)` 在请求 context 绑定下执行（批量 biz 方法内联运行于周期线程，需请求 thread-local context）；兜底 `BaseContext.execute` 的 enqueue-false-内联-flush 语义保证无人泵队列时任务仍被执行（见 Baseline 上下文设施）。
4. **执行线程选择**：周期任务提交到 `NOP_GLOBAL_WORKER` 池（非 `globalTimer`）——batch biz 方法可含 DB IO，内联在 timer 线程（默认池 5、全平台共享定时调度）会拖垮平台定时任务；worker 池为通用工作池，承载此负载合适。
5. **既有行为保持**：`dispatchAll()`/`registerDataLoader`/`getDataLoader` 公开契约与 `GraphQLExecutor` :98/:120/:179 递归链不动；既有 5 用例的「裸构造+手动 dispatchAll」用法继续成立（requestDispatch 是**追加**的活性保障，不是既有路径的替换）。

**已知取舍（登记，经审查修正适用面）**：批拆分竞态**不限于跨线程 burst**——单请求同步路径（`fetchList` 大列表游走 vs worker 周期微秒级启动延迟）也会进竞态：现状同步路径是「全部注册完 → :98 一次性 dispatch」的确定性单批，修复后变为概率性拆批。功能结果不变（全部 load 完成），仅批效率在竞争窗口内损失；彻底消除需 burst 边界追踪（跨线程协调），成本与本缺陷修复不匹配。

## Goals

- 异步 operation+批量 loader 组合不再挂死（端到端超时断言）。
- 同步既有行为零回归（含批保护：同步 burst 键全覆盖无丢失；既有 5 用例经确定性化后全绿）。
- graphql-core 全模块绿。

## Non-Goals

- P1-2.6-001（plan 2375 已交付）；其他 GraphQL 发现。
- 重构 `GraphQLExecutor` 的调度递归链（保持不动）。
- 批拆分竞态的彻底消除（见已知取舍）。

## Scope

### In Scope

- `io.nop.graphql.core.IGraphQLExecutionContext`（新增 `requestDispatch()` 抽象方法——仓内唯一实现 `GraphQLExecutionContext`，无 default 空实现）
- `io.nop.graphql.core.engine.GraphQLExecutionContext`（调度标志 + 周期实现）
- `io.nop.graphql.core.fetcher.BeanMethodBatchFetcher`（注册分支调用 `requestDispatch()`）
- `nop-graphql-core/src/test/java/**`（新增判别/批保护测试 + 既有用例确定性化）
- 治理文件：roadmap MR 表 R1.10b 行增补（含规则 #5 审查记录）、index P1-2.6-004 行、daily log、本 plan

### Out Of Scope

- 其他 GraphQL 发现；`GraphQLExecutor` 递归链改动；批拆分竞态消除

## Execution Plan

### Phase 1 - 实现 + 判别/批保护回归

Status: planned
Targets: `IGraphQLExecutionContext.java` / `GraphQLExecutionContext.java` / `BeanMethodBatchFetcher.java` / src/test

- Item Types: `Fix`

- [x] `GraphQLExecutionContext` 实现调度周期：原子调度标志 + 向 `NOP_GLOBAL_WORKER` 池提交周期任务 + `getServiceContext().getContext().execute(...)` 绑定执行 `dispatchAll()` + 「先清标志 → 再查注册表 → 非空续调」终止不变式；`IGraphQLExecutionContext` 增 `requestDispatch()` 抽象方法
- [x] `BeanMethodBatchFetcher.get()` 注册成功分支（register 后）调用 `context.requestDispatch()`
- [x] 判别测试（挂死消除）：**交付形态=自由形态（closure audit M-1 勘误记录：偏离本行原写的引擎形态）**——`TestGraphQLDataLoaderDispatchLifecycle` 独立线程模拟异步 body 注册 loader 并 await load future，**3s 超时断言**；偏离理由：首轮审查 §7 明确认可自由形态「不需要引擎」，且 fix 未触碰 GraphQLExecutor（引擎形态需为 async-await-loader 场景新建全套 fixture，成本与判别力不匹配；自由形态已覆盖 fix 全部改动面：注册→requestDispatch→worker 周期→context 绑定 dispatch→batch load→await 恢复）
- [x] 批保护测试（引擎自由形态）：单线程 burst N=50 次 get 同一 loader → 等待周期静默后断言**功能不变式：全部批量调用的 keys 并集恰为 50 个且无重复/丢失、每个 source 拿到正确值**；批数断言仅为「≥1 且键全覆盖」（「恰 1 批」受已知取舍约束不可确定断言——受控 executor 注入才可断言，不在本批）
- [x] 既有 5 用例对周期时序的确定性化：`NoAutoDispatchContext` 记录型替身（覆写 requestDispatch 记录不提交——测试替身，非生产静默 no-op）应用于 `testNoExtraArgsKeepsOriginalLoaderName`（原名注册断言保持 + requestDispatch 接线断言==1）与 `testConcurrentFirstRegistrationSharesLoader`（原断言集保持 + 接线断言≥1）；其余 3 用例经交错分析竞态安全不动（每 loader 键集不变、批数不变、顺序分布与改前一致）
- [x] stash 判别：`git stash push` fetcher 单文件（接口/上下文保留使替身可编译）→ 判别双红（async body `TimeoutException` ERROR + burst FAILURE，各 3s）→ pop 恢复 → 2/2 绿
- [x] 本 Phase owner-doc 处置：`api-and-graphql.md`「扩展返回字段的默认做法」节增「批量 loader 的 dispatch 时机」段（注册即请求异步周期/异步 body 场景活性保障/聚合批/调用方无需自驱 dispatchAll）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-10.md` R1.10b 条目）
- [x] `./mvnw test -pl nop-service-framework/nop-graphql/nop-graphql-core -am -T 1C` BUILD SUCCESS

Exit Criteria:

- [x] 端到端判别绿且带超时断言；stash 判别 FAIL→PASS 记录在本 plan
- [x] 批保护断言在案（键全覆盖不变式）；既有 5 用例确定性化后全绿
- [x] graphql-core 全模块绿（`-am -T 1C` BUILD SUCCESS）
- [x] owner-doc 处置已裁定并落地（api-and-graphql.md dispatch 时机段）
- [x] **无静默跳过**：requestDispatch 提交失败即清标志并重抛（NopException.adapt），无吞异常路径

## Closure Gates

- [x] 挂死消除（超时断言回归锁定）
- [x] 批保护断言在案（键全覆盖不变式）
- [x] graphql-core 全模块绿
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：stash 判别真实 FAIL→PASS
- [x] `./mvnw -q compile -pl nop-service-framework/nop-graphql/nop-graphql-core -am` 通过（checkstyle 随构建门禁）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] roadmap MR 表 R1.10b 行增补（含规则 #5 审查记录）+ index P1-2.6-004 行更新

## Deferred But Adjudicated

### 跨线程/同步游走批拆分竞态的消除

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅竞争窗口内的批效率损失（功能正确性不变）；现状 executor 递归链本就存在中途快照竞态；彻底消除需 burst 边界追踪（跨线程协调），成本与本缺陷修复不匹配。
- Successor Required: `no`

## Closure

Status Note: P1-2.6-004 闭合：注册触发异步调度周期落地（requestDispatch → NOP_GLOBAL_WORKER → context 绑定 dispatchAll → 「先清标志→再查注册表→非空续调」终止不变式），挂死消除（stash 判别双红 TimeoutException → 恢复双绿）；**执行中发现并修复 strand 竞态**（load 移入 synchronized 临界区——闭包审确认锁语义成立：dispatchAll 与 fetcher 同监视器，check-register-load 原子于快照+清空）；批保护键全覆盖不变式 + 既有 5 用例替身确定性化全绿；graphql-core 全模块 155 tests 重放 0 失败。独立 closure audit 判定 Can Close（roadmap R1.10b 行规则 #5 审查：可合入）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_538c8059（独立 fresh-session closure auditor，2026-10-10，含 roadmap 增补行规则 #5 对抗审查）
- Evidence:
  - 实现 9 项逐锚 PASS：IGraphQLExecutionContext.java:41 抽象方法；GraphQLExecutionContext.java:48/:164/:168/:177/:178-184/:169-172（标志/去重/worker 池/绑定执行/终止不变式顺序/失败重抛）；BeanMethodBatchFetcher.java:76（requestDispatch）/:78-81（strand 注释）/:82（load 在 synchronized :64-83 内）
  - 判别独立重放：stash fetcher 单文件 → 4 红（async body ERROR + burst FAILURE/TimeoutException 3.25s，与 plan 记录逐字吻合）→ pop → 7/7 绿——Anti-Hollow 真实成立
  - 验证命令重放：7/7 绿 ×4 次重复 + 全模块 155 tests 0 失败 + compile exit 0 + doc-links 0 errors + git status 变更集与 Scope 吻合无越界
  - owner doc：api-and-graphql.md:204「批量 loader 的 dispatch 时机」段与实现一致
  - roadmap R1.10b 行规则 #5 审查：**可合入**（plan 编号/finding dim12-07/R1.10 拆出依赖/插入位置/技术描述全部实证；前向引用「closure audit 见本段」以本落账为生效条件）
  - Deferred 分类检查：批拆分竞态 watch-only residual 诚实（修复引入的已披露新取舍、功能不变式已由测试锁定、成本论证在案、三处披露一致）
  - 关单前置动作已按 audit 处方完成：M-1 判别测试形态偏差勘误（自由形态交付+偏离理由留档）+ m-1/m-2 措辞勘误 + m-3 import 归位 + 本 Evidence 落盘 + 全 checklist 勾选 + Plan Status 翻 completed；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 关单前复跑退出码 0

Follow-up:

- 批拆分竞态彻底消除：watch-only residual（见 Deferred But Adjudicated）
- closed-context 重提交自旋（audit m-4 观察）：context 已关闭且注册表非空时以 worker 池 RejectedExecutionException 自限，修复前同状态为静默挂死，属可接受劣化，watch

## Plan Review Log

- 2026-10-09 初稿（三候选方向未定稿）随 plan 2375 执行产出，未送审。
- 2026-10-10 重设计稿 v1（方案定稿方向 1′）。首轮独立审查 agent_20248b50 **NOT READY**（2026-10-10，实码走查含 BaseContext/ContextTaskQueue 语义亲读）：**B1** `getServiceContext().execute` 不存在（IServiceContext/IExecutionContext 均不继承 IContext，正确路径 `getContext().execute`）；**M1** execute 的 enqueue-false-内联-flush 语义依据未写明；**M2** 既有 2 用例受周期抢跑变竞态 flaky；**M3** 调度标志清除顺序不变式未钉死；**M4** 批拆分竞态适用面被低估（同步游走也竞态）+ globalTimer 线程承载 batch biz 方法的吞吐风险；m1-m5（行号/机制细节层级/owner-doc 裁定项/Closure Gates 模板项/第四方案留档）。**本轮全部修订**：B1 调用路径更正、M1 语义依据写入 Baseline、M2 确定性化测试项、M3 终止不变式钉死、M4 worker 池替代 globalTimer + 取舍适用面更正、m1-m5 全落。第二轮复核 agent_20248b50 **NOT READY（收窄至测试规格层）**：B1/M1/M3/M4 全部落实与实码一致、主体设计定稿；新发现 N1（批保护「恰 1 批」断言与已知取舍自相矛盾→改键全覆盖不变式）+ N2（testNoExtraArgs 的等静默前置方向写反→改 race-free 观测点；concurrent 用例断言改键全覆盖）+ 交叉引用笔误（取舍 4→约束 4）——三项已修订；点验（同 agent）确认 N1/N2-a 通过、N2-b 需再改一行（concurrent 用例键覆盖口径在最坏交错下仍失败——同 bullet 前半的记录型替身抑制周期时序、保留原断言集）——已按点验单修正，**达到 READY，进入执行**（点验单明示改后无需再送审查）。
