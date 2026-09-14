# S3 三方扩展点同点多触发顺序（WI6 交付物）

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai-agent / deepseek-harness（dsh）/ pi 三方扩展点上多个实现共存时的排序来源、分发语义、短路规则、可预测性与可配置性；S3 主题权威深挖
> Conclusion: 三方的顺序机制收敛于"无优先级字段、顺序=注册/声明序"，但三个关键差异：①dsh waterfall 洋葱方向为**先注册=最外层=最先执行**（`next()` 从注册序数组头部 shift），唯一位置控制是 `prepend: true`，且组成行序无装载语义（fiber 服务可用性驱动激活，插件必须容序设计）；②nop 是唯一有 priority 排序的点（IContributionRegistry priority 升序稳定排序），且 session 层与 execution 层**永不合并**（不同 enum 两个独立 region），hooks 短路为命中即 return（Reenter/Bail/Veto 均中止同点剩余 hook）；③pi 全链无优先级，顺序=扩展加载序（四级路径解析+稳定 rank 排序：CLI>项目 settings>项目 auto>用户 settings>用户 auto>packages）×扩展内注册调用序，链式组合方向为第一个 handler 处理原始值、前者输出=后者输入。
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、**dsh=c291e7961a（2026-09-10，重钉；141eb6fef8 为其祖先）**、pi=c49906ec7；全部锚点行号当日实测
> 锚点重钉: 2026-09-14，HEAD 4582e780dad4（plan 355 重构+M5/M6 修复后逐锚点核对；仅行号更新，结论不变）
> 引用: 00-dimension-matrix.md（S3 章节契约）、04-extension-capability-matrix.md（单点能力，本档不重复）；本文档是同点多触发顺序主题权威源

## ① 结论摘要

- 共同点：三方都**没有 per-handler 优先级字段**（nop 仅 IContributionRegistry 有 priority；dsh/pi 全无）；默认顺序全部=注册/声明序。
- 关键差异 1（洋葱方向）：dsh waterfall 先注册=最外层=最先执行、`inner`（内建默认）在所有 listener 之后且仅当无人 veto 执行；nop MiddlewareChain index 0=最外层（filter-chain 先注册故居最外层，`<middlewares>` 声明序依次内层）；pi 无洋葱（顺序 await 或链式替换）。
- 关键差异 2（短路规则三分）：nop hook Reenter/Bail/Veto 命中即 return（同点剩余 hook 不执行）；dsh waterfall 不调 `next()`=否决整链含 inner、serial bail 值短路、emit 无短路（contained 封装下每 listener 独立容错）；pi 按事件类型固定短路语义（cancel:true / block:true / handled / user_bash 首个 truthy / project_trust 首个非 undecided），链式类无短路全量执行。
- 关键差异 3（可配置性）：dsh 运行期唯一手段 prepend:true（spill-policy 等在用）；nop 装配期 DSL 声明序可配、运行期不可（contribution priority 除外）；pi 靠加载序（settings 声明/CLI 顺序可配）。
- nop 两个特有"顺序免议"设计：7-checkpoint 固定序硬编码不可配置；guardrail/repairer/skill provider 单实例不可多（天然无顺序问题）。
- dsh 组成行序无装载语义（cordis.patch.yml:12-13 明文），出厂插件以容序设计兜底（llm-retry 不含 CONTEXT_WINDOW_EXCEEDED 重试码+always 模式先委托下游；compaction-basic 只拦溢出码）——"顺序可预测性"在 dsh 是被设计规避的问题。

## ② 排序来源（六类分类法，按 WI5 census 逐面判定）

排序来源分类：**注册序**（运行时 add/push 顺序）、**声明序**（DSL/XML/settings 声明顺序）、**订阅序**（事件 addSubscriber 顺序）、**组合序**（装配代码包装/链式顺序）、**固定序**（硬编码不可配）、**单槽**（无顺序可言，最后赋值胜）。

### nop（排序来源逐面）

| 面 | 排序来源 | 细节 | 锚点 |
|---|---|---|---|
| lifecycle hooks 同点多注册 | 注册序 | EnumMap<AgentLifecyclePoint, List>，ArrayList 追加；先注册先执行；**允许重复注册**（无去重）；`<hook>` DSL = XML 声明序（KeyedList 保序） | `hook/DefaultHookRegistry.java:32,51-54,97-115`、`nop-kernel/.../KeyedList.java:32-54` |
| session middleware | 声明序（装配序） | `<filter-chain>` 先注册（居**最外层**）→ `<middlewares>` 声明序追加；MiddlewareChain index 0=最外层（outer-to-inner） | `engine/AgentExecutorResolver.java:149-157,282-295`、`middleware/MiddlewareChain.java:34,55-61`、`middleware/FilterChainResolver.java:68-86` |
| execution middleware | 注册序 | `<middlewares scope="execution">` 声明序；同点洋葱组合 | `hook/DefaultHookRegistry.java:34-39`、`engine/AgentHookInvoker.java:113-141` |
| 7-checkpoint | **固定序** | postDenial→toolAccess→permission→pathAccess→layer2→layer3→conflict 硬编码；构造时一次构建；不可配置 | `engine/AgentSecurityConsultation.java:113-116,277-285` |
| IContentGuardrail | **单实例** | 单 setter 单字段；多 guardrail 需用户自行复合 | `engine/DefaultAgentEngineConfig.java:110,929-930` |
| IToolCallRepairer | 引擎单实例；ChainRepairer 内 4 stage **固定序** | NameNorm→ArgStructure→ValueCoercion→Cleanup；无短路级联 | `repair/ChainRepairer.java:42-48,74-80` |
| IContributionRegistry | **priority 升序稳定排序，同 priority 注册序兜底**（nop 唯一带优先级的点） | getContributions 快照排序；跨 source 同 id 抛异常 | `contribution/InMemoryContributionRegistry.java:62,87-100,140-154` |
| 事件订阅 | 订阅序 | CopyOnWriteArrayList 扇出 | `engine/DefaultAgentEventPublisher.java:15-32` |
| ITalent / ISkillProvider | 列表注册序 / **单实例**（激活序=requiredSkills 声明序→availableSkills 声明序） | talent 无短路 | `engine/AgentPromptAssembly.java:109-135`、`skill/SkillResolver.java:40-122` |

**重要澄清（对 WI4/plan 线索的勘误）**：session 层与 execution 层**永不合并**——两个不同 enum 的独立 region，接口 javadoc 明示 "the two scopes never interact"（`hook/IHookRegistry.java:50-52`）；不存在"先 session 后 execution"的合并顺序。

### dsh（全部事件统一机制）

| 面 | 排序来源 | 细节 | 锚点 |
|---|---|---|---|
| 所有事件（单一全局总线） | **注册时刻序** | 根 Context 单一 EventsService，每事件一个扁平 Hook[]，push 追加；scope 只做准入过滤（事件只向上流，listener 收到后代 scope 事件）**不影响排序** | `vendor/cordis/src/events.ts:132,165-175,254-260`、`packages/core/scope/src/index.ts:170-185` |
| 位置控制 | **prepend: true（unshift）——全仓唯一手段** | spill-policy/session-reference/model-selection 等在用 | `events.ts:111-117,254-260`、`packages/spill/spill-policy/src/index.ts:185-211` |
| 优先级字段 | **无** | EventOptions 仅 {prepend, global}；grep 无 priority | `events.ts:111-117` |
| 重复注册 | 不去重（同一函数注册两次调用两次）；unregister 只移除第一个匹配 | | `events.ts:257,269-275` |
| 组成行序 | **无装载语义** | Group.update allSettled 并发创建 + Fiber 服务可用性驱动激活；明文 "Row order carries no load semantics" | `packages/bundle/base/cordis.patch.yml:12-13`、`vendor/loader/src/config/group.ts:71`、`vendor/cordis/src/fiber.ts:314-319` |

### pi（加载序 × 注册序）

| 面 | 排序来源 | 细节 | 锚点 |
|---|---|---|---|
| 扩展加载顺序（一切顺序的根） | 组合序（四级路径解析+稳定 rank 排序） | 最终顺序：**CLI > 项目 settings 声明序 > 项目 .pi/extensions readdir 序 > 用户 settings 声明序 > 用户 readdir 序 > packages**；目录内 readdir **不排序**（文件系统序）；子目录按 package.json pi.extensions 声明序/index.ts/index.js；同路径先到先得 | `package-manager.ts:891-943,167-183,2560-2586`、`resource-loader.ts:549-568,613-616,846-858`、`loader.ts:547-585` |
| 同扩展内 handler | 注册调用序 | api.on 仅 push | `loader.ts:260-265` |
| 跨扩展 handler 遍历 | 加载序（外层）× 注册序（内层） | runner.extensions 数组顺序=路径列表顺序 | `runner.ts:299-312,801-833` |
| 优先级字段 | **无** | on(event, handler) 两参数；types.ts 全文无 priority | `loader.ts:260-265` |
| AgentLoopConfig 单槽 | **单槽**（最后赋值胜） | beforeToolCall/transformContext 等被 coding-agent 装配为 runner 链；_installAgentNextTurnRefresh 包装 prepareNextTurn（refresh=外层，先跑 previous 再覆盖 systemPrompt/tools/model/thinking） | `agent-session.ts:484-561`、`sdk.ts:328-364` |
| 重名注册 | 跨扩展**先到先得**（registerTool/Flag）；同扩展内 Map.set 后胜；shortcuts 后者覆盖；command 改名 :1/:2 消歧 | | `runner.ts:450-537,603-637`、`loader.ts:267-274` |
| registerProvider 同名 | config 形式=**字段级合并**（后者 defined 键覆盖，undefined 保留前者）；native 形式=后注册整体覆盖；两形式互斥 | | `model-runtime.ts:733-778`、`runner.ts:353-411` |

## ③ 分发语义

### nop：顺序遍历 + 命中即 return

`invokeHooks`（`engine/AgentHookInvoker.java:142-191`）按列表序逐个调用：
- **Reenter/Bail/Veto 命中→立即 return，同点剩余 hook 全部不执行**（:156-162,172-174,176-178）；Pass 继续。
- hook 抛异常：PRE_*/BEFORE_* 前缀 error+rethrow（循环中止，剩余 hook 不执行→顶层 failed）；ON_ERROR/after_* warn+继续。
- middleware 洋葱：`MiddlewareChain.proceed` index 0 最外层；不调 proceed=断链；Veto 断链；Bail 非 POST 点 fail-loud（`middleware/MiddlewareChain.java:55-61`、`middleware/IAgentMiddleware.java:25-44`）。
- 7-checkpoint：非 ALLOW 立即返回，后续 checkpoint 不执行（`security/SecurityCheckpointChain.java:16-24`）。

### dsh：四种 mode（cordis 统一原语）

| mode | 语义 | 短路 | 锚点 |
|---|---|---|---|
| waterfall | **洋葱**：`next()` 从注册序数组头部 shift——**先注册=最外层=最先执行**；返回值经 return 向外传递=最外层 listener 的返回值；`inner`（内建默认）在所有 listener 之后、仅当每个都调 next() 才执行 | 不调 next()=否决整链含 inner | `vendor/cordis/src/events.ts:234-243` |
| serial | 注册序逐个 await | 返回非 null/false/undefined（isBailed）立即 return，剩余跳过 | `:13-15,204-209` |
| emit | 注册序同步顺序调用，不 await、忽略返回值；裸 emit 同步 throw 会饿死后续；dsh contained 封装（dispatch.emit）逐 listener 兜住 throw+rejection 后继续 | 无短路 | `:194-196`、`packages/core/agent/src/dispatch.ts:120-137` |
| parallel | Promise.allSettled 全执行、顺序无关 | 不短路；全 settle 后抛注册序第一个 rejection | `:183-187`、`packages/core/session/src/index.ts:1144-1161` |

实际顺序场景（出厂插件的容序设计）：
- **agent/request-error 上 llm-retry × compaction-basic**：两者都 `ctx.on` 该事件，注册时机由 fiber 服务可用性驱动（组成行序不保证）；容序证据——llm-retry 默认 retryableCodes 不含 CONTEXT_WINDOW_EXCEEDED（溢出错误 `return next()` 透传给内层），always 模式**先委托下游**（下游已裁决 retry 则采纳）；compaction-basic 只拦溢出码否则透传——**无论谁外谁内语义都正确**（`packages/llm/llm/src/retry-policy.ts:18-24`、`packages/llm/llm-retry/src/index.ts:199-223`、`packages/compaction/compaction-basic/src/index.ts:184`）。
- **tools/post-execute 上 spill-policy**：全仓唯一显式 `{prepend: true}` 钉位——永远最外层：先执行、`await next()` 后收尾，从而能改写所有下游 settle 后的最终结果（`packages/spill/spill-policy/src/index.ts:185-211` 注释即顺序契约）。
- **system-prompt section order 与 listener 顺序是两回事**：section 按 order 数值升序+同 order 按 name code-unit 字典序在 waterfall **之前**排好；listener 洋葱作用于整个 assembly，与 order 解耦（`packages/core/system-prompt/src/index.ts:231-234,589,617-620`）。

### pi：顺序 await + 链式组合 + 首胜短路 + 单槽

| 原语 | 语义 | 短路 | 锚点 |
|---|---|---|---|
| 通用 emit | 外层加载序×内层注册序，顺序 await | 仅 session_before_* 的 `cancel:true` 立即 return（跳过全部剩余）；非 cancel 结果**后者覆盖前者** | `runner.ts:801-833,792-799` |
| 链式替换（message_end/context/before_provider_request/before_agent_start systemPrompt） | 第一个 handler 处理原始值（context 入口 structuredClone **一次**），前者输出=后者输入，全部执行 | 无短路（message_end role 不符跳过该 handler 继续） | `runner.ts:835-875,984-1014,1016-1048,1081-1145` |
| 链式裁决（tool_call） | 顺序 await，truthy 返回覆盖 result | `block:true` 立即 return；**无 try/catch——handler 抛错中止全链**（向外传播→loop catch 降级为 veto） | `:932-953` |
| 链式合并（tool_result） | 逐字段合并，前者修改对后者可见 | 无短路 | `:877-930` |
| 原位 mutation（before_provider_headers） | 共享可变对象，返回值忽略 | 无短路（mutation 先后即顺序） | `:1050-1079` |
| 首胜短路 | user_bash：首个 **truthy** 结果胜出并 return；project_trust：首个非 undecided（yes/no 同权重）胜出；错误不中断 | 首胜即短路 | `:955-982,203-233` |
| input | transform 链 | `action:"handled"` 立即 return | `:1196-1235` |
| 单槽 | AgentLoopConfig 每槽单函数，整体替换；_installAgentNextTurnRefresh 包装链（refresh 外层：先 await previous 再覆盖 4 字段） | 槽位替换 | `agent-session.ts:540-561` |

## ④ 可预测性与可配置性

三问裁定（顺序影响结果吗 / 调用方能控制吗 / 有优先级机制吗）：

| 方 | 顺序影响结果吗 | 调用方能控制吗 | 优先级机制 |
|---|---|---|---|
| nop | **影响**（hooks 短路：先注册者 Veto 即后注册者不执行；middleware 洋葱层次改变 around 语义）但**可预测**：顺序完全由注册/声明序决定，无隐藏重排；7-checkpoint/guardrail/repair 固定序面天然无顺序争议 | 装配期可配（DSL 声明序/filter-chain 位置）；运行期不可（hooks 列表无重排 API）；contribution 有 priority | **仅 IContributionRegistry**（priority 升序稳定排序） |
| dsh | **设计上不影响**：waterfall 语义要求插件容序（透传型总是 next()）；短路点（reject/deny/block）语义与顺序无关；官方明文 "Data decides"（turn-stopping） | 仅 `prepend: true`（个体钉位到最外层）；组成行序不可依赖（服务可用性驱动） | **无**（唯一手段 prepend） |
| pi | **影响且可预测**：链式替换类顺序直接决定最终值（后者覆盖前者）；首胜类顺序决定谁裁决；全部顺序=加载序×注册序，加载序由配置决定 | **可配置性最强**：settings 声明序/CLI 顺序/目录布局都可控制加载序；但无 per-handler 插队 | **无** |

- **对调用方的透明度**：nop 的顺序在装配文件（agent.xml）里静态可见；dsh 的顺序在运行时由 fiber 激活时序决定（静态不可见，靠插件文档与测试约定）；pi 的顺序在 settings/目录结构里静态可见。
- **伪差异警示**：dsh "注册序=外层" 与 nop "先注册=最外层"（filter-chain/middleware index 0）方向**相同**，但 nop hooks 是扁平列表命中即 return（非洋葱）——把 dsh waterfall 直接类比 nop middleware 链会得出"等价"的错误结论（nop hook 无 around 收尾阶段）。

## ⑤ 权威源引用关系

- 本文档是 **S3（同点多触发顺序）主题的权威深挖**。**dsh-D2（WI9）/ pi-D2（WI19）** 引用本文档 ②③④ 结论，只写对比增量；冲突时以本文档为准，由 WI29 收敛。
- 与 04-extension-capability-matrix.md 的分工：04 管单点能力级别（返回值消费），本文档管多实现顺序与分发机制；跨扩展机制叠加/冲突裁决归 WI7（S4，06-extension-composition.md）。
- 对既有文档的勘误回写：WI4/05 计划线索中 "session/execution 两层分存合并顺序" 表述不成立——两层永不交互（② nop 节澄清）；03-flow-agent-loop.md 未涉及此问题无需回写。
- 基线注记：dsh 本次重钉为 c291e7961a（2026-09-10，release-0.1.5-sync-master）；后续 WI 实测 dsh 时须再次确认 HEAD。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（S3 契约）
- `ai-dev/analysis/compare-agent-design/04-extension-capability-matrix.md`（单点能力，census 基线）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/DefaultHookRegistry.java`、`engine/AgentHookInvoker.java`、`engine/AgentExecutorResolver.java`、`middleware/{MiddlewareChain,FilterChainResolver}.java`、`engine/AgentSecurityConsultation.java`、`contribution/InMemoryContributionRegistry.java`
- dsh：`vendor/cordis/src/events.ts`、`vendor/cordis/src/fiber.ts`、`packages/core/agent/src/dispatch.ts`、`packages/core/scope/src/index.ts`、`packages/llm/llm-retry/src/index.ts`、`packages/compaction/compaction-basic/src/index.ts`、`packages/spill/spill-policy/src/index.ts`、`packages/core/system-prompt/src/index.ts`、`packages/core/session/src/index.ts`（`~/ai/deepseek-harness` @ c291e7961a）
- pi：`packages/coding-agent/src/core/package-manager.ts`、`resource-loader.ts`、`extensions/{runner,loader}.ts`、`core/agent-session.ts`、`core/model-runtime.ts`、`core/sdk.ts`（`~/ai/pi` @ c49906ec7）
