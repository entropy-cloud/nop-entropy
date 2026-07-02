# Lattice vs AGE (Attractor-Guided Engineering) 对比分析

> Status: open
> Date: 2026-07-02
> Scope: Lattice (产品化 AI Coding Control Plane) 与 AGE Template (方法论骨架/项目记忆模板) 的全维度对比；两个 nop-entropy 已覆盖的方向
> Conclusion:

## Context

- **Lattice** 是一个可安装的 repo-local AI Coding Control Plane（产品），包含 PrismSpec（Spec Coding 工作流技能包）和完整交付管线
- **AGE (Attractor-Guided Engineering)** 是从 nop-chaos-flux 实践涌现的方法论，AGE Template 是其**文档骨架模板 + 可编程执行层**——除了 `docs/` 文档结构外，还包含 `tools/mission-driver/`（Node.js 流式引擎，自动化整个 AGE 开发循环）
- 此前 `2026-06-07-trellis-vs-age-comparison.md` 对比了 Trellis（第三方框架）与 AGE，但 Trellis 和 Lattice 是**不同的项目**——Trellis 也是产品化框架，但侧重多平台适配和工作流状态机；Lattice 侧重 repo-local 控制面 + PrismSpec 规约驱动
- nop-entropy 的 `ai-dev/` 体系继承自 AGE 方法论，因此本分析对 nop-entropy 的自身工具演进有直接参考价值

## 元定位：方法论 vs 产品

| 维度 | Lattice | AGE Template |
|------|---------|-------------|
| 本质 | **可安装的产品** (install.sh → 目标项目) | **复制即用的文档骨架** (git clone → 手动填充) |
| 一句话定义 | "面向团队的 repo-local AI Coding Control Plane" | "轻量吸引子工程文档模板" |
| 核心能力 | PrismSpec 工作流 + Context + Verification + Evidence/Eval + Loop/Learn | 项目记忆分层 + mission-driver 自动化执行层（文档结构 + 可编程流引擎） |
| 可执行码 | 有：Bash 脚本、pipeline、gates、eval 管线 | 有：Node.js mission-driver 流引擎 + `tools/` 辅助脚本 + opencode subagent 编排 |
| 安装方式 | `bash install.sh /path/to/project --init` | 手动 `cp -r` 或 `git clone` 后填充 `START-HERE-after-copy.md` |
| 升级路径 | `kernel/` 框架代码可升级，项目资产不覆盖 | 无（模板是一次性复制品） |
| 依赖 | Bash 3.2+, yq 4.x, git | 无运行时依赖（tools/ 需要 Node.js，可选） |

**Lattice 是个可以"装"到项目里的工程控制面**。安装后目标项目获得 `lattice/` + `prismspec/` 目录、CLAUDE.md import 和 pipeline 脚本。项目资产和框架代码分离，框架可升级。

**AGE Template 是个包含文档结构和可编程执行层的模板**。复制后目标项目获得 `docs/`（文档骨架）+ `tools/mission-driver/`（Node.js 流式引擎）+ `AGENTS.md` + 21 个 skills。mission-driver 本身是一个产品级组件，有完整的测试套件（`tools/mission-driver/test/`）和设计文档（`tools/mission-driver/design/`），可被各项目独立升级。nop-entropy 即通过 `ai-dev/tools/mission-driver.sh` 引用 AGE Template 的 mission-driver。

## 核心工作流对比

### Lattice：PrismSpec 规约驱动

```
Intent → [Clarify] → Specification (spec.md) → Planning (plan.md) → Implementation (plan|tdd) → Review (review.md) → Verification (verify.md)
```

中心思想：**每次 AI Coding 任务落到四个可审查文件**（spec.md / plan.md / review.md / verify.md），由 `guide.sh` 根据当前产物自动路由下一阶段。管的是**单个任务的契约生命周期**。

### AGE：吸引子驱动

```
吸引子（architecture owner docs）→ 偏差发现（audit）→ 轨迹闭合（plan）→ 局部执行 → 独立验证（closure audit）→ 证据记录（logs/bugs/testing/analysis）
```

中心思想：**系统应收敛到架构 owner doc 定义的稳定结构**，多类文档各有职责，不强制每件事都通过 spec/change 管道。管的是**项目长期收敛方向**。

### 核心差异

| 维度 | Lattice | AGE |
|------|---------|-----|
| 聚焦粒度 | **单次 AI Coding 任务** | **项目长期演化** |
| 工作流触发 | 用户输入 Intent → guide.sh 路由 | 需求/审计/测试发现 → 分类到不同文档类别 |
| 工件形态 | 固定 4 件套（spec/plan/review/verify） | 自由但受约束（22 类文档各有职责和优先级） |
| 执行模式 | plan（低风险） / tdd（高风险） | 两步走：plan 定义局部轨迹闭合契约 → closure audit 独立验证 |
| 元信息载体 | `spec.md#Context Basis` 记录采纳/排除的事实 | 各类 owner doc 各自记录"当前状态"和"拒绝的方案" |
| 风险自适应 | plan → tdd 可升级，不允许静默降级 | plan 必须有 closure gates，高风险走独立 audit |

## 工件组织：框架固定 vs 领域自由

### Lattice 的固定结构

```
your-project/
├── lattice/
│   ├── manifest.yaml            # 项目契约
│   ├── config/failure-categories.yaml
│   ├── kernel/                  # 可升级框架码
│   ├── context/                 # 项目上下文
│   │   ├── README.md            # 上下文地图
│   │   ├── knowledge/           # 项目知识
│   │   └── drafts/              # 知识草稿
│   ├── specs/<spec-id>/         # 核心：4 件套
│   │   ├── spec.md
│   │   ├── plan.md
│   │   ├── review.md
│   │   └── verify.md
│   └── state/                   # 运行状态
└── prismspec/                   # 独立 skill pack
    ├── skills/                  # 13 个 agent skills
    ├── templates/
    ├── references/
    └── bin/
```

Lattice 的工件结构由**框架的脚本需要**决定：`guide.sh` 读 `specs/` 目录，`pipeline.sh` 读 `manifest.yaml`，`eval-sink.sh` 写 `state/`。

### AGE Template 的领域驱动结构

```
your-project/
├── AGENTS.md
└── docs/
    ├── context/                 # 强制 AI 上下文
    ├── backlog/                 # 优先级队列
    ├── input/                   # 原始输入
    ├── requirements/            # 实现就绪需求
    ├── design/                  # 应用层设计
    ├── architecture/            # 技术基线
    ├── plans/                   # 执行计划
    ├── logs/                    # 每日日志
    ├── bugs/                    # 复杂 bug
    ├── testing/                 # 人工测试发现
    ├── analysis/                # 调研评估
    ├── audits/                  # 审计记录
    ├── skills/                  # 可复用 prompt
    ├── discussions/             # 讨论记录
    ├── retrospectives/          # 复盘分析
    ├── lessons/                 # 经验教训
    ├── examples/                # 示例
    └── references/              # 快速参考
```

AGE 的结构由**应用开发领域需要**决定：输入需要 `input/` + `requirements/` 分离；PM 需要 `backlog/`；设计需要 `design/` + `architecture/` 分离；历史需要 `logs/` + `bugs/` + `analysis/` 分流。

### 对比

| 维度 | Lattice | AGE |
|------|---------|-----|
| 结构决定者 | **框架**（pipeline/guide/orchestrator 脚本需要） | **领域**（项目类型决定） |
| 跨项目一致性 | 高（所有 Lattice 项目结构一样） | 低（每个项目按领域需求组织） |
| 框架适配成本 | 无（Lattice 就是框架） | 需要理解领域 |
| 吸引子多样性 | 单类型（spec.md + context knowledge） | 多类型（architecture/doc + plans/logs + skills/prompts + bugs） |
| 累积性 | `state/` 自动累积，`context/knowledge/` 受控累积 | 全部类别天然累积，独立文件不分岔 |

## 项目级执行层：mission-driver vs Lattice Pipeline

> **前一版分析最大的遗漏**：认为"AGE 只有文档约束，没有自动化管线"。实际上 AGE Template 包含 `tools/mission-driver/`——一个 Node.js 流式引擎，提供比 Lattice pipeline 更高层级的自动化。

### Lattice Pipeline：单次任务的质量门禁

Lattice 的 `pipeline.sh` 是**命令级自动化**——运行一系列预定义命令并收集结构化输出：

```bash
lattice/kernel/delivery/pipeline.sh --json-out
  ├── spec-lint      # 规范格式检查
  ├── ac-coverage    # AC 覆盖度
  ├── drift-check    # 架构漂移检测
  └── compliance     # 合规门禁
```

特点：
- 每个 step 是 shell 命令，退出码决定 pass/fail
- 失败后 retry → escalate（等人工）
- 输出 eval JSON 供 CI/dashboard 消费
- 运行时间是秒级到分钟级
- 不受 LLM 调用影响，完全可复现

### AGE mission-driver：项目级自动开发循环

AGE 的 `mission-driver` 是**子代理级自动化**——用独立 opencode subagent 执行每个步骤，自主决策和修复：

```
CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → AUDIT → (loop)
```

| Step | 类型 | 具体行为 |
|------|------|----------|
| CHECK | AI agent prompt | 运行 `mvn test`，失败时 AI 自诊断自修复，最多 3 次重试（每次全新空上下文 session） |
| REVIEW_PLANS | 脚本 group | 扫描 `plans/` 目录，把 draft plan 提升为 active，零 AI 调用 |
| EXEC_PLANS | group + forEach | 扫描所有 active plan → 对每个 plan 执行 `plan-execution` 子流程 |
| DRAFT_PLANS | AI agent prompt | 读 roadmap → 生成新 plan → 调用独立 subagent 做对抗性审查（最多 2 轮）→ 输出 `created` |
| AUDIT | subflow | multi-audit + open-ended audit → 从发现结果 draft 新 plan |

#### plan-execution 子流程

```
EXECUTE (AI agent)
  → CLOSURE_SCRIPT_CHECK (脚本：检查 checklist 是否全勾、状态是否一致)
    → pass → BUILD_VERIFY (AI agent：构建验证)
    → fail → CLOSURE_AUDIT (AI agent：诊断修复)
      → approved → BUILD_VERIFY
      → issues (最多 3 次) → 回 EXECUTE
  → BUILD_VERIFY pass → 完成
```

#### 与 Lattice Pipeline 的深层差异

| 维度 | Lattice pipeline.sh | AGE mission-driver |
|------|--------------------|--------------------|
| 自动化层级 | **命令级**（shell 脚本） | **子代理级**（opencode subagent） |
| 失败处理 | retry → escalate（等人工） | AI 自诊断自修复（全新 session 上下文独立重试） |
| 修复能力 | 无（只能报告失败） | 有（Health Check 失败→AI 读日志→改代码→重试） |
| 作用域 | **单次任务**（一个 spec 的质量门禁） | **整个项目**（执行所有 plan、draft 新 plan、审计、循环） |
| 循环能力 | 无（一次执行结束） | 有（maxCycleVisits=30，执行完→draft→审计→再执行） |
| 数据结构依赖 | manifest.yaml + spec 目录 | roadmap + plans 目录 + audit 结果文件 |
| 输出 | eval JSON（结构化数据） | plan 文件更新 + audit 结果文件 + log |
| 运行时间 | 秒~分钟 | 分钟~小时（涉及多次 LLM 调用） |
| 可复现性 | 高（相同命令相同结果） | 中（依赖 LLM 输出，但有 retry 和评审机制兜底） |

### 为什么这个差异重要

**Lattice 没有也不可能有 mission-driver 的对等物**。原因不是技术上的，而是数据结构上的：Lattice 的 `specs/<id>/` 目录是**任务隔离**的——每个 spec 独立，没有跨 spec 的 roadmap、没有独立存放的 audit 结果、没有 project 级的 plans 目录。Lattice 的操作单元是"一次任务"，它没有"项目级状态"这个概念。

mission-driver 操作的数据结构恰好是 AGE 的文档类别：
- `docs/backlog/roadmap.md` → 决定"下一步该做什么"
- `docs/plans/*.md` → 执行和验证
- `docs/audits/*.md` → 发现偏差和生成新工作

AGE 的文档结构本身就是一个可被程序操作的项目状态机。mission-driver 是这个状态机的执行器。

### nop-entropy 的实际使用

```bash
# ai-dev/tools/mission-driver.sh — 指向 AGE Template 的 mission-driver
MISSION_DRIVER_HOME="$HOME/app/attractor-guided-engineering-template/tools/mission-driver"
exec node "$MISSION_DRIVER_HOME/src/main.js" --dir "$DIR/../.." --missions-dir "ai-dev/missions" "$@"
```

`ai-dev/missions/ai-agent.json` 定义了 nop-ai-agent 模块的 mission：
- roadmap: `design/nop-ai-agent/nop-ai-agent-roadmap.md`
- plans 目录: `plans/`
- 测试命令: `mvn test -pl nop-ai/nop-ai-core -am`

这意味着 `./ai-dev/tools/mission-driver.sh run ai-agent` 可以全自动执行 nop-ai-agent 的开发循环：先确保构建通过，然后逐一执行所有活跃 plan，再从 roadmap 生成新 plan，最后审计代码质量和架构一致性。

## 验证与闭合机制

Lattice 和 AGE 在验证层面的差异，需要放在 mission-driver 的上下文里重新理解。

### Lattice：Pipeline + Gates + Eval

Lattice 的验证是可复现的命令管线：

```bash
lattice/kernel/delivery/pipeline.sh --json-out
  ├── spec-lint      # 规范格式检查
  ├── ac-coverage    # AC 覆盖度
  ├── drift-check    # 架构漂移检测
  └── compliance     # 合规门禁
```

验证结果是**结构化证据**，写入 `lattice/state/eval-runs/*.json`，可被 CI 上传、dashboard 汇总、history/report 查询。

Agent 行为由 `rules.md` + 编排脚本（task-next, task-complete, spec-status, plan-lint）精密控制，不依赖对话记忆。

### AGE：mission-driver 驱动的执行→验证闭环

AGE 的验证通过 mission-driver 的 `plan-execution` 子流程实现，它是**子代理驱动的自动闭环**：

```
EXECUTE (AI agent 执行 plan)
  → CLOSURE_SCRIPT_CHECK (脚本：checklist 检查)
    → pass → BUILD_VERIFY (AI agent 构建验证)
    → fail → CLOSURE_AUDIT (独立 subagent 回到活仓库验证)
      → approved → BUILD_VERIFY
      → issues (最多 3 次) → 重新 EXECUTE（带着审计反馈）
```

三层验证：
1. **脚本层**：`CLOSURE_SCRIPT_CHECK`——机械检查 checklist 是否全勾、状态是否一致
2. **子代理层**：`CLOSURE_AUDIT`——独立 subagent 回到活仓库检查代码/文档/测试/闭合条件
3. **构建层**：`BUILD_VERIFY`——确保编译通过，AI 自修复构建失败

AGE 的 plan 是"局部轨迹闭合契约"——定义从哪里开始（Current Baseline）、做什么不做什么（Goals/Non-Goals）、每个执行项的性质（Fix/Decision/Proof）、闭合条件（Closure Gates）。

### 对比

| 维度 | Lattice pipeline | AGE mission-driver plan-execution |
|------|------------------|-----------------------------------|
| 验证触发 | pipeline 命令 | subflow 编排（脚本 + AI agent + 独立 subagent） |
| 验证内容 | lint + AC coverage + drift + compliance | script checklist + closure audit（全量检查）+ build verify |
| 验证结果形式 | eval JSON + Markdown | closure audit 裁决（Verdict: acceptable/reject） |
| 可复现性 | 高（同样命令同样结果） | 脚本层高，AI 层中（但有多轮 retry 兜底） |
| CI 集成 | 原生（eval CI artifact + PR comment） | 可通过 mission-driver.sh 集成 |
| 检查对象 | spec/plan 文件格式 + 代码漂移 | 计划 checklist + 实际代码/文档/测试一致性 |
| 证据持久化 | eval-runs JSON + outcome + history | plan 文件 Closure 段落 + log + audit 记录 |
| 自动化程度 | 命令级（shell） | **子代理级**（AI agent 执行 + 自修复 + 独立审计） |
| 失败处理 | retry → escalate（等人工） | AI 自修复（最多 3 次全新 session 重试）→ subflow 级 fallback → 主循环继续 |
| 人工介入点 | pipeline 失败后 | plan 的 closure audit 由独立 subagent 完成，人只在吸引子定义和架构决策 |

## 知识提取机制

两者都有关键的共同点：**知识应从实践中反向提取，不是预设的**。但机制不同。

### Lattice：Capture → Draft → Review → Promote

```
verify.md Knowledge Candidates
  → lattice/kernel/context/summary-learn-draft.sh
  → lattice/context/drafts/<draft>.md
  → lattice/kernel/context/knowledge-review.sh approve|reject
  → lattice/context/knowledge/ (pitfalls.md / rules.md / glossary.md)
```

知识沉淀有明确的**电子流**：draft → review event → promotion/discard → knowledge-governance-lint。

### AGE：晋升阶梯

```
Level 0: 发现重复问题
Level 1: bug note (docs/bugs/) — 记录非显然根因
Level 2: lesson (docs/lessons/) — 提取可复用判断规则
Level 3: skill/prompt (docs/skills/) — 可复用的审计/诊断方法
Level 4: audit script (scripts/audit/) — 半自动化扫描
Level 5: lint rule / CI guard — 全自动化拦截
```

知识晋升没有 Lattice 的电子流，而是**文档化的渐进升级**：从 bug note 开始，逐步提取为 lesson → prompt → script → lint。

有两层循环：计划回路（audit → plan → closure）和知识回路（bug → lesson → tool）。

### 对比

| 维度 | Lattice | AGE |
|------|---------|-----|
| 起点 | `verify.md` 的 Knowledge Candidates | bug / audit 发现的重复失败模式 |
| 晋升机制 | draft → review event → promote/discard | bug → lesson → prompt → script → lint |
| 自动化辅助 | summary-learn-draft.sh、knowledge-review.sh | 无（靠 AGENTS.md 第 10/15 条约束） |
| 晋升到 lint | 暂缺（Lattice 1.x 还没有 linter 自动生成） | 实际例子：nop-chaos-flux 15 个 audit 扫描器、nop-entropy 3 条 ast-grep 规则 |
| 治理 | review event + governance lint | AGENTS.md + 独立 subagent 判断 |
| 与 owner doc 关系 | 知识沉淀到 context/knowledge/ | 知识晋升同时反向更新 owner doc |

Lattice 的晋升在上层（draft → review → knowledge）有更结构化的电子流；AGE 在下层（script → lint）有更多实际产出。两者互相补充。

## 规范/历史分离

| 维度 | Lattice | AGE |
|------|---------|-----|
| 规范载体 | `lattice/context/knowledge/` + `spec.md` | `docs/architecture/` + `docs/design/` + `docs/context/` |
| 历史载体 | `lattice/state/`（运行状态） | `docs/logs/` + `docs/bugs/` + `docs/analysis/` + `docs/plans/` |
| 分离策略 | 框架代码/项目资产/运行状态三层分离 | 规范文档（稳定文件名）/时效性记录（带日期）分离 |
| 规范膨胀倾向 | 低（spec 每次新任务产生，不长期膨胀） | 低（owner doc 只在吸引子变化时替换） |
| 分离强制度 | **框架强制**（路径决定分类） | **规则强制**（AGENTS.md + plan guide 约束） |

Lattice 依赖**路径强分离**：`state/` 下是运行生成，`kernel/` 是框架代码，`context/` + `specs/` 是项目资产。AGE 依赖**命名规则 + 规则约束**：带日期的文件是历史，稳定文件名是规范。

## Agent 控制方式

| 维度 | Lattice | AGE |
|------|---------|-----|
| 入口文件 | `CLAUDE.lattice.md`（@import rules.md） | `AGENTS.md`（路由规则 + 操作约定） |
| 控制方式 | `rules.md` + 编排脚本（task-next/task-complete/spec-status） | AGENTS.md 路由规则 + mission-driver 流引擎编排 |
| 路由协议 | `guide.sh --json` 返回 JSON（stage/mode/skill/spec_dir/verify_command） | `docs/index.md` 路由表（单次任务）；mission-driver（项目级自动循环） |
| 状态推进 | 脚本驱动：`spec-status.sh` `drafted → planned → implemented → verified` | plan 文件状态字段 + mission-driver 的 `scan-active-plans` 脚本 |
| 自动循环 | 无（每次任务手动触发） | **有**（mission-driver: CHECK→REVIEW→EXEC→DRAFT→AUDIT loop，max 30 cycles） |
| 恢复机制 | `guide.sh --json` 从文件状态恢复 | mission-driver `--step <STEP>` 从指定步骤恢复 |
| 人环程度 | 低（Agent 自动路由，人在 manifest 和 key decision point） | 低（mission-driver 全自动执行，人在吸引子定义 + architecture decision） |

AGE 的 Agent 控制通过 mission-driver 提供了**比 Lattice 更高层级的自动化**。Lattice 控制的是"单个任务怎么走"，AGE 控制的是"项目怎么长期自动演进"。

## 与 nop-entropy 的关系

nop-entropy 继承的是 AGE 方法论，不是 Lattice：

| nop-entropy 实践 | 对应 AGE | 对应 Lattice |
|-----------------|----------|-------------|
| `ai-dev/analysis/` | `docs/analysis/` | 无直接对应 |
| `ai-dev/plans/` | `docs/plans/` | `lattice/specs/<id>/plan.md` |
| `ai-dev/logs/` | `docs/logs/` | 无（Lattice 无日志函数） |
| `ai-dev/design/` | `docs/design/` | context/knowledge/ |
| `ai-dev/bugs/` | `docs/bugs/` | 无（Lattice 无 bug note 概念） |
| `ai-dev/tools/` | `tools/` + `scripts/audit/` | `prismspec/bin/` + `lattice/kernel/delivery/` |
| `ai-dev/tools/mission-driver.sh` | **`tools/mission-driver/`** | 无直接对应（Lattice 无项目级自动循环） |
| `ai-dev/missions/ai-agent.json` | `mission.json` 配置 | 无直接对应 |
| `docs-for-ai/` | `docs/architecture/` + `docs/context/` | `lattice/context/` |
| 晋升阶梯实践 | 3 条 ast-grep lint 规则 | 暂缺实际晋升例 |
| 验证方式 | mission-driver plan-execution（AI agent 执行 + closure audit + build verify） | pipeline.sh + gates |

nop-entropy 的自动化执行层来自 AGE 的 mission-driver（通过 `ai-dev/tools/mission-driver.sh` 引用），不是来自 Lattice。mission-driver 是整个 AGE 体系中与 Lattice pipeline 直接对等的组件，且自动化层级更高。

## 总结

### 各自适合的场景

| 场景 | 推荐 Lattice | 推荐 AGE |
|------|-------------|----------|
| 团队需要可复现的验证管线 | ✅ pipeline.sh + gates | ✅ mission-driver plan-execution（AI agent + closure audit + build verify） |
| 需要全自动项目级开发循环 | ❌ 无项目级编排 | ✅ mission-driver（CHECK→PLANS→DRAFT→AUDIT loop） |
| 每次 AI 任务需留审计痕迹 | ✅ spec+plan+review+verify | ✅ plan+log+closure audit |
| 项目知识需要电子流治理 | ✅ draft→review→promote | ⚠️ 靠 AGENTS.md 规则 + 晋升阶梯 |
| 需要按领域自由组织文档 | ❌ 框架固定结构 | ✅ 领域驱动 |
| 需要 owner doc + 吸引子机制 | ❌ 无吸引子概念 | ✅ 核心方法论 |
| 轻量应用 (APP < 5 万行) | ⚠️ 可能太重 | ✅ 刚好够 |
| 框架核心开发 (如 nop-entropy) | ❌ 领域不适合 | ✅ 经验证（含 mission-driver 自动化） |
| 多 Agent 多平台适配 | ⚠️ 部分（有 adapter 文档） | ❌ 无 |
| 团队文档积累初始启动 | ❌ 需要先安装和配置 | ✅ 复制即用 |

### 根本分歧

1. **产品 vs 方法论**：Lattice 是可安装的产品，选 Lattice 就选了固定工件结构；AGE 是可复制的模板，选 AGE 就选了领域自由 + 自建工具。

2. **任务级契约 vs 项目级收敛**：Lattice 管的是"这次 AI Coding 任务怎么规约、验证、留痕"；AGE 管的是"项目长期怎么不偏离吸引子"。

3. **命令级管线 vs 子代理级自动循环**：Lattice 用 pipeline + gates + eval JSON 做命令级可复现验证，失败只能 escalate；AGE 用 mission-driver（子代理级流引擎）做全自动项目级开发循环，包含 AI 自诊断自修复、独立 subagent closure audit、multi-audit/deep-audit，且支持 maxCycleVisits 无限循环。AGE 的自动化层级高于 Lattice。

4. **框架升级 vs 自我演进**：Lattice 的 `kernel/` 可升级，知识存入 `context/knowledge/` 受控目录；AGE 的 AGENTS.md 第 15 条定义了工具晋升阶梯（bug→prompt→script→lint），工具从实践中生长，不依赖外部升级。

5. **single-spec vs multi-memory**：Lattice 一次只聚焦一个 spec/任务，状态在 state/ 中自动累积但缺乏"跨 spec 的历史叙事"（无 logs、无 bug notes、无 retrospectives）；AGE 有 22 类文档承载不同类型项目记忆——一次审计发现可以进入 audit → plan → logs → bugs → skills 的完整链路。

### 互补性

两者并非对立。理论上，一个项目可以同时使用 Lattice 做**任务级控制面**（用 PrismSpec 规约每次 AI Coding），同时引入 AGE 的**项目级记忆结构**（用 logs/bugs/analysis/retrospectives 承载长期轨迹）。但需要解决的问题：

- Lattice 的 `specs/` 结构是框架固定的，如何与 AGE 的 `docs/plans/` 共存？
- Lattice 没有 logs 函数，如何记录跨 spec 的历史叙事？
- Lattice 的 knowledge 治理是 draft→promote 电子流，AGE 的晋升是 bug→script 多形态，如何对齐？
- 两个体系的 Agent 控制规则（rules.md vs AGENTS.md）谁优先？

这些问题在单项目中同时采用两者时需要显式解决。

## References

**Lattice:**
- `~/ai/lattice/README.md` — 产品入口
- `~/ai/lattice/AGENTS.md` — 源码仓库 agent 指南
- `~/ai/lattice/docs/wiki/overall-design.md` — 整体设计文档
- `~/ai/lattice/harness-template/lattice/kernel/orchestrator/rules.md` — Agent 控制规则
- `~/ai/lattice/prismspec/README.md` — PrismSpec skill pack 说明
- `~/ai/lattice/docs/wiki/gaps-and-roadmap.md` — Gap 与路线图

**AGE:**
- `~/app/attractor-guided-engineering-template/AGENTS.md` — 模板 agent 指南
- `~/app/attractor-guided-engineering-template/docs/articles/from-spec-driven-development-to-attractor-guided-engineering.md` — AGE vs Spec-Driven 方法论文章
- `~/app/attractor-guided-engineering-template/docs/articles/attractor-before-harness-ai-large-scale-development-methodology.md` — 吸引子先于线束
- `~/app/attractor-guided-engineering-template/tools/mission-driver/` — mission-driver 流引擎（设计文档、源码、测试、流定义）
- `~/app/attractor-guided-engineering-template/tools/mission-driver/design/mission-driver-flow-design.md` — 主循环设计文档（含重试策略、subflow 隔离、marker 传播机制）
- `~/app/attractor-guided-engineering-template/tools/mission-driver/flows/mission-driver.json` — 主循环流定义
- `~/app/attractor-guided-engineering-template/tools/mission-driver/flows/plan-execution.json` — plan 执行子流程定义
- `~/app/attractor-guided-engineering-template/tools/mission-driver/flows/deep-audit-loop.json` — 深度审计子流程定义

**已有分析:**
- `ai-dev/analysis/2026-06-07-trellis-vs-age-comparison.md` — Trellis (第三方) vs AGE 对比

**nop-entropy 实践:**
- `docs-for-ai/INDEX.md` — 规范文档索引
- `ai-dev/logs/2026/07-02.md` — 当日日志
- `ai-dev/tools/rules/` — 从异常处理实践中提取的 ast-grep 规则
