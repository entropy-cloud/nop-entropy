# Nop AI Agent 设计文档

核心定位：**面向大规模无人值守自动化执行**。

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，采用**四层接口架构**——扩展通过添加接口实现，不通过阶段切换。

分层架构（详见 `nop-ai-agent-roadmap.md`）：

```
Layer 4: Platform Extensions (平台扩展层)
Layer 3: Reliability Extensions (可靠性扩展层)
Layer 2: Execution Extensions (执行扩展层)
Layer 1: Core Interfaces (核心接口层)
```

文档按设计维度组织：

## 设计原则层

- `00-vision.md`
  - 高层设计原则：产品定位、成功标准、不可违反的约束、显式 non-goals、设计收敛路径、必须由人决策的决策点、核心隐喻（三层分离 + Actor 模型）

## 架构基线层

- `01-architecture-baseline.md`
  - 架构分层、核心对象职责契约、模块边界、关键设计决策
  - 部署模型（单进程/多实例）、通信模型（IMessageService）、存储模型（VFS + 持久化接口）
  - Session 模型、Agent 身份模型、多租户与资源隔离、Plan 与 Todo 系统
  - 与传统 Actor 框架的对应关系
- `nop-ai-agent-context-model.md`
  - Agent 上下文模型：组成维度、Tool 可见性、Agent-as-Subprocess 隐喻、上下文继承与 fork、内部 Agent 化
- `nop-ai-agent-multi-agent.md`
  - 多 Agent 并行协同：冲突分类、文件写意图、资源声明、通信模型

## 执行模型层

- `02-execution-model.md`
  - 双循环模型（followUp + ReAct）、Steering 机制、Hook 生命周期、执行控制（循环控制 + 资源控制）、错误处理分类
- `nop-ai-agent-llm-layer.md`
  - LLM 层接口设计：Layer 1 IMessageFormat (CanonicalMessage) + Layer 2 IModelDialect (Formatter)/ITalent (动态准入)/IModelRouter (Smart Router) + Layer 3 IRetryPolicy (Provider 重试)、Cache-First 三区域架构
- `04-tool-invocation.md`
  - 工具发现、执行流程、并行执行、JSON Schema 兼容

## DSL 层

- `nop-ai-agent-dsl.md`
  - 对应 `agent.xdef`
- `nop-ai-agent-plan-dsl.md`
  - 对应 `agent-plan.xdef`
- `nop-ai-tool-dsl.md`
  - 对应 `tool.xdef`、`tool-call.xdef`、`call-tools.xdef`、`call-tools-response.xdef`
- `nop-ai-call-agent-dsl.md`
  - 对应真实 `call-agent.tool.xml`

## Java 引擎层

- `nop-ai-agent-react-engine.md`
  - 单 Agent ReAct 执行引擎详细设计
- `nop-ai-agent-hook-skill-engine.md`
  - Hook / Skill 的引擎层组织方式
- `nop-ai-agent-session-engine.md`
  - Session 的引擎层设计

## 语义映射层

- `nop-ai-agent-runtime-semantics.md`
  - 解释 DSL 如何映射到运行时行为

## 策略层

- `nop-ai-agent-session-and-storage.md`
  - Session、快照、分叉、Session Tree、压缩回写、存储边界
- `nop-ai-agent-security-and-permissions.md`
  - 权限、安全边界、目录保护
- `nop-ai-agent-reliability.md`
  - 错误分类、压缩、超时、回退、恢复、崩溃恢复模型
- `nop-ai-shell-design.md`
  - nop-ai-shell 模块设计：与 nop-ai-toolkit 接口对齐、命令分层、混合执行模式
- `nop-ai-shell-syntax-spec.md`
  - nop-ai-shell 语法规范：支持的 bash 子集（Tier 1/2/3）、变量展开、命令行为约定
- `skill-system-design.md`
  - Skill 系统三层表示（SSL 参照）、匹配机制

## 愿景层

- `nop-ai-agent-actor-runtime-vision.md`
  - Platform Layer 具体架构：ActorRuntime、MessageRouter、TeamManager、RecoveryManager、ResourceGuard
- `nop-ai-agent-roadmap.md`
  - **分层架构与实施路线**：四层接口组织（Core → Execution → Reliability → Platform），扩展通过添加接口实现

---

## 阅读顺序

**必读路径**（理解设计原则 → 架构 → 执行模型）：

1. `00-vision.md` — 设计原则和约束
2. `01-architecture-baseline.md` — 架构基线和核心决策
3. `02-execution-model.md` — 执行模型

**按需深入**：

4. `nop-ai-agent-llm-layer.md` — LLM 层接口设计
5. `nop-ai-agent-context-model.md` — 上下文模型
6. `04-tool-invocation.md` — 工具调用架构
7. `nop-ai-agent-dsl.md` → `nop-ai-agent-plan-dsl.md` → `nop-ai-tool-dsl.md` → `nop-ai-call-agent-dsl.md` — DSL 详细设计
8. `nop-ai-agent-react-engine.md` → `nop-ai-agent-hook-skill-engine.md` → `nop-ai-agent-session-engine.md` — 引擎详细设计
9. `nop-ai-agent-runtime-semantics.md` — DSL 到运行时的语义映射
10. `nop-ai-agent-multi-agent.md` — 多 Agent 协同
11. 策略层：`nop-ai-agent-session-and-storage.md`、`nop-ai-agent-security-and-permissions.md`、`nop-ai-agent-reliability.md`、`skill-system-design.md`

**扩展方向**：

12. `nop-ai-agent-actor-runtime-vision.md` — Platform Layer 组件设计
13. `nop-ai-agent-roadmap.md` — 分层架构与实施路线
