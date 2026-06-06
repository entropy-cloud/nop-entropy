# oh-my-pi (omp) 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/oh-my-pi — 增强版 Pi coding agent
> Conclusion:

## Context

- oh-my-pi 是 Pi (pi-mono) 的增强 fork，由 Can Boluk (@can1357) 开发
- 定位 "A coding agent with the IDE wired in"，在 Pi 基础上大幅扩展
- 通过 ~32.6K 行 Rust 代码消除了 fork/exec 开销，并添加了 IDE 集成、子 agent 编排、内存系统等 Pi 缺少的能力

## Analysis

### 项目定位

**oh-my-pi (omp)** 是 Pi 的 **batteries-included 增强 fork**，通过 ~27,000 行 Rust 代码消除了 fork/exec 开销，并添加了 IDE 集成、子 agent 编排、内存系统等 Pi 缺少的能力。

- **作者**: Can Boluk (primary), Mario Zechner (upstream Pi)
- **许可**: MIT
- **版本**: 15.9.2
- **网站**: https://omp.sh
- **Slogan**: "A coding agent with the IDE wired in."

### 技术栈

| 层 | 技术 |
|----|------|
| **运行时** | Bun (>= 1.3.14)，非 Node.js |
| **主语言** | TypeScript (~588K 行 in packages/) |
| **系统语言** | Rust (~33.8K 行非 vendored in crates/)，nightly-2026-04-29 |
| **Python** | FastAPI (robomp triage bot), pytest |
| **前端** | React + Tailwind (stats dashboard); SolidJS + Tailwind (robomp-web) |
| **绑定** | napi-rs (Rust → JS N-API bindings) |
| **测试** | bun test (TS), cargo test (RS), pytest (Python) |

### 架构：TypeScript + Rust 混合

#### TypeScript 包 (packages/)

```
ai/              LLM 客户端库
agent/            Agent 运行时 (loop, state, tools)
coding-agent/     主 CLI + SDK
tui/              终端 UI 渲染引擎
natives/          Rust N-API 绑定
stats/            可观测性 dashboard (React + Tailwind)
utils/            共享工具
hashline/         内容哈希锚定的 patch 格式
swarm-extension/  Swarm 编排扩展
mnemopi/          SQLite 内存引擎 (Hindsight 后端)
```

#### Rust crates (crates/)

| 模块 | ~行数 | 功能 |
|------|-------|------|
| shell (pi-shell) | ~14,760 | 嵌入式 bash (brush-shell)，持久会话，minimizer |
| grep | 1,907 | 正则搜索，并行/串行，glob/type 过滤 |
| keys | 1,678 | Kitty 键盘协议 + xterm fallback |
| text | 1,944 | ANSI-aware 宽度/截断/SGR 保留换行 |
| summarize (pi-ast) | 1,278 | Tree-sitter 结构化源码摘要 |
| ast | 1,107 | ast-grep 模式匹配 + 结构化重写 |
| fs_cache | 840 | mtime-keyed 文件缓存 |
| highlight | 468 | 语法高亮 (syntect) |
| pty | 565 | 原生 PTY 分配 |
| iso | 245 | 工作区隔离 (APFS/btrfs/overlayfs) |
| glob | — | 文件模式匹配 |
| workspace | — | 工作区发现和管理 |
| fd | — | 高性能文件查找 |
| clipboard | — | 剪贴板读写 |
| tokens | — | BPE tokenizer 计数 |
| html | — | HTML 解析和渲染 |

> 注：共 4 个非 vendored Rust crate（pi-natives, pi-shell, pi-ast, pi-iso），pi-natives 内含约 24 个 .rs 模块文件，总计 ~33.8K 行非 vendored Rust 代码

所有 Rust 编译为单个 N-API cdylib (`pi_natives.node`)，**进程内运行无 fork/exec**。

### 核心差异化特性（vs 上游 Pi）

#### 1. Hashline 编辑系统

内容哈希锚定的 patch 格式：每行标记内容哈希（`11#VK|`），编辑时引用哈希。如果文件自上次读取后已变更，编辑被拒绝。**从 str_replace 的 6.7% 成功率提升到 68.3%（Grok Code Fast 1 测试）**。

#### 2. 子 Agent 编排

- 进程内子 agent: 嵌套 `createAgentSession()` 调用
- Schema 验证的输出
- Git worktree 隔离（通过 pi-iso: APFS clones, btrfs reflinks）
- IRC 工具实现 agent 间通信
- 可配置递归深度（默认 2）

#### 3. LSP + DAP 集成

- **LSP**: 14 个操作（diagnostics, hover, references, definitions, symbols...）
- **DAP**: 28 个操作（debugger 断点、变量查看、调用栈...）
- 不是文本操作层面，而是真正的 IDE 语言服务集成

#### 4. Hindsight 内存系统

- `retain` / `recall` / `reflect` 三工具内存表面
- 项目范围的 memory bank，会话间持久化
- 心智模型: 压缩的会话摘要，首次 turn 自动加载

#### 5. Time-Travel Stream Rules (TTSR)

- 正则触发的规则：在模型输出流中检测模式
- **流中断注入**: 检测到匹配时中断流，注入规则为系统提醒，从同一点重试
- **Compaction 存活**: 注入的内容在 compaction 后仍然保留
- 实时纠错，无需额外 context 消耗

#### 6. 跨工具配置继承

原生读取 8+ 种配置格式：
- Cursor MDC
- Cline `.clinerules`
- Codex `AGENTS.md`
- Copilot `applyTo`
- Pi `.pi/settings.json`
- 等

#### 7. 40+ LLM 提供商 + 12 Web 搜索后端

比上游 Pi 更多的 provider 支持，加上 Exa, Brave, Jina, Kimi 等 12 个 web 搜索后端。

#### 8. 其他增强

- 浏览器自动化 (Puppeteer + stealth mode)
- GitHub 作为文件系统 (`pr://`, `issue://`, `conflict://` URL scheme)
- 代码执行：持久 Python + JavaScript 内核，带工具 re-entry bridge
- Model-per-role 路由：不同模型用于 default/smol/slow/vision/plan/designer/commit/task 8 个角色
- robomp: 自托管的 GitHub triage bot (Python/FastAPI)

### 配置系统

- **格式**: YAML（非 Pi 的 JSON）
- **层级**: 全局 `~/.omp/agent/config.yml` → 项目 `.omp/settings.yml` → 路径范围
- **热重载**: 后台持久化 + 同步 get/set
- **Schema 验证**: `settings-schema.ts` 定义 100+ 设置

### 与上游 Pi 的关系

| 维度 | Pi (upstream) | oh-my-pi (fork) |
|------|---------------|-----------------|
| Runtime | Node.js | Bun |
| 系统级操作 | fork/exec | Rust N-API 进程内 |
| LSP/DAP | 无 | 14+28 操作 |
| 子 Agent | 无 | Schema 验证 + worktree 隔离 |
| 编辑系统 | str_replace | Hashline 内容哈希锚定 |
| 内存系统 | 无 | Hindsight retain/recall/reflect |
| 流式纠错 | 无 | TTSR 正则触发 |
| Provider 数 | 35 个已知 | 40+ |
| Web 搜索 | 无 | 12 后端 |
| SDK 嵌入 | 无 | Full SDK + RPC + ACP |

核心 agent loop、TUI 渲染和消息格式继承自 Pi。

### 优势

1. **性能优先**: ~33.8K 行 Rust 消除 fork/exec，grep/shell/AST 全部进程内
2. **深度 IDE 集成**: 真正的 LSP 和 DAP 支持（不是仅文本操作）
3. **Hashline 编辑**: 大幅提升编辑成功率，减少 token 浪费
4. **子 Agent 架构**: Schema 验证 + 并行 + worktree 隔离
5. **Hindsight 内存**: 真正的跨会话记忆
6. **TTSR 流式规则**: 实时纠错无 context 消耗
7. **跨工具兼容**: 读取 8 种配置格式，零迁移成本
8. **robomp triage bot**: 自托管 GitHub 自动化
9. **SDK + RPC + ACP**: 可编程嵌入（Node SDK）, JSON-RPC over stdio（非 Node 嵌入）, Agent Client Protocol（编辑器集成）
10. **ast_grep / ast_edit**: 50+ tree-sitter 语法的结构化代码搜索和重写
11. **代码审查**: `/review` 生成 P0-P3 优先级的专业审查子 agent

### 劣势

1. **Bun 依赖**: 不兼容 Node.js，深度使用 Bun 专属 API (Bun.file, Bun.spawn, bun:sqlite)，迁移代价极高
2. **单一维护者风险**: Can Boluk 为主要/唯一开发者
3. **Monorepo 复杂度**: ~588K 行 TS + ~33.8K 行 Rust + 10+ 个包
4. **学习曲线陡峭**: agent → loop → tools → extensions → MCP → skills → rules 多层
5. **Nightly Rust**: 需要 nightly-2026-04-29，安装门槛高
6. **Windows 支持弱**: brush shell 和 PTY 在 Windows 不成熟
7. **无 Web UI**: 纯终端（stats dashboard 是独立 web 服务）
8. **配置膨胀**: 100+ 设置跨多层
9. **Fork 分歧风险**: 与上游 Pi 的差异持续增大，合并上游变更的难度未知

### 与 Nop 平台的关联

#### 可借鉴

- **Hashline 编辑**: 内容哈希锚定概念可借鉴——Nop 代码生成管线可引入类似的"内容指纹"机制来防止模板覆盖手工修改
- **TTSR 流式规则**: 模式有意义但机制不同——Nop 的 Delta 定制作用于模型文件，TTSR 作用于 LLM 输出流；核心思想是"检测到问题立即纠正"可应用于 XPL 模板输出
- **Model-per-role**: 直接适用于 Nop AI 集成——不同复杂度任务使用不同模型/配置（如 ORM 生成用快速模型，架构设计用强模型）
- **Schema 验证的子 agent 输出**: 可用于 Nop biz 层的任务编排——子任务输出需满足 schema 约束
- **跨工具配置继承**: Nop 可考虑兼容多种 AI 工具的配置格式

#### 不适用

- Bun 运行时与 Java 生态不兼容
- LSP/DAP 集成方式（终端 PTY）不适用于 Nop 的 Web/GraphQL 场景

## Conclusion

分析进行中。oh-my-pi 在 Pi 基础上通过 ~33.8K 行 Rust 实现了性能飞跃和 IDE 深度集成，Hashline 编辑和 TTSR 流式规则是最具借鉴价值的创新。对 Nop 最直接的借鉴是 Hashline 的内容哈希锚定概念（可应用于代码生成管线的防覆盖机制）和 Model-per-role 路由模式。

## Open Questions

- [ ] Hashline 的内容哈希锚定机制能否引入 Nop 的 XLang 代码生成？
- [ ] TTSR 流式规则的模式是否适用于 Nop 的 XPL 模板输出纠错？
- [ ] oh-my-pi 的 SDK/RPC 模式能否与 Nop 的 GraphQL 层对接？

## References

- ~/ai/oh-my-pi/README.md
- ~/ai/oh-my-pi/AGENTS.md
- ~/ai/oh-my-pi/packages/ (各 package.json)
- ~/ai/oh-my-pi/crates/ (各 Cargo.toml)
- ~/ai/oh-my-pi/docs/ (~60 篇架构文档)
- https://omp.sh
- https://github.com/can1357/oh-my-pi
- https://blog.can.ac/2026/02/12/the-harness-problem/ (Hashline 理论基础)
