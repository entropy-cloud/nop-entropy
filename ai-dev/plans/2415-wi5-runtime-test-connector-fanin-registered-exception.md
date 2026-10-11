# 2414 WI5 runtime 测试侧 connector 反向 fan-in 收敛

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI5，Phase B）
> Related: ai-dev/plans/2413-wi4-connector-jdbc-debezium-dependency-hardening.md

## Purpose

runtime 模块的 test classpath 以 test 作用域依赖全部 5 个 connector + rocksdb——编译面成立的 AR-2 意图（引擎不依赖具体 connector）在测试面被完整 reintroduce，且 pom 注释自述的「ALL FOUR」已过时。本 WI 按 plan 阶段裁定收敛处置：承认测试面 fan-in 为**显式登记的例外**并收敛到最小集，重写 pom 注释为逐依赖消费者普查表，重钉 runtime 测试基线（WI6/WI7/WI10 引用）。

## Current Baseline

- `nop-stream/nop-stream-runtime/pom.xml` test 作用域依赖：connector-debezium（:96-101）、connector-batch（:102-107）、connector-kafka（:110-114）、rocksdb（:143-148）、connector（:207-212）、connector-jdbc（:217-222）。:88-95 注释块自述「ALL FOUR (connector, connector-jdbc, debezium, batch)」——**已过时**（现 5 个 connector，kafka 缺席于该清单）。
- 消费者普查（本会话 grep 实证，与 roadmap v1.2 一致）：
  - connector-jdbc ← TestE2EJdbcTwoPhaseCommitSink、TestCheckpointInheritedTwoPhaseCommitRestore、TestRecalculateBackfillE2E（3 文件）
  - connector-batch ← TestBatchLoaderSourceE2E、TestRecalculateBackfillE2E（2 文件；另 TestWi19BoundedReplayDeclarationE2E:87 以 FQN 形式引用 BatchLoaderSourceFunction）
  - connector（file sink）← TestFileSourceE2E、TestSourceApiRestoreE2E（2 文件）
  - connector 根包 ← TestMessageSourceE2E、TestRateLimitValidationFace（2 文件）
  - connector-kafka ← TestKafkaConnectorExactlyOnceE2E（1 文件，gated real-broker kill-recover）
  - **合计 9 个唯一直接 import 文件 + 1 个 FQN 引用文件**
- **debezium 依赖不可收掉的实证**：TestStreamConnectorRegistryDiscovery（P-REQ-28 聚合注册验收记录）经 registry 发现断言完整集合——:93 断言 discovered beans 含 connector-debezium.beans.xml、:106 断言 source 类型含 debezium-cdc、:110 断言全集恰 10 个 connector。移除依赖必须削弱该验收断言（违反 Bug Fix Test Coverage Rule 的「不弱化既有覆盖」），故 **debezium 依赖保留**。
- **rocksdb 依赖有真实消费面**（推翻 roadmap「确认无消费者的 rocksdb」候选前提）：runtime 测试树 **9 个文件**使用（8 个直接 import RocksDBStateBackend：TestE2EWindowAggregateRestore、TestCheckpointCoordinatorIncrementalIntegration、TestCheckpointCoordinatorJdbcRetainedManifests、TestStateMigrationEndToEnd、TestCheckpointCoordinatorIncrementalPersistRollback、TestCheckpointInheritedSegmentRefCount、integration/TestKeyGroupRoutingE2E、integration/TestRocksDBStateBackendE2E + TestE2EWindowAggregateRestore 内联 FQN；注：TestStreamModelFingerprintRecoveryCompat 对 rocksdb 的唯一提及是 javadoc 引用另一测试类名，非消费者）。**rocksdb 保留**。
- runtime 测试基线（本 WI 重钉值，后续 WI 引用）：**surefire 1377 run / 0 failed / 10 skipped（BUILD SUCCESS）；测试树 361 个 java 文件（320 个 Test* 前缀类 + 1 个 *Test 后缀类 + 40 辅助类；统计命令 `find nop-stream/nop-stream-runtime/src/test/java -name '*.java' | wc -l` 与 `-name 'Test*.java' | wc -l`）**。**测量环境前提**：skipped=10 在 Docker daemon 不可用（或显式 opt-out）环境下测得——TestKafkaConnectorExactlyOnceE2E 与 TestS3CheckpointStorageMinioE2E 为 `assumeTrue(DOCKER_AVAILABLE ...)` 门控，Docker 可用时二者真实运行、skipped=8；1377 总数含 skipped、环境稳定。
- 每个 test 依赖的既有 pom 注释已记录历史动因（kafka ← WI3 plan 2335、jdbc ← Stage 52、connector ← Stage 49 等）。

## Goals

- pom 注释重写：修正「ALL FOUR」，以逐依赖消费者普查表显式登记 AR-2 test-scope 例外（例外范围 = 6 个 test 依赖全部保留，各附消费者与动因）。
- 重钉 runtime 测试基线并写入 roadmap（WI6/WI7/WI10 的引用锚）。
- 零测试改动、零依赖增删（最小集 = 现有集，裁定依据见 Adjudication）。

## Non-Goals

- 不迁测试至独立 IT 模块（方案 (a) 被否决，见 Adjudication）。
- 不动 runtime main 依赖（编译面已无 connector）。
- 不改任何测试文件。

## Adjudication（方案二选一，plan 阶段裁定）

**裁定：方案 (b)——显式登记的例外，最小集 = 现有 6 个 test 依赖。**

1. fan-in 对 E2E 测试是**内生需求**：引擎级 E2E（checkpoint 恢复、backfill、kill-recover、2PC）必须经真实 connector 源汇驱动；测试面零 connector 化只能以削弱 E2E 语义或外迁为代价。
2. 方案 (a)（迁 9 文件至新 IT 模块）是**耦合搬迁而非消除**：新模块的依赖图 = runtime + 全部 connector，与现状同构，且引入新模块的构建面/CI/维护成本；9 个测试与 runtime 测试树的 ~350 文件共享 fixture（H2/CoreInitialization/2PC 基座），物理拆分的回归风险与 Phase B「行为保持」目标相悖。
3. 收掉与门控两个分支均被裁定否决：**收掉**——debezium 被 P-REQ-28 验收断言硬性要求（收掉 = 削弱验收覆盖），rocksdb 有 9 个真实消费文件（roadmap 的「确认无消费者」候选前提不成立）；**门控**（test 按需 profile / 独立 execution）——依赖默认不上 test classpath 时 discovery 测试要么默认红、要么同被门控，P-REQ-28 验收断言退出默认验证面，验收覆盖被隐性削弱；依赖若仍默认在 classpath 则门控零收益。两条分支殊途同归：**最小集即现有集**。
4. 方案 (b) 的交付面收敛为：pom 注释 = 真实的消费者普查表（消除「ALL FOUR」漂移）+ AR-2 test-scope 例外显式登记 + 基线重钉。M1 门的「显式登记的最小集」由此达成。
5. **WI12 门禁措辞联动**：roadmap WI12 条件步对方案 (b) 的 CI 断言原文为「pom 注释登记完整（无直接消费者的依赖不出现）」——按本裁定 debezium（无直接 import 消费者、有 registry 断言消费）恰在 test 依赖面，WI12 落地时该断言须改写为「每个 test 依赖要么有登记消费者、要么有例外登记」的普查表一致性断言。

## Scope

### In Scope

- `nop-stream/nop-stream-runtime/pom.xml`：:89-95 注释块重写为逐依赖普查表（每依赖列消费者文件与动因，登记 AR-2 test-scope 例外与裁定依据）；既有各依赖的内联注释保留。
- `ai-dev/backlog/nop-stream-structure-refactor-roadmap.md`：WI5 状态行括注重钉基线双值（1377/10 skipped + 361 文件）。

### Out Of Scope

- 任何 java 文件、任何依赖条目的增删改（scope 层面）；docs-for-ai（WI12 收口统一核对）。

## Execution Plan

### Phase 1 - 普查表登记与基线重钉

Status: completed
Targets: `nop-stream/nop-stream-runtime/pom.xml`、roadmap WI5 状态行

- Item Types: `Decision`（例外登记与最小集裁定）；`Proof`（基线重钉）

- [x] pom :88-95 注释块重写：修正 ALL FOUR → 5 connector + rocksdb；逐依赖列直接消费者（9+1 文件清单）与登记动因（含 debezium 的 P-REQ-28 断言依据、rocksdb 的 9 消费文件）；显式声明 AR-2 test-scope 例外与 plan 2415 裁定；**并入原块的两项承重内容**——surefire init-cap（nop.core.initialize.max-initialize-level）使 connector initializer 惰性的机制说明 + F-26 审计更正记录；声明普查表为权威面、各依赖既有内联注释（kafka WI3 plan 2335 / connector Stage 49 / jdbc Stage 52）为历史指针
- [x] roadmap WI5 状态行改为 done 并括注重钉基线双值

Exit Criteria:

- [x] pom 中不再有「ALL FOUR」表述（grep 零命中）；普查表列全 6 个 test 依赖的消费者与动因
- [x] **基线重钉生效**：`./mvnw -o test -pl nop-stream/nop-stream-runtime` 复跑 BUILD SUCCESS 且 Tests run = 1377（Skipped 按环境断言：Docker 不可用/opt-out 时 = 10，Docker 可用时 = 8）（零测试改动下总数应与处置前基线完全相同；前提：本地仓库已含上游 SNAPSHOT 构件，否则先 `./mvnw -o install -DskipTests -pl nop-stream/nop-stream-core,nop-stream/nop-stream-flow -am`；若总数 ≠ 1377，正确动作是查因后以新值重钉本 plan 与 roadmap 括注，**不得为凑数改动任何测试**）
- [x] `./mvnw -o dependency:tree -pl nop-stream/nop-stream-runtime` 确认 5 connector + rocksdb 均为 test scope（compile 面零 connector 不变）
- [x] No new test required: 纯 pom 注释与文档登记，无代码路径；基线一致性本身即验证面
- [x] No owner-doc update required（pom 注释即登记载体；AR-2 意图与测试面例外的正式关系由基线文档依赖表 WI3 已登记的行承载）
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI5 条目

## Closure Gates

- [x] pom 普查表与 live 消费面一致（抽查 3 个依赖的消费者文件真实存在且 import 关系成立）
- [x] runtime 测试复跑与重钉基线一致（1377/10 skipped）
- [x] dependency:tree test scope 断言成立
- [x] 无 in-scope live defect 被降级为 follow-up（fan-in 按裁定登记为例外，非降级——M1 门明文允许「显式登记的最小集」）
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2415-wi5-runtime-test-connector-fanin-registered-exception.md --strict` 退出码 0

## Closure

Status Note: 全部项经独立审计 PASS（普查表 4/4 抽查、基线值经 surefire-reports 聚合独立复核 1377/0/10 精确吻合、tree test-scope 断言、编号唯一性）。审计发现 1 项交付缺陷并阻断关闭：roadmap WI5 状态行括注内嵌套全角括号违反 :5 规则且实测使解析器丢行（12→11 项）——已按审计建议改写为破折号形式并复跑解析器确认 12 项、WI1-WI5=done 后关闭。本 plan 原编号 2414 因并发会话编号冲突已重命名为 2415（pom/roadmap/log 引用同步更新）。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_7e85b241-2e0e-4c03-beaf-4be7a03342b0
- Evidence:
  - pom 注释：ALL FOUR 零命中；登记块 :89-126 全要素在位（AR-2 例外、P-REQ-28 依据、rocksdb 清单、init-cap、F-26、权威面声明）；diff 仅注释行无 XML 结构变更
  - 普查表抽查 4/4 PASS（connector-jdbc 3 文件 import 成立、rocksdb 抽样 2 文件、TestWi19:87 FQN、discovery :93/:106/:110 断言核读）
  - 基线独立证据：surefire-reports 聚合 run=1377/failed=0/skipped=10（319 报告文件）；find 实测 361/320
  - dependency:tree 全部 :test、compile 面零 connector；check-doc-links --strict exit 0
  - 发现并阻断后修复：roadmap WI5 括注嵌套括号使解析器丢行——改写后复跑 12 项/WI1-WI5 done
  - Deferred 分类检查：无 in-scope live defect 被降级；fan-in 例外登记为 M1 门明文允许形态

Follow-up:

- no remaining plan-owned work
