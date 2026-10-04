# nop-message SPI 字段 × 后端能力矩阵（WI2）

> Status: resolved
> Date: 2026-10-04
> Scope: 13 个 `IMessageService` 命名实现 × SPI 选项字段 / 枚举 / 返回值语义 / 信封元数据
> Conclusion: as-is 处置共采集 13 实现 × 22 字段/枚举维 + 5 返回值 + 7 信封项。生产实现（9 main）中 as-is 合法三态（support / typed-reject / warn-per-call）覆盖率约 25%，其余为非法「ignore」或语义失真的伪 support。提案目标态已逐格给出（无一格 ignore），作为 WI3 D1-D10 裁定与 WI6 校验器的直接输入。两后端（Kafka/Pulsar）在 seek 维度互为镜像地失效（Kafka 伪 support、Pulsar 空 stub），信封元数据仅 Pulsar 完整。

## Context

roadmap Hard constraint：每个选项字段在每个后端必须有显式三态处置（生效 / 类型化错误 / 一次性 WARN），「声明了但从不读」与空方法体均非法。WI1 审计基线（`ai-dev/analysis/2026-10/nop-message-audit-baseline.md`）确认 58 条缺陷成立，其中大量正是 ignore 形态。本矩阵把三态处置逐格落到 13 个实现，是 D1（能力声明机制）、D2（信封契约）、D3（seek 起点）、D4（SubscriptionType 中立化）、D5/D6（返回协议与 DLQ）、D7（身份模型）、D8（选项字段兼容）、D11（事务语义）的裁定底稿，也是 WI6 `MessageOptionsValidator` 的规格输入。

事实来源：WI1 基线 + 2026-10-04 对全部 13 实现的逐字段源码核查（每格锚点）。注意 `MessageSubscribeOptions` 为 **10 字段**（roadmap 个别处按 11 计，实测 10）。

## 实现清单与语义定位

| # | 实现 | 模块 | 语义定位 | 覆盖 getAckTopic |
| --- | --- | --- | --- | --- |
| 1 | LocalMessageService | nop-message-core | 进程内同步广播总线（同 topic 全部订阅者各收一份，send 线程直接回调） | 否（默认） |
| 2 | SysDaoMessageService | nop-sys-dao | DB 事件表轮询：广播 topic 全员投递；非广播队列语义（跨 JVM DB 租约竞争） | 是（同默认实现） |
| 3 | LettucePubSubService | nop-nosql-lettuce | Redis pub/sub fire-and-forget 广播（无持久化、无 ack） | 否 |
| 4 | MqttServerMessageService | nop-vertx-mqtt-server | MQTT 桥：对匹配过滤器的全部连接逐一 publish | 否 |
| 5 | TdMessageService | nop-orm-tdengine | 纯占位 stub：send/subscribe 全部 UnsupportedOperationException | 否 |
| 6 | DBMessageService | nop-ai-agent | DB 消息表 + 单线程 poller + 原子 claim（at-least-once 跨 JVM 队列） | 否 |
| 7 | DataPlaneMessageServiceAdapter | nop-stream-runtime | 装饰器：wire codec 编解码后全量透传 delegate | 是（委托） |
| 8 | DelegateMessageService | nop-api-core | 双向委托壳（sender/subscriber 可异构） | 是（委托） |
| 9 | DelayedMessageService | nop-benchmark-stream | JMH 基准桩（parkNanos 模拟延迟，空壳订阅） | 否 |
| 10 | KafkaMessageService | nop-message-kafka | Kafka consumer group 消费组语义 | 否 |
| 11 | PulsarMessageService | nop-message-pulsar | Pulsar subscription 语义 | 否 |
| 12 | MockMessageService | test（nop-metadata-service） | 测试记录桩 | 否 |
| 13 | PollingJdbcMessageService | test（nop-stream-runtime） | 测试用 JDBC 轮询 pub/sub | 否 |

豁免说明：#5（TdMessageService）对全部字段 typed-reject，天然合规；#9/#12/#13 为 test/bench 桩，不适用生产三态门禁（不注册 validator），但其行为差异在 WI32 契约测试中登记。

## 维度 A — MessageSendOptions（sendAsync 路径）

图例：`as-is → 提案`。support=生效；typed-reject=类型化错误；warn-once=每个选项组合一次性 WARN 后按声明行为执行。行内裸行号以该实现的主类文件为准（如 Kafka 行裸行号属 KafkaMessageService.java 或 KafkaConsumeTask.java，按 WI1 基线锚点可区分）；跨文件锚点显式写文件名。

| 实现 | delay | sendTimeout | cancelToken |
| --- | --- | --- | --- |
| Local | ignore（`:160-162` 未读）→ **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| SysDao | support：delay>0 设 scheduleTime（`:443-458`）→ **support（保持）** | ignore → **warn-once** | ignore → **warn-once** |
| Lettuce | ignore（`:47-50`）→ **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| MqttServer | 透传后丢弃（`MqttConnection.java:227-243`）→ **warn-once** | 同 → **warn-once** | 同 → **warn-once** |
| Td | typed-reject（`:20-22`）→ **typed-reject（保持）** | 同 | 同 |
| DB(agent) | ignore（`:237-276`）→ **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| DataPlane | 透传（`:74-77`）→ **透传（= delegate 三态）** | 同 | 同 |
| Delegate | 转发（`DelegateMessageSender.java:22-35`）→ **转发（= delegate 三态）** | 同 | 同 |
| Delayed(bench) | ignore（用构造参数，`:160-166`）→ 桩豁免 | 桩豁免 | 桩豁免 |
| Kafka | warn-per-call + ignore（`:199-205`）→ **warn-once（F-53）** | ignore（仅 producer 级 `:112-114`）→ **support：接入发送超时（WI15）** | ignore → **support：发送前检查已取消（WI15，对齐 RpcMessageSender `:52`）** |
| Pulsar | support：deliverAfter（`PulsarHelper.java:73-77`）→ **support（保持）** | ignore（仅 producer 级 `:153`）→ **support：接入发送超时（WI15）** | ignore → **support：发送前检查（WI15）** |
| Mock(test) | 桩豁免 | 桩豁免 | 桩豁免 |
| PollingJdbc(test) | 桩豁免 | 桩豁免 | 桩豁免 |

提案依据：sendTimeout/cancelToken 全仓零消费（F-45），WI15 要求「接入或显式拒绝，不得保留既不消费也不拒绝的字段」——两 broker 接入（有超时/取消的天然实现位），其余后端 warn-once（无超时 substrate，拒绝会造成既有调用方破坏）。

## 维度 B — MessageSubscribeOptions（subscribe 路径）

| 实现 | subscribeName | transactional | transactionTimeout | batchReceiveCount | batchReceiveTimeout | concurrency |
| --- | --- | --- | --- | --- | --- | --- |
| Local | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| SysDao | support：订阅者 id（`:539-547`）→ **support（保持）** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| Lettuce | ignore → **warn-once** | 同 | 同 | 同 | 同 | 同 |
| MqttServer | ignore → **warn-once** | 同 | 同 | 同 | 同 | 同 |
| Td | typed-reject → **保持** | 同 | 同 | 同 | 同 | 同 |
| DB(agent) | ignore → **warn-once** | 同 | 同 | 同 | 同 | 同 |
| DataPlane | 合成缺省 + 透传（`:84-89`）→ **透传** | 透传 | 透传 | 透传 | 透传 | 透传 |
| Delegate | 转发 → **转发** | 同 | 同 | 同 | 同 | 同 |
| Kafka | support：=group.id，缺失 typed-reject（`:267-271,295-304`）→ **support（D7 修正遮蔽后）** | ignore（F-13）→ **typed-reject：transactional=true 抛错（D11 过渡态）** | 同 transactional | support：布尔门（`:180`）→ **support（arity 契约按 WI25）** | 伪 support：直连 poll 时长（F-11）→ **support：修正语义（WI17）** | support：N 个 consumer（`:247-262`）→ **support（保持）** |
| Pulsar | support（`:222-224`）→ **support；缺失改 typed-reject（F-29：消除随机订阅名）** | support：每消息建事务（F-52）→ **support：修粒度，或随 D11(b) 字段删除** | support：timeout=0 静默回落（F-57）→ **同上** | support：allowBatchConsume 门（`:82-84`）→ **support：接线 batchReceivePolicy（WI25）** | ignore（F-17）→ **support：batchReceivePolicy.timeoutMs（WI25）** | support（`:192-209`）→ **support：Exclusive+>1 改 typed-reject（F-32）** |
| Delayed/Mock/PollingJdbc | 桩豁免 ×6 | — | — | — | — | — |

| 实现 | subscriptionType | seekMode | seekToMessage | seekToTime |
| --- | --- | --- | --- | --- |
| Local | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| SysDao | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** | ignore → **warn-once** |
| Lettuce / MqttServer / DB(agent) | ignore → **warn-once** | 同 | 同 | 同 |
| Td | typed-reject → **保持** | 同 | 同 | 同 |
| DataPlane / Delegate | 透传/转发 | 同 | 同 | 同 |
| Delayed / Mock / PollingJdbc | 桩豁免 | 桩豁免 | 桩豁免 | 桩豁免 |
| Kafka | warn-per-call（`:240-245`）→ **warn-once（F-53；组语义无 Pulsar 式四型）** | 伪 support：assignment 空集 no-op（F-01）→ **support：rebalance listener 逐次重解析（D3/WI7）** | typed-reject（`:115-118`）→ **typed-reject（保持）** | 伪 support（同 F-01）→ **support（WI7 修复后）** |
| Pulsar | support：四值映射（`:225-227,254-266`）→ **support（保持）** | ignore：空 TODO（F-03）→ **support：seek() 映射（D3/WI8）** | ignore → **typed-reject（无 message-id 寻址消费语义）** | ignore → **support：seek(timestamp)（WI8）** |

## 维度 C — SeekMode 逐值（仅两 broker 有位移语义）

| SeekMode | Kafka as-is → 提案 | Pulsar as-is → 提案 | 其余生产后端提案 |
| --- | --- | --- | --- |
| DEFAULT | 伪 no-op（assignment 空集）→ **support：交给 auto.offset.reset + seekToTime** | stub → **support：cursor 语义** | **warn-once（值无意义）**；DEFAULT 本身视为无 seek |
| INIT_SEEK_TO_BEGIN | 伪 no-op → **support：rebalance listener seekToBeginning（D3）** | stub → **support：seek(MessageId.earliest)** | **warn-once** |
| INIT_SEEK_TO_END | 伪 no-op → **support：seekToEnd（D3）** | stub → **support：seek(MessageId.latest)** | **warn-once** |
| ALWAYS_SEEK_TO_END | 伪 no-op → **support：rebalance listener 每次重连 seekToEnd（D3 明确一次性 seek 不可表达）** | stub → **support：每次 consumer 创建 seek(latest)** | **warn-once** |

## 维度 D — SubscriptionType 逐值

| 值 | Pulsar | Kafka | Local / SysDao / Lettuce / MqttServer / DB(agent) |
| --- | --- | --- | --- |
| Exclusive | support（`:254-266`）→ **support**；Exclusive+concurrency>1 当前 broker 拒绝（F-32）→ validator 层 typed-reject | warn-per-call（`:240-245`）→ **warn-once：组内单成员近似，不承诺排他** | **warn-once** |
| Shared | support → **support** | warn-per-call → **warn-once：组语义天然近 Shared** | **warn-once** |
| Failover | support → **support** | warn-per-call → **warn-once** | **warn-once** |
| Key_Shared | support → **support** | warn-per-call → **warn-once**（Kafka 按 key 分区有序可注释近似，但声明层面仍 warn） | **warn-once** |

D4 中立枚举（SINGLE_ACTIVE / LOAD_BALANCED / ORDERED_BY_KEY）映射表的落位由 WI27 承载；本矩阵只登记 as-is 与三态。

## 维度 E — onMessage 五种返回值语义

| 实现 | null | CompletionStage | ConsumeLater | Acknowledge | 其他非 null |
| --- | --- | --- | --- | --- | --- |
| Local | debug 忽略 → **support（=完成）** | syncGet → **support** | **当 reply 发出（F-14，`:187-188`）→ support：识别 + 延迟重投（进程内可计时重投）或 typed-reject；默认提案 support** | reply → **support** | reply → **support** |
| SysDao | 静默 → **support** | syncGet（`:505-507`）→ **support** | 识别 + delay 生效（`:158-165` 处理器）；广播路径 warn → **support（统一）** | reply → **support** | reply → **support** |
| Lettuce | 丢弃（`:134`）→ **support（=完成，fire-and-forget）** | 丢弃 → **support（同步解包后丢弃）** | 丢弃 → **warn-once（声明不支持延迟重投）** | 丢弃 → **warn-once** | 丢弃 → **warn-once** |
| MqttServer | 丢弃（`:128`）→ **support（fire-and-forget）** | 丢弃 → **support（解包）** | warn-once | warn-once | warn-once |
| Td | 不可达（send 已拒）→ **n/a** | n/a | n/a | n/a | n/a |
| DB(agent) | markConsumed（`:587-589`）→ **support** | whenComplete 递归（`:566-584`）→ **support** | 识别但 delay 忽略（`:585-586`）→ **support：honor delay（对齐 SysDao 的 minProcessDelay 语义）** | markConsumed 不发 reply → **support：补 ack-topic reply（对齐协议）或 warn-once；提案 support** | markConsumed → **support（=普通完成）** |
| DataPlane | 解码失败→null → **support（已声明）** | 透传 → **透传** | 透传 | 透传 | 透传 |
| Delegate | 转发 | 转发 | 转发 | 转发 | 转发 |
| Kafka | commit（`:239`）→ **support** | syncGet（`:223`）→ **support** | seek+不提交，delay 仅 debug（F-09）→ **support：honor delay（WI12）** | reply+commit（`:232-235`）→ **support** | reply+commit → **support** |
| Pulsar | ack → **support** | syncGet（`:165`）→ **support** | reconsumeLater + delay 生效（`:167-169`）→ **support（保持；F-16 批量 arity 另修）** | reply+ack（`:170-172`）→ **support** | reply+ack → **support** |
| Delayed / Mock | 桩豁免（无消费路径） | — | — | — | — |
| PollingJdbc | 丢弃（`:329`）→ **support（fire-and-forget）** | 丢弃 → **support（解包）** | warn-once | warn-once | warn-once |

D5(b') 新增的「终止拒绝信号」与「部分提交信号」是协议扩展，不在本矩阵 as-is 内；WI6/WI16/WI25 落地后本表需在 owner doc 中补两行（记录于 Non-Blocking Follow-ups）。

## 维度 F — 信封元数据（消费侧构建的 ApiMessage）

| 元数据项 | Kafka as-is → 提案 | Pulsar as-is → 提案 | SysDao | 其余 |
| --- | --- | --- | --- | --- |
| 消息 id | 无（F-48）→ **support：nop-id = topic-partition-offset（D2 契约）** | HEADER_ID ← messageId（`PulsarHelper.java:33-34`）→ **support（保持）** | 无 → **warn-once（或 sid 填充，D2 裁定）** | 无 → **warn-once / n-a** |
| topic | HEADER_TOPIC（`:56`）→ **support** | HEADER_TOPIC（`:36-39`）→ **support** | ApiHeaders.setTopic → **support** | n-a / warn-once |
| eventTime | record timestamp（`:60-62`）→ **support** | eventTime+publishTime+sequenceId（`:41-54`）→ **support** | setEventTime/setProcessTime → **support** | n-a |
| key(bizKey) | record key → HEADER_BIZ_KEY（`:52-54`）→ **support** | key → HEADER_BIZ_KEY（`:28-31`）→ **support** | bizKey header → **support** | n-a |
| headers 往返 | 全量解码复制（`:64-71`；发送侧 `:92-108`）→ **support（D2 保真契约 + F-33 顺序修复）** | properties 全量（`:56-61`）→ **support（同上）** | JSON round-trip → **support** | n-a |
| 上游头不可覆盖框架头 | 当前可覆盖（F-33，`:64-69` 后写）→ **support：框架头后写（WI24）** | 同（F-33）→ **support（WI24）** | n-a（无框架头集合） | n-a |
| 非 String payload | JSON 编码（F-31）→ **support：与 Pulsar 一致的两态（String 直用 / 其余拒绝或编码，D2 裁定）** | STRING schema 塞入（F-31，`:99`）→ **support：同上** | JSON 序列化 → **support** | n-a |

> 维度 F「其余」列的 n-a 说明：Local / Lettuce / MqttServer / DB(agent) / Delayed / Mock / PollingJdbc 七个实现的消费路径**不构建 ApiMessage 信封**（消息对象原样传递或裸 payload 直交 consumer），故各元数据项无可评估面——非桩豁免而是「无信封机制」这一事实本身；其提案为 D2 裁定时决定是否补信封（成本收益见 D2），未裁定前记 warn-once（Local/MQTT 等）或保持现状并在 owner doc 声明。test 桩（Mock/PollingJdbc）与 bench 桩（Delayed）沿用全局豁免。

## 建议能力集草案（D1 输入）

`MessageCapability` 枚举建议值：SEND / SUBSCRIBE / BATCH_CONSUME / DELAYED_SEND / TRANSACTION / SEEK / ACK_REPLY / ENVELOPE_METADATA / DLQ。

| 实现 | 建议声明能力集 | 说明 |
| --- | --- | --- |
| Kafka | SEND, SUBSCRIBE, BATCH_CONSUME, SEEK, ACK_REPLY, ENVELOPE_METADATA | TRANSACTION/DELAYED_SEND 不声明 → validator 对相应字段 typed-reject |
| Pulsar | SEND, SUBSCRIBE, BATCH_CONSUME, DELAYED_SEND, TRANSACTION, SEEK, ACK_REPLY, ENVELOPE_METADATA | 最全；DLQ 待 WI21 接线后增声明 |
| Local | SEND, SUBSCRIBE, ACK_REPLY | 进程内；其余 warn-once |
| SysDao | SEND, SUBSCRIBE, DELAYED_SEND, ACK_REPLY | DB 轮询 |
| Lettuce | SEND, SUBSCRIBE | fire-and-forget |
| MqttServer | SEND, SUBSCRIBE | fire-and-forget |
| DB(agent) | SEND, SUBSCRIBE, BATCH_CONSUME, ACK_REPLY | 队列语义 |
| DataPlane / Delegate | 透传（validator 作用于 delegate 结果的并集或直接透传） | 装饰器不声明自有能力，由 validator 递归到 delegate |
| Td | 空集（全部 typed-reject） | 符合 EvalRegistry 先例的空集拒绝 |
| Delayed / Mock / PollingJdbc | 不注册 validator（test/bench 桩豁免） | WI32 登记豁免理由 |

## Conclusion

- as-is 合规率（生产 9 实现全格三态）：仅 Td（全拒）与少数 support 格合规；Kafka 5 格伪 support（F-01/F-11）、Pulsar 6 格 ignore（F-03/F-17/F-29 系）、Local/Lettuce/MQTT/DB 全 ignore——这正是 D1 判定「枚举本身不是门禁、挂在入口的 validator 才是」的事实基础。
- 被否决的处理方式：无（本项为事实矩阵 + 提案，裁定权在 WI3）。
- 后续工作：WI3 依据本矩阵「建议能力集草案」与各提案列落 D1-D11 裁定；WI6 按终版矩阵实现 `MessageOptionsValidator`；WI7/WI8/WI12/WI15/WI17/WI24/WI25/WI27 按各自格子修复。

## Open Questions

- [ ] DBMessageService 的 Acknowledge 是否补 ack-topic reply（提案 support）——WI3 D5 裁定时确认
- [ ] Local 的 ConsumeLater 提案（进程内计时重投）实现成本 vs typed-reject——WI3 D5 裁定时确认

## References

- `ai-dev/analysis/2026-10/nop-message-audit-baseline.md`（WI1 基线，全部 MSG-F 锚点）
- `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（Hard constraint 三态定义、D1-D11 建议）
- `docs-for-ai/02-core-guides/error-handling.md`（typed-reject 的错误码规范）
