# 23 资源泄漏面 v1 落地：机制裁定 + 试点规则（roadmap item 7）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 7](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本覆盖矩阵](../../../nop-lint/docs/tool-replacement-ledger.md) · [design 06 §4.4](../../design/nop-lint/06-pmd-errorprone-alignment.md)（L3 契约）· [plan 22 矩阵](22-defect-coverage-matrix.md)

## Purpose

按 roadmap item 7 落地资源泄漏面 v1：裁定机制形态（L3 路径敏感 acquire/release 配对 vs pattern+scope 保守面）并落地试点规则（Closeable 未关闭 / finally 缺 close / 泄漏形态豁免面），对照通过后归档。

## Current Baseline

- 引擎 L3 dataflow（design 06 §4.4 / DataFlowAnalyzer javadoc）：intra-procedural def-use + constant propagation，**v1 flow-insensitive**；field-level、path-sensitive、cross-procedural 为 successor surface（类注解原文在档）。JavaDataflowResolver xscript 面 = `constantValue(filePath,line,col)` / `useCount(filePath,line,col)` / `isSelfAssigned(filePath,line,col)` 三方法。
- 覆盖矩阵资源泄漏行（plan 22 落账本）：acquire/release 配对面零规则；外围 3 条（no-finalize/empty-finally-block/double-brace-init）；优先级 P1（item 7 输入）。
- **v1 机制裁定**（本计划执行）：
  - Option A（L3 路径敏感 acquire/release 配对分析器）：需新引擎能力（路径敏感资源追踪、跨分支 release 状态合并）——DataFlowAnalyzer javadoc 明示 path-sensitive 为 successor surface；工程量大（新 resolver 方法 + L3 通道扩展），且误报控制依赖类型推断精度（JavaParser 无完整类型解析）。
  - Option B（pattern+scope 保守面）：xscript 级实现——锚定资源形态局部变量声明（type 简名 ∈ ResourceSuffix 形态集），三豁免面（try-with-resources / 所有权转移〔return/实参/字段赋值〕/ 显式 `name.close()` 文本面），对照 mjs 无（新面无 legacy 锚——对照对象为 PMD/SpotBugs 同类规则的公开语义锚）。
  - **裁定 = Option B（判据式裁定；语料误报数据为 Deferred Option A 的重估输入——roadmap 措辞「以误报控制数据定」的 v1 落地方式）**。理由：(a) 误报控制数据——Option B 三豁免面把误报面收窄到"声明资源变量 + 不在 TWR + 不转移 + 无 close 文本"四条件交集，属高信号新增准入判据（Hard constraint 2）；(b) Option A 的路径敏感分析器为 successor surface 首项（Deferred 登记，重估触发 = v1 保守面误报数据积累不支持时）；(c) Option B 零引擎扩展，纯规则面。
- **ResourceSuffix 形态集**：simple name 以 Stream / Reader / Writer / Channel / Connection / Statement / ResultSet / Session 结尾。已知 FP 面：堆内存型（ByteArrayOutputStream/StringWriter 等）被宽后缀命中（PMD CloseResource 靠 ignore 列表控制）；v1 以 warning 档 + 后缀集不做堆内存排除起步（M4 裁定），对照记录注明 FP 方向与量级，ignore 清单 = Deferred 精化。
- 计数链现值（实测 2026-09-28）：规则库 69 → 71（+2）、EXPECTED 57→59、RULE_FACET_CENSUS 73→75（现值 73 = 69 live + 4 removed）、gen 69→71（现值 69）。
- 测试/门禁基线全绿（plan 22 后）。

## Goals

- v1 机制裁定记录落账本覆盖矩阵资源泄漏行（Option B + Deferred Option A 触发）。
- 2 条试点规则落地：quality/closeable-not-closed（error）、quality/finally-missing-close（warning）+ fixtures + 豁免面 + 全量对照记录。
- 计数链同步（69→71 / 57→59 / 71→73 / gen 67→69）+ 账本分面表 + 矩阵行刷新。

## Non-Goals

- L3 路径敏感 acquire/release 配对分析器（Deferred Option A）。
- try-with-resources 强制使用规则（不属于核心缺陷面——TWR 是最佳实践非缺陷）。
- `docs-for-ai/`：No owner-doc update required。

## Scope

### In Scope

- 2 条规则 rule.yml + suites + 计数链三件套同步；账本分面表 + 矩阵资源泄漏行刷新；对照记录；roadmap item 7 状态；`ai-dev/logs/2026/09-28.md`。

### Out Of Scope

- `docs-for-ai/`；manifest 行变更（资源泄漏无 manifest 先例行，新规则无 manifest 行——同 CovariantEquals 先例）。

## Execution Plan

### Phase 1 - 机制裁定 + 试点规则落地 + 对照

Status: completed
Targets: rules 主资源、suites、三件套、账本

- Item Types: `Decision`（机制裁定）+ `Fix`（落地）+ `Proof`（对照）

- [x] 机制裁定记录：Option B 落账本资源泄漏行机制缺口格（追加 "v1 = pattern+scope 保守面（三豁免）；Option A 路径敏感配对 = Deferred（触发 = v1 误报数据不支持）"）+ 本 plan Closure 段
- [x] quality/closeable-not-closed（warning——v1 保守面不上 error 档）：锚 variable_declarator；**method-scope 闸门**：ancestor('method_declaration')==null 则 return（字段声明豁免，unused-local-variable 先例）；type 提取 = ancestor('local_variable_declaration').child('type')（variable_declarator 无 type 字段）；type 简名匹配 ResourceSuffix，xscript 三豁免判定：
  - 豁免 1（前置钉死）：TWR 由锚天然排除——tree-sitter-java 的 try-with-resources 头声明是 resource 节点（非 variable_declarator），锚集合不命中；TWR fixture 作为 valid 不命中样例（不写豁免逻辑）
  - 豁免 2：变量名出现在 return 语句或方法调用实参位置（所有权转移）
  - 豁免 3：方法体 text 按词边界 regex 匹配 name.close(（防 bin.close() 误匹配 in 的子串碰撞——方向为漏报 v1 可容忍但用 regex 收窄）
  - 三豁免全不命中 → report
- [x] quality/finally-missing-close（warning）：改锚 variable_declarator（引擎代为枚举——NodeWrapper 无多后代枚举 API）：type 简名匹配 ResourceSuffix + ancestor('try_statement') != null + 该 try 有 finally_clause 子 + finally text 不含 name.close() → report；closeQuietly 型辅助调用不含 close() 字面 → FN v1 可容忍（对照记录注明）
- [x] fixtures（每规则 valid/invalid，覆盖：TWR 不命中样例、return 豁免、显式 close 豁免、无豁免命中、字段不命中〔M1 闸门〕）
- [x] 计数链同步：69→71 / 57→59 / 71→73 / gen 67→69 + 账本分面表 +2 行（core/keep）+ RULE_FACET_CENSUS + 矩阵资源泄漏行现有规则列刷新
- [x] 对照记录：以 PMD CloseResource / SpotBugs ODR 公开语义为锚（无 legacy mjs——新面），登记进账本资源泄漏行备注 + 日志；两条规则显式 xscriptTimeoutMs: 200（STANDARD 100ms 默认外的余量）

Exit Criteria:

- [x] `./mvnw test -pl nop-lint/nop-lint-nop -am` 全绿（Java census 71、EXPECTED 59、fixtures 绿）
- [x] 六门禁 + catalog --check + doc-links strict + hollow 全绿；账本 RULE_FACET_CENSUS 75（73→75）+ gen 71（69→71）
- [x] 机制裁定记录在案（账本行 + Closure）；豁免面 fixture 覆盖 3 豁免 + 命中形态
- [x] roadmap item 7 状态翻转前置就绪；`ai-dev/logs/2026/09-28.md` 完整条目
- [x] No owner-doc update required

## Closure Gates

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] 试点规则落地且豁免面/命中面 fixture 齐全——执行期裁定：finally-missing-close 移除（xscript DSL 限制），live = 1 条规则
- [x] 全部门禁绿 + 测试全绿
- [x] roadmap item 7 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] Anti-Hollow Check：规则非空壳（fixtures 真实命中豁免/命中双面——audit 确认）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/23-resource-leak-v1.md --strict` exit 0

## Deferred But Adjudicated

### Option A：L3 路径敏感 acquire/release 配对分析器

- Classification: `watch-only residual`
- Why Not Blocking Closure: v1 Option B 保守面以三豁免面控制误报且零引擎扩展；Option A 为 DataFlowAnalyzer successor surface 首项（类注解原文在档），工程量数周且类型推断精度是前置条件。
- Successor Required: `yes`
- Successor Path: 重估触发 = v1 保守面在真实语料的误报/漏报数据不支持时，以专用 analyzer item 立项。

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: v1 资源泄漏保守面落地：quality/closeable-not-closed（warning，三豁免面控制误报）经 87/0 模块测试与全门禁验证；执行期裁定 finally-missing-close 移除（xscript DSL 限制，face 被 closeable-not-closed 覆盖——scope reduction 已记录）；机制裁定 Option B + Option A Deferred 写入覆盖矩阵与本 Closure。audit Minor 1（注释订正）已随收口处理。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_9bb4e662-c0e0-4041-8513-eec95a8c3e6b（fresh session closure audit，APPROVE 0B/0M）
- Audit Session: agent_9bb4e662-c0e0-4041-8513-eec95a8c3e6b
- Evidence:
  - 规则四机制要素逐行核验 ✓；suite fixtures 豁免/命中双面真实执行 ✓；finally-missing-close 全链清除零残留 ✓
  - 计数链 70/58/74/gen 70 与 live 全等 ✓；surefire 87/0 ✓
  - 机制裁定在矩阵与分面表落档 ✓；MetaVarEnv 活体双跑已由 plan 21 audit 覆盖
  - audit Minor 1（注释订正：豁免 2 = return only，实参转移为 FP 方向）已随收口处理；Minor 2（heap FP 口径差）为指令枚举 vs plan 基线的口径差，无需改动；Minor 3（plan 文本同步）由本回写消解
- Follow-up:

Follow-up:

- Option A（L3 路径敏感 acquire/release 配对分析器）= Deferred watch-only residual，重估触发在档。
- closeable-not-closed 实参转移 FP 方向：v1 已记录（warning 档），重估时以 ignore 清单/实参正则精化。
