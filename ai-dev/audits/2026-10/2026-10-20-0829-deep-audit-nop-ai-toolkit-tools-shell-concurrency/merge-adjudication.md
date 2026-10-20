# 单元内合并裁定 — nop-ai-toolkit-tools-shell（WI 3.2）

- 裁定人：CRA WI 3.2 独立合并裁定员（与四维度初审/深挖/复核代理不同任务）
- 裁定日期：2026-10-20
- Status：closed（单元内族结构收敛完成；2 组判级分歧移交 7.1，不阻塞工单结构与修复范围）
- 输入基线：本目录四维度报告（`## 最终保留项` + `## 维度复核结论`）+ round2 三文件 + `C10-test-protection.md` + `ai-dev/audits/cra-index.md`（P1 台账 117 条，WI 1.1–3.1）

---

## 一、裁定口径与输入

### 1.1 输入

1. **39 条复核后保留发现**（P0×0 / P1×9 / P2×28 / P3×2）：C1×10（P1×1/P2×7/P3×2）、C4×8（P1×3/P2×5）、C6×9（P1×4/P2×5）、C8×12（P1×1 并案成员/P2×11）。现状标注全部为**新发现**（唯二注记：C6-001 为已修站点残余缺口面；C4-002 为「killTree 已修实例」的 F3 残留形态——均不改变新发现定性）。
2. **复核已登记的主登记建议网**（视为已定事实，本裁定只做收敛，不翻案）：
   - C1-001=C4-005 主登记 C1（pathQueues 挂死族，悬挂面并集）
   - C6-001 主登记 reader 泄漏族（C4-001 并案关闭，P1）
   - C6-002 主登记 docker 清理族（C8-009 收编为成员；C4-002 心跳占用面并入 C6-003 工单）
   - C6-004 族案主登记（shell 管道死锁：C4-003 REE 面收编 + C8-001/003 随族 + C6-005/C8-002/C1-003 EOF 面同批同判级）
   - C8-006 主登记 pathQueues F4 三登记（C6-006/C4-CL-005 归并）
   - exportedEnv 三登记（C6-CL-009/C4-CL-006/C8-CL-016）主登记候选 C6-CL-009
3. **cra-index 台账**：WI 1.1–3.1 累计 117 条 P1 + CL 台账，用于跨单元查重（§七）。
4. **C10 缺保护登记 23 条**：映射随本体工单，不独立立项（§九）。

### 1.2 三态判定规则

每条保留发现恰好落入一处：

- **①缺陷族**：≥2 条同根因同修复点 → 吸收合并，单工单（主登记 + 成员），族严重度取成员最严；
- **②独立立案**：单条缺陷单工单（可带同批/对齐注记）；
- **③watch-only**：不立案、登记观察。

### 1.3 本裁定的显式收敛点（冲突裁定）

| # | 冲突/待收敛点 | 裁定 |
|---|---|---|
| 裁-1 | EOF offer 面（C6-005/C8-002/C1-003）是并入 C6-004 管道族还是独立族 | **独立族 MA-05**：C6 复核已确认「EOF 信号投递 vs 提交中止」为不同机制面；同批同判级约束保留（与 MA-06 同批实施、判级联动） |
| 裁-2 | MA-05 主登记归属（C6-005 vs C8-002） | **C8-002 主登记**：C1 复核建议「终裁取 C8 侧判级」+ C8 侧确定性触发链（恰 1024 chunk 单命令 close 时队列必满）强于 C6-005 口径 + C1 最终表指向「并入 C8-002 工单」 |
| 裁-3 | C8-011（`0<&1` 自引用）是否入 C6-004 族 | **入族 MA-06**：C8 复核判定表明示「挂死族成员（随 C6-004 族案）」，C10-001 单保护面覆盖五成员佐证 |
| 裁-4 | C6-008 是否入 reader 族（建议网仅点名 C4-001） | **入族 MA-02**：C6 复核「与 C6-001 同 diff」+ 同触发链（`&` 孪生持管道/宽限耗尽读者仍存活），C10-005 单保护面覆盖三成员佐证 |
| 裁-5 | C1-002/C1-009/C1-008 是否同族 | **同族 MA-08**：同根因（shell 会话单线程假设被 `&`/pipeline 打破而状态无同步、契约无运行时守卫，F10 族）+ 同修复点（会话状态守卫同批方案，C1 复核互指「同批守卫方案」），C10-007 单保护面佐证 |
| 裁-6 | C8-009 的三个面（CLI 进程/reader/waitFor）跨族归属 | 工单级收编 **MA-03**（整条不重复计数）；面级归属：reader 线程/管道面由 MA-02 覆盖、runShortCommand waitFor 面由 MA-04 覆盖——按 C6/C8 复核一致意见登记，修复 diff 分工据此 |

**判级分歧不属本裁定改动范围**，登记移交 7.1（§五）：①沙箱族跨单元（WI 3.1 MA-17 P2 vs 本单元 MA-02/MA-03 P1）；②shell 族（C4 复核族案 P1 vs C6/C8 复核条件化 P2）。无论走向，族成员关系与单 diff 修复范围不变（四方复核一致确认）。

---

## 二、缺陷族结构（MA-01 起）

### MA-01 pathQueues 排队链挂死族 — **P1**

- **根因**：`ToolManagerImpl.callTool`（ToolManagerImpl.java:101-123）在 `CHM.compute` 锁内经已完成的 trigger 同步执行 `executeNow.get()`；返回给调用方的手工 `result` future 的完成契约依赖 `whenComplete` 注册成功——`executeNow` 同步抛出（executor null NPE / 池 REE）时 `result` 永不完成，调用方静默悬挂；同步执行器装配（CLI `SyncThreadPoolExecutor.INSTANCE`）下全执行在 bin 锁内；同 path 嵌套派发触发 CHM 递归更新。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C1-001（**主登记**） | P1 | 新发现 | 锁内执行 + 重入 + 悬挂三面并集；生产装配补强（CLI Sync / ChatBi null executor / Builder 无默认值） |
  | C4-005（并案关闭） | P1 | 新发现 | 排队悬挂面 + JDK 25 实测修正（首个调用同样静默悬挂，缺陷面更宽）；修复注意：lambda 内重抛无效，必须 `completeExceptionally` |
- **修复点**（单 diff）：`executeNow.get()` 移出 compute + `result` 完成契约兜底（try/catch → `completeExceptionally`）+ `orTimeout` 兜底评估；同批消除 (a)(b)(c) 三面。
- **族严重度**：P1（C4 复核实证修正后两成员均 P1 维持）。

### MA-02 sandbox reader 泄漏族（含快照撕裂面） — **P1**

- **根因**：Host/DockerBashSandbox 每命令新建 daemon reader 线程阻塞在进程管道 `InputStream.read()`（不可中断）；`awaitReader` 有界 join + interrupt 对流读无效；`&` 孪生子持有 stdout 管道写端时 EOF 永不到来——线程 + fd + 缓冲每次泄漏；同触发链下 `drainStream` finally 向 `capturedRef` 发布**活 StringBuilder**，调用线程/心跳线程对活对象 `toString()` 与读者 append 交叠 → SIOOBE/UTF-16 撕裂。stdin 管道默认 PIPE 从不关闭。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C6-001（**主登记**） | P1 | 新发现（站点有 plan 2285/WI4 修复史，本条为残余缺口面） | 面集最全：reader 泄漏 + fd + stdin 管道 + 取消路径不关流 |
  | C4-001（并案关闭） | P1 | 新发现 | 并案校准升级论证（`server &` 高频负载 + 每次一线程一 fd）；独有贡献：同名线程不可观测 |
  | C6-008（成员，裁-4） | P1 | 新发现 | 终态活引用发布竞态（快照撕裂/AIOOBE），同 diff |
- **修复点**（单 diff）：reader 终止闭环（关流强制 EOF / 宽限后 destroyForcibly）+ `redirectInput(NUL)` + finally 发布不可变快照 + 线程唯一命名（NamedThreadFactory 语义）。
- **族严重度**：P1（三成员均 P1）。判级分歧：vs WI 3.1 MA-17（§五-1）。

### MA-03 docker 清理族（容器/CLI 滞留 + 取消句柄注销） — **P1**

- **根因**：`DockerBashSandbox` 超时/中断分支只 `killContainer`，从不 `destroyForcibly()` 本地 `docker run` CLI；`killContainer` 两步清理全部 `catch Exception ignored`——失败无痕；finally `tracker.unregister` 撤销唯一取消句柄 → 清理失败后容器成为不可寻址永久残留，且容器无任何侧超时（无 `--stop-timeout`/reaper）。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C6-002（**主登记**） | P1 | 新发现 | 容器无界残留 + 取消句柄注销 + CLI leg |
  | C8-009（收编成员，裁-6） | P1 | 新发现（宿主站点） | 本地 CLI 进程 + reader + 缓冲泄漏面（reader 面归 MA-02、waitFor 面归 MA-04）；对照 HostBashSandbox.killTree 已修实例，F3 残留独立支撑 |
- **修复点**：超时分支补 `process.destroyForcibly()`（先杀 CLI 或兜底）+ killContainer 失败留痕（WARN/计数）+ 容器侧兜底（`--stop-timeout` 或后台 reaper）。
- **族严重度**：P1（两成员均 P1）。判级分歧：vs WI 3.1 MA-17（§五-1）。

### MA-04 docker runShortCommand 挂死族 — **P1**

- **根因**：`runShortCommand` 的 drain `in.read()` 无超时且先于 `waitFor(5s)`——docker daemon 挂死时清理线程（工具线程或心跳/取消线程）永久阻塞在 read，`waitFor(5s)` 永远不可达；waitFor 超时分支返回值忽略且无 `destroyForcibly()` 兜底。wall-clock 契约被绕过、F5 挂死对健康检测不可见。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C6-003（**主登记**） | P1 | 新发现 | drain 无时限 + waitFor 无销毁（kill 与 rm -f 两分支同修） |
  | C4-002（成员） | P2 | 新发现（Docker 变体 F3 残留，killTree 已修） | 独有面：心跳/取消线程同步占用（`cancelExecution` → killContainer 在取消线程上阻塞） |
- **修复点**（单 diff）：限时 drain（累计时限/独立泵）+ `waitFor` 超时必 `destroyForcibly()`；kill 与 rm -f 两分支覆盖；心跳占用面随同 diff 消除。C8-009 的 runShortCommand waitFor 面归本工单（裁-6）。
- **族严重度**：P1（取 C6-003 最严）。

### MA-05 shell EOF 投递族 — **P2（条件化：shell 投产/真实适配器接通即回 P1）**

- **根因**：`BlockingQueueShellOutput.close()`（:42-47）与 `BlockingQueueShellInput.sendEof()`（:50-55）用忽略返回值的非阻塞 `offer` 投递 EOF；队列满（单命令路径恰 1024 chunk 时 close 由生产线程在收集前调用——确定性队满）时 EOF 静默丢失，消费者排空后 `take()` 永久阻塞。与数据通道的阻塞 `put` 不对称。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C8-002（**主登记**，裁-2） | P2（条件化） | 新发现 | 确定性触发链口径（==1024 必满） |
  | C6-005（并案关闭） | P2（条件化） | 新发现 | 同证据；与 MA-06 组合才产生「调用方永久挂死」形态 |
  | C1-003（同站点引用） | P2 | 新发现 | 最早登记（竞态口径，弱于 C8 侧） |
- **修复点**（单 diff，与 MA-06 同批）：close/sendEof 阻塞投递（`put`）或读侧 `closed` 兜底判空；**同批改写 `ShellIOTest:294-300`（反向钉-1：put 变体使该测试超时红）**。
- **族严重度**：P2 条件化（三成员复核后均为 P2 条件化）。判级分歧：C4 复核族案 P1（§五-2）。

### MA-06 shell 管道死锁/弃管族案 — **P1（取最严暂计；判级分歧移交 7.1）**

- **根因**：`ShellCommandExecutor.executePipeline`（:243-286）+ `executeSimpleCommand`（:221-241）：有界(1024)队列 + 阻塞 `put` + 仅末级 drain + 「先生产后收集」拓扑 + 失败传播只沿 future 链向下游、从不上游关流——中途段失败/REE 提交中止/早退弃管/≥1024 chunk 自锁/`0<&1` 自引用拓扑五种形态均致 worker 线程永久钉死或调用方永久挂死；`cancelToken` 全程无检查点。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C6-004（**族案主登记**） | P2（条件化） | 新发现 | 主面（中途段失败弃管）+ 早退 + ≥1024 自锁；复核 T3 时序修正（纯本面=worker 钉死，调用方挂死需组合面） |
  | C4-003（REE 面收编） | **P1** | 新发现 | REE 提交中止独有面（下游从未被提交）；复核维持 P1 |
  | C8-001（随族） | P2（条件化） | 新发现 | 先生产后收集两机制（>1024 挂生产者 / 末级延迟收集） |
  | C8-003（随族） | P2（条件化） | 新发现 | 下游死亡→上游 put 阻塞（末级分支表述已修正） |
  | C8-011（随族，裁-3） | P2（条件化） | 新发现 | `0<&1` FD_INPUT 自引用确定性挂死；其余 FD 组合静默忽略面同修 |
- **修复点**（单 diff 盖五面）：提交循环 try/catch REE → cancel+close 已提交项；任一 stageFuture 异常即关闭全部中间队列并向上游传播取消；全 stageFutures 挂 whenComplete 观察异常（F9）；早退分支先取消已提交段；收集与生产并行化；cancelToken 检查点；`0<&1` 显式拒绝 + 其余 FD 组合补齐。
- **族严重度**：**P1**（取 C4-003 复核维持的最严值）；C6/C8 复核条件化 P2 分歧登记 7.1（§五-2）。前提注记：默认装配（ExternalCommandAdapter 为 UOE 桩、内建命令 ≤1 chunk）下触发面休眠，真实外部适配器接通即普通负载可达。

### MA-07 pathQueues F4/过载族 — **P2**

- **根因**：`ToolManagerImpl.pathQueues`（:32-33, 101-121）只增不删（唯一写点 compute，无任何 remove/evict）+ 无容量上限/溢出策略/排队超时/积压指标——entry 随历史路径基数单调增长（F4），同路径洪峰深链无界积压，队头堵塞不可诊断。完成后的旧链节可 GC（复核勘误），驻留 = path key 集合 + 在途深链窗口。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C8-006（**主登记**，建议网已定） | P2 | 新发现 | 过载语义 + 容量护栏 + 指标 + 驱逐方案面最全；生产接线不降档 |
  | C6-006（归并成员） | P2 | 新发现 | F4 生命周期视角成员 |
- **修复点**（单 diff）：尾完成且无新等待者时 `remove(pathKey, tail)`（防 ABA）+ QueueOverflowPolicy.FAIL 护栏（per-path 挂起阈值）+ 排队超时评估 + `size()`/max 链长指标；同批核对 C1-004 的会话清理挂钩面（CL-001/CL-005 归并本族）。
- **族严重度**：P2（两成员均 P2）。生产接线（nopToolManager bean + AgentToolDispatcher 生产调用）。

### MA-08 shell 会话状态守卫族 — **P2**

- **根因**：shell 会话模型以「单命令在飞」单线程契约为前提，`&` 后台 job 与 pipeline 打破该前提，而会话状态载体全部无同步、契约无运行时守卫（F10 族）：`exportedEnv`（裸 HashMap）/`currentWorkingDir` 三类线程并发读写 + 组/子 shell 快照恢复交叠；`ListShellInput.readIndex` 非原子 check-then-act（`FileShellInput.position` 同族）；`closed` 死标志 use-after-close 不设防（同族孤例）。
- **成员清单**：
  | 成员 | 最终严重度 | 现状 | 贡献面 |
  |---|---|---|---|
  | C1-002（**主登记**） | P2 | 新发现 | exportedEnv/cwd 三写面竞态（条件：嵌入方多线程驱动或接线 sink，复核降档 P1→P2） |
  | C1-009（成员，裁-5） | P2 | 新发现 | readIndex 双消费者丢行/重复（条件同 C1-002） |
  | C1-008（成员，裁-5） | P3 | 新发现 | closed 死标志（随守卫批次顺带） |
- **修复点**（同批守卫方案）：exportedEnv 换 CHM + cwd volatile + 快照恢复持同一 stateLock；`ListShellInput.read()` 加 synchronized / 游标 AtomicInteger 化（含 FileShellInput.position）；`execute()` 入口 closed 守卫（125/error 语义）；`&` job 与前台共享会话状态的语义文档化。
- **族严重度**：P2（取最严；降档条件解除即恢复 P1 级——C1 复核登记）。

---

## 三、独立立案清单（17 条）

| # | 工单 | 严重度 | 一句话 | 批次/关联注记 |
|---|---|---|---|---|
| 1 | C1-004 | P2 | UpdateTodosExecutor static todoLists 的 clear/write 复合操作竞态（同会话并行丢更新，向量=agent 引擎 fan-out）；会话条目不清理 F4 面与 MA-07 同族登记 | 生产接线（ai-toolkit-defaults.beans.xml）；CL-014/CL-015 并入本工单 |
| 2 | C1-005 | P2 | DeltaVFS delta 目录非原子写（同仓库原子替换模式漏修兄弟）+ 扩展面 DslToolImpl/AiFileTool 经 ResourceHelper.writeText 就地写（4 写点，生产请求可达） | 照抄 atomicWriteText 模式；mavenvfs 核心子面休眠条件化不降档（扩展面可达） |
| 3 | C1-006 | P2 | 两公共注册表（DefaultToolExecutorProvider.executors / ShellCommandRegistry.commands/aliases）非线程安全 Map 承载热路径读 + 运行时注册 API 契约（当前零生产写方） | **7.1 裁决修复方向**：CHM 替换 vs 声明边界+运行时守卫（对照 WI 2.4 CL-415 watch 判例，§五-3） |
| 4 | C1-007 | P2 | ToolPathAffinity.extract 单源读 input JSON：XML 节点派发绕过 per-path 串行化保护（当前并发 XML 派发无生产调用方，条件化附注） | 单点补双源回退 + 回归测试；与 TestToolPathQueue degradation 措辞同批复核 |
| 5 | C1-010 | P3 | SequentialThinkingBizModel.processThought 的 addThought→getAllThoughts 跨两锁域：并发 clearHistory 使「添加成功」与分析输入撕裂（派生视图，无持久化损坏） | **7.1 裁决**：addAndList 单锁域复合方法 vs javadoc 声明最终一致（§五-4）；生产接线 |
| 6 | C4-004 | P2 | pipeline 阶段与全平台任务共池（缺省 globalWorker core=4/max=30/queue=10000）无隔离：饥饿/延迟放大（永久死锁主张按复核 FIFO 论证不成立，只主张可达饥饿） | 装配点隔离；与 WI 1.2 MA-06 / WI 3.1 C4-011 修复批次对齐评估（判级差异已论证）；CL-002 并入（饥饿面） |
| 7 | C4-006 | P2 | `&` 后台 job body 以 join() 阻塞在 sink 线程等 executor 阶段链：sink 池身份无守卫，同池化即自死锁（零生产接线条件面） | 修复三选一留工单设计（javadoc 契约+运行时守卫 / SPI CompletionStage 重载 / SyncExecutor）；与 WI 1.5 MA-03 同池嵌套族批次对齐，不并案 |
| 8 | C4-007 | P2 | BashExecutor 后台路径 submit→run 无回滚：run 抛 UOE 时 taskId 永久滞留 RUNNING（F2 半个成功 + 账本无界） | try/catch + complete(FAILED) 回滚 / supportsRun 前置守卫；agent 侧 BackgroundRegistryJobSink 同族面移交 agent 批次核对 |
| 9 | C4-008 | P2 | 取消链覆盖面：排队路径手工 result future 与执行链断链（cancel 假确认）+ 16/17 executor 不读 cancelToken；复核欠报修正：经 manager 返回 future 的取消任何路径都不达池任务 | 与 CL-012（jq 注册窄窗）同 diff；WI 3.1 MA-08 伞族双轨登记零重复；WI 2.1 api-core bindCancel 单点改造协同点；CL-017 并入 |
| 10 | C6-007 | P2 | ThoughtStorage 会话文件无删除路径：clearHistory 只覆写空内容 + headerless 请求铸造一次性 UUID 会话 → 存储目录文件数无界增长（F4） | clearHistory 改删文件 + TTL 清扫 + headerless 拒绝落盘（headerless 放大面依赖认证配置，留 7.1 注记）；CL-018 并入；与 WI 3.1 MA-21 同族不同站点 |
| 11 | C6-009 | P2 | BashExecutionTracker.cancelExecution remove-before-act + 销毁无 UOE/SecurityException 守卫：取消半途失败后注册项已丢失，取消不可重试、返回值语义混淆 | act-then-remove/回填 + 照抄 killTree 守卫模式；建议(3) 宽口径兜底为语义变更决策留 7.1（§五-5）；与 WI 3.1 C3-011(MA-08) 上下游同链两面零重复 |
| 12 | C8-004 | P2 | 消费线程中断被映射为「干净 EOF」：输出静默截断无标记（与 BlockingQueueShellInput 抛异常语义不一致） | CL-008② 处置以本条为准（C4-CL-008② 类名误写已勘误）；shell 零生产接线条件面登记 |
| 13 | C8-005 | P2 | executeParallel 信号量：callTool 同步抛出时 whenComplete 从未挂接 → permit 永久泄漏 + 无界阻塞 acquire（强休眠面：executeParallel 零生产调用方） | 泄漏面补强（L122 直发 NPE 同样可达）；CL-003/CL-013 并入；接线即达条件登记 |
| 14 | C8-007 | P2 | http-request / graphql-query 响应体 `getBodyAsString()` 无上界全量物化入工具输出（同模块 bash 1MiB / read-file 100K / skill 4000 三先例对照，F3 一致性缺口） | 截断-续排空模式 + `@InjectValue` 配置化；graphql 错误/成功双路径同修；生产 bean 不降档 |
| 15 | C8-008 | P2 | 文件类工具输出上界可被调用方参数绕过（grep/glob 上限直通、read-file 行区间无总量帽、patch-file 全量载入、readFiles `<=0 means no limit`；ApplyDelta 已修实例为对照模板） | 7 失效面服务端 clamp 单批；ApplyDelta 已有帽补回归测试同批（成本极低） |
| 16 | C8-010 | P2 | `>` 重定向绕过 1024 chunk 队列上界：FileShellOutput 无界 StringBuilder 缓冲至 close 才落盘（峰值 2× 拷贝） | 与 C8-012 同批修（分块落盘 + 字节帽）；行为性缺陷单线程不豁免口径核验通过 |
| 17 | C8-012 | P2 | 输出边界仅 chunk 计数有界、字节无界：readLine/readAllText 聚合缓冲无帽 + 单巨 chunk（FileShellInput）+ SafeLineReader 有帽模板在位未复用 | 与 C8-010 同批修；与 CL-010 同源不同面（修复动作不同）不重复立项、同批核对 |

---

## 四、watch-only 清单

**发现级：0 条。** 39 条保留发现全部落位于缺陷族（22 条）或独立立案（17 条），无发现落入 watch-only。

watch 级登记仅存在于以下非发现载体（不占三态计数，随属主工单/台账携带）：

1. **反向钉 watch**：`TestToolPathQueue.java:203-228` 的「pathless → 不排队（documented degradation）」措辞——若未来将 pathless 调用改为入队或 loud-fail 须同步改写（随 C1-007 工单与 C10-023）。
2. **C10 口径 watch**：假保护/无测试分档边界口径不统一（C10 复核 watch-only 1 项）——后续批次沿用矩阵时统一为「缺陷触发路径是否有任何测试触达」。
3. **CL 台账 watch 候选**：CL-019（progressSnapshot 全量拷贝性能面）——量化后定级（§八）。

---

## 五、开放裁定与 7.1 移交项

| # | 事项 | 分歧内容 | 本裁定立场 |
|---|---|---|---|
| 1 | **沙箱族判级分歧（跨单元）** | WI 3.1 MA-17（nop-ai-agent DockerSandboxBackend，族判 P2）vs 本单元 MA-02/MA-03（P1）——两套实现同病（超时不 destroy / interrupt 对管道 read 无效 / kill 失败滞留），两实现仓内同为 opt-in/零生产实例化，本单元按「配置可达生产路径」判级 | 维持 P1 暂计；7.1 按「装配后生产形态」统一两单元判级；零重复立项（不同类不同模块），修复批次协同、护栏测试共享形态（3.1 C10-011） |
| 2 | **shell 族判级分歧（单元内跨维）** | C4 复核维持族案 P1（C4-003）vs C6/C8 复核条件化 P2（C6-004/005、C8-001/002/003/011）——分歧源于零生产接线事实 vs 注入小池+真实适配器常态可达判断 | MA-06 按取最严规则**暂计 P1**（C4-003 成员维持）、MA-05 暂计 P2 条件化；7.1 按装配后生产形态统一；无论走向，族成员关系与单 diff 修复范围不变 |
| 3 | **C1-006 修复方向** | CHM 替换（成本极低）vs javadoc 收窄+运行时断言守卫（F10）——对照 WI 2.4 CL-415（同类形态裁 watch）判例 | 工单保留（独立立案），修复方向 7.1 逐项裁决；C10-019 条件性保护登记随裁决联动 |
| 4 | **C1-010 修复方向** | ThoughtStorage 增 addAndList 单锁域复合方法 vs javadoc 声明「分析输入为最终一致快照」 | 工单保留（独立立案），7.1 裁决；C10-020 条件性保护登记随裁决联动 |
| 5 | **C6-009 建议(3) 语义变更** | cancelAction 的 catch(RuntimeException)+rethrow 现状是响亮失败；「宽口径兜底」实为语义变更决策 | 缺陷本体收窄为 remove-before-act + 守卫缺失（复核已裁）；兜底方案 7.1 决策 |
| 6 | **C6-007 headerless 放大面** | 依赖端点认证暴露配置 | P2 维持；暴露配置核查注记随工单 |
| 7 | **MA-05 修复变体** | 阻塞 `put` vs 读侧 closed 兜底 vs 先排空后关闭 | 受反向钉-1 约束（put 变体须同批改写 ShellIOTest:294-300）；变体选择属工单设计，非 7.1 事项 |

---

## 六、权威汇总表

**合并统计：39 条 → 25 工单 = 缺陷族 8 个（MA-01…08）吸收 22 条 + 独立立案 17 条 + watch-only 0 条。**

**工单严重度分布：P0×0 / P1×5（MA-01/02/03/04/06）/ P2×19（MA-05/07/08 + 独立 16）/ P3×1（C1-010）。**

（工单级 P1 收敛说明：合并前 P1×9 条全部落位于 5 个 P1 族工单——MA-01 吸收 C1-001+C4-005、MA-02 吸收 C6-001+C4-001+C6-008、MA-03 吸收 C6-002+C8-009、MA-04 承载 C6-003、MA-06 吸收 C4-003。）

### 逐条落位表（39 条 → 25 工单，无遗漏无重复）

| 原条目 | 维度 | 复核终判 | 落位 |
|---|---|---|---|
| C1-001 | C1 | P1 | MA-01（主登记） |
| C1-002 | C1 | P2 | MA-08（主登记） |
| C1-003 | C1 | P2 | MA-05（成员，同站点引用） |
| C1-004 | C1 | P2 | 独立工单 |
| C1-005 | C1 | P2 | 独立工单 |
| C1-006 | C1 | P2 | 独立工单（7.1 裁修复方向） |
| C1-007 | C1 | P2 | 独立工单 |
| C1-008 | C1 | P3 | MA-08（成员） |
| C1-009 | C1 | P2 | MA-08（成员） |
| C1-010 | C1 | P3 | 独立工单（7.1 裁修复方向） |
| C4-001 | C4 | P1 | MA-02（成员，并案关闭） |
| C4-002 | C4 | P2 | MA-04（成员，心跳占用面） |
| C4-003 | C4 | P1 | MA-06（成员，REE 面收编） |
| C4-004 | C4 | P2 | 独立工单 |
| C4-005 | C4 | P1 | MA-01（成员，并案关闭） |
| C4-006 | C4 | P2 | 独立工单 |
| C4-007 | C4 | P2 | 独立工单 |
| C4-008 | C4 | P2 | 独立工单 |
| C6-001 | C6 | P1 | MA-02（主登记） |
| C6-002 | C6 | P1 | MA-03（主登记） |
| C6-003 | C6 | P1 | MA-04（主登记） |
| C6-004 | C6 | P2 条件化 | MA-06（族案主登记） |
| C6-005 | C6 | P2 条件化 | MA-05（成员，并案关闭） |
| C6-006 | C6 | P2 | MA-07（成员，归并） |
| C6-007 | C6 | P2 | 独立工单 |
| C6-008 | C6 | P1 | MA-02（成员，同 diff） |
| C6-009 | C6 | P2 | 独立工单 |
| C8-001 | C8 | P2 条件化 | MA-06（成员） |
| C8-002 | C8 | P2 条件化 | MA-05（主登记） |
| C8-003 | C8 | P2 条件化 | MA-06（成员） |
| C8-004 | C8 | P2 | 独立工单 |
| C8-005 | C8 | P2 | 独立工单 |
| C8-006 | C8 | P2 | MA-07（主登记） |
| C8-007 | C8 | P2 | 独立工单 |
| C8-008 | C8 | P2 | 独立工单 |
| C8-009 | C8 | P1（并案成员） | MA-03（成员，收编；面级拆分见裁-6） |
| C8-010 | C8 | P2 | 独立工单 |
| C8-011 | C8 | P2 条件化 | MA-06（成员） |
| C8-012 | C8 | P2 | 独立工单 |

**逐条落位声明**：39/39 恰好落位一处（8 族 22 条 + 独立 17 条），无遗漏、无重复计数、无悬空；与四维度 `## 最终保留项` 及 C10 矩阵对账（39 行 = C1×10 + C4×8 + C6×9 + C8×12）零差异。

---

## 七、跨单元对账

### 7.1 P1 台账查重（cra-index WI 1.1–3.1 累计 117 条）

四维度复核已各自对台账逐条比对：**本单元 9 条 P1 与台账零同站点重复**（C1/C4/C6/C8 四方复核结论一致，本裁定合并后复核无新增重叠——9 P1 收敛的 5 个族工单站点 ToolManagerImpl:101-123、Host/DockerBashSandbox、ShellCommandExecutor.executePipeline、BlockingQueueShell* 均未在台账出现）。

### 7.2 家族级关联（零重复立项，单列 9 处）

| # | 本单元 | 跨单元 | 关系 | 处置 |
|---|---|---|---|---|
| 1 | MA-02/MA-03（toolkit 沙箱族 P1） | WI 3.1 MA-17（agent DockerSandboxBackend，P2） | 同病不同实现（javadoc 自认复用 pattern） | 判级分歧移交 7.1；修复批次协同；零重复 |
| 2 | C4-008 + C6-009 | WI 3.1 MA-08（C3-011 超时取消传播断链）| 双轨登记：工具侧结构面（本单元）vs 引擎侧编排面（3.1） | 零重复；协同点 = WI 2.1 api-core bindCancel 单点改造 |
| 3 | C4-006（sink 池身份条件面） | WI 1.5 MA-03（sync+executor 同池嵌套死锁族 P1） | 同形态不同站点（彼为实际缺省同池装配） | 修复批次对齐登记，不并案 |
| 4 | C4-004（共池无隔离 P2） | WI 1.2 MA-06 + WI 3.1 C4-011（共享池阻塞族 P1） | 判级差异已论证（彼为无限/300s 阻塞任务实际占池） | 家族关联，P2 维持 |
| 5 | C1-006（注册表运行时注册） | WI 2.4 CL-415（SqlDataTypeMapping.register，watch） | 同形态判例 | 7.1 逐项裁决对照 |
| 6 | MA-07 + C1-004-F4 面（pathQueues/todoLists 只增不删） | WI 3.1 C5-015（MA-03）F4 引擎终态清理族、WI 1.2 MA-03、WI 1.6 C7-001、WI 1.7 C7-003 | 同族不同站点 | 台账关联，修复批次可对齐保留策略方案，零重复 |
| 7 | C6-007（ThoughtStorage 会话文件无回收） | WI 3.1 MA-21（聊天日志保留期族 P2） | 同族不同站点 | 台账关联，P2 校准一致 |
| 8 | C4-001（裸 `new Thread` + 同名 reader） | WI 2.1 CL-021（裸线程绕过平台线程设施族） | 新增点位 | 移交 WI 2.1 CL-021 台账补登 |
| 9 | MA-05/MA-06（有界队列挂死族/背压旁路） | WI 1.1 C4-001（有界队列互等，不同设施）、WI 2.3 C3-003（BufferedStreamingPublisher 背压断裂）、WI 3.1 MA-01（流式主链聚合计无界） | 形态家族 | 台账关联，零重复 |

**跨单元关联合计：家族级 9 处单列 + 判级分歧 2 组移交 7.1；同案重复立项 0。**

### 7.3 同族历史修复对照（单元内既有修复先例，非台账在案）

- `HostBashSandbox.killTree`（waitFor+destroyForcibly）= MA-03/MA-04 修复模板，Docker 变体未同修（F3 残留形态）；
- `LocalToolFileSystem.atomicReplace` / `ThoughtStorage.atomicWriteText` = C1-005 原子替换模板；
- `BashExecutionTracker` register/unregister finally 配对 = MA-07 驱逐方案模板；
- `ApplyDeltaExecutor` 1M/10M 帽 = C8-008 clamp 模板（自身无回归测试，随 C10-010 补）。

---

## 八、CL 线索处置表（CL×20 全落位）

| 线索 | 归维 | 处置 | 落位工单/说明 |
|---|---|---|---|
| CL-001 | C5/F4 | **并入工单** | MA-07（pathQueues 完成尾不驱逐 = C8-006 主登记同站点） |
| CL-002 | C3 | **并入工单**（部分主张修正） | C4-004（饥饿/隔离面）；「全部线程互等永久死锁」主张按 C4-004 复核 FIFO 相邻提交论证不成立，不随并入复活 |
| CL-003 | C2 | **并入工单** | C8-005（submitBounded 阻塞 acquire 同站点，平台 HighWatermarkSemaphore/异步排队修复方向一致） |
| CL-004 | C9/C6 | **并入工单**（跨两工单） | MA-02（reader interrupt 不敏感面）+ MA-03（docker kill 尽力而为累积链）；主处置 MA-02 |
| CL-005 | C7/F4 | **并入工单** | MA-07（三登记之一；「链式持有完整工具结果」内存放大面按 C8 复核勘误——完成旧链可 GC——随 C7 定界注记） |
| CL-006 | C2 | **并入工单** | MA-08（exportedEnv 面，随 C1-002） |
| CL-007 | C3 | **独立线索**（台账登记，不计工单） | GraphqlQueryExecutor 调用线程同步 DNS（:78-97）；对照 HttpRequestExecutor:97 整体提交为同族正确形态；无维度复核反对，留 C3/协调方定级 |
| CL-008 | C9 | **并入工单**（拆两子面） | ①非末位阶段异常被吞 → MA-06（修复建议「全 stageFutures 挂 whenComplete」覆盖）；②中断被解释为 EOF → **以 C8-004 为准**（C4-CL-008② 类名误写已勘误），并入 C8-004 工单 |
| CL-009 | C2 | **并入工单**（exportedEnv 三登记**主登记**，建议网已定） | MA-08（C1-002 主工单）；CL 台账主登记 = 本条（面最全：whenComplete 写回点 + 裸 HashMap + getExportedEnv 活 map 逃逸 CME + 快照互踩） |
| CL-010 | C5 | **独立线索**（台账登记） | FileShellInput 构造期无上界全量读入（`< 大文件`）；与 C8-012 同源不同面、修复同批核对；留 C5/协调方定级 |
| CL-011 | C9 | **并入工单** | MA-08（closed 死标志 = C1-008 成员面同 diff） |
| CL-012 | C9 | **并入工单** | C4-008（jq 取消监听注册窄窗，同 diff——C4 复核已登记「CL-012 并入 C4-008 修复 diff」） |
| CL-013 | C2/C5 | **并入工单** | C8-005（submitBounded 阻塞 acquire，与 CL-003 同站点） |
| CL-014 | C7/F4 | **并入工单** | C1-004（todoLists 只进不出 = C1-004 的 F4 面，同条本体） |
| CL-015 | C3 | **并入工单** | C1-004（handleWrite getOrDefault→put 丢单更新 = C1-004 缺陷本体同一站点） |
| CL-016 | C2 | **并入工单**（exportedEnv 三登记别名） | MA-08（CL 台账归并至主登记 CL-009） |
| CL-017 | C5 | **并入工单** | C4-008（fs 族不读 cancelToken/expireAt = 17 executor 取消能力对账范围内，失效面已含 fs 族 10 executor） |
| CL-018 | C7/F4 | **并入工单** | C6-007（ThoughtStorage 会话文件无 TTL = 同条本体） |
| CL-019 | C9/C7 | **独立线索**（watch 候选，量化后定级） | progressSnapshot 每心跳全量拷贝 1MiB 再截尾；分配浪费非正确性缺陷，与 MA-02 同文件不并入修复面 |
| CL-020 | C9/F9 | **独立线索**（台账登记） | `>` 重定向写失败被 IoHelper.safeClose 静默吞（FileShellOutput.close 先置标志再 flush 使重试不可能）；与 C8-010/C8-012 同文件，修复批次对齐登记 |

**CL 处置统计**：20 条 = 并入工单 16（其中 exportedEnv 三登记收敛为 CL-009 主登记、CL-008 拆两子面分落 MA-06/C8-004）+ 独立线索 4（CL-007/010/019/020）+ 驳回 0。

---

## 九、C10 缺保护登记映射（23 条 → 随本体工单，不独立立项）

| C10 登记 | 覆盖成员（P1 承载加粗） | 映射工单 |
|---|---|---|
| C10-001 | **C6-004**、C8-001、C8-003、**C4-003**、C8-011 | MA-06 |
| C10-002（含反向钉-1 改写义务） | C8-002、C6-005、C1-003 | MA-05 |
| C10-003 | C8-006、C6-006 | MA-07 |
| C10-004 | **C1-001**、**C4-005** | MA-01 |
| C10-005 | **C6-001**、**C4-001**、**C6-008** | MA-02 |
| C10-006 | **C6-002**、**C6-003**、C4-002、**C8-009** | MA-03 + MA-04（共享 CommandRunner 注入接缝，一缝两单） |
| C10-007 | C1-002、C1-009、C1-008 | MA-08 |
| C10-008 | C8-010、C8-012 | 独立 #16 + #17（同批） |
| C10-009 | C8-007 | 独立 #14 |
| C10-010 | C8-008（含 ApplyDelta 已修无测补测义务） | 独立 #15 |
| C10-011 | C4-004 | 独立 #6 |
| C10-012 | C4-006 | 独立 #7 |
| C10-013 | C4-007 | 独立 #8 |
| C10-014 | C4-008（17 executor 取消矩阵 + wrapper 传播断言） | 独立 #9 |
| C10-015 | C8-004 | 独立 #12 |
| C10-016 | C8-005 | 独立 #13 |
| C10-017 | C1-004 | 独立 #1 |
| C10-018 | C1-005 | 独立 #2 |
| C10-019（条件性） | C1-006 | 独立 #3（随 7.1 裁决联动） |
| C10-020（条件性） | C1-010 | 独立 #5（随 7.1 裁决联动） |
| C10-021 | C6-007 | 独立 #10 |
| C10-022 | C6-009 | 独立 #11 |
| C10-023 | C1-007 | 独立 #4 |

**映射声明**：23/23 登记全部映射至本体工单，零独立立项、零悬空；9 条 P1 缺陷的保护缺口由 C10-001/002/004/005/006 五条登记承载（对应 5 个 P1 族工单），每张工单随单至少携带一条保护登记义务；C10 复核 3 处证据修正项（C1-008 证据文件错标 / 统计句条数 / grep 精度）不改变映射关系。

---

## 附：裁定纪律声明

- 未修改任何原发现条目正文与各维度复核结论；本文件为裁定层唯一新增产物。
- 主登记建议网全部采纳为已定事实；本裁定仅新增 3 处成员范围收敛（裁-3/4/5：C8-011 入 MA-06、C6-008 入 MA-02、C1-008/009 入 MA-08）与 2 处归属终裁（裁-1/2：EOF 独立族、C8-002 主登记），每处均以维度复核原文 + C10 单保护面佐证。
- 判级一律沿复核终判，本裁定零改判；族严重度取成员最严仅影响工单级计数，不改任何单条判级。
- cra-index 更新（报告清单行 + WI 3.2 CL 台账节）与 roadmap/plan 回填属裁定后继步骤，不在本文件范围。
