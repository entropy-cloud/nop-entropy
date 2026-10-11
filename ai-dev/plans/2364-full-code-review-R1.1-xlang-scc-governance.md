# 2364 full-code-review R1.1 — nop-xlang 包级 SCC 三档治理（P1-1.2-001）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.1 行；finding `P1-1.2-001`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.2-xlang-structure-pipeline-audit.md`）
> Related: `ai-dev/design/xlang-package-layering-design.md`（本计划的设计决策文档，Protected Area plan-first 证据）
> ⚠️ Protected Area：nop-xlang 内部变更，plan-first + 设计文档 + 回归测试证据（本计划即 plan-first 凭证）

## Purpose

把 P1-1.2-001（nop-xlang 包级 21 节点 SCC，架构无分层）按审计裁定的三档治理收口到可验证状态：①协同环豁免登记；②expr→compile 单边割离（第一条、性价比最高，割后 compile 整包出环）；③内核「零新环 + 入边冻结」红线成文。不追求一次性拆完 SCC——剩余割边按审计建议留作后续小步（design doc §五）。

## Current Baseline

- expr→compile 包级边**只存在于一个文件**：`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/XLangExprParser.java`（:16-20 五个 import + :75 一处限定名使用），全部集中在 `buildExecutable` 方法与相邻 trace 块。
- `XLangExprParser implements IXLangExprParser`；`XplCompiler extends XLangExprParser implements IXplCompiler`；`IXplCompiler extends IXLangExprParser`。`buildExecutable` 的全部调用方经接口调用：`scope.getCompiler().buildExecutable(...)`（utils/ExprEvalHelper:125、compile/LexicalScopeAnalysis:988、xpl/utils/XplParseHelper:604、xpl/tags/MacroScriptTagCompiler:57）与 `XLangCompileTool.cp.buildExecutable(...)`（api/XLangCompileTool:215/288/309/314）。
- `XplParseHelper.runMacroExpr(Expression, IXLangExprParser cp, IXLangCompileScope)`（:600）以 `IXLangExprParser` 形参调用 `cp.buildExecutable`——接口方法不能移除或搬迁。
- compile 包当前不 import expr（0 文件）；xpl 包不 import compile（0 文件）；compile→xpl 已存在（3 个类引用跨 2 个文件：`compile/LexicalScopeAnalysis.java:88` import `xpl.utils.XplParseHelper`；`compile/BuildExecutableProcessor.java:219-220` import `xpl.output.OutputParseHelper` + `xpl.xlib.XplLibTagCompiler`）。
- 调用方归组修正：`MacroScriptTagCompiler:57` 的 `cp` 来自 `parseTag(XNode, IXplCompiler, ...)` 形参（:45）而非 `scope.getCompiler()`；`XLangCompileTool.cp` 为 `IXplCompiler` 字段。因 `IXplCompiler extends IXLangExprParser`，「全部经接口调用、公开契约不变」结论不变。
- 包级依赖结构事实（163 边、SCC(21)、8 割→13 内核）由审计复现脚本 `_tmp/dim01-r2-scc.py`、`_tmp/dim01-r2-mincut.py` 支撑，可复跑。
- 平台接缝先例：`DslModelHelper.g_excelModelLoaderFactory`（ServiceLoader + `Lazy` 缓存）、`EvalBackendRegistry`（显式注册 + fail-fast 契约）；错误码为 `XLangErrors` 中 `ErrorCode.define(...)` 手写常量；`Lazy` 为 `io.nop.commons.functional.Lazy`（无 reset 语义，`set()` 后锁定）。
- nop-xlang 测试基线当前为绿（M0 0.3 绿色构建 + Wave 1 各项 closure audit 均以绿色模块测试收口）。

## Goals

- expr 包零 `io.nop.xlang.compile` import（割离完成，compile 出环）。
- `IXLangExprParser.buildExecutable` 公开契约、方法签名、求值语义、日志消息与配置门控行为全部不变（logger 类别随承载类迁移允许变化）。
- 三档治理成文：设计文档登记豁免表 + 红线 + 验证命令；owner doc 增加包级分层治理条目。
- 未注册接缝时 fail-loud（具名 ErrorCode），无静默跳过（plan guide Rule #24）。

## Non-Goals

- 不割剩余 6 条出环边，不治理 xdef/xdsl/xmeta 非 IFACE 边（design doc §五，后续小步候选）。
- 不把红线固化为 nop-lint/CI 规则（design doc §四.4，optimization candidate）。
- 不改 `XplCompiler`/`XplExprParser`/`XplParseHelper` 等调用方代码（接口契约不变，调用方零改动）。
- 不做 exec→xpl 反向边割离（P2-1.2-003，不在 R1.1 范围）。

## Scope

### In Scope

- `ai-dev/design/xlang-package-layering-design.md`（已建，随本计划收口核对）。
- `nop-kernel/nop-xlang`：expr 包新接口 + `XLangExprParser` 委托改造；compile 包新实现类；`META-INF/services` 注册文件；`XLangErrors` 新错误码。
- 新增 JUnit 5 回归测试（接缝行为三断言：委托连通 / 覆盖注册生效 / 未注册 fail-loud）。
- owner doc：`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 增补包级分层治理小节（指向设计文档）。
- `docs-for-ai/INDEX.md` 如路由变化则同步；`ai-dev/logs/2026/10-09.md` 记录。

### Out Of Scope

- 任何 `_gen/`、`_*.xml` 生成物（本变更不触生成管线）。
- 执行语义、宏展开、类型推导行为变更。

## Execution Plan

### Phase 1 - 治理成文（设计文档 + owner doc）

Status: completed
Targets: `ai-dev/design/xlang-package-layering-design.md`、`docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`

- Item Types: `Decision`

- [x] 核对设计文档三档内容与审计报告一致（豁免表逐条对得上 1.2 报告的甄别结论）
- [x] owner doc `xlang-and-xpl-basics.md` 增补「包级分层治理」小节：expr 包禁止 import compile 的硬边界 + 指向设计文档的链接
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 设计文档存在且含：三档结论、豁免登记表（含 compile→expr 适配边首条登记）、割离裁定与已拒绝方案表、红线与验证命令、移交清单
- [x] owner doc 小节可从 `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md` 直接读到 expr 包硬边界规则
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 2/3 收口同日落笔，见 10-09 条目）

### Phase 2 - expr→compile 单边割离（代码 + 测试）

Status: completed
Targets: `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/`、`.../compile/`、`.../XLangErrors.java`、`nop-kernel/nop-xlang/src/main/resources/META-INF/services/`、`nop-kernel/nop-xlang/src/test/java/`

- Item Types: `Fix`（P1-1.2-001 架构债修复）、`Feature`（新接口接缝——伴随缺陷修复的必要新面）

- [x] expr 包新增 `IExecutableExprCompiler` 接口（单方法：Expression + optimize + scope → IExecutableExpression）
- [x] `XLangExprParser`：删除 5 个 compile import 与 :75 限定名使用；`buildExecutable` 改为委托接缝（ServiceLoader 解析 + 缓存 + 静态覆盖注册入口，显式 null 判定）；未注册时抛新具名错误码
- [x] `XLangErrors` 新增错误码（如 `nop.err.xlang.exec-compiler-not-registered`）
- [x] compile 包新增实现类，原 `buildExecutable` 管线代码原样迁入（词法分析 + trace 打印 + 类型推导门控 + 构建执行体），行为逐行等价；日志消息文本与级别保持不变（logger 类别随承载类迁移，允许变化）
- [x] `META-INF/services/io.nop.xlang.expr.IExecutableExprCompiler` 注册文件落到 `nop-kernel/nop-xlang/src/main/resources/`
- [x] 委托处解析结果显式判 null 抛具名 ErrorCode（不把 fail-loud 藏进 Lazy supplier——`Lazy.set()` 可被用于模拟未注册态）
- [x] 新增回归测试：①端到端委托连通（`XLang.newCompileTool().buildExecutable(expr)` 求值正确——参照 `TestTypeInferenceProcessor:2104` 现有形态）；②静态注册覆盖生效（注册测试桩 builder 后 `buildExecutable` 走桩）；③未注册 fail-loud（经 holder `set(null)` 模拟未注册后抛具名 ErrorCode，测试后重注册恢复）；④默认实现来源断言（`TestExecutableExprCompilerSeam` 4 用例）
- [x] 割离后机械验证：`grep -rn "io\.nop\.xlang\.compile" nop-kernel/nop-xlang/src/main/java/io/nop/xlang/expr/` 输出为空（覆盖 import 与限定名内联使用两种形态）

Exit Criteria:

- [x] expr 包零 `io.nop.xlang.compile` 引用（grep 不带 `import` 前缀的证据记入本 plan Closure）
- [x] `buildExecutable` 求值语义不变：现有 `TestSimpleExprParser`、`TestTypeInferenceProcessor` 全量通过（含端到端入口用例）
- [x] 新增回归测试三条全绿（委托连通 / 覆盖生效 / fail-loud）
- [x] **接线验证**（Rule #23）：compile 实现类在运行时确实被 `XLangExprParser.buildExecutable` 调用——由测试①（端到端求值走通）+ 测试②（覆盖注册改变行为）共同证明，非仅类型存在
- [x] **无静默跳过**（Rule #24）：未注册接缝时抛 `NopException`（具名 ErrorCode），不是返回 null 或空实现
- [x] `./mvnw test -pl nop-xlang -am` 全绿（`-pl nop-kernel/nop-xlang -am` 路径形式；859 tests / 0 failures / 0 errors）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang --severity high` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新（随 Phase 3 收口同日落笔，见 10-09 条目）

### Phase 3 - SCC 复测与收口

Status: completed
Targets: `_tmp/dim01-r2-scc.py`（复测）、roadmap R1.1 行、`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/index.md`、本 plan

- Item Types: `Proof`

- [x] 重跑 SCC 复测脚本，记录割后 SCC 成员数（预期 21→20，compile 出环）；结果写入本 plan Closure
- [x] SCC 复测脚本复制进版本化位置 `ai-dev/tools/xlang-package-scc-check.py`（`_tmp/` 被 gitignore，红线复测命令须可版本追溯），设计文档 §四.3 命令同步替换（并新增 E0 基线打印节）
- [x] roadmap R1.1 行 Status → done（含日期与 plan 号）
- [x] index.md P1 汇总表 P1-1.2-001 修复状态 → fixed（附 plan 号与日期）+ index MR 表追加 R1.1 行
- [x] 文本一致性核对：本 plan Status / Phase Status / Exit Criteria / Closure Gates / daily log 五处一致
- [x] index MR/MV/MG 表 R1.1 行登记（规则 #9）

Exit Criteria:

- [x] SCC 复测结果（割后成员清单或计数）已记录在 plan Closure 段
- [x] roadmap R1.1 行与 index.md P1 汇总表均已回填且彼此一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-1.2-001 修复落地：expr→compile 割离完成且 expr 包零 compile import
- [x] 公开契约不变：`IXLangExprParser`/`IXplCompiler`/`XLangCompileTool` 签名与行为无漂移（调用方零改动）
- [x] 三档治理成文：设计文档 + owner doc + 红线验证命令可独立执行
- [x] focused verification 完成：新增回归测试三条 + nop-xlang 全量模块测试绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（剩余割边为审计明示的后续候选，非本条 finding 的未完成部分）
- [x] owner docs 已同步（xlang-and-xpl-basics.md + INDEX 如适用）；无其他 owner-doc 漂移
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 已验证接缝调用链运行时连通（测试①②）+ 无空方法体/静默跳过（fail-loud 测试③）
- [x] `./mvnw test -pl nop-xlang -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### 剩余 6 条出环割边 + xdef/xdsl/xmeta 非 IFACE 边

- Classification: `optimization candidate`
- Why Not Blocking Closure: P1-1.2-001 的修复裁定为「三档治理，expr→compile 单边割离优先」（roadmap R1.1 行原文）；审计明确剩余割边「R1.x 逐边小步」，首边落地后 SCC 已从 21 收缩且 compile 出环，finding 的 P1 级风险（SCC 无约束增长误导开发）已由红线 + 首割控制
- Successor Required: `no`（后续割边为独立小步立项候选，登记于设计文档 §五；无既存 plan 承载）

### 红线固化进 nop-lint / CI 校验

- Classification: `optimization candidate`
- Why Not Blocking Closure: 红线已成文并配有可手工执行的验证命令（design doc §四.3）；lint 固化属工程化增强，不改变治理有效性
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2-1.2-003（exec→xpl 反向边解耦）沿用本计划的接口/回调解耦范式，归后续批次
- P2-1.2-008/009（`_vfs` 死配置删除）低风险删除项，归后续批次

## Closure

Status Note: P1-1.2-001 三档治理全部落地：expr→compile 单边割离完成（expr 包零 compile 引用，SCC 21→20 compile 出环实测，公开契约与调用方零改动）；三档治理成文（设计文档 + owner doc + 版本化 SCC 复测工具）；fail-loud 接缝回归 4 用例 + nop-xlang 全量 859 tests 全绿。独立 closure audit（agent_0dbc7edc）判定 Can Close，5 条 advisory 全部处置（javadoc 错误码名勘正/设计文档装配措辞与 §五 计数勘正/source-anchors 增补 XLANG-009/2 条确认为预存或无需动作）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_0dbc7edc-fbfd-4d47-9199-6d1d8972adb4
- Evidence:
  - **Anti-Hollow**：PASS——调用链亲读连通（XLangCompileTool:313-314 → XLangExprParser:96-101 委托 → META-INF/services → DefaultExecutableExprCompiler:28-49 管线；5 调用方全走接口、XplCompiler/XplExprParser 无 override 无旁路）；无静默跳过（委托处显式 null 判定抛 ERR_EXEC_COMPILER_NOT_REGISTERED，测试③断言精确错误码；hollow scan exit 0 零发现）。
  - **割离证明**：PASS（auditor 亲跑）——`grep -rn "io\.nop\.xlang\.compile" .../xlang/expr/` 无匹配；`python3 ai-dev/tools/xlang-package-scc-check.py` E0 打印 SCC(20)（XLangConstants|api|ast|backend|delta|exec|expr|feature|filter|functions|parse|scope|script|utils|xdef|xdsl|xmeta|xpath|xpl|xt），`compile in SCC: False`，`remove expr->compile: edge does not exist`。
  - **公开契约**：PASS——git diff 亲读：仅 XLangErrors（+3 行错误码）与 XLangExprParser（删 compile import、buildExecutable 方法体改委托签名不变、新增 2 静态方法）；5 个调用方文件零 diff；无 _gen/ 触碰；日志消息文本与配置门控保留。
  - **测试**：PASS——surefire 亲算 80 类 859/0/0/2 skipped（2 skipped 为 4.2 在案预存 @Disabled）；auditor 复跑 TestExecutableExprCompilerSeam 4/0/0、TestSimpleExprParser 27/0/0；TestTypeInferenceProcessor 117/0/0。
  - **状态一致性**：PASS——roadmap R1.1 done 行 / index P1-1.2-001 fixed 行 / index MR 表 R1.1 行 / daily log 10-09 条目四处一致。
  - **Deferred 项分类检查**：PASS——剩余割边为 1.2 审计明示「逐边小步」后续候选（optimization candidate），utils↔xdef 属 P3-1.2-001 独立条目，P2-1.2-003/008/009 为独立 P2 finding；无 in-scope live defect 被降级。
  - **Exit Criteria / Closure Gates**：全部 PASS（详见上表各行证据）；`node ai-dev/tools/check-plan-checklist.mjs` 本文件 --strict 退出码 0（关闭后复跑确认）。
  - Advisory 处置：①javadoc 错误码名勘正（已改）②设计文档「Lazy 缓存」→「volatile 双检查锁缓存」措辞对齐实现（已改）③§五「6 条割边」括号 7 包勘正（已改）④source-anchors 增补 XLANG-009 接缝锚点（已做）⑤doc-links 3 条 warning 属预存 nop-bytecode 旧 plan（0 errors，不处理）。

Follow-up:

- 剩余出环割边（script/delta/filter/backend/xt/functions）与 xdef/xdsl/xmeta 非 IFACE 边：后续小步割离候选（设计文档 §五 登记，无既存 successor plan）
- P2-1.2-003（exec→xpl 反向边解耦）、P2-1.2-008/009（_vfs 死配置删除）：独立 P2 finding 各自修复方向，属 Non-Blocking Follow-ups
- 红线固化进 nop-lint/CI：optimization candidate（红线已配手工验证命令）
