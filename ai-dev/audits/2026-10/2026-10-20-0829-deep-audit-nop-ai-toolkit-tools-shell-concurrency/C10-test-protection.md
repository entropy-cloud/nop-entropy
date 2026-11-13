# C10 测试保护力 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计人：C10 测试保护力维度（最后批次，单轮）
- 日期：2026-10-20
- 评估对象：本单元四维度（C1/C4/C6/C8）`## 最终保留项` 全部 39 条保留发现（P0×0 / P1×9 / P2×28 / P3×2）
- 代码基线：与 C1–C8 复核同一 live code（源码于主工作区 worktree 读取，行号一致）

## 审计范围与口径

**范围**：三模块 `src/test/java`（nop-ai-toolkit 43 文件、nop-ai-tools 9 文件、nop-ai-shell 15 文件）+ 跨模块测试触达（nop-ai-agent 测试源对 io.nop.ai.shell 的 2 处 import：`BackgroundRegistryJobSink` / `TestBackgroundRegistryJobSink`，仅登记不作为本单元保护力）。逐条对 39 条保留发现做变异推演。

**判定口径（变异方向声明）**：39 条保留发现全部为「新发现、当前未修复、CI 绿」。因此「会红」不可能以"测试现在就是红的"形式存在；本维度采用标准变异推演：**假设任一发现按建议修复后，其缺陷语义被作为回归重新引入（mutation），现有哪个测试会红？** 据此三档判定：

- **会红·有保护**：存在测试其断言语义与缺陷直接对立、且触发时序在测试装配下可达——缺陷回归即红（含超时红）。
- **假保护**：有测试触达同一类/同一方法接缝，但断言的不是缺陷语义，或测试装配（小输出、单线程、stub 不抛异常、assumeTrue 门控）使缺陷触发时序不可达——缺陷回归时全绿，覆盖是表面覆盖。
- **无测试**：缺陷触发路径在 src/test 无任何触达（每条给出至少一种检索路径），修复回退不会使任何测试变色。

**检索纪律执行记录**：全仓 grep 关键词及命中数——`registerTool` 三模块测试 0 命中；`killContainer|runShortCommand` 0 命中；`pathQueues` 0 命中；`ListShellInput|FileShellInput` 0 个测试文件；`cancel(true)` 仅 2 文件（JqToolExecutorTest、RecordingBackgroundJobSink）；`1024` 于 nop-ai-shell 测试 0 命中（唯一命中在 BashSandboxTest 的 helper 缓冲区，与队列容量无关）；`RejectedExecution` 三模块测试 0 命中；`completeExceptionally` 仅 1 处（JqToolExecutorTest:210，测试 stub 自身，非被测语义）。

**测试盘点（并发相关）**：三模块中真正的多线程/latch 驱动测试仅 4 个文件——`TestToolPathQueue`（4 测试，队列串行化机制）、`ToolManagerImplParallelTest`（5 测试，maxConcurrency 机制）、`ShellConcurrencyEdgeCaseTest`（管道并发/后台 job/EOF 传播）、`TestBashExecutionProgress`（进度快照+取消）。其余全部为单线程功能测试。

---

## 保护力矩阵

| 发现 ID | 所在模块 | 现有测试覆盖判定 | 证据（测试文件:line 或检索说明） |
|---|---|---|---|
| C1-001 | nop-ai-toolkit | 无测试 | `TestToolPathQueue.java:119-228` 4 测试全部经 provider 桩（executeAsync 从不同步抛出、自带异步池），未复现锁内执行/重入/sync-throw 任一子面；检索：`callTool` 测试触达仅 3 文件，`RejectedExecution`=0、`completeExceptionally` 断言=0、无 null-executor/REE 注入测试 |
| C1-002 | nop-ai-shell | 假保护 | `ShellCommandExecutorTest.java:635-681`（group/subshell 环境还原）、`ShellConcurrencyEdgeCaseTest.java:565-575`（双实例隔离）触达 exportedEnv/cwd 字段，但全部单线程；grep `exportedEnv` + Thread 并发驱动 = 0 |
| C1-003 | nop-ai-shell | 假保护 | `ShellIOTest.java:294-300` 恰好构造了"队满时 close"（容量 1，write 后 close），但只断言 write-after-close 抛异常，不断言 EOF 可达；全部 read 类测试（:190-260）close 时队列非满 |
| C1-004 | nop-ai-toolkit | 假保护 | `UpdateTodosExecutorTest.java:116-127`（clear）、`:132-167`（跨会话隔离）、`:170-202`（写空仅清本会话）全单线程；文件内 grep `concurrent/Parallel/Thread` = 0 |
| C1-005 | nop-ai-toolkit | 假保护 | `DeltaVirtualFileSystemTest.java:109-142`（testWriteToDelta/testCopyToVirtual 功能断言，无撕裂读）；扩展面 `TestAiFileTool.java:75-103` 断言写成功非原子性；已修兄弟有 `TestLocalToolFileSystemAtomicWrite.java:37-97` 但不覆盖 DeltaVFS/DslToolImpl/AiFileTool；并发读-写测试 = 0 |
| C1-006 | 跨两模块 | 假保护 | `ShellCommandRegistryTest.java:13-112` 单线程 register/findCommand；DefaultToolExecutorProvider 侧检索 `registerTool` 三模块测试 = 0 命中；无并发 register+read 测试 |
| C1-007 | nop-ai-toolkit | 假保护 | `TestToolPathQueue.java:203-228` 钉死的是 JSON 形态 pathless→不排队；XML 形态（fromNode + path 子节点）的串行化回归测试不存在；`ToolManagerImplParallelTest.java:107-121` 的 fromNode 调用无 path 子节点，不触达双源提取；`ToolPathAffinity` 测试直接引用 = 0 |
| C1-008 | nop-ai-shell | 假保护 | `ShellCommandExecutorTest.java:382-397`（close 幂等/close 后不再跟踪 job）触达 close()；execute()-after-close 的守卫断言 = 0（两处 close 调用 :631/:741 均为测试收尾） |
| C1-009 | nop-ai-shell | 无测试 | 检索：`ListShellInput` 在三模块测试 0 个文件引用；`readIndex` 行为（并发消费同一 stdin）无任何测试；FileShellInput 同样 0 测试文件 |
| C1-010 | nop-ai-tools | 假保护 | `TestSequentialThinkingBizModel.java:64+`（成功路径持久化）、`TestThoughtStorage.java:156-168`（clearHistory 清空）单线程触达 add/getAll/clear 三方法；并发 clear-during-add 测试 = 0 |
| C4-001 | nop-ai-toolkit | 无测试 | `BashSandboxTest.java:41-77` 仅 `sh -c "sleep 30"`（killTree 已修面）与 echo 捕获；`server &` 孪生持管道形态 0 测试；检索 `http.server\|descendants\|isAlive\|线程数` 测试断言 = 0；`awaitReader`/drainStream 无直接测试 |
| C4-002 | nop-ai-toolkit | 无测试 | 检索 `runShortCommand` 测试 = 0 命中；Docker 真实启动测试被 assumeTrue 门控（`BashSandboxTest.java:225-281`），CI 跳过即零覆盖；daemon 半挂死故障注入 = 0 |
| C4-003 | nop-ai-shell | 无测试 | 全部测试用 `new ShellCommandExecutor(registry, fileSystem)`（默认 globalWorker，队列 10000，REE 不可达）——构造器注入小池的测试装配 = 0（grep 构造变体仅 2/4 参两种，均无 executor 参数）；多阶段仅 2-3 段小命令 |
| C4-004 | nop-ai-shell | 无测试 | `ShellConcurrencyEdgeCaseTest.java:97-151` 依赖缺省共享池断言并发性，无池隔离/饥饿测试；grep `newExecutor\|DefaultThreadPoolExecutor` 于 nop-ai-shell 测试 = 0 |
| C4-005 | nop-ai-toolkit | 无测试 | 同 C1-001 证据：`TestToolPathQueue.java` provider 桩不抛、`ToolManagerImplParallelTest.java:123-130` MockContext 池无界队列；排队调用 sync-throw→result 悬挂的注入测试 = 0 |
| C4-006 | nop-ai-shell | 假保护 | `RecordingBackgroundJobSink.java:23-27` 测试 sink 固定用独立池（test-shell-bg-fork），同池化装配在测试中结构性不可达；`ShellCommandExecutorTest.java:388-403` 仅钉 null-sink fail-loud；池身份守卫断言 = 0 |
| C4-007 | nop-ai-toolkit | 无测试 | `TestBashExecutorBackgroundMode.java:82-92` StubRegistry.run 永不抛（池内 catch 转 FAILED 是 stub 行为非被测语义）；`submit 成功→run 抛 UOE` 的回滚断言 = 0；fail-loud 测试（:221-243）在 submit 之前即失败，不触达 submit→run 间隙 |
| C4-008 | nop-ai-toolkit | 假保护 | `JqToolExecutorTest.java:33,78` 唯一断言 cancelToken→cancel(true)→检查点终止（17 分之 1 的合规兄弟）；其余 16 个 executor 的取消检查点测试 = 0；排队路径 wrapper future cancel 不传播亦无测试（grep `cancel(true)` 全测试树仅 2 文件） |
| C6-001 | nop-ai-toolkit | 无测试 | 同 C4-001 证据（孪生进程持管道 / reader 存活断言 / fd 泄漏检测钩子全缺）；`TestBashExecutionProgress.java` 只覆盖周期副本（已修面） |
| C6-002 | nop-ai-toolkit | 无测试 | 检索 `killContainer` 测试 = 0；kill 失败吞异常路径无故障注入；容器残留计数/枚举断言 = 0 |
| C6-003 | nop-ai-toolkit | 无测试 | 检索 `runShortCommand` 测试 = 0；`BashSandboxTest.java:300-326` isDockerAvailable helper 的 javadoc 原文记载了本条挂死机理并正确实现 wait-first-then-drain——知识在测试文件内，但零断言作用于生产代码 |
| C6-004 | nop-ai-shell | 假保护 | `ShellConcurrencyEdgeCaseTest.java:154-174`（中段失败）用的是退出码 1（正常完成，lambda 不抛异常）；阶段 lambda 抛异常路径 0 测试；早退分支 0 测试；≥1024 chunk 自锁面 0 测试（grep `1024` shell 测试 = 0） |
| C6-005 | nop-ai-shell | 假保护 | 同 C1-003（`ShellIOTest.java:294-300` 触达队满 close 但不断言 EOF；`sendEof` 测试 :343-373 均队非满）；BlockingQueueShellInput 队满 sendEof 丢失 = 0 测试 |
| C6-006 | nop-ai-toolkit | 无测试 | 检索 `pathQueues` 测试 = 0；无 map size/驱逐/增长断言；TestToolPathQueue 只断言串行化机制不断言生命周期 |
| C6-007 | nop-ai-tools | 假保护 | `TestThoughtStorage.java:59-71`（断言持久化=增长面）、`:156-168`（断言 clearHistory 清**内容**——恰为本条指认的不充分语义）；文件数/TTL/headerless UUID 积累断言 = 0 |
| C6-008 | nop-ai-toolkit | 无测试 | `TestBashExecutionProgress.java:46-86` 只读运行期周期副本（已修面）；终态活引用发布后的 toString/心跳竞态（AIOOBE/代理对撕裂）0 测试；孪生进程越宽限窗口 0 构造 |
| C6-009 | nop-ai-toolkit | 假保护 | `TestBashExecutionProgress.java:79`（cancel 返回 true）、`:42`（unknown→false）钉死 happy path 语义；remove-before-act 与 UOE/SecurityException 守卫缺失需故障注入，0 测试；半途失败后可重试性断言 = 0 |
| C8-001 | nop-ai-shell | 假保护 | 全部管道/单命令测试输出 ≤5 chunk（`ShellConcurrencyEdgeCaseTest.java:430-512` 等）；≥1025 chunk 确定性挂死拓扑 0 测试；类级 `@Timeout(10)`（:35）具备挂死检测能力但无测试到达可挂死路径 |
| C8-002 | nop-ai-shell | 假保护 | 同 C1-003/C6-005（队满 EOF 丢失断言 = 0；恰-1024-chunk 确定性触发链 0 测试） |
| C8-003 | nop-ai-shell | 无测试 | 下游 stage **抛异常死亡** + 上游持续产出的测试 = 0（`testPipelineFailingMiddleStage:154-174` 为退出码失败非异常死亡）；线程泄漏/put 阻塞断言 = 0 |
| C8-004 | nop-ai-shell | 无测试 | 检索：nop-ai-shell io 测试（ShellIOTest）无任何 interrupt/cancel(true) 消费者测试；adapter read() 中断→null≡EOF 语义 0 断言；对称兄弟 BlockingQueueShellInput.read 抛异常语义的中断测试亦 = 0 |
| C8-005 | nop-ai-toolkit | 假保护 | `ToolManagerImplParallelTest.java:133-196` 保护信号量**限流机制**（maxObserved≤2 即红）——但 permit 泄漏需 callTool 同步抛出，测试拦截器为空、provider 不抛，泄漏/无界 acquire 面断言 = 0 |
| C8-006 | nop-ai-toolkit | 无测试 | 同 C6-006（容量护栏/溢出策略/指标/驱逐 0 测试；`pathQueues` 测试引用 = 0） |
| C8-007 | nop-ai-toolkit | 无测试 | `HttpRequestExecutorTest`/`GraphqlQueryExecutorTest` 全部 MockHttpResponse 小响应体；大响应/截断标记/上限断言 = 0（grep `truncat\|limit\|large` 两文件 = 0） |
| C8-008 | nop-ai-toolkit | 假保护 | `SearchContentExecutorTest.java:51+`、`ReadFileExecutorTest.java:61-88`（lastLines=2）、`PatchFileExecutorTest.java:74+`（dry-run 小文件）、`SearchFilesExecutorTest.java:43+` 全部默认小参数，断言检索语义非 clamp 语义；病态参数（1e9）绕过测试 = 0；注意已修实例 ApplyDelta 的 1M/10M 帽同样无回归测试（`ApplyDeltaExecutorTest` grep cap/limit = 0） |
| C8-009 | nop-ai-toolkit | 无测试 | 同 C6-002/C6-003（Docker 路径 assumeTrue 门控 + `killContainer/runShortCommand` 0 引用 + destroyForcibly 泄漏断言 = 0） |
| C8-010 | nop-ai-shell | 假保护 | `ShellCommandExecutorTest.java:176-208`（重定向到文件/追加，小内容内容断言）、`ShellIOTest.java:383-416`（FileShellOutput 覆写/追加）触达 write/flush 接缝；无界累积/字节帽/分块落盘断言 = 0 |
| C8-011 | nop-ai-shell | 假保护 | 解析层被钉住：`BashSyntaxParserTest.java:660-670`（`0<&3` FD_INPUT 解析）、`BashLexerTest.java:29-31`（`<&` token）——**执行拓扑**（自引用挂死）0 测试；`0<&1` 执行期拒绝/挂死断言 = 0 |
| C8-012 | nop-ai-shell | 假保护 | `ShellIOTest.java:190-260` readLine/readAllText 全小 chunk；`ShellCommandExecutorTest.java:210-246`（input 重定向）小文件；无换行巨行/单巨 chunk/聚合帽断言 = 0；SafeLineReader 复用回归 = 0 |

**矩阵统计**：会红·有保护 **0** / 假保护 **21** / 无测试 **18**（合计 39）。
**P1 单列**：9 条 P1（C1-001、C4-005、C4-003、C4-001、C6-001、C6-002、C6-003、C6-008、C8-009）**全部为无测试**——9/9 P1 缺测试保护。

---

## 反向钉死检查

逐条排查"现有测试断言的正是缺陷语义、修复会打红须同步改写"。对 39 条全部回溯其接缝测试，结果：

**命中 1 处（条件性，针对特定修复变体）**：

1. **[反向钉-1] ShellIOTest 钉住"队满 close 不阻塞"现状——阻断 EOF 丢失修复的首选变体**
   - 位置：`nop-ai-shell/src/test/java/io/nop/ai/shell/io/ShellIOTest.java:294-300`（`testBlockingQueueShellOutputBoundedCapacityWriteThrowsAfterClose`）＋类级 `@Timeout(5)`（:21）
   - 语义：容量 1 的队列 write 1 chunk 后调用 `close()`——此刻队列**满**。测试在主线程调用 close 且无消费者线程。
   - 冲突：C1-003/C6-005/C8-002 的**首选修复变体**（close 改 `queue.put(eof)` 阻塞投递）在该测试装配下将永久阻塞 → 测试被 `@Timeout(5)` 打红。修复若采用"读侧 closed 标志兜底"或"先排空后关闭"变体则不受影响。
   - 处置要求：该修复工单必须**同批改写本测试**（为 close 提供消费者线程或改用兜底变体后补 EOF 可达断言），否则修复即红。这是本单元唯一一处"测试与缺陷语义共生于同一小输出装配"的实证。

**排查为否（要点列举，均已逐一核对修复不破坏现状断言）**：

- `TestBashExecutionProgress.java:42`（unknown cancelId→false）与 `:79`（cancel→true）：C6-009 修复（act-then-remove + 守卫）不改变两断言 → 绿。
- `TestToolPathQueue.java:203-228`（pathless/malformed→不排队）：C1-007 修复为节点双源提取，本测试的调用节点同样无 path → 绿；但该测试把"无 path→不排队"标注为 documented degradation，属**watch 级邻接钉**——若未来将 pathless 调用改为入队或 loud-fail，此测试须同步改写。
- `ShellConcurrencyEdgeCaseTest.java:154-174`（中段退出码 1→末级退出码语义）：C6-004/C8-003 修复针对 lambda 抛异常/EOF 丢失，不改退出码传播 → 绿。
- `ShellCommandExecutorTest.java:388-403`（无 sink fail-loud）：C4-006 修复（池身份守卫）不改 null-sink 行为 → 绿。
- `TestThoughtStorage.java:156-168`（clearHistory 清空内容）：C6-007 修复变体"删文件"后 getAllThoughts 仍返回空 → 绿；"headerless 拒绝落盘"变体不触达本测试 → 绿。
- `BashSyntaxParserTest.java:660-670` / `BashLexerTest.java:29-31`（FD_INPUT 解析）：C8-011 修复为执行期拒绝，解析层不变 → 绿。
- `TestBashExecutorBackgroundMode.java:221-243`（fail-loud/fail-closed）：C4-007 修复（submit 后回滚）不改变 submit 前失败语义 → 绿。
- `BashSandboxTest.java:41-59`（墙钟超时 <15s）：C6-002/C6-003 修复（增加 destroyForcibly/限时 drain）只会更快 → 绿。
- shell 管道类全部绿测试在 C8-001 修复（收集并行化）后语义不变（echo 级输出） → 绿。

---

## 缺保护登记

> 全部 39 条均为假保护或无测试（会红=0），按附录 A 格式逐面登记；合并粒度对照各维度复核的主登记建议网（同工单/同批修/同站点归同一保护面）。缺保护条目统一 P2（C10 口径：与缺陷本体分开计数）。`触发时序`字段按 C10 语义记载"缺陷回归时现有测试的行为"。

### [C10-001] shell 管道/队列挂死与弃管族零边界测试（≥1024 拓扑 + 阶段异常弃管 + REE 提交中止）

- **文件**: `nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellConcurrencyEdgeCaseTest.java:35,86-174,430-512`（现状测试）；缺口对象 `nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:243-286`
- **证据片段**:
  ```java
  @Timeout(10)   // 具备挂死检测能力，但下述全部测试输出 ≤5 chunk、阶段不抛异常
  ExecutionResult result = executor.execute("failcmd | echo after", ...)
          .toCompletableFuture().get(5, TimeUnit.SECONDS);
  assertEquals(0, result.exitCode(), "Last stage exit code should be used");
  ```
- **严重程度**: P2
- **并发场景**: 管道阶段线程与收集回调线程（生产者-消费者）；覆盖成员 C6-004（主）、C8-001、C8-003、C4-003、C8-011。
- **触发时序**: 缺陷回归（如收集并行化被回退/阶段异常弃管回归）时：现有测试输出 ≤5 chunk、阶段 lambda 从不抛异常、executor 从不注入小池——三重装配使挂死不可达，全部测试保持绿。
- **现状**: `1024` 于 shell 测试 0 命中；`new ShellCommandExecutor` 无一注入 executor 参数；无阶段异常注入。
- **风险**: 族案修复（C6-004 主登记单 diff）无回归护栏；修复被重构回退时静默回到"挂死/线程泄漏"形态。
- **建议**: (1) 以容量参数化测试注入小容量队列/小池（构造器已支持），构造 1025-chunk 确定性挂死并用 `@Timeout` 断言完成；(2) 阶段 lambda 抛异常注入（AbstractShellCommand.execute 内 throw）断言上游解锁、无线程滞留（线程数断言）；(3) REE 面用饱和池注入；(4) `0<&1` 拒绝语义测试。
- **失效面枚举**: C6-004（主面）、C8-001（先生产后收集）、C8-003（下游死亡上游阻塞）、C4-003（REE 中止）、C8-011（自引用拓扑）——5 条同族，本登记全覆盖；与 C6 复核族案成员关系一致。
- **信心水平**: 确定
- **误报排除**: 非"要求真实大规模压测"——C10 步骤 5 明确以小规模参数化模拟为主（小池/小容量即可确定性触发）。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-002] EOF offer 丢失站点：队满 EOF 可达性零断言 + 反向钉-1 改写义务

- **文件**: `nop-ai-shell/src/test/java/io/nop/ai/shell/io/ShellIOTest.java:263-300,343-373`（现状测试）；缺口对象 `BlockingQueueShellOutput.java:42-47`、`BlockingQueueShellInput.java:50-55`
- **证据片段**:
  ```java
  BlockingQueueShellOutput output = new BlockingQueueShellOutput(1);
  output.write(ShellChunk.text("a"));   // 队列满
  output.close();                        // offer(eof) 失败被忽略——测试在此之后无任何读断言
  assertThrows(NopException.class, () -> output.write(ShellChunk.text("b")));
  ```
- **严重程度**: P2
- **并发场景**: 生产/关闭线程与消费线程；覆盖成员 C6-005/C8-002（择一主登记）与 C1-003（成员）。
- **触发时序**: 缺陷回归（EOF 投递保持 offer-and-ignore）时：所有 read 类测试 close 时队列非满、EOF 正常入队，测试全绿——唯一的队满 close 测试（:294-300）在 close 后只断言 write 拒绝。
- **现状**: 队满 EOF 可达性断言 = 0；且命中反向钉-1（阻塞投递修复变体会使 :294-300 超时红）。
- **风险**: 三登记同站点（C6-005/C8-002/C1-003）合并工单修复后无防回退护栏。
- **建议**: 新增"capacity=N 写满 N chunk → close → asInput().readAllText() 必须含全部 N chunk 且正常返回"参数化测试（多容量档位）；修复若选阻塞投递变体，须同步为 :294-300 提供消费者线程（反向钉-1 义务）。
- **失效面枚举**: `BlockingQueueShellOutput.close`、`BlockingQueueShellInput.sendEof` 两站点同批；TeeOutput/DuplexShellOutput 委托链最终落在本面（C8-002 失效面⑤）随本测试间接覆盖。
- **信心水平**: 确定
- **误报排除**: 非"已有 close 测试即有覆盖"——:294-300 断言的是 write 拒绝，与 EOF 可达性正交。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-003] pathQueues 生命周期/过载面（F4 账本 + 无溢出策略）零行为断言

- **文件**: 缺口对象 `nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:32-33,101-121`；现有邻接测试 `TestToolPathQueue.java`（仅串行化机制）
- **证据片段**:
  ```java
  // 三模块测试 grep "pathQueues" = 0 命中；驱逐/容量/指标行为无任何测试触达
  private final ConcurrentHashMap<String, CompletableFuture<Void>> pathQueues = new ConcurrentHashMap<>();
  ```
- **严重程度**: P2
- **并发场景**: 多派发线程对多 pathKey 并发 callTool；覆盖成员 C8-006（主登记）、C6-006（F4 视角）。
- **触发时序**: 驱逐/护栏修复回退（remove 逻辑被删/阈值检查被绕过）时：无任何测试断言 map size、entry 驱逐、满载拒绝语义——全绿。
- **现状**: 测试只覆盖"同 path 串行、异 path 并行、safe 旁路、pathless 不排队"四个机制面；生命周期与过载面零触达。
- **风险**: F4 修复（完成尾驱逐）与容量护栏（QueueOverflowPolicy）落地后不可防回归；性能回归（驱逐误删导致串行化失效）也无护栏——两类回归方向相反，均无测试仲裁。
- **建议**: (1) 驱逐测试：N 个不同 path 各调用一次→断言内部 map 可观测大小回到基线（需加包级可见性探针或行为化：弱引用/内存断言）；(2) 护栏测试：per-path 挂起数超阈值→第 N+1 调用得显式错误结果而非无限排队；(3) 与 TestToolPathQueue 同文件补齐，防串行化语义被驱逐误伤。
- **失效面枚举**: ToolManagerImpl.pathQueues 唯一写点；同族 `UpdateTodosExecutor.todoLists`（C1-004，另登记 C10-017）。
- **信心水平**: 确定
- **误报排除**: 非把"机制已测"记为"全覆盖"——本登记对象是生命周期语义，与既有机制测试正交。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-004] ToolManagerImpl 排队链异常完成契约（sync-throw→result 永不完成）零故障注入

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/manager/TestToolPathQueue.java:49-110`（provider 桩）、`TestBackgroundCapableChokepoint.java:135-159`（SyncThreadPool 装配）；缺口对象 `ToolManagerImpl.java:101-123`
- **证据片段**:
  ```java
  // 测试桩：executeAsync 从不同步抛出——sync-throw 面（NPE/REE）结构性不可达
  public CompletableFuture<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
      startCount.incrementAndGet();
      return CompletableFuture.supplyAsync(() -> { ... }, executorService);
  }
  ```
- **严重程度**: P2
- **并发场景**: 排队调用在其前驱完成线程上执行函数体；覆盖成员 C1-001（主）、C4-005（合并单成员）。
- **触发时序**: 修复（executeNow 移出 compute + completeExceptionally 兜底）回退时：测试桩无任何同步抛出路径，`result` 悬挂语义不可达；即便可达，`second.get(5s)` 之外的断言也不检查异常完成——全绿。
- **现状**: `RejectedExecution` 测试注入 = 0；null-executor context 注入 = 0；(a) 锁内执行面（CHM bin 锁持有时长）无断言原语；(b) 重入面（同 path 嵌套派发）无测试。
- **风险**: 唯一 P1 合并单（C1-001+C4-005）修复后零防回退能力；"修复即红"风险也存在于反向——若修复引入排队语义回归，TestToolPathQueue 4 测试可兜底机制面，但异常契约面完全裸奔。
- **建议**: (1) provider 桩增加"同步抛 REE/RuntimeException"模式，断言 result 以异常完成且后续调用不受污染（队列不中毒）；(2) context.getExecutor()=null 的 Builder 缺省装配测试；(3) 锁内执行面以"两 path 同 bin 交叠计时代"或结构断言（grep 级守卫）低成本覆盖；(4) 同 path 重入（callTool→executor 内再 callTool 同 path）断言 fail-fast 而非 CHM 递归崩。
- **失效面枚举**: ToolManagerImpl.callTool 排队分支（本面）；非排队分支 L122 与 executeSequential（loud，无缺陷面，登记不修）；submitBounded（C8-005，另登记 C10-016）。
- **信心水平**: 确定
- **误报排除**: 非"已有 4 个排队测试即有保护"——现有测试全部经不抛桩，恰证明触发时序不可达（假保护/无测试判据的典型形态）。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-005] 沙箱 reader 线程/管道泄漏 + 终态快照竞态：孪生进程负载零测试

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/sandbox/BashSandboxTest.java:41-77`、`TestBashExecutionProgress.java:46-86`；缺口对象 `HostBashSandbox.java:92-95,144-198`、`DockerBashSandbox.java:107-110,277-331`
- **证据片段**:
  ```java
  // 现状最接近的测试：sleep 30 超时 → killTree（已修面），进程退出管道 EOF 正常
  .command(List.of("sh", "-c", "sleep 30"))
  assertTrue(result.isTimedOut(), "...");
  // 缺失：sh -c "<server> & echo started" 孪生持管道形态 0 测试
  ```
- **严重程度**: P2
- **并发场景**: daemon reader 线程/调用线程/心跳线程三方；覆盖成员 C6-001（主登记）、C4-001（并案成员）、C6-008（同 diff）。
- **触发时序**: 修复（关流强制 EOF/唯一命名/终态不可变快照）回退时：现有测试无孪生进程、无线程存活断言、无快照竞态构造——线程照常泄漏、capturedRef 照常发布活对象，全绿。
- **现状**: `TestBashExecutionProgress` 只保护周期副本（plan 2285 已修面）；`Thread.currentThread()`/线程数/fd 断言在 sandbox 测试 = 0。
- **风险**: C6-001 主登记工单（P1）修复后不可防回归；快照撕裂（C6-008，P1）同理。
- **建议**: (1) Host 后端（无 Docker 依赖）加孪生进程测试：`sh -c "sleep 30 & echo started"` + execute 返回后断言 reader 线程在预算内终止（`Thread` 引用可经包级探针或线程组枚举按名计数）；(2) 终态快照测试：execute 返回后并发调用 progressSnapshot/capturedToString N 次断言无异常且内容前缀稳定；(3) 线程唯一命名断言（名字含 callId）。
- **失效面枚举**: HostBashSandbox.execute/drainStream（本体）、DockerBashSandbox 同型（Docker 门控下可延后）、BashExecutionTracker.progressSnapshot（消费面）、两沙箱 capturedToString（消费面）。
- **信心水平**: 确定
- **误报排除**: 非"assumeTrue(sh) 门控已算保护"——门控只解决可移植性；本登记要求的是断言语义（线程终止/快照稳定），当前为零。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-006] Docker 清理链故障注入缺失（kill 失败吞没 / runShortCommand 挂死 / waitFor 无销毁）

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/sandbox/BashSandboxTest.java:121-216,225-281`；缺口对象 `DockerBashSandbox.java:127-156,253-275`
- **证据片段**:
  ```java
  // 唯一涉及 docker 的非门控测试是 buildDockerCommand 字符串装配断言（不启动进程）
  List<String> cmd = DockerBashSandbox.buildDockerCommand("c1", "alpine:3.19", request, config);
  assertTrue(joined.contains("--cpus 1.5"), ...);
  // killContainer/runShortCommand 测试引用 = 0；真实启动测试 assumeTrue 门控（CI 零覆盖）
  ```
- **严重程度**: P2
- **并发场景**: 超时/取消线程对挂死 docker CLI 的清理；覆盖成员 C6-002（主登记）、C6-003、C4-002（心跳占用面）、C8-009（并案成员面）。
- **触发时序**: 修复（destroyForcibly 兜底/限时 drain/失败可观测）回退时：无故障注入手段（docker CLI 悬挂/失败无法从测试注入），全绿。
- **现状**: 生产代码将 `new ProcessBuilder(...)` 硬编码在 private static 方法内，无接缝；测试 helper（isDockerAvailable:300-326）javadoc 记载了完全相同的挂死机理并实现了 wait-first 正确模式——知识在测试侧已存在，未转化为对生产代码的断言。
- **风险**: 四条成员（含两条 P1 面）修复后零防回退；该故障面（daemon 抖动）在生产必然重现而 CI 结构性盲区。
- **建议**: (1) 为 DockerBashSandbox 提供命令执行接缝（包级 `CommandRunner` 函数接口，缺省 ProcessBuilder），测试注入"悬挂 CLI/立即失败 CLI"桩；(2) 断言：超时路径调用 destroyForcibly、runShortCommand 在预算内返回、killContainer 失败留痕（日志/计数）；(3) 可选：Testcontainers 形态的真实 docker 门控测试补充端到端。
- **失效面枚举**: doExecute 超时分支、中断分支、runShortCommand kill 分支、rm -f 分支、cancelAction 路径（C6-002 失效面①③）——全部依赖同一注入接缝，一次接缝改造全族可测。
- **信心水平**: 确定
- **误报排除**: 非"门控真实测试已覆盖"——真实测试被门控且即便运行也不注入 daemon 故障；非平台设施重审——接缝属本模块代码。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-007] shell 会话状态并发守卫（exportedEnv/cwd/readIndex/closed 标志）多线程驱动零测试

- **文件**: 现状测试 `nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java:635-681`、`ShellConcurrencyEdgeCaseTest.java:565-575`；缺口对象 `ShellCommandExecutor.java:45-47,80-83,319-370,545-585`、`ListShellInput`
- **证据片段**:
  ```java
  // 现状：单线程还原语义（组内 export 不泄漏 + 组外 export 泄漏）——C1-002 快照恢复面被单线程钉住
  assertFalse(executor.getExportedEnv().containsKey("GROUP_VAR"),
          "export inside a group must not leak ...");
  // ListShellInput：三模块测试 0 个文件引用（检索路径：类名 grep）
  ```
- **严重程度**: P2
- **并发场景**: 前台线程与 sink 线程（& job）/池线程并发读写会话状态；覆盖成员 C1-002（主）、C1-009、C1-008（随守卫批次顺带）。
- **触发时序**: 守卫修复（CHM 化/volatile/stateLock/在飞 fail-fast）回退时：全部会话状态测试单线程，CME/丢失更新/use-after-close 不可达——全绿。
- **现状**: 后台 job 测试（testBackgroundJobCompletes/WithException/Multiple/LifecycleOwnedBySink）验证了 sink 通道契约但 job 体不触碰 export/cd 与前台交叠。
- **风险**: C1-002 守卫批次（含 C1-009/C1-008）修复后零防回退；且"会话单命令在飞"契约（F10）本应先有守卫测试再有待飞守卫。
- **建议**: (1) 双线程驱动：前台 `export FOO=1; env` 与 sink job `export BAR=2` 交叠 N 次，断言终态环境为两者并集且无异常（CHM 化后应恒真）；(2) 组/snapshot 恢复与 job export 交叠的丢失更新断言；(3) ListShellInput/FileShellInput 双线程消费计数断言（不丢行不重复）；(4) close 后 execute 断言 125/error 语义（C1-008）。
- **失效面枚举**: buildEnvironment:551、updateContextFromResult:564、executeGroup:326-327、executeSubshell:340-346、ListShellInput.readIndex、FileShellInput.position、ShellCommandExecutor.closed——C1-002/C1-009 失效面清单全量。
- **信心水平**: 确定
- **误报排除**: 非"环境还原测试已覆盖会话状态"——还原语义与并发可见性是两个语义层，前者单线程即可断言，后者必须多线程。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-008] 输出无界旁路族（FileShellOutput 无界缓冲 + 聚合无帽）字节维度零断言

- **文件**: 现状测试 `nop-ai-shell/src/test/java/io/nop/ai/shell/io/ShellIOTest.java:190-260,383-416`、`ShellCommandExecutorTest.java:176-208,210-246`；缺口对象 `FileShellOutput.java:26-48`、`AbstractShellInput.java:19-77`、`FileShellInput.java:24-30`
- **证据片段**:
  ```java
  // 现状：小内容功能断言（覆写/追加/行聚合），字节量恒 ≤ 数十字节
  output.println("new line 1"); output.println("new line 2"); output.close();
  assertTrue(content.contains("new line 1"));
  ```
- **严重程度**: P2
- **并发场景**: 单线程即可触达（行为性缺陷）；覆盖成员 C8-010 与 C8-012（复核裁定同批修）。
- **触发时序**: 分块落盘/字节帽修复回退时：小内容测试无法区分有界与无界实现——全绿。
- **现状**: 无任何大输入 fixture；无 truncated 标志断言；`readLine` 长行聚合、`FileShellInput` 单巨 chunk、`FileShellOutput` 阈值落盘三面全部零参数化。
- **风险**: 同批修复（分块落盘 + SafeLineReader 帽复用）落地后不可防回归；帽回归是纯性能/稳定性语义，无功能测试可兜底。
- **建议**: (1) 参数化大内容（如 2MB 无换行字符串）断言 readLine 截断 + truncated 标志；(2) FileShellOutput 阈值落盘测试（写超阈值后中间落盘可见）；(3) readAllText 总帽断言。
- **失效面枚举**: FileShellOutput.write/flush、AbstractShellInput.readLine/readAllText、FileShellInput.read（CL-010 同源面随同验证）、ListShellInput 聚合——C8-010/C8-012 失效面清单全量。
- **信心水平**: 确定
- **误报排除**: 非"小内容测试已覆盖写路径"——写正确性与写有界性正交；C8 口径"行为性缺陷单线程不豁免"同样适用于其测试。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-009] http-request / graphql-query 响应体上界缺失零测试

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/HttpRequestExecutorTest.java`、`GraphqlQueryExecutorTest.java`（全小响应 Mock）；缺口对象 `HttpRequestExecutor.java:244-272`、`GraphqlQueryExecutor.java:150-158`
- **证据片段**:
  ```java
  // MockHttpResponse 全部返回小 body；grep "truncat|limit|large" 两测试文件 = 0 命中
  String body = response.getBodyAsString();   // 缺口：无上限断言
  ```
- **严重程度**: P2
- **并发场景**: 单调用即可触发（无需竞态）；C8-007 独立工单对应保护面。
- **触发时序**: 截断帽修复回退时：Mock 小响应无法区分有无上界——全绿。
- **现状**: mock 体系（MockHttpResponse/MockHttpClient）具备注入任意 body 的能力但从未注入大 body；无 truncated 标志契约测试。
- **风险**: 与兄弟上界（bash 1MiB、read-file 100K、skill 4000）的一致性修复（F3）落地后不可防回归。
- **建议**: Mock 注入 ≥ 上限×2 的 body，断言输出长度 ≤ 帽 + truncated 标记；graphql 错误/成功双路径同测（对齐失效面②）。
- **失效面枚举**: HttpRequestExecutor.buildSuccessResult（本体）、GraphqlQueryExecutor 成功/错误双路径（同修）；已有界兄弟（SearchEngine/Jq）登记不修但建议纳入同一参数化护栏防回归。
- **信心水平**: 确定
- **误报排除**: 非"SSRF 测试已覆盖该 executor"——SSRF 测试断言的是请求拒绝面，与响应体上界面正交。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-010] 文件类工具上界绕过（clamp 缺失族）零边界值测试

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/SearchContentExecutorTest.java:51+`、`ReadFileExecutorTest.java:61-88`、`PatchFileExecutorTest.java:74+`、`SearchFilesExecutorTest.java:43+`；缺口对象 `SearchContentExecutor.java:40-41`、`ReadFileExecutor.java:48-66`、`PatchFileExecutor.java:53`、`FileToolBizModel.java:52-73`
- **证据片段**:
  ```java
  node.setAttr("lastLines", 2);   // 现状：仅小值功能断言；病态值（Integer.MAX_VALUE）绕过路径 0 测试
  ```
- **严重程度**: P2
- **并发场景**: 单调用触发（模型不可信输入面）；C8-008 独立工单对应保护面。
- **触发时序**: 服务端 clamp 修复回退时：默认小参数测试不经过 clamp 分支——全绿。
- **现状**: 7 失效面（grep/read-file 行区间/patch-file/readFiles/maxResults）全部无病态参数测试；**注意**：已修实例 ApplyDeltaExecutor 的 1M/10M 帽同样无回归测试（`ApplyDeltaExecutorTest` grep cap/limit/truncat = 0）——修复族自身无测试先例。
- **风险**: clamp 族修复（7 失效面单批）落地后不可防回归；且"已修实例无测试"意味着该族在任何重构下都可能无声回退。
- **建议**: 对 7 失效面逐一加边界值测试（maxMatchesPerFile=1e9 等断言结果被 clamp）；为 ApplyDelta 已有帽补齐回归测试（同批，成本极低）。
- **失效面枚举**: C8-008 失效面①②③④⑥（缺陷面）+⑤⑦（已正确面，建议一并护栏）+ ApplyDeltaExecutor（已修无测，F3 残留）。
- **信心水平**: 确定
- **误报排除**: 非"mock fs 测试已覆盖工具语义"——mock 层接收的参数恰是待 clamp 的值，边界值测试在 mock 层即可精确断言。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-011] pipeline 与全平台共池无隔离：装配面零测试

- **文件**: 现状测试 `nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellConcurrencyEdgeCaseTest.java:97-151`（依赖缺省共享池）；缺口对象 `ShellCommandExecutor.java:58-60`
- **证据片段**:
  ```java
  // 测试恒用缺省构造（globalWorker）；grep "newExecutor|DefaultThreadPoolExecutor" nop-ai-shell 测试 = 0
  ShellCommandExecutor executor = new ShellCommandExecutor(localRegistry, fileSystem);
  ```
- **严重程度**: P2
- **并发场景**: shell 阶段任务与平台其他任务共池；C4-004 独立工单对应保护面。
- **触发时序**: 池隔离修复（注入独立池）回退/误配回 globalWorker 时：无测试断言池身份——全绿。
- **现状**: 测试反而**依赖**共享池默认装配（并发断言建立在 globalWorker 之上）；隔离装配的注入路径零测试触达。
- **风险**: 装配点隔离修复后，"注入是否生效"无护栏；未来对缺省池的改动也无回归仲裁。
- **建议**: 注入自定义池的测试断言阶段任务线程名归属；保留一个缺省装配冒烟测试防注入路径破坏现有行为。
- **失效面枚举**: ShellCommandExecutor 缺省回退（本体）；tool 执行池注入面（ToolExecuteContext，C4-004 失效面②）——装配测试形态可共享。
- **信心水平**: 确定
- **误报排除**: 非"并发测试已证明 pipeline 可用"——可用性与隔离性正交。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-012] `&` job sink 池身份无守卫：同池死锁装配零测试

- **文件**: 现状测试 `nop-ai-shell/src/test/java/io/nop/ai/shell/executor/RecordingBackgroundJobSink.java:23-27`（独立池 stub）、`ShellCommandExecutorTest.java:388-403`；缺口对象 `ShellCommandExecutor.java:350-370`
- **证据片段**:
  ```java
  // 测试 sink 固定独立池——同池化（最直觉装配）在测试中结构性不可达
  private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "test-shell-bg-fork"); ...
  ```
- **严重程度**: P2
- **并发场景**: sink 线程 join 等待 executor 池内阶段任务；C4-006 独立工单对应保护面。
- **触发时序**: 守卫/javadoc 契约修复回退时：测试 sink 恒为异池，join-同池自锁不可达——全绿。
- **现状**: sink 生命周期契约（提交/取消/异常传播）测试充分（5+ 测试）；池身份维度零断言。
- **风险**: 运行时守卫修复（若 7.1 选守卫方案）无法防回归；SPI 契约测试缺失使嵌入方误装配无护栏。
- **建议**: 增加"同池 sink"测试装配（body 经同一 executor 提交），断言守卫 fail-fast 或按所选修复方案断言不死锁（限时完成）。
- **失效面枚举**: executeBackground 唯一 sink.submit 点（本体）；agent 侧 BackgroundRegistryJobSink（超界，登记供 agent 批次）。
- **信心水平**: 确定
- **误报排除**: 非"sink 测试已覆盖后台语义"——生命周期通道与池拓扑是两个维度。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-013] BashExecutor submit→run 无回滚：UOE 注入测试缺失

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/TestBashExecutorBackgroundMode.java:58-133,221-243`；缺口对象 `BashExecutor.java:209-244`
- **证据片段**:
  ```java
  @Override public void run(String taskId, Runnable body) {
      pool.submit(() -> { try { body.run(); } catch (Exception e) { complete(taskId, STATUS_FAILED, ...); } });
  }   // stub 的 run 永不抛 UOE——接口 default run 的抛出契约未被任何测试触达
  ```
- **严重程度**: P2
- **并发场景**: 前台工具线程与 registry 账本；C4-007 独立工单对应保护面。
- **触发时序**: 回滚修复（complete(FAILED)+错误结果）回退时：StubRegistry.run 不抛，submit→run 间隙异常不可达——全绿。
- **现状**: fail-loud 测试（无 registry/无 sandbox）断言的错误结果语义正确但发生在 submit 之前；"部分装配"（有 submit 无 run）形态零测试。
- **风险**: 僵尸 RUNNING 账本面（F2）修复后不可防回归。
- **建议**: StubRegistry 变体保留接口 default run（抛 UOE），断言：(1) 返回错误结果且 (2) `registry.snapshot(taskId)` 终态为 FAILED 而非 RUNNING 残留。
- **失效面枚举**: doExecuteBackground（本体）；ToolManagerImpl runInBackground 预检（不拦截本缺陷，登记）；BackgroundRegistryJobSink submit+run 连续调用（超界登记）。
- **信心水平**: 确定
- **误报排除**: 非"fail-loud 测试已覆盖错误语义"——两测试的错误发生在 submit 前，回滚语义在其断言面之外。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-014] 取消链覆盖面（16/17 executor 不读 cancelToken + 排队路径断链）零矩阵测试

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/JqToolExecutorTest.java:33,78`（唯一取消语义测试）；缺口对象 17 个 executor 的 doExecute 与 `ToolManagerImpl` wrapper future
- **证据片段**:
  ```java
  // 唯一的取消检查点测试——jq；其余 16 executor 的取消语义零测试
  // cancelToken → future.cancel(true) → jq 检查点终止（无僵尸计算）
  ```
- **严重程度**: P2
- **并发场景**: 调用方取消线程与 executor 池线程；C4-008 独立工单对应保护面。
- **触发时序**: 修复（统一 cancelToken 检查点 + wrapper cancel 传播）回退时：除 jq 外全部 executor 无取消断言，manager 层 wrapper.cancel 不传播也无测试——全绿。
- **现状**: `cancel(true)` 全测试树仅 2 文件命中（jq 测试 + shell 测试 stub）；jq 测试同时是"正确形态"的模板证据。
- **风险**: 与 WI 3.1 MA-08 / WI 2.1 bindCancel 协同的修复落地后，工具侧 16 executor 面不可防回归。
- **建议**: 参数化取消矩阵：对 17 executor 各注入慢操作桩 + cancelToken 取消，断言在预算内终止；manager 层加 wrapper.cancel 传播断言（修复后应达池任务）。
- **失效面枚举**: 17 executor 全清单（jq 为唯一已合规成员，测试已在位）；manager 排队/直发 wrapper future 两路径。
- **信心水平**: 确定
- **误报排除**: 非"jq 测试即族保护"——单成员测试不约束其余成员的回归。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-015] 消费中断映射为干净 EOF：中断语义零测试

- **文件**: 缺口对象 `nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:66-81`；检索路径：nop-ai-shell io 测试（ShellIOTest）grep `interrupt|cancel` = 0 命中
- **证据片段**:
  ```java
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;                       // null ≡ EOF —— 无任何测试触达本分支
  ```
- **严重程度**: P2
- **并发场景**: 消费者线程被外部中断；C8-004 独立工单对应保护面。
- **触发时序**: 修复（中断抛 NopException / truncated 标志）回退时：无中断注入测试——全绿。
- **现状**: 同族对照 BlockingQueueShellInput.read 的抛异常语义同样无中断测试（对称缺口）。
- **风险**: "截断结果对模型不可见"的语义修复后不可防回归。
- **建议**: 消费者线程注入 interrupt（阻塞于 take 时），断言修复后语义（异常或 truncated 标志）；同批为 BlockingQueueShellInput.read 补对称中断测试。
- **失效面枚举**: BlockingQueueShellOutput$Adapter.read（本体）；BlockingQueueShellInput.read（已正确模板）；AbstractShellInput.readLine/chunks（下游受害面随同断言）。
- **信心水平**: 确定
- **误报排除**: 非"中断是罕见路径不配测试"——C8-004 的风险正是取消/过载路径静默截断，恰需测试固化。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-016] executeParallel 信号量 permit 泄漏 + 无界 acquire：异常注入测试缺失

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/manager/ToolManagerImplParallelTest.java:133-196`；缺口对象 `ToolManagerImpl.java:232-246`
- **证据片段**:
  ```java
  // 现有测试保护的是限流机制本身（maxObserved<=2 即红）；
  // 泄漏面需要 callTool 同步抛出——测试拦截器为空列表、provider 不抛，不可达
  assertTrue(countingExecutor.maxObserved.get() <= 2, "...");
  ```
- **严重程度**: P2
- **并发场景**: 派发线程阻塞于 acquire；C8-005 独立工单对应保护面。
- **触发时序**: try/finally release 修复回退时：无同步异常注入——permit 泄漏不可见——全绿（后续测试用新 manager 实例，泄漏不跨测试累积）。
- **现状**: 并行批次语义（限流/顺序/丢包）保护充分（5 测试）；异常路径与 permit 记账零断言。
- **风险**: 修复后不可防回归；且泄漏形态（后续 acquire 永久阻塞）若回归，现有测试因各自新建 manager 而无法暴露。
- **建议**: 注入"首个调用同步抛出"的 provider，断言：(1) 该调用得异常完成；(2) 同 manager 后续批次仍能获得全部 permit（记账守恒）。
- **失效面枚举**: submitBounded L237-245（本体）；callTool pathQueues 分支（无信号量，不受影响）；executeSequential（无并发控制，不受影响）——对齐 C8-005 失效面。
- **信心水平**: 确定
- **误报排除**: 非"限流测试已覆盖信号量"——机制正确性与异常路径记账正交。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-017] UpdateTodosExecutor static todoLists 复合操作竞态：并发同会话测试缺失

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/UpdateTodosExecutorTest.java:33-227`；缺口对象 `UpdateTodosExecutor.java:25,93-133`
- **证据片段**:
  ```java
  // 全部单线程：clear 语义（:116）、跨会话隔离（:132）、写空仅清本会话（:170）
  // 文件内 grep "concurrent|Parallel|Thread" = 0
  ```
- **严重程度**: P2
- **并发场景**: 同 session 两条 update-todos 并行派发；C1-004 独立工单对应保护面。
- **触发时序**: compute 原子化修复回退时：单线程测试不产生 remove/put 交叠——全绿（竞态为概率性，测试即使并行也需多次迭代放大）。
- **现状**: 功能语义（写/读/清/隔离/无 session fail-closed）覆盖充分；并发维度零触达。
- **风险**: 修复后不可防回归；static 进程级状态的回归影响跨测试污染（反而可能造成偶发红——可作脆弱性信号）。
- **建议**: 同 session 并行 clear+write × N 迭代断言终态一致（两种合法终态之一）；修复后应恒为"后写胜"或按 compute 语义。
- **失效面枚举**: UpdateTodosExecutor.todoLists（本体）；同族 static 集合（SsrfAddressGuard 等不可变，安全，登记不修）。
- **信心水平**: 确定
- **误报排除**: 非"CHM 单操作原子即安全"——被测语义是 remove/put 复合决策原子性。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-018] DeltaVFS/扩展面非原子写：撕裂读测试缺失（对照已修兄弟已有测试模板）

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/mavenvfs/vfs/DeltaVirtualFileSystemTest.java:109-142`、`nop-ai-tools/src/test/java/io/nop/ai/tools/mcp/TestAiFileTool.java:75-103`；模板 `TestLocalToolFileSystemAtomicWrite.java:37-97`；缺口对象 `DeltaVirtualFileSystem.java:149-195`、`DslToolImpl.saveDslFile:81`
- **证据片段**:
  ```java
  // 现状：写后读功能断言；并发"写线程 + 读线程"撕裂构造 = 0
  vfs.copyToVirtual(sourceFile, "copied/CopiedFile.txt");
  // 已修兄弟的测试模板在位（临时文件+ATOMIC_MOVE 的失败保旧断言），未复用到本站点
  ```
- **严重程度**: P2
- **并发场景**: 安装/更新写线程与解析读线程；C1-005 独立工单对应保护面。
- **触发时序**: atomicWriteText 模式移植回退时：单线程写读测试无法区分原子与非原子实现——全绿。
- **现状**: TestDslToolImpl 甚至无 save 路径测试（仅 load 两测试）；TestAiFileTool 断言写成功非写原子性。
- **风险**: 三站点（getOutputStream/copyToVirtual/扩展面）修复后不可防回归；断电/半写语义无任何验证。
- **建议**: 照抄 TestLocalToolFileSystemAtomicWrite 形态：失败保旧断言（写中途失败→旧内容完好）+ 并发读断言（读线程循环读，写线程反复覆盖，断言每次读取要么旧要么新、无空/半截）。
- **失效面枚举**: DeltaVirtualFileSystem.getOutputStream、copyToVirtual（本体两站点）；DslToolImpl.saveDslFile、AiFileTool 保存路径（扩展面）；LocalToolFileSystem.atomicReplace/ThoughtStorage.atomicWriteText（已修有测，模板）。
- **信心水平**: 确定
- **误报排除**: 非"mavenvfs 零生产用户不需要测试"——扩展面为生产接线请求可达（C1-005 复核裁定），且模板成本极低。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-019] 两公共注册表并发注册：契约测试缺失（7.1 裁决前置条件面）

- **文件**: 现状测试 `nop-ai-shell/src/test/java/io/nop/ai/shell/commands/ShellCommandRegistryTest.java:13-112`；缺口对象 `DefaultToolExecutorProvider.java:18-67`、`ShellCommandRegistry.java:20-116`
- **证据片段**:
  ```java
  // DefaultToolExecutorProvider.registerTool：三模块测试 grep = 0 命中（连功能测试都没有）
  // ShellCommandRegistry：单线程 register/findCommand 功能测试在位
  ```
- **严重程度**: P2
- **并发场景**: 请求线程热路径读 + 运行时注册写；C1-006（7.1 裁决：CHM 替换或声明边界+守卫）对应保护面。
- **触发时序**: 任一修复方向落地后回退时：无并发注册+查找测试——全绿；且 registerTool 目前连功能测试都为零。
- **现状**: 该条为"理论风险，未具体化"（复核确认零生产写方）；其保护力登记为**条件性**——若 7.1 裁"声明边界+javadoc"则测试需求降为契约文档验证；若裁 CHM/守卫则需回归测试。
- **风险**: 修复方向未定，登记以防 7.1 裁定后遗漏。
- **建议**: 裁定后补：并发 register+get 循环测试（CHM 方案）或运行时断言守卫测试（守卫方案）；getAllCommands 快照语义断言。
- **失效面枚举**: DefaultToolExecutorProvider.executors、ShellCommandRegistry.commands/aliases（同族同修两根）。
- **信心水平**: 确定
- **误报排除**: 非"零生产写方不配测试"——公共 API 契约（javadoc 鼓励运行时注册）使契约测试成立。
- **复核状态**: 未复核（C10 单轮维度，条件性登记）

### [C10-020] SequentialThinking 双锁域撕裂：并发一致性测试缺失（7.1 裁决前置条件面）

- **文件**: 现状测试 `nop-ai-tools/src/test/java/io/nop/ai/tools/sequential_thinking/service/TestSequentialThinkingBizModel.java:64+`、`TestThoughtStorage.java:156-168`；缺口对象 `SequentialThinkingBizModel.processThought`
- **证据片段**:
  ```java
  // addThought→getAllThoughts 跨两锁域 + clearHistory 并发撕裂：全部测试单线程
  storage.clearHistory("s1");
  assertTrue(storage.getAllThoughts("s1").isEmpty(), "clearHistory must empty the session");
  ```
- **严重程度**: P2
- **并发场景**: 请求线程并发 processThought/clearHistory；C1-010（7.1 裁决：单锁域复合方法或 javadoc 声明最终一致）对应保护面。
- **触发时序**: 若 7.1 裁"单锁域复合方法"：修复回退时单线程测试无法区分——全绿；若裁"声明最终一致"则无需回归测试（登记条件同 C10-019）。
- **现状**: processThought 成功路径、错误码、路径穿越拒绝覆盖充分；并发一致性零触达。
- **风险**: 派生视图撕裂（P3 量级）修复后不可防回归。
- **建议**: 条件性：并发 add+getAll 断言"添加成功必可读"（单锁域方案）或登记文档断言（声明方案）。
- **失效面枚举**: processThought 的 addThought→getAllThoughts 复合（本体）；clearHistory 并发面（同修点）；exportSession（登记）。
- **信心水平**: 确定
- **误报排除**: 非"P3 不配登记"——C10 口径对全部保留发现逐条评估，P3 缺保护同样登记（判级仍 P2 计数从轻）。
- **复核状态**: 未复核（C10 单轮维度，条件性登记）

### [C10-021] ThoughtStorage 会话文件无回收：生命周期断言缺失

- **文件**: 现状测试 `nop-ai-tools/src/test/java/io/nop/ai/tools/sequential_thinking/service/TestThoughtStorage.java:59-71,156-168`；缺口对象 `ThoughtStorage.java:97-146`、`AiToolsHelper.java:27-33`
- **证据片段**:
  ```java
  // 现状断言的恰是"清内容不删文件"：
  storage.clearHistory("s1");
  assertTrue(storage.getAllThoughts("s1").isEmpty(), "clearHistory must empty the session");
  // 文件数/目录 listing 增长断言 = 0；headerless UUID 铸造积累 = 0 测试
  ```
- **严重程度**: P2
- **并发场景**: headerless 请求刷文件（单线程即可触达增长）；C6-007 独立工单对应保护面。
- **触发时序**: 回收修复（clearHistory 删文件/TTL 清扫/headerless 拒绝落盘）回退时：现有断言在两种实现下均绿——全绿。
- **现状**: 持久化/隔离/导入导出/路径安全覆盖充分；唯生命周期维度（文件数守恒/回收）零断言。
- **风险**: F4 修复后不可防回归；反向注意：删文件变体与现有 clearHistory 断言兼容（已核对，反向钉死排查通过）。
- **建议**: clearHistory 后断言存储目录文件数回到基线；headerless N 次调用后（启用拒绝/内存态方案时）断言目录零增长。
- **失效面枚举**: addThought/saveSession 文件创建（本体）、clearHistory（唯一清理 API）、exportSession（导出副本，登记）。
- **信心水平**: 确定
- **误报排除**: 非"clearHistory 测试即清理测试"——清内容与清文件是两个语义（本条缺陷本体恰是二者混淆）。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-022] BashExecutionTracker remove-before-act + 销毁无守卫：失败注入测试缺失

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/sandbox/TestBashExecutionProgress.java:36-43,79-85`；缺口对象 `BashExecutionTracker.java:82-98`
- **证据片段**:
  ```java
  assertTrue(sandbox.cancelExecution("call-progress-1"));          // happy path 已钉
  assertFalse(tracker.cancelExecution("no-such-call"));            // unknown→false 已钉
  // 缺失：destroyForcibly 抛 UOE/SecurityException 的注入；半途失败后的可重试断言
  ```
- **严重程度**: P2
- **并发场景**: 取消线程与执行线程 finally unregister 竞争；C6-009 独立工单对应保护面（与 C10-006 共享故障注入 harness）。
- **触发时序**: act-then-remove/守卫修复回退时：无失败注入——remove-before-act 与守卫缺失不可观测——全绿。
- **现状**: 取消的 happy/unknown 两面已测试（且与修复兼容，反向钉死排查通过）；失败路径零触达。
- **风险**: "取消不可重试"修复后不可防回归。
- **建议**: 注入式 ProcessHandle 桩（descendants/destroyForcibly 抛 UOE），断言注册项保留、二次 cancel 可重试；返回值语义断言（失败≠"无在途"）。
- **失效面枚举**: BashExecutionTracker.cancelExecution:83-96（本体）；HostBashSandbox.killTree（守卫模板，已有隐式保护经 BashSandboxTest 墙钟测试）。
- **信心水平**: 确定
- **误报排除**: 非"happy path 测试已覆盖 cancelExecution"——顺序缺陷与守卫缺失只在失败路径显现。
- **复核状态**: 未复核（C10 单轮维度）

### [C10-023] ToolPathAffinity XML 形态串行化：双源回归测试缺失

- **文件**: 现状测试 `nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/manager/TestToolPathQueue.java:203-228`（JSON 形态 pathless 钉住"不排队"）、`ToolManagerImplParallelTest.java:107-121`（fromNode 但无 path 子节点）；缺口对象 `ToolPathAffinity.java:34-58`
- **证据片段**:
  ```java
  // 现状最近邻测试断言的是反方向语义：
  assertEquals(2, maxObserved.get(), "no extractable path → unqueued (documented degradation)");
  // XML 形态（fromNode + <path> 子节点）同 path 两调用被串行化的回归测试 = 0
  ```
- **严重程度**: P2
- **并发场景**: XML DSL 派发（AiToolCall.fromNode）并行同 path 写；C1-007 独立工单对应保护面。
- **触发时序**: 双源提取修复回退时：现有测试的调用均无节点 path，提取结果两种实现下同为 null——全绿。
- **现状**: `ToolPathAffinity` 测试直接引用 = 0；双源读取家族（runInBackgroundRequested/BashExecutor.textArg）有测试间接经过但均非 path 键提取。
- **风险**: "串行化保护对 XML 入口静默失效"修复后不可防回归；watch 级邻接钉（malformedOrPathlessInputRunsUnqueued 的 degradation 措辞）需在修复同批复核措辞。
- **建议**: fromNode + path 子节点双调用测试断言 maxObserved==1（串行）；保留 pathless degradation 测试并更新其注释与新语义对齐。
- **失效面枚举**: ToolPathAffinity.extract（唯一影响串行化覆盖面的站点）；双源家族其余成员（已双源，登记不修）。
- **信心水平**: 确定
- **误报排除**: 非"排队机制已有 4 测试"——机制测试全部走 JSON input 形态，XML 形态是独立入口。
- **复核状态**: 未复核（C10 单轮维度）

**缺保护登记统计**：C10-001…023 共 **23 条**（全部 P2），覆盖 39/39 保留发现（会红=0 故全量登记）；其中条件性登记 2 条（C10-019/C10-020，7.1 裁决前置）；含反向钉-1 改写义务 1 条（C10-002）。按严重度加权：覆盖 9 条 P1 缺陷的登记为 C10-001/002/004/005/006（5 条登记承载全部 9 条 P1 的保护缺口）。

---

## 系统性观察

1. **故障注入维度整体缺失（最重要）**：三模块 67 个测试文件中，没有任何测试注入"同步抛出/池拒绝/null 依赖/悬挂子进程/daemon 故障/中断"任一故障形态（grep `RejectedExecution`=0、故障注入 stub=0）。9 条 P1 全部依赖故障路径或特定交叠触发，因此 9/9 无保护不是巧合而是结构必然——现有测试文化只覆盖 happy path 语义。修复批次若不同步建立故障注入 harness（C10-004/005/006/022 共享同一套模式），P1 修复将处于零护栏状态。

2. **shell 模块的"小输出盲区"是背压族缺陷的直接成因面**：全部 shell 测试输出 ≤5 chunk，模块核心设计参数 1024（队列容量）在测试树中零出现。C8-001/002/003/011/012、C6-004/005、C4-003 八条发现全部生活在这条从未被测试跨越的边界上。类级 `@Timeout` 说明作者具备挂死意识，但没有任何测试到达可挂死拓扑。任何修复都应先补一个"小容量参数化"测试设施（构造器已支持自定义容量，成本极低）。

3. **假保护的主导形态是"机制测试冒充语义测试"**：21 条假保护中 16 条的模式相同——测试存在于同一接缝且保护了*机制的 happy path*（串行化机制、限流机制、管道并发、环境还原、写成功），但缺陷语义（异常路径、失败记账、生命周期、边界值）在其断言集合之外。典型：ToolManagerImplParallelTest 保护了信号量限流却对 permit 泄漏零感知；UpdateTodosExecutorTest 覆盖了 clear 语义却对 remove/put 交叠零感知。评审"已有测试"时须按语义而非按接缝计数。

4. **外部进程路径结构性不可测**：DockerBashSandbox 将 ProcessBuilder 硬编码在 private static 方法中，无任何注入接缝，导致 4 条发现（C6-002/003、C4-002、C8-009）的 CI 覆盖恒为零（真实 docker 测试被 assumeTrue 门控）。讽刺性证据：`BashSandboxTest.isDockerAvailable:300-326` 的 javadoc 逐字记载了 runShortCommand 的挂死机理并在 helper 中实现了正确模式——修复知识已存在于测试文件，唯一缺的是把它变成对生产代码的断言。建议以 CommandRunner 接缝改造作为 C6-002 工单的前置项。

5. **泄漏检测钩子全仓为零 + 已修实例保护不均衡**：无线程计数、无 fd 计数、无 registry 大小、无文件数断言（C10 步骤 4 要求的形态全部缺失）。历史修复的保护也不均衡：atomicReplace 有专测（TestLocalToolFileSystemAtomicWrite）、周期副本有专测（TestBashExecutionProgress）、而 ApplyDelta 1M/10M 帽与 killTree 唯一墙钟断言间接保护——同族修复三种保护水平并存，说明"修复必带回归测试"未成为本单元的机械惯例（对照 AGENTS.md Bug Fix Test Coverage Rule）。

---

## 终止判定

**C10 为单轮维度，显式声明终止。** 依据：(1) 本维度对象是封闭集合（39 条最终保留发现 × 现有测试 suite），评估为静态变异推演，无增量发现面；(2) 39/39 全部完成三档判定并登记，矩阵闭合；(3) 反向钉死排查覆盖全部接缝测试并逐条核对修复兼容性，产出 1 命中 + 1 watch；(4) 缺保护登记按主登记建议网合并收敛为 23 条，无遗留未归置发现。无第 2 轮需求。

---

## C10 复核结论

- 复核人：C10 维度独立复核员（WI 3.2，2026-10-20）
- 复核基线：master worktree（`nop-entropy-master`，含 b779ee8a69 / 71224ea148）live code，与报告声明的「源码于主工作区 worktree 读取，行号一致」一致。注意：`concurrency-audit-remediation` 分支 worktree 的 `BashSandboxTest.java` 等文件落后于该基线（缺 b779ee8a69 的 isDockerAvailable javadoc + wait-first 改造），本复核全部回测以 master 为准。
- 复核方法：39 行矩阵 ↔ 四维度 `## 最终保留项` 全量对账；深度回测 23 条矩阵行（假保护 13 + 无测试 10，均超 ≥8/≥3/≥3 要求）；报告全部 grep 检索声明逐一复现；反向钉死命中项与 9 项「排查为否」抽样 7 项回验；缺保护登记抽验 12 条（超 ≥5 要求）。

### 一、对账结果（39 行 ↔ 最终保留清单）

**差异 0。** C1×10 + C4×8 + C6×9 + C8×12 = 39 行，与四维度最终保留清单逐条一一对应，无遗漏、无多记、无重复行；模块归属抽查全部正确。严重度分布复核一致：P1×9（C1-001、C4-001/003/005、C6-001/002/003/008、C8-009）、P2×28、P3×2（C1-008、C1-010）、P0×0。矩阵统计行自洽：会红 0 + 假保护 21 + 无测试 18 = 39（逐行重计吻合）；「9/9 P1 全部无测试」逐行核对成立。三模块测试文件盘点 43+9+15=67 复现。

### 二、抽查结果与修正项

**判定维持 39/39（抽查深度回验 23/23 维持）。** 抽查中核实的代表性证据：`TestToolPathQueue.java:223`、`TestBashExecutionProgress.java:42/:79`、`TestThoughtStorage.java:168`、`ShellIOTest.java:296-299`、`ReadFileExecutorTest.java:78`（`lastLines=2`）、`ToolManagerImplParallelTest.java:147`、`ShellConcurrencyEdgeCaseTest.java:158-173`（failcmd 退出码 1 非 lambda 抛异常）等引用均为逐字吻合；`registerTool`/`killContainer|runShortCommand`/`pathQueues`/`ListShellInput|FileShellInput`/`RejectedExecution` 三模块测试 0 命中、`cancel(true)` 恰 2 文件、`1024` shell 测试 0 命中、`truncat|limit|large` 两 executor 测试 0 命中——全部复现。

修正项 3 处（均不动判定，登记如下供修复工单引用时更正）：

1. **[修正-1] C1-008 行证据文件错标（唯一的证据指向错误）**：矩阵该行写 `ShellCommandExecutorTest.java:382-397`（close 幂等/close 后不再跟踪 job）——实际两测试在 `ShellConcurrencyEdgeCaseTest.java:382-399`（`testCloseIdempotent`、`testCloseWithNoBackgroundJobs`）；`ShellCommandExecutorTest.java:382-397` 是 `testBackgroundWithoutSinkFailsLoud` 前的管道测试尾部。判定（假保护：触达 close()、execute-after-close 守卫断言 = 0）经回验**维持**。
2. **[修正-2] 登记/矩阵统计句算术**：`缺保护登记统计`行「覆盖 9 条 P1 缺陷的登记为 C10-001/002/004/005/006（5 条）」——实际承载 9 条 P1 的是 **4 条**登记（C10-001 承载 C4-003；C10-004 承载 C1-001+C4-005；C10-005 承载 C4-001+C6-001+C6-008；C10-006 承载 C6-002+C6-003+C8-009）；C10-002 的成员（C6-005/C8-002/C1-003）全为 P2 条件化，不含 P1。承载关系本身无遗漏，仅条数表述有误。
3. **[修正-3] 两处 grep 表述精度**：① C1-004 行「文件内 grep concurrent/Parallel/Thread = 0」实际 3 命中（`UpdateTodosExecutorTest.java:8-9,243`，均为 import 与 MockContext.getExecutor 声明，无任何并发驱动——判定不变）；② C8-004 行/C10-015「ShellIOTest grep interrupt|cancel = 0」实际 1 命中（`:359` sleep-catch 样板 `Thread.currentThread().interrupt()`，非中断语义测试——判定不变）。

**Watch-only（不要求返工）**：假保护/无测试的分档边界在「同接缝存在机制测试」案上应用不完全一致——C1-001/C4-005/C6-006/C8-006（同接缝有机制测试但缺陷触发路径结构性不可达）判**无测试**，而 C1-004/C6-009（同接缝单线程触达、并发触发时序不可达）判**假保护**。两档处置等价（均全量登记、判级均 P2），对 39/39 登记面与 9/9 P1 结论零影响；如后续批次沿用本矩阵，建议在口径声明中把判据统一为「缺陷触发路径是否有任何测试触达」。

### 三、反向钉死复核

**命中项维持。** [反向钉-1] 方向与断言原文逐行一致：`ShellIOTest.java:21` 类级 `@Timeout(5)`；`:294-300` 容量 1 → write 1 chunk（队满）→ 主线程 `close()`（无消费者线程）→ `assertThrows(NopException, write)`。生产侧 `BlockingQueueShellOutput.close`（:42-47，`queue.offer(eof)` 忽略返回值）若改 `queue.put(eof)` 阻塞投递，该测试主线程永久阻塞、被 `@Timeout(5)` 打红——钉死方向、条件性定性（仅阻断 put 变体）、C10-002 的同批改写义务均成立。生产缺陷本体（offer-and-ignore）在 master 源码 :45 复核属实。「排查为否」9 项抽验 7 项（TestToolPathQueue:223 degradation 措辞 watch、EdgeCaseTest:154-174 退出码语义、ShellCommandExecutorTest:388-403 null-sink、TestThoughtStorage:156-168、BashSyntaxParserTest:658-670/BashLexerTest:27-34 解析层、TestBashExecutorBackgroundMode:220-243 submit 前失败、BashSandboxTest:41-59 墙钟断言）全部与修复不冲突，维持「绿」结论。

### 四、缺保护登记复核

**23 条全部保留，驳回 0。** 抽验 12 条（C10-001/002/003/004/012/015/016/019/020/021/022/023）：

- **格式**：附录 A 12 字段齐全（含硬性字段失效面枚举），各条均 ≥10 行，证据片段与现状测试/生产源码逐字吻合（含 `failcmd | echo after`、`clearHistory must empty the session`、`test-shell-bg-fork`、`no extractable path → unqueued` 等引文）。
- **编号**：C10-001…023 连续无断号。
- **覆盖闭合**：39 条发现的主归属映射逐条核对为 5+3+2+2+3+4+3+2+15×1 = 39，每条发现恰有一个唯一主归属，无双重归属、无悬空。生产锚点抽查（`ToolManagerImpl.java:32-33/101-121/122/233-248`、`BashExecutor.java:209-215` submit→run 间隙、`ShellCommandExecutor.java:58-60/349-372`、`BlockingQueueShellOutput.java:42-47/66-81`）全部命中。
- **条件性登记口径**：C10-019（C1-006）/C10-020（C1-010）与四维度最终保留项中「7.1 逐项裁决」双分支修复归属一致——裁 CHM/守卫则需回归测试、裁声明边界则降为契约验证，条件表述成立，保留。
- **反向钉-1 义务**：已正确挂接在 C10-002（风险/建议字段）。

### 五、判定统计与最终结论

| 项 | 结果 |
|---|---|
| 矩阵对账差异 | 0 |
| 矩阵行判定维持/修正 | 39 维持 / 0 修正（证据指向修正 1 处：C1-008） |
| 反向钉死命中项 | 1 维持（ShellIOTest:294-300，方向与原文一致） |
| 缺保护登记保留/驳回 | 23 保留 / 0 驳回 |
| 修正项 | 3（证据文件错标 1、统计句条数 1、grep 表述精度 2，合并计 3 条记录） |
| Watch-only | 1（假保护/无测试分档边界口径） |
| **最终保留缺保护登记数** | **23（全部 P2；条件性 2：C10-019/020；含反向钉改写义务 1：C10-002）** |

C10 审计通过复核。39 行矩阵判定全部成立，「会红=0、9/9 P1 零测试保护、故障注入维度整体缺失」三项核心结论经独立回测维持；系统性观察 4 的 isDockerAvailable javadoc 证据在 master 基线（b779ee8a69）复核属实。上述 3 处修正项均为证据表述层，不影响任何工单归属与修复范围。
