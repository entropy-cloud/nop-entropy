# WI 3.1 会话/配额/取消束 a（R4-21，plan 2555）设计裁定

> Status: active
> Last Reviewed: 2026-11-10
> Source: `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`（merge-adjudication MA 族 + C1-010 工单）+ fresh-session 取证（2026-11-10：原样 9 + MA-12 部分覆盖[C1-004 已修]）

## 一、设计结论（十条裁定 A-J）

1. **裁定 A（MA-10 会话存储锁纪律统一；R1-M1/m4 修订）**：per-session 装载锁模板统一——`FileBackedSessionStore.loadPersisted` 的非 heal `persistedStates.put`（:430）收入 per-session 锁域（heal 写已在锁内）；`remove` 不摘锁/锁内复查（CL-028/039/067 归并——锁身份更换致会话复活面闭合；DB store remove 摘锁同受此约束）；`DBSessionStore` **租户复合键作用域覆盖 sessions/persistedStates/sessionLocks 三映射**（sessions CHM 复合键 + persistedStates/registerPersistedState 同复合键——仅 sessions 复合键不闭合 C1-020 碰撞面）；checkpoint managers（DB/FileBacked）整表替换改 per-session 条目移除 **且必须同步 `loadedSessions.remove`**（负缓存不清理将阻断重装载）。**无界面裁定**：sessionLocks 在 remove 不摘锁形态下按活跃 session 数缓增——锁条目仅在其空闲（tryLock 成功）时**机会性**移除（机会性、非正确性依赖），残余缓增登记 watch-only。护栏 C10-004 并发用例随批。

2. **裁定 B（MA-12 换装/volatile 清扫）**：C1-003 七 static 单例（XmlResponseParser/MarkdownResponseParser/YamlResponseParser/DefaultSystemPromptLoader 等）volatile 化；C1-013 `DefaultAgentEngine.agentExecutor`（:130/:485-487）与 `SessionLockRenewal.lockRenewExecutor`（:28/:37-39）volatile + setter 覆盖前旧池 shutdown（own 语义保持，close 路径不变）；C1-022 七组 plain 字段（TeamTaskFlowOrchestrator/DBMessageService/CallAgentExecutor 等）volatile 化。C1-004 已修（plan 2498）= Proof 验收项（`TestEmbeddingRetrySchedulerLifecycle_C4_004` 复绿）。护栏 C10-013。

3. **裁定 C（MA-13 惰性记忆化）**：`PromptTemplateManager` check-then-set → volatile + synchronized 记忆化（或 computeIfAbsent 语义等价原子形态）；`PromptModel` 字段 volatile；`PromptOutputModel` 惰性 clone 发布面 volatile 化 + 构造后不变注记（休眠面校准）。护栏 C10-013/014。

4. **裁定 D（MA-14 复合操作原子化；compute 域内禁 IO/发布——enforceQuota 判定纯内存）**：`InMemoryTeamManager.bindMemberSession` quota 判定收入 `members.compute` 锁域（三步并一步）；`InMemoryContributionRegistry.unregister` 全局全序（bySource 摘键与注册表移除同锁，javadoc :103 失真声明勘正）；`CalibratedTokenEstimator` EMA RMW 收敛 synchronized 或 CAS（AtomicReference）。护栏 C10-012 并发用例。

5. **裁定 E（MA-15 租户 save-restore）**：`DefaultAgentEngine.cancelSession/forkSession` 与 `MemberFanOutDispatcher`（:294/:312，基于 plan 279 claim-epoch 新注释语义）统一 save-restore 租户包装（捕获→set→finally restore 原值而非 clear）；resume/wake 若同型随参数化遍历。护栏 C10-019。

6. **裁定 F（MA-16 续期调度池；R1-M2 修订）**：`SessionLockRenewal` 单线程调度器 → 小型专用多线程调度池（可配置核心数，缺省 2；**阻塞 JDBC 体不得指向 globalTimer**——MA-16 约束收窄后形态[R1-03 已解除 globalTimer 禁令]）。**executor 换装 = 启动期操作**（setter javadoc 显式声明：运行中会话的活跃 renewHandle 不跨换装迁移，随旧池终止——运维须在会话空闲窗操作；close() 路径 own-shutdown 语义不变）——`TestExecutorSwapLifecycle` 钉启动期换装形态。`DbSessionTakeoverLock.tryRenew` 语句加 queryTimeout——**模块内无既有 queryTimeout 约定（grep 零命中），新增 @cfg 键 `nop.ai.agent.takeover-renew-query-timeout`（秒，缺省 5）**。与 MA-12/C1-013 同文件（SessionLockRenewal）合并 diff。护栏 C10-010 池形态断言（墙钟轴不在本批）。

7. **裁定 G（MA-18 恢复预算）**：`DefaultOrphanRecoveryHandler.handleResume` 与 `DefaultSessionTimeoutHandler.localCancelled` 加重试预算（attempt 上界+指数退避，可配置）+ 结果观测（outcome 记录）+ 预算耗尽失败升级（类型化 outcome/日志）。护栏 C10-018。

8. **裁定 H（MA-20 读取限额下沉；R1-M3 修订）**：`LocalFileOperator` limit 语义作用于读取面——readText 按 offset/limit 有界读取（**模块内新建有界读取 helper**——nop-commons FileHelper 仅全量 readText/readLines，无可复用原语）；分页 readLines 读取条数上界；grepFiles 每文件读取上界；globGrep 的 maxFileCount 自弃点（:441 传 0）改**内部缺省上界常量（1000 文件）+ 截断 WARN**——不改 IFileOperator 接口签名（避免 nop-ai-tools FileToolBizModel 跨模块涟漪，接口化传递登记 successor 触发条款）。护栏 C10-015（LocalFileOperator 零测试→本批建测试类）。

9. **裁定 I（MA-21 聊天日志保留期；R1-M4 修订）**：配置化保留期 `nop.ai.chat-log.retention-days`（缺省 0 = 不清理，运维边界声明 + owner doc 包络）；>0 时写入路径惰性清理过期日期分区目录（布局 `logDir/yyyy/MM-dd/` 使清理为目录级删除），**带 once-per-date 节流守卫**（内存 lastSweepDate——每日期至多一次全量扫描，避免每请求目录 IO 热点）。与 MA-01 盘写异步化同文件分账（MA-01 已落，本批纯增量）。

10. **裁定 J（C1-010 装配窗 TOCTOU；R1-B1 修订为最小哨兵形态——不改注册时点）**：执行槽注册时点保持现状（不前移——前移形态需 CancelHandle.context 非 final 化、doExecute 新装配失败清理路径、四入口涟漪，收益不匹配风险）。`cancelSession` 未注册分支改哨兵互斥：`putIfAbsent(sessionId, CANCEL_SENTINEL)`（静态 null-ctx 标记 CancelHandle，真句柄 context final 语义零触碰）。生命周期：①putIfAbsent 失败且赢家=真句柄 → 既有已注册取消路径；②赢家=哨兵 → 二次取消幂等成功（no-op）；③putIfAbsent 成功 → try{sessionStore.get 判定：null → 既有 not-found 抛出；非 null → 既有 else 分支取消语义（setStatus/publishCancelled/checkpoint remove）} finally{remove(sessionId, sentinel)}——哨兵不滞留（清理与消费必须用 CHM 两参条件移除 remove(key, sentinel)——单参移除会在输者消费哨兵并注册真句柄后误删真句柄重开装配窗）。**注册侧**：registerExecutionSlot（及 resume/wake/restore 同型 putIfAbsent）输者读赢家，若=哨兵 → 消费哨兵（remove）+ 对刚构建 ctx 立即走既有注册后取消流程（setCancelRequested → 执行体自终止；其自身租约由既有取消路径正常释放——哨兵无租约，不存在输者替哨兵持租约面）。**入口→getOrCreate 残余窗**：登记 watch-only（与今日等宽，cancelSession 存在性检查覆盖）。**`checkpointManager().remove` 并发删除面**随哨兵互斥消除。复核 `TestDefaultAgentEngineCancel:229-235` ghost 断言零改动复绿（C10-005）。

## 二、载体表

| 工单 | 载体 | 模块 | 类型 |
|---|---|---|---|
| MA-10 | `TestSessionStoreConcurrency`（并发 getOrCreate 单实例 + remove 不复活 + 租户键同键单例 + checkpoint per-session 移除结构钉） | nop-ai-agent | 行为钉 |
| MA-12 | `TestExecutorSwapLifecycle`（setter 覆盖旧池 shutdown 断言 + volatile 结构钉七组） | nop-ai-agent + nop-ai-core | 行为钉+结构钉 |
| MA-13 | `TestPromptMemoizationConcurrency`（并发首用单例构造断言 + volatile 结构钉） | nop-ai-core | 行为钉+结构钉 |
| MA-14 | `TestTeamQuotaAtomicity`（并发 bindMemberSession 不超配额 + unregister 全序 + EMA 并发一致） | nop-ai-agent | 行为钉 |
| MA-15 | `TestTenantSaveRestore`（cancel/fork/fan-out 后外层租户值保持断言） | nop-ai-agent | 行为钉 |
| MA-16 | `TestLockRenewalPoolForm`（续期池多线程形态断言 + 单会话续期不被其他会话 DB 停顿队头阻塞[slow-hijack 注入] + tryRenew queryTimeout 结构钉） | nop-ai-agent | 行为钉 |
| MA-18 | `TestOrphanRecoveryBudget`（失败恢复 attempt 上界 + 退避 + 耗尽升级断言） | nop-ai-agent | 行为钉 |
| MA-20 | `TestLocalFileOperatorLimits`（readText limit 读取量断言[大文件只读前 N 字节] + 分页/grep 上界 + glob maxFileCount） | nop-ai-core | 行为钉 |
| MA-21 | `TestChatLogRetention`（retention-days>0 过期分区清理 + =0 不清理断言） | nop-ai-core | 行为钉 |
| C1-010 | `TestCancelDuringAssembly`（装配窗取消哨兵：真实执行者 putIfAbsent 失败读哨兵→立即取消 + TestDefaultAgentEngineCancel ghost 断言零改动复绿） | nop-ai-agent | 行为钉 |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am`（nop-ai 为聚合器，实际叶=core/agent；无 docker 依赖，H2 内存库）。
- 硬接缝：① SessionLockRenewal 一文件三单（MA-16+MA-12）合并 diff；② DefaultAgentEngine 三案同方法（C1-010+MA-12+MA-15）一次改写；③ MemberFanOutDispatcher 基于 plan 279 claim-epoch 新语义；④ `TestDefaultAgentEngineCancel:229-235` ghost 断言复核；⑤ C7-004 墙钟轴不在本批（R4-32）。
