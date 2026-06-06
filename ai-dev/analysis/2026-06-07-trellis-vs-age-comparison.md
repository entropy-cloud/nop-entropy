# Trellis (第三方) vs AGE 工具体系对比

> Status: open
> Date: 2026-06-07
> Scope: 第三方 AI 工程框架 Trellis 与 AGE (Attractor-Guided Engineering) 的工具体系对比；两个 AGE 实例 nop-chaos-flux 和 nop-entropy 的工具提取实践
> Conclusion:

## Context

- **Trellis 不是 AGE**。它是一个第三方开源模板 (`@mindfoldhq/trellis`)，目标是"让 AI 编码代理在 14+ 平台上有一致的工程流程"
- **AGE (Attractor-Guided Engineering)** 是从 nop-chaos-flux 的实践过程中涌现的方法论，核心是 `state space -> attractor -> trajectory -> control`
- AGE 的工具不是预设的，而是**从历史轨迹中反向提取**的：反复出现的错误 → bug note → lesson → skill/prompt → audit script → lint rule → CI guard
- 本分析聚焦于两者在**工具来源、知识持久化、规范/历史分离、plan 系统、审计体系**上的根本差异
- 两者面向不同问题：Trellis 是跨平台通用工程框架（新项目开箱即用），AGE 是围绕吸引子收敛的方法论（需要实践积累）

## Analysis

### 1. 定位：Trellis 是什么、AGE 是什么

**Trellis**：

- 一个**产品化的工程框架**，通过 `npm install -g @mindfoldhq/trellis && trellis init` 安装
- 提供 3 相位工作流（Plan → Execute → Finish）、任务管理脚本、spec 系统、子代理分派、多平台 hook
- 工具链在设计时就被确定：`trellis-brainstorm`、`trellis-check`、`trellis-update-spec`、`trellis-break-loop` 等 12 个 skill
- 用户按框架规定的结构填入自己的 spec
- 5 条核心原则：Plan before code、Specs injected not remembered、Persist everything、Incremental development、Capture learnings

**AGE**：

- 一个**方法论**，不是产品。没有 npm 包，没有安装命令
- AGE Template (`attractor-guided-engineering-template`) 是一个复制即用的文档骨架，定义文档职责和流程，但不预装具体工具
- 工具从实践中生长出来：nop-chaos-flux 的 `scripts/audit/` 下有 15 个文件（含 11 个扫描器）、`docs/skills/` 下有 22 个 prompt——全部是从 bug 历史、审计发现、重复失败中反向提取的
- nop-entropy 的 `ai-dev/tools/` 下有 3 条 ast-grep lint 规则——从 Java 异常处理实践中提取
- 理论框架：`state space → attractor → trajectory → control`，所有实践元素可从此推演

### 2. 工具来源：预设 vs 反向提取

这是最根本的差异。

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

这些 skill 是通用的、与具体项目无关的。用户不需要有 bug 历史或审计发现就能获得这些工具。其中 `trellis-break-loop` 和 `trellis-update-spec` 构成了一套从失败中提取知识的机制（详见第 4 节）。

#### AGE：工具从历史轨迹中反向提取

nop-chaos-flux 的工具生长链路清晰可见：

**链路 1：硬编码类型分发 → audit script**

```
Bug 发现：渲染器中存在硬编码的 type switch
  → 审计发现：违反"渲染器应通过注册表分发"的架构契约
  → Plan 430: eliminate-hardcoded-type-dispatch-plan
  → 提取为脚本: scripts/audit/find-hardcoded-type-dispatch.mjs
  → 规则编码在: scripts/audit/rules.mjs
```

**链路 2：渲染器 marker 缺失 → audit script**

```
Bug 发现：渲染器未按契约输出 marker class
  → 多次出现在 deep audit 维度 09
  → 提取为脚本: scripts/audit/find-missing-renderer-markers.mjs
```

**链路 3-5**：响应式订阅不精确、async 无失败路径、React 19 遗留 API → 各自对应的 audit script

nop-entropy 同样遵循这个模式：

```
Bug 发现：Java 代码中使用裸 RuntimeException
  → 约定：应使用 NopException 子类
  → 提取为 ast-grep 规则: ai-dev/tools/rules/java-lint-bare-runtimeexception.yml
```

#### 开箱即用 vs 积累后才有

这是一个关键权衡：Trellis 新项目立即拥有完整的 12 个 skill + 工作流状态机 + 跨平台 hook；AGE 新项目只有 AGE Template 的基础工具（`check-doc-links`、`check-oversized-files` 等通用工具），有价值的领域特定工具需要先有实践积累。Trellis 适合"今天就开始"的场景，AGE 适合"长期演化"的项目。

### 3. 从失败中提取知识：两种不同深度的机制

Trellis 和 AGE 都有从失败中提取知识的机制，但提取的**深度和自动化程度**不同。

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

两者都从失败中提取知识。差异在于**提取产物的形式和自动化程度**：

| 维度 | Trellis | AGE |
|------|---------|-----|
| 分析框架 | 5 维度（break-loop） | 晋升阶梯（5 层级） |
| 知识产物 | prose spec（编码约定、案例） | prompt → script → lint rule（逐步自动化） |
| 沉淀位置 | `.trellis/spec/` | `docs/skills/` → `scripts/audit/` → CI |
| 自动化程度 | prose only（人读） | prose → 半自动扫描 → 全自动拦截 |
| 递进机制 | 无（break-loop 产出直接进 spec） | 有（同一模式重复出现时晋升到更高层级） |

AGE 之所以能递进，是因为它有专门的时效性文档类别（`bugs/`、`lessons/`、`skills/`）来跟踪模式是否重复出现。Trellis 的知识一次性沉淀到 spec，没有"同一模式再犯时如何升级"的机制。

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

**规范与历史没有分离**。`trellis-update-spec` 的模板类型（Design Decision、Common Mistake、Case Study、Gotcha）天然鼓励把历史信息写入 spec。`trellis-break-loop` 在 5 维度分析后也要求"update spec/guides"。Trellis 没有独立的 `bugs/`、`logs/`、`lessons/` 目录来承接历史信息——`tasks/` 完成后归档，`workspace/journal` 2000 行轮转。学到的内容无处可去，只能沉淀到 spec 里。

实际例子——`quality-guidelines.md`（982 行）中的历史性内容：

- "Case Study (2026-04-22): `current_phase` / `next_action` drift across 4 writers + type declaration" — 完整的 bug 演化历史，包含"第一次审计漏掉了什么"、"四种漂移模式"、"最终整合结果"
- "Cautionary tale — 0.6.0-beta.3 → 0.6.0-beta.4 emergency revert" — 版本回退事件经过
- "Case Study (2026-04-30): issue #204 `--yes` + bootstrap recovery" — 包含 commit hash、发现过程、修复过程

`workflow-state-contract.md`（299 行）中："Two production bugs (Phase 1.3 jsonl curation skip, Phase 3.4 commit skip) hit exactly this failure mode."

这不是说 Trellis 的做法没有价值——Case Study 混在规范中提供了即时的因果上下文（"为什么这条规则存在"），AGE 则需要跨文件引用才能获得同样的上下文。但代价是 spec 文件存在膨胀风险，且 AI 读取时无法区分"当前规范"和"历史教训"。

#### AGE：按领域需要组织，规范与历史严格分离

AGE 文档组织的约束只有两条：

1. **渐进式披露**：从最小的入口（index / start-here）逐步展开到详细内容
2. **规范文档与时效性历史文档分离**：稳定文件用稳定文件名，时间敏感记录带日期

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

### 5. Plans vs Todo：闭合契约，且为人逐步退出做准备

AGE 的 plan 与 todo 有本质区别。Plan 的目标不仅是"把这件事做完"，而是为**AI 全自动执行**建立可验证的闭合条件。

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

AGE 的整体目标是**逐步减少人类介入节点**。当前实践中人仍然控制少数关键节点（定义吸引子、裁决冲突、校准方向），但 plan 的 draft review 和 closure audit 已经完全由 AI 独立子代理完成。未来的方向是：人只控制**输入**和**最终产出**，对中间过程不逐条审查，而是抽样监控关键节点。

`first-principles-of-agent-engineering.md` 定义了 Agent 的本质："Agent 是一个在目标约束下，跨时间维持、验证、修正、复用可行动认知结构的系统。"Plan 是验证和修正的局部载体——它定义"这轮扩张怎样才算真正完成"，然后由独立子代理验证。

#### 与 Trellis 的对比

Trellis 的等价物是 `prd.md` + `implement.jsonl`。PRD 通过 Phase 1 的协作式 brainstorming 与用户共同创建，Phase 2 的实现和检查由子代理自主完成，Phase 3.4 的提交需要用户 one-shot 确认。

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
| 人的角色 | 参与 PRD 协作和提交确认 | 不审查中间 plan，只控制输入和最终产出 |
| 自动化目标 | 人始终在环 | 逐步向全自动推进 |
| 实现阶段 | Phase 2 自主（子代理执行+检查） | AI 自主执行 |

#### Logs：自动记录的轨迹

AGE 的 AGENTS.md 强制要求："After completing any significant code change, you MUST update the daily dev log"。

nop-chaos-flux 的 `logs/2026/` 目录有 72 个每日日志文件，每个记录：具体关闭了哪些 plan 的哪个 workstream、修改了哪些代码路径（精确到文件:行号）、哪些 focused proof 通过了、哪些 owner doc 同步更新了、全仓验证状态、独立闭合审计的 subagent task ID。

Trellis 的等价物是 `workspace/journal-N.md`——个人会话记忆，2000 行后轮转。不记录精确的代码路径和验证基线，与 plan 无关联。

### 6. 审计体系：执行性检查 vs 方向性审计

AGE 和 Trellis 都有审计能力，但审计的对象和目标不同。

#### Trellis：代码质量检查

Trellis 的 `trellis-check` 是代码质量检查 skill（lint、typecheck、test），加上 `trellis-break-loop` 的 5 维度事后分析。审计对象是**代码是否符合 spec**。

#### AGE：文档+代码整体一致性审计

AGE 提供了两种互补的审计方式，专门检查"吸引子与实际投影是否一致"：

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

**核心差异**：AGE 的审计是在检查"系统是否还在朝正确方向收敛"（方向性审计），Trellis 的检查是在验证"代码是否按规范写"（执行性检查）。AGE 的 deep-audit 执行成本极高（多轮独立子代理），这是需要权衡的代价。

### 7. 两者的工具对比总结

| 维度 | Trellis | AGE (nop-chaos-flux / nop-entropy) |
|------|---------|-------------------------------------|
| 工具来源 | 框架预设，安装即有 | 从历史轨迹反向提取 |
| 新项目启动成本 | `trellis init` 即有完整工具集 | 需要先有实践历史才能提取有价值的领域特定工具 |
| 工具演进机制 | `trellis update` (框架升级) | 晋升阶梯 (bug → lesson → prompt → script → lint) |
| 从失败中提取知识 | 有（break-loop 5 维度分析 → spec prose） | 有（晋升阶梯 → 逐步自动化工具） |
| 知识产物自动化 | prose only | prose → 半自动扫描 → 全自动拦截 |
| 多平台适配 | 核心能力 (14+ 平台) | 不关注，依赖宿主项目的 CI |
| 流程自动化 | 核心能力 (状态机、hook、子代理) | 有限 (计划闭合检查脚本、pre-commit hook) |
| 工具维护成本 | 低（框架统一维护） | 高（每个工具需要随项目演进更新白名单和规则） |
| 规范/历史分离 | 不分离（历史沉淀到 spec） | 严格分离（owner doc 只含当前状态） |
| Plan 系统 | PRD 需求描述 | 闭合契约（draft review + closure audit） |
| 审计类型 | 执行性检查（代码 vs spec） | 方向性审计（代码 vs 吸引子） |

### 8. 理论与实践的共演

动力系统的思想（`state space → attractor → trajectory → control`）和吸引子的概念是 AGE 最早确立的概念框架。但具体如何落地——owner doc 用什么结构、闭合审计怎么做、晋升阶梯怎么设计——这些细节是在实践中不断完善的。

nop-chaos-flux 的演化历史：

- **吸引子载体**从扁平架构文档演化为 4 层优先级体系——在多次审计中发现优先级冲突后才固化
- **闭合审计**从人工检查演化为必须由独立子代理验证——Plan 143 的闭合假设被多次推翻后才确立
- **晋升阶梯**不是一开始设计的，而是发现"prose-only lessons 无法阻止同一错误再次出现"后逐步形成的
- **open-ended adversarial review** 的出现是因为 deep-audit 的固定维度有时会错过维度之外的问题

理论提供方向和概念框架。具体用什么文档结构承载吸引子、用什么机制检查偏离——需要在动力系统思想指导下，通过实践不断校准和细化。

Trellis 来自实用观察（"什么实践在 AI 工程中有效"），5 条核心原则是经验总结。没有从理论推导实践的链路，扩展是经验性的。

## Conclusion

Trellis 和 AGE 的差异不在工具数量或文档结构细节，而在七个根本分歧：

1. **工具从哪里来**：Trellis 的 12 个 skill 在框架设计时预设，新项目开箱即用；AGE 的工具从历史轨迹反向提取，需要积累。两者面向不同的阶段需求。

2. **从失败中提取知识的深度**：两者都有从失败中学习的机制。Trellis 的 `trellis-break-loop` 提供 5 维度结构化分析，知识沉淀为 prose spec；AGE 的晋升阶梯将同一模式逐级提升为自动化工具。差异不在"是否提取"，而在"提取产物的自动化程度和递进机制"。

3. **文档结构服从谁**：Trellis 的 `.trellis/` 三件套服务于框架脚本的运转需要，所有项目结构相同；AGE 只要求渐进式披露和规范/历史分离两条约束，每个项目按领域需要组织。

4. **规范与历史是否分离**：AGE 的 owner doc 只描述"当前应该怎样"，历史信息分流到 `bugs/`/`logs/`/`plans/` 中。Trellis 的 spec 不区分——Case Study、Cautionary Tale 直接写进规范。Trellis 的做法提供了就地因果上下文，但存在 spec 膨胀风险。

5. **Plan 不是 Todo**：AGE 的 plan 是局部轨迹的闭合契约，有 draft review 和 closure audit（均由独立子代理完成），目标是逐步向全自动推进。Trellis 的 PRD 是需求描述，Phase 2 自主执行但整体人始终在环。

6. **审计类型**：AGE 的 deep-audit（20 维度，含文档-代码一致性）和 open-ended adversarial review 检查"系统是否朝正确方向收敛"，但执行成本极高。Trellis 的 `trellis-check` 检查"代码是否符合 spec"，成本低但维度窄。

7. **理论生成 vs 经验堆叠**：AGE 以动力系统思想为最早确立的概念框架，实践元素可从理论推演。Trellis 来自实用观察，扩展是经验性的。

## Open Questions

- [ ] AGE 的 deep-audit 执行成本极高。在中小型项目中如何降低？是否有"最低限度的方向性审计"标准？
- [ ] Trellis 的 spec 混合模式是否比 AGE 的分离模式对 AI 更友好？（就地因果上下文 vs 跨文件引用）
- [ ] Trellis 的 `trellis-break-loop` 框架是否可以扩展为 AGE 式的晋升阶梯？即在 prose spec 之上增加"同一模式再犯时晋升为自动化工具"的机制？
- [ ] AGE 的工具提取高度依赖实践积累。一个全新项目在初期如何获得足够的结构性防护？
- [ ] Trellis 的 `trellis-check` 是否可以增加文档-代码一致性维度？还是说这种检查需要吸引子概念才能有效执行？

## References

**Trellis:**
- `~/ai/trellis/.trellis/workflow.md` — 工作流状态机（690 行），含 5 条核心原则
- `~/ai/trellis/.trellis/spec/cli/backend/quality-guidelines.md` — 982 行，含 Case Study 和 Cautionary Tale
- `~/ai/trellis/.trellis/spec/cli/backend/error-handling.md` — 380 行，含 Real example 和 Common Mistake
- `~/ai/trellis/.agents/skills/trellis-break-loop/SKILL.md` — 130 行，5 维度 bug 分析框架
- `~/ai/trellis/.agents/skills/trellis-update-spec/SKILL.md` — 356 行，spec 更新流程和模板
- `~/ai/trellis/.agents/skills/` — 12 个预设 skill（contribute、trellis-brainstorm、trellis-check 等）

**AGE 方法论:**
- `~/app/attractor-guided-engineering-template/AGENTS.md` — 第 15 条：工具晋升规则
- `~/app/attractor-guided-engineering-template/docs/articles/attractor-before-harness-ai-large-scale-development-methodology.md` — 吸引子先于线束

**nop-chaos-flux (AGE 实例):**
- `docs/skills/README.md` — 22 个 skill/prompt 索引
- `docs/skills/deep-audit-prompts.md` — 2141 行，20 维度深度审计框架
- `docs/skills/open-ended-adversarial-review-prompt.md` — 开放式对抗性审查
- `docs/skills/bug-diagnosis-prompt.md` — 从 66+ bug 中提取的诊断方法论
- `docs/plans/00-plan-authoring-and-execution-guide.md` — 403 行，24 条最小规则
- `scripts/audit/` — 15 个文件（含 11 个扫描器 + 规则/共享模块）
- `docs/bugs/` — 66 个 bug 修复历史
- `docs/articles/first-principles-of-agent-engineering.md` — 276 行，Agent 工程根本原则

**nop-entropy (AGE 实例):**
- `ai-dev/tools/rules/` — 3 条 ast-grep Java lint 规则
- `docs-for-ai/INDEX.md` — 规范性使用文档索引
