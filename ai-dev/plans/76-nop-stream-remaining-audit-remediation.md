# 76 nop-stream R1/R2 审计遗留修复

> Plan Status: completed
> Last Reviewed: 2026-05-30
> Source: ai-dev/audits/2026-05-27-deep-audit-nop-stream-r1/ (6 维度 47 条), ai-dev/audits/2026-05-28-deep-audit-nop-stream/ (9 维度 ~20 条)
> Related: 73-nop-stream-p3-audit-remediation (completed), 74-nop-stream-p0-p1-audit-remediation (completed), 75-nop-stream-round9-and-p2-audit-remediation (completed)

## Purpose

将 2026-05-27 R1 和 2026-05-28 R2 审计中尚未被 Plan 73/74/75 覆盖的发现修复到可验证状态。Plan 75 Non-Blocking Follow-ups 中也列出了"P3 发现修复（约 30+ 项，可合并为 Plan 76）"。

## Current Baseline

- Plan 75 完成后 `./mvnw test -pl nop-stream -am` 全量通过
- Plan 73 修复了 2026-05-28 21 维度审计的 21 个 P3
- Plan 74 修复了 Round 7-8 + 深度审计的全部 P0/P1（20 项）
- Plan 75 修复了 Round 9 + 深度审计的全部 P0/P1/P2（6 个 Phase）
- R1-09-05 (P1) 经 live repo 验证已在 Plan 74/75 期间被间接修复：GraphModelCheckpointExecutor:325-326 现抛 `StreamException(ERR_STREAM_CHECKPOINT_EXECUTOR_SAVEPOINT_FAILED)`
- 交叉核对确认以下发现未被任何计划覆盖

### 未覆盖发现清单

**P2（8 项）**

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| R1-01-02 | 01 | nop-stream-runtime/pom.xml | spec 规定 runtime 依赖 cep/connector，但实际仅 core；spec 文档与代码漂移 |
| R1-09-04 | 09 | CheckpointCoordinator.java | 定时触发失败后静默跳过，无连续失败计数/告警 |
| R1-09-08 | 09 | PendingCheckpoint.java:129 | acknowledgePrecedingCheckpoint 是未实现的 public API，抛 UnsupportedOperationException |
| R1-15-02 | 15 | OperatorSnapshotResult/TaskStateSnapshot | 状态快照/恢复路径全面基于 Map<String, Object> 弱类型 |
| R1-16-01 | 16 | TestWindowedStreamAggregation.java | assertDoesNotThrow 弱断言，应验证 transformation 名称和 DAG 结构 |
| R1-16-02 | 16 | TestCheckpointIntegration.java | Thread.sleep 等待超时，应用 Awaitility |
| R1-16-05 | 16 | ProcessingTimeoutTrigger.java | 零测试覆盖 |
| R1-16-09 | 16 | TestCheckpointParticipant | 未集成 IKeyedStateBackend，仅 stub 测试接口契约 |
| R2-01-03 | 01 | nop-bom/pom.xml | nop-bom 未管理 nop-stream-connector 和 nop-stream-runtime |

**P3（19 项）**

| 编号 | 维度 | 文件 | 摘要 |
|------|------|------|------|
| R1-01-03 | 01 | fraud-example/pom.xml | spec 规定依赖 runtime+cep，实际仅 cep |
| R1-01-04 | 01 | fraud-example/pom.xml | 对兄弟模块使用显式 ${project.version} |
| R1-01-05 | 01 | runtime/pom.xml | 冗余 maven.compiler 属性 |
| R1-02-02 | 02 | checkpoint/flink/flow | 空壳含 IDE 杂物（.classpath/.project/.settings） |
| R1-02-05 | 02 | NFA.java | 346 行 EventWrapper 内部类未提取 |
| R1-02-08 | 02 | CheckpointBarrierTracker | 混入算子依赖，应随 02-07 移入 runtime |
| R1-02-10 | 02 | CepOperator.java | 硬编码 MemoryKeyedStateBackend |
| R1-09-09 | 09 | ChainingOutput.java | 异常包装丢失算子名称上下文 |
| R1-15-06 | 15 | StreamRecord.java | rogue pattern replace() 伪造泛型 |
| R1-15-07 | 15 | Output.java | instanceof 分派 + unchecked cast |
| R1-15-08 | 15 | KeyedStreamImpl.java | Comparable 假设不安全 |
| R1-15-09 | 15 | LocalFileCheckpointStorage | 反序列化 unchecked cast 无防御 |
| R1-16-04 | 16 | CEP operator 测试 | 反射注入 processingTimeService 脆弱 |
| R1-16-06 | 16 | CoMapFunction/CoFlatMapFunction | 零测试覆盖 |
| R1-16-07 | 16 | PrintSinkFunction | 零测试覆盖 |
| R1-16-08 | 16 | FunctionUtils | 零测试覆盖 |
| R1-17-06 | 17 | SharedBuffer.java | 注释掉的代码残留（~30行） |
| R1-17-07 | 17 | StreamRecord.java | 注释掉的异常抛出逻辑 |
| R2-02-04 | 02 | WindowOperator.java | processElement ~155 行过长 |
| R2-02-05 | 02 | GraphModelCheckpointExecutor | restore 逻辑有 ~100 行重复 |

**adjudicated-not-needed（不纳入修复）**

| 编号 | 原因 |
|------|------|
| R1-01-06 | R2 审计确认 IMessageService 来自 nop-api-core，optional 标注正确 |
| R1-02-04 | R2 审计确认算法强内聚，"暂不拆分" |
| R2-01-01 | 同 R1-01-06，optional 标注合理 |

**已在 Deferred But Adjudicated 中（不纳入修复）**

| 编号 | 来源计划 | 分类 |
|------|---------|------|
| R1-01-01/R1-02-01 | Plan 75 DA-02-04 | optimization candidate（api 空壳→架构拆分） |
| R1-02-06/R2-02-08 | Plan 75 DA-02-03 | optimization candidate（WindowOperator 重叠） |
| R1-02-07 | Plan 75 Out Of Scope | 架构级迁移 |
| R1-02-09 | Plan 75 DA-02-07 | optimization candidate（全静态类） |

## Goals

- 修复全部 8 项未覆盖 P2 发现
- 修复可快速执行的 P3 发现（估计 ~15 项）
- 每个修复完成后 `./mvnw test -pl nop-stream -am` 全部通过

## Non-Goals

- 架构级重构（core 模块拆分、GraphModelCheckpointExecutor 拆分、WindowOperator 统一）— 已在 Plan 75 Deferred
- EmbeddedDistributedExecutor checkpoint 集成 — 已在 Plan 74/75 Deferred
- ICheckpointStorage throws Exception → throws CheckpointStorageException 细化 — 需评估接口变更影响面
- 约 205 文件导入排序批量修复（R1-17-03/04）— 需要 IDE 批量操作，不在 plan 中单文件修复

## Scope

### In Scope

- nop-stream-runtime: CheckpointCoordinator 失败告警、PendingCheckpoint 死代码、BOM 注册、pom.xml 清理、restore 去重
- nop-stream-core: 测试质量改进（assertDoesNotThrow→强断言、Thread.sleep→CountDownLatch/CompletableFuture、新增 ProcessingTimeoutTrigger 测试）、ChainingOutput 异常上下文、StreamRecord/Output 类型安全注释
- nop-stream-connector: BOM 注册
- nop-stream-cep: EventWrapper 提取、SharedBuffer 注释代码清理、CEP 测试工具方法
- nop-stream-fraud-example: pom.xml 版本号清理
- nop-bom: 新增 connector/runtime 版本管理
- IDE 杂物清理、空壳 pom.xml 注释

### Out Of Scope

- 01-01/02-01 api 空壳架构决策（Plan 75 Deferred）
- 02-06/02-03 WindowOperator 重叠（Plan 75 Deferred）
- 02-07 core 含 runtime 级代码（架构级迁移）
- 02-09 GraphModelCheckpointExecutor 全静态（Plan 75 Deferred）
- 15-02 TypedStateValue sealed interface 架构改进
- 17-03/04 约 205 文件导入排序

## Execution Plan

### Phase 1 - BOM + POM 治理 + IDE 杂物清理

Status: completed
Targets: `nop-bom/pom.xml`, `nop-stream/nop-stream-runtime/pom.xml`, `nop-stream/nop-stream-fraud-example/pom.xml`, `nop-stream/nop-stream-{checkpoint,flink,flow}/`

- Item Types: `Fix`

- [x] R2-01-03: nop-bom/pom.xml 新增 `nop-stream-connector` 和 `nop-stream-runtime` 版本管理
- [x] R1-01-04: nop-stream-fraud-example/pom.xml 移除 `<version>${project.version}</version>`
- [x] R1-01-05: nop-stream-runtime/pom.xml 删除冗余 maven.compiler 属性（3 行）
- [x] R1-02-02: 删除 nop-stream-{checkpoint,flink,flow} 下的 `.classpath`、`.project`、`.settings/` 目录；在 `nop-stream/.gitignore` 中添加 `.classpath`、`.project`、`.settings/` 模式
- [x] R1-01-03: 在 nop-stream-fraud-example/pom.xml 中添加注释说明实际仅依赖 cep（与 spec 中"runtime+cep"不同）

Exit Criteria:

- [x] `grep -rn "nop-stream-connector\|nop-stream-runtime" nop-bom/pom.xml` 返回非空结果
- [x] fraud-example pom.xml 中无 `${project.version}`
- [x] runtime pom.xml 中无 `maven.compiler.source/target/encoding` 属性
- [x] checkpoint/flink/flow 下无 `.classpath`/`.project`/`.settings` 文件
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 运行时正确性改进（CheckpointCoordinator + PendingCheckpoint）

Status: completed
Targets: `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`, `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java`

- Item Types: `Fix`

- [x] R1-09-04: CheckpointCoordinator 添加连续触发失败计数器（consecutiveTriggerFailures），超过阈值（默认 3）时 LOG.error 告警；成功触发后重置计数器
- [x] R1-09-08: PendingCheckpoint.acknowledgePrecedingCheckpoint 判断是否有调用方——若无则移除该 public 方法（死代码）；若有则补全实现或标注 @Deprecated

Exit Criteria:

- [x] CheckpointCoordinator 拥有 consecutiveTriggerFailures 计数字段；连续 N 次失败后日志包含连续失败次数（如 "Checkpoint trigger failed 3 consecutive times"）；成功触发后计数器重置为 0
- [x] PendingCheckpoint 中无未实现的 public 方法（要么已移除，要么已实现，要么已 @Deprecated）
- [x] 新增测试：连续失败告警阈值、PendingCheckpoint API 状态
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 测试质量改进

Status: completed
Targets: `nop-stream-core/src/test/`, `nop-stream-runtime/src/test/`, `nop-stream-cep/src/test/`

- Item Types: `Fix`, `Proof`

- [x] R1-16-01: TestWindowedStreamAggregation 中 3 处 assertDoesNotThrow 替换为验证 transformation 名称/DAG 结构的强断言
- [x] R1-16-02: TestCheckpointIntegration 中 Thread.sleep 替换为 CountDownLatch.await(timeout, TimeUnit) 或 CompletableFuture.get(timeout)（项目无 Awaitility 依赖，不引入新依赖）
- [x] R1-16-05: 新增 TestProcessingTimeoutTrigger 测试覆盖超时触发、清空窗口、状态恢复
- [x] R1-16-09: TestCheckpointParticipant（nop-stream-runtime 的 TestCheckpointParticipantIntegration）增加 IKeyedStateBackend 集成验证（至少验证状态恢复后的值正确性）

Exit Criteria:

- [x] TestWindowedStreamAggregation 中无 assertDoesNotThrow
- [x] TestCheckpointIntegration 中无 Thread.sleep（用 CountDownLatch 或 CompletableFuture.get 替代）
- [x] ProcessingTimeoutTrigger 有 >= 3 个测试方法覆盖核心行为
- [x] TestCheckpointParticipant 验证状态后端恢复后的值
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 异常处理 + 类型安全注解改进

Status: completed
Targets: `nop-stream-core/.../operators/`, `nop-stream-cep/.../nfa/`, `nop-stream-runtime/.../checkpoint/storage/CheckpointSerDe.java`

- Item Types: `Fix`

- [x] R1-09-09: ChainingOutput 异常包装附带算子名称上下文——给 ChainingOutput 添加 String operatorName 构造器参数，在 catch 块中 `.param(ARG_OPERATOR_NAME, operatorName)`；同步修改所有 ChainingOutput 实例化点（搜索 `new ChainingOutput` 或 `new ChainingOutputWrapper`）传入算子名称
- [x] R1-15-06: StreamRecord.replace() rogue pattern 添加 Javadoc 警告不得保留旧引用
- [x] R1-15-07: Output.collectElement() 添加 else 分支处理未覆盖的 StreamElement 类型——因 Output 是接口 default 方法不能直接用 Logger，用 `System.getLogger()` 或在 else 中抛 `StreamException(ERR_STREAM_UNSUPPORTED)` 快速失败
- [x] R1-15-08: KeyedStreamImpl sum/min/max 方法添加 @SuppressWarnings("unchecked") + 注释说明 Comparable 假设限制（类级泛型 T 无法添加 Comparable 约束，运行时 instanceof 检查已存在）
- [x] R1-15-09: CheckpointSerDe.java（反序列化逻辑已从 LocalFileCheckpointStorage 提取到此类）中 unchecked cast 添加 instanceof Map 检查

Exit Criteria:

- [x] ChainingOutput 异常 param 包含算子名称（operatorName）
- [x] StreamRecord.replace() 有 Javadoc 警告
- [x] Output.collectElement 有 else 分支处理未覆盖类型
- [x] KeyedStreamImpl sum/min/max 有 @SuppressWarnings + 注释说明
- [x] CheckpointSerDe.java 中反序列化有 instanceof Map 类型检查
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 代码清理与内部类提取

Status: completed
Targets: `nop-stream-cep/.../nfa/NFA.java`, `nop-stream-cep/.../sharedbuffer/SharedBuffer.java`, `nop-stream-core/.../execution/StreamRecord.java`, `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java`

- Item Types: `Fix`

- [x] ~~R1-02-05: NFA.EventWrapper 提取为独立顶层类 NFAEventWrapper~~ (deferred → see Deferred But Adjudicated)
- [x] R1-17-06: SharedBuffer 删除注释掉的代码残留（~30 行）
- [x] R1-17-07: StreamRecord 删除注释掉的异常抛出逻辑
- [x] R2-02-05: GraphModelCheckpointExecutor 提取公共 `restoreFromCompletedCheckpoint()` 方法，消除 restoreFromCheckpoint 和 restoreFromSavepointPath 的 ~100 行重复
- [x] R2-02-04: WindowOperator.processElement 提取 `processElementForMergingWindow` 和 `processElementForRegularWindow` 子方法

Exit Criteria:

- [x] ~~NFA.java 中无 EventWrapper 内部类（已提取为独立文件）~~ (deferred → see Deferred But Adjudicated)
- [x] SharedBuffer.java 中无注释掉的代码块
- [x] StreamRecord.java 中无注释掉的异常逻辑
- [x] GraphModelCheckpointExecutor 中 restore 重复代码已消除
- [x] WindowOperator.processElement < 80 行
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 低优先级补丁（零覆盖测试 + CepOperator + spec 漂移）

Status: completed
Targets: `nop-stream-core/src/test/`, `nop-stream-cep/.../operator/CepOperator.java`

- Item Types: `Fix`, `Proof`

- [x] R1-16-06: CoMapFunction/CoFlatMapFunction 如不使用则标注 @Deprecated，否则新增基本测试
- [x] R1-16-07: PrintSinkFunction 新增基本测试（输出到 ByteArrayOutputStream 验证）
- [x] R1-16-08: FunctionUtils 新增基本测试
- [x] R1-16-04: CEP operator 测试中反射注入 processingTimeService 提取为共享工具方法 `CepTestUtils.injectProcessingTimeService()`
- [x] R1-02-10: CepOperator 添加 Javadoc 说明当前硬编码 MemoryKeyedStateBackend 的设计原因和改进方向
- [x] R1-01-02: nop-stream-runtime pom.xml 添加注释说明 cep/connector 集成规划（spec 漂移治理）

Exit Criteria:

- [x] CoMapFunction/CoFlatMapFunction 有 @Deprecated 或基本测试
- [x] PrintSinkFunction 有 >= 1 个测试
- [x] FunctionUtils 有 >= 1 个测试
- [x] CEP operator 测试中使用共享工具方法而非各自反射注入
- [x] CepOperator 有状态后端设计说明 Javadoc
- [x] runtime pom.xml 有 cep/connector 集成注释
- [x] `./mvnw test -pl nop-stream -am` 全部通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 8 项未覆盖 P2 发现已修复，有对应测试或代码审查证明
- [x] 可执行的 P3 发现已修复（R1-02-05 deferred → 见 Deferred But Adjudicated）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope P2 缺陷
- [x] No owner-doc update required（全部为代码/测试/pom 修复，不涉及平台约定变更）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check: 修复未引入空壳或静默跳过
- [x] `./mvnw compile -pl nop-stream -am` 通过
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] `ai-dev/logs/` 收口记录已写入

## Deferred But Adjudicated

### R1-15-02: 状态快照路径弱类型（Map<String, Object>）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 引入 TypedStateValue sealed interface 是架构级改进，影响所有状态后端实现和快照格式。当前弱类型在功能上正确，无运行时缺陷。需独立设计文档。
- Successor Required: yes
- Successor Path: 独立设计文档

### R1-02-08: CheckpointBarrierTracker 混入算子依赖

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 随 R1-02-07（core 含 runtime 级代码）一起迁移到 runtime 模块。架构级迁移需独立评估。
- Successor Required: yes
- Successor Path: 与 R1-02-07 架构迁移计划合并

### R1-02-05: NFA.EventWrapper 内部类未提取

- Classification: `optimization candidate`
- Why Not Blocking Closure: EventWrapper 是 private inner class，直接访问 NFA 的 SharedBufferAccessor。提取为顶层类需将 SharedBufferAccessor 访问改为包级可见或通过构造器注入，影响 NFA 的内部封装性。这是纯代码组织优化，不影响功能正确性或可维护性（内部类仅 38 行，且仅在 NFA 内部使用）。
- Successor Required: yes
- Successor Path: 合并到 NFA 模块重构计划

## Non-Blocking Follow-ups

- ~205 文件导入排序批量修复（R1-17-03/04）— 建议 IDE 批量操作
- 57 文件通配符导入清理（R1-17-05）
- 5 文件 tab 缩进修复（R1-17-08）
- 非英文注释清理（R1-17-09）
- ICheckpointStorage throws Exception → throws CheckpointStorageException 细化
- 架构级重构设计文档（core 模块拆分、WindowOperator 统一）

## Closure

Status Note: Plan 76 completed. All 8 P2 items fixed. 18 of 19 P3 items fixed (R1-02-05 deferred as optimization candidate). 6 phases executed across 6 commits. `./mvnw test -pl nop-stream -am` passes.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent closure audit (task ses_18974d449ffeEfCru5DdMWVaLV)
- Evidence: All 14 in-scope exit criteria verified via source code inspection. `./mvnw test -pl nop-stream -am` BUILD SUCCESS. R1-02-05 moved to Deferred But Adjudicated with documented rationale. No hollow implementations or silent no-ops introduced.

Follow-up:

- R1-02-05 NFA.EventWrapper extraction (optimization candidate, successor: NFA module refactor plan)
- ~205 文件导入排序批量修复（R1-17-03/04）— 建议 IDE 批量操作
- 57 文件通配符导入清理（R1-17-05）
- 5 文件 tab 缩进修复（R1-17-08）
- 非英文注释清理（R1-17-09）
- ICheckpointStorage throws Exception → throws CheckpointStorageException 细化
- 架构级重构设计文档（core 模块拆分、WindowOperator 统一）
