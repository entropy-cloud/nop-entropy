# ai-toolkit-skills 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-ai/{nop-ai-toolkit,nop-ai-skills,nop-ai-tools}
- 文件数: 实际约 126（src/main/java）：toolkit 73（其中 12 个为 `_gen` 生成文件，只读不改）、nop-ai-tools 19、nop-ai-skills 34（全部位于子模块 nop-ai-code-analyzer；nop-ai-translate 与 nop-ai-deepwiki 两个子模块没有 src/main/java，只有测试与 prompt 资源）
- 覆盖范围声明:
  - 深读（逐行）: toolkit 全部 21 个工具执行器（ReadFile/WriteFile/Delete/Move/Copy/ListDir/CreateDir/PatchFile/ApplyDelta/Bash/HttpRequest/GraphqlQuery/Skill/SearchFiles/SearchContent/UpdateTodos/ReadRef/AskOracle/SearchEngine 等）、fs 包全部 7 个类、sandbox 包全部 8 个类、ssrf 包 2 个类、manager/executor/api 核心（ToolManagerImpl、DefaultToolExecutorProvider、ToolExecuteContext、IToolExecuteContext）、compact 2 个类、model 层 AiToolCall/AiToolCallResult；nop-ai-tools 全部 19 个文件；skills 的 code-analyzer 核心（maven 包 11/11、stats 包 5/5、git/project 2/2、code 包核心 JavaCodeFileInfoParser/JavaFileSplitter/JavaParserBuilder/JarResolverCollection/CodeSymbolInterning 等，CodeClassInfo/CodeFunctionInfo/MavenDependencyNode 等数据类通读）。
  - 抽查: model 层 `_gen` 生成文件仅浏览未逐行；code 包剩余纯数据类（CodeCallInfo/CodeVariableInfo/CodeSymbol/AccessModifier 等）浏览；NopAiCodeAnalyzerErrors/CodeAnalyzerConstants 通读。
  - 配置面: toolkit 的 beans 配置（nop-ai-toolkit.beans）、8 个关键 .tool.xml（read-file/write-file/bash/grep/glob/skill/http-request/graphql-query）、nop-ai-tools 的 2 个 beans 配置均已对照执行器实现验证契约。
  - 范围外仅作佐证: nop-http（HttpRequest/IHttpResponse/DefaultHttpClientFactory）、nop-commons（StringHelper/FileHelper/SafeLineReader）、nop-core（XNode）、nop-ai-core（LocalFileOperator 未逐行，但 FileToolBizModel 对 projectName 做了 fileName+isValidFileName 清洗，路径逃逸面在 projectName 维度已关闭）。
  - 方法: 先全模块 grep（空 catch、printStackTrace、bare RuntimeException、ProcessBuilder/exec、HTTP 客户端、synchronized/Concurrent*），再逐文件深读，每个发现均在源码中核实行号与触发路径。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 6 |
| P2 | 7 |
| P3 | 8 |

## 发现列表

### [P0] ThoughtStorage 以客户端可控的 sessionId 直接拼接文件路径，存在路径遍历读写

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java:60-63`，配合 `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/utils/AiToolsHelper.java:9-15`
- **维度**: D5
- **证据**:
```java
// AiToolsHelper: sessionId 直接取自请求 header，无任何清洗
String sessionId = (String) ctx.getRequestHeader("nop-chat-session-Id");
if (StringHelper.isEmpty(sessionId)) {
    sessionId = StringHelper.generateUUID();
}

// ThoughtStorage:
private File getSessionFile(String sessionId) {
    Objects.requireNonNull(sessionId, "sessionId cannot be null");
    return new File(storageDir, sessionId + ".json");   // 无 ../ 清洗、无 fileName 校验
}
```
`saveSession` 走 `FileHelper.writeText`，后者调用 `assureParent(file)` 会自动创建缺失的父目录（nop-commons FileHelper.java:146-147）。
- **现状**: `SequentialThinkingBizModel.processThought/generateSummary/clearHistory` 用 header 值作为 sessionId，直接拼进 `new File(storageDir, sessionId + ".json")`。sessionId 形如 `../../../../tmp/evil` 或 `a/b/c` 时可逃逸 storageDir 任意读写 .json 文件，且会创建任意中间目录；`clearHistory` 可用空对象覆盖目标文件。写入内容是 thought 文本所在的 JSON（部分可控），读取内容经 `analyzeThought` 摘要回显。
- **风险**: 已通过 GraphQL 认证（持有 `SequentialThinking:process` 权限的聊天用户）可越权写服务器任意 `.json` 文件（覆盖配置、其他会话数据）并探测/读取任意 `.json` 路径；`../` 段还会触发目录创建。对比同模块 `FileToolBizModel.getProjectDir` 对 projectName 做了 `StringHelper.fileName()` + `isValidFileName()` 清洗，此处完全没有。
- **建议**: 对 sessionId 做与 projectName 相同的处理（`StringHelper.fileName` + `isValidFileName` 校验），或强制 UUID/十六进制白名单字符集后再拼路径。
- **误报排除**: 已核实 IServiceContext.getRequestHeader 返回客户端提交的原始 header 值；已核实 FileHelper.writeText 会 assureParent（目录创建成立）；已核实 BizModel 无任何中间清洗层。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷成立，已修复。`AiToolsHelper` 新增公共校验方法 `requireValidSessionId`（fail-closed 白名单 `^[A-Za-z0-9_-]+$`，复用 nop-ai-core 既有 `ERR_AI_SESSION_ID_IS_EMPTY`/`ERR_AI_SESSION_ID_INVALID` 错误码，与 `ChatLogHelper`/`SessionIds.requireValidIdentifier` 同一 allow-list，即报告建议的白名单方案），`makeChatSessionId` 统一走校验（processThought/generateSummary/clearHistory 三入口全覆盖）；`ThoughtStorage.getSessionFile` 在路径拼接前调用同一校验兜底（覆盖 export/import 等直接调用）。测试：`nop-ai-tools` `TestThoughtStorage#testSessionIdPathTraversalRejected`、`TestSequentialThinkingBizModel#testHeaderSessionIdPathTraversalRejected`（修复前 `../evil`、`../../tmp/evil`、`a/b`、`a\b`、`..`、绝对路径等恶意 sessionId 不抛异常且在 storageDir 外创建/覆盖 `evil.json`；修复后抛 `NopException` 且不触碰文件系统，合法 sessionId 正常读写，模块 25 测试全绿）。

### [P1] BashExecutor 解析 env 变量用了错误的节点层级，按 schema 的合法输入永远读不到 env

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:206`，对照 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/bash.tool.xml`（schema 段）
- **维度**: D1（参数绑定）/ D8
- **证据**:
```java
List<XNode> envNodes = node.childrenByTag("env");   // 在 bash 节点的直接子节点中找 env
```
而 bash.tool.xml 定义的合法结构是：
```xml
<bash id="!" explanation="" timeoutMs="int" workingDir="full-path">
    <command>!string</command>
    <envs xdef:body-type="list" xdef:key-attr="name">
        <env name="!string" value="string" />
    </envs>
</bash>
```
`XNode.childrenByTag` 只匹配直接子节点（XNode.java:1024-1034），`env` 是 `envs` 的子节点。
- **现状**: 符合 DSL schema 的调用中 `node.childrenByTag("env")` 恒为空，`parseEnv` 永远返回空 map，环境变量功能完全失效；同时 `DANGEROUS_ENV_VARS` 过滤（第 214 行）因为输入为空也形同虚设。正确写法应为 `node.childByTag("envs")` 后再遍历其 `env` 子节点。
- **风险**: 工具拿不到调用方声明的参数（env 全部丢失），命令以错误环境运行导致结果错误；纵深防御的 env 黑名单从未被实际执行。
- **建议**: 改为 `XNode envsNode = node.childByTag("envs"); if (envsNode != null) for (XNode envNode : envsNode.getChildren()) ...`，并补一个按 schema 构造工具调用的回归测试。
- **误报排除**: 已核实 XNode.childrenByTag 仅扫描直接 children；已核实 AiToolCall.getNode() 返回的是 bash 元素节点本身（AiToolCall.fromNode），不存在中间包装层。

### [P1] read-file 的 fromLine/toLine 模式返回的 totalLines 恒等于请求的 toLine，违背 DSL 明确承诺

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:135`，消费方 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ReadFileExecutor.java:57-58`
- **维度**: D1 / D8
- **证据**:
```java
// LocalToolFileSystem.readLines —— totalLines 参数位填的是请求的 toLine
return new LineResult(path, lines.isEmpty() ? 0 : toLine, fromLine,
        Math.min(toLine, fromLine + lines.size() - 1), lines);

// ReadFileExecutor
LineResult lineResult = fs.readLines(path, fromLine, toLine, 10000);
totalLines = lineResult.getTotalLines();
output.setTotalLines(totalLines);
```
read-file.tool.xml 示例 2 明确承诺：读 42 行文件的前 5 行（fromLine=1 toLine=5），响应 `totalLines="42"`。
- **现状**: 分页读取时实现返回 totalLines=toLine（示例场景下为 5）。实际文件总行数从未被统计（`int[] totalLines = {0}` 声明后从未使用，LocalToolFileSystem.java:118）。
- **风险**: AI 依据 totalLines 判断"文件已读完"，对超出 toLine 的内容漏读，产生不完整分析；与 DSL 示例直接矛盾。
- **建议**: fromLine/toLine 分支下用 `fs.countLines(path, 0)` 获取真实总行数，或当无法廉价获取时不设置 totalLines（置 null）而非填请求值；删除 readLines 中无用的 `totalLines` 数组。
- **误报排除**: 已核对 SafeLineReader 无补充 totalLines 语义；已核对 AiToolOutput.totalLines 序列化到响应（AiToolCallResult.fromNode 第 38 行）；lastLines 分支（countLines 全量计数）不受影响。

### [P1] grep 工具对"文件路径"输入静默返回空结果，DSL 声明支持"文件或目录"

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:302-305`，调用方 `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SearchContentExecutor.java:37,44`
- **维度**: D1 / D8
- **证据**:
```java
File dir = resolveFile(searchDir);
if (!dir.exists() || !dir.isDirectory()) {
    return result;    // path 是普通文件时静默返回空列表，不报错
}
```
grep.tool.xml 描述：`path：目标文件或目录路径`。
- **现状**: AI 按契约对单个文件执行 grep（如 `path="/path/Audit.java"`）时得到 status=success 的空输出，被误判为"无匹配"。
- **风险**: 工具失败静默（Anti-Silent-NoOp 违背），AI 基于假阴性结果继续推理，典型如"检查该文件是否调用了危险 API"场景得出错误结论。
- **建议**: path 为文件时直接对该文件执行 grepInFile；或至少返回显式错误"path is not a directory"。测试补一个单文件 grep 用例。
- **误报排除**: 已核实 grep.tool.xml 描述原文；已核实 SearchContentExecutor 无文件/目录区分逻辑，直接透传给 fs.grep。

### [P1] 默认配置下 SSRF 防护只有文本 pre-flight：SsrfGuardDnsResolver 从未接线，域名解析到内网/重定向跳转可绕过

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ssrf/SsrfGuardDnsResolver.java:19-32`（自身 Javadoc 已承认 Phase 1 finding）、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:73-89`；全仓 grep 确认 `SsrfGuardDnsResolver` 仅被测试引用，未出现在任何 beans.xml/autoconfig
- **维度**: D5
- **证据**:
```java
// SsrfGuardDnsResolver Javadoc:
// <li>JDK HttpClient ... does NOT consult HttpClientConfig.dnsResolver. Resolver-level
// protection does not apply; pre-flight validation in the executor is the only defense.</li>
// <li>OkHttp ... does not consult HttpClientConfig.dnsResolver.</li>

// HttpRequestExecutor.validateUrl —— 对非 IP 字面量主机只做 BLOCKED_HOSTS/localhost 文本比对，
// 不做 DNS 解析（validateHost 返回 null 放行）:
return SsrfAddressGuard.validateHost(host);
```
- **现状**: transport 层防线（可拦 DNS rebinding 与 redirect 跳转到 169.254.169.254 等）在模块内没有默认注册；pre-flight 对"域名解析到内网地址"完全盲（例如攻击者控制的 DNS 记录指向 10.x/127.0.0.1），对 HTTP 302 跳转到 metadata 端点也无检查（HttpRequestExecutor 无任何 redirect 相关处理，http-request.tool.xml 声明的 followRedirects 属性亦未实现，见 P2 条目）。
- **风险**: url 参数由 AI（可被 prompt injection 影响）提供，工具部署在云环境时可直接打云 metadata/内网服务。文本防线仅对 IP 字面量与固定主机名有效（这部分实现质量不错，含十进制/十六进制/IPv6-mapped 归一化）。
- **建议**: 在 nop-ai-toolkit.beans 中提供默认接线（依赖 Apache HttpClient 的 dnsResolver 配置 bean），或在 pre-flight 中对域名做一次受控 DNS 解析并校验全部 A/AAAA 记录；至少在文档/工具描述中标注当前缺口。
- **误报排除**: 已确认 SsrfGuardDnsResolver 无任何 beans.xml 注册点（全仓 grep）；已确认 Apache/JDK/OkHttp 三客户端对 dnsResolver 的支持差异是该 resolver Javadoc 自己写明的事实，非推测。

### [P1] DockerBashSandbox 把容器内命令的普通失败误判为 CONTAINER_START_FAILED，命令输出全部丢失

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/DockerBashSandbox.java:121-127,172-196`，消费方 `BashExecutor.java:123-128`
- **维度**: D1 / D8
- **证据**:
```java
BashSandboxFailureReason reason = classifyFailure(exitCode, stdout);
if (reason != null) {
    throw new BashSandboxException(reason, "... exited with code " + exitCode + ...);
}
// classifyFailure 尾部: 任何非零退出码兜底返回
return BashSandboxFailureReason.CONTAINER_START_FAILED;
```
BashExecutor 捕获后仅返回 `"Sandbox refused execution [CONTAINER_START_FAILED]"`。
- **现状**: 容器正常启动、命令本身非零退出（如 `ls /nonexistent` 退出 2，或命令输出恰好包含 "permission denied"）时，被当成 sandbox 故障抛异常：真实 stdout/stderr 与退出码全部丢失，错误分类错误。与 HostBashSandbox（非零退出码正常返回 failure + stdout）行为不一致，也与 bash.tool.xml 契约（"失败时（exitCode ≠ 0）将 stderr 放入 error，并提供具体退出码"）不符。
- **风险**: Docker 后端下所有失败命令都变成无诊断信息的"Sandbox refused"，AI 无法修复命令；误分类还会误导运维把用户命令失败当基础设施故障。
- **建议**: classifyFailure 仅对"可判定为启动/后端故障"的特征串返回 reason，其余非零退出码应作为正常 BashSandboxResult（exitCode + stdout）返回，由 BashExecutor.toResult 统一封装。
- **误报排除**: 已通读 execute() 全链路确认无其他分支能返回非零退出码结果；已对比 HostBashSandbox 同场景行为差异；BashExecutor 的 BashSandboxException 处理路径（仅 reason 字符串）已核实。

### [P1] JavaSourceFileFinder.getAllJavaSourceFiles 用 parallelStream 向非线程安全 ArrayList 并发写

- **文件**: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/maven/JavaSourceFileFinder.java:165-173`
- **维度**: D3
- **证据**:
```java
public List<File> getAllJavaSourceFiles() {
    List<File> results = new ArrayList<>();
    moduleStructure.getModules().values().parallelStream()   // 多线程
            .map(this::resolveModuleDirectory)
            .forEach(moduleDir -> findJavaFilesInModule(moduleDir, results));  // 共享 ArrayList.add
    return results;
}
```
- **现状**: 每个模块的目录递归在 ForkJoinPool 工作线程中执行，全部向同一个 `ArrayList` add。ArrayList 非线程安全，并发扩容会丢元素、抛 ArrayIndexOutOfBoundsException 或产生含 null 的列表。
- **风险**: 多模块项目调用该 API（如代码索引构建）时结果不完整或随机崩溃；下游对 null 元素的 NPE。
- **建议**: 收集阶段改为各模块返回独立 List 后 join（`.map(...).collect(Collectors.toList())` 再 flatMap），或用 `Collections.synchronizedList`/`ConcurrentLinkedQueue` 聚合。
- **误报排除**: 已确认 findJavaFilesInModule/findJavaFiles 递归中所有 add 都发生在 parallel 线程；findBySimpleNameInAllModules（串行路径）不受影响，仅此方法有 parallelStream。

### [P2] UpdateTodosExecutor 的 todo 列表以固定 "default" key 全局共享，跨会话互相覆盖

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/UpdateTodosExecutor.java:19,54-56`
- **维度**: D3
- **证据**:
```java
private static final Map<String, List<TodoItem>> todoLists = new ConcurrentHashMap<>();
private static final String DEFAULT_LIST_KEY = "default";

private String getListKey(IToolExecuteContext context) {
    return DEFAULT_LIST_KEY;    // context 参数被完全忽略
}
```
- **现状**: list key 固定为 "default"，static map 进程级共享；getListKey 是明显的未完成扩展点（签名接收 context 却不用）。concurrent 容器只保证 map 结构安全，`handleWrite` 的整表替换在并发会话间是丢失更新语义。
- **风险**: 同一进程内多个会话/agent 并发使用 update-todos 时互相覆盖与串读，AI 的任务清单被别的会话清空/改写。
- **建议**: 用 context 的会话标识（如 cancelToken/workDir/envs 中的 session id）作为 key；无会话上下文时至少按 workDir 隔离。
- **误报排除**: 已确认 IToolExecuteContext 无默认 session 字段可用，但 envs/workDir 可充当隔离维度；确认无其他调用方传入 listKey（handleRead/handleWrite 均内部取 key）。

### [P2] SkillExecutor 的 load 动作是空操作：返回 "loaded successfully" 但从未读取或注入任何 skill 内容

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SkillExecutor.java:76-101`
- **维度**: D8 / D1
- **证据**:
```java
if (!found) {
    return AiToolCallResult.errorResult(call.getId(), "Skill not found: " + skillName);
}
AiToolCallResult result = new AiToolCallResult();
...
output.setBody("Skill '" + skillName + "' loaded successfully.");
```
skill.tool.xml 描述承诺 "load：动态加载指定技能到当前agent上下文"；discoverSkills 也只回 `description = "Skill: " + name`，从不读取 skill 目录里的 SKILL 内容。
- **现状**: load 只做存在性检查（线性遍历 VFS /nop/skills 子目录）后返回成功消息；没有任何内容读取、没有向 context 输出任何东西。
- **风险**: AI 被告知技能已加载而实际什么都没发生（静默 no-op），与仓库自身的 Anti-Silent-NoOp 裁定精神（AskOracleExecutor 中 P2-MA1-011）冲突；list 返回的描述也无信息量（仅 "Skill: name"）。
- **建议**: load 时读取 skill 定义内容并放入 output body；或在描述与返回消息中明确"本工具仅做会话标记，内容注入由引擎层完成"。
- **误报排除**: 已通读 SkillExecutor 全文确认无隐藏的内容加载；tool.xml 示例输出与实现一致（同为 loaded 消息），故按"描述语义未兑现"而非"示例不符"定级。

### [P2] http-request 工具 DSL 声明的 followRedirects/maxRedirects/digest 认证与 redirected/url 响应字段均未实现

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:91-122,158-177,179-207`，对照 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/http-request.tool.xml`
- **维度**: D8
- **证据**:
```xml
<!-- tool.xml schema -->
<http-request ... followRedirects="boolean" maxRedirects="int">
    <auth type="enum:none|basic|bearer|digest">
```
```java
// HttpRequestExecutor：无任何 followRedirects/maxRedirects 读取；
// applyAuth 只处理 basic/bearer，digest 静默落入无操作
// buildSuccessResult 只输出 status/headers/body，无 redirected、无最终 url
```
- **现状**: AI 按契约传 `followRedirects="false"` 不会改变行为；选 digest 认证时请求实际不带任何认证发出；描述承诺的 `redirected` 与 `url` 响应字段缺失。
- **风险**: digest 静默 no-op 会让 AI 误以为已认证（请求可能被服务端以匿名身份处理，产生越权语义）；redirect 相关参数失效叠加 SSRF 条目（P1）放大重定向面。
- **建议**: 要么实现（followRedirects 映射到客户端配置、digest 报"not supported"错误、响应补字段），要么从 tool.xml schema/描述中删除这些声明，保持 DSL 与执行器一致。
- **误报排除**: 已全文 grep HttpRequestExecutor 确认无 redirect/digest 处理；已核对 nop HttpRequest 数据类（无 followRedirects 字段，属引擎级配置）。

### [P1→合并说明] grep 契约承诺的截断提示未实现，且 maxFiles 不限制实际扫描的文件数

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:311-335,337-358`，对照 grep.tool.xml 描述
- **维度**: D8 / D6
- **证据**:
```java
// 描述承诺：“达到限制时会在该文件结果末尾添加英文截断提示”、“... (more files not shown, found 10 files, ...)”
// 实现：
int filesWithMatches = 0;
for (File file : files) {
    if (filesWithMatches >= maxFiles) break;   // 只数“有匹配的文件”，其余文件全部照读
    ...
    grepInFile(file, ...);                      // 每个文件都完整读行直到无匹配或达 maxMatches
```
- **现状**: ① 无任何截断提示文本，AI 无法区分"恰好 N 个匹配"与"被截断"；② maxFiles 只限制"有匹配的文件数"，无匹配的文件会被逐个完整读取（含大文件/二进制文件按 UTF-8 替换字符读），在 10 万文件的目录里即使 maxFiles=10 也可能全量 IO；③ 每行读取上限 65536 字符但行数无上限。
- **风险**: 截断信息缺失导致 AI 误判搜索完备性；扫描无上限在工具线程池里造成长尾阻塞与磁盘压力。
- **建议**: 实现 tool.xml 承诺的截断提示；为 grep 递归引入"已扫描文件数/字节数"硬上限；跳过明显二进制文件。
- **误报排除**: 已通读 grepRecursive/grepInFile 与 SearchContentExecutor 输出拼接逻辑，确认无提示文本；描述原文已引用。

### [P2] glob 工具在指定 directory 时返回相对路径而非契约承诺的绝对路径，maxResults 默认值与描述不符

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:262-294`，调用方 `SearchFilesExecutor.java:32-36`，对照 glob.tool.xml
- **维度**: D8
- **证据**:
```java
// globRecursive 只累积相对 directory 的 relPath
String relPath = relativePath.isEmpty() ? path.getFileName().toString()
        : relativePath + "/" + path.getFileName().toString();
```
glob.tool.xml：`若指定了 directory（绝对路径），返回包含 directory 的绝对路径`；`maxResults：最大返回行数（可选，默认无限制）`。
- **现状**: 指定 directory="/var/log" 时返回 `app/access.log` 而非 `/var/log/app/access.log`（示例展示的是绝对路径）；maxResults 实现默认 100（`call.attrInt("maxResults", 100)`），描述称默认无限制。
- **风险**: AI 拿到相对路径后无法直接定位文件（不知道基准目录），后续 read-file 调用可能拼错路径；默认 100 与"无限制"的差异造成静默截断且无提示。
- **建议**: directory 指定时以绝对路径输出（或前缀 directory）；修正 tool.xml 描述与实现其一，并在截断时提示。
- **误报排除**: 已核对 SearchFilesExecutor 直接输出 file.getPath()（即 relPath）；globRecursive 中无任何绝对化处理。

### [P2] HttpRequestExecutor 对响应体无大小限制，超大响应全量进入内存与 AI 上下文

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:179-207`
- **维度**: D2 / D6
- **证据**:
```java
IHttpResponse response = httpClient.fetch(request, null);
...
String body = response.getBodyAsString();   // IHttpResponse 为 byte[] 全量模型
sb.append(body != null ? StringHelper.escapeJson(body) : "");
```
- **现状**: 没有任何 maxChars/截断逻辑；read-file 工具有 DEFAULT_MAX_CHARS=100000 与 SafeLineReader 截断机制，HTTP 工具没有对称防护。timeout 有（30s 默认），但响应大小不限。
- **风险**: AI 请求大文件/无限流端点（或恶意端点故意返回超大 body）时内存放大 + 工具结果把整个响应塞进对话上下文，直接挤爆上下文/产生高额 token 消耗。
- **建议**: 对 body 做上限（如复用 100KB 默认）并加 truncated 标记；headers 同理只保留常见头。
- **误报排除**: 已确认 IHttpResponse 接口为 byte[] 全量模型（getBodyAsBytes），fetch 返回时 body 已全读入内存，截断只能在 executor 侧做。

### [P2] GraphQLToolProvider 对不存在的操作名抛 NPE（配置错误时无诊断信息）

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/graphql/GraphQLToolProvider.java:88-100`
- **维度**: D1 / D4
- **证据**:
```java
protected ToolSpecification loadToolSpec(String operationName) {
    ToolSpecification spec = ToolSpecificationLoader.loadSpecification(operationName);
    if (spec != null) { return spec; }
    spec = new ToolSpecification();
    GraphQLFieldDefinition op = graphqlEngine.getOperationDefinition(null, operationName);
    spec.setDescription(op.getDescription());   // op 可能为 null → NPE
```
- **现状**: `nop.ai.tools.graphql-tool-names` 配置了不存在的操作名（拼写错误/权限裁剪后的 schema）时，`getOperationDefinition` 返回 null，直接 NPE，且发生在 `cache.computeIfAbsent` 内。
- **风险**: 启动期/首调用期以裸 NPE 失败，无"unknown graphql operation"提示，排障成本高；computeIfAbsent 内抛异常会传播给 getToolsByPrefix 全部失败。
- **建议**: op 为 null 时抛带操作名参数的 NopException（模块已有 NopException 使用惯例）。
- **误报排除**: 已确认 loadSpecification 返回 null 的分支正是为"spec 不存在"设计的兜底路径；工具名来源为 beans 配置集合（GraphQLToolSetFactoryBean.buildToolSet），非终端用户输入，故定 P2 而非 P1。

### [P2] BashExecutor 危险环境变量黑名单用全名匹配，BASH_FUNC_ 前缀形式可绕过

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:44-49,213-217`
- **维度**: D5
- **证据**:
```java
private static final Set<String> DANGEROUS_ENV_VARS = Set.of(
        "LD_PRELOAD", ..., "BASH_ENV", "BASH_FUNC_", ...);

String upperName = name.toUpperCase();
if (DANGEROUS_ENV_VARS.contains(upperName)) {   // 全名匹配
    LOG.warn("BashExecutor: rejecting dangerous env var {}", name);
    continue;
}
```
- **现状**: bash 函数注入的载体名形如 `BASH_FUNC_foo%%`，`contains("BASH_FUNC_FOO%%")` 为 false，绕过黑名单；集合里放 `BASH_FUNC_` 显然意图是前缀匹配但实现是全名匹配。
- **风险**: HostBashSandbox（显式 opt-in 的宿主执行）+ sh 链接到 bash 的系统上，可通过 env 完成函数注入（纵深防御层失效；注意当前 env 解析本身有 P1 缺陷，两条一起修才有效）。
- **建议**: BASH_FUNC_ 改为 `upperName.startsWith("BASH_FUNC_")` 判断；顺带补 `ENV`/`POSIXLY_CORRECT` 等向量。
- **误报排除**: 已核实无其他前缀匹配逻辑；DANGEROUS_ENV_VARS 中其余条目均为全名语义，仅 BASH_FUNC_ 是前缀语义混入全名匹配。

### [P3] ToolManagerImpl.loadTool 直接拼接 toolName 进 VFS 组件路径，无合法性校验

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:133-137`
- **维度**: D5 / D1
- **证据**:
```java
public AiToolModel loadTool(String toolName) {
    String path = "/nop/ai/tools/" + toolName + ".tool.xml";
    return (AiToolModel) ResourceComponentManager.instance().loadComponentModel(path);
}
```
- **现状**: toolName 来自上层（agent 引擎以 AI 输出的工具名调用，见 nop-ai-agent AgentPromptAssembly），`../` 会被 VFS 规范化后逃出 /nop/ai/tools 加载其他组件模型，再被强转 `(AiToolModel)`，非 tool 模型时抛无上下文 ClassCastException。
- **风险**: 影响限于 VFS（classpath）内读取 + 异常，不算文件系统逃逸；主要是不健壮与错误信息差。
- **建议**: 校验 toolName 匹配 `[a-zA-Z0-9_-]+` 再拼接，或捕获 cast 失败给出"unknown tool"错误。
- **误报排除**: 已确认调用方 nop-ai-agent 传入的 toolName 源自 AI 生成的调用而非白名单（AgentPromptAssembly.java:124,178）；VFS 路径规范化行为属平台通用逻辑，故仅定 P3。

### [P3] HttpRequestExecutor 的 URL_WHITELIST_PATTERN 是死代码，实际校验走另一套 URI 解析

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java:30-31`
- **维度**: D4 / 维护性
- **证据**:
```java
private static final Pattern URL_WHITELIST_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+(:\\d+)?(/.*)?$");
```
- **现状**: 全文无任何引用；实际校验在 validateUrl（URI 解析 + SsrfAddressGuard）。两套规则并存会误导维护者以为白名单生效。
- **风险**: 维护性风险：修改正则不会产生任何效果。
- **建议**: 删除死代码，或在 validateUrl 中真正启用。
- **误报排除**: 已全文 grep 确认仅定义处一次出现。

### [P3] CodeLineAnalyzer 把单行块注释 `/* ... */` 计为代码行，读取失败时伪造 1/1 统计

- **文件**: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/CodeLineAnalyzer.java:45-67,68-72`
- **维度**: D1 / D4
- **证据**:
```java
boolean isComment = detectComment(trimmedLine, extension, inBlockComment);  // java 只认 "//"
if (trimmedLine.contains("/*") && !isStringLiteral(line, "/*")) { inBlockComment = true; }
if (trimmedLine.contains("*/") && inBlockComment) {
    inBlockComment = false;
    ... if (!afterComment.isEmpty()) { isComment = false; }
}
if (isComment || inBlockComment) stats.commentLines++;   // 单行 /* x */ 此时两者皆 false
else stats.codeLines++;
...
} catch (Exception e) {
    stats.totalLines = 1; stats.codeLines = 1;   // 读取失败伪造数据
}
```
- **现状**: `/* foo */` 单行块注释行：状态机开合后复位且 isComment 从未置 true，落入 codeLines；catch 分支把不可读文件报成 1 行 1 代码行（吞异常且数据造假，0/0 更诚实）。
- **风险**: 语言统计（GitHub Languages 风格）系统性偏差；异常静默。
- **建议**: 单行同时含 `/*`...`*/` 时置 isComment=true；catch 分支返回全零并 debug 日志。
- **误报排除**: 已按状态机逐步推演 java 扩展名路径；CSS 分支因 detectComment 认 `/*` 不受影响。

### [P3] JavaCodeFileInfoParser 解析失败时 orElseThrow() 抛无上下文的 NoSuchElementException

- **文件**: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/JavaCodeFileInfoParser.java:77`
- **维度**: D4
- **证据**:
```java
CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow();
```
- **现状**: 语法错误严重的 Java 文件 parse 结果为空时抛 `NoSuchElementException`（无消息），丢失文件路径与 parse problem 明细（ParseResult 的 problems 列表被丢弃）。
- **风险**: 批量索引任务遇损坏文件时报无意义异常，无法定位是哪个文件。
- **建议**: 空结果时抛 NopException 附文件路径，或取 `parse.getProblems()` 摘要。
- **误报排除**: 已确认 javaparser 对致命语法错误 getResult() 返回空 Optional 的行为；caller 无兜底 try-catch（JavaCodeFileInfoGenerator.generateFile 直接传播）。

### [P3] LocalToolFileSystem.grepInFile 吞 IOException 仅 debug 日志，扫描中不可读文件无痕迹

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:337-358`
- **维度**: D4
- **证据**:
```java
} catch (IOException e) {
    LOG.debug("Failed to read file for grep: {}", filePath);
}
```
- **现状**: 权限/IO 故障的文件在 grep 中被静默跳过，日志级别 debug 生产默认不可见；与同文件其他方法（readText 等抛 NopException）风格不一致。
- **风险**: 大目录扫描部分失败时结果不完整且无告警，AI 与运维都无从察觉。
- **建议**: 至少 warn 级别并计入截断/失败统计。
- **误报排除**: 已确认无重试或其他日志分支；grep 场景跳过单文件是合理策略，问题仅在日志级别。

### [P3] ToolManagerImpl 并行/串行执行的异常语义不一致，异常路径绕过 afterCall 拦截器

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/manager/ToolManagerImpl.java:36-58,78-96`
- **维度**: D3 / D8
- **证据**:
```java
return executor.executeAsync(call, context)
        .thenApply(result -> {                       // executeAsync 异常完成时跳过
            for (IToolCallInterceptor interceptor : interceptors) {
                interceptor.afterCall(toolName, call, context, result);
            ...
// executeParallel 中:
results.add(f.join());                               // 某个 future 异常完成时 CompletionException 传播
// executeParallel 的 for 循环里 callTool(...) 同步抛出时直接冒出 callTools
```
- **现状**: executor future 异常完成时 afterCall 拦截器不被调用（观测缺口）；并行模式下一个工具异常导致整个 allOf 失败（而非逐工具 error result），串行模式下 callTool 在循环外同步调用（executeParallel 的 for 中）可同步抛出，两种模式失败面不同。
- **风险**: 拦截器审计不完整；批量调用的错误隔离不一致。当前各 executor 内部普遍 catch Exception 返回 errorResult，故触达面窄。
- **建议**: executeAsync 结果统一 `exceptionally` 包装为 failure result；executeParallel 循环内用 CompletableFuture.supplyAsync 包裹 callTool。
- **误报排除**: 已核实所有 toolkit 内 executor 的 doExecute 都有 catch(Exception) 兜底，故定 P3；异常路径需依赖自定义 executor 或拦截器抛错才可达。

### [P3] JavaFileSplitter 按方法签名（不含类名）去重，跨类同名方法在分块间重复出现

- **文件**: `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/JavaFileSplitter.java:122-143`
- **维度**: D1
- **证据**:
```java
MethodFilter(List<MethodDeclaration> methodsToKeep) {
    this.keepSignatures = methodsToKeep.stream()
            .map(md -> md.getSignature().asString())   // "foo(int)" 不含所属类
            .collect(Collectors.toSet());
}
```
- **现状**: 同一编译单元中两个类（含嵌套类）存在同名同参方法时，chunk A 的保留集合也会命中 chunk B 类中的方法（clone 树未删除），方法内容跨 chunk 重复。
- **风险**: RAG 分块内容重复（不丢失，仅冗余），影响嵌入与检索质量。
- **建议**: key 加入所属类限定名（遍历时记录 classDecl 链）。
- **误报排除**: 已确认 JavaParser 的 MethodDeclaration.getSignature().asString() 不含 declaring class；createChunkCompilationUnit 对整棵 CU clone 后过滤，跨类同名必然命中同一签名集合。

### [P3] DockerBashSandbox/HostBashSandbox 按字节截断输出，可能切断 UTF-8 多字节字符

- **文件**: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/sandbox/HostBashSandbox.java:111-137`（DockerBashSandbox.java:238-264 同款）
- **维度**: D1
- **证据**:
```java
int take = Math.min(n, remaining);
captured.append(new String(buf, 0, take, StandardCharsets.UTF_8));  // take 可落在多字节字符中间
```
- **现状**: maxBytes 截断点在任意字节边界，中文等 3 字节字符被切半产生替换字符。
- **风险**: 截断尾部出现乱码（影响可读性，不影响安全）。
- **建议**: 截断时回退到最后一个完整 UTF-8 序列边界。
- **误报排除**: 已确认无后续的清洗逻辑；两个 sandbox 后端实现相同问题。

## 补充说明（未列为发现的事项）

- **路径逃逸防护总体良好**: LocalToolFileSystem.isPathAllowed 用 canonical path + 按路径段比较的 `StringHelper.pathStartsWith`（已核实实现，`/work/dirX` 不会误匹配 `/work/dir`），`..` 与符号链接逃逸均被 canonical 化拦截；BashSandboxPaths 的 workingDirectory jail（拒绝 `..`、toRealPath 校验、allowedBaseDirs 空则拒绝）fail-closed 设计正确；BashExecutor 无后端时 fail-closed 拒绝执行（beans.xml 默认未 wire sandbox，bash 工具默认不可用，符合设计）。
- **D7（Nop 约定）未发现违背**: 三个模块所有 `@Inject` 均为 public setter 注入（无 private 字段注入）；bean 均在 `_vfs` 下 beans.xml 显式定义（含 `ioc:collect-beans` 聚合、`ioc:condition` 条件注册）；配置值用 `@InjectValue("@cfg:...")`；错误处理基本遵循两档策略（模块内 NopAiException/BashSandboxException，公共 API NopException+ErrorCode+.param()），grep 出的 IllegalArgumentException 均为参数校验且带 cause 或明确消息，未发现 bare RuntimeException。未发现空 catch 块与 printStackTrace。
- **SkillExecutor 的 load/list 对 VFS `/nop/skills` 的访问不存在宿主文件系统路径逃逸**（VirtualFileSystem 边界内）。
- **AskOracleExecutor 的"未实现即显式失败"**（P2-MA1-011 裁定）与 **ThoughtStorage 保留文件存储**（P3-MA1-013 裁定）均为有意决策，未列为发现（但 ThoughtStorage 的 sessionId 路径拼接不受该裁定保护）。
- nop-ai-skills 的 nop-ai-translate、nop-ai-deepwiki 两个子模块无 src/main/java 代码（仅测试与 prompt 资源），本次无实现代码可查。
