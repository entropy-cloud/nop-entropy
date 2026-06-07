# Pi Agent 生态对比分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/ 下 pi-agent 相关项目横向对比
> Conclusion:

## Context

- ~/ai/ 下存在一个以 pi (pi-mono) 为核心的 agent 生态
- oh-my-pi (pi fork), oh-my-claudecode (Claude Code 插件), oh-my-opencode (OpenCode 插件) 形成平行进化
- 需要横向对比以理解各自的设计取舍和适用场景

## Analysis

### 生态关系图

```
pi (pi-mono) ─── fork ──→ oh-my-pi
     │                       │
     │ (inspiration)         │ (hashline 灵感)
     ↓                       ↓
awesome-pi-agent         oh-my-opencode ── inspire → oh-my-claudecode
                          (OpenCode 插件)    (Claude Code 插件)
```

### 定位对比

| 维度 | pi | oh-my-pi | oh-my-claudecode | oh-my-opencode |
|------|----|----------|------------------|----------------|
| **角色** | 基础 agent harness | 增强型 coding agent | Claude Code 编排插件 | OpenCode 编排插件 |
| **与 host 关系** | 独立应用 | Pi 的 fork | Claude Code 的插件 | OpenCode 的插件 |
| **运行时** | Node.js | Bun | Node.js | Bun |
| **许可** | MIT | MIT | MIT | SUL-1.0 (非商业) |
| **语言规模** | 4 包 (TS) | ~495K 行 TS + ~32.6K 行 Rust | ~1049 文件 TS | ~117K 行 TS |
| **测试规模** | 250+ 文件 | 未明确 | ~550+ 文件 | ~193K 行 |
| **Agent 数** | 单 agent | 单 agent + 子 agent | 19 专业 agent | 10 Discipline Agent |
| **LLM 提供商** | 34 个已知 | 40+ | 3 (Claude+Codex+Gemini) | 7+ |
| **核心创新** | 扩展系统 | Hashline + Rust N-API | Sisyphean 模型 | Discipline Agent |

### 技术架构对比

#### 编辑系统

| 项目 | 编辑方式 | 准确率 |
|------|----------|--------|
| pi | str_replace (find/replace) | 未公开 |
| oh-my-pi | Hashline (内容哈希锚定) | 68.3% (vs 6.7%) |
| oh-my-opencode | Hashline (受 omp 启发) | 同上 |
| oh-my-claudecode | Claude Code 内置 | 依赖 host |

#### Agent 编排

| 项目 | 编排模式 | 并行支持 | 隔离 |
|------|----------|----------|------|
| pi | 无（单 agent loop） | 工具级并行 | 无 |
| oh-my-pi | 子 agent (进程内) | Schema 验证并行 | Git worktree (APFS/btrfs) |
| oh-my-claudecode | 19 agent + Team (tmux) | tmux 真实进程并行 | Git worktree |
| oh-my-opencode | 10 Discipline Agent + Team | tmux 真实进程并行 | Git worktree |

#### 性能优化

| 项目 | Grep | Shell | AST |
|------|------|-------|-----|
| pi | fork → ripgrep | fork → bash | 无 |
| oh-my-pi | Rust N-API (进程内) | Rust N-API (brush-shell) | Rust N-API (tree-sitter) |
| oh-my-claudecode | host 依赖 | host 依赖 | @ast-grep/napi |
| oh-my-opencode | host 依赖 | host 依赖 | @ast-grep/napi |

#### 扩展性

| 项目 | 扩展方式 | 事件数 | 工具注册 | Provider 注册 |
|------|----------|--------|----------|--------------|
| pi | TypeScript ExtensionFactory | 22 | ✓ | ✓ |
| oh-my-pi | 继承 pi + MCP + tools | 22+ | ✓ | ✓ |
| oh-my-claudecode | Hook + Agent MD + MCP + Skill | 11 lifecycle | ✓ | 有限 |
| oh-my-opencode | Plugin API + Hook + MCP + Skill | 54+ hooks | ✓ | ✓ |

### 设计哲学对比

| 项目 | 核心哲学 | 一句话概括 |
|------|----------|-----------|
| pi | 自扩展 | 给 agent 一个可扩展的 harness |
| oh-my-pi | 性能+深度 | 把 IDE 编进 agent 里 |
| oh-my-claudecode | 永不放弃 | 西西弗斯推石头到山顶 |
| oh-my-opencode | 全副武装 | 一键 ultrawork 全员出动 |

### 成熟度评估

| 维度 | pi | oh-my-pi | oh-my-claudecode | oh-my-opencode |
|------|----|----------|------------------|----------------|
| 代码质量 | 高 | 高 | 中高 | 高 |
| 测试充分性 | 高 | 中 | 高 | 极高 |
| 文档 | 中 | 高 (60 篇) | 高 (多语言) | 高 |
| 社区 | 活跃 (awesome list) | 中 | 极高 (35k stars) | 高 |
| 许可友好 | 高 (MIT) | 高 (MIT) | 高 (MIT) | 低 (SUL-1.0) |
| 安装门槛 | 中 (Node 22) | 高 (Bun + Rust nightly) | 低 (npm install) | 中 (Bun) |

### 与 Nop 平台的可借鉴性排序

| 优先级 | 项目 | 可借鉴点 |
|--------|------|----------|
| **P0** | pi | 分层架构 (LLM API → Agent 运行时 → 应用), Provider Registry 模式, Session 持久化 |
| **P0** | oh-my-pi | Hashline 编辑, TTSR 流式规则, Model-per-role 路由 |
| **P1** | oh-my-claudecode | Sisyphean 持久执行, Staged pipeline 模式, Magic keywords |
| **P1** | oh-my-opencode | Discipline Agent 模式, boulder-state 状态机, rules-engine |
| **P2** | awesome-pi-agent | AI 自维护文档模式 |

### 共性设计模式

1. **事件驱动架构**: 所有项目都基于事件/生命周期钩子构建
2. **分层配置**: 全局 → 项目 → 环境变量的级联合并
3. **Provider 抽象**: 统一的 LLM 调用接口，隐藏 provider 差异
4. **Session 持久化**: JSONL 格式的 append-only 会话存储
5. **Context Compaction**: 自动上下文窗口管理
6. **工具 schema 驱动**: TypeBox/Zod/JSON Schema 定义工具参数
7. **Model-per-role/category**: 按任务复杂度/角色路由到不同模型

### 差异化设计

1. **oh-my-pi 的 Rust N-API**: 唯一使用系统语言消除 fork/exec 的项目
2. **oh-my-claudecode 的 Sisyphean 模型**: 唯一强制任务完成的持久执行模型
3. **oh-my-opencode 的 Discipline Agent**: 唯一按模型深度定制 prompt 的 agent 定义方式
4. **pi 的 ExtensionFactory**: 最纯粹的扩展系统设计
5. **awesome-pi-agent 的 AI 自维护**: 唯一由 agent 维护自身生态文档的项目

### 状态管理对比

| 项目 | 持久化方式 | 状态恢复 |
|------|-----------|---------|
| pi | JSONL 文件 | Session 树导航 |
| oh-my-pi | JSONL + SQLite (auth) | Session 树 + blob externalization |
| oh-my-claudecode | better-sqlite3 + JSONL | Boulder state + pre-compact hooks |
| oh-my-opencode | 文件 + boulder-state 包 | Session recovery + preemptive compaction |

## Conclusion

Pi agent 生态呈现 **"基础 → fork 增强 → 跨平台编排"** 的进化路径：

- **pi** 提供了干净的 agent harness 基础，扩展系统设计最为纯粹
- **oh-my-pi** 在 Pi 基础上通过 Rust 实现了性能飞跃和 IDE 深度集成，Hashline 编辑和 TTSR 是独特创新
- **oh-my-claudecode** 将编排哲学发挥到极致，Sisyphean 模型解决了 agent 半途而废的核心痛点
- **oh-my-opencode** 以测试驱动的方式实现了最丰富的模型支持，Discipline Agent 是独特的 agent 设计模式

对 Nop 平台最有价值的借鉴：
1. pi 的分层 LLM API + Provider Registry → Nop AI 集成的底层架构
2. oh-my-pi 的 Hashline + TTSR → Nop 代码生成管线的质量保证
3. oh-my-claudecode 的 Sisyphean + Staged pipeline → Nop biz 层的任务编排

## Open Questions

- [ ] 哪个项目的 agent loop 设计最适合 Nop 的 XLang 执行模型？
- [ ] Hashline 编辑机制能否通过 XPL 模板集成到 Nop 的代码生成流程中？
- [ ] Sisyphean 持久执行是否需要 Nop 层面的支持，还是纯 prompt 工程即可？

## References

- `ai-dev/analysis/2026-06-05-pi-agent-analysis.md`
- `ai-dev/analysis/2026-06-05-oh-my-pi-analysis.md`
- `ai-dev/analysis/2026-06-05-oh-my-claudecode-analysis.md`
- `ai-dev/analysis/2026-06-05-oh-my-opencode-analysis.md`
- `ai-dev/analysis/2026-06-05-awesome-pi-agent-analysis.md`
