# nop-rule 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-rule
- 文件数: 88（src/main/java，其中 14 个为 `_gen`/`_` 前缀生成文件，实际手写 74 个）
- 覆盖范围声明: 逐行深读 nop-rule-core 全部执行器/编译器/模型/Excel 解析器/表达式解析器/服务实现（27 个文件）、nop-rule-dao 的 DaoRuleModelLoader/DaoRuleModelSaver/实体扩展类、nop-rule-service 的 4 个 BizModel 与配置；api 层 beans/crud 接口、错误码常量类、app 启动类通读确认为平凡或 XGEN 生成代码。配置深读：`nop-kernel/nop-xdefs/.../rule.xdef`（契约基准）、`_vfs/nop/rule/imp/rule.imp.xml`、`rule-defaults/_api-impl/_dao/_service beans.xml`、两个 register-model.xml。生成文件（`_gen`、`_` 前缀）、target/、测试代码不在范围。为排除误报交叉验证了 nop-api-core（QueryBean/Guard）、nop-core（Underscore/CellPosition）、nop-commons（MathHelper）依赖语义。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 4 |
| P3 | 3 |

## 发现列表

### [P1] DecoratedExecutableRule.afterExecute 在正常执行路径上从不执行，与 xdef 契约直接矛盾

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/DecoratedExecutableRule.java:29-43`
- **维度**: D8（API/契约一致性）、D1（正确性）、D4（丢 cause）
- **证据**:
```java
public boolean execute(IRuleRuntime ruleRt) {
    try {
        if (beforeExecute != null)
            beforeExecute.invoke(ruleRt);
        boolean b = rule.execute(ruleRt);
        ruleRt.setRuleMatch(b);
        return b;                       // 正常路径直接返回，跳过 afterExecute
    } catch (Exception e) {
        ruleRt.setException(e);
        if (afterExecute != null) {
            afterExecute.invoke(ruleRt);  // 仅异常路径执行
        }
        throw NopException.adapt(e);
    }
}
```
- **现状**: 模型契约（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/rule.xdef:13-16`）明确定义：`<!-- 无论规则是否成功匹配，都会执行到这里 --> <afterExecute>xpl</afterExecute>`。而实现中 afterExecute 只在 catch 分支被调用，规则执行成功（无论匹配与否）时永不执行。excel 导入（rule.imp.xml Config sheet `afterExecute` 字段）与 DSL 均可配置该钩子。
- **风险**: 任何配置了 afterExecute 的规则（如审计埋点、上下文清理、结果修正）在正常路径上静默不执行；若 afterExecute 用于补充输出计算，将直接产生错误的决策输出。另外 catch 分支中 afterExecute 自身抛异常时，新异常直接取代原异常传播，原始异常 e 完全丢失（cause 链断裂）。
- **建议**: 将 afterExecute 移入 finally（或 try 成功路径末尾）保证"无论是否匹配都执行"；catch 中 afterExecute 的异常应包一层并保留原异常为 cause。
- **误报排除**: 全仓 grep 确认 afterExecute 语义无其他实现/包装（`RuleModelCompiler.compileRule:66-69` 直接用此装饰器）；xdef 注释为唯一契约来源，非测试代码可推翻。

### [P2] Excel 注释配置变量校验逻辑写反，ERR_RULE_UNKNOWN_CONFIG_VAR 永不可达，拼写错误被静默忽略

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java:443-454`
- **维度**: D1（正确性）、D4（校验失效）
- **证据**:
```java
Map<String, ValueWithLocation> vars = MultiLineConfigParser.INSTANCE.parseConfig(loc, cell.getComment());
if (vars != null) {
    if (COMMENT_VAR_NAMES.containsAll(vars.keySet())) {   // 只有全部合法才进入
        for (String varName : vars.keySet()) {
            if (!COMMENT_VAR_NAMES.contains(varName))     // 与外层条件矛盾，恒为 false
                throw new NopException(ERR_RULE_UNKNOWN_CONFIG_VAR)...
        }
    }
}
```
- **现状**: 外层 `containsAll` 为真时，内层 `!contains(varName)` 恒为 false，抛错分支是死代码；外层为假（存在未知变量）时直接放行。`COMMENT_VAR_NAMES = {var, valueExpr, multiMatch, id}`（RuleConstants.java:36）。
- **风险**: excel 单元格注释中的配置名拼错（如 `@multi-Match: true`、`@value-expr`）时静默忽略：multiMatch 退回默认 false（单分支匹配）、valueExpr 不生效走单元格文本——规则匹配语义悄然改变且无任何告警，在风控/定价场景是隐性配置事故源。该模块专门定义了 ERR_RULE_UNKNOWN_CONFIG_VAR 错误码，说明拦截是设计意图。
- **建议**: 去掉外层 `if (COMMENT_VAR_NAMES.containsAll(...))`，直接对每个 key 判断 `!contains` 即抛错（即原意应为"存在未知变量时报错"）。
- **误报排除**: 已确认 `MultiLineConfigParser.parseConfig` 返回的 key 即注释变量名，无其他归一化处理；错误码在 nop-rule 内无其他抛出点（全模块唯一引用即此处）。

### [P2] 空规则模型（无 decisionTree 子节点且无 decisionMatrix）编译时 NPE，坏模型可入库后才爆

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/compile/RuleModelCompiler.java:52-58`
- **维度**: D1（正确性/NPE）、D4（错误处理）
- **证据**:
```java
if (ruleModel.getDecisionTree() != null && ruleModel.getDecisionTree().hasChildren()) {
    rule = compileTree(ruleModel.getDecisionTree());
} else {
    rule = compileMatrix(ruleModel.getDecisionMatrix());   // decisionMatrix 可为 null
}
...
private RuleDecider compileDecider(RuleDecisionTreeModel node) {
    IEvalPredicate predicate = this.compilePredicate(node); // node == null 时 NPE
```
- **现状**: rule.xdef 中 `decisionTree` 与 `decisionMatrix` 均为可选元素。当模型二者皆无（或 decisionTree 存在但无 children）时，走 `compileMatrix(null)` → `compileDecider(null)` → `node.getPredicate()` 直接 NPE，无规则名、无位置信息。且保存侧校验存在缺口：`NopRuleDefinitionBizModel.validateModel`（NopRuleDefinitionBizModel.java:149-151）只调 `buildRuleModel`（仅 parse 不 compile），坏模型可成功入库；随后每次 `resolve-rule:` 加载（DaoRuleModelLoader.loadObjectFromPath:77 compileRule）均 NPE。可达路径现实：ruleType=TREE 且无任何 rule node 的规则定义（`DaoRuleModelLoader.buildRuleModelNode` 对空节点集仍生成空 children 的 decisionTree，`hasChildren()` 为 false）。
- **风险**: 规则保存成功但运行期每次解析抛裸 NPE，无任何定位信息，排查成本高；规则求值失败即业务决策失败。
- **建议**: compileRule 入口显式校验"tree 有 children 或 matrix 非空"，缺失时抛 `NopException`（带 ruleName/location）；`validateModel` 补充 compile 一步，把失败拦在入库前。
- **误报排除**: 已核对 `_RuleDecisionTreeModel.hasChildren()` 为 `!children.isEmpty()`；`DaoRuleModelLoader.buildRuleModelNode` TREE 分支恒创建 decisionTree 节点（children 可为空）并删除 decisionMatrix；DSL 路径两元素 xdef 均非 mandatory。

### [P2] 零输入变量的规则编译抛裸 IllegalArgumentException，无规则上下文，违反错误处理两档策略

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/NormalizeInputExecutableRule.java:35`（触发链：`RuleModelCompiler.java:64`）
- **维度**: D4（bare RuntimeException）、D7（平台规范：错误处理两档策略）、D8
- **证据**:
```java
// RuleModelCompiler.compileRule:64
rule = new NormalizeInputExecutableRule(ruleModel.getLocation(),
        KeyedList.fromList(ruleModel.getInputs(), RuleInputDefineModel::getName), rule);

// NormalizeInputExecutableRule:35
this.inputDefines = Guard.notEmpty(inputDefines, "inputDefines");
// Guard.notEmpty -> throw new IllegalArgumentException("IsEmpty:inputDefines")
```
- **现状**: rule.xdef 对 `<input>` 数量无下限约束（0 个合法），但编译期用 `Guard.notEmpty` 断言非空，零输入规则（如返回常量的开关型规则）触发 `IllegalArgumentException("IsEmpty:inputDefines")`。规则名、位置、修复建议全部缺失。对 rule.xlsx（xlsx-loader 只 parse 不 compile）该异常推迟到**首次执行**（RuleManager.getExecutableRule 懒编译）才爆。
- **风险**: 裸 IAE 违反"框架核心用 NopException + ErrorCode + .param(...)"的两档策略；若零输入确属非法，应在模型解析期以带上下文的错误拒绝，而非懒编译期抛裸异常。
- **建议**: 要么放宽为允许空输入（直接透传求值），要么在 compileRule 用 NopException（ERR_XXX + ruleName param）显式报错。
- **误报排除**: 已核对 `_RuleModel.getInputs()` 默认 `KeyedList.emptyList()`（非 null，NPE 排除）；Guard.notEmpty 实现确认抛 IllegalArgumentException；RuleDslModelLoader/xlsx 加载链确认懒编译时机。

### [P2] 决策矩阵解析对缺失顶栏单元格抛裸 IllegalArgumentException("null top cell")，无坐标上下文

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java:607-609`
- **维度**: D4（bare RuntimeException、异常吞噬上下文）
- **证据**:
```java
ExcelCell topCell = (ExcelCell) getRealCell(table, outBeginRow - 1, j);
if (topCell == null)
    throw new IllegalArgumentException("null top cell");
```
- **现状**: 矩阵 excel 顶栏（列维度表头）在合并单元格边界错位/缺失时走到此分支，抛裸 IAE。同文件其余所有校验（ERR_RULE_NOT_ALLOW_MERGED_CELL、ERR_RULE_INVALID_OUTPUT_CELL 等，均带 `ARG_CELL_POS` 参数）都规范使用 NopException，唯此处不一致。
- **风险**: excel 导入失败时用户只能看到 "null top cell"，无 sheet/坐标定位；违反错误处理策略（模块内同族校验已定义错误码体系）。全模块唯一的裸 `new RuntimeException` 族命中（grep 验证）。
- **建议**: 改为 `new NopException(ERR_RULE_INVALID_OUTPUT_CELL).param(ARG_CELL_POS, CellPosition.toABString(outBeginRow - 1, j))` 复用既有错误码。
- **误报排除**: 确认 `getRealCell` 对越界/空单元格返回 null 的路径可达（依赖 excel 合并区域形态），且同类 NPE 风险点（cell0 未判空）在空表场景由 ExcelTable 行列约束兜底，未列入。

### [P3] 每个规则节点每次求值无条件输出 INFO 日志

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/RuleRuntime.java:195-216`（调用方 `ExecutableRule.java:70-76`）
- **维度**: D6（性能隐患）
- **证据**:
```java
public void logMessage(String message, String ruleNodeId, String ruleNodeLabel) {
    addToLogFile(message, ruleNodeId, ruleNodeLabel);   // 无条件
    if (collectLogMessage) { ... }
}
protected void addToLogFile(...) {
    LOG.info("rule-log:message={},ruleNodeId={},ruleNodeLabel={}", ...);
}
```
- **现状**: 只要节点有 id 或 label，每次求值每个被访问节点（含 MISMATCH 分支）都打一条 INFO。excel 导入的规则 id 恒被赋值（`RuleTableModelParser.buildRuleNode:266-267` 以单元格坐标兜底），RuleList 导入 id 为 mandatory——即生产主流路径全部命中。
- **风险**: 高频决策（风控每笔交易）下日志量 = 求值次数 × 访问节点数，INFO 级别无法在线关闭（需改日志级别为 WARN），造成 IO 压力与日志淹没。
- **建议**: 降为 LOG.debug，或由配置项控制。
- **误报排除**: 已确认 logMessage 调用点仅 ExecutableRule/RuleDecider 且仅受 id/label 非空约束，不受 collectLogMessage 约束。

### [P3] DaoRuleModelSaver 永久破坏入参模型 + 静默丢弃无 predicate 的子节点

- **文件**: `nop-rule/nop-rule-dao/src/main/java/io/nop/rule/dao/model/DaoRuleModelSaver.java:33-35, 71-74`
- **维度**: D1（数据丢失）、D8（副作用契约）
- **证据**:
```java
public void saveRuleModel(RuleModel ruleModel, NopRuleDefinition entity) {
    RuleDecisionTreeModel tree = ruleModel.getDecisionTree();
    ruleModel.setDecisionTree(null);        // 永久清空，无恢复
    ...
    for (RuleDecisionTreeModel child : children) {
        index++;
        if (child.getPredicate() == null)
            continue;                        // 无 predicate 的子节点（含其整棵子树）静默不持久化
```
- **现状**: (1) `setDecisionTree(null)` 后从不恢复，方法返回后入参模型已被破坏；(2) predicate 为 null 的子节点（xdef 中 predicate 是可选元素，DSL 手写规则可合法存在，语义为恒真分支）被静默丢弃，重新加载后规则结构改变。当前唯一调用方 `NopRuleDefinitionBizModel.importExcelFile` 恰好在保存后丢弃模型、且两条 excel 导入路径都不产生 null predicate，故今日无实际资损，属潜在陷阱。
- **风险**: 后续任何复用入参模型或保存 DSL 来源模型的调用方会踩中：模型被静默篡改 / 恒真分支丢失导致匹配语义变化。
- **建议**: 在 finally 中恢复 decisionTree；对 predicate 为 null 的节点序列化为 always-true 而非跳过，或显式抛错拒绝保存。
- **误报排除**: 已 grep 确认 saveRuleModel 全仓仅 importExcelFile 一个调用方；两条 excel 导入路径（Rule sheet / RuleList sheet，后者 predicate mandatory）确实不产生 null predicate，据此定级 P3 而非 P2。

### [P3] RuleList 导入路径输出变量名硬编码 'RESULT'，与平台约定 VAR_RESULT="result" 漂移

- **文件**: `nop-rule/nop-rule-core/src/main/resources/_vfs/nop/rule/imp/rule.imp.xml:182,233`（对照 `RuleConstants.java:42`）
- **维度**: D8（契约一致性）
- **证据**:
```javascript
// rule.imp.xml normalizeFieldsExpr
record.outputs.push({ name: 'RESULT', value: record['ext:result'] });
```
```java
// RuleConstants.java / IExecutableRule.java:49
String VAR_RESULT = "result";
return outputs.get(RuleConstants.VAR_RESULT);   // executeForResult
```
- **现状**: 同一模块内两套结果变量名：矩阵 excel 路径（RuleTableModelParser:617-619）与 `executeForResult`/`getOutputFields` 默认值均用小写 "result"，RuleList 列表导入路径却写死大写 'RESULT'（测试 TestRuleExcelParser 也断言 'RESULT'，属历史既成行为）。
- **风险**: 用 RuleList 模板导入的规则，调用 `IExecutableRule.executeForResult` 恒取到 null；若 Config sheet 按平台默认声明输出名 "result"，汇总配置（aggregate）也无法命中 'RESULT' 输出列表。
- **建议**: 统一为 `RuleConstants.VAR_RESULT`，或在 imp.xml 中引用常量；至少在文档中固化唯一约定。
- **误报排除**: 已全仓 grep 'RESULT' 确认无后续归一化步骤将 'RESULT' 映射回 'result'；影响面限于 RuleList 导入 + executeForResult/默认输出声明组合。

## 已排查未列入的疑点（误报排除记录）

- **规则编译缓存并发（D3）**: `RuleManager.getExecutableRule` 读写均在 `synchronized(ruleModel)` 内；懒编译结果经 `ResourceComponentManager` 缓存（内部并发 Map 的 put/get 建立 happens-before）发布，执行器对象全为不可变 final 字段。无问题。
- **DAO 规则版本解析**: `DaoRuleModelLoader.loadRuleDefinition:106` 的 `addOrderField(PROP_NAME_ruleVersion, true)` 第二参为 desc（已核对 QueryBean.java:432），未指定版本时取**最新** ACTIVE 版本，正确。
- **矩阵 outputs 数组越界/空单元格 NPE（D1）**: 维度由 `getMaxLeafIndex()+1` 决定，leafIndex 由 `calcLeafIndex` 从 0 连续分配；编译器将全部格子填满（空单元格为 `IEvalAction.NULL_ACTION`，RuleModelCompiler:144-155），执行期 `outputs[rowIndex][colIndex]` 安全。
- **NormalizeOutput 遍历 outputs 为 null（D1）**: `_RuleModel._outputs` 默认 `KeyedList.emptyList()` 非 null。
- **聚合函数遇 null 元素（D1）**: excel 决策树空输出单元格（"-"）会向 output list 添加 null，但 `Underscore.min/max/sum`（SafeOrderedComparator/MathHelper null 容忍）与默认 last 聚合均可处理 null，无 NPE。
- **getNonEmptyRowBound 忽略 start 参数**: 因表头合并单元格的 mergeDown 跳过逻辑，从 0 扫描与从 start 扫描在合法表上等价，不单列。
- **D5 表达式注入面**: 规则表达式（predicate/valueExpr/beforeExecute/afterExecute）为管理员维护的受信模型内容，`allowUnregisteredScopeVar(true)` 仅放宽编译期未注册变量检查；executeRule 经标准 GraphQL 鉴权，未发现新增注入面。
- **D7 IoC 规范**: 全模块 4 处注入均合规——`@Inject` 用于 setter 或 package-private 字段（RuleServiceImpl、DaoRuleModelLoader、NopRuleDefinitionBizModel、NopRuleNodeBizModel），无 private 字段注入、无 @Value；bean 均在 `_vfs` 下 beans.xml 显式注册（nopRuleManager、RuleServiceSpi、nopDaoRuleModelLoader、各 BizModel）。
