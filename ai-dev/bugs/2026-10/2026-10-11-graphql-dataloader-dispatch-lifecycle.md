# 2026-10-11 GraphQL DataLoader 调度生命周期缺陷（异步 operation 批量字段静默挂死）与修复方式返工

> 承接 audit finding P1-2.6-004（dim12-07）；替代 plan 2376（R1.10b）的 requestDispatch 调度周期方案。
> 复现探针：`_tmp/fcr26-probe/DataLoaderDispatchProbe.java`（A-sync 9ms / B-async+loader 挂死 / C-async-no-loader 206ms）。

## Problem

- 异步顶层 operation（biz 方法返回未完成的 `CompletionStage`）+ 批量 `@BizLoader` 字段组合：请求**静默挂死、无超时、无错误**。
- 批量 biz 方法一次都不会被调用（探针 trace：挂死 3 秒内零 `loader-batch-invoked`），对照的异步无 loader 请求正常完成。
- 同步 operation + 批量字段完全正常——缺陷只出现在异步形态。

## Diagnostic Method

- 判别难点：挂死无任何日志/错误，且同步路径完全正常，容易误判为调用方问题。
- 先查调度点分布：`GraphQLExecutor` 的 `dispatchAll` 只在三处 operation **发起时**的顺序代码（:98/:120/:179）+ 一个续燃回调（`promise.thenAccept`）。关键证据：续燃以「上一轮真的 dispatch 到东西」为前提——第一轮注册表为空时返回 null，调度链从未武装。
- 实跑时间线（探针加时间戳+线程名）：t=0 发起并空跑 dispatch；t=200ms body 在 ForkJoinPool 线程完成 → fetchNext 游走注册 loader → 此后无任何调度来源 → batch 函数零调用。
- 被排除的假设：
  - 「operation body 内注册并 await loader」——不可达：`requireDataLoader` 全仓零调用方，注册点只有两个 fetcher，均由 executor 游走驱动。plan 2375/2376 对场景的描述失真，并据此否定了「调度点后移」族方案。
  - 「java-dataloader 自动调度」——v3 不 auto-dispatch，探针 `-Dprobe.trace=true` 栈证实。
- 决定性证据：还原修复前 fetcher 的对照实验——A 同步用例 batch 跑在 main 线程、栈为 `executeAsync:179 → dispatchAll`（同步游走内联先于发起时调度点）；B 异步用例 HANG。

## Root Cause

- 游走回合返回的 future **本身包含等待 load 完成**（fetchNext 收集的 promise 树里有 load future）→ 依赖链倒挂：`body 完成 → 游走注册 → load 入队 →【必须在此 dispatch】→ load 完成 → 回合 future 完成`。任何挂在「完成」之后的 dispatch 都晚于挂死点。
- executor 的全部调度点都在发起时刻；异步场景注册发生在其后，且递归链因首轮空转从未武装——注册对调度不可见。

## Fix（2026-10-11 返工，替代 plan 2376 的 requestDispatch 周期）

plan 2376 的「注册触发全局 worker 池调度周期」方案有三个缺陷（本轮实跑证实）：
1. 周期抢跑同步路径的注册游走——确定性单批退化为概率性拆批（修复后 batch 从 main 线程跑到 `nop-global-worker` 线程）；
2. 只接线了两个注册点之一——`OrmBatchLoader.makeDataLoader`（5 个 ORM fetcher 的公共注册点）未接，同场景仍挂死；
3. 全局 worker 池成为请求活性依赖，且迫使 `loader.load()` 移入锁临界区。

**最终方案：游走回合出口泵（调度权完全归 GraphQLExecutor）**
- `GraphQLExecutor.invokeOperation` 的 `thenCompose` 与 `thenFetchNext` 两个续行接缝：fetchNext 返回后、future 交给上游前，各泵一次 `dispatchAll`——游走注册与上游等待之间唯一的确定性缝隙。
- `:98/:120/:179` 发起点与 `:183-188` 递归保留为兜底；fetcher/context 全部退出调度职责，删除 `requestDispatch` 机制（接口方法、调度周期、AtomicBoolean、GlobalExecutors 依赖，净删约 50 行）。
- `BeanMethodBatchFetcher` 保留 `synchronized(context)` 的 check-register-load 原子性（与并发回合 dispatchAll「快照+清空」互斥防丢 key，与触发方无关）。
- 批语义：每回合一次 dispatch，同步路径恢复与缺陷前一致的确定性单批（探针 A 复验：batch 回到 main 线程）；异步路径在 body 完成线程上被接缝泵救活（探针 B 复验：206ms 完成，不经 worker 池）。

## Tests

- `nop-graphql-core/src/test/.../engine/TestGraphQLDataLoaderDispatchLifecycle.java`（新建，引擎形态，4 用例）：异步列表（挂死判别+恰 1 批+键全覆盖）、同步列表（恰 1 批零回归）、嵌套异步字段（thenFetchNext 接缝判别）、异步单对象（探针 B 回归化）——全部带 5s 超时断言。
- `.../fetcher/TestBeanMethodBatchFetcher.java`：删除 NoAutoDispatchContext 替身与 requestDispatch 接线断言，原 5 用例恢复确定性。
- Anti-Hollow：stash `GraphQLExecutor.java` → 3 个异步用例 TimeoutException 红、同步绿；pop → 4/4 绿。模块 157 tests 全绿。

## Affected Files

- `nop-graphql-core/.../engine/GraphQLExecutor.java`（两接缝加泵 + 驱动模型注释）
- `nop-graphql-core/.../engine/GraphQLExecutionContext.java`（删调度周期）
- `nop-graphql-core/.../IGraphQLExecutionContext.java`（删 requestDispatch）
- `nop-graphql-core/.../fetcher/BeanMethodBatchFetcher.java`（删触发调用，锁不变式注释更新）

## Notes For Future Refactors

- **不变式**：批量 loader 的注册必须只发生在 GraphQLExecutor 的游走回合内（fetcher.get 调用链）。绕过引擎直接向 context 注册 loader 并 await 的用法不受调度保障（`requireDataLoader` 当前零调用方，保持为零是约束成立的条件）。
- `dispatchAll` 的「快照并清空注册表」语义意味着同 key 再次 get 会重注册新实例——任何在回合外新增 dispatch 触发点的改动都可能重引丢 key 竞态，须与 fetcher 的 `synchronized(context)` 原子性联动评估。
- 订阅路径（GraphQLEngine 直接 `fetcher.get` 构造 Publisher）不走游走生命周期，批量 fetcher 用于订阅根字段属异形，未接泵。
