# R4-32 R28 墙钟敏感跨单元批（单调时钟注入）设计

> Status: draft（待 PA 评审 + plan-first 授权复核）
> Batch: R4-32（roadmap 8.72；batch-proposal §二 R4-32 行）
> Members: 1.5-C7-006；1.6-C7-011；2.1-C7-004（含 C7-CL-034 随批 + cra-index :381 三点追加面）；2.3-C8-005；2.5-C7-013（与 2.3-C8-005 同批设计）；3.1-C7-004；3.3-C7-002
> Non-members: 3.3-C7-003（P3 条化回升面，同批第 8 面——激活时并入，现不入批）；3.1-C7-012/C7-018（nop-ai 独立工单）；2.3-C8-008（DefaultFlowControlRunner，A9 裁定独立设施）；FeishuConnector 入站滚动窗（C7-003 面，见 §四-G）

---

## 一、设施裁定：零新增设施，复用既有 IClock / CoreMetrics.nanoTime

### 1.1 Live 取证结论

平台**已有完整的单调时钟注入设施**，本批不需要新建 IClock/ClockProvider：

| 设施 | 在位锚点 | 能力 |
|---|---|---|
| `IClock` | `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/IClock.java` | `default long nanoTime()` 已内建单调语义（javadoc 明确「与 System.nanoTime 语义一致，不得与 currentTimeMillis 时钟调整联动回退，仅用于相对计时」） |
| `CoreMetrics.nanoTime()` | 同目录 `CoreMetrics.java` :72 | 全局静态换源入口（`s_clock.nanoTime()`） |
| `CoreMetrics.registerClock(IClock)` | `CoreMetrics.java` :43 | 全局注入缝；`NopJunitExtension` :77 在测试后恢复 `defaultClock()`，`NopTestConfigProcessor` :32 注册 `TestClock`——恢复纪律已有先例 |
| `TestClock` | `nop-autotest/nop-autotest-core/src/main/java/io/nop/autotest/core/util/TestClock.java` | 锚定仿真墙钟（锚点+单调水位），**未覆写 `nanoTime()`**——wall 域钉可用，nanoTime 域钉需站点级 stub clock |
| 局部构造/覆写缝先例 | `ProviderFailoverQueue`（`nowMs` LongSupplier 构造参数，默认 `CoreMetrics::currentTimeMillis`）；`RetryScannerImpl.currentTimeMillis()` protected seam（2543 W-12） | 站点级确定性 stub 注入通道 |

**决策**：设施落点 = 维持 `nop-api-core` 既有 `IClock`/`CoreMetrics`，本批零 API 新增、零新类（nop-kernel/nop-api-core 无新公共面）。被拒绝的替代方案：新建独立 `ClockProvider`/`MonotonicClock` 设施——重复 `IClock.nanoTime()` 既有能力，制造第二时钟入口，违背「一处设计」收敛目标。

**PA 声明**：虽然零新增公共 API，站点换源与 `IResourceLock` 契约修订（§四-C）均落在 `nop-kernel`（nop-core/nop-commons）框架核心 plan-first 面内。沿 plan 2547 先例声明授权链：授权来源 = roadmap 8.72 行（concurrency-resource-audit-remediation-roadmap.md :243）+ batch-proposal.md §二 R4-32 行；owner doc = `docs-for-ai/04-reference/source-anchors.md`（IClock/ClockProvider 锚点，本批实施时回填 `IClock.nanoTime` 单调语义锚点）+ 各单元 owner doc；回归测试证据 = 七模块载体测试全绿 + 变异红证（§四）。

### 1.2 时钟口径判定准则（全批 SSOT）

| 语义 | 口径 | 换源形态 |
|---|---|---|
| 相对计时（deadline 预算、租约/冷却/回填时长、verdict TTL、maxAge、时长指标） | 单调 | `CoreMetrics.nanoTime()`（或站点局部缝返回单调值） |
| 与外部时钟可比的绝对时间（写 DB 的 dueTime/leaseUntil/nextTriggerTime、跨节点共享时间戳、mtime 比较、日志/指标展示时间戳） | 墙钟 | 保留 `currentTimeMillis`，统一经可测缝；跨节点面改为**服务器侧时钟**（§四-E） |
| DB 钟语义（首提/续租与 reclaim 判据同源） | DB 估计钟 | `getDbEstimatedClock()` 读侧 min / 写侧 max 双缝（plan 2543 裁定 B 全批沿用） |

mtime 比较链（`DefaultResourceChangeChecker` 的 lastModified 比较、`ResourceDependencySet`）属绝对域，**零改写**（plan 2500 E1 教训：写入年龄判据已定稿，本批只换 `now` 的来源，不动判据结构）。

### 1.3 测试缝纪律（吸收 plan 2543 红⑧教训）

1. **钉不覆写 seam 的 stub**：凡存在「取哪个时钟」的裁定面（如 retry/tcc store 双缝），单元钉**不覆写缝本身**——stub 缝下游依赖（`IOrmEntityDao.getDbEstimatedClock()` 返回已知 min/max），断言基类选中的是哪一侧；变异红证 = 把缝实现回退本地墙钟 → RED（红⑧ 形态，2543 `TestCraR410TccClockPins` 先例）。
2. **站点级 stub clock 优先**：相对计时域的推进/回拨注入经局部构造缝或 protected seam 覆写（确定性强、零全局污染）。
3. **全局 `registerClock` 仅兜底**：无局部缝的静态调用点允许 `CoreMetrics.registerClock(stubClock)`，必须 try/finally 恢复 `CoreMetrics.defaultClock()`（先例 `NopJunitExtension` :77）；stub clock 需双针可控（wall 与 nanoTime 独立推进/回拨），`TestClock` 不满足 nanoTime 针 → 各模块测试自建 package-private `StubClock implements IClock`（非公共 API，不入 nop-api-core）。
4. **真实 sleep 钉退役**：2.1 既有 `TestLocalLockManager#testTryResetLeaseOnExpired/#testIsHoldingLockOnExpired` 真实 sleep 钉（C10-test-protection :370 判定「时钟源不敏感」）随本批改 stub 推进，保护力不弱化（断言同一过期/续租语义，且回拨腿新增）。

---

## 二、cra-index :381 三条边界判定

**判定：三点随批并入 2.1-C7-004 成员面（追加面），成立；[R1-B1 改判] 第(1)条 DefaultResourceChangeChecker:56 `now` 与 mtime epoch 同表达式耦合（writeAge=now-current），换 nanoTime 恒巨大负值=宽限双条件永久退化，静默杀死 plan 2500 写入年龄锚=功能性回归——**改判为绝对域豁免注记**（保留 currentTimeMillis，以「豁免+理由」落账（含 :81 javadoc「墙钟敏感点归R4-32换源面覆盖」注记行同步改豁免措辞——R2-m3）），变异③删除重定向。三点落账口径对齐 :381 原文[R1-M3]：(1)=isChangeRecord now→豁免、(2)=verdictCheckTime 记忆化 TTL 换源、(3)=maxAge 强制重载判定换源；isRefreshEnabled/lastLoadTime「零改写」按 R2-B1 勘正注记口径落账（判据结构零改写、时钟源同源切换成对）。**

依据链：
1. cra-index R4-32 注（plan 2500 Phase 3，裁定⑥(a) 义务）明文「R4-32（R28 墙钟批，roadmap 8.72）换源面必须覆盖」——三条为 R1-05（2026-10-25）在 nop-core 失效链新增的消费点，晚于 kernel 单元审计立案时点，kernel 台账 C7-004 行自然未列。
2. 三点全部位于 2.1 域（`nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/DefaultResourceChangeChecker.java` :56 与 `.../cache/ResourceCacheEntry.java`），与 2.1-C7-004 同模块同域，符合「换源面必须覆盖」的归属指向。
3. 防漏：实施时在 kernel 单元台账 C7-004 行与 batch-proposal R4-32 行各加一行扩面注记（三点 + 锚点），两处一致落账。
4. 防扩边界：并入范围**仅限 cra-index :381 注记三条**——(1) `DefaultResourceChangeChecker.isChangeRecord` 的 `now`（:56 纯比较路径同源；:81 javadoc 已预注「墙钟敏感点归 R4-32 换源面覆盖」）；(2) `ResourceCacheEntry` 门内 verdict 记忆化过期判定（:145 `isRefreshEnabled` 节流 now 同文件同源一并换源）；(3) `ResourceCacheEntry.getObject` maxAge 判定（:247 now）与 lastLoadTime 写入（:514）**成对**换 nanoTime（读写同源原子切换）。既有 R1-05 敏感点中的 mtime 比较与「写入年龄」判据结构零改写；`:372/:392` 超时等待 deadline（DefaultTaskExecutionQueue 族同形）归 C7-round2 既有站点表。

---

## 三、Live 取证总表（七站点）

| # | 成员 | 模块 | Live 锚点（核实 @ 本工作区） | 语义域 |
|---|---|---|---|---|
| 1 | 1.5-C7-006 | nop-wf（nop-wf-scheduler） | `WfTaskScanner.java` :244/:255（扫描 deadline 预算）、:435/:444（二段预算）、:287/:475（leaseUntil 写）、:417（newDueTime 退避写）、:518（newRemindTime 退避写）——全部 `System.currentTimeMillis` | 预算=相对；lease/dueTime 写=绝对（DB 可比） |
| 2 | 1.6-C7-011 | nop-retry（nop-retry-engine） | `RetryRecordStoreImpl.java` :361（renewLease 写侧经 :517 `getCurrentTime()`→:518 DB 估计钟 max）；`RetryEngineImpl.java` 首提路径（:282 注记并发首提 TOCTOU）与扫描 reclaim 判据（:243/:282/:431 DB 估计钟双缝）之间的首提口径混用 | DB 钟域（2543 W-08 双缝设施已在位） |
| 3 | 2.1-C7-004 | nop-kernel（nop-commons + nop-core） | `LocalResourceLockManager.java` :94/:118/:146/:152/:174/:240/:266；`IResourceLock.java` :82（expireAt 写）；`ResourceMultiLock.java` :99/:116；`DefaultTaskExecutionQueue.java` :314/:317；`ResourceCacheEntry.java` :145/:247/:372/:392/:514（verdict TTL+maxAge）；[R1-M1 剔除] `VirtualResourceDependencySet.java` :28 为 mtime 域替身绝对域，从换源清单剔除注记；`DefaultResourceChangeChecker.java` :56（追加面①→R1-B1 绝对域豁免，不改码只落账）；`TimeOut`（nop-api-core，CL-034——[R1-M7] 锚点勘正：无 getEndTimeMillis，实为 getExpireTime()/getRemainingTime()，内部换 nanoTime 绕开 wall 版 timeoutToExpireTime/expireTimeToTimeout；TimeOut 属 nop-api-core 内部行为变更面，PA 声明补入） | 全部相对计时域（mtime 除外） |
| 4 | 2.3-C8-005 | nop-service-framework（nop-gateway） | `AiRateLimitGatewayInterceptor.java` :158（lastRefillTime 构造写）、:171（refill elapsed 判定） | 相对计时域（惰性回填） |
| 5 | 2.5-C7-013 | nop-persistence（nop-nosql-lettuce） | `LettuceRateLimiter.java` :56（`now = currentTimeMillis()/1000` 作 Lua 参数）→ `RedisScripts.RATE_LIMIT` 脚本 Redis 侧比较 | **跨节点绝对域**（禁本地钟） |
| 6 | 3.1-C7-004 | nop-ai（nop-ai-core） | `ThresholdBreaker.java` :229/:325/:473 直连+:526 注释行[R2-m5 订正：注释非消费点，注释文本连带更新]；`ProviderFailoverQueue.java` :45（构造缝默认 `CoreMetrics::currentTimeMillis`） | 相对计时域（熔断/冷却时长） |
| 7 | 3.3-C7-002 | nop-ai-gateway | `FailoverStreamFlow.java` :181（windowStart，行内注释已自标「仅 metrics 时长维度」）、:608/:703（时长消费） | 相对计时域（指标时长） |

---

## 四、逐站点裁定（A–G）

裁定条目统一格式：换源形态 / 载体测试与钉 / 变异红证 / 接缝 / 冻结面。

### A. 2.1-C7-004（nop-kernel，含 CL-034 + :381 三点）——**先于其余站点实施**

- **换源形态**：全批设施锚点站。全部相对计时站点 `CoreMetrics.currentTimeMillis()` → `CoreMetrics.nanoTime()`，按类内**整域原子切换**（读写同源）：LocalResourceLockManager 七站点 + IResourceLock:82 + ResourceMultiLock:99/:116 + DefaultTaskExecutionQueue:314/:317 + ResourceCacheEntry:145/:247/:372/:392 + :514 lastLoadTime 写 + :145 now **成对换源**（[R2-B1 恢复读写同源原子切换]：读点 :146/:252/:262/:273 全部与 lastLoadTime 同表达式相减，仅读侧换=修复终态即混源（nanoTime 原点远小于 epoch→now-lastLoadTime 恒巨大负值→maxAge 永不触发/isRefreshEnabled 永不节流/TRUE 侧限速永假），与变异②判死的形态相同且先红后绿闭环不可能——cra-index :381 原文「isRefreshEnabled/lastLoadTime 写入零改写」系 plan 2500 写定时未虑混源危险的原文技术错误，落账时以勘正注记修订：「零改写=判据结构零改写，时钟源随读侧同源切换」；:145 读取判定侧随之同源）+ [R1-M1 剔除] VirtualResourceDependencySet:28 不入清单 + DefaultResourceChangeChecker:56 R1-B1 豁免不改码。`IResourceLock.getExpireTime()` 契约修订：javadoc 声明返回值语义从 epoch 改为「单调相对值，仅与本进程 nanoTime 比较，不得持久化或跨进程传输」[R1-M2：**事实勘正——调用面不止 :174 内部比较**：LocalResourceLockManager :124/:242/:268 也消费[R2-m1 文件归属勘正：三行属 LocalResourceLockManager 非 IResourceLock]，且 tryLockAndExecute default 方法把 expireAt 经 action.apply(expireAt) 泄漏为对外回调契约——javadoc 修订须覆盖 tryLockAndExecute 参数语义，全部 tryLockAndExecute/tryLockWithLease 调用方为回归面]（nop-commons 框架核心面，PA 声明见 §1.1）。`getLastLoadTime()` public 但全仓零消费者——域翻转仅类内注记[R1-m7]。CL-034 [R1-M7 勘正]：`TimeOut`（nop-api-core）内部期限判定换 nanoTime（绕开 wall 版 CoreMetrics.timeoutToExpireTime/expireTimeToTimeout，锚点=getExpireTime()/getRemainingTime()）；对外 wall 展示面实施时核实消费面再定（getExpireTime() 公共 getter epoch→nano 翻转须补 javadoc 注记——R2-m4；生产零消费者、唯一回归面=TestTimeOut）。TimeOut 属 nop-api-core 内部行为变更面——PA 声明补入（plan 同步）。mtime 比较、C7-007 窗口二判据、写入年龄宽限结构零改写；checkTimeout debug 日志 new Timestamp(lastCleanUpTime) 与 ResourceLockState.toString() :102-103 new Timestamp(lockTime/createTime/expireTime) 日志展示连带处理[R1-m4+R2-m2]。
- **载体测试**：`TestLocalClockSensitivity_C7_004`（nop-commons）：StubClock 双针注入经 `registerClock` + finally 恢复；断言「回拨 1h 后租约仍按真实流逝过期」「前跳不提前过期」「verdict TTL/maxAge 按单调流逝判定」。既有 `TestLocalLockManager` 两真实 sleep 用例改 stub 推进（保护力保持 + 回拨腿新增）。
- **变异红证**：[R2-M1 改写：两条各一钉，变异③已删——该站点 R1-B1 豁免零改码，「now 回退墙钟」为无差异变异]——变异① revert LocalResourceLockManager 为 currentTimeMillis → 回拨注入钉 RED；变异② ResourceCacheEntry 读侧换 nanoTime 而 lastLoadTime 保留墙钟写（混源）→ maxAge 钉 RED（混源即错，读值巨大负差）。红证写行为断言（伪重载 vs 永不重载按实际符号定——R1-m3）。
- **接缝**：`CoreMetrics.nanoTime()` 即全批接缝；本站点产出 `docs-for-ai/04-reference/source-anchors.md` 锚点回填（IClock.nanoTime 单调契约 + 七站点清单）供其余六站点对齐。
- **冻结面**：mtime 语义、C7-007 判据结构、`ResourceTimestampCache` 移除现状、R1-05 全部既有钉零改写；`IClock` 接口零变更。

### B. 1.5-C7-006

[R2-m6] 预算换算 TimeUnit.MILLISECONDS.toNanos（预算四站点 nowNanos 侧）。（nop-wf）

- **换源形态**：`WfTaskScanner` 增 protected `nowMillis()` 缝（默认 `CoreMetrics::currentTimeMillis`，先例 RetryScannerImpl seam）；纯相对预算四站点（:244/:255/:435/:444）缝内换 `CoreMetrics.nanoTime()`；绝对写四站点（leaseUntil :287/:475、newDueTime :417、newRemindTime :518）保留 epoch 值统一走缝。回拨对绝对域的影响（lease/dueTime 与 DB 可比，回拨=租约变长，自愈型有界）以类注记声明，不引入 DB 钟改造（防扩：1.5-C4-007/MA-05 归 R4-09）。
- **载体测试**：stub 子类覆写 `nowMillis()` 推进/回拨；扫描预算钉（前跳不伪超时、回拨不超额等待）。
- **变异红证**：revert :255 为缝外直接 `System.currentTimeMillis` → 前跳伪超时钉 RED（确定性：stub 前跳注入，无 sleep）。
- **接缝**：扫描集/失败韧性面（C4-007/MA-05/MA-06，R4-09）只消费本批新增缝，不改其行为；nop-job-C7-006（形态 A 宿主）跨单元互注不变。
- **冻结面**：`tryClaimScanLease` DB 判据结构、WfScanQueryShape 既有钉零改写。

### C. 1.6-C7-011（nop-retry）

- **换源形态**：无本地钟残留——首提租约写侧归一：`RetryEngineImpl` 首提路径任何 `nextTriggerTime/lease` 写值统一经 `RetryRecordStoreImpl.getCurrentTime()`（:517，DB 估计钟 max 写侧口径）；renewLease :361 已在缝上。全站点核对零 `System.currentTimeMillis` 直连（RetryScannerImpl :199/:207/:328 `lastScanFailureTimeMillis` 为退避记点——核实消费面若仅本地退避比较则随缝单调化，若持久化/与 DB 比较则归写侧 max；实施时按消费面裁定并留注记；[R2-M2 补第三分支] live 核实 RetryScannerImpl :199/:207/:328 为 System.currentTimeMillis 直连（非 :163 缝）、消费面仅 getter :256 诊断展示+两测试 >0 断言——属对外可观测诊断时间戳，**保留墙钟+注记**，不入二分支叉）。
- **载体测试**：**红⑧ 形态钉**（`TestCraR432RetryClockPins`）：stub `IOrmEntityDao.getDbEstimatedClock()` 返回已知 min/max（min≠本地墙钟），断言首提与续租写值取 max 侧；不覆写 store 缝。
- **变异红证**：把 `getCurrentTime()` 回退本地 `CoreMetrics.currentTimeMillis()` → 断言 `expected: <max侧已知值> but was: <本地墙钟>` RED（2543 M-1 同形态）。
- **接缝**：2543 W-08 `nowForRead/nowForWrite` 双缝为既有设施，本批零新增缝；A5 时钟族姊妹面 tcc 全链（1.6-C7-007，已判级归 2543 既有面）不重做。
- **冻结面**：2543 产物（红⑧钉、双缝、RoundEndReason）零改写零删除；1.6-C10-006 覆盖面保持。

### D. 2.3-C8-005（nop-gateway 令牌桶）+ E. 2.5-C7-013（跨节点令牌桶）——同批设计的接缝

- **D 换源形态**：`AiRateLimitGatewayInterceptor.TokenBucket` :158/:171 换 `CoreMetrics.nanoTime()`；`lastRefillTime` 单调化后 elapsed 恒非负（时钟回拨冻结面消除）、前跳集中回填消除（nanoTime 不受墙钟调整影响）；`Math.min(capacity, ...)` 钳制保留。
- **D 载体测试/钉**：StubClock（经 registerClock+finally，或拦截器测试构造点注入）回拨注入钉：修复前 wall 回拨 → elapsed<0 → 桶冻结（红证可构造）；修复后回拨注入 → 正常按真实流逝回填。前跳钉对称。
- **D 变异红证**：revert 两站点为 currentTimeMillis → 回拨钉 RED。
- **E 换源形态**：`LettuceRateLimiter` :56 **禁止换本地 nanoTime**——时间戳在 Redis 侧跨节点比较存储，客户端本地钟即 finding 本体（「客户端墙钟跨节点共享」）。裁定：Lua 脚本改用 Redis 服务器时钟（`redis.call('TIME')`），客户端 `now` 参数移除（口径准则 §1.2 第三行：跨节点共享绝对时间 → 服务器侧时钟）。[R1-M6 三裁决] ① 单位钉死秒：脚本内 TIME 取 sec 针（现行为客户端传秒、存量键 epoch 秒；秒针不劣化，µs 可选改进不做）+ 存量键混存行为注记（delta=max(0,…)+min(capacity,…) 使错域值有界，写入脚本注释与 owner doc）；② 效果复制：Lua 内调 TIME 为非确定性命令——脚本首行显式 `redis.replicate_commands()`（<5.0 必需，≥5.0 默认 effect replication 亦兼容），不用泛化「实施时核实」；③ 测试面：nop-nosql-lettuce 已有 testcontainers Redis 先例（TestLettuceHashTtlAtomic 等），行为钉用 testcontainers 构造真实 Redis 验证 TIME 路径+旧键混存腿。不可用时的回退形态 = 保留客户端传参但改传服务器同步钟——回退形态需在实施 plan 重新过 PA。
- **E 载体测试/钉**：结构钉 = 断言脚本源不含客户端 now 参数消费；行为钉 = embedded/测试双倍 stub 无法模拟 Redis 钟 → 以集成钉或脚本单测（FakeRedis 口径）声明，若载体不可构造则按「结构钉 + 诚实声明」落账（红⑬ 先例）。
- **D+E 接缝**：两设施 A9 裁定独立（不并单、不合并实现），接缝 = 共用 §1.2 口径准则（本地相对 → nanoTime；跨节点 → 服务器钟）+ 同批回拨红证形态；`docs-for-ai/04-reference/source-anchors.md` 与 gateway owner doc 双登记互注。
- **冻结面**：C8-008（DefaultFlowControlRunner）独立工单面零触碰；CL-011 驳回终判维持。

### F. 3.1-C7-004（nop-ai）

[R2-m5] ThresholdBreaker 无构造缝（:229/:325/:473 直连 CoreMetrics）——载体测试走全局 registerClock+finally 兜底（§1.3-3），**非** TestProviderFailoverQueue 构造注入模式（该类具备构造缝；既有护栏兼容经 R2 推演成立）。

- **换源形态**：`ThresholdBreaker` :229/:325/:473/:526 换 `CoreMetrics.nanoTime()`（冷却/开路/探针槽全部相对域）；`ProviderFailoverQueue` 构造缝默认值改 `CoreMetrics::nanoTime`（既有 `nowMs` LongSupplier 缝形态不变——merge-adjudication :194 指定的 TestProviderFailoverQueue 注入护栏模式即本缝）。
- **载体测试**：既有 TestProviderFailoverQueue 模式注入 stub 时钟推进：冷却未满拒开、冷却满探针放行；新增回拨腿（wall 回拨不影响冷却判定）。
- **变异红证**：revert 默认缝为 `currentTimeMillis` → 回拨注入钉 RED。
- **接缝**：nop-ai 单元 C7-012/C7-018（DB 锁/租约期限，独立工单）不触碰；同一 2547 先例 nop-kernel 批的模块边界不越。
- **冻结面**：ProviderFailoverQueue 既有审计修复注记（javadoc :21-24）保持，仅默认值行更新并注明 R4-32。

### G. 3.3-C7-002（nop-ai-gateway）

- **换源形态**：`FailoverStreamFlow` :181/:608/:703 换 `CoreMetrics.nanoTime()`（windowStart 与两处时长消费同源原子切换；时长差值计算形态不变）。**边界（防扩）**：`FeishuConnector.tryAcquireInbound` :465 入站滚动窗为 **C7-003 面**（merge-adjudication #19 立案），现不入批——C7-003 激活回升时随第 8 面并入，且届时按 §1.2 准则裁定（滚动窗内本地时间戳列表属相对窗，换 nanoTime 化为单调时戳列表）。
- **载体测试**：StubClock 推断时长钉：回拨注入后 failover 窗口时长指标仍按真实流逝；与 C7-003 的批次对齐注记（「不并单」裁定 4）保持。
- **变异红证**：revert :181 为 currentTimeMillis → 回拨钉 RED。
- **接缝**：R2-06（G58 已含 3.3 其余成员）产出面零回改；C7-003 第 8 面预留注记落 gateway owner doc。
- **冻结面**：流式取消/期限两侧（2505 批）产物零改写。

---

## 五、实施顺序与验证

1. **P0 设施锚点**（2.1 域先行）：§四-A 全部 + source-anchors 锚点回填 + 台账扩面注记（§二-3）。`./mvnw test -pl nop-kernel -am`
2. **P1 站点批**（依赖 P0 锚点，站点间可并行）：B（nop-wf）→ C（nop-retry）→ F（nop-ai）→ G（nop-gateway）→ D（nop-gateway 令牌桶）。各自 `-pl <模块> -am`。
3. **P2 跨节点面**：E（nop-persistence，Redis TIME 脚本兼容性核实先行）。`./mvnw test -pl nop-persistence -am`
4. 收尾：cra-index R4-32 注追加收敛注（含 :381 三点归属落账）、roadmap 8.72 行状态、daily log、doc link checker `node ai-dev/tools/check-doc-links.mjs --strict`。

**红证确定性总则**：全部回拨/前跳注入经 §1.3 缝纪律（局部 stub 优先、全局兜底+finally 恢复），零真实 sleep、零跨测试全局污染；每条变异红证在修复前基线复现 RED、修复后 GREEN，留证形态沿各批先例（红证总表随 plan 落账）。

## 六、勘正记录

1. **cra-index :381 三点不在 kernel 审计台账 C7-004 原行**（审计先于 R1-05）——以 cra-index 注记为追加义务并入本批成员面，实施时台账两处加注（防漏）。非台账漂移，无既有文本错误需改。
2. **batch-proposal R4-32 行成员清单一字不差成立**（七项 + CL-034 随批注记已在）；第 8 面 3.3-C7-003「现不入批」与本设计 §四-G 边界一致。
3. **「IClock/ClockProvider 设施一处设计」的表述勘正**：设施**已存在**（`IClock.nanoTime` + `CoreMetrics.registerClock`，nop-api-core），本批为「既有设施一处确认 + 七站点换源」，无新建设施、无 nop-api-core 新公共 API——PA 面收窄为「框架核心内部行为变更 + IResourceLock 契约 javadoc 修订」，仍按 plan-first 声明（§1.1）。
4. **FeishuConnector:465 归属勘正（防扩）**：该站为 C7-003 入站滚动窗面，非 C7-002——3.3-C7-002 范围锁定 FailoverStreamFlow 三站点。
