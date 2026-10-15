# XLang 在线调试 在线 eval REPL 与安全边界（D6）

**日期**：2026-10-16
**范围**：REPL 服务契约（compile/execute 端点）、取消机制、沙箱规则、权限矩阵、审计事件、超时与资源限制；实现归属 item 14（服务端）
**状态**：active
**上层约束**：[00-overview.md](00-overview.md) §3.5/§3.6、[01-ws-protocol.md](01-ws-protocol.md) §3.1/§3.5/§3.7、[03-sessionization.md](03-sessionization.md)（标记契约）、[04-execution-backend-link.md](04-execution-backend-link.md)（调试态降级）、[05-frontend-architecture.md](05-frontend-architecture.md) §3.3（诊断桥 open question 兑现）

---

## 一、设计结论

1. **取消机制 = 融合执行器的 per-entry 检查**（`LintDeadlineExecutor` 的 per-entry 检查粒度 + D3 路由的调试钩点，**融合为单一执行器**）：平台 executor 传递约定是自线程化（`expr.execute(this, rt)`）——外挂装饰器与调试执行器不能串行两全（外装饰器自线程化则路由/断点旁路；每入口委托则只有根入口一次检查）。裁定：**deadline/取消检查并入会话调试执行器入口**（每入口先查后 `checkBreakpoint` 再自线程化），会话销毁分支由独立取消检查执行器承接（§3.2）。覆盖循环 back-edge 与空体死循环（`while(true){}` 在 `WhileExecutable` 内部自旋，但每轮回边都走 executor 入口）。
2. **取消/超时异常使用 Error 系**（独立于 Exception 系的 `XScriptTimeoutException`）：`TryExecutable` 无差别 `catch (Exception)`——Exception 系取消异常必被脚本吞掉并造成吞后 livelock；**同步解释器主路径已核实无 `catch (Throwable)`**（nop-xlang/exec、nop-core/lang/eval 抽查），Error 系直达 execute 服务边界。**已知反例 = async 面**：`JsPromise` 有 4 处 `catch (Throwable)` 且 `Promise` 是内建全局（import 白名单拦不住）——裁定 **一期 REPL 编译面禁用 Promise/async 构造**（REPL 编译包装的保留标识符/禁用全局校验，§3.3 规则表），禁用解除与 JsPromise Error 重抛（保护区改动）留 item 25 评估。
3. **取消通道不可见性**：deadline/取消旗标**不经脚本可见 scope**（通用编译器下脚本可按名读写/删除 scope 键——`SCOPE_VAR_REF` 回退/`setLocalValue`/`removeLocalValue`），走执行器内部 **ThreadLocal 通道**（lint 先例中 `EVAL_DEPTH` 同款形态）；辅以 REPL 编译面的保留标识符编译期校验（`__repl*` 前缀拒绝）作纵深。
4. **残余兜底诚实声明**：降级态（Error 系被拦截的场景）下"每入口重抛" = 抛-吞-再抛 livelock，**线程放弃只能放弃阻塞等待线程、无法回收自旋线程**，且同步 HTTP 契约下一期**没有请求级超时的宿主机制**（servlet async 超时留 follow-up）——降级态 livelock 残余显式登记 item 25 复核。
5. **REPL 可用性门控（双层）**：启动层——`nop.xlang.debug-web.enabled=true` 蕴含 `nop.xlang.debugger.enabled=true`（D0 §3.3 推论），`enabled=false` 时 execute 端点不可达；**运行层——execute 每请求复查 `debugger.enabled` 执行期谓词，false 即拒绝**（D4 §3.5 运行期可切：运行期翻 false 会使绑定单元恢复直调、零 executor 入口，取消/断点全失效）。
6. **沙箱 = 纵深防御而非 JVM 级隔离**：import 白名单（**含 Java 类导入**——`import <fqn>` 可达任意 classpath 类的静态成员与构造器，默认拒绝未知）+ REPL 编译面禁用/保留清单 + 权限码 + 超时/取消时间窗 + 审计追溯；**危险构造（Delta 写/VFS 写/线程/反射/静态污染）一期不做运行时拦截**——诚实声明残余风险（含入口内阻塞调用使取消延迟无上界、内存无预算的 OOM DoS 面、深递归 StackOverflowError 行为），交 item 25 评估。
7. **服务落点**：nop-dev-tools 调试 web 子模块（与 WS 网关同模块），HTTP compile/execute 两端点，路由 `POST /r/nop-xlang-debug/compile|execute`（item 14 按平台路由惯例校准）；拒绝独立 nop-biz 模块（重复建设）与塞进 WS 通道（同步诊断走 HTTP 更自然，尺寸限制复用 HTTP 栈）。
8. **权限强制点裁定**：WebResource 形态 + 服务内显式权限码校验（复用平台权限服务接口），**不引入 nop-biz 模块依赖**（D0 §3.5 最小依赖面）；item 14 plan 按平台实际鉴权挂点校准并申请评审。
9. **compile 诊断端点在本档定义**（兑现 05 §3.3 open question）：payload schema 以本档为事实源。
10. **D3 契约修订（随本档生效）**：会话销毁分支 `plainExecute` 语义修订为"仍经取消检查执行器（deadline 检查保持有效）、仅跳过会话调试钩点"（融合执行器形态下 = 会话实例已销毁时改由独立取消检查执行器承接）。已同步修订 `03-sessionization.md` §3.4（取消检查执行器形态）。

## 二、背景与动机

REPL 让浏览器端直接执行 XLang 代码——等同远程代码执行的高权限能力，安全边界是它的第一设计约束。取消机制是硬约束：脚本死循环会占死请求线程。设计时点的关键事实修正：roadmap 写作时"引擎无 deadline"的前提已过时——nop-lint-core 已落地 `LintDeadlineExecutor` 装饰器（2026-09-22，经 `registerGlobalExecutor` 安装、每 executor 入口检查、防篡改 scope 键、深度上限），证明装饰器层零保护区可达 per-entry 取消，且 D4 §3.2 已引用该先例。

## 三、核心设计

### 3.1 REPL 服务契约

**落点**：nop-dev-tools 调试 web 子模块（D0 §3.5 网关同模块），HTTP 端点 ×2，鉴权走平台 Web 鉴权链（未登录 → 401 语义，与 WS 4401 红线同源）。

**compile 端点**（编译诊断，无执行；`POST /r/nop-xlang-debug/compile`）：

- 请求：`{ sourcePath?: string, source: string }`（`sourcePath` 为诊断展示锚点，可选）
- 响应：`{ diagnostics: [{ line: int, column?: int, severity: "error"|"warning", message: string, errorCode?: string }] }`
- 诊断 payload schema 自本档定义（事实源），错误码承载与 D1 §3.7 对齐；前端诊断桥（05 §3.3）把本 schema 映射为 CodeMirror Diagnostic（映射归 05 侧工厂）。
- 无会话标记：编译期常量/宏求值路由落默认会话（IDEA 断点可命中编译期求值——与 IDEA 现状行为同源，D3 编译期窗口裁定的自然推论），跨通道干扰已知并接受。

**execute 端点**（编译 + 执行，要求有效调试会话；`POST /r/nop-xlang-debug/execute`）：

- 请求：`{ sessionId: string, source: string, timeoutMs?: int }`
- **会话属主校验**：HTTP principal 必须 == 会话 `boundUserId`（D1 §3.4 红线），违反拒绝且不区分不存在/他属（防探测，对齐 D1 `ERR_XLANG_DEBUGGER_SESSION_BOUND_ELSEWHERE` 语义）；每请求复查 `debugger.enabled` 执行期谓词（§一.5 运行层门控）。
- 响应（同步等待完成的短执行）：`{ ok: boolean, result?: string, error?: { message, errorCode? }, durationMs, cancelled?: boolean }`——`result` 为脚本返回值的字符串化（对象 JSON 序列化，受 §3.4 结果上限截断）；长执行不属一期语义（§3.4 超时上限内同步完成），`evt.completed` 承载被调试脚本（非 REPL 提交）的完成事件。
- 执行语义：source 经 D3 标记契约挂会话标记（插入点②，覆盖 compile+execute）→ 调试态解释器降级（D4）→ **融合执行器**每入口 deadline 检查 + `checkBreakpoint`（§3.2）——REPL 代码可被调试，与编辑器断点一体。

### 3.2 取消机制（融合执行器 per-entry 检查）

融合执行器入口语义（伪代码，行为规格）：

```
execute(expr, rt):                       # 会话调试执行器（融合形态，item 8/14 演进）
    if deadlineExpired(rt) or cancelFlagSet(rt):     # ThreadLocal 通道，脚本不可见（§3.3）
        throw ReplCancelledError                     # Error 系，绕过脚本 catch(Exception)
    checkBreakpoint(expr.location, rt)               # 挂起语义（mute 早退只影响断点，不影响上一行）
    return expr.execute(this, rt)                    # 自线程化（平台约定）

executeDestroyed(expr, rt):              # 会话销毁分支（D3 修订后，item 8）
    if deadlineExpired(rt) or cancelFlagSet(rt):
        throw ReplCancelledError
    return expr.execute(defaultExecutor, rt)         # 无调试钩点，有取消保护
```

- **融合而非串行**：平台 executor 约定自线程化（`expr.execute(this, rt)`）——外挂装饰器与调试执行器串行不可两全（装饰器自线程化则路由/断点旁路；每入口委托则仅根入口一次检查，`while(true)` 永不复查）。deadline 检查并入调试执行器入口是唯一同时满足 per-entry 取消 + per-entry 断点钩点的形态；`LintDeadlineExecutor` 的可复用资产是其 per-entry 检查机制与防篡改经验（scope 键教训见下），非其外挂形态。
- **取消通道不可见**：deadline/旗标走执行线程 ThreadLocal（lint 先例 `EVAL_DEPTH` 同款形态）——通用编译器下脚本可按名读写/删除 scope 键（`SCOPE_VAR_REF` 回退/`setLocalValue`/`removeLocalValue`，lint 的防篡改靠其专用编译器白名单，REPL 通用编译不具备），scope 注入不可用；辅以 REPL 编译包装的保留标识符编译期校验（`__repl*` 前缀拒绝）纵深。
- **取消异常裁定**：Error 系独立类型（不复用 Exception 系 `XScriptTimeoutException`——`TryExecutable` 无差别 `catch (Exception)`，Exception 系必被吞 + 吞后 livelock）。同步解释器主路径已核实无 `catch (Throwable)`；**已知反例 = async 面**（`JsPromise` 4 处 `catch (Throwable)`，`Promise` 为内建全局）——一期 REPL 编译面禁用 Promise/async（§3.3 规则表），解除禁用须先做 JsPromise Error 重抛（nop-xlang 保护区，item 25 评估）。新增错误码 `ERR_XLANG_DEBUGGER_REPL_CANCELLED` / `_REPL_TIMEOUT`——由 item 14 按 D1 additive 规则登记进其 §3.7 表。
- **三选一裁定记录**：①独立引擎 interrupt 检查点——**拒绝**（保护区改动 + 融合执行器已零保护区实现同粒度检查，重复建设）；②线程放弃——**拒绝为主机制**（只能放弃阻塞等待线程、无法回收自旋线程的 CPU livelock；且同步 HTTP 契约下一期无请求级超时宿主），降格为 §一.4 残余兜底；③进程隔离——**拒绝**（worker 池/序列化边界/结果回传复杂度远超一期需求，违背 self-contained 原则），留 item 25 评估。

### 3.3 沙箱规则表

| 构造/能力 | 处置 | 机制 |
| --- | --- | --- |
| import 平台标准 xlib/system 库（白名单清单，item 14 落地时枚举） | 允许 | 编译期 import 校验；白名单外 **默认拒绝**（编译诊断报错，不进入执行） |
| **import Java 类（`import <fqn>`）** | 白名单内允许，白名单外拒绝 | 类导入可达其静态字段/方法（静态污染面）与构造器（任意对象创建）——白名单枚举范围必须**同时覆盖 xlib/system 资源与 Java 类 FQN**，不只 xlib |
| Promise/async 构造 | **一期编译面禁用**（REPL 编译包装的禁用全局校验） | `JsPromise` 4 处 `catch (Throwable)` 会吞 Error 系取消异常（§3.2 已知反例）；解除禁用须先做 JsPromise Error 重抛（保护区，item 25 评估） |
| `__repl*` 保留标识符 | 编译期拒绝 | REPL 编译包装校验（防 scope 注入纵深，§3.2） |
| Delta 写 / VFS 写 / 线程创建 / 反射类危险构造 | **一期无运行时拦截**（诚实声明） | 纵深防御：白名单 import（含类导入）限制可达面 + 权限码 + 超时/取消时间窗 + 审计事后追溯；运行时强制交 item 25 评估立项 |
| 非白名单 scope 键读写 | 保留键走 ThreadLocal 通道（脚本不可见）；脚本自有 scope 键不限制 | §3.2 取消通道裁定 |
| 输出/结果超限 | 截断/拒绝 | §3.4 限制表 |

**残余风险登记（item 25 复核清单）**：①入口内阻塞调用（网络/IO/sleep 类 xlib 或导入类方法）期间 deadline 无法触发——取消延迟无上界（per-entry 机制固有窗口）；②内存无预算——时间窗内紧循环可分配远超结果上限的中间堆（OOM DoS 面）；③深递归 → `StackOverflowError`（Error 系）穿透行为未专项定义；④降级态（Error 被拦截场景）吞异常 livelock 且线程放弃不可回收自旋（§一.4）；⑤静态字段污染（白名单内类的静态可变状态跨请求残留）。

沙箱定位声明：一期**不做 JVM 级隔离**（安全沙箱自 Java 18 起废弃、进程隔离成本见 §3.2），REPL 的安全等式 = 白名单可达面 × 权限资格 × 时间窗（超时）× 事后可追溯（审计）；四要素由权限体系与 item 25 审计持续复核。

### 3.4 权限矩阵与限制

权限码命名空间 `nop/xlang/debug/*`，鉴权走平台 Web 鉴权链（BizModel/WebResource 惯例）：

| 操作 | 权限码 | 未登录 | 已登录无权限 |
| --- | --- | --- | --- |
| compile（编译诊断） | `nop/xlang/debug/ReplCompile` | 401 | 403 |
| execute（执行） | `nop/xlang/debug/ReplExecute` | 401 | 403 |
| 会话/断点/单步（WS 通道） | `nop/xlang/debug/DebugSession` | 4401（D1） | 会话创建拒绝（errorCode 名归 item 12 按 D1 additive 规则登记，如 `ERR_XLANG_DEBUGGER_PERMISSION_DENIED`） |

限制表（与 D1 §3.1 衔接：`expr ≤ 8KB` 等既有约束引用不重定义）：

| 限制 | 默认 | 上限 | 超限处置 |
| --- | --- | --- | --- |
| execute 超时 | 30000 ms | 120000 ms | Error 系取消异常（§3.2）；**绝对时钟，断点挂起时间计入**——挂起超时会话 resume 后首入口即超时（一期限制，如实声明；调试 REPL 代码应调高 timeoutMs，挂起感知 deadline 留 follow-up） |
| compile 超时 | 10000 ms | 30000 ms | 同上（编译期常量/宏求值会执行代码，超时非纯防御值） |
| source 大小 | ≤ 256 KB | 同左 | 413 语义错误响应 |
| 结果大小 | ≤ 1 MB | 同左 | 截断 + `truncated` 标记（对齐 D1 §3.1 出站约束） |
| 并发 | 同会话 execute 串行（=1）；全局并发数配置化 | 配置上限 | 限流错误（不关闭通道，对齐 D1 RATE_LIMITED 语义） |

### 3.5 审计事件

| 字段 | 说明 |
| --- | --- |
| timestamp | 事件时间 |
| actor | 操作者 userId（来自鉴权上下文） |
| sessionId | 关联调试会话（execute 必有；compile 为空） |
| action | `compile` / `execute`（取消/超时经 result 体现，无独立端点故无独立 action） |
| sourceDigest | source 的 SHA-256 摘要（可关联不泄漏） |
| sourcePreview | source 前 200 字符截断（人工排查用） |
| result | `ok` / `error` / `cancelled` / `timeout` |
| durationMs | 执行耗时 |

存储通道裁定：一期以专用 logger（`nop.xlang.debug.audit`）输出结构化审计日志（接入平台日志采集），不做独立审计表（表存储按运营需要 follow-up）。审计不可被脚本行为影响（执行侧无法写审计通道）。

### 3.6 接缝

- **D3**：execute 为标记契约插入点②（覆盖 compile+execute 全窗口）；**契约修订**——destroyed 分支 `plainExecute` 语义改为"仍经全局装饰链、仅跳过调试钩点"（已同步 `03-sessionization.md` §3.4），保证会话销毁不解除取消保护。
- **D4**：`debugger.enabled=true`（web 激活的蕴含前提）使全部执行降级解释器——融合执行器入口检查全覆盖的前提；execute **每请求复查**该执行期谓词（D4 §3.5 运行期可切，翻 false 即拒绝服务）。
- **D1**：被调试脚本（非 REPL 提交）的完成事件经 `evt.completed`；取消/超时错误码由 item 14 按 additive 规则登记 D1 §3.7；限制表衔接 D1 §3.1。
- **05**：compile 端点 = 05 §3.3 诊断桥的 `compile` 注入服务（open question 兑现）。

## 四、拒绝了什么

- **独立引擎 interrupt 检查点**：保护区改动 + 与装饰器先例重复；装饰器层已覆盖同一检查粒度（per-entry）且更宽（不 gate allowBreakPoint）。
- **进程隔离（worker 执行）**：序列化边界/worker 池/结果回传的复杂度与 self-contained 原则冲突；留 item 25 评估。
- **Exception 系取消异常**：`TryExecutable` catch(Exception) 无差别捕获 → 吞异常 + livelock；Error 系裁定见 §3.2。
- **REPL 无会话执行**：脱离会话 = 脱离路由/断点/租约语义，且取消保护依赖调试态前提；execute 强制 sessionId。
- **运行时沙箱强制（一期）**：无 JVM 级沙箱可用（平台废弃）、自建拦截面大；纵深防御四要素先行，运行时强制交 item 25 裁定。
- **异步长执行（一期）**：REPL 语义是即时反馈；异步化引入任务生命周期管理，留 follow-up。

## 五、与已有设计的关系

- [00-overview.md](00-overview.md) §3.5/§3.6：网关与 REPL 同模块（服务落点裁定的上游）；REPL 超时与资源限制属总纲"范围裁定"外的本档细化。
- [01-ws-protocol.md](01-ws-protocol.md)：限制表衔接、evt.completed 承载、错误码登记辖区（additive）。
- [03-sessionization.md](03-sessionization.md)：标记契约插入点②；本档 §一.8 的 plainExecute 修订已同步其 §3.4。
- [04-execution-backend-link.md](04-execution-backend-link.md)：调试态解释器降级 = 取消检查点与调试钩点全覆盖的前提。
- [05-frontend-architecture.md](05-frontend-architecture.md)：compile 端点兑现其诊断桥 open question。
- `ai-dev/design/xlang-execution/`：REPL 执行走 interpreter 后端语义；不触碰后端 SPI。
