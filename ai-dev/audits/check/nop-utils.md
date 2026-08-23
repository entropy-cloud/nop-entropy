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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认成立并已修复。将 `UnifiedDiffLine` 构造函数中 `Guard.notEmpty(content, "content")` 改为 `Guard.notNull`（unified diff 中空行即单个前缀字符，空串 content 合法）。测试：`nop-utils/nop-diff` `TestUnifiedDiffParser#testParseDiffWithBlankLines`（修复前解析含空 context/delete/add 行的 diff 抛 `IllegalArgumentException: IsEmpty:content`）、`TestUnifiedDiffParser#testFromDiffStringBlankLine`（修复前 `fromDiffString(" ")` 等同样抛异常）。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 确认成立并已修复。`ShellCommand.create` Unix 分支改为 `sh -c <原始命令串>`（命令串作为单个参数由 shell 解析，不再走 `splitCommandLine` 逐词追加；Windows 分支保持 `cmd /c` + split 参数不变）。测试：`nop-utils/nop-shell` `TestShellCommand#testCreateUsesShellDashCOnUnix`（修复前 Unix 下构造出 `[sh, echo, hi]`，缺 `-c`）、`TestShellCommand#testRunSingleStringCommand`（修复前 `echo hi` 实际执行为 `sh echo hi`，退出码 126 执行失败；Windows 上跳过真命令执行）。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`getWorkingTreeDiff` 重写为 HEAD 树（`CanonicalTreeParser.reset(reader, treeId)`）vs 工作区迭代器（`FileTreeIterator`）的 `diffFormatter.scan`，符合接口"工作区与最新提交的差异"契约；`ObjectReader`/`RevWalk` 纳入 try-with-resources，删除 `DirCacheIterator` blob 当 tree 的错误用法。红验证：`TestGitRepositoryImpl#testGetWorkingTreeDiff` 修复前抛 `NopWrapException` 包装 `IncorrectObjectTypeException: Object ... is not a tree`（index 有条目时）、空 index 场景 NPE，与审计推演一致；修复后 MODIFY/ADD（含未跟踪文件）均正确返回，`testGetWorkingTreeDiff_cleanTree` 验证干净工作树返回空列表。附带超审计新发现一并修复：`commit(message, author)` 传 null email 给 JGit `PersonIdent` 必抛 `IllegalArgumentException`（commit 本身不可用），改为作者名派生占位邮箱。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。写入 `inputBytes` 后在 try/finally 中 `IoHelper.safeClose(process.getOutputStream())`，子进程读到 EOF 正常退出。红验证：`TestShellRunner#testRunWithInput`（`cat` + input("hello") + 10s timeout）修复前挂起至超时抛 `ERR_SHELL_EXEC_COMMAND_TIMEOUT`（审计预测的失败形态），修复后退出码 0 且输出含 hello。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`validateRow` 按 `columnNames` + `rowAdaptor` 构建行数据 Map（含 rowIndex）：有外部上下文时用 `scope.newChildScope(rowVars)`（行数据覆盖同名上下文变量、外部变量仍可见，且不再污染外部 scope），无上下文时 `BeanVariableScope(rowVars)`；删除从未调用的死方法 `buildRowScope`。红验证：`TestTableValidatorEngine#testRowValidatorResolvesColumns`（修复前不通过）、`#testRowValidatorWithEvalContext`（valueName 引用外部变量 minAge）。注意：旧代码在到达审计描述的"每行误报"形态之前就先崩在两处更早的缺陷上——红测试实际失败形态为 `UnsupportedOperationException`（`RowWiseCollector.addError` 对 `ModelBasedValidator` 传入的 `Collections.emptyMap()` 直接 put）与 `ERR_FILTER_UNKNOWN_OP op=condition`（compiler 不解包 condition 包裹节点）。这两处超审计新发现已一并修复：RowWiseCollector 复制为可变 Map 再补 rowIndex；`TableValidatorCompiler.unwrapCondition` 单子节点解包、多子节点包 AND。新增 `#testRowValidatorFromModelCompiler` 覆盖模型+compiler 的真实用户路径（修复前红）。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复，且根因比审计描述更深。live 复核发现（探针实证）：`MyersDiffAlgorithm.computeDiff` 按编辑路径回溯产出的 change 列表是**文档逆序**（INSERT 在前、DELETE 在后，且相邻 DELETE 因逆序未被 `mergeChanges` 合并），而 `generateHunks` 假设正序遍历（`lastEndOriginal` 单调、hunk 合并、偏移累计都依赖它）——多 change 场景下整个 hunk 结构错乱，不止 newStartLine。修复三件套：（1）`generateUnifiedDiff` 先按 `startOriginal` 排序；（2）revised 偏移改为前缀累计（各 change 的 revisedSize-originalSize 之和，按 index 记账，不能用 endRevised-endOriginal 逐个累加——Myers 相邻 change 的 revised 坐标已互相折算会重复计；（3）hunk 合并重建改为横跨两端的 CHANGE 块（旧逻辑把前一个 change 的增删行当上下文重发，超出审计的新发现）。红验证：`TestTextDiffUtils#testMultiChangeHunkNewStartLineAccountsForPriorOffset`（修复前 hunk 数与 newStartLine 全错：期望 2 个 hunk 实得 1 个）与 `#testMergedHunksKeepChanges`（修复前 `ERR_DIFF_APPLY_CONTEXT_MISMATCH`，合并重建丢改动），修复后 @@ 头部行号正确且 roundtrip 还原 revised 文本。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。静态 `JAVA_PARSER` 实例改为静态不可变 `ParserConfiguration` + 每次 `format` 调用 `newParser()` 新建实例（与模块内 `JavaParseTool`/`DeltaJavaMerger` 用法一致）。红验证出乎意料地确定性：`JavaParserCodeFormatterTest#testConcurrentFormat`（8 线程 × 50 次交替格式化两个源文件）在共享实例下 3/3 次运行必失败，形态为 `GeneratedJavaParserTokenManager` 内部状态损坏（`Index -1 out of bounds for length 4096`）→ `ERR_JAVA_PARSER_PARSE_FAILED`，修复后绿。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`Trie.match/matchAll/matchAllValues` 空 path 时改为检查 rootNode 的值（注册与查询恢复对称：无根注册仍返回 null/空集）。红验证：`TestTriePathRouter#testRootPathPattern`（修复前 `matchPath("/")` 返回 null）、`#testRootPath_noRootRegistered`（负向用例保持 null 语义）。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`addMatchAll` 改用 `"{*path}"` 通配段（varNames `["*path"]`、tillEnd=true，与 `addPathPattern("/{*path}", value)` 语义一致）。红验证：`TestTriePathRouter#testAddMatchAll`（修复前 `matchPath("/a/b/c")` null）。处置中另发现并修复超出审计的关联缺陷：`Trie._match` 沿精确子树走到死路时不回退祖先层的 tillEnd 通配——正是 GatewayModel"默认路由 + 精确路由"混合场景（请求 `/api/orders` 只有 `/api/users` 精确前缀时兜底仍 404），已在 `_match` 精确分支未命中时增加祖先层 tillEnd 回退；`#testAddMatchAll_withExactPriority` 修复前 NPE（红），并验证精确匹配仍优先。回归：nop-gateway 68 tests 绿。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。按 POSIX+Windows 混合约定重写转义规则：单引号内反斜杠为字面量；双引号内/外仅 `\\`、`\"`、`\'` 三种被转义，其余（如 Windows 路径分隔符）保留反斜杠字面量；行尾悬空反斜杠抛 `IllegalArgumentException`（原 escaped 标志机制移除，改前瞻式）。红验证 4/4：`TestShellCommand#testSplitCommandLineWindowsPathPreserved`（修复前 `C:\tools\bin`→`C:toolsbin`，与审计例子一致）、`#testSplitCommandLineQuotedWindowsPath`、`#testSplitCommandLineSingleQuoteKeepsBackslashLiteral`、`#testSplitCommandLineDanglingBackslashRejected`（修复前不抛）；`#testSplitCommandLineEscapedQuoteAndBackslash`/`#testSplitCommandLinePlainArgs` 保证常规拆分行为不回归。注：P0-2 修复后 Unix 分支走 `sh -c <整串>`，本方法现实上仅服务 Windows `cmd /c` 分支，字面量保留规则与其语义匹配。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。（1）`charAt(i)`→`charAt(j)`；（2）`getGroup` 改读 `getOutput()`（超审计新发现：原读 `.getError()`，而 `runCommand` 开启 `redirectErrorStream` 后 error 恒空，`split("\n")[22]` 必 AIOOBE）并按 "Local/Global Group Memberships" 标签行定位解析（新纯函数 `parseWindowsGroupFromNetUserOutput`，无法定位抛 IOException 而非越界）；（3）create* 系列统一 `checkRunResult` 校验退出码，非 0 抛 IOException，`createUser` 不再无条件返回 true。红验证说明：Windows 平台路径在 macOS 验证机上无法真实执行，免红测试；新增纯函数测试 `TestOsUserHelper` 3 个（英文输出解析/Global 行/中文 locale 输出与空输出抛 IOException）+ getSudoCmd 2 个。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。（1）`createMacUser` 日志改为 `sudo sysadminctl -addUser {} -password ***`，密码不再明文进日志；（2）`getSudoCmd` 对 tenantCode 做白名单校验（`isSafeTenantCode`：仅字母/数字/下划线/中划线），非法值 `Guard.checkArgument` 抛 IllegalArgumentException（含空格/分号/`$()`/反引号的注入尝试均被拒绝）。红验证：`TestOsUserHelper#testGetSudoCmd_rejectsInjection` 修复前 "Expected IllegalArgumentException to be thrown, but nothing was thrown"；`#testGetSudoCmd_validTenant` 验证合法值与空值行为不变。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，按最小方案修复：catch 中增加 `LOG.warn("nop.git.ls-remote-fail:url={}", url, e)`，网络/认证/超时的原因不再被完全吞掉，操作者可据此区分"仓库不存在"与基础设施故障。boolean 返回契约保持不变（三态/抛异常变体属接口设计变更，超出本战役最小修复范围）。免红测试：纯日志修复，无数值行为语义变化（返回值不变）。按异常类型精确区分"不存在"依赖 JGit 异常消息细节（TransportException 文案），长期产品化如需三态语义应扩展 IGitRepositoryManager 接口，不在本条处置内。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`BetweenMatchPattern.matchValue` 与 `AlwaysFalseMatchPattern.matchValue` 的 `state.buildError(...)` 链末尾补上 `.addToCollector(state.getErrorCollector())`，与同族 13 个 pattern 行为对齐。红验证 2/2：`TestMatchPattern#testBetweenCollectError`/`#testAlwaysFalseCollectError` 修复前 collector 收到 0 条错误（expected: <1> but was: <0>），修复后 1 条且 errorCode 正确（ERR_MATCH_BETWEEN_CHECK_FAIL / ERR_MATCH_ASSERT_OP_MATCH_FAIL）。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复，但与模块既有 fuzzy 语义做了显式调和。`normalizeLine` 默认分支返回原字符串（保留前导缩进），`ignoreTrailingWhitespace` 分支改为仅去行尾空白（原 `.trim()` 连前导也去，选项语义同样不准）。fuzzy 定位是显式开启的宽松模式（既有测试 `testFuzzyMatchWithTrim` 注释"默认会 trim"编码了该意图），故 fuzzy 路径（extractContextLines/findContextPositions/tryMatchHunkFromFirstContext）分离出独立的 `fuzzyNormalize`（全 trim），且 fuzzy 已验证 context/delete 行后不再重复 strict 校验——既满足审计"strict 模式实际严格"的要求，又不破坏 fuzzy 的容错定位语义。红验证：`TestUnifiedDiffApplier#testStrictContextRejectsIndentMismatch`（diff context 无缩进 vs 文件 4 空格缩进）修复前静默通过（nothing thrown），修复后抛 `ERR_DIFF_APPLY_CONTEXT_MISMATCH`；`#testIgnoreTrailingWhitespaceStillTolerated` 验证选项开启时行尾空白仍被容忍。既有 18 个 applier 测试（含 3 个 fuzzy 测试）保持绿。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`resizeImage` 绘制前 `g2d.setColor(Color.WHITE); g2d.fillRect(0,0,w,h)` 铺白底，透明像素合成白色而非黑色；`bufferedImageToBytes` else 分支补 `ImageIO.write` 返回值检查，找不到 writer 时抛 IOException 而非返回空 byte[]。红验证：`TestImageCompressHelper#testTransparentPngCompressedWithWhiteBackground`（200×200 左半透明右半噪声 PNG，maxSize=png 一半强制走压缩路径）修复前左上角像素 rgb=0,0,0（黑），修复后 >200,200,200（白）。write 返回值检查子项为防御性加固：无 writer 的格式在 `readImage` 阶段已失败，无可达测试路径，注明免测试。附带：nop-image 原无测试基建，pom 补 junit-jupiter test 依赖。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。调整为先取 `getRepositoryState()` 记日志、后 `safeCloseObject(git)`。免红测试：纯语句顺序调整，当前 JGit 版本下行为等价（日志内容不变），消除的是升级后 `IllegalStateException` 的脆弱性，无数值行为语义变化。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`getFileContent` 在 `resolve` 后判 null 抛 `ERR_GIT_INVALID_COMMIT_ID`（带 revision 参数）；`getChangedFilesBetweenCommits` 与 `getCommitDiff` 对齐，oldId/newId 任一为 null 抛同错误码（带 oldCommit/newCommit 参数）。红验证：`TestGitRepositoryImpl#testGetFileContentInvalidRevision`（修复前 NPE `Cannot invoke AnyObjectId.hashCode() because id is null` 被包成语义模糊的 NopWrapException）与 `#testGetChangedFilesBetweenCommitsInvalidRevision`，修复后错误码断言通过；前者同时验证合法 `HEAD~1` 正常取回历史内容。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。改为先 `if (!Desktop.isDesktopSupported()) return;` 再 `getDesktop()`，headless 服务器静默跳过而非抛 HeadlessException。测试 `TestDesktopHelper#testOpenBrowserHeadlessDoesNotThrow` 用 `assumeTrue(GraphicsEnvironment.isHeadless())` 守卫（避免非 headless 环境真打开浏览器）。红验证说明：验证机为带显示的 macOS，测试在本机跳过（skipped），红形态需 headless CI 环境方可复现（修复前 `Desktop.getDesktop()` 在 headless 下必抛 HeadlessException，JDK API 语义确定）；本地尝试以 `-DargLine` 强制 headless 未生效（surefire fork 未传播），如实记录。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。删除 MatchResult/Trie/TrieNode 三个重复 import。免测试：编译级清理，无行为语义。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。删除空 if 死分支改为 if/else 链；同时统一 context 行取值策略——`applyHunkToText` 的 context 行改为从原文件复制（与 `apply()` 一致，保留原文件空白），delete 行只前进行号、add 行写 diff 内容。红验证：`TestUnifiedDiffApplier#testApplyHunkToTextCopiesContextFromOriginalFile`（原文件首行 "header␠␠␠"、diff context 行 "header"，ignoreTrailingWhitespace 下校验通过）修复前输出丢失行尾空白（用 diff 内容），修复后保留原文件空白。

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

---

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。三个 Map 全部改为 `ConcurrentHashMap`（存储值均非 null，兼容 CHM 约束），`addContentTypeMapping`/`addMapping` 的运行期写入不再有 HashMap 结构破坏风险。免红测试：并发结构加固，无法以确定性单测复现 HashMap 损坏；读写行为（键值集合不变）由现有调用方语义保持。

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

> **处置（fix-ai-check 分支，2026-08-22）**: 属实，已修复。`run` 的 catch (Exception) 块先判 `stopped.get()`，为 true 时抛 `ERR_SHELL_EXEC_COMMAND_TIMEOUT`（保留原异常为 cause），仅命令自身失败才抛 `ERR_SHELL_EXEC_COMMAND_FAIL`；`stopped` 变量提前到 try 外声明以便 catch 可见。免红测试：竞态取决于超时线程关流与主线程 read 阻塞的时序交错，无法确定性复现（多数情况 read 因进程被杀返回 EOF 走正确 TIMEOUT 路径）；修复为纯异常分类逻辑，不改变任何执行路径的数据结果。

## 总体观察

- 模块整体错误处理纪律良好：全模块无空 catch、无 bare RuntimeException、无 printStackTrace，NopException + ErrorCode + .param 用法普遍规范。
- 高风险集中在两类：从外部项目移植且未适配的代码（ShellRunner 自 hadoop、OsUserHelper 自 dolphinscheduler），以及新写但缺少针对真实输入形态测试的功能（nop-diff 解析器对空行、TriePathRouter 对根路径/兜底路由、table-validator 对行级校验）——两者的现有测试都恰好绕开了缺陷输入。
- nop-fsm 的 `_gen` 4 个文件未审计（生成产物）；nop-match 大部分编译器类、nop-java-parser 的 delta 合并族仅做抽样与模式扫描，未逐行审读，如需全量覆盖建议另行安排。
