# oh-my-claudecode (OMC) 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/oh-my-claudecode — Claude Code 多 agent 编排插件
> Conclusion:

## Context

- oh-my-claudecode 是 Claude Code 的多 agent 编排插件，将单 agent 变为编排平台
- 灵感来自 oh-my-opencode，是 "oh-my-*" 家族中 Claude Code 生态的代表
- 调研目的：理解其在 Claude Code 上的编排架构，以及 Sisyphean 执行模型的实践

## Analysis

### 项目定位

**oh-my-claudecode (OMC)** 是 Claude Code 的 **多 agent 编排插件**——不替换 Claude Code，而是在其上层添加编排层。npm 包名 `oh-my-claude-sisyphus`，体现了 "西西弗斯" 持续执行哲学。

- **作者**: Yeachan Heo (主维护，项目 ~35k GitHub stars)
- **许可**: MIT
- **版本**: 4.14.5
- **包名**: `oh-my-claude-sisyphus`
- **Slogan**: "Multi-agent orchestration for Claude Code. Zero learning curve."

### 技术栈

| 层 | 技术 |
|----|------|
| **语言** | TypeScript (strict, ESM) |
| **运行时** | Node.js >= 20.0.0 |
| **构建** | tsc + esbuild |
| **测试** | Vitest (v4), V8 覆盖率 |
| **Schema** | Zod + AJV |
| **MCP SDK** | @modelcontextprotocol/sdk v1.26 |
| **Agent SDK** | @anthropic-ai/claude-agent-sdk v0.1.0 |
| **AST** | @ast-grep/napi |
| **DB** | better-sqlite3 |
| **CLI** | Commander.js |
| **配置** | JSONC |

代码规模: **~1061 TS 源文件, ~550+ 测试文件**（含 __tests__/ 目录下文件）。

### 核心架构：四大系统互锁

```
User Input → Hooks (事件检测) → Skills (行为注入)
           → Agents (任务执行) → State (进度追踪)
```

#### 1. Hook 系统 (事件拦截骨干)

利用 Claude Code 的 shell hook 系统，拦截所有生命周期事件：

| 事件 | 功能 |
|------|------|
| `UserPromptSubmit` | 关键词检测, skill 注入 |
| `SessionStart` | 代码库 map 注入, 项目 memory |
| `PreToolUse` | 执行规则 |
| `PostToolUse` | 验证, 项目 memory 更新 |
| `PostToolUseFailure` | 工具失败处理 |
| `PermissionRequest` | 安全处理器 |
| `Stop` | 持续模式强制执行 |
| `PreCompact` | compaction 前状态保存 |
| `SessionEnd` | 清理, wiki 同步 |
| `SubagentStart/Stop` | Agent 追踪 |

#### 2. Agent 系统 (19 个专业 Agent)

| 通道 | Agent | 用途 |
|------|-------|------|
| **Build/Analysis** | explore, analyst, planner, architect, debugger, executor, verifier, tracer | 完整开发生命周期 |
| **Review** | security-reviewer, code-reviewer | 质量门控 |
| **Domain** | test-engineer, designer, writer, qa-tester, scientist, git-master, document-specialist, code-simplifier | 专业领域 |
| **Coordination** | critic | 计划/设计挑战 |

Agent 定义为 **Markdown 文件** (agents/*.md)，YAML frontmatter + XML `<Agent_Prompt>` 结构体。

#### 3. Skill 系统 (~40 个内置 Skill)

- **项目范围**: `.omc/skills/`
- **用户范围**: `~/.omc/skills/`
- `/skillify` 从调试会话提取可复用模式
- 自动注入：匹配 trigger 到当前 context

内置 skill: autopilot, ralph, ultrawork, team, plan, deep-interview, ccg, verify, debug, trace, wiki 等。

#### 4. Team 系统 (最复杂子系统, 61 非测试源文件)

- **tmux CLI workers**: 在 tmux pane 中生成真实的 `claude`/`codex`/`gemini` CLI 进程
- **Staged pipeline**: `team-plan → team-prd → team-exec → team-verify → team-fix (loop)`
- **文件协调**: Task 文件, inbox/outbox JSONL 消息, heartbeat 文件
- **Worktree 隔离**: Git worktree 为并行 worker 提供隔离
- **Phase controller**: 流水线阶段的状态机

### Sisyphean 执行模型（核心差异化）

整个系统围绕 "永不放弃" 的西西弗斯隐喻构建：

1. **System prompt** 包含 "Sisyphean Oath"
2. **Stop-hook** 拦截停止事件，检查 todo 列表
3. **Todo 验证** 确保 agent 不在完成前停止
4. **后台任务管理** 持续追踪未完成工作
5. **Pre-compact hook** 在 context compaction 前保存状态
6. **Project Memory**: 项目范围的持久化记忆（project_memory_read/write/add_note）
7. **Notepad**: 优先级工作笔记（notepad_read/write_priority/working/manual）

### Magic Keywords (触发式增强)

特殊关键词触发行为增强：

- `ultrawork` / `ulw` — 最大并行 agent 编排
- `search` / `find` — 多 agent 搜索
- `analyze` / `investigate` — 深度分析
- `ultrathink` — 扩展思考模式
- 支持多语言触发（中/日/韩/越）

### 智能模型路由

三层成本/性能模型：

| 层级 | 模型 | 用途 |
|------|------|------|
| LOW | haiku | 快速查找, 文档 |
| MEDIUM | sonnet | 实现, 调试, 测试 |
| HIGH | opus | 架构, 战略规划, 关键审查 |

路由基于复杂度评分 + agent 特定覆盖 + 失败自动升级。项目声称节省 **30-50% token**（自报告，未独立验证）。

### 多 AI 集成

- **Codex CLI** (OpenAI): 架构验证, 代码审查
- **Gemini CLI** (Google): 设计审查, UI 一致性 (1M token context)
- 通过 `omc ask`, `/ask`, `/ccg` 进行交叉验证

### 编排模式

| 模式 | 用途 |
|------|------|
| Team (推荐) | Staged 多 agent pipeline + tmux workers |
| Autopilot | 单 lead 自主执行 |
| Ultrawork | 最大并行 burst |
| Ralph | 持久验证完成 |
| UltraQA | 质量门循环 |
| CCG | 三模型 (Claude+Codex+Gemini) 顾问综合 |
| Deep Interview | 苏格拉底式需求澄清 |

### 配置层级

```
内置默认 → 用户配置 (~/.config/claude-omc/config.jsonc) → 项目配置 (.claude/omc.jsonc) → 环境变量
```

### 通知系统

Telegram, Discord, Slack, webhook 回调，OpenClaw 网关集成。

### 优势

1. **全面的编排**: 覆盖从探索到验证的完整工作流
2. **Sisyphean 持久模型**: 确保 agent 真正完成任务
3. **智能模型路由**: 三个层级 + 复杂度评分，节省 30-50% token
4. **多 provider 支持**: Claude + Codex + Gemini 透明编排
5. **零摩擦 UX**: Magic keywords, 自动注入, 自然语言触发
6. **状态韧性**: Boulder state, notepad, pre-compact hooks
7. **成熟测试**: ~550+ 测试文件
8. **i18n**: 12 种语言
9. **MCP 工具**: LSP 工具 (12), AST 工具 (2), Python REPL, Skills 工具, State/Memory/Trace 工具

### 劣势

1. **复杂度极高**: 1049+ TS 文件，单 team 模块 62+ 文件
2. **tmux 依赖**: Team 模式需要 tmux，Windows 不原生支持
3. **Native addon**: better-sqlite3 需要编译
4. **Prompt 即架构**: 大量行为编码在 Markdown prompts 和 system prompt 字符串中（definitions.ts 中 115 行行为规则），难以测试/调试
5. **快速演进**: 频繁迭代产生废弃代码和兼容层
6. **单一维护者**: 复杂度高 + 一人主导 = 可持续性风险
7. **文档分散**: README + docs/ + website + CLAUDE.md + AGENTS.md
8. **安全隐忧**: Hook 系统在每个工具调用时执行任意脚本

### 与 pi / oh-my-pi 生态的关系

- **oh-my-opencode** 是灵感来源（README 明确提及）
- **oh-my-codex** 是姐妹项目（Codex CLI 版本）
- 三者共同构成 "oh-my-*" 编排插件家族，分别面向 Claude Code, OpenCode, Codex
- 与 oh-my-pi 无直接代码关系，但在 Sisyphean 模型（永不放弃哲学）方面有概念对齐

### 与 Nop 平台的关联

#### 可借鉴

- **Sisyphean 持久执行**: AI agent 任务不应该半途而废——Nop 的 biz 层也可以引入类似的"完成保证"机制
- **Team staged pipeline**: team-plan → team-prd → team-exec → team-verify 的流水线模式可用于 Nop 的任务编排
- **Magic keywords**: 简单的触发式增强机制，可以应用于 Nop 的 CLI/REPL
- **Pre-compact state preservation**: 长时间 AI 会话的状态保存模式
- **Agent 定义即 Markdown**: 低门槛的 agent 定义方式

#### 不适用

- Claude Code 特定的 hook/agent SDK 不适用于 Nop
- tmux 进程管理不适用于 Java 进程
- better-sqlite3 状态管理不适用于 Nop 的 ORM

## Conclusion

分析进行中。oh-my-claudecode 最具借鉴价值的是 Sisyphean 持久执行模型（解决 agent 半途而废的核心痛点）和 Team staged pipeline 模式。Agent 定义即 Markdown 的低门槛方式也值得关注。但 Claude Code 特定的 hook/SDK 机制不直接适用于 Nop。

## Open Questions

- [ ] Sisyphean 执行模型能否抽象为 Nop biz 层的通用模式？
- [ ] Markdown Agent 定义是否适合 Nop 的 XLang 元模型？
- [ ] Team pipeline 的 staged 模式与 Nop 的工作流引擎如何对接？

## References

- ~/ai/oh-my-claudecode/README.md
- ~/ai/oh-my-claudecode/CLAUDE.md
- ~/ai/oh-my-claudecode/agents/ (Markdown agent 定义)
- ~/ai/oh-my-claudecode/hooks/hooks.json
- ~/ai/oh-my-claudecode/src/agents/definitions.ts
- ~/ai/oh-my-claudecode/docs/
- https://github.com/Yeachan-Heo/oh-my-claudecode
