# Record Mapping

本页回答三个高频问题：`record-mapping` 默认用来做什么，字段级条件校验应该放哪一层，以及 `gateway` 的 `bodyMapping` 到底只是映射还是也承担校验。

## 默认结论

1. `record-mapping` 默认是**字段变换 + 类型转换 + 条件校验**的统一入口，不只是简单拷贝字段。
2. 当规则依赖当前 route、来源字段、上下文变量或条件分支时，优先放在 mapping 里，不要先做独立的静态消息校验模型。
3. `field.when + mandatory + schema` 是表达"条件成立时字段才必填、才需要校验"的默认写法。
4. 嵌套对象、Map、List 的递归处理优先靠 `mapping` / `itemMapping`，不要指望单个 `<schema/>` 自动完成整棵对象树的业务校验。
5. `gateway` 的 `requestMapping.bodyMapping` / `responseMapping.bodyMapping` 可以同时承担转换和 route-specific 校验。

## 它解决什么问题

`record-mapping` 适合这些场景：

| 场景 | 默认做法 |
|------|---------|
| 外部 payload 字段名和内部 DTO / Bean 不一致 | `field from="..." name="..."` |
| 需要按字段条件决定是否映射 / 是否必填 | `field.when + mandatory` |
| 需要做类型转换、字典约束、简单 schema 校验 | `field.schema` |
| 嵌套对象、列表、Map 递归映射 | `mapping` / `itemMapping` |
| `gateway` 路由对请求 / 响应做定制转换 | `requestMapping.bodyMapping` / `responseMapping.bodyMapping` |
| 同一来源结构因 discriminator 不同走不同字段规则 | `varName` + `when` + 多个 field / itemMapping |

不适合这些场景：

| 场景 | 不要优先用它做什么 |
|------|-------------------|
| 脱离具体 route / use case 的平台级静态消息契约 | 不要把 mapping 当成统一 API 契约文档 |
| 完整对象树的通用 schema 校验器 | 不要把它等同于 `SchemaBasedValidator` |
| 简单 Java 内部对象间一对一复制且无规则差异 | 不要为了形式统一强行引入复杂 mapping |

## 文件结构

最常见的 DSL 结构：

```xml
<definitions x:schema="/nop/schema/record/record-mappings.xdef"
             xmlns:x="/nop/schema/xdsl.xdef">

    <mapping name="CreateOrderRequest_to_OrderCmd">
        <fields>
            <field from="userId" name="operatorId" mandatory="true">
                <schema type="String"/>
            </field>
        </fields>
    </mapping>

</definitions>
```

- 根定义文件：`/nop/schema/record/record-mappings.xdef`
- 单个 mapping 定义：`/nop/schema/record/record-mapping.xdef`

## 执行顺序

理解 `record-mapping` 最重要的是顺序，不要只看 XML 名字猜语义。

普通 `field` 的默认执行顺序可近似理解为：

1. `when` 判断是否处理当前字段
2. `beforeFieldMapping`
3. 取值：`computeExpr` 或 `from` / `alias` / `flattenFrom`
4. `valueExpr`
5. `valueMapper` / `defaultValue`
6. 类型转换：`schema.stdDomain` 或 `type` / `schema.type`
7. `schema` 校验（简单类型 + dict）
8. `mandatory` 检查
9. `ignoreWhenEmpty` 决定是否跳过写入目标
10. 写入目标字段或写入上下文变量 `varName`
11. `afterFieldMapping`

这带来两个高影响结论：

1. `when=false` 时，后续校验和必填都不会发生。
2. `ignoreWhenEmpty` 发生在必填检查之后，**不能**把它当成"空值时跳过 mandatory"的替代。

## 字段级能力

### `when`

`when` 是条件校验的核心入口。

```xml
<field from="idCard" name="idCard" mandatory="true">
    <when><![CDATA[source.userType == 'PERSON']]></when>
    <schema stdDomain="id-card-no"/>
</field>
```

语义是：

1. 只有 `userType == 'PERSON'` 时才处理这个字段。
2. 条件成立时，`idCard` 必填且按 `schema` 校验。
3. 条件不成立时，这个字段整体跳过。

这正是"某个条件满足时字段才应该存在、才需要校验"的默认表达方式。

### `mandatory`

`mandatory="true"` 表示当前字段参与处理时不能为空。

- 对普通字段：空值会直接报错。
- 对 `itemMapping` 字段：如果整个集合 / Map 值为 `null`，也会报错。

如果你的规则是"某条件下才 mandatory"，不要写两套 message model，直接配合 `when`。

### `schema`

`field.schema` 默认承担两类事情：

1. 类型 / 标准域转换
2. 简单字段校验和字典校验

典型写法：

```xml
<field from="amount" name="amount" mandatory="true">
    <schema type="BigDecimal"/>
</field>

<field from="status" name="status">
    <schema dict="order-status"/>
</field>
```

注意：这里的 `schema` 主要是**字段级**能力。对于嵌套对象或集合，默认还是靠 `mapping` / `itemMapping` 递归处理，而不是靠一个外层 `<schema/>` 自动做完整业务树校验。

### `optional`

`optional="true"` 用于来源对象可能没有这个属性的场景。它会把取值从强取改成 try-get，避免因为字段不存在直接失败。

适合：

1. 输入结构存在多个变体。
2. 老协议和新协议并存。
3. 需要兼容来源中不存在的可选字段。

### `ignoreWhenEmpty`

空值时不写入目标对象，适合 patch / merge 风格写入。

不要误用为：

1. 条件校验开关
2. mandatory 的替代

### `varName`

`varName` 不是写目标属性，而是把结果放到 `RecordMappingContext` 里，供后续字段使用。

这很适合 discriminator / 预处理变量：

```xml
<field from="userType" varName="userTypeValue"/>

<field from="companyName" name="companyName" mandatory="true">
    <when><![CDATA[userTypeValue == 'COMPANY']]></when>
</field>
```

默认优先用它表达字段间依赖，不要急着扩展独立验证 DSL。

## 嵌套对象、Map、List

### `mapping`

`mapping` 表示当前字段本身对应另一个对象映射规则。

### `itemMapping`

`itemMapping` 表示当前字段是 `Map` 或 `Collection`，每个条目继续套用另一个 mapping。

```xml
<field from="items" name="items" itemMapping="OrderItemInput_to_OrderItemCmd" mandatory="true"/>
```

这类配置默认比单纯 `schema` 更重要，因为实际的递归字段处理是由它们承担的。

### `itemFilterExpr`

如果集合里的每个元素也有条件，可用 `itemFilterExpr` 过滤条目。

## `patternField`

当来源字段名本身是动态的，使用 `patternField`，其余语义和 `field` 基本一致，包括：

- `when`
- `mandatory`
- `schema`
- `itemMapping`
- `beforeFieldMapping` / `afterFieldMapping`

适合：

1. 扁平键值输入
2. 动态列名
3. 需要根据命名模式生成目标属性名

## Gateway 中怎么用

`gateway` 的 `requestMapping` / `responseMapping` 通过 `bodyMapping` 接入 `record-mapping`。这意味着它默认不只是改字段名，也可以承担 route-specific 校验。

典型判断：

| 问题 | 默认判断 |
|------|---------|
| 这个请求规则是否依赖当前 route | 放 mapping |
| 某字段是否只在某类请求下必填 | 放 `when + mandatory` |
| 需要在转换前后同时做字段级约束 | 仍放 mapping |
| 需要一个脱离 route 的静态 API 契约文档 | 看 `ApiMessageModel`，不要只看 mapping |

对 gateway 请求，默认先问自己：

1. 这是静态消息契约，还是 route-specific 规则？
2. 它是否依赖当前 payload 中的某个 discriminator？
3. 它是否与字段变换强耦合？

如果答案偏向 route-specific、条件化、变换耦合，优先写在 `bodyMapping` 里。

## 与 `ApiMessageModel` / `SchemaBasedValidator` 的边界

容易混淆的地方要分开：

| 能力 | 更适合放哪 |
|------|-----------|
| 对外静态消息结构、代码生成、契约展示 | `ApiMessageModel` |
| 字段转换、条件出现、条件必填、route-specific 校验 | `record-mapping` |
| 独立于 mapping 的通用 schema 校验器 | `SchemaBasedValidator` |

默认不要把三者混成一个概念。

尤其是：

1. `ApiMessageModel` 更像静态契约模型。
2. `record-mapping` 更像执行期转换和条件校验模型。
3. `SchemaBasedValidator` 是通用 schema 校验器，但它不是 `gateway bodyMapping` 场景下唯一或默认的表达方式。

## 什么时候优先补 mapping 文档，而不是再造验证模型

如果你碰到的是这些问题，优先补 mapping：

1. "字段 A 只有在 B=xxx 时才必填"
2. "列表里的元素只有满足某条件才保留"
3. "先解析一个字段，再决定后面字段怎么映射"
4. "不同 route 对同一原始 payload 有不同约束"

这些都属于 `record-mapping` 的天然表达域。

## 常见坑

1. 把 `ignoreWhenEmpty` 误认为会跳过 `mandatory`。
2. 只写 `schema`，却没有给复杂对象 / 集合配置 `mapping` / `itemMapping`。
3. 本质是 route-specific 条件校验，却试图统一塞进静态 `ApiMessageModel`。
4. 需要字段间依赖时没用 `varName`，反而复制多套 mapping。
5. 忘记 `optional="true"`，导致来源缺字段时过早失败。

## 相关文档

- `./api-model-and-codegen.md`
- `./dto-json-and-message-beans.md`
- `./xdef-and-xdsl.md`
- `./debugging-and-diagnostics.md`
- `../04-reference/source-anchors.md`
