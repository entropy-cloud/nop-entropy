# W5b 规则选择策略（XLang 规则 DSL）

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Review Consensus: 三轮独立 fresh-session 对抗性审查达成共识（R1: 2 Major 修复；R2: 3 Major 修复；R3: 0 Blocker 0 Major，consensus approve——终审 agent 核验：输出契约（selectedIndex 非 mandatory）与 NormalizeOutputExecutableRule 一致、谓词 `<expr>`/computed 裁定与 XLangASTBuilder 一致、ref ioc:optional 与 BeanDefinitionBuilder 一致、全部引用 live 核实）
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W5b）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.3/§3.4/§4.1/§五 Q10）、`ai-dev/plans/2026-08-15-0849-2-w5-model-class-routing-and-selection.md`（W5，Q10 拆 successor 裁定）
> Related: `ai-dev/design/nop-ai-gateway/01-architecture.md`、`nop-rule/nop-rule-core`（IRuleManager/RuleModel/RuleConstants）
> Mission: nop-ai-gateway-failover
> Work Item: W5b

## Purpose

落地 `ISelectionStrategy` 的第二种实现——**规则选择策略**：用平台既有 XLang 规则 DSL（`rule.xdef`，nop-rule）表达候选选择逻辑（成本/权重/时段/请求属性等），经 IoC bean 绑定，作为默认策略（健康度 + 并发感知 + 声明序）的可插拔替换。W5 已将 Q10 裁定为 successor plan（out-of-scope improvement，Successor Required: yes），本计划负责收口该 successor：DSL 形态、规则输入/输出契约、IoC 绑定方式、依赖归属与测试。

## Current Baseline

（live repo 核实，2026-08-15）

- **策略接口已就位**（W5）：`ISelectionStrategy`（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/ISelectionStrategy.java`）——`select(ChatRequest, List<ModelClassCandidate>, IModelClassHealth, Set<ModelClassCandidate>)` → 选中候选或 null（null = 无可用候选，调用方 fail-loud）；javadoc 明确"规则策略为同一接口的另一种实现，本接口即扩展点"。
- **默认策略已就位**（W5）：`DefaultSelectionStrategy`（健康度 + 并发感知 + 声明序 + 已尝试集跳过），`io.nop.ai.core.routing` 包。
- **候选/健康视图已就位**（W5）：`ModelClassCandidate`（provider/model/accountKey/accountBaseUrl/concurrencyLimit + `getModelKey()` = `provider:model`，value semantics equals/hashCode）；`IModelClassHealth.healthOf(candidate)` → `CandidateHealth`（circuitState / currentConcurrency / concurrencyLimit / `isAvailable()`）。
- **规则引擎平台既有**（nop-rule）：
  - `IRuleManager`（`nop-rule/nop-rule-core/.../core/IRuleManager.java`）：`getRule(ruleName, version)` / `loadRuleFromPath(path)` / `getRuleModel` / `executeRule`；实现 `RuleManager`（`io.nop.rule.core.execute.RuleManager`），bean id `nopRuleManager`（`rule-defaults.beans.xml`）。
  - `IExecutableRule.executeForOutputs(IRuleRuntime)` → `Map<String,Object>` 输出变量；`IRuleRuntime`（setInputs/getInputs/outputs/ruleMatch）。
  - DSL：`rule.xdef`（`nop-kernel/nop-xdefs/.../schema/rule.xdef`）——`RuleModel`（ruleName/ruleVersion/inputs/outputs/decisionTree/decisionMatrix/predicate + xpl）；规则文件解析路径 `RuleServiceHelper.buildResolveRulePath` = `resolve-rule:` + `{ruleName}` 或 `{ruleName}/v{version}`（`RuleConstants.RESOLVE_RULE_NS_PREFIX`），经 `ResourceComponentManager.loadComponentModel` 加载（测试可放 `src/test/resources/_vfs/nop/rule/{name}/v{version}.rule.xml`）。
  - nop-rule-core 依赖：`nop-rule-api`、`nop-xlang`、`nop-ooxml-xlsx`（Excel 规则导入，编译期依赖会随依赖传递）。
- **nop-ai-core 当前无 nop-rule 依赖**：`nop-ai/nop-ai-core/pom.xml` 仅有 nop-ai-api/nop-api-core/nop-http-api/nop-http-client-jdk/nop-xlang/nop-markdown/nop-autotest-junit——规则策略若落 nop-ai-core 需新增 `nop-rule-core` 依赖（无环：nop-rule-core 不依赖 nop-ai-core）。
- **Q10 遗留裁定面**（W5 Phase 1）：规则策略 DSL 形态 + IoC bean 绑定方式留本 successor plan 裁决；`ISelectionStrategy` 接口即扩展点，无需额外预留。
- **roadmap W5b 工作项**："规则选择策略（XLang 规则 DSL 形态 + IoC bean 绑定方式；ISelectionStrategy 的另一种实现，替换默认策略的可插拔扩展）"。
- 无任何规则策略实现代码（grep 实证 nop-ai-core 无 rule 引用）。

## Goals

- 规则选择策略类（`ISelectionStrategy` 实现）落地，DSL 形态 = 平台既有 `rule.xdef`（Q10 裁决），规则文件经 IoC bean 绑定（`IRuleManager` 注入 + ruleName/ruleVersion 属性）。
- 规则输入/输出契约明确并落档：规则可见输入 = 请求属性 + 候选集 + 健康视图 + 已尝试集；规则输出 = 选中候选标识；规则未命中/输出无效有显式语义（不静默）。
- 复用优先：消费 `IRuleManager` + 既有候选/健康视图类型，不新建第二套规则引擎或健康判定。
- 默认策略零回归（`DefaultSelectionStrategy` 与 W5 既有 routing 包测试（25 个 @Test，3 个测试类）不受影响）。
- 需求文档 §五 Q10 回填 + roadmap W5b 状态推进。

## Non-Goals

- 不改 `ISelectionStrategy` 接口契约、不改 `DefaultSelectionStrategy` 行为。
- 不新增第二套规则 DSL（拒绝自造 xpl 规则文件格式——平台既有 `rule.xdef`）。
- 不实现网关/本地适配器编排（W6/W7 消费策略 bean）。
- 不做规则热更新/多实例规则状态共享（显式 non-goal，沿用 §3.4）。
- 不扩展模型类候选集数据结构（W5 产物不动）。

## Scope

### In Scope

- Q10 裁决：DSL 形态（`rule.xdef`）+ IoC bean 绑定方式 + 模块归属与依赖形态（Phase 1）。
- 规则输入/输出契约设计（Phase 1 裁定 + 类 javadoc 落档）。
- 规则策略实现（nop-ai-core，`io.nop.ai.core.routing`）。
- 测试：策略语义测试 + IoC 绑定测试 + 规则文件加载测试 + 端到端（规则文件 → 策略 → router 消费）。
- 文档回填：requirement §五 Q10 + §3.3/§3.4 落地状态；roadmap W5b。

### Out Of Scope

- W6/W7 的编排消费（策略 bean 的注入点由 W6/W7 计划声明）。
- 候选权重/成本字段扩展（权重/成本由规则文件内表达，不扩展数据结构——W5 Deferred 已声明随本计划覆盖）。
- `docs-for-ai/` 使用文档（归 W8 OBS-03 统一收口）。

## Execution Plan

### Phase 1 - 裁定：DSL 形态 + IoC 绑定 + 模块归属 + 规则契约

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§五 Q10 裁定记录；正式回填在 Phase 4）

- Item Types: `Decision | Proof`

- [x] **DSL 形态裁定（Q10）**：二选一——① 平台 `rule.xdef`（`IRuleManager` 执行决策树/决策矩阵，输入/输出变量契约，规则文件 `resolve-rule:{name}[/v{ver}]`）；② 自定义轻量 xpl 规则文件。裁决考量落档（复用优先不变式：nop-rule 是平台既有规则 DSL，禁止再造第二套；rule.xdef 支持 predicate + decisionTree/decisionMatrix + 输出变量，覆盖"按成本/权重/时段/请求属性选择"需求；xpl 自定义形态会绕开 `IRuleManager`/`RuleModel` 编译缓存与既有治理路径）。
  - **推荐裁定（执行时确认）**：选 ① `rule.xdef`——复用优先硬约束（roadmap Cross-cutting"规则策略 DSL | XLang 规则 DSL（平台既有）"），且 W5 Deferred 明示"规则策略 = 同一策略接口的另一种实现"。
- [x] **规则输入/输出契约裁定**（**必须包含平台硬约束——审查 M-2/Major-1/Major-2 修复**）：已核实 `RuleModelCompiler`（`nop-rule/nop-rule-core/.../core/model/compile/RuleModelCompiler.java:64`）无条件包裹 `NormalizeInputExecutableRule`（`nop-rule/nop-rule-core/.../core/execute/NormalizeInputExecutableRule.java`），其强制语义：规则文件必须声明 **≥1 个 input**（`Guard.notEmpty`，`:35`）；策略设置未在规则文件声明的输入 → 运行期抛 `ERR_RULE_UNKNOWN_INPUT_VAR`（`:82`）；mandatory 输入缺失 → `ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY`（`:56-62`）；computed 输入被显式 set → `ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR`（`:86-90`）。**输出侧硬约束（审查 Major-1 修复）**：`NormalizeOutputExecutableRule`（`.../core/execute/NormalizeOutputExecutableRule.java:52-56`）在**规则未命中时也**无条件校验 declared outputs——`selectedIndex` **不得声明 `mandatory`**（否则未命中路径先抛引擎错误 `ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY`，契约分支①"未命中 → null"错误码漂移）；可声明 `type="int"`（非整数由引擎 cast 抛错 = 快速失败，属引擎域；越界/无效由策略域校验——分工落档）。因此契约 = **策略设置固定文档化的输入变量集（规则文件必须逐一在 `<input>` 中声明，测试强制）；输入值形态落档**：
  - 请求：`model`（string，`request.options==null` 时可为 null——`ISelectionStrategy.java:31` 允许，**不得 mandatory**）、`provider`（string，**不得 mandatory**）；
  - 候选集：`candidates`（`List<Map>`，每项 = `{index:int, provider, model, accountBaseUrl, concurrencyLimit}`——**不含 accountKey/apiKey**，见安全裁定；**不得 mandatory**——空候选池是合法状态）；
  - 健康视图：`health`（`Map<Integer, {circuitState, currentConcurrency, concurrencyLimit, available}>`，**键 = Integer**（与 candidates index 一致，审查 Minor-1 修复）；circuitState = `CircuitState` 枚举名 string；**不得 mandatory**）；
  - 已尝试：`attempted`（`List<Integer>`，候选 index 集；**不得 mandatory**——空集合法）。
  - **规则谓词编写模式裁定（审查 Major-2 修复）**：已核实 filter-bean 的 `name` 属性经 `XLangASTBuilder.buildPropExpr` 编译（`nop-xlang/.../XLangASTBuilder.java:256-280`）**只按 `.` 切分构造标识符/成员表达式，不支持 `[index]` 下标**——`<eq name="candidates[0].provider">` 会被解析为标识符 `candidates[0]`，作用域查找恒 null → 谓词恒不命中（`allowUnregisteredScopeVar(true)` 下运行期抛 `ERR_EXEC_SCOPE_VAR_IS_UNDEFINED`，fail-loud 非静默——审查 R1 措辞修正）。契约落档：规则文件访问列表/映射元素必须用 ① `<expr>` filter op（body 为完整 XLang 表达式，支持 `candidates[0].provider`、`health[0].circuitState`）或 ② computed 输入派生辅助变量（`computed="true"` + `<defaultExpr>`）；**禁止 filter-bean `name` 下标写法**。Phase 3 测试规则文件必须使用 `<expr>` 或 computed 输入模式（至少一种，两种皆含更佳）。
  - **输出契约**：规则输出变量名 = **`selectedIndex`**（int，候选列表 0-based index——审查 M-1/M-4 修复：**拒绝 `provider|model|accountKey` 复合 id**，因为 `ModelClassCandidate.getAccountKey()` 对备用账号即 apiKey 明文（`ModelClassCandidate.java:49-54`），进规则输入/日志 = 密钥泄露 + 密钥轮换导致 id 不稳定；index 在同一 select() 调用内稳定，规则与配置同为部署产物）。**安全裁定**：规则输入**禁止**含 accountKey（apiKey 明文），候选输入仅含 index/provider/model/accountBaseUrl/concurrencyLimit。
  - **语义分支（三个可区分分支分别落档）**：① `isRuleMatch()==false`（未命中）→ 策略返回 null（调用方 fail-loud；router 主动路径最终抛 `ERR_AI_MODEL_CLASS_SATURATED`——此交互显式落档，W6/W7 编排不得误读为静默降级）；② 命中但输出 map 无 `selectedIndex` → 返回 null（同①）；③ 命中且 `selectedIndex` 越界/非整数 → **fail-loud**（规则配置错误，不静默回退默认策略——Minimum Rules #24；非整数由引擎 cast 抛错透传 = 引擎域快速失败，越界由策略域抛 `ERR_AI_AGENT_INVALID_ARG`）。
  - **已尝试集语义**：`selectedIndex` 命中已尝试候选 → **fail-loud**（接口契约"已尝试不得重复返回"，规则作者需自行使用 attempted 输入；显式失败而非静默）。
  - **index 跨调用稳定性落档（审查 Minor-5 修复）**：语义分支③与已尝试判定隐含"同一游走执行内多次 select 的 index 含义一致"——`ModelClassRouter.currentPool()`（`ModelClassRouter.java:171-179`）扩展后重建 list 但内容/顺序稳定（equals 语义可换算），成立；javadoc 显式记录该前提。
  - **策略单例线程安全契约**：策略 bean 为单例，`select()` 每调用新建 `IRuleRuntime`（`RuleRuntime` 是 per-call 有状态对象），不得缓存为字段——javadoc 显式声明 statelessness（W6/W7 并发消费前提）。
- [x] **IoC 绑定方式裁定**：策略 bean 属性 = `ruleManager`（`IRuleManager`，setter 注入，NopIoC 无 private 注入约束）+ `ruleName`（必填）+ `ruleVersion`（可选，null = 最新）；bean 注册位置 = `nop-ai-gateway` 的 `ai-gateway-defaults.beans.xml`（部署面 opt-in，不覆盖默认策略）vs nop-ai-core 默认 beans（会影响所有引用方）——推荐 nop-ai-gateway 注册（规则名是部署级配置；core 只提供类 + 契约）。**ref 必须 `ioc:optional`（审查 Major-3 修复）**：NopIoC 在 bean 定义构建期即校验 ref（`BeanDefinitionBuilder.buildRefResolver`），非 optional 的 `<ref bean="nopRuleManager"/>` 在未加载 rule-defaults 的容器启动时抛 `ERR_IOC_UNKNOWN_BEAN_REF`——本 beans 文件既有先例（`nopLocalMessageService`/`nopUserChannelResolver`）全部 `ioc:optional`，对齐之；`ruleManager` 为 null 时的行为 = 显式 fail-fast（策略首用抛错，不静默——与 optional 注入不冲突：容器可启动，功能调用才失败）。
- [x] **模块归属与依赖裁定**：需求 §4.1 归属表"选择策略接口 + 默认/规则策略 | nop-ai-core"——规则策略类落 nop-ai-core `io.nop.ai.core.routing`，需给 nop-ai-core pom 新增 `nop-rule-core` 依赖（编译期，传递 nop-ooxml-xlsx）。裁决考量落档：归属表优先（规则策略是通用 LLM 选择能力，服务网关/适配器/agent 引擎所有形态）vs nop-ai-core 依赖重量（nop-ooxml-xlsx 传递）——若裁定依赖重量不可接受，备选 = 类落 nop-ai-gateway（需 requirement §4.1 修订，plan-first），推荐维持 nop-ai-core（nop-ooxml-xlsx 为平台标准模块，非外部重量依赖；`./mvnw test -pl :nop-ai-core` 增量验证）。
- [x] **错误码策略裁定**：规则输出无效/越界/命中已尝试 → 优先复用既有 `NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG`（W2 已确立的通用 invalid-arg 码，带 `ARG_MSG`）——不新增码（避免 core 错误码膨胀）；如需区分可经 `ARG_MSG` 描述（英文消息）。落档后实现不得再摇摆。
- [x] 消费点盘点（Proof）：grep 实证 nop-ai-core 无 `io.nop.rule` 引用；nop-rule-core 不依赖 nop-ai-core（无环）；新增依赖不触碰既有 nop-ai-core 消费方（W2/W3/W5 产物零改动）。

#### Phase 1 裁定记录（2026-08-15 执行期落档，全部经 live repo 核实）

- **[D1] DSL 形态 = ① 平台 `rule.xdef`（裁定）**：nop-rule 是平台既有规则 DSL（`rule.register-model.xml` 注册 `rule.xml`/`rule.yaml`/`rule.xlsx` 加载器，`resolve-rule:` 前缀经 `ResourceComponentManager.loadComponentModel` 解析——`nop-kernel/nop-xdefs/.../register-model.xdef` 注释与 `docs/dev-guide/vfs/model-loader.md` 实证），`IRuleManager`/`RuleModel`/编译缓存/治理路径齐备；xpl 自定义形态会再造第二套规则面（绕开 `RuleModelCompiler` 与既有解析缓存），违反复用优先不变式（roadmap Cross-cutting + W5 Deferred 明示"规则策略 = 同一策略接口的另一种实现"）。**拒绝②**。
- **[D2] 规则输入/输出契约（裁定，平台硬约束逐一 live 核实）**：
  - `RuleModelCompiler.compileRule`（`RuleModelCompiler.java:52-74`）无条件包裹 `NormalizeInputExecutableRule` + `NormalizeOutputExecutableRule`；`NormalizeInputExecutableRule:35` `Guard.notEmpty(inputDefines)` → **规则文件必须声明 ≥1 个 input**；`:82` 未声明输入被设置 → `ERR_RULE_UNKNOWN_INPUT_VAR`；`:56-62` mandatory 缺失 → `ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY`；`:86-90` computed 输入被显式 set → `ERR_RULE_INPUT_NOT_ALLOW_COMPUTED_VAR`。
  - `NormalizeOutputExecutableRule:52-56` **未命中时也**无条件校验 declared outputs → `selectedIndex` **不得 mandatory**（否则未命中路径先抛 `ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY`，契约分支①"未命中 → null"错误码漂移）；`:58-60` `type="int"` 非整数由 `BeanTool.castBeanToType` cast 抛错 = 引擎域快速失败。
  - 输入变量集（固定文档化）：`model`/`provider`（string，均不得 mandatory——`request.options==null` 合法）/`candidates`（`List<Map>`，每项 `{index:int, provider, model, accountBaseUrl, concurrencyLimit}`，**不含 accountKey**——`ModelClassCandidate.java:49-54` 实证 `getAccountKey()` 对备用账号即 apiKey 明文，安全裁定；不得 mandatory——空候选池合法）/`health`（`Map<Integer, {...}>`，**键 = Integer**（与 candidates index 一致）；circuitState = `CircuitState` 枚举名 string；不得 mandatory）/`attempted`（`List<Integer>`，不得 mandatory——空集合法）。
  - **谓词下标禁令（live 核实 `XLangASTBuilder.buildPropExpr:256-280`）**：只按 `.` 切分构造标识符/成员表达式，不支持 `[index]`——`<eq name="candidates[0].provider">` 被解析为标识符 `candidates[0]`，作用域查找恒 null/抛 `ERR_EXEC_SCOPE_VAR_IS_UNDEFINED`（`allowUnregisteredScopeVar(true)` 下 fail-loud 非静默）。规则文件访问列表/映射元素必须用 computed 输入（`computed="true"` + `<defaultExpr>`，`NormalizeInputExecutableRule:47-49` 实证）。
  - **执行期实证修订（2026-08-15 Phase 3）——`<expr>` op 在 XML 规则文件中不可用**：`FilterBeanToPredicateTransformer.visitUnknown:169-183` 从 `value` attr 读取已编译的 `Expression`，而 XML 规则文件的 `<expr>` body 文本不会被编译进该 attr（原始 XNode 无 value attr）→ 编译期 NPE（`XLangASTVisitor.visit` null node，实测）。`<expr>` op 仅适用于编程式构造的 filter bean（`ExpressionToFilterBeanTransformer` 路径）。**契约修订：XML 规则文件访问列表/映射元素一律用 computed 输入模式；computed 输入声明顺序必须在其依赖输入之后（scope locals 按声明序写入）**。本 plan 全部测试规则文件采用 computed 模式（契约"至少一种"满足）。
  - **输出契约**：输出变量名 = `selectedIndex`（int，候选列表 0-based index）。拒绝 `provider|model|accountKey` 复合 id（accountKey = apiKey 明文 + 密钥轮换导致 id 不稳定；index 在单次 `select()` 内稳定）。
  - **语义分支**：① `isRuleMatch()==false` → null（调用方 fail-loud；router 主动路径最终抛 `ERR_AI_MODEL_CLASS_SATURATED`）；② 命中但输出无 `selectedIndex` → null（同①）；③ 命中且越界 → fail-loud `ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG`（非整数由引擎 cast 抛错透传 = 引擎域快速失败，越界由策略域校验）。
  - **已尝试集语义**：`selectedIndex` 命中已尝试候选 → fail-loud（接口契约"已尝试不得重复返回"，规则作者需自行使用 attempted 输入）。
  - **index 跨调用稳定性前提**：语义分支③与已尝试判定隐含"同一游走执行内多次 select 的 index 含义一致"——`ModelClassRouter.currentPool():171-179` 扩展后重建 list 但内容/顺序稳定（equals 语义可换算），成立；javadoc 显式记录该前提。
  - **单例 stateless 契约**：策略 bean 为单例，`select()` 每调用新建 `IRuleRuntime`（per-call 有状态），不得缓存字段。
- **[D3] IoC 绑定方式（裁定）**：bean 属性 = `ruleManager`（`IRuleManager`，setter 注入）+ `ruleName`（必填）+ `ruleVersion`（可选，null = 最新）；注册位置 = nop-ai-gateway `ai-gateway-defaults.beans.xml`（部署面 opt-in，不覆盖默认策略；core 只提供类 + 契约）。**ref 必须 `ioc:optional`**（`BeanDefinitionBuilder.buildRefResolver` 构建期校验 ref，非 optional 在未加载 rule-defaults 的容器启动时抛 `ERR_IOC_UNKNOWN_BEAN_REF`；本 beans 文件既有先例 `nopLocalMessageService`/`nopUserChannelResolver` 全部 `ioc:optional`——`ai-gateway-defaults.beans.xml:74,80` 实证）；ruleManager 为 null 时首用显式 fail-fast（容器可启动，功能调用才失败，不静默）。
- **[D4] 模块归属与依赖（裁定）**：规则策略类落 nop-ai-core `io.nop.ai.core.routing`（§4.1 归属表"选择策略接口 + 默认/规则策略 | nop-ai-core"优先——规则策略是通用 LLM 选择能力，服务网关/适配器/agent 引擎所有形态），nop-ai-core pom 新增 `nop-rule-core` 编译依赖（pom 实证：nop-rule-core deps = nop-rule-api/nop-xlang/nop-ooxml-xlsx + test junit，无任何 nop-ai 引用 = 无环；nop-ooxml-xlsx 为平台标准模块，非外部重量依赖，接受传递）。
- **[D5] 错误码（裁定）**：规则输出无效/越界/命中已尝试 → 复用 `ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG`（英文描述），不新增码。
- **[P1] 消费点盘点（Proof）**：grep 实证 nop-ai-core `src/main/java` 无 `io.nop.rule` 引用（零残留）；`nop-rule-core/pom.xml` 无 nop-ai 依赖（无环）；W2/W3/W5 产物（reliability 包/routing 包/错误码）零改动。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全部裁定（DSL 形态/规则契约/IoC 绑定/模块归属）落档于本 plan 文件（内联落档），无未决项；Phase 4 统一回填 requirement doc §五 Q10。
- [x] DSL 形态裁定附理由与两选项比较；规则输出契约二选一无"或"残留。
- [x] Proof：依赖方向无环实证 + 既有消费方零改动确认。
- [x] `No owner-doc update required`（正式回填在 Phase 4）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 规则策略实现

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/`（新策略类）、`nop-ai/nop-ai-core/pom.xml`（nop-rule-core 依赖）、`nop-ai/nop-ai-gateway/src/main/resources/_vfs/nop/ai/gateway/beans/ai-gateway-defaults.beans.xml`（bean 注册）

- Item Types: `Fix | Proof`

- [x] 按 Phase 1 裁定实现规则策略类 **`RuleBasedSelectionStrategy`**（类名 Phase 1 裁定定名；`ISelectionStrategy` 实现）：注入 `IRuleManager` + ruleName/ruleVersion（bean 属性）；`select()` 构建规则输入（按 Phase 1 固定契约集：model/provider/candidates/health/attempted，**不含 accountKey**）→ **`ruleManager.getRule(ruleName, ruleVersion)`**（走 resolve-rule 版本化解析，与 bean 属性形态一致；不用 `loadRuleFromPath`）→ `newRuleRuntime()` + `setInputs` → `executeForOutputs(ruleRt)` → 按 Phase 1 语义分支处理（未命中/无输出 → null；越界/命中已尝试 → `ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG` fail-loud）；javadoc 完整记录规则输入/输出契约 + **单例 stateless 契约**（每次 select 新建 ruleRt，不得缓存字段）。
- [x] nop-ai-core pom 新增 `nop-rule-core` 依赖（按 Phase 1 裁定）；`./mvnw compile -pl :nop-ai-core -am` 通过。
- [x] bean 注册（按 Phase 1 裁定）：nop-ai-gateway beans 注册规则策略 bean（`ruleManager` ref `nopRuleManager` **`ioc:optional="true"`** + ruleName 属性，ioc:type=ISelectionStrategy，非默认）；ruleManager 为 null 时首用 fail-fast 语义落档（抛 `ERR_AI_AGENT_INVALID_ARG` + ARG_MSG，不静默）。
- [x] **无静默跳过**：规则未命中返回 null 是接口契约语义（非静默吞错）；输出无效/越界/命中已尝试全部显式抛错（`ERR_AI_AGENT_INVALID_ARG`），无空 catch。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 策略类 + 依赖 + bean 注册就位；`./mvnw compile -pl :nop-ai-core,:nop-ai-gateway -am` 通过。
- [x] 规则输入/输出契约在类 javadoc 中完整可查（执行者可据此编写规则文件）。
- [x] **无静默跳过**（Minimum Rules #24）：无效输出/未知候选/已尝试命中显式抛错，无空 catch/`continue`。
- [x] `No owner-doc update required`（文档回填在 Phase 4）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试

Status: completed
Targets: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/routing/`（策略测试）、`nop-ai/nop-ai-core/src/test/resources/_vfs/nop/rule/`（测试规则文件）、`nop-ai/nop-ai-gateway/src/test/`（IoC 绑定测试）

- Item Types: `Fix | Proof`

- [x] 测试规则文件（Phase 1 契约的样例：**使用 computed 输入派生辅助变量**（谓词下标访问禁令——审查 Major-2；`<expr>` op XML 不可用——执行期实证修订见 D2），**input 声明与策略契约输入集一致（≥1 个，candidates/health/attempted 均不得 mandatory）**，输出 = `selectedIndex` 声明 `type="int"` **不得 mandatory**）置于 `src/test/resources/_vfs/nop/rule/{ruleName}/v{version}.rule.xml`（已核实 `ResourceComponentManager` 可经 resolve-rule 加载该路径——文件名 = `v{version}.rule.xml` 置于 `{ruleName}/` 下，非目录分层；先例：`TestRuleExcelParser` 的 `.rule.xlsx` 于 `_vfs/nop/rule/test/`）。
- [x] 策略单元测试（Minimum Rules #25，直接 `new RuleManager()` 先例可用）：规则命中选中正确候选（`selectedIndex` 映射回候选）/ 规则未命中（`isRuleMatch()==false`）返回 null / 命中但无输出变量返回 null / 输出越界 fail-loud（`ERR_AI_AGENT_INVALID_ARG` 断言）/ 输出命中已尝试候选 fail-loud / 健康视图与候选集正确传给规则输入（输入变量断言：`candidates` 不含 accountKey、health 键为 Integer）/ 请求属性正确传入 / **未声明输入导致运行期显式错误码**（规则文件漏声明契约输入 → `ERR_RULE_UNKNOWN_INPUT_VAR` 断言，验证契约与平台硬约束一致）。
- [x] IoC 绑定测试（nop-ai-gateway）：**AppnLoader 不加载 autoconfig**（既有先例 `TestChannelMessageServiceIoC`），测试 beans 文件必须显式 `<import resource="/nop/rule/beans/rule-defaults.beans.xml"/>` 或自建 `RuleManager` bean，否则 `nopRuleManager` 注入 null——beans 加载后策略 bean 可解析、ruleManager 注入非 null；ruleManager 缺失场景显式失败。
- [x] **接线验证**（Minimum Rules #23）：`ModelClassRouter` 以规则策略实例运行时确实调用策略（router 经构造注入策略 → 端到端测试断言 `select` 被调用且返回规则选中候选）。
- [x] **端到端验证**（Minimum Rules #22）：一条全链测试——规则文件（`_vfs/nop/rule/`）→ `IRuleManager` 加载 → 策略 `select` → `ModelClassRouter`（构造注入规则策略 + 候选集）→ 选中候选下沉 `ChatOptions`（provider/model/accountKey/accountBaseUrl）→ 断言与规则选择一致。
- [x] 回归：W5 既有 routing 包测试（25 个）全绿；`DefaultSelectionStrategy` 测试零改动零回归。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全部新测试绿（策略语义 + IoC 绑定 + 接线 + 端到端全链）；既有 routing 测试零回归。
- [x] **端到端**：从规则文件到 router 选中候选的完整路径已验证（Minimum Rules #22）。
- [x] **接线验证**：router 运行时调用规则策略已断言（Minimum Rules #23）。
- [x] **无静默跳过**：fail-loud 分支错误码断言在案。
- [x] `No owner-doc update required`（文档回填在 Phase 4）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步 + roadmap 状态推进

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`、`ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`

- Item Types: `Follow-up`

- [x] `02-account-failover-requirement.md`：§五 Q10 回填（规则策略已落地，DSL 形态/IoC 绑定/模块归属裁定结果）；§3.3/§3.4 动态选择策略段补规则策略落地状态；§4.1 归属表核对（如模块归属裁定与归属表一致则记录核查结论）。
- [x] roadmap W5b 状态：`todo` → `planned`（**本计划经独立 draft review 通过时立即置**，非执行期）→ `done`（独立 closure audit 通过后，Phase 4 只做 `→ done`）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Q10 回填完成（裁定结果 + 落地状态与 live baseline 一致）。
- [x] roadmap W5b 状态推进到位（closure audit 通过后 `done`）。
- [x] `check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 `00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 规则选择策略（rule.xdef DSL 形态 + IoC bean 绑定 + 输入/输出契约）全部落地（nop-ai-core），无空洞组件（类有测试 + router 消费链接通）。
- [x] 端到端全链测试绿（规则文件 → IRuleManager → 策略 → router → 选中候选）；接线验证（router 调策略）通过。
- [x] 复用优先不变式验证通过（消费 `IRuleManager`/既有健康视图，无第二套规则引擎）。
- [x] 默认策略 + W5 既有 routing 测试零回归（`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` 绿）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [x] 受影响的 owner docs（requirement）已同步到 live baseline。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：策略被 router 运行时调用、fail-loud 语义、复用优先、零回归、Q10 处置可追溯）。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）router→策略→IRuleManager→规则文件的调用链在运行时确实连通（端到端测试断言），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl :nop-ai-core,:nop-ai-gateway -am`
- [x] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过（或按 mission 既有 lint 兜底通道判定——参考 W2-W5 记录：无有效 checkstyle 门禁，以 compile/test + grep 零残留为准）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（文档变更后执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（关闭时执行——新策略类在 W6/W7 前无生产调用方属空壳高危面，端到端 + 接线测试为其解除证据；若 scanner 报 high 且经人工核对为"有完整实现 + 端到端测试消费、仅无生产调用方"，按 W5 兜底裁定逐项记录后放行）

## Deferred But Adjudicated

### 候选权重/成本数据结构字段扩展

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 权重/成本由规则文件内表达（规则输入含候选属性，规则输出决定选择），不扩展 `ModelClassCandidate` 数据结构——W5 Deferred 已声明随本计划覆盖该维度。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 规则策略 bean 在 W6/W7 编排中的注入点由 W6/W7 计划声明（本计划只交付类 + 契约 + 部署面注册）。
- `docs-for-ai/` 规则策略使用文档归 W8 OBS-03 统一收口。

## Closure

Status Note: 全部 4 Phase 落地（裁定内联落档 / 规则策略实现 / 测试 / 文档回填），独立 closure audit closure-approve（10/10 PASS），Closure Gates 全勾选，验证命令全绿——关闭。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh general subagent（read-only，session `ses_ffc1a87a1ffe1SNZnKB2odT0Am`）
- Audit Session: `ses_ffc1a87a1ffe1SNZnKB2odT0Am`
- Evidence:
  - Phase 1 裁定内联落档齐全（D1-D5 + P1，plan §Phase 1 裁定记录），全部经 live code 核实；执行期契约修订（`<expr>` op XML 不可用 → computed 输入模式）已落档 D2 与策略 javadoc。
  - Phase 2 `RuleBasedSelectionStrategy` 实现真实（`getRule().executeForOutputs()` + 三分支语义 + fail-loud 错误码断言在案），nop-ai-core pom 新增 nop-rule-core（无环实证），bean 注册 `ioc:optional` 对齐既有先例。
  - Phase 3 测试：13 策略用例 + 2 IoC 用例全绿（surefire 实证）；接线验证（router 运行时消费规则策略——选中 backup-1 非默认策略首候选）与端到端全链（规则文件 → IRuleManager → 策略 → router → ChatOptions 四字段下沉 = 规则决策）断言在案。
  - Closure Gate 逐条 PASS（证据来源见 gate 对应项）：compile/test/install BUILD SUCCESS（审计独立复跑 `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` 1:56min 全绿）；checkstyle 0 violations（受影响模块；既有 nop-api-core 基线残留与 W2-W5 记录一致，无有效门禁）；`check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（0 high findings）。
  - Anti-Hollow 检查：router→strategy→IRuleManager→规则文件的调用链经端到端测试断言连通（accountKey=key-backup-1 下沉断言）；无空方法体/静默跳过/no-op（audit grep 实证 + scan-hollow 0 findings）。
  - Deferred 分类检查：Deferred But Adjudicated 唯一项 = 候选权重/成本数据结构扩展（out-of-scope improvement，理由已记录，Successor Required: no）；无 in-scope live defect 被降级。
  - 文本一致性：Plan Status `completed`、4 Phase `completed`、Exit Criteria 全 `[x]`、Closure Gates 全 `[x]`、daily log 收口记录一致。

Follow-up:

- 规则策略 bean 在 W6/W7 编排中的注入点由 W6/W7 计划声明（Non-Blocking Follow-ups，既有）。
- `docs-for-ai/` 规则策略使用文档归 W8 OBS-03 统一收口（Non-Blocking Follow-ups，既有）。
- no remaining plan-owned work.
