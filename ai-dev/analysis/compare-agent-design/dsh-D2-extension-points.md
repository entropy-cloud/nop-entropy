# dsh-D2 扩展点对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D2 子机制 D2-1..D2-4）、02-terminology-map.md（T6-T7）; 专项 04-extension-capability-matrix.md（能力级别权威源）、05-extension-ordering.md（顺序语义）、06-extension-composition.md（跨机制协同）——本报告能力级别/顺序/协同全部引用专项结论只写对比增量
> Owner: `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（该 doc 存在 6 项与代码的偏差，已登记于 04 ⑤ 与 daily log，本报告以代码为准）

## ① 结论摘要

- 总裁定：**等价（各有体系性优势）**。双方都是"全场景统一扩展原语"设计，但原语不同——nop 是枚举点+结果对象（HookResult 四态），dsh 是 cordis 事件五 mode（waterfall 洋葱为核）。
- 关键差异 1（D2-3 veto）：dsh veto 是结构化 Decision 返回值（reject/deny/block）且**无 run 级终止权**（06 ④.2）；nop veto 语义不对称（6/12 lifecycle point 的 veto 无消费者）但策略面拥有 run 级终止权。
- 关键差异 2（D2-2 注册）：dsh 统一 `ctx.on` + scope 准入（唯一位置控制 prepend）；nop 三种注册形态并存（代码 API / XML DSL / contribution），DSL 声明序可静态审计。
- 关键差异 3（D2-4 错误传播）：dsh 结构化收口（turn error，driver 存活）；nop 前缀 fail-fast/后缀容错的不对称设计。
- 可吸收增量建议一句话：nop 可借鉴 dsh 的"参数已落账不可改"不变式与声明式 filter-chain 的注册容错语义（见 ⑥）。

## ② nop 侧机制与锚点

能力级别、顺序语义、跨机制协同按 04 ③3.1、05 ②/③、06 ③3.1/④ 权威结论，只列对比相关增量：

- **扩展点类型与触发阶段**（D2-1）：12 个 session 级 AgentLifecyclePoint（PRE_CALL…AFTER_TOOL_RESULT_PROCESSED，`hook/AgentLifecyclePoint.java:4-14`）+ 4 个 attempt 级 ExecutionPoint（PRE/POST_LLM_ATTEMPT、PRE/POST_TOOL_ATTEMPT，`middleware/ExecutionPoint.java`）+ 7-checkpoint 安全链 + guardrail/repairer 单实例面 + 9 策略对象。nop 是三方唯一在类型上显式区分会话/尝试粒度的（04 ③）。
- **注册方式**（D2-2）：三种形态——代码 API（`IHookRegistry.register` + Builder）、XML DSL（`<hook event>`/`<middlewares>`/`<filter-chain>`，`hook/DefaultHookRegistry.java:97-115`、`engine/AgentExecutorResolver.java:148-156`）、contribution 注册表（HOOK/PROMPT 类有引擎消费）。声明序静态可审计；同 impl 双声明 fail-loud（`AgentExecutorResolver.java:280-290`）。
- **veto/改写**（D2-3）：HookResult 四态合同（Pass/Veto/Reenter/Bail），合法点白名单 fail-loud（`hook/AgentHookInvoker.java:154-161,212-219`）；执行级 veto→retry 决策（cap 3）；middleware veto 结构性优先于 hooks（06 ④.1）。
- **错误传播**（D2-4）：PRE_*/BEFORE_* hook 抛→重抛→顶层 failed；after_* 容错继续；执行级不对称（PRE 在 try 外、POST 在 try 内进 retry）——06 ④.1。

## ③ 对方侧机制与锚点

按 04 ③3.2、05 ②/③、06 ③3.2/④ 权威结论，对比增量：

- **扩展点类型**（D2-1）：cordis 事件五种 DispatchMode（emit/parallel/serial/bail/waterfall，`vendor/cordis/src/events.ts:32`）；agent/* 3 waterfall + 1 serial + 8 emit、tools/* 4 waterfall + 2 emit、llm/stream、system-prompt/assemble、session/* 4——**一个统一原语覆盖全部扩展面**，无独立"中间件/拦截器"概念。
- **注册方式**（D2-2）：统一 `ctx.on(event, listener, {prepend?})`（`events.ts:254-260`）；scope 准入过滤（事件只向上流）；无优先级字段，prepend 是唯一位置控制；组成 patch 行序无装载语义（fiber 服务可用性驱动）。
- **veto/改写**（D2-3）：结构化 Decision 判别联合（PreStepDecision reject/enter、PreToolDecision allow/deny/ask、PostToolDecision accept/block、RequestErrorAction retry/undefined）——每点返回类型显式；waterfall 不调 next() 即否决内链（技术 veto，语义由消费端类型裁定）。
- **错误传播**（D2-4）：waterfall 异常→turn catch→结构化 turn error + agent/error emit→driver 边界包含（进程不死）；emit 类 per-listener 双 contained（裸 change 事件除外）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D2-1 扩展点类型与触发阶段 | 双 scope（session 12 点 + attempt 4 点）+ 7-checkpoint + 单实例面（guardrail/repairer）+ 9 策略对象；类型系统显式区分粒度 | 单一事件原语（五 mode）覆盖 agent/tools/llm/session/system-prompt 全部面；无独立 middleware/拦截器概念 | 等价 | nop `AgentLifecyclePoint.java:4-14` + `ExecutionPoint.java`；dsh `events.ts:32` + `runtime-types.ts:245-404`——面覆盖度相当（04 census：nop ≈38 面 vs dsh ≈25 面），组织哲学不同（分层枚举 vs 统一原语） |
| D2-2 注册方式 | 三形态并存（API/XML DSL/contribution）；DSL 声明序静态可审计；重名 fail-loud | 统一 ctx.on + scope 准入；prepend 唯一位置控制；注册序=运行时序（组成行序无语义） | 等价 | nop `DefaultHookRegistry.java:97-115`、`AgentExecutorResolver.java:148-156,280-290`；dsh `events.ts:254-260`、`cordis.patch.yml:12-13`——nop 静态可审计性更强，dsh 注册面更统一（05 ②④） |
| D2-3 veto / 改写语义 | HookResult 四态合同 + 白名单 fail-loud；veto 消费点不对称（6/12 点丢弃）；策略对象面拥有 run 级终止权（judge/sustainer/wait/denial/breaker/retry） | 结构化 Decision 判别联合逐点显式；veto 封顶 turn 级（pre-step reject→blocked）；**无 run 级 veto**（agent.cancel 命令唯一） | 等价 | 04 ③3.1/3.2 能力矩阵、06 ④.2 终止范围表——nop veto 权限更分散且部分为死配置，dsh veto 更诚实（每点类型显式）；run 级终止权 nop 独有但属策略对象面而非监听者 |
| D2-4 能力级别与错误传播 | 前缀 fail-fast（PRE_*/BEFORE_* 重抛→failed）/ 后缀容错（after_* 告警继续）；执行级不对称（PRE try 外、POST try 内进 retry）；事件订阅者异常隔离 | waterfall 异常→结构化 turn error→driver 存活；emit 双 contained；唯一裸面 tools/change | 等价 | 06 ④.1；nop 的 fail-fast/容错分界清晰但不对称易误用（PRE_LLM 中间件抛错直接终止 vs POST 进 retry），dsh 的统一结构化收口更可预测 |

## ⑤ 语义差异与取舍

- **结果对象 vs 返回值协议**（T7）：nop HookResult 四态是**类型层级**（instanceof 判定），dsh Decision 是**判别联合**（kind 字段）——语义等价、形态不同；但 nop 的四态在 12 个点上合法子集不同（白名单），dsh 每点类型即合同（无越权可能）——dsh 的类型即文档更防误用。
- **"参数已落账"不变式**：dsh tools/pre-execute 明确禁止改参数（"already logged and presented"，`tools/src/index.ts:583-591`），改写只能经 tools/execute 换 signal 或 post-execute 改结果；nop 相反——repairer 在安全检查**前** transform 工具调用（`ReActAgentExecutor.java:864`），pi 扩展可就地 mutate。这是审计取向差异：dsh 把"已展示给用户的输入不可变"作为不变式。
- **死配置问题**：nop 6/12 lifecycle point 的 veto/bail 无消费者（04 ③3.1.1）——合同声明的能力与实际消费脱节；dsh 每个事件的 mode 与返回类型即实际行为。词同义异警示：看到 nop hook 能返回 Veto 不代表该点 veto 生效。
- **策略对象归属**：nop 的 9 个策略对象（judge/sustainer/breaker 等）在 dsh 无监听者对位（dsh 的对位是 per-provider retryPolicy 配置与 agent/request waterfall 改写）——按 02 T6 口径，这是"机制等价、形态不同"的典型（可替换组件 vs 事件改写）。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——双方扩展面覆盖度与能力上限相当（census 38 vs 25 面、五级能力各有分布），组织哲学不同（分层枚举+白名单合同 vs 统一原语+类型即合同），无一方全面领先；nop 的 run 级策略终止权与 dsh 的统一原语各自不可替代。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop veto 死配置】对 6 个 veto 无消费者的 lifecycle point（PRE_ACTING/POST_ACTING/POST_CALL/PRE_COMPACT/POST_COMPACT/REASONING_CHUNK）要么实现消费要么在类型上收窄合同（如引入 `ObserverPoint` 无返回值接口），消除"合同声明≠实际行为"的脱节。
2. 【来源 dsh；针对 nop 修复器改参】评估"参数已落账不可改"不变式：将 ChainRepairer 移到安全检查之后或对修复前后差异做审计落账（当前修复发生在 7-checkpoint 评估之前，修复后的参数才被检查——审批所见与执行所用一致性可加强）。
3. 【来源 dsh；针对 nop 三种注册形态】为 XML DSL 增加 prepend/顺序微调能力（当前仅声明序），或在文档中明确声明序即执行序的合同地位（当前行为正确但合同未成文）。
4. 【来源 nop；反向记录】dsh 可参考 nop 的 7-checkpoint 固定序安全链模式为 tools/pre-execute 提供有序多 checkpoint 组合（当前为平铺 waterfall，顺序语义靠插件自 律）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D2 定义）、`02-terminology-map.md`（T6-T7）、`04-extension-capability-matrix.md`、`05-extension-ordering.md`、`06-extension-composition.md`（权威源）
- `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（Owner doc，6 项偏差见 04 ⑤）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/`、`middleware/`、`engine/AgentExecutorResolver.java`
- dsh：`vendor/cordis/src/events.ts`、`packages/core/agent/src/runtime-types.ts`、`packages/core/tools/src/index.ts`（`~/ai/deepseek-harness` @ c291e7961a）
