# C1 深挖第 2 轮 — nop-ai-toolkit-tools-shell（WI 3.2）

- 审计日期：2026-10-20（深挖第 2 轮，合并批 C1/C4/C6/C8）
- 定位：第 1 轮 C1 共 8 条（C1-001…C1-008）集中于 ToolManagerImpl/shell 会话状态；本轮按派发单指定的邻接面深挖，编号续接 C1-009。

## 深挖范围与方法

第 1 轮发现的邻接面 + 派发单指定的五个深挖方向逐面核毕，结论如下（file:line 均为当前 live code）：

| # | 指定深挖面 | 核对方法与结论 |
|---|---|---|
| 1 | mavenvfs 缓存/索引并发面 | 逐文件读 `mavenvfs/vfs/DeltaVirtualFileSystem.java`（全文 325 行）、`DeltaWorkspaceReader.java`、`cli/VfsMavenCli.java`、`config/DeltaWorkspaceReaderConfigurator.java`。**无缓存、无索引、无实例可变字段**：`baseDir/deltaDir` 为 final（:38-39），文件查找每次直达文件系统（getFile:101-125）；`VfsMavenCli.execute` 为 stub（:129-142 仅拼命令字符串返回，注释明示"实际执行需要使用ProcessBuilder"）。唯一共享可变状态是 delta 目录文件内容——非原子写已在第 1 轮 C1-005 立案。本轮无新发现。 |
| 2 | 沙箱会话注册表（BashExecutionTracker） | 全文核对 `sandbox/BashExecutionTracker.java`（99 行）：`inFlight` 为 CHM（:36）；register/unregister 在两个 sandbox 的 execute finally 配对（Host:101-110 / Docker:114-124）；`AtomicReference<StringBuilder>` 发布快照（progressSnapshot:63-75）。javadoc（:38-43）明文声明 callId 由提供方保证唯一、"replacement only occurs on caller misuse"——callId 冲突下 unregister 摘错条目属**已声明边界**（误报校准第 6 条），不立项。 |
| 3 | tool 注册表运行时变更 | `DefaultToolExecutorProvider.executors` / `ShellCommandRegistry.commands/aliases` 的非线程安全 Map 已在第 1 轮 C1-006 立案（P2，理论风险）。本轮 grep 复核：`registerTool` 仍无生产调用方、`registerCommand/registerAlias` 仅测试/装配可达，无新增兄弟面。 |
| 4 | tool 的执行上下文对象跨线程共享 | `ToolExecuteContext` 全字段 final、构造后不可变（:21-38）；`envs` 经 `Collections.unmodifiableMap` 包装（:32）。`AiToolCall` 无懒解析缓存（:7-138，attr*/child* 每次直达 node）。`AiToolCallResult` 每次调用新建。跨线程共享的 context 本体安全；共享问题集中在 executor 实例字段（第 1 轮已立案）与本轮 C1-009 的 stdin 游标。 |
| 5 | GraphqlQuery/Jq 等执行器实例状态 | 17 个 executor 逐一类体核对：可变实例字段仅 setter 注入的装配依赖（`GraphqlQueryExecutor.httpClient/dnsResolver` :28-35、`HttpRequestExecutor` :39-48、`SearchEngineExecutor.searchEngine` :29、`BashExecutor.sandbox` :80）——第 1 轮口径 2 已逐一降档论证（IoC 装配期单线程写、请求期只读、全仓无运行时 setter 调用）。`JqToolExecutor`/`SkillExecutor`/`ReadRefExecutor`/`AskOracleExecutor`/fs 族 executor **完全无状态**。`SsrfAddressGuard`/`SsrfGuardDnsResolver` 仅 static final 不可变集合（SsrfAddressGuard:33-37）。无新发现。 |

追加核对：static 可变字段全根复扫（第 1 轮清单外无新增）；`nop-ai-shell` 的 `DefaultShellExecutionContext`（per-command 新建，:29-60）、`CdCommand`（mutation 落在被丢弃的 per-command 实例上，:29-56，会话 cwd 由 ShellCommandExecutor.updateContextFromResult 维护）无跨调用共享。ThreadLocal 全根仍零命中。

**共享可变状态清单表（本轮新增/变动行）**：

| 载体 | 字段/对象 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| 实例字段（每输入流） | `ListShellInput.readIndex`（int 游标） | 前台命令线程 + `&` job sink 线程（并发消费同一 context.stdin()） | 同左 | **无**（`chunks.get(readIndex++)` 非原子） | → C1-009 |
| 实例字段（单例 biz） | `SequentialThinkingBizModel.storage` 背后的会话文件 | addThought（锁内）与 getAllThoughts（另一次加锁） | analyzer | 两次独立 lock 域，复合操作无原子性 | → C1-010 |
| 文件系统即共享状态 | `DslToolImpl.saveDslFile` / `AiFileTool` 写路径 | 各请求线程 | 各读者 | **无**（平台 `ResourceHelper.writeText` 就地写） | 扩展 C1-005（已立案缺陷族的兄弟站点落定） |

---

## 新增发现

### [C1-009] ListShellInput 读取游标非原子：`&` 后台 job 与前台命令并发消费同一 stdin 时行丢失/重复

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/ListShellInput.java:20-27, 29-32`；并发消费接线点 `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:363-365`
- **证据片段**:
  ```java
  // ListShellInput.java:20-27
  private int readIndex = 0;
  @Override
  public ShellChunk read() {
      if (isClosed()) return null;
      if (readIndex >= chunks.size()) return null;      // check
      ShellChunk chunk = chunks.get(readIndex++);       // act —— 非原子 check-then-act
      ...
  ```
- **严重程度**: P2
- **并发场景**: `executeBackground`（ShellCommandExecutor.java:363-365）把 job body（`executeExpression(background.inner(), context, ...)`）fork 到 sink 线程，body 内 SimpleCommand 与前台继续执行的命令**并发调用 `context.stdin().read()`**——stdin 是引擎注入的会话级 `IShellInput`，`ListShellInput` 即本模块为该场景提供的公开实现（`add/addText/sendEof` 生产者 API，:29-40）。
- **触发时序**: T1（前台线程）执行 `job & ; grep foo`：`&` 提交后前台立即执行 grep → grep 在 T1 上调 `read()`，通过 L23 空检查（readIndex=3）→ T2（sink 线程）job body 的首条命令同时调 `read()`，同样通过检查（仍是 3）→ 两者同取 `chunks.get(3)` 且各自 `readIndex++`（3→4、4→5，++ 丢失一次）→ chunk 4 被两个消费者各读一次（重复输入）而后续某个 chunk 被跳过（丢失输入）；`readIndex++` 的读-改-写竞争在并发下还可产生索引回退。`BlockingQueueShellInput` 变体：`queue.take()` 单次原子但两个消费者任意瓜分 chunk 序列，行在 chunk 边界被撕裂。
- **现状**: 会话输入流无"单消费者"运行时守卫（F10）；`&` job 与 pipeline 正是打破单线程会话假设的特性——与第 1 轮 C1-002 同一判定基准（会话状态共享但无同步），但载体是**输入流游标**，不在 C1-002 的失效面清单内。
- **风险**: 后台 job 场景下命令输入行静默重复/丢失——对 AI shell 语义是难复现的错误结果（grep 看到重复行、下游读到撕裂行）；无崩溃、无日志，漂移随调度时序变化。
- **建议**: (1) `ListShellInput.read()` 加 `synchronized` 或将游标换 `AtomicInteger` + `getAndIncrement` 后越界回退校验；(2) 结构性修复与 C1-002 同批：在 executeBackground 提交时对 stdin 做所有权裁定（job body 不继承 stdin，或文档声明 `&` job 的 stdin 语义并加守卫）；(3) `BlockingQueueShellInput` 文档声明"单消费者契约"。
- **失效面枚举**（F3）：三根内"有状态读取游标的 IShellInput"全清单——`ListShellInput.readIndex`（**非原子，本条**）、`FileShellInput.position`（`FileShellInput.java:24-30`，check-then-act，双消费者各得全量内容=重复消费，同族同修）、`BlockingQueueShellInput.read`（`BlockingQueueShellInput.java:23-37`，take 原子，无内存竞争，仅共享消费语义面，登记观察）、`BlockingQueueShellOutput$BlockingQueueShellInputAdapter.eofReceived`（:58-93，单消费者假设，pipeline 每级独立 adapter 实例，当前无共享消费点，通过）；`AbstractShellInput.buffer`（:19，readLine 拼装缓冲，依赖"单消费者"前提，随本条守卫一并覆盖）。
- **信心水平**: 确定（`readIndex++` 非原子是 JLS 语义；并发消费接线点在模块内代码可直接复核）
- **误报排除**: 非"看到共享字段就报线程不安全"——已给出双线程同索引的完整交错；非"低频路径"——`&` 是 WI25 显式特性且引擎以 ListShellInput 喂入是模块公开用法；亦非已声明边界——javadoc 无任何单消费者声明。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验通过（readIndex check-then-act :21-24、并发消费接线点 executeBackground:363-371、FileShellInput.position 兄弟 :24-30）；条件附注：并发消费向量依赖 IBackgroundJobSink 接线（仓内生产调用方 0）且 nop-ai-shell 全仓零生产实例化（复核证据见第 1 轮文件 C1-002 复核行）——与 C1-002 同批守卫方案修复，P2 维持（真实数据竞态非防御性冗余，条件解除即恢复并发可达）。

### [C1-010] SequentialThinkingBizModel.processThought 的 addThought→getAllThoughts 为两次独立加锁的复合操作：并发 clearHistory 使"添加成功"与分析输入撕裂（微缩 F2）

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/SequentialThinkingBizModel.java:96-99`；锁域分裂点 `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java:104-115, 117-124, 139-146`
- **证据片段**:
  ```java
  // SequentialThinkingBizModel.java:96-99 —— 两个独立加锁区间
  storage.addThought(sessionId, thoughtData);              // 锁域 1：load+add+save
  List<ThoughtData> allThoughts = storage.getAllThoughts(sessionId);  // 锁域 2：重新 load
  return analyzer.analyzeThought(thoughtData, allThoughts);
  // ThoughtStorage.clearHistory（L139-146）：锁内 saveSession(sessionId, new ArrayList<>())
  ```
- **严重程度**: P3
- **并发场景**: 单例 BizModel（`@BizModel("SequentialThinking")`）的 `processThought` 与 `clearHistory` 各自在 `ThoughtStorage` 的同一 `ReentrantLock` 内执行，但 `processThought` 内部的 add 与 read 是**两次独立加锁**，中间无原子性保障。
- **触发时序**: T1 `processThought` 完成锁域 1（thought 已持久化、对外语义"添加成功"）→ T2 `clearHistory` 抢到锁、把会话覆写为空列表 → T1 进入锁域 2 重新加载得到 `[]` → `analyzeThought(thoughtData, [])` 基于"本 thought 是唯一思考"的空上下文生成摘要/阶段统计——与刚刚持久化的真实状态（ ThoughtData 仍在文件里，但 clearHistory 已把它覆盖为空）产生不可调和的报告撕裂。反向序列（两并发 processThought）则各自读到含对方 thought 的快照，分析输入互相污染但收敛。写不出更重后果（分析结果是派生视图），故 P3。
- **现状**: ReentrantLock 每操作互斥正确（第 1 轮已裁定"设计正确，不立项"）；缺陷在**锁的粒度是操作级而非事务级**，add+read 复合语义跨越两个锁域。
- **风险**: 分析/摘要输出偶发与存储状态不一致；无数据损坏（addThought 本身持久化成功），影响面限于该工具给模型的派生反馈。
- **建议**: ThoughtStorage 增加 `addAndList(sessionId, thought)` 单锁域复合方法（持锁内 load→add→save→返回全量），processThought 改调之；或接受为已声明边界并在 javadoc 注明"分析输入为最终一致快照"。
- **失效面枚举**（F3）：同文件同族复合操作清单——`processThought` add+getAll（本条）；`generateSummary`（L104-108）单次 getAll、无复合面；`clearHistory`（L112-115）单操作；`ThoughtStorage` 内部全部公开方法均为单锁域单操作，通过。`UpdateTodosExecutor` 的 getOrDefault→put 复合（同族"跨操作无原子性"）已由第 1 轮 C1-004 立案，不重复。
- **信心水平**: 确定（锁域边界逐行可核；后果量级小故 P3）
- **误报排除**: 非"ReentrantLock 使用本身"误报——报的是复合操作跨锁域；非 F4/生命周期问题——文件增长面已由第 1 轮 C6-007 承担。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现——回码核验通过（processThought add→getAll 两独立锁域 :96-99、ThoughtStorage addThought:104-115/getAllThoughts:117-124/clearHistory:139-146 单锁域单操作）；生产接线核实（ai-tools-defaults.beans.xml 注册 SequentialThinkingBizModel bean）；后果量级（派生分析视图撕裂、无持久化损坏）与 P3 匹配。

### 扩展 C1-005 —— delta 目录非原子写族的"待核兄弟"落定：DslToolImpl / AiFileTool 写路径确认未修

第 1 轮 C1-005 的失效面枚举将 `DslToolImpl.saveDslFile` 与 `AiFileTool` 保存路径登记为"经平台 ResourceHelper.writeText（是否原子未审，待核兄弟）"。本轮核实：

- `DslToolImpl.saveDslFile` → `ResourceHelper.writeText(resource, result, null)`（`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/xdsl/DslToolImpl.java:81`）；
- `AiFileTool` 三处写点 → `ResourceHelper.writeText(resource, ...)`（`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/mcp/AiFileTool.java:152,154,157`）；
- 平台实现为**就地 Writer 直写**：`nop-kernel/nop-core/src/main/java/io/nop/core/resource/ResourceHelper.java:725-728`（`toWriter` 打开目标 → `out.write(text)` → flush），无临时文件、无原子换名——与已修的 `LocalToolFileSystem.atomicReplace` / `ThoughtStorage.atomicWriteText` 模式相反。

平台 helper 自身不重审（口径），但**使用方式**层面确认：C1-005 缺陷族的兄弟站点为未修状态（2 个类、4 个写点），应随 C1-005 同批修复。不另立新案。

---

## 维度集外线索（overlay）

（本轮 C1 深挖未产生 C2/C3/C5/C7/C9 新线索；全模块 overlay 流水号 CL-020 由 C6-round2 登记。）

---

## 继续判定

本轮新增 2 条（P2×1：C1-009；P3×1：C1-010）+ 1 条扩展证据（C1-005 兄弟站点落定）。派发单指定的五个深挖面全部核毕：mavenvfs 无缓存/索引面（stub）、沙箱注册表为已声明边界、tool 注册表无新面（C1-006 已立案）、执行器上下文不可变、执行器实例字段全为启动装配——**新增全部为 P2/P3 且深挖面有明确收敛证据**，输出 **终止信号**（C1 维度建议收敛，不再开第 3 轮）。

---

## 维度复核结论（第 2 轮条目）

- 复核人：C1 维度独立复核员（WI 3.2 Phase 4）
- **C1-009：保留(P2)｜新发现**——readIndex 非原子 check-then-act、并发消费接线点、FileShellInput 兄弟站点全部回码核实；向量条件（IBackgroundJobSink 仓内零接线 + nop-ai-shell 零生产实例化）作为附注登记，不改变判级（与 C1-002 同批修复）。
- **C1-010：保留(P3)｜新发现**——两锁域复合操作、clearHistory 撕裂时序、生产接线（beans 注册）全部核实；P3 与后果量级匹配。
- **扩展 C1-005 证据：核实成立**——DslToolImpl:81 / AiFileTool 3 写点 → ResourceHelper.writeText:718-728 就地 Writer 直写逐点回码确认；FileToolBizModel（@BizModel，bean 已注册）→ DslToolImpl 为生产接线，故该扩展面为请求可达，支撑第 1 轮 C1-005 维持 P2（未降）。
- 本轮判定与第 1 轮复核结论合并统计：C1 维度 10 条保留 10（降级 1：C1-002 P1→P2）、驳回 0；终局判定表、跨维同站点指针（C1-001↔C4-005、C1-003↔C8-002）、同案命中清单与高风险子项复核说明见 `C1-shared-mutable-state.md`「维度复核结论」节，本文件不重复。
