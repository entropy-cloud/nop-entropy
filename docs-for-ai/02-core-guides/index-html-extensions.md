# Index.html Extensions 机制

> 服务端多扩展加载机制：扫描 `extension/{name}/extension.json`，把每个启用的扩展注入 `<link rel="stylesheet">` 与 `<script type="module">` 到 `<!--NOP_EXTENSIONS_INJECT-->` 占位符。

## 概述

- **目的**：在宿主 `index.html` 中按白名单追加扩展脚本与样式。
- **典型场景**：宿主为 nop-chaos-next / 自研 React shell 等"前端运行时不需要知道扩展存在"的应用；扩展作为独立产物（独立 npm 仓、独立 Vite 构建）部署到 `META-INF/resources/extension/{name}/`，由 Java 后端扫描并把每个启用的扩展的 CSS / JS 直接渲染到 HTML 中。
- **核心模型**：服务端白名单 + 服务端 `<link>` / `<script>` 注入。HTML 替换完成后浏览器会**立即**发起扩展资源请求，扩展模块通过原生 `<script type="module">` 执行。前端运行时不需要主动 `import()`。

## 默认行为

- **缺省不启用**。`nop.web.index-extensions-dir` 默认值为空，未配置时不加载任何扩展片段。
- 启用后，`IndexHtmlProvider` 会读取 `nop.web.index-extension-names` 配置项（逗号分隔），按白名单顺序加载对应子目录下的 `extension.json`，把每个启用的扩展依次渲染到占位符位置。
- 占位符 `<!--NOP_EXTENSIONS_INJECT-->` 默认位于 `nop-web-site` 的 `nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/index.html` 的 `<body>` 末尾，紧邻 `<div id="root">`。
- Spring (`ZipContentEncodingFilter`) 与 Quarkus (`ZipContentEncodingFilterRegistrar`) 对 `/` 与 `/index.html` 路径都会触发处理。

## 扩展清单 (`extension.json`)

每个扩展在 VFS 中以子目录形式存在，目录根路径由 `nop.web.index-extensions-dir` 指定（典型配置为 `extension`）。子目录中必须包含 `extension.json`：

### 字段定义

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 扩展唯一标识，对应子目录名；同时出现在 HTTP 路径段中 |
| `name` | string | 是 | 扩展显示名称（仅元数据，Java 端不消费） |
| `version` | string | 否 | 语义版本（仅元数据） |
| `description` | string | 否 | 描述（仅元数据） |
| `entry` | string | 是 | 入口 JS 文件路径，**相对于扩展子目录**。由 Java 端拼成完整 HTTP 路径后注入 `<script type="module">` |
| `styleAssets` | string[] | 否 | 样式文件路径列表，**相对于扩展子目录**。由 Java 端依次渲染为 `<link rel="stylesheet">` |

Java 端字段定义见 `io.nop.web.page.ExtensionMeta`。

### 示例清单

```json
{
  "id": "example-extension-demo",
  "name": "Harbor Operations Suite",
  "version": "0.0.1",
  "description": "Demo extension showcasing extension loading",
  "entry": "./assets/index-COw24fxy.js",
  "styleAssets": [
    "./assets/index-3Z9nCm1K.css"
  ]
}
```

## 部署目录结构

Java 后端部署产物（`META-INF/resources/`）：

```
META-INF/resources/
  index.html                                       ← 宿主 HTML（含 <!--NOP_EXTENSIONS_INJECT--> 占位符）
  assets/                                          ← 宿主资源
  extensions/                                      ← 扩展根（与 URL base path `/extensions` 对齐，复数）
    example-extension-demo/
      extension.json                               ← 扩展清单（ExtensionManifest）
      assets/
        index-{hash}.js                            ← 扩展入口
        harbor-{hash}.css                          ← 扩展样式
        harbor-mark-{hash}.svg                     ← 扩展 SVG 资源
```

与 nop-chaos-next 的 `examples/extension-demo` 配套使用：

- 扩展项目（如 `examples/extension-demo/`）用 `pnpm build` 产出 dist/extension.json 与 dist/assets/{hash}.{js,css,svg}（extension.json 内所有路径相对 extension.json 自身，如 ./assets/index.js）
- 把 `dist/*` 直接复制到 `META-INF/resources/extensions/{id}/`
- Java 后端 `IndexHtmlProvider` 扫描 `extensions/{id}/extension.json`，拼接 URL `/extensions/{id}/<stripDotSlash(entry)>` 注入 host `index.html`
- Spring/Quarkus 默认把 `classpath:/META-INF/resources/**` 暴露为 `/`，所以 HTTP `/extensions/{id}/assets/...` 自动路由到 `META-INF/resources/extensions/{id}/assets/...`，无需额外 `addResourceHandlers` 配置

`extension.json` 内的路径字段语义：

| 字段 | 相对基点 | 示例 |
|------|----------|------|
| `entry` | `extension.json` 自身 | `./assets/index.js` |
| `styleAssets[i]` | `extension.json` 自身 | `./assets/harbor-{hash}.css` |
| `assets[i]` | `extension.json` 自身 | `./assets/harbor-mark-{hash}.svg` |

Java 端 `IndexHtmlProvider.normalizePath()` 拼接 URL 时去除 `./` 前缀，使 `entry`/`styleAssets`/`assets` 字段无需做特殊处理即可拼成正确的 HTTP 路径。

## 注入结果示例

配置：

```yaml
nop.web.index-extensions-dir: /extensions
nop.web.index-extension-names: example-extension-demo
nop.web.index-extensions-base-path: /extensions
```

占位符会被替换为：

```html
<link rel="stylesheet" data-nop-extension data-nop-extension-id="example-extension-demo" href="/extensions/example-extension-demo/assets/harbor-xxx.css" />
<script type="module" data-nop-extension data-nop-extension-id="example-extension-demo" src="/extensions/example-extension-demo/assets/index.js"></script>
```

浏览器解析这些标签时会立即发起 HTTP 请求。静态资源由 Spring/Quarkus 默认的 `classpath:/META-INF/resources/**` 映射直接服务（`/extensions/{id}/assets/...` → `META-INF/resources/extensions/{id}/assets/...`），无需额外 `addResourceHandlers` 配置。

## 配置项

| 配置键 | 类型 | 缺省值 | 说明 |
|--------|------|--------|------|
| `nop.web.index-extensions-dir` | String | `/extensions` | 扩展目录的根路径，每个子目录对应一个扩展，子目录内必须含 `extension.json`。检索优先走 VFS（可 Delta 定制），VFS 中不存在时 fallback 到 classpath 静态资源 `META-INF/resources/extensions/{id}/` |
| `nop.web.index-extension-names` | String | 空（不启用） | 启用的扩展名称列表，逗号分隔。只有列表中的扩展会被加载，未列出的扩展会被跳过（即便目录存在） |
| `nop.web.index-extensions-base-path` | String | `/extensions` | 扩展资源的 HTTP 访问基础路径。最终 `<link>` / `<script>` 的 `href` / `src` 为 `{basePath}/{id}/{styleAsset or entry}` |
| `nop.web.index-title` | String | 空 | `index.html` 的 `<title>` 内容，支持 `${var}` 模板变量 |

配置项定义在 `WebConfigs` 中。

## 架构

### 核心类

| 类 | 模块 | 职责 |
|----|------|------|
| `IndexHtmlProvider` | `nop-web` | 加载 `index.html`、检查占位符、扫描扩展目录、解析 `extension.json`、按白名单顺序渲染 `<link>` 与 `<script>`、替换占位符并返回 |
| `ExtensionMeta` | `nop-web` | 与 `extension.json` 同构的元数据模型 |
| `WebConfigs` | `nop-web` | 配置项定义 |
| `ZipContentEncodingFilter` | `nop-spring-web-starter` | `/` 与 `/index.html` 拦截，调用 `IndexHtmlProvider` |
| `ZipContentEncodingFilterRegistrar` | `nop-quarkus-web` | 同上，Quarkus 版本 |

### 调用流程

```
GET / 或 /index.html
  → Filter 拦截
    → IndexHtmlProvider.getIndexHtml()
      → 从 classpath 加载 index.html
      → 替换 <title>（若配置 nop.web.index-title）
      → 扫描 nop.web.index-extensions-dir + "/" + 名称 + "/extension.json"
      → 按 nop.web.index-extension-names 白名单过滤
      → 依次拼接：
           <link rel="stylesheet" href="{basePath}/{id}/{styleAsset}">
           <script type="module" src="{basePath}/{id}/{entry}">
      → 替换 <!--NOP_EXTENSIONS_INJECT--> 占位符，返回最终 HTML
    → Filter 写入 response
```

### 缓存

- `IndexHtmlProvider` 内部用 `ResourceCacheEntry` 缓存扩展元数据列表
- 缓存 key 为扩展目录路径，缓存失效调用 `invalidateCache()`
- 缓存条目通过 `CacheEntryManagement` 注册到 `GlobalCacheRegistry`，由扩展 jar 包更新 / 卸载事件触发失效

### IoC 注册

`IndexHtmlProvider` 作为 Nop IoC bean 注册在 `nop-web` 模块的 `web-defaults.beans.xml` 中。

## 与 nop-chaos-next 的契约对应

| nop-chaos-next 部署形态 | Java 端契约 | 备注 |
|------------------------|-----------|------|
| 生产部署（Java 后端打包扩展产物） | 扫描 `extension/{name}/extension.json`，把 `<link>` + `<script>` 注入 HTML | 浏览器原生 `<script type="module">` 执行扩展 |
| Prototype 模式（Vite dev server，无 Java 后端） | `vite-plugin-prototype-server` 通过 `transformIndexHtml` 注入 `window.__NOP_EXTENSIONS__` | 与生产契约不同，仅用于开发联调 |

**两者并存**：Java 端的 `IndexHtmlProvider` 不依赖前端 `window.__NOP_EXTENSIONS__` 数组，前端 `bootstrapExtensions()` 也不依赖 Java 端是否注入了 `<script>`。生产部署走 Java 端契约，prototype / 独立 dev server 走 `window.__NOP_EXTENSIONS__` 契约，互不干扰。

## 使用场景

- 把多个独立交付的扩展产物部署到同一 host；服务端白名单控制每个环境启用哪些扩展
- 扩展 CSS 在 `<head>` 加载，先于应用 JS 执行，避免 FOUC
- 扩展 JS 通过 `<script type="module">` 异步加载，前端 React 启动期不阻塞
- 通过 Delta 机制在不同部署环境配置不同的白名单

## 不适用场景

- 需要前端运行时合并 `ShellExtension` 配置项（languages、themes、builtinPages、auth 等）：Java 端契约只负责静态资源加载，不会把扩展的 `ShellExtension` 配置合并进宿主运行时。如需该能力，需另接 nop-chaos-next 的 `bootstrapExtensions()` 链路（在 `window.__NOP_EXTENSIONS__` 契约下生效）
- 需要 `load: () => import('@alias')` 本地源码联调：Java 端契约只支持 HTTP 静态资源，不支持构建期 alias。如需该能力，使用 prototype 模式或独立 dev server

## 测试覆盖

- `IndexHtmlProviderTest` 覆盖单扩展加载、多扩展加载、HTML 生成、不存在的扩展处理、未配置扩展名场景、缓存失效
- 测试 fixture 位于 `nop-web/src/test/resources/_vfs/nop/test/extensions/{test-extension,another-extension}/extension.json`