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
| GraphQL schema 或字段不对 | `_dump/{appName}/nop/main/graphql/schema.graphql`（启动时自动 dump）或运行时 `DevDoc__graphql` |
| Delta 合并结果不对 | `_dump/{appName}/...` 下对应模型的最终文件 |
| 新增 `_vfs` 文件不生效 | 刷新 VFS / 清理缓存 |
| 生成链路结果不对 | `../03-runbooks/debug-codegen-and-generated-files.md` |
| 页面生成结果不对 | `PageProvider__getPage`（见下方） |
| TOPM 图标或模块菜单不对 | 先看 `/{moduleId}/model/module-meta.json`，再看对应 `_*.action-auth.xml` |

## `_dump` 是什么

调试模式下（`nop.debug=true`），平台会把以下内容输出到 `_dump/{appName}/...`：

| 资源类型 | dump 路径 | 说明 |
|---------|-----------|------|
| XDSL 模型合并结果 | `_dump/{appName}/{moduleId}/...`（xmeta/view/orm 等） | 所有 XDSL 模型差量合并后的最终结果（XML + LOC 注释） |
| IoC bean 配置 | `_dump/{appName}/nop/.../merged-app.beans.xml` | 所有 bean 配置合并后的最终结果 |
| GraphQL schema | `_dump/{appName}/nop/main/graphql/schema.graphql` | 完整 GraphQL schema（SDL 格式） |
| i18n 合并结果 | `_dump/{appName}/i18n/...` | 各 locale 的 i18n 合并产物 |
| **site-map 菜单树** | `_dump/{appName}/nop/main/site/{locale}-menu.yaml` | action-auth.xml 合并后的**最终菜单树**（如 `zh-CN-menu.yaml`） |

**关于 site-map dump 的特殊性**（调试菜单时最易踩的坑）：

- 菜单合并产物文件名是 **`{locale}-menu.yaml`**（如 `zh-CN-menu.yaml`），**不是** `action-auth.xml`。`DslModelParser` 解析 `static-config-path` 指向的 action-auth.xml 的过程不单独 dump，只 dump 合并后的最终菜单树。
- 想看"运行时实际菜单结构"（测试 TOPM 是否删除、业务分组是否正确、图标是否生效），看 `{locale}-menu.yaml`，而不是 `_dump` 里的 action-auth.xml。
- **触发时机**：`SiteMapProviderImpl` 用懒加载缓存。debug 模式下 `@PostConstruct init()` 会主动预加载一次（把合并问题暴露在启动期并产出 dump）；非 debug 模式需首次调用 `getSiteMap` 才合并。详见 `./auth-and-permissions.md` 的"site-map 加载时机与调试 dump"。

`_dump/` 是调试期临时输出目录，不是源码目录：

1. 不要手工修改 `_dump/` 下文件。
2. 不要把 `_dump/` 当成常规质量修复目标。
3. 需要修复 `_dump` 中看到的问题时，回到源模型、Delta、保留层文件或模板，再让 debug 输出自动刷新。

### 属性来源追踪

`_dump` 输出的核心价值不仅是看"最终长什么样"，更重要的是**通过 XML 注释记录每个节点和属性的实际来源源码位置**。格式如下：

```xml
<!--LOC:[17:6:0:0]/nop/auth/orm/_app.orm.xml-->
<domains>
    ...
</domains>

<!--LOC:[41:10:0:0]/nop/auth/orm/_app.orm.xml
 @name=[370:56:0:0]/nop/orm/xlib/orm-gen.xlib#/nop/auth/orm/app.orm.xml
-->
<entity name="io.nop.auth.dao.entity.NopAuthUser" ...>
```

含义：
- `<!--LOC:[行:列:0:0]/vfs路径-->`：该节点的来源文件和位置
- `@属性名=[行:列:0:0]/vfs路径`：某个属性的来源与节点主体不同时，单独标注该属性来自哪个文件（例如节点来自 `_app.orm.xml`，但 `name` 属性由 `x:gen-extends` 中的 `orm-gen.xlib` 生成）

通过 LOC 注释可以定位：
1. 某个 Delta 到底有没有生效——看 LOC 注释中的路径是否指向 Delta 文件。
2. 某个值是被哪一层覆盖的——看 LOC 路径指向的是基础模型、`x:gen-extends` 产物（`.xlib#`）还是 Delta 文件。
3. 合并顺序是否正确——结合 `./xdef-and-xdsl.md` 中的合并链理解覆盖关系。

也可以在单个模型根节点设置 `x:dump="true"` 打印合并过程的中间结果（不仅是最终结果）。

### `_dump` 中的 GraphQL schema

`nop.debug=true` 且应用启动完成后，`_dump/{appName}/nop/main/graphql/schema.graphql` 会包含完整的 GraphQL schema 定义（SDL 格式），包括：

- 所有 Query / Mutation / Subscription 操作
- 所有 Object / Input / Enum / Interface / Scalar 类型定义
- 所有类型字段、参数、返回值

这个文件在启动时自动产生，与运行时 `DevDoc__graphql` 返回的内容一致，但无需启动服务即可离线查看。

**典型用途：**
1. 确认某个字段/操作是否已注册到 schema 中
2. 对比不同模块版本间的 schema 差异
3. 作为 API 文档离线参考

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
3. `DevDoc__graphql`：运行时查看 GraphQL 文档（返回 SDL 格式文本）。
4. `DevTool.refreshVirtualFileSystem`：刷新虚拟文件系统。
5. `DevTool.clearComponentCache`：清理组件与 GraphQL 缓存。

不要把访问路径写死成某个固定 HTTP URL。优先按 GraphQL 调用名理解这些能力。

### `_dump` vs DevDoc — 关系说明

| 机制 | 时机 | 内容格式 | 适用场景 |
|------|------|---------|---------|
| `_dump/{appName}/...`（XDSL/beans 模型） | 启动时自动产生 | XML + LOC 注释 | 追踪 Delta 合并来源、属性覆盖层 |
| `_dump/{appName}/nop/main/graphql/schema.graphql` | 启动时自动产生 | GraphQL SDL 文本 | 查看完整 schema 类型定义、操作列表 |
| `DevDoc__graphql` | 运行时按需查询 | GraphQL SDL 文本 | 运行时对比当前 schema、热加载后确认变更 |
| `DevDoc__beans` | 运行时按需查询 | XML | 运行时确认 bean 定义 |

- `_dump` 是静态快照，适合离线查看和对比。
- DevDoc 是动态查询，反映当前运行时状态（包括热加载/动态注册的变更）。
- **如果 `_dump` 中看不到预期内容，先确认 `nop.debug=true` 且应用已完成启动。**

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
4. `_dump` 中 LOC 注释的路径指向哪个文件——如果指向的不是 Delta 文件，说明被其他层覆盖了。检查合并顺序（见 `./xdef-and-xdsl.md` 的合并链）。

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

## 测试失败调试铁律

### 第一步永远是看完整堆栈

**不猜，先看证据。** 这是最重要的原则。

当测试失败时：

1. **读 surefire 报告**：`{module}/target/surefire-reports/{TestClass}.txt` 包含完整堆栈
2. **从最内层 `Caused by` 往外读**：最内层是真正出错的地方，外层是传播链
3. **关注 `at` 行中的文件名和行号**：直接跳到源码对应行

### 反面教材（真实案例）

测试返回 `status=-1`，排查过程走了以下弯路：

| 猜测方向 | 耗时 | 结果 |
|---------|------|------|
| 命名转换 `addCart` → `addToCart` | 30分钟 | 无关 |
| classpath 缓存问题 | 20分钟 | 无关 |
| `mvn clean` vs `mvn test` 差异 | 20分钟 | 无关 |
| xbiz/xmeta 配置问题 | 15分钟 | 无关 |
| **最终看堆栈：`findFirst(query, null, null)` 传了 null context** | **5分钟** | **根因** |

堆栈直接显示了 `CrudBizModel.prepareFindPageQuery:381` → `context.getDataAuthChecker()` NPE，追溯到 `findUserCartByProduct` 方法把 null 传给了 `findFirst` 的 context 参数。

### 正确的排查顺序

```
1. 看 surefire 报告完整堆栈（1分钟）
2. 定位最内层异常类型和消息（30秒）
3. 看 at 行的源码位置（30秒）
4. 如果堆栈不够，加 System.out 打印 response（2分钟）
5. 只在堆栈信息不足时才扩大排查范围
```

### 常见测试失败模式

| 现象 | 根因 | 排查方式 |
|------|------|---------|
| `status=-1` | 业务异常或 NPE | 看 surefire 报告的完整堆栈 |
| `ApiResponse.getData() is null` | 业务方法抛异常，GraphQL engine 返回错误响应 | 先看 `result.getMsg()` 和 `result.getCode()` |
| `ClassNotFoundException` | 依赖版本不匹配或 surefire classpath 问题 | `mvn clean` 后重新编译 |
| `BizObject not support action` | BizModel 方法名与 xbiz 生成的 action 名不匹配 | 检查 `_dump/` 中 xbiz 的 action 列表 |
| `context is null` NPE | 私有辅助方法没传 `IServiceContext` 参数 | 检查 `findFirst`/`findList` 等 CrudBizModel 方法的 context 参数 |
| IoC 启动失败 `create-bean-fail` | BizObject 初始化时发现不存在的 tryMethod 或方法 | 检查 `_dump/` 中 xbiz 的 action 配置，确认 BizModel 有对应方法 |

### 快速获取错误信息的技巧

在测试中加打印（仅调试时用，提交前删除）：

```java
ApiResponse<?> result = graphQLEngine.executeRpc(ctx);
if (result.getStatus() != 0) {
    System.out.println("ERROR: status=" + result.getStatus()
        + ", code=" + result.getCode()
        + ", msg=" + result.getMsg());
}
```

surefire 的 stdout 输出在 `{module}/target/surefire-reports/{TestClass}-output.txt`。

### CrudBizModel 方法调用必须传 context

`CrudBizModel` 的 `findFirst`、`findList`、`findPage` 等方法内部会调用 `context.getDataAuthChecker()`。在私有辅助方法中调用这些方法时，**必须把 context 透传下去**，不能传 null：

```java
// 错误
private LitemallCart findByProduct(String userId, String productId) {
    return findFirst(query, null, null); // NPE!
}

// 正确
private LitemallCart findByProduct(String userId, String productId, IServiceContext context) {
    return findFirst(query, null, context);
}
```

## 相关文档

- `../03-runbooks/debug-codegen-and-generated-files.md`
- `./ioc-and-config.md`
- `./testing.md`
- `../04-reference/source-anchors.md`
