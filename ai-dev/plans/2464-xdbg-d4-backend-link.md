# 2464 XDBG D4 调试模式与执行后端联动详设（roadmap item 5）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 5（Stage detail "5. D4 调试模式与执行后端联动详设"）；`00-overview.md` §一.8/§3.6（interpreter 承诺、D4 转派）；`03-sessionization.md` §五（D4 接缝）
> Related: plans/2460-2463（均 done）

## Purpose

产出 `ai-dev/design/xlang-web-debugger/04-execution-backend-link.md`：消除两条断点失效路径（单元级静态绑定直通 + xlib 标签绑定体直通）的机制裁定；定义 `nop.xlang.debugger.enabled` 与 `force-interpreter`/`java-backend-enabled`/`truffle-backend-enabled` 的联动矩阵；运行期切换可行性结论；性能影响评估。实现归属 item 11（nop-xlang 保护区，需 plan audit）。

## Current Baseline

- **静态绑定直通点共三处（已核验，"两条路径"实为三个执行落点）**：① 单元级路由激活路径：`EvalBackendRouter.executeAdjudicated` 分支 A → `executeBoundUnit`（L174-181）→ **`bound.getBinding().execute(rt)` 直接调用，不经 `EvalStaticBoundExecutable.execute`**；② 标签级 + fast-path：`bindTagFunction` 产生的 body 经 `ExecutableFunction` → `executor.execute(body, rt)` 走 `EvalStaticBoundExecutable.execute(executor, rt)`（L60-62，忽略 executor 直接 `binding.execute(rt)`，`allowBreakPoint()` 恒 false）；③ 运行期裁决路径：`decide()` 返回 STATIC 时 `executeAdjudicated`（L104-107）直接 `decision.getStaticBinding().execute(rt)`——裸 binding 无任何包装。消除机制必须三点全覆盖（单谓词、多落点）。
- **降级先例已存在**：`EvalStaticDegradedExecutable`（加载期"后端不可用/绑定缺失"时产生）包装原树、`execute` 经 `EvalExprProvider.getGlobalExecutor()` 重取执行、`allowBreakPoint()` 委托原树——**降级体天然可断点**（D3 路由外壳就位后经外壳→会话实例）。
- **短路先例已存在**：`EvalBackendRouter.decide()` 第一步 `isForceInterpreter()` 短路 → interpreter 且静默不记观测；加载期 `bindLoadedUnit`/`bindTagFunction` 同样被 force-interpreter 短路（返回原树不产包装）。配置 `nop.xlang.execution.force-interpreter`（`XLangConfigs` L58-60，默认 false），唯一消费者是 Router。
- **配置组（`XLangConfigs`，已核验）**：`nop.xlang.debugger.enabled`（L41，默认 false）、`nop.xlang.execution.java-backend-enabled`（L51，默认 true）、`nop.xlang.execution.truffle-backend-enabled`（L55，默认 true）、`nop.xlang.execution.deployment-form`（L63，auto）、`require-manifest`（L67，默认 false）。`CFG_XLANG_DEBUGGER_ENABLED` 定义于 nop-xlang，Router 可直接读——降级谓词无跨模块耦合。
- **后端注册**：java 后端 `XLangJavaBackendInitializer`（order +10）注册 `JavaEvalExecutionBackend` + 清单安装；truffle 后端（order +20）注册动态后端。`decide()` 决策树：静态候选→java 单跳、否则动态→truffle 单跳、均降级解释器。路由激活条件 = `EvalBackendRegistry` 非空。
- **缓存语义**：绑定产物随标签编译缓存/RCM 单元缓存复用（`bindTagFunction` body 替换随 `CompiledTag` 缓存；`bindLoadedUnit` 随 ComponentCacheEntry）——加载期抑制方案需处理缓存失效，运行期方案不需要。
- **上游裁定**：D0 §一.8（调试仅承诺 interpreter 后端；编译后端降级行为由 D4 矩阵登记）；D3 §五（D4 消除后的执行仍经 D3 路由到达会话实例）。

## Goals

- `04-execution-backend-link.md` 落地：①两条路径的单点消除机制裁定（运行期降级 vs 加载期抑制 vs 会话内强制——含拒绝记录）；②降级谓词与配置联动矩阵（`debugger.enabled` × `java-backend-enabled` × `truffle-backend-enabled` × `force-interpreter`，含 web 开关的下游关系）；③运行期切换可行性结论；④性能影响评估（生产路径零改动论证）；⑤truffle/java 编译后端在调试会话内的降级语义登记；⑥与 D3 路由/xlang-execution 后端 SPI 的接缝；⑦item 11 实现职责与回归测试锚点（静态绑定单元断点命中、xpl 标签内断点命中、非调试路径性能无损）。
- 设计目录 README 职责表状态更新。

## Non-Goals

- 实现代码（item 11）；truffle/java 后端的调试能力增强（显式 out of scope，D0 §3.6）；会话粒度的后端选择（降级是单元级/全局谓词，不做 per-session 后端路由——理由入档）；租约/协议/会话化（D1-D3 已定）。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/04-execution-backend-link.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（职责表更新）
- `ai-dev/design/xlang-web-debugger/00-overview.md`（条件项：enabled=true 行为增量需 M2/§3.3 判据限定时修订，比照 D3 先例）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 5 状态 + Stages 表第 5 行 owner plan）
- `ai-dev/logs/` 执行当日条目

### Out Of Scope

- 任何代码变更；`docs-for-ai/`；D5/D6 文档。

## Execution Plan

### Phase 1 - 执行后端联动详设文档

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/04-execution-backend-link.md`

- Item Types: `Feature`、`Decision`（消除机制、降级谓词、矩阵、性能裁定）

- [x] 写 04-execution-backend-link.md：①机制解剖（**三个直通执行落点**逐一登记）；②消除机制裁定（**运行期降级，单谓词多落点**）；③递归终止性与帧语义论证；④降级谓词裁定（含加载期不读调试开关裁定）；⑤联动矩阵（四开关 + vs 现状标注）；⑥运行期切换可行性（作用域限定）；⑦truffle/java 降级语义登记；⑧性能影响评估；⑨接缝（D3 路由 + xlang-execution SPI + item 11 保护区提示）；⑩item 11 实现职责与回归测试锚点
- [x] README 职责表：04-execution-backend-link 行状态更新并加链接
- [x] `00-overview.md`：§3.3 矩阵行 2 与 §3.8 M2 已做判据限定修订（绑定单元降级 = 有意缺陷修复，非回归破损）

Exit Criteria:

- [x] 消除机制唯一裁定且**三点全覆盖**；两条被拒方案各有缓存失效/侵入面理由
- [x] 递归终止性论证含"sourceTree 必为非包装原树"不变式与嵌套调用有界终止
- [x] 联动矩阵覆盖四开关全部组合的有效后端并逐行标注 vs 现状行为变化；`debugger.enabled=true` 行为与 D0 §一.8 一致
- [x] 降级谓词有唯一裁定并显式论证"不做活跃会话精细谓词"的理由；加载期绑定谓词有显式裁定
- [x] 运行期切换可行性有结论且作用域限定（启动期门控 vs 执行期谓词分离）
- [x] 性能评估含生产零改动论证与调试态代价声明
- [x] 文档不引用 `ai-dev/analysis/`/`ai-dev/discussions/`；与 D0/D3 及 xlang-execution 设计无矛盾（含 00-overview.md 修订后一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] owner-doc 更新 = 本文档 + README 职责表 + 00-overview.md 判据限定；`docs-for-ai/` 不动
- [x] `ai-dev/logs/` 执行当日条目已更新（随收口追加）
- [x] No new test required：纯文档计划（guide rule 25）

### Phase 2 - 对抗性评审与收口

Status: completed
Targets: `04-execution-backend-link.md`、roadmap

- Item Types: `Proof`

- [x] 独立子代理对抗性评审一轮：想象性分析（item 11 工程师照此实现是否有无歧义落点——**三落点逐一核对**）；矩阵完备性（四开关组合 + vs 现状标注）；与 D0/D3/xlang-execution 设计一致性（含 00-overview.md 修订后）；保护区提示（item 11 落 nop-xlang 需 plan audit）
- [x] 修复评审发现的问题（F1 矩阵行 4 归因、F2 行 2 矛盾、F3 终止性前提、F4 owner-doc 同步任务、F5 reason 字面量、F6 装饰器枚举、F7 演进防回归规则——closure audit 复核 7/7 PASS 判 GO，附带 N-1 措辞随收口修正）
- [x] roadmap item 5 状态翻转（激活时 `planned`、audit 后 `done`）+ Stages 表第 5 行 owner plan
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现全部修复或书面裁定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 5 `done` 且设计收口 milestone 未翻转
- [x] `ai-dev/logs/` 执行当日条目含本项记录

## Closure Gates

> 纯文档计划：构建验证豁免，doc-links --strict 为机械验证门。

- [x] XDBG-D4-01 交付物齐备且满足 Stage detail 要求（裁定+两条路径消除方案+联动矩阵+性能影响评估）
- [x] 设计评审完成，无未处置 Blocker/Major
- [x] 文档与 live repo 事实一致（三个直通落点、两包装类行为、配置键、绑定缓存语义、decide() 分支——audit 矩阵五行逐行复核零偏差）
- [x] 无静默降级项
- [x] owner docs 已同步（README 职责表 + 00-overview.md 判据限定；xlang-execution 基线同步已派 item 11）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2464-xdbg-d4-backend-link.md --strict` 退出码 0
- [x] No build verification required（纯文档计划）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 04-execution-backend-link.md 五要素齐备（三直通落点解剖 / 运行期降级单谓词多落点机制+两拒绝方案 / 递归终止性不变式论证 / 四开关联动矩阵五行含 vs 现状标注 / 运行期切换作用域限定 / 性能双态评估 / D3+xlang-execution 接缝与演进防回归规则）。对抗性评审 F1-F7 全部修复（F1 矩阵行 4 归因错误为 must-fix）；独立 closure audit 逐条验证（含 live code 逐行对照）判 GO，附带 N-1 措辞随收口修正。连带修订 00-overview §3.3/§3.8 判据限定（enabled=true 绑定单元降级 = 有意缺陷修复）。roadmap Work Item 5 翻 `done`。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（对抗性评审 agent_8eb0fec0-274e-447e-ab6f-d02f76b53d89；closure audit agent_c26b6878-101f-4b36-8542-0a39861ddbb0）
- Audit Session: closure audit 由 fresh 子代理执行，与实现会话隔离
- Evidence:
  - 修复验证：F1-F7 全部 PASS（audit 含 live code 逐行对照：Router L152-154 加载静默守卫、L243-248 config-disabled 观测、L88 instanceof 先于 decide()、AssertOpExecutable L62 中途调用点、LintDeadlineExecutor javadoc、XLangConfigs L41/51/55/58-60）
  - 新矛盾扫描：矩阵五行逐行复核零偏差；§3.2 内部互洽；00-overview 修订与正文交叉一致；audit 附带 N-1（诊断环措辞）已随收口修正为"降级观测通道静默、决策环照常记 debugger-enabled"
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2464-xdbg-d4-backend-link.md --strict` 退出码 0；grep analysis/discussions 0 命中
  - Deferred 项分类检查：Deferred 为空；无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work
