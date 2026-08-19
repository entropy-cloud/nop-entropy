# nop-xlang 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-kernel/nop-xlang
- 文件数: 868（src/main/java，实际统计；任务描述约 919，以 `find | wc -l` 为准）

## 覆盖范围声明

- **方法**: 先全模块 grep 可疑模式（空 catch、`new RuntimeException`、`printStackTrace`、可变 static 集合、`synchronized`、`Thread.sleep`、`ThreadLocal`、`ConcurrentHashMap`、InputStream/Reader），每个命中点 Read 上下文验证；再按风险深读核心链路。
- **深读（逐行级）**: Delta 合并链（`delta/DeltaMerger`、`ChildNodeMap`、`OverrideHelper`、`DeltaDiffer`、`xdsl/XDslExtender`、`XDslSource`、`XDslValidator`、`XDslCleaner` 调用方、`DslNodeLoader`）；xdef 链（`xdef/impl/XDefRefResolver`、`XDefinition`、`XDefMergeLoader`、`XDefMergeOptions`、`xmeta/SchemaLoader`、`xmeta/impl/ObjMetaRefResolver`）；Xpl 编译与标签运行时（`xpl/impl/XplCompiler`、`xpl/xlib/XplLibTagCompiler`（含 LazyCompiledFunction）、`xpl/tags/IncludeTagCompiler`、`xpl/utils/XplParseHelper`、`expr/ExprEvalHelper`、`functions/TemplateMacroImpls`）；表达式解析与执行（`expr/simple/AbstractExprParser`、`AbstractPredicateExprParser`、`TypeDefinitionParser`、`feature/FeatureConditionEvaluator`、`feature/XModelInclude`、`exec/` 抽查 ForIn/ForOf/GetProperty/SetProperty 链/EscapeOutput/Literal 等 10+ 个热点 Executable）；缓存与注册表（`xdsl/XDslParseHelper`、`expr/SimpleExprHelper`、`xdef/domain/StdDomainRegistry`、`script/ScriptCompilerRegistry`、`scope/XLangCompileScope`、`api/XLang`、`api/XLangCompileTool`、`api/DefaultFunctionProvider`、`utils/RefResolver`、`janino/JaninoScriptCompiler`、`xmeta/validate/SchemaBasedValidator`）。
- **抽查/浏览**: `xdsl/AbstractDslParser`、`GenericDslParser`、`XDslExtracter`、`xpl/impl/XplTaskLoader`、`xpl/loader/*`、`compile/` 包（BuildExecutableProcessor 错误处理骨架）、`functions/GlobalFunctions`、`xpkg/`。
- **未深读**: antlr 生成物（`parse/antlr/XLangLexer|XLangParser`，生成代码）、`ast/_gen` 与 `xmeta/_gen` 生成物、`xpath/` 求值器细节、`xt/` 变换实现、`xmeta/layout|mapper|jsonschema`、`filter/BizExprHelper` 仅浏览。
- **测试代码不在范围**，但为验证可达性查阅了个别测试（`TestFeatureConditionEvaluator`、`TestXDefMergeLoader`）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 3 |
| P3 | 10 |

## 发现列表

### [P1] feature:off 分支误用 feature:on 属性求值，表达式形式的 feature:off 完全失效

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/feature/XModelInclude.java:117-127`
- **维度**: D1
- **证据**:
```java
if (!StringHelper.isBlank(offAttr.asString())) {
    String off = offAttr.asString();

    if (StringHelper.isValidConfigVar(off)) {
        if (ConvertHelper.toTruthy(AppConfig.var(off)))
            return false;
    }

    if (evaluator.evaluate(onAttr.getLocation(), onAttr.asString()))   // 应为 offAttr
        return false;
}
```
- **现状**: off 分支缺少 `else`，且表达式求值用的是 `onAttr.getLocation()/asString()` 而非 `offAttr`。推导两种触发形态：
  1. `feature:off="<表达式>"`（非 config var）：off 表达式从未被求值；onAttr 通常为 `ValueWithLocation.NULL`，`asString()` 返回 null，`AbstractExprParser.parseExpr(loc, null)` 对空串直接返回 null，`toTruthy(null)=false` → 节点永远不会因 off 条件被删除；
  2. `feature:on="<表达式>"` + `feature:off="<config var>"` 且 config var 为假（未开启关闭开关）：on 分支通过后落入 `evaluator.evaluate(onAttr...)` 为 true → `return false`，节点被**错误删除**。
- **风险**: `XModelInclude.processNode` 是所有 XML/DSL 加载的必经路径（`DslNodeLoader.loadFromResource/processDslNode`、`IncludeTagCompiler`）。feature 开关裁剪结果错误会静默传导到所有合并后的模型（应删的节点保留、不该删的被删）。
- **建议**: 改为 `else if (evaluator.evaluate(offAttr.getLocation(), offAttr.asString())) return false;`，并补一个能区分正确/错误实现的用例。
- **误报排除**: 已核对 `AbstractExprParser.parseExpr`（`StringHelper.isEmpty(s)` 返回 null，不会 NPE）；现有测试 `TestFeatureConditionEvaluator.testVirtualNode` 中 `feature:off='test'` 求值为空串→假，正确实现与错误实现输出相同，测试恰好掩盖该缺陷；当前仓库资源中 feature:off 仅用于 config var 单属性场景（结果碰巧正确），表达式形式尚无使用，故定为 P1 而非 P0。

### [P2] XDefMergeLoader.define 模式下重复引用同一外部 xdef 产生悬空 xdef:ref 名称

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefMergeLoader.java:191-224`
- **维度**: D1
- **证据**:
```java
private void convertToDefine(XNode node, String refPath) {
    String defineName = generateDefineName(refPath);          // 重名时自动改为 Foo2/Foo3...
    if (!collectedDefines.containsKey(defineName)) {
        if (loadedPaths.add(refPath)) { ... collectedDefines.put(defineName, defineNode); }
    }
    node.setAttr("xdef:ref", defineName);                      // 第二次引用时指向不存在的 Foo2
}
```
- **现状**: `generateDefineName` 基于 `collectedDefines` 已有 key 做去重。第二次引用同一 `refPath` 时：生成名撞上第一次的 define（如 `Foo`）→ 改名为 `Foo2`；`containsKey("Foo2")` 为 false 进入 if，但 `loadedPaths.add(refPath)` 失败跳过加载，`Foo2` 永远不会被放入 `collectedDefines`；最后节点被设置为 `xdef:ref="Foo2"` —— 指向一个不存在的 define。
- **风险**: `forMetaModel()` 模式（`inlineXDefRef=false`）输出给 AI/元模型消费方的合并 xdef 含悬空引用，模型静默错误。当前仓库内该类仅测试使用，但属于 main 代码公开 API。
- **建议**: 先按 `refPath -> defineName` 建立映射去重（命中已加载路径时直接复用首次生成的名称），名称去重只应在真正首次加载时进行。
- **误报排除**: 已核对 `generateDefineName`（259 行 while 循环确实会为重复路径改名）与 `loadFromResource` 的状态清理逻辑（每次 load 前 clear，不跨请求残留）；`TestXDefMergeLoader` 仅做导出+计数断言，不校验引用完整性，无法暴露此问题。

### [P2] XDefMergeLoader.loadRefNode 吞掉所有异常且无日志，静默输出不完整模型

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefMergeLoader.java:235-243`
- **维度**: D4
- **证据**:
```java
private XNode loadRefNode(String refPath) {
    try {
        IResource refResource = VirtualFileSystem.instance().getResource(refPath);
        XNode refNode = DslNodeLoader.INSTANCE.loadFromResource(refResource).getNode();
        return transformNode(refNode);
    } catch (Exception e) {
        return null;                       // 无日志、无 cause 传播
    }
}
```
- **现状**: 引用的 xdef 加载失败（路径错误、schema 缺失、解析异常等）时静默返回 null，调用方（`inlineXDefRef`/`convertToDefine`）把 null 当"无内容"处理，产出缺失整棵子树的合并结果。
- **风险**: 错误被完全掩盖，排障无从下手；输出的元模型不完整却无任何信号。
- **建议**: 至少 `LOG.warn("...", e)` 保留异常与路径信息；更好的做法是区分"资源不存在"（可容忍）与"解析失败"（应失败）。
- **误报排除**: 确认这是全模块唯一一个完全无日志的 catch（模块内其余 59 处 catch 均有传播或日志）；非有意设计的容错（javadoc 无相关说明）。

### [P2] XDslExtender.loadSource 在 def 为 null 时对 yaml 扩展路径 NPE

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java:320-338`
- **维度**: D1（NPE）、D4（错误信息丢失）
- **证据**:
```java
private XDslSource loadSource(IXDefinition def, String path, IEvalScope genScope) {
    XNode node;
    if (path.endsWith(ResourceConstants.FILE_POSTFIX_YAML)) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        Object bean = JsonTool.parseBeanFromResource(resource, JObject.class, true);
        node = DslModelHelper.dslModelToXNode(def.resourcePath(), bean);   // def 可能为 null
```
- **现状**: `buildSource`（264-268 行）在 `def==null` 时尝试 `loadXDef(node)`，若节点无 `x:schema` 则 def 保持 null 传入 `loadSource`；yaml 分支直接 `def.resourcePath()` NPE。非 yaml 分支（`XModelInclude.loadActiveNode`）与后续 `transformNode(node, def, ...)` 均容忍 null，唯独此行不容忍。
- **风险**: 触发条件：无 schema 定义的节点上 `x:extends` 指向 `.yaml` 文件（如 `extendsSub` 遇到未知名空间子节点携带 x:extends 时 subDef 为 null）。表现为裸 NPE，无 NopException 的位置/路径上下文。
- **建议**: yaml 分支前判空并抛 `ERR_XDSL_NO_SCHEMA` 类带上下文异常（或用 `node.resourcePath()` 兜底）。
- **误报排除**: 已核对 `buildSource`/`extendsSub` 的 def 传递链确认 null 可达；`loadXDef` 对无 schema 返回 null 而非抛错。

### [P3] DeltaMerger.mergeChildren uniques 分支为死代码且内部 data/unique 变量混用

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/delta/DeltaMerger.java:306-315`
- **维度**: D1（潜伏）、维护性
- **证据**:
```java
} else if (data.uniques != null) {
    for (ChildNodeMap.NodeData unique : data.uniques.values()) {
        if (unique.bIndex >= 0) {
            xb.replaceChild(data.bIndex, data.node);      // 应为 unique.bIndex / unique.node
            if (bIndexes != null) {
                bIndexes[data.bIndex] = data.aIndex;      // 应为 unique.*；此处 data.bIndex 必为 -1
            }
        }
    }
}
```
- **现状**: `ChildNodeMap.build` 从不填充 `NodeData.uniques`（`addUnique`/`makeData` 全仓库无调用方），`mergeMap` 仅在 `data.node==null` 时触及 uniques，而 ChildNodeMap 构造的 NodeData node 恒非空，故该分支当前不可达。若未来复活该结构，`data.bIndex==-1` 会直接数组越界。
- **风险**: 潜伏的复制粘贴错误，误导后续维护。
- **建议**: 删除死分支，或修正为 `unique.bIndex/unique.node/unique.aIndex` 并补充用例。
- **误报排除**: 已 grep 确认 `addUnique`、`makeData` 无任何调用方；`DeltaDiffer`（唯一另一处 ChildNodeMap 使用者）同样不构造 uniques。

### [P3] LazyCompiledFunction 状态机在异常路径下可能永久卡在 IS_COMPILING，且缓存异常对象反复重抛

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/xlib/XplLibTagCompiler.java:691-744`
- **维度**: D3、D4
- **证据**:
```java
public synchronized void compile() {
    if (compileException != null) throw compileException;   // 重抛同一异常实例，栈帧被反复覆盖
    if (compiled) return;
    compiled = true;                                         // 先置位
    ResourceComponentManager.instance().collectDependsTo(deps, () -> { ... });
```
- **现状**: (1) `compiled=true` 在编译前设置，若 `collectDependsTo` 本身在 inner try 之外抛出（依赖收集机制异常），则 `compiledFn`/`compileException` 均为 null，后续 `compile()` 静默返回而 `invoke()` 永远抛 `ERR_XPL_TAG_FUNC_IS_COMPILING`；(2) `compileException` 缓存后每次重抛同一 NopException 实例，后续线程看到的堆栈是首次抛出线程被覆盖后的栈；(3) `invoke()` 无锁读 `compiledFn`/`compileException`（非 volatile），依赖组件缓存的安全发布，主流程成立。
- **风险**: 低概率下标签函数进入不可恢复状态；错误堆栈信息误导。
- **建议**: `compiled` 在成功后置位；编译异常可重新编译或至少包装新异常抛出；字段声明为 volatile。
- **误报排除**: 已确认主流程 `parseTag -> lazyCompile()`（208 行）与 `getFunctionModel -> lazyCompile()`（633-639 行）在发布前同步编译，正常路径无可见性问题。

### [P3] TypeDefinitionParser 使用 bare RuntimeException / UnsupportedOperationException

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/simple/TypeDefinitionParser.java:379-380, 386-390, 402-404`
- **维度**: D4、D7
- **证据**:
```java
} catch (Exception e) {
    throw new RuntimeException("Failed to create " + typeName + " type", e);
}
...
throw new UnsupportedOperationException(
    "Complex " + typeName + " type not fully supported yet: " + right.getClass().getSimpleName());
```
- **现状**: 这是全模块唯一的 `new RuntimeException`（grep 确认）。位于框架核心的表达式类型解析器，违背"NopException + ErrorCode"两档错误处理策略，且无 source location。
- **风险**: 触发路径不现实（反射实例化 TypeNode 失败/嵌套复合类型），主要是规范违背。
- **建议**: 换成 `NopEvalException` + ErrorCode + `.source(loc)`。
- **误报排除**: 无（grep 全模块仅此一处）。

### [P3] ObjMetaRefResolver.getRefSchema 错误分支把 null 作为 source 传入

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/impl/ObjMetaRefResolver.java:172-173`
- **维度**: D4
- **证据**:
```java
if (refDef == null)
    throw new NopException(ERR_XMETA_UNKNOWN_REF).param(ARG_REF_NAME, ref).source(refNode);  // refNode 此时必为 null
```
- **现状**: 抛错处 `refNode` 尚未赋值（恒为 null），应为 `.source(schema)`。错误丢失节点位置信息。
- **风险**: 仅影响报错质量（该分支本身几乎不可达，loadXMeta 为 null 时）。
- **建议**: 改为 `.source(schema)`。
- **误报排除**: 已读上下文确认变量流；对比同类 `XDefRefResolver.loadRefNode` 用的是 `.source(node)`，确属笔误。

### [P3] defTypeCache 全局缓存导致 XDefTypeDecl 位置信息指向首次解析处

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslParseHelper.java:337-368`
- **维度**: D4、D6
- **证据**:
```java
static final ICache<String, XDefTypeDecl> defTypeCache = LocalCache.newCache("def-type-parse-cache", newConfig(1000));
...
return defTypeCache.computeIfAbsent(text, k -> new XDefTypeDeclParser().parseFromText(loc, text));
```
- **现状**: 缓存 key 只有类型文本，value 携带首次调用方的 `SourceLocation`。后续其它文件命中缓存得到的是别人的位置，校验失败时报错位置漂移到无关文件。
- **风险**: 诊断误导；不影响语义正确性。
- **建议**: 缓存不携带 loc 的解析结果，或在抛错时用使用点 loc 覆盖。
- **误报排除**: 已确认 `XDefTypeDecl` 持有 loc 字段且校验错误使用它；缓存 cap 1000，无泄漏风险。

### [P3] SimpleExprHelper 编译缓存统一传 loc=null，运行期错误无位置信息

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/SimpleExprHelper.java:19-24`
- **维度**: D4
- **证据**:
```java
static final ICache<String, ExprEvalAction> exprCache = LocalCache.newCache("simple-expr-compile-cache",
        newConfig(1000), expr -> compileSimpleExpr(null, expr));
```
- **现状**: 缓存 loader 固定用 null 位置编译，等价文本复用同一 action；这些表达式运行抛错时（如属性不存在）异常无源位置。
- **风险**: 通过 `getCompiledExpr` 走缓存的调用方（如过滤器表达式）出错时排障困难。
- **建议**: 接受缓存键限制的同时，在执行侧允许注入调用点位置。
- **误报排除**: 确认 `LocalCache` loader 签名无 loc 通道，属设计取舍而非误读。

### [P3] SchemaBasedValidator.validateUnion 对 null 值 NPE（静态版有判空，实例版没有）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/validate/SchemaBasedValidator.java:99-102`
- **维度**: D1（潜伏）、D4
- **证据**:
```java
protected void validateUnion(IUnionSchema schema, String bizObjName, String propName, Object value, ...) {
    String subTypeProp = schema.getSubTypeProp();
    Object typeValue = BeanTool.instance().getProperty(value, subTypeProp);  // value==null 时 NPE
```
- **现状**: `validate()` 对 union 分支未判 value 是否为 null 即取属性；`BeanTool` 静态方法 `getProperty(null,..)` 有判空返回 null，但这里直接调实例方法，`BeanToolImpl.getBeanModel(bean)` 中 `bean.getClass()` 对 null 抛 NPE。集合元素为 null（list/map 值为 null）时校验器崩溃而非报校验错误。
- **风险**: 当前仓库内该类仅被自身测试引用（grep 全仓库确认），主链路未触达，故降为 P3；属公开 API 的潜伏缺陷。
- **建议**: 改用 `BeanTool.getProperty(value, subTypeProp)` 静态版或前置判空。
- **误报排除**: 已核对 `BeanToolImpl.getProperty`/`getBeanModel` 无 null 防护；`validateCollection`/`validateMap` 不排除 null 元素，可达性成立（一旦被接入主链路）。

### [P3] 唯一键校验与合并阶段对空键语义不一致（XDef 契约漂移）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslValidator.java:365-381` 与 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/delta/ChildNodeMap.java:211-225`
- **维度**: D8
- **证据**:
```java
// XDslValidator.checkUniqueAttr: 空键直接跳过，多个空键可通过校验
String keyValue = child.attrText(uniqueAttr);
if (StringHelper.isEmpty(keyValue))
    continue;

// ChildNodeMap.addByUniqueAttr: 空键直接抛错
if (StringHelper.isEmpty(key))
    throw new NopException(ERR_XDSL_NODE_UNIQUE_KEY_VALUE_NOT_ALLOW_EMPTY)...;
```
- **现状**: 同一 `xdef:unique-attr` 约束，合并期（有 x:extends 时）空键抛 `ERR_XDSL_NODE_UNIQUE_KEY_VALUE_NOT_ALLOW_EMPTY`，校验期（无 extends 的纯校验路径）空键被静默放过、允许重复。xdef 元模型契约（声明 unique-attr 即必须有键）与实际 DSL 行为漂移。
- **风险**: 无 extends 的模型可携带多个空键节点通过校验，后续一旦被其它模型 extends 立即报错，行为不一致难以理解。
- **建议**: 校验器对 mandatory 的 unique-attr 补空键检查（非 mandatory 保持跳过）。
- **误报排除**: 已核对两条路径的触发条件差异（ChildNodeMap 仅在 DeltaMerger/DeltaDiffer 构建时调用）。

### [P3] DslModelHelper Excel loader 回退分支丢失异常细节，且抛 bare IllegalArgumentException

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/DslModelHelper.java:118-145`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    LOG.warn("nop.xlang.not-support-excel-model-loader:missing-lib={}", "nop-ooxml-xlsx.jar");  // 未带 e
    return null;
}
...
throw new IllegalArgumentException("not support excel model loader");   // 非 NopException，无上下文
```
- **现状**: ServiceLoad 失败原因（不一定是缺 jar）被丢弃；不支持 Excel 加载时抛 bare IllegalArgumentException。
- **风险**: 排障信息缺失；公共 helper 违背错误处理约定（轻微）。
- **建议**: `LOG.warn(msg, e)`；改为 NopException + ErrorCode。
- **误报排除**: 无。

### [P3] TemplateMacroImpls.xpath 宏每次编译都重新解析，未像 jpath 一样走缓存

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/TemplateMacroImpls.java:154-166`
- **维度**: D6
- **证据**:
```java
public static Expression jpath(IXLangCompileScope scope, CallExpression expr) {
    ...
    return Literal.valueOf(expr.getArgument(0).getLocation(), JPath.compileWithCache(tpl));   // 有缓存
}
public static Expression xpath(IXLangCompileScope scope, CallExpression expr) {
    ...
    return Literal.valueOf(expr.getArgument(0).getLocation(), XPathHelper.parseXSelector(tpl)); // 无缓存
}
```
- **现状**: 已核对 `XPathHelper.parseXSelector` 无缓存实现。同一 xpath 字符串在多处/多次模板编译时重复解析。
- **风险**: 仅编译期开销，量级小。
- **建议**: 参照 `JPath.compileWithCache` 增加 LocalCache。
- **误报排除**: 已确认 `XPathHelper` 中无缓存路径。

## 检查过但排除的疑点（误报排除记录）

以下命中点经核实不构成问题，记录备查：

1. `XDslExtender.loadTransformers` 懒初始化 `transformers` 字段无同步 —— `XDslExtender` 每次 `xtend` 都新建实例（`DslNodeLoader.java:95`），无跨线程共享。
2. `StdDomainRegistry`/`ScriptCompilerRegistry`/`XPathOperatorRegistry`/`ObjPropMapperRegistry` 的 ConcurrentHashMap —— 注册集中在初始化期，读多写少，`remove(key,value)` 用法正确，无 check-then-act 组合竞争。
3. `GetPropertyExecutable._cacheGetter` 非 volatile 可变字段（共享编译产物上的运行时写）—— 单引用赋值的幂等良性竞争，最坏重复查 getter，结果一致。
4. `DefaultFunctionProvider` 内部 HashMap —— 在本模块仅被 `XLangCompileScope`（每次编译新建）使用，无并发共享。
5. `XLang._provider` 静态非 final 字段 —— 内联初始化后模块内无 setter，等效只读。
6. `XDefinition.getDefaultExtendsNode` 懒建 `defaultsDsl` 缓存字段 —— 冻结后写缓存字段，最坏重复加载一次，良性。
7. `ForInExecutable`/`ForOfExecutable` 的 ExitMode 处理顺序（RETURN 传播、BREAK/CONTINUE 消费）—— 逐行核对无泄漏；Simple 变体仅在 AST 静态分析确认无 break/continue 时选用。
8. `XplCompiler._parseTag` 的 outputMode/ignoreTag 等 scope 状态 restore —— try/finally 配对完整；scope 为编译期独享对象，异常即整体废弃。
9. `OverrideHelper` 合并映射表中作者自注 `// 存在问题` 的条目（MERGE+APPEND→REPLACE 等）—— 已知有意的结合律近似，语义与 `x:override` 文档行为一致，不计新发现。
10. `XplCompiler` 640+ 行编译器与 antlr 生成解析器未逐行深读，按覆盖声明如实说明。
11. `RefResolver`/`XDefRefResolver`/`ObjMetaRefResolver` 的 ThreadLocal ResolveState —— inc/dec 在 try/finally 中配对，嵌套解析正确归零清理。
12. 安全面（D5）：`x:extends`/`x:schema`/include 的路径经 `StringHelper.absolutePath` + VFS 虚拟路径约束，`getAttrVPath` 有 `isValidVPath` 校验（拒绝 `..` 等非法形态）；`janino`/XScript 的编译期代码执行能力（`x:gen-extends`、`xpl:exec`）是平台设计前提（DSL 文件属开发者可信资源），未见面向终端用户的不可信表达式执行入口新增面。未发现路径遍历可越出 VFS 命名空间的问题。
13. D2 资源管理：模块主代码无直接 InputStream/Reader/Zip 操作（grep 确认），资源读取统一委托 `ResourceHelper`/`VirtualFileSystem`（nop-core），无流泄漏面。
