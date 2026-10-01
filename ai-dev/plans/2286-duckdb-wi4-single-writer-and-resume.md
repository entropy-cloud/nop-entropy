# 2286 DuckDB WI4 — 单写者与续跑语义

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI4；WI0 裁定报告（问③锁冲突形态）；WI1-WI3 交付
> Related: 2283/2285

## Purpose

把 WI0 实测的单写者约束转成显式产品语义（错误码/可读消息/retry 裁定），测试独立文件并行与多读单写，验证 duckdb 步骤参与的任务跨"重启"续跑且数据一致，覆盖 native 缺失/临时目录不可写等故障注入。

## Current Baseline

- WI0 问③实测：跨进程第二写者 → SQLException "IO Error: Could not set lock on file ... Conflicting lock is held in <java> (PID x) by user y"，SQLState=null、errorCode=0；同 JVM 多连接共享实例；未提交事务 kill 后回滚、已提交存活
- WI1 现状：openConnection 仅翻译 "different configuration" → config-conflict；锁冲突消息目前落入 connect-failed（语义未区分）
- WI3 已交付 DuckDbSqlTaskStep（getLocalValue 输入语义、无状态单例）；RetryPolicy 对非 bizFatal NopException 全部可重试（WI3 裁定永久错误精细化归本 WI）
- nop-task 续跑机制：suspend 步骤 + ITaskStateStore。可用的快照 store 模板是 **nop-task-core test 源码的 FullSnapshotTaskStateStore**（113 行：覆写全部 5 个方法、copyStep 拷贝 stateBean+bodyStepIndex、loadTaskState/saveTaskState 齐备）+ TestSuspendContract（suspend→getTaskRuntime(recoverMode)→resume 完整范例）；nop-task-ext 的 SnapshotResumeTaskStateStore 不拷贝 stateBean/bodyStepIndex 且未实现 loadTaskState，**不可作模板**。模板在 test scope，需复制进 nop-duckdb 测试树（约 70-110 行）
- WI2 契约：.duckdb 文件为任务级工作集，关闭重开已提交数据一致

## Goals

- 锁冲突显式语义：新错误码 file-locked（英文消息含文件路径与持锁者信息），openConnection 识别 "Could not set lock on file"/"Conflicting lock is held" 翻译；**retry 裁定：file-locked 与 config-conflict 为 bizFatal（不可自动重试）**——持锁方释放/配置修正才能恢复，自动重试只是掩盖；其余 duckdb 错误维持可重试（WI3 口径）
- 并行语义实证：独立文件并行任务全绿；同文件多读一写（同 JVM）正常
- 续跑语义实证：duckdb 步骤参与的 task 经 suspend 中途"kill"（丢弃内存状态）→ 恢复链 store.loadTaskState → taskFlowManager.getTaskRuntime(taskInstanceId, recoverMode) → task.execute → 后续步骤读到 kill 前数据（数据一致）。store 为复制进测试树的 FullSnapshotTaskStateStore 最小实现（必须拷贝 stateBean/bodyStepIndex 并实现 loadTaskState/saveTaskState）
- 故障注入：native 缺失（步骤层，BrokenNative 模式）；临时目录不可写（压低 memory_limit 强制溢出 + temp_directory 指向不可写路径 → 显式 SQLException 浮出为步骤层 io-failed，不产生部分写）；磁盘满以"外存不可写"代理（真实 ENOSPC 无法确定性注入，登记裁定）

## Non-Goals

- 不做多进程任务调度/分布式锁（单写者边界由 DuckDB 文件锁承担）
- 不改 nop-task-ext（续跑 store 与终态恢复的既有能力不重测——DB-backed store 已由该模块测试家族覆盖：TestDbBackedTerminalStateResume、TestDaoTaskStateStoreStateDataWrapperRoundTrip、TestDaoTaskStateStoreErrorBeanRoundTrip 等，作为降级裁定证据）
- 不做对拍/性能（WI5/WI7）；使用文档归 WI9

## Scope

### In Scope

- nop-duckdb：file-locked 错误码 + openConnection 锁冲突翻译 + bizFatal 裁定（config-conflict/file-locked/invalid-config/file-not-found/table-exists/invalid-step-input/engine-closed 七个永久错误码 bizFatal=true）
- 测试：跨 JVM 锁冲突（ProcessBuilder）、独立文件并行、同文件多读一写、suspend-resume 数据一致性（快照 store 内置于测试）、native 缺失步骤层、不可写 temp_directory 溢出注入
- 当日 ai-dev/logs/ 更新

### Out Of Scope

- nop-task-ext / nop-dao 改动；真实 ENOSPC 注入；CI

## Execution Plan

### Phase 1 - 锁冲突显式语义（生产代码）

Status: completed
Targets: `nop-duckdb/src/main/java/io/nop/duckdb/`

- Item Types: `Fix`（live 语义缺口：锁冲突现落入 connect-failed，语义不可辨）

- [x] NopDuckDbErrors 增 file-locked（英文描述含 filePath/reason）
- [x] DuckDbEngine.openConnection：锁冲突消息翻译为 file-locked
- [x] bizFatal 裁定落地：**八个**永久错误码（file-locked/config-conflict/invalid-config/file-not-found/table-exists/invalid-step-input/engine-closed/**native-load-failed**——平台缺 native 重试无意义且现状会被 RetryPolicy 自动重试，一并裁定）抛出处 bizFatal(true)（NopException.bizFatal(boolean) 链式方法，RetryPolicy.isRecoverableException 读 isBizFatal 且本路径无 exceptionFilter）；可重试面（SQL 执行失败 io-failed 等）维持 WI3 口径

Exit Criteria:

- [x] 锁冲突/config-conflict 等永久错误不再被 RetryPolicy 自动重试（断言由 Phase 2 第 2 项交付：TestDuckDbSingleWriter.testFileLockedErrorIsNotRetried，attempts=1 且诚实抛出）
- [x] `./mvnw install -DskipTests -pl nop-duckdb -am` 退出码 0（实际以更强口径验证：`./mvnw test -pl nop-duckdb -am` EXIT=0，编译+测试全过）
- [x] No owner-doc update required（WI9 承接）
- [x] `ai-dev/logs/2026/10-01.md` 已更新

### Phase 2 - 并行/续跑/故障注入测试

Status: completed
Targets: `nop-duckdb/src/test/java/io/nop/duckdb/`、test resources

- Item Types: `Proof`

- [x] 跨 JVM 锁冲突：child main 类进 nop-duckdb/src/test/java（持锁 + READY 握手 + stdin 阻塞）；ProcessBuilder 用 System.getProperty("java.class.path")（先例 MiniStreamCluster）+ watchdog 超时；主进程 openFile 抛 file-locked（bizFatal），消息含路径；**测试 finally 与失败路径均 destroy 子进程**防遗留持锁 JVM（TestDuckDbSingleWriter.testCrossJvmLockConflictIsExplicitFileLocked + LockHolderMain）
- [x] bizFatal 不重试：retry.task.xml 挂 file-locked 场景（flaky 包装首试抛 file-locked）→ 断言不重试（attempts=1、诚实抛出）（TestDuckDbSingleWriter.testFileLockedErrorIsNotRetried + TestFileLockedStep + filelocked-retry.task.xml）
- [x] 独立文件并行：两个 engine 各写各的 .duckdb 文件（多线程），互不干扰全绿（testParallelIndependentFiles）
- [x] 同文件多读一写：同 JVM 一写多读连接并发操作不冲突（testSameJvmMultiReadSingleWrite）
- [x] suspend-resume 数据一致性：task = [duckdb 步骤建表插数 → suspend → duckdb 步骤查询]；store 为复制版 FullSnapshotTaskStateStore（拷贝 stateBean/bodyStepIndex、实现 loadTaskState/saveTaskState）；执行至挂起（模拟 kill：丢弃 runtime 与 engine）→ loadTaskState 恢复 + getTaskRuntime + 新 engine 重开同 dbPath → resume → 查询步读到 kill 前数据（TestDuckDbResume + TestSnapshotTaskStateStore + resume.task.xml）
- [x] 故障注入：步骤层 native 缺失（engine 子类 TestBrokenNativeEngine 在 newJdbcConnection 边界抛 ExceptionInInitializerError → **native-load-failed** 透传，不裸穿 Error）；temp_directory 不可用（常规文件充当 temp 目录，ENOTDIR 跨平台确定）+ memory_limit=64MB 触发外存路径 → **失败形态实测固化：io-failed（"COPY failed: Out of Memory Error: could not allocate block"，duckdb 自管 OOM Error 经步骤层 SQLException 包装）+ 目标文件无部分写**，不锚定具体消息；另加 default-engine control 测试隔离故障变量（TestDuckDbFaultInjection 3 测 + TestSpillEngine + fault-spill/control.task.xml）
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] **端到端验证**：suspend-resume 全链（含真实文件工作集跨"重启"）有测试贯通且数据一致断言（TestDuckDbResume.testSuspendKillResumeDataConsistent）
- [x] 新增语义（file-locked/bizFatal/续跑/注入）每项至少一测
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] No owner-doc update required（WI9 承接）
- [x] `ai-dev/logs/2026/10-01.md` 已更新 WI4 收口记录

执行偏差（相对计划文本，已按实测修正落地）：

1. **BrokenNative 挂钩点**：`newJdbcConnection` 在 `DuckDbEngine` 而非 step 上——改为 engine 子类 `TestBrokenNativeEngine`（plain DuckDbSqlTaskStep bean 注入该 engine），语义等价（异常仍经 openConnection 的 catch(Throwable) 包装为 native-load-failed）
2. **resume 需重放启动输入**：`TaskStateBean` 无 inputs 字段——全平台 resume 路径都不携带 launch inputs，重放输入本就是调用方/编排器职责（ext 家族 resume 测试不涉及 inputs 因其任务无 launch inputs，见审计裁定）。本测试显式 `setInput("dbPath",...)` 即编排器重放角色；被测核心语义（步骤状态持久化 + 工作集跨重启数据一致）不受影响
3. **溢出失败实测形态**：不可用 temp_directory 下 DuckDB 报的是自管 "Out of Memory Error"（无法溢出转而预算内分配失败）而非写权限 IO Error；经步骤层包装后为 io-failed——按 plan "以实测为准固化" 采纳，消息不锚定
4. **64MB 预算过紧**：即使 ~9MB 小负载也会因 DuckDB 30.5MB 连续块分配失败（印证 WI0 "溢出非无条件成立"分级记录）——control 测试改走默认 engine 隔离变量
5. **invalid-config bizFatal 补齐**：验证中发现计划八个 bizFatal 错误码中 invalid-config（execSet/invalidConfig 两处）未加 bizFatal(true)，按计划文本补齐

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（锁冲突语义不可辨为本 WI 的 Fix 输入）
- [x] 行为/契约结果已达成：单写者失败可读可辨、不假装可并行；续跑数据一致
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确 No owner-doc update required（显式归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：audit 验证锁冲突翻译真实触发（跨进程测试）、resume 链真实经过 state store、无空壳
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] 代码规范核对通过（本构建未接 checkstyle 工具，口径=import 分组/命名/缩进人工核对 + scan-hollow 0，独立审计员同口径复核 PASS）
- [x] scan-hollow-implementations --module nop-duckdb --severity high 退出码 0

## Deferred But Adjudicated

### 真实磁盘满（ENOSPC）注入

- Classification: `watch-only residual`
- Why Not Blocking Closure: ENOSPC 无法在常规 CI 环境确定性构造（需特殊文件系统/配额）；"外存不可用"代理测试已覆盖同一产品契约面（temp 目录不可用 + 强制外存执行 → duckdb 自管显式失败 → 步骤层 io-failed + 无部分写；实测形态为不可溢出时的预算内分配失败 OOM Error，见执行偏差 3）
- Successor Required: `no`
- Successor Path: 无（若未来引入可注入 FS 再补）

## Non-Blocking Follow-ups

- lock conflict 的 retry 语义当前为 bizFatal 不可重试；若未来出现"读等待锁释放"场景需求（带超时的 retry-able lock），另行立项

## Closure

Status Note: WI4 收口。独立 closure audit（fresh subagent，全量 Exit Criteria/Closure Gates 逐条核对 + 四道验证门复跑 + Anti-Hollow 三项亲验 + bizFatal 八码全量清单）唯一 blocker 为日志缺失，已补写；两条 Minor 文本修正（偏差 2 措辞、checkstyle gate 措辞）已按审计意见落地。锁冲突显式语义（file-locked bizFatal）、独立文件并行、同 JVM 多读一写、suspend-resume 跨重启数据一致、native/外存故障注入全部有测试实证，37/37 全绿。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理（fresh session，read-only audit）
- Audit Session: agent_aa93807d-d447-49ac-8694-14bc6b00fba3
- Evidence:
  - Phase 1 Exit Criteria：EC1.1 PASS（testFileLockedErrorIsNotRetried attempts=1 + RetryPolicy.isRecoverableException 读 isBizFatal 机制核实）；EC1.2 PASS（审计员复跑 -am EXIT=0）；EC1.3 PASS（WI9 承接裁定）；EC1.4 首轮 FAIL（日志缺失）→ 已补写 `ai-dev/logs/2026/10-01.md` WI4 条目
  - Phase 2 Exit Criteria：EC2.1 PASS（TestDuckDbResume 全链 + verifyRows==1 断言 kill 前数据）；EC2.2 PASS（file-locked/bizFatal/续跑/注入每项有测）；EC2.3 PASS（双口径 EXIT=0）；EC2.4 PASS；EC2.5 首轮 FAIL → 同上补写
  - Closure Gates：全表 PASS（含 Anti-Hollow 三项：(a) file-locked 翻译真实触发——LockHolderMain 子 JVM 持真实文件锁 + getCause() 非空证明翻译非伪造；(b) resume 链经 TaskFlowManagerImpl:124 stateStore.loadTaskState 强制路径 + stateBean 拷贝使 suspend first 标记存活（TaskStepRuntimeImpl:155 回灌）；(c) src/main 6 文件逐行读毕无空壳，scan-hollow 0 交叉印证）
  - bizFatal 抽查：八码全部抛出点带 bizFatal(true)（含 config-conflict ×2、invalid-config ×2）；io-failed ×6 + connect-failed ×1 无 bizFatal，可重试口径维持
  - `node ai-dev/tools/check-plan-checklist.mjs` 与 `scan-hollow-implementations --severity high` 退出码 0
  - Deferred 项分类检查：ENOSPC watch-only residual 裁定成立；Non-Blocking Follow-ups 无 in-scope defect 藏匿
  - 审计 Minor 修复记录：偏差 2 措辞更正（TaskStateBean 无 inputs 字段，重放输入是编排器职责）、checkstyle gate 措辞改写、Deferred 措辞精确化、无关工作区噪音 .rels 不随本提交

Follow-up:

- lock conflict 的 retry 语义当前为 bizFatal 不可重试；若未来出现"读等待锁释放"场景需求（带超时的 retry-able lock），另行立项（Non-Blocking Follow-ups 既有条目，不变）
- no remaining plan-owned work
