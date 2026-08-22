# kernel-small 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-kernel/{nop-codegen,nop-javac,nop-dataset,nop-antlr4,nop-markdown,nop-record-mapping,nop-kernel-cli}
- 文件数: 约 197（src/main/java，实际统计：codegen 41、javac 14、dataset 59、antlr4-common 9、antlr4-tool 20、markdown 25、record-mapping 24（含 4 个 `_gen` 产物）、kernel-cli 5）
- 覆盖范围声明: 全部 197 个主代码文件均经过文件级浏览与结构确认；其中约 60 个核心文件逐行深读（codegen 生成链路与 javac 桥接、javac 全部编译器实现、dataset 记录输入/元数据/映射核心、record-mapping 映射引擎与 Markdown 双向解析/生成、markdown 解析器核心、antlr4-common 全部、kernel-cli 全部）；其余文件经统一 grep 模式扫描（空 catch、bare RuntimeException、printStackTrace、synchronized、可变 static 集合、字符串下标运算 substring/indexOf/charAt、@Inject/@Value、IllegalArgumentException/IllegalStateException、路径拼接），命中点均 Read 上下文验证。`_gen` 下 4 个生成文件仅确认生成物身份，未逐行审。测试代码与 target/ 不在范围。注意：XCodeGenerator 的文件写出实现实际位于其父类 `TemplateFileGenerator`（nop-core 模块），不在本检查单元内，本报告仅追踪到模块边界（调用方传参、targetRootPath 计算无越权路径拼接）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 2 |
| P2 | 8 |
| P3 | 6 |

## 发现列表

### [P0] BaseRecordInput.next() off-by-one：首条记录被跳过、末次访问抛 IndexOutOfBoundsException

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/record/impl/BaseRecordInput.java:66-73`
- **维度**: D1
- **证据**:
```java
@Override
public boolean hasNext() {
    return readCount < records.size();
}

@Override
public T next() {
    if (!hasNext())
        throw new NoSuchElementException();

    readCount++;
    return records.get(readCount);
}
```
- **现状**: `next()` 先自增 `readCount` 再取 `records.get(readCount)`。以 `hasNext()` 的语义（`readCount` 为已读条数）为基准，首次 `next()` 返回的是第 2 个元素；当 `readCount == size-1` 时 `hasNext()` 为 true，但 `next()` 自增到 `size` 后 `records.get(size)` 越界。单元素列表第一次调用 `next()` 即抛 `IndexOutOfBoundsException`；n 元素列表完整迭代返回元素 1..n-1 后崩溃，元素 0 永远不可读。
- **风险**: 该类是列表型记录输入的公共基类，现实触发路径已确认：
  - `nop-dataset/.../impl/DataSetCacheHelper.java:38-46`（`toDataSet` 构造 `BaseDataSet`），被 `nop-persistence/nop-dao/.../JdbcTemplateImpl.java:315,420` 用于查询结果缓存命中路径——`IRecordInput` 的 `readBatch/readAll/forEach/stream` 缺省实现（`RecordInputImpls.defaultReadBatch` 等）全部经 `hasNext()/next()` 迭代，缓存命中后回调一旦完整消费数据集必然抛越界异常（或静默丢失首行）；
  - `nop-kernel/nop-core/.../ListRecordIO.java:20`（`new BaseRecordInput<>(records, null)`）；
  - `BaseDataSet.buildFrom` / `TransformedDataSet.detach()` / `JdbcDataSet.detach()`（nop-dao:554）产出的 `BaseDataSet` 同样继承该 `next()`。
  - nop-dataset 模块没有任何 src/test 目录，该缺陷无测试覆盖。
- **建议**: 改为 `T record = records.get(readCount); readCount++; return record;`，并补充单元素/多元素/空列表的迭代回归测试。
- **误报排除**: 已核对 `BaseDataSet` 未覆写 `next()/hasNext()`；`skip()`、`getReadCount()`、`getTotalCount()` 均按"已读条数"语义实现，确认 `next()` 与其余方法不一致是实现错误而非 1-based 约定；`ListRecordIO` 直接把原始 list 传入构造器，无索引 0 占位逻辑。

---
> **处置（fix-ai-check 分支，2026-08-22）**: 确认为真实缺陷，已修复。`BaseRecordInput.next()` 改为先 `T record = records.get(readCount)` 再 `readCount++`，与 `hasNext()`/`getReadCount()` 的"已读条数"语义对齐；同时在 `nop-dataset` 新建 `src/test/java` 并在 pom 按模块惯例补充 test-scope `junit-jupiter` 依赖。测试：`nop-dataset` `TestBaseRecordInput#testSingleElement`（修复前首次 `next()` 即抛 `IndexOutOfBoundsException: Index: 1 Size: 1`）、`#testMultiElementIteration`（修复前元素 0 被跳过、完整迭代末次抛 `ArrayIndexOutOfBoundsException`）、`#testReadBatchDefaultPath`（修复前 `readBatch(2)` 返回 `[b, c]` 而非 `[a, b]`）、`#testReadAllDefaultPath`（修复前 3 元素列表 `readAll()` 末次抛越界）、`#testEmptyList`（空列表行为不受影响，修复前后均通过）。修复后 `./mvnw test -pl nop-kernel/nop-dataset` 5 个测试全部通过。

### [P1] MarkdownDocumentParser.parseFromText 对空/纯空白文档抛 NPE

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/simple/MarkdownDocumentParser.java:40-41`（根因在 107-110）
- **维度**: D1
- **证据**:
```java
MarkdownSection section = parseRootSection(sc);   // line 40
section.forEachSection(this::normalizeSectionContent); // line 41

public MarkdownSection parseRootSection(TextScanner sc) {
    sc.skipBlank();
    if (sc.isEnd())
        return null;              // 空/纯空白文本返回 null
```
- **现状**: 文本为空或仅空白时 `parseRootSection` 返回 null，紧接着第 41 行无条件调用 `section.forEachSection(...)` 抛 NullPointerException。
- **风险**: `MarkdownTool.instance().parseFromResource/parseFromText` 是平台加载 `.md` 文档（含 `MarkdownDslResourceLoader` 解析 md 型 DSL）的公共入口，一个空的 `.md` 文件（现实中常见的占位文件）即触发 NPE，且异常无文件位置上下文，难以定位。
- **建议**: `parseRootSection` 为 null 时构造空 `MarkdownSection`（或提前返回空 `MarkdownDocument`）。
- **误报排除**: 已确认上游 `DefaultMarkdownTool.parseFromResource`/`AbstractResourceParser` 无空文本守卫；`DefaultMarkdownTool.loadChildSections` 中大量 `getRootSection() != null` 判断说明调用方预期 rootSection 可为 null，第 41 行的裸调用与该预期矛盾。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`parseRootSection` 返回 null 时构造空 `MarkdownSection` 再走 `forEachSection`（空节点的 forEach 为无害空遍历），front-matter-only 的占位文档同样覆盖。测试：`MarkdownDocumentParserTest#testEmptyTextDoesNotThrowNpe`/`#testBlankTextDoesNotThrowNpe`/`#testFrontMatterOnlyDocDoesNotThrowNpe`（红验证：修复前空文本/纯空白/仅 front-matter 三种输入均裸 NPE）。

### [P1] JdkJavaCompiler.ClassLoaderImpl.findClass 未找到类时返回 null，违反 ClassLoader 契约

- **文件**: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/JdkJavaCompiler.java:133-142`
- **维度**: D1/D8
- **证据**:
```java
@Override
protected Class<?> findClass(final String className) throws ClassNotFoundException {
    final ByteArrayOutputStream bos = byteStreams.get(className);
    if (bos == null) {
        return null;   // 契约要求抛 ClassNotFoundException
    }
    final byte[] b = bos.toByteArray();
    return super.defineClass(className, b, 0, b.length);
}
```
- **现状**: `ClassLoader.findClass` 的契约是找不到类时抛 `ClassNotFoundException`。此处返回 null 会导致 `loadClass` 向调用方返回 null（父加载器未命中时），`JavaCompileResult.getGeneratedClass`（JavaCompileResult.java:54-60）及所有 `Class.forName`/反射探测方得到的是 NPE 而非可捕获的 CNFE。
- **风险**: 该编译器被 `nop-codegen/.../GenAopProxy.java:88-95` 用于 AOP 代理编译，产物 ClassLoader 交由后续代码查询类（包括探测性查询生成集合之外、父加载器也没有的类名，如可选依赖），届时以 NPE 形态崩溃且丢失"类不存在"语义。
- **建议**: `bos == null` 时改为 `throw new ClassNotFoundException(className)`。
- **误报排除**: 已核对 `JavaCompileResult.getGeneratedClass` 捕获的是 `Exception`（NPE 也会被包装成 NopException，但错误信息退化为 NPE 堆栈而非"类不存在"）；父类 `SecureClassLoader` 无其他 findClass 兜底。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`bos == null` 时改抛 `ClassNotFoundException(className)`，符合 ClassLoader 契约；探测性查询（父加载器也没有的类名）现在得到可捕获的 CNFE 而非 null/NPE。测试：`TestJavaCompileTool#testLoadMissingGeneratedClassThrowsCNFE`（断言 getGeneratedClass 包装出的 NopException 的 cause 是 ClassNotFoundException，且已生成类正常加载）。

### [P2] GenAopProxy 泄漏未关闭的 URLClassLoader

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/GenAopProxy.java:55-62`
- **维度**: D2
- **证据**:
```java
ClassLoader buildExtClassLoader(File classesDir) {
    URLClassLoader classLoader = new URLClassLoader(
            new URL[]{FileHelper.toURL(classesDir)}, ClassHelper.getDefaultClassLoader());
    return classLoader;
}

public void generate(File classesDir, File sourceDir) {
    ...
    ClassLoader classLoader = buildExtClassLoader(classesDir);   // 从未 close
```
- **现状**: `generate()` 创建的 `URLClassLoader` 全程未关闭。`CodeGenTask.genAopProxy`（CodeGenTask.java:207-220）对每个工程执行两次（main + test），多模块构建下逐模块累积。
- **风险**: URLClassLoader 打开的目录/JAR 句柄在 Windows 上会锁定 `target/classes` 目录，影响后续 clean/覆盖操作；长期运行的进程内则累积元空间与句柄。
- **建议**: 用 try-with-resources 包裹类加载阶段（类名收集与源码生成在 loader 作用域内完成即可释放）。
- **误报排除**: 已全文核对 `generate()` 及其调用链（`CodeGenTask.genAopProxy` → `execute`）无 close 调用；类对象仅在方法内使用，无逃逸到返回值，关闭安全。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。类扫描阶段包进 try-with-resources（`buildExtClassLoader` 返回类型收窄为 `URLClassLoader`），编译阶段在 loader 作用域外保持原逻辑；类对象无逃逸，关闭安全。免新增测试：纯资源生命周期重构，无数值行为语义，生成链路由既有 `TestXCodeGenerator`/`TestUseApprovalCodegen` 覆盖（14 tests 绿）。

### [P2] JdkJavaCompiler 每次编译创建的 JavaFileManager 从不关闭

- **文件**: `nop-kernel/nop-javac/src/main/java/io/nop/javac/jdk/JdkJavaCompiler.java:108-113,163-191`
- **维度**: D2
- **证据**:
```java
JavaFileManager fileManager = createFileManager(compiler, resultClassLoader);
final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, files);
JavaCompileResult result = new JavaCompileResult(task.call(), diagnostics, resultClassLoader);
return result;   // fileManager 未 close
```
- **现状**: `compiler.getStandardFileManager(...)` 创建的 StandardJavaFileManager（经 ForwardingJavaFileManager 包装）在 `task.call()` 后未关闭。javax.tools 约定调用方提供的 fileManager 由调用方负责生命周期；javac 会经该 manager 打开 classpath 上的 JAR 并缓存（JavacFileManager 内部容器缓存）。
- **风险**: 每次动态编译泄漏一个 file manager 及其打开的 JAR 句柄；在 Windows 上锁定依赖 JAR，在重复编译场景（GenAopProxy 按模块调用）累积句柄。
- **建议**: `task.call()` 结束后（无论成败）`fileManager.close()`，注意需在读取 diagnostics 与生成类字节之后执行。
- **误报排除**: 已核对 `JavaCompileResult` 只持有 ClassLoaderImpl 与 diagnostics，不持有 fileManager；模块内无任何 close 补偿逻辑。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`task.call()` 后 finally 中 `fileManager.close()`（失败仅 WARN 不影响诊断信息读取）；ClassLoaderImpl 只持有内存字节流、diagnostics 在构造 JavaCompileResult 时已收集，均不受关闭影响。免新增测试：资源生命周期修复无数值行为语义，编译功能由 `TestJavaCompileTool#testLoadMissingGeneratedClassThrowsCNFE`（含真实编译成功路径断言）覆盖。

### [P2] MarkdownTableHelper.parseMappingTable 用表头名作 key，逐行覆盖，只保留最后一行数据

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/utils/MarkdownTableHelper.java:111-126`
- **维度**: D1
- **证据**:
```java
String sourceField = table.getCellText(0, 0);
String targetField = table.getCellText(0, 1);

for (int i = 1, n = table.getRowCount(); i < n; i++) {
    IRowView row = table.getRow(i);
    String source = row.getCellText(0);
    String target = row.getCellText(1);
    map.put(sourceField, source);   // key 是表头，不是 source
    map.put(targetField, target);   // 每轮覆盖，最终只剩最后一行
}
```
- **现状**: 循环内以表头单元格文本为 key 写入，每行数据互相覆盖，结果 map 恒为 2 个 entry（各列最后一行的值），n-1 行数据丢失。意图显然是 `map.put(source, target)`。
- **风险**: 公开工具方法（`MarkdownHelper.parseMappingTable` 转发）产出错误数据。当前仓内无调用方（已全仓 grep），属潜伏地雷；一旦被用于"值映射表"解析即产生静默数据错误。
- **建议**: 改为 `map.put(source, target)`；补一个多行映射表的单元测试。
- **误报排除**: 已核对 `MarkdownHelper.parseMappingTable`（MarkdownHelper.java:259-261）仅为透传，无二次加工；仓内（含模板/xgen）无其他调用点。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。改为 `map.put(source, target)`（按审计建议），删除随之无用的表头变量读取。测试：`TestMarkdownTableHelper#testParseMappingTableUsesRowDataAsKey`（红验证：修复前 map 恒为 2 个 entry 且值为最后一行 `{source:b, target:2}`）。

### [P2] IDataSetMeta.rename()/projectWithRename() 语义失效：新列名被丢弃，ProjectDataSetMeta 路径直接抛错

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/impl/BaseDataSetMeta.java:100-109`；`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/impl/ProjectDataSetMeta.java:121-124`；`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/IDataSetMeta.java:63-72`
- **维度**: D8/D1
- **证据**:
```java
// BaseDataSetMeta.projectWithRename
new2old.forEach((newName, oldName) -> {
    BaseDataFieldMeta field = fieldMetas.get(getFieldIndex(oldName));
    if (field != null) {
        newColumns.add(field);      // newName 从未使用，字段仍带旧名
    }
});

// ProjectDataSetMeta.projectWithRename
List<String> fields = new ArrayList<>(new2old.keySet());
return new ProjectDataSetMeta(source.projectWithRename(new2old), fields);
// buildFieldIndexes 用 new 名到（仍是旧名的）source 里查找 → getFieldIndex 抛 ERR_DATASET_UNKNOWN_COLUMN
```
- **现状**: `rename(new2old)` 缺省实现组装 `{旧名:旧名} ∪ {新名:旧名}` 后调 `projectWithRename`。`BaseDataSetMeta` 实现忽略 `newName`，产出的 meta 仍报旧列名（重命名静默失效）；`ProjectDataSetMeta` 实现先让内层变成旧名 meta、再用新名 keySet 查索引，只要发生真实重命名（新名≠旧名）构造时即抛"未知列"异常。另：`if (field != null)` 为死代码——`getFieldIndex` 未知列时直接抛 NopException 而非返回非法值；结果列顺序跟随 Map 迭代序。
- **风险**: 公共元数据 API 的重命名契约整体不可用。当前仓内无调用方（仅接口缺省方法引用），属潜伏缺陷。
- **建议**: `projectWithRename` 构建 `RenameDataFieldMeta`（模块内已有该类但两处实现均未使用）或复制 field meta 并改名为 `newName`；`ProjectDataSetMeta` 路径在内层解析 old 名、外层报 new 名。
- **误报排除**: 已全仓 grep `projectWithRename|\.rename(`，仅接口与两个实现互调，无业务调用方；结论基于代码语义推演，非运行时验证（已在风险描述中注明"潜伏"）。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。根因只在 `BaseDataSetMeta.projectWithRename`：改用模块内既有但从未被使用的 `BaseDataFieldMeta.renameTo(newName)` 复制字段并改名（顺带删除 `if (field != null)` 死代码——`getFieldIndex` 未知列直接抛 NopException）；`ProjectDataSetMeta.projectWithRename` 的实现本身正确（内层产出旧名 meta 才导致构造抛错），内层修复后该路径随之正常，无需改动。测试：`TestDataSetMetaRename` 四用例（红验证：修复前 `rename` 后 `getFieldName(0)` 仍报旧名 "a"；`ProjectDataSetMeta.projectWithRename` 真实重命名场景构造即抛 `nop.err.dao.dataset.unknown-column colName=x`，正是审计预测的崩溃点）。

### [P2] ProjectDataSetMeta.hasField() 与 getFieldIndex() 语义矛盾，可致下游 -1 索引越界

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/impl/ProjectDataSetMeta.java:61-68`
- **维度**: D8/D1
- **证据**:
```java
@Override
public int getFieldIndex(String colName) {
    return ArrayHelper.indexOf(fieldIndexes, source.getFieldIndex(colName));
}

@Override
public boolean hasField(String name) {
    return source.hasField(name);   // 未按投影字段过滤
}
```
- **现状**: `hasField("x")` 委托给源 meta：被投影掉的字段返回 true，而 `getFieldIndex("x")` 返回 -1。同时与 `BaseDataSetMeta.getFieldIndex`（未知列抛 NopException）的失败语义也不一致（一个返回 -1、一个抛异常）。
- **风险**: 调用方按 `if (meta.hasField(name)) ... meta.getFieldIndex(name)` 模式编程时（常见于 `IDataRow` 取值封装），对投影后的 meta 会拿到 -1 并在 `getFieldName(-1)/getObject(-1)` 处越界崩溃。
- **建议**: `hasField` 改为遍历投影后的 `fields`（或 `getFieldIndex >= 0` 判定），并统一两实现的失败语义。
- **误报排除**: 已核对 `buildFieldIndexes` 使用 `source.getFieldIndex`（抛异常语义），indexOf 在字段确属投影子集时才可能返回 -1，两条路径组合必然出现 hasField=true / getFieldIndex=-1。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。`hasField` 改为遍历投影后的 `fields` 列表（按 `isCaseInsensitive()` 决定是否忽略大小写），被投影掉的字段返回 false，不再委托 source。测试：`TestDataSetMetaRename#testProjectMetaHasFieldRespectsProjection`（红验证：修复前 `hasField("b")` 对投影掉的字段返回 true；同时断言 hasField=true 的字段 getFieldIndex ≥ 0）。

### [P2] MarkdownTableParser 丢弃数据行尾部空单元格，与生成端不对称，导致 toRecordList 缺失末列字段

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/table/MarkdownTableParser.java:69-74`
- **维度**: D1/D8
- **证据**:
```java
} else if (sc.cur == '\n' || sc.cur == '\r' || sc.isEnd()) {
    sc.skipBlank();
    if (!cellText.isEmpty()) {
        row.internalAddCell(cell);   // 行尾空单元格被丢弃
    }
    break;
}
```
- **现状**: 行结束前解析到的最后一个单元格若为空文本则不入 row。`| a | | |`（4 列、末两列为空）解析为 2 个 cell 而非 3 个。配合 `MarkdownTableHelper.toRecordList`（按 colIndex 对齐表头）与生成端 `TableToMarkdownConverter.appendDataRows`（cell 数不足时直接少写），空值尾列在"表格→记录列表"后整列缺失（key 不存在，而非值为空/null）。
- **风险**: 以 Markdown 表格承载模型数据的链路（record-mapping 的 `FORMAT_TABLE` 字段 → `MarkdownHelper.toRecordList`）中，末列显式留空的行会丢失该字段，触发必填校验误报或字段静默缺失。
- **建议**: 仅当该空 cell 是行终止前因尾随 `|` 产生的"虚拟 cell"时丢弃（即 cell 由 `|` 终止则总是加入，`\n` 终止的空 cell 也应保留占位），或按表头列数补齐。
- **误报排除**: 已手工推演 `| a | | |` 与 `| a | b |` 的解析序列：`|` 终止的空 cell（行中部）会保留，仅 `\n`/EOF 终止的空 cell 被丢；不对称性确凿。推演基于已读取的 parseRow 循环逻辑。

> **处置（fix-ai-check 分支，2026-08-22）**: **复查非问题，审计推演有误**。实证（探针测试逐模式验证）：`| a | | |` 实际解析为 **3 个 cell** `[a][][]` 而非审计断言的 2 个——`|` 终止的空 cell（含最后一个 `|` 之前那个）本来就无条件保留，审计对管道计数的推演有误。对 7 种行模式与 GFM 算法（按未转义 `|` 切分、首尾空白段丢弃）逐一对照，当前实现与 GFM 语义全部一致；审计建议的"`\n` 终止的空 cell 也保留"反而会引入真实缺陷——尾管道风格 `| a | b |` 会多出第 3 个空 cell，破坏本仓库生成端（TableToMarkdownConverter 恒输出尾管道）的 roundtrip。审计担忧的"末列留空丢字段"仅在无尾管道的短行（`| a | `）出现，而该输入按 GFM 同样丢弃尾空白段。未改解析器；补特征化测试 `TestMarkdownTableHelper#testTrailingEmptyCellSemantics` 固化 7 种模式的行为供后续审计对照。

### [P2] Markdown 字段名的转义/反转义不对称：含 ':' 等字符的键 roundtrip 损坏

- **文件**: `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MappingBasedMarkdownGenerator.java:321-329` 与 `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MappingBasedMarkdownParser.java:323-334,374-378`
- **维度**: D1/D8
- **证据**:
```java
// Generator.encodeKey：含 : 换行 引号 | 时加引号
if (str.contains(":") || str.contains("\n") || str.contains("\"") || str.contains("|")) {
    return StringHelper.quote(str);
}

// Parser.parseNameValuePair：按第一个 ':' 切分，且 decodeKey 不做 unquote
int pos = text.indexOf(':');
key = text.substring(0, pos);
...
String decodeKey(String key) { key = key.trim(); return MarkdownHelper.removeStyle(key); }
```
- **现状**: 生成端对含 `:` 的键输出 `- "a:b": v`；解析端按第一个 `:`（引号内的那个）切分，key 变成 `"a`（带残留引号），与原键 `a:b` 不相等。值的 encode/decode 是对称的（decodeValue 有 unquote），键不对称。
- **风险**: `MarkdownDslResourceLoader.serializeDslNodeToText → 重新 loadDslNodeFromResource` 的 md 型 DSL 保存/重载 roundtrip 中，含 `:`/`|` 的字段名损坏，后续按 from 名匹配字段时静默失配或触发"未知字段"错误。键名含 ':' 在领域模型中不常见，故定 P2。
- **建议**: 解析端先检测键首部成对引号（整体 unquote 后再按 ':' 切分），或生成端对键改用其它脱敏策略（如转义 `\:`）。
- **误报排除**: 已核对 decodeKey 全文无 unquote；parseNameValuePair 确以 `indexOf(':')` 首个冒号切分；`item.getContent()` 不含行首 `- ` 前缀（由 MarkdownListParser 剥离）。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复（采用审计建议的"解析端先检测成对引号"方案）。`parseNameValuePair` 对 `"` 开头的文本先经 `findQuotedStringEnd`（处理 java 转义，与生成端 `StringHelper.quote` 的 escapeJava 对称）定位结束引号，再从引号之后的 `:` 切分；畸形引号（无结束引号/跨行）回退旧的按首个 `:` 切分行为。`decodeKey` 补 `StringHelper.unquote`（非引号键原样返回）。测试：`TestMarkdownNameValuePairRoundtrip` 6 用例，经暴露 `encodeKey` 的子类做生成端→解析端 roundtrip（红验证：修复前含 `:` 的键变 `"a`、含 `|`/`"` 的键带引号残留）。

### [P2] MarkdownHelper.removeStyle 对 "*" / "**" 输入抛 StringIndexOutOfBoundsException

- **文件**: `nop-kernel/nop-markdown/src/main/java/io/nop/markdown/utils/MarkdownHelper.java:18-30`
- **维度**: D1
- **证据**:
```java
public static String removeStyle(String text) {
    if (text.startsWith("___") && text.endsWith("___")) { text = text.substring(3, text.length() - 3); }
    if (text.startsWith("**") && text.endsWith("**"))   { text = text.substring(2, text.length() - 2); }
    if (text.startsWith("*") && text.endsWith("*"))    { text = text.substring(1, text.length() - 1); }
    return text;
}
```
- **现状**: 输入 `"*"` 时第三分支 `substring(1, 0)`（begin > end）抛 StringIndexOutOfBoundsException；输入 `"**"` 时第二分支 `substring(2, 0)` 同样越界。未校验长度。
- **风险**: `removeStyle` 被 `MappingBasedMarkdownParser.decodeKey`（markdown DSL 键清洗）、`MarkdownSectionHeaderParser.parseSectionHeader`（标题清洗，`# *` 这类标题）等公共解析路径调用，单个 `*`/`**` 标题或单元格即让整份文档解析失败，异常信息与文档内容无关，难排查。
- **建议**: 每个分支增加长度守卫（如 `text.length() > 2` / `> 4`），或统一为"取首尾标记且长度足够时才剥"。
- **误报排除**: 已核对 `String.substring` begin>end 必抛越界；两个调用点（decodeKey/parseSectionHeader:30）传入未预检长度的用户文本。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。三个分支分别加长度守卫（`> 6`/`> 4`/`> 2`，确保剥除后内容非空）；单标记字符（`*`、`**`、`___`）原样返回，`***` 按单星包裹语义剥为 `*`。测试：`MarkdownHelperTest#testRemoveStyleSingleStarDoesNotThrow`（红验证：修复前 `*`/`**`/`***`/`___` 四种输入均抛 StringIndexOutOfBoundsException；正常剥除行为断言不变）。

### [P3] e.printStackTrace() 与日志框架混用（CodeGenTask / KernelCliValidateCommand）

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/task/CodeGenTask.java:215-218`；`nop-kernel/nop-kernel-cli/src/main/java/io/nop/kernel/cli/commands/KernelCliValidateCommand.java:49`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    e.printStackTrace();
    LOG.debug("nop.gen-aop-proxy-fail", e);
}
```
- **现状**: CodeGenTask 对同一异常既 `printStackTrace()`（直写 stderr，绕过日志配置）又 `LOG.debug`，且默认 debug 不输出——实际错误信息只出现在 stderr 无级别控制。KernelCliValidateCommand 在 verbose 模式同样使用 printStackTrace。
- **风险**: 构建日志污染、错误分级缺失；Maven 并行构建下 stderr 交错难读。
- **建议**: 统一用 `LOG.error/WARN`；CLI 侧可保留 stderr 输出但走格式化方法。
- **误报排除**: 两处均为刻意兜底（best-effort 生成测试代理、CLI 校验循环），逻辑本身不吞异常，仅输出方式不当。

> **处置（fix-ai-check 分支，2026-08-22）**: CodeGenTask 已修复：`e.printStackTrace()` + `LOG.debug` 改为 `LOG.error("nop.gen-aop-proxy-fail", e)`（构建路径有日志框架配置，printStackTrace 绕过级别控制且 debug 默认不可见）。免新增测试：纯日志通道修正，无数值行为语义。KernelCliValidateCommand 裁定**不修复**：CLI 工具不装配日志框架，stderr 是其诊断输出通道；verbose 模式下打印堆栈由用户显式开关控制、且每条失败已先输出 `[FAIL]` 与 message 摘要，属 CLI 惯例而非"日志框架混用"，强行改造无净收益。

### [P3] bare IllegalArgumentException 集群违背两档异常策略（含拼接伪错误码、原地排序入参）

- **文件**: `nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MappingBasedMarkdownParser.java:358`；`nop-kernel/nop-record-mapping/src/main/java/io/nop/record_mapping/md/MarkdownDslResourceLoaderFactory.java:16`；`nop-kernel/nop-markdown/src/main/java/io/nop/markdown/utils/MarkdownHelper.java:135-140,190-196`；`nop-kernel/nop-markdown/src/main/java/io/nop/markdown/table/TableToMarkdownConverter.java:26` 等
- **维度**: D4/D7
- **证据**:
```java
// MappingBasedMarkdownParser:358 —— 拼接伪 ErrorCode 字符串
throw new IllegalArgumentException("nop.err.record.md-table-field-no-item-mapping:" + field.getLocation());
// MarkdownDslResourceLoaderFactory:16
throw new IllegalArgumentException("nop.err.record.null-mapping-name:" + config.getModelType());
```
- **现状**: 两个模块均定义了自身错误码（`RecordMappingErrors`、`MarkdownErrors`）却在这些路径抛 bare `IllegalArgumentException`；错误码以字符串拼接形式嵌在 message 里，丢失 `.param()` 上下文与错误定位能力。另外 `MarkdownHelper.addImageSummarization/changeLinkUrl` 还会 `Collections.sort` 调用方传入的 posList（原地修改入参的副作用）。
- **风险**: 模型加载失败时上层无法按 ErrorCode 区分/国际化，诊断信息降级；入参突变属隐蔽副作用。
- **建议**: 替换为对应模块 `NopException + ErrorCode + .param(...)`；排序改为本地副本。
- **误报排除**: 已逐处读取上下文确认均为可预期的业务校验失败（非 JVM 编程错误），适用平台异常策略；模块内其他路径确实使用了 NopException，属同模块风格不一致。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复（4 处 + 排序副作用）。`MappingBasedMarkdownParser:358` → `NopException(ERR_RECORD_MD_TABLE_FIELD_NO_ITEM_MAPPING)`（新错误码，带 fieldName + `.loc(field.getLocation())`）；`MarkdownDslResourceLoaderFactory` → `NopException(ERR_RECORD_NULL_MAPPING_NAME)`（新错误码，带 modelType）；`MarkdownHelper.addImageSummarization/changeLinkUrl` 两处 → `NopException(ERR_MARKDOWN_POS_LIST_SIZE_NOT_MATCH)`（新错误码，带 expected/actual 计数）。`Collections.sort(posList)` 原地修改入参 → 改为 `sortedOrder` 索引副本排序；**超审计发现**：原实现只排 posList 不排并行传递的 infos/newUrls，乱序输入时区间与替换值配对本就错乱，索引排序顺带修复配对（测试 `MarkdownHelperTest#testChangeLinkUrlDoesNotMutateInputAndKeepsPairing` 以逆序输入验证配对与不变性；`#testChangeLinkUrlSizeMismatchThrowsNopException` 红验证异常类型）。`TableToMarkdownConverter:23` 的 `IllegalArgumentException("TableView cannot be null")` 裁定**维持现状**：null 入参属编程契约违规，与平台 `Guard` 系列使用 IAE 的惯例一致，不属两档策略针对的业务校验路径。

### [P3] 可变 public static 单例字段（非 final）可被外部覆写

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/binder/DataParameterBinders.java:25-434`（17 个 binder 常量）；`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/rowmapper/SmartRowMapper.java:16-20`、`SingleColumnRowMapper.java:15`；`nop-kernel/nop-markdown/src/main/java/io/nop/markdown/simple/MarkdownListParser.java:17-18`、`MarkdownCodeBlockParser.java:15`、`MarkdownSectionHeaderParser.java:8` 等
- **维度**: D3/D7
- **证据**:
```java
public static IDataParameterBinder STRING = new IDataParameterBinder() { ... };
public static MarkdownListParser NESTED = new MarkdownListParser(true);
```
- **现状**: 全部以 `public static`（无 final）暴露，任何代码可 `DataParameterBinders.STRING = xxx` 覆写全局行为；并发覆写无防护。同类 `INSTANCE` 单例（MarkdownCodeBlockParser、TableToMarkdownConverter 等）同样非 final。
- **风险**: 全局静默篡改/意外赋值难以排查；与平台不可变单例惯例不符。
- **建议**: 加 `final`；需要替换语义的场景已由 `registerInstance`/`register` 机制承担。
- **误报排除**: 已确认无任何代码路径需要重赋值这些字段（注册机制走 `defaultBinders` map / `registerInstance`）。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。全仓 grep 确认零重新赋值后统一加 `final`：`DataParameterBinders` 17 个 binder 常量、`SmartRowMapper` 4 个（INSTANCE/CASE_SENSITIVE/CASE_INSENSITIVE/CAMEL_CASE）、`SingleColumnRowMapper.INSTANCE`、`MarkdownListParser.NESTED/FLAT`、`MarkdownCodeBlockParser.INSTANCE`、`MarkdownSectionHeaderParser.INSTANCE`（`TableToMarkdownConverter.INSTANCE` 原本已是 final）。免新增测试：纯不可变化，由编译期保证，模块全量测试编译+运行通过即为验证。

### [P3] ClassRenamer 正则无法处理含括号的注解参数，且 appendReplacement 未转义替换串

- **文件**: `nop-kernel/nop-codegen/src/main/java/io/nop/codegen/utils/ClassRenamer.java:17-27,41-48`
- **维度**: D1
- **证据**:
```java
private static final Pattern PUBLIC_CTOR_PATTERN =
        Pattern.compile("(public\\s+)(\\w+)\\s*\\(([^)]*)\\)");
...
ctorMatcher.appendReplacement(sb,
        ctorMatcher.group(1) + newClassName + "(" + ctorMatcher.group(3) + ")");
```
- **现状**: `([^)]*)` 在构造函数参数含 `)`（如 `@Prop("a(b")`、嵌套泛型带注解 `@Ann(x=1)`）时提前截断匹配，替换后参数列表被改写损坏；且 group(3) 含 `$`/`\` 时 appendReplacement 会抛 IllegalArgumentException 或注入分组引用。
- **风险**: 当前仅被 `ClassRenamerTest` 使用（已全仓 grep），属潜伏工具缺陷；一旦用于含注解参数的源码即产出无法编译的结果。
- **建议**: 用 Janino 解析（模块内已有 `JavaCompileTool.parseJavaSource`）或至少 `Matcher.quoteReplacement` + 更严谨的参数匹配。
- **误报排除**: 测试用例仅覆盖无注解简单参数，未覆盖该分支；已确认生产代码无调用点。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。构造函数替换从正则改为手工扫描：定位单词边界后的 `public <类名>(`，跳过字符串/字符字面量（处理 `\` 转义）找到平衡右括号，参数列表逐字复制（`$`/`\` 天然安全，无需 quoteReplacement），括号不平衡的异常输入保持原样不损坏；类名替换仍用原 CLASS_PATTERN（`\w+` 匹配，无注入面）。保留为工具类（对外 API，下游可能使用），未删除。测试：`ClassRenamerTest` 新增 3 用例：`#testConstructorWithParenAndQuoteInAnnotation`（`@Prop("a(b")`、`@Prop("x\"y")` 参数完整保留——修复前 `([^)]*)` 在引号内 `)` 处截断损坏参数列表）、`#testConstructorWithDollarAndBackslashInParams`（`$`/`\` 修复前触发 appendReplacement 分组引用/转义异常）、`#testUnbalancedParenKeptAsIs`（防御性行为）。

### [P3] RecordInputImpls.defaultReadBatch 在 maxCount==0 时仍读取 1 条记录

- **文件**: `nop-kernel/nop-dataset/src/main/java/io/nop/dataset/record/impl/RecordInputImpls.java:58-66`（同型逻辑 41-52）
- **维度**: D1/D8
- **证据**:
```java
int n = 0;
while (input.hasNext()) {
    T record = input.next();
    ret.accept(fn.apply(record));
    n++;
    if (maxCount >= 0 && n >= maxCount) {
        break;
    }
}
```
- **现状**: 先消费 1 条再检查上限，`maxCount==0` 时返回 1 条而非 0 条。`IRecordInput.readBatch(maxCount)` 的语义为"最多读取 maxCount 条"，`LimitRecordInput` 也用 `Guard.nonNegativeLong` 允许 0。
- **风险**: `chunkIterator(0)` 或显式 `readBatch(0)`（如分页边界、探测性读取）多消费一条且该条已被迭代器吞掉，无法回退。
- **建议**: 循环前 `if (maxCount == 0) return;` 或将判断移到消费前（`n < maxCount || maxCount < 0`）。
- **误报排除**: 负数被显式用作"无限"哨兵（`maxCount >= 0` 条件可证），仅 0 值行为错误。

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷确认属实，已修复。两个 `defaultReadBatch` 重载均在循环前 `if (maxCount == 0) return;`，负数无限哨兵与正常上限行为不变。测试：`TestRecordInputImpls#testDefaultReadBatchZeroMaxCountReadsNothing`（断言 0 条返回且输入未被消费——修复前多吞 1 条）/`#testDefaultReadBatchWithFilterZeroMaxCountReadsNothing`（filter 重载）。

### [P3] JavaCompileTool.getErrorDetail 的 substring 存在越界风险

- **文件**: `nop-kernel/nop-javac/src/main/java/io/nop/javac/JavaCompileTool.java:58-69`
- **维度**: D1
- **证据**:
```java
String locStr = loc.toString();
String msg = e.getMessage();
if (msg.startsWith(locStr)) {
    return msg.substring(locStr.length() + 2);
}
```
- **现状**: 当 message 恰好等于 `locStr` 或仅比其长 1 个字符（如尾部只剩 `":"`) 时，`substring(locStr.length() + 2)` 越界抛 StringIndexOutOfBoundsException，掩盖原始解析错误。
- **风险**: 触发依赖 Janino 特定 message 形态，窗口窄；一旦触发，用户看到的是越界异常而非解析失败详情。
- **建议**: 改为 `msg.length() > locStr.length() + 2 ? msg.substring(locStr.length() + 2) : msg` 或用 indexOf 定位。
- **误报排除**: `msg.startsWith(locStr)` 已确认存在（代码作者即为此场景所写），仅长度差 <2 的子分支未防护。

> **处置（fix-ai-check 分支，2026-08-22）**: **复查非问题（触发前提不可达）+ 顺手加固**。实证 janino `LocatedException.getMessage()` 的实现（反编译核实）：location 非空时恒为 `locStr + ": " + rawMessage`，即 `msg.length() >= locStr.length() + 2` 恒成立，`substring(locStr.length() + 2)` 的越界路径（长度差 <2）经 getMessage() 不可达；location 为空时早已提前返回。审计设想的"message 恰好等于 locStr"无法由真实 CompileException 构造出。仍保留了一个防御性长度检查（`>=`，对可达输入与原行为逐位一致），并补测试 `TestJavaCompileTool#testGetErrorDetailShortMessageDoesNotThrow` 固化空/单字符 rawMessage 与正常剥离三种行为——防 janino 升级改变 message 格式时引入越界。

## 附注

- D7（Nop 平台 IoC/配置约定）：7 个模块均未使用 `@Inject`/`@Value`/`@Component` 注解（grep 全量确认为零命中），无 beans.xml 依赖，不存在 private 字段注入问题；异常策略问题已单列（P3 集群）。
- D5（路径遍历）：codegen 产物路径由受信模板（VirtualFileSystem 内资源）驱动，`XCodeGenerator.getTargetPath` 虽允许模板指定绝对路径，但模板属构建期受信输入；CLI 的 output 目录为用户显式参数；`JavaCompileResult.saveGenerated` 的 className 来自编译产物集合。未发现现实可达的路径注入面，故未列发现。
- `nop-antlr4` 两个子模块抽查（AbstractAntlrLexer 的错误 token 处理、AbstractParseTreeParser 两阶段解析、AstGrammar 的返回类型推导、MarkdownListParser 的缩进/树构建）未发现可证实缺陷；`twoPhaseParse` 第二阶段沿用 BailErrorStrategy（仅切换 LL 预测）会使错误恢复信息弱于标准两阶段模式，属取舍而非错误，未列入。
- 本报告为纯检查产物，未修改任何源代码。
