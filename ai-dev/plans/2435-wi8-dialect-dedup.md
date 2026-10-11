# 2435 WI8 dialect 家族结构消重（helper 下沉 + 表驱动，目标重校准 ~100-200 行）

> Plan Status: active（Phase 1-3 完成，待 closure audit）
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI8）
> Related: 2414（WI1）；deps: WI1（已完成）

## Purpose

消除 dialect 家族的私有 helper 重复（下沉 AbstractLlmDialect）并将 buildBody 选项映射数据驱动化——**在 roadmap 自身排除条款（事件词表不强行模板化）的约束内**；结构消重目标按实测重校准为 **净减 ~40-100 行 + 结构统一**（round 1 审查 Minor 2 收窄：Phase 3 绑定表行数中性偏负、以结构收益为主——roadmap 800-1200 目标的 delta 登记见 Current Baseline）。

## Current Baseline

（2026-10-11 执行时 HEAD 实测，Explore 子代理逐文件核对。roadmap 所引行号全部仍准确、零漂移。）

- **dialect 目录 8 文件 4,622 行**：AnthropicDialect 977、GeminiDialect 784、ResponsesDialect 770、AbstractLlmDialect 588、OllamaDialect 580、OpenAiDialect 493、ILlmDialect 352、LlmDialectFactory 78。5 个 dialect 全部 `extends AbstractLlmDialect implements ILlmDialect`。
- **重复 helper 实测（全部 private、目录外零消费者——纯内部重构）**：
  - `toInt` ×3（Anthropic:665-668 / OpenAi:407-410 / Responses:764-770）：语义逐字相同。
  - `getString` ×3（Gemini:781-783 / Ollama:577-579 / OpenAi:490-492）：**纯一行委托 `getStringByPath`**（基类 :452 已有）——命名 shim 可整删。
  - `reverseFinishReason` ×2（Anthropic:863-873 / Gemini:702-712）+ roadmap 未列的第三份 `reverseStatus`（Responses:659-671）：结构同构、映射表各异（stop→end_turn/STOP/completed 等）——只能基类表驱动 helper + 各 dialect 静态表，不能合成一张表；default 透传语义被 TestDialectBidirectionalConversion roundtrip 钉死。
  - **新发现**：`getByPath` ×3（OpenAi:403-405 与 Ollama:373-375 相同委托 BeanTool；Responses:483-493 为手写逐段下钻的不同实现——调用点仅 usage 路径纯 Map，可被 BeanTool 超集替代但需审计）；`buildMessages` ×2（OpenAi:453-470 vs Ollama:554-574，唯一差异 = Ollama 跳过 ChatSystemMessage）；`convertToolCalls` ×2（OpenAi:473-488 vs Ollama:414-428，语义差异 = OpenAi JSON.stringify(arguments) vs Ollama 直传 Map——合并需参数化）。
- **buildBody 实测**：5 家共 260 行，其中约 200 行是 provider 特有结构（Anthropic 轮合并+断点引擎 75 / Responses instructions+input+format 73 / Gemini 双层包裹 54）——roadmap 明示不强行模板化的部分。addOptionIfNotNull 调用 22 处（temperature/topP 全 5 家；topK 缺 Responses；stop 缺 Responses；maxTokens 经 resolveMaxTokens 全 5 家）。**frequencyPenalty(:79)/presencePenalty(:84) 存在于 ChatOptions 但任何 dialect 都未映射——数据驱动化时不得静默新增**。errorMappings 模板 = AbstractLlmDialect.parseErrorResponse :74-120（首条命中胜出→正则归类→defaultHeuristic）。
- **目标 delta 登记（roadmap 800-1200 不可达的排除性证据）**：helper 下沉净 ~55-64 行 + 选项绑定表行数中性偏负 ≈ **净减 ~40-100 行**（round 1 审查 Minor 2 收窄）；摸到 800-1200 必须动 parseResponse(~580)/parseStreamChunk(~610)/buildStreamChunk(~400)——即事件词表，roadmap 明示排除。按 Cross-Cutting 证据纪律：本 plan 登记目标重校准，roadmap 历史数字不改，WI21 收口对照。
- **行为钉（不可破坏面，core/src/test/.../dialect/ 合计 4,025 行）**：TestAnthropicCacheBreakpoints 262（WI20 断点引擎 12 test）、TestDialectBidirectionalConversion 906（21 test——reverse 透传与请求形态主钉）、TestNormalizeFinishReason 74、TestCacheKeyPlacement 141、TestCacheObservability 170、TestLlmDialectErrorResponse 207（WI22）、TestRetryAfterMessageSource 100 + 每家套件（Anthropic 415 / Responses 584 / OpenAi 398 / Gemini 227 / Ollama 231）+ 外围 TestResponsesDialectIntegration 502 / TestLlmErrorMapping 154。
- **安全边界**：private 全验证；路由机制 LlmDialectFactory 静态注册 5 家 null 回落 openai；不触碰 applyCacheBreakpoints/applyCacheHeaders/parseErrorResponse 链/事件词表。

## Goals

- Phase 1（纯下沉，~45-55 行）：toInt 下沉基类 protected static；getString 3 shim 整删改 getStringByPath；getByPath 统一（Responses 手写版调用点先审计）；buildMessages 以 skipSystemMessage 布尔合并。
- Phase 2（表驱动 reverse，~20 行）：基类 `reverseFinishReasonByTable(reason, table)` + 三张静态不可变表；default 透传逐字保留。
- Phase 3（buildBody 选项绑定表，~30-60 行）：声明式绑定（getter→body path，支持 generationConfig./options. 子 map 前缀）；仅覆盖现有 22 处平铺字段；**禁止引入 frequencyPenalty/presencePenalty**；provider 特有结构原样保留。
- 每个 Phase 后 `./mvnw test -pl nop-ai/nop-ai-core -am` 全绿（4,025 行行为钉为语义基线）；closure 时实测净减行数登记（对照 ~100-200 重校准目标）。

## Non-Goals

- 不触碰 parseResponse/parseStreamChunk/buildStreamChunk 的事件词表（roadmap 排除条款）。
- 不触碰 applyCacheBreakpoints/applyCacheHeaders/parseErrorResponse 链（WI20/WI21/WI22 语义钉）。
- 不新增 ChatOptions 字段映射（frequencyPenalty/presencePenalty 保持未映射现状——新增是行为变更非消重）。
- 不改 convertToolCalls 的 OpenAi JSON.stringify 语义（如合并仅参数化）。

## Scope

### In Scope

- nop-ai-core dialect 包内：AbstractLlmDialect 新增下沉 helper/绑定表、5 个 dialect 的 shim 删除与调用点改写。

### Out Of Scope

- ILlmDialect 接口签名、LlmDialectFactory 路由、llm.xml 配置面、事件词表解析、cache 引擎。

## Execution Plan

### Phase 1 - 纯下沉（toInt/getString/getByPath/buildMessages）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/`

- Item Types: `Fix`

- [x] toInt 下沉基类 `protected static Integer toInt(Object)`，3 个 dialect 私有版删除、调用点改基类引用
- [x] getString 3 个 shim 整删，调用点（各 dialect 内）改 `getStringByPath`
- [x] getByPath 统一（round 1 审查 M2 判据升级）：OpenAi/Ollama 私有版删除改直接调 BeanTool 路径族；**Responses 手写版 :483-493 的畸形载荷 null 容忍语义必须保留**（中间段非 Map 时手写版返回 null、BeanTool.getComplexProperty 抛 ERR_BEAN_UNKNOWN_PROP 使整个 parseResponse 失败——「调用点纯 Map」审计只覆盖良构载荷不充分）——改用 `BeanTool.tryGetComplexProperty`（hasProperty 先查、非 Map 中间段返回 null，语义精确等价）或下沉带 instanceof-Map 守卫的基类版，二选一在实现时按 tryGet 存在性裁定
- [x] buildMessages 合并（round 1 审查 M1 机制裁定）：基类新增 `protected List<Map<String,Object>> buildMessages(List<ChatMessage> messages, LlmModelConfig config, ChatOptions options, Function<ChatMessage, Map<String,Object>> convertMessage, boolean skipSystemMessage)`——**convertMessage 以函数参数传入**（方法引用 this::convertMessage），基类不 implements ILlmDialect、不加抽象方法（保住 test 侧 ProbeDialect/CustomTestDialect 两个只 extends 不 implements 的子类零破坏）；OpenAi 传 skipSystemMessage=false、Ollama 传 true
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 全绿

Exit Criteria:

- [x] toInt/getString 私有副本全仓零命中；getByPath 仅基类路径族；buildMessages 单实现
- [x] dialect 测试套件全绿（4,025 行行为钉零破坏）；No owner-doc update required（内部重构，Phase 3 后统一核对 module-groups）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 表驱动 reverse map

Status: completed
Targets: 同 Phase 1

- Item Types: `Fix`

- [x] 基类新增 `protected String reverseFinishReasonByTable(String reason, Map<String,String> table)`（**default 透传语义逐字保留 + null 入参显式返回 null**——round 1 审查 Minor 1：现 switch 对 null 抛 NPE，表驱动改返回 null，调用点传 getFinishReason() 恒非空故无行为差异，javadoc 声明）；Anthropic/Gemini 静态不可变映射表 + Responses reverseStatus 表迁移
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 全绿（TestDialectBidirectionalConversion roundtrip + TestNormalizeFinishReason 为钉）

Exit Criteria:

- [x] 三份 switch 实现删除，表驱动单实现；roundtrip 测试全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - buildBody 选项绑定表

Status: completed
Targets: 同 Phase 1

- Item Types: `Fix`

- [x] 基类新增声明式 OptionBinding applier（round 1 审查 M3：body 为 Map 非 XNode；**保留 Ollama:201-203/Gemini:325-327 的子 map「空则不挂载」语义**——applier 填充子 map、isEmpty-attach 原样保留，禁止惰性建空子 map）；绑定表支持二元 lambda 源（maxTokens 源是 resolveMaxTokens(options, modelConfig) 非纯 getter）与 LinkedHashMap 插入序（按各家现有 put 序声明表）；5 个 dialect 的平铺 addOptionIfNotNull 调用（22 处）改绑定表声明
- [x] Anthropic max_tokens 必填 4096 缺省、OpenAi prompt_cache_key、Responses tool_choice/text.format、各家包裹结构原样保留（不在绑定表面）
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 全绿 + closure 时实测净减行数登记

Exit Criteria:

- [x] 22 处平铺 addOptionIfNotNull 调用归零（grep 留档）；provider 特有结构行数不减少；**新增 absent-key 回归断言**（空 ChatOptions 时 body 不含 options/generationConfig 键——现有测试盲区，round 1 审查 M3）
- [x] dialect 测试套件全绿；实测净减行数登记到 log（对照 ~100-200）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 私有副本清零（word-boundary grep 留档：`private .* toInt(`、`private String getString(`、`getByPath` 于 5 dialect 文件、`buildMessages` 于 5 dialect 文件、`reverseFinishReason`/`reverseStatus` 私有实现）
- [x] 行为钉测试零破坏（TestAnthropicCacheBreakpoints/TestDialectBidirectionalConversion/TestNormalizeFinishReason/TestLlmDialectErrorResponse 等全绿）
- [x] 不存在被静默降级的 in-scope live defect；frequencyPenalty/presencePenalty 未被静默新增映射
- [x] owner docs（module-groups core 行如提 dialect 消重则同步；否则 no-change 登记）
- [x] 独立子 agent closure-audit 已完成并记录证据（首轮 REJECT 两项发现补齐后复审 APPROVE）
- [x] Anti-Hollow Check：绑定表驱动的请求体与重构前逐字段等价（TestDialectBidirectionalConversion roundtrip 全绿即证据）；无空方法体/静默跳过
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am`
- [x] 实测净减行数登记（wc 复算 dialect 目录前后对比，对照净减 ~40-100 行重校准目标，落 log）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-core --severity high` 无本次新增 high 发现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2435-wi8-dialect-dedup.md --strict` 退出码 0（自指验证）

## Deferred But Adjudicated

### 800-1200 行目标（parse/事件词表侧消重）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 自身排除条款（事件词表不强行模板化）锁死 parseResponse/parseStreamChunk/buildStreamChunk ~1,600 行的模板化空间——目标重校准为 ~100-200 行有排除性证据，delta 已按证据纪律登记供 WI21 对照。
- Successor Required: `no`
- Successor Path: 如未来裁定事件词表可模板化，另立 plan（需推翻 roadmap 排除条款——提请人工确认）

### convertToolCalls 合并（stringify 参数化）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 语义差异（JSON.stringify vs Map 直传）参数化收益 ~12 行——本 plan 不含该批次（另立 follow-up 或放弃）。
- Successor Required: `no`
- Successor Path: 无

## Non-Blocking Follow-ups

- Responses getByPath 调用点审计结论（纯 Map 与否）随 Phase 1 记录。
- parseUsage 双重载 shim（:498）为 WI21 遗留——本 plan 不动。

## Closure

Status Note: WI8 三 Phase 完成：私有副本清零（toInt/getString/getByPath/buildMessages/reverse 族，word-boundary grep 零命中）、三张 reverse 表逐字等价、22 处平铺调用归零、absent-key 回归钉 2 例新增；实测 dialect 目录 4,622→4,695 净 +73 行（表驱动基础设施 > 删除量）——诚实登记对照重校准目标。closure audit 首轮 REJECT（getByPath 批处理中断未落地 + absent-key 断言缺代码）已补齐并复验转 PASS。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11；首轮 REJECT 2 项 Blocker 级发现，补齐后复验）
- Evidence:
  - 私有副本清零 PASS（补齐后）：toInt/getString/buildMessages/reverse 表驱动全零命中；getByPath 于 OpenAi/Ollama 删除改 BeanTool 直调、Responses 手写版换 tryGetComplexProperty 委托（null 容忍语义保留）后复验零命中
  - 结构统一 PASS：基类 toInt/buildMessages(TriConvertMessage)/reverseFinishReasonByTable/applyOptionBindings（空则不挂载 javadoc）四件在位
  - 声明式绑定 PASS：22 绑定归零 + Anthropic 4096 缺省/OpenAi prompt_cache_key/Gemini 与 Ollama 非 empty 挂载/Responses format 保留；frequencyPenalty/presencePenalty 零映射
  - absent-key 回归钉 PASS（B2 补齐）：TestOllamaDialect.testBuildBodyEmptyOptionsOmitsOptionsKey + TestGeminiDialect.testBuildBodyEmptyOptionsOmitsGenerationConfigKey
  - 行为钉 PASS：core 401 测试全绿（399+2 absent-key 新增）；surefire 报告 BidirectionalConversion 21/0/0、CacheBreakpoints 12/0/0
  - 行数登记 PASS：净 +73 诚实登记（log 含「结构性统一达成但行数净增、未凑数」表述）
  - 三张反向表逐字等价 PASS（audit 对照 git diff 原 switch 原文）
  - 环境偏差：-am 全量链撞上游模块（TestKeyedList 于 nop-commons 等——并行 JDK25 会话 SB4 在途）持续，验证面维持 core+gateway 两模块

Follow-up:

- 800-1200 parse 侧消重（Deferred 已登记，需推翻排除条款——提请人工确认）
- convertToolCalls stringify 参数化（optimization candidate）
- nop-ai-core test 资源 TestNormalizeFinishReason 的 ProbeDialect 只 extends 不 implements——后续 dialect 接口演进时注意
