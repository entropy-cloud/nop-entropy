# C1 深挖第 2 轮 — nop-ai-core-nop-ai-agent（WI 3.1）

## 深挖范围与方法

- **深挖面（任务指派六项，逐一处置）**：① dialect 包注册变更面；② persist 层并发；③ simplifier 中间态；④ conflict/contribution 裁决中间态；⑤ compact 压缩中间态；⑥ hook/middleware 链执行中的共享可变状态。
- **方法**：逐文件读审 `nop-ai-core` 的 `dialect/`（8 文件全文：LlmDialectFactory/AbstractLlmDialect/OpenAi/Anthropic/Gemini/Ollama/Responses/ILlmDialect）、`persist/`（4 文件全文）、`simplifier/`（3 文件全文 + 全库 grep 调用方）；`nop-ai-agent` 的 `conflict/`（7 文件，FailFastStrategy/InMemoryWriteIntentRegistry 全文复读）、`contribution/`（6 文件）、`compact/`（14 文件：PipelineCompactor/ReferenceCompactionStrategy/Layer2TurnPruningStrategy/MicroCompressionCompactor/Layer3FullSummaryStrategy/ToolResultTruncator/InMemorySpillStore 等逐文件）、`hook/`（7 文件）+ `middleware/`（7 文件）+ `engine/AgentHookInvoker.java`（全文 279 行）；adjacency 复读 `tool/DefaultBackgroundTaskRegistry` Entry 字段群、`engine/AgentPromptAssembly`、`skill/SkillResolver`+`LLMCurator`。
- **盲审纪律**：仅读第 1 轮五文件 + 本文件；未读 audits 其他目录/plans/bugs/lessons/backlog；未查 git 历史。行号已在审计工作树（nop-entropy-concurrency-audit）逐行核验。
- **与第 1 轮的关系**：第 1 轮 27 条（C1-001…C1-027）未发现重复立案对象；本轮六大深挖面均已在第 1 轮批次 A（dialect/persist/simplifier）与批次 E（16 治理小包）覆盖判定通过，本轮为逐面复读复核 + 邻接面扩展，**无新增 C1 立案**（详见下方处置表）。

### 深挖面处置表（六面 × 复核证据 × 判定）

| 深挖面 | 本轮复读证据 | 判定 |
|---|---|---|
| ① dialect 注册变更面 | `LlmDialectFactory.java:17-77`：`DIALECTS` 为 static final CHM，`register(apiStyle,dialect)`=put（覆盖）、`register(...,false)`=putIfAbsent、`getDialect` 每次 get（null→openai 兜底）。CHM 单操作原子，value 经 CHM put 安全发布，get 读到旧或新实例均完整初始化——第 1 轮 C1-003 失效面枚举的注记判定（「无原子切换点但无撕裂」）经全文复读维持；5 个内置方言 static 块注册（:19-25）启动期单线程 | 通过（维持第 1 轮判定） |
| ② persist 层并发 | `DefaultAiChatResponseCache.java:44-88`（exists→isExpired→delete→load 时序已被 CL-002/C5-002/C7-008 三维覆盖）；`DefaultChatExchangePersister.java:17-63`（textCipher/encryptEnabled 为装配期 setter 写，bean 发布链；`maybeEncrypt/maybeDecrypt` 纯函数；`calcRequestHash` 无状态）；load 侧反序列化产物为 per-call 新实例（:58-61 mutate 的是本线程私有对象） | 通过（无新面） |
| ③ simplifier 中间态 | `JsonSimplifier.java`/`XNodeSimplifier.java` 全文：仅 final Set 配置字段，simplify 纯函数新建结果容器；全库 main grep `JsonSimplifier|XNodeSimplifier` **零调用方**（休眠面，无共享态可竞争） | 通过（休眠纯函数） |
| ④ conflict/contribution 裁决中间态 | `FailFastStrategy.java:20-78`：无状态单例、纯函数 resolve（第 1 轮 C1-025/C1-026、C5-019 已盖 registry 侧复合操作与生命周期，strategy 本体无状态）；`InMemoryWriteIntentRegistry`（per-bucket monitor）/`InMemoryContributionRegistry`（内层锁 + C1-025 竞态已立案）复读维持 | 通过（C1-025/026 已立案，无新面） |
| ⑤ compact 压缩中间态 | `PipelineCompactor.java:56-146`：`current` 列表为 compact 调用栈内局部新建（:81/:116），per-strategy `rebuildContext` 每层新建 CompactionContext（:158-175），无跨调用共享可变态；`ReferenceCompactionStrategy.java:88-227` 无实例可变字段（仅 final threshold）；`Layer2TurnPruningStrategy`/`MicroCompressionCompactor`/`Layer3FullSummaryStrategy` 中间列表均为栈内新建（grep 复核）；`InMemorySpillStore`/双 archive 的 HashMap 跨线程触达面已由第 1 轮 CL-026/CL-070 登记为线索 | 通过（CL-070 线索维持，不重复立案） |
| ⑥ hook/middleware 链执行中的共享可变状态 | `AgentHookInvoker.java`（全文）：`executeWithMiddleware:61-82`/`executeExecutionMiddleware:113-141` 每次 new HookContext + new MiddlewareChain（per-invocation）；`MiddlewareChain.java:27-61` 不可变（index 递进以新建子链实现，无共享游标）；`DefaultHookRegistry` per-execute 新建（第 1 轮批次 E 清单 E1 判定通过）；hook 异常分级处置（:156-166）无共享态写入。**新发现一处契约缺口（非并发域）→ CL-075** | 通过（并发面）+ CL-075（功能契约） |

## 新增发现

本轮无新增 C1 立案。六项深挖面经逐文件复读 + 邻接面扩展（`DefaultBackgroundTaskRegistry.Entry` 字段群全 volatile 复核 :321-342、`AgentPromptAssembly` per-execute 栈封闭复核、`SkillResolver`/`LLMCurator` 无共享可变态复核），未发现第 1 轮 27 条之外的同族漏评站点；亦未产生对既有条目的新失效面/新触发序证据（无「扩展 C1-xxx」小节）。

## 维度集外线索（overlay）

### [nop-ai-core-nop-ai-agent-CL-075]（非并发域：功能契约）executeExecutionMiddleware 向 HookContext 硬编码写入伪 lifecyclePoint，ExecutionPoint 完全不进入上下文——执行级中间件无法感知实际触发点

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentHookInvoker.java:120`（`HookContext mwCtx = new HookContext(AgentLifecyclePoint.PRE_REASONING, ctx);`——对全部 4 个 ExecutionPoint 统一硬编码）；对照 `middleware/ExecutionPoint.java:16-52`（PRE_LLM_ATTEMPT/POST_LLM_ATTEMPT/PRE_TOOL_ATTEMPT/POST_TOOL_ATTEMPT 四值独立枚举）、`hook/HookContext.java:11,26-28`（`lifecyclePoint` 为 final 字段、`getLifecyclePoint()` 是中间件读取触发点的唯一通道，无 attemptPoint 字段）。
- **形态**: 执行级中间件在任何触发点收到的 `hookCtx.getLifecyclePoint()` 恒为 `PRE_REASONING`——POST_TOOL_ATTEMPT 的中间件若按 point 分支（如只在 POST 点做结果改写），将误判为 PRE_REASONING 会话点；AttemptContext 虽携带 attempt 序号但不携带 point 标识，四点不可区分。深挖面⑥的复读中发现（hook/middleware 链执行上下文契约），无并发交错参与，故不入 C1 立案。
- **为何超出本维度**: 无共享可变状态/可见性问题（HookContext per-invocation 新建，第 1 轮清单 E15 通过）；缺陷是触发点语义在上下文载体中的丢失，属功能契约/正确性域。
- **建议归维**: 功能正确性复核（或 W3-1 设计符合性核查）。修复方向：HookContext 增加 `executionPoint` 字段（或 `setExecutionPoint`），`executeExecutionMiddleware` 回填真实 point；`getLifecyclePoint()` javadoc 声明执行级调用下的语义。
- **佐证强度**: 高——硬编码写入点唯一（:120），ExecutionPoint 四值与 HookContext 字段清单为逐行对照，grep `getLifecyclePoint()` 全库仅 HookContext 自身定义（无其他读者，当前无实际误读方，故为契约缺口而非活跃缺陷）。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-076]（C9）AbstractLlmDialect.matchesMapping 每次错误响应解析对每条 mapping 现场编译正则——CL-011（guardrail 三站点）在 dialect 错误映射路径的同族兄弟

- **位置**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AbstractLlmDialect.java:160-168`（`Pattern.compile(m.getMessagePattern().replace('_', ' '), MULTILINE|DOTALL|CASE_INSENSITIVE)` 在 `matchesMapping` 内、`parseErrorResponse:88-94` 的 per-mapping 循环中调用）；对照同包先例 `parseRetryAfterText:262-264`（`RETRY_HINT_PATTERN` 为 static final 预编译——正确姿势）。
- **形态**: 每次非 2xx 响应（`parseErrorResponse` 是全部 5 方言共用的错误规范化入口）对每条携带 `messagePattern` 的 errorMapping 现场编译一次 Pattern；同一失败 model-key 在重试链上每次 attempt 都重复编译。pattern 来自 llm.xml 配置、运行期不可变，完全可在 `LlmErrorMappingModel` 装载期（模型 init 临界区内）预编译缓存。与 CL-011（`RuleGraphGuardrail`/`RuleResultAggregator` 三站点）构成同族第三/四面（F3 同族：均为「配置期不可变 pattern × 运行期重复编译」）。
- **量化口径**: 错误路径频率 = 失败响应率 × attempt 数 × mapping 数；单次 `Pattern.compile` 为微秒级 + 分配，非热主路径（仅失败时触达），故 P3 线索而非立案。
- **为何超出本维度**: 无共享状态缺陷；属可量化的热（错误）路径分配浪费，归 C9。
- **建议归维**: C9 复核（与 CL-011 合并为「运行期 Pattern.compile 复发族」一次收口）。修复：`LlmErrorMappingModel` 增加惰性 `compiledPattern()`（volatile 缓存 + 双检）或在 LlmModel init() 期预编译。
- **佐证强度**: 高——编译点与循环结构逐行确认；static 预编译先例同文件存在（:262-264）。
- **状态**: 未复核

## 继续判定

**终止信号**。判定依据：本轮新增立案 0 条（无 P0/P1/P2/P3 新增）；六大深挖面全部完成逐文件复读复核并逐一给出处置（上表），其中五面维持第 1 轴「通过」判定、一面（hook/middleware 上下文）产出的缺口为非并发域契约线索（CL-075）；CL-076 为 P3 性能线索归 C9。第 1 轮 27 条 C1 发现 + 本轮复核共同构成 C1 维度在 nop-ai-core-nop-ai-agent 双工程上的收敛证据：载体清单五批次收官（批次 A–E）+ 深挖面复读无新同族站点。

---

## 复核结论（WI 3.1 C1 独立复核，2026-10-18）

- **零新增立案成立**：六大深挖面处置表逐行复核——面①（LlmDialectFactory CHM 注册）、面②（persist 装配期写 + per-call 反序列化实例）、面④（FailFastStrategy 无状态单例）、面⑤（compact 栈内新建）、面⑥（AgentHookInvoker per-invocation HookContext / MiddlewareChain 不可变子链，:120 硬编码 PRE_REASONING 抽查命中）经独立回码维持「通过」判定；CL-075 证据（AgentHookInvoker:120 唯一硬编码点、ExecutionPoint 四值、HookContext 无 attemptPoint 字段）抽查确认，佐证强度「高」成立。
- **一处事实性更正（不改判定）**：面③ simplifier「全库 main 零调用方（休眠面）」表述失实——`nop-ai-tools/src/main/java/io/nop/ai/tools/mcp/AiFileTool.java:110/:116` 存在 main 调用方（`new JsonSimplifier(...)`/`new XNodeSimplifier(...)`）。但两调用均为 **per-call 新建实例**（无跨线程共享实例），原条目并发结论「simplify 纯函数新建结果容器、无共享态可竞争」不受影响，判定「通过」维持；休眠面标注应更正为「有调用方、per-call 实例隔离」。
- **CL-076 抽查**：`AbstractLlmDialect.matchesMapping` 循环内 `Pattern.compile`（:160-168）与同文件 `RETRY_HINT_PATTERN` static final 先例（:262-264）逐行确认，P3 线索归 C9 成立。
- **C1 主维衔接**：本轮与第 1 轮 27 条共同构成 C1 维度收敛证据的结论成立；终止信号判定维持。第 1 轮 27 条独立复核结论见 `C1-shared-mutable-state.md`「维度复核结论」节（保留 26 / 降级 1 / 驳回 0，全部新发现）。
