# Mission Driver：Loop Engineering 的标准实现

> 从入门到精通，理解一个通用的 AI 任务驱动引擎如何通过 loop 嵌套实现局部容错和稳定保障。

---

## 一、问题：AI 应用的"失控"

你让 AI 做一件复杂的事——重构一个模块、处理一批数据、写一份深度对比分析。AI 很聪明，但它会：

- **走偏**：开始改 A，中途跑去改 B，最后 A 没改完 B 也改坏了
- **遗忘**：改了代码忘了跑测试，写了文档忘了更新引用
- **卡死**：某一步失败后反复重试进入死循环
- **无法恢复**：进程崩了，之前做了什么全丢了，从头再来
- **自我宣布完成**：说"做完了"但实际没人验证

这些问题的根源是：**AI 缺乏一个稳定的外部控制结构来约束它的行为轨迹**。

Mission Driver 就是这个外部控制结构。

---

## 二、什么是 Mission Driver（一分钟版）

Mission Driver 是一个**声明式的任务驱动引擎**。你给它一个目标（通过 roadmap 描述），它就自动进入一个循环：

```
检查健康状态（初始一次）→ 审查方案 → 执行方案 → 起草新方案 → （回到审查方案）→ 无新方案可起草时 → 深度审计 → （回到审查方案）...
```

循环一直跑，直到目标达成或审计预算耗尽。每一步都是独立的 AI 子进程，单个步骤失败不会影响整体循环。

它最初为软件开发设计（AGE 模板的一部分），其 flow 结构**可推广到**数据处理、文档分析等场景——但这需要配合自定义 prompt 和 commands 配置（详见 §6-§7）。

---

## 三、核心设计理念：Loop Engineering

### 3.1 为什么是 Loop，不是 Pipeline

传统的自动化是 pipeline 式的：A→B→C→D，线性执行，一步失败全部回滚或终止。

但 AI 任务不是线性的。AI 任务是**探索性的**：

- 执行 C 之后发现 B 的方案有问题 → 需要回到 B 重新设计
- 审计发现 D 的质量不达标 → 需要重新执行 D 或修改 D 的方案
- 执行过程中发现需求本身有歧义 → 需要回到设计阶段

这就需要 **loop**——一个可以反复迭代、局部重试、多层嵌套的控制结构。

### 3.2 Loop Engineering 的三个原则

1. **轨迹可恢复**：每一步的执行状态持久化到磁盘，进程崩溃后从磁盘恢复，不回放历史
2. **局部容错**：一个子任务的失败不传播到父循环——通过 loop 嵌套形成隔离边界
3. **独立验证**：完成与否不由执行者自己说了算，由独立子代理审计

### 3.3 与动力系统的类比

Mission Driver 的设计灵感来自 AGE（Attractor-Guided Engineering）理论：

```
状态空间 (State Space)  = 仓库/项目的所有可能状态
吸引子 (Attractor)      = docs 文档体系（design/architecture/context 等规范化文档）
                          定义"系统应长期收敛到什么稳定结构"
轨迹 (Trajectory)      = plans + logs + audits 记录的"怎么走到现在的"
控制 (Control)         = CHECK + CLOSURE_AUDIT + DEEP_AUDIT 校正偏离
```

**关键区分：roadmap 不是吸引子。** Roadmap 是人类可阅读、可控制的宏观规划——它定义"下一步做什么"，是吸引子的任务化投影。真正的吸引子是 docs 中的文档体系，特别是 design 等规范化文档——它们定义"系统应该是什么样子"，变化速度远慢于 roadmap（季度级 vs 周级）。

引擎的任务就是：让系统状态沿着轨迹收敛到吸引子，并在偏离时施加校正力。

---

### 3.4 与 Codex goal 的对比

2025-2026 年涌现了不少"agent loop"方案：OpenAI Codex 的 goal 模式、Claude Code 的 `/goal` 命令、社区的 Ralph Loop 等。下面聚焦有源码可验证的 Codex goal（基于 `codex-rs/` 源码分析），与 Mission Driver 做对比。

### Codex goal 与 Mission Driver 的结构性对比

> 基于 Codex `codex-rs/` 源码分析。Codex 有比较完善的机制（SQLite 状态库、goal 6 态状态机、pause/resume、token 预算），但以下维度仍有结构性差距。

| 维度 | Codex goal（源码级） | Mission Driver |
|------|---------------------|----------------|
| **状态持久化** | SQLite `thread_goals` 表 + rollout JSONL。**但**运行时计量状态（token baseline、active_goal_id）仅在内存 Mutex，进程崩溃即丢失；rollout 文件可能未物化（deferred materialization），崩溃前未 flush 的 items 全丢 | checkbox 磁盘持久化 + run-state.json，**所有状态都在磁盘**，无损恢复 |
| **独立验证** | continuation prompt 要求模型自审完成度，但 update_goal(complete) 仅写 DB 不检查客观状态。/review 子代理存在但**不参与 goal 完成判定** | CLOSURE_AUDIT **强制**由独立子代理执行（不同 session、无共享上下文），不通过不能 close |
| **任务粒度** | 每 thread 至多 **1 个 goal**（`thread_goals` 表 ON CONFLICT(thread_id)），是单一目标 + token 预算，不是任务清单 | **多 plan 并行**：plans/ 目录可有多个 active plan，引擎逐个执行 |
| **异步交互** | 可 pause/resume **同一线程**（修改 status=Paused → 恢复时 reload rollout）。**但无法异步注入新任务**——外部不能向运行中的 goal 添加 plan | 用户随时往 plans/ 目录塞 plan，引擎下轮 REVIEW_PLANS 自动拾取，**无需暂停当前执行** |
| **失败隔离** | 单 goal 内失败污染整个 context window；V2 多 agent 有线程隔离但非 plan 级 | 子流隔离：一个 plan 失败不影响其他 plan |
| **终止保障** | 有 token 预算（无 time 预算，`time_used_seconds` 仅计量上报不触发停机）、blocked 检测。blocked 有两条路径：模型经 update_goal 自报需 3 轮重复阻塞；系统在 turn 不可重试错误时**强制**置 blocked（不经 3 轮判定）。前者无外部强制计数 | 多层防线（重试预算、死循环检测、看门狗、磁盘恢复等），全部外部强制 |
| **信息外部可见性** | SQLite 和 rollout 外部可读，但需 sqlite 客户端 + 解析 JSONL；**运行时实时状态外部不可见**（内存 Mutex） | 所有状态都是**文件**（plan .md + run-state.json），cat 即可读 |

### METR 研究的实证

METR 的研究表明，大量通过自动化测试的自主 agent PR 仍需显著人工修正才能合并（来源：METR developer productivity study, Becker et al., arXiv:2507.09089, July 2025；以及 daviddaniel.tech 的自主 agent 研究综述, February 2026）。这说明"通过测试"远不等于"可用"。

Ralph Loop 社区的实践也印证了这一点：VentureBeat 2026 年 1 月报道的"$50,000 合同 $297 API 成本完成"案例，以及 Anthropic 用 16 个并行 Claude 实例构建 C 编译器的实验（通过 99% GCC torture suite），社区审查者均指出产出代码**"能跑但不可维护"**。

原因在于缺乏独立审计层——agent 既是执行者又是验证者，没有外部控制结构来约束质量标准。

---

### 3.5 吸引子的形成生命周期

吸引子不是一开始就完整的。它有一个从模糊到精确的形成过程：

```
一句话需求 ("帮我做 X")
    │
    ▼ Grill Me（结构化需求澄清，从用户获取信息）
    │ "什么维度？什么深度？什么约束？什么成功标准？"
    │ → 信息充分后输出 clarified spec
    │
    ▼ 吸引子雏形
    │ docs/ 下创建初始 design 文档（粗粒度）
    │ 同时生成 roadmap（任务化投影）
    │
    ▼ Roadmap 初期：吸引子细化阶段
    │ roadmap 的前几个工作项是"调研竞品"、"分析现有方案"等
    │ 这些工作的产出反哺 docs/ → 吸引子逐步精确
    │ 吸引子越精确 → 后续执行越收敛
    │
    ▼ Roadmap 中期：执行阶段
    │ 按 roadmap 工作项顺序执行
    │ 每个 plan 执行后被审计 → 轨迹向吸引子收敛
    │
    ▼ Roadmap 定期插入：审计/重构/再思考
    │ roadmap 中可以插入 audit-only 或 refactor 工作项
    │ 这些过程的产出可以修正 roadmap 或补充 design 文档
    │ → 吸引子自我修正
    │
    ▼ 收敛完成
    │ roadmap 全部 done + maxAuditRounds 耗尽
    │ 吸引子（docs 文档体系）与实现一致
```

**整个 roadmap 全自主执行，不需要人类交互。** 人类的影响通过两个通道施加：
1. **执行前**：Grill Me 澄清 + 初始 roadmap 设定（此时吸引子雏形形成）
2. **执行中**：异步注入 plan 到 plans/ 目录（下一节详述）

---

### 3.6 异步人机交互：plans 目录作为消息队列

Mission Driver 执行过程中**不需要人类实时交互**。但人类可以随时通过 plans 目录异步注入新任务或修正：

```
Mission Driver 正在自主运行...
    │
    │  用户（或另一个 AI agent）发现需要修正
    │  → 生成一个新 plan 文件
    │  → 放入 plans/ 目录
    │
    ▼ 下一轮 REVIEW_PLANS 步骤
    │  引擎扫描 plans/ 目录
    │  发现新 plan
    │  如果 Status: draft → 独立子代理审查 → 提升为 active
    │  如果 Status: active → 直接进入 EXEC_PLANS 队列
    │
    ▼ EXEC_PLANS 按顺序执行
    │  原有 plan 继续执行
    │  新注入的 plan 在队列中被拾取执行
    │  如果新 plan 是修正之前错误的 → 自然覆盖之前的结果
```

### "不打断"原则

**默认情况下，即使当前 AI 执行的内容可能有误，也优先不立刻打断。** 原因：

1. **打断 = 丢失上下文**：中断正在运行的 agent 会丢失它的推理上下文，恢复成本高
2. **错误会被审计捕获**：CLOSURE_AUDIT 和 DEEP_AUDIT 会发现执行结果的问题
3. **修正可以排队**：直接插入一个后续修正 plan，引擎会在当前 plan 完成后执行修正
4. **plan 可以是 draft 状态**：不需要写成完美的 plan——引擎的 REVIEW_PLANS 会自动审查 draft plan，发现问题后改进再提升为 active

**例外条件**（以下情况应立即打断）：
- 执行涉及破坏性操作（删除文件、覆盖远程分支、发送不可逆请求）
- token 预算已告警且执行方向明显错误
- 明显的死循环或重复失败（引擎自身的 ping_pong 检测通常会先拦截）

默认不打断的思路类似于 log-structured 系统——优先追加修正而非回滚重来。不过具体哪种策略更合适取决于场景：可迭代的探索性任务（代码开发、数据分析）适合追加修正；不可逆操作仍然需要事务式保护。

### 多源协作

由于 plans 目录是文件系统上的共享队列，多个贡献者可以独立写入：

- 人类开发者通过 IDE 写一个 plan 文件
- 另一个 AI agent（如 Code Review agent）生成一个修正 plan
- Mission Driver 自身的 DEEP_AUDIT 也会生成 remediation plan

所有这些 plan 都进入同一个队列，按 Status 和优先级被引擎拾取执行。

---

## 四、四层 DSL 架构

Mission Driver 由四层 DSL 组成，属于声明式系统，通过配置而非编程使用。

### 第一层：Mission Config（`missions/<name>.json`）

纯静态配置，声明"做什么、在哪做、怎么验证"：

```json
{
  "name": "medical-qa",
  "description": "从医疗论文生成 QA 训练数据集",
  "roadmapPath": "docs/backlog/medical-qa-roadmap.md",
  "plansDir": "docs/plans/medical-qa",
  "commands": {
    "test": "python scripts/check_quality.py --min-records 500",
    "typecheck": "python -c \"import dataflow\" && echo OK"
  },
  "promptsDir": "missions/prompts/data-processing"  // 任务类型级 prompt 目录
}
```

关键设计：
- **纯静态**：无运行时字段。运行时状态在 `_tmp/<runId>/run-state.json`
- **继承**：`"extends": "base"` 从 base 配置继承共享默认值
- **命令即门控**：`commands.test` 是必需字段，CHECK 和 BUILD_VERIFY 都会运行它；`build`/`lint`/`typecheck` 可选，缺失则对应步骤跳过

### 第二层：Flow 定义（`flows/<name>.json`）

状态机 DSL，声明"步骤怎么编排"。内置 flow 是：

```
CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → [DEEP_AUDIT] → loop
```

支持：
- **子流组合**：EXEC_PLANS 内部启动 `plan-execution` 子流
- **条件跳过**：某条件不满足时跳过对应步骤
- **项目级覆盖**：在 missions 目录下放一个 flow 定义文件就覆盖内置流
- **完全自定义**：写 `missions/flows/<custom>.json`，mission 设 `flowName: "<custom>"`

### 第三层：Plan 文件（`plans/<name>.md`）

Plan 不是任务清单，是**关闭契约**。Markdown + checkbox，核心元素如下：

```markdown
# 01 采购订单审批流接入

> Plan Status: draft                  ← draft | active | completed | superseded
> Last Reviewed: 2026-07-25
> Source: roadmap item "核心业务逻辑 M1"

## Current Baseline
- ErpPurOrder 实体已建模，CRUD 已生成
- 尚无审批逻辑，docStatus 仅有 DRAFT

## Goals
- 采购订单支持提交→审批→拒绝/通过状态流转
- 审批通过后触发库存过账

## Non-Goals
- 不做多级审批（仅单级）
- 不做审批委托

### Phase 1 - 审批状态机
Status: planned
Targets: `ErpPurOrder.xbiz`, `ErpPurOrderProcessor.java`

- [ ] 实现 submitForApproval / approve / reject 方法
- [ ] 状态校验：仅 DRAFT 可提交，仅 SUBMITTED 可审批

Exit Criteria:
- [ ] submit→approve 后 posted=true
- [ ] reject 后回到 DRAFT，可重新提交
- [ ] 单元测试覆盖所有合法/非法状态转换

### Phase 2 - 过账触发
Status: planned

- [ ] 审批通过后调用 IErpFinPostingService.post()
- [ ] 红字冲销时调用 reverse()

Exit Criteria:
- [ ] 审批通过 → 凭证生成 → 库存移动单创建（端到端）
- [ ] 冲销 → 反向凭证 → 库存回退

## Closure Gates
- [ ] 所有状态转换路径有测试覆盖
- [ ] 端到端：提交→审批→过账→冲销 完整链路跑通
- [ ] 独立子代理 closure audit 通过
- [ ] mvn test 全绿
```

**核心元素**：顶部三行状态标记（Plan Status / Last Reviewed / Source）、Goals + Non-Goals（防止 scope drift）、每个 Phase 有自己的 Status + checkbox + Exit Criteria、Closure Gates 是 plan 级关门检查。Checkbox `[x]`/`[ ]` 是机器可读的持久化状态——引擎通过扫描 checkbox 决定从中断点恢复。标记 `completed` 前所有 checkbox 必须勾选。

### 第四层：Roadmap（`docs/backlog/<name>-roadmap.md`）

工作项索引——人类可阅读、可控制的宏观规划：

```markdown
# 采购到付款（P2P）路线图

## Work Items

| Status | Item | Target | Autonomy |
|--------|------|--------|----------|
| done   | ORM 建模 + codegen 首次闭环 | 10 域 145 实体 | implement |
| done   | CRUD 全域铺开 | 18 域菜单+页面 | implement |
| active | 采购审批 → 库存过账 | 端到端 P2P 打通 | plan-first |
| todo   | 供应商发票 → 三单匹配 | 三单匹配 + 自动核销 | plan-first |
| todo   | 付款 → 银行对账 | 付款单 + 对账 | plan-first |
| planned| 多公司多账套 | 多公司行为 | ask-first |

## Milestones

- [x] M1: 核心域 CRUD 全绿（18 域）
- [ ] M2: P2P 端到端（采购→入库→发票→付款）
- [ ] M3: O2C 端到端（报价→订单→出库→收款）

## Dependencies

```mermaid
graph LR
  CRUD --> 审批过账 --> 三单匹配 --> 付款对账
```
```

**核心元素**：Work Items 表（Status / Item / Target / Autonomy 四列）、Milestone 里程碑、Dependency 依赖图。引擎的 DRAFT_PLANS 读第一个 `todo`/`active` 项起草 plan。完成后变 `done`，引擎进入下一个。`Autonomy` 列控制该工作项的自主级别（implement / plan-first / ask-first）。

---

## 五、内置流程：三层 Loop 嵌套与容错

这是 Mission Driver 最核心的设计。

### 5.1 三层 Loop 全景

```
┌─────────────────────────────────────────────────────────────────┐
│  Level 1: 顶层永续循环                                           │
│                                                                 │
│  CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → (loop)      │
│                                        ↓                        │
│                                   (nothing to draft)            │
│                                        ↓                        │
│                                   DEEP_AUDIT                    │
│                                        ↓                        │
│                                   (creates new plans)           │
│                                        ↓                        │
│                                   REVIEW_PLANS (re-enter)      │
│                                                                 │
│  终止条件: roadmap 全部 done + maxAuditRounds 耗尽              │
│  容错: 任何子步骤失败都不影响这个循环的继续运行                  │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       │ EXEC_PLANS 对每个 active plan 启动子流
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Level 2: Plan-Execution 子流（每个 plan 独立一个）              │
│                                                                 │
│  EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY │
│                                                                 │
│  终止条件: BUILD_VERIFY 通过 或 重试 3 次仍失败                  │
│  容错: plan-001 失败不影响 plan-002 的执行                      │
│  隔离: 每个 plan 是独立的子流，状态独立持久化                    │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       │ DEEP_AUDIT 内部的审计循环
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Level 3: Deep-Audit 子流                                       │
│                                                                 │
│  CHECK_OPEN_AUDITS → MULTI_AUDIT → OPEN_AUDIT → SCAN_NEW_RESULTS│
│                                                                 │
│  发现质量问题 → 起草 remediation plan → 回到 Level 1 执行       │
│  容错: 审计是独立子代理执行，与执行者无上下文共享                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 局部容错：一个 plan 失败时发生什么

假设有 3 个 active plan。plan-002 的 EXECUTE 步骤失败了：

```
EXEC_PLANS 开始:
  ├── plan-001 子流:
  │     EXECUTE ✓ → CLOSURE_SCRIPT_CHECK ✓ → CLOSURE_AUDIT ✓ → BUILD_VERIFY ✓
  │     → plan-001 completed ✓
  │
  ├── plan-002 子流:
  │     EXECUTE ✗ (脚本报错)
  │     → 重试 1: 新 session 执行 EXECUTE ✗
  │     → 重试 2: 新 session 执行 EXECUTE ✗
  │     → 重试 3: 新 session 执行 EXECUTE ✗
  │     → 重试耗尽，标记 plan-002 的 checkbox 为未完成
  │     → plan-002 保持 active 状态（不是 completed）
  │     → 记录失败原因到 run-state.json
  │
  ├── plan-003 子流:
  │     EXECUTE ✓ → CLOSURE_SCRIPT_CHECK ✓ → ... → BUILD_VERIFY ✓
  │     → plan-003 completed ✓
  │
  → EXEC_PLANS 完成，回到顶层循环
  → DRAFT_PLANS: 读 roadmap，没有新 todo
  → DEEP_AUDIT: 扫描发现 plan-002 失败
  → 起草 plan-004: 修复 plan-002 的问题
  → REVIEW_PLANS → EXEC_PLANS: 执行 plan-004
  → ...
```

plan-002 的失败完全没有影响 plan-001 和 plan-003。这就是 loop 嵌套带来的局部容错——失败被限制在子流内部，不传播到兄弟子流或父循环。

### 5.3 稳定保障：多层防线

| 防线 | 机制 | 作用 |
|------|------|------|
| **磁盘持久化** | 所有状态写磁盘（checkbox、run-state.json、plan 文件） | 进程崩溃 → 重启 → 磁盘扫描 → 从断点恢复 |
| **子流隔离** | 每个 plan 是独立子流，失败不传播 | 局部错误不会扩散到其他 plan |
| **重试预算** | 每步最多重试 3 次，每次全新 session | 避免同一上下文下的重复失败 |
| **死循环检测** | ping_pong 检测（两步之间振荡）+ max_cycles + max_total_steps | 防止无限循环 |
| **看门狗** | 60 分钟子 agent 超时 | 防止 agent 挂死 |
| **独立审计** | CLOSURE_AUDIT 用不同子代理（无共享上下文） | 防止执行者自欺欺人 |

### 5.4 恢复机制：不是回放，是扫描

Mission Driver 的恢复**不是 replay**（不重新执行历史步骤），而是**disk scan**（扫描磁盘上的持久化标记）：

```
进程崩溃 → 用户重新运行 ./ai-dev/tools/mission-driver.sh run <name>

引擎启动:
  1. 扫描 plansDir 的 .md 文件
  2. 找到 Status: active 的 plan
  3. 读 checkbox: [x] = 已完成(跳过), [ ] = 未完成(从这里恢复)
  4. 找到 Status: draft 的 plan
  5. 在 REVIEW_PLANS 中审查并提升
  6. 继续正常循环

run-state.json 的 steps[] 历史仅用于审计查看，不驱动恢复。
```

这比 replay 机制更可靠——replay 需要完整的历史日志且保证幂等性；disk scan 只需要当前磁盘状态正确。

### 5.5 为什么 Loop 嵌套比 Pipeline 更适合 AI 任务

Pipeline（如 CI/CD 流水线）假设每一步是确定性的、可重复的。但 AI 步骤是**概率性的**——同一个 prompt 在不同 session 可能产生不同结果。

Loop 嵌套的优势在于：

1. **失败是预期的**：AI 步骤可能失败，loop 天然支持重试和跳过
2. **质量是迭代的**：第一轮结果可能不够好，audit 发现后可以改进
3. **恢复是自然的**：checkpoint 就是磁盘上的 checkbox，不需要额外的事务日志
4. **隔离是结构性的**：子流边界 = 容错边界，不需要 try-catch 代码

---

### 5.6 Plan Loop：内层 Harness 与"生成-验收分离"

前文描述的 Mission Driver Loop 是外层编排。每次 plan 的执行本身也有一个内部控制循环——Plan Loop。理解两层 Loop 的嵌套关系，才能理解为什么 Mission Driver 能做到全自主执行。

### Plan 的生命周期

```
draft → 独立草案审查（fresh session）→ active → 执行（含验证）→ 独立结束审计（fresh session）→ completed
```

Plan 不是任务清单，是**关闭契约**。它包含：
- **Current Baseline**：从仓库读取的当前状态，不依赖对话记忆
- **Goals + Non-Goals**：明确做什么和**不做什么**
- **Exit Criteria**：可观察的完成条件
- **Closure Gates**：最终验证检查清单

### 生成与验收分离

Plan Loop 的核心原则：生成与验收必须分离。

- 草案审查和结束审计都由**独立子代理在全新会话中执行**
- 审计者不继承执行者的上下文——从零开始读仓库
- 这是从 Human In The Loop（人在环中）升级到 Human On The Loop（人在环上）的前提
- AI 自主执行完整一轮，不等人，不自验

### 两层 Loop 的嵌套关系

```
Mission Driver Loop（天/周级编排）
  │
  ├── CHECK（健康检查）
  ├── REVIEW_PLANS（拾取 draft plan → 独立审查 → 提升 active）
  ├── EXEC_PLANS
  │     └── 对每个 active plan，启动 Plan Loop：
  │           EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY
  │           ↑ 这里的 CLOSURE_AUDIT 就是 Plan Loop 的"独立结束审计"
  ├── DRAFT_PLANS（读 roadmap → 起草新 plan → 独立草案审查 → active）
  └── DEEP_AUDIT（空闲时深度审计 → 生成修正 plan → 回到 REVIEW_PLANS）
```

Plan Loop 控制单次变更的质量，Mission Driver Loop 控制编排的自动化。两层嵌套使得每次变更有契约、全局编排自动化。

### 验证体系：脚本检测 + AI 自动改进

Mission Driver 的验证不是单纯的机械检查或 AI 审计，而是两者的配对协作——**脚本自动执行检测，发现问题后 AI 自动诊断并修复，然后重新检测**。

**plan-execution 子流的控制流**：

```
EXECUTE (agent: AI 执行 plan 内容)
  ↓
CLOSURE_SCRIPT_CHECK (script: 检查 checkbox 全勾选 + Closure evidence)
  ├── pass → BUILD_VERIFY
  └── fail → CLOSURE_AUDIT (agent: 独立子代理审查问题)
               ├── 发现可修 → 回 EXECUTE (AI 修复后重新走检测)
               └── 不可修 / 重试耗尽 → plan 失败
BUILD_VERIFY (agent: AI 运行 test/build/lint/typecheck 命令)
  ├── 命令失败 → AI 自动诊断原因 → 修复代码 → 重新运行命令
  ├── git hook 拒绝 → 同上，AI 修复后重新提交
  └── 全部通过 → plan completed ✓
```

BUILD_VERIFY 是一个 agent 步骤，当 test/build/lint 失败时，AI 会自动分析失败原因、修复代码、重新运行命令，只有修复重试耗尽才真正失败。CHECK 步骤在 mission 启动时确认项目从可编译、测试通过的基线开始，发现问题时同样由 AI 自动诊断和修复，最多重试 3 次，每次使用全新 session 避免上下文污染。

| 检查点 | 检测方式 | 发现问题后 |
|--------|---------|-----------|
| **CHECK** | 确认项目处于可编译、测试通过的基线状态（运行 commands.test/typecheck/build） | AI 自动诊断并修复（最多 3 次全新 session） |
| **CLOSURE_SCRIPT_CHECK** | script 调用 inspectPlan 检查 checkbox + evidence | 进入 CLOSURE_AUDIT（独立 AI 审查）→ 回 EXECUTE 修复 |
| **BUILD_VERIFY** | AI 运行 test/build/lint/typecheck 命令 | AI 自动诊断失败原因 → 修复代码 → 重新运行 |
| **DEEP_AUDIT** | AI 多维度审计 + 对抗审查 | 生成 remediation plan → 回 REVIEW_PLANS 执行修复 |

每个检查点都是"脚本/命令做客观检测 + AI 做自动改进"的配对。脚本保证检测的客观性——test 命令退出码非 0 时 AI 不能声称"测试通过了"。AI 保证修复的灵活性——不只是报错退出，而是主动分析原因并修复。两者分工明确：机器负责裁判，AI 负责改进。

与 Codex 对比：Codex 的 update_goal(complete) 仅写 DB 不检查客观状态，也没有失败后自动修复的循环。Mission Driver 的每个验证点都是"检测→失败→自动改进→重新检测"的闭环。

---

### 5.7 实证：nop-app-erp——22 天 154 模块的 AI 自主开发

nop-app-erp 项目提供了一个可公开审计的案例：三层 Loop 如何驱动 AI 在 22 天内从空骨架产出产品级 ERP。

### 规模指标（经独立审计校准）

| 维度 | 数值 |
|------|------|
| 开发周期 | 22 天（06-22 ~ 07-13） |
| 业务域 | 18 + 1 个跨域通知派发子系统 |
| Maven 模块 | 154 个 |
| 自有实体 | 352 个 + 110 个跨域引用桩 |
| Java 测试 | ~2890 个（0 failures / 0 errors） |
| Playwright E2E | 260+ spec（0 回归） |
| Plan 文件 | 187 份（全部经过双审计） |
| 审计记录 | 9 份（从单代理演进到 4 路对抗性） |

### 一次真实的 Mission Driver 循环（07-10）

```
08:00 CHECK     — mvn 全绿
08:05 REVIEW    — 4 个 draft plan → 独立审查 → all active
09:30 EXEC      — 4 个 plan 全部执行完毕，全绿通过
16:00 DRAFT     — 路线图工作项全部 done
16:05 DEEP_AUDIT — 自动启动深度审计（不是停止，是升级）
```

一次循环约 8 小时，处理 4 个 plan。整个路线图横跨 22 天，由数十次这样的循环完成遍历。

### Plan Loop 的编码前缺陷拦截

07-10 的一个 plan 批次（销售定价引擎），独立草案审查拦截了 **4 个 P0 缺陷**：
- 码值冲突（不同业务类型使用了相同的字典码值）
- BUDGET 污染实际财务（预算科目混入实际凭证）
- GlBalance 架构前提错误（过账前提条件不满足）
- 维度歧义（定价维度的语义不一致）

**全部在编码前拦截。** 如果没有 Plan Loop，这 4 个缺陷会在实施甚至运行阶段才暴露，修复成本高 5-10 倍。

### 知识转移曲线

这是整个案例中最重要的洞察。

项目中用户的介入分为三类：
- **A 类**（明确指明平台机制）：集中在项目早期，用户充当"平台知识权威"
- **B 类**（指明工程原则方向）：给出原则或质疑，但不给具体答案
- **C 类**（只让 AI 自查对比）：绝大多数，项目中后期为主

```
06-22  AAAAAAAAAAA  A 类密集 + B + C 起步
06-29        CCCCCCCCCC  C 类（grill 83 问，仅 4 题需纠正）
07-01  AA    BB      最后两次 A 类 + 两次关键 B 类
07-04+         CCCCCCCCCCCC  几乎全 C 类
```

两条曲线在 06-29 ~ 07-01 交叉——此后 AI 自主成为主要工作模式。

**核心结论**：

> "用户后期介入归零不是因为 AI 学会了写代码，而是因为**吸引子已经定义好了**。方向对了，AI 能自动扩张。"
>
> "用户不是在写代码，而是在**转移平台知识**。一旦知识转移完成，他的介入频率自动归零。"

这条曲线揭示了 AGE 的本质：人的注意力应该花在定义吸引子上，而不是监督执行。Mission Driver 做的是后者。

---

## 六、全面可定制性

Mission Driver 的设计哲学是：引擎通用，定制通过配置和 prompt 完成，不改代码。

### 6.1 三层定制点

| 定制层 | 机制 | 示例 |
|--------|------|------|
| **Prompt 覆盖** | `missions/prompts/<name>.md` 覆盖内置 prompt | 数据处理场景的 EXECUTE prompt 说"调脚本"而非"改代码" |
| **Flow 覆盖** | `missions/flows/<name>.json` 覆盖内置 flow | 增加一个合规审计步骤 |
| **Commands 配置** | mission.json 的 `commands` 字段 | test = 质量检查脚本，typecheck = schema 验证 |

### 6.2 `promptsDir`：任务类型级 Prompt 集

当前 prompt 覆盖通过 missions 目录下的 prompts 子目录实现（项目级覆盖内置默认）。`promptsDir` 配置在此基础上增加一层任务类型级的 prompt 选择能力，允许不同 mission 指向不同的 prompt 子目录，实现同一引擎、不同 prompt 集：

```json
{
  "name": "dataflow-vs-nop-stream-analysis",
  "promptsDir": "missions/prompts/analysis",
  "flowName": "mission-driver"
}
```

引擎 prompt 加载链：
```
① <promptsDir>/<name>.md         ← 任务类型级（最高优先）
② <missionsDir>/prompts/<name>.md ← 项目级
③ tools/mission-driver/prompts/<name>.md  ← 内置默认
```

向后兼容：不设 `promptsDir` 时行为不变。部分覆盖：某个 prompt 在 `promptsDir` 中不存在时自动回退到上级。

预设的 prompt 集目录：

```
missions/prompts/
  code-dev/           ← 代码开发（改代码、跑测试）
  data-processing/    ← 数据处理（调脚本、检查数据质量）
  analysis/           ← 对比分析（读文档、生成对比表格）
  research/           ← 调研（搜索、阅读、总结）
```

### 6.3 不需要定制的部分

- **Flow**：内置的 CHECK→REVIEW→EXEC→DRAFT→AUDIT 适用于几乎所有场景。只有需要增加/删除步骤时才覆盖。
- **Plan 格式**：Phase + checkbox + Exit Criteria 是通用结构，不需要改。
- **恢复机制**：checkbox 磁盘扫描是通用的，不需要改。
- **监控**：内置 dashboard（端口 9300）是通用的。

---

## 七、超越代码开发：通用应用场景

Mission Driver 最初为代码开发设计，其 flow 结构**可推广到**其他场景——但需要注意：内置步骤的语义绑定偏向代码开发（`commands.test` 是必需字段，BUILD_VERIFY 假设有编译/测试步骤）。非代码场景需要将 `commands.test` 映射为场景对应的质量检查脚本（如数据质量断言、分析完整性检查）。

**适用边界**：Mission Driver 适合 **>1 小时、有明确验收标准、需要多步迭代** 的复杂任务。不适合快速修复（<30 min）、一次性脚本、探索性原型（参见 SKILL.md 的 Decision 表）。

以下场景在配合自定义 prompt 和 commands 后可行：

### 场景一：数据处理 Pipeline

```
roadmap: "生成医疗 QA 数据集 v1 (≥500 条)"
  → DRAFT: 读 scripts/index.md 了解可用工具 → 拟制 pipeline 方案
  → EXEC: 调预置脚本 (extract_text.py → generate_qa.py → filter_quality.py)
  → AUDIT: 数据质量断言 (行数、空值率、schema)
  → 多轮: audit 发现质量低 → 改进 prompt → 重新生成
```

### 场景二：文档对比分析

```
roadmap: "对比 DataFlow 和 nop-stream 的架构差异"
  → DRAFT: 拟制分析维度 (执行模型/并行/容错/状态管理)
  → EXEC: 读源码+文档，按维度生成对比表格
  → AUDIT: 检查分析覆盖度和深度
  → 多轮: audit 发现遗漏 → 补充维度 → 重新分析
```

### 场景三：技术调研

```
roadmap: "调研主流 AI Agent 框架的差异"
  → DRAFT: 拟制调研提纲 (架构/能力/生态/适用场景)
  → EXEC: 搜索+阅读+总结每个框架
  → AUDIT: 检查覆盖度和结论可靠性
  → 多轮: audit 发现对比不公平 → 调整标准 → 重新评估
```

### 场景四：代码开发（原始场景）

```
roadmap: "重构 auth 模块，迁移到 OAuth2"
  → DRAFT: 读设计文档 → 拟制代码变更 plan
  → EXEC: 改代码
  → AUDIT: 跑测试 + lint + 代码审查
  → 多轮: 测试失败 → 修复 → 重新验证
```

四个场景的区别只在 prompt 集和 commands（验证命令）。Flow 引擎、plan 格式、恢复机制、审计循环全部相同。

---

## 八、实践指南

### 8.1 核心理念：智能就是循环反馈改进

Mission Driver 的有效性不来自单次 AI 调用的质量，而来自**循环反馈改进**。每一轮执行都会产生反馈（测试结果、审计发现、人工纠正），这些反馈被记录下来，用于改进下一轮的提示词和规范。

具体而言，有两条改进轨道在并行运行：

**做事的提示词**：DRAFT_PLANS 和 EXECUTE 的 prompt。当 AI 生成的 plan 或代码被人工纠正时，纠正记录被另一个子 agent 分析，提取出通用的编码规范和方法论，写入 skill。下次 AI 自动加载这些 skill。

**检查的提示词**：CLOSURE_AUDIT 和 DEEP_AUDIT 的 prompt。当审计遗漏了某个问题（事后发现），补充检查维度到审计 prompt 中。nop-app-erp 的审计体系就从单代理逐步演进到 4 路对抗性，正是检查提示词不断强化的过程。

两条轨道的共同原则：**AI 生成第一版，人工修改的历史要记录下来，修改意见用于完善提示词。** 随着循环轮次增加，做事的提示词越来越精准（减少犯错），检查的提示词越来越全面（不漏问题），系统整体质量逐步提升。

### 8.2 起步流程

**第一步，下载 AGE 模板，让 AI 阅读并适配。** AGE 模板包含了 AGENTS.md 路由规则、docs/ 文档体系骨架、missions/ 配置目录、plan 编写指南。把模板交给 AI，让它阅读后适配到你的项目——调整文档结构、配置 commands（test/build/lint）、初始化 roadmap。AGE 模板初始化后，项目自动具备操作日志记录和 plan 审计流程。

**第二步，把测试基础设施准备好。** Mission Driver 的 BUILD_VERIFY 步骤会运行 commands.test，所以项目必须有可执行的测试命令。Node.js 项目确保 `npm test` 可跑，Java 项目确保 `mvn test` 可跑，E2E 测试确保 Playwright 已配置。没有测试，验证体系就无法运转。

**第三步，日常使用中积累 plan 和 log。** 不需要一开始就启动 mission-driver。平时与 AI 对话完成工作时，要求 AI 按照 plan guide 拟制计划到 plans 目录下。AGE 模板会自动记录操作日志。当 plans 目录积累了足够的计划后，再启动 mission-driver 自动编排。

**第四步，启动 mission。**

```bash
./tools/mission-driver.sh run <mission-name>
```

mission-driver 会自动扫描 plans 目录，拾取 draft 状态的 plan，审查、执行、审计、推进 roadmap。

### 8.3 标准工作步骤

一个完整的任务从问题到交付，经过以下五步：

```
1. 分析
   写分析报告，用独立子 agent 反复审查改进直到达成共识
   产出：对问题的结构化理解、可选方案、推荐路径

2. 计划拟制
   根据分析报告，按照 plan guide 拟制 plan 到 plans 目录
   plan 包含 Goals / Non-Goals / Exit Criteria / Closure Gates

3. 自动审查
   plan 拟制完毕后，REVIEW_PLANS 步骤自动启动独立子 agent 审查
   审查通过则提升为 active，未通过则退回修改

4. 执行
   EXECUTE 步骤执行 plan 内容
   之后 CLOSURE_SCRIPT_CHECK 检查 checkbox 完整性
   BUILD_VERIFY 运行测试和构建，失败时 AI 自动修复

5. 审计关闭
   用独立子 agent 审计关闭（CLOSURE_AUDIT）
   独立于执行者，从零开始读仓库验证
   通过后 plan 标记 completed
```

这五步不是可选项。跳过分析直接编码，跳过审查直接执行，跳过审计直接关闭，都是最常见的质量事故来源。

### 8.4 修改历史的记录与提示词进化

当 AI 生成的产出被人工修改时，不要只改当前文件。完整流程是：

```
AI 生成第一版
  → 人工修改（修改过程被 log 记录）
  → 独立子 agent 对比原始版本和修改后版本
  → 提取"为什么改"的通用规则
  → 写入 skill 或编码规范
  → 下次 AI 自动加载
```

这个循环适用于所有 AI 产出：

| 产出类型 | 修改历史如何转化为提示词 |
|---------|------------------------|
| Plan 草案 | 纠正 scope 过宽/过窄的模式 → 写入 DRAFT prompt |
| 代码 | 纠正编码模式偏差 → 写入 skill 的反模式清单 |
| E2E 测试 | 纠正选择器/断言错误 → 写入测试编写规范 |
| 审计报告 | 补充遗漏的检查维度 → 写入审计 prompt |

nop-app-erp 项目积累了 19 个可复用 skill，全部来自这个"修改→提取→固化"的循环。初期人工纠正频繁，后期随着 skill 积累，AI 产出质量逐步稳定。

### 8.5 E2E 测试的 AI 自动生成

E2E 测试是验证体系的重要组成，但手写成本高。可以让 AI 自动生成，关键是降低 AI 编写 E2E 的难度。

在 AMIS 前端中可以封装 PageObject 模式，提供 `getFieldValue(containerLocator, fieldName)` 这类简化操作，让 AI 不需要处理复杂的 DOM 选择器。AI 只需要知道"在哪个容器里取哪个字段"，不需要知道具体的 CSS selector。

然后就是 §8.4 的循环：AI 生成 → 人工纠正 → 提取规则 → 写入 skill → 下次改进。nop-app-erp 的 Playwright E2E 从 0 到 260+ spec，初期人工纠正频繁，后期基本自动生成。

### 8.6 时间尺度认知

Mission Driver 的操作尺度不是分钟级，而是天到周级。一次完整的 mission 循环通常需要 4-12 小时。一个 roadmap 横跨数天到数周。

- 启动 mission 后不需要盯着看。它在后台自主推进，你可以做其他事。
- 中间结果通过 plan 文件、audit 文件、log 文件持久化，随时查看。
- 如果方向有误，不需要打断，插入一个修正 plan 即可。
- 整个 roadmap 完成后，DEEP_AUDIT 会自动做质量总检查。

### 8.7 相关概念框架

Mission Driver 的设计与其他几个概念框架有交集：

**Harness Engineering**：构建控制 AI 行为的"线束"（harness）——plan 审计、验证命令、质量门控。Mission Driver 的 plan-execution 子流（EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY）就是一个 harness。

**Loop Engineering**：用循环结构替代线性 pipeline 来驱动 AI 任务。Mission Driver 的三层 loop 嵌套是 Loop Engineering 的一种实现。

**SDD（Spec-Driven Development）/ OpenSpec**：先写规格再写代码的开发模式。Mission Driver 的 plan（Goals / Non-Goals / Exit Criteria / Closure Gates）本质上就是一份执行规格，plan guide 约束了规格的格式。

这三者的交集正是 Mission Driver 的定位：用 Loop Engineering 的循环结构，在 Harness 的控制下，按照 SDD 的规格驱动 AI 工作。

---

## 九、Goal-Driven 愿景

Mission Driver 的演进方向是一个 goal-driven 的 AI 系统——用户说目标，系统自动澄清、规划、执行、验证。

```
用户: "帮我分析 DataFlow 和 Flink 的区别"
    │
    ▼
┌─────────────────────────────────────────┐
│  Grill Me (需求澄清)                     │
│  "分析哪些维度？什么深度？产出什么格式？"  │
│  → 用户回答 → 输出 clarified spec        │
└──────────────┬──────────────────────────┘
               ▼
┌─────────────────────────────────────────┐
│  Auto-Detect (任务类型检测)              │
│  "分析/对比" → promptsDir = analysis     │
└──────────────┬──────────────────────────┘
               ▼
┌─────────────────────────────────────────┐
│  Generate (自动生成)                     │
│  → roadmap.md (分析维度 + 成功标准)      │
│  → mission.json (含 promptsDir)          │
└──────────────┬──────────────────────────┘
               ▼
┌─────────────────────────────────────────┐
│  Run (自动执行)                          │
│  → mission-driver 自动循环执行           │
│  → 直到 roadmap 完成                     │
└─────────────────────────────────────────┘
```

其中部分步骤已有实现基础：
- **Generate**（roadmap + mission 生成）：mission-driver 的 `draft` 命令
- **Run**（自动循环执行）：mission-driver 的 `run` 命令
- **Grill Me**（面向 mission 的结构化需求澄清）：deep-interview skill 已有，增强为输出结构化 mission spec 即可
- **Task Type Detection**（自动选择 promptsDir）：简单的分类逻辑
- **Wrapper**（串接四步的入口层）：薄包装层

在现有 `draft` + `run` 基础上补 Grill Me 增强和 wrapper 即可闭环。

---

## 十、快速上手

> **引擎部署说明**：Mission Driver 的启动脚本路径因项目而异。在 AGE Template 原始项目中位于 tools/mission-driver.sh，在 nop-entropy 等衍生项目中位于 ai-dev/tools/mission-driver.sh（代理脚本，指向外部引擎）。使用前确认脚本位置和 MISSION_DRIVER_HOME 环境变量。

### 创建第一个 mission

```bash
# 1. 生成 mission 配置 + roadmap
./ai-dev/tools/mission-driver.sh draft "你的目标描述" --target-file <需求文档>

# 2. 验证（mission-check.mjs 位于引擎目录 $MISSION_DRIVER_HOME/src/ 下）
node $MISSION_DRIVER_HOME/src/mission-check.mjs missions/<name>.json .

# 3. Dry-run 验证流程编排
./ai-dev/tools/mission-driver.sh run <name> --dry-run --no-monitor

# 4. 正式运行
./ai-dev/tools/mission-driver.sh run <name>
```

### 监控运行

```bash
# 方式 1: 浏览器看板
open http://localhost:9300

# 方式 2: 读状态文件
cat _tmp/<runDir>/run-state.json

# 方式 3: 追日志
tail -f _tmp/<runDir>/<mission-name>.log
```

### 中断和恢复

```bash
# Ctrl-C 安全中断（引擎捕获信号，清理子进程）

# 重新运行即自动恢复（从磁盘扫描 plan checkbox）
./ai-dev/tools/mission-driver.sh run <name>

# 从特定步骤恢复
./ai-dev/tools/mission-driver.sh run <name> --from-step EXEC_PLANS

# 快速模式（默认跳过 DEEP_AUDIT 以加速）
./ai-dev/tools/mission-driver.sh run <name> --fast
```

### Postmortem（任务后复盘）

```bash
# 对最近一次运行做 postmortem 分析
./ai-dev/tools/mission-driver.sh analyze

# 对特定运行做 postmortem
./ai-dev/tools/mission-driver.sh analyze <runId>
```

Postmortem 扫描所有事件和日志，运行复盘 agent，将结构化报告写入 memory 目录。后续同模块的 mission 会自动加载这些经验记忆。

### 注意事项

- 不要在 mission 执行期间手动编辑正在被执行的 plan 文件（写竞争）
- 不要在同一个 opencode session 中嵌套启动 mission（mission 自身会派生子进程，嵌套会导致状态混乱）
- 启动前先手动运行一次 `commands.test` 确认基线通过，否则 CHECK 会浪费重试次数
- 如果需要修改 roadmap（增删/重排工作项），先停掉 mission（Ctrl-C），改完再重启

### 定制 prompt

当前（已实现）的 prompt 覆盖机制：将自定义 prompt 放入 missions 目录下的 prompts 子目录，引擎会优先加载它而非内置默认。

```bash
# 覆盖某个 prompt（如 execute.md）
mkdir -p missions/prompts
cat > missions/prompts/execute.md << 'EOF'
# 你的自定义 EXECUTE prompt
告诉 agent 怎么执行你的任务...
EOF
```

> `promptsDir`（按任务类型选择不同 prompt 子目录）扩展了当前的项目级 prompt 覆盖机制，支持按任务类型自动选择 prompt 集。

---

## 十一、总结

Mission Driver 的价值在于三个方面：

第一，它用持久化的、可局部重试的 loop 嵌套替代线性 pipeline。AI 步骤是概率性的，需要重试、迭代、局部容错。子流隔离让一个 plan 的失败不影响其他 plan。

第二，它通过配置而非编程实现定制。不同任务类型的区别只在 prompt（怎么想）和 commands（怎么验证）。项目级 prompt 覆盖和 commands 配置已经可用，`promptsDir` 提供任务类型级的 prompt 自动选择能力。

第三，它把所有状态放在磁盘上。checkbox 持久化 + 磁盘扫描恢复，进程崩溃后无损恢复。

它不局限于代码开发。任何需要多步迭代、质量审计、容错恢复的复杂任务（>1 小时、有明确验收标准），都可以用它驱动。这正是 Loop Engineering 作为一种工程实践的落地实现。

---

## 进一步阅读

- Mission Driver 完整文档：`.opencode/skills/mission-driver/SKILL.md`
- Mission Config Schema：`.opencode/skills/mission-driver/references/mission-config-schema.md`
- Roadmap 模板：`.opencode/skills/mission-driver/references/roadmap-template.md`
- Plan 编写指南：`ai-dev/plans/00-plan-authoring-and-execution-guide.md`
- AGE 理论深度分析：`ai-dev/analysis/2026-06-07-trellis-vs-age-comparison.md`
- DataFlow Harness 完整推演：`ai-dev/analysis/2026-07-25-opendcai-vs-age-vs-mission-driver.md`
