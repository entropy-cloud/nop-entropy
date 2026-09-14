---
audit-rounds: 2
---

# nop-ai-agent 内在设计对比 Roadmap — deepseek-harness 与 pi

> Last updated: 2026-09-14
> Sources: 2026-09-12 三方代码实地探查（本 roadmap 全部代码锚点来自该次探查，执行时须按各仓库当前 HEAD 复核）；既有调研见 Framework / Platform Reuse
> 位置：本文件按仓库 roadmap 惯例存放于 `ai-dev/backlog/`；全部对比报告（Deliverable）产出至 ai-dev/analysis/compare-agent-design/（用户指定目录）。格式遵循 AGE 模板（attractor-guided-engineering-template）的 docs/backlog/00-roadmap-authoring-guide.md（frontmatter + checkbox 通道）。
> 书写约定：Deliverable 是尚未产出的未来报告，其路径一律用普通文本书写、**不加反引号**——check-doc-links 会把反引号路径当作必须已存在的仓库内链接校验；已存在的 owner / 参考文档路径保持反引号，持续受 checker 保护。

## Purpose

本 roadmap 编排一项**纯分析任务**：以 nop-ai-agent 为基准，逐项核对其与 `~/ai/deepseek-harness`（Part A）以及 `~/ai/pi`（Part B）在 agent 内在设计上的异同。终态：ai-dev/analysis/compare-agent-design/ 目录下三份基线产物 + 4 份专项深挖文档 + 20 份逐维对比报告 + 1 份总报告全部存在、代码锚点可复核、通过交叉一致性校对。

对比维度 D1–D10（权威定义与子机制拆解由 WI2 维度矩阵落定）：

- D1 内部 agent loop：主循环形态（turn/step/iteration 分层）、终止条件、迭代上限与延长、流式输出、输入注入/steering
- D2 扩展点：hook/中间件/waterfall/拦截器的类型与触发阶段、注册方式（声明式 DSL vs 代码）、veto/改写语义
- D3 事件类型与触发方式：事件词表与分类、发射点、订阅模型（emit/serial/waterfall、同步/异步）、持久化事件 vs 瞬时事件、UI 桥接
- D4 容错性：错误分类体系、重试策略与退避、部分失败与中断语义、abort/cancel、降级与恢复路径
- D5 自动切换：provider/model/账号故障转移、熔断与冷却、配额感知、模型分级路由、切换语义与 fail-loud
- D6 前缀缓存利用：缓存断点控制、前缀稳定性构造、压缩与缓存的交互、一次性调用是否写缓存、缓存命中观测
- D7 工具系统：声明与 schema、执行调度（并行/串行/屏障）、结果回填、工具调用修复、权限/沙箱
- D8 上下文工程与压缩：token 预算与压力检测、触发条件、策略分层、spill/引用式保真、产物形态（破坏性 vs 追加式）
- D9 会话持久化与恢复：session 数据模型、存储格式与后端、resume/fork/branch、checkpoint 与崩溃恢复
- D10 多代理与子代理：派生模型、深度预算、团队/编排、与主循环的隔离边界

专项深挖主题（M1，用户指定的四个机制级专题，三方覆盖，权威深挖）：

- S1 agent loop 具体执行流程：从输入进入到最终响应的逐步调用链，每阶段做什么、扩展点挂载在流程哪个位置
- S2 扩展点全量清单与能力语义：逐点标注触发时机、可以扩展什么、能力级别（仅监听 observe / 修改内容 transform / 否决跳过 veto / 中断轮次 abort-bail / 注入内容 inject）、同步异步与错误传播
- S3 同一扩展点上多个触发的顺序：排序来源（注册序/优先级/声明序）、分发语义（waterfall/serial/emit/链式）、顺序是否可预测可配置
- S4 不同扩展机制之间的协同：同一行为穿过多种扩展机制时的叠加顺序、冲突裁决、veto/中断的传播边界

范围边界（Non-Goals）：**只比较 agent 内在设计**。明确排除——插件动态更新/热重载（dsh 的 `cordis-host-runner`/`tool-cordis`/client HMR；pi 的 `/reload`、extension cache 失效、`pi install/update`）；UI/TUI 与 RPC 协议层；产品形态与部署；评测基准。总报告只产出对比结论与可吸收增量**建议**，不排实施计划（实施另开 roadmap/plan）。

## Work Item Status

> 唯一动态状态块。勾选 = 独立 closure audit 通过（见 Cross-Cutting 完成判定）。WI 编号全文件递增；顺序即执行顺序，AI 不重排。

### M0 — 对比基线

- [x] WI1 三方代码地图：nop-ai-agent / deepseek-harness / pi 的包结构、关键类与 load-bearing 文件清单，记录各仓库 HEAD commit（Deliverable: ai-dev/analysis/compare-agent-design/01-code-map.md; deps: 无; Owner: `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`）
- [x] WI2 对比维度矩阵 D1–D10：每维度的子机制拆解、三方代码锚点、报告 6 节模板与裁定格式（Deliverable: ai-dev/analysis/compare-agent-design/00-dimension-matrix.md; deps: WI1; 参考: `ai-dev/analysis/00-analysis-writing-guide.md`）
- [x] WI3 术语与概念对齐表：loop/turn/step/iteration、hook/middleware/waterfall、持久化事件 vs 瞬时事件、session/compaction/checkpoint/spill 等三方概念映射（Deliverable: ai-dev/analysis/compare-agent-design/02-terminology-map.md; deps: WI2）

### M1 — 专项深挖：执行流程与扩展机制（S1–S4，三方覆盖）

- [x] WI4 S1 agent loop 具体执行流程逐步分解：三方各产出一条从输入进入到最终响应的完整调用链（阶段划分、每步职责、流式路径），流程图上逐点标注该阶段挂载的扩展点（Deliverable: ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md; deps: WI3; nop 锚点: ReActAgentExecutor/LlmCallCoordinator/AgentToolDispatcher）
- [x] WI5 S2 扩展点全量清单与能力语义：三方每个扩展点一行——触发时机、可扩展/可影响的内容、能力级别（observe/transform/veto/abort-bail/inject 分级，能力级别以代码实际行为为准，不以注释或文档为准）、同步异步、异常如何传播（Deliverable: ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md; deps: WI4）
- [x] WI6 S3 同点多触发顺序：三方每个扩展点上多个实现共存的排序来源（注册顺序/优先级字段/DSL 声明顺序/订阅顺序）、该点的分发语义（waterfall 逐层包裹 / serial 顺序 await / emit 广播）、顺序对调用方的可预测性与可配置性（Deliverable: ai-dev/analysis/compare-agent-design/05-extension-ordering.md; deps: WI5）
- [x] WI7 S4 跨扩展协同：同一行为穿过多种扩展机制时的组合语义——nop 的 lifecycle hook × execution middleware × filter chain × DSL 声明扩展，dsh 的 agent waterfall × tools waterfall × llm/stream × system-prompt，pi 的 AgentLoopConfig hook × ExtensionAPI event × registerProvider——叠加顺序、冲突裁决、veto/bail 的传播边界与终止范围（Deliverable: ai-dev/analysis/compare-agent-design/06-extension-composition.md; deps: WI5, WI6）

### M2 — deepseek-harness 逐维对比（Part A；范围排除其插件动态更新机制）

- [x] WI8 D1 内部 agent loop：ReActAgentExecutor/AgentLoopGuard 迭代模型（maxIterations/ISustainer）vs ReactLoopAgent turn/step 模型（TurnEndReason、无固定步数上限、Inbox followup/steer/inject、chunk 持久化）；流程级细节引用 03 专项文档，只写对比增量（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D1-agent-loop.md; deps: WI3, WI4; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`）
- [x] WI9 D2 扩展点：12 个 AgentLifecyclePoint + 4 个 ExecutionPoint + filter chain + 66 接口扩展矩阵 vs `agent/pre-step`·`agent/request`·`agent/request-error`·`agent/turn-stopping` waterfall + `tools/*` waterfall + `llm/stream` + capability seams；能力级别与顺序语义引用 04/05/06 专项文档，只写对比增量（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D2-extension-points.md; deps: WI3, WI5; Owner: `ai-dev/design/nop-ai-agent/03-extension-matrix.md`）
- [x] WI10 D3 事件类型与触发方式：AgentEventType/IAgentEventPublisher + 异步消息信封（IMailbox/Topics）vs 48 类持久化 SessionEvent + Cordis emit/serial/waterfall 瞬时事件 + UI 桥接（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D3-events.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/02-execution-model.md`）
- [x] WI11 D4 容错性：ErrorClassification/IRetryPolicy/三级失败升级/IDenialLedger vs HarnessError 稳定错误码 + `agent/request-error` 重试 waterfall + 持久化 `llm/retry` 事件 + 工具中止合成结果（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D4-fault-tolerance.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`）
- [x] WI12 D5 自动切换：LlmCallCoordinator 多通道（重试→熔断→账号链→ProviderFailoverChain→模型分级路由）vs dsh 无内置 failover——`agent/request` 逐步重写 provider/model + per-provider 重试策略 + 配额仅分类不转移（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D5-auto-failover.md; deps: WI11; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`）
- [x] WI13 D6 前缀缓存利用：dsh 构造性前缀稳定（section 化静态 system prompt、动态上下文走 user 快照消息、压缩请求字节级重放复用 KV cache、`purpose` 标记辅助调用）vs nop 侧现状逐项核查（显式缓存断点/前缀稳定性约定是否存在）（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D6-prefix-cache.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`）
- [x] WI14 D7 工具系统：tool.xdef DSL + AgentToolDispatcher 标签过滤 + IToolCallRepairer 修复链 vs defineTool 输出 schema/render/`isConcurrencySafe` + tools/pre-execute 审批 + Landlock/Seatbelt 沙箱 + code-mode（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D7-tool-system.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`）
- [x] WI15 D8 上下文工程与压缩：PipelineCompactor 分层 + short-ref 引用式 + spill store vs CompactionEngine seam（pressure/context-overflow 触发、token 预算保留、tool-pairing 平衡、spill policy）（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D8-context-compaction.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md`）
- [x] WI16 D9 会话持久化与恢复：ISessionStore 三实现 + ICheckpointManager 体系（幂等键/发散检测）vs 追加式类型化事件日志 + fork 边界 + JSONL(zstd)/SQLite 双后端 + write-behind（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D9-session-persistence.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`）
- [x] WI17 D10 多代理与子代理：team/actor/plan 全栈（FanOut/Reduction/调度守护进程/跨进程协调）vs `ctx.subagents` 6 provider + 深度预算 + 实验性 agent-team（Deliverable: ai-dev/analysis/compare-agent-design/dsh-D10-multi-agent.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`）

### M3 — pi 逐维对比（Part B；维度同 D1–D10）

M2 完成后执行可复用其方法与裁定口径（软依赖，不阻塞）。

- [x] WI18 D1 内部 agent loop：agentLoop() 单层循环 + stopReason 终止 + steering/follow-up 队列 + 截断消息工具调用失败语义 vs nop 迭代模型；流程级细节引用 03 专项文档（Deliverable: ai-dev/analysis/compare-agent-design/pi-D1-agent-loop.md; deps: WI3, WI4; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`）
- [x] WI19 D2 扩展点：AgentLoopConfig 配置级 hook（transformContext/beforeToolCall/afterToolCall/prepareNextTurn 等）+ ExtensionAPI 约 35 个 `pi.on` 事件 hook + registerTool/registerCommand/registerProvider vs nop 双层扩展；能力级别与顺序语义引用 04/05/06 专项文档（Deliverable: ai-dev/analysis/compare-agent-design/pi-D2-extension-points.md; deps: WI3, WI5; Owner: `ai-dev/design/nop-ai-agent/03-extension-matrix.md`）
- [x] WI20 D3 事件类型与触发方式：AgentEvent 判别联合 + 顺序 await sink + AgentSessionEvent 扩展（auto_retry_* 等）+ JSONL/TUI 桥接 vs nop 事件体系（Deliverable: ai-dev/analysis/compare-agent-design/pi-D3-events.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/02-execution-model.md`）
- [x] WI21 D4 容错性：可重试/不可重试正则分类 + 传输层（SDK 镜像策略）与应用层（AgentSession 自动重试）双重重试 + 上下文溢出独立分类走压缩 + 错误工具结果回填 vs nop 错误规范化与重试（Deliverable: ai-dev/analysis/compare-agent-design/pi-D4-fault-tolerance.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`）
- [x] WI22 D5 自动切换：pi 明确无内置切换——用户/扩展驱动 model_select + registerProvider 热注册 + per-call getApiKey + 配额不可重试 vs nop 多通道故障转移（Deliverable: ai-dev/analysis/compare-agent-design/pi-D5-auto-failover.md; deps: WI21; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`）
- [x] WI23 D6 前缀缓存利用：Anthropic cache_control 断点（system/最后工具定义/最后 user 消息）+ cacheRetention long/short/none + 工具集变化才重建 system prompt + 稳定工具序 + 一次性调用禁写缓存 + cache-stats 未命中观测 vs nop 侧现状逐项核查（Deliverable: ai-dev/analysis/compare-agent-design/pi-D6-prefix-cache.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`）
- [x] WI24 D7 工具系统：typebox schema + 默认并行/sequential 覆盖 + deferred tools（transcript 中途引入）+ 文件变更串行队列 + 权限留白给扩展 vs nop 工具体系（Deliverable: ai-dev/analysis/compare-agent-design/pi-D7-tool-system.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`）
- [x] WI25 D8 上下文工程与压缩：threshold/overflow/manual 触发 + reserveTokens/keepRecentTokens 预算 + 追加式 CompactionEntry 非破坏重建 + branch summarization vs nop 分层压缩（Deliverable: ai-dev/analysis/compare-agent-design/pi-D8-context-compaction.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md`）
- [x] WI26 D9 会话持久化与恢复：JSONL + id/parentId 会话树 + 类型化条目与版本迁移 + labels + SQLite 后端 + 上下文重建 vs nop session/checkpoint 体系（Deliverable: ai-dev/analysis/compare-agent-design/pi-D9-session-persistence.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`）
- [x] WI27 D10 多代理与子代理：pi 无内置（官方 example extension 形态）及其设计取舍 vs nop team 全栈（Deliverable: ai-dev/analysis/compare-agent-design/pi-D10-multi-agent.md; deps: WI3; Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`）

### M4 — 汇总与收口

- [x] WI28 总对比报告：汇总 20 份维度报告与 4 份专项文档，产出全维度三方对照总表、结构性差异（范式级）清单、逐维裁定汇总与可吸收增量建议（仅建议）（Deliverable: ai-dev/analysis/compare-agent-design/99-overall-comparison.md; deps: WI4..WI27 全部; 参考: `ai-dev/analysis/agent-survey/agentscope-harness-vs-nop-ai-agent-comparison.md`）
- [x] WI29 交叉一致性校对与收口：由独立子代理校对全部 28 个产物间的结论矛盾、锚点失效与模板缺失（含专项文档与 D1/D2 报告间的权威源一致性），修正后在总报告附录记录校对结论（Deliverable: ai-dev/analysis/compare-agent-design/99-overall-comparison.md 附录; deps: WI28）

### M5 — Deep Audit Findings R1

> 来源：2026-09-14 deep-audit round 1（nop-ai 全模块组 vs 架构文档交叉审核；4 个并行维度子代理 + 主 agent 复核）。全部 P0/P1 发现落此块，关闭机制 = 勾选 checkbox（由 DRAFT 管线据此起草 remediation plan，本审计不自行起草）。

- [x] [P1] plan 356 ErrorCode 化后新增 5 个错误码零测试钉子——`ERR_AGENT_INVALID_ARGUMENT`/`ERR_AGENT_INVALID_STATE`/`ERR_AGENT_INTERNAL_DETAIL`（NopAiAgentErrors）+ `ERR_AI_CORE_INVALID_*`（NopAiCoreErrors）共 628+ 处 throw 站点，agent/core 测试零 `getErrorCode()` 断言（仅断言异常类型）；错误码值/参数契约可静默漂移而测试全绿（NopAiAgentErrors.java、NopAiCoreErrors.java; 来源: deep-audit round 1）
- [x] [P1] `AiModelCredentialResolverImpl.java:64-96` 三个 ErrorCode ID 违反 `nop.err.ai.*` 点号命名约定（`ERR_AI_CREDENTIAL_*` 大写常量式，含中文描述），经跨模块公共路径被 nop-ai-core ChatServiceImpl 消费，i18n/日志/前端按 nop.err.* 解析失效；同模块 NopAiErrors 已是正确格式，属契约面命名漂移（来源: deep-audit round 1）
- [x] [P1] nop-ai-tools → nop-ai-coder 分层倒置——`FileToolBizModel.java:250-251` 直接 `new DslToolImpl(...)`（import io.nop.ai.coder.xdsl），与 module-groups.md 分层（tools 低层工具实现、coder 应用层助手）相悖；复用 DSL 工具须连带引入 coder 重量传递依赖，契约应下沉 toolkit/core 或显式登记为有意设计（来源: deep-audit round 1）
- [x] [P1] nop-ai-gateway 模块范围漂移——channel 消息网关（ChannelMessageServiceImpl/FeishuConnector/ChannelSessionStoreImpl/IChannelConnector）与扫码登录编排（ChannelLoginApiBizModel/ChannelLoginScanProcessor）为生产面，但在 nop-ai.md/nop-ai-gateway.md/module-groups.md 零文档覆盖（docs-for-ai 0 命中），模块文档仍只见"LLM failover 网关"；消费者按文档引入模块会连带 nop-ai-dao/nop-auth-api/feishu 依赖（来源: deep-audit round 1）

### M6 — Deep Audit Findings R2

> 来源：2026-09-14 deep-audit round 2（nop-ai 全模块 open-ended adversarial review；4 并行子代理 + 主 agent 复核）。1×P0 + 5×P1 新发现落此块；round 1 的 4×P1（M5）+ 9×P2 全部复核仍存在、未修复，不重复登记。

- [x] [P0] `AiFileTool`（nop-ai-mcp-server）任意文件读写——`getResource` 用 `new File(baseDir, path)` 拼接后仅 normalize 不含校验：绝对路径（`loadNopFile("/etc/passwd")`）完全绕过 baseDir、`../` 段直接逃逸，MCP 工具权限 `AiFileTool:read/write` 恰好是授予 LLM 的工具权限，prompt-injection 暴露面下即任意主机文件读取/覆盖（AiFileTool.java:163-179; 来源: deep-audit round 2）
- [x] [P1] `ReActAgentExecutor` 失败执行仍发布 EXECUTION_COMPLETED + 跑 POST_CALL hooks——`canPublishExecutionCompleted`（:1271-1278）排除 cancelled/forced_stopped/escalated/paused/truncated/waiting 但漏 `failed`：重试耗尽/不可重试分类终止后 `finalizeLlmCallResult`（LlmCallCoordinator.java:430-434）置 failed → 循环 break → `adjudicateTerminal` 照常发布完成事件并触发副作用 hook，与 :1254-1260 注释"aborted/suspended 不得发布"契约漂移，下游消费者收到失败后的"完成"事件（ReActAgentExecutor.java:482, 1261; 来源: deep-audit round 2）
- [x] [P1] 同实例重复提交会删掉运行中执行的 takeover 租约——`DefaultAgentEngine.java:789-806`/`resumeSession:298-313`：同实例第二次提交 `tryAcquire` 走同 owner 续租成功（DbSessionTakeoverLock.java:203-224），`putIfAbsent` 失败后 catch 调 `releaseLockQuietly(sessionId, instanceId)`，其 `DELETE ... AND LOCK_OWNER=?` 删除的是**胜出执行**的租约行 → 其续租失败被强制 cancel + 第三方可趁机双执行；报错文案"locked by another instance"也错误（来源: deep-audit round 2）
- [x] [P1] `FileToolBizModel.getProjectDir` 沙箱逃逸——`StringHelper.fileName("..")` 原样返回且 `isValidFileName` 不拒 `..`，`new File(baseDir, "..")` 把整个工具沙箱上移一级（默认 /nop/projects → /nop），readFiles/saveFile/saveFiles/mergeFile/saveDslFile 全部脱沙；对侧 `AiToolsHelper.requireValidSessionId` 同类输入 fail-closed，同一抽象两套安全姿态（FileToolBizModel.java:265-271 + LocalFileOperator.java:76-88; 来源: deep-audit round 2）
- [x] [P1] Shell `&>`/`&>>` 合并重定向输出翻倍——`handleMergeRedirect` 用 `new TeeOutput(fileOutput, fileOutput)` 同一实例两次，write 逐 leg 落同一 buffer 导致每次 flush 写入双倍内容（echo stdout &> f 产出两行 stdout）；回归测试只断言 `contains("stdout")` 所以 CI 全绿（ShellCommandExecutor.java:499-506 + TeeOutput.java:33-37 + ShellCommandExecutorTest.java:239-253; 来源: deep-audit round 2）
- [x] [P1] `FeishuConnector.isBotMentioned` 群聊 @任意成员即触发 agent——仅检查 mentions 数组含 `"key"` 与 `"open_id"` 两个子串，群消息 @了**任何其他人**也通过过滤 → 未 @ bot 的群消息触发 IAgentEngine 执行与回复（成本/滥用向量，且消耗限流窗口）；类注释自认"bot open_id 精确匹配 deferred"，测试缺"@了其他用户"负例（FeishuConnector.java:586-617 + TestFeishuConnector.java:356-371; 来源: deep-audit round 2）

## Follow-up Backlog

> P2 发现（trivial / 非阻塞 polish）。来源统一标注 `source: deep-audit round <n>`。

- [x] [P2] compare-agent-design 7 份报告（03/04/05/06 专项 + dsh-D9/D5 + pi-D4）约 118 处 nop 侧 `:行号` 锚点漂移——plan 355 重构后 ReActAgentExecutor 1053→1387 行、LlmCallCoordinator 765→872、AgentToolDispatcher 434→566；全部类/机制锚点仍有效、结论不受影响，仅行号需重钉（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 1，7 份报告全部锚点按 HEAD `4582e780dad4` 逐条重核重钉，每份抽查 ≥5 锚点解析成立，报告头部已记重钉说明）
- [x] [P2] 03-extension-matrix.md §5.2"唯一实现 NoOpBudgetProvider"措辞不精确——test scope 存在 `InMemoryBudgetProvider implements IBudgetProvider`（src/test/.../budget/InMemoryBudgetProvider.java:37），建议补注 test-scope 实现以免绝对化表述被误读；矩阵其余 10 项事实声明（72 接口/死点/死代码/接线缺口/半闭合/存储实现）全部与 live code 一致（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 3，§4.2/§5.1/§5.2 补注 test-scope 实现）
- [ ] [P2] `ChannelConnectorContext.java:26-31` 两处裸 `IllegalArgumentException` 参数校验，模块已有 `NopAiGatewayErrors` 错误码容器；按 error-handling.md 两档策略应改模块异常类/ErrorCode（FailoverStreamFlow.java:186 的 IAE 为 Reactive Streams 规范强制，豁免）（source: deep-audit round 1）
- [ ] [P2] nop-ai-agent/core 约 9 个零价值测试方法（unit-test-antipatterns P-1/P-5）：TestAgentLifecyclePoint:15-60 枚举计数+assertNotNull 遍历、TestHookResult:87-93 与 TestCompletionDecision:56-61 编译期强转断言、TestNoOpContextCompactor:110 静态常量断言、TestRoutingResult:57-66 toString 仅非空、TestXmlResponseParser:17-23 零内容断言、TestGeminiDialect:39-47 仅 key 存在、TestChainRepairer:249-280 装配未验证、TestUsageRecord:17-39 全字段往返；占全量 @Test（agent 3347 + core 382）<0.5%，关键引擎 11 类覆盖扎实（source: deep-audit round 1）
- [x] [P2] roadmap Cross-Cutting 机器校验声明不可满足——"运行 `tools/mission-driver/src/roadmap-check.mjs`（指向本文件）得 passed: true"：该脚本无 CLI 入口（仅导出 parseRoadmapMarkdown/roadmapAllDone）、不解析本文件 checkbox 格式（实测 0 items / allDone=false）；AGE 模板的 ledger 校验（scanRoadmapLedger/validateRoadmapFrontmatter）不在本仓库 tools/ 副本中（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 2，Cross-Cutting 与 Reuse 表两处声明改写为可执行自检：check-doc-links --strict 0 error + checkbox 人工/脚本核对；按事实登记本仓库无对应 ledger 校验器）
- [x] [P2] roadmap Current Baseline "nop-ai-agent main java 536 文件"快照漂移——当前 src/main 实测 535（plan 354/355 后）；包计数 engine 42 / plan 66 / security 74 / team 55 / reliability 30 精确成立（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 2，536→535 + 包计数按 live 复核并标注复核命令/日期/HEAD）
- [x] [P2] nop-ai.md:126 "42 个 xbiz 文件"计数漂移——实测 main 44 个（22 实体 × 基+保留）；"非下划线 22 个全空 actions"结论仍成立（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 3，nop-ai.md 计数 42→44 + 实体表补全 22 实体）
- [x] [P2] module-groups.md:85 "不直接依赖 core 内部包"表述不精确——nop-ai-agent 直接 import `io.nop.ai.core.reliability` 18 个类型（ThresholdBreaker/LlmErrorClassifier/ProviderFailoverChain/StandardRetryPolicy 等）；建议改为"token 估算经 bridge，可靠性机制直接复用 core.reliability 包"（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 3，module-groups.md:85 表述已改）
- [x] [P2] plan 356"裸异常归零"广义表述不成立——四模块仍残留 10 处 UnsupportedOperationException fail-fast 默认方法（agent 7 / core 1 / toolkit 1 / shell 1，均为接口 default/NoOp 占位、英文消息、plan 明确排除在范围外）；建议在 plan Deferred 段补记清单防后续审核误判（source: deep-audit round 1）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 2，plan 356 Deferred 段已登记 UOE residual 清单，含 team `notEnabled()` 站点，以 live grep 口径为准）
- [x] [P2] `ThresholdBreaker` HALF_OPEN 探测位可永久卡死——`probeInFlight` 仅由 recordSuccess/recordFailure 清除：若探针调用永不回报（取消/hang/线程终止），熔断器永久 HALF_OPEN 拒绝全部后续调用，无超时逃生门；另含重复 static import（ThresholdBreaker.java:3-4, 132-139; source: deep-audit round 2）（2026-09-14 收口：plan 2026-09-14-1638-2 Phase 1，probeTimeoutMs 默认 30s 逃生门 + 重占探针槽 + 3 回归测试 + import 去重；owner doc `nop-ai-agent-reliability.md` §3.3/§5.1 已同步）
- [x] [P2] `ConcurrencyRegistry.release` 下溢补偿非原子 + 计数表永不收缩——`decrementAndGet` 后 `incrementAndGet` 恢复与并发 acquire 竞争会永久 +1 幽灵计数（自诱导下溢）；counts map 按 (provider, accountKey) 只增不删，长跑网关缓慢内存泄漏（ConcurrencyRegistry.java:52-64; source: deep-audit round 2）（2026-09-14 收口：plan 2026-09-14-1638-2 Phase 2，acquire/release 全部移入 CHM compute per-key 原子临界区（先比较后减 + 归零移除）；2 回归测试 + gateway 68 例无回归；owner doc `02-account-failover-requirement.md` §3.3 已同步）
- [x] [P2] `CFG_AI_SERVICE_CONNECT_TIMEOUT` 死配置——定义于 AiCoreConfigs.java:31 但全仓零消费，`ChatServiceImpl.buildHttpRequest` 只设 read timeout；文档宣称的连接超时 30s 静默失效，连接挂起会远超预期阻塞（AiCoreConfigs.java:30-32 + ChatServiceImpl.java:269; source: deep-audit round 2）（2026-09-14 收口：plan 2026-09-14-1638-2 Phase 3，裁定 B 删除死配置 + 30s 宣称随 `@Description` 移除，全仓 grep 零残留；不改 nop-http-api 公共契约）
- [x] [P2] RetryDecision/IRetryPolicy/LlmErrorClassifier javadoc 过期——"FALLBACK = fail-loud STOP，无 fallback chain wired"（RetryDecision.java:14-18 等）与 StandardRetryPolicy.java:126-129 已产 FALLBACK + LlmCallCoordinator 账号链/provider 链实际路由相矛盾；LlmErrorClassifier javadoc 声称 ChatServiceImpl 抛 NopException 也与 :146-152 归一化错误 ChatResponse 不符，误导消费者裁定 FALLBACK 语义（source: deep-audit round 2）（2026-09-14 收口：plan 2026-09-14-1638-2 Phase 4，3 份 javadoc 更新至 live 语义——FALLBACK 双通道已接线 + 响应级错误归一化）
- [ ] [P2] `AbstractLlmDialect.buildFullContentWithThinking` 垃圾默认标记 + 死代码——默认 think 包裹字面量 `"ery\n"`/`"module-info>\n"` 为无意义笔误残留，全仓 main 零调用（已列 ai-core-api.md:407 P3 未修）；应删除或给 sane 默认（AbstractLlmDialect.java:460-480; source: deep-audit round 2）
- [ ] [P2] `CalibratedTokenEstimator.apiStyle` 死字段 + javadoc 漂移——文档称标定"keyed on ApiStyle"，实际 estimateTokens/record 只消费 dialect；`getApiStyle()` 全仓零调用，工厂恒传 `ApiStyle.openai`（CalibratedTokenEstimator.java:19-62 + TokenEstimators.java:10-11; source: deep-audit round 2）
- [ ] [P2] `AgentHookInvoker` 非 PRE/BEFORE 点的 fail-loud bail 校验被吞——`validateBailPoint` 抛出的 NopAiAgentException 落入 else 分支被降级为 `LOG.warn("after_* hook ... continuing")`（:183-186），W5-3 fail-loud 保证（Minimum Rules #24）只对 PRE/BEFORE 点成立；POST_COMPACT/REASONING_CHUNK/ON_ERROR 直调路径丢校验（AgentHookInvoker.java:149-188; source: deep-audit round 2）
- [ ] [P2] `extractAssistantMessage` 可返回 null 但调用方无守卫——tool-call-only 响应（真实 provider 形态）时 assistantMsg=null 仍 `ctx.addMessage(null)` 并在 saveLlmTurnCheckpoint 对 `getContent()` NPE（ReActAgentExecutor.java:943-948, 1019；SingleTurnExecutor.java:82-83 同模式），执行整体以 failed 收场；防御姿态与对 messages 列表的 null 检查不一致（source: deep-audit round 2）
- [x] [P2] `LlmCallCoordinator` FALLBACK 循环无总步数上限——每次 FALLBACK 切换 `attempt=0` 重置（:206-271, 312-363, 400-412），唯一护栏是 veto cap 3 与 circuit scan 64；循环 getFallback 的自定义 IModelRouter（A→B→A）可 while(true) 无限发真实 LLM 调用 + backoff 睡眠，与同文件其他显式 cap 风格不一致（source: deep-audit round 2）（2026-09-14 收口：plan 2026-09-14-1638-2 Phase 4，MAX_FALLBACK_STEPS=16 总步数上限 + 4 切换点全部计数 + 超限 fail-loud；A→B→A 循环回归测试 callCount ≤ cap+1；owner doc `nop-ai-agent-reliability.md` 重试段已同步）
- [ ] [P2] `ThoughtStorage.exportSession/importSession` 任意路径读写原语——生产类中未校验的 `FileHelper.writeText(new File(filePath))`/`readText`，仅测试调用；一旦接线到请求 bean 即成任意文件读写工具（ThoughtStorage.java:133-158; source: deep-audit round 2）
- [ ] [P2] 文件修改工具全部原地截断写无原子性——LocalToolFileSystem.writeText/PatchFileExecutor/ApplyDeltaExecutor/ThoughtStorage.saveSession 均直接覆盖原文件（无 temp+rename、无 fsync、无 .bak）；部分写/崩溃/ENOSPC 时旧内容永久丢失，且与 move/copy 的失败转异常不一致——mkdirs()/delete() 返回值被忽略，Create/DeleteDirectoryExecutor 对失败仍报"成功"（LocalToolFileSystem.java:161-164, 198-220; source: deep-audit round 2）
- [ ] [P2] `ChannelLoginScanProcessor` MFA 分支零测试 + 跨模块裸字符串契约——`MFA_REQUIRED_ERROR_CODE = "nop.err.auth.mfa-required"`（:37, 147）与 NopAuthErrors.java:97 靠字符串精确匹配且无测试守护；nop-auth 侧改名会静默把 MFA 从"挑战应答"降级为"透传失败"，gateway 测试面 10 例全覆盖 happy path 独缺 MFA（ChannelLoginScanProcessor.java:141-176; source: deep-audit round 2）
- [ ] [P2] `IChannelConnector.getCapabilities()` 无生产消费者——105 行 ChannelCapabilities SPI 仅 FeishuConnector 自实现自调用，`ChannelMessageServiceImpl.sendToUser`（:268-304）从不读 maxMessageLength/supportsMarkdown 等；承诺的跨通道降级语义（截断/适配）无法实现，要么消费要么登记为 reserved（source: deep-audit round 2）
- [ ] [P2] `FeishuConnector` 凭证路径与自身 beans.xml 契约矛盾——ai-gateway-defaults.beans.xml:36-44 注释称 FeishuCredentials bean 来自 feishu-defaults.beans.xml，但 resolveCredentials（:619-641）从不读注入的 FeishuCredentials，改从 ChannelConfig options 手工拼、最后兜底空凭证延迟到 FeishuClient.start 失败；平台标准 `nop.integration.feishu.credentialId` 凭证面经连接器不可达，测试还把该忽略行为固化为断言（source: deep-audit round 2）
- [ ] [P2] `McpServerErrors` 中文描述违反 AGENTS.md 英文错误消息约定——`ERR_MCP_FILE_NOT_FOUND` 描述为"文件不存在: {path}"（同文件兄弟码全英文）；且 `AiModelCredentialResolverImpl.java:147` javadoc 写 `setLimit(1)` 实际 `setLimit(2)`（重复探测是意图，文档误导后续"修复"丢 WARN）（McpServerErrors.java:12 + AiModelCredentialResolverImpl.java:147-158; source: deep-audit round 2）
- [x] [P2] docs-for-ai 计数/锚点漂移——nop-ai.md:20-32 实体表仅列 22 实体中的 12 个（漏 NopAiChannelSession/NopAiEvent/NopAiSessionMessage/NopAiTodo/NopAiProjectConfig/*History）；nop-auth.md:247/216 锚点过期（loginByScan :144→实际 :147，ERR_AUTH_MFA_REQUIRED :216→实际 :97）（source: deep-audit round 2）（2026-09-14 收口：plan 2026-09-14-1638-1 Phase 3，nop-ai.md 实体表补全至 22 实体；nop-auth.md 锚点重钉 loginByScan `ChannelLoginApiBizModel.java:146/:148`、ERR_AUTH_MFA_REQUIRED `NopAuthErrors.java:97`）

## Framework / Platform Reuse

| Capability | Provider | Notes |
| --- | --- | --- |
| dsh 深度调研（较新） | `ai-dev/analysis/agent-survey/2026-08-13-deepseek-harness-analysis.md` | 2026-08 快照，M2 可增量引用；锚点须按当前 HEAD 复核 |
| pi 调研（已过期） | `ai-dev/analysis/agent-survey/2026-06-05-pi-agent-analysis.md`、`2026-06-05-pi-ecosystem-comparison.md` | pi 演进快（WIP harness、cache retention 等为新增量），必须按当前 HEAD 重核 |
| 对比报告格式先例 | `ai-dev/analysis/agent-survey/agentscope-harness-vs-nop-ai-agent-comparison.md` | 结论先行 + 勘误 + 对照表结构，WI28/WI2 参照 |
| 分析写作规范 | `ai-dev/analysis/00-analysis-writing-guide.md` | 所有报告写作前必读 |
| nop 侧设计基线 | `ai-dev/design/nop-ai-agent/`（54 篇；`03-extension-matrix.md` 索引 68 个扩展接口，2026-09-14 修订） | nop 侧 Owner doc，仍须与代码核对 |
| 对方一手架构文档 | dsh 仓库内 docs/architecture.md 与 docs/subsystems/*.md；pi 仓库内 packages/coding-agent/docs/extensions.md、docs/compaction.md、docs/session-format.md（外部仓库路径，不作本仓库链接） | 只作导航，结论必须落到代码锚点 |
| roadmap 机器校验 | 无（本仓库副本无 AGE ledger 校验器；`tools/mission-driver/src/roadmap-check.mjs` 仅导出 `parseRoadmapMarkdown`/`roadmapAllDone`，无 CLI 入口且不解析本文件 checkbox 通道） | 更新后运行 `node ai-dev/tools/check-doc-links.mjs --strict`（须 0 error）+ 人工/脚本核对 checkbox 勾选状态；不设自动化通过门禁 |
| 可选执行器 | `ai-dev/tools/mission-driver.sh`（若配置 mission） | 逐工作项 DRAFT→EXECUTE→closure audit 闭环；不强制 |

## Current Baseline

- **WI5 follow-up 已收口（2026-09-14）**：WI5 登记的 6 项 owner doc 勘误线索（IContentGuardrail 状态、接口计数矛盾、53/52 扩展点、REASONING_CHUNK 死点、HookToMiddlewareAdapter 死代码、DAE/RAE 消费者失真）已全部修订于 `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（纯文档，无代码变更；记录见 `ai-dev/logs/2026/09-14.md`、plan `ai-dev/plans/nop-ai-agent-design-comparison/2026-09-14-110620-1-extension-matrix-docfix.md`）。矩阵现索引 68 个扩展接口（72 顶层 public interface − 4 矩阵外），layer 标题计数已与表格行数一致。
- 三方代码位置（2026-09-12 探查）：
  - nop-ai-agent：本仓库 `nop-ai/nop-ai-agent`（main java 535 文件，2026-09-14 @ HEAD `4582e780dad4` 复核：`find nop-ai/nop-ai-agent/src/main/java -name '*.java' | wc -l` = 535）；agent runtime 在 `io.nop.ai.agent` 下 27 个包（engine 42 / plan 66 / security 74 / team 55 / reliability 30 等，2026-09-14 复核与上文一致），LLM 可靠性层在 `nop-ai/nop-ai-core` 的 `reliability/`，provider 中立 API 在 `nop-ai/nop-ai-api`。
  - deepseek-harness：`~/ai/deepseek-harness`，pnpm 双层 monorepo，Cordis 插件框架（vendored）；核心在 `packages/core/{agent-loop,agent,session,tools,system-prompt,scope}` + `packages/llm/{llm,llm-retry,token-meter}` + `packages/compaction/*`。
  - pi：`~/ai/pi`，npm workspaces monorepo；核心在 `packages/agent`（agent-loop）、`packages/coding-agent`（AgentSession/扩展/会话/压缩）、`packages/ai`（多 provider 统一 API）。
- 既有调研与文档见 Reuse 表。**尚无任何一份 nop vs dsh / nop vs pi 的逐维对比报告**；报告目录 ai-dev/analysis/compare-agent-design/ 待 WI1 创建。
- 探查期初步假设（仅作为 M2/M3 的核对输入，不作为结论，逐维证实或证伪）：
  - dsh 与 pi 均无内置 provider 故障转移/熔断（dsh 用 waterfall 重写表达，pi 明确留白给扩展）；nop 是三者中唯一内置多通道切换的实现。
  - 三者的主循环都无硬性步数上限语义（nop 为 maxIterations + ISustainer 可延长；dsh/pi 以 stopReason/turn 语义终止）。
  - pi 拥有三者中最显式的 prefix-cache 工程化设计（断点 + TTL + 观测）；dsh 靠构造性稳定；nop 侧是否存在等价设计待 D6 核查。

## Dependency Graph

```mermaid
graph TD
    WI1["WI1 三方代码地图"] --> WI2["WI2 维度矩阵 + 报告模板"]
    WI2 --> WI3["WI3 术语对齐表"]
    WI3 --> S["WI4–WI7 M1 专项深挖 S1–S4"]
    S --> M2["WI8–WI17 M2 dsh 逐维对比"]
    S --> M3["WI18–WI27 M3 pi 逐维对比"]
    M2 -.方法复用（软依赖）.-> M3
    WI11["WI11 dsh-D4 容错"] --> WI12["WI12 dsh-D5 自动切换"]
    WI21["WI21 pi-D4 容错"] --> WI22["WI22 pi-D5 自动切换"]
    M2 --> WI28["WI28 总对比报告"]
    M3 --> WI28
    WI28 --> WI29["WI29 交叉校对与收口"]
```

## Cross-Cutting

- 证据纪律：每条结论必须带代码锚点（仓库相对路径 + 类/函数名，必要时 `:line`）；对方仓库的设计文档只作导航，结论必须核对代码；每份报告头部记录被分析仓库的 HEAD commit 与分析日期。能力级别（observe/transform/veto/abort-bail/inject）必须以代码实际行为证明，不以注释、文档或类型名为准。
- 权威源约定：03–06 专项文档是执行流程、扩展点能力语义、同点顺序、跨扩展协同四个主题的权威深挖；dsh-D1/D2、pi-D1/D2 等维度报告引用专项结论，只写对比增量，不重复展开；若出现结论冲突，以专项文档为准并由 WI29 校对收敛。
- 报告模板契约（每份 D 报告固定 6 节，由 WI2 模板固化；专项文档 S1–S4 的章节结构由各自 deliverable 需求定义、在 WI2 模板中登记）：① 结论摘要（≤10 行）② nop 侧机制与锚点 ③ 对方侧机制与锚点 ④ 子机制逐项对照表 ⑤ 语义差异与取舍 ⑥ 裁定（nop 领先 / 对方领先 / 等价 / 双方均无 / 不可比）+ 可吸收增量建议（仅记录，不实施）。
- 语义对齐：Java 与 TS 范式差异统一按 WI3 术语表翻译，禁止直译制造伪差异；同一子机制双方皆无时裁定"双方均无"，不得硬比。
- closure audit（每个工作项的完成判定）：报告存在 + 模板结构完整 + 锚点抽查（每份报告 ≥5 个锚点可在对应仓库解析）+ 结论与对照表一致 + 全仓 check-doc-links 无 error；由独立子代理执行，通过后才勾选 checkbox。
- 验证面：纯分析任务，无代码变更，不涉及 mvn 构建与测试。每份报告产出后运行 `node ai-dev/tools/check-doc-links.mjs --strict` 保持 0 error（报告位于 ai-dev/analysis/ 历史目录，报告内部引用不做强检；本文件对未来交付物的引用按头部书写约定用普通文本）。本文件**无自动化机器校验门禁**：AGE 模板的 ledger 校验器（`scanRoadmapLedger`/`validateRoadmapFrontmatter`）不在本仓库副本中，`tools/mission-driver/src/roadmap-check.mjs` 无 CLI 入口且不解析本文件 checkbox 通道（2026-09-14 实测）；更新后以 check-doc-links --strict 0 error + 人工/脚本核对 checkbox 勾选为准。
- 报告语言：中文行文，类名/函数名/术语保留英文原名；外部仓库路径以 `~/ai/...` 或仓库相对路径书写并注明仓库。

## Rules

- 状态只在本文件 `## Work Item Status` 的 checkbox 通道维护；不设第二状态面，里程碑无状态。
- WI 编号全文件唯一递增；执行顺序 = 文档顺序；AI 不重排优先级、不发明工作项；需新增/调整工作项时先提请人工确认。
- 本文件不写对比结论正文；结论一律落对应报告，本文件只维护完成状态与范围。
- 维度定义、子机制拆解与报告模板的 owner doc 是 00-dimension-matrix.md（WI2 产物）；其与本文件工作项行描述冲突时，先更新矩阵，再同步回写本文件。
- 书写约定（延续头部）：未来交付物路径不加反引号；已存在的仓库内文档路径用反引号，使 check-doc-links 对本文件持续可校验。

## Deep Audit Record

- dispatch #audit-2026-09-14-110620-nop-ai-agent-design-comparison-1-a3f91c2e to main-agent models={exec:mission-driver-2026-09-14-110620,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-nop-ai-agent-design-comparison-1-a3f91c2e findings=items：4×P1（plan 356 错误码零测试钉子、ERR_AI_CREDENTIAL_* 命名约定违规、tools→coder 分层倒置、gateway channel/login 范围漂移）登记 M5 工作项；9×P2（报告锚点行号漂移、矩阵措辞、裸 IAE、测试反模式、roadmap 机器校验声明不可满足、快照计数漂移等）登记 Follow-up Backlog。正向确认：28 份交付物齐全、72 接口计数/死点/死代码矩阵声明 10/11 成立、143 锚点抽查零机制失真、@Inject private 与 Spring 注解零违规。
- dispatch #audit-2026-09-14-110620-nop-ai-agent-design-comparison-2-27384d32 to opencode-go/deepseek-v4-flash models={exec:mission-driver-2026-09-14-110620,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-2026-09-14-110620-nop-ai-agent-design-comparison-2-27384d32 findings=items：1×P0（AiFileTool 任意文件读写）+ 5×P1（failed 执行仍发 EXECUTION_COMPLETED、同实例重复提交删胜出执行租约、FileToolBizModel projectName=".." 沙箱逃逸、shell &> 合并重定向输出翻倍、Feishu isBotMentioned @任意成员触发）登记 M6 工作项；16×P2（ThresholdBreaker HALF_OPEN 卡死、ConcurrencyRegistry 补偿竞态+表不收缩、connect-timeout 死配置、FALLBACK javadoc 过期、think 标记垃圾默认、apiStyle 死字段、hook fail-loud 被吞、null assistant NPE、FALLBACK 无上限、ThoughtStorage 任意路径、文件工具无原子写、MFA 分支零测试、getCapabilities 死面、Feishu 凭证契约矛盾、McpServerErrors 中文描述、docs 计数/锚点漂移）登记 Follow-up Backlog。round 1 全部 4×P1 + 9×P2 复核仍存在未修复。正向确认：引擎/可靠性/路由/routing 测试覆盖扎实、断路器与退避数学正确、shell 沙箱 fail-closed、SSRF 防护、@Inject private 与 Spring 注解零违规、beans.xml 类引用全部可解析。
