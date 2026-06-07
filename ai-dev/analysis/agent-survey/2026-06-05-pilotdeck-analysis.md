# PilotDeck 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/PilotDeck — WorkSpace-Centric Agent Operating System
> Conclusion:

## Context

- PilotDeck 是清华大学 THUNLP / ModelBest / OpenBMB / AI9Stars 联合开发的开源 Agent 操作系统
- 2026-05-28 开源，AGPL-3.0 许可
- 核心理念：以 WorkSpace 为基本单元，围绕 White-box Memory、Smart Routing、Always-on 三大支柱
- 调研目的：理解学术机构出品的 Agent OS 架构，对比工业界 Agent 框架设计

## Analysis

### 项目定位

- **组织**: 清华大学 THUNLP / ModelBest / OpenBMB / AI9Stars
- **许可**: AGPL-3.0
- **版本**: 0.1.0（初始开源）
- **语言**: TypeScript (strict, ^5.9.3)
- **运行时**: Node.js >= 22
- **前端**: React 18 + Vite + Express
- **LOC**: ~73,694 行 TS (核心) + ~46,303 行 TS/TSX (UI) + ~21,683 行 (UI server) ≈ **141,680 行**
- **Commit**: 33394d1 (2026-05-29)
- **GitHub**: https://github.com/OpenBMB/PilotDeck

### 顶层架构

```
PilotDeck
├── src/                        # 后端核心 (~73,694 行, 476 .ts 文件)
│   ├── agent/                  # Agent 循环 & 运行时
│   ├── always-on/              # 常驻后台执行
│   ├── context/                # 上下文 & 记忆系统
│   ├── cron/                   # 定时任务
│   ├── extension/              # 插件 & 扩展
│   ├── gateway/                # 多通道网关 (CLI/TUI/IM/Web)
│   ├── lifecycle/              # 生命周期钩子
│   ├── mcp/                    # MCP 客户端
│   ├── model/                  # 模型协议 & Provider
│   ├── permission/             # 权限系统
│   ├── pilot/                  # 配置管理
│   ├── router/                 # 智能路由
│   ├── session/                # 会话 & 文件系统隔离
│   ├── task/                   # 任务管理
│   ├── tool/                   # 工具系统
│   ├── adapters/               # 通道适配器
│   └── cli/                    # CLI 入口
├── ui/                         # React Web UI (~46,303 行 + ~21,683 行 server)
├── skills/                     # 内置技能包 (6 个)
├── tests/                      # 测试
└── products/                   # 产品配置
```

### 核心模块详解

#### 1. Agent Loop — AsyncGenerator 架构

`AgentLoop.run()` 是一个 **AsyncGenerator**，yield `AgentEvent` 对象，共 35+ 事件类型。

**循环流程**:
1. **自动压缩检查**: 每个 turn 前评估 token budget，必要时压缩上下文
2. **路由决策**: `router.decide()` → 路由后压缩（若模型 context 更小）
3. **模型执行**: `router.execute()` 流式返回，实时转发事件
4. **错误恢复**: prompt_too_long → 截断头部重试；max_output_reached → 输出 token 重试
5. **JSON 自修正**: invalid_tool_arguments 最多重试 3 次
6. **工具收集**: 从 assistant message 提取 `CanonicalToolCall`
7. **工具执行**: `scheduler.executeAll()` 并发执行（安全工具并行，不安全顺序）
8. **权限处理**: 每个 tool call 经过 permission + lifecycle hooks
9. **熔断器**: 连续 3 个 turn 所有工具调用失败 → 终止
10. **继续循环**: 递增 turn 计数，yield turn_continued

**关键设计**:
- **Canonical 消息格式**: `CanonicalMessage` + `CanonicalContentBlock[]` 与 LLM Provider 解耦
- **Content Gate**: 一旦 yield 内容给消费者，fallback/retry 锁定，防止重复文本
- **Tiktoken 精确计数**: 使用 o200k_base BPE 编码（非 char/4 估算），对 CJK 内容关键

#### 2. Sub-Agent — Fork 模型

4 种内置子 agent 类型:

| 类型 | 工具 | 只读 | 用途 |
|------|------|------|------|
| `general-purpose` | `["*"]` 全部 | 否 | 通用任务 |
| `explore` | read_file, grep, glob, bash | 是 | 代码探索 |
| `plan` | read_file, grep, glob | 是 | 规划分析 |
| `verify` | read_file, grep, glob, bash | 是 | 验证检查 |

- 从 parent **fork 消息历史**
- **隔离工具注册表**: 无嵌套 `agent`、`always_on_*`、`ask_user_question`
- 强制 **5 字段输出格式**（Scope, Result, Key files, Files changed, Issues）
- 深度追踪（默认最大深度 = 1）
- Clone parent 的 readFileState 和 writeSnapshots
- 活动事件回传为 `subagent_*` 事件

#### 3. Memory — EdgeClaw 白盒记忆

**EdgeClaw** 是内嵌的记忆子系统（vendored，非 git submodule，~14,665 行）:

```
MemoryResolver (接口)
  ├── retrieve(input)    → 检索相关 systemContext
  └── captureTurn(input) → 捕获 turn 快照索引
```

**白盒特性**:
- 记忆的生成、提取、存储、检索全程可见
- 可编辑/删除单条记忆条目
- **Dream Mode**: 空闲时自动整合记忆，支持一键回滚
- WorkSpace 级别隔离: A 的记忆不会泄漏到 B

**上下文压缩三级策略**:
1. **MicroCompaction** (Tier 1): 截断旧 tool_result，无模型调用
2. **SnipEngine** (Tier 2): 修剪中间 turn，保留头尾锚点
3. **CompactionEngine** (Tier 3): 通过模型调用做完整摘要

#### 4. Smart Router — 成本优化路由

`RouterRuntime` (809 行) 路由决策管线:

1. **Session Sticky**: 复用上一轮 provider/model
2. **Custom Router**: 插件提供的自定义路由
3. **Scenario Detection**: 区分 subagent / explicit / default
4. **Token Saver**: 用 judge 模型分类复杂度（simple/medium/complex），路由到对应成本模型
5. **Auto-orchestration**: 注入编排 prompt，可能精简工具集
6. **Fallback Chain**: 主模型失败时尝试备选链

**实测数据**（README 声称）:
- 小红书运营场景: Smart Routing 开启 $2.83 vs 关闭 $12.58，**节省 ~77%**（README 声称 ~70%）
- 7 个复杂任务: Sonnet 4.6 + MiniMax-M2.7 组合得分 70.6 vs Sonnet 4.6 单体 69.1，**成本 1/6**

**Model Catalog**: 30+ 模型硬编码目录，支持 OpenAI/Anthropic 两种协议

#### 5. Always-on — 常驻后台执行

`DiscoveryFire` (1,045 行) 执行 5 阶段管线:

1. **Discovery**: 模型分析最近聊天历史，识别待办任务
2. **Workspace Preparation**: git worktree 或 snapshot copy 隔离
3. **Execution**: Agent loop 执行发现的任务
4. **Report Generation**: 生成总结报告
5. **Workspace Cleanup**: 清理工作区

**门控检查**: enabled? → 项目存在? → 休眠? → agent 忙? → 最近有用户消息? → 冷却期? → 日预算?

**全部文件存储**: JSON/JSONL，无数据库依赖

#### 6. Extension — 插件架构

**Plugin Manifest**:
```typescript
{
  name, version,
  commands?, agents?, skills?, hooks?,
  mcpServers?, lspServers?, outputStyles?,
  marketplace?, mcpb?, settings?
}
```

**7 种 Contribution 类型**: Tool, Command, Hook, MCP Server, Permission Rule, Prompt, Router

**28 种 Hook 事件**: PreToolUse, PostToolUse, SessionStart/End, PreModelRequest, PermissionRequest, PreCompact, SubagentStart/Stop, WorktreeCreate/Remove, Elicitation 等

**5 种 Hook 执行器**: Agent, Callback, Command (shell), HTTP, Prompt

#### 7. Tool System — 19 个内置工具

| 类别 | 工具 |
|------|------|
| 文件系统 | read_file (712行), edit_file, write_file, edit_notebook (439行) |
| Shell | bash (+ commandRunner), glob, grep (412行) |
| Web | web_fetch, web_search (676行, GLM/Tavily/custom) |
| Agent | agent (492行, 子 agent 调度), structuredOutput |
| 交互 | ask_user_question, plan_mode, planFile, todoWrite, taskTools |
| MCP | mcpTool, mcpResources, readSkill |

**工具调度**: ConcurrentToolScheduler（安全工具并行）+ SequentialToolScheduler（全顺序）

**执行管线**: 中断检查 → 查找 → 输入验证 → PreToolUse Hook → 权限决策 → 审计记录 → 执行 → 大小限制 → PostToolUse Hook

#### 8. Gateway — 多通道网关

**Client/Server 架构**: InProcessGateway / RemoteGateway / GatewayServer (WebSocket)

**23 个通道**: cli, tui, web, feishu, weixin, qq, telegram, discord, slack, matrix, mattermost, signal, whatsapp, bluebubbles, dingtalk, wecom, wecom_callback, email, sms, homeassistant, api_server, webhook, test（+ 可扩展 string）

**Gateway 接口** (387 行): submitTurn, abortTurn, respondElicitation, permissionDecide, cronCRUD, skillsCRUD, alwaysOn 控制, 热重载配置

#### 9. Permission — 5 种模式

| 模式 | 行为 |
|------|------|
| `default` | 写/Shell 工具需审批 |
| `plan` | 只读探索 + 规划 |
| `acceptEdits` | 文件编辑自动批准，Shell 需审批 |
| `bypassPermissions` | 全部自动批准 |
| `dontAsk` | 不提示，拒绝模糊请求 |

#### 10. Session — 文件系统隔离

- `FileHistoryStore` (460 行): 跟踪文件编辑，支持撤销/回退
- Git Worktree 支持: `findGitRoot()`, `resolveCanonicalRoot()`
- Resume: 从 transcript replay 重建 AgentSession
- Transcript: JSONL 格式持久化 + InMemory 缓冲 + Chain 组合

#### 11. Cron — 定时任务

- 两种调度: `once`（一次性）+ `cron`（标准 cron 表达式 + 时区）
- 4 个内置工具: CronCreate, CronDelete, CronList, CronStop
- 持久化存储（JSON/JSONL）

### 技术亮点

1. **Canonical 消息抽象**: 与 LLM Provider 完全解耦，Anthropic/OpenAI 两种协议适配
2. **AsyncGenerator Agent Loop**: 天然支持流式 UX，所有通道适配器统一消费
3. **三级上下文压缩**: 从零成本（micro）到高成本（full summarization）渐进策略
4. **Smart Routing 实测有效**: 学术论文级别的 A/B 测试数据支撑（节省 ~77%）
5. **Always-on 5 阶段管线**: 离线任务发现 + WorkSpace 隔离 + 报告生成
6. **23 通道适配器**: 覆盖 CLI/TUI/Web/IM (飞书/微信/QQ/Telegram/Discord/Slack 等)
7. **文件存储无数据库**: 全 JSON/JSONL，降低部署门槛
8. **插件贡献系统**: 7 种 contribution 类型 + 28 种 hook 事件
9. **EdgeClaw 白盒记忆**: 可审计、可编辑、可回滚的记忆系统
10. **Sub-Agent Fork 模型**: 从 parent fork 消息历史 + 隔离工具集

### 劣势

1. **AGPL-3.0 许可**: 商业使用受限，与 MIT/Apache 项目生态不同
2. **初始版本 0.1.0**: API 可能大幅变动，生产就绪度存疑
3. **无数据库依赖是双刃剑**: JSON/JSONL 在大规模数据下性能堪忧
4. **Token Saver 依赖额外 LLM 调用**: 分类调用本身有成本，简单任务可能不划算
5. **仅 2 种协议适配器**: Anthropic + OpenAI 协议，但通过 OpenAI 兼容协议已覆盖 30+ 模型提供商（DeepSeek/Qwen/Kimi/MiniMax 等）
6. **单仓巨大**: 141K+ 行全在一个 repo，UI 和后端未独立发布
7. **测试覆盖薄**: tests/ 仅 3 个文件，E2E 测试需手动触发
8. **项目处于早期**: GitHub Issues/PR 活动尚在初期阶段，API 可能变动

### 竞品对比

| 维度 | PilotDeck | VoltAgent | Claude Code | OpenHands |
|------|-----------|-----------|-------------|-----------|
| **定位** | Agent OS | Agent 平台 | Coding Agent | Coding Agent 平台 |
| **许可** | AGPL-3.0 | MIT | 商业 | MIT |
| **语言** | TypeScript | TypeScript | TypeScript | Python |
| **核心理念** | WorkSpace 隔离 | 单包核心 | 项目级 AI | 沙箱隔离 |
| **记忆系统** | 白盒 EdgeClaw | 三适配器 Memory | 黑盒压缩 | 无内建 |
| **智能路由** | 三级分类 + fallback | 无（用户选模型） | 无 | 无 |
| **Always-on** | 5 阶段管线 | 无 | 无 | 无 |
| **通道数** | 23 | HTTP (Hono/Elysia) | CLI only | Web UI |
| **插件系统** | 7 种 Contribution | 无 | 无 | 无 |
| **上下文压缩** | 三级渐进 | 无（AI SDK 管理） | 单层压缩 | 无 |
| **LOC** | ~142K | ~60K (core) | N/A | ~100K |

### 与 Nop 平台的关联

#### 可借鉴 (高价值)

1. **Canonical 消息抽象**: Nop 可定义 Java 版 `CanonicalMessage` + `CanonicalContentBlock`，通过 XMeta 定义 schema，实现 LLM Provider 解耦
2. **三级上下文压缩**: MicroCompaction → SnipEngine → CompactionEngine 的渐进策略可映射到 Nop 的 biz 层
3. **Smart Routing**: Token Saver 的 judge 分类 + 分级路由模式可用于 Nop 的模型选择策略
4. **Always-on 5 阶段管线**: Discovery → Prepare → Execute → Report → Cleanup 可作为 Nop 异步任务的参考架构
5. **Gateway 多通道**: 23 通道适配器模式可用于 Nop 的多端接入层
6. **Plugin Contribution 系统**: 7 种贡献类型（Tool/Command/Hook/MCP/Permission/Prompt/Router）可作为 Nop 扩展点设计的参考
7. **WorkSpace 隔离**: Git worktree + snapshot copy 的文件系统隔离对 Nop 多项目并行有启发
8. **Tiktoken 精确计数**: 对 CJK 场景的 token 精确估算方法可移植到 Java

#### 可借鉴 (中等价值)

9. **EdgeClaw 白盒记忆**: 可审计/可编辑/可回滚的记忆系统对 Nop 的 AI 记忆管理有参考价值
10. **Sub-Agent Fork**: 消息历史 fork + 工具隔离的模式可用于 Nop 的多 agent 场景
11. **Permission 5 模式**: default/plan/acceptEdits/bypass/dontAsk 的权限分级可参考

#### 不适用

- TypeScript 技术栈不可移植
- 文件存储无数据库的策略与 Nop 的 ORM-first 哲学冲突
- AGPL-3.0 许可限制商业集成
- 代码优先配置与 Nop 模型优先不同
- 单仓巨石架构不适用于 Nop 的模块化体系

## Conclusion

PilotDeck 是学术机构出品的最为完整的 Agent OS 实现之一，Canonical 消息抽象、三级上下文压缩、Smart Routing 成本优化、Always-on 5 阶段管线、23 通道适配器、7 种 Plugin Contribution 的组合在同类项目中独具特色。尤其 Smart Routing 的实测 A/B 数据（~77% 成本节省 + 复杂任务得分持平）和 Always-on 的离线任务发现是其他框架少有的能力。对 Nop 最有价值的借鉴：Canonical 消息抽象、三级压缩策略、Smart Routing 模式、多通道 Gateway、以及 Plugin Contribution 系统。但 AGPL-3.0 许可和文件存储策略限制了直接集成的可能性。

## Open Questions

- [ ] Canonical 消息格式能否用 XMeta 定义并自动生成 Java 类？
- [ ] 三级压缩策略如何在 Nop 的 ORM 上下文中实现（而非 JSONL 文件）？
- [ ] Smart Routing 的 judge 分类调用本身有成本，在 Nop 场景下是否值得？
- [ ] Always-on 的 Discovery 阶段如何与 Nop 的工作流引擎 (nop-wf) 协作？
- [ ] AGPL-3.0 许可是否允许在 Nop 项目中参考其架构模式？

## References

- ~/ai/PilotDeck/README.md
- ~/ai/PilotDeck/README.zh.md
- ~/ai/PilotDeck/package.json
- ~/ai/PilotDeck/src/agent/loop/AgentLoop.ts (1,429 行)
- ~/ai/PilotDeck/src/router/RouterRuntime.ts (809 行)
- ~/ai/PilotDeck/src/always-on/runtime/DiscoveryFire.ts (1,045 行)
- ~/ai/PilotDeck/src/extension/skills/SkillManager.ts (842 行)
- ~/ai/PilotDeck/src/gateway/protocol/types.ts (387 行)
- ~/ai/PilotDeck/src/tool/builtin/ (23 个工具)
- ~/ai/PilotDeck/src/context/memory/edgeclaw-memory-core/ (~14,665 行)
- https://github.com/OpenBMB/PilotDeck
- https://pilotdeck.openbmb.cn
