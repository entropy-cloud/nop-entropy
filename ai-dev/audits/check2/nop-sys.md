# nop-sys 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-sys
- 文件数: 165（src/main/java 全量，含 `_gen/` 生成文件 26 个）；非生成实现文件 139 个，其中 57 个为 nop-sys-api 下带 `__XGEN_FORCE_OVERRIDE__` 标记的生成保留数据类
- 覆盖范围声明: 深读 36 个含逻辑文件——dao 层全部 18 个实现类（SysDaoResourceLockManager、SysSequenceGenerator、SysCodeRuleGenerator、DefaultCodeRule、SysDictLoader、SysI18nMessageLoader、SysDaoMessageService、Broadcast/NonBroadcastEventProcessor、SysEventHelper、SysDaoLeaderElector、SysDaoNamingService、OrmEntityChangeLogInterceptor 及 configs/errors）、service 层 10 个 BizModel + SysCompactExtFieldHelper + 3 个常量类、app 入口 NopSysApplication、实体壳类抽查 3 个（NopSysLock/NopSysEvent/NopSysUserVariable，其余 16 个经行数与结构核验为 20-38 行无逻辑壳类）；api 模块 57 个与 biz 层 20 个标记接口仅结构扫描；模式扫描（private @Inject、Spring 注解、SimpleDateFormat、bare RuntimeException、catch 吞噬、beans.xml 注册核对）覆盖 139 个非生成文件 100%。结论均经调用方/被调方（AbstractDaoHandler、ResourceLock、AbstractPollingLeaderElector/AbstractLeaderElector、LocalMessageService、OrmEntityDao.findNext、tryUpdateManyWithVersionCheck、ContextProvider、RetryPolicy、DbEstimatedClock、app-dao.beans.xml、nop-sys.orm.xml）交叉验证。未覆盖区域: nop-sys-api 生成数据类字段级内容、_gen 生成实体、src/test。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 4 |
| P3 | 8 |

## 发现列表

### [P1] 编码规则 `@seq:N` 在序号超过 N 位时静默截断低位，必然产生重复业务编码

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/coderule/DefaultCodeRule.java:{76-89}`
- **维度**: D1（边界条件）、D8
- **证据**:
```java
protected String generateSeq(String options, CodeRuleParams params) {
    int count = ConvertHelper.toPrimitiveInt(options, ...);
    if (count > MAX_COUNT)
        throw new NopException(NopSysErrors.ERR_SYS_CHAR_COUNT_EXCEED_LIMIT)...;

    long seq = params.getSeqGenerator().getAsLong();
    String str = String.valueOf(seq);
    if (str.length() < count) {
        return StringHelper.leftPad(str, count, '0');
    } else {
        return str.substring(str.length() - count);   // 截取末 N 位，无告警
    }
}
```
- **现状**: 序号不足 N 位时左补零；超过 N 位时只保留末 N 位。序列由 `SysSequenceGenerator` 提供且全局单调递增、无按周期重置逻辑（`syncFromDb` 只推进 `nextValue`）；测试（`TestCodeRule.testCodeRule`）只覆盖 seq=1 补零路径。
- **风险**: 配置如 `D{@year}{@seq:4}` 的规则在累计生成超过 10000 次后，编码从 `...9999` 回绕到 `...0000`，与历史编码静默重复，无任何异常或日志；多节点各持有 cacheSize 区间时回绕更早出现。业务编码（单据号/主键前缀）重复属数据级错误。
- **建议**: 序号位数超限时抛出 `ERR_SYS_CHAR_COUNT_EXCEED_LIMIT` 类错误（与 `generateRand` 的防御一致），或在生成器层校验 seq < 10^count；至少记录告警日志。
- **误报排除**: 已读 `SysSequenceGenerator`（无重置逻辑、cacheSize 只影响批量取号不改变单调性）、`SysCodeRuleGenerator.generate`（seqGenerator 直接绑定该 seq 名）、`TestCodeRule` 与 `TestSysCodeRuleGeneratorCache`（无超限用例，确认非预期设计已被测试固定）；`generateFromProp` 的同类截断是对实体属性取末 N 位（属性值稳定），与序号截断的重复后果不同。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. generateSeq 超宽时抛新错误码 ERR_SYS_SEQ_VALUE_EXCEED_LIMIT（nop.err.sys.seq-value-exceed-limit，参数 seqValue/pattern/count）。未复用 ERR_SYS_CHAR_COUNT_EXCEED_LIMIT：其语义是"配置宽度超 MAX_COUNT 上限"（参数只有 count），此处是"序号值超出配置宽度"，复用会误导排障；模块 i18n 资源（nop-sys-meta _vfs/i18n）仅含 entity/prop label、不含错误码条目，与既有错误码一致以 define() 内联描述为准，无需 i18n 同步。红验证：TestDefaultCodeRule#testSeqExceedingWidthThrowsInsteadOfTruncating（行为红：临时回退抛错逻辑后 "Expected NopException to be thrown, but nothing was thrown"，即静默返回 "D23"；初版为编译级红——新错误码未定义）。模块测试 dao 52 run/0 fail/0 skip、service 15 run/0 fail/0 skip。

### [P2] 持久订阅的 suspend/resume 完全失效，暂停期间消息照常投递

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:{327-345, 443-455, 530-549}`
- **维度**: D8（契约）、D1
- **证据**:
```java
// 分发路径直接遍历 durableSubscriptions，不检查 suspended
for (SubscriptionState subscription : subscriptions) {
    Object ret = invokeConsumer(subscription.consumer, event.getEventTopic(), ...);
}
...
// 挂起只作用于 localService 的内存订阅
@Override
public void suspend() {
    delegate.suspend();
}
```
- **现状**: `subscribe()` 返回的 `DurableSubscription` 的 `suspend()/resume()` 仅委托给内部 `LocalMessageService` 的订阅对象；但持久消息的实际投递路径 `dispatchBroadcastToSubscribers`（广播）与 `invokeDurableConsumers`（队列）直接遍历 `durableSubscriptions` 调用 `consumer.onMessage`，从不读取 suspended 状态。而 `LocalMessageService.invokeMessageListener`（唯一检查 suspended 的地方）因 `localService.send` 被重定向到 DB 持久化而永远不会被触发。
- **风险**: 调用方按 `IMessageSubscription` 契约挂起订阅（如维护窗口、消费者降级）后消息仍持续投递，违反接口语义且无任何告警。
- **建议**: 在 `SubscriptionState` 上增加 volatile suspended 标志，`dispatchBroadcastToSubscribers`/`invokeDurableConsumers` 投递前检查；或文档化持久订阅不支持挂起并在 `suspend()` 中抛出 UnsupportedOperationException。
- **误报排除**: 已 grep 全模块 `isSuspended|suspend`（仅 DurableSubscription 委托处出现）；已读 `LocalMessageService` 全文确认 `invokeMessageListener` 只被其 `send` 调用，而匿名子类重写了 `send` 路由回 DB；确认 durable 分发链路无任何挂起检查。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. SubscriptionState 增加 volatile suspended（多线程轮询分发可见性）；广播分发 dispatchBroadcastToSubscribers 跳过挂起订阅者；队列分发 invokeDurableConsumers 投递首个未挂起订阅者、全部挂起时返回 ConsumeLater 使事件回 WAITING（handleResult 按 minProcessDelay 重新调度），resume 后重投——不再按无消费者丢弃成 PROCESSED、也不走异常重试耗尽成 FAILED；DurableSubscription.suspend/resume/isSuspended 同时维护 state 与 delegate。红验证：TestSysDaoMessageService#testSuspendedDurableSubscriptionHoldsNonBroadcastEvent（"suspended subscription must not receive messages ==> expected: <[]> but was: <[evt-1]>"）、#testSuspendedBroadcastSubscriberIsSkipped（"expected: <[]> but was: <[evt-1]>"）。模块测试 dao 52/0/0、service 15/0/0。

### [P2] 审计日志拦截器在无上下文线程写审计实体时 NPE

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java:{101-107}`
- **维度**: D1（NPE）、D4
- **证据**:
```java
changeLog.setBizObjName(entityModel.getShortName());
changeLog.setObjId(entity.orm_idString());
changeLog.setChangeTime(CoreMetrics.currentTimestamp());
IContext context = ContextProvider.currentContext();
String appId = context.getDynAppId();   // context 可能为 null
```
- **现状**: `BaseContextProvider.currentContext()` 直接返回 `ThreadLocal.get()`，未绑定上下文的线程返回 null。本模块的消息消费分发链路（`SysDaoMessageService` 轮询线程 → `NonBroadcastEventProcessor.process` → `invokeDurableConsumers` → `consumer.onMessage`）全程不建立 IContext，也不存在任何 runWithContext 包装（已 grep 验证）。
- **风险**: 消费者或后台定时任务在线程内保存/更新带 `tagSet="audit"` 的实体（仓库内 nop-credential 的 `nop_credential`/`nop_credential_auth` 即启用该 tagSet；拦截器默认开启，见 app-dao.beans.xml 的 feature:on）时，flush 触发 `postSave/postUpdate` → NPE，消费失败进入重试→FAILED 循环。
- **建议**: `context == null` 时回退默认值（appId 取 `AppConfig.appName()`、operatorId 置空或 "system"），或在分发链路建立上下文。
- **误报排除**: 已读 `ContextProvider`/`BaseContextProvider`（默认实例 `new BaseContextProvider()`，无兜底上下文）；已确认 nop-sys 消息分发链路无上下文建立；已确认仓库内确有实体使用 audit tagSet（nop-credential.orm.xml）。当前仓库内 credential 写入均发生在登录请求线程（有上下文），故降为 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. initChangeLog 对 currentContext()==null 分支处理：appId 回退 AppConfig.appName()、operationName 回退默认操作名、operatorId 回退 "system"（报告两个选项中取 "system"，因 OPERATOR_ID 列 mandatory=true，置空实测触发 nop.err.orm.mandatory-prop-is-null 使审计写入整体失败）。连带修复（同为审计写入健壮性）：写入改经 ormDao.getOrmTemplate().runInNewSession(session::saveDirectly) 独立新会话落库——原 dao.saveEntity 在外层会话 flush 回调内触发时 INSERT 随会话收尾被丢弃（实测 internalSave 已入队但无 INSERT 执行），而 dao.saveEntityDirectly 复用环境会话又与进行中的 flush 重入、同一动作重复入队导致 duplicate-key。红验证：TestOrmEntityChangeLogInterceptor#testInitChangeLogToleratesContextlessThread（"NPE: Cannot invoke IContext.getDynAppId() because \"context\" is null"，与审计证据逐字吻合）+ #testAuditUpdateOnContextlessThreadWritesChangeLog 集成路径（无上下文线程更新审计实体、审计行落库）。模块测试 dao 52/0/0、service 15/0/0。

### [P2] nop_sys_event 表 WAITING/FAILED 事件永无清理路径，无订阅者主题事件无限累积

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:{207-224}`；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/NonBroadcastEventProcessor.java:{92-99, 181-194}`
- **维度**: D6（资源）、D1
- **证据**:
```java
// 清理只删 PROCESSED
eventQuery.addFilter(FilterBeans.eq(NopSysEvent.PROP_NAME_eventStatus,
        NopSysDaoConstants.SYS_EVENT_STATUS_PROCESSED));
eventQuery.addFilter(FilterBeans.lt(NopSysEvent.PROP_NAME_eventTime, expireBefore));
dao().deleteByQuery(eventQuery);
...
// 扫描只扫已订阅 topic：发往从未被订阅 topic 的事件永远 WAITING
TreeBean filter = FilterBeans.and(
        FilterBeans.in(NopSysEvent.PROP_NAME_eventTopic, topics), ...);
...
if (effectiveDelay < 0) {
    event.setEventStatus(NopSysDaoConstants.SYS_EVENT_STATUS_FAILED);   // FAILED 也无清理
}
```
- **现状**: `cleanupExpiredEvents` 仅删除 `PROCESSED` 且超保留期的行。两类行永久滞留: (1) 发送到没有任何节点订阅的 topic 的事件（生产者配置漂移、订阅者永久下线）保持 WAITING，`fetchCandidates` 的 topic-in 过滤使其永不被扫描也永不被清理；(2) 重试耗尽（默认 RetryPolicy maxRetryCount=2）后置为 FAILED 的行。
- **风险**: 事件表随时间无界增长，而广播/非广播轮询查询在 eventTime/eventTopic 上扫描的基数持续膨胀——代码注释自述"不清理会随时间线性膨胀使消费延迟持续恶化"，但当前实现只封住了 PROCESSED 一条通道。
- **建议**: 清理条件改为"PROCESSED 或 FAILED 且超保留期"，另对超长滞留（如 eventTime 早于 N 倍保留期）的 WAITING 行告警或按配置清除。
- **误报排除**: 已 grep 全模块 `NopSysEvent` 引用与 deploy 脚本确认无其他清理路径；已读 `RetryPolicy` 默认值（maxRetryCount=2，重试 3 次后 delay 返回机制导致 FAILED）；已确认 `fetchCandidates` 仅查询 `topicsProvider.get()` 内的 topic。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. cleanupExpiredEvents 清理条件扩展为 PROCESSED/FAILED 超保留期；另增 WAITING/CLAIMED 超长滞留清理（覆盖发往无订阅者 topic 的 WAITING 行与被接管后遗弃的 CLAIMED 行），新配置 nop.sys.event.cleanup-stale-waiting-days（默认30天，0=禁用），@InjectValue 写法与同文件 cleanup-retention-days 一致。红验证：TestSysEventCleanup#testCleanupExpiredEventsCoversFailedAndStaleWaiting（"only the fresh WAITING event should survive cleanup ==> expected: <1> but was: <3>"，即 FAILED-旧与 WAITING-超滞留行均存活）+ #testFailedEventWithinRetentionIsKept（保留期内 FAILED 不误删，回归钉）。模块测试 dao 52/0/0、service 15/0/0。

### [P2] SysCompactExtFieldHelper 配置缓存只在启动时加载，管理端修改永不生效

- **文件**: `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/impl/SysCompactExtFieldHelper.java:{27-31}`；`nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/entity/NopSysCompactExtFieldBizModel.java:{11-15}`
- **维度**: D1（错误默认值）、D8
- **证据**:
```java
@PostConstruct
public void init() {
    refreshCache();
}

public void refreshCache() {
    ... // 全量重建 entityFieldPositions / entityFieldConfigs
}
```
```java
// NopSysCompactExtFieldBizModel：无任何 afterEntityChange 失效钩子
@BizModel("NopSysCompactExtField")
public class NopSysCompactExtFieldBizModel extends CrudBizModel<NopSysCompactExtField> implements INopSysCompactExtFieldBiz {
    public NopSysCompactExtFieldBizModel(){
        setEntityName(NopSysCompactExtField.class.getName());
    }
}
```
- **现状**: `refreshCache()` 全仓库仅 `@PostConstruct init()` 一处调用（已 grep）。NopSysCompactExtField 提供完整 CRUD BizModel/API 供管理端调整 position/defaultValue，但修改后 helper 的两份内存映射直到应用重启都返回旧配置；同模块的 `NopSysCodeRuleBizModel`/`NopSysSequenceBizModel` 均实现了 `afterEntityChange` 失效钩子，唯独此处缺失。
- **风险**: 管理端新增/调整紧凑扩展字段映射后，`getExtValue/getExtValues` 按旧 position 解析 extFlags 字符串，读到其他字段的值——属于静默的数据错读而非报错，且多节点间行为不一致（重启先后不同）。
- **建议**: 在 NopSysCompactExtFieldBizModel 覆盖 `afterEntityChange` 调用 `helper.refreshCache()`；多节点场景配合广播事件或短 TTL 兜底。
- **误报排除**: 已 grep `refreshCache|SysCompactExtFieldHelper` 全模块确认无第二调用点；已读 app-service.beans.xml 确认 helper 为单例 bean；已对照 CodeRule/Sequence 两个 BizModel 的失效钩子写法确认平台具备该机制且此处未使用。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. NopSysCompactExtFieldBizModel 覆盖 afterEntityChange 调用 compactExtFieldHelper.refreshCache()（注入写法与 NopSysCodeRuleBizModel/NopSysSequenceBizModel 同式，@Inject @Nullable 防测试环境缺 bean）。多节点场景（其他节点的管理端修改）本节点仍无失效传播——与 CodeRule 缓存已补的 TTL 兜底不对等，留 follow-up（见收尾建议）。红验证：TestSysConfigCacheInvalidation#testCompactExtFieldConfigChangeRefreshesHelperCache（编译级红：compactExtFieldHelper 字段原不存在，javac 找不到符号）。模块测试 dao 52/0/0、service 15/0/0。

### [P3] SysDaoResourceLockManager 三处 catch 块中重复输出无条件 LOG.trace（含全栈）

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:{125-134, 145-154, 185-194}`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    if (isDuplicateKeyError(e)) {
        LOG.trace("nop.lock.sys.lock-held-by-other:resourceId={}", resourceId);
    } else {
        LOG.warn("nop.lock.sys.save-lock-failed:resourceId={}", resourceId, e);
    }
    LOG.trace("nop.lock.sys.save-lock-failed:resourceId={}", resourceId, e);  // 无条件重复
}
```
- **现状**: warn/trace 分支结束后又无条件执行一次 `LOG.trace(..., e)`，三处相同（首次尝试、循环内尝试、删除后重试）。看起来是新增分级日志时遗留的旧语句。
- **风险**: trace 级别开启时每次争用输出两条含全栈的日志，干扰排查；无功能危害。
- **建议**: 删除三处无条件的 `LOG.trace("nop.lock.sys.save-lock-failed"...)` 尾行。
- **误报排除**: 已读完整方法确认三处结构完全一致且该行不在 if/else 内。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 删除三处无条件的 `LOG.trace("nop.lock.sys.save-lock-failed"..., e)` 尾行，分级逻辑收敛到提取的 logSaveLockFailure（dup-key→trace、其他→warn，语义不变）。免红：纯日志语句删除，无行为语义变化，无法构造有意义的红测试。模块测试 dao 52/0/0、service 15/0/0。

### [P3] 锁等待循环中"锁行已删除但重插失败（非重复键错误）"路径无退避

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:{180-196}`
- **维度**: D6
- **证据**:
```java
} else {
    // 如果数据库记录已经被删除，则重试锁定
    try {
        entity = saveNew(...);
        return new EntityResourceLockState(entity);
    } catch (Exception e) {
        ... // 仅记日志，无 sleep
    }
}
} while (true);
```
- **现状**: `entity != null`（他人持有）路径有 `Thread.sleep(100)` 退避；`entity == null`（已删除）路径的 saveNew 失败（非 dup-key 的持续错误，如约束/权限类快速失败）后直接进入下一轮，每轮含 2 次 saveNew + 1 次查询共 3 次 DB 访问，在 waitTime 窗口内空转。
- **风险**: 有界（leftWait 耗尽即返回 null），但大 waitTime 配置下对 DB 产生无意义压力；与互斥正确性无关（DB 整体故障时 runInNewSession 查询会抛出终止循环）。
- **建议**: 该分支失败后同样 sleep 一个小间隔再进入下一轮。
- **误报排除**: 已核对循环全路径：dup-key 常态路径经 runInNewSession 读到他人持有的行后 sleep；仅"行被删 + 写持续快速失败 + 读持续成功"这一不对称故障组合触发空转，故定级 P3。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. else 分支重插失败后同样退避（提取 backoffBeforeRetry 100ms，与"他人持有"路径共用）。红验证：TestSysDaoResourceLockManager#testDeletedRowRetryFailureBacksOffInsteadOfSpinning（"persistent save failure must back off between retries instead of spinning on the DB, attempts=667"，修复后约 20 次）。附注：测试以 saveNew 尝试次数而非墙钟耗时断言——autotest 的 TestClock 会使 CoreMetrics 虚拟时间快于墙钟（同毫秒内每次调用 +1），CoreMetrics 计时的 wait 循环会提前退出，墙钟断言不可靠。模块测试 dao 52/0/0、service 15/0/0。

### [P3] postUpdate 审计记录未跳过 version 列，与 postSave 行为不一致

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java:{33-36, 61-68}`
- **维度**: D1（一致性）、D6
- **证据**:
```java
// postSave 跳过版本列
int propId = col.getPropId();
if (propId == entityModel.getVersionPropId())
    return;
...
// postUpdate 无此判断
entity.orm_forEachDirtyProp((value, propId) -> {
    NopSysChangeLog log = changeLog.cloneInstance();
    IColumnModel col = entityModel.getColumnByPropId(propId, false);
    log.setPropName(col.getName());
```
- **现状**: 每次乐观锁更新 version 必为脏属性，postUpdate 会为每次实体更新额外写一条 `propName=version` 的变更记录。
- **风险**: 审计日志噪声与存储膨胀（每次更新多一行）；无数据错误。
- **建议**: postUpdate 中同样跳过 `propId == entityModel.getVersionPropId()`。
- **误报排除**: 已确认 `orm_forEachDirtyProp` 遍历 oldValues 全部脏属性、无列类型过滤；已核对 `getColumnByPropId(propId,false)` 为"未知即抛异常"语义（不存在 null NPE，另行排除）。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（保留防御性加固）. 实测撤销跳过逻辑后标准更新路径仍无 version 审计行（TestOrmEntityChangeLogInterceptor#testPostUpdateAuditSkipsVersionColumn 仍绿）：乐观锁递增由 SQL 表达式 `VERSION=VERSION+1` 在 queueUpdate 回调 incOptimisticLockVersion 中完成，不写入 oldValue 脏集，`orm_forEachDirtyProp` 不遍历到 version——审计所述"每次更新多写一条 version 记录"在标准 dao.updateEntity 路径不可达。仍保留与 postSave 对称的 `propId == versionPropId` 跳过（若 ORM 内部改为脏跟踪版本列，或业务代码显式置脏 version，不引入噪声行），测试作为回归钉。红验证：无法单独构造红（前提不可达）；该测试初版红随 P2-2 的 flush 丢写入问题一并出现（"got logs: []"）。模块测试 dao 52/0/0、service 15/0/0。

### [P3] postSave 使用 `audit-save` 标签而 postUpdate/postDelete 使用 `audit`，同一 tagSet 的实体只记改不记增

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java:{23, 51, 74}`
- **维度**: D8（一致性，存疑设计）
- **证据**:
```java
// postSave
if (!entityModel.containsTag(OrmModelConstants.TAG_AUDIT_SAVE))   // "audit-save"
    return;
...
// postUpdate / postDelete
if (!entityModel.containsTag(OrmModelConstants.TAG_AUDIT))        // "audit"
    return;
```
- **现状**: 仓库内实际使用的标签只有 `tagSet="audit"`（nop-credential 等），没有任何模型带 `audit-save`。这些实体的新增操作不产生变更日志，更新/删除产生。
- **风险**: 若非有意设计（"audit"=只记变更），使用方按直觉配置 `audit` 后创建行为缺失审计；若有意设计，缺少文档区分。
- **建议**: 明确语义并在文档/模型校验中声明；若期望创建也留痕，postSave 应同样接受 `audit`。
- **误报排除**: 已 grep 全仓库 orm.xml 确认 `audit-save` 无任何模型使用、`audit` 被 nop-credential 使用；无法从代码判定意图，故按"标签语义不一致"降为 P3 存疑项。

> **处置（fix-ai-check 分支，2026-08-28）**: 裁定暂缓. 决策点：确认标签语义（"audit"=只记改+删，还是也涵盖新增）。若按直觉语义（audit 涵盖新增），postSave 改 `containsTag(TAG_AUDIT_SAVE) || containsTag(TAG_AUDIT)` 即可，但会改变现有 tagSet="audit" 实体（仓库内 nop-credential 的 nop_credential/nop_credential_auth 两表）新增操作的审计行为与存储量，属用户可见行为变更，需产品决策并在 docs-for-ai（audit-field-changes.md）与 orm 模型校验中声明，超出单条修复范围。已复核仓库现状（audit-save 仍零使用）。免红：未实施行为变更。模块测试 dao 52/0/0、service 15/0/0。

### [P3] 取消订阅后空 topic 残留：无谓轮询持续，且无订阅者的事件被认领后直接标记 PROCESSED 丢弃

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:{481-493, 521-528, 443-448}`
- **维度**: D6、D1
- **证据**:
```java
IMessageSubscription subscription = localService.subscribe(topic, listener, options);
return new DurableSubscription(subscription, state);
...
@Override
public void cancel() {
    delegate.cancel();                       // LocalMessageService 只 remove 元素，list 仍留在 map
    List<SubscriptionState> subscriptions = durableSubscriptions.get(state.topic);
    if (subscriptions != null) subscriptions.remove(state);
}
```
- **现状**: `LocalMessageService.consumers` 的 keySet（topic 列表）在最后一个订阅者取消后仍保留空列表；`getBroadcastTopics/getNonBroadcastTopics` 基于 keySet，轮询查询继续包含该 topic。广播事件照常查出但因 durableSubscriptions 为空仅打 debug 日志；非广播事件被 claim 后经 `invokeDurableConsumers` 返回 null 而 `handleResult` 置为 PROCESSED——即事件被"消费"丢弃。
- **风险**: 订阅反复增删时空 topic 累积，轮询 IN 列表膨胀；订阅者取消期间到达的消息被静默丢弃（而非保留待新订阅者）。
- **建议**: `DurableSubscription.cancel` 时若列表已空则从 localService.consumers 移除 key（或 LocalMessageService.removeConsumer 后做空列表清理）；对无消费者的非广播事件考虑保留 WAITING 或记告警。
- **误报排除**: 已读 `LocalMessageService.removeConsumer/getBroadcastTopics` 确认空列表残留行为；已核对 `handleResult` 对 null 返回值置 PROCSESSED 的路径。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. DurableSubscription.cancel 在最后一个订阅者移除后同步清理 durableSubscriptions 与 localService.getConsumers() 的空 topic 键（ConcurrentHashMap.remove(key, emptyList) 语义：仅当映射为空列表时移除，避免误删并发新订阅者的非空列表）；invokeDurableConsumers 无消费者分支 debug→warn（该路径事件将被置 PROCCESSED 丢弃，必须可见；修复空 topic 清理后此情形仅剩取消/订阅竞态瞬态）。红验证：TestSysDaoMessageService#testCancelLastSubscriptionRemovesTopicFromPollSet（"empty topic must be removed from the polling set after the last subscriber cancels ==> expected: <false> but was: <true>"，广播/非广播双断言）。warn 升级免红（日志级别变更）。模块测试 dao 52/0/0、service 15/0/0。

### [P3] 死代码: `claimNonBroadcastEvents`/`processClaimedNonBroadcastEvent` 无任何调用方，且自带 @Transactional 自调用陷阱；`SysDaoLeaderElector.restartElection` 的 DB 版本同样无人调用

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:{269-273, 300-302}`；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/elector/SysDaoLeaderElector.java:{219-238}`
- **维度**: D8、D4
- **证据**:
```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public List<NopSysEvent> claimNonBroadcastEvents(List<NopSysEvent> events) {
    ensureNonBroadcastProcessor();
    return nonBroadcastProcessor.claim(events);
}
...
public void restartElection() {          // 无调用方（catch 块调用的是 onRestartElection，仅重置 promise）
    IEntityDao<NopSysClusterLeader> dao = dao();
    for (int i = 0; i < 10; i++) { ... dao.updateEntityDirectly(leader); ... }
}
```
- **现状**: 全仓库 grep 确认 `claimNonBroadcastEvents`、`processClaimedNonBroadcastEvent`、`ILeaderElector.restartElection`（两个实现 SysDaoLeaderElector/JdbcLeaderElector 均是）在 main 源码零调用。`claimNonBroadcastEvents` 若未来被同类内部调用将静默绕过 REQUIRES_NEW 事务代理。
- **风险**: 无直接运行时危害；维护陷阱与契约漂移（接口承诺的强制重选举能力实际不可达）。审计过程中一度怀疑 `restartElection` 在 catch 中被调用会导致选举循环停摆，经核对 `onRestartElection()`（AbstractLeaderElector，仅重置 promise，不触 DB）与 `restartElection()`（DB 版）是两个方法、catch 块调用的是前者，选举循环对 DB 异常是健壮的——该 P1 候选已排除。
- **建议**: 删除或在方法注释中标明预留用途；restartElection 若保留应通过集群管理 API 暴露。
- **误报排除**: 已 grep 全仓库（含 nop-stream、nop-quarkus）确认零调用；已读 AbstractPollingLeaderElector/AbstractLeaderElector 确认调度链与回调异常安全性。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题（主会话跨模块验证后修正）. claimNonBroadcastEvents/processClaimedNonBroadcastEvent 并非死代码——`nop-batch-sys/src/main/resources/_vfs/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml` 以 XLang 表达式 `svc!.claimNonBroadcastEvents(items)`/`svc!.processClaimedNonBroadcastEvent(item)` 调用（Java 向 grep 漏掉 DSL 调用点，本条审计前提与处置子代理复核均漏检）。本批曾按死代码删除，主会话全 reactor 验证暴露 nop-batch-sys 5 测试 NopEvalException(`nop.err.xlang.exec.no-obj-method funcName=claimNonBroadcastEvents`) 后恢复两方法（含 @Transactional REQUIRES_NEW——batch.xml 经注入的 AOP 代理调用，事务语义生效，原"自调用陷阱"前提在真实调用路径上不成立）；恢复处已加注释标明 DSL 调用方。红验证：TestSysEventBatchTrigger×2 + TestSysEventBatchPartitionE2E×3 在删除态 error（no-obj-method），恢复后转绿——即本条的现实回归钉定。restartElection 部分维持复查非问题：系 ILeaderElector 接口（nop-cluster-core）契约方法，TestDaoLeaderElector#testElection 与 TestJobCoordinatorWithSysDaoLeaderElector#testPlatformIntegrationContractObserved（4bc54bd9eb/cf3d210626 引入，先于本审计）演练生产 failover 路径，非死代码；是否经集群管理 API 暴露属接口设计决策（改动涉 nop-cluster 模块，超出本条范围）。模块测试 dao/service + nop-batch-sys 全绿（见 08-28 主会话验证）。

### [P3] SysSequenceGenerator 生产代码依赖单元测试基础设施 BaseTestCase；ERR_SYS_NO_SEQ 错误码定义后从未使用

- **文件**: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java:{22, 141-149}`；`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/NopSysErrors.java:{20-22}`
- **维度**: D7、D8
- **证据**:
```java
import io.nop.core.unittest.BaseTestCase;   // 生产代码引用 unittest 包
...
public void lazyInit() {
    if (!CFG_SYS_INIT_DEFAULT_SEQUENCE.get()) return;
    if (BaseTestCase.isTestRunning()) {
        BaseTestCase.addLazyAction(this::addDefaultSequence);
    } else {
        addDefaultSequence();
    }
}
```
- **现状**: `io.nop.core.unittest.BaseTestCase` 进入 main 依赖链；`ERR_SYS_NO_SEQ`（"在序列号表中缺少对应记录"）无引用，序列行被并发删除时 `session.load` 抛出的是通用 ORM 错误而非该语义化错误码。
- **风险**: 测试框架类随生产构件分发并出现在调用栈；排障时缺少预期错误码。无直接运行时错误。
- **建议**: 用配置开关替代 `BaseTestCase.isTestRunning()` 分支；删除或在 `syncFromDb` 捕获转译时启用 ERR_SYS_NO_SEQ。
- **误报排除**: 已 grep `ERR_SYS_NO_SEQ` 全仓库仅定义处一条；已核对 `lazyInit` 作为 beans.xml 的 `ioc:delay-method` 在生产启动路径执行。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（ERR_SYS_NO_SEQ）+ 裁定不修复（BaseTestCase 分支）. ERR_SYS_NO_SEQ：syncFromDb 捕获 RuntimeException 后复查序列行确实缺失（loadCacheItemFromDb==null）时转译为该语义化错误码，其余异常原样抛出——并发删除序列行时排障可见"在序列号表中缺少对应记录"而非通用 ORM 错误。红验证：TestSysSequenceGenerator#testSyncFromDbThrowsSemanticErrorWhenSeqRowDeleted（"missing sequence row must surface the semantic no-seq error code ==> expected: <nop.err.sys.no-seq> but was: <nop.err.orm.lock-entity-fail>"）。BaseTestCase 分支裁定不修复：BaseTestCase 位于 nop-core 的 main 源码（io.nop.core.unittest 包随生产 jar 分发，非 test-scoped 构件，"生产依赖测试构件"的前提不完全成立）；该分支实现平台 per-test 生命周期契约（JunitBaseTestCase: clearLazyActions→initBeans→runLazyActions，保证缺省序列插入发生在容器重启后的受控阶段），配置开关无法复现该语义；且该路径承载 ORM 主键生成（前轮已裁定"缺失序列不抛错只修回退缓存"），改动回归风险大于收益。模块测试 dao 52/0/0、service 15/0/0。

### [P3] SysCompactExtFieldHelper.getExtValues 对未配置的泛化 extN 槽位返回原始空格而非默认值

- **文件**: `nop-sys/nop-sys-service/src/main/java/io/nop/sys/service/impl/SysCompactExtFieldHelper.java:{134-152}`
- **维度**: D1（一致性）
- **证据**:
```java
// 已配置字段：空格回落默认值
value = c == ' ' ? getDefaultValue(entityName, propName) : String.valueOf(c);
...
// 未配置的泛化槽位：空格原样返回
for (int i = 0; i < flags.length(); i++) {
    String extName = "ext" + (i + 1);
    if (!result.containsKey(extName)) {
        result.put(extName, String.valueOf(flags.charAt(i)));   // ' ' 不回落
    }
}
```
- **现状**: 同一个空格字符，经配置映射读取时回落为默认值，走泛化 extN 分支时返回 `" "`。
- **风险**: 调用方对未配置槽位拿到 `" "` 与 `null`（超长时 getDefaultValue 返回 null）两种"空"表示，语义不一致；无数据破坏。
- **建议**: 泛化分支同样对 `' '` 做处理（返回 null 或空串），统一空值语义。
- **误报排除**: 已读 getExtValue/setExtValue/getExtValues/setExtValues 全部四个方法核对空格处理规则；仅此一处不一致。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复. 泛化 extN 槽位对 ' ' 返回 null，与已配置字段"空格回落默认值（未配置时 getDefaultValue 返回 null）"的空值语义统一，消除 ' ' 与 null 两种空值表示并存。红验证：TestSysCompactExtFieldHelper#testUnconfiguredBlankSlotReturnsNull（"blank unconfigured slot must use null instead of ' ' to keep empty-value semantics uniform ==> expected: <null> but was: < >"）。模块测试 dao 52/0/0、service 15/0/0。
