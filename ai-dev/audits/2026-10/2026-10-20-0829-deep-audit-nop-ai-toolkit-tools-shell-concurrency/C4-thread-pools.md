# C4 线程池与执行器治理 — nop-ai-toolkit-tools-shell（WI 3.2）

## 审计范围与口径

- **单批次全源码根覆盖声明**：本批次一次性覆盖全部三个源码根，逐文件审读（手写口径，排除 `_` 前缀生成文件）：
  - `nop-ai/nop-ai-toolkit/src/main/java`（74 文件）——重点审读 sandbox 族（HostBashSandbox/DockerBashSandbox/BashExecutionTracker）、BashExecutor、ToolManagerImpl、ToolExecuteContext、Graphql/Jq/Http/SearchEngine 等 17 个 tool executor；mavenvfs 子包逐文件确认无线程创建点（VfsMavenCli 仅拼装命令字符串，`execute()` 是 stub，不启动进程）。
  - `nop-ai/nop-ai-tools/src/main/java`（23 文件）——grep 全量确认**零线程/零执行器创建点**（无 Thread/Executor/Schedule/定时器），业务跑在平台调用线程上；C4 维度无发现。
  - `nop-ai/nop-ai-shell/src/main/java`（44 文件）——重点审读 ShellCommandExecutor（pipeline 阶段并发模型、`&` 后台 job）、IBackgroundJobSink SPI、BlockingQueueShellInput/Output、ExternalCommandAdapter（本模块内是 throw-UnsupportedOperationException 的桩，真实实现在 nop-shell，不在本批次范围）。
- **口径纪律声明**：
  - 盲审纪律：未读 `ai-dev/audits/**`（本文件除外）、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`；未用 git log 查修复历史。
  - 每条发现给出具体触发时序（T1/T2/交叠后果）；写不出具体时序的标注并降级 P2。
  - F3 失效面枚举为硬性字段：每条发现均 grep 全部同族兄弟并逐一登记状态。
  - 平台基础设施（nop-commons `GlobalExecutors`/`StandardThreadPoolExecutor`/`ExecutorHelper`）自身实现不重审，只审使用方式；为确定使用方式正确性而核对的平台事实（池参数、拒绝策略、`FutureHelper.bindCancel` 取消传播）在相应条目中注明出处。
  - 降档口径：请求触达面——启动期降档**不适用**；nop-ai-shell 当前无生产消费者（`nop-ai-agent` 测试源码 javadoc 自述 "orphan module"）仅作为现状上下文记录，不据此降档（该模块是公开嵌入 API，设计意图为 "A production embedding wires the same one-liner at its engine assembly point"）。
  - 误报排除已执行：`context.getExecutor().submit()` 返回的 CompletableFuture 经 `ExecutorHelper.submit` → `FutureHelper.bindCancel(future, f)` 绑定底层 FutureTask，`future.cancel(true)` **会**中断工作线程——JqToolExecutor 的取消链路有效，不作为发现。

## 线程池清单表（池 × 参数 × 队列 × 拒绝策略 × 关闭路径 × 判定）

| # | 池/线程 | 创建点 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|---|
| 1 | `nop-bash-sandbox-reader`（每命令一线程） | HostBashSandbox.java:92-95 | 裸 `new Thread`，daemon=true，**所有实例同名无唯一后缀** | —（管道读） | — | `awaitReader`：join ≤2s 后 `interrupt()`（对 `InputStream.read` 无效） | **C4-001**：孙进程持管道时线程泄漏 |
| 2 | `nop-bash-sandbox-docker-reader`（每命令一线程） | DockerBashSandbox.java:107-110 | 同上 | — | — | 同上；且超时路径先过 `killContainer`（可挂死，见 C4-002） | **C4-001 / C4-002** |
| 3 | `docker kill` / `docker rm` 子进程（同步短命令） | DockerBashSandbox.java:266-275 | 调用方线程同步执行 | — | — | `waitFor(5s)` 超时后**不 destroy**；drain 循环先于 waitFor、无超时 | **C4-002**：挂死时超时/取消路径永久阻塞 |
| 4 | `nop-global-worker`（缺省 executor，跨模块共享） | ShellCommandExecutor.java:60 缺省回退 `GlobalExecutors.globalWorker()` | 平台缺省：core=4，max=`nop.commons.concurrent.global-worker.maxPoolSize`（缺省 30），daemon，`NamedThreadFactory("nop-global-worker")` | TaskQueue（LinkedBlockingQueue）capacity=10000，tomcat 式"先扩线程后入队" | abort（StandardThreadPoolExecutor 拒绝即抛 RejectedExecutionException，带限时 force 重试） | 平台 bean 持有（无模块级 shutdown 义务） | **C4-003**（阶段提交失败→僵尸阻塞阶段）、**C4-004**（共池无隔离） |
| 5 | tool 执行池（注入式 `IThreadPoolExecutor`） | ToolExecuteContext.java:87,120（Builder 注入，**无缺省值，可为 null**）；17 个 executor 调 `context.getExecutor().submit(...)` | 由嵌入方决定（本模块不建池） | 由实现决定 | 由实现决定 | 注入方负责 | **C4-005**（提交同步抛出的排队悬挂）、**C4-007**（后台 submit→run 无回滚） |
| 6 | `&` 后台 job body | ShellCommandExecutor.java:363-365 经 `IBackgroundJobSink.submit` fork 到 sink 池 | sink 实现决定（SPI，本模块无实现） | — | — | sink 拥有（close() 明确不取消，L635-641） | **C4-006**：body `.join()` 阻塞在 sink 线程，池身份无守卫 |
| 7 | mavenvfs 构建线程 | 无（VfsMavenCli.execute 为 stub，无 ProcessBuilder 活代码） | — | — | — | — | 无发现 |
| 8 | nop-ai-tools | 无线程创建点 | — | — | — | — | 无发现 |
| 9 | 定时调度器（scheduleAtFixedRate 族） | 无（三模块 grep 零命中，基线 C4b 节亦为空） | — | — | — | — | 无发现（无 Error 逃逸面） |

---

## 发现

### [C4-001] sandbox reader 泵线程在孙进程持管道时永久泄漏（interrupt 对流读无效）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java:92-95,188-198`；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:107-110,321-331`
- **证据片段**:
  ```java
  // HostBashSandbox.java:92-95（DockerBashSandbox.java:107-110 同型）
  Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
          "nop-bash-sandbox-reader");
  reader.setDaemon(true);
  reader.start();
  // HostBashSandbox.java:188-198
  private static void awaitReader(Thread reader, int wallSeconds) {
      long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
      try { reader.join(graceMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      if (reader.isAlive()) { reader.interrupt(); }   // ← 对阻塞在 InputStream.read() 的线程无效
  ```
- **严重程度**: P2
- **并发场景**: 每次工具调用一条泵线程（reader）+ 提交命令的 executor 工作线程（BashExecutor.doExecute 阻塞在 `process.waitFor`）。reader 阻塞在进程管道的 `in.read()`（HostBashSandbox.java:156 / DockerBashSandbox.java:289）。
- **触发时序**: T1：AI 会话执行 `sh -c "some-server &"`（DESTRUCTIVE_COMMAND 黑名单不拦 `&`；server 进程继承合并后的 stdout 管道），主 shell 立即退出 → T2：`process.waitFor`（HostBashSandbox.java:117）正常返回，`awaitReader` join 0.5–2s 超时，`reader.interrupt()` 发出——但 `InputStream.read()` 不响应中断，reader 仍阻塞在管道读 → 交叠后果：只要孙进程存活（dev server 可存活数小时乃至永不退出），reader 线程及其持有的 StringBuilder（≤maxOutputBytes=1MiB）持续滞留；每次此类调用泄漏一条线程。非超时路径（L129）不 kill 进程树，无任何兜底回收。
- **现状**: daemon 标记保证了不阻塞 JVM 退出，但长运行服务内线程与内存随调用次数无界增长；且所有 reader 线程共享同一名字，线程 dump 中无法区分泄漏来源与在途任务。
- **风险**: AI agent 高频"后台起服务"类命令（真实工作负载）下线程数线性上涨（每线程约 1MB 栈预约）；观测上同名线程无法定位泄漏点。
- **建议**: (1) 非超时路径在 `awaitReader` 超时后也执行 `process.destroyForcibly()`（或至少 `ProcessHandle` 级关闭管道）强制 EOF；(2) 线程名加唯一后缀（如 callId），复用平台 `NamedThreadFactory` 语义；(3) 中期可将 reader 收敛为共享 daemon 池 + 超时强制关闭进程，而非裸线程。
- **失效面枚举**（F3 同族清扫）: ① HostBashSandbox.execute/drainStream——本条主对象；② DockerBashSandbox.execute/drainStream——同型代码，同缺陷，一并修；③ DockerBashSandbox.runShortCommand 的 drain 循环（L266-273）——同为不可中断阻塞，但属超时治理缺陷，单列为 C4-002；④ ShellCommandExecutor pipeline 无进程管道（内存队列），不受影响；⑤ nop-ai-shell ExternalCommandAdapter 本模块为桩，无泵线程。全部同族已登记。
- **信心水平**: 确定（`InputStream.read` 不响应 interrupt 是 JDK 既有语义；代码路径已逐行核实）
- **误报排除**: 不属于"低频路径粗粒度 synchronized"豁免——这是成功路径上的资源泄漏且触发命令形态在 AI bash 工具中常见；也不是平台设施未用——平台无进程泵线程设施，问题是自建线程的关闭路径缺陷。
- **复核状态**: 保留(P1，并案校准)｜新发现——同站点同案 **C6-001**（同缺陷、面更全：另含 stdin 管道不关闭、取消路径不关流、fd 泄漏，原判 P1），建议以 C6-001 为主登记合并；C4 文本漏计 fd 泄漏面致原判 P2 偏保守，并案后随 C6-001 校准为 P1（成功路径 + `server &` 型真实工作负载 + 每次永久泄漏一线程一 fd，长会话 agent 下无界增长）；本条独有贡献（同名线程不可观测）随并案同 diff 修

### [C4-002] DockerBashSandbox 短命令无超时覆盖且不销毁进程——wall-clock 超时与取消路径可被挂死

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:266-275`（runShortCommand）；调用方 L137-141（超时路径）、L116+253-264（cancel 钩子）
- **证据片段**:
  ```java
  private static void runShortCommand(String... argv) throws IOException, InterruptedException {
      Process p = new ProcessBuilder(Arrays.asList(argv)).redirectErrorStream(true).start();
      byte[] buf = new byte[1024];
      try (InputStream in = p.getInputStream()) {
          while (in.read(buf) != -1) {   // ← 先于 waitFor，且无超时
              // discard
          }
      }
      p.waitFor(5, TimeUnit.SECONDS);    // ← 超时后不 destroyForcibly
  }
  ```
- **严重程度**: P2
- **并发场景**: ① 工具 executor 线程命中 `wallSeconds` 超时后，在 doExecute → killContainer（L138）同步执行 `docker kill`/`docker rm`；② 心跳/取消线程经 BashExecutionTracker.cancelExecution（BashExecutionTracker.java:82-98）执行 cancelAction=killContainer（DockerBashSandbox.java:116）。两条路径都同步阻塞在 runShortCommand。
- **触发时序**: T1：docker daemon 半挂死（CLI 连接后不输出也不退出——daemon wedge 的真实形态），工具线程 waitFor 超时进入 killContainer → T2：runShortCommand 阻塞在 `in.read(buf)`（drain 先于 waitFor，5s 超时从未生效）；若 CLI 退出但输出流被孙进程持有则卡在 waitFor 后也无销毁 → 交叠后果：号称的 wall-clock 强制（javado c 明示 "wallSeconds → enforced via Process#waitFor + docker kill on timeout"）被绕过，工具调用永不返回；取消路径上则是心跳线程被永久占用。
- **现状**: 5s 超时只覆盖"EOF 之后的退出等待"，不覆盖"读输出"这个更早更常见的阻塞点；超时分支缺少 `p.destroyForcibly()` 兜底。
- **风险**: 沙箱最核心的超时保证在 docker 故障场景下静默失效；被挂死的线程可能是池线程（超时路径）或心跳/取消线程（cancel 路径），逐步耗尽并发预算。
- **建议**: 复用同文件同族正确实现 HostBashSandbox.killTree（L208-226：`waitFor(2s)` + 超时 `destroyForcibly`）的结构：先异步/限时排水，再 waitFor(5s)、超时必 `destroyForcibly()`；drain 改为单独泵线程或直接 `redirectTo(DESTROY_ON_EXIT)`/`inheritIO` 丢弃输出。
- **失效面枚举**（F3 同族清扫）: ① docker kill 分支——本条；② docker rm -f 分支（L259-263）——同型同修；③ HostBashSandbox.killTree——已正确（waitFor(2s)+destroyForcibly+中断恢复），是修复模板；④ BashExecutionTracker.cancelExecution 的 `process.destroyForcibly()`（L87-88）——无输出排水，不受本缺陷影响；⑤ VfsMavenCli——stub 无进程。全族已覆盖。
- **信心水平**: 确定
- **误报排除**: 不是"错误路径资源泄漏"豁免类——它使主保护（wall-clock）失效并阻塞调用/取消线程，是超时治理缺陷（F5：超时未覆盖全部等待点），非单纯清理疏漏。
- **复核状态**: 保留(P2)｜新发现——同站点同案 **C6-003**（挂死面主登记）+ **C8-009**（CLI 进程泄漏面）；本条独有面：`BashExecutionTracker.cancelExecution`（BashExecutionTracker.java:89-96）在心跳/取消线程上**同步**执行 killContainer 的占用面；触发前置为 docker daemon 故障（外部基础设施失效面），P2 校准成立；同文件 HostBashSandbox.killTree（waitFor(2s)+destroyForcibly）为已修实例而 Docker 变体未同修——F3"修实例不修类别"残留形态，修复 diff 必须覆盖 kill 与 rm -f 两分支

### [C4-003] pipeline 阶段提交中途失败无清理——已提交上游阶段永久阻塞 queue.put，执行器线程永久泄漏

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:243-286`（executePipeline）；`io/nop/ai/shell/io/BlockingQueueShellOutput.java:15,27-35`
- **证据片段**:
  ```java
  for (int i = 0; i < commands.size(); i++) {
      ...
      CompletableFuture<Integer> stageFuture = CompletableFuture.supplyAsync(() -> {
          try { return executeSimpleCommandWithContext(cmd, input, out, context.stderr(), context, cancelToken); }
          catch (Exception e) { throw NopException.adapt(e); }
          finally { IoHelper.safeClose(out); }
      }, executor);
      stageFutures.add(stageFuture);   // ← 第 k 次提交抛 RejectedExecutionException 时循环中止
      prevOutput = output;             //   已提交的第 k-1 阶段的消费者永远不会被提交
  }
  // BlockingQueueShellOutput.java:15 — 队列容量 1024；write 用 queue.put（阻塞）
  ```
- **严重程度**: P1
- **并发场景**: pipeline 的 N 个阶段任务与前台单命令/其他会话共享同一 executor（缺省 `nop-global-worker`，core=4/max=30/queue=10000；嵌入方可注入小池）。阶段 i 阻塞在向阶段 i+1 的 1024 容量队列 `put`，阶段 i+1 阻塞在 `take`——互为生产者消费者，只有当双方都拿到线程才能推进。
- **触发时序**: T1：executor 饱和（池满且队列满，StandardThreadPoolExecutor 拒绝即抛 REE），executePipeline 提交到第 k 个阶段时 `supplyAsync` 同步抛 RejectedExecutionException，循环中止，异常向调用方传播（fail-loud 本身正确）→ T2：已提交的阶段 k-1 运行并向 `out` 队列写数据，写满 1024 chunk 后永久阻塞在 `queue.put`——它的消费者（阶段 k）永远不会被提交，无人 close 该队列，无人 drain → 交叠后果：阶段 k-1 独占一条 executor 线程直到进程重启；上游 k-2、k-3 依次因下游满队列连锁阻塞。每次部分提交失败按链长级联吞噬线程，30 线程池在反复触发后完全瘫痪（后续所有提交 REE 或任务饿死）。
- **现状**: 代码只处理了"全部提交成功"的路径；对"提交到一半失败"没有任何回滚——既不 close 已提交阶段的输出队列（解除 put 阻塞），也不取消已提交阶段。
- **风险**: 错误路径的资源泄漏且后果不可自愈（阻塞的 put 不受 cancelToken 影响）；注入小池的嵌入（ToolExecuteContext 模式下池属性由嵌入方决定，小池是常态）在中等负载即可触发；触发后表现为"shell 会话逐渐全部卡死"，极难现场定位。
- **建议**: 提交循环 try/catch REE：失败时对 `stageFutures` 已提交项逐个 `cancel(true)` 并对 `prevOutput` 链逐个 `close()`（close 会 offer EOF，解除下游 put 阻塞）；或先预检 `executor` 余量。复用平台 `QueueOverflowPolicy` 语义显式声明溢出行为。
- **失效面枚举**（F3 同族清扫）: ① executePipeline 多阶段提交循环——唯一的多任务提交点，本条；② executeSequence/executeLogicalExpr（L288-387）——thenCompose 串行链，不向 executor 提交，无此面；③ executeBackground（L363-365）——单任务经 sink 提交，见 C4-006/C4-007；④ 17 个 tool executor——各自单任务提交，`submit` 抛 REE 时走 C4-005 面而非本面；⑤ nop-ai-toolkit GraphqlQueryExecutor/SearchEngineExecutor——fetchAsync/searchAsync，不经 executor 提交循环。全族已覆盖。
- **信心水平**: 确定（REE 中止路径与 `queue.put` 阻塞语义均逐行核实）
- **误报排除**: 非理论竞态——给出了单次部分提交失败即可复现的确定性时序；非"有界队列即豁免"——队列有界正是 put 阻塞的前提。
- **复核状态**: 保留(P1)｜新发现——同根族案：**C6-004**（中途段失败/早退弃管面，主登记）+ **C8-003**（下游死亡→上游 put 阻塞面）+ **C8-001**（先生产后收集拓扑面），单 diff 修复；本条独有面：**REE 提交中止**（下游阶段从未被提交，区别于 C6-004 的"下游已运行后死亡"）。复核补充触发条件：永久挂死还需 stage k-1 输出 >1024 chunk（否则该段自完成不泄漏线程、仅结果静默丢弃）；注入小池（ToolExecuteContext 装配常态）+ 真实外部适配器下中等负载可达，缺省 globalWorker（queue 10000）下 REE 罕见——族案 P1 判级与 C6-004/C8-003 一致成立

### [C4-004] pipeline 阶段与全平台任务共池（缺省 globalWorker）——生产/消费阶段占用线程造成负载饥饿，无池隔离

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:58-60`（缺省回退）；`243-286`（阶段提交）；平台事实：`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:85-96`、`ThreadPoolConfig.java:16-31`、`ExecutorHelper.java:73-90`
- **证据片段**:
  ```java
  // ShellCommandExecutor.java:60 — 未注入即回退全平台共享池
  this.executor = executor != null ? executor : GlobalExecutors.globalWorker();
  // GlobalExecutors.java:91 — max = CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE（缺省 30）
  config.setMaxPoolSize(CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE.get());
  // ThreadPoolConfig.java:25 — queueCapacity 缺省 10000
  ```
- **严重程度**: P2
- **并发场景**: 缺省装配下，shell pipeline 阶段任务、前台 bash 工具执行（BashExecutor.doExecute 在 executor 线程上阻塞 `process.waitFor` 最长达 wallSeconds，前台缺省 30s、后台上限 1h）以及其他模块提交到 `nop-global-worker` 的任务全部共池（core=4/max=30/queue=10000，daemon，abort 拒绝）。
- **触发时序**: T1：30 个会话并发执行 bash 工具（各占一条 executor 线程，阻塞在 waitFor 至 30–60s）→ T2：同一窗口内任一会话执行 `a | b | c`，阶段任务入 10000 深队列，等待其他会话的 bash 释放线程；每条管道的阶段间因 put/take 互等而只能"整链并发推进"，实际并发管道数被剩余线程数硬性钳制 → 交叠后果：管道延迟被无关模块负载放大（排队+互等双重等待），10000 深队列使拒绝长时间不触发、饥饿不可感知（F5：无任何 stall 可见性）。**如实记录**：由于 `supplyAsync` 在紧循环内按阶段相邻次序入队、线程按 FIFO 取任务，"全部线程被阻塞生产者占据而其消费者全部排队"的永久互等死锁需要队首 30 项全为生产者——相邻提交使其不可达；故本条只主张可达的饥饿/延迟放大，不主张永久死锁。
- **现状**: 缺省池选择使工具执行（慢、阻塞、分钟级）与全平台任务零隔离；且 10000 队列深度让 abort 背压形同虚设。
- **风险**: 负载上升时 shell 会话 P99 无界恶化；bash 前台调用按 30 并发上限天然限流全 JVM 工具面，与嵌入方的容量规划脱节。
- **建议**: 嵌入装配点为 shell 注入独立有界池（`DefaultThreadPoolExecutor.newExecutor("nop-ai-shell-pipeline", n, m)`），与前台 bash 工具池、平台 worker 三者隔离；池参数对照"最大并发会话 × 最大管道段数"设定并在 owner doc 声明。
- **失效面枚举**（F3 同族清扫）: ① ShellCommandExecutor 缺省回退——本条；② tool 执行池为注入式（ToolExecuteContext.java:87 无缺省），嵌入方若同样注入 globalWorker 则 17 个 tool executor 与 shell 共池——同一修法（装配点隔离），登记为同族待装配审查项；③ `&` job 的 sink 池身份见 C4-006。全族已覆盖。
- **信心水平**: 很可能（饥饿时序具体可达；永久死锁不可达的论证基于 FIFO 相邻提交，若未来提交点插入异步间隙则升级）
- **误报排除**: 不按"共享池是常见做法"豁免——维度正文第 4 步明确要求审查任务隔离；此处已区分"可达的饥饿"（报告）与"不可达的死锁"（明示不主张）。
- **复核状态**: 保留(P2)｜新发现——平台参数全部复核成立（ThreadPoolConfig core=4/queue=10000、CFG_CONCURRENT_GLOBAL_WORKER_MAX_POOL_SIZE 缺省 30、StandardThreadPoolExecutor abort+TaskQueue.force 限时重试）；对"相邻提交使互等不可达"的 FIFO 论证复核成立；跨单元同族（WI 1.2 C4-004/MA-06、WI 3.1 C4-011 均 P1）判级差异成立：彼两条为无限/300s 阻塞任务**实际**占池，本条缺省装配阶段任务为内存短命令、主张仅饥饿延迟放大——P2 维持；外部适配器接通后的重阻塞面随 C6-004 族案核

### [C4-005] pathQueues 排队链中 executeNow 同步抛出（池拒绝/executor 未注入 NPE）→ 排队调用的 result 永不完成，调用方静默挂死

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:83-121`；`io/nop/ai/toolkit/executor/ToolExecuteContext.java:87,120,130-133`（executor 无缺省值）
- **证据片段**:
  ```java
  // ToolManagerImpl.java:104-119 — 排队链
  CompletableFuture<AiToolCallResult> result = new CompletableFuture<>();
  pathQueues.compute(pathKey, (k, tail) -> {
      CompletableFuture<Void> trigger = (tail == null
              ? CompletableFuture.completedFuture(null) : tail).handle((v, e) -> null);
      return trigger.thenCompose(x -> executeNow.get()      // ← 同步抛出点
              .whenComplete((r, err) -> {                    // ← 抛出时此段不会构建
                  if (err != null) result.completeExceptionally(err); else result.complete(r);
              }).thenApply(r -> (Void) null));
  });
  return result;                          // ← 永不 complete → 调用方永久等待
  // ToolExecuteContext.Builder：executor 字段无缺省、可 null；getExecutor().submit(...) 即 NPE
  ```
- **严重程度**: P1
- **并发场景**: 同路径工具调用串行化队列：调用 A 在途时，调用 B（同 pathKey）的执行体以 `thenCompose` 挂在 A 的尾链上，B 的 `result` future 交给调用方（engine dispatcher）。`executeNow.get()` 内联执行 `executor.executeAsync` → 17 个 executor 均为 `context.getExecutor().submit(...)`，在池拒绝（REE）或 executor 未注入（NPE）时**同步**抛出。
- **触发时序**: T1：会话对同一路径发出调用 A（如 30s 的 bash 写文件），B 排队（tail 未完成，B 的 thenCompose 注册而非立即执行）→ T2：A 完成，其尾链在完成线程上执行 B 的函数体 `executeNow.get()`；此刻池恰好饱和（小注入池常态），`submit` 抛 REE → 交叠后果：异常被 CompletableFuture 机制捕获并使内部尾链异常完成，但 `whenComplete` 段从未构建，`result` 永不 complete 也永不 completeExceptionally——B 的调用方无限等待（本模块无任何超时兜底；context.expireAt 只是工具体内部的建议值）。对照：**首个**调用（trigger 已完成）同步抛出会穿出 compute 向调用方 loud-fail——同一缺陷只惩罚"排队者"，且是静默惩罚。
- **现状**: 被拒绝任务的去向未定义（既非 fail-fast 也非丢弃通知），违反 C4 步骤 2 "拒绝策略——被拒绝的任务去哪了"的可达性要求；排队链把"提交失败"变成了"提交消失"。
- **风险**: 中等负载 + 注入小池（ToolExecuteContext 装配模式下的常规形态）下，同路径并发调用随机静默挂死；engine 侧若依赖工具结果推进状态机，则整个 ReAct 轮次卡死且无错误可观测。
- **建议**: 把 `executeNow.get()` 包入 try/catch，同步抛出时 `result.completeExceptionally(e)` 后重抛（或返回 error result）；顺带给 `result` 挂 expireAt 兜底超时。
- **失效面枚举**（F3 同族清扫）: ① ToolManagerImpl.callTool 排队分支——本条；② callTool 非排队分支（L122）与 callTools/executeSequential（L248-266）——同步抛出直接向调用方传播（loud），无此缺陷；③ executeParallel/submitBounded（L198-246）——信号量排队在提交之前，acquire 中断有兜底（L239-243），提交异常经 allOf 传播，无此缺陷；④ 17 个 executor 自身——同步抛出（拒绝/NPE）正是本条的输入面，本身不构成独立缺陷（fail-loud 语义由调用方保证）；⑤ BashExecutor 后台路径的提交不对称独立为 C4-007。全族已覆盖。
- **信心水平**: 确定（CF 链异常传播语义逐行核实）
- **误报排除**: 非泛泛的"异常处理建议"——给出了排队/直发两条路径行为不对称的具体差异与可复现时序；属 F2/F9（半个成功 + 静默吞掉提交失败导致状态不一致）在本模块的实证形态。
- **复核状态**: 保留(P1)｜新发现——与 **C1-001** 合并单（同站点 ToolManagerImpl.java:104-119，同 diff，取并集不双计；C1 复核维持 P1，C4 侧同意）。**复核实证修正（JDK 25 实测）**：原条目"首个调用（trigger 已完成）同步抛出会穿出 compute 向调用方 loud-fail"**错误**——`thenCompose` lambda 内同步抛出被 CF 捕获完成依赖链异常，**不穿出调用点**；即排队分支的**首个调用同样静默悬挂**（result 永不完成），缺陷面比原条目更宽而非更窄；loud 路径仅存于非排队分支（L122，并发安全工具/无 pathKey）。修复注意：在 lambda 内 catch 后**重抛不能穿出**（仍被 CF 吞），必须 `result.completeExceptionally(e)` 且链尾以异常完成收束；与 C1-001 的"executeNow 移出 compute 锁"同一 diff。可达性由 C1 复核补强（CLI Sync 执行器/ChatBi null executor/Builder 无默认值）

### [C4-006] `&` 后台 job body 以 join() 阻塞在 sink 线程等待 executor 上的阶段链——sink 池身份无守卫，同池化即自死锁

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:350-370`；SPI 契约 `io/nop/ai/shell/executor/IBackgroundJobSink.java:23-38`
- **证据片段**:
  ```java
  String taskId = sink.submit(String.valueOf(background.inner()),
          () -> executeExpression(background.inner(), context, cancelToken)
                  .toCompletableFuture().join());   // ← body 在 sink 的线程上阻塞等待
  ```
- **严重程度**: P2
- **并发场景**: `cmd &` 的 body（可能含 pipeline）在 sink 实现的线程上以 `.join()` 阻塞等待；body 内 pipeline 阶段又提交到 `this.executor`（缺省 globalWorker）。sink 的线程池身份由嵌入方（SPI 实现方）决定，本模块无任何约束或守卫要求二者不同池。仓库内唯一参考实现 `BackgroundRegistryJobSink`（nop-ai-agent 测试源码，out of scope）把 body 交给 `IBackgroundTaskRegistry.run`（javadoc：registry 的 background executor），池身份同样未约束。
- **触发时序**: T1：嵌入方把 sink 的执行池配成与 shell executor 同一池（同一 globalWorker 是最省事的接法）；多个 `cmd &` 任务 + 前台负载占满池线程 → T2：某 job 的 body 线程阻塞在 join，其 pipeline 阶段任务在队列中等待线程；body 线程本身是池线程、不释放 → 交叠后果：join-等待-池内任务的自死锁形态（维度正文步骤 4 "池内任务再提交同池任务的死锁"），且无超时、无守卫、无文档警告。
- **现状**: C4-004 已论证纯 pipeline 相邻提交使互等不可达，但 body-join 引入了**跨池阻塞等待**这一新等待形态：body 占线等阶段，阶段等空闲线程——同池化时这是可达的永久等待，代码与 javadoc 均未声明"sink 池必须与 executor 不同"。
- **风险**: 嵌入方按最直觉方式装配（同一池）即踩雷；死锁不可自愈且因发生在 `&` 语义下、结果走通知通道，故障面远离故障点，排查成本高。
- **建议**: 三选一：(1) javadoc 契约 + 运行时守卫（sink.submit 时校验 body 执行器非本 executor，fail-fast）；(2) body 不 join——把 CompletionStage 直接交给 sink（SPI 增加 `submit(String, CompletionStage<ExecutionResult>)` 重载）；(3) body 内 pipeline 强制走调用方线程（SyncExecutor）。
- **失效面枚举**（F3 同族清扫）: ① executeBackground——唯一的 sink.submit 调用点，本条；② BashExecutor.doExecuteBackground——后台语义但经 IBackgroundTaskRegistry.run fork，不 join（registry 负责），无此形态但存在 C4-007 的相邻缺陷；③ agent 侧 BackgroundRegistryJobSink——out of scope，已在失效面中登记供 C4-nop-ai-agent 批次核对（其 run 的执行池身份是同一风险的另一半）。全族已覆盖。
- **信心水平**: 很可能（缺陷形态确定；可达性取决于嵌入方装配，本模块内无法证明其必然发生）
- **误报排除**: 非平台设施未用类误报——问题是 shell 侧把"fork 到别处"与"阻塞等待本池"耦合在同一 body 里且无契约；也非"SPI 未实现是边界"——接口 javadoc 明示 fork 语义却未声明池隔离要求，守卫缺失即缺陷。
- **复核状态**: 保留(P2)｜新发现——零生产接线条件面复核成立：sink 缺省 null 为 fail-loud（ShellCommandExecutor.java:356-361 声明设计）、仓库内唯一参考实现 BackgroundRegistryJobSink（nop-ai-agent 测试源码）按 IBackgroundTaskRegistry 契约走 registry 专用后台执行器（"off the foreground dispatch pool"）——按零生产接线先例条件化降档成立，P2 维持；跨单元家族关联：**WI 1.5 MA-03**（sync+executor 同池嵌套死锁族，P1）同形态不同站点（彼为实际缺省同池装配、本条为 SPI 装配条件面），修复批次对齐登记；javadoc 池隔离契约 + 运行时守卫为最小修法

### [C4-007] BashExecutor 后台路径 submit→run 无回滚——run 抛出时 taskId 永久滞留 RUNNING（提交/完成不对称）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:209-244,180-197`；接口缺省契约 `io/nop/ai/toolkit/api/IBackgroundTaskRegistry.java:42-47`
- **证据片段**:
  ```java
  String taskId = registry.submit(TOOL_NAME, sessionId, callId, timeoutMs);  // L209：先登记
  ...
  registry.run(taskId, () -> { ... });   // L216：后 fork —— 接口缺省实现直接抛 UnsupportedOperationException
  ```
- **严重程度**: P2
- **并发场景**: 前台工具线程执行 doExecuteBackground；registry 的任务账本（agent 侧实现，跨线程共享）。`IBackgroundTaskRegistry.run` 是 default 方法且**契约性地可抛 UOE**（fail-loud 是 javado c 明文设计："callers surface 'background mode is not wired in this runtime'"）。
- **触发时序**: T1：`registry.submit` 成功（账本新增 RUNNING 任务、占用 callId 关联）→ T2：`registry.run` 抛 UnsupportedOperationException（未接后台执行器的 registry 实现——接口缺省行为）→ 交叠后果：异常被 doExecute 的 catch(Exception)（L163）转成错误结果返回（fail-loud 达成），但 L209 已提交的 taskId 无人 complete/stop——账本永久滞留一条 RUNNING 任务，通知永不到来，快照/readOutput 永远返回运行中；随同类调用反复触发，账本无界增长。
- **现状**: submit 与 run 之间无 try/catch 回滚（失败时应 `complete(taskId, STATUS_FAILED, ...)` 或提供取消路径）；"半个成功"（F2）形态：登记成功、执行失败、状态不可达。
- **风险**: 部分装配（registry 有状态管理但未接后台执行器）的部署里，每次 runInBackground 调用净增一条僵尸 RUNNING 记录；模型侧看到"已启动"的假象与永不到来的通知。
- **建议**: L216 包 try/catch(Throwable)，失败路径 `registry.complete(taskId, STATUS_FAILED, "background executor not wired: " + e.getMessage())` 后再返回错误结果；或在接口层把"校验 run 可用"前置为 `registry.supportsRun()` 类守卫，submit 之前 fail-fast。
- **失效面枚举**（F3 同族清扫）: ① BashExecutor.doExecuteBackground——本条；② ToolManagerImpl 的 runInBackground 预检（L75-79）——只校验工具声明，不校验 registry 能力，不能拦截本缺陷；③ IBackgroundJobSink 路径（shell `&`）——BackgroundRegistryJobSink 在其 submit 内部连续做 submit+run（agent 测试源码，out of scope），run 抛出时同样留下孤儿 taskId，同一缺陷族，已登记供 agent 批次核对；④ 前台 bash 路径（doExecute L107-166）——无 registry 交互，不受影响。全族已覆盖。
- **信心水平**: 确定（接口缺省契约 + 调用点代码逐行核实）
- **误报排除**: 非"UOE 是 fail-loud 设计所以不是缺陷"——设计意图是调用方得到错误结果，这一点达成；缺陷在于设计者漏掉了 submit 已发生的回滚义务（F8 资源清理不对称），错误结果与账本残留并存正是问题本身。
- **复核状态**: 保留(P2)｜新发现——接口缺省契约（IBackgroundTaskRegistry.java:42-47 default run 抛 UOE）与调用点（BashExecutor.java:209 submit→L216 run→L163 catch(Exception) 转错误结果）逐行核实成立；F2/F8 形态认定成立；触发条件为"部分装配"（registry 实现了 submit 未实现 run），P2 维持；同族面：BackgroundRegistryJobSink 的 submit+run 连续调用（agent 测试源码，移交 agent 批次核对）

---

## 维度集外线索（overlay C2/C3/C5/C7/C9）

### [nop-ai-toolkit-tools-shell-CL-005] pathQueues 无界账本：条目永不清理且链式持有完整工具结果（C7 内存 / F4）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:32,101-121`
- **线索**: `pathQueues`（ConcurrentHashMap<String, CompletableFuture<Void>>）以文件路径为键，只有 `compute` 写入，全文件无任何 remove/清理/淘汰路径（已 grep 确认）。每个条目的尾链 `trigger.thenCompose(...).whenComplete(...).thenApply(...)` 使完成的 future 链强引用 `result` future，而 `result` 持有完整 `AiToolCallResult`（含输出 body 全文，文件类工具可达 KB–MB 级）——账本随进程触达的不同路径数线性增长且永释放。长运行 agent 服务按路径幂集增长，属 F4 无界账本族。C4 维度内仅登记线索，内存定界与规模包络对照请 C7 复核。
- **建议方向**: 尾链完成且无人排队时 `pathQueues.remove(pathKey, tail)`（compute 内 CAS 式清理），或换平台 `ICache` + 淘汰策略。

### [nop-ai-toolkit-tools-shell-CL-006] ShellCommandExecutor 会话状态（exportedEnv/currentWorkingDir）跨线程无同步读写（C2）

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:45-47,318-348,545-557`
- **线索**: `exportedEnv`（普通 HashMap）与 `currentWorkingDir` 被三类线程并发触达：前台调用线程（executeSimpleCommand→buildEnvironment L551 写 exportedEnv、updateContextFromResult L579-582 写 currentWorkingDir）、pipeline 阶段任务线程（buildEnvironment 读 exportedEnv+写 envVar）、`&` job 的 sink 线程（body 完整重入 executeExpression，同样读写二者）；executeGroup/executeSubshell 的 whenComplete 恢复（L325-328,344-347）也在任意完成线程上整体替换字段。T1 前台 `export X=1` 触发 HashMap put 扩容，T2 同实例的后台 job 在另一线程 `putAll(exportedEnv)` 迭代——CME/读到撕裂状态；非 volatile 字段的快照恢复还存在可见性竞态。单会话串行前提下安全，但 `&` 与 pipeline 正是打破串行的特性（会话模型自洽性问题），需 C2 判定装配契约（一实例一独占会话）是否成文。

### [nop-ai-toolkit-tools-shell-CL-007] GraphqlQueryExecutor 在调用线程同步做 DNS 解析（C3 阻塞）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/GraphqlQueryExecutor.java:78-97,129-134`
- **线索**: `executeAsync` 在提交任何异步操作**之前**于调用方线程（engine dispatcher / ToolManagerImpl 链）执行 `validateResolvedHost` → `dnsResolver.resolve(host)`——阻塞网络解析，系统解析器超时可达秒级且不受 call.timeoutMs 约束。对照同包 HttpRequestExecutor.java:97 把 `doExecute`（含全部校验）整体 `context.getExecutor().submit(...)`，GraphqlQueryExecutor 是同族中唯一在校验面使用调用线程的。SSRF 防护语义正确，线程归属错位；C3 判定调度线程阻塞容忍度。

### [nop-ai-toolkit-tools-shell-CL-008] pipeline 非末位阶段异常被吞 + 阻塞输入中断被解释为 EOF（C9 静默吞异常）

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:277-286`；`io/nop/ai/shell/io/BlockingQueueShellInput.java:23-37`
- **线索**: ① `pipelineFuture = stageFutures.get(N-1).thenApply(...)` 只观测末位阶段——第 k<N 阶段的 stageFuture 异常完成时无人 `.whenComplete`/join，异常静默丢失（F9），管道以末位阶段的部分输出"正常"结束，下游无从得知中间段失败；② `BlockingQueueShellInput.read()` 捕获 InterruptedException 后返回 null——中断被解释为 EOF，下游命令把"被打断"当"输入为空"继续执行并可能返回退出码 0，取消语义被静默降级为成功语义（对照 BlockingQueueShellOutput.write L30-34 的同中断是重抛）。C9 判定吞异常后果与取消传播一致性。

---

## 维度复核结论

- 复核：WI 3.2 C4 维度独立复核（2026-10-20）。方法：8 条发现逐条回码验证（全部 file:line 实读核对）、JDK 25 实证测试（CompletableFuture 传播语义）、平台设施核验（GlobalExecutors/ThreadPoolConfig/StandardThreadPoolExecutor/ExecutorHelper/ICancelToken）、cra-index WI 1.1–3.1 P1 台账逐条查重、同批 C1 复核结论与 C6/C8 第 1 轮原文对照。
- **判定统计：8 条保留 / 0 降级 / 0 驳回**；最终严重度 **P1×3（C4-001 并案校准、C4-003、C4-005）、P2×5（C4-002/004/006/007/008）**；现状标注：**新发现 ×8**（本单元首审，无已知未修复/已修复项；唯一"已修实例"对照：HostBashSandbox.killTree 为先前修复产物，其 Docker 同族未同修——C4-002 现状中单列）。

### 逐条判定表（C4-001…008 全覆盖）

| 发现 | 原判 | 判定 | 最终严重度 | 现状 | 同案/家族 |
|---|---|---|---|---|---|
| C4-001 reader 泵线程泄漏 | P2 | 保留（并案校准） | **P1** | 新发现 | 同站点同案 C6-001（主登记建议 C6-001） |
| C4-002 runShortCommand 无超时覆盖 | P2 | 保留 | P2 | 新发现（Docker 变体 F3 残留，killTree 已修） | 同案 C6-003（挂死面）+C8-009（进程泄漏面）；独有：心跳线程占用面 |
| C4-003 pipeline 提交中止弃管 | P1 | 保留 | P1 | 新发现 | 同根族 C6-004（主登记）+C8-003+C8-001；独有：REE 提交中止面 |
| C4-004 共池无隔离 | P2 | 保留 | P2 | 新发现 | 跨单元家族 WI 1.2 MA-06/WI 3.1 C4-011（判级差异成立，非同案） |
| C4-005 pathQueues result 永不完成 | P1 | 保留（实证修正后维持） | P1 | 新发现 | 合并单 C1-001（同站点同 diff，C1 复核维持 P1） |
| C4-006 sink 池身份无守卫 | P2 | 保留 | P2 | 新发现 | 跨单元家族 WI 1.5 MA-03 同池嵌套死锁族（条件面降档成立） |
| C4-007 submit→run 无回滚 | P2 | 保留 | P2 | 新发现 | 同族面移交 agent 批次（BackgroundRegistryJobSink） |
| C4-008 取消链覆盖面（round2） | P2 | 保留（实证补充后维持） | P2 | 新发现 | 子面 CL-012；跨单元伞族 WI 3.1 MA-08；协同点 WI 2.1 api-core bindCancel |

### 复核实证修正（两处，均使缺陷面变宽，不改判级）

1. **C4-005"首个调用 loud-fail"对照错误（实测证伪）**：JDK 25 实测 `completedFuture().thenCompose(x -> {throw …})` 异常被 CF 捕获完成依赖链，**不穿出调用点**——排队分支首个调用与排队调用同样静默悬挂（`result` 永不完成），loud 路径仅存于非排队分支（ToolManagerImpl.java:122）。原条目把缺陷面写窄了，P1 维持且加强。
2. **C4-008"直发路径 bash 有效"不准确（实测补充）**：`ExecutorHelper.submit → FutureHelper.bindCancel` 只绑定**池 future↔FutureTask**；ToolManagerImpl 直发路径返回的是 `executeAsync(…).thenApply(…)` 包装 future，实测 `wrapper.cancel(true)` 不传播上游——即**经 manager 返回 future 的取消在任何路径都不达池任务**（jq 直发有效仅因 cancelToken 通道，bash 实际 kill 通道只剩 agent 心跳）。C4-008 为欠报而非误报，P2 维持（bash 心跳通道存在 + 与 MA-08 同族避免双计 P1）。

### 同案命中清单（合并裁定输入）

| C4 条目 | 同案/家族 | 处置建议 |
|---|---|---|
| C4-001 | **C6-001 同站点同缺陷**（sandbox reader；C6 面更全判 P1） | 以 C6-001 为主登记合并，C4-001 关闭为成员；线程唯一命名随同 diff |
| C4-002 | **C6-003 同站点**（runShortCommand 挂死面）+ **C8-009**（CLI 进程泄漏面） | C6-003 主登记；C4-002 心跳线程占用面并入；kill/rm -f 两分支同 diff |
| C4-003 | **C6-004 + C8-003 + C8-001 同根族**（管道弃管族，均 P1） | C6-004 主登记族案；C4-003 REE 提交中止面并入（提交循环 try/catch REE→cancel+close 已提交项） |
| C4-005 | **C1-001 合并单**（同站点 ：104-119） | 已按 C1 复核裁定取并集维持 P1；本节实证修正两节共享 |
| C4-006 | WI 1.5 MA-03 同池嵌套死锁族（家族关联，非同案） | 跨单元修复批次对齐登记，不并案 |
| C4-008 | 子面 **CL-012**（jq 注册窄窗）；伞族 **WI 3.1 MA-08**（C3-011，P1）；协同点 **WI 2.1 api-core 移交包 bindCancel 单点改造** | CL-012 并入 C4-008 修复 diff；MA-08 双轨登记零重复（工具侧结构面 vs 引擎侧编排面） |
| overlay CL-005/006（本文件维度集外线索，非本次复核对象） | CL-005 ↔ **C6-006/C8-006 三登记**；CL-006 ↔ **C6-CL-009/C8-CL-016 三登记** | 供合并裁定去重：各留一处主登记 |

### 高风险条目子项复核说明

- **C4-003（P1）**：REE 传播链逐层核实（ExecutorHelper.submit:130 `executor.submit` 同步抛 → StandardThreadPoolExecutor.execute REE → TaskQueue.force(timeout=0) 失败再抛 REE）——`CompletableFuture.supplyAsync` 同步中止提交循环确认；补充触发条件（stage k-1 输出 >1024 chunk，否则仅结果丢弃无线程泄漏）；子项结论：注入小池 + 真实外部适配器装配下 P1 成立，缺省 globalWorker（queue 10000）下 REE 面收敛为族案成员面。
- **C4-005（P1）**：除实证修正 1 外，可达性采纳 C1 复核生产装配证据（CLI Sync 执行器全链锁内执行/ChatBi null executor/Builder 无默认值）；修复方向修正：lambda 内重抛无效，必须 completeExceptionally（见状态行）。
- **C4-001（并案 P1）**：升级理由——成功路径条件化泄漏 + `server &` 型命令为 AI bash 工具高频真实负载 + 每次永久泄漏一线程一 fd（fd 面采信 C6-001）+ 同名线程不可观测；对照判例：WI 2.2 C6-001（错误路径泄漏 P1）、WI 3.1 C4-010（每调用泄漏 P1），本条触发条件居中，P1 落点成立。daemon 标记仅保证不阻 JVM 退出，不构成降档理由。

### 跨单元对照结论（cra-index 查重）

- 与 WI 1.1–3.1 P1 台账**零同站点重复**：C4-001…008 站点均未在台账出现。家族级关联 4 处单列：WI 3.1 MA-08（取消传播伞族，C4-008 为工具侧结构面）、WI 1.5 MA-03（同池嵌套死锁族，C4-006 同形态条件面）、WI 1.2 MA-06 + WI 3.1 C4-011（共享池阻塞族，C4-004 判级差异已论证）、WI 2.1 CL-021（裸线程绕过平台线程设施族——C4-001 的裸 `new Thread` + 同名线程为新增点位，移交 CL-021 台账补登）。
- round2 五深挖面收敛证据复核成立：mavenvfs stub 零线程、nop-ai-tools 零线程、三根 `scheduleAtFixedRate` 族零命中、线程站点全清单穷举（4 站点全部已立案）经独立 grep 复现确认。**确认第 2 轮终止信号，C4 维度收敛。**

## 最终保留项

| # | 条目 | 最终严重度 | 处置 |
|---|---|---|---|
| 1 | C4-005 pathQueues 排队链提交失败→result 永不完成（调用方静默挂死） | **P1** | 本单元工单，与 C1-001 合并单同 diff |
| 2 | C4-003 pipeline 阶段提交中止弃管（并入 C6-004 族案） | **P1** | 族案工单（C6-004 主登记），单 diff 盖 REE/中途失败/早退三面 |
| 3 | C4-001 sandbox reader 线程泄漏（并入 C6-001） | **P1** | C6-001 主登记工单成员；加线程唯一命名（NamedThreadFactory 语义） |
| 4 | C4-002 DockerBashSandbox runShortCommand 无超时覆盖（并入 C6-003） | P2 | C6-003 主登记工单成员；kill/rm -f 两分支 + 心跳线程占用面同 diff |
| 5 | C4-004 pipeline 与全平台任务共池无隔离 | P2 | 本单元工单（装配点隔离），与 MA-06/C4-011 修复批次对齐评估 |
| 6 | C4-006 `&` body join 于 sink 线程、池身份无守卫 | P2 | 本单元工单（javadoc 契约 + 运行时守卫 + SPI CompletionStage 重载三选一）；与 WI 1.5 MA-03 批次对齐 |
| 7 | C4-007 BashExecutor submit→run 无回滚（僵尸 RUNNING） | P2 | 本单元工单（try/catch + complete(FAILED) 回滚 / supportsRun 前置守卫） |
| 8 | C4-008 取消链覆盖面：排队路径断链 + 16/17 executor 不读 cancelToken | P2 | 本单元工单，与 CL-012 同 diff；WI 3.1 MA-08 / WI 2.1 api-core bindCancel 协同点登记 |
