# nop-core 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-kernel/nop-core
- 文件数: 757（src/main/java，未含 target/；`io/nop/core/unittest/` 测试支撑类虽在 main 下但未计入重点）
- 覆盖范围声明:
  - 全模块 grep 扫描（每个命中点均 Read 验证）: 空 catch / `printStackTrace` / `new RuntimeException` / `Thread.sleep` / `SimpleDateFormat` / `synchronized` / 静态可变集合 / `getCanonicalPath` / `..` 路径拼接 / `Math.random` 等。
  - 深读: resource 包核心（ResourceHelper、VirtualFileSystem、DefaultVirtualFileSystem、LocalResourceStore、InMemoryResourceStore、ZipResourceStore、ResourceTreeNode、DeltaResourceStore、FileNamespaceHandler、FileResource、ClassPathResource、ZipEntryResource、AutoCloseZipEntryResource、InputStreamResource、DynamicResource、SimpleBakResourceHistory、ClassPathScanner、VfsConfigLoader、ResourceLoadingCache、ResourceCacheEntry、ResourceDependsManager、ResourceDependsStack、ResourceDependencySet、DefaultResourceChangeChecker、ResourceTenantManager、ResourceComponentManager）；initialize 链（CoreInitialization）；reflect（ReflectionManager、ClassExtension、ClassModelBuilder 部分、BeanCopier、集合适配器、AopCodeGenerator）；context（ServiceContextImpl、部分 ExecutionContextImpl）；lang/xml/parse/XNodeParser（XXE 检查）；stat/JdbcSqlStat（原子性抽查）；exceptions/ErrorMessageManager。
  - 抽查未深读: lang/json 序列化细节、lang/eval 大部分、model/graph、model/table、Underscore、XNode 主体（2824 行）、reflect/accessor 与 reflect/bean 的 BeanModelBuilder。这些区域的结论不体现在发现列表中。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 3 |
| P2 | 4 |
| P3 | 10 |

## 发现列表

### [P0] BeanCopier 数组浅拷贝复制 0 个元素，目标数组静默保持为空

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanCopier.java:115-119`
- **维度**: D1
- **证据**:
```java
private void _copyToArray(Object src, Object target, IGenericType targetType, boolean deep,
                          FieldSelectionBean selection, BeanCopyOptions options) {
    ...
    // 数组元素类型一致
    if (srcCompType == targetType.getComponentType().getRawClass()) {
        if (!deep || isSimpleType(srcCompType)) {
            System.arraycopy(src, 0, target, 0, 0);   // <-- length 传了 0
            return;
        }
    }
```
- **现状**: 源/目标组件类型一致且浅拷贝（`deep=false`）时的快路径调用 `System.arraycopy(src, 0, target, 0, 0)`，最后一个参数（复制长度）硬编码为 `0`，应为 `n`。自首次提交（git log -L 确认）即如此。
- **风险**: 触发路径现实且确定: 公共 API `BeanTool.copyBean(src, target, type, deep=false)` / `BeanTool.copyProperties(src, target)` 在 src、target 为同组件类型数组时（如 `String[]` → `String[]`、`int[]` → `int[]`）静默不复制任何元素，目标数组保持全空，属于数据丢失级 bug。另外若 src 是 `Collection` 且 `BeanCollectionAdapter.getComponentType` 解析出的元素类型与目标数组组件类型相同（如 `class MyList extends ArrayList<String>` 目标 `String[]`），会把 List 传给 `System.arraycopy`，JDK 对非数组 source 直接抛 `ArrayStoreException`。
- **建议**: 改为 `System.arraycopy(src, 0, target, 0, n)`；并确认该快路径仅在 `src` 确为数组时进入（`_copyToCollection` 的对应快路径是 `target.add(v)`，不受此影响）。
- **误报排除**: 已读完整方法与两个适配器（`ArrayBeanCollectionAdapter.getComponentType` 返回真实数组组件类），确认长度参数不是变量而是字面 `0`；对比 `_copyToCollection` 同构分支是逐元素 add，证实此处意图是全量复制。

### [P1] `IFile.getResource("..")` 绕过相对名校验，FileResource 可逃逸到父目录

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/impl/AbstractFile.java:71-78`（根因在 nop-commons `StringHelper.isCanonicalFilePath`，nop-kernel/nop-commons/src/main/java/io/nop/commons/util/StringHelper.java:2478-2498）
- **维度**: D5（兼 D8）
- **证据**:
```java
// AbstractFile.getResource
Guard.checkArgument(ResourceHelper.isValidRelativeName(relativeName), "invalid relative resource path", relativeName);
...
// ResourceHelper.isValidRelativeName -> StringHelper.isCanonicalFilePath(".."):
if (name.contains("../"))  return false;   // ".." 不含 "../"，通过
if (name.endsWith("/.."))  return false;   // ".." 不以 "/.." 结尾，通过
// 最终 isValidFilePath("..") == true

// FileResource.doGetRelative：
File subFile = new File(file, relativeName);   // new File(dir, "..") => 父目录
```
- **现状**: `IFile.getResource` 的契约（IFile.java:53 注释）明确 relativeName "不能包含./或者../这种相对定位形式"，但校验函数对**恰好等于 `..`** 的整段名字漏判（`isCanonicalFilePath` 只检查 `../` 子串和 `/..` 后缀，裸 `..` 全部通过；`"a/.."`、`"a/../b"` 等均被正确拒绝，只有裸 `".."` 例外）。`FileResource.doGetRelative` 随后直接 `new File(file, "..")` 得到父目录资源，可链式调用（`getResource("..").getResource("..")`）逐级上溯。
- **风险**: 任何把外部输入作为 relativeName 传给 `IFile.getResource` 的调用点都可实现目录逃逸。VFS 主入口（`DefaultVirtualFileSystem.getResource` → `checkNormalVirtualPath`）因路径必须以 `/` 开头而不会出现裸 `..` 段，但 `AbstractFile.getResource` 是分散在所有 IFile 实现上的独立入口，单层防线失效。
- **建议**: 在 `isCanonicalFilePath`（或 `isValidRelativeName`）中显式拒绝整段 `"."`/`".."`（以及以 `"/"` 分割后任一段为 `..` 的路径）。
- **误报排除**: 逐条核对了 `isCanonicalFilePath` 的全部短路条件与 `isValidFilePath("..")` 的返回（无非法字符、无 `//`），确认裸 `..` 通过；并确认 `AbstractFile.getResource` 为 `final`、FileResource 未另行校验；`new File(dir,"..")` 语义即父目录。

### [P1] `loadComponentModelByUrl` 的 `?` 查询段解析错误，接口文档声明的 URL 格式必然失败

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:295-316`
- **维度**: D1（兼 D8）
- **证据**:
```java
int pos = resourcePath.indexOf('#');
if (pos > 0) { subName = resourcePath.substring(pos + 1); resourcePath = resourcePath.substring(0, pos); }
pos = resourcePath.indexOf('?');
if (pos > 0) {
    transform = resourcePath.substring(0, pos);   // 与下一行相同
    resourcePath = resourcePath.substring(0, pos); // 两个都是前缀
}
```
- **现状**: `?` 分支两个赋值完全相同（复制粘贴错误），`transform` 被赋成资源路径本身，`?` 之后的内容全部丢失。接口契约（IResourceComponentManager.java:100）声明 url 格式为 `resourcePath?paramName=paramValue`，如 `/a/b.xmeta?transform=xdef&sub=MyObject`。
- **风险**: 按文档格式调用 `loadComponentModelByUrl("/a/b.xmeta?transform=xdef")` 时 transform="/a/b.xmeta"，`getTransformer(modelType, "/a/b.xmeta")` 查不到转换器，抛 `ERR_COMPONENT_UNDEFINED_COMPONENT_MODEL_TRANSFORM`；即文档格式下公共 API 必然失败（或在更巧合的取值下静默返回错误模型）。对比 `#` 分支（subName 取后缀）可确认 `?` 分支本应取后缀并解析参数。
- **建议**: 按文档解析查询参数（至少 `transform = resourcePath.substring(pos + 1)`），与 `#` 分支对称。
- **误报排除**: 通读方法全文与接口 javadoc，确认没有其他地方解析回 `?` 之后内容；仓库内无其他调用方（该 bug 属公共 API 契约破坏，触发条件就是按文档使用）。

### [P1] `ResourceHelper.resolveResourceInDir` 的 `../` 防护可被裸 `..` 绕过，且 `FileHelper.getAbsolutePath` 归一化会消除痕迹

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/ResourceHelper.java:1136-1146`（配合 nop-kernel/nop-commons/src/main/java/io/nop/commons/util/FileHelper.java:405-411）
- **维度**: D5
- **证据**:
```java
public static IResource resolveResourceInDir(String dir, String fileName) {
    String path = fileName;
    fileName = fileName.replace('\\', '/');
    if (!StringHelper.isEmpty(dir)) {
        if (fileName.contains("../"))                       // 只拦截 "../" 子串
            throw new IllegalArgumentException("nop.err.invalid-file-name:" + fileName);
        path = StringHelper.appendPath(dir, fileName);      // appendPath 纯拼接，不归一化
    }
    return resolveRelativePathResource(path);
}
// FileHelper.getAbsolutePath(file) => StringHelper.normalizePath(file.getAbsolutePath())
// normalizePath 会把 "/data/app/.." 折叠成 "/data"，FileNamespaceHandler 校验的是折叠后的路径，防线被绕过
```
- **现状**: 该方法存在的 `../` 检查是显式安全防护（抛 "nop.err.invalid-file-name"），但 `fileName == ".."` / `"./.."` 不含 `../` 子串而通过；随后 `resolveRelativePath` → `getFileUrl(new File(...))` → `getAbsolutePath` 调用 `normalizePath` 把 `..` 折叠掉，最终 `FileNamespaceHandler.checkNormalVirtualPath` 看到的是已归一化的父目录路径，全部校验通过，静默返回 dir 之外（父目录）的资源。
- **风险**: 仓库内无其他调用方，属于暴露给下游应用的公共工具方法；任何把它用于"把用户提供的文件名限制在目录内"的场景都可越权访问父目录。
- **建议**: 拦截条件改为归一化后再比较（`StringHelper.normalizePath(fileName)` 是否逃出 dir），或拒绝任何包含 `..` 段的文件名；同时按两档错误策略改抛 `NopException + ErrorCode`。
- **误报排除**: 已核对 `appendPath`（纯拼接）、`normalizePath`（折叠 `..`）、`FileHelper.resolveFile/getFileUrl/getAbsolutePath`（均经 normalizePath）与 `FileNamespaceHandler.buildFileResource` 的校验顺序，确认带 `..` 的中间路径在到达最终校验前已被折叠为合法父目录路径。

### [P2] `ReflectionManager.unregisterTypeConverter` 从错误的 Map 移除，注销操作无效

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/ReflectionManager.java:255-262`
- **维度**: D1（兼 D8）
- **证据**:
```java
public void registerTypeConverter(Type clazz, ITypeConverter converter) {
    registeredConverters.put(clazz, converter);      // 注册进 registeredConverters
}
public void unregisterTypeConverter(Type clazz, ITypeConverter converter) {
    converterCache.remove(clazz, converter);          // 却从 converterCache 移除
}
```
- **现状**: 注册写入 `registeredConverters`，注销却清理 `converterCache`（懒加载缓存），且未校验值相等（`converterCache.remove(clazz, converter)` 即便 key 命中，value 也不等）。注销后 `getConverterForJavaType` 仍从 `registeredConverters` 命中旧转换器。
- **风险**: 公共注销 API 静默失效：模块卸载/热替换后旧转换器继续生效，产生错误类型转换。仓库内当前无其他调用方，属契约破坏+条件触发。
- **建议**: `unregisterTypeConverter` 改为 `registeredConverters.remove(clazz, converter)`，并同步清理 `converterCache`。
- **误报排除**: 对比同文件 `registerClassModel/unregisterClassModel`、`registerInvokers/unregisterInvokers`（均操作同一 Map），确认本处为笔误而非有意设计。

### [P2] `CoreInitialization.reinitialize` 独立调用时 NPE（bootstrapConfig 未加载）

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:264-275`（触发点 212-219）
- **维度**: D1
- **证据**:
```java
public static synchronized void reinitialize(int fromLevel) {
    List<ICoreInitializer> list = initializers;
    if (list == null) {
        list = loadInitializers();     // -> getDisabled()
    }
...
private static Set<String> getDisabled() {
    Object configValue = CFG_MODULE_DISABLED_MODULE_NAMES.get();   // 默认 null
    if (configValue == null)
        configValue = bootstrapConfig.get(...);   // bootstrapConfig 可能为 null -> NPE
```
- **现状**: `loadBootstrapConfig()` 只在 `initializeTo` 中调用；`reinitialize` 在 `initializers == null` 时（新 JVM 直接调用 reinitialize，或 `destroy()` 已把 initializers 和 bootstrapConfig 置 null 后调用）会经 `loadInitializers → getDisabled` 对 null 的 `bootstrapConfig` 调用 `.get()` 抛 NPE。
- **风险**: reinitialize 的代码显式支持 initializers 为 null 的独立调用场景，但该场景在 `nop.core.module.disabled-module-names` 未配置（默认 null）时必然 NPE。
- **建议**: `getDisabled` 增加 `bootstrapConfig == null` 判空，或 reinitialize 入口先调 `loadBootstrapConfig()`。
- **误报排除**: 已确认 `CFG_MODULE_DISABLED_MODULE_NAMES` 默认值为 null（CoreConfigs.java:187-188），且 destroy() 首行 `bootstrapConfig = null`、随后 `initializers = null`。

### [P2] `FileResource.mkdirs()` 条件反转，永远无法创建新目录

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/impl/FileResource.java:175-180`
- **维度**: D1（兼 D8）
- **证据**:
```java
@Override
public boolean mkdirs() {
    if (!isDirectory())                       // file.isDirectory()：目录不存在时为 false
        throw new NopException(ERR_RESOURCE_NOT_DIR).param(ARG_RESOURCE, this);
    return file.mkdirs();
}
```
- **现状**: 目录尚不存在时 `isDirectory()` 为 false，直接抛 `ERR_RESOURCE_NOT_DIR`；目录已存在时 `file.mkdirs()` 恒返回 false。方法在任何输入下要么抛异常要么返回 false，`IFile.mkdirs()` 的"创建目录"契约不可实现。对比 `UnknownResource.mkdirs()` 返回 false、`VirtualFile.mkdirs()` 委托本方法，均被连带废掉。
- **风险**: 公开契约失效；依赖 `IFile.mkdirs()` 建目录的下游代码会意外得到异常。当前仓库内 main 代码主要经 `FileHelper.assureParent` 建目录，未踩中。
- **建议**: 移除该前置检查（或改为对"已存在且非目录"的情况抛错），直接 `return file.mkdirs()`。
- **误报排除**: 确认 `FileResource.isDirectory()` 即 `file.isDirectory()`，无其他覆写；`ERR_RESOURCE_NOT_DIR` 语义为"资源不是目录"，用在建目录入口属逻辑反转。

### [P2] SqlLikeUtils 用裸 RuntimeException 报错，违背框架核心错误处理两档策略

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/model/query/SqlLikeUtils.java:121-127`
- **维度**: D4（兼 D7）
- **证据**:
```java
public static RuntimeException invalidEscapeCharacter(String s) {
    return new RuntimeException("Invalid escape character '" + s + "'");
}
public static RuntimeException invalidEscapeSequence(String s, int i) {
    return new RuntimeException("Invalid escape sequence '" + s + "', " + i);
}
```
- **现状**: LIKE/SIMILAR 模式非法转义（`invalidEscapeSequence` 在 101/108/139/143 行被 throw）时抛裸 `RuntimeException`。该类位于平台核心、由查询模型构造触发（模式串可来自外部输入），按约定应使用 `NopException + ErrorCode + .param(...)`。
- **风险**: 错误码/参数缺失导致无法国际化、无法归一映射；违背 AGENTS.md 明确的 "Never use bare RuntimeException"。
- **建议**: 定义对应 CoreErrors 错误码并改抛 `NopException`。同类问题: `ResourceHelper.resolveRelativeResource`（ResourceHelper.java:277-278）抛裸 `IllegalArgumentException`（见 P3 条目）。
- **误报排除**: 该类看似移植自 PostgreSQL JDBC 工具，但位于 `io.nop.core.model.query` 且被平台查询模型使用，属自产核心代码而非第三方内联，两档策略适用。

### [P3] `ResourceCacheEntry.reloadObject` 对 null 重载结果的处理与 getObject 不一致

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:226-238`
- **维度**: D1
- **证据**:
```java
private synchronized void reloadObject(IResourceObjectLoader<T> loader) {
    Object oldObj = normalizeObject(this.object);
    Object obj = loadObject(loader);
    if (obj != oldObj) {
        if (listener != null) { listener.onCreated((T) obj); }   // obj 可能为 null
        this.object = obj;                                       // 置裸 null 而非 Null.NULL
        destroyObject(oldObj);
    }
}
```
- **现状**: `getObject` 对 null 结果置 `Null.NULL` 占位且仅非 null 才回调 `onCreated`；`reloadObject`（checkRefresh 强制刷新路径）对 null 结果置裸 null、仍调用 `listener.onCreated(null)`。
- **风险**: 刷新返回 null 后（配合 cacheNull=true），后续每次 `getObject` 都把 object==null 视为"未加载"而重复装载；`onCreated(null)` 破坏监听器约定。
- **建议**: reloadObject 对 null 采用与 getObject 相同的 `Null.NULL` + 跳过 onCreated 逻辑。
- **误报排除**: 已对照 getObject(175-207) 的完整分支确认两处语义分歧。

### [P3] `ClassPathResource.initLength` 空 catch 吞掉全部异常且 URLConnection 未释放

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/impl/ClassPathResource.java:114-124`
- **维度**: D4（兼 D2）
- **证据**:
```java
private void initLength() {
    if (length >= 0) return;
    URLConnection conn = null;
    try {
        conn = url.openConnection();
        this.length = conn.getContentLengthLong();
    } catch (Exception expected) {
    }
}
```
- **现状**: 打开 URLConnection 读取长度后从不 disconnect；异常被空 catch 完全吞掉（无日志），length 静默保持 -1。
- **风险**: 对 http(s) 类 classpath URL 会遗留连接；长度获取失败无任何痕迹，排障困难。classpath 常见 jar:/file: 协议下影响有限，故定 P3。
- **建议**: 至少 DEBUG 记录异常；finally 中对 HttpURLConnection 调 disconnect。
- **误报排除**: 已确认 length() 只在 `toFile()==null` 时走到该分支（jar 内资源），此时确会触发。

### [P3] AopCodeGenerator 生成的静态块用 `e.printStackTrace()` 吞异常

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/aop/AopCodeGenerator.java:114-116`
- **维度**: D4
- **证据**:
```java
buf.append("        } catch (Exception e) {\n" + "            e.printStackTrace();\n" + "        }\n");
```
- **现状**: 生成的代理类静态初始化块反射失败时仅 stdout 打印，不抛出；失败后对应 MethodModel 字段为 null，后续调用将以 NPE 形式暴露，丢失根因。
- **风险**: 生成代码质量缺陷，故障定位困难；与平台日志体系（NOP 错误码/SLFJ）脱节。
- **建议**: 生成的 catch 中抛 `NopException`（或至少用日志框架 error 级输出）。
- **误报排除**: 已确认该字符串拼进生成的 `.java` 源码（`$$methodName_i` 字段初始化块），非运行时代码。

### [P3] DefaultVirtualFileSystem 的 refresh/destroy 锁使用不一致，状态字段无安全发布

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java:69-91`
- **维度**: D3
- **证据**:
```java
public synchronized void refresh(boolean refreshDepends) { ... buildResourceStore(); ... }
public void destroy() {            // 未同步
    ...
    this.deltaResourceStore = deltaResourceStore;   // 非 volatile 字段无同步写入
```
- **现状**: `refresh` 是 synchronized，`destroy` 不是；`deltaResourceStore`、`zipFiles` 为普通字段，读路径（getResource 等）全部无锁。
- **风险**: refresh/destroy 与并发读之间没有 happens-before，读线程可能长时间看到旧 store 或半更新状态；refresh 期间旧行为多为良性（旧资源），但在 destroy 后仍可能使用已关闭的 zip 相关 store。低概率、影响限于刷新窗口。
- **建议**: destroy 与 refresh 统一加锁；字段声明为 volatile（引用替换型更新足够）。
- **误报排除**: 已确认无其他同步手段（字段无 volatile、读路径无锁）。

### [P3] 依赖变更检查并发更新共享 ResourceDependencySet.lastModified 无同步

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/deps/ResourceDependsManager.java:240-246`
- **维度**: D3
- **证据**:
```java
private boolean isDependencyChanged(ResourceDependencySet deps, Set<String> checkedResourcePaths) {
    ...
    ResourceChangeCheckResult result = changeChecker.checkChanged(deps.getResourcePath(), deps.getLastModified());
    if (result.isChanged()) return true;
    deps.setLastModified(result.getLastModified());   // 多线程同时进入，非 volatile long
```
- **现状**: `isAnyDependsChange` 会被多个线程的 `ResourceCacheEntry.isChanged()` 并发调用，对已冻结（freeze 后仍允许 setLastModified）的共享 `ResourceDependencySet` 无锁读写 `lastModified`（非 volatile long）。
- **风险**: 64 位 long 理论上存在撕裂读；主要实际影响是可见性延迟（偶发多查一次文件时间戳），不破坏结构。冻结机制保证了 dependsMap 不再结构变更，故仅 P3。
- **建议**: `lastModified` 声明为 volatile（或 AtomicLong）。
- **误报排除**: 已核对 ResourceDependencySet: freeze 只置 frozen 标志，setLastModified 无 checkAllowChange，确认共享可变。

### [P3] `ResourceHelper.readBytes` 对超过 2GB 资源做 int 截断

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/ResourceHelper.java:739-747`
- **维度**: D1
- **证据**:
```java
long length = resource.length();
if (length > 0) {
    byte[] data = new byte[(int) length];   // length > Integer.MAX_VALUE 时 (int) 截断可能为负
```
- **现状**: `length` 在 (2^31, 2^32) 区间时 `(int) length` 为负 → `NegativeArraySizeException`；更大的值静默分配错误大小后由 `readFully` 抛错。
- **风险**: 常规资源文件不会超过 2GB，触发条件苛刻，故 P3；一旦触发是无意义的底层异常而非 NopException。
- **建议**: `length > Integer.MAX_VALUE` 时直接抛带 ErrorCode 的 NopException 或走流式读取。
- **误报排除**: 已确认仅 `length==0` 与 `length<0` 有专门分支，正数大文件直接截断。

### [P3] `ErrorMessageManager.clearErrorCodeMappings` 未清理 subMappings

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/exceptions/ErrorMessageManager.java:104-106`
- **维度**: D1（兼 D8）
- **证据**:
```java
public void clearErrorCodeMappings() {
    this.errorCodeMappings.clear();     // subMappings 未清空
}
```
- **现状**: `addErrorCodeMappings` 同时维护 `errorCodeMappings` 与 `subMappings` 两张表，清空方法只清前者；`subMappings`（errorCode?param 形式的细化映射）在重复 loadErrorCodeMappings 时按同 filter 替换，但已删除模块的条目永久残留。
- **风险**: 错误码映射热重载/模块卸载后，旧细化映射继续参与 `resolveErrorBean` 匹配（412 行读取），可能把已下线的错误码映射到过期响应码。当前 main 代码无人调用 clear（影响面在下游热重载场景）。
- **建议**: clear 时同步 `subMappings.clear()`。
- **误报排除**: 已 grep 全文件确认 subMappings 无其他清空点，读取点在 412 行。

### [P3] `DeltaResourceStore.getRawResource` 对无子路径的租户路径抛数组越界

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DeltaResourceStore.java:298-306`
- **维度**: D1
- **证据**:
```java
if (ResourceHelper.isTenantPath(path)) {                     // 前缀 "/_tenant/"
    int pos = path.indexOf('/', ResourceConstants.TENANT_PATH_PREFIX.length());
    String tenantId = path.substring(ResourceConstants.TENANT_PATH_PREFIX.length(), pos);  // pos==-1 时越界
```
- **现状**: 路径形如 `/_tenant/abc`（有租户段、无后续子路径）时 `indexOf` 返回 -1，`substring(9, -1)` 抛 `StringIndexOutOfBoundsException`，绕过错误处理体系。
- **风险**: 仅畸形路径触发（正常使用都带子路径），影响是异常类型不友好，P3。
- **建议**: pos<0 时抛 `ERR_RESOURCE_INVALID_PATH` 的 NopException。
- **误报排除**: 已确认 `TENANT_PATH_PREFIX = "/_tenant/"`（以 `/` 结尾），`/_tenant/abc` 场景 pos 必为 -1。

### [P3] `ReflectionManager.getFromCache` 并发下重复构建 ClassModel

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/ReflectionManager.java:325-339`
- **维度**: D6
- **证据**:
```java
IClassModel model = introspectCache.get(clazz);
if (model != null) return model;
ClassModelLoader loader = tempLoaders.computeIfAbsent(clazz, cls -> new ClassModelLoader(paramClass));
IClassModel classModel = loader.load();      // synchronized 但不记忆结果
introspectCache.put(clazz, classModel);
```
- **现状**: 两个线程同时 miss 时先后进入同一个 `synchronized load()`，第二个线程在第一个完成后仍会从头重建 ClassModel（synchronized 方法内没有"已构建则复用"检查），构建结果只写缓存一次、重复构建 n-1 次。
- **风险**: 反射模型构建较重（全方法扫描），高并发冷启动时浪费 CPU；正确性不受影响。设计上已通过 tempLoaders 规避了 CHM 递归死锁，此为残留的重复工作问题。
- **建议**: `ClassModelLoader` 记忆首次结果（double-check 或在锁内检查 introspectCache）。
- **误报排除**: 已确认 load() 每次都 new ClassModelBuilder().build()，无缓存判断。

### [P3] `ResourceHelper.resolveRelativeResource` 抛裸 IllegalArgumentException

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/ResourceHelper.java:276-282`
- **维度**: D4（兼 D7）
- **证据**:
```java
public static IResource resolveRelativeResource(IResource resource, String relativePath, boolean allowParent) {
    if (!allowParent && relativePath.contains(".."))
        throw new IllegalArgumentException("invalid-relative-path:" + relativePath);
```
- **现状**: 框架核心公共工具方法用裸 `IllegalArgumentException` + 手工拼接消息报错，违背两档错误策略（应 `NopException + ErrorCode + .param`）。与 `resolveResourceInDir` 的 `IllegalArgumentException("nop.err.invalid-file-name:...")` 同类。
- **风险**: 错误无错误码、无法国际化；`contains("..")` 也偏宽（会误拒 `a..b` 这类合法名字，属 fail-safe 方向，可接受）。
- **建议**: 改用 NopException + 专用错误码。
- **误报排除**: 已确认方法无其他入参校验，且消息串非错误码资源格式（nop.err.* 前缀形式仅在 i18n 资源存在时有效，此处为硬编码串）。

## 补充说明（未计入发现的观察）

- XNodeParser 为手写解析器，仅支持 5 个内置实体、未知实体抛 `ERR_XML_UNKNOWN_XML_ENTITY`，未发现 XXE 风险。
- `JdbcSqlStat` 全部计数经 AtomicFieldUpdater，抽查未发现非原子读改写。
- `AutoCloseZipEntryResource` 的流关闭链（FilterInputStream.close → 关 ZipFile）实现正确。
- `ResourceTreeNode`/`InMemoryResourceStore` 使用非线程安全 TreeMap，但按"初始化期写、运行期读"模式使用；若运行期调用 `updateInMemoryLayer` 并发读存在理论竞争，未找到现实触发链，未列发现。
- `unittest` 包（BaseTestCase 等静态可变集合）位于 src/main 但属测试基础设施，未计入。
