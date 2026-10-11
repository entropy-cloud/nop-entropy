# 2438 WI9 错误码归位——NopAiCoreErrors 代管项归还各模块

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI9（roadmap WI 执行顺序中当前项）；消费者属地普查 = 2026-10-11 本 plan 起草时全仓 grep 实测
> Related: WI4（RAG SPI 归位，completed）——`nop.err.ai.vector-length-mismatch` 孤儿化的成因；WI5/plan 2424（routing 归位，completed）——`nop.err.ai.model-class.saturated` 孤儿化的成因；W2/plan 2026-08-15-0604-2（reliability sink-down，历史）——`ERR_AI_AGENT_INVALID_ARG` 下沉 core 的原始裁定
> Draft Review: 2026-10-11 独立子代理对抗审查 round 1（agent_859eaeae）——3 Major（ARG_MODEL_CLASS 双重消费误判可致 core 编译失败 / error-handling.md:120 已记载 tools 码归属使 "No owner-doc" 预裁定为假 / TestGenErrorCodesYaml 生成清单滞后将静默丢 11 键）+ 4 Minor（计数勘误/Phase 2 缺验证门/A3 去向未登记/注册机制措辞），已全部修入正文；round 2（agent_1b567bcd）裁定"轻量再修后可执行"——1 Major（nop-ai-gateway/nop-ai-rag 不在 nop-bom 须显式 version）+ 2 Minor（Scope 计数 6→5 / 覆盖清单补 nop-ai-rag）已修入，转入执行

## Purpose

把 `NopAiCoreErrors` 中消费者已不在 core 的"代管"错误码归还到消费者属地模块的注册表，使 core 注册表只保留 core 自有错误码。全部 error code ID 与描述保持不变（ID 是持久化/日志契约，改名即行为变更）。roadmap 正文范围只点名 `nop.err.ai.agent.*`、`nop.err.ai.tools.*` 两个前缀；执行时普查发现 WI4/WI5 迁移遗留了两个同类孤儿码（`nop.err.ai.vector-length-mismatch`、`nop.err.ai.model-class.saturated`），属同一缺陷类，纳入本 plan（见 Adjudications）。

## Current Baseline

> 全部为 2026-10-11 live HEAD 全仓 grep 实测（`--include="*.java"`，排除 `/target/`）。

- `NopAiCoreErrors`（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java`）现持有约 40 个码，按消费者属地分四类：
  - **tools 代管（9 码）**：`ERR_AI_TOOLS_INVALID_THOUGHT` / `ERR_AI_TOOLS_INVALID_PROJECT_NAME` / `ERR_AI_TOOLS_THOUGHT_EMPTY` / `ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER` / `ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS` / `ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER` / `ERR_AI_TOOLS_INVALID_STAGE` / `ERR_AI_TOOLS_INVALID_MAX_RESULTS` / `ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID`。main 消费者全部在 nop-ai-tools（`FileToolBizModel`、`sequential_thinking/` 族 5 文件）；test 消费者 nop-ai-tools 4 文件 + core `TestNopAiCoreErrorsDescriptionEnglish`（ROUND4_CODES 18 码清单含 `ERR_AI_TOOLS_INVALID_THOUGHT`，`assertEquals(18, ...)` 钉数量）。core main 零消费者。
  - **agent 下沉码（1 个）**：`ERR_AI_AGENT_INVALID_ARG`（ID `nop.err.ai.agent.invalid-arg`）。W2（plan 2026-08-15-0604-2）将 reliability 类（ThresholdBreaker/ProviderFailoverQueue/StandardRetryPolicy/RetryContext/RetryOutcome）下沉 core 时**有意**随迁并双侧 javadoc 记录"仅持有类移动、ID 保留"；`docs-for-ai/02-core-guides/error-handling.md` 已记载该跨模块 ID 命名空间"属有意为之"。消费者：core main（reliability 5 文件 + `LlmConfigHelper` 3 处 throw）、gateway main（failover/routing/converter 8 文件）、agent main（30 文件）。core 不依赖 agent（agent→core 方向），字面"归还 NopAiAgentErrors"将造成 core→agent 分层倒置，编译不可达方案。
  - **WI5 孤儿码（1 个）**：`ERR_AI_MODEL_CLASS_SATURATED`（ID `nop.err.ai.model-class.saturated`）。WI5/plan 2424 把 routing 9 文件迁 gateway 后，其全部消费者在 gateway（routing 2 + failover 2 main 文件、test 5 文件：TestModelClassRouter / TestChatServiceFailoverAdapterStreaming / TestChatServiceFailoverAdapterNonStreaming / TestFailoverMetricsLocal / TestAiGatewayFailoverInterceptorStreaming）；core main 零消费者。gateway 既有注册表 `login/NopAiGatewayErrors` 为扫码登录专用（javadoc 自述），不宜混入。**注意 `ARG_MODEL_CLASS` 不能随迁删除**：core `LlmConfigHelper.java:31` static import 它并在 :375/:421 作为留守码 `ERR_AI_SERVICE_NO_DEFAULT_LLMS` 的参数名使用——core 保留原常量，`NopAiRoutingErrors` 自建同名本地常量（String 常量无注册冲突）。
  - **WI4 孤儿码（1 个）**：`ERR_AI_VECTOR_LENGTH_MISMATCH`。WI4 把 CosineSimilarity/RelevanceScore 迁 nop-ai-rag 后，消费者 = rag main `CosineSimilarity.java:58` + rag test 1 文件；core 零消费者。rag 已有注册表 `io/nop/ai/rag/NopAiRagErrors.java`。
  - **core 自有（留守）**：`nop.err.ai.service.*` 族、`ERR_AI_RATE_LIMITED`、prompt 表达式族、`ERR_AI_MANDATORY_INPUT/OUTPUT_IS_EMPTY`、`ERR_AI_FILE_CONTENT_NO_PATH`、`ERR_AI_SESSION_ID_IS_EMPTY/INVALID`（tools 亦消费但 core main 有消费者——向下消费合法）、`nop.err.ai.embedding.*` 5 码（消费者 = core `EmbeddingServiceImpl`）、`ERR_AI_CORE_INVALID_ARGUMENT/INVALID_STATE`。
- ARG 常量归属：`ARG_VALUE` 仅被 9 个 tools 码使用（随迁）；`ARG_FILE_PATH` 另被 `nop-ai-skills/nop-ai-code-analyzer/.../JavaCodeFileInfoGenerator.java:19` 引用（留守 core，tools 注册表自建同名常量）；`ARG_MODEL_CLASS` 被 core `LlmConfigHelper` 与 model-class 码双重消费（core 保留，gateway 注册表自建本地副本）；`ARG_MSG` 随 agent 码留守 core。
- i18n：`nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/zh-CN/nop-cli-errors.i18n.yaml` 按 ID 键控钉住 9 个 tools 码 + model-class 码 + vector-length 码的英文描述——ID 不变则键值不受迁移影响。**该 yaml 是生成物**：`nop-runner/nop-cli-core/src/test/java/io/nop/cli/TestGenErrorCodesYaml.java` 以 `ErrorCodeExtracter.saveToFile`（按 holder 类反射提取、**整体覆写不合并**）从固定 holder 清单再生，清单现含 `io.nop.ai.core.NopAiCoreErrors` 与 `io.nop.ai.coder.AiCoderErrors` 两个 nop-ai holder——迁移后若不把新注册表补入清单，任何人重跑该测试将静默丢失 11 个键（处置见 Phase 5 与 Adjudication A4）。
- 模块依赖方向（pom 实证）：tools→core、agent→core+toolkit、gateway→core+agent、rag→core。tools 无既有错误码注册表。
- 错误码注册机制：`ErrorCode.define` 为普通工厂（`nop-api-core`），无全局注册表、无重复定义检测——"每 ID 恰一处定义"是代码库约定，验收手段为 grep 断言（本 plan Exit Criteria 采用）；运行时 i18n/NopException 按 ID 字符串解析，与持有类无关。全仓无按 ID 反射查找这些码的代码（字符串字面量仅 2 处测试断言 + 上述 yaml 键）。

## Goals

- `NopAiCoreErrors` 中不再有任何"消费者全部在 core 之外"的错误码（grep 可验收：tools 前缀、model-class、vector-length 码零残留）。
- 新建 `io.nop.ai.tools.NopAiToolsErrors`（9 码 + ARG_VALUE + ARG_FILE_PATH 本地常量）；新建 `io.nop.ai.gateway.routing.NopAiRoutingErrors`（model-class 码 + ARG_MODEL_CLASS）；`ERR_AI_VECTOR_LENGTH_MISMATCH` 入既有 `io.nop.ai.rag.NopAiRagErrors`。
- `ERR_AI_AGENT_INVALID_ARG` 留守 core 的重裁定以 javadoc 形式记录在双侧（core 持有类 + agent 注册表头）。
- 全部错误码 ID、英文描述、参数占位符零变化；`nop-cli-errors.i18n.yaml` 的键集合不因本 plan 净变化（三个新注册表补入 `TestGenErrorCodesYaml` 生成清单，重生成相对当前工作树应为 no-op）。
- `docs-for-ai/02-core-guides/error-handling.md` 的英文描述覆盖模块清单与迁移后 holder 归属一致。

## Non-Goals

- 不改任何错误码 ID/描述/参数契约。
- 不移动 `ERR_AI_AGENT_INVALID_ARG` 的持有位置（重裁定留守，理由与重启条件见 Adjudications）。
- 不处理 `ERR_AI_AGENT_INVALID_ARG` 常量名与 ID 前缀不一致的 P3 命名问题（roadmap 明确 P3 归 WI12 顺带评估或不立项）。
- 不触碰 nop-rule-core 依赖、routing/failover 语义（WI5 已收口，gateway-failover roadmap 所有）。
- 不改 `nop-cli-core` i18n yaml 与任何 i18n 文件。

## Scope

### In Scope

- nop-ai-core：`NopAiCoreErrors.java` 删 11 码（9 tools + model-class + vector-length）+ `ARG_VALUE`，javadoc 更新（迁移注记 + agent 码重裁定）。
- nop-ai-tools：新建 `NopAiToolsErrors`，main 6 文件 + test 4 文件 import 更新，新增英文描述钉测试。
- nop-ai-gateway：新建 `routing/NopAiRoutingErrors`，main 4 文件 + test 5 文件 import 更新。
- nop-ai-rag：`NopAiRagErrors` 增 1 码，main 1 文件 + test 1 文件 import 更新。
- 文档：roadmap WI9 状态行 + M2 里程碑行、当日 log、`docs-for-ai/` 若有涉及错误码归属的章节则同步。

### Out Of Scope

- agent/toolkit 模块代码（`ERR_AI_AGENT_INVALID_ARG` 留守，其消费方零改动）。
- `nop-ai-skills/nop-ai-code-analyzer`（仅消费留守常量，零改动）。
- 任何错误码语义、异常类型、错误处理行为变更。
- `nop-cli-errors.i18n.yaml` 文件内容的主动改写（其工作树当前含并发会话未提交的 WI1 镜像删除，不属于本 plan；本 plan 只补生成清单保证未来再生成不丢键）。

## Adjudications

### A1 `ERR_AI_AGENT_INVALID_ARG` 留守 core（重裁定，偏离 roadmap 字面）

- roadmap 字面要求"归还 NopAiAgentErrors"，但 live 事实：该码消费者跨 core（reliability+LlmConfigHelper）/gateway/agent 三模块，且 W2 已裁定"仅持有类随 reliability 下沉、ID 保留"。core 编译依赖 agent 属分层倒置，字面方案不可达。
- 裁定：持有位置留在 core；语义重新归类为 core 自有码（其主要 owner 类 core reliability 在 core），`nop.err.ai.agent.*` ID 前缀作为历史契约保留。
- 重启条件：若未来 reliability 类迁回 agent（无在途 roadmap 立项），该码随迁 NopAiAgentErrors。

### A2 WI4/WI5 孤儿码纳入本 plan（scope 扩展）

- `nop.err.ai.model-class.saturated`、`nop.err.ai.vector-length-mismatch` 为本 roadmap 自身 WI4/WI5 执行遗留的"SPI 走了、配套留下"半迁移（roadmap WI4 明令避免的形态）。同属 WI9 目标缺陷类，纳入后 "core 只保留自身错误码" 才可验收；否则该目标对本 plan 恒假。
- 计 11 码迁移 + 1 码重裁定留守 + 其余实测留守，`NopAiCoreErrors` 净减 11 个码定义。

### A3 tools 码去向 = tools 模块新建注册表（偏离 roadmap 字面）

- roadmap 字面写 "归还 `NopAiAgentErrors`/toolkit 错误码注册表"，但 `nop.err.ai.tools.*` 的全部消费者在 nop-ai-tools 模块，且 nop-ai-tools 不依赖 nop-ai-toolkit（pom 实证）、无既有注册表；放 toolkit 会新建 tools→toolkit 依赖边（反向于 agent→toolkit 的既有方向），放 agent 同理不可达。
- 裁定：按消费者属地新建 `io.nop.ai.tools.NopAiToolsErrors`（与 `NopAiToolkitErrors`/`NopAiAgentErrors` 命名模式一致）。roadmap 的 "toolkit" 措辞与其 WI6 已纠正过的 v1 消费者普查假设同源，属基线措辞误差。

### A4 i18n yaml 生成清单同步（生成物 provenance 修正）

- `nop-cli-errors.i18n.yaml` 为 `TestGenErrorCodesYaml` 按 holder 清单整体覆写的生成物；只补清单、不在本 plan 内主动改写 yaml 文件（其工作树含并发会话未提交的 WI1 镜像删除，且 saveToFile 会把并发会话未提交的 OrmEqlErrors 变更一并卷入，提交边界不清）。
- 裁定：本 plan 补三个新 holder 入清单 + nop-cli-core pom 增三个 test 依赖；运行生成测试验证再生成输出与工作树现状一致（no-op 证明键集合不丢）。yaml 文件本身不纳入本 plan 提交。
- 重启条件：若并发会话后续提交 yaml 或其 holder 变更落 HEAD，下次触碰错误码的会话重跑生成测试即可收敛；清单已含全部相关 holder，无丢键风险。

## Execution Plan

### Phase 1 - tools 9 码迁移至新建 NopAiToolsErrors

Status: completed
Targets: `nop-ai/nop-ai-core/.../NopAiCoreErrors.java`、`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/NopAiToolsErrors.java`（新建）、nop-ai-tools main 6 文件 + test 4 文件、`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/TestNopAiCoreErrorsDescriptionEnglish.java`

- Item Types: `Fix`

- [x] 新建 `io.nop.ai.tools.NopAiToolsErrors`：9 个 `ERR_AI_TOOLS_*` 码逐字迁移（ID/描述/参数不变）+ `ARG_VALUE` + `ARG_FILE_PATH` 本地常量，javadoc 注记来源与 WI9 归位裁定
- [x] nop-ai-tools main import 更新：`FileToolBizModel`、`ThoughtData`、`ThoughtStage`、`SequentialThinkingBizModel`、`ThoughtAnalyzer`、`ThoughtStorage`
- [x] nop-ai-tools test import 更新：`TestToolErrorCodeConversion`、`TestFileToolBizModelProjectDir`（含 `NopAiCoreErrors.ARG_VALUE` qualified 引用 :52，逐文件处理勿只换 import 行）、`TestSequentialThinkingBizModel`、`TestThoughtStorage`（含 `NopAiCoreErrors.ARG_FILE_PATH` qualified 引用 :213/:221）；注意 `tools/utils/AiToolsHelper.java` 消费留守的 `ERR_AI_SESSION_ID_*`，其 `NopAiCoreErrors` import 保持不动
- [x] `NopAiCoreErrors` 删除 9 码与 `ARG_VALUE`；保留 `ARG_FILE_PATH`（code-analyzer 消费者）；删除处不留死注释
- [x] `TestNopAiCoreErrorsDescriptionEnglish`：ROUND4_CODES 18→17（移除 `ERR_AI_TOOLS_INVALID_THOUGHT`），类 javadoc 的数量说明同步
- [x] nop-ai-tools 新增 `TestNopAiToolsErrorsDescriptionEnglish`（对齐 core 模式：钉 9 码 ID + 英文描述 + 占位符契约，承接自 core 的描述契约覆盖）

Exit Criteria:

- [x] 全仓 grep `io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_` 零命中（main+test）
- [x] 全仓 grep `NopAiToolsErrors` 确认 9 码仅定义一次（无双定义）
- [x] `TestNopAiCoreErrorsDescriptionEnglish` 数量断言与清单一致且绿；新增 tools 描述钉测试绿
- [x] 文档同步项登记（编辑落 Phase 5）：`docs-for-ai/02-core-guides/error-handling.md` 覆盖清单行 "nop-ai-core（`NopAiCoreErrors`，含 `nop.err.ai.tools.*` 码）" 须改为 nop-ai-core 不含 tools 码 + 清单新增 nop-ai-tools（`NopAiToolsErrors`）
- [x] `ai-dev/logs/` 对应日期条目已更新（audit 后统一写入）

### Phase 2 - model-class 码迁移至新建 NopAiRoutingErrors

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/NopAiRoutingErrors.java`（新建）、gateway main 4 文件 + test 5 文件、`NopAiCoreErrors.java`

- Item Types: `Fix`

- [x] 新建 `io.nop.ai.gateway.routing.NopAiRoutingErrors`：`ERR_AI_MODEL_CLASS_SATURATED` 逐字迁移 + `ARG_MODEL_CLASS` **本地副本常量**（core 原常量保留——`LlmConfigHelper` 消费），javadoc 注记 WI5 迁移后归位
- [x] gateway main import 更新：`ModelClassRouter`、`RuleBasedSelectionStrategy`、`ChatServiceFailoverAdapter`、`FailoverProbeSupport`
- [x] gateway test import 更新（5 文件）：`TestModelClassRouter`、`TestChatServiceFailoverAdapterStreaming`、`TestChatServiceFailoverAdapterNonStreaming`、`TestFailoverMetricsLocal`、`TestAiGatewayFailoverInterceptorStreaming`；`TestRuleBasedSelectionStrategy`/`TestConcurrencyRegistry`/`TestRuleBasedSelectionStrategyIoC` 消费的是 agent 码非本码，不改
- [x] `NopAiCoreErrors` 删除该码；`ARG_MODEL_CLASS` **保留**（LlmConfigHelper :31/:375/:421 消费）

Exit Criteria:

- [x] 全仓 grep `ERR_AI_MODEL_CLASS_SATURATED` 仅 `NopAiRoutingErrors` 一处定义 + gateway 消费
- [x] `NopAiCoreErrors` 无 model-class 码残留；`ARG_MODEL_CLASS` 在 core 保留且 `NopAiRoutingErrors` 有本地副本
- [x] `./mvnw test-compile -pl nop-ai/nop-ai-gateway -am` 通过（phase 内编译门，勿等 Phase 5 才暴露）
- [x] No new test required：既有 `TestModelClassRouter` 等已钉该码的抛出行为与 ID，新建注册表为纯持有类无新行为（guide Rule #25 注记）
- [x] `ai-dev/logs/` 对应日期条目已更新（audit 后统一写入）

### Phase 3 - vector-length 码迁移至既有 NopAiRagErrors

Status: completed
Targets: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/NopAiRagErrors.java`、`rag/.../embedding/CosineSimilarity.java`、`rag test TestCosineSimilarityAndRelevanceScore`、`NopAiCoreErrors.java`

- Item Types: `Fix`

- [x] `ERR_AI_VECTOR_LENGTH_MISMATCH` 逐字迁入 `NopAiRagErrors`（追加，不破坏既有码序）
- [x] `CosineSimilarity.java` 与 `TestCosineSimilarityAndRelevanceScore.java` import 更新
- [x] `NopAiCoreErrors` 删除该码

Exit Criteria:

- [x] 全仓 grep `ERR_AI_VECTOR_LENGTH_MISMATCH` 仅 rag 一处定义 + rag 消费
- [x] `NopAiCoreErrors` 无该码残留
- [x] `./mvnw test-compile -pl nop-ai/nop-ai-rag -am` 通过（phase 内编译门）
- [x] No new test required：rag 既有 `TestCosineSimilarityAndRelevanceScore` 已钉该码抛出行为与 ID，注册表追加为纯持有变更（guide Rule #25 注记）
- [x] `ai-dev/logs/` 对应日期条目已更新（audit 后统一写入）

### Phase 4 - agent 码重裁定 javadoc 记录

Status: completed
Targets: `NopAiCoreErrors.java`（ERR_AI_AGENT_INVALID_ARG javadoc）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/NopAiAgentErrors.java`（头注释）

- Item Types: `Decision`

- [x] `NopAiCoreErrors` 的 `ERR_AI_AGENT_INVALID_ARG` javadoc 增补 WI9 重裁定：消费属地三模块、core→agent 倒置不可行、ID 前缀历史保留、重启条件（reliability 迁回则随迁）
- [x] `NopAiAgentErrors` 头注释 W2 段落增补一句 WI9 复核结论（避免读者按 roadmap 字面再度"归还"）

Exit Criteria:

- [x] 两处 javadoc 与 Adjudications A1 文本一致
- [x] 无任何代码行为变化（diff 仅注释）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新（audit 后统一写入）

### Phase 5 - 回归验证、roadmap 收口与提交

Status: completed
Targets: 受影响四模块、`ai-dev/backlog/nop-ai-structure-refactor-roadmap.md`、`ai-dev/logs/2026/10-11.md`

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway,nop-ai/nop-ai-rag -am` 全绿（gateway -am 级联覆盖 agent 构建；rag -am 覆盖 core）
- [x] 汇总 grep 断言：`NopAiCoreErrors` 现存每个码至少有一个 core main 消费者（"core 只保留自身错误码"的机械验收）；全仓无错误码双定义（每 ID 恰一处 `define(`，grep 约定验收）
- [x] i18n 生成清单同步（Adjudication A4）：`TestGenErrorCodesYaml` holder 清单增补 `io.nop.ai.tools.NopAiToolsErrors`、`io.nop.ai.gateway.routing.NopAiRoutingErrors`、`io.nop.ai.rag.NopAiRagErrors` 三类；`nop-runner/nop-cli-core/pom.xml` 增三个 test 依赖——`nop-ai-tools` 由 nop-bom 管理（:1432）不写 version，`nop-ai-gateway`/`nop-ai-rag` 不在 nop-bom 须按同 pom 既有先例写显式 `<version>2.0.0-SNAPSHOT</version>`；运行 `./mvnw test -pl nop-runner/nop-cli-core -Dtest=TestGenErrorCodesYaml -DfailIfNoTests=false`（按需 -am）验证再生成输出与工作树 yaml 一致（键集合零丢失），**yaml 文件本身不纳入本 plan 提交**
- [x] owner doc 同步：`docs-for-ai/02-core-guides/error-handling.md` 覆盖清单行改写——nop-ai-core 条目去掉"含 `nop.err.ai.tools.*` 码"，新增 nop-ai-tools（`NopAiToolsErrors`）与 nop-ai-rag（`NopAiRagErrors`，现清单遗漏、本 plan 向其迁入 1 码）条目，gateway 条目扩为（`NopAiGatewayErrors`、`NopAiRoutingErrors`）；核对 `docs-for-ai/04-reference/source-anchors.md` AIGW-006 行（prose 提及码名，无 holder 路径，预计无需改动，执行时确认）；`error-handling.md` 的 W2 段落已记载 agent 码跨模块 ID 命名空间"属有意为之"，与 A1 裁定一致无需改动
- [x] roadmap `## Work Item Status`：WI9 状态行更新为 `done`（含括注：11 码归位 + agent 码重裁定留守 + 2 孤儿码纳入 + tools 落点为 tools 模块注册表）；M2 里程碑行（WI4—WI9 全 done）状态改 `done`；重跑 mission-driver roadmap 解析核对条目数
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0（closure 时）
- [x] pathspec 限定提交（仅本 plan 触碰文件；仓库有并发会话在途改动且 `nop-cli-errors.i18n.yaml` 含并发未提交 diff，严禁 `git add -A`/无 pathspec amend）

Exit Criteria:

- [x] 上述命令全部通过且结果记入当日 log
- [x] roadmap 状态行与里程碑行更新后解析核对通过
- [x] `ai-dev/logs/2026/10-11.md` 条目含：改动摘要、验证命令与结果、A1/A2 偏差登记、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] 全部 in-scope 错误码迁移完成且 ID/描述/参数零变化（逐码 grep 验收）
- [x] `NopAiCoreErrors` 无代管码残留（机械断言：每现存码有 core main 消费者）
- [x] 无错误码双定义
- [x] 受影响四模块 `-am` 测试全绿
- [x] 不存在被静默降级的 in-scope live defect（A1 为显式裁定非降级，理由与重启条件已记录）
- [x] 受影响 owner docs 已同步到 live baseline：`docs-for-ai/02-core-guides/error-handling.md` 覆盖清单行与迁移后 holder 归属一致（Phase 5 项）
- [x] i18n 生成清单已含三个新注册表 holder，重跑生成测试键集合零丢失（yaml 文件本身不在本 plan 提交内）
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway,nop-ai/nop-ai-rag -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2438-wi9-error-code-relocation.md --strict` 退出码 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- `ERR_AI_AGENT_INVALID_ARG` 常量名与 `nop.err.ai.agent.*` ID 前缀的命名不一致属 P3（roadmap 排除项）；若 WI12 顺带评估裁定改名，涉及约 60 处机械替换，另行立项。

## Closure

Status Note: 11 个代管/孤儿码归位至消费者属地注册表（tools 9 → NopAiToolsErrors、model-class → NopAiRoutingErrors、vector-length → NopAiRagErrors），ERR_AI_AGENT_INVALID_ARG 经重裁定留守 core 并双侧 javadoc 记录；ID/描述/占位符逐字零变化；`NopAiCoreErrors` 40→29 码且经机械断言全数有 core main 消费者；i18n 生成清单补齐三个新 holder（重生成验证 11 键零丢失，副产品 rag 既有 3 键首次入 yaml——A4 预期 no-op 的偏差，系清单此前漏 rag holder 的既有缺口被修正，已登记当日 log）；四模块 -am 测试全绿；owner doc 同步；roadmap WI9 → done、M2 → done。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（agent_c715f76b，fresh session，2026-10-11）
- Evidence:
  - Phase 1-4 全部 Exit Criterion PASS：旧符号全仓 grep 0 命中（ERR_AI_TOOLS_/ERR_AI_MODEL_CLASS_SATURATED/ERR_AI_VECTOR_LENGTH 经 NopAiCoreErrors 前缀）；三个新注册表各 9/1/1 码仅定义一次；core 描述钉 17 码断言与清单一致；ARG_MODEL_CLASS core 保留 + routing 本地副本；LlmConfigHelper :31/:375/:421 原样编译通过
  - 契约零变化：11 码 ID+描述对 HEAD 做程序化字节级比对全部 VERBATIM-MATCH（含 vector-length 双空格）；11 ID 双定义 grep 恰各一处
  - 编译/测试：四模块 `test -am` BUILD SUCCESS（1556 测试类 0 失败 0 错误）；审计追加 `-o` 离线同 reactor 编译复验 exit 0（排除旧快照掩盖悬空引用）
  - yaml 生成链：TestGenErrorCodesYaml 清单含 4 个 nop-ai holder；pom 三依赖写法核验（nop-bom :1432 行号精确）；11 迁移键全部在位
  - owner doc：error-handling.md 覆盖清单行与 holder 归属一致；source-anchors AIGW-006 "无需改动"判断成立
  - Anti-Hollow：三个新注册表均被运行时真实消费（FileToolBizModel/ThoughtStorage 等 6 main 文件、ModelClassRouter:152 throw、CosineSimilarity throw），非孤立定义
  - Deferred 分类诚实：Non-Blocking Follow-up 仅 P3 命名一项，roadmap :30 明文排除，非 in-scope defect 降级
  - `check-plan-checklist.mjs --strict` 退出码 0（closure 前末次运行确认）
  - 裁定：APPROVE closure——0 Blocker / 0 Major / 3 Minor（Minor-2 计数笔误、Minor-3 log 提前打勾已修正；Minor-1 javadoc 挂载位置沿用 W2 既有结构，裁定不改）

Follow-up:

- 见 Non-Blocking Follow-ups
