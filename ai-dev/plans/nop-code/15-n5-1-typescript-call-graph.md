# 15 N5.1 TypeScript 调用图补全

> Plan Status: completed(R1 2M/5m 修订 + R2 复审 APPROVE + 独立 closure audit 修订后通过，agent_e3da79cf)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N5.1；live 核对（2026-09-28）
> Related: plan 13（N3.1-s 增量 callee 解析——本项产出的 calleeQualifiedName 直接被全量 resolveCalls 与增量解析消费）

## Purpose

`nop-code-lang-typescript` 当前提取 call 行但从不设置 calleeQualifiedName/calleeId（persist 过滤掉 calleeId 为空的调用），**TS 调用边在任何流程（含全量索引）都不落库**——TS 项目调用图为空。本项补全 TS 调用图：分析器产出 calleeQualifiedName 候选，复用既有全量 `resolveCalls` 与增量 `resolveReanalyzedCalls` 机制完成 id 解析。

## Current Baseline（live 核对 2026-09-28）

- `TypeScriptCodeFileAnalyzer.walkNodeForCalls`（L429）提取 call_expression：仅设 methodName（member_expression 的 property 或函数名）/context（object 文本）/callerId/provenance=AST_EXTRACTION；**不设 calleeQualifiedName**。persist 处 calleeId==null 即跳过 → TS 调用边恒不落库。
- **TS 符号 qn 由文件路径确定性派生**：`buildQualifiedPrefix`（去掉 src/ 前缀与扩展名，`/`→`.`）+ 符号名。与 Java 不同，**跨文件 qn 构造可行**（无需类型求解器）。
- 分析器**不收集 import_statement**（result.getImports() 恒空）→ TS 文件的 nop_code_dependency 行也为空（N3.1 文件级传播对 TS 项目失明）——本项一并补齐 import 收集。
- 解析消费面已就绪：全量流 `ProjectAnalyzer.resolveCalls`（精确 qn→去参模糊）与增量流 `resolveReanalyzedCalls`（plan 13）按 calleeQualifiedName 查全局符号表/库内符号。
- 测试基建：模块测试以 `@EnabledIf(TreeSitterNativeAvailableCondition#isNativeLibAvailable)` 条件执行（tree-sitter native 库）；既有 `TestTypeScriptCallExtraction` 仅断言 calls 非空、不断言 qn。
- `TypeScriptImportResolver.resolveImports`（持久层）已有相对路径解析算法（`./`/`../` + .ts/.tsx/index.ts/index.tsx 候选），但依赖 projectFiles 集合——分析器层无该集合，采用"规范化路径候选直接构造 qn"策略（目标文件不存在时 qn 查不到符号 → INFERRED 不落库，无误边）。

## Goals

- **G1 同文件调用解析候选**：call_expression 的 callee 为纯标识符或 `this.method` 时，若 methodName 命中本文件符号，设 calleeQualifiedName = 该符号 qn。**两阶段结构**：calls 在 walk 中产生（walkNodeForCalls 由 handleFunctionDeclaration/handleMethodDefinition 逐符号触发），符号表必须 **post-walk 第二遍遍历 result.getCalls() 后处理 qn**——禁止在 walk 内查不完整映射（声明在后的 callee 会静默 miss）。
- **G2 导入调用解析候选**：`import { a, b } from './bar'` 的导入名建立 name→模块前缀 映射（dir 相对当前文件目录；`./`/`../` 归一化；别名导入 `a as c` 以**本地绑定名 c** 为键）。调用解析：
  - 纯标识符调用 `foo()`：qn = `<模块前缀>.foo`；
  - 成员调用 `Bar.method()` 且 obj 命中导入名：qn = `<模块前缀>.Bar.<methodName>`（**必须追加 methodName**，否则错解析成类符号产生错边）；
  - 本地符号与导入名同名时**本地符号优先**（先查本地映射）。
- **G3 import 语句收集**：import_statement **整句原文**进 result.getImports()（对齐 Python 分析器存整句的先例——TypeScriptImportResolver.extractModuleSpecifier 靠引号提取，必须整句；Java 的点分名形态不适用）——持久层据此生成 nop_code_dependency 行（TS 文件级依赖图与 N3.1 传播随之生效）。
- **G4 测试钉住**：模块单测（qn 产生语义）+ service 集成测试（indexDirectory TS 项目 → nop_code_call 行存在且 calleeId 指向真实符号——端到端证明 resolveCalls 消费 TS qn）。

## Non-Goals

- 不做类型推导（`obj.method()` 的 obj 为导入类实例等场景无法确定性解析，保持 qn 为空 → INFERRED 不落库，与全量流对 Java 的处理一致）。
- 不改 resolveCalls/增量解析机制（N3.1-s 已交付，直接复用）。
- 不改 tree-sitter 运行时与 grammar blob。
- default import / namespace import（`import X from`、`import * as NS`）不构造 qn（无确定性符号 qn 对应）。

## Scope

### In Scope

- `TypeScriptCodeFileAnalyzer`：import_statement 收集 + 同文件符号表构建 + 调用 calleeQualifiedName 候选（同文件/导入两通道）。
- 模块单测 + nop-code-service 集成测试。
- owner docs：缺口矩阵 N5.1 行、roadmap 状态（如搜索/模块文档涉及 TS 调用图描述则同步）。

### Out Of Scope

- Python 调用图（独立演进，不与本项耦合）。
- TS 类型检查级解析（LanguageService 等）。

## Execution Plan

### Phase 1 - 分析器补全 + 模块单测（G1/G2/G3/G4）

Status: completed
Targets: `TypeScriptCodeFileAnalyzer.java`、`nop-code/nop-code-lang-typescript/src/test/java/`

- Item Types: `Fix`（roadmap 登记的功能缺口）

- [x] import_statement 收集：**整句原文**进 result.getImports()（Python 先例；TypeScriptImportResolver 靠引号提取 specifier）；顺手修正 buildQualifiedPrefix 的 javadoc 与代码矛盾（代码剥 src/，javadoc 称保留）
- [x] **两阶段结构**：符号表 post-walk 构建（result.getSymbols() 的 name→qualifiedName 映射，同名取首个+确定性排序），再**第二遍遍历 result.getCalls()** 后处理 qn；本地符号优先于导入名
- [x] 调用候选：纯标识符/`this.method` 命中本地映射 → 设 qn；`foo()` 导入函数 → `<前缀>.foo`；`Bar.method()` obj 命中导入名 → `<前缀>.Bar.<methodName>`；别名导入以本地绑定名为键
- [x] 导入名→模块前缀：解析 import 语句的花括号导入名与模块 specifier（复用/对齐 TypeScriptImportResolver 的 extractModuleSpecifier 语义），qn = normalize(dir(callerFile)+specifier).模块化前缀.导入名
- [x] 模块单测：同文件 this.method / 顶层函数调用 / 导入函数 `foo()` / 导入成员 `Bar.method()`（断言含 methodName 后缀）/ 别名导入 五类 qn 断言 + imports 整句收集断言 + 声明在后（后处理可见性）+ 未解析调用 qn 保持 null
- [x] 全部既有模块测试回归绿（native 库可用时）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 模块单测全绿：五类调用 qn 候选精确断言、imports 收集、未解析调用不受影响
- [x] **无静默跳过**：无法解析的调用保持 calleeQualifiedName 为空（INFERRED 不落库），不伪造 qn
- [x] 既有模块测试回归全绿
- [x] No owner-doc update required（本 Phase 纯分析器行为补全；owner docs 统一在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 集成 e2e + docs/roadmap 同步（G4）

Status: completed
Targets: `nop-code/nop-code-service/src/test/java/`、缺口矩阵、roadmap

- Item Types: `Fix | Proof`

- [x] 集成测试：service 容器内 indexDirectory 一个 TS 小项目（两个文件、跨文件导入调用 + 同文件调用；fixture 不用 index.ts 形态——`./bar` 的 index.ts 解析 qn 前缀与符号 qn 不一致，属 INFERRED 已知语义）→ 直查 nop_code_call：调用行存在、calleeId 指向目标符号当前 id、跨文件与同文件边都有
- [x] service 集成测试不复用模块的 @EnabledIf 条件（TreeSitterNativeAvailableCondition 是 lang-typescript test 源码，跨模块不可见；service classpath natives 正常即可跑，Windows ARM64 等罕见平台会 fail——显式接受）
- [x] 缺口矩阵 N5.1 行 done；roadmap N5.1 todo→done + 汇总计数
- [x] `docs-for-ai/03-modules/nop-code.md` 或 module-groups 中 TS 调用图描述核对/同步（"TS 无调用图"表述清除）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0
- [x] `ai-dev/logs/` 收口条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 集成测试全绿：TS 调用边真实落库且 id 解析正确（端到端从入口到 DB 行）
- [x] roadmap/缺口矩阵/模块文档三处与 live 一致
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] TS 调用图补全落地：同文件/导入调用 qn 候选 + imports 收集，全量与增量解析机制直接消费
- [x] 必要 focused verification 完成（模块测试 + service 集成测试 + `./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope gap
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) TS 调用边从 indexDirectory 入口到 nop_code_call 行真实落库（集成测试断言），(b) 增量路径对 TS 文件同样生效（N3.1-s 机制消费 TS qn——代码链路核对），(c) 无静默跳过
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项）

### 执行备注（偏差与关键发现）

- **live qn 约定校正**：类符号 qn 为 `<路径前缀>.<类名>` 且方法 qn 挂其下（如 `app.UserService.UserService.validateUser`）——测试断言以符号实际 qn 为准（自洽性），不假设字面形态。
- **tree-sitter 节点名**：具名导入节点为 `import_specifier`（非 C 语法名 `named_import_specifier`）；别名以 `alias` 字段承载。
- **依赖行 resolved=false 残差**：见 Non-Blocking Follow-ups（预存 flush 可见性，Java/TS 一致）。

## Non-Blocking Follow-ups

- TS 文件 nop_code_dependency 行（import 收集后）依赖持久层 TypeScriptImportResolver——既有机制自动生效，无需本 plan 处理；其解析质量（路径别名 tsconfig paths 等）属独立增强。
- **依赖行 resolved=false 的 flush 可见性残差（执行期发现，预存行为）**：全量索引同批次内 `getProjectFilePaths` 读不到未 flush 的文件行，跨文件依赖首次索引时 resolved=false（Java 路径形态相同，非 N5.1 引入）；重索引/增量时项目文件集已完整，可正常 resolve。Classification: `watch-only residual`；Why Not Blocking Closure: 预存于持久层（Java/TS 一致）、不影响本 plan 的调用边交付（qn 解析走符号表而非 dependency 行）、全量重建后自行收敛。

## Closure

Status Note: TS 调用图补全落地——同文件/导入调用 qn 候选（含别名与成员调用公式）+ import 整句收集，经既有全量/增量解析机制落库并经集成测试端到端断言；执行期发现的依赖行 resolved=false 残差属预存持久层行为（Java/TS 一致），已登记 watch-only。独立 closure audit 首轮 REVISE 仅文档收尾项（README drift/证据回填/提交），全部修订后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_e3da79cf）
- Evidence:
  - Phase 1/2 Exit Criteria 全 PASS（模块 25/25、service 集成 -am run 通过、两扫描工具 exit 0）
  - Anti-Hollow 三项 PASS：(a) 集成测试从 indexDirectory 入口直查 nop_code_call（跨文件 run→formatName + 同文件 run→logIt 两边断言）；(b) 两阶段结构真实（analyze L83 walkNode → L87 collectImports → L91 resolveCallCandidates post-walk，声明在后可见性有测试钉住）；(c) 未解析调用保持 null（resolveCallCandidates 语义 + assertNull 测试）
  - 错边防护 PASS：`Bar.build()` → `<前缀>.Bar.build` 断言精确；本地映射仅 FUNCTION/METHOD kind
  - 实跑 PASS：lang-typescript 25/0；nop-code-service -am 237/0（不带 -am 的 NoClassDefFoundError 系 ~/.m2 旧快照，非缺陷）
  - 首轮审计 REVISE 项全部修订：README.md 实现状态行清除"暂无调用图"、本 Closure 段回填、变更提交
  - Deferred 分类检查：resolved=false 残差经 git 历史核实为预存（getProjectFilePaths 非 b66241d28d 之后新增），watch-only 成立
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- 依赖行 resolved=false 的 flush 可见性残差（watch-only，见 Non-Blocking Follow-ups）
