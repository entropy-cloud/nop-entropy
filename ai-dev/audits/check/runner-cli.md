# runner-cli 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-runner
- 文件数: 37（src/main/java，实际数；任务描述中的"约 50"与实际不符：nop-cli-core 24 + nop-cli-jdk11 1 + nop-cli 1 + nop-tool 11）
- 覆盖范围声明: 37 个主代码文件全部完整深读（100%），含 NopCliApplication/MainCommand/全部 17 个命令类/CliErrors/NopExitCodeExceptionMapper/FileWatcherFactory/GenOrmHelper，以及 nop-tool 全部 11 个类与两个 main 入口。测试代码不在审计范围，仅为验证可触达性而查阅 TestNopCli.java（注意：该测试类整体 @Disabled）。为排除误报额外核验了模块外实现：FileHelper.getJarFile、FileResource(File)、ResourceHelper（resolveRelativePath/zipDir/unzipToDir/getTempResource）、LocalFileOperator（路径遍历防护）、NopApplication、BeanContainer、QuarkusIntegration、IocCoreInitializer、JdbcMetaDiscovery（连接关闭）、FileWatcher。并用本地 JDK 实测 `new File(base,"/")` 解析行为。已验证无 ProcessBuilder/Runtime.exec（无命令注入面）；nop-cli 模块仅 EmptyMain 占位类，无问题。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 7 |
| P3 | 8 |

---

## 发现列表

### [P1] CliDiffCommand.loadModel 忽略传入参数，恒定加载 newModelPath，diff 结果恒为空

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliDiffCommand.java:78-84`
- **维度**: D1（正确性），兼 D8
- **证据**:
```java
Object loadModel(String modelPath) {
    IResource resource = ResourceHelper.resolveRelativePathResource(newModelPath);  // 应为 modelPath
    String fileExt = StringHelper.fileExt(modelPath);
    if (JsonTool.isJsonOrYamlFileExt(fileExt))
        return JsonTool.parseBeanFromResource(resource);
    return ResourceComponentManager.instance().loadComponentModel(resource.getPath());
}
```
- **现状**: `call()` 中 `loadModel(oldModelPath)` 与 `loadModel(newModelPath)` 都会解析 `newModelPath` 字段。旧模型永远不会被加载，diff 结果恒为 `{}`（新旧模型是同一个对象）。另外 `StringHelper.fileExt(modelPath)` 用参数而资源用字段，两者还可能来自不同文件（扩展名判断与实际加载资源不一致）。`-N/--new-model` 选项未声明 `required = true`，仅传 `-O` 时 `resolveRelativePathResource(null)` 会解析为当前目录资源（ResourceHelper.resolveRelativePath 对空路径返回当前目录 URL），产生完全无关联的错误。
- **风险**: 一旦该命令被启用（见下一条 P2：未注册），`diff` 会静默返回空差异——用错误结果污染下游判断（例如用 diff 结果决定是否重新生成代码），属于数据错误类缺陷。
- **建议**: `resolveRelativePathResource(modelPath)`；同时给 `-O/-N` 增加 `required = true`，并在加载前校验资源存在。
- **误报排除**: 已确认全仓库无其他调用 `loadModel(String)`；已核实 `resolveRelativePath` 对 null/空串返回当前目录（ResourceHelper.java:1063-1066）。命令当前未注册（见 P2 条目），故降级 P1 而非 P0。

### [P1] CliWatchCommand 依赖容器注入 FileWatcherFactory，在无 DI 的启动器下 watch 命令必现 NPE

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:62-67`
- **维度**: D1（NPE）、D3（启动流程）
- **证据**:
```java
@Inject
FileWatcherFactory fileWatcherFactory;

@Override
public Integer call() {
    FileWatcher watcher = fileWatcherFactory.newFileWatcher();   // 无 null 防护
```
- **现状**: 该字段由 `@Inject`（jakarta）注入，只在 Quarkus picocli 集成（`NopCliApplication` 的 `factory != null` 分支）下生效。`nop-cli-jdk11` 发行版（pom 明确 exclude `quarkus-picocli` 与 `nop-quarkus-core-starter`，shade 后 mainClass 为 `NopCliMainForJdk11`）使用 `new CommandLine(new MainCommand())` 默认反射工厂，picocli 本身不做依赖注入，字段保持 null → `watch` 命令 NPE 崩溃。`NopCliApplication.run` 的 `factory == null` 回退分支同样触发。FileWatcherFactory 也未在任何 beans.xml 中定义（全仓库检索无命中），NopIoC 路径同样无法提供。
- **风险**: jdk11 胖 jar 这个现实发行物上 `java -jar nop-cli-jdk11.jar watch ...` 必现 NPE，命令完全不可用，且 NPE 发生在命令入口、报错无上下文。
- **建议**: 在 `call()` 内做 null 回退（`fileWatcherFactory != null ? fileWatcherFactory.newFileWatcher() : new FileWatcher(new NioFileWatchService(), GlobalExecutors.globalTimer())`，注意 start），或改为命令内部直接构造，去掉对容器注入的依赖。
- **误报排除**: 已读 nop-cli-jdk11/pom.xml 确认 exclude 与 mainClass；已确认 TestNopCli 通过注入 factory 运行（且整体 @Disabled，未覆盖 jdk11 路径）；picocli 默认工厂不处理 @Inject 属库的既定行为。

### [P2] CliRunCommand 对目录执行时 listFiles() 无 null 检查，Arrays.sort(null) NPE

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRunCommand.java:111-112`
- **维度**: D1
- **证据**:
```java
File[] subFiles = file.listFiles();
Arrays.sort(subFiles);
for (File subFile : subFiles) {
```
- **现状**: `File.listFiles()` 在 IO 错误、目录失去读权限、或目录在 exists 检查后被并发删除/替换时返回 null，`Arrays.sort(null)` 直接 NPE。同模块同类遍历（SourceRefactor.refactorDir:96-97、AbstractMigrateTask.migrateDir:37-38、SourceCounter.count:47-48）都做了 null 检查，唯独此处缺失。
- **风险**: 特定条件（权限/竞态）触发崩溃，报错信息与业务原因无关，难以定位。
- **建议**: `if (subFiles == null) return;` 或抛带路径的 NopException。
- **误报排除**: 非 Style 建议；Java 标准语义 listFiles 可返回 null；对比同模块其余三处实现均已判空，确属遗漏。

### [P2] CliRunCommand 定时模式：任务抛异常会静默终止整个调度循环且无任何日志

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRunCommand.java:89-99`
- **维度**: D4
- **证据**:
```java
if (interval > 0) {
    GlobalExecutors.globalTimer().scheduleWithFixedDelay(() -> {
        runTasks(globalState);
    }, interval, interval, TimeUnit.MILLISECONDS);
```
- **现状**: `runTasks` 会因任务文件校验失败（ERR_CLI_FILE_NOT_TASK_FILE 等）、脚本执行异常等抛出 NopException。`scheduleWithFixedDelay` 的语义是任一次执行抛异常后后续执行全部被取消且异常被 executor 吞掉。循环体内无 try/catch、无日志。且首次 `runTasks(globalState)`（第 87 行）若成功后文件才被改坏，CLI 会表现为"挂着但再也不干活"，用户无从得知。
- **风险**: 监控/轮询类任务静默失效（数据不再刷新），是典型异常吞噬导致的正确性问题。
- **建议**: lambda 内 try/catch，异常用 LOG.error 记录后返回（保持调度继续）。
- **误报排除**: 已核对 JDK ScheduledExecutorService.scheduleWithFixedDelay 的 javadoc 语义（"If any execution of the task encounters an exception, subsequent executions are suppressed"）；GlobalExecutors.globalTimer() 为标准调度器封装。

### [P2] CliRepackageCommand 选项未标 required，缺参/非 jar 运行时以 NPE 深层失败

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliRepackageCommand.java:20-32,55`
- **维度**: D1、D8
- **证据**:
```java
@CommandLine.Option(names = {"-i", "--input"}, description = "Input directory")
File inputDir;                       // 无 required = true

@CommandLine.Option(names = {"-o", "--output"}, description = "Output jar file")
File outputFile;                     // 无 required = true
...
File jarFile = FileHelper.getJarFile(CliRepackageCommand.class);   // 非 jar 运行时返回 null
...
ResourceHelper.zipDir(tempDir, new FileResource(outputFile), options);  // outputFile==null → NPE
```
- **现状**: 三条失败路径：(1) 缺 `-o` 时 `new FileResource(null)` 在 FileResource.buildPath 里 `file.getAbsolutePath()` NPE——且发生在整个 jar 已解压、复制完成之后，白做全部工作；(2) 从 classpath/IDE 运行（非 fat jar）时 `getJarFile` 返回 null，第 32 行 `new FileResource(null)` NPE；(3) 缺 `-i` 时 `new File(null, "bootstrap.yaml")` 语义等同于当前目录下找文件，静默跳过所有复制，输出一个与输入无关的 jar，属静默错误结果。
- **风险**: 参数错误的用户得到裸 NPE 而非参数校验错误；缺 `-i` 时产出看似成功但内容错误的 jar。
- **建议**: 两个选项加 `required = true`；对 `getJarFile` 返回 null 抛带说明的 NopException（"must run from jar"）。
- **误报排除**: 已读 FileHelper.getJarFile（nop-commons FileHelper.java:426-441，非 `jar:file:` 前缀返回 null）与 FileResource 构造器（FileResource.java:37-44）确认 NPE 路径真实。

### [P2] CliFileCommand write 操作：仅传 --input 被拒，同时传 --content 与 --input 时 --content 被静默忽略

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliFileCommand.java:150-163`
- **维度**: D8（契约不一致）
- **证据**:
```java
if (content == null) {
    throw new IllegalArgumentException("--content is required for write operation");  // 先检查 -c
}
FileContents contents;
if (inputFile != null) {
    String fileContent = FileHelper.readText(inputFile, null);   // -i 存在时 -c 被完全忽略
    contents = FileContents.fromText(fileContent);
```
- **现状**: `-i/--input` 的帮助文本为 "Read content from input file (write operation)"，暗示可单独使用；但代码先强制 `-c` 非空，导致只传 `-i` 时报误导性错误 `--content is required`（必须再传一个任意 `-c xxx` 才能用文件输入）。同时传两者时 `-c` 的值被静默丢弃。
- **风险**: 契约漂移：按帮助文档使用即失败；错误信息指向不存在的必填项。
- **建议**: 判空条件改为 `content == null && inputFile == null`；两者同时给出时报错或明确 -i 优先。
- **误报排除**: 已核对完整 call/handleWrite 流程，确认无其他路径补偿该检查。

### [P2] CliFileCommand 顶层 catch(Exception) 仅打印 getMessage()，吞掉全部栈信息

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliFileCommand.java:126-129`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    System.err.println("Error: " + e.getMessage());
    return 2;
}
```
- **现状**: 所有子操作的任意异常（包括 NPE、ClassCastException、NopException）只输出一行 message。NPE 的 getMessage() 多为 null（输出 "Error: null"），PatternSyntaxException 等只有一行描述，无类型无栈无日志，无法定位。同模块其他命令（CliGenCommand/CliDiffCommand/CliCallToolsCommand）都用 LOG.error 记录完整异常。
- **风险**: 不可预期的运行时异常完全不可诊断，用户只能看到 "Error: null" 与退出码 2。
- **建议**: 至少 `LOG.error("...", e)` 后再输出用户可读信息；或保留栈输出。
- **误报排除**: 已确认无 verbose/ debug 开关弥补；对比同模块错误处理惯例确认这是孤例。

### [P2] CliDiffCommand 未注册进 MainCommand，diff 子命令从 CLI 不可达

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/MainCommand.java:17-35`
- **维度**: D8（命令声明与行为不匹配）
- **证据**:
```java
subcommands = {
        CliGenCommand.class,
        CliReverseDbCommand.class,
        CliWatchCommand.class,
        ...                      // 共 17 个，无 CliDiffCommand.class
```
- **现状**: CliDiffCommand 实现完整（含 @CommandLine.Command(name="diff")），但未列入 MainCommand 的 subcommands。`nop-cli diff -O a -N b` 会得到 picocli 的 "Unmatched argument" 错误。全仓库检索无其他引用，也无测试覆盖（TestNopCli 整体 @Disabled 且无 diff 用例）。
- **风险**: 功能缺失类契约漂移：类存在、看似可用，实际入口未接；同时掩盖了上面的 P1 缺陷（正因不可达而未被发现）。
- **建议**: 注册 CliDiffCommand.class 并补充针对 P1 的回归测试（断言 diff 非空）；或若有意废弃则删除该类。
- **误报排除**: 已全仓库 grep `CliDiffCommand`，仅类自身定义一处命中。

### [P2] CliWatchZipCommand：目标 zip 位于被监控目录内时形成自触发无限重压缩循环

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchZipCommand.java:103-131`
- **维度**: D1
- **证据**:
```java
watchService.watch(dir.toPath(), path -> true, recursive, new IFileWatchListener() {
    public void onFileChange(...) { scheduleZipUpdate(executor, dir, zipFile); }
    ...
});
// scheduleZipUpdate:
ResourceHelper.zipDir(new FileResource(dir), new FileResource(zipFile), null);
```
- **现状**: 监控过滤器恒为 `path -> true`，不排除 zipFile 自身。当用户指定的 zip 落在 watchDir 内（如 `nop-cli watch-zip . out.zip`，或 `watch-zip mydir mydir/x.zip`）时：写 zip → 触发 change 事件 → debounce 后重写 zip（时间戳变化）→ 再触发事件……形成无限循环；且 zipDir 遍历目录时会把正在写入的 zipFile 自身打入包内。默认 zip 路径（父目录下 `<dir>.zip`）不受影响。
- **风险**: 特定但现实的参数组合下 CPU/IO 持续空转、zip 文件反复膨胀重写。
- **建议**: 监控过滤排除 zipFile 的绝对路径（`!path.toAbsolutePath().equals(zipFile.toAbsolutePath())`），或校验 zipFile 必须位于 watchDir 之外。
- **误报排除**: 已确认 debounce 仅合并窗口内事件，不阻止由 zip 写入本身产生的新一轮事件；zipDir 每次 rewrite 无字节稳定性保证。

### [P3] NopCliMainForJdk11 未装配 NopExitCodeExceptionMapper、未做 CoreInitialization.destroy()

- **文件**: `nop-runner/nop-cli-jdk11/src/main/java/io/nop/cli/jdk11/NopCliMainForJdk11.java:8-13`
- **维度**: D8、D4
- **证据**:
```java
public static void main(String[] args) {
    new NopApplication().run(args);
    int exitCode = new CommandLine(new MainCommand()).execute(args);
    System.exit(exitCode);
}
```
- **现状**: 与 NopCliApplication 相比：未 `setExitCodeExceptionMapper(new NopExitCodeExceptionMapper())`（命令异常时退化为 picocli 默认行为：打印整栈 + 固定退出码 1），也未在结束后调用 `CoreInitialization.destroy()`（靠 ShutdownHook 兜底）。两个入口的退出码语义不一致。
- **风险**: 同一命令在不同发行版上退出码/输出不一致，影响脚本化使用。
- **建议**: 与 NopCliApplication 对齐：装配 mapper，执行后 destroy。
- **误报排除**: 已对比 NopCliApplication.run 两个分支的实现。

### [P3] NopExitCodeExceptionMapper 将 HTTP 语义状态码（含负值）直接作为进程退出码

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/exception/NopExitCodeExceptionMapper.java:16-21`
- **维度**: D8
- **证据**:
```java
ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exception);
return errorBean.getStatus();     // 500/404/-100 等直接作为 exit code
```
- **现状**: 通用异常默认 status 500；CliRunCommand 显式 `.status(-100)`。退出码落在 shell 0-255 惯例之外（500 mod 256 = 244，-100 → 156），语义混乱。
- **风险**: 脚本按退出码分支判断不可靠；负值/大数值被 shell 截断。
- **建议**: 映射为受控区间（如 NopException 显式指定时取 `status & 0xFF`，或统一 1）。
- **误报排除**: 已确认 buildErrorMessage 会继承 NopException.getStatus()（CliRunCommand:66 `.status(-100)`）。

### [P3] CliValidateCommand 使用 e.printStackTrace() 而非 Logger

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliValidateCommand.java:48-50`
- **维度**: D4、D7
- **证据**:
```java
if (verbose) {
    e.printStackTrace();
}
```
- **现状**: verbose 模式直接向 stderr 打栈，绕过日志框架；同命令其余输出均走 System.out/err，尚可接受，但与模块整体用 SLF4J 的惯例不一致。
- **风险**: 无日志级别/上下文，维护性弱。
- **建议**: `LOG.error("[FAIL] {}", inputFile, e)`。
- **误报排除**: 仅此一处 printStackTrace（全模块 grep 确认）。

### [P3] 多个命令将 -i/--input JSON 结果强转 Map，非对象 JSON 报 ClassCastException

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliGenCommand.java:71`、`CliWatchCommand.java:100`、`CliRunCommand.java:72`、`CliRunTaskCommand.java:113,118`、`CliExportDbCommand.java:66`、`CliImportDbCommand.java:67`
- **维度**: D1
- **证据**:
```java
Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(null, input);
scope.setLocalValues(map);
```
- **现状**: `-i "[1,2]"` 或 `-i "abc"` 等合法 JSON 但非对象时，抛 `ClassCastException: class java.util.ArrayList cannot be cast to class java.util.Map`，用户无从知道是 -i 格式问题。
- **风险**: 参数错误的报错不可读（CliWatchCommand 中该异常还会发生在 watcher 回调线程里）。
- **建议**: 解析后 `instanceof Map` 校验，否则抛带参数名的 NopException。
- **误报排除**: 已核对 parseNonStrict 返回 Object 且可为 List/String/Number；各处均无类型检查。

### [P3] CliWatchCommand：System.in.read() 空 catch 吞 IOException，watch 返回的 ICancellable 未取消

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliWatchCommand.java:84-93`
- **维度**: D4
- **证据**:
```java
watcher.watch(..., events -> processEvents(xpl, events, state));

try {
    System.in.read();
} catch (IOException e) {
}
return 0;
```
- **现状**: (1) 空 catch 静默吞 IO 异常；(2) `FileWatcher.watch` 返回 `ICancellable`（已核验 FileWatcher.java:49），被丢弃，退出监听循环后依赖进程级 CoreInitialization.destroy() 收尾；(3) stdin 为 EOF（如后台/重定向运行）时 read 立即返回，watch 模式立即退出，命令看起来"成功"但没做任何事。
- **风险**: 轻微：退出路径资源回收依赖兜底；后台运行时行为反直觉。
- **建议**: 捕获后 LOG.warn；持有 ICancellable 并在返回前 cancel。
- **误报排除**: 已核验 FileWatcher.watch 签名返回 ICancellable。

### [P3] GenOrmHelper.addCatalog 死代码且行号 off-by-one

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/GenOrmHelper.java:66-93`
- **维度**: D1（潜在）
- **证据**:
```java
int index = 1;
...
for (IEntityModel entityModel : tables) {
    ExcelRow row = table.makeRow(index++);
    row.makeCell(0).setValue(index);      // 使用自增后的值，首行编号为 2
```
- **现状**: 全模块检索 `addCatalog` 无任何调用（nop-ofbiz 中同名方法属另一类），为不可达死代码。若被复用：序号列首行写 2；且 `makeRow(1)` 会与读取样式的第 1 行（`getCell(1,0)` 等取样式来源行）重叠，可能覆盖模板行。
- **风险**: 当前无影响；未来复用时引入编号错位。
- **建议**: 删除或修复（`setValue(index - 1)` 或先取值再自增），并确认 makeRow 行号语义。
- **误报排除**: 已全仓库 grep 确认无调用方。

### [P3] SourceCounter：潜在 NPE 与 .pptx 过滤 typo

- **文件**: `nop-runner/nop-tool/src/main/java/io/nop/tool/counter/SourceCounter.java:82,88`
- **维度**: D1
- **证据**:
```java
if (name.equals("target") && file.getParentFile().listFiles().length == 1)
    return true;
...
if (name.endsWith(".xlsx") || name.endsWith(".pdf") || name.equals(".pptx") || ...)
```
- **现状**: (1) `getParentFile().listFiles()` 可能为 null（空目录列举失败/权限），`.length` NPE；且该条件（父目录仅 1 个条目就忽略 target）语义可疑，疑为调试残留；(2) `name.equals(".pptx")` 应为 `endsWith(".pptx")`，真实 pptx 文件名永远不等于 ".pptx"，过滤不生效（默认扩展名集合不含 pptx，影响有限）。
- **风险**: 统计工具在特定目录结构下崩溃；pptx 计入统计（若自定义扩展名集合）。
- **建议**: 判空；修正 endsWith。
- **误报排除**: 已确认第 78 行已处理"pom.xml 同级 target"，第 82 行是独立条件；typo 直接可见。

### [P3] 错误提示与实际支持不一致 + 用户输入校验未走 CliErrors 错误码体系

- **文件**: `nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliGenOrmExcelCommand.java:44,79`、`CliFileCommand.java:123`、`CliGenFileCommand.java:122`、`CliSplitCommand.java:44`
- **维度**: D8、D7
- **证据**:
```java
// CliGenOrmExcelCommand：参数描述与错误信息矛盾
@CommandLine.Parameters(description = "Model file (.pdm | .pdma.json | .xmeta | .xdef)", index = "0")
...
System.err.println("Only .pdm or .pdma.json model files are supported");   // 但 .xmeta/.xdef 分支存在

// CliFileCommand default 分支提示过时
System.err.println("Error: Unknown operation. Must be 'read' or 'write'");  // 实际还支持 path-tree/plain-path-tree/find

// CliGenFileCommand
throw new IllegalArgumentException("invalid template:" + template);
```
- **现状**: (1) gen-orm-excel 的失败提示否认 .xmeta/.xdef 支持，与其参数说明和代码分支矛盾；(2) file 命令 default 分支提示遗漏三种操作；(3) 模块已定义 CliErrors 错误码（NopException + ErrorCode + .param），但 CliFileCommand/CliGenFileCommand/CliSplitCommand 对用户输入错误抛裸 IllegalArgumentException、经 System.err 输出，未复用错误码体系（CliGenOrmExcelCommand 的 "null meta" 同类）。
- **风险**: 帮助/报错误导用户；错误处理风格与平台两档策略漂移（未到 bare RuntimeException 程度，属轻微）。
- **建议**: 修正文案；用户输入错误统一走 CliErrors 补充定义的 ErrorCode。
- **误报排除**: 已通读 CliErrors.java 确认现有错误码未覆盖这些场景（是"未使用体系"而非"错误码拼错"）；IllegalArgumentException 非 bare RuntimeException，按轻微违规定级。

---

## 维度覆盖小结

- D1 正确性: P1×2（diff 加载错文件、watch NPE）+ P2×3 + P3×3
- D2 资源管理: 未发现泄漏（LogProcessor/SourceRefactor/CliRepackageCommand 的 close/finally 均正确；CliReverseDbCommand 的连接由 JdbcMetaDiscovery 内部 finally 关闭，已核验排除）
- D3 并发/线程安全: 共享状态均用 ConcurrentHashMap；watch 调度线程模型核验无数据竞争；启动流程问题并入 P1 watch 条目
- D4 错误处理: P2×2 + P3×3；无 bare RuntimeException、无丢 cause 的 NopException.adapt 误用
- D5 安全: LocalFileOperator 已有 `..`/`:` 拒绝 + canonical 前缀校验（已核验），无进程执行、无命令注入面；未发现可报问题
- D6 性能: 未发现重复初始化或显著重复 IO（SourceCounter 每文件双读属统计工具内部行为，不计）
- D7 平台规范: @Inject 均为 package-private 字段或 setter（合规）；FileWatcherFactory 依赖 Quarkus 而非 beans.xml 的问题已按 D1 定级（P1）；其余见 P3 错误处理条目
- D8 契约一致性: P2×3 + P3×3（未注册命令、选项 required 缺失、write 契约、退出码语义）
