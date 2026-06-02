# Index.html Extensions 机制

index.html 中的 `<!--NOP_EXTENSIONS_INJECT-->` 占位符会在服务端被替换为一段 HTML 片段，用于向前端页面注入扩展脚本或样式。

## 默认行为

- **缺省不启用**。配置项 `nop.web.index-extensions-path` 默认为空，此时不加载任何扩展片段。
- 必须明确配置 `nop.web.index-extensions-path` 才启用扩展注入。
- 启用后，Provider 会检查 index.html 是否包含 `<!--NOP_EXTENSIONS_INJECT-->` 占位符。不包含则原样返回。
- 占位符出现在 `nop-web-site` 模块的 `nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/index.html` 的 `<body>` 末尾。
- Spring (`ZipContentEncodingFilter`) 和 Quarkus (`ZipContentEncodingFilterRegistrar`) 对 `/` 和 `/index.html` 路径都会触发处理。

## 片段文件

### 路径与格式

通过配置项 `nop.web.index-extensions-path` 指定片段文件的 VFS 路径。支持两种后缀：

| 后缀 | 加载方式 |
|------|---------|
| `.xpl` | 通过 `XLang.loadTpl()` 加载 XplModel，利用 `ResourceComponentManager` 编译缓存，执行后生成 HTML 文本 |
| `.html` | 直接读取文件内容作为 HTML 文本 |

### 启用示例

```yaml
nop.web.index-extensions-path: /nop/web/index/extensions.xpl
```

或使用纯 HTML：

```yaml
nop.web.index-extensions-path: /nop/web/index/extensions.html
```

### Delta 定制

片段文件存放在 VFS 中，支持 Delta 覆盖：

- 基础层：`_vfs/nop/web/index/extensions.xpl`
- Delta 层：在 delta 目录下放同名文件即可覆盖

### XPL 模板示例

```xml
<c:unit xpl:outputMode="text">
  <script src="/extensions/my-extension.js"></script>
  <link rel="stylesheet" href="/extensions/my-extension.css" />
</c:unit>
```

也可以包含动态逻辑：

```xml
<c:unit xpl:outputMode="text">
  <c:if test="${config.getBoolean('nop.web.enable-analytics')}">
    <script src="/analytics.js"></script>
  </c:if>
</c:unit>
```

## 架构

### 核心类

| 类 | 模块 | 职责 |
|----|------|------|
| `IndexHtmlProvider` | `nop-web` | 加载 index.html、检查占位符、根据配置加载扩展片段（xpl/html）、替换并返回 |
| `ZipContentEncodingFilter` | `nop-spring-web-starter` | `/` 和 `/index.html` 拦截，调用 `IndexHtmlProvider` |
| `ZipContentEncodingFilterRegistrar` | `nop-quarkus-web` | 同上，Quarkus 版本 |

### 调用流程

```
GET / 或 /index.html
  → Filter 拦截
    → IndexHtmlProvider.getIndexHtml()
      → 从 classpath 加载 index.html
      → 检查是否包含 <!--NOP_EXTENSIONS_INJECT--> 占位符
        → 不包含：原样返回
        → 包含且配置了 extensions-path：
          → .xpl 后缀：通过 ResourceComponentManager 加载 XplModel 并执行，生成 HTML
          → .html 后缀：直接读取文件内容
          → 替换占位符，返回最终 HTML
    → Filter 写入 response
```

### 配置项

| 配置键 | 类型 | 缺省值 | 说明 |
|--------|------|--------|------|
| `nop.web.index-extensions-path` | String | 空（不启用） | 扩展片段文件的 VFS 路径，支持 `.xpl` 和 `.html` 后缀 |

配置项定义在 `WebConfigs.CFG_WEB_INDEX_EXTENSIONS_PATH` 中。

### IoC 注册

`IndexHtmlProvider` 作为 Nop IoC bean 注册在 `nop-web` 模块的 `web-defaults.beans.xml` 中。

## 执行时序

替换后的 HTML 片段位于 `<body>` 末尾、`</body>` 之前。插入的 `<script>` 标签的执行时机取决于其类型：

| 插入的脚本类型 | 相对于 main.js (`type="module"`) 的执行时机 |
|---|---|
| `<script>` (普通) | 先于 main.js 执行 |
| `<script type="module">` | 后于 main.js 执行 |

如果需要扩展脚本在 main.js 之前执行，使用普通 `<script>` 标签。

## 使用场景

- 注入第三方分析脚本（如 Google Analytics）
- 注入全局 CSS 样式覆盖
- 注入系统监控或诊断脚本
- 通过 Delta 机制在不同部署环境注入不同脚本
