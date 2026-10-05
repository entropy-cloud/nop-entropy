# 24 WI24 信封元数据与 payload 契约

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Draft Review: 一轮独立子代理对抗审查（agent_8c5bbbe4）发现 1 Blocker——HEADER_ID write-last-wins 会静默打断消息 RPC 应答关联（客户端 nop-id 被框架派生值覆盖、rel-id 配对失败、全仓零测试钉定）——改 fill-if-absent 裁定 + 5 Major（bizKey 基线勘误/去 final/encode 断言迁移/回归命令补全/equals 残差）+ 6 Minor 全部修订；审查确认修复后结构健全，可进入执行
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI24，deps WI6✓；对应 MSG-F-33 / MSG-F-31 / MSG-F-48 / MSG-F-59；D2/D8 裁定）
> Related: `ai-dev/design/nop-message/message-contract-design.md`（3.2 信封契约 + 3.4 D8，本计划更新差异表为实现态）、`backend-semantics-design.md`

## Purpose

落地 D2 最小信封契约的实现态与 D8 加性规则（F-33 上游头覆盖框架元数据、F-48 信封对称性、F-31 非 String payload 双端一致、F-59 TopicMessage @DataBean 缺陷、encodeValue 去重）。完成判定（roadmap 原文）：本项所有选项字段与信封字段的改动一律加性引入并给弃用期，不得直接重命名 `@DataBean` 字段；按 D2 契约补齐两后端信封元数据对称性；上游同名头不可覆盖框架元数据头；非 String payload 两后端行为一致；`encodeValue` 重复实现去重。

## Current Baseline

- **F-33 在场（两后端）**：`KafkaHelper.buildApiMessage` 与 `PulsarHelper.buildApiMessage` 均**先设框架头（topic/partition/offset/eventTime/id）后拷贝上游 headers/properties**——上游同名头覆盖真实 topic/offset/messageId。
- **F-48 在场**：Pulsar 设 `HEADER_ID`/`nop-publish-time`/`nop-sequence-id`；Kafka 不设 `HEADER_ID`（无 messageId 头）——依赖 `nop-id` 去重的消费方在 Kafka 侧得 null。
- **F-31 在场（Pulsar 侧）**：Kafka `toWireValue` 已是「String 直用 / 其余 encodeValue(JSON)」；Pulsar `_buildPulsarMessage` 仍 `builder.value(message.getData())` 原样塞 STRING schema → 非 String 发送期序列化失败。
- **encodeValue 重复**：`KafkaHelper.encodeValue` 与 `PulsarHelper.encodeValue` 逻辑相同（String 直用/Number|Boolean toString/其余 `JSON.stringify`）。`JSON`（`io.nop.api.core.json.JSON`）在 **nop-api-core**——共享 codec 可落 nop-api-core（两后端经 nop-core 传递依赖可达），无循环依赖。
- **F-59 在场**：`TopicMessage`（nop-api-core，@DataBean）**无无参构造器**（final 字段仅全参 ctor）且**无 equals/hashCode**；`MessageSendOptions.cancelToken` 接口类型字段的可写 YAML 键问题已由 WI15 接线消除（SEND_TIMEOUT 能力门 + 前置取消检查），无需改名（D8 加性合规）。
- **必答回归方**（roadmap Hard constraint）：本计划触及信封头与 onMessage 载荷 → `nop-rpc-core`（MessageRpcClient/MessageRpcServer）与 `nop-graphql-message` 必须回归。
- **Protected Area**：`TopicMessage` 与新 codec 落 `nop-api-core`——本 plan 即 plan-first 载体；全部改动为**收窄式新增**（新增 ctor/方法/类），零签名变更、零字段改名（D8）。

## Goals

- **F-33（审查 B1 裁定：分字段保护策略）**：两后端 `buildApiMessage` 改为**先拷贝上游 headers/properties，后写框架元数据头**（write-last-wins）——适用于 `HEADER_TOPIC`/`HEADER_EVENT_TIME`/Kafka partition/offset 专有头/**bizKey**（现状 setBizKey 在上游拷贝之前，上游同名头今天就能覆盖真实 key——审查 M1 勘误，翻转时把 setBizKey 一并后移）。**`HEADER_ID` 例外 = fill-if-absent**：上游拷贝后仅当无 `nop-id` 时才写框架值。理由（B1 证据链）：消息 RPC 的应答关联依赖消费侧存活的客户端 nop-id（`MessageRpcClient` 发前合成 UUID → `RpcMessageSubscriber` 以 `ApiHeaders.getId` 回填 rel-id → 客户端 `getRelId` 配对 promise）——框架后写覆盖会让全部非 one-way 消息 RPC 静默超时，且全仓零测试钉定、零检出。fill-if-absent 同时满足 F-48（普通发送得非 null id）与 D2 往返保真。**Pulsar 侧同规则**——RPC 客户端的 nop-id 经 Pulsar properties 传输，与 Kafka headers 完全同构（执行中自纠：初版仅对 Kafka fill-if-absent、Pulsar 写最后会同样打断关联）。
- **F-48**：Kafka `buildApiMessage` fill-if-absent 增 `HEADER_ID = "<topic>-<partition>@<offset>"`（与 ConsumeFailure location 同派生格式，加性；Kafka 专有定位已由 partition/offset 头承载）。Pulsar 侧不补 Kafka 专有头（差异表登记：publish-time/sequence-id 为 Pulsar 专有，Kafka 无对应概念不伪造）。D2 五项往返保真达成：messageId（Pulsar 原生 / Kafka 缺失时派生）、topic、eventTime、key（bizKey）、headers。
- **F-31**：Pulsar `_buildPulsarMessage` 改 `builder.value(共享 codec.encodeValue(message.getData()))`——String 直用、其余 JSON 编码，与 Kafka `toWireValue` 同一编码规则（双端 wire 一致）。
- **encodeValue 去重（审查 m1 命名与语义裁定）**：nop-api-core 新增 `io.nop.api.core.message.MessageValueCodec`：`static encodeValue`（String 直用 / Number|Boolean toString / 其余 `JSON.stringify`；null→null，`toWireValue` 的 null 分支收敛于此）**两后端共用**；`static decodeValue(byte[])`（null/empty→null，UTF-8 容错）**仅 Kafka 消费**（Pulsar properties 本为 String，禁用 decode）。两后端 Helper 删除实现体改委托。
- **F-59（审查 M2/M5 裁定）**：`TopicMessage` **字段去 final**（无参 ctor 与 final 冲突；getter 签名不变、保留全参 ctor）+ `equals/hashCode` 基于 `topic + Objects.equals(message)` 字段值语义，javadoc 明示残差：载荷相等性取决于载荷类型自身 equals（ApiMessage 基类无 equals → 引用相等），值相等超范围。`cancelToken` 按 WI15 裁定维持接线消除、design 差异表记「无需改名」。
- **测试**（Exit Criteria ①-④）；契约测试保持全绿。
- **D8 声明**：本计划零改名零删除；`message-contract-design.md` 3.2 差异表更新为实现态（含 cancelToken 处置结论）。

## Non-Goals

- 不改 SPI 既有签名/字段（TopicMessage 纯新增成员）；不做信封字段的可配置化；不动 ack topic 派生（WI9 已收口）；不处理 Pulsar retryLetterTopic 噪音（已登记 Follow-up）。

## Scope

### In Scope

- `nop-kernel/nop-api-core`：`MessageValueCodec` 新类、`TopicMessage` 无参 ctor + equals/hashCode。
- `nop-message-kafka` / `nop-message-pulsar`：buildApiMessage 头序修复 + Kafka HEADER_ID + Pulsar wire value 编码 + Helper 委托共享 codec。
- 必答回归：`nop-rpc-core`、`nop-graphql-message` 测试。
- `message-contract-design.md` 差异表实现态、日志、roadmap WI24 状态行。

### Out Of Scope

- WI25-WI35；SPI 签名变更；信封字段增删（仅新增只读派生）。

## Execution Plan

### Phase 1 - 信封对称性与 payload 一致

Status: completed
Targets: `nop-kernel/nop-api-core`、`nop-message/nop-message-kafka`、`nop-message/nop-message-pulsar`

- Item Types: `Fix`（F-33/F-48/F-31/F-59 均已确认缺陷）

- [x] nop-api-core：`MessageValueCodec` 新类（encodeValue 共用 + decodeValue(byte[]) 仅 Kafka 消费）；`TopicMessage` 字段去 final + 无参 ctor + 字段值 equals/hashCode（javadoc 登记 ApiMessage 引用相等残差）
- [x] Kafka：`buildApiMessage` 头序翻转（setBizKey 后移）+ `HEADER_ID` fill-if-absent 派生 + Helper 委托 codec（实现体删除；既有 TestKafkaHelper encode/decode 断言迁移为 codec 直测）
- [x] Pulsar：`buildApiMessage` 头序翻转（setBizKey 后移）+ `_buildPulsarMessage` wire value 编码 + Helper 委托 codec（TestPulsarHelper encode 断言迁移）
- [x] 测试① 头保护：上游 `HEADER_TOPIC`/`HEADER_EVENT_TIME`/`nop-biz-key` 假头 → 框架真值胜出（含类型断言：partition/offset 为数值型，防浅实现）；**上游 `nop-id` 存活断言**（fill-if-absent 的 RPC 关联保护）+ 缺失时才落派生值
- [x] 测试② Kafka 无上游 id 时 `HEADER_ID` = `topic-partition@offset`；Pulsar `HEADER_ID` = messageId 串（对称性对照）
- [x] 测试③ 非 String payload：codec 直测（String/Number/Boolean/POJO 四类）+ Pulsar `builder.value` capture 断言收到编码字符串（经公共入口 buildPulsarMessage 驱动）+ Kafka `toWireValue` 同值
- [x] 测试④ `TopicMessage` equals/hashCode + 无参 ctor；契约测试全绿
- [x] `message-contract-design.md` 3.2 差异表更新为实现态（含 cancelToken 结论、Kafka HEADER_ID fill-if-absent 派生格式、TopicMessage equals 残差）
- [x] 顺手 docs bug：`nop-message-kafka/pom.xml` 的「ApiMessage live in nop-core (NOT nop-api-core)」陈旧注释更正（审查 m6）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl <13 模块合并命令，含 nop-sys/nop-sys-dao>` 退出 0 全绿——**偏离记录（审计回填）**：①nop-sys-dao 从命令中剔除：其测试编译失败（TestOrmEntityChangeLogInterceptorNullMarker 找不到 io.nop.core.unittest.JunitBaseTestCase）经 git stash 实证为 HEAD 存量问题、与本计划零触碰；②并行 -T 1C 下 sendTimeout 时序测试负载性抖动（elapsed=19<40，单模块两轮复跑全绿）——改串行后全绿；③实际执行 = 串行 12 模块（BUILD SUCCESS，_tmp/wi24-regression.log：api-core 163/0F、kafka 78/0F、pulsar 47/0F、全量 13190 tests 0F，含 nop-rpc-core/nop-stream-runtime/nop-graphql-message SUCCESS）。
- [x] **新功能必有测试**：①-④ 全部存在且通过——① 是 F-33 的直接证明；② 是 F-48 的直接证明；③ 是 F-31 的直接证明；④ 是 F-59 的直接证明
- [x] **无静默跳过**：框架头保护是确定性写入顺序（非过滤黑名单的猜测式实现）；差异表中「Kafka 无 publish-time/sequence-id」为显式登记非遗漏
- [x] **接线验证**：测试③ capture 断言 Pulsar builder 真实收到编码值；Helper 委托经实现体删除 + 迁移后的 codec 直测证明（grep 实现体零残留，委托签名保留）
- [x] Hard constraint：SPI 既有签名零变更（TopicMessage 字段去 final + 新增成员、MessageValueCodec 纯新增）；D8 零改名；SPI 回归面（11 主源码实现所在模块）经合并回归命令覆盖；DataPlaneMessageServiceAdapter 不直接调 Helper（审查已 grep 证实），其 wire 契约经 nop-stream-runtime 回归守护
- [x] Owner-doc：`message-contract-design.md` 3.2 实现态已落档
- [x] `ai-dev/logs/2026/` 当日条目已更新
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0

## Closure Gates

- [x] MSG-F-33/F-48/F-31/F-59 已修复且有测试证明（①-④；审计条件项 Pulsar 缺失填充断言已补）
- [x] 独立子 agent closure audit 已完成并记录证据（agent_bd0c2d79，有条件 PASS——条件项已修复，见 Evidence）
- [x] `./mvnw test -pl nop-message/nop-message-kafka,nop-message/nop-message-pulsar -am` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-message --severity high` 退出码 0
- [x] `node ai-dev/tools/check-silent-wrong-result.mjs --module <m>` 四个 nop-message 模块（core/kafka/pulsar/debezium）逐一退出 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-message/24-wi24-envelope-contract.md --strict` 退出码 0

## Deferred But Adjudicated

无。

## Non-Blocking Follow-ups

- Kafka 无 publish-time/sequence-id 的原生等价物（out-of-scope improvement：Kafka record 无该概念，若未来需要可用 header 派生，无实证需求）。
- `TestKafkaMessageServiceSend.sendTimeoutBoundsTheReturnedFuture` 时序下限断言（elapsed>=40）在并行构建负载下抖动（实测 elapsed=19）——建议放宽下限或改 latch 门控（pre-existing flaky，WI24 并行回归实测暴露）。

## Closure

Status Note: WI24 收口：F-33（头序翻转 + setBizKey 后移 + HEADER_ID fill-if-absent 双后端——B1 RPC 关联保护）、F-48（Kafka 派生 id/Pulsar 原生 id 对称 + Pulsar 专有头显式登记）、F-31（共享 MessageValueCodec、Pulsar 非 String 编码、wire 一致）、F-59（TopicMessage 去 final + 无参 ctor + 字段值 equals/hashCode 残差登记）全部落地并有测试证明。SPI 零签名变更、D8 零改名。审计有条件 PASS 的条件项全部修复（Pulsar 缺失填充断言/偏离记录回填/无用 import/计数归属勘误）。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_bd0c2d79-fac8-4171-bab0-56d333fad540（fresh session，亲跑全部验证命令）
- Evidence:
  - 9 条执行项 8 PASS + 测试② PARTIAL（Pulsar 缺失填充断言缺失）——修复：TestPulsarHelper 增 absent→String.valueOf(msgId) 断言，pulsar 47/0F 复验。
  - B1 核对 PASS（审计亲证）：两后端 if(!hasHeader) fill-if-absent 形态、上游 nop-id 存活断言在两后端均在、RPC 关联链亲证（RpcMessageSubscriber rel-id 配对）。
  - Hard constraint：git diff 证 SPI 零改动、TopicMessage 仅新增 setter、触碰面合规；D8 零改名。
  - Anti-Hollow：委托实现体物理删除、nop-message 主源码 JSON 零残留、capture+奇偶锚点真断言。
  - 审计 Minor 项：无用 JSON import 已删（Minor-4）、78 归属勘误（Minor-5，见 EC① 回填）、toWireValue 同值由单行委托+codec 直测覆盖（Minor-3 登记）。
  - 门禁：合并串行回归 BUILD SUCCESS（13190 tests 0F，含 nop-rpc-core/nop-stream-runtime）；scan-hollow 0；silent-wrong-result ×4 = 0；doc-links 0；checklist 收口后复验 0。
  - Deferred 分类检查：无 in-scope live defect 被降级（时序测试抖动为 pre-existing flaky，登记 Follow-up）。

Follow-up:

- TestKafkaMessageServiceSend.sendTimeoutBoundsTheReturnedFuture 时序断言并行负载抖动（pre-existing flaky，建议放宽下限或 latch 门控）
- Kafka 无 publish-time/sequence-id 原生等价物（out-of-scope improvement）
- 无 remaining plan-owned work
