# `@`-prefix Value Resolver

XDSL YAML / JSON / XML 模型在**加载阶段**允许字符串值以 `@prefix:` 开头，触发对应的 value resolver 编译并求值，最终被替换为实际值。这是与 `x:extends` 合并机制并行的另一条通用 XDSL 加载链路。

> 与 IoC 容器中 `<property value="@cfg:...">` / `@InjectValue("@cfg:...")` 的 `@cfg:` **写法相同但实现不同**，详见末尾「与 IoC 容器 `@cfg:` 的关系」。

## 通用语法

绑定表达式前缀符号统一为 `@`（`IValueResolverCompiler.BIND_EXPR_SYMBOL = '@'`）。

| 形式 | 含义 |
|------|------|
| `@prefix:value` | 用 `prefix` 对应的 resolver 处理 `value`，整体替换为最终值 |
| `@prefix:k1,k2\|default` | 多数 resolver 遵循「候选列表 + 默认值」约定：依次尝试 `k1`、`k2`，第一个非空者胜出，都为空用 `default` |
| `prefix-text-{@prefix:key} suffix` | 部分 resolver（如 `@i18n:`）支持 `{...}` 内嵌形式，只替换 `{}` 部分而非整体替换 |

**整体替换 vs 内嵌**：以 `@` 开头的整条字符串会被整体替换；`{@prefix:key}` 形式仅替换 `{}` 片段，常用于「前缀/后缀 + 解析值」拼接。

默认值（`|` 后面）按 **JSON 字面量** 解析：`3` → int，`true` → boolean，`"str"` → string，`null` → null。

## 内置 resolver

注册于 `ValueResolverCompilerRegistry.DEFAULT`（`nop-kernel/nop-core/.../json/bind/ValueResolverCompilerRegistry.java`）。

| 前缀 | 实现类 | 行为 |
|------|--------|------|
| `@cfg:` | `resolver.ConfigValueResolver` | 取配置项。`@cfg:a.b.c\|3`、`@cfg:a.b.c,b.c.e\|3` 多候选。读取 `AppConfig.var(key)`，加载期一次性求值 |
| `@i18n:` | `resolver.I18nTextResolver` | 取 i18n 文本。`@i18n:auth.login.label\|登录`，支持 `{@i18n:key}` 内嵌。按当前 locale 查询 |
| `@var:` | `resolver.ScopeVarResolver` | 取求值上下文（`IEvalScope`）中的变量 |
| `@uuid:` | `resolver.UuidResolver` | 生成随机 UUID |
| `@load:` | `resolver.LoadTextResolver` | 从 VFS 路径加载文本内容 |
| `@empty:` | `resolver.EmptyTextResolver` | 返回空字符串占位（用于强制识别为表达式但值为空） |

## 在哪里生效

只要经过 XDSL delta 加载通道，所有字符串字段都可以用上述 resolver。常见入口：

| 入口 | 加载方法 | 是否启用 |
|------|---------|---------|
| `scheduler.yaml`（nop-job） | `JsonTool.loadDeltaBeanFromResource(resource, LocalSchedulerConfig.class)` via `LocalJobConfigLoader` | ✅ 默认启用 |
| 任意 `JsonTool.loadDeltaBean(path, Class)` / `loadDeltaBeanFromResource(...)` | `JsonTool` 静态方法 | ✅ 默认启用 |
| `loadDeltaJson`（`GlobalFunctions.loadDeltaJson`，XPL/元编程中调用） | 同上 | ✅ |
| XML DSL（`.beans.xml`、`.xmeta`、`.view.xml`、`.orm.xml` 等）经 `DslModelParser` | `ResourceComponentManager.loadResourceModel(...)` | ✅ 同一套 `ValueResolverCompilerRegistry.DEFAULT` |

`JsonTool.loadDeltaBeanFromResource` 的默认配置（`nop-kernel/nop-core/.../json/JsonTool.java:191-199`）显式启用：

```java
DeltaJsonOptions options = new DeltaJsonOptions();
options.setIgnoreUnknownValueResolver(false);
options.setRegistry(ValueResolverCompilerRegistry.DEFAULT);
// 同时启用了 x:extends 与 feature 表达式
```

## 示例

`scheduler.yaml`（nop-job）：

```yaml
enabled: "@cfg:nop.sys.scheduler.enabled|true"
jobs:
  - jobName: sys-event-batch-consumer
    trigger:
      cronExpr: "@cfg:nop.sys.event.cron|0/5 * * * * ?"
    invoker:
      bean: nopBatchTaskRunner
      method: executeAsync
```

XMeta / View / 页面 JSON 中的 `@i18n:`（详见 `./error-handling.md` 的 `@i18n:` 章节）：

```yaml
label: "@i18n:auth.login.label|登录"
title: "前缀 {@i18n:auth.field.name} 后缀"
```

## 与 IoC 容器 `@cfg:` 的关系

| 维度 | XDSL delta 加载（本页） | IoC 容器 |
|------|------------------------|----------|
| 入口 | `JsonTool.loadDeltaBeanFromResource` / `DslModelParser` | `ConfigExpressionProcessor.parsePrefixExpr` + `BeanDefinitionBuilder` |
| 注册表 | `ValueResolverCompilerRegistry.DEFAULT`（6 个内置） | IoC 自有 resolver 集合 |
| 内置前缀 | `@cfg:` `@i18n:` `@var:` `@uuid:` `@load:` `@empty:` | `@cfg:` `@r-cfg:`（reactive）`@bean:` `@inject-ref:` `@inject-type:` 等 |
| 求值时机 | **加载期一次性**，结果固化为字面量进入模型 | bean 实例化时求值；`@r-cfg:` 为 reactive，配置变更可触发 bean 重建 |
| 典型用法 | `scheduler.yaml`、batch YAML、YAML/JSON delta | `*.beans.xml` 的 `<property value="@cfg:...">`、Java `@InjectValue("@cfg:...")` |

要点：

- **写法一致、实现独立**：两边都用 `@cfg:`，但分别由不同类处理（`io.nop.core.lang.json.bind.resolver.ConfigValueResolver` vs `io.nop.ioc.impl.resolvers.ConfigValueResolver`）。
- **YAML 中的 `@cfg:` 不会 reactive**：模型加载后值就固定了，运行期修改配置项不会影响已加载的模型。需要重新生效必须重启或重新触发加载。
- IoC 的 reactive 写法是 `@r-cfg:`（`IocConstants.PREFIX_R_CFG`），**只在 IoC 容器有效**，YAML delta 加载不识别。

## 扩展：自定义 resolver

调用 `ValueResolverCompilerRegistry.DEFAULT.addResolverCompiler(prefix, compiler)` 注册新的 `IValueResolverCompiler`。注册后所有走 `loadDeltaBeanFromResource` 的加载都会识别该前缀。

注意：`ValueResolverCompilerRegistry.DEFAULT` 是**全局静态字段**，注册会影响整个 JVM 的 XDSL 加载行为，仅用于平台级扩展。

## 什么时候你应该先想起本页

1. 在 YAML / JSON 模型文件中想引用配置项或 i18n 文本。
2. 看到 `@cfg:`、`@i18n:` 等以 `@` 开头的字符串值，需要判断它的求值时机。
3. 排查「改了 `application.yaml` 但 `scheduler.yaml` 里的 `@cfg:` 没重新生效」——通常是因为加载期一次性求值，不是 reactive。
4. 区分 XDSL delta 加载的 `@cfg:` 与 IoC 容器的 `@cfg:` / `@r-cfg:`。

## 相关文档

- `./ioc-and-config.md` — IoC 容器的 `@InjectValue("@cfg:...")` 与 `<property value="@cfg:...">`
- `./error-handling.md` — `@i18n:` 前缀语法（XMeta / XView / XJson 场景的详细示例）
- `./xdef-and-xdsl.md` — XDSL `x:extends` 合并机制（与本页的 value resolver 是并行的两条加载链路）
- `./dto-json-and-message-beans.md` — `JsonTool.loadDeltaBeanFromResource` 入口
- `../03-modules/nop-job.md` — `scheduler.yaml` 加载与 `LocalJobConfigLoader`
- `../04-reference/source-anchors.md`
