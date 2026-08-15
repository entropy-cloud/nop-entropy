# W8 全链回归收口 + docs-for-ai 使用文档 + 需求文档状态收口（OBS-02/03/04）

> Plan Status: active
> Last Reviewed: 2026-08-15
> Review Consensus: 三轮独立 fresh-session 对抗性审查达成共识——R1: 3 Major（矩阵骨架/gap 判定规则、docs 缺挂载契约与启用方式、Closure Gates 缺硬门禁）+ 8 Minor，全部修复；R2: approve（9 项修复与 live repo 实证一致，0 Blocker 0 Major）；R3: approve（M1-M6 措辞修正 + scan-hollow 位置路径修正实机验证，全局一致）
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W8 OBS-02/03/04）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.x 落地状态 + §五 Q2/Q3/Q4/Q5/Q7）、`ai-dev/design/nop-ai-gateway/01-architecture.md`
> Related: `ai-dev/plans/2026-08-15-1615-1-nop-ai-gateway-w8-observability-metrics.md`（W8 OBS-01，本计划前置）、`ai-dev/plans/2026-08-15-1116-1-nop-ai-gateway-w5b-rule-selection-strategy.md`（规则策略使用文档归 OBS-03）、`ai-dev/plans/2026-08-15-1116-2` / `2026-08-15-1116-3`（W6/W7，Q2/Q5/Q7 处置记录）
> Mission: nop-ai-gateway-failover
> Work Item: W8 (OBS-02/03/04)

## Purpose

生产化收口第二半——（1）全链回归测试矩阵收口：两种形态（本地适配器 + 网关拦截器）× 非流式/流式 × 各触发分类与边界（缓冲窗口、重试预算、饱和、并发释放、探活恢复）gap 分析后补缺测试，既有测试零回归；（2）`docs-for-ai/` failover 使用文档（网关配置 + 本地适配器用法 + 模型类/账号配置示例 + 规则策略用法 + 指标清单）并同步 INDEX.md / source-anchors.md / 03-modules 路由；（3）需求文档状态收口：§五 Q 表 Q2/Q3/Q4/Q5/Q7 处置结果回填、§3.x 落地状态一致性复核。完成后 W1-W8 全部落地证据闭环，roadmap W8 具备标 `done` 的判定材料（`done` 标记由引擎经独立 closure audit 后执行，本计划不自行标）。

## Current Baseline

- W1-W7 已落地（roadmap `done`）。测试现状（live repo 核实）：本地形态 `TestChatServiceFailoverAdapterNonStreaming`（17 用例）+ `TestChatServiceFailoverAdapterStreaming`（15 用例）；网关形态 `TestAiGatewayFailoverInterceptorStreaming`（8 用例）+ `TestAiGatewayFailoverInterceptorNonStreaming`（5 用例）；W4 双向转换测试 + converter 接线测试；既有网关用例 152 个（W7 记录）；测试基建 `FailoverTestSupport`/`W7GatewayTestSupport`。
- `docs-for-ai/` 现状：无 nop-ai-gateway failover 使用文档；`docs-for-ai/INDEX.md` 无 ai-gateway 路由条目；`docs-for-ai/04-reference/source-anchors.md` 无 failover 锚点；`docs-for-ai/03-modules/nop-ai.md` 子模块表未列 `nop-ai-gateway`。
- W5b 计划 Non-Blocking Follow-up 已声明：`docs-for-ai/` 规则策略使用文档归 W8 OBS-03 统一收口。
- 需求文档 Q 表现状：Q2/Q5（前端 model 语义、RATE_LIMITED/TRANSIENT 切换偏离）推荐默认已在 W6/W7 落地但**人工裁决未回填**（W6/W7 均登记"裁决回填归 W8 OBS-04"，watch-only residual）；Q3 已裁定（W6/W7 执行期：N=10/T=1000ms/预算 2 次/总延迟默认 null）但 Q 表状态未同步；Q7 已决（W1 spike + W7 落地）但 Q 表未回填；Q4（指标契约）由 OBS-01（plan `2026-08-15-1615-1` Phase 1）落档 §3.6 后，本计划回填状态。
- OBS-01（plan `2026-08-15-1615-1`）完成后，指标契约与实现可用，回归测试可加指标断言。
- **真实 gap**：OBS-02 全链矩阵缺显式 gap 分析与文档化；OBS-03 无任何使用文档；OBS-04 Q 表未收口。

## Goals

- 全链回归矩阵显式化并补缺：矩阵 = 形态（本地/网关）× 模式（非流式/流式）× 触发分类（QUOTA_EXCEEDED/AUTH_INVALID/RATE_LIMITED/TRANSIENT → 切换；NON_TRANSIENT → 不切换不耗预算；CACHE_STATE_LOST → 原地重试）× 边界（缓冲窗口内重订阅/窗口外断流、重试预算耗尽、全池饱和 fail-loud、并发 +1/-1 配对、探活恢复、取消语义）；对既有测试做 gap 分析，只补缺口不重写既有测试；新增测试（含指标断言，依赖 OBS-01）全绿且既有测试零回归。
- `docs-for-ai/` 使用文档交付：failover 使用指南（网关形态配置 + 本地适配器用法 + `model-class.xdef` 模型类/账号配置示例 + `llm.xdef` concurrencyLimit + 规则策略用法（W5b follow-up 收口）+ 指标清单（OBS-01 契约摘要）），内容与 live repo 配置键/bean 名/xdef 结构一致；INDEX.md 路由、source-anchors.md 锚点、03-modules 路由同步；`check-doc-links.mjs --strict` 0 errors。
- 需求文档状态收口：§五 Q 表 Q2/Q3/Q4/Q5/Q7 逐条回填处置结果（沿用推荐默认者标注"已落地 + 待人工最终确认"或按裁定状态标记，**不允许把未裁决项静默标记为已决**）；§3.x 落地状态与 live repo 一致性抽查；roadmap W8 `todo` 状态的引擎侧流转材料就绪。

## Non-Goals

- 新指标实现/契约扩展（OBS-01 已收口，本计划只引用）。
- 任何新 failover 功能（手动运维、多实例聚合、告警、总延迟上限落地——均为已裁定 non-goal / optimization candidate）。
- 重写既有通过测试；为补覆盖率而扩展现有测试断言之外的行为变更。
- 修改 `02-account-failover-requirement.md` 的需求语义（仅状态回填与落地注记，不改变已审查结论）。

## Scope

### In Scope

- 全链回归矩阵定义（计划内文档化矩阵表）+ 缺口测试补写（`nop-ai/nop-ai-gateway/src/test`）。
- 指标断言测试（消费 OBS-01 指标服务，覆盖切换/重订阅/饱和/熔断迁移等关键指标在端到端路径上的观测）。
- `docs-for-ai/` failover 使用文档（落点执行期裁定，判据见 Phase 2 item 1：内容量阈值 + 组内先例）与 INDEX/source-anchors/nop-ai.md 同步。
- `02-account-failover-requirement.md` §五 Q 表收口 + §3.x 落地状态一致性复核。
- `ai-dev/logs/` 收口记录。

### Out Of Scope

- OBS-01（指标实现）任何遗留（若 OBS-01 未完成，本计划阻塞等待，不代为实现）。
- 需求文档语义修订、roadmap 状态改写（roadmap 的 `todo → done` 流转由引擎/closure audit 驱动）。

## Execution Plan

### Phase 1 - 全链回归 gap 分析与补缺（OBS-02）

Status: planned
Targets: `nop-ai/nop-ai-gateway/src/test`、测试矩阵文档

- Item Types: `Proof | Fix`

- [ ] 构建全链回归矩阵（平铺场景清单，避免全组合空转）：场景 = §3.1 动作表 6 分类（QUOTA_EXCEEDED/AUTH_INVALID/RATE_LIMITED/TRANSIENT → 切换；NON_TRANSIENT → 不切换不耗预算；CACHE_STATE_LOST → 原地重试）× 边界（缓冲窗口内重订阅/窗口外断流、重试预算耗尽、全池饱和 fail-loud、并发 +1/-1 全终止路径配对、探活恢复、取消顺序契约、非流式重发），视图 = 形态（本地/网关）× 模式（非流式/流式）四象限；**行为空缺组合不机械生成**（如 NON_TRANSIENT × 取消顺序契约 直接 N/A），**一条测试可覆盖多格**（用例标注其覆盖的格子）；**每格标注覆盖状态：既有用例名 / 新增用例名 / N/A（理由必须引用使事件协议层不成立的需求条款，如"网关非流式路径无缓冲窗口事件——缓冲窗口是流式重订阅机制（§3.2），非流式对应机制为非流式重发，见后者行"），禁止用空 N/A 逃避覆盖**
- [ ] 逐格对照既有 4 个测试类（17/15/8/5 用例）产出 gap 清单；**gap 判定规则**：格内无任何断言该场景的测试 = gap；有测试但断言不覆盖该场景关键行为（如只断言不抛异常未断言重发到新账号）= gap；**gap 若揭示行为缺口（测试失败根因是功能未实现/有 bug 而非测试设计问题），属 in-scope Fix，不得降级为 follow-up**
- [ ] 补缺测试：未覆盖的矩阵格新增用例（保持既有测试不改写）；**指标断言限于端到端路径测试**（切换次数、重订阅次数、饱和计数、熔断迁移计数在端到端路径上的增量断言，消费 OBS-01 指标服务；单元级补缺测试不强制指标断言）
- [ ] 边界验证：缓冲窗口内重订阅 / 窗口外断流、重试预算耗尽 fail、全池饱和 fail-loud、并发 +1/-1 全终止路径配对、探活恢复（HALF_OPEN → CLOSED）、取消顺序契约、NON_TRANSIENT 不耗预算
- [ ] 双形态 × 非流式/流式 × 各分类的端到端全链验证（从适配器/拦截器入口到最终输出/指标计数）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 矩阵表落档 **plan 内**（附 daily log 引用）：平铺场景清单逐格标注既有用例名/新增用例名/N/A+理由
- [ ] 矩阵 100% 覆盖：每个非 N/A 格子在 live 测试套件中有对应用例（新增或既有），N/A 格均有理由落档
- [ ] **端到端验证**：本地形态与网关形态各至少一条从入口到输出的全链测试（含指标断言）绿
- [ ] **接线验证**：新测试断言了指标/重订阅/熔断等组件的运行时调用（非仅类型存在）
- [ ] 既有测试零回归（口径：W6 32 + W7 13 为组成部分，网关模块用例总数 152 含上述新增——**OBS-01 已落地的指标测试为既有用例一并纳入，新增用例不并入 152 基数**；零回归核验 = 152 全绿且无既有用例改写）
- [ ] 无 flaky：全链相关测试连续 2 次运行全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - docs-for-ai 使用文档（OBS-03）

Status: planned
Targets: `docs-for-ai/`（03-modules、INDEX.md、04-reference/source-anchors.md）

- Item Types: `Decision | Follow-up`

- [ ] 裁定文档落点并执行。判据：内容量（约 ≥6 节/含完整配置示例 → 新增独立页 `03-modules/nop-ai-gateway.md`，与 nop-job.md 等平级；否则 nop-ai.md 内章节 + 子模块表行）；**注意组内先例**——nop-ai 组 12 个子模块均无独立页（仅聚合页行），若裁定独立页需说明理由落档；两种落点都必须同步 INDEX 路由
- [ ] 内容：网关形态配置（拦截器 bean 注册 + **`gateway.xml <interceptors>` 挂载契约——bean 注册 ≠ 挂载，部署必须引用 bean 到路由拦截器链（W7 M-7 契约）**、`gateway.xdef` streaming bufferEnabled/bufferSize/bufferTimeMs、`nop.ai.gateway.failover.*` 配置键、重试预算）；本地适配器用法（`nopChatServiceFailoverAdapter` bean 装配 + **部署启用方式——独立 bean 不覆盖 ioc:default，部署方需显式切换注入目标（W6 D4 裁定）**）；模型类与账号配置示例（引用 `model-class.xdef` 结构 + `llm.xdef` concurrencyLimit + `<accounts>`，**示例为新建示意片段，不拷贝 test fixture `_default.model-class.xml` 内容、不修改任何 `_` 前缀文件**）；规则策略用法（W5b follow-up 收口：`rule.xdef` + IoC 绑定 + 输入/输出契约 + `nop.ai.gateway.rule-selection.rule-name` 配置键）；指标清单（OBS-01 契约摘要）
- [ ] 内容与 live repo 一致性核对（配置键名/bean 名/xdef 元素名逐一比对源码与 beans.xml）
- [ ] 路由同步：INDEX.md 新增条目、source-anchors.md 新增锚点（如 AI 网关 failover 关键类）、03-modules/nop-ai.md 子模块表补充 nop-ai-gateway 行
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 使用文档已交付且被 INDEX.md 路由；source-anchors.md / nop-ai.md 同步（文件路径 + 章节可核）
- [ ] 文档引用的每个配置键/bean 名/xdef 元素与 live repo 一致（抽查比对记录）
- [ ] check-doc-links.mjs --strict 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 需求文档状态收口（OBS-04）

Status: planned
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`

- Item Types: `Decision | Follow-up`

- [ ] §五 Q 表逐条收口，**状态依据**：`待人工确认` = roadmap/W1 暴露为人工决策点的项（Q2/Q5，推荐默认已落地）；`已决` = 执行期裁定（Q3，W6 Phase 1 记录）或 spike 结论（Q7，W1 plan + W7 落地记录）——按此依据区分，不得混标。具体：Q2（推荐默认 = 路由覆盖 model，已落地，标注待人工最终确认）、Q5（推荐默认 = 确认偏离，RATE_LIMITED/TRANSIENT → 账号链切换，已落地，标注待人工最终确认）、Q3（执行期裁定回填：N=10/T=1000ms/预算 2/总延迟 null）、Q4（引用 OBS-01 §3.6 契约，状态 = 已落档）、Q7（spike + W7 落地回填：机制 A 最小通用改动）
- [ ] 未获人工裁决项（Q2/Q5）显式保留"待确认"状态并引用 W6/W7 落地记录，**不得静默标记已决**
- [ ] §3.x 落地状态与 live repo 一致性抽查（§3.1 动作表、§3.2 切换语义、§3.3 路由/熔断/并发、§3.4 配置、§3.7 双向转换的落地注记 vs 代码/测试）
- [ ] roadmap W8 标 `done` 的判定材料就绪（closure audit 证据链）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Q 表 Q2/Q3/Q4/Q5/Q7 均已回填处置结果（Q 表行可核），未裁决项保持显式"待确认"标注
- [ ] §3.x 抽查记录在 daily log（抽查条目 + 结果）
- [ ] requirement doc 无语义修订（仅状态回填与注记），R1-R8 审查结论未被破坏
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 收口验证（Proof）

Status: planned
Targets: 全量验证 + closure audit 材料

- Item Types: `Proof`

- [ ] 相关模块全量测试绿色：`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` + **nop-gateway 模块测试**（`-pl nop-service-framework/nop-gateway -am`，W7 follow-up 声明"nop-gateway 改动在 W8 全链回归中复核"——缓冲层 11 用例回跑；若裁定 nop-gateway 无改动仅由 nop-ai-gateway 测试间接覆盖，须落档理由）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors（复核 Phase 2）
- [ ] 全链矩阵最终复核（矩阵表 vs live 测试套件）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 上述三项验证全过，结果记录于 daily log
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 全链回归矩阵 100% 覆盖且零回归（补缺测试全绿、既有测试未改写）
- [ ] docs-for-ai 使用文档交付 + INDEX/source-anchors/nop-ai.md 同步 + doc link check 0 errors
- [ ] 需求文档 Q 表收口（Q2/Q3/Q4/Q5/Q7），未裁决项显式标注待确认
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline（requirement doc + docs-for-ai）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）新增测试覆盖的链路从入口到输出/指标在运行时确实连通，（b）无空方法体/静默跳过/no-op 作为正常实现（测试为真实断言，非占位）
- [ ] `./mvnw compile`（`-pl :nop-ai-gateway -am`）
- [ ] `./mvnw test`（`-pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` + nop-gateway 模块测试，见 Phase 4）
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本 plan 文件> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs nop-ai/nop-ai-gateway --severity high` 退出码 0（**用位置路径**：`--module nop-ai-gateway` 会解析到不存在的 `<root>/nop-ai-gateway` 空扫描恒绿，禁止使用）

## Deferred But Adjudicated

### 各 provider 显式非零 concurrencyLimit 缺省值（Q6 剩余）

- Classification: `optimization candidate`
- Why Not Blocking Closure: requirement §3.4/Q6 已决"缺省 = 不限制（零回归）"；"各 provider 是否需要显式配非零缺省"是部署面取值决策，不影响配置面语义契约成立。
- Successor Required: `no`

### 手动运维 / 多实例聚合 / 告警

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: requirement §3.6 显式 non-goal（本期），不影响 W8 收口判定。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 总延迟上限（time-based retry budget）落地：optimization candidate（W6/W7 裁定默认 null），如未来需求确认可单独评估。
- 指标告警规则/阈值：部署面增强，无 successor 硬依赖。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
