# nop-sys 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-sys
- 文件数: 165（src/main/java，其中 26 个为 `_` 前缀/`_gen/` 生成文件；非生成 139 个）
- 覆盖范围声明: 7 个子模块（nop-sys-api 57 / nop-sys-dao 83 / nop-sys-service 24 / nop-sys-app 1 / 其余 0）的 src/main/java 全量扫描（空 catch、bare RuntimeException、printStackTrace、synchronized、轮询循环、private @Inject、Spring 注解等模式），核心手写基础设施逐行深读：SysSequenceGenerator、SysDaoResourceLockManager、EntityResourceLockState、SysDaoMessageService、NonBroadcastEventProcessor、BroadcastEventProcessor、SysEventHelper、SysDaoLeaderElector、SysCodeRuleGenerator、DefaultCodeRule、SysDictLoader、SysI18nMessageLoader、SysDaoNamingService、OrmEntityChangeLogInterceptor、SysCompactExtFieldHelper、全部 service 层 BizModel、configs/errors/constants、beans.xml 注册。api/crud、biz 接口与 entity 类为代码生成风格薄封装，抽样核读。关键结论均交叉验证了 nop-orm（deleteDirectly/genDeleteSql/version 初始化）、nop-commons（ResourceLock/BindScheduledExecutor/LocalResourceLockManager）、nop-api-core（IEstimatedClock）、nop-dao（DbEstimatedClock/findNext）的实现语义及 git 历史。target/、测试代码、前端 XML 不在范围内。已验证的关键调用链（nop-sys 以 `ioc:default="true"` 注册默认锁管理器与序列号生成器、条件注册消息服务）确认问题可触达。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 4 |
| P2 | 8 |
| P3 | 7 |

## 发现列表

### [P0] 分布式锁过期判断反向：争用时抢删有效锁（互斥失效），过期锁永不回收（死锁）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:175-177`（触发点 143-153）
- **维度**: D3（兼 D1）
- **证据**:
```java
protected boolean isExpired(NopSysLock entity, IEstimatedClock clock) {
    return entity.getExpireAt().getTime() >= clock.getMaxCurrentTimeMillis();
}
// 调用处：
if (existing != null) {
    // 如果已过期，则尝试删除
    if (isExpired(existing, clock)) {
        if (orm().tryDelete(existing))
            return null;
    }
}
```
- **现状**: `IEstimatedClock.getMaxCurrentTimeMillis()` 返回当前真实时间的**上限**（javadoc："currentTimeMillis <= maxCurrentTimeMillis"；实现见 `DbEstimatedClock.TimeData.getMax()`）。因此 `expireAt >= maxNow` 为真恰恰意味着"锁肯定还没过期"。判断写反了：本应是 `expireAt < clock.getMinCurrentTimeMillis()`（或至少 `< maxNow`）。同仓库 `LocalResourceLockManager`（`existingLock.getExpireTime() < current` 才删）与 `SysDaoLeaderElector`（`currentTime < expireAt - gap` 判"肯定未超时"）均使用正确方向，反证此处为笔误。git 历史显示该行自 2024-12-13 文件创建时即如此。
- **风险**: 双向破坏。(1) 锁仍有效（默认 lease 10s 内）时，任何争用者的 `tryLockWithLease` 循环都会把**有效锁删除并立即抢占**——两个持有者同时持锁，互斥完全失效（对该锁保护的业务并发约束失守）。这不是边角场景，而是 `tryLockWithLease` 的主争用路径。(2) 持有者宕机未释放时，锁真过期后 `isExpired` 返回 false，永远无人删除，所有等待者空转至 waitTime 耗尽后失败——资源被永久锁死，只能人工 `forceUnlock`。
- **建议**: 改为 `return entity.getExpireAt().getTime() < clock.getMinCurrentTimeMillis();`（保守口径），并为锁管理器补充"争用时不可获取""过期后可恢复"两条回归测试。
- **误报排除**: 已核实 `getMaxCurrentTimeMillis` 语义（javadoc + DbEstimatedClock 实现）、`tryDelete` 为按 id+version 的即时 DELETE（可成功删除活锁）、该方法仅此一处调用、该 bean 以 `ioc:default="true"` 注册（app-dao.beans.xml:24）为默认 `IResourceLockManager`。

### [P1] 序列号生成对 cacheSize=NULL 的序列必然 NPE，该序列完全不可用

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:233`
- **维度**: D1
- **证据**:
```java
item.update(seq);
item.cacheSize = seq.getCacheSize();   // getCacheSize() 返回 java.lang.Integer，自动拆箱
```
- **现状**: `_NopSysSequence.getCacheSize()` 返回 `java.lang.Integer`；ORM 模型中 `CACHE_SIZE` 列**无 mandatory、无 defaultValue**（nop-sys.orm.xml:52-53），管理端新建序列可不填缓存个数。首次取号进入 `syncFromDb` 时 `int cacheSize = seq.getCacheSize()` 拆箱抛 NPE。对照 `SeqItem.update()` 中 `stepSize` 做了 `!= null` 防护，cacheSize 此处漏防。
- **风险**: 任何 cacheSize 为 NULL 的序列，每次 `generateLong/generateString` 都抛 NPE——依赖该序列生成主键/业务编码的业务全部失败，且因 `runLocal` 是 REQUIRES_NEW 独立事务，失败发生在取号环节。
- **建议**: `Integer cacheSize = seq.getCacheSize(); item.cacheSize = cacheSize == null ? 0 : cacheSize;` 并补一条 cacheSize=NULL 的单测。
- **误报排除**: 已核对生成实体 getter 签名（`java.lang.Integer getCacheSize()`）与 ORM 列定义；`addDefaultSequence` 只保证 default 序列 cacheSize=100，不覆盖其他序列行。

### [P1] 事件消费定时任务无异常保护：一次 DB 异常即永久停摆，消息静默积压

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:170-175`（消费入口 189-192、255-258；异常可达点 `NonBroadcastEventProcessor.java:68/73/101/138`、`BroadcastEventProcessor.java:55/63/78-79`）
- **维度**: D3（兼 D4）
- **证据**:
```java
checkBroadcastFuture = timer.executeOn(executor).scheduleWithFixedDelay(this::processBroadcastEvent,
        checkBroadcastEventInterval.toMillis(), ...);
...
protected void processNonBroadcastEvent() {
    ensureNonBroadcastProcessor();
    nonBroadcastProcessor.process();   // fetchCandidates()/claim() 的 DB 异常直接向上抛
}
```
- **现状**: `BindScheduledExecutor.scheduleWithFixedDelay`（nop-commons）在任务抛 Throwable 时执行 `future.completeExceptionally(e)`，此后每次调度因 `future.isDone()` 直接返回——**周期任务永久停止**。而 `process()` 内只有逐事件处理的 `process(event)` 包了 try/catch，外层的 `fetchCandidates()`（DB 查询）、`claim()`（`tryUpdateManyWithVersionCheck` DB 写）、`ensureStartTimeInitialized()`（`getDbEstimatedClock()` DB 调用）均无保护。
- **风险**: 一次数据库抖动/连接池耗尽/主从切换（现实常见的瞬时异常）后，广播与非广播事件消费全部静默停摆直到重启；事件表持续积压，依赖事件驱动的业务流程断裂（丢实时性、告警缺失），且只有一条 error 日志可循。
- **建议**: 在 `processBroadcastEvent/processNonBroadcastEvent` 外层包 try-catch（记录 error 后返回，让下个周期继续）；或改造调度器对周期任务做异常隔离。
- **误报排除**: 已核实 `GlobalExecutors.globalTimer()` 返回 `DefaultScheduledExecutor`（裸委托 JDK ScheduledExecutorService），`executeOn` 返回的 `BindScheduledExecutor` 上述停摆语义（BindScheduledExecutor.java:88-103），以及两个 process 方法确实无兜底 catch。

### [P1] 锁的释放/续约版本防护形同虚设：新锁行 version 恒为 0，旧持有者可删/改新持有者的锁

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:229-241`（releaseLock）、202-215（tryResetLease）
- **维度**: D3
- **证据**:
```java
public void releaseLock(IResourceLockState lock) {
    NopSysLock entity = ((EntityResourceLockState) lock).getEntity();
    runLocal(session -> {
        session.deleteDirectly(entity);
//            SQL sql = ... .and().eq(NopSysLock.PROP_NAME_version, entity.getVersion()).end();
        return null;
    });
}
```
- **现状**: `deleteDirectly` 底层确实生成带版本条件的 `DELETE ... WHERE id=? AND version=?`（GenSqlHelper.genDeleteSql:176-181），但 `EntityPersisterImpl.processOptimisticLockVersion` 将新实体版本初始化为 0——**每次重建的锁行 version 都是 0**，与旧持有者内存实体的 version 恒相等。于是持有者 A 租约过期、锁被 B 接管（新行 version=0）后，A 的 `unlock()` 执行 `DELETE WHERE id AND version=0` 会**删掉 B 的锁**，第三个竞争者随即获取——互斥破坏。`tryResetLease` 同理会改写新持有者的 expireAt。`ResourceLock.unlock()` 也不先检查 `isHoldingLock`。源码中被注释掉的显式版本条件 SQL 说明作者曾考虑该语义。
- **风险**: 租约过期+接管是分布式锁的正常路径（P0 修复后即成为现实路径；当前也可经 `forceUnlock` 后的重新争用触发）。长任务持有者（lease 10s 默认值很容易超过）晚释放即可能破坏他人锁。
- **建议**: release/reset 前比对 DB 中 holderId 与自身一致（或引入全局递增的锁 token/epoch 替代 version），失败时仅记日志不删行。
- **误报排除**: 已核实 version 初始化为 0 的位置（EntityPersisterImpl.java:348-355）、delete SQL 生成含版本条件、NopSysLock 模型 `versionProp="version"`、以及 detach 实体在 `deleteDirectly` 的 `isManaged` 检查可通过（session close 不重置实体状态）。

### [P1] 事件载荷静默丢失：非 ApiRequest 消息/回复被序列化为空 Map "{}"

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysEventHelper.java:84-111`
- **维度**: D8（兼 D1）
- **证据**:
```java
payload.eventHeaders = JsonTool.stringify(Collections.emptyMap());
payload.eventData = JsonTool.stringify(Collections.emptyMap());

if (message instanceof ApiRequest) {
    ...
    if (request.getData() != null) {
        payload.eventData = JsonTool.stringify(request.getData());
    }
}
```
- **现状**: `toEventPayload` 只处理 `ApiRequest` 类型消息；String、Map、POJO 等其他类型落库时 eventData/eventHeaders 一律为 `"{}"`，消费端 `fromSysEvent` 重建的 `ApiRequest.data` 为空 Map。没有任何校验或警告。本模块自身的 ack 回路即触发点：`SysDaoMessageService.invokeConsumer`（403-420 行）把消费者返回值 `send(getAckTopic(topic), ret, options)` 发往 "ack-" topic——返回值通常是任意业务对象。
- **风险**: 消息未丢但内容丢失且完全静默：依赖消息体/回复内容的业务流程拿到空数据，故障极难定位（发送端对象完好、消费端 data 为 {}）。
- **建议**: 非 ApiRequest 消息用 `JsonTool.stringify(message)`（或至少 LOG.warn 拒绝），保持往返一致。
- **误报排除**: 已核实该服务所有投递均经 DB 往返（`localService.send` 被重定向到本服务 saveMessage，消费经扫描→`fromSysEvent`），无本地直投旁路。

### [P2] randNumber 编码段使用有符号 BigInteger：约半数编码带 '-' 前缀，且可能抛越界异常

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/coderule/DefaultCodeRule.java:66-68`
- **维度**: D1
- **证据**:
```java
byte[] bytes = new byte[count];
MathHelper.secureRandom().nextBytes(bytes);
return new BigInteger(bytes).toString().substring(0, count);
```
- **现状**: `new BigInteger(byte[])` 按有符号二补数解析：最高位为 1（概率约 50%）时 `toString()` 以 '-' 开头，`substring(0, count)` 把负号放进业务编码；且 `toString()` 会去前导零，长度可能小于 count（小 count 时概率可观，如 count=2 时约 10/65536），此时 `substring(0, count)` 抛 `StringIndexOutOfBoundsException`。
- **风险**: 使用 `{randNumber:n}` 模式的业务编码随机出现 '-' 前缀或生成失败；负号还可能违反字段校验/破坏下游解析。
- **建议**: `new BigInteger(1, bytes)`（强制正数）+ 长度不足时 `leftPad` 补零。
- **误报排除**: 已核对 BigInteger(byte[]) 构造器语义；count 上限 20（MAX_COUNT）下正数分支长度通常足够，主要风险是负号与小 count 越界。

### [P2] 事件表/广播表零索引且无清理任务：500ms 轮询全表扫描，表无限增长

- **文件**: `nop-sys/model/nop-sys.orm.xml`（全文件 247 列定义、0 个 index）；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/NonBroadcastEventProcessor.java:92-101`、`BroadcastEventProcessor.java:57-63`
- **维度**: D6
- **证据**:
```java
TreeBean filter = FilterBeans.and(
        FilterBeans.in(NopSysEvent.PROP_NAME_eventTopic, topics),
        FilterBeans.in(NopSysEvent.PROP_NAME_eventStatus, List.of(WAITING, CLAIMED)),
        FilterBeans.le(NopSysEvent.PROP_NAME_scheduleTime, new Timestamp(now)), ...);
List<NopSysEvent> candidates = dao.findNext(null, filter, null, fetchSize * 4);
```
- **现状**: nop-sys.orm.xml 未声明任何索引（唯一含 "index" 的文本是列名 PARTITION_INDEX）。每 500ms（checkSimpleEventInterval）按 topic+status+scheduleTime 扫描 nop_sys_event、按 topic+eventTime 扫描 nop_sys_broadcast_event。同时模块内没有任何对 PROCESSED 事件、已消费广播行、无订阅者 topic 行的清理/归档任务（全仓 grep 无 deleteByQuery 作用于这两张表，也无 job 配置）。
- **风险**: 表随时间线性膨胀，轮询查询退化为全表扫描，DB CPU 与消费延迟持续恶化，最终事件队列近乎停摆——对"事件队列丢消息=业务流程断裂"的基础设施是慢性致命伤。
- **建议**: 为 nop_sys_event 增加 (eventTopic, eventStatus, scheduleTime)、为 nop_sys_broadcast_event 增加 (eventTopic, eventTime) 索引；增加按策略清理已处理/过期事件的定时任务。
- **误报排除**: 已确认 orm.xml 为模型源头（model-first，索引应在此声明）；消息服务默认启用路径（app-dao.beans.xml:32-41 + nonBroadcastAutoScanEnabled 缺省 true）。

### [P2] tryLockWithLease 捕获全部异常仅记 TRACE：DB 故障被吞成"抢锁失败"

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:124-127、138-141、167-170`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    // ignore data base error
    LOG.trace("nop.lock.sys.save-lock-failed:resourceId={}", resourceId, e);
}
```
- **现状**: `saveNew` 的重复键冲突与连接失败、权限错误等所有异常共用同一 catch，且仅 TRACE 级别（生产日志默认不可见），无法区分"锁被他人持有"与"数据库不可用"。DB 故障时线程以 100ms 间隔反复尝试连接直到 waitTime 耗尽，调用方只得到 tryLock=false。
- **风险**: 数据库故障期间所有依赖锁的业务静默退化为"无锁可用/降级运行"，排障时无日志线索；trace 级别也违背"错误必须可见"的处理原则。
- **建议**: 精确捕获重复键异常（参照 SysSequenceGenerator.addDefaultSequence 对 `DaoErrors.ERR_SQL_DUPLICATE_KEY` 的判断），其余异常至少 WARN/ERROR 记录。
- **误报排除**: 三处 catch 均为 `catch (Exception e)`，无异常类型区分，已确认无其他日志补充。

### [P2] 雪花序列 workerId 由 hostId 哈希到 1024 槽位：多节点部署可碰撞导致跨节点重号

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:128-135`
- **维度**: D3
- **证据**:
```java
if (workerId == 0) {
    String hostId = CFG_HOST_ID.get();
    if (StringHelper.isEmpty(hostId)) hostId = NetHelper.findLocalIp();
    workerId = Math.abs(HashHelper.murmur3_32(hostId) % 1024);
}
this.snowflakeGenerator = new SnowflakeSequenceGeneator(workerId);
```
- **现状**: 未显式配置 `nop.sys.seq.snowflake-worker-id` 时，workerId 取 hostId/IP 哈希模 1024。约 24+ 节点时碰撞概率已超 20%（生日问题），动态扩缩容的容器环境（IP 复用、hostId 未稳定配置）更易碰撞。
- **风险**: 两个节点同 workerId 时，snowflake 型序列在同一毫秒+同序号下生成**完全相同**的 ID——若用作业务主键即主键冲突/数据覆盖。
- **建议**: 启动时向协调存储（如 nop_sys_sequence 表）注册并租用唯一 workerId，或至少碰撞时告警；文档强调多节点必须显式配置。
- **误报排除**: 已核实 `SnowflakeSequenceGeneator`（nop-dao）的 workerId 仅 10bit（MAX 1023），ID 构成为 timestamp+workerId+sequence，同 workerId 同毫秒同序号即重号。

### [P2] 序列配置缓存无失效机制：管理端修改不生效且会被内存轨迹写回覆盖

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:55、96-102、226-247`；`nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/entity/NopSysSequenceBizModel.java`（纯 CrudBizModel 无钩子）
- **维度**: D1（兼 D6）
- **证据**:
```java
private final Map<String, SeqItem> cache = new ConcurrentHashMap<>();
...
public void clearCache() { cache.clear(); }      // 全仓库无调用方
public void removeCache(String cacheKey) { cache.remove(cacheKey); }  // 全仓库无调用方
```
- **现状**: SeqItem（含 nextValue/cacheSize/stepSize）按 JVM 永久缓存，`clearCache/removeCache` 全仓 grep 无任何调用；NopSysSequenceBizModel 是无定制钩子的 CRUD 模型，修改/新建序列后不会通知生成器。且下次 `syncFromDb` 会用内存轨迹推进 DB nextValue（`seq.setNextValue(item.nextValue + cacheSize*step)`），覆盖管理端的修改。
- **风险**: 运维通过管理端回拨 nextValue 修重号时修复静默失效，缓存耗尽后写回旧轨迹继续分配，可能再次发出已冲突的号段；调整 cacheSize/stepSize 同样长时间不生效，多节点行为不一致。
- **建议**: 在序列管理 BizModel 的保存/删除后调用 `removeCache(seqName)`（该组件已在本模块内，改动极小）。
- **误报排除**: 已 grep 全仓确认 clearCache/removeCache 零调用；确认 BizModel 无 after-update 钩子。

### [P2] SysDaoNamingService.cleanup 定时任务一次异常即被永久取消

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/naming/SysDaoNamingService.java:76-79、89-96`
- **维度**: D3（兼 D4）
- **证据**:
```java
cleanupFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::cleanup, ...);
...
void cleanup() {
    ...
    dao.deleteByQuery(query);   // 无 try-catch
}
```
- **现状**: `globalTimer()` 是 `DefaultScheduledExecutor`（裸委托 JDK ScheduledExecutorService），周期任务抛出未捕获异常即被取消且无日志。cleanup 中的 deleteByQuery 遇到一次 DB 异常后清理永久停止。
- **风险**: 实例表 nop_sys_service_instance 中失效临时实例（isEphemeral=true）不再被清理，服务发现 `getInstances` 虽有 updateTime 过滤兜底，但表持续膨胀、扫描变慢。
- **建议**: cleanup 方法内部包 try-catch 记录 error。
- **误报排除**: 已核实 DefaultScheduledExecutor 直接透传 JDK 语义（DefaultScheduledExecutor.java:132-135）。

### [P2] ISequenceGenerator 契约漂移：序列不存在时静默降级 UUID，专用错误码从未使用

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:261-271`；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/NopSysErrors.java:21-23`
- **维度**: D8
- **证据**:
```java
if (seq == null) {
    if (useDefault && !SEQ_DEFAULT.equals(seqName)) {
        SeqItem defaultItem = findSeqItem(SEQ_DEFAULT, false);
        return defaultItem;
    }
    item = new SeqItem(seqName);   // useUuid = true，静默随机
    defaultCache.put(seqName, item);
    return item;
}
```
- **现状**: 接口契约只定义了"不存在时是否回退 default"；实现中 `useDefault=false` 且序列缺失时返回 `MathHelper.randomPositiveLong()`/随机 UUID，不报错。模块专门定义的 `ERR_SYS_NO_SEQ`（"在序列号表中缺少对应记录"）**全仓零引用**。此外走 default 回退的未知序列名不缓存，每次取号都执行一次 `loadCacheItemFromDb` 的 DB 查询。
- **风险**: 序列名拼写错误/漏配置被随机 ID 掩盖，问题延迟暴露到数据层；随机正长量程有限（`randomPositiveLong`），高并发下理论碰撞概率非零，用作主键有隐患。
- **建议**: `useDefault=false` 时抛 `NopException(ERR_SYS_NO_SEQ).param(...)`；回退结果按名称缓存避免重复查库。
- **误报排除**: 已 grep 确认 ERR_SYS_NO_SEQ 无引用；已核对接口 javadoc 无"降级为 UUID"语义。

### [P2] 事件队列吞吐上限：每分区每 500ms 仅消费 1 条，无 bizKey 的 topic 全部挤在单分区

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/NonBroadcastEventProcessor.java:65-82`；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysEventHelper.java:65-76`
- **维度**: D6
- **证据**:
```java
for (NopSysEvent event : claimed) {
    process(event);
    processedPartitions.add(event.getPartitionIndex());   // 本轮扫描内该分区不再处理
}
// partition 决定逻辑：
if (!StringHelper.isEmpty(payload.bizKey)) return StringHelper.shortHash(payload.bizObjName + '|' + payload.bizKey);
if (!StringHelper.isEmpty(payload.topic)) return StringHelper.shortHash(payload.topic);  // 无 bizKey → 整个 topic 一个分区
```
- **现状**: 单次 `process()` 内每分区只处理 1 条（保序设计），扫描周期默认 500ms；非 ApiRequest 消息无 bizKey，partition = hash(topic)，整个 topic 串行单分区。
- **风险**: 无 bizKey 的 topic 理论吞吐 2 条/秒；高峰期事件积压、延迟以分钟计，业务方易误判为"丢消息"。
- **建议**: 单轮扫描内对同一分区按序处理多条（claim 后逐条推进即可保序）；或缩短扫描间隔/文档明确要求带 bizKey。
- **误报排除**: 已核对 checkSimpleEventInterval 默认 500ms 与 beans.xml 注入路径；确认 maxScanLoops 只是外层循环上限，不改变每分区每轮 1 条的事实。

### [P3] 广播 topic 前缀 "bro-" 硬编码，未复用平台常量

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:344`
- **维度**: D7
- **证据**:
```java
if (topic != null && topic.startsWith("bro-")) {
```
- **现状**: `MessageCoreConstants.TOPIC_PREFIX_BROADCAST = "bro-"` 已存在且同包 `SysEventHelper` 已 import 该常量类，此处仍写死字符串。
- **风险**: 前缀约定变更时此处静默失配，广播/非广播路由错乱。
- **建议**: 改用 `TOPIC_PREFIX_BROADCAST` 常量。
- **误报排除**: 已核对常量值一致（当前行为正确，仅维护性风险）。

### [P3] runInNewTransaction 名不副实：不开事务且全仓无调用方

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:251-253`
- **维度**: D4（维护性）
- **证据**:
```java
public <R, T> T runInNewTransaction(Function<R, T> fn, R request, TransactionPropagation propagation) {
    return fn.apply(request);   // 没有任何事务语义
}
```
- **现状**: 方法名与参数承诺 REQUIRES_NEW 等传播行为，实现只是直接调用。
- **风险**: 未来调用方按名字依赖事务保障会落空。
- **建议**: 删除或实现真实事务逻辑。
- **误报排除**: 已 grep 全仓确认无调用方（可能为外部模块预留，但语义误导应消除）。

### [P3] SysDaoMessageService.startTime 为死赋值字段

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:76、168`
- **维度**: D1（死代码）
- **证据**: `private Timestamp startTime;` 仅在 `doStart()` 赋值一次，全类无读取（BroadcastEventProcessor 自行计算 startTime）。
- **现状/风险/建议**: 无行为影响，仅误导维护者以为起点可控；建议删除。
- **误报排除**: 已 grep 该类所有 `startTime` 出现位置。

### [P3] SysDictLoader.loadDict 忽略 locale 参数，标签不随请求本地化

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/dict/SysDictLoader.java:58-62`
- **维度**: D8
- **证据**:
```java
public DictBean loadDict(String locale, String dictName, IEvalContext ctx) {
    ...
    bean.setLocale(I18nMessageManager.instance().getDefaultLocale());  // 传入的 locale 被丢弃
```
- **现状**: 请求指定 locale 时仍取服务端默认 locale，且 NopSysDictOption 只有单一 label 列，无多语言列。上层 DictProvider 的缓存 key 含 locale，不同 locale 会缓存"同一份默认内容"。
- **风险**: 多语言场景字典标签始终为默认语言，属功能缺口而非崩溃。
- **建议**: 至少在 label 取值处按 locale 解析（如 label 存 i18n key 时走 I18nMessageManager），或文档明确 sys/ 字典不支持多语言。
- **误报排除**: 已核对 DictOptionBean 仅有单 label 字段，确认为设计缺口。

### [P3] SysCompactExtFieldHelper.refreshCache 非原子：clear 与 putAll 之间存在空窗

- **文件**: `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/impl/SysCompactExtFieldHelper.java:50-53`
- **维度**: D3
- **证据**:
```java
entityFieldPositions.clear();
entityFieldPositions.putAll(positions);
entityFieldConfigs.clear();
entityFieldConfigs.putAll(configs);
```
- **现状**: 并发读线程在 clear 与 putAll 之间会短暂读到空映射，position 解析退化为 extName 解析或返回 null。
- **风险**: 刷新瞬间的取值可能短暂偏差，概率低且自愈；影响有限。
- **建议**: 用 volatile 引用整体替换 Map（写时复制）。
- **误报排除**: 已确认该类多线程可达（ORM 实体访问路径）。

### [P3] SysCodeRuleGenerator.generate 每次生成编码都查询编码规则表，无缓存

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/coderule/SysCodeRuleGenerator.java:53-60`
- **维度**: D6
- **证据**:
```java
public String generate(String ruleName, Object bean) {
    NopSysCodeRule example = new NopSysCodeRule();
    example.setName(ruleName);
    NopSysCodeRule rule = daoProvider.daoFor(NopSysCodeRule.class).findFirstByExample(example);
```
- **现状**: 规则配置基本不变，但每次生成编码（常在主键/单据号路径）都走一次 DB 查询。
- **风险**: 高频单据生成场景的额外 DB 往返；规则表无索引时叠加扫描成本。
- **建议**: 按规则名做带失效的本地缓存。
- **误报排除**: 已确认无任何缓存层包裹。

### [P3] 非广播消息仅投递给同 topic 的第一个订阅者

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:389-401`
- **维度**: D8
- **证据**:
```java
if (broadcast) { throw new NopSysDaoException("Broadcast durable path should invoke one consumer at a time"); }
return invokeConsumer(subscriptions.get(0).consumer, topic, message, options, false);
```
- **现状**: 队列语义下同 JVM 内同一 topic 的多个持久订阅者，只有 `subscriptions.get(0)` 永远收到消息，其余订阅者永远收不到（并非轮询/竞争分发）。
- **风险**: 使用者按常见 MQ 直觉注册多个消费者时部分消费者静默饿死。
- **建议**: 文档明确"每 topic 单消费者"约定，或实现轮询分发；至少在第二个订阅者注册时告警。
- **误报排除**: 已核对 subscribe 逻辑，durableSubscriptions 按 topic 累积列表，消费只取 get(0)。

## 补充说明

- 未发现 private 字段注入（D7）：全部 `@Inject` 字段为 package-private/protected 或 setter 注入，符合 Nop IoC 约定；配置注入均使用 `@InjectValue`；未发现 Spring 依赖残留。
- `NopSysDaoException extends RuntimeException` 为模块级异常类，符合平台两档错误处理策略（模块内部允许），不计为发现。
- 未发现资源泄漏（D2）：模块内无直接流/连接操作，均委托 ORM 层托管。
- Maker-Checker 审批的业务逻辑（maker/checker 记录流转）在 nop-sys 中仅为 CrudBizModel 薄封装（NopSysMakerCheckerRecordBizModel/NopSysCheckerRecordBizModel 各 21 行），实际机制由 nop-biz 框架承担，本模块未发现问题。
- SysDaoLeaderElector 的时间边界使用正确（`currentTime < expireAt - leaseSafeGap` 判"肯定未超时"），与 P0 形成对照，可作为修复 isExpired 的参考写法。
