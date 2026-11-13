# nop-batch C7 维度审计报告：长时运行稳定性（第 2 轮盲审深挖）

- **审计单元**: nop-batch 聚合器（11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service；216 files / 15,655 行手写 src/main，不含 `_` 前缀生成物与 target/）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **本轮输入**: 第 1 轮报告 `C7-long-running.md`（C7-001~011：P1×2 / P2×8 / P3×1 + 单调增长源清单 12 项 + 挂死点清单 11 项 + CL-016~021）；基线 `_tmp/cra-baseline-nop-batch.txt`；live code。
- **盲审纪律声明**: 本轮为盲审轮次。审计期间未读取 `ai-dev/audits/`（本输出目录中本维度第 1 轮文件 `C7-long-running.md` 除外）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；未读取同目录其他维度（C1/C5/C6/C8）的任何文件。平台侧文件（nop-orm / nop-dao / nop-api-core）仅在本单元代码引用其契约时按需阅读，不重审平台设施本身。
- **本轮目标（逐条执行完毕）**: ① C7-001 失效面扩展（状态闸门死区族全转移守卫穷举 + stale 恢复能力 + 运维 API 判定 + cron 错误风暴量级）；② C7-003 清理面反向核查（全量 DELETE/TTL/purge grep + beans.xml 装配 + 三表清单完整性 + 12 项增长源逐一确认）；③ retry/skip 策略深水区（实现类边界语义：指数溢出 / retryDelay=0 / exceptionFilter 缺省判定 + 计数偏差站点 F3）；④ keyset/offset 翻页退化面扩展（复合列 / nullable 排序列 / JdbcPage 参数包络 / filterProcessed 语义）；⑤ 时钟使用面（全部时钟站点判定 + completedIndex 崩溃窗口量化）。
- **反虚构核对**: 第 1 轮预注全部实测复核成立——nop-batch 本体无 `Timer`/`ScheduledExecutorService` 自建周期任务；`BatchTaskManagerImpl` **不存在** `findNext` 方法（实测其方法面为 newBatchTaskContext/newBatchTask/newBatchTaskFromModel/loadBatchTaskFromPath/loadBatchTaskModel；keyset 翻页确在 `OrmQueryBatchLoaderProvider.load → IEntityDao.findNext`）；lease 语义读点（`leaseExpireTime`/`getEstimatedNow()`）仅在 sys-event DSL 配置文本内（`non-broadcast-consumer.batch.xml:26`），求值实现在 nop-sys（跨单元不立案）。

---

## 一、指令 ① 执行记录：状态闸门死区族失效面扩展（C7-001 深化）

### 1.1 NopBatchTask 状态机全部转移守卫穷举（实测）

状态值（`_NopBatchDaoConstants`）：CREATED=0（主链路无写者，死常量）、RUNNING=10、SUSPENDED=20、COMPLETED=30、FAILED=40、CANCELLED=50、KILLED=60。

| # | 守卫 | 位置 | 语义 | stale 恢复能力 |
|---|---|---|---|---|
| G1 | KILLED 快失败 | `DaoBatchStateStore.java:108-113` | `taskStatus==KILLED` → `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED` | **无任何逃生门**（`allowStartIfComplete` 只豁免 COMPLETED，不豁免 KILLED）→ KILLED 是吸收态（立案 C7-012） |
| G2 | startLimit 闸 | `:115-120` | `startLimit>0 && execCount>=startLimit` → `ERR_BATCH_TASK_EXCEED_START_LIMIT` | 无恢复；DSL 缺省 `startLimit=0`（batch.xdef:12 `!int=0`）= 不限制，仅误配触发，属声明语义 |
| G3 | RUNNING 快失败 | `:122-129` | `taskStatus<=RUNNING(10)` → `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE` | **无 stale 判定**（无 lease 时间戳比较、无 workerId 存活探测、无启动回收）——第 1 轮 C7-001 主体，本轮复核确认无变化 |
| G4 | COMPLETED 闸 | `:131-137` | 非 `allowStartIfComplete` 且 `taskStatus==COMPLETED` → 拒绝 | 有显式逃生门（`allowStartIfComplete`），非死区 |
| G5 | 启动闸门（原子） | `tryMarkTaskRunning :187-195` | `UPDATE ... SET taskStatus=10 WHERE sid=? AND taskStatus>10`，affected=0 → 冲突响亮失败 | 原子性成立（plan 2282 G7-04-01）；但 WHERE 只认 `>RUNNING`，与 G3 共同把 RUNNING 定义为唯一吸收态 |
| G6 | 终态写入（无转移守卫） | `saveTaskState :233-255` | `complete=true` 时直接 `setTaskStatus(...)` + `updateEntityDirectly`，**无 WHERE taskStatus 条件** | 依赖 ORM version CAS 兜底（见 1.3）；无「终态不可逆/非法转移」校验 |
| G7 | 插入竞争闸 | `tryInsertNewTask :80-104` | (taskName,taskKey) 唯一键 UK_NOP_BATCH_TASK_NAME_KEY，冲突回滚转既有行路径 | 正确；UUID-key 任务（无 taskKeyExpr）绕过全部闸门（C7-011 ② 已立案） |

**结论**：RUNNING 与 KILLED 是状态机的两个吸收态，均无自动/半自动解除路径；SUSPENDED(20)/FAILED(40)/CANCELLED(50) 均可经 G5 正常重启（20/40/50 > 10）。

### 1.2 运维恢复入口判定（grep 实测）

- `IBatchTaskManager` / `BatchTaskManagerImpl`：方法面仅任务**构造/加载**（newBatchTaskContext / newBatchTask / newBatchTaskFromModel / loadBatchTaskFromPath / loadBatchTaskModel），**无 kill/restart/reset/resume 任何运维方法**。
- BizModel 层：`NopBatchTaskBizModel` 等 5 个服务层 BizModel 全部为裸 `CrudBizModel`（仅继承通用 CRUD，无自定义 action）；`INopBatchTaskBiz` 无方法。
- **唯一非改库恢复入口 = 通用 CRUD GraphQL mutation**：`NopBatchTask.xmeta`（`nop-batch-meta/.../_NopBatchTask.xmeta:36-37`）中 `taskStatus` 声明 `insertable="true" updatable="true"`，无 readonly/写保护——运维可经 `NopBatchTask__update` 把卡死行改成 FAILED(40) 解除 G3。但该入口无转移校验（可把任意行改成任意状态，包括手工造出 RUNNING 死区或绕过 G1）、无专用审计语义——判定为「事实存在但未声明的恢复面」，连同其与 version CAS 的冲突问题立案 **C7-012**。
- 全模块 grep `nopBatchTaskManager`：beans 装配点 `batch-dsl.beans.xml:4`（`BatchTaskManagerImpl`）+ `BatchTaskRunner` 注入，无其他消费者，无运维 CLI/工具类。

### 1.3 乐观锁面（G6 的兜底机制，实测）

`nop-batch.orm.xml:42,98` 为 `nop_batch_task` 声明 `versionProp="version"`（VERSION BIGINT mandatory）。实体 UPDATE 链：`updateEntityDirectly → orm().updateDirectly → flushUpdate → EntityPersisterImpl.queueUpdate → JdbcEntityPersistDriver.buildUpdateSql → GenSqlHelper.genUpdateSql:342-350`——WHERE 子句含 `AND version=?`，SET 侧 `version=version+1`，0 行命中由 `EntityPersisterImpl.checkUpdateResult:508-522` 抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND`。**含义**：终态写入有 version CAS，但无 fencing token 语义——「恢复者（人工/CRUD）改状态成功后，若旧执行进程仍存活，旧进程后续每次 saveTaskState 均因 version 失配抛 NOT_FOUND → chunk 失败 → 任务 FAILED」，即恢复动作对存活旧进程的驱逐依赖 version 冲突的**随机失败**而非确定性 fencing（并入 C7-012）。

### 1.4 cron 错误风暴量级确认（C7-001 补强）

死区行（RUNNING 残留）每次触发（如 sys-event 链 5s cron）的实际 churn（实测调用链 `BatchTaskRunner.executeAsync:36-50 → BatchTask.executeAsync:117 → stateStore.loadTaskState → runLocal → loadExistingTask`）：

- DB churn：每次触发 1 个 ORM session 开闭 + 1 次连接池 checkout/return + 1 条 SELECT（taskName+taskKey 过滤 + execCount 排序）。
- batch 侧日志：**零**——G3 快失败路径（`:122-129`）无 LOG 调用（仅 G5 冲突路径有 WARN，快失败在其之前返回）；异常对象携带错误码同步抛出 `executeAsync`（future 建立之前）。
- 调用方（nop-job invoker 线程，跨单元）按其错误策略记账，典型为每触发 1 条多行 ERROR 堆栈。
- 量级：5s cron → 17,280 次/天/死任务 = 17,280 次 session+SELECT +（job 侧）≈17,280 条 ERROR 堆栈日志/天/任务。与第 1 轮「≈17,280 条/天」口径一致并补齐 batch 侧零日志、DB churn 两项实测。

## 二、指令 ② 执行记录：清理面反向核查（C7-003 深化）

### 2.1 全量 grep 结果（`deleteBy|removeBy|DELETE FROM|cleanup|purge|expire|archive|ttl|retention|deleteEntity|delEntity|deleteDirectly`，src/main，排除 _gen/target/test）

**Java 侧唯一命中**：`DefaultBizEntityExporter.cleanup:67-77`（导出临时文件：出错立即删 + 成功后 `globalTimer().schedule(10min)` 删，配置项 `nop.biz.report.keep-temp-file-minutes`）。XML/YAML/SQL 资产侧唯一命中：`non-broadcast-consumer.batch.xml:26` 的 `leaseExpireTime` 过滤条件（查询语义，非清理）。

### 2.2 beans.xml 装配面（_vfs 下全部 beans 定义实测）

`app-batch-dao.beans.xml`（nopDaoBatchStateStore / nopDaoBatchHistoryStoreBuilder）、`batch-dsl.beans.xml`（nopBatchTaskManager / nopBatchTaskRunner + 可选 nopJobPartitionResolver）、`app-service.beans.xml`（5 个 BizModel + 5 个 BizProxy）。**无任何清理/TTL/归档 bean、无任何 job yaml 指向清理**（nop-batch-sys 仅 `scheduler.yaml` + `sys-event-batch-consumer.job.yaml`，后者执行的是 sys-event 消费任务，不做删除）。

### 2.3 二态判定与 F3 全枚举

- **DB 表（nop_batch_task / nop_batch_record_result / nop_batch_task_var / nop_batch_file）：清理代码完全不存在**（不是「存在但零装配」——连死代码都没有，Java/SQL/XML 三面 grep 均零命中）。
- **文件面**：导出临时文件是全模块唯一有清理代码且已装配的删除路径（成功 10min 延迟删 / 失败立即删）；进程在延迟窗口内重启 → 孤儿文件无启动清扫（第 1 轮 CL-018，维持）。
- EtlTaskStateStore 状态文件：覆盖写（大小=表数量级），`reset()/resetTableState()` 是内存态重置非文件删除；状态文件数量=CLI 配置的 stateFile 数（使用方控制，有界）。
- C7-003 三表清单完整性：确认无遗漏。12 项单调增长源逐一复核：#1/#2/#3（三表）无界成立；#4/#5（日志）C7-010；#6/#8/#9/#10/#12 有界维持；#7（metrics）未接线维持；#11（导出临时文件）有清理但重启孤儿（CL-018）。**修正一处第 1 轮口径**：`nop_batch_record_result` 在 `nop-batch.orm.xml:186-238` 中 `batchTaskId` 与 `recordKey` 均 `primary="true"`——**复合主键 (BATCH_TASK_ID, RECORD_KEY) 已在模型层存在**。含义：(a) `filterProcessed` 的 IN 查询天然走 PK 前缀索引（batchTaskId 等值 + recordKey IN = 前缀范围扫描），第 1 轮「表膨胀后每 chunk IN 查询退化」应修正为「查询代价 O(batchSize × log 行数) 的索引范围扫描，退化仅来自树深增长，非常数退化」；(b) `saveProcessed` 重复插入同 (taskId,recordKey) 必然撞主键响亮失败（`dao.saveEntity → orm().save → internalSave` 为纯 INSERT，`saveDirectly` 对非 transient 直接抛错），第 1 轮「物理逐次插入」的静默累积表述修正为「重复即失败」；(c) 第 1 轮建议 (3)「评估 upsert」的实际动机从「防重复行」变为「重复记录场景的任务可用性」（见 C7-015）。

## 三、指令 ③ 执行记录：retry/skip 深水区

### 3.1 RetryPolicy 边界语义逐项判定（`nop-commons/.../retry/RetryPolicy.java` 实测）

- **exponentialDelay 溢出**：`getRetryDelay:139-160` 计算 `timeToSleep = tryDelay * (long) Math.pow(2, retryTimes)`。`(long)Math.pow(2,rt)` 在 rt≥63 饱和为 Long.MAX_VALUE；long 乘法溢出后由 `:143` `if (timeToSleep < 0 || timeToSleep > maxRetryDelay) timeToSleep = maxRetryDelay;` 钳制——**负值溢出全部被兜住**，无超时上限突破。但存在一个精确归零点：tryDelay = o·2^k（o 为奇数）时，`tryDelay·2^rt mod 2^64` 在 **rt+k ≥ 64 时恒等于 0**（如缺省 retryDelay=1000=125×2³ → rt≥61；retryDelay=1024 → rt≥54）——退避在重试次数跨过该阈值后**精确变为 0**，与 `retryDelay=0` 的紧循环等效。可达前提：重试检查永不触发（maxRetryCount<0，即 C7-004 的无限重试）或 maxRetryCount≥61 的罕见配置。立案 **C7-013**。
- **retryDelay=0 紧循环**：`Guard.nonNegativeInt` 允许 0；`tryDelay>0` 条件不成立 → timeToSleep=0 → `ThreadHelper.sleep(0)` 立即重试。有限重试次数下仅 CPU 小浪费；与负 maxRetryCount 组合即无节流紧循环（并入 C7-013 的风暴量级）。`RetryPolicy.retryNTimes(n)` 工厂方法即内置 retryDelay=0（本模块内仅测试使用，grep 实测）。
- **exceptionFilter 匹配失败（判定为不可恢复）的缺省行为**：`isRecoverableException:128-137` 无 filter 时——NopException 按 `!isBizFatal()`、其余按 `!(instanceof Error)`；filter 返回 false → `getRetryDelay` 返回 -1 → `RetryConsumeHelper.checkRetry:27-29` / `RetryBatchLoader.retryLoad:60-61` 抛出原始异常 → **重试判定为失败（fail），不是跳过**。但若外层装配了 skipPolicy（`BatchTaskBuilder:433-439`：retryOneByOne=false 时 SkipBatchConsumer 包在 retry 链外层），该异常继续进入 `SkipConsumeHelper.consumeWithSkipPolicy` → `BatchSkipPolicy.shouldSkip` 的 **skip 过滤器缺省谓词仅排除 Error**（`return !(exception instanceof Error)`）——bizFatal 等「重试侧判死」的异常被「跳过侧判活」而静默丢弃。两套缺省谓词不对称，立案 **C7-016**。

### 3.2 maxRetryCount off-by-one 的 F3 扩展：计数语义偏差站点全清单

| 站点 | 路径 | 实测总尝试次数（maxRetryCount=M） | 判定 |
|---|---|---|---|
| `AbstractRetryBatchConsumer.consume:38-58` + `RetryConsumeHelper.retryConsume:44-78` | 消费链（RetryAll/RetryOneByOne） | 初始 1 + checkRetry(e,0)（只 sleep 计数不消费）+ 循环 M+1 次 = **M+2 次、M+1 次 sleep** | 偏差站点（第 1 轮 C7-004 已实测） |
| `RetryBatchLoader.load/retryLoad:40-70` | 加载链 | 初始 1 + 循环 M 次（incLoadRetryCount 从 1 起，`isExceedRetryCount(M)` 终止）= **M+1 次、M 次 sleep** | **语义正确**（与 RetryPolicy 注释「重试两次总共 3 次」一致） |
| `loadRetryCount` 计数器 | `BatchChunkContextImpl:29`（chunk 级字段，每 chunk 新建归零） | — | **无跨 chunk 预算耗尽问题**（排疑）；`BatchTaskContextImpl:60` 的同名 AtomicLong 是任务级聚合观测值，二者独立 |

**新增偏差结论**：同一 DSL `retryPolicy` 配置在消费链与加载链的总尝试次数**相差 1 次**（M+2 vs M+1）——配置声明与行为的偏差在两条链路不一致，修复面应一并归口（并入 C7-004 的修复建议）。

## 四、指令 ④ 执行记录：keyset/offset 翻页退化面扩展

### 4.1 OrmQueryBatchLoaderProvider 同族：复合排序列与 nullable 排序列（平台 keyset 契约实测）

`OrmEntityDao.findNext:705-715 → DaoQueryHelper.queryToFindNextSql:318-334`：

- **复合列安全**：`appendPkOrderBy:358-368` 自动把全部 PK 列追加到 orderBy（保证全序）；`appendCursorCondition:390-405` 按 `(f1 cmp v1 or (f1=v1 and f2 cmp v2) or ...)` 元组展开（不依赖行值比较语法），eq 分支对 NULL 值生成 `is null`（`appendCursorEq:407-414`）。**复合排序列的 keyset 语义正确，无同族缺陷。**
- **nullable 排序列失败墙**：`appendCursorCmp:416-431` 对锚点实体的排序值为 NULL 时直接 `throw ERR_ORM_CURSOR_SORT_VALUE_NULL`——任务在首个 NULL 锚点行处**确定性失败**，且该行已被当前 chunk 返回并消费。配合 orm-reader 无断点续传（C7-006）与 cron 触发：每次触发全额重扫至 NULL 行再失败，任务**永远无法完成**。失败响亮（非静默丢失），可通过查询过滤 `f is not null` 或列约束规避——立案 **C7-014**（P3）。
- `requireOrderColumn:434-447`：非实体列/带非 o owner 的 orderBy 显式报错——keyset 契约边界清晰，无退化路径。
- 第 1 轮 C7-007（活实体锚点漂移）结论不变：复合/PK 追加不修复该缺陷（漂移发生在用户排序列的 cmp 值上，PK 腿稳定）。

### 4.2 JdbcPageBatchLoaderProvider offset 参数包络量化（C7-008 补强）

`findPage → ISqlExecutor.findPage:184-186 → LongRangeBean(offset,limit) → JdbcHelper.prepareStatement:184-198 → dialect.getPaginationHandler().getPagedSql(range, sql)`——实测为方言级 LIMIT/OFFSET 改写（非 seek）。参数包络：

- 扫描量公式：`S(N,B) ≈ N²/(2B)` 行（N=总行数，B=batchSize）。
- 任务时长关系式：`T ≈ N·p + c·N²/(2B)`（p=每行处理成本，c=每行扫描丢弃成本）；进度分数 f 处的单 chunk 耗时 `T_chunk(f) ≈ B·p + c·f·N`（线性恶化，末页扫描整表前缀）。与 keyset 对比的**总额外扫描量等效于 N/2 行全表扫描**。
- 锚定示例：N=10⁷、B=1000 → S=5×10¹⁰ 行；以内存扫描 10⁷ 行/s 计 ≈ 83 分钟纯扫描开销，磁盘随机读场景放大 1~2 个数量级；chunk 数 = N/B = 10,000（每 chunk 1 次查询 + saveState=true 时 1 次 saveTaskState + 2 条 INFO 日志）。
- offset 快照不稳定性（并发删前移跳行/插后移重复读）维持 C7-008 原判；重复读变体在配 historyStore 时转化为 C7-015 的主键冲突失败面。

### 4.3 DaoBatchRecordHistoryStore.filterProcessed 语义（非 keyset/offset）

实测为每 chunk 一次 `batchTaskId= AND recordKey IN(chunkKeys) AND resultStatus=0` 查询（非翻页，代价与 chunk 大小绑定，走 4.2 所述 PK 前缀索引）。语义缺口发现：`buildRecordMap` 以 recordKey 为 LinkedHashMap 键——**keyExpr 产出 null/empty 的记录全部塌缩到单一 "default" 键**（`getRecordKey:59-64`），同 chunk 同键多记录只保留最后一个映射条目；跨 chunk 已存在 "default" 历史行时，塌缩条目被误判为已处理而跳过；同键多记录通过过滤后 `saveProcessed` 逐条 INSERT 同 (taskId,key) 主键行→ 复合主键冲突 → 事务回滚 → chunk 失败。按记录到达的 chunk 分布不同，呈现「确定性响亮失败」或（batchSize=1 等同键记录独占 chunk 场景）「静默误跳」双面行为——立案 **C7-015**。

## 五、指令 ⑤ 执行记录：时钟使用面与 completedIndex 崩溃窗口

### 5.1 时钟站点全清单（grep `System.currentTimeMillis|System.nanoTime|CoreMetrics.|new Date|Instant.|currentTimestamp` 实测，共 6 类）

| # | 站点 | 用途 | 长时任务时钟敏感性判定 |
|---|---|---|---|
| 1 | `BatchTask.java:300/350`（CoreMetrics.currentTimeMillis） | chunk 耗时统计，仅日志 | 回拨 → usedTime 负值（日志瑕疵）；无控制流依赖。不立案 |
| 2 | `DaoBatchStateStore.java:153/251/278`（currentTimestamp） | startTime/restartTime/endTime 元数据 | 纯元数据，无任何 lease/超时比较——这正是 C7-001 缺 stale 判定的另一面（第 1 轮已判，维持）。不立案 |
| 3 | `ResourceRecordLoaderProvider.java:373-376` | waitUntilUnderLimit deadline（墙钟） | 回拨 → 等待窗口拉长 ≤ overLimitWaitMillis（有界）；前跳 → 提前超时 fail-fast（响亮、有界）。不立案 |
| 4 | `EtlTaskState.java:21,26,40` / `EtlTableState.java:23` | 状态文件时间戳元数据 | 无控制流依赖。不立案 |
| 5 | 重试退避 `ThreadHelper.sleep(delay)`（RetryConsumeHelper:30 / RetryBatchLoader:58） | 相对 sleep，非墙钟差值 | 时钟无关（sleep 受信号中断语义平台管辖，不立案）。delay 计算的溢出面见 C7-013 |
| 6 | sys-event DSL `getEstimatedNow()`/`leaseExpireTime` | nop-sys 时钟服务 | 求值与读点均在 nop-sys（跨单元），batch 侧仅 DSL 文本引用。不立案 |

**无新增时钟发现。**

### 5.2 completedIndex 保存节奏与崩溃窗口量化

保存链实测：loader 推进 completedIndex（`ResourceRecordLoaderProvider:301`，连续完成水印）→ `BatchTask.processChunk:330` 在 consume 成功后调用 `stateStore.saveTaskState(false,null,context)` 每 chunk 一次持久化（`synchronized` 串行，CL-016）；DB 事务原子，无半态。崩溃窗口：

- **窗口**：线程完成 chunk k 的 consume（含 history 事务提交）之后、saveTaskState(k) 落库之前的区间。每线程窗口内未持久化工作量 ≤ 1 个 chunk；并发 C 线程独立窗口 → **单次崩溃最大重复量 ≤ C×B 行**（C=concurrency，B=batchSize）。
- **契约校准**：重复方向仅造成重做（at-least-once 内）——配 historyStore 时重做行被 filterProcessed 幂等过滤（读放大），未配时副作用重执行。无丢失面、无跳过面，不立案；量级示例：C=4、B=1000 → 单次崩溃 ≤ 4,000 行重做，占 10⁷ 行任务的 0.04%。
- 巨型单 chunk（大 B）场景单 chunk 重做成本 = 该 chunk 全量，属 batchSize 配置面，登记不立案。

## 六、新发现条目（从 C7-012 起）

### [nop-batch-C7-012] 状态机吸收态扩展：KILLED 永久不可重启 + 恢复入口仅有无校验的通用 CRUD 且与 version CAS 无 fencing 交互

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:108-113,187-195,233-255`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ICancellable.java:17`（`CANCEL_REASON_KILL="kill"` 为 `cancel()` 缺省值）；`nop-batch-meta/src/main/resources/_vfs/nop/batch/model/NopBatchTask/_NopBatchTask.xmeta:36-38`（taskStatus `updatable="true"`）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:481-522`（version CAS）
- **证据片段**:
  ```java
  // DaoBatchStateStore.startExistingTask —— KILLED 无任何逃生门
  if (task.getTaskStatus() == NopBatchDaoConstants.TASK_STATUS_KILLED)
      throw new NopException(ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_KILLED)...
  // ICancellable —— 缺省取消原因即 kill
  default void cancel() { cancel(CANCEL_REASON_KILL); }   // "kill"
  // _NopBatchTask.xmeta —— taskStatus 可经通用 CRUD 随意改写
  <prop name="taskStatus" ... insertable="true" updatable="true">
  ```
- **严重程度**: P2
- **并发场景**: 三方交错——外部取消/恢复操作者（嵌入代码或经 GraphQL `NopBatchTask__update` 的运维）、存活中的旧执行进程（chunk 线程 + saveTaskState）、新的启动触发（nop-job cron 调度线程）。
- **触发时序**: 时序 A（KILLED 吸收态）：运维/嵌入代码对挂死任务调用任务级 `context.cancel()`（缺省 reason="kill"，任务上下文经 BatchTaskGlobals/初始化器对外可达）→ `getTaskStatus:266-267` 落到 KILLED(60) → 此后该 (taskName,taskKey) 行的每次启动在 `:108` 被拒（`allowStartIfComplete` 仅豁免 COMPLETED），**任何配置组合都无法重启**——「杀掉失控任务」这一最自然的运维动作把行永久变砖。时序 B（无 fencing 恢复）：行陷入 RUNNING 死区（C7-001）→ 运维经通用 CRUD 把 taskStatus 改为 40（唯一非改库恢复入口）→ 若旧进程仍存活（如仅停止调度未杀进程），旧进程下一次 `saveTaskState`（每 chunk 一次）因 version 失配抛 `ERR_ORM_UPDATE_ENTITY_NOT_FOUND` → chunk 失败 → 任务 FAILED——恢复生效但依赖 version 冲突的随机失败，无确定性 fencing/租约语义；反之若运维误把状态改成 ≤RUNNING 的值（无转移校验），即手工制造 RUNNING 死区。
- **现状**: 状态机有两个吸收态（RUNNING 死区 = C7-001，KILLED 永久拒绝 = 本条）；唯一恢复入口是未声明的通用 CRUD 写（xmeta updatable=true，无转移校验、无专用 action、无审计语义）；`IBatchTaskManager` 与全部 BizModel 实测无 kill/reset/resume 方法。
- **风险**: 长时运行系统的必然事件（任务挂死/失控）触发的运维动作（kill、恢复）落到「永久变砖」或「无校验改库级操作」两个坏出口；KILLED 行在多租户/多任务环境下累积为不可解释的僵尸行（无任何 API 能列出或解除）。
- **建议**: (1) G1 增加显式逃生门（如 `allowStartIfComplete` 语义扩展或专用 `forceRestart` 布尔），或提供 `NopBatchTaskBizModel` 专用 kill/reset action 封装「cancel→终态→可重启」的合法转移；(2) 恢复采用 fencing：恢复时同时更新 workerId/lease token，旧进程 saveTaskState 的 WHERE 增加 workerId 匹配（复用现成 version CAS 基础设施即可半确定性化）；(3) xmeta 对 taskStatus 收紧为 `updatable="false"` 或提供只读字典校验，把状态修改收敛到专用 action。
- **失效面枚举（F3）**: 吸收态/恢复面全清单——① RUNNING 死区（C7-001，不重复立案）；② KILLED 永久拒绝（本条主体，缺省 `cancel()` 即可达）；③ startLimit 耗尽拒绝（`:115-120`，DSL 显式配置语义，Spring Batch 同族惯例，**不立案**）；④ COMPLETED 拒绝（有 `allowStartIfComplete` 逃生门，**不命中**）；⑤ SUSPENDED/CANCELLED/FAILED（>RUNNING，G5 可重启，**不命中**）；⑥ 通用 CRUD 无校验改状态（本条恢复面主体）；⑦ version CAS 驱逐旧进程（本条 fencing 面）；⑧ UUID-key 任务（绕过全部闸门，C7-011 ② 已立案，不重复）。
- **信心水平**: 确定（全部站点实测；时序 B 的旧进程存活前提在进程级恢复场景中为常见态）
- **误报排除**: 不是「已声明边界」——owner doc 未声明 KILLED 永久不可重启，亦未声明恢复路径；不是第 1 轮 C7-001 的重复——C7-001 立案 RUNNING 吸收态的成因与错误风暴，本条立案 (a) 第二吸收态 KILLED、(b) 恢复入口的存在性与不安全形、(c) 无 fencing 的恢复交互，三者均为第 1 轮未覆盖的独立失效面。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P2**。逐点亲验成立：G1 KILLED 快失败（DaoBatchStateStore.java:108-113）无逃生门、`allowStartIfComplete` 仅豁免 COMPLETED（:131）；`ICancellable.cancel()` 缺省 reason="kill"（ICancellable.java:23-24）+ getTaskStatus 非 suspend/skip 的 BatchCancelException → KILLED（DaoBatchStateStore:266）——「杀失控任务即变砖」在引擎层成立；xmeta taskStatus `insertable="true" updatable="true"`（_NopBatchTask.xmeta:36-38）+ 无转移校验亲验成立；version CAS 兜底亲验成立（nop-batch.orm.xml:42 versionProp + EntityPersisterImpl:508-521 ERR_ORM_UPDATE_ENTITY_NOT_FOUND），无确定性 fencing 成立。校准（不改变判级）：「KILLED 无任何 API 能解除」应读作「引擎层无专用解除端点」——通用 CRUD 写路径理论可解除（本条恢复面主体已如实登记该入口），与 C6 轮 C6-001 复核同口径。与 C7-001 的分工（本条=第二吸收态+恢复入口不安全形+无 fencing 交互）成立，非重复立案。现状：新发现·未修复。

### [nop-batch-C7-013] exponentialDelay 指数溢出精确归零点：重试次数跨过 64−v₂(retryDelay) 后退避变 0，与负 maxRetryCount 组合为零延迟无限紧循环

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java:139-160`（计算点与钳制点）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/RetryConsumeHelper.java:27-31`（batch 消费侧使用点）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/loader/RetryBatchLoader.java:57-61`（batch 加载侧使用点）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/AbstractRetryBatchConsumer.java:58`
- **证据片段**:
  ```java
  if (exponentialDelay) {
      timeToSleep = tryDelay * (long) Math.pow(2, retryTimes);   // long 溢出
  } ...
  if (timeToSleep < 0 || timeToSleep > maxRetryDelay) {
      timeToSleep = maxRetryDelay;    // 负溢出被钳制，但精确 0 不被拦截
  }
  ```
- **严重程度**: P2（单独配置下不可达；与已立案的 C7-004 负 maxRetryCount 组合后完全具体化且后果升级）
- **并发场景**: 每 chunk 线程独立进入重试循环；C 个并发 chunk 线程同时进入零延迟循环时 CPU 与日志放大按 C 倍增。
- **触发时序**: retryDelay = o·2^k（o 奇数）时 `tryDelay·2^rt mod 2^64` 在 rt ≥ 64−k 处**恒等于 0**（long 乘法溢出归零，`<0 || >max` 钳制不拦截 0 值）——缺省 retryDelay=1000=125×2³ → **第 61 次重试起退避恒为 0**；retryDelay=1024 → 第 54 次起。时间尺度：负 maxRetryCount（C7-004）下，前 61 轮以缺省 1s→2s→…→5min（maxRetryDelay）带抖动退避，累计约 4.6 小时；跨过 rt=61 后循环退化为 `sleep(0)` 无限紧循环——单核 100% CPU 自旋 + 每轮 1 条全栈 ERROR（`RetryConsumeHelper:55`，日志 I/O 成为唯一节流）+ 对下游的请求频率从 ≤1/5min/线程 跃升为每秒成百上千次/线程（重试风暴升级为打挂下游的请求洪流）。任务仍不终态（叠加 C7-001 死区面）。
- **现状**: `maxRetryDelay` 钳制只防「睡太久」与「负值」，未防「0 值」；`retryDelay=0` 经 Guard 合法（`Guard.nonNegativeInt`），`retryNTimes()` 工厂亦内置 0；DSL/xdef 对 maxRetryCount 无非负校验（C7-004）使 rt=61 可达。
- **风险**: 把 C7-004 的「≤5min/轮的慢性重试风暴」在约 4.6 小时后自动升级为「零延迟 CPU 自旋 + 无节流下游请求洪流」——故障形态随时间恶化，运维若按初期节奏估算影响将严重低估。
- **建议**: (1) `getRetryDelay` 对 `timeToSleep==0 && tryDelay>0` 的情况钳制为 maxRetryDelay（或直接用无乘法溢出的移位+上限写法 `tryDelay << min(rt, 62)` 后判溢出）；(2) 修 C7-004 的 maxRetryCount 非负校验即同时封闭本条触发链；(3) owner doc 声明 exponentialDelay 的有效指数上限。
- **失效面枚举（F3）**: getRetryDelay 消费点全清单——① `RetryConsumeHelper.checkRetry:27-31`（消费重试，同族命中）；② `RetryBatchLoader.retryLoad:57-61`（加载重试，同族命中）；③ `AbstractRetryBatchConsumer.checkRetry:58`（入口预检查，同族命中）；④ 平台其他 RetryPolicy 使用方（nop-commons 设施，按纪律登记不展开）；⑤ 非指数路径（`tryDelay*retryTimes` 线性）同样可溢出但结果只会被钳到 maxRetryDelay，**不命中**归零面。
- **信心水平**: 确定（溢出算术为可精确验证的整数运算：125×2⁶⁴ ≡ 0 mod 2⁶⁴）
- **误报排除**: 不是「看到 long 乘法就报」——负溢出已被现有钳制兜住（验证过非问题），立案的仅是「精确 0 不被拦截」这一具体缺口；触发链与 C7-004 的组合是完整可执行序列（负配置 → 无限重试 → rt 跨 61 → 紧循环），非理论风险。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留但降级 P2→P3，且核心推导有误须事实修正**。独立按精确 Java 语义模拟（`(long)Math.pow(2,rt)` 在 rt≥63 饱和为 Long.MAX_VALUE——本条 3.1 节自身已正确指出该饱和，却未代入归零点推导，推导内部不一致）：零延迟窗口 = **rt ∈ [64−k, 62]**（上限被饱和截断于 62），**并非「rt ≥ 64−k 恒等于 0」**。缺省 retryDelay=1000（k=3）：仅 rt∈{61,62} 两轮退避为 0；rt≥63 乘数饱和使 `tryDelay×(2^63−1) mod 2^64 = −tryDelay` → 负值被 :168-170 钳制**回落 maxRetryDelay**。retryDelay=1024（k=10）：零窗口 rt∈[54,62] 共 9 轮。因此「跨过 rt=61 后循环退化为 sleep(0) 无限紧循环」「零延迟 CPU 自旋 + 每秒成百上千次下游请求洪流」「故障形态随时间恶化」**均不成立**——实际后果为重试循环中段一次**有界**（≤k−1 轮）零延迟突发，随后自动恢复 ≤5min 退避；「约 4.6 小时」的累计时长推算本身无误（rt=0..8 指数 + rt≥9 钳制 5min ≈ 4.5h），但对应的是进入瞬时零延迟窗口而非永久恶化点。残余缺陷（钳制只拦 `<0||>max` 不拦精确 0）属实、触发链（需 C7-004 负 maxRetryCount）属实，故降 P3 保留：修复建议 (1) 仍成立（`timeToSleep==0 && tryDelay>0` 钳 maxRetryDelay 或移位写法），建议 (2) 封闭触发链不变。现状：新发现·未修复。

### [nop-batch-C7-014] orm-reader keyset 游标对 NULL 排序值为确定性失败墙：任务永不完成，cron 触发下每轮全额重扫后失败

- **文件**: `nop-batch/nop-batch-orm/src/main/java/io/nop/batch/orm/loader/OrmQueryBatchLoaderProvider.java:110-113`（findNext 调用点）；`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:416-431`（抛错点 `ERR_ORM_CURSOR_SORT_VALUE_NULL`，平台 keyset 契约实现）
- **证据片段**:
  ```java
  private static void appendCursorCmp(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity,
                                     OrderFieldBean field, boolean next) {
      Object value = entity.orm_propValue(col.getPropId());
      if (value == null)
          throw new NopException(ERR_ORM_CURSOR_SORT_VALUE_NULL).param(ARG_PROP_NAME, field.getName())...
  ```
- **严重程度**: P3（判级注明：失败响亮非静默、可通过查询过滤/列约束规避、无数据丢失；但「任务在该表上永远无法完成 + cron 下每轮重付全额扫描成本」是完全具体的长时稳定性退化，故不降为登记项）
- **并发场景**: 无需并发——单线程顺序翻页即触发；锚点行是上一页最后一行。
- **触发时序**: orm-reader 查询模型声明 `orderBy` 于可空列 C 且表中存在 C 为 NULL 的行 → 任务处理至排序位置紧邻 NULL 块的行时，该行成为锚点 → 下一次 `dao.findNext` 构造游标条件读锚点排序值 → NULL → 抛 `ERR_ORM_CURSOR_SORT_VALUE_NULL` → chunk 失败 → 任务 FAILED。数据库 NULL 排序位置决定代价：NULLs-last 方言（PostgreSQL ASC 缺省）下任务先处理完全表（小时级）才在末尾失败；NULLs-first 方言（MySQL ASC）首个 chunk 即失败。cron 触发（如 5s 周期）时每次触发重复同一循环：全额重扫（无断点，C7-006）→ 推进到 NULL 墙 → 失败，`nop_batch_task` 表同步累积 FAILED 行（若 saveState=true）。重启/重配前任务在该数据集上不可完成。
- **现状**: 平台 keyset 契约（`findNext`）对 NULL 排序值选择 fail-fast（`appendCursorEq` 的 eq 分支可处理 NULL，cmp 分支不可），契约合理；缺口在 batch 侧——DSL 构建期不校验「orderBy 列可空且数据含 NULL」的组合，owner doc 无「排序列必须非空」契约声明；orm-reader 缺省无 orderBy 时仅按 PK 排序（PK 非空，安全），缺口仅限显式 orderBy 可空列的配置。
- **风险**: 大表 + NULL 脏数据的组合在长时运行中常见（状态列、时间列未回填）；故障表现为「任务反复跑几小时然后失败」，日志仅一条游标异常，运维难以定位到「某行的排序列为 NULL」这一数据问题。
- **建议**: (1) DSL 构建期对 orm-reader 的 orderBy 列检查 orm 模型 mandatory 标记，可空列告警或报错；(2) owner doc 增加「orderBy 列在数据集上必须全部非空」契约；(3) 或在游标条件中对 NULL 生成 `is null` 分支扩展（平台侧改动，超出本单元，登记给平台 owner）。
- **失效面枚举（F3）**: keyset 游标边界全清单——① 用户排序列 NULL 值（本条主体）；② PK 列（非空约束，**不命中**）；③ 复合 orderBy 中 eq 腿的 NULL（`appendCursorEq:407-414` 生成 `is null`，正确处理，**不命中**）；④ 排序列被本任务变更（C7-007 已立案，不重复）；⑤ 非 o owner/非列 orderBy（`requireOrderColumn:434-447` 构建期显式报错，**不命中**）；⑥ jdbc-reader（非 keyset，C7-008/C7-009 面，不命中本条）。
- **信心水平**: 确定（抛错点与触发链实测；方言排序差异仅影响失败时刻不影响判定）
- **误报排除**: 不是平台设施重审——平台侧 fail-fast 是合理契约，立案点在 batch 侧对该契约的配置期无校验与文档无声明；失败响亮故不属「不产出也不报错」，判 P3 而非 P2 已按「可规避、响亮、无丢失」校准。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P3**。`appendCursorCmp` 对锚点排序值 NULL 抛 `ERR_ORM_CURSOR_SORT_VALUE_NULL` 亲验成立（DaoQueryHelper ~:416-431），对照 `appendCursorEq` 的 `is null` 分支（~:407-414）亲验成立；「eq 腿可处理 NULL、cmp 腿确定性失败」的契约描述准确；失败响亮/可规避/无丢失的 P3 校准合理，cron 下每轮全额重扫的退化面成立（叠加 C7-006 orm-reader 无断点）。现状：新发现·未修复。

### [nop-batch-C7-015] recordKeyExpr 空值塌缩 "default" 与同键重复记录的复合主键冲突：确定性任务失败 + 同键记录独占 chunk 时的静默误跳双面缺口

- **文件**: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/history/DaoBatchRecordHistoryStore.java:39-56,59-64,77-111`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/WithHistoryBatchConsumer.java:41-82`；`nop-batch/model/nop-batch.orm.xml:190-193`（BATCH_TASK_ID+RECORD_KEY 双 `primary="true"` 复合主键）
- **证据片段**:
  ```java
  protected String getRecordKey(S record, IEvalFunction fn, IEvalScope scope) {
      String key = StringHelper.toString(fn.call1(null, record, scope), null);
      if (StringHelper.isEmpty(key))
          key = DEFAULT_KEY;            // 空键全部塌缩到 "default"
  ...
  // buildRecordMap: map.put(recordKey, record)  —— 同键多记录仅最后一个存活
  // saveProcessed: dao.saveEntity(result)        —— 纯 INSERT，同 (taskId,key) 第二行撞复合主键
  ```
- **严重程度**: P3（判级注明：需「historyStore 已配置 + recordKeyExpr 产出重复键（含空键）」的组合配置；主行为是响亮失败，静默面限于特定 chunk 分布）
- **并发场景**: 单 chunk 线程即可触发；多 chunk 并发时不同线程处理同键记录（如 offset 翻页位移导致的重复读，C7-008 变体）同样撞主键。
- **触发时序**: 面 A（确定性失败）：任务配 historyStore，chunk 内出现两条同键记录（keyExpr 配错字段/业务键非唯一/两记录 key 均为空）→ `filterProcessed` 放行 → consume 处理 → `saveProcessed` 连续 INSERT 两行同 (batchTaskId,recordKey) → 第二行主键冲突 → 事务回滚 → chunk 失败 → 任务 FAILED。面 B（静默误跳）：同键记录**独占 chunk**（batchSize=1 或该 chunk 其余记录键不同且均已处理）且该键已有历史行 → `buildRecordMap` 塌缩后被 history 命中 → 记录被当作已处理**静默跳过**（WithHistoryBatchConsumer:56-60 标记 completed+history，不消费、不告警）——从未处理的记录被永久跳过且无死信记录。面 C（同 chunk 塌缩误判）：含 ≥2 条空键记录且 "default" 已有历史行的 chunk，最后一条空键记录被误跳（随后其余记录的 saveProcessed 触发面 A 失败，误跳被失败掩盖）。
- **现状**: ORM 模型已声明复合主键（第 1 轮 C7-003 未登记此事实，本轮修正）；`getRecordKey` 对空键的 DEFAULT_KEY 塌缩是显式代码；xdef/owner doc 均未声明「recordKey 在任务范围内必须唯一」与「空键塌缩」语义；重复键失败无专属错误码（以底层 SQL 异常呈现，运维难定位到 keyExpr 配置）。
- **风险**: keyExpr 配错是批处理任务常见配置错误；故障形态（SQL 主键冲突堆栈）与根因（键配置）距离远；面 B 的静默跳过超出 at-least-once 的已处理行丢失面——但触发分布窄（同键记录需独占 chunk 且键已有历史），故整条 P3。
- **建议**: (1) `saveProcessed`/构建期对同 chunk 重复键显式报专属错误码（指明 keyExpr 配置问题）；(2) 空键塌缩改为告警或按行号派生唯一键，杜绝跨记录塌缩；(3) owner doc 补「recordKey 任务内唯一」契约与空键行为声明。
- **失效面枚举（F3）**: recordKey 消费/写入面全清单——① `buildRecordMap` 塌缩（本条面 B/C）；② `saveProcessed` 重复 INSERT（本条面 A）；③ `filterProcessed` history 匹配（同 ① 的判定点）；④ `WithHistoryBatchConsumer` 全过滤分支（`:72-81`，把被过滤项标记 completed——键唯一时语义正确，**不独立命中**）；⑤ 非 historyStore 任务（无此账本，**不命中**）；⑥ 与 C7-008 重复读变体的组合（重复读 → 同键重插 → 主键失败，已计入面 A 触发源，不重复立案）。
- **信心水平**: 确定（塌缩/插入/冲突三步均为实测代码路径；复合主键为 orm 模型显式声明）
- **误报排除**: 不是「主键冲突是正常防护」的误报——问题不在冲突本身而在 (a) 冲突错误无业务语义定位信息、(b) 空键塌缩的静默误跳窗口、(c) 契约未声明；亦非 C7-003 重复——C7-003 立案增长无界，本条立案键语义缺口（并修正其「物理逐次插入」表述）。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P3**。三步链亲验成立：`getRecordKey` 空键塌缩 "default"（DaoBatchRecordHistoryStore:70-75）；`buildRecordMap` LinkedHashMap 同键 last-win（:61-67）；`saveProcessed` 对 filtered 逐条纯 INSERT（:91-110）× 复合主键（nop-batch.orm.xml:195-199 双 primary=true）→ 同键第二条撞主键。面 B（同键记录独占 chunk + 已有历史行 → WithHistoryBatchConsumer 全过滤分支 :72-82 静默标记 completed 不消费）亲验成立。复核补充一处精化：filterProcessed 的 history 匹配命中塌缩键时 map.remove 的是最后一条记录，故塌缩 chunk 重放的是首条——不改变面 B/C 判定。P3 校准合理 → 维持。现状：新发现·未修复。

### [nop-batch-C7-016] retry/skip 缺省异常谓词不对称：重试侧判死的 bizFatal 异常落入跳过侧缺省谓词被静默丢弃

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java:128-137`（retry 缺省谓词）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchSkipPolicy.java:46-53`（skip 缺省谓词）；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/consumer/SkipConsumeHelper.java:26-47`；`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:425-439`（装配顺序：skip 包在 retry 外层）
- **证据片段**:
  ```java
  // RetryPolicy —— 缺省：bizFatal 不可重试
  if (exception instanceof NopException) {
      return !((NopException) exception).isBizFatal();
  }
  return !(exception instanceof Error);
  // BatchSkipPolicy —— 缺省：仅 Error 不可跳过
  return !(exception instanceof Error);
  ```
- **严重程度**: P3（判级注明：需同时配置 retryPolicy 与 skipPolicy；行为是两层各按自身缺省谓词独立裁决的复合结果，单看每层都「正确」）
- **并发场景**: 单 chunk 线程即可触发；重试预算耗尽后异常沿调用栈上抛至 skip 层。
- **触发时序**: 任务同时配 `<retryPolicy/>` 与 `<skipPolicy/>`（retryOneByOne=false 装配：SkipBatchConsumer 在 retry 链外层，`BatchTaskBuilder:433-437`）→ 记录消费抛 `NopException`（bizFatal=true，如业务规则硬失败）→ retry 层 `getRetryDelay` 返回 -1（不可恢复）→ checkRetry 抛原始异常 → skip 层 `shouldSkip`：非 Error → **缺省可跳过** → 记录被静默丢弃（仅 INFO 日志 + skip 指标，`SkipConsumeHelper:36-42`）。「业务致命错误」这一最不应丢弃的异常类别成为唯一同时被两层缺省谓词放行丢弃的组合；非 bizFatal 异常则重试 M+2 次后再被跳过（C7-005 的无限跳过面上再多一层重试放大）。
- **现状**: 两套缺省谓词各自内部一致，但无文档声明其交集语义；`skipExceptionFilter` 可自定义收紧，缺省不收紧；owner doc 配置表未说明 retry 与 skip 叠加时的裁决顺序与缺省交集。
- **风险**: bizFatal 数据被静默丢弃且不入任何死信账本（C7-005 的账本缺失叠加）；运维从 `nop.batch.skip-error` INFO 日志才能发现，周期任务下毒记录永久循环丢弃。
- **建议**: (1) `BatchSkipPolicy` 缺省谓词与 RetryPolicy 对齐（bizFatal 的 NopException 缺省不可跳过）；(2) 或构建期检测同时配置两策略且 skip 用缺省谓词时告警；(3) owner doc 声明叠加语义表。
- **失效面枚举（F3）**: 异常裁决面全清单——① retry 缺省谓词（Error/bizFatal 判死，其余重试）；② skip 缺省谓词（仅 Error 判死）；③ ①∩② 交集 = bizFatal 被 skip 丢弃（本条主体）；④ retryOneByOne=true 装配（skip 内嵌于逐条路径 `RetryOneByOneBatchConsumer:31-38`，同一交集，同族命中）；⑤ 两层均自定义 filter（语义由用户定义，**不命中**）；⑥ 无 skipPolicy（bizFatal 判死后任务 FAILED，行为正确，**不命中**）。
- **信心水平**: 确定（两谓词与装配顺序实测；裁决链无其他分支）
- **误报排除**: 不是 C7-005 重复——C7-005 立案 maxSkipCount 缺省无限的无限跳过与无账本；本条立案两套缺省谓词的交集语义缺口（bizFatal 专属），修复点不同（谓词对齐 vs 上限校验）。
- **复核状态**: 未复核
- **复核裁定（C7 独立复核 2026-10-16）**: **保留，维持 P3**。两套缺省谓词亲验成立（RetryPolicy.isRecoverableException :128-137：NopException 按 !isBizFatal、其余排 Error；BatchSkipPolicy.shouldSkip :41-53 缺省仅排 Error）；装配顺序亲验成立（BatchTaskBuilder ~:425-439：retry 先包、skip 后包在外层，retryOneByOne=true 时 skip 内嵌于逐条路径）；bizFatal 为唯一交集丢弃组合的推导成立。与 C7-005 的分工（谓词交集 vs 无上限+无账本）成立 → P3 维持。现状：新发现·未修复。

## 七、维度集外线索（从 CL-036 起）

### [nop-batch-CL-036]（维度集外线索，不立案展开）
形态归属 C2（锁与同步原语——锁内 I/O 扩展面）。`ResourceRecordLoaderProvider` 的 chunk 收尾逻辑在 `synchronized(state)` 监视器内执行 `LOG.info("nop.batch.loader.chunk-end:...")`（`ResourceRecordLoaderProvider.java:294`，代码块尾部 `state.notifyAll()` 佐证处于锁内）——该锁同时串行化所有 chunk 线程的 `load` 入口（`setup` 返回的 lambda `synchronized(state){ load(...) }`），file/excel 大任务高并发时日志格式化+I/O 直接计入全局 load 串行点。严重度候选 P3（第 1 轮 CL-020「锁内 DB I/O」的同族文件版，合并修复面）。一句话证据：`LOG.info(...)` 与 `state.notifyAll()` 同处 `synchronized(state)` 块内。
- **复核处置（C7 独立复核 2026-10-16）**: **确认**。`LOG.info("nop.batch.loader.chunk-end:...")` 与 `state.notifyAll()` 同处 `synchronized(state)` 块内亲验成立（ResourceRecordLoaderProvider ~:283-310，chunk 收尾与 load 入口共用同一 state 监视器）；该锁串行化所有 chunk 线程 load 入口亲验成立。P3 候选维持，锁内 I/O 族（CL-012/016/020/036）合并修复面。

## 八、已验证一致 / 不立案结论（第 2 轮新增，复核阶段可直接引用）

1. **复合排序列 keyset 正确性**：`appendPkOrderBy` 自动补 PK 全序 + 元组展开游标条件（`DaoQueryHelper:318-405`），复合 orderBy 无漂移面（用户列漂移仍归 C7-007）。
2. **exponentialDelay 负溢出**：`<0 || >maxRetryDelay` 钳制覆盖全部负溢出路径（仅精确 0 缺口立案 C7-013）；非指数线性路径同样被钳制，无超限睡眠。
3. **loadRetryCount 跨 chunk 预算疑点**：`BatchChunkContextImpl.loadRetryCount` 为 chunk 级字段每 chunk 归零，任务级同名 AtomicLong 仅聚合观测——无「重试预算全局耗尽」问题。
4. **MathHelper.random() 争用疑点**：`s_randomImpl = DefaultThreadLocalRandom.INSTANCE`（ThreadLocal），`BatchChunkProcessor.adjustBatchSize` 每 chunk 调用无线程争用。
5. **completedIndex 崩溃窗口**：≤ C×B 行重做（见 5.2 量化），at-least-once 边界内，无丢失/跳过面。
6. **nop_batch_record_result 幂等查询索引**：复合主键 (BATCH_TASK_ID, RECORD_KEY) 前缀覆盖 `filterProcessed` 查询，代价 O(batchSize×log 行数)——C7-003 的查询退化表述按此修正（增长无界结论不变）。
7. **内部 fail-fast 不产生 KILLED 误分类**：`executeChunkLoop:254` 的 `context.cancel(STOP)` 后兄弟线程 BatchCancelException 不入 primaryErrorRef，终态由业务异常驱动为 FAILED（plan 2381 P1-3.3-003 机制实测有效）；KILLED 仅由外部缺省 `cancel()` 可达（C7-012 时序 A）。

## 九、总计与终止信号判定

| 类别 | 数量 | 明细 |
|---|---|---|
| 新增发现 | 5 | P2×2（C7-012 状态机吸收态/恢复面、C7-013 指数溢出零延迟）；P3×3（C7-014 NULL 游标失败墙、C7-015 recordKey 塌缩、C7-016 retry/skip 谓词不对称） |
| 维度集外线索 | 1 | CL-036（锁内日志 I/O，C2 归属） |
| 第 1 轮发现深化 | 5 | C7-001（转移守卫穷举 G1-G7 + 运维入口判定 + 风暴三项量级）；C7-003（清理面二态判定「代码完全不存在」+ 复合主键修正 + 12 项增长源复核）；C7-004（loader M+1 vs consumer M+2 计数不一致 F3）；C7-008（offset 参数包络公式与锚定示例）；C7-006（与 C7-014 的重跑叠加关系确认） |
| 不立案新结论 | 7 | 第八节 1-7 |

**终止信号判定**：本轮指令 ①~⑤ 全部执行完毕，各面均为穷举式核查（状态机 7 守卫、清理 grep 全资产面、retry/skip 全实现类、keyset/offset 全消费点、时钟 6 站点全判定）；新增发现全部为 P2/P3，**无新增 P0/P1**；第 1 轮两条 P1（C7-001/C7-003）经反向核查维持原判且修复面不变。剩余未决问题均为跨单元依赖（nop-job 同 job 重叠语义、nop-sys claim 原子性、平台 RetryPolicy 其他使用方），按盲审纪律不在本单元立案。**建议终止本维度，不启动第 3 轮。**
