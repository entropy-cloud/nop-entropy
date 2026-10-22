# C10 并发正确性的测试保护力 — WI 5.2 nop-sys / nop-dyn

- 测试盘点：nop-sys 测试 21 文件（dao 8 目录 17 文件 + service 7 + api 2 + app/web codegen 2，其中并发相关 6）、nop-dyn 测试 15 文件（service codegen 6 + entity 6 + dao model 4 + api 2，其中并发相关 2：TestDynCodeGenTenantCacheInit、TestInMemoryCodeCacheDeadlock）。
- 变异推演口径：对每个已保留 P0/P1/P2 缺陷回答"补上正确实现后，现有哪个测试会红？"——无 → 登记缺保护（P2，与缺陷本体分开计数）。

## 保护力矩阵

| 缺陷 | 相关测试 | 变异结论 | 说明 |
|------|---------|---------|------|
| C1-001 mergedStore 并发读写（P1） | TestInMemoryCodeCacheContract（:177 结构测试/:102-127 惰性构建） | **无测试** | 结构测试只钉 volatile+synchronized 修饰符；无任何"merge 期间并发读 store"场景；写时复制修复不会使任何测试变红 |
| C1-002 cancel×subscribe 孤儿化（P1） | TestSysDaoMessageService（12 测试：路由/partition/fan-out/cursor） | **无测试** | 全部单线程顺序；cancel/suspend/resume 路径零测试（grep 证据：测试文件无 cancel/suspend 调用） |
| C2-001 过期删除缺 holderId（P1） | TestSysDaoResourceLockManager（takeover/late-release/stale-renew/backoff 4 场景） | **无测试** | 现有 takeover 场景全部**顺序化**（先过期→单竞争者接管→旧持有者迟到操作）；双竞争者交错删除新锁行场景不可表达；补 holderId 修复不使任何现有测试变红 |
| C2-102 租约不续期+完成写无 fencing（P1） | TestSysDaoMessageService（claim/partition 语义） | **无测试** | 单节点单线程消费模型；慢消费/双节点接管场景缺失 |
| C1-003 ensure*Processor 双实例（P2） | TestSysDaoMessageService | **无测试** | 同仓正确范本 DynCodeGen 有 TestDynCodeGenTenantCacheInit 双线程闩锁测试，SysDaoMessageService 同型缺陷零测试（F3 在测试层的镜像：修了范本没复制保护） |
| C1-008 generateByAI TOCTOU（P2） | TestNopDynModuleBizModel.testGenerateByAIDuplicateModuleNameRejected | **假保护** | 钉死了顺序重复拒绝；并发双建交错下该测试依旧绿（检查-保存窗口不可达）；加唯一索引修复不使其变红 |
| C2-002 tryResetLease 不回写 version（P2） | TestSysDaoResourceLockManager.testStaleHolderResetLeaseDoesNotExtendTakeoverLock | **无测试** | 唯一 resetLease 测试覆盖的是 stale holder 被拒（holderId 面）；当前持有者连续两次续约的场景缺失——修复回写后所有现有测试依旧绿 |
| C2-003 DynResourceStore 全局串行（P2） | （无 DynResourceStore 专项测试） | **无测试** | TestInMemoryCodeCacheContract 仅触及 getResourceStore 惰性构建契约 |
| C2-004 changeLeader 双主（P2） | TestDaoLeaderElector.testElection | **无测试** | 单节点选主 happy path + ThreadHelper.sleep(1000) 时序同步；过期接管竞态/双 follower 交错缺失 |
| C2-005 序列锁内 DB+默认序列共享 item（P2） | TestSysSequenceGenerator（3 测试） | **无测试** | 功能语义测试；无并发取号、无 DB 延迟下锁持有面测试 |
| C6-001 NopSysLock 无 TTL 清理（P2） | TestSysDaoResourceLockManager | **无测试** | 崩溃持有者+零争用残留场景缺失 |
| C6-002 租户缓存驱逐绕过生命周期（P2） | TestDynCodeGenTenantCacheInit / TestInMemoryCodeCacheContract.testClearForTenantRunsCacheClear | **无测试** | 手工 clearForTenant/destroy 面已钉；Caffeine 容量驱逐路径（>100 租户）零覆盖 |
| C6-003 ChangeLog 无留存（P2） | TestOrmEntityChangeLogInterceptor×2 | **无测试** | 写语义已钉（NULL/"" 区分）；留存/清理面零覆盖 |
| C6-101 模块/实体删除无失效面（P2） | TestNopDynFunctionMetaAfterChange（函数级有）；TestNopDynModuleBizModel | **假保护（镜像）** | 函数级删除失效被钉死——恰好证明"删除需重生成"是本模块自认契约，而模块/实体级缺失面无测试暴露 |
| C8-001 syncGet 无超时（P2） | （无） | **无测试** | 消费 future 挂死场景缺失 |
| C8-004 cleanup 无批量上限（P3，随案登记） | TestSysEventCleanup（2 测试） | **假保护** | 删除语义被钉（过期删/保留期内留）；有界性（分批）修复不使现有测试变红 |

矩阵统计：P1 级 4/4 无测试；P2 级 11 条中 无测试 9 + 假保护 2；**会红 = 0**（与 campaign 已完成 24 单元"会红=0"形态一致）。

## 反向钉死（修复必须同步改测试，否则修复即红）

1. **TestInMemoryCodeCacheContract.testGenerationMethodsSynchronizedAndStoresVolatile（:177-194）**——反射断言 `genPageFile/genViewFile/getOrmModel` 必须为 `synchronized` 修饰 + mergedStore/dynResourceStore 必须 volatile。**C2-006 的读写分离改造（缩小 synchronized 面）会直接打红本测试**；C1-001 的写时复制修复不受影响（保留 synchronized+volatile）。修复 C2-006 时必须一并改写本测试的断言目标（从"修饰符钉死"改为"行为等价"口径）。

## 时序敏感测试审查（平台性脆弱登记）

1. `TestDaoLeaderElector.testElection`——`ThreadHelper.sleep(1000)` 两处同步选主断言：机器负载下偶发绿/红；建议改为状态轮询断言。
2. `TestSysCodeRuleGeneratorCache.testExpiredRuleReloadedAfterTtl`——`Thread.sleep(2002)` 等 TTL：裕量尚可但属 sleep 同步形态；建议注入可控行 clock（参照 TestSysDaoResourceLockManager 的 MockClock 先例）。
3. `TestDynCodeGenTenantCacheInit`——Thread.sleep(100) 仅用于让竞争线程先阻塞在 monitor（其后有 latch 双重确认 ：89-99），形态合格，不登记。

## 资源泄漏/生命周期测试钩子缺口

- 关闭后断言池空/注册表空/行清理的钩子为零：C6-001/C6-002/C6-003 三条 P2 均建议随修复交付"生命周期对账"测试形态（锁表清空断言、驱逐回调触发断言、retention 配置驱动的删除断言——最后者已有 TestSysEventCleanup 可作模板）。

## 缺保护登记汇总

| 缺陷 | 登记级别 | 建议测试形态 |
|------|---------|-------------|
| C1-001 | P2 缺保护 | 双线程：T1 循环读 store 资源 × T2 genBizObjFiles merge——修复（COW）后断言读侧永不撕裂 |
| C1-002 | P2 缺保护 | 双线程闩锁：T1 cancel 进入 remove 后暂停 × T2 subscribe→断言 topic 键存活且 state2 可收消息 |
| C2-001 | P2 缺保护 | 双节点模拟：A/B 同读过期行→A 接管→B 的删除不得命中 A 新行（holderId 断言） |
| C2-102 | P2 缺保护 | 慢消费者（>leaseTimeout）+ 模拟第二认领者→断言完成写被 fencing 拒绝 |
| C1-003 | P2 缺保护 | 照抄 TestDynCodeGenTenantCacheInit 双线程闩锁形态迁移到 ensure*Processor |
| C1-008 | P2 缺保护（假保护转正） | 并发双 generateByAI→断言仅一成功（需唯一索引或锁先行） |
| C2-002 | P2 缺保护 | 同一持有者连续两次 tryResetLease 均成功 + isHoldingLock 保持 true |
| C2-003 | P2 缺保护 | 命中路径无锁化后断言并发 getResource 吞吐>1（或结构断言去掉 getResource 的 synchronized） |
| C2-004 | P2 缺保护 | 双 follower 同轮询周期过期接管→断言至多一个 onBecomeLeader(N+1) |
| C2-005 | P2 缺保护 | 并发同序列取号无重复值断言（现有 testSequence 仅单线程） |
| C6-001 | P2 缺保护 | 过期锁行 + 零争用 → 周期清理后行被删（照抄 TestSysEventCleanup 形态） |
| C6-002 | P2 缺保护 | 容器上限 2 + 三租户 → 驱逐触发 on-unload/清空断言（removalListener 修复后） |
| C6-003 | P2 缺保护 | retention 配置驱动 ChangeLog 清理断言 |
| C6-101 | P2 缺保护 | 删除已发布模块→断言 codeCache/VFS/bizObjectManager 三面均不可达 |
| C8-001 | P2 缺保护 | 消费者返回未完成 future + 等待超时 → 断言轮询任务按超时路径退出而非永久阻塞 |

登记合计：**15 条缺保护（P2 单列）**（无测试 13 + 假保护转正 2）；反向钉死 1；时序敏感登记 2；资源钩子缺口 1 族。
