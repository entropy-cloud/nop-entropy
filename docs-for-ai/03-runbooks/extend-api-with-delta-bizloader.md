# 通过 Delta + BizLoader 扩展返回字段

## 适用场景

- 需要给既有 API 返回对象增加字段，但不想修改原 BizModel Java 代码。
- 希望保持接口兼容，只有前端 selection 请求该字段时才计算。

## AI 决策提示

- 优先用 Delta 类 + `@BizLoader(autoCreateField = true)`。
- 新字段默认配合 `@LazyLoad`，避免性能和兼容性问题。
- 这是“扩展已有 API”的默认路径，不是先改生成 DTO。

## 最小闭环

### 1. 创建对应 BizModel 的 Delta 类

参考仓库中的真实实现：

- `nop-demo/nop-delta-demo/src/main/java/io/nop/demo/biz/LoginApiBizModelDelta.java`

### 2. 增加自动创建字段的 loader

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {

    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result, IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

### 3. 构建并验证

1. 构建对应模块。
2. 在 GraphQL selection 中显式请求新字段。
3. 确认未请求时不会强制计算该字段。

## 关键点

| 配置 | 作用 |
|------|------|
| `autoCreateField = true` | 目标 GraphQL 类型没有该字段时自动补字段 |
| `forType = Xxx.class` | 把 loader 挂到指定输出类型，而不是当前 BizModel 自身类型 |
| `@LazyLoad` | 只有 selection 请求该字段时才计算 |

## 常见坑

1. 只想补已有字段的 loader，却误用 `autoCreateField = true`。
2. 没有 `@LazyLoad`，导致昂贵字段每次都计算。
3. 直接改基础产品源码，而不是走 Delta。

## 相关文档

- `./add-bizloader-field.md`
- `./prefer-delta-over-direct-modification.md`
- `../02-core-guides/api-and-graphql.md`
- `../04-reference/source-anchors.md`
