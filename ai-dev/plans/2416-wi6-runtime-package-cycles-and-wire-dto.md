# 2416 WI6 runtime 包级环打破与 wire DTO 叶子包提取

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI6，Phase B；M1 最后一项）
> Related: ai-dev/plans/2415-wi5-runtime-test-connector-fanin-registered-exception.md

## Purpose

消解 runtime 内的三个包级环（transport↔rpc↔taskmanager、rpc↔coordinator、coordinator↔taskmanager）：把 5 个跨环 wire DTO 提取到零出边叶子包 io.nop.stream.runtime.protocol，四环包共同依赖叶子而互不成环；同批处理 wire codec 重复与 metrics 绕过。行为保持：wire 格式、序列化字节、指标名零变更。

## Current Baseline

- **三个环**（本会话构建的 runtime 全包依赖图实证）：
  1. transport → rpc → taskmanager → transport：SubtaskPlanBuilder.java:21-22 import rpc.{RemotePipelineResolver,TaskDeploymentDescriptor}；IStreamCoordinatorRpcService.java:15-18 import coordinator.{JobStatusResponse,TaskProgress,TaskStatusReport} + taskmanager.CheckpointAckMessage；TaskManager.java:58-60 import transport.{RemoteInputChannel,RemoteResultPartition,SubtaskPlanBuilder}
  2. rpc ↔ coordinator：rpc→coordinator（IStreamCoordinatorRpcService 3 个 DTO import）+ coordinator→rpc（JobCoordinator 等 2 文件 import rpc 服务接口）
  3. coordinator ↔ taskmanager：JobCoordinator.java:56-57 import taskmanager.{CheckpointAckMessage,TaskManager}（**TaskManager import 零代码使用——仅 javadoc 纯文本词匹配，已逐行核验 :65/:74/:84/:105/:445/:2798/:2847 均为注释/字符串**，先删）+ taskmanager→coordinator（TaskManager.java:53-54 import TaskProgress/TaskStatusReport）
- **7 个 wire 类型**（现位置 → 目标 io.nop.stream.runtime.protocol）：rpc/TaskDeploymentDescriptor（13 文件引用）、taskmanager/CheckpointAckMessage（7）、coordinator/TaskProgress（4）、coordinator/TaskStatusReport（7）、coordinator/JobStatusResponse（2）+ **coordinator/JobStatus 枚举**（JobStatusResponse 的同包字段类型——第一轮审查 B1 发现：不移则 protocol→coordinator 残余边成新环，必须随迁）+ **rpc/RemotePipelineSpec**（TaskDeploymentDescriptor 的同包字段类型——执行期发现的第 7 个必随迁项，纯 core 依赖）。类型自身依赖：core 类型（TaskLocation/TaskStateSnapshot/DeploymentPlan/JobGraph）+ runtime.cluster.TaskIdentity（TDD/TSR 两个）——cluster 为零出边叶子，protocol→cluster 向下无环。
- **序列化兼容性结论（经第一轮审查 B2 修正后的准确口径）**：控制面主路径（IMessageService + StreamControlRpcTransformer extends 平台 DefaultRpcMessageTransformer）按参数名转换、接收端按服务签名静态定型，payload 不含 FQN——包迁移对其无影响。**但 launch/PollingJdbcMessageService.java:221-243 存在 Java 序列化 cross-JVM 路径（ObjectOutputStream/ObjectInputStream）**，Java 序列化流内嵌类 FQN：同版本部署单元下两端同类同 serialVersionUID（5 类型均 1L，已核）round-trip 兼容不受影响；跨版本残留风险（旧版本写入 nop_stream_msg_queue 的行经旧 FQN 编码）登记为 watch-only residual。该路径的覆盖测试（multijvm 族）被 @EnabledIfSystemProperty 门控、不在 1377 基线内。surefire 报告中的 FQN 均为日志 toString（非 wire 字节）；全仓源码/配置对旧 FQN 无字符串字面量引用。
- **wire codec 克隆**：transport/KafkaStringWireCodec.java:24,36 自述为 PulsarStringWireCodec 的逐字克隆（「deliberately deferred duplication」；Pulsar final 不可扩展 + Kafka 命名顾虑）。两者 send=JsonTool.stringify(toWireMap(envelope))、fromWire=parseBeanFromText(jsonObjectToBean)。
- **metrics 绕过 12 处**（修正 roadmap 路径：ChannelQueueGauges/DbBacklogGauges 实在 transport/，TaskCheckpointWiring 实在 execution/）：TaskManager.java:306,311,563,751、JobCoordinator.java:1921、transport/ChannelQueueGauges.java:105,113、transport/DbBacklogGauges.java:52、operators/windowing/WindowOperator.java:622、OverWindowOperator.java:128、execution/TaskCheckpointWiring.java:100、ops/StreamMetricsReporter.java:110。形态：包装层方法（TaskNodeMetrics.registerRunningGauge 等）要求调用点显式传 `StreamMetricsRegistries.registry()`，或调用点直接 `registry().counter(...)`。
- 测试基线（WI5 重钉）：runtime surefire **1377 run / 0 failed / 10 skipped**（Docker 不可用环境；Docker 可用时 skipped=8）+ 测试树 361 java 文件。本 WI 零测试改动，复跑须等值。

## Goals

- io.nop.stream.runtime.protocol 零出边叶子包承载 5 个 wire DTO；四环包间无环（import 扫描脚本判定）。
- JobCoordinator 的死 TaskManager import 删除。
- Kafka/Pulsar StringWireCodec 抽共同基类，克隆面消除。
- 12 处 metrics 绕过收口：调用点零 `StreamMetricsRegistries.registry()` 直接引用（包装层自持 registry 访问）。
- runtime 测试与重钉基线等值（1377/10 skipped，Docker 不可用环境）。

## Non-Goals

- 不动 JobCoordinator/TaskManager 等巨型类的职责结构（RD-05 对象）。
- 不改 RPC 服务接口签名与服务实现位置（TaskDeploymentDescriptor 等 DTO 迁走后接口方法签名经 import 更新，方法集不变）。
- 不动 wire 编码格式与指标名（E1 行为保持）。
- protocol 包不加 package-info 以外的任何逻辑类。

## Scope

### In Scope

- 新包 io.nop.stream.runtime.protocol：5 个 DTO 文件迁移（git mv + package/import 改写）+ package-info.java（边界说明：跨 JVM wire 契约类型，零出边依赖 cluster/core，禁止反向 import 四环包）。
- 四环包 + execution/launch/ops/maintain 等全部引用点的 import 改写（预计 ~30 文件）。
- JobCoordinator.java:57 死 import 删除。
- transport/：KafkaStringWireCodec + PulsarStringWireCodec 抽共同抽象基类（AbstractStringWireCodec：toWire/fromWire 模板 + extractData），两子类仅保留 backend 特异注释与常量。
- metrics（两级收口规则，经审查 M2/M3 修订）：(i) 既有 register* 静态方法（TaskNodeMetrics.registerRunningGauge/registerCommitQueueDepthGauge、EngineMetrics.registerNodesActiveGauge）**新增无 registry 参数重载**（内部自取 registry）——保留显式 registry 变体（TestEngineAndTaskNodeMetrics/TestMetricsLifecycle/TestStreamOpsHttpServer 以自定义 SimpleMeterRegistry 做隔离断言，删参会破坏测试与隔离语义）；(ii) runtime.metrics 新增统一门面（final class 静态 registry 访问器），供无既有包装形态的调用点（MicrometerStreamTaskMetrics 构造、per-edge gauge、scrape 读路径、算子级 counter）取得 registry——包装层保持 registry 来源唯一所有权。
- 一次性 import 环扫描脚本（ai-dev/tools/ 下登记为可复跑工具或 _tmp 一次性脚本 + 结果留档；WI12 固化为 CI 检查）。

### Out Of Scope

- DTO 字段/方法变更；RPC 接口语义变更；测试文件内容变更（import 语句随 DTO 迁移需机械更新的测试文件除外——属引用改写非测试逻辑变更）。

## Execution Plan

### Phase 1 - 叶子包提取与环消除

Status: completed
Targets: `io.nop.stream.runtime.protocol`（新）、四环包、全部引用点

- Item Types: `Fix`（包级环为已确认结构缺陷）；死 import 删除为 `Fix`

- [x] 删 JobCoordinator.java:57 死 TaskManager import（编译+测试立证零行为影响）
- [x] git mv 7 个 wire 类型至 protocol 包（package 声明 + 全部引用点 import 改写，main 与 test 两棵树）
- [x] protocol/package-info.java 边界说明
- [x] import 环扫描脚本跑四环包：判定无环并留档「仍存在边」对照表（预期剩余：transport→rpc 单向、taskmanager→rpc/transport、coordinator→rpc/checkpoint/cluster、rpc→cluster——均为 DAG 向下边）；**扫描含内联 FQN**（JobCoordinator:440/2836/2840、AssignmentPlanner:110 的 RemotePipelineSpec——执行期裁定随迁 protocol 后内联 FQN 已同步改写，非保留项）
- [x] 迁移 javadoc 断链修整：TaskDeploymentDescriptor 的 {@link IStreamTaskRpcService}/{@link StreamTopicNaming}、TaskManager:585、cluster/JdbcClusterRegistry:39 等跨包 {@link 改为文字形式（仅注释）
- [x] **新增 wire 兼容回归测试**（TestWireDtoMigrationCompat 之类，随本 WI 交付）：对 7 个迁移类型逐一断言（Java 序列化路径覆盖 5 个 Serializable DTO + JobStatusResponse；JSON 路径覆盖 @DataBean 形态；JobStatus 枚举全值） (a) Java 序列化 round-trip 相等（钉 PollingJdbcMessageService 路径的同版本兼容）；(b) nop JSON round-trip 相等（@DataBean 字段编码形态，钉控制面 JSON 路径的形状不变）
- [x] 既有控制面/分发面测试全绿：TestStreamControlRpc、TestRpcDistributedExecutorE2E（注意口径：这些测试经 LocalMessageService 对象直递，证的是 transformer/reflect 分发面而非 wire 字节；字节面由上一条新测试钉住）

### Phase 2 - codec 基类与 metrics 收口

Status: completed
Targets: transport/ 两个 codec、metrics 包装层、12 处调用点

- Item Types: `Fix`（登记在案的重复与绕过）

- [x] AbstractStringWireCodec 提取（toWire/fromWire/extractData 模板），Kafka/Pulsar 子类化（两子类保持 final + INSTANCE 单例形态；行为钉 = 既有 TestKafkaStringWireCodec/TestPulsarStringWireCodec 的 round-trip + 固定 JSON 样例断言零改动通过）
- [x] metrics 包装层改造：register* 方法内部自取 registry；新增调用点所需包装入口
- [x] 10 处调用点改写（清单见 Current Baseline，**剔除 operators/windowing 2 处**——WindowOperator:622、OverWindowOperator:128：该包今日对 runtime 零 import（WI7 搬迁前提），本 WI 若引入 runtime.metrics 依赖，WI7 归位 core 后将形成 core→runtime 反向依赖；裁定顺延至 WI7 随 core 侧方案收口，roadmap WI6 条目括注登记）
- [x] 收口钉：runtime main 树 `StreamMetricsRegistries` 直接引用仅剩 metrics/ 包装层自身（4 处合法）+ operators/windowing 2 处（WI7 对象，已登记）

Exit Criteria:

- [x] **无环判定**：import 扫描脚本输出四环包间无环（transport→rpc 单向边允许），结果留档
- [x] **行为保持**：`./mvnw -o test -pl nop-stream/nop-stream-runtime` BUILD SUCCESS 且 Tests run = 1380 / Failed 0 / Skipped 10（= WI5 重钉基线 1377 全数保持 + 本 WI 新增 wire 兼容钉 3——计划 B2/M1 修订项新增的 TestWireDtoPackageMigrationCompat；Skipped 环境限定同 WI5）
- [x] **wire 格式保持**：5 个 DTO 字段集与 @DataBean 注解零变更（git diff 字段级核对）；codec 提取后 Kafka/Pulsar 两 codec 的 wire 输出与提取前逐字节一致（既有 codec 相关测试覆盖）
- [x] protocol 包零出边（import 扫描：6 个迁移类型对 {cluster, core, java} 之外无依赖；protocol 包内无逻辑类新增——JobStatus 枚举为既有类型迁移）
- [x] 旧位置 import 零命中：`grep -rn "import io.nop.stream.runtime.rpc.TaskDeploymentDescriptor\|import io.nop.stream.runtime.taskmanager.CheckpointAckMessage\|import io.nop.stream.runtime.coordinator.(TaskProgress|TaskStatusReport|JobStatusResponse|JobStatus);" nop-stream/nop-stream-runtime/src -E` 零命中；同包用户（rpc/IStreamTaskRpcService、rpc/RemotePipelineSpec、taskmanager/CheckpointAckSender、coordinator 包内 3 DTO 的用户）无旧 import 可改，由编译兜底（旧类文件已 mv 走）
- [x] metrics 收口钉：runtime main 树 StreamMetricsRegistries 直接引用仅 metrics/ 包装层（+ operators/windowing 2 处 WI7 顺延项）；新重载与显式 registry 变体并存且既有 metrics 测试零改动全绿
- [x] No new test required: 行为保持型结构重构（E1：既有测试基线为钉）；环消除与 DTO 迁移无新增行为面
- [x] No owner-doc update required（结构内聚变更；protocol 新包的对外说明在 package-info + 基线文档 WI12 终稿时随依赖方向表更新——WI3 已登记的测试侧 fan-in 行与本 WI 无涉）
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI6 条目

## Closure Gates

- [x] 四环包无环（扫描脚本留档）
- [x] runtime 测试与 WI5 基线等值（1377/skipped 环境限定）
- [x] wire DTO 字段与 codec 输出行为保持
- [x] metrics 收口钉成立（包装层外零直接引用）
- [x] 无 in-scope live defect 被降级为 follow-up
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2416-wi6-runtime-package-cycles-and-wire-dto.md --strict` 退出码 0

## Closure

Status Note: 全部 8 项审计 PASS：四条禁边（rpc→taskmanager/coordinator、coordinator→taskmanager、taskmanager→coordinator）import 级零命中、protocol 零出边钉成立、7 类型迁移字段/serialVersionUID 零变更、wire 兼容钉审计者实跑 3/3 绿、codec 测试与 metrics 测试零改动、基线 1380/0/10 精确吻合、解析器 12 项 6 done。计划文本的「6 类型」滞后口径已按审计建议改齐为 7。附随修复：EquiJoinOperator.hasPendingRestoredBuffer 可见性恢复（merge e7534f088e 回退了 plan 2389 已提交的 public 形态致 HEAD 测试编译破损——语义为恢复既有已审契约，仅可见性零行为变更，归属已在 10-11 日志补记）。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_d2804b9f-3dd9-4732-b99d-ac82df95792c
- Evidence:
  - 环消除：import 口径实扫，四条禁边 0 命中；protocol import 仅 cluster/core/api.core/java；全图 DAG（审计者独立建图）
  - 迁移完整性：protocol/ 恰 7 类型 + package-info；旧位置 7 文件 RM；旧 import 与旧 FQN 字符串全 src 0 命中
  - wire 兼容钉：审计者实跑 TestWireDtoPackageMigrationCompat = 3/0/0 BUILD SUCCESS
  - codec：AbstractStringWireCodec + 两 final 子类仅 INSTANCE；TestKafka/TestPulsarStringWireCodec git 零改动
  - metrics 钉：registry() 访问器 main 树恰剩 windowing 2 处（WI7 顺延）；三组无 registry 重载在位；三个 metrics 测试零改动
  - 基线：surefire-reports 聚合 1380/0/10（320 报告文件）；解析器 12 项 6 done；WI6/M1 行 done
  - 行为保持：7 类型 diff 仅 package 行 + javadoc；6 个 serialVersionUID 保留
  - Deferred 分类检查：windowing 2 处（WI7 承接）/跨版本队列残留/单次 flake/WI12 CI 固化——均已裁定 non-blocking，无 in-scope live defect 滞留

Follow-up:

- no remaining plan-owned work
