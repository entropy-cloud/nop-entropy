# VFS 虚拟文件系统与资源解析

Nop 平台的核心抽象层之一。理解 VFS（Virtual File System）是理解一切资源加载、Delta 定制和多模块协作的前提。

## 默认结论

1. VFS 是跨模块的**单一平坦命名空间**——所有 Maven 模块 `src/main/resources/_vfs/` 下的文件都合并到一个统一的虚拟路径空间，没有模块间隔离。
2. 路径解析顺序：**Tenant → Delta Layers（高优先级→低优先级）→ Base**。Base 内部按 **外部覆盖目录 → VFS 索引 → Classpath 扫描 → libPaths → 当前项目源码** 优先级合并。
3. 不同模块的 `_vfs` 下**不允许出现相同路径**（`_module` 文件除外）。触发后默认抛 `NopException`，可用 `check-duplicate-vfs-resource: false` 降级为 INFO 日志（仅开发模式）。
4. VFS 内容通过 **Classpath 扫描** 发现 —— 运行时扫描所有 JAR 和 classpath 目录下 `_vfs/` 子目录中的文件，不是靠手动注册。

## 物理位置

每个 Maven 模块在 `src/main/resources/_vfs/` 下按模块 ID 子目录组织资源：

```
src/main/resources/_vfs/
  _delta/                              # Delta 覆盖层目录
    {deltaDir}/                        # 例如 default, app, product
      {moduleId}/                      # 例如 nop/auth
        orm/app.orm.xml
        pages/.../
        beans/...beans.xml
  i18n/                                # 国际化
    en/{module}.i18n.yaml
    zh-CN/{module}.i18n.yaml
  dict/                                # 字典定义
    {category}/{name}.dict.yaml
  {moduleId}/                          # 模块根，例如 nop/core
    _module                            # 模块标记文件（空文件，允许重复）
    beans/                             # IoC bean 定义
    orm/                               # ORM 模型
    model/{EntityName}/{EntityName}.xmeta
    pages/{EntityName}/...             # 页面定义
    xlib/                              # XLang 标签库
    auth/                              # 权限配置
    xdef/                              # XDef schema（主要在 nop-xdefs）
    dialect/                           # 数据库方言
```

测试资源放在 `src/test/resources/_vfs/` 下，路径规则相同。

## VFS 路径解析顺序

当调用 `VirtualFileSystem.instance().getResource("/nop/core/a.xml")` 时，完整的解析链如下：

```
        请求路径: /nop/core/a.xml
               │
               ▼
  ┌─────────────────────────────┐
  │ 1. Tenant 层 (可选)         │
  │    /_tenant/{tenantId}/...  │
  │    找到 → 返回              │
  └──────────┬──────────────────┘
             │ 未找到
             ▼
  ┌─────────────────────────────┐
  │ 2. Delta 层（按序）         │
  │    /_delta/{layerId[0]}/... │  ← 最高优先级
  │    /_delta/{layerId[1]}/... │
  │    /_delta/{layerId[n]}/... │  ← 最低优先级
  │    找到 → 返回              │
  └──────────┬──────────────────┘
             │ 全部未找到
             ▼
  ┌─────────────────────────────┐
  │ 3. Base 层                  │
  │    /nop/core/a.xml          │
  │    在 Base 内部按以下优先级 │
  │    合并（见下方说明）       │
  └─────────────────────────────┘
```

### Base 层的内部组成

Base 层自身也是一个多层合并结构，优先级从高到低：

| 优先级 | 来源 | 配置项 | 说明 |
|--------|------|--------|------|
| 最高 | **外部覆盖 VFS 目录** | `nop.core.resource.dir-override-vfs`（默认 `./_vfs`） | 运行目录下的 `_vfs/` 目录，可用于覆盖 JAR 内资源 |
| 次高 | **VFS 索引文件** | `classpath:nop-vfs-index.txt`（`nop.core.resource.use-nop-vfs-index`） | 可选预编译索引，GraalVM native-image 必需 |
| 中 | **Classpath 扫描** | `ClassPathScanner.scanPath("_vfs/")` | 扫描所有 JAR 和 classpath 目录下的 `_vfs/` |
| 低 | **libPaths** | `nop.core.vfs.lib-paths` | 额外指定的 JAR/ZIP/目录 |
| 最低 | **当前项目源码** | `src/main/resources/_vfs`（`nop.core.resource.include-current-project-resources`） | 开发时直接从源码目录加载 |

**关键规则**：同层内优先发现的资源生效（first wins），不同层之间高优先级覆盖低优先级。

## 模块无关性

VFS 的路径空间是**全局平坦**的，不依赖调用方所在的模块：

```
模块 A: src/main/resources/_vfs/nop/foo/bar.xml
模块 B: src/main/resources/_vfs/nop/foo/bar.xml
模块 C: src/main/resources/_vfs/nop/foo/bar.xml
        ↓
    合并到同一个虚拟路径 /nop/foo/bar.xml
    冲突 → 重复检测抛异常（除非配置跳过）
```

这意味着：
- 任何模块代码调用 `VirtualFileSystem.instance().getResource("/nop/foo/bar.xml")` 都得到**相同结果**
- `_module` 文件（各模块根目录下的零字节标记文件）是唯一的例外——它是 VFS 唯一允许重复的路径，用于 `ModuleManager` 收集所有已注册的模块
- Bean 发现（`/<moduleId>/beans/app-*.beans.xml`）也是通过 VFS 遍历完成，不是 Java 类路径扫描

## 模块发现与注册

### moduleId 与 moduleName

Nop 模块在 VFS 中用两段式路径标识：

| 概念 | 格式 | 示例 | 说明 |
|------|------|------|------|
| `moduleId` | `{part1}/{part2}` | `nop/auth`, `nop/core` | VFS 路径中的目录名，使用 `/` 分隔 |
| `moduleName` | `{part1}-{part2}` | `nop-auth`, `nop-core` | 配置项、Java 代码中引用时使用，`-` 分隔 |

转换规则（`ResourceHelper.java:162-179`）：

```
moduleId = moduleName.replace('-', '/')
moduleName = moduleId.replace('/', '-')
```

从 VFS 路径提取：取路径前两段，如 `/nop/auth/beans/app.beans.xml` 的 moduleId 为 `nop/auth`，moduleName 为 `nop-auth`。

### 发现生命周期

模块发现由 `ModuleManager`（全局单例）管理，在 VFS 初始化完成后自动触发：

```
VirtualFileSystemInitializer.init()
  └── ModuleManager.instance().discover()
        └── VFS.findAll("*/*/_module")          ← 扫描所有两段式路径下的 _module
              │
              ├── 匹配 disabled-module-names → 跳过
              ├── enabled-module-names 非空且不匹配 → 跳过
              │
              └── /{moduleId}/app.module.yaml 存在？
                    ├── 是 → 解析为 ModuleModel（YAML → Java bean）
                    └── 否 → 创建默认 ModuleModel（version=1.0）
                          └── 按 moduleName 排序注册到模块 Map
```

> `_module` 文件是**零字节标记文件**（empty marker），它的存在仅表示"此目录是一个模块根"。

关键源码：
- `ModuleManager.java:70-94` — `discover()` 方法
- `ModuleManager.java:116-127` — `loadModuleById()` 加载 `app.module.yaml`
- `ModuleManager.java:39-57` — 全局单例

### 启禁用模块

通过配置项控制哪些模块生效：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `nop.core.module.enabled-module-names` | Set | null | 非空时**仅**启用列表内的模块 |
| `nop.core.module.disabled-module-names` | Set | null | 排除列表内的模块（优先于 enabled） |

值使用逗号分隔的 moduleName，如 `nop-auth,nop-wf`。定义在 `CoreConfigs.java:184-188`。

### app.module.yaml

每个模块可以在 `/{moduleId}/app.module.yaml` 提供元数据。文件**可选**，不存在的模块自动使用 `version: "1.0"` 的默认值。内容使用 YAML 格式，解析为 `ModuleModel` 的 Java bean。

```yaml
# /nop/core/app.module.yaml 示例
version: 2.0
```

支持的全部属性（`ModuleModel.java:16-118`）：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `moduleId` | String | 派生 | VFS 路径确定，如 `nop/auth` |
| `moduleName` | String | 派生 | `moduleId` 中 `/` 替换为 `-` |
| `version` | String | `"1.0"` | 模块版本 |
| `displayName` | String | null | 显示名称 |
| `description` | String | null | 描述 |
| `dependsOn` | Set\<String\> | null | 依赖的 moduleName 集合 |
| `author` | String | null | 作者 |
| `publishDate` | String | null | 发布日期 |
| `dynamic` | boolean | false | 运行时动态注册的模块 |
| `sid` | String | null | 动态模块的会话 ID |

### module: 名字空间

VFS 支持 `module:` 前缀，按模块隔离查找资源。路径 `module:/path/to/file` 等价于"遍历已启用模块，返回第一个存在的 `/{moduleId}/path/to/file`"。实现类 `ModuleNamespaceHandler.java`，内部调用 `ModuleManager.instance().getModuleResource(true, path)`。

### 模块消费者一览

以下核心功能遍历已启用的模块列表，按约定路径发现资源（`ModuleManager` 的 `getEnabledModules()`、`findModuleResources()`、`getAllModuleResources()` 三种模式）：

| 功能 | 类 | VFS 扫描路径 |
|------|---|-------------|
| **IoC bean 定义** | `AppBeanContainerLoader` | `/{moduleId}/beans/app.beans.xml` 和 `app-*.beans.xml` |
| **ORM 模型** | `OrmModelLoader` | `/{moduleId}/orm/app.orm.xml` |
| **ORM 拦截器** | `XplOrmInterceptorLoader` | `/{moduleId}/orm/app.orm-interceptor.xml` |
| **Biz 模型** | `GraphQLBizModels` | `/{moduleId}/model/*.xbiz` |
| **XMeta 元模型** | `GraphQLBizModels` | `/{moduleId}/model/*.xmeta` |
| **SQL 库** | `SqlLibManager` | `/{moduleId}/sql/*.sql-lib.xml` |
| **配置变量** | `ConfigModelLoader` | `/{moduleId}/conf/config.vars.yaml` |
| **错误码映射** | `ErrorCodeMappingsLoader` | `/{moduleId}/conf/app.errors.yaml` |
| **页面定义** | `PageProvider` | `/{moduleId}/pages/*/*.page.yaml` |
| **动态 XJS/XCSS** | `DynamicWebFileProvider` | `/{moduleId}/pages/*.xjs`, `/{moduleId}/js/*.xjs` 等 |
| **TCC 元数据** | `DefaultTccServiceMetaLoader` | `/{moduleId}/tcc/meta/*.tcc-meta.json5` |
| **Spring bean** | `NopBeansAutoConfiguration` | `/{moduleId}/beans/spring-*.beans.xml` |
| **MyBatis mapper** | `NopMybatisSessionFactoryCustomizer` | `/{moduleId}/mapper/*.mapper.xml` |
| **版本化模型** | `ResourceVersionedModelStore` | `/{moduleId}/{basePath}/{name}/*.{ext}` |

**不遍历模块的功能**：
- **i18n 国际化**：从 `/i18n/{locale}/` 扁平目录加载，非按模块扫描
- **Auth 权限/菜单**：从 `/nop/main/auth/app.action-auth.xml` 单一路径加载，非按模块扫描

## Classpath 扫描机制

VFS 内容的发现通过 `ClassPathScanner` 实现，流程如下：

1. **调用 `ClassLoader.getResources("_vfs/")`** —— 找到 Classpath 上所有包含 `_vfs/` 的 JAR 和目录
2. **JAR 扫描**：遍历 JarEntry，匹配前缀 `_vfs/`
3. **目录扫描**：递归遍历文件系统下的 `_vfs/` 目录
4. **注册到 `InMemoryResourceStore`**：按 first-wins 规则添加到内存资源树
5. **Normalize**：优先将 Maven target 编译输出中的资源映射回源码目录（方便开发时热加载，由 `toSrcResource()` 自动转换）

```java
// DeltaResourceStoreBuilder.buildInMemoryStore()
new ClassPathScanner().scanPath("_vfs/", (path, url) -> {
    path = path.substring(1); // 去掉前导 "/"
    IResource resource = ResourceHelper.buildResourceFromURL(path, url);
    resource = normalizeResource(resource); // 优先 src/ 而非 target/
    boolean b = store.addResourceIfAbsent(resource);
    if (!b && !isAllowDuplicate(path) && CFG_CHECK_DUPLICATE_VFS_RESOURCE.get()) {
        throw new NopException(ERR_RESOURCE_DUPLICATE_VFS_RESOURCE);
    }
});
```

## 重复检测

| 条件 | 行为 |
|------|------|
| 标准扫描（`_vfs/`） | 默认抛 `NopException`（`ERR_RESOURCE_DUPLICATE_VFS_RESOURCE`） |
| 扩展扫描（`nop.core.resource.scan-ext-vfs-path`） | 仅 WARN 日志，不抛异常 |
| `_module` 文件 | 始终允许重复 |
| 配置 `check-duplicate-vfs-resource: false` | 跳过重复检查（仅 INFO 日志），开发模式使用 |

## Namespace 前缀

VFS 支持多种 namespace 前缀，用于访问不同层或不同来源的资源：

| 前缀 | 示例 | 作用 |
|------|------|------|
| （无前缀） | `/nop/core/a.xml` | 标准路径，走完整 tenant→delta→base 解析 |
| `super:` | `super:/nop/core/a.xml` | 跳过当前所在的 delta 层及其上层，向**下层**查找 |
| `raw:` | `raw:/nop/core/a.xml` | 绕过 delta/tenant 解析，直接从底层 store 获取 |
| `v:` | `v:/nop/core/a.xml` | 与无前缀等价（纯别名） |
| `module:` | `module:/nop/core/a.xml` | 按模块隔离空间查找 |
| `file:` | `file:/path/to/file` | 直接读取本地文件系统 |
| `classpath:` | `classpath:path/to/file` | 直接从 Classpath 读取（绕过 VFS） |
| `dump:` | `dump:/path/to/file` | 读取调试 `_dump/` 目录 |
| `temp:` | `temp:/file.tmp` | 读取临时目录 |

## 关键配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.core.vfs.delta-layer-ids` | 自动检测 | Delta 层 ID 列表（逗号分隔），顺序=优先级 |
| `nop.core.vfs.lib-paths` | 空 | 额外的 VFS 来源目录/JAR/ZIP |
| `nop.core.resource.dir-override-vfs` | `./_vfs` | 运行目录下覆盖 JAR 资源的 VFS 目录 |
| `nop.core.resource.check-duplicate-vfs-resource` | `true` | 重复 VFS 路径是否抛异常 |
| `nop.core.resource.include-current-project-resources` | `true` | 是否加载当前项目 `src/main/resources/_vfs` |
| `nop.core.resource.use-nop-vfs-index` | `true` | 是否使用 `nop-vfs-index.txt` 预编译索引 |
| `nop.core.resource.scan-ext-vfs-path` | 空 | 额外的 VFS 扫描路径 |
| `nop.core.vfs.delta-resource-store-builder-class` | `DeltaResourceStoreBuilder` | 自定义 store builder |

## 常见操作

### 读取 VFS 资源

```java
// 推荐方式
IResource resource = VirtualFileSystem.instance().getResource("/nop/core/a.xml");
String text = resource.readText();
InputStream is = resource.getInputStream();
```

### 遍历 VFS 目录

```java
List<? extends IResource> children = VirtualFileSystem.instance().getChildren("/nop/core");
// 递归遍历
resource.depthIterator().forEachRemaining(child -> { ... });
```

### 刷新 VFS

```java
// 开发工具
DevTool.refreshVirtualFileSystem();

// 代码中
((IRefreshable) VirtualFileSystem.instance()).refresh(true);
```

### 检查资源是否存在

```java
IResource resource = VirtualFileSystem.instance().getResource("/nop/core/a.xml", true);
if (resource != null && resource.exists()) { ... }
```

## 常见坑

1. 新增 `_vfs` 文件后刷新 VFS 才能生效（已有文件修改通常能触发依赖失效，但新文件需要显式刷新）。
2. 两个模块放同路径文件到 `src/main/resources/_vfs/` 下会触发重复检测异常——默认不允许覆盖 JAR 内资源。
3. target 编译目录下的文件会优先映射回源码目录，开发时修改源码立即生效。
4. Bean 发现基于 VFS 文件路径，不是 Java classpath annotation scanning——需要确保 `_vfs` 下有对应的 bean 文件。

## 相关文档

- `../02-core-guides/delta-customization.md` — Delta 定制具体写法
- `../02-core-guides/ioc-and-config.md` — IoC bean 发现与配置
- `../02-core-guides/debugging-and-diagnostics.md` — VFS 刷新与重复检测调试
- `../01-repo-map/where-things-live.md` — VFS 中各资源类型的位置
- `../04-reference/source-anchors.md` — VFS 实现锚点
