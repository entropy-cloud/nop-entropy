# 通过 Delta + BizLoader 扩展返回字段（不改原代码）

## 适用场景

- 你要给既有 API 返回对象增加字段，但不修改原 BizModel 的 Java 代码
- 你希望保持接口兼容：只有请求 selection 时才计算/返回新字段

## AI 决策提示

- ✅ 优先：Delta 方式新增 BizModel Delta 类 + `@BizLoader(autoCreateField = true)`
- ✅ 新字段默认配合 `@LazyLoad`，避免性能与兼容性问题

## 最小闭环

参考类：`io.nop.demo.biz.LoginApiBizModelDelta`

核心片段如下：

```java
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.LazyLoad;
import io.nop.auth.api.messages.LoginResult;
import io.nop.core.context.IServiceContext;

@BizModel("LoginApi")
public class LoginApiBizModelDelta {

    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result, IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

## 行为要点（避免 AI 误用）

- `autoCreateField = true`：当目标 GraphQL 类型没有对应字段时，允许自动创建该字段
- `forType = Xxx.class`：表示把 loader 挂到指定输出类型，不局限于当前 BizModel 的类型
- `@LazyLoad`：只有 selection 明确请求该字段时才计算/返回，避免破坏兼容性与性能

## 相关类

- `io.nop.api.core.annotations.biz.BizLoader`
- `io.nop.api.core.annotations.biz.ContextSource`
- `io.nop.api.core.annotations.core.LazyLoad`
