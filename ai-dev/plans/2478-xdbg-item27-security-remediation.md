# 2478 xdbg item 27 安全域 remediation（item 25 审计 SEC-01~10 消化）

> Plan Status: active
> Last Reviewed: 2026-10-18
> Source: roadmap item 27（ai-dev/backlog/xlang-web-debugger-roadmap.md）；审计报告 ai-dev/audits/xlang-web-debugger/2026-10-18-item25-security-deep-audit.md；设计契约 ai-dev/design/xlang-web-debugger/07-repl-security.md（D6）/01-ws-protocol.md（D1）
> Related: plans/2473（item 14 REPL 服务端）、2477（item 23 文档）；flux 侧前端 remediation plan（另立，消费本 plan 的 D1 additive 登记）
> Draft Review: R1 agent_da2607ab（0B/5M/6m 全修订）→ R2 agent_23dea4f4（approved-for-execution，0B/0M/5m 随执行落文本：watch 顶层对象字面量在 parseFullExpr 下语法错→-32602 属未声明假阳变化须 D6 落档 / watch 测试 finally restoreExecutor 防全局槽位污染 / compile 校验放置需 rethrow 卫语句防 ReplAccessException 被 catch(Exception) 吞成诊断 / setImportWhitelist(null)=清除 override、""=显式空白名单 / token 预扫描须过滤 hidden channel）

## Purpose

消化 item 25 安全深度审计的全部 P0（SEC-01/02/03，import 白名单三形态绕过 → RCE 级）与 P1（SEC-04/05/06/07），并顺带消化同审计域的低成本 P2（SEC-08/09/10）。收口后 REPL 沙箱校验从词法近似迁至"parse-only AST 遍历 + 正则字面量 token 级精确判别"的默认拒绝，WS 通道权限码落地，求值面接入 deadline 取消通道，审计盲区消除。

## Current Baseline

- 审计基线：branch feat-xlang-web-debugger @ 84fea0df6c，报告 ai-dev/audits/xlang-web-debugger/2026-10-18-item25-security-deep-audit.md（SEC-01/02/03 P0 CONFIRMED；SEC-04~07 P1 CONFIRMED；SEC-08/09/10 P2 CONFIRMED）。
- `ReplService.validateSandbox`（nop-dev-tools/nop-xlang-debug-web/.../ReplService.java:269-282）为纯正则词法校验：`IMPORT_PATTERN`（行首锚定+强制分号）可被 ASI 无分号 import（SEC-01）、同行第二个 import（SEC-02）、字面量 xlib import（SEC-03）三形态绕过；`LexicalScopeAnalysis`/`XplParseHelper.runImportExpr` 第二层无校验。
- `XLangCompileTool.parseFullExpr(loc, source)`（nop-kernel/nop-xlang/.../api/XLangCompileTool.java:147）经核实为 **parse-only**：`XLangExprParser.parseFullExpr` = ANTLR 解析 + `XLangASTBuildVisitor` 树→AST 构建，`resolveMacro` 参数被忽略、`visitImportAsDeclaration` 只 set source/local 不加载任何类/xlib；类/xlib 加载仅发生在 compileFullExpr 的 `LexicalScopeAnalysis` 阶段。校验先于 compileFullExpr 即可在任何加载/求值发生前拦截。
- `Program.getBody(): List<XLangASTNode>` 含 `ImportAsDeclaration` 节点；访问器为 `isImportClass()/getImportClassName()`（QualifiedName→FQN）与 `isImportLib()/getImportLibPath()`（Literal→xlib 路径）（ast/ImportAsDeclaration.java:36-48）。ESM 风格 `import {x} from "path"` 产出 `ImportDeclaration` 节点，经核实**不加载任何类/xlib**（LexicalScopeAnalysis 未覆写、BuildExecutableProcessor 返回 null），不构成注入面。
- **正则字面量在 AST 层不可识别**：`RegularExpressionLiteral` token 经 `XLangParseHelper.literalValue` 变为普通字符串 `Literal`（值=模式文本），与普通字符串字面量在 AST 层无区分。识别须走 **token 级预扫描**（用 `XLangLexer` 对源跑一遍 token 流，按 token 类型 `RegularExpressionLiteral` 精确判别——`IsRegexPossible` 谓词已在 `XLangLexerBase` 正确消歧除法/正则；这是精确 token 判别，非 plan 所反对的"词法近似"）。
- `XLangDebugger.eval`（nop-dev-tools/nop-xlang-debugger/.../XLangDebugger.java:404-417）以 `DefaultExpressionExecutor.INSTANCE` 直执行（其 execute 零检查），无 deadline；网关 `getExprValue/expandExprValue`（DebugWebSocketHandler.java:423-454）无时间界。**打断机制的关键事实**：eval 入口单次检查对长构造贡献有限，真正的 per-entry 打断依赖函数/lambda 调用边界经 `ExecutableFunction` → `XLang.getExecutor()` 重读全局执行器（:158）；dispatch 线程无 DebugSessionScope 标记时路由**默认会话**执行器。`AbstractDebugGatewayTest` 测试基座**未安装** SessionRoutingExecutor（全局执行器=裸 DefaultExpressionExecutor）——watch 打断测试须显式安装（照 TestXdslTaskDebugFullChain 的 registerRoutingExecutor 模式），否则永久挂死。
- `ReplCancelledError` 是 **Error 系**（extends Error）：DebugWebSocketHandler dispatch 的 catch 链（DebugProtocolException/NopException/IllegalArgumentException/Exception，:166-181）**接不住**——watch 路径必须显式 catch 并映射 -32000 错误响应，否则 Error 逃逸 onMessage。
- watch expr 经 `SimpleExprParser.parseExpr` 解析——**表达式语法无 while 语句**，死循环探针须写成 lambda IIFE/函数调用背后的循环（打断发生在调用边界的执行器入口）。
- WS `session.open`（DebugWebSocketHandler.java:221-222 → DebugSessionBinder.open）全程无 IActionAuthChecker；`ERR_XLANG_DEBUGGER_PERMISSION_DENIED` 错误码不存在（XLangErrors.java:1025-1057 无此项）；DebugWebServerConfig 已有 `tryGetBeanByType(IActionAuthChecker.class)` 先例（:64-68，装配给 ReplAccessGuard）。拒绝后"回错误响应 + 关闭连接"有既有先例（handleSessionOpen protoVersion → -32000 + 4400，DebugProtocolException 携 closeCode，:262-267）。
- **测试基座断裂风险**：`AbstractDebugGatewayTest.setUpGateway` 构造 DebugGatewayRuntime 不带 authChecker——SEC-05 fail-closed 一旦落地，全部既有 WS/REPL/task 全链测试的 openSession 均 4403。基座必须同步改造（注入 permissive checker + 增加 checker 有/无两态用例）。
- `ReplService.compile` 不经 globalPermits 也不复查 enabledPredicate（SEC-08，与 execute 门控序不对称，ReplService.java:96-105）；拒绝路径（guard/属主/尺寸/沙箱/enabled/并发）早于 audit 调用点抛出，Error 终止路径（OOM/SOE）穿透 catch(Exception) 不写审计（SEC-09）。**403 权限拒绝时 `ReplAccessGuard.checkAccess` 抛异常不携带已提取的 IUserContext**（ReplAccessGuard.java:47-48）——denied 审计要写 actor 需让异常携带 ctx 或拆分 checkAccess（本 plan 内小重构）。
- `DebugGatewayRuntime.importWhitelist` 为构造时快照（SEC-10，DebugGatewayRuntime.java:55,141-147）；`TestReplService.testSandboxValidation:189-196` 经 `runtime.setImportWhitelist("java.lang.String")` 注入并断言放行——getter 改读 CFG 后 setter 变死路径，须裁定兼容方案（见 Phase 2：显式 override 优先于 CFG 的测试面语义）。
- 既有测试面：TestReplService（含 testGateOrder/testAuditEvents/testExecuteDeadlineInterruptsInfiniteLoop/testSandboxValidation）、TestDebugGatewayProtocol（4401/4409）、TestDebugGatewayE2E、TestXdslTaskDebugFullChain（watch expr 为裸标识符 "a"/"RESULT"，parse 为表达式语句无违规，**不会**被新校验拒绝）。全部需保持绿。

## Goals

- SEC-01/02/03（P0）：import 校验迁至 AST 级——parse-only 阶段枚举 Program 全部 `ImportAsDeclaration`，`getImportClassName()` 与 `getImportLibPath()` 两态统一走白名单，默认拒绝；三形态探针（无分号/同行双 import/xlib 字面量）红→绿回归测试钉住。
- SEC-04（P1）：REPL 编译面禁用零 import 能力面函数 `inject`/`loadDslModel`/`loadDeltaJson`（编译期拒绝，与 Promise 同款机制）。
- SEC-06（P1）：REPL 编译面禁用 `RegExp` 标识符与正则字面量（token 级精确判别）；**收窄**语句内无界 CPU 面（注：`.matches()/.split()/.replaceAll()` 等字符串方法仍可构造回溯——残余归 Deferred，与审计 §八.4 进程隔离裁定项同 successor）。
- SEC-07（P1）：`getExprValue/expandExprValue` 求值接入 deadline 通道——网关包窗口（begin/finally end + ReplCancelledError 显式 catch 映射 -32000）+ 求值入口检查 + 函数调用边界经全局执行器 per-entry 检查（测试须安装 SessionRoutingExecutor 才能真实打断）；watch 表达式经**完整共享沙箱校验器**校验（与 REPL 同一禁用清单，防 `inject(...)` 等经 watch 通道绕过 SEC-04）。
- SEC-05（P1）：WS `session.open` 经 IActionAuthChecker 校验权限码 `nop/xlang/debug/DebugSession`；无权限拒绝并关闭连接（4403 + `nop.err.xlang.debugger.permission-denied`）；checker 缺失 fail-closed；测试基座同步改造守住 fail-closed；D1 additive 登记。
- SEC-08（P2）：compile 端点接入并发门（复用 globalPermits，tryAcquire 非阻塞语义与 execute 一致，无锁序交叉）与 enabledPredicate 复查。
- SEC-09（P2）：拒绝路径统一产生 `result=denied` 审计事件（403 actor 获取：ReplAccessException 携带 IUserContext 或拆分 checkAccess，plan 内小重构）；execute 增加 Throwable 兜底审计（记录后重抛，不吞 Error）；D6 §3.5 增补 denied 枚举。
- SEC-10（P2）：`DebugGatewayRuntime.importWhitelist()` 改为优先返回显式测试 override、否则每调用读 `CFG_XLANG_DEBUG_WEB_REPL_IMPORT_WHITELIST`——运行期配置变更即时生效，既有测试注入路径（setImportWhitelist）保持有效且语义登记。
- owner docs 同步：D6 §3.3（校验点=AST+token、禁用清单、残余清单勾销/更新）、D6 §3.5（denied 枚举）、D1 §3.1 约束表 + §3.7/§3.8（session.open 权限拒绝语义 + 新错误码 + 4403 复用裁定 + watch 校验/超时语义）、docs-for-ai 在线调试节（权限码 + watch 配置键）。

## Non-Goals

- 进程隔离/worker 池（D6 §3.2 被拒裁定的重评）：登记 follow-up，由审计 §八.4 中期裁定项承接，不入本 plan。
- `String.repeat`/`JSON.parse` 内存放大预算与字符串方法正则回溯的运行时拦截（SEC-06 残余面）：归 Deferred But Adjudicated（见下）。
- 前端（flux 仓）修复：FE 对新错误码的消费属前端 remediation plan（session.open 4403 关闭 → FE 既有 onclose 4403 终态分支天然兼容，无需前端先行动）。
- SEC-11~14（P3 运营观察项：sessionId 不透明化、access log 运营、tokenRefresh 关闭码统一、错误详情透传）。
- watch 求值的帧快照物化等 additive 演进（C-5 彻底解法，归并发域 plan）。
- ESM 风格 `ImportDeclaration` 的处理（经核实不加载任何资源，非注入面）。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debug-web`：ReplService（AST/token 校验接线 + 禁用清单 + 门控序 + 审计兜底 + checkAccess actor 携带）、新增共享沙箱校验器类、DebugGatewayRuntime（authChecker 装配位 + 白名单读取语义 + watch 超时消费）、DebugWebSocketHandler（session.open 权限校验 + watch deadline 窗口 + ReplCancelledError 映射 + 表达式校验）、DebugWebServerConfig（checker 装配）。
- `nop-dev-tools/nop-xlang-debugger`：XLangDebuggerConfigs（watch 超时配置键）、XLangDebugger.eval（deadline 入口检查）。
- `nop-kernel/nop-xlang`：XLangErrors 增 `ERR_XLANG_DEBUGGER_PERMISSION_DENIED`（错码族追加，与本分支既有 1025-1057 族同模式；保护区改动随本 plan 审计放行）。
- 测试：TestReplService 扩展（AST 三探针 + 禁用清单 + denied 审计 + compile 并发门 + 白名单 override 语义）、AbstractDebugGatewayTest 基座改造（permissive checker 注入 + 有/无两态）、TestDebugGatewayProtocol/E2E 扩展（session.open 权限拒绝 4403、watch deadline 取消——含 SessionRoutingExecutor 安装）。
- 文档：ai-dev/design/xlang-web-debugger/07-repl-security.md、01-ws-protocol.md；docs-for-ai/02-core-guides/debugging-and-diagnostics.md。

### Out Of Scope

- flux 仓全部文件；nop-xlang 编译管线内部改造（LexicalScopeAnalysis 注入 checker 等更重方案）；审计 SEC-11~14；并发域发现（C-1~C-10，归 plan 2479）。

## Execution Plan

### Phase 1 - AST/token 沙箱校验器 + REPL 编译面禁用清单（SEC-01/02/03/04/06）

Status: completed
Targets: `nop-dev-tools/nop-xlang-debug-web`（新增共享校验器 + ReplService 接线）

- Item Types: `Fix`（SEC-01/02/03 为已确认 live defect；SEC-04/06 为已确认深度防御缺口）

- [x] 新增共享沙箱校验器（nop-xlang-debug-web 内，如 `ReplSourceValidator`），两级机制：
  - parse-only AST 遍历：`XLang.newCompileTool().parseFullExpr` 得 `Program`，枚举 body 全部 `ImportAsDeclaration`——`isImportClass()` 取 `getImportClassName()`、`isImportLib()` 取 `getImportLibPath()`，统一对照白名单（默认拒绝）；同时按标识符拒绝 `RegExp`/`inject`/`loadDslModel`/`loadDeltaJson`（词层 `__repl*`、Promise/async 既有检查保留）
  - token 级预扫描：`XLangLexer` token 流按 `RegularExpressionLiteral` 类型精确判别正则字面量（AST 层不可识别——经 R1 核实为普通字符串 Literal）
  - 语法错误原样抛 NopException（交调用方按诊断/拒绝语义处理），不静默放行
- [x] ReplService.compile/execute 接线：validateSandbox 改为"词法快检 + 校验器（AST+token）"两段，校验先于任何 compileFullExpr 调用；**放置约束**：compile() 中校验器的 NopException 须落在既有 `catch (NopException)` → diagnostics 的 try 块内（语法错误→诊断返回），校验违规的 ReplAccessException 在其外（→400）；两异常无法从同一次调用自然分流（既有 `catch (Exception)` 会吞 ReplAccessException 成诊断）——用 rethrow 卫语句（catch 内 instanceof ReplAccessException 先 rethrow）或拆 parse/违规两段调用
- [x] token 预扫描实现注意：过滤 hidden channel（空白/注释）token；lexer 错误 token（UnexpectedCharacter）交由后续 AST 解析显式报错（不静默放行）
- [x] 回归测试（TestReplService 扩展，**红探针安全形态**：探针 payload 仅"import + return 常量"，修复前红 = 请求成功无 400，不真实执行危险调用）：
  - 无分号 import（SEC-01）、同行双 import 且首项在白名单（SEC-02）、字面量 xlib import（SEC-03）全部 400 拒绝
  - `new RegExp`/正则字面量/`inject(...)`/`loadDslModel(...)`/`loadDeltaJson(...)` 400 拒绝
  - 白名单内 FQN 合法放行（既有 testSandboxValidation 用例不回归）
- [x] **判别力探针**：对 validateSandbox 词法正则做删除变异，确认 AST/token 层独立拦截（不依赖词法层）；修复前红/修复后绿的探针证据记入本 plan

Exit Criteria:

**Phase 1 执行证据（2026-10-18）**：红态 = stash 仅还原 ReplService 接线（validator 在场但未接线，同时覆盖"修复前"与"接线移除变异"两重判别）——testAstImportBypassProbes + testBannedConstructs 双红（"Expected ReplAccessException to be thrown, but nothing was thrown"，旧词法层对 SEC-01 ASI/SEC-02 同行第二项/SEC-03 字面量 xlib/RegExp/正则字面量/inject/loadDslModel/loadDeltaJson 零拦截）；恢复接线后 debug-web 全套 49/49 绿（TestReplService 13 含 2 新用例）。

- [x] 三探针 + 禁用清单用例修复前红、修复后绿（证据入档；红探针为良性形态——无危险调用真实执行）
- [x] **接线验证**：校验器在 compile 与 execute 两条门控序中均被调用（变异探针：移除接线 → 三探针转红）
- [x] 既有 TestReplService 全绿（白名单内合法 import 用例零回归）
- [x] **无静默跳过**：校验器对无法解析的形态显式抛错，不静默放行
- [x] `ai-dev/logs/` 对应日期条目已更新；owner docs 更新归 Phase 3 统一落档

### Phase 2 - watch 求值 deadline + WS 权限码 + 门控/审计补齐（SEC-07/05/08/09/10）

Status: planned
Targets: `nop-dev-tools/nop-xlang-debug-web`（DebugWebSocketHandler/DebugGatewayRuntime/DebugWebServerConfig/ReplService/ReplAccessGuard）、`nop-dev-tools/nop-xlang-debugger`（XLangDebuggerConfigs/XLangDebugger）、`nop-kernel/nop-xlang`（XLangErrors）

- Item Types: `Fix`（SEC-05/07 已确认深度防御缺口；SEC-08/09/10 加固）

- [x] XLangDebuggerConfigs 增 watch 求值超时配置键（`nop.xlang.debug-web.watch.eval-timeout-ms`，默认 5000，命名对齐既有 `<area>.<name>` 惯例）
- [x] DebugWebSocketHandler `getExprValue/expandExprValue`：求值前对 expr 跑**完整共享校验器**（与 REPL 同一禁用清单——防 `inject` 等经 watch 通道绕过；违例 -32602 invalidParams）；求值包 deadline 窗口（begin/finally end）；**ReplCancelledError 显式 catch 映射 -32000 错误响应**（Error 系，既有 catch 链接不住——R1 核实）；窗口超时产生可观测错误响应而非挂死
- [x] XLangDebugger.eval 求值入口接入 deadline 通道检查（`checkDeadline` 无窗口时早退不误伤既有调用方；不引入断点挂起语义——watch 观察器语义不变）；打断机制 = 入口单次检查 + 函数调用边界经全局执行器 per-entry 检查双层
- [x] DebugGatewayRuntime 增加 authChecker 装配位（DebugWebServerConfig 注入 IActionAuthChecker，缺失 fail-closed 拒绝）；session.open 经 checker 校验 `nop/xlang/debug/DebugSession`，拒绝路径回 `-32000` + `nop.err.xlang.debugger.permission-denied` 并以 4403 关闭连接（DebugProtocolException 携 closeCode 既有模式）
- [x] XLangErrors 追加 `ERR_XLANG_DEBUGGER_PERMISSION_DENIED`（nop.err.xlang.debugger.permission-denied）
- [x] **测试基座改造**：AbstractDebugGatewayTest 注入 permissive checker（默认放行），守住既有 openSession 全链测试；新增 checker 有/无两态用例（无 checker → fail-closed 4403；有 checker 无权限 → permission-denied + 4403）——**禁止以放软 fail-closed 为代价保测试绿**
- [x] ReplService.compile 接入 globalPermits 并发门（tryAcquire 失败 429）+ enabledPredicate 复查（false → 403），与 execute 门控序对齐
- [x] 审计盲区消除（SEC-09）：ReplAccessException 携带 IUserContext（或拆分 checkAccess，二选一以最小 diff 为准）→ compile/execute 门控拒绝路径统一写 `result=denied` 审计事件（401 actor 为空照写）；execute 增 `catch (Throwable)` 兜底——写审计后重抛 Error（不吞）
- [x] DebugGatewayRuntime.importWhitelist() 改为：显式测试 override（setImportWhitelist）优先，否则每调用读 CFG；`setImportWhitelist(null)`=清除 override、`setImportWhitelist("")`=显式空白名单 override（语义钉死并登记）（SEC-10 运行期生效；既有 testSandboxValidation 注入路径保持有效）
- [x] 回归测试：session.open 权限拒绝全链（checker 有/无两态 + 4403）；watch deadline 打断——**死循环探针须为 lambda IIFE/函数调用后的循环**（表达式语法无 while），测试显式安装 SessionRoutingExecutor（照 TestXdslTaskDebugFullChain.registerRoutingExecutor 模式，**finally 必须 restoreExecutor 恢复全局执行器**，防 JVM 级槽位污染产生测试序依赖），断言在 watch 窗口内取消并得 -32000 错误响应；**修复前红探针用辅助线程 + 超时闩设计**（同步等待将挂死构建而非红）；watch expr 携 import/regex/`inject` 被拒；compile 并发门 429 + enabled=false 403；拒绝路径 denied 审计 + Error 路径审计断言
- [x] 全部新增公共行为有测试（Test-Mandated）

Exit Criteria:

- [x] session.open 权限拒绝全链测试绿（checker 有/无两态 + fail-closed 分支）；AbstractDebugGatewayTest 改造后全部既有 WS/REPL/task 测试零回归
- [x] watch deadline 取消测试绿（修复前红证据入档——红态为超时闩触发而非构建挂死）
- [x] SEC-08/09/10 用例绿；既有 TestReplService/TestDebugGatewayProtocol/TestDebugGatewayE2E/TestXdslTaskDebugFullChain 全绿
- [x] **端到端验证**：watch 取消链路从 WS 请求 → handler 校验/窗口 → 引擎 eval 入口 → 调用边界执行器 → ReplCancelledError → -32000 响应全链贯通（非组件级拼装）
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 绿
- [x] **无静默跳过**：checker 缺失=拒绝而非放行；deadline 打断=错误响应而非挂死或静默吞 Error
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计文档与 owner docs 同步

Status: planned
Targets: `ai-dev/design/xlang-web-debugger/07-repl-security.md`、`01-ws-protocol.md`、`docs-for-ai/02-core-guides/debugging-and-diagnostics.md`

- Item Types: `Fix`（owner-doc 与 live baseline 对齐）

- [x] D6 §3.3：校验机制改述为"parse-only AST 遍历（ImportAsDeclaration 两态统一白名单）+ 正则字面量 token 级精确判别"；禁用清单补 RegExp/正则字面量/inject/loadDslModel/loadDeltaJson + watch 表达式同一校验；watch 表达式经 parseFullExpr 校验的已知行为变化落档——顶层对象字面量（如 `{a:1}`）在 statement 文法下语法错→-32602（原 parseExpr 接受），属显式登记的假阳收窄；残余清单勾销（词法近似边界声明由 AST/token 落地取代）并保留内存放大/字符串方法回溯残余② + worker 隔离 successor 登记
- [x] D6 §3.5：result 枚举增补 `denied`
- [x] D1 §3.1 约束表 + §3.7/§3.8：session.open 权限拒绝语义（-32000 + permission-denied + 4403 关闭复用裁定，additive 登记）、watch 表达式校验与 eval-timeout 语义
- [x] docs-for-ai 在线调试节：权限码要求 + watch 超时配置键登记（对齐既有描述）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors

Exit Criteria:

- [x] 三处文档与 live code 行为一致（抽查：错误码字面量、配置键名、权限码字符串逐字一致）
- [x] doc-links --strict exit 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 审计 SEC-01/02/03（P0）三探针修复前红/修复后绿，变异探针（接线移除转红）证据入档
- [x] 审计 SEC-04/05/06/07（P1）全部落地且有判别性测试
- [x] SEC-08/09/10（P2）落地且有测试
- [x] 既有测试面全绿：`./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am`（连带 nop-xlang 依赖链与 task 全链）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（OOM/字符串回溯残余与 SEC-11~14 均按 Deferred But Adjudicated 分类登记并给出 successor/理由）
- [x] owner docs（D6/D1/docs-for-ai）同步且 doc-links --strict 0 errors
- [ ] 独立子 agent closure-audit 完成并记录证据（含 Anti-Hollow：校验器真实接入 compile/execute/watch 三条调用链，非孤立类）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang-debug-web --severity high` exit 0

## Deferred But Adjudicated

### SEC-06 残余：String.repeat/JSON.parse 内存放大、深递归 SOE、字符串方法（.matches/.split/.replaceAll）正则回溯

- Classification: `watch-only residual`
- Why Not Blocking Closure: 位于鉴权 + 权限码 + 全局并发（4 permits）+ source 256KB 门之后；SOE 单请求自限（栈展开后线程存活）；OOM 需授权用户单请求 ~2GB 分配且 D6 §3.3 残余② 已声明"内存无预算"；本 plan 的 RegExp/正则字面量禁用**收窄**（非闭合）无界 CPU 面——字符串方法回溯（`'aaa…c'.matches('(a+)+b')`，零 import 零 RegExp 字面量）仍可达，根治需引擎级步数/内存预算或进程隔离（保护区 + D6 §3.2 被拒裁定重评），已登记 successor
- Successor Required: `yes`
- Successor Path: follow-up 登记"进程隔离/worker 池重评"（审计 §八.4 中期裁定项；与并发域 plan 2479 的 follow-up 合并跟踪）

### SEC-11~14（P3 运营观察：sessionId 不透明化 / access log 运营 / tokenRefresh 关闭码统一 / 错误详情透传）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 审计自评"增益极小/低危/运营注意事项"，无越权影响（sessionId 可猜测但四处属主校验闭环）；属运营加固而非契约缺口
- Successor Required: `no`
- Successor Path: 安全运营 checklist 吸收（审计 §八.5 运营条目）

### 中期裁定项：进程隔离/worker 池重评 + permit 占用独立时长上限

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 审计明确标注为"中期裁定/建议重评"，其输入（SEC-06 具体构造）已由本 plan 部分收窄（RegExp/正则字面量面）；重评需 D6 §3.2 裁定流程，超出 remediation plan 范围
- Successor Required: `yes`
- Successor Path: ai-dev/backlog 登记 follow-up（与并发域 plan 2479 的 follow-up 合并）

## Non-Blocking Follow-ups

- watch 求值帧快照物化（挂起期间读快照，C-5 彻底解法，归并发域）
- 前端 flux 侧对 permission-denied 错误码的细粒度文案（当前 4403 终态分支已兼容，属体验优化）
