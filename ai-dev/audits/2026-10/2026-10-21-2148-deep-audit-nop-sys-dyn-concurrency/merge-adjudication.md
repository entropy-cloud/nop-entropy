# 合并裁定 — WI 5.2 nop-sys-dyn 并发审计（C1/C2/C6/C8 + C10）

- 裁定日期：2026-10-22
- 裁定输入：本目录四维度主文件（含复核后「最终保留项」）+ 四深挖第 2 轮文件（含复核结论）+ `C10-test-protection.md` + `ai-dev/audits/cra-index.md`（P1 台账 **159 条**口径查重）
- 裁定口径：同族相邻按「根因 + 失效层」合并，防止同一缺陷被多维度重复计数；判级取证据最全的主登记；条件化（休眠/潜伏面）成员显式标注激活回升条款；watch-only 条目不入工单计数、单列台账；每条保留发现逐条落位（族/独立/watch）无遗漏

---

## §一 裁定口径、输入与计数对账

### 输入与范围

| 输入 | 条数 |
|---|---|
| C1 初审 | 9（C1-001…009；P1×2/P2×2/P3×5） |
| C2 初审 | 7（C2-001…007；P1×1/P2×4/P3×2） |
| C6 初审 | 5（C6-001…005；P2×3/P3×2） |
| C8 初审 | 4（C8-001…004；P2×1/P3×3） |
| 深挖第 2 轮新增 | 8（C1-101/102、C2-101/102、C6-101/102、C8-101/102） |
| **发现小计** | **33**（复核：保留 31 / 降级 watch 2〔C1-004、C6-005〕/ 驳回 0） |
| C10 缺保护登记 | 15（P2 单列）+ C8-004 假保护随案 1（矩阵 16 行） |
| overlay 线索 | 预注 CL 候选 1（周期任务族，前缀 `nop-sys-dyn-`）+ C3/C4/C5/C7/C9 五形态（零命中/并入，见 §八） |

### 计数口径对账（输入 → 输出）

- **33 条发现 → 17 张工单**（7 缺陷族 MA-01~07 承载 21 条 = 主登记 7 + 吸收 14；10 独立 I-01~10）+ **watch 台账 2 条**（W-01 C1-004、W-02 C6-005）。
- 判级分布（工单口径）：**P0×0（显式空执行）/ P1×3 / P2×8 / P3×6**。
- 发现级判级（留档）：P1×4（C1-001/C1-002/C2-001/C2-102）、P2×11、P3×16（含 watch 2）。
- 族吸收：MA-01 吸收 4（C2-002/004/007/102）、MA-02 吸收 2（C2-006/101）、MA-03 吸收 2（C6-102 + 跨维度 C1-101）、MA-04 吸收 1（C6-003）、MA-05 吸收 1（C1-007）、MA-06 吸收 1（C1-005；C1-004 watch 搭车）、MA-07 吸收 3（C8-002/102/004）。
- 发现覆盖对账：族承载 21 + 独立承载 10 + watch 2 = **33 = 33 闭合**，无遗漏。
- C10 缺保护 15 条单列随对应工单落地（§九），会红 = 0。
- cra-index P1 台账 159 条逐单元查重：**同站点重复 0**；家族关联 11 处单列（§七）。

### 冲突与勘误裁定清单（6 项）

1. **租户缓存三站点归族（推翻维度复核建议）**：C6 复核建议「C6-002/C6-102 裁定阶段保留为独立工单（修复面不同：removalListener vs 预算/异步化）」；裁定改为 **MA-03 单族合并**——三缺陷（驱逐/冷启动/destroy）是同一资源（DynCodeGen tenantCache）同一生命周期缝的三个转变点，同文件单批治理，内部保留三个子修复面不混同；判级取主登记 P2。
2. **C8-001 落位裁定**：C8 复核建议「与 C2-102 两单互列关联」；裁定 C8-001 **独立立项 I-05**（F5 无界等待本体独立于 fencing 缺失——C2-102 修复不消除挂死停摆，超时修复不消除重复处理），与 MA-01 双向关联、修复批次协同。
3. **C1-004 降级确认**：初审 P3 → 复核降级 watch-only（纳秒级读窗、无状态损坏面）；裁定确认降级，随 MA-06 快照对象化批搭车消解，不入工单计数。
4. **C6-005 watch 确认**：复核维持 P3 但标注信心=有趣的猜测（依赖内核 findNext 语义未证）；裁定入 watch 台账并登记触发条件（§五#4）。
5. **C2-002 条件判级**：维持 P2 并附**激活回升条款**——全仓库当前无生产周期续约调用方（复核 grep：唯一调用方为测试单次续约场景）；上游引入 watchdog 续约即升 P1（开放项 §五#1）。
6. **反向钉死相容性裁定**：`TestInMemoryCodeCacheContract.testGenerationMethodsSynchronizedAndStoresVolatile`（:177-194）反射钉死 `genPageFile/genViewFile/getOrmModel` synchronized 修饰 + mergedStore/dynResourceStore volatile——**I-01（C1-001）写时复制修复保留 synchronized+volatile，不触红**；**MA-02 读写分离改造必须同 diff 改写本测试断言**（修饰符钉死 → 行为等价口径），否则修复即红。

---

## §二 缺陷族合并（MA-01~07）

| 族 | 判级 | 主登记 | 吸收成员 | 根因 + 失效层 | 修复形态（同 diff 批） |
|---|---|---|---|---|---|
| **MA-01** DB 行协调原语 fencing 纪律族 | **P1** | C2-001 | C2-002、C2-004、C2-007、C2-102 | 根因：DB 行协调原语（锁行/租约/选主/事件认领）以非条件写或无 fencing 写决出协调结果，CAS 成功后内存状态不同步；失效层：nop-sys-dao 三类协调原语五站点 | 三个子批：①SysDaoResourceLockManager 锁行状态机（:151 过期删除补 holderId 条件 + tryResetLease 成功回写 `entity.setVersion(v+1)`）；②SysDaoLeaderElector 条件更新（changeLeader/tryRenewLease 改 `where leaderEpoch=N AND expireAt=旧值`/`where leaderId=self`，停止关闭版本检查）；③NonBroadcastEventProcessor 认领续租（leaseTimeout 分数间隔 tryResetLease）+ 完成写 fencing（`where version=认领时版本 AND leaseOwner=self`，0 行放弃写）——C10-003/004 缺保护随批 |
| **MA-02** 动态模型生成锁全局串行族 | P2 | C2-003 | C2-006、C2-101 | 根因：动态模型服务路径单一全局生成互斥，临界区无界（DB I/O + XPL 生成 + 用户 on-load/on-unload 钩子）且覆盖高频命中读——生成锁=全局限速器；失效层：DynResourceStore/InMemoryCodeCache/DynCodeGen | 命中路径无锁化（先无锁读、miss DCL——范本 `DynCodeGen.getTenantCodeCache` :160-167）+ 读写分离/钩子移出临界区；文档声明生成期窗口；**反向钉死：TestInMemoryCodeCacheContract 修饰符断言须同 diff 改写（§一.3#6）** |
| **MA-03** 租户缓存生命周期治理族 | P2 | C6-002 | C6-102、C1-101（跨维度吸收） | 根因：租户 InMemoryCodeCache 三个生命周期转变点均无对称治理——Caffeine 驱逐绕过 on-unload 且摘除使用中实例、冷启动全量初始化在请求线程无预算、destroy 存在孤儿窗口；失效层：DynCodeGen tenantCache | ①eviction/removalListener 执行 clear()+on-unload；②初始化后台化/等待预算；③destroy `computeIfPresent` 或残留复扫；容器上限 ≥ 活跃租户数文档声明；getUsedTenantIds 监控失真随批修正 |
| **MA-04** nop-sys DB 行资源零保留族（F4） | P2 | C6-001 | C6-003 | 根因：状态行只创建无回收（同模块 event/naming 均有清理，锁行与审计行没有——F3 资源面漏项镜像）；失效层：sys_lock 表 + sys_change_log 表 | 照抄 `cleanupExpiredEvents` 形态：retention 配置 + 周期清理（`delete where expireAt < now - grace`）；ChangeLog saveDirectly 批量化（每列一会话写放大面同批）；C10 缺保护随批 |
| **MA-05** 序列取号默认回退路径族 | P2 | C2-005 | C1-007 | 根因：SEQ_DEFAULT 回退路径多序列名共享同一锁 item + synchronized(item) 内 REQUIRES_NEW DB 往返（含 session.lock 行锁等待）；且 defaultCache 只装不清（失效不彻底，TTL 60s 兜底）；失效层：SysSequenceGenerator | 回退项按 seqName 各建 item（不共享锁对象）；DB 环节 statement/锁超时上限；clearCache/removeCache 同步 `defaultCache.invalidate`——generateLong/generateString 两入口同批 |
| **MA-06** 懒初始化与快照发布卫生族 | P2 | C1-003 | C1-005；C1-004（watch 搭车） | 根因：检查-然后-行动懒初始化无同步（字段非 volatile）+ 双 volatile 字段无法原子替换；失效层：SysDaoMessageService ensure*Processor×2、DynOrmModelProvider、SysCompactExtFieldHelper | DCL/volatile 化（范本 DynCodeGen.getTenantCodeCache 有 TestDynCodeGenTenantCacheInit 钉死，测试形态照抄迁移）+ Snapshot record 一次 volatile 发布（消化 W-01） |
| **MA-07** 消息轮询管线无界工作族 | P3 | C8-101 | C8-002、C8-102、C8-004 | 根因：周期任务单轮工作无预算/无隔离/无退避（清扫 1000 轮、逐订阅者同步直调、全挂起空转认领-回退、清理 deleteByQuery 无 LIMIT）；失效层：SysDaoMessageService/BroadcastEventProcessor/NonBroadcastEventProcessor | 轮内时间预算（单轮 ≤ checkInterval×0.5）+ 每订阅者投递预算 + 挂起态短路/指数退避 + 清理分批 LIMIT 循环；清扫与广播 executor 隔离为可选子项 |

---

## §三 独立工单台账（I-01~I-10）

| 工单 | 判级 | 发现 | 一句话描述 | 修复约束 / Protected Area |
|---|---|---|---|---|
| I-01 | **P1** | C1-001 | 已发布 mergedStore 在读线程并发期间被原地结构修改（`genBizObjFiles` :320-328 merge + `addToMergedStore` :419-425，底层普通 TreeMap 无同步）——违反本类自声明只读发布契约，动态模型资源热路径撕裂读 | 恢复写时复制发布（沿用 SysCompactExtFieldHelper :50-52 模式）；COW 修复保留 synchronized+volatile **不触反向钉死测试**（§一.3#6） |
| I-02 | **P1** | C1-002 | DurableSubscription.cancel 与 subscribe 复合序列交错——订阅者孤儿化，后续事件 warn 后置 PROCESSED 静默丢弃 | cancel 判空+摘键改 `compute(topic, …)` 单步（与 subscribe computeIfAbsent 同键互斥）；家族关联 WI 4.1 MA-05（§七#2） |
| I-03 | P2 | C1-008 | generateByAI moduleName 去重 TOCTOU（表无唯一索引，注释自证）——并发双建重复模块行 + 动态模型混合态 | moduleName 唯一索引触碰 orm 模型 = **plan-first**；或分布式锁/单飞——选型留 7.1（§五#6） |
| I-04 | P2 | C6-101 | 动态模块/实体/属性删除后零缓存失效面——已删除模型（含其权限/校验逻辑）继续服务直至重启（函数级有对偶修复，F3 镜像；removeDynModule 悬空零调用方） | NopDynModuleBizModel/NopDynEntityMetaBizModel 补 afterEntityChange（照抄 NopDynFunctionMetaBizModel :31-47 形态）或文档声明手动重发布 |
| I-05 | P2 | C8-001 | invokeConsumer `FutureHelper.syncGet` 无超时阻塞轮询线程（F5）——消费 future 挂死则全部事件消费静默停摆、健康检测不可见 | 带超时等待（tryGet(future, leaseTimeout) 走 retryDelayProvider）+ stall 看门狗；与 MA-01（C2-102）双向放大关联、批次协同 |
| I-06 | P3 | C1-006 | InMemoryCodeCache.scope 跨代共享仅 clear() 清理——模板变量跨生成残留（生成串味风险）与引用滞留 | 每生成入口局部 `XLang.newEvalScope()` 或 genXxx 入口统一 scope.clear() |
| I-07 | P3 | C1-009 | NopSysUserVariableBizModel.setVar get-then-save TOCTOU——双开页面偶发主键冲突报错（重试自愈，无损坏） | 捕获 ERR_SQL_DUPLICATE_KEY 转 update/upsert 归一（WI 5.1 MA-05 先例形态） |
| I-08 | P3 | C1-102 | useTenant 模式无租户上下文请求落入从未初始化的共享 codeCache 并向其生成模块——两套事实旁路缓存 | fail-fast（ERR_TENANT_REQUIRED）或显式声明共享缓存语义边界——留 7.1（§五#3） |
| I-09 | P3 | C6-004 | doStop `cancel(false)` 不等待在途周期任务——停机窗口任务仍触达已停服务（优雅停机不彻底） | doStop 后 awaitDone/在途计数栅栏；SysDaoNamingService.destroy :81-87 同族一并 |
| I-10 | P3 | C8-003 | sendMultiAsync 逐条 saveMessage 无事务/无部分成功语义声明（F2）——批量重复或缺失无失败清单 | javadoc 声明 at-least-once + 幂等消费要求，或提供逐条结果列表变体 |

---

## §四 watch 台账（不入工单计数）

| 项 | 原判级 | 内容 | 处置 |
|---|---|---|---|
| W-01 | P3（降级） | C1-004 SysCompactExtFieldHelper.refreshCache 双 volatile 替换瞬态错配（纳秒级窗口，无状态损坏面） | 不立项；随 **MA-06** 快照对象化批一并消解 |
| W-02 | P3（watch） | C6-005 BroadcastEventProcessor.lastEvent 持实体作游标——游标行被 7 天保留期清理后 findNext 语义未证（信心=有趣的猜测） | 触发条件见 §五#4；单线程无并发缺陷本体 |

---

## §五 开放裁定（留 7.1）

1. **C2-002 升级条款确认**：维持 P2 的前提是「无生产周期续约调用方」（复核 grep 证据成立）——7.1 复核该前提；上游引入 watchdog 续约即升 P1。
2. **选主 fencing 跨单元判级对齐**：本单元 C2-004/C2-007 判 P2/P3，WI 4.2 MA-01 选主纪律族判 P1（异站点异实现：cluster 设施 vs sys DAO 实现）——条件更新纪律统一批次与判级口径对齐留 7.1。
3. **I-08（C1-102）方案裁决**：fail-fast（ERR_TENANT_REQUIRED）vs 文档声明共享缓存语义——取决于部署形态确认（useTenant=true 且存在无租户头管理调用的组合是否真实存在）。
4. **W-02（C6-005）watch 触发条件**：核实内核 findNext 对已删游标行语义——依赖行存在性则升 P2 立项，比较式 seek（(eventTime,id) 边界）则关闭 watch 并补行为钉死测试。
5. **MA-04 留存语义**：NopSysChangeLog 审计留痕「有意长期保留」的可能性未排除——retention 默认值与审计合规要求对照后定清理策略。
6. **I-03（C1-008）修复路径选型**：moduleName 唯一索引（orm 模型 **plan-first**）vs generateByAI 单飞/分布式锁——7.1 选型后实施。

---

## §六 权威汇总表（工单台账）

| 工单 | 判级 | 成员（主/吸收） | 一句话 | 批次约束 |
|---|---|---|---|---|
| MA-01 | **P1** | C2-001 主 + C2-002/C2-004/C2-007/C2-102 | DB 行协调原语无条件写/无 fencing/状态不同步——互斥无声破坏 + 双节点双执行（P1 站点×2） | 三子批；跨单元纪律统一（§七#1）；C10×4 随批 |
| I-01 | **P1** | C1-001 | 已发布 mergedStore 并发结构修改（TreeMap） | COW 发布；不触反向钉死 |
| I-02 | **P1** | C1-002 | 订阅 cancel×subscribe 孤儿化 → 消息静默丢弃 | compute 单步归一 |
| MA-02 | P2 | C2-003 主 + C2-006/C2-101 | 动态模型生成锁全局串行：命中读 + DB I/O + 用户钩子同一临界区 | **同 diff 改写 TestInMemoryCodeCacheContract 断言** |
| MA-03 | P2 | C6-002 主 + C6-102/C1-101 | 租户缓存 evict/init/destroy 三转变点无对称治理（默认配置即可触发） | 三子修复面单批；§一.3#1 冲突裁定 |
| MA-04 | P2 | C6-001 主 + C6-003 | sys_lock/sys_change_log 零保留（F4）+ 逐列写放大 | 照抄 cleanupExpiredEvents 形态；留存语义留 7.1 |
| MA-05 | P2 | C2-005 主 + C1-007 | 序列默认回退路径：共享锁 item + 锁内 DB 往返 + defaultCache 漏清 | generateLong/generateString 同批 |
| MA-06 | P2 | C1-003 主 + C1-005（C1-004 watch 搭车） | 懒初始化守卫缺失 + 双 volatile 快照发布 | DCL/volatile + Snapshot record；C10-005 随批 |
| MA-07 | P3 | C8-101 主 + C8-002/C8-102/C8-004 | 轮询管线无界工作：无预算/无隔离/无退避/清理无 LIMIT | 预算+短路+分批；executor 隔离可选 |
| I-03 | P2 | C1-008 | generateByAI moduleName TOCTOU | **唯一索引方案 plan-first**；选型留 7.1 |
| I-04 | P2 | C6-101 | 动态模型删除零失效面（已删模型继续服务） | afterEntityChange 照抄函数级先例 |
| I-05 | P2 | C8-001 | 消费等待无超时（F5 静默挂死） | 与 MA-01 批次协同；双向关联 |
| I-06 | P3 | C1-006 | scope 跨代残留 | 局部 scope/入口 clear |
| I-07 | P3 | C1-009 | setVar get-then-save | 重复键归一 |
| I-08 | P3 | C1-102 | 无租户上下文旁路缓存 | fail-fast vs 声明，留 7.1 |
| I-09 | P3 | C6-004 | doStop 不等在途任务 | awaitDone 栅栏；naming 同族 |
| I-10 | P3 | C8-003 | 批量发送半个成功未声明（F2） | javadoc 契约/逐条结果 |
| W-01 | watch | C1-004 | 双 volatile 瞬态错配 | 随 MA-06 消化 |
| W-02 | watch | C6-005 | 游标行清理后 findNext 语义 | §五#4 触发条件 |

**判级合计（工单口径）：P0×0（显式空执行）/ P1×3 / P2×8 / P3×6 = 17 张工单 + watch×2。**

### 结构性发现

1. **fencing/条件写纪律缺失集中于 nop-sys-dao 协调原语**：锁行/租约/选主/事件认领五站点同一修法（条件 UPDATE + 状态回写），且 C2-001 为教科书式 F3——同文件两处同族已修且注释言明根因，第三站点漏网；修复方向全部收敛为条件写，无一处需要 JVM 锁。
2. **「会红=0」campaign 第 6 例同型确认**：C10 矩阵 16 行会红=0（P1 4/4 无测试；P2 无测试 9 + 假保护 2）；唯一反向钉死恰是「结构断言钉死实现修饰符」形态——把实现钉死在正确的一半，读写分离改造反被其打红。
3. **动态模型服务路径「生成锁=全局限速器」**：读路径与生成共用单一全局互斥，临界区含 DB I/O 与用户 XPL 钩子——管理端一次编辑即触发平台级动态模型排队。
4. **租户缓存生命周期三缝隙**：evict/init/destroy 三转变点均无对称治理，默认容器上限 100 即可触发周期性整租户重初始化风暴。

---

## §七 跨单元对账（cra-index P1 台账 159 条查重）

**同站点重复立项：0**（cra-index 报告清单 22 单元无 nop-sys/nop-dyn 条目；dispatch-log 历史审计对照「sys/dyn 并发主题直接审计 0 命中」；159 条 P1 逐条比对唯一缺陷站点零命中）。家族/同型关联 11 处单列（零重复，修复批协同）：

| # | 本单元 | 关联单元/条目 | 关系 |
|---|---|---|---|
| 1 | MA-01（fencing 纪律族） | WI 1.6 fencing 族（C1-001/C3-002/C3-003/C3-012）+ WI 4.2 MA-01 选主并发纪律族 + WI 2.5 MA-05（零单飞/fencing）+ WI 1.5 C7-001（WfScanner fencing） | 条件更新/fencing 纪律跨单元统一批次；异单元异站点（C2-004 现状标注自证） |
| 2 | I-02（订阅孤儿化） | WI 4.1 MA-05/C1-006（幽灵订阅——LocalMessageService 站点）+ WI 2.5 C6-004（PubSub cancel/suspend/resume） | 订阅生命周期竞态族：彼为嵌入/通道侧、本站为 DB 通道侧 |
| 3 | MA-04（零保留族 F4） | WI 5.1 MA-01（六族清理配对）+ WI 1.6 C7-001（三账本零保留）+ WI 1.7 C7-003 | F4 无界账本族第 N 例——保留期/清理设施统一批次 |
| 4 | I-05（无界等待 F5） | WI 3.1 C7-001（SSE 挂死对健康检测不可见）+ WI 1.6 C8-001（扫描停摆零观测） | 静默挂死/停摆族；in-unit 与 MA-01（C2-102）双向放大关联 |
| 5 | I-07（get-then-save 输家） | WI 5.1 MA-05（check-then-insert 输家归一族） | 同型异单元——重复键归一先例移植 |
| 6 | I-09（stop 不等待） | WI 2.5 MA-04（链停止协议：stopAll 仅 cancel 不等待） | 停止协议族——awaitDone 统一形态 |
| 7 | I-10（批量半个成功 F2） | WI 1.6 C6-003（delete+insert 半个成功）+ WI 2.4 C2-013（事务注册半个成功）+ WI 5.1 I-02（刷新半个成功） | 半个成功族——声明/归一口径对齐 |
| 8 | MA-07（轮询无界工作） | WI 1.5 C5-001（到期扫描无 LIMIT 全量载入） | 周期扫描无界族——预算/分批统一形态 |
| 9 | I-03（TOCTOU 双建） | WI 2.5 MA-05（编排入口零单飞面） | 单飞/fencing 治理协同（非同站点） |
| 10 | MA-06（懒初始化守卫） | WI 5.1 I-04（两个单例 5 个 check-then-act 惰性初始化字段） | 懒初始化守卫族——DCL/volatile 同法 |
| 11 | MA-02（锁内重 I/O） | WI 2.1 C2-001（ResourceCacheEntry 持监视器加载模型） | 锁内重操作同型异层：内核设施 vs 模块使用点，修复互不依赖 |

---

## §八 overlay 线索处置（CL）

本单元 overlay 五形态（C3/C4/C5/C7/C9）+ 预注 CL 候选逐条处置；**CL 编号实占 0、驳回 0**（线索未产生独立编号项，全部并入维度正文立案或零命中关闭）：

| 线索 | 处置 | 去向 |
|---|---|---|
| 预注 CL 候选：周期任务族（SysDaoMessageService 3×scheduleWithFixedDelay + SysDaoNamingService globalTimer，与 WI 1.6/4.5 周期停摆族同族） | **确认落位拆解**：Error 逃逸绝缘已自备（三个周期任务 try/catch 全包，C6-004 证据 :294-303/:365-373——WI 1.6 C8-001/4.5 C4-001/2.1 C3-001 的停摆形态在本单元不可达）；停摆残余面（无界等待挂死）由 **I-05** 承载、停止面 **I-09**、预算/隔离面 **MA-07** | 不另立 CL 工单 |
| C4 周期任务锚点（2 文件 9 命中） | 全部落入 C6-004/C8-101/C8-001 立案面 | I-09/MA-07/I-05 |
| C5 无界输入 | 零命中确认（8 根无 readAllBytes/无 LIMIT findAll；SysI18nMessageLoader 启动期一次全量豁免） | 关闭 |
| C7 超时面 | 唯一无界等待点已立案（I-05）；锁 backoff :186-193 有界 ✅ | I-05 |
| C3 异步原语 | orTimeout/ThreadLocal/BlockingQueue/new Thread 全零命中（R1 基线 + C1 复核双确认）；CompletionStage 面由 I-05/I-10 承载 | 关闭/并入 |
| C9 | 无命中 | 零立案 |

---

## §九 C10 缺保护登记映射（随本体）

C10 缺保护 **15 条单列（P2，不入工单计数）**，全部映射随缺陷本体工单落地；另有 C8-004 假保护随案 1（P3，不在 15 内）：

| 本体工单 | C10 缺保护登记 | 建议测试形态要点 |
|---|---|---|
| I-01 | C1-001 | 双线程：T1 循环读 store × T2 genBizObjFiles merge——COW 后断言读侧永不撕裂 |
| I-02 | C1-002 | 双线程闩锁：T1 cancel 进入 remove 后暂停 × T2 subscribe——断言 topic 键存活且 state2 可收消息 |
| I-03 | C1-008（假保护转正） | 并发双 generateByAI——断言仅一成功（唯一索引或锁先行） |
| I-04 | C6-101（假保护镜像） | 删除已发布模块——断言 codeCache/VFS/bizObjectManager 三面均不可达 |
| I-05 | C8-001 | 消费者返回未完成 future + 等待超时——断言轮询任务按超时路径退出 |
| MA-01 | C2-001、C2-102、C2-002、C2-004 | 双竞争者接管 holderId 断言 / 慢消费+第二认领者 fencing 拒绝 / 连续两次续约均成功 / 双 follower 至多一个 onBecomeLeader |
| MA-02 | C2-003 | 命中路径无锁化后断言并发 getResource 吞吐 > 1（或结构断言去 synchronized） |
| MA-03 | C6-002 | 容器上限 2 + 三租户——驱逐触发 on-unload/清空断言（removalListener 修复后） |
| MA-04 | C6-001、C6-003 | 过期锁行零争用周期清理被删（照抄 TestSysEventCleanup）/ retention 配置驱动清理断言 |
| MA-05 | C2-005 | 并发同序列取号无重复值断言（现有 testSequence 仅单线程） |
| MA-06 | C1-003 | 照抄 TestDynCodeGenTenantCacheInit 双线程闩锁迁移到 ensure*Processor |
| MA-07 | C8-004（假保护随案，P3） | 分批删除断言（有界性修复不使现有测试变红——随批补钉） |

- 矩阵统计：16 行会红 = **0**（P1 级 4/4 无测试；P2 级 11 条 = 无测试 9 + 假保护 2；C8-004 假保护随案）——campaign 第 6 例。
- **反向钉死 1**：TestInMemoryCodeCacheContract（:177-194）——MA-02 修复同 diff 改写；I-01 COW 不触红（§一.3#6）。
- 时序敏感登记 2（TestDaoLeaderElector sleep(1000) 改状态轮询；TestSysCodeRuleGeneratorCache sleep(2002) 注入可控 clock）；资源钩子缺口 1 族（C6-001/002/003 生命周期对账测试形态，TestSysEventCleanup 可作模板）。

---

---

## 附录 收口与回填备忘（非裁定节，供主 agent 收口）

- **Phase 8 批量修复入口**：P1×3（MA-01 三子批 / I-01 COW 发布 / I-02 compute 归一）优先；P2×8 随后（MA-02 携反向钉死改写、MA-03 三子面、I-03 留 7.1 选型、I-04 照抄先例、I-05 与 MA-01 协同）；P3×6 逐项 7.1 裁决（in-scope / deferred + Why Not Blocking）。
- **Protected Area**：本单元 0 张工单触碰 nop-sys-api/nop-dyn-api 叶；I-03 若走唯一索引方案触碰 orm 模型 = **plan-first**。审计全程只读。
- **cra-index 回填（主 agent）**：报告清单登记 WI 5.2 行（P0×0/P1×3/P2×8/P3×6 + C10 缺保护 15 单列，计数口径见 §一）；P1 台账追加 `nop-sys-dyn-MA-01`（P1 站点×2）、`nop-sys-dyn-I-01`、`nop-sys-dyn-I-02` 三行。
- **owner doc**：nop-dyn.md 并发关键词 0 命中 drift 登记（dispatch-log）；修复落地时按 AGENTS.md 同步 owner doc。
