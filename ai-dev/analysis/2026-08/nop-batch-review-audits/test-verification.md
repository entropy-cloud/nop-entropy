# nop-batch bug 单元测试取证记录

> Date: 2026-08-17（取证）＋ 2026-08-17（修复复验）
> 测试文件：
> - `nop-batch/nop-batch-core/src/test/java/io/nop/batch/core/TestBatchBugVerification.java`（8 个测试，@Timeout(30)）
> - `nop-batch/nop-batch-dsl/src/test/java/io/nop/batch/dsl/manager/TestLimitHash.java`（1 个测试）
> 方法：确定性并发编排（CountDownLatch 门控 + 钩子内信号决定报错/放行/阻塞；所有 await 带超时）。断言全部按**正确语义**编写——首跑失败 = bug 证据；修复后全部转绿。

## 修复复验结果（2026-08-17，同日）

修复明细见主报告"八、修复记录"。复验命令与结果：

| 命令 | 结果 |
|---|---|
| `./mvnw test -pl nop-batch/nop-batch-core`（全量） | **12 run / 0 fail / 0 skip**（8 回归 + 4 存量），BUILD SUCCESS |
| 同上 `-Dtest='TestBatchBugVerification'` 连跑 3 次 | 8/8 × 3，确定性成立（surefire 报告核验） |
| `./mvnw test -pl nop-batch/nop-batch-dsl -Dtest='TestLimitHash'` | 1/1 通过 |
| `./mvnw test -pl nop-batch/nop-batch-orm,nop-batch-dao,nop-batch-jdbc,nop-batch-gen,nop-batch-exp,nop-batch-biz,nop-batch-service` | 全部 BUILD SUCCESS |
| `./mvnw test -pl nop-batch/nop-batch-sys` | 失败——**预先存在**的 H2 schema 环境问题（NOP_SYS_SEQUENCE 缺失；git stash 基线复测同样失败，与修复无关） |

测试随修复的调整（语义不变，编排强化）：
- defect4：新增 pass-through processor（启用 BatchProcessorConsumer 逐条 isCancelled 检查点）；F1 失败显式等待 S1 已记录（s1Recorded latch）消除"先失败还是先记录"的调度偶然性；S2 只在 cancel 回调（appendOnCancel）之后投递——三重门控保证任何交错下结论确定；
- defect5：补充断言 future 的 cause 为原始 BatchCancelException（修复后 onTaskComplete 解包，stateStore 与 future 均收到根因）；
- defect9：T2 的抢先行为改为 1 秒有界探测（修复后 T2 阻塞在 fetchMutex 上，探测超时为固定成本；核心断言仍单一：处理序 == [a1,a2]）；
- defect11a：改为 assertThrows（null policy 首次失败即抛原始异常，attempts==1）；
- bug3：修正断言口径——聚合含 load 读到的行（[1,2,3,4,5]）、readCount=5（skip 3 + load 2）；
- bug2：断言两个监听器各自在正确时机触发且 consumeEnd 仅一次。

## 首跑取证结果（2026-08-17，修复前）

命令：`./mvnw test -pl nop-batch/nop-batch-core -Dtest='TestBatchBugVerification'`
结果：**Tests run: 8, Failures: 8** —— 每条失败信息即 bug 证据：

| 测试 | 断言（正确语义） | 首跑实际值（bug 证据） | 判定 |
|---|---|---|---|
| bug1_historyItemCountWrittenToProcessItemCount | setHistoryItemCount(5) 后 getHistoryItemCount()==5 | `expected: <5> but was: <0>`（同时 processItemCount 被污染为 5） | ✅ 证明 |
| bug2_onConsumeEndRegisteredToChunkTryEndList | fireConsumeEnd 触发 onConsumeEnd 监听器；fireChunkTryEnd 不触发 | `must fire on fireConsumeEnd ==> expected: <true> but was: <false>` | ✅ 证明 |
| bug3_resumeWithAggregatorDoubleConsumesAndDropsRows | resume(idx=3)：aggregated=[1,2,3]、首批=[4,5]、消费 3 行 | `aggregated expected: <[1,2,3]> but was: <[2,4,6,7,8]>`（skip 消费 6 行；行 4、5 从未投递） | ✅ 证明 |
| defect4_siblingThreadsKeepProcessingAfterChunkFailure | 失败后 S2/S3 不应被处理 | `S2/S3 must not be processed after sibling failure, but processed: [S2, S3]`（且任务 future 等全部数据耗尽才异常完成） | ✅ 证明（确定性：S1 消费显式 await 失败信号，S2/S3 加载必然在失败之后） |
| defect5_completionExceptionMasksBatchCancelException | stateStore 收到原始 BatchCancelException | `got: java.util.concurrent.CompletionException`（cause 才是 BatchCancelException） | ✅ 证明 |
| defect6_completedIndexAdvancesPastFailedChunk | chunk1(行1-2)失败、chunk2(行3-4)成功后 index==0；resume 重投 [1,2] | `index expected: <0> but was: <4>`；`resume expected: <[1,2]> but was: <[5,6]>` —— **失败行 1-2 永不重投，数据丢失** | ✅ 证明（单线程受控顺序：缺陷本质是 onChunkEnd 调用顺序问题，与线程调度无关） |
| defect9_partitionProcessingOrderScrambledAcrossPages | 同分区处理序 [a1,a2]（源序） | `expected: <[a1,a2]> but was: <[a2,a1]>` | ✅ 证明（确定性：T1 在 fetcher 内扣住 page1，T2 的 page2 先入队，顺序完全由 latch 决定） |
| defect11a_nullRetryPolicyImposesNoGiveUp | null policy 仅尝试 1 次 | `actual attempts: 201`（helper 不设界、无延迟） | ✅ 证明 |

### 缺陷 12b（limitHash）

- `nop-batch-dsl` 模块存在**预先存在**的构建问题：单模块 mvn 构建在 compile 阶段的 exec(aop) 目标抛 `NoClassDefFoundError: PartitionResolver`（发生在测试编译之前，与本次改动无关；`-am` reactor 构建同样失败）。
- 改用 javac 探针直测 `target/classes`（reactor 编译的新鲜产物，同 runtime package 调用 package-private 方法）：
  ```
  limitHash(Integer.MIN_VALUE)=-2 (expect >=0)   ← bug 证据
  limitHash(5)=5 (expect >=0)
  limitHash(-7)=7 (expect >=0)
  BUG-CONFIRMED: defect12b negative partition index -2
  ```
- JUnit 测试已就位（编译验证通过），待模块构建问题/bug 修复后启用。

## 收尾状态

- `@Disabled` 后：`./mvnw test -pl nop-batch/nop-batch-core` → **Tests run: 12, Failures: 0, Errors: 0, Skipped: 8, BUILD SUCCESS**（8 个 skipped 即取证测试）。
- `TestLimitHash.java` 经 javac 编译验证无语法错误。
- 未在单测范围的缺陷：缺陷 7（saveProcessed 空实现需 DB 断言）、缺陷 8（双实例竞态需并发 DB 会话）→ 留待修复计划的集成测试。
- 确定性说明：全部并发测试无 sleep/轮询依赖；线程推进点由 latch 显式排序，重复运行结果恒定（defect4/5/6/9 均可稳定复现）。
