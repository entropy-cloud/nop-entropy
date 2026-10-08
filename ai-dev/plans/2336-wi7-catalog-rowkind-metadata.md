# 2336 WI7 引擎级变更元数据（RowKind + tableId on StreamRecord）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI7（M2）+ 裁定 `ai-dev/design/nop-stream/multi-engine-gap-adjudication.md` §三 R4（落点 = StreamRecord 引擎级可选变更元数据，非 CDC 层消息信封）
> Related: plan 2335（typed 拒绝面惯例）；cdc-design（上游语义）

## Purpose

在引擎级记录载体上落地可选变更元数据（RowKind + tableId）：CDC 变更语义从「单连接器私有字段」迁移为引擎可见的记录元数据，为 schema evolution（WI15）与生态 sink 前置。兼容约束（R4）：元数据可选，缺省 = INSERT-only append 语义，现有全链路零行为变化；非 CDC-aware sink 消费非 INSERT 记录 = typed fail-fast。

## Current Baseline（2026-10-08 研究核实 + 对抗性审查修订）

- `StreamRecord<T>`（nop-stream-core streamrecord 包）：value + 可选 timestamp 的 final 载体，`copy/copyTo/replace` 三路径；无任何变更语义字段。
- 源发射桥接点：`StreamSourceOperator` SourceContext（collect :247 / collectWithTimestamp :257）与 `SourceReaderOperator`（:320，Source API 路径——R3 裁定的战略路径）两处构建记录。
- **元数据存活边界（审查 P4 钉定）**：生产代码新建 StreamRecord 的点 20+（StreamMap:49、StreamFlatMap:51、Reduce/Join/Window/Cep/Process side-output/SideOutputTap 等）——新建记录型 operator **丢弃**元数据；元数据存活 = 源桥接点 + 前向传递（RecordWriterOutput.copy :1227 / KeyExtractingOutput 原样前向 / copyTo）。本期交付该边界内的引擎可见性；新建记录型算子的桥接扩展归后续（见 Deferred）。
- sink 消费点：`StreamSinkOperator.processElement`（:95-97）→ `userFunction.consume(element.getValue())`——守卫落此对所有图部署 sink 生效；**side-output sink 经 SideOutputRegistry 直连不经过本 operator（守卫旁路，审查 P6，Out Of Scope 声明）**。
- 跨进程/恢复传输（审查 P9 裁定 = 方案 A 纳入 scope）：`StreamElementCodec` envelope 只携带 value+timestamp(+outputTagId)——消费方 runtime `RemoteResultPartition.encode`/`RemoteInputChannel.decode` 与 core `ChannelState.toSerializableForm/fromSerializableForm`（unaligned checkpoint 在途记录）。**本期 envelope 增可选 rowKind/tableId 字段**（null 缺省后向兼容），保住多顶点 job 的守卫有效性。
- sink 清单处置（审查 P7）：jdbc-2pc 实现 CdcChangeAwareSink（keyed 从 value 推导操作 / INSERT 模式对 ChangeEvent u/d 已 typed 拒绝）；file/message/batch-consumer/kafka-2pc **不实现**（透传语义 sink——对带非 INSERT 元数据的记录 typed fail-fast = R4 预期行为，connectors.md 相应行补注；kafka 透传 CDC 流为新增失败面，如实记入执行发现）。
- `ChangeEvent`（nop-message-debezium）：非 final POJO，`operation`（c/u/d/r）+ `ChangeEventMetadata`（含表标识）；**nop-message-debezium 依赖 nop-api-core（不依赖 nop-stream-core）**；nop-stream-core 亦依赖 nop-api-core——`nop-api-core` 是两侧唯一共享内核。
- jdbc-2pc sink 现状：keyed 模式（UPSERT/UPSERT_DELETE）从 `opMapper` 或 ChangeEvent.operation 推导操作；INSERT 模式对 ChangeEvent u/d 已 typed 拒绝——sink 侧语义承载在 value 内，与引擎无关。
- 无任何现成 RowKind/tableId 抽象（grep 核实）。

## Goals

- **nop-api-core**：新增 `io.nop.api.core.stream.RowKind`（INSERT / UPDATE_BEFORE / UPDATE_AFTER / DELETE，Flink 四值语义）与 `io.nop.api.core.stream.ChangeSemanticView`（`RowKind getRowKind()` + `String getTableId()`，null tableId 合法 = 未标识）。nop-stream-core pom 增补显式 nop-api-core 依赖（现为传递可用，审查 P1 依赖卫生）。
- **传输面（审查 P9 方案 A）**：`StreamMessageEnvelope` 增可选 rowKind/tableId 字段 + `StreamElementCodec` encode/decode + `ChannelState` envelope-map 读写（null 缺省，JSON 后向兼容）。
- **nop-stream-core**：
  - `StreamRecord` 增可选 `rowKind`（null = INSERT-only append 缺省语义）/`tableId`（null = 未标识）字段；`copy/copyTo` 携带元数据；`replace` 保留元数据（原地变异语义不变）；equals/hashCode 不纳入（元数据非身份）；toString 追加。
  - `StreamSourceOperator`：value `instanceof ChangeSemanticView` 时自动把 `getRowKind()/getTableId()` 盖到构建的 StreamRecord（桥接点——连接器零改动即得迁移）。
  - `StreamSinkOperator.processElement` 守卫：记录带非 INSERT rowKind 且 sink 非 `CdcChangeAwareSink`（core 新标记接口）→ typed fail-fast（新错误码 `nop.err.stream.sink-not-change-aware`，含 sink 类名与 rowKind）。
  - `JdbcTwoPhaseCommitSink`（connector-jdbc）实现 `CdcChangeAwareSink`（keyed 与 INSERT 模式都实现：keyed 模式本就从 value 推导操作；INSERT 模式已有 u/d typed 拒绝——标记语义 = 「sink 自身能正确处理/拒绝变更记录」，守卫的 fail-fast 对 INSERT 模式由 sink 既有守卫兑现）。
- **nop-message-debezium**：`ChangeEvent implements ChangeSemanticView`（operation c/r→INSERT、u→UPDATE_AFTER、d→DELETE、**null/未知→INSERT**（防御语义，审查 P2；原 operation 仍随 value 供 sink 自行拒绝）；tableId 取 metadata 表标识；getter 新增使 wire JSON 增两可选属性——后向兼容，无既有 JSON 精确断言）。
- 测试：StreamRecord 元数据 copy/replace 往返；SourceOperator + SourceReaderOperator 双桥接；守卫矩阵（**「INSERT sink + DELETE 记录」用例必须用 ChangeEvent 值构造**——审查钉定：INSERT 模式拒绝以 value instanceof ChangeEvent 为前提，非 ChangeEvent 值 + DELETE 元数据组合本期引擎内不可达，CdcChangeAwareSink 契约 javadoc 写明此前提）；codec envelope 与 ChannelState 元数据往返；零回归（rowKind=null 行为不变 + 既有 CDC 链路 c/r→INSERT 不触发守卫）；ChangeEvent 映射矩阵（c/r/u/d/未知）。
- docs：nop-stream.md 增「引擎级变更元数据」节（可选语义/桥接面/守卫面/CdcChangeAwareSink 契约）；connectors.md 能力矩阵不变（连接器面无变化——debezium 行为零改动）。

## Non-Goals

- CatalogTable 完整 schema 目录（roadmap 行标题的 CatalogTable 部分随 R4 收窄为 tableId 标识；schema evolution 归 WI15）；sink 按 rowKind 驱动 DML（keyed 模式仍以 value 为操作真相源——rowKind 本期为引擎可见性与守卫面，非 sink 驱动面）；UPDATE_BEFORE 生产（debezium 映射只产 UPDATE_AFTER）； envelop/信封方案（R4 已拒绝）。

## Scope

### In Scope

- api-core 两类型 + core 三点（StreamRecord/operator 桥接/sink 守卫 + CdcChangeAwareSink）+ connector-jdbc 标记 + nop-message-debezium ChangeEvent 实现。
- 上述全测试面与 owner docs。

### Out Of Scope

- rowKind 驱动 sink DML；schema evolution；新建记录型算子（map/flatMap/reduce/join/window/cep）的元数据桥接（Deferred 登记）；side-output sink 守卫面（SideOutputRegistry 直连旁路，审查 P6 声明）；StreamModel/XDSL 声明面。

## Execution Plan

### Phase 1 - 抽象落地与测试

Status: completed
Targets: nop-api-core、nop-stream-core、connector-jdbc、nop-message-debezium、测试

- Item Types: `Feature`

- [x] api-core：RowKind + ChangeSemanticView；ChangeEvent 实现视图接口（映射矩阵）
- [x] core：StreamRecord 元数据字段（copy/replace 语义）+ SourceOperator 桥接 + SinkOperator 守卫 + CdcChangeAwareSink + 错误码 + envelope/codec/ChannelState 传输面（审查 P9 方案 A）
- [x] connector-jdbc：JdbcTwoPhaseCommitSink implements CdcChangeAwareSink
- [x] 测试：元数据往返/桥接/守卫矩阵/零回归缺省/ChangeEvent 映射（+16：core 13 + debezium 3）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector-debezium -am` 全绿（CHAIN_EXIT=0）+ nop-message-debezium 模块测试全绿（DEBEZIUM_EXIT=0）
- [x] **零回归**：rowKind=null 缺省下既有全链路行为不变（runtime 全量复跑见 Phase 2；既有 CDC 链路 c/r→INSERT 不触发守卫由守卫矩阵 `guardPassesInsertOnlyRecordsToAnySink` 钉定）
- [x] **无静默跳过**：非 INSERT 记录进非 CDC-aware sink = typed fail-fast（`guardFailsTypedForChangeRecordOnUnawareSink` 断言错误码 + sink 类名 + 未消费）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-08.md` plan 2336 条目）

### Phase 2 - 收口

Status: completed
Targets: owner docs、plan/roadmap

- Item Types: `Proof`

- [x] docs：nop-stream.md「引擎级变更元数据」节
- [x] 独立子 agent closure audit；roadmap WI7 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI7 已勾选、提交完成

## Closure Gates

- [x] 元数据抽象落地且缺省零行为变化（零回归）
- [x] CDC 变更语义迁移：ChangeEvent → 记录元数据桥接 + sink 守卫 typed 面
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-connector-jdbc,nop-stream/nop-stream-connector-debezium -am` 全绿
- [x] **Anti-Hollow Check**：桥接与守卫被运行链路真实消费（operator 桥接测试 + 守卫矩阵测试 + 既有链路零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0（另扫 connector-jdbc）
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 新建记录型算子的元数据桥接（map/flatMap/reduce/join/window/cep/side-output）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本期引擎可见性 = 源桥接（双点）+ 前向传递 + envelope/恢复传输面；新建记录型算子需逐算子裁定元数据映射语义（如聚合输出的 rowKind），无真实 CDC 管线诉求前扩展即投机；baseline/Out-Of-Scope/owner doc 三处已按此边界如实声明
- Successor Required: `no`

### rowKind 驱动 sink DML（元数据作为操作真相源）

- Classification: `watch-only residual`
- Why Not Blocking Closure: R4 的首版目标 = 引擎可见性与守卫面；sink 驱动面存在「value 与 rowKind 双真相源」一致性难题，待生态 sink 出现真实诉求另议
- Successor Required: `no`

## Closure

Status Note: WI7 引擎级变更元数据（RowKind + tableId）落地且缺省零行为变化（runtime 全量 -am 复跑 EXIT=0 零失败，首轮单跑假红为 m2 旧 flow jar 已排除）；CDC 变更语义迁移完成（ChangeEvent 视图实现 + 双桥接点 + sink 守卫 typed 面 + envelope/恢复传输面）；独立 closure audit 七项全 PASS、0 Blocker/0 Major，其 Minor-1（Deferred 悬空引用）与 Minor-2（javadoc/注解漂移）已修复后勾选。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_45e0545d-e180-4287-96be-ab3738e34434），全程只读
- Evidence:
  - 实现 vs plan PASS（七维度逐条 file:line：RowKind 四值/StreamRecord 三路径语义/双桥接/守卫/传输面/ChangeEvent 映射/pom 依赖）
  - 测试真实覆盖 PASS（core 13 + debezium 3 = 16 用例 surefire 实测绿；守卫矩阵断言错误码+类名+未消费）
  - 零回归 PASS（CHAIN_EXIT=0 / DEBEZIUM_EXIT=0 / runtime -am 复跑 RUNTIME_EXIT=0，TestSideOutputPipelineE2E 单跑假红由 -am 复跑排除）
  - owner docs PASS（nop-stream.md 五要素含存活边界如实声明；connectors.md kafka-2pc 注）
  - Anti-Hollow PASS（桥接非死代码：构造点全量 grep 证实三入口 + codec decode；scan-hollow core+connector-jdbc exit 0）
  - Minor 处置：Minor-1 Deferred 悬空引用补条目；Minor-2 javadoc/注解漂移修复；Minor-3（connectors.md 三行未加注）接受——四 sink 守卫清单已在 nop-stream.md 覆盖
  - 提交圈定：仅 WI7 文件（api-core 2 类 + debezium ChangeEvent + core 8 文件 + jdbc 标记 + 测试 5 + docs 2 + plan + roadmap 勾选 + 10-08 日志），sibling survey 不混入

Follow-up:

- 新建记录型算子的元数据桥接（Deferred out-of-scope，见 Deferred 节）
- no remaining plan-owned work
