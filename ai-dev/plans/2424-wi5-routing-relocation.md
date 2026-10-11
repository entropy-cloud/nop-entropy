# 2424 WI5 模型选路 routing 归位 gateway（拆两层：配置留 core / 引擎迁 gateway）

> Plan Status: completed
> Last Reviewed: 2026-10-11（round 1：2B+3M+4m 修复；round 2：fixture 枚举补全 + 文本清理后可执行）
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI5）
> Related: 2414/2419/2421（WI1/WI2/WI4 已完成）；deps: WI1（已完成）
> 协同：gateway-failover roadmap W1-W8 已 done（本 plan 属后动者移动前者落地物，已按其落地形态复核）

## Purpose

把模型选路（model-class routing）从 nop-ai-core 归位到 nop-ai-gateway：**拆两层**——配置层（xdef 生成模型 + 解析 helper + 候选值类型）留 core，选路引擎 8 文件迁 gateway 新子包 `io.nop.ai.gateway.routing`；core pom 斩断 nop-rule-core 传递依赖（rag/coder/dsl-orm/agent 对规则引擎的传递引用随之消除，四者均零代码引用已实证）。

## Current Baseline

（2026-10-11 执行时 HEAD 实测，Explore 子代理普查 + 主会话复核。）

- **routing/ 9 文件 873 行**：引擎 8 文件（ModelClassRouter 186、ISelectionStrategy 39、DefaultSelectionStrategy 47、RuleBasedSelectionStrategy 222——唯一 nop-rule 消费者（io.nop.rule.core :7-8）、ConcurrencyRegistry 156、IModelClassHealth 16、CandidateHealth 66（依赖 core.reliability.CircuitState）、CandidateHealthProvider 38（依赖 core.reliability breaker 族））+ 配置值类型 ModelClassCandidate 103（零代码依赖）。全部手写，无 _gen/XGEN。
- **ModelClassRouter 在 core main 内零调用者**——引擎只被 gateway failover 编排消费（ChatServiceFailoverAdapter :370-372 routerFor、AiGatewayFailoverInterceptor :190、FailoverProbeSupport、FailoverStreamFlow、GatewayStreamingRetryCallback）。
- **配置层事实**：core/model/ 下 ModelClassConfig/ModelClassModel/ModelClassCandidateModel 各 9 行薄壳继承 `_gen/_ModelClass*.java`（生成源 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/model-class.xdef`，`xdef:bean-package="io.nop.ai.core.model"`；fileType 注册 `nop-ai/nop-ai-core/src/main/resources/_vfs/nop/core/registry/llm.register-model.xml`）。`routing.ModelClassCandidate`（运行期展开候选）≠ `model.ModelClassCandidateModel`（XML 声明条目）。
- **LlmConfigHelper（core/service/，439 行）**：对 routing 唯一引用 = :12 import ModelClassCandidate，用于 model-class 解析家族（resolveModelClass :292 / resolveModelClassCandidates :336 / resolveProviderChainCandidates :382 / MODEL_CLASS_CONFIG_PATH :276）。存在 service↔routing 包级环（Helper 构造 Candidate，Router 调 Helper）——同模块可编译、跨模块即倒置。其 chat 路径消费者（ChatServiceImpl/EmbeddingServiceImpl/reliability 链族/agent LlmCallCoordinator）不触 model-class 方法。
- **消费者普查（round 1 审查 M1 勘误）**：routing 类 main 消费者 = gateway failover **7 文件**（6 个真实 import + FailoverConstants javadoc {@link}）+ core LlmConfigHelper；rag/coder/agent main/nop-code 零引用。test 消费者 = core routing 测试 4 文件（TestConcurrencyRegistry 199/TestDefaultSelectionStrategy 120/TestModelClassRouter 262/TestRuleBasedSelectionStrategy 326——后者 import io.nop.rule）+ TestLlmConfigHelperModelClass 191 + TestNopAiCoreErrorsContract :66-67（CandidateHealthProvider 触发 ERR_AI_CORE_INVALID_ARGUMENT 契约）+ gateway 测试 8 文件 + agent `TestInvariantGate1SecureDefault.java:98` FQN 字符串。
- **nop-rule-core**：core pom :41-48 compile（注释自证来源 = W5b）；routing 之外 core 全零 import；全仓声明 nop-rule-core 的 pom 仅 rule 族/nop-task-ext/tests/nop-bom/nop-ai-core——传递依赖唯一来源就是 nop-ai-core；rag/coder/dsl-orm/agent 对 io.nop.rule 零代码引用，斩断无破坏面。gateway 无直接声明（迁移后需新增）。
- **beans/FQN 同步面**：`ai-gateway-defaults.beans.xml:158-159`（nopAiRuleBasedSelectionStrategy，class=io.nop.ai.core.routing.…，ioc:optional ref nopRuleManager + :169 on-class 守卫）、`:207-210`（nopFailoverConcurrencyRegistry）；gateway 测试 beans ×2；`nop-ai-core/src/test/resources/_vfs/nop/rule/rule-selection/` 7 个规则资源（随规则策略测试迁 gateway——gateway 侧已有 rule-selection/gateway/v1.rule.xml）。
- **设计/参考文档**：`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md` :175（Q8 裁定 model-class.xdef bean 包 io.nop.ai.core.model——**配置层留 core 的设计级依据**）、:267 归属表「模型类候选集（数据结构+解析）| nop-ai-core」（需回填引擎行）；`docs-for-ai/03-modules/nop-ai-gateway.md` :240/:247-248（接口与 bean FQCN）；`docs-for-ai/04-reference/source-anchors.md:209` AIGW-006 锚点直指 core routing 四文件；llm-layer.md 仅 :241-242/:295 弱关联。
- **门禁耦合（round 1 审查 B1 勘误）**：agent `TestInvariantGate1SecureDefault` :98 FQN 字符串引用该类；agent pom 无 gateway 依赖且 reactor 禁环不可加——处置为 agent TABLE 删行 + gateway 新建等价门禁测试（见 Phase 1）。
- **构建环境注意**：本机存在并行会话共享 ~/.m2，新增依赖首次解析可能命中陈旧 jar——WI4 教训：编译报「找不到符号/程序包不存在」时先核对 ~/.m2 jar 时效与依赖 scope 降级（直接声明 test scope 会把传递 compile 降级）。

## Goals

- 引擎层 8 文件迁 `io.nop.ai.gateway.routing`（保方法语义，包名变更为公共 API 变更——迁移说明 + FQN 同步面全量更新）。
- 配置层留 core：xdef/_gen/注册文件零改动；`ModelClassCandidate` 迁 `io.nop.ai.core.model`（与 ModelClassCandidateModel 同包，命名区分靠后缀，登记）；LlmConfigHelper 原地保留（:12 import 指向新包）——service↔routing 包级环消除。
- core pom 删 nop-rule-core；gateway pom 增直接依赖；core 依赖树无 nop-rule-core、core main 无 io.nop.ai.core.routing 残留。
- SecureDefault 门禁同步：agent TABLE 删行 + gateway 新建等价门禁测试（处方见 Phase 1，round 1 审查 B1：agent 加 gateway 依赖不可行——reactor 禁环）。

## Non-Goals

- 不改 nop-kernel 的 model-class.xdef 与 _gen 生成物（Protected Areas；配置层留 core 即零改动）。
- 不改 gateway failover 的编排语义与 beans 装配结构（仅 FQCN 字符串更新）。
- 不动 core/reliability（W2 下沉成果——引擎迁 gateway 后由 gateway→core 消费，方向合法）。
- 不迁移 LlmConfigHelper 的非 model-class 方法（chat 主路径消费者不动）。

## Scope

### In Scope

- nop-ai-core：删 routing/ 9 文件（ModelClassCandidate 迁 core/model）、pom 删 nop-rule-core、routing 测试 4 文件与 rule-selection 资源迁出、TestLlmConfigHelperModelClass import 更新、TestNopAiCoreErrorsContract 契约用例处置。
- nop-ai-gateway：接收引擎 8 文件（io.nop.ai.gateway.routing）、pom 增 nop-rule-core、beans FQCN ×2、测试 8 文件 import 更新、新增 CandidateHealthProvider 错误码契约用例（自 core TestNopAiCoreErrorsContract 迁出）、SecureDefault 门禁 FQN/包同步。
- 文档：nop-ai-gateway.md、source-anchors AIGW-006、02-account-failover-requirement.md 归属表、llm-layer.md 弱关联核对。

### Out Of Scope

- model-class.xdef/_gen、failover 编排语义、core/reliability、W1-W8 已收口能力。

## Execution Plan

### Phase 1 - 拆两层迁移

Status: completed
Targets: `nop-ai/nop-ai-core/src/{main/java/io/nop/ai/core/{routing,model},test/...}`、`nop-ai/nop-ai-gateway/`

- Item Types: `Fix`

- [x] `ModelClassCandidate.java` git mv 至 `core/model/`（io.nop.ai.core.model 包，包声明更新）；`LlmConfigHelper.java:12` import 更新；round 1 审查 m5 勘误：LlmConfigHelper 原地保留故 ModelClassRouter 对它的 import **不变**；原 routing 包内 6 个引擎文件对 ModelClassCandidate 是**同包隐式引用**——迁移后需**新增** import io.nop.ai.core.model.ModelClassCandidate
- [x] 引擎 8 文件 git mv 至 `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/routing/`（包声明 io.nop.ai.gateway.routing；LlmConfigHelper import 不变——其原地保留；DefaultSelectionStrategy/RuleBasedSelectionStrategy 的 SecureDefault import 不变——gateway 依赖 nop-ai-api）
- [x] pom：core 删 nop-rule-core（:41-48 块及注释，round 1 审查 m2 锚点勘误）；gateway dependencies 增 nop-rule-core（compile，版本经 nop-bom :985-989 dependencyManagement 管理，无需版本号）
- [x] 测试迁移：core routing 测试 4 文件 git mv 至 gateway test 同包（包声明更新）+ rule-selection 资源 `core/src/test/resources/_vfs/nop/rule/rule-selection/` 7 文件 git mv 至 gateway test resources 同路径（gateway 既有 rule-selection/gateway/ 子目录无路径冲突）；**test fixture 处置（round 1 审查 B2）**：迁入测试依赖 core test 资源 `_default.model-class.xml`（tier-v4/tier-misc/tier-sat 段）与 `_default.llm-failover.xml`（test-sat→[test-failover] 链），与 gateway test **同名同路径**的 gw-* 夹具冲突且 core test 资源不传递——裁定（round 2 审查枚举补全）：将 tier-v4/tier-misc/tier-sat 段、**test-accounts 与 test-sat 两条 failover 链**全量**并入** gateway 现有 `_default.*` 文件 + `test-accounts.llm.xml`/`test-modelclass.llm.xml`/`test-sat.llm.xml`/`test-failover.llm.xml` **4 个 provider 文件一并迁入**（test-modelclass.llm.xml 被 TestModelClassRouter :115/:222/:226-227 的 tripOpen 与饱和断言硬性消费；test-accounts 链被 :194-232 端到端用例消费）（core 侧 TestLlmConfigHelperModelClass 的 `_default.model-class.xml` 走相对路径已留 core，**同名冲突需按文件逐个核对消费方后处置**——两 core 测试资源均在 core/src/test/resources/_vfs/nop/ai/llm/ 下，若 core 留存测试仍消费则 gateway 侧以并入策略为准、core 原文件保留）；迁入后必须回归 gateway 既有 8 个 failover 测试（夹具合并不得破坏 gw-* 用例）
- [x] TestLlmConfigHelperModelClass 留 core 改 import；TestNopAiCoreErrorsContract 删除 CandidateHealthProvider 契约用例（:65-72）并在 gateway 侧新增等价用例（ERR_AI_CORE_INVALID_ARGUMENT 契约随类走）+ 同步该文件 :34-36 javadoc throw-site inventory（round 1 审查 m3：该注释称 CandidateHealthProvider 是 main 唯一 throw site）
- [x] beans/FQN 同步（round 1 审查 M2：共 5 处 + 测试 beans）：ai-gateway-defaults.beans.xml **:158 class / :159 ioc:type / :169 on-class / :208 class / :210 on-class** 全部改 io.nop.ai.gateway.routing.*（on-class 指向策略/Registry 类自身，漏改即 bean 装配期静默消失）；gateway 测试 beans ×2 各 2 处同步；failover main 文件 import 同步（round 1 审查 M1：**7 个文件**——6 个真实 import 含 GatewayStreamingLifecycleListener + FailoverConstants javadoc {@link}）
- [x] SecureDefault 门禁同步（round 1 审查 B1 重写处方：agent 加 gateway 依赖不可行——Maven reactor 禁环；importPackages 加 io.nop.ai.gateway.. 在 agent 测试 classpath 扫零个类必红）：agent `TestInvariantGate1SecureDefault` TABLE 删 `io.nop.ai.core.routing.DefaultSelectionStrategy` 行（:98；33→32，:66-68 注释计数同步），**gateway 侧新建等价门禁测试**（覆盖 DefaultSelectionStrategy 的 @SecureDefault 声明，参照 agent 门禁 checkTable 模式）
- [x] 编译 + 测试：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-gateway -am`；**resources 残留校验**（round 1 审查 M2：无测试装配 ai-gateway-defaults.beans.xml，靠 grep 兜底）：grep `io.nop.ai.core.routing` 于两模块 src/main/resources 与 src/test/resources 零命中

Exit Criteria:

- [x] core main 无 io.nop.ai.core.routing 残留（grep 零命中）；core 依赖树无 nop-rule-core（`./mvnw dependency:tree -pl nop-ai/nop-ai-core | grep rule` 零命中，输出留档）
- [x] gateway 引擎 8 文件在 io.nop.ai.gateway.routing；beans/门禁 FQN 全部同步
- [x] core/gateway 测试全绿（gateway 8 + 迁入 4 测试文件）
- [x] owner-doc 更新于本 plan Phase 2 内完成；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 文档回写与迁移说明

Status: completed
Targets: `docs-for-ai/03-modules/nop-ai-gateway.md`、`docs-for-ai/04-reference/source-anchors.md`、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`

- Item Types: `Decision` + `Proof`

- [x] 迁移说明（公共 API 变更，Protected Areas）：FQN 变更映射表（io.nop.ai.core.routing.* → io.nop.ai.gateway.routing.*，ModelClassCandidate → io.nop.ai.core.model）、配置层/引擎层拆分裁定（设计级依据 = 02-account-failover §4.1 Q8 + 归属表）、外部消费者迁移指引（无——main 消费者仅 gateway 自身）
- [x] docs-for-ai/03-modules/nop-ai-gateway.md :240/:247-248 FQN 更新 + 「模型类路由」能力归属表述核对；source-anchors.md AIGW-006 四文件路径更新；02-account-failover-requirement.md :267 归属表回填引擎行（nop-ai-gateway）；**`ai-dev/audits/nop-ai-invariants/invariant-catalog.md`（round 1 审查 M3）：:102 漂移叙述、:142 row 29 routing/DefaultSelectionStrategy.java 路径、SecureDefault 清单 33→32 计数口径**同步；llm-layer.md 弱关联核对
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 五处 doc 更新在位且与 live code 一致
- [x] check-doc-links --strict 0 error

## Closure Gates

- [x] core main 无 io.nop.ai.core.routing 残留 + 依赖树无 nop-rule-core（输出留档）
- [x] 引擎 8 文件在 io.nop.ai.gateway.routing；配置层 3+3+_gen+xdef+注册文件零改动
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-gateway -am` 全绿
- [x] 不存在被静默降级的 in-scope live defect；service↔routing 包级环消除（登记）
- [x] owner docs 五处已同步 + 迁移说明在位
- [x] 独立子 agent closure-audit 已完成并记录证据（APPROVE，3 Minor 已修）
- [x] Anti-Hollow Check：gateway failover 编排对 Router 的运行时调用链不变（测试 beans ×2 的 FQN 同步 + IoC/Failover 测试全绿为**测试装配面**证据；**生产 defaults 文件无测试装配**——以 resources grep 零残留 + 5 处 FQN 逐处核对为证据，round 1 审查 M2）；无空方法体/静默跳过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-gateway --severity high` 无本次新增 high 发现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2424-wi5-routing-relocation.md --strict` 退出码 0（自指验证）

## Deferred But Adjudicated

### ModelClassCandidate 与 ModelClassCandidateModel 同包命名

- Classification: `watch-only residual`
- Why Not Blocking Closure: 二者语义不同（运行期展开候选 vs XML 声明条目）且后缀可区分；改名属公共 API 变更，收益低。
- Successor Required: `no`
- Successor Path: 无（如未来 model-class.xdef 演进时顺带评估）

## Non-Blocking Follow-ups

- llm-layer.md :241-242/:295 弱关联表述在 Phase 2 核对，如无 routing 实质内容则登记 no-change。
- core 侧 rule-selection 资源迁出后 core test resources 的 rule 目录清空核对。

## Closure

Status Note: WI5 两 Phase 完成：引擎层 8 文件迁 io.nop.ai.gateway.routing、配置层留守 core（xdef/_gen 零改动）、core pom 斩断 nop-rule-core（依赖树零命中留档）、agent 门禁 TABLE 删行 + gateway 等价门禁落地、fixture 冲突经路径可配置化解决（默认值不变生产行为零变化）、service↔routing 包级环消除。closure audit 初判 3 项 Minor 文档归属标注已随手修。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11）
- Evidence:
  - A 迁移完成度 PASS：core routing/ ABSENT、gateway 8 文件包声明逐一验证、配置层 3+3+_gen+xdef+注册文件 git diff 为空、LlmConfigHelper 新配置键默认值 = 原生产路径
  - B PASS：core main/resources 五处 grep 零命中；dependency:tree 无 nop-rule-core（审计者重跑）；gateway→core 方向合法
  - C 测试 PASS：routing 包 5 测试文件（含新门禁 TestRoutingSecureDefaultGate）+ @BeforeAll/@AfterAll 作用域配置在位；agent TABLE 32 行无 gateway importPackages；core 400 / gateway 243 绿留档
  - D owner docs PASS：五处验收更新全部落地且与 live code 一致；check-doc-links --strict exit 0
  - E 文本一致性 PASS：执行期修正 6 项内联登记、Deferred 在位、log 条目含两深坑/环境偏差/流程教训
  - 审计 Minor 处置：M-1/M-2/M-3 文档归属标注已随签核修复
  - 环境偏差登记：-am 全量链撞并行 JDK25 会话（plan 2425）在途改动，验证面收窄为 core+gateway 两模块（先行 install core），WI21 收口重试全量

Follow-up:

- StubHttpClient 模式三处复制（deepwiki/rag/code-web）——WI7/WI12 收敛候选
- （承袭）WI19 gateway 承载边界裁定将复核 routing/failover/channel/login 的包布局
