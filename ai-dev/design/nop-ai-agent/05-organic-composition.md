# Nop AI Agent 有机组合

**日期**：2026-06-21
**范围**：`nop-ai-agent` 模块 25 子包的依赖结构与装配汇合
**状态**：active

---

## 一、设计结论

1. 25 子包构成以 `engine` 为根的有向无环图（DAG），**无循环依赖**
2. **L2 七子包（`hook` / `skill` / `memory` / `repair` / `plan` / `runtime` / `compact`）两两零 import**——扩展点彼此正交，是本设计最强的结构性证据
3. 装配汇合是**"两层持有 + 一层转发"**（DAE → RAE → ATEC），不是真正的"单点"汇合
4. **35 个扩展点在 DAE 与 RAE 中被重复持有**，是装配分层的必然结果，不是冗余——但带来"新增扩展点需三处一致维护"的成本
5. `fencing` 是唯一**完全孤岛包**（incoming=0 / outgoing=0），与 `03-extension-matrix.md` §5.3 的未闭合状态对应

## 二、定位与边界

### 2.1 本篇回答什么

- **`01-architecture-baseline.md` 不回答**：25 个子包互相 import 的关系？哪些包正交？装配链如何穿透？
- **`03-extension-matrix.md` 不回答**：消费者之间如何互联？哪些扩展点在同一装配层？哪些是"传播字段"？
- **本篇回答**：**子包如何组合成有机整体 + 装配汇合的真实结构 + 三大有机性原则**

### 2.2 本篇不回答什么

- **不重复 `03` 的闭合度状态**（per-接口是否闭合）
- **不重复 `01` 的核心对象职责契约**（per-对象做什么）
- **不重复 `glossary` 的 Layer 归属**（per-术语属于哪层）
- **不展开字段类型与签名**（看源码）

### 2.3 阅读对象

- **架构审计者**：用本篇验证"是否有机整体"的三个原则
- **新增扩展点设计者**：用本篇 §8 检查清单确保新扩展点不破坏有机性
- **重构者**：用本篇识别"装配链断层"（如 `ICompletionJudge` 的接线缺口）

## 三、25 子包跨包依赖结构

### 3.1 依赖图（关键边）

完整 25×25 矩阵的数据量大但信号低（68 条非零边）。本节只列**结构性关键边**：

```
                   ┌─────────────────────────────┐
                   │  engine (42 个扩展点字段)   │  ← 装配根
                   │  被 18 个包依赖              │
                   └──────────────┬──────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
        ▼                         ▼                         ▼
   security (79)             team (32)                tool (36→team)
   被 engine 依赖             依赖 engine              依赖 team
        │                         │
        │                         │
   被 runtime(19)            依赖 quota(8)
   被 team(10)              依赖 security(10)
   被 session(3)            依赖 runtime(2)
   被 message(3)
   被 reliability(3)
   被 usage(2)
```

> 边权重 = `import io.nop.ai.agent.<dst>.` 语句的计数（按源包 grep）。`engine → security (79)` 是最高权重边——`DefaultAgentEngine` 与 `ReActAgentExecutor` 装配 security 链路（13 个接口）的开销。

### 3.2 包的角色分类

| 角色 | 定义 | 包 |
|---|---|---|
| **装配根** | 被最多包依赖、自身依赖最少 | `engine` |
| **横切依赖** | 单接口被 ≥10 个类依赖 | `security`（`ITenantResolver` 被 13 个 DB-backed 类持有，见 §6.2） |
| **叶子包** | outgoing = 0（不被别人依赖就独立） | `conflict`、`quota` |
| **顶层包** | incoming = 0（不依赖别人） | `tool` |
| **完全孤岛** | incoming = 0 且 outgoing = 0 | **`fencing`** |

### 3.3 L2 七子包正交性证据

L2 子包定义：`hook`、`skill`、`memory`、`repair`、`plan`、`runtime`、`compact`

**验证命令**（在 `nop-ai-agent/src/main/java/io/nop/ai/agent/` 下执行）：

```bash
for src in hook skill memory repair plan runtime compact; do
  for dst in hook skill memory repair plan runtime compact; do
    [ "$src" = "$dst" ] && continue
    count=$(grep -rhE "^import io\.nop\.ai\.agent\.${dst}\." "$src" 2>/dev/null | wc -l)
    [ "$count" -gt 0 ] && echo "VIOLATION: $src -> $dst ($count)"
  done
done
# 输出：空
```

**结果**：7×6=42 对组合中**零违反**。L2 七子包互相之间没有任何 `import` 语句。

**含义**：这 7 个扩展机制完全正交。它们都只通过 `engine.AgentExecutionContext` / `engine.AgentToolExecuteContext` 间接汇合。增加新的 L2 扩展点时，无需考虑与其他 6 个 L2 子包的耦合。

## 四、装配汇合的三层架构

### 4.1 装配链总图

```
       DefaultAgentEngine (DAE)
       ─────────────────────
       42 个扩展点字段
       生命周期：单进程长生命周期
       装配职责：装配期 setter，存放稳定依赖
                    │
                    │  resolveExecutor()
                    │  一次性装配，转发 35 字段
                    ▼
       ReActAgentExecutor (RAE)
       ─────────────────────
       37 个扩展点字段
       生命周期：per-execute 短生命周期
       装配职责：Builder-only，主循环消费
                    │
                    │  每轮 dispatch
                    │  重建 ATEC，转发 5 字段
                    ▼
       AgentToolExecuteContext (ATEC)
       ─────────────────────
       5 个扩展点字段
       生命周期：per-dispatch 极短
       装配职责：final 字段，工具层能力背包
                    │
                    │
                    ▼
       AgentExecutionContext (AEC)
       ─────────────────────
       0 个扩展点字段（纯 POJO）
       状态载体，非汇合点
```

**关键事实**：`AgentExecutionContext` 是**纯 POJO**（329 行全部是 concrete 类型字段），不是扩展点汇合点。真正的扩展点汇合点是 **DAE 和 RAE**，两者形成"两层持有 + 一层转发"的装配链。

### 4.2 字段的 Layer 分布

| 装配类 | L1 | L2 | L3 | L4 | 合计 |
|---|---|---|---|---|---|
| **DAE** | 7 | 13 | 10 | 12 | **42** |
| **RAE** | 7 | 14 | 10 | 6 | **37** |
| **ATEC** | 1 | 0 | 0 | 4 | **5** |
| **AEC** | 0 | 0 | 0 | 0 | **0** |

**Layer 装配规律**：
- **L1 Core** 全部在 DAE+RAE（核心契约 + 必装依赖）
- **L2 Execution** 主要在 DAE+RAE（每轮消费或装配期解析）
- **L3 Reliability** 全部在 DAE+RAE（主循环触发）
- **L4 Platform** 分化：
  - **"engine 生命周期入口独占"**（DAE-only 4 个）：`IActorRuntime`、`ISessionTakeoverLock`、`IRecoveryManager`、`ITeamTaskSchedulerDaemon`——只在 doExecute/resumeSession/restoreSession 三入口被调用
  - **"工具层能力"**（穿透到 ATEC 4 个）：`IAgentMessenger`、`ITeamManager`、`ITeamTaskStore`、`ITeamAclChecker`——工具执行时通过 ATEC 拿到

### 4.3 DAE ∩ RAE 共同持有 = 35 个

| Layer | 重复持有字段数 | 含义 |
|---|---|---|
| L1 | 7 | 全部 L1 字段都重复持有 |
| L2 | 12 | L2 主体都重复持有（仅 `IHookRegistry` 是 RAE-only） |
| L3 | 10 | 全部 L3 字段都重复持有 |
| L4 | 6 | 仅"工具能力"重复持有，其余 L4 是 DAE-only 或 RAE-only |

**这不是冗余**——是 DAE（单例长生命周期）与 RAE（per-execute 短生命周期）解耦的标准装配做法。`DefaultAgentEngine.resolveExecutor` 是唯一的 RAE 装配点，Builder 链逐一 `.xxxField(this.xxxField)` 把 DAE 字段全部转发给 RAE。

**但带来维护成本**：每新增一个扩展点，需要在 **DAE 字段 + DAE setter + resolveExecutor 转发** 三处同时改。`ICompletionJudge` 就是少了一处（DAE 端缺 setter + resolveExecutor 缺一行）的活例子（见 §6.3）。

## 五、扩展点正交原则

### 5.1 原则陈述

**扩展点之间不互相依赖**。两个扩展点接口如果属于不同的关注点（如 hook 和 memory），它们的实现类之间不应该有 `import` 关系。

### 5.2 落实证据

L2 七子包两两零 import（§3.3）是这条原则的最强证据。任取两个 L2 扩展机制（如 `IAgentLifecycleHook` 与 `IAiMemoryStore`），它们的实现类之间没有任何 import——它们都只通过 `AgentExecutionContext` 间接汇合。

### 5.3 违反时的修复方向

如果发现两个扩展点包之间出现直接 import，按以下顺序处理：

1. **检查是否应该合并**：如果两个扩展点确实强耦合，可能应该合并为一个扩展点
2. **检查是否应该提升到 L1**：如果是公共依赖（如 `ITenantResolver`），应该作为横切依赖放在 L1
3. **检查是否应该经 ATEC 间接汇合**：如果一个扩展点需要在工具层使用另一个扩展点，应该经 `AgentToolExecuteContext` 间接传递

## 六、汇合点单点原则

### 6.1 原则陈述

**所有扩展点在装配链上汇合，且每个扩展点只在一个装配类上"首次落地"**。"首次落地"指扩展点的默认值和 setter 集中在一处，不分散在多个装配类。

### 6.2 真实图景（不是真正的"单点"）

如 §4 所示，本模块的扩展点汇合是"两层持有 + 一层转发"，不是真正的单点。每个扩展点的"首次落地"位置：

| 首次落地位置 | 扩展点 |
|---|---|
| **DAE** | 36 个（除 RAE-only `IHookRegistry`、`ICompletionJudge` 外的全部 DAE 字段） |
| **RAE** | 1 个（`IHookRegistry`——per-execute 创建，不存 DAE） |
| **ATEC** | 1 个（`IAiMemoryStore`——per-session 解析，不存 DAE/RAE） |
| **DAE 内部组件** | 1 个（`IContentTrustEvaluator`——只在 `DefaultLevelHintsProducer` 内） |
| **子模块入口** | 多个（`IRecoveryManager` 等恢复 handler 在 SRM；`IDaemonCoordinator` 在 TTSD） |

### 6.3 接线缺口（违反单点原则的反例）

| 接口 | 缺口 | 后果 |
|---|---|---|
| `ICompletionJudge` | DAE 无 setter，resolveExecutor 不转发 | **RAE 永远走 NoOp**——功能实现（`RuleBasedCompletionJudge` / `LlmCompletionJudge`）只能由调用方绕过 DAE 自行构造 RAE 时注入 |

**修复方向**：DAE 增加 `setCompletionJudge` setter，并在 `resolveExecutor` Builder 链补 `.completionJudge(this.completionJudge)`。

### 6.4 横切依赖例外

`ITenantResolver` 是横切依赖——被 **14 个类**直接持有（13 个 DB-backed 类 + `InMemoryActorRegistry`），不通过 DAE/RAE/ATEC 装配。这是合理的：tenant 解析是 DB 层的基础设施，每个 DB 类自己持有 resolver 才能在 SQL 注入 tenant 条件。

**但带来命名一致性问题**：`NullTenantResolver` 不叫 `NoOpTenantResolver`，偏离 NoOp 三件套范式（见 `03-extension-matrix.md` §6.2）。

## 七、闭合优先原则

### 7.1 原则陈述

**每个扩展点要么有消费者，要么在 roadmap 显式标记为 successor**。不允许"接口已就位、消费者永不落地"的纯投机切片。

### 7.2 与 03 §5 的关系

闭合优先原则是 `03-extension-matrix.md` §5「闭合状态分析」的方法论依据。本原则给出"应该怎样"，03 §5 给出"当前怎样"。

### 7.3 当前状态对照

| 闭合状态 | 计数 | 是否符合原则 | 说明 |
|---|---|---|---|
| ✅ 闭合 | 59 | ✅ 符合 | 消费者 + 功能实现齐全 |
| 🟡 半闭合 | 3 | ✅ 符合 | successor 已在 design 文档标注（ITalent / IContentGuardrail / IBudgetProvider） |
| 🔴 未闭合 | 1 | ⚠️ **边界情况** | `IFencingTokenService` 已在 03 §5.3 标注为"设计性主动预留"，但应在 roadmap 显式登记为 successor |
| ⚪ 回调契约 | 3 | ✅ 符合 | 不参与闭合分析 |

## 八、新增扩展点的检查清单

新增一个扩展点接口时，**必须**完成以下检查（按 ROI 排序）：

### 8.1 必做（设计层）

- [ ] **Layer 归属**：在 `glossary.md` 登记 Layer 1/2/3/4
- [ ] **NoOp 三件套**：实现接口 + NoOp shipped 默认 + （可选）功能默认实现
- [ ] **包归属**：放在正确的子包（按 §3.2 的角色分类）
- [ ] **闭合优先**：要么已有消费者，要么在 design 文档显式标 successor

### 8.2 必做（装配层）

- [ ] **DAE 字段**：在 `DefaultAgentEngine` 增加 `private IXxx xxxField;`
- [ ] **DAE setter**：增加 `public void setXxx(IXxx xxx)` + null-safe fallback
- [ ] **resolveExecutor 转发**：在 `resolveExecutor` 的 Builder 链补 `.xxx(this.xxx)`
- [ ] **RAE 字段**：在 `ReActAgentExecutor` 增加 `private final IXxx xxx;` + Builder 方法
- [ ] **RAE fallback**：构造器内对 null 字段提供 NoOp fallback

> 三处一致维护（DAE 字段 + DAE setter + resolveExecutor 转发）是 §4.3 强调的关键。`ICompletionJudge` 接线缺口就是少了一处的反例。

### 8.3 视情况（消费层）

- [ ] **RAE 主循环消费**：如果扩展点需要在 ReAct 循环中触发，在 RAE `execute()` 中调用
- [ ] **ATEC 穿透**：如果扩展点需要被工具执行器使用，在 ATEC 增加字段并在 RAE `dispatch` 时透传
- [ ] **事件触发**：如果是 hook 类扩展点，在 RAE 对应生命周期点 `invokeHooks` 调用

### 8.4 必做（文档层）

- [ ] **更新 `03-extension-matrix.md`**：新增一行，标注闭合状态
- [ ] **更新 `glossary.md`**：新增术语 + Layer 归属
- [ ] **更新本篇 §4.2**：DAE/RAE/ATEC 字段 Layer 分布表加 1

## 九、拒绝了什么

| 被拒绝的方案 | 拒绝理由 |
|---|---|
| **强制单点装配**（所有扩展点只在 DAE 字段） | DAE 字段已达 42 个，继续膨胀会让 DAE 不可维护。两层装配（DAE 持有 + RAE 转发）是合理的解耦 |
| **L2 子包之间允许 utility 共享**（如 `memory` 和 `compact` 共享一个 `MemorySearchSupport`） | 一旦允许，正交性破坏，每对扩展机制的耦合需要单独审查。当前 7 个 L2 子包零依赖是设计上的硬约束 |
| **把 `fencing` 包暂时删除**（零消费者） | fencing 是 plan 235 的设计性主动预留原语，scope_claim / Compaction / ResourceGuard fencing 集成都是已规划 successor。删除会让后续消费者无法落地 |
| **把 `ICompletionJudge` 接线缺口标为"已知问题"忽略** | 这是闭合优先原则的违反点。DAE 增加 setter + resolveExecutor 转发是低成本修复，应该在下一个 plan 闭合 |
| **把横切依赖（如 `ITenantResolver`）强制走 DAE 装配** | 13 个 DB-backed 类自己持有 resolver 是合理的（SQL 注入需要直接访问），强制走 DAE 会增加无意义的转发层 |

## 十、与其他文档的关系

- `00-vision.md` §4 渐进式增强约束 — 本篇是"组合性"维度的展开
- `00-vision.md` §7 Actor 隐喻 — 本篇 §4 用装配链图补充其高层隐喻
- `01-architecture-baseline.md` §4 核心对象职责契约 — 本篇 §4 验证 DAE/RAE/ATEC/AEC 的字段分布
- `02-execution-model.md` §5 Hook 生命周期 — 本篇 §3.2 验证扩展点在 ReAct 主循环的真实触发位置
- `03-extension-matrix.md` — 本篇与其互补（03 = per-接口闭合度，本篇 = per-包组合性）
- `glossary.md` — 本篇 Layer 归属与其严格对齐
- 源码 `io.nop.ai.agent.*` — 本篇全部数据的唯一来源；与源码冲突时以源码为准
