# MiMoCode (Xiaomi MiMo) 技术分析

> Status: open
> Date: 2026-06-12
> Scope: ~/ai/mimo-code — Xiaomi 出品的 AI coding agent，OpenCode 的重要分支
> Conclusion:

## Context

- 调研 ~/ai/ 下 agent 相关项目，MiMoCode 是 Xiaomi 基于 OpenCode 的开源分支，加入跨会话记忆、子智能体编排、工作流引擎等差异化特性
- 需要理解其架构设计、核心抽象和扩展机制，为 Nop 平台的 AI agent 集成提供参考
- 约束：项目规模大（monorepo，18 packages），基于 OpenCode fork 有大量重叠，需聚焦其独有的设计决策

## Analysis

### 项目定位

**MiMoCode** 是一个 **终端原生 AI 编程助手**，基于 [OpenCode](https://github.com/anomalyco/opencode) 深度 fork，增加跨会话持久记忆、子智能体系统、智能上下文管理、工作流编排、以及自改进 (dream/distill) 能力。

- **组织**: Xiaomi MiMo (https://github.com/XiaomiMiMo)
- **许可**: MIT
- **语言**: TypeScript (Bun 1.3.11)
- **网站**: https://mimo.xiaomi.com
- **安装量**: ~10M+ (GitHub + npm 合计，截至 2026-01)
- **入口**: `@mimo-ai/cli`

### OpenCode 继承关系

MiMoCode 明确声明为 OpenCode fork，保留 OpenCode 的所有核心能力：

- 多 Provider 支持 (20+ LLM provider via Vercel AI SDK)
- TUI 终端 UI (基于 OpenTUI)
- LSP 集成
- MCP 支持
- 插件系统
- Skill / Slash Command 系统
- SQLite 持久化

### MiMoCode 独有模块

| 模块 | 路径 | 关键文件 |
|------|------|----------|
| **Actor/Spwan 系统** | `src/actor/` | `spawn.ts` (741行), `registry.ts`, `waiter.ts` |
| **持久记忆** | `src/memory/` | `service.ts`, `reconcile.ts`, `fts-query.ts` |
| **Session Checkpoint** | `src/session/` | `checkpoint.ts` (1478行), `compaction.ts` (543行) |
| **工作流引擎** | `src/workflow/` | `runtime.ts` (1234行), `sandbox.ts`, `meta.ts` |
| **任务系统** | `src/task/` | `registry.ts` (387行), `gate.ts`, `gate-state.ts` |
| **控制平面** | `src/control-plane/` | `workspace.ts` (615行) |
| **Goal 系统** | `src/session/` | `goal.ts` (284行) |
| **Max Mode** | `src/session/` | `max-mode.ts` (467行) |
| **Agent 定义** | `src/agent/` | `agent.ts` (554行) |
| **Session 管理** | `src/session/` | `session.ts` (827行), `processor.ts`, `prompt.ts` (3355行) |

### 架构总览

```
packages/
  opencode/              @mimo-ai/cli        主力 CLI (88,968 行 TypeScript)
  ui/                    @mimo-ai/ui         共享 UI 组件
  sdk/                   @mimo-ai/sdk        客户端 SDK
  plugin/                @mimo-ai/plugin      插件框架
  shared/                @mimo-ai/shared      共享工具库
  script/                @mimo-ai/script      构建脚本
  app/                   Web 应用
  console/               控制台应用
  desktop/               桌面应用 (Electron)
  enterprise/            企业版
  identity/              身份服务
  containers/            Docker 容器
  function/              云函数
  extensions/            扩展
  slack/                 Slack bot
  storybook/             UI 组件库
```

### 核心系统设计

#### 1. Actor / Subagent 系统（核心差异化）

MiMoCode 最显著的差异化在于其 **Actor 系统** (`src/actor/spawn.ts`)，这是一个完整的子智能体 (subagent) 运行时：

**两种角色**:
- **Subagent**: 共享父 session 的子智能体，轻量级，`context:"none"` 隔离上下文
- **Peer**: 拥有独立 child session 的对等智能体

**Spawn 输入** (`SpawnInput`):
```typescript
interface SpawnInput {
  mode: "peer" | "subagent"
  sessionID: SessionID
  agentType: string        // e.g. "general", "explore", "checkpoint-writer"
  task: string
  context: "none" | "full"
  tools: ToolWhitelist
  model?: { providerID; modelID }
  background: boolean
  forkContext?: ForkContext
  format?: MessageV2.OutputFormat
  lifecycle: "ephemeral" | "persistent"
}
```

**ReAct 钩子循环**:
- **PreStop 钩子** (`actorPreStop`): turn 执行后、delivery 前 — 插件可要求 agent 继续
- **Completion Gate**: 检查任务状态，必要时重入
- **PostStop 钩子** (`actorPostStop`): delivery 后 — 写 checkpoint/dream/distill
- 上限: `MAX_PRE_REACT = 3`, `MAX_POST_REACT = 3`

**ForkContext**: 快照父 agent 的 system prompt + tools + permission 给 checkpoint-writer，实现 prefix-cache 复用。

**Return 格式**:
```markdown
**Status**: success | partial | failed | blocked
**Summary**: <一句话描述>
**Files touched**: <路径>
**Findings worth promoting**: <可跨任务传递的知识>
```

#### 2. 持久记忆系统 (`src/memory/`)

基于 SQLite FTS5 全文索引的跨会话记忆：

- **索引范围**: `MEMORY.md` (项目知识)、`checkpoint.md` (会话快照)、`notes.md` (临时笔记)
- **搜索**: `buildFtsQuery()` 将自然语言转为 token 级 FTS5 查询，BM25 排序 + 相对分数阈值
- **Reconcile**: 自动扫描文件目录，增量索引到 FTS5
- **Budgeted Read**: 按 token 预算注入记忆内容，基于重要性排序

#### 3. Session Checkpoint（智能上下文管理）

Checkpoint 系统是 MiMoCode 最具创新的模块之一 (`src/session/checkpoint.ts`, 1478 行)：

- **自动决定何时保存**: 基于模型上下文窗口压力
- **上下文重建**: 接近 token 上限时，从最新 checkpoint + project memory + 保留的近期消息重建
- **Budgeted 注入**: 用 token 预算控制 checkpoint/memory/notes 内容进入上下文
- **3 种文件**: `checkpoint.md` (结构化状态), `MEMORY.md` (持久知识), `notes.md` (临时)
- **checkpoint-writer agent**: 专用子智能体负责写 checkpoint，fork 父 agent 的 forkContext

#### 4. Workflow 引擎 (`src/workflow/runtime.ts`)

QuickJS 沙箱内的脚本工作流引擎：

- **沙箱执行**: 使用 `quickjs-emscripten` (QuickJS WebAssembly)，完全隔离，12h deadline，64MiB 内存上限
- **流程脚本**: TypeScript 子集，`export const meta = { name, description, phases }` 定义元数据
- **Host 函数注入**: `agent(prompt, opts?)`, `readFile`, `writeFile`, `glob`, `exec` 等
- **并发控制**: 全局 semaphore (默认 min(16, 2×cores))，per-run 只能缩小不能扩大
- **生命周期**: 最多 1000 agents/run，1 个 per-agent 超时
- **恢复**: `resume()` 通过 journal JSONL 重放，支持脚本变化检测
- **Isolated 模式**: `isolation: "worktree"` 为每个 agent 创建独立 worktree

```typescript
// 工作流脚本示例
export const meta = {
  name: "deep-research",
  description: "多角度代码库深度分析",
  phases: [
    { title: "架构扫描", detail: "识别模块边界和依赖" },
    { title: "深入分析", detail: "逐模块代码质量评估" }
  ]
}
// 通过 agent() 宿主函数编排子智能体
const results = await parallel(modules.map(m => () => agent(`分析模块 ${m}`, { agentType: "explore" })))
```

#### 5. 任务系统 (`src/task/`)

树形任务跟踪（`T1`, `T1.1`, `T1.2` ...）：

- SQLite 持久化的任务树
- 状态机: `open → in_progress → done | abandoned | blocked`
- 自动 checkpoint 集成
- Completion Gate: 当 agent 尝试 stop 时，gate 检查任务状态，需要时重入

#### 6. Max Mode (`src/session/max-mode.ts`)

并行 best-of-N 推理 + Judge 模型选择：

- 多个 token 预算/不同模型并行生成
- Judge 模型评估结果质量
- 选择最佳响应

#### 7. Goal 系统 (`src/session/goal.ts`)

- `/goal` 命令设置会话停止条件
- 独立 Judge 模型评估条件是否满足
- 防止过早乐观停止

#### 8. Dream & Distill

- **`/dream`**: 扫描近期会话，提取持久知识到 MEMORY.md，清理过期条目
- **`/distill`**: 发现重复手动工作流，打包为可复用 skill / subagent / command

### Agent 体系

| Agent | 工具权限 | 用途 |
|-------|----------|------|
| **build** | 全部 | 默认 agent，完整开发权限 |
| **plan** | 只读 (read-only) | 代码探索和方案设计 |
| **compose** | specs 驱动 | 编排式开发（spec → 实现 → review → merge） |
| **explore** | 只读 (专用 prompt) | 代码库探索 |
| **checkpoint-writer** | 受限写 (仅 memory path) | 自动写 checkpoint |
| **general** | 子智能体通用 | subagent 的默认 agent 类型 |
| **dream-distill** | 读 memory | 知识提取和 skill 发现 |

### 存储层

- **主力**: SQLite (Bun SQLite + Drizzle ORM)
- **全文本搜索**: SQLite FTS5
- **会话**: Projector 架构 (Session → Message → parts)
- **迁移**: JSON-based 迁移系统 (src/storage/json-migration.ts)
- **Schema**: Drizzle ORM schema 定义
- **Claude Code 导入**: 一次性的 Claude Code 会话导入

### 配置层级

```
default → ~/.config/mimocode/mimocode.json (global) → .mimocode/mimocode.json (project) → CLI args
```

使用 `mimocode.json` (OpenCode 兼容的 `opencode.json` 风格)，支持 50+ 配置项。

### 工具系统 (~60 个工具)

继承 OpenCode 的全部工具，包括: `read`, `write`, `edit`, `bash`, `grep`, `glob`, `ls`, `codeSearch`, `webSearch`, `webFetch`, `greptile`, `actorTool`, `skill`, `mcp` 等。

### 工作流依赖

| 依赖 | 用途 |
|------|------|
| **effect** (v4.0.0-beta.48) | 核心函数式编程框架 |
| **ai** (v6.0.168) | Vercel AI SDK |
| **zod** (v4.1.8) | Schema 验证 |
| **quickjs-emscripten** | 工作流沙箱 |
| **drizzle-orm** | ORM / SQLite |
| **hono** | HTTP 服务器 |
| **solid-js** | UI 渲染 |
| **opentui** | 终端 UI 框架 |
| **yargs** | CLI 参数解析 |

## 核心设计模式总结

| 模式 | 说明 |
|------|------|
| Effect Functional Runtime | 全项目使用 Effect 作为依赖注入和副作用管理框架 |
| Actor Model | spawn/subagent 作为 actor，通过 Deferred 通信 |
| Projector Architecture | session → message-v2 → parts 的事件源投影 |
| ForkContext + Prefix Cache | 子 agent 快照父 agent 上下文复用 prompt cache |
| Checkpoint Compaction | 自动检测 token 压力 → 压缩 → 重建上下文 |
| Workflow Sandbox | QuickJS WebAssembly 隔离执行工作流脚本 |
| Hook ReAct Loop | preStop / postStop 插件钩子，支持多轮重入 |
| Budgeted Injection | token 预算控制上下文内容注入 |
| FTS5 Full-Text Memory | SQLite FTS5 作为持久记忆搜索引擎 |
| Completion Gate | 独立 Judge 模型验证任务完成状态 |

## 优势

1. **持久记忆系统** — SQLite FTS5 实现的跨会话记忆，比 Claude Code 文件级记忆更结构化
2. **Actor/Subagent 架构** — 完整的子智能体生命周期管理，支持 parallel 执行
3. **Checkpoint 智能上下文** — 自动检测 token 压力 + 预算注入，长会话保持上下文新鲜
4. **Workflow 沙箱** — QuickJS 隔离执行，TypeScript 子集编排，支持重放/恢复
5. **Completion Gate** — Judge 模型验证任务完成，防止过早停止
6. **强大的任务树** — 树形任务跟踪 + 自动 checkpoint 集成
7. **Dream/Distill 自改进** — 从历史会话自动提取知识和 skill
8. **Max Mode** — 并行 best-of-N 推理
9. **OpenCode 生态兼容** — 继承 OpenCode 全部 Provider/TUI/MCP/Plugin 生态
10. **极快的增长** — 10M+ 安装量，六个月从 0 到主流
11. **企业版支持** — Desktop (Electron)、Slack bot、控制平面

## 劣势

1. **Bun 依赖** — 必须 Bun 1.3.11，不支持纯 Node.js 运行
2. **包体积大** — 依赖节点多 (250+ npm 包)
3. **Effect 框架陡峭** — 全项目 Effect 函数式编程，学习曲线高
4. **复杂度面大** — Actor/Checkpoint/Workflow/Task/Goal/MaxMode 多层系统叠加
5. **文档偏有限** — 主要是 README 和 AGENTS.md，缺乏 API 文档
6. **OpenCode 分支锁定** — 分叉后的差异管理成本，持续跟进 upstream 的挑战
7. **QuickJS 沙箱限制** — TypeScript 子集能力受限，无法使用 import/export
8. **无独立 Agent Framework** — agent 系统与 OpenCode CLI 紧耦合，无法独立嵌入

## 与 Nop 平台的关联

### 可借鉴

- **Actor/Subagent 模式**: spawn + ReAct 钩子循环，可作为 Nop AI Agent 运行时的参考
- **Checkpoint 智能上下文管理**: Budgeted Injection + 自动 compaction，Nop 工作流的长会话处理可借鉴
- **Workflow 沙箱**: QuickJS 隔离执行 + TypeScript 子集编排，与 Nop XLang 的执行引擎理念相似
- **Completion Gate**: 独立 Judge 验证任务完成，Nop 工作流的条件评估可参考
- **FTS5 持久记忆**: SQLite FTS5 作为 Agent 长期记忆的轻量级方案
- **Hook ReAct 循环**: preStop/postStop 插件钩子，与 Nop Delta/Interceptor 机制对应

### 不适用

- Bun / TypeScript 技术栈与 Nop (Java) 不同
- Effect 函数式框架在 Java 生态无直接对应
- 与 OpenCode 紧耦合，无法直接嵌入 JVM 进程
- QuickJS 沙箱在 JVM 生态用 GraalVM 或 Nashorn 替代

## Conclusion

MiMoCode 是目前 OpenCode 生态中最具野心和能力的分支。其核心创新——持久记忆、智能上下文管理、Actor/Subagent 系统、Workflow 沙箱引擎——都是在 Claude Code 和 OpenCode 的基线之上构建的真正增量价值。10M+ 的安装量验证了这些特性的市场需求。

对 Nop 最有价值的借鉴是: (1) Checkpoint/Compaction 驱动的智能上下文管理——解决长任务 token 溢出问题； (2) Actor/Subagent 的 ReAct 钩子循环——为 Agent 运行时提供插件化扩展点； (3) Workflow 沙箱引擎——QuickJS 隔离执行模式可用于 Nop XLang 的脚本执行。

## Open Questions

- [ ] MiMoCode 的 checkpoint compaction 策略能否映射到 Nop 的 Delta 定制层？
- [ ] Actor/Subagent 系统是否可作为 Nop Process/Workflow 引擎的 Agent 节点扩展模型？
- [ ] Workflow 沙箱 (QuickJS) 与 Nop 的 XLang 引擎在隔离执行理念上如何对比？

## References

- ~/ai/mimo-code/README.md
- ~/ai/mimo-code/AGENTS.md
- ~/ai/mimo-code/package.json
- ~/ai/mimo-code/packages/opencode/src/actor/spawn.ts
- ~/ai/mimo-code/packages/opencode/src/memory/service.ts
- ~/ai/mimo-code/packages/opencode/src/session/checkpoint.ts
- ~/ai/mimo-code/packages/opencode/src/workflow/runtime.ts
- ~/ai/mimo-code/packages/opencode/src/task/registry.ts
- ~/ai/mimo-code/packages/opencode/src/agent/agent.ts
- https://github.com/XiaomiMiMo/MiMo-Code
- https://mimo.xiaomi.com
