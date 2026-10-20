# C8 背压、流控与过载行为 — nop-ai-toolkit-tools-shell（WI 3.2）

## 审计范围与口径

- **单批次全源码根覆盖声明**：本报告为 CRA WI 3.2 的 C8 维度（背压、流控与过载行为）盲审初审，单批次逐文件审读以下三个手写源码根（不含 `_` 前缀生成文件）：
  - `nop-ai/nop-ai-toolkit/src/main/java`（工具输出捕获、沙箱进程输出泵、mavenvfs、tool manager 并发链）
  - `nop-ai/nop-ai-tools/src/main/java`（内建工具输出、sequential-thinking 会话存储）
  - `nop-ai/nop-ai-shell/src/main/java`（shell 管道队列、输出缓冲、后台作业）
- **Phase 2 收官说明**：本批次为 Phase 2 收官维。同一审计目录内已有 C1/C4/C6 报告（本初审员按盲审纪律未读其内容，仅知其存在）；C2/C3/C5/C7/C9 作为 overlay 维度由协调方汇总。本报告的跨维度线索按编号续接 CL-014 起（CL-001…013 已被前序批次占用）。
- **口径纪律声明**：
  - 全部发现以当前 live code 为准，未读 `ai-dev/audits|plans|bugs|lessons|backlog/**` 任何历史记录（盲审），未用 git log 查修复历史；现状标注（新发现/已知未修复）留待复核阶段。
  - 每条并发/资源发现均给出具体触发时序（T1/T2 交错）与失效面枚举（F3 同族清扫）；写不出具体时序的一律降级 P2 并标注。
  - 数量结论均由本初审员人工复现代码路径得出，未直接采信基线文件计数；基线 `_tmp/cra-baseline-nop-ai-toolkit-tools-shell.txt` 的 C8a/C8b 节仅用于圈定重点文件。
  - 已声明的边界不算缺陷：`BashSandboxConfig`（maxOutputBytes 默认 1MiB、wallSeconds 默认 60s，构造期校验）、`ThoughtStorage` 设计文档声明的「会话级、每会话数十条、~1KB/条」包络、`LocalToolFileSystem.readText(maxChars)`/`SafeLineReader` 平台截断语义，均按「已声明边界」处理；缺陷只在「声明与实现矛盾」「守卫缺失」「守卫可绕过」处立项。
  - 平台基础设施（nop-kernel/nop-commons 的 IBlockingQueue、QueueOverflowPolicy、SafeLineReader、FileHelper）自身不重审，只审使用方式。

## 生产-消费边界清单（C8 执行步骤 1 产出）

| # | 边界 | 有界性 | 满载行为 | 判定 |
|---|------|--------|---------|------|
| B1 | shell 单命令 stdout/stderr → `BlockingQueueShellOutput`(1024 chunk) → 同线程 `collectOutput` | 有界（1024 chunk ≈ 1024 行） | 满时 `put()` 阻塞；EOF 用 `offer()` 满时**静默丢弃** | **C8-001/C8-002** |
| B2 | 管道 stage→stage `BlockingQueueShellOutput`(1024) → 下一 stage `take()`；末级 stage → future 完成后才 collect | 有界 | 级间有真背压；末级无消费者直到完成 | **C8-001** |
| B3 | 管道 stage 失败 → 上游 stage 写满后 `put()` 永久阻塞（input 无失败关闭路径） | 有界 | 永久阻塞泄漏 worker 线程 | **C8-003** |
| B4 | 消费线程中断 → `read()` 返回 null ≡ EOF | — | 静默截断 | **C8-004** |
| B5 | `executeParallel` maxConcurrency `Semaphore` → `callTool` | 有界（调用方传入） | permit 同步异常路径不释放；acquire 无超时 | **C8-005** |
| B6 | `ToolManagerImpl.pathQueues` per-path future 链 | **无界**（无容量、无溢出策略、entry 永不驱逐） | 无满载概念，链无限增长 | **C8-006** |
| B7 | bash 沙箱进程 stdout → `drainStream` StringBuilder | 有界（maxOutputBytes，默认 1MiB，超限截断续排空） | 截断但保持排空（不阻塞子进程） | 合格（对照声明） |
| B8 | http-request / graphql-query 响应体 → 工具输出 | **无界**（`getBodyAsString()` 全量） | 无降级 | **C8-007** |
| B9 | 文件类工具输出上界（read-file 默认 100K chars；grep/glob/read-file 行区间参数无服务端上限；patch-file 全量载入） | 部分有界，上限可被调用方参数绕过 | 无服务端兜底 | **C8-008** |
| B10 | `> file` 重定向 `FileShellOutput` StringBuilder | **无界**（flush 于 close 才落盘） | 无降级 | **C8-010** |

限流/信号量盘点：三源码根内无 `IRateLimiter`/`HighWatermarkSemaphore`/`QueueOverflowPolicy` 的任何使用（grep 证实）；唯一信号量是 `ToolManagerImpl` 的 JDK `Semaphore`（见 C8-005）。过载可观测性（积压深度/拒绝数指标）在三源码根内不存在——`pathQueues` 深度、`executeParallel` 等待数均无 metrics 暴露，随 C8-005/C8-006 一并立项。

## 发现

### [C8-001] shell 单命令与管道末级输出「先生产后收集」拓扑：≥1024 chunk 输出永久挂死调用线程

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:221-231, 257-285, 627-633`
- **证据片段**:
  ```java
  // executeSimpleCommand（同一调用线程：先跑命令写满队列，再收集）
  BlockingQueueShellOutput stdoutOutput = new BlockingQueueShellOutput();   // L221, 容量 1024
  ...
  int exitCode = executeSimpleCommandWithContext(cmd, context.stdin(), stdoutOutput, ...); // L225 命令在此线程写 put()
  stdoutOutput.close();
  stderrOutput.close();
  String stdout = collectOutput(stdoutOutput);                              // L230 收集发生在命令返回之后
  ```
  ```java
  // executePipeline（末级 stage 输出直到该 stage future 完成后才有人读）
  stageFutures.get(stageFutures.size() - 1).thenApply(lastExitCode -> {
      String stdout = collectOutput(lastOutput);                            // L281
      ...
  ```
- **严重程度**: P1
- **并发场景**: 调用线程（`executeSimpleCommand` 路径，生产与消费同线程）；`GlobalExecutors.globalWorker()` 池线程（管道 stage 线程，生产者）与 future 完成回调线程（消费者，`collectOutput`）。
- **触发时序**: 机制 A（>1024，`put` 阻塞挂死）：T1=调用线程执行 `executeSimpleCommandWithContext`，命令（自定义注册 `IShellCommand` 或经 nop-shell 集成的外部进程适配器，`writeLine` 每行 1 chunk）逐行写 `queue.put()`；写到第 1025 行时队列已满（容量 1024，`BlockingQueueShellOutput` L15/L30），`put()` 无限阻塞；`collectOutput` 位于 L230，只有命令返回后才执行——但命令永不返回 → 调用线程永久挂死，cancelToken 无任何检查点，无超时兜底。机制 B（管道末级）：T1=末级 stage 线程在 `supplyAsync`（L263-271）内写 stdout，>1024 行后 `put()` 阻塞；T2=`thenApply`（L280）等待该 stage future 完成才开始 `collectOutput`——消费者被拓扑禁止在完成前启动 → stage 线程与 pipeline future 双双永不落定。两机制均为**确定性触发**（不依赖竞态，只需输出 ≥1024 行）。
- **现状**: 有界队列（1024 chunk）与「先跑完再收集」的同步收集拓扑组合成自死锁：队列的背压信号（满）无人消费，收集器被顺序约束在生产者之后。
- **风险**: 长时间运行的 agent 会话中任何 ≥1024 行输出的命令（如 `cat` 大日志、`ls -R` 深目录的外部命令形态）使调用线程/worker 线程永久挂死且不可取消；`GlobalExecutors.globalWorker()` 为全局共享池，泄漏线程累积后拖垮同池其他任务。本模块内置命令（cd/echo/ls）单次输出 ≤1 chunk，故在本仓库内直接触发需自定义命令或 nop-shell 外部命令集成——该集成是本执行器的设计目标（`ExternalCommandAdapter` 注释明示），集成后升级为普通命令即可触发的 P0 频度。
- **建议**: 收集与生产并行化：为每级输出启动消费者（`output.asInput()` 立即在独立线程/ CompletableFuture 中 `readAllText`，与生产重叠），或将 `collectOutput` 改为消费-转发循环并在 `executeSimpleCommandWithContext` 期间持续排空；管道末级同理在 stage 启动同时启动收集。修复后 C8-002 的 EOF 丢失窗口随之收窄。
- **失效面枚举**（F3 同族清扫）: ① `executeSimpleCommand` L221-231 同线程收集——**缺陷本体**；② `executePipeline` L277-285 末级延迟收集——**缺陷本体（同一根因第二入口）**；③ `collectOutput` L627-633 异常吞掉返回 ""（F9，挂死时不抛异常故此处不是主失效面，随本条修复一并收敛）；④ `LogicalExpr`/`executeSequence`（L288-316, L377-387）——stdout 逐段拼接，底层仍走 ①，受同一缺陷影响；⑤ 内置命令 EchoCommand/LsCommand（单 println=1 chunk）——当前不触发，不豁免登记：命令集是开放扩展点。
- **信心水平**: 确定
- **误报排除**: 不属于「有界声明存在的队列」误报校准项——问题不在队列有界，而在队列满载信号永远无人响应（消费者被同步拓扑排除）；也不是「原语使用不是问题」——`put`/`take` 用法正确，复合拓扑（produce-then-collect 同线程）才是缺陷。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：shell 执行器投产/真实外部命令适配器接通即回 P1)｜新发现——回码核验成立（executeSimpleCommand L221-241 同线程 produce-then-collect、executePipeline L277-285 末级延迟收集、两机制对 ≥1025 chunk 输出均为确定性挂死、cancelToken 全程无检查点）；触发前提核实：内建命令（cd/echo/ls）单次输出 ≤1 chunk、ExternalCommandAdapter 为 UOE 桩（adapter/ExternalCommandAdapter.java:11）；shell 零生产接线 grep 证据（io.nop.ai.shell 全仓仅 nop-ai-agent 测试源 import、无任何 beans 注册、无 main 消费方）——按族案主登记 C6-004 已裁的条件化降档先例（WI 3.1 C1-005/C5-022 口径）同批同判级；族案成员关系随 C6-004（成员：C8-001/C8-003/C4-003 REE 面），判级分歧（C4 复核维持族案 P1）登记 7.1 终裁

### [C8-002] EOF 以非阻塞 `offer` 投递：队列恰满时 EOF 静默丢失，消费者排空后永久阻塞在 `take()`

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:42-47`；`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellInput.java:50-55`
- **证据片段**:
  ```java
  // BlockingQueueShellOutput.close()
  public void close() {
      if (!closed) {
          closed = true;
          queue.offer(ShellChunk.eof());     // L45: 队列满时 offer 返回 false，EOF 静默丢弃
      }
  }
  ```
  ```java
  // BlockingQueueShellInput.sendEof() — 同族第二处
  public void sendEof() {
      if (!eofReceived) {
          eofReceived = true;
          queue.offer(ShellChunk.eof());     // L53
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: 生产者线程（命令执行/stage 线程，最后一个 `put()` 刚把队列填到 1024）与关闭发起线程（同线程 finally、或管道协调线程调用 `IoHelper.safeClose(out)`）；消费者线程在 `asInput().read()` 的 `queue.take()` 上等待。
- **触发时序**: T1=生产者在 `write()` L30 `queue.put()` 写入第 1024 个 chunk（put 在 size=1023 时被放行，插入后 size==1024）；T2=任一线程调用 `close()`，L45 `offer(eof)` 因队列满返回 **false**，返回值被忽略，`closed=true`；T3=消费者（`executePipeline` L280 的 `collectOutput` 或 `readAllText`）排空 1024 个数据 chunk 后再次 `take()`——EOF 已丢失，**永久阻塞**。调用线程挂死；无超时、无 interrupt 恢复路径（除非外部 interrupt，而 interrupt 在 adapter 中被映射为「正常 EOF」，见 C8-004）。
- **现状**: 流终止信号（EOF）的投递选用了可失败的非阻塞 `offer` 且不检查返回值，与数据通道的阻塞 `put` 不对称；「队列满」恰好是 EOF 最需要可靠投递的过载场景。
- **风险**: 与 C8-001 机制 B 构成同一输出量级（≥1024 chunk）的两种挂死形态：>1024 挂生产者（C8-001），==1024 挂消费者（本条）。任何触发其中之一的命令使 `execute()` 永不返回；对 agent 引擎表现为工具调用无响应直至请求超时层（若存在）放弃，线程仍泄漏。
- **建议**: `close()`/`sendEof()` 改用阻塞投递（`queue.put(ShellChunk.eof())`）并在投递前处理「消费者已关闭」情形；或采用「先排空再关闭」协议；或用平台 `IBlockingQueue` + `QueueOverflowPolicy` 声明满载语义。`close()` 内先 `if (queue.remainingCapacity() == 0)` 无法修复（有竞态），必须阻塞投递或独占 drain 锁。
- **失效面枚举**（F3 同族清扫）: ① `BlockingQueueShellOutput.close()` L45——**缺陷本体**；② `BlockingQueueShellInput.sendEof()` L53——**同族缺陷，同修**；③ `BlockingQueueShellInput.close()` L58-61（只置标志不投递）——若生产者仍在 `put`，生产者在 `eofReceived` 检查（L40）通过后仍可 `put` 成功，数据写入无人消费的队列（不同机制，登记不立项，随 ② 修复时一并审）；④ `ListShellOutput.close()`（无界 List，`add` 不可失败）——不受影响；⑤ `TeeOutput.close()`/`DuplexShellOutput.close()`——转发给 owned 输出，最终落到 ①，受影响面随 ① 修复闭合。
- **信心水平**: 确定
- **误报排除**: 不是「原语误用不是问题」的反例——单看 `offer` 无错，错在**终止信号的 at-most-once 丢弃语义**与流协议（消费者以 EOF 为唯一终止条件）组合后违反终止性；也不是已声明边界——无任何文档声明「队列满时 EOF 可丢失」。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：shell 投产即回 P1)｜新发现——回码核验成立（close() L45 / sendEof() L53 offer 返回值忽略、adapter read() L68-76 以 EOF 为唯一终止信号）；触发链确定性论证核验通过且强于 C1-003 的「竞态」口径：单命令路径 close 由生产线程在命令返回后立即调用、此刻消费者（collectOutput）尚未启动 → 队列必满 → 恰 1024 chunk 输出即确定性丢 EOF；同站点三登记（C6-005↔C8-002↔C1-003）同证据确认，按 C6 复核「两案同批同判级」原则随 C6-005 条件化 P2，主登记择一留合并裁定；C1 复核「终裁取 C8 判级」——C8 侧终裁意见即本行：条件化 P2（与零生产接线证据及族案判级一致）

### [C8-003] 管道 stage 失败路径：下游死亡后上游永久阻塞在 `put()`，逐次泄漏全局 worker 线程

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:263-271`
- **证据片段**:
  ```java
  CompletableFuture<Integer> stageFuture = CompletableFuture.supplyAsync(() -> {
      try {
          return executeSimpleCommandWithContext(cmd, input, out, context.stderr(), context, cancelToken);
      } catch (Exception e) {
          throw io.nop.api.core.exceptions.NopException.adapt(e);
      } finally {
          IoHelper.safeClose(out);      // 只关自己的输出；从不关闭 input
      }
  }, executor);
  ```
- **严重程度**: P1
- **并发场景**: 上游 stage 线程（生产者，阻塞于 `BlockingQueueShellOutput.put()`）与下游 stage 线程（消费者，抛异常死亡后不再 `take()`）；线程来自共享 `GlobalExecutors.globalWorker()`。
- **触发时序**: T1=stage i+1 在 `executeSimpleCommandWithContext` 中抛异常（如命令检查通过但执行失败），其 future 异常完成，该线程退出且**再无人从 stage i 的输出队列 `take()`**；T2=stage i 持续写输出，写满 1024 chunk 后 `put()` 永久阻塞；T3=stage i 的 finally 永不执行（线程停在 try 块内），`out` 永不关闭，无人投递 EOF；stage i 的 future 永不落定，该 worker 线程永久占用。若 stage i 恰为末级，pipeline future 亦永不完成（叠加 C8-001 机制 B）；若非末级，调用方可能收到「成功」结果而线程已泄漏。
- **现状**: 失败传播只沿 future 链向下游走，从不向**上游**反向关闭 input 以解除生产者阻塞；`finally` 清理是不对称的（F8：只 close 产出、不 close 消费）。
- **风险**: 每次「下游失败 + 上游 ≥1024 行输出」组合泄漏一个全局池线程；反复失败（agent 循环重试坏管道）累积后 `globalWorker` 耗尽，同 JVM 所有依赖该池的功能停摆——负载/故障上升必然恶化的资源耗尽。
- **建议**: stage 异常路径显式 `IoHelper.safeClose(input)`（使上游读者侧判定终止）并将异常写入上游队列或关闭上游 output 以解锁 `put()`；更彻底的做法是给 stage 间队列接入平台 `IBlockingQueue` 的中断式关闭语义，或在 `put` 循环中检查下游 future 的完成状态（`cancelToken`/`stageFuture.isDone()` 轮询检查点）。
- **失效面枚举**（F3 同族清扫）: ① `executePipeline` L263-271——**缺陷本体**；② `executeSimpleCommand` L236-239 异常路径——同线程收集（无独立消费者线程），`safeClose` 后异常上抛，**不受影响**；③ `executeBackground` L363-365 `join()`——后台体阻塞于 sink 线程，无取消传播检查点（本条不同机制，登记观察：sink 实现在 agent 侧，超界）；④ `AbstractShellInput.close()` L143-146——提供了解除读端阻塞的机制（置 eofSeen），但本缺陷中无人调用它，属「机制存在、失败路径未接线」。
- **信心水平**: 确定
- **误报排除**: 不是「低频路径粗粒度同步合理」项——管道是 shell 主路径；也不是单纯理论竞态——触发链完全具体（下游异常是常规事件，如 grep 类命令退出码非零本属正常，异常路径来自命令抛错/检查器放行后的运行期失败）。
- **复核状态**: 未复核
- 复核状态: 降级(P2，条件化：shell 投产/真实适配器接通即回 P1)｜新发现——回码核验成立（stage lambda L263-271 finally 只 close 自身 out、从不关闭 input；下游死亡后上游写满 1024 chunk 阻塞于 put、其 finally 永不执行）；子项修正一处：触发时序「若 stage i 恰为末级，pipeline future 亦永不完成」分支不成立——末级不存在下游，末级失败时 pipelineFuture（链于末级）异常完成、被泄漏的是其上游段；准确口径：泄漏线程在「失败段末级（调用方见失败）」与「失败段非末级（调用方见成功假象）」两形态下均不可回收，与 C6-004 复核的主面时序修正一致；族案成员随 C6-004 条件化降档，shell 零生产接线证据同 C8-001 复核行

### [C8-004] 消费线程中断被映射为「干净 EOF」：输出静默截断且无任何标记（与 `BlockingQueueShellInput` 语义不一致）

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:66-81`
- **证据片段**:
  ```java
  // BlockingQueueShellOutput.BlockingQueueShellInputAdapter.read()
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;                       // L79: null ≡ EOF，readAllText 据此正常收尾
  }
  ```
  对照同族 `BlockingQueueShellInput.read()` L33-36：中断时 `throw NopException.adapt(e)`（失败可见）。
- **严重程度**: P2
- **并发场景**: 消费者线程（`collectOutput`/`readAllText` 所在线程）被外部中断（执行器 shutdown、上游超时控制、`Future.cancel(true)`）时，与仍在生产的生产者线程交叠。
- **触发时序**: T1=消费者在 `queue.take()` L71 阻塞等待下一 chunk；T2=外部中断到达（如调用方对 pipeline future `cancel(true)` 或线程池 shutdown），L77 捕获、复位中断标志、返回 null；T3=`AbstractShellInput.readAllText()` L69 视 null 为 EOF 正常返回**部分内容**——调用方拿到「成功、截断」的 stdout，与「未截断」结果不可区分（无 truncated 标志、无异常、无日志）。生产者随后写入的 chunk 全部滞留队列成为垃圾。
- **现状**: 同一模块内两个队列读端对中断的语义相反（一个抛异常、一个映射 EOF）；adapter 选择静默吞掉中断（F9 变体：吞的不是异常而是终止信号差异）。
- **风险**: 过载/取消路径上输出被静默截断，AI 消费方（模型）据不完整输出继续推理而无任何「被截断」线索；问题定位困难（表面是成功结果）。理论风险已具体化（触发链完整），但需外部中断才能到达，故 P2。
- **建议**: 与 `BlockingQueueShellInput` 对齐：中断时抛 `NopException`（或返回携带 truncated 标志的结果对象）；至少在 catch 内记录 WARN 并在返回前丢弃剩余队列内容。修复属一处改动，连同 `asInput()` 的全部调用方（`ShellCommandExecutor.collectOutput`、`TeeOutput.asInput`）回归。
- **失效面枚举**（F3 同族清扫）: ① `BlockingQueueShellOutput$BlockingQueueShellInputAdapter.read()` L77-79——**缺陷本体**；② `BlockingQueueShellInput.read()` L33-36——**已正确**（抛异常），作为修复模板；③ `AbstractShellInput.readLine()` L35-44 / `chunks()` L111-117——null-as-EOF 的消费端约定，属 ① 的下游受害者，不单独修；④ `FileShellInput.read()`/`ListShellInput.read()`——非阻塞实现，无中断点，不受影响。
- **信心水平**: 确定
- **误报排除**: 不是风格问题——null 返回值在本输入协议中**就是** EOF 信号（`IShellInput` 契约），中断复用该信号即造成语义违约；已对照误报校准「原语使用不是问题」——此处是协议级语义冲突而非原语用法。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立（adapter read() L77-79 中断复位后返回 null ≡ EOF、readAllText L65-77 以 null 正常收尾返回部分内容、对照 BlockingQueueShellInput.read() L33-36 抛 NopException 失败可见）；本条为该不对称的准确登记版本——C4-CL-008② 把返回 null 的类误写为 BlockingQueueShellInput（实际是其抛异常；返回 null 的是 BlockingQueueShellOutput 内部 adapter），CL-008② 处置时应以本条为准；触发需外部中断（cancel(true)/shutdown），P2 校准成立；shell 零生产接线条件面同 C8-001 复核行登记（不改变 P2）

### [C8-005] `executeParallel` 并发信号量：acquire 与 whenComplete 之间的同步异常泄漏 permit；阻塞 acquire 无超时形成调度器级联停摆点

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:232-246`
- **证据片段**:
  ```java
  try {
      semaphore.acquire();                        // L238 阻塞式，无超时
  } catch (InterruptedException e) { ... }
  return callTool(toolName, call, context)        // L244 若此处同步抛出……
          .whenComplete((result, error) -> semaphore.release());  // L245 ……release 永不执行
  ```
- **严重程度**: P2
- **并发场景**: 调度线程（`executeParallel` 的调用方，通常是 agent 引擎派发线程）在 `acquire()` 上阻塞；工具执行线程持有 permit 直至工具完成。
- **触发时序**: 泄漏链：T1..Tn=n 个调用分别 `acquire()` 耗尽 maxConcurrency 个 permit；T1 的 `callTool` 在返回 future 前同步抛出（`interceptors.beforeCall/afterCall` L54-55/L86-88 抛 RuntimeException、`executorProvider.getExecutor` 实现异常等——`callTool` 对同步异常无 try/except 包裹），L245 的 `whenComplete` 从未挂接 → 该 permit 永久丢失；此后每次 `submitBounded` 在 L238 `acquire()` **阻塞调用线程**直至永久——派发线程停摆，且无指标暴露「卡在 acquire」。级联停摆链：某工具无界期挂死（本 manager 层无 per-call 超时）占住 permit 不放 → 后续调用线程全部阻塞在 acquire。
- **现状**: permit 的释放以「callTool 正常返回 CompletionStage」为前提，异常路径无 finally 兜底；acquire 无超时、无拒绝/降级策略（平台 `IRateLimiter`/`ISemaphore`/`QueueOverflowPolicy` 均未使用）。
- **风险**: 一次拦截器异常即进入不可自愈的永久停摆；长时运行中工具挂死 + 无界 acquire 组合使并行工具派发线程逐个冻结。P2 而非 P1 的原因：同步异常前提依赖拦截器/提供方实现质量（当前内置拦截器为空列表时不可达），未达「负载上升必然触发」。
- **建议**: `acquire()` 成功后用 try/finally 或 `CompletableFuture.supplyAsync` 包裹 `callTool` 保证 release；acquire 改带超时版本并在超时时返回显式错误结果（对照 L241-242 的中断分支已有 fail-loud 模板）；考虑替换为平台 `io.nop.commons.concurrent.semaphore.ISemaphore` 获得统一 metrics。
- **失效面枚举**（F3 同族清扫）: ① `submitBounded` L237-245——**缺陷本体**；② `callTool` 的 pathQueues 分支 L101-121——无信号量参与，`compute` 内异常会传播但**不**泄漏 permit，不受本条影响（其无界问题归 C8-006）；③ `executeSequential` L248-266——无并发控制，不受影响；④ 三源码根内无其他 `Semaphore/acquire` 使用点（grep 证实），无更多同族。
- **信心水平**: 很可能（泄漏链代码级确定；「内置拦截器今日为空列表」使主触发前提当前不可达，依赖扩展方引入）
- **误报排除**: 不是「有界声明存在」的豁免项——maxConcurrency 有界但其**守卫可被同步异常绕过**（校准条款明确「守卫可绕过才是缺陷」）；也不是平台设施自身问题——JDK Semaphore 使用方式缺陷。
- **复核状态**: 未复核
- 复核状态: 保留(P2，附强休眠面标注)｜新发现——回码核验成立（acquire L238 无超时、release 仅挂于 L245 whenComplete、callTool 同步抛出时 whenComplete 从未挂接）；子项补强两处：① 泄漏面不依赖自定义拦截器——L122 直发分支的 executeNow.get() 同步抛出（如 context.getExecutor()==null NPE，C1 复核已证 ChatBi 装配可达）同样穿出 callTool 使 L245 永不挂接；② 强休眠面：submitBounded 唯一调用方 executeParallel ← callTools(parallel=true) 在仓内零生产调用方（grep 证实：callTools 仅 agent 测试 mock 实现，生产派发走 AgentToolDispatcher.callTool 逐调用，ToolManagerImpl L95 注释自认 executeParallel 无生产调用方）——缺陷本体真实但入口零接线，维持 P2 底判级并附条件（callTools/parallel 派发接线即达）

### [C8-006] `pathQueues` per-path 串行化队列：无容量上限、无溢出策略、entry 永不驱逐（QueueOverflowPolicy 该用未用 + F4 账本无界）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:32-33, 101-121`
- **证据片段**:
  ```java
  private final ConcurrentHashMap<String, CompletableFuture<Void>> pathQueues =
          new ConcurrentHashMap<>();              // L32-33: 只进不出，无任何 remove
  ...
  pathQueues.compute(pathKey, (k, tail) -> {     // L105: 每个 pathKey 永久占一个 entry
      CompletableFuture<Void> trigger = (tail == null ? ... : tail).handle((v, e) -> null);
      return trigger.thenCompose(x -> executeNow.get()...);   // L109: 新调用链接到旧尾部
  });
  ```
- **严重程度**: P2
- **并发场景**: 多个派发线程对同一 `pathKey`（文件路径）并发 `callTool`，经 `compute` 原子接链；无消费者线程——「队列」是 future 链本身。
- **触发时序**: 无界增长链：长时运行进程中每个曾被工具触碰的**不同路径**都留下一个 entry（写工具/不安全工具才有 pathKey；`ToolPathAffinity.extract` 从 path/file_path/filePath 提取），永不移除——T1 会话 A 写 `/w/a.txt` 建 entry；T2…Tn 会话 B/C/… 触碰各自路径，entry 数单调上升（F4）。同路径洪峰：同一 path 上游洪峰（同一会话并行派发 N 个同文件写调用）时链长 N 无上限、无拒绝、无降级——每链节占一个待执行调用 + 其捕获的 context，深链同时是内存放大器。队头堵塞：链头调用挂死（如 patch-file 读大文件卡 IO）则同路径所有后续调用无限期排队，无排队超时、无积压深度指标。
- **现状**: 过载行为完全未定义：既非阻塞、非拒绝也非降级，而是无限积压 + entry 永久驻留；平台 `QueueOverflowPolicy`（FAIL/Discard/阻塞策略族）明确可用却未用。
- **风险**: 声明规模包络内（agent 长会话、多会话共享进程）必然单调增长；虽然单 entry 小，但与同路径深链叠加后无上界。无观测手段（无队列深度 gauge）使过载不可诊断。
- **建议**: entry 驱逐：tail 完成且无新等待者时 `pathQueues.remove(pathKey, tail)`（compute 返回 null 语义）；容量护栏：per-path 挂起数超阈值时拒绝并返回显式错误（平台 `QueueOverflowPolicy.FAIL` 语义）；挂起等待加超时；暴露 `pathQueues.size()` 与 max 链长指标。
- **失效面枚举**（F3 同族清扫）: ① `ToolManagerImpl.pathQueues` L32-33/L105-118——**缺陷本体**；② `UpdateTodosExecutor.todoLists`（static ConcurrentHashMap，见 CL-014）——同族「只进不出」但属 C7 线索；③ `BashExecutionTracker.inFlight`——**已正确**（register/unregister 成对，finally 保证），作为同仓库修复模板；④ `DefaultToolExecutorProvider.executors`——工具名到执行器的注册表，键集合封闭（工具模型集合），不构成无界增长，不受影响。
- **信心水平**: 确定
- **误报排除**: 不是「有界声明存在」豁免——无任何容量声明与上限；不是 C7 重复立项——本条以「过载行为未定义（无满载语义）」为主张立 C8，entry 泄漏（F4）作为同一代码位置的伴生面，二者同源同修。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立（pathQueues L32-33 全文件唯一写点 L105 compute、无任何 remove/evict；ToolPathAffinity.extract L34-59 从 input JSON 提取 path/file_path/filePath）；生产接线核实（nopToolManager bean 于 ai-toolkit-defaults.beans.xml:65 + AgentToolDispatcher.java:683 生产调用 callTool）——不适用休眠降档；事实面一处勘误：完成后的旧链节可被 GC（C6-006 复核同口径），驻留增长 = path key 集合（F4）+ 在途深链窗口（排队调用的 context/结果），「每链节占一个待执行调用」限于洪峰在途期；三登记（C6-006↔C4-CL-005↔C8-006）同站点同事实确认，**同意 C6 复核建议：本条主登记**（过载语义 + 容量护栏 + 指标面 + 驱逐方案最全），C6-006 保留 F4 生命周期视角、C4-CL-005 供 C7 复核定界

### [C8-007] http-request / graphql-query 响应体无上界捕获：`getBodyAsString()` 全量物化后直接作为工具输出

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:244-272`；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/GraphqlQueryExecutor.java:150-158`
- **证据片段**:
  ```java
  // HttpRequestExecutor.buildSuccessResult
  String body = response.getBodyAsString();       // L246: 无任何 size 上限
  ...
  sb.append("\"").append(body != null ? StringHelper.escapeJson(body) : "").append("\"");
  return AiToolCallResult.successResult(call.getId(), sb.toString());   // L271 全量入工具输出
  ```
  ```java
  // GraphqlQueryExecutor 同族
  return AiToolCallResult.successResult(call.getId(), response.getBodyAsString());  // L158
  ```
- **严重程度**: P2
- **并发场景**: 工具执行线程（`context.getExecutor().submit`）同步读取远端响应；多个并行工具调用各自物化各自响应，内存压力叠加（与 C8-005 的并行派发组合）。
- **触发时序**: 单调用即时触发，无需竞态：T1=模型发起 `http-request` 指向返回大响应的 URL（下载、大 JSON 导出、日志端点）；T2=`getBodyAsString()` 将整个响应读入内存（字符串，char×2 字节），L271 原样塞进工具输出；T3=该输出进入结果通道（进一步拼接/转义，峰值 ≥3×body）。无响应体上限、无截断标记、无降级——超时（默认 30s，L82）只约束时长不约束体积，慢速大响应可在时限内注入数百 MB。
- **现状**: 同模块内 bash 工具有 1MiB 捕获上界（`BashSandboxConfig.DEFAULT_MAX_OUTPUT_BYTES`）、read-file 有 100K chars 上界（`ReadFileExecutor.DEFAULT_MAX_CHARS`）、skill 有 4000 chars 上界——唯独 HTTP 系两工具无上界（F3 缺口）。
- **风险**: 模型可预期地探到大响应端点即触发大分配（AI 工具输入不可信为威胁模型的一部分）；峰值内存放大（body + escapeJson 拷贝 + 结果容器）叠加并行调用可致 OOM；超大输出还会撑爆下游 LLM 上下文预算。
- **建议**: 复用 `BashSandboxConfig.maxOutputBytes` 的截断-续排空模式：读取时按上限截断并在输出尾部追加 `...(truncated N bytes)` 标记；上限走 `@InjectValue` 配置化；graphql 同步修改（F3）。
- **失效面枚举**（F3 同族清扫）: ① `HttpRequestExecutor.buildSuccessResult` L246/L271——**缺陷本体**；② `GraphqlQueryExecutor` L156/L158——**同族缺陷，同修**（错误路径 L156 的响应体拼接同样无界）；③ `SearchEngineExecutor` L63/L69——`maxResults` 服务端默认 10 且经 `request.setLimit` 下推，**已有界**；④ `JqToolExecutor`——jq 引擎输出限额检查点保障，已有界；⑤ `AskOracleExecutor`——fail-fast 未实现，无输出面。
- **信心水平**: 确定
- **误报排除**: 不属「序列化中间体积」C7 泛化项——本条主张是**工具输出捕获上界缺失**（C8 审计要点明列项），且同模块已有三种上界先例可对照，属一致性缺口而非纯容量问题。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立（HttpRequestExecutor L246 getBodyAsString 无上限、L263-271 全量入结果；GraphqlQueryExecutor L154-158 错误/成功双路径同样无界）；兄弟上界逐一核实（BashSandboxConfig.DEFAULT_MAX_OUTPUT_BYTES=1MiB L34、ReadFileExecutor.DEFAULT_MAX_CHARS=100000 L15、SkillExecutor MAX_LOADED_FILE_CHARS=4000 L76）——F3 一致性缺口成立；两 executor 均生产 bean（beans.xml L39-40）+ AgentToolDispatcher 生产可达——不适用休眠降档；P2 校准（单调用可触发但峰值受下游 LLM 预算与并行度约束，未达必然 OOM）维持

### [C8-008] 文件类工具输出上界可被调用方参数绕过：grep/glob 上限直通调用方、read-file 行区间无总量帽、patch-file 全量载入（对照 ApplyDelta 已修实例）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SearchContentExecutor.java:40-41`；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:374`；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ReadFileExecutor.java:48-66`；`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/PatchFileExecutor.java:53`；`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/file/FileToolBizModel.java:52-73`
- **证据片段**:
  ```java
  // SearchContentExecutor: 上限完全由模型调用方决定，无服务端 clamp
  int maxMatchesPerFile = call.attrInt("maxMatchesPerFile", 100);
  int maxFiles = call.attrInt("maxFiles", 10);
  // LocalToolFileSystem.grepRecursive 的唯一护栏就是调用方值的乘积
  if (result.size() >= maxFiles * maxMatchesPerFile) return;   // L374
  ```
  ```java
  // PatchFileExecutor: 无帽全量载入（对照 ApplyDeltaExecutor L51/L57 的 1M/10M 帽）
  TextResult fileResult = fs.readText(path, Integer.MAX_VALUE);  // L53
  ```
- **严重程度**: P2
- **并发场景**: 模型调用方（不可信输入面）单调用触发；并行派发下多个此类调用叠加（同 C8-007）。
- **触发时序**: T1=模型调用 `grep` 传 `maxMatchesPerFile=1000000000, maxFiles=1000000000`；T2=`LocalToolFileSystem.grep` 以乘积为唯一上限全树扫描，结果 List 与输出 StringBuilder 仅受命中数约束（每命中含整行内容 ≤65536 chars，L404）——内存上限=命中数×行长，实际无界；T3=输出全量进入工具结果。同族链：`read-file` 传 `lastLines=2000000000` 或 `toLine=Integer.MAX_VALUE`（L48-57）→ `readLines` 将全文件行读入 `List<Line>`（L125/135），绕过默认路径的 100K chars 帽；`patch-file`（L53）对任意大小文件全量载入+diff 应用（原/新双份字符串）；`FileToolBizModel.readFiles`（L52-73）的 `maxLengthPerFile` 直通 `IFileOperator.readFileContents`，而契约 `<=0 means no limit`（nop-ai-core IFileOperator L44-59）——省略即 0 即无限制，8192 默认值只作用于 `maxTotalLength` 且在**全量读入后**才截断（limit-after-load）。
- **现状**: 「批上限」存在但全部位于调用方可控参数上，服务端无 clamp；已修复实例（ApplyDeltaExecutor 1M/10M 帽）证明该族缺陷的修复模式已知，其余兄弟未跟进（F3）。
- **风险**: 模型幻觉参数或提示注入即可触发大分配/OOM；`grep` 另有时间面（无命中时全树扫描不可取消，见 CL-017）。
- **建议**: 所有 `attrInt` 上限参数加服务端 clamp（如 grep 命中总数 ≤1 万、read-file 行区间展开后字符总量 ≤100K 复用 DEFAULT_MAX_CHARS 帽、patch-file 复用 ApplyDelta 的 10M 帽）；`readFiles` 将 `maxLengthPerFile<=0` 兜底为 `defaultMaxLengthPerFile` 并把截断下沉到读取时。
- **失效面枚举**（F3 同族清扫）: ① `SearchContentExecutor` maxMatchesPerFile/maxFiles——**缺陷本体**；② `SearchFilesExecutor` maxResults L32——同构（默认 100 但可传大值），**同修**；③ `ReadFileExecutor` lastLines/fromLine/toLine L48-57——**同族**；④ `PatchFileExecutor` L53——**同族**（对照 `ApplyDeltaExecutor` L51/L57 已有帽——已修复实例）；⑤ `ListDirectoryExecutor` L32 maxCount=100 硬编码——**已正确**；⑥ `FileToolBizModel.readFiles` maxLengthPerFile 直通 + limit-after-load——**同族**；⑦ `SkillExecutor` MAX_LOADED_FILE_CHARS=4000——**已正确**。
- **信心水平**: 确定
- **误报排除**: 不属「已声明边界」——各 `.tool.xml` 契约声明的是默认值而非「调用方可任意放大」的语义；不与 C7 无界收集混排——本条主张是**上界绕过/缺失服务端 clamp**（C8 执行步骤 3 明列「单批超大输入是否绕过批上限」）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立（SearchContentExecutor L40-41 上限直通调用方、LocalToolFileSystem L374 乘积为唯一护栏；ReadFileExecutor L48-57 行区间路径 per-line 10000 帽但无总量帽 + countLines 全文扫；PatchFileExecutor L53 Integer.MAX_VALUE 全量载入对照 ApplyDeltaExecutor L51 1M/L57 10M 已修实例；FileToolBizModel L61-75 maxLengthPerFile 直通 + IFileOperator L46「<=0 means no limit」契约 + L72-73 limit-after-load）；失效面 7 项逐一核实无误（⑤ ListDirectoryExecutor 硬编码 100、⑦ SkillExecutor 4000 已正确）；grep/read-file/patch-file 均生产 bean——不适用休眠降档；P2 维持

### [C8-009] DockerBashSandbox 超时路径不销毁本地 `docker` CLI 进程：CLI 无响应时逐次泄漏进程 + reader 线程 + 捕获缓冲（对照 HostBashSandbox killTree 已修实例）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:127-156, 253-275, 321-331`；对照 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java:188-198, 208-226`
- **证据片段**:
  ```java
  if (timedOut) {
      killContainer(containerName);      // L138: 只 docker kill 容器，不销毁本地 docker CLI 进程
      awaitReader(reader, config.getWallSeconds());
      return new BashSandboxResult(exitCodeOrNegative(process), ...);  // L140 照常返回
  }
  ```
  ```java
  private static void awaitReader(Thread reader, int wallSeconds) {
      ...
      if (reader.isAlive()) reader.interrupt();   // L329: InputStream.read() 不可中断，interrupt 是 no-op
  }
  // 对照 HostBashSandbox.killTree L220: process.destroyForcibly() —— 兄弟实现有、Docker 实现没有
  ```
- **严重程度**: P1
- **并发场景**: 调用线程（`doExecute`，wall clock 到期）与 daemon reader 线程（`drainStream` 阻塞在 `process.getInputStream().read()`）；泄漏的 `docker run` CLI 子进程持有 stdout 管道写端。
- **触发时序**: T1=`docker run` CLI 因 daemon 无响应/网络悬挂而既不输出也不退出；T2=调用线程 `process.waitFor(wallSeconds)` 超时，`killContainer` 对**不存在的容器**执行 `docker kill/rm -f`（失败被吞），本地 CLI 进程未被 `destroyForcibly()`；T3=`awaitReader` join 2s 超时后 `reader.interrupt()` 对阻塞在 `read()` 的线程无效；`doExecute` 带部分输出返回，tracker 在 finally 注销——此后该 CLI 进程 + reader 线程 + ≤1MiB `captured` StringBuilder **永久泄漏**（管道写端由存活的 CLI 持有，read 永不返回）。每次 wall-clock 超时遇 CLI 悬挂泄漏一套。次要面：`runShortCommand` L266-275 的 `docker kill/rm` 进程 `waitFor(5s)` 超时后同样无人 destroy。
- **现状**: 清理不对称（F8）：Host 后端 killTree 销毁进程树，Docker 后端只清理远端容器、遗漏本地 CLI 进程；`awaitReader` 的 interrupt 是对不可中断 IO 的无效手势（F5：挂死线程对健康检测不可见——tracker 已注销，cancel 无法再寻址）。
- **风险**: 长时运行服务中 daemon 抖动期间的每次 bash 超时累积一个僵尸 `docker` 进程 + 线程 + 缓冲；进程句柄与线程耗尽属「错误路径必然累积的资源泄漏」（校准 P1 项）。正常路径（CLI 响应正常）不受影响——泄漏仅限 CLI 悬挂分支。
- **建议**: 超时分支补 `process.destroyForcibly()`（先杀 CLI 再 killContainer，或之后兜底）；`runShortCommand` 的 `waitFor` 超时分支同样 destroy；`awaitReader` 的 interrupt 替换为「关闭底层 Stream 迫使 read 抛异常」或直接放弃线程但登记泄漏计数。
- **失效面枚举**（F3 同族清扫）: ① `DockerBashSandbox.doExecute` 超时分支 L137-141——**缺陷本体**；② `DockerBashSandbox.runShortCommand` L274——**同族**（waitFor 无 destroy 兜底）；③ `HostBashSandbox.killTree` L208-226——**已正确**（destroyForcibly 进程树），修复模板；④ `HostBashSandbox.awaitReader` L188-198——与 ① 共享的 interrupt-no-op 手势（其进程已被 killTree 销毁、管道随进程树死亡关闭，实际可达性低），随 ① 的修复模式统一；⑤ `BashExecutionTracker.cancelExecution` L82-98——**已正确**（descendants + process 双 destroy）。
- **信心水平**: 确定（代码路径确定；触发依赖 docker CLI 悬挂这一外部故障模式）
- **误报排除**: 不是「best-effort 清理可接受」——注释声称超时路径有完整清理（类 javadoc「A timeout issues docker kill + docker rm -f cleanup」），实现与声明不符即守卫缺口；不属平台设施重审——ProcessBuilder 使用方式问题。
- **复核状态**: 未复核
- 复核状态: 保留(并案收编：主登记 C6-002，成员面 P1)｜新发现（宿主站点）——回码核验成立（DockerBashSandbox 全文件无 destroyForcibly、超时分支 L137-141 仅 killContainer、awaitReader L321-331 join 宽限 + interrupt 对管道 read 无效、runShortCommand L266-275 waitFor(5s) 返回值忽略无 destroy；对照 HostBashSandbox.killTree L212/L220 destroyForcibly 已修实例）；归属核对并表态：**同意 C6 复核细化**——CLI 进程滞留 + killContainer 失败吞掉 + 取消句柄注销归 C6-002 主登记，reader 线程/管道滞留面由 C6-001 覆盖，runShortCommand waitFor 面归 C6-003，本条关闭为成员不单独计数；两处口径精确化：① javadoc L28-29 只声明容器级清理、未声明本地 CLI 进程，「实现与声明不符」论证弱化，F3 残留形态（Host 已修/Docker 未跟）独立支撑立案；② 沙箱为注释内 opt-in 装配（beans L20-34 注释块、shipped default bash fail-closed 拒绝），随 C6 复核「按配置可达生产路径判级、不适用休眠降档」口径维持成员 P1；WI 3.1 MA-17 沙箱族判级分歧（P2 vs P1）同 C6-001/002 登记 7.1 统一

### [C8-010] `>` 重定向路径绕过 1024 chunk 队列上界：`FileShellOutput` 无界 StringBuilder 缓冲至 close 才落盘

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/FileShellOutput.java:26-40`；接线点 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:485-489, 532-543`
- **证据片段**:
  ```java
  public void write(ShellChunk chunk) {          // L26
      if (closed) throw ...;
      if (chunk.isText()) {
          buffer.append(chunk.asText());          // L29: 无上限累积
      }
  }
  public void flush() {                           // L34
      if (buffer.length() > 0) {
          String content = buffer.toString();     // L36: 整体拷贝后一次落盘
          buffer.setLength(0);
          fileSystem.writeText(filePath, content, append);
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 命令执行线程对 `FileShellOutput` 单线程写（重定向接线于 `applyRedirects`）；与队列路径（B1/B2，1024 chunk 有界）并行存在、互为对照。
- **触发时序**: T1=命令产生重定向（`applyRedirects` L485-489 将 stdout 换为 `FileShellOutput`）后持续输出（外部命令集成下可为任意大流）；T2=每 chunk 追加进 `buffer`，无容量检查、无落盘——同一输出若走队列路径会在 1024 chunk 处产生背压/挂死，走重定向路径则**完全绕过上界**无限累积；T3=close（L43-48）时 `flush()` 再整体 `toString()` 拷贝一份（峰值 2×）才写盘。`>&` 合并重定向（L532-543）共享同一无界实例，流量双倍。
- **现状**: 有界队列声称的「输出洪峰有界降级」仅覆盖管道/收集路径；文件重定向路径是未声明的无界旁路，且缓冲策略（全量驻留内存、close 才落盘）使长命令的中间产出不可见、崩溃即全丢（F2 邻接：无中间落盘语义）。
- **风险**: 大输出重定向（`cat big > out`、构建日志）内存随输出线性增长至 OOM；与 C8-001 修复后命令可跑完的前提下，该旁路成为新的无界点。
- **建议**: `FileShellOutput` 改为阈值触发分块落盘（如 64KB flush 一次，append 语义已支持）；或复用平台带缓冲上限的写出设施；`flush()` 的双倍拷贝用 `FileHelper.writeText(CharSequence)` 类 API 消除。
- **失效面枚举**（F3 同族清扫）: ① `FileShellOutput.write/flush` L26-43——**缺陷本体**；② `FileShellInput` 构造器 L17-28（`readText(filePath, 0)` 全量载入文件到 String，`<` 输入重定向的无界输入侧孪生）——**同族，同修时机处理**；③ `ListShellOutput.write`（无界 CopyOnWriteArrayList）——测试用途输出器，登记不立项；④ `PrintStreamShellOutput.write`——流式直写，**已正确**（无缓冲累积）；⑤ `TeeOutput.write`——扇出到子输出，上界随子输出（①在列时受其影响）。
- **信心水平**: 确定
- **误报排除**: 不是 C7 泛化重复——本条主张是**队列上界的旁路**（过载降级语义的不一致：同洪峰两条路径一有一无），C8 范围内的行为性缺陷；单线程不豁免（降档口径明示「过载/无界面是行为性缺陷——单线程不豁免」）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立（FileShellOutput L26-31 write 无上限累积、L34-40 flush 整体 toString 拷贝后一次落盘、close L43-48 才 flush；接线点 handleOutputRedirect L485-489 / handleMergeRedirect L532-543 共享同一实例）；行为性缺陷单线程不豁免口径核对通过；shell 零生产接线条件面同 C8-001 复核行登记（已为 P2 底，不另降）；与 C8-012 修复同批（分块落盘 + 字节帽）

## 维度集外线索（overlay C2/C3/C5/C7/C9）

### [CL-014]（C7/F4）UpdateTodosExecutor 静态会话 todo 表只进不出
`nop-ai-toolkit/.../tools/UpdateTodosExecutor.java:25` —— `static final Map<String, List<TodoItem>> todoLists`，仅当某会话显式写空列表时 `remove`（L100）；会话正常结束/放弃不清理。长时运行服务器上每会话一项永久驻留（F4 账本无界）。单条数据小，增长缓慢，故作 C7 线索非 C8 发现。建议：会话关闭钩子清理或 TTL。

### [CL-015]（C3）UpdateTodosExecutor handleWrite 的 getOrDefault→put 丢单更新
`nop-ai-toolkit/.../tools/UpdateTodosExecutor.java:95,103` —— `oldTodos = todoLists.getOrDefault(...)` 与 `todoLists.put(listKey, newTodos)` 为 check-then-act：同会话两个并发 write 交错时后写覆盖前写（变更摘要亦基于过期 oldTodos 生成错误 diff）。应改 `compute` 原子完成读-改-写。工具若被模型并行调用（无 pathKey 亲和、非本 manager 串行化范围）即达。

### [CL-016]（C2）ShellCommandExecutor 实例字段 exportedEnv/currentWorkingDir 跨并发 execute 无同步
`nop-ai-shell/.../executor/ShellCommandExecutor.java:45-49, 318-334, 545-557` —— 长生命周期 executor（单例 bean 形态）的 `exportedEnv`/`currentWorkingDir` 为普通 HashMap/String 字段：两个并发 `execute()`（不同会话共享同一 executor）交错 `cd`/`export` 时相互污染；group/subshell 的快照恢复（L319-328 `whenComplete` 写回）与另一请求的写入构成丢失更新。checker 上下文（L106-113）读到的 env/目录也可能是中间态。属 C2 共享可变状态维度主线，此处登记交叉证据。

### [CL-017]（C5）fs 族工具不检查 cancelToken/expireAt：大扫描不可取消
`nop-ai-toolkit/.../tools/SearchContentExecutor.java:32-46`、`ReadFileExecutor.java:27-66`、`fs/LocalToolFileSystem.java:371-395` —— grep/glob/read-file 执行循环均不轮询 `context.getCancelToken()`/`getExpireAt()`；无命中的递归 grep 全树扫描时间无界且不可中断。对照同模块 `JqToolExecutor`（deadline fail-fast + cancel 中断检查点，注释明示）——同族中唯一具备取消能力的实例。长时运行挂死点 + 取消失灵，归 C5。

### [CL-018]（C7/F4）sequential-thinking 会话文件无 TTL/清理路径
`nop-ai-tools/.../service/ThoughtStorage.java:51-56, 104-115` —— 每会话一个 JSON 文件落于 `./_tmp/ai/sequential-thinking/store`，仅会话内 `clearHistory` 可清空内容；无任何按会话生命周期/过期时间的删除路径。单会话数据量在声明包络内（设计文档明示 tens/KB 级，不立项为规模缺陷），但目录级会话数在长时运行多会话服务器上单调增长。建议：会话关闭钩子或启动期 TTL 清扫。

### [CL-019]（C9）progressSnapshot 每次心跳全量拷贝捕获缓冲后再截尾
`nop-ai-toolkit/.../tools/sandbox/BashExecutionTracker.java:68-74` —— `captured.toString()`（≤1MiB）先整体拷贝，再 `substring` 取 2000 chars 尾部；心跳频率 × 1MiB 分配为可量化浪费（分配链：每快照 1 次全量 String + 1 次尾部 String）。建议：从尾部反向截取（StringBuilder 不可随机截尾，可在 publish 侧同时维护尾窗）。量化口径：50ms 发布节奏 × 快照调用频率 × 每次至多 2MiB（char×2）瞬时分配。

---

*审计完成：本批次（C8）共 10 条发现（P1×4：C8-001/002/003/009；P2×6：C8-004/005/006/007/008/010），维度集外线索 6 条（CL-014…019）。盲审纪律全程保持：未读同目录其他维度报告内容，CL 编号自 CL-014 续接。*

## 维度复核结论

- 复核人：C8 维度独立复核员（WI 3.2，2026-10-20）
- 方法：12 条发现（C8-001…010 本文件 + C8-011/012 见 C8-round2.md）逐条回码验证（ShellCommandExecutor/BlockingQueueShellOutput/Input/AbstractShellInput/FileShellOutput/FileShellInput/ToolManagerImpl/ToolPathAffinity/HttpRequestExecutor/GraphqlQueryExecutor/SearchContentExecutor/ReadFileExecutor/PatchFileExecutor/FileToolBizModel/DockerBashSandbox/HostBashSandbox/BashSandboxConfig/ExternalCommandAdapter/BashLexer/Redirect/beans 装配逐行核对）；查重比对 `ai-dev/audits/cra-index.md` WI 1.1–3.1 P1/P2 台账 + WI 3.1 MA-01 流式族/MA-03 F4 族；降档口径核对（过载/无界面单线程不豁免——C8-010 口径核验通过；休眠面按零生产接线先例条件化降档——C8-001/002/003/011/012 shell 侧与 C8-005 toolkit 侧适用，grep 证据在案）。

### 逐条判定表（C8-001…012 全覆盖）

| 发现 | 初审判级 | 判定 | 最终严重度 | 核心依据 | 同案/去向 |
|---|---|---|---|---|---|
| C8-001 先生产后收集 ≥1024 挂死 | P1 | 降级 | P2（条件化） | 回码成立；shell 零生产接线 + 内建命令 ≤1 chunk + 适配器 UOE 桩 | 族案成员（主登记 C6-004） |
| C8-002 EOF offer 静默丢失 | P1 | 降级 | P2（条件化） | 回码成立且触发链强于 C1-003 口径；同站点三登记同批同判级 | 与 C6-005 择一主登记（成员 C1-003） |
| C8-003 管道下游死亡上游阻塞 | P1 | 降级 | P2（条件化） | 回码成立；末级分支表述修正；shell 零生产接线 | 族案成员（主登记 C6-004） |
| C8-004 中断映射 EOF 静默截断 | P2 | 保留 | P2 | 回码成立；本条为准确版本（C4-CL-008② 类名误写以其为准） | 独立（CL-008② 处置输入） |
| C8-005 信号量 permit 泄漏 + 无界 acquire | P2 | 保留 | P2（附强休眠面） | 回码成立 + 泄漏面补强（L122 NPE 直发）；executeParallel 零生产调用方 | 独立 |
| C8-006 pathQueues 无界无溢出策略 | P2 | 保留 | P2 | 回码成立；生产接线不降档 | **三登记主登记**（成员 C6-006/C4-CL-005） |
| C8-007 HTTP/GraphQL 响应无上界 | P2 | 保留 | P2 | 回码成立；生产 bean；F3 兄弟上界对照成立 | 独立 |
| C8-008 文件工具上界可被参数绕过 | P2 | 保留 | P2 | 回码成立（7 失效面逐一核实）；ApplyDelta 已修实例对照成立 | 独立 |
| C8-009 Docker 超时路径不销毁本地 CLI | P1 | 保留（并案收编） | P1（成员面） | 回码成立；opt-in 装配按配置可达判级 | 并入 C6-002 主登记（reader 面归 C6-001、waitFor 面归 C6-003） |
| C8-010 `>` 重定向无界旁路 | P2 | 保留 | P2 | 回码成立；单线程不豁免口径核验通过 | 独立（与 C8-012 同批修） |
| C8-011 `0<&1` 自引用挂死（round2） | P2 | 保留 | P2（条件化标注） | 回码成立（语法链 + 拓扑接线 + 内建无 stdin 读点） | 挂死族成员（随 C6-004 族案） |
| C8-012 计数有界字节无界（round2） | P2 | 保留 | P2 | 回码成立（聚合无帽 + 单巨 chunk + SafeLineReader 模板在位） | 独立（与 CL-010 同源不同面） |

**判定统计**：保留 9 / 降级 3 / 驳回 0。**最终保留严重度分布**：P1×1（C8-009，并案成员随主登记 C6-002/C6-003，不单独计数）+ P2×11（其中 C8-001/002/003 为条件化 P2——shell 投产/真实外部适配器接通即回 P1；C8-005/011 附条件标注）。**现状标注分布**：新发现 ×12（本单元首审；已知未修复 0、已修复 0；唯一已修实例对照 ApplyDeltaExecutor 1M/10M 帽与 HostBashSandbox.killTree，均作为 F3 残留形态证据）。**查重结论**：cra-index WI 1.1–3.1 台账逐条比对零同案命中（同族关联见下）。

### 跨维指针核对意见（C8 侧）

1. **C8-001/C8-003 ↔ C6-004 族案 ↔ C4-003（shell 管道死锁族）**：零生产接线依据**同样适用于 C8-001/002/003**——本复核独立复现 grep 证据（io.nop.ai.shell 无 main 消费方、无 beans 注册、仅 nop-ai-agent 测试源 import、ExternalCommandAdapter UOE 桩 adapter/ExternalCommandAdapter.java:11、内建命令 cd/echo/ls 单次输出 ≤1 chunk），同意 C6 复核对 C6-004/005 的条件化降档，并按「同族同根同批同判级」原则将 C8-001/002/003 同步降为条件化 P2。**判级分歧登记 7.1**：C4 复核维持族案 P1 vs C6 复核条件化 P2 vs 本复核条件化 P2——建议按「装配后生产形态」（shipped default = bash 拒绝 + shell 模块零接线）统一裁决；无论走向，族案成员关系与单 diff 修复范围不变。
2. **C8-002 ↔ C6-005 ↔ C1-003（EOF offer 同站点）**：三登记同证据确认；C1 复核建议「终裁取 C8 判级」——C8 侧终裁意见：**条件化 P2**（触发链确定性论证采纳本条 C8-002 口径——恰 1024 chunk 单命令路径 close 时队列必满，强于 C1-003「竞态」表述；判级随零生产接线与 C6-005 同批同判级）；主登记择一（C6-005 或 C8-002）留合并裁定，两案须同 diff。
3. **C8-006 ↔ C6-006 ↔ C4-CL-005（pathQueues F4 三登记）**：同意 C6 复核建议——**C8-006 主登记**（过载语义 + 容量护栏 + 指标 + 驱逐方案面最全）；C6-006 保留 F4 生命周期视角；C4-CL-005 的「链式持有完整工具结果」内存放大面交 C7 复核定界（本复核勘误：完成后的旧链节可 GC，驻留 = path key 集合 + 在途深链窗口）。
4. **C8-009 ↔ C6-002/C6-003 ↔ C4-002 ↔ C6-001（沙箱超时清理族）**：**同意 C6 复核归属细化**——CLI leg（超时分支不 destroy 本地 CLI + killContainer 吞失败 + 取消句柄注销）归 **C6-002 主登记**；reader 线程/管道滞留面由 C6-001 覆盖；runShortCommand waitFor 无 destroy 面归 **C6-003**；C4-002 心跳线程占用面并入 C6-003 工单。C8-009 关闭为成员（P1 随主登记，不单独计数）。精确化两点：javadoc 只声明容器级清理（「声明与实现不符」论证弱化，F3 残留独立支撑）；沙箱 opt-in 装配随 C6 口径不降档。
5. **C8-004 ↔ C4-CL-008②（中断映射 EOF）**：C4-CL-008② 把返回 null 的类误写为 BlockingQueueShellInput（实际该类抛 NopException；返回 null 的是 BlockingQueueShellOutput 内部 adapter）——CL-008② 处置时以 C8-004 为准，C4 侧 ①（非末位异常被吞）维持独立。
6. **C8-012 ↔ CL-010（FileShellInput）**：同源不同面（构造期全量载入 C5 vs 消费聚合无帽 C8），与 C1 复核处置一致，不重复立项、同批修复。
7. **跨单元家族关联（零重复立项）**：挂死族 ↔ WI 1.1 C4-001（有界队列互等，不同设施）；背压旁路/无界缓冲 ↔ WI 2.3 C3-003（BufferedStreamingPublisher 背压断裂）、WI 3.1 MA-01（流式主链聚合计无界）；pathQueues F4 ↔ WI 3.1 C5-015(MA-03) F4 引擎终态清理族——均台账关联。

### 高风险条目子项复核说明

- **C8-001（P1 初判，高风险降档条目）**：两机制确定性逐行核验通过（机制 A：L225 命令同线程写满 1024 后第 1025 个 put 阻塞、L230 收集被顺序约束；机制 B：L263-271 末级 supplyAsync 写阻塞 + L280 thenApply 拓扑禁止消费者先启动）；降档决策独立于机制成立性，仅依据零生产接线事实（证据同跨维指针 1）；修复建议（收集与生产并行化）与族案单 diff 兼容。
- **C8-003（P1 初判，高风险降档条目）**：主体时序核验通过且**不受 C6-004 复核时序修正影响**（该修正是关于死亡段下游 c 收 EOF 正常终止；本条是死亡段上游 a 被阻塞——两正交面）；子项修正：「若 stage i 恰为末级」分支删除（末级无下游，不可能成为被阻塞的上游），准确口径见状态行。
- **C8-005（休眠面条目）**：泄漏面比初判更宽（拦截器空列表不可达的结论过保守——L122 直发路径 executor NPE 同步逃逸即可达，C1 复核 ChatBi 证据）；但入口休眠面也比初判更强（executeParallel 零生产调用方，初判未发现）——两向修正相抵，P2 维持。
- **C8-006（三登记主登记条目）**：驻留语义勘误（完成旧链可 GC，初判「每链节占一个待执行调用」限于在途窗口）；生产接线 + F4 增长口径（路径基数 × 长驻会话）核验通过，P2 维持；主登记身份建议 7.1 采纳。
- **C8-009（并案条目）**：全部证据片段逐行核实（含 javadoc L28-29 原文）；归属细化表态见跨维指针 4；opt-in 判级口径与 C6-001/002/003 一致（配置可达生产路径），成员 P1 维持。

## 最终保留项

- **P1×1（并案成员）**：C8-009（并入 C6-002 主登记；reader 面归 C6-001、runShortCommand waitFor 面归 C6-003；opt-in 装配按配置可达判级）。
- **P2×11**：条件化 P2×3——C8-001（族案成员，主登记 C6-004）、C8-002（与 C6-005 择一主登记，成员 C1-003）、C8-003（族案成员）；条件标注 P2×2——C8-005（executeParallel 接线即达）、C8-011（stdin 读命令/适配器接通即回 P1）；独立 P2×6——C8-004、**C8-006（pathQueues 三登记主登记）**、C8-007、C8-008、C8-010（与 C8-012 同批修）、C8-012。
- **判级一致性提示（7.1 终裁输入）**：① shell 管道/EOF 挂死族本复核条件化 P2 = C6 复核口径，vs C4 复核族案 P1——按「装配后生产形态」统一；② EOF offer 站点三登记（C6-005/C8-002/C1-003）须同批同判级，C8 侧终裁意见 = 条件化 P2；③ C8-009 并案后 C8 维度无独立 P1，修复归属以 C6 主登记为准。
