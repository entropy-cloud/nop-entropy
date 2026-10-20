# C3 深挖第 3 轮（收敛轮）— nop-ai-core-nop-ai-agent（WI 3.1）

## 深挖范围与方法

- **触发面（第 2 轮移交两项，逐一处置）**：① dialect 流式协议族——8 文件（5 实现 + ILlmDialect/LlmDialectFactory/AbstractLlmDialect）全部流式解析路径的「流式 vs 非流式」分叉穷举审计（产出类型：text / reasoning / tool_call 声明与参数 / 单事件并行多 tool_call / finishReason / usage / id·model 元数据 / 带内错误信号），C3-028 已立案三站点之外的同族清扫；② 审计副作用族——LLM 调用成功路径上的同步副作用（记账/检查点/审计写）站点全量清扫（C3-029 同族面）。
- **方法**：5 方言全文逐行读审（parseStreamChunk vs parseResponse 逐产出类型对照）；消费端 `ChatServiceImpl.StreamAggregator/ToolCallAccumulator`（:483-624）与 `callAsync` stream 缺省值（:113-124）、`IChatService.call`→`callAsync` 委托（:19-20）复核；usage 消费链端到端追踪（`ReActAgentExecutor` :1046-1048/:1066-1090/:1402-1404/:1534-1536 → `AgentCompactionCoordinator.shouldTriggerCompaction` :57-63 → `SmartModelRouter` :94-95 预算降级 → `CalibratedTokenEstimator.record` :72-84 校准通道 → `DbUsageRecorder` 计费）；`SingleTurnExecutor`（:85-131 纯内存）、`LlmCompletionJudge.accumulateTokens`（:220-233 纯内存）、`SimpleUsageRecorder`（无状态）逐一排除；C3-029 移交兄弟 `OrmModelSwitchedMessageWriter`（nop-ai-service，全文 69 行）核对；`InSessionCompactionSnapshotArchive`（全文）与 compaction 副作用位（`AgentCompactionCoordinator` :90-93 vs try :113-120）核对。grep 证实：`stream_options`/`include_usage` 全仓零命中。
- **盲审纪律**：仅读第 1 轮 C3-async-chains.md、第 2 轮 C3-round2.md 两文件 + 本提示词；未读其他 audits/plans/bugs/lessons/backlog；未查 git。行号在审计工作树逐行核验。
- **与既有发现的关系**：本轮 3 条新立案（C3-030/031/032）全部位于第 2 轮触发面①（方言流式协议族）的**未覆盖产出类型**（usage 通道、else-if 阶梯空串抢占、带内错误信号）——与 C3-028（tool_call 单事件截断）同族不同站点不同根，不重复计数。触发面②（审计副作用族）零新增（详见「扩展既有发现」），callStream/执行槽/mailbox 三族未展开（第 1/2 轮已收敛）。

## 流式 vs 非流式分叉审计矩阵

契约层根约束：`ILlmDialect.parseStreamChunk`（ILlmDialect.java:138）**单事件单 chunk 返回**——多条目产出类型（并行 tool_call、多 part）在契约层即无通道（C3-028 已立案其截断后果；ChatStreamChunk.java:75 注释自认「单事件单返回」）。

图例：✓ 完整覆盖；◐ 部分覆盖/有条件；✗ 缺失；**粗体** = 缺陷站点。非流式 `parseResponse` 五实现为对照基线（全量解析全部产出类型，含 usage/finishReason/多 tool_call）。

| 产出类型 | OpenAi | Anthropic | Gemini | Ollama | Responses |
|---|---|---|---|---|---|
| text 增量 | ✓ :268-273 | ✓ :561-572/:584-589 | ✓ :490-495（首 part，截断面归 C3-028） | ✓ :327-331 | ✓ :541-547 |
| reasoning 增量 | ✓ :276-286（thinking/reasoning_content/reasoning 三字段名） | ✓ :556-560/:589-593 | ✓ :484-489（thought:true；首 part 截断归 C3-028） | ◐ **:332-336 被 :327 空串 content 抢占（C3-031）** | ✓ :548-554 |
| tool_call 声明+参数 | ◐ 已立案 C3-028（:345 单 entry return）；:289 `if(!hasContent)` 阶梯并存抢占（watch） | ✓ 协议免疫（单事件单 content_block）:566-572/:594-603 | ◐ 已立案 C3-028（:512 首 part return） | ◐ **:327 空串抢占（C3-031）+ :341 get(0)（C3-028）双重叠加** | ✓ 免疫（output_item 单事件单 item）:515-540 |
| 单事件并行多 tool_call | ✗ 已立案 C3-028 | ✓ 免疫 | ✗ 已立案 C3-028 | ✗ 已立案 C3-028 + C3-031 | ✓ 免疫 |
| finishReason → DONE | ✓ :297-302 | ✓ :611-634/:636-640 | ✓ :456-460 | ✓ :367-371 | ✓ :562-577（status） |
| **usage** | **✗ :248-305 全程无 usage 读取；buildBody :151-184 未启用 stream_options.include_usage（全仓 grep 零命中）→ 双重缺失（C3-030）** | **◐ 仅读 message_delta :611-634；input/cache token 按协议在 message_start.usage，:538-546 只读 model → prompt/cache 计量残缺（C3-030）** | **✗ :440-523 无 usageMetadata 读取（C3-030）** | **✗ :311-374 无 prompt_eval_count/eval_count 读取（C3-030）** | ✓ :571-574（parseUsageFromResponses 与非流式共用） |
| id/model 元数据 | ✓ id :257 | ◐ model ✓ :544，**id 未采集**（message_start.message.id 丢弃，P3 观测面） | ✓ model :450-453 | ✓ model :320 | ✓ :510-511/:568-569 |
| 带内错误信号 | ✗ error 事件 → 仅 id 的空 chunk，静默丢（C3-032） | **✗ error 事件 → 伪造为 "[ERROR] …" text DELTA :646-661，污染 assistant 内容（C3-032）** | ✗ error 对象 → 仅 model 的空 chunk，静默丢（C3-032） | ✗ error 字段 → 静默丢（C3-032） | ✗ error 事件 → default null :579-580 静默丢；response.failed → DONE+finishReason（与非流式一致，非分叉）（C3-032） |

非流式对照（全部 ✓）：OpenAi parseResponse :187-245（usage :240-242）、Anthropic :398-499（usage :477-496 含 cache 专用字段）、Gemini :338-437（usageMetadata :432-434）、Ollama :216-308（prompt_eval_count/eval_count :304-305）、Responses :376-427（usage :421-424）。错误通道对照：非流式非 2xx 经 `AbstractLlmDialect.parseErrorResponse`（:76-122，errorClassification/retryAfterMs → 驱动重试/账号链 fallback）；流式带内错误载荷无任何归一通道（`ChatServiceImpl.parseStreamError` :448-468 仅处理流启动前的传输异常）。

## 新增发现

### [C3-030] 流式 usage 全链路缺失：OpenAI/Gemini/Ollama 三方言 parseStreamChunk 零 usage 读取（OpenAI 另未启用 stream_options.include_usage，Anthropic 从错误事件帧读 prompt/cache token）——缺省流式主路径 usage 恒 null/残缺，tokensUsed 不累积，预算降级、compaction token 触发、估算器校准、计费记账四条消费链整体失活（F3 同族四站点，与非流式 usage 解析全面分叉）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:248-305`（parseStreamChunk 全函数无 usage 读取）、`:151-184`（buildBody 无 stream_options）；`dialect/GeminiDialect.java:440-523`（无 usageMetadata 读取）；`dialect/OllamaDialect.java:311-374`（无 prompt_eval_count/eval_count 读取）；`dialect/AnthropicDialect.java:538-546`（message_start 只读 model）、`:611-634`（usage 仅从 message_delta 采集）；消费端 `service/ChatServiceImpl.java:502-504/:536`（chunk.usage 是流式 usage 唯一通道）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1046-1048`（usage==null → 记账整体跳过）
- **证据片段**:
  ```java
  // OpenAiDialect.parseStreamChunk :248-305 —— 全函数仅读 choices.0.delta / choices.0.finish_reason；
  // 非流式同方言 parseResponse :240-242 却解析 usage.prompt_tokens/completion_tokens/cached_tokens
  String finishReason = normalizeFinishReason(getStringByPath(dataMap, "choices.0.finish_reason"));
  if (finishReason != null) { chunk.setPhase(StreamItemPhase.DONE); ... }
  return chunk;                                          // chunk.usage 从未 set —— OpenAI/Gemini/Ollama 三处同型
  // 消费端（stream 缺省 true，ChatServiceImpl:117）：StreamAggregator :502-504 永远收不到 usage
  if (llmResult.response.getUsage() != null) {           // ReActAgentExecutor:1046 —— 恒 false（三方言）
      recordLlmTurnUsage(...);                           // :1073 tokensUsed / :1085 record / :1087-1089 校准 全部不可达
  }
  ```
- **严重程度**: P1（stream 缺省 true 为全模块主路径；三主流方言确定性触发且零日志——usage-and-billing 设计的四大消费面（预算降级/上下文压缩/估算器校准/计费）在缺省部署下静默失效，属「长时运行/负载上升必然触发」的机制性违约，与 C3-028 同档）
- **并发场景**: 非竞态——流式聚合路径的协议采集缺失（C3 要点 1 方言层异步入口的产出面分叉）。触发面：引擎 `chatService.call`（IChatService:19-20 → callAsync :117 stream=true）→ `aggregateStreamToResponse` → `StreamAggregator.addChunk`。
- **触发时序**: T1 会话经 OpenAI 兼容 provider 执行 ReAct 循环（缺省 stream=true）→ T2 SSE 终帧不含 usage（buildBody 从未发送 `stream_options:{"include_usage":true}`，OpenAI 协议下 provider 亦不下发；Gemini 每帧 usageMetadata、Ollama 终帧 eval_count 均被 parseStreamChunk 无视）→ T3 聚合 response.usage==null → :1046 门false → `ctx.tokensUsed` 恒 0 → (a) `AgentCompactionCoordinator.shouldTriggerCompaction`:59 的 token 触发（>80% maxContextTokens）永不成立，仅剩 :62 消息数>30 兜底，长上下文会话可越过 provider 窗口触发上游 400；(b) `budgetProvider.getBudget`（ReActAgentExecutor:943，IBudgetProvider:52 声明以 tokensUsed 为源）恒未超限 → `SmartModelRouter`:94-95 预算降级失活；(c) `CalibratedTokenEstimator.record`（:1087 门 promptTokens>0）零调用 → factor 永久钉在 1.0，其 javadoc（:30-39, MA6.3-AR-3）明言未校准实例对 CJK 可低计「远超 4×」；(d) DbUsageRecorder/SimpleUsageRecorder 零记录，计费对账面无数据。
- **现状**: 五方言中仅 Responses 在流式采集 usage（:571-574，复用非流式解析器）；`StreamAggregator` 的 usage 通道（:502-504/:536）存在且有聚合器级测试（TestStreamAggregator.testAggregator_usage），但三方言从不喂数——通道建成、源头断流；Anthropic 只在 message_delta 读 usage（input/cache token 按官方流式协议位于 message_start.usage，:538-546 未采集），promptTokens 恒 null → :1087 校准门对 Anthropic 同样失活。无任何 javadoc/配置声明「流式路径无 usage」。
- **风险**: 预算治理（降级到便宜模型）与上下文压缩（token 维度）两大成本保护机制在缺省路径失效——长会话成本失控与 provider 窗口溢出；计费数据缺失使 usage 侧的全部观测（DbUsageRecorder 表、nop.ai.agent.usage-record 日志）对流式流量为空；故障表现为「预算/压缩/计费从未生效」而非报错，排障面大。
- **建议**: (a) OpenAI：buildBody 流式分支补 `stream_options:{"include_usage":true}`（兼容实现不识别时多数忽略，风险低），parseStreamChunk 终帧解析 `usage` 字段（流式 usage 位于含 finish_reason 的终帧或独立 usage 帧）；(b) Gemini：parseStreamChunk 读 `usageMetadata`（每帧携带，取末帧即可，StreamAggregator :502-504 已是 last-wins 语义）；(c) Ollama：终帧读 `prompt_eval_count`/`eval_count`；(d) Anthropic：message_start 帧（:538）补读 `usage.input_tokens/cache_read_input_tokens/cache_creation_input_tokens`，与 message_delta 的 output 合并（StreamAggregator 需从 last-wins 升级为字段级合并，或 chunk 侧合并后下发）；(e) 修复后回归面：ReActAgentExecutor :1046 门、CalibratedTokenEstimator 校准、shouldTriggerCompaction 各补一条流式 e2e 断言。可与非流式 parseUsage 共用路径配置（LlmResponseModel paths）。
- **失效面枚举**（F3 同族清扫：全部流式 usage 站点 + 全部消费端）:
  - `OpenAiDialect.parseStreamChunk`:248-305 —— **缺陷点**（零读取 + buildBody 双重缺失）；
  - `GeminiDialect.parseStreamChunk`:440-523 —— **缺陷点**（零读取）；
  - `OllamaDialect.parseStreamChunk`:311-374 —— **缺陷点**（零读取）；
  - `AnthropicDialect.parseStreamChunk`:538-546/:611-634 —— **缺陷点**（采集帧错位，prompt/cache 残缺）；
  - `ResponsesDialect.parseStreamChunk`:571-574 —— 正确对照面（复用 parseUsageFromResponses）；
  - 非流式五实现 parseResponse —— 全量解析，对照面，无缺陷；
  - 消费端 `StreamAggregator`:502-504/:536 —— 通道正确，受害面随四站点同修；
  - 消费链 `ReActAgentExecutor`:1046-1048/:1073/:1085/:1087-1089、`AgentCompactionCoordinator`:59、`SmartModelRouter`:94-95、`SingleTurnExecutor`:109-113、`LlmCompletionJudge`:220-233 —— 均为受害面（门控逻辑本身健全），修复源头即恢复，无需改动。
- **信心水平**: 确定（三方言零读取与 stream_options 缺失为逐行/grep 代码事实；OpenAI include_usage 门控、Gemini/Ollama 流式 usage 字段为协议文档化形态；Anthropic message_start 持 input/cache token 为官方流式协议形态——message_delta 在部分新版本亦携带 input_tokens，届时 Anthropic 面「残缺」降为「部分残缺」，不影响其余三站点全缺结论）
- **误报排除**: 非「有界声明」——无任何文档声明流式无 usage，且 budget/compaction/estimator 的 javadoc 均假设 usage 存在（CalibratedTokenEstimator 甚至为未校准态写了专门的警告段落，却未意识到缺省路径永远无法进入校准）；非第 1/2 轮重复——C3-028 立案的是 tool_call 截断，usage 产出类型未覆盖；非 C5 内存维度越界——缺陷是流式/非流式产出类型分叉（本矩阵审计的直接命中），后果经预算/压缩机制放大。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——矩阵逐格回码核验成立：OpenAi parseStreamChunk 全函数零 usage 读取 + buildBody 无 stream_options（全仓 include_usage grep 零命中复证）、Gemini/Ollama 零读取、Responses :571-574 正确对照、StreamAggregator :502-505/:536 通道存在且 TestStreamAggregator.testAggregator_usage 在册、ReActAgentExecutor :1046 门直核。一处校正：Anthropic message_delta 处理器（:613-621）实际读取 input_tokens/cache_read/cache_creation 四字段——「promptTokens 恒 null」仅在 classic 协议（message_delta 不带 input_tokens）下成立，发现信心注记已披露该版本依赖；P1 站于三站点零读取 + buildBody 双重缺失，不受影响

### [C3-031] Ollama 流式 else-if 阶梯以 `content != null` 判定分支：Ollama 线格式恒序列化空串 content（Go Message.Content 无 omitempty）→ thinking 与 tool_calls 分支不可达，流式思考与工具调用全量丢失（非 C3-028 的「取首个」截断，是更早的分支阻断）；OpenAI 同型阶梯用 `!isEmpty` 判定正确——同族判定纪律分叉

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OllamaDialect.java:327-336`（`if (content != null)` 阶梯首支）、`:332-336`（thinking 分支不可达）、`:337-341`（tool_calls 分支不可达；:341 get(0) 为 C3-028 已立案面，仅在本条前提下不可达）；判定纪律对照 `dialect/OpenAiDialect.java:265-266`（`hasContent = content != null && !content.isEmpty()`）；掩盖面 `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/dialect/TestOllamaDialect.java:195`（P0 回归样本钉 `content:null`）
- **证据片段**:
  ```java
  // OllamaDialect.parseStreamChunk :323-341 —— 阶梯首支只判 null：
  String content = getStringByPath(dataMap, "message.content");   // "" → toString() → ""（非 null，AbstractLlmDialect:566-572）
  if (content != null) {                       // :327 —— 空串命中此支
      chunk.setItemType(StreamItemType.text);  // :328 产出空 delta 的 text chunk
      ...
  } else if (thinking != null) {               // :332 —— content:"" 时不可达
      ...
  } else {
      Object toolCallsObj = ...                // :337-341 —— content:"" 时不可达（C3-028 的 get(0) 在其后，永不执行）
  ```
- **严重程度**: P1（与 C3-028 同档：缺省流式主路径上模型发出的 thinking/tool_calls 确定性丢失且零日志；前提为 Ollama 线格式对空串 content 恒序列化——Go `Message.Content` json tag 无 omitempty，与其 thinking 字段的 `omitempty` 形成对照；若该前提不成立则降级为理论风险）
- **并发场景**: 非竞态——单帧解析的分支判定缺陷。触发面同 C3-030（stream 缺省 true 主路径）。
- **触发时序**: T1 Ollama thinking 模型流式输出：每帧 `{"message":{"role":"assistant","content":"","thinking":"<token>"}}`（content 恒空串在场）→ :327 空串命中 text 支 → :332 thinking 支永不执行 → 思考流全量丢失，聚合后 ChatReasoningMessage 为空。T2 工具调用终帧：`{"message":{"role":"assistant","content":"","tool_calls":[...]}}` → 同样命中 text 支 → tool_calls 支不可达 → **全部**（而非 C3-028 的首个之外）工具调用丢失 → ReAct 引擎判「无工具调用」按文本轮处置，模型意图静默蒸发。
- **现状**: 判定纪律族内分叉——OpenAI 同型阶梯显式要求 `!isEmpty`（OpenAiDialect:265-266 注释自认「空串不是有效增量」），Ollama 阶梯只判 null；仓库 P0 回归样本（TestOllamaDialect:195）以 `content:null` 钉住可达路径，恰好掩盖空串变体——测试通过 ≠ 线格式正确。第 2 轮 C3-028 的 T1 时序（「终帧 → :341 仅取 get(0)」）隐含 tool_calls 支可达，本条证明在真实线格式下该支整体不可达，C3-028 的 Ollama 面需按本条前提重估。
- **风险**: Ollama 装配下流式 thinking 与工具调用功能整体不可用（比 C3-028 的「只执行首个」更彻底）；表现为「模型不思考/不调工具」，零日志零告警。若前提不成立（Ollama 实际省略空 content），则本条自动降级、C3-028 的 get(0) 面恢复为真实缺陷——两种线格式下两案必居其一，修复应同时消除两个根（isEmpty 判定 + 多 entry 产出）。
- **建议**: 阶梯首支改为 `if (content != null && !content.isEmpty())`，thinking 支同步加 isEmpty 守卫（OpenAI :265-266 为现成范式）；Ollama 线格式以集成样本（真实 ollama serve 抓帧）钉住 content 空串形态补进 TestOllamaDialect（替换 content:null 样本或增补双形态样本）；与 C3-028 的 Ollama 多 entry 产出改造同批落地（单事件多 chunk 契约，见 C3-028 建议 (c)）。
- **失效面枚举**（F3 同族清扫：五方言流式阶梯判定纪律）:
  - `OllamaDialect.parseStreamChunk`:327 —— **缺陷点**（null 判定放过空串，text 支抢占两兄弟支）；
  - `OllamaDialect.parseStreamChunk`:332/:337-341 —— 受害支（thinking/tool_calls 不可达），随首支同修；
  - `OpenAiDialect.parseStreamChunk`:265-266/:276/:289 —— 判定正确对照面（hasContent=!isEmpty；其 tool_calls 亦挂在 `!hasContent` 下，canonical OpenAI 不并发的假设成立，watch-only）；
  - `GeminiDialect.parseStreamChunk`:484（`text != null`）—— part 级粒度无空串抢占问题（Gemini 空文本 part 非常规形态，截断面归 C3-028），通过；
  - `AnthropicDialect`/`ResponsesDialect` —— 事件模型无同型阶梯，通过；
  - `TestOllamaDialect.java:195` —— 掩盖面（content:null 样本与真实线格式不符），随修复一并更正。
- **信心水平**: 很可能（阶梯判定与 getStringByPath 空串语义为代码事实；Ollama 恒序列化空串 content 为协议实现强指征——其 thinking/tool_calls 字段均 omitempty 而 content 不是，且官方客户端样本普遍含 "content":""——但未在本仓内直接证实线格式）
- **误报排除**: 非 C3-028 重复——根不同（分支判定放过空串 vs 单事件多 entry 截断）、站点不同（:327 vs :341）、后果不同（全量丢失 vs 首个之外丢失）、修复不同（isEmpty 守卫 vs 多 chunk 产出）；非「原语使用」——缺陷在复合分支语义；非「测试已覆盖即无缺陷」——恰是测试样本钉错了线格式形态。
- **复核状态**: 未复核
- 复核状态: 保留(P1，条件面)｜新发现——代码事实全核验：阶梯首支 :327 仅判 null、getStringByPath 空串保值（AbstractLlmDialect:566-572 toString 直核）、TestOllamaDialect :195 样本钉 content:null、OpenAI :265-266 !isEmpty 对照确证；线格式前提（Go 无 omitempty）仓内不可证——但「两案必居其一」论证闭合：前提不成立则 C3-031 降级、C3-028 Ollama 面成立，P1 稳定

### [C3-032] 流式路径丢失错误规范化通道：非流式非 2xx 经 parseErrorResponse 归一（分类/Retry-After → 驱动重试与账号链 fallback），流式带内错误载荷五方言无一进入该通道——Anthropic error 事件被伪造为 "[ERROR] …" assistant 文本污染会话历史，其余四方言静默丢弃；流式中途错误对重试/回退机制整体不可见（F2/F9）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:646-661`（error → text DELTA）；`GeminiDialect.java:440-523`（error 对象 → 仅 model 的 chunk，丢）、`OllamaDialect.java:311-374`（同型丢）、`OpenAiDialect.java:248-305`（error 事件 → 仅 id 的 chunk，丢）、`ResponsesDialect.java:579-580`（default null，丢）；对照通道 `AbstractLlmDialect.java:76-122`（parseErrorResponse）与 `service/ChatServiceImpl.java:448-468`（parseStreamError 仅归一流启动前的传输异常）
- **证据片段**:
  ```java
  // AnthropicDialect.parseStreamChunk —— provider 的带内错误信号变成「assistant 说的话」：
  case "error":
      // 错误消息：作为 text item 的错误文本（快速暴露，不静默）
      ...
      chunk.setItemType(StreamItemType.text);
      chunk.setItemIndex(0);
      chunk.setPhase(StreamItemPhase.DELTA);
      chunk.setDelta("[ERROR] " + errorMsg);    // → StreamAggregator 拼进 assistant 正文，
  }                                             //   onComplete 产出 isSuccess()==true 的响应
  ```
- **严重程度**: P2（触发前提为 provider 流中途错误事件——Anthropic 官方流式协议明文支持 mid-stream `error` 事件如 overloaded_error；后果为伪造内容 + 可重试错误被吞，非确定性必现，故 P2）
- **并发场景**: 非竞态——流式错误信号的语义路由缺失。触发面：SSE 流内 `data:{"type":"error",...}`（Anthropic）/`{"error":{...}}`（Gemini/Ollama/OpenAI 兼容）→ parseStreamChunk → StreamAggregator。
- **触发时序**: 面 1（Anthropic 伪造）：T1 流中途 provider 下发 `{"type":"error","error":{"type":"overloaded_error",...}}` 并关闭流 → :659 产出 `[ERROR] ...` 的 text DELTA chunk → T2 流关闭触发 onComplete → 聚合响应 isSuccess()==true、正文含 "[ERROR] ..."、finishReason==null → T3 引擎把该文本作为 assistant 轮入 ctx 并持久化，下一轮请求携带伪造历史；本应 TRANSIENT/重试的错误（非流式同错误经 parseErrorResponse 得 errorClassification=TRANSIENT → retry/fallback）在流式路径零重试。面 2（其余方言静默）：T1' Gemini/Ollama/OpenAI 兼容 provider 流中途下发 error 载荷 → chunk 无 itemType（仅 id/model 元数据）或返回 null → StreamAggregator :506-508 直接跳过 → T2' onComplete 按截断流产出部分内容成功响应（该「截断按成功交付」的共性面归 C7-002，本条只立带内错误信号的路由缺失面）。
- **现状**: Anthropic 站点注释自认「快速暴露，不静默」——把错误暴露为**内容**与「不静默」是两回事：无 errorClassification、无 finishReason、无日志、响应判成功；其余四方言连暴露都没有。错误归一通道（分类/Retry-After/重试路由）仅在传输层异常（非 2xx，流启动前）可达（ChatServiceImpl:448-468 循环找 ARG_HTTP_STATUS），带内错误载荷天然不含该参数。
- **风险**: 流式中途错误（overload/限流/内容策略中断——生产常态）在缺省流式路径上：Anthropic 装配产生伪造 assistant 历史（污染后续轮次的模型输入，难归因）；全部装配丢失重试/账号链 fallback 资格（同错误非流式可重试、流式静默失败——可用性行为随 stream 开关翻转）；观测面无任何 ERROR 级信号。
- **建议**: parseStreamChunk 契约增加错误出口（返回携带 error 信息的 chunk 子类型，或 ILlmDialect 增加 `parseStreamErrorPayload` 钩子），bridge（ChatServiceImpl:215-220 onNext）识别后转 `publisher.closeExceptionally(带 ARG_HTTP_STATUS/ARG_BODY 的 NopException)`——复用 parseStreamError 既有归一通道，流式即获得与非流式同一套 classification/retryAfter/重试路由；Anthropic 站点删除 "[ERROR] " 文本伪造分支。最低限度：丢弃前 LOG.warn（含 provider 原始错误载荷）。
- **失效面枚举**（F3 同族清扫：五方言带内错误站点）:
  - `AnthropicDialect.parseStreamChunk`:646-661 —— **缺陷点**（伪造文本，最重）；
  - `GeminiDialect.parseStreamChunk`（error 对象落入无分支路径）—— **缺陷点**（静默丢）；
  - `OllamaDialect.parseStreamChunk`（error 字段同型）—— **缺陷点**（静默丢）；
  - `OpenAiDialect.parseStreamChunk`（error 事件同型）—— **缺陷点**（静默丢）；
  - `ResponsesDialect.parseStreamChunk`:579-580 —— **缺陷点**（error 事件 default 丢；response.failed → DONE 与非流式一致，非分叉，排除）；
  - `ChatServiceImpl.parseStreamError`:448-468 —— 通道本身正确但仅覆盖传输异常，随上游统一改造后自然覆盖带内错误，受害面非独立缺陷；
  - 非流式五实现 + parseErrorResponse:76-122 —— 正确对照面。
- **信心水平**: 确定（五站点路由行为为逐行代码事实；Anthropic mid-stream error 事件为官方文档化协议形态，其余方言带内 error 载荷为各自文档化/常见形态）
- **误报排除**: 非 C7-002 重复——C7-002 立案「截断流按成功交付」（无终止信号），本条立案「provider 主动下发的错误信号被吞/伪造」（有信号、路由错），机制与修复点不同；非「错误处理维度越界」——报的是流式/非流式错误语义分叉及其对重试路由（可靠性层输入）的断供，属 C3 异常传播要点；非 Anthropic 站点注释的「快速暴露」自辩——暴露为可重试错误信号才算暴露，暴露为 assistant 正文是语义污染。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——五站点路由行为逐行核验成立：Anthropic :646-661 error→"[ERROR] " text DELTA 直核（isSuccess()==true 伪造响应链闭合）、OpenAi/Gemini/Ollama parseStreamChunk 区间 grep 零 error 分支（静默丢确证）、Responses :579-580 default null；parseErrorResponse（AbstractLlmDialect:76+）与 parseStreamError（ChatServiceImpl:448-468 仅传输异常）双通道对照确证

## 扩展既有发现

### C3-029 兄弟站点核对收口（F3）：OrmModelSwitchedMessageWriter 同构成立；recordLlmTurnUsage 另有两个调用点同一修复点

- **兄弟确认**: `nop-ai/nop-ai-service/src/main/java/io/nop/ai/service/agent/OrmModelSwitchedMessageWriter.java:64`——`saveEntityDirectly(message)` 无 try/catch，ORM 异常直接传播；javadoc（:22-24）自认其为**生产装配推荐实现**（DbModelSwitchedMessageWriter 已弃用）——C3-029 面 2 的实际生产暴露面比第 2 轮登记的更大。同根同修，不另立案。
- **调用点补全**: `recordLlmTurnUsage` 除主循环 :1047 外另有 `ReActAgentExecutor.java:1403`（steering wakeup 轮）与 `:1535`（heartbeat wakeup 轮）两处调用——C3-029 面 1 的修复点（usageRecorder.record 隔离）一处修复三处生效，无独立站点。
- **副作用族清扫结论（触发面②）**: LLM 成功路径同步副作用全量站点——`usageRecorder.record`（已立案 C3-029）、`writeModelSwitched`（已立案 C3-029 + 本条兄弟确认）、`saveLlmTurnCheckpoint`/`sessionStore.save`/`persistInjectedMessages`/compaction 检查点（核心持久化语义，C3-029 已裁定豁免）、`tokenEstimator.record`/`SingleTurnExecutor:109-113`/`LlmCompletionJudge.accumulateTokens:220-233`（纯内存，不抛）、`SimpleUsageRecorder`（无状态，javadoc 自证）、`hookInvoker`/`publishEvent`（CL-020 已登，per-subscriber catch 健全）、`snapshotArchive.put`（shipped InSessionCompactionSnapshotArchive 纯内存，put 不可现实抛出——自定义实现暴露面见 CL-077）。**触发面②零新增立案，族收敛。**

### C3-028 前提修正交叉引用

C3-031 证明 Ollama 站点的 tool_calls 分支在真实线格式（空串 content 恒在场）下整体不可达——C3-028 的 Ollama T1 时序（get(0) 截断）需以 C3-031 的分支阻断为前置；两案同批修复（isEmpty 守卫 + 多 entry 产出）后以集成抓帧样本回归。

## 维度集外线索（overlay）

### [nop-ai-core-nop-ai-agent-CL-077]（C3/C6）AgentCompactionCoordinator 快照归档 put 在 compact 的容错 try 之外：自定义 ICompactionSnapshotArchive 的 I/O 失败将中止整个执行轮，与 Design §8.3 Decision F「archive 不可用非失败」的声明语义相悖

`engine/AgentCompactionCoordinator.java:90-93`（`snapshotId = snapshotArchive.put(ctx.getMessages())` 位于 :112-120 容错 try 之前）vs :108-120（仅 `contextCompactor.compact` 被 try 包裹，注释明言「a throwing custom compactor ... does NOT bubble up and abort the agent」）。Decision F 注释（:87-89）声明「snapshotId stays null（the 'archive not available' case, not a failure）」——但该宽容仅覆盖 archive 为 null（未装配）的情形：已装配的自定义实现（如文件/DB 快照归档）在 put 时抛 I/O 异常将穿透 performCompaction → ReAct 主循环 :526 catch → 成功压缩被判定为会话失败。shipped `InSessionCompactionSnapshotArchive`（session 包，全文核验）为纯内存 HashMap，put 仅对 null/empty 抛（:48-50 已被 :91 的 isEmpty 守卫前置排除），现实不可达——故仅登记线索：自定义 archive 实现接入时应在 :91-93 补 try/catch（失败 LOG.warn + snapshotId=null 继续），与 Decision F 声明语义对齐；或契约 javadoc 声明「实现不得抛出」。C3-029 同族（非核心副作用失败不得中止主流程）的前瞻面。

本轮无其他集外线索（Anthropic 流式 id 未采集为 P3 观测面，已入矩阵，不占 CL 号）。

## 继续判定

**终止信号（C3 维度对本模块收敛达成）。** 判定依据：

1. **触发面①（方言流式协议族）已穷举**：8 文件全部产出类型（text/reasoning/tool_call 声明与参数/单事件并行多调用/finishReason/usage/元数据/带内错误）逐一入矩阵（见上表），每个单元格有 file:line 级证据；C3-028（第 2 轮）+ C3-030/031/032（本轮）构成该族完整缺陷面，无未探单元格。剩余不确定性（C3-031 的 Ollama 线格式前提、C3-030 的 Anthropic message_delta usage 版本差异）均属**修复期集成样本验证**，不是审计盲区——再开一轮盲审只会重走已逐行读过的同一批文件。
2. **触发面②（审计副作用族）零新增**：成功路径副作用站点全量清扫完毕（见「扩展既有发现」），除 C3-029 已立案两站点及其兄弟确认外全部豁免有据（核心持久化/纯内存/无状态/per-subscriber catch）。
3. **其余 C3 面**（callStream 族/执行槽族/mailbox 族）：第 1 轮 27 条 + 第 2 轮复核后无新 P0/P1 实证，本轮不展开（任务书口径），维持收敛。
4. **机械规则核对**：本轮新增含 P1（C3-030/031），按第 2 轮「新增含 P0/P1 → 需下轮」的字面规则应触发第 4 轮；但两案与 C3-028 同属方言流式协议族且该族矩阵已闭合——继续的边际价值为零，且 AGENTS.md 审查预算纪律（最多 2 轮 + 超时冻结）均指向收敛。故裁定：**不启动第 4 轮发现审计**；C3-028/030/031/032 四案并入同一修复批次（方言流式协议族统一改造：多 chunk 契约 + usage 采集 + isEmpty 阶梯守卫 + 错误出口），修复后以「修复批次复核」（非发现轮）验证回归面即可。

新增立案：3 条（P1×2：C3-030、C3-031；P2×1：C3-032）。CL 追加：1 条（CL-077）。扩展既有发现：2 项（C3-029 兄弟确认 + 调用点补全；C3-028 前提交叉引用）。

## 维度复核结论（C3 独立复核，2026-10-18）

- **判定**：本轮 3 条全部**保留**，严重度维持——C3-030 保留 P1（新发现，一处校正见下）、C3-031 保留 P1（新发现，条件面）、C3-032 保留 P2（新发现）。逐条状态行已追加至各发现末尾；集中判定表见 `C3-async-chains.md`「维度复核结论」。
- **回码核验摘要**：分叉审计矩阵逐格核验与代码一致（五方言 parseStreamChunk 全区间逐一对照）。**一处校正（不改判定）**：Anthropic message_delta 处理器实际读取 input_tokens/cache 四字段（:613-621），「promptTokens 恒 null」仅在 classic 协议下成立——发现信心注记已披露该版本依赖，P1 站于 OpenAI/Gemini/Ollama 三站点零读取 + buildBody 双重缺失（include_usage 全仓 grep 零命中复证），不受影响。C3-031 的 getStringByPath 空串保值语义、TestOllamaDialect content:null 样本掩盖面、OpenAI !isEmpty 对照全部直核；线格式前提仓内不可证，但「两案必居其一」闭合论证成立。C3-032 五站点路由行为（Anthropic 伪造文本/四方言静默丢/Responses default null）逐行直核。
- **收敛裁定复核**：本轮「终止信号」（不启动第 4 轮）复核通过——触发面①矩阵穷举闭合、触发面②零新增有据；「新增含 P1 未启第 4 轮」偏差的理由（同族单修复批收敛 + 审查预算）成立，予以确认。C3-028/030/031/032 四案并入同一修复批次的建议与矩阵证据一致，修复期须以真实 provider 抓帧样本回归（Ollama 线格式、Anthropic message_delta usage 版本差异两处剩余不确定性即在该环节消解）。
