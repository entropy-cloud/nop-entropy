# dev-tools 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-dev-tools（idea-plugin/api-debugger/xlang-debugger/maven-shaded-plugin）
- 文件数: 181（任务书口径；实际 `src/main/java` 为 155: idea-plugin 131、api-debugger 11、xlang-debugger 9、maven-shaded-plugin 4，其余为测试/构建产物）
- 覆盖范围声明:
  - **精读**: nop-xlang-debugger 9/9、nop-maven-shaded-plugin 4/4；idea-plugin 的 `debugger/` 包 13/16、`annotator/` 2/2、`resource/` 6/7、`services/` 2/2、`utils/` 8/8、`vfs/` 2/2、`doc/` 1/1；lang 核心 PSI（XLangTag/XLangTagMeta/XLangAttribute/XLangDocumentation/XLangLanguageSubstitutor/XLangScriptLanguageInjector/XLangElementRenameProcessor）与 reference 核心 9 文件；script 核心 3 文件（ExpressionNode 为重点段精读）；template 2/5；api-debugger 核心接口 5/11。
  - **模式扫描未逐行精读**: idea-plugin `lang/script/psi` 下约 30 个小节点类、其余 reference 类（约 8 个）、template 其余 3 个、api-debugger 剩余简单 bean。扫描模式包括: 空 catch、`new RuntimeException`、`printStackTrace`、`invokeLater`/`ApplicationManager`/`runReadAction`、`synchronized`、`FilenameIndex`、`Thread.sleep`/`while(true)`、`.get(0)`、`catch (Throwable)`。每个候选命中均回读上下文确认后才立项或排除。
  - D5（网络面）: 调试器 RPC 服务默认 `host=127.0.0.1`（`ServerConfig` 默认值）且默认关闭（`CFG_XLANG_DEBUGGER_ENABLED=false`），未发现默认对外暴露；无鉴权问题单列 P3。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 14 |
| P3 | 8 |

## 发现列表

### [P1] 调试器数组变量展开必然抛 IllegalArgumentException（`Array.getLength` 传错参数）

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/DebugValueHelper.java:43-48`
- **维度**: D1
- **证据**:
```java
private static Object getNextValue(Object value, DebugValueKey key) {
    if (value.getClass().isArray()) {
        int index = key.getIndex();
        int len = Array.getLength(index);   // 传入了 index(int)，应为 Array.getLength(value)
        if (index < 0 || index >= len)
            return null;
        return Array.get(value, index);
```
- **现状**: `Array.getLength(Object array)` 收到自动装箱的 `Integer`，非数组对象，必然抛 `IllegalArgumentException: Argument is not an array`。
- **风险**: 调试会话中对任何数组类型变量（含 `byte[]`/`List` 转数组等）执行"展开子项"操作时，异常沿 `XLangDebugger.expandExprValue` → RPC → IDEA 端 `XLangValue.computeChildren` 的 err 分支传播，数组展开功能 100% 不可用（显示错误而非子项）。
- **建议**: 改为 `Array.getLength(value)`。
- **误报排除**: 已确认调用链可达: `XLangValue.computeChildren` → `expandExprValueAsync` → `XLangDebugger.expandExprValue` → `DebugValueHelper.getExpandValue` → `getNextValue`，全程无 catch。

### [P1] 调试器变量加载成功分支调用 `reportError(err.getMessage())`，err 恒为 null；且未防 vars 为 null

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangStackFrame.java:76-90`
- **维度**: D1/D8
- **证据**:
```java
debugger.getFrameVariablesAsync(threadId, frameIndex)
        .whenComplete((vars, err) -> {
            if (err != null) {
                super.computeChildren(node);
            } else {
                XValueChildrenList list = new XValueChildrenList(vars.size()); // vars 可能为 null
                for (DebugVariable var : vars) { ... }
                node.addChildren(list, true);
                debugProcess.getSession().reportError(err.getMessage()); // 成功分支 err==null
            }
        });
```
- **现状**: `reportError` 写在 else（成功）分支，`err` 恒为 null，等于每次成功加载变量都向调试会话上报 `null` 错误消息（应为 null 检查或本意是写进 err 分支）。同时服务端 `XLangDebugger.getFrameVariables`（XLangDebugger.java:313-326）在线程不存在时**返回 null**（接口 `IDebugger.getFrameVariables` 无 `@Nullable` 声明），RPC 成功返回 null 后此处 `vars.size()` 直接 NPE。
- **风险**: 每次在调试器 Variables 面板展开帧变量都会触发一次空错误上报（红字/null 弹报）；线程已恢复（stale frame）时回调内 NPE，变量面板静默失败。
- **建议**: 删除成功分支的 `reportError`；对 `vars` 判空；服务端对未找到线程返回空列表而非 null（或接口标注 `@Nullable`）。
- **误报排除**: 已核对 `XLangDebugger.getFrameVariables` 返回 null 的路径（`getSuspendedThread` 未命中），以及 IDEA 端无其他判空。

### [P2] 调试器 Map 子项展开: 循环缺少 `index++` 且返回 key 而非 value

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/DebugValueHelper.java:49-59`
- **维度**: D1
- **证据**:
```java
} else if (value instanceof Map) {
    if (key.getIndex() >= 0) {
        int index = 0;
        for (Object v : ((Map<?, ?>) value).keySet()) {
            if (index == key.getIndex())
                return v;          // 返回的是 key；且 index 从未自增
        }
        return null;
```
- **现状**: `index` 永远是 0，只有第 0 项能命中；命中后返回的是 keySet 的元素（key），而 `expandValue` 中 Map 子项展示的是 `entry.getValue()`，展开目标应是 value。
- **风险**: 值为非 String 的 Map（`expandValue` 中 String 值 index=-1 走 `map.get(name)` 正常；非 String 值 index>=0 走此分支）子项展开: 第 0 项返回错误对象，其余项返回 null（展开为空），调试器 Map 检查结果不可信。
- **建议**: 循环内 `index++`，并返回 `((Map) value).get(v)` 或直接遍历 `entrySet()` 返回 `entry.getValue()`。
- **误报排除**: 已核对 `expandValue`（同文件 134-147 行）对 Map 子项 `setIndex` 的赋值规则，确认非 String 值走 index 路径。

### [P2] XLangDebugContextListener 条件运算符优先级错误 + 空 catch 吞异常

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangDebugContextListener.java:37-53`
- **维度**: D1/D4
- **证据**:
```java
if (event == DebuggerSession.Event.PAUSE
        || event == DebuggerSession.Event.CONTEXT
        || event == DebuggerSession.Event.REFRESH
        || event == DebuggerSession.Event.REFRESH_WITH_STACK
        && myJavaSession.isPaused()) {     // && 只绑定最后一个 ||
    ...
    if (suspendContext instanceof XLangSuspendContext) {
        try {
            session.resume();
        } catch (Exception e) {            // 空 catch
        }
    }
```
- **现状**: 按 Java 优先级实际条件为 `PAUSE || CONTEXT || REFRESH || (REFRESH_WITH_STACK && isPaused())`；按缩进意图应为 `(…|| REFRESH_WITH_STACK) && isPaused()`。PAUSE/CONTEXT/REFRESH 事件在 java 会话未暂停时也会进入分支。
- **风险**: XLang 断点挂起期间，java 调试器侧的 CONTEXT/REFRESH 事件（如用户在调试器视图间切换）会在未经确认的情况下 `session.resume()` 恢复 XLang 挂起——用户断点"自动消失"；恢复失败的异常被空 catch 吞掉无任何日志。
- **建议**: 显式加括号 `(A || B || C || D) && myJavaSession.isPaused()`；空 catch 至少记录 debug 日志。
- **误报排除**: 已确认该 listener 通过 `DebuggerSession.getContextManager().addListener` 注册（XLangDebuggerRunner.java:93-94），事件在 java 调试器活动期间会持续触发。

### [P2] XLangDebugProcess.connect() 无限重试且不可取消（不检查 indicator）

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangDebugProcess.java:108-144`
- **维度**: D2/D6
- **证据**:
```java
ProgressManager.getInstance().run(new Task.Backgroundable(null, "XLang debugger connector", true) {
    public void run(@NotNull final ProgressIndicator indicator) {
        ...
        try {
            if (connect()) { startDebugSession(); }
        } catch (Exception e) { onConnectFail(e.getMessage()); }
    }
});
private boolean connect() {
    while (true) {
        try { debugger = connector.connect(); ... return true; }
        catch (Exception e) {
            try { Thread.sleep(200); } catch (Exception ignored) {}
            if (getProcessHandler().isProcessTerminated()) { return false; }
        }
    }
}
```
- **现状**: `Task.Backgroundable` 声明可取消，但 `run` 内从不调用 `indicator.checkCanceled()`，唯一退出条件是被调试进程终止。
- **风险**: 被调试应用未开调试端口且长运行时，后台任务以 200ms 间隔无限重试且用户无法取消（进度条取消按钮无效），产生持续连接风暴与永不结束的后台任务。
- **建议**: 每轮循环加 `indicator.checkCanceled()`，并考虑加上限次数/超时后走 `onConnectFail`。
- **误报排除**: 已确认 `connect()` 由该 Task 直接调用，且 `onConnectFail` 仅在外层 try 抛异常时进入（循环内已吞掉所有连接异常，实际几乎不可能到达）。

### [P2] `XLangDebugProcess.debugger` 字段跨线程读写但非 volatile

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangDebugProcess.java:58`
- **维度**: D3
- **证据**:
```java
private IDebuggerAsync debugger;   // 无 volatile/同步

// 后台 pooled 线程写入:
private boolean connect() { ... debugger = connector.connect(); ... }
// EDT / 调试器管理线程读取:
public IDebuggerAsync getDebugger() { return debugger; }
```
- **现状**: 字段在 `Task.Backgroundable` 线程中赋值，在 EDT（`resume`/`startStepOver` 等）与调试器管理线程（`XLangBreakpointHandler.sendBreakpoints`）中读取，无 happens-before 保证。
- **风险**: EDT 可能长期读到 stale `null`，导致 resume/step 命令或断点同步被静默丢弃（`if (debugger != null)` 分支直接跳过），表现为"按钮无响应、断点不生效"，且难以复现。
- **建议**: 声明为 `volatile`。
- **误报排除**: 已核对全部读写点（grep `getDebugger()` 调用方覆盖 EDT 与 manager 线程两类）。

### [P2] XLangBreakpointHandler 的两个 HashMap 被多线程并发读写

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangBreakpointHandler.java:39,43,130-134`
- **维度**: D3
- **证据**:
```java
private final Map<String, XLineBreakpoint<XLangBreakpointProperties>> breakpoints = new HashMap<>();
private final Map<String, Breakpoint> bpMap = new HashMap<>();

// EDT（xdebugger 断点事件）: registerBreakpoint/unregisterBreakpoint 做 put/remove
// 调试器管理线程: findBreakPoint 做 get
public XBreakpoint<XLangBreakpointProperties> findBreakPoint(@NotNull StackTraceElement elm) {
    return breakpoints.get(path + ':' + lineNumber);
}
```
- **现状**: `registerBreakpoint`/`unregisterBreakpoint` 由 XBreakpointManager 在 EDT 触发；`findBreakPoint` 在 `XLangDebugProcess.debuggerNotification` 的 `getManagerThread().schedule(...)` 中执行。普通 HashMap 并发读写。
- **风险**: 断点命中通知与断点增删并发时可能读到损坏状态（JDK7 及以前可成环死循环，JDK8+ 表现为丢失更新），断点命中无法定位到 IDE 断点对象，`breakpointReached` 退化为 `positionReached`。
- **建议**: 换 `ConcurrentHashMap`。
- **误报排除**: 已核对 `findBreakPoint` 唯一调用点位于 debuggerNotification 的 manager thread 调度内，与 EDT 写入确为不同线程。

### [P2] XLangSuspendContext 的 LinkedList 在管理线程修改、EDT 读取

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangSuspendContext.java:24,31-46,58-60`
- **维度**: D3
- **证据**:
```java
private List<XLangExecutionStack> myExecutionStacks = new LinkedList<>();

public XLangExecutionStack addExecutionStack(StackInfo stackInfo) {  // debugger manager 线程调用
    ...
    removeExecutionStack(stackInfo.getThreadId());
    myExecutionStacks.add(stack);
    ...
}
public XExecutionStack[] getExecutionStacks() {                      // 调试器 UI（EDT）调用
    return myExecutionStacks.toArray(new XExecutionStack[myExecutionStacks.size()]);
}
```
- **现状**: `addExecutionStack` 由 `debuggerNotification`（manager 线程）调用；`getExecutionStacks`/`getActiveExecutionStack` 由 XDebugSession UI 线程读取，无同步。
- **风险**: 多线程断点命中时（多线程执行 XLang），EDT 遍历/toArray 与后台结构性修改并发，可抛 `ConcurrentModificationException` 或返回不一致快照，线程切换器显示错乱。
- **建议**: 使用 `CopyOnWriteArrayList` 或在访问处同步。
- **误报排除**: 已核对调用链（debuggerNotification → addExecutionStack；XExecutionStack API 由平台 UI 线程消费）。

### [P2] XLangValue.computeChildren 未判空 `getDebugger()` 直接调用（与 XLangStackFrame 行为不一致）

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/debugger/XLangValue.java:63-68`
- **维度**: D1
- **证据**:
```java
String expr = list.get(0).getName();
...
frame.getDebugProcess().getDebugger()      // 可能为 null，未判空
        .expandExprValueAsync(frame.getThreadId(), frame.getFrameIndex(), expr, keys)
```
- **现状**: `XLangStackFrame.computeChildren` 对 `getDebugger()` 判了 null，此处没有。`debugger` 在连接建立前/失败后为 null（参见 P2 volatile 发现）。
- **风险**: 连接未建立或断开后用户在 Watches/Variables 中展开表达式（evaluate 结果的 XLangValue），EDT 上直接 NPE → IDE 报内部错误。
- **建议**: 与 `XLangStackFrame` 一致判 null，走 `node.setErrorMessage` 降级。
- **误报排除**: 已确认 evaluate 路径（`XLangStackFrame.getEvaluator`）在 debugger 为 null 时构造 kind="invalid" 的 XLangValue，该值随后仍可被用户展开触发本方法。

### [P2] XLangDebuggerInitializer.destroy() 不调用 debugger.close()，挂起线程永久阻塞 + 清理线程泄漏

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/initialize/XLangDebuggerInitializer.java:154-162`
- **维度**: D2
- **证据**:
```java
@Override
public void destroy() {
    EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
    if (server != null) {
        server.stop();
        debugger = null;      // 仅置空引用，未调用 debugger.close()
        server = null;
    }
}
```
- **现状**: `XLangDebugger.close()` 负责 `closed=true` + `monitorNotify(RESUME)` 唤醒挂起线程 + `cleanupThread.shutdown()`。destroy 只停了 RPC server。
- **风险**: destroy 时若有线程挂起在 `XLangDebugger.monitorWait`（`while (suspended && !closed)`，`closed` 永不为 true），该线程永久阻塞；`xlang-debugger-cleanup` 单线程调度池（构造器中创建并调度，XLangDebugger.java:72-81）不被 shutdown，反复 initialize/destroy（如测试环境）会累积泄漏线程。
- **建议**: destroy 中保存引用并在 `server.stop()` 后调用 `debugger.close()`。
- **误报排除**: 已核对 `XLangDebugger.close()`/`monitorWait` 的唤醒条件，确认除 close 外无其他路径设置 `closed`。

### [P2] XLangDebugger.waitSuspended() 不可中断，忽略中断后继续死等

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:127-142`
- **维度**: D4/D2
- **证据**:
```java
public void waitSuspended() {
    monitorLock.lock();
    try {
        while (!suspended) {
            try {
                if (!suspendedCondition.await(monitorWaitInterval, TimeUnit.MILLISECONDS)) { ... }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // 恢复标志后继续循环死等
            }
        }
    } finally { monitorLock.unlock(); }
}
```
- **现状**: 被中断时仅恢复中断标志，不退出等待循环。
- **风险**: 通过 RPC 调用 `waitSuspendedAsync` 的服务器线程无法被取消/中断（如客户端断连、关闭请求），对应工作线程被永久占用；`close()` 也不会唤醒 `waitSuspended` 的等待者（close 只 signal `resumeCondition`）。
- **建议**: 捕获中断时检查 `closed` 并退出循环抛出中断/关闭异常。
- **误报排除**: 已核对 `close()` 与 `monitorNotify` 只 signal `resumeCondition`，不 signal `suspendedCondition`。

### [P2] XLangLanguageSubstitutor 以 `file.hashCode()`（identity hash）作缓存键

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/lang/XLangLanguageSubstitutor.java:42,55-59`
- **维度**: D1/D6
- **证据**:
```java
private final Cache<Integer, Language> cached = Caffeine.newBuilder().maximumSize(500).build();

if (file.getFileSystem() instanceof ArchiveFileSystem) {
    lang = cached.get(file.hashCode(), (k) -> getLanguage(file));   // VirtualFile 为 identity hashCode
}
```
- **现状**: `VirtualFile` 未重写 hashCode（identity 语义），装箱为 Integer 后由 Caffeine 按 equals 比较。两个不同 jar 文件的 identityHashCode 碰撞时会命中对方缓存条目；且缓存键不含路径，jar 更新后旧 VirtualFile 换新对象也无法主动失效（仅靠 maximumSize 淘汰）。
- **风险**: 低概率但真实（大项目海量 jar 文件下 birthday 碰撞概率上升）: 某个 jar 内 XML 文件被错误识别为 XLang（或反之），PSI 语言判定错误进而导致解析/高亮/注入异常；缓存永不失效导致 jar 内容变更后仍用旧判定。
- **建议**: 以 `file.getUrl()`（或 path）作键。
- **误报排除**: 已确认该类注册于 `languageSubstitutor` 扩展点且对每个 xml 文件都会被平台调用（plugin.xml 已核对）。

### [P2] XdslResourceTransformer.processXDef 对 `io.nop.` 开头的属性值统一加 `enum:` 前缀

- **文件**: `nop-dev-tools/nop-maven-shaded-plugin/src/main/java/io/nop/maven/plugin/shaded/XdslResourceTransformer.java:102-117`
- **维度**: D1
- **证据**:
```java
if (value.startsWith("enum:")) {
    String typeName = value.substring("enum:".length()).trim();
    String relocated = relocate(typeName, relocatorList);
    entry.setValue(ValueWithLocation.of(null, "enum:" + relocated));
} else if (value.startsWith("io.nop.")) {
    String relocated = relocate(value, relocatorList);
    entry.setValue(ValueWithLocation.of(null, "enum:" + relocated));   // 无条件加 enum: 前缀
}
```
- **现状**: `.xdef` 文件中任意属性值只要以 `io.nop.` 开头，就会被改写为 `enum:<类名>` 形式，且**无论 relocator 是否实际命中**都会加前缀。与上一分支（保留原形式、仅 relocate 类型名）不对称。
- **风险**: 若原值语义不是枚举引用（例如 xdef 某属性值恰好是 `io.nop.*` 类名/常量路径），shade 产物中的 xdef 资源被语义性篡改（`io.nop.commons.util.XHelper` → `enum:io.nop.commons.util.XHelper`），运行期解析该 xdef 行为改变——构建产物数据错误。若"裸 io.nop.* 即枚举引用"是刻意规范，则也应对第一分支之外的写法做类型校验。
- **建议**: 与 owner 确认意图；至少应仅在 relocate 实际改变值时才改写，且第二分支应回写 `relocated` 而非 `"enum:" + relocated`。
- **误报排除**: 已确认该 transformer 对所有 `.xdef` 资源生效（`canTransformResource`），且该分支无条件执行（无类型检查）。

### [P2] XLangAnnotator.annotate 捕获 ProcessCanceledException 并转为警告注解

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/annotator/XLangAnnotator.java:79-92`
- **维度**: D4/D3
- **证据**:
```java
try {
    doAnnotate(holder, element);
} catch (Exception e) {           // ProcessCanceledException 是 RuntimeException，会被捕获
    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
    LOG.debug("nop.validate-xlang-fail", e);
    holder.newAnnotation(HighlightSeverity.WARNING, msg).create();
}
```
- **现状**: `ProcessCanceledException`（含新版 `CancellationException`）落入 `catch (Exception)`，被转成用户可见的 WARNING 注解而不是向上抛出。同项目 `XLangTagMeta.create`（537-539 行）则正确地单独 rethrow 了 PCE，本处遗漏。
- **风险**: 平台约定 PCE 必须传播（表示索引更新/任务取消）。吞掉 PCE 会: 1) 取消期间向用户显示 "Cancelled" 之类警告；2) 破坏平台重试机制（dumb mode 期间 annotate 被取消后本应静默重来），可能引发 EA 断言或反复重跑。
- **建议**: 在 catch Exception 前先 `catch (ProcessCanceledException e) { throw e; }`。
- **误报排除**: 已核对该 annotator 注册于 plugin.xml（`annotator language="XLang"`），且 doAnnotate 链路会调用 `FilenameIndex`（PCE 常见来源）。

### [P2] XlibTagMeta.getAttrDocumentation 对 getAttribute()==null 未判空直接构造文档

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/lang/xlib/XlibTagMeta.java:104-113`
- **维度**: D1
- **证据**:
```java
public XLangDocumentation getAttrDocumentation(String attrName) {
    XlibXDefAttribute attr = getAttribute(attrName);   // 可返回 null（xlib 加载失败/属性未定义）
    XLangDocumentation doc = new XLangDocumentation(attr);  // 构造器内 defAttr.getType()
    doc.setMainTitle(attrName);
    doc.setSubTitle(attr.label);                       // NPE
```
- **现状**: `getAttribute` 经 `withLoadedXlib(..., null)` 可返回 null；`XLangDocumentation(IXDefAttribute)` 构造器（XLangDocumentation.java:64-66）直接调用 `defAttr.getType()`，随后 `attr.label` 也 NPE。
- **风险**: 在 xlib 标签属性上请求 Quick Doc（F1）且该属性在 xlib 模型中不存在或 lib 加载失败时，文档计算链路 NPE，IDE 弹内部错误。调用链已确认: `XLangTagMeta.getAttrDocumentation`（XLangTagMeta.java:494-499）在 `defAttr.isUnknownAttr()` 时进入。
- **建议**: attr 为 null 时返回 null（无文档）。
- **误报排除**: 已核对 `withLoadedXlib` 失败返回 defaultValue=null、`tag.getAttr(attrName)` 未命中返回 null 两条路径。

### [P2] 首次补全触发全项目+全依赖库的文件名级索引全量扫描

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/utils/ProjectFileHelper.java:119-167`
- **维度**: D6
- **证据**:
```java
public static Collection<String> findAllNopVfsPaths(Project project) {
    GlobalSearchScope scope = getSearchScope(project);   // ProjectAndLibrariesScope
    Set<String> names = ...;
    FilenameIndex.processAllFileNames((name) -> { names.add(name); return true; }, scope, null);
    FilenameIndex.processFilesByNames(names, true, scope, null, (file) -> { ... });  // 对每个文件名逐一查询
```
- **现状**: 为收集 vfs 路径，先枚举项目+所有库中全部文件名，再按**每一个文件名**做一次索引查询。结果虽有 CachedValue 缓存（依赖 JavaLibraryModificationTracker/ProjectRootManager），但首次计算为 O(distinct file names) 次索引查询；该计算由 `NopVirtualFileReference.getVariants`（补全路径属性）触发。
- **风险**: 大型项目/大量依赖 jar 下，第一次路径补全会出现可感知的卡顿（数百毫秒到秒级）；依赖变更后缓存失效重新全量扫描。
- **建议**: 使用自定义 `FileBasedIndex`/`IdFilter` 限定后缀（如仅 `*.xdef`/`*.xpl`/`*.xlib`），或至少在 `processAllFileNames` 阶段按扩展名过滤 names 集合。
- **误报排除**: 已核对 `getCachedNopVfsPaths` 的缓存依赖与调用点（NopVirtualFileReference.getVariants、getCachedNopXDefVfsPaths 等），确认无更窄的预过滤。

### [P3] XdslResourceTransformer 用 printStackTrace 输出构建错误

- **文件**: `nop-dev-tools/nop-maven-shaded-plugin/src/main/java/io/nop/maven/plugin/shaded/XdslResourceTransformer.java:52-61`
- **维度**: D4
- **证据**:
```java
} catch (NopException e) {
    e.printStackTrace();
    throw e;
}
```
- **现状**: Maven 插件内向 stderr 打印栈轨迹后再抛出。
- **风险**: 输出绕过 Maven 日志体系（不遵循 -q/-e/--batch-mode），污染构建日志；且 `canTransformResource` 对所有 `.xml` 都返回 true，任意非良构 XML 资源都会走到这里导致 shade 构建直接失败。
- **建议**: 用 `AbstractMojo` 的 Log 或 SLF4J 记录后抛出。
- **误报排除**: 已确认这是全模块唯一 printStackTrace（grep 验证）。

### [P3] XLangDebuggerInitializer.initialize 吞掉初始化异常，调试器静默失效

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/initialize/XLangDebuggerInitializer.java:64-80`
- **维度**: D4
- **证据**:
```java
try {
    debugger = createDebugger();
    ...
    server.start();
    ...
    EvalExprProvider.registerGlobalExecutor(new DebugExpressionExecutor(debugger));
} catch (Exception e) {
    LOG.error("nop.debugger.init-fail", e);   // 仅打日志，无任何用户可见反馈
}
```
- **现状**: 端口被占用等情况导致初始化失败时仅记 error 日志，应用照常运行且无调试能力。
- **风险**: 用户开启 `nop.xlang.debugger.enabled=true` 后端口冲突，调试器不工作但无显式失败提示，排查成本高。
- **建议**: 失败时至少通过 notifier/状态位暴露初始化失败原因。
- **误报排除**: 已确认 ICoreInitializer.initialize 的异常在平台侧同样只被日志化，此处叠加了本地吞异常。

### [P3] ProjectFileHelper.nopVfsPathCaches 静态 Map 以 Project 为键，项目关闭后泄漏

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/utils/ProjectFileHelper.java:49,170-187`
- **维度**: D2
- **证据**:
```java
private static final Map<Project, CachedValue<Collection<String>>> nopVfsPathCaches = new ConcurrentHashMap<>();
...
return caches.computeIfAbsent(project, (p) -> CachedValuesManager.getManager(p).createCachedValue(...));
```
- **现状**: 静态 Map 强引用 Project 与其 CachedValue，项目关闭后条目永不移除。
- **风险**: 多项目反复打开/关闭的 IDE 会话中缓慢累积（经典 IDEA 插件内存泄漏模式，量级: 每项目一个条目 + 缓存集合）。
- **建议**: 改用 `project.putUserData`/`project.getService` 承载缓存，或注册项目关闭监听清理。
- **误报排除**: 已核对全类无 remove 调用。

### [P3] SuspendedThread.suspended 非 volatile，跨线程可见性无保证

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/SuspendedThread.java:38,51-53`
- **维度**: D3
- **证据**:
```java
private boolean suspended;                 // 非 volatile

public void setSuspended(boolean suspended) { this.suspended = suspended; }
// XLangDebugger.doSuspend 在业务线程置 true；getSuspendedThreads 在 RPC 线程读取 isSuspended()
```
- **现状**: `XLangDebugger.doSuspend` 的注释声称通过全局 `suspended` volatile 写建立 happens-before，但 `getSuspendedThreads()` 并不读取该全局标志，只遍历 map 检查 `thread.isSuspended()`，不存在可见性屏障。
- **风险**: RPC 查询线程列表时可能读到过期的 false，线程列表偶发为空/不全（UI 显示与实际挂起状态短暂不一致）。
- **建议**: 将 `suspended` 声明为 volatile。
- **误报排除**: 已核对 `getSuspendedThreads`（XLangDebugger.java:219-229）未读取任何 volatile 字段。

### [P3] PsiClassHelper.getField 未处理 findClass 返回 null

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/utils/PsiClassHelper.java:220-223`
- **维度**: D1
- **证据**:
```java
public static PsiField getField(PsiElement context, String className, String fieldName) {
    PsiClass clazz = findClass(context, className);
    return clazz.findFieldByName(fieldName, true);   // clazz 可能为 null
}
```
- **现状**: 调用方 `XLangDictOptionReference.resolveInner`（XLangDictOptionReference.java:69）传入缓存的 `dictOpt.className`；字典有缓存而类后来不可解析（依赖被移除等）时 `findClass` 返回 null。
- **风险**: 引用解析（高亮常驻路径）内 NPE，IDE 报内部错误；窗口窄（需要字典缓存与类解析状态不同步）。
- **建议**: 判空返回 null。
- **误报排除**: 已核对 `JavaPsiFacade.findClass` 对未知名返回 null 且此处无其他判空。

### [P3] ProjectResourceComponentManager.runWhenDependsChanged 返回 null 而不执行任务

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/resource/ProjectResourceComponentManager.java:124-127`
- **维度**: D8
- **证据**:
```java
@Override
public <T> T runWhenDependsChanged(String resourcePath, Supplier<T> task) {
    return null;   // 基接口语义是执行任务并返回结果
}
```
- **现状**: 直接返回 null，不调用 `task.get()`，也未委托给 `getImpl()`。
- **风险**: 任何经插件内组件管理器调用该方法的平台代码（如依赖变更回调注册）拿到 null 结果且任务被静默丢弃，行为与 `IResourceComponentManager` 契约不符。
- **建议**: 至少执行 `task.get()` 或抛 UnsupportedOperationException 使契约违背显性化。
- **误报排除**: 已核对基接口默认实现语义（nop-core 的 ResourceComponentManager 中为执行 task）。

### [P3] XLangReferenceHelper.getRegisteredStdDomains 反射访问私有字段

- **文件**: `nop-dev-tools/nop-idea-plugin/src/main/java/io/nop/idea/plugin/lang/reference/XLangReferenceHelper.java:302-316`
- **维度**: D8
- **证据**:
```java
Field field = registry.getClass().getDeclaredField("domainHandlers");
field.setAccessible(true);
List<String> result = new ArrayList<>(((Map<String, ?>) field.get(registry)).keySet());
...
} catch (Exception ignore) { return new ArrayList<>(); }
```
- **现状**: 依赖 `StdDomainRegistry` 私有字段名 `domainHandlers`，重命名即静默退化为空列表（catch ignore）。
- **风险**: 上游字段改名后 std-domain 补全/引用静默失效，无任何报错，属于脆弱契约耦合。
- **建议**: 为 `StdDomainRegistry` 提供公开的 registered names 访问器。
- **误报排除**: 已确认 `StdDomainRegistry` 现有字段名为 `domainHandlers`（当前可用，属维护性风险而非现行 bug）。

### [P3] IDebugger 实现可返回 null 但接口契约未声明 @Nullable

- **文件**: `nop-dev-tools/nop-api-debugger/src/main/java/io/nop/api/debugger/IDebugger.java:70-72`（实现: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java:302-326`）
- **维度**: D8
- **证据**:
```java
// IDebugger: 返回类型无 @Nullable
List<DebugVariable> getScopeVariables(@Name("threadId") long threadId);
List<DebugVariable> getFrameVariables(@Name("threadId") long threadId, @Name("frameIndex") int frameIndex);

// XLangDebugger 实现:
List<DebugVariable> vars = null;
if (thread == null) return null;   // 线程不存在时返回 null 而非空列表
```
- **现状**: 两个 List 返回型方法在找不到挂起线程时返回 null；`expandExprValue` 等其余方法返回空集合，风格不一致。
- **风险**: 与 P1（XLangStackFrame NPE）直接关联: 客户端（IDEA 插件）按非 null 契约消费。跨进程 RPC 场景下契约歧义会持续诱发 NPE。
- **建议**: 统一返回空集合，或在接口上标注 `@Nullable`。
- **误报排除**: 已核对实现与客户端（XLangStackFrame.computeChildren）均未判 null。

### [P3] 调试器 RPC 服务无鉴权（默认 localhost + 默认关闭缓解）

- **文件**: `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/initialize/XLangDebuggerInitializer.java:55-77`（绑定配置: `nop-network/nop-socket/src/main/java/io/nop/socket/ServerConfig.java:16`）
- **维度**: D5
- **证据**:
```java
ServerConfig config = new ServerConfig();
config.setPort(CFG_XLANG_DEBUGGER_PORT.get());   // 未显式 setHost，默认 127.0.0.1
...
server.addServiceImpl(IDebugger.class, debugger);
```
- **现状**: 调试 RPC 暴露 `getExprValue`（任意表达式编译+求值）等能力，协议无任何认证。缓解因素: `ServerConfig.host` 默认 `NetHelper.LOCALHOST4()`，且 `CFG_XLANG_DEBUGGER_ENABLED` 默认 false。
- **风险**: 用户若将 host 显式配置为 `0.0.0.0`（或容器/共享环境下），本机外任意可达者可远程执行表达式求值（等同 RCE 面）。默认配置下风险不触发。
- **建议**: 在文档/配置描述中明确警示 host 不要设为非回环地址；或为非回环绑定增加显式确认/鉴权。
- **误报排除**: 已核对 `ServerConfig` 默认 host 与 `XLangConfigs` 中 enabled 默认值（false）、端口 12345。

## 已排查未立项项（避免后续重复排查）

- `XLangTag.getXlibTagMeta`（XLangTag.java:247）`getAttribute(FROM_NAME).getValue()` 疑似 NPE——已排除: `XlibTagMeta.findXlibImportTag`（157-160 行）保证返回的 import 标签必带非 null from 值。
- `XLangCompletionContributor.doFillCompletion` 中 `parentTagDefNode.getChildren()` 潜在 NPE——已排除: 该类在 plugin.xml 中已被注释掉（第 127 行），当前未注册，属死代码。
- `XLangTagMeta.createForChildTag` 中 `getChildDefNode` 返回 null 后直接 `.isUnknownTag()` 的 NPE——由 `create()` 的 catch(Exception) 转为 errorTag 降级，不会外泄崩溃（错误消息不准确，未立项）。
- `XLangDebugProcess.debuggerNotification` 中 `getTopElement()` 理论可空——服务端 `SuspendedThread.getStackTrace` 保证至少 1 帧，不可达。
- D7（Nop 平台约定）: 4 个模块内无 `_vfs` beans.xml、无 `@Inject`/`@InjectValue`/bean 扫描用法（IDEA 插件走平台 Service/EP 体系，xlang-debugger 走 `META-INF/services` SPI），未发现 Nop IoC 约定违背；未发现 bare `new RuntimeException`（唯一 `printStackTrace` 见 P3）。
