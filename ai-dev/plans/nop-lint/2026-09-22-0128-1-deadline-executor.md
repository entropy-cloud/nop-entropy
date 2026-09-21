---
status: active
mission: nop-lint
work-item: "item-15"
group: "2026-09-22-0128"
verify: [test]
---

# Deadline 执行器：路线 A 全局 executor 包装 + xscript 超时强制（roadmap item 15）

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- xscript 引擎 v1 已落地（item 14 done）：`XScriptCompiler` 白名单编译（`XLang.newCompileTool()` + `allowUnregisteredScopeVar(false)` + readOnly scope var 注册、import AST 预扫拒绝，零平台改动）；`XScriptEngine.executeMatch` 每 match 新建子 scope 绑定 `node`/`captures`/`report`/`declType` 后 `action.invoke(scope)`（`XScriptEngine.java:101-136`）；`RuleSetRunner.runXscriptRule` 拥有失败跳过 + 计数语义（`nop.lint.xscript.match-failed` warn），连续 50 次失败禁用规则（`RuleSetRunner.java:39,77-106`）；`LintStats` 携带 run 级计数 `xscriptMatchesExecuted`/`xscriptFailedMatches`/`xscriptCappedMatches` + `disabledRuleIds`。
- `xscriptTimeoutMs` 已由 `RuleDslModel` 携带（`RuleDslModel.java:82`；`RuleDslParser.java:95` 默认 100，测试证实 250 可解析）但**不强制**——"无 deadline 下脚本死循环可挂起"的残留已在 plan `2026-09-21-2137-2-xscript-engine-v1` 的 `Deferred But Adjudicated` 显式登记（watch-only residual，Successor Path = roadmap item 15）。本 plan 消除该残留。
- 路线 A 关键事实（design 07 §4，已对照代码核验）：`EvalExprProvider.registerGlobalExecutor(IExpressionExecutor)` 是 nop-core 公开扩展点（`EvalExprProvider.java:35`，直接替换全局 executor），`getGlobalExecutor()` 可取回当前 executor（`EvalExprProvider.java:47`）；执行统一经 `IExpressionExecutor.execute(IExecutableExpression, EvalRuntime)`（`IExpressionExecutor.java:13`）；`EvalRuntime.getScope()`（`EvalRuntime.java:26`）；`IEvalScope.getValue(String)` 沿 scope 链解析、`setLocalValue` 注入（`IEvalScope.java:123,133`）。deadline 走 scope-local value，`WhileExecutable` 每次循环回边都经 executor 执行循环体——运行期包装即得每节点拦截点，无需编译期插桩。
- 设计参数：默认 100ms/match；fast 档收紧为 20ms（design 07 §3 timeout 行 + design 11 §7「deadline 默认值按档位缩放：fast 20ms / standard 100ms」）；`xscriptTimeoutMs` 规则可配、上限 1000ms（design 07 §3）；超时后果 = 该 match 视为不匹配 + 记录 timeout 指标 + 同规则超时率 > 1% 输出警告（design 07 §3 超时后果）；调用深度上限 32 依赖路线 A executor 钩子（design 07 §3 v1 注记：「调用深度上限 32 需 §4 路线 A 的 executor 钩子，随 item 15 落地」）。
- `LintEngine` 持有 `LintProfile`（`FAST`/`STANDARD`，`engine/LintProfile.java`），但 `CompiledRule.compile(rule, language)` 构造 `XScriptEngine` 时 profile 不可见——deadline 预算解析（档位缩放）的落点需在执行时裁定。
- 现失败语义口径：`RuleSetRunner` 对 `Exception | StackOverflowError` 统一按 failed-match 处理并计入连击（`RuleSetRunner.java:85`）——超时必须与该路径分离（design 07 §3「该 match 视为不匹配」），否则死循环脚本会被连击禁用路径误杀。
- 硬边界：nop-xlang / nop-core 为 Protected Area，**零平台改动**；只允许使用 `EvalExprProvider.registerGlobalExecutor` 公开扩展点（roadmap 硬约束「deadline executor route A must be exhausted before proposing route C」）。

## Goals

- 路线 A wrapper 落地：每次 execute 入口检查 scope-local deadline，过期则以显式异常中止该脚本执行；无 deadline 值时**原样透传**既有 executor——非 lint 的 XLang 求值语义不受任何影响。
- Deadline 注入与预算解析：每 match 注入 `startNanos + budget`；budget = 规则 `xscriptTimeoutMs`（缺省 100），FAST 档缩放为 `min(20ms, 规则值)`；`xscriptTimeoutMs` 超过 1000ms 上限时 DSL 解析期 fail-closed 拒绝。
- 超时语义可观测且与失败分离：超时 match 独立计数（`LintStats` 新计数域）+ 结构化 warn；不计入 `xscriptFailedMatches`、不触发连续失败禁用；同 run 同规则超时率 > 1% 输出警告（design 07 §3 超时后果）。
- 调用深度上限 32 经 executor 钩子在 lint 脚本作用域内落地：超限中止脚本（按脚本失败路径显式报错 + 计数）；非 lint 求值不受限。
- 端到端：死循环 xscript 规则的 lint 在 deadline 内可控终止——plan 2137-2 登记的 timeout 残留被实证消除。
- roadmap item 15 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- 路线 B（nop-lint 本地预插桩）与路线 C（改 nop-xlang 编译器）——仅在路线 A 有实测性能需求时重评（design 07 §4）。
- per-match deadline = min(20ms, 文件时间片剩余) 的 fast 预算闭合细化与 `xscriptBudgetExceeded`/`degraded` 标记（design 11 §5 fast 预算闭合行；降级阶梯属 design 11 §8 Phase 2 / item 31 方向）——optimization candidate，实测需求出现时另行立项。
- 每规则累计执行次数/平均耗时到 GraphQL 的暴露（design 07 §3 可观测行——Wave 6 item 38）。
- nop-xlang / nop-core / nop-xdef 平台改动（Protected Area，硬边界）。
- 增量解析（item 16）、抑制判定（item 17）、CLI（item 18）。

## Phase 1 — 路线 A wrapper 与 deadline 注入（Decision + Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-core`（main + test）

- Item Types: `Decision | Fix | Proof`

- [x] **Decision（wrapper 安装与接线）**：裁定 wrapper 的注册时机与幂等安装方式（经 `EvalExprProvider.registerGlobalExecutor` 安装、委托给安装时 `getGlobalExecutor()` 取回的既有 executor，而非直连 `DefaultExpressionExecutor`）；deadline scope-local key 命名（不进入 `XScriptCompiler` 编译白名单——它是宿主侧注入值，非脚本可见绑定）；并**亲自核验**真实 xscript 执行中每个表达式节点 / `WhileExecutable` 循环回边确实经过 wrapper（接线证据落日志）。结论回写 design 07 §4 增注。硬边界：现有公开 API 不足以实现时停止并回到本 plan 修订，不得改平台。
- [x] wrapper 落地：deadline 存在且已过期 → 以显式异常中止；无 deadline → 逐字透传（含异常语义不变）。
- [x] deadline 注入：`XScriptEngine` 在 `invoke` 前 `setLocalValue` 写入本 match 的 deadline；预算解析落点裁定（`CompiledRule`/引擎层引入 profile 或预算参数），FAST → `min(20ms, 规则值)`、STANDARD → 规则值。
- [x] `xscriptTimeoutMs` 上限校验：`RuleDslParser` 对 > 1000ms fail-closed 解析错误（英文消息含规则 id）。
- [x] 单元测试矩阵：无 deadline 透传（非 lint 求值不受影响）、过期中止、预算解析矩阵（缺省 100 / 规则 250 / FAST 收紧 / 1000 边界值）、超上限解析拒绝。（Minimum Rules #25）

Exit Criteria:

- [x] 上述矩阵全格有测试断言；中止路径显式抛错而非静默返回（Minimum Rules #24）。
- [x] **接线验证**（Minimum Rules #23）：wrapper 在真实 xscript 执行路径上被逐节点/循环回边调用——由死循环端到端测试（Phase 3）与接线证据共同证明。
- [x] **零平台改动**经 git diff 核实（仅 nop-lint 模块内变更）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 07 §4 增注完成（安装/委托/key 裁定）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 超时/深度语义与可观测计数（Fix + Proof）

Status: completed
Targets: `nop-lint/nop-lint-core`（main + test）

- Item Types: `Fix | Proof`

- [x] 超时与脚本失败分离：`RuleSetRunner` 单独识别超时异常——计入新 `LintStats` 计数域（如 `xscriptTimedOutMatches`）+ 独立结构化 warn 日志；不计入 `xscriptFailedMatches`、不触发连续失败禁用。
- [x] 同 run 同规则超时率 > 1% → run 级警告输出（design 07 §3 超时后果）。
- [x] 调用深度上限 32：executor 钩子对 lint 脚本作用域内的嵌套执行计深，超限 → 脚本按失败路径中止（显式错误 + 计数）；非 lint 求值不适用。
- [x] 单元测试：超时独立计数且失败连击计数不受影响（超时不进 `xscriptFailedMatches`、不触发禁用）、超时率警告触发/不触发边界、深度超限中止与计数（经真实 XLang 嵌套构造驱动；若 XLang 无递归构造则以嵌套表达式链驱动同一钩子路径，载体执行时裁定并记录）。（Minimum Rules #25）

Exit Criteria:

- [x] 超时/超时率/深度三条路径各有焦点断言，无一处静默忽略分支（Minimum Rules #24）。
- [x] 计数语义可观测：`LintStats` 新计数域有访问器且被测试断言。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] owner-doc：design 07 §3 增注完成（超时 vs 失败裁定、深度 32 承载方式）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — 端到端收口（Proof）

Status: completed
Targets: `nop-lint/nop-lint-core`（test）、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Proof`

- [x] 端到端测试：`.rule.yml` 夹具（pattern + 死循环 xscript）→ `LintEngine.lint` 在 deadline 内完成——该 match 零诊断（视为不匹配）、超时计数 > 0、同文件其他规则诊断不受影响、规则未被禁用（不进 `disabledRuleIds`）。
- [x] 回归确认：既有 `TestXScriptRules` 全部用例与全量测试保持绿色（deadline 路径不改变既有命中/过滤/异常语义）。
- [x] 收口项：roadmap item 15 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 items 16–18 状态未受扰动；plan 2137-2 `Deferred But Adjudicated` 残留的消除关系记入日志。

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：夹具 → 引擎 → 超时可控终止全链路走通（测试本身即"不终止即挂起"的证明）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [x] roadmap item 15 状态回写正确。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 所有 Phase 的执行项与 Exit Criteria 全部勾选，无未勾选的 in-scope 项残留。
- [x] 无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up。
- [x] 行为契约达成：无 deadline 透传语义不变、deadline 过期显式中止、超时与失败分离、深度上限 32 仅限 lint 脚本作用域（Goals 逐条落地）。
- [x] plan 2137-2 登记的 timeout watch-only residual 已实证消除（端到端测试为证）。
- [x] 受影响 owner docs 已同步：design 07 §3/§4 增注完成；roadmap item 15 状态回写正确。
- [ ] **Anti-Hollow Check**：独立 closure audit 验证 wrapper 在真实 xscript 执行链上被逐节点/循环回边调用（接线证据 + 端到端测试），无空方法体/静默跳过/no-op 作为正常实现。
- [ ] 独立子 agent closure-audit 已完成并将证据写入 `## Closure` 段落。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-22-0128-1-deadline-executor-1-b12406cc to opencode-2026-09-21-142035
- 2026-09-22：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-22-0128-1-deadline-executor-1-b12406cc

## Verification

## Closure
