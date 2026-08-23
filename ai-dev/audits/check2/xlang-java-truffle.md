# xlang-java-truffle 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-kernel/nop-xlang-java + nop-xlang-truffle + nop-xlang-java-e2e
- 文件数: 14 + 118 + 55 = 187（src/main/java；java-e2e 全部为 `Gen__*` 生成 corpus 类）
- 覆盖范围声明: nop-xlang-java 14/14 文件全文深读；nop-xlang-truffle 118 个文件全部读取或分组 dump（其中约 45 个核心文件逐行深读，其余为同构小叶子节点）；nop-xlang-java-e2e 55 个生成类做代表性逐行核对 + 双清单一致性核对；Truffle 侧结论经 25.2.4 运行时字节码交叉验证。未覆盖区域: truffle-dsl 注解处理器生成的 `XFunctionDispatchNodeGen`（target 生成物）、src/test 全部测试代码（按任务纪律排除）。
- nop-xlang-java：14/14 全文深读（`ExecToJavaTranslator`、`ExecutableTreeFingerprints`、`GeneratedClassManifest`、`GeneratedClassBindingBinder`、`XlangJavaGenTask`、`GeneratedManifestFiles` 等）。
- nop-xlang-truffle：全部 118 个文件均被读取或分组 dump 过类体/execute 体；其中约 45 个核心文件逐行深读（`XLangContextPool`、`XLangTruffleEngine`、`XLangContext`、`XLangLanguage`、`XLangRootNode`、`XLangFunctionRootNode`、`XLangTruffleFunction`、`EvalHandoff`、`XLangTruffleEval`、`TruffleEvalExecutionBackend`、`TranslationCache`、`TreeFingerprints`、`ExecToTruffleTranslator`、`FrameLayout(Mapper)`、`SyntheticSources`、`XLControlFlowException` 族、`XOutputSwapNode`、`XGenNodeNode`、`XForOfNode`、`XForInNode`、`XTryNode`、`XLocalCallNode`、`XFunctionDispatchNode`、控制流/作用域/引用/属性/输出/绑定族节点等）；其余为上述节点的同构小叶子节点（分组核对 execute 体与共享 helper 委托，无独立状态）。
- nop-xlang-java-e2e：55 个 `Gen__*` 为生成产物（源头是 `ExecToJavaTranslator`，已被审计）；对代表性产物（tag 形态 `Gen__test_xlang_e2e_e2e_xlib_Sum`、循环产物 `Gen__test_xlang_e2e_corpus_static_b_ctrl_forin_xpl` 等）做了逐行核对，并核对了双清单（55 条 vs 55 类、Tab 三列、64-hex 指纹）与 reflect-config 的一致性。
- 关键结论均经交叉验证：消费方（`EvalBackendRouter`/`EvalStaticBoundExecutable`/`XplLibTagCompiler.LazyCompiledFunction`）、被调方（`XLangSemantics` 共享 helper、`EvalFrame`、`ExecutableFunction`、`ForOf/ForIn/Switch/CollectText` 解释器实现）以及 Truffle 25.2.4 运行时字节码（`FrameDescriptor$Builder.addSlot` 初始 tag、`FrameWithoutBoxing.verifyIndexedSet/getValue` 的 tag 语义）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 3 |
| P3 | 5 |

## 发现列表

### [P1] Truffle 帧 slot 原始类型 kind 推断导致"未初始化读取"语义与解释器漂移（0/false/0.0 vs null）

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/frame/FrameLayoutMapper.java:{120-133,408-425}`；`nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/nodes/XSlotReadNode.java:{22-24}`
- **维度**: D1（另涉 D8：与解释器契约漂移）
- **证据**:
```java
// FrameLayoutMapper.buildLayout：推断 kind 直接作为 FrameDescriptor 声明 kind
FrameSlotKind kind = usage.inferableKind();
...
builder.addSlot(kind, slotNames[i], meta);

// SlotUsage：全部被跟踪写入都是同族 primitive 字面量即推断 primitive kind
FrameSlotKind inferableKind() {
    return kindReason() == KindReason.INFERRED ? primitiveKind(writeKinds.get(0)) : FrameSlotKind.Object;
}

// XSlotReadNode：javadoc 声称与解释器未初始化语义一致
@Override
public Object execute(VirtualFrame frame) {
    return frame.getValue(slot);   // "未初始化 slot 返回 null，与解释器 EvalFrame 语义一致"
}
```
- **现状**: kind 推断只看**静态跟踪到的写入**是否全为同族 primitive 字面量（`literalFamily`），不要求该写入在运行时先于任何读取执行。经 Truffle 25.2.4 字节码核实：`FrameDescriptor$Builder.addSlot(kind,...)` 把声明 kind 写入初始 tag 数组（`tags[size] = kind.tag`）；`FrameWithoutBoxing.getValue` 按**运行时 tag** 分派——声明为 `Int/Long/Double/Float/Boolean` 的 slot 在首次写入前读取，将分别返回 `Integer(0)/Long(0)/Double(0.0)/Float(0.0)/Boolean(false)`（primitive 数组默认值），而解释器 `EvalFrame` 的 `Object[] stack` 默认为 `null`。
- **风险**: 触发条件现实存在：slot 的全部被跟踪写入均为 primitive 字面量、但控制流允许先读后写，例如
  ```xpl
  let x;
  if (flag) { x = 1 }
  if (x != null) { ... }   // 解释器：flag=false 时 x 为 null，跳过；truffle：x 为 Integer(0)，进入分支
  ```
  同一脚本在解释器/java 后端（`Object $v = null` 局部变量）与 truffle 后端结果不同，违反三后端对拍前提，属于静默的值级语义漂移（非崩溃）。
- **建议**: 保守化推断：仅当 slot 存在**无条件先导写入**（如声明初始化在函数体首部且无跳过路径）才推断 primitive kind；或一律 `FrameSlotKind.Object`（解释器等价），将 primitive kind 保留给可证明安全的 slot；至少修正 `XSlotReadNode` 的 javadoc 契约声明。
- **误报排除**: 已核对（1）`FrameLayoutMapper.SlotScan.onVisitExpr` 只跟踪 `SlotAssign` 等写入，无任何"先写后读"的执行序分析；（2）解释器 `EvalFrame` 构造 `new Object[slotNames.length]` 默认 null；（3）反编译 truffle-api 25.2.4 确认初始 tag = 声明 kind、`getValue` 按 tag 返回装箱 primitive 默认值（`Integer.valueOf(getInt(...))`）；（4）java 后端生成代码入口用 `Object $vN = null`（见 `Gen__test_xlang_e2e_expr_xpl`），与解释器一致，仅 truffle 漂移。

### [P1] TruffleEvalExecutionBackend 翻译失败事件按 sourceKey 单键关联 + remove，并发下可误配：真实求值错误被降级重放 / 翻译失败被硬抛

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/backend/TruffleEvalExecutionBackend.java:{130-148,181-191}`
- **维度**: D3
- **证据**:
```java
// 异常关联判定：命中翻译失败事件 = 单元级降级（第三分支）；否则真实求值错误重抛
TranslationFailureEvent event = takeRecentFailure(sourceKey);
if (event != null)
    return EvalBackendDynamicOutcome.fallback(
            EvalBackendDynamicOutcome.FALLBACK_UNIT_TRANSLATION_FAILURE, event);
if (thrown instanceof RuntimeException)
    throw (RuntimeException) thrown;
...
} catch (RuntimeException e) {
    // 池租借/求值协议异常同样做事件关联（翻译失败可能在协议抛出前已上报）
    TranslationFailureEvent event = takeRecentFailure(sourceKey);
    if (event != null)
        return EvalBackendDynamicOutcome.fallback(...FALLBACK_UNIT_TRANSLATION_FAILURE, event);
    throw e;
}

private void onTranslationFailure(TranslationFailureEvent event) {
    synchronized (recentTranslationFailures) {
        recentTranslationFailures.put(event.getSourceKey(), event);  // 仅按 sourceKey 记录
    }
}
private TranslationFailureEvent takeRecentFailure(String sourceKey) {
    synchronized (recentTranslationFailures) {
        return recentTranslationFailures.remove(sourceKey);          // 消费即删，无归属校验
    }
}
```
- **现状**: 翻译失败事件的记录与消费只以 `sourceKey`（resourcePath 或动态身份键）关联，不区分树指纹/求值请求。同一 sourceKey 可并发承载多个不同树（租户 delta 合并树、资源热更新重载后同路径新树——这正是 `TreeFingerprints` 注释里明确要防的场景）。
- **风险**: 并发窗口内：(a) 线程 B 的**真实求值错误**抢先 `remove` 了线程 A 记录的翻译失败事件 → B 被误判为"单元级翻译失败"走 fallback，`EvalBackendRouter.executeAdjudicated` 随即用解释器**重新执行整棵树**——若 B 在 truffle 侧已产生部分输出/副作用（输出缓冲写入、集合变更），将被重复执行（输出重复）；(b) A 随后 `takeRecentFailure` 得 null → A 的翻译失败被当作真实求值错误**硬抛**，而不是设计约定的"降级解释器"稳态。两方向都破坏第三分支契约。另外外层 `catch (RuntimeException)` 还会把池租借协议违约（`IllegalStateException`）在存在同 sourceKey 陈旧事件时一并吞成 fallback，掩盖接线缺陷红灯。
- **建议**: 事件关联加入归属维度（如按树指纹/`TranslatedEval` 引用或 per-request 令牌），或至少改为"本次求值确实发生在 `TranslationCache.getOrBuild` 抛错路径"的显式返回信号（例如 `TranslatedEval` 携带 translationFailed 标志），不依赖跨线程共享 map 的键碰撞；协议异常（lease/handoff 违约）不应参与事件关联。
- **误报排除**: 已通读 `TranslationCache.getOrBuild`（失败上报 + 原样重抛）、`XLangLanguage.parse`/`reportTranslationFailure`（监听器逐个通知）、`EvalBackendRouter.executeAdjudicated`（fallback 分支确实用解释器重执行 `expr`），确认误配的两个后果链条都真实可达；单线程顺序路径下事件总能在同一请求内被消费，故为并发条件触发而非必现。

### [P2] TruffleEvalExecutionBackend.close() 与惰性 pool() 存在竞态，可泄漏整个 Context 池

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/backend/TruffleEvalExecutionBackend.java:{152-179}`
- **维度**: D3
- **证据**:
```java
@Override
public void close() {
    XLangContextPool currentPool = this.pool;   // 非 synchronized 读取
    this.pool = null;
    if (currentPool != null) {
        currentPool.getLanguage().removeTranslationFailureListener(failureListener);
        currentPool.close();
    }
}

private synchronized XLangContextPool pool() {
    if (unavailableReason != null)
        return null;
    if (pool == null) {
        ...
        XLangContextPool opened = XLangContextPool.open();   // 创建（含 maxSize 个 Context 预热）
        opened.getLanguage().addTranslationFailureListener(failureListener);
        pool = opened;
    }
    return pool;
}
```
- **现状**: `pool()` 全程持锁，但 `close()` 不持锁。交错序列：线程 T1 进入 `pool()`（持锁，`pool==null`，开始 `open()` 预热）；线程 T2 执行 `close()`——此刻读到 `this.pool == null`，直接返回；T1 随后把新建池赋给 `pool`。结果：后端已注销/关闭，但新建的池永远无人关闭。
- **风险**: 泄漏对象为 `maxSize`（缺省 = CPU 数）个 polyglot Context + 共享 Engine 内的编译缓存/线程资源；进程内不可回收（`XLangTruffleEngine` 单例也不关闭）。触发需要"destroy 与首次动态求值并发"，属于关闭期竞态。
- **建议**: `close()` 加 `synchronized`（与 `pool()` 同锁），或将 `pool` 声明前先在锁内完成判空+置闭包标志；`pool()` 在赋值前复查关闭标志。
- **误报排除**: 已核对 `XLangContextPool.open/close`（close 幂等性依赖 `closed` 标志，先置位再回收，被漏关的池无任何兜底回收路径）与 `XLangTruffleBackendInitializer.destroy()`（unregister → close 一次），确认唯一防线上就是这个非同步读。

### [P2] ExecToJavaTranslator 对 Float NaN/Infinity 字面量生成非法 Java 源码（Double 有特判、Float 漏掉）

- **文件**: `nop-kernel/nop-xlang-java/src/main/java/io/nop/xlang/java/translator/ExecToJavaTranslator.java:{2173-2184}`
- **维度**: D1
- **证据**:
```java
if (value instanceof Double) {
    double d = (Double) value;
    if (Double.isNaN(d))
        return "Double.NaN";
    if (d == Double.POSITIVE_INFINITY)
        return "Double.POSITIVE_INFINITY";
    if (d == Double.NEGATIVE_INFINITY)
        return "Double.NEGATIVE_INFINITY";
    return "Double.valueOf(" + d + ")";
}
if (value instanceof Float)
    return "Float.valueOf(" + value + "F)";    // NaN/Infinity 无特判
```
- **现状**: `Float` 分支直接拼接 `String.valueOf(value)`：`Float.NaN` 生成 `Float.valueOf(NaNF)`、`Float.POSITIVE_INFINITY` 生成 `Float.valueOf(InfinityF)`——均不是合法 Java 字面量（`NaN`/`Infinity` 是 `Double`/`Float` 类的静态成员，只能裸写 `Float.NaN`）。
- **风险**: 含 Float NaN/±Infinity 字面量载荷的 xpl 会让 `XlangJavaGenTask` 产出不可编译源码：生成模式下表现为下游 javac 编译失败（报错指向生成文件，难定位根因）；`--check` 模式下表现为产物漂移退出码 1。普通有限 Float（如 `1.5`、`1.0E40`）不受影响（`1.0E40F` 合法）。触发面窄（浮点 NaN/Infinity 通常经运算而非字面量产生），故 P2。
- **建议**: 与 Double 分支对齐补 `Float.NaN/Float.POSITIVE_INFINITY/Float.NEGATIVE_INFINITY` 特判。
- **误报排除**: 已核对 `literal(...)` 全方法无其他 Float 处理路径，且 e2e corpus 无该形态样本（`literal-int/string` 均为常规值），确认是潜伏缺口而非已有覆盖。

### [P2] FrameLayoutMapper 的 slot 用量扫描漏记 ForOf/ForIn/Try 等运行时写入源，kind 推断口径不健全

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/frame/FrameLayoutMapper.java:{166-242}`；对照 `XForOfNode.java:{54-60}`、`XTryNode.java:{45-47}`
- **维度**: D1（推断健全性）/ D8（与 `XSlotWriteNode` 的声明 kind 契约）
- **证据**:
```java
// SlotScan.onVisitExpr 只识别这些写入源：
// SlotAssign / Reference* / Init/EnhanceRefSlot / Self* / VarStatus / BindVar / Array/ObjectBinding ...
// —— 没有 ForOfExecutable.getVarSlot()/getIndexSlot()、ForInExecutable.getVarSlot()、
//    TryExecutable.getExceptionSlot() 的写入记录

// 而运行时这些节点用 setObject 无视声明 kind 写入：
frame.setObject(varSlot, useRef ? new EvalReference(var) : var);   // XForOfNode
frame.setObject(exceptionSlot, e);                                  // XTryNode
```
- **现状**: `XForOfNode/XForInNode/XTryNode`（以及 `XOutputSwapNode` 换缓冲回调内的执行）向 slot 写入任意 Object，但这些写入不进入 `SlotUsage.writeKinds`。若同一 slot 另有被跟踪的 primitive 字面量写入（如 `let x = 1; for (x of list) {...}`——`BuildExecutableProcessor.processForOfStatement` 允许循环变量解析到外部既有变量 slot），推断会得出 primitive kind。
- **风险**: 经 25.2.4 运行时字节码核实，`FrameWithoutBoxing.verifyIndexedSet` 不校验声明 kind、setObject 会把运行时 tag 翻回 Object，随后 `getValue` 按 tag 读取一致，**当前不崩溃**；但这依赖"运行时 tag 动态覆盖声明 kind"的实现细节，`XSlotWriteNode` 仍按声明 kind 走 typed setter 强转（`(Integer) v`），整个 primitive-kind 机制的实际安全性建立在扫描口径完整这一前提上——该前提目前不成立，任何后续依赖声明 kind 的优化（static slot、类型特化读取）都会被未跟踪写入源破坏，并与上文 P1 的默认值漂移叠加。判 P2（当前无直接运行时危害的健全性缺口）。
- **建议**: `SlotScan.onVisitExpr` 补记 `ForOfExecutable/ForInExecutable`（varSlot/indexSlot，非字面量族）与 `TryExecutable`（exceptionSlot）写入；或收敛为"出现任何未跟踪写入族即回落 Object"。
- **误报排除**: 已核对 `processForOfStatement`（`var.getResolvedDefinition()` 可指向外部变量 slot，`SlotAssign` 与 for-of 可共享 slot）、`XSlotWriteNode` 的 kind 分派 typed setter、以及 `FrameWithoutBoxing` 字节码确认现状不抛异常——本条按"健全性缺口"而非"崩溃 bug"定级。

### [P3] 共享单例节点 XBreakNode/XContinueNode.INSTANCE 被 setSourceSection 反复覆写（含并发写）

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/translate/ExecToTruffleTranslator.java:{758-761,871}`；`nodes/XBreakNode.java:{12-22}`
- **维度**: D1（诊断信息失真）/ D3（无同步共享可变状态）
- **证据**:
```java
} else if (node instanceof BreakExecutable) {
    result = XBreakNode.INSTANCE;
} else if (node instanceof ContinueExecutable) {
    result = XContinueNode.INSTANCE;
}
...
result.setSourceSection(SyntheticSources.sectionOf(node.getLocation()));
```
- **现状**: 翻译器把共享静态单例作为 `result` 后仍统一调用 `setSourceSection`，单例的 `section` 字段被每个翻译单元的每个 break/continue 节点覆写；`TranslationCache` 允许锁外并行翻译，多线程同时写该字段。
- **风险**: 单例上的 source section 永远指向"最后翻译的那个 break"，错误定位/诊断串源；无同步写的可见性问题为良性（引用写不可撕裂）。当前因子节点均为普通 final 字段（无 `@Child`/adoption）才未与 Truffle 节点父子机制冲突。
- **建议**: 单例不设 section（genExpr 对 INSTANCE 提前返回，与 `XNullNode` 的 null-node 早退对齐——后者已在 `node == null` 早退中规避了该问题）。
- **误报排除**: 已核对 `XNullNode.INSTANCE` 路径在 `genExpr` 开头早退不受影响、`XExprNode.setSourceSection` 为普通字段赋值、`XLangRootNode.getSourceSection` 的懒初始化属同类问题但字段私有且值幂等。

### [P3] 生成代码 wrapCallFuncException 首参（stackObj）传 display 字符串，与解释器的节点对象契约漂移

- **文件**: `nop-kernel/nop-xlang-java/src/main/java/io/nop/xlang/java/translator/ExecToJavaTranslator.java:{1607-1611}`
- **维度**: D8
- **证据**:
```java
ctx.line("throw XLangSemantics.wrapCallFuncException(" + displayOf(node) + ", " + ctx.locRef(node)
        + ", " + displayOf(node) + ", " + exVar + ");");
```
- **现状**: 共享 helper 签名为 `wrapCallFuncException(Object stackObj, SourceLocation loc, String display, Exception e)`；解释器传 `this`（可执行节点，`addXplStack` 会展开其定位信息），生成代码两处都传 display 字符串（首参应为 stackObj 语义载体）。
- **风险**: NopException 的 XPL 调用栈条目形态在 java 后端与解释器不一致（字符串 vs 节点对象），仅影响错误报告内容，不影响控制流。
- **建议**: 生成代码首参可传 `locRef` 对应的常量名或约定占位对象并注释语义，或让 helper 对 String stackObj 做适配。
- **误报排除**: 已读 `XLangSemantics.wrapCallFuncException` 实现（`addXplStack(stackObj)`）与 `CallFuncExecutable/CallFuncWithClosureExecutable` 解释器调用点（传 `this`），确认为形态漂移而非功能错误。

### [P3] XLangTruffleFunction.invoke/callN 忽略调用方传入的 scope，函数体作用域完全依赖求值窗口

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/nodes/XLangTruffleFunction.java:{39-52}`
- **维度**: D8
- **证据**:
```java
@Override
public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
    return callTarget.call(callArguments(args == null ? new Object[0] : args, scope));
}
// callArguments 不使用 scope；函数体内作用域读取节点走
// XLangLanguage.currentContext().requireEvalScope()
```
- **现状**: 解释器 `ExecutableFunction.invoke` 用**传入的 scope** 构建 `EvalRuntime` 执行函数体；truffle 函数值忽略 `scope`，体内 `XScopeReadNode` 等从当前 Context 的求值窗口取 scope。窗口内混合调用（解释器代码以派生 scope 调用 truffle 函数值）时，派生 scope 的局部变量对函数体不可见；窗口外调用直接 `IllegalStateException` fail-fast（设计已声明）。
- **风险**: 边缘场景语义漂移 + 窗口外硬失败；属协议已知约束，正常池化路径（同一窗口 scope）无影响。
- **建议**: 在 javadoc 中把"scope 参数被忽略、以窗口 scope 为准"的契约显式化；或混合调用路径回退 `XLangSemantics.callVarFunction`。
- **误报排除**: 已对照解释器 `ExecutableFunction.invoke`（用传入 scope）与 `XScopeReadNode.execute`（`requireEvalScope()`），确认差异真实存在但受求值窗口协议保护。

### [P3] SyntheticSources.sectionOf 每节点新建 Source 对象，翻译期开销与引擎 Source 数量随节点数线性增长

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/translate/SyntheticSources.java:{21-34}`
- **维度**: D6
- **证据**:
```java
public static SourceSection sectionOf(SourceLocation loc) {
    ...
    StringBuilder content = new StringBuilder(line + col);
    for (int i = 1; i < line; i++)
        content.append('\n');
    for (int i = 1; i < col; i++)
        content.append(' ');
    content.append('^');
    Source source = Source.newBuilder(XLangLanguage.ID, content, loc.getPath()).build();
    return source.createSection(content.length() - 1, 1);
}
```
- **现状**: 每个翻译节点各建一个 Source（内容为逐行/逐列填充的占位串）。大行号/列号节点构造 O(line+col) 字符串；共享 Engine 的 Source 管理（source map、断点索引）承受每单元成百上千个单字符 Source。
- **风险**: 仅翻译期与引擎内存/索引开销，无正确性影响；对 LRU 淘汰后重翻译的单元反复发生。
- **建议**: 按编译单元缓存一个 Source（以资源 path 为名），`createSection` 按 loc 定位行列。
- **误报排除**: 已确认调用点唯一（`ExecToTruffleTranslator.genExpr` 尾部、`XLangRootNode.getSourceSection`），无其他复用机制。

### [P3] XExprNode 子节点以普通 final 字段持有（无 @Child），限制 Truffle 节点机制并使跨树共享成为可能

- **文件**: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/nodes/XExprNode.java:{12-25}`（代表性：`XBinaryOpNode`、`XIfNode` 等全部非 DSL 节点）
- **维度**: D6 / 平台规范
- **证据**:
```java
public abstract class XExprNode extends Node {
    private SourceSection section;
    public abstract Object execute(VirtualFrame frame);
    ...
}
// 例：XBinaryOpNode
private final XExprNode left;   // 非 @Child
private final XExprNode right;
```
- **现状**: 翻译节点全部为手写 programmatic 节点，子节点不标注 `@Child`，仅 `XLocalCallNode` 的 `DirectCallNode` 与 `XVarFunctionCallNode` 的 dispatch 用了 `@Child`。
- **风险**: 无 adoption/父子关系，`replace()`/节点计数/instrumentation 等引擎机制不可用；PE 内联依赖 final 字段常量折叠而非标准节点机制（可用但非典型）；这也是共享 `INSTANCE` 单例未与父子机制冲突的原因（见前述 P3）。属有意的设计取舍（javadoc 自述 programmatic 节点），记录为可维护性/性能隐患而非缺陷。
- **建议**: 若后续追求稳态性能，评估对高频节点改用 `@Child` + DSL；至少维持"绝不引入会 adoption 单例"的纪律。
- **误报排除**: 已核对全部节点字段声明（分组 dump），确认仅两处 `@Child`；`XFunctionDispatchNode` 为 DSL 生成体系（`XFunctionDispatchNodeGen` 由注解处理器生成，不在审计范围）。

## 未发现问题的重点核查项（负面结论备查）

- `XLangContextPool` 租借状态机（CAS AVAILABLE/LEASED/RETIRED + 毒丸关闭 + 预热走 activate 同一路径）自洽；`Lease.close` 的残留检测/防御清空/leave 顺序正确，double-return 与 use-after-close 均 fail-fast。
- `EvalHandoff` ThreadLocal 嵌套 fail-fast、`end()` 在 finally；`XLangTruffleEval.GLOBAL_EVAL_SEQ` 全局唯一化避免共享 Engine parse 缓存串味。
- `GeneratedManifestFiles` try-with-resources 资源关闭、多 jar 聚合冲突 fail-fast、CRLF trim 处理均正确。
- `ExecutableTreeFingerprints`/`TreeFingerprints` 载荷白名单对可翻译节点 fail-fast（防静默弱哈希）；java 侧 SHA-256 与 truffle 侧 64-bit 缓存键口径分工与注释一致。
- `TranslationCache` LRU 锁外翻译 + 重入收敛，并发重复翻译为良性竞争。
- 控制流异常族（XLReturn/Break/Continue）在各 catch(Exception) 包装点的放行盘点（`XTryNode` 放行、函数边界消化、换缓冲 cell 抑制、根节点消费）与解释器 exitMode 语义逐点对上；换缓冲族丢弃 XLReturn 携带值、以收集值为返回值的行为与解释器 `CollectTextExecutable` 逐字对应。
- D5/D7：无 SQL/命令/路径注入面；无 `@Inject private`/`@Value`；两个 Initializer 均经 `META-INF/services` 显式注册。
