# 03 · nop-ai 深审发现

> 范围：`nop-ai/` 22 个子模块，main 源码 1047 个 Java 文件（排除 src/test、_gen、target、demo）。
> 规则编号 R1-R16 见 `01-checklist-and-baseline.md`。标注 ✅verified 的条目已经主审计源码复核。

## A. 做对的部分（正面样板）

1. **标准 CRUD 层完全合规**：nop-ai-service 的 22 个实体 BizModel 全部 `@BizModel + extends CrudBizModel<T> implements INopAiXxxBiz`，xmeta 齐备（66 个），I*Biz 方法带 `@BizQuery + @Name + IServiceContext` 末参。
2. **模块异常体系**：NopAiErrors / NopAiAgentErrors（32 个 ErrorCode）等成体系；公共 API 错误大量 `ErrorCode.define + NopException`。
3. **nop-credential 正确接入**：`AiModelCredentialResolverImpl` 消费 `ICredentialProvider` SPI，`NopAiModelBizModel` save/delete 做引用计数 reconcile——教科书式接线。
4. **nop-search / nop-rule / nop-task-core 有复用**：`SearchEngineExecutor` 用 `ISearchEngine`；`RuleBasedSelectionStrategy` 用 `IRuleManager`；`TeamTaskFlowOrchestrator` 基于 `ITaskFlowManager/AbstractTaskStep` 编排。
5. 字典声明式（`ai/session-status` 绑定实体列）；无 `@BizMutation+@Transactional`、无 `Map<String,Object>` 返回、`@Inject` 全非 private、无 `*Controller`；无 `new ObjectMapper`、无 Commons StringUtils。

## B. 发现清单

### P1

**AI-1 ✅verified · 706 行过程式巨型方法：Agent ReAct 主循环**（违反 R10）
`nop-ai-agent/.../engine/ReActAgentExecutor.java:351` `execute()` 内双层 `while(true)/reactLoop`，351-1057 行间 10 处 `ctx.setStatus(...)` 状态流转 if-else 链；终态判定是多枚举比较链。
修复：每轮迭代（reasoning→tool→observed→adjudicate）拆阶段化 TaskStep（模块已依赖 nop-task-core 且有 GraphTaskStep 先例）；终态判定下沉状态机/`canXxx()`。

**AI-2 ✅verified · nop-ai-agent 自建 raw-JDBC 持久化栈，绕过 ORM，且与 ORM 管理表双写**（违反 R1/R6/R3）
14 个 main 类直用 `java.sql.Connection/PreparedStatement`。实锤：`session/DbModelSwitchedMessageWriter.java:74` `"INSERT INTO " + NopAiSessionMessageTable.TABLE_NAME` 直写 **ORM 实体 `NopAiSessionMessage` 管理的同一张表**（`nop_ai_session_message`），形成两条写入路径；`DBSessionStore.java:31` 另建 `ai_agent_session` 表与 dao 层 `NopAiSession` 概念重复。
后果：静默绕过逻辑删除/多租户/版本/dirty-tracking 管道；同一业务数据双写路径是字段演进的阻断性技术债。
修复：写路径改 `INopAiSessionMessageBiz`；自建表要么注册进 ORM 模型，要么文档明确定位独立 store 层并补齐租户/软删语义。

**AI-3 ✅verified · 破坏性操作标注 `@BizQuery`**（违反 R4）
`nop-ai-tools/.../sequential_thinking/service/SequentialThinkingBizModel.java:110-112` `@BizQuery clearHistory()`——写操作走查询注解（无事务语义）。修复：改 `@BizMutation`，权限 action 同步调整。

**AI-4 ✅verified · `@Name("String")` 复制粘贴缺陷（对外契约 bug）**（违反 R4）
`nop-ai-tools/.../file/FileToolBizModel.java:96` `saveFiles(@Name("projectName") ..., @Name("String") String fileContents)`。GraphQL/RPC 调用方必须以 `String` 作为参数名才能传值。修复：改 `@Name("fileContents")`。

**AI-5 · FileToolBizModel 伪 BizModel + 全部方法缺 `IServiceContext` 末参 + 无 I*Biz 接口**（违反 R7/R8）
`FileToolBizModel.java:39` 无 CrudBizModel、无对应实体/xmeta；7 个 public 方法 `IServiceContext` 出现 0 次。
修复：补 `IFileToolBiz` 与 context 透传；或迁移到 nop-ai-toolkit 工具执行器体系。

**AI-6 · AiFileTool public 方法无 Biz 注解 + 错误以字符串返回值吞掉**（违反 R7/R13）
`nop-ai-mcp-server/.../AiFileTool.java:52` `loadNopFileXDef` 无 `@BizQuery/@BizAction`；`saveNopFile` 返回 `"ERROR: ..." / "SUCCESS"` 字符串而非 `NopException(ErrorCode)`（模块已有 `McpServerErrors` 可用）。

### P2

**AI-7 · ChannelLoginApiBizModel 伪 BizModel + 112 行编排 + 通用 ErrorCode**（违反 R8/R10/R13）
`nop-ai-gateway/.../login/ChannelLoginApiBizModel.java`：无 xmeta；`loginByScanAsync` 112 行四步编排；约 10 个失败分支全部 `ERR_CHECK_INVALID_ARGUMENT` 未区分语义。注：镜像平台 `LoginApiBizModel` 先例、有 P0 加固注释，属"有意的边界"，判 P2。

**AI-8 · 自建调度守护线程，未评估 nop-job**（违反 R3）
`TeamTaskSchedulerDaemon.java:237`、`ScheduledRecoveryManager.java:359`、`DBMessageService.java:188` 均 `scheduleWithFixedDelay` 自起守护；另有 `DbDaemonCoordinator` 自实现 DB 互斥选主。修复：迁 nop-job 或留档豁免。

**AI-9 · 自建重试+熔断框架（20 类）**（违反 R3，部分可辩护）
`nop-ai-core/.../reliability/`（StandardRetryPolicy 完整重实现退避/抖动/熔断）。LLM 调用级带错误分类/账号链/跨 provider failover 有领域特殊性，但通用退避算法应评估下沉或对接 nop-retry `IRetryEngine`。

**AI-10 · 自建向量/嵌入检索抽象（17 文件），平行于 nop-search**（违反 R3）
`nop-ai-agent/.../memory/IVectorAdapter...`（内存线性扫描实现，注释自认生产实现待做）。toolkit 侧 `SearchEngineExecutor` 已正确复用 `ISearchEngine`，agent memory 侧另起炉灶。修复：生产实现落 ISearchEngine 适配器。

**AI-11 · DBMessageService 自建 DB 消息传输（660 行）**（违反 R3）
自建 `ai_agent_message` 表 + 50ms 轮询 + CLAIMED 抢占 + stale sweep，完整重发至少一次投递协议，与 nop-message-core 的实现重复。修复：评估替换或登记为 IMessageService 的正式 sibling 实现。

**AI-12 · 贫血模型 + Processor 缺失整体失衡**（违反 R10，对照基线 §2.1/§2.2）
22 个实体 0 个领域方法（`NopAiSession.java` 空类）；全 nop-ai 仅 1 个 Processor；**>80 行方法 49 个**（最长 706 行）：`AgentToolDispatcher.executeAllowedCalls`(270)、`LlmCallCoordinator.doLlmCallWithRetry`(246)、`AgentSessionLifecycle.restoreSession`(210)、`DefaultAgentEngine.doExecute`(200)。
修复：实体补稳定判定（如 `NopAiSession.isIdle/isTerminal` 基于已有 dict 字段）；大编排方法按阶段拆分（不必教条化为每方法一 Processor，见基线 2.1）。

**AI-13 · 业务状态机 Java enum 固化**（违反 R12）
`TeamTaskStatus`（CREATED→CLAIMED→COMPLETED/ABANDONED 全 Java CAS 实现）、`AgentExecStatus`（9 态运行时枚举）；nop-ai-agent 共 57 个 enum。`AgentExecStatus` 有 P2-MA1-035 裁定注释（运行态 vs 持久化 dict 边界），TeamTaskStatus 若需租户定制应升级为 DSL。

**AI-14 · 裸 IAE/ISE 大量存在（main ~150 处）**（违反 R13）
代表：`FailureEscalationPolicy:33-94`、`PlanExecutionState:76/178/181`、`ThresholdBreaker:137/182/216`（`Unknown circuit state` ISE）。修复：公共入口换 `NopException + NopAiAgentErrors`。

**AI-15 · NopAiAgentException 裸消息构造 157 处（main 含）**（违反 R13）
全库 558 处 `new NopAiAgentException(`，401 带 ErrorCode、157 仅消息。修复：main 代码裸消息调用点补错误码。

**AI-16 · service 模块内直接 DAO 查询**（违反 R6）
`nop-ai-service/.../credential/AiModelCredentialResolverImpl.java:154-159` `daoProvider().daoFor(NopAiModel.class)` + `findAllByQuery`。有注释说明 LLM 运行链无请求上下文，但位于 service 而非 infra 包。修复：移包或改 `INopAiModelBiz`。

**AI-17 · NopAiModelBizModel 用 `dao().getEntityById`（有注释的边界用法）**（违反 R5，P2 边缘）
`NopAiModelBizModel.java:96/149`，注释声明"save 前事务内读旧值"。保留需注释完整（已有）；更优是 `doSave` 的 prepareSave 回调取旧实体。

### P3

- **AI-18** 裸时间 API：`DbUsageRecorder.java:131`（写库时间戳）、`AgentMessageEnvelope.java:30` 等；`System.currentTimeMillis` 全库 102 处（50 处非 mock main）。
- **AI-19** `Files.readString/new FileInputStream` 代替 VFS：`CheckpointSnapshotReader:41`、`CheckpointJournalReader:109`、`SessionFileReader:56`、`JavaMethodReplacer:21`、`JavaCodeFileInfoParser:76`。
- **AI-20** `.getBytes()` 无字符集：`JavaMethodReplacer:49`、`HttpRequestExecutor:168`（Basic 认证凭据）、`LanguageStatsAggregator:121/129`。
- **AI-21** `*Service` 命名 ~10 个（`DefaultAiChatService/ChatServiceImpl/ChannelMessageServiceImpl/DBMessageService` 等）——多因实现平台 SPI 命名被迫；新代码建议 `-Manager/-Coordinator/-Store` 后缀。
- **AI-22** DTO 用 Jackson `@JsonProperty`（sequential_thinking 8 个文件）——应用平台 `@DataBean/@Name` 约定。
- **AI-23** HTTP 状态码魔数：`AiGatewayFailoverInterceptor:353-356`（429/401/403 内联），建议常量化。
- **AI-24** `BeanContainer` 在 CLI 工厂入口（`AiCommand:78/238`）可接受；实体内 0 处（合规）。
- **AI-25** store 层 DAO（`ChannelSessionStoreImpl:87`）——文档允许的例外，确认合规。

## C. 统计

| 指标 | 数值 |
|---|---|
| Processor 类 / Step 类 | **1** / 6（基于 nop-task-core） |
| @BizModel | 26 手写（22 CrudBizModel + 4 工具/编排） |
| 超长方法（>80 行） | **49**（>100 行 24 个，最长 706 行） |
| 实体领域方法 | **0 / 22 实体** |
| 自建重复能力 | **7 项**：重试框架、熔断器、DB 消息传输、调度守护（3 处）、向量检索（17 文件）、raw-JDBC 栈（14 类）、代码态状态机（57 enum） |
| 正确复用 | nop-credential、nop-search(toolkit)、nop-rule、nop-task-core、dict |

## D. 小结

分层差异极大：**nop-ai-service/-meta/-dao/-gateway 登录链 ~90% 合规（正面样板）**；**nop-ai-agent（498 文件，占近半）~45-50%**——实质是一套自包含运行时框架（自建持久化/调度/重试/消息/向量检索），过程式巨型方法集中于此。整改第一优先级：AI-2（raw-JDBC 双写）+ AI-1（706 行主循环），其次 AI-3/4/6 三个低成本契约修复。
