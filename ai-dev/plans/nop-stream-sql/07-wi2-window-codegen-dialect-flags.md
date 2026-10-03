# 07 WI2 窗口 codegen 与方言能力开关

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI2 行、D3 裁定行与 D3 分层原则落地机制、§3.3、Cross-Cuting 2/4）
> Related: `ai-dev/plans/nop-stream-sql/04-wi1-eql-window-grammar-ast.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立审查两轮修订——B1 生成链改 wrapper 保留文件方案（见 Baseline 第 2 条）；B2 命名窗口编译遍历纳入 Scope；M1/M2 时序与遍历语义钉死。

## Purpose

让 WI1 引入的窗口 AST 可被翻译：AstToEqlGenerator / AstToSqlGenerator 输出 frame 与命名窗口（消除 round-trip 不对称），EqlTransformVisitor 补 WINDOW 子句遍历与引用校验，并按 D3 分层原则落地方言能力开关——三个按 frame 单位拆分的能力位（supportWindowFrameRows/Range/Groups），default.dialect.xml 集中缺省 false，翻译期未启用抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`。

## Current Baseline

- 打印器（2026-10-02 实测）：`AstToEqlGenerator.visitSqlWindowExpr`（:915-921）只打印 `over( partitionBy orderBy )`，frame/windowName 被静默丢弃；`printSelect`（:431-462，AstToSqlGenerator 继承复用）无 WINDOW 子句输出位。分页包装路径（PrintSqlSelect → printSelect）与直接路径都汇入基类 printSelect——修基类即双通道生效（审查实测）。`visitChild(null)` 安全（AbstractVisitor:22-25）。输出风格为小写关键字 + `over(` 无空格。
- **生成链约束（审查 B1 实测）**：nop-dao 的 pom **刻意未声明** exec-maven-plugin（nop-persistence/nop-dao/pom.xml:117-121 `<!-- No exec plugin - let's skip precompile -->`；runbook debug-codegen-and-generated-files.md:15,47 记载此陷阱），`generate-sources -pl nop-dao` 是 no-op；且 precompile 从本地仓库 nop-xdefs jar 读 xdef，需先 install。因此 `_DialectFeatures`（生成基类）不可重生成。
  - **采用方案：wrapper 保留文件**——`DialectFeatures extends _DialectFeatures`（`model/DialectFeatures.java` 为非下划线手写保留类，AGENTS.md 允许修改保留文件），在 wrapper 上补三个属性的 setter/getter。xdef 绑定按属性名反射 setter，wrapper 方法即可生效；后续若仓库启用 nop-dao precompile，生成基类获得同签名方法时 wrapper 覆盖无害（记 FU）。
- 特征位机制先例：`dialect.xdef` `<features>`（:45-58，24 能力位）；`IDialect.isSupportWithAsClause()`（:87）+ `DialectImpl.java:282-284` `Boolean.TRUE.equals(features.getSupportWithAsClause())`。xdef 属性无缺省值 → 缺失即 null → Boolean.TRUE.equals(null)=false，**机制上不写即关**；default.dialect.xml 显式写 false 是 D3 裁定「每个新能力位必须补缺省值」的集中声明（经 x:extends 属性合并传播），非功能必需——保留但注明。
- 翻译期裁决先例：`EqlTransformVisitor.java:1477-1483` returning for update → `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（OrmEqlErrors:190-192，ARG_DIALECT+ARG_FEATURE）+ `OrmEqlConstants` 特征名常量。
- **WINDOW 子句编译遍历缺失（审查 B2 实测）**：`EqlTransformVisitor.visitSqlQuerySelect`（:238-310）处理 from/projections/where/groupBy/having/orderBy/limit，**从不遍历 windowClause**——decl 内列不被 resolve（SQL 通道打印会 NPE）、SqlWindowDecl.frame 绕过能力门、`over w` 引用未声明窗口名无校验。基类默认 visit 实现的遍历被该 override 覆盖。
- 无 FROM 查询路径：AstToSqlGenerator.printSelect:114-132 select-from-dual 分支静默丢弃 WINDOW 等子句；`ERR_EQL_QUERY_NO_FROM_CLAUSE` 触发集（EqlTransformVisitor:252-255）不含 windowClause。
- WI1 已交付 AST 与 21 用例；`TestEqlCompileSql` 合成方言（`new DialectFeatures()` + setter）+ 最小实体上下文是能力开关测试的**正确工具**（需精确控制 features；与 WI4 验证真实 extends 链目的不同）。`AstToEqlGenerator` 无参构造 + `EqlASTNode.toSqlString()` 支撑 EQL 通道纯文本断言；断言用 contains 片段（pretty 缩进注入换行）。
- 「AND 缺 BETWEEN」语义（WI1 已裁定实现）：grammar 宽松可 parse，但 WI1 的 visitSqlWindowFrame 对「end 存在而 BETWEEN 缺失」显式抛 `ERR_EQL_INVALID_WINDOW_FRAME`——用例期望 = **拒绝**（断言抛该错误码），判据明确。
- WI4 已交付 `TestPostgresWindowFunctionDialect` 证明 nop-orm-eql 测试可加载真实方言（CoreInitialization + DialectManager）。

## Goals

- 打印：`visitSqlWindowExpr` 输出 `over(partitionBy? orderBy? frame?)` 与 `over w` 两形态；`printSelect` 在 HAVING 后 ORDER BY 前输出 WINDOW 子句；EQL 与 SQL 双通道一致。
- 编译遍历（B2）：EqlTransformVisitor.visitSqlQuerySelect 在 having 之后 orderBy 之前遍历 windowClause——逐 decl resolve spec 列、对 SqlWindowDecl.frame 按 unit 过能力门、校验 `over w` 的 windowName 必须命中当前查询声明的窗口名（未命中抛新错误码 ERR_EQL_UNKNOWN_WINDOW_NAME）；无 FROM 但有 windowClause 的查询纳入 ERR_EQL_QUERY_NO_FROM_CLAUSE 触发集（堵静默丢弃路径）。
- 能力位：dialect.xdef features 增 `supportWindowFrameRows`/`supportWindowFrameRange`/`supportWindowFrameGroups` 三 boolean；`DialectFeatures` wrapper 补三属性 setter/getter（不动生成基类与 nop-dao pom）；IDialect + DialectImpl 增三 getter；default.dialect.xml 集中缺省 false。
- 翻译门：visitSqlWindowExpr 对 inline frame 按 unit 校验（**先门后 super 遍历**——门未过即抛，不进入子节点解析；错误优先级 = frame 能力门先于子节点 unknown-function）；decl frame 的门在 windowClause 遍历中执行。
- 测试：TestEqlCompileSql 扩展（frame 三单位 开/关双态、命名窗口 EQL/SQL 双通道 round-trip、windowName 未声明报错、AND-缺-BETWEEN 拒绝、真实 default 方言缺省关闭断言）；WI1 既有 21 用例与全量回归绿。

## Non-Goals

- 不填各具体方言的启用值（WI3 实跑产出）；不改 window-expr-support.dialect.xml。
- 不做 W2 伪表函数；不改 WI1 grammar/AST 形态；不修 es/tdengine 登记缺口。
- 不给 nop-dao 启用 exec-maven-plugin（protected area 不扩大；wrapper 方案规避，记 FU）。
- eql-and-database-compatibility.md 的窗口口径全文校正归 WI5。

## Scope

### In Scope

- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/dialect.xdef`（features 三属性；plan-first 依 roadmap Cross-Cuting 4）
- `nop-persistence/nop-dao`：`model/DialectFeatures.java`（wrapper 三属性，非生成文件）、`IDialect` 三方法、`impl/DialectImpl` 实现、`default.dialect.xml` 缺省 false
- `nop-persistence/nop-orm-eql`：`AstToEqlGenerator`（visitSqlWindowExpr + printSelect）、`EqlTransformVisitor`（visitSqlQuerySelect 的 windowClause 遍历 + visitSqlWindowExpr 能力门 + no-from 触发集扩展）、`OrmEqlConstants`（三特征常量）、`OrmEqlErrors`（ERR_EQL_UNKNOWN_WINDOW_NAME）
- 测试：TestEqlCompileSql 扩展用例
- owner doc：eql-and-database-compatibility.md 能力开关一句；当日日志；plan 与 roadmap 收口

### Out Of Scope

- 各具体方言 dialect.xml 的启用值（WI3）；grammar/AST 变更；es/tdengine；nop-dao pom 生成管线接线（FU）。

## Execution Plan

### Phase 1 - 红：打印、遍历与能力门测试先行

Status: completed
Targets: TestEqlCompileSql 扩展

- Item Types: `Proof`

- [x] 新增用例（合成方言精确控制 features）：(a) features 关时 `sum(a) over (order by c rows between unbounded preceding and current row)` 编译抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` 且 ARG_FEATURE=supportWindowFrameRows；(b) features 开时同查询编译不再抛 NOT_SUPPORT 且 SQL 含 `rows between unbounded preceding and current row`；(c) RANGE/GROUPS 两单位各有独立开关断言；(d) 命名窗口 `over w` + `window w as (partition by o.name)` 编译后 SQL 含 WINDOW 子句且位于 having 之后 order by 之前、decl 内列被正确 resolve 为表列；(e) `over w` 引用未声明窗口名抛 `ERR_EQL_UNKNOWN_WINDOW_NAME`；(f) 无 FROM 但有 windowClause 抛 `ERR_EQL_QUERY_NO_FROM_CLAUSE`；(g) `rows 1 preceding and 1 following`（AND 缺 BETWEEN）抛 `ERR_EQL_INVALID_WINDOW_FRAME`；(h) 真实方言绑定（独立测试类 TestDefaultDialectWindowFeatures，CoreInitialization + DialectManager，见 N1 修订）：default 方言三能力位均为 false；(h2) 测试 fixture test-window-features.dialect.xml（放 src/test/resources/_vfs/nop/dao/dialect/，x:extends="h2.dialect.xml"（default 为不可独立加载的基文件）+ features supportWindowFrameRows="true"）经 DialectManager 加载断言 isSupportWindowFrameRows()==true——一条用例钉住 xdef 解析、wrapper setter、extends 合并、Boolean.TRUE.equals、IDialect getter 五环（false 断言区分不了绑定静默 no-op，true 侧才是有效方向）
- [x] 修复前运行确认红：TestEqlCompileSql 新用例编译期失败（setSupportWindowFrameRows/isSupportWindowFrameRows/ERR_EQL_UNKNOWN_WINDOW_NAME 符号不存在——正是待引入 API），(g) pre-green 如预期。当日日志已记录。**注：(g) 为 WI1 既有行为的回归钉用例，预期修复前即绿，不纳入红清单**

Exit Criteria:

- [x] 修复前新用例按预期失败（编译期符号缺失；(g) 除外——pre-green 回归钉）
- [x] 既有 TestEqlCompileSql / TestEqlCompiler 基线绿

### Phase 2 - 能力位与编译遍历

Status: completed
Targets: dialect.xdef、DialectFeatures wrapper、IDialect/DialectImpl、EqlTransformVisitor

- Item Types: `Feature`

- [x] dialect.xdef features 增三 boolean 属性
- [x] `DialectFeatures` wrapper 补三属性 setter/getter（Boolean 类型；**未动 `_gen` 生成基类、未动 nop-dao pom**；注：机制上 xdef 缺失属性即 null=false，显式写 default.dialect.xml false 是 D3 集中缺省声明）
- [x] IDialect 增三方法 + DialectImpl 实现（Boolean.TRUE.equals 模式）；OrmEqlConstants 增三特征常量；OrmEqlErrors 增 ERR_EQL_UNKNOWN_WINDOW_NAME + ARG_WINDOW_NAME
- [x] default.dialect.xml features 增三项 false
- [x] EqlTransformVisitor：visitSqlQuerySelect 入口经 windowNameScopes 栈收集声明窗口名；visitSqlWindowExpr 先校验 windowName 引用（未命中抛 ERR_EQL_UNKNOWN_WINDOW_NAME）再对 inline frame 按 unit 过能力门（先门后 super 遍历）；visitSqlQuerySelect 在 having 后 orderBy 前遍历 windowClause（decl frame 过门 + resolve 列）；no-from 触发集扩展 windowClause
- [x] 补 WI1 audit Minor-2 的「AND 缺 BETWEEN」用例（期望 = 抛 ERR_EQL_INVALID_WINDOW_FRAME，pre-green 回归钉）

Exit Criteria:

- [x] (h) h2 方言三能力位 false + (h2) fixture 方言 true 侧加载断言 2/2 绿（共同证明 xdef 属性 + wrapper setter 绑定端到端生效，true 侧排除静默 no-op；default.dialect.xml 为被继承基文件不能独立加载——缺 driverClassName，改用 h2 并在测试注释说明）
- [x] (a) 关断言绿、(c) RANGE/GROUPS 关态断言绿、(e)(f) 断言绿；(b) 门放行断言绿（编译不再抛 NOT_SUPPORT）；(g) 持续绿（pre-green 回归钉）。SQL 内容断言（(b)(c) 开态、(d)）按 M1 时序归 Phase 3 转绿，Phase 2 时点确认 3 个失败均为打印断言
- [x] 未重生成任何 `_gen` 文件（nop-dao 无 `_gen` diff）；owner-doc 更新归 Phase 3 一并落（IDialect/xdef 契约变更的文档项显式归属）

### Phase 3 - 打印器与转绿

Status: completed
Targets: AstToEqlGenerator

- Item Types: `Feature`

- [x] visitSqlWindowExpr：null 安全输出 partitionBy?/orderBy?/frame?；windowName 形态输出 `over w`；新增 visitSqlWindowFrame（unit 小写 + between/bound 五形态）与 visitSqlWindowFrameBound、visitSqlWindowClause
- [x] printSelect：HAVING 后 ORDER BY 前输出 WINDOW 子句；AstToSqlGenerator 经继承生效
- [x] Phase 1 全部用例转绿：TestEqlCompileSql 24/24（17 既有 + 7 新增）+ TestDefaultDialectWindowFeatures 2/2；`./mvnw test -pl nop-persistence/nop-orm-eql -am` 全绿 92 测试（WI1 既有 21 用例不回归）
- [x] owner doc：eql-and-database-compatibility.md 补「frame 能力开关」段（三能力位缺省关闭，启用矩阵归 WI3）；import 人工分组核对；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] frame 三单位与命名窗口的 EQL/SQL 双通道打印有 round-trip contains 断言证明
- [x] `-am` 全量绿（92 测试）
- [x] 无静默跳过：能力位关闭显式抛错；无 FROM + WINDOW 显式抛错；windowName 未声明显式抛错（各有测试）
- [x] owner doc 更新；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/ 当日条目更新

### Phase 4 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：首轮 FAIL（2 Major：EQL 通道 round-trip 断言缺失、三单位双态字面未满足）→ 补 testEqlChannelRoundTrip 与 testWindowFrameRangeOffGroupsOnGate（顺带修 bound 打印双空格缺陷与 4 Minor）→ 复核 PASS；证据落 ai-dev/audits/nop-stream-sql/wi2-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm-eql --severity high` 与 `--module nop-persistence/nop-dao --severity high` 均退出码 0（执行与 audit 双重复核）
- [x] audit 通过后 roadmap WI2 `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符）；解析器翻转后实测 items 31 milestones 7
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI2 = done，解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0

## Closure Gates

- [x] frame 三单位能力位各有 开/关 双态测试（复核确认矩阵闭合）；default.dialect.xml 集中缺省 false 在位（含真实方言加载断言）
- [x] frame 与命名窗口的 EQL/SQL 双通道打印有 round-trip 断言（testEqlChannelRoundTrip + SQL 通道断言）；WINDOW 子句位置有断言
- [x] WINDOW 子句编译遍历真实生效：decl 列被 resolve（SQL 通道输出正确表列）、windowName 未声明显式报错、decl frame 过能力门
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 绿（94 测试）；WI1 既有 21 用例不回归
- [x] scan-hollow 两模块高危零发现
- [x] 无静默跳过、无空壳实现（能力关/引用未声明/无 FROM 三条显式报错路径均有测试）
- [x] 未触碰生成管线（nop-dao pom 与 `_gen` 零 diff，audit 两轮确认）
- [x] owner doc 更新；import 分组人工核对
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id；首轮 FAIL → 补强 → 复核 PASS）
- [x] Anti-Hollow Check：能力门与 WINDOW 遍历在真实编译链路生效（audit 读码 + 端到端测试证明）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/07-wi2-window-codegen-dialect-flags.md --strict` 退出码 0

## Deferred But Adjudicated

（无——启用方言清单属 WI3 完成判定，非本计划 deferred 项）

## Non-Blocking Follow-ups

- nop-dao 生成管线接线（exec-maven-plugin 声明 + nop-xdefs install 顺序）：若未来启用，`DialectFeatures` wrapper 三属性可迁入生成基类并删除 wrapper 方法（wrapper 属性用 Boolean 类型对齐生成风格；注意生成基类的 deepClone/serializeFields/freeze 只覆盖生成字段，迁移时需随生成基类重建）——记 FU，当前不接线（protected area 不扩大）。

## Closure

Status Note: 窗口 AST 可被完整翻译——双通道打印 frame 与命名窗口、WINDOW 子句编译遍历与引用校验落地、三能力位经 wrapper 方案绑定并集中缺省关闭。首轮 closure audit FAIL 的 2 项测试缺口已补强并经同一 auditor 复核 PASS。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi2-closure-audit.md
- Evidence:
  - 首轮审计：机制本体全部 PASS（双通道测试 24/24 实测、能力门读码确认非空壳、生成管线零触碰、92 回归绿、scan-hollow 0）；FAIL 仅 2 Major 测试缺口
  - 补强：testEqlChannelRoundTrip（EQL 通道 toSqlString 断言）+ testWindowFrameRangeOffGroupsOnGate（RANGE 关/GROUPS 开，双态矩阵闭合）+ bound 打印双空格修正 + 4 Minor
  - 复核（同一 auditor 仅核两条 Major）：实测 PASS，nop-orm-eql -am 全量 94/94
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/07-wi2-window-codegen-dialect-flags.md --strict` 退出码 0

Follow-up:

- 见 Non-Blocking Follow-ups；无 plan-owned 剩余工作
