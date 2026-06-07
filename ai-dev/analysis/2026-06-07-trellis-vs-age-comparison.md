# 任务级工具 vs 轨迹级方法论：Trellis、OpenSpec 与 AGE 的根本分歧

## 简介

- **Trellis 不是 AGE**。它是一个第三方开源模板 (`@mindfoldhq/trellis`)，目标是"让 AI 编码代理在 14+ 平台上有一致的工程流程"。
- **OpenSpec 不是 AGE**。它是一个第三方开源框架 (`@fission-ai/openspec`)，目标是"spec-driven development"，通过 delta specs 管理变更
- **AGE (Attractor-Guided Engineering)** 是从 nop-chaos-flux 的实践中长出来的方法论，核心思想是 `状态空间 -> 吸引子 -> 轨迹 -> 控制`
  - 微信公众号系列文章：
    - 《Attractor Before Harness: AI 大规模开发的方法论》 https://mp.weixin.qq.com/s/TwMkUDLNo2-bIrXrfvPqIw
    - 《从 Spec-Driven Development 到 Attractor-Guided Engineering》 https://mp.weixin.qq.com/s/j4dZm1bAK61qB8i5RzHRWA
    - 《为什么 Attractor Guided Engineering 不能被降级为 AI Agent Skill》 https://mp.weixin.qq.com/s/CqdHL9kIeSmQnLt5FBbDHA
    - 《控制层和方向层的分野：OpenProse、NLAHs 与 AGE》 https://mp.weixin.qq.com/s/scTDkvSOxww3YI4YNs09mA
- AGE 并不预设很多固定工具，强调工具应从反复出错的地方**逐步生长出来**：反复出现的错误 → bug note → lesson → skill/prompt → audit script → lint rule → CI guard
- **核心发现**：Trellis 和 OpenSpec 的共同局限是都以**单次变更**为组织单元，缺乏仓库整体真相和向 AI 全自动演进的支撑能力。本分析从 AGE 的理论框架出发，对比三者的根本差异

## 分析

### 1. 定位：三者是什么

**Trellis**：

- 一个**产品化的工程框架**，通过 `npm install -g @mindfoldhq/trellis && trellis init` 安装
- 提供 3 相位工作流（Plan → Execute → Finish）、任务管理脚本、spec 系统、子代理分派、多平台 hook
- 工具链在设计时就被确定：`trellis-brainstorm`、`trellis-check`、`trellis-update-spec`、`trellis-break-loop` 等 12 个 skill
- 5 条核心原则：Plan before code、Specs injected not remembered、Persist everything、Incremental development、Capture learnings

**OpenSpec**：

- 一个**产品化的 spec-driven 框架**，通过 `npm install -g @fission-ai/openspec && openspec init` 安装
- 核心模型是 spec + delta + archive 循环：`specs/`（source of truth）← merge ← `changes/`（delta specs）
- 每个 change 包含 proposal → specs → design → tasks 四种 artifact，按 schema 依赖图生成
- Delta specs 用 ADDED/MODIFIED/REMOVED 描述增量修改，archive 时合并回 main specs
- 4 条哲学：fluid not rigid、iterative not waterfall、easy not complex、brownfield-first
- 支持 25+ AI 工具的 slash command 集成

**AGE**：

- 一个**方法论**，不是产品。没有 npm 包，没有安装命令
- AGE Template 是一个复制即用的文档骨架（https://github.com/entropy-cloud/attractor-guided-engineering-template） ，定义文档职责和流程，附带通用工具（`check-doc-links`、`check-oversized-files` 等）。领域特定工具需要从实践中提取
- AGE 作者开源了多个大型范例项目，展示 AGE 在不同领域的完整实践：
  - nop-chaos-flux（https://github.com/entropy-cloud/nop-chaos-flux） ：前端低代码框架，15 个审计文件（含 11 个扫描器）、22 个 prompt、66 个 bug note、72 个日志
  - nop-entropy（https://github.com/entropy-cloud/nop-entropy） ：后端全栈框架，`docs-for-ai/` 规范文档 + `ai-dev/` 开发记忆分轨
  - nop-chaos-next（https://gitee.com/canonical-entropy/nop-chaos-next） ：应用层项目，`design/`、`input/`、`logs/`、`skills/` 轻量实践
- 理论框架：`state space → attractor → trajectory → control`，所有实践元素可从此推演

### 2. 工具来源：预设 vs 逐步生长出来

Trellis 内置了 12 个 skill ，AGE 的领域工具需要自己积累，或者从其他项目借鉴。

#### Trellis：工具在设计时就确定

Trellis 的 12 个 skill 在项目创建时就存在：

| Skill | 来源 | 职责 |
|-------|------|------|
| `trellis-brainstorm` | 框架预设 | 需求发现流程 |
| `trellis-check` | 框架预设 | 代码质量检查 |
| `trellis-update-spec` | 框架预设 | spec 回写 |
| `trellis-break-loop` | 框架预设 | 深度 bug 分析（5 维度框架） |
| `trellis-before-dev` | 框架预设 | 开发前上下文加载 |
| `trellis-finish-work` | 框架预设 | 任务收尾 |
| `trellis-start` | 框架预设 | 任务启动 |
| `trellis-continue` | 框架预设 | 任务恢复 |
| `trellis-meta` | 框架预设 | 框架自身操作 |
| `first-principles-thinking` | 框架预设 | 思维方法 |
| `python-design` | 框架预设 | Python 设计指南 |
| `contribute` | 框架预设 | 贡献指南（文档、marketplace） |

这些 skill 是通用的、与具体项目无关的。用户不需要有 bug 历史或审计发现就能获得这些工具。其中 `trellis-break-loop` 和 `trellis-update-spec` 构成了一套从失败中提取知识的机制（详见第 3 节）。

#### AGE：工具从实践中逐步提炼

nop-chaos-flux 的工具生长链路清晰可见：

**链路 1：硬编码类型分发 → audit script**

```text
Bug 发现：渲染器中存在硬编码的 type switch
  → 审计发现：违反"渲染器应通过注册表分发"的架构契约
  → Plan 430: eliminate-hardcoded-type-dispatch-plan
  → 提取为脚本: scripts/audit/find-hardcoded-type-dispatch.mjs
  → 规则编码在: scripts/audit/rules.mjs
```

**链路 2：渲染器 marker 缺失 → audit script**

```text
Bug 发现：渲染器未按契约输出 marker class
  → 多次出现在 deep audit 维度 09
  → 提取为脚本: scripts/audit/find-missing-renderer-markers.mjs
```

**链路 3-5**：响应式订阅不精确、async 无失败路径、React 19 遗留 API → 各自对应的 audit script

nop-entropy 同样遵循这个模式：

```text
Bug 发现：Java 代码中使用裸 RuntimeException
  → 约定：应使用 NopException 子类
  → 提取为 ast-grep 规则: ai-dev/tools/rules/java-lint-bare-runtimeexception.yml
```

#### 开箱即用 vs 逐步积累

Trellis 新项目立即拥有完整的 12 个 skill + 工作流状态机 + 跨平台 hook。AGE 新项目从 Template 起步只有通用工具，但可以参考 nop-chaos-flux、nop-entropy、nop-chaos-next 等开源范例来快速建立领域特定工具。Trellis 适合"今天就开始"，AGE 适合"长期演化"。

### 3. 从失败中提取知识：两种不同深度的机制

Trellis 和 AGE 都从失败中提取知识，但产物形式和自动化程度不同。

#### Trellis：提取到 prose spec

`trellis-break-loop` 提供了结构化的 5 维度 bug 分析框架：

1. **Root Cause Category**：5 类（Missing Spec / Cross-Layer Contract / Change Propagation Failure / Test Coverage Gap / Implicit Assumption）
2. **Why Fixes Failed**：4 种失败模式（Surface Fix / Incomplete Scope / Tool Limitation / Mental Model）
3. **Prevention Mechanisms**：6 种预防机制（Documentation / Architecture / Compile-time / Runtime / Test Coverage / Code Review）
4. **Systematic Expansion**：相似问题 / 设计缺陷 / 流程缺陷
5. **Knowledge Capture**：**强制要求更新 spec 文件**（"The analysis is worthless if it stays in chat. The value is in the updated specs."）

分析后的知识通过 `trellis-update-spec` 写入 `.trellis/spec/`，以 Design Decision、Common Mistake、Case Study 等形式沉淀为 prose。

#### AGE：提取到逐步自动化的工具

AGE 的晋升阶梯（AGE Template AGENTS.md Rule 15）：

```
Level 0: 发现重复问题
  ↓
Level 1: bug note (docs/bugs/) — 记录非显然根因
  ↓
Level 2: lesson (docs/lessons/) — 提取可复用的判断规则
  ↓
Level 3: skill/prompt (docs/skills/) — 可复用的审计/诊断方法
  ↓
Level 4: audit script (scripts/audit/) — 半自动化扫描
  ↓
Level 5: lint rule / CI guard — 全自动化拦截
```

nop-chaos-flux 的 `docs/skills/` 目录（22 个 prompt 文件）：

| Skill/Prompt | 晋升来源 |
|-------------|---------|
| `bug-diagnosis-prompt.md` (242 行) | 从 66+ bug 修复历史中提取的诊断方法论 |
| `open-ended-adversarial-review-prompt.md` (116 行) | 从多轮对抗性审查中提取的开放式发现方法 |
| `deep-audit-prompts.md` (2141 行，20 维度) | 从多轮深度审计中提取的结构化审查框架 |
| `implementation-contract-review-prompt.md` | 从 plan 闭合审计中提取的契约审查方法 |

#### 核心差异

两者都从失败中提取知识，但产物的形式和自动化程度不同：

| 维度 | Trellis | AGE |
|------|---------|-----|
| 分析框架 | 5 维度（break-loop） | 晋升阶梯（5 层级） |
| 知识产物 | prose spec（编码约定、案例） | prompt → script → lint rule（逐步自动化） |
| 沉淀位置 | `.trellis/spec/` | `docs/skills/` → `scripts/audit/` → CI |
| 自动化程度 | prose only（人读） | prose → 半自动扫描 → 全自动拦截 |
| 递进机制 | 无（break-loop 产出直接进 spec） | 有（同一模式重复出现时晋升到更高层级） |

AGE 之所以能递进，是因为它有专门的时效性文档类别（`bugs/`、`lessons/`、`skills/`）来跟踪模式是否重复出现。Trellis 的知识一次性沉淀到 spec，同一个错再犯时没有升级路径。

### 4. 文档组织：领域适配 vs 框架适配 + 规范/历史分离

这是两个紧密相关的维度：文档结构由谁决定，以及规范与历史信息是否分离。

#### Trellis：框架预设固定结构，规范与历史混合

```
.trellis/                  # Trellis 规定的顶层目录
├── spec/                  # 按 package/layer 组织的编码规范 + 学习积累
│   ├── cli/backend/
│   ├── cli/unit-test/
│   └── guides/
├── tasks/                 # 按日期命名的任务目录（完成后归档）
│   └── MM-DD-name/
│       ├── prd.md / implement.jsonl / check.jsonl / task.json
└── workspace/             # 按开发者名隔离的会话记忆（2000 行轮转）
```

这个结构服务于 Trellis 自身的运转需要：`spec/` 的分层服务于 `get_context.py` 的发现机制，`tasks/` 的格式服务于 `task.py` 的生命周期管理，`workspace/` 的开发者隔离服务于多用户场景。5 条核心原则（Plan before code、Persist everything 等）是跨项目的共性约束。

**规范与历史没有分离**。`trellis-update-spec` 的模板类型（Design Decision、Common Mistake、Case Study、Gotcha）天然鼓励把历史信息写入 spec。`trellis-break-loop` 在 5 维度分析后也要求"update spec/guides"。Trellis 没有独立的 `bugs/`、`logs/`、`lessons/` 目录——`tasks/` 完成后归档，`workspace/journal` 2000 行轮转。学到的内容无处可去，只能沉淀到 spec 里。

实际例子——`quality-guidelines.md`（982 行）中的历史性内容：

- "Case Study (2026-04-22): `current_phase` / `next_action` drift across 4 writers + type declaration" — 完整的 bug 演化历史，包含"第一次审计漏掉了什么"、"四种漂移模式"、"最终整合结果"
- "Cautionary tale — 0.6.0-beta.3 → 0.6.0-beta.4 emergency revert" — 版本回退事件经过
- "Case Study (2026-04-30): issue #204 `--yes` + bootstrap recovery" — 包含 commit hash、发现过程、修复过程

`workflow-state-contract.md`（299 行）中："Two production bugs (Phase 1.3 jsonl curation skip, Phase 3.4 commit skip) hit exactly this failure mode."

Case Study 混在规范中提供了即时的因果上下文（"为什么这条规则存在"），AGE 则需要跨文件引用才能获得同样的上下文。代价是 spec 文件膨胀，且 AI 读取时无法区分"当前规范"和"历史教训"。

#### AGE：按领域需要组织，规范与历史严格分离

AGE 文档组织只有两条约束：

1. **渐进式披露**：从最小的入口（index / start-here）逐步展开到详细内容
2. **规范与时效性历史分离**：稳定文件用稳定文件名，时间敏感记录带日期

在这两条约束内，每个项目按自己的领域需要组织文档结构。

**nop-chaos-flux（前端低代码框架）**：`docs/architecture/` 按 4 层优先级组织（纲领→规范→基线→子系统），`docs/components/` 有 100 个组件设计文档，`docs/references/` 压缩最常用类型到单文件。这是前端框架的领域需要。

**nop-entropy（后端全栈框架）**：`docs-for-ai/` 按编号前缀组织阅读顺序（00→04），与 `ai-dev/`（开发过程记忆）分轨。因为使用者和开发者是完全不同的受众。

**AGE Template（应用层项目）**：有 `input/`（原始 PM 输入）和 `requirements/`（实现就绪需求）分离、`backlog/`（优先级队列）。这是应用开发的领域需要。

AGE 的规范/历史分离由 nop-chaos-flux plan guide Rule 14 明确规定：

> `docs/architecture/` 下的文档**只描述当前最新设计状态**。不写历史变迁、不写"Proposed vs Current"对比、不写演进叙事。

规范文档里允许出现的历史相关内容只有三种：选择原因（为什么选 A 不选 B）、拒绝的替代方案及原因（否定空间）、例外记录（"有意保留的遗留行为"）。演化叙事必须留在 `logs/`、`bugs/`、`plans/` 中。

#### 核心差异

| 维度 | Trellis | AGE |
|------|---------|-----|
| 文档结构决定者 | 框架（`.trellis/` 三件套） | 领域（每个项目不同） |
| 共性约束 | 5 条核心原则 + 框架结构规定 | 渐进式披露 + 规范/历史分离 |
| 规范中的历史 | 允许且鼓励（Case Study、Cautionary Tale） | 严格排除（只保留选择原因和否定空间） |
| 历史承接载体 | 无专门载体（journal 轮转、tasks 归档） | `bugs/`、`logs/`、`plans/`、`lessons/` |
| spec 膨胀风险 | 存在（知识只进不出） | 低（owner doc 只保留当前状态） |
| 规范可读性 | 高（因果上下文就地可得） | 需跨文件引用获取上下文 |

### 5. Plans：闭合契约，不是任务清单

AGE 的 plan 不是"把这件事做完"，而是为**AI 全自动执行**建立可验证的闭合条件。

#### Plan 的完整生命周期

AGE 的 plan 有三个关键门控，全部由**AI 独立子代理**完成：

1. **Draft review**：独立子代理审计 scope 是否诚实、closure gates 是否真实、是否有隐藏依赖
2. **执行**：AI 围绕计划执行，每一步记录 focused proof、owner doc 同步、验证结果
3. **Closure audit**：**另一个独立子代理**回到活仓库重新检查——独立验证代码、文档、测试、闭合条件是否真的满足

nop-chaos-flux 的 plan guide（403 行，24 条最小规则）明确要求：

- Rule 8："`completed` 必须来自单独的 closure audit"
- Rule 12："标记 `completed` 前，必须完成一次由独立审阅者或独立子 agent 执行的 closure audit"
- Rule 11："关闭计划时，必须区分'contract surface 已出现'和'contract semantics 已落地'"

#### 逐步向全自动推进

AGE 的整体目标是**逐步减少人类介入节点**。当前实践中人仍然控制少数关键节点（定义吸引子、裁决冲突、校准方向），但 plan 的 draft review 和 closure audit 已经完全由 AI 独立子代理完成。未来方向：人只控制**输入**和**最终产出**，对中间过程抽样监控。

`first-principles-of-agent-engineering.md` 定义了 Agent 的本质："Agent 是一个在目标约束下，跨时间维持、验证、修正、复用可行动认知结构的系统。"Plan 是验证和修正的局部载体——它定义"这轮扩张怎样才算真正完成"，然后由独立子代理验证。

#### 与 Trellis 的对比

Trellis 的计划/执行体系有两层：

**第一层：PRD（设计文档 + 需求文档）**。Trellis 的 `prd.md` 比纯需求描述更接近设计文档——包含 Goal、Assumptions、Requirements、Acceptance Criteria、Out of Scope、Decisions (ADR-lite)、Technical Notes。通过 Phase 1 的协作式 brainstorming 与用户共同创建。

**第二层：通用执行流程**。`workflow.md` 定义了每个任务通用的 3 相位执行列表：Phase 1（1.0-1.5：创建任务→探索需求→研究→配置上下文→激活）→ Phase 2（2.1-2.3：实现→质量检查→回滚）→ Phase 3（3.1-3.5：质量验证→调试复盘→spec 更新→提交→收尾）。Phase 2 的实现和检查由子代理自主完成，Phase 3.4 的提交需要用户 one-shot 确认。

AGE 的 plan 与 Trellis 的 PRD + 执行流程的对比：

| 维度 | Trellis PRD + 3 相位流程 | AGE Plan |
|------|--------------------------|----------|
| 计划内容 | PRD：设计+需求+技术方案+验收标准 | Goals/Non-Goals/Closure Gates/执行项 |
| 执行列表 | 通用（workflow.md 3 相位，所有任务共用） | 定制（每个 plan 有自己的 Fix/Decision/Proof 执行项） |
| Current Baseline | 无 | 强制：执行前核对活仓库 |
| Non-Goals | 有（Out of Scope 段落） | 强制：防止 scope drift |
| Closure Gates | 有（Acceptance Criteria，实现者自行勾选） | 强制：闭合条件，独立子代理验证 |
| Draft review | 无 | 独立子代理审计 |
| Closure audit | 无（实现者按 Acceptance Criteria 自查） | 独立子代理回到活仓库验证 |
| 完成判定 | Phase 3.4 提交前自查 | 独立 closure audit 证据 |
| 人的角色 | 参与 PRD 协作和提交确认 | 不审查中间 plan，只控制输入和最终产出 |
| 自动化目标 | 人始终在环 | 逐步向全自动推进 |

#### Logs：自动记录的轨迹

AGE 的 AGENTS.md 强制要求："After completing any significant code change, you MUST update the daily dev log"。

nop-chaos-flux 的 `docs/logs/2026/` 目录有 72 个每日日志文件，每个记录：具体关闭了哪些 plan 的哪个 workstream、修改了哪些代码路径（精确到文件:行号）、哪些 focused proof 通过了、哪些 owner doc 同步更新了、全仓验证状态、独立闭合审计的 subagent task ID。

Trellis 的等价物是 `workspace/journal-N.md`——个人会话记忆，2000 行后轮转。不记录精确的代码路径和验证基线，与 plan 无关联。

### 6. 审计体系：共享的执行性检查 + AGE 独有的方向性审计

AGE 和 Trellis 都有代码质量门禁。区别在 AGE 多了一层方向性审计。

#### 共同基础：lint/test/typecheck 作为提交门禁

Trellis 的 Phase 2.2（Quality check）和 Phase 3.1（Quality verification）要求运行 `pnpm lint && pnpm typecheck && pnpm test`，`trellis-check` skill 封装了这个流程。

AGE 同样有强制门禁。nop-chaos-flux 的 plan guide 要求 `pnpm typecheck/build/lint/test` 全绿才能关闭 plan；nop-entropy 的 AGENTS.md 要求 `./mvnw test -pl <affected-module> -am` passes，且 Rule 13 明确规定"已经进入 lint、静态检查脚本、或 CI fail-fast 的固定规则，都是不可降级的硬约束"。nop-entropy 的 pre-commit hook 也强制代码通过格式化检查。两者在这个层面等价。

#### Trellis 独有：5 维度事后分析

`trellis-break-loop` 提供结构化的 bug 复盘（Root Cause Category / Why Fixes Failed / Prevention / Systematic Expansion / Knowledge Capture），分析后更新 spec。

#### AGE 独有：文档+代码整体一致性审计

**Deep Audit**（`deep-audit-prompts.md`，2141 行，20 维度）覆盖 6 大类：

| 类别 | 维度 | 审计对象 |
|------|------|---------|
| A. 架构与模块边界 | 01-03 | 依赖图、模块职责、API 表面积 |
| B. 运行时与状态 | 04-08 | 状态所有权、响应式精度、异步安全、生命周期、验证一致性 |
| C. 渲染器与 UI | 09-12 | 渲染器契约、样式合规、组件使用、字段建模 |
| D. 工程质量 | 13-15 | 类型安全、测试覆盖、安全性能 |
| E. 文档与一致性 | 16-18 | **文档-代码一致性**、**命名一致性**、**跨包模式一致性** |
| F. 运行时鲁棒性 | 19-20 | 错误传播保真度、可访问性 |

维度 16-18 **专门审计文档和代码之间的一致性**。审计执行模型：阶段一迭代深挖（每维度最多 10 轮，每轮独立子代理），阶段二独立复核（独立子代理回到活代码重新核对每条发现）。

**Open-ended Adversarial Review**（`open-ended-adversarial-review-prompt.md`）不预设检查维度，鼓励跳跃式探索和自我否定，循环直到无新发现。

AGE 多了方向性审计：owner doc 是否与代码一致、命名是否与术语表一致、相同概念在不同包中是否实现一致。deep-audit 执行成本极高（多轮独立子代理），这是需要权衡的代价。

### 7. 两者的工具对比总结

| 维度 | Trellis | AGE (nop-chaos-flux / nop-entropy) |
|------|---------|-------------------------------------|
| 工具来源 | 框架预设，安装即有 | 从历史轨迹反向提取 |
| 新项目启动成本 | `trellis init` 即有完整工具集 | AGE Template 只有通用工具，但可参考开源范例（nop-chaos-flux、nop-entropy）快速建立领域特定工具 |
| 工具演进机制 | `trellis update` (框架升级) | 晋升阶梯 (bug → lesson → prompt → script → lint) |
| 从失败中提取知识 | 有（break-loop 5 维度分析 → spec prose） | 有（晋升阶梯 → 逐步自动化工具） |
| 知识产物自动化 | prose only | prose → 半自动扫描 → 全自动拦截 |
| 多平台适配 | 核心能力 (14+ 平台) | 不关注，依赖宿主项目的 CI |
| 流程自动化 | 核心能力 (状态机、hook、子代理) | 有限 (计划闭合检查脚本、pre-commit hook) |
| 工具维护成本 | 低（框架统一维护） | 高（每个工具需要随项目演进更新白名单和规则） |
| 规范/历史分离 | 不分离（历史沉淀到 spec） | 严格分离（owner doc 只含当前状态） |
| Plan 系统 | PRD 需求描述 | 闭合契约（draft review + closure audit） |
| 代码质量门禁 | 有（Phase 2.2/3.1: lint+typecheck+test） | 有（plan guide Rule 13: 硬门禁不可降级） |
| 事后分析 | 有（break-loop 5 维度 bug 复盘） | 有（bug note → lesson → skill 晋升） |
| 方向性审计 | 无 | 有（deep-audit 20 维度含文档-代码一致性） |

### 8. 共同局限：Trellis 和 OpenSpec 都是任务级工具

Trellis 和 OpenSpec 有三个结构性的缺失。

#### 缺失一：没有仓库整体真相

Trellis 和 OpenSpec 的核心组织单元都是**单次变更**（一个 task / 一个 change），不是**仓库整体**。

- Trellis：每个 task 有自己的 `prd.md`，`spec/` 是编码约定的堆叠。没有"系统当前整体是什么、应该收敛到哪里"的维护点
- OpenSpec：`specs/` 号称 source of truth，但它是需求的**累加**（每次 archive 合并 delta，只增不减），不是方向定义。累加出来的 specs 是需求清单，不是架构吸引子

AGE 的 owner doc 维护的是整体方向：哪些模块必须分离、依赖只能朝哪个方向流、什么概念不允许混在一起。

AGE 的 `first-principles-of-agent-engineering.md` 区分了两种事实源：四维真相（事实、因果来源、置信状态、否定空间）回答"现在是什么和曾经怎样"，吸引子回答"应该收敛到什么"。Trellis 和 OpenSpec 都没有区分这两种事实源——它们只有"当前规范"（specs/spec），没有"方向定义"（attractor）。

#### 缺失二：单次变更之间没有轨迹

- Trellis：task 完成后归档，`journal` 2000 行轮转。下一个 task 看不到上一个 task 做了什么、为什么这样决定、否决了什么
- OpenSpec：change 归档后保留在 `archive/` 里，但只是历史文件夹。没有结构化的轨迹记录（代码路径、验证基线、owner doc 同步状态）。下一个 change 不从上一个 change 的执行过程中学习

AGE 的 `logs/` 是强制轨迹：精确到代码路径、focused proof、owner doc 同步、验证基线。`bugs/` 记录否定空间（哪些路被排除、哪些假设被证伪）。未来的 session 可以从轨迹中恢复"到昨天为止发生了什么"。

这个差异来自组织单元的不同：任务级工具关心"这次变更是否正确"，仓库级方法论关心"连续多次变更之后仓库是否还在朝正确方向走"。

#### 缺失三：没有向 AI 全自动演进的支撑点

- Trellis：人参与 PRD 协作和提交确认，3 相位流程为人设计。没有独立 closure audit，人最终检查
- OpenSpec：`/opsx:propose` 和 `/opsx:verify` 都假设人在环。verify 是可选的，没有独立验证。tasks.md 是给人看的 checklist

AGE 的 plan 有 draft review 和 closure audit 由**独立 AI 子代理**完成，人逐步退出。plan 是闭合契约，不是给人看的任务清单。吸引子定义了"收敛到哪里"，审计检查"是否在收敛"，两者都不需要人介入。

AGE 的 `age-from-state-engineering-to-trajectory-engineering.md` 说得直接：传统软件工程的基本结构是"状态检查 + 人类隐性方向感"。AI 深度参与后，人脑不再持有完整蓝图，"方向感"必须外化到仓库结构中。Trellis 和 OpenSpec 都仍然依赖人的隐性方向感——它们让单次 AI-人协作更可靠，但不替代人对方向的判断。

#### Trellis 和 OpenSpec 的相对进步

两者各自比对方有进步，但都在任务级框架内打转。

**Trellis 相对 OpenSpec 的进步：**

1. **从失败到 spec 的知识回写**：`trellis-break-loop`（5 维度 bug 分析）→ `trellis-update-spec`（强制更新 spec 文件）。OpenSpec 的 `/opsx:archive` 只合并 delta specs（需求变更），不回写故障教训——学到的教训留在 archive 文件夹里，不回流到 specs 中
2. **完整的执行管线**：子代理分派（`trellis-implement`/`trellis-check`）、jsonl 上下文注入、rollback。OpenSpec 只有 artifact 依赖图（proposal → specs → design → tasks），没有执行流程管理——`/opsx:apply` 依赖 AI 自行找到并读取 specs
3. **结构化的 bug 分析**：`trellis-break-loop` 的 5 维度分析框架（Root Cause Category / Why Fixes Failed / Prevention / Systematic Expansion / Knowledge Capture）。OpenSpec 没有等价物

**OpenSpec 相对 Trellis 的进步：**

1. **Spec 强制校验**：`openspec validate` 用 Zod schema 解析 Markdown，强制要求 Purpose 段、Requirements 段、每个 Requirement 至少一个 Scenario、ADDED/MODIFIED 必须含 SHALL/MUST、跨 section 不冲突。Trellis 的 spec 文件是完全自由的 Markdown，没有结构校验
2. **Delta 机制**：ADDED/MODIFIED/REMOVED 增量描述 + archive 时合并回 main specs。比 Trellis 的"把所有知识塞进 spec 文件"更结构化
3. **Spec/Change 分离**：`specs/`（source of truth）和 `changes/`（增量修改）分开。比 Trellis 不区分规范和提议前进了一步

OpenSpec 的 spec 格式有一个根本局限：Given/When/Then Scenario 是给机器验证的格式（可自动转化为测试用例），不是给人读的方向定义。AGE 的 owner doc 是给人读的架构方向（"模块必须分离"、"依赖只能单向流"），两者的受众不同。

#### 三方对照表

| AGE 维度 | AGE | OpenSpec | Trellis |
|---------|-----|----------|---------|
| 组织单元 | 仓库整体（attractor + trajectory） | 单次变更（change） | 单次变更（task） |
| 规范性质 | 方向定义（"应收敛到哪里"） | 行为描述（"当前行为"） | 编码约定（"怎么写代码"） |
| 变更之间 | 轨迹记录（logs + bugs + plans） | 归档文件夹（archive/） | 归档 + 轮转 journal |
| 否定空间 | bugs/（排除的路） | 无 | 无 |
| 闭合验证 | 独立 AI 子代理 closure audit | 可选的 /opsx:verify（自查） | Acceptance Criteria（自查） |
| AI 全自动 | 概念层面支撑（plan=契约，不为人设计） | 未考虑（流程假设人在环） | 未考虑（3 相位为人设计） |
| Spec 校验 | 无（owner doc 按领域自由组织） | 强制（Zod schema + `openspec validate`） | 无（自由 Markdown） |
| 知识晋升 | bug → lesson → skill → script → lint | 无（archive 后无递进） | 无（break-loop 产出进 spec 即止） |

### 9. 理论与实践的共演

动力系统的思想（`state space → attractor → trajectory → control`）是 AGE 最早确立的概念框架。但具体如何落地——owner doc 用什么结构、闭合审计怎么做、晋升阶梯怎么设计——在实践中不断调整。

nop-chaos-flux 的演化历史：

- **吸引子载体**从扁平架构文档演化为 4 层优先级体系——在多次审计中发现优先级冲突后才固化
- **闭合审计**从人工检查演化为必须由独立子代理验证——Plan 143 的闭合假设被多次推翻后才确立
- **晋升阶梯**不是一开始设计的，而是发现"prose-only lessons 无法阻止同一错误再次出现"后逐步形成的
- **open-ended adversarial review** 的出现是因为 deep-audit 的固定维度有时会错过维度之外的问题

具体用什么文档结构承载吸引子、用什么机制检查偏离——在动力系统思想指导下，通过实践不断校准和细化。

Trellis 来自实用观察（"什么实践在 AI 工程中有效"），5 条核心原则是经验总结。没有从理论推导实践的链路，扩展靠的是经验堆叠。

## 总结

**核心发现**：Trellis 和 OpenSpec 都以**单次变更**为组织单元，AGE 以**仓库整体轨迹**为组织单元。前者关心"这次变更是否正确"，后者关心"连续多次 AI 变更之后仓库是否还在朝正确方向走"。

具体来说，三者在八个维度上存在根本分歧：

1. **组织单元**：Trellis 和 OpenSpec 是任务级工具（一个 task / 一个 change）；AGE 是轨迹级方法论（仓库整体）。这是最根本的差异，以下所有分歧都由此衍生。

2. **工具从哪里来**：Trellis 的 12 个 skill 和 OpenSpec 的 slash commands 在框架设计时预设，新项目开箱即用；AGE 的工具从历史轨迹反向提取，新项目从 Template 通用工具起步，但可参考 nop-chaos-flux、nop-entropy、nop-chaos-next 等开源范例加速。两者面向不同的阶段需求。

3. **从失败中提取知识的深度**：三者都从失败中学习。Trellis 的 `trellis-break-loop` 提供 5 维度分析，OpenSpec 有 archive 保留变更历史，知识沉淀为 prose；AGE 的晋升阶梯将同一模式逐级提升为自动化工具。区别在产物的自动化程度。

4. **规范性质**：OpenSpec 的 specs 是结构化行为契约（Requirement + Scenario），比 Trellis 的编码约定更接近"可执行契约"。但两者都没有区分"当前行为"和"应收敛到哪里"。AGE 的 owner doc 是方向定义，不是需求描述。

5. **单次变更之间的连续性**：Trellis（task 归档 + journal 轮转）和 OpenSpec（archive 文件夹）都没有结构化的轨迹记录。AGE 的 logs 精确到代码路径和验证基线，bugs 记录否定空间。

6. **向 AI 全自动演进的支撑**：Trellis 和 OpenSpec 的流程都假设人在环。AGE 的 plan 是闭合契约，draft review 和 closure audit 由独立 AI 子代理完成，人逐步退出。

7. **审计层级**：三者都有代码质量门禁。AGE 多了方向性审计（deep-audit 20 维度含文档-代码一致性），检查"系统是否朝正确方向收敛"，但执行成本极高。

8. **理论生成 vs 经验堆叠**：AGE 以动力系统思想为起点，实践元素可从理论推演。Trellis 和 OpenSpec 来自实用观察，扩展靠经验堆叠。


## References

**AGE 方法论:**
- AGE Template: [https://github.com/entropy-cloud/attractor-guided-engineering-template](https://github.com/entropy-cloud/attractor-guided-engineering-template)
- `~/app/attractor-guided-engineering-template/AGENTS.md` — 第 15 条：工具晋升规则
- `~/app/attractor-guided-engineering-template/docs/articles/attractor-before-harness-ai-large-scale-development-methodology.md` — 吸引子先于线束

**nop-chaos-flux (AGE 实例):**
- GitHub: [https://github.com/entropy-cloud/nop-chaos-flux](https://github.com/entropy-cloud/nop-chaos-flux)
- Gitee: [https://gitee.com/canonical-entropy/nop-chaos-flux](https://gitee.com/canonical-entropy/nop-chaos-flux)
- `docs/skills/README.md` — 22 个 skill/prompt 索引
- `docs/skills/deep-audit-prompts.md` — 2141 行，20 维度深度审计框架
- `docs/skills/open-ended-adversarial-review-prompt.md` — 开放式对抗性审查
- `docs/skills/bug-diagnosis-prompt.md` — 从 66+ bug 中提取的诊断方法论
- `docs/plans/00-plan-authoring-and-execution-guide.md` — 403 行，24 条最小规则
- `scripts/audit/` — 15 个文件（含 11 个扫描器 + 规则/共享模块）
- `docs/bugs/` — 66 个 bug 修复历史
- `docs/articles/first-principles-of-agent-engineering.md` — 276 行，Agent 工程根本原则
- `docs/articles/age-from-state-engineering-to-trajectory-engineering.md` — 从状态工程到轨迹工程

**nop-entropy (AGE 实例):**
- GitHub: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- `ai-dev/tools/rules/` — 3 条 ast-grep Java lint 规则
- `docs-for-ai/INDEX.md` — 规范性使用文档索引

**nop-chaos-next (AGE 实例):**
- Gitee: [https://gitee.com/canonical-entropy/nop-chaos-next](https://gitee.com/canonical-entropy/nop-chaos-next)
- 应用层 AGE 实践：`design/`、`input/`、`logs/`、`bugs`、`skills/` 轻量模式
