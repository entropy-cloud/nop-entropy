# Adopt Excel as a Visualization Designer's Rule Engine NopRule

Decision trees and decision matrices are the intuitive forms of complex IF-ELSE logic that business users can easily understand, and they are also the most commonly used and useful parts in rule engines. While Drools, a common rule engine, provides more extensive functionality,
particular its so-called RETE algorithm, is effective for reusing and repeating expressions, in practical business applications, we rarely encounter situations where we must use the RETE algorithm. In most cases, we fall back to using decision tables and decision matrices.

> By logically arranging the nodes of decision trees and decision matrices, we can achieve optimization. However, for scenarios requiring the RETE algorithm, both business users and developers often find it difficult to configure, as it is not intuitive for business users to understand and set up, and even developers may struggle with its internal details. It is often more practical to manually implement rather than trying to optimize using RETE.

> Using decision tables and decision matrices is generally more effective than employing the RETE algorithm.

NopRule is a lightweight rule engine that can be embedded within Java programs or used as a remote service via microservices. It provides an online visualization design interface, allowing rules models to be stored in databases for dynamic updates while also supporting static model files without database storage.

Similar to the NopReport report engine, NopRule allows using Excel as a visualization design tool, directly importing rule models in Excel format, significantly simplifying both rule design and usage.

## Usage Methods

Follow the approach used in nop-quarks-demo, adding the following modules to your pom file:

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-rule-service</artifactId>
</dependency>

<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-rule-web</artifactId>
</dependency>
```

NopRule-Service provides backend rule services, while NopRule-Web includes the corresponding amis frontend management pages.

In `/_vfs/nop/demo/app.action-auth.xml`, import nop-rule.action-auth.xml to define functionality menus for testing.

```xml
<auth x:extends="/nop/auth/auth/nop-auth.action-auth.xml,/nop/sys/auth/nop-sys.action-auth.xml,
    /nop/rule/auth/nop-rule.action-auth.xml">
</auth>
```

After starting, access http://localhost:8080/#/NopRuleDefinition-main

![images/rule-definition-page.png](images/rule-definition-page.png)

## 1.1 Rule Calls

Call remote rule services via the [RuleService](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/nop-rule-api/src/main/java/io/nop/rule/api/RuleService.java) interface.

```javascript
Map<String, Object> inputs = new HashMap<String, Object>();
inputs.put("season", "Winter");
inputs.put("guestCount", 10);

RuleRequestBean request = new RuleRequestBean();
request.setRuleName("test-decision-table");
request.setInputs(inputs);

RuleResponseBean response = ruleService.executeRule(ApiRequest.build(inputs)).get();
Map<String, Object> outputs = response.getOutputs();
``` 

If using NopRule within a Java program, utilize the [IRuleManager](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/nop-rule-api/src/main/java/io/nop/rule/api/IRuleManager.java) interface to directly call rules without additional packaging and transformation.

# Rule Configuration and Execution

## 1. Model Import
To import the rule model, you can upload an Excel file containing the rule definitions.

![](images/import-excel-rule.png)

## 2. Testing Rules
After importing the rule model, click on the "Test Rules" button to trigger the test.

![](images/test-rule-input.png)

The test results will be displayed in the results page, which includes:
- The set of output variables
- Detailed logs from the execution process

## 3. Rule Execution
When testing a specific rule path, the system will evaluate the rules in the specified order and return the corresponding output variable.

## 2.1 Excel Model Configuration
The Excel model requires two sheets:
- `Rule` sheet: Defines decision rules for each node.
- `Config` sheet: Specifies input variables and their types.

![](images/rule-config.png)

## 2.2 Rule Expression
In the "Expression" column of the input table, you can enter rule expressions similar to the Friendly Enough Expression Language (FEEL). These expressions are parsed using the [RulExprParser](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/expr/RuleExprParser.java).

![](images/rule-expr.png)

## 1.2 Variable Handling
- Input Variables: These are the variables used in rule expressions.
- Output Variables: These are the variables returned by the rules after matching.

## 1.3 Rule Execution Steps
1. Set the input values for the current test case.
2. Execute the rule using the `executeRule` method.
3. Serialize the log messages and print them to the console.

```javascript
IRuleManager ruleManager = getRuleManager();
IRuleRuntime ruleRt = ruleManager.newRuntime();
ruleRt.setInput("season", "Winter");
ruleRt.setInput("guestCount", 4);

Map<String, Object> output = ruleManager.executeRule("test/test-table", null, ruleRt);
assertEquals("Roastbeef", output.get("dish"));

System.out.println(JsonTool.serialize(ruleRt.getLogMessages(), true));
```

## 2.3 Rule Expression Syntax
1. `true` and `false` can be directly used for matches.
2. `equals` (-) is used to check equality.
3. For numbers, strict comparison is enforced.
4. Enclosed strings use double quotes (`"example"`).
5. Variables are treated as values unless specified otherwise.

For complex conditions:
```javascript
rule "test"
  condition: input.age == 25 and input.name starts with "A"
```

6. The `and` (`&&`) and `or` (`||`) operators are supported, along with parentheses for grouping.

7. Custom operations like `contains`, `startsWith`, and `endsWith` are supported by the [FilterOp](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/model/query/FilterOp.java) class.

After rule matching, all global functions registered in XScript are automatically called.


Sometimes we want a Rule expression to match after evaluation, and then dynamically generate output variables using a complex function. At this point, you can call a custom function within the output variable section. In the【Config】Sheet, functions can be defined.

![images/rule-output-var.png](images/rule-output-var.png)

![images/rule-define-function.png](images/rule-define-function.png)


## 2.3 Decision Table Configuration

An example of a decision table configuration can be found in [decision-tree.rule.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/nop-rule-service/cases/io/nop/rule/service/entity/TestNopRuleDefinitionBizModel/testImport/input/decision-tree.rule.xlsx)
![decision-tree.png](decision-tree.png)

The content in the upper-left corner of a decision table must be the letter "T", representing "Table". This is followed by input columns and output columns. Each column in the input and output sections corresponds to an input/output variable name.


## 2.4 Decision Matrix Configuration

An example of a decision matrix configuration can be found in [decision-matrix.rule.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/nop-rule-service/cases/io/nop/rule/service/entity/TestNopRuleDefinitionBizModel/testDecisionMatrix/input/decision-matrix.rule.xlsx)
![decision-matrix.png](decision-matrix.png)

The content in the upper-left corner of a decision matrix must be the letter "M", representing "Matrix".

![images/matrix-input-var.png](images/matrix-input-var.png)

1. The first cell in each column on the left side and each row at the top can be configured with input variable names.
2. The cell can specify an expression using a batch comment (valueExpr). At this point, the cell text is only used as a label if not configured. If not configured, the cell text is used as the expression.
3. Multiple result values can be configured for output.


### 2.5 Online Editing

For decision tree models, editing can be done online. The configuration of matching conditions for the rule can be modified using AMIS's ConditionBuilder control.

![images/config-online.png](images/config-online.png)


## 3. Design Principle

In the overall design of the Nop platform, NopRule is responsible for abstracting complex judgment logic. The core part of this is the Filter model.

![images/rule-model.png](images/rule-model.png)


## 3.1 Filter Model

The Filter model is defined by [filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef) at the filter level. It can be used to describe complex and/or conditions.

```xml
<and>
    <or>
        <eq name="status" value="1" />
        <eq name="status" value="2" />
    </or>
    <gt name="amount" value="3" />
</and>
```

1. The Nop platform uses the Filter model for all conditional expressions throughout the system, corresponding to the ITreeBean type in Java programs.
2. Using XML and JSON bidirectional conversion, the Filter model can be saved as either XML or JSON format.
3. Advanced queries in the Nop platform use the Filter model, and the backend uses [FilterBeanToSQLTransformer](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/lang/sql/FilterBeanToSQLTransformer.java) to convert it into SQL statements.
4. The AMIS ConditionBuilder control can save complex conditional expressions as Condition objects using [ConditionExprHelper](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-web-page/src/main/java/io/nop/web/page/condition/ConditionExprHelper.java). This helper class handles the bidirectional conversion between Condition and Filter models.


The Filter model can be compiled using the `[FilterBeanToPredicateTransformer](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/model/compile/FilterBeanToPredicateTransformer.java)` to obtain the `IEvalPredicate` interface, and execute the filtering logic directly in memory.

6. The Filter model can also be executed using the `[FilterBeanEvaluator](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/model/query/FilterBeanEvaluator.java)` in memory.

7. The Filter model and Expression language (Expression) can leverage the `[FilterBeanExpressionCompiler](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/xpl/tags/FilterBeanExpressionCompiler.java)` and `[ExpressionToFilterBeanTransformer](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/expr/filter/ExpressionToFilterBeanTransformer.java)` for reversible conversion,

---


## 3.2 Schema Model

In the Nop platform, all places where object types need to be defined uniformly use the schema model. This model is defined by the metamodel `[schema.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/schema.xdef)`.

1. The XDef metamodel and Schema model can be converted between each other. XDef is used to define XML structures, while Schema is used to define objects and JSON structures.
2. The `[SimpleSchemaValidator](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/xmeta/SimpleSchemaValidator.java)` can check whether a value meets the schema requirements.
3. The `[XSchemaToJsonSchema](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/java/io/nop/xlang/xmeta/jsonschema/XSchemaToJsonSchema.java)` can convert the schema object into a JSON Schema definition.
4. The `[ConditionSchemaHelper](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-web-page/src/main/java/io/nop/web/page/condition/ConditionSchemaHelper.java)` is responsible for converting input variables in the rule model to the schema definitions supported by the ConditionBuilder control.

---


## 3.3 Excel Data Model

The Nop platform provides a bidirectional conversion between the Excel data model and Java domain model objects, enabling Excel parsing and exporting objects to Excel files without programming. For detailed information, refer to `[excel-import.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/report/excel-import.md)`.

* The NopRule class uses standard Excel conversion technologies for parsing the Config form.

```
RuleModel rule = ImportModelHelper.parseSheet(sheetModel, configSheet, compileTool, scope, RuleModel.class);
```

* NopRule uses Excel format API models to define external service interfaces and automatically generates corresponding interface definitions and service framework classes. See [nop-rule.api.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-rule/model/nop-rule.api.xlsx)

## Summary

NopRule's implementation adopted the basic technical strategy of the Nop platform:

1. Define domain models using meta-models
2. Generate bidirectional conversions between models through automatic deduction to minimize hard-coding
3. Use reusable generic models, such as Filter and Expression, to assemble more complex domain models like RuleModel

Based on reversible computation theory, the low-code platform NopPlatform has been open-sourced:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible computation principles and introduction of the Nop platform on Bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

