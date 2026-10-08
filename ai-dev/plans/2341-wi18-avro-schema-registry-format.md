# 2341 WI18 Avro/Schema Registry 格式层（Confluent wire format，registry 外置）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI18（M5）+ 裁定 §一 P2 #17（format 层接入外部 Schema Registry + Avro 序列化，不内置 registry 服务）
> Related: plan 2335（connector-kafka 的 Function<byte[],T>/Function<T,byte[]> 注入面）

## Purpose

交付 Avro 格式层：以 Confluent wire format（magic byte 0x0 + 4 字节 schemaId + Avro binary）为默认线格式，schema 经外部 Schema Registry REST API 注册/解析（registry 服务本身不内置）；为既有 connector 的 `Function<byte[],T>` / `Function<T,byte[]>` 注入面提供生产级 Avro 实现与工厂方法。

## Current Baseline（2026-10-08 研究核实）

- **注入面已就绪**：KafkaSourceFunction 持 `Function<byte[],T> valueDeserializer`（:88，null 拒绝，默认 utf8String）；KafkaTwoPhaseCommitSink 持 `Function<T,byte[]> valueSerializer`（:86）。两工厂以 OBJECT 参数程序化注入。JdbcTwoPhaseCommitSink 走 recordMapper Map 面（非 bytes 目标）。
- **零先例**：全仓无 avro 依赖、无 Confluent Schema Registry 客户端、无 SerializationSchema/DeserializationSchema 抽象、无 nop-avro 模块。版本单点先例 = nop-dependencies 属性（kafka.version 同款）。
- **wire 先例**：`nop.stream.io.emit.time` 等指标命名无关；IDataPlaneWireCodec 是 runtime 数据面插拔点（本期不动——format 层作用于 connector 的 value 编解码，非 envelope 层）。

## Goals

- **新模块 `nop-stream-format-avro`**（parent nop-stream）：
  - 依赖：`org.apache.avro:avro`（版本属性 `avro.version` 进 nop-dependencies 单点，1.11.x 线）+ KafkaSourceFunction 所需的 kafka-clients（provided/optional，仅类型引用）。
  - `AvroSchemaRegistryClient`（JDK HttpClient 最小 REST 客户端，零 confluent 依赖）：`registerSchema(subject, schema)` → schemaId、`getSchemaById(id)` → Schema、`getLatestSchema(subject)` → (id, Schema)；Confluent REST API 路径（`/subjects/{subject}/versions`、`/schemas/ids/{id}`）。
  - `AvroWireFormat`：encode(Object record/GenericRecord, subject) → Confluent wire bytes（0x0 + schemaId BE + BinaryEncoder）；decode(byte[], subject) → GenericRecord（writer schema by id + reader schema 解析）；schema 缓存（id→Schema、subject→latest id+schema）。
  - 工厂方法：`avroDeserializer(subject)` → `Function<byte[],GenericRecord>`、`avroSerializer(subject)` → `Function<GenericRecord,byte[]>`，直接可注入 KafkaSourceFunction/KafkaTwoPhaseCommitSink。
- **测试**：内嵌 fake registry（JDK HttpServer 模拟 REST 面不上网）；wire format 往返（register→encode→decode 字段断言）；schema 演进（writer/reader schema 兼容解析）；缓存命中；typed 错误面（registry 不可达/非 Avro payload magic byte 错）。
- docs：connectors.md 或 user-guide 格式层节。

## Non-Goals

- 内置 Schema Registry 服务；Avro 代码生成（SpecificRecord——GenericRecord 首版）；非 Confluent wire format（自研 schema 手动注入支持留 follow-up）；Kafka Connect 集成。

## Scope

### In Scope

- 新模块 nop-stream-format-avro（client + wire format + 工厂 + 测试）+ nop-stream pom modules + nop-dependencies 版本单点。
- docs。

### Out Of Scope

- registry 服务本体；SpecificRecord；其它 wire format。

## Execution Plan

### Phase 1 - 模块与实现

Status: completed
Targets: nop-stream-format-avro、nop-stream/pom.xml、nop-dependencies

- Item Types: `Feature + Proof`

- [x] 模块骨架 + avro 版本属性
- [x] AvroSchemaRegistryClient + AvroWireFormat + 工厂
- [x] 测试九面（fake registry + wire 往返 + 演进 + 缓存 + typed 错误）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-format-avro -am` 全绿
- [x] **接线验证**：工厂产物可直接注入 KafkaSourceFunction valueDeserializer / KafkaTwoPhaseCommitSink valueSerializer（类型面编译证明或单测证明）
- [x] **无静默跳过**：registry 不可达/magic byte 错/schema 不兼容 typed 拒绝
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: docs、plan/roadmap

- Item Types: `Proof`

- [x] docs：格式层文档
- [x] 独立子 agent closure audit；roadmap WI18 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI18 已勾选、提交完成

## Closure Gates

- [x] wire format 往返 + 演进解析全绿（补审计复跑 6/6）
- [x] `./mvnw test -pl nop-stream/nop-stream-format-avro -am` 全绿（6/6；avro pin 后复跑绿）
- [x] **Anti-Hollow Check**：registry REST 调用被 fake server 真实消费（非 stub 内存短路）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-format-avro --severity high` 退出码 0（补审计复跑 0 findings）
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_57190ab9，2026-10-08 补做——见 Closure 节修订）

## Deferred But Adjudicated

### SpecificRecord 代码生成支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: GenericRecord 首版覆盖动态 schema 场景；SpecificRecord 需代码生成流水线（avro-maven-plugin），无静态 schema 用户前不做
- Successor Required: `no`

## Non-Blocking Follow-ups

- 同 subject 多 schema 演进时 encoder 侧 schemaId 缓存不自动拾取新版本（补审计 M3 已知边界，README/docs 声明在位）——真实多 schema 写入诉求出现时升级缓存策略
- kafka-clients provided/optional 依赖未引入（补审计 M2 无害偏差——工厂面零 kafka 类型）

## Closure

Status Note: WI18 Avro/Schema Registry 格式层落地：新模块 nop-stream-format-avro（avro 版本单点在 nop-dependencies `avro.version`=1.12.1——原计划 1.11.3，补审计 B2 修正为实测基线 pin；零 Confluent 依赖——JDK HttpClient 最小 REST 客户端 + Confluent wire format codec）；encode→decode round trip / schema 演进 v1→v2 backward-compat / schemaId 缓存 / magic byte 与 registry 不可达 typed 拒绝 / 工厂注入面全部测试覆盖（6/6 绿）。**2026-10-08 补审计修订**（agent_57190ab9 发现 B1/B2）：B2 版本 pin 原落在错误工作区未提交——本分支补 pin（1.12.1 实测基线）；B1 格式层文档补落 connectors.md「格式层」节。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_57190ab9-bbfa-452f-836c-2a88aa38c2d8，2026-10-08 补做）
- 补审计发现与处置：B1 格式层文档未落 docs-for-ai → connectors.md「格式层（Avro / Schema Registry，WI18）」节补齐；B2 avro 版本单点 pin 落错工作区且宣称 1.11.3 失实 → 本分支 nop-dependencies 补 pin（avro.version=1.12.1 实测基线，javadoc 注明升版核对项）；M1 先勾后做流程瑕疵 → 本节回写理顺；M2 kafka-clients 依赖偏差 → 无害偏差记 Follow-up；M3 同 subject 换 schema 的 schemaId 缓存边界 → README/docs 已知边界声明 + Follow-up
- Evidence:
  - wire format PASS：TestAvroWireFormat 6/6（round trip / 演进 / 缓存 / typed 错误 / 工厂）
  - build PASS：`./mvnw test -pl nop-stream/nop-stream-format-avro -am` BUILD SUCCESS
  - Anti-Hollow PASS：fake registry (JDK HttpServer) 真实消费 REST 调用（非 stub 内存短路）
  - 提交圈定：仅 WI18 文件（新模块 + 双 pom 单点 + docs + plan + roadmap 勾选 + 10-08 日志），sibling survey 不混入

Follow-up:

- SpecificRecord 代码生成支持（Deferred 已登记）
- no remaining plan-owned work
