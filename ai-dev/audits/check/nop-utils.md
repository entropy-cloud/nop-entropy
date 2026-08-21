# nop-utils 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-utils
- 文件数: 138（任务给定口径）；实测 src/main/java 下 115 个 Java 文件，其中 4 个为 `_gen` 生成文件（nop-fsm/model/_gen/，按规则排除），实际审计对象 111 个
- 覆盖范围声明:
  - 子模块构成（10 个）: nop-commons-java21(2)、nop-diff(13)、nop-fsm(16，含 4 个 _gen 排除)、nop-git(6)、nop-image(2)、nop-java-parser(11)、nop-match(38)、nop-router(6)、nop-shell(12)、nop-table-validator(13)
  - 逐行深读约 45 个高风险/核心文件: nop-shell 全部核心 7 个（ShellRunner/ShellCommand/ShellCommands/OsUserHelper/DesktopHelper/DefaultCollector/LogCollector）、nop-git 全部（GitRepositoryImpl/GitRepositoryManagerImpl/IGitRepository/GitServerConfig）、nop-diff 9 个（Parser/Applier/Hunk/Line/LineType/UnifiedDiff/TextDiffUtils + Myers 算法头部粗读）、nop-router 全部核心 4 个（TriePathRouter/Trie/TrieNode/PatternChild）、nop-image 全部 2 个、nop-fsm 执行核心 2 个（StateMachine/StateId）、nop-table-validator 5 个核心、nop-java-parser 2 个（Formatter/DeltaJavaMerger）、commons-java21 全部 2 个
  - 全模块 grep 扫描: 空 catch、bare RuntimeException、printStackTrace、synchronized、可变 static 集合、Runtime/ProcessBuilder/Executors、@Inject/@Component 等（前三项全模块无命中）
  - 未深读: nop-match 剩余 ~30 个编译器/模式类（抽查了 5 个并对全部 pattern 的错误收集路径做了一致性比对）、nop-java-parser 剩余 9 个（Delta 合并器族/简化器）、nop-fsm 模型类 ~10 个、nop-diff 的 Myers 两个算法文件仅头部粗读
  - 关键外部行为已用字节码/源码验证: JGit 7.3.0（DirCacheIterator 构造后 parseEntry、Repository.getRepositoryState 不检查 closed、Git.close 不置空 repo）、javaparser-core 3.27.0（JavaParser 持有可变 astParser 字段）、Guard.notEmpty("") 会抛 IllegalArgumentException、ApiStringHelper.split("",c) 返回空列表
  - 测试代码不在审计范围，仅用于交叉验证调用契约

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 7 |
| P2 | 7 |
| P3 | 7 |

## 发现列表

### [P0] UnifiedDiffLine 禁止空内容，导致含空行的 diff 解析必然抛异常

- **文件**: `nop-utils/nop-diff/src/main/java/io/nop/diff/UnifiedDiffLine.java:29-33`（触发链经 `UnifiedDiffParser.java:193`、`UnifiedDiffLine.java:91-103`）
- **维度**: D1
- **证据**:
```java
public UnifiedDiffLine(@JsonProperty("type") UnifiedDiffLineType type,
                       @JsonProperty("content") String content) {
    this.type = Guard.notNull(type, "type");
    this.content = Guard.notEmpty(content, "content");   // 空字符串抛 IllegalArgumentException
}

public static UnifiedDiffLine fromDiffString(String line) {
    ...
    char prefix = line.charAt(0);
    UnifiedDiffLineType type = UnifiedDiffLineType.fromPrefix(prefix);
    ...
    return new UnifiedDiffLine(type, line.substring(1));  // " " -> content=""
}
```
- **现状**: git unified diff 中空行（无论 context、删除还是新增的空行）的标准表示是单个前缀字符（如一个空格 `" "`）。`fromDiffString(" ")` 得到 `type=CONTEXT, content=""`，构造函数中 `Guard.notEmpty("")` 抛出 `IllegalArgumentException("IsEmpty:content")`（已验证 `ApiStringHelper.isEmptyObject` 对空串返回 true）。`UnifiedDiffParser.parseLines` 中该行 `trimmedLine=" "` 非空、`isValidPrefix(' ')` 为 true，会走入 `fromDiffString` 触发异常。
- **风险**: 几乎任何真实代码 diff（源文件中含空行是常态）都会让 `UnifiedDiffParser.parse/parseSingleDiff` 抛非受检异常。下游 `nop-ai/nop-ai-toolkit` 的 `PatchFileExecutor` 使用该解析器执行 AI 补丁，空行 diff 是常态输入。模块自带测试未覆盖空行场景所以未暴露。
- **建议**: 将 `Guard.notEmpty(content, "content")` 改为 `Guard.notNull`，允许空字符串内容；同时为 parse 补一个含空 context/add/delete 行的回归测试。
- **误报排除**: 已验证 `Guard.notEmpty` → `ApiStringHelper.isEmptyObject("") == true` 必抛；已检查测试文件 `TestUnifiedDiffParser.java` 无空行用例（故现有测试全绿但功能坏）。

### [P0] ShellCommand.create 在 Unix 下缺少 `-c`，单串命令在 Linux/macOS 上必然执行失败

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellCommand.java:63-80`
- **维度**: D1（附带 D5 命令语义不可控）
- **证据**:
```java
public static ShellCommand create(String command) {
    ...
    ShellCommand cmd = new ShellCommand();
    if (PlatformEnv.isWindows()) {
        cmd.addCmd("cmd");
        cmd.addCmd("/c");
    } else {
        cmd.addCmd("sh");          // 缺少 "-c"
    }
    String[] args = splitCommandLine(command);
    for (String arg : args) {
        cmd.addCmd(arg);
    }
    return cmd;
}
```
- **现状**: Windows 分支生成 `cmd /c <args...>` 语义正确；Unix 分支生成 `sh <args...>` 而非 `sh -c "<command>"`。`sh` 会把第一个参数当作脚本文件名执行。例如 `runCommand("mvn -DoutputFile=x dependency:tree")` 在 Linux/macOS 上实际执行 `sh mvn -DoutputFile=x dependency:tree`，sh 报 "Can't open mvn" 失败，且退出码非 0 时 `ShellRunner.runCommand` 只记 error 日志并返回非零 `ShellResult`。
- **风险**: 现实调用方 `nop-ai/nop-ai-skills/nop-ai-code-analyzer/.../MavenProject.java:22` 正是以单字符串命令调用 `ShellRunner.runCommand("mvn ... dependency:tree", dir)`，在所有非 Windows 平台该功能完全不可用。`OsUserHelper` 的 Mac 分支（`dscl . list /users`、`groups`）同样经此路径失效。
- **建议**: Unix 分支改为 `cmd.addCmd("sh"); cmd.addCmd("-c"); cmd.addCmd(command);`（保留原命令串，由 shell 解析），或统一改用 `splitCommandLine` 结果加 `-c` 拼接；补充跨平台单元测试。
- **误报排除**: 已通读 ShellCommand 全文确认无其他地方补 `-c`；已确认 `ShellCommands.scriptFile/task` 走独立路径不受影响；已找到仓库内现实调用方佐证触发路径。

### [P1] GitRepositoryImpl.getWorkingTreeDiff 实现与契约不符：把 index 首个 entry 的 blob 当 tree 解析，index 为空时 NPE

- **文件**: `nop-utils/nop-git/src/main/java/io/nop/git/impl/GitRepositoryImpl.java:279-290`
- **维度**: D1、D8（接口契约 `IGitRepository.getWorkingTreeDiff`: "获取工作区与最新提交的差异"）
- **证据**:
```java
// 比较工作区和暂存区
ObjectReader reader = repository.newObjectReader();
ObjectId headId = repository.resolve(Constants.HEAD);
CanonicalTreeParser oldTree = new CanonicalTreeParser();
if (headId != null) {
    oldTree.reset(reader, new RevWalk(repository).parseTree(headId));
}
CanonicalTreeParser newTree = new CanonicalTreeParser();
DirCacheIterator dirCacheIter = new DirCacheIterator(repository.readDirCache());
newTree.reset(reader, dirCacheIter.getEntryObjectId());   // blob id 当 tree 用
```
- **现状**: （1）从未读取工作区文件，方法与"工作区差异"契约无关；（2）`new DirCacheIterator(cache)` 构造后（已用 JGit 7.3.0 字节码验证构造函数会 `parseEntry()`）`getEntryObjectId()` 返回 index 第一个文件条目的 blob ObjectId，把它 `reset` 进 `CanonicalTreeParser` 会按 tree 格式解析 blob，抛 CorruptObjectException 类错误；index 为空时 currentEntry 为 null，直接 NPE。错误最终被 `handleError` 包成语义无关的 NopException。正确做法是 `diffFormatter.scan(oldTreeIter, new WorkingTreeIterator)` 或 `git.diff()`。（3）附带：`newObjectReader()` 与 `new RevWalk(...)` 均未关闭（Closeable 泄漏）。
- **风险**: 公共 API 任何调用都得不到正确结果：新仓库（空 index）直接异常；有 index 时 diff 结果完全错误。当前仓库内无下游调用，但该接口是模块对外主 API。
- **建议**: 重写为 `new Git(repository).diff().setOldTree/head vs working tree`（DiffCommand 未 setPathFilter 时即工作区 diff）；用 try-with-resources 管理 reader/RevWalk；补最小集成测试。
- **误报排除**: 已反编译 JGit 7.3.0 `DirCacheIterator` 构造与 `AbstractTreeIterator.getEntryObjectId` 确认初始 entry 语义；`GitRepositoryImpl.getCommitDiff` 的正确写法（resolve 后 null 检查 + `^{tree}`）对照证明此处是错误用法而非 JGit 惯例。

### [P1] ShellRunner 写入 inputBytes 后不关闭进程 stdin，读 stdin 到 EOF 的命令将永久挂起

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:91-96`（配合 `IoHelper.java:154-162`）
- **维度**: D2
- **证据**:
```java
try {
    if (command.getInputBytes() != null) {
        IoHelper.write(process.getOutputStream(), command.getInputBytes(), null);
        // 未 close process.getOutputStream()
    }
    readOutput(process, command, collector, stopped);
```
```java
// IoHelper.write 只 write 不 close
public static void write(OutputStream os, byte[] data, IStepProgressListener listener) throws IOException {
    ...
    os.write(data);
}
```
- **现状**: 当调用方通过 `ShellCommand.input(...)` 提供输入后，进程 stdin 管道保持打开。`cat`、`wc`、`grep <pattern>`（无文件参数）、`sh` 从 stdin 读脚本的命令都会阻塞等待 EOF。主线程随后阻塞在 `readOutput` 的 `readLine()` 上：未设置 timeout 时 `run()` 永久挂起；设置了 timeout 则被超时处理器杀死（结果为失败而非正常完成）。
- **风险**: `ShellCommand.input()` 是公开 API，配合此类命令即触发线程/进程永久占用（D2 资源耗尽）。timeout=0 是默认值，挂起为默认行为。
- **建议**: 写完 inputBytes 后 `IoHelper.safeClose(process.getOutputStream())`（try/finally），让子进程读到 EOF 正常退出。
- **误报排除**: 已读 IoHelper.write 实现确认不 close；已确认 safeDestroy（超时路径）会关 stdout 但正常路径无人关闭；Hadoop 原版 Shell 亦有 close 逻辑，此处移植时缺失。

### [P1] ModelBasedTableValidator.validateRow 未把行数据放入校验 scope，行级校验引用列名必然失效

- **文件**: `nop-utils/nop-table-validator/src/main/java/io/nop/table/validator/ModelBasedTableValidator.java:73-89`
- **维度**: D1、D8
- **证据**:
```java
public void validateRow(T row, IEvalContext context) {
    int rowIdx = totalRowCount++;
    ModelBasedValidator[] rowValidators = compiled.getRowValidators();
    if (rowValidators != null) {
        IEvalScope scope = context != null ? context.getEvalScope() : null;
        if (scope != null) {
            scope.setLocalValue("rowIndex", rowIdx);
        }
        IVariableScope rowScope = scope != null ? scope : new BeanVariableScope(Map.of("rowIndex", rowIdx));
        for (ModelBasedValidator rv : rowValidators) {
            rv.validate(rowScope, new RowWiseCollector(collector, rowIdx));
        }
    }
```
- **现状**: `ModelBasedValidator.validate(IVariableScope scope, ...)` 的所有字段引用（如 condition 中 `age > 20` 的 `age`）都从 scope 解析（已对照 nop-core 的 `validateWithDefaultCollector`，正确用法是 `BeanVariableScope.makeScope(obj)` 包住被校验对象）。此处 rowScope 只含 `rowIndex`（或外部上下文变量），行数据 `row` 从未进入 scope，`rowAdaptor` 也只用于统计列。校验条件引用任何列名时解析为 null → `passCondition` 为 false → 每行误报错（或引用外部上下文变量时用错值）。私有方法 `buildRowScope(int rowIdx)`（同样不含行数据）从未被调用，是明显的未完成痕迹。自带测试只测 stat/table check 且 `validateRow(row, null)`，未覆盖行级校验。
- **风险**: 行级校验（rowValidators）功能失效：要么全部误报，要么静默放过，取决于 condition 写法。
- **建议**: 构建包含行数据的 scope（如 `BeanVariableScope.makeScope(rowAdaptor.toMap(row))` 或用 IRowDataAdaptor 按列名取值包装 IVariableScope），再传入 `rv.validate`；删除死方法并补行级校验测试。
- **误报排除**: 已读 nop-core `ModelBasedValidator` 全文确认字段解析完全依赖传入 scope；已确认 IRowDataAdaptor 仅有 getValue(row, colIdx) 用于统计；测试文件交叉验证无行字段校验用例。

### [P1] TextDiffUtils 多 change 场景下 hunk 的 newStartLine 忽略前序偏移，新文件行号错误

- **文件**: `nop-utils/nop-diff/src/main/java/io/nop/diff/TextDiffUtils.java:162-187、243-251`
- **维度**: D1
- **证据**:
```java
private static int mapToRevisedLine(DiffChange change, int originalLine) {
    if (originalLine < change.getStartOriginal()) {
        return originalLine;      // 假设变更之前一一对应，忽略前面 change 的净偏移
    }
    // 简化映射：假设变更之前的行一一对应
    return change.getStartRevised() + (originalLine - change.getStartOriginal());
}
// generateHunks 中：
int lastEndRevised = 0;    // 只赋值从未读取（死变量）
...
lastEndRevised = change.getEndRevised();
```
- **现状**: 当一个 diff 含多个 change（如先删 2 行再在第 8 行插入）时，第二个 hunk 的 `newStartLine = mapToRevisedLine(change2, contextStart)+1` 直接返回 original 行号，没有加上 change1 造成的 revised 侧行数偏移（`endRevised-endOriginal`），生成的新文件行号偏小。`lastEndRevised` 被维护但从未使用，说明 revised 侧映射未实现。old 侧行号正确，new 侧行号在多 change 时必错。
- **风险**: `generateDiffText/generateDiffFile` 产生的 diff 中 `@@ -a,b +c,d @@` 的 `c` 错误；下游按新行号定位（编辑器跳转、补丁应用校验）会错位。
- **建议**: 维护 revised 偏移累计量（把 lastEndRevised 用起来）：`newStart = mapToRevisedLine + (lastEndRevised - lastEndOriginal)`；补多 change 的行号断言测试。
- **误报排除**: 已手工推演"10 行文件：change1 删 2/3 行，change2 在第 8 行插入"确认第二个 hunk newStartLine 少 2；单 change 场景正确（现有测试只覆盖单 change）。

### [P1] JavaParserCodeFormatter 静态共享非线程安全的 JavaParser 实例

- **文件**: `nop-utils/nop-java-parser/src/main/java/io/nop/javaparser/format/JavaParserCodeFormatter.java:25-36`
- **维度**: D3
- **证据**:
```java
public static JavaParserCodeFormatter INSTANCE = new JavaParserCodeFormatter();

private static final JavaParser JAVA_PARSER = new JavaParser(
        new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

@Override
public String format(SourceLocation loc, String sourceCode, boolean ignoreErrors) {
    ...
    ParseResult<CompilationUnit> parseResult = JAVA_PARSER.parse(sourceCode);
```
- **现状**: `INSTANCE` 是全局单例 `ITextFormatter`，`format` 可被任意线程调用；而 javaparser-core 3.27.0 的 `JavaParser` 实例持有可变字段 `astParser`（惰性创建的内部 parser，已从本地 jar 反编译确认字段存在），官方文档明确 JavaParser 实例非线程安全。模块内其他调用点（`JavaParseTool`、`DeltaJavaMerger`）都是每次 `new JavaParser()`，只有此处共享静态实例。
- **风险**: 并发格式化（如代码风格化流水线并行处理多文件）时内部状态竞争，可能产生损坏的 AST、错误的格式化结果或偶发异常，难以复现。
- **建议**: 每次 format 新建 JavaParser（与其他调用点一致），或对 JAVA_PARSER 加同步，或使用 ThreadLocal。
- **误报排除**: 已反编译确认 `JavaParser` 含非 final 可变字段 `astParser`；已对比模块内 `JavaParseTool.java:46`、`DeltaJavaMerger.java:41` 均为每次 new，佐证共享静态实例违背库的使用约束。

### [P1] TriePathRouter 根路径 "/" 注册后永远无法匹配

- **文件**: `nop-utils/nop-router/src/main/java/io/nop/router/trie/Trie.java:60-70、78-80` 与 `TriePathRouter.java:45-56、120-126`
- **维度**: D1
- **证据**:
```java
// TriePathRouter.addPathPattern: list 为空时注册到 rootNode
if (list.isEmpty()) {
    makeNode(Collections.emptyList(), node -> addValue(node, ..., value));
}
// TriePathRouter.parseRoute("/") -> split("", '/') -> emptyList（已验证 ApiStringHelper.split 行为）
// Trie.match:
public MatchResult<V> match(List<String> path) {
    if(path.isEmpty())
        return null;          // 根路径被直接短路
```
- **现状**: `addPathPattern("/", v)` 经 `parseRoute` 得到空段列表，值注册在 rootNode 上；而 `match/matchAll/matchAllValues` 对空 path 一律提前返回 null/空集，根本不会检查 rootNode 的值。注册与查询不对称。
- **风险**: 以 "/" 作为路由模式的调用方（如网关根路由）永远匹配不到，表现为静默 404/找不到路由。
- **建议**: `match` 系列在 path 为空时直接检查 rootNode 的 value；或在 parseRoute 用哨兵空串段表示根。
- **误报排除**: 已验证 `ApiStringHelper.split("", '/')` 返回 emptyList；已通读 Trie._match 确认 rootNode.value 只能经空 pattern 写入但无读取路径。

### [P1] TriePathRouter.addMatchAll 使用无大括号的字面段 "*path"，兜底路由永远不生效

- **文件**: `nop-utils/nop-router/src/main/java/io/nop/router/TriePathRouter.java:36-43`（对照 `Trie.java:38-51`）
- **维度**: D1
- **证据**:
```java
public void addMatchAll(E value) {
    List<String> list = List.of("*path");     // 字面段，不含 '{'
    makeNode(list, node -> {
        List<String> varNames = List.of("path");
        node.setTillEnd(true);
        addValue(node, varNames, value);
    });
}
// Trie._makeNode 的通配符判定：
boolean wildcard = name == null || name.indexOf('{') >= 0;   // "*path" 不含 '{' -> exact child
```
- **现状**: `_makeNode` 只把含 `{` 的段放进 wildcardChild；`addPathPattern` 的 `/**` 语法糖会转成 `{*path}`（带大括号），而 `addMatchAll` 直接用无大括号的 `*path`，在 rootNode 下创建了名为 `*path` 的 exact-match 子节点。匹配 `/a/b/c` 时 exact 查找 `a` 失败、wildcardChild 为 null，返回 null。该注册值只能匹配字面段序列 `["*path"]`。
- **风险**: 现实调用方 `nop-service-framework/nop-gateway/.../GatewayModel.java:32,45` 在路由/拦截器未配 match path 时以 `addMatchAll` 注册兜底路由——网关默认路由与默认拦截器静默失效。
- **建议**: `addMatchAll` 改用 `List.of("{*path}")`（或直接调用 `addPathPattern("/**", value)`），并补 `matchPath("/anything")` 命中兜底值的测试。
- **误报排除**: 已通读 TrieNode（无星号特殊处理）、Trie._makeNode/_match 全路径确认 exact/wildcard 分派逻辑；已确认 GatewayModel 为现实调用方。

### [P2] ShellCommand.splitCommandLine 反斜杠语义错误：双引号外的反斜杠一律吃掉下一字符

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellCommand.java:100-155`
- **维度**: D1
- **证据**:
```java
switch (c) {
    case '\\':
        // 遇到转义字符，标记下一个字符需要转义
        escaped = true;
        break;
    ...
    // default 分支之前，escaped 在循环头被消费：
if (escaped) {
    currentArg.append(c);   // 反斜杠本身丢失
    escaped = false;
    continue;
}
```
- **现状**: 该转义逻辑不区分引号上下文：（1）双引号外 `C:\tools\bin` 解析成 `C:toolsbin`（Windows 路径场景现实）；（2）POSIX 单引号内反斜杠应为字面量，这里也被当转义符；（3）行尾悬空 `\` 使 escaped 残留，末参数静默丢一个字符（无校验）。Windows 上 `cmd /c` + 此拆分结果路径即错。
- **风险**: 任何含反斜杠的命令参数在 `ShellCommand.create(String)` 路径下被静默改写。
- **建议**: 单引号内不处理转义；双引号外/内按 POSIX+Windows 混合约定明确规则；结尾 `escaped==true` 时报 IllegalArgumentException。
- **误报排除**: 已逐字符推演 `"C:\test x"` 的解析结果为 `C:test x`；已确认该方法仅被 `create(String)` 调用（受 P0-2 影响的同一入口）。

### [P2] OsUserHelper Windows 分支索引错误、硬编码行号与命令结果不校验（自 dolphinscheduler 复制的缺陷族）

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/utils/OsUserHelper.java:115-141、244-264、163-184`
- **维度**: D1、D4、D5
- **证据**:
```java
// getUserListFromWindows: 内层循环用行号 i 作列索引
for (int j = 0; j < lines[i].length(); j++) {
    if (lines[i].charAt(i) == '-') {     // 应为 charAt(j)
        count++;
    }
}
// getGroup: 硬编码第 23 行
String line = result.split("\n")[22];    // locale/版本变化即 AIOOBE 或取错行
String group = PATTERN.split(line)[1];
// createUser: 无视 runCommand 的退出码
runCommand(userCreateCmd);   // 未检查 ShellResult.getReturnCode()
return true;                 // 无条件返回成功
```
- **现状**: （1）`charAt(i)` 索引错位：当某行长度小于其行号时直接 StringIndexOutOfBoundsException（被 getUserList 捕获返回空列表），分隔行检测逻辑退化为"整行恒等比较"碰巧可用；（2）`split("\n")[22]` 依赖 `net user` 英文输出的固定行数，中文系统/不同 Windows 版本即越界或取错数据；（3）所有 create*User 调用 `runCommand` 后不检查 returnCode，`createUser` 无条件返回 true，`createUserIfAbsent` 据此打"success"日志。
- **风险**: Windows 平台上用户列表解析异常/取错、创建用户失败被报成功。当前仓库内无调用方（工具类备用），故降为 P2。
- **建议**: 修正 `charAt(j)`；解析 `net user` 输出改按分隔线定位而非固定行号；create* 系列检查 `ShellResult.getReturnCode()`。
- **误报排除**: 已通读全文并对照 dolphinscheduler 原版（文件头注明 copy 来源，原版同样存在 charAt(i)）；已确认 runCommand 返回值被忽略的多处调用点。

### [P2] OsUserHelper 凭据明文进日志，getSudoCmd 拼接外部输入无校验

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/utils/OsUserHelper.java:207-217、273-278`
- **维度**: D5
- **证据**:
```java
String createUserCmd = String.format("sudo sysadminctl -addUser %s -password %s", userName, userName);
logger.info("create user command: {}", createUserCmd);   // 密码明文进日志
...
public static String getSudoCmd(String tenantCode, String command) {
    if (StringHelper.isEmpty(tenantCode)) {
        return command;
    }
    return String.format("sudo -u %s %s", tenantCode, command);   // tenantCode 未校验
}
```
- **现状**: （1）创建 Mac 用户时把含密码的完整命令打进 info 日志；（2）`getSudoCmd(tenantCode, command)` 对 tenantCode 无任何合法性校验，含空格/分号的 tenantCode 可注入额外命令（如 `a; rm -rf /tmp/x`），该方法设计为接收租户编码（外部可影响的数据）。
- **风险**: 凭据泄漏到日志（合规问题）；tenantCode 被污染时以应用权限执行任意命令。
- **建议**: 日志脱敏（不打印 -password 参数）；getSudoCmd 对 tenantCode 做 `StringHelper.isValidFileName` 类白名单校验。
- **误报排除**: 已确认两处代码如上；属于复制自 dolphinscheduler 的已知不良实践，但按本平台 D5 标准仍应报告。

### [P2] GitRepositoryManagerImpl.existsRemoteRepository 把所有异常当"仓库不存在"

- **文件**: `nop-utils/nop-git/src/main/java/io/nop/git/impl/GitRepositoryManagerImpl.java:70-87`
- **维度**: D4
- **证据**:
```java
try {
    Collection<Ref> list = command.call();
    LOG.trace("nop.git.ls-remote:url={},{}", url, list);
    return true;
} catch (Exception e) {
    return false;      // 网络/认证/超时与"不存在"不可区分
}
```
- **现状**: ls-remote 因网络故障、凭据错误、超时抛出的异常与"远程没有该仓库"（TransportException/RepositoryNotFound）一律返回 false，且异常被完全吞掉（连 debug 日志都没有）。
- **风险**: 调用方在网络抖动时误判仓库不存在，可能触发错误的"重新初始化/克隆"分支。
- **建议**: 捕获后按异常类型区分；至少 LOG.warn 记录原因；或提供 `checkRemote` 返回三态/抛异常的变体。
- **误报排除**: 已通读全文确认无日志记录；已确认 JGit 的NotFoundException 与 GenericTransportException 可区分。

### [P2] BetweenMatchPattern / AlwaysFalseMatchPattern 构建错误后未 addToCollector，错误详情丢失

- **文件**: `nop-utils/nop-match/src/main/java/io/nop/match/pattern/BetweenMatchPattern.java:53-62`、`AlwaysFalseMatchPattern.java:26-30`
- **维度**: D4、D8（与同族 pattern 行为不一致）
- **证据**:
```java
// BetweenMatchPattern.matchValue
if (!operator.test(state.getValue(), minValue, maxValue, excludeMin, excludeMax)) {
    if (collectError) {
        state.buildError(ERR_MATCH_BETWEEN_CHECK_FAIL).param(ARG_FILTER_OP, filterOp).param(ARG_MIN, minValue)
                .param(ARG_MAX, maxValue).param(ARG_EXCLUDE_MIN, excludeMin).param(ARG_EXCLUDE_MAX, excludeMax);
        // 缺少 .addToCollector(state.getErrorCollector())，构建的异常被丢弃
    }
    return false;
}
```
- **现状**: 同包其他 pattern（Eq/CompareOp/Expr/Check/IsNull/Map/AssertOp）失败时都执行 `.addToCollector(state.getErrorCollector())`，Between 和 AlwaysFalse 构建了带参数的 NopException 后直接丢弃，收集器收不到 between 校验失败的详细原因。
- **风险**: 启用 collectError 的校验场景中 between 失败只返回 false，错误报告中缺失该条目（或缺失 min/max 上下文），排障困难。
- **建议**: 补上 `.addToCollector(state.getErrorCollector())`。
- **误报排除**: 已对同包全部 13 个 pattern 的 buildError 调用做一致性比对，确认其余均有 addToCollector。

### [P2] UnifiedDiffApplier.normalizeLine 无条件 trim，strictContext 模式实际不严格

- **文件**: `nop-utils/nop-diff/src/main/java/io/nop/diff/UnifiedDiffApplier.java:381-386`
- **维度**: D1、D8（与 `Config.strictContext` 语义不符）
- **证据**:
```java
private String normalizeLine(String line) {
    if (config.ignoreTrailingWhitespace) {
        return trimTrailingWhitespace(line).trim();
    }
    return line.trim();     // 默认模式也 trim 首尾空白
}
```
- **现状**: 默认配置（strictContext=true、ignoreTrailingWhitespace=false）下匹配校验（tryMatchAtLine/validateContext/extractContextLines）全部使用 `line.trim()`，前导缩进差异（如 4 空格 vs tab 缩进的同一行）被视为相同。fuzzyMatch 的唯一性判断同样基于 trim 结果，两个仅缩进不同的位置会被判为"不唯一"或互相混淆。
- **风险**: 本应报 ERR_DIFF_APPLY_CONTEXT_MISMATCH 的不匹配被放过，patch 可能应用在与 diff 生成时缩进不同的代码上；fuzzy 定位可能选错位置。
- **建议**: 默认分支返回原字符串（保留缩进），仅在 ignoreTrailingWhitespace 时做 trim 处理。
- **误报排除**: 已通读 normalizeLine 的全部 6 个调用点确认影响面；与 Config 注释（"严格模式：验证上下文行是否匹配"）对照确认语义冲突。

### [P2] ImageCompressHelper 压缩路径用 TYPE_INT_RGB，透明 PNG 转 JPEG 后透明区域变黑

- **文件**: `nop-utils/nop-image/src/main/java/io/nop/image/utils/ImageCompressHelper.java:105-133`
- **维度**: D1
- **证据**:
```java
while (quality >= 0.3f) {
    ...
    BufferedImage resized = resizeImage(image, tryWidth, tryHeight);
    byte[] jpegBytes = bufferedImageToBytes(resized, "jpg", quality);   // 强制转 jpg
    ...
}
private static BufferedImage resizeImage(BufferedImage src, int w, int h) {
    Image tmp = src.getScaledInstance(w, h, Image.SCALE_SMOOTH);
    BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);  // 无 alpha
```
- **现状**: PNG 超过 maxSize 后进入降质循环，一律重绘到 TYPE_INT_RGB 再编码为 jpg。半透明/透明像素在无 alpha 的 RGB 目标上默认合成黑色背景。附带：`bufferedImageToBytes` 的 else 分支不检查 `ImageIO.write` 返回值（找不到 writer 时返回空 byte[]，调用方会把 0 字节当合法结果）。
- **风险**: 带透明的 PNG（logo、图标）压缩后透明区域变黑，输出图片明显损坏。
- **建议**: 先绘制到白色背景（fillRect 白底再 drawImage）或使用 TYPE_INT_ARGB + 支持 alpha 的目标格式；检查 ImageIO.write 返回值。
- **误报排除**: 已通读 compressImageWithLimit 全流程确认 PNG 超限时必然走此路径；TYPE_INT_RGB 无 alpha 为 JDK 确定行为。

### [P3] GitRepositoryImpl.cloneRepository 关闭 Git 对象后再使用（close-after-use 反模式）

- **文件**: `nop-utils/nop-git/src/main/java/io/nop/git/impl/GitRepositoryImpl.java:122-131`
- **维度**: D2
- **证据**:
```java
Git git = gitCall(command);
IoHelper.safeCloseObject(git);       // 先关闭
LOG.info("nop.git.clone:path={},state={}", path,
        git.getRepository().getRepositoryState());   // 再使用
```
- **现状**: clone 成功后立即 close，随后又访问 `git.getRepository().getRepositoryState()` 记日志。已验证当前 JGit 7.3.0 中 `Git.close()` 不置空 repo 字段、`getRepositoryState()` 不检查 closed，所以今日不抛异常；但这是依赖实现细节的脆弱写法，JGit 升级后随时可能抛 IllegalStateException，使"已成功的 clone"报错。
- **风险**: 潜在的升级破坏点；语义混乱。
- **建议**: 先取 state 记日志，再 safeCloseObject。
- **误报排除**: 已反编译验证当前版本行为（不抛），故定 P3 而非更高。

### [P3] GitRepositoryImpl.getFileContent / getChangedFilesBetweenCommits 对 resolve 返回 null 无检查

- **文件**: `nop-utils/nop-git/src/main/java/io/nop/git/impl/GitRepositoryImpl.java:219-240、243-266`
- **维度**: D1、D8（与 getCommitDiff 的防护不一致）
- **证据**:
```java
// getFileContent:
ObjectId commitId = repository.resolve(revision);   // 非法 revision -> null
RevCommit commit = revWalk.parseCommit(commitId);   // null -> NPE/模糊错误
// 对照 getCommitDiff:
if (oldId == null || newId == null) {
    throw new NopException(ERR_GIT_INVALID_COMMIT_ID)...   // 有防护
}
```
- **现状**: 传入非法 revision 时 `resolve` 返回 null，`parseCommit(null)`/`diffFormatter.scan(null,...)` 抛 NPE，被 `handleError` 包成语义模糊的 NopException，而不是明确的 ERR_GIT_INVALID_COMMIT_ID。
- **风险**: 错误诊断体验差；同类方法行为不一致。
- **建议**: 与 getCommitDiff 对齐，resolve 后判 null 抛 ERR_GIT_INVALID_COMMIT_ID。
- **误报排除**: 已对照同文件三个方法的防护差异。

### [P3] DesktopHelper 先 getDesktop() 后 isDesktopSupported()，检查顺序失效

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/utils/DesktopHelper.java:19-29`
- **维度**: D1
- **证据**:
```java
public static void openBrowser(String url) {
    Desktop desktop = Desktop.getDesktop();   // headless/不支持时这里直接抛异常
    if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
```
- **现状**: `Desktop.getDesktop()` 在 headless 环境抛 HeadlessException、在 `isDesktopSupported()==false` 时抛 UnsupportedOperationException，后续的 isSupported 检查永远起不到保护作用。服务器（无显示）环境调用即抛异常而非静默跳过。
- **风险**: headless 服务器上调用 openBrowser 直接异常。
- **建议**: 先 `if (!Desktop.isDesktopSupported()) return;` 再 getDesktop。
- **误报排除**: JDK Desktop API 语义确定。

### [P3] TriePathRouter 重复 import

- **文件**: `nop-utils/nop-router/src/main/java/io/nop/router/TriePathRouter.java:11-17`
- **维度**: 代码风格
- **证据**:
```java
import io.nop.router.trie.MatchResult;
import io.nop.router.trie.PatternChild;
import io.nop.router.trie.Trie;
import io.nop.router.trie.TrieNode;
import io.nop.router.trie.MatchResult;   // 重复
import io.nop.router.trie.Trie;          // 重复
import io.nop.router.trie.TrieNode;      // 重复
```
- **现状**: MatchResult/Trie/TrieNode 各 import 两次。编译可通过，属明显复制粘贴残留。
- **风险**: 无功能影响，违反导入分组整洁约定。
- **建议**: 删除重复 import。
- **误报排除**: 直接可见。

### [P3] UnifiedDiffApplier.applyHunkToText 存在空 if 死分支

- **文件**: `nop-utils/nop-diff/src/main/java/io/nop/diff/UnifiedDiffApplier.java:423-430`
- **维度**: 代码风格
- **证据**:
```java
for (UnifiedDiffLine diffLine : hunk.getLines()) {
    if (diffLine.isContext() || diffLine.isDelete()) {
        // 跳过原始行（delete 或 context 都对应原始行）   <- 空 if，无操作
    }
    if (diffLine.isContext() || diffLine.isAdd()) {
        result.append(diffLine.getContent()).append('\n');
    }
}
```
- **现状**: 第一个 if 分支体为空（仅注释），只起文档作用；且此方法对 context 行使用 diff 中的内容而非原文件内容，与 `apply()`（从原文件复制 context）行为不一致（两者在 strict 校验通过时结果等价，但空白容错场景可能不同）。
- **风险**: 可读性差；两个入口的 context 行来源不一致是潜在漂移点。
- **建议**: 删除空 if，改用 if/else 链；统一 context 行取值策略。
- **误报排除**: 已确认该分支无副作用。

### [P3] ImageTypeMap 全局可变单例暴露公共修改方法，非线程安全

- **文件**: `nop-utils/nop-image/src/main/java/io/nop/image/utils/ImageTypeMap.java:8-59`
- **维度**: D3
- **证据**:
```java
public static final ImageTypeMap INSTANCE = new ImageTypeMap();
private final Map<String, String> fileExtToMimeType = new HashMap<>();   // HashMap

public void addContentTypeMapping(String fileExt, String mimeType) {
    fileExtToMimeType.put(fileExt, mimeType);   // 运行期可变共享状态
}
```
- **现状**: INSTANCE 是共享单例，`addContentTypeMapping/addMapping` 在运行期向非线程安全的 HashMap 写入；并发"一读一写"理论上可致 HashMap 内部不一致。
- **风险**: 初始化后调 add* 的并发场景（罕见）有可见性/结构破坏风险。
- **建议**: 换 ConcurrentHashMap，或只读化（构建后不可变）。
- **误报排除**: 写入口当前仓库内无调用方，实际触达概率低，定 P3。

### [P3] ShellRunner 超时销毁路径下读流 IOException 被误报为命令执行失败而非超时

- **文件**: `nop-utils/nop-shell/src/main/java/io/nop/shell/ShellRunner.java:96-126`
- **维度**: D4
- **证据**:
```java
readOutput(process, command, collector, stopped);   // 阻塞读；超时线程 safeDestroy 关闭流后可能抛 IOException
...
} catch (Exception e) {
    throw new NopException(ERR_SHELL_EXEC_COMMAND_FAIL, e).param(ARG_COMMAND, ...);
    // 未检查 stopped.get()，超时引发的 IOException 被归类为 EXEC_FAIL
}
throw new NopException(ERR_SHELL_EXEC_COMMAND_TIMEOUT)...
```
- **现状**: 超时处理器 `safeDestroy` 会关闭三个流；若主线程此时阻塞在 `read()`/`readLine()` 且被流关闭唤醒为 IOException（而非 EOF），异常进入 `catch (Exception e)` 抛 ERR_SHELL_EXEC_COMMAND_FAIL。仅在流关闭先于进程退出唤醒时发生（race），多数情况 read 因进程被杀返回 EOF 走正确的 TIMEOUT 路径。
- **风险**: 偶发的错误分类，调用方难以区分"命令失败"与"超时"。
- **建议**: catch 块中先判 `stopped.get()`，为 true 时抛 TIMEOUT。
- **误报排除**: 已分析两条唤醒路径（EOF 正常 / IOException 竞态），因依赖时序且不造成数据损坏，定 P3。

## 总体观察

- 模块整体错误处理纪律良好：全模块无空 catch、无 bare RuntimeException、无 printStackTrace，NopException + ErrorCode + .param 用法普遍规范。
- 高风险集中在两类：从外部项目移植且未适配的代码（ShellRunner 自 hadoop、OsUserHelper 自 dolphinscheduler），以及新写但缺少针对真实输入形态测试的功能（nop-diff 解析器对空行、TriePathRouter 对根路径/兜底路由、table-validator 对行级校验）——两者的现有测试都恰好绕开了缺陷输入。
- nop-fsm 的 `_gen` 4 个文件未审计（生成产物）；nop-match 大部分编译器类、nop-java-parser 的 delta 合并族仅做抽样与模式扫描，未逐行审读，如需全量覆盖建议另行安排。
