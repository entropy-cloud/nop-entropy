# C10 测试保护力 — nop-batch 并发·异步·资源专项审计（本单元收尾批次）

- **审计单元**: nop-batch 各子模块 `src/test`（java + resources）对 C1/C5/C6/C7/C8 五维最终保留项的回归保护力评估
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **输入口径**: 任务书为 52 条保留发现（P1×8）。实测五文件「最终保留项」节合计 **53 行**：C1×7、C5×9、C6×11、C8×10 与任务书一致；**C7 最终保留项表实为 16 行**（C7 复核统计表自记「保留 15、驳回 0」与其 16 行终裁清单自相矛盾，16 个 ID 全部在列且均未被驳回）——本矩阵按 **53 条（P1×8、P2×23、P3×22）** 全量覆盖，口径差报请 7.1 合并裁定更正。现状全部「新发现·未修复」。
- **审计日期**: 2026-10-16
- **纪律**: 只读。未修改任何测试、产品代码与五维文件。全部判定基于逐个打开测试文件核对断言内容（非凭测试名推断）。

## 方法说明

三档判定针对**每条缺陷的缺陷面**（缺陷所否定的正确行为）：

| 档 | 定义 |
|---|---|
| `有钉` | 存在测试钉住该行为正确面，缺陷（或其修复的回退）回潮会红——给出测试类#方法名 |
| `部分` | 覆盖相邻面（同机制兄弟面/同类正确性面）但缺陷面未被钉住；修复回退不会红 |
| `无保护` | 缺陷面及其相邻面均无测试触及 |

**结构性前提**：52/53 条现状均为「新发现·未修复」且现有套件绿——逻辑上不可能存在严格意义的 `有钉`（若正确面已被钉住，缺陷在 live code 上套件即应红）。因此本矩阵 `有钉=0` 是审计结论而非方法缺位；判定重心在 `部分` 与 `无保护` 的切分，以及「修复落地后需要补什么钉」。任务要求的「有钉抽查 3 条反向验证」改按**3 条最强相邻钉反向验证**执行（见下），确认其缺陷面确实未被钉住。

## 一、测试资产盘点

### 1.1 模块 × 被测面归类

| 被测面 | 模块 | 测试类（个） | @Test（个） | 测试资源 | 覆盖形态 |
|---|---|---|---|---|---|
| core 引擎（BatchTask/BatchTaskBuilder/consumer/loader 装饰器/dispatch/状态回调） | nop-batch-core | 20（含 2 个 Debug fixture、0 @Test） | 61 | 无 | 最厚：chunk 失败终态化（defect4/5/6）、checkpoint 语义 8 例、dispatch 正值路径、rateLimit、retry/skip 装饰器、file loader 压实/回调泄漏、primaryError、range 工具 14 例 |
| dsl 装配（xdef 模型→provider 装配、runner、tag/limitHash 校验） | nop-batch-dsl | 11（含 2 个 0 @Test 生成助手） | 21 | 9（batch/task xml、xlsx） | DSL 字段装配反射断言（jdbc reader/writer 全字段）、runner 分区接线、forTag 校验、E2E 3 条（test-batch.task.xml 含嵌套 batch:Execute、test-import-excel、processor-order） |
| orm（consumer/snapshot） | nop-batch-orm | 2 | 9 | 无 | **仅 consumer 写路径 + snapshot 恢复；loader 面零测试** |
| jdbc loader-consumer | nop-batch-jdbc | 3 | 3 | 无 | 窄：共享 bean setup 元数据、close 后 load 空返回、分页 offset 重试重读；**均不触达真实 SQL 执行/资源关闭** |
| exp 工具（Export/ImportDbTool、状态存储、配置） | nop-batch-exp | 6 | 19 | 6 | 导出 xlsx 正确性 roundtrip、导入 happy path（注入 DataSource）、ETL 状态文件原子写+并发读写压力、字段 processor 求值 6 例、配置脱敏/克隆 |
| sys E2E（sys-event 触发链） | nop-batch-sys | 2 | 5 | 1 | 分区过滤/重排语义、ConsumeLater 重排；**不校验 stateStore/闸门联动** |
| dao state store/history | nop-batch-dao | 4（含 1 个 Race fixture） | 11 | 2 | 启动闸门语义 5 例 + 并发首发/并发重启确定性竞态 2 例（G7-04-01）、history 行写入/强制列/真实 flush |
| gen（BatchGenModel） | nop-batch-gen | 2 | 2 | 1 | 单线程生成正确性、mergeMap 分支 |
| biz（导入导出 builder） | nop-batch-biz | 3 | 3 | 无 | 格式错误、locator、importer 未实现占位 |
| web / codegen | nop-batch-web、codegen | 3 | 1 | 无 | 页面冒烟 1 例 + codegen main 助手 |
| **合计** | 11 模块 | **56** | **135** | **19** | 7 个文件为 fixture/main 助手（0 @Test） |

### 1.2 值得肯定的套件纪律

- 并发测试普遍 `@Timeout`（TestBatchBugVerification 30s、TestBatchTaskCheckpoint 10s）+ CountDownLatch 确定性编排（不靠 sleep 碰运气），是补缺保护时可直接复用的先例模式。
- 历史修复均有回归钉（bug1~3/defect4~6/9/11a/12b、primaryError、history 事务提升、AsyncFetch 假 EOF、ETL 状态并发损坏），覆盖了本单元多条发现的**相邻面**。

### 1.3 明显空洞（资产级）

1. **故障注入面系统性缺失**：全套件 0 处 `Error` 注入、0 个拒绝型 executor、0 个会抛错的 stateStore、0 个负值/零值参数用例（grep 亲证）——恰是 P1×8 中 6 条的缺陷面。
2. **orm 模块 loader 零测试**（仅 consumer/snapshot）→ C7-007/C7-014 无任何约束。
3. **jdbc 模块无执行级测试**：3 个测试全部不触达 SQL 执行、fetchSize 应用、PreparedStatement 关闭、连接持有时点。
4. **generator/异步装配零覆盖**：`useBatchRequestGenerator`/`SequentialBatchRequestGenerator`/`asyncProcessor`/`asyncExec` 组合在 src/test（java+xml）零出现。
5. **可观测性零断言**：无任何测试断言 metrics 数据源/接线或日志量。
6. **exp 工具只测正确性**：不断言 chunk 计数、线程终止、destroy 语义。
7. **biz 侧护栏零测试**：batchSize 钳位/穿透无用例。

## 二、保护力矩阵

### 2.1 P1 专项详表（8 条逐条，不归组）

| ID | 缺陷面 | 判定 | 相邻钉（实测核对） | 缺陷面触及 | 缺口要点 |
|---|---|---|---|---|---|
| nop-batch-C5-001 | excel-reader 全量物化违背内存包络 | **部分** | `TestBatchTaskDsl#testImportExcel`（E2E 正确性）、`TestExportDbTool#testExportXlsx`（ExcelResourceIO roundtrip） | **缺陷路径被执行**（同一读取路径）但零内存/流式断言 | 无 maxCount/流式行为断言；testExportXlsx 甚至以 `readAll()` 全量读回作为验证手段，与缺陷同构 |
| nop-batch-C5-002 | jdbc fetchSize 死配置（&&门 :246-247 永不生效） | **部分** | `TestJdbcBatchSupport#testNewJdbcReaderMapsAllModelFields`（反射断言 fetchSize=500 装配到位）、`#testNewJdbcReaderKeepsDefaultsWhenModelEmpty` | **未触及**（无任何测试执行 jdbc loader 的 SQL 路径） | 只钉「模型→provider 字段装配」，运行时生效面零测试；缺省测试注释还将死配置契约文档化（见 N2） |
| nop-batch-C6-001 | chunk 循环仅 catch Exception，Error 使 future 永不完成 | **部分** | `TestBatchBugVerification#defect5`、`TestBatchTaskCheckpoint#testTaskFailureSavesErrorCheckpoint`、`impl/TestBatchPrimaryError`（Exception 级「future 必结算+终态必保存」契约已钉） | **未触及**（全仓 0 处 Error 注入，catch 站点 :136/:200/:243/:328 全为 Exception） | Error 注入 → future 有界结算 + 终态保存 + claim 释放，全缺 |
| nop-batch-C6-002 | executor.execute 同步 REE 绕过 onTaskComplete | **部分** | 同上（chunk 级终态化契约） | **未触及**（0 个拒绝型 executor；全部用 cachedThreadPool/Runnable::run） | 提交点 S2 失败 → setup 已开资源泄漏 + 任务行永久 RUNNING，无任何测试 |
| nop-batch-C7-001 | 终态保存失败/崩溃 → RUNNING 死区（无 stale 判定）永久拒启 | **部分** | `TestDaoBatchStateStore` 7 例：RUNNING/KILLED/COMPLETED 拒绝、并发首发单胜者、并发重启、recover mode（闸门语义钉得很厚） | **未触及**（store 从不抛错；无 stale workerId/心跳伪造场景；无「保存失败后重启」序列） | 闸门正确面有钉，死区面（保存失败吞没 + stale 永拒 + 无恢复）零钉；与 N1 联动 |
| nop-batch-C7-003 | 三表零保留无清理机制 | **部分** | `TestDaoBatchRecordHistoryStore(RealFlush)`（历史行写入/强制列）、`TestRecordLoader`（completedIndex 推进）——增长面被钉为正确行为 | **未触及**（清理代码不存在，自然无测试） | 保留/清理策略落地时需全新回归（保留期、行数守恒） |
| nop-batch-C8-001 | loadBatchSize≤0 五层无护栏（orm 挂死/jdbc-file 静默丢全量/cancel 无效） | **部分** | `TestBatchTaskDispatchLoadRetry`（loadBatchSize=5 正值）、`TestPartitionDispatchLoader`×2、`TestPartitionDispatchQueueFinish#testAddBatchReturnsAfterFinish`（finish 释放钉住）、`TestAsyncFetchLoaderFailure`（fetch 失败传播钉住） | **未触及**（0/负值容量零用例；cancel-不释放入口面零测试） | 护栏三层防线（队列构造/构建期/xdef）任一落地均需新钉；orm 修复后期望终态= fail-fast 而非空任务（C8 复核修正口径） |
| nop-batch-C8-002 | ExportDbTool 缺省 batchSize=0 每行一 chunk 写放大 | **部分** | `TestExportDbTool#testExportXlsx`（config 未设 batchSize → **缺陷缺省路径被执行**，仅断言 xlsx 输出正确） | 缺陷路径被执行、零放大断言 | chunk 计数/INFO 量/状态文件写次数/与 Import 守卫对称性，全零断言 |

**P1 小结**：8 条全部 `部分`，**缺陷面零钉**；其中 C5-001/C8-002 的缺陷路径被顺带执行（零断言），其余 6 条（C5-002、C6-001、C6-002、C7-001、C7-003、C8-001）缺陷面完全未触及。

### 2.2 其余 45 条归组矩阵

| 组 | 成员（ID / 级） | 缺陷面 | 判定 | 相邻钉（测试类#方法，实测） | 缺口一句话 |
|---|---|---|---|---|---|
| G1 嵌套 TL | C1-001 P2 | TL provide/remove 无 save-restore | 部分 | `TestBatchTaskDsl#testBatchTask`（test-batch.task.xml 无 asyncExec → 嵌套同步链路**被执行**，仅断言输出） | BatchTaskGlobals 测试零引用，TL 存续零断言 |
| G2 generator 游标 | C1-002 P2 | genCount/subIndex 并发无锁 | 部分 | `TestBatchGenModel#testGen`（单线程顺序/计数钉住） | 并发面零用例 |
| G3 回调 swap-on-fire | C1-003 P2 | fire 后注册被静默丢弃 | 部分 | `TestBatchBugVerification#bug2`（fire 前注册映射钉住） | fire 后注册语义零用例 |
| G4 async processor 竞态 | C1-006 P2 | subIndex/subContext 跨线程错位 | **无保护** | 无（grep `useBatchRequestGenerator` src/test 零命中） | 组合装配 + 并发推进零覆盖 |
| G5 死载体/重入 | C1-004 P3；C1-005 P3 | onTaskEnds 死字段；重入无守卫 | **无保护** | 无（`onTaskEnds` 测试零引用；所有用例单次 executeAsync） | — |
| G6 complete 两段窗口 | C1-007 P3 | 两段窗口注册丢弃 + 失败路径跳过 beforeCompletes | 部分 | `TestResourceRecordLoaderCallbackLeak#testBeforeCompleteRegisteredOnceAcrossLoads`（反射钉注册去重；成功路径 onAfterComplete 多例） | 失败路径 beforeCompletes 语义零钉 |
| G7 generator 输出上界 | C5-003 P2 | while(true) 环绕 batchSize 包络 | **无保护** | 无 | — |
| G8 loader List 常驻 | C5-004 P2 | provider 返回 List 全量常驻无上界 | 部分 | `TestListBatchLoader`（size≤batchSize 分块契约）、`TestBatchLoaderHelper#testMultipleProvidersDoNotInterfere`（分页正确性） | 内存常驻/上界守卫零断言 |
| G9 dispatch 容量守卫（并入 C8-001） | C5-005 P2 | 同 C8-001 | 部分 | 同 C8-001 | 同 C8-001 |
| G10 excel maxCount | C5-006 P3 | maxCountExpr 能力缺位 | **无保护** | 无（`maxCount` 测试零命中） | — |
| G11 SplitBatchConsumer | C5-007 P3 | activeConsumers 只增不减 | **无保护** | 无（类名测试零命中） | — |
| G12 上下文容器容量 | C5-008 P3 | 三类容器无容量守卫 | 部分 | 监听器注册 API 被 TestBatchTaskCheckpoint/TestBatchTask 等大量使用 | 容量面零断言（理论风险） |
| G13 batchSize 上界 | C5-009 P3 | 全链路无上界强制 | 部分 | `TestListBatchLoader`（loader 服从 batchSize 面） | xdef/Builder 上界缺失面零钉 |
| G14 try 守卫起点 | C6-010 P2 | propagateContext/provideTaskContext 在 try 外 | **无保护** | 无（该前置段失败场景零注入） | 并入终态化族同 diff 验证 |
| G15 终态写失败吞没 | C6-009 P2 | saveTaskState 终态写失败静默 | **无保护** | 无（全部测试 store 从不抛错——RecordingStateStore/capturingStore 均无 throw 分支，实测核对） | — |
| G16 exp 工具生命周期 | C6-007 P2；C6-004 P2；C6-003 P2 理论 | Hikari try 外；无 awaitTermination；destroy 窗口缺口 | 部分（C6-007/004）；无保护（C6-003） | `TestImportDbTool#testImport`（注入 DS 绕过自建池）、`TestExportDbTool#testExportXlsx`（threadCount=4 正确性） | 失败注入/线程终止断言全缺 |
| G17 saveState×AsyncFetch | C6-008 P2 | 压实回调挂 fetch 一次性 ctx | 部分 | `TestAsyncFetchLoaderFailure`×2（fetch 失败传播）、`TestBatchBugVerification#defect6`+`TestRecordLoader`（file loader 压实/completedIndex） | 组合面零钉 |
| G18 jdbc 资源面 | C6-005 P3；C6-011 P3 理论 | PS 不关；事务绑定连接 | **无保护** | 无（SQLException 分支/事务场景零测试） | — |
| G19 死代码 | C6-006 P3 | removePartition 零调用方 | **无保护** | 无（测试零引用） | — |
| G20 future 双断裂 | C7-002 P2 | executor 拒绝 + Error 逃逸 | 部分 | 同 C6-001（Exception 级终态化契约） | 同 C6-001 |
| G21 retry 参数 | C7-004 P2；C7-013 P3 | 负值=无限重试；溢出零延迟 | **无保护** | 无（全部用正值 retryNTimes；exponentialDelay 全仓含 nop-commons 零测试，grep 亲证） | — |
| G22 skip 参数 | C7-005 P2 | 缺省 0=无限跳过、跳过不入账 | 部分 | `TestBatchTask#testRetry`（显式 maxSkipCount=10000 + skipItemCount=502 钉住跳过计数面） | 缺省语义与「不入账」面零钉 |
| G23 断点续传契约 | C7-006 P2 | completedIndex 仅 file/excel 生效 | 部分 | `TestRecordLoader`（file 面 completedIndex=10000）、`defect6`、test-batch.task.xml（saveState=true file-reader） | orm/jdbc/gen 空转面零钉 |
| G24 orm keyset | C7-007 P2；C7-014 P3 | 活实体游标漂移；NULL 失败墙 | **无保护** | 无（orm 模块 loader 零测试，见 1.3-2） | — |
| G25 jdbc offset 翻页 | C7-008 P2 | O(N²/2B) + 并发写跳行 | 部分 | `TestJdbcPageBatchLoaderRetry#testCursorNotAdvancedWhenPageQueryFails`（offset 重读语义钉住） | 退化/静默丢失面零钉 |
| G26 流式持连 | C7-009 P2 | 整任务持有单连接 | 部分 | `TestJdbcBatchSupport`（streaming 字段装配反射断言） | 运行时持连/断连行为零钉 |
| G27 日志放大 | C7-010 P3 | 每 chunk 双 INFO/每轮 ERROR | **无保护** | 无（零日志断言） | — |
| G28 sys 链守卫 | C7-011 P2 | saveState 未配置 → 闸门结构性不覆盖 | 部分 | `TestSysEventBatchTrigger`×2、`TestSysEventBatchPartitionE2E`×3（分区/重排语义；被测 batch.xml 实测无 stateStore 配置） | 守卫覆盖面零断言 |
| G29 KILLED 吸收态 | C7-012 P2 | 永久吸收态 + 无 fencing | 部分 | `TestDaoBatchStateStore#testLoadTaskState_killedTask_rejected`（KILLED 拒绝钉住） | 吸收态永久性/解除路径零钉 |
| G30 recordKey 塌缩 | C7-015 P3 | 空值塌缩 + 复合主键冲突 | 部分 | `TestDaoBatchRecordHistoryStore`（key 写入/强制列钉住） | null/重复键行为零钉 |
| G31 谓词不对称 | C7-016 P3 | bizFatal 重试判死后被静默跳过 | 部分 | `TestBatchTask#testRetry`（显式策略 RuntimeException 端到端）、`TestRetryConsumerExceptions`（装饰器级） | 缺省谓词 bizFatal 交集零钉 |
| G32 全局池/声明失真 | C8-003 P2 | cached 池无上界 + xdef 注释失真 | 部分 | cachedThreadPool 被广泛用作测试 executor（间接） | 上界零断言；schema 声明面无断言测试 |
| G33 metrics 零接线/零数据源/无出口/幽灵旋钮 | C8-004 P2；C8-007 P2；C8-008 P2；C8-009 P2 | 零接线；retry 零数据源；水位无出口；幽灵旋钮 | 部分（C8-004）；**无保护**（C8-007/008/009） | `TestBatchTask`（手工构造 BatchTaskMetricsImpl + print，零值断言） | 接线/数据源/出口/旋钮四面零钉 |
| G34 discard 审计 | C8-005 P3 | 丢弃量不可审计 + takeBatch 静默 null 残余 | 部分 | `TestAsyncFetchLoaderFailure`（**已修复子面有真钉**：fetch 失败必须传播不得假 EOF——本矩阵最接近「有钉」的一行） | discard 计数/审计零钉 |
| G35 rateLimit 组合 | C8-006 P3 | batchSize×rateLimit 必死组合 | 部分 | `TestRateLimitConsumer`×3（许可计量 + fail-closed 超时钉住） | 装配期相容性校验零钉 |
| G36 biz/exp 护栏双标 | C8-010 P2 | biz 钳位无下界 + core/exp 无界 | **无保护** | 无（biz 模块仅格式/locator 测试） | — |

### 2.3 反向验证记录（3 条最强相邻钉，任务书「有钉抽查」的等价执行）

1. **C6-001/C7-002 最强相邻钉** `TestBatchBugVerification#defect5` + `TestBatchTaskCheckpoint#testTaskFailureSavesErrorCheckpoint`：逐行核对——注入异常为 `BatchCancelException`/`IllegalStateException`（RuntimeException 族），断言 future 结算与终态保存仅覆盖 Exception 路径；**Error 面确认无钉**。
2. **C7-001 最强相邻钉** `TestDaoBatchStateStore`（全文 317 行核对）：全部场景使用 fresh RUNNING/KILLED/COMPLETED 行与不抛错的 store；无 stale workerId/过期心跳伪造、无 saveTaskState 抛错后重启序列、无恢复端点用例——**死区面确认无钉**。
3. **C8-001 最强相邻钉** `TestPartitionDispatchQueueFinish` + `TestBatchTaskDispatchLoadRetry`：capacity=2、loadBatchSize=5，全为正值；0/负值守卫面确认无钉（`TestJdbcPageBatchLoaderRetry` 属 JdbcPageBatchLoaderProvider，非缺陷类）。

附加反向验证：**C5-002** `TestJdbcBatchSupport` 全部断言为反射字段读取（privateField），无任何 load/SQL 执行——运行时生效面确认无钉。

## 三、缺保护发现清单（统一 P2，进 7.1 裁决通道）

**登记规则**：P1×8（全部 `部分`=缺陷面零钉）+ P2 保护力缺口最重项，按同族归并为 10 条。P3 级缺口不单独立案（随所属族顺带覆盖）。

### nop-batch-C10-001
- **严重度**: P2
- **缺口描述**: 终态化阻断族零故障注入。Exception 级「future 必结算 + 终态必保存」契约已有厚钉，但 Error 逃逸（S1）、提交期同步 REE（S2）、executeChunkLoop try 前段失败（S3）三个缺陷面零测试、零触及；修复（try 上移 + catch Throwable + S2 终态化）落地后无任何回归保护。
- **涉及缺陷 ID**: nop-batch-C6-001、nop-batch-C6-002、nop-batch-C6-010、nop-batch-C7-002；CL-024（claim 释放）作为验证断言一并纳入
- **建议回归测试形态**: BatchTask 级故障注入测试（沿用 TestBatchBugVerification 的 latch 编排 + @Timeout 纪律）：① loader/processor/consumer 分别抛 `Error`；② 注入 execute 即抛 RejectedExecutionException 的 executor；③ 断言 future 在有界时间内结算、onTaskComplete/终态 saveTaskState 恰达一次、PartitionDispatchQueue claim 全部释放（completeBatch）、executor 关闭后无存活非守护线程。
- **判定依据**: 矩阵 C6-001/C6-002/C6-010/C7-002 行；反向验证记录 1

### nop-batch-C10-002
- **严重度**: P2
- **缺口描述**: 状态存储故障注入与闸门覆盖缺口。闸门「正确面」钉得厚（TestDaoBatchStateStore 7 例），但死区面零钉：saveTaskState 抛错/半写被静默吞没（C6-009）、stale RUNNING 永久拒启（C7-001）、sys-event 链 saveState 未配置使闸门结构性旁路（C7-011）、KILLED 吸收态无解除（C7-012）全部无测试。
- **涉及缺陷 ID**: nop-batch-C7-001、nop-batch-C6-009、nop-batch-C7-011、nop-batch-C7-012；CL-012/016（锁内 I/O）、CL-017（幽灵 processor）相邻顺带
- **建议回归测试形态**: ① 抛错 store 注入：终态写失败后任务行状态/日志可观测性断言（与 C10-001 共用终态断言基建）；② stale 矩阵用例：伪造死亡 workerId/过期心跳的 RUNNING 行 vs 存活 RUNNING 行，前者允许恢复、后者仍 EXIST_RUNNING（与既有钉共存，见 N1 参数化义务）；③ sys 链装配断言：non-broadcast-consumer.batch.xml 的 saveState/闸门联动按裁决钉住（配置或声明二选一）；④ cancel → KILLED 后的解除/重启语义按裁决钉住。
- **判定依据**: 矩阵 C7-001 行及反向验证记录 2；C6-009/C7-011/C7-012 行

### nop-batch-C10-003
- **严重度**: P2
- **缺口描述**: dispatch 链容量参数零负值/零值覆盖。正值路径有钉（loadBatchSize=5、capacity=2/5/20），但 loadBatchSize≤0 的五层无护栏缺陷面（orm 挂死 + 内存尖峰、jdbc/file/generator/list 静默空任务、cancel 无效）零用例；与 C5-005 同位点。
- **涉及缺陷 ID**: nop-batch-C8-001、nop-batch-C5-005；C8-009（幽灵旋钮参数化前置）关联
- **建议回归测试形态**: ① 护栏用例：loadBatchSize=0/负、capacity=0/负 在构造期 fail-fast，断言错误码携带 loadBatchSize 与 capacity 公式上下文（C8 复核补条）；② 修复后期望终态区分：orm-reader 路径断言 fail-fast（**非空任务成功**——按 C8-001 复核事实修正口径），jdbc/file 路径断言不丢全量；③ cancel 期间 acquirePermits/takeBatch 可退出断言（补 finish 之外的第二释放入口）。
- **判定依据**: 矩阵 C8-001 行及反向验证记录 3

### nop-batch-C10-004
- **严重度**: P2
- **缺口描述**: exp 工具生命周期与写放大回归缺口。ExportDbTool 缺省 batchSize=0 缺陷路径虽被执行（测试 config 未设 batchSize），但零放大断言；destroy 无 awaitTermination、自建 Hikari 泄漏、destroy 窗口缺口全部无失败注入测试。
- **涉及缺陷 ID**: nop-batch-C8-002、nop-batch-C6-004、nop-batch-C6-007、nop-batch-C6-003
- **建议回归测试形态**: ① 缺省/边界 batchSize 钳位断言（chunk 计数或状态文件写次数上界，可用可观测 consumer 计数）；② Import 守卫与 Export 对称性用例（同参数双工具行为一致）；③ syncConfigWithDb 失败注入 → 无未关闭池（句柄/活跃连接计数断言）；④ execute 返回后线程终止断言（表任务线程存活扫描）。
- **判定依据**: 矩阵 C8-002、C6-007/004/003 行

### nop-batch-C10-005
- **严重度**: P2
- **缺口描述**: jdbc loader 运行时行为零执行级测试。现套件仅覆盖 DSL 字段装配与分页重读，fetchSize 运行时生效（C5-002 &&门）、PreparedStatement 异常分支关闭（C6-005）、事务绑定连接持有（C6-011）、流式整任务持连（C7-009）、offset 翻页退化/跳行（C7-008）全部无测试。
- **涉及缺陷 ID**: nop-batch-C5-002、nop-batch-C6-005、nop-batch-C6-011、nop-batch-C7-009、nop-batch-C7-008
- **建议回归测试形态**: 以记录型 IJdbcTemplate/Statement/Connection 代理（TestJdbcBatchConsumerProviderSharedField 的 Proxy 先例）：① DSL fetchSize=500 → 断言 ps.setFetchSize(500) 实际被调用（修复面直钉）；② load 中 SQLException 注入 → 断言 ps 关闭；③ streaming=true → 断言连接获取/释放时点与任务生命周期关系；④ 并发写场景下 offset 翻页行为按裁决钉住（快照分页或声明限定）。
- **判定依据**: 矩阵 C5-002 行及附加反向验证；C6-005/011、C7-008/009 行

### nop-batch-C10-006
- **严重度**: P2
- **缺口描述**: orm-reader loader 面零测试（模块级空洞）。nop-batch-orm 仅有 consumer/snapshot 测试，keyset 活实体锚点游标漂移（C7-007）、NULL 排序值失败墙（C7-014）、orm 侧 completedIndex 空转（C7-006 的 orm 面）完全无约束。
- **涉及缺陷 ID**: nop-batch-C7-007、nop-batch-C7-014、nop-batch-C7-006（orm 子面）
- **建议回归测试形态**: orm 模块新增 loader 单测（fake IDaoProvider/OrmTemplate 代理）：① orderBy 列被本任务更新后游标不漂移（修复面直钉）或按声明钉住漂移行为；② NULL 排序值行为显式用例（跳过/响亮报错，按裁决）；③ saveState=true 时 completedIndex 推进断言（orm/jdbc/gen 全 loader 参数化，与 C10-007 契约测试衔接）。
- **判定依据**: 矩阵 C7-007/C7-014 行；资产空洞 1.3-2

### nop-batch-C10-007
- **严重度**: P2
- **缺口描述**: 断点续传契约与三表保留策略回归缺口。completedIndex「仅 file/excel 生效」的限定未在任何层面被钉（契约缺口本身无测试表达）；三表零保留（C7-003）的清理机制落地时无现成回归位；recordKey 塌缩（C7-015）与 retry/skip 缺省谓词不对称（C7-016）缺陷面零钉。
- **涉及缺陷 ID**: nop-batch-C7-003、nop-batch-C7-006、nop-batch-C7-015、nop-batch-C7-016
- **建议回归测试形态**: ① 契约参数化测试：枚举全部内建 loader × saveState=true，断言 completedIndex 推进行为与修复后声明一致（file/excel 现有钉保持）；② 保留策略用例：插入过期行 → 清理后行数守恒、活跃行不受影响（与 TestDaoBatchRecordHistoryStore 增长钉互补）；③ recordKeyExpr 返回 null/同键重复的显式用例；④ bizFatal 异常穿越缺省 retry/skip 谓词的端到端用例。
- **判定依据**: 矩阵 C7-003/006 行、G30/G31 组

### nop-batch-C10-008
- **严重度**: P2
- **缺口描述**: generator 装配与 loader 输出上界并发面零覆盖。useBatchRequestGenerator/asyncProcessor 组合（C1-006）、sequential 游标并发驱动（C1-002 的并发半面）、chunk 输出集无上界（C5-003）、provider List 常驻（C5-004）、嵌套执行 TL 存续（C1-001）在 src/test 零触及或仅单线程/顺带执行。
- **涉及缺陷 ID**: nop-batch-C1-001、nop-batch-C1-002、nop-batch-C1-006、nop-batch-C5-003、nop-batch-C5-004
- **建议回归测试形态**: ① 多 chunk 线程并发驱动 sequential generator（latch 编排先例），断言无超发/无 `generator-already-finished`/无同 case 重复消费；② useBatchRequestGenerator+asyncProcessor 装配用例，断言 subIndex/subContext 对位与并发安全（修复面直钉）；③ DSL 装配层输出上界断言（按裁决的包络机制）；④ 嵌套 batch:Execute 后外层 BatchTaskGlobals.useTaskContext() 非空断言（save/restore 修复面直钉）。
- **判定依据**: 矩阵 G1/G2/G4/G7/G8 组

### nop-batch-C10-009
- **严重度**: P2
- **缺口描述**: 生命周期回调注册时序语义未钉。fire 后注册的丢弃/补偿语义（C1-003）、失败路径 beforeCompletes 确定性不执行（C1-007）、saveState×AsyncFetch 压实回调挂死组合（C6-008）零用例；现有钉全部停留在 fire 前注册与成功路径。
- **涉及缺陷 ID**: nop-batch-C1-003、nop-batch-C1-007、nop-batch-C6-008
- **建议回归测试形态**: ① fireTaskBegin 之后注册监听器的行为用例（按裁决钉住：报错/补偿/声明保留现状）；② completeExceptionally 路径 beforeCompletes 执行语义用例；③ ResourceRecordLoaderProvider 压实回调经 AsyncFetchPartitionDispatchLoaderProvider 包装的集成用例，断言 completedIndex 推进不依赖一次性 ctx。
- **判定依据**: 矩阵 G3/G6/G17 组

### nop-batch-C10-010
- **严重度**: P2
- **缺口描述**: 可观测性与参数护栏回归缺口。metrics 生产接线（C8-004）、retry 数据源与 loadItemCount 错源（C8-007）、水位出口（C8-008）、biz 钳位下界穿透（C8-010）、maxRetryCount 负值（C7-004）、skipPolicy 缺省（C7-005）、exponentialDelay 溢出边界（C7-013）、batchSize 上界（C5-009）、全局池上界与 xdef 声明面（C8-003）零测试或仅相邻面。
- **涉及缺陷 ID**: nop-batch-C8-004、C8-007、C8-008、C8-010、C8-003；nop-batch-C7-004、C7-005、C7-013；nop-batch-C5-009
- **建议回归测试形态**: ① beans 装配冒烟测试：断言 BatchTaskMetricsImpl 被生产接线且 retry timer 有数据源、loadItemCount 取自 loadCounter；② 参数边界参数化：maxRetryCount=-1、retryDelay=1000×2^rt（rt∈{61,62,63}）、maxSkipCount 缺省、batchSize=0/-1/超大 经 xdef/Builder/biz 三层入口的归一与钳位断言；③ schema 断言测试：batch.xdef 声明文本与实现行为一致性（防声明再失真）。
- **判定依据**: 矩阵 G21/G22/G33/G35/G36 组

## 四、测试套件自身缺陷注记（N）

### N1 — 启动闸门钉用例与 C7-001 修复方案的耦合（TestDaoBatchStateStore）
`testLoadTaskState_runningInstance_rejectedLoudly`、`testConcurrentStart_freshKey_onlyOneInstanceEntersRunning`、`testConcurrentRestartFromCompleted_onlyOneInstanceGainsRunning` 以「存在 RUNNING 行即无条件拒绝第二个实例」为期望。若 C7-001 修复采用 workerId/心跳 stale 判定且允许接管非存活 worker 的 RUNNING 行，上述用例将打红，须同步参数化（显式构造 live/stale 两态前置行）。expireTime 类方案则保持绿。属条件性钉住缺陷行为，修复 diff 必须携带用例改写评估。

### N2 — 缺省 fetchSize 测试注释把死配置契约文档化（TestJdbcBatchSupport）
`testNewJdbcReaderKeepsDefaultsWhenModelEmpty` 的断言注释「unset fetchSize stays null so loader uses SQL-level default」正是 C5-002 死配置契约的装配层表达。修复若仅动执行门（JdbcBatchLoaderProvider:246-247），该用例保持绿；若修复改变 wiring 契约（provider 层兜底缺省值），断言须同步改写。登记为修复 diff 的同步评估项。

### N3 — 无超时 await 使 dispatch 正值钉在挂死回归下表现为悬挂而非红（TestPartitionDispatchLoader）
`testAsyncFetchLoader`/`testLoad` 的 `latch.await()` 无超时（违背仓库 testing.md 异步防挂起纪律，同套件 TestBatchBugVerification 已自我声明该纪律）。若 C8-001 族修复回潮造成挂死，这两个用例将**悬挂构建**而非失败断言——保护力弱化为「构建卡死」信号。建议补超时；`TestAsyncFetchLoaderFailure` 的 `ThreadHelper.sleep(300)` 时序假设亦有偶发脆弱性（弱相关，顺带登记）。

除 N1~N3 外，未发现无条件钉住缺陷行为（正确修复必然打红）的用例——现有套件与全部 53 条缺陷共存绿，与此前提一致。

## 五、统计

### 5.1 总体（53 条）

| 档 | 条数 | 占比 |
|---|---|---|
| 有钉 | 0 | 0% |
| 部分 | 32 | 60.4% |
| 无保护 | 21 | 39.6% |

无保护 21 条清单：C1-004/005/006、C5-003/006/007、C6-003/005/006/009/010/011、C7-004/007/010/013/014、C8-007/008/009/010。

### 5.2 P1 专项（8 条）

| 项 | 值 |
|---|---|
| 有钉 / 部分 / 无保护 | 0 / 8 / 0 |
| 缺陷面零钉 | 8/8（100%） |
| 缺陷路径被执行但零断言 | 2（C5-001、C8-002） |
| 缺陷面完全未触及 | 6（C5-002、C6-001、C6-002、C7-001、C7-003、C8-001） |

### 5.3 维度切面

| 维度 | 有钉 | 部分 | 无保护 | 小计 |
|---|---|---|---|---|
| C1 共享可变状态 | 0 | 4 | 3 | 7 |
| C5 内存上界 | 0 | 6 | 3 | 9 |
| C6 资源生命周期 | 0 | 5 | 6 | 11 |
| C7 长时运行 | 0 | 11 | 5 | 16 |
| C8 背压流控 | 0 | 6 | 4 | 10 |

### 5.4 缺保护发现与套件注记

- 缺保护发现：**10 条**（nop-batch-C10-001 ~ C10-010，统一 P2，进 7.1 裁决通道），全覆盖 P1×8 与全部 P2 缺口最重项；P3 缺口随所属族顺带覆盖，不单独立案。
- 测试套件自身缺陷注记：**3 条**（N1 条件性钉住缺陷行为×2、N3 套件纪律缺陷×1）。

## 六、CL 处置结果注记（单列，不进矩阵主体）

CL 台账合计 33 条线索（CL-001~039，缺号属各维驳回/并入）：全部不进保护力矩阵主体。与矩阵有交接的三处已在 C10 发现中顺带：CL-024（claim 泄漏）→ 作为 C6-001 修复族验证断言并入 **C10-001**；CL-012/016（锁内 DB I/O）、CL-017（幽灵 processor）→ 相邻顺带于 **C10-002**；CL-010（jdbc setup 持连）→ 相邻顺带于 **C10-005**。其余 CL 线索的保护力评估随其归属维度缺陷的修复批次进行，本轮不单独立案。

## C10 复核结论（独立复核子代理，2026-10-16）

- **复核人立场**：与 C10 初审相互独立的复核轮，以怀疑姿态抽验。纪律：只读（本节为唯一新增）；全部判定基于逐个打开被引测试文件核对断言内容 + 对测试树/全仓的结构性 grep 亲证，非凭测试名推断。复核对象：保护力矩阵 53 条 + 缺保护登记 C10-001~010 + N1~N3 注记。
- **总裁定**：**矩阵主体结论全部维持**（有钉 0 / 部分 32 / 无保护 21，P1 专项 0/8/0）；抽验 38 行零翻案，4 处证据级表述修正（不改判定）；N1~N3 全部属实维持；C10-001~010 全部维持登记；**新补 2 条登记（C10-011、C10-012），缺保护发现 10→12 条**；口径修正 2 处（P2/P3 切分、CL 台账计数）。

### 一、抽验明细（38 行，超出任务书最低 10 行要求）

**P1 专项 8 条全部抽验（判定全部维持「部分·缺陷面零钉」）**：

| ID | 亲验证据 | 裁定 |
|---|---|---|
| C5-001 | `TestBatchTaskDsl#testImportExcel` 执行 `test-import-excel.batch.xml`（batch:ImportFromExcelLoader，同一读取路径）→ `task.execute()` 后零断言；`TestExportDbTool#testExportXlsx` 以 `input.readAll()` 全量读回验证（与缺陷同构）属实 | 部分·维持 |
| C5-002 | `TestJdbcBatchSupport` 7 例全部为反射字段读取（`privateField`）/getter 断言，无任何 load/SQL 执行；`testNewJdbcReaderMapsAllModelFields` :112 钉 fetchSize=500 装配 | 部分·维持 |
| C6-001 | `TestBatchBugVerification#defect5`（注入 `BatchCancelException`）、`TestBatchTaskCheckpoint#testTaskFailureSavesErrorCheckpoint`（注入 `IllegalStateException`）、`impl/TestBatchPrimaryError`（`RuntimeException`/`BatchCancelException` 直调）——全部 Exception 级；`BatchTask.java` catch 站点 ：136/:200/:243/:328 亲证全为 `catch (Exception e)`；全测试树 0 处 `Error` 注入（grep 亲证） | 部分·维持 |
| C6-002 | 全测试树 0 处 `RejectedExecutionException`、0 个拒绝型 executor（grep 亲证）；`executor.execute` 调用点在 try 外（BatchTask impl :227）亲证 | 部分·维持 |
| C7-001 | `TestDaoBatchStateStore` 全文 317 行复核：7 例全部 fresh 行 + 不抛错 store；无 stale workerId/心跳伪造、无保存失败后重启序列、无恢复端点用例；`recover mode` 断言存在于 completedTask_rejectedUnlessAllowed | 部分·维持 |
| C7-003 | `TestDaoBatchRecordHistoryStore`（3 例）+ `RealFlush`（1 例）钉历史行写入/强制列/真实 flush；`TestRecordLoader` :39 钉 completedIndex 推进为正确行为——增长面有钉、清理面无代码故无测试 | 部分·维持 |
| C8-001 | `TestBatchTaskDispatchLoadRetry`（loadBatchSize=5 正值）、`TestPartitionDispatchQueueFinish#testAddBatchReturnsAfterFinish`（capacity=2 正值，仅 finish 释放入口）、`TestAsyncFetchLoaderFailure`×2（fetch 失败传播，正值参数）——0/负值面零用例亲证；`TestJdbcPageBatchLoaderRetry` 确属 JdbcPageBatchLoaderProvider 非缺陷类 | 部分·维持 |
| C8-002 | `test.export-db-xlsx.xml` 实测**无 batchSize 属性**——缺陷缺省路径被执行亲证；仅断言 xlsx 行内容，零放大断言 | 部分·维持 |

**「部分」行抽验 17 组（判定全部维持）**：G1（test-batch.task.xml 无 asyncExec、saveState=true file-reader 亲证）、G2（`TestBatchGenModel#testGen` 单线程）、G3（`bug2` fire 前注册映射）、G6（`TestResourceRecordLoaderCallbackLeak#testBeforeCompleteRegisteredOnceAcrossLoads` 反射钉 beforeCompletes 注册去重）、G8（`TestListBatchLoader` 4 例 size≤batchSize 契约 + `TestBatchLoaderHelper#testMultipleProvidersDoNotInterfere` 存在）、G16（`TestImportDbTool#testImport` 注入 DataSource 绕过自建池亲证）、G17（AsyncFetchLoaderFailure + defect6 + RecordLoader）、G22（`TestBatchTask#testRetry` 显式 `maxSkipCount(10000)` + skipItemCount=502 断言亲证）、G23（RecordLoader completedIndex=10000）、G25（`TestJdbcPageBatchLoaderRetry#testCursorNotAdvancedWhenPageQueryFails` offset 重读语义亲证）、G28（`TestSysEventBatchTrigger`×2 + `TestSysEventBatchPartitionE2E`×3，被测 `/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml` 实测无 stateStore 配置）、G29（`testLoadTaskState_killedTask_rejected` 存在）、G30（HistoryStore 键写入/强制列）、G31（`testRetry` RuntimeException 端到端 + `TestRetryConsumerExceptions` 装饰器 3 例，bizFatal 面零命中）、G33（`TestBatchTask` :33 手工构造 BatchTaskMetricsImpl + :55 print）、G34（`TestAsyncFetchLoaderFailure` 对「fetch 失败必须传播不得假 EOF」构成真钉——已修复子面定性准确）、G35（`TestRateLimitConsumer` 3 例：许可计量/单模式不放大/超时 fail-closed）。

**「无保护」行抽验 13 组（判定全部维持，grep 亲证）**：G4（`useBatchRequestGenerator` src/test 零命中）、G5（`onTaskEnds` 零命中）、G7（generator 测试零出现）、G10（`maxCountExpr` 零命中，见修正 3）、G11（`SplitBatchConsumer` 零命中）、G14（try 前段失败零注入，propagateContext :229/provideTaskContext :231 在 try（:232）外亲证）、G15（RecordingStateStore/capturingStore/defect5 匿名 store 逐一核对均无 throw 分支）、G18（jdbc 3 测试无 SQLException 注入/事务场景；PageBatchLoaderRetry 抛 IllegalStateException 且非缺陷类）、G19（`removePartition` 测试零引用）、G21（`exponentialDelay` 全仓 grep 仅 main 代码含 nop-commons `RetryPolicy`，零测试；maxRetryCount 负值零用例）、G24（orm 模块 2 类 9 @Test 全部 consumer/snapshot，loader 零测试）、G27（日志断言零命中）。

**反向验证记录 1~3 + 附加（C5-002）逐条复算**：全部成立。

### 二、修正行清单（4 处，均为证据级表述，判定不变）

1. **G16 行（C6-007/004）**：「`TestExportDbTool#testExportXlsx`（threadCount=4 正确性）」证据错误——testExportXlsx 实际加载 `test.export-db-xlsx.xml`，该文件**无 threadCount 属性**；`threadCount="4"` 在 `test.export-db.xml`，而后者**未被任何测试引用**（grep 亲证，孤儿测试资源）。实际保护力弱于记载：多线程导出路径从未被测试执行。判定「部分」维持（正确性 roundtrip 有钉、生命周期/失败注入零断言不变）。
2. **G1 行（C1-001）**：「仅断言输出」不准——`TestBatchTaskDsl#testBatchTask` 实为**零断言**（仅 `System.out.println(outputs)`）。判定「部分」维持（嵌套同步链路被执行、BatchTaskGlobals 测试零引用均亲证）。
3. **G10 行（C5-006）**：「`maxCount` 测试零命中」不准——`maxCount` 在 `DebugResourceRecordIO` fixture 有命中（构造参数）；缺陷面 `maxCountExpr` 确为零命中。判定「无保护」维持。
4. **G33 行（C8-004）**：「零值断言」表述歧义——实为「**无任何 metrics 值断言**」（仅手工构造 + print，consumer/skip 计数断言与 metrics 无关）。判定「部分」维持。

另核实 1.2 节「defect12b」回归钉真实存在（`TestLimitHash#defect12b_limitHashMustBeNonNegative`），该节其余点名（bug1~3/defect4~6/9/11a、primaryError、history 事务提升、AsyncFetch 假 EOF、ETL 状态并发损坏）逐一对应真实测试，**无修正**。

### 三、C10-001~010 缺保护登记裁定表（10 条全部维持）

| 登记 | ID 集与矩阵自洽 | 测试形态可执行（设施存在性亲证） | 裁定 |
|---|---|---|---|
| C10-001 | ✓（C6-001/002/010/C7-002 + CL-024） | ✓ TestBatchBugVerification latch+@Timeout(30) 先例、completeBatch API（PartitionDispatchQueue :262）真实 | 维持 |
| C10-002 | ✓（C7-001/C6-009/C7-011/C7-012 + CL-012/016/017） | ✓ TestDaoBatchStateStore 真实；non-broadcast-consumer.batch.xml 真实存在于 nop-batch-sys/src/main/resources | 维持 |
| C10-003 | ✓（C8-001/C5-005 + C8-009 前置；orm fail-fast 口径与 C8 复核一致） | ✓ 正值参数用例先例齐全 | 维持 |
| C10-004 | ✓（C8-002/C6-004/C6-007/C6-003） | ✓ syncConfigWithDb 在 ExportDbTool :119/ImportDbTool :121 均存在 | 维持 |
| C10-005 | ✓（C5-002/C6-005/C6-011/C7-009/C7-008） | ✓ 记录型 Proxy 先例真实（TestJdbcBatchConsumerProviderSharedField、TestJdbcPageBatchLoaderRetry） | 维持 |
| C10-006 | ✓（C7-007/C7-014/C7-006 orm 子面） | ✓ orm loader 零测试亲证；orm 测试已用 Proxy 先例 | 维持 |
| C10-007 | ✓（C7-003/C7-006/C7-015/C7-016） | ✓ TestDaoBatchRecordHistoryStore(RealFlush) 真实 | 维持 |
| C10-008 | ✓（C1-001/002/006/C5-003/004） | ✓ latch 编排先例、零 generator 覆盖亲证 | 维持 |
| C10-009 | ✓（C1-003/C1-007/C6-008） | ✓ CallbackLeak 现钉止于 fire 前注册亲证；AsyncFetch 包装组合两 provider 类真实 | 维持 |
| C10-010 | ✓（C8-004/007/008/010/003、C7-004/005/013、C5-009） | ✓ batch.xdef 真实（nop-xdefs）；TestBatchTask 手工 metrics 亲证 | 维持 |

### 四、补充发现（新登记 2 条，编号续 C10-011 起）

### nop-batch-C10-011
- **严重度**: P2
- **缺口性质**: **登记完整性缺口——P1 漏登记**。本文件第三节自设规则为「P1×8（全部 部分=缺陷面零钉）+ P2 缺口最重项归并为 10 条」，但 **nop-batch-C5-001（P1，excel-reader 全量物化）未出现在任何 C10-00x 的「涉及缺陷 ID」中**（全文 grep 亲证：C5-001 仅见于矩阵行/P1 小结/统计）。C10-004 覆盖了同形态的 C8-002（「缺陷路径被执行但零断言」），却漏掉 C5-001——8 条 P1 中唯一无修复回归钉登记的一条。
- **涉及缺陷 ID**: nop-batch-C5-001；顺带覆盖 nop-batch-C5-006（maxCountExpr 能力缺位，若裁决补能力则同 diff）
- **建议回归测试形态**: excel-reader 内存包络回归：① 修复后按包络机制断言读取行数上界/分批推进（可观测 consumer 计数 + DebugResourceRecordIO readCount 上界断言），**验证手段不得复用 `readAll()` 全量读回**（现 testImportExcel/testExportXlsx 与缺陷同构）；② 大行数（>batchSize×N）下断言读取路径峰值行数有界；③ 若裁决补 maxCountExpr，则增 xdef 特性 → 装配 → 截断行为的端到端用例。
- **判定依据**: 矩阵 C5-001 行亲验（本复核第一节）；第三节登记规则原文

### nop-batch-C10-012
- **严重度**: P3（对应缺陷均为 P3；破例单列理由见下）
- **缺口性质**: 第三节「P3 级缺口随所属族顺带覆盖」对 8 条 P3 **不成立**（C1-004、C1-005、C5-006、C5-007、C6-006、C7-010、C8-005、C8-006 未附着于任何 C10-00x，逐条核对亲证）。其中最有材料的两组缺陷面**零相邻钉**：C8-005 的 discard 计数/审计面与 takeBatch 静默 null 残余（TestAsyncFetchLoaderFailure 只钉 fetch 传播子面）、C8-006 的 batchSize×rateLimit 装配期相容性面（TestRateLimitConsumer 只钉许可计量与 fail-closed，不相容组合零用例）。
- **涉及缺陷 ID**: nop-batch-C8-005、nop-batch-C8-006（核心）；C1-004/005、C5-006/007、C6-006、C7-010 维持不立案（理论风险/观测面，随各自归属缺陷修复批次顺带）
- **建议回归测试形态**: ① TestRateLimitConsumer 增装配相容性用例（batchSize>rateLimit×1200 组合在构建期按裁决 fail-fast 或告警断言；rateLimit≤0 行为显式用例）；② discard 路径用可观测 consumer 断言丢弃量入账 + takeBatch null 残余不产生幽灵计数。
- **判定依据**: 矩阵 G34/G35 行亲验；第三节「随所属族顺带覆盖」表述与实际登记的偏差

### 五、N1~N3 注记复核（3 条全部属实，维持）

| 注记 | 亲验证据 | 裁定 |
|---|---|---|
| N1 | 三个点名用例（runningInstance_rejectedLoudly / concurrentStart_freshKey / concurrentRestartFromCompleted）全部存在且语义相符；RUNNING 行均由同进程 store 创建、无 workerId 存活性判别——stale 接管型修复必然打红前两例的定性准确 | 维持 |
| N2 | `TestJdbcBatchSupport` :180 注释原文逐字一致（"unset fetchSize stays null so loader uses SQL-level default"）；修复面（JdbcBatchLoaderProvider :246-247 && 门，行号亲证精确）与 wiring 契约的耦合定性准确 | 维持 |
| N3 | `TestPartitionDispatchLoader#testAsyncFetchLoader` :67 / `#testLoad` :114 两处 `latch.await()` 无超时、类与方法均无 @Timeout（同套件 TestBatchBugVerification 类注释 :35 确自我声明带超时纪律——对比成立）；`TestAsyncFetchLoaderFailure` :78/:138 `ThreadHelper.sleep(300)` 亲证 | 维持 |

末段「除 N1~N3 外无无条件钉住缺陷行为的用例」与全部 38 行抽验一致，维持。

### 六、口径一致性核对（与五个维度文件 + dispatch-log）

| 口径项 | C10 原文 | 复核结果 | 正确口径 |
|---|---|---|---|
| 总数 53（C1×7、C5×9、C6×11、C7×16、C8×10） | 一致 | ✓ 逐行清点五文件最终保留项 + dispatch-log :47 均为 53 | **维持** |
| C7=16 行 vs C7 复核统计表「保留 15、驳回 0」 | C10 读法：16 行全部保留、统计表自相矛盾 | ✓ C7 复核统计表原文「保留 15（其中 1 条降级）」确漏计降级项，16 行全部在列且无驳回 | **C10 读法正确，维持** |
| P1×8 | 一致 | ✓ C5-001/002、C6-001/002、C7-001/003、C8-001/002 与五文件 P1 集合完全一致 | **维持** |
| **P2/P3 切分** | 头部「P2×23、P3×22」 | ✗ **错误**。五文件逐行清点：P2 = C1×4+C5×3+C6×6+C7×9+C8×6 = **28**；P3 = C1×3+C5×4+C6×3+C7×5+C8×2 = **17**（8+28+17=53）。疑为沿任务书切分 +1（C7-016 P3）未重推导 | **更正为 P1×8、P2×28、P3×17**（报请 7.1 一并裁定） |
| 矩阵切面（部分 32 / 无保护 21） | 5.1/5.3 | ✓ 与矩阵逐行复核一致；无保护 21 条清单逐 ID 核对无误 | **维持** |
| **CL 台账计数** | 第六节「合计 33 条（CL-001~039，缺号属驳回/并入）」 | ✗ **错误**。CL-001~039 共 39 个编号**无缺号**（dispatch-log :48「维度集外线索累计 39 条」）；驳回/不立案 4 条（CL-007/009/015/038）后确认台账 **35 条**（其中 CL-010 跨 C5/C6 单条、CL-012=CL-016、CL-013=CL-017 合并单列、CL-008→C8-002、CL-026→C6-001、CL-027→CL-006 并入）。「33」无法由任何口径推出 | **更正为「编号 39 条、确认台账 35 条」** |
| CL 交接三处（CL-024→C10-001、CL-012/016/017→C10-002、CL-010→C10-005） | 一致 | ✓ 与各维度文件处置注记相符 | **维持** |
| 现状「全部新发现·未修复」前提 | 方法说明 | ✓ 与 C7 复核基线声明（b3a9c76bed 起 nop-batch 零变更）一致；有钉=0 逻辑成立 | **维持** |

### 七、最终统计（复核后）

| 项 | 初审值 | 复核后 | 说明 |
|---|---|---|---|
| 矩阵覆盖 | 53 条（P1×8、P2×23、P3×22） | **53 条（P1×8、P2×28、P3×17）** | 条数与切面维持；严重度切分更正 |
| 有钉 / 部分 / 无保护 | 0 / 32 / 21 | **0 / 32 / 21（维持）** | 38 行抽验零翻案 |
| P1 专项 | 0/8/0，缺陷面零钉 8/8 | **维持** | 8 条全部亲验 |
| 缺保护发现 | 10 条（C10-001~010） | **12 条（C10-001~012）** | 补 C10-011（P1 漏登记）、C10-012（P3 无族覆盖残余簇） |
| 套件注记 | N1~N3 | **N1~N3 维持** | 3 条全部亲证属实 |
| 证据级修正 | — | **4 处**（G16 threadCount、G1 零断言、G10 maxCount→maxCountExpr、G33 零 metrics 断言） | 判定均不变 |

**复核终止判定**：C10 维度审计的矩阵主体、N 注记与 10 条原登记全部成立；修正限于 2 处口径数字（P2/P3 切分、CL 台账计数）、4 处证据表述与 2 条补充登记。建议 7.1 合并裁定采纳：① P2×28/P3×17 口径；② CL 台账 35 条口径；③ 缺保护发现按 12 条受理（C10-011 为 P1 修复批次的必补回归位）。
