# 2339 WI13 checkpoint 对象存储后端（S3 兼容，storageType=s3）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI13（M3）+ 裁定 §一 P1 #15（对齐既有 ICheckpointStorage SPI 形态）
> Related: `ai-dev/design/nop-stream/checkpoint-design.md`（Object Storage 原子发布规则：先写 segment，manifest 唯一提交点）；plan 2335（gated 容器 E2E 骨架）

## Purpose

交付 S3 兼容对象存储的 checkpoint 后端（storageType=`s3`），对齐既有 LocalFile/Jdbc 双先例的 ICheckpointStorage SPI 形态，使 checkpoint/manifest/savepoint 可落 S3 兼容对象存储（含 MinIO 等 endpoint 覆盖形态）。

## Current Baseline（2026-10-08 研究核实 + 对抗性审查修订）

- **SPI**：`ICheckpointStorage`（core checkpoint/storage）方法集：storeCheckPoint/getLatestCheckpoint/getAllCheckpoints/getLatestCheckpoints/deleteCheckpoint/deleteAllCheckpoints/getName/exists(default)/getCheckpointCount/storeSavepoint/loadSavepoint/loadSavepointMetadata/storeEpochManifest/loadLatestEpochManifest/loadRetainedEpochManifests(default)/pruneEpochManifests(default no-op，生产实现 MUST 覆写)。
- **双先例**：LocalFileCheckpointStorage（布局 `{jobId}/{pipelineId}/{checkpointId}.checkpoint` + `{epochId}.epoch` + savepoint 目录；.tmp+ATOMIC_MOVE；id 白名单；覆写 loadRetainedEpochManifests/prune）与 JdbcCheckpointStorage（两表 BLOB；deleteAll 清两平面；覆写 exists/prune）。
- **SerDe**：整个 checkpoint = 单个 JSON 文档 bytes（CheckpointSerDe.serializeCheckpoint，v1/v2 信封 + checksum）；manifest 同为 JSON。一个 (job,pipeline,checkpointId) = 一个对象。
- **路由**：`GraphModelCheckpointExecutor.createStorage`（:921-938）——`jdbc` 抛错占位；`local` 走 path 属性。路由测试 TestE2EStorageTypeRouting。**审查 Mj1 修订：createStorage 非唯一实例化点**——`EmbeddedDistributedExecutor:260`、`RpcDistributedExecutor:221` 硬编码 LocalFile，`prepareSavepointRuntime`（:374-376）overrideStoragePath 也强制 LocalFile。本期裁定：s3 先覆盖 createStorage 直连入口（嵌入式/单进程主形态），三处分布式/savepoint 路径的 s3 化记 Deferred（各自需要 config 透传改造，无真实部署诉求前不动）；文档如实声明边界。
- **auto-restore gate（审查 B1 修订）**：`executeWithCheckpointSkeleton` 的恢复意图判定 `hasExplicitStoragePath`（:962-967）只认 `storageProperty("path")`——s3 配 `s3.bucket` 不会触发 auto-restore。**本期扩展判定**：storageType=s3 且 `s3.bucket` 已配置 = 显式恢复意图（镜像 local「显式 path = 显式意图」裁定），并加走 executor 真实入口的 s3 恢复证明（防「组件绿、生产断」hollow）。
- ** SPI 语义钉定（审查 Mj2/Mj3/Mn1/Mn2/Mn3）**：getLatestCheckpoint/latest 系 = **key 解析 checkpointId 取 max**（镜像 local 零 GET，不用 completedTimestamp）；`loadRetainedEpochManifests` MUST 覆写（镜像 local 保留集，漏覆写 = SharedStateRegistry 引用计数静默退化——Jdbc 曾修复同款）；`pruneEpochManifests` 覆写；`getName()` 返回类名 `S3CheckpointStorage`（对齐双先例）；getCheckpointCount=list 前缀计数 .checkpoint 后缀不反序列化；getAllCheckpoints/getLatestCheckpoints=列前缀全量反序列化按 checkpointId 降序；savepoint=两个 putObject（bytes 等价 local 重序列化）返回 prefix 字符串，load 校验 prefix 包含于 basePrefix（镜像 local validatePath 安全面）。
- **S3 客户端先例**：`nop-integration-oss` 用 `com.amazonaws:aws-java-sdk-s3`（版本单点 nop-dependencies L97 =1.12.638，dependencyManagement L394-398）；endpoint 可覆盖即 S3 兼容；pathStyleAccess 默认 true（MinIO 形态）。nop-stream 内零 S3 依赖。
- **测试先例**：TestJobCoordinatorFailoverRestore（fresh coordinator + JdbcCheckpointStorage 恢复 durable epoch）；TestKafkaConnectorExactlyOnceE2E（gated 容器骨架：assumeTrue + opt-out + registry 前缀三属性）；runtime pom testcontainers-bom 2.0.5。

## Goals

- **runtime 新类 `S3CheckpointStorage`**（runtime/checkpoint/storage，实现 ICheckpointStorage）：
  - 构造 `(AmazonS3 client, String bucket)`（`com.amazonaws.services.s3.AmazonS3` 接口面——测试可注入内存 fake）；static `fromConfig(CheckpointConfig)` 读 storageProperties：`s3.endpoint` / `s3.bucket` / `s3.accessKey` / `s3.secretKey` / `s3.region` / `s3.pathStyleAccess`（缺省 true）——缺 bucket 或 endpoint 时 typed 拒绝。
  - 对象布局镜像 local：`{basePrefix}/{jobId}/{pipelineId}/{checkpointId}.checkpoint` / `{epochId}.epoch` / savepoint 对象对（.checkpoint+.metadata）；basePrefix 可配（缺省 `nop-stream-checkpoints`）。key 白名单校验同 local（防 key 注入）。
  - 方法映射：storeCheckPoint=putObject(JSON bytes)；getLatestCheckpoint/getLatestCheckpoints=getAllCheckpoints 后按 key 解析 checkpointId max/降序（零 GET 镜像 local）；deleteCheckpoint/deleteAllCheckpoints=逐 key delete（checkpoint+manifest 两平面）；savepoint=两个 putObject 返回 prefix；pruneEpochManifests+loadRetainedEpochManifests 均覆写。写侧单 putObject 即发布点（S3 同 key 覆盖原子语义）。SDK 细节（审查 Mn4）：putObject 用 bytes+ObjectMetadata content-length；getObject 后 S3Object 必须关闭（连接泄漏）；listObjectsV2 对 isTruncated 出 WARN（单页裁定的可见性）。
  - `getName()`=`S3CheckpointStorage`（类名对齐双先例）。
- **路由**：createStorage 增 `s3` 分支（`S3CheckpointStorage.fromConfig(config)`）；jdbc/local 行为不变；TestE2EStorageTypeRouting 扩 s3 断言（含缺 bucket typed 拒绝）。
- **依赖**：runtime pom 增 `aws-java-sdk-s3`（compile 无版本号走 nop-dependencies 单点；nop-integration-oss 同款先例）。fake 用 SDK 自带 `AbstractAmazonS3`（1.12.638 已验证存在），只覆写用到的最小方法面。
- 测试：
  - **内存 S3 fake**（test 面）：`InMemoryAmazonS3 extends AbstractAmazonS3`——覆写最小方法面（putObject/getObject/deleteObject/listObjectsV2/doesObjectExist），内部 ConcurrentHashMap 模拟桶。用于不经网络的全部存储行为单测（save/load round-trip、latest 按 checkpointId max、delete 两平面、savepoint、prune、key 白名单、exists）。
  - **gated 容器 E2E**（MinIO GenericContainer，docker+opt-out+镜像属性三属性照 KafkaBrokerE2ETest 惯例、属性名自建 `nop.test.stream.e2e.registry`；**先 createBucket——MinIO 不自动建桶**；MINIO_ROOT_PASSWORD ≥8 字符）：fromConfig→真存储 round trip + **走 executor 真实入口的恢复证明**（executeWithCheckpoint 两轮同 jobId——round1 写 checkpoint 后释放，round2 同 config 新 executor 经 s3 感知的 auto-restore gate 恢复，断言状态延续）。
- docs：connectors.md（或 nop-stream.md checkpoint 节）storageType=s3 配置面。

## Non-Goals

- Segment store 的 S3 实现（ISegmentStore 对象存储版留 RocksDB 大状态场景触发时另立）；分页列举的 token 优化（首版单页足够，list 上限内）；aws sdk v2 迁移；其它对象存储（Azure/GCS）；JDBC 分支的实现（维持抛错占位）。

## Scope

### In Scope

- S3CheckpointStorage + fromConfig + createStorage s3 分支 + 路由测试扩展。
- InMemoryAmazonS3 fake + 存储行为单测 + gated MinIO E2E。
- runtime pom aws sdk 依赖 + docs。

### Out Of Scope

- S3 SegmentStore；sdk v2；其它云存储；跨进程聚合。

## Execution Plan

### Phase 1 - 存储实现与测试

Status: completed
Targets: runtime checkpoint/storage、GraphModelCheckpointExecutor、pom、测试

- Item Types: `Feature + Proof`

- [x] S3CheckpointStorage（全 SPI 方法映射 + fromConfig + key 校验 + prune/loadRetainedEpochManifests/exists 覆写 + latest 按 checkpointId max）
- [x] createStorage s3 分支 + hasExplicitStoragePath s3 感知（B1）+ TestE2EStorageTypeRouting 扩展（s3 成功路由 + 缺 bucket typed 拒绝）——7/7 绿
- [x] InMemoryAmazonS3 fake + 存储行为单测（round-trip/latest/delete/savepoint/prune/白名单）——6/6 绿
- [x] runtime pom aws-java-sdk-s3 依赖（无版本号单点）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（全量链见 Phase 2）
- [x] **接线验证**：路由测试证明 storageType=s3 从 config 真实产出 S3CheckpointStorage（非孤岛类）+ hasExplicitStoragePath 识别 bucket
- [x] **无静默跳过**：prune + loadRetainedEpochManifests 双覆写（保留集/淘汰断言）；缺 bucket/endpoint typed 拒绝；key 注入 typed 拒绝；secretKey 不进日志/异常（无任何回显点）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-08.md` plan 2339 条目）

### Phase 2 - 容器 E2E 与收口

Status: completed
Targets: gated E2E、docs、plan/roadmap

- Item Types: `Feature + Proof`

- [x] MinIO gated E2E（真 S3 兼容存储 round trip + coordinator 恢复）
- [x] docs：storageType=s3 配置面落 nop-stream-user-guide.md 配置表（审查 Mn8 权威位置）+ nop-stream.md 路由行
- [x] 独立子 agent closure audit；roadmap WI13 勾选；git commit

Exit Criteria:

- [x] **端到端验证**：gated E2E docker 可达时真实绿（round trip + fresh coordinator restore）；SKIP 仅在 docker 不可达/镜像不可拉时可见发生
- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI13 已勾选、提交完成

## Closure Gates

- [x] S3 存储全 SPI 方法落地且行为单测全绿（内存 fake 面）
- [x] gated 容器 E2E 真跑绿（或 SKIP 可见+记录）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（全量链见 Phase 2）
- [x] **Anti-Hollow Check**：s3 路由→实例化→存储行为被测试真实消费
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### S3 SegmentStore（RocksDB 增量共享态）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: ISegmentStore 的对象存储版只在 RocksDB 增量 + 多进程共享态场景有价值，Memory 后端（本 roadmap 基线）不经 segment 面
- Successor Required: `no`

## Non-Blocking Follow-ups

- （closure 时填写）

## Closure

Status Note: WI13 S3 兼容 checkpoint 后端落地：全 SPI 方法镜像 local 语义（latest=checkpointId max 零 GET、retained+prune 双覆写、key 白名单、savepoint prefix 安全面 + 缺席返 null）；createStorage s3 分支 + hasExplicitStoragePath bucket 感知（B1 修复：auto-restore 意图标记）；内存 fake 存储行为单测 6/6 + 路由 7/7 + gated MinIO E2E 真跑绿（2.4s 非跳过）；审查 1B/3M/8Mn 全处置（B1 gate 扩展/Mj1 边界声明+Deferred/Mj2 语义纠偏/Mj3 双覆写/Mn1-Mn8）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_1e838c8a-64d4-4b47-9e5e-c8068b8053aa 只读审查 + 本收口轮处置），审查 1B/3M/8Mn 全部落 plan 后实现
- Evidence:
  - SPI 全方法映射 PASS（对照 ICheckpointStorage 16 方法 + 双先例语义逐条）
  - 路由 PASS（TestE2EStorageTypeRouting 7/7：s3 路由产出 S3CheckpointStorage + 缺 bucket typed + hasExplicitStoragePath bucket 感知）
  - E2E PASS（TestS3CheckpointStorageMinioE2E 真跑绿 2.4s：真 MinIO round trip + fresh coordinator restore + state 断言；镜像默认本地 pinned tag 零拉取——远端拉取超时致 surefire fork 超时的执行发现已记录）
  - 内存 fake 单测 PASS（TestS3CheckpointStorage 6/6：round-trip/latest/delete 两平面/savepoint/prune/白名单/absent null）
  - 全量链 EXIT=0（runtime -am 全量 0F/0E）
  - secretKey 不回显核实（代码零回显点）；prune/retained 双覆写 + key 白名单测试断言
  - 提交圈定：仅 WI13 文件（runtime S3 类+fake+2 测试+executor 双方法+pom+user-guide + plan + roadmap 勾选 + 10-08 日志），sibling survey 不混入

Follow-up:

- MinIO 镜像默认用本地 pinned tag（远端拉取超时实测），拉取可经 -Dnop.test.stream.e2e.minio.image 覆盖
- 分布式执行器/savepoint override 的 s3 化（Deferred 已登记）
- no remaining plan-owned work
