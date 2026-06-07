# Nop AI Agent 设计文档深度审查

> Status: resolved
> Date: 2026-06-07
> Scope: `ai-dev/design/nop-ai-agent/` 全套 owner-doc，与 `agent.xdef`、`agent-plan.xdef`、`tool.xdef`、`tool-call.xdef`、`call-agent.tool.xml` 的契约一致性
> Conclusion: `nop-ai-agent` 已经基本完成 DSL-first 收敛，但当前设计集仍存在 5 类会直接影响实现落地的结构性问题：Phase 边界混杂、Hook 契约不稳定、Plan ownership/fork 冲突、Plan 表示与存储契约冲突、Skill source-of-truth 不清；此外还存在一类次级术语漂移问题。

## Context

- 本次分析目标不是修改设计，而是评估 `ai-dev/design/nop-ai-agent/` 是否已经形成可实施的稳定设计边界。
- 审查范围覆盖设计原则层、架构基线层、执行模型层、DSL 层、引擎层、策略层、路线图，以及对应真实 schema / tool 定义。
- 分析标准主要看三件事：文档间是否自洽、文档与真实 DSL 是否一致、Phase 1/2/3/4 的落地边界是否稳定。

## Overall Assessment

- 正向结论：这套文档已经明显优于早期的“runtime-first 大草稿”，主线已经收敛到 `xdef/.tool.xml -> runtime semantics -> engine/policy`，总体方向是正确的。
- 正向结论：`nop-ai-agent-dsl.md`、`nop-ai-tool-dsl.md`、`nop-ai-call-agent-dsl.md` 与真实 schema 的贴合度较高，尤其明确区分了“当前 DSL 已存在字段”和“未来扩展设想”。
- 核心问题：设计集已经具备“研究闭环”，但还没有完全达到“实现 hard-contract 闭环”。当前最大风险不是缺少更多想法，而是多个文档对同一核心对象给出不同层级、不同阶段、甚至不同生命周期的定义。

## Findings

### 1. P1: 当前基线把 Phase 1 本地文件实现和 Phase 2+/4 分布式恢复混写成同一套“当前设计”

**现象**

- `01-architecture-baseline.md` 把系统描述为“天然分布式的 actor 系统”，并且写明 Session 状态持久化到数据库、任何服务实例都可以接管恢复，锁由 actor 调度系统负责（`01-architecture-baseline.md:113-118`, `172-180`）。
- `nop-ai-agent-reliability.md` 也把“AgentSession 状态持久化到数据库，任何服务实例都可以接管恢复”写成恢复模型前提（`nop-ai-agent-reliability.md:17-23`）。
- 但 `nop-ai-agent-session-and-storage.md` 明确声明“本篇只讨论本地会话与文件存储，不讨论分布式会话存储”，并把“分布式 session store / 多节点锁”列为后置能力（`nop-ai-agent-session-and-storage.md:12-13`, `70-76`, `349-356`）。
- `nop-ai-agent-roadmap.md` 又把 Actor Runtime、RecoveryManager、ResourceGuard 放到 Phase 4（`nop-ai-agent-roadmap.md:140-165`）。

**影响**

- 实现者无法判断 Phase 1 的真正硬边界：到底只需要 file-backed local session，还是必须从一开始就满足 DB takeover 语义。
- 这会直接影响接口设计：`SessionManager`、`IMessageService`、锁语义、恢复语义到底是“当前约束”还是“未来兼容点”并不清楚。

**判断**

- 问题不在于设计文档中存在 roadmap / vision；`ai-dev/design/00-design-writing-guide.md` 明确允许 design 描述未实现目标架构，而 `ai-dev/design/nop-ai-agent/README.md` 也把“愿景层”单独隔离为“不驱动当前实现”的文档层级。
- 这不是“描述粒度不同”，而是“基线层”和“路线图层”混用了不同成熟度的断言。
- 当前最合理的解释是：文件后端 + 本地锁才是 Phase 1 hard contract；数据库接管和分布式恢复应降级为 Phase 2+/4 目标，而不应继续出现在“当前基线”表述中。

**建议**

- 把 Session/Recovery 语义拆成两层：
- `Current Baseline`: Phase 1 文件后端、单节点 Session 锁、按需恢复。
- `Forward-Compatible Contract`: 抽象接口必须允许未来替换为 DB / 分布式实现。
- 所有“任何服务实例都可以接管恢复”的说法都应显式加上 `Phase 2+` 或 `Phase 4` 标注。

### 2. P1: Hook 契约在执行模型层、引擎层、可靠性层之间并未稳定

**现象**

- `02-execution-model.md` 定义了 10 个生命周期点：`PRE_CALL`、`POST_CALL`、`PRE_REASONING`、`POST_REASONING`、`REASONING_CHUNK`、`PRE_ACTING`、`POST_ACTING`、`PRE_SUMMARY`、`POST_SUMMARY`、`ON_ERROR`（`02-execution-model.md:79-95`）。
- 但 `nop-ai-agent-react-engine.md` 和 `nop-ai-agent-runtime-semantics.md` 又只固定 5 个 lower-case 事件：`before_reasoning`、`after_reasoning`、`before_acting`、`after_acting`、`on_error`（`nop-ai-agent-react-engine.md:107-117`, `nop-ai-agent-runtime-semantics.md:109-119`）。
- `nop-ai-agent-hook-skill-engine.md` 还混用了 `POST_REASONING`、`ON_ERROR`、`POST_CALL` 这类 upper-case 事件名，进一步说明 Hook contract 尚未收口（`nop-ai-agent-hook-skill-engine.md:67-72`）。
- `nop-ai-agent-reliability.md` 在压缩设计中又引入 `before_compaction` / `after_compaction`，同时承认“当前 Hook 引擎仅定义 5 个 Hook 生命周期点”（`nop-ai-agent-reliability.md:233-238`）。
- `agent.xdef` 的真实 DSL 只暴露 `<on event="!event-pattern-string">`，并没有文档层已经统一的 canonical event list（`agent.xdef:34-38`）。

**影响**

- Hook 是执行引擎最核心的扩展点之一，但现在“当前可实现集合”和“未来可扩展集合”混在一起。
- 任何人开始实现 `HookRegistry` / `HookMatcher` / `HookInvoker` 时，都会先遇到“事件名到底以哪份文档为准”的问题。
- 这会连锁影响 Skill 注入、Summary、Compaction、流式事件以及测试矩阵设计。

**判断**

- 当前真正稳定的 Hook hard contract 只有 5 个事件；10 个事件和 compaction hooks 更像 proposal。
- `02-execution-model.md` 现在写法更像“目标态设计”，但标题和结构又把它表述成当前执行模型结论。

**建议**

- 建一个唯一 canonical hook contract：
- `Current Hook Contract`: 只列当前 5 个稳定事件。
- `Proposed Future Events`: 列 `PRE_CALL` / `POST_CALL` / `*_CHUNK` / `*_SUMMARY` / `*_COMPACTION`，并标明 phase。
- 事件命名也要统一：要么统一 upper-case enum 名 + DSL alias，要么统一 lower-case runtime 名，不要两套名字并存但没有映射表。

### 3. P1: Plan 的所有权、持久化位置和 fork 语义存在直接冲突

**现象**

- `01-architecture-baseline.md` 把 Plan/Todo 定义为“属于单个 Agent，不传递给子 Agent”（`01-architecture-baseline.md:213-219`）。
- `nop-ai-agent-context-model.md` 又把“计划状态”定义成项目级 `ai-dev/plans/` 中的持久化实体，而且标注为“跨 session”（`nop-ai-agent-context-model.md:26-33`）。
- `nop-ai-agent-session-and-storage.md` 进一步明确“Plan 是项目级实体，不属于任何 session；一个 plan 可能在 session A 创建，在 session B 继续执行”（`nop-ai-agent-session-and-storage.md:62-68`, `131-166`）。
- 但同一篇 `nop-ai-agent-context-model.md` 在 fork 语义中又说“新 session 的 Plan 状态是当前 Plan 的深拷贝”，而 `inheritContext=true + agentId="self"` 等价于 fork（`nop-ai-agent-context-model.md:97-111`）。

**影响**

- 当前设计无法回答几个实现层必须回答的问题：
- 子 Agent 调用时，到底是共享同一个 `planId`、复制一份新的 plan、还是根本不带 plan。
- “Plan 是单 Agent 私有状态”与“Plan 是项目级跨 Session 实体”不能同时作为 hard contract 成立。
- 如果这个边界不收敛，恢复、并发、审计、Plan 校验、子 Agent 委派都会持续漂移。

**判断**

- 这里实际混用了至少 3 个不同对象：
- 项目级 Markdown 计划文档（AGE artifact）。
- Agent 运行时任务状态（runtime plan state）。
- 子任务委派输入（delegation spec）。
- 现在文档把这 3 个对象都叫 `Plan`，导致语义串线。

**建议**

- 强制拆分概念并命名：
- `ProjectPlanDoc`: `ai-dev/plans/*.md`，跨 session、面向审计。
- `ExecutionPlanState`: 当前 Agent 的 runtime task/progress 状态，跟随 session 或跟随 agent-instance。
- `DelegationSpec`: 父 Agent 传给子 Agent 的最小任务说明，不等于完整 Plan 继承。
- 在此之前，不建议再继续扩展 `call-agent` / fork / plan recovery 语义。

### 4. P1: Plan 的表示形式与存储 source-of-truth 仍未收口

**现象**

- `agent-plan.xdef` 和 `nop-ai-agent-plan-dsl.md` 把 Plan 定义为结构化 DSL / runtime protocol，强调 hard-contract 与 soft narrative 的边界，以及 Markdown/XML 的部分映射关系（`agent-plan.xdef:12-136`, `nop-ai-agent-plan-dsl.md:11-24`, `94-95`, `336-347`, `405-418`）。
- 但 `nop-ai-agent-session-and-storage.md` 又把 Plan 的存储位置直接固定为项目级 Markdown 文件 `ai-dev/plans/{planId}-{title}.md`，并称“运行时引擎通过 VFS 接口管理 plan 的读写”（`nop-ai-agent-session-and-storage.md:140-152`）。
- 同一篇 `nop-ai-agent-session-and-storage.md` 还在更前面写到“Plan 对外发布按 AGE 规范写入 `ai-dev/plans/`，与 VFS 内部存储分离”，说明它自己就在“外部发布物”和“运行时存储”之间摇摆（`nop-ai-agent-session-and-storage.md:23`）。

**影响**

- 实现者无法判断 `agent-plan.xdef` 到底是：
- runtime 内部真实协议。
- Markdown plan 的结构化镜像。
- 未来可能存在但当前未落地的另一种表示。
- 如果 source-of-truth 不明确，Plan 校验、恢复、fork、跨 session 续跑都会落在不稳定基础上。

**判断**

- 这里的冲突比 ownership 更底层：它涉及 Plan 究竟以 XML model 为准、以 Markdown AGE artifact 为准、还是以二者之间的双层投影模型为准。
- 在没有显式双层模型说明前，把 `ai-dev/plans/*.md` 直接写成 runtime plan 的唯一存储位置，会让 DSL 文档和存储文档相互拉扯。

**建议**

- 先明确 Plan 的表示模型三选一：
- `XML-as-source`: `agent-plan.xdef` 实例为主，Markdown 为导出/审计视图。
- `Markdown-as-source`: AGE Markdown 为主，XML 为运行时抽取出的 hard-contract 子集。
- `Dual-layer`: ProjectPlanDoc 与 ExecutionPlanState 是两个对象，有明确同步/投影关系。
- 在没有做出这一裁定前，不建议把 Plan recovery、cross-session continuation、fork semantics 写成稳定契约。

### 5. P2: Skill 的 source-of-truth 与生成链路定义自相矛盾

**现象**

- `skill-system-design.md` 先把 `.opencode/skills/` 下的 `SKILL.md` 作为现有源目录（`skill-system-design.md:15-18`）。
- 随后又定义“工具读取 `SKILL.md`，输出 `.skill.yaml` 到 `nop-ai-agent/skills/`”（`skill-system-design.md:126-130`）。
- 但同一段又写“人工运行时注册仍然编辑 `.skill.yaml` 作为 source of truth”（`skill-system-design.md:128-130`）。

**影响**

- 这会直接破坏 Skill DSL 的维护边界：到底 `SKILL.md` 是源，`.skill.yaml` 是派生产物；还是 `.skill.yaml` 是人工维护主文件，`SKILL.md` 只是素材。
- 一旦进入版本化、Delta 覆盖、注册中心加载阶段，这个歧义会放大为工具链歧义。

**判断**

- 当前设计在“研究素材”与“运行时契约文件”之间还没有选定唯一 owner。
- 如果不收口，Skill 系统很容易再次滑回“双写双真相”。

**建议**

- 只保留一种 source-of-truth：
- 方案 A：`SKILL.md` 为源，`.skill.yaml` 纯生成且禁止手改。
- 方案 B：`.skill.yaml` 为源，`SKILL.md` 仅作说明文档，不再作为 build 输入。
- 在 Nop 语境下，如果 Skill 最终要进入 Delta / Registry / runtime 校验链，更适合把结构化 `.skill.yaml` 作为唯一契约文件。

### 6. P3: 核心对象命名还没有形成稳定术语表，容易导致实现层“各自发明类型”

**现象**

- `00-vision.md` 使用 `AgentModel + Agent + AgentSession`，并把 `AgentState/AgentSession` 混合描述（`00-vision.md:27-31`, `68-77`）。
- `01-architecture-baseline.md` 也在“AgentState/AgentSession”之间切换（`01-architecture-baseline.md:80-88`）。
- `nop-ai-agent-react-engine.md` 示例字段又使用 `AgentRuntimeModel agentModel`（`nop-ai-agent-react-engine.md:37-49`）。

**影响**

- 文档读者能理解大意，但实现者会自然开始各自命名：`AgentRuntimeModel`、`AgentState`、`AgentSessionState`、`ActorRef` 等容易并存。
- 一旦代码层先于术语表成型，后续文档再统一会付出更高成本。

**判断**

- 这不是细节问题。对一个 DSL-first 系统，术语表本身就是设计 hard-contract 的组成部分。
- 但它的严重性低于前 5 项；前 5 项直接影响生命周期和 source-of-truth，术语问题更像“如果不尽快治理，会在实现期放大”的次级风险。

**建议**

- 在 `README.md` 或单独 glossary 中固定一份 canonical vocabulary：
- 配置对象名。
- 无状态执行体名。
- 持久化状态对象名。
- 单次执行上下文名。
- 配置名 / 实例 ID / sessionId / planId 的职责边界。

## Rejected Alternatives

- 否决结论 1：不是“文档太多导致读起来复杂”，而是少数核心对象在多篇文档中承担了不同语义，属于结构性问题，不是纯摘要问题。
- 否决结论 2：不是“只要开始实现，细节自然会收敛”。当前冲突集中在 session、hook、plan ownership，这些正是代码骨架层决策，不能靠实现时临场拍板。
- 否决结论 3：不是“所有问题都要先补新 schema”。相反，当前最需要的是把已有 DSL 与 runtime contract 的边界重新标注清楚，减少文档对未来能力的提前承诺。

## Conclusion

- 最终结论：`nop-ai-agent` 的设计方向已经基本正确，特别是 DSL-first、配置/执行/状态分离、`call-agent` 服从工具体系这几条主线都值得保留。
- 但在真正进入实现前，至少应先收敛 5 个 hard-contract：Phase 1 存储/恢复边界、Hook canonical contract、Plan ownership/fork model、Plan representation/source-of-truth、Skill source-of-truth。
- 被否决的路线：继续在当前状态下横向扩展 Actor Runtime、TeamSpec、Advisor Agent 细节。原因：底层 contract 仍不稳定，继续扩展只会放大命名和生命周期漂移。
- 后续工作：若采纳本报告，优先回写 `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`、`02-execution-model.md`、`nop-ai-agent-context-model.md`、`nop-ai-agent-session-and-storage.md`、`skill-system-design.md`，并在 `README.md` 增补 canonical glossary / current-vs-future mapping。

## Open Questions

- [ ] `ai-dev/plans/*.md` 在 `nop-ai-agent` 中到底是“项目级 owner artifact”，还是“runtime plan 的外部投影”，还是两者同时存在的双层模型？
- [ ] `call-agent` 派生子 Agent 时，默认语义应更接近“共享 ProjectPlanDoc + 独立 ExecutionPlanState”，还是“完全独立的新 Plan”?
- [ ] Hook 事件是否需要保留 `event-pattern-string` 的开放性，还是先收敛到枚举集再允许模式扩展？

## References

- `ai-dev/design/nop-ai-agent/README.md`
- `ai-dev/design/nop-ai-agent/00-vision.md`
- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`
- `ai-dev/design/nop-ai-agent/02-execution-model.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/design/nop-ai-agent/nop-ai-tool-dsl.md`
- `ai-dev/design/nop-ai-agent/nop-ai-call-agent-dsl.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-runtime-semantics.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`
- `ai-dev/design/nop-ai-agent/skill-system-design.md`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/tool.xdef`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/tool-call.xdef`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/call-tools.xdef`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/tool/call-tools-response.xdef`
- `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/call-agent.tool.xml`
