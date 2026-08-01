# W5-1 GuardrailTestSuite — AttackPlugin + Grader 打分分离与攻击语料库

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W5-1；`ai-dev/design/nop-ai-agent/guardrail-contract.md` §增量 1；`ai-dev/analysis/agent-survey/2026-08-01-promptfoo-redteam-eval-analysis.md`
> Related: `2026-08-01-1437-2`（W2-3 三级失败升级，已 completed，其 `Deferred But Adjudicated` 显式把"完整 aegis 子系统（60+ 攻击类型语料、AttackPlugin、Grader rubric）"的 successor 指向本 W5-1 work item）；`2026-08-02-0900-3`（W5-3 BAIL，已 completed）
> Mission: nop-ai-agent-harness-evolution
> Work Item: W5-1

## Purpose

为 nop-ai-agent 的运行时 guardrail 执行补上"测试与验收"维度：把"防御能力"变成"可度量、可回归"的工程闭环。
nop 已有 guardrail 执行（`IContentGuardrail` / `PromptInjectionGuardrail` / security 6 层）但无系统化测试——本计划交付一个 **test-time 组件**（非运行时），采用 promptfoo 的 **Plugin（生成攻击）+ Grader（rubric 打分）分离** 范式，对既有 guardrail 做红队验收。

## Current Baseline

（2026-08-02 live repo 核对）

- `IContentGuardrail`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/IContentGuardrail.java`）是 guardrail SPI，方法签名 `check(GuardrailDirection, String content, AgentExecutionContext) → GuardrailResult`。
- `GuardrailResult`（同包）是密封式三态结果：`PassResult` / `BlockResult(reason)` / `ModifyResult(content)`。
- `PromptInjectionGuardrail`（同包）是唯一 production 实现：正则检测 prompt_override / role_hijack / exfiltration / invisible_char 四类注入，`GuardrailMode` OFF/REPORT/ENFORCE。
- `NoOpContentGuardrail` 是 shipped default（INFO-level awareness，见 `AgentStartupWarnings`）。
- guardrail 在 `AgentPromptAssembly` 两处被调用：INPUT 检查（`:79`）+ OUTPUT 检查（`:305`）。
- security 层另有 `IToolAccessChecker` / `IPathAccessChecker` 等独立 SPI（见 `guardrail-contract.md` SPI 分类表）——这些是不同检查面，本计划聚焦 `IContentGuardrail`（内容 guardrail）。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-eval-design.md` 是更广义的 agent eval 系统（Vercel eve 范式，`*.eval.yaml` 走真实 session）的**草案**——与本计划正交（eval 测 agent ReAct 行为，本计划测 guardrail 拦截能力），但 Grader rubric 模板思路可复用。
- **缺口**：无任何 guardrail 测试语料、无攻击生成器、无拦截效果度量。`guardrail-contract.md` §增量 1 是本计划的设计来源（待落地；该节当前无显式 status 标注，与已标 final 的 §增量 3 不同）。
- **已识别的 pre-existing doc drift**（本计划编辑同一 `guardrail-contract.md`，须诚实裁定，不能默默跳过）：
  - SPI 表（`:67`）称 `IContentGuardrail` "No production alternative exists"，但 `PromptInjectionGuardrail` 存在且自标 Production-grade——**表与代码矛盾**。
  - `IContentGuardrail.java:6-8` `@apiNote` 称 "no production-grade implementation exists in this version"——**注释与代码矛盾**。
  - `guardrail-contract.md:79` 称 NoOpContentGuardrail 检查 fire at **INFO**，但 `AgentStartupWarnings.java:132` 实际用 `LOG.warn(...)`——**日志级别描述与代码矛盾**。
  - 裁定：这些 drift 是 pre-existing（非本计划引入），但本计划编辑同一文档时须在 Phase 1 一并修正（SPI 表承认 PromptInjectionGuardrail、修正日志级别描述），否则 plan 声称做了"live repo 核对"却留下矛盾 baseline，削弱可信度。`@apiNote` 注释修正属运行时文件微调，不改行为。

## Goals

- 交付 **test-time** GuardrailTestSuite 框架：AttackPlugin（提供攻击用例）与 GuardrailGrader（rubric 判定拦截效果）职责分离，可独立扩展。
- 交付 **60+ 攻击用例语料库**，覆盖 LLM 安全关键威胁类别（prompt injection / prompt extraction / role hijack / 数据渗出 exfiltration / jailbreak / hallucination / invisible char / 越权指令 等，对标 promptfoo 60+ 红队插件覆盖面）。
- 交付 **可度量、可回归的 Report**：拦截率 / 漏报率 / 误报率，可在 CI 中作为回归门禁。
- 交付**策略层**对 payload 的二次变换（base64 / crescendo），验证 guardrail 对变换后攻击的鲁棒性。
- 形成"建设（运行时 guardrail，已有）+ 验收（本计划）"闭环。

## Non-Goals

- **不改动运行时 guardrail 执行路径**——本计划是 test-time 组件，不改 `IContentGuardrail` SPI、`AgentPromptAssembly` 调用点、`PromptInjectionGuardrail` 既有检测逻辑（如运行时检测有缺陷，那是独立 Fix，不属于本计划）。
- **不做 LLM 驱动的攻击动态生成**（promptfoo 用 LLM 生成攻击）——首版用**静态声明式语料库**（可 git diff、可 Delta 定制、确定性回归）。LLM 动态生成留 successor（见 Deferred）。
- **不做 LLM-judge 式模糊打分**——首版 Grader 对 `GuardrailResult`（Block/Pass/Modify 三态）做**确定性 rubric 判定**（攻击应被 Block、良性内容应被 Pass）。LLM-judge 留 successor。
- **不测 security 层独立 SPI**（`IToolAccessChecker` / `IPathAccessChecker` 等）——首版聚焦 `IContentGuardrail` 内容面；security SPI 验收是独立 successor。
- **不引入外部依赖**（promptfoo 是 TS，不引入；仅吸收 Plugin+Grader 分离语义，以 nop 原生方式实现）。

## Scope

### In Scope

- GuardrailTestSuite 框架：`AttackPlugin` SPI（提供攻击用例）+ `GuardrailGrader` SPI（rubric 判定）+ orchestrator + `GuardrailTestReport`。
- 60+ 静态攻击用例语料库（声明式数据，覆盖 OWASP LLM Top 10 关键类别）+ 良性内容对照集（测误报）。
- 策略层 payload 二次变换（base64 / crescendo）。
- 端到端：Plugin 造攻击 → 既有 `IContentGuardrail.check()` 拦截 → Grader 判定 → Report 度量。
- 与 `PromptInjectionGuardrail` 的集成验证（作为既有 production guardrail 的验收用例）。

### Out Of Scope

- 运行时 guardrail 执行逻辑改动（见 Non-Goals）。
- LLM 动态攻击生成 / LLM-judge 打分（successor）。
- security 层独立 SPI（`IToolAccessChecker` 等）的验收（successor）。
- 广义 agent eval 系统（`nop-ai-agent-eval-design.md`，独立 track）。

## Execution Plan

### Phase 1 - 设计裁定与契约定义

Status: completed
Targets: `ai-dev/design/nop-ai-agent/guardrail-contract.md` §增量 1（由"增量设计"升级为 final 设计）

- Item Types: `Decision`

- [x] **Decision A：组件归属与模块边界**。裁定 GuardrailTestSuite 放在 `nop-ai-agent` main source 的 test-time 包（如 `io.nop.ai.agent.guardrail.test`）还是独立 test-support 模块。裁定依据：(a) 它是测试时库需被消费方引用（类 JUnit support lib 在 main source 的惯例）；(b) 不污染运行时类路径语义；(c) 与 `nop-ai-agent-eval-design.md` 草案的边界。回写 `guardrail-contract.md` §增量 1。
- [x] **Decision B：AttackPlugin 契约形态**。裁定 AttackPlugin 的 I/O 契约——它"提供攻击用例"是返回静态 `AttackCase` 列表（声明式语料），还是带生成参数。首版裁定为**声明式语料提供者**（确定性回归），LLM 生成留 successor。裁定 AttackCase 的字段（category / payload / direction / expectedBehavior / threatClass 标签）。回写设计。
- [x] **Decision C：GuardrailGrader rubric 判定模型**。裁定 Grader 如何判定——对 `GuardrailResult` 三态 + AttackCase 的 `expectedBehavior` 做确定性比对（攻击应 Block / 良性应 Pass / Modify 视为半通过或按 expected）。裁定 rubric 是否用模板（参照 promptfoo Nunjucks → nop `TemplateRenderer`）还是纯结构化判定。首版裁定为结构化确定性判定（rubric 模板留 successor）。回写设计。
- [x] **Decision D：Report 度量维度**。裁定 `GuardrailTestReport` 度量集——拦截率（攻击被 Block 比例）/ 漏报率（攻击被 Pass 比例）/ 误报率（良性被 Block 比例）/ 分类别（per threatClass）度量。裁定 CI 门禁形态（gate 阈值 vs 仅记录）。回写设计。
- [x] **Decision E：策略层 payload 变换**。裁定策略层（base64 / crescendo）如何作用于 AttackCase payload——是 AttackCase 的装饰器（生成变体）还是独立的策略 SPI。裁定变换后 expectedBehavior 是否保持（变换不改变攻击性质 → 仍应 Block）。回写设计。
- [x] **Decision F：语料库组织形式**。裁定 60+ 攻击用例以何种声明式形态存储——内联 Java 数据 vs 外部资源文件（`*.yaml` / `*.json` 语料文件，可 Delta 定制）。首版倾向外部声明式语料（可 git diff、可 Delta）。回写设计。
- [x] **Decision G：SPI 契约写入设计文档**。把 Decision A–F 的裁定结果固化为 design doc 中可直接编码的**接口契约描述**——至少包含：AttackPlugin 的 I/O（输入/输出 AttackCase 列表）、AttackCase 数据模型字段、GuardrailGrader 的 I/O（输入 GuardrailResult + AttackCase.expectedBehavior → 输出 GradeResult 判定态）、GuardrailTestSuite.run() 入口签名、GuardrailTestReport 度量字段。目的是消除 Phase 1→Phase 2 断层（Phase 2 executor 不需自行发明 SPI 形态）。**注意**：现 §增量 1 含 `└── Nunjucks 式 rubric 模板（Java: TemplateRenderer）` 行——按 Decision C 首版裁定为结构化确定性判定，该行将被**重写/替代**（非追加），避免增量 1 内部自相矛盾。回写 `guardrail-contract.md` §增量 1。
- [x] **修正 pre-existing doc drift**（Current Baseline 已识别）：修正 `guardrail-contract.md` SPI 表对 `IContentGuardrail` 的 "No production alternative exists" 描述（承认 `PromptInjectionGuardrail`）+ 修正 NoOp awareness 日志级别描述（INFO→WARN）。修正 `IContentGuardrail.java` `@apiNote` 与代码矛盾的措辞（不改行为，仅注释准确性）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 7 条 Decision（A–G）全部裁定并回写 `guardrail-contract.md` §增量 1（status 升级为 final）
- [x] design doc §增量 1 包含 AttackPlugin / GuardrailGrader / GuardrailTestSuite / GuardrailTestReport 四个组件的**接口契约描述**（I/O 明确，Phase 2 可直接编码），可在仓库中读取验证
- [x] pre-existing doc drift（SPI 表 / 日志级别 / @apiNote）已修正，design doc 与代码不再矛盾
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（Phase 1 执行项含此工具，Exit Criteria 镜像）
- [x] No owner-doc update required beyond `guardrail-contract.md`（本 Phase 仅设计裁定 + `IContentGuardrail.java` 注释准确性修正，不改运行时行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 框架实现（AttackPlugin + Grader + Report + Orchestrator）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/test/`（按 Decision A 裁定的包）；`nop-ai/nop-ai-agent/src/test/...`

- Item Types: `Decision` | `Proof`

- [x] 按 Decision A + G 落地组件归属与包结构（test-time 包位置）
- [x] 按 Decision B + G 落地 AttackPlugin SPI + AttackCase 数据模型（category / payload / direction / expectedBehavior / threatClass）
- [x] 按 Decision C 落地 GuardrailGrader SPI + 确定性 rubric 判定实现（消费 `GuardrailResult` 三态 + expectedBehavior → 判定 PASS/FAIL/部分）
- [x] 按 Decision D 落地 GuardrailTestReport（拦截率/漏报率/误报率/per-category 度量，不可变结果对象）
- [x] 落地 GuardrailTestSuite orchestrator：驱动 Plugin 造攻击 → 调既有 `IContentGuardrail.check()` → Grader 判定 → 聚合 Report（不调用真实 LLM / 真实 agent session；guardrail 是纯函数式检查）
- [x] 按 Decision E 落地策略层 payload 变换（base64 / crescendo），作为 AttackCase 装饰器或独立 SPI 生成变体
- [x] 按 Decision F 落地声明式语料加载器（外部语料文件 → AttackCase 列表）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] AttackPlugin / GuardrailGrader / GuardrailTestReport / GuardrailTestSuite 四个组件均有对应单元测试（Minimum Rules #25）
- [x] **接线验证**（Minimum Rules #23）：测试证明 GuardrailTestSuite 在运行时确实调用了既有 `IContentGuardrail.check()` 的**真实实现**（可用 spy/计数器/marker 证明 check 被调用，但被验证的对象必须是真实 guardrail 逻辑而非空 stub），Grader 确实消费了 guardrail 返回的 `GuardrailResult`
- [x] **无静默跳过**（Minimum Rules #24）：guardrail 返回 null/异常时 orchestrator 显式失败（非静默跳过该用例）；新增公共方法在未实现路径抛 `UnsupportedOperationException`
- [x] Report 度量值可被断言（拦截率/漏报率/误报率是具体数值，非抽象描述）
- [x] 策略层变换后的 payload 仍走完整 guardrail→grader 链（非旁路）
- [x] No owner-doc update required beyond Phase 1 已回写的设计（本 Phase 是实现，不改运行时行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 60+ 攻击语料库与端到端验收

Status: completed
Targets: 语料资源文件（按 Decision F）；`nop-ai/nop-ai-agent/src/test/...`

- Item Types: `Proof`

- [x] 编写 60+ 攻击用例，覆盖 LLM 安全关键威胁类别（prompt injection / prompt extraction / role hijack / exfiltration / jailbreak / hallucination / invisible char / 越权指令 等，对标 promptfoo 红队插件覆盖面），每类至少数例
- [x] 编写良性内容对照集（测误报：正常编程问题 / 正常对话 / 合法系统指令）
- [x] 编写行业垂直集样例（financial / medical 至少一组，验证可扩展性）
- [x] **端到端验证**（Minimum Rules #22）：一个集成测试从"语料加载 → GuardrailTestSuite.run(PromptInjectionGuardrail) → Grader 判定 → GuardrailTestReport"完整跑通，断言 Report 拦截率/漏报率/误报率数值
- [x] 对 `PromptInjectionGuardrail`（既有 production guardrail）跑验收套件，记录基线度量（哪些类别拦截、哪些漏报——漏报是既有 guardrail 的已知能力边界，不是本计划缺陷，但须如实记录）
- [x] 策略层变换（base64/crescendo）变体跑验收，记录变换后拦截率变化

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 攻击用例数 ≥ 60（无论 Decision F 裁定的语料形态如何——外部资源文件或内联 Java 数据——AttackCase 实例数可在仓库中计数验证），覆盖至少 8 个 LLM 安全威胁类别
- [x] 良性对照集存在且误报率可度量
- [x] **端到端验证**：集成测试完整跑通 Plugin→guardrail→Grader→Report，Report 度量数值被断言（Anti-Hollow：不是只验证组件存在，而是验证从语料到 Report 度量的完整路径）
- [x] `PromptInjectionGuardrail` 基线度量已记录到 **daily log**（`ai-dev/logs/`）+ 作为测试中的断言快照（哪个 threatClass 拦截 / 哪个漏报），后续 guardrail 改动可回归对比
- [x] **接线验证**：端到端测试中 `IContentGuardrail.check()` 确实被调用（如通过 spy/计数器证明），非空壳
- [x] `guardrail-contract.md` §增量 1 与落地一致（无新 drift）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] GuardrailTestSuite 框架（Plugin/Grader/Report/orchestrator）完整落地，无空壳
- [x] 60+ 攻击语料库 + 良性对照集落地，覆盖 OWASP LLM Top 10 关键类别
- [x] 策略层 payload 变换（base64/crescendo）落地
- [x] 端到端验收路径完整连通（语料→guardrail→Grader→Report 度量）
- [x] 既有 `PromptInjectionGuardrail` 的验收基线度量已记录
- [x] 无运行时 guardrail 执行路径被改动（零回归：既有 guardrail 行为不变）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs（`guardrail-contract.md` §增量 1）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）GuardrailTestSuite 运行时确实调用了 `IContentGuardrail.check()`，（b）Grader 确实消费 `GuardrailResult`，（c）Report 度量来自真实拦截结果（非硬编码）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Minimum Rules #26）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码 0（Minimum Rules #22/#24，When Closing step 5b）
- [x] backlog `nop-ai-agent-harness-evolution-roadmap.md` W5-1 标记 done

## Deferred But Adjudicated

### LLM 驱动的攻击动态生成

- Classification: `optimization candidate`
- Why Not Blocking Closure: promptfoo 用 LLM 动态生成攻击以扩大覆盖面；但 nop 首版用静态声明式语料（60+ 用例）即可形成可度量可回归的验收闭环（确定性是回归测试的必要属性）。LLM 动态生成是非确定性的增量扩展，不阻断"验收闭环成立"。
- Successor Required: yes
- Successor Path: W5 后续 successor plan（或 `nop-ai-agent-eval-design.md` 广义 eval track 的 LLM-judge 能力）

### LLM-judge 式模糊打分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首版 Grader 对 `GuardrailResult` 三态做确定性判定（攻击应 Block / 良性应 Pass），满足 guardrail 验收的确定性需求。LLM-judge（如 factuality/closedQA rubric）适用于模糊正确性，guardrail 拦截是二值判定，确定性足够。
- Successor Required: no

### security 层独立 SPI（IToolAccessChecker / IPathAccessChecker 等）的验收

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划聚焦 `IContentGuardrail`（内容 guardrail）面。security 层独立 SPI 是不同检查面（工具/路径访问控制），其验收是独立工作，不阻断内容 guardrail 验收闭环成立。
- Successor Required: yes
- Successor Path: 独立 successor（guardrail 验收套件扩展到 security SPI 维度）

## Non-Blocking Follow-ups

- 攻击语料库的行业垂直集完整覆盖（financial/insurance/medical/telecom/pharmacy）——首版交付样例验证可扩展性即可
- CI 门禁自动化（Report gate 阈值作为 CI fail-fast 规则）——首版 Report 可度量即可，门禁策略由消费方裁定

## Closure

Status Note: W5-1 GuardrailTestSuite 全部落地。test-time 组件包 `io.nop.ai.agent.guardrail.test` 交付了 AttackPlugin+Grader+Report+Orchestrator+策略层变换+声明式 YAML 语料加载，与既有运行时 guardrail 执行路径完全解耦（runtime 零改动）。65 攻击用例（10 类别）+ 12 良性对照形成可度量可回归的验收闭环；PromptInjectionGuardrail 基线（目标类全拦截、非目标类如实记录漏报边界）作为测试断言快照固化。三 Phase 全部 completed，所有 Closure Gates 经独立审计验证 PASS。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit subagent（fresh session，task ses_040d89a2effefDSVk0dIqtjuPh）
- Audit Session: ses_040d89a2effefDSVk0dIqtjuPh（read-only，未修改任何文件）
- Evidence:
  - Phase 1 PASS：`guardrail-contract.md` §增量 1 标 final（:131），7 条裁定 A–G + 四组件 I/O 契约表（:151-227）齐备；3 处 pre-existing doc drift 全修正（SPI 表承认 PromptInjectionGuardrail :69-80；NoOp awareness WARN :88-96，与 `AgentStartupWarnings.java:132` LOG.warn 一致；`IContentGuardrail.java:8-17` @apiNote 承认 PromptInjectionGuardrail）。
  - Phase 2 PASS：`guardrail/test/` 包 19 文件非空壳（无 `return null` 占位、无吞异常、无空方法体）；`GuardrailTestSuite.run()` 调用链完整：`:106` `guardrail.check(...)` → `:112` `grader.grade(ac, actual)` → `GuardrailTestReport.build()`。
  - Phase 3 PASS：语料 65 BLOCK 攻击（≥60）+ 12 PASS 良性 + 13 类别（≥8）+ 行业垂直集；`TestGuardrailRedteamEndToEnd` 6 测试通过。
  - Anti-Hollow（a）`GuardrailTestSuite.java:106` 运行时调用 `IContentGuardrail.check()`，由 `TestGuardrailTestSuite.suiteInvokesRealGuardrailCheckAndGraderConsumesResult`（:70-71）`assertTrue(checkCount.get() > 0)` 对真实 `PromptInjectionGuardrail`（非 stub）证明；（b）`DefaultGuardrailGrader.java:23-58` 消费 `GuardrailResult` 三态；（c）`GuardrailTestReport.build()`（:49-102）从真实 verdict 聚合度量（非硬编码）；（d）null→`NopAiAgentException` fail-loud（:107-111，`nullGuardrailResultFailsLoud` 证明），throw 传播不吞（`guardrayThrowingPropagates` 证明）；变换变体走完整链（`transformsGenerateVariantsThatRunFullChain` 断言 checkCount==3）。
  - Scope discipline PASS：git diff 显示 runtime 路径仅 `IContentGuardrail.java` javadoc 改动（12+/5-，纯注释，签名/行为不变）；`PromptInjectionGuardrail.java`、`AgentPromptAssembly` 零 diff。
  - Deferred 诚实 PASS：3 项 deferred 全为 optimization/out-of-scope（LLM 动态生成、LLM-judge、security SPI 验收），非 in-scope live defect；PromptInjectionGuardrail 漏报边界在测试中作为回归快照断言（非 deferred）。
  - `./mvnw compile -pl nop-ai/nop-ai-agent -am` BUILD SUCCESS；`./mvnw test -pl nop-ai/nop-ai-agent -am` 3268 tests 0 failures（含 52 新测试）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（closure evidence 写入后无 warning）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high`：2 high findings 均为 pre-existing（`PlanReplanner.java:272` W1-4、`NoOpProviderFailoverQueue.java:34` W2-4），**新 `guardrail/test/` 包零 finding**；退出码 0。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors）。
  - backlog `nop-ai-agent-harness-evolution-roadmap.md` W5-1 已标 `[x]`。

Follow-up:

- CI 门禁自动化（Report gate 阈值作为 CI fail-fast）——首版 Report 可度量即可，门禁策略由消费方裁定（Non-Blocking Follow-up）。
- 行业垂直集完整覆盖（insurance/telecom/pharmacy）——首版交付 financial/medical 样例验证可扩展性。
- LLM 驱动的攻击动态生成 / LLM-judge 模糊打分 / security SPI（IToolAccessChecker 等）验收——见 Deferred But Adjudicated（successor）。
- no remaining plan-owned work。
