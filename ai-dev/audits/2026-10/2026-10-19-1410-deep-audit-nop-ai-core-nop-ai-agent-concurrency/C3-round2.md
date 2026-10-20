# C3 深挖第 2 轮 — nop-ai-core-nop-ai-agent（WI 3.1）

## 深挖范围与方法

- **深挖面（任务指派六项，逐一处置）**：① 方言层（dialect 8 文件）流式协议差异处理异步面；② prompt 模板异步装载；③ persist 异步写；④ budget/quota 异步记账；⑤ router 路由决策异步面；⑥ repair 的异步修复链。
- **方法**：dialect 8 文件全文读审（重点 `parseStreamChunk` 五实现逐行对照 + `AbstractLlmDialect` 错误归一/Retry-After 解析链），消费端 `ChatServiceImpl.StreamAggregator/ToolCallAccumulator`（:483-624）复读；`usage/DbUsageRecorder`（全文 209 行）、`session/DbModelSwitchedMessageWriter`（全文 167 行）+ 引擎消费链 `ReActAgentExecutor`（recordLlmTurn :1015-1090、resolveRoutedOptions :921-1008、主 catch :526-537）逐行核对异常路径；`router/SmartModelRouter`（:55-175）+ `LlmCallCoordinator` 守卫上限（resolveCircuitAware :888-941、MAX_FALLBACK_SCAN=64/MAX_FALLBACK_STEPS=16/MAX_EXECUTION_VETOES=3 :941-970）；`repair/` 8 文件（ChainRepairer/ToolSchemaParser/ArgumentValueCoercionStage 全文）；`skill/LLMCurator`（全文 341 行）+ `skill/SkillResolver`（全文）；prompt 装载与 persist 写路径按 C3 要点复读（无异步入口——同步 VFS 装载与同步文件 I/O）。
- **盲审纪律**：仅读第 1 轮五文件 + 本文件；未读其他 audits/plans/bugs/lessons/backlog；未查 git。行号已在审计工作树逐行核验。
- **与第 1 轮的关系**：第 1 轮 27 条集中于 callStream/执行槽/mailbox；本轮两条新立案（C3-028/C3-029）均在第 1 轮未覆盖的邻接面（方言流式 tool_call 协议处理、usage/审计 DB 副作用的异常传播），与 C3-006（nop-ai-core 缓存写失败）同族不同站点，不重复计数。

## 新增发现

### [C3-028] 方言流式 tool_call 单事件多调用静默截断：OpenAI 取首 entry 后 return、Gemini 取首 functionCall part 后 return、Ollama 仅取 get(0)——并行工具调用在流式默认路径丢失且无任何日志（F3 同族三站点，与非流式解析行为分叉）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:288-347`（`parseToolCallDelta`，:345 `return; // 单 chunk 返回，取首个 entry`）；`dialect/GeminiDialect.java:474-518`（parts 循环内 :496 text 首 part `return chunk; // 每个 chunk 通常只有一个内容`、:512 functionCall 首 part `return chunk`）；`dialect/OllamaDialect.java:338-364`（:341 `Object first = ((List<?>) toolCallsObj).get(0);`）；消费端 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:521-525`（StreamAggregator 按 itemIndex 逐 chunk 累积——被截断的条目根本不产生 chunk）
- **证据片段**:
  ```java
  // OpenAiDialect.parseToolCallDelta:320-346 —— delta.tool_calls[] 逐条解析，处理完首条即返回
  for (Object tc : toolCalls) {
      ...
      if (isAdded) { chunk.setPhase(StreamItemPhase.ADDED); chunk.setDelta(name); }
      else { chunk.setPhase(StreamItemPhase.DELTA); chunk.setDelta(args); }
      return; // 单 chunk 返回，取首个 entry        ← 其余 entry 静默丢弃
  }
  // OllamaDialect.parseStreamChunk:340-344 —— 终帧 message.tool_calls[] 仅取 get(0)
  if (toolCallsObj instanceof List && !((List<?>) toolCallsObj).isEmpty()) {
      Object first = ((List<?>) toolCallsObj).get(0);
  ```
- **严重程度**: P1（流式为 `ChatOptions.stream` 缺省主路径；Ollama 终帧批量下发多 tool_call 与 Gemini 并行 functionCall parts 为协议常规形态，截断为**确定性**发生且零日志零告警——LLM 发出的工具调用被静默丢弃属语义违约，与 C7-002「截断流按成功交付」同档）
- **并发场景**: 非竞态——单条 SSE 事件解析路径的协议处理缺陷（C3 要点 1 方言层异步入口的输入面）。触发面：`ChatServiceImpl.callStream`（缺省 stream=true 主路径）→ `dialect.parseStreamChunk` → `StreamAggregator.addChunk`。多线程无关，但经 `SubmissionPublisher` 派发链投递（链路 HB 正确，无并发因子）。
- **触发时序**: T1（Ollama 形态）：模型并行调用两个工具 → Ollama 在单个 SSE 终帧下发 `message.tool_calls=[tc1,tc2]` → :341 仅取 `get(0)`，tc2 不产生任何 chunk → StreamAggregator 只收敛出 1 个 tool call → 引擎只执行 tc1，tc2 的调用与参数永久丢失、无日志。T2（Gemini 形态）：模型返回含并行 functionCall 的 candidates[0].parts=[functionCall1,functionCall2] → :512 处理完 functionCall1 即 `return chunk`，functionCall2 丢失；更早的变体：若 parts 中 text 在 functionCall 之前，:496 先行 return，**整帧的 functionCall 全部丢失**。T3（OpenAI 兼容形态）：provider 在单个 `delta.tool_calls` 数组中批量下发两条声明（OpenAI 协议允许数组多元素，部分 OpenAI 兼容实现批量下发首见声明）→ :345 取首条后 return，第二条的 callId/name 声明丢失；若其后续 DELTA 片段独立到帧，将以无 callId/name 的裸累积器形式产出畸形 tool call（`ToolCallAccumulator.toToolCall`:597-623 产出 name=null 条目，交由 repair 链失败或以 null 名派发）。
- **现状**: 三站点在各自注释中自认该行为（「同一 SSE 事件内多 index 增量取首个」「每个 chunk 通常只有一个内容」「流式 tool_calls 单事件完整下发…取 first」）——注释陈述了实现，未声明丢弃后果；与非流式同 dialect 的完整解析形成分叉：`GeminiDialect.parseResponse:374-412` 明确「functionCall parts → 独立 ChatToolCallMessage」（全量），`OpenAiDialect.parseResponse` 与 Ollama 非流式同样解析全部 tool_calls（F3「同协议两种形态行为分叉」）。对照面：`AnthropicDialect` 免疫——Anthropic SSE 协议本身单事件仅承载单 content_block 事件；`ResponsesDialect.parseStreamChunk` 免疫——output_item 事件模型单事件单 item。
- **风险**: 并行工具调用（主流模型常规能力）在流式缺省路径上静默降级为单调用：轻则功能不完整（第二个工具的结果缺失，模型下一轮重试放大 token 成本），重则死循环形态（模型每轮重新发出同一组并行调用、每轮只有首个被执行，直至 maxIterations/force-stop 烧尽迭代预算）；name=null 的畸形 tool call 进入派发/repair 链产生难归因错误。因零日志，故障表现为「模型行为怪异」而非解析缺陷，排障面极大。
- **建议**: 统一修复方向——`parseStreamChunk` 契约改为可返回多 chunk（`List<ChatStreamChunk>` 或对 `StreamAggregator.addChunk` 逐 entry 回调）：(a) OpenAI：`parseToolCallDelta` 移除 ：345 的 return，循环产出每 entry 一个 chunk（或一次填装多 entry 的聚合 chunk）；(b) Gemini：parts 循环中 text/functionCall 都不再提前 return，改为收集后按 order 产出（与 `parseResponse`:374-412 的全量语义对齐）；(c) Ollama：终帧 `message.tool_calls` 逐条产出 ADDED chunk（复用 :354-360 的 arguments 全量通道逐条填装）；(d) 降级兜底：截断发生时至少 `LOG.warn`（计数被丢弃 entry 数），消除静默性。回归面：`StreamAggregator` 已按 itemIndex 支持多累积器（:486/:522-524），消费端无需改动。
- **失效面枚举**（F3 同族清扫：五方言 parseStreamChunk 全量）:
  - `OpenAiDialect.parseToolCallDelta`:345 —— **缺陷点**（单事件多 entry 截断 + 后续裸 DELTA 畸形累积）；
  - `GeminiDialect.parseStreamChunk`:496/:512 —— **缺陷点**（首 part 截断；text-先于-functionCall 变体丢失整帧调用）；
  - `OllamaDialect.parseStreamChunk`:341 —— **缺陷点**（get(0) 截断，终帧批量形态确定性触发）；
  - `AnthropicDialect.parseStreamChunk` —— 协议免疫（SSE 事件模型单事件单 content_block 增量），通过；
  - `ResponsesDialect.parseStreamChunk` —— 协议免疫（output_item/delta 事件单事件单条目），通过；
  - 非流式 `parseResponse` 五实现 —— 全量解析（Gemini :374-412 注释实证），对照面，无缺陷；
  - 消费端 `StreamAggregator`/`ToolCallAccumulator` —— per-itemIndex 累积器完备（:486/:522-524），被截断条目不可达为其受害面而非独立缺陷，随三站点同修。
- **信心水平**: 确定（三站点截断逻辑为逐行代码事实；Ollama 终帧批量与 Gemini 并行 functionCall parts 为协议文档化输入形态；OpenAI 数组多元素为协议允许形态，部分兼容实现实证批量）
- **误报排除**: 非「原语使用误报」——缺陷在三方言对同一数组/列表字段的截断式消费，非 JSON 解析原语问题；非「已声明边界」——站点注释仅陈述「取首个」的实现事实，未声明丢弃多调用条目的语义后果，且与非流式全量解析直接矛盾；非第 1 轮重复——第 1 轮 C3 面未覆盖方言 tool_call 流式协议处理（CL-031 登记的是消费端双通道翻倍累积的反向疑虑，与本条截断方向相反、站点不同）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——矩阵逐格回码核验成立：OpenAiDialect :345 循环内 return、GeminiDialect :496/:512 首 part return（text 先于 functionCall 变体整帧丢失确证）、OllamaDialect :341 get(0)；裸 DELTA→name=null 畸形累积经 ToolCallAccumulator.toToolCall（callId==null&&name==null&&args 非空即产出）复证；Anthropic/Responses 协议免疫对照确证

### [C3-029] LLM 热路径上的 opt-in DB 审计/记账副作用异常直接中止成功执行：DbUsageRecorder.record 抛错使已成功的 LLM 轮变为 failed 执行且丢失本轮 LLM_TURN 检查点；DbModelSwitchedMessageWriter 抛错在 LLM 调用前中止执行——审计副作用未与主流程隔离（每 turn 另附两次同步 DB 往返的延迟耦合）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/usage/DbUsageRecorder.java:150-176`（record 的 SQLException → `throw new NopAiAgentException`）、`:184-208`（resolveModelId 每 record 一次无缓存 SELECT）；`session/DbModelSwitchedMessageWriter.java:143-162`（writeModelSwitched 的 SQLException → throw）；消费链 `engine/ReActAgentExecutor.java:1046-1048`（recordLlmTurn 调用点，无 try/catch）、`:1066-1090`（:1073 tokensUsed 累加 → :1085 `usageRecorder.record(usageRecord)` → :1087-1089 estimator.record；:1050 `saveLlmTurnCheckpoint` 位于其后）、`:985`（`modelSwitchedMessageWriter.writeModelSwitched` 在 resolveRoutedOptions 内、LLM 调用前，:908 主迭代调用链）、`:526-537`（主循环 `catch (Exception e)` → `ctx.setStatus(AgentExecStatus.failed)`）
- **证据片段**:
  ```java
  // ReActAgentExecutor.recordLlmTurn —— LLM 已成功、assistant 消息已入 ctx（:1040-1044），
  // 记账/检查点/事件均在无容错的直线上：
  if (llmResult.response.getUsage() != null) {
      recordLlmTurnUsage(ctx, sessionId, agentName, request, routedOptions, llmResult);  // :1085 record 可抛
  }
  saveLlmTurnCheckpoint(ctx, sessionId, execStartTime, checkpointSeq, assistantMsg);     // :1050 record 抛则不可达
  ...
  } catch (Exception e) {                                                                // :526
      ctx.setStatus(AgentExecStatus.failed);                                            // :531 成功轮被判 failed
  ```
- **严重程度**: P2（与 C3-006「缓存写失败使成功响应异常交付」同族同档：非核心副作用失败升级为主流程失败；触发前提为 opt-in 装配 DbUsageRecorder/DbModelSwitchedMessageWriter + DB 抖动，但一旦装配即处于每 LLM 轮必经路径）
- **并发场景**: ReAct worker 线程同步执行记账/审计 DB I/O（无并发交错参与——缺陷是异常传播契约：C3 要点 2「回调中抛出的异常去哪了」的同步变体）。DB 故障窗口内全部并发会话的每次成功 LLM 轮都会命中。
- **触发时序**: 面 1（usage 记账）：T1 会话 S 的 LLM 调用成功返回、`ctx.addMessage(assistantMsg)` 与 `appendToolCallMessages` 已执行（:1040-1044）→ T2 `recordLlmTurnUsage`:1085 调用 `DbUsageRecorder.record`，INSERT 撞上 DB 抖动（连接池耗尽/锁超时）抛 `NopAiAgentException` → T3 异常穿透 ：1047 直达主循环 ：526 catch → 会话置 **failed**、EXECUTION_FAILED 事件发布；:1050 的 LLM_TURN 检查点被跳过（本轮恢复点退回上一轮），tokensUsed 已累加但账本无行（计费对账缺口）；已消耗的 LLM token 成本照付而执行被判失败。面 2（模型切换审计）：T1' 下一次迭代 `resolveRoutedOptions`:908 → :985 `writeModelSwitched` INSERT 失败抛出（审计消息）→ T2' 异常在 **LLM 调用发起之前** 中止执行——纯审计副作用直接否决整轮执行。
- **现状**: 两 writer 的 javadoc 均 self-declare「SQL failures are wrapped in NopAiAgentException and never swallowed」（DbUsageRecorder:46-48、DbModelSwitchedMessageWriter:56-59）——fail-loud 本身是显式选择，但该选择的作用域被声明为「记录失败要可见」，实现效果却是「记录失败中止主流程」：可见性与隔离性未被区分，javadoc 未声明对 ReAct 执行的传导后果。附带面：`resolveModelId`（:184-208）每 record 无缓存执行一次 `SELECT id FROM nop_ai_model`——每 LLM 轮共 2 次同步 DB 往返（SELECT+INSERT）串行插入迭代延迟，DB 慢时直接放大每轮耗时（provider 计费时长与引擎轮时长解耦失效）。
- **风险**: DB 抖动窗口内（生产常态事件）：装配了 usage/审计 writer 的部署中每次 LLM 成功轮都以 failed 终态收场——上层重试/恢复逻辑看到的是连续失败，实际是记账通道故障；LLM_TURN 检查点缺口使崩溃恢复点退化；批量并发下 DB 抖动被 N 个会话的重试风暴反馈放大（失败→重试→再记账→再失败）。计费面：tokensUsed 内存累加与 usage 表行数发散，对账困难。
- **建议**: 记账/审计副作用与主流程隔离：在 `recordLlmTurnUsage`/`writeModelSwitched` 调用点包 try/catch（失败 LOG.warn + 会话级失败计数，超阈值再升级告警——保持「可见」而不「中止」），或给 IUsageRecorder/IModelSwitchedMessageWriter 契约增加「advisory：实现方不得抛出」约定并给两个 DB 实现内部兜底；`resolveModelId` 增加按 (provider,model) 的进程级缓存（CHM computeIfAbsent，模型主键为慢变数据）。姊妹先例：nop-ai-core 侧 C3-003（日志失败→悬挂）与 C3-006（缓存写失败→异常交付）已裁定「非核心 I/O 副作用失败不得改变主流程交付语义」，本条为 agent 侧同族收口。
- **失效面枚举**（F3 同族清扫：ReAct 主循环上全部可抛副作用的站点）:
  - `ReActAgentExecutor`:1085 `usageRecorder.record` —— **缺陷点**（成功轮中止 + 检查点跳过）；
  - `ReActAgentExecutor`:985 `writeModelSwitched` —— **缺陷点**（调用前中止）；同族兄弟 `nop-ai-service` 的 `OrmModelSwitchedMessageWriter`（跨单元，同款「never swallowed」声明，建议同批核对）；
  - `ReActAgentExecutor`:1050 `saveLlmTurnCheckpoint` / `AgentToolDispatcher` 每 checkpoint 写穿 —— checkpoint 本体失败中止执行属**核心**持久化语义（恢复正确性依赖），不属本条豁免范围，维持现状；
  - `ReActAgentExecutor`:1088 `tokenEstimator.record` —— 纯内存，不抛，通过；
  - `hookInvoker.publishEvent`/`executeWithMiddleware` —— 第 1 轮 CL-020 已登记慢订阅者面（异常面 per-subscriber catch 健全），不重复；
  - nop-ai-core 姊妹先例：C3-003（chatLogger 悬挂）、C3-006（chatCache 异常交付）—— 同族已立案，本条为 agent 侧新站点，不重复计数。
- **信心水平**: 确定（抛出路径、调用链、catch 归宿、检查点跳过顺序均逐行核验；触发前提「DB 抖动 + opt-in 装配」为生产常态组合）
- **误报排除**: 非「错误处理维度越界」——本条报的是副作用的异常传播契约（记账失败是否应中止主流程），属 C3 异常传播要点；非「fail-loud 纪律反对」——不否定「不得吞异常」的最小规则，反对的是把 advisory 副作用置于与核心持久化同级的失败权重上（C3-006 先例裁定过同一区分）；非第 1 轮重复——第 1 轮 C3 站点集中在 callStream/执行槽/mailbox/恢复链，usage/审计 writer 的异常传导未覆盖。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：DbUsageRecorder.record SQLException→throw、recordLlmTurnUsage 内 usageRecorder.record 先于 :1050 检查点、主循环 :526 catch→failed；recordLlmTurnUsage 三调用点（:1047/:1403/:1535）与兄弟站点 OrmModelSwitchedMessageWriter:64 核验一致

## 扩展既有发现

### 扩展 CL-012（超时必须作用于所有等待点族）——第三兄弟：LLMCurator.curateBatch 直调 chatService.call 无超时包装

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/LLMCurator.java:98`（`response = config.getChatService().call(request, null);`——无 orTimeout/get(timeout) 任何包装）；家族头 CL-012（`compact/Layer3FullSummaryStrategy.java:176`、`completion/LlmCompletionJudge.java:90`）。
- **新增证据**: `LLMCurator.curateInBatches:137-153` 在注册表超过 `maxSkillsPerCall` 时**串行循环**逐批调用 `curateBatch`——每批一次无超时 LLM 全量调用，N 批 = N 次串联挂起面；失败批仅贡献 fail marker（:149-152），挂死批则阻塞调用线程无界。触发链：`DefaultAgentEngineConfig.curateSkills():230-233`（公开同步 API，调用方线程直连）。curation 为管理面 opt-in 功能，量级低于 Layer3/Judge 两兄弟，维持 CL 线索登记、不升级立案；修复时与本族统一套用 `callChatWithTimeout` 包装（降级分支已就绪：batch fail marker）。

## 维度集外线索（overlay）

本轮无新增集外线索（dialect 错误路径的 `Pattern.compile` 重复编译已登记于 `C1-round2.md` 统一流水 CL-076，此处不重复编号）。

## 继续判定

**需第 3 轮**。判定依据：新增立案含 1 条 P1（C3-028，方言流式 tool_call 协议族），按「新增含 P0/P1 → 需第 3 轮」机械规则触发。第 3 轮建议收窄为定向复核而非全域：(a) C3-028 修复方案的协议面验证（五方言 parseStreamChunk 多 chunk 契约改造的回归面 + Anthropic/Responses 免疫声明复核）；(b) C3-029 的兄弟站点清扫（OrmModelSwitchedMessageWriter 及未来 usage/审计实现族的契约约束）。其余四个深挖面（prompt 异步装载——同步 VFS 无异步面；persist 异步写——同步 I/O 且 C3-003/006 已盖；router 异步面——纯同步 + 三重上限守卫 MAX_FALLBACK_SCAN/STEPS/EXECUTION_VETOES 逐行核验；repair 链——单遍同步无循环收敛问题）均复核通过，不构成本判定依据。

## 维度复核结论（C3 独立复核，2026-10-18）

- **判定**：本轮 2 条全部**保留**，严重度维持——C3-028 保留 P1（新发现）、C3-029 保留 P2（新发现）。逐条状态行已追加至各发现末尾；集中判定表与跨维指针见 `C3-async-chains.md`「维度复核结论」。
- **回码核验摘要**：C3-028 三站点截断逻辑（OpenAiDialect :345 / GeminiDialect :496/:512 / OllamaDialect :341）与免疫对照（Anthropic/Responses 事件模型）逐行核验成立；「裸 DELTA→name=null 畸形累积」经 ToolCallAccumulator.toToolCall 守卫条件复证；与 C3-031（R3）的前提交链已在该条复核中闭合——Ollama 站点两案必居其一，修复须同批。C3-029 抛出路径（DbUsageRecorder.record → :1050 检查点跳过 → :526 catch→failed）、writeModelSwitched 调用前中止、recordLlmTurnUsage 三调用点（:1047/:1403/:1535）与兄弟站点 OrmModelSwitchedMessageWriter:64 全部核验一致。
- **与 R3 的衔接核验**：本轮「继续判定」（需第 3 轮）的机械规则触发成立；R3 对触发面①/②的收敛与本轮移交两项一一对应，无遗漏面。
