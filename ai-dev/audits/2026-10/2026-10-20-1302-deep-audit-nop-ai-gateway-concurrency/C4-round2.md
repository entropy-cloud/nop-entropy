# C4 深挖第 2 轮 — nop-ai-gateway（WI 3.3）

- **审计日期**: 2026-10-20
- **审计员**: C4 维度深挖第 2 轮盲审员（盲审纪律同第 1 轮；编号续接 C4-007 起）
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118 + L199–217（C4 正文）+ L351–377（附录 A）

## 深挖范围与方法

按派单深挖面逐一执行：

1. **池拒绝策略语义（同族其他提交点）**：全源码根 grep 执行器提交点（`runAsync|supplyAsync|execute(|submit(|new Thread|Executors.`）复核——模块内**唯一**执行器提交点 = `fanOutToListeners` 的 `CompletableFuture.runAsync(..., executor)`（ChannelMessageServiceImpl L393）；`agentEngine.execute`（FeishuConnector L345）是引擎 API 调用（提交发生在 nop-ai-agent 池，越界留痕）；`FutureHelper.success/reject`（登录/饱和路径）为已完成 stage 工厂，无提交。**结论：C6-003 已立案的 RejectedExecutionException 面没有第二个同族提交点——但注入式执行器形态（`setFanOutExecutor`）是同一提交点的未治理变体，立案为 C4-007**。
2. **线程命名/daemon**：`FanOutWorkerThreadFactory`（L254-263）——命名 `ai-channel-fanout-N`（AtomicInteger 计数，可观测）+ daemon=true，复核合格；对照平台 `NamedThreadFactory` 惯例一致。owned 池 `newFixedThreadPool` 核心线程常驻不超时——量级 max(2,CPU) 条空闲 parked 线程，有界且 daemon，不立案。**负验证**。
3. **FutureHelper 取消链覆盖**：模块内 `FutureHelper` 使用点全量 grep——`FutureHelper.success`（ChannelLoginScanProcessor L159）、`FutureHelper.reject`（两编排饱和路径）——均为已完成 stage 的包装原语，无 `orTimeout`/取消传播链经 FutureHelper；超时全部走 `orTimeout`（JDK Delayer）且其取消语义已被第 1 轮 C8-002/C4-002 穷尽。**负验证**。
4. 补充盘点：`close()` 的 awaitTermination(2s) 有界等待 + 中断恢复（L245-249）复核合格；mode-2 `sendAsync` 不占池线程（第 1 轮已豁免）复核成立。

## 新增发现

### [C4-007] 注入式 fanOutExecutor 形态无执行器契约校验——caller-runs 池使 listener 内联回传输线程且 30s 超时后 workerRef 捕获的正是传输线程并对其 interrupt，I3 R-2-1 有界派发契约被注入配置静默击穿

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:206-209`（注入口）、`:216-234`（resolve 优先注入）、`:391-436`（workerRef 捕获与超时中断）
- **证据片段**:
  ```java
  @Inject
  public void setFanOutExecutor(@Nullable Executor fanOutExecutor) {   // L207 —— 任意 Executor 可注入
      this.fanOutExecutor = fanOutExecutor;
  }
  private Executor resolveFanOutExecutor() {
      Executor injected = this.fanOutExecutor;
      if (injected != null) { return injected; }                        // L218 —— 注入优先，无任何语义校验
      ...
  }
  // 任务体（L391-403）—— 对注入池同样成立：
  final AtomicReference<Thread> workerRef = new AtomicReference<>();
  CompletableFuture<Void> raw = CompletableFuture.runAsync(() -> {
      Thread worker = Thread.currentThread();
      workerRef.set(worker);            // caller-runs 池下 = 调用方（传输/消费）线程
      ...
      listener.onInbound(message);
  ```
- **严重程度**: P3（降档依据：标准装配不注入 fanOutExecutor——beans.xml 无该 property wiring，owned 池为唯一生产路径；触发依赖部署侧主动注入）
- **并发场景**: 部署侧注入 caller-runs 语义执行器（如 `Executors.newCachedThreadPool` 高饱和回退、平台池配置 CallerRunsPolicy、或同步语义 Executor）后，`fanOutToListeners` 的 per-listener 任务在**提交者线程**（mode-1 传输回调线程 / mode-2 骨干消费线程）内联执行。
- **触发时序**: T1 传输线程调用 `dispatchInbound` → `fanOutToListeners` → `runAsync(task, injectedCallerRunsPool)` → 任务同步在 T1 自身执行 → `workerRef.set(T1)` → `listener.onInbound` 在传输线程阻塞（正是 I3 R-2-1 声明要消灭的形态，javadoc L60-63"a hanging listener can no longer block the transport thread"被注入配置击穿）→ 30s 后 `orTimeout` 触发 → `cancelled.set(true)` + `raw.cancel(true)` + **`workerRef.get()` = T1 传输线程** → `worker.interrupt()`（L433-436）——对 FeishuClient WS 读线程（mode-1）注入中断标志：若其正阻塞在可中断调用则被意外唤醒、若不可中断则标志滞留至 finally `Thread.interrupted()`（L410）消费。交叠后果：ACK 延迟、正在进行的传输帧处理被中断扰动，且该行为对注入方完全不可见（无契约文档、无校验、无 WARN）。
- **现状**: 注入口 javadoc 仅声明"the injector owns its lifecycle"（生命周期归属），对**执行语义**（不得 caller-runs / 需有界队列 / 拒绝策略）零声明零校验；owned 池路径的第 1 轮四条立案（C4-001/002/003/006）均不覆盖注入变体。
- **风险**: 与第 1 轮 C4-005 同后果族（传输线程被业务阻塞 → ACK 延迟 → 服务端重投递），但根因是模块对注入面缺乏防御；有界注入池的 RejectedExecutionException 面则与 C6-003 同族（已在彼条失效面 ① 内，本条不重复计数）。
- **建议**: `setFanOutExecutor` javadoc 补执行器契约（必须有界队列 + 非调用方执行策略 + 拒绝策略显式）；或 resolve 时对 `Executor` 做能力探测不可行则至少在类 javadoc 的部署契约段声明；评估复用平台 `IThreadPoolExecutor`/`ThreadPoolConfig` 作为注入口径类型替代裸 `Executor`（类型层即排除 caller-runs 语义漂移）。
- **失效面枚举（F3）**: 执行器提交/消费形态全量清单——①owned 池（默认路径，第 1 轮 C4-001/002/003/006 已立案四条，不重复）；②注入 caller-runs 池（**本条缺陷面**）；③注入有界池（REE 面 = C6-003 已立案同族，不重复）；④注入有界队列正常池（契约满足，无缺陷）；⑤mode-2 `sendAsync`（不经执行器，豁免）；⑥`agentEngine.execute`（nop-ai-agent 池，越界留痕——其拒绝/内联语义由该模块批次审）。
- **信心水平**: 确定（机制链完整：workerRef 捕获语义与 interrupt 路径为代码事实；触发依赖注入配置）
- **误报排除**: 非"有界声明存在的池"误报——本条恰是声明缺失面；非第 1 轮已立案条目的重复——owned 池四条 + C6-003 均不涉及注入语义校验；按"低触发面配置依赖"如实降档 P3，不夸大为生产必现。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现。机制链回码证实：workerRef 捕获语义（L391-395，任务体恒在执行器线程取 `Thread.currentThread()`）与超时 interrupt 路径（L433-436）对注入池同样成立——caller-runs 池下 `workerRef.get()` 即调用方传输线程，interrupt 扰动面成立；注入口零契约校验（javadoc 仅生命周期归属声明）证实。触达面双层休眠：`fanOutExecutor` 无 beans.xml wiring（L108-126 证实）+ fan-out 面本身零生产触达（dispatchInbound 零生产调用）——P3 判级恰当不升档。与第 1 轮四条（owned 池面）及 C6-003（REE 面）边界清晰，不重复

## 维度集外线索（overlay）

—— 本维度第 2 轮无新增集外线索。

深挖面负验证留痕（不立案）：①模块内执行器提交点唯一性（grep 全根证实，REE 同族无漏网提交点）；②FanOutWorkerThreadFactory 命名 + daemon 合格；③FutureHelper 无取消链（仅成功/失败原语）；④close() awaitTermination 有界 + 中断恢复合格；⑤owned 池核心线程常驻有界（≤ max(2,CPU)）且 daemon。

## 继续判定

新增 1 条（P3×1，无 P0/P1/P2）。判定：**终止信号**——①第 1 轮 4×P1 的主站点（孤儿池/无界队列/停机断链/传输线程内联）在第 2 轮定向复核中未再产出同量级新面，本轮唯一新发现为配置依赖的 P3 防御缺口；②三个定向深挖面（提交点同族/线程治理/FutureHelper）均以负验证或唯一变体立案闭合；③提交点唯一性由全根 grep 证实，失效面封闭。建议 C4 维度不再派第 3 轮，转入 Phase 4 复核合并。

---
*审计员：C4 深挖第 2 轮盲审。发现计数：P0=0，P1=0，P2=0，P3=1；CL 追加=0。*

## 复核结论（WI 3.3 独立复核，合并批 C3+C4）

C4-007 **保留(P3)**：workerRef 捕获 + interrupt 路径对注入执行器形态的机制链逐行回码证实；触达面为双层休眠（`fanOutExecutor` 无 beans.xml wiring + fan-out 面零生产触达），P3 判级恰当。该条为第 1 轮四条 owned 池立案之外唯一独立保留项——"提交点唯一性"负验证结论（模块内唯一执行器提交点）经复核成立，注入变体立案边界清晰。全维度判定（6 降 1 保，降档全部为零生产接线触发面条件化、机制零修正）见 C4-thread-pools.md「维度复核结论」。
