# GSD-Core vs AGE：上下文工程 vs 轨迹工程

> Status: final
> Date: 2026-06-07
> Scope: 第三方 AI 工程框架 GSD-Core 与 AGE (Attractor-Guided Engineering) 的深度对比

## 简介

- **GSD-Core**（`@opengsd/gsd-core`）是一个产品化的 AI 工程框架，核心解决的问题是**上下文衰减**：AI 编码代理随着上下文窗口填满而退化，不是因为模型变差，而是信噪比在有限注意力窗口中持续下降
  - GitHub: https://github.com/open-gsd/gsd-core
  - 三根支柱：上下文工程（fresh-context subagent）+ spec-driven development + meta-prompting
  - 5 步核心循环（discuss → plan → execute → verify → ship）+ 4 个可选/附加相位（spec、validate、secure、review）
  - 33 个 agent 定义文件（GSD-Core 内部文档有时引用 '31-agent roster'，源于早期版本），88+ 个 workflow 文件，文件系统级持久化（`.planning/`）
- **AGE (Attractor-Guided Engineering)** 是从 nop-chaos-flux 的实践中长出来的方法论，核心思想是 `状态空间 -> 吸引子 -> 轨迹 -> 控制`
  - AGE Template（`attractor-guided-engineering-template`）定义了 21 个目录的语义拓扑，覆盖从输入捕获到交付后反思的完整链条
  - 两层核心约束：(1) 规范性文档与时效性文档分离（stable name vs dated name）；(2) 渐进式披露（从 index 到详细内容）
  - 每个问题有明确的真值来源（`source-of-truth-and-precedence.md`）：需求看 requirements/，行为看 design/，技术看 architecture/，数据库看 model 文件
  - 微信公众号系列文章：
    - 《Attractor Before Harness: AI 大规模开发的方法论》 https://mp.weixin.qq.com/s/TwMkUDLNo2-bIrXrfvPqIw
    - 《从 Spec-Driven Development 到 Attractor-Guided Engineering》 https://mp.weixin.qq.com/s/j4dZm1bAK61qB8i5RzHRWA
  - AGE 并不预设很多固定工具，强调工具应从反复出错的地方**逐步生长出来**：bug note → lesson → skill/prompt → audit script → lint rule → CI guard
- **核心差异**：GSD-Core 解决"单次执行中 AI 如何保持清醒"，AGE 解决"连续多次变更后仓库如何保持方向"。前者管上下文窗口，后者管仓库轨迹。

## 分析

### 1. 问题定义：上下文衰减 vs 轨迹漂移

两者解决的问题根本不同。

**GSD-Core 的问题**：

AI 编码代理在长会话中退化。根因不是模型变差，而是信噪比在有限注意力窗口中持续下降（综合自 `multi-agent-orchestration.md` 和 `context-engineering.md`）。

上下文窗口是有限资源。会话越长，早期信息被冲淡得越厉害。GSD-Core 定义了 4 级衰减：PEAK（0-30%）→ GOOD（30-50%）→ DEGRADING（50-70%，警告用户）→ POOR（70%+，立即 checkpoint）。

解决方案是 fresh-context subagent：瘦编排器保持精简，重活交给短命专用 agent，每个 agent 拿到干净的上下文窗口，只接收它需要的信息。

**AGE 的问题**：

AI 在多次会话中反复修改仓库。单次变更可能完全正确，但连续 100 次变更后仓库可能已经偏离正确方向。AGE 的 `age-from-state-engineering-to-trajectory-engineering.md` 说得直接：传统软件工程的基本结构是"状态检查 + 人类隐性方向感"。AI 深度参与后，人脑不再持有完整蓝图，"方向感"必须外化到仓库结构中。

解决方案是吸引子：owner doc 定义"仓库应收敛到哪里"，审计检查"是否在收敛"，轨迹记录"曾经怎样"。

**两者不在同一个层面**：GSD-Core 管 AI 在一次执行中的认知清晰度，AGE 管 AI 在多次执行后的方向正确性。

### 2. 工作流：5 步核心循环 + 4 可选相位 vs 2 层约束

**GSD-Core 的 5+4 相位工作流**：

```
核心循环: discuss → plan → execute → verify → ship
可选附加: spec → validate → secure → review
```

核心 5 步是每次执行的必经路径，spec（需求分析）、validate（验证间隙审计）、secure（安全审计）、review（跨 AI 对等审查）按需触发。每个相位都有独立的 workflow 文件、专用的 agent、明确的入口/出口条件。关键机制：

- **spec-phase**：苏格拉底式访谈 + 量化模糊度评分（Goal 35%、Boundary 25%、Constraint 20%、Acceptance 20%）。门控：模糊度 ≤ 0.20 才能通过。产出 REQUIREMENTS.md，含可证伪需求
- **plan-phase**：研究 → 计划 → 验证 → 完成。编排 gsd-phase-researcher → gsd-planner → gsd-plan-checker。修订循环最多 3 轮
- **execute-phase**：波次并行执行。编排器保持精简，委托给 gsd-executor subagent。运行时特定生成
- **verify-phase**：目标回溯分析。"Task completion ≠ Goal achievement."四个验证级别：TRUTH → EXISTS → WIRED → TESTS PROVE
- **validate-phase**：Nyquist 验证间隙审计
- **secure-phase**：威胁缓解验证，对照 PLAN.md 威胁登记册
- **review**：跨 AI 对等审查——调用外部 AI CLI（gemini、claude、codex、coderabbit）

整个流水线是线性的、阶段性的、每次"从需求到交付"走一遍。

**AGE 的 2 层约束**：

AGE 没有固定的相位流水线。它只有两层约束：

1. **渐进式披露**：从最小的入口逐步展开到详细内容
2. **规范与时效性历史分离**：稳定文件用稳定文件名，时间敏感记录带日期

在这两条约束内，文档结构由领域决定，执行流程由具体 plan 决定。nop-chaos-flux 有 10 阶段工作流，nop-entropy 有 Maven 构建管线，AGE Template 有轻量 10 步流程——都是领域适配的结果，不是 AGE 强制的。

AGE 不关心"需求到交付走几步"，它关心"owner doc 是否维护了方向、plan 是否有闭合条件、轨迹是否被记录"。

**对比**：

| 维度 | GSD-Core | AGE |
|------|----------|-----|
| 工作流形态 | 5 步核心循环 + 4 可选相位 | 无固定流水线，2 层约束 |
| 控制粒度 | 相位级（每个相位有入口/出口门控） | Plan 级（每个 plan 有自己的闭合条件） |
| 规范性 | 步骤级（92 个 workflow 文件精确定义每一步） | 语义约束级（文件命名、真值来源、审计契约） |
| 领域适配 | 通过 runtime 抽象适配 11+ 平台 | 通过领域决定文档结构 |
| 单次执行 | 精细管控（波次并行、agent 分派、上下文预算） | 不关注（依赖 AI 自行执行，只管闭合验证） |

### 3. Agent 系统：33 个预设 agent vs 无预设

这是两者在工具设计上最大的差异。

**GSD-Core 的 33 个 agent**：

每个 agent 有定义好的完成标记（如 `## PLANNING COMPLETE`、`## PLAN COMPLETE`），workflow 通过正则匹配检测完成。关键 agent：

| 类别 | Agent | 职责 |
|------|-------|------|
| 研究 | gsd-phase-researcher, gsd-domain-researcher, gsd-ai-researcher, gsd-codebase-mapper | 并行研究阶段 |
| 规划 | gsd-planner, gsd-plan-checker | 计划 + 检查修订循环 |
| 执行 | gsd-executor | 波次并行实现 |
| 验证 | gsd-verifier, gsd-code-reviewer | 目标回溯验证 |
| 安全 | gsd-security-auditor, gsd-nyquist-auditor | 威胁 + 验证间隙 |
| 文档 | gsd-doc-writer, gsd-doc-verifier, gsd-doc-synthesizer, gsd-doc-classifier | 文档生命周期 |

工具权限最小化——每个 agent 只拿到必要权限，没有 Bash 执行权限除非需要。

模型路由：5 个 profile（quality / balanced / budget / adaptive / inherit），根据 phase_type 和 agent 角色动态选择模型。轻量 agent 用便宜模型，重量 agent 用强模型。

**AGE 无预设 agent**：

AGE 不预装任何 agent。nop-chaos-flux 的 22 个 prompt 是从 66+ bug 修复历史中逐步提取的，nop-entropy 的 3 条 ast-grep lint 规则是从实际错误中生长出来的。

AGE 的晋升阶梯（AGE Template AGENTS.md Rule 15）：

```
Level 0: 发现重复问题
  ↓
Level 1: bug note (docs/bugs/) — 记录非显然根因
  ↓
Level 2: lesson (docs/lessons/) — 提取可复用判断规则
  ↓
Level 3: skill/prompt (docs/skills/) — 可复用审计/诊断方法
  ↓
Level 4: audit script (scripts/audit/) — 半自动化扫描
  ↓
Level 5: lint rule / CI guard — 全自动化拦截
```

AGE 的工具从实践中生长，GSD-Core 的 agent 在设计时就确定。

nop-chaos-flux 的 `agent-skills-vs-age-practice.md` 进一步指出两者的组织原则不同。Skill 的组织轴是 `task intent → capability bundle`（"遇到 X 类问题时调用 Y 工具"）。AGE 的组织轴是 `domain concept → semantic commitment → implementation → proof → audit → follow-up obligation`（"这个概念承诺了什么、在哪里实现、怎么证明、谁来审计、后续义务是什么"）。

GSD-Core 的 33 个 agent 是 skill——外部能力包。它们解决"AI 遇到规划任务时怎么做"。AGE 的工具链维护的是仓库内部的语义拓扑——哪些概念被承诺了、哪些被实现了、哪些被验证了。两者不在同一个抽象层。

**对比**：

| 维度 | GSD-Core | AGE |
|------|----------|-----|
| agent/工具来源 | 框架预设 33 个 agent | 从实践中逐步提取 |
| 新项目启动 | 安装即有完整 agent 集 | 只有通用工具，领域工具需积累 |
| agent 职责 | 精确定义（完成标记、权限、上下文预算） | 按需定义（从 bug 中提取） |
| 组织轴 | task intent → capability bundle | domain concept → semantic commitment → proof |
| 模型路由 | 内建（5 profile + 动态升级） | 不关注（由宿主项目的 AI 工具决定） |
| 工具演进 | 框架升级（`gsd update`） | 晋升阶梯（bug → lesson → script → lint） |

### 4. 知识载体：7 个 artifact vs 22 目录的语义拓扑

两者都用文件系统作为持久化载体，但承载的信息类型和组织复杂度完全不同。

**GSD-Core 的核心 artifact**：

| Artifact | 职责 | 生命周期 |
|----------|------|---------|
| REQUIREMENTS.md | 苏格拉底式访谈产出的可证伪需求 | spec-phase 创建，后续 phase 消费 |
| PLAN.md | frontmatter + objective + tasks + success criteria | plan-phase 创建，execute-phase 消费 |
| SUMMARY.md | frontmatter + commits table + deviations + self-check | execute-phase 创建，verify-phase 消费 |
| STATE.md | 项目位置追踪器（相位、plan、进度、决策） | 持续更新 |
| CONTEXT.md | 机器可 grep 的谓词知识库 | 持续更新 |
| ROADMAP.md | 项目路线图 | 持续更新 |
| HANDOFF.json + .continue-here.md | 会话恢复结构化暂停状态 | 跨 session |

核心原则：**artifact that no workflow reads is inert**（不被任何 workflow 消费的 artifact 是死的）。消费机制赋予 artifact 意义。

**AGE Template 的 22 目录语义拓扑**：

AGE Template（`attractor-guided-engineering-template`）定义了 21 个目录，每个目录有明确的角色、命名规则和真值来源约定：

| 类别 | 目录 | 职责 | 命名规则 |
|------|------|------|---------|
| **规范性（stable owner docs）** | `design/` | 稳定应用层特征和业务流——仓库应收敛到哪里 | 稳定文件名，原地更新 |
| | `architecture/` | 稳定技术基线和模块边界 | 稳定文件名 |
| | `references/` | 稳定查阅指南和维护辅助 | 稳定文件名 |
| | `skills/` | 可复用 AI prompt 和审计 playbook | 稳定文件名 |
| | `context/` | 强制 AI 上下文、真值优先级、项目约定 | 稳定文件名 |
| | `process/` | 工作流和操作流程文档 | 稳定文件名 |
| | `requirements/` 中的基线文件 | 产品范围、MVP 等长期需求基线 | 稳定文件名 |
| | `articles/` | 外向方法论和解释性文章（AGE 对外传播载体） | 稳定文件名 |
| | `lessons/` | 从反复出现的问题中提取的持久工程教训 | 稳定文件名 |
| **时效性（time-sensitive records）** | `logs/` | 每日强制轨迹——代码路径、focused proof、owner doc 同步 | 带日期：`YYYY/MM-DD.md` |
| | `plans/` | 闭合契约——goals/non-goals/closure gates | 带日期：`YYYY-MM-DD-topic-plan.md` |
| | `bugs/` | 复杂回归历史和根因笔记 | 编号或带日期 |
| | `discussions/` | 需求澄清——多轮对话记录 | 带日期：`YYYY-MM-DD-topic.md` |
| | `analysis/` | 可选的调研、对比、设计权衡 | 带日期：`YYYY-MM-DD-topic.md` |
| | `audits/` | 审计方法和审计记录 | 带日期：`YYYY-MM-DD-kind-topic.md` |
| | `testing/` | 探索性和手动测试笔记 | 带日期：`YYYY/MM-DD.md` |
| | `retrospectives/` | 交付后差距分析和流程改进 | 带日期：`YYYY-MM-DD-topic.md` |
| **输入与路由** | `input/` | 原始外部输入和源材料 | 按来源 |
| | `backlog/` | 优先级排序的候选工作 | 按优先级 |
| **持久化** | `archive/` | 人工决定归档的不活跃文档 | 保留原名 |
| | `examples/` | 可复制的骨架文件 | 按用途 |

AGE 的 `document-naming-and-timeliness.md` 明确定义了分离规则：**稳定文档回答"当前支持的基线是什么？"用稳定文件名；时效性记录回答"这轮/这天/这次调查发生了什么？"用带日期的文件名。**

AGE 的 `source-of-truth-and-precedence.md` 进一步定义了每个问题的真值来源：需求看 `requirements/`，应用行为看 `design/`，技术结构看 `architecture/`，数据库真值看 model 文件，API 真值看 schema 文件，执行历史看 `logs/`。冲突解决规则明确：代码和文档不一致时，不能静默选择一方。

**关键差异**：

GSD-Core 有 7 个核心 artifact，围绕**单次执行流水线**组织：REQUIREMENTS → PLAN → SUMMARY，每次从需求到交付走一遍。STATE.md 跨相位持续更新，但它的粒度是"当前项目在哪个相位、哪个 plan"。

AGE Template 有 21 个目录，围绕**仓库的完整生命周期**组织。它不是为单次执行设计的——它覆盖了从输入捕获（`input/`）到需求澄清（`discussions/`）到需求综合（`requirements/`）到方向定义（`design/`、`architecture/`）到执行契约（`plans/`）到执行轨迹（`logs/`）到问题记录（`bugs/`）到知识提取（`lessons/`、`skills/`）到审计验证（`audits/`）到交付后反思（`retrospectives/`）的完整链条。每一个环节都有独立的目录、命名规则和真值来源约定。

GSD-Core 的 artifact 是**流水线的中间产物**——用完即弃，下次重新生成。AGE 的目录结构是**仓库的记忆器官**——持续积累，越用越精确。

### 5. 门控系统：4 类 gate vs 独立子代理 closure audit

两者都有验证机制，但验证的执行者和对象不同。

**GSD-Core 的 4 类 gate**：

| 类型 | 作用 | 例子 |
|------|------|------|
| Pre-flight | 验证前置条件，阻止进入 | plan-phase 检查 REQUIREMENTS.md 是否存在 |
| Revision | 评估输出质量，循环修订（最多 3 轮） | gsd-plan-checker 评估 gsd-planner 的输出 |
| Escalation | 无法解决的问题上浮给开发者 | 暂停 workflow，展示选项 |
| Abort | 终止以防损害 | 保留状态，报告原因 |

Revision 循环：checker 评估输出 → 解析问题 → 如果问题数 ≥ 上一轮，停滞检测 → 升级。每轮生成新的 agent。3 轮后仍有问题则展示给用户。

检查点系统：3 类——human-verify（90%）、decision（9%）、human-action（1%）。黄金规则："If Claude can automate it, Claude must automate it."

**AGE 的 closure audit**：

AGE 不用 gate 阻止进入下一个相位。它的验证发生在 plan 闭合时，由**独立 AI 子代理**执行：

1. **Draft review**：独立子代理审计 scope 是否诚实、closure gates 是否真实、是否有隐藏依赖
2. **Closure audit**：另一个独立子代理回到活仓库重新检查——独立验证代码、文档、测试、闭合条件是否真的满足

nop-chaos-flux plan guide（403 行，24 条最小规则）明确要求：Rule 8 "`completed` 必须来自单独的 closure audit"，Rule 11 "关闭计划时，必须区分'contract surface 已出现'和'contract semantics 已落地'"。

**对比**：

| 维度 | GSD-Core | AGE |
|------|----------|-----|
| 验证时机 | 每个相位入口/出口（pre-flight + revision） | plan 闭合时（draft review + closure audit） |
| 验证执行者 | 同一框架内的专用 agent（gsd-plan-checker、gsd-verifier） | 独立子代理（不同 task_id，无上下文共享） |
| 验证对象 | artifact 质量（需求是否可证伪、计划是否完整） | 仓库状态（代码+文档+测试是否与闭合条件一致） |
| 循环机制 | 最多 3 轮修订循环 | 不循环，一次闭合审计通过或打回 |
| 人的角色 | 检查点中 90% 需要人工验证 | 不审查中间 plan，只控制输入和最终产出 |

### 6. 上下文管理：上下文预算 vs 规范/历史分离

两者都在管理信息过载，但策略完全不同。实际上 nop-chaos-flux 已经有一篇专门对比 Context-Engineering 和 AGE 的文章（`docs/articles/context-engineering-age-flux-practice-comparison.md`），核心判断是：前者关注"会话级上下文如何收敛"，后者关注"仓库如何长期产生正确上下文"。GSD-Core 属于前者的工程化实现。

**GSD-Core 的上下文预算**：

GSD-Core 把上下文窗口当作**需要精打细算的有限资源**：

- 永远不读 agent 定义（自动加载）
- 永远不把大文件内联到 subagent prompt 中
- 读深度随上下文窗口大小变化：< 500k token 只读 frontmatter，≥ 500k 才允许读全文
- MCP tax 审计：每个启用的 MCP 服务器都会在每一轮注入工具 schema，需要在相位开始前审计 MCP 开销
- 3 种上下文模式（dev/review/research），控制详细程度

核心策略：**fresh-context subagent**。瘦编排器保持精简，重活交给短命专用 agent，每个 agent 拿到干净的上下文窗口。

**AGE 的规范/历史分离**：

AGE 不管理上下文窗口。它管理的是**文件系统中信息的组织方式**：

- 稳定文件（owner doc）用稳定文件名
- 时间敏感记录（log、bug、plan）带日期
- 规范文档只描述当前状态，不写历史变迁
- 渐进式披露：从 index 到详细内容

AGE 的假设是：如果文件系统组织得当，AI 自然能找到它需要的信息。不需要精打细算上下文窗口——因为每次执行都是独立的，AI 重新读取仓库。

AGE 的 `first-princle-agent-design.md`（AGE 方法论自身文章，非独立第三方视角）进一步指出：上下文的本质是**计算预算**，不是信息容器。每个 token 都在消耗注意力分配，每条信息都在和其他信息竞争 AI 的处理能力。AGE 的做法是分层：核心约束进常驻上下文，其他按需加载——owner doc 引用式、历史日志检索式。需要注意的是，GSD-Core 也采用了类似的策略——指令分布在 92 个 workflow 文件和 33 个 agent 定义中，每个 agent 启动时只加载自己需要的上下文，而非集中在一个大文件中。两者在"按需加载"上方向一致，区别在于 GSD-Core 用 agent 边界隔离上下文，AGE 用文件系统层级和渐进式披露隔离上下文。

**根本差异**：GSD-Core 假设 AI 在长会话中工作，上下文窗口会填满，需要主动管理。AGE 假设 AI 在多次短会话中工作，每次重新读取仓库，不需要管理上下文窗口——但需要确保仓库中的信息组织得当，AI 能快速找到正确的信息。

### 7. 多 session 持久化：HANDOFF.json vs 日志 + plan

两者都解决跨 session 恢复的问题。

**GSD-Core**：

- `HANDOFF.json` + `.continue-here.md`：结构化暂停状态
- `STATE.md`：项目位置追踪器（相位、plan、进度、决策）
- `gsd-tools.cjs`：CLI 工具查询所有状态。确定性状态加载，不依赖 session

**AGE**：

- `logs/`：每日强制记录，精确到代码路径、focused proof、owner doc 同步、验证基线
- `plans/`：每个 plan 是闭合契约，status 字段标记进行中/完成
- `bugs/`：否定空间记录

GSD-Core 的恢复是"从暂停点继续"——STATE.md 记录你在流水线的哪个位置。AGE 的恢复是"从轨迹重建上下文"——日志记录发生了什么，plan 记录未完成的工作，owner doc 记录方向。

### 8. 安全模型

**GSD-Core 的纵深防御（3 层）**：

1. **包合法性门控**：`slopcheck` 工具检查 AI 推荐的包。标记：[SLOP]（完全剥离）、[SUS]（人工验证检查点）、[ASSUMED]（网络发现，总是门控）。slopcheck 不可用时优雅降级：所有包标记 [ASSUMED]，全部加人工检查点
2. **提示注入防御**：输入验证、写入 `.planning/` 的 advisory-only hook、`gsd-read-injection-scanner.js`（读取内容注入扫描）、`gsd-workflow-guard.js`（工作流外编辑检测）、CI 源文件扫描
3. **组织级控制**：固定依赖、npm 完整性验证、secret 扫描、Unicode 同形字检测

**AGE 的安全与质量保障**：

AGE 没有独立的安全模型——安全由宿主项目处理（CI 门禁、lint 规则、pre-commit hook）。nop-entropy 有 pre-commit hook 强制格式化检查，nop-chaos-flux 有 11 个 audit scanner。但 AGE 的审计体系（plan audit、closure audit、document audit）提供了另一种形式的质量保障：通过独立子代理验证计划诚实性、闭合真实性和文档一致性，覆盖了传统安全模型不涉及的语义正确性层面。

### 9. 共同局限与互补性

**GSD-Core 的局限**：

1. **组织单元是单次执行流水线**：5+4 相位从需求到交付走一遍。没有"连续多次流水线执行后仓库是否还在朝正确方向走"的检查
2. **没有方向定义**：REQUIREMENTS.md 定义"这次要做什么"，不是"仓库应收敛到哪里"。STATE.md 追踪项目位置，不追踪仓库方向
3. **没有显式轨迹记录**：`.planning/` 中的 artifacts（SUMMARY.md、VERIFICATION.md 等）和 git 历史提供了隐式轨迹，但缺少 AGE 那样的每日强制日志和显式否定空间记录（否决了什么、为什么这样决定）
4. **没有知识晋升**：工具预设 33 个 agent，通过框架升级演进。没有"同一模式重复出现时如何升级到自动化工具"的机制

**AGE 的局限**：

1. **不管理上下文**：AGE 假设每次执行 AI 重新读取仓库。如果 AI 在长会话中退化，AGE 没有应对机制
2. **不预设 agent**：新项目只有通用工具。没有 GSD-Core 那样的波次并行执行、修订循环、模型路由
3. **没有量化门控**：GSD-Core 的 spec-phase 有模糊度评分（≤ 0.20），verify-phase 有 4 级验证（TRUTH → TESTS PROVE）。AGE 的闭合审计是定性的

**互补性**：

GSD-Core 管单次执行的认知清晰度，AGE 管多次执行的方向正确性。理论上可以组合：

- 用 GSD-Core 管理 spec → plan → execute → verify 的单次流水线
- 用 AGE 的 owner doc 维护方向、closure audit 验证闭合、logs 记录轨迹
- GSD-Core 的 STATE.md 对应 AGE 的 plan status，但 AGE 在此之上多了 owner doc（方向）和 logs（轨迹）

## 结论

GSD-Core 和 AGE 不在同一个层面竞争。

GSD-Core 是**单次执行的上下文工程**：33 个 agent、92 个 workflow、4 类 gate、上下文预算管理、模型动态路由。它让 AI 在一次 spec → ship 的过程中保持认知清醒，不被上下文衰减拖垮。

AGE 是**多次执行的轨迹工程**：owner doc 定义方向、plan 是闭合契约、logs 记录轨迹、bugs 记录否定空间、晋升阶梯将知识逐步自动化。它让 AI 在跨 session 的长期开发中保持方向正确，不被连续扰动带偏。

核心差异在组织单元：GSD-Core 以单次执行流水线为组织单元，AGE 以仓库整体轨迹为组织单元。前者关心"这次执行是否清醒且正确"，后者关心"连续多次执行之后仓库是否还在朝正确方向走"。

AGE 的第一性原理分析（`first-princle-agent-design.md`）提供了一个来自 AGE 框架内部的理论预测：AI 的失败不是随机的——它有**方向性**，倾向于向训练分布的平均值偏移（平庸化、去差异化）。如果这个预测成立，GSD-Core 的 3 轮修订循环可能捕获随机错误，但理论上难以捕获系统性偏移——因为 checker 和 executor 虽然有不同的上下文窗口和系统提示，但都基于同一类模型，可能共享类似的偏差方向。AGE 的独立子代理 closure audit（不同 task_id、无上下文共享）具有更强的统计独立性。但需要指出：这个论断尚未经过独立实证验证，且同样的逻辑也可以反过来质疑 AGE 的独立子代理是否也共享模型偏差。这个问题的答案取决于未来对 AI 系统性偏差传播机制的实证研究。

另一个深层差异是 AGE 的"尺度分离原则"——信息应该存在于与其变化速度匹配的载体中。owner doc 是慢变量（季度级变化），plan 是中变量（周级），log 是快变量（日级）。GSD-Core 的 artifact 没有这个区分——REQUIREMENTS.md、PLAN.md、SUMMARY.md 都是单次执行级，变化速度相同。STATE.md 跨相位持续更新，但它是状态追踪器，不是方向定义器。

## 开放问题

- [ ] GSD-Core 的 5+4 相位工作流能否嵌入 AGE 的 owner doc 约束？即在 spec-phase 中引入吸引子检查
- [ ] AGE 的独立子代理 closure audit 能否复用 GSD-Core 的 gsd-verifier agent 模式？
- [ ] GSD-Core 的上下文预算管理在 AGE 的"每次重新读取仓库"模式下是否仍有价值？
- [ ] 两者的 checkpoint 系统能否统一？GSD-Core 的 human-verify checkpoint 和 AGE 的 closure audit 有没有交叉？
- [ ] GSD-Core 的 3 轮修订循环能否检测方向性偏移？如果 checker 和 executor 共享同一偏差方向，revision 是否只是"错误地确认错误"？
- [ ] AGE 的"尺度分离原则"是否可以形式化为上下文预算策略？即按信息变化速度分配 token 预算

## 参考资料

**GSD-Core:**
- `~/ai/gsd-core/README.md` — 项目介绍
- `~/ai/gsd-core/CONTEXT.md` — 机器可 grep 的谓词知识库（701 行）
- `~/ai/gsd-core/docs/ARCHITECTURE.md` — 完整系统架构（848 行）
- `~/ai/gsd-core/docs/explanation/context-engineering.md` — 为什么 GSD 存在
- `~/ai/gsd-core/docs/explanation/multi-agent-orchestration.md` — Agent 模式（234 行）
- `~/ai/gsd-core/docs/explanation/security-model.md` — 纵深防御（252 行）
- `~/ai/gsd-core/gsd-core/workflows/` — 92 个 workflow 文件（plan-phase、spec-phase、execute-phase 等）
- `~/ai/gsd-core/agents/` — 33 个 agent 定义（gsd-planner、gsd-executor、gsd-verifier 等）
- `~/ai/gsd-core/gsd-core/references/` — gate taxonomy、revision loop、artifact types、context budget、checkpoints、model profiles
- `~/ai/gsd-core/gsd-core/contexts/` — dev、review、research 三种上下文模式
- `~/ai/gsd-core/package.json` — npm 包定义（`@opengsd/gsd-core`）

**AGE 方法论:**
- AGE Template: [https://github.com/entropy-cloud/attractor-guided-engineering-template](https://github.com/entropy-cloud/attractor-guided-engineering-template)
- `~/app/attractor-guided-engineering-template/AGENTS.md` — 第 15 条：工具晋升规则
- `~/app/attractor-guided-engineering-template/docs/` — 21 个目录的语义拓扑
- `~/app/attractor-guided-engineering-template/docs/references/document-naming-and-timeliness.md` — 规范性 vs 时效性文档分离规则（169 行）
- `~/app/attractor-guided-engineering-template/docs/context/source-of-truth-and-precedence.md` — 真值来源和优先级（157 行）
- `~/app/attractor-guided-engineering-template/docs/audits/00-audit-execution-guide.md` — 3 类审计：document audit、plan audit、closure audit（88 行）
- `~/app/attractor-guided-engineering-template/docs/index.md` — 顶层路由表（124 行，51 行路由矩阵）
- `~/app/attractor-guided-engineering-template/docs/articles/attractor-before-harness-ai-large-scale-development-methodology.md` — 吸引子先于线束

**nop-chaos-flux (AGE 实例):**
- GitHub: [https://github.com/entropy-cloud/nop-chaos-flux](https://github.com/entropy-cloud/nop-chaos-flux)
- Gitee: [https://gitee.com/canonical-entropy/nop-chaos-flux](https://gitee.com/canonical-entropy/nop-chaos-flux)
- `docs/skills/` — 22 个 prompt 文件
- `docs/plans/00-plan-authoring-and-execution-guide.md` — 403 行，24 条最小规则
- `scripts/audit/` — 15 个文件（含 11 个扫描器）
- `docs/articles/age-from-state-engineering-to-trajectory-engineering.md` — 从状态工程到轨迹工程
- `docs/articles/context-engineering-age-flux-practice-comparison.md` — Context Engineering vs AGE：两个项目都用 attractor 但层级不同
- `docs/articles/agent-skills-vs-age-practice.md` — AGE 不能降级为 agent skill：skill 是语义 hash map，AGE 维护语义拓扑
- `docs/articles/first-princle-agent-design.md` — agent 设计第一性原理：5 个基本假定

**nop-entropy (AGE 实例):**
- GitHub: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
