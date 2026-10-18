# xlang-web-debugger 安全深度审计（roadmap item 25）

- **日期**：2026-10-18
- **审计者**：独立子代理（非实现者），对抗性方法：对每个疑点构造具体攻击（攻击者角色 + 请求序列），再以 file:line 执法代码 CONFIRM/REFUTE
- **范围**：REPL 服务（ReplService/ReplAccessGuard）、WS 鉴权（DebugAuthenticator/DebugWebSocketHandler/DebugSessionBinder）、沙箱逃逸面、越权面、DoS 面、审计日志完备性
- **证据基线**：branch `feat-xlang-web-debugger` @ `84fea0df6c`；设计契约 `ai-dev/design/xlang-web-debugger/07-repl-security.md`（D6）；item 14 收口记录 `ai-dev/logs/2026/10-16.md`（F5/F6/F7 登记 Non-Blocking/item 25——本审计即该 successor）
- **严重度定义**：P0=未鉴权可达/跨用户越权/沙箱逃逸可利用；P1=深度防御缺口；P2=加固建议；P3=观测性

---

## 摘要

REPL 服务的**鉴权链与越权面总体扎实**：401/403 fail-closed、属主同码防探测、WS 4401、限流均有服务端强制，未发现未鉴权可达或跨用户越权路径（详见 §3 REFUTED 清单）。

但**沙箱层被击穿**：`ReplService.validateSandbox` 的 import 白名单是**纯正则词法校验**（`ReplService.java:64`），而 XLang 语法允许三种正则覆盖不到的合法 import 形态（无分号 ASI / 同行多 import / 字面量 xlib import），三者均可绕过"默认拒绝"直接导入任意 classpath 类（→ `Runtime.getRuntime().exec` 级 RCE）或加载任意 VFS xlib（→ 编译期宏执行）。这是对 D6 §3.3 "白名单外默认拒绝" 契约的**实现级违背**，判 P0。

其次，**取消机制的 per-entry 粒度存在具体可达的语句内长构造**（`new RegExp("(a+)+$")` 回溯、`String.repeat` 内存放大），以及 `debugger.getExprValue` 完全绕过 deadline 通道的求值面；**WS 通道权限码（D6 §3.4 矩阵 WS 行）未落地**；**审计存在拒绝路径与 Error 路径盲区**。

---

## 一、鉴权链完整表

### 1.1 HTTP REPL 端点（`POST /r/nop-xlang-debug/compile|execute`）

宿主壳：quarkus `nop-quarkus-web/.../NopXlangDebugReplWebService.java:34-75`、spring `nop-spring-web-starter/.../NopXlangDebugReplWebService.java:38-92`（薄壳只抽头/映射状态码；runtime 单例为 null → 503）。所有门控在容器无关核心，gating 顺序如下：

| # | 检查点 | compile 落点 | execute 落点 | 失败行为 | 证据 |
|---|--------|------------|-------------|---------|------|
| 1 | 网关开关 | `singleton()==null \|\| replService==null` → 503 | 同左 | 503 拒绝（fail-closed） | quarkus 壳 :42-44 |
| 2 | 登录态 | `guard.checkAccess(headers, PERM_COMPILE)` | 同左（PERM_EXECUTE） | extractor 为 null、抛 NopLoginException、或返回 null → **401**；extractor 本身缺失 → DebugWebServerConfig 装配**拒绝型 extractor（恒 null）→ 401** | ReplAccessGuard.java:36-46；DebugWebServerConfig.java:51-59 |
| 3 | 权限码 | 同上（authChecker） | 同上 | checker 缺失（tryGetBeanByType 为 null）或 `isPermitted=false` → **403**（"无鉴权不开放"红线，fail-closed） | ReplAccessGuard.java:47-48；DebugWebServerConfig.java:62-68 |
| 4 | 会话属主 | 不适用（无 sessionId） | `isSessionOwner(sessionId, actor)` | **403 `nop.err.xlang.debugger.session-bound-elsewhere`，不存在/他属同码**（防探测） | ReplService.java:156-158；DebugSessionBinder.java:316-322 |
| 5 | source 尺寸 | `checkSourceSize` → 413 | 同左（在属主检查之后） | 413 | ReplService.java:159, 262-266 |
| 6 | 沙箱词法 | `validateSandbox` → 400 | 同左 | 400（import 白名单 / Promise-async / `__repl*`）——**本次审计证明可绕过，见 SEC-01/02/03** | ReplService.java:100, 160, 269-282 |
| 7 | enabled 运行期谓词 | **缺失（SEC-08）** | `enabledPredicate.getAsBoolean()` → 403 | 403 | ReplService.java:162-163 |
| 8 | 会话存在 | 不适用 | `sessionManager.getSession` + `isDestroyed` → 403 同码 | 403 | ReplService.java:165-168 |
| 9 | 全局并发 | **无（SEC-08）** | `globalPermits.tryAcquire()` → 429 | 429 `nop.err.xlang.debugger.rate-limited` | ReplService.java:170-172 |
| 10 | 同会话串行 | 不适用 | `inFlightSessions.add` 失败 → 429（finally 释放 permit，配对正确） | 429 | ReplService.java:173-177, 211-214 |
| 11 | 超时窗 | compile deadline = min(config, 30s) | execute deadline = min(timeoutMs\|config, 120s) | `ReplCancelledError`（Error 系）→ cancelled/timeout 响应 | ReplService.java:103-105, 179-181 |

extractor 生产实现 = BeanContainer 的 `IUserContextExtractor`（ILoginService），与平台 Web 鉴权同链（DebugAuthenticator 把 Bearer token 映射为 `Authorization` 头走同一 `extractFromHeaders`，DebugAuthenticator.java:39-44）。**结论：HTTP 链 fail-closed 成立，未鉴权不可达。**

### 1.2 WS 端点（`/ws/xlang-debugger`）

| 方法族 | 鉴权点 | 失败行为 | 证据 |
|--------|--------|---------|------|
| 握手（一切方法前提） | `runtime.authenticate(nopDebugToken)` → userId | null / NopLoginException → **4401 关闭**，`onMessage` 对 `!authenticated` 静默丢弃 | DebugWebSocketHandler.java:96-108, 130；XLangDebugWebSocketEndpoint.java:43-46；测试 TestDebugGatewayProtocol.testAuthFailures4401 |
| tokenRefresh | `authenticator.refresh(accessToken)` + **用户一致性校验** | 无效 → 4401；换人 token → **4403 关闭**（防连接身份置换） | DebugWebSocketHandler.java:279-306 |
| session.open | 依赖握手身份（无独立权限码——SEC-05） | 重复 open → 4409 | DebugWebSocketHandler.java:219-227；DebugSessionBinder.java:111-115 |
| session.rebind | `sessionOwners` 属主校验，违反 → SESSION_BOUND_ELSEWHERE（不存在/他属同码，防探测） | -32000 错误 | DebugSessionBinder.java:157-162 |
| session.close / lease.renew | 绑定本连接方可操作（connectionId 服务端生成） | NO_SESSION | DebugSessionBinder.java:234-239, 210-227 |
| debugger.*（24 方法） | 握手身份 + **本连接 binding**（binding 只能由本连接 open/rebind 创建，userId 恒等 boundUserId）；suspended 线程预检 | NO_SESSION / THREAD_NOT_SUSPENDED | DebugWebSocketHandler.java:310-318, 577-581 |

**WS 层没有任何 `IActionAuthChecker` 参与者**（全模块 grep 仅 ReplAccessGuard 使用 authChecker）——D6 §3.4 矩阵 WS 行"已登录无权限 → 会话创建拒绝（ERR_XLANG_DEBUGGER_PERMISSION_DENIED）"未落地，判 SEC-05（P1）。

---

## 二、沙箱逃逸面（P0 发现）

### SEC-01（P0，CONFIRMED）import 白名单被 ASI（无分号）绕过——默认配置即可达任意类导入

**攻击者**：持有 `nop/xlang/debug/ReplExecute` 权限的已登录用户（REPL 的目标用户群）。
**请求序列**：

```
POST /r/nop-xlang-debug/execute
{"sessionId":"u1:conn-1","source":"import java.lang.Runtime\nreturn Runtime.getRuntime().exec('id').text"}
```

**执法代码 vs 语法事实**：

- 白名单校验正则 `IMPORT_PATTERN = (?m)^\s*import\s+([\w.]+)\s*;`（ReplService.java:64）**强制要求分号**。
- XLang 语法 `eos__ : SemiColon | EOF | {lineTerminatorAhead}? | {closeBrace}?`（XLangParser.g4:637-641）；`lineTerminatorAhead` 检查 HIDDEN 通道 LineTerminator（XLangParserBase.java:96-130），lexer 将 `\r\n` 送 HIDDEN 通道（XLangLexer.g4:209）——**JS 式 ASI 成立，import 后换行即为合法语句终结**。
- 解析后 `LexicalScopeAnalysis.visitImportAsDeclaration` → `XplParseHelper.runImportExpr`（LexicalScopeAnalysis.java:252-254；XplParseHelper.java:695-721）→ `scope.getClassModelLoader().loadClassModel(fqn)` + `scope.addImportedClass`——**第二层无任何白名单校验**。
- 导入类即可静态调用 + 构造（D6 §3.3 自己声明的能力面）：`Runtime.getRuntime().exec(...)`、`Files.readAllBytes`、`Socket` 等。

**影响**：默认白名单为空（XLangDebuggerConfigs.java:66-68 `""`）时，`validateSandbox` 对无分号 import **一次正则都不匹配、白名单根本不被咨询**，400 门完全旁路 → 编译+执行成功 → **JVM 级 RCE**（以服务进程身份）。这是"import 默认拒绝"契约（D6 §3.3）的实现级失效。
**修复方向**：词法校验改为 AST 级——在 `LexicalScopeAnalysis`/compile 前遍历 `Program` 收集全部 `ImportAsDeclaration`（含 QualifiedName 与 Literal 两态）逐一校验；或在 `runImportExpr` 增加 REPL 专用 import 拦截面（scope 上传入 whitelist checker）。词法补丁（允许无分号/同行）不可靠，不建议。

### SEC-02（P0，CONFIRMED，条件=白名单非空）同行第二个 import 不被校验

**攻击序列**（运维已按白名单放行 `java.lang.String`，如 TestReplService.java:189-196 的合法用法）：

```
source: "import java.lang.String; import java.lang.Runtime; return 1;"
```

- `IMPORT_PATTERN` 为 MULTILINE `^` 锚定 + `find()` 循环：首 match 消费 `import java.lang.String;` 后，扫描位置在其后**同行**，`^\s*import` 不再成立 → 第二个 import **不匹配、不校验**。
- 编译器将两个 ImportAsDeclaration 全部解析注册（`topLevelStatements_: e=ast_topLevelStatement*` 序列语法，XLangParser.g4:29-34）→ `Runtime` 可用 → RCE。
- 同理：任意"行首放一个白名单类 + 同行跟随任意个非白名单 import"的组合。

**影响**：白名单一旦非空（白名单存在的意义就是放行少量安全类），即提供藏匿通道。
**修复方向**：同 SEC-01（AST 级校验根治）。

### SEC-03（P0，CONFIRMED）字面量 xlib import 完全绕过校验——任意 VFS xlib 加载（含编译期宏执行）

**攻击序列**（默认空白名单即可）：

```
source: "import \"/nop/core/xlib/test.xlib\" as T;\nreturn T.someFn(1);"
```

- `ast_importSource: qualifiedName | literal_string`（XLangParser.g4:104-106）——`import "<path>.xlib"` 是一等语法。
- 该形态 source 是 Literal，`IMPORT_PATTERN` 的 `[\w.]+` 无法匹配（首字符 `/`、`"`）→ **零校验**。
- `ImportAsDeclaration.isImportLib()`（source instanceof Literal，ImportAsDeclaration.java:37-39）→ `XplLibHelper.loadLib`：仅检查 `.xlib` 后缀后 `ResourceComponentManager.loadComponentModel(任意VFS路径)`（XplLibHelper.java:62-70）——**无 allowlist**，Delta 层/平台全部 xlib 可达。
- xlib 的 `@Macro` 标签在**编译期执行**（如 GlobalFunctions 中 static_eval 等 Macro 同机制）——即绕过 compile/execute 区分，获得"编译面即可执行平台 xlib 代码"的面；xlib 亦可在自身源内 import 任意 Java 类（xlib 属受信模型，但其能力整体暴露给 REPL）。

**影响**：默认拒绝契约三重失效的第三形态；且把"平台 xlib 全集"纳入 REPL 可达面（包括 dao/orm/io 类 xlib 若存在）。
**修复方向**：同 SEC-01（ImportAsDeclaration 两种 source 形态统一走白名单；xlib 路径与 Java FQN 共用同一默认拒绝清单）。

### 2.1 沙箱白名单 vs XLang 语言能力差距表

即使 import 白名单被完美执行（修复 SEC-01/02/03 后），**白名单只约束 `import` 语句**，以下零 import 能力面不受 `validateSandbox` 任何约束：

| 能力 | 无需 import 的到达方式 | 能做什么 | 判定 |
|------|----------------------|---------|------|
| **IoC 全容器** | 全局函数 `inject(beanNameOrType)`（GlobalFunctions.java:278-286，`scope.getBeanProvider().getBean`） | 取任意 bean：DataSource → 直接 SQL 读写、ORM、RPC 客户端、资源管理器——**白名单安全等式失效**：可达面 = 全部 bean，不是白名单 | P1（SEC-04）。D6 §3.3"一期不做运行时拦截"已声明残余风险方向，但未点名 inject；"白名单可达面 × 权限 × 时间窗 × 审计"四要素中白名单要素对 bean 面为空集约束 |
| **任意 VFS 模型读取** | `loadDslModel(path)` / `loadDeltaJson(path)`（GlobalFunctions.java:438-447） | 读取任意模型/JSON 资源（orm/beans/task/xmeta…），跨模块配置探测 | 同上 |
| **scope 读写** | `get(scope,name)` / `assign(scope,name,value)` / `getByPropPath`（GlobalFunctions.java:174-187, 205-211） | 写任意 scope 键。**REFUTE 取消通道篡改**：deadline/旗标走 ThreadLocal 不经 scope（ThreadLocalReplDeadlineChannel.java:20-23），`assign` 够不到；`__repl*` 保留标识符正则（ReplService.java:66, 280-281）防的是直接标识符引用，`assign("__replX",…)` 字符串形态可写键但该键无机制语义——**不构成取消通道逃逸** | 残余纵深观察项 |
| **JS 风格全局静态类** | 裸名 `Math/JSON/Number/Date/Object/Promise` → JsXxx 静态类映射（LexicalScopeAnalysis.java:143-157 JS_GLOBAL_VARS）；`new Date/Array/Map/Set/RegExp/Error/Promise` → 类型别名（:155-158 TYPE_NAME_ALIAS，`Array`→`java.util.ArrayList` 等） | `JSON.parse`、`Object.keys`、集合构造等；`Promise/async/await` 字样被 ASYNC_PATTERN 词法禁用（ReplService.java:65, 277-279）——**裸名映射本身不泄露 Promise 旁路**（引用必须写出"Promise"字样）；但 `new RegExp`（JsRegExp 包装 `java.util.regex.Pattern`，JsRegExp.java:24-46）与 regex 字面量（XLangLexer.g4:12）**不受任何禁用** → SEC-06 | 部分构成 SEC-06 |
| **阻塞调用** | 声明面（D6 §一.6/§3.3 残余①）：导入类方法阻塞使 deadline 无法触发 | `Thread.sleep` 类不可直达（需 import），一期空白名单下**未发现零 import 阻塞内建**（GlobalFunctions/DebugHelper/LogFunctions grep 无 sleep/wait/io-block） | 维持 D6 声明，无新增构造 |
| **异常吞噬** | `try{...}catch(e){}` 脚本语法 | 吞 Exception 系；取消异常为 Error 系（ReplCancelledError extends Error，ReplCancelledError.java:16）直达服务边界——**设计裁定成立，REFUTE 吞异常 livelock**（一期 Promise 禁用后无 catch(Throwable) 面） | — |

---

## 三、取消机制（deadline）绕过分析

取消机制本体按设计工作：`DebugExpressionExecutor.execute` 每入口先 `deadlineChannel.checkDeadline` 再 `checkBreakpoint` 再自线程化（DebugExpressionExecutor.java:38-45）；destroyed 分支同粒度（DestroyedSessionExecutor.java:39-46）；通道为 ThreadLocal deadline + 共享 volatile 旗标、脚本不可见（ThreadLocalReplDeadlineChannel.java:35-75）；测试实证 `while(true){}` 在 deadline 内被打断（TestReplService.testExecuteDeadlineInterruptsInfiniteLoop）。**每回边检查的覆盖面对"纯脚本循环"成立。**

绕过面 = **单个 executable 入口内长时间不返回的构造**（入口检查之间）：

### SEC-06（P1，CONFIRMED）语句内长构造：ReDoS / 内存放大 / 深递归

| 构造 | 攻击序列（默认空白名单、无禁用词） | 机制 | 后果 |
|------|--------------------------------|------|------|
| 正则回溯 | `return new RegExp('(a+)+$').test('a'.repeat(200000)+'b');` 或 regex 字面量 `/(a+)+$/.test(...)` | `Pattern.compile/match` 在单个 MethodCallExecutable 入口内完成（JsRegExp.java:24-46 无超时），期间**零 executor 入口** | 线程回转至小时级；期间 1 个全局 permit + 同会话槽被占；4 个并发（默认 max-concurrent=4，XLangDebuggerConfigs.java:62-64）即可**整体瘫痪 REPL 服务**；无外部 cancel 端点（一期无 HTTP cancel），只能等回溯自然结束或重启 |
| 内存放大 | `return 'x'.repeat(1000000000);` | String.repeat 单入口分配 2GB char | `OutOfOfMemoryError`（Error 系）穿透 `catch(Exception)`（ReplService.java:207）→ 容器 500；重复 2-3 次即可触发堆抖动/Full GC 影响同进程业务——**OOM DoS 面**（D6 残余②的具体化）。另：Error 路径**不写审计**（SEC-09） |
| 深递归/深嵌套 | `return JSON.parse('['.repeat(50000)+']'.repeat(50000));` | 解析递归 → `StackOverflowError` 穿透，同上不写审计 | 单请求 500；SOE 已在栈展开后线程存活，无永久损伤；审计盲区是主要问题 |

**严重度**：P1（D6 §一.6/§3.3 已诚实声明"入口内阻塞取消延迟无上界/内存无预算/深递归未专项定义"，本审计将其从"声明"推进为"默认配置可达的具体构造"，且正则面没有任何声明覆盖——`new RegExp` 不在禁用清单）。
**修复方向**：a) REPL 编译面把 `RegExp/正则字面量` 与 Promise 同等列入编译面禁用或包装为可中断实现；b) `String.repeat/JSON.parse` 类放大点设结果/输入预算；c) 中期：进程隔离/worker 池（D6 §3.2 三选一被拒项，建议 item 25 复核时重新裁定——ReDoS 不可中断使"时间窗"要素对单入口构造失效）。

### SEC-07（P1，CONFIRMED）`debugger.getExprValue/expandExprValue` 完全绕过 deadline 通道

**攻击序列**：已认证 WS 用户 open 会话 → 业务代码挂起在断点（自己的 REPL 或 item 21 后端链路）→ 发送：

```
{"method":"debugger.getExprValue","params":{"threadId":N,"frameIndex":0,
 "expr":"new RegExp('(a+)+$').test('a'.repeat(150000)+'b')"}}
```

- `XLangDebugger.eval` 用 `DefaultExpressionExecutor.INSTANCE` 直执行（XLangDebugger.java:409-417）——**非融合执行器、无 checkDeadline、无取消旗标消费**；表达式上限仅 8KB（DebugWebSocketHandler.java:64, 530-536）。
- WS dispatch 线程被 ReDoS 占死，**无任何超时/取消/审计**；多连接可耗尽 WS 调度资源。
- 附：watch 求值绕过断点/调试钩点本身是合理设计（观察器不应触发挂起），缺的是**时间界**。
**修复方向**：eval 包 `channel.begin/end` 窗口 + 换用可检查 executor；或对 watch 表达式禁用函数调用类构造（白名单只读属性访问）。

### REFUTED（取消机制）清单

- **吞后 livelock**：Error 系取消 + Promise 编译期禁用（ReplService.java:65）→ 一期无 catch(Throwable) 面。成立。
- **scope 键篡改取消通道**：通道不经 scope（见 §2.1 行 3）。不成立。
- **跨线程 cancel 误伤**：`checkDeadline` 对无窗口线程早退（ThreadLocalReplDeadlineChannel.java:63-68），爆炸半径收口正确。

---

## 四、越权面

### REFUTED：跨用户会话访问（设计红线成立）

攻击者 u2 尝试访问 u1 的会话 `u1:conn-1`（格式 `{userId}:conn-{AtomicLong}`，DebugSessionBinder.java:117；DebugGatewayRuntime.java:153-155——**可猜测**，但）：

| 攻击路径 | 执法点 | 结果 |
|---------|--------|------|
| HTTP execute 带他人 sessionId | `isSessionOwner` → 403，与"不存在"同码（防探测）；测试钉子 TestReplService.testGateOrder e3/e4 | REFUTE |
| WS rebind 到他人会话 | `sessionOwners.get + owner.equals(userId)` → SESSION_BOUND_ELSEWHERE 同码（DebugSessionBinder.java:158-162） | REFUTE |
| WS debugger.* 直连他人会话 | 方法只取 `getBinding(本连接 connectionId)`（DebugWebSocketHandler.java:311），binding 仅能由本连接 open/rebind 创建且 userId 恒等 | REFUTE |
| 猜测 connectionId 复用 | connectionId 服务端 AtomicLong 生成，不收客户端输入 | REFUTE |
| sessionId 出现在他人 WS 事件 | bridge 按 sessionId 一对一出站（GatewayNotifierBridge 每会话持有自身 session） | REFUTE |

**残余观察（P3，SEC-11）**：可猜测 sessionId 虽无越权影响，但出现在审计日志/前端日志中的 `u1:conn-1` 便于枚举用户名；建议改不透明 id（`UUID`）。

**断点跨用户观测面（专门核查后 REFUTE）**：断点按 session 标记路由——`SessionRoutingExecutor` 只把带本会话标记的执行路由给本会话 executor（SessionRoutingExecutor.java:51-60），无标记执行落**默认会话**（其 debugger 不暴露给任何 WS binding，DebugSessionBinder.open 恒新建会话）。用户断点无法挂起他人的业务执行，`getStackInfo/getScopeVariables` 的 threadId 又受 `requireSuspended` 本会话预检（DebugWebSocketHandler.java:577-581）。**观察项**：item 21 后端链路把任务执行标记进会话时，须在标记侧校验"提交人 == 会话属主"（本次审计未发现违反点，登记为后续接入的必查项）。

### SEC-05（P1，CONFIRMED）WS 通道权限码未落地

- D6 §3.4 权限矩阵登记 WS 行权限码 `nop/xlang/debug/DebugSession`（未登录 4401 / 已登录无权限"会话创建拒绝"）；实现中 handler/binder **全程无 IActionAuthChecker**（DebugWebServerConfig 只把 checker 装配给 ReplAccessGuard，:64-68）；`ERR_XLANG_DEBUGGER_PERMISSION_DENIED` 在 XLangErrors 中**不存在**（grep 零命中）。
- 攻击者：任意已登录但未授予调试权限的用户 → WS 握手成功 → `session.open` 成功 → 全部 24 个 debugger 方法 + rebind/renew 可用（作用域限于自身会话的标记执行，见上——越权放大受控，故 P1 而非 P0）。
- 与设计契约直接不符（矩阵承诺"无权限拒绝"），实现层静默降级为"仅认证"。
**修复方向**：session.open 前经 authChecker 校验 `nop/xlang/debug/DebugSession`；登记点分错误码（D1 additive 规则）。

### REFUTED：lease.renew 重放

renew 以服务端生成的 connectionId 为键（requireBinding），重放帧必须来自已认证连接本身；限流在**服务端强制**：`now - lastRenew < renewInterval × 0.8` → RATE_LIMITED（DebugSessionBinder.java:210-227，不关闭连接，对齐 D1）。open/rebind 后首 renew 豁免为协议语义非漏洞。

### SEC-13（P3）tokenRefresh 有效 token 预言

`tokenRefresh` 对他人**有效** token 回 4403（user mismatch），对无效 token 回 4401（DebugWebSocketHandler.java:287-302）——为持有他人 token 的攻击者提供"token 是否有效"的区分预言。前提是攻击者已持有效连接 + 疑似窃取的 token 本体，增益极小。可在 mismatch 响应上对两类失败统一关闭码（延迟到 item 25 后续）。

### SEC-12（P3）nopDebugToken 走 URL query

D1 §3.2 已接受的设计权衡（浏览器 WS 无法带自定义头）；token 进入访问日志/代理日志/浏览器历史的泄漏面为运营注意事项：确保 `/ws/xlang-debugger` 不落入通用 access log，token 短 TTL。

---

## 五、DoS 面

| # | 面 | 裁定 | 证据 |
|---|----|------|------|
| 1 | execute 高频提交（RATE_LIMITED 是否服务端强制） | **CONFIRM 有强制**：全局 Semaphore tryAcquire（默认 4）+ 同会话 inFlight=1，finally 配对释放（item 14 F6 的理论泄漏窗口已由 finally 配对覆盖） | ReplService.java:170-177, 211-214 |
| 2 | 大量断点 | **CONFIRM 有上限**：单会话 500，add/set/update 三路径都查 | DebugWebSocketHandler.java:65, 325-327, 338-340, 394-396 |
| 3 | 高频 renew | 服务端限流（见 §四） | DebugSessionBinder.java:213-217 |
| 4 | 超大求值表达式 | execute 侧 256KB source + deadline；**getExprValue 侧 8KB 但无时间界**（SEC-07） | — |
| 5 | 死循环脚本 | deadline 有效（§三）；**单入口长构造无效**（SEC-06） | — |
| 6 | 连接洪水 | 未鉴权：每连接一次 `authenticate`（走 ILoginService 验签）——网关层无速率限制，认证服务 DoS 面依赖容器/网关层限流（P3 登记）；已鉴权：每连接一个 handler + keepalive 定时任务，无每用户连接数上限——单用户可开 N 连接占定时器/内存（P3） | DebugWebSocketHandler.java:109-113 |
| 7 | 入站帧洪水/协议违规 | 256KB 帧上限 + 违规计数 >10 → 4400 关闭；非法 JSON 直接 4400 | DebugWebSocketHandler.java:63, 70, 133-147, 615-619 |
| 8 | 挂起占用全局 permit | 断点挂起期间 permit 被占（绝对时钟 deadline 在 resume 后首入口才触发）；攻击者 renew 可无限续 —— 单用户 4 会话（4 连接）可占满全局并发使 REPL 服务全员 429。租约 TTL 30s + 断连 grace 15s 保证攻击者停止续期后自愈 | ReplService.java:170-177；DebugSessionBinder.java:246-263；P2 加固：permit 占用时长上限独立于脚本 deadline |
| 9 | **compile 无并发门** | **SEC-08（P2）CONFIRM**：compile() 不经过 globalPermits 也不受 enabledPredicate 复查（与 execute 门控序不对称，ReplService.java:96-144 vs 149-181）；256KB 源 × N 并发 ANTLR 解析 → 授权用户 CPU 耗尽。修复：compile 复用 Semaphore（独立或共享）+ enabled 谓词对齐 | ReplService.java:96-105 |

---

## 六、审计日志完备性

**八字段本体 CONFIRMED**（timestamp/actor/sessionId/action/sourceDigest/sourcePreview/result/durationMs，ReplService.java:293-310；consumer 缺省走专用 logger `nop.xlang.debug.audit`，脚本行为无法触达该通道——审计不可被脚本影响成立）；compile 无 sessionId、execute 必有，result 枚举 ok/error/cancelled/timeout 与 D6 §3.5 一致（测试 TestReplService.testAuditEvents）。

**SEC-09（P2）审计盲区（CONFIRMED，两处）**：

1. **全部拒绝路径无审计**：`guard.checkAccess` 401/403、属主 403、尺寸 413、沙箱 400、enabled 403、并发 429 均以 `ReplAccessException` 抛出**早于** `audit(...)` 调用点（ReplService.java:96-177 抛出 vs :142/:217 才写审计）。后果：**跨用户 sessionId 探测尝试完全不可观测**——响应侧"同码防探测"挡住了信息泄露，但安全运营侧没有任何日志可告警"有人在枚举会话"。D6 §3.5 的 result 枚举未定义 `denied` 值，属设计期遗漏。
2. **Error 终止路径无审计**：execute 只 `catch (ReplCancelledError | NopException | Exception)`（ReplService.java:199-210）——`OutOfOfMemoryError/StackOverflowError` 直接穿透（finally 释放 permit 正确），**跳过 audit()**：恰恰是最该留痕的 OOM 攻击面（SEC-06）无审计记录。

**修复方向**：a) 拒绝路径补 `result=denied` 事件（含 permission/sessionId 摘要，不含 source 全文）；b) execute 外层加 `catch (Throwable t)` 写审计后重抛（注意不得吞 Error）；c) D6 §3.5 增补 denied 枚举（owner-doc 同步）。

---

## 七、发现总表

| ID | 严重度 | 状态 | 一句话 | 首要证据 |
|----|--------|------|--------|---------|
| SEC-01 | P0 | CONFIRMED | import 无分号（ASI）绕过白名单 → 默认配置可达任意类导入 RCE | ReplService.java:64；XLangParser.g4:637-641；XplParseHelper.java:695-721 |
| SEC-02 | P0 | CONFIRMED | 同行第二个 import 不被词法校验（非空白名单下藏匿） | ReplService.java:64（^ 锚定） |
| SEC-03 | P0 | CONFIRMED | 字面量 xlib import 零校验 → 任意 VFS xlib 加载（编译期宏执行） | XLangParser.g4:104-106；XplLibHelper.java:62-70 |
| SEC-04 | P1 | CONFIRMED（部分为 D6 已声明残余） | 零 import 能力面未被白名单约束：inject()→IoC 全容器、loadDslModel/loadDeltaJson→任意 VFS 读、assign/get→scope 写读 | GlobalFunctions.java:174-187, 205-211, 278-286, 438-447 |
| SEC-05 | P1 | CONFIRMED | WS 通道无权限码校验，D6 §3.4 矩阵 WS 行未落地（PERMISSION_DENIED 码不存在） | DebugWebSocketHandler.java:90-114（仅认证）；XLangErrors grep 零命中 |
| SEC-06 | P1 | CONFIRMED | 语句内长构造绕过 deadline：RegExp 回溯（无禁用）/repeat OOM/深嵌套 SOE；4 并发即整体瘫痪 REPL | JsRegExp.java:24-46；ReplService.java:59-61 |
| SEC-07 | P1 | CONFIRMED | debugger.getExprValue/expandExprValue 无 deadline/取消/审计 | XLangDebugger.java:409-417（DefaultExpressionExecutor 直执行） |
| SEC-08 | P2 | CONFIRMED | compile 端点无并发门控、无 enabled 谓词复查（与 execute 不对称） | ReplService.java:96-144 |
| SEC-09 | P2 | CONFIRMED | 审计盲区：拒绝路径（含跨用户探测）与 Error 终止路径（OOM）均不产生审计事件 | ReplService.java:96-177 vs 142/217；199-210 |
| SEC-10 | P2 | CONFIRMED（item 14 F5 未处置项复述） | import 白名单构造时快照，运行期配置变更不生效（setImportWhitelist 仅测试面） | DebugGatewayRuntime.java:55, 140-147 |
| SEC-11 | P3 | 观察 | sessionId 可猜测（无越权影响）；建议不透明化 | DebugSessionBinder.java:117 |
| SEC-12 | P3 | 观察（D1 已接受权衡） | nopDebugToken 走 URL query 的日志泄漏面 | XLangDebugWebSocketEndpoint.java:43 |
| SEC-13 | P3 | 观察 | tokenRefresh 有效 token 预言（4403 vs 4401） | DebugWebSocketHandler.java:287-302 |
| SEC-14 | P3 | 观察 | execute 错误响应透传 e.getDescription()（内部细节泄漏给已持高权限用户，低危） | ReplService.java:203-205 |

**REFUTED（经执法代码排除的疑点，防止后续审计重复开挖）**：未鉴权可达（extractor/checker 缺失均 fail-closed）；跨用户会话劫持（属主链四处闭环 + 同码防探测 + 测试钉子）；断点跨用户观测（会话标记路由 + 默认会话 debugger 不暴露）；lease.renew 重放（连接绑定 + 服务端 0.8 限流）；RATE_LIMITED 无服务端强制（有）；断点数无上限（500 上限三路径全覆盖）；取消通道 scope 篡改（ThreadLocal 不可见 + `__repl*` 保留 + assign 写无机制语义键）；Exception 系取消被吞（Error 系 + Promise 禁用）；permit 泄漏（finally 配对）。

---

## 八、总结与修复建议

**总评**：鉴权/越权/限流/审计骨架是同类实现中的高完成度品（fail-closed 纪律、同码防探测、Error 系取消、finally 配对均经得起对抗性核对）；但**沙箱层"编译面词法校验"的形态选型被语法事实击穿**——三条 import 绕过全部源于"正则近似语法"，这是 D6 §3.3 自我声明"词法近似边界"的风险兑现，item 14 收口时登记的 F7（词法空白变体）只是冰山一角。**P0 三项应在 REPL 面重新开放前修复**（若 debug-web 已在生产/dev 环境开启）。

**修复优先级**：

1. **立即（P0，同一修复面）**：把 import 校验从正则迁到 AST——在 `compileFullExpr` 前（或 `LexicalScopeAnalysis.visitImportAsDeclaration`/`runImportExpr` 注入 REPL 专用 checker）枚举 Program 全部 ImportAsDeclaration，QualifiedName 与 Literal 两态统一走白名单，默认拒绝；同线补回归测试：无分号/同行双 import/xlib 字面量三探针（红→绿）。D6 §3.3 规则表同步"校验点 = AST 而非词法"。
2. **短期（P1）**：a) WS session.open 接入 authChecker + 登记 PERMISSION_DENIED 码；b) REPL 编译面禁用 RegExp/正则字面量（Promise 同款机制），或 watch/求值统一包 deadline 窗口；c) `inject/loadDslModel/loadDeltaJson` 在 REPL compile 面列入编译期禁用清单（或 REPL 专用 global-function 白名单机制）——这是把 D6 §3.3 残余声明升级为强制点的最小动作。
3. **短期（P2）**：compile 并发门 + enabled 对齐；拒绝/Error 路径审计（denied 枚举 + catch Throwable 记录后重抛）；白名单改运行期可刷新（读配置引用而非构造快照）。
4. **中期（裁定项）**：ReDoS 不可中断 + OOM 无预算使"时间窗"要素对单入口构造失效——建议重启 D6 §3.2 被拒的"进程隔离/worker 执行"裁定评估（原 P0 反对理由是复杂度，现在的输入是本审计的具体攻击构造）；以及 permit 占用独立时长上限（挂起 renew 无限续占用全局并发）。
5. **运营（P3）**：sessionId 不透明化、`/ws/xlang-debugger` 排除出通用 access log、tokenRefresh 失败关闭码统一。

**文档同步要求**（修复实施时）：D6 §3.3（校验机制 + 禁用清单 + 残余清单勾销/更新）、D1 §3.7（新错误码）、`docs-for-ai` 在线调试面文档、item 25 roadmap 收口记录引用本文件。
