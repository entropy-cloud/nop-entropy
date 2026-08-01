# Goose Provider Registry / Hook / Recipe 深度分析 & Nop AI Agent 扩展机制

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/goose`（block/goose，Rust 编写的桌面 Agent Harness，最近月更活跃）vs `nop-ai-agent`（Provider/Hook/MCP 抽象）
> Conclusion:

## 一、总览

**Goose** 是一个 Rust 编写的 Agent Harness（桌面/CLI），最近一个月更新非常活跃（v1.0.17+ 多个版本，含 MCP 生态接入）。其核心抽象是四层：**Provider Registry**（模型供应商注册）、**MCP Extension**（工具/资源集成）、**Hook**（生命周期事件回调）、**Recipe**（可复用 agent 行为配方）。

| 维度 | Goose | Nop AI Agent |
|------|-------|--------------|
| 供应商抽象 | Provider Registry（认证/模型能力） | ChatModelProvider 接口（nop 有类似抽象） |
| 工具集成 | MCP Extension（protocol 标准） | MCPRegistry + ToolRegistry |
| 生命周期 | Hook（11 个 HookEvent 事件点回调） | Middleware + Hook（12 个 AgentLifecyclePoint） |
| 行为配方 | Recipe（prompt + 工具集 + 配置快照） | 无（DSL 静态配置但无"配方"组合层） |
| 语言 | Rust（CLI） + TS（SDK） | Java 21 |

**核心结论先行**：Goose 对 nop 的最大借鉴不是底层引擎（nop 的 middleware/hook 体系已更完整），而是**Recipe（配方）这一组合层**——把「prompt + 工具集 + 配置」打包成可分享的行为单元，正好补上 nop 从"静态 DSL 配置"到"可组合行为单元"的中间层。其次是 Provider Registry 的认证/能力统一管理。

## 二、Context（调研背景）

- **为什么需要这个分析**：7 月博客文章《Goose Harness 深度解析：MCP 生态的 Agent Harness》将其定位为"MCP 生态的 agent harness"，与 nop 的 MCP 集成方向直接相关。
- **要回答的问题**：Goose 的 Provider Registry、Hook、Recipe 三层与 nop 现有抽象的差异；Recipe 如何在 nop 落地。
- **约束**：nop 是 Java DSL-first，配置用 XDEF；Goose 是 Rust CLI + JSON 配置。

## 三、核心机制详解

### 3.1 Provider Registry

- 统一管理模型供应商：认证（API key 管理/登录）、模型能力（上下文长度/工具支持）、供应商列表。
- 抽象层级：Provider → ProviderManager →（模型名 → 能力）。
- 对 nop：`ChatModelProvider` 已实现同一目标（`io/nop/ai/llm`），nop 的优势是统一 ChatOptions/Embedding 抽象；Goose 的增值点在**多供应商切换的 UX 与认证管理**。

### 3.2 MCP Extension

- 工具经 MCP protocol 接入：stdio/HTTP；Extension 负责连接生命周期与工具发现。
- 与 nop 的 `MCPRegistry` 高度一致——MCP 已成工具集成事实标准，nop 方向正确。

### 3.3 Hook

- 生命周期事件（`HookEvent` 枚举 **11 个**）：PreToolUse/PostToolUse/PostToolUseFailure/SessionStart/SessionEnd/UserPromptSubmit/BeforeReadFile/AfterFileEdit/BeforeShellExecution/AfterShellExecution/Stop，通过配置文件声明式注册。
- nop 的 middleware 洋葱链 + Hook 12 生命周期点（PRE_CALL/PRE_REASONING/POST_REASONING/PRE_ACTING/POST_ACTING/ON_ERROR/POST_CALL/REASONING_CHUNK/PRE_COMPACT/POST_COMPACT/BEFORE_TOOL_RESULT_PROCESSED/AFTER_TOOL_RESULT_PROCESSED）是更工程化的实现（有序、可配置、可组合）。
- **nop 不落后，此层无新借鉴**。

### 3.4 Recipe（最大借鉴点）

- Recipe = 一个可分享的"agent 行为配方"：包含 prompt（角色/规则）+ 推荐工具集 + 配置快照。
- 用户一键启用：`goose recipe add <名称>` → 自动配置 session。
- 本质：把 agent 的"性格 + 工具 + 配置"打包成可复用单元（类似 skill 但带工具集与配置）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Hook 失败传播**：PreToolUse 等 hook 返回 deny 中断链——被拒工具调用可调整参数重试。
- **Recipe 快照重试**：Recipe 含配置快照——**行为可复现**（重试时配置一致）。
- **Provider Registry 容错**：多供应商注册——单供应商失败可切换重试。
- **对 nop 的启示**：Recipe 配置快照让重试行为一致（可复现）；Provider 切换是 nop 模型路由的参考。

## 四、优缺点

### 优点

1. Recipe 让 agent 行为可分享、可复用、可审计（配置快照）。
2. MCP-first 生态策略正确（一次集成到处可用）。
3. Rust 性能与单二进制分发方便。

### 缺点

1. 引擎层（middleware/hook 深度）不如 nop 精细；Recipe 是配置层而非运行时钩子。
2. 未提供 Java 服务端场景（纯客户端 harness）。
3. Provider 抽象未统一 OpenAI/Anthropic 能力差异（nop 的 ChatOptions 更统一）。

## 五、对 nop-ai-agent 的借鉴要点（核心价值）

### 5.1 Recipe 组合层（最高价值）

nop 现状：AgentModel 静态配置（工具列表 + prompt + hook 配置）是**单体**的——没有"多个 agent 共享一个配方，配方 = 模板 + 参数"的组合层。

```
nop Recipe 落地：
  recipe（XDEF 配置，可分享）：
    - name / description / version
    - promptTemplate（角色 + 规则，可参数化）
    - toolSets（引用 ToolSet 定义，而非逐个工具）
    - modelConfig（provider + temperature + maxTokens 快照）
    - hooks（引用 hook 链配置）
  AgentModel ← applies recipe（agent 可引用 1..n recipes 叠加）
```

- 与 skill 的边界：skill 是"能力注入"（新增工具/知识），recipe 是"行为配置"（整体形态）。
- nop 的 `AgentModel` 已可承载 recipe（其本身就是配置模型），需补的是**模板参数化 + 组合叠加 + 分享格式**。

### 5.2 Provider Registry 认证统一（中价值）

- nop 各 provider 适配器各自管理认证；借鉴 Goose 集中认证管理（secret 存储 + 供应商级配置），但注意 nop 的服务端场景应复用 nop-config 体系。

### 5.3 MCP 方向确认（已对齐）

- nop 的 MCPRegistry/工具注册方向与 Goose 一致，无需改动；差距是 MCP 生态丰富度（非代码问题）。

## 六、结论

- Goose 三层中，Hook/Provider 与 nop 现有抽象对齐或更弱；**Recipe 配方层**是唯一高价值借鉴——把"prompt + 工具集 + 配置"打包为可分享行为单元。
- nop 落地建议：新增 `recipe.xdef`（模板化 + 可叠加）+ AgentModel 增加 recipe 引用；与 skill 体系正交。
- 后续工作：指向 `ai-dev/design/nop-ai-agent/` 中 agent-model DSL 设计的 recipe 扩展。

## Open Questions

- [ ] recipe 与 skill/agent-template 的边界如何定义（三者在 nop 中分别是什么）？
- [ ] recipe 叠加冲突时的解析策略（后写覆盖 vs 显式优先级）？
- [ ] recipe 是否可动态引用（运行时加载外部 recipe 文件）？

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D8**（Recipe 配方组合层）、**D1**（11 HookEvent）、**D12**（Recipe 快照重试可复现）。缺失/薄弱：D5、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **Provider 抽象**：nop `ChatModelProvider` + 统一 ChatOptions/Embedding 抽象，优于 goose 的简单认证管理。
- **Hook 体系**：nop 12 个 AgentLifecyclePoint + middleware 洋葱链（可拦截），goose 仅 11 个 HookEvent（声明式注册）——nop 更工程化、更细粒度。
- **MCP 集成**：nop `MCPRegistry` + ToolRegistry 与 goose 的 MCP Extension 能力相当，但 nop 有 safety 链加持。

**必要参考的增量（以超越方式吸收）**：
- **Recipe 配方组合层**：nop AgentModel 是单体静态配置——"prompt + 工具集 + 配置快照"打包为可分享行为单元是真正增量（以 `recipe.xdef` 模板化 + 可叠加实现，超越 goose 的 JSON 快照）。

**总评**：nop-ai-agent 在 Provider/Hook/MCP 上**全面超越**；仅 Recipe 组合层值得吸收（以 XDEF 模板 + 参数化超越 goose 的静态 Recipe）。

## References

- `~/ai/goose/`（crates/hook、crates/provider、configs/recipes、docs/docs/recipes.md）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md`、`00-vision.md`
- `nop-ai-agent/src/main/java/io/nop/ai/agent/`（model/AgentModel、hook、middleware）
- `ai-dev/analysis/agent-survey/2026-06-05-opencode-analysis.md`（opencode 的 skill 体系对照）
