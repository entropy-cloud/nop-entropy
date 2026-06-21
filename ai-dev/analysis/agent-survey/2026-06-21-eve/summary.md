# Vercel `eve` 框架调研

> Status: resolved
> Date: 2026-06-21
> Scope: Vercel eve（filesystem-first durable AI agent 框架）的设计哲学、架构、API、运行时、实现与可借鉴模式
> Conclusion: eve 用「目录即配置、路径即身份、会话即 durable workflow」三大理念构建高工程化 agent 框架；其 filesystem-first 哲学、durable-by-default、单依赖+vendoring、一等公民 eval 系统值得 Nop AI agent 设计借鉴，但强 Vercel 绑定且 pre-1.0 不保证兼容，架构不可直接照搬
> 调研对象：`~/ai/eve`（`eve@0.11.10`，beta）

## eve 是什么

`eve` 是 Vercel 推出的 **filesystem-first framework for durable backend AI agents**（文件系统优先的、面向持久化后端 AI agent 的框架）。核心理念：**你把 agent 写成磁盘上的一个目录**——指令、技能、工具、连接、通道、子代理、调度都是文件——eve 负责发现、编译、运行。agent 跑在 Vercel 上叫"Vercel Agent"，跑在别处是普通 HTTP 服务。

一句话定位：**目录即配置、文件路径即身份、会话即 durable workflow**。

## 为什么值得调研

1. **filesystem-first 是一种强约束的设计哲学**——与 Nop AI agent 的"DSL-First + VFS 统一抽象"形成鲜明对比，两者都追求"可读即真相"，但真相载体（文件系统 vs DSL）和统一抽象（物理目录 vs VFS）截然不同。
2. **Durable by default**——每个会话是 Workflow DevKit 的 durable run，自动 checkpoint、崩溃恢复、跨天跨重启。这是 agent 框架里少见的工程深度。
3. **工程化水平极高**——单一运行时依赖（nitro）、vendored 27 个三方库、编译产物可 audit、一等公民的 eval 系统。
4. **三层分离架构**（Channel / Harness / Runtime）清晰解决了传输归一化、模型调用、持久化的关注点。

## 报告导览

| 文档 | 内容 |
|---|---|
| [01-overview.md](01-overview.md) | 定位、技术栈、运行时依赖、生态、一句话总结 |
| [02-philosophy-and-architecture.md](02-philosophy-and-architecture.md) | filesystem-first 哲学、三层架构、源码模块职责、设计原则 |
| [03-project-layout.md](03-project-layout.md) | agent 目录结构、各 slot 详解、概念词典、身份派生规则 |
| [04-api.md](04-api.md) | 核心 `define*` API 清单（defineAgent/Tool/Skill/Channel/...） |
| [05-runtime.md](05-runtime.md) | 编译→加载→执行全链路、Discovery/Compile/Load/Workflow 四阶段 |
| [06-durability-and-state.md](06-durability-and-state.md) | Durable 会话、turn/step 模型、park/resume、defineState、消息语义 |
| [07-examples-and-patterns.md](07-examples-and-patterns.md) | weather-agent 解剖、HITL/Subagent/Connection/Sandbox 等典型模式 |
| [08-evals.md](08-evals.md) | Eval 系统：defineEval、`t` 上下文、gate/soft、LLM-judge、运行方式 |
| [09-toolchain-deployment.md](09-toolchain-deployment.md) | CLI 命令、构建管线、`#compiled/*` 虚拟模块、Vercel/自托管部署 |
| [10-insights.md](10-insights.md) | 与 Nop/IDE-agent/LangChain 的对比、可借鉴的设计模式、关键启示 |

## 阅读建议

- **想快速理解**：读 `01` + `02` + `03`
- **想深入实现**：读 `05` + `06`
- **想看实际用法**：读 `07` + `08`
- **想提炼借鉴点**：读 `10`

## 关键事实速查

| 项 | 值 |
|---|---|
| 仓库 | `github.com/vercel/eve` |
| 包名 / 版本 | `eve` / `0.11.10`（beta，pre-1.0） |
| 运行时依赖 | 仅 `nitro`（唯一） |
| Node 版本 | `>= 24` |
| 包管理器 | `pnpm@11.7.0` + turbo monorepo |
| Vendored 三方库 | 27 个（通过 `#compiled/*` 别名） |
| 编译产物版本 | `COMPILED_AGENT_MANIFEST_VERSION = 30` |
| 框架默认 tools | 10 个（bash/glob/grep/read_file/write_file/ask_question/todo/web_fetch/web_search/skill） |
| 稳定 workflow id | 2 个（`workflowEntry` / `turnWorkflow`） |
| 内置 channel | slack/discord/teams/telegram/twilio/github/linear |
| 框架集成 | Next.js / Nuxt / SvelteKit |
| 前端 SDK | React / Vue / Svelte |
| e2e fixture | 10 个（fixture-owned eval） |
