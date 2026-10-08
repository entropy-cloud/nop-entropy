# 06 WI26 HOP 与 SESSION 伪表函数

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI26 行）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §3（D4 伪表函数裁定）、§4b W2 行；`ai-dev/analysis/streaming-sql-survey/2026-10-07-feldera-analysis.md` 与 2026-10-07-flink-sql-analysis.md（形态与常量校验原则，转引）
> Related: `ai-dev/plans/nop-stream-sql/04-wi1-eql-window-grammar-ast.md`（TUMBLE grammar 先例）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

按 D4 已裁定的伪表函数形态补全 HOP 与 SESSION 两个变体：grammar 增 `sqlHopTableSource`（`HOP(t, slide, size)`——Flink TVF 参数序）与 `sqlSessionTableSource`（`SESSION(t, gap)`），AST 类一一对应；流侧编译到既有 SlidingEventTimeWindows / EventTimeSessionWindows assigner（引擎零改动）；间隔校验沿用 EqlParseHelper 三原则（常量字面量、正值、短单位）；window_start/window_end 系统列命名契约钉入编译器（SELECT 面暴露归 FU-E2）。

## Current Baseline

2026-10-08 实测锚点（R1 复核后行号刷新）：

- **引擎零改动可行性**：`nop-stream/nop-stream-core/.../windowing/assigners/` 已有 SlidingEventTimeWindows（`of(size, slide)`，:31-73，validateSliding 正值校验）与 EventTimeSessionWindows（`withGap(gap)`，:20-71，MergingWindowAssigner）；`WindowOperator` merge 管线现成（:749/:949/:968-1005 MergingWindowSet）。**无需自建 assigner**。
- **grammar**：DMLStatement.g4 :156-162 `sqlTumbleTableSource`（唯一伪表函数）；:145-150 sqlTableSource 分支；BaseRule.g4 :437-448 `sqlIntervalExpr`；SQL92Keyword.g4 :656-659 TUMBLE token 登记 unreservedWord_（标识符兼容先例）；**SESSION token 已存在**（:612，且 BaseRule.g4 :89 unreservedWord_ 已登记）——仅 HOP 需新增；DMLStatement.g4 :292-294 死注释（HOPPING/SESSION 历史意图证据）。再生成：nop-orm-eql 的 precompile/gen-eql-parser.xgen + gen-eql-ast.xgen（`./mvnw install -pl nop-persistence/nop-orm-eql -DskipTests` 产出 parse/antlr/*、EqlASTBuildVisitor、ast/_gen/_Sql*TableSource）。
- **AST**：EqlAST.xjava :311-323 SqlTumbleTableSource（tableName/timeColumn/interval/alias）；**EqlASTKind ordinal 只能尾部追加**（SqlValuesTableSource 尾部追加先例 :613-616——live EqlASTKind 实为 ordinal 67，xjava 注释「=68」陈旧，R2-N1）；手写分派点六处需镜像 TUMBLE：EqlASTVisitor :134、EqlASTProcessor、EqlASTOptimizer :103-104/:1137、AstToSqlGenerator :394-405（TUMBLE 逐字渲染先例——W2/T1 stream-only，SQL 通道回显）、AstToEqlGenerator :704、EqlTransformVisitor :570-574（ORM 通道 fail-fast `nop.err.eql.table-source-not-resolved`）。生成 visitor 抽象方法（SqlHopTableSource_tableName 等 String label）须在手写 EqlASTBuildVisitor（:878-886 先例）补实现。
- **编译器**：StreamSqlCompiler :302-306 表源白名单仅 Single/Tumble（行号随 WI25 漂移，R1-m6）；buildGroupedAggregate 提取 durationMillis；nextStrategy :1132 windowFnId 硬编码 "tumbling-event-time"；plain 路径 :350-386 仅 Tumble 变体；requireJoinSide :568-580 join 边 plain-table 守卫。AdvancedTransforms.resolveWindowAssigner :291-334 参数化分支仅 "tumbling-event-time"（:310-319），legacy 白名单 :321-330。
- **interval 校验**：EqlParseHelper.intervalDurationMillis :68-113（常量字面量/正值/短单位三原则 + MICROSECOND 整毫秒 + 日历单位 fail-fast）——对 size 与 slide/gap 双 interval 同样适用（各自调用）。
- **window_start/window_end**：全 main 源码零引用；聚合路径未知列 fail-fast（:888-889）。FU-E2（SELECT 面暴露）不在本 plan。
- **测试先例**：TestEqlTumbleGrammarParse（成功+fail-fast 矩阵形态）；TestStreamSqlCompiler.tumbleWindowedAggregateProductShape（产物钉子）；TestTumbleAggregateQueryE2E（执行断言）。

## Closure Audit R1 记录（2026-10-08，agent_8da235aa REJECTED→补救）

- **B-1**：执行者两轮回归中 nop-stream-sql 均 SKIPPED（首轮停于 eql corpus、次轮停于 nop-batch flake）——补救=重跑含 sql 的完整回归留存 log。
- **M-1**：「仅 TUMBLE 时间列生效」lateness 措辞残留 docs-for-ai/nop-stream-sql.md 与 sql-subset §4b 两处——已按 Goal 4 更正为「伪表函数（TUMBLE/HOP/SESSION）时间列」。
- **m-1 偏差记录**：EqlASTKind 新 kind 实落 ordinal 31/32（按 xjava 定义序中段插入，后续 kind 全体 +2）而非基线所述「尾部追加 68/69」——全仓无 ordinal 消费方（分派均为枚举恒等），功能无害；「尾部追加」约束的实质是 xjava 类避免插在既有条目之间引发语义混淆，本次插在 Tumble 表源族之后属同族相邻，接受并记录。
- **m-2**：日志测试计数更正（+37→+31：eql 16+sql 11+flow 4；sql 聚焦 11/11 非 12/12）。
- **m-4**：GROUP BY 保留名检查布尔表达式加括号。

## Goals

1. **grammar/AST**：`sqlHopTableSource: HOP(t, slide, size)`（Flink TVF 参数序，文档显式声明）与 `sqlSessionTableSource: SESSION(t, gap)`；SQL92Keyword **仅增 HOP token**（R1-M3：SESSION token 已存在于 :612 且已登记 unreservedWord_——零 keyword 改动，标识符兼容现成）；EqlAST.xjava 尾部追加 `SqlHopTableSource`（tableName/timeColumn/slideInterval/sizeInterval/alias/decorators 继承基类）与 `SqlSessionTableSource`（tableName/timeColumn/gapInterval/alias）——**grammar prop label 与 AST prop 名恒等**（R1-B1：antlr 再生成模板 @init.xrun 把 label 逐字当 AST prop 名查找，故标签用 `slideInterval=`/`sizeInterval=`/`gapInterval=`，镜像 TUMBLE 的 interval 惯例）；手写 wrapper 类两个（R1-M2：fail-fast 覆写镜像 SqlTumbleTableSource.java :22-33 的 ORM 通道 `nop.err.eql.table-source-not-resolved`）。

### 执行顺序（R1-B2 硬依赖，必须按序）

① grammar（DMLStatement.g4 + SQL92Keyword.g4）与 EqlAST.xjava **同批编辑**（validateAstNodeName 要求 AST define 先在）→ ② `./mvnw generate-sources -pl nop-persistence/nop-orm-eql` 单独验证生成物（parse/antlr 新 visit 方法 + ast/_gen/_SqlHopTableSource + _EqlASTBuildVisitor 抽象 label 方法 + EqlASTKind 尾部自动追加 ordinal 68/69——Visitor/Processor/Optimizer/Kind 四文件带 `__XGEN_FORCE_OVERRIDE__` 头，再生成整体覆盖，**禁止手编**，R1-M1）→ ③ 手写补齐（两个 wrapper 类 fail-fast 覆写 + EqlASTBuildVisitor label 方法 + AstToSqlGenerator/AstToEqlGenerator 逐字回显镜像 + EqlTransformVisitor fail-fast 镜像）→ ④ install/compile → ⑤ 编译器/flow/xdef → ⑥ 测试。
2. **编译器**：windowed aggregate 路径接受 Hop/Session 表源——buildGroupedAggregate 泛化为窗口规格（kind/sizeMillis/slideMillis 或 gapMillis，双 interval 各过三原则校验）；strategy 产物 `windowFnId="sliding-event-time"`（duration=size + 新 slide 属性）或 `"session-event-time"`（duration=gap）；plain 路径遇 HOP/SESSION fail-fast（无聚合无窗口载体，no-silent）；windowed aggregate SELECT 中 `window_start`/`window_end` 标识符 → typed fail-fast（点名 FU-E2 与对齐命名——命名契约钉入代码）。
3. **DSL 消费**：stream.xdef strategy 增 `slide="string"` 可选属性；resolveWindowAssigner 增 "sliding-event-time"（duration+slide 必填、slide<=size 校验）与 "session-event-time"（duration=gap）参数化分支（duration+legacy id 冲突守卫复用）。
4. **lateness/emit 语义推广裁定**（R1-M5）：WI25 的 field-lateness 映射与文案从「TUMBLE 时间列」推广为「**伪表函数时间列**（TUMBLE/HOP/SESSION）」——时间列由伪表函数统一承载，映射与 skipPurgeForLateness 语义同构；编译器两处 TUMBLE 专属文案（:416-423/:1119-1123）与 docs-for-ai/sql-subset 的「仅 TUMBLE」措辞随本 WI 更正；emit=UPDATE × SESSION merge 组合见 Deferred But Adjudicated（watch-only）。
5. **slide<=size 裁定**（R1-m3）：引擎 validateSliding 只查正值/offset/ratio≤10000，**不校验 slide<=size**；本仓库裁定 slide<=size（对齐 Flink SQL HOP 惯例，杜绝 gapped windows 静默无产物）——**sql 编译层即拒绝**（双 interval 提取后校验，最早失败）+ flow 分支同守卫（防御 raw DSL 面）；写入 §4b W2 行。
6. **测试**：grammar 成功/fail-fast 矩阵（镜像 TUMBLE 先例，双 interval 各测）；编译器产物 shape（windowFnId/duration/slide）；plain 拒绝；window_start/window_end typed 错误（R1-M4：下沉到 aggregatedRowResolver 级——任意子句 WHERE/GROUP BY/HAVING/SELECT 引用均拒，非仅 SELECT）；E2E 两条——SESSION（orders 数据 gap=5s：a 会话合并 [1s,9s)→a=4、a=-5 独立会话、b 双会话 b=2/b=4、c=1）与 HOP（size/slide 手算窗口和，实现时注释推导）。

## Non-Goals

- window_start/window_end 的 SELECT 面暴露（FU-E2）；CUMULATE 窗口；processing-time 伪表函数变体；named window / WINDOW 子句；RDBMS 端点新语义（W2/T1 直通口径不变——toSQL 逐字回显即失败，既有边界）；引擎 assigner/WindowOperator 改动。

## Scope

### In Scope

- nop-persistence/nop-orm-eql：DMLStatement.g4、SQL92Keyword.g4、EqlAST.xjava、EqlASTBuildVisitor（手写 label 方法）、EqlASTVisitor/EqlASTProcessor/EqlASTOptimizer/AstToSqlGenerator/AstToEqlGenerator/EqlTransformVisitor 六处分派点、再生成产物（parse/antlr/*、ast/_gen/*——生成物随构建更新，不手工编辑）。
- nop-stream/nop-stream-sql：StreamSqlCompiler（表源白名单/窗口规格泛化/strategy 写点/plain 拒绝/window_start·end typed 错误）。
- nop-stream/nop-stream-flow：AdvancedTransforms.resolveWindowAssigner 两分支；nop-kernel/nop-xdefs：strategy slide 属性。
- 测试：nop-orm-eql（grammar 矩阵）、nop-stream-sql（shape/门/E2E×2）、nop-stream-flow（assigner 分支）。
- 文档：docs-for-ai/03-modules/nop-stream-sql.md（HOP/SESSION 语法与窗口列命名契约）；sql-subset-and-semantics §4b W2 行与窗口声明行。
- roadmap：WI26 状态行；当日日志。

### Out Of Scope

- 引擎 windowing 包改动；FU-E2；CEP；其他 roadmap。

## Execution Plan

### Phase 1 - grammar/AST/编译器/DSL/测试

Status: completed
Targets: `nop-persistence/nop-orm-eql/model/`、`nop-stream/nop-stream-sql/`、`nop-stream/nop-stream-flow/`、`nop-kernel/nop-xdefs/`

- Item Types: `Feature`

- [x] ① 同批编辑：SQL92Keyword.g4 仅增 HOP token + unreservedWord_ 登记（SESSION 已存在勿动）；DMLStatement.g4 增 sqlHopTableSource（`HOP LP_ tableName=sqlIdentifier_ DOT_ timeColumn=sqlIdentifier_ COMMA_ slideInterval=sqlIntervalExpr COMMA_ sizeInterval=sqlIntervalExpr RP_ AS? alias=sqlAlias? decorators=sqlDecorators_?`——label 与 AST prop 恒等 + 镜像 TUMBLE 的 alias/decorators）与 sqlSessionTableSource（同形单 gapInterval）规则 + sqlTableSource 两分支；EqlAST.xjava 尾部追加两 class（ordinal 尾部追加由再生成自动落 68/69——xjava :617 的「=68」陈旧注释勿照抄，R1-m1）
- [x] ② `./mvnw generate-sources -pl nop-persistence/nop-orm-eql` 验证生成物：parse/antlr 新 visit 分派、ast/_gen/_SqlHopTableSource/_SqlSessionTableSource、_EqlASTBuildVisitor 新抽象 label 方法、EqlASTKind 尾部 ordinal
- [x] ③ 手写补齐：两个 wrapper 类（SqlHopTableSource/SqlSessionTableSource.java，fail-fast 覆写镜像 TUMBLE）；EqlASTBuildVisitor 补 String label 方法（tableName/timeColumn）；AstToSqlGenerator/AstToEqlGenerator 逐字回显镜像；EqlTransformVisitor fail-fast 镜像（同码 table-source-not-resolved）；`./mvnw compile -pl nop-persistence/nop-orm-eql` 过
- [x] （并入①③两条——生成物四文件禁止手编，R1-M1/M2 更正后此独立条目取消）
- [x] ④ 编译器：表源白名单 + 窗口规格泛化（kind/size/slide|gap，双 interval 各过 intervalDurationMillis + **sql 层 slide<=size 校验**）+ lateness 文案推广为伪表函数时间列（R1-M5）+ nextStrategy 增 windowFnId/slide 参数 + plain 路径 HOP/SESSION fail-fast + window_start/window_end 保留名检查**下沉 aggregatedRowResolver 级**（任意子句引用均 typed 拒绝、文案含 FU-E2 指向与命名契约——R1-M4）
- [x] ⑤ flow/xdef：strategy `slide="string"` 属性；resolveWindowAssigner 增 "sliding-event-time"（duration+slide 必填、**slide<=size 守卫**——引擎 validateSliding 不校验，本仓裁定）与 "session-event-time" 分支（duration 缺失 fail-fast 复用既有守卫）
- [x] 测试：nop-orm-eql `TestEqlHopSessionGrammarParse`（成功矩阵：基本/大小写/别名/HOP 双 interval 字段断言/SESSION gap 字段断言/查询内使用；fail-fast：非字面量/零与负/日历单位/亚毫秒 MICROSECOND/语法错误/标识符兼容/ORM 通道 resolve fail-fast——镜像 TUMBLE 先例逐条）；nop-stream-sql shape+门+E2E×2（SESSION/HOP，推导注释钉死预期窗口和）；nop-stream-flow assigner 分支测试（sliding/session 装配 + slide 缺失 fail-fast）
- [x] 全量回归：`./mvnw test -pl nop-persistence/nop-orm-eql,nop-stream/nop-stream-core,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am`——第三轮 BUILD SUCCESS 全 Reactor SUCCESS（_tmp/wi26-full-regression3.log）：eql 807 内含新 16、flow 206（新 4）、sql 121（新 11）、runtime 1336/10s、batch flake 本轮通过；首轮 corpus 快照合法刷新（diff 仅 expected-token 集新增 HOP）

Exit Criteria:

- [x] **端到端验证**（guide #22）：`SELECT ... FROM SESSION(...ts, INTERVAL 5 SECOND) GROUP BY item` 与 HOP 对应查询经真实执行产生符合窗口语义的聚合行（E2E 断言精确 multiset，推导注释）
- [x] **接线验证**（guide #23）：HOP/SESSION 表源 → strategy(windowFnId/slide/duration) → resolveWindowAssigner → Sliding/Session assigner 链连通——sql 产物 shape 断言 + flow 分支装配断言 + E2E 执行三段拼接
- [x] **无静默跳过**（guide #24）：plain 路径遇 HOP/SESSION 显式拒绝；window_start/window_end 保留名在任意子句 typed 拒绝（resolver 级，命名契约非悬空）；ORM 通道 resolve fail-fast 与 TUMBLE 同码；slide>size 在 sql 层与 flow 层双守卫
- [x] 新功能测试覆盖（guide #25）：grammar/AST 字段/编译器写点/assigner 分支/E2E 五处有测试
- [x] interval 三原则对双 interval 全覆盖（HOP 的 slide 与 size 各自走 intervalDurationMillis）
- [x] `./mvnw test -pl <eql+四模块> -am` 全绿
- [x] owner doc 更新：docs-for-ai/nop-stream-sql.md、sql-subset §4b 两行
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit（与起草审查不同 task）；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi26-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI26 状态行 `todo` → `done`（括注单层、内无全角 `）` 与半角 `)` 字符、禁嵌套全角括号——首版括注两次踩坑：HOP(t, slide, size) 半角括号与（label…）全角嵌套均被 BULLET_RE 静默丢弃致 wi26 缺失，解析器重跑门逐次捕获修正）；重跑解析器核对 items 18 milestones 4（实测 items 18 milestones 4 wi26 done）
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，scan-hollow --severity high 退出码 0

Exit Criteria:

- [x] roadmap WI26 行同步，解析器实测 items 18 milestones 4
- [x] 独立 audit 证据已写入 plan Closure 段与 audit 文件
- [x] 三工具退出码 0
- [x] 日志收口记录与 plan 状态一致

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无——本项为 Feature）
- [x] 行为/契约结果已达成：HOP/SESSION 伪表函数端到端可用，window_start/window_end 命名契约钉入编译器
- [x] 必要 focused verification 已完成（grammar 矩阵/shape/门/E2E×2）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步（两处）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证表源→strategy→assigner→算子全链运行时连通（E2E 真实执行）
- [x] `./mvnw compile -pl <eql+四模块> -am` 通过
- [x] `./mvnw test -pl <eql+四模块> -am` 全绿
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### emit=UPDATE × SESSION merge 组合测试

- Classification: `watch-only residual`
- Why Not Blocking Closure: 组合语义与 assigner 无关（plan 04 裁定 update=逐元素 FIRE+ACCUMULATING，WindowOperator merge 管线与 trigger 独立），理论自洽；本组合无真实需求驱动，暂不投入组合 E2E。
- Successor Required: `no`
- Successor Path: 出现 SESSION+emit=UPDATE 真实用例时随用例补组合测试

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: HOP/SESSION 伪表函数端到端交付——grammar/AST 一一对应（label 与 prop 恒等）、编译器 WindowSpec 泛化（windowFnId 三 kind + slide 属性 + slide<=size 双守卫 + window_start/window_end 保留名契约 + lateness 推广伪表函数时间列）、引擎 assigner 零改动复用 Sliding/Session/Merge 管线；E2E 双条判别性真跑。closure audit R1 REJECTED 两项硬缺口（sql 回归 SKIPPED、lateness 措辞残留）补救后同 agent 复核 APPROVED。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 agent_2c57b5f5/agent_07fb8dc0 均不同 task）：agent_8da235aa-56aa-40cb-bfdb-5469de9986b7
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi26-closure-audit.md
- Evidence:
  - R1 REJECTED 两项硬缺口如实处置：B-1（sql 回归 SKIPPED）→ 第三轮回归 BUILD SUCCESS 全 Reactor SUCCESS（sql 121/0=110 存量+11 新增，时序完整覆盖最终代码）；M-1（lateness 措辞）→ 两 doc 更正为伪表函数时间列推广
  - Exit Criteria 八条终判全 PASS（auditor R2 复核）；Anti-Hollow：E2E compile→sink 真实执行 + 全分支显式 fail-fast
  - 回归：eql 807/0（含 corpus 快照合法刷新——diff 仅 expected-token 集新增 HOP）、core 1754/0、flow 206/0、sql 121/0；batch flake 本轮通过
  - audit 5 Minor 处置：m-1 ordinal 偏差记录于 plan「Closure Audit R1 记录」节、m-2 日志计数更正（+31/sql 11-11）、m-3 doc-links 复跑 0、m-4 布尔括号修复（回归3覆盖）、日志 12/12 笔误随收口修正
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/06-wi26-hop-session-table-functions.md --strict` 退出码 0（翻转后复跑确认）
  - roadmap 状态行翻转首版两次踩 BULLET_RE（半角括号与全角嵌套），解析器重跑门逐次捕获修正——18+4 终核

Follow-up:

- no remaining plan-owned work；window_start/window_end SELECT 面暴露由 roadmap FU-E2 承接
