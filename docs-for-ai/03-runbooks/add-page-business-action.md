# 给页面增加业务动作按钮

## 适用场景

- 页面上需要新增一个“批准 / 拒绝 / 退款 / 立即执行”之类的业务按钮。
- 按钮需要直接调用 BizModel 方法。

## AI 决策提示

- 先确认业务动作已经有 BizModel 方法。
- 再在 `view.xml` 的 `listActions` 或 `rowActions` 里挂 `@query` / `@mutation`。
- 如果按钮只在特定状态出现，优先用 `visibleOn` 控制，而不是把业务判断写进前端脚本。

## 最小闭环

### 1. 先有 BizModel 方法

```java
@BizMutation
public void refund(@Name("id") String id, IServiceContext context) {
    ...
}
```

### 2. 再在页面里挂动作

```xml
<action id="refund-button" label="退款" level="primary">
    <api url="@mutation:LitemallAftersale__refund">
        <data>
            <id>$id</id>
        </data>
    </api>
    <confirmText>确认退款吗？</confirmText>
</action>
```

## 最值得抄的真实链路

典型链路（以父表 `Xxx` 上的业务动作为例）：

1. 页面动作：在 `Xxx.view.xml` 中定义 `batchApprove`、`batchReject` 等动作。
2. BizModel 实现：在 `XxxBizModel.java` 中对应有 `@BizMutation` 方法。

这个链路展示了：

1. 页面定义动作按钮。
2. 页面通过 `@mutation:Xxx__...` 直接调 BizModel。
3. BizModel 里对应有 `@BizMutation` 方法。
4. 按钮的出现范围由页面结构和所在 tab/filter 共同约束。

## 常见补充配置

1. `<data>`
2. `confirmText`
3. `visibleOn`
4. `batch="true"`
5. `actionType="drawer"` / `dialog`

## 常见坑

1. 页面先加按钮，但 BizModel 方法根本不存在。
2. 明明是业务动作，却去写前端假逻辑而不是挂 Biz API。
3. 忘了 `confirmText`、`visibleOn`，导致危险动作体验差。

## 相关文档

- `./write-bizmodel-method.md`
- `../02-core-guides/api-and-graphql.md`
- `../02-core-guides/page-dsl-pattern-catalog.md`
