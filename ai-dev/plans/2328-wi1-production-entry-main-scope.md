# 2328 WI1 生产进程入口 main-scope 化（JobCoordinatorMain/TaskManagerMain 迁 main sources）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI1（M1 P0 主线）+ productization F 表 F-12/F-26 + 对比分析 P0-2
> Related: plan 2327（WI0 裁定，completed）；`ai-dev/design/nop-stream/distributed-runbook.md`（启动顺序文档）；WI19（收口 deps 本 WI）

## Purpose

把独立集群进程入口（`JobCoordinatorMain`/`TaskManagerMain` 及其启动支撑类）从 `nop-stream-runtime` 的 test sources 迁入 main sources，提供启动脚本与启动文档，使发布 runtime jar 的 compile 类路径可直接启动生产集群；随迁核对并闭合 productization F-12（nop-dao provided 作用域风险）与 F-26（pom 注释矛盾），closure 时按仓库惯例回写 F 表销号。

## Current Baseline

- 入口类现状（live 核实）：`JobCoordinatorMain`、`TaskManagerMain`、`SharedJdbcInfrastructure`、`PollingJdbcMessageService` 位于 `nop-stream-runtime/src/test/java/io/nop/stream/runtime/launch/`；`ClusterLaunchConfig`、`ClusterPipelineFactory` 已在 main sources 同包。四类迁移无编译障碍：全部 import 均已 main-scope 可达（JdbcClusterRegistry/JdbcLeaderElector/JdbcCheckpointStorage/CollectionReplayableSource/ClusterLaunchConfig/ClusterPipelineFactory）；main 侧对四类的引用全部是 javadoc/注释。
- `docs-for-ai/03-modules/nop-stream.md:243` 有 F-11 诚实化警告块：「发布 runtime jar 的类路径上没有这两个入口类」，列了三种 workaround；`nop-stream-user-guide.md:203-205` 同口径。迁移后须同步改写。
- **两条启动路径并存（关键分叉）**：① gated 多 JVM E2E 经 `MiniStreamCluster`（runtime test，multijvm 包）用 surefire 测试类路径（`java.class.path`）spawn 入口，且传 `-Dnop.core.initialize.max-initialize-level=4500`（低于 IOC 的 cap）；② 脚本/生产独立启动用 compile 类路径、满级初始化（IoC + dao-defaults beans 加载）——后者当前零验证覆盖，本 plan 补上。
- gated 多 JVM 测试分布：runtime 4 个（`TestMiniStreamClusterProcessSpawn`/`TestMultiJvmCoordinatorFailover`/`TestMultiJvmExactlyOnceRecovery`/`TestMultiJvmHealthStateAndAlerts`）+ fraud-example 6 个（S1/S2/Parallel2Pc/S2RestoreRescale/Backpressure/StabilityExercise），全部 `@EnabledIfSystemProperty(nop.stream.test.multi-jvm.enabled)` 门控——`./mvnw test` 不带 gate 时跳过而非运行（F-07 诚实化在案）。
- runtime pom 现状：`nop-dao` = provided（F-12 风险面）；`HikariCP` = test（`SharedJdbcInfrastructure` 主路径依赖）；`h2` = test（驱动经 Hikari 反射加载，编译期不需要——**build-classpath 装配的 compile 类路径不含任何 JDBC 驱动，启动方须自带驱动 jar**）；Item 19 注释称「connector / connector-jdbc are already main deps」但两者实为 test scope（F-26 注释矛盾；F 表锚点 :63-67 vs :143-161 已过期，现约 :68-72 vs :148-166，销号时顺带修正锚点）。
- `fraud-example/pom.xml:68-73` 注释称 launch harness「lives in runtime test sources，经 test-jar 导出」——迁移后成假话，须同批同步（否则本 plan 闭合 F-26 又新增同类矛盾）。
- `SharedJdbcInfrastructure` 硬编码 `org.h2.Driver`（生产启动需可配置驱动）。
- HikariCP 版本经根 pom → nop-bom → nop-dependencies 导入的 BOM 链裁决（`nop-kernel/nop-dependencies/pom.xml:123-164`），scope 翻转无版本解析风险。
- 已有 main-scope 运维面（`OpsJobManager`/`StreamOpsHttpServer`/`StreamMaintenanceMain`）不受影响。

## Goals

- 四个 launch 类进 main sources，`mvn install` 产物 runtime jar 直接含生产入口。
- `SharedJdbcInfrastructure` 驱动可配置（新 `jdbcDriver` 配置键，默认 `org.h2.Driver` 保持现行为）。
- pom 诚实化：nop-dao → compile（F-12 闭合）、HikariCP → compile、F-26 注释矛盾修正、test-jar 导出注释更新（MiniStreamCluster 仍 test-jar，入口类已 main）、fraud-example pom 注释同批同步。
- 启动脚本 `nop-stream-runtime/src/main/scripts/nop-stream-cluster.sh`（coordinator/taskmanager/classpath 子命令，classpath 装配 + 驱动 jar 追加口）。
- 启动文档：`nop-stream.md` 运维手册 F-11 警告块改写为 main-scope 现实 + classpath 装配说明；user-guide 入口类现状节同步；distributed-runbook 引用脚本。
- 端到端证明分两层：① test-classpath 路径——gated 多 JVM 真进程 E2E 全绿（spawn 迁移后入口）；② compile-classpath 生产路径——脚本真实冒烟（JC+TM 真进程、满级初始化、SIGTERM exit 0）。

## Non-Goals

- 不改启动协议/参数语义（`MiniStreamCluster` 与 gated E2E 零修改预期通过；仅当迁移暴露真缺陷才修）。
- 不做 shade uberjar / K8s 编排（K8s = D-GAP defer 维持，WI0 R6 口径）。
- 不动 `OpsJobManager`/`StreamMaintenanceMain` 既有 main-scope 面。
- 不新增 Windows 批处理脚本（文档记录即可）。

## Scope

### In Scope

- `nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/`：迁入 4 类（git mv 保留历史）+ `ClusterLaunchConfig` 增 `jdbcDriver` 键 + `SharedJdbcInfrastructure` 驱动配置化。
- `nop-stream-runtime/pom.xml`：F-12/F-26 修正 + test-jar 注释更新。
- `nop-stream-fraud-example/pom.xml`：launch harness 注释同步（仅注释，一行）。
- `nop-stream-runtime/src/main/scripts/nop-stream-cluster.sh`（新增）。
- `nop-stream-runtime/src/test/java/.../launch/TestLaunchMainScopeWiring.java`（新增 wiring 测试）。
- `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-user-guide.md`、`ai-dev/design/nop-stream/distributed-runbook.md`。
- `ai-dev/backlog/nop-stream-productization-roadmap.md`：按仓库惯例销号 F-12/F-26 并修正过期锚点——`Last updated` header + EXEC_PLANS blockquote + F 表行内删除线注记（执行注记：首轮审查建议「行内不打注记」，执行时发现同文件 AR-16 行已有行内删除线销号先例，从先例改为 blockquote + 行内注记双写，audit 已确认形态一致）。

### Out Of Scope

- 其余 productization F 表行（WI19 统一核对 F-30/F-33/F-34/F-36）。
- fraud-example 场景模块代码变更（pom 注释同步除外；gated E2E 仅作回归验证面）。
- PollingJdbcMessageService 的生产化替代（生产 IMessageService 后端注入是既有能力）。

## Execution Plan

### Phase 1 - 迁移与 pom 诚实化

Status: completed
Targets: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/`、`nop-stream-runtime/pom.xml`、`nop-stream-fraud-example/pom.xml`

- Item Types: `Feature` + `Fix`（F-12/F-26 为已确认 contract drift）

- [x] git mv 四类（JobCoordinatorMain/TaskManagerMain/SharedJdbcInfrastructure/PollingJdbcMessageService）到 main sources 同包；javadoc 中「test classpath/test-jar」表述随迁更新
- [x] `ClusterLaunchConfig` 增 `KEY_JDBC_DRIVER = "jdbcDriver"`；`SharedJdbcInfrastructure` 以 `config.get(KEY_JDBC_DRIVER, "org.h2.Driver")` 替换硬编码（默认值 = 现行为，既有测试零修改通过）
- [x] pom：nop-dao scope provided→compile（注释记 F-12 闭合依据：生产入口 standalone classpath 必含 JDBC 面）；HikariCP test→compile（SharedJdbcInfrastructure 主路径依赖）；Item 19 注释改为如实描述（connector-debezium/batch/connector/jdbc 四模块均为 test-scope 聚合发现测试依赖）；test-jar 注释更新（导出面收敛为 MiniStreamCluster + 测试装置，入口类已 main-scope）；fraud-example pom launch-harness 注释同步
- [x] 新增 `TestLaunchMainScopeWiring`：断言四类 `.class` 资源 URL 位于 `/classes/` 且非 `/test-classes/`（main-scope 迁移的接线钉子）+ `jdbcDriver` 配置键生效（非 h2 驱动名传入 Hikari 配置）

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（既有 TestJobCoordinatorMain/TestTaskManagerMain/TestTaskManagerOpsEndpoint 等非门控测试零修改通过；4 个 multijvm gated 测试跳过属预期，spawn 回归面由下一条覆盖）
- [x] gated spawn 回归：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestMiniStreamClusterProcessSpawn -Dnop.stream.test.multi-jvm.enabled=true -Dsurefire.failIfNoSpecifiedTests=false` 全绿（真进程 spawn 迁移后入口）
- [x] 下游传递面回归：`./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿（nop-dao 转 compile 后首个新获得传递依赖的模块，验证初始化面无回归）
- [x] runtime `./mvnw install -DskipTests -pl nop-stream/nop-stream-runtime` 产物 jar 含四类（jar tf 断言或 wiring 测试等效证据）
- [x] **接线验证**：`TestLaunchMainScopeWiring` 断言类来源为 main classes（防 test-scope 迁移假完成）
- [x] **无静默跳过**：`jdbcDriver` 未配置时默认 h2（行为不变），配置了不存在驱动时 Hikari 启动 fail-fast（Hikari 语义，不新增吞异常路径）
- [x] `ai-dev/logs/` 对应日期条目已更新（可与其他 Phase 合并写入）

### Phase 2 - 启动脚本、双路径 E2E 与启动文档

Status: completed
Targets: `nop-stream-runtime/src/main/scripts/`、gated fraud-example E2E、compile-classpath 冒烟、docs-for-ai 两文件 + runbook

- Item Types: `Feature` + `Proof`

- [x] `nop-stream-cluster.sh`：`coordinator|taskmanager|classpath` 子命令；classpath 装配 = `mvn -q dependency:build-classpath`（缓存到 `<project-root>/_tmp/`，可用 `NOP_STREAM_CLASSPATH` 整体覆盖）+ `NOP_STREAM_EXTRA_CLASSPATH` 追加（承载 JDBC 驱动 jar——compile 装配不含驱动，须显式提供）；缺 maven/缺参数 fail-fast 非零退出。脚本自身不另写单元测试（No new test required: 脚本行为由本 Phase 的 compile-classpath 冒烟真实执行覆盖）
- [x] **compile-classpath 生产路径冒烟**：脚本真实拉起 JC + 1 TM（trivial 空源作业，h2 驱动经 `NOP_STREAM_EXTRA_CLASSPATH` 提供，classpath 由 `classpath` 子命令装配——不含 test-classes），grep 启动成功日志，SIGTERM 后双进程 exit 0；该冒烟同时验证满级初始化路径（无 `-Dnop.core.initialize.max-initialize-level` cap，IoC + dao-defaults 完整加载）
- [x] gated 多 JVM 真进程 E2E 回归（test-classpath 路径）：`./mvnw test -pl nop-stream/nop-stream-fraud-example -am -T 1C -Dtest=TestS1MultiJvmE2E -Dnop.stream.test.multi-jvm.enabled=true -Dsurefire.failIfNoSpecifiedTests=false` 全绿（spawn 迁移后入口：JC+TM×2 真进程、任务部署、checkpoint、SIGTERM 优雅退出）
- [x] `nop-stream.md`：F-11 警告块（:243）改写为 main-scope 现实（classpath 装配两种方式：脚本 / `dependency:build-classpath` 手工；驱动 jar 须自带）；:95/:96/:121/:248/:271 的「test-scope 入口」注记清除
- [x] `nop-stream-user-guide.md:203-205` 入口类现状节改写
- [x] `distributed-runbook.md` 启动节补脚本用法引用
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] compile-classpath 冒烟通过：JC/TM 双真进程独立启动、日志含启动成功标记、SIGTERM exit 0（命令与输出摘要记入 daily log）
- [x] gated fraud-example E2E 全绿（test-classpath 路径回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 文档中不再残留「入口类 test-scope」的过时表述（grep 限于 nop-stream.md / nop-stream-user-guide.md / distributed-runbook.md 三文件；其他文件对入口类的合法 javadoc 提及不算）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - F 表销号、closure audit 与提交

Status: completed
Targets: productization roadmap、roadmap WI1、plan 2328

- Item Types: `Proof` + `Follow-up`（F 表销号）

- [x] productization roadmap：按仓库惯例销号——顶部 `Last updated` 追加收口记录 + EXEC_PLANS blockquote 追加一行列出 F-12/F-26 闭合（含 plan 号与处置方式），并修正 F-12/F-26 行内过期 pom 锚点；不在 F 表行内打销号注记
- [x] 独立子 agent closure audit（fresh session）：Phase 1/2 exit criteria + Closure Gates + F 表销号 + roadmap 勾选一致性
- [x] roadmap WI1 checkbox 勾选
- [x] git commit（feat(nop-stream): WI1 生产入口 main-scope 化…）
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] No owner-doc update required（F 表/roadmap 属 ai-dev 面状态通道；docs-for-ai 更新已在 Phase 2 完成）

Exit Criteria:

- [x] F 表销号符合仓库 blockquote 惯例且锚点已修正
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI1 已勾选、提交完成

## Closure Gates

- [x] 四个 launch 类已迁 main sources 且 runtime jar 产物包含
- [x] F-12（nop-dao provided 风险）与 F-26（pom 注释矛盾）已闭合并按惯例销号
- [x] main-scope 入口可独立启动集群——双路径证据齐备：compile-classpath 冒烟（生产形态）+ gated 多 JVM E2E（test 形态回归）
- [x] 启动文档与 live 行为一致（无过时 test-scope 表述）
- [x] 不存在被静默降级的 in-scope 项
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-runtime --severity high` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

（无——如执行中发现新的相邻问题，按 plan guide 移入此处并写明 Why Not Blocking Closure）

## Closure

Status Note: WI1 完成：四 launch 类迁 main sources（jar 产物含 12 项 launch/*.class）、nop-dao→compile + HikariCP→compile（F-12/F-26 闭合并按惯例销号）、`nop-stream-cluster.sh` 启动脚本（`-DincludeScope=runtime` 发布依赖面装配）、双路径 E2E 证据（compile-classpath 冒烟真进程启动+优雅关闭；gated TestS1MultiJvmE2E 2/2 + TestMiniStreamClusterProcessSpawn 3/3 spawn main-scope 入口）、三份文档 test-scope 表述清零、runtime 1251/0 回归 + nop-stream-sql -am 绿。roadmap WI1 已勾选。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_29f89a07-aa2e-4d8f-8438-c74a43591d4f，两轮审计）
- Audit Session: agent_29f89a07-aa2e-4d8f-8438-c74a43591d4f（首轮 REJECTED：1 Blocker（F 表锚点断链致 doc-links 红）+ 2 Major（header/blockquote 预写未发生事实、Plan Status 滞后）+ 3 Minor；全部修复后复验 APPROVED）
- Evidence:
  - Phase 1 各项 PASS：四类 rename 迁 main、pom scope/注释、jdbcDriver 键、wiring 测试断言（/classes/ 非 /test-classes/ + FakeDriver 透传）
  - Phase 2 各项 PASS：脚本 `-DincludeScope=runtime`、双路径 E2E 证据、三文档无 test-scope 残留、doc-links exit 0
  - Anti-Hollow PASS：jar 含四类（unzip -l 实测）、MiniStreamCluster spawn 链 + 脚本 exec java -cp 调用链连通、scan-hollow `--module nop-stream/nop-stream-runtime` exit 0（118 files，0 high）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（复验实测）
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（closure 回填后全勾选）
  - Deferred 项分类检查：无 in-scope live defect 被降级；TestBatchTaskCheckpoint flaky 为 watch-only 留档（与 WI1 改动面无关，隔离重跑绿）

Follow-up:

- TestBatchTaskCheckpoint（nop-batch-core）在 `-T 1C` 并行构建下偶发竞态失败（IllegalStateException vs BatchCancelException），隔离重跑稳定绿——watch-only 留档，不阻塞 closure
