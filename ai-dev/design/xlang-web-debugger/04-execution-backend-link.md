# XLang 在线调试 调试模式与执行后端联动（D4）

**日期**：2026-10-16
**范围**：静态绑定产物在调试模式下的断点失效路径消除、配置联动矩阵、运行期切换语义；实现归属 item 11（落 `nop-kernel/nop-xlang`——**保护区，实现 plan 需 plan audit**）
**状态**：active
**上层约束**：[00-overview.md](00-overview.md) §一.8（调试仅承诺 interpreter）/§3.6（编译后端降级登记）、[03-sessionization.md](03-sessionization.md) §五（降级后的执行经 D3 路由到达会话实例）

---

## 一、设计结论

1. **消除机制 = 运行期降级（单谓词、多落点）**：降级谓词（`nop.xlang.debugger.enabled`）激活时，三个静态绑定直通执行点全部改道解释器——`decide()` 之首新增调试短路（比照 force-interpreter 先例）+ `executeBoundUnit` 与 `EvalStaticBoundExecutable.execute` 两处改经 `EvalExprProvider.getGlobalExecutor()` 执行 `sourceTree`。
2. **拒绝加载期抑制**：绑定产物随标签编译缓存 / RCM 单元缓存复用，调试会话开启晚于绑定时机，加载期抑制对已缓存包装无效，必须依赖执行期谓词兜底；加载期与执行期双机制冗余无收益。**加载期绑定不读调试开关**（保持单一职责，见 §3.4）。
3. **降级谓词 = `nop.xlang.debugger.enabled` 直读**：与调试子系统同源开关、Router 同文件读自家配置零耦合；显式拒绝"存在活跃 web 会话才降级"的精细谓词（§3.3）。
4. **联动矩阵坍缩规则**：`debugger.enabled=true` ⟹ 调试态 ⟹ 一切执行降级 interpreter（java/truffle 开关被遮蔽）；`false` ⟹ 现状矩阵逐字不变。web 开关是 debugger.enabled 的下游开关，不参与本矩阵。
5. **运行期切换**：执行期谓词可随时切换、无缓存失效；但 `debugger.enabled` 同时是调试子系统本体的**启动期**接线门控——运行期翻 true 只得"绑定单元降级 + 无调试接线"的半态，两层语义分离（§3.5）。
6. **性能红线**：`enabled=false`（生产默认）下仅新增一次布尔开关判断，绑定直通行为逐字节不变；调试态绑定单元降为解释器执行属 dev-only 必要代价。
7. **enabled=true（IDEA-only 模式）的行为增量**：绑定单元从"静默不可断点"变为"降级可断点"——这是本设计要修复的缺陷本身（通道无关），属有意行为变更，已在 `00-overview.md` §3.3/§3.8 M2 判据限定中登记（比照 D3 先例）。

## 二、背景与动机

静态绑定是执行提速产物：命中清单的编译单元/标签函数体在加载期绑定到生成类入口，执行期直调、不经解释器逐节点循环——断点检查（`DebugExpressionExecutor` 的 `checkBreakpoint`）随之失效。roadmap 将"静态绑定直通 + 标签绑定体直通"列为两条断点失效路径；实际代码中它们的**直接执行落点有三个**（§3.1），消除机制必须三点全覆盖。

## 三、核心设计

### 3.1 三个直通执行落点（机制解剖）

| # | 落点 | 到达路径 | 包装状态 | 现状行为 |
| --- | --- | --- | --- | --- |
| ① | `EvalBackendRouter.executeBoundUnit` | `XLang.execute` → `executeAdjudicated` 分支 A（单元级静态绑定命中） | `EvalStaticBoundExecutable`，但**不经其 execute**——直接 `bound.getBinding().execute(rt)` | 生成类直调，无断点 |
| ② | `EvalStaticBoundExecutable.execute` | xlib 标签体（`bindTagFunction` 产物经 `ExecutableFunction` 边界 → `executor.execute(body, rt)`）+ 注册表空时 fast-path 后的包装体 | 包装类自身（忽略 executor 参数） | `binding.execute(rt)`，`allowBreakPoint()` 恒 false |
| ③ | `executeAdjudicated` STATIC 分支 | `decide()` 运行期裁决返回 STATIC（未走加载期绑定的静态候选） | **裸 `IEvalStaticBinding`，无任何包装** | `decision.getStaticBinding().execute(rt)` |

旁注：`EvalStaticDegradedExecutable`（加载期降级包装）**不在**失效路径内——其 `execute` 经 `getGlobalExecutor()` 重取执行原树、`allowBreakPoint()` 委托原树，天然可断点；本设计让绑定体在调试态获得与降级体相同的执行形态。

### 3.2 消除机制：运行期降级（单谓词、多落点）

降级谓词 P = `nop.xlang.debugger.enabled`（Router 内静态读取，比照 `isForceInterpreter()` 的同款消费模式）。P 激活时：

- **落点③ + 全部裁决路径**：`decide()` 之首（**先于** force-interpreter 短路）新增调试短路 → interpreter，记 interpreter 决策 reason=`debugger-enabled`（固定字面量，比照 `"force-interpreter"`/`"config-disabled"` 命名契约），静默不记降级观测（两开关同开时 reason 取 debugger-enabled，force-interpreter 标记被遮蔽——插入顺序即定义）。此短路同时遮蔽 java STATIC 与 truffle DYNAMIC 裁决——未标记树的全部后端选择在调试态坍缩为解释器。
- **落点①**：`executeBoundUnit` 在 P 激活时不调 `binding.execute(rt)`，改 `EvalExprProvider.getGlobalExecutor().execute(bound.getSourceTree(), rt)`。
- **落点②**：`EvalStaticBoundExecutable.execute` 在 P 激活时不调 `binding.execute(rt)`，改 `EvalExprProvider.getGlobalExecutor().execute(sourceTree, rt)`。

降级执行的断点语义：`getGlobalExecutor()` 返回 D3 路由外壳（web 激活）或 `DebugExpressionExecutor`（web 未激活/历史形态），或包裹上述执行器的装饰器（如 `LintDeadlineExecutor`——委托链最终到达调试执行器）——原树经逐节点断点循环执行，断点按 D3 路由命中对应会话实例；无调试器接线时（理论半态，§3.5）等价普通解释器执行。

**递归终止性与帧语义论证**：

- 不变式：**每个包装的 `sourceTree` 必为非包装原树**——绑定点只包新鲜编译产物（`bindLoadedUnit` 收 parser 产物、`bindTagFunction` 收 `compileFunction` 刚产出 body），`LazyCompiledFunction.compile()` 同步一次性、setBody 后不再编译，无"包装套包装"。
- 终止性（承重墙是不变式，非 choke point 唯一性）：同一包装不会被二次包装，故降级执行的 `sourceTree` 再进入落点①②的路径不存在；`XLang.execute` 在树执行中途确有调用点（`AssertOpExecutable` 等），但其入参为裸树/新编译树，与已包装单元无关；树内嵌套标签/函数调用经边界重取外壳后可能进入**其他单元**的包装，每层各自降级一次，按调用树深度有界终止。
- 帧语义：降级执行与被包装前执行使用同一 `rt`（帧由调用方在 body 执行前构建），与 `EvalStaticDegradedExecutable` 现状执行形态完全同构——有 live 先例背书，非新语义。

### 3.3 降级谓词裁定

**裁定：P = `nop.xlang.debugger.enabled` 直读。** 理由：与调试子系统（SimpleRpcServer/executor 注入）同源开关，语义一致（"调试子系统激活 ⟹ 调试态执行语义"）；Router 读自家模块配置（`XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED`），零跨模块耦合；生产默认 false。

**显式拒绝"存在活跃 web 会话才降级"的精细谓词**：① 每次执行求值谓词需查询会话表——nop-xlang 不能反向依赖 nop-dev-tools，需引入回调注册 SPI，为 dev-only 场景引入生产耦合面；② 精细谓词漏掉默认会话：IDEA 调试在现状下同样无法于绑定单元断点（缺陷是通道无关的），`enabled` 谓词让 IDEA 同步受益；③ `enabled` 是 dev-only 开关，"调试态全解释器"的可预期性比细粒度性能更有价值。

**加载期裁定：加载期绑定不读调试开关**。`bindLoadedUnit`/`bindTagFunction` 在调试态照常工作（清单查找/指纹/绑定查找）。理由：运行期谓词已是完备机制（覆盖已缓存包装），加载期短路只对"调试态下新加载的单元"省一次绑定工作、却引入第二套开关语义；加载期降级观测（unavailable/binding-missing WARN）是配置错误信号，调试态下同样有价值。代价（调试态加载期多一次绑定工作）记入 §3.6 评估。

### 3.4 配置联动矩阵

| `debugger.enabled` | `force-interpreter` | `java-backend-enabled` | `truffle-backend-enabled` | 绑定命中单元（①②） | 未标记树（③/裁决） | vs 现状 |
| --- | --- | --- | --- | --- | --- | --- |
| **true** | 任意 | 任意（被遮蔽） | 任意（被遮蔽） | **降级解释器（可断点）** | **解释器（调试短路）** | **变化**：绑定单元从生成类直调变解释器执行（缺陷修复，IDEA/web 同受益） |
| false | true | 任意 | 任意 | 原树解释器（加载期 force 短路返回原树，稳态无绑定。注：运行期才翻开 force 时，翻转前已绑定的缓存包装仍经落点①直调——现状即如此；"无变化"按稳态判定） | 解释器（force 短路，现状） | 无变化（稳态） |
| false | false | true | 任意 | 生成类直调（现状） | 静态候选→java 单跳，其余按现状裁决链 | 无变化 |
| false | false | false | true | 原树解释器（加载期 java 开关关静默返回原树，不产包装不记观测；执行期 config-disabled 降级观测） | 动态候选→truffle 单跳，其余解释器 | 无变化 |
| false | false | false | false | 原树解释器 | 解释器 | 无变化 |

- web 开关（`nop.xlang.debug-web.enabled`）不参与本矩阵：它是 `debugger.enabled` 的下游开关（D0 §3.3 互斥红线），`enabled=true` 已蕴含降级语义。
- `debugger.enabled=true` 行为与 D0 §一.8 一致：调试仅承诺 interpreter 后端；java/truffle 编译后端在调试态的降级即本矩阵第一行。

### 3.5 运行期切换可行性

- **执行期谓词**：P 每次执行求值（读缓存配置布尔，与 force-interpreter 同款），切换即时生效、无缓存失效（运行期谓词正是为绕开绑定缓存而选）、无需重启。
- **作用域限定**：`debugger.enabled` 同时是 `XLangDebuggerInitializer.isEnabled()` 的**启动期**门控（SimpleRpcServer/executor 注入接线）。运行期把 false 翻 true 只会得到"绑定单元降级 + 无调试接线"的半态；**本档的运行期可切结论仅覆盖后端联动谓词**，调试子系统本体的启用/注册语义（是否支持运行期接线）归 item 8/12 的实现声明，不在本档承诺范围。

### 3.6 性能影响评估

- **生产路径（`enabled=false`，默认）**：三落点各新增一次布尔判断（P 求值短路为 false 后走原路径），绑定直通行为逐字节不变；生产零回归。
- **调试态（`enabled=true`）**：绑定命中单元从生成类（反射 Method 调用）降为解释器树执行——数量级级慢化，但仅发生于 dev/调试部署（该开关本来只为调试打开），且是"可断点"的必要代价；未绑定单元在调试态也走解释器（短路），与"调试仅承诺 interpreter"一致。
- **加载期**：调试态绑定工作照常（§3.3 裁定），代价为加载期一次清单查找/指纹计算——仅 dev 态、每单元一次。
- **诊断面**：调试短路静默不记降级观测（WARN/指标通道不被调试态刷屏，force-interpreter 同语义）；决策环 `recentDecisions` 照常记 reason=`debugger-enabled` 的 interpreter 决策；落点①②的降级执行走解释器路径，同样照常记 interpreter 决策——观测面完整。

### 3.7 接缝

- **D3 路由**：降级执行经 `getGlobalExecutor()` = D3 路由外壳（web 激活）→ 按会话标识到达会话实例；web 未激活 → `DebugExpressionExecutor` → 默认会话。降级不改变 D3 的任何路由语义。
- **xlang-execution SPI**：调试短路位于 `decide()` 之首、先于一切后端选择——SPI 结构（能力槽位/降级链/注册）零变更；与 force-interpreter 短路并列成对，语义类同（全局执行语义开关）。
- **item 11 实现落点**：`EvalBackendRouter`（短路 + executeBoundUnit + 谓词）、`EvalStaticBoundExecutable`（execute 降级分支）——均属 nop-xlang，**保护区 plan-first，item 11 plan 内须申请 plan audit**；回归测试锚点：三落点各自断言（绑定单元断点命中 / xpl 标签体断点命中 / 裁决路径短路，reason=`debugger-enabled`）、`enabled=false` 绑定直通回归（含矩阵行 4 的"加载期静默返回原树"现状锁定）、调试态与生产态性能对比。
- **owner-doc 同步任务（归 item 11）**：调试短路使 `decide()` 的判定输入从四项变五项——同步更新 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §三判定输入枚举与 `EvalBackendRouter` 类 javadoc，防 owner-doc 分叉。
- **演进防回归规则**：三落点完备性是对当前 live code 的判定；未来新增任何绕过 executor 的执行包装类型（如 truffle 翻译缓存形态）**必须消费调试谓词或登记于本档**，防演进静默重开断点失效路径。

## 四、拒绝了什么

- **加载期抑制静态绑定**（调试态加载期不产绑定）：对已缓存包装无效（缓存复用先于会话生命周期），必须依赖执行期谓词；双机制冗余。见 §3.2/§3.3。
- **会话内强制 interpreter**（per-session 后端路由）：需把会话概念引入后端选择，侵入 xlang-execution SPI 与 Router 决策树，为 dev-only 场景改变生产决策结构；且单元级绑定产物无会话归属，"会话内"粒度在落点①②不成立。
- **活跃会话精细谓词**：见 §3.3。
- **`EvalStaticBoundExecutable` 增加 allowBreakPoint 动态化**（让 DebugExpressionExecutor 走进包装体）：包装体 execute 仍直调 binding，逐节点断点需要的是执行 `sourceTree` 而非"进入包装"——改 execute 分支才是直击。
- **运行期全子系统热接线**：见 §3.5 作用域限定。

## 五、与已有设计的关系

- [00-overview.md](00-overview.md) §一.8/§3.6/§3.8：调试仅承诺 interpreter 的承诺由本矩阵第一行落位；M2 判据"静态绑定路径断点可命中"由本机制实现；enabled=true 行为增量已在 00-overview §3.3/§3.8 判据限定登记。
- [03-sessionization.md](03-sessionization.md) §五：D3 路由仅覆盖 interpreter 可断点路径——本机制的降级执行正是把绑定路径送回该覆盖面。
- `ai-dev/design/xlang-execution/`（三后端统一架构）：本档调试短路与其"选择机制与降级链"并列（decide() 之首），SPI 零结构变更；后端注册/降级语义仍以该设计为权威。
