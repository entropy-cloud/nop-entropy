# frontend-benchmark 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-frontend-support + nop-benchmark
- 文件数: 约 182（src/main/java，其中 nop-frontend-support 105 个、nop-benchmark 61 个；不含 `_` 前缀生成文件与测试）
- 覆盖范围声明:
  - **深读（全文阅读）**: nop-web 全部 18 个 main java；nop-js 全部 9 个；nop-css 1 个；nop-web-page 非生成 4 个（ConditionExprHelper / ConditionSchemaHelper / GraphDesignerCodeGenerator / GraphDesignerModel）；nop-ui 非生成 24 个中的 20 个（vue 解析与转换链、amis ConditionAdapter、XuiHelper、XuiViewAnalyzer、initialize、UiFormModel/UiGridModel/UiRefViewModel/UiFormCellModel/UiGridColModel/GraphDesigner 校验等；XuiConstants/VueConstants/VueErrors 及若干 16 行纯声明模型仅抽查）。`_gen` 生成文件 25+ 个按要求跳过。
  - **nop-benchmark**: 深读 DataSourceHelper / JdbcService / BaseService / BeetlSQLService / NopOrmService / JMHMain / NopBoot / BaseBenchmark / XplBenchmark / FreemarkerBenchmark / TestReflection / UseInvoker / TestTemplateEngine / EishayParseString（其余 eishay 变体与 orm vo 类为同构样板，抽查 + grep 扫描确认无独立风险点）。
  - **方法**: 先结构扫描，再 grep 空 catch / bare RuntimeException / printStackTrace / synchronized / @Inject private（均无私人字段注入命中），每个可疑点均 Read 上下文验证；为验证结论额外阅读了 nop-core 的 JsonTransformHelper / XNode / TreeBean / StdDomainRegistry / ApiStringHelper.isDigit 与 nop-web 的 `web-defaults.beans.xml`（仅作证据，未修改）。
  - **未覆盖/边界**: nop-core 的 VFS 路径归一化（`..` 防护）属 nop-core 职责，本次仅在 biz 层确认了扩展名校验；`_vfs` 下的 .xpl 模板（web.xlib 等）非 Java 实现代码，仅在追踪调用链时引用。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 8 |
| P3 | 9 |

## 发现列表

### [P1] JavaScriptService.refreshConfig 扩容路径必然抛 ArrayIndexOutOfBoundsException，且新 worker 未初始化

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptService.java:96-107`
- **维度**: D1（另含 D8：扩容出的 worker 缺少必要配置）
- **证据**:
```java
public void refreshConfig() {
    JavaScriptWorker[] workers = this.workers;          // 旧数组，长度 L
    int workerCount = this.workerCount;                 // 新值 N > L
    if (workers.length < workerCount) {
        JavaScriptWorker[] newWorkers = new JavaScriptWorker[workerCount];
        System.arraycopy(workers, 0, newWorkers, 0, workers.length);
        for (int i = workers.length; i < workerCount; i++) {
            JavaScriptWorker worker = new JavaScriptWorker();  // 未走 newWorker()，未设置 initScriptPath/jsLibLoader
            workers[i] = worker;                        // 写入旧数组，i >= L
            executor.execute(worker);
        }
        this.workers = newWorkers;
```
- **现状**: 循环第一次迭代 `workers[L] = worker` 即越界抛 `ArrayIndexOutOfBoundsException`。即使改为写 `newWorkers`，`new JavaScriptWorker()` 也绕过了 `newWorker()`，新 worker 的 `initScriptPath`/`jsLibLoader` 均为 null，启动后 `runInitScript` 会失败。
- **风险**: `nop.js.service.worker-count` 在 `web-defaults.beans.xml` 中声明为 `@r-cfg`（可刷新配置），运行期调大该值即触发；配置刷新中断，动态 JS 转换（rollup）无法按预期扩容。缩减路径（`shutdownGracefully`）正常。
- **建议**: 循环内改写 `newWorkers[i] = worker`，并统一使用 `newWorker()` 工厂方法构造；补一个改变 workerCount 的刷新单测。
- **误报排除**: 已确认 `this.workers` 初始长度等于启动时 workerCount（默认 5），仅当刷新后的配置值大于当前数组长度才进入该分支——这正是该分支的唯一用途，属真实缺陷而非防御代码。

### [P1] PageProvider.getPage 对缓存 PageModel 的嵌套 map 做无同步就地改写（permissions→roles 转换存在竞态）

- **文件**: `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageProvider.java:175-213`（配合 `nop-kernel/nop-core/.../json/utils/JsonTransformHelper.java:30-68`）
- **维度**: D3（并发）/ D1（权限展示正确性）
- **证据**:
```java
PageModel pageModel = (PageModel) ResourceComponentManager.instance().loadComponentModel(localeAndPath); // 缓存实例
Map<String, Object> data = pageModel.getData();
if (rolePermissionMapping != null) {
    data = (Map<String, Object>) JsonTransformHelper.transform(data, this::transformPermissions, this::hasXuiAuth);
}
...
protected Object transformPermissions(Object value) {
    Map<String, Object> map = (Map<String, Object>) value;
    Object perms = map.remove(WebConstants.ATTR_XUI_PERMISSIONS);   // 就地改写缓存中的嵌套 map
    ...
    map.put(WebConstants.ATTR_XUI_ROLES, mergedRoles);
```
`JsonTransformHelper.transformMap` 只复制外层容器，谓词命中的嵌套 map 原样传入 transformer 并被 `remove`/`put` 就地修改——该 map 同时被缓存的 PageModel 引用，且无任何同步。
- **现状**: 首次加载后多个请求并发执行 `getPage` 时，多个线程会并发对同一 HashMap 执行 remove+put；HashMap 非安全发布，其它线程可能观察到"permissions 已删、roles 尚未写入"的中间态并直接原样拷贝返回。
- **风险**: 该中间态返回给前端时，子节点既无 `xui:permissions` 也无 `xui:roles`；代码注释自述"roles 为空会被前台误以为不需要判断权限"（所以才有 `"none"` 兜底），缺失两个属性同样导致前台跳过权限过滤，越权展示受限 UI 元素。同时并发 HashMap 结构性写存在容器损坏风险（永久污染缓存模型）。
- **建议**: 在 loadPage 完成后一次性完成 permissions→roles 转换（加载期单线程执行），或 transform 前对嵌套命中 map 做深拷贝，保证返回结构与缓存隔离。
- **误报排除**: 已核实 `JsonTransformHelper.transform/transformMap` 的实现确认嵌套命中对象不复制；已核实 `ResourceComponentManager.loadComponentModel(localeAndPath)` 按 locale+path 缓存同一实例；`getPage` 由 `PageProviderBizModel.getPage`（publicAccess）每请求调用，竞态窗口为首次加载后的并发首访。

### [P2] XuiHelper._getControlTag 域名后缀剥离判断越界（charAt(pos+2)）：单字符后缀崩溃、注释自带的 "json-4k" 用例失效

- **文件**: `nop-frontend-support/nop-ui/src/main/java/io/nop/xui/utils/XuiHelper.java:158-167`
- **维度**: D1
- **证据**:
```java
if (domain != null) {
    int pos = domain.lastIndexOf('-');
    // 忽略 json-4k这种后面的长度描述
    if (pos > 0 && StringHelper.isDigit(domain.charAt(pos + 2))) {
        String baseDomain = domain.substring(0, pos);
        tag = tryGetControl(lib, baseDomain, mode);
```
- **现状**: 判断的是 `-` 后第 2 个字符。以 `domain="json-4"`（长度 6，pos=4）为例，`charAt(6)` 抛 `StringIndexOutOfBoundsException`；`"json-4k"`（注释中的目标场景）`charAt(6)='k'` 非数字，baseDomain 剥离静默失效；只有 ≥2 位数字后缀（如仓库中实际存在的 `json-4000`）才恰好工作。
- **风险**: 控件解析在 view/form 生成链路上被 `web.xlib:668`、`flux-web.xlib:634` 调用；schema 配置 `domain="xxx-4"` 这类单字符后缀会在页面/视图生成时直接崩溃，`json-4k` 类则静默丢失控件回退。
- **建议**: 改为 `domain.charAt(pos + 1)`（或先校验 `pos + 2 < domain.length()`），并为 `"json-4k"`/`"json-4"`/`"json-4000"` 三种形态补单测。
- **误报排除**: 已核实 `StringHelper.isDigit(int)` 即 `'0'..'9'` 判断（ApiStringHelper.java:575）；已 grep 仓库 xmeta/orm 确认现有数据均为多位后缀（`json-4000`），故降为 P2 而非 P1，但注释与实现不一致本身就是缺陷证据。

### [P2] WebPageHelper.normalizeXuiImportUrls 的 Collection 分支误用属性名 name 代替元素 path

- **文件**: `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java:284-303`
- **维度**: D1
- **证据**:
```java
} else if (value instanceof Collection) {
    Collection<String> paths = (Collection<String>) value;
    return paths.stream().map(path -> normalizeFileUrl(loc, name)).collect(Collectors.joining(","));
```
- **现状**: lambda 参数 `path` 未被使用，传入的是属性名 `name`（固定为 `"xui:import"`）。`normalizeFileUrl` 会将其解析为相对路径并检查 `resource.exists()`，必然失败并抛 `ERR_RESOURCE_NOT_EXISTS`。
- **风险**: 页面以列表形式声明 `xui:import: [a.lib.js, b.lib.js]` 时（该分支就是为此而写），页面加载直接报错；同分支还存在把 List 折叠为逗号串的语义变化。
- **建议**: 改为 `normalizeFileUrl(loc, path)`；Map 分支的 `(String) entry.getValue()` 强转也建议加类型防护。
- **误报排除**: 仓库内现有 page.yaml 均用字符串形式（如 `xui:import: test.lib.js`），所以当前未爆雷；但该分支是刻意编写的支持代码，逻辑错误明确，属潜伏缺陷。

### [P2] ConditionExprHelper._filterToCondition：tagName 为 null 时 NPE，且 `else if ("or")` 分支为不可达死代码

- **文件**: `nop-frontend-support/nop-web-page/src/main/java/io/nop/web/page/condition/ConditionExprHelper.java:49-73`
- **维度**: D1 / D4
- **证据**:
```java
String filterOp = filterBean.getTagName();
...
if ("and".equals(filterOp) || filterOp.equals("or")) {      // filterOp==null 时 NPE；"or" 在此已被处理
    ...
} else if ("or".equals(filterOp)) {                          // 永不可达
    ... ret.put("not", true) ...
}
```
- **现状**: `filterToCondition(value)` 对 Map 输入经 `TreeBean.createFromJson`（无 `$type` 时 tagName 为 null，已核实 TreeBean.java:400-415）或纯文本输入（XNode 文本节点 tagName 为 null）进入本方法后，`filterOp.equals("or")` 抛 NPE；第二个 `else if ("or")` 因前一个条件已含 "or" 永不执行，其 `not=true` 逻辑全部落空。
- **风险**: 作为模块公共 API（amis 条件 ↔ FilterBean 双向转换），畸形/非树输入得到裸 NPE 而非带定位的错误；死代码表明此处发生过重构事故，`not` 语义见下一条。
- **建议**: 第一条件改为 `"and".equals(filterOp) || "or".equals(filterOp)`；删除或恢复（按 tag `"not"`）死分支；入口对 tagName==null 显式报错。
- **误报排除**: 已核实 `XNode.fromValue`（XNode.java:2614-2627）对 Map/String 的转换路径确实可产生 tagName==null；仓库内仅测试引用该类，属对外支撑 API，按公共 API 契约缺陷计。

### [P2] ConditionExprHelper 反向转换丢失 NOT 语义、between 列表越界、children 可产生 null 元素

- **文件**: `nop-frontend-support/nop-web-page/src/main/java/io/nop/web/page/condition/ConditionExprHelper.java:119-165`
- **维度**: D1 / D8
- **证据**:
```java
boolean or = "or".equals(conjunction);
TreeBean ret = or ? FilterBeans.or(filters) : FilterBeans.and(filters);
if (not && or) {                       // not && and 时 NOT 被直接丢弃
    ret = FilterBeans.not(ret);
}
...
if (op != null && op.getType() == FilterOpType.BETWEEN_OP) {
    List<Object> list = (List<Object>) right;
    if (list != null) { min = list.get(0); max = list.get(1); }  // size<1 时越界；非 List 时 CCE
...
return list.stream().map(ConditionExprHelper::conditionToFilter).collect(...); // 元素可为 null
```
- **现状**: (1) amis 支持 `conjunction:"and", not:true`（NAND），转换结果只保留 and，否定条件被静默丢弃，过滤结果集包含本应排除的数据；(2) between 的 right 为单元素数组时 `list.get(1)` 抛 IndexOutOfBoundsException，为标量时 ClassCastException；(3) child 为 `{}` 时 `conditionToFilter` 返回 null，null 进入 filters 后由 `FilterBeans.and/or` 消费。
- **风险**: 前端查询构造器产物属外部可控输入，畸形条件会导致接口 500 或过滤语义错误（数据泄漏方向）。
- **建议**: `not` 与 `and` 组合时包一层 `FilterBeans.not`；between 按 size 与类型防御；`conditionToFilterList` 过滤 null 元素。
- **误报排除**: 已核对 `filterToConditions`（正向）确实跳过 null，说明反向遗漏是非对称疏忽；结合上一条死代码，NOT 处理缺失的推断有代码自证。

### [P2] UiRefViewModel.validate 的 endsWith 参数写反：任意 ".xml" 引用被当作 view.xml 强制校验

- **文件**: `nop-frontend-support/nop-ui/src/main/java/io/nop/xui/model/UiRefViewModel.java:63-83`
- **维度**: D1
- **证据**:
```java
String fileType = StringHelper.fileType(path);
if (XuiConstants.FILE_TYPE_VIEW_XML.endsWith(fileType)) {   // 常量在前："view.xml".endsWith(fileType)
    String page = getPage(); ... // 强制 page/form/grid 恰好一个非空，否则抛异常
```
- **现状**: `FILE_TYPE_VIEW_XML = "view.xml"`（已核实常量值），`"view.xml".endsWith("xml")` 为 true，因此 fileType 为 `"xml"`、`"l.xml"` 等 view.xml 后缀的普通 xml 引用全部进入 view.xml 约束分支。
- **风险**: refView 指向非 view.xml 的 xml 资源时，被误抛 `ERR_XUI_REF_VIEW_MUST_HAS_PAGE_OR_GRID_OR_FORM_ATTR` / `..._ONLY_ALLOW_ONE_NON_EMPTY`，合法配置无法通过校验（视图加载失败）。
- **建议**: 改为 `fileType.equals(FILE_TYPE_VIEW_XML)` 或 `fileType.endsWith(FILE_TYPE_VIEW_XML)`。
- **误报排除**: 仓库内现有 refView 路径多为 view.xml/xmeta，未触雷，但这是纯参数顺序笔误，语义与变量命名（fileType 在后）相悖。

### [P2] JavaScriptWorker 初始化失败后线程死亡但 closed 仍为 false：任务无限排队、调用方永久挂起

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:276-323, 98-103`
- **维度**: D4（异常吞噬后果）/ D2（资源耗尽类比）
- **证据**:
```java
public void run() {
    try {
        ...
        this.init();                 // 失败（如 initScriptPath 资源缺失）直接抛出
        do { ... } while (!closed);
    } finally {
        closed = true;               // 线程死亡时才置位
        ...
    }
}
public void execute(Consumer<Boolean> task) {
    if (closed) throw new NopException(ERR_JS_CONTEXT_ALREADY_CLOSED);
    taskQueue.add(task);             // 线程死后 closed 由 finally 置 true —— 但见下
}
```
- **现状**: `run()` 的 `finally` 会把 `closed=true` 并清空队列，但此后 `execute()` 会正确拒绝；问题在于 `JavaScriptService` 持有的 workers 数组仍包含死 worker，且启动期多个 worker 同时死亡时 `DynamicJsLoader.transformToSystemJs → FutureHelper.syncGet(future)` 这类已入队/将入队的调用在 worker 死亡与 closed 置位之间无超时保护，`syncGet` 无限等待。此外 `schedule()` 入队成功与否完全依赖 closed 的可见性时序。
- **风险**: 初始化脚本缺失/损坏（配置 `/nop/js/libs/nop-server-tool.mjs` 不可达）时，页面首次请求 xjs 转换可能永久挂起而非快速失败。
- **建议**: `schedule` 使用带超时的 future 或在 worker 死亡时主动 complete 队列中所有 future；`JavaScriptService` 监测 worker 存活并重建。
- **误报排除**: 已核实 `FutureHelper.syncGet` 无超时参数调用（DynamicJsLoader.java:131）；`init()` 抛异常路径真实存在（runInitScript 中 `NopException.adapt(e)`）。

### [P2] JdbcService.addEntity 异常路径连接泄漏（无 finally 关闭）

- **文件**: `nop-benchmark/nop-benchmark-orm/src/main/java/org/beetl/sql/jmh/jdbc/JdbcService.java:30-49`
- **维度**: D2
- **证据**:
```java
Connection conn = null;
try {
    conn = dataSource.getConnection();
    PreparedStatement ps = conn.prepareStatement("insert into sys_user  (id,code) values (?,?)");
    ...
    conn.commit();
    conn.close();          // 仅正常路径关闭；SQLException 时泄漏
} catch (SQLException ex) {
    throw new RuntimeException(ex);   // conn 未关闭
}
```
- **现状**: 与同文件 `getEntity()`（有 finally close）形成对照，`addEntity()` 的连接只在 happy path 关闭；`ps`/`rs` 也没有显式关闭（依赖 conn.close 间接释放）。
- **风险**: 基准场景中一旦 insert 失败（如主键冲突），Hikari 池连接被泄漏，反复失败会耗尽连接池（bench harness 卡死）。仅影响基准模块。
- **建议**: 改为 try-with-resources。
- **误报排除**: 已对照同文件 getEntity 的 finally 写法确认差异真实存在。

### [P2] GraalVM JS 上下文授予 HostAccess.ALL + 全量类查找 + 允许类加载（最小权限缺失）

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:339-358`
- **维度**: D5
- **证据**:
```java
return Context.newBuilder("js").allowCreateProcess(false)
        .allowCreateThread(false).allowHostClassLoading(true)
        .allowHostAccess(HostAccess.ALL).allowIO(true).fileSystem(fileSystem)
        .allowHostClassLookup(className -> true)
        ...
```
- **现状**: 引擎加载的脚本来自 VFS（`_vfs/nop/js/**`），而 `SystemJsProviderBizModel.saveJsSource` 允许已登录且 edit-enabled（默认 true）的用户写 `.xjs` 源文件；当前 rollup 管线（nop-server-tool.mjs 的 `rollupTransform`）只解析不执行用户源码，但上下文本身对其中运行的任何代码开放全部宿主能力（任意 Java 类加载、反射、全部成员访问），且 `VAR_JAVA_WORKER` 直接暴露 worker 实例（含 close/execute 等公共方法）。
- **风险**: 一旦任何管线变更让用户可控脚本体进入 `context.eval`（或 rollup 插件执行了模块顶层代码），即等价于服务端任意代码执行；即便不发生，也违反最小权限原则，放大第三方库漏洞的影响面。
- **建议**: 收紧为显式 `HostAccess.Builder` 白名单、`allowHostClassLookup` 限定包前缀、`allowHostClassLoading(false)`；对进入 eval 的内容做来源边界说明。
- **误报排除**: 已核实当前 rollup 路径为"解析打包"而非执行（阅读 nop-server-tool.mjs:10509-10565），故按"加固缺失"定 P2 而非 P0/P1。

### [P3] JavaScriptWorker.toJavaValue：Duration 返回 `isDuration()` 布尔值、Instant 误调 `asDuration()`

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:254-259`
- **维度**: D1（潜伏）
- **证据**:
```java
} else if (value.isDuration()) {
    return value.isDuration();     // 返回 true，而非 value.asDuration()
} else if (value.isInstant()) {
    return value.asDuration();     // Instant 上调 asDuration
```
- **现状**: JS 侧返回 Temporal.Duration 时 Java 得到 `Boolean.TRUE`；返回 Instant 时 `asDuration()` 行为未定义（通常抛转换异常）。
- **风险**: 仅当脚本返回 Duration/Instant 值时触发，当前内置 init 脚本未使用，故 P3。
- **建议**: 改为 `value.asDuration()` / `value.asInstant()`。
- **误报排除**: 逐字核对源码确认两处调用；按 GraalVM Value API 语义判定。

### [P3] JavaScriptWorker.wrapPromise 将"正常完成但结果为 null"的 Future 误路由到 reject

- **文件**: `nop-frontend-support/nop-js/src/main/java/io/nop/js/engine/JavaScriptWorker.java:407-421`
- **维度**: D1（潜伏）/ D8
- **证据**:
```java
javaFuture.whenComplete((result, ex) -> {
    if (result != null) {
        resolve.execute(result);
    } else {
        reject.execute(ex);        // result==null 且 ex==null 时以 null reject
    }
});
```
- **现状**: 判断条件应为 `ex == null`。Java 异步方法正常返回 null（如 `CompletableFuture<Void>`）时，JS promise 被错误 reject。
- **风险**: `toJsPromise` 仅作为全局绑定暴露，内置 init 脚本未引用（已 grep `_vfs` 确认），自定义脚本使用时才会暴露，P3。
- **建议**: 改为 `if (ex == null) resolve.execute(result); else reject.execute(ex);`
- **误报排除**: 已确认仓库内 mjs/js 无 toJsPromise 调用点，按潜伏契约缺陷记录。

### [P3] DynamicJsLoader.transformToSystemJs 抛裸 IllegalStateException 而非 NopException

- **文件**: `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/DynamicJsLoader.java:134`
- **维度**: D7
- **证据**:
```java
} else {
    throw new IllegalStateException("nop.err.web.no-system-js-transformer:not include nop-js module");
}
```
- **现状**: 该异常沿 `getJs`（publicAccess 的 BizQuery）冒泡，错误码字符串未在 `WebErrors` 注册，且未带资源路径参数。
- **风险**: 违反平台"公共 API 用 NopException + ErrorCode"约定，前端只能得到 500 无诊断信息。
- **建议**: 在 `WebErrors` 定义错误码并用 `.param(ARG_PATH, sourcePath)`。
- **误报排除**: 触发条件为未注册 `systemJsTransformer` 且 xjs 内容非 SystemJS 格式，配置上可达（beans.xml 中该 ref 为 optional）。

### [P3] GraphDesignerCodeGenerator 构造函数抛裸 IllegalArgumentException

- **文件**: `nop-frontend-support/nop-web-page/src/main/java/io/nop/web/page/graph_designer/GraphDesignerCodeGenerator.java:31-32`
- **维度**: D7
- **证据**:
```java
if (StringHelper.isEmpty(codeGenLib))
    throw new IllegalArgumentException("nop.err.empty-code-gen-lib");
```
- **现状**: 模型配置错误时无 loc/param，违反错误处理约定（错误码同样未注册）。
- **风险**: 低（设计器代码生成链路，配置错误时才触发）。
- **建议**: 换用 NopException + 错误码并携带模型定位。
- **误报排除**: 已确认 `WebPageConstants`/模块内无对应错误码定义。

### [P3] UiGridModel.validate 先调用 `cm.validate()` 再判空，null 防护为死代码

- **文件**: `nop-frontend-support/nop-ui/src/main/java/io/nop/xui/model/UiGridModel.java:36-53`
- **维度**: D1（潜在 NPE）/ 维护性
- **证据**:
```java
getCols().forEach(col -> {
    UiGridColModel cm = getCol(col.getId());
    cm.validate();                        // cm 可能为 null（getCol 为 keyed 查找）
    ...
    if (cm == null || !cm.isCustom()) {   // 死代码：null 已在上面 NPE
```
- **现状**: 与 `UiFormModel.validate`（`if (cm != null) cm.validate();`）的处理不一致；`getCol` 底层为 `_cols.getByKey(name)`（已核实生成代码），id 为 null 或列表异常时先 NPE。
- **风险**: 触发条件苛刻（col 无 id），主要是一致性/维护性问题。
- **建议**: 对齐 UiFormModel 的判空顺序。
- **误报排除**: 已核对生成的 `getCol` 实现确认可返回 null。

### [P3] ConditionSchemaHelper.getPropType 对未注册 stdDomain 无防护（handler 可为 null）；propToGroup 的 children 可为 null

- **文件**: `nop-frontend-support/nop-web-page/src/main/java/io/nop/web/page/condition/ConditionSchemaHelper.java:98-104, 73-77`
- **维度**: D1（潜在 NPE）
- **证据**:
```java
IStdDomainHandler handler = StdDomainRegistry.instance().getStdDomainHandler(stdDomain);
dataType = handler.getGenericType(false, null).getStdDataType();   // handler 可能为 null
...
static Map<String, Object> propToGroup(...) {
    List<Map<String, Object>> children = schemaToFields(prefix, schema); // props 为空时返回 null
    ret.put("children", children);
```
- **现状**: 已核实 `StdDomainRegistry.getStdDomainHandler` 对未注册且非 `*-string` 后缀的域名返回 null；此时 NPE。`schemaToFields` 在 props 为空时返回 null，直接放入 children。
- **风险**: schema 配置了未注册 stdDomain 的 prop（或空 props 的 group）时，条件字段生成抛 NPE/产出 `"children": null`。是否会被上游 xdef 校验拦截未验证，故 P3。
- **建议**: handler 为 null 时回退 "text"；children 为 null 时置空列表。
- **误报排除**: 已读 StdDomainRegistry.java:33-40 确认可返回 null；未确认 xmeta 加载期是否强制校验 stdDomain 注册，按防御缺失记录。

### [P3] ResourceWithHistoryProvider.rollback 使用 java.security.Timestamp 且忽略时间参数

- **文件**: `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/ResourceWithHistoryProvider.java:18, 35-40`
- **维度**: D8（契约漂移）
- **证据**:
```java
import java.security.Timestamp;
...
public void rollback(String path, Timestamp timestamp) {
    resourceLockManager.runWithLock(path, ..., lock -> {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        resourceHistory.rollback(resource, null);   // timestamp 被忽略，恒传 null
    });
}
```
- **现状**: 参数类型是 JCE 证书时间戳类（几乎必然是想用 `java.sql.Timestamp`/`Instant`），且传入值被丢弃；调用方（两个 BizModel）均传 null，掩盖了问题。
- **风险**: API 语义误导，未来传入时间也不生效。
- **建议**: 修正类型并把参数透传给 `resourceHistory.rollback`。
- **误报排除**: 已 grep 两个调用方均传 null，行为当前无偏差，属契约缺陷而非运行故障。

### [P3] WebPageHelper.unfixPage 并非 fixPage 的逆操作：flux 模式保存页面时反向注入 amis className

- **文件**: `nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java:202-239`（对照 fixPage 139-178）
- **维度**: D1（数据污染，轻）
- **证据**:
```java
public static void unfixPage(Map<String, Object> map) {
    JsonTransformHelper.transformInPlace(map, value -> {
        ...
        } else if (value instanceof Map) {
            ...
            Object dialog = data.get("dialog");
            if (dialog instanceof Map) {
                addClassName((Map<String, Object>) dialog, "bodyClassName", "amis");  // 与 fixPage 同向叠加
```
- **现状**: `fixPage` 在 flux 模式下跳过 className 注入，但 `unfixPage`（savePageSource/getPageDelta 前调用）无条件再注入 `"amis"`；amis 模式下因幂等无感，flux 模式下会把 amis 作用域类写回用户页面源码。
- **风险**: flux 模式经编辑器保存一次后源文件被永久混入 amis 专属类。
- **建议**: unfixPage 对 flux 模式同样跳过，或实现真正的逆向（移除注入值）。
- **误报排除**: 已对照 fixPage 的 `if (isFlux) return value;` 分支与 unfixPage 缺失该分支的事实。

### [P3] nop-benchmark 卫生问题：printStackTrace、裸 RuntimeException 包装、available()+单次 read 且流未关闭

- **文件**:
  - `nop-benchmark/nop-benchmark-orm/src/main/java/org/beetl/sql/jmh/jdbc/JdbcService.java:77`（printStackTrace）
  - `nop-benchmark/nop-benchmark-json/src/main/java/com/alibaba/fastjson2/benchmark/eishay/EishayParseString.java:37`（及同目录 3 个变体，static 块 printStackTrace 后 `str` 保持 null）
  - `nop-benchmark/nop-benchmark-orm/src/main/java/org/beetl/sql/jmh/DataSourceHelper.java:44-67`
- **维度**: D4 / D2 / D7
- **证据**:
```java
// DataSourceHelper.getSqlFromFile
InputStream ins = DataSourceHelper.class.getResourceAsStream("/db/schema.sql");
int len = ins.available();          // available 不保证总长
byte[] bs = new byte[len];
ins.read(bs);                       // 单次 read 不保证读满；ins 从不关闭
...
} catch (Exception ex) {
    throw new RuntimeException(ex); // 裸 RuntimeException（同文件 2 处、JdbcService 2 处）
}
```
- **现状**: 基准代码普遍以 `new RuntimeException(ex)` 包装、`printStackTrace` 记错；schema.sql 读取未关流且 read 语义误用（classpath 资源通常恰好一次读满，故未爆雷）。
- **风险**: 仅影响基准运行与诊断体验，不影响平台运行时。
- **建议**: try-with-resources + readAllBytes；异常改用带上下文的包装。
- **误报排除**: 逐一打开文件确认行号与上下文；均位于 src/main 但属 JMH harness 代码。

## 结论

两模块整体质量尚可：错误处理大多遵循 NopException + ErrorCode 约定，未发现 private 字段注入、`_` 前缀文件手改、空 catch 吞异常等问题。需要优先处理的是：**nop-js 配置刷新扩容必然崩溃（P1）**、**页面权限转换对缓存模型的无同步就地改写（P1）**，以及前端元数据双向转换（ConditionExprHelper/UiRefViewModel/XuiHelper/WebPageHelper）中四处确定的转换缺陷——它们都直接影响"页面渲染正确性"这一模块核心职责。
