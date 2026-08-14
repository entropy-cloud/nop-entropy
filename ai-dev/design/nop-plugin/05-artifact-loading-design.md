# plugin artifact 加载与类加载设计

**日期**：2026-08-14
**范围**：`nop-plugin-manager`（artifact 加载器）+ `PluginClassLoader`（类加载）；不改 `nop-plugin-api`
**状态**：active
**关联**：`01-architecture-baseline.md`（§二 定义来源双轨之 uber jar 路径）

---

## 一、设计结论

1. **类加载模型**：plugin 的 ClassLoader **parent = Nop 平台 classloader**——plugin 可直接使用底层 Nop 平台的全部类；plugin **自己的类**通过 plugin id 从加载器下载的 jar 中加载（`PluginClassLoader`）。
2. **加载器接口**：`IPluginResourceResolver`（现有接口，契约增强）——通过 plugin id（`ArtifactCoordinates`）经 http/https 之类 URL **下载到本地 repository**，返回本地 jar URL。
3. **hash 校验**：下载 jar 须做 **SHA256 校验**（hash 来源：响应 header `X-Checksum-Sha256`、`{url}.sha256` 文件或配置 expected-hash map）；校验失败 fail-fast（删除损坏文件、抛异常），不使用未校验的 jar（**已落地**：W7 补齐，含缓存重验/遗留缓存重下载/无 hash 来源显式失败）。
4. **缺省实现**：`HttpPluginResourceResolver`（基于 `IHttpClient`，`io.nop.http.api.client.IHttpClient`），通过模板 URL（`nop.plugin.service-url`，groupId/artifactId/version 变量）下载。

## 二、类加载模型

```
Nop 平台 classloader（宿主）
   ▲ parent（Java 委派：parent-first）
   │
PluginClassLoader（jar: 本地 repository 中该 plugin 的 jar）
   ├─ 解析 plugin 自己的类 → 从 jar 加载
   └─ 找不到时委派 parent → 平台全部类直接可用
```

- plugin **可直接使用底层 Nop 平台的全部类**（parent 委派，无需重新打包平台类）。
- plugin **自己的类**仅存在于其 jar 中（`PluginClassLoader(urls, parent=平台classloader)`——现状已如此：`PluginManagerImpl:48` 传入 `this.getClass().getClassLoader()`）。
- **接口类型可见性约束**（01 §二双轨表）：`getService(Class<T>)` 的 `T` 须宿主可见——uber jar 场景下接口类由平台加载，plugin jar 只装实现类。

## 三、加载器接口（IPluginResourceResolver，契约增强）

```java
public interface IPluginResourceResolver {
    /**
     * 通过 plugin id 解析 artifact：若本地 repository 无缓存则经 URL（http/https）下载，
     * 下载后做 SHA256 校验；校验通过返回本地 jar URL（已缓存且校验通过则直接返回）。
     */
    List<URL> resolvePluginResource(ArtifactCoordinates pluginId);
}
```

**契约要点**：
1. **输入** plugin id（`ArtifactCoordinates`：groupId/artifactId/version）。
2. **输出** **本地** jar URL（不返回远程 URL——PluginClassLoader 只读本地文件，避免远程加载的不可控）。
3. **职责链**：URL 组装（模板）→ 下载到本地 repository（临时文件 + 原子 move）→ **SHA256 校验** → 返回本地路径。
4. **幂等**：本地已缓存且校验通过 → 直接返回（不重复下载）。

## 四、本地 repository

| 项 | 设计 |
|---|---|
| 目录 | 配置化：`nop.plugin.cache-dir`（默认 `/nop/plugin`）；可作为系统属性/配置覆盖 |
| 布局 | Maven 风格：`{repo}/{groupId路径}/{artifactId}/{version}/{artifactId}-{version}.jar`（`ArtifactCoordinates.getJarFilePath()` 已提供） |
| 校验文件 | 同目录 `{jar}.sha256` |
| 缓存语义 | jar 存在且 sha256 匹配 → 直接使用；否则重新下载 |

> **修订注解（W7，2026-08-15）**：缓存篡改语义按 W7 Decision 裁定为 **fail-fast**——缓存 jar 重验发现与 `.sha256` 不匹配（篡改/损坏）时抛 `ERR_PLUGIN_SHA256_MISMATCH`（报警而非静默重下载，与"绝不使用未通过校验的 jar"一致）；缓存 jar 但 `.sha256` 缺失（pre-W7 遗留缓存）→ **重新下载并校验**（不静默使用未校验缓存）；`nop.plugin.skip-cache-verify=true` 时同时跳过重验与遗留重下载（启动优化逃生门，声明式显式行为）。
| 原子性 | 下载到临时文件（`.tmp`）→ 校验 → 原子 move 到目标（现状已有 tmp+move，补校验后再 move） |

## 五、SHA256 校验（补齐现状缺口）

- **算法**：SHA256（`HashHelper`，nop-commons 已有；文件摘要 helper 为 resolver 私有静态方法，不新增 nop-commons 公共 API——见 §六 修订注解）。
- **hash 来源**（按优先级）：
  1. **元数据服务**：随下载服务提供的 hash（如响应 header `X-Checksum-Sha256` 或 `{url}.sha256` 文件请求）；
  2. **配置显式指定**：调用方在 plugin id 元数据中携带预期 hash。

> **修订注解（W7，2026-08-15）**：§五.2 的"plugin id 元数据携带预期 hash"载体裁定为 **resolver 级 expected-hash map**——`ArtifactCoordinates`（nop-api-core 公共 API）无 hash 字段、新增字段属公共 API 变更（§八 拒绝 API 面扩大），resolver 接口 `resolvePluginResource(coordinates)` 无额外元数据通道；`HttpPluginResourceResolver` 新增 setter 注入 `Map<String,String>`（key = `groupId:artifactId:version`，value = hex hash，宿主应用经 beans.xml 配置），优先级 header → `.sha256` 请求 → 该 map。全无 hash 来源 → 显式失败 `ERR_PLUGIN_CHECKSUM_NOT_AVAILABLE`（不允许静默降级为无校验下载）。
- **校验时机**：下载完成、**move 之前**（临时文件上校验；失败则删除临时文件，不留损坏产物）。
- **失败处理**：fail-fast——删除临时文件 + 抛 `NopException`（明确错误码与参数：pluginId、期望/实际 hash）；**绝不使用未通过校验的 jar**。
- **已缓存校验**：再次 resolve 时对本地 jar 重算 hash 与 `.sha256` 比对（防篡改/损坏；可配置跳过以优化启动）。

## 六、缺省实现（HttpPluginResourceResolver）

现有实现已具备：`@Inject IHttpClient`、`nop.plugin.service-url` 模板（`{pluginGroupId}/{pluginArtifactId}/{pluginVersion}` 变量渲染）、`cacheDir` 缓存、tmp+move 原子下载。**需补**：

```
download(coordinates, jarFile):
  tmp = createTempFile                                        // 前置 assureParent（Maven 风格子目录）
  httpClient.download(request(url), tmp)                      // 现状已有
  expectedSha256 = fetchChecksum(header → url + ".sha256" → 配置 map) // 补（优先级钉死）
  actualSha256 = 私有文件摘要 helper（HashHelper 只接受 byte[]）      // 补：不新增 nop-commons 公共 API
  if (!match) { delete(tmp); throw NopException(...) }        // 补：fail-fast
  delete(jarFile)                                             // 补：遗留缓存先删（moveFile 无 REPLACE_EXISTING）
  move(tmp, jarFile)                                          // 现状已有（校验后才 move）
  writeChecksumFile(jarFile, actualSha256)                    // 补（写失败 → 删已 move 的 jar + 抛异常）
```

> **修订注解（W7，2026-08-15）**：`resolvePluginResource` 缓存命中路径同步补重验（含 `nop.plugin.skip-cache-verify` 跳过配置）；遗留缓存（jar 在、`.sha256` 缺失）走重新下载。SHA256 文件摘要 helper 为 resolver 私有静态方法（`FileHelper.calculateMD5` 同款先例），不新增 nop-commons 公共 API（避免公共面 + 独立测试负担）。

## 七、加载链路（与 PluginManager 的关系）

```
loadPlugin(ArtifactCoordinates id)
  → IPluginResourceResolver.resolvePluginResource(id)      // 下载+校验 → 本地 jar URL
  → new PluginClassLoader(urls, 平台classloader)            // plugin 类从 jar，平台类经 parent
  → classLoader.loadPlugin()（ServiceLoader 发现 IPlugin 实现）
  → plugin.start / createInstance...
```

## 八、拒绝了什么

1. **PluginClassLoader 直接使用远程 URL**：拒绝——远程加载不可控（网络抖动/重复下载），必须先落地本地 repository 并校验。
2. **无校验的下载**：拒绝——Javadoc 声称有校验而代码没有的现状即是反例；hash 校验是安全底线。
3. **每次 resolve 都重新下载**：拒绝——本地 repository 缓存 + hash 比对，幂等复用。
4. **接口放 `nop-plugin-api`**：拒绝——`IPluginResourceResolver` 依赖 `ArtifactCoordinates`（nop-api-core）与 URL 语义，属 manager 层扩展点；api 保持零依赖（加载器是宿主侧机制，不是 plugin 实现者的契约）。
5. **自定义新接口名**（如 IPluginArtifactLoader）：拒绝——现有 `IPluginResourceResolver` 语义吻合（"解析 plugin 资源"），增强契约即可，避免接口翻倍。

## 九、源码锚点

| 锚点 | 位置 | 说明 |
|---|---|---|
| `IPluginResourceResolver` | `nop-plugin-manager/.../resolver/IPluginResourceResolver.java` | 加载器接口（契约增强：校验语义） |
| `HttpPluginResourceResolver` | `.../resolver/HttpPluginResourceResolver.java` | 缺省实现（IHttpClient + cacheDir + URL 模板 + SHA256 校验：下载后 move 前校验、缓存重验、expected-hash map setter、`nop.plugin.skip-cache-verify` 跳过配置） |
| `PluginClassLoader` | `.../classloader/PluginClassLoader.java` | parent=平台 classloader（plugin 用平台全部类，自己类从 jar） |
| `PluginManagerImpl.loadPlugin` | `.../impl/PluginManagerImpl.java:45` | 加载链路入口（resolver → classloader → ServiceLoader → start） |
| `IHttpClient` | `io.nop.http.api.client.IHttpClient`（nop-http-api） | http 下载抽象（缺省实现依赖） |
| `ArtifactCoordinates` | `io.nop.api.core.beans.ArtifactCoordinates` | plugin id（含 `getJarFilePath()` Maven 风格路径） |
