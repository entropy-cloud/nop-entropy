# 2377 full-code-review R1.11 — nop-graphql fragment 上下文变量校验三位点修复（P1-2.6-002/003）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.11；findings `P1-2.6-002`（dim12-01）+ `P1-2.6-003`（dim12-05），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.6-biz-graphql-deep-audit.md`；探针 `_tmp/fcr26-probe/FragmentVarResolverProbe.java`、`FragmentSpreadDirProbe.java`
> Related: plan 2375/2376（R1.10，P1-2.6-001 已交付、P1-2.6-004 独立 successor）
> Plan Review: agent_da52f942（fresh session）两轮——第 1 轮 Not Ready 1 Blocker（F1 测试布景 @BizLoader 带参 route 因 mergeField 不合并 arguments 确定性失败）+3 建议（F2 docVars null 安全/F3 位点 2 负对照/F4 先例措辞）；第 2 轮复核 5 修订点全一致+Ready（N1 SDL 分隔符 nit 已顺手修正）

## Purpose

修复 GraphQLSelectionResolver 对 fragment 上下文中变量引用的三位点误拒（修复族 A）：operation 已声明的变量在 fragment body 字段参数、fragment body 字段指令、fragment spread 指令中引用时，解析期被 ERR_GRAPHQL_UNKNOWN_VAR 整查拒绝。修复后合法形态通过、未声明变量仍响亮拒绝，并在 owner doc 登记能力边界（P3-2.6-007 伴随条目）。

## Current Baseline

- **位点 1（:171，P1-2.6-002）**：`GraphQLSelectionResolver.resolveFragment` 对 fragment body 预解析传入 `new HashMap<>()`（恒空 map）。fragment 体内字段参数引用变量（如 `fragment FA on Tree { children(limit: $lim) { name } }`）在 `resolveValue`（:494-498）处抛 `ERR_GRAPHQL_UNKNOWN_VAR`——即使变量已在 operation 声明。fragment 预解析先于 operation 解析（`resolveSelection` 先遍历 fragment 后遍历 operation），且 `isResolved` 早退使 operation 侧调用无法补校验，错误不可恢复且信息误导（报"未定义的变量"）。探针 `FragmentVarResolverProbe` 实证。
- **位点 2（:273，P1-2.6-003）**：`resolveFragmentSelection` 对 fragment spread 自身指令校验硬编码 `new HashMap<>()`。spread 指令参数引用变量（如 `...FA @skip(if: $c)`）被误拒。该位点独立可达（fragment body 无变量也触发——operation 顶层 spread 在 operation 解析阶段走到此处）。与执行面直接矛盾：`SelectionBeanBuilder.getExcludeFields(fragmentSelection, vars)` 用真实 vars 求值平台自有 `@Exclude` 指令（base.graphql:45 `directive @Exclude(fields:[String!]!) on FRAGMENT_SPREAD`）——校验面拒执行面支持的特性，@Exclude/@skip/@include（default.graphql :44/:51 均含 FRAGMENT_SPREAD location）的变量化使用全部死胎。探针 `FragmentSpreadDirProbe` 用例 1 实证。
- **位点 3（:426，传播链）**：`resolveFieldSelection` 的 `resolveDirectives(selection.getDirectives(), FIELD, vars)` 本身传 vars，但 fragment body 内字段指令收到的 vars 正是位点 1 传入的空 map——同根因。探针 `FragmentSpreadDirProbe` 用例 5 实证（`fragment FC on Tree { name @Skip(if: $c) }` 被误拒）。
- **方法签名现状**：`resolveFragmentSelection(doc, typeDef, fragmentSelection, level)` 不携带 vars；其唯一调用方 `resolveSelections` 持有 vars 可传递。
- **外部调用面**：`resolveSelection(GraphQLDocument)` 仅 `GraphQLEngine.resolveSelections(doc, maxDepth)`（:290）与测试 `TestGraphQLDocumentParser:76` 调用；`IGraphQLEngine.resolveSelection(objName, selectionSet, vars)` 三参重载无生产调用方。改动集中在本类内部，无跨模块 API 面。
- **执行契约**：请求省略 variables 键等同空变量集，未提供变量取值为 null（`GraphQLEngine:422` 注释 + `TestGraphQLVariables` 在案）。执行面（SelectionBeanBuilder）用真实请求 vars 求值 fragment spread 指令与字段参数。
- **inline fragment**：parser 显式拒绝（`GraphQLDocumentParser:728-742`，错误码 ERR_GRAPHQL_PARSE_UNSUPPORTED_INLINE_FRAGMENT），fragment 内变量无 inline 可达面，本 plan 不涉及。
- **测试布景现状**：`TestGraphQLFragmentDepth`（4 用例）+ `MockGraphQLSchemaLoader` + `test.graphql`（Tree.children 无参数）。布景中不存在「带参数的字段」。**布景约束（plan 审查 F1 确认的代码事实）**：`MockGraphQLSchemaLoader` 先解析 test.graphql 后 `bizModels.customize(def)`→`mergeLoaderTo`→`GraphQLObjectDefinition.mergeField(field, false)`——旧字段已存在时**任何分支都不合并 arguments**（全仓 `setArguments` 仅 parser/ObjMetaToGraphQLDefinition 调用），故 extraBeans 传入带参 `@BizLoader("children")` 的 `limit` 参数定义会被静默丢弃，`children(limit: $lim)` 将抛 `ERR_GRAPHQL_UNDEFINED_FIELD_ARG`（错误码与判别预期不符）。**主路线必须是独立 fixture graphql 直接以 SDL 定义带参字段**；`MockGraphQLSchemaLoader` 硬编码 test.graphql 路径（:49），需增加 fixture 路径构造变体。`TestGraphQLFragmentDepth.TreeBizModel` 是 extraBeans+`@BizQuery` 形态先例（getTree(id) 根操作仍需 biz bean 提供，`resolveOperation` 经 `getOperationDefinition` 查询）。
- **owner doc 现状**：`docs-for-ai/02-core-guides/api-and-graphql.md` 全文无 fragment/内联片段能力边界登记（grep 0 命中）——P3-2.6-007 判定的文档缺口成立。

## Goals

- 三位点一次闭合：fragment body 字段参数、fragment body 字段指令、fragment spread 指令中引用「文档中任一 operation 已声明」的变量，解析期通过。
- 未声明变量（`$undefinedVar`）仍然 `ERR_GRAPHQL_UNKNOWN_VAR` 响亮拒绝（负对照保持）。
- 既有防护零回归：fragment 循环引用拒绝、引用链深度限制、maxDepth 校验、既有 33+ 测试全绿。
- owner doc 登记 fragment 变量支持语义与 inline fragment 限制（P3-2.6-007 伴随）。

## Non-Goals

- 不实现 inline fragment 支持（parser 显式拒绝是既定工程取舍，dim12-04 已登记，解除需独立立项）。
- 不实现逐 operation 严格校验（fragment 被 op A 引用而变量仅 op B 声明的跨 operation 共享面）。裁定依据：执行契约「未提供的变量取 null」（GraphQLEngine:422 + TestGraphQLVariables 在案）下，跨 operation 引用未声明变量的运行时行为与「声明但未提供」完全一致（取 null），不产生静默错值通道；文档级并集校验消除的是本 P1 的误拒面。该宽松度裁定登记 owner doc。
- 不动 P1-2.6-004（DataLoader 调度，plan 2376）、修复族 B（P2-2.6-007/010）、修复族 C（R1.12）。

## Scope

### In Scope

- `nop-service-framework/nop-graphql/nop-graphql-core` 的 `GraphQLSelectionResolver.java`：resolveFragment/resolveFragmentSelection 变量上下文修复。
- 新增回归测试类 `TestGraphQLFragmentVariables`（含判别证据）。
- `docs-for-ai/02-core-guides/api-and-graphql.md` 能力边界登记。

### Out Of Scope

- `GraphQLDocumentParser`（inline fragment 拒绝逻辑）。
- 执行面 `SelectionBeanBuilder`/`RpcSelectionSetBuilder`（已正确用真实 vars）。
- 其他模块。

## Execution Plan

### Phase 1 - resolver 三位点修复 + 回归测试

Status: completed
Targets: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLSelectionResolver.java`、`.../src/test/java/io/nop/graphql/core/engine/TestGraphQLFragmentVariables.java`

- Item Types: `Fix`、`Proof`

- [x] 修复位点 1：`resolveSelection(doc)` 预解析阶段计算 docVars（文档内全部 operation 变量定义并集），`resolveFragment` 预解析 fragment body 时以 docVars 替换空 map。**docVars 构建须 null 安全**：从 `op.getVariableDefinitions()`（parser 解析期即填充）构建，不依赖 `doc.init()`（直接实例化 resolver 的路径如 `TestGraphQLDocumentParser:76` 不触发 init，`getVars()` 可能为 null——plan 审查 F2）
- [x] 修复位点 2：`resolveFragmentSelection` 增加 vars 参数（由 `resolveSelections` 传入），spread 指令校验以传入 vars 替换空 map；体内嵌套 spread 预解析阶段继承 docVars、operation 解析阶段继承 op vars
- [x] 位点 3 随位点 1 的 vars 传播自然闭合（预期零独立代码改动），以回归用例覆盖证明
- [x] 测试布景（按审查 F1 修订的主路线）：
  - 新建独立 fixture `fragment-var-test.graphql`（test resources，不动共享 `test.graphql`；字段换行分隔，parser 不接受 `;` 分隔——复核 N1）：
    ```graphql
    type Tree {
        id: ID
        name: String
        children(limit: Int): [Tree]
    }

    type Query {
    }
    ```
    SDL 直接定义带参字段，绕开 mergeField 不合并 arguments 的约束
  - `MockGraphQLSchemaLoader` 增加 fixture 路径构造变体（既有构造器委托，零破坏）
  - 测试类自带 `TreeBizModel`（`@BizQuery("get") @GraphQLReturn(bizObjName="Tree") getTree(@Name("id") String id)`，照 `TestGraphQLFragmentDepth.TreeBizModel` 先例）
- [x] 新增 `TestGraphQLFragmentVariables`（7 用例）：
  - fragment body 字段参数变量（`children(limit: $lim)`）通过——位点 1
  - fragment body 字段指令变量（`name @skip(if: $c)`，default.graphql @skip 含 FIELD location）通过——位点 3
  - fragment spread 指令变量（`...FA @skip(if: $c)`）通过——位点 2
  - 平台自有 `@Exclude` 指令 spread 常量参数通过（证明平台指令面活）
  - 嵌套 fragment 链（F1 spread F2，变量在 F2 body）通过——P2-2.6-008 伴随边界
  - 负对照 a：fragment body 引用未声明变量（`children(limit: $undefinedVar)`）抛 `ERR_GRAPHQL_UNKNOWN_VAR`
  - 负对照 b：fragment spread 指令引用未声明变量（`...FA @skip(if: $undefinedVar)`）抛 `ERR_GRAPHQL_UNKNOWN_VAR`——锁定位点 2 修复后的拒绝路径（审查 F3）
- [x] 判别：stash 修复后新用例中位点 1/2/3 正向用例 FAIL（ERR_GRAPHQL_UNKNOWN_VAR）、负对照仍绿；恢复后全绿（实测：stash 后 4 正向用例 FAIL 错误码逐字 `nop.err.graphql.unknown-var`〔params varName=lim/c〕、两负对照+@Exclude 常量绿；恢复后 7/7 绿）

Exit Criteria:

- [x] 位点 1/2 代码修复在位：`GraphQLSelectionResolver` 中不再存在对 fragment 上下文硬编码空 map 的调用（grep `new HashMap<>()` 仅剩非 fragment 语境使用）
- [x] `TestGraphQLFragmentVariables` 7 用例全绿（三正向+平台指令+嵌套链+两负对照）
- [x] 判别证据记录于 daily log：stash 状态下正向用例 FAIL 且错误码为 `ERR_GRAPHQL_UNKNOWN_VAR`，恢复后全绿
- [x] `./mvnw test -pl nop-service-framework/nop-graphql/nop-graphql-core -am` 全绿（既有 TestGraphQLFragmentDepth/TestGraphQLDocumentParser/TestGraphQLVariables 零回归）
- [x] **无静默跳过**：未声明变量路径仍抛 `ERR_GRAPHQL_UNKNOWN_VAR`（负对照用例锁定），修复不引入「跳过校验」形态
- [x] No owner-doc update required（Phase 2 承载文档更新）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 能力边界登记

Status: completed
Targets: `docs-for-ai/02-core-guides/api-and-graphql.md`

- Item Types: `Fix`（文档缺口 P3-2.6-007 伴随）

- [x] api-and-graphql.md 新增 GraphQL 查询能力边界小节：①named fragment 体内/字段指令/spread 指令支持引用 operation 已声明变量（本次修复后语义；并集校验的作用语境为 fragment 预解析——生产单 operation 文档下与逐 operation 校验重合，plan 审查 F5 注记）；②inline fragment（`... on Type`）当前不支持（parser 显式拒绝）；③平台自有 @Exclude 指令用于 fragment spread 字段排除
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] api-and-graphql.md 能力边界条目在位且与 live 行为一致（三条逐一与实现对照）
- [x] doc-links 检查退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新（可并入 Phase 1 条目）

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-2.6-002 三位点 + P1-2.6-003）
- [x] 行为/契约结果已达成：合法 fragment 变量形态解析通过、未声明变量仍拒绝
- [x] 必要 focused verification 已完成（7 用例+判别）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（跨 operation 并集宽松度已作为显式裁定登记 owner doc，非 silent 降级；closure audit 第六节判定诚实）
- [x] 受影响的 owner docs 已同步（api-and-graphql.md 能力边界）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）修复经 `GraphQLEngine.parseOperation` 入口端到端生效（非仅直接实例化 resolver），（b）校验拒绝路径未被弱化（负对照在位）
- [x] `./mvnw compile`（模块级——test 阶段含编译，BUILD SUCCESS）
- [x] `./mvnw test -pl nop-service-framework/nop-graphql/nop-graphql-core -am` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-graphql-core --severity high` 退出码 0

## Deferred But Adjudicated

### 跨 operation fragment 变量共享的逐 operation 严格校验

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 执行契约「未提供变量取 null」使跨 operation 未声明变量的运行时行为与「声明未提供」一致（取 null），并集校验不产生静默错值通道；严格逐 operation 校验需 per-op 二次遍历已解析 fragment 树（含环防护），属增强非缺陷
- Successor Required: `no`

## Non-Blocking Follow-ups

-（无）

## Closure

Status Note: P1-2.6-002/003 三位点一次闭合（docVars 并集预解析+spread 指令 vars 传递，位点 3 随传播闭合）；判别成立（stash 后 4 正向用例 FAIL 错误码逐字 `nop.err.graphql.unknown-var` 吻合审计探针，两负对照恒绿）；graphql-core 141 全绿（134 基线+7 新增）；owner doc 能力边界登记（P3-2.6-007 伴随）。roadmap R1.11 翻 done、index P1-2.6-002/003 → fixed、MR 表 R1.11 行已落盘。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: agent_de643721（fresh session，独立只读审计）
- Evidence:
  - Phase 1 七条 Exit Criteria 全 PASS：①grep `new HashMap<>()` 仅剩 :132 docVars 构建（非 fragment 语境）；②7 用例独立实跑全绿；③daily log 判别条目在案且自洽（审计从 git diff 还原修复前代码逐用例推演，「4 FAIL+3 绿」为该 stash 状态唯一可能形态）；④141/0/0 独立实跑吻合 134+7 声明（注：审计复跑为 -pl 模块级，-am 变体以实现者日志为准——改动面不外溢本模块，验证口径差异已记录）；⑤拒绝路径原样保留+双负对照断言错误码；⑥/⑦文档与日志在位
  - Phase 2 三条全 PASS：owner doc 三条逐一与 live 对照一致（collectDocVars 实现/GraphQLEngine:422 契约/parser :411 错误码/base.graphql:45+default.graphql 指令 location）；doc-links --strict 独立复跑 exit 0
  - Anti-Hollow：parseOperation→initDocument→resolveSelection→collectDocVars 调用链实读连通，7 测试全经引擎入口；hollow scan 独立复跑 0 findings exit 0；diff 证实为真实变量上下文传递非跳过校验
  - Deferred 分类检查：跨 operation 严格校验=out-of-scope improvement 成立（缺陷本体是已声明变量误拒，并集恰好消除；无静默错值通道）
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0（关闭前复跑确认）
  - 首轮审计裁决 Cannot Close 附 F-1（Major，台账翻页声明超前于落盘）——已按处方修复（roadmap/index/MR 表实际翻页+L97/L113 补勾），审计明示无需重新技术审计
- Follow-up:
  - no remaining plan-owned work
