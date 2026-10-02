# 2026-10-02 WI3 测试暴露的产品缺陷嫌疑清单（未修，待独立立项）

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

## 语义观察（已写入测试注释，不构成缺陷指控）

- `DeltaDiffer.diff` 会 `detachChildren` 消费 xb（副作用）。
- `removeDuplicateAttr` 在 prepend/append 公共前后缀消除时会把同值 `x:override` 一并去除。
- `markRemoved` 仅在节点显式设置 `uniqueAttr` 字段时清除非唯一属性（解析节点不自动识别 id/name）。
