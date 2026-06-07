# oh-my-opencode (oh-my-openagent) 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/oh-my-opencode — OpenCode 多模型多 agent 编排插件
> Conclusion:

## Context

- oh-my-opencode 是 OpenCode 终端 AI coding agent 的插件，正重命名为 oh-my-openagent
- 是 "oh-my-*" 家族中 OpenCode 生态的代表，也是 oh-my-claudecode 的灵感来源
- 调研目的：理解其多模型编排、Discipline Agent 模式和 hashline 编辑系统

## Analysis

### 项目定位

**oh-my-opencode (OmO)** / **oh-my-openagent** 是 OpenCode 的 **batteries-included 插件**——将单模型、单会话工具转变为多模型、多 agent 编排平台。用户输入 `ultrawork`，一组 AI 专家团队并行执行任务。

- **作者**: YeonGyu-Kim (@code-yeongyu)，维护者 "Jobdori" (AI 助手)
- **许可**: Sustainable Use License 1.0 (SUL-1.0) — **源码可见、非商业许可**，非 OSI 开源
- **版本**: 4.5.1
- **Slogan**: "The Best AI Agent Harness -- Batteries-Included OpenCode Plugin"

### 技术栈

| 层 | 技术 |
|----|------|
| **语言** | TypeScript (strict) |
| **运行时** | Bun (主要), Node.js 兼容 |
| **构建** | Bun bundler + tsgo + tsc |
| **测试** | bun test |
| **Schema** | Zod v4 |
| **Plugin SDK** | @opencode-ai/plugin ^1.15.4 |
| **AST** | @ast-grep/napi ^0.42.2 |
| **MCP** | @modelcontextprotocol/sdk ^1.29 |
| **函数式** | effect 4.0.0-beta.65 |
| **CLI** | Commander ^14.0.3 |
| **遥测** | PostHog (匿名, 默认开启) |

代码规模: **~117K 行生产代码 + ~193K 行测试代码**（测试超过生产代码）。

### 架构：插件 + 10 个内部包

```
src/                      主插件源码 (~117K 行非测试)
packages/
  utils/                  @oh-my-opencode/utils
  rules-engine/           @oh-my-opencode/rules-engine       规则发现 & AGENTS.md
  agents-md-core/         @oh-my-opencode/agents-md-core     AGENTS.md 发现/注入
  model-core/             @oh-my-opencode/model-core         模型解析逻辑
  prompts-core/           @oh-my-opencode/prompts-core       Prompt 加载 & 模型路由
  hashline-core/          @oh-my-opencode/hashline-core      哈希锚定编辑
  comment-checker-core/   @oh-my-opencode/comment-checker-core  注释检查
  ast-grep-core/          @oh-my-opencode/ast-grep-core      AST 搜索/重写
  ast-grep-mcp/           @oh-my-opencode/ast-grep-mcp       AST-grep MCP 服务
  boulder-state/          @oh-my-opencode/boulder-state      工作追踪状态机
```

分层依赖 DAG: Core (纯 TS, 无 harness 依赖) → MCP (stdio 进程边界) → Skills (纯 Markdown) → Adapters (harness 胶水) → Platform (编译二进制)

### 核心: Discipline Agent 模式

每个 agent 不是简单的模型包装，而是针对特定模型深度定制的 prompt：

| Agent | 角色 | 文档默认模型 |
|-------|------|----------|
| **Sisyphus** | 主编排器 | claude-opus-4-7 / kimi-k2.6 |
| **Hephaestus** | 自主深度 worker | gpt-5.5 |
| **Prometheus** | 战略规划器 (访谈模式) | claude-opus-4-7 / kimi-k2.6 |
| **Oracle** | 架构/调试顾问 (只读) | gpt-5.5 |
| **Librarian** | 文档/代码搜索 | gpt-5.4-mini-fast |
| **Explore** | 快速代码库 grep | gpt-5.4-mini-fast |
| **Multimodal-Looker** | PDF/图像分析 | gpt-5.5 |
| **Metis** | 预规划分析 | claude-sonnet-4-6 |
| **Momus** | 计划审查/批评 | gpt-5.5 |
| **Atlas** | Todo-list 编排 | claude-sonnet-4-6 |

> 注：模型为运行时动态解析，基于用户可用的 provider 和 category 路由，上述为文档中的默认值

每个 agent 有：
- **模型特定 prompt**: 针对不同模型的数百行 prompt 工程
- **Fallback chain**: 自动 provider/model 降级链
- **Category delegation**: visual-engineering, deep, quick, ultrabrain 等任务类别。Agent 声明 category，harness 映射到模型
- **IntentGate**: 分析用户真实意图后再分类和执行（核心创新）

### Hashline (哈希锚定编辑)

受 oh-my-pi 启发，每行标记内容哈希：

```
11#VK|  original line content
```

编辑引用哈希，文件变更后编辑被拒绝。**从 6.7% 到 68.3% 编辑成功率提升**。

独立提取为 `@oh-my-opencode/hashline-core` 包。

### 54+ Hook 系统

拦截 OpenCode agent 生命周期的所有方面：
- `chat.params` — 覆盖模型参数
- `chat.message` — 消息拦截, 关键词注入
- `tool.execute.before/after` — 工具调用守卫和转换
- `event` — 会话生命周期事件
- `command.execute.before` — slash 命令处理

### Team Mode (v4.0)

- Lead agent + 最多 8 个并行 member
- 实时 tmux 可视化
- 专用 `team_*` 工具族
- Powers `hyperplan` (5 个敌对批评者) 和 `security-research` (3 个 hunter + 2 个 PoC 工程师)

### 内置 MCP 服务

- **Websearch** (Exa)
- **Context7** — 官方文档查找
- **Grep.app** — GitHub 代码搜索
- **ast-grep** — 25 语言 AST 代码搜索
- **LSP** — Language Server Protocol 工具

### 内置 Skill & Command

**Skill**: playwright, git-master, frontend-ui-ux, review-work, ai-slop-remover, team-mode（configurable skill IDs）；shipped SKILL.md: github-triage, hyperplan, security-research, pre-publish-review 等

**Command**: `/ultrawork`, `/ralph-loop`, `/ulw-loop`, `/init-deep` (分层 AGENTS.md 生成), `/start-work`, `/refactor`, `/handoff`, `/hyperplan`, `/ai-slop-remover`

### 配置 (41 个子 Schema)

```
用户全局 (~/.config/opencode/oh-my-openagent.jsonc) → 项目 (.opencode/oh-my-openagent.jsonc) walked up to $HOME
```

支持 agents, categories, team_mode, background_task, tmux, disabled_*, default_mode, comment_checker 等。

### 关键设计决策

1. **测试 > 生产代码**: 193K 行测试 vs 117K 行生产——业界罕见的高测试比
2. **模型名引用前瞻**: GPT-5.5, Claude Opus 4.7 等——可能是面向未来的命名或非标准约定
3. **正在从 OpenCode 解耦**: ROADMAP 明确计划多 harness 支持 (Codex, Pi, Claude Code, Amp)
4. **SUL-1.0 许可**: 源码可见但限制商业使用——影响企业采纳

### 优势

1. **巨大的功能面**: 10 agent, 90+ hook, 60+ tool, 内置 MCP, Team Mode
2. **模型无关**: Claude, GPT, Gemini, Kimi, GLM, MiniMax, Qwen 全支持
3. **Hashline 编辑**: 解决了 agent harness 的核心痛点
4. **非凡测试覆盖**: 193K 行测试代码
5. **实际验证**: README 声称用户在 Google, Microsoft, Vercel, Deepgram 工作（个人背景，非机构背书）
6. **Clean extraction 进行中**: 包分层重构思路清晰
7. **Claude Code 兼容**: 低迁移成本
8. **Session Recovery**: 自动从错误、context 限制、API 故障恢复
9. **Preemptive Compaction**: 主动上下文窗口管理，防止突发截断

### 劣势

1. **非开源许可**: SUL-1.0 限制商业使用和再分发
2. **OpenCode 耦合**: 尽管有多 harness 路线图，当前深度耦合；ROADMAP 记录了 OpenCode plugin API 的竞争条件问题
3. **复杂度**: 117K+ 行生产代码的插件
4. **强制默认**: 激进的 auto-delegation, todo enforcement 可能与用户期望冲突
5. **PostHog 遥测**: 默认开启（匿名但非 opt-in）
6. **Bun 依赖**: 实质上需要 Bun
7. **模型名非标准**: 引用尚不存在的模型版本

### 与 pi / oh-my-pi 生态的关系

- **oh-my-pi** 是 Hashline 的灵感来源（README 明确致谢 Can Boluk）
- **Pi** 是多 harness 路线图中的目标适配器之一
- **OpenClaw** (维护者的 fork) 用于运行 "Jobdori" AI 助手
- 哲学对齐：改进 agent harness 而非仅改进模型

### 与 Nop 平台的关联

#### 可借鉴

- **Discipline Agent**: 针对模型深度定制的 prompt 模式——Nop 的 AI 集成可以类似地为不同 LLM 定制 prompt
- **Hashline-core**: 独立包的设计方式，内容哈希锚定概念可引入 Nop 代码生成管线防止模板覆盖手工修改
- **boulder-state**: 工作追踪状态机，适用于 Nop biz 层的任务进度管理
- **rules-engine**: AGENTS.md 发现和注入模式，直接对应 Nop 的 docs-for-ai/ 加载机制；`/init-deep` 自动生成分层 AGENTS.md 最值得借鉴
- **Category delegation**: 按任务类别（visual-engineering, deep, quick, ultrabrain）分配模型/配置的模式
- **IntentGate**: 分析用户真实意图后再执行的模式

#### 不适用

- OpenCode plugin API 不适用于 Nop
- SUL-1.0 许可限制商业使用
- Bun 运行时不兼容 Java

## Conclusion

分析进行中。oh-my-opencode 最具借鉴价值的是：1) `/init-deep` 分层 AGENTS.md 自动生成（直接对应 Nop 的 docs-for-ai/ 结构）；2) rules-engine 的 AGENTS.md 发现/注入模式；3) Hashline 内容哈希锚定编辑；4) Discipline Agent 的模型特定 prompt 工程。SUL-1.0 许可限制了直接使用，但设计模式值得参考。

## Open Questions

- [ ] hashline-core 能否作为 Nop Maven 插件或 XLang 扩展使用？
- [ ] boulder-state 状态机模式是否适合 Nop 的工作流引擎？
- [ ] Discipline Agent 的模型特定 prompt 模式如何映射到 Nop 的 XPL 模板？

## References

- ~/ai/oh-my-opencode/README.md
- ~/ai/oh-my-opencode/ROADMAP.md
- ~/ai/oh-my-opencode/packages/ (各 package.json)
- ~/ai/oh-my-opencode/docs/
- https://github.com/code-yeongyu/oh-my-opencode
