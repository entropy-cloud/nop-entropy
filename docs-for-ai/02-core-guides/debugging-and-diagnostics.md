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
| 页面生成结果不对 | `PageProvider__getPage`（见下方） |
| TOPM 图标或模块菜单不对 | 先看 `/{moduleId}/model/module-meta.json`，再看对应 `_*.action-auth.xml` |

## `_dump` 是什么

调试模式下，平台会把最终合并后的模型输出到 `_dump/{appName}/...`。

`_dump/` 是调试期临时输出目录，不是源码目录：

1. 不要手工修改 `_dump/` 下文件。
2. 不要把 `_dump/` 当成常规质量修复目标。
3. 需要修复 `_dump` 中看到的问题时，回到源模型、Delta、保留层文件或模板，再让 debug 输出自动刷新。

这比直接读源文件更适合回答这些问题：

1. 某个 Delta 到底有没有生效。
2. 某个 bean 最终是否真的被启用。
3. 一个模型最终长成了什么样。

## 页面生成调试

通过 REST 接口获取 view.xml 经过模板展开后的最终 AMIS JSON，用于验证页面配置是否正确：

```bash
curl -s "http://localhost:8080/p/PageProvider__getPage?path=/nop/code/pages/dashboard/main.page.yaml"
```

- `path` 参数为 `.page.yaml` 的 VFS 路径，不是 `.view.xml`。
- 只有存在 `.page.yaml` 入口文件的页面才能独立获取。view.xml 中的 `<simple>` 页面如果没有对应的 `.page.yaml`，只能被其他页面内嵌引用，无法独立访问。
- 返回的 JSON 即前端实际渲染用的 AMIS schema，可直接对比 view.xml 中的 grid 列、form 字段、action 配置是否正确生成了。

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

### TOPM 图标没按预期生成

先区分：

1. 你要的是模块级 TOPM icon，还是实体级 SUBM icon。
2. 模块级 icon 看 source `model/*.orm.xml` 根 `<orm ext:icon>`，不要只看 `entity ext:icon`。
3. 先检查 `*-meta` 下的 `/{moduleId}/model/module-meta.json` 是否已经带上 `icon`。
4. 再检查 `*-web` 下生成的 `_*.action-auth.xml` 是否读取到 `moduleMeta.icon`，未配置时会回退到 `ion:grid-outline`。

## 日志与异常

1. Java 代码默认用 SLF4J。
2. 调试时优先读取异常里的 `SourceLocation` 和 XLang 堆栈。
3. 如果只是想确认配置/bean/schema 的最终状态，先看 `_dump` 和 DevDoc，不要一开始就盲目加日志。

## 启动与调试 Quarkus 应用

Nop 平台的 `*-app` 模块基于 Quarkus。以 `nop-code-app` 为例：

### 前提：构建

`nop-code` 已包含在根 `pom.xml` 的 `<modules>` 中，可以直接从根目录构建：

```bash
./mvnw clean install -pl nop-code -am -DskipTests -T 1C
```

其他在根 `pom.xml` `<modules>` 中的模块（如 `nop-auth-app`）可以直接从根目录构建。

### java -jar 启动

```bash
cd nop-code/nop-code-app
java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar
```

指定端口：`-Dquarkus.http.port=9090`

### Maven quarkus:dev（支持热重载）

```bash
cd nop-code/nop-code-app
../../mvnw quarkus:dev -Dquarkus.profile=dev
```

指定端口：加 `-Dquarkus.http.port=9090`

### dev profile 的作用

`-Dquarkus.profile=dev` 激活 `%dev` 配置段，典型配置：

```yaml
"%dev":
  nop:
    core:
      resource:
        check-duplicate-vfs-resource: false  # 开发模式跳过 VFS 重复资源检查
    debug: true
```

### 跳过登录认证

引入了 `nop-auth-web` 的应用默认要求登录才能访问 `/graphql`、`/r/*` 等服务路径。开发调试时可通过配置跳过：

启动参数：`-Dnop.auth.service-public=true`

或在 `application.yaml` 的 `%dev` 段中：

```yaml
"%dev":
  nop:
    auth:
      service-public: true
```

`service-public=true` 时，未登录访问服务路径会自动创建 `sys` 用户上下文，不返回 401。

另一种方案是从 pom.xml 移除 `nop-auth-web` 和 `nop-auth-service` 依赖，完全去掉认证过滤器，但同时失去用户管理和登录页。

### 验证启动成功

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
# 返回 200 即启动成功
```

同样的模式适用于仓库中所有 Quarkus 应用模块（`nop-quarkus-demo`、`nop-auth-app` 等），替换路径即可。

## 相关文档

- `../03-runbooks/debug-codegen-and-generated-files.md`
- `./ioc-and-config.md`
- `./testing.md`
- `../04-reference/source-anchors.md`
