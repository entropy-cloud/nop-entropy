# IoC 与配置注入

本页只保留与当前仓库 AI 开发最相关的 IoC 规则。

## 默认结论

1. 字段注入可以使用 `@Inject`，但字段不能是 `private`。
2. 配置注入使用 `@InjectValue`。
3. 常见配置文件是 `bootstrap.yaml`、`application.yaml`、`application-{profile}.yaml`。
4. 通过 `nop.profile` 激活 profile。
5. 需要明确依赖时，也可以使用 setter 注入。
6. 不要把 Spring 专有注解和 Spring AOP 用法当作当前仓库默认模式。

## 推荐写法

```java
public class MyComponent {
    @Inject
    protected MyDependency dependency;

    @InjectValue("@cfg:app.config-value|default-value")
    protected String configValue;

    @InjectValue("@cfg:app.timeout|30")
    protected int timeout;
}
```

## 配置文件与 profile

| 文件 | 作用 |
|------|------|
| `bootstrap.yaml` | 启动期基础配置，优先进入配置系统 |
| `application.yaml` | 应用主配置 |
| `application-{profile}.yaml` | 按 `nop.profile` 激活的环境覆盖配置 |

对当前仓库里的 AI 开发，记住这几个结论就够了：

1. 环境变量和系统属性优先级高于文件。
2. `bootstrap.yaml` 会先于应用配置参与加载。
3. `application-{profile}.yaml` 会覆盖 `application.yaml`。

### 激活 profile

最常见的是：

```yaml
nop:
  profile: dev
```

也可以通过启动参数设置 `-Dnop.profile=dev`。

## 为什么 `private` 不行

当前仓库源码中，`ClassModelBuilder` 在发现字段时会跳过 `private` 字段，因此 `@Inject private Foo foo;` 不会成为可靠写法。

## 配置相关的默认判断

| 场景 | 默认做法 |
|------|---------|
| 注入 bean | `@Inject` |
| 注入配置值 | `@InjectValue("@cfg:key|default")` |
| 需要动态测试配置 | `@NopTestConfig(testConfigFile = ...)` |

## 不要默认传播的模式

1. Spring `@Value`
2. Spring `@Aspect/@Around` 样式的 AOP 示例
3. `@Inject private Foo foo;`

## 相关文档

- `./testing.md`
- `./debugging-and-diagnostics.md`
- `../04-reference/source-anchors.md`
