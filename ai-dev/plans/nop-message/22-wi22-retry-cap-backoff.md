# 22 WI22 失败重试计数与退避策略

> Plan Status: completed
> Draft Review: 两轮独立子代理对抗审查通过（R1：agent_5aa6ab40，2 Blocker——默认值丢消息窗口裁定、终点路径丢消息/失效——+8 Major+5 Minor 全修；R2：agent_5b7daa1c，判定可直接执行）
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI22，deps WI16✓ WI21✓；对应 MSG-F-21 与 MSG-F-22 残面）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（3.3/3.3.1，本计划增补 3.3.2）

## Purpose

关闭 MSG-F-21（退避硬编码不可配置）与 MSG-F-22 的残面（无重试上限、毒消息空转、跨重启 attempt 语义未声明）。完成判定（roadmap 原文）：每消息 attempt 计数可查询且跨重启语义明确；消费错误退避改为可配置且有上限；毒消息到达上限后进入失败终点而不再空转；跨重启 attempt 语义在设计文档中显式声明。

## Current Baseline

- **attempt 计数（WI16 已落）**：两后端 `failureAttempts`（location → 连续失败次数，成功清零）+ `ConsumeFailure.getAttempt()` 是查询面；均为任务实例内存态。计数自增发生在 `dispatchConsumeFailure`（runTask catch 内 merge）——**计数点与 location 信息分离**（location 在内层 catch 填充）。既有不对称：Pulsar 的 ConsumeLater 轮会清 per-location 计数（`:301` 在分支链后无条件执行）而 Kafka 不会——本计划保持现状并在 3.3.2 记录。
- **退避（F-21）**：`consumeErrorBackoffMs` 构造参数存在但生产调用方传硬编码常量（Kafka `newTask` 传 `DEFAULT_CONSUME_ERROR_BACKOFF_MS=1000`；Pulsar `doSubscribe` 传 4 参构造默认 1000）；固定值无升级、无上限配置。
- **无重试上限（F-22 残面）**：毒消息（handler 持续抛错）在两后端无限重投空转——Kafka seek 回同 offset、Pulsar nack 重投；唯一终止手段是 handler 返回 `TerminateConsume`（主动）。`maxConsumeAttempts` 概念不存在。
- **跨重启语义未声明**：design 3.3 只写了「attempt 为同 location 连续失败计数（成功即清零；跨重启语义归 WI22）」。
- **Pulsar 跨重启原生能力（javap 实测 2.8.0）**：`Message.getRedeliveryCount()` 存在——broker 侧重投计数，随消息传递、跨重启/跨消费者持续；Kafka 无对应概念。
- **WI21 substrate**：Kafka 应用级 DLQ 重发布（`publishToDeadLetterIfConfigured`）+ Pulsar 原生 DeadLetterPolicy（ack 触发分流）——本计划的失败终点去向。
- **配置注入通道先例（WI21）**：task 经 `service` 引用 + 包私有访问器读模块级 config（`resolveDeadLetterTopic` 形态）。

## Goals

- **默认值裁定（审查 B1，产品行为显式化）**：`maxConsumeAttempts` 默认 **10**（重试次数口径，终点在第 11 次失败触发，见 M6 比较符裁定）。取舍如实声明（design 3.3.2 + config javadoc 双写）：有限预算内重投保护暂态故障；**超出预算（默认升级退避下约数分钟）的持续故障期间未提交消息将被终点丢弃（DLQ off 时）**——这是有界终点与无限重投的固有权衡，对齐 spring-kafka `DefaultErrorHandler`「有界重试后跳过」先例；零丢失诉求的部署设 `maxConsumeAttempts=0`（unlimited，现行为）或配置 DLQ。
- **attempt 计数点重构（可查询语义不变）**：计数自增从 `dispatchConsumeFailure` 移到已知 location 的失败点（Kafka 单条内层 catch / 批量外层 catch 读 progress；Pulsar 单条 catch），`dispatchConsumeFailure` 改为读取（peek）不再自增。**批量归因收紧（审查 m3）**：progress 初始 -1（`onMessageBatch` 本身抛/返回 null 时为框架级失败、不 bump），仅响应处理期失败按 progress 记录 bump；rewind 起点对 -1 回落 0。查询面保持 `ConsumeFailure.getAttempt()`。
- **毒消息失败终点（上限驱动，审查 B2 控制流细化）**：
  - **Kafka 单条**：失败时 bump(location)；`attempt > maxConsumeAttempts` 命中 → ① 显式派发 `onConsumeFailure`（attempt=封顶值，与批量/rethrow 路径一致——审查 M1 裁定）→ ② `publishToDeadLetterIfConfigured`（DLQ off 时 WARN 丢弃留证）——**发布失败回退 seek+rethrow 走正常失败路径**（防 position 已过导致的静默丢失，B2-1）→ ③ 清 lastFailure 三字段 + 清零该 location 计数 → 不 seek 不 rethrow，继续批内后续记录，终点消息随本轮 commit。
  - **Kafka 批量**：progress 处失败且命中 → 终点记录先 `boundaries.merge(offset+1)` **再 `commitConverged`**（catch 路径显式提交终点与头部，防 rebalance/重启后终点记录复活——B2-2）→ `publishToDeadLetterIfConfigured`（**发布失败回退 `rewindFrom(recordList, progress)` 正常失败路径**，防尾部丢失，B2-3）→ WARN 留证 + 清字段清计数 → `rewindFrom(recordList, progress+1)` → rethrow（退避由 run loop 承担）。
  - **Pulsar 单条**：cap 判定 `attempt > maxConsumeAttempts` 且 attempt 取 **max(本地 bump, message.getRedeliveryCount())**；命中 → ① 派发 onConsumeFailure（同 M1）→ ② `syncGet(acknowledgeAsync)` **永久确认**（不再 nack）——**ack 失败 try/catch 仅 WARN 不抛**（broker 侧未 ack 自有重投兜底，M5）→ ③ WARN 留证 + 清字段清计数 → 不 rethrow，loop 继续。不自行重发布 DLQ（原生 DeadLetterPolicy 在 ack 时按 `redeliveryCount >= maxRedeliverCount` 分流——`>` 口径使本地 cap 与原生 policy 等值配置时精确衔接，无双写，M6）。
  - **Pulsar 批量**：失败为整批 nack、无逐条 location 归因 → 不设应用级终点（原生 policy 覆盖 Shared 场景）；3.3.2 显式声明该边界（非静默）。
- **比较符与命名（M6 裁定）**：`maxConsumeAttempts` 语义 = 重试次数上限，比较符 `>`（第 cap+1 次失败触发终点）；config javadoc、测试②、3.3.2 三处一致；与 Pulsar 原生 `maxRedeliverCount`（入表条件 `redeliveryCount >= max`）等值配置时精确衔接。
- **退避可配置且有上限（F-21，M3/m1 裁定）**：两后端 config 新增 `consumeErrorBackoffMs`（默认 1000）与 `consumeErrorBackoffMaxMs`（默认 30000）。run loop 退避 = `min(base × 2^n, max)`，**n 从 0 起（首错睡 base，兼容现状默认）**，n = 连续错误轮数；**清零条件 = 本轮成功派发至少一条消息**（空 poll/receive 超时轮中性不清零——否则 Pulsar nack 重投间隔下的空轮会把升级打回 base，F-21 原样复发）；ConsumeLater 轮算派发成功轮。`computeBackoffMs(base, max, n)` 纯函数两后端各留私有静态副本（两后端不依赖 nop-message-core，不加模块边），单测锚定 0→base 与封顶。`doSubscribe` 生产调用方改传 config 值。
- **注入通道（M7/M8 裁定）**：backoff base 沿构造参数（`newTask`/Pulsar 5 参构造传 config 值）；`maxConsumeAttempts`/`consumeErrorBackoffMaxMs` 经 service 包私有访问器读 config，`defaultConsumerConfig == null` 时回落模块默认常量（**非 0**，与生产行为一致）。mock service 的 int 访问器默认返回 0=off——既有失败循环测试自然保持现状（无需 stub），测试② 显式 stub、测试⑤ 显式 stub 0（可见失败无静默陷阱）。测试⑤ 断言面：`KafkaMessageService.newTask` 提权 protected，spy 捕获传参。
- **cap 作用域（m4）**：终点仅适用于消息级处理失败（有 location，含处理期 ack-topic 发送失败）；框架级失败（poll/commit，无 location）永不 bump 永不终点。
- **跨重启 attempt 语义声明（design 3.3.2，M4 限定）**：attempt=消费任务实例内同 location 连续失败计数（内存态，重启/rebalance 归零）；Pulsar cap 判定并入 broker redeliveryCount——**跨重启有效性仅 Shared/Key_Shared 成立**（2.8 非 Shared 计数恒 0，退化为本地预算，与 Kafka 同款）；Kafka 无 broker 计数，重启后毒消息重获完整本地预算（显式声明不宣称）。会话内终点记录若因批量 catch 提交后仍 crash 在 commit 前，同样受重启预算重置影响（at-least-once 残留，如实声明）。
- **无 substrate 后端义务不变**；SPI（nop-api-core）零变更。

## Non-Goals

- 不做 attempt 持久化存储（跨重启计数器落库）——Kafka 侧显式声明为本地预算；持久化属 optimization candidate。
- 不动批量路径的 arity 语义（WI25）；不做 DLQ 机制本身（WI21 已交付）。
- 不改 `TerminateConsume` 语义与 ack topic 派生（无必答回归方触发）。
- 不做退避抖动（jitter）与按 topic 粒度退避配置。

## Scope

### In Scope

- `nop-message-kafka`：config 三字段（backoff/backoffMax/maxConsumeAttempts）、`KafkaConsumeTask`（计数点重构、终点逻辑、computeBackoffMs、doSubscribe 传参）、测试。
- `nop-message-pulsar`：同上 + `getRedeliveryCount()` 并入 cap 判定、测试。
- `ai-dev/design/nop-message/backend-semantics-design.md` 3.3.2、日志、roadmap WI22 状态行。

### Out Of Scope

- WI23/WI25-WI35；SPI 变更；Local 等非 broker 后端；attempt 持久化。

## Execution Plan

### Phase 1 - attempt 重构、失败终点与退避配置

Status: completed
Targets: `nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`、design 3.3.2
Audit Fixes: 审计有条件 PASS 的 2 Major 证据缺口已修复——批量发布失败回退测试改为 failedFuture 响应形态（原 handler 直抛 progress=-1 分支不可达，且实测纠出 commit-first 顺序缺陷：已改为 publish 成功先行、commit 后置）；补单条终点 DLQ sendAsync 断言与发布失败回退测试；Pulsar 补 ack 失败 WARN 不抛断言与 batchConsume 边界 javadoc；plan 勘误 attempt=2→3。

- Item Types: `Feature`（上限/退避为 F-21/F-22 的新增机制面）；测试归类 `Proof`

- [x] 两后端 config 增 `consumeErrorBackoffMs`/`consumeErrorBackoffMaxMs`/`maxConsumeAttempts`（默认 1000/30000/10，javadoc 双写默认值取舍——终点第 cap+1 次失败触发、DLQ off 丢弃面、0=unlimited）
- [x] `KafkaConsumeTask`：attempt 计数点内移（bump/peek 分离 + progress=-1 归因收紧）、单条终点（派发回调→DLQ 复用/丢弃，发布失败回退 seek+rethrow→清字段清计数→不 seek 继续）、批量终点（merge 终点 boundary→commitConverged→发布失败回退 rewind(progress)→rewind(progress+1)→rethrow）、`computeBackoffMs`（n 从 0 起）、runTask 升级退避（派发成功轮清零、空轮中性）
- [x] `PulsarConsumeTask`：单条终点（max(本地, redeliveryCount) 判定、派发回调、ack 失败 WARN 不抛、清字段清计数、不 rethrow）、runTask 升级退避；批量不设终点（javadoc + 3.3.2 声明）
- [x] 两后端 `doSubscribe`/`newTask` 改传 config 值（backoff base 构造参数；maxConsumeAttempts/backoffMax 经 service 访问器 + null 回落常量；newTask 提权 protected）
- [x] 测试① computeBackoffMs：0 次=base、升级序列、上限封顶
- [x] 测试② Kafka 单条 cap：maxConsumeAttempts=2（stub 访问器），同记录连续 3 轮失败 → 前两轮 seek 重投、第 3 轮终点（DLQ stub 时 sendAsync 调用；未配置时不 seek、随本轮 commit、继续）+ 终点轮 onConsumeFailure 派发 attempt=3（实际失败序次——`>` 口径勘误，审计 Minor-1）+ 计数清零（第 4 轮失败 attempt=1）
- [x] 测试③ Kafka 批量 cap：progress 处记录终点 → commitConverged 含终点 boundary、rewind 到 progress+1（尾部仍重投）、DLQ 发布失败回退 rewind(progress)
- [x] 测试④ Pulsar 单条 cap：本地计数与 redeliveryCount 取大判定；命中后 acknowledgeAsync 替代 negativeAcknowledge、不 rethrow、ack 失败 WARN 不抛
- [x] 测试⑤ maxConsumeAttempts=0（off，stub）→ 无限重投现状；config 接线断言（spy newTask 捕获 backoff base；访问器 null-config 回落常量）
- [x] 测试⑥ 既有全量回归（WI16 attempt 递增测试语义保持——bump/peek 重构后 attempt 序列断言不变，全绿）
- [x] design 3.3.2 落档（attempt 查询面与计数点、默认值取舍与丢失面、`>` 比较符与原生 policy 衔接、跨重启语义含 Shared/Key_Shared 限定、终点回调派发一致、批量边界与归因收紧、cap 作用域、ConsumeLater 清零不对称现状）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am -T 1C` 退出 0 全绿（BUILD SUCCESS：kafka 74/0F、pulsar 45/0F，_tmp/wi22-regression.log）
- [x] **新功能必有测试**：①-⑥ 全部存在且通过（②③④ 是终点的直接证明；① 是退避封顶的直接证明；⑤ 是 off 开关与配置接线的直接证明）
- [x] **无静默跳过**：终点路径显式 WARN 留证（消息被永久确认/跳过必须留证）；unlimited=0 显式语义；Pulsar 批量不设终点在 design 显式声明边界，非静默
- [x] **端到端验证（机制面）**：Kafka 毒消息从首轮失败 → seek 重投 → 计数升级 → cap 命中 → DLQ/丢弃 → commit 的完整序列有测试覆盖（②）
- [x] **接线验证**：doSubscribe 生产调用方真实使用 config 值（⑤ 断言）；终点真实复用 WI21 `publishToDeadLetterIfConfigured`（② DLQ stub 断言）
- [x] Hard constraint：SPI 零变更；无第三重试机制（终点是 ack/跳过 + 既有 DLQ 去向，退避是错误节流非重试策略）；错误处理两层策略（无裸 RuntimeException）
- [x] Owner-doc：design 3.3.2 已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-21（退避可配置有上限）与 MSG-F-22 残面（上限终点、跨重启语义声明）已修复且有测试证明（①-⑥）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_214b2cb4，有条件 PASS——2 Major 证据缺口已修复并复验，见 Closure Audit Evidence）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0（kafka 76/0F、pulsar 45/0F）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个 nop-message 模块逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/22-wi22-retry-cap-backoff.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- attempt 持久化（跨重启 Kafka 计数器落库）（optimization candidate：Kafka 无 broker 计数，落库成本 > 收益，无实证需求）。
- 退避 jitter 与 per-topic 退避粒度（optimization candidate）。

## Closure

Status Note: WI22 收口：F-21（退避 base/max 可配置 + 指数升级 n 从 0 起 + 派发成功轮清零）与 F-22 残面（maxConsumeAttempts 终点——Kafka 单条/批量与 Pulsar 单条、`>` 口径与 Pulsar 原生 DLQ 分流精确衔接、跨重启语义 Shared/Key_Shared 限定声明）全部落地并有测试证明。审计 2 Major 证据缺口修复过程中实测纠出批量终点 commit-first 顺序缺陷（发布失败时 boundary 已持久化使回退失效）——已改为 publish 成功先行、commit 后置并有回退测试钉定。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_214b2cb4-2628-4239-8f02-c5f95333b5c4（fresh session，亲跑全部验证命令）
- Evidence:
  - 9 条 Exit Criteria：审计裁定 7 PASS + 2 PARTIAL（证据缺口）——修复后复验：①四模块 BUILD SUCCESS（kafka 76/0F、pulsar 45/0F）；②测试①-⑥ 全部真实覆盖（Major-1 修复：批量发布失败回退测试改 failedFuture 形态使终点分支可达；Major-2 修复：补 singleEndpointRepublishesToDeadLetterWhenConfigured + singleEndpointPublishFailureFallsBackToSeekAndRethrow）；③无静默跳过（+Minor-2 batchConsume 边界 javadoc）；④端到端序列；⑤接线（newTask=777 断言）；⑥SPI 零变更（git diff 实证）；⑦design 3.3.2 一致（+Info-1 批量终点双派发说明）；⑧日志；⑨doc-links 0。
  - 审计 Minor-1（plan 文字 attempt=2→3）/Minor-3（ack 失败断言）已修；Info-2（回归日志截断）由审计独立复跑覆盖。
  - Anti-Hollow：runTask→computeBackoffMs 真实连线；终点复用 WI21 publishToDeadLetterIfConfigured（非重写）；访问器真实读 config（回落断言）。
  - 门禁：scan-hollow 0；四模块 check-silent-wrong-result 0；check-doc-links 0 errors；check-plan-checklist --strict 0（收口后复验）。
  - Deferred 分类检查：无 in-scope live defect 被降级（attempt 持久化/jitter 为 optimization candidate）。

Follow-up:

- attempt 持久化（Kafka 跨重启预算落库）与退避 jitter/per-topic 粒度（optimization candidates）
- 批量终点轮的双次 onConsumeFailure 派发已记 design 3.3.2（单条无此现象）
- 无 remaining plan-owned work
