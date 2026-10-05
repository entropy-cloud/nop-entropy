# 25 WI25 批量消费契约与 arity 对齐

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Draft Review: 一轮独立子代理对抗审查（agent_b15fc0b2）发现 1 Blocker——Pulsar policy maxNumMessages-only 公式丢失客户端默认 timeout/maxNumBytes，批量完成延迟从 ≤100ms 变为攒满 count 才返回（低吞吐无界延迟回归）→ 改保底默认方案；1 Major——arity WARN 放置规则缺失（早退分支豁免 + PartialCommit 保持 return 非 break + post-loop 计算）；7 Minor 全部纳入执行项
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI25，deps WI4✓；对应 MSG-F-16 / MSG-F-17；D5(b-prime)）
> Related: `ai-dev/design/nop-message/backend-semantics-design.md`（本计划增补 3.10 批量契约节）

## Purpose

裁定 `onMessageBatch` 的成败语义并消除 arity 不匹配的未定义行为（F-16）、把 `batchReceiveCount` 两后端接线（F-17）。完成判定（roadmap 原文）：按 D5 的 b-prime 裁定部分提交信号，裁定 `onMessageBatch` 是全批成败还是按记录；两后端响应列表与记录列表 arity 不匹配均有确定行为；`batchReceiveCount` 两后端接线；逐条回归测试。

## Current Baseline

- **F-16 在场（Pulsar）**：`batchConsume` 以 `for (response : responses) { message = it.next(); }` 迭代——responses 多于 messages → `NoSuchElementException` → 整批负确认（**含已发 reply 的记录**）；messages 多于 responses → 尾部消息**永不 ack**。与 Kafka 方向相反的第二个 arity bug。
- **Kafka 侧已确定但无告警**：`batchConsume` 循环 `i < responses.size() && i < recordList.size()`——extra responses 静默忽略；尾部记录经 `rewindUndispatched` 重投（WI11 语义）。缺 arity 失配告警。
- **F-17 在场**：Kafka `batchReceiveCount` 仅作 `allowBatchConsume()` 布尔门，批量大小由 `max.poll.records`（全局默认 500）决定；Pulsar 用无参 `batchReceive()`（客户端默认 policy），`batchReceiveCount`/`batchReceiveTimeout` 均未接线。
- **D5(b-prime) 已落地（WI6）**：`PartialCommit` = 前缀提交信号，两后端语义已实现并有测试。本计划的「裁定」= 把「onMessageBatch 是**按记录返回响应**（非全批成败）」落成 design 显式声明 + arity 确定行为补全。
- **API 实测（javap 2.8.0）**：`BatchReceivePolicy.builder().maxNumMessages(int)[.timeout(int, TimeUnit)].build()`；`ConsumerBuilder.batchReceivePolicy(...)` 存在。Kafka 侧接线点 = `ConsumerConfig.MAX_POLL_RECORDS_CONFIG`。
- **必答回归方**：批量语义触及 onMessage 返回值协议 → nop-rpc-core / nop-graphql-message；nop-stream-runtime 数据面。

## Goals

- **D5 裁定落档（design 3.10）**：`onMessageBatch` 为**按记录语义**——返回 List 与记录一一对应（第 i 个响应裁定第 i 条记录）；`PartialCommit` 为前缀提交信号；**非全批成败**（handler 级异常走既有失败路径：Kafka rewind / Pulsar 整批 nack）。
- **F-16 修复（Pulsar）**：成对迭代 `n = min(responses.size(), messages.size())`。**WARN 放置规则（审查 M-2 裁定）**：arity WARN 一律在**循环正常完成后**计算（extra = responses.size()-n；tail = iterator 余量）；`PartialCommit` 早退分支保持自带边界+尾部 nack 并 **`return`**（不得改 break 共享 post-loop 尾部处理）——早退是显式契约信号，**豁免 arity WARN**（否则既有 4:3 PartialCommit 测试误报且行为不可区分）：
  - responses 多于 messages → 循环完成后多余响应 **WARN 忽略**（`nop.message.pulsar.batch-arity-extra-responses`，逐次——handler 契约破坏应显式暴露；extras 中的信号类型不扫描——响应 i 裁定记录 i，无记录可施加即无语义）；
  - messages 多于 responses → 循环完成后尾部消息逐条 **negativeAcknowledge**（at-least-once 重投）+ WARN（`batch-arity-unhandled-tail`）；
  - `return dispatched` 统一（空 responses 轮不再返回 true——WI22 consecutiveErrors 不被空轮复位，微变更成文）。
- **Kafka 侧对齐**：arity WARN 同为 post-loop 计算（两个既有 `return`——PartialCommit/ConsumeLater 早退——天然豁免）：extra responses `nop.message.kafka.batch-arity-extra-responses` WARN（忽略行为保持）；尾部经 rewindUndispatched 重投 + `batch-arity-unhandled-tail` WARN——两后端行为对称且均有告警。
- **F-17 接线**：
  - Kafka：`options.batchReceiveCount > 0` 时 `MAX_POLL_RECORDS_CONFIG = batchReceiveCount`（per-subscribe 选项覆盖全局 `defaultConsumerConfig.maxPollRecords`——加性覆盖，优先级链 extraProps < managed keys < per-subscribe option；`buildConsumerProps` 加 `(String, MessageSubscribeOptions)` 重载，旧签名委托 null options 保既有测试）。
  - Pulsar（审查 B-1 裁定，方案 1 最小）：`batchReceiveCount > 0` 时 `builder.batchReceivePolicy(BatchReceivePolicy.builder().maxNumMessages(count).maxNumBytes(BatchReceivePolicy.DEFAULT_POLICY.getMaxNumBytes()).timeout(100, TimeUnit.MILLISECONDS).build())`——**保底客户端默认 timeout/bytes**：maxNumMessages-only 的 gate 语义会让低吞吐 topic 的批量完成延迟从 ≤100ms 变为无界（攒满 count 才返回），属 F-17 自引入的延迟回归。与 Kafka 的纯上限口径不对称性在 design 3.10 登记（Kafka=cap / Pulsar=gate+100ms 兜底）。`batchReceiveTimeout` 不接线（WI17 裁定保持，差异登记）。
- **测试①-⑤**（见 Exit Criteria）。

## Non-Goals

- 不改 PartialCommit/其他返回值语义（WI6 已收口）；不做 Pulsar 逐条失败终点（WI22 边界保持）；`batchReceiveTimeout` 的 Pulsar policy 接线不做（WI17 裁定延伸，登记 Follow-up）。

## Scope

### In Scope

- `nop-message-pulsar`：batchConsume 成对迭代 + WARN、doSubscribe batchReceivePolicy 接线、测试。
- `nop-message-kafka`：buildConsumerProps MAX_POLL_RECORDS 覆盖、batchConsume arity WARN、测试。
- design 3.10、日志、roadmap WI25 状态行。

### Out Of Scope

- WI26-WI35；SPI 变更；`batchReceiveTimeout` Pulsar 接线。

## Execution Plan

### Phase 1 - arity 确定行为与 batchReceiveCount 接线

Status: completed
Targets: `nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`、design 3.10

- Item Types: `Fix`（F-16/F-17 为已确认缺陷）；「按记录语义」落档为 `Decision`

- [x] Pulsar `batchConsume` 成对迭代（F-16 修复：extra responses WARN 忽略 / tail messages nack + WARN；PartialCommit 保持）
- [x] Kafka `batchConsume` arity 失配 WARN（extra responses 忽略已有确定行为）；`buildConsumerProps` MAX_POLL_RECORDS per-subscribe 覆盖
- [x] Pulsar `doSubscribe` `batchReceivePolicy(maxNumMessages + 客户端默认保底)` 接线
- [x] 测试① Pulsar responses>messages：无 NoSuchElementException、前 n 条按响应处理、多余响应 WARN 忽略
- [x] 测试② Pulsar messages>responses：已响应记录 ack、尾部 nack 重投
- [x] 测试③ Kafka extra responses 忽略 + 尾部 rewind 保持（WARN 不入断言，以行为断言 + 审计代码审阅为证——无日志捕获设施）；`buildConsumerProps(String, options)` 重载 MAX_POLL_RECORDS 覆盖断言（既有两测试直呼旧签名保绿）；预期新 WARN 的既有测试：batchCommitNeverCrossesUndispatchedRecords（2:1）
- [x] 测试④ Pulsar `batchReceivePolicy` 接线断言（builder mock 非链式 capture：maxNumMessages + maxNumBytes/timeout 保底值）；未配置不设 policy；options.batchReceiveCount>0 时 consume task 走 batch 路径需 stub batchReceiveAsync 返回空批（防 mock 错误循环噪音）
- [x] 测试⑤ 既有回归（Pulsar 4:3 PartialCommit 测试行为与四条 verify 断言不回退且无 arity WARN 误报；Kafka 批量测试除预期 WARN 外不回退）+ design 3.10 落档（按记录语义裁定含 Pulsar ConsumeLater 非早退的既有差异登记、arity 规则与豁免、batchReceiveCount 两后端口径含 cap/gate 不对称、11 主源码 onMessageBatch 实现评估结论：仅 SPI 默认实现恒等长零影响、batchReceiveTimeout Pulsar 不接线的差异登记）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium,nop-network/nop-rpc/nop-rpc-core,nop-service-framework/nop-graphql/nop-graphql-message,nop-stream/nop-stream-runtime -am` 退出 0 全绿（BUILD SUCCESS：kafka 80/0F、pulsar 51/0F，_tmp/wi25-regression.log——审计复跑证实，81 系笔误已勘误）
- [x] **新功能必有测试**：①-⑤ 全部存在且通过——①② 是 F-16 两个方向的直接证明；③ 是 Kafka 对称行为证明；④ 是 F-17 接线证明
- [x] **无静默跳过**：arity 失配两侧均显式 WARN（非静默截断/忽略）；Pulsar tail nack 是显式 at-least-once 动作
- [x] **接线验证**：④ 的 builder capture + ③ 的 consumer props 断言为运行时证据
- [x] Hard constraint：SPI 零变更；不引入 AckMode（PartialCommit 既有语义保持）；broker 切换机制不变
- [x] Owner-doc：design 3.10 已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-16/MSG-F-17 已修复且有测试证明（①-⑤）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_2a521254，PASS）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个 nop-message 模块（core/kafka/pulsar/debezium）逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/25-wi25-batch-contract.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- `batchReceiveTimeout` 的 Pulsar BatchReceivePolicy.timeout 接线（optimization candidate：WI17 F-11 已声明 Kafka 口径，Pulsar 侧无实证需求）。

## Closure

Status Note: WI25 收口：F-16（Pulsar 成对迭代消除 NoSuchElementException/尾部永挂，两方向 arity 失配确定行为 + post-loop WARN + 早退豁免）、F-17（Kafka max.poll.records per-subscribe 覆盖 + Pulsar BatchReceivePolicy gate+100ms 兜底——审查 B-1 保底裁定）落地并有判别力测试。D5 按记录语义落 design 3.10（含 cap/gate 不对称、ConsumeLater 非早退差异、11 实现零波及评估）。SPI 零变更。独立 closure audit PASS。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_2a521254-054e-4b91-9ccd-ba669102a4fe（fresh session，亲跑全部验证命令）
- Evidence:
  - 8 条 Exit Criteria 全 PASS：合并回归 BUILD SUCCESS（stream-runtime 1245/0F）；kafka 80/0F + pulsar 51/0F（审计独立复跑；EC1 笔误 81 已勘误）。
  - B-1 复核 PASS：policy 三值断言（maxNumMessages=25/maxNumBytes=DEFAULT/timeout=100）；M-2 复核 PASS：post-loop 计算、PartialCommit 保持 return、既有 4:3 测试 diff 纯新增零删行且四条 verify 原样通过（早退不可达 post-loop 无误报）。
  - F-16 测试判别力：审计静态推演旧代码下两测试必失败（ NoSuchElementException 路径 / tail 永挂路径）——非恒真。
  - Anti-Hollow：grep it.next() 仅 3 处（i<n 有界 1 处 + hasNext 守护 2 处）——无界拉取零残留；接线 props/builder capture 真断言。
  - Hard constraint：nop-api-core diff 为空（SPI 零变更）；AckMode 零命中；触碰面 8 java 文件全在 nop-message。
  - 门禁：scan-hollow 0；silent-wrong-result ×4 = 0；doc-links 0 errors；checklist 收口后复验 0。
  - 审计 Minor 项处置：L62 漏勾已勾、EC1 计数已勘误、roadmap 翻 done。
  - Deferred 分类检查：无 in-scope live defect 被降级（WARN 无自动化断言为已登记验证盲区；测试命名瑕疵为 Info）。

Follow-up:

- batchReceiveTimeout 的 Pulsar policy timeout 接线（optimization candidate）
- Kafka arity WARN 无自动化断言（已登记验证盲区；建议后续引入 logback ListAppender 设施）
- 无 remaining plan-owned work
