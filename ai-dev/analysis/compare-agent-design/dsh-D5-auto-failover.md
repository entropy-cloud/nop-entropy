# dsh-D5 自动切换对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 锚点重钉: 2026-09-14，HEAD 4582e780dad4（plan 355 重构+M5/M6 修复后逐锚点核对；仅行号更新，结论不变）
> 引用: 00-dimension-matrix.md（WI2，D5 子机制 D5-1..D5-5）、02-terminology-map.md（T11）; 机制事实引用 03-flow-agent-loop.md（P8/P9 链路）与 04（策略对象面），只写对比增量；deps WI11（dsh-D4）已裁定容错基线
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`

## ① 结论摘要

- 总裁定：**nop 领先**。nop 是两方中唯一内置多通道自动切换的实现（roadmap 初步假设②当日复核**成立**）；dsh 核心刻意不内置，切换表达力留白给 `agent/request`/`agent/request-error` waterfall 扩展。
- 关键差异 1（D5-1）：nop 内置账号链（同 provider 换 key）→跨 provider failover 链→模型 tier 三级通道；dsh 全仓 grep "failover" 零命中。
- 关键差异 2（D5-2）：nop ThresholdBreaker 熔断状态机（CLOSED/OPEN/HALF_OPEN，阈值 3/60s 冷却/懒探针）；dsh 无熔断概念。
- 关键差异 3（D5-5）：dsh 的"无"是显式设计立场——`agent/request-error` payload 同时给出 provider 与 retryPolicy，failover 型插件有完整决策输入；dsh 模型切换的撕裂防护（installModelSelection 三联监听）反而比 nop 的审计消息更精细。
- 可吸收增量建议一句话：反向记录为主——dsh 的"切换一致性快照"设计（assembled 快照防 prompt/路由撕裂）值得 nop 在多通道切换时借鉴（见 ⑥）。

## ② nop 侧机制与锚点

按 02 T11 与 03 §2.1（P8/P9），对比增量：

- **故障转移**（D5-1）：三级通道——QUOTA/AUTH_INVALID→`IAccountChainResolver` 账号链（同 provider 换 key/baseUrl，attempt 归零，`LlmCallCoordinator.java:319-331`、`CORE/service/LlmConfigHelper.java:168`）；账号链耗尽→`IProviderFailoverChain`（`_default.llm-failover.xml` 声明链，跳过冷却中 provider，:333-347）；TRANSIENT→`IModelRouter.getFallback` 模型 tier 回退（:354-363）。
- **熔断与冷却**（D5-2）：`ThresholdBreaker`（CLOSED/OPEN/HALF_OPEN，连续失败阈值 3/60s 冷却/半开懒探针，`CORE/reliability/ThresholdBreaker.java:112-144`）；熔断感知换模 `resolveCircuitAware`（主模型 OPEN 则扫 fallback 链找允许模型，上限 64，全拒绝 fail-loud，`LlmCallCoordinator.java:784-837`）。
- **配额感知**（D5-3）：QUOTA_EXCEEDED 触发账号链切换（配额失败驱动转移）；`IBudgetProvider` 预算快照供 SmartModelRouter 降档（`engine/ReActAgentExecutor.java:872-879`）。
- **模型分级路由**（D5-4）：`SmartModelRouter`——Complexity 启发式分级 + 预算超额降档 + tier fallback 链（`router/SmartModelRouter.java:85-123`）；切换写 role=80 审计消息（`ReActAgentExecutor.java:909-919`）。
- **fail-loud 语义**（D5-5）：熔断全拒绝/账号链耗尽/fallback exhausted→NopAiAgentException→status=failed（`LlmCallCoordinator.java:170-173,609-627`）——快速失败不静默。

## ③ 对方侧机制与锚点

按 02 T11 与 03 §2.2（P10/P13），对比增量：

- **故障转移**（D5-1）：无内置（grep 零命中）；表达方式=`agent/request` waterfall 返回替换 LlmCallConfig（改 provider/model 字段，`packages/core/agent/src/runtime-types.ts:347`）；`agent/request-error` payload 同时给出 provider 与 retryPolicy——failover 插件的完整决策输入（:363）。
- **熔断与冷却**（D5-2）：无熔断概念；兜底是 llm-retry 的 per-provider retryPolicy（normal 5 次/always，D4 已裁定）。
- **配额感知**（D5-3）：QUOTA 仅是稳定错误码分类，不触发转移；pi 侧"配额不可重试"的快速失败立场在 dsh 由 retryableCodes 默认集表达（QUOTA 不在可重试集）。
- **模型分级路由**（D5-4）：无故障驱动路由；`installModelSelection` 是用户/扩展驱动的成对切换（system-prompt/assemble + agent/request 读同一 assembled 快照 + pre-step prepend 通知，**三联监听防 prompt/路由撕裂**，`packages/core/agent/src/model-selection.ts:76-127`）。
- **fail-loud 语义**（D5-5）：waterfall 后缺 provider/model→结构化报错 `NO_ADAPTER` 类错误（`agent.ts:535-537`）——与 nop 的 fail-loud 同哲学；差异在 nop 是"所有通道耗尽后"，dsh 是"配置缺失即报"。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D5-1 provider/model/账号故障转移 | 内置三级：账号链→跨 provider 链→模型 tier；声明式链配置 | 无内置；waterfall 改写表达（failover 可写为插件，payload 有完整决策输入） | nop 领先 | nop `LlmCallCoordinator.java:312-364`；dsh grep 零命中 + `runtime-types.ts:347,363`——开箱自动切换能力 nop 独有 |
| D5-2 熔断与冷却 | ThresholdBreaker 状态机（3/60s/懒探针）+ 熔断感知换模扫描 | 无熔断概念；per-provider 重试策略兜底 | nop 领先 | nop `ThresholdBreaker.java:112-144`、`LlmCallCoordinator.java:784-837`；dsh 无对位——熔断防止对故障通道的持续撞击，dsh 场景依赖重试上限自然止损 |
| D5-3 配额感知 | QUOTA→驱动账号链切换；IBudgetProvider 预算降档 | QUOTA 仅分类不转移；可重试集声明表达快速失败 | nop 领先 | nop `LlmCallCoordinator.java:319-331`；dsh `retry-policy.ts:14-24`——配额是可切换故障的最典型场景，nop 自动换 key，dsh 需插件/人工 |
| D5-4 模型分级路由 | SmartModelRouter：Complexity 分级+预算降档+fallback 链（故障驱动） | 无故障驱动路由；model_selection 用户/扩展驱动（非故障触发）但撕裂防护精细 | nop 领先 | nop `SmartModelRouter.java:85-123`；dsh `model-selection.ts:76-127`——故障驱动的自动分级路由 nop 独有；dsh 的手动切换一致性工程更强（不可比子面：交互式换模型 vs 故障降级） |
| D5-5 切换语义与 fail-loud | 通道全耗尽 fail-loud（异常→failed）；切换写 role=80 审计 | 配置缺失结构化报错；切换表达留白扩展；无切换审计事件（header change 记账间接可见） | 等价 | nop `LlmCallCoordinator.java:170-173`、`ReActAgentExecutor.java:909-919`；dsh `agent.ts:535-537`——fail-loud 哲学一致；dsh 的 request/header change 记账（`session/src/types.ts:232-261`）其实是另一种切换审计 |

## ⑤ 语义差异与取舍

- **内置 vs 留白**：nop 把 failover 当引擎职责（声明式链配置+熔断+审计全内置）；dsh 把它当生态职责（waterfall 表达力+payload 决策输入）。取舍：nop 开箱高可用适合服务端部署；dsh 立场使核心无通道状态、切换逻辑可单测可定制。两者都符合各自产品形态——但就"自动切换能力本身"而言 nop 完备、dsh 缺位，裁定不打折。
- **切换一致性**：nop 切换后 prompt/工具面不变（只有 model 变），撕裂面小；dsh 的 assemble×request 联动是因为其 system prompt 与路由都可被独立改写（scope 注册+waterfall），一致性需要显式快照协议——dsh 的三联监听是为其架构量身设计，nop 若引入"prompt 随路由切换"的场景才需要同款。
- **审计语义不等价**：nop role=80 审计消息进入持久会话（人可见）；dsh request/header reason:change 是机器记账（KV 前缀失效判断用）。词同义异警示：两者都"记录切换"但消费方与粒度不同。
- **熔断的哲学**：nop 熔断是共享状态（跨请求累计）；dsh 无状态化处理（重试上限即止损）——在单进程多会话场景 nop 熔断保护多会话共享的通道资源，dsh 每会话独立重试可能重复撞击故障通道。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：nop 领先**——五个子机制中四个 nop 领先（D5-1/2/3/4），一个等价；nop 是两方中唯一提供开箱自动多通道切换的实现，且熔断/配额/分级路由三层纵深完整。

可吸收增量建议（仅记录，不实施）：

1. 【来源 nop；反向记录供 dsh 参考】dsh 可将 nop 的账号链模式（QUOTA/AUTH 同 provider 换 key）实现为官方 llm-failover 插件（agent/request-error 已有完整决策输入），填补最常见的配额切换场景。
2. 【来源 dsh；针对 nop 切换一致性】若 nop 未来支持"按路由切换 system prompt/工具面"（如不同模型不同 prompt 模板），应借鉴 dsh 的 assembled 快照协议（prompt 与 route 从同一快照读取，杜绝半切换状态）。
3. 【来源 dsh；针对 nop 切换审计粒度】nop role=80 审计消息可补充 request/header 式的机器记账字段（前次/本次 model 键），供 token 计量与缓存失效判断消费（与 D6 建议联动）。
4. 【来源 nop 自身；记录】熔断的跨会话共享特性应在文档中显式声明（多会话共享通道熔断状态是特性而非缺陷）——当前 `ThresholdBreaker` 作用域文档不足。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D5 定义）、`02-terminology-map.md`（T11）、`03-flow-agent-loop.md`（P8/P9）、`dsh-D4-fault-tolerance.md`（deps 前置：重试基线）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（Owner doc）
- nop：`nop-ai/nop-ai-core/.../reliability/{ThresholdBreaker,IAccountChainResolver,IProviderFailoverChainResolver}.java`、`nop-ai/nop-ai-agent/.../engine/LlmCallCoordinator.java`、`router/SmartModelRouter.java`
- dsh：`packages/core/agent/src/{runtime-types,model-selection}.ts`、`packages/llm/llm/src/retry-policy.ts`（`~/ai/deepseek-harness` @ c291e7961a）
