# W3 llm.xdef 并发上限（concurrencyLimit）配置面扩展

> Plan Status: active
> Last Reviewed: 2026-08-15
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W3）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.3/§3.4/§五 Q6）
> Related: `ai-dev/design/nop-ai-gateway/01-architecture.md`
> Mission: nop-ai-gateway-failover
> Work Item: W3

## Purpose

扩展 `llm.xdef` 配置面，新增账号并发上限字段 `concurrencyLimit`（provider 级缺省 + 账号级覆盖，缺省 = 不限制），经 codegen 再生成 `_LlmAccountModel` 等生成物并补 `LlmConfigHelper` 读取支持，为 W5/W6/W7 的并发限流运行时提供配置面。`llm.xdef` 位于 nop-kernel/nop-xdefs（protected area），本计划即 plan-first 载体。

## Current Baseline

- `llm.xdef` 位于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/llm.xdef`；根元素已有 `rateLimit` 属性（每秒 QPS，排队语义，与 `concurrencyLimit` 的跳过语义并存不互斥——需求 §3.4 已裁定）。
- `<accounts>` 结构（`llm.xdef` live 核实）：`<accounts xdef:body-type="list" xdef:key-attr="id">`，`<account id="!string" xdef:name="LlmAccountModel" apiKey baseUrl quotaLimit renewAt/>`。账号链语义：备用账号有序链（不含主账号），主账号走 `resolveApiKey(provider)`。
- 生成物 `_LlmAccountModel` 位于 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/_gen/_LlmAccountModel.java`（_gen 生成物，**禁止手编**），当前字段：`apiKey` / `baseUrl` / `id` / `quotaLimit` / `renewAt`。
- `LlmConfigHelper.resolveAccountChain(provider)` 位于 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java:161`，返回 `List<LlmAccountModel>`。
- 既有测试：`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/TestLlmConfigHelperAccountChain.java` + `TestLlmConfigHelper.java`（零回归目标）。
- 需求 §3.4 已决：配置粒度 = provider 级缺省 + 账号级覆盖；主账号无 `LlmAccountModel` 实例，限流值取 provider 级缺省；缺省 = 不限制 → 既有配置与测试零回归。
- xdef 修改流程约束（project-context）：改 schema 后须先 `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 重新打包，下游模块的 xdef codegen 才使用新 schema（codegen 从本地仓库 jar 加载）。
- nop-ai-core pom 已配置 `exec-maven-plugin`（codegen 载体，pom.xml:58）。

## Goals

- `llm.xdef` 新增 `concurrencyLimit`：provider 根元素缺省值 + `<accounts><account>` 账号级覆盖，非 mandatory（缺失即 null）。**层级语义**：账号级未配置 = 回退 provider 级缺省；provider 级未配置 = 不限制；显式配置 `0`/负数 = 显式不限制（不回退）。
- `_LlmAccountModel` 等生成物经 codegen 再生成（禁止手编），新字段可由代码读取。
- `LlmConfigHelper` 提供读取支持（账号级值 + provider 级缺省值的解析入口），供 W5/W6/W7 并发限流运行时消费。
- 零回归：既有 `<accounts>` 配置、`TestLlmConfigHelperAccountChain`/`TestLlmConfigHelper` 全绿。

## Non-Goals

- 并发计数/切换运行时实现（W5/W6/W7 消费，本计划只提供配置面）。
- 模型类路由组配置（Q8，归 W5）。
- 其他账号字段扩展（权重 / 按账号覆盖 model——需求 §3.4"默认不扩展"）。
- 配置热更新（显式 non-goal，需求 §3.4）。

## Scope

### In Scope

- XDEF-01: `llm.xdef` 新增 `concurrencyLimit`（provider 根元素缺省 + `<accounts>` 账号级覆盖；`xdef:name="LlmAccountModel"` 的 account 元素加字段）。
- XDEF-02: `_LlmAccountModel` 等生成物经 codegen 再生成（nop-kernel/nop-xdefs 打包 → nop-ai-core 再生成）；`LlmConfigHelper` 读取支持。
- XDEF-03: 零回归验证（既有账号链配置与测试不破坏）+ 新字段读取测试。

### Out Of Scope

- W5/W6/W7 的并发运行时（计数/切换/fail-loud）。
- 模型类分组配置面（Q8）。

## Execution Plan

### Phase 1 - llm.xdef 扩展 + codegen 再生成 + helper 读取（XDEF-01 + XDEF-02）

Status: planned
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/llm.xdef`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/_gen/_LlmAccountModel.java` + `_LlmModel.java`（生成物）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java`、`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`（字段集描述同步）

- Item Types: `Fix | Decision | Follow-up`

- [ ] 类型裁定：`concurrencyLimit` 字段类型 = `int`，**非 mandatory（无 `!` 前缀，缺失即 null = 不限制）**——禁止写成 `!int`（否则既有 `{provider}.llm.xml` 全部校验失败，零回归灾难）。语义：账号显式配 `0` 或负数 = 显式"不限制"（不回退 provider 缺省）；未配置 = null = 回退 provider 级缺省；provider 级也未配置 = 不限制。记录裁定到 plan。
- [ ] `llm.xdef` 修改：provider 根元素新增 `concurrencyLimit` 属性（缺省语义 = 不限制）；`<accounts><account>` 元素新增 `concurrencyLimit`（账号级覆盖）。javadoc 注释写明语义（in-flight 并发上限、跳过语义、与 rateLimit 排队的区别、provider 缺省与账号覆盖关系、显式 0 = 不限制、非 mandatory）。
- [ ] codegen 再生成：`./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` → nop-ai-core 构建触发 exec-maven-plugin codegen（`precompile/gen-ai-xdsl.xgen` 渲染 `/nop/schema/ai/llm.xdef`），`_LlmAccountModel` **与 `_LlmModel`** 再生成含新字段（零手编）；`git diff` 核对再生 diff 仅含新字段（模板漂移检查——若出现无关改动，还原生成物并排查模板来源）。
- [ ] `LlmConfigHelper` 读取支持：账号级 `concurrencyLimit`（从 `LlmAccountModel`）+ provider 级缺省（从 `loadConfig(provider)` 返回的根模型，`config.getConcurrencyLimit()`，有 `checkRateLimit` 的 `config.getRateLimit()` 先例 `ChatServiceImpl.java:342-349`）的解析入口；**语义契约落档目的地 = helper javadoc（行为契约权威）** + 同步写一行到 `nop-ai-llm-error-normalization-design.md` 账号字段集描述（未配置 = 回退/不限制、显式 0/负数 = 不限制）；主账号（无 `LlmAccountModel` 实例）路径 = 取 provider 级缺省。
- [ ] 文档同步（Follow-up）：`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md` 账号字段集描述（约 :254 列出 apiKey/baseUrl/quotaLimit/renewAt）补 `concurrencyLimit` + 层级语义一行；`02-account-failover-requirement.md` §3.4"既有结构（id/apiKey/baseUrl/quotaLimit/renewAt）"清单补 `concurrencyLimit`（一行）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `llm.xdef` 新字段就位且 javadoc 语义完整（非 mandatory、显式 0、provider/账号覆盖关系——grep/read 实证）。
- [ ] `_LlmAccountModel` 与 `_LlmModel` 生成物均含 `concurrencyLimit`（read 实证），且非手编；`git diff` 显示再生 diff 仅含新增字段（模板漂移检查通过）。
- [ ] `LlmConfigHelper` 新读取入口可编译可调用（`./mvnw compile -pl :nop-ai-core -am` 通过）；主账号（null account）路径语义已落档（helper javadoc）。**Rule #25 拆分说明**：新读取入口的单元测试于 Phase 2 编写（Phase 1 仅编译门禁；Phase 2 五态矩阵完整覆盖）。
- [ ] `nop-ai-llm-error-normalization-design.md` 账号字段集描述已同步（含 concurrencyLimit + 层级语义）；`02-account-failover-requirement.md` §3.4 字段清单已同步——两处均不得静默跳过。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 新字段测试 + 零回归（XDEF-03）

Status: planned
Targets: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/`（TestLlmConfigHelperAccountChain / TestLlmConfigHelper 或新增测试类）、`nop-ai/nop-ai-core/src/test/resources/_vfs/nop/ai/llm/`（测试 provider 配置数据：test-accounts.llm.xml 扩展或新增文件——root 级 concurrencyLimit + 各 account 混合设置 + 一个全无配置的 provider）

- Item Types: `Fix | Proof`

- [ ] 新字段读取测试（四态 + 主账号路径）：账号级覆盖生效（配置了账号级值时返回账号值）、账号未配置时回退 provider 级缺省、均未配置 = 不限制、**显式配置 0/负数 = 显式不限制（不回退 provider 缺省）**、**主账号路径（无 `LlmAccountModel` 实例）取 provider 级缺省**。
- [ ] 零回归：`TestLlmConfigHelperAccountChain` / `TestLlmConfigHelper` 全绿（未配置 `concurrencyLimit` 的既有 `<accounts>` 配置行为不变）。

Exit Criteria:

- [ ] 新字段五态读取测试全绿（账号覆盖 / provider 缺省回退 / 不限制缺省 / 显式 0 / 主账号回退 provider 缺省）。
- [ ] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` BUILD SUCCESS（mission 三模块验证基线），既有测试零回归。
- [ ] **接线验证**（Minimum Rules #23）：`LlmConfigHelper` 读取入口确实从配置模型（`LlmAccountModel` 新字段 + provider 根属性）取值（测试断言 + 代码路径实证），非 stub。
- [ ] **无静默跳过**（Minimum Rules #24）：未配置 `concurrencyLimit` 的语义是明确的"不限制"契约（测试钉死），非静默忽略。
- [ ] `No owner-doc update required`（文档同步已含于 Phase 1）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `concurrencyLimit` 配置面完整落地（xdef + 生成物 + helper 读取 + 三态测试）。
- [ ] 零回归验证完成（既有账号链配置/测试全绿）。
- [ ] 生成物经 codegen 再生成、无手编（生成物检查实证）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：xdef 语义、生成物来源、读取路径接线、零回归）。
- [ ] **Anti-Hollow Check**：closure audit 已验证 helper 读取路径确实从配置取值（测试断言），无空壳/no-op。
- [ ] `./mvnw compile -pl :nop-ai-core -am`
- [ ] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`
- [ ] checkstyle / 代码规范检查通过（或按 mission lint 兜底通道判定）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（关闭时执行——新 helper 方法无运行时调用方属空壳高危面）

## Deferred But Adjudicated

### 各 provider 显式配置非零缺省值

- Classification: `optimization candidate`
- Why Not Blocking Closure: 需求 §五 Q6 已决"缺省 = 不限制（零回归）"，剩余"各 provider 是否需要显式配非零缺省值"是部署时配置决策，不影响配置面契约成立；W5/W6/W7 消费时由部署方显式配置。
- Successor Required: `no`

### 权重 / 按账号覆盖 model 字段

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需求 §3.4 明确"默认不扩展"；若需求确认则作为独立计划扩展（同 xdef 面、同 codegen 流程）。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- （关闭时填写）
