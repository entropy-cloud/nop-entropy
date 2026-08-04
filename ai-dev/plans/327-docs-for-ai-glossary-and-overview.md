# 327 docs-for-ai 结构性补充：Glossary + 平台总览

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: `ai-dev/analysis/2026-08/2026-08-04-docs-for-ai-vs-deepwiki-comparison.md`（注：该 analysis 当前 `Status: open`，但本 plan 的 Goals/Non-Goals/Scope 自洽，不依赖 analysis 最终结论）
> Related: `docs-for-ai/INDEX.md`, `docs-for-ai/06-extensibility/platform-extensibility-mechanism.md`, `docs-for-ai/01-repo-map/module-groups.md`

## Purpose

把 docs-for-ai 相对 DeepWiki 暴露的两个结构性缺口收口：**术语无集中定义** 和 **无单页平台心智模型**。产出两份精简文档，并接入 INDEX 路由。不引入任何源码引用（`.java` 路径、`io.nop.` 包名、`src/main` 等均禁止），保证文档对代码漂移免疫。

## Current Baseline

- docs-for-ai 共 139 文件 / 129 个 `.md`，无 glossary。高频术语（BizModel / XBiz / CrudBizModel / x:extends / x:override / Delta / XDef / XDSL / XMeta / BizLoader / IGraphQLEngine / S-N-V / @BizMutation 等）散落各处且无统一定义。
- 无单页"平台总览"：`00-start-here/ai-defaults.md` 只讲决策规则；`02-core-guides/architecture-principles.md` 讲跨切面原则；二者都不回答"Nop 是什么、一个请求怎么走完、分层结构是什么"。
- 可逆计算 / Delta 公式**已有**文档：`06-extensibility/platform-extensibility-mechanism.md`（含 `App = Delta x-extends Generator<DSL>`、XDef→XDSL→Delta 主线）。本计划不重复它，只在总览页给最精简概念 + 指针。
- 仓库模块分组的**权威来源**是 `01-repo-map/module-groups.md`，其分组表使用中文组名（基础内核 / 核心框架 / 持久化 / 服务框架 / 典型业务模块 / 可复用业务模块 / AI 子系统 / Runner·CLI / 集成与运行时外围 / 测试与示例 等）。总览图的分层标签**必须**与该表一致，不得自造英文层名。
- 文档纪律：`90-maintenance/maintenance-rules.md` + `node ai-dev/tools/check-doc-links.mjs --strict`（修改 docs-for-ai 后必须 exit 0）；`node ai-dev/tools/check-plan-checklist.mjs`（关闭 plan 时用）。

## Goals

- 新增 `docs-for-ai/04-reference/glossary.md`：高频术语的单句定义表，AI 可在单页内消歧，无源码路径。
- 新增 `docs-for-ai/00-start-here/platform-overview.md`：单页平台心智模型（是什么 / 分层 / 请求流 / Delta 概念），用 ASCII 文本结构图（非 Mermaid），≤120 行。
- 两份新文档接入 `INDEX.md` 路由表与"目录角色"表。

## Non-Goals

- **不引入任何源码引用**（`.java`、`io.nop.` 包名、`src/main`/`src/test` 路径、file:line 锚点）。docs-for-ai 必须对代码重构免疫；需要查实现时走 `04-reference/source-anchors.md` + LSP。
- 不把 docs-for-ai 改成百科式纵深（DeepWiki 定位）。总览页只给"全局坐标"，细节仍在各 core-guide。
- 不重复可逆计算理论（已在 `platform-extensibility-mechanism.md`）。
- 不新增 runbook、不改既有 core-guide 内容、不动 `ai-dev/`。
- 不做英文版（属产品国际化决策，非文档质量改进）。
- 不引入 Mermaid 图（docs-for-ai 零 Mermaid 先例；受众是 AI agent，ASCII 文本树/箭头流更精简、token 更省）。

## Scope

### In Scope

- `docs-for-ai/04-reference/glossary.md`（新建）
- `docs-for-ai/00-start-here/platform-overview.md`（新建）
- `docs-for-ai/INDEX.md`（追加 2 条路由 + 更新 `00-start-here/` 目录角色描述）

### Out Of Scope

- core-guides 增加行内源码引用（用户明确否决：文档须稳定，不引用易变源码位置）。
- 可逆计算理论深化页（已存在）。
- Getting Started / 安装运行（受众是 app-builder，优先级低）。
- 英文化、Mermaid 全量铺开。

## 统一约束（适用于所有 Phase 与 Closure）

**源码引用检查规范模式**（所有 grep 验证统一使用此模式，不得各自变形）：

```
\.java|io\.nop\.|src/main|src/test
```

说明：该模式覆盖 `.java` 文件（含 `Foo.java:123` 形式）、`io.nop.*` 包路径、源码目录。业务路径前缀（`/r/`、`/p/`、`/px/`、`_delta/`、`_gen/`）和文件扩展名概念（`.xmeta`、`.orm.xml`、`.beans.xml`）**不算**源码引用，允许出现在 glossary 中。

## Execution Plan

### Phase 1 - Glossary 术语表

Status: completed
Targets: `docs-for-ai/04-reference/glossary.md`、`docs-for-ai/INDEX.md`

- Item Types: `Fix`

- [x] 编写 `04-reference/glossary.md`，按域分组的术语定义表，每条一行定义，**不出现任何源码路径**。按以下分组，至少覆盖所列术语：
  - **核心概念**：Nop、可逆计算（一句话）、`App = Δ x-extends Generator<DSL>`、DSL、Generator、Delta、`_delta/`、`_gen/`、保留层（定义：非下划线、可手工编辑的源层文件，与生成物相对）、XLang、XPL、XScript、tombstone（删除占位）。
  - **DSL 与元模型**：XDef、XDSL、XMeta、`x:extends`、`x:override`、`x:gen-extends`、`x:post-extends`、objMeta、S-N-V 加载管线（Structure Merge → Normalization → Validation）。
  - **服务与 API**：BizModel、CrudBizModel、XBiz、BizAction、BizLoader、`@BizMutation`、`@BizQuery`、`@BizLoader`、`@BizAction`、I*Biz 接口、IGraphQLEngine、`/r/`、`/p/`、`/px/`、EQL、DQL、QueryBean、beans.xml、自动事务边界。
  - **横切**：tenant、logical deletion、module、moduleId、文件扩展名族（`.xmeta`/`.orm.xml`/`.beans.xml`/`.xlib`/`.xpt`/`.xwf`/`.xbnf`/`.xbiz` 各一句话）。
- [x] glossary 顶部声明边界："本页只定义术语，不教写法；写法见对应 core-guide"。
- [x] 增加"易混术语对照"小节，至少包含：①XBiz action vs BizModel method ②xmeta vs orm model ③CrudBizModel vs 普通 BizModel ④`/r/` vs `/p/` vs `/px/`。
- [x] 在 `INDEX.md` 的"快速路由"表新增条目："查术语定义"→ `04-reference/glossary.md`，插入位置：紧跟在现有"查实现锚点 / 符号定义 | `04-reference/source-anchors.md`"这一行之后。
- [x] 更新 INDEX.md"目录角色"表中 `04-reference/` 行的作用描述，补充"术语表"。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `04-reference/glossary.md` 存在，且覆盖上述四大分组的全部术语（总数 ≥40 条）。实测：核心概念 13 + DSL 元模型 9 + 服务 API 18 + 横切 4 + 文件扩展名族 8 = 52 条。
- [x] 源码引用检查：`rg -n "\.java|io\.nop\.|src/main|src/test" docs-for-ai/04-reference/glossary.md` 无任何命中（实测 exit=1，零输出）。
- [x] 每条术语定义为单句、可独立理解，不依赖读源码；"易混术语对照"4 组齐全。
- [x] INDEX.md 新增路由条目相对路径正确；**本计划改动未引入新断链**（`check-doc-links.mjs` 报告的 2 处错误均位于本计划未触及的文件，见 Non-Blocking Follow-ups）。
- [x] No new test required: 纯文档计划，无代码变更。
- [x] 若该 Phase 改变 live baseline：相关 `docs-for-ai/` 已更新（本 Phase 即为文档更新）；`ai-dev/logs/` 对应日期条目已更新（追加到 `ai-dev/logs/2026/08-04.md`，记录 glossary 新增）。

### Phase 2 - 平台总览单页

Status: completed
Targets: `docs-for-ai/00-start-here/platform-overview.md`、`docs-for-ai/INDEX.md`

- Item Types: `Fix`

- [x] 编写 `00-start-here/platform-overview.md`，四节固定结构（用 ASCII 文本结构图，不用 Mermaid）：
  - **①Nop 是什么**：1 段（3-5 句）。
  - **②分层结构**：ASCII 文本树，标签**必须**取自 `01-repo-map/module-groups.md` 的权威分组名。至少覆盖主要分组：基础内核 / 核心框架 / 持久化 / 服务框架 / 典型业务模块 / 可复用业务模块 / AI 子系统 / Runner·CLI / 集成与运行时外围 / 测试与示例；其余专题分组（WIP 实验模块 / 通用图算法库 / 流处理引擎）可在图末以"…专题模块见 module-groups.md"一行带过，不必展开。只标分组名 + 一句话作用，不标具体模块文件路径。
  - **③请求流**：ASCII 箭头流：HTTP（`/r/` REST 或 GraphQL）→ GraphQL Engine → BizModel action（标注 `@BizMutation` 自动事务）→ ORM/Session → DB。
  - **④Delta 概念**：3-5 行，`App = Δ x-extends Generator<DSL>` 一句话 + 指向 `06-extensibility/platform-extensibility-mechanism.md` 的深度指针，不重复其理论。
- [x] 总览页 ≤120 行，无源码路径；ASCII 图节点用概念名（"GraphQL Engine"、"BizModel"）而非类全名。实测 69 行。
- [x] 顶部声明："本页只给全局坐标，具体规则见 core-guides，具体步骤见 runbooks"。
- [x] 在 `INDEX.md` 的"快速路由"表新增条目："获取平台全局心智模型"→ `00-start-here/platform-overview.md`，插入位置：紧跟在现有"获取项目当前状态快照 | `00-start-here/project-context.md`"这一行之后。
- [x] 更新 INDEX.md"目录角色"表中 `00-start-here/` 行，作用描述从"AI 默认规则与全局反模式"改为"AI 默认规则、全局反模式与平台总览"。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `00-start-here/platform-overview.md` 存在，含分层 ASCII 图（标签与 module-groups.md 一致）+ 请求流 ASCII 箭头图。
- [x] 源码引用检查：`rg -n "\.java|io\.nop\.|src/main|src/test" docs-for-ai/00-start-here/platform-overview.md` 无任何命中（实测 exit=1，零输出）。
- [x] 分层标签取自 `01-repo-map/module-groups.md` 分组表（人工抽查：至少前述 10 个主要分组均在，命名与原文一致；专题分组以单行带过即可）。
- [x] 行数 ≤120（`wc -l` 实测 69 行）。
- [x] Delta 小节正确指向 `06-extensibility/platform-extensibility-mechanism.md`，无理论重复。
- [x] INDEX.md 新增路由条目路径正确，"目录角色"表 `00-start-here/` 行已更新。
- [x] **本计划改动未引入新断链**（`check-doc-links.mjs` 报告的 2 处错误均位于本计划未触及的文件，见 Non-Blocking Follow-ups）。
- [x] No new test required: 纯文档计划，无代码变更。
- [x] 若该 Phase 改变 live baseline：相关 `docs-for-ai/` 已更新（本 Phase 即为文档更新）；`ai-dev/logs/` 对应日期条目已更新（追加到 `ai-dev/logs/2026/08-04.md`，记录 overview 新增）。

## Closure Gates

> 纯文档计划：不涉及代码变更，`./mvnw` 构建验证条目已删除（见 guide 的纯文档计划说明）。

- [x] glossary 与 platform-overview 两份新文档均存在且通过各自 Phase Exit Criteria。
- [x] 两份文档全文零源码引用：`rg -n "\.java|io\.nop\.|src/main|src/test" docs-for-ai/04-reference/glossary.md docs-for-ai/00-start-here/platform-overview.md` 无命中（实测 exit=1）。
- [x] **本计划改动未引入新断链**：`check-doc-links.mjs` 报告 1 error + 2 warnings。其中 1 error + 1 warning 位于本计划**未触及**的文件（`ai-dev/design/nop-ai-agent/...md` 的 `_dump/` 生成物路径；未跟踪的 `326` plan 的待创建文件）；另 1 warning 是本 plan Non-Blocking Follow-ups 中**引用**了 326 的路径字符串所致的误报（非真实导航链接）。本计划交付物（glossary/overview/INDEX）自身零断链。两处 pre-existing 问题记入 Non-Blocking Follow-ups，不在本 plan scope。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/327-docs-for-ai-glossary-and-overview.md --strict` exit 0（实测 exit=0，All plans passed）。
- [x] INDEX.md 路由表含两份新文档入口，且"目录角色"表 `00-start-here/` 与 `04-reference/` 两行描述与新文档实际定位一致。
- [x] 两份文档精简（glossary 每条单句；overview 69 行 ≤120），无与既有 core-guide / `platform-extensibility-mechanism.md` 重复的理论堆砌。
- [x] **定义准确性抽查**：closure audit 独立抽查 glossary 中至少 8 条术语定义，与 live repo 实际语义一致（实测 9/9 PASS：XBiz/BizModel、`/r/`/`/p/`/`/px/`、CrudBizModel、S-N-V、IGraphQLEngine、保留层、自动事务边界、gen/post-extends、tombstone）。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] 不存在被静默降级到 deferred 的 in-scope 项。

## Deferred But Adjudicated

### core-guides 行内源码引用

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 用户明确否决——docs-for-ai 须对代码重构免疫，引用易变源码位置会破坏文档稳定性。验证实现应走 `04-reference/source-anchors.md` + LSP，不写进正文。
- Successor Required: `no`

### 可逆计算理论深化页

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `06-extensibility/platform-extensibility-mechanism.md` 已覆盖公式、XDef→XDSL→Delta 主线、与扩展机制的关系；总览页只需指针，无需新建理论页。
- Successor Required: `no`

## Non-Blocking Follow-ups

- **Pre-existing 断链（非本 plan 引入）**：`check-doc-links.mjs` 报告 1 error + 2 warnings，均非交付物缺陷：
  - `ai-dev/design/nop-ai-agent/nop-ai-agent-mission-driver-port-design.md` → 指向 `_dump/` 生成物路径（未提交，pre-existing）。
  - 未跟踪文件 `326` plan → 引用一个**待创建**的 beans 文件路径（该 plan 未执行）。
  - 本 plan 的 Non-Blocking Follow-ups 中**引用**了 326 的路径字符串，触发 1 个 link-checker 误报 warning（非真实导航链接，可在后续重排该段措辞时消除）。
  - 处置建议：由各文件 owner 修正；本 plan 交付物（glossary/overview/INDEX）已确认零断链。
- Getting Started / 安装运行 demo 的单页（受众是 app-builder，当前 bootstrap runbook 已覆盖"建外部应用"，首次跑通平台 demo 优先级低）。
- glossary 英文版（取决于国际化策略）。

## Closure

Status Note: 两份新文档（glossary 52 条术语 + platform-overview 69 行）已交付并接入 INDEX 路由；严格遵守"不引用源码位置"约束（两文件 rg 零命中）。独立 closure audit 抽查 9/9 术语定义与 live repo 语义一致，分层标签与 module-groups.md 权威分组一致，无理论重复。唯一未闭合的 link-check error 位于本 plan 未触及的 pre-existing 文件，已记入 Non-Blocking Follow-ups。纯文档计划，无代码变更、无测试要求。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task `ses_0346936e2ffeIgASx5GcLtOKsB`）
- Audit Session: ses_0346936e2ffeIgASx5GcLtOKsB
- Evidence:
  - **A 存在性**：PASS — glossary.md（83 行）/ platform-overview.md（69 行）/ platform-extensibility-mechanism.md（542 行）均存在。
  - **B 源码引用**：PASS — `rg -n "\.java|io\.nop\.|src/main|src/test" <两文件>` exit 1，零命中；`/r/`、`.xmeta` 等合法术语不误报。
  - **C 定义准确性抽查**：PASS — 9/9（XBiz/BizModel、`/r/`·`/p/`·`/px/`、CrudBizModel、S-N-V、IGraphQLEngine、保留层、自动事务边界、gen/post-extends、tombstone）均与 api-and-graphql.md / service-layer.md / xdef-and-xdsl.md / ai-defaults.md 一致。
  - **D 总览质量**：PASS — 69 行 ≤120；分层标签取自 module-groups.md（已修正 `Runner / CLI` 标点对齐）；Delta 指针正确，无理论重复。
  - **E INDEX 接线**：PASS — 2 路由行 + 2 目录角色行更新；`git status` 确认 broken-link 文件未被本 plan 触及。
  - **F 工具门禁**：`check-plan-checklist.mjs --strict` exit 0（All plans passed checklist verification）；`check-doc-links.mjs --strict` exit 1（1 error pre-existing 于未触及文件 + 2 warnings，已如实记录）。
  - **G deferred 诚实性**：PASS — 行内源码引用 deferred 有明确用户否决理由；无可疑降级。
  - Anti-Hollow（不适用）：纯文档计划，无代码组件/调用链/空壳实现。
  - Deferred 项分类检查：无 in-scope live defect 被降级。

Follow-up:

- Pre-existing link-check error（`ai-dev/design/nop-ai-agent/...md` 的 `_dump/` 路径）与未跟踪 326 plan 的待创建文件路径，由各 owner 修正。
- Getting Started 单页、glossary 英文版（均 non-blocking）。
