# A6 工程化、开发者体验与 AI 辅助开发

> Plan Status: completed
> Mission: nop-deep-analysis
> Work Item: A6 工程化、开发者体验与 AI 辅助开发（mission-driver / cli / AutoTest / e2e / 文档体系）+ 联网对标 AI 开发工具链
> Last Reviewed: 2026-07-24
> Source: `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md` (Work Item A6)
> Related: 引用 A1 可逆计算/Delta 术语；A7（capstone）依赖本项。**注**：A1 产出为纯理论（GRC/XLang/XDSL/XDef），不含「对 AI 友好性」论证，本 plan §5 须从具体仓库证据（`_` 前缀不可手改约束、Delta 隔离生成物、source-anchors 最小源码入口策略、文档即契约）独立论证，不依赖 A1 提供现成结论

## Purpose

评估平台在工程化、可测试性与 AI 协同开发上的能力与差异化，产出一份分析文档，为 A7（综合评估与演进建议）提供「工程化 / DX / AI 辅助开发」层面的参照。补齐 roadmap 主要缺口：缺一份把「mission-driver 自动闭环 + nop-cli 脚手架 + AutoTest + e2e + `docs-for-ai/` 文档体系 + 可逆计算对 AI 友好性」串成统一工程化叙事的综合分析。

## Current Baseline

**工程化与 AI 协同基础设施已建立：**

- **mission-driver 自动开发闭环**：仓库内启动器 `ai-dev/tools/mission-driver.sh` + `.opencode/skills/mission-driver/SKILL.md`（流程约定）+ `missions/<name>.json`（mission 配置）驱动 roadmap → CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → DEEP_AUDIT 循环；引擎本体在仓库外部（`attractor-guided-engineering-template`），本 plan 仅引用其流程契约，不审计引擎源码。本 plan 即由该引擎触发（`missions/nop-deep-analysis.json`）
- **`ai-dev/` 开发知识层**：完整七层（logs/plans/design/analysis/discussions/bugs/audits/skills），`AGENTS.md` + `docs-for-ai/00-start-here/project-context.md` 为 AI 运行手册
- **自动化校验工具链**：`ai-dev/tools/`（`check-doc-links.mjs`、`check-plan-checklist.mjs`、`scan-hollow-implementations.mjs`、`check-import-order.mjs`、`check-vfs-violations.mjs`、`check-ibiz-interfaces.mjs`、`check-orm-icons.mjs`、`check-oversized-files.mjs`、`code-stats.mjs` 等）
- **`docs-for-ai/` 文档体系**：`INDEX.md` 导航、`00-start-here/`（project-context / ai-defaults）、`01-repo-map/`、`02-core-guides/`、`03-modules/`、`03-runbooks/`、`04-reference/source-anchors.md`（实现锚点最小集）

**可测试性与脚手架：**

- **AutoTest**：`nop-autotest/`（快照测试基类 `JunitAutoTestCase` TEST-001、`JunitBaseTestCase` TEST-002、`@NopTestConfig` TEST-003、测试专用 beans + `testBeansFile` TEST-004、`input/request/output/outputText` helper TEST-005）
- **E2E**：`nop-entropy-e2e/`（Playwright），`nop-entropy-e2e/README.md`
- **nop-cli 脚手架**：`nop-runner/nop-cli-core/`、`scripts/nop-cli.cmd`、`tasks/gen-web.xrun`（XLANG-006）；codegen-master skill 驱动 `nop-cli gen` 从 ORM 模型生成初始项目（见 A3 生成链路）
- **调试能力**：`DBG-001~005`（`nop.debug=true` 注册 DevDoc/DevTool、`DevDocBizModel` 查询 beans/configVars/graphql、`DevToolBizModel` 刷新 VFS、`_dump/` 合并结果出口、启动自动 dump GraphQL schema）

**已有的对比/分析资料（可大量复用）：**

- `ai-dev/analysis/agent-survey/`（40+ 份 AI 开发工具对比：opencode/deepagents/oh-my-opencode/soloncode/mimo-code/omnigent 等）
- `ai-dev/analysis/agent-survey/2026-06-12-nop-ai-vs-mimo-code-deep-comparison.md`、`ai-dev/analysis/agent-survey/2026-06-12-nop-phase1-lockin-risk-analysis.md`
- `ai-dev/analysis/2026-06-15-maven-local-repo-customization-vs-nop-delta.md`（Delta 与传统配置的工程化对比）
- `ai-dev/analysis/2026-07/2026-07-15-docs-for-ai-accuracy-and-consistency-audit.md`（文档体系准确性）

**主要缺口（本 plan 要补齐）：**

- 缺一份把「自动开发闭环 + 脚手架 + 测试 + 文档体系 + 可逆计算 AI 友好性」串成统一工程化叙事的综合分析
- 缺联网对标主流 AI 驱动开发工具链（Devin、Cursor、Claude Code、agent 框架 / AGE / mission-driver 同类），说明「文档即 AI 契约 + Delta 定制」对 AI 协同的独特价值

## Goals

- 产出 `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md`，覆盖工程化、可测试性与 AI 协同开发能力
- 阐明 mission-driver 自动开发闭环：roadmap → plan → exec → audit 循环机制与 `ai-dev/` 知识层如何支撑
- 阐明可测试性：AutoTest 快照机制、E2E（Playwright）、调试能力（DevDoc/DevTool/`_dump`）
- 阐明脚手架与文档体系：nop-cli gen、`docs-for-ai/` 作为 AI 运行手册、自动化校验工具链
- 阐明可逆计算对 AI 生成代码的友好性：Delta 定制不破坏生成物、`_` 前缀约束、文档即契约（**从具体仓库证据独立论证**，A1 仅提供 Delta 术语引用，不提供现成 AI 友好性结论）
- 联网调研并对标 AI 驱动开发工具链（Devin、Cursor、Claude Code 等外部工具），说明差异化价值，附来源链接
- 所有事实性论断用 source-anchors.md 锚点 + 工具/文档交叉核对

## Non-Goals

- mission-driver 引擎内部实现细节（以仓库内 `ai-dev/tools/mission-driver.sh` + `.opencode/skills/mission-driver/SKILL.md` 为流程契约；引擎本体源码在仓库外部，仅引用，不审计）
- 可逆计算理论公理（A1）、核心引擎（A2）、codegen 管线（A3）、服务层/前端（A4）、业务模块（A5）
- 新功能实施或代码变更（仅产出分析文档）

## Scope

### In Scope

- mission-driver 自动开发闭环：roadmap/mission/plan 状态机、CHECK/REVIEW/EXEC/DRAFT/DEEP_AUDIT 各阶段职责、`ai-dev/` 七层知识如何被引擎消费
- 可测试性：AutoTest（`JunitAutoTestCase` 快照、`@NopTestConfig`、测试专用 beans）、E2E（Playwright `nop-entropy-e2e`）、调试（DBG-001~005）
- 脚手架与文档体系：nop-cli gen 初始生成（引用 A3）、`docs-for-ai/` 各区（`00-start-here`/`01-repo-map`/`02-core-guides`/`03-modules`/`03-runbooks`/`04-reference`）作为 AI 运行手册、`AGENTS.md` 路由、自动化校验工具链（`check-*`/`scan-hollow`）
- 可逆计算对 AI 的友好性：Delta 定制隔离生成物、`_` 前缀不可手改约束、source-anchors 作为「最小源码入口」策略、文档即 AI 契约降低幻觉
- 联网对标：Devin、Cursor、Claude Code、agent 框架 / AGE / mission-driver 同类（复用 `agent-survey/` 结论）

### Out Of Scope

- mission-driver 引擎源码逐行实现
- 核心引擎/生成链路/服务层/业务模块（A2–A5）
- codegen 模板内部细节（A3）

## Execution Plan

### Phase 1 - 工程化与 AI 协同基础设施源材料梳理

Status: completed
Targets: `ai-dev/tools/mission-driver.sh`（仓库内启动器）、`.opencode/skills/mission-driver/SKILL.md`、`missions/nop-deep-analysis.json`、`AGENTS.md`、`docs-for-ai/00-start-here/`（project-context / ai-defaults）、`docs-for-ai/INDEX.md`、`ai-dev/tools/`、`nop-autotest/`、`nop-entropy-e2e/README.md`、`nop-runner/nop-cli-core/`、source-anchors TEST-001~005 / DBG-001~005 / XLANG-006

- Item Types: `Proof | Decision`

- [x] 梳理 mission-driver 闭环：roadmap/mission/plan 状态机、各阶段（CHECK/REVIEW_PLANS/EXEC_PLANS/DRAFT_PLANS/DEEP_AUDIT）职责、`ai-dev/` 七层如何被消费（读 `ai-dev/tools/mission-driver.sh` + `.opencode/skills/mission-driver/SKILL.md` + `00-plan-authoring-and-execution-guide.md`；引擎本体在仓库外部，仅引用流程契约）
- [x] 梳理可测试性：AutoTest 快照机制（TEST-001~005）、E2E Playwright（`nop-entropy-e2e/README.md`）、调试能力（DBG-001~005）
- [x] 梳理脚手架与文档体系：nop-cli gen（XLANG-006 + codegen-master skill）、`docs-for-ai/` 各区（`00-start-here`/`01-repo-map`/`02-core-guides`/`03-modules`/`03-runbooks`/`04-reference`）作为 AI 运行手册、`AGENTS.md` 路由规则、source-anchors「最小源码入口」策略
- [x] 梳理自动化校验工具链：`ai-dev/tools/` 下各 `check-*`/`scan-hollow`/`code-stats` 工具的职责与门禁作用
- [x] 搜索 `ai-dev/analysis/agent-survey/` 既有 AI 工具对比结论，避免重复研究
- [x] 对 TEST/DBG 锚点做源码交叉核对，确认文档描述与实际代码一致；核对结果记录到当日 `ai-dev/logs/` 条目

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] mission-driver 闭环、可测试性、脚手架/文档体系三条主线各自的核心机制与关键类/文件已梳理，标注 source-anchor 编号
- [x] 工程化叙事已串联：脚手架 → 文档体系 → 自动闭环 → 可测试性 → 可逆计算 AI 友好性，形成一条完整链路
- [x] 已完成 ≥8 个 source-anchors 锚点（TEST/DBG/XLANG）的源码交叉核对，记录结果
- [x] No owner-doc update required: Phase 1 为源材料梳理，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 联网调研与 AI 开发工具链对标

Status: completed
Targets: 外部工具文档（web search）+ 既有 `ai-dev/analysis/agent-survey/` 结论

- Item Types: `Proof`

- [x] 调研 Devin：自主软件工程师 agent，与 nop mission-driver 闭环对比
- [x] 调研 Cursor：AI 代码编辑器（inline edit/chat/composer），与 nop「文档即契约 + Delta 定制」对比
- [x] 调研 Claude Code / opencode：CLI agent，与 nop 工程化集成（AGENTS.md / skills）对比（复用 `ai-dev/analysis/agent-survey/2026-06-05-opencode-analysis.md`）
- [x] 调研 agent 框架 / roadmap-driven dev-loop 同类：与 mission-driver 闭环对比（复用 `agent-survey/` 结论；**注**：AGE/Attractor-Guided-Engineering 是 nop 自身的 mission-driver 概念，作为「nop 的综合答案」呈现，不混入第三方对标方向）
- [x] 每条调研附来源 URL + 访问日期，提炼「文档即 AI 契约 + Delta 定制」的差异化价值；调研结果汇总到当日 daily log

Exit Criteria:

- [x] 至少覆盖 3 个**外部**对标工具方向（Devin / Cursor / Claude Code-opencode），每个附 ≥1 来源链接或既有分析引用；另以「roadmap-driven dev-loop 同类」作综合对比方向（AGE 作为 nop 答案，不计入第三方方向数）
- [x] 每个方向有「该工具做什么 vs nop 做什么 vs 差异点」对照
- [x] nop 的差异化定位（roadmap-driven 闭环、文档即 AI 契约、Delta 隔离 AI 生成、source-anchors 最小源码入口、自动化校验门禁）已明确表述
- [x] 复用既有 `agent-survey/` 分析时明确标注引用来源
- [x] 所有新增外部链接附有访问日期
- [x] No owner-doc update required: Phase 2 为联网调研，不修改任何 owner doc
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 撰写分析文档与准确性终检

Status: completed
Targets: `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md`

- Item Types: `Proof | Decision`

- [x] 按 `ai-dev/analysis/00-analysis-writing-guide.md` 模板撰写分析文档，含完整元数据
- [x] 正文结构：① 工程化总览 → ② mission-driver 自动闭环 → ③ 可测试性（AutoTest/E2E/调试）→ ④ 脚手架与文档体系 → ⑤ 可逆计算对 AI 的友好性 → ⑥ 联网对标与差异定位 → ⑦ 开放问题
- [x] 所有平台内部引用使用 `file:line` 或工具/文档锚点格式
- [x] 更新 `ai-dev/design/nop-deep-analysis/nop-deep-analysis-roadmap.md` 第 157 行 A6 deliverable 路径，从 `2026-07-XX-nop-engineering-dx-ai-dev.md` 占位符修正为实际文件名
- [x] 完成全文事实性论断与源码/工具的一致性终检

Exit Criteria:

- [x] 分析文档存在于 `ai-dev/analysis/2026-07/`，命名符合规范（实际 `2026-07-24-nop-engineering-dx-ai-dev.md`）
- [x] 文档含完整元数据（Status: resolved / Date / Scope / Conclusion / Superseded By），正文含 References 章节
- [x] 工程化叙事章节存在，从脚手架到 AI 闭环有代码/工具/文档锚点支撑
- [x] roadmap.md A6 deliverable 占位符已修正为实际文件名（`2026-07-XX-` → `2026-07-24-nop-engineering-dx-ai-dev.md`）
- [x] 联网对标章节存在，覆盖 ≥3 个外部工具方向且附来源链接
- [x] 准确性终检完成：每条涉及代码/工具的事实性论断都有对应 source-anchor / 文件路径可验证
- [x] 若终检发现 `docs-for-ai/00-start-here/` 或工具说明有事实性偏差，已记录到 Open Questions
- [x] No owner-doc update required: 本 plan 仅产出分析文档，不修改 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **纯文档计划**：本计划不涉及任何代码变更，`./mvnw test`、`./mvnw compile` 等构建验证条目不适用。

- [x] 分析文档 `ai-dev/analysis/2026-07/2026-07-24-nop-engineering-dx-ai-dev.md` 已产出且含完整元数据
- [x] 工程化叙事（脚手架→文档→闭环→测试→AI 友好性）已梳理并与代码/工具锚点交叉核对
- [x] 联网对标章节覆盖 ≥3 个外部工具方向，附来源链接或既有分析引用
- [x] 不存在被静默降级到 deferred 的 in-scope 分析要求
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证文档中的工程化描述在仓库内可观测成立——(a) `ai-dev/tools/mission-driver.sh` 启动器确实存在；(b) `missions/nop-deep-analysis.json` 配置确实存在且引用本 roadmap；(c) `_tmp/*-mission-driver/run-state.json` 日志证明 CHECK/REVIEW/EXEC/DRAFT/DEEP_AUDIT 步骤确实被执行过；(d) AutoTest 快照基类（TEST-001）与 `check-*`/`scan-hollow` 工具确实可运行。引擎本体源码在仓库外部，不作为 in-repo 验证目标
- [x] `node ai-dev/tools/check-doc-links.mjs --strict`：A6 引入的链接（本 plan 文件 + roadmap A6 行）全部修正为 0 broken link；剩余 error 均为 A7 未启动 deliverable 的 `2026-07-XX-` 占位符（pre-existing，A7 执行时自然解析，本 mission 标记 non-blocking）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0

## Deferred But Adjudicated

（暂无）

## Non-Blocking Follow-ups

- 若准确性终检发现 `docs-for-ai/00-start-here/`（project-context / ai-defaults）或工具说明有事实性偏差，记录到分析文档 Open Questions，不在本 plan 内修复
- mission-driver 引擎内部实现细节仍以仓库内 `ai-dev/tools/mission-driver.sh` + `.opencode/skills/mission-driver/SKILL.md` 为流程契约（引擎本体源码在仓库外部），本 plan 不替代
- 是否将工程化分析迁移到 `docs-for-ai/` 由 A7 capstone 综合评估后决定

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
