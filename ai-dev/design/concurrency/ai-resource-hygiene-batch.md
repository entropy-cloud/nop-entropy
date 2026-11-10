# WI 3.1 资源卫生束 b（R4-22，plan 2556）设计裁定

> Status: active
> Last Reviewed: 2026-11-10
> Source: `ai-dev/audits/2026-10/2026-10-19-1410-deep-audit-nop-ai-core-nop-ai-agent-concurrency/`（独立立案 10 条）+ fresh-session 取证（2026-11-10：10/10 原样）

## 一、设计结论（十条裁定 A-J）

1. **裁定 A（C3-005 同步抛出逃逸异步包络；R1-m6 锚定勘正）**：`AiCommand.callChat`（:339 直调点）整体 try/catch → `FutureHelper.reject`——loadConfig/resolveBaseUrl/凭证校验/checkRateLimit（ChatServiceImpl :448-460 同步 `ERR_AI_RATE_LIMITED` throw 在 callAsync 返回前同步抛出）等前置段异常一律收敛为 failed future（调用方单一异常通道；`returnExceptionAsResponse` 语义保持）。护栏 C10-019。

2. **裁定 B（C3-006 缓存读写异常对称化）**：`saveCachedResponse` 调用（AiCommand :309-315 thenApply 内裸调）包 try/catch——失败 WARN 带 throwable 后仍返回成功响应 `res`（缓存写失败不使成功响应 exceptionally）；`load` 的 catch 日志升 WARN 带 throwable（:291-301 降级语义保持）。

3. **裁定 C（C3-029 DB 副作用隔离）**：`ReActAgentExecutor` 三调用点（:1058/:1430/:1564 usageRecorder.record、:996 writeModelSwitched）与 `DbUsageRecorder`/`DbModelSwitchedMessageWriter` 内部 throw 点（:173-174/:157-158）改隔离形态——调用点 try/catch（LOG.warn + 失败计数，超阈值 WARN 升级）；`resolveModelId` 加 (provider,model)→id 进程级 CHM 缓存（:72 每次SELECT 消除）。**兄弟站点 `nop-ai-service` `OrmModelSwitchedMessageWriter:64` 同修**（模块入 Targets）。

4. **裁定 D（C5-002 响应缓存磁盘包络）**：`DefaultAiChatResponseCache` 加 ①`nop.ai.service.cache-max-entries`（缺省 1000，LRU 逐出，复用 io.nop.commons.cache）②单条大小上限 `nop.ai.service.cache-max-entry-bytes`（超限放弃缓存+WARN）③load 单条读上限 ④CL-041 promptName 路径穿越守卫（同文件随单）⑤owner doc 磁盘包络声明（TTL 缺省 0 语义保持）；逐出→文件删除映射（LRU 逐出回调删文件）+ 单条大小写前检查（serialize 后量长度）。护栏 C10-015。

5. **裁定 E（C5-017 DeferredAckMailbox 有界化；R1-M4 修订——接线/清扫机制显式化）**：①deadLetter 容量上界（缺省 1000，满则淘汰最旧+WARN）②inFlight stale 回放：**惰性 synchronized 方法内清扫**（offer/poll/nack 临界区驱动，`MailboxEntry.polledAt` 在册字段复用——无新增调度器线程；对齐 DBMessageService 5min stale-claim 语义）③**接线事实核正**：生产默认 mailboxFactory=null（opt-in，不构造邮箱）——本批为**显式启用路径提供有界缺省**（DefaultAgentEngineConfig 的 mailbox factory 配置形态构造即有界 capacity；缺省 off 语义保持 + owner doc 包络声明）。护栏 C10-019。

6. **裁定 F（C7-003 Retry-After sanity cap；R1-m1 单点化）**：`StandardRetryPolicy.shouldRetry` **入口单点** normalize——retryAfterMs（来源 :163/:195/:212 三路）统一 cap `MAX_RETRY_AFTER_MS`（10min，与 OVERLOADED_MAX_DELAY_MS 同级常量），超限取上限 + WARN 记原始值；`AbstractLlmDialect` 解析端保持原值输出。

7. **裁定 G（C7-005 probe 世代令牌；R1-B1/m2 修订——API 形态显式化）**：`ThresholdBreaker` 内部 per-key `probeGeneration`（进入 probe 递增）。**API 形态**：`ICircuitBreaker`（nop-ai-core 模块内 SPI，非 nop-*-api PA 面）加 default 方法 `long currentGeneration(String key)` 与带 gen 的 `recordSuccess(key, gen)`/`recordFailure(key, gen, err)` 重载（default 落旧签名=未升级调用方兼容回落：内部 gen=-1 视为「未知世代」，按现有语义生效并 WARN once——兼容回退显式声明）；`ThresholdBreaker` 覆写校验：gen 不匹配当前世代 → 陈旧逃逸回执，WARN 丢弃不翻转状态。**调用方接线（LlmCallCoordinator，nop-ai-agent :240/:958/:980 allowCall、:228/:344/:444/:505 record*）**：allowCall 时捕获 generation，record 时回传（计划 Targets/载体补该接线断言）。`DEFAULT_PROBE_TIMEOUT_MS` 30s→90s，经 `DefaultAgentEngineConfig` 构造参数可配（5 处无参构造点透传）。javadoc 补陈旧回执语义。护栏 C10-018。

8. **裁定 H（C7-006 重试退避+限流复查；R1-M1/M2/M3 修订）**：`AiCommand` 重试循环重写为**异常感知形态**——现 `RetryHelper.retryNTimes` 为 thenCompose 结果链，异常完成的尝试短路不重试（C3-005 包络化后限流异常将永远不重试——A×H 交互面），新循环必须显式 `recover`/handle 异常完成 → `LlmErrorClassifier.classify`（ERR_AI_RATE_LIMITED→RATE_LIMITED 可重试类）→ 可重试则按策略延迟重试。**限流复查 = 重试层级上移实现**：重试以 callChat 为单位（每 attempt 重入 chatService.callAsync → checkRateLimit 天然复查入口限流器，无新增 API 面、零 nop-ai-api PA 触碰）；若现重试包络低于 callChat 层则上移。**延迟调度**：非阻塞延迟 hop 经 `GlobalTimer` 轻量调度（§3.1.1 轻量直连豁免——重试体本体仍落完成线程，无阻塞体指向 globalTimer）；invalid ChatExchange（正常返回无异常）映射 TRANSIENT 分类取 StandardRetryPolicy floor 延迟（full-jitter）。与裁定 A/B 同文件（AiCommand）合并 diff。

9. **裁定 I（C7-007 失败记忆 TTL）**：`ThresholdBreaker.entries` 加 lastTransition 时间戳，allowCall compute 临界区内惰性清除超 TTL（`nop.ai.breaker.entry-ttl-ms` 缺省 1h）的 OPEN/CLOSED-with-failures 条目（pristine 条目既有即时清理保持）；javadoc 无界豁免声明随 TTL 收窄。**C10-018 护栏义务（C7-003/005/006/007 四单）随批载体承载**。

10. **裁定 J（C7-015 restore 锁序）**：`AgentSessionLifecycle.restoreSession` 的 `transitionToRunningAndPublishRestored`（setStatus+事件）移入 `acquireRestoreExecutionSlot` 成功之后（对齐 resumeSession :313-344 模式；verifyLatestCheckpoint 只读可留锁前）。**与 plan 2555 同文件 rebase**：不得回退取消哨兵/租户 save-restore 改动与 `TestOrphanRecoveryBudget` 钉；护栏=补 `TestSessionLifecycleLockGate` restore 锁门用例（adjudication 指定义务，当前缺失）。

## 二、载体表

| 工单 | 载体 | 模块 | 类型 |
|---|---|---|---|
| C3-005 | `TestAiCommandAsyncEnvelope`（限流触发→failed future 非同步抛出断言） | nop-ai-core | 行为钉 |
| C3-006 | `TestAiCommandCacheFailureIsolation`（save 抛异常→响应仍成功返回） | nop-ai-core | 行为钉 |
| C3-029 | `TestUsageRecorderIsolation`（recorder 抛异常→主流程继续 + 阈值升级；resolveModelId 缓存结构钉） | nop-ai-agent | 行为钉+结构钉 |
| C5-002 | `TestResponseCacheBounds`（max-entries LRU 逐出 + 单条超限放弃 + CL-041 穿越拒绝） | nop-ai-core | 行为钉 |
| C5-017 | `TestDeferredAckMailboxBounds`（deadLetter 上界淘汰 + stale 回放） | nop-ai-agent | 行为钉 |
| C7-003 | `TestRetryAfterCap`（超大 retryAfter → cap 上限 + WARN） | nop-ai-core | 行为钉 |
| C7-005 | `TestProbeGenerationToken`（陈旧回执丢弃 + 新回执生效 + 可配 timeout） | nop-ai-core | 行为钉 |
| C7-006 | `TestAiCommandRetryBackoff`（重试退避非零 + 重试前限流复查拒绝） | nop-ai-core | 行为钉 |
| C7-007 | `TestBreakerEntryTtl`（超 TTL 条目惰性清除 + pristine 即时清理保持） | nop-ai-core | 行为钉 |
| C7-015 | `TestSessionLifecycleLockGate` 扩展（restore 锁门用例：锁前无状态变更断言） | nop-ai-agent | 行为钉 |

## 三、回归面与接缝

- 回归基线：`./mvnw -o test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent,nop-ai/nop-ai-service -am`（本批三叶；C3-029 兄弟站点在 nop-ai-service）。
- 硬接缝：① AiCommand 三单同文件（A/B/H）合并 diff；② ThresholdBreaker 两单同状态机（G/I）协同设计；③ C7-015 与 plan 2555 AgentSessionLifecycle 改动同文件 rebase（哨兵/save-restore/预算钉零回退）；④ C5-017 stale 语义以 2555 后 DBMessageService 为基准。
