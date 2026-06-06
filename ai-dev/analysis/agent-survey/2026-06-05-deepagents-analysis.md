# Deep Agents 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/deepagents — LangChain 出品的 batteries-included agent harness
> Conclusion:

## Context

- Deep Agents 是 LangChain, Inc. 推出的 "batteries-included" agent harness
- 基于 LangGraph + LangChain，定位为开源可扩展版的 Claude Code / Codex
- 调研目的：理解 LangChain 生态的 agent harness 设计，对比 Python 生态实践

## Analysis

### 项目定位

- **组织**: LangChain, Inc.
- **许可**: MIT
- **版本**: SDK v0.3.12, CLI v0.0.19, ACP v0.0.1, Harbor v0.0.1
- **语言**: Python 3.11-3.14
- **核心依赖**: LangGraph (状态图引擎) + LangChain (模型/工具抽象)
- **默认模型**: Anthropic Claude Sonnet 4.5
- **定位**: 不是框架，是**开箱即用的 agent harness**

### 模块结构

```
libs/
  deepagents/      SDK (核心库, create_deep_agent)
  cli/             CLI 工具 (Textual TUI)
  acp/             Agent Client Protocol 集成
  harbor/          评估/benchmark 框架
  partners/daytona/ Daytona 沙箱集成
examples/          5 个示例 agent
```

### 核心抽象

#### Backend Protocol (策略模式)

统一的文件/执行操作接口，多个实现：

| Backend | 存储 | 执行 | 场景 |
|---------|------|------|------|
| `StateBackend` | 内存 (LangGraph state) | 无 | 默认临时存储 |
| `FilesystemBackend` | 真实文件系统 | 无 | 本地开发 |
| `LocalShellBackend` | 真实文件系统 | 本地 shell | CLI 工具 |
| `StoreBackend` | LangGraph BaseStore | 无 | 跨线程持久化 |
| `CompositeBackend` | 路径前缀路由 | 委托 | 混合存储 |
| `BaseSandbox` | Shell 命令实现 | 远程沙箱 | Modal/Daytona/Runloop |

`CompositeBackend` 路径前缀路由（最长匹配优先），如 `/memories/` → StateBackend, 其余 → FilesystemBackend。

#### Middleware Pipeline (拦截器链)

每个中间件可：添加工具、修改 system prompt、拦截 before/after agent 步骤、定义额外 state schema。

标准中间件栈：

| 中间件 | 功能 |
|--------|------|
| `TodoListMiddleware` | 任务分解/进度 (write_todos/read_todos) |
| `MemoryMiddleware` | 加载 AGENTS.md 到 system prompt |
| `SkillsMiddleware` | 渐进式技能加载 (SKILL.md + YAML frontmatter) |
| `FilesystemMiddleware` | 文件工具: ls, read, write, edit, glob, grep, execute |
| `SubAgentMiddleware` | task 工具生成临时子 agent |
| `SummarizationMiddleware` | 上下文自动摘要 + 历史卸载 |
| `HumanInTheLoopMiddleware` | 指定工具的中断/审批 |

#### Sub-Agent 系统

- **`SubAgent`**: 声明式（name, description, prompt, tools, model），框架自动编译
- **`CompiledSubAgent`**: 预构建的 LangGraph runnable
- 子 agent 拥有独立 context window 和中间件栈
- 状态过滤防止父 state 泄漏到子 agent
- 支持并行执行（LLM 决定）

#### 上下文管理

`SummarizationMiddleware`:
- 可配置触发器：token 数、消息数、context window 占比
- 历史卸载：完整对话历史持久化到 backend
- 参数截断：旧工具调用参数在 context 填满时截断
- 模型自适应：根据模型 `max_input_tokens` 自动配置阈值

#### Skills 系统

- 遵循 agentskills.io 规范
- SKILL.md + YAML frontmatter (name, description, license, compatibility)
- 多源分层：base → user → project → team（后者覆盖前者）
- 渐进式披露：metadata 在 system prompt，完整指令按需加载

### 使用方式

```python
agent = create_deep_agent(
    model="openai:gpt-4o",
    tools=[my_tool],
    subagents=[{...}],
    skills=["/skills/user/"],
    memory=["/AGENTS.md"],
    backend=my_backend,
    interrupt_on={"edit_file": True},
)
```

返回 LangGraph `CompiledStateGraph`，原生支持 streaming, checkpointing, Studio。

### 优势

1. **真正的开箱即用** — 3 行代码得到工作 agent，无需手动组装
2. **Backend Protocol 设计精良** — 清晰接口 + 多实现 + CompositeBackend 路由
3. **上下文管理成熟** — 自动摘要 + 历史卸载 + 参数截断 + 模型自适应
4. **子 Agent 架构优秀** — 隔离 context + 状态过滤 + prompt 工程教学 LLM 委托
5. **中间件解耦** — 每个能力（文件系统、记忆、技能、子 agent、摘要）独立中间件
6. **Provider 无关** — 任何 LangChain 兼容模型
7. **LangGraph 原生** — 完整生产特性（streaming, checkpointing, Studio）
8. **安全设计** — 路径验证、虚拟模式、symlink 保护、沙箱隔离、HITL

### 劣势

1. **LangChain 生态深度耦合** — 依赖 langchain-core, langgraph, langchain 内部 API，不可移植
2. **中间件栈复杂** — 7+ 中间件链的交互点难以调试
3. **双 sync/async** — 每个方法都有同步和异步版本，代码量翻倍
4. **次要包质量低** — ACP (v0.0.1), Harbor (v0.0.1) 仍处于极早期
5. **Python only** — JS/TS 版本在独立仓库 (deepagentsjs)
6. **依赖面大** — LangChain + LangGraph + Anthropic + Google GenAI 等

### 与其他项目的对比

| 维度 | Deep Agents | CrewAI | AutoGen | Pi |
|------|------------|--------|---------|-----|
| **定位** | 开箱即用 harness | 多 agent 角色 | 多 agent 对话 | 扩展型 harness |
| **语言** | Python | Python | Python | TypeScript |
| **子 Agent** | task 工具 + 隔离 context | 进程级 | 对话级 | 无内置 |
| **上下文管理** | 自动摘要 + 卸载 | 手动 | 手动 | Compaction |
| **文件系统** | 可插拔 Backend | 手动 | 代码执行 | fork/exec |
| **沙箱** | Modal/Daytona/Runloop | 无 | 无 | 无 |
| **生产运行时** | LangGraph | 自定义 | 自定义 | 自定义 |

**核心差异**: Deep Agents 是一个 **opinionated agent harness**，不是框架。不需要从原语构建 agent，而是得到完整工作 agent 后按需定制。最接近的开源类比是 Claude Code / Codex 的可扩展版本。

### 与 Nop 平台的关联

#### 可借鉴（概念级）

1. **Backend Protocol**: 可插拔后端 + CompositeBackend 路由 → Nop 可实现类似的存储策略组合
2. **Middleware Pipeline**: 中间件链（工具、state schema、prompt 修改、before/after hooks）→ Nop biz 拦截器参考
3. **Sub-Agent 委托**: task 工具模式（隔离 agent + 单一结果返回）→ Nop biz 层委托参考
4. **上下文管理**: 自动摘要 + 可配置触发器 + 历史卸载 → Nop AI 会话管理参考
5. **Skills 渐进式披露**: metadata 在 prompt、完整指令按需加载 → 资源管理模式参考
6. **AGENTS.md 记忆**: 启动加载 + prompt 注入 + agent 自更新 → Nop 的 docs-for-ai 机制参考
7. **LLM-first 工具设计**: 结构化错误消息、分页、行号 → Nop AI 工具 API 设计参考

#### 不适用

- Python 技术栈不可直接在 Java/Nop 中使用
- LangChain/LangGraph 深度耦合
- 集成仅能通过 HTTP API（Nop 暴露服务给 Deep Agent 调用）

## Conclusion

Deep Agents 是 Python 生态中最完整的 opinionated agent harness，Backend Protocol + Middleware Pipeline + Sub-Agent + 上下文管理的设计成熟。作为 LangChain 官方产品，代表了 Python agent harness 的最佳实践。对 Nop 的价值主要是概念级借鉴——Middleware 管道、Backend 路由、上下文管理策略等模式可跨语言应用。

## Open Questions

- [ ] Deep Agents 的 Middleware Pipeline 模式是否适合映射到 Nop 的 XPL 执行模型？
- [ ] Backend Protocol 的 CompositeBackend 路由是否可应用于 Nop 的代码生成管线？
- [ ] LangGraph 作为生产运行时的模式是否适合 Nop 工作流引擎参考？

## References

- ~/ai/deepagents/README.md
- ~/ai/deepagents/AGENTS.md
- ~/ai/deepagents/libs/deepagents/
- https://github.com/langchain-ai/deepagents
