# 2432 WI11 分布式路径切统一内核——对齐收尾 + 双跑归零 + 拆分评估裁定

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md Phase D WI11；WI9 §4.1/§4.3/§4.4；WI10 Deferred 承接（终态预算默认值/EXPORT 接入/启动路径门）
> Related: plan 2426（WI10）；plan 2423（WI9）；CC-06/CC-17 承接 plan 同波次（独立编号）

## Purpose

路线 (a) 收尾：两驱动面对共享内核的**完全对齐**（每步经双跑门），分布式路径与本地路径在共享内核上语义归零；顺带完成 createStorage 工厂迁移（6 处直 new 收敛）与 execution 包拆分**评估裁定**（只裁不拆）。gated S1/S2 多 JVM 场景全绿、exactly-once 断言不弱化。

## Current Baseline

2026-10-11 实测（基线 febbd1d0a4；审查时 HEAD 已前移一测试基建 docs 提交，无实质影响）：

- 差分 harness（WI10 C1）双脸四件套全绿（3 连绿 ~11s）；TestLegacyCheckpointFixtureRestore 跨构建恢复绿；TestJobCoordinatorFailoverRestore 11/11；TestRemoteDeployCheckpointWiringE2E 绿。
- 对齐残留（WI10 Deferred + WI9 裁定）：①JC EXPORT_SAVEPOINT 未接 SavepointMaterializer（4a 机制接入）；②JC 启动路径 `JobCoordinatorMain` raw restore 无 manifest 校验门（failover 路径已门）；③终态预算默认值归属未裁（1c：E3 方向=分布式 60s；本地 600s）；④`createStorage`（GMCE :904-925，语义锚 类#方法；审查实测修正）散落 6 处直 `new LocalFileCheckpointStorage`（execution 5 + JobCoordinatorMain 1）。
- gated S1/S2：`TestS1MultiJvmE2E` / `TestS2MultiJvmE2E`（`-Dnop.stream.test.multi-jvm.enabled=true`，runbook §5 命令形）；C0 基线两方法 + C1 kill-recover 两方法为本 WI 验证面。
- execution 包 6 类职责/扇出 7 顶层包——**只评估裁定**，实际拆分另立。
- 基线：core 1967/0/1、runtime 1203/0/11、flow 315/0/0。
- 本计划编号 2432 为当前最大+1（2431 并发会话占用）。

## Goals

- **对齐项（每步差分 harness + fixture restore 双门）**：①`CheckpointStorages` 工厂落 `runtime.checkpoint.storage`，6 处直 new 收敛（src/main）；②JobCoordinatorMain 启动路径补 manifest 校验门（与 failover 路径同语义抽共享助手：指纹 fail-closed + counter 越 manifest epoch；fail-closed = launch 失败非静默 fresh）；③两项评估裁定 defer 入台账（Phase 1b）：EXPORT 接入（4 契约缺口）与终态预算默认值归一（钉子测试 + 无差分语料）——successor 归 r5-successors。
- **双跑校验归零记录**：全部对齐步落地后差分 harness + fixture + failover 族全绿留档；恢复点漂移/账本吸收等已知差异逐项归零或登记。
- **拆分评估裁定**：execution 包迁出后职责盘点 → 「是否达到可拆条件」结论 + 建议拆分线，写入设计档 §四.6（只裁不拆，实际拆分另立）。
- **gated 验证**：S1/S2 多 JVM C0 基线 + C1 kill-recover 四方法全绿；exactly-once 断言不弱化（期望集逐元素断言保持）。
- roadmap WI11 翻转 + daily log。

## Non-Goals

- execution 包实际拆分（另立 WI/backlog）；CC-06/CC-17（独立承接 plan，本 WI 后执行）；DIV-01～06 冻结项；1c 预算回退失败时的强推（测试仲裁为准）。

## Scope

### In Scope

- 新增 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointStorages.java`（工厂：local/jdbc/s3 分派，收编 GMCE createStorage 逻辑）；6 调用点改造。
- JobCoordinatorMain 校验门（共享助手 RestoreGates + TestRestoreGate 四场景）；EXPORT 接入与预算默认归一 = 评估裁定 defer（Phase 1b，入台账）。
- 设计档 §四.6 拆分评估裁定；roadmap WI11 翻转；daily log。
- gated S1/S2 运行留档（runbook §5 命令形 + 结果）。

### Out Of Scope

- execution 包实际拆分；DIV 冻结项；fraud-example 场景断言改动（只跑不改）；quickstart/connector 面。

## Execution Plan

### Phase 1 - 对齐项落地（每步双门；提交序列 A/B）

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointStorages.java`、6 调用点、`checkpoint/core` 恢复门共享助手、JobCoordinatorMain、测试

- Item Types: `Fix`（对齐——WI9/WI10 承接）、`Feature`（共享门助手 + 测试）

- [x] **A（结构迁移）**：`CheckpointStorages` 工厂落 `runtime.checkpoint.storage`（local path 属性/jdbc 拒绝默认构造/s3 fromConfig；`defaultStorageBaseDir` 随迁）；GMCE.createStorage 改委托工厂（保留方法签名——3 个测试文件约 10 处调用零迁移）；savepoint/savepoint-parent 两处 GMCE 内直 new 与 Embedded/Rpc/JobCoordinatorMain 三处改用工厂；src/test 的合法直 new 不动
- [x] **B1（行为，差分门）**：JobCoordinatorMain 启动路径 manifest 校验门——抽共享助手 `checkpoint.core.RestoreGates.applyManifestGate(coordinator)`（指纹 fail-closed + counter 越 manifest epoch；与 JC failover 门 :2043-2076 同语义，JC 门改调共享助手消重复）；**启动路径语义裁定：门 fail-closed 时启动失败（抛出），镜像 failover——用户可见后果 = launch 失败，非静默 fresh**；新增 `TestRestoreGate`（4 场景：无 manifest skip/兼容过门/mismatch fail-closed/无源 fail-closed）镜像 TestJobCoordinatorFailoverRestore 形态
- [x] **每步双门**：差分 harness（10.85s）+ fixture restore（3.9s）+ failover 11/11 + TestRestoreGate 4/4 全绿
- [x] 提交序列：A（结构工厂迁移）与 B1（行为门 + 共享助手 + 测试）已分离提交

Exit Criteria:

- [x] src/main 直 new LocalFileCheckpointStorage 仅剩工厂内部（grep 实证）；差分 harness 绿
- [x] TestRestoreGate 四场景绿；JC failover 族 11/11 不回归（共享助手改造后）
- [x] 提交序列 A/B 分离

### Phase 1b - 裁定项（评估结论，不落地代码）

Status: completed
Targets: 设计档 §四.5 续

- Item Types: `Decision`

- [x] **EXPORT 接入评估结论 = defer（4 契约缺口记录）**：①materialize 硬编码 SAVEPOINT vs 矩阵钉 EXPORTED_SAVEPOINT（type 参数化属共享内核契约变更）；②预算源冲突（materialize 读 config resolve=10s vs JC 自有字段 60s，矩阵 setTerminationCheckpointTimeoutMs(200) 会被静默忽略）；③分布式面无 GraphExecutionPlan，KeyGroup 归属戳机制不存在（WI9 4a 本就裁定 EXPORT 无归属戳——接入须显式 skip）；④storage 实例不可达（JC 仅持路径字符串，coordinator 无 getter）；附注：materialize 末次 storeCheckPoint 为幂等覆写、EXPORT 丢弃路径返回语义正确（持久性由 coordinator 完成路径先行保证）。接入需 materialize 契约参数化设计 → 归 successor（r5-successors 或 WI12 后续）。
- [x] **终态预算默认值归一 = defer（钉子测试 + 证据面裁定）**：TestCheckpointTerminationBudget.nullFallsBackToCheckpointTimeout 钉住 null→checkpointTimeout（WI10 零变更契约的组成部分），改 60s 回退必然转红且属断言级；被点名的 TriggerBoundedRetry（显式传预算）/ReproC2（不走 resolve）实际不敏感——无差分语料能仲裁默认值边界；E3 方向（60s）记录为 successor 指引。
- [x] 设计档两条裁定写入（实际落点 **§四.6 WI11 落地回写**，指针勘误；successor→r5-successors）——唯一写入点

Exit Criteria:

- [x] 两条裁定落设计档 §四.5 续，gap/依据/successor（r5-successors）完整

### Phase 2 - 双跑归零记录 + 拆分评估裁定

Status: completed
Targets: 设计档 §四.6

- Item Types: `Decision`

- [x] 差异归零记录：三项已知差异（恢复点漂移/指纹非对称/终态 epoch 写面）逐项标结构性保留 + 依据，入设计档 §四.6 前置台账——无未解释差异
- [x] 设计档回写核验：Phase 1b 两裁定已入 §四.6（指针勘误：审计发现实落 §四.6 非 §四.5 续）
- [x] execution 包拆分评估裁定 = **暂不拆**（§四.7：checkpoint 大头已迁出；剩余扇出源自 GMCE/JC 两巨型类——包拆不对症，RD-05 类级拆分才是对症项；复核触发条件已记录）；实际拆分按 roadmap 另立

Exit Criteria:

- [x] 差异清单逐项有归零或结构性保留裁定
- [x] 拆分评估结论落设计档 §四.7

### Phase 3 - gated S1/S2 + 基线 + 收口

Status: completed
Targets: fraud-example gated 场景、roadmap、daily log

- Item Types: `Proof`

- [x] gated 运行（runbook §5 命令形；**前置修复已落**：fraud-example 3 测试文件的 `runtime.rpc.RemotePipelineSpec` 旧 FQN（WI6 搬迁遗留——gated 套件不进默认编译故未暴露）改 `runtime.protocol`；**red 处置纪律：复跑一次排除环境抖动，仍红则按 runbook 归因分类〔hang 先核验 item 28 归属〕并留档，不静默跳过**）：`TestS1MultiJvmE2E#s1MultiJvmBaselineDeploysFullSemanticsWithCheckpoints`、`TestS2MultiJvmE2E#s2MultiJvmBaselineBaseAndDeltaProduceExactlyOnceOutput`、`TestS1MultiJvmE2E#s1MultiJvmKillRecoverFencingExactlyOnce`、`TestS2MultiJvmE2E#s2MultiJvmKillRecoverFencingExactlyOnce`——全绿留档（结果摘要入本 plan）
- [x] **runtime `-am` 显式裁定（scope change）**：全 reactor 被并发 jdk25 会话的 test-fork 污染阻断（nop-stream-connector-debezium NoClassDefFound JsonTool 仅 -am 上下文复现、单模块独立运行绿实证；该会话 plan 2430 正在承接）——本 WI 记录归因 + 模块级验证完整（三模块直接运行全绿），**-am 全绿复核移交 WI12**（其全量 install 阶段将在 2430 落地后复核）；三模块基线不下降（core 1967/0/1、runtime 1203/0/11、flow 315/0/0）
- [x] roadmap WI11 `todo`→`done`（done 注记携带两条 defer→r5-successors 指针——2426 先例）+ 复跑 12 条目 11 done；daily log；doc-links 0 errors
- [x] C2 收口提交（docs + plan Closure——经一次污染事故〔amend 无 pathspec 卷入并发暂存〕reset+pathspec 净化重建，事故已登记 daily log）

Exit Criteria:

- [x] 四 gated 方法全绿且摘要留档（S1 2/2 19.35s、S2 2/2 34.01s）；exactly-once 断言零弱化（只跑不改）
- [x] runtime `-am`：见上方显式裁定（并发污染阻断，单模块绿，复核归 WI12）；三模块基线不下降
- [x] roadmap-check 12 条目 11 done、WI11 done；daily log；doc-links 0 errors（audit D12）

## Closure

Status Note: WI11 三 Phase + 1b 裁定全部完成：CheckpointStorages 工厂收敛六处直构（单 choke-point）+ RestoreGates 共享恢复门（JC failover 与 JCM 启动两调用点，fail-closed=launch 失败语义）+ EXPORT/预算两项评估裁定 defer→r5-successors + execution 拆分裁定暂不拆（§四.7）+ 差异台账三项结构性保留。gated S1/S2 多 JVM 四方法全绿（19.35s/34.01s）留档；三模块基线全绿（core 1967/0/1、runtime 1203/0/11、flow 315/0/0）。runtime `-am` 全 reactor 被并发会话 test-fork 污染阻断——显式裁定为 scope change：单模块独立绿已足、全绿复核移交 WI12（audit E14 唯一子项，已按规则 16 记录 scope change + successor）。独立 closure audit 13 项实质全 PASS + 机械收口已落。顺带修复 fraud-example 3 文件 WI6 遗留 FQN。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（general-purpose，fresh session，agent_19eaa315-e42a-4cb9-9a97-5c99c825b801）
- Audit Session: agent_19eaa315-e42a-4cb9-9a97-5c99c825b801（内容质量门为 agent_ced1de8c-111a-4162-9e99-7534a444cc7d，两轮）
- Evidence:
  - A 组（对齐项 live code）A1-A3 全 PASS：工厂三入口 + src/main 收敛 grep 实证；RestoreGates 语义 + 两调用点 + JCM 抛出语义；audit 亲跑 TestRestoreGate 4/4 + FailoverRestore 11/11（15 tests 0F0E）。
  - B 组（裁定与文档）B4-B7 全 PASS：§四.5 续→§四.6 两条裁定 + §四.6 台账 + §四.7 裁定；roadmap WI11 done 注记带 defer 指针 + 解析 12 条目 11 done；daily log 含提交事故登记。
  - C 组（gated 证据）C8-C9 全 PASS：S1/S2 两日志亲读（2/2 + exit 0）；首跑红归因（FQN 编译断）留档非掩盖；FQN 修复 grep=0。
  - D 组（门禁亲跑）D10-D13 全 PASS：inventory exit 0；hollow 扫描 0（790 文件）；doc-links 0 errors；基线日志三模块实测 + 工作树无未提交 nop-stream 代码。
  - E 组（计划一致性）E14 PARTIAL→已收口：唯一实质项 = runtime `-am` 显式裁定入 plan（scope change + successor WI12）；其余为勾选滞后 + §四.5/§四.6 指针勘误——全部落地。E15 PASS：HEAD 提交恰 7 文件无并发卷入。
  - Anti-Hollow 抽查 PASS（audit A2/B 组）：RestoreGates 两调用点运行时执行、gated 真实多 JVM、工厂唯一构造点。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2432-wi11-distributed-path-unified-kernel.md --strict` 关闭前终跑 exit 0。

Follow-up:

- CC-06/CC-17 承接 plan（差分绿后独立 commit，同波次）。
- WI12 承接：runtime `-am` 全绿复核（2430 落地后）、DIV-01～06 与 EXPORT/预算 defer 的 r5-successors 处置、全量收口。## Deferred But Adjudicated

### JC EXPORT 接入 SavepointMaterializer

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 评估已得出结论——接入存在四契约缺口（materialize 硬编码 SAVEPOINT vs 矩阵钉 EXPORTED_SAVEPOINT；预算源三方冲突 config-resolve 10s/JC 字段 60s/矩阵 set 200ms；分布式面无 GraphExecutionPlan 故 KeyGroup 归属戳机制不存在〔WI9 4a 原裁定 EXPORT 无归属戳〕；storage 实例不可达仅路径字符串），接入须先做 materialize 契约参数化设计。现行 EXPORT 语义完整保留且测试钉全（TerminationMatrix），无 live defect。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-stream-r5-successors.md`（与 DIV/CC 台账惯例一致），本 WI Phase 1b 落档。

### 终态预算默认值归一（1c 后半）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 无差分语料可仲裁——TestCheckpointTerminationBudget.nullFallsBack 钉住 null→checkpointTimeout（WI10 零变更契约组成部分，断言级转红不构成预算边界证据），被点名测试均显式传预算或不走 resolve。E3 方向（分布式 60s）记录为 successor 指引，需产品级预算边界语料支撑。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-stream-r5-successors.md`，本 WI Phase 1b 落档。

### execution 包实际拆分

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: roadmap WI11 明文「只做评估与裁定，实际拆分另立 WI 或登记 backlog，避免本 WI 失控膨胀」。
- Successor Required: `yes`
- Successor Path: 按本 WI §四.6 裁定结论另立或入 backlog。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- （关闭时填写）
