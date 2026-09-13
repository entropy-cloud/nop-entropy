# pi-D5 自动切换对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D5 子机制 D5-1..D5-5）、02-terminology-map.md（T11）; 机制事实引用 03 §2.3 与 dsh-D5 报告（nop 侧共享基线）；deps WI21（pi-D4）已裁定容错基线（配额不可重试）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`

## ① 结论摘要

- 总裁定：**nop 领先**。pi 与 dsh 同样无内置故障转移（roadmap 初步假设"pi 明确留白给扩展"当日复核**成立**），且留白比 dsh 更彻底——无 waterfall 式请求改写机制，切换只有 model_select 事件（用户/扩展驱动）与 registerProvider 热注册两条手动路径。
- 关键差异 1（D5-5）：pi 的设计立场最显式——配额错误不可重试（分类器硬排除）即"快速失败不换道"；`Model.compat.allowedFallbackModels`→Anthropic 原生 `params.fallbacks` 是 API 协议特性非 agent 层 failover。
- 关键差异 2（D5-4）：pi 的 `prepareNextTurn` hook 可换 model/context/thinkingLevel——单点替换能力存在但无故障触发语义；registerProvider 热注册是"换端点"机制非"切换"机制。
- 可吸收增量建议一句话：与 dsh-D5 同向——nop 领先地位保持；pi 的 per-call getApiKey（支持过期 OAuth 刷新）是 nop 账号链可借鉴的凭据新鲜度设计（见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D5 报告 ② 节共享：三级故障转移（账号链→跨 provider 链→模型 tier，`LlmCallCoordinator.java:276-328`）+ ThresholdBreaker 熔断（:698-752）+ 配额驱动切换 + SmartModelRouter 分级路由 + fail-loud（`router/SmartModelRouter.java:84-122`）。

## ③ pi 侧机制与锚点

按 02 T11 与 03 §2.3、dsh-D5 ③ 的 pi 行，对比增量：

- **故障转移**（D5-1）：无内置编排（`runLoop` 遇 stopReason error/aborted 直接终止，`packages/agent/src/agent-loop.ts:196-200`）；换模型=`model_select` 事件（source: set|cycle|restore，`agent-session.ts:1573-1586`）由用户 setModel 或扩展触发——**用户/扩展驱动，非故障驱动**。
- **熔断与冷却**（D5-2）：无概念；兜底=retry 双层预算（D4）。
- **配额感知**（D5-3）：配额错误被 `NON_RETRYABLE_PROVIDER_LIMIT_ERROR_PATTERN` 硬排除不可重试（`packages/ai/src/utils/retry.ts:7-24`）——快速失败；无配额转移。
- **模型分级路由**（D5-4）：`prepareNextTurn` hook 返回 AgentLoopTurnUpdate 可换 model/thinkingLevel/context（`packages/agent/src/types.ts:229-245`）——机制存在但无复杂度分级/预算降档语义；`registerProvider(name, config)` 热注册支持覆盖 baseUrl/自定义 streamSimple（`extensions/types.ts:1388-1456`、`model-runtime.ts:742-778` 字段级合并）——换端点≠故障切换。
- **fail-loud**（D5-5）：`setModel` 校验 auth 无 key 直接抛"No API key for provider/model"（`agent-session.ts:1596-1600`）；扩展 setModel 返回 false（`extensions/types.ts:1376`）；唯一近似物 `Model.compat.allowedFallbackModels`→Anthropic `params.fallbacks`（`anthropic-messages.ts:1107-1109`）——provider 原生 fallback，非 agent 层。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D5-1 provider/model/账号故障转移 | 内置三级通道自动切换 | 无；model_select 用户/扩展驱动 + registerProvider 换端点（非故障触发） | nop 领先 | nop `LlmCallCoordinator.java:276-328`；pi `agent-loop.ts:196-200`、`agent-session.ts:1573-1586`——故障驱动的自动切换 pi 完全缺失 |
| D5-2 熔断与冷却 | ThresholdBreaker 状态机+熔断感知换模 | 无概念；retry 双层预算兜底 | nop 领先 | 同 dsh-D5 D5-2 结论；pi 与 dsh 同缺 |
| D5-3 配额感知 | QUOTA→驱动账号链切换+IBudgetProvider 预算降档 | 配额不可重试（分类器硬排除）=快速失败不换道 | nop 领先 | pi `retry.ts:7-24`——"最典型的可切换故障"在 pi 是终态 |
| D5-4 模型分级路由 | SmartModelRouter 复杂度分级+预算降档+fallback 链 | prepareNextTurn 可换 model（机制在、故障语义无）；无分级路由 | nop 领先 | pi `types.ts:229-245`——替换机制存在但无自动触发；与 dsh 的 installModelSelection 同属"手动切换"族 |
| D5-5 切换语义与 fail-loud | 通道全耗尽 fail-loud+role=80 审计 | 无 key 结构化抛错；per-call getApiKey（过期 OAuth 每次调用前重解析，`agent-loop.ts:305-306`）；fallbacks 是 API 协议特性 | 等价 | 双方 fail-loud 哲学一致；pi 的 getApiKey 凭据新鲜度设计是独有亮点（nop 账号链静态 key 列表），单点扳回 |

## ⑤ 语义差异与取舍

- **三方"无"的谱系**（与 dsh-D5 ⑤ 合观）：dsh 无内置但有完整表达机制（waterfall 改写 LlmCallConfig+request-error payload 决策输入）——留白给插件；pi 无内置且表达机制弱（只有事件+热注册）——留白给用户/扩展的手动操作。nop 内置全自动。三者构成"自动化程度谱系"：nop > dsh > pi。
- **凭据新鲜度**：pi per-call getApiKey 每次调用前重解析（应对 OAuth token 过期）——nop AccountChain 是静态 key 列表，长会话中 token 过期会转为 AUTH_INVALID→账号链切换（行为上可恢复但语义不同：pi 原地刷新 vs nop 换账号）。
- **fallbacks 陷阱**（词同义异）：Anthropic `params.fallbacks` 是 API 网关层的模型回退参数（provider 侧行为），与 agent 层 failover（感知错误并主动换道）机制层级不同——pi 暴露它不等于 pi 有 agent 层 failover。
- **registerProvider 的真实定位**：热注册支持 OAuth/自定义 streamSimple/baseUrl 覆盖——它是"provider 生态扩展"机制，被 failover 场景借用时需要扩展自己写全部切换逻辑（含故障检测）。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：nop 领先**——五个子机制四个 nop 领先（D5-1/2/3/4），一个等价（fail-loud）；pi 在三方中自动化程度最低（与 dsh-D5 结论合并：nop 是唯一内置实现）。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 账号链凭据】AccountChain 条目支持 per-call 凭据解析器（对位 getApiKey），长会话 OAuth token 过期时原地刷新而非切换账号（减少审计噪音与配额分散）。
2. 【来源 pi；反向记录供 pi 生态】pi 可将 nop 的账号链+熔断模式实现为扩展（registerProvider+model_select 已有切换原语，缺故障检测与编排——与 dsh-D5 建议 1 同旨）。
3. 【来源 nop 自身；登记】三方自动化谱系（nop 内置/dsh 表达机制/pi 手动）已验证，总报告 WI28 可作为结构性差异清单条目。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D5 定义）、`02-terminology-map.md`（T11）、`dsh-D5-auto-failover.md`（nop 基线共享+deps pi-D4）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（Owner doc）
- nop：`nop-ai/nop-ai-core/.../reliability/`、`nop-ai/nop-ai-agent/.../engine/LlmCallCoordinator.java`
- pi：`packages/ai/src/utils/retry.ts`、`packages/coding-agent/src/core/{agent-session,model-runtime}.ts`、`packages/ai/src/api/anthropic-messages.ts`（`~/ai/pi` @ c49906ec7）
