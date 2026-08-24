# nop-xlang 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-kernel/nop-xlang
- 文件数: 885（src/main/java 全部），其中非生成文件约 722（排除 `_` 前缀文件与 `parse/antlr/` ANTLR 生成物、`_gen/` 目录）
- 覆盖范围声明: 按风险优先级深读约 58 个核心文件（xdsl/delta 全部核心类：XDslExtender、DeltaMerger、ChildNodeMap、OverrideHelper、XDslSource、DslNodeLoader、XDslValidator、DslModelParser、AbstractDslParser、GenericDslParser、DslXNodeToJsonTransformer、DslBeanModelParser、DslModelToXNodeTransformer；xpl：XplCompiler、OutputParseHelper、Xml/TextOutputTagCompiler、XplLibTagCompiler、XplSlotProcessor、For/IncludeTagCompiler、XLangParseBuffer、XplLibHelper；functions：GlobalFunctions、TemplateMacroImpls 全文；exec：XLangSemantics（约 1500 行全文）、For/ForOf/GenNodeExecutable；expr：XLangExprParser、SimpleExprParser；scope：XLangCompileScope；xdef：XDefMergeLoader、XDefinition、XDefAttribute、XDefinitionParser/XDefRefResolver/XplStdDomainHandlers 关键段；xmeta：SimpleSchemaValidator、SchemaLoader、JaninoHelper；xt：XtTransformCompiler 关键段；backend：EvalBackendRouter 全文；api：XLang、XLangCompileTool；feature：XModelInclude、MetaCfgProcessor；utils：ExprEvalHelper、RefResolver；initialize：RegisterModelDiscovery 关键段；parse：XLangParseHelper 关键段）。其余约 660 文件通过模式扫描覆盖（SimpleDateFormat/ObjectMapper/Random/printStackTrace/System.out/@Inject/Spring import/可变静态字段/subList/get(0)/catch 吞噬/IO 操作全部 grep 过滤后逐条核对，全模块 SimpleDateFormat、@Inject private、Spring 依赖、printStackTrace 均为 0 命中）。未覆盖区域: ast/ 约 140 个模型类与生成式 Visitor/Optimizer、exec/ 其余约 130 个同构小执行器、xpath/ 41 个选择器、xmeta/ 其余 schema 模型类、janino/、xpl/tags 其余标签编译器、parse/antlr（生成物）。`compile/` 三个超大文件（BuildExecutableProcessor 77KB、TypeInferenceProcessor 62KB、LexicalScopeAnalysis 36KB）仅做定向抽检（catch/循环/集合操作模式），未逐行审读。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 1 |
| P2 | 3 |
| P3 | 5 |

## 发现列表

### [P0] AND/OR 全局宏编译结果丢失最后一个参数，AND(a,b) 恒为 false

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/functions/GlobalFunctions.java:328`
- **维度**: D1（逻辑错误/边界条件）
- **证据**:
```java
private static Expression newLogicalExpr(List<Expression> exprs, XLangOperator op) {
    if (exprs.isEmpty())
        return Literal.booleanValue(null, false);

    if (exprs.size() == 1)
        return exprs.get(0);

    Expression expr = exprs.get(0);
    LogicalExpression stm = new LogicalExpression();
    stm.setLocation(expr.getLocation());
    stm.setOperator(op);
    stm.setLeft(expr);
    stm.setRight(newLogicalExpr(exprs.subList(1, exprs.size() - 1), op));  // BUG: 应为 subList(1, exprs.size())
    return stm;
}
```
- **现状**: `exprs.subList(1, exprs.size() - 1)` 把右操作数的递归区间写成了 `[1, size-1)`，丢掉了最后一个表达式。逐步展开：
  - `AND(a, b)`：size=2，右操作数 = `newLogicalExpr(subList(1,1)=[])` = `Literal(false)`，编译结果为 `a && false`，**恒为 false**；
  - `OR(a, b)`：编译结果为 `a || false` = `a`，**b 被完全忽略**；
  - `AND(a, b, c)`：编译结果为 `a && b`，**c 被丢弃**。
- **风险**: `GlobalFunctions` 的全部静态函数由 `XLangCoreInitializer.registerAll`（`initialize/XLangCoreInitializer.java:47`，`EvalGlobalRegistry.instance().registerStaticFunctions(GlobalFunctions.class)`）注册为平台全局函数，任何 XLang/XPL 表达式（规则引擎、权限过滤、模板条件、Excel 式公式等）中调用 `AND(...)/OR(...)` 且参数 ≥2 时都会得到错误的布尔结果，且是静默错误（不抛异常）。这是公开契约"类似于Excel的AND/OR函数"的直接违背。
- **建议**: 改为 `exprs.subList(1, exprs.size())`，并补充 2/3/多参数下 AND/OR 求值的回归测试（当前 nop-xlang 及全仓库 src/main 均无该宏的测试覆盖）。
- **误报排除**: (1) 同文件 `LogFunctions.java:81` 存在同构代码 `expr.getArguments().subList(1, expr.getArguments().size())`（正确写法），对比证实 `size() - 1` 是笔误而非刻意语义；(2) 读取了 `LogicalExpression` 的 setLeft/setRight 语义（左/右操作数二叉递归），确认右子树应包含除第一个元素外的全部元素；(3) 确认 `OR`/`AND`（GlobalFunctions.java:300/309）直接把 `expr.getArguments()` 传入本函数；(4) 确认注册链路 `XLangCoreInitializer` 在平台初始化时全局注册，触发路径现实存在（未在仓库 src/main 内找到直接调用点，但该函数是面向所有 XLang 表达式的公开 API）。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`newLogicalExpr` 递归区间改为 `exprs.subList(1, exprs.size())`（原 `size() - 1` 丢弃最后一个参数）。测试：`TestGlobalFunctions#testAndMacro`/`#testOrMacro`（经 `EvalGlobalRegistry.registerStaticFunctions` + `compileSimpleExpr` 走 `LexicalScopeAnalysis.visitCallExpression` 宏展开全链路，覆盖 2/3 参数 AND/OR 共 10 组真值）。红验证：修复前 `AND(true, true)` 得 false（恒 false）、`OR(false, true)` 得 false（b 被完全忽略），与审计推演的两种错误形态一致。影响面确认为全局表达式宏（XLangCoreInitializer 全局注册），修复后全模块测试回归绿。

### [P1] DslXNodeToJsonTransformer 缺省属性值写入错误的属性名（name 而非 propName）

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/json/DslXNodeToJsonTransformer.java:302`
- **维度**: D1（复制粘贴错误）、D8（与子类实现契约漂移）
- **证据**:
```java
// 节点上缺失的属性需要设置缺省值
defNode.getAttributes().forEach((name, attr) -> {
    if (!node.hasAttr(name)) {
        Object defaultValue = attr.getType().getDefaultValue();
        if (defaultValue == null) {
            obj.addPropDefault(attr.getPropName(), defaultValue);   // 用 propName
        } else {
            // 如果值非空，则需要作为明确的属性保存...
            obj.addProp(name, defaultValue);                        // BUG: 用原始 attr name
        }
    }
});
```
- **现状**: 已解析属性（第 286-291 行）按 `attr.getPropName() ?: name` 作为 DynamicObject 的属性键；而缺失属性的非空缺省值却用原始 XML 属性名 `name` 作为键。`XDefAttribute.propName` 由 `XDefinitionParser.parseAttrs`（第 636 行 `attr.setPropName(buildPropName(name))`）设置，`XDefHelper.buildPropName` 对 kebab-case 名（如 `order-by`）及 `propNs` 命名空间名会返回与 `name` 不同的 camelCase 名。因此当属性名与 propName 不一致、节点未写该属性、且 xdef 类型声明带非空缺省值时，DynamicObject 上会多出一个错误键 `order-by`，而正确键 `orderBy` 缺失。
- **风险**: 走 `DslModelParser.dynamic(true)/forEditor(true)` 与 `GenericDslParser` 的解析路径（如 nop-ooxml-docx 的 Word 模板加载、编辑器 JSON 数据、nop-task-ext demo 均使用）产出的模型对象缺省值丢失/错位；后续转强类型 bean 时该属性为 null 而非缺省值。null 分支（第 299 行）用 `attr.getPropName()`，两分支行为不一致进一步证实是笔误。
- **建议**: 第 302 行改为 `obj.addProp(attr.getPropName() != null ? attr.getPropName() : name, defaultValue);`，与子类 `DslBeanModelParser.java:114`（`beanModel.setProperty(obj, attr.getPropName(), defaultValue)`，正确写法）对齐。
- **误报排除**: (1) 通读 `XDefAttribute`/`XDefHelper.buildPropName`/`StringHelper.xmlNameToPropName`，确认 propName 与 name 可实际不同（kebab-case → camelCase）；(2) 对比子类 `DslBeanModelParser` 同位置代码使用 `attr.getPropName()`；(3) 确认本类的调用方（`DslModelParser.doParseNode0` dynamic/forEditor 分支、`GenericDslParser.doParseNode`、`DslModelHelper.loadDslModelAsJson`）在仓库内有生产/测试调用。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。缺省值非空分支改为 `obj.addProp(attr.getPropName() != null ? attr.getPropName() : name, defaultValue)`，与同方法 null 分支（`getPropName()`）、已解析属性分支（propName ?: name）及子类 `DslBeanModelParser`（`beanModel.setProperty(obj, attr.getPropName(), ...)`）对齐。测试：`TestGenericDslParser#testDefaultAttrUsesPropName`（新 fixture `_vfs/test/test-default-attr-prop-name.xdef`：kebab-case 属性声明 `order-by="string=unknown-order"`，节点不写该属性，走 GenericDslParser dynamic 路径）。红验证：修复前 DynamicObject 上键为原始 XML 名 `order-by`，`prop_has("orderBy")` 为 false——"expected: <true> but was: <false>"（stash 对照复验）。

### [P2] XDefMergeLoader 吞掉 ref 加载异常，产出悬空 xdef:ref 的部分合并模型

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdef/XDefMergeLoader.java:243`
- **维度**: D4（异常吞噬导致静默数据不完整）
- **证据**:
```java
private XNode loadRefNode(String refPath) {
    try {
        IResource refResource = VirtualFileSystem.instance().getResource(refPath);
        XNode refNode = DslNodeLoader.INSTANCE.loadFromResource(refResource).getNode();
        return transformNode(refNode);
    } catch (Exception e) {
        LOG.warn("nop.xlang.xdef.merge-load-ref-fail:refPath={}", refPath, e);
        return null;
    }
}

private void convertToDefine(XNode node, String refPath) {
    String defineName = refPathDefineNames.get(refPath);
    if (defineName == null) {
        defineName = generateDefineName(refPath);
        if (loadedPaths.add(refPath)) {
            XNode refNode = loadRefNode(refPath);
            if (refNode != null) { ... collectedDefines.put(defineName, defineNode); ... }
        }
    }
    node.setAttr("xdef:ref", defineName);   // refNode == null 时仍指向一个从未收集的 define
}
```
- **现状**: 外部 xdef:ref 加载失败（文件缺失、schema 不符等）时仅 WARN 并返回 null。define 模式下 `convertToDefine` 仍执行 `node.setAttr("xdef:ref", defineName)`，但 `collectedDefines` 中不存在该 define，产出悬空引用；inline 模式下 `inlineXDefRef` 提前 return，节点保留指向失败路径的原 `xdef:ref`。此外 `loadedPaths.add(refPath)` 在加载前执行，失败后同一 loader 实例内不会重试。
- **风险**: 该 loader 用于"向 AI 传递完整 xdef 元模型"，静默产出结构不完整的合并模型（引用断裂、子结构缺失），消费方无法感知数据被截断。
- **建议**: 加载失败时抛出 NopException（或至少在结果节点上标记失败信息）；`convertToDefine` 在 refNode == null 时不应改写 `xdef:ref` 为未登记的 defineName。
- **误报排除**: 通读了 `loadFromResource`/`convertToDefine`/`inlineXDefRef`/`loadFromPath` 全部调用链与 `XDefMergeOptions` 分支，确认两条模式都会到达该吞异常路径；确认 `DslNodeLoader.INSTANCE.loadFromResource` 对缺失资源会抛 NopException（被此处的 catch (Exception) 吞掉）。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`loadRefNode` 删除 `catch (Exception)` 吞异常（含 08-22 补的 WARN 日志与随之无用的 LOG 字段），加载失败原样上抛底层 NopException（携带被引用资源路径），`convertToDefine` 的悬空 `xdef:ref` 改写与 `inlineXDefRef` 的断裂引用保留分支随之不可达。测试：`TestXDefMergeLoader#testLoadMissingRefFails`/`#testLoadMissingRefFailsInDefineMode`（新 fixture `merge-missing-ref.xdef` 引用不存在的 `not-exist-ref-target.xdef`，inline/define 双模式，断言 NopException 且异常串含缺失 ref 路径）。红验证：修复前 `assertThrows(NopException)` 失败——"Expected NopException to be thrown, but nothing was thrown"（异常被吞、静默产出部分合并模型）。回归证据：`testMergeAllXDefs` 批量合并修复前后均为 Success 112 / Failed 2（两个失败为根加载既有问题：xdef.xdef duplicate-child、font.xdef UnsupportedOperationException，与本修复无关，见"新发现缺陷"记录）。

### [P2] EvalBackendRouter 在热路径上使用全局 synchronized 记录诊断环

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/backend/EvalBackendRouter.java:302`
- **维度**: D3/D6（全局锁热点）
- **证据**:
```java
public Object executeAdjudicated(IExecutableExpression expr, EvalRuntime rt) {
    ...
    decision.setSourceKey(sourceKeyOf(resourcePath, expr));
    recordDecision(decision);      // 每次求值都执行
    return result;
}

private synchronized void recordDecision(EvalBackendDecision decision) {
    recentDecisions.addFirst(decision);
    while (recentDecisions.size() > DECISION_RING_MAX)
        recentDecisions.removeLast();
}
```
- **现状**: `XLang.execute` 是全平台表达式求值唯一入口；一旦注册了任一后端（`router.isActive()`），每次 `executeAdjudicated`/`executeBoundUnit`/`executeDegradedUnit` 都要在单例 Router 的内置锁上串行执行 `recordDecision`。注册表为空时走 fast-path 无锁（当前缺省形态），问题只在启用 java/truffle 后端时出现。
- **风险**: 高并发下所有线程的表达式求值在一把全局锁上排队（锁内虽短，但频率 = 全部求值次数），成为可伸缩性瓶颈；`getRecentDecisions()` 在同一锁上全量拷贝 128 元素环，诊断读取会加剧争用。
- **建议**: 诊断环改用无锁结构（如 `ConcurrentLinkedDeque` + 容量截断，或按线程分片），或仅按采样/开关启用记录。
- **误报排除**: 通读 `EvalBackendRouter` 全文与 `XLang.execute` 调用点，确认 `recordDecision` 位于每次求值返回前的必经路径且为实例级 synchronized 单点；确认 `XLang.execute` 的 fast-path 分支（registry 为空）不经过该锁，问题限定在启用后端的配置下。

> **处置（fix-ai-check 分支，2026-08-24）**: 裁定暂缓。缺陷属实（`recordDecision` 仍为单例 Router 实例级 synchronized，`getRecentDecisions()` 同锁全量拷贝），但属 xlang-execution 在研子系统的设计决策点：(1) JDK 无精确界限无锁双端队列——`ConcurrentLinkedDeque.size()` 为 O(n)，朴素替换把锁争用换成每次求值 O(128) 节点遍历，需在"计数器近似界限裁剪 / 按线程分片 / 采样记录"间做设计取舍；(2) 诊断环契约（`getRecentDecisions().get(0)`=最近一次裁决、精确 128 界限）被 nop-xlang-java 与 nop-xlang-truffle 两模块约 30 处测试身份断言消费（`TestGeneratedClassBindingBinder`/`TestJavaBackendRoutingScenarios`/`TestTruffleBackendRoutingScenarios` 等，超出本单元可改范围），采样/分片方案会改变该契约；(3) 取舍需要 nop-benchmark-xlang `DynamicCompareBenchmark` 的基准数据支撑而非直觉裁定。影响面：注册表空（当前缺省形态）时 `XLang.execute` fast-path 不经该锁，仅在 java/truffle 后端注册后激活；锁内仅 addFirst + 至多一次 removeLast，属可伸缩性瓶颈而非正确性缺陷，无静默错误结果风险。

### [P2] x:extends / c:include 无循环引用检测，环状引用导致 StackOverflowError

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/XDslExtender.java:321`
- **维度**: D1/D6（无限递归）
- **证据**:
```java
private XDslSource loadSource(IXDefinition def, String path, IEvalScope genScope) {
    XNode node;
    if (path.endsWith(ResourceConstants.FILE_POSTFIX_YAML)) { ... } else {
        ...
        node = XModelInclude.instance().loadActiveNode(path);   // 每次重新解析文件，无缓存无环检测
    }
    node = transformNode(node, def, genScope);
    return buildSource(def, node, node.resourcePath(), genScope);  // 递归
}
```
- **现状**: `buildSource → loadSource → buildSource` 沿 x:extends 链无限递归；`XModelInclude.loadActiveNode` 直接 `XNodeParser.parseFromVirtualPath` 重新读文件，不经过组件缓存，也没有任何路径栈/已访问集合。A extends B、B extends A（或自引用）时直接 StackOverflowError。`IncludeTagCompiler.parseTag`（`xpl/tags/IncludeTagCompiler.java:68`，`XModelInclude.instance().loadActiveNode(src)` 后继续编译）同样无环检测。
- **风险**: 环状引用是配置错误，但后果是裸 StackOverflowError 而非带路径的清晰错误信息，且发生在模型加载期，可能拖垮首次加载线程的栈。与平台其他位置的做法不一致：`XtTransformCompiler`（circularReference）、`RefResolver`（ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE）均实现了显式环检测。
- **建议**: 在 XDslExtender 递归链路上传入已访问路径集合，检测到回边时抛 `ERR_XDSL_*` 循环引用错误；c:include 同理。
- **误报排除**: 通读 `XDslExtender.buildSource/loadSource/extendsSub` 与 `XModelInclude.loadActiveNode/processNode`、`DslNodeLoader`，确认整条链路无 visited 集合，也无组件缓存短路（组件缓存在更外层 `ResourceComponentManager`，`loadActiveNode` 本身绕过缓存）；grep 全模块 cycle/circular 关键字确认 x:extends 路径无检测。

> **处置（fix-ai-check 分支，2026-08-24）**: 裁定暂缓。缺陷属实（本日复核 `buildSource→loadSource` 链路仍无 visited 集合，grep cycle/circular 在 XDslExtender/XModelInclude/IncludeTagCompiler 均 0 命中，环引用仍 SOE），但修复有硬外部依赖与设计决策点：(1) 需新增专用错误码（如 ERR_XDSL_EXTENDS_CIRCULAR_REFERENCE）并同步 i18n 聚合条目——聚合文件在 `nop-runner/nop-cli-core/src/main/resources/_vfs/i18n/{zh-CN,en}/nop-cli-errors.i18n.yaml`，在本单元可改范围之外；复用 `ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE`（"xdef:ref不允许循环引用"）或 `ERR_XT_CIRCULAR_REFERENCE`（"xt转换规则存在循环引用"）会产出错误域的误导消息；(2) 键控语义需按"加载后解析出的物理 resourcePath"而非原始 extendPath 字符串——合法多层 `super:` 链每跳字符串相同（super:/path）但物理文件不同（`DeltaResourceStore.getSuperResource` 逐层解析），按字符串键控会误报环；根路径入栈时机、菱形依赖的 add-on-enter/remove-on-exit 语义需设计确认；(3) 审计提及的 c:include（IncludeTagCompiler）是否共用同一检测需一并裁定。影响面：环引用属配置错误，现状为模型加载期 StackOverflowError（限于加载线程栈），无数据正确性危害。

### [P3] SimpleSchemaValidator 上界校验错误参数复制粘贴错误

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/SimpleSchemaValidator.java:114-122`
- **维度**: D1（复制粘贴错误，仅影响错误信息）
- **证据**:
```java
if (schema.getMax() != null) {
    int cmp = MathHelper.compareWithConversion(v, schema.getMax());
    boolean greater = Boolean.TRUE.equals(schema.getExcludeMax()) ? cmp >= 0 : cmp > 0;
    if (greater) {
        collector.buildError(ERR_SCHEMA_PROP_VALUE_TOO_LARGE)
                ...
                .param(ARG_MIN_VALUE, schema.getMin())              // 应为 ARG_MAX_VALUE/getMax
                .param(ARG_VALUE, value)
                .param(ARG_EXCLUDE_MIN, Boolean.TRUE.equals(schema.getExcludeMin()))  // 应为 ARG_EXCLUDE_MAX/getExcludeMax
                .addToCollector(collector);
    }
}
```
- **现状**: "值过大"错误里填充的是 min 相关参数，max/excludeMax 信息丢失。
- **风险**: 校验错误信息误导用户（显示错误的最小值/排除下界），不影响校验判定本身。
- **建议**: 改为 `ARG_MAX_VALUE/schema.getMax()` 与 `ARG_EXCLUDE_MAX/schema.getExcludeMax()`。
- **误报排除**: 对照同方法内 min 分支（100-112 行）参数写法与 `XLangErrors` 中 ARG_MAX_VALUE/ARG_EXCLUDE_MAX 常量存在性，确认是复制粘贴遗漏；确认 `MathHelper.compareWithConversion` 对 null 安全（null 视为最小），转换失败时不抛 NPE。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。"值过大"错误参数改为 `ARG_MAX_VALUE/getMax()` 与 `ARG_EXCLUDE_MAX/getExcludeMax()`（原为 min 分支复制粘贴的 `ARG_MIN_VALUE/getMin()`/`ARG_EXCLUDE_MIN/getExcludeMin()`；ARG_MIN_VALUE/ARG_EXCLUDE_MIN 仍被 min 分支使用故 import 保留）。测试：新增 `TestSimpleSchemaValidator#testValueTooLargeErrorParams`/`#testExcludeMaxErrorParams`（后者覆盖 excludeMax=true 开区间时 10 本身违规，断言错误参数 excludeMax=TRUE），经 `IValidationErrorCollector` 收集 ErrorBean 断言 params。红验证：修复前 `error.getParams().get("maxValue")` 为 null（填的是 min 参数），两用例均 "expected: <10.0> but was: <null>"。

### [P3] 十六进制字面量未按无符号解析，与二进制字面量行为不一致

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/parse/XLangParseHelper.java:115`
- **维度**: D1（边界条件不一致）
- **证据**:
```java
public static Number hexIntegerLiteralValue(TerminalNode node) {
    ...
    if (text.endsWith("L")) {
        value = Long.parseLong(text.substring(0, text.length() - 1), 16);
    } else {
        value = Long.parseLong(text, 16);          // 有符号解析，≥0x8000000000000000 抛异常
    }
    ...
}

public static Number binaryIntegerValue(TerminalNode node) {
    ...
        value = Long.parseUnsignedLong(text, 2);   // 无符号解析，支持到 0b111...1(64位)
    ...
}
```
- **现状**: 同为整数非十进制字面量，二进制用 `parseUnsignedLong`、十六进制用 `parseLong`，`0xFFFFFFFFFFFFFFFF` 会报转换失败而 `0b` 全 1 的 64 位值可以解析。
- **风险**: 边界值十六进制字面量（高位为 1 的 64 位值）无法编译，报错信息（ERR_CONVERT_TO_TYPE_FAIL）也不符合用户预期。低频。
- **建议**: hex 路径改用 `Long.parseUnsignedLong(text, 16)`。
- **误报排除**: 通读两个方法的完整代码对比；确认 catch(Exception) 后统一转换为转换错误异常，不会抛出裸 NumberFormatException。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`hexIntegerLiteralValue` 两个分支（带/不带 L 后缀）`Long.parseLong(...,16)` 均改为 `Long.parseUnsignedLong(...,16)`，与 `binaryIntegerValue` 的无符号语义对齐（高位为 1 的 64 位十六进制字面量回绕为负 long，同 Java 语言语义——Java 中 `0xFFFFFFFFFFFFFFFFL == -1L` 合法）。测试：`TestXLangParser#testHexLiteralUnsignedParse`（0xFFFFFFFFFFFFFFFF=-1L、0xFF=255L、0x7FFFFFFFFFFFFFFF=Long.MAX_VALUE，经 `compileFullExpr` 全链路）。红验证：修复前 `0xFFFFFFFFFFFFFFFF` 编译期抛 `nop.err.api.convert-to-type-fail`（params value=FFFFFFFFFFFFFFFF、targetType=Long），即审计所述不符合预期的转换失败报错。

### [P3] XplTaskLoader 对空/无效 .xpl 任务文件的 NPE

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/impl/XplTaskLoader.java:21-22`
- **维度**: D1（NPE 风险）
- **证据**:
```java
public XplTaskResult loadObjectFromPath(String path) {
    IResource resource = VirtualFileSystem.instance().getResource(path);
    IEvalAction action = XLang.parseXpl(resource, XLangOutputMode.none);
    Object returnValue = action.invoke(XLang.newEvalScope());   // action 可能为 null
    ...
}
```
- **现状**: `XLang.parseXpl` 内部 `new XplModelParser().parseFromResource(resource)` 对空资源可能返回 null model，`model.getExpr()` 产生的 action 为 null 时第 22 行直接 NPE。
- **风险**: 空任务文件得到裸 NPE 而非带资源路径的解析错误。触发条件为资源内容为空/无法产出表达式，属异常路径。
- **建议**: invoke 前判空并抛 `ERR_COMPONENT_PARSE_MISSING_RESOURCE` 类带路径的 NopException。
- **误报排除**: 读取 `XLang.parseXpl` 与 `XplModelParser.parseFromResource` 返回值链路，确认存在返回 null 的分支（model == null 时 parseXpl 直接返回 null）。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。invoke 前判空，action 为 null 时抛既有错误码 `ERR_COMPONENT_PARSE_MISSING_RESOURCE` + `ARG_RESOURCE_PATH`（按审计建议复用，i18n 已同步，未新增错误码）。测试：新增 `TestXplTaskLoader#testEmptyTaskFileThrowsWithPath`（新 fixture `empty-task.xtask` 内容 `<c:unit/>`——合法 XML 但 body 编译产出空表达式，走 `XplModelParser.doParseResource` action==null → parseXpl 返回 null 分支；经验证：完全空文件在 XML 解析层即抛 `nop.err.commons.io.unexpected-eof` 不到该分支，输出模式 none 下非 c: 命名空间节点抛 not-allow-output）。红验证：修复前 `java.lang.NullPointerException: Cannot invoke "IEvalAction.invoke(...)" because "action" is null`（裸 NPE 无资源路径），assertThrows 断言 "Unexpected exception type thrown, expected NopException but was NullPointerException"。

### [P3] 框架核心多处使用 bare IllegalStateException/IllegalArgumentException 且错误码内嵌在 message 中

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpl/impl/XplCompiler.java:307`（同类：`xmeta/impl/ObjMetaToXDef.java:63,82,138,185`、`initialize/RegisterModelDiscovery.java:287,299`、`xmeta/xjava/JaninoParser.java:146`、`xdsl/json/DeltaExtendsGenerator.java:43`、`delta/DeltaMerger.java:115`、`ast/XLangASTVisitor.java:434` 等）
- **维度**: D4/D7（平台错误处理规范）
- **证据**:
```java
private IXplUnknownTagCompiler getUnknownTagCompiler(XNode node, IXLangCompileScope scope) {
    IXplUnknownTagCompiler tagCompiler = OutputModelHandlers.getHandler(scope.getOutputMode());
    if (tagCompiler == null)
        throw new IllegalStateException(
                "nop.err.xlang.no-default-compiler-for-outputMode:" + scope.getOutputMode() + ",node=" + node);
    return tagCompiler;
}
```
- **现状**: 平台核心模块（按约定应使用 `NopException` + `ErrorCode` + `.param(...)`）存在十余处 bare `IllegalStateException/IllegalArgumentException`，错误码以 `nop.err.xxx:` 前缀硬编码在 message 字符串里，无错误参数结构、无源码定位。
- **风险**: 这些异常不携带 SourceLocation/param，用户看到的错误缺乏定位信息；违反项目错误处理两层策略中对框架核心的约定。多为"不可达"防御分支，运行时触发频率低。
- **建议**: 将带 `nop.err.` 前缀的分支迁移到 `XLangErrors` 中定义的 ErrorCode + NopException；纯断言类（如 DeltaMerger 的 switch default）可保留。
- **误报排除**: 逐条查看各 throw 点上下文，确认其中多数（XplCompiler 输出模式、ObjMetaToXDef 变换空节点、RegisterModelDiscovery 类型不符）是用户输入/配置可触发的错误路径而非纯内部断言。

> **处置（fix-ai-check 分支，2026-08-24）**: 裁定暂缓。本日复核 bare `IllegalStateException/IllegalArgumentException` 全模块（排除生成物）共 16 处仍存：带 `nop.err.` 前缀需迁移的为 XplCompiler:307、RegisterModelDiscovery:287/299、JaninoParser:146、DeltaExtendsGenerator:43；ObjMetaToXDef:63/82/138/185 为普通消息；DeltaMerger:115 与 XLangASTVisitor:434 属 switch default 纯断言（按审计建议可保留）。暂缓理由：(1) `nop.err.core.invalid-transformer-type/invalid-loader-type`（RegisterModelDiscovery）与 `nop.err.json.source-not-string`（DeltaExtendsGenerator）的错误码规范归属为 nop-core `CoreErrors`——该模块在本单元可改范围之外，各消息前缀也自证原作者预期归属 core；(2) janino/xmeta 各处需新增错误码并同步 i18n 聚合条目（聚合文件在 nop-cli-core，同样超出范围，本任务规则"新增错误码需同步 i18n"与之冲突构成硬外部依赖）；(3) XplCompiler:307 一处可直接复用既有 `ERR_XPL_UNKNOWN_OUTPUT_MODE`+`ARG_OUTPUT_MODE`（i18n 已同步），待上述阻塞解除后可随批先行迁移。影响面：均为低频防御分支/配置错误路径，异常缺 SourceLocation/param 结构但不损运行正确性。

### [P3] 模型解析热路径上的 INFO 级日志噪声

- **文件**: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/feature/XModelInclude.java:69`（同类：`xdsl/AbstractDslParser.java:294`）
- **维度**: D6（日志热点）
- **证据**:
```java
public boolean checkFeatureSwitch(XNode node, FeatureConditionEvaluator evaluator) {
    if (!isEnabled(node, evaluator)) {
        LOG.info("nop.xlang.xdsl.remove-node-when-feature-disabled:node={}", node);   // 每个被裁剪节点一条 INFO
        return false;
    }
    ...
}
```
- **现状**: 每个因 feature:off 被裁剪的节点输出一条 INFO 日志（`node.toString()` 序列化整节点）；`AbstractDslParser.parseNodeFromResource` 每次解析输出一条带耗时的 INFO。在大规模模型加载（平台启动扫描上千 DSL 文件）时产生显著日志量与 toString 开销。
- **风险**: 仅影响启动/加载性能与日志可读性，无功能危害。
- **建议**: 降为 DEBUG 级，或仅在被裁剪的是根节点时输出。
- **误报排除**: 确认 `checkFeatureSwitch` 递归遍历每个节点、被裁剪父节点的全部子孙都会逐个命中该日志；确认 `AbstractDslParser` 的 debug 变体已存在（parseFromNode 用 LOG.debug），仅 parseNodeFromResource 用 INFO，属不一致。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。两处均降为 DEBUG：`XModelInclude.checkFeatureSwitch` 的 `nop.xlang.xdsl.remove-node-when-feature-disabled`（原每个被裁剪节点 `node.toString()` 序列化整节点输出一条 INFO）与 `AbstractDslParser.parseNodeFromResource` finally 块的 `nop.core.component.finish-parse-node-from-resource`（原每次资源解析一条带耗时 INFO），与同文件既有 `parse-from-node`/`parse-use-time`/`begin-parse-node-from-resource` 的 DEBUG 级对齐（消除审计指出的不一致）。免新增测试：纯日志级别调整，无数值行为语义变化；grep 确认无任何测试断言这两条日志消息。同类的 nop-core `AbstractResourceParser.finish-parse-resource` INFO 不在本模块可改范围，未动（记录为新发现）。

## 附注（核对过但未立为发现的项）

- `OverrideHelper` 映射表中 `MERGE+APPEND→REPLACE` 等行内注释标注"存在问题"：为原作者对合并语义的已知取舍说明，非实现缺陷。
- `XDslExtender.loadTransformers` 按 xdef 的 `xdef:transformer-class` 反射实例化：xdef 为平台开发者管控资源，与 beans.xml 同级信任模型，不构成 D5 漏洞。
- `XplLibTagCompiler.LazyCompiledFunction`：synchronized compile + volatile 产物 + 失败异常缓存，双重检查与不可恢复状态防护完整（注释明确处理了 collectDependsTo 自身失败分支），未发现并发缺陷。
- `DeltaMerger.mergeChildren` 的 byKeys/byTags 双图合并与 bIndexes 回填：逐一推演 a/b 两侧节点归属，未发现重复挂载或丢失；`applyDefaultAttrValue` 对节点写缺省键值属设计内副作用。
- `RefResolver`/`ObjMetaRefResolver`/`XtTransformCompiler` 均有显式环检测；`EvalBackendRegistry` 注册路径有锁保护。
- D7（Nop IoC 规范）：模块内无 `@Inject`、无 Spring import、无 beans.xml（该模块不承载 bean 定义），规范符合。
- 资源管理（D2）：模块内直接 IO 仅经 `ResourceHelper.readText/writeText`（内部 try-finally safeClose）与 `StringWriter`，未发现流泄漏。
