# nop-api-core 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-kernel/nop-api-core
- 文件数: 322（src/main/java，共约 24696 行）
- 覆盖范围声明: 深读约 60 个实现类（按风险优先级：FutureHelper / ResolvedPromise / ContextTaskQueue / BaseContext / ContextProvider / BaseContextProvider / DelegateContext / TenantProxyContext / CallExpireTimeProxyContext / ConvertHelper / SysConverterRegistry / ApiStringHelper / QueryBean 及全部 query 子 Bean / TreeBean / FilterBeans / FieldSelectionBean / FieldSelectionPrinter / ApiHeaders / NopException / NopWrapException / NopRebuildException / NopEvalException / SourceLocation / Guard / CloneHelper / FreezeHelper / CoreMetrics / TimeOut / StaticBeanContainer / AppConfig / AbstractConfigProvider / PlaceholderConfigReference / DefaultConfigReference / AbstractConfigReference / SimpleConfigProvider / CastTypeConfigReference / ApiMessage / ApiResponse / ApiRequest / ErrorBean / PageBean / DictBean / DictOptionBean / IntRangeBean / IntRangeSet / LongRangeBean / MultiCsvSet / CrudApiPageIterator / CrudApiItemIterator / PointBean / RectangleBean / JSON / JsonParseOptions / GraphBean / TreeResultBean / ExtensibleBean / ApiInvokeHelper / StaticValue / ApiConfigs / TaskStatusBean / AuditRequest / ITreeBean / message 包等），并对其余全部 322 个文件执行了 D2-D7 反模式全文扫描（IO/流/连接、@Inject private、Spring 依赖、SimpleDateFormat、Random、bare RuntimeException、printStackTrace、非 final 静态可变字段、exec/反序列化等），命中项均逐一回读上下文验证。未逐行深读的约 250 个文件为注解定义（annotations 包约 150 个）、简单数据 Bean 与接口（oauth/std/file/task/graphql 子包），对这些文件做了抽样深读（GraphQLResponseBean/GraphQLConnectionInput/CheckResultBean/TaskStatusBean/FileStatusBean/AuditRequest/MessageSubscribeOptions/MultiMessageSubscription 等）与全量 grep 扫描。本模块 src/main/java 无任何流/连接/进程执行代码（D2 无适用目标），无 IoC/Spring 违规（D7 无命中）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 9 |
| P3 | 14 |

## 发现列表

### [P1] QuerySourceBean.cloneInstance() 丢失 join conditions，QueryBean 克隆后连接条件被静默丢弃

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QuerySourceBean.java:33`
- **维度**: D1
- **证据**:
```java
public class QuerySourceBean implements ICloneable {
    private String sourceName;
    private String alias;
    private TreeBean filter;
    private List<String> dimFields;
    private String joinType;
    private List<QueryJoinConditionBean> conditions;

    @Override
    public QuerySourceBean cloneInstance() {
        QuerySourceBean bean = new QuerySourceBean();
        bean.setSourceName(sourceName);
        bean.setAlias(alias);
        bean.setJoinType(joinType);
        if (filter != null)
            bean.setFilter(filter.cloneInstance());
        if (dimFields != null)
            bean.setDimFields(new ArrayList<>(dimFields));
        return bean;   // conditions 字段未复制
    }
```
- **现状**: `QueryBean.addJoin(...)` 在 dimFields 不匹配时通过 `join.setConditions(joins)` 设置连接条件（QueryBean.java:536-543），但 `QuerySourceBean.cloneInstance()` 只复制 sourceName/alias/joinType/filter/dimFields，`conditions` 字段被丢弃。
- **风险**: `QueryBean.cloneInstance()`（QueryBean.java:96-98 逐个调用 `QuerySourceBean::cloneInstance`）产出的克隆查询丢失 join 条件。仓库内现实调用路径：`CrudApiPageIterator.buildPageQuery()`（nop-api-core）、`OrmEntityDao.java:793`、`nop-batch/nop-batch-orm/.../OrmQueryBatchLoaderProvider.java:73`、`DaoEntityBlockingSource.java:48` 均对 QueryBean 做 cloneInstance。含条件 join 的查询经克隆后变为无条件连接（笛卡尔积或 SQL 语义变化），造成查询结果数据错误，且无任何报错。
- **建议**: 在 cloneInstance 中补充 `if (conditions != null) bean.setConditions(conditions.stream().map(c -> {...}).collect(...))`（QueryJoinConditionBean 为纯数据类，浅拷贝列表或逐项新建均可），并增加回归测试：构造带 conditions 的 join，断言克隆后 conditions 等价。
- **误报排除**: 已通读 QueryBean.java 全文确认 addJoin 是 conditions 的唯一写入点、getJoins 是下游消费点；已 grep 全仓库确认 cloneInstance 的多条调用链（OrmEntityDao、batch loader、CrudApiPageIterator）都会把克隆后的 QueryBean 交给查询执行层；QueryJoinConditionBean 无 ICloneable 实现（纯 getter/setter），不存在其它克隆补偿机制。

### [P1] QueryFieldBean.cloneInstance() 丢失 expression/formula/internal 三个字段

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryFieldBean.java:86`
- **维度**: D1
- **证据**:
```java
private TreeBean expression;
private String formula;
private boolean internal;
...
public QueryFieldBean cloneInstance() {
    QueryFieldBean field = new QueryFieldBean();
    field.setOwner(owner);
    field.setName(name);
    field.setAlias(alias);
    field.setAggFunc(aggFunc);
    return field;   // expression/formula/internal 未复制
}
```
- **现状**: `QueryBean.cloneInstance()`（QueryBean.java:86-88）通过 `fields.stream().map(QueryFieldBean::cloneInstance)` 克隆字段列表，但克隆只保留 owner/name/alias/aggFunc。
- **风险**: 带计算字段（expression 公式树、formula 表达式）或 internal 标记的查询经 `query.cloneInstance()`（CrudApiPageIterator 分页、OrmEntityDao:793、batch loader 等）后，克隆查询返回的字段集发生变化：计算列消失或退化为普通列，查询结果列缺失/取值错误，静默发生。
- **建议**: 补充 `field.setExpression(expression == null ? null : expression.cloneInstance()); field.setFormula(formula); field.setInternal(internal);` 并加回归测试（同目录已有 TestQueryBeanJoinAndClone.java 可扩展）。
- **误报排除**: 已通读 QueryFieldBean 全文确认这三个字段均有公开 getter/setter 且被序列化（@PropMeta 5/6/7），属于一等字段；对比同包 `QueryAggregateFieldBean.cloneInstance()`（完整复制 formula/filter）与 `OrderFieldBean.cloneInstance()`（完整复制），确认这是遗漏而非有意裁剪。

### [P2] ContextProvider.disableExpireTime() 实际上没有禁用超时时间

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextProvider.java:252`
- **维度**: D1
- **证据**:
```java
/**
 * 在执行task的过程中禁用context上的callExpireTime参数。主要用于一些记录日志的场景，避免日志记录过程中因为超时发生日志漏记
 */
public static <T> T disableExpireTime(Supplier<T> task) {
    IContext context = currentContext();
    if (context == null || context.getCallExpireTime() < 0)
        return task.get();

    IContext proxy = new CallExpireTimeProxyContext(context);
    return runWithProxyContext(proxy, context, task);
}
```
```java
// CallExpireTimeProxyContext.java:6
public CallExpireTimeProxyContext(IContext context) {
    super(context);
    this.callExpireTime = context.getCallExpireTime();  // 原值拷贝，未置为 -1
}
```
- **现状**: javadoc 承诺"禁用 callExpireTime"，但代理上下文只是把原 expireTime 拷贝到自己的字段并覆写 getter，task 运行期间 `ContextProvider.currentContext().getCallExpireTime()` 返回值与原 context 完全相同。
- **风险**: 该方法调用后在 task 内所有超时判断（isCallExpired 等）行为不变，超时后日志补偿逻辑仍会被判定过期而中断，与方法语义相反。
- **建议**: 构造代理后显式 `proxy.setCallExpireTime(-1)`（代理已覆写 setter，不影响原 context），或提供带参重载。
- **误报排除**: 已通读 CallExpireTimeProxyContext.java 全文（全部 15 行）确认没有任何地方把代理值改为 -1；已通读 DelegateContext.java 确认其余方法均直接委托、不会改写超时；与 nop-entropy-master 工作副本对比确认两份代码一致（非本工作区引入的改动）；grep 全仓库未发现其它调用点会在构造后补设 -1。

### [P2] PointBean.fromLngLatString/fromLatLngString 子串起始位置 off-by-one，合法输入永远解析失败

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/geometry/PointBean.java:108`
- **维度**: D1
- **证据**:
```java
public static PointBean fromLngLatString(String str) {
    ...
    int pos = str.indexOf(',');
    ...
    double x = ConvertHelper.toPrimitiveDouble(str.substring(1, pos), NopException::new);
    double y = ConvertHelper.toPrimitiveDouble(str.substring(pos, str.length() - 1),
            NopException::new);
    return new PointBean(x, y);
}
```
- **现状**: y 分量取 `str.substring(pos, ...)` 从逗号本身开始，例如 `"[113.5,22.3]"` 得到 `",22.3"`，`Double.parseDouble(",22.3")` 必然抛 NumberFormatException，经 `NopException::new` 转成 ERR_CONVERT_TO_TYPE_FAIL 异常。fromLatLngString（131 行）同样问题。应为 `substring(pos + 1, str.length() - 1)`。对比 fromWktString（146 行）分隔符是空格、`parseDouble(" 22.3")` 会忽略首尾空白，所以只有 WKT 形式侥幸可用。
- **风险**: 任何对合法 `[lng,lat]` / `[lat,lng]` 字符串的解析都抛异常；该方法标注 `@StaticFactoryMethod`，会被框架按名字反射用于字符串到 PointBean 的转换（geo 属性反序列化），对应输入路径整体不可用。
- **建议**: 两处 `substring(pos, ...)` 改为 `substring(pos + 1, ...)`，并补充 `"[1,2]"` 解析单测。
- **误报排除**: 已通读 PointBean 全文三个 parse 方法逐一核算下标；grep 全仓库确认无其它调用方自行预处理逗号；src/test 下无该类测试掩盖此问题。

### [P2] ConvertHelper.stringToLong 对单字符单位后缀（"G"/"M"/"K"）产生 NPE

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:827`
- **维度**: D1
- **证据**:
```java
if (str.charAt(0) != '-') {
    char c = str.charAt(str.length() - 1);
    if (c == 'G' || c == 'g') {
        str = str.substring(0, str.length() - 1);
        long value = (long) (stringToNumber(str, errorFactory).doubleValue() * 1024 * 1024 * 1024L);
        return value;
    } else if (c == 'M' || c == 'm') { ...
```
```java
// ConvertHelper.java:1087
public static Number stringToNumber(String val, Function<ErrorCode, NopException> errorFactory) {
    if (val == null || val.length() <= 0)
        return null;
```
- **现状**: 输入 `"G"`/`"M"`/`"K"`（非空、首字符即单位后缀）时 `str.substring(0,0)` 得到空串，`stringToNumber("")` 按契约返回 null，随后 `.doubleValue()` 抛 NullPointerException。
- **风险**: 配置值/请求参数形如 `max-size=G` 的手误不会得到带错误码的转换失败（ERR_CONVERT_TO_TYPE_FAIL），而是裸 NPE，丢失 value、targetType 等错误上下文。该方法是 Long 类型转换（含 `convertConfigTo` 配置注入路径）的公共入口。
- **建议**: 单位分支内先判空并调用 `handleError(ERR_CONVERT_TO_TYPE_FAIL, null, Long.class, 原始str, errorFactory)`。
- **误报排除**: 已核对 ApiStringHelper.isEmpty 语义（仅 null/长度 0，"G" 不为空会进入分支）与 stringToNumber 空串返回 null 的实现；追踪 `SysConverterRegistry` 中 `toLong` → `ConvertHelper::toLong` → `stringToLong` 的注册链确认调用路径现实存在。

### [P2] ConvertHelper.stringToNumber 的 expPos 计算沿袭 commons-lang 老 bug，畸形科学计数法导致裸 SIOOBE/NFE

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:1116`
- **维度**: D4（兼 D1）
- **证据**:
```java
int decPos = val.indexOf('.');
int expPos = val.indexOf('e') + val.indexOf('E') + 1;

if (decPos > -1) {
    if (expPos > -1) {
        if (expPos < decPos) { ... }
        dec = val.substring(decPos + 1, expPos);   // expPos 可能越过串尾
    } else {
        dec = val.substring(decPos + 1);
    }
    mant = val.substring(0, decPos);
```
- **现状**: `indexOf('e') + indexOf('E') + 1` 在 e/E 同时出现（如 `"1.2e3E4"`，expPos=9 > 长度 7）或仅一处但位置组合特殊时，`substring(decPos + 1, expPos)` 直接抛 StringIndexOutOfBoundsException；`"1e1E1"` 类输入则走到 `new BigInteger("1e1E1")` 抛未捕获 NumberFormatException。这两段代码不在任何 try/catch 内，`toNumber(...)` 直接把裸异常抛给调用方。
- **风险**: 对外类型转换 API（`ConvertHelper.toNumber`、以及 "G/M/K" 后缀分支里的 `stringToNumber(...).doubleValue()`）遇到同时含 e/E 的用户输入时抛出无错误码、无参数上下文的原生运行时异常，违反模块 "NopException + ErrorCode" 的错误处理契约。此为 Apache commons-lang NumberUtils.createNumber 的已知历史缺陷的复制版本。
- **建议**: expPos 改为分别记录 `idxE`/`idxE2` 并取有效者（commons-lang 后续版本的修法），或整体 try/catch 后统一走 `handleError`。
- **误报排除**: 已逐字符推演 "1.2e3E4"（decPos=1，expPos=3+5+1=9，substring(2,9) 越界）与 "1e1E1"（dec==null && exp==null 分支落到 `new BigInteger(val)` 未捕获）两个具体输入；确认 stringToNumber 主体（1115-1135 行）无外层 try 包裹。

### [P2] IntRangeBean.parse / LongRangeBean.parse 对空段输入（",5" / "3,"）抛裸 NPE

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/IntRangeBean.java:58`（LongRangeBean.java:41 同型）
- **维度**: D1
- **证据**:
```java
Integer limit = ConvertHelper.stringToInt(str.substring(pos + 1),
        err -> new NopException(ApiErrors.ERR_INVALID_OFFSET_LIMIT_STRING).param(ApiErrors.ARG_VALUE, str));
return of(start, limit);   // start/limit 为 null 时自动拆箱 NPE
```
- **现状**: `ConvertHelper.stringToInt("")`/`stringToLong("")` 对空串返回 null（isEmpty 直接 return null），`of(int, int)` 触发 Long/Integer→long/int 自动拆箱抛 NullPointerException。
- **风险**: 这两个方法标注 `@StaticFactoryMethod`（框架反射用于字符串到区间 Bean 的转换，如分页/配置字符串 "offset,limit"），手误输入 `"3,"` 或 `",5"` 时得到裸 NPE，而不是方法内精心准备的 ERR_INVALID_OFFSET_LIMIT_STRING 错误。
- **建议**: 解析结果为 null 时调用 handleError 报 ERR_INVALID_OFFSET_LIMIT_STRING（与现有 errorFactory 一致）。
- **误报排除**: 已核对 stringToInt/stringToLong 的 isEmpty→null 契约（ConvertHelper.java:812-820 / 823-826）与 `of` 的原生参数签名；确认两个 parse 方法均无其它空值防护。

### [P2] ApiMessage.removeHeader 与 setHeader 锁纪律不一致，并发下可损坏 TreeMap

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiMessage.java:103`
- **维度**: D3
- **证据**:
```java
public void setHeader(String name, Object value) {
    // 并发setHeader需要串行化，否则并发写TreeMap可能损坏结构
    synchronized (this) {
        ApiHeaders.setHeader(getHeaders(), name, value);
    }
}
...
public void removeHeader(String name) {
    if (headers != null) {
        headers.remove(name);     // 未持锁
    }
}
```
- **现状**: 类注释明确 headers 是懒初始化的 TreeMap 且并发写会损坏结构，setHeader/addHeadersIfAbsent 均已 synchronized(this)，但 removeHeader 直接对同一 TreeMap 做结构性修改而不持锁。
- **风险**: ApiRequest/ApiResponse 常跨线程传递（类注释自述）。一个线程 setHeader（持锁 put）与另一线程 removeHeader（无锁 remove）并发时，TreeMap 红黑树结构可被破坏，后续读取抛 ConcurrentModificationException 或死循环。
- **建议**: removeHeader 同样包裹 `synchronized (this)`，与 setHeader 保持同一把锁。
- **误报排除**: 已通读 ApiMessage.java 全文确认仅这三处访问 headers 的写路径、锁对象均为 this；removeHeader 无任何其它同步包裹；getHeadersOrNull/removeHeader 的调用方（如 setBearerToken→setHeader 路径）不构成替代保护。

### [P2] PlaceholderConfigReference 缓存字段非 volatile，并发首次读取可能返回未替换的原始值

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/config/PlaceholderConfigReference.java:29`
- **维度**: D3
- **证据**:
```java
private final IConfigReference<T> ref;

private int refValueHash = 0;
private T actualValue = null;

@Override
public T get() {
    T value = ref.getAssignedValue();
    if (value == null) {
        return ref.getDefaultValue();
    }
    int valueHash = value.hashCode();
    if (actualValue == null) {
        refValueHash = valueHash;
        actualValue = replace(value, AppConfig.getConfigProvider());
    } else if (valueHash != refValueHash) {
        return value;
    }
    return actualValue;
}
```
- **现状**: `actualValue`/`refValueHash` 是普通字段的 check-then-act 缓存。两个线程同时首次 get() 时：线程 B 可能看到 `actualValue != null`（已发布）但读到过期的 `refValueHash`（两条写语句非原子），于是走 `valueHash != refValueHash` 分支返回未做占位符替换的原始值；另外该懒发布无 happens-before，存在不安全发布风险。
- **风险**: 配置引用（AppConfig.withPlaceholder 包装的静态引用）会被多线程并发读取；竞态窗口内调用方拿到 `${xxx}` 形式的未解析值，连接串/参数携带占位符下发，间歇性且难复现。（注：值更新后返回原始新值、引用目标变化不重解析是 TestAppConfig 明确固化的设计，不计为缺陷。）
- **建议**: 两个字段声明为 volatile 并将"判空+写入"收敛到局部变量后一次性赋值（先 actualValue 后配套 hash 或改用单一不可变 holder），或对 get() 同步。
- **误报排除**: 已通读该类全文与 TestAppConfig.java（确认单线程语义符合预期、测试未覆盖并发）；grep 确认 withPlaceholder 仅在 AppConfig 与本类出现，无其它线程安全的包装层。

### [P2] CrudApiPageIterator 依赖后端游标契约，hasNext/nextCursor 缺失时可能无限重复拉取首页

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/api/CrudApiPageIterator.java:92`
- **维度**: D1
- **证据**:
```java
QueryBean pageQuery = buildPageQuery();
PageBean<O> page = api.findPage(pageQuery, selection, cancelToken);

if (page == null || page.getItems() == null || page.getItems().isEmpty()
        || Boolean.FALSE.equals(page.getHasNext())) {
    eof = true;
    return false;
}
currentBatch = page.getItems();
cursor = page.getNextCursor();

if (currentBatch.size() < pageSize) {
    eof = true;
}
```
- **现状**: 迭代器强制 `setOffset(0)` 并完全依赖 cursor 推进（buildPageQuery，120-133 行）。终止条件只识别 `hasNext == FALSE` 或"本页不足 pageSize"。若 findPage 实现不填 hasNext（null）且每页恰好返回 pageSize 条、或 hasNext=TRUE 但 nextCursor 为 null（按 offset 分页的后端），则 cursor 保持 null，每次 buildPageQuery 构造出完全相同的查询。
- **风险**: hasNext() 永远返回 true，next() 反复返回同一页数据，形成对下游 API 的无限调用与重复数据处理。PageBean 的 hasNext/nextCursor 都是可空字段，ICrudApi 接口未文档化强制契约；仓库内主要实现（OrmEntityDao:612 setHasNext）正确填充，但该迭代器是公共 API，对接其它实现（RPC 代理、自定义 CrudBizModel）时风险现实存在。
- **建议**: 当 `cursor == null`（或 cursor 与上一轮相同）且未收到 eof 信号时置 eof 并停止，或在构造时要求后端支持游标并显式校验。
- **误报排除**: 已通读 CrudApiPageIterator 与 CrudApiItemIterator 全文、PageBean 字段可空性、ICrudApi.findPage 契约（无 hasNext 强制说明）；已核对 OrmEntityDao 是目前唯一 setHasNext 的实现，其它实现路径无此保证。

### [P2] ApiMessage.appendHeaders 使 ApiResponse/ApiRequest.toString() 明文输出 Authorization/AccessToken 等敏感头

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiMessage.java:140`
- **维度**: D5
- **证据**:
```java
// 默认 toString() 只输出对象 hash，错误/调试信息完全不可见。子类应重写 toString() 暴露核心字段，
// 并通过此方法统一附加 headers，避免散落的拼接逻辑。
protected void appendHeaders(StringBuilder sb) {
    if (hasHeaders()) {
        sb.append(",headers=").append(getHeadersOrNull());
    }
}
```
- **现状**: ApiRequest.toString()/ApiResponse.toString()（注释明确"便于调试与日志排查"）在日志中整体打印 headers Map。RPC 场景下该 Map 携带 `Authorization`（含 Bearer token，见 getBearerToken/setBearerToken）、`X-Nop-Access-Token`、`Cookie` 等敏感头，均为明文。
- **风险**: 常见 `LOG.info("...{}", request/response)` 模式会把令牌写入日志文件/日志采集系统，造成凭据泄漏。
- **建议**: appendHeaders 对敏感键（HEADER_AUTHORIZATION、HEADER_ACCESS_TOKEN、HEADER_COOKIE）做脱敏（如截断为前 4 位 + "***"），或仅输出键名集合。
- **误报排除**: 已通读 ApiMessage/ApiRequest/ApiResponse 三个 toString 实现确认 headers 无任何过滤；确认 ApiHeaders.setAuthToken/setAuthorization 确实把这些值放入同一 Map；toString 的注释表明其设计用途就是日志输出。

### [P3] ResolvedPromise.whenComplete 在已失败场景吞掉回调抛出的异常

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ResolvedPromise.java:476`
- **维度**: D8（兼 D4）
- **证据**:
```java
public CompletionStage<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
    Objects.requireNonNull(action);
    try {
        action.accept(this.result, this.exception);
    } catch (final Throwable e) {
        if (this.exception == null) {
            LOG.error("nop.err.promise.whenComplete.action.fail", e);
            return ResolvedPromise.completionException(e);
        }
        // exception != null 时直接落到这里：既不记录也不传播 e
    }
    return this;
}
```
- **现状**: CompletionStage.whenComplete 规范要求"action 抛异常时返回的 stage 以该异常完成"，与源 stage 状态无关；此处当 promise 已失败时，action 的异常被完全吞掉（连日志都没有），返回 stage 仍保持原异常。
- **风险**: 基于 whenComplete 做清理/计数的回调若抛错，在失败路径上静默丢失，掩盖资源清理失败等问题。属第三方(asyncutils)拷贝代码的语义偏差，当前平台内多为成功路径使用，危害有限。
- **建议**: 失败分支同样 LOG.error 记录 e，或按规范返回以 e 完成的 stage。
- **误报排除**: 已通读 ResolvedPromise 全文其余 whenCompleteAsync/handle 实现对比（它们均有记录或传播），确认只有此同步版本吞异常。

### [P3] TimeOut.isExpired() 恒等于初始 timeout==0，从不做时间判断

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/TimeOut.java:33`
- **维度**: D1
- **证据**:
```java
public class TimeOut {
    private final long timeout;
    private final long expireTime;

    public TimeOut(long timeout) {
        this.timeout = timeout;
        this.expireTime = CoreMetrics.timeoutToExpireTime(timeout);
    }
    /**
     * 是否已经到达超时时间
     */
    public boolean isExpired() {
        return timeout == 0;
    }
```
- **现状**: isExpired 判断的是构造时传入的常量 timeout 是否为 0，与当前时间无关；构造 timeout>0 的实例经过再长时间 isExpired() 仍为 false。语义应类似 `getRemainingTime() <= 0`（getRemainingTime 的实现是正确的时间差计算）。
- **风险**: 依赖该方法做超时中断的调用方永远等不到"已超时"。grep 全仓库未发现 TimeOut.from/isExpired 的仓库内调用方，属公共工具类的潜在错误，暂无现实触发路径。
- **建议**: 改为 `timeout >= 0 && CoreMetrics.expireTimeToTimeout(expireTime) <= 0`，并补充单测。
- **误报排除**: 已通读 TimeOut 全文与 CoreMetrics 对应方法；全仓库 grep 确认无调用方（仅定义处命中），据此降级为 P3。

### [P3] SysConverterRegistry.registerNamedConverter 的第二个 Guard 校验对象写错（复制粘贴）

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/SysConverterRegistry.java:141`
- **维度**: D1
- **证据**:
```java
public void registerNamedConverter(String name, TargetTypeConverter converter) {
    LOG.trace("nop.api.convert.register-converter:name={}", name);
    Guard.notEmpty(name, "nop.err.api.convert.empty-converter-name");
    Guard.notNull(name, "nop.err.api.convert.null-converter");   // 应为 converter
    namedConverters.put(name, converter);
}
```
- **现状**: 第二个 Guard 重复校验 name，应为 `Guard.notNull(converter, ...)`。
- **风险**: 传入 null converter 时不会被 Guard 拦截，而是落到 ConcurrentHashMap.put 的 NPE，错误信息无法指向真实原因；name 重复校验属死代码。
- **建议**: 改为 `Guard.notNull(converter, "nop.err.api.convert.null-converter")`。
- **误报排除**: 已对照同文件 registerConverter（128-139 行）的正确写法确认意图。

### [P3] StaticBeanContainer.getBean 声明 @Nonnull 但可能返回 null

- **文件**: `nop-entropy-fix-ai-check/nop-kernel/nop-api-core/src/main/java/io/nop/api/core/ioc/StaticBeanContainer.java:48`
- **维度**: D8
- **证据**:
```java
@Nonnull
@Override
public Object getBean(String name) {
    return beans.get(name);
}
```
- **现状**: 接口 IBeanContainer.getBean 标注 jakarta @Nonnull，本实现直接返回 map.get 结果，bean 不存在时返回 null，违反契约。
- **风险**: 依赖 @Nonnull 做判空省略的调用方（静态分析或运行时断言）会在远处遭遇 NPE，错误位置与根因分离。对比同文件 getBeanByType 缺 bean 时显式抛 IllegalArgumentException。
- **建议**: 缺失时抛出带 bean 名的异常，或去掉 @Nonnull 语义并文档化。
- **误报排除**: 已通读 StaticBeanContainer 全文与 IBeanContainer.getBean 声明。

### [P3] FilterBeans.and/or 的 List 重载与可变参数重载行为不一致（不跳过 null、不展平、直接别名入参列表）

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FilterBeans.java:357`
- **维度**: D8
- **证据**:
```java
public static TreeBean and(TreeBean... filters) {
    ...
    for (int i = 0, n = filters.length; i < n; i++) {
        TreeBean filter = filters[i];
        if (filter == null)
            continue;
        if (filter.getTagName().equals(FILTER_OP_AND) || DUMMY_TAG_NAME.equals(filter.getTagName())) {
            // 展平嵌套 AND
```
```java
public static TreeBean and(List<TreeBean> filters) {
    if (filters.size() == 0)
        return alwaysTrue();
    if (filters.size() == 1)
        return filters.get(0);
    TreeBean ret = new TreeBean(FILTER_OP_AND);
    ret.setChildren(filters);      // 不判 null 元素、不展平、复用调用方列表
    return ret;
}
```
- **现状**: 可变参数版本跳过 null 元素并展平嵌套 AND/OR；List 版本（369 行 or 同）直接 `setChildren(filters)`：null 元素原样保留进 children，后续遍历（如下游 transformChild、SQL 生成）会 NPE；同时把调用方列表直接作为内部结构（外部后续修改列表会改变 filter 树）。
- **风险**: `QueryBean.addFilters(List)` 走 List 版本，传入含 null 的列表时产生的过滤树带 null 子节点，特定输入下 NPE；两重载语义漂移也易引发使用错误。
- **建议**: List 版本复用可变参数逻辑或至少过滤 null 并拷贝列表。
- **误报排除**: 已通读 FilterBeans 全文比对两个重载；已核对 QueryBean.addFilters（249-259 行）确实调用 List 版本且只做了 isEmpty 检查。

### [P3] TreeBean.replaceChild(old, null) 在未找到 old 时把 null 追加进 children

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/TreeBean.java:256`
- **维度**: D1
- **证据**:
```java
public void replaceChild(TreeBean oldChild, TreeBean newChild) {
    if (children == null) {
        if (newChild != null) { children = new ArrayList<>(); children.add(newChild); }
    } else {
        int index = children.indexOf(oldChild);
        if (index < 0) {
            children.add(newChild);       // newChild 可能为 null
        } else if (newChild == null) {
            children.remove(index);
        } else {
            children.set(index, newChild);
        }
    }
}
```
- **现状**: `newChild == null` 语义为删除，但仅当 oldChild 被找到时成立；未找到时执行 `children.add(null)`，null 进入子节点列表。
- **风险**: 后续遍历 children（childWithAttr、transformChild 等）对 null 元素 NPE。
- **建议**: `if (index < 0) { if (newChild != null) children.add(newChild); }`。
- **误报排除**: 已通读 TreeBean 全文确认 children 的所有消费方都直接解引用元素。

### [P3] ConvertHelper.stringToMonthDay 的字符串比较校验可放过非法数字段，随后 parseInt 抛裸 NFE

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:1485`
- **维度**: D1
- **证据**:
```java
String month = str.substring(0, pos);
String day = str.substring(pos + 1);
if (month.compareTo("00") < 0 || month.compareTo("12") > 0
        || day.compareTo("00") < 0 || day.compareTo("31") > 0)
    return handleError(...);

int monthValue = Integer.parseInt(month);
int dayValue = Integer.parseInt(day);
```
- **现状**: 用字典序比较做范围校验："12-0a"、"12-1x" 这类 day 含非数字但字典序落在 ["00","31"] 的输入（如 "0a"、"1x"）通过校验，`Integer.parseInt("0a")` 抛出未包装的 NumberFormatException。另外该字典序校验会拒绝合法的非补零形式 "2-15"（"2".compareTo("12") > 0），宽严不一致。
- **风险**: 恶意/错误输入得到裸 NFE 而非 ERR_CONVERT_TO_TYPE_FAIL；非补零输入被误拒。
- **建议**: 先用正则/Character.isDigit 校验数字组成再 parseInt，比较基于整数值。
- **误报排除**: 已用 "0a"/"1x" 与 "12" 的字典序逐字符核算确认可绕过；确认 parseInt 调用在 handleError 之后无 try 包裹。

### [P3] TreeBean.treeEquals 在默认构造（tagName 为 null）实例上 NPE

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/TreeBean.java:47`
- **维度**: D1
- **证据**:
```java
public TreeBean() {
}

public boolean treeEquals(TreeBean node) {
    if (!tagName.equals(node.getTagName()))
        return false;
```
- **现状**: tagName 只能经 setTagName 写入（拒绝 null/空），但无参构造 `new TreeBean()` 留下 null tagName；对其调用 treeEquals 在 `tagName.equals(...)` 处 NPE。
- **风险**: 反序列化/代码中先构造再填充的场景调用 treeEquals 崩溃。边界输入，影响小。
- **建议**: 改为 `Objects.equals(tagName, node.getTagName())`。
- **误报排除**: 已通读 TreeBean 构造器与 setTagName 校验，确认 null tagName 实例可经无参构造产生。

### [P3] DictBean 的 valueMap/labelMap 懒初始化无同步，冻结后的共享字典仍会被并发写缓存字段

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/DictBean.java:189`
- **维度**: D3
- **证据**:
```java
private transient Map<String, DictOptionBean> valueMap;
private transient Map<String, DictOptionBean> labelMap;
...
public DictOptionBean getOptionByValue(Object value) {
    if (valueMap == null)
        this.valueMap = makeValueMap();
    return this.valueMap.get(ConvertHelper.toString(value));
}
```
- **现状**: DictBean 实例常被字典缓存共享并 freeze；getOptionByValue/getOptionByLabel 首次调用时无锁写 transient 缓存字段（freeze 也未预构建），存在竞态：重复构建（幂等、浪费）与无 happens-before 的字段发布。
- **风险**: 并发首查时理论上可见未完全构建的 HashMap（JMM 不安全发布）；同时构成对"冻结对象不可变"约定的例外写。实际概率低、后果多为重复计算。
- **建议**: freeze(true) 时预构建两个缓存（只读共享），或字段加 volatile。
- **误报排除**: 已通读 DictBean 全文确认 valueMap/labelMap 仅这两处写、setOptions 的失效逻辑只覆盖显式 set 路径；确认 freeze 路径未预热缓存。

### [P3] MultiCsvSet 构造器用 Guard.notEmpty 校验集合，实际不拦截空集合

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/MultiCsvSet.java:29`
- **维度**: D1
- **证据**:
```java
public MultiCsvSet(List<Set<String>> sets) {
    this.sets = Guard.notEmpty(sets, "sets");
}
```
```java
// Guard.java:88
public static <T> T notEmpty(T value, String message) {
    if (ApiStringHelper.isEmptyObject(value))   // 仅识别 null 与空 String
        throw new IllegalArgumentException("IsEmpty:" + message);
    return value;
}
```
- **现状**: Guard.notEmpty 基于 isEmptyObject，只判 null 与空字符串，空 List 不会被拦截，构造意图（非空校验）落空。
- **风险**: 语义误导：调用方以为空集合会被拒绝。当前类内 isEmpty()/getFirst() 均自防，无直接崩溃。
- **建议**: 显式 `if (sets == null || sets.isEmpty()) throw ...`。
- **误报排除**: 已通读 Guard.notEmpty 与 ApiStringHelper.isEmptyObject 实现确认判定范围；通读 MultiCsvSet 全文确认无其它防线被依赖。

### [P3] FieldSelectionBean.freeze(cascade=false) 仍对 args/directives 强制级联深度冻结，级联语义不一致

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/FieldSelectionBean.java:190`
- **维度**: D1（兼 D3）
- **证据**:
```java
public void freeze(boolean cascade) {
    if (!frozen) {
        this.frozen = true;
        this.args = freezeMap(args, true);          // cascade 参数被忽略，恒为 true
        this.directives = freezeMap(directives, true);
    }
    if (fields != null)
        freezeItems(fields.values(), cascade);      // 仅 fields 遵循 cascade
}
```
- **现状**: freezeMap 的第二参硬编码 true：即使调用 freeze(false)（只冻结自身、不冻结子节点），args/directives 及其嵌套值也会被深度冻结（嵌套 Map 被 freezeMap 就地 entry.setValue 替换为不可变包装）。
- **风险**: 若 args 中共享了外部可变配置 Map，freeze(false) 后该共享 Map 的嵌套结构被就地改为不可变，外部后续修改抛 UnsupportedOperationException；与 IFreezable 的 cascade 约定相悖。
- **建议**: 两处 `freezeMap(..., true)` 改为 `freezeMap(..., cascade)`，或在 javadoc 明示 args/directives 恒级联冻结。
- **误报排除**: 已通读 FreezeHelper.freezeMap/_deepFreezeMap 确认 cascade=true 时会就地修改嵌套条目；通读 FieldSelectionBean 全文确认 frozen 写路径（makeSubField 的 deepClone 替换）不缓解此问题。

### [P3] CloneHelper.deepMerge(ret, m1, m2) 违背"不修改 m1/m2"的 javadoc，会改写 m1 的嵌套 Map

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/CloneHelper.java:141`
- **维度**: D8（兼 D1）
- **证据**:
```java
/**
 * 不修改m1或者m2, 合并内容存放到ret中
 */
public static void deepMerge(Map<String, Object> ret, Map<String, Object> m1, Map<String, Object> m2) {
    Guard.checkArgument(ret != null && ret != EMPTY_MAP, "invalid merge result");
    deepMerge(ret, m1);
    deepMerge(ret, m2);
}
```
```java
// 2参版本内部（107-130行）：
Object oldValue = m1.get(key);            // 3参场景下 oldValue 即 m1 的嵌套 Map 引用
if (oldValue == null) { m1.put(key,value); continue; }   // 此处 m1 实为 ret
...
if (oldValue instanceof Map && value instanceof Map) {
    Map<String, Object> map = deepMerge((Map) oldValue, (Map) value);  // 就地改写 oldValue
```
- **现状**: 3 参版本先把 m1 的值按引用放进 ret（2 参版本 oldValue==null 分支不克隆），随后合并 m2 时对碰撞 key 调用 2 参 deepMerge 就地修改该引用，即修改了 m1 的嵌套 Map。
- **风险**: 调用方基于 javadoc 假设源 Map 不变（如以缓存/模板 Map 为入参），合并后源被污染。grep 全仓库未发现该 3 参重载的调用方，暂无现实触发路径，故定 P3。
- **建议**: 3 参版本对放入 ret 的值先 deepClone，或修正 javadoc。
- **误报排除**: 已通读 CloneHelper 全文核对 2 参版本的引用语义；全仓库 grep 确认 3 参版本无调用方（仅 2 参被 FieldSelectionBean 使用）。

### [P3] ApiStringHelper.encodeStringMap 生成尾部悬挂分隔符，与 encodeQuery 风格不一致

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/ApiStringHelper.java:387`
- **维度**: D8
- **证据**:
```java
for (Map.Entry<String, ?> entry : map.entrySet()) {
    sb.append(entry.getKey());
    sb.append(keySepChar);
    if (entry.getValue() != null)
        sb.append(entry.getValue());
    sb.append(itemSepChar);       // 每项（含最后一项）之后都追加分隔符
}
```
- **现状**: 输出形如 `a=1,b=2,`（尾部多一个 itemSepChar）。parseStringMap 因跳过空白项可容忍回环，但与 encodeQuery（无尾部 '&'）不一致，直接拼接字符串的比较/展示场景会出错。
- **风险**: 低；主要是往返不对称与外部系统解析困惑。ApiHeaders.setSvcRoute 使用该编码写入 header。
- **建议**: 与 encodeQuery 一致地处理首项分隔符。
- **误报排除**: 已对照 encodeQuery 实现（415-441 行）确认风格差异；确认 parseStringMap 对尾分隔符的容忍仅限回环场景。

### [P3] QueryBean.setFieldNames 语义为追加而非设置，与 setter 命名相悖

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java:165`
- **维度**: D8
- **证据**:
```java
public void setFieldNames(List<String> fieldNames) {
    if (fieldNames == null || fieldNames.isEmpty()) {
        this.fields = null;
    } else {
        for (String fieldName : fieldNames) {
            addField(QueryFieldBean.forField(fieldName));   // 追加到现有 fields
        }
    }
}
```
- **现状**: 先有 fields 再调用 setFieldNames 时结果是合并（可能产生重名字段），而非替换；Jackson 反序列化/数据绑定场景下 setter 的覆盖语义被打破。
- **风险**: 特定赋值顺序下查询字段集与预期不符（重复列）。无直接运行时崩溃。
- **建议**: 先 `this.fields = null` 再逐项 add，或改名为 addFieldNames。
- **误报排除**: 已通读 QueryBean 的 fields 全部写路径（addField/addFields/setFields/setFieldNames）确认只有此方法为追加语义。

## 补充说明（无发现维度的核查结论）

- **D2 资源管理**: 模块 src/main/java 不含任何 InputStream/OutputStream/Reader/Writer/Connection/Channel 持有代码（grep 全量确认，IResourceReference/IByteArrayView 仅为接口声明），无可审目标。
- **D7 平台规范**: 全模块无 `@Inject`、无 org.springframework 引用、无 beans.xml 依赖（该模块不含 `_vfs` beans.xml，属纯 API/内核库），无违规。
- **D5 其余项**: 无命令执行、无反序列化（JsonParseOptions.setYaml 仅为解析选项标志）、无 MessageDigest/随机数使用；路径处理仅 getStdPath 做租户前缀剥离，不涉及文件系统遍历。
- **D6 性能**: 热路径（ConvertHelper/SysConverterRegistry/ApiStringHelper）均使用静态缓存与指针等值优化，未发现循环内重复编译/反射或 O(n²) 拼接；FutureHelper 的常量 promise 复用设计良好。
