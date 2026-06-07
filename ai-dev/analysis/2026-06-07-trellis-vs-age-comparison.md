# Trellis (第三方) vs AGE 工具体系对比：工具从哪里来

> Status: open
> Date: 2026-06-07
> Scope: 第三方 AI 工程框架 Trellis 与 AGE (Attractor-Guided Engineering) 的工具体系对比；两个 AGE 实例 nop-chaos-flux 和 nop-entropy 的工具提取实践
> Conclusion:

## Context

- **Trellis 不是 AGE**。它是一个第三方开源模板 (`@mindfoldhq/trellis`)，目标是"让 AI 编码代理在 14+ 平台上有一致的工程流程"
- **AGE (Attractor-Guided Engineering)** 是从 nop-chaos-flux 的实践过程中涌现的方法论，核心是 `state space -> attractor -> trajectory -> control`
- AGE 的工具不是预设的，而是**从历史轨迹中反向提取**的：反复出现的错误 → bug note → lesson → skill/prompt → audit script → lint rule → CI guard
- 本分析聚焦于两者在**工具来源、工具形态、工具演进机制**上的根本差异

## Analysis

### 1. 定位：Trellis 是什么、AGE 是什么

**Trellis**：

- 一个**产品化的工程框架**，通过 `npm install -g @mindfoldhq/trellis && trellis init` 安装
- 提供 3 相位工作流（Plan → Execute → Finish）、任务管理脚本、spec 系统、子代理分派、多平台 hook
- 工具链在设计时就被确定：`trellis-brainstorm`、`trellis-implement`、`trellis-check`、`trellis-update-spec` 等 12 个 skill
- 用户按框架规定的结构填入自己的 spec

**AGE**：

- 一个**方法论**，不是产品。没有 npm 包，没有安装命令
- AGE Template (`attractor-guided-engineering-template`) 是一个复制即用的文档骨架，定义文档职责和流程，但不预装具体工具
- 工具从实践中生长出来：nop-chaos-flux 的 `scripts/audit/` 下有 15 个扫描器、`docs/skills/` 下有 23 个 prompt——全部是从 bug 历史、审计发现、重复失败中反向提取的
- nop-entropy 的 `ai-dev/tools/` 下有 3 条 ast-grep lint 规则——从 Java 异常处理实践中提取

### 2. 工具来源：预设 vs 反向提取

这是最根本的差异。

#### Trellis：工具在设计时就确定

Trellis 的 12 个 skill 在项目创建时就存在：

| Skill | 来源 |
|-------|------|
| `trellis-brainstorm` | 框架预设：需求发现流程 |
| `trellis-implement` | 框架预设：子代理实现 |
| `trellis-check` | 框架预设：代码质量检查 |
| `trellis-update-spec` | 框架预设：spec 回写 |
| `trellis-break-loop` | 框架预设：调试循环打断 |
| `trellis-before-dev` | 框架预设：开发前上下文加载 |
| `trellis-finish-work` | 框架预设：任务收尾 |
| `trellis-start` | 框架预设：任务启动 |
| `trellis-continue` | 框架预设：任务恢复 |
| `trellis-meta` | 框架预设：框架自身操作 |
| `first-principles-thinking` | 框架预设：思维方法 |
| `python-design` | 框架预设：Python 设计指南 |

这些 skill 是通用的、与具体项目无关的。用户不需要有 bug 历史或审计发现就能获得这些工具。

#### AGE：工具从历史轨迹中反向提取

nop-chaos-flux 的工具生长链路清晰可见：

**链路 1：硬编码类型分发 → audit script**

```
Bug 发现：渲染器中存在硬编码的 type switch (如 if type === 'input' else if type === 'select')
  → 审计发现：这违反了"渲染器应通过注册表分发"的架构契约
  → Plan 430: eliminate-hardcoded-type-dispatch-plan
  → 提取为脚本: scripts/audit/find-hardcoded-type-dispatch.mjs
  → 规则编码在: scripts/audit/rules.mjs (hardcodedTypeDispatchRules)
```

**链路 2：渲染器 marker 缺失 → audit script**

```
Bug 发现：渲染器未按契约输出 marker class
  → 多次出现在 deep audit 维度 09 (渲染器契约合规性)
  → 提取为脚本: scripts/audit/find-missing-renderer-markers.mjs
  → 规则编码在: scripts/audit/rules.mjs (rendererMarkerRules)
```

**链路 3：响应式订阅不精确 → audit script**

```
Bug 发现：组件在 render 中直接读取响应式源导致不必要重渲染
  → 多次出现在 deep audit 维度 05 (响应式订阅精度)
  → Bug #15: render-nodes-setstate-during-render
  → Bug #32: react19-external-store-derived-snapshot-loop
  → 提取为脚本: scripts/audit/find-reactive-render-reads.mjs
```

**链路 4：async 无失败路径 → audit script**

```
Bug 发现：async 函数未处理错误路径
  → 出现在 deep audit 维度 06 (异步模式与取消安全)
  → 提取为脚本: scripts/audit/find-async-without-failure-path.mjs
```

**链路 5：React 19 遗留 API → audit script**

```
Bug 发现：手写 React.memo/useCallback/useMemo 在 React 19 Compiler 下是冗余的
  → 提取为脚本: scripts/audit/find-react19-optimization-candidates.mjs
  → 提取为脚本: scripts/check-react19-legacy-apis.mjs
  → 规则编码在: scripts/audit/react19-rules.mjs
```

nop-entropy 的工具提取同样遵循这个模式：

```
Bug 发现：Java 代码中使用裸 RuntimeException
  → 约定：应使用 NopException 子类 (docs-for-ai/02-core-guides/error-handling.md)
  → 提取为 ast-grep 规则: ai-dev/tools/rules/java-lint-bare-runtimeexception.yml

Bug 发现：catch 块中只用 e.getMessage() 丢失了完整异常信息
  → 提取为 ast-grep 规则: ai-dev/tools/rules/java-lint-getmessage-only.yml

Bug 发现：空 catch 块吞没异常
  → 提取为 ast-grep 规则: ai-dev/tools/rules/java-lint-empty-catch.yml
```

### 3. 工具提取的 AGE 晋升阶梯

AGE Template 的 `AGENTS.md` 第 15 条明确描述了这个晋升机制：

> "When the same error pattern keeps recurring, do not stop at prose-only lessons. First promote it into a reusable audit prompt, checklist, or review playbook when that method is still missing. If the defect pattern still recurs, then evaluate promotion into a heuristic script, static check, lint rule, CI guard, or codemod."

nop-chaos-flux 的完整晋升链路：

```
Level 0: 发现重复问题
  ↓
Level 1: bug note (docs/bugs/XX-xxx-fix.md) — 记录非显然根因
  ↓
Level 2: lesson (docs/lessons/XX-xxx.md) — 提取可复用的判断规则
  ↓
Level 3: skill/prompt (docs/skills/xxx-prompt.md) — 可复用的审计/诊断方法
  ↓
Level 4: audit script (scripts/audit/find-xxx.mjs) — 半自动化扫描
  ↓
Level 5: lint rule / CI guard — 全自动化拦截
```

实际例子——nop-chaos-flux 的 `docs/skills/` 目录内容：

| Skill/Prompt | 晋升来源 |
|-------------|---------|
| `bug-diagnosis-prompt.md` (242 行) | 从 62+ bug 修复历史中提取的诊断方法论 |
| `open-ended-adversarial-review-prompt.md` (116 行) | 从多轮对抗性审查中提取的开放式发现方法 |
| `deep-audit-prompts.md` (2141 行，20 维度) | 从多轮深度审计中提取的结构化审查框架 |
| `implementation-contract-review-prompt.md` | 从 plan 闭合审计中提取的契约审查方法 |
| `exploratory-contract-testing-prompt.md` | 从测试实践中提取的契约发现方法 |
| `exploratory-e2e-testing-prompt.md` | 从 E2E 失败中提取的探索性测试方法 |
| `react19-best-practices-review.md` | 从 React 19 迁移中提取的最佳实践 |
| `ux-design-pattern-audit-prompt.md` | 从 UX 审计中提取的设计模式检查 |
| `code-quality-audit-prompt.md` | 从代码质量审计中提取的检查方法 |
| `architecture-deepening-review-prompt.md` | 从架构深化审查中提取的方法 |
| `deprecated-feature-cleanup.md` | 从废弃功能清理中提取的方法 |
| `next-gen-lowcode-attractor-discovery-prompt.md` | 从吸引子演化中提取的发现方法 |

**Trellis 没有这个晋升机制**。它的 `trellis-update-spec` 是把学到的东西写回 `.trellis/spec/`，但 spec 是编码约定，不是从失败中提取的结构性防护工具。Trellis 不会从同一个 bug 模式中逐步生成 audit script 或 lint rule。

### 4. 工具形态对比

#### Trellis 的工具形态

| 工具类型 | 示例 | 特点 |
|---------|------|------|
| 流程脚本 | `task.py`, `get_context.py`, `add_session.py` | 管理任务生命周期、上下文注入、日志 |
| 技能 prompt | 12 个 `SKILL.md` 文件 | 通用方法论（brainstorm、check、implement 等） |
| Spec 注入 | `implement.jsonl` / `check.jsonl` | 在子代理分派时注入上下文 |
| Hook/Plugin | 各平台 hook 脚本 | 多平台适配 |
| 工作流状态机 | `workflow.md` (690 行) | 用标签块驱动每轮行为 breadcrumb |

特征：**流程导向**。工具管的是"AI 怎么执行任务"。

#### nop-chaos-flux (AGE) 的工具形态

| 工具类型 | 示例 | 特点 |
|---------|------|------|
| 审计扫描器 | `scripts/audit/find-*.mjs` (15 个) | 从失败模式中提取的半自动检查 |
| Prompt 模板 | `docs/skills/*.md` (23 个) | 从实践中提取的可复用审计/诊断方法 |
| 构造性脚本 | `scripts/check-*.mjs` (10+ 个) | 特定约束的自动化验证 |
| Lint 规则 | `scripts/audit/rules.mjs` (625 行) | 编码了项目的结构不变量 |
| Bug 历史 | `docs/bugs/` (62 个) | 可追溯的工具来源证据 |
| Lessons | `docs/lessons/` | 判断规则的文档化 |

特征：**内容导向**。工具管的是"哪些结构不变量必须被守护"。

#### nop-entropy (AGE) 的工具形态

| 工具类型 | 示例 | 特点 |
|---------|------|------|
| ast-grep lint | `ai-dev/tools/rules/java-lint-*.yml` (3 个) | 从异常处理实践中提取 |
| 文档检查 | `check-doc-links.mjs`, `check-doc-index.mjs` | 从文档维护实践中提取 |
| 导入顺序 | `check-import-order.mjs` | 从编码约定中提取 |
| 文件大小 | `check-oversized-files.mjs` | 从代码质量实践中提取 |
| i18n 检查 | `check-i18n-en-xml.mjs` | 从国际化实践中提取 |
| 计划检查 | `check-plan-checklist.mjs` | 从计划闭合实践中提取 |

### 5. 文档组织：领域适配 vs 框架适配

这是另一个根本差异。AGE 的文档结构按领域需要组织，Trellis 的文档结构按框架需要组织。

#### Trellis：框架预设固定结构

```
.trellis/                  # Trellis 规定的顶层目录
├── spec/                  # 框架规定：按 package/layer 组织的编码规范
│   ├── cli/backend/       # 框架规定的分类方式
│   ├── cli/unit-test/
│   └── guides/
├── tasks/                 # 框架规定：按日期命名的任务目录
│   └── MM-DD-name/        # 框架规定的命名格式
│       ├── prd.md         # 框架规定的文件
│       ├── implement.jsonl # 框架规定的上下文注入文件
│       ├── check.jsonl    # 框架规定的上下文注入文件
│       └── task.json      # 框架规定的状态文件
└── workspace/             # 框架规定：按开发者名隔离的日志
    └── <name>/            # 框架规定的命名方式
        └── journal-N.md   # 框架规定的轮转格式
```

这个结构服务于 Trellis 自身的运转需要：

- `spec/` 的 package/layer 分层服务于 `get_context.py --mode packages` 的发现机制
- `tasks/` 的日期前缀 + 固定文件集服务于 `task.py` 的生命周期管理
- `implement.jsonl` / `check.jsonl` 服务于子代理的上下文注入管线
- `workspace/` 的开发者隔离服务于多用户场景

**不管你的项目是什么领域，结构都一样。**

#### AGE：按领域需要组织，只遵守两条约束

AGE 文档组织的约束只有两条：

1. **渐进式披露**：从最小的入口（index / start-here）逐步展开到详细内容，读者只读当前任务需要的部分
2. **规范文档与时效性历史文档分离**：稳定文件用稳定文件名，时间敏感记录带日期

在这两条约束内，每个项目按自己的领域需要组织文档结构。

**nop-chaos-flux（前端低代码框架）** 的文档结构完全由前端框架领域驱动：

```
docs/
├── architecture/          # 框架领域需要：58 个架构文档，4 层优先级
│   ├── flux-design-principles.md      # 纲领层：设计意图
│   ├── frontend-programming-model.md  # 规范层：原语定义、边界
│   ├── flux-core.md                   # 基线层：代码级架构
│   ├── renderer-runtime.md            # 子系统：渲染器契约
│   ├── flow-designer/                 # 子领域：设计器架构
│   ├── report-designer/               # 子领域：报表设计器架构
│   └── word-editor/                   # 子领域：Word 编辑器架构
├── components/            # 框架领域需要：100 个渲染器组件设计文档
│   ├── table/design.md
│   ├── form/design.md
│   ├── dialog/design.md
│   └── ...
├── references/            # 框架领域需要：快速参考、术语表、实现指南
│   ├── quick-reference.md             # 压缩最常用类型到单文件
│   ├── terminology.md                 # 统一术语
│   └── reopened-design-decisions.md   # 历史决策追踪
├── skills/                # 23 个从实践中提取的 prompt
├── bugs/                  # 62 个 bug 历史
├── plans/                 # 400+ 个执行计划
└── ... (其他按需目录)
```

为什么有 `components/` 目录？因为 nop-chaos-flux 有 100+ 个渲染器组件，每个组件有自己的设计契约。这是前端框架的领域需要。

为什么有 `references/` 目录？因为前端框架有大量类型签名、hook API、渲染器模式，需要一个压缩参考文件让 AI 不必读 10+ 源码文件。这是框架使用的领域需要。

为什么 `architecture/` 有 4 层优先级？因为前端框架的结构不变量有明确的依赖层次（设计原则 → 编程模型 → 代码基线 → 子系统），不这样组织就会出现优先级冲突。这是架构领域的需要。

**nop-entropy（后端全栈框架）** 的文档结构完全不同，由后端框架领域驱动：

```
docs-for-ai/               # 规范性使用文档（面向使用者）
├── 00-start-here/         # 渐进披露：项目状态 → 默认规则 → 真值优先级
├── 01-repo-map/           # 渐进披露：仓库结构、模块关系、文件位置
├── 02-core-guides/        # 核心使用指南（服务层、API、ORM、IoC 等）
├── 03-modules/            # 各模块使用说明
├── 03-runbooks/           # 按任务的操作手册
└── 04-reference/          # 源码锚点引用

ai-dev/                    # 开发过程记忆（面向开发者）
├── analysis/              # 调研分析
├── bugs/                  # Bug 历史
├── design/                # 设计文档
├── logs/                  # 每日日志
├── plans/                 # 执行计划
└── tools/                 # 开发工具
```

为什么有编号前缀（`00-`、`01-`、`02-`）？因为后端框架的使用文档有明确的阅读顺序：先看项目状态（00），再看仓库结构（01），再看核心指南（02），再看模块说明（03），最后查引用（04）。这是渐进式披露在后端框架领域的具体形式。

为什么分 `docs-for-ai/` 和 `ai-dev/` 两条轨道？因为后端框架的使用者（基于 Nop 构建应用的开发者/AI）和开发者（修改 Nop 平台本身的 AI）是完全不同的受众，需要完全不同的吸引子。nop-chaos-flux 没有这种分轨，因为它的使用者和开发者基本是同一批人。

**AGE Template（应用层项目）** 又是另一种结构：

```
docs/
├── context/               # 应用项目需要：强制 AI 上下文
├── backlog/               # 应用项目需要：优先级队列和 AI 自治标签
├── input/                 # 应用项目需要：原始 PM 输入
├── requirements/          # 应用项目需要：实现就绪需求
├── design/                # 应用项目需要：应用层行为 owner doc
├── architecture/          # 应用项目需要：技术基线
└── ... (其他按需目录)
```

为什么有 `input/` 和 `requirements/` 分离？因为应用项目经常从原始 PM 笔记开始，需要先保留原始输入（`input/`），再综合成实现就绪的需求（`requirements/`）。这是应用开发的领域需要。nop-chaos-flux 不需要这种分离，因为它的需求来自架构决策而不是 PM。

为什么有 `backlog/`？因为应用项目需要优先级队列和 AI 自治标签来决定"下一步做什么"。框架项目不需要 backlog，因为工作由架构偏离和技术债务驱动。

#### 核心差异总结

| 维度 | Trellis | AGE |
|------|---------|-----|
| 文档结构决定者 | 框架（`.trellis/` 三件套） | 领域（每个项目不同） |
| 结构约束来源 | 框架的脚本需要（jsonl、task.py、journal 轮转） | 领域的需要（组件契约、模块关系、阅读顺序） |
| 共性约束 | 无（完全由框架规定） | 渐进式披露 + 规范/历史分离 |
| 跨项目一致性 | 高（所有项目结构相同） | 低（每个领域结构不同） |
| 框架适配成本 | 无（框架就是结构） | 需要理解领域才能组织好文档 |
| 吸引子承载能力 | 受限于框架结构 | 不受限，按领域需要承载 |

AGE 不预设文档结构。它只要求文档能发挥吸引子的作用（渐进式披露让 AI 能快速找到当前任务需要的上下文）并且把规范文档（稳定文件名）和时效性历史文档（带日期）分离。在这个约束内，nop-chaos-flux 选择了前端框架领域需要的结构，nop-entropy 选择了后端框架领域需要的结构，AGE Template 选择了应用层项目需要的结构。

### 6. 规范与历史分离：AGE 的硬约束，Trellis 不区分

AGE 要求规范文档（owner doc / 吸引子载体）只描述"系统应该收敛到哪里"，把时效性历史信息（演化叙事、决策过程、bug 复盘）放到带日期的独立文件中。Trellis 的 spec 没有这个区分——规范和历史混在同一文件里。

#### AGE 的分离规则

nop-chaos-flux 的 plan guide Rule 14：

> `docs/architecture/` 下的文档**只描述当前最新设计状态**：最终方案、选择原因、拒绝的替代方案及原因。不写历史变迁、不写"Proposed vs Current"对比、不写演进叙事。如果一个 design doc 包含 "Proposed Design" 或 "Current vs Proposed" 章节，说明它还停留在 draft 状态，实施完成后必须重写为最终设计文档。

AGE 的文档分类：

| 文档类型 | 职责 | 命名方式 | 例子 |
|---------|------|---------|------|
| Owner doc（吸引子载体） | 只描述当前应该怎样 | 稳定文件名，无日期 | `architecture/flux-design-principles.md` |
| 时效性记录 | 记录发生了什么、为什么这样决定 | 带日期或编号 | `logs/2026/05-24.md`、`bugs/15-render-nodes-setstate-during-render.md` |
| 计划 | 局部轨迹的闭合契约 | 编号 + 主题 | `plans/430-eliminate-hardcoded-type-dispatch-plan.md` |

规范文档里允许出现的与"历史"相关的内容只有三种：**选择原因**（为什么选 A 不选 B，这是当前状态的一部分）、**拒绝的替代方案及原因**（否定空间，防止反复重提）、**例外记录**（显式标注"有意保留的遗留行为"）。演化叙事（"我们曾经用 X，后来迁移到 Y，过程如下"）必须留在 logs / bugs / plans 中。

#### Trellis 的混合现实

Trellis 的 `.trellis/spec/` 没有规范/历史分离的要求。`trellis-update-spec` skill 提供的模板类型包括 "Design Decision"（含 Options Considered、决策背景）、"Common Mistake"（含 Symptom/Cause/Fix/Prevention）、"Case Study"——这些模板天然鼓励把历史信息写入规范文档。

实际例子——`quality-guidelines.md`（982 行）中的历史性内容：

**Case Study 带日期**：
> "Case Study (2026-04-22): `current_phase` / `next_action` drift across 4 writers + type declaration"

这段是完整的历史复盘叙事，包含"第一次审计漏掉了什么"、"后来发现了四种漂移模式"、"三个教训"和"最终的整合结果"。它是 bug #87 的完整演化历史，不是当前规范。

**版本回退叙事**：
> "Cautionary tale — 0.6.0-beta.3 → 0.6.0-beta.4 emergency revert"
> "Time to detect: ~4 hours after publish."

这是 0.6.0-beta.3 发布后紧急回退的事件经过，包含时间线、失败级联、修复方案。这是时间敏感的历史事件记录。

**Issue 编号引用**：
> "Case Study (2026-04-30): issue #204 `--yes` + bootstrap recovery"
> "The first commit (`346003d`) added... Caught by `trellis-check` sub-agent... Fixed in `589f753`..."

包含 commit hash、issue 编号、发现过程和修复过程。这是完整的 bug 修复历史。

`workflow-state-contract.md`（299 行）中同样混入了历史：
> "Two production bugs (Phase 1.3 jsonl curation skip, Phase 3.4 commit skip) hit exactly this failure mode."

`error-handling.md`（380 行）中的 Pattern 5（"Probe-Based Error Distinction"）包含：
> "**Real example**: `fetchTemplateIndex` returning `[]` for all errors caused a registry marketplace to be misclassified as a direct-download source when the network had a transient failure."

这些都是具体历史事件的记录，不是"当前应该怎样"。

#### 后果：规范文档持续膨胀

当规范和历史混在一起，规范文件会随时间持续膨胀：

| Trellis spec 文件 | 行数 | 包含的历史性内容 |
|------------------|------|----------------|
| `quality-guidelines.md` | 982 | 2 个 Case Study、1 个 Cautionary Tale、多个带 commit hash 的 bug 复盘 |
| `error-handling.md` | 380 | 5 个 Common Mistake（每个含 Symptom/Cause/Fix/Prevention 叙事）、Real example 引用 |
| `workflow-state-contract.md` | 299 | Production bugs 历史引用 |

AGE 的 owner doc 不会这样膨胀，因为历史被分流到了 `logs/`、`bugs/`、`plans/` 中。owner doc 只在"当前规范本身需要更新"时才被修改，且修改是替换性的（重写为最终设计），不是追加性的（在末尾叠加历史）。

#### 知识定位差异

这个差异反映了两者的知识定位根本不同：

| 维度 | Trellis spec | AGE owner doc |
|------|-------------|---------------|
| 回答的问题 | "我们学到了什么" | "系统应该收敛到哪里" |
| 内容增长方式 | 追加（学一条记一条） | 替换（只有当前状态） |
| 历史信息处理 | 混在规范中作为案例 | 分流到 bugs/logs/plans |
| 文件膨胀倾向 | 高（每学一课追加一节） | 低（只在吸引子变化时替换） |
| AI 读取成本 | 持续增长（spec 越来越长） | 稳定（owner doc 长度与吸引子精度正相关，与项目年龄无关） |
| 否定空间 | 以 Common Mistake / Case Study 形式存在 | 以"拒绝的替代方案及原因"形式存在于 owner doc 的固定段落中 |

AGE 之所以能严格分离，是因为它有专门的时效性文档类别（`logs/`、`bugs/`、`plans/`、`lessons/`）来承接历史信息。Trellis 没有这些类别——`tasks/` 是任务目录（完成后归档），`workspace/journal` 是会话记忆（2000 行轮转），都不是持久化的历史记录载体。学到的内容无处可去，只能沉淀到 spec 里。

### 7. 知识持久化方向：Spec 回写 vs 轨迹提取

**Trellis** 的知识流是**向下沉淀**的：

```
任务执行 → spec 回写 (trellis-update-spec)
               ↓
         .trellis/spec/ 里的编码约定
               ↓
         下次任务通过 jsonl 注入给子代理
```

知识以"编码约定"的形式沉淀。学到的内容是"怎么写代码才对"。

**AGE** 的知识流是**反向提取**的：

```
bug/审计/计划闭合 → 问题模式识别
                       ↓
                 bug note (docs/bugs/)
                       ↓
                 lesson (docs/lessons/)
                       ↓
                 skill/prompt (docs/skills/)
                       ↓
                 audit script (scripts/audit/)
                       ↓
                 lint rule / CI guard
                       ↓
                 同时反向更新 owner doc (docs/architecture/)
```

知识以"防止再次偏离的结构性防护"的形式提取。学到的内容是"哪些不变量被违反了、如何防止再犯"。

同时，AGE 的 bug 历史本身就是吸引子精度的证据。nop-chaos-flux 的 62 个 bug 记录不是废弃物——它们是吸引子方程的校准数据。每个 bug 都暴露了"系统在哪个维度上偏离了吸引子"，而提取出的工具就是防止同类偏离的控制机制。

### 8. Plans vs Todo：闭合契约，且为人逐步退出做准备

AGE 的 plan 与 todo 有本质区别。Plan 的目标不仅是"把这件事做完"，而是为**AI 全自动执行**建立可验证的闭合条件。

#### Plan 的完整生命周期

AGE 的 plan 有三个关键门控，全部由**AI 独立子代理**完成：

1. **Draft review**：计划写完后，独立子代理审计 scope 是否诚实、closure gates 是否真实、是否有隐藏依赖、是否依赖未解决的需求缺口
2. **执行**：AI 围绕计划执行，每一步记录 focused proof、owner doc 同步、验证结果
3. **Closure audit**：执行完成后，**另一个独立子代理**回到活仓库重新检查——不是检查"implementer 声称做了什么"，而是独立验证代码、文档、测试、闭合条件是否真的满足

nop-chaos-flux 的 plan guide（403 行，24 条最小规则）明确要求：

- Rule 8："`completed` 必须来自单独的 closure audit，不要在完成最后一个编码 slice 的同时顺手宣布 plan 关闭"
- Rule 12："标记 `completed` 前，必须完成一次由独立审阅者或独立子 agent 执行的 closure audit"
- Rule 15："每个 execution item 都必须能被归类为 `Fix`、`Decision`、`Proof`、或 `Follow-up`"
- Rule 11："关闭计划时，必须区分'contract surface 已出现'和'contract semantics 已落地'；前者不能替代后者"

Plan 不是 todo list。它是局部轨迹的闭合契约：定义这轮扩张从哪里开始（Current Baseline）、做什么不做什么（Goals/Non-Goals）、每个执行项的性质（Fix/Decision/Proof）、闭合条件（Closure Gates）、以及需要独立验证的证据。

#### 逐步向全自动推进

AGE 的整体目标是**逐步减少人类介入节点**。当前实践中人仍然控制少数关键节点（定义吸引子、裁决冲突、校准方向），但 plan 的 draft review 和 closure audit 已经完全由 AI 独立子代理完成——人不阅读也不检查中间过程。

未来的方向是：人只控制**输入**（需求、吸引子定义）和**最终产出**（代码、运行程序），对中间的 plan、audit、implementation 人不逐条审查，而是抽样监控某些关键节点不要偏移。plan 系统的存在不是为了给人看的——是为了让 AI 能**自我验证其产出是否真正收敛到吸引子**。

`first-principles-of-agent-engineering.md` 明确定义了 Agent 的本质："Agent 是一个在目标约束下，跨时间维持、验证、修正、复用可行动认知结构的系统。"四个动词（维持、验证、修正、复用）中，验证和修正都预设了"知道正确的方向在哪"。Plan 是验证和修正的局部载体——它定义"这轮扩张怎样才算真正完成"，然后由独立子代理验证。

#### 与 Trellis 的对比

Trellis 的等价物是 `prd.md` + `implement.jsonl`：

| 维度 | Trellis PRD | AGE Plan |
|------|------------|----------|
| 性质 | 需求描述 | 局部轨迹闭合契约 |
| Current Baseline | 无 | 强制：执行前核对活仓库 |
| Non-Goals | 无 | 强制：防止 scope drift |
| Closure Gates | 无 | 强制：闭合条件 |
| 执行项分类 | 无（todo list） | Fix / Decision / Proof / Follow-up |
| Draft review | 无 | 独立子代理审计 |
| Closure audit | 无 | 独立子代理回到活仓库验证 |
| 完成判定 | implementer 自行宣布 | 独立 closure audit 证据 |
| 人的角色 | 人审查 PRD 和产出 | 人不审查中间 plan，只控制输入和最终产出 |
| 自动化目标 | 人始终在环 | 逐步向全自动推进 |

#### Logs：自动记录的轨迹

AGE 的 AGENTS.md 强制要求："After completing any significant code change, you MUST update the daily dev log"。这不是可选的日志——这是轨迹的自动记录。

nop-chaos-flux 的 logs/2026/ 目录有 72 个每日日志文件（从 03-20 到 06-02），每个日志记录：

- 具体关闭了哪些 plan 的哪个 workstream
- 修改了哪些代码路径（精确到文件:行号）
- 哪些 focused proof 通过了（精确到测试文件名）
- 哪些 owner doc 同步更新了
- 全仓验证状态（`pnpm typecheck/build/lint/test` 全绿）
- 独立闭合审计的 subagent task ID

例如 `logs/2026/05-24.md`（83 行）记录了一天之内关闭的 20+ 个 workstream item，每个都有代码路径、测试文件、owner doc 同步和验证命令。这不是日记——这是轨迹的精确记录。

Trellis 的等价物是 `workspace/journal-N.md`。Journal 记录的是"这次会话做了什么"，是个人工作记忆，最大 2000 行后轮转。它不记录精确的代码路径、focused proof、owner doc 同步和验证基线。

**核心差异**：

| 维度 | Trellis Journal | AGE Log |
|------|----------------|---------|
| 性质 | 个人会话记忆 | 项目轨迹记录 |
| 内容 | 会话做了什么 | 精确的代码路径、测试证明、owner doc 同步、验证基线 |
| 强制度 | `/trellis:finish-work` 触发 | AGENTS.md 强制：每次显著代码变更必须更新 |
| 与 plan 的关系 | 无关 | 是 plan 执行的轨迹证据 |
| 可追溯性 | 按会话 | 按日期，可追溯到具体 plan workstream |
| 轮转 | 2000 行后自动轮转 | append-only，不轮转 |

AGE 的 logs 体现的是动力系统思维：轨迹信息（为什么走到这里）和状态信息（现在在哪里）同样重要。Logs 不是"写完了记一下"——它们是系统轨迹的可读投影，让未来的会话能恢复"到昨天为止发生了什么"。

### 9. 文档+代码整体审计：AGE 独有的能力

AGE 提供了大量专门审计**文档+代码整体一致性**的提示词。Trellis 没有等价物。

#### Deep Audit：20 维度、2141 行、文档+代码整体审计

nop-chaos-flux 的 `deep-audit-prompts.md` 是一个 2141 行的审计框架，覆盖 20 个维度，分为 6 大类：

| 类别 | 维度 | 审计对象 |
|------|------|---------|
| A. 架构与模块边界 | 01-03 | 依赖图、模块职责、API 表面积 |
| B. 运行时与状态 | 04-08 | 状态所有权、响应式精度、异步安全、生命周期、验证一致性 |
| C. 渲染器与 UI | 09-12 | 渲染器契约、样式合规、组件使用、字段建模 |
| D. 工程质量 | 13-15 | 类型安全、测试覆盖、安全性能 |
| E. 文档与一致性 | 16-18 | **文档-代码一致性**、**命名一致性**、**跨包模式一致性** |
| F. 运行时鲁棒性 | 19-20 | 错误传播保真度、可访问性 |

注意类别 E：维度 16-18 **专门审计文档和代码之间的一致性**。这不是代码质量检查——这是检查 owner doc 是否与代码实现一致、命名是否与术语表一致、相同概念在不同包中是否实现一致。

审计执行模型：

1. **阶段一：迭代深挖**——每个维度最多 10 轮，每轮派发独立子代理。第 1 轮初审，后续轮次读取前序发现继续深挖未覆盖的盲区。
2. **阶段二：复核**——深挖结束后，派发**独立的复核子代理**回到活代码重新核对每条发现，输出"保留/降级/驳回"。

这个审计模型的独特之处：**审计对象是文档+代码的整体**，不是单独的代码质量。维度 16（文档-代码一致性）检查 owner doc 是否漂移、文档是否过时、plan 状态是否失真——这是 AGE 对"吸引子与实际投影是否一致"的系统性验证。

#### Open-ended Adversarial Review：开放式对抗性审查

nop-chaos-flux 的 `open-ended-adversarial-review-prompt.md` 是一种完全不同的审计方式：

- **不预设检查维度**——先读代码，让代码告诉你哪里有问题
- **鼓励跳跃式探索**——在读 A 模块时发现 B 模块可能有问题就立即去验证
- **鼓励自我否定**——深入后发现不是问题就直接放弃
- **质量优先于数量**——一个跨领域的意外发现比十个机械风格问题有价值
- **提供 12 种可选视角**作为起步提示（新人开发者、恶意输入者、10x 规模运维者、时序攻击者等），但不强制使用
- **要求落盘**——每轮发现必须保存到 `docs/analysis/` 下的独立文件，下一轮只作为去重背景
- **循环直到无新发现**——不设上限，直到某一轮确实没有新的高价值问题

这两种审计方式互补：Deep Audit 保证维度覆盖，Open-ended Review 发现维度之外的意外问题。

#### Trellis 的等价物

Trellis 的 `trellis-check` 是一个代码质量检查 skill：

```markdown
## Step 3: Run Project Checks
Run the project's lint, type-check, and test commands.

## Step 4: Review Against Checklist
### Code Quality
- Linter passes?
- Type checker passes?
- Tests pass?
- No debug logging left in?
```

这是纯代码质量检查。没有文档-代码一致性检查，没有 owner doc 漂移检测，没有命名一致性审计，没有跨包模式一致性验证。

Trellis 的 `trellis-update-spec` 是在任务完成后评估是否需要更新 spec，但这不是审计——这是学习回写。它不挑战"当前的 spec 是否与代码一致"，而是判断"这次任务是否产生了值得记录的新知识"。

**核心差异**：AGE 的审计提示词是在检查"吸引子（owner doc）与实际投影（代码）是否一致"。Trellis 的检查是在验证"代码是否符合 spec"。前者是**方向性审计**（系统是否还在朝正确方向收敛），后者是**执行性检查**（代码是否按规范写）。

### 10. 两者的工具对比总结

| 维度 | Trellis | AGE (nop-chaos-flux / nop-entropy) |
|------|---------|-------------------------------------|
| 工具来源 | 框架预设，安装即有 | 从历史轨迹反向提取 |
| 工具数量增长方式 | 框架版本升级 | 每次重复失败都可能催生新工具 |
| 工具内容 | 通用方法论（brainstorm、check 等） | 项目特定（find-hardcoded-type-dispatch、java-lint-bare-runtimeexception 等） |
| 工具与项目知识的关系 | Spec 是用户填入的约定 | 工具编码了项目特有的结构不变量 |
| 工具演进机制 | `trellis update` (框架升级) | 晋升阶梯 (bug → lesson → prompt → script → lint) |
| 工具精度 | 通用精度 | 针对具体失败模式的高精度 |
| 多平台适配 | 核心能力 (14+ 平台) | 不关注，依赖宿主项目的 CI |
| 流程自动化 | 核心能力 (状态机、hook、子代理) | 有限 (计划闭合检查脚本、pre-commit hook) |
| 新项目启动成本 | `trellis init` 即可获得完整工具集 | 需要先有实践历史才能开始提取工具 |
| 工具维护成本 | 低（框架统一维护） | 高（每个工具需要随项目演进更新白名单和规则） |

### 11. AGE Template 的 `tools/` 与 Trellis 的本质差异

AGE Template 自带一个 `tools/` 目录，但它与 Trellis 的工具集有本质不同：

- AGE Template 的 `tools/` 只包含**通用基础工具**：`check-doc-links`、`check-oversized-files`、`check-docs-garbled`、`audit/` 示例
- 这些工具是**占位和示例**，不是项目特有的结构守护者
- README 明确说："Files kept in this directory should satisfy: generic enough to be useful across many copied projects OR representative enough to serve as a reusable example pattern"
- 真正有价值的工具（如 nop-chaos-flux 的 15 个 audit 扫描器、nop-entropy 的 3 条 Java lint 规则）是从**复制后的项目实践**中生长出来的

相比之下，Trellis 的工具集是**完整且不可变的**——用户不需要在实践中提取新工具，框架已经提供了所需的全部。

### 12. 为什么这个差异重要

AGE 的核心理念是"吸引子先于线束存在"。这意味着：

1. **先有吸引子**（owner doc 定义的结构不变量）
2. **再有线束**（从失败中提取的防护工具）
3. **线束的精度取决于吸引子的精度**

nop-chaos-flux 之所以有 15 个 audit 扫描器、23 个 skill prompt、625 行的 rules.mjs，是因为它的吸引子精度极高（7 个原语封闭集、编译优先管线、依赖方向约束等）。吸引子越精确，能被识别的偏离就越多，能被提取的工具就越丰富。

Trellis 不依赖吸引子。它依赖的是**预设的工程流程**。只要 AI 代理按 3 相位循环执行、按 spec 写代码、按 check 验证，就认为产出是可靠的。这种可靠性不来自"系统收敛到正确结构"，而来自"每个任务都通过了标准化检查"。

### 13. 累积性设计：分门别类、不断增长、不放到单一文件

AGE 的 bugs / testing / lessons / skills / plans 在设计层面就考虑了**不断累积**。每个条目是独立文件，分门别类地组织，不是堆在单一文件中。

nop-chaos-flux 的实际规模：

| 目录 | 条目数 | 组织方式 |
|------|--------|---------|
| `docs/bugs/` | 62 个 bug note | `NN-xxx-fix.md` 编号 + 主题命名 |
| `docs/plans/` | 44+ 个活跃计划（归档更多） | `NNN-xxx-plan.md` 编号 + 主题命名 |
| `docs/skills/` | 23 个 prompt | 按功能命名 |
| `docs/components/` | 100 个组件设计文档 | 按组件名分目录 |
| `docs/architecture/` | 58 个架构文档 | 按主题命名，4 层优先级 |
| `scripts/audit/` | 15 个扫描器 | `find-xxx.mjs` 按检查目标命名 |
| `docs/logs/` | 72 个每日日志 | `YYYY/MM-DD.md` 按日期 |

如果这些内容放到单一文件中（比如一个"项目知识.md"或一个"编码规范.md"），文件会膨胀到不可维护，AI 无法有效检索，且条目之间的独立性丧失——一个条目的修改会影响其他条目的上下文。

Trellis 的做法正好相反：所有编码知识都放在 `.trellis/spec/` 下，按 package/layer 组织为若干 `index.md` 和引用文件。spec 是一个**压缩的知识库**，目标是注入给子代理时占用尽可能少的上下文窗口。它不设计为不断累积——一个 spec 条目被学到了就写进去，后续 spec 的增长是靠内容更新而不是条目追加。

AGE 的累积性设计与动力系统理论的关系：bugs 是轨迹偏离的记录，lessons 是偏离模式的结构化，skills 是防护方法的复用化，plans 是局部轨迹的闭合契约，logs 是轨迹本身的逐日投影。**每一种文档对应动力系统中的一个不同对象**，所以它们天然需要独立存在、分门别类、持续累积。如果混在同一个文件里，不同类型的信息会相互干扰，丧失各自的信息职责。

这种设计也使得 AGE 的文档天然支持**渐进式披露**：AI 不需要读全部 62 个 bug，只需要通过 `docs/skills/bug-diagnosis-prompt.md` 获取诊断方法论（它是从 62 个 bug 中提取的精华），或者通过 `docs/index.md` 路由到具体的 bug note。累积的是原始证据，使用时只取需要的。

### 14. 理论与实践的共演：先有思想，落地细节在实践中完善

前面说"理论指导不等于理论先行"需要更精确的表述：

**动力系统的思想（state space / attractor / trajectory / control）和吸引子的概念是最早确立的**。这个概念框架从一开始就决定了 AGE 的基本方向：仓库是系统真相，变迁是动力系统演化，需要定义吸引子、记录轨迹、建立控制机制。

**但吸引子具体如何落地——owner doc 用什么结构、precedence 怎么表达、闭合审计怎么做、文档怎么分门别类——这些细节是在实践中不断完善的。**

nop-chaos-flux 的演化历史是这个过程的具体体现：

- **吸引子的载体**从早期的扁平架构文档，演化为 4 层优先级体系（governing → normative → platform extension → focused），这个分层是在多次审计中发现优先级冲突后才固化的
- **闭合审计**从早期的人工检查，演化为必须由独立子代理回到活仓库重新验证，这个规则是在 Plan 143 的闭合假设被多次推翻后才确立的
- **promotion ladder**（bug → lesson → prompt → script → lint）不是一开始就设计好的，而是在实践中发现"prose-only lessons 无法阻止同一错误再次出现"后逐步形成的
- **deep-audit 的 20 个维度**不是预设的检查清单，而是从多轮深度审计实践中归纳出来的，每一轮审计都可能发现需要新增维度
- **open-ended adversarial review** 的出现是因为 deep-audit 的固定维度有时会错过维度之外的问题
- **`docs/components/` 目录**（100 个组件设计文档）不是预先规划的，而是在实践中发现渲染器契约需要逐组件记录后才产生的

理论提供了**方向**和**概念框架**：吸引子必须存在、轨迹必须记录、控制必须与吸引子对齐。但具体**用什么文档结构承载吸引子、用什么机制检查偏离、用什么格式记录轨迹**——这些是在动力系统思想的指导下，通过实践不断校准和细化的。

这与科学理论的发展模式一致：牛顿力学的基本定律（F=ma）是理论框架，但如何用它设计一座桥——截面形状、材料选择、荷载模型——需要在工程实践中不断完善。理论告诉你桥必须满足力学定律，但不告诉你具体该用 I-beam 还是 box girder。

AGE 的情况也是如此：动力系统理论告诉你仓库必须围绕吸引子收敛，但不告诉你 nop-chaos-flux 的吸引子应该是 7 个原语的封闭集还是 58 个架构文档的 4 层优先级——后者需要在前端框架领域中去发现。

## Conclusion

Trellis 和 AGE 的差异不在工具数量或文档结构细节，而在七个根本分歧：

1. **工具从哪里来**：Trellis 的 12 个 skill 在框架设计时预设，管的是"AI 怎么执行任务"；AGE 的工具（nop-chaos-flux 的 15 个 audit 扫描器、23 个 prompt；nop-entropy 的 3 条 lint 规则）从历史轨迹中反向提取，管的是"哪些结构不变量被违反了"。AGE 有明确的晋升阶梯：bug → lesson → prompt → script → lint。

2. **文档结构服从谁**：Trellis 的 `.trellis/` 三件套（spec/tasks/workspace）服务于框架脚本的运转需要，所有项目结构相同；AGE 只要求渐进式披露和规范/历史分离两条约束，在此范围内每个项目按领域需要组织。

3. **规范与历史是否分离**：AGE 的 owner doc 只描述"当前应该怎样"，历史信息（bug 复盘、演化叙事、版本回退事件）分流到 `bugs/`/`logs/`/`plans/` 中。Trellis 的 spec 没有这个区分——Case Study、Cautionary Tale、Common Mistake 直接写进规范文档，导致 spec 持续膨胀（`quality-guidelines.md` 982 行）。

4. **Plan 不是 Todo**：AGE 的 plan 是局部轨迹的闭合契约，有 draft review（独立子代理审计）和 closure audit（独立子代理回到活仓库验证）。Plan 的目标是让 AI 能自我验证产出是否收敛到吸引子，逐步向全自动推进——人不逐条审查中间过程，只控制输入和最终产出。Trellis 的 PRD 是需求描述，没有闭合条件、没有 non-goals、没有独立审计。

5. **轨迹记录 vs 任务归档**：AGE 的 logs 是强制的轨迹记录（精确到代码路径、focused proof、owner doc 同步、验证基线），与 plans（闭合契约）是两种不同的文档。Trellis 的 journal 是个人会话记忆。

6. **文档+代码整体审计**：AGE 提供了 deep-audit（20 维度、2141 行、含专门的文档-代码一致性审计）和 open-ended adversarial review（开放式对抗性审查），专门检查"吸引子与实际投影是否一致"。Trellis 的 `trellis-check` 只做代码质量检查。

7. **理论生成 vs 经验堆叠**：AGE 以动力系统思想（`state space → attractor → trajectory → control`）为最早确立的概念框架，具体落地细节（owner doc 结构、precedence 表达、闭合审计流程）在实践中不断完善。所有实践元素可从理论自然推演。Trellis 来自实用观察，没有统一理论框架，扩展是经验性的。

## Open Questions

- [ ] AGE 的 deep-audit（20 维度迭代深挖+独立复核）执行成本极高。这个成本在中小型项目中如何降低？
- [ ] Trellis 的 `trellis-update-spec` 是否可以扩展为 AGE 式的晋升阶梯？还是说 spec 回写和工具提取在本质上是不同类型的知识操作？
- [ ] AGE 的强制 log 记录在什么场景下可以简化？是否有"最低限度的轨迹记录"标准？
- [ ] deep-audit 的文档-代码一致性审计维度（16-18）是否可以在框架无关的情况下通用化？

## References

**Trellis:**
- `~/ai/trellis/.trellis/workflow.md` — 工作流状态机（690 行）
- `~/ai/trellis/.trellis/spec/guides/` — 跨包思维指南
- `~/ai/trellis/.agents/skills/` — 12 个预设 skill
- `~/ai/trellis/.trellis/config.yaml` — 配置文件

**AGE 方法论:**
- `~/app/attractor-guided-engineering-template/AGENTS.md` — 第 15 条：工具晋升规则
- `~/app/attractor-guided-engineering-template/docs/articles/attractor-before-harness-ai-large-scale-development-methodology.md` — 吸引子先于线束
- `~/app/attractor-guided-engineering-template/tools/README.md` — 工具选择规则

**nop-chaos-flux (AGE 实例):**
- `docs/skills/README.md` — 23 个 skill/prompt 索引
- `docs/skills/deep-audit-prompts.md` — 2141 行，20 维度深度审计框架
- `docs/skills/open-ended-adversarial-review-prompt.md` — 开放式对抗性审查
- `docs/skills/bug-diagnosis-prompt.md` — 从 62+ bug 中提取的诊断方法论
- `scripts/audit/` — 15 个从失败模式中提取的扫描器
- `scripts/audit/rules.mjs` — 625 行规则编码
- `docs/bugs/` — 62 个 bug 修复历史（工具来源证据）
- `docs/lessons/01-*.md` — 从失败中提取的判断规则

**nop-entropy (AGE 实例):**
- `ai-dev/tools/rules/` — 3 条 ast-grep Java lint 规则
- `ai-dev/tools/README.md` — 工具索引和添加规则
- `docs-for-ai/INDEX.md` — 规范性使用文档索引
