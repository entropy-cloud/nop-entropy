# nop-core 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-kernel/nop-core
- 文件数: 757（src/main/java）
- 覆盖范围声明: 深读 45 个文件（XNode、XNodeParser、JsonTool、JsonSerializer、JsonMerger、JsonExtender、DeltaMergeHelper、DeltaJsonLoader、BeanCopier、BeanToolImpl、ReflectionManager、ClassExtension、EvalScopeImpl、EvalGlobalRegistry、ResourceHelper、ResourceComponentManager、ResourceLoadingCache、ResourceCacheEntry、ResourceTenantManager、DefaultVirtualFileSystem、DeltaResourceStore、InMemoryResourceStore、ZipResourceStore、FileResource、ZipEntryResource、AutoCloseZipEntryResource、NioFileWatchService、FileWatchEntry、CsvRecordInput/Output、JsonlRecordInput、CoreInitialization、ExecutionContextImpl、ServiceContextImpl、DefaultTaskExecutionQueue、DefaultDirectedGraph、TopologicalOrderIterator、ContainerTypeData、IGenericType、I18nMessageManager、GlobalStatManager、AopMethodInvocation、JsonlResourceRecordIO 等），约覆盖全部代码量的 25%（按行数，含 reflect/accessor 与 model/* 的接口阅读）；模式扫描（SimpleDateFormat、@Inject private、Spring @Value、bare RuntimeException、printStackTrace、new Random、ProcessBuilder、getInputStream 泄漏模式、synchronized 分布等）覆盖 100% 文件。未深读区域: reflect/accessor 全部 30 个 accessor 实现、reflect/impl 的 ClassModelBuilder/BeanModelBuilder 细节、lang/json/bind 的 resolver 编译器、model/query、model/table/impl、model/mapper、model/graph 的 AStar/SpanningTree/LCA 算法、unittest、i18n 的 Loader 细节。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 6 |
| P3 | 10 |

## 发现列表

### [P1] XNode.removeAll 遍历中删除子节点导致漏处理或 IndexOutOfBoundsException

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/xml/XNode.java:2004`
- **维度**: D1
- **证据**:
```java
public void removeAll(Predicate<XNode> filter) {
    checkNotReadOnly();

    if (filter.test(this)) {
        this.detach();
    } else {
        for (int i = 0, n = children.size(); i < n; i++) {
            XNode child = children.get(i);
            child.removeAll(filter);
        }
    }
}
```
- **现状**: 循环上限 `n` 在循环前缓存，但递归调用 `child.removeAll(filter)` 中当 `filter.test(child)==true` 时执行 `child.detach()`，其内部 `parent.getChildren().remove(this)` 会从**正在遍历的 `this.children` 列表中删除元素**（detach 见同文件 1669-1677 行）。
- **风险**: 只要被删除的直接子节点不在末位，后续 `children.get(i)` 会漏处理被顶替上来的元素；删除后 `i` 继续递增至旧 `n-1` 时抛 `IndexOutOfBoundsException`。例：children=[A(命中),B(不命中),C]，i=0 时 A 被 detach，i=2 时 get(2) 越界。该方法是 XNode 公开树操作 API（`removeAllByAttr` 的底层实现），"删除多个匹配节点"是其典型用法，一旦使用几乎必然触发。
- **建议**: 收集待删除子节点后统一删除（先迭代记录命中节点再逐个 detach），或改为倒序遍历 / 使用 `children.removeIf` 配合对命中节点递归清理。
- **误报排除**: 已确认 `detach()`（1669-1677 行）直接调用 `parent.getChildren().remove(this)`，且 `this.children` 就是循环中正在 get 的同一个列表对象；仓内 grep 确认 `removeAll`/`removeAllByAttr` 当前在 nop-core 主代码无调用方（仅测试间接路径），故不升 P0，但作为公开 API 语义错误确认成立。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。改为倒序遍历：`child.removeAll(filter)` 内部 detach 只会从当前列表移除下标 i 处元素，倒序时 i 之后的元素均已处理，天然免疫移位/越界。测试：TestXNode#testRemoveAll、#testRemoveAllByAttr（children=[a,b,c] 删 a/c 后仅剩 b）。红验证：HEAD 上两测试均以 Errors 形态失败（`IndexOutOfBoundsException`，i=0 处 a 被 detach 后 i=2 越界）；修复后绿。

### [P1] NioFileWatchService 的 watchKeyMap（HashMap）被多线程无锁并发读写

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:31`
- **维度**: D3
- **证据**:
```java
private final Map<WatchKey, FileWatchEntry> watchKeyMap = new HashMap<>();   // L31

// watcher 线程（checkChange 内，无锁）：
FileWatchEntry entry = watchKeyMap.get(key);          // L71
FileWatchEntry subEntry = register(...);              // L93，内部 watchKeyMap.put(key, entry)  L145

// 取消监视线程（任意调用方线程，unwatch 未加 synchronized）：
private void unwatch(FileWatchEntry entry) {          // L211
    this.watchKeyMap.remove(entry.getWatchKey());     // L213
```
- **现状**: `watch()` 与 `doStop()` 是 `synchronized`，但 watcher 线程的 `checkChange()`（get + 递归注册子目录时的 put，L71/L93/L145）和任意线程调用的 `unwatch()`（remove，L211-219，无 synchronized）都不持有实例锁。HashMap 被至少 3 类线程并发结构修改/读取。
- **风险**: HashMap 并发 put/remove/get 可能导致丢失映射（watch 事件被忽略）、`doStop()` 中遍历 `watchKeyMap.keySet()` 抛 ConcurrentModificationException，JDK7 及以下实现下 resize 还可能链表成环。热部署/动态注册目录监听场景（watch 递归扫描期间新目录触发 ENTRY_CREATE → watcher 线程 put，与主线程 watch() 的 put 并发）即可触发。
- **建议**: 将 `watchKeyMap` 改为 `ConcurrentHashMap`（最低成本），或让 `register`/`unwatch`/`checkChange` 内的访问统一持锁。
- **误报排除**: 已通读整个文件确认 `unwatch` 无 synchronized 修饰、`checkChange` 运行在独立 executor 线程且不获取实例锁；`FileWatchEntry.subEntries` 用了 ConcurrentHashMap 说明作者意识到了跨线程访问，但外层 map 遗漏。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`watchKeyMap` 由 `HashMap` 改为 `ConcurrentHashMap`（采纳报告最低成本选项；doStop 遍历 keySet 不再受 CME 威胁）。未做红验证：HashMap 并发结构破坏的触发依赖线程交错，无法构造确定性红测试；以功能回归测试 TestNioFileWatchService#testWatchFileCreate（真实 WatchService 上 watch→create file→listener 回调）守护行为未破坏，并发修复本身由类型替换闭合。

### [P1] BeanCopier._copyToCollection 对目标集合语义不一致：空源 clear、非空源直接追加

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/reflect/bean/BeanCopier.java:140`
- **维度**: D1
- **证据**:
```java
int size = srcAdapter.getSize(src);
if (size == 0) {
    target.clear();          // L142：空源 → 清空目标
    return;
}

// 数组元素类型一致
if (srcCompType == targetType.getComponentType().getRawClass()) {
    if (!deep || isSimpleType(srcCompType)) {
        srcAdapter.forEach(src, (v, i) -> {
            target.add(v);   // L150：非空源 → 不清空，直接追加
        });
        return;
    }
}
```
- **现状**: 源集合为空时目标集合被清空；源集合非空时目标集合原有元素保留并被追加新元素。同一方法两种互相矛盾的复制语义。
- **风险**: `copyBean(src, target, ...)` 是 `BeanTool.copyBean` 公开 API（见 BeanToolImpl.java:146-148），当调用方传入非空 Collection 作为 target（如复用已有集合对象）时，得到"旧元素 + 新元素"的重复数据；对照 `_copyToArray`（L108-110 长度不匹配直接抛异常）可推断本意是全量替换语义，非空分支漏掉 `target.clear()`。
- **建议**: 在方法开头统一 `target.clear()`（与 size==0 分支及 _copyToArray 的替换语义对齐）。
- **误报排除**: 已通读 BeanCopier 全文（60-520 行）及 BeanTool/BeanToolImpl 调用链，确认非空分支无任何复用目标元素的逻辑（无按下标 set），追加前不 clear 属于遗漏而非"合并追加"设计；size==0 分支的 clear 也反证替换语义为本意。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`target.clear()` 前移到方法开头统一执行（size==0 早退分支不再单独 clear），非空源复制前先清空目标，与 `_copyToArray` 的全量替换语义对齐。测试：TestBeanCopierArray#testCopyToCollectionReplacesTarget、#testCopyToCollectionEmptySrcClearsTarget（守护原空源清空语义）。红验证：HEAD 上 replacesTarget 失败形态 `array lengths differ, expected: <2> but was: <3>`（目标=[old,a,b]）；修复后绿。

### [P2] ExecutionContextImpl 的 complete()/completeExceptionally() 存在 done 检查竞态，回调可能以错误参数触发

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/context/ExecutionContextImpl.java:109`
- **维度**: D3
- **证据**:
```java
public void complete() {
    if (isDone())        // L110：锁外检查
        return;
    fireBeforeComplete();
    synchronized (this) {
        done = true;     // L115
    }
    fireAfterComplete(null);   // L117
}

public void completeExceptionally(Throwable exception) {
    synchronized (this) {
        this.error = exception;
        this.done = true;      // L124：无 isDone 检查
    }
    fireAfterComplete(exception);
}
```
- **现状**: `complete()` 的 `isDone()` 检查在 synchronized 块外；`completeExceptionally()` 完全不检查 done。两线程分别调用 complete() 与 completeExceptionally() 时可同时通过检查，一个走成功路径（fireAfterComplete(null)），另一个走失败路径（fireAfterComplete(error)）。虽然 afterCompletes 列表通过"取走置 null"保证回调只执行一次，但执行的那次可能拿到与实际终态相反的参数（如已 completeExceptionally 后仍以 null 触发回调）。另外 `fireBeforeComplete()` L143 读 `error` 字段无同步（error 由 synchronized 的 setError 写入），存在可见性问题。
- **风险**: 并发完成/失败竞态下（如请求取消与正常完成同时到达），完成回调观察到错误的终态，下游可能把失败任务当成功清理。
- **建议**: `complete()`/`completeExceptionally()` 进入时先 `synchronized` 双重检查 done，终态参数在锁内确定后再触发回调；`fireBeforeComplete` 中的 `error` 读取放入同步块。
- **误报排除**: 已通读该文件全部方法，确认无其他 CAS/守卫机制；fireAfterComplete 的幂等（置 null）只防重复执行、不防参数错乱。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`complete()` 改为锁内首检 + fireBeforeComplete 后锁内二重检查抢占终态（win 标志），仅抢占成功者触发 fireAfterComplete(null)；`completeExceptionally()` 进入即锁内检查 done，已终结直接返回（不再改写 error、不再触发回调）；`fireBeforeComplete` 对 error 的读取改经 synchronized 的 `getError()` 保证可见性。测试：TestExecutionContextImpl#testCompleteExceptionallyAfterCompleteIsIgnored（红：HEAD 上 complete() 后 completeExceptionally() 仍改写 error，`expected: <null> but was: <NopException mock>`）、#testCompleteAfterCompleteExceptionallyIsIgnored（对称守护）、#testConcurrentCompleteAndCompleteExceptionally（500 轮双线程竞态，断言回调恰触发一次且参数与终态一致；HEAD 红：`expected: <true> but was: <false>`，即回调收到 null 而 error 已设）。保留既有语义：落败的 complete() 在 fireBeforeComplete 读到已设 error 时按原逻辑抛出（测试对 f.get() 容忍 ExecutionException）。

### [P2] DefaultVirtualFileSystem.refresh() 先关闭旧 ZipFile 再重建，窗口期内读线程访问已关闭的 zip

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/DefaultVirtualFileSystem.java:70`
- **维度**: D2/D3
- **证据**:
```java
public synchronized void refresh(boolean refreshDepends) {
    IoHelper.safeCloseAll(zipFiles);   // L71：先关闭旧 store 持有的所有 ZipFile
    zipFiles = null;
    this.buildResourceStore();         // L74：重建耗时期间 deltaResourceStore 仍指向旧 store（volatile）
}
```
- **现状**: 注释（L39）表明设计意图是"读路径无锁，通过 volatile 引用替换保证安全发布"。但 refresh 顺序是"先关旧资源、后发布新 store"，在 `buildResourceStore()` 执行期间（读 VfsConfig、构建 DeltaResourceStore、扫描 zip），读线程通过 volatile 读到**旧** deltaResourceStore，其 ZipResourceStore 内的 ZipEntryResource 调用 `zipFile.getInputStream(entry)` 会抛 `IllegalStateException: zip file closed`。
- **风险**: 开发态热刷新（reinitialize/refresh）期间并发的模型加载请求随机失败；失败以未受控的 IllegalStateException 形式抛出而非资源不存在。
- **建议**: 调整顺序为"先 buildResourceStore 构建新 store（打开新 ZipFile）→ 赋值 volatile 字段 → 最后关闭旧 zipFiles"，即先发布后关闭。
- **误报排除**: 已确认 `zipFiles`/`deltaResourceStore` 的 volatile 语义（L40-41）、`buildResourceStore` 的耗时操作内容（L77-82），以及 ZipEntryResource.getInputStream（ZipEntryResource.java:98-104）直接使用构造时注入的 ZipFile；关闭顺序问题无法靠 volatile 消除。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。refresh 改为先捕获旧 zipFiles 引用、执行 buildResourceStore()（构建并经 volatile 发布新 store + 新 zipFiles）、最后关闭旧 zip——与 destroy() 的"先发布后关闭"序一致。测试：TestDefaultVirtualFileSystemRefresh#testRefreshClosesOldZipOnlyAfterRebuild（反射注入真实 ZipFile，匿名子类在 buildResourceStore 期间探测旧 zip 是否已关闭，并断言 refresh 结束后旧 zip 已关闭）。红验证：HEAD 上 `old zip files must not be closed while rebuilding ==> expected: <false> but was: <true>`；修复后绿。

### [P2] DefaultDirectedGraph.removeVertex/removeAllVertices 不清理全局 edges 集合，edgeSet/toGraphBean 返回悬空边

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/model/graph/DefaultDirectedGraph.java:231`
- **维度**: D1/D8
- **证据**:
```java
public boolean removeVertex(V v) {
    final VertexInfo<V, E> info = vertexMap.remove(v);
    if (info == null) {
        return false;
    }
    _removeEdges(info, v);           // L237：只清邻居的 inEdges/outEdges 列表
    return true;
}

private void _removeEdges(VertexInfo<V, E> info, V v) {
    for (E edge : info.inEdges) {
        final V source = edge.getSource();
        final VertexInfo<V, E> sourceInfo = vertexMap.get(source);
        sourceInfo.outEdges.removeIf(e -> e.getTarget().equals(v));
    }
    ...                              // 从不操作全局 this.edges
}
```
- **现状**: 全局 `edges`（L42，LinkedHashSet）只在 `addEdge`/`removeEdge` 中维护；`removeVertex`、`removeMinorityVertices`（L278-288）、`removeMajorityVertices`（L294-299）删除顶点时都不从 `edges` 移除关联边。
- **风险**: 删除顶点后 `edgeSet()`（L128）、`toGraphBean`（L85-100）、`toString`（L114-117）、`SpanningTreeFinder`/`GraphvizHelper`（按 edgeSet 遍历，见 SpanningTreeFinder.java:16）会看到端点已不存在的悬空边，输出错误的图快照。属于公开 API 的内部状态不一致（契约：edges 与 vertexMap 应同步）。
- **建议**: `_removeEdges` 中同步 `edges.removeAll(info.inEdges)` / `edges.removeAll(info.outEdges)`（需先收集，避免并发修改）。
- **误报排除**: 已通读全文件并 grep 全仓：当前仓库主代码无 removeVertex 调用方（仅接口定义与实现），故评 P2 而非 P1；但 edgeSet/toGraphBean 是被 SpanningTreeFinder、GraphvizHelper、ReversedDirectedGraphView 实际消费的路径，一旦有调用方使用 removeVertex 即产生错误数据。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`_removeEdges` 末尾增加 `edges.removeAll(info.inEdges)` / `edges.removeAll(info.outEdges)`（覆盖 removeVertex 与 removeMinorityVertices 路径）；`removeMajorityVertices` 增加 `edges.removeIf(端点在删除集合内)`。测试：TestDirectedGraph#testRemoveVertexCleansGlobalEdgeSet（含 toGraphBean 断言）、#testRemoveMinorityVerticesCleansGlobalEdgeSet（6 顶点删 2 走 minority 路径，断言仅剩 c->d）、#testRemoveMajorityVerticesCleansGlobalEdgeSet。红验证：HEAD 上三测试分别失败（悬空边残留 / `expected: <1> but was: <5>` / isEmpty=false）；修复后绿。附带新发现（未修，见文末超范围记录）：removeVertex 对自环边会 NPE。

### [P2] JsonMerger.mergeMap 修改入参 mapB 且部分分支直接返回 mapB 引用，存在缓存对象污染风险

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/delta/JsonMerger.java:73`
- **维度**: D8/D1
- **证据**:
```java
public Map<String, Object> mergeMap(Map<String, Object> mapA, Map<String, Object> mapB) {
    if (mapB.isEmpty())
        return mapA;

    mapB.remove(CoreConstants.ATTR_X_VIRTUAL);      // L73：副作用：修改入参
    mapB.remove(CoreConstants.ATTR_X_INHERIT);      // L74

    if (mapA.isEmpty() || shouldRemove(mapA) || shouldReplace(mapB))
        return mapB;                                // L77：直接返回入参引用
    if (shouldRemove(mapB)) {
        return mapB;                                // L80
    }
```
- **现状**: `merge` 语义上是纯函数（调用方 JsonExtender/DeltaMergeHelper 均把结果当新对象使用），但 mergeMap 对 mapB 做结构性修改（remove 两个属性），且在 3 个分支直接返回 mapB 本身。调用方 JsonExtender.xtendMap（JsonExtender.java:89-91）拿到返回值后会 `ret.put(ATTR_X_OVERRIDE, OVERRIDE_REPLACE)` 继续修改。
- **风险**: nop-core 内默认 loader（DeltaJsonLoader.loadFromResource → JsonTool.parseBeanFromResource）每次解析新对象，当前路径安全；但 `DeltaJsonOptions.getResourceLoader()`（DeltaJsonLoader.java:81-82）允许上层传入带缓存的自定义 loader（nop-xlang 的 XJsonLoader 即通过 instance().loadDeltaBean 使用），若 loader 返回缓存对象，merge 会破坏缓存中的 x:virtual/x:inherit 标记并被后续 put 污染，导致差量合并结果跨请求串扰。
- **建议**: mergeMap 开头克隆 mapB（或至少把 remove 操作应用到克隆副本）；或在与调用方协商后在 JavaDoc 中明确"merge 拥有 mapB 所有权"的契约。
- **误报排除**: 已通读 JsonMerger 全文、JsonExtender 全文、DeltaJsonLoader 全文，确认默认路径每次新建对象（无共享）、自定义 loader 路径存在共享可能；JsonExtender L90-91 的 `ret.put` 确实会在 `mapA.isEmpty()` 分支落到原 mapB 上。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复，结果内容语义不变。合并分支（JObject/LinkedHashMap）改为迭代 mapB 时跳过 x:virtual/x:inherit（此前是先从 mapB 物理删除）；三个直接返回 mapB 的分支改为返回剥离标记后的浅拷贝 `copyWithoutMarkers`（JObject 经 `new JObject(loc, LinkedHashMap<String,ValueWithLocation>)` 保真克隆，保留 location/comment；普通 Map 用 LinkedHashMap）——入参不再被修改，返回值不再与入参共享引用，自定义缓存 loader 场景的污染路径闭合。shouldRemove/shouldReplace 只读 ATTR_X_OVERRIDE，与标记剥离顺序无关，判定行为不变。测试：TestJsonMerger#testMergeMapDoesNotMutateInput（红：HEAD 上 mapB 的 x:virtual/x:inherit 被删，`expected: <true> but was: <null>`）、#testMergeMapReturnsCopyInsteadOfInput（红：assertNotSame 失败，HEAD 直接返回 mapB 本体）、#testMergeMapNestedMergeContent（守护嵌套 map/list 合并结果内容与 HEAD 一致）。

### [P2] CsvRecordInput/CsvRecordOutput 构造函数异常路径泄漏已打开的 Reader/Writer

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/record/csv/CsvRecordInput.java:66`
- **维度**: D2
- **证据**:
```java
// CsvRecordInput L66-73
this.reader = ResourceHelper.toReader(resource, encoding, supportZip);   // 已打开文件流
try {
    CSVParser csvReader = CSVParser.parse(reader, format.withHeader());  // 解析头部
    ...
} catch (Exception e) {
    throw NopException.adapt(e);   // 构造失败，this.reader 无人可关
}

// CsvRecordOutput L41-46 同样模式
Writer out = ResourceHelper.toWriter(resource, encoding, supportZip);
try {
    this.writer = new CSVPrinter(out, format);
} catch (Exception e) {
    throw NopException.adapt(e);   // out 泄漏
}
```
- **现状**: 构造函数中先打开流，后续初始化抛异常时构造失败，调用方拿不到对象引用，无法关闭流，文件句柄泄漏直到 GC。
- **风险**: CSV 文件头部损坏/非法（`CSVParser.parse` 抛 IOException/IllegalArgumentException）或 CSVPrinter 初始化失败时泄漏文件句柄；批量导入场景反复遇到损坏文件会累积句柄、最终 `too many open files`。
- **建议**: catch 块中先 `IoHelper.safeClose(reader/out)` 再抛出；或改为静态工厂方法持有本地引用并 try-with-resources。
- **误报排除**: 已确认两个构造函数的完整代码（CsvRecordInput.java:61-89、CsvRecordOutput.java:39-55），异常路径确实无任何 close；对照同包 JsonlResourceRecordIO / ResourceHelper 其他方法（均有 finally close）确认这是遗漏而非约定。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。两个资源形态构造函数的 catch 块中先 `IoHelper.safeClose(reader/out)` 再 `NopException.adapt(e)` 重抛（外部 Reader 形态构造函数不涉及资源打开，维持不变）。测试：TestCsvRecordIoLeak#testInputClosesReaderOnParseError（DelegateResource 返回 read 即抛 IOException 的 Reader；红：HEAD 上 `reader should be closed ... expected: <true> but was: <false>`）、#testOutputClosesWriterOnPrinterError（null format 使 CSVPrinter 构造失败；红同形态）。注：本仓解析到 commons-csv 1.8，其构造失败抛 IllegalArgumentException，经 `NopException.adapt` 对 RuntimeException 原样重抛，故 output 测试断言 RuntimeException（NopException 为其子类），input 路径 IOException 被 wrap 为 NopException。

### [P2] NioFileWatchService.checkChange 捕获 IOException 后 watcher 循环整体退出，监控静默失效

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/watch/NioFileWatchService.java:132`
- **维度**: D4
- **证据**:
```java
private void checkChange() {
    try {
        WatchService watchService = this.getWatchService();
        do {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                ...
            } catch (InterruptedException e2) { ... }
        } while (isActive());
    } catch (IOException e) {
        LOG.error("nop.core.resource.watch.file-watcher-fail", e);   // L133：仅记日志，循环终止
    }
}
```
- **现状**: do-while 内部 `register()`（L93，ENTRY_CREATE 时递归注册新子目录）声明 throws IOException，任一次注册失败（如新目录瞬间被删除、目录权限变化）会跳出整个 do-while，外层 catch 只打日志，watcher 线程结束且服务状态仍为 active。
- **风险**: 一次瞬时 IO 异常导致所有后续文件变更监控永久失效，且无任何状态暴露（isStarted 仍为 true），开发态增量编译/热更新静默停摆。
- **建议**: 将 register 失降级为单目录跳过（catch 后 continue），或在 catch 后安排重建/重启循环。
- **误报排除**: 已通读全文件，确认 register 在 L93（watcher 线程内调用）抛出的 IOException 会传播出 do-while 到 L132 的外层 catch；`isActive()` 来自 LifeCycleSupport，服务未 stop。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。watcher 线程内递归 register（ENTRY_CREATE 新子目录）包以 try/catch(IOException)，失败记 error 日志（nop.core.resource.watch.register-subdir-fail）后继续处理后续事件——单目录注册失败降级为跳过该目录。原外层 `catch (IOException)` 因唯一异常源已被局部捕获成为不可达代码（编译错误），随结构调整移除，watcher 循环不再有任何 IOException 可中断路径。测试：TestNioFileWatchService#testRegisterFailureDoesNotKillWatcher（测试子类覆写 register 使第 2 次——即 watcher 线程对新子目录的注册——抛 IOException，latch 确认该事件已被处理后创建 b.txt，断言事件仍被投递）。红验证：初版测试因 macOS 轮询式 WatchService 事件乱序在 HEAD 侥幸通过，改为等待 mock register 被调用后再创建 b.txt 后，HEAD 红：`watcher loop should survive register failure ... expected: <true> but was: <false>`（12s 超时，watcher 已死）；修复后绿。

### [P3] ResourceComponentManager.resolveModelLoader 对 "resolve-" 前缀路径未校验冒号，与同文件其他方法行为不一致

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:173`
- **维度**: D1（边界）
- **证据**:
```java
if (path.startsWith(ResourceConstants.RESOLVE_PREFIX)) {   // RESOLVE_PREFIX = "resolve-"（无冒号）
    int pos = path.indexOf(':');
    String subName = path.substring(pos + 1);              // pos=-1 时 subName = 整个 path
```
- **现状**: `RESOLVE_PREFIX` 是 `"resolve-"`（ResourceConstants.java:21），不是 `"resolve:"`。`getModelConfigByModelPath`（L452-458）对同一前缀路径检查了 `pos < 0` 并抛 `ERR_COMPONENT_INVALID_MODEL_PATH`，而 `resolveModelLoader` 未检查，畸形路径（`resolve-xxx` 无冒号）会把整个字符串当作 subName 拼进目录，最终抛出误导性的 `no-loader-for-path` IllegalArgumentException（L109，还是字符串拼接式错误码而非 NopException）。
- **风险**: 仅影响畸形内部路径的诊断体验，不会错误加载数据。
- **建议**: 与 L454 一致补充 `pos < 0` 检查抛 ERR_COMPONENT_INVALID_MODEL_PATH；顺带将 L109 的 `IllegalArgumentException("nop.err...:" + path)` 改为带 ErrorCode 的 NopException。
- **误报排除**: 已确认 ResourceConstants.RESOLVE_PREFIX 的字面值、getModelConfigByModelPath 的对照实现（L452-458），以及调用链上 loadComponentModel → findModelTypeFromPath → requireModelConfigByModelPath 已保证 config 非 null（L175 的 `config.getResolveInDir()` 不会 NPE）。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。resolveModelLoader 补充 `pos < 0` 检查抛 `ERR_COMPONENT_INVALID_MODEL_PATH`（与 getModelConfigByModelPath L452-458 一致）；顺带将 L109 的字符串拼接式 `IllegalArgumentException("nop.err.resource.no-loader-for-path:"+path)` 改为新增错误码 `ERR_COMPONENT_NO_LOADER_FOR_PATH`（nop.err.core.component.no-loader-for-path）的 NopException——核对 nop-core 错误码无 i18n bundle 惯例，新码沿用 CoreErrors 同文件既有 define 风格。测试：TestResourceComponentManager#testResolveModelLoaderRejectsPathWithoutColon（红：HEAD 上以 NPE 形态失败——`config = modelTypeConfigs.get(modelType)` 为 null 后直接解引用，`expected NopException but was NullPointerException`；修复后绿）。L109 转换无可达测试路径（需 VFS fixture 构造 resolve- 无 default loader 场景），以仓内无 IllegalArgumentException 捕获方 + 全量编译验证。

### [P3] CoreInitialization.reinitialize 在未初始化时调用不会回写 initializers，后续 destroy 被整体跳过

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:216`
- **维度**: D1（边界）
- **证据**:
```java
List<ICoreInitializer> list = initializers;
if (list == null) {
    list = loadInitializers();   // L218：局部变量，未赋值回 initializers
}
...
initializer.initialize();
initializationLevel = initializer.order();
```
- **现状**: `reinitialize` 在 `initializers == null`（从未 initialize/已 destroy）时加载到局部变量执行，静态字段 `initializers` 保持 null。之后调用 `destroy()`（L161-163：`if (list == null) return;`）会直接返回，跳过所有 initializer 的 destroy 与 cleanups；下次 `initializeTo` 会重新 loadInitializers 并对可能已初始化的 initializer 重复 initialize。
- **风险**: 边界使用序列（destroy → reinitialize → destroy）下资源清理缺失。grep 全仓未发现 reinitialize 的主代码调用方，现实触发概率低。
- **建议**: `list = loadInitializers()` 后同步 `initializers = list`。
- **误报排除**: 已通读 CoreInitialization 全文并核对 destroy（L158-206）对 `initializers == null` 的提前返回逻辑；确认 initializeTo（L94-95）有 `if (initializers == null) initializers = loadInitializers()` 的回写，reinitialize 遗漏了同样处理。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`loadInitializers()` 后回写 `initializers = list`（镜像 initializeTo L94-95 的处理）。测试：TestCoreInitialization#testReinitializeWritesBackInitializers（destroy→reinitialize→反射断言 initializers 非空→destroy 复位验证）。红验证：HEAD 上 `reinitialize should write back loaded initializers ==> expected: not <null>`；修复后绿。

### [P3] TopologicalOrderIterator.containsCycle/findCycles 在 allowLoop=true 的实例上永远返回"无环"

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/model/graph/TopologicalOrderIterator.java:126`
- **维度**: D1（语义陷阱）
- **证据**:
```java
public boolean containsCycle() {
    while (hasNext()) {          // hasNext: allowLoop=true 时 breakLoop() 会主动清空 countMap
        nextNoCycle();
    }
    return !countMap.isEmpty();  // 循环结束后 countMap 必空 → 恒 false
}
```
- **现状**: 默认构造器 `TopologicalOrderIterator(ITargetVertexView)` 以 allowLoop=true 创建（L53-55）。该模式下 `hasNext()` 内的 `breakLoop()`（L97-106）会把剩余节点逐个摘出放入 empties，`containsCycle()`/`findCycles()` 循环结束后 `countMap` 必为空，环检测恒返回"无环"。
- **风险**: 方法行为依赖构造参数的模式，allowLoop=true 实例上调用环检测得到错误结果。当前仓内实际调用方（CycleDetector.java:35、ITargetVertexView.findCycles）都显式以 allowLoop=false 构造，无现实触发。
- **建议**: 在 containsCycle/findCycles 内部直接 `new TopologicalOrderIterator(vertexSet 复用...)`（allowLoop=false），或文档标注仅 allowLoop=false 时有效。
- **误报排除**: 已通读全文件并 grep 全仓调用方，确认 CycleDetector 与测试均用 allowLoop=false 路径，故降为 P3。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。containsCycle/findCycles 的驱动条件由 `hasNext()`（allowLoop=true 时内部 breakLoop 主动摘空 countMap）改为 `!empties.isEmpty()`：只消费已度为 0 的节点，循环结束后 countMap 剩余即环上节点。allowLoop=false 时 hasNext() ≡ !empties.isEmpty()，既有调用方（CycleDetector 等）行为不变。测试：TestDirectedGraph#testContainsCycleWithAllowLoopIterator（默认 allowLoop=true 构造器 + a->b->a 环；红：`expected: <true> but was: <false>`，findCycles 恒空）；修复后绿（containsCycle=true、findCycles=2）。

### [P3] ResourceCacheEntry.deps/lastLoadTime 非 volatile，锁外读取存在可见性问题

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java:169`
- **维度**: D3
- **证据**:
```java
private ResourceDependencySet deps;        // L40：普通字段
private long lastLoadTime;                 // L42

public boolean isChanged() {               // L162：无锁调用
    ...
    if (deps == null) return true;
    return ResourceComponentManager.instance().isAnyDependsChange(deps.getDepends());
}

public boolean isRefreshEnabled(int refreshMinInterval) {   // L102：无锁读 lastLoadTime
    long now = CoreMetrics.currentTimeMillis();
    return now - lastLoadTime > refreshMinInterval;
}
```
- **现状**: `deps`/`lastLoadTime` 在 synchronized 的 `loadObject`（L215-224）中写入，但 `isChanged`/`isRefreshEnabled`/`getDeps` 均无锁读取，`object` 字段是 volatile 而 deps 不是。
- **风险**: 读线程可能长期看到 stale 的 deps（如 null → 恒判"已变化"触发多余重载）或 stale lastLoadTime（绕过 refresh 最小间隔限流）。功能上偏保守方向，不产生错误数据，且 getObject 主路径的 double-check 已用 volatile object 兜底。
- **建议**: 将两个字段声明为 volatile。
- **误报排除**: 已通读全文件梳理所有读写路径，确认写入仅发生在锁内、读取发生在锁外；评估其后果为"多余刷新/限流失效"而非数据错误。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`deps`、`lastLoadTime` 声明加 volatile（写入均在 synchronized loadObject 锁内，锁外读 isChanged/isRefreshEnabled/getDeps 由此获得可见性）。未做红验证：JMM 可见性问题无法构造确定性红测试（最坏形态为多余刷新/限流失效，报告已评估为非数据错误方向）；字段声明核对为全部读写路径。

### [P3] JsonTool.loadDeltaBean 是实例方法，与同类全静态 facade 风格不一致

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/JsonTool.java:187`
- **维度**: D8
- **证据**:
```java
public static <T> T loadBean(String path, Class<?> beanClass) { ... }   // L172：static

public <T> T loadDeltaBean(String path, Type targetType) {              // L187：实例方法（遗漏 static）
    return loadDeltaBeanFromResource(VirtualFileSystem.instance().getResource(path), targetType);
}
```
- **现状**: JsonTool 其余 30+ 个方法全部为 static facade，唯独 loadDeltaBean 是实例方法。调用方被迫写 `JsonTool.instance().loadDeltaBean(...)`（如 nop-xlang XJsonLoader.java:29），直接 `JsonTool.loadDeltaBean(path, type)` 编译错误。
- **风险**: 无运行时危害；API 一致性/易用性问题，且方法体不使用任何实例状态。
- **建议**: 若无二进制兼容顾虑，补充 static（同名实例方法可保留为废弃代理）。
- **误报排除**: 已核对全文件方法签名及仓内调用方式（grep `loadDeltaBean`），确认所有调用方都走 `JsonTool.instance()`，说明现状可用但风格漂移。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`loadDeltaBean(String, Type)` 补充 static（采纳报告方案；无法与实例方法同签名共存，故直接转换而非保留废弃代理）。可行性核对：JsonTool 为 facade 类不实现 IJsonTool，该方法不属于任何接口契约；IJsonTool 仅声明 (IResource, Type, DeltaJsonOptions) 形态；全仓无该 String 形态调用方（既有调用均为 resource 形态或经 instance() 走接口方法），无二进制兼容负担。未加测试：纯签名（API 形态）变化、无运行时行为差异（同文案类免测试），方法体一行未动。

### [P3] EvalScopeImpl.removeLocalValue 中 locations.remove 未与其他方法一致地加锁

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/EvalScopeImpl.java:230`
- **维度**: D3
- **证据**:
```java
public void removeLocalValue(String name) {
    variables.remove(name);
    if (ENABLE_EVAL_DEBUG) {
        if (locations != null)
            locations.remove(name);      // L234：setLocalValue/clear 都用 synchronized(locations)，此处没有
    }
}
```
- **现状**: `getLocalLocation`（L142-145）、`setLocalValue`（L214-216）、`clear`（L243-246）对 `locations` 的访问都在 `synchronized (locations)` 块内，唯独 removeLocalValue 的 remove 未同步。
- **风险**: 仅在 ENABLE_EVAL_DEBUG 开启且多线程共用 scope 时可能出现 HashMap 并发修改；调试模式限定，影响面小。
- **建议**: 补充 `synchronized (locations)`。
- **误报排除**: 已对照同文件其他三个操作 locations 的方法，确认它们的同步约定，此处为遗漏。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`locations.remove(name)` 包入 `synchronized (locations)`，与 setLocalValue/clear 对齐。未做红验证：ENABLE_EVAL_DEBUG 为 `CFG_EVAL_SCOPE_DEBUG_ENABLED.get()` 的接口常量（类加载期定值），测试无法在 JVM 内开启该路径，并发问题亦不可确定性构造。

### [P3] ResourceHelper 资源读写方法在高频路径打 INFO 日志

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/ResourceHelper.java:686`
- **维度**: D6
- **证据**:
```java
public static String readText(IResource resource, String encoding) {
    LOG.info("resource.readText:resource={},encoding={}", resource, encoding);   // L686
    ...
}
// 同类：readBytes L736、writeText L725-726、writeBytes L777、readProperties L805、
//      writeProperties L820、writeJson L859、readObject L903、writeObject L922、readState/writeState
```
- **现状**: 每次资源读写（包括经 ResourceComponentManager 缓存未命中后的加载）都输出一条 INFO 日志，readText 的 `resource` 参数 toString 还会构造完整路径。
- **风险**: 生产环境高频模型/配置加载产生大量低价值 INFO 日志，影响日志吞吐与可读性（readTextHeader 已改为 LOG.trace，L695，说明级别选择是有意识的）。
- **建议**: 将常规读写降为 LOG.debug，保留写操作或失败场景的 INFO。
- **误报排除**: 已统计该文件中 LOG.info 与 LOG.trace 的分布（readTextHeader/writeText 的 trace 变体），确认读写主路径均为 INFO。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（按报告建议的保守口径）。读路径 4 处 INFO 降为 DEBUG：readText(L684)、readBytes、readProperties、readObject；写路径（writeText/writeBytes/writeStream/writeProperties/writeJson/writeXml/writeObject/writeState/saveToStream）保留 INFO。未加测试：日志级别非行为契约、断言日志级别易碎，属文案类免测试。

### [P3] XNode 对 freeze(false) 父节点的子树变更抛 UnsupportedOperationException 而非预期的 NopException

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/xml/XNode.java:289`
- **维度**: D1（一致性）
- **证据**:
```java
public void freeze(boolean cascade) {
    ...
    this.flags |= FLAG_READ_ONLY;
    if (cascade) { /* 递归 freeze 子节点 */ }
    if (!frozen && children != EMPTY_CHILDREN) {
        children = Collections.unmodifiableList(children);   // L305：无论 cascade 都包裹只读
    }
}
```
- **现状**: `freeze(false)` 只设置自身 flag、不设置子节点 flag，但会把 children 替换为 unmodifiableList。此后对未 frozen 的子节点调用 `detach()`（L1669-1677：`checkNotReadOnly()` 检查的是子节点自身，通过；随后 `parent.getChildren().remove(this)`）或 `removeChild` 等抛 JDK 的 UnsupportedOperationException，而不是 `ERR_XNODE_IS_READONLY` 的 NopException。
- **风险**: 只读保护的最终效果一致（操作被拒绝），但异常类型与平台错误处理约定（NopException + ErrorCode）不符，错误诊断信息劣化。
- **建议**: `detach()` 入口增加 `parent.checkNotReadOnly()`（或 getParent().frozen() 检查），或 freeze(false) 不包裹 children 列表。
- **误报排除**: 已确认 detach 的 checkNotReadOnly 仅作用于 this（L1670），freeze 的 children 包裹不区分 cascade（L304-306）。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（采纳建议方案 a）。`detach()` 在 `this.checkNotReadOnly()` 之外增加 `parent.checkNotReadOnly()`：freeze(false) 下子节点自身未冻结、但父节点只读时，修改父节点 children 列表的操作以 `ERR_XNODE_IS_READONLY` 的 NopException 拒绝，异常类型回归平台约定。其余修改 children 的方法均为持有列表的节点自身实例方法、入口已有 checkNotReadOnly，无同类旁路。测试：TestXNode#testDetachFromNonCascadeFrozenParent（红：`expected NopException but was UnsupportedOperationException`；修复后绿且错误码匹配）。

### [P3] ZipResourceStore.build 构造异常时已打开的 ZipFile 泄漏

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/store/ZipResourceStore.java:38`
- **维度**: D2
- **证据**:
```java
public static ZipResourceStore build(File file, String basePath, String baseEntryPath) {
    try {
        return new ZipResourceStore(new ZipFile(file), baseEntryPath, basePath);
    } catch (IOException e) {
        throw NopException.adapt(e);   // 已打开的 ZipFile 无人关闭
    }
}
```
- **现状**: `new ZipFile(file)` 成功后若构造器内 `FileScanHelper.scanZip` 抛异常（损坏 zip、扫描中断），ZipFile 引用已丢失，句柄泄漏。
- **风险**: 批量加载损坏 zip 的场景累积文件句柄；概率低。
- **建议**: 构造器内 try-catch 并在异常时 close zipFile，或 build 中持有局部引用 catch 后关闭。
- **误报排除**: 已通读该类，确认 close() 只能由成功构造出的实例调用；scanZip 无自带关闭逻辑。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。build 局部持有 `ZipFile` 引用：`new ZipFile(file)` 的 IOException 路径维持 adapt（无句柄可漏）；ZipResourceStore 构造（scanZip 内 checkNormalVirtualPath 等运行时异常）失败时 `IoHelper.safeClose(zipFile)` 后原样重抛（保留原异常类型，多 catch 不可行——构造器未声明受检异常）。未做红验证：build 内部创建的 ZipFile 引用无外部观测途径（ZipFile 无 isOpen API、macOS 上句柄不阻止 unlink），close 时序无法从外部断言；同型缺陷 P2-8（CSV 构造泄漏）已有红测试覆盖该修复模式。

### [P3] 多个全局单例的静态实例字段延迟替换无 volatile/可见性保证

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:70`
- **维度**: D3
- **证据**:
```java
private static IResourceComponentManager _instance = new ResourceComponentManager(true);   // L70

public static void registerInstance(IResourceComponentManager instance) {
    _instance = instance;    // L77：普通静态字段写
}
```
- **现状**: 同类模式还出现在 `ResourceTenantManager._instance`（L46）、`JsonTool._instance`（L38）、`I18nMessageManager._instance`（L38）。`registerInstance` 通常由 IoC 初始化线程调用，业务线程随后通过 `instance()` 读取，字段均非 volatile，理论上存在可见性延迟。此外 `ResourceTenantManager.enabledTenantPaths/disabledTenantPaths`（L56-57）的懒初始化也是无同步的 check-then-act（结果幂等，最坏重复计算）。
- **风险**: 实际中 IoC 初始化与业务请求之间存在 happens-before（线程池提交等），可见性问题很难观测；后果也只是短暂读到旧实例/重复初始化，属于规范性风险。
- **建议**: 这些字段加 volatile（成本极低）。
- **误报排除**: 已核对四处字段的声明与写入点，确认均为非 volatile 普通静态字段；评估触发条件苛刻故仅列 P3。


> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。四处静态实例字段加 volatile：ResourceComponentManager._instance、ResourceTenantManager._instance、JsonTool._instance、I18nMessageManager._instance。未做红验证：可见性风险不可确定性构造（报告亦评估触发条件苛刻）；改动为字段声明级、无逻辑变化。ResourceTenantManager 的 enabledTenantPaths/disabledTenantPaths 懒初始化维持现状（幂等最坏重复计算，报告未列为必修项）。

---

## 补充说明（未列为发现的排查项）

- `ResourceHelper.resolveRelativeResource`（L281-283）用 `relativePath.contains("..")` 拒绝父路径，会误伤形如 `a..b.txt` 的文件名，但方向保守（宁可拒绝），不构成安全问题。
- `JsonSerializer.shouldInclude` 的 NON_EMPTY 分支不会对数组值误走 `Collection` 强转：`IGenericType.isCollectionLike` 默认实现为 `isListLike()||isSetLike()`（IGenericType.java:149-151），数组类型不满足（已核对 ContainerTypeData/GenericArrayTypeImpl）。
- `XNode.setAttr` 在 `attributes == EMPTY_ATTRS` 时执行 `attributes.remove(name)`（L715）不会抛异常：`Collections.emptyMap` 继承 AbstractMap，remove 不存在的 key 返回 null 而非抛 UnsupportedOperationException。
- 模式扫描未发现：共享 SimpleDateFormat、`@Inject private` 字段、Spring `@Value`、bare `new RuntimeException`、`printStackTrace`（仅注释残留）、`new Random`、Runtime.exec。错误处理整体符合平台两级异常规范（NopException + ErrorCode + param）。
- `DefaultTaskExecutionQueue`（execution 包）的任务状态清理与指标更新路径已逐一核对，`addTaskIfAbsent` 的 computeIfAbsent 内提交任务依赖 executor 异步执行（DefaultThreadPoolExecutor），无死锁路径。

## 超条目处置：FilterBeanToSQLTransformer contains 拼 ilike 同源问题（归本单元的遗留 follow-up）

> 来源：plan346 中 nop-orm-eql 单元记录的待跟进项「nop-core FilterBeanToSQLTransformer contains 拼 ilike 同源问题（归 nop-core 单元记录）」。

> **处置（fix-ai-check 分支，2026-08-25）**: 复查非问题（已被 eql 侧修复覆盖，nop-core 自身无独立缺陷）。证据链均经 live code 核实：

> 1. 产出形态：`FilterBeanToSQLTransformer.visitCompareOp` 对 `icontains` 生成 `ilike` SQL 文本 + `.param("%"+value+"%")` 参数绑定值（FilterBeanToSQLTransformer.java:167-168，valueName 变体 L119-120）；`contains` 生成 `like`（L163-164/L115-116）。nop-core 不感知 SQL 方言，产出的是待翻译的 EQL 文本而非终态 SQL。
> 2. 消费链路统一经 EQL 编译层做方言翻译：仓内仅有的两个消费方——nop-orm `FilterSqlHelper.buildFilterSql`（`tool.compileSql("filter", sb.getText(), ...)`）与 `DaoQueryHelper.appendFilter`（产出 SQL 经 `orm().findPage/findLong` 等执行，`JdbcQueryExecutor.executeQuery/executeUpdate/executeStatement` 入口一律先 `env.compileSql(name, eql.getText(), ...)`）——都把文本送入 EQL 编译器。`ilike` 关键字在文法中独立成产生式，经 `AstToSqlGenerator.visitSqlLikeExpr`（nop-orm-eql AstToSqlGenerator.java:456-476）按方言翻译：方言注册了 ilike 函数则用模板，未注册则抛 `ERR_EQL_NOT_SUPPORT_ILIKE`——即 check2 nop-orm-eql 单元 2026-08-24 已完成的修复（原为静默降级为大小写敏感 like，live code 已核实为现行抛错语义）。本路径"contains/icontains 在不支持方言上的语义降级"随之消失（变为显式报错），无需也不应在 nop-core 内复制方言逻辑。
> 3. 转义方面：值一律经 `.param()` 绑定，无 SQL 注入；值中 `%`/`_` 通配符透传是平台 like 语义的统一惯例（eql 层 `SqlLikeExpr` 对值同样不做转义），非 nop-core 特有缺陷；如需收紧属全局 like-escape 设计决策，超出本条目范围。

## 处置期间的超范围新发现（未修，留待后续）

- `DefaultDirectedGraph.removeVertex` 对自环边（v→v）会 NPE：`removeVertex` 先执行 `vertexMap.remove(v)`，`_removeEdges` 内 `vertexMap.get(edge.getSource()==v)` 返回 null 后直接解引用 `sourceInfo.outEdges`。`removeMinorityVertices` 先 `_removeEdges` 后删 map、不受影响。不在本次任何发现条目范围内，未顺带修复（避免无回归测试的扩面改动），建议后续单独立项处理。
