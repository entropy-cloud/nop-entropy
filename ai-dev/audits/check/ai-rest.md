# ai-rest 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-ai/{nop-ai-gateway,nop-ai-shell,nop-ai-coder,nop-ai-dao,nop-ai-service,nop-ai-maven,nop-ai-rag,nop-ai-dsl-orm,nop-ai-tools,nop-ai-mcp-server,nop-spring-mcp-server,nop-spring-mcp-server-support,nop-ai-app}
- 文件数: 约 201（src/main/java，剔除 `_gen` 后实际为 176 + tools/mcp-server/spring/app 等小模块 25；任务描述中的文件数含测试与资源，与本计数口径不同）
- 覆盖范围声明:
  - **逐行深读**: nop-ai-shell 全部核心类（executor/parser/lexer/checker/io/commands/registry）；nop-ai-gateway 核心（ChannelMessageServiceImpl、ChannelConnectorManager、ChannelSessionStoreImpl、FeishuConnector、ChannelLoginApiBizModel、AiGatewayFailoverInterceptor、FailoverProbeSupport、GatewayStreamingRetryCallback、GatewayStreamingLifecycleListener、AiDialectBackendMessageConverter、ChatServiceFailoverAdapter 主体）；nop-ai-coder 全部文件写路径（AiCoderHelper、DslToolImpl、JavaMethodReplacer）；nop-ai-tools（FileToolBizModel、AiToolsHelper、SequentialThinkingBizModel、ThoughtStorage）；nop-ai-mcp-server AiFileTool；nop-ai-service（AiModelCredentialResolverImpl、NopAiModelBizModel、NopAiChatResponseBizModel + 15 行生成 stub 抽查）；nop-ai-maven（DeltaWorkspaceReader、DeltaVirtualFileSystem）；nop-ai-dsl-orm GptOrmModelParser；nop-ai-dao 非生成类（biz 接口、dto、constants、entity 薄壳）。
  - **模式扫描 + 抽查**: 全目标模块 grep（空 catch / bare RuntimeException / printStackTrace / ProcessBuilder / new File / 路径拼接 / synchronized）后逐点验证；gateway failover 的 FailoverStreamFlow（543 行，本地形态）与 FailoverMetricsImpl/CircuitObservation 仅做并发与异常模式扫描，未逐行；shell 的 model 纯数据类（Token/Redirect/EnvVar 等）与 io 剩余实现类（ListShellInput/Output、PrintStreamShellOutput 等）快速过读。
  - **关联依赖抽查**（shell 沙箱风险面与 P0 证据链的直接依赖，非本单元全量）: nop-ai-toolkit 的 BashExecutor、HostBashSandbox、BashSandboxPaths、LocalToolFileSystem；nop-ai-core 的 LocalFileOperator（FileTool 实际钳制点）；nop-integration-feishu 的 FeishuBindProvider（P0 证据）；nop-auth-service 的 ChannelBindServiceImpl.findBinding（P0 证据）；nop-kernel StringHelper 的文件名校验实现（路径遍历判定依据）。
  - **未覆盖**: nop-ai-agent/nop-ai-core/nop-ai-toolkit 其余类（属其他检查单元）；`_gen` 生成代码与全部测试代码（按任务规则排除）；nop-ai-rag 无 Java 源码（仅 pom/README）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 7 |
| P2 | 6 |
| P3 | 6 |

## 发现列表

### [P0] 扫码登录公共端点信任调用方伪造的渠道身份，可导致任意用户账号接管

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/login/ChannelLoginApiBizModel.java:142-221`（证据链另见 `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java:109-150`）
- **维度**: D5
- **证据**:
```java
@BizMutation("loginByScan")
@Auth(publicAccess = true)
public CompletionStage<ScanLoginResult> loginByScanAsync(@RequestBean ChannelScanCallback callback, ...) {
    ...
    ChannelBindResult bindResult = provider.onChannelScanCallback(callback);   // extId 取自 callback.rawPayload
    String extId = bindResult.getExtId();
    ...
    ChannelBindingInfo binding = channelBindService.findBinding(channelType, extId);  // 按攻击者给定的 extId 反查
    ...
    return sessionBootstrap.createSessionForUserAsync(binding.getPlatformUserId(), resolvedLoginType)
            .thenApply(userContext -> { ... return buildResult(userContext); });      // 为该用户签发 accessCode
```
FeishuBindProvider 侧（无任何签名/换取校验，ticket 不与身份绑定）:
```java
String openId = stringField(payload, "open_id");        // 直接取调用方 payload
TicketEntry entry = tickets.get(ticketId);              // ticketId 也来自调用方
tickets.remove(ticketId);                               // 消费 ticket，但 entry.platformUserId 在登录链路中被忽略
result.setExtId(openId);
```
- **现状**: `ChannelLoginApi__loginByScan` 是 `publicAccess=true` 的公共 GraphQL/REST 端点。`ChannelScanCallback` 是纯 `@DataBean`（channelType/ticketId/rawPayload 三字段全部由调用方提供）。链路中唯一的"凭证"是 provider 侧的 bind ticket，但 ticket 既不与 extId 绑定，其 platformUserId 在登录流程中也被忽略（登录走 `findBinding(channelType, extId)`，extId 完全由调用方 payload 决定）。整个链路没有任何渠道方签名验证、OAuth code 换取或 ticket↔身份 绑定校验。
- **风险**: 完整账号接管链：攻击者(1) 通过任意已登录账号（可自行注册）发起渠道绑定拿到一个有效 ticketId（或利用 ticketId 为 `"fs_bind_" + 自增序列` 的可预测性猜测他人未消费 ticket）；(2) 直接调用公共端点 `ChannelLoginApi__loginByScan`，`channelType=feishu`、`ticketId=<自己的有效ticket>`、`rawPayload={"open_id": "<受害者的 open_id>"}`；(3) `findBinding` 返回受害者绑定的 platformUserId；(4) 为受害者创建完整会话并返回一次性 accessCode；(5) 攻击者用 accessCode 换取 `LoginResult`，以受害者身份登录。前提仅是受害者曾绑定过该渠道（这正是扫码登录的存在前提）。
- **建议**: 登录链路必须以服务端到渠道方的验证结果为准：要么验证渠道推送事件的签名（Feishu 事件加密/签名），要么采用 OAuth code 由服务端用 appSecret 换取真实 open_id（qrPayload 中 `state=ticketId` 的授权码流程本应如此）；同时 ticket 应与扫码用户身份在渠道回调时由渠道方断言，而非信任回调 payload。至少应在 provider 层强制 `extId` 来自服务端换取结果。
- **误报排除**: 已核实 (a) 端点确为 `publicAccess=true` 且 bean 在 `ai-gateway-defaults.beans.xml` 注册暴露；(b) `ChannelScanCallback` 无任何签名字段；(c) 仓库内唯一 provider 实现（FeishuBindProvider）确实直接信任 `rawPayload.open_id`，ticket 校验不涉及身份绑定；(d) `ChannelBindServiceImpl.findBinding` 是纯 DB 反查（按 loginType+extId），无附加验证；(e) `createSessionForUserAsync` 以 `binding.getPlatformUserId()` 建会话，accessCode 返回给调用方。链路每一环均已读源码确认。

### [P1] AiFileTool 路径无任何钳制，可任意路径读/写主机文件

- **文件**: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java:159-175`
- **维度**: D5
- **证据**:
```java
IResource getResource(String path) {
    File file = new File(baseDir, path);          // 无 ".." 拒绝、无 canonical 钳制
    if (file.exists()) {
        path = StringHelper.normalizePath(path);
        if (!path.startsWith("/") && path.indexOf(':') < 0)
            path = "/" + path;
        return new FileResource(path, file);      // 后缀仅影响资源标签，不影响实际 File
    }
    ...
}
```
`loadNopFile` 中 `toFileType=raw` 直接返回原文；`saveNopFile` 中 `ResourceHelper.writeText(resource, content)` 直写该 File。
- **现状**: 与 `LocalToolOperator.resolveFile`（拒绝 `..`/`:` + canonical 前缀校验）不同，此处对 path 无任何校验。相对路径 `../../x` 逃出 baseDir；JDK `File(parent, child)` 在 child 为绝对路径时直接采用 child（UnixFileSystem.resolve 语义），因此 `path=/etc/hosts` 解析为绝对路径。baseDir 默认值还是 `"."`（JVM 工作目录）。
- **风险**: 持有 `AiFileTool:read` 权限的调用方可读取主机任意文件（`loadNopFile(path="/etc/shadow", toFileType="raw")`）；持有 `AiFileTool:write` 权限可覆写任意文件。该工具是 MCP server 暴露给 AI agent 的文件面，path 由模型输出驱动，可被提示注入利用。
- **建议**: 复用 `LocalToolOperator` 式钳制：拒绝包含 `..` 的路径 + resolved canonical path 必须以 baseDir canonical path 为前缀；绝对路径一律拒绝或映射到 baseDir 内。
- **误报排除**: 已对照同仓库 `LocalToolFileOperator`/`LocalFileOperator` 的防御实现，确认此处缺失非平台缺省行为；已确认 `FileResource(path, file)` 写操作落在 `file` 上；权限注解 `@Auth(permissions="AiFileTool:read/write")` 限制了未认证访问，故不评 P0。

### [P1] AiFileTool.saveNopFile 的 merge 分支条件写反：支持的类型报错、不支持的类型 NPE

- **文件**: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java:128-147`
- **维度**: D1
- **证据**:
```java
String xdefPath = builder.getXdefPath(fromFileType);
if (xdefPath != null) {
    return "ERROR: fileType " + fromFileType + " is not supported for merge";   // 条件反了
}
IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);                     // xdefPath==null 时 NPE
```
- **现状**: 判断条件与意图相反。有 xdef 的类型（正是支持 Delta merge 的类型）直接返回"不支持"；无 xdef 的类型继续执行 `loadXDefinition(null)` 抛 NPE（未捕获，向前端抛 500）。
- **风险**: `saveNopFile(merge=true)` 功能完全不可用；对可 merge 类型给出误导性错误，对不可 merge 类型抛 NPE。每次调用必现其一。
- **建议**: 条件改为 `if (xdefPath == null) return "ERROR: ...";`。
- **误报排除**: 已读完整方法上下文，无其他分支兜底；`getXdefPath` 返回 null/非 null 两种取值路径均已推演。

### [P1] SequentialThinking 会话 ID 取自客户端 Header 且未校验，拼接文件名造成路径遍历写

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/utils/AiToolsHelper.java:9-15`、`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java:60-63,77-82`
- **维度**: D5
- **证据**:
```java
// AiToolsHelper
String sessionId = (String) ctx.getRequestHeader("nop-chat-session-Id");  // 客户端可控，原样返回
if (StringHelper.isEmpty(sessionId)) sessionId = StringHelper.generateUUID();

// ThoughtStorage
private File getSessionFile(String sessionId) {
    Objects.requireNonNull(sessionId, "sessionId cannot be null");
    return new File(storageDir, sessionId + ".json");                     // 无文件名/遍历校验
}
private void saveSession(String sessionId, List<ThoughtData> thoughts) {
    ...
    FileHelper.writeText(sessionFile, json, null);                        // 思考内容进入文件
}
```
- **现状**: Header 值原样作为文件名拼接。`nop-chat-session-Id: ../../foo` 时 `new File(storageDir, "../../foo.json")` 逃出存储目录；`FileHelper.writeText` 会创建缺失的父目录。
- **风险**: 持有 `SequentialThinking:process` 权限的调用方可向主机任意路径写入 `.json` 后缀文件（内容为攻击者可控的思考文本包在 `{"thoughts":[...]}` 结构中），可覆盖既有 `.json` 配置文件或预置恶意配置；`clearHistory` 可将任意 `.json` 路径清空为 `{"thoughts":[]}`。该工具同样面向 LLM 调用，sessionId 可被提示注入操控。
- **建议**: 对 sessionId 做白名单校验（如仅 `[A-Za-z0-9_-]`），或复用 `isValidFileName` + 显式拒绝 `..`/绝对路径；写入统一限定在 storageDir 的 canonical 前缀内。
- **误报排除**: 已确认 header 名无平台级过滤（`ctx.getRequestHeader` 原样返回）；已确认 `processThought/generateSummary/clearHistory` 均以该值落盘；权限注解存在故不评 P0。

### [P1] 模拟 shell 中 cd 对后续命令完全无效（工作目录状态断链）

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:375-378,522-541`（关联 `commands/impl/CdCommand.java:54-56`）
- **维度**: D1
- **证据**:
```java
// 每条命令都用 baseContext 的 workingDirectory 构造一次性上下文
IShellCommandExecutionContext cmdContext = new DefaultShellExecutionContext(
        redirectedStreams.stdin, redirectedStreams.stdout, redirectedStreams.stderr,
        env, context.workingDirectory(), args, context.fileSystem(), cancelToken);

// CdCommand 只修改这个一次性上下文
if (context instanceof DefaultShellExecutionContext) {
    ((DefaultShellExecutionContext) context).setWorkingDirectory(resolvedDir);
}

// executor 自己的 currentWorkingDir 只在 GroupExpr 的 executeSequence 里更新，且只喂给 checker
if (simpleCmd.getCommand().equals("cd")) {
    currentWorkingDir = resolvePath(currentWorkingDir, args.get(0));
}
```
- **现状**: `cd` 的效果写在 per-command 的一次性 `DefaultShellExecutionContext` 上，命令返回即丢弃；后续命令仍从 base context 拿原始 `workingDirectory()`。executor 的 `currentWorkingDir` 仅被 `CheckVisitor`（安全检查）使用，从不传给命令上下文；且只在 group 序列中更新、更新时不看 exitCode（`cd /不存在目录 && pwd` 也会更新检查器视角的目录）。顶层 `cd /a; ls`、`cd /a && ls` 均不改变 ls 的行为。
- **风险**: shell 会话语义核心断裂——所有依赖 cd 的脚本行为错误（ls 列目录不变、相对路径重定向仍相对旧目录）。同时造成"检查器视角目录"与"执行视角目录"漂移：基于 workingDirectory 的安全判定与实际执行目录不一致，是沙箱类系统的不良信号。
- **建议**: `executeSimpleCommandWithContext` 应以 `currentWorkingDir`（而非 base context 快照）作为命令上下文的 workingDirectory，并在命令成功（exitCode==0）后同步 executor 状态；或让 CdCommand 通过 executor 回调更新共享状态。
- **误报排除**: 已通读 executor 全部调用链（execute/executeExpression/executeSequence/updateContextFromResult），确认没有任何路径把 executor.currentWorkingDir 或 cd 后的一次性上下文回写到 base context；`getBackgroundJobs/getCurrentWorkingDir` 的外部使用方（nop-ai-agent ISandboxBackend）不参与命令上下文构造。

### [P1] 命令检查器可被引号包裹与 `bash -c` 形态绕过（deny-list 失效）

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/checker/DefaultCommandChecker.java:126-161`、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java:310,543`
- **维度**: D5
- **证据**:
```java
// 解析器：命令名与重定向目标不剥引号（只有 args 剥）
String command = consume().value();          // QUOTED token 原样含引号
...
String target = consume().value();           // 重定向目标同样含引号

// 检查器按字面比较
if (command.equals("rm")) { checkRm(args); }
if (SHELL_INTERPRETERS.contains(command) && args.isEmpty()) { ... }   // 仅拦无参解释器
```
- **现状**: (a) `"rm" -rf /x` 解析出的 cmdName 为 `"rm"`（含引号），`equals("rm")` 与 `BLOCKED_COMMANDS.contains` 均不命中，rm/chmod/chown 检查全部跳过；`> "/dev/sda"` 的重定向目标含引号，`STORAGE_DEVICE` 正则 `^/dev/...` 不匹配，存储设备重定向检查同样绕过（sudo 前缀透传子命令时同理）。(b) `bash -c "任意命令"`、`sudo bash -c ...` 中 `args` 非空，直接放行，且内部命令串不会递归检查。
- **风险**: 该类是 `@SecureDefault` 标注的安全门，为接入 `ExternalCommandAdapter`（注释明示 nop-shell 依赖提供真实执行）时的防线。当前 adapter 是恒抛 `UnsupportedOperationException` 的 stub，故绕过暂不可达真实执行；一旦接入真实执行，deny-list 对引号变体与解释器 `-c` 形态完全失效，等同于命令注入防线失守。
- **建议**: 解析层对命令名/重定向目标做与 args 一致的 unquote 后再交给检查器；检查器对 shell 解释器一律拒绝携带 `-c`/脚本参数的形态，或改为白名单模型（与 BashExecutor 的沙箱隔离基线保持同样思路：deny-list 只作纵深防御）。
- **误报排除**: 已读 BashLexer（QUOTED token 保留引号原文）与 parser 的 unquote 逻辑（仅作用于 args 循环 315-321 行），确认命令名/redirect target 不剥引号；已确认 DefaultCommandChecker 全部分支为字面量比较。当前不可利用（adapter stub）故评 P1 而非 P0。

### [P1] 后台任务表 backgroundJobs 非线程安全且 close 无法终止阻塞中的任务

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:50,320-335,591-603`
- **维度**: D3/D2
- **证据**:
```java
private final Map<String, CompletableFuture<?>> backgroundJobs = new LinkedHashMap<>();  // 非线程安全

CompletableFuture<ExecutionResult> bgFuture = CompletableFuture.supplyAsync(() -> {
    return executeExpression(background.inner(), context, cancelToken)
            .toCompletableFuture().join();          // 阻塞占用 worker 线程
}, executor);
backgroundJobs.put(jobId, bgFuture);
bgFuture.whenComplete((r, ex) -> backgroundJobs.remove(jobId));   // 完成线程并发 remove

public void close() {
    closed = true;
    for (Map.Entry<...> entry : backgroundJobs.entrySet())       // 迭代期间可能被并发 remove
        entry.getValue().cancel(true);                            // CF.cancel 不中断底层任务
```
- **现状**: put（调用方线程）、remove（完成回调线程）、close 的迭代/清空存在数据竞争，LinkedHashMap 无任何同步，可能丢条目或抛 ConcurrentModificationException。`cancel(true)` 不会中断正在 `join()` 的工作线程，内部 future 也未被级联取消——若内部命令挂起（如等待输入），worker 线程永久阻塞，close 仅等 1 秒后放弃。
- **风险**: 并发后台命令场景下 close 抛 CME（关闭流程中断）；挂起的后台任务持续占用 `GlobalExecutors.globalWorker()` 线程池线程，多次累积后线程池耗尽，前台命令无法调度。
- **建议**: 换成 `ConcurrentHashMap`；close 时级联取消内部 future（保存内部 future 引用）或对 join 使用带超时的 `get(n,unit)`；将 cancelToken 传入后台执行以支持协作式取消。
- **误报排除**: 已确认 executor 可为 `GlobalExecutors.globalWorker()`（构造函数缺省），后台与前台共用同一池；已确认没有任何同步块保护 backgroundJobs。

### [P1] `&>`/`2>&1` 合并重定向经 TeeOutput 对同一文件双写，内容翻倍

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:499-506`、`nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/TeeOutput.java:32-37`
- **维度**: D1
- **证据**:
```java
private void handleMergeRedirect(RedirectedStreams streams, Redirect redirect, boolean append) {
    FileShellOutput fileOutput = new FileShellOutput(redirect.target(), fileSystem, append);
    TeeOutput teeOutput = new TeeOutput(fileOutput, fileOutput);   // 同一 output 加两次
    ...
}
// TeeOutput.write
for (IShellOutput output : outputs) {
    output.write(chunk);          // 每个chunk写两次 fileOutput
}
```
- **现状**: `TeeOutput(fileOutput, fileOutput)` 把同一实例放进 outputs 两次，`write` 对每个 chunk 调用 `FileShellOutput.write` 两次，缓冲区内容翻倍后整体落盘。
- **风险**: 每条 `cmd &> file` / `cmd >file 2>&1` / `&>>` 命令的文件内容都是双份——数据损坏，触发路径为常规用法。
- **建议**: 直接 `streams.stdout = streams.stderr = fileOutput;`（或 `new TeeOutput(fileOutput)` 单输出）。若意图是"文件+透传"，第二个参数应是透传目标而非同一文件。
- **误报排除**: 已读 FileShellOutput.write（append 到 StringBuilder 缓冲）与 flush（整段落盘），确认两次 write 必然产生双倍内容。

### [P2] 管道中间 stage 异常被静默丢弃

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:214-257`
- **维度**: D4
- **证据**:
```java
CompletableFuture<ApiResponse<Integer>> ... stageFutures.add(stageFuture);   // 中间stage future
...
CompletableFuture<ExecutionResult> pipelineFuture =
        stageFutures.get(stageFutures.size() - 1).thenApply(lastExitCode -> {  // 只观察最后一个
            String stdout = collectOutput(lastOutput);
            return new ExecutionResult(lastExitCode, stdout, "");
        });
```
`collectOutput` 亦吞异常返回 ""。
- **现状**: 中间 stage 的 future 从未被观察（无 whenComplete 记录、不参与结果）；中间命令抛异常（如输入重定向文件不存在时 `FileShellInput` 构造抛 NopAiException）时，下游读到 EOF，管道以 exit=0 + 空输出"成功"返回。
- **风险**: 错误被掩盖，调用方（AI 工具循环）拿到看似成功但截断/为空的结果，行为不可预期。
- **建议**: 对每个中间 stage future 附加 `whenComplete` 记录异常并（可选）将异常传播到管道结果；`collectOutput` 至少记录失败原因。
- **误报排除**: 已确认 `stageFutures` 除最后一个外无任何消费点；`executeSimpleCommandWithContext` 内部 catch 仅覆盖 `UnsupportedOperationException`，`FileShellInput` 构造异常会传播到 stage future。

### [P2] Feishu 会话映射在首个请求完成前不落库，快速连发消息导致会话分裂

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java:271-286,344-350`（关联 `channel/ChannelSessionStoreImpl.java:80-97`）
- **维度**: D3/D1
- **证据**:
```java
session = sessionStore.findByChannel(CHANNEL_TYPE, chatId);   // 消息到达时查映射
String existingSessionId = session != null ? session.getSessionId() : null;
boolean isNewSession = existingSessionId == null;
...
future.whenComplete((result, error) -> onExecutionComplete(result, error, replyTarget, isNewSession, agentName));
// onExecutionComplete 内才 saveMapping —— 首个请求执行完成之后
```
- **现状**: 映射在引擎执行完成的回调里才写入。首条消息执行期间（LLM 调用通常秒级以上）到达的第二条消息查不到映射，再次新建引擎会话；两个回调先后 saveMapping，第二个撞 `uk_nop_ai_channel_session_channel` 唯一键（orm 模型 1466-1470 行）抛异常，被 catch 后仅 warn。
- **风险**: 对话上下文分裂成两个会话（第二条消息丢失前文语境）；并发下产生一次失败的 save + warn 噪音。唯一键兜底避免了脏数据，故降为 P2。
- **建议**: saveMapping 提前到引擎 ack 返回 sessionId 时（若有同步 ack），或对同一 chatId 的消息做串行化/映射预占；saveMapping 改 upsert 语义。
- **误报排除**: 已核对 orm 模型确认唯一键存在（否则会评 P1 重复数据）；已确认 `onExecutionComplete` 的 catch 只 LOG.warn 不重试不更新既有行。

### [P2] failover 链路两处 `LlmConfigHelper.loadConfig` 返回 null 未判空（NPE）

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/AiGatewayFailoverInterceptor.java:488-505`、`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/GatewayStreamingRetryCallback.java:104-131`
- **维度**: D1
- **证据**:
```java
// sinkAuthHeader —— 同类 sinkCandidate(472-478行) 对 null 有判空，此处没有
LlmModel config = LlmConfigHelper.loadConfig(candidate.getProvider());
String apiKey = candidate.getAccountKey();
if (StringHelper.isEmpty(apiKey)) apiKey = LlmConfigHelper.resolveApiKey(...);
if (StringHelper.isEmpty(apiKey)) return;
ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());   // config==null → NPE

// GatewayStreamingRetryCallback.buildHttpRequest 同型
ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());   // 113行
... baseUrl = config.getBaseUrl();                                          // 116行
```
- **现状**: `loadConfig` 可返回 null（`AiDialectBackendMessageConverter.resolveConfig` 148-159 行对同一调用显式 fail-loud 抛错，证明 null 是已知情形；sinkCandidate 也判了空）。候选带 accountKey（备用账号）而 provider 配置缺失/被删时，认证头下沉与流式重试建请求直接 NPE。
- **风险**: 特定条件（路由注册表与 llm 配置不一致，如运行期下线 provider 配置）下，非流式认证头下沉抛 NPE 中断请求；流式重试回调抛 NPE 导致重试链路异常而非走 fail-loud 错误。
- **建议**: 与 `resolveConfig` 一致：null 时抛带 provider 信息的 `NopAiCoreException`（fail-loud），而非留 NPE。
- **误报排除**: 已比对同文件/同链路三处对 loadConfig 的判空策略不一致；`resolveApiKey` 对无配置 provider 返回空、但 accountKey 来自候选对象可独立非空，触发路径成立。

### [P2] FileTool 项目名 `".."` 通过校验，baseDir 抬升一级打破项目隔离

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/file/FileToolBizModel.java:238-244`
- **维度**: D5
- **证据**:
```java
protected File getProjectDir(String projectName) {
    String dirName = StringHelper.fileName(projectName);      // 取最后一段，".." 无分隔符原样保留
    if (!StringHelper.isValidFileName(dirName))               // 非法字符集不含 '.'
        throw new NopException(ERR_AI_TOOLS_INVALID_PROJECT_NAME).param(ARG_VALUE, projectName);
    return new File(baseDir, dirName);                        // ".." → baseDir 的父目录
}
```
- **现状**: `INVALID_FILE_NAME_CHARS = {'/','\\',':','*','?','"','<','>','|',0}` 不含 `.`，`isValidFileName("..")` 为 true；`fileName("../..")` 截成 `..` 同样通过。`new File(baseDir, "..")` 使 LocalFileOperator 的根变成 baseDir 父目录（默认 `/nop/projects` → `/nop`），其内部钳制以逃逸后的目录为基准。
- **风险**: 持有 `FileTool:read/write` 权限者（含被提示注入的 LLM 工具调用）可跨项目读写 baseDir 同级目录（仅一级；LocalFileOperator 内部路径仍受 canonical 钳制，无法继续上溯）。破坏 projectName 的项目隔离承诺。
- **建议**: `getProjectDir` 显式拒绝 `.`/`..`（及 normalize 后非纯文件名的值），或对最终目录做 canonical 前缀校验。
- **误报排除**: 已读 StringHelper 的 `fileName`（lastPart('/'）)与 `isValidFileName` 实现确认判定；已读 LocalFileOperator.resolveFile 确认内部路径钳制以传入 baseDir 为准（即逃逸一级后即封顶），故评 P2 而非 P1。

### [P2] JavaMethodReplacer 括号匹配的转义判断有缺陷，错误替换会写坏源文件（当前休眠）

- **文件**: `nop-ai/nop-ai-coder/src/main/java/io/nop/ai/coder/code/JavaMethodReplacer.java:60-113,44-49`
- **维度**: D1
- **证据**:
```java
if (c == '\'' && !inDoubleQuote) {
    if (!inSingleQuote || prev != '\\') { inSingleQuote = !inSingleQuote; }
} ...
// prev 只看前一个字符：Java 源码 "...\\"（转义的反斜杠结尾）中，
// 收尾引号的前一字符是 '\' 但它本身已被转义 → 引号状态判断错误
...
Files.write(path, newContent.getBytes());   // 按错误 braceEnd 替换后直接写回
```
- **现状**: 字符串/字符字面量状态机只用单字符 `prev` 判断反斜杠转义，无法处理 `\\`（自身被转义的反斜杠）。方法体内含 `"C:\\"`、正则字符串等常见字面量时，引号配对错乱，`findMatchingBrace` 返回错误的右括号位置（或 -1），替换结果写回即损坏源文件。另注释称"只替换大括号内的内容"但实现连大括号一起去掉（newMethodBody 需自带括号），契约易误用。
- **风险**: 一旦该工具被接入 AI diff 应用链路（当前仅测试引用，main 无调用方），会产生静默源码损坏。当前不可达故评 P2。
- **建议**: 改为逐字符推进并维护"前一反斜杠是否已被转义"状态（或复用平台已有的 Java 词法工具）；写回前用语法校验兜底；修正注释与实现的括号契约。
- **误报排除**: 已全仓 grep 确认 main 代码无调用方（仅 TestCoderErrorCodeConversion 引用），故不评 P1；解析缺陷通过 `"\\"` 字符序列推演验证。

### [P2] 有界输出队列在下游不读尽输入时永久阻塞上游 worker 线程

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/io/BlockingQueueShellOutput.java:14,26-34`（关联 `executor/ShellCommandExecutor.java:234-242`）
- **维度**: D2
- **证据**:
```java
this.queue = new LinkedBlockingQueue<>(1024);   // 有界
public void write(ShellChunk chunk) {
    ...
    queue.put(chunk);                            // 满时永久阻塞
}
```
- **现状**: 管道各 stage 经 `BlockingQueueShellOutput` 传递；上游命令写满 1024 chunk 后 `put` 阻塞，若下游命令提前退出不读尽输入（`cat 大文件 | head` 语义），上游 stage 的 `executeSimpleCommandWithContext` 永不返回，其 `finally { IoHelper.safeClose(out) }` 也不执行（close 只是投递 EOF，不解阻塞的 put）。
- **风险**: worker 线程泄漏，多次累积耗尽共享线程池。当前内置命令集（echo/ls/cd）单次输出不超 1024 chunk，难以触发；命令集扩展后即成为现实问题，故评 P2。
- **建议**: 下游关闭时传播取消（如 close 时置中断标志，write 检测后抛 IO 异常），或对 put 使用带超时的 offer + 检查下游状态。
- **误报排除**: 已确认 ShellCommandRegistry 内置命令与 DefaultShellExecutionContext 的输出模式；确认 stage 的 close 在 `finally` 中、仅在命令返回后才执行，无法解救阻塞中的 put。

### [P3] SequentialThinking.clearHistory 变更操作标注为 @BizQuery

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/SequentialThinkingBizModel.java:110-115`
- **维度**: D8/D7
- **证据**:
```java
@BizQuery
@Auth(permissions = "SequentialThinking:delete")
public void clearHistory(IServiceContext ctx) {
    String sessionId = AiToolsHelper.makeChatSessionId(ctx);
    storage.clearHistory(sessionId);
}
```
- **现状**: 清空历史是变更操作，却用 `@BizQuery`（平台读语义），与 `@BizMutation` 事务/审计语义不符；返回 void 也不符合 Query 惯例。
- **风险**: 平台层按 Query 处理（无变更事务语义、可缓存/幂等假设），契约漂移。
- **建议**: 改为 `@BizMutation`。
- **误报排除**: 已对照同文件 `processThought`（@BizMutation）确认非笔误复制。

### [P3] `cmd 2> f` 的 fd 数字被当作位置参数，stderr 未按语义重定向

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java:312-322,209-216`
- **维度**: D8
- **证据**:
```java
// parseBaseCommand 的 args 循环先于 parseSimpleCommand 的 trailing-redirect 循环执行
while (peek() != null && isCommandOrArgument(peek())) {   // "2" 是 COMMAND token，被吃成 arg
    args.add(argToken.value());
}
```
- **现状**: `ls 2> err.txt` 中 `2` 先被 args 循环消费为 ls 的位置参数；`parseSimpleCommand` 的 fd 识别分支只对 redirect 之后的数字 token 生效。stderr 实际写入 stdout 管道，ls 收到多余的 `2` 参数。
- **风险**: fd 重定向语义漂移，含 stderr 过滤的脚本行为错误（模拟 shell 内）。
- **建议**: args 循环遇到"数字 token 且下一 token 是 REDIRECT_*"时回退交给 redirect 解析。
- **误报排除**: 已按 token 序列（COMMAND(ls), COMMAND(2), REDIRECT_OUTPUT, COMMAND(err.txt)）逐步推演 parse 流程确认。

### [P3] ChannelConnectorManager.stopAll 中途异常中断 LIFO 释放且不清列表

- **文件**: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelConnectorManager.java:115-120`
- **维度**: D4/D2
- **证据**:
```java
public void stopAll() {
    for (int i = started.size() - 1; i >= 0; i--) {
        started.get(i).stop();          // 某个 stop 抛异常 → 后续连接器不被 stop
    }
    started.clear();                    // 异常时不执行
}
```
- **现状**: 单个连接器 stop 抛异常会中断整个关闭序列，剩余连接器不释放。
- **风险**: 部分连接器（长连接/线程）泄漏；与 startAll"失败立即暴露"的注释意图不一致。
- **建议**: 逐个 try/catch 收集异常，最后聚合抛出（或至少保证全部 stop 后再抛）。
- **误报排除**: 已确认 stop 实现可抛（FeishuConnector.stop 内部吞异常，但接口契约不保证）。

### [P3] AiFileTool.loadNopFile 死条件：空 toFileType 分支内判断 isJsonFileExt(toFileType)

- **文件**: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java:78-91`
- **维度**: D1
- **证据**:
```java
if (StringHelper.isEmpty(toFileType)) {          // 进入分支时 toFileType 必为空
    ...
    if (JsonTool.isYamlFileExt(fromFileExt)) {
        return JsonTool.serializeToYaml(...);
    } else if (JsonTool.isJsonFileExt(toFileType)) {   // 恒 false，疑似应为 fromFileExt
        return JsonTool.stringify(...);
    }
```
- **现状**: JSON 文件在未指定 toFileType 时永远走不到 `stringify` 分支，落到 xdoc/text 分支。
- **风险**: 输出格式与预期不符（轻微行为缺陷）。
- **建议**: 改为 `JsonTool.isJsonFileExt(fromFileExt)`。
- **误报排除**: 已确认分支进入条件与判断变量的取值矛盾。

### [P3] ThoughtStorage 解析缺 thoughts 字段的 JSON 返回 null 引发 NPE；写盘非原子

- **文件**: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/sequential_thinking/service/ThoughtStorage.java:65-82`
- **维度**: D1/D2
- **证据**:
```java
ThoughtSession session = fromJson(json, ThoughtSession.class);
return session.getThoughts();        // thoughts 字段缺失时为 null → addThought/getAllThoughts NPE
...
FileHelper.writeText(sessionFile, json, null);   // 直接覆写，无 temp+rename
```
- **现状**: 若文件内容非预期（手改、旧版本、写入中途崩溃截断），`getThoughts()` 为 null 时 `thoughts.add(thought)` 与 `new ArrayList<>(null)` 抛 NPE；写盘非原子，崩溃可留半截文件随后解析失败。
- **风险**: 单会话工具的健壮性问题，影响面小。
- **建议**: `getThoughts()` 判空回退空列表；写盘采用 temp+rename。
- **误报排除**: 已确认无别处对文件内容做 schema 校验；调用链（BizModel→Storage）无 null 拦截。

### [P3] 外部命令回退语义不一致：简单命令与管道路径行为不同

- **文件**: `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:186-212,356-367`
- **维度**: D8
- **证据**:
```java
// executeSimpleCommand：预先查 registry，未注册直接 127
if (command == null) {
    return FutureHelper.success(new ExecutionResult(127, "", "Command not found: " + commandName));
}
// executeSimpleCommandWithContext：未注册时尝试 externalAdapter（仅管道路径可达）
if (command == null) {
    try { return externalAdapter.execute(...); }
    catch (UnsupportedOperationException e) { stderr.println("Command not found: " + commandName); return 127; }
}
```
- **现状**: 同一未注册命令，独立执行走 `executeSimpleCommand` 的预检短路（不经过 adapter），管道中走 `executeSimpleCommandWithContext` 的 adapter 回退。当前 adapter 恒抛故结果一致（127），但接入真实 adapter 后两条路径行为分叉（管道可执行外部命令、独立执行不行）。
- **风险**: 契约漂移与未来接入时的语义意外。
- **建议**: 统一走 `executeSimpleCommandWithContext`（去掉预检短路）或统一去掉 adapter 回退。
- **误报排除**: 已确认两条调用路径的分流点（visit(SimpleCommand) vs executePipeline）。

## 附注（检查过但未列为发现的项）

- toolkit 沙箱链路（BashExecutor fail-closed 无后端拒执、HostBashSandbox 进程树 kill + 输出排干、BashSandboxPaths 的 `..` 拒绝 + toRealPath 白名单）整体稳健；`BashExecutor.DESTRIVE_COMMAND` 正则会把所有 `rm -f`（无论目标）判为破坏性命令而拒绝（过拦，防御性纵深下可接受，未单列）。toolkit 属其他检查单元，此处仅覆盖沙箱 seam。
- gateway 的 ChannelMessageServiceImpl（fan-out 线程池、超时中断、bridge 去重）与 failover 拦截器主体（acquire/release 配对、预算、探活）质量较高，未发现资源泄漏或计数失配。
- Nop 平台规范（D7）：全部目标模块 `@Inject` 字段均为 protected/setter 注入，无 private 字段注入；错误处理符合两档策略（gateway/service/shell/maven 用 NopException+ErrorCode+.param，模块内部用 NopAiException 等模块异常），主源码无 bare RuntimeException、无 printStackTrace、无空 catch。
- nop-ai-rag 模块无 Java 源码（仅 pom/README），未产生发现。
- dao 层 `_gen` 生成类与 `_NopAiDaoConstants` 按规则未审；非生成 entity/biz 均为薄壳，未见手写逻辑缺陷。
