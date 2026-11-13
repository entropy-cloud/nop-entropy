# C3 异步链路正确性审计报告 — nop-persistence 读写热路径

- **审计对象**: nop-orm / nop-orm-eql / nop-orm-model / nop-dao / nop-orm-data（5 源码根，src/main，共约 84,484 行）
- **维度定义（C3）**: 异步组合的异常传播、取消/超时语义、上下文传播与时序假设；future 转换链断裂
- **盲审纪律声明**: 本轮为盲审。未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/ 下任何文件；仅依据 `_tmp/cra-baseline-nop-persistence-rw.txt`（C3/C4 节）与当前 live code。api-core（FutureHelper/ContextProvider/BaseContext/ContextTaskQueue）按任务书仅作对照引用，不作为发现对象。
- **批次**: 合并批 1/1（C3+C4 合并初审，单批完成）
- **判级口径**: ORM 层被全平台数据访问调用；判级沿领域校准（P1=错误/挂起路径资源泄漏、罕见但可具体化的竞态、异步链异常静默吞掉导致状态不一致等）
- **异步面盘点总览**: `grep CompletionStage` 确认异步 API 面集中在 nop-orm（persister/driver/session/loader/template）、nop-dao（txn 链）与 nop-orm-data（DaoEntityBlockingSource）；**nop-orm-eql 与 nop-orm-model 主代码零 CompletionStage 使用**。IOrmTemplate 异步 API 清单：仅 `runInSessionAsync` / `runInNewSessionAsync`（IOrmTemplate.java:76/78）；batch 装载异步面在 `IEntityPersister.loadAsync/batchLoadAsync/batchExecuteAsync`、`ICollectionPersister.batchLoadCollectionAsync`、`IOrmSessionImplementor.internalBatchLoadAsync/internalBatchLoadCollectionAsync`、`IOrmEntityFileStore.batchLoadResourceAsync`。全部驱动实现（JdbcEntityPersistDriver/JdbcCollectionPersistDriver）经 `FutureHelper.futureCall` 在**调用方线程同步执行**后返回已完成 promise（对照 api-core FutureHelper.java:122-129：`task.call()` 后 `toCompletionStage`）。全 5 根无 `orTimeout`/`completeOnTimeout`/future cancel 传播点：**取消语义与超时语义在 ORM 异步链上整体缺位**（并入 C3-001/C3-006 各自失效面，不单列）。
- **测试代码说明**: nop-orm 存在 TestFlushFailure（flush 失败/delay task）、nop-dao 存在 TestTransactionTemplateAsync、nop-orm-data 存在 TestDaoEntityBlockingSource(Claim)。按纪律测试代码不是生产守卫；变异推演在各条目内标注"现有测试是否会红"。

---

### [nop-persistence-rw-C3-001] runInNewSessionAsync：上游 future 永不完成时 session 永不关闭且调用方 context 会话注册表滞留新会话

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:244-270`
- **证据片段**:
  ```java
  public <T> CompletionStage<T> runInNewSessionAsync(Function<IOrmSession, CompletionStage<T>> callback) {
      IOrmSession session = sessionFactory.openSession(stateless);
      OrmSessionRegistry registry = OrmSessionRegistry.instance();
      IOrmSession oldSession = registry.put(sessionFactory, session);   // L247: 调用方 context 注册表被替换
      CompletionStage<T> future;
      try {
          future = callback.apply(session);
      } catch (Throwable t) {
          future = FutureHelper.reject(t);
      }
      return ContextProvider.thenOnContext(future).whenComplete((ret, err) -> {  // L255: 清理只挂在完成回调上
          try {
              if (err == null) { session.flush(); }
          } finally {
              IOrmSession s = registry.put(sessionFactory, oldSession);  // L261: 仅 future 完成时才恢复
              if (s == session) { session.close(); } ...
  ```
- **严重程度**: P1
- **并发场景**: 调用方线程（HTTP worker/调度线程）打开会话并注册进 context 级 `OrmSessionRegistry`（OrmSessionRegistry extends ContextualizedRegistry，键为 IContext，见 OrmSessionRegistry.java:16-27）；异步上游在另一线程完成 future 后，清理回调才经 `context.execute` 排空执行。
- **触发时序**: T1（请求线程，context X）执行 L245-247：session S 打开并注册进 X 的注册表（oldSession 被顶替）；T1 返回包装 future 给上层后挂起等待。上游业务任务提交的 future F 因下游无超时（本模块 5 根内无任何 `orTimeout`，上游链超时不在本模块控制内）永不完成 → L255 的 `whenComplete` 永不执行 → S 永不 flush/close，且 X 的注册表中 S 持续顶替 oldSession → 同 context 内任何后续 `ormTemplate.runInSession`（L205 `registry.get`）命中泄漏的 S，读到陈旧一级缓存并继续向其写入；context 为长生命周期（全局/调度型 context）时滞留无限期。后果三连：实体缓存驻留（内存）、注册表污染（后续请求复用脏会话）、连接若已被 flush 外的首批操作懒取则一并滞留（连接面见 C4-003）。
- **现状**: 清理完全依赖上游 future 完成；同步版 `runInNewSession`（L214-231）用 try/finally 保证恢复，异步版无对应的兜底（无超时、无取消挂钩、无 context 关闭钩子）。
- **风险**: 任一上游挂起（如未配置超时的 RPC/异步驱动）即触发；对本模块所属 ORM 层意味着每挂起一次泄漏一个会话，且污染同 context 后续数据访问的正确性（读到旧会话缓存）。context 请求级关闭时 `BaseContext.close()` 仅清 attributes（BaseContext.java:315-327），不会补调 session.close()，泄漏的关闭责任无处落地。
- **建议**: 在 L247 处同时注册兜底：将 `session` 经 `context` 的 onClose/属性生命周期钩子或 `session.addOnClose` 反向登记，future 完成路径保持现状；或为返回 future 增加 `FutureHelper.bindCancelToken` 类似的超时兜底（平台设施优先复用 `ContextProvider.completeOnContext`）。最小修复：在注册表 put 时保存 oldSession 于 context 属性，并在 context 关闭钩子中关闭仍注册的会话。
- **失效面枚举（F3）**: 全部异步会话入口收敛于本方法——(1) `runInSessionAsync` 无既有会话分支 → 直接委托 runInNewSessionAsync（同修复点）；(2) `runInSessionAsync` 有会话分支（L236-238）不开不关，无本缺陷；(3) 同步 `runInNewSession`（L214-231）try/finally 兜底，无缺陷；(4) `SingleSessionFunctionInvoker.invokeAsync` → runInSessionAsync（承继）；(5) `SingleSessionMethodInterceptor/SingleSessionServiceInterceptor` 异步分支 → runInSessionAsync（承继）；(6) `OrmSessionTaskStepWrapper.execute` newSession 分支（nop-task-ext）→ runInNewSessionAsync（承继）；(7) `BizActionInvoker.invokeActionAsync` → runInSessionAsync（承继）。一处修复覆盖全部兄弟面。
- **信心水平**: 确定（清理路径唯一性可由代码直接证明；"永不完成"的具体上游属本模块边界外，但链路内无超时兜底属本模块事实）
- **误报排除**: 不属"平台基础设施自身实现"（api-core 的 thenOnContext 对照确认其注释明确"返回的promise需要立刻被使用，如果传递到其他线程上使用，则不确保这一点"——兜底责任在调用方即本模块）；也不属同步版已保护的路径。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。清理唯一挂在 whenComplete 亲验成立（OrmTemplateImpl.java:257-268 无超时/取消/context 关闭兜底）；挂起路径资源泄漏属本维度 P1 明示类别；round2 调用点穷举（8 类调用方、异步可达仅 3 条 AOP 入口全部收敛于 runInNewSessionAsync 单一修复点）复核**确认**

---

### [nop-persistence-rw-C3-002] runInSessionAsync 两个分支异常传播契约不一致：已有会话时同步抛出，无会话时返回失败 future

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:233-241`（对照 L249-254）
- **证据片段**:
  ```java
  public <T> CompletionStage<T> runInSessionAsync(Function<IOrmSession, CompletionStage<T>> callback) {
      IOrmSession session = OrmSessionRegistry.instance().get(sessionFactory);
      if (session != null && session.isStateless() == stateless) {
          return ContextProvider.thenOnContext(callback.apply(session));  // L237: callback 同步抛出 → 直接穿出本方法
      }
      return runInNewSessionAsync(callback);  // L252-253: catch Throwable → FutureHelper.reject(t)
  }
  ```
- **严重程度**: P2
- **并发场景**: 同一方法、同一入参，行为取决于调用线程 context 中是否已存在匹配会话——即取决于并发/调度环境（请求链前段是否已开过会话），非调用方可控。
- **触发时序**: 同一 `@SingleSession` 异步 biz 方法被两次调用。调用 A：此前链路已 `runInNewSession` 开过会话 → L237 `callback.apply(session)` 内业务代码抛 NPE → 异常**同步**穿出 `runInSessionAsync`，穿出 `SingleSessionServiceInterceptor.interceptAsync`，以同步异常形态到达 RPC 层；调用 B：无既有会话 → 同一异常被 L252 捕获，以**失败 future** 形态返回。按"返回 CompletionStage"契约编写的上层代码（如 `thenApply` 链、GraphQL 异步数据装载）对失败 future 有处理、对同步异常没有 → 调用 A 路径异常处理被绕过。
- **现状**: 新会话分支 catch Throwable 转 reject；既有会话分支裸调用。同接口双契约。
- **风险**: 异常处理代码路径偶发失效（典型的环境依赖型偶发缺陷）；对照事务侧 `TransactionTemplateImpl.runInTransactionAsync`（L189-194 同样 catch → reject）证明平台契约意图是"异步方法不同步抛"，ORM 侧与其不一致。
- **建议**: L237 改为 try/catch 包裹：`try { return ContextProvider.thenOnContext(callback.apply(session)); } catch (Throwable t) { return FutureHelper.reject(t); }`，与新会话分支及事务模板对齐。
- **失效面枚举（F3）**: (1) `runInSessionAsync` 既有会话分支——缺陷本体；(2) `runInNewSessionAsync`——已 catch，无缺陷；(3) `SingleSessionMethodInterceptor.invoke` 异步分支——其内部 try/catch(Exception)→NopException 只是转换异常类型，最终仍从 lambda 抛出进 L237，承继本缺陷（且仅捕获 Exception，Error 直接穿出）；(4) `SingleSessionServiceInterceptor.interceptAsync`——lambda `inv.proceedAsync()` 同上承继；(5) `SingleSessionFunctionInvoker.invokeAsync`——`task.apply(request)` 同上承继；(6) `OrmSessionTaskStepWrapper` runInSessionAsync 分支——`getTaskStep().execute(stepRt).getReturnPromise()` 同上承继。修复点唯一（L237），全部兄弟面一并修复。
- **信心水平**: 确定
- **误报排除**: 非原语使用问题（不是"CompletionStage 本身"）；是同一公开 API 方法内两条路径对同一异常的传播模式分歧，可写出确定性触发条件（有无既有会话）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。runInSessionAsync 既有会话分支裸调用 callback（:237）亲验成立，与新会话分支 catch→reject 双契约确认

---

### [nop-persistence-rw-C3-003] BatchActionQueueImpl.flushAsync finally 块 `delayTasks = null` 命中局部变量遮蔽：字段永不清空，历史 delay 任务跨 flush 重放

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:152, 218-219, 232-238`（API: IBatchActionQueue.java:36-38）
- **证据片段**:
  ```java
  List<IBatchAction.CollectionBatchAction> collActions = this.collectionActions;
  Map<String, Runnable> delayTasks = this.delayTasks;      // L152: 声明局部变量遮蔽字段
  ...
  } finally {
      // 无论是否异步返回，这里都可以清空map了
      actionMap.clear();              // 字段正确清空
      delayTasks = null;              // L235: 清的是局部变量！this.delayTasks 未清
      this.collectionActions = null;  // 字段正确清空
      this.flushing = false;
  }
  ```
- **严重程度**: P2
- **并发场景**: 非竞态，单线程会话语义内的状态复位缺陷；`SessionBatchActionQueue.getBatchActionQueue`（SessionBatchActionQueue.java:27-43）为每个 (session, querySpace) **创建一次、跨多次 flush 复用**同一 `BatchActionQueueImpl` 实例（OrmSessionImpl.java:112 字段长生命周期），缺陷因此跨 flush 周期生效。
- **触发时序**: T1 会话首个 flush 周期：扩展代码经 `IBatchActionQueue.addDelayTask("K", task)`（IBatchActionQueue 公开 SPI，OrmSessionImpl.getBatchActionQueue:847-849 暴露）登记延迟任务 → flush 成功 → `runDelayTasks` 执行 task → finally 只清局部变量，`this.delayTasks` 仍持有 K→task。同会话第二次 flush 周期（如 `updateDirectly(entity, flushActionQueue=true)`，OrmSessionImpl.java:534-547）：L152 快照再次含 K→task → flush 成功 → L219 `runDelayTasks` **重放**已执行过的 task；且每次 flush 后继续累积，同会话内存无界增长（有界上界=会话存活期×登记键数，会话通常请求级，故增长有限但重放确定发生）。
- **现状**: 与同 finally 块内 `actionMap.clear()`、`this.collectionActions = null` 的正确复位形成对照，属漏改；`getDelayTask(key)`（L136-138）也会向后续 flush 周期返回陈旧任务。
- **风险**: 延迟任务语义为"flush 全部成功后执行一次"的扩展回调（级联装配/延迟通知类）；重放 = 回调双执行（通知重复、装配重复）；失败路径不执行 runDelayTasks（L217-219 仅 error==null 时执行），任务滞留至下次成功 flush 时再执行——时序进一步漂移。
- **建议**: L235 改为 `this.delayTasks = null;`。一行修复。
- **失效面枚举（F3）**: 本 finally 块三个复位目标逐一核对——(1) `actionMap`（clear）正确；(2) `collectionActions`（this. 赋 null）正确；(3) `delayTasks`（缺 this.）缺陷本体。同族接口 `IBatchActionQueue.flush` 默认实现（IBatchActionQueue.java:43 syncGet(flushAsync())）与本实现共享缺陷面；`SessionBatchActionQueue.flushAsync` 不触碰 delayTasks，无缺陷。
- **信心水平**: 确定（Java 名字遮蔽语义明确；仓库内生产调用方为空不影响缺陷成立——公开 SPI 的行为契约已破坏）
- **误报排除**: 非"测试专用简化并发模型"；`addDelayTask` 是 `_vfs` beans 体系暴露给扩展实现的生产 SPI，只是当前仓库内仅测试调用（TestFlushFailure.java:113/139——单 flush 用例，现有测试不会红，无保护力）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。delayTasks 局部变量遮蔽亲验成立（:151 声明局部变量、finally :235 `delayTasks = null` 清局部变量，与同块 actionMap.clear()/this.collectionActions=null 的正确复位形成对照）

---

### [nop-persistence-rw-C3-004] flushAsync 两阶段拓扑顺序与错误中断依赖"future 同步完成"：异步 SPI 驱动下顺序违约且无 fail-fast 守卫

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:160-208`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/SessionBatchActionQueue.java:49-63`
- **证据片段**:
  ```java
  CompletionStage<Void> future = persister.batchExecuteAsync(true, querySpace, ...); // 阶段1: topo 正序
  FutureHelper.collectWaiting(future, futures);      // 只挂起等待列表，不等待
  future.whenComplete((v, err) -> { if (err != null) errorRef.compareAndSet(null, err); });
  if (FutureHelper.isError(future) || errorRef.get() != null) { ... break; }  // L173: 仅对"已完成"的 future 有效
  ...
  // 阶段2: topo 逆序 delete —— 阶段1 的 future 全部完成后才应开始，但此处无等待
  ```
- **严重程度**: P2
- **并发场景**: flush 调用线程与（假想的）异步驱动执行线程；`IEntityPersistDriver.batchExecuteAsync` 是公开 SPI（IEntityPersistDriver.java:49-53），接口签名承诺 CompletionStage，未声明"必须同步完成"。
- **触发时序**: 若扩展实现返回真异步 future（如分片并行写驱动）：T1 flush 线程循环阶段 1，`isError(future)` 对未完成 future 恒 false，errorRef 不置位 → 循环不打断，阶段 1 对全部实体模型的写 SQL 全部**提交**（未完成）；循环结束立即进入阶段 2 逆序 delete 循环 → 子表 delete 与父表 insert 并发在途，外键顺序违约；`notifyUnsubmittedFailure` 的 phase1FailIndex/phase2FailIndex 全部失真，未提交动作的失败通知错发/漏发；`waitAll`（L210）只在最后兜底收集异常，无法恢复拓扑顺序。当前唯一实现 JdbcEntityPersistDriver.batchExecuteAsync（JdbcEntityPersistDriver.java:236 `FutureHelper.futureCall`）同步完成，缺陷潜伏。
- **现状**: 实现正确性隐式依赖"驱动同步完成"约定；接口 javadoc（IEntityPersistDriver）无此声明；无运行时守卫（如 `if (!future.isDone()) fail-fast`）。同族代码 OrmBatchLoadQueueImpl.flush（L596-597 注释"异步驱动下回调新入队的装载项…"）证明作者知晓异步驱动的存在，写侧 flushAsync 却未设防。
- **风险**: 任一异步驱动扩展（跨库分片、异步批管道）接入即数据完整性违约（F2 半个成功 + 拓扑倒置）；ORM 层为全平台数据访问底座，SPI 面暴露度高。
- **建议**: 二选一并落于契约：(a) 接口 javadoc 明示"返回的 future 必须在返回时已完成（同步执行语义）"，并在 flushAsync 循环内对未完成 future fail-fast（`FutureHelper.isDone` 为 false 即抛 OrmException）；(b) 或真正异步化：阶段 1 全部 collect 后 `waitAll` 再进阶段 2。
- **失效面枚举（F3）**: 同一"同步完成假设"的全部依赖点——(1) BatchActionQueueImpl 阶段 1 break 检查（L173）；(2) 阶段 2 break 检查（L196）；(3) SessionBatchActionQueue.flushAsync 的 `isError(future)` break（L55-57）；(4) OrmBatchLoadQueueImpl.flush 的 do-while 循环（L587-600，有注释与二次捕获 loadQueue 的补救，属已感知点）；(5) 对照组：EntityPersisterImpl._batchLoad 分块循环（L209-214）用 bothSuccess 链式组合，对异步完成正确，无需修。四处缺陷点共享同一修复裁定。
- **信心水平**: 确定（缺陷以"约定缺失+守卫缺失"形式成立；当前驱动下不可触发）
- **误报排除**: 非"有界声明存在"类误报——接口未声明同步完成约束，守卫可绕过/缺失恰是校准条款定义的缺陷形态（"守卫缺失、文档未声明"）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P2**。同步完成假设的四依赖点枚举成立；与 C4-001 同根分维度立案维持

---

### [nop-persistence-rw-C3-005] OrmSessionImpl.flush 将 dirty 置 false 与清缓存脏标记先于 SQL 执行；flush 失败后批动作被清空且无法重试——非事务路径半个成功状态残留

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:205-227`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:232-238`
- **证据片段**:
  ```java
  try {
      this.flusher = new CascadeFlusher(this, cache);
      this.flusher.execute();          // 实体变更装配为批动作入队
      this.dirty = false;              // L211: 先声明干净
      this.cache.clearDirty();         // L212: 会话缓存脏标记清除
      this.batchActionQueue.flush();   // L214: 此后才真正执行 SQL —— 失败则以上两步已成事实
  } catch (Throwable e) { exp = e; } finally { ... }
  ```
  且 `BatchActionQueueImpl.flushAsync` 的 finally（L234 `actionMap.clear()`，注释"无论是否异步返回，这里都可以清空map了"）在失败路径同样清空动作队列。
- **严重程度**: P1
- **并发场景**: flush 调用线程与 JDBC 执行线程（伪异步下同一线程，见 C4-001）；失败恢复路径涉及同会话的后续任意操作。
- **触发时序**: T1 会话修改实体 A、B → `session.flush()`：L211-212 先置 dirty=false、清缓存脏标记 → L214 `batchActionQueue.flush()` 执行 UPDATE 时约束冲突抛 SQLException → 异常经 L215-224 抛给调用方。若调用方处于事务链（TransactionServiceInterceptor），rollback 兜底、会话随事务关闭——此为受保护路径。若调用方为**非事务会话**（如 `runInNewSession` 直连、DaoEntityBlockingSource 的 runInSession 每语句自动提交路径）捕获异常后继续使用会话：重试 `flush()` 在 L189 `if (!dirty)` 短路返回"成功"，A、B 的修改既未落库也不再会被 flush（动作队列已清、脏标记已清、会话缓存中实体标记为干净）→ 后续 `session.close()` 静默丢弃 → 调用方以为已持久化的数据丢失。
- **现状**: 失败可见（异常抛出）但失败后的会话状态不自洽：声称干净、实际未写、且丧失重写能力。对照 `flushImmediately`（L1016-1031）对 dirty 采用"oldDirty || dirty"保守恢复并带注释说明——同一文件内两处 dirty 处理哲学不一致。
- **风险**: 捕获-重试是批写失败的常见编排；命中即静默丢数据（F2/F9 复合形态）。事务路径受 rollback 保护，故判 P1 而非 P0（主路径有事务护栏）。
- **建议**: 将 L211-212 移至 `batchActionQueue.flush()` 成功之后；或 flush 失败时回滚 `dirty=true`/缓存脏标记，并将失败语义补发给队列中动作（动作 onFailure 已由 flushAsync 的 notifyUnsubmittedFailure 覆盖未提交部分，但"已提交失败"部分依赖 batchExecuteCommand 内部回调，需一并核对）。
- **失效面枚举（F3）**: "先声明干净后执行"的全部兄弟点——(1) `flush()`（L211-212 缺陷本体）；(2) `flushImmediately`（L1021-1029：先存 oldDirty、finally 保守合并，正确写法，可作为修复模板）；(3) `saveDirectly/updateDirectly/deleteDirectly`（L506-562，flushActionQueue=true 时走 batchActionQueue.flush，dirty 语义由 markDirty/clearDirty 与 flush() 分担，承继 (1) 的结果）；(4) `flushSave/flushUpdate/flushDelete`（L1034-1076）直接走 persister 同步路径，无队列，不受影响。修复点唯一在 flush()。
- **信心水平**: 确定（顺序与短路条件均可直接从代码证明；"调用方捕获后续用会话"是否普遍属使用模式判断，故未判 P0）
- **误报排除**: 非"低频路径粗粒度锁"类；flush 失败处理是错误主路径，且同文件 flushImmediately 的谨慎写法证明该顺序非有意设计。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。dirty=false/clearDirty 先于 batchActionQueue.flush() 的顺序亲验成立（OrmSessionImpl.java:211-214）；flushImmediately 的 oldDirty 保守写法对照成立；round2 落实的现实触发形态（BlockingSourceConsumeTask catch-continue + drainTo 轮询体内 dao.flushSession:196）确认，P1 维持

---

### [nop-persistence-rw-C3-006] DaoEntityBlockingSource.drainTo 阻塞轮询模型：REQUIRES_NEW 事务横跨整个等待窗口（take() 为无限期）持有连接，消费线程以默认 100ms 周期轮询 DB，单次 DB 调用无超时

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:172-214`（对照 FutureHelper.java:500-515 waitUntil、JdbcTransaction.java:61-75 懒取连接）
- **证据片段**:
  ```java
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  public int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait) {
      ...
      long timeout = maxWait > 0 ? maxWait : Long.MAX_VALUE / 2;   // take()/takeMulti: 无限等待
      long interval = minWait <= 0 ? pollInterval : Math.min(pollInterval, minWait);
      if (interval <= 0) interval = DEFAULT_POLL_INTERVAL_MILLIS;  // 默认 100ms
      boolean found = FutureHelper.waitUntil(() -> {
          List<T> items = loadItems(dao, maxElements - (c.size() - oldSize));  // 每个 tick 一条 SELECT
          ...
      }, timeout, interval);   // waitUntil = while(!test){ Thread.sleep(interval); }
  ```
- **严重程度**: P1
- **并发场景**: N 个消费者线程（消费框架的 worker 线程，本类不建线程）各自阻塞在 `take()/takeMulti`（minWait=maxWait=-1）；事务拦截器 `TransactionalMethodInterceptor` 经 `ioc:default="true"` 注册（`_vfs/nop/dao/beans/dao-defaults.beans.xml:109-110`）作用于本 bean，REQUIRES_NEW 在进入 drainTo 时即绑定新事务。
- **触发时序**: 消费者 C1..Cn 调用 `take()` → AOP 开启 REQUIRES_NEW 事务 → 首个 tick `loadItems` → `dao.findAllByQuery` 触发 `JdbcTransaction.getConnection()`（懒取，JdbcTransaction.java:61-75）从池中取走连接 → 队列为空 → waitUntil 进入 `Thread.sleep(100ms)` 循环，**事务与连接横跨整个等待窗口**（无空窗、无 per-tick 释放）→ 队列长期空闲时每个消费者无限期占有一条连接：池容量 10、消费者 10 个即连接池整体死锁（其他业务取不到连接，负载上升必然触发）。若消费方绕过 AOP 代理直取原始对象，则 @Transactional 静默失效——抢占 UPDATE 与 SELECT 之间失去事务语义声明，属同一配置面的另一失效形态。叠加面：每个 tick 一条 SELECT（默认 10 次/秒/空闲消费者），空闲 DB 负载随消费者数线性放大；`loadItems` 内单次 DB 调用无超时（F5：慢查询/锁等待直接挂死消费线程，waitUntil 的 interval 只作用于轮询间隙，不作用于 DB 调用本身）。
- **现状**: 轮询间隔与溢出防御（注释 L186-192）已处理；中断语义已恢复（L208-211）；但"事务/连接存活窗口=等待窗口"与"DB 调用无超时"未设防。仓库内无 beans.xml 注册（仅测试），由下游应用装配——装配方式决定 AOP 是否生效，两种形态均登记。
- **风险**: 连接池耗尽是全平台性故障（不止消费链路自身）；空闲轮询放大 DB 压力；挂死消费线程对健康检测不可见（F5）。
- **建议**: (1) 将事务边界收窄到单次 `loadItems`（每 tick 一个短事务），take() 的长等待移出事务；(2) 为 `findAllByQuery` 的 Statement 设置 queryTimeout 或用 `JdbcHelper` 现有超时设施；(3) 文档声明"必须经 IoC 代理使用，@Transactional 才生效"。平台设施复用：事务模板 `TransactionTemplateImpl.runInTransaction(REQUIRES_NEW, ...)` 包住 loadItems。
- **失效面枚举（F3）**: 本类全部消费入口收敛于 `drainTo(4参)`——(1) `take()`（L122-126，maxWait=-1，最长持有，缺陷本体）；(2) `takeMulti`（L157-159，同上）；(3) `poll(timeout,unit)`（L139-144，minWait=maxWait=timeout>0，连接持有至多 timeout，同缺陷较短窗口）；(4) `poll()`（L129-136 与 `drainTo(c,max)`（L147-154）均走 maxWait=0 分支（L180-184），单次查询立即返回，事务窗口极短，不受影响；(5) `tryClaim` 内 `ormTemplate.runInSession(updateByExample)`（L260）独立短会话，不受影响。修复点唯一（drainTo 事务边界内移）。
- **信心水平**: 确定（事务拦截链与懒取连接代码均可直接追证；"消费者规模≥池容量"的触发条件在任何队列型应用中常规出现）
- **误报排除**: 非"有界声明存在"类——pollInterval 有默认值防 0 间隔，但事务窗口与 DB 超时无任何声明与守卫；也不属平台基础设施重审（事务模板仅对照使用方式）。
- **复核状态**: 未复核
- **复核状态**: 已复核（独立复核合并批 2026-10-18）——**维持 P1**。REQUIRES_NEW 横跨 waitUntil 窗口 + 单次 DB 调用无超时亲验成立；与 C6-001 同位点合并工单终裁**确认**（修复重叠 >80%，双锚点保留、计数去重）；K 量化亲验（DataSourceConfig.maxSize 默认 8、DEFAULT_POLL_INTERVAL_MILLIS=100）

---

## 维度集外线索（CL-500 段，C3 报告侧）

### [CL-500] OrmSessionImpl.close() 静默丢弃未 flush 的批动作，无 onFailure 通知（C6/F8 形态）
`nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:768-791`。close() 仅清缓存/置 closed/跑 onClose 回调，不 flush、不遍历 `batchActionQueue` 对滞留动作补发 onFailure。与 runInNewSessionAsync 错误路径（C3-001 证据 L257-268：err!=null 跳过 flush 后直接 close）组合后，已入队未执行的写动作既不执行也无回调，依赖动作回调做清理的扩展（对照 BatchActionQueueImpl.notifyUnsubmittedFailure 的设计意图）被静默跳过。属资源生命周期/清理对称性维度，移交 C6/C8 口径裁定。

### [CL-501] JdbcCollectionPersistDriver.batchLoadCollectionAsync 失败路径：orm_beginLoad 后无状态恢复（C6 失败路径清理不对称）
`nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcCollectionPersistDriver.java:122-150`。批路径对全部集合先执行 `orm_beginLoad()`（L124 循环；OrmEntitySet.java:679-686：清空 entities/removedEntities、重置索引 map），随后 L127 `jdbc().executeQuery`；SQL 抛异常时 L146 的 `orm_endLoad()`（实现为空方法体）不执行，集合停留于"已清空+加载中断"状态。同文件单集合路径（L87-99）的 beginLoad 位于行回调内，结构不同不受此影响。当前 `orm_endLoad` 为空使后果有限（下次懒加载可重试），但 beginLoad 的破坏性（丢弃既有内容）与失败路径无恢复不对称，属 F8 形态，移交 C6。

### [CL-502] 驱动单槽 SQL 缓存跨线程竞态为良性 + 集合驱动非默认方言每次重生成 SQL（C1/C9 形态）
`JdbcEntityPersistDriver.java:129-134, 154-159, 192-197`：`loadSql/batchLoadSqlPart/lockSql` 单槽缓存为共享可变字段，多线程 check-then-act 竞态只丢失缓存槽（各线程使用本地生成的不可变 EntitySQL，内容正确），属良性；`JdbcCollectionPersistDriver.java:111-116` 同一模式但**无写回**，非默认方言（shard）路径每次调用重生成 SQL，热路径分配浪费（C9 形态）。C1 轮若未覆盖此三字段，补登 C9。

### [CL-503] BatchActionQueueImpl.runDelayTasks 只捕获 Exception：Error 中断回调链且成功被报告为失败（F9/F2 形态）
`BatchActionQueueImpl.java:279-289, 213-231`。delay task 抛 Error（非 Exception）时：`runDelayTasks` 不捕获 → `whenComplete` 回调中断 → `collActions` 的 onSuccess/onFailure 全部不执行（集合动作回调既不走成功也不走失败），且 retFuture 以 Error 异常完成——DB 动作实际全部成功，调用方却收到失败（半个成功的反相：成功被报告为失败）。移交异常处理/F9 口径。

### [CL-504] OrmBatchLoadQueueImpl.flush 失败时 afterFlushCallbacks 被静默丢弃（F9 形态）
`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java:576-607, 629-644`。`afterFlush(Runnable)` 注册的回调仅在 flush 全程成功时于 L602 执行；`syncGet(waitAll)` 抛异常时 finally（L603-606）直接 `afterFlushCallbacks = null`，已注册回调既不执行也无失败通知。登记性线索，移交 C6/F9。

---

## C3 附：异步入口清单（盘点交付物）

| 入口 | 实现 | 完成线程 | 超时/取消 |
|---|---|---|---|
| IOrmTemplate.runInSessionAsync/runInNewSessionAsync | OrmTemplateImpl:234/244 | 调用方（callback 同步段）；context 队列排空线程（完成回调） | 无 |
| IEntityPersister.loadAsync/batchLoadAsync/batchExecuteAsync | EntityPersisterImpl:137/218/638 | 调用方线程（伪异步） | 无 |
| ICollectionPersister.batchLoadCollectionAsync | CollectionPersisterImpl:111 | 调用方线程（伪异步） | 无 |
| IEntityPersistDriver 三异步方法 / ICollectionPersistDriver.batchLoadCollectionAsync | JdbcEntityPersistDriver:125/188/233、JdbcCollectionPersistDriver:104 | 调用方线程（futureCall 同步执行） | 无 |
| IOrmSessionImplementor.internalBatchLoadAsync/internalBatchLoadCollectionAsync | OrmSessionImpl:892/902 | 承上 | 无 |
| OrmBatchLoadQueueImpl.flush | :577 | 调用方线程（syncGet 收敛） | 无 |
| IBatchActionQueue.flushAsync / SessionBatchActionQueue.flushAsync | BatchActionQueueImpl:145 / SessionBatchActionQueue:49 | 调用方线程（伪异步） | 无 |
| IOrmEntityFileStore.batchLoadResourceAsync（SPI，仓库内无实现） | IOrmEntityFileStore:33 | 实现方定义；default batchLoadResource 以 syncGet 收敛 | 无 |
| ITransactionTemplate.runInTransactionAsync | TransactionTemplateImpl:185 | 调用方（task 同步段）；context 队列排空线程（commit/rollback/cleanup） | 无 |
| ITransaction.commitAsync/rollbackAsync | AbstractTransaction:311/224（JdbcTransaction 承继） | 调用方（doCommit/doRollback 同步段）+ context 队列（监听器回调） | 无 |
| DaoEntityBlockingSource.take/poll/drainTo | DaoEntityBlockingSource:122-214 | 消费方线程（sleep 轮询） | 轮询窗口有界（maxWait>0）；DB 调用无超时 |

**CL 交叉引用说明**: api-core 的 thenOnContext/completeAsyncOnContext/ContextTaskQueue（单 processingThread 串行化、syncGet 排空等待）语义经对照确认成立，本报告的完成线程行为分析以此为前提；api-core 自身的 CL-401/402/407/418 相关面不在本轮立案范围。

## 维度复核结论（独立复核合并批，2026-10-18）

- **复核方式**: 与初审/深挖代理独立的第 2 轮合并复核批。本维度 6 条发现证据逐条亲验；P1×3 逐条子项复核；历史对照面已解除盲审。
- **裁定统计**: 6 条全部**保留**（降级 0、驳回 0）——P1×3 维持 / P2×3 维持。
- **P1 终裁**: C3-001（挂起路径会话泄漏——本维度 P1 明示类别「挂起路径资源泄漏」，round2 调用点穷举确认异步可达链仅 3 条 AOP 入口且全部收敛单一修复点）、C3-005（flush 先清 dirty 后执行 SQL——顺序亲验 + round2 落实 BlockingSourceConsumeTask catch-continue 现实触发形态）、C3-006（drainTo 池死锁——K≥8 阈值亲验，池默认 8）全部维持。
- **合并裁定终裁**: **C6-001+C3-006 同位点单工单——确认**（同一方法 drainTo、修复重叠 >80%：事务边界内移同时消解两案，queryTimeout 子项挂同一工单验收清单；台账双锚点保留、remediation 计数 1 项双挂）。
- **CL 线索处置（批量裁定，5 条全部确认登记）**: CL-500（close 静默丢批动作）、CL-501（beginLoad 无失败恢复）、CL-502（单槽缓存良性 + 集合驱动重生成）、CL-503（runDelayTasks 只捕 Exception）、CL-504（afterFlushCallbacks 失败丢弃）均确认登记，移交归属维持，无驳回。
- **争议点**: C3-005 的 P1 依赖「调用方捕获异常并继续使用会话」编排——round2 已将触发面从假想编排落实为平台自带消费骨架（DaoEntityBlockingSource.drainTo 轮询体内 dao.flushSession:196 + BlockingSourceConsumeTask catch-continue），本轮复核确认该落实成立，P1 争议闭合。

## 最终保留项

| ID | 初判 | 终裁 | 条目 |
|---|---|---|---|
| C3-001 | P1 | **P1** | runInNewSessionAsync 上游永不完成会话永不关闭 |
| C3-002 | P2 | P2 | runInSessionAsync 双分支异常传播契约不一致 |
| C3-003 | P2 | P2 | delayTasks 局部变量遮蔽（跨 flush 重放） |
| C3-004 | P2 | P2 | flushAsync 拓扑顺序依赖同步完成约定 |
| C3-005 | P1 | **P1** | flush 先清 dirty 后执行 SQL 半成功残留 |
| C3-006 | P1 | **P1**（与 C6-001 合并工单） | drainTo REQUIRES_NEW 横跨等待窗口 |

**维度终局**: P1×3 / P2×3，无驳回、无撤案。
