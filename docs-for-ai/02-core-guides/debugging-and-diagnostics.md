# 调试与诊断

本页只保留当前仓库里最适合 AI 使用的排障路径。

## 默认结论

1. 需要看最终合并结果时，先看 `_dump/`，不要只盯源文件。
2. 需要看实际启用的 bean、配置变量或 GraphQL schema 时，先确认 `nop.debug=true`。
3. 新增了 `_vfs` 文件但运行中没生效时，先考虑虚拟文件系统和缓存刷新。
4. `NopException` 的源码位置和 XLang 执行堆栈通常比表面报错更重要。

## 调试开关

最常用的开关是：

```yaml
nop:
  debug: true
```

通常放在 `bootstrap.yaml` 或 `application.yaml` 中。

## 先看什么

| 问题类型 | 先看哪里 |
|---------|---------|
| Bean 没按预期注入 | `_dump/{appName}/nop/main/beans/merged-app.beans.xml`，以及 `DevDoc__beans` |
| 配置值不对 | `DevDoc__configVars`，以及 `bootstrap.yaml` / `application.yaml` / `application-{profile}.yaml` |
| GraphQL schema 或字段不对 | `DevDoc__graphql` |
| Delta 合并结果不对 | `_dump/{appName}/...` 下对应模型的最终文件 |
| 新增 `_vfs` 文件不生效 | 刷新 VFS / 清理缓存 |
| 生成链路结果不对 | `../03-runbooks/debug-codegen-and-generated-files.md` |

## `_dump` 是什么

调试模式下，平台会把最终合并后的模型输出到 `_dump/{appName}/...`。

这比直接读源文件更适合回答这些问题：

1. 某个 Delta 到底有没有生效。
2. 某个 bean 最终是否真的被启用。
3. 一个模型最终长成了什么样。

## DevDoc / DevTool

在 `nop.debug=true` 时，仓库会注册开发期调试能力。

最常用的是：

1. `DevDoc__beans`：查看最终启用的 bean。
2. `DevDoc__configVars`：查看配置变量。
3. `DevDoc__graphql`：查看 GraphQL 文档。
4. `DevTool.refreshVirtualFileSystem`：刷新虚拟文件系统。
5. `DevTool.clearComponentCache`：清理组件与 GraphQL 缓存。

不要把访问路径写死成某个固定 HTTP URL。优先按 GraphQL 调用名理解这些能力。

## 常见定位路径

### Bean 注入失败

先排查：

1. `@Inject` 字段是不是 `private`。
2. 目标 bean 是否真的被装配进最终 `merged-app.beans.xml`。
3. 是否被条件 bean、profile 或测试 beans 覆盖。

### Delta 不生效

先排查：

1. 文件路径是否在 `_vfs/_delta/{deltaDir}/...`。
2. 是否使用了正确的 `x:extends` / `x:override`。
3. `_dump` 中的最终模型是否已经出现你的变更。

### 新文件没被运行时看到

已有文件修改通常能触发依赖失效；但新文件有时需要显式刷新 VFS。

可优先：

1. 使用 DevTool 的刷新能力。
2. 重新启动应用。
3. 再检查 `_dump` 或最终加载结果。

### 页面或标签修改看起来没生效

先区分：

1. 你改的是源模型还是生成物。
2. 页面文字是否又被 i18n key 覆盖。
3. 视图是否来自 `_gen` 输出而不是保留层文件。

## 日志与异常

1. Java 代码默认用 SLF4J。
2. 调试时优先读取异常里的 `SourceLocation` 和 XLang 堆栈。
3. 如果只是想确认配置/bean/schema 的最终状态，先看 `_dump` 和 DevDoc，不要一开始就盲目加日志。

## 相关文档

- `../03-runbooks/debug-codegen-and-generated-files.md`
- `./ioc-and-config.md`
- `./testing.md`
- `../04-reference/source-anchors.md`
