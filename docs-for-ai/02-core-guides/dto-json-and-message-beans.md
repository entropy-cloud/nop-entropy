# DTO、JSON 与 Message Bean

本页回答三个高频问题：DTO 默认怎么写，什么时候跟随 message bean 风格，以及 JSON / YAML 应该走什么入口。

## 默认选择

| 场景 | 默认做法 |
|------|---------|
| 当前任务里的 request / result DTO | `@DataBean` + `Serializable` + getter / setter |
| 模块里已有成体系的 API message bean | `@DataBean` + `ExtensibleBean` + `@PropMeta` |
| 很小的不可变值对象 | 仅在 surrounding style 已存在时使用构造函数 + `@JsonProperty` |
| JSON / YAML 解析与序列化 | `JsonTool` |

## DTO 默认规则

局部 DTO 的默认规则仍然很简单：

1. 加 `@DataBean`。
2. 实现 `Serializable`。
3. 提供标准 getter / setter。
4. 默认放在 `*-dao/.../dto/`，便于 BizModel 和 Processor 共用。
5. 不要默认用 `Map<String, Object>` 代替强类型 DTO。

如果只是当前任务中的局部请求和返回对象，通常不需要上升到 message bean 模式。

## 什么时候跟随 Message Bean 风格

仓库里已经存在一套稳定 message bean 模式，例如 `LoginRequest`、`LoginResult`：

1. `@DataBean`
2. `extends ExtensibleBean`
3. getter 上使用 `@PropMeta(propId = N)`
4. 在 getter 上按需加 `@JsonInclude`、`@JsonIgnore`

这种风格适合：

1. 模块内已经大量使用同一模式。
2. 这是跨模块、跨端或平台级 API message。
3. 需要可扩展属性和稳定的属性序号。

不适合：

1. 只在一个 BizModel 方法里临时使用的本地 DTO。
2. 为了“更高级”而把简单 DTO 复杂化。

## `@PropMeta` 与 Jackson 注解怎么用

| 注解 | 默认用途 |
|------|---------|
| `@PropMeta` | 为 message bean 属性分配稳定序号 |
| `@JsonInclude` | 控制可选字段何时输出 |
| `@JsonIgnore` | 排除内部或计算属性 |
| `@JsonProperty` | 构造函数参数名或特殊 JSON 字段名 |

默认跟随仓库已有风格：优先把这些注解放在 getter 上，而不是自己发明另一套写法。

## JSON / YAML 默认入口

不要默认散用第三方 JSON 库。当前仓库的统一入口是 `JsonTool`。

| 场景 | 默认方法 |
|------|---------|
| 已知目标类型的 JSON 文本 | `JsonTool.parseBeanFromText(text, Target.class)` |
| 已知目标类型的 YAML 文本 | `JsonTool.parseBeanFromYaml(text, Target.class)` |
| 动态 JSON 对象 | `JsonTool.parseMap(text)` |
| 紧凑输出 JSON | `JsonTool.stringify(obj)` |
| 格式化输出 JSON | `JsonTool.serialize(obj, true)` |
| 输出 YAML | `JsonTool.serializeToYaml(obj)` |
| 从资源路径加载 JSON / YAML | `JsonTool.loadBean(path, Target.class)` |
| 从 Delta 资源层合并后加载 | `JsonTool.loadDeltaBeanFromResource(resource, Target.class)` |

最常见的默认写法：

```java
LoginRequest request = JsonTool.parseBeanFromText(text, LoginRequest.class);
Map<String, Object> raw = JsonTool.parseMap(text);

String payload = JsonTool.stringify(result);
String pretty = JsonTool.serialize(result, true);
```

如果手里只有虚拟路径，先取 `IResource`，再调用 `JsonTool.loadDeltaBeanFromResource(...)`。

## 实用判断

| 问题 | 默认回答 |
|------|---------|
| 这个 Request DTO 要不要加 `@DataBean` | 要 |
| 要不要默认继承 `ExtensibleBean` | 不要，除非周边 API 已经明确使用 message bean 模式 |
| JSON 解析用什么 | `JsonTool` |
| YAML 解析用什么 | `JsonTool.parseBeanFromYaml` 或 `loadBean` |
| 可选字段怎么控制序列化 | `@JsonInclude` |
| 内部字段怎么排除 | `@JsonIgnore` |

## 常见坑

1. 在简单局部 DTO 上无意义地引入 `ExtensibleBean`。
2. 新写一套与周边模块不一致的 `@PropMeta` 编号风格。
3. 已知类型的数据仍然先 `parseMap` 再手动转 Bean。
4. 在普通开发里默认引入另一套 JSON 工具，而不是先看 `JsonTool`。

## 相关文档

- `./service-layer.md`
- `../03-runbooks/create-request-response-dto.md`
- `../04-reference/common-java-helpers.md`
- `../04-reference/source-anchors.md`
