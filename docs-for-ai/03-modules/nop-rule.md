# nop-rule — 规则引擎

## 功能概览

nop-rule 是一个**确定性映射引擎**（决策树/决策矩阵），解决"条件组合 → 输出值"的确定性决策点。业务规则本质是条件表的映射，不是事实推理网——Rete、冲突消解、前向链等技术概念在 ERP 评分映射场景中与业务概念不直接对应，引入反而增加无谓复杂度。

- **决策树（TREE）**：基于条件分支的树形决策，逐层匹配
- **决策矩阵（MATX）**：基于多维度交叉的矩阵决策，行列交汇点取值
- **多输出**：一次匹配同时产出多个维度的输出值（如平衡记分卡：财务分、客户分、流程分、学习分 + 加权综合分）
- 规则版本管理（`ruleName` + `ruleVersion` 复合主键）
- 角色级访问控制（`NopRuleRole`）
- 执行日志（`NopRuleLog`）
- 支持 Excel / XML / YAML 三种定义格式，**Excel 为主流编辑方式**
- 内置输出聚合：min / max / sum / avg / weighted_avg / first / last / list
- `beforeExecute` / `afterExecute` XPL 钩子

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopRuleDefinition | `nop_rule_definition` | 规则定义（ruleType: TREE/MATX），modelText 存 XML |
| NopRuleNode | `nop_rule_node` | 决策树节点（条件谓词 + 输出） |
| NopRuleRole | `nop_rule_role` | 规则访问权限（按角色） |
| NopRuleLog | `nop_rule_log` | 规则执行日志 |

## 二重 DSL

理解 nop-rule 的关键：谓词条件和输出值用的是**两套不同引擎**。

### 谓词条件（决策树分支导航）

`RuleExprParser` 继承 `SimpleExprParser` + `ExprFeatures.ALL`，**有三层解析路径**，逐级降级：

**第一层 — 自动比较（parseExpr 入口）**：单值文本/数字/布尔直接包装为 `varName == value`

```
Winter     → season == 'Winter'
100        → guestCount == 100
true       → active == true
```

**第二层 — 运算符简写（ruleExpr）**：同一变量的比较链 + `not` / `in` / FilterOp

```
>= 3 and < 5      → guestCount >= 3 &amp;&amp; guestCount < 5
in [A,B]          → status in ['A', 'B']
not in [1,2]      → !(status in [1, 2])
not == 'VIP'      → !(level == 'VIP')
contains 'abc'    → name contains 'abc'
startsWith 'A'    → code startsWith 'A'
```

**第三层 — 全 XLang 表达式**：以上模式都不匹配时，**兜底到 `super.simpleExpr()` + `ExprFeatures.ALL`**，支持跨变量、算术、函数调用

```
// 跨变量比较（不同输入字段）
a + b > c * 2

// 调函数
round(score) >= 60

// 混合表达式
price * quantity > 1000 and level != 'Guest'
```

**编译流水线**：`RuleExprParser` → XLang AST → `ExpressionToFilterBeanTransformer`。对于无法分解为标准 filter-bean 的复杂表达式（左右均非简单标识符），`ExpressionToFilterBeanTransformer` 创建 `<expr value="{ast}"/>` 节点，由 `FilterBeanToPredicateTransformer.visitUnknown()` 用 `XLangCompileTool` 编译并包装为 `IEvalPredicate`。不支持的同级 op（如 `visitOther()`）会抛出 `ERR_FILTER_UNKNOWN_OP`。

**特殊值**：`-` 或空 = ALWAYS_TRUE（无条件分支）。

### 输出值（命中的分支赋值）

用 `XLangCompileTool.compileSimpleExpr()` → `SimpleExprParser` + `ExprFeatures.ALL`，**完整 XLang 表达式**：

```java
// 算术
count * 3 + weight * 5

// 函数调用
sum(weightedScores) / sum(maxScores) * 100

// 属性链
inspection.passCount / inspection.totalCount

// 字符串/数字/布尔字面量
'Winter Feast'
100
true
```

Excel 输出单元格或 XML `<valueExpr>` 中写的都是 XLang 表达式。

## Excel 编辑方式（主要方式）

规则定义支持 `.rule.xlsx` 格式，用 Excel 直接编辑。一个 workbook 包含以下工作表：

### Config 表

定义规则的输入变量、输出变量及元数据：

| 列 | 含义 |
|----|------|
| name | 变量名 |
| displayName | 显示名 |
| type | 类型（int/string/double/boolean 等） |
| mandatory | 是否必须 |
| default | 默认值表达式 |
| aggregate | 同名输出汇总方式（见"聚合方法"节） |
| useWeight | 是否启用权重（true/false） |

### Rule 表（决策树）

树形决策结构：

```
|   season    | guestCount |      dish      |
|-------------|------------|----------------|
|   Winter    |   >= 6     | 'Winter Feast' |
|             |     -      | 'Roastbeef'    |
|   Summer    |     -      | 'Light Salad'  |
```

- 第一行是变量名（对应 Config 的 input/output name）
- 首列单元格的 `rowSpan` 决定合并行数（树的层级）
- 单元格值为**谓词条件**（输入列）或 **XLang 表达式**（输出列）
- `-` 或空表示无条件（ALWAYS_TRUE）

### Rule 表（决策矩阵）

首格为 `M` 时解析为矩阵模式：

```
| 是否有房 | 是否已婚 |     | baseInfo.age |
|----------|----------|-----|--------------|
|    是    |    是    |     |   >= 30      |
|    否    |    否    |     |     -        |
|----------------------|-----|--------------|
|       result         |     |              |
|       400            | 300 |              |
|       300            | 200 |              |
```

- 上方是**列选择器**（列条件→列索引）
- 左方是**行选择器**（行条件→行索引）
- 中部矩阵单元格是输出值
- 矩阵单元格默认输出变量名为 `result`（`RuleConstants.VAR_RESULT`）

### RuleList / RuleTable 表

- **RuleList**：扁平列表格式，每行一个条件+输出映射
- **RuleTable**：矩阵的输出明细表，支持多输出变量

### Excel 单元格注释 DSL

在 Excel 单元格注释中可附加配置：

| 注释键 | 用途 |
|--------|------|
| `var: myProp` | 覆盖默认变量名（支持属性链：`obj.nested.prop`） |
| `valueExpr: myExpr` | 用自定义 XLang 表达式替代单元格文本 |
| `multiMatch: true` | 允许此节点匹配多个子分支 |
| `id: node123` | 自定义节点 ID |

## 决策树模式（TREE）

```xml
<rule ruleName="discount-rule" ruleVersion="1" x:schema="/nop/schema/rule.xdef">
    <input name="season" mandatory="true"/>
    <input name="guestCount" mandatory="true"/>
    <output name="dish"/>

    <decisionTree multiMatch="false">
        <predicate/>
        <children>
            <child id="1">
                <predicate><eq name="season" value="@:Winter"/></predicate>
                <children>
                    <child id="1.1">
                        <predicate><ge name="guestCount" value="@:6"/></predicate>
                        <output name="dish"><value>Winter Feast</value></output>
                    </child>
                    <child id="1.2">
                        <predicate><lt name="guestCount" value="@:6"/></predicate>
                        <output name="dish"><value>Roastbeef</value></output>
                    </child>
                </children>
            </child>
        </children>
    </decisionTree>
</rule>
```

执行逻辑：从根开始逐级匹配谓词，`multiMatch=false`（默认）时只走第一个匹配的子分支，`multiMatch=true` 时所有匹配分支都执行。

## 决策矩阵模式（MATX）

```xml
<rule ruleName="credit-matrix" ruleVersion="1">
    <input name="hasHouse"/>
    <input name="isMarried"/>
    <input name="baseInfo"/>
    <output name="result"/>

    <decisionMatrix>
        <rowDecider>
            <children>
                <child id="r1">
                    <predicate><eq name="isMarried" value="@:true"/></predicate>
                    <children>
                        <child id="r1.1">
                            <predicate><gte name="baseInfo.age" value="@:30"/></predicate>
                        </child>
                        <child id="r1.2">
                            <predicate/>
                        </child>
                    </children>
                </child>
            </children>
        </rowDecider>
        <colDecider>
            <children>
                <child id="c1">
                    <predicate><eq name="hasHouse" value="@:true"/></predicate>
                </child>
                <child id="c2">
                    <predicate/>
                </child>
            </children>
        </colDecider>
        <cells>
            <cell pos="A0"><output name="result"><value>400</value></output></cell>
            <cell pos="A1"><output name="result"><value>300</value></output></cell>
            <cell pos="B0"><output name="result"><value>300</value></output></cell>
            <cell pos="B1"><output name="result"><value>200</value></output></cell>
        </cells>
    </decisionMatrix>
</rule>
```

执行逻辑：行选择器确定匹配的行索引，列选择器确定匹配的列索引，行列交汇处的单元格输出被触发。

## 聚合方法

当决策树 `multiMatch=true` 或有多个匹配行时，同名输出变量会产生多个值。`aggregate` 控制如何汇总：

| 方法 | 含义 |
|------|------|
| `first` | 取第一个值（默认） |
| `last` | 取最后一个值 |
| `min` | 取最小值 |
| `max` | 取最大值 |
| `sum` | 求和 |
| `avg` | 取平均值 |
| `weighted_avg` | 按权重取加权平均 |
| `list` | 汇总为列表 |

### 加权聚合

`useWeight=true` 时启用权重机制。每个输出变量自动关联 `{变量名}__weight` 权重变量，匹配行同时产生值和权重，最终汇总时按权重计算。

## Java API

```java
@Inject
IRuleService ruleService;

// 执行规则（按名称+版本）
Map<String, Object> inputs = new HashMap<>();
inputs.put("season", "Winter");
inputs.put("guestCount", 4);
Map<String, Object> outputs = ruleService.evaluateRule("discount-rule", "1.0", inputs);

// 按模型路径加载执行
IRuleManager ruleManager = new RuleManager();
IRuleRuntime ruleRt = ruleManager.newRuleRuntime();
ruleRt.setInput("season", "Winter");
ruleRt.setInput("guestCount", 4);
Map<String, Object> result = ruleManager.executeRule("test/test-table", null, ruleRt);

// XPL 标签调用
<rule:Execute ruleName="discount-rule" ruleVersion="1"
    inputs="${{amount: 1000, level: 'VIP'}}"/>
```

## 与 task flow 的分工

nop-task 编排"步骤结构"（拓扑、顺序、分支、重试）。nop-rule 解决"确定性决策点"（条件→输出的映射）。两者正交：task flow 的某步可以调用 nop-rule 做决策，规则的 `beforeExecute`/`afterExecute` 钩子也可调用 task flow。

`nop-task` 和 `nop-rule` 是 Nop 平台 DSL 体系中分工明确的成员，都通过 VFS 加载、支持热更新，不需要引入外部规则引擎或工作流引擎。

## 设计哲学与平台集成

nop-rule 不是 Drools 等 Rete 推理引擎的替代品，而是 Nop 平台 DSL 体系中的**确定性映射层**。以下设计原则避免将不必要的技术复杂度引入业务：

- **业务规则 = 确定性映射表**，非事实推理网。Rete/冲突消解/前向链等技术概念在 ERP 评分场景中与业务概念不直接对应。
- **时间窗口/跨行聚合**由聚合根对象的业务属性和方法提供（如 `order.averageDailySales`），或通过上下文 `IEvalScope` 注入帮助函数。规则引擎不需要知道"窗口"，它只看到已算好的输入值。
- **VFS 热更新**：所有 DSL（rule / task / workflow / xsql 等）统一通过 `ResourceComponentManager` 从 VFS 加载，更新文件后自动清缓存重编译，nop-rule 自然继承此能力。
- **函数扩展**：通过 `IEvalScope` 上下文注入自定义帮助对象和方法，规则表达式直接调用 `helper.computeScore(...)`。
- **版本管理**：`ruleName` + `ruleVersion` 复合主键，支持规则生效期控制、回滚和追溯。

## 适用场景

| 场景 | 输入 | 输出 | 匹配方式 | 说明 |
|------|------|------|----------|------|
| **折扣定价** | 客户等级、金额、季节 | 折扣率 | 决策树 | 条件分支多，可多层 |
| **信用评分 / 授信** | 收入、负债、历史信用 | 额度 / 等级 | 决策矩阵 | 多维度交叉 |
| **审批路由** | 金额、部门、风险等级 | 审批链 | 决策树 | 一次性判定 |
| **自动补货** | 库存、提前期、预测 | 补货量 | 决策树 | 条件拼接 |
| **税率判定** | 品类、地区、客户类型 | 税率 | 矩阵 | 二维交叉 |
| **提成计算** | 销售额、品类、等级 | 提成率 | 矩阵 | 多维度 |
| **质检定级** | 缺陷数、严重程度 | A/B/C | 树 | 分层判定 |
| **平衡记分卡** | 各维度 KPI 值 | 财务分 / 客户分 / 流程分 / 学习分 + 综合分 | 多输出树/矩阵 | 一次匹配多输出 |
| **异常阈值** | 偏差率、趋势、历史 | 正常/警告/异常 | 树 | 分层判定 |
| **科目映射** | 原始科目、业务类型、地区 | 目标科目 | 矩阵 | 查表映射 |

### 不适合或不需要通过规则引擎的场景

- **步骤编排**（多步流程 OR 依赖链）：用 nop-task 而非 nop-rule。但 task flow 的某步可以调用 nop-rule 做决策，两者正交。
- **纯算术/公式计算**（`score = 0.3 * x1 + 0.2 * x2`）：直接在 Entity/BizModel 方法或 XLang 表达式中计算即可。
- **逐行遍历明细再汇总**：规则引擎不迭代集合。Java 循环调用 `ruleService.evaluateRule()` 或外部预聚合后作为单值输入。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-rule-core` | 规则核心引擎（编译/执行/Excel 解析） |
| `nop-rule-api` | API DTO |
| `nop-rule-dao` | ORM 实体与 DAO |
| `nop-rule-service` | 业务逻辑（含文件导入） |
| `nop-rule-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-rule/model/nop-rule.orm.xml` |
| XDEF 模式 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/rule.xdef` |
| Excel 解析器 | `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/excel/RuleTableModelParser.java` |
| 规则编译器 | `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/compile/RuleModelCompiler.java` |
| 谓词解析器 | `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/expr/RuleExprParser.java` |
| 规则管理器 | `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/RuleManager.java` |
| 规则服务 | `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/service/impl/RuleServiceImpl.java` |

## 相关文档

- `../reusable-modules-overview.md` — 可复用模块概览
- `../03-modules/nop-task.md` — task flow（与 nop-rule 的分工）
- `../02-core-guides/architecture-principles.md` — DSL 优先原则
