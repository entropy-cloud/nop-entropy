# 345 xpath 与 xtransform 实现与测试硬化

> Plan Status: completed
> Last Reviewed: 2026-08-22
> Source: 内部 audit（xpath + xtransform 设计/实现/测试/外部使用情况审查，2026-08-22），`docs/dev-guide/xlang/xtransform.md`、`docs/dev-guide/xlang/xpath.md`

## Purpose

把 `io.nop.xlang.xpath` 与 `io.nop.xlang.xt` 两个子模块从"骨架完工+核心特性空缺"提升到"设计文档承诺的功能全部连通且被测试覆盖"。范围严格限定在 `nop-kernel/nop-xlang` 模块内，不引入新的对外依赖。

## Current Baseline

### xpath（`io.nop.xlang.xpath`）

- 13 个 selector + 8 个 operator 全部存在，selector 关系在仓库内已被 OOXML 模块通过 `XPathHelper.parseXSelector` 间接使用。
- `XPathOperatorRegistry` 静态块只注册 5 个 operator：`$value/$tag/$xml/$innerXml/$text`；`$html`、`$innerHtml`、`$node` 三个常量存在但未注册——硬编码 `parseFromText("/a/$node")` 会抛 `ERR_XPATH_UNKNOWN_OPERATOR`（`XPathSelectorParser.valueOperator` 在 `operatorProvider.getOperator(name)` 返回 null 时抛 `ERR_XPATH_UNKNOWN_OPERATOR`）。
- `IXPathValueSelector.isBiDirectional()` 没有 selector 重写；`XSelectorAdapter.updateSelected()` 是空方法。设计文档承诺的"自动创建 a/b 节点并 setValue"完全不工作。
- `DefaultXPathContext.containsValue/getValue` 仅识别 `XPATH_VAR_THIS_NODE`/`XPATH_VAR_ROOT`；`scope.setExtension(this)` 注册了 `this`，但 `getValue` 没查 `scope.getExtension(...)`。
- 缺少任何 XPath 轴（ancestor / descendant / sibling / preceding / following）。
- `XPathSelectorParser` 不支持标准 XPath 函数库（fn:substring / fn:string-length / fn:contains 等）。
- 测试 `TestXPath.java` 只有 2 个用例，且不返回值断言。

### xtransform（`io.nop.xlang.xt`）

- 19 个 `_gen/_Xt*Model.java` 模型已生成，`xt.xdef` 定义清晰。`XtTransformModelLoader` 类存在但**未在任何地方注册**（`XtTransform.load()` 直接走 `DslModelParser`，不经过 loader 注册表）。
- 13 条规则实现类全部存在：`ApplyTemplateRule`、`ApplyMappingRule`、`CopyNodeRule`、`CopyBodyRule`、`ValueRule`、`ValueOutputRule`、`EachRule`、`ChooseRule`、`IfRule`、`CompositeRule`、`GenRule`、`ScriptRule`、`CustomTagRule`，以及 `AbstractSelectorRule` 基类。
- `XtTransformContext.getRuleForTag(mappingId, tagName)` 当前直接做 `(IXTransformRule) mapping.getMatch(tagName)` 强转——`XtMappingMatchModel extends XtRuleGroupModel` 链路里没有任何类实现 `IXTransformRule`。运行期 `XtTransform.transform()` 走 `<xt:apply-mapping>` 路径会立刻 `ClassCastException`。
- `<xt:import>` 元素被 schema 接受，`XtTransformModel.getImports()` 暴露 `KeyedList<XtImportModel>`，但 `XtTransformCompiler.compile/compileTemplates/getMappings` **完全没有读取 imports**。
- `<mapping inherits="...">` 元素被 schema 接受，`XtMappingModel.getInherits()` 暴露 `Set<String>`，但 `XtTransformContext.getRuleForTag` 与 `XtTransformCompiler` 都**没有处理 inherits**。
- 各 rule 中样板不一致：`IfRule`/`ChooseRule`/`ValueRule`/`ValueOutputRule`/`GenRule` 在 invoke 前各自 `setLocalValue("node"/"context"/"params")`；`ScriptRule` 多设了 `"output"`；`CustomTagRule` 设了 `XPATH_VAR_THIS_NODE` 和 `selected`；`XtTransformContext` 构造时仅预先注入 `context` 和 `params`。结果：`xt:each` 内部的 `xt:if test="${node.attr('x')}"` 看到的 `node` 取决于 if 的位置，行为不一致。
- `XNode` 类没有 `attr(String)` 方法，仅有 `getAttr(String)` / `attrText(String)` 等。这意味着表达式 `${node.attr('id')=='X'}` 实际无法直接调用，需改为 `${node.getAttr('id')=='X'}` 或 `${@id=='X'}`（xt:expr 路径下的属性简写）。
- `IXtTransformOutput` 接口仅含 `addChild(XNode)` / `setValue(Object)` / `addAttr(String, Object)` / `getCurrentNode()` / `newOutputNode(String)` / `pushNode(XNode)` / `popNode()`，**没有 `addNode` 方法**。`xt:script` 路径下写 `$output.addNode(...)` 会报编译错。
- `XtTransformContext.childContext(XNode newNode, XNode newOutput)` 当前实现**忽略了 `newOutput` 参数**：`new XtTransformContext(... , output.getCurrentNode() , scope)`，第 6 个参数位置是 `outputRoot`，但传的是 `output.getCurrentNode()`——`newOutput` 形同虚设。
- 错误码 `XLangErrors` 仅定义了 `ERR_XT_TEMPLATE_NOT_FOUND / ERR_XT_MAPPING_NOT_FOUND / ERR_XT_MANDATORY_NODE_NOT_FOUND`；设计文档 §9 列出的 `ERR_XT_XPATH_ERROR / ERR_XT_RULE_COMPILE_ERROR / ERR_XT_CIRCULAR_REFERENCE` 缺失，`apply-template`/`apply-mapping` 均无循环引用检测。
- `KeyedList` 公开 API（`getByKey / removeByKey / add / set`）**不支持 key 改写**。要改 `<import prefix="X">` 后模板/mapping 的逻辑 id，必须走"外部 Map 层重映射"路径。
- `TestXtTransform.java` 9 个测试全过，但其中 `testMapping`、`testIfRule`、`testChooseRule` 对应的 `.xt.xml` 实际**根本没用到 mapping/if/choose 路径**，全是静态文本。
- `IXTransform` 接口是空接口；`IXtTransformCompiler` 接口在设计文档 §6 中提到但代码里**未定义**。
- 整个仓库（除 nop-xlang 自身与测试）**无任何业务模块调用** `io.nop.xlang.xt`；`io.nop.xlang.xpath` 仅被 OOXML 两个模块通过 `XPathHelper.parseXSelector` 调用。
- `docs/dev-guide/xlang/xpath.md` 是 1 行空文件；`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`、`xdef-and-xdsl.md` 等都**没有提及** XPath 表达式或 xt 规则；`docs-for-ai/04-reference/source-anchors.md` 与 `INDEX.md` 没有 xpath/xt 路由项。

## Goals

1. 把 xpath 中所有 `XLangConstants.XPATH_OPERATOR_*` 中声明的 operator 全部注册到 `XPathOperatorRegistry` 并通过测试验证。
2. 修复 `XtTransformContext.getRuleForTag` 的强转 bug，让 `<xt:apply-mapping>`、`<mapping><match>`、`<default>` 这条核心 dispatch 路径真正可工作并通过测试验证。
3. 在编译期做模板/mapping id 存在性检查（设计文档 §9.2 承诺但 live 缺失），未找到抛 `ERR_XT_TEMPLATE_NOT_FOUND` / `ERR_XT_MAPPING_NOT_FOUND`。
4. 实现 `<xt:import>` 文件加载与 id 前缀重写，并通过测试验证。
5. 实现 `<mapping inherits>` 合并，并通过测试验证。
6. 在 `XtTransformContext` 构造时统一注入 `$node/$thisNode/$output/$root/$params/$context` 六个内置变量；删除各 rule 中重复的样板（**`ScriptRule` 例外**：XPL 脚本体内需要 `$output` 引用，保留 `setLocalValue("output", ...)` 一行作为幂等"再次确认"，仅删 `node/context/params` 三行样板——注入顺序与 Phase 2 描述一致）。
7. 修复 `XtTransformContext.childContext(node, output)` 忽略 `newOutput` 的 bug，明确 `newOutput` 是 push 到 `IXtTransformOutput` 的栈顶；协调 `CustomTagRule` 现存的 push/pop 与 childContext 的 push 路径。
8. 补齐 `ERR_XT_XPATH_ERROR / ERR_XT_RULE_COMPILE_ERROR / ERR_XT_CIRCULAR_REFERENCE` 三个错误码，并在 `apply-template`/`apply-mapping` 路径上加循环引用检测。
9. 测试覆盖率：xpath 至少新增 7 个 `@Test` 用例覆盖 `$tag/$xml/$innerXml/$html/$innerHtml/$node` operator、`|` 联合，外加 2 个错误路径用例（`ERR_XPATH_UNKNOWN_OPERATOR`、`ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR`，后者即 `..` 边界），断言返回值；xt 至少新增 16 个 `@Test` 用例覆盖 `<xt:apply-mapping>`、`<mapping><match>`、`<default>`、`<xt:choose>`+`<when test>`、`<xt:if test>`、`<xt:value>` 表达式、`<xt:script>`、`<xt:gen>`、参数传递、自定义标签输出，并且 mandatory 抛错、template/mapping 未找到抛错、循环引用抛错均有断言验证。
10. 把 xpath 和 xt 的使用契约迁入 `docs-for-ai/02-core-guides/`（新增 `xpath-and-xtransform.md`），并在 `docs-for-ai/04-reference/source-anchors.md` 与 `INDEX.md` 增加对应路由项。

## Non-Goals

1. 实现完整标准 XPath 1.0 / 2.0 / 3.0 函数库与轴——超出 `XLangConstants` 现有 operator 范围的语义不进 plan；ancestor 等轴保留为 deferred。
2. 改造 `XPathSelectorParser` 走 ANTLR / 完整 XPath W3C 语法——保持现有手写递归下降 parser 的形态。
3. 修改 `_gen/` 下任何生成文件（按 AGENTS.md `Hard Stop: Generated Files` 规则禁止手编辑）。
4. 修改 `xt.xdef` 的结构性 xdef 节点（除非修复 dispatch bug 必需；本 plan 通过"外部 cache Map"绕开，避免动 xdef）。
5. 提供 xt 的 IDE 插件、流式处理、JSON 输入输出等扩展能力——已在 docs `## 12. 未来规划` 列入。
6. 把 XTransform 接入现有业务模块（仅做模块内自包含硬化，不引入新调用方）。
7. `XPathSelectorParser.isBiDirectional()` 双向 setValue 实现——保留为 deferred；与 Goal #1-10 解耦，不在本 plan 范围。
8. 重写 `xtransform.md`/`xpath.md` 设计文档内容；仅迁入 docs-for-ai 必要章节。

## Scope

### In Scope

- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/` 内的所有源文件（13 个 selector、8 个 operator、parser、context、provider、helper、adapter）。
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/` 内的所有源文件（4 个接口、13 条规则、compiler、context、loader）。
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangConstants.java` 与 `XLangErrors.java` 中新增 operator 注册与错误码（**不**改 `XLangConstants` 既有常量）。
- `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpath/TestXPath.java` 与 `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xt/TestXtTransform.java` 的扩充，以及新增的测试资源 `xt.xml`/`input.xml`。
- `docs-for-ai/02-core-guides/` 下新增 xpath 与 xt 的使用契约章节。
- `docs-for-ai/04-reference/source-anchors.md` 与 `INDEX.md` 增加对应路由项。

### Out Of Scope

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/xt.xdef` 的结构性改动。
- `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/selector/_gen/` 与 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/model/_gen/` 的手编辑。
- 任何业务模块（`nop-ooxml`、`nop-record-mapping` 等）的调用方迁移。
- `xtransform.md`/`xpath.md` 文档内容修订——保留为历史快照。

## Phase 间依赖关系

顺序强依赖（不可并行）：

- Phase 2 必须在 Phase 4 之前完成（`testIfRuleWithEach` 与 `testXtScript` 依赖 Phase 2 修复的样板统一与内置变量注入）。
- Phase 3 必须在 Phase 4 之前完成（`testApplyMappingMatch`、`testApplyTemplateNotFound`、`testApplyMappingNotFound`、`testApplyTemplateMandatory`、`testApplyTemplateCircular` 都依赖 Phase 3 的 dispatch/import/inherits/circular 检测）。
- Phase 1 与 Phase 3 可并行（互不依赖）。
- Phase 5 必须最后执行（依赖前 4 个 Phase 的 live code 与结论）。

## Execution Plan

### Phase 1 - xpath operator 闭环与 selector 边界测试

Status: completed
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/operator/XPathOperatorRegistry.java`、`io/nop/xlang/xpath/operator/HtmlOperator.java`（新建）、`InnerHtmlOperator.java`（新建）、`IdentityOperator.java`（已有），`nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xpath/TestXPath.java`

- Item Types: `Fix | Proof`

- [x] 在 `XPathOperatorRegistry` 静态块中注册 `XPATH_OPERATOR_HTML / XPATH_OPERATOR_INNER_HTML / XPATH_OPERATOR_IDENTITY` 三个 operator。
- [x] 新建 `HtmlOperator.java` 与 `InnerHtmlOperator.java` 两个类，分别实现 `apply` 调用 `context.adapter().html(node)` / `context.adapter().innerHtml(node)`（与 `OuterXmlOperator`/`InnerXmlOperator` 形态一致）。`IdentityOperator.java` 已存在，无需新建。
- [x] 在 `TestXPath` 中新增至少 7 个 `@Test`：
  - `testOperatorTag`：`/root/$tag` 返回根节点标签名
  - `testOperatorXml`：`/root/$xml` 返回 outer XML 字符串
  - `testOperatorInnerXml`：`/root/$innerXml` 返回 inner XML 字符串
  - `testOperatorHtml`：`/root/$html` 返回 HTML 字符串（利用 XNode 既有 `html()` 实现）
  - `testOperatorInnerHtml`：`/root/$innerHtml` 返回 inner HTML 字符串（`$innerHtml` 是本 Phase 新增注册的 operator，必须有测试覆盖，不能只注册不测试）
  - `testOperatorNode`：`/$node` 返回根节点本身（`assertSame`）
  - `testPipeUnion`：`//child | /root/child` 选 3 个节点（2 个 child + 1 个根 child）
- [x] 在 `TestXPath` 中新增 2 个错误路径断言：`testUnknownOperator` 写 `/$foo` 抛 `ERR_XPATH_UNKNOWN_OPERATOR`（用 `assertThrows`）；`testParentOnRoot` 写 `/..` 抛 `ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR`（`..` 边界由此用例覆盖）。

Exit Criteria:

- [x] `XPathOperatorRegistry` 注册的 operator 数量 = `grep -c 'registerOperator' nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xpath/operator/XPathOperatorRegistry.java`。
- [x] `grep -c XPATH_OPERATOR nop-kernel/nop-xlang/src/main/java/io/nop/xlang/XLangConstants.java`（**常量声明数**）等于 registry 注册数。
- [x] `TestXPath.java` 至少包含 11 个 `@Test` 方法（live 2 个 + 新增 9 个）。
- [x] `./mvnw test -pl nop-kernel/nop-xlang -Dtest=TestXPath` 全过。
- [x] **接线验证**：从 `XPathSelectorParser.valueOperator` 反向追踪到 `XPathOperatorRegistry.getOperator`，确认 3 个新增注册的 operator（`$html`/`$innerHtml`/`$node`）全部触达，且每个新增 operator 都有对应 `@Test` 覆盖。
- [x] **无静默跳过**：未注册 operator 必须抛 `ERR_XPATH_UNKNOWN_OPERATOR`，不是返回 null。
- [x] `ai-dev/logs/2026/08-22.md` 记录本次改动。
- [x] No owner-doc update required（Phase 5 统一更新）。

### Phase 2 - xt 上下文内置变量与样板统一

Status: completed
Precondition: Phase 1 不阻塞本 Phase。
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/core/XtTransformContext.java`、`IfRule.java`、`ChooseRule.java`、`ValueRule.java`、`ValueOutputRule.java`、`GenRule.java`、`CustomTagRule.java`、**`ScriptRule.java`（仅移除部分样板）**、`EachRule.java`、`ApplyTemplateRule.java`、`ApplyMappingRule.java`、`TestXtTransform.java`

- Item Types: `Fix`

- [x] 在 `XtTransformContext` 构造时显式注入 `node=null`、`thisNode=null`、`root=null`、`output`、`params`、`context` 六个 IEvalScope 内置变量。XtTransform.transform() 在 mainRule.apply 前更新 `node`、`root` 到 source。
- [x] 实现 `XtTransformContext.childContext(XNode newNode)` 与 `childContext(XNode newNode, XNode newOutput)`：
  - `newNode` 切换当前节点：context 子类的 `currentNode` 字段设为 `newNode`，并在子 context scope 内更新 `node`、`thisNode` 为 `newNode`。
  - `newOutput` 切换输出根：**调用 `output.pushNode(newOutput)`**（IXtTransformOutput 接口已含此方法），返回共享同一 output 实例但栈顶已切换的 context。
  - **生命周期约束**：调用 `childContext(node, outputNode)` 后，调用方负责在适当的时候（推荐 try-finally）调用 `context.getOutput().popNode()` 还原。本 Phase 内唯一已存在的第二参调用方是 `CustomTagRule`（见下条协调项），改造后不再走第二参路径。
  - 协调：`CustomTagRule.apply` 当前**已经**调用 `output.pushNode(outputNode)` + `finally popNode()`，且同时调用 `childContext(selected, outputNode)`（第二参因 childContext 忽略 newOutput 的 bug 形同虚设）。本 Phase 修复 childContext 后，CustomTagRule 若继续传第二参会与自己的 push 叠加（双重 push）；因此保留 CustomTagRule 自己的 push/pop，**改为调用 `context.childContext(selected)`（不传第二参）**。
- [x] `EachRule.apply`：进入子 context 前先记录 `prevNode = scope.getValue("node")`；在循环 body 入口由 `childContext(selected)` 把 `node` 写为当前迭代节点（IEvalScope.setLocalValue 直接覆盖）；finally 块恢复 `scope.setLocalValue("node", prevNode)`，保证后续迭代与外层 rule 看到的 `node` 是 expected 值。
- [x] 删除 `IfRule`、`ChooseRule`、`ValueRule`、`ValueOutputRule`、`GenRule`、`CustomTagRule` 中重复的 `setLocalValue("node"/"context"/"params")` 样板——context 已注入。
- [x] `ScriptRule` 处理：保留 `setLocalValue("output", context.getOutput())`（XPL 脚本体内需要 `$output`），删除 `setLocalValue("node"/"context"/"params")` 样板。
- [x] 新增测试 `TestXtTransform.testIfRuleWithEach`：xt 内容 `<main><result><xt:each xpath="/root/item"><xt:if test="${@id=='X'}"><match/></xt:if></xt:each></result></main>`，input 含两个 `item` 一个 id=`X`，断言只输出一个 match 子节点。

Exit Criteria:

- [x] `XtTransformContext` 构造时显式注入全部 6 个内置变量（grep 验证：构造器体内含 6 处 `setLocalValue`）。
- [x] 上述 7 个 rule 类（除 ScriptRule 保留 `output` 注入）中不再出现 `setLocalValue("node")` / `setLocalValue("context")` / `setLocalValue("params")`。
- [x] 表达式语法统一：用 `${@id=='X'}`（xt:expr 内置属性简写）而不是 `${node.attr('id')=='X'}`，因为 `XNode` 没有 `attr(String)` 方法（仅 `getAttr(String)`）。
- [x] 新增的 `testIfRuleWithEach` 通过且断言 `xt:if` 在 `xt:each` 内每轮看到的 `node` 是当前迭代节点（用 `assertEquals("X", result.child(0).attrText("id"))`）。
- [x] `./mvnw test -pl nop-kernel/nop-xlang -Dtest=TestXtTransform` 已有 9 个用例全部不回归。
- [x] No owner-doc update required（Phase 5 统一更新）。

### Phase 3 - xt mapping dispatch 与 import/inherits 实现

Status: completed
Precondition: 必须先于 Phase 4。
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xt/core/XtTransformCompiler.java`、`core/XtTransformContext.java`、`core/XtTransform.java`、`io/nop/xlang/xt/IXTransformContext.java`（注意：在 `xt/` 根目录，不在 `core/` 下）、`io/nop/xlang/xt/IXTransform.java`、`XLangErrors.java`、`XtImportModel.java`（仅读取，不改 schema）

- Item Types: `Fix`

- [x] **mapping cache 设计**：在 `XtTransformCompiler.compileMappings(model, scope)` 中产出 `Map<String /*mappingId*/, Map<String /*tagName*/, IXTransformRule>>` 与 `Map<String /*mappingId*/, IXTransformRule /*default*/>`。`XtTransform` 实例持有这两个 Map 作为字段；`XtTransformContext` 通过构造器接收这两个 Map 作为参数（替代现有 `Map<String, XtMappingModel>`）。
- [x] **`IXTransformContext` 接口改造**：移除 `XtMappingModel getMapping(String id)`（nop-xlang 之外的调用方为零；模块内唯一调用点 `ApplyMappingRule` 在本 Phase 内同步改用 `getCompiledRuleForTag`，无遗留引用），保留 `IXTransformRule getTemplate(String id)`；新增 `IXTransformRule getCompiledRuleForTag(String mappingId, String tagName)`（与 `getRuleForTag` 平行存在，旧方法标 `@Deprecated` 暂时保留以避免破坏）。
- [x] **XtTransformContext.getCompiledRuleForTag(mappingId, tagName)**：直接查 cache 返回 `IXTransformRule`；null 时返回 mapping 的 `default` rule；都为 null 时返回 null。
- [x] **ApplyMappingRule.apply**：改用 `getCompiledRuleForTag(mappingId, tagName)`。
- [x] **import 处理（在 XtTransformCompiler.compile/compileMappings 中实现）**：遍历 `model.getImports()`：
  - 加载 `from` 指向的 transform model（复用 `XtTransform.load(IResource)` 或直接 `new XtTransformModelLoader()`——该 loader 未注册到注册表，需显式实例化）；
  - 对被 import 文件的 templates / mappings，按 `prefix + ":" + originalId` 在当前 `XtTransform` 实例的 cache 里登记；`prefix` 为空时直接用 `originalId`。
  - 加载的 templates / mappings 的**内容**（即 XtRuleGroupModel 子树）原样合并——子模型不需要 id 改写；
  - 跨文件引用：通过 "id 解析"在 `XtTransformContext.getTemplate/getCompiledRuleForTag` 入口做 prefix-aware lookup；
  - 冲突（同一 id 重复定义）：抛新增 `ERR_XT_IMPORT_CONFLICT`（带前缀参数）。
- [x] **inherits 处理（在 XtTransformCompiler.compileMappings 中实现）**：对每个子 mapping，按 `inherits` 列表合并父 mapping 的 `match`/`default`：子有同名 match 覆盖父；子无 default 时继承父 default；多重继承按 csv 顺序后者覆盖前者。
- [x] **循环引用检测**：`XtTransformContext` 新增 `Set<String> visitedTemplates` 与 `Set<String> visitedMappings` 字段（`childContext` 不复制，保证所有 context 共享同一份 visited state）；`ApplyTemplateRule.apply` 入口 push，finally pop，重复访问抛 `ERR_XT_CIRCULAR_REFERENCE`。`ApplyMappingRule.apply` 同理（但 mapping 一般不递归，visited 主要针对 template）。
- [x] **编译期 ID 存在性检查（设计文档 §9.2 承诺）**：在 `XtTransformCompiler` 编译 main / templates / mappings 时，扫描所有 `apply-template id` / `apply-mapping id` 属性，验证引用的 id 在当前 cache（已合并 imports/inherits 后）中存在；不存在抛 `ERR_XT_TEMPLATE_NOT_FOUND` / `ERR_XT_MAPPING_NOT_FOUND`，并附 source location。
- [x] **新增错误码**（`XLangErrors.java`）：
  - `ERR_XT_CIRCULAR_REFERENCE = define("nop.err.xt.circular-reference", "xt转换规则存在循环引用:{path}", ARG_PATH)`，ARG_PATH 已存在可直接复用（确认后）。
  - `ERR_XT_IMPORT_CONFLICT = define("nop.err.xt.import-conflict", "import冲突:id={id} 已被定义", ARG_ID)`，ARG_ID 已存在。
  - `ERR_XT_XPATH_ERROR = define("nop.err.xt.xpath-error", "xpath表达式错误:{xpath}", ARG_XPATH)`，ARG_XPATH 已存在。
  - `ERR_XT_RULE_COMPILE_ERROR = define("nop.err.xt.rule-compile-error", "规则编译失败:{ruleType}", ARG_RULE_TYPE)`，需新增 ARG_RULE_TYPE 常量。
- [x] **IXTransform 接口定义**（非空）：`XNode transform(XNode source)` 与 `XNode transform(XNode source, Map<String,Object> params)`；`XtTransform implements IXTransform`。
- [x] **childContext(node, output) 修复**：见 Phase 2，本 Phase 同步检查实现一致性。

Exit Criteria:

- [x] `XtTransformContext.getCompiledRuleForTag(mappingId, tagName)` 返回类型严格为 `IXTransformRule`（无强转、无 ClassCastException 风险）。
- [x] `XtTransform` 实例持 `Map<String /*prefixedId*/, IXTransformRule>` templates 与 `Map<String /*prefixedId*/, Map<String /*tagName*/, IXTransformRule>>` mappings。
- [x] 新增测试 `TestXtTransform.testApplyMappingMatch`：mapping 含 match 与 default；输入 `<foo/>` 与 `<bar/>`，验证两条分支都被命中。
- [x] 新增测试 `TestXtTransform.testImportPrefix`：xt A 包含 `<import from="B.xt.xml" prefix="ext"/>`；B 含 template `t`；A 通过 `<xt:apply-template id="ext:t"/>` 调用成功。
- [x] 新增测试 `TestXtTransform.testImportConflict`：A 已有 template `t` 且 import `prefix="p"` 的 B 也含 `t`；但 A 通过 `<xt:apply-template id="p:t"/>` 与 `<xt:apply-template id="t"/>` 都可解析（prefix 隔离）；A 直接定义与 B 直接定义同 id 时抛 `ERR_XT_IMPORT_CONFLICT`。
- [x] 新增测试 `TestXtTransform.testMappingInherits`：父 mapping 含 match，子 mapping 通过 `inherits` 继承并可覆盖；子未覆盖时回退父。
- [x] 新增测试 `TestXtTransform.testApplyTemplateCircular`：template A 调用 template A，抛 `ERR_XT_CIRCULAR_REFERENCE`。
- [x] 新增测试 `TestXtTransform.testApplyTemplateNotFound`：`xt:apply-template id="nonexistent"` 抛 `ERR_XT_TEMPLATE_NOT_FOUND`（编译期抛）。
- [x] 新增测试 `TestXtTransform.testApplyMappingNotFound`：`xt:apply-mapping id="nonexistent"` 抛 `ERR_XT_MAPPING_NOT_FOUND`（编译期抛）。
- [x] 新增测试 `TestXtTransform.testApplyTemplateMandatory`：mandatory=true 且 xpath 找不到节点抛 `ERR_XT_MANDATORY_NODE_NOT_FOUND`。
- [x] **端到端验证**：每条新增测试都走 `XtTransform.load → transform → 断言 XNode` 完整路径，覆盖 mapping dispatch / import / inherits / circular / compile-time ID check / runtime mandatory 六条主路径。
- [x] **接线验证**：mapping match/default 的编译结果在 `ApplyMappingRule.apply` 中通过 `getCompiledRuleForTag` 确实被调用（grep 验证：`getCompiledRuleForTag` 至少出现 2 处调用）。
- [x] **无静默跳过**：所有失败路径（missing template/mapping、circular、mandatory not found、xpath 错误）必须抛错，不是返回 null。
- [x] `./mvnw test -pl nop-kernel/nop-xlang -Dtest=TestXtTransform` 全过。
- [x] `ai-dev/logs/2026/08-22.md` 记录本次改动。
- [x] No owner-doc update required（Phase 5 统一更新；本 Phase 的接口/错误码变更均在 `nop-xlang` 框架内部，docs-for-ai 契约章节由 Phase 5 一并落盘）。

### Phase 4 - xt 表达式驱动规则测试覆盖

Status: completed
Precondition: Phase 2 与 Phase 3 必须先完成。
Targets: `nop-kernel/nop-xlang/src/test/java/io/nop/xlang/xt/TestXtTransform.java`、`src/test/resources/io/nop/xlang/xt/*.xt.xml`

- Item Types: `Proof`

- [x] 新增测试 `testXtValueExpr`：xt 含 `<div><xt:value>${@a + '_' + @b}</xt:value></div>`，input `<root a="x" b="y"/>`，断言 `result.contentText() == "x_y"`。
- [x] 新增测试 `testXtIfWithXPath`：xt 含 `<main><result><xt:if test="${@enabled=='true'}"><yes/></xt:if><xt:if test="${@enabled=='false'}"><no/></xt:if><xt:if test="${@enabled==null}"><none/></xt:if></result></main>`，三个 input 各覆盖一个分支，断言对应子节点存在且另两个不存在。
- [x] 新增测试 `testXtChooseWhen`：xt 含 `<main><result><xt:choose><when test="${@type=='A'}"><typeA/></when><when test="${@type=='B'}"><typeB/></when><otherwise><unknown/></otherwise></xt:choose></result></main>`，三个 input 各覆盖一个分支。
- [x] 新增测试 `testXtScript`：xt 含 `<main><result><xt:script xpath="/root"><c:script>$output.addChild(XNode.make('built', null, 'yes'))</c:script></xt:script></result></main>`，断言 result 含 `<built>yes</built>`（注意用 `addChild` 不是 `addNode`）。
- [x] 新增测试 `testXtGen`：xt 含 `<main><result><xt:gen xpath="/root"><c:for var="i" items="${items}"><row>${i.name}</row></c:for></xt:gen></result></main>`（假设 `items` 通过 params 传入），断言 result 含 N 个 `<row>` 子节点。
- [x] 新增测试 `testParameters`：xt 含 `<main><result title="${$params.k}">${$params.v}</result></main>`，`transform(source, {"k":"myTitle","v":"hello"})`，断言 `result.attrText("title") == "myTitle"` 且 `result.contentText() == "hello"`。
- [x] 新增测试 `testCustomTagOutput`：xt 含 `<main><output xt:xpath="/root/x" name="${@n}" xt:attrs="${ {'extra':'val'} }">inner</output></main>`，input `<root><x n="alice"/></root>`，断言 result.tag == "output" 且 `result.attrText("name") == "alice"` 且 `result.attrText("extra") == "val"`。
- [x] 新增测试 `testEachNestedIf`：**与 Phase 2 的 `testIfRuleWithEach` 重复，不新增**——xt:each + 内嵌 xt:if 的路径已由 Phase 2 用例覆盖（`testIfRuleWithEach` 断言 `xt:if` 在 `xt:each` 内每轮看到的 `node` 是当前迭代节点），本 Phase 不重复编写。
- [x] 新增测试 `testXtRuleMandatoryOnPath`：xt 含 `<main><result><xt:copy-node xpath="/no/such/node" mandatory="true"/></result></main>`，断言抛 `ERR_XT_MANDATORY_NODE_NOT_FOUND`。

Exit Criteria:

- [x] `TestXtTransform.java` 至少包含 17 个 `@Test` 方法（live 9 + 新增 8：Phase 3 的 8 个 + 本 Phase 的 8 个）。
- [x] `./mvnw test -pl nop-kernel/nop-xlang -Dtest=TestXtTransform` 全过。
- [x] **端到端验证**：每条新增测试都走 `XtTransform.load → transform → 断言 XNode` 完整链路。
- [x] **无静默跳过**：未找到的 template/mapping、循环引用、mandatory 节点缺失均抛错，不是返回 null。
- [x] `ai-dev/logs/2026/08-22.md` 记录本次改动。
- [x] No owner-doc update required（Phase 5 统一更新）。

### Phase 5 - docs-for-ai 迁入与索引同步

Status: completed
Precondition: 必须在 Phase 1-4 全部完成后执行。
Targets: 新建 `docs-for-ai/02-core-guides/xpath-and-xtransform.md`、更新 `docs-for-ai/04-reference/source-anchors.md` 与 `docs-for-ai/INDEX.md`

- Item Types: `Follow-up`

- [x] 新建 `docs-for-ai/02-core-guides/xpath-and-xtransform.md`，内容覆盖：
  - **xpath 语法**：绝对路径、相对路径、轴 (`//`、`..`)、谓词 `[@a='v']`、通配符 `*`、联合 `|`、operator `$value/$tag/$xml/$innerXml/$html/$innerHtml/$text/$node`、内置变量 `$thisNode/$root/$context`。
  - **xt 文档结构**：`<transform>` / `<import>` / `<mapping>` / `<template>` / `<main>`、13 条规则指令的语法与语义。
  - **错误码清单**：列出 `XLangErrors.java` 中所有 `ERR_XT_*` 与 `ERR_XPATH_*` 错误码。
  - **与 XSLT 差异表**（参考 `xtransform.md` §1.2）。
  - 文档内容必须与 `xt.xdef` 与本 plan 实施后的 live code 一致。
- [x] 在 `docs-for-ai/04-reference/source-anchors.md` 中新增 3 条 anchor：
  - `XPT-001` → `io.nop.xlang.xpath/operator/XPathOperatorRegistry.java` + `selector/XPathSelector.java` + `parse/XPathSelectorParser.java`：xpath selector/operator 注册与解析
  - `XPT-002` → `io.nop.xlang.xt/core/XtTransformCompiler.java` + `core/XtTransformContext.java` + `core/XtTransform.java`：xt 编译/上下文/执行入口
  - `XPT-003` → `io.nop.xlang.xt/rules/ApplyTemplateRule.java` + `rules/ApplyMappingRule.java` + `rules/ChooseRule.java`：xt 核心规则 dispatch 与 import/inherits 路径
- [x] 在 `docs-for-ai/INDEX.md` 的「快速路由」表（任务→首选文档路由表）追加 1 行：`io.nop.xlang.xpath` 与 `io.nop.xlang.xt` → `docs-for-ai/02-core-guides/xpath-and-xtransform.md`。
- [x] 校对：`grep -r xpath docs-for-ai/02-core-guides/xpath-and-xtransform.md` 能定位到 xpath 章节；`grep -r 'xt:apply-template' docs-for-ai/02-core-guides/xpath-and-xtransform.md` 同理。
- [x] 校对：`docs-for-ai/02-core-guides/xpath-and-xtransform.md` 中列出的 operator 数量、规则指令数量与 live 代码一致（手工核对至少 8 个 operator 与 13 条规则）。
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。

Exit Criteria:

- [x] `docs-for-ai/02-core-guides/xpath-and-xtransform.md` 存在且包含 xpath 语法 + xt 规则指令 + 错误码清单三个章节。
- [x] `source-anchors.md` 中包含 `XPT-001` / `XPT-002` / `XPT-003` 三条 anchor，且每条 anchor 指向的源文件路径在仓库中存在（grep 验证）。
- [x] `INDEX.md` 在「快速路由」表中列出 `io.nop.xlang.xpath` 与 `io.nop.xlang.xt` 的 owner doc 指针。
- [x] `./mvnw test -pl nop-kernel/nop-xlang` 全过。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `ai-dev/logs/2026/08-22.md` 记录本次改动。

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria 与本节全部勾选为 `[x]`，且独立 closure audit 写入 `Closure` 段落后，才能把 `Plan Status` 改为 `completed`。

- [x] Phase 1 Exit Criteria 全部勾选
- [x] Phase 2 Exit Criteria 全部勾选
- [x] Phase 3 Exit Criteria 全部勾选
- [x] Phase 4 Exit Criteria 全部勾选
- [x] Phase 5 Exit Criteria 全部勾选
- [x] `./mvnw test -pl nop-kernel/nop-xlang` 全过
- [x] `./mvnw compile -pl nop-kernel/nop-xlang` 通过
- [x] `./mvnw install -DskipTests -pl nop-kernel/nop-xlang` 通过（验证 `_gen/` 重新生成不会破坏生成文件）
- [x] checkstyle 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/345-xpath-xtransform-hardening.md --strict` 退出码为 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
- [x] **Anti-Hollow Check**：
  - `XtTransformContext.getCompiledRuleForTag` 返回的 rule 在 `ApplyMappingRule.apply` 中通过 `getCompiledRuleForTag` 确实被调用（grep 验证调用链至少 2 处）
  - `XtTransformCompiler.compileMappings` 的 import 处理路径在 `compile(...)` 中被调用
  - 没有任何新增方法为空方法体或 `continue` 跳过应处理分支
  - `XPathOperatorRegistry` 注册的 8 个 operator 全部能从 `parseFromText` 入口触达
  - `XtTransform` 的唯一接口实现语义在所有 13 条规则的执行路径上都没有 fallback 到 default null 路径
- [x] 独立子 agent closure-audit 完成，evidence 写入 `Closure` 段落
- [x] **测试数量对齐**：`TestXPath.java` `@Test` 方法数 ≥ 11（live 2 + 新增 9）；`TestXtTransform.java` `@Test` 方法数 ≥ 17（live 9 + 新增 16）；两者总和 ≥ 28

## Deferred But Adjudicated

### xpath 完整函数库（fn:substring / fn:contains / fn:number 等）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 `XPathConstants` 没有声明这些函数作为 operator；`XPathExprParser` 已支持 `: [...]` 内嵌任意 XLang 表达式，调用方在谓词内可用 `string-length(@a)` 等语法经 `StringHelper`/`EvalRuntime` 间接走通。完整 XPath 1.0 函数库属于 `## 12. 未来规划`，需要先在 `XPathConstants` 层面扩展 operator 注册表 + 加新的 Operator 类，超出当前 plan 目标。
- Successor Required: yes
- Successor Path: 可由后续 plan 跟进，不在本 plan 阻塞 closure。

### xpath ancestor/descendant/preceding/following/sibling 轴

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 `XPathConstants` 中的 operator 设计未涉及轴；`CascadeSelector` 已实现 `//`（descendant-or-self），`ParentSelector` 已实现 `..`（parent），覆盖最常见用例。其余轴需要新增 selector 类与 parser 集成，是 parser 改造级别的工作量，超出本 plan 的硬化目标。
- Successor Required: yes
- Successor Path: 后续 plan 跟进。

### `XPathSelectorParser` 全 ANTLR 化或扩展 W3C 兼容语法

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前手写递归下降 parser 与 `TextScanner` 配合良好，能覆盖现有 operator 与 `|` 联合。改用 ANTLR 是工具链替换级别，影响整个 xlang 模块，构建收益不明确。
- Successor Required: no

### XTransform 业务模块接入（OOXML、report、record-mapping 等）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前 plan 目标是把模块本身硬化到"设计文档承诺的功能全部连通且被测试覆盖"，**不**包含"让其他模块开始使用 XTransform"。业务模块接入需各自业务方单独评估收益并提交独立 plan。
- Successor Required: no（按需）

### `IXPathValueSelector.isBiDirectional()` 与 `XSelectorAdapter.updateSelected` 实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan Goals #1-10 不依赖双向 setValue（设计文档中提及但 live 行为尚未启用）。当前 8 个 operator 在正向查询路径已完整可用，setValue 路径属于 DML 操作场景，需要单独 plan 评估是否要做反向 DML（涉及对象图构建、循环引用处理等）。
- Successor Required: yes
- Successor Path: 后续 plan 跟进。

## Non-Blocking Follow-ups

- `XtTransformContext.childContext(node, output)` 的 `newOutput` 参数语义已在 Phase 2/3 中统一为 `output.pushNode(newOutput)`，但 `IXtTransformOutput` 没有 `peekNode()` 等附加管理方法；未来可补充。
- `IXtTransformCompiler` 接口定义出来（Phase 3 删除空 `IXTransform` 接口时可顺手定义），当前阶段 `XtTransformCompiler` 类已经满足调用。

## Closure

Status Note: 5 个 Phase 全部执行完毕并逐项勾选；独立 closure audit 判定 CLOSE_ALLOWED 后收口。xpath 8 个 operator 全部注册且可从 `parseFromText` 触达（3 个新增各有返回值断言测试）；xt 的 mapping dispatch ClassCastException 根因消除（编译期 cache，无强转），import/inherits/循环引用/编译期 ID 检查全部落地并有端到端测试；表达式层 `${@attr}`/`${$params}` 简写与 `%{}` 兼容可用；owner doc 与索引路由落盘。执行期发现的所有偏差（union 不去重/`|` 空格、`/..` 解析边界、VFS 仅挂载 `_vfs/`、`$` 前缀平台保留规则、`xt:attrs` 为 expr domain、`XNode.make` 无三参重载、inherits 再入非冲突）均已记录在 `ai-dev/logs/2026/08-22.md` 各 Phase 条目。
Completed: 2026-08-22

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（fresh session，task ses_fd60ad69effeeoItyGFhwHJxhr，general agent）
- Audit Session: ses_fd60ad69effeeoItyGFhwHJxhr
- Evidence:
  - 每条 Exit Criterion 的验证结果：Phase 1-5 全部 PASS（审计逐条对照 live code：registry 8 注册=XLangConstants 8 常量、XtTransformContext.java:77-82 恰好 6 处 setLocalValue、6 个 rule 无 node/context/params 样板、EachRule.java:35-46 save/restore、CustomTagRule.java:45 单参 childContext + :70/:77 自持 push/pop、getCompiledRuleForTag 无强转且 missing mapping 抛错（XtTransformContext.java:94-105）、compileAll 管线（XtTransformCompiler.java:98-104）含 import prefix 合并/inherits 合并/validateReferences、visited 集合跨 childContext 共享、IXTransformContext 无 getMapping、IXTransform 双 transform 方法、4 新错误码 + ARG_RULE_TYPE（XLangErrors.java:1092-1100）、ApplyMappingRule.java:36 接线、TestXtTransform 26 @Test、TestXPath 11 @Test、owner doc/anchors/INDEX 均在且 9 个 anchor 源文件 ls 验证存在）
  - Closure Gates 命令退出码：`./mvnw test -pl nop-kernel/nop-xlang` → Tests run: 577, Failures: 0, Errors: 0, Skipped: 2，BUILD SUCCESS；`./mvnw compile -pl nop-kernel/nop-xlang -am` exit 0；`./mvnw clean install -DskipTests -pl nop-kernel/nop-xlang -am -T 1C` BUILD SUCCESS（_gen 无破坏）；`./mvnw checkstyle:check -Pqa -pl nop-kernel/nop-xlang` BUILD SUCCESS（qa profile failOnViolation=false，非门禁；残留 1 WARN 为 XtTransformContext 包私有构造器 9 参，保留 plan 规定的显式 cache 参数签名）；`check-doc-links.mjs --strict` exit 0（0 errors，15 warnings 均为 plan 文本自身简写路径）；`scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` exit 0（0 findings）
  - Anti-Hollow 检查结果：dispatch 链 transform → mainRule.apply → ApplyMappingRule.apply:36 → getCompiledRuleForTag → rule.apply:50 实际连通；operator 链 valueOperator→getOperator 未注册即抛 ERR_XPATH_UNKNOWN_OPERATOR（XPathSelectorParser.java:192-197）；17 个改动文件无新增空方法体/continue 跳过/吞异常（`compileKnownRule` 返回 null 是被 compileRule:315-322 消费的 documented 兜底；XSelectorAdapter.updateSelected 为 pre-existing 已裁定 deferred）；git status 确认无 `_gen/`/`_*.xml` 生成文件被改动，改动仅限 nop-kernel/nop-xlang + docs-for-ai + ai-dev
  - Deferred 项分类检查：5 个 deferred 条目均为诚实的 out-of-scope improvement / optimization candidate，无 in-scope live defect 被降级（updateSelected 未被本次改动触碰且在 owner doc §1.5 公开标注为未支持）
  - 测试数量对齐：TestXPath 11 + TestXtTransform 26 = 37 ≥ 28
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/345-xpath-xtransform-hardening.md --strict` 退出码 0（收口后复跑确认）

Follow-up:

- 无剩余 plan-owned work。deferred 项（xpath 函数库/其余轴/isBiDirectional 双向 setValue/业务模块接入）已由 `Deferred But Adjudicated` 裁定为 out-of-scope，successor plan 需要时另行立项。
- 次要残留（非缺陷）：`IXtTransformOutput` 无 `peekNode()` 等栈管理辅助方法；`IXtTransformCompiler` 接口未定义（`XtTransformCompiler` 类已满足），见 `Non-Blocking Follow-ups`。
