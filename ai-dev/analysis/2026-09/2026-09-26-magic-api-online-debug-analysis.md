# magic-api 在线开发与调试器实现分析

> Status: open
> Date: 2026-09-26
> Scope: 外部项目 magic-api v2.2.2 (master `e1cdccaa`) + magic-script v1.9.0 + magic-editor 前端；对照 Nop `nop-dev-tools/nop-xlang-debugger` 现状与 nop-chaos-flux `flux-code-editor` 前端基座
> Conclusion: （open，结论为建议方向）magic-api 调试器 = **编译期按行插桩 `pause()` + 每请求携带断点的 HTTP 触发 + WebSocket 推送断点命中/日志/异常 + 阻塞队列挂起脚本线程**。Nop 已有比它更强的调试引擎（`XLangDebugger`），缺的是 Web 通道与编辑器前端；建议复用 `IDebugger` 协议加 WebSocket/JSON 网关，而非照搬 magic-api 的按请求重编译模式。

## Context

- 目标：为 Nop 平台的 XLang 实现"在线开发调试编辑器"（浏览器端），先调研 magic-api 是如何实现在线开发+调试的，评估哪些可借鉴。
- magic-api 是国内流行的"接口在线开发"框架：浏览器编辑脚本 → 保存即发布 → 页面内测试、打断点、单步、看变量、看日志。
- 调研素材（本次全部拉取到本地）：
  - `/Volumes/data/sources/magic-api`（GitHub `ssssssss-team/magic-api`，master `e1cdccaa`，1479 commits，fsck 通过）
  - `/Volumes/data/sources/magic-script`（gitee `ssssssss-team/magic-script`，v1.9.0；**调试器内核在此仓库，不在 magic-api 仓库**；GitHub 上无此仓库，clone 走 gitee）
  - `/Volumes/data/sources/magic-editor-1.7.5-src`（前端 Vue 源码。2.0 起前端闭源只发编译产物，从 magic-api git 历史 `052e43ff^` 用 `git archive` 提取；2.x 前端行为通过分析打包产物 `magic-editor/src/main/resources/magic-editor/assets/app.60f63c60.js` 交叉验证）
- Nop 侧现状调研由子代理完成（只读），关键结论已逐条核验过主要 file:line。

## 调研目标

1. magic-api "在线开发"（编辑→保存→热发布→测试）的完整链路是什么？
2. 在线调试器的实现逻辑：断点如何注入、命中如何通知、线程如何挂起/恢复、变量/日志如何回传？
3. 前后端协议长什么样？前端如何交互？
4. 该方案有哪些缺陷/局限？
5. 对"Nop XLang 在线调试编辑器"哪些可借鉴、哪些不可借鉴？

---

## Analysis

### 1. 总体架构：三层职责划分

| 层 | 仓库/模块 | 职责 |
|----|----------|------|
| 脚本引擎内核 | `magic-script` v1.9.0 | 编译期插桩 + `MagicScriptDebugContext` 挂起/恢复/变量快照 |
| 服务端框架 | `magic-api` | HTTP 触发调试、WebSocket 控制台协议、日志管道、集群转发、资源热发布 |
| 前端 | `magic-editor` | Monaco 编辑器、断点装饰、调试面板、日志面板 |

**关键认知：调试器核心（`MagicScriptDebugContext`）在 magic-script 里，magic-api 只做"通道"**（把断点从 HTTP header 递进去、把断点命中/日志/异常从 WebSocket 推出来）。

### 2. 在线开发链路（编辑 → 热发布 → 测试）

#### 2.1 资源存储与热发布

- 接口/函数/数据源统一抽象为 `MagicEntity`，存 DB 或文件（`MagicResourceService`）；保存入口 `MagicResourceController` `/resource/file/{folder}/save`（`magic-api/src/main/java/org/ssssssss/magicapi/core/web/MagicResourceController.java:73-96`）。
- 保存后发 `FileEvent`，`AbstractMagicDynamicRegistry.processEvent` 按 `CREATE/SAVE/DELETE/MOVE` 增量注册（`.../core/service/AbstractMagicDynamicRegistry.java:32-78`）：
  - `mappingKey`（method+path）不变时仅刷新实体引用；
  - 变化时 unregister 旧 Spring MVC `RequestMappingInfo`、register 新的——**动态注册/注销 Spring MVC 映射，免重启发布**（`RequestMagicDynamicRegistry` 用反射持有 `RequestHandler.invoke` 作为统一 handler，`.../core/service/impl/RequestMagicDynamicRegistry.java:49-51`）。
- 多实例通过 `MagicNotifyService`（DB 轮询或 Redis）广播文件事件实现集群热更新。
- 备份/回滚：`backup` 包按 fileId 存历史版本，前端 `viewHistory` 调 `backups?id=`（`magic-script-editor.vue` `viewHistory()`）。

#### 2.2 WebSocket 控制台（所有实时消息的载体）

- 端点：`{web}/console`，注册于 `MagicAPIAutoConfiguration.registerWebSocketHandlers`（`magic-api-spring-boot-starter/.../MagicAPIAutoConfiguration.java:390-406`），handler 链 = `MagicDebugHandler`（调试）+ `MagicCoordinationHandler`（协作）+ `MagicWorkbenchHandler`（登录/在线用户）。
- 分发器 `MagicWebSocketDispatcher`：
  - 启动时反射扫描 `@Message(MessageType.X)` 注解方法建路由表（`:46-51`）；
  - 消息是自造的 CSV 文本协议 `msgType,arg1,arg2,...`，最后一个数组/对象参数按 JSON 解析（`:53-90`），构造端对应 `WebSocketSessionManager.buildMessage`（`.../core/config/WebSocketSessionManager.java:132-146`）；
  - **handler 返回 `false`（本机没有该 session/context）时，把整条消息经 `MagicNotify`（`WS_C_S` 事件）转发给其他实例处理**（`:117-120`）——集群调试路由的基础。
- 会话管理 `WebSocketSessionManager`（全部静态单例）：
  - `SESSIONS: Map<clientId, MagicConsoleSession>`、`CONTEXTS: Map<clientId+scriptId, MagicScriptDebugContext>`（`:24,:28,:186-196`）；
  - 保活：服务端 20s 一次发 `PING`，60s 无 `PONG` 踢下线并广播 `USER_LOGOUT`（`:198-213`）；前端 `reconnecting-websocket.js` 自动重连；
  - 登录：`LOGIN` 消息带 `[token, clientId]`，clientId 由前端生成随机串（2.x bundle：`CLIENT_ID=zi(16)`），同一 clientId 重复登录拒绝（`MagicWorkbenchHandler.onLogin:41-72`）。

#### 2.3 测试请求与日志回传

- 测试 = 前端直接对**真实接口路径**发 HTTP 请求，外加 3 个头：
  - `Magic-Request-Client-Id`、`Magic-Request-Script-Id`（标识"这是控制台测试"）、`Magic-Request-Breakpoints`（逗号分隔行号，`Constants.java:75-79`）。
  - 版本演进：v1.7.5 用单一 `Magic-Request-Session` 头（`git show v1.7.5:.../RequestHandler.java:93`），2.x 改为 clientId+scriptId 组合键，旧头服务端已不读。
- 服务端 `RequestHandler.invoke` 判定 `requestedFromTest = enableWeb && clientId!=null && scriptId!=null`（`.../core/web/RequestHandler.java:95`）。
- **日志**：`MagicLoggerContext.SESSION`（`InheritableThreadLocal<String>`，值为 clientId）在测试请求线程设置（`:177`）；logback/log4j2 向 ROOT logger 挂自定义 Appender，`println` 时按 ThreadLocal 取 clientId → `sendLogs` 攒批（≥100 条或 1s 定时 flush）→ WS `LOG`/`LOGS` 消息（`WebSocketSessionManager.java:46-51,77-97`）。脚本 `println()` 与业务日志统一走这条管道，控制台日志面板实时显示。

### 3. 在线调试器实现逻辑（核心）

#### 3.1 编译期插桩（magic-script）

- magic-script 把脚本编译成 JVM 字节码（ASM 生成 `MagicScriptRuntime` 子类）。
- **调试开关藏在源码文本里**：`MagicScript.DEBUG_MARK = "!# DEBUG\r\n"`，`create()` 检测前缀置 `debug=true` 并剥掉前缀再 parse（`magic-script/src/main/java/org/ssssssss/script/MagicScript.java:39,103-107`）。
- 编译器 `MagicScriptCompiler.compile(Node,pop)`：`debug=true` 时，**每遇到与上一条不同行号的 AST 节点，就插入一段字节码：`context.pause(startRow, startCol, endRow, endCol, variables)`**（`magic-script/src/main/java/org/ssssssss/script/compile/MagicScriptCompiler.java:242-256`；行号经 `lineNumber(span)` 记录进 spans 供异常定位，`:213-222`）。
- 行粒度：`lastLineNumber != line` 才插桩 → 每行只插一次。断点匹配的是**语句起始行**。
- 行号为 1-based（`Span.getLine()` 计算 `lineNumber` 从 0 自增后 +1，`parsing/Span.java:145-156`），与 Monaco 行号天然一致。
- `MagicScriptContext.pause(...)` 默认空实现（`MagicScriptContext.java:181-183`）；只有运行时传入的是 `MagicScriptDebugContext` 才有行为。**非调试执行零开销**（没有插桩代码）。
- 编译缓存 `CompileCache` 以"完整源串（含 DEBUG_MARK 前缀）"为 key → 同一脚本的调试版/普通版是两份独立的编译产物，互不污染（`MagicScript.java:98-108`）。

#### 3.2 调试模式的进入条件（重要约束）

- `ScriptManager.executeScript`：**仅当 context 是 `MagicScriptDebugContext` 时才加 DEBUG_MARK 前缀**（`magic-api/.../utils/ScriptManager.java:22`）。
- `RequestHandler.createMagicScriptContext`：**仅当 `requestedFromDebug`（即断点列表非空）才创建 debug context**（`RequestHandler.java:402-415`，`RequestEntity.isRequestedFromDebug:78-79`）。
- 推论：**不设断点就无法进入调试模式**——没有插桩，后续的 stepInto 也无从谈起。这是"按请求重编译"方案的固有约束。

#### 3.3 挂起与恢复（`MagicScriptDebugContext`）

`magic-script/src/main/java/org/ssssssss/script/MagicScriptDebugContext.java`，全文 95 行，本质是一个**双阻塞队列握手**：

```java
synchronized void pause(startRow, ..., Variables variables) {
    if (stepInto || breakpoints.contains(startRow)) {
        this.line = [startRow, startCol, endRow, endCol];
        consumer.offer(this.id);                       // ① 登记"我已挂起"
        varMap = rootVariables + variables.getVariables(this);  // ② 变量快照
        callback.accept(getDebugInfo(varMap));         // ③ 经 WS 推 BREAKPOINT 给前端
        producer.poll(timeout, SECONDS);               // ④ 挂起，等恢复信号；超时(默认60s)自动放行
    }
}
void singal() { producer.offer(this.id); await(); }   // 恢复：⑤ 写入恢复信号；⑥ 吃掉①的令牌
```

- 断点判定在**运行时**做（`breakpoints.contains(startRow)`），插桩只保证"每行都有机会检查"。
- 变量快照：`Variables.getVariables` 沿 parent 链（闭包/函数调用栈）合并所有已赋值变量槽位，按 `varNames` 映射回变量名（`runtime/Variables.java:67-90`）；值经 JSON 序列化为字符串（`DebugRequest.java:64-70`），一次性全量推给前端。
- `pause` 是 `synchronized`（同一 context 串行挂起）；`singal` 非同步，允许 WS 线程在脚本线程持锁阻塞时调用。
- **timeout 语义**：`producer.poll(timeout)` 超时返回 null → pause 正常返回 → 脚本自动继续（`Debug.timeout=60s`，`core/config/Debug.java`）。`DebugTimeoutException` 定义了但**从未被抛出**（死代码）。

**该握手的缺陷（源码推演，值得引以为戒）**：

1. **过期恢复信号**：若前端在脚本未挂起时发 `resume_breakpoint`（竞态窗口），`producer` 里会留一个令牌，下次断点命中时 `pause` 的 `poll` 立刻取到令牌 → 断点"闪一下就过"。
2. **无限阻塞**：`singal()` 的 `await()`（`consumer.take()`）无超时。极端时序下 WS 分发线程会一直阻塞到下一次断点命中才返回。
3. `setBreakpoints` 与 `pause` 中的读取之间无同步保护（可见性靠 `synchronized pause` 间接兜底）。

#### 3.4 调试通道协议（HTTP 触发 + WS 推送/控制）

一次完整的调试会话时序：

```
前端                          服务端(RequestHandler线程)              WebSocket线程
 │ POST /group/api + 3个头        │                                    │
 │ (clientId,scriptId,breakpoints)│                                    │
 ├───────────────────────────────>│                                    │
 │                                │ 创建 MagicScriptDebugContext        │
 │                                │ 注册 CONTEXTS[clientId+scriptId]    │
 │                                │ DEBUG_MARK 前缀→带插桩编译执行        │
 │                                │ ── 命中断点: pause() ──             │
 │                                │   callback: sendByClientId(clientId)│
 │<── WS: breakpoint,scriptId,{variables,range} ───────────────┤      │
 │ HTTP 请求保持挂起(长连接)         │ producer.poll(60s)                │
 │ 用户点"继续/单步"                │                                    │
 ├── WS: resume_breakpoint,step,lines ────────────────────────>│      │
 │                                │ MagicDebugHandler.resumeBreakpoint │
 │                                │   context.setStepInto(step)        │
 │                                │   context.setBreakpoints(...)      │
 │                                │   context.singal() → 脚本继续       │
 │<── 循环直到脚本结束 ──┤                                                 │
 │<── HTTP 响应(最终结果) ──────────┤                                   │
```

- WS 消息类型（`core/config/MessageType.java`）：S→C：`LOG/LOGS`（日志）、`BREAKPOINT`（命中断点）、`EXCEPTION`（异常+行列区间）、`PING`；C→S：`SET_BREAKPOINT`（运行中改断点）、`RESUME_BREAKPOINT`（恢复/单步+新断点表）、`LOGIN`、`PONG`。
- `BREAKPOINT` 消息体：`scriptId + {variables:[{name,value(json串),type}], range:[line,startCol,endLine,endCol]}`（`MagicScriptDebugContext.getDebugInfo:66-86`；值序列化在 `DebugRequest.java:64-70`）。
- `RESUME_BREAKPOINT` 参数：`stepInto(0/1) + "|"分隔的新断点行号表`（`MagicDebugHandler.resumeBreakpoint:41-58`）——**恢复的同时可以改断点**。
- 异常：`RequestHandler.processException` 从 `MagicScriptException.getLine()` 拿 `[startLine,endLine,startCol,endCol]`，WS 推 `EXCEPTION`，前端画红色波浪线（`RequestHandler.java:351-370`）。
- 集群：`CONTEXTS` 本机查不到 → handler 返回 false → `MagicNotify(WS_C_S)` 转发到持有该 context 的实例执行；反向推送同理（`WS_S_C`/`WS_S_S`，`DefaultMagicAPIService.processNotify:176-200`）。

#### 3.5 单步实现

- 没有真正的"单步语义引擎"：**stepInto 就是把 `stepInto` 标志置 true，此后每一行（不同行号的第一个节点）的 `pause()` 都直接命中**；继续执行（不单步）时置回 false，退回"仅断点"模式。没有 stepOver/stepOut（无法跳过函数调用内部、无法跳出当前函数）。

#### 3.6 前端交互（magic-editor）

- **断点管理 = Monaco 装饰器**：行号槽点击 → 增删 `linesDecorationsClassName:'breakpoints'` 装饰（`magic-script-editor.vue:180-208`）；测试时从 `getAllDecorations()` 过滤出断点行号，`,` 拼进 `Magic-Request-Breakpoints` 头（`sendTestRequest:702-706`）。
- **断点命中**：`ws_breakpoint` 事件 → 当前行加 `debug-line` 高亮装饰 + 变量表渲染到调试面板（`magic-debug.vue`：继续/单步两个按钮 + 三列变量表格，值用递归树组件 `magic-structure` 展示）+ 切换到 debug tab（`onBreakpoint:278-296`）。
- **继续/单步**：`doContinue(step)` → WS 发 `resume_breakpoint,<step>,<断点行号'|'拼接>`（`:660-673`）。
- **异常**：`ws_exception` → 校验 scriptId 后在对应 range 画 `squiggly-error` 并 reveal（`onException:252-274`）。
- WebSocket 客户端：`ReconnectingWebSocket` 自动重连；消息经全局 `bus`（Vue event bus）以 `ws_<msgType>` 事件名广播；发送统一 `bus.$emit('message', type, content)`（`scripts/websocket.js`）。
- 2.x bundle 交叉验证：协议不变；新增 `Magic-Request-Client-Id`/`Script-Id` 头（测试时从被测文件 id 取值）；**`set_breakpoint` 在前端 bundle 中不存在**——"运行中改断点"这条服务端能力 UI 从未使用，断点变更实际都搭车在 `resume_breakpoint` 里。

#### 3.7 magic-api 方案的局限清单

| # | 局限 | 根源 |
|---|------|------|
| 1 | 无条件断点、无 logpoint、无 run-to-cursor | 断点只是 `List<Integer>` 行号 |
| 2 | 无 stepOver/stepOut | 只有 stepInto 布尔标志 |
| 3 | 无调用栈概念、无帧切换 | 脚本模型扁平，快照只有当前作用域 |
| 4 | 变量全量一次性序列化推送，对象大时卡顿/超长 | callback 里 toJsonStringWithoutLog |
| 5 | 必须先设断点才能调试 | DEBUG_MARK 按请求注入 |
| 6 | 挂起占用 HTTP 请求线程，60s 超时后静默放行 | pause 内联在请求线程 |
| 7 | 过期 resume 令牌/无超时 take 竞态 | 双队列握手设计（见 3.3） |
| 8 | 线程模型粗放：挂起期间线程池线程被占死 | 同步阻塞式调试 |
| 9 | CSV 文本协议无法表达复杂结构 | buildMessage 手拼 |
| 10 | `SET_BREAKPOINT` 服务端能力 UI 未用 | 协议与实现脱节 |

### 4. 与 Nop XLang 现状对比

Nop 侧事实（已核验，路径均相对仓库根）：

- **协议层**：`nop-dev-tools/nop-api-debugger` 11 个类，`IDebugger`（`stepInto/stepOver/stepOut/suspend/resume/waitSuspended/runToPosition/getExprValue/expandExprValue/getStackInfo/getScopeVariables/getFrameVariables/updateBreakpoints/muteBreakpoints`），`Breakpoint` 支持 **sourcePath+line+condition+logExpr**（条件断点+日志断点）。接口注释即设计意图："为任意 XDSL 加调试：仅需元模型上记录 SourceLocation，在每个动作前调用 checkBreakpoint"。
- **引擎层**：`nop-dev-tools/nop-xlang-debugger` 的 `XLangDebugger`（589 行）：`ReentrantLock` + `Condition` + 200ms 轮询窗口的挂起/恢复；按线程 id 登记 `SuspendedThread`（多线程并发挂起）；stepOver/stepOut 基于帧深度（`XLangDebugger.java:400-441`）；临时断点 runToPosition；条件断点/日志断点求值（`:496-556`）；挂起帧表达式求值（`SimpleExprParser` 编译 + `EvalRuntime.getRuntimeForFrame`，`:252-285`）。
- **注入点**：`DebugExpressionExecutor` 实现 `IExpressionExecutor`，在每个 `allowBreakPoint()==true` 的 `IExecutableExpression` 执行前调 `debugger.checkBreakpoint(expr.getLocation(), rt)`；通过 `EvalExprProvider.registerGlobalExecutor` 全局注册（`XLangDebuggerInitializer.java:74-76`）。解释执行器树上约 50% 语句级节点可断点，叶子节点跳过自身检查但 executor 继续下传子节点。
- **位置信息**：`SourceLocation`（line/col）从 parser 起随 AST 节点（`ASTNode.location`）→ 编译进 `AbstractExecutable.getLocation()` → 执行帧 `EvalFrame`，异常/栈/变量全线携带。**运行时判定断点、无需重编译**——这点与 magic-script 相反。
- **传输层**：自研 `SimpleRpcServer` TCP socket，默认 `127.0.0.1:12345`，`nop.xlang.debugger.enabled` 默认 false（`XLangConfigs.java:34-47`）；**唯一消费者是 IDEA 插件**（`nop-idea-plugin/.../XLangDebugConnector.java:26-45` socket+退避重连）。全仓无任何 WebSocket/HTTP 调试暴露。
- **已知缺口**：`EvalBackendRouter` 静态绑定产物 `EvalStaticBoundExecutable` 绕过全局 executor 且 `allowBreakPoint()=false`（`backend/EvalBackendRouter.java:87-91`）——静态优化单元不可调试；`force-interpreter` 配置存在但未与 `debugger.enabled` 联动；`XLangDebugger` 全局单例状态（`suspended/stepMode/lastSuspendThread`），无会话隔离。

#### 对比表

| 维度 | magic-api (magic-script) | Nop xlang-debugger |
|------|--------------------------|--------------------|
| 断点注入 | 编译期插桩 `pause()`（按请求重编译） | 运行期 executor 包装，零重编译 |
| 断点能力 | 行号列表 | 行 + 条件表达式 + logpoint + runToPosition |
| 单步 | 仅 stepInto（每行停） | stepInto/Over/Out（帧深度判定） |
| 调用栈/多帧 | 无 | 有（SuspendedThread + frameIndex） |
| 表达式求值 | 无 | 有（挂起帧内 eval + 变量树懒展开） |
| 多线程挂起 | 单 context 串行 | 多线程并发登记 |
| 变量回传 | 全量 JSON 一次性推送 | 懒展开（getFrameVariables/expandExprValue） |
| 触发方式 | 每次测试请求携带断点 | 持久断点管理器（updateBreakpoints） |
| 挂起原语 | 双 BlockingQueue 握手（有竞态） | Lock/Condition + 超时轮询（可中断、有 cleanup 线程） |
| 传输 | WebSocket（浏览器直连）+ 集群转发 | TCP socket（仅 IDEA 桌面端） |
| 会话模型 | 每请求一个 context，天然隔离 | 全局单例状态，无会话 |
| 超时保护 | 60s 自动放行 | monitorWaitInterval 轮询 + close 中断 |

**一句话：Nop 的调试引擎能力是 magic-api 的超集，magic-api 领先的是"浏览器可达"的通道与前端。**

### 5. 与当前项目的关系

#### 可借鉴

1. **WebSocket 作为浏览器调试通道 + 事件推送模型**：`BREAKPOINT/EXCEPTION/LOG/LOGS` 四类推送 + `RESUME/SET` 两类控制，Nop 可直接映射到 `IDebugger` 的方法与 `IDebugNotifier` 回调；消息封包应改用 JSON（拒绝 CSV）。
2. **日志管道设计**：`ThreadLocal(sessionId) + ROOT logger Appender + 攒批(100条/1s) flush + 按 clientId 定向推送`——XLang 在线 eval/Biz 调试的日志回传可直接套用。
3. **测试请求调试触发**：控制台测试请求带头字段标识 + 断点列表，服务端"测试即调试"，与正常请求零耦合（`requestedFromTest` 判定 + `afterCompletion` 暴露 CORS 头）。
4. **前端交互范式**：Monaco 装饰器管理断点/当前行/异常波浪线；断点表随请求携带、随 resume 更新；WebSocket 自动重连 + event bus 分发。
5. **集群消息转发**：本机无目标 session/context 时经内部总线转发到持有者实例执行（`WS_C_S/WS_S_C`），对 Nop 集群部署的调试会话路由有直接参考价值。
6. **热发布模型**：文件事件 → 动态注册/注销（Nop 对应 Delta 模型热加载，已有基础）。

#### 不可借鉴（Nop 已有更好方案或方案本身有缺陷）

1. **编译期按行插桩 + 按请求重编译**：Nop 解释执行器天然带 `SourceLocation`，executor 包装即可断点；重编译方案还带来"必须先设断点"和"调试/正常两份编译产物"的负担。
2. **双 BlockingQueue 握手**：存在过期信号与无限阻塞竞态（3.3）；Nop 的 Lock/Condition + 超时轮询 + cleanup 线程更健壮。
3. **变量全量序列化推送**：应采用 Nop 已有的懒展开协议（`expandExprValue(keys)`）。
4. **CSV 文本协议**：直接上 JSON envelope `{type, payload}`。
5. **无会话全局态**：magic-api 用"每请求 context"回避了会话问题；Nop 引入 Web 端时必须设计**每会话一个 `XLangDebugger` 实例**（当前单例 `suspended/stepMode` 字段会互相干扰，需要会话化改造）。

### 6. 设计约束更新（2026-09-26 用户裁定）

以下三条为用户裁定，约束后续 design：

**裁定 1：前端基座 = nop-chaos-flux 现有 code editor，在其上改进，不引入 Monaco。**

- 已核实 `nop-chaos-flux`（worktree `/Users/abc/app/nop-chaos-flux-wt/nop-chaos-flux-master`）：
  - `packages/flux-code-editor`（`@nop-chaos/flux-code-editor`）= **CodeMirror 6** + React 19 渲染器，经 flux renderer registry 懒加载注册（`src/index.ts`）。语言经 `createLanguageExtension(language: EditorLanguage)` 注册（`src/extensions/base.ts:59`），已内置 js/ts、sql（多方言+执行面板）、json、html、css、python、markdown、yaml、xml 与表达式扩展（lint/补全/模板装饰，`src/extensions/expression/*`）；另有变量面板（`variable-panel.tsx`）、merge diff（`use-merge-view.ts`）、代码片段面板。
  - `packages/nop-debugger` = **页面运行时 devtools 面板**（overview/timeline/network/node 四 tab、节点求值解释、inspect 模式、脱敏），是"页面为什么渲染成这样"的解释器，**不是源码级断点调试器**；其面板 UX（tab 结构、事件时间线、证据引用）可作为 XLang 调试面板的交互先例，但两者职责不同，不能混同。
- 对 XLang 编辑器的落地含义（改造点清单，供 design 细化）：
  1. 新增 `xlang`/`xpl` EditorLanguage：CodeMirror Lezer grammar（可参考 `nop-idea-plugin` 的语法规则与 `nop-treesitter` 的 grammar 编写经验）；表达式 lint 对接后端编译诊断。
  2. 新增调试扩展：断点 gutter（`gutter()` + 行号槽点击，对应 magic-editor 的 decorations 方案）、当前挂起行 `Decoration.line` 高亮、异常波浪线（`Decoration.mark`）。
  3. 调试侧栏：变量树（对接 `getFrameVariables`/`expandExprValue` 懒展开）、调用栈切换（frameIndex）、表达式求值输入框（`getExprValue`）、继续/单步按钮；复用 flux-code-editor 的 `SQLExecutionConfig`+结果面板模式做"表达式→值"面板。
- 注意 magic-api 前端用的是 Monaco，其交互范式（§3.6）可借鉴，但 API 落地全部换为 CodeMirror 对应物（decorations→`Decoration`/`gutter`，markers→lint 扩展）。

**裁定 2：挂起超时 = 前端租约续期（lease + renewal），前端消失后后端不得永久阻塞。**

- 语义：断点挂起时授予租约 TTL（如 30s）；前端处于前台/调试视图可见时周期发送续期（复用 WS 心跳帧或独立 `renew` 控制消息）；TTL 到期未续 → **自动 resume**（不是杀线程——脚本必须继续走完，否则请求线程泄漏），并向前端推送"租约过期已放行"事件。
- 与现有实现对照：
  - magic-api：固定 `Debug.timeout=60s`，`producer.poll(timeout)` 到期静默放行——无续期、无通知，用户休眠/切屏回来断点已飞（§3.3 局限 #6 的改进方向）。
  - Nop `XLangDebugger.monitorWait`：`resumeCondition.await(monitorWaitInterval=200ms)` 轮询，可中断、`close()` 可强制放行（抛 `ERR_XLANG_DEBUGGER_ALREADY_CLOSED`）——**已具备"不永久阻塞"的底座**，缺的只是租约计时与续期消息。
- 设计要点（留给 design）：租约状态挂在 SuspendedThread（per-thread TTL 而非全局）；页面 `beforeunload`/WS 断连即视为放弃续期（即刻或短宽限后放行）；续期与心跳合帧避免消息风暴；多标签页打开同一会话时的续期归属（最后写者胜/共享租约）需定义。

**裁定 3：多用户并发 = 每用户（每调试会话）独立 debugger 实例。**

- 现状障碍（§4 缺口）：`XLangDebugger` 的 `suspended/stepMode/lastSuspendThread/breakpointsMuted/tempBreakpoint` 全是实例字段，且通过 `EvalExprProvider.registerGlobalExecutor` 注册**全局单例** executor（`XLangDebuggerInitializer.java:74-76`）——两个用户同时调试会互相踩挂起状态与断点表。
- 改造方向（留给 design 细化，二选一或混合）：
  - **方案 A：会话路由 executor**。保持单一全局 executor 外壳，内部按 `EvalRuntime` 上的会话标识（注入到 EvalContext/ThreadLocal）路由到该用户的 `XLangDebugger` 实例；无会话标识的执行走空实现。改动集中在 `DebugExpressionExecutor` 一层，`IExpressionExecutor` SPI 不动。
  - **方案 B：runtime 级注入**。`EvalRuntime`/执行动作创建时按会话绑定 executor，绕过全局注册。侵入面更大，需动 `XLang.execute` choke point（`XLang.java:47-54`）。
  - 每会话一个 `XLangDebugger` 实例（含自己的 `BreakpointManagerImpl`、`suspendedThreads`、租约状态）；会话生命周期 = 前端 WS 连接（断连→`close()` 放行全部挂起线程→销毁实例）；与 magic-api "每请求一个 context"的天然隔离殊途同归，但 Nop 引擎是有状态服务，隔离单位应是"会话"而非"请求"。
- 多线程语义不变：同一会话内仍支持多线程并发挂起（`suspendedThreads` 按 threadId 登记），单步命令只作用于 `lastSuspendThread`，多线程同时挂起时的单步目标选择策略需在 design 中定义（现状即存在，非新问题）。

---

## Conclusion

（Status=open，以下为建议方向，待 `ai-dev/design/` 立项评审）

- magic-api 的在线调试 = **"编译期按行插桩 + HTTP 头携带断点 + WebSocket 推送 + 阻塞队列挂起"**；其价值主要在通道设计（WS 消息集、日志管道、集群转发、前端交互），调试内核本身（magic-script）能力弱于 Nop 现有的 `XLangDebugger`。
- 给 Nop XLang 做在线开发调试编辑器的最短路径（已按 §6 三条用户裁定修正）：**保留 `IDebugger`/`XLangDebugger` 引擎 + 新增 Web 网关（WebSocket/JSON 映射 `IDebugger` 方法与 `IDebugNotifier` 事件）+ 前端在 nop-chaos-flux `flux-code-editor`（CodeMirror 6）上加 xlang 语言与调试扩展**；挂起采用租约续期模型（前端消失不永久阻塞）；调试器会话化（每用户独立实例，会话路由 executor）；同时补齐 `debugger.enabled` 与 `force-interpreter`/静态绑定产物的联动（否则断点不生效）、在线 eval REPL 端点。
- 不建议照搬 magic-api 的按请求重编译、变量全量推送与固定超时模式。

## Open Questions

- [x] ~~前端基座选型~~ → 已裁定：nop-chaos-flux `flux-code-editor`（CodeMirror 6）上改进（§6 裁定 1）。
- [x] ~~挂起超时策略~~ → 已裁定：租约续期 + 到期自动放行（§6 裁定 2）。
- [x] ~~多用户并发模型~~ → 已裁定：每用户独立 debugger 实例（§6 裁定 3）。
- [ ] Web 传输与续期通道：WebSocket 端点挂哪（独立端点 vs 复用 `/r/` RPC 面）？续期帧与心跳是否合帧？GraphQL subscription 是否有额外收益？
- [ ] `XLangDebugger` 会话化的最小改动面：方案 A（会话路由 executor）vs 方案 B（runtime 级注入）的取舍；会话标识如何在 `EvalRuntime` 中传递（嵌套 action/函数调用是否可靠透传）。
- [ ] `EvalStaticBoundExecutable` 绕过 executor：调试会话内是否强制 interpreter，还是按单元粒度降级？运行期切换是否可行？
- [ ] 在线 eval REPL 的安全边界（沙箱、权限、禁写 Delta）与 `nop-biz` 权限模型对接。
- [ ] XLang 的 CodeMirror Lezer grammar 从哪来：参照 `nop-idea-plugin` 语法规则新写、复用 `nop-treesitter` 的 grammar 资产、还是 XText/ANTLR 定义转译？
- [ ] 多标签页打开同一调试会话时的租约归属与单步目标选择（最后写者胜 or 共享租约）。
- [ ] 多人协作（magic-api 有 INTO_FILE_ID/USER_LOGIN 协同消息）是否纳入一期？

## References

- 本地源码：`/Volumes/data/sources/magic-api`（master `e1cdccaa`）、`/Volumes/data/sources/magic-script`（v1.9.0）、`/Volumes/data/sources/magic-editor-1.7.5-src`（前端历史源码快照，`git archive 052e43ff^`）
- nop-chaos-flux（前端基座，worktree `/Users/abc/app/nop-chaos-flux-wt/nop-chaos-flux-master`）：`packages/flux-code-editor/src/extensions/base.ts`（语言注册）、`packages/flux-code-editor/src/index.ts`（renderer 注册）、`packages/nop-debugger/src/types.ts`（页面 devtools 面板定位）
- Nop 现状锚点（关键类）：
  - `nop-dev-tools/nop-api-debugger/src/main/java/io/nop/api/debugger/IDebugger.java`
  - `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/XLangDebugger.java`
  - `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/initialize/XLangDebuggerInitializer.java`
  - `nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/EvalExprProvider.java`
  - `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/backend/EvalBackendRouter.java`
- 相关文档：`ai-dev/audits/check/dev-tools.md`（调试器模块既有审计）、`ai-dev/design/xlang-truffle/00-vision.md`（一期明确不做 debugger 的边界）、`docs-for-ai/02-core-guides/debugging-and-diagnostics.md`（DevDoc/DevTool，非调试器）、`docs-for-ai/01-repo-map/module-groups.md`（注意：模块表尚无 `nop-dev-tools/` 条目）
- 历史相关分析：`ai-dev/analysis/2026-09/2026-09-07-tree-sitter-runtime-architecture.md`
