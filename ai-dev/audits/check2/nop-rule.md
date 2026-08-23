# nop-rule 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-rule
- 文件数: 72（src/main/java，已排除 target/、`_gen/`、`_` 前缀生成文件；其中手写实现类约 30 个，其余为空壳/生成基类）
- 覆盖范围声明: 深读 30 个手写实现文件（ExecutableMatrixRule、RuleDecider、ExecutableRule、DecoratedExecutableRule、NormalizeInput/OutputExecutableRule、TraceVarsExecutableRule、MainExecutableRule、RuleOutputAction、RuleRuntime、RuleManager、RuleModelCompiler、FilterBeanToPredicateTransformer、RuleDslModelLoader、RuleTableModelParser、RuleModelHelper、RuleExprParser、RuleModel、RuleDecisionTreeModel、RuleDecisionMatrixModel、DaoRuleModelLoader、DaoRuleModelSaver、RuleServiceImpl、4 个 BizModel、NopRuleDefinition 等 entity），模式扫描（@Inject private/Spring 依赖/SimpleDateFormat/bare RuntimeException/beans.xml 注册）覆盖 100% 文件；生成本地文件 `io.nop.rule.api.beans._gen._RuleRequestBean`、`io.nop.rule.core.model._gen._RuleTableCellModel/_RuleDecisionMatrixModel` 仅作契约交叉验证读取。未覆盖区域: 无（空壳 beans/接口逐个过目确认无逻辑）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 3 |
| P2 | 3 |
| P3 | 8 |

## 发现列表

### [P0] DecoratedExecutableRule 正常执行路径不执行 afterExecute，违反 xdef 声明的契约

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/DecoratedExecutableRule.java:34`
- **维度**: D1（正确性）/ D8（契约一致性）
- **证据**:
```java
public boolean execute(IRuleRuntime ruleRt) {
    try {
        if (beforeExecute != null)
            beforeExecute.invoke(ruleRt);
        boolean b = rule.execute(ruleRt);
        ruleRt.setRuleMatch(b);
        return b;                                  // 正常路径直接返回，afterExecute 未执行
    } catch (Exception e) {
        ruleRt.setException(e);
        if (afterExecute != null) {
            afterExecute.invoke(ruleRt);           // 仅异常路径执行
        }
        throw NopException.adapt(e);
    }
}
```
- **现状**: afterExecute 只在规则执行抛异常时被调用；执行成功或规则不匹配（正常返回 true/false）时永远不执行。而平台 schema `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/rule.xdef:11-14` 对该节点的契约注释明确为"无论规则是否成功匹配，都会执行到这里"。
- **风险**: 任何在 rule.xml / rule.xlsx 中配置了 afterExecute 的规则（典型用途：结果后处理、上下文清理、审计记录），其 afterExecute 逻辑在正常路径下静默不执行，输出结果与模型声明的行为不一致，造成数据错误且难以察觉。
- **建议**: 将 afterExecute 移入 finally（或正常路径 return 前调用），与 xdef 注释对齐；同时为正常/不匹配/异常三条路径补充单元测试（当前 `TestRuleExecutionTracing` 未覆盖 DecoratedExecutableRule）。
- **误报排除**: 已读 `RuleModelCompiler.compileRule`（第 66-69 行）确认该包装器只包一层且 MainExecutableRule 不补偿调用 afterExecute；已读全部 nop-rule 测试（TestRuleExecutionTracing 等）确认无任何测试断言 afterExecute 只在异常时执行；契约以 xdef 原文为准，非主观推断。

### [P1] NormalizeInputExecutableRule.checkInputs 对 inputs==null 直接 NPE（合法请求触发）

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/NormalizeInputExecutableRule.java:92`
- **维度**: D1（NPE 风险）
- **证据**:
```java
private void checkInputs(IRuleRuntime ruleRt) {
    // 输入变量必须在已知范围之内
    for (String name : ruleRt.getInputs().keySet()) {   // getInputs() 可能为 null
        // 跳过$schema等额外的描述信息
        if (name.startsWith("$"))
            continue;
```
- **现状**: `RuleServiceImpl.executeRule`（`nop-rule-core/src/main/java/io/nop/rule/core/service/impl/RuleServiceImpl.java:63`）执行 `ruleRt.setInputs(request.getInputs())`。`_RuleRequestBean._inputs` 字段无默认值（`nop-rule-api/.../beans/_gen/_RuleRequestBean.java:47` 为 `private Map<String,Object> _inputs;`），请求体不携带 inputs 时为 null；`RuleRuntime.inputs` 初始即为 null。接口 `IRuleRuntime` 的 default 方法 `getInput`/`setInput` 均对 null 做了防御（`IRuleRuntime.java:38-52`），说明接口契约允许 inputs 为 null，本实现却未防御。
- **风险**: 通过 GraphQL/REST 调用 `RuleService__executeRule` 且请求不带 inputs 字段（例如执行全部为常量/computed 输入的规则）时，抛出裸 NullPointerException 而非业务异常（如 ERR_RULE_INPUT_VAR_NOT_ALLOW_EMPTY），错误信息无任何上下文。
- **建议**: `checkInputs` 开头判空（`Map<String,Object> inputs = ruleRt.getInputs(); if (inputs == null) return;`），或在 `RuleServiceImpl.executeRule` 中将 null 归一化为空 Map。
- **误报排除**: 已确认所有成功编译的规则必带 NormalizeInputExecutableRule 包装（RuleModelCompiler 第 64 行无条件包装，且 Guard.notEmpty 使无输入规则无法编译，见 P2-3），即 NPE 路径存在于每个可执行规则的调用链上；已核对 GraphQL 入口 `RuleService.executeRule`（生成接口）直接透传 request bean，无中间层补默认值。

### [P1] Excel 规则单元格注释配置变量的白名单校验逻辑写反，非法配置名永远不报错

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java:443`
- **维度**: D1（逻辑错误/复制粘贴错误）
- **证据**:
```java
Map<String, ValueWithLocation> vars = MultiLineConfigParser.INSTANCE.parseConfig(loc, cell.getComment());
if (vars != null) {
    if (COMMENT_VAR_NAMES.containsAll(vars.keySet())) {   // 只有全部合法时才进入校验块
        for (String varName : vars.keySet()) {
            if (!COMMENT_VAR_NAMES.contains(varName))     // 永远为 false，死代码
                throw new NopException(ERR_RULE_UNKNOWN_CONFIG_VAR)
                        ...
```
- **现状**: 外层条件 `COMMENT_VAR_NAMES.containsAll(vars.keySet())` 为 true 时表示所有 key 均在白名单内，内层 `!contains(varName)` 恒为 false，异常永不触发；一旦存在非法配置名，外层为 false 直接跳过整个校验块。校验完全失效，`ERR_RULE_UNKNOWN_CONFIG_VAR` 成为死代码。
- **风险**: 用户在单元格注释中拼写错误的配置（如 `valueExpre=`、`multMatch=true`）被静默忽略，导致 `valueExpr`/`multiMatch` 等配置不生效，规则执行结果与用户预期不符且无任何提示（例如本想多匹配却按单匹配执行，分支被跳过）。
- **建议**: 去掉外层 if，直接无条件循环校验每个 key。
- **误报排除**: 已读 `MultiLineConfigParser.parseConfig`（nop-excel 模块）确认解析器本身接受任意 key 不做白名单校验，本方法是唯一防线；已读 `COMMENT_VAR_NAMES` 定义（RuleConstants: `var/valueExpr/multiMatch/id`）与 `getCommentVar` 消费方确认拼错的 key 无法匹配任何读取逻辑，即静默丢弃。

### [P1] DaoRuleModelSaver 按 predicate 匹配复用节点，同父下重复 predicate 的分支被静默覆盖丢失

- **文件**: `nop-rule/nop-rule-dao/src/main/java/io/nop/rule/dao/model/DaoRuleModelSaver.java:64`
- **维度**: D1（边界条件/数据错乱）
- **证据**:
```java
Map<String, NopRuleNode> map = new HashMap<>();
for (NopRuleNode node : nodes) {
    map.putIfAbsent(node.getPredicate(), node);       // 以 predicate 串为唯一匹配键
}
...
for (RuleDecisionTreeModel child : children) {
    ...
    NopRuleNode node = map.get(predicate);
    if (node == null) {
        node = entity.newOrmEntity(NopRuleNode.class, true);
        map.put(predicate, node);
    }
    node.setPredicate(predicate);
    ...
    node.setSortNo(index);                            // 第二个同 predicate 分支覆盖第一个
    node.setLabel(child.getLabel());
    node.setOutputs(buildOutputs(child));
```
- **现状**: 增量更新按 predicate JSON 字符串在兄弟节点集合中匹配复用。若用户新模型中同一父节点下两个分支的 predicate 完全相同（复制粘贴常见失误），第二个分支会命中第一个分支已复用的同一 `NopRuleNode`，覆盖其 sortNo/label/outputs/children，且返回的 list 中同一节点引用出现两次。
- **风险**: 保存后一个分支的信息（输出、子树）被静默丢失/覆盖，规则树结构与用户模型不一致，产生错误决策结果且无报错。
- **建议**: 匹配命中后将该 key 从 map 中移除（`map.remove(predicate)` 实现一次性消费），重复 predicate 时强制新建节点；或检测到重复时抛出明确的建模错误。
- **误报排除**: 已读调用链 `NopRuleDefinitionBizModel.importExcelFile → saveRuleModel → updateNodes` 确认无前置去重校验；已读 `DaoRuleModelLoader.buildRuleModelNode/buildRuleTree` 确认加载侧按 `TreeIndex.buildFromParentId` 重建，无法恢复被覆盖的分支；已确认 `parseOutputVars`（Excel 解析）不阻止同级相同条件（条件可经 valueExpr 表达式产生等价 predicate）。

### [P2] 规则未命中时 mandatory 输出直接抛异常，"未命中"信号被错误替代

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/NormalizeOutputExecutableRule.java:49`
- **维度**: D1（边界条件）/ D8（语义契约）
- **证据**:
```java
boolean b = rule.execute(ruleRt);
if (b) {
    for (RuleOutputDefineModel output : outputs) {
        aggOutput(output, ruleRt);      // 仅命中时执行聚合与 defaultExpr 兜底
    }
}

for (RuleOutputDefineModel output : outputs) {
    Object value = ruleRt.getOutput(output.getName());
    if (StringHelper.isEmptyObject(value) && output.isMandatory())
        throw new NopException(ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY)...  // 未命中也检查
```
- **现状**: `aggOutput`（含 defaultExpr 缺省值兜底，第 86-91 行）只在命中（b=true）时执行，而 mandatory 检查无条件执行。规则不匹配时输出必然为空，mandatory 输出必然抛 ERR_RULE_OUTPUT_VAR_NOT_ALLOW_EMPTY。
- **风险**: 配置了 mandatory 输出的规则，"未命中"这一正常业务分支无法以 `ruleMatch=false` 形式返回给调用方，而是以异常中断；且未命中时 defaultExpr 兜底逻辑被跳过，两个语义互相矛盾。
- **建议**: mandatory 检查应仅在 b=true 时执行（或将 defaultExpr 兜底移到未命中路径），保证未命中可正常返回。
- **误报排除**: 已读 `IExecutableRule.executeForOutputs/executeForResult`（IRuleRuntime 调用方）确认 `match=false` 是预期的正常返回值；已读 `aggOutput` 全文确认 defaultExpr 只在 list==null 且被调用时生效；未发现任何上层 catch 将该异常转译回"未命中"。

### [P2] RuleRuntime.logMessage 在规则求值热路径上无条件输出 INFO 日志

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/RuleRuntime.java:214`
- **维度**: D6（性能隐患）
- **证据**:
```java
@Override
public void logMessage(String message, String ruleNodeId, String ruleNodeLabel) {
    addToLogFile(message, ruleNodeId, ruleNodeLabel);   // 无条件执行
    ...
}

protected void addToLogFile(String message, String ruleNodeId, String ruleNodeLabel) {
    LOG.info("rule-log:message={},ruleNodeId={},ruleNodeLabel={}", message, ruleNodeId, ruleNodeLabel);
}
```
- **现状**: `RuleDecider.test`（RuleDecider.java:87-98）与 `ExecutableRule.execute`（ExecutableRule.java:74-89）在每个决策节点匹配/不匹配时都调用 logMessage（节点带 id 或 label 即触发，Excel 导入的节点 id 默认为单元格位置非空），每次都同步打一条 INFO 日志，与 collectLogMessage 开关无关。
- **风险**: 决策矩阵/决策树求值是高频热路径，每次规则执行产生 O(节点数) 条 INFO 日志；多匹配大矩阵（编译期上限 499x499）与批量规则调用场景下日志量爆炸，拖慢执行并淹没其他日志。
- **建议**: addToLogFile 增加 `if (LOG.isDebugEnabled())` 或独立开关（如仅 collectLogMessage 或 trace 级别时输出），或按 ruleName 采样。
- **误报排除**: 已读 RuleDecider/ExecutableRule 的 test/execute 确认 logMessage 在每次节点判定时触发且无开关拦截；已确认 Excel 解析侧 buildRuleNode 为每个节点设置非空 id（CellPosition.toABString），即默认每个节点都会打日志。

### [P2] 无输入变量定义的规则模型编译抛裸 IllegalArgumentException，无规则名/位置上下文

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/compile/RuleModelCompiler.java:64`
- **维度**: D4（错误处理）/ D8（契约）
- **证据**:
```java
rule = new NormalizeInputExecutableRule(ruleModel.getLocation(),
        KeyedList.fromList(ruleModel.getInputs(), RuleInputDefineModel::getName), rule);
// NormalizeInputExecutableRule 构造函数：
this.inputDefines = Guard.notEmpty(inputDefines, "inputDefines");
```
- **现状**: `rule.xdef` 对 `<input>` 未强制至少一个（非 mandatory 列表）；`_RuleModel._inputs` 默认为空 KeyedList。规则模型不含任何 input 时（手写 rule.xml 的常量规则、或模型构造失误），compileRule 在此抛 `IllegalArgumentException("IsEmpty:inputDefines")`。
- **风险**: 加载/编译期抛裸异常（无 ruleName、无 SourceLocation、无错误码），调用方（ResourceComponentManager 加载链）只能看到无上下文的 IllegalArgumentException，排障困难；同时使 xdef 允许的合法模型无法编译。
- **建议**: 要么在 xdef 层强制 input 至少一个并给出带位置的错误，要么允许空 inputs 时跳过 NormalizeInput 包装；至少应抛带 `source(ruleModel)` 与 ruleName 的 NopException。
- **误报排除**: 已读 `Guard.notEmpty`（nop-api-core Guard.java:88-92）确认空集合抛 IllegalArgumentException；已读 `_RuleModel._inputs` 默认值 `KeyedList.emptyList()` 与 `RuleModel.initVarMap`（null 时设为空 ArrayList）确认空 inputs 可到达此处；已读 `rule.xdef` 全文确认 input 无出现次数下限。

### [P3] RuleExprParser.restRuleOrExpr 的 checkRightValue 误传 AND 操作符文本（复制粘贴）

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/expr/RuleExprParser.java:79`
- **维度**: D1（复制粘贴错误，仅影响报错文案）
- **证据**:
```java
protected Expression restRuleOrExpr(TextScanner sc, Expression x) {
    if (consumeOrOp(sc)) {
        Expression y = ruleExpr(sc);
        checkRightValue(sc, XLangOperator.AND.getText(), y);   // OR 场景误用 AND 文案
```
- **现状**: OR 表达式右值缺失时错误提示显示 `and` 而非 `or`（对照 restRuleAndExpr:91 使用 AND.getText()）。
- **风险**: 仅误导用户排障，无行为错误。
- **建议**: 改为 `XLangOperator.OR.getText()`。
- **误报排除**: 已对照相邻方法 restRuleAndExpr 的对称实现确认此处应为 OR。

### [P3] FilterBeanToPredicateTransformer.visitOr 空 children 返回 ALWAYS_TRUE（空析取语义存疑）

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/compile/FilterBeanToPredicateTransformer.java:148`
- **维度**: D1（边界条件）
- **证据**:
```java
public IEvalPredicate visitOr(ITreeBean filter, IVariableScope scope) {
    List<? extends ITreeBean> children = filter.getChildren();
    if (children == null || children.isEmpty())
        return IEvalPredicate.ALWAYS_TRUE;   // 空析取恒真，与直觉(恒假)相反
```
- **现状**: 空 `<or>` 编译为恒真谓词。
- **风险**: 逻辑上空析取应为 false；当前空 or 使任意条件命中。但与平台其他实现完全一致（见误报排除），实际触发还需 DSL 允许空 or 节点。
- **建议**: 平台层面统一修正（FilterBeanEvaluator/FilterBeanExpressionCompiler 同步），或由 xdef 禁止空 or。
- **误报排除**: 已读 `FilterBeanEvaluator.visitOr`（nop-core）与 `FilterBeanExpressionCompiler.visitOr`（nop-xlang），两者对空 children 同样返回 true，故这是平台统一惯例而非本模块独有缺陷，降为 P3 提示。

### [P3] parseMatrixOutputs 抛裸 IllegalArgumentException 且无位置信息

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java:609`
- **维度**: D4（错误处理）
- **证据**:
```java
ExcelCell topCell = (ExcelCell) getRealCell(table, outBeginRow - 1, j);
if (topCell == null)
    throw new IllegalArgumentException("null top cell");
```
- **现状**: 决策矩阵输出区解析遇空表头单元格抛裸异常，无单元格位置、无错误码，且为模块内唯一的 bare IllegalArgumentException。
- **风险**: 用户 Excel 格式错误时得到不可理解的报错。
- **建议**: 改用 `ERR_RULE_INVALID_OUTPUT_CELL` + `CellPosition.toABString(i, j)` 参数。
- **误报排除**: 已对照同文件其他错误路径（如第 623-625 行）确认平台惯例是 NopException + ARG_CELL_POS。

### [P3] requireRealCell 命名为 require 却返回 null，契约与命名不符

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java:331`
- **维度**: D8（契约一致性）
- **证据**:
```java
private ExcelCell requireRealCell(ExcelTable table, int rowIndex, int colIndex) {
    ICell cell = table.getCell(rowIndex, colIndex);
    if (cell == null)
        return null;                       // require 语义却返回 null
    if (cell.isProxyCell())
        throw new NopException(ERR_RULE_NOT_ALLOW_MERGED_CELL)...
```
- **现状**: 调用方 `parseOutputCell`（第 644-647 行）不得不再次判 null，说明契约混乱。
- **风险**: 维护性风险：后续调用者按 require 语义使用可能漏判 null。
- **建议**: 改名 getRealCellChecked 或统一为抛异常语义。
- **误报排除**: 已核对全部两个调用点（parseMatrixOutputs:614、parseOutputCell:644），后者已判 null 证明 null 返回是现实路径。

### [P3] DaoRuleModelSaver.saveRuleModel 篡改传入的 ruleModel（副作用）

- **文件**: `nop-rule/nop-rule-dao/src/main/java/io/nop/rule/dao/model/DaoRuleModelSaver.java:34`
- **维度**: D7/D1（可维护性）
- **证据**:
```java
public void saveRuleModel(RuleModel ruleModel, NopRuleDefinition entity) {
    RuleDecisionTreeModel tree = ruleModel.getDecisionTree();
    ruleModel.setDecisionTree(null);          // 修改调用方的模型对象
```
- **现状**: 为避免树结构重复序列化而将传入模型的 decisionTree 置 null，调用方（NopRuleDefinitionBizModel.importExcelFile）此后若再使用该 ruleModel 的 decisionTree 将得到 null。
- **风险**: 当前调用链中 setRuleType 在 save 之前执行，暂无实际错误；但调用顺序一旦调整即引入隐蔽 bug。
- **建议**: 在方法内克隆模型或序列化时局部排除 decisionTree，避免修改输入。
- **误报排除**: 已读唯一调用链 `NopRuleDefinitionBizModel.importExcelFile`（第 125-133 行）确认当前顺序恰好无错、但 ruleModel 后续仅用于无树依赖的 validateModel，属侥幸正确。

### [P3] getNonEmptyRowBound 的 start 参数从未使用

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java:124`
- **维度**: D1（死代码/可维护性）
- **证据**:
```java
private int getNonEmptyRowBound(ExcelTable table, int start) {
    for (int i = 0, n = table.getRowCount(); i < n; i++) {   // 始终从 0 扫描，start 未用
        ICell cell = table.getCell(i, 0);
```
- **现状**: 两个调用点均传起始行（表头边界），但循环从第 0 行开始；因合并主格的 getMergeDown 会跳过 proxy 行（已验证 ExcelCell proxy 的 value 为 null 但循环 i += mergeDown 会跨过它们），当前从 0 扫描与从 start 扫描结果一致，参数为死参数。
- **风险**: 若表头区第 0 列存在真实空单元格（非合并 proxy），会提前截断输出区边界导致静默丢行；参数意图（从 start 开始）与实现不一致，维护时易引入错误。
- **建议**: 删除 start 参数或真正从 start 开始扫描。
- **误报排除**: 已读 ExcelTable.newProxyCell 与 ExcelCell proxy 的 value 委托行为（proxy value 为 null）并人工推演合并行跳过逻辑，确认当前行为等价、无现实 bug，故仅 P3。

### [P3] RuleOutputValueModel.varModel 有写入无消费（死代码）

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/RuleOutputValueModel.java:20`
- **维度**: D7（可维护性）
- **证据**:
```java
private RuleOutputDefineModel varModel;
...
public RuleOutputDefineModel getVarModel() { return varModel; }
```
- **现状**: 全仓库唯一写入点是 `RuleTableModelParser.parseOutputVars:315`，`getVarModel()` 无任何消费者。
- **风险**: 无运行时危害，误导维护者以为该关联生效。
- **建议**: 删除或在编译期真正使用（如类型校验）。
- **误报排除**: 已 grep 全仓库 getVarModel/setVarModel 确认无消费点。

### [P3] 规则执行日志落库链路未接通（IRuleLogMessageSaver 无实现、NopRuleLog 无写入方）

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/service/impl/RuleServiceImpl.java:76`
- **维度**: D8（功能契约）
- **证据**:
```java
if (ruleRt.isCollectLogMessage()) {
    ret.setLogMessages(ruleRt.getLogMessages());
    if (saveLogMessage && logMessageSaver != null)
        logMessageSaver.saveLogMessages(ruleRt.getLogMessages(), ruleRt);
}
```
- **现状**: `saveLogMessage` 默认 false 且无 beans.xml 配置注入；`IRuleLogMessageSaver` 在全仓库无实现类；`NopRuleLog` 表（规则执行日志）全仓库无写入方。且 saveLogMessage 生效的前提是客户端 selection 请求了 logMessages 字段（第 66-68 行），两个功能不必要地耦合。
- **风险**: 平台内规则执行日志持久化功能整体处于未接通状态，仅前端查询到空表；saveLogMessage 配置即使打开，客户端不请求 logMessages 也不落库。
- **建议**: 提供缺省 saver 实现或删除预留开关；日志收集与日志落库的触发条件解耦。
- **误报排除**: 已 grep 全仓库（含 nop-file、nop-wf 等模块）确认 IRuleLogMessageSaver 无实现、NopRuleLog 无写入门路（仅 CrudBizModel 提供手动 CRUD）。

## 其他核实无误的点（误报排除记录）

- `DaoRuleModelLoader.loadRuleDefinition` 的 `addOrderField(ruleVersion, true)`：已查 `QueryBean.addOrderField(String, boolean desc)` 签名，true 为降序，未指定版本时取最新激活版本，正确。
- `ExecutableMatrixRule` 非 multiMatch 分支 `matched` 初始 0 的误用疑虑：RuleDecider.execute 返回 true 必然至少回调一次 task（归纳可证），matched 必已被赋值，无越界。
- `compileMatrix` 的 `CellPosition.toABString(row, col)` 与 `RuleTableCellModel.setPos(CellPosition.of(rowLeaf, colLeaf))` 一致性：已验证 `KeyedList` key 提取经 `StringHelper.toString`（CellPosition.toString 即 toABString），索引一致。
- `RuleModel.init()` 对 `matrix.getRowDecider()` 的潜在 NPE：xdef 中 rowDecider/colDecider 均 `xdef:mandatory="true"`，DSL 解析保证非空。
- `NopRuleDefinitionBizModel.importExcelFile` 路径遍历疑虑：`DaoResourceFileStore.decodeFileId/getFileResource` 按 fileId 主键查询并校验 bizObj/objId/field 绑定，无路径拼接。
- `NopRuleDefinition.getBeforeExecute`（单参）与 `setBeforeExecute`（双参）不对称疑虑：已读 `XmlOrmComponent` 两方法实现，均操作根节点下同名子节点，行为对称。
- D7 平台规范：无 `@Inject private` 字段（DaoRuleModelLoader/RuleServiceImpl 为 setter 注入，BizModel 为包私有字段注入）；无 Spring `@Value`/Spring 依赖；所有 bean 均在 beans.xml 注册（RuleManager→rule-defaults.beans.xml、RuleServiceImpl→_api-impl.beans.xml、DaoRuleModelLoader→app-rule-dao.beans.xml 并经 dao-rule.register-model.xml 关联）。
