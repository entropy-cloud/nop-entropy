# nop-message 消息契约设计（D1 / D2 / D4 / D8）

**日期**：2026-10-04
**范围**：`nop-kernel/nop-api-core`（io.nop.api.core.message）、`nop-message/*` 各后端
**状态**：active
**负责人**：nop-message mission 执行 agent（ZCode session 71fd21c6），2026-10-04，依 roadmap `ai-dev/backlog/nop-message-encapsulation-roadmap.md` 前置裁定表生效

---

## 一、设计结论

1. **D1（能力声明机制）**：新增 `MessageCapability` 枚举与 `IMessageService.getCapabilities()`（default 方法，收窄式新增），门禁为挂在**每个 `subscribe` 与 `sendAsync` 入口**强制调用的 `MessageOptionsValidator`。空集默认本身是 fail-open——枚举不是门禁，入口校验才是。9 个非 broker 后端的能力声明按「逐后端能力表」落格，不允许「忽略全部选项」的第三态。
2. **D2（信封元数据契约）**：定义最小公共信封契约——messageId / topic / eventTime / key / headers 五项**往返保真**；每后端差异显式落表。上游同名头不得覆盖框架元数据头。
3. **D4（SubscriptionType 中立化）**：加性引入三值中立枚举（SINGLE_ACTIVE / LOAD_BALANCED / ORDERED_BY_KEY），与既有四值枚举并存 + 映射表 + 弃用路径；不做破坏性重命名。
4. **D8（选项字段兼容策略）**：`MessageSubscribeOptions` / `MessageSendOptions` 带 `@DataBean`/`@ConfigBean`，字段名即用户 YAML 键——一切字段变更（新增/改名/删除）一律**加性优先**：新增不破坏、改名或删除须给弃用期与迁移说明。

## 二、背景与动机

SPI 公分母策略（一套 `IMessageService` + beans 覆盖切换）的前提是能力差异可声明、可查询、可 fail-fast。当前缺失该机制导致同一选项字段在不同后端有三种以上语义且全部静默：Kafka 的 seek 在首次 poll 前是空操作（代码形态 support、运行时 no-op）、Pulsar 的 seek 是空方法体 TODO、Local 完全不读任何选项。IoC 构造的 bean 没有平台内 EvalBackendRegistry 那样的注册点（META-INF/services 驱动），故注册期 fail-fast 先例只能迁移「声明集合」思想，门禁必须改挂运行时入口。

信封元数据是最常被消费却完全无契约的面：仅 Pulsar 设置消息 id，依赖 `nop-id` 去重的消费者在 Kafka 侧得 null；两后端均先设框架头再拷贝上游头，上游同名头可覆盖真实 topic/offset。

## 三、核心设计

### 3.1 D1 能力声明与入口门禁

- `MessageCapability` 为枚举能力集，建议值域：SEND / SUBSCRIBE / BATCH_CONSUME / DELAYED_SEND / TRANSACTION / SEEK / ACK_REPLY / ENVELOPE_METADATA / DLQ。值域随实现演进可增，但新增后端必须显式声明全集。
- `getCapabilities()` 以接口 default 方法新增（不改变极简骨架形态）；返回空集的语义是「该实现拒绝一切选项字段」——validator 对任何非空选项抛类型化错误，而非静默放行。
- `MessageOptionsValidator` 在每个 `subscribe` 与 `sendAsync` 入口调用（不是 init）：校验「选项字段所需要的能力 ∈ 实现声明的能力集」，缺失即抛错误码；支持的字段放行，不支持的已声明字段按逐后端三态处置（生效 / 类型化错误 / 一次性 WARN）。
- 逐后端能力声明表（裁定生效版；test/bench 桩不注册 validator）：

| 后端 | 声明能力集 | 其余字段处置 |
| --- | --- | --- |
| Kafka | SEND, SUBSCRIBE, BATCH_CONSUME, SEEK, ACK_REPLY, ENVELOPE_METADATA | DELAYED_SEND warn-once；TRANSACTION 类型化拒绝（见 backend-semantics D11） |
| Pulsar | SEND, SUBSCRIBE, BATCH_CONSUME, DELAYED_SEND, TRANSACTION, ACK_REPLY, ENVELOPE_METADATA（SEEK 待 WI8 实现落地时同批声明——声明不得跑在行为前） | DLQ 接线后增声明 |
| Local | SEND, SUBSCRIBE, ACK_REPLY | 其余逐字段 warn-once |
| SysDao | SEND, SUBSCRIBE, DELAYED_SEND, ACK_REPLY | 其余逐字段 warn-once |
| Lettuce / MqttServer | SEND, SUBSCRIBE | 其余逐字段 warn-once（fire-and-forget 语义） |
| DB(agent) | SEND, SUBSCRIBE, BATCH_CONSUME, ACK_REPLY | 其余逐字段 warn-once |
| DataPlane / Delegate 装饰器 | 递归委托 delegate 声明 | 校验作用于最终 delegate |
| TdMessageService | 空集 | 全字段类型化拒绝（现行为保持） |

- 装饰器（DataPlane/Delegate）不声明自有能力；validator 必须穿透装饰器取最终 delegate 的声明，防止「装饰器层放行、底层忽略」的伪 support。

### 3.2 D2 最小信封契约

- 五项往返保真：messageId、topic、eventTime、key（bizKey）、headers。发送侧写入的元数据，消费侧必须能以同名语义读回。
- 每后端差异表（裁定生效版）：

| 元数据项 | Kafka | Pulsar | 其余后端 |
| --- | --- | --- | --- |
| messageId | topic-partition-offset 派生 | 原生 messageId | 无原生 id 的后端在信封契约中声明「messageId 由传输层保证唯一性，无则显式 warn-once」 |
| topic / eventTime / key | 原生映射（record key → bizKey，timestamp → eventTime） | 原生映射（key/eventTime/publishTime/sequenceId 全量） | SysDao 已映射；无信封的后端声明 warn-once |
| headers | 全量 UTF-8 往返 | properties 全量往返 | JSON round-trip（SysDao）或 warn-once |
| 非 String payload | 与 Pulsar 一致的两态：String 直用；其余按 D2 统一编码规则（编码或类型化拒绝，二选一，两后端必须同态） | 同左 | 不适用 |
| 框架头保护 | 框架元数据头**最后写入**，上游同名头不可覆盖 | 同左 | 同左 |

### 3.3 D4 SubscriptionType 中立化（加性）

- 新增三值中立枚举：SINGLE_ACTIVE（同订阅名同时仅一个活跃消费者）、LOAD_BALANCED（同订阅名多消费者轮询分发、无顺序承诺）、ORDERED_BY_KEY（同 key 消息固定路由到同一消费者）。
- 旧四值枚举（Exclusive/Shared/Failover/Key_Shared）保留并与新枚举双向映射：Exclusive↔SINGLE_ACTIVE、Shared↔LOAD_BALANCED、Failover↔SINGLE_ACTIVE（failover 语义注记）、Key_Shared↔ORDERED_BY_KEY。旧值进入弃用期，弃用期内使用打一次性 WARN。
- Pulsar 直通映射原生四型；Kafka 无原生订阅型，三值全部 warn-once（组语义近似 LOAD_BALANCED，不承诺排他）。

### 3.4 D8 选项字段演进规则

- `@DataBean`/`@ConfigBean` 选项类的字段名即用户 YAML 键，字段增删改一律加性优先：
  - 新增字段：允许，缺省值必须等价于「未配置」语义。
  - 改名/删除：给一个弃用期（旧键继续可读 + 启动期一次性 WARN 指向新键），弃用期后移除；迁移说明写入 owner doc。
  - 禁止在无弃用期的情况下直接重命名或删除已发布字段（`transactional`/`transactionTimeout` 的删除按 D11 执行并遵守本条）。
- 已知的历史实例：`cancelToken` 是接口类型字段却作为可写 YAML 键暴露而无后端兑现——按「声明了但从不读」处理（WI15 接线或 D8 规则移除，二选一以 WI15 裁定为准）。

## 四、拒绝了什么

- **D1 拒绝 (a) 仅 owner doc 矩阵**：文档没有强制力，正是当前「全静默」的成因。
- **D1 拒绝 (c) 各后端自建元数据**：无统一查询面，validator 无法跨后端工作。
- **D1 拒绝注册期 fail-fast 形态**：IoC 构造无注册点，且 Eval 先例的 EvalBackendRouter 自身含静默分支，不满足「不得静默」约束；仅迁移「声明集合」思想。
- **D2 拒绝维持现状各自拼装**：信封是跨后端消费契约，无契约即数据正确性风险（去重键丢失、元数据被覆盖）。
- **D4 拒绝 (a) 四值重命名与 (c) 维持现状**：重命名破坏 YAML 键与编译兼容；维持现状则 Pulsar 专属词汇（Exclusive 等）继续污染中立 SPI。
- **D8 拒绝 (a) 直接改字段名与 (c) 冻结选项类**：前者破坏用户配置，后者使「声明了但从不读」的死字段无法清理。

## 五、与已有设计的关系

- `backend-semantics-design.md`：D1 的字段三态处置与 D5/D6 的返回协议信号、D11 的字段删除互为引用；错误码形态遵守该文档 D9 的前缀裁定。
- 平台先例：`nop-xlang` 的 `EvalBackendCapability`/`EvalBackendRegistry`（仅「声明集合」思想可迁移，理由见 3.1）。
- 错误处理两层策略见 `docs-for-ai/02-core-guides/error-handling.md`。
