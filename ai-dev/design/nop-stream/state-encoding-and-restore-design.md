# nop-stream 状态编码与恢复设计

**范围**：`nop-stream/nop-stream-core`（`common/state`、`checkpoint`、`operators`）、`nop-stream/nop-stream-runtime`（`checkpoint/storage`、`execution`）、`nop-stream/nop-stream-cep`（`nfa/sharedbuffer`、`operator`）、`nop-stream/nop-stream-rocksdb`
**状态**：草案（owner 确认后转 active；确认前以现有 active 文档为实施契约）。
**定位**：本档是 `state-management-design.md` §6 的补充设计，并补全 `cep-design.md` §5.3（CEP 状态依赖）与 `checkpoint-design.md` §8.4.1（Serializer Fingerprint 策略）中与"状态编码"相关的契约。三份文档已回写指针；本档与 active 文档冲突时，以 active 文档为实施契约，owner 确认后以本档为准。

---

## 一、设计结论

1. **状态分三层：durable logical / derived rebuildable / ephemeral external**。只有第一类进入 checkpoint；第二类在恢复时由第一类重建；第三类不进入状态模型。checkpoint 持久化的是**逻辑状态**，不是运行时对象图。
2. **`Lockable` 属于 derived**，不应进入持久化面。它是 CEP 共享缓冲区的引用计数适配器；节点与边的引用计数由逻辑图（事件表、节点/边表、NFA computation states）按确定性规则重建，而不是把 `AtomicInteger` 与包装对象整体序列化。
3. **持久化边界从"对象图"改为"逻辑 schema + codec"**：状态 family 声明 `StateSchema`（逻辑字段与派生字段）与 `IStateCodec`（逻辑值 ↔ 字节），后端只存取不透明字节；MapState 的 key 与 value 各自有 codec 契约；TTL sidecar 属于运行时，不进入 schema。
4. **恢复是分阶段而不是一次反序列化**：operator state 恢复 → descriptor/schema 绑定 → keyed load → eager migrate → rebuild derived → validate invariants → activate。恢复的宿主、时序与失败原子性必须显式定义（§3.3/§3.4）。
5. **CEP 的 durable schema 采用 live 物理 stateName**（`sharedBuffer-events` / `sharedBuffer-entries-with-lockable-edges` / `sharedBuffer-events-count` / `nfaStateName` / `eventQueuesStateName` / `cep-current-watermark` / `cep-event-time-timers` / `cep-key-class`；另新增 `cep-event-class`），不重命名；逻辑 family 名只作文档别名。若未来必须改名，必须提供 alias 映射与"快照中存在但本作业未声明 state"的 fail-fast。
6. **用户类型可用性是一等契约**：类名白名单按三套配置面分开（v3 状态 codec / v2 legacy Java 状态 / 传输），不复用较宽的 Java 反序列化 baseline；写侧与读侧对称；容器元素类型在写侧也校验；显式注册 codec 视为受信代码，平台只对内置 codec 提供强制过滤。
7. **后端编码能力显式化**：状态后端的 `IStateEncodingCapability` 声明 `json` / `binary` / `custom` / `derived-rebuild` 支持；执行层的 `StreamBackendCapability` 保持独立，不混用。CEP 需要的编码能力被满足后，Memory 与 RocksDB 对 CEP 必须同时可用。
8. **格式版本单点推进**：`CheckpointFormatVersions.CURRENT_FORMAT_VERSION` 由 2 升到 3；v3 只写新格式，v2 只读兼容。读取分派先看 manifest `stateFormatVersion`：v2 再按 stateName/family 选择 legacy 或 JSON reader（`__java_bytes__` 仅 legacy reader 内部格式校验），v3 按 per-state `codecId` 分派；不得以 payload 内容触发 Java 反序列化。降级不承诺（旧引擎读 v3 必须 typed reject）。

---

## 二、约束与判断准则

### 2.1 什么必须持久化、什么必须重建

判断标准不是"对象是否 Serializable"，而是**它能否由 durable 逻辑状态 + 作业配置在所有 JVM 上确定性地重建**：

- 能重建 → 不持久化（引用计数、LRU 缓存、索引、cache statistics、统计句柄、pending replay 结构、in-flight 快照中间结果）。
- 不能重建 → 必须持久化（事件内容、节点/边拓扑、NFA 计算状态、待处理事件队列、timer、watermark、source offset、2PC pending transaction、用户 keyed state、operator state）。
- 不能也不需要 → 外部资源（连接、订阅、线程池、文件句柄）在 open/activate 期按配置重新创建。

新增状态 family 必须在注册处显式声明所属类别与重建方式；未声明即 fail-fast（§3.9 不变量 1）。

### 2.2 运行时包装不属于持久化面（以 `Lockable` 为例）

`Lockable<T>` 是共享缓冲区的引用计数包装，语义上属于"图结构 + 活跃引用"的派生结果。它出现在持久化面的路径是：MapState 的 value 被声明为 `Lockable`、`SharedBufferNode.edges` 直接持有 `Lockable<SharedBufferEdge>`、以及用 Java 原生序列化整体搬运对象图。这条路径把三个运行时关注点带进 durable 格式：`AtomicInteger` 的计数、Guava 缓存、对象图身份；它们都不是跨 JVM 稳定的业务事实。

设计约束：**durable schema 不得包含引用计数、缓存、索引、lambda/comparator 实例、JVM 句柄或对象图身份**。需要这些结构时，持久化其逻辑输入并在恢复期重建。

### 2.3 CEP 逻辑图与可重建性

CEP 的 durable 逻辑状态是一张按 key 隔离的有向图：

- 事件表：`EventId → (timestamp, 用户事件值)`。
- 节点/边表：`NodeId → 出边列表（targetNodeId, deweyNumber）`；节点身份由 `(eventId, stateName)` 决定。
- 计算状态：`partialMatches` / `completedMatches` 中每个 `ComputationState` 的 `previousBufferEntry` 与 dewey 版本。
- 待处理事件队列、`eventsCount`（per-timestamp next-EventId 分配器）、timer、watermark。

**引用计数重建规则**（逐 key、逐 namespace，与 live `lockNode`/`releaseNode` 语义一致）：

```
lockEvents(N) = multiset(
  [edge.dewey | 存在节点 M 的边 M -> N，其 dewey = edge.dewey]      // 每条入边一次
∪ [state.version | 存在 computation state，其 previousBufferEntry = N] // 每个 state 一次
)
nodeRef(N)  = |lockEvents(N)|                       // 多重集大小（重复项计数）
edgeRef(N.e) = |{ v ∈ lockEvents(N) : v.isCompatibleWith(N.e.dewey) }|  // 同样保留重复
eventRef(Ev) = |{ N | N.eventId = Ev }|
```

**为什么是多重集**：live 的 `lockNode` 每次调用无条件 `lock()`，不按版本去重；同一节点/同一 dewey 版本可能被多次加锁（例如 `NFA.handleTakeEdge` 对同一 `previousEntry` 与 `nextVersion` 连续创建多个 computation state、或多条同版本入边）。集合去重会低估 nodeRef/edgeRef，恢复后提前 detach 共享路径。

- `partialMatches` 与 `completedMatches` 都必须计入（两者都持有 `previousBufferEntry` 引用）。
- 边上的引用计数是**跨记录存活的持久引用计数**（保护节点出边不被提前 detach），不是访问期临时锁；恢复时不能统一置 0。
- 节点/事件 ref 为 0 的孤立条目**不自动删除**：保留在图中，validate 记录为可回收候选；删除只由 live 的 release 语义决定。清理策略由 WI5 给出有界方案，不允许无界滞留。
- 重建后 validate 必须校验：所有边 target 存在、无负计数、eventRef 与节点表一致；每个 **非 start** computation state 的 `previousBufferEntry`/`startEventId` 必须存在（start state 两者合法为 null，必须豁免）；v3 另校验结构不变量（每条边的 target.nodeRef ≥ 1、引用非空）；**legacy 投影时**再用旧 `refCounter` 与多重集公式对账（v3 由公式重建，公式重算本身不作为独立不变量）。失败则 fail-fast（§3.4）。

---

## 三、核心设计

### 3.1 状态分类

| 类别 | 定义 | 判据 | 示例 | checkpoint | 恢复动作 |
|---|---|---|---|---|---|
| **Durable logical** | 业务事实与一致性问题需要跨故障保留的状态 | 丢失会改变结果或破坏 exactly-once | 用户 keyed state、窗口内容、CEP 事件/节点/边/NFA 计算状态、待处理事件队列、eventsCount、timer、watermark、source cursor、2PC pending commits | 必须，经 codec | 解码 + 校验 |
| **Derived rebuildable** | 由 durable logical + 配置确定性推导出的运行时结构 | 可由输入重建，且重建成本有界 | `Lockable.refCounter`、Guava LRU 缓存、cache stats、倒排索引、NFA `PriorityQueue`/comparator 实例 | 禁止 | 由 rebuild unit 重建 |
| **Ephemeral external** | JVM/进程生命周期内的外部资源与观测句柄 | 无法序列化，也不应序列化 | JDBC 连接、消息订阅、线程池、文件句柄、metrics handle、`lastSnapshotResult`、current key、TTL sidecar | 禁止 | open/activate 期重新创建或等待运行时设置 |

边界说明：

- **eventsCount**：per-timestamp 的 next-EventId 分配器（`SharedBuffer.registerEvent` 读取后写回 `id+1`；watermark 推进时按 timestamp 清理 key）。它属于 Durable，理由是保证 EventId 单调、避免恢复后与历史 id 冲突；不是"每 timestamp 事件数"，整数 value 也不是清理判据。
- **TTL sidecar**：state-management-design §12 明确 TTL 是运行时行为、不持久化；恢复后由 live descriptor 重新绑定。v3 不改此语义。
- **current key**：由上游 `KeyExtractingOutput` 在记录处理前设置，不持久化；恢复只恢复 key 类型（`cep-key-class`）并等待运行时设置当前 key。

### 3.2 编码契约

每个 keyed state family 在注册时声明：

1. **`StateSchema`**：逻辑字段、字段类型、schema version、派生字段清单（声明不持久化、由 rebuild 生成）、用户类型信息（如 CEP 的用户事件类型）。
2. **`IStateCodec<T>`**：作用于**单个逻辑值**（MapState 作用于单个 map entry）；`codecId` + encode/decode；不感知 key/namespace/分片。
3. **`IStateKeyCodec<K>`**（仅 MapState）：map key 的类型信息与编码；与 value codec 一起进入 fingerprint。
4. **`IStateRebuildUnit` / `StateRebuildContext`**：同一 rebuild unit 内可跨 family 读取（例如 CEP 需要同时访问 events、entries、NFA state）；context 提供当前 key/namespace、sibling state 只读访问、作业配置；单位是 **per-key（必要时 per-namespace）**，禁止全局重建。

**MapState 语义**：codec 按 per-entry 工作（与 RocksDB 的 base key + map-key 后缀布局一致），不是整 map 编码；map key 恢复必须有类型来源（key codec），避免 JSON-native key 漂移。

**与既有 `IStreamSerializer` 的关系**：`IStreamSerializer` 是用户态 opt-in serializer，保留为 `StateDescriptor.setSerializer` 的兼容入口；v3 中它被适配为 value codec（`codecId` 记录适配器标识），不再作为绕过 schema/fingerprint 的旁路。引擎内部状态不得使用 `IStreamSerializer` 作为持久化格式；`JavaStreamSerializer` 只作为 legacy 读路径。

**operator state**：v3 初版范围是 keyed state family。`OperatorSnapshotResult` 的 operator states（source offset、2PC pending、CEP timer 台账、watermark、key class/event class）继续沿用现有 JSON 快照面，不纳入 `IStateCodec`。**该面当前没有 per-state schemaVersion**（`CheckpointSerDe` 只有 envelope/formatVersion/checksum），CEP timer 台账的旧格式兼容是 `CepOperator.restoreState` 的 ad hoc 分支；v3 初版显式接受该现状（不宣称有版本契约），若后续要统一，另立设计。

### 3.3 恢复阶段与宿主

恢复顺序固定为：

```
1. operator.restoreState      : 恢复 operator state（key class、event class、watermark、timer 台账、source offset、2PC pending）；
                                此阶段先于 backend 创建，供 typed backend 选择 key/event 类
2. descriptor/schema bind     : 创建 StateDescriptor/codec/StateSchema（必须在 eager migrate 之前）
3. keyed load                 : 读入 durable keyed entries 到暂存结构（operator 侧 applyPendingRestoreState 触发，
                                不是 backend API；backend 只做 decode）
4. eager migrate              : 按 codecId/schemaVersion/checksum 查 StateMigrationFunction；未注册 → fail-fast
5. rebuild                    : 按 rebuild unit 逐 key 重建派生结构（refCounter/cache/index）
6. validate                   : family/unit 结构不变量；失败 → fail-fast（§3.4）
7. activate                   : 绑定 timer/watermark、重建 timer 再武装；等待运行时设置 current key 后开始处理记录
```

**时序约束**：

- 保留既有 "restoreState 先于 open" 契约（CEP key class 依赖它）；key class/event class 只在阶段 1 恢复，activate 不再重复恢复。
- 迁移必须 **eager** 在 load 之后、rebuild 之前执行，不能依赖首次 `getState()` 的懒触发（否则 rebuild 可能读到未迁移的值）；因此 descriptor/schema 注册必须先于 migrate。仅当指纹不匹配、需要迁移时才查 `StateMigrationFunction`；无迁移需求时不触发 fail-fast。
- 当前实现没有 `activate` 钩子；WI4 负责定义宿主（例如 operator open 尾部的受保护生命周期阶段），WI5 把 CEP 的 PT timer 再武装从 `open()` 前段移到 activate；event-time timer 台账在 validate 后排水。
- keyed load 到暂存结构后再 swap；禁止先 `clear()` 再逐 state 校验（现有 `MemoryStateSerDe.restoreState` 的 clear-before-validate 属需修复的失败原子性缺陷）。

### 3.4 失败原子性

- load/migrate/rebuild/validate 任一阶段失败：任务失败，按现有全局恢复路径处理；**恢复点是 latest durable epoch**，不是"上一 durable epoch"（引擎不提供隐式 older-epoch 选择；需要更早恢复必须使用显式 savepoint）。
- 不允许"部分重建 + 继续处理"；validate 失败不得把不完整状态留在可运行 backend 中（staging swap 或失败即丢弃 backend 实例，具体机制由 WI4 裁定）。
- 单条损坏记录与结构性 schema/版本错误的语义分开：结构性错误 fail-fast；现有 checkpoint-design §2.11.4 对 channel state 的 best-effort skip 维持不变，不在本设计范围。

### 3.5 CEP durable schema（v3）

**物理 stateName 保持 live 名称**（避免 v2→v3 静默空状态）：

| stateName | 形态 | 类别 | 说明 |
|---|---|---|---|
| `sharedBuffer-events` | `MapState<EventId, EventPayload>` | Durable | EventId 已含 timestamp；EventPayload 只存用户事件值；payload codec 需要用户事件类型来源 |
| `sharedBuffer-entries-with-lockable-edges` | `MapState<NodeId, NodeRecord>` | Durable | 有序边列表，每条边 = `(targetNodeId?, deweyNumber)`；不内嵌对象 |
| `sharedBuffer-events-count` | `MapState<Long, Integer>` | Durable | per-timestamp next-EventId 分配器 |
| `nfaStateName` | `ValueState<NfaStateRecord>` | Durable | computation states 扁平记录；不含 PriorityQueue/comparator |
| `eventQueuesStateName` | `MapState<Long, List<EventPayload>>` | Durable | 尚未进入 NFA 的缓冲事件（ET 的 watermark 之上、PT 的时间桶）；PT 排水依赖它 |
| `cep-current-watermark` | operator state | Durable | 当前 watermark |
| `cep-event-time-timers` | operator state（typed JSON） | Durable | per-key timer 台账 |
| `cep-key-class` | operator state | Durable | 恢复键类 |
| `cep-event-class`（新增） | operator state 或 state header | Durable | 用户事件类型来源；首事件捕获 + checkpoint 携带，规则同 key class |

**用户事件 payload 类型来源**：`SharedBuffer` 构造参数 `valueSerializer` 当前"accepts but does not use"；`CepOperator` 以 `Lockable.class` 作为 MapState value 类型，无法从 descriptor 得到 V。v3 规则：**声明面显式类型优先**；未声明时按首事件捕获 + checkpoint 携带（规则同 `cep-key-class`）；两者都没有时仅在“存在需要按该类型解码的 event payload”时 fail-fast——空状态/全超时清空后的 checkpoint 允许恢复。多态事件类型需显式声明基类或注册 codec；v2 legacy 投影从旧对象图的元素运行时类型推导事件类，推导失败按上述规则处理。payload codec 类型进入 fingerprint。

**NfaStateRecord**：

- `partialMatches` / `completedMatches`：每条记录包含 `currentStateName`、`deweyVersion`、`startTimestamp`、`previousTimestamp`、`previousBufferEntry`、`startEventId`。
- `stateChanged` / `isNewStartPartialMatch`：声明为 ephemeral，恢复置 false（快照点本就为 false；显式声明防未来漂移）。
- 队列顺序由 comparator 规则在恢复时重建；comparator 实例不持久化。
- **记录缺失/空**：仅当同一 key 的 **events / entries** 也全部为空（合法清空路径，例如全超时后 `resetNfaStateIfFullyTimedOut`）时，才允许恢复为 `nfa.createInitialNFAState()` 语义的初始 start state；若 events 或 entries 非空而 NFA 记录缺失/空，必须 typed fail-fast（禁止静默丢弃 partial matches）。**`eventQueues` 不参与该门禁**：它持有尚未进入 NFA 的缓冲事件，不引用 buffer entry，可以合法非空（例如首个事件时间戳 > watermark 时只入队、NFA 记录仍为 null）。该契约写入恢复代码与测试。

**重建**（§2.3 多重集公式）：逐 key 加载 events/entries；构造包装 ref=0；对每个节点创建时对 event 记一次引用（于是 eventRef = 引用它的节点数）；对每条入边与每个 partial/completed computation state 的 previousBufferEntry，执行一次与 live `lockNode(target, version)` 等价的计数操作：target 节点 ref++，并按 `version.isCompatibleWith(edge.dewey)` 对其兼容边各 ref++（重复调用重复计数）；重建队列（comparator 由代码提供）与空缓存。validate 按 §2.3 校验。

**legacy 投影**：v2 的 `__java_bytes__` 旧对象图（`NFAState`、`SharedBuffer`、`Lockable`）先由 legacy reader 解码为旧对象，再投影到上述 DTO，然后执行同一 rebuild/validate；不得直接把旧 refCounter 当 v3 结果（但仍可用于 validate 对账）。

### 3.6 用户类型与安全边界

配置面必须三分，不能混用：

| 面 | 载体 | 白名单 | 说明 |
|---|---|---|---|
| v3 状态 codec（新） | `nop.stream.state.codec.allowed-prefixes` | 新键；写读对称 | 只作用于 `IStateCodec`/JSON/容器元素/namespace 类型 |
| v2 legacy Java 状态 | `nop.stream.state.deserialize.allowed-prefixes` | 旧键；**保持现状不放宽** | 只作用于 `JavaStreamSerializer` 的 `__java_bytes__` legacy 读 |
| 传输/线协议 | `ClassNameValidator`（`StreamElementCodec` 等） | 独立 baseline | **不随状态面放宽**；与 `StreamDeserializationFilter` 不是同一配置 |

- 旧键不是新键的“只读别名”：两者默认值可以相同，但语义与生命周期独立；任何合并都必须显式说明放宽面与安全评估。
- **已知缺口（本设计范围外，WI7 认领）**：`PollingJdbcMessageService` 的 JDBC message 路径使用裸 `ObjectInputStream.readObject()`，当前没有 `ObjectInputFilter`；要么在 WI7 补 filter + 负向测试，要么在“明确不认领”中登记为已接受缺口，不能声称“传输面已有 baseline”。
- **写读对称**：写侧与读侧使用同一 v3 状态白名单与同一 typed 错误码；容器元素类型在 encode 时也做校验；未声明类型在 write 侧 fail-fast，不生产不可恢复 checkpoint。
- **类加载**：白名单通过后先 `Class.forName`，失败再尝试 TCCL（线程上下文类加载器）；两处失败均 typed 错误，不静默回退。
- **custom codec 信任边界**：显式注册的 `IStateCodec` 是受信代码，平台不保证其内部反序列化安全；内置 codec 强制白名单。
- **JSON 多态类型标签**：现有 namespace 编码依赖 `@type`（如 `TimeWindow`/`GlobalWindow`/`VoidNamespace`）；v3 必须定义**内置允许集**（这些引擎类型 + 内置容器/DTO），用户类型必须显式注册后才允许出现；未注册的 `@type` typed reject。
- **容器元素类型**（R5-ST-05）：沿 §3.2 的 MapState/ListState codec 契约恢复元素类型，禁止“读侧 WARN + JSON-native”作为默认降级；无法解析时 fail-fast。
- **文档契约**：第三方包或非白名单 `io.nop.*` 应用包使用自定义状态类型时，必须配置前缀或注册 codec；owner doc 记录配置键、失败语义与示例（WI36 回写）。

### 3.7 后端编码能力

**分层**：状态后端能力与执行后端能力分开：

- `IStateEncodingCapability`（状态后端）：`json` / `binary` / `custom` / `derived-rebuild`。
- `StreamBackendCapability`（执行后端）：分布式执行、远程状态服务、rescale、requirement 集合（保持现有语义）。

| 能力 | 语义 | Memory | RocksDB |
|---|---|---|---|
| `json` | 支持 `JsonStateCodec` | 是 | 是 |
| `binary` | 支持二进制 value codec | 是 | 实现后支持 |
| `custom` | 支持用户注册 codec | 是 | 实现后支持 |
| `derived-rebuild` | 恢复阶段调用 rebuild unit | 是 | 是 |

**incremental checkpoint 不属于 codec 能力**：它是 checkpoint 存储/快照格式能力。v3 初版的规则是：增量 SST 的 value 字节必须与所选 codec 一致，但当前 segment 描述只有 `codec=identity`/schemaVersion，没有 per-state codecId；因此在 v3 初版中，**非 JSON codec 与增量 checkpoint 组合必须在部署/启动期 typed fail-fast**；JSON codec + 增量的 migrate/validate 语义也必须在 WI6 明确（固定 schema 版本、禁止迁移，或把增量排除出 v3 兼容面）。per-segment codec/格式版本另立设计。

校验时机：构建期校验 model requirement；部署/运行期校验所选后端能力。不允许"配置了 RocksDB 却静默降级为 Memory"。

### 3.8 版本、兼容、迁移、回滚

- **版本**：`CheckpointFormatVersions.CURRENT_FORMAT_VERSION` 2→3；`EpochManifest.stateFormatVersion` 与信封同步；manifest/body 的 canonical 文本格式不在本设计范围（D3 的 Deferred 项另有 owner 门）。
- **写入**：新写路径只写 v3；每个 keyed state family 的 snapshot 记录 `codecId`、`schemaVersion`、`schemaChecksum`；operator state 沿用现有 JSON。
- **读取分派**：由 **manifest 的 `stateFormatVersion` + per-state `codecId` 是否存在**决定，不依赖 payload 内容触发 Java 反序列化。v2（`stateFormatVersion ≤ 2`、无 codecId）：按 stateName/family 选择 legacy 或 JSON reader，其中 `__java_bytes__` 只作为 legacy reader 内部的格式校验 marker；v3（`stateFormatVersion = 3`、有 codecId）：按 codecId 分派。restore context 必须显式携带 formatVersion，从 manifest 下传到 keyed state restore；高版本 → `ERR_STREAM_CHECKPOINT_FORMAT_VERSION_UNSUPPORTED`。
- **stateName**：v3 保持 live 名称（§3.5）；任何重命名必须提供 alias 映射 + "快照中存在但本作业未声明 state" fail-fast，并有 v2 CEP fixture 覆盖。
- **迁移**：`StateMigrationFunction` 作用于逻辑值（decode 后、rebuild 前）；选择依据为 `codecId + schemaVersion + checksum`；生产路径必须注入 `StreamComponents` 注册表（当前只有测试注入，见 roadmap WI8）。
- **fixture**：v2 fixture 必须包含 CEP 的 `__java_bytes__` 状态（现有 legacy fixture 只有 source job，不足以证明 CEP legacy 恢复）；v3 fixture 同时入库；跨版本测试成为门禁。
- **回滚**：旧引擎读 v3 必须 typed reject，不承诺降级；发布说明写明。

### 3.9 不变量

1. 只有 Durable logical 状态允许进入 checkpoint；Derived/Ephemeral 必须在注册处显式声明并证明可重建。
2. 持久化状态不得包含运行时生命周期包装（引用计数、缓存、句柄、lambda、comparator 实例、current key、TTL sidecar）。
3. codec 解码后的逻辑值必须通过 family schema 校验；未知 codecId/schemaVersion/stateName 必须 typed fail-fast。
4. 恢复顺序固定为 load → migrate → rebuild → validate → activate；validate 失败不得进入数据处理。
5. 派生状态重建必须逐 key/namespace、幂等、只依赖 durable 状态与配置、成本有界可观测。
6. 用户类型白名单在写侧与读侧对称；容器元素类型在写侧同样校验；"写成功但读拒绝/降级"是缺陷。
7. 后端能力与 state family codec 需求必须在部署/运行期校验；不允许静默降级。
8. CEP 的 nodeRef/edgeRef/eventRef 必须按 §2.3 公式重建并通过守恒校验；边引用计数不得统一置 0。

---

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| 继续用 Java 原生序列化承载引擎状态 | 格式被 Java 类演进绑死；RocksDB 无法消费；安全白名单被迫大范围放行；无法做逻辑级 schema 迁移 |
| 把 `Lockable` 当作正式状态 schema 的一部分持久化 | 引用计数是派生数据，持久化会把运行时实现细节变成兼容性契约；且无法保证与图一致 |
| 边引用计数恢复为 0 | 与 live 的 `lockNode`/`releaseNode` 语义相反：共享前缀的边会被提前 detach，导致匹配路径截断/静默丢匹配 |
| 为 CEP 单独实现一套"特殊状态后端" | 违反"统一状态后端"目标；应通过 codec/能力面接入，而不是后端特判 |
| 全量复刻 Flink TypeInformation/TypeSerializerSnapshot | 复杂度与业务代码污染不可接受；nop 的定位只需要"逻辑 schema + codec + 迁移" |
| 只做 JSON 并接受 CEP 仅 Memory | 直接放弃成功标准 5；用户类型问题也不会因"只用 JSON"消失（类名白名单同样阻塞） |
| 在恢复期自动丢弃无法恢复的派生状态并继续运行 | 违反 no-silent-no-op；必须 fail-fast，由现有全局恢复路径处理 |
| v3 初版同时支持所有 codec 的增量 checkpoint | 增量 SST 是 per-checkpoint 的 binary 实物，codec 版本面未设计前混用会静默损坏；先显式 fail-fast，另立设计 |

---

## 五、与已有设计的关系

| 文档 | 关系 | 回写动作（完成状态） |
|---|---|---|
| `state-management-design.md` §6 | 本档补充并取代其"唯一序列化实现是 JsonTool / 内存快照零序列化 / 所有持久化结构 JSON round-trip"的表述 | 已加目标模型指针、校正序列化/迁移/codecId 表述；最终一致性回写归 WI8 |
| `cep-design.md` §5.3 / §8 | 本档补全 CEP 状态依赖与已知限制 | 已加 §5.3.1（live stateName、event queue、多重集引用计数重建）；RocksDB 限制标注为 v3 前 |
| `checkpoint-design.md` §2.6/§8.4.1 | 本档补充 Serializer Fingerprint 的 codec 维度与 v3 版本语义 | §2.6 已加 v3 目标段、§8.4.1 已加 codecId/schemaVersion；lazy migrate 与 fingerprint 匹配规则的最终改写归 WI8 |
| `ai-dev/design/nop-stream/README.md` | 索引与 CEP 状态模型条目 | 已更新 |
| `00-vision.md` §三 #3、§八 #12 | 本档不改变约束，只新增 §3.9 候选不变量 | 是否入 vision 由 WI2 裁定 |
| `checkpoint-engine-convergence.md` | DIV 项与本设计无直接冲突；v3 迁移须保持双驱动面一致（差分门继续适用） | 实施时按 roadmap WI15/WI31 复核 |

---

## 六、验收与证明矩阵

| 证明项 | 断言 | 测试形态 |
|---|---|---|
| Lockable 不在持久化面 | v3 CEP 状态无 `Lockable`/Java 字节；refCounter 由逻辑图重建 | 结构断言 + 序列化格式扫描 |
| 边引用计数重建 | 按多重集公式逐项相等；覆盖 a→b→c、a,b,b、同一节点同 dewey 多处 lock（optional-final 双锁）、多父边同版本形状；人工置 0 后恢复/继续处理必须红 | `TestCepEdgeRefcountRebuild` + 变异测试 |
| event queue 恢复 | 事件已入队、watermark 未到时的 checkpoint → 恢复后事件仍被处理；NFA 记录 null + eventQueues 非空、reset 清空 NFA + 未来 bucket 两种合法态不得误 fail-fast | `TestCepBufferedEventRestore` + `TestCepFirstEventBufferedRestore` + `TestCepNfaClearedWithFutureBucketRestore`（memory 先行；RocksDB 在 WI6/WI9 能力就绪后补跑） |
| 状态名/legacy | v2 CEP fixture（含 `__java_bytes__`）可恢复且匹配结果非空；未知 state 不静默为空 | `TestLegacyCepV2Restore` |
| per-key rebuild | 多 key 相同 EventId/NodeId 不串线 | 多 key 单测 |
| 用户类型 | 第三方前缀声明后可 checkpoint/restore；未声明写侧 fail-fast；容器元素写读对称 | 单测 + E2E（memory/rocksdb） |
| 迁移可达 | 生产路径注入 registry 后迁移生效；未注册 fail-fast；v2→v3 | E2E |
| 后端能力 | 不支持组合部署期 fail-fast；CEP×RocksDB 全链路绿；非 JSON + incremental fail-fast | 能力矩阵参数化测试 |
| 恢复原子性 | load/validate 失败不留下可运行的部分状态 | 故障注入 + 断言 backend 状态 |
| 性能不回退 | 默认 JSON 路径 checkpoint 体积/耗时与 v2 同量级；binary codec 有基准收益 | JMH + 基准门（WI30） |

---

## 七、待 owner 裁定（WI2 清单见 roadmap）

1. v3 格式是否保留 v2 写入灰度开关（推荐：只写 v3）。
2. 用户类型白名单配置键是否合并为单键（推荐：状态面单键；旧键独立、不是别名）。
3. CEP `eventsCount` 维持 durable 还是改 derived（推荐：durable，理由见 §3.1）。
4. 二进制 codec 的默认启用条件（推荐：显式 opt-in）。
5. v2 legacy reader 的保留窗口与移除触发条件。
6. CEP event class 的携带机制（推荐：声明面显式类型优先，首事件捕获兜底，空状态允许无类型恢复；见 §3.5）。
