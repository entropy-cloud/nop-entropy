# pi-D2 扩展点对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D2 子机制 D2-1..D2-4）、02-terminology-map.md（T6-T7）; 专项 04（能力 ③3.3）、05（顺序）、06（槽位-事件桥接）——引用只写对比增量
> Owner: `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（6 项代码偏差见 04 ⑤）

## ① 结论摘要

- 总裁定：**等价（组织哲学三分中的第三极）**。pi 的三层扩展位（配置级单槽 hook → ExtensionAPI 多播事件 → register* 注册面）与 nop 双层枚举点、dsh 统一 waterfall 各成一极；能力上限相当（census：nop ≈38 面 / dsh ≈25 面 / pi ≈53 面）。
- 关键差异 1（D2-2）：pi 的配置级 hook 与扩展事件是**同一通道的装配**（beforeToolCall 槽装的就是 emitToolCall），SDK 用户函数与扩展链无组合点——单槽赋值即覆盖。
- 关键差异 2（D2-3）：pi 的 veto 语义按层不同构——配置级 hook 抛异常=run 终止，扩展 handler 抛异常=包含降级（唯一穿透点 tool_call 降级为 veto）；pi 扩展层独有"就地 mutate input 无再校验"通道。
- 关键差异 3（D2-4）：pi 异常传播双轨哲学（04 ④.3-4）——同 within 机制内一致，跨机制行为不同；nop 的不对称在层内，pi 的不对称在层间。
- 可吸收增量建议一句话：nop 可吸收 pi 的"事件 handler 异常隔离+错误上报通道"（runner emitError 对位，见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D2 报告 ② 节共享（04 ③3.1 权威结论），要点：12 lifecycle point（HookResult 四态合同+白名单）+ 4 ExecutionPoint + 7-checkpoint + guardrail/repairer 单实例 + 9 策略对象；注册三形态（API/XML DSL/contribution）；错误传播前缀 fail-fast/后缀容错不对称（`engine/AgentHookInvoker.java:58-79,140-190`、`middleware/ExecutionPoint.java`）。

## ③ pi 侧机制与锚点

按 04 ③3.3、05 ②/③、06 ③3.3/④ 权威结论，对比增量：

- **扩展点类型**（D2-1）：三层——①`AgentLoopConfig` 配置级 hook（types.ts:149-293：transformContext/convertToLlm/getApiKey/shouldStopAfterTurn/prepareNextTurn/getSteeringMessages/getFollowUpMessages/beforeToolCall/afterToolCall + toolExecution 调度配置），**每类单槽无链**；②ExtensionAPI 事件（`pi.on`，34 type 标签/25 联合成员/48 具体事件接口，`extensions/types.ts:1050-1261`）；③register* 注册面（tool/command/shortcut/flag/renderer×3/provider，:1268-1456）。
- **注册方式**（D2-2）：事件注册=api.on push（加载序×调用序，`loader.ts:260-265`）；单槽=赋值覆盖（无链；coding-agent 用 `_installAgentToolHooks` 把槽装成 runner 链，`agent-session.ts:484-538`）；registerProvider config 字段级合并 vs native 后覆盖（`model-runtime.ts:733-778`）；无优先级字段。
- **veto/改写**（D2-3）：配置级——beforeToolCall block（不能改参数，`types.ts:61-69`）、afterToolCall 浅覆盖、shouldStopAfterTurn 优雅终止；扩展层——tool_call block+**就地 mutate event.input（无再校验）**、session_before_* cancel、project_trust/input/user_bash 首胜裁决、message_end 同 role 替换（04 ③3.3.2 全表）。
- **错误传播**（D2-4）：配置级 hook 调用点无 try/catch→`Agent.runWithLifecycle` catch→`handleRunFailure` 合成 error 收场（`agent.ts:502-527`）；扩展 handler 逐个 try/catch→errorListeners 上报不打断（`runner.ts:809-828`）；唯一穿透 tool_call（:941 无 catch）→降级 veto。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D2-1 扩展点类型与触发阶段 | 双 scope 枚举点+checkpoint 链+策略对象（类型显式区分会话/尝试粒度） | 三层（配置单槽/多播事件/注册面）；粒度由三层各自覆盖（hook=每次调用、事件=全生命周期、注册面=资源面） | 等价 | nop `AgentLifecyclePoint.java:4-14`+`ExecutionPoint.java`；pi `types.ts:149-293`+`types.ts:1050-1261`——面覆盖 pi 最宽（census 53 面），组织最碎；三者无包含关系 |
| D2-2 注册方式 | 三形态（API/XML DSL/contribution），DSL 声明序静态可审计 | 双形态（事件 on 多播+槽位赋值）；扩展加载序四级解析可配（CLI>项目 settings>auto>用户>packages）；provider 双语义注册 | 等价 | nop `AgentExecutorResolver.java:148-156`；pi `package-manager.ts:891-943`、`agent-session.ts:485`——nop 静态可审计 vs pi 加载序用户可控（05 ④）；槽位覆盖语义是 pi 独有陷阱（SDK 用户赋值会杀扩展桥） |
| D2-3 veto / 改写语义 | HookResult 四态合同+白名单；策略对象面 run 级终止权 | 配置级 block/浅覆盖/优雅终止；扩展层 veto 8 事件+就地 mutate（无再校验）+首胜裁决；无 run 级 veto（shouldStopAfterTurn/terminate 是优雅停止非否决） | 等价 | 04 ③3.1/3.3 能力矩阵、06 ④.2——nop veto 权在 hook+策略面，pi veto 权在扩展事件；就地 mutate（无再校验）与 nop repairer 前置改写是两种"参数可变"通道，审计取向相反（dsh 禁改） |
| D2-4 能力级别与错误传播 | 层内不对称（PRE fail-fast/POST 容错；执行级 PRE try 外 POST try 内） | 层间不对称（配置级抛=run 终止 vs 扩展级抛=包含降级）；错误上报通道（errorListeners）独立 | 等价 | 06 ④.1（nop 行）vs 06 ④.1（pi 行 `runner.ts:809-828`）——两者的不对称都是"实现契约"而非缺陷，但 pi 的 errorListeners 显式上报通道 nop 缺（nop after_* 仅 LOG） |

## ⑤ 语义差异与取舍

- **单槽 vs 多播的取舍**：pi 配置级 hook 单槽=零组合歧义（谁最后赋值谁生效）但多扩展协作必须经 coding-agent 装配层；nop 枚举点多播=天然多实现但需要白名单约束合法返回值。三方对照：pi"少而纯"、nop"多而约"、dsh"统一原语"。
- **"hook"词义在 pi 内部分裂**（02 T6）：AgentLoopConfig hook（单槽函数）≠ ExtensionAPI 事件 handler（多播）——中文文档常混译"钩子"；本报告严格区分"槽/handler"。
- **mutate-without-revalidation**：pi tool_call 就地改参数不重校验（ext/types:914-928 明文）——与 nop repairer（改后走安全检查）取舍相反；两者都违背 dsh 的"已落账不可变"不变式。登记为三方参数可变谱：dsh 禁改 < nop 改后检查 < pi 改不检查。
- **错误上报的可见性**：pi ExtensionError 事件式上报（UI 可订阅）vs nop LOG.warn/error（不可编程消费）——pi 的扩展失败可观测性更好。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：等价**——三层扩展位与双层枚举点能力上限相当，组织哲学正交；pi 的面最宽（53）但最碎，nop 的粒度类型化最显式，无一方全面领先。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 事件容错可见性】为 IAgentEventPublisher 引入订阅者失败上报通道（error listener 或事件），使扩展消费方的失败可编程观测（当前仅 LOG）。
2. 【来源 pi；针对 nop hook 白名单】nop 的合法点白名单（Bail/Reenter fail-loud 校验）可借鉴 pi 的"类型即合同"进一步静态化——以类型系统（如 per-point 返回类型接口）替代运行时校验。
3. 【来源 nop；反向记录】pi 的单槽覆盖陷阱（SDK 用户赋值杀扩展桥）提示：若 nop 引入类似槽位 API，需保留组合点（coding-agent 式装配层）而非裸赋值。
4. 【来源 pi；针对 nop mutate 审计】若 nop 引入扩展级参数改写通道（当前仅 repairer），必须重走安全检查（pi 的 no-revalidation 是反例记录）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D2 定义）、`02-terminology-map.md`（T6-T7）、`04/05/06` 专项、`dsh-D2-extension-points.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../hook/`、`middleware/`
- pi：`packages/agent/src/types.ts`、`packages/coding-agent/src/core/extensions/{types,runner,loader}.ts`、`core/agent-session.ts`、`core/model-runtime.ts`（`~/ai/pi` @ c49906ec7）
