# nop-api-core 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-kernel/nop-api-core
- 文件数: 322（src/main/java，实测；任务描述约 338 含资源文件差异）
- 覆盖范围声明: 对 322 个主代码文件做了全量 grep 扫描（空 catch、bare RuntimeException、printStackTrace、ThreadLocal、synchronized、可变 static、equals/hashCode、@Inject/@Value 等）；深读约 50 个高风险文件：context 包全部 10 个文件、validate 包全部 5 个、exceptions 包核心（NopException/NopRebuildException/NopValidateException/NopEvalException）、beans 核心（ApiMessage/ApiRequest/ApiResponse/ErrorBean/TreeBean/ExtensibleBean/FilterBeans/IntRangeBean/IntRangeSet/PageBean/FieldSelectionBean/WebContentBean/BinaryDataBean）、beans/query（QueryBean/OrderFieldBean）、time 全部、convert（ConvertHelper 全文/SysConverterRegistry）、util（FutureHelper/ResolvedPromise/Guard/ApiStringHelper/ApiHeaders/ApiInvokeHelper/CloneHelper/FreezeHelper/MultiCsvSet/SourceLocation 节选）、json/JSON、config（AppConfig/SimpleConfigProvider）、api（CrudApiPageIterator）。其余文件（annotations、rpc、graphql、message、auth、beans 其余 DTO 子包）为结构浏览 + grep 抽查，未逐行阅读。跨模块仅做了调用点核实（grep 调用方），未审计调用方模块本身。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 11 |
| P3 | 3 |

## 发现列表

### [P0] FutureHelper.thenRun 异步分支仅在失败时执行任务，成功路径回调被跳过

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/FutureHelper.java:428-439`
- **维度**: D1（逻辑错误）、D8（API 契约与语义不符）
- **证据**:
```java
public static <T> T thenRun(T result, Runnable task) {
    if (result instanceof CompletionStage) {
        return (T) ((CompletionStage<?>) result).whenComplete((v, err) -> {
            if (err != null) {
                task.run();
            }
        });
    } else {
        task.run();
    }
    return result;
}
```
- **现状**: 方法语义为"完成后执行 task"，同步分支无条件执行，异步分支却只在 `err != null`（失败）时执行。两个分支行为不对称，成功路径的回调被静默跳过。条件大概率是写反（或应为无条件执行）。
- **风险**: 已有现实调用方放大为数据错误：`nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/CacheEvictActionDecorator.java:40` 用它实现"biz action 完成后清除缓存"。当 biz 方法返回 `CompletionStage`（Nop 异步服务的主流形态）且执行**成功**时，缓存永远不会失效，所有后续读请求拿到修改前的脏数据。异步 action + 缓存驱逐的组合在生产中是现实触发路径。
- **建议**: 异步分支去掉 `if (err != null)`，改为在 `whenComplete` 中无条件执行 task（与同步分支一致）；若确有"仅失败时执行"的需求，应另立方法名（如 `thenRunOnFailure`）。
- **误报排除**: 已核实本仓库内 `FutureHelper.thenRun` 的唯一主代码调用方是 CacheEvictActionDecorator（其余 `thenRun` 命中为 `CompletionStage.thenRun` JDK 方法，无关）；已阅读调用方确认其意图是成功后清缓存，非仅失败时清理。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`FutureHelper.thenRun(T, Runnable)` 异步分支 `whenComplete` 中去掉 `if (err != null)` 条件，改为无条件执行 task（与同步分支一致）；复核确认全仓库唯一主代码调用方 CacheEvictActionDecorator 依赖的正是成功后执行语义，无调用方依赖旧错误行为。测试：`nop-api-core` `TestFutureHelper#testThenRunAsyncSuccess`（修复前异步 future 成功完成后 task 不执行，断言失败）。

### [P1] ApiRequest.cloneInstance 将 properties 复制给了源对象，克隆体丢失全部 properties

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiRequest.java:127-140`
- **维度**: D1（逻辑错误）、D3（对源对象的意外变更）
- **证据**:
```java
public ApiRequest<T> cloneInstance(boolean includeHeaders) {
    ApiRequest<T> ret = new ApiRequest<>();
    ...
    ret.setSelection(selection);
    ret.setData(data);
    if (this.properties != null)
        this.properties = new HashMap<>(this.properties);   // 赋给 this，而非 ret
    return ret;
}
```
- **现状**: 复制 properties 时写成了 `this.properties = ...`，应为 `ret.setProperties(...)`。结果：(1) 克隆体的 `properties` 永远为 null，`getProperty`/`getStringProperty` 等全部返回 null；(2) 源对象被原地替换为自己的浅拷贝，若源对象被并发读取则引入数据竞争。
- **风险**: `ApiRequest` 是全平台 RPC/GraphQL 请求的公共契约对象，任何调用 `cloneInstance` 的下游（含外部基于该契约库开发的代码）在请求携带 properties（分页参数、业务扩展属性）时静默丢失。
- **建议**: 改为 `ret.setProperties(new HashMap<>(this.properties))`。
- **误报排除**: 逐行比对 `ApiResponse.cloneInstance`（正确使用 `ret.setHeaders(...)`）确认这是笔误而非某种约定；grep 确认当前仓库主代码内无 `ApiRequest.cloneInstance` 调用方（属公共 API 潜在缺陷，故未升 P0）。

### [P1] OrderFieldBean.cloneInstance 反转排序方向，克隆查询的 orderBy 全部反向

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/OrderFieldBean.java:72-80`
- **维度**: D1（逻辑错误）、D8（克隆契约不符）
- **证据**:
```java
@Override
public OrderFieldBean cloneInstance() {
    OrderFieldBean ret = new OrderFieldBean();
    ret.setOwner(owner);
    ret.setName(name);
    ret.setDesc(!desc);          // 克隆却取反
    ret.setNullsFirst(nullsFirst);
    return ret;
}
```
- **现状**: `cloneInstance` 把 `desc` 取反（且不取反 `nullsFirst`）。真正的反转语义在另一个方法 `reverse()`（同时取反 desc 与 nullsFirst）中已正确实现，二者明显混淆。`QueryBean.cloneInstance()` 对每个 orderBy 字段调用 `OrderFieldBean::cloneInstance`，因此克隆整个查询会把所有排序方向反转。
- **风险**: 现实放大路径：`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/OrmEntityDao.java:791`（`makeQuery` 在 sourceName 为空时克隆查询）、`OrmQueryBatchLoaderProvider`/`DaoEntityBlockingSource`（每次批量加载克隆查询模板）。带 orderBy 的查询经克隆后返回结果顺序与请求相反，分页取到错误的页。
- **建议**: `ret.setDesc(desc)`；并补充 OrderFieldBean 克隆/反转的单元测试。
- **误报排除**: git 历史核实（commit 9a001bc6a 已修复过 `desc()/asc()` 工厂方法的同族方向错误，但未触及 cloneInstance）；仓库内无任何测试断言克隆取反的"预期行为"；`reverse()` 的存在排除"克隆即反转"的设计意图。

### [P1] ConvertHelper.monthDayToString 日期段输出的是月份值

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:1459-1471`
- **维度**: D1（逻辑错误）
- **证据**:
```java
sb.append('-');
if (monthDay.getDayOfMonth() < 10) {
    sb.append('0');
}
sb.append(monthDay.getMonthValue());   // 应为 getDayOfMonth()
return sb.toString();
```
- **现状**: 输出格式为 `MM-dd`，但 day 段用了 `getMonthValue()`。`MonthDay.of(12, 25)` 序列化为 `"12-12"`；只有 day==month 时碰巧正确。
- **风险**: `ConvertHelper.toString(Object)` 对所有 `MonthDay` 走此路径，且 `toMonthDay` 已注册为系统转换器。`nop-job` 的 `AnnualCalendarSpec`/`AnnualCalendar`（年度日历）使用 MonthDay，序列化产物是错误数据，回读后语义改变（如 12-25 变 12-12）。
- **建议**: 改为 `sb.append(monthDay.getDayOfMonth())`。
- **误报排除**: 全文确认无重载分流；空参判断（`<10` 补零）使用的是 `getDayOfMonth()`，进一步证明取值目标是 day，属复制粘贴错误。

### [P1] ConvertHelper.localDateTimeToMillis 使用 raw offset 忽略夏令时，与反向转换不对称

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:1009-1022`
- **维度**: D1（时区/DST 边界错误）
- **证据**:
```java
public static Long localDateTimeToMillis(LocalDateTime value) {
    ...
    long offset = TimeZone.getDefault().getRawOffset();          // 不含 DST 修正
    return value.toInstant(ZoneOffset.UTC).toEpochMilli() - offset;
}

public static LocalDateTime millisToLocalDateTime(Long value) {
    ...
    return new Timestamp(value).toLocalDateTime();               // 使用含 DST 的实际偏移
}
```
- **现状**: 正向转换用 `getRawOffset()`（不含 DST），反向转换经 `Timestamp.toLocalDateTime()`（使用 JVM 默认时区的实际偏移，含 DST）。在实行夏令时的时区（如 Europe/Berlin），夏季两者相差 1 小时。
- **风险**: DST 时区夏季运行时，`LocalDateTime -> Long -> LocalDateTime` 往返偏移 1 小时；`toLong(LocalDateTime)`、`toTimestamp`、`stringToLocalDateTime(纯数字时间戳)` 等公共转换路径（作业调度、消息事件时间头 `ApiHeaders.getEventTime` 等）都会产生 1 小时的时间错位。固定偏移时区（如 Asia/Shanghai）不受影响。
- **建议**: 正向改用 `value.atZone(TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()`，或反向同样只用 raw offset，保证两个方向对称。
- **误报排除**: 核对 JDK 语义：`Timestamp.toLocalDateTime()` 按 JVM 默认 ZoneId 转换（含 DST），`getRawOffset()` 明确不含 DST，二者不对称成立；代码库中该两方法互为正反向（stringToLocalDateTime 数字分支调用 millisToLocalDateTime）。

### [P1] ContextTaskQueue.enqueue 与 flush 退出存在 check-then-act 竞态，任务可能无限滞留队列

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextTaskQueue.java:113-145`
- **维度**: D3（并发）
- **证据**:
```java
public boolean enqueue(Runnable task) {
    tasks.add(task);                 // (1) 锁外入队
    lock.lock();
    try {
        if (syncing > 0) { queueReady.signalAll(); return true; }
        return false;                // (2) 判定"有人在处理/等待"，调用方将不再 flush
    } finally { lock.unlock(); }
}
// flush(): beginProcess() 后循环 tasks.poll()，poll 到 null 即 break，最后 endProcess()
```
- **现状**: 竞态窗口：处理线程 A 在 `flush` 循环中 `poll()` 返回 null（此刻队列确为空）但尚未 `endProcess()`；线程 B 执行 `tasks.add(task)` 后进入锁，看到 `syncing == 0` 返回 false；`BaseContext.runOnContext` 随即调用的 `flush()` 在 `beginProcess()` 中因 `processingThread` 仍是 A 而直接放弃。A 已跳出循环不会再 poll，任务滞留。
- **风险**: 多线程并发向同一 context 投递回调（`thenOnContext` 的核心场景）时，完成 promise 的任务可能永远不执行：有 `syncGet` 等待者时可被唤醒自愈；fire-and-forget 场景（如响应回调）则永久滞留，表现为请求挂起/回调丢失。窗口窄（poll-null 到 endProcess 之间），但持续高并发下可复现。
- **建议**: 将 `tasks.add(task)` 移入锁内，并在锁内检查 `processingThread == null && syncing == 0` 时返回 false、否则自行处理；或 flush 退出前在锁内二次确认队列为空再清 `processingThread`。
- **误报排除**: 逐行推演 ConcurrentLinkedDeque 与锁的 happens-before：`add` 发生在 A 的最后一次 `poll` 之后时，A 不会重读队列；`enqueue` 返回值语义（"已有处理者"）与 `beginProcess` 判定之间存在 TOCTOU，推演成立，非臆测。

### [P2] ErrorBean.cloneInstance 丢失 forPublic 标志（且 cause/details 为共享浅拷贝）

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ErrorBean.java:64-80`
- **维度**: D1、D8
- **证据**:
```java
public ErrorBean cloneInstance() {
    ErrorBean ret = new ErrorBean();
    ret.setSourceLocation(sourceLocation);
    ... // 复制 status/errorCode/description/params/bizFatal/errorStack/severity/details/resolved
    ret.setCause(cause);
    return ret;
}   // 没有复制 forPublic
```
- **现状**: `forPublic`（"该错误可直接返回给客户端"的标记，`NopRebuildException.rebuild` 会读取）未复制；克隆体永远 `forPublic=false`。另外 `details` 只拷贝外层 Map（值共享）、`cause` 直接共享引用，深浅不一致。
- **风险**: `nop-core/ErrorMessageManager:341`、`DaoTaskStateStore:539` 克隆 ErrorBean 后，forPublic 信息丢失，错误可能被当作内部错误处理，客户端提示行为改变。
- **建议**: 补 `ret.setForPublic(forPublic)`；明确 cause/details 的克隆策略。
- **误报排除**: 通读全文件确认 `forPublic` 字段及其 getter/setter 存在且被 `NopRebuildException.rebuild(ErrorBean)` 消费，克隆方法中确无复制语句。

### [P2] ApiResponse.cloneInstance 丢失 bizFatal 与 tryResponse 字段

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiResponse.java:117-136`
- **维度**: D1、D8
- **证据**:
```java
public ApiResponse<T> cloneInstance(boolean includeHeaders) {
    ApiResponse<T> ret = new ApiResponse<>();
    ...
    ret.setMsgTimeout(msgTimeout);
    ret.setErrors(errors);
    ret.setData(data);
    ret.setWrapper(wrapper);
    return ret;      // 未复制 bizFatal、tryResponse
}
```
- **现状**: `bizFatal`（Boolean，`isBizSuccess`/前端提示语义依赖）与 `tryResponse`（maker-checker tryMethod 结果）未复制。
- **风险**: `GraphQLWebService.buildRestResponse`、`GraphQLResponseHelper`、`GatewayHttpFilter`（第 257/279 行）均以 `response.cloneInstance(false)` 的序列化结果作为 HTTP 响应体，错误响应经克隆后 `bizFatal` 字段从 JSON 中消失（NON_NULL 序列化），前端/网关据此判断的逻辑失效；tryResponse 丢失影响 maker-checker 流程。
- **建议**: 补 `ret.setBizFatal(bizFatal)` 与 `ret.setTryResponse(tryResponse)`。
- **误报排除**: 已核实上述三个调用方均以克隆体作为对外序列化对象（非仅内部裁剪 headers），字段丢失会外显。

### [P2] QueryBean.rightJoin 实际构造的是 leftJoin

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java:492-495`
- **维度**: D1、D8
- **证据**:
```java
public QueryBean rightJoin(String sourceName, String alias, String leftJoinFields, String rightJoinFields) {
    return addJoin(JOIN_TYPE_LEFT_JOIN, sourceName, alias, leftJoinFields, rightJoinFields);
}
```
- **现状**: `ApiConstants` 中定义了 `JOIN_TYPE_RIGHT_JOIN = "rightJoin"`（第 292 行）但此处传的是 `JOIN_TYPE_LEFT_JOIN`，复制粘贴错误。
- **风险**: 调用 `rightJoin(...)` 得到 left join，语义静默反转（错误数据）。当前仓库主代码未发现该方法调用方（元数据层的 `MetaJoinExecutor` 明确抛"right 不支持"而不是降级，侧面说明 right join 语义应显式、不应静默替换），属公共契约 API 的潜伏缺陷。
- **建议**: 改用 `JOIN_TYPE_RIGHT_JOIN`，并确认下游执行器支持该类型（不支持时应显式报错）。
- **误报排除**: 常量已确认存在；`leftJoin`/`innerJoin` 均正确传自身类型，仅 `rightJoin` 错。

### [P2] QueryBean.addJoin 的 dimFields 判断类型不匹配导致分支永不命中

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java:530-541`
- **维度**: D1
- **证据**:
```java
if (Objects.equals(dimFields, leftJoinFields)) {
    join.setDimFields(rightProps);
} else {
    List<QueryJoinConditionBean> joins = ...;  // 总是走这里
}
```
- **现状**: `dimFields` 是 `List<String>`，`leftJoinFields` 是 `String`，`Objects.equals` 恒为 false（leftJoinFields 前面已校验非空非 null），`setDimFields` 分支为死代码。
- **风险**: 按维度字段 join 时无法走 dimFields 简化路径，总是退化为 conditions 列表，语义等价但丢失下游可利用的结构信息；也说明该特性从未生效过。
- **建议**: 比较前先归一化（如 `Objects.equals(ApiStringHelper.join(dimFields, ","), leftJoinFields)`），或直接比较 `leftProps`。
- **误报排除**: 两变量声明类型已核实（`private List<String> dimFields;` 与方法参数 `String leftJoinFields`）。

### [P2] FilterBeans.or 空参数返回 alwaysTrue（空析取语义应为假），且 or 不容忍 null 元素

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FilterBeans.java:326-347`
- **维度**: D1、D8（安全相关契约）
- **证据**:
```java
public static TreeBean or(TreeBean... filters) {
    if (filters.length == 0)
        return alwaysTrue();        // 空析取应返回 alwaysFalse
    ...
    for (...) {
        TreeBean filter = filters[i];
        if (filter.getTagName().equals(FILTER_OP_OR)) {   // filter 为 null 时 NPE
```
- **现状**: 逻辑学上空 OR 为 false、空 AND 为 true；`and(...)` 对空集返回 alwaysTrue 正确，`or(...)` 对空集返回 alwaysTrue 错误。另外 `and` 跳过 null 元素，`or` 遇 null 直接 NPE，两姐妹方法行为不一致。
- **风险**: 调用方以动态列表构造 OR 过滤（如权限条件合并）时，空列表会得到"恒真"过滤器——在数据过滤/权限场景是过滤条件被绕过的方向性错误；混入 null 则抛 NPE。
- **建议**: 空参数返回 `alwaysFalse()`；对 null 元素与 `and` 一致地跳过或显式报错。
- **误报排除**: 仓库内现有调用（如 `inRanges`）都保证非空入参，故当前无直接错误数据路径，属契约级缺陷（未升 P1 的原因）。

### [P2] ContextProvider.registerInstance 契约自相矛盾且静态字段无可见性保证

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextProvider.java:27-40`
- **维度**: D8、D3
- **证据**:
```java
private static IContextProvider _instance = new BaseContextProvider();

public static void registerInstance(IContextProvider instance) {
    if (_instance != null && instance != null)
        throw new NopException(ERR_CONTEXT_PROVIDER_ALREADY_INITIALIZED);
    _instance = instance;
}
```
- **现状**: `_instance` 初始即非 null，因此任何传入非 null 的注册都会抛 ALREADY_INITIALIZED，除非先调用 `registerInstance(null)` 注销——方法没有任何注释说明这一两步用法。同时 `_instance` 非 volatile，运行期注册/读取跨线程无 happens-before。
- **风险**: 下游（如 quarkus/spring 集成层想替换 context provider）按常规直觉调用必抛异常；若先注销再注册，其他线程可能长期读到旧 provider。
- **建议**: 提供 `unregister`/`replaceInstance` 明确两段式 API，或允许首帧覆盖；字段加 volatile。
- **误报排除**: 全仓库 grep 确认主代码无 `ContextProvider.registerInstance` 调用（当前用默认 provider），问题属 API 契约缺陷而非运行故障。

### [P2] thenOnContext0 在 context 已关闭时异常被吞、返回的 promise 永不完成

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextProvider.java:175-186`
- **维度**: D4（异常吞噬/挂起）
- **证据**:
```java
CompletableFuture<T> promise = new CompletableFuture<>();
future.whenComplete((value, err) -> {
    context.execute(() -> {              // context 已 close 时此处抛 ERR_CONTEXT_ALREADY_CLOSED
        FutureHelper.complete(promise, value, err);
    });
});
return promise;
```
- **现状**: `whenComplete` 的返回值（携带 action 抛出的异常）被丢弃。若源 future 在 context 关闭之后才完成，`context.execute` 抛出的 NopException 只进入被丢弃的依赖 stage，`promise` 永不完成，异常也无日志。
- **风险**: 请求超时/中断路径先关闭 context、残留异步操作稍后完成时，依赖该 promise 的链路静默挂起，且无任何错误线索，难以排查。
- **建议**: action 内 try/catch，异常时 `promise.completeExceptionally(e)`（并考虑 log）；或改用 `handle`。
- **误报排除**: 确认 `BaseContext.execute -> checkClosed` 会抛异常、`BaseContext.close()` 无条件把 `closed` 置 true 且不清空待完成 future；`whenComplete` 返回值确未接收。

### [P2] BaseContext.executeBlocking 忽略 ordered 参数且无 worker 线程，实际在调用线程同步执行

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContext.java:295-303`
- **维度**: D8（接口契约与实现不符）
- **证据**:
```java
public <T> CompletionStage<T> executeBlocking(Supplier<?> task, boolean ordered) {
    checkClosed();
    CompletableFuture<T> future = new CompletableFuture<>();
    runOnContext(() -> {
        FutureHelper.completeAfterTask(future, () -> task.get());
    });
    return future;
}
```
- **现状**: `IContext.executeBlocking` 的 javadoc 承诺"ordered=false 时选择 worker 线程执行"，`BaseContext` 实现完全忽略 `ordered`，且 `runOnContext` 在无线程处理时（enqueue 返回 false）直接在当前线程 flush——"blocking" 任务在调用线程上同步运行。
- **风险**: 在使用 BaseContext 的环境（测试、非 vert.x 运行时）中，调用方以为提交到了阻塞线程池，实际阻塞了事件线程/调用线程，可能拖垮吞吐甚至死锁（与 syncGet 组合时）。
- **建议**: 至少在 javadoc 标明 BaseContext 实现是"当前线程同步执行"的降级实现；或 `ordered=false` 时用公共 ForkJoinPool/Executor 执行。
- **误报排除**: `runOnContext -> enqueue` 无处理线程时同步 `flush` 的路径已核实（ContextTaskQueue.flush/BaseContext.runOnContext 231-244 行）。

### [P2] ConvertHelper.toFalsy 的 NaN 判断恒为 false，NaN 不再是假值

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:303-320`
- **维度**: D1
- **证据**:
```java
if (o instanceof Number) {
    double d = ((Number) o).doubleValue();
    return d == 0 || d == Double.NaN;    // NaN 与任何值 == 比较恒为 false
}
```
- **现状**: javadoc 明确"按 JavaScript 规定 false/0/null/\"\"/undefined/NaN 为假值"，但 `d == Double.NaN` 永远为 false（NaN 不等于 NaN），NaN 输入被判定为真值。
- **风险**: 表达式/模板引擎中 `toFalsy(NaN)` 应为 true 却返回 false，条件分支反向。触发需要 NaN 的 Double 值（解析异常数据、0.0/0.0 等），不常见但语义明确错误。
- **建议**: 改为 `d == 0 || Double.isNaN(d)`。
- **误报排除**: IEEE 754 语义确认 `==` 对 NaN 恒 false；javadoc 的意图排除了"故意排除 NaN"的可能。

### [P2] FieldSelectionBean.flattenFields 永远返回空集合（且前缀拼接错误）

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FieldSelectionBean.java:484-499`
- **维度**: D1、D8
- **证据**:
```java
public Set<String> flattenFields() {
    Set<String> ret = new TreeSet<>();
    _flatten(ret, null, this);
    return ret;
}

void _flatten(Set<String> ret, String prefix, FieldSelectionBean subField) {
    if (subField.fields != null) {
        for (Map.Entry<String, FieldSelectionBean> entry : subField.fields.entrySet()) {
            _flatten(ret, prefix == null ? entry.getKey() : prefix + entry.getKey() + ".", entry.getValue());
        }
    }
}   // 全程没有 ret.add(...)
```
- **现状**: `_flatten` 只递归从不向 `ret` 添加元素，`flattenFields()` 对任何 selection 都返回空集。即使补上 add，前缀拼接 `prefix + key + "."` 也少了层级分隔符（"a"+"b"+"." → "ab."，应为 "a.b"）。
- **风险**: 公共 API 完全失效。当前仓库主代码无调用方，属潜伏地雷：一旦被使用，调用方拿到空集合会误判"没有请求任何字段"。
- **建议**: 递归中对每个 entry 执行 `ret.add(prefix == null ? key : prefix + key)`，子级前缀为 `(prefix==null?key:prefix+key) + "."`。
- **误报排除**: 全文检索方法体确认无其他写入 `ret` 的路径；`forEachField`（正确实现）对比确认意图。

### [P2] SysConverterRegistry 用非并发 HashMap 暴露运行时注册/注销 API

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/SysConverterRegistry.java:43-46,102-117`
- **维度**: D3
- **证据**:
```java
private Map<Class<?>, ITypeConverter> converters = new HashMap<>();
private Map<String, TargetTypeConverter> namedConverters = new HashMap<>();

public void removeConverterByType(Class<?> targetClass) { converters.remove(targetClass); }
public void registerConverter(String name, Class<?> targetType, ITypeConverter converter) { ... converters.put(...); }
```
- **现状**: 注册表以普通 HashMap 存储，同时提供 public 的 register/remove/unregister 方法。若在系统运行期（其他线程正在 `getConverterByType` 热路径读取时）注册或注销转换器，存在 HashMap 并发读写风险（JDK8+ 表现为数据错乱/丢条目，极端情况下 resize 竞争可致 CPU 尖刺）。
- **风险**: 动态模块热插拔或运行期扩展转换器时全局转换行为不可预期。
- **建议**: 换成 `ConcurrentHashMap`（字段还声明为非 final，一并修正）。
- **误报排除**: 字段类型与初始化已核实为 `HashMap`；当前主流程仅在启动期注册，故日常路径无并发写，未升 P1。

### [P3] 一组非 final / 非 volatile 的公共静态可变单例与懒初始化字段

- **文件**:
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextProvider.java:27`（`_instance` 非 volatile）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/AppConfig.java:28`（`s_provider` 非 volatile）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/json/JSON.java:23`（`s_provider` 非 volatile）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/CoreMetrics.java:42`（`s_clock` 非 volatile）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/exceptions/NopException.java:36`（`s_errorMessageManager` 非 volatile）
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/IntRangeBean.java:29`、`util/SourceLocation.java:42`、`util/MultiCsvSet.java:26`、`util/ApiStringHelper.java:36-37`（`EMPTY`/`UNKNOWN`/`INVALID_DATE`/`FUTURE_DATE` 等公共静态字段非 final，可被外部改写污染全局）
- **维度**: D3
- **证据**: `private static IContextProvider _instance = new BaseContextProvider();`（无 volatile/final，register 方法运行期可写）
- **现状**: 全平台单例注册点统一采用"非 volatile 静态字段 + 启动期赋值"模式；跨线程注册时读取方可能看到旧值。`INVALID_DATE` 等 public static 非 final 字段任何代码都能重新赋值。
- **风险**: 实际注册多发生在 main 线程启动阶段（线程启动自带 happens-before），日常风险低；但热替换/运行期注册场景存在可见性延迟，公共可变静态常量存在被误用污染的全局风险。
- **建议**: 注册字段加 volatile；常量类字段加 final。
- **误报排除**: 逐一打开文件核实字段声明；确认这些字段均有运行期赋值方法（register/更新接口）或为 public 非 final。

### [P3] IntRangeSet 对空集合调用 compact/getFirstBegin/getLastEnd 抛数组越界

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/IntRangeSet.java:69-128`
- **维度**: D1（边界条件）
- **证据**:
```java
public IntRangeSet compact() {
    if (size() == 1) return this;
    IntRangeBean[] array = toSortedArray();
    IntRangeBean prev = array[0];     // size()==0 时 AIOOBE
```
- **现状**: `compact()` 只短路 size==1，空集合时 `array[0]` 抛 ArrayIndexOutOfBoundsException；`getFirstBegin()/getLastEnd()` 同样无空保护。
- **风险**: 空区间集合（合法构造：`rangeSet(Collections.emptyList())`）触发即抛未分类异常。属边界防御缺失，正常业务路径多为非空。
- **建议**: size()==0 时直接返回 this/默认值。
- **误报排除**: `rangeSet(emptyList,false)` 构造合法（Guard.notNull 仅查 null）；`split` 的 `total<=n` 分支对空 ranges 也返回空列表（`new ArrayList<>(0)`），确认 compact 是唯一未防护入口。

### [P3] ApiMessage.getHeaders() 懒初始化 TreeMap 非线程安全

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiMessage.java:27-32`
- **维度**: D3
- **证据**:
```java
public Map<String, Object> getHeaders() {
    if (headers == null)
        headers = new TreeMap<>();
    return headers;
}
```
- **现状**: `ApiRequest`/`ApiResponse` 常被跨线程传递（异步 RPC 回调、序列化线程），两个线程同时首次调用 `getHeaders()` 会各建一个 TreeMap（其一的写入丢失），后续并发 `setHeader` 直接并发写 TreeMap 可能损坏结构。
- **风险**: 消息跨线程共享且首读并发时 header 丢失或 Map 损坏；多数用法单线程构造后共享只读，实际触发面窄。
- **建议**: 构造时初始化，或改用 ConcurrentHashMap + 复合赋值。
- **误报排除**: 确认 `setHeader`/`addHeaders` 均经此懒初始化路径或直接操作同一非并发字段。

## 维度小结

- D1 正确性: P0-thenRun、P1 四条（clone×2、MonthDay、DST）、P2 若干（falsy/flatten/rightJoin/dimFields/clone 丢字段）
- D2 资源管理: 未发现流泄漏（模块仅持有引用，`WebContentBean.autoCleanResource` 语义交给消费方）
- D3 并发: ContextTaskQueue 竞态（P1）、SysConverterRegistry（P2）、静态单例可见性合集（P3）、getHeaders 懒初始化（P3）
- D4 错误处理: thenOnContext0 异常吞噬+挂起（P2）。模块内无空 catch（唯一命中位于注释代码）、无 bare RuntimeException、无 printStackTrace
- D5 安全: 未发现 api-core 主动泄漏敏感信息；`FilterBeans.or` 空集恒真属过滤语义方向性风险（P2 中说明）
- D6 性能: 未发现热路径 O(n²) 或重复计算问题（FieldSelectionPrinter/renderTemplate 实现线性）
- D7 平台规范: 模块内无 `@Inject`/`@Value`/`@Component` 等注解、无 beans.xml、无 `_` 前缀生成文件，属纯契约模块，无违背
- D8 契约一致性: registerInstance 矛盾契约、executeBlocking 忽略参数、clone 系列丢字段/反转、or 空集语义
