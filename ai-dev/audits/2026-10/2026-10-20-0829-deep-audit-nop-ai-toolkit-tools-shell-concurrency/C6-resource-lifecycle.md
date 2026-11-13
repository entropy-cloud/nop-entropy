# C6 资源生命周期与泄漏 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计日期：2026-10-20（盲审初审，单批次）
- 审计维度：C6 资源生命周期与泄漏（进程/流/句柄生命周期为主战场）
- 审计员：C6 维度盲审初审员（未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`，未查 git log）

## 审计范围与口径

**单批次全源码根覆盖声明**：本条目覆盖以下三个源码根的全部手写 `.java` 文件（不含 `_` 前缀生成文件），逐文件审读：

| 源码根 | 文件数 | 审读重点 |
|---|---|---|
| `nop-ai/nop-ai-toolkit/src/main/java` | 74 | 沙箱进程/容器生命周期（sandbox/*）、mavenvfs 句柄、文件操作（fs/*、tools/*） |
| `nop-ai/nop-ai-tools/src/main/java` | 23 | 内建工具文件/流操作（ThoughtStorage、FileToolBizModel、DslToolImpl） |
| `nop-ai/nop-ai-shell/src/main/java` | 44 | shell 管道流（io/*）、执行器（executor/*）、命令实现（commands/impl/*） |

**口径纪律声明**：

1. 以当前 live code 为准；现状标注（新发现/已知未修复）留待复核阶段。
2. 泄漏与残留形态一律**不适用启动期单线程降档**——所有发现按运行期路径判级。
3. 并发类发现写出具体 T1/T2 触发时序；写不出的标注"理论风险，未具体化"并降 P2。
4. 基线 `_tmp/cra-baseline-nop-ai-toolkit-tools-shell.txt` C6a/C6b 节全部命中点已逐一核对：
   C6a 14 处（SsrfGuardDnsResolver 注释、GraphqlQuery/HttpRequest 的 IHttpClient 注入——客户端为单例 bean 无泄漏面、Docker/Host sandbox 读者线程、ToolManagerImpl semaphore、DeltaVirtualFileSystem 流工厂）；C6b 9 处（两个 sandbox 的 waitFor/exitValue/killTree、VfsMavenCli 注释掉的 waitFor——该类实际不执行进程，无资源面）。基线无 C6a/C6b 命中于 nop-ai-tools 根。
5. `ExternalCommandAdapter` 在本模块内是抛 UOE 的桩（`adapter/ExternalCommandAdapter.java:11`），但它是下游注入真实外部命令执行的扩展点；涉及它的发现按"扩展点接通后的主路径"判级并显式标注前提。
6. mavenvfs 结论：`DeltaVirtualFileSystem` 的流全部由调用方持有（`Files.newInputStream/newOutputStream` 工厂），模块内部无句柄登记表/索引，无 lease 需释放；`VfsMavenCli` 不执行进程；`DeltaWorkspaceReader` 无流操作。**该子包未发现 C6 缺陷**。

**资源获取点配对表（C6 执行步骤 1–5 产出，摘要）**：

| 获取点 | 释放配对 | 结论 |
|---|---|---|
| Host/Docker sandbox `ProcessBuilder.start()` + 读者线程 | destroy 路径有；读者 join 有界宽限 + 无效 interrupt；流不关闭 | C6-001/C6-002/C6-008 |
| Docker timeout 路径的 `docker run` CLI 进程 | 无 destroyForcibly（对照 Host.killTree 有） | C6-002 |
| `docker kill`/`docker rm -f` 辅助进程（runShortCommand） | drain 无超时；waitFor(5s) 结果忽略、超时无 destroy | C6-003 |
| shell 管道 BlockingQueueShellOutput(1024) | write=阻塞 put；close=非阻塞 offer(eof)；仅末级被 drain | C6-004/C6-005 |
| ToolManagerImpl.pathQueues 注册表 | 只有 compute 无 remove/evict | C6-006 |
| ThoughtStorage 会话文件 | 只有创建/覆写，无删除路径；clearHistory 保留空文件 | C6-007 |
| LocalToolFileSystem 读流（readText/readLines/countLines/grepInFile） | finally safeClose 全配对 | 通过 |
| atomicReplace 临时文件（LocalToolFileSystem/ThoughtStorage） | finally deleteIfExists 配对 | 通过 |
| LocalToolFileSystem.globRecursive | Files.list try-with-resources | 通过 |
| JqToolExecutor cancel listener | appendOnCancel/removeOnCancel whenComplete 配对（有窄窗，见 CL-012） | 基本通过 |
| ToolManagerImpl.submitBounded 信号量 | acquire 后 whenComplete release 配对；interrupt 分支不持许可 | 通过 |
| Sandbox tracker（BashExecutionTracker） | register/finally unregister 配对 | 通过 |
| HTTP executors（HttpRequest/GraphqlQuery） | IHttpResponse 非 Closeable、body 由客户端缓冲；client 为注入单例 | 通过 |

## 发现

### [C6-001] Host/Docker 沙箱读者线程与管道在子进程持有 stdout 时永久泄漏（interrupt 对阻塞 read 无效）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java:92-95,144-198`；同族孪生 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:107-110,277-331`
- **证据片段**:
  ```java
  // HostBashSandbox.java L92-95（Docker 同型）
  Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
          "nop-bash-sandbox-reader");
  reader.setDaemon(true);
  reader.start();
  // L188-198 awaitReader：join 有界宽限后仅 interrupt —— 进程管道 read 不可被 interrupt 打断
  if (reader.isAlive()) {
      reader.interrupt();
  }
  ```
- **严重程度**: P1
- **并发场景**: 调用方工作线程（BashExecutor 经 `context.getExecutor().submit` 派发，或后台任务注册表线程）与每命令新建的 daemon 读者线程；读者线程阻塞在 `InputStream.read()` 原生管道读上。
- **触发时序**: T1 = BashExecutor 以 `sh -c "<cmd>"` 调 HostBashSandbox（`BashExecutor.java:308-319` buildArgv），命令为 `python -m http.server 8080 & echo started`（agent 启动开发服务器的常见成功负载）。T2 = `sh` 打印后退出，`waitFor` 返回，但 `&` 孪生子进程继承了 stdout 管道写端 → 管道不产生 EOF。T3 = `awaitReader` join 0.5–2s 超时，`reader.interrupt()` 对阻塞在原生 read 的线程无任何效果（进程管道流非可中断读）。后果：读者线程永久存活、继续阻塞，管道 fd 与 Process 对象不释放；`execute()` 正常返回结果，无任何报错。取消路径同型：`BashExecutionTracker.cancelExecution`（`BashExecutionTracker.java:82-98`）只 kill 进程不关流不终止读者，孪生子持有管道时读者同样永久残留。
- **现状**: 每次此类命令泄漏 1 个 daemon 线程 + 管道 fd；另 stdin 管道（`pb.start()` 默认）从不关闭，`sh -c "cat"` 类命令只能靠 wall 超时回收。
- **风险**: 长会话 agent 反复后台拉起服务进程 → 线程数与 fd 单调增长（线程栈每线程 ~1MB 虚拟内存）；fd 耗尽后沙箱全部拒绝服务。daemon 线程不阻断 JVM 退出，但服务进程常驻的部署形态（nop-ai-agent 长会话）必然累积。
- **建议**: (1) drainStream 结束/中断路径显式 `in.close()`；(2) 命令启动前用 `pb.redirectInput(new File("/dev/null"))`（NUL 设备）杜绝 stdin 悬挂；(3) 孪生子场景需在 wall 超时之外引入"主进程退出后 N 秒强制关流"策略，或文档声明"`&` 后台子进程导致输出截断/线程滞留"为已声明边界并给出 `ProcessHandle.descendants()` 收割方案（对照 `killTree` 已有 descendants API）。
- **失效面枚举**（F3 同族清扫）: ① `HostBashSandbox.execute` 读者线程（L92）— 缺陷本体；② `DockerBashSandbox.execute` 读者线程（L107）— 同型缺陷（docker CLI 退出即 EOF，仅 daemon 挂死时触发，同修）；③ `DockerBashSandbox.runShortCommand`（L269 try-with-resources）— 已正确关闭，无缺陷；④ `BashExecutionTracker.cancelExecution`（L87-88）— 只杀进程不关流，取消路径同型缺口；⑤ `VfsMavenCli` — 不执行进程，无面；⑥ shell 模块 `ExternalCommandAdapter` — 本模块为 UOE 桩，无进程，无面。
- **信心水平**: 确定
- **误报排除**: 非平台基础设施（nop-kernel）实现问题，是本模块自建 ProcessBuilder 生命周期管理；非"原语使用"误报——缺陷在复合路径（孪生子 + 不可中断读 + 有界 join）而非单个 API；也不是测试专用代码。
- **复核状态**: 保留(P1)｜新发现（残余缺口面：站点经 plan 2285/WI4 修复周期副本发布，本条缺口仍在）——回码核验成立（L92-95 读者线程、L188-198 有界 join + interrupt 对管道 read 无效、stdin 管道默认 PIPE 从不关闭、cancelExecution 只杀进程不关流均属实）；同站点 C4-001 已由 C4 复核并案、以本条为主登记并校准 P1，维持；与 WI 3.1 MA-17（nop-ai-agent DockerSandboxBackend 同病不同实现，族判 P2）跨单元同族对照成立、零重复立项，判级分歧登记 7.1 统一；沙箱为 fail-closed opt-in 装配（beans 注释明示），按配置可达生产路径判级、不适用休眠面降档

### [C6-002] Docker 沙箱超时路径不销毁 `docker run` CLI 进程，killContainer 失败被静默吞掉 → 容器无界残留且取消句柄已注销

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:129-141,253-264`
- **证据片段**:
  ```java
  // L137-141 超时分支：只 killContainer，从不 process.destroyForcibly()
  if (timedOut) {
      killContainer(containerName);
      awaitReader(reader, config.getWallSeconds());
      return new BashSandboxResult(exitCodeOrNegative(process), ...);
  }
  // L253-264：两步清理全部 catch Exception ignored —— 失败无痕迹、无重试、无升级
  private static void killContainer(String containerName) {
      try { runShortCommand("docker", "kill", containerName); }
      catch (Exception ignored) { /* best-effort — --rm should already clean up on kill */ }
      try { runShortCommand("docker", "rm", "-f", containerName); }
      catch (Exception ignored) { }
  }
  ```
- **严重程度**: P1
- **并发场景**: 单调用线程的超时/中断路径；与 tracker 的后台取消线程（`cancelExecution` → killContainer）竞争同一容器名。
- **触发时序**: T1 = wall 超时（或调用线程被中断，L132-135 置 timedOut=true）进入 killContainer。T2 = `docker kill`/`docker rm -f` 因 daemon 瞬断/慢响应抛异常（runShortCommand 内 IOException/InterruptedException）→ 双双被 `ignored` 吞掉。T3 = 容器继续运行且**无任何容器侧超时**（wallSeconds 仅由父进程 waitFor 强制）；finally 中 `tracker.unregister(callId)`（L120-124）移除了唯一的取消句柄 → 该容器成为不可寻址的永久残留，本地 `docker run` CLI 进程同样滞留（无 destroyForcibly 兜底，对照 HostBashSandbox.killTree L220 有 `process.destroyForcibly()`）。
- **现状**: 超时兜底链条单点且静默失败；注释"--rm should already clean up on kill"只在 kill 真正生效时成立。
- **风险**: 每次清理失败泄漏一个带 `--cpus/--memory` 配额的运行中容器 + 一个 CLI 进程；累计后占满 Docker 资源配额；容器内 `--network none` 下仍消耗磁盘/内存。属超时路径资源残留，按口径不降档。
- **建议**: (1) killContainer 失败改为记 WARN + 至少一次 `process.destroyForcibly()` 兜底杀 CLI（CLI 死不一定死容器，但阻止本地进程泄漏）；(2) 容器侧兜底：`docker run` 加 `--stop-timeout` 或启动时注册 `--name` 后由后台 reaper 在 wall+margin 兜底 `docker rm -f`；(3) 保留失败容器名到可观测面（日志/计数）而非 ignored。
- **失效面枚举**: ① `DockerBashSandbox.doExecute` 超时分支（L137）— 缺陷本体；② `DockerBashSandbox.doExecute` 中断分支（L132-135 复用 timedOut=true）— 同一本体覆盖；③ `BashExecutionTracker.cancelExecution` → cancelAction=killContainer（DockerBashSandbox.java:116）— 失败被 catch（killContainer 内部吞），同型；取消方能看到 boolean 返回但看不到清理失败；④ `HostBashSandbox.killTree`（L208-226）— 有 destroyForcibly 兜底，进程面通过（但同样不重试，仅进程级）；⑤ tracker.unregister 的 finally — 配对正确，不是缺陷点。
- **信心水平**: 确定
- **误报排除**: 非"有界声明存在的资源"——容器无 TTL、无 stop-timeout、无后台 reaper；非低频启动路径——超时是 bash 工具 wall 契约的正常触发分支。
- **复核状态**: 保留(P1)｜新发现——回码核验成立（Docker 全文件确无 destroyForcibly（对照 Host.killTree L220）、killContainer 双 catch ignored、finally unregister(L120-124) 撤销唯一取消句柄、容器无任何侧超时）；超时/错误路径资源残留，启动期降档不适用，P1 维持；同面 C8-009（超时分支 CLI 进程+reader 滞留）建议并入本条为主登记；MA-17 判级分歧同 C6-001 登记 7.1

### [C6-003] runShortCommand 先无限期 drain 后 waitFor(5s)：`docker kill` 挂起时超时清理路径永久阻塞，墙钟预算失效；waitFor 超时后辅助进程不销毁

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:266-275`
- **证据片段**:
  ```java
  private static void runShortCommand(String... argv) throws IOException, InterruptedException {
      Process p = new ProcessBuilder(Arrays.asList(argv)).redirectErrorStream(true).start();
      byte[] buf = new byte[1024];
      try (InputStream in = p.getInputStream()) {
          while (in.read(buf) != -1) {   // ← 无超时的 read：p 不退出则永久阻塞
              // discard
          }
      }
      p.waitFor(5, TimeUnit.SECONDS);    // ← 返回值忽略；超时后无 destroyForcibly
  }
  ```
- **严重程度**: P1
- **并发场景**: 超时路径调用线程（或 tracker 取消线程）与失控的 `docker kill`/`docker rm -f` 子进程；daemon 无响应时子进程不退出。
- **触发时序**: T1 = wall 超时进入 killContainer → runShortCommand("docker","kill",name)。T2 = docker daemon 挂死，`docker kill` 客户端不退出且管道保持打开。T3 = `in.read()` 永久阻塞（read 无超时，`waitFor(5s)` 永远到达不了）→ 执行墙钟超时清理的线程本身被挂死：wallSeconds 契约失效，调用方（BashExecutor 的 `context.getExecutor().submit` 任务）永不返回，占用工作线程。次生面：若 read 返回后 `waitFor(5s)` 超时，返回值被忽略且无 `p.destroyForcibly()` → 辅助进程滞留。
- **现状**: 清理辅助命令把"最多等 5 秒"的意图放在了 read 之后，实际 read 段无任何时限；waitFor 超时分支无资源回收。
- **风险**: 一次 daemon 故障即可把超时处置线程（可能是全局 worker 池线程）钉死，后续 bash 工具调用排队饿死（F5 族：挂死对健康检测不可见——无心跳覆盖此等待点）。
- **建议**: 用平台设施重写：`Process` + 限时 drain（`ExecutorService` + `Future.get(timeout)` 或进程级 `waitFor(total)` 后 `destroyForcibly()` 再 drain 剩余），或直接复用 `io.nop.commons.util.ProcessHelper` 类似工具（若存在）；最低限度：drain 循环加累计时限 + `waitFor` 返回 false 时 `destroyForcibly()`。
- **失效面枚举**: ① `runShortCommand("docker","kill",...)` 调用点（L255）— 缺陷本体；② `runShortCommand("docker","rm","-f",...)` 调用点（L260）— 同一本体；③ `HostBashSandbox.killTree` 的 `waitFor(2s)`（L222）— 结果同样忽略，但后续无依赖且进程已 destroyForcibly，滞留面仅理论，登记不修；④ shell 模块无同类辅助进程调用，无面。
- **信心水平**: 确定
- **误报排除**: 不是"低频管理操作粗粒度阻塞"——wall 超时是 bash 工具每次超时必经路径；不是平台设施自身缺陷——ProcessBuilder 使用方式是本模块代码。
- **复核状态**: 保留(P1)｜新发现——回码核验成立（drain read 无时限位于 waitFor(5s) 之前、waitFor 返回值忽略且无 destroy 兜底，L266-275 逐行属实）；F5 挂死对健康检测不可见面成立，P1 维持；C4-002（心跳线程占用独有面，C4 复核保留 P2）并入本条工单、C8-009 次面（runShortCommand waitFor 无 destroy）归本条；HostBashSandbox.killTree 已修实例对照成立（F3 残留形态确认）

### [C6-004] ShellCommandExecutor 管道：有界(1024)队列 + 阻塞 put + 仅末级 drain + 中途失败/早退弃管 → 执行器任务永久挂死、调用方永不返回、取消令牌无效

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:243-286`；队列语义 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:14-35`
- **证据片段**:
  ```java
  // BlockingQueueShellOutput：容量 1024，写阻塞，EOF 靠 close()
  this.queue = new LinkedBlockingQueue<>(1024);          // L15
  queue.put(chunk);                                       // L30 满→阻塞
  // ShellCommandExecutor L263-271：各段 supplyAsync，只有 finally 关自己的 out
  CompletableFuture<Integer> stageFuture = CompletableFuture.supplyAsync(() -> {
      try { return executeSimpleCommandWithContext(cmd, input, out, ...); }
      catch (Exception e) { throw NopException.adapt(e); }
      finally { IoHelper.safeClose(out); }
  }, executor);
  // L277-285：只链接最后一段；前序段异常无人观察；全程不读 cancelToken
  ```
- **严重程度**: P1
- **并发场景**: 管道各段并发运行于 `GlobalExecutors.globalWorker()`（L60 默认执行器）；上游段写队列与下游段读队列构成生产者-消费者对；失败段完成而上游段仍在写。
- **触发时序**（主面：中途段失败弃管）: T1 = `a | b | c` 中 b 启动即抛异常（ wired 外部适配器 spawn 失败 / 命令实现抛异常）→ b 的 stageFuture 异常完成，但该异常**从未被任何 thenApply/whenComplete 观察**（只链接最后一段，L277-285）。T2 = a 持续写自己的 out 队列（b 的输入），队列达 1024 后 `put` 永久阻塞——b 已死，无人再消费；b 的 finally close 的是 b 自己的 out，救不了 a。T3 = c 因输入 EOF 永不到来而阻塞在 `queue.take()` → pipelineFuture 永不完成 → 调用方永久挂起，≥2 个 worker 线程被钉死。次面：L252-254 早退（管道含复合命令）在已提交前序段后直接 return，同样弃管。第三面：单段输出 ≥1024 chunk 时即使无失败也在 put 上自锁（消费方 collectOutput 在命令返回后才启动，L277-285）。
- **现状**: 前提标注：本模块 `ExternalCommandAdapter` 为 UOE 桩，内建命令单条 println 输出极小，默认装配下难以触发 ≥1024 chunk；但模块契约即允许注入真实外部命令适配器，接通后上述主面是普通负载（大输出 + 任一段失败）可复现的确定性死锁。
- **风险**: worker 池线程永久泄漏、调用方（agent 会话线程）永久挂起；`cancelToken` 在 executePipeline 全程未被读取（F5：挂死对取消不可见），唯一出路是重启。`executeBackground`（L363-365）以 `join()` 包裹同一执行链，挂死会传导为后台任务体永久占用 sink 线程。
- **建议**: (1) 任一 stageFuture 异常完成时立即 safeClose 全部中间队列并向上游传播取消（`cancel(true)`）；(2) 对全部 stageFutures 挂 whenComplete 观察异常（F9）；(3) 早退分支先取消已提交段；(4) write 侧改带超时的 `offer(timeout)` 或满队列时 fail-fast 抛"downstream closed"；(5) 在 stage 循环中注册 cancelToken 回调，取消时中断各段。
- **失效面枚举**: ① `executePipeline`（L243-286）— 缺陷本体；② `executeSimpleCommand`（L221-241）— 同步执行自带配对（close 后 collect），≥1024 chunk 同样自锁在 put（同根缺陷的兄弟入口，同修）；③ `executeSequence`/`executeLogicalExpr`（L288-387）— thenCompose 链式无队列，通过；④ `executeBackground`（L350-370）— 放大器（join 无超时），登记关联修复；⑤ `collectOutput`（L627-633）— 异常吞成 ""，掩盖挂死后的部分输出语义，随本条一并修。
- **信心水平**: 很可能（默认桩装配下触发需外部适配器接通；代码路径本身确定）
- **误报排除**: 非测试专用简化并发模型——这是 shell 模块生产执行器；非"有界队列已配置上限即合规"误报——上限配置与阻塞写 + 末级才 drain 的使用方式互相矛盾（校准条款明确"除非上限配置与使用方规模声明矛盾"）。
- **复核状态**: 降级(P2，条件化：shell 执行器投产/真实外部适配器接通即回 P1)｜新发现——三个子面（中途段失败弃管、早退弃管、≥1024 chunk 自锁）回码核验全部成立；但主面 T3 时序修正：b 即抛时其 finally close 向 queueB 投 EOF（非满 offer 成功）→ c 正常收到 EOF 完成、pipelineFuture 落定，纯本面后果为上游段 worker 线程永久钉死 + 异常无观察 + F2 半个成功；"调用方永久挂起"需组合 C6-005 满队列 EOF 丢失或 ≥1024 面。shell 模块零生产接线（grep 证据：ShellCommandExecutor 仓内仅测试引用、io.nop.ai.shell 无任何 main 消费方、无 beans 注册、内建命令 cd/echo/ls 单次输出 ≤1 chunk）——按零生产接线先例条件化降档（WI 3.1 C1-005/C5-022 口径）；族案（成员 C8-001/C8-003/C4-003 REE 收编）单 diff 修复范围不变，7.1 若维持族案 P1 则本条随族案回 P1

### [C6-005] BlockingQueueShellOutput.close()/BlockingQueueShellInput.sendEof() 用非阻塞 offer 投递 EOF：队列满时 EOF 静默丢失，下游 take() 永久阻塞

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:42-47`；同族 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellInput.java:50-55`
- **证据片段**:
  ```java
  @Override
  public void close() {
      if (!closed) {
          closed = true;
          queue.offer(ShellChunk.eof());   // 队列满 1024 时返回 false —— EOF 无声消失
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: 生产者线程（命令实现/上游段）写满队列后，另一线程（命令包装层 finally / 管道段收尾）调用 close；消费者线程随后在 `take()` 上等待 EOF。
- **触发时序**: T1 = 生产者恰好写满 1024 chunk（每 chunk = 一次 write/println，`IShellOutput.java:13-18`）后正常返回。T2 = 收尾方调用 close()，此刻队列仍满（消费者尚未 drain），`offer(eof)` 静默失败。T3 = 消费者 `readAllText()`/`read()`（`AbstractShellInput.java:65-77`）drain 完 1024 chunk 后在 `take()` 永久阻塞——EOF 标志已不可达。管道场景与 C6-004 叠加：中段 close 时机先于下游 drain 完成，窗口常态存在。
- **现状**: close 的语义是"尽力投递 EOF"而非"保证 EOF 可达"；`closed` 标志只防重复 close，不保证 EOF 入队。
- **风险**: 与 C6-004 同级后果：读取方线程永久阻塞、Future 永不完成；且该失败模式无声（无日志无异常），故障现场只剩一个停在 `take()` 的线程。
- **建议**: close 改为循环 `while (!queue.offer(eof, 100ms))` 带总时限，或先 drain-to-EOF 语义（close 阻塞至 eof 入队成功）；最低限度 offer 失败时记 WARN。同族 `BlockingQueueShellInput.sendEof`（L50-55）同型同修。
- **失效面枚举**: ① `BlockingQueueShellOutput.close()`（L42-47）— 缺陷本体；② `BlockingQueueShellInput.sendEof()`（L50-55）— 同型（公共喂入 API，外部 feeder 在下游消费慢时同样丢 EOF）；③ `FileShellOutput.close()`（`FileShellOutput.java:43-48`）— flush 落盘路径，无队列，通过；④ `DuplexShellOutput.close()` — 委托 owned target，无队列，通过；⑤ `TeeOutput.close()` — 顺序 safeClose ownedOutputs，无 EOF 语义，通过。
- **信心水平**: 确定
- **误报排除**: 非原语误报——`offer` 单点看合法，缺陷在 close 契约与消费者阻塞读的组合；非有界声明即合规——上限与 EOF 投递失败组合成死锁（校准条款矛盾项）。
- **复核状态**: 降级(P2，条件化：投产即回 P1)｜新发现——回码核验成立（close/sendEof 非阻塞 offer 丢 EOF、AbstractShellInput.readAllText drain 后 take 永久阻塞，L42-47/L50-55 属实）；同案 C8-002 同站点同证据，主登记择一留合并裁定、两案须同批同判级；shell 零生产接线同 C6-004 条件化降档；与 C4-003 REE 提交中止为不同机制面确认成立（本条为 EOF 信号丢失，且与 C6-004 组合出调用方挂死形态）

### [C6-006] ToolManagerImpl.pathQueues 注册表只增不删：每条曾出现过的路径 key 永久驻留（F4 无界账本）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:32-33,101-121`
- **证据片段**:
  ```java
  private final ConcurrentHashMap<String, CompletableFuture<Void>> pathQueues =
          new ConcurrentHashMap<>();          // L32-33：全文件唯一写点在 L105 compute
  ...
  pathQueues.compute(pathKey, (k, tail) -> { ... });   // L105：put/替换，无任何 remove/evict
  ```
- **严重程度**: P2
- **并发场景**: dispatcher 并行 fan-out 多线程调用 `callTool`（L94-99 注释自证并发入口）；map 为并发容器，key 集合单调增长。
- **触发时序**: T1 = 会话 A 对 `/proj/a.java` 写操作入队，key `…/a.java` 进入 pathQueues。T2 = 任务完成后 tail 被替换为新 future，但 key 永不移除（全文件 grep 仅 L105 一处访问）。T3 = 长驻进程中每条曾被"非并发安全工具 + 可提取路径"调用触碰过的路径字符串永久驻留——进程生命周期内无清理/TTL/归档路径。
- **现状**: value 链旧 future 可被 GC，但 key（路径字符串 + 空 chain 头）永不回收；manager 为单例 bean（NopIoC 无并发守卫约定下的跨请求共享状态）。
- **风险**: 增速=会话中出现的不同文件路径数；对代码仓库级 agent 会话（数千文件 × 长驻服务周/月）累积至 MB 级驻留 + hashCode 桶退化。量级有限故 P2，但属 F4 实证族（只增不删账本）。
- **建议**: 任务完成后尾值为已完成 future 时 `pathQueues.remove(pathKey, tail)`（compute 内判断 tail.isDone() 时移除，需防 ABA：仅当 map 中仍是该 tail 时删）；或换 Guava-style 带弱引用/最大容量 Cache（平台 `io.nop.commons.cache.ICache/CacheConfig`）。
- **失效面枚举**: ① `ToolManagerImpl.pathQueues` — 缺陷本体；② `BashExecutionTracker.inFlight`（`BashExecutionTracker.java:36`）— register/unregister 在 execute finally 配对，通过；③ `DefaultToolExecutorProvider.executors`（`DefaultToolExecutorProvider.java:23,51`）— 启动期注册、规模=工具数有界，通过；④ `ShellCommandRegistry.commands/aliases` — 启动期注册有界，通过。
- **信心水平**: 确定
- **误报排除**: 非"有界声明缓存"——无 CacheConfig、无容量上限、无淘汰；非启动期低频路径——运行期每次非并发安全工具调用都经过。
- **复核状态**: 保留(P2)｜新发现——回码核验成立（pathQueues 全文件唯一写点 L105 compute、无任何 remove/evict 访问；ToolManagerImpl 为生产接线 bean，ai-toolkit-defaults.beans.xml L65）；F4 实证族维持 P2（增速=路径基数、单条目小）；三登记（C6-006↔C4-CL-005↔C8-006）同站点同事实确认，去重留合并裁定（建议 C8-006 主登记：含过载语义/容量护栏/指标面最全，本条保留为 F4 生命周期视角成员）；与 WI 3.1 C5-015(MA-03) F4 引擎终态清理族同族不同站点，台账关联不并案

### [C6-007] ThoughtStorage 会话文件无删除路径：clearHistory 只覆写空内容、headerless 请求铸造一次性 UUID 会话 → 存储目录文件数无界增长（F4）

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java:97-146,214-234`；铸造点 `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/utils/AiToolsHelper.java:27-33`
- **证据片段**:
  ```java
  // ThoughtStorage：全文件无任何 Files.delete(sessionFile) / 目录清扫
  public void clearHistory(String sessionId) {
      lock.lock();
      try { saveSession(sessionId, new ArrayList<>()); }   // 只覆写为空数组，文件保留
      finally { lock.unlock(); }
  }
  // AiToolsHelper.makeChatSessionId：无 session 头时每次请求生成新 UUID
  if (StringHelper.isEmpty(sessionId)) {
      sessionId = StringHelper.generateUUID();
  }
  ```
- **严重程度**: P2
- **并发场景**: ReentrantLock 单锁串行化全部会话操作（设计裁定允许）；此处问题非竞态而是文件生命周期无终止路径；多请求并发时 headerless 调用各自铸造不同 UUID。
- **触发时序**: T1 = 客户端缺失 `nop-chat-session-Id` 头调用 processThought → `makeChatSessionId` 生成一次性 UUID → `addThought` 落盘 `<uuid>.json`。T2 = 该 UUID 永不复用，客户端也不会对其调用 clearHistory。T3 = 每个此类请求在 `./_tmp/ai/sequential-thinking/store`（或 `~/.mcp_sequential_thinking`）留下一个永不删除的文件；正常会话结束后文件同样无回收路径（clearHistory 仅清内容）。
- **现状**: 存储目录文件数 = 历史出现的会话 id 总数，单调递增；设计文档声明数据"ephemeral"但无任何 ephemeral 回收机制。
- **风险**: 长驻公网服务（该 BizModel 暴露为 @BizQuery/@BizMutation 服务）可被无头请求刷出任意多文件 → inode/磁盘慢耗尽 + 目录 listing 退化。单文件小（KB 级），故 P2；但触发无需认证放大（依赖 @Auth 权限配置）。
- **建议**: (1) clearHistory 改为删除文件（`Files.deleteIfExists`）；(2) 增加启动时/定期清扫（按 lastModified TTL；可用平台 `GlobalExecutors` 定时器或声明 TTL 配置项）；(3) headerless 请求建议拒绝或用请求级内存存储，不落盘一次性 UUID 会话。
- **失效面枚举**: ① `ThoughtStorage.addThought/saveSession` 文件创建 — 缺陷本体；② `ThoughtStorage.clearHistory`（L139-146）— 唯一清理 API 但不删文件，同修点；③ `ThoughtStorage.exportSession`（L148-160）— 导出副本同样无清理，登记；④ `atomicWriteText` 临时文件（L214-234）— finally deleteIfExists 配对，通过；⑤ `LocalToolFileSystem.atomicReplace`（`LocalToolFileSystem.java:191-211`）— 同型 finally 清理，通过。
- **信心水平**: 确定
- **误报排除**: 非已声明边界——设计裁定声明的是"file persistence per-JVM / 数据量小"，未声明无回收；多实例共享路径限制有文档声明，与本发现（单实例内无界增长）正交。
- **复核状态**: 保留(P2)｜新发现——回码核验成立（clearHistory 只覆写空数组、全文件无任何会话文件删除（唯一 deleteIfExists 为 temp 文件 L229）、AiToolsHelper.makeChatSessionId headerless 每请求铸新 UUID；SequentialThinkingBizModel @BizModel + ai-tools-defaults.beans.xml 生产接线，无启动期降档问题）；P2 校准与 WI 3.1 MA-21 聊天日志保留期族（P2）一致，同族不同站点台账关联；headerless 放大面依赖端点认证暴露配置，留 7.1

### [C6-008] 沙箱收尾把"活" StringBuilder 发布进 capturedRef 后才非确定性 join 读者 → 主线程 toString 与读者 append 竞态，快照撕裂/偶发 AIOOBE（F1）

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java:178-186,188-198`；同型 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:309-319`；消费点 `BashExecutionTracker.java:63-75`
- **证据片段**:
  ```java
  // drainStream finally：周期性发布的是副本（L173/L306），最后一次发布的是活对象
  } finally {
      capturedRef.set(captured);          // captured 仍可能被未终止的读者线程继续 append
  }
  // awaitReader 仅 join 有界宽限 + 无效 interrupt（见 C6-001），返回时读者可能仍存活
  // capturedToString 直接 toString() 活对象：
  StringBuilder sb = ref.get();
  return sb != null ? sb.toString() : "";
  ```
- **严重程度**: P1
- **并发场景**: daemon 读者线程（append）与调用线程（`capturedToString` → `toString()`）以及心跳线程（`progressSnapshot` → `toString()`，经 tracker 读取同一 capturedRef）三方共享同一可变 StringBuilder。
- **触发时序**: T1 = 命令含 `&` 孪生子（或输出超过宽限期内未能排干），`awaitReader` 宽限 0.5–2s 耗尽，读者仍阻塞在 read/仍在 append（interrupt 无效，见 C6-001）。T2 = 调用线程进入 `capturedToString`，对活 StringBuilder 调 `toString()`；同一时刻读者 `captured.append(...)` 扩容。T3 = 交叠后果：`StringBuilder.toString()` 内部 `new String(value,0,count)` 与扩容交叠可抛 `ArrayIndexOutOfBoundsException`（工具调用崩溃）或产生撕裂字符（截断 UTF-16 代理对/半行输出，输出静默损坏）。次生面：T2' = 心跳线程在 finally 发布之后、unregister 之前调用 `progressSnapshot`（`BashExecutionTracker.java:68-74`）读取同一活对象，同样竞态。
- **现状**: 周期性进度发布已用副本（修复了历史缺陷），但终止语义未闭环：join 不保证读者停止，最后发布却退回活引用。
- **风险**: 触发频率随"后台子进程型命令"出现频率线性增长（agent 场景常见）；失败形态为工具调用异常或输出静默截断——对模型可见的错误结果，无资源后果但破坏输出契约。
- **建议**: (1) finally 发布改为不可变副本 `capturedRef.set(new StringBuilder(captured))` 或直接 `String` 快照；(2) capturedToString/progressSnapshot 改读不可变快照类型；(3) 与 C6-001 一并修：读者终止（关流）后再发布终态。
- **失效面枚举**: ① `HostBashSandbox.drainStream/finally`（L178-180）— 缺陷本体；② `DockerBashSandbox.drainStream/finally`（L309-313）— 同型同修；③ `BashExecutionTracker.progressSnapshot`（L68-74）— 消费面，副本化后自动安全；④ 两个 sandbox 的 `capturedToString`（L183-186 / L316-319）— 消费面，同上；⑤ `BashProgressSnapshot` 值对象 — 不可变，通过。
- **信心水平**: 确定
- **误报排除**: 非理论竞态降级项——T1 可由具体命令模式（`server &`）确定性构造（与 C6-001 同一触发链）；非"看到共享字段就报线程不安全"——已写出具体交错与两种后果形态。
- **复核状态**: 保留(P1)｜新发现——回码核验成立（finally 发布活对象 L179/L312、capturedToString 对活对象 toString、周期副本仅覆盖运行期；join 宽限耗尽后读者可仍存活且在 append，String(value,0,count) 与扩容交叠 → StringIndexOutOfBounds/UTF-16 撕裂技术核实成立）；触发面细化：窗口需读者仍存活且在写（`&` 孪生持续产出或排空超宽限），运行中心跳读副本安全、仅终态发布后窗口受影响——与 C6-001 同触发链同 diff 修，快照撕裂属校准 P1 项，维持；沙箱 opt-in 判级口径同 C6-001

## 维度集外线索（overlay C2/C3/C5/C7/C9）

### [CL-009] [C2] ShellCommandExecutor 的 exportedEnv/currentWorkingDir 为跨请求共享可变状态，异步 whenComplete 恢复写与并发 execute 读无任何守卫

- 位置：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:45-47,318-348,559-585`。
- `executeGroup`/`executeSubshell` 在 `whenComplete` 回调（运行于完成线程，可能是 `GlobalExecutors.globalWorker()` 任意线程）中写回 `this.exportedEnv`/`this.currentWorkingDir`。
- `exportedEnv` 是普通 `HashMap`，`buildEnvironment`（L545-557）与 `updateContextFromResult`（L559-567）在同会话并发 `execute()` 调用下无锁读写。
- `getExportedEnv`（L643-645）返回活 map 的 unmodifiable 视图，CheckVisitor（L110）迭代期间他人写入 → `ConcurrentModificationException`/脏读；组/scope 快照-恢复在并发下互相覆盖（A 的恢复抹掉 B 的 export）。
- 建议：按会话绑定 executor 实例，或 env/dir 状态改为 AtomicReference + 不可变 Map 快照交换。供 C2 维度复核。

### [CL-010] [C5] FileShellInput 构造期无上界全量读入堆：`< 大文件` 重定向一次载入整个文件为 String

- 位置：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/FileShellInput.java:12-16`。
- 构造器 `fileSystem.readText(filePath, 0)`（maxChars=0 = 无上限，对照 `LocalToolFileSystem.readText` L108 的截断语义）在构造时同步读整个文件进 `content` 字段。
- `handleInputRedirect`（ShellCommandExecutor.java:491-495）对每个输入重定向新建实例 → `cmd < huge.log` 即全量驻留堆。
- 对照 C5b 基线 full-read 口径，属未声明包络的无界读。
- 建议：maxChars 传有界值或改惰性按行读取。供 C5 维度复核。

### [CL-011] [C9] ShellCommandExecutor.close() 写入的 closed 标志无人校验：close 后 execute 照常执行，Closeable 契约空转

- 位置：`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:49,635-641`。
- `close()` 仅 `closed = true`（L640），全文件无任何读取点；close 后 `execute()`/`setBackgroundJobSink()` 照常受理新命令。
- `&` 后台任务归 sink 所有是已声明设计（L51-56 注释），但 executor 自身的 close 语义为空——生命周期 API 与实际行为脱节。
- 集成方按 `Closeable` 契约调用 close 后仍会继续派发命令，无任何 fail-fast 信号。
- 建议：execute 入口校验 closed 抛显式异常，或删除 Closeable 声明以免误导。供 C9 维度复核。

### [CL-012] [C9] JqToolExecutor 取消监听注册窗口：submit 与 appendOnCancel 之间触发的 cancel 事件丢失中断

- 位置：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/JqToolExecutor.java:37-48`。
- `context.getExecutor().submit(...)` 先入队，随后才 `cancelToken.appendOnCancel(onCancel)`；两步之间的窗口内触发的取消事件不会被监听器看到。
- 错过事件时 `future.cancel(true)` 永不执行，jq 计算只能靠 `expireAt` 检查点自然终止；无 expireAt 的调用路径计算将跑满全程。
- `removeOnCancel` 在 whenComplete 中配对正确，无监听器泄漏。
- 建议：注册前先同步检查 `cancelToken.isCancelled()` 补触发一次。供 C9 维度复核。

### [CL-013] [C2/C5] ToolManagerImpl.submitBounded 在 dispatcher 线程上阻塞 acquire：maxConcurrency 排队钉住派发线程

- 位置：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:232-246`。
- `semaphore.acquire()` 无超时阻塞调用方线程；`executeParallel` 在单线程循环内逐个 submit，第 N+1 个调用在 acquire 上挂起直至前面的在途调用完成。
- 并行 fan-out 的派发延迟被最慢在途调用支配；派发线程被钉住期间无法处理其他会话/心跳（取决于 dispatcher 池形态）。
- 信号量本身 acquire/release 配对正确（interrupt 分支不持许可），非泄漏面，纯调度形态问题。
- 建议：`tryAcquire(timeout)` + 队列化，或改用平台异步限流设施（`IRateLimiter`/`HighWatermarkSemaphore`）。供 C2/C5 维度复核。

---
（本维度审计完成：发现 8 条（P1×6 / P2×2），overlay 线索 5 条（CL-009…CL-013）。无 P0/P3。）

## 维度复核结论

- 复核人：C6 维度独立复核员（WI 3.2，2026-10-20）
- 方法：9 条发现（C6-001…008 本文件 + C6-009 见 C6-round2.md）逐条回码验证（HostBashSandbox/DockerBashSandbox/BashExecutionTracker/ShellCommandExecutor/BlockingQueueShellOutput/Input/AbstractShellInput/ToolManagerImpl/ThoughtStorage/AiToolsHelper 逐行核对）；查重比对 `ai-dev/audits/cra-index.md` WI 1.1–3.1 P1/P2 台账 + WI 3.1 MA-17 沙箱族；降档口径核对（泄漏与残留形态不适用启动期降档——初审全部按运行期路径判级，无违例；休眠面按零生产接线先例条件化降档——shell 两条适用，grep 证据在案）。

### 逐条判定表

| 发现 | 初审判级 | 判定 | 最终严重度 | 核心依据 | 同案/去向 |
|---|---|---|---|---|---|
| C6-001 | P1 | 保留 | P1 | 回码成立；C4-001 同站点并案主登记 | 主登记（成员 C4-001） |
| C6-002 | P1 | 保留 | P1 | 回码成立；超时路径残留 + 取消句柄注销 | 主登记（建议收编 C8-009 主面） |
| C6-003 | P1 | 保留 | P1 | 回码成立；F5 不可见挂死 | 主登记（成员 C4-002；C8-009 次面归此） |
| C6-004 | P1 | 降级 | P2（条件化） | shell 零生产接线（grep 证据）+ 主面 T3 时序修正 | 族案主登记（成员 C8-001/C8-003/C4-003） |
| C6-005 | P1 | 降级 | P2（条件化） | shell 零生产接线；同案 C8-002 | 与 C8-002 择一主登记 |
| C6-006 | P2 | 保留 | P2 | 回码成立；F4 实证族；生产接线 | 三登记去重（建议 C8-006 主登记） |
| C6-007 | P2 | 保留 | P2 | 回码成立；@BizModel 生产接线 | 独立（MA-21 关联） |
| C6-008 | P1 | 保留 | P1 | 竞态回码成立（活引用终态发布） | 与 C6-001 同 diff |
| C6-009 | P2 | 保留 | P2 | 结构事实成立；一处口径修正 | 独立（与 WI 3.1 C3-011 同链两面） |

**判定统计**：保留 7 / 降级 2 / 驳回 0。**最终保留严重度分布**：P1×4（C6-001/002/003/008）+ P2×5（C6-004/005/006/007/009，其中 C6-004/005 为条件化 P2——投产/接通即回 P1）。**现状标注分布**：新发现 9（其中 C6-001 为已修站点残余缺口面，站点有 plan 2285/WI4 修复史）；已知未修复 0；已修复 0。**查重结论**：cra-index WI 1.1–3.1 台账逐条比对零同案命中（最近邻为家族关联，见下）。

### 跨维指针核对意见（C6 侧）

1. **C6-001↔C4-001 同站点**：同意并案，C6-001 主登记（面集更全：fd + stdin 管道 + 取消路径不关流），P1 校准维持——C4 复核升档论证（`server &` 型高频真实负载 + 每次一线程一 fd + WI 2.2 C6-001/WI 3.1 C4-010 判例）采信；C4 侧独有贡献（同名线程不可观测）随同 diff。
2. **C6-004 族案**：同意收编 C4-003 REE 提交中止面——同根（stage 弃管后无取消/关闭向上游传播），单 diff 覆盖 REE/中途失败/早退三面成立；确认 **C6-005 EOF offer 丢失为不同机制面**（EOF 信号投递 vs 提交中止），但两案组合才产生"调用方永久挂起"形态，修复须同批核对；C8-001（先生产后收集）/C8-003（下游死亡上游阻塞）为同根兄弟入口随族案。
3. **C6-003↔C4-002 + C8-009**：C6-003 主登记挂死面成立；归属细化——C8-009 主面（超时分支本地 CLI 进程 + reader 滞留）与 C6-002 的 CLI leg 同面，建议 **C8-009 并入 C6-002 为主**、其 runShortCommand waitFor 面归 C6-003；C4-002 心跳线程同步占用面（C4 复核保留 P2）并入 C6-003 工单同 diff。
4. **F4 pathQueues 三登记（C6-006↔C4-CL-005↔C8-006）**：三处同站点同事实确认；建议 C8-006 主登记（过载语义 + 容量护栏 + 指标面最全），C6-006 关闭为成员或保留 F4 视角，由合并裁定终裁。**exportedEnv 三登记（C6-CL-009↔C4-CL-006↔C8-CL-016）**：C6 侧 CL-009 面最全（whenComplete 写回点 + 裸 HashMap 裸读裸写 + getExportedEnv 活 map 逃逸 CME + 组/scope 快照互踩），建议其为主登记候选，供 C2 维度复核采纳。
5. **WI 3.1 MA-17 沙箱族同族对照（两套沙箱）**：nop-ai-agent DockerSandboxBackend（MA-17，族判 P2）与本单元 Host/DockerBashSandbox（后者 javadoc 自认复用前者 pattern）同病——超时不 destroy 本地进程/interrupt 对管道 read 无效/kill 失败线程滞留；两实现仓内同为零生产实例化（grep：`new DockerSandboxBackend` 仅测试；本单元沙箱 bean 仅注释配置、缺省 fail-closed）。**判级分歧（P2 vs 本单元 P1）登记 7.1 统一**；零重复立项（不同类不同模块），建议修复批次协同、护栏测试（3.1 C10-011）共享形态。
6. **F4 无界账本族对照**：C6-006/C6-007 与 WI 1.2 MA-03（2PC 台账）、WI 1.6 C7-001（retry 三表）、WI 1.7 C7-003（batch 三表）、WI 3.1 C5-015(MA-03)/MA-21 同族不同站点——台账关联，零重复立项，修复批次可对齐保留策略方案。

### 高风险条目子项复核说明

- **C6-004 主面时序修正**：初判 T3 主张"b 即抛 → c 因 EOF 永不到来阻塞 → 调用方永久挂起"不成立于纯本面——b 的 finally close（safeClose(b.out)）在 queueB 非满时 offer(eof) 成功，c 正常终止；纯本面后果为上游段 worker 线程永久钉死 + 段异常无观察 + F2 半个成功。"调用方永不返回"需组合 C6-005（b 死时 queueB 恰满 → EOF 丢失）或第三面（≥1024 自锁）。三个子面本身与修复建议 (1)–(5) 全部维持；降级决策另据零生产接线事实（与该修正独立）。
- **C6-009 口径修正**：L89-96 为 catch(RuntimeException) 后 **rethrow**（注释明示 "must surface its failure"），cancelAction 失败是响亮的——"静默失效"表述过强，缺陷本体是 **remove-before-act 使任何半途失败不可重试**（注册项已摘除，后续 cancel 恒 false）+ L87-88 无 killTree 同款守卫。守卫可达性证据（同文件 killTree L217 防御性 catch）采信，P2 维持。
- **C6-008 触发面细化**：竞态窗口需读者线程仍存活且在 append（`&` 孪生持续产出，或宽限期内未排完缓冲）；运行期心跳经周期副本读取安全，受影响面为 finally 活发布后的终态窗口（capturedToString + 心跳窄窗）；后果形态（SIOOBE/代理对撕裂）技术核实成立。
- **C6-001 stdin 面核实**：`pb.start()` 默认 stdin=PIPE 且从不关闭，`sh -c "cat"` 类仅能靠 wall 超时回收——并入本条修复面（redirectInput NUL 设备）。

## 最终保留项

- **P1×4**：C6-001（主登记，成员 C4-001）、C6-002（主登记，建议收编 C8-009 主面）、C6-003（主登记，成员 C4-002 + C8-009 次面）、C6-008（与 C6-001 同 diff）。
- **P2×5**：C6-004（条件化：投产/真实适配器接通即回 P1；族案主登记，成员 C8-001/C8-003/C4-003）、C6-005（条件化；与 C8-002 择一主登记、同批同判级）、C6-006（三登记去重留合并）、C6-007（独立）、C6-009（独立）。
- **判级一致性提示（7.1 终裁输入）**：① 沙箱族本单元 P1 vs WI 3.1 MA-17 P2；② shell 管道族本复核条件化 P2 vs C4/C8 侧初裁 P1——两组分歧建议按"装配后生产形态"统一裁决；无论判级走向，单 diff 修复范围与族案成员关系不受影响。
