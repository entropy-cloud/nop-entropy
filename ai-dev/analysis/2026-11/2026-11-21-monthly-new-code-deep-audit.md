# 近一月新增代码深度审计（b63e4cbe85..HEAD）

> Status: resolved
> Date: 2026-11-21
> Scope: 全仓近一月新增/变更主源码（6 分区，4139 文件，约 +428k 行；代码提交约 540 个）
> Conclusion: 发现 3 个 Blocker（nop-git 策略绕过×1、nop-git HTTP 无认证×1、nop-ib 索引陈旧假绿×1）、约 20 个 Major、40+ 个 Minor；修复拆入 plans 2582–2587。

## Context

- 用户要求对近一月新增代码做深度+开放式审计并写分析报告，随后拟制修复计划。
- 审计窗口：`git diff b63e4cbe85..HEAD`（2026-10-21 至 2026-11-21）。
- 方法：6 个并行独立审计子代理分区覆盖（nop-git / nop-ib+nop-rg / nop-ai / 框架核心+持久化 / 运行时服务 / 格式+报表+调试器），每条 finding 均带 file:line 证据；只读，未运行构建。

## Findings 总账（按严重级）

### Blocker（3）

| # | 模块 | 位置 | 问题 |
|---|------|------|------|
| B1 | nop-git | `nop-tools/nop-git/nop-git-core/.../policy/DefaultGitPolicyEngine.java:204` + `JGitRepositoryService.java:677-681` | 保护分支 force-push 策略可 4 种方式绕过：refspec `+` 前缀、`--force-with-lease`、无 refspec 直推、通配 refspec；策略引擎核心目标失守 |
| B2 | nop-git | `nop-tools/nop-git/nop-git-server/.../NopGitHttpServer.java:44-55` | HTTP git 服务无任何认证、默认绑定所有网卡、receive-pack 对任意客户端开放；hook 只拦 `refs/heads/`，其他 refs（tags 等）可任意改写 |
| B3 | nop-ib | `nop-tools/nop-ib/.../engine/IbEngine.java:116-119`（联动 TestCommand.java:397,511） | 单元索引进程级缓存跨编译陈旧：同一次 check/test 内预编译后测试选择集与 `--cache` 指纹仍用旧索引 → 新测试漏派发 + 缓存假绿命中；HTTP 服务模式跨请求累积 |

### Major（20）

- nop-git（5）：`push -u` 无 refspec NPE（Wave4Ops.java:244-248）；CLI/facade operation 命名漂移致策略规则旁路（GitCompatCli.java:49-50）；review 审批 env 通道可被守护进程自身绕过（DefaultGitPolicyEngine.java:251-252）；`expandPathspec` 忽略 .gitignore/嵌套仓库（JGitRepositoryService.java:818-830）；worktree add 允许分支双 attach + master 硬编码回退（WorktreeSupport.java:176-184,219-221）
- nop-git（资源/语义，2）：TreeWalk/ObjectReader/Files.list 泄漏（JGitRepositoryService.java:964-993, WorktreeSupport.java:139）；merge 用 RESOLVE 策略与 native ort 偏差（Wave4Ops.java:320）
- nop-ib（4）：`setWorkerBudgetOverride` 全局静态并发互踩（ServiceCore.java:169-175）；MavenFallback 超时失效——readLine 永久阻塞、EXIT_LOCK_TIMEOUT=75 死代码、err 丢弃（MavenFallback.java:80-92）；`compile -pl` 首个无源模块整体早退假绿（CompileCommand.java:94-97）；wave11 去锁后 `-pl` 并行分支并发派生多个 mvn 进程写竞争（TestCommand.java:326-344）
- nop-rg（1）：RegexLiteralExtractor 漏检字符类内行终止构造 → 候选行预滤静默丢匹配（RegexLiteralExtractor.java:137-186）
- nop-ai（3）：DBMessageService deadLetter 非原子 + DLQ 主键冲突毒消息永久循环（DBMessageService.java:889-947）；checkpoint journal 重写与 append 双锁不覆盖 + 非原子截断写（FileBackedCheckpointManager.java:342-363）；claimMessage UPDATE 与 gen 读取跨连接 TOCTOU（DBMessageService.java:694-725）
- 运行时（2）：wf 条带锁父子流程锁序倒置 ABBA 死锁面（WorkflowEngineImpl.java:1798,1821,1265）；runXpl 用户脚本在 wf 条带锁内执行违反自家不变式（WorkflowEngineImpl.java:1817）
- 报表/格式（3）：报表缓存开关关闭仍写缓存目录成永久孤儿 + 同步生成无并发去重可损坏文件（NopReportResultCache.java:77-96,157-179）；PdfDoc.exportPages 跨 COSDocument 共享 PDPage 无深拷贝（PdfDoc.java:306-330）；echarts 请求级超时写入全局单例 last-write-wins 跨请求污染（EchartsEngine.java:53-66 + ChartExporter.java:136-140）
- GraphQL WS（1）：idle 断连以自定义 JSON-RPC pong 为唯一活性依据，标准 graphql-ws 客户端被误杀（JsonRpcWebSocketHandler.java:583-590）

### Minor（40+，摘要归组）

- 错误处理规范偏离（裸 IllegalArgumentException/NPE/NFE/NumberFormatException，应 NopException+ErrorCode）：nop-ai（DefaultOrphanRecoveryHandler.java:113-127 等）、nop-orm（OrmSessionImpl.java:139）、nop-wf-dao（DaoWorkflowStore 分批入口）、nop-stream-runtime（PollingJdbcMessageService.java:155-160）、nop-git（server/Main.java:16）、nop-ib（TestCommand.java:423 NPE）
- 边界/off-by-one：gateway 重订阅上界 maxAttempts+1（BufferedStreamingPublisher.java:650）【2026-11-21 plan 2585 勘误：对抗审查裁定本条 off-by-one 口径有误——配置键 CFG_GATEWAY_STREAM_MAX_RESUBSCRIBE_ATTEMPTS 语义为「重订阅次数上界」，总 attempt 数 = 1 首发 + maxAttempts 次重订阅，`attemptCount >= maxAttempts + 1` 与键语义自洽，非缺陷，比较符不翻转；钉 = TestStreamResubscribeBound.pin3a】；wf due 扫描同毫秒游标整批跳过（DaoWorkflowStore.java:290-296 + WfTaskScanner.java:293）；clean 无 -f 静默、stash apply 误 drop、worktree prune 无视 locked（nop-git）
- 资源/内存：echarts ThreadLocal Context 无关闭钩子、resizeCanvas 画布累积、PdfImageRenderer.renderAll 全页光栅 OOM、datav per-user 信号量 map 无淘汰、AgentSessionLifecycle terminalStatuses 无界、pdf ImageCensus 空吞异常+大图内存放大
- 并发/一致性与语义：GenerationFence 触顶 clear 回填窄窗（nop-orm）、pendingTxnEvictKeys 泄漏、AbstractTransaction close 无 CAS 门、rollback-only commit 语义变更未登记迁移、WS 订阅闸类加载定格、review ack 静默、BoundedStreamPublisher 吞 RejectedExecutionException、WfTaskScanner deadline>lease 无装配校验、GitIocFactory policyPath 配置静默失效、config 裸读与 commit 口径不一致
- nop-ib 可观测性：生产路径 System.err debug 输出、缓存指纹缺 JDK/运行器维度、/v1/run 请求体无上限、QUEUED 取消不 notify

## 结构性观察

1. **新模块（nop-git）风险密度最高**：3 个 Blocker/Major 集群全部落在策略引擎与 HTTP 服务的"安全目标 vs 实现"缝隙上——功能面（parity、CLI）质量高，安全面缺少对抗性验证（无绕过测试）。
2. **假绿是 nop-ib 的系统性主题**：索引陈旧（B3）、compile -pl 早退、surefire passthrough 系列已修过三轮仍新增一处；根因是"编译/测试选择"与"事实记录（UnitIndex/fingerprint）"的生命周期没有统一 owner。
3. **老模块修复质量高**：nop-kernel/nop-persistence/nop-wf 本轮变更大多带 plan 裁定注记 + 载体钉测试，无 Blocker；少量 Minor 集中在错误码规范与窄窗并发。
4. **错误处理规范偏离面广但浅**：约 10 处裸异常，均为新写的参数校验路径，修复成本低。

## Conclusion

- 结论已得出：3 Blocker + 20 Major + 40+ Minor，全部带 file:line 证据（本文件即底账）。
- 后续工作：修复拆入 6 份计划 `ai-dev/plans/2582-nop-git-security-hardening.md`、`2583-nop-ib-correctness-fixes.md`、`2584-nop-ai-agent-persistence-resource-fixes.md`、`2585-runtime-services-concurrency-fixes.md`、`2586-report-format-resource-fixes.md`、`2587-framework-core-convention-fixes.md`。
- 各计划的执行优先级建议：2582（安全）> 2583（假绿）> 2584（数据一致性）> 2585 > 2586 > 2587。

## Open Questions

- [ ] nop-git HTTP 服务的部署形态（是否仅本机/可信网络使用）决定 B2 修复取认证方案还是仅默认 loopback；需 owner 裁定。
- [x] AbstractTransaction rollback-only commit 语义变更是否有外部调用方依赖抛错语义（plan 2587 内核查）。
  结论（2026-11-21，plan 2587 执行时核查）：grep 全仓无外部调用方依赖该抛错语义——`ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT` 仅 AbstractTransaction 自身（sync beforeCommit :379 / commitAsync :405 保留点）与 `TestTransactionStateCas`（裁定载体钉）引用；sync commit 主路径自动 rollback 语义无调用方破绽。分叉已作为已知行为登记于 `docs-for-ai/03-runbooks/transaction-boundaries.md`（事务对象状态机语义节），双路径钉覆盖（TestTransactionStateCas#pin_2587_e）。

## Erratum（2026-11-21，plan 2587 执行期勘误）

- 「GenerationFence 触顶 clear 回填窄窗」（并发/一致性分组）：**缺陷不成立，撤销**。前提探针钉
  （nop-orm `TestCra2517GenerationFence#testPlan2587_probe_capClearOrphanFenceCannotPassCas`）验证：
  clear 后 fence 重建走 per-persister 单调分配器（epoch.incrementAndGet()*1_000_000），新 entry 初始
  世代严格大于全部历史已发放世代，持 clear 前旧 readGen 的孤儿迟到回填 CAS 必败——原 finding 描述的
  「孤儿 fence CAS 放行迟到回填」路径不存在。按 plan 2587 审查裁定撤销该项修复，探针钉保留为防回归勘误证据。

## References

- 审计窗口：git diff `b63e4cbe85..HEAD`
- `ai-dev/plans/00-plan-authoring-and-execution-guide.md`
- `docs-for-ai/02-core-guides/error-handling.md`
