# 2026-10-02 WI3 测试暴露的产品缺陷嫌疑清单（plan 2306 收口：1 already-fixed、2/3/4 已修）

> 来源：plan 2295（WI3 nop-xlang 补强）执行期发现。各项以缺陷锚定测试固化（记录现状而非错误行为），修复独立立项。

## 1. ExpressionToFilterBeanTransformer 常量在左比较语义反转（P1 嫌疑）

- Problem：`io.nop.xlang.expr.filter.ExpressionToFilterBeanTransformer.transformBinary` 计算了 `reverseOp` 但只做非空校验，实际仍用原始 `filterOp`——`3 < a`（语义等价 `a > 3`）生成 `lt(a,3)`。
- 锚定：`TestExpressionToFilterBeanTransformer.testCompareWithConstantOnLeftIsSwapped`。
- 影响：常量在左的过滤表达式生成方向相反的 SQL/过滤条件。

## 2. CallExpression.getArgument(i) 无越界保护（P2 嫌疑）

- Problem：`arguments.get(i)` 直取——3/4 参数的 `between(...)` 在 `transformCall` 取第 4/5 个排除标记时抛 `IndexOutOfBoundsException`，仅 5 参数形式可用。
- 锚定：两个缺陷锚定测试。

## 3. XSchemaToJsonSchema union 分支 anyOf 成员类型错误（P2 嫌疑）

- Problem：union 分支拼装了转换后的 `list` 却 `ret.put("anyOf", schemas)` 放入原始 ISchema 列表——anyOf 成员不是 JSON Schema Map。
- 影响：含 union 类型的 xmeta 转 JSON Schema 产物不合法。

## 4. JavaToXLangTransformer 类字段全部丢失（P2 嫌疑）

- Problem：janino 3.1.12 的 `getMemberTypeDeclarations()` 不含字段声明 → 类字段转换后丢失；原始类型返回名的 ParameterizedTypeNode typeName=null。
- 锚定：缺陷锚定断言。
- 修复方向：改用 janino 反射补齐字段遍历，或升级 janino 后复核。

## 5. JsPromise 三处错误路径偏离 JS 语义（既有审计项，未触碰）

- 既有 `TestJsPromise`（G2-10-01）已锚定现状：executor 同步/异步抛错转 rejected、rejected 经仅成功回调的 then 透传可被 catch、finally 回调抛错转 rejected/正常透传忽略返回值。
- 修复立项时直接复用现有测试为回归基线，与 2026-09-30 审计发现合并处理。


## Fix（2026-10-03 plan 2306 回填）

- **1 常量在左比较反转：`fixed`**。transformBinary 已计算 reverseOp 但仍用原 filterOp。修复：`FilterBeans.compareOp(reverseOp.toFilterOp(), rightName, leftValue)`（switchLeftRight 仅对可逆比较符返回非空，toFilterOp 必非 null）。回归：testCompareWithConstantOnLeftIsSwapped 翻转为 gt/ge/le/lt/eq/ne 全谱正向断言。爆炸半径：ExpressionToFilterBeanTransformer 仅供表达式→FilterBean 转换（nop-xlang 855 测试全绿，docs-for-ai 查询语义描述与修复后行为一致——`3 < a` 本就语义等价 `a > 3`，文档无需变更）。
- **2 CallExpression.getArgument 越界：`fixed`**。getArgument(i) 越界返回 null（负索引仍 fail-fast），3/4 参数 between 合法、排除标记缺省 false；参数数 <3/>5 仍报既有 ERR_FILTER_OP_INVALID_ARG_COUNT（复用，未新增错误码）。回归：testBetweenWithThreeAndFourArgsIsSupported + testBetweenWithFourArgsIsSupported（两个特征化用例翻转）。
- **3 union anyOf 原始 ISchema：`fixed`**。`ret.put("anyOf", list)` 放转换后 JSON Schema Map。回归：testUnionSchemaBecomesAnyOf 强化断言（成员必须是 Map 且携带 type，string/integer 两分支）。
- **4 JavaToXLangTransformer 字段丢失：`fixed`**。janino 3.1.12 的 getMemberTypeDeclarations() 只含嵌套类型；改从 `fieldDeclarationsAndInitializers` 提取（含多 declarator 展开），并补原始类型 typeName（Java.Primitive → name 小写）。回归：TestJavaToXLangTransformer 字段断言翻转（names/value 字段 + 类型名 + 原始类型 int）。
- **5 JsPromise 三处偏离：`already-fixed`**。rev2 审查改判成立——commit `df4e4f8fa7`（plan 2282-WS4）已修复，TestJsPromise 现为正向断言。本 plan 不再列入执行条目。

## 语义观察（已写入测试注释，不构成缺陷指控）

- `DeltaDiffer.diff` 会 `detachChildren` 消费 xb（副作用）。
- `removeDuplicateAttr` 在 prepend/append 公共前后缀消除时会把同值 `x:override` 一并去除。
- `markRemoved` 仅在节点显式设置 `uniqueAttr` 字段时清除非唯一属性（解析节点不自动识别 id/name）。

## Affected Files

- nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/filter/ExpressionToFilterBeanTransformer.java
- nop-kernel/nop-xlang/src/main/java/io/nop/xlang/ast/CallExpression.java
- nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xmeta/jsonschema/XSchemaToJsonSchema.java
- nop-kernel/nop-xlang/src/main/java/io/nop/xlang/janino/JavaToXLangTransformer.java
- 对应 src/test 下 TestExpressionToFilterBeanTransformer / TestXSchemaToJsonSchema / TestJavaToXLangTransformer
