# Planning-with-Files 持久化规划机制深度分析 & Nop AI Agent 计划运行时

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/planning-with-files`（OthmanAdi/planning-with-files，Manus 风格持久化规划 Skill）vs `nop-ai-agent`（plan 包：静态 DSL 模型，缺运行时计划器）
> Conclusion:

## 一、总览

**Planning-with-Files** 是一个"把计划持久化到文件系统"的 Agent 技能包（v2.3.0，MIT），不接管 agent 主循环，而是通过 SKILL.md 的 hooks 配置 + 提示词规则，让"计划落盘、阶段跟踪、完成校验"成为 agent 的行为准则。它解决的核心问题是：**上下文窗口是 RAM（易失、有限），文件系统是磁盘（持久、无限）——重要的状态必须落盘**。

| 维度 | planning-with-files | Nop AI Agent |
|------|---------------------|--------------|
| 形态 | Prompt/Skill 层约定（markdown + shell/python 脚本） | Java 引擎（DSL-first，AgentModel/AgentPlan 静态模型） |
| 计划载体 | 3 个 markdown 文件（task_plan/findings/progress） | `plan.xdef` 编译的 AgentPlan bean（20 个静态模型） |
| 状态机 | `pending → in_progress → complete`（grep 字符串计数） | 无（AgentPlan 无运行时状态） |
| 注入机制 | PreToolUse hook 每次工具调用前 `cat task_plan.md \| head -30` | 无（PromptAssembly 不注入计划状态） |
| 完成校验 | Stop hook 跑 check-complete.sh（exit 1 强制继续） | CompletionJudge 规则式 + LLM 判定 |
| 会话恢复 | session-catchup.py 从 CLI 会话 jsonl 提取断点 | AgentSession 存储 + DBCheckpointManager |

**核心结论先行**：planning-with-files 是"计划层"的最佳实践参考——它的 3-File Pattern、计划注入、完成门、会话恢复四个机制，正好是 nop-ai-agent `plan` 包从"静态 DSL 模型"进化到"运行时计划器"的现成蓝图。nop 不应照抄 markdown+grep 实现（脆弱），而应把其机制升级为结构化运行时状态。

## 二、Context（调研背景）

- **为什么需要这个分析**：nop-ai-agent 的 `plan` 包目前只有 AgentPlan 静态模型（goal/tasks/phase/scope 等 20 个模型类），**没有运行时计划执行器**——Agent 是纯 ReAct，不真正"做计划"。7 月博客文章《Planning Files：长时 Agent Harness》与《planning-with-files 深度解析》连续两篇介绍该项目的持久化规划机制，与 nop plan 包的缺口直接对应。
- **要回答的问题**：planning-with-files 的哪些机制值得移植？如何把 markdown 字符串状态机升级为 nop 的结构化运行时计划器？
- **约束**：nop-ai-agent 是 Java 21 + DSL-first，Agent 通过 XDEF 装载静态配置，执行时状态独立于模型（AgentModel → Agent → AgentSession 三分离）。

## 三、项目定位与架构

### 3.1 定位：不是执行引擎，是行为准则

- 仓库中实际有 5 个脚本：`init-session.sh/.ps1`、`check-complete.sh/.ps1`、`session-catchup.py`（注：无 `inject-plan.sh`，计划注入靠 SKILL.md hook 配置）。
- 核心载体是 `SKILL.md`（frontmatter 含 hooks 配置）+ `reference.md`（Manus 6 大上下文工程原则）+ `templates/`（3 个文件模板）+ `examples.md`。
- 通过 `.claude-plugin/`、`.cursor/rules/`、`.opencode/`、`.codex/` 等多 IDE 适配分发生效。

### 3.2 3-File Pattern（核心设计）

| 文件 | 职责 | 更新时机 |
|---|---|---|
| `task_plan.md` | 路线图：Goal / Current Phase / Phases / Decisions / Errors | 每阶段结束后 |
| `findings.md` | 外部知识库：需求、研究结论、技术决策、资源 | 每次发现后（2-Action Rule） |
| `progress.md` | 会话日志：动作、文件变更、测试结果、错误 | 整个会话期间 |

哲学：**Context Window = RAM（易失），Filesystem = Disk（持久）→ Anything important gets written to disk**（README.md:97-102）。

## 四、核心机制详解

### 4.1 计划注入 = 注意力操纵（PreToolUse hook）

`SKILL.md:15-34` 定义了 4 个 hook：

```yaml
hooks:
  PreToolUse:                    # 每次 Write/Edit/Bash/Read/Glob/Grep 之前
    - matcher: "Write|Edit|Bash|Read|Glob|Grep"
      hooks:
        - type: command
          command: "cat task_plan.md 2>/dev/null | head -30 || true"  # ← 计划注入点
  PostToolUse:                   # 每次写文件之后
    - matcher: "Write|Edit"
      hooks: ...
  Stop:                          # agent 想停止时
    - hooks:
        - type: command
          command: "... check-complete.sh ..."  # 完成校验门
```

机制本质：**把 task_plan.md 前 30 行强行塞进每次工具调用前的上下文**，对抗"lost in the middle"效应（reference.md:42-54 引述 Manus 原话："Creates and updates todo.md throughout tasks to push global plan into model's recent attention span"）。这是低成本高收益的防目标漂移手段。

### 4.2 完成门 = 强制循环（Stop hook）

`check-complete.sh`（44 行）是核心状态机实现：

```bash
TOTAL=$(grep -c "### Phase" "$PLAN_FILE" || true)
COMPLETE=$(grep -cF "**Status:** complete" "$PLAN_FILE" || true)
...
if [ "$COMPLETE" -eq "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
    echo "ALL PHASES COMPLETE"; exit 0
else
    echo "TASK NOT COMPLETE"; echo "Do not stop until all phases are complete."; exit 1
fi
```

- 状态即字符串模式：只认 `**Status:** complete` 字面量。
- 循环机制：Stop hook 返回 exit 1 → agent 回到循环继续工作，直到全部 Phase 为 complete。
- 长时运行靠"人为持久循环"：`/planning-with-files` → 工作到上下文满 → `/clear` → 再 `/planning-with-files` → `session-catchup.py` 恢复断点 → 继续。**上下文清空不丢状态，靠的是磁盘三件套 + catchup 报告**。

### 4.3 会话恢复 = 断点续传（session-catchup.py）

1. 把项目路径 `/a/b/c` 映射到 Claude Code 会话存储 `~/.claude/projects/-a-b-c/`。
2. 解析 `.jsonl` 会话文件，找最后一次 Write/Edit 规划文件的**行号**。
3. 提取该行号之后的所有对话 → 输出 catchup 报告（最多 15 条）。
4. 建议 `git diff --stat` 核对后更新规划文件。

### 4.4 状态机与目标刷新

- 三态：`pending → in_progress → complete`（无 IDLE/STUCK 枚举；"卡住"用 Errors 表 + 3-Strike Error Protocol 表达）。
- 5-Question Reboot Test（SKILL.md:177-187）：Where am I? / Where am I going? / What's the goal? / What have I learned? / What have I done?——每个答案指向某个文件，构成"文件即状态"的完整可恢复性。
- 2-Action Rule：每 2 次 view/browser/search 操作后必须落盘 findings/progress。

## 四.5 Harness 可靠性（Retry/Replan/Resume）

- **完成门强制循环**（`check-complete.sh`）：Stop hook 检查 `**Status:** complete` 计数，不完整则 exit 1 强制继续——**重试直到全部 Phase 完成**。
- **3-Strike Error Protocol**：Errors 表 + 三次失败即放弃该路径（错误记忆防重复失败）。
- **session-catchup.py 断点恢复**：从会话 jsonl 提取断点（最后计划更新行号）→ 恢复上下文继续。
- **5-Question Reboot Test**：Where am I/goal/learned/done——重试前状态自检。
- **对 nop 的启示**：完成门（grep 计数 → nop 的结构化 checkComplete）+ 3-Strike 协议是 nop plan 运行时重试语义的参考；catchup 对应 nop checkpoint 恢复。

## 五、优缺点

### 优点

- **零依赖零成本**：纯 markdown + shell/python stdlib，任何 agent 环境可用（已适配 Claude Code/Cursor/Codex/OpenCode/KiloCode）。
- **人机可读**：计划文件本身就是交付物和审计日志。
- **真持久化**：上下文清空/崩溃后可通过三件套 + session-catchup 完全恢复。
- **注意力工程有效**：PreToolUse 复读是低成本高收益的防目标漂移手段。
- **错误记忆**：Errors 表 + 3-Strike 协议显著减少重复失败。

### 缺点

- **状态即字符串，无结构**：`grep -cF "**Status:** complete"` 计数即状态机——无法表达 IDLE/STUCK/阻塞/并行依赖；格式漂移即失效。
- **无运行时执行器**：依赖宿主 IDE hooks 强制循环；在无 hook 环境的 agent 中所有约束退化为提示词"建议"。
- **无调度/并发/重规划**：线性 Phase 序列，不支持并行子任务、动态重规划、超时、重试策略。
- **更新开销**：Manus 发现 ~33% 动作耗在更新 todo.md——文件式计划有同样的写入开销风险。

## 六、对 nop-ai-agent 的借鉴要点（核心价值）

nop 现状：`AgentPlan` 是静态配置 bean（goal/constraints/phases/tasks/scope/stages），**无运行时状态、无计划器、无注入机制**。以下机制可直接迁移：

### 6.1 计划模型 → 文件状态映射（A：计划运行时视图）

- `task_plan.md` 可编译为 Java 运行时对象：`PlanRun(goal, currentPhase, phases[{title, status, tasks[]}], decisions[], errors[])`，作为 AgentPlan 的**运行时视图**。
- 状态机扩展：`pending/in_progress/complete` → 顺势扩展 `IDLE/STUCK/BLOCKED`（planning-with-files 缺、nop 补的机会点）。
- 文件是事实源（source of truth），Java 内存对象是缓存——不破坏 DSL-first 的"模型即代码"理念。

### 6.2 三个可直接抄的机制

1. **计划注入（替代 PreToolUse hook）**：`ReActAgentExecutor` 每次 tool call 前把当前 Phase status + goal + 剩余 phases 序列化注入系统提示尾部——Java 里就是 executor loop 里的一行。nop 已有 `AgentPromptAssembly`，加入 `consultPlanRun()` 即可。
2. **完成门（替代 Stop hook）**：实现 `checkComplete()`——遍历运行时 Phase 列表，全部 complete 才结束循环；比 grep 更可靠（有结构化模型）。
3. **会话恢复（替代 session-catchup.py）**：把 `~/.claude/projects/*.jsonl` 换成 nop 的 `AgentSession` 存储，实现"最后计划更新点之后的消息提取 + 未同步上下文报告"。

### 6.3 需要工程化的点（planning-with-files 没有的）

- **持久化格式**：markdown 适合人类；Java 运行时建议文件头 YAML/JSON frontmatter + markdown 正文，或直接 JSON——保留可读性同时消除 grep 脆弱性。
- **把"更新计划"从自由文本动作变成有 schema 的工具调用**：新增 `updatePlan / logError` 工具（对应 Manus 从 todo.md 演进到 planner agent 的方向，reference.md:118）。
- **循环控制**：nop 需自己实现 7 步循环（reference.md:130-164），planning-with-files 只是计划层不含调度；AgentPlan 静态模型 + 文件运行时计划 = "定义与状态分离"，与 DSL-first 天然契合。
- **接入点**：给 plan 包新增 `FileBackedPlanStore`（读写三件套）+ `PlanRunner`（注入→执行→校验→重读循环），暴露 `Plan.Status` 枚举与 `checkComplete()`——即把机制从"grep 字符串"升级为"结构化运行时状态"。

### 6.4 参考对立实现

- `~/ai/agentscope-java`（HarnessAgent 的 Plan Mode：计划文件持久化并驱动执行）已有对照分析。
- `~/ai/spec-kit`（SDD 工作流引擎，RunState 持久化 + gate 步骤）是更工程化的计划执行器，见 `2026-08-01-spec-kit-workflow-engine-analysis.md`。

## 七、结论

- **planning-with-files 是"计划层"的极简参考**：3-File Pattern、计划注入、完成门、会话恢复四机制与 nop plan 包的运行时缺口一一对应。
- **nop 不应照抄 markdown+grep**（脆弱、无结构），而应把其机制升级为结构化运行时状态：`PlanRun` 模型 + `FileBackedPlanStore` + `PlanRunner` + `updatePlan/logError` 工具。
- 优先级建议：先做**计划注入**（一行代码，收益最大）→ 再做**完成门**（checkComplete + status 枚举）→ 最后做**会话恢复**（复用现有 checkpoint）。
- 后续工作：指向 `ai-dev/design/nop-ai-agent-plan-dsl.md` 的运行时扩展 + `ai-dev/plans/` 对应执行计划。

## Open Questions

- [ ] nop 的 PlanRun 状态机是否需要 BLOCKED/STUCK 枚举（面向无人值守场景）？
- [ ] 计划文件用 JSON 还是 YAML frontmatter + markdown 正文？
- [ ] updatePlan 工具与现有 ToolDispatcher 的集成方式（是否走 safety 链？）

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D2**（3-File Pattern 文件系统即状态）、**D5**（task_plan/findings/progress+完成门）、**D9**（check-complete.sh 质量门）、**D12**（3-Strike 协议+session-catchup 恢复）。缺失/薄弱：D1（依赖宿主 IDE hooks）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **计划模型**：nop 有 `AgentPlan` + 21 个静态模型类（AgentPlanPhase/AgentPlanTaskModel/AgentPlanClosure/...）+ `AgentExecStatus` 9 态，远优于 planning-with-files 的"markdown 字符串 + grep 计数"状态机。
- **持久化**：nop `DBCheckpointManager` append-only INSERT（按 watermark 检索）优于其文件覆盖写。
- **hook 体系**：nop 12 个 AgentLifecyclePoint + middleware 洋葱链，优于其依赖宿主 IDE 的 PreToolUse hook。

**必要参考的增量（以超越方式吸收）**：
- **计划注入机制**：其"每次工具调用前把计划状态注入上下文"的注意力操纵思想值得吸收——nop 以 `AgentPromptAssembly.consultPlanRun()` 结构化注入实现（而非 `cat | head -30`）。
- **完成门（checkComplete）**：nop 用结构化 PlanRun 状态遍历替代其 grep 计数，更强。
- **3-Strike Error Protocol**：错误记忆防重复失败——nop plan 包可吸收为错误状态表。

**总评**：nop-ai-agent 在计划模型、持久化、hook 体系上**全面超越**；仅"计划注入/完成门/错误协议"三个机制思想值得以结构化方式吸收，实现层面无需任何照搬。

## References

- `~/ai/planning-with-files/skills/planning-with-files/SKILL.md`（hooks 配置）
- `~/ai/planning-with-files/skills/planning-with-files/templates/task_plan.md`（状态机规范）
- `~/ai/planning-with-files/skills/planning-with-files/scripts/{check-complete,init-session}.sh`、`session-catchup.py`
- `~/ai/planning-with-files/skills/planning-with-files/reference.md`（Manus 原理）
- `ai-dev/design/nop-ai-agent/00-vision.md`、`nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-06-06-agent-memory-compaction-session-deep-dive.md`
