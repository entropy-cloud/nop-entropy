# 09 WI5 双目标翻译 golden 与文档校正

> Plan Status: draft
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI5 行、§3.3「文档口径」行、D3/D15 裁定、W1/W2 分账规则）
> Related: `ai-dev/plans/nop-stream-sql/08-wi3-dialect-window-matrix.md`（快照基建与实跑矩阵）
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立审查一轮修订——方言分账按 live x:extends 链重推：frame 可编译集合 5 方言（h2/postgresql/duckdb/postgis/h2gis）、fail-fast 集合 6 方言（dm/mariadb/mssql/mysql/mysql5.7/oracle）、db2 不可独立加载排除；Related 链接、doc-links 门禁与 BOUNDARY 警示补齐。

## Purpose

产出窗口 golden 翻译快照集并把 `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 的窗口口径从「支持标准 SQL 全部子句」的过宽宣示校正为「**grammar 接受度 ∩ 方言能力位 ∩ 方言继承链**（函数登记为排名族的附加维度）」的权威口径。「窗口两族」= inline over 族 + 命名窗口族（W2 TUMBLE 未进语法层，见 Deferred）。M1 里程碑收官项。

## Current Baseline

- **方言 frame 分账（2026-10-02 实测 x:extends 链，审查复核）**：
  - frame 可编译集合（features true×3，含继承）= **5 方言**：h2、postgresql、duckdb（extends postgresql）、postgis（extends postgresql+geo-support）、h2gis（extends h2+geo-support）。
  - fail-fast 集合（features false×3）= **6 方言**：dm、mariadb、mssql、mysql、mysql5.7、oracle。
  - db2：不可独立加载（缺 driverClassName，生产经 selector 注入）——不适用快照与 fail-fast 断言。
- WI3 快照现状：`__snapshot/<dialect>.window.sql` 共 **10** 方言（dm/duckdb/mariadb/mssql/mysql/mysql5.7/oracle/postgis/postgresql/h2gis——postgresql 在库），只含**无 frame** 的 over 与 WINDOW 子句两类查询；带 frame 的生成文本仅有 WI2 合成方言单测与 WI3 的 TestH2WindowFrameCompileEndToEnd（真实 h2 rows 单查询断言）——**无方言级 frame golden 文件**（本计划独有增量，不与 WI3 重复记账：WI3 是无 frame 两类，本计划是 frame 六组）。
- 能力门机制（WI2）：`EqlTransformVisitor.checkWindowFrameFeature` 按 unit 查 `isSupportWindowFrameRows/Range/Groups`，未启用抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` 且 param feature=<单位名>。
- 文档口径（实测 `eql-and-database-compatibility.md:34`）：「EQL 支持标准 SQL 的全部子句：…窗口函数」——roadmap §3.3 点名的过宽宣示；:40 的「函数登记 + 能力开关」段是排名族附加维度（WI4 修复），校正时**必须保留**。
- W2（TUMBLE 伪表函数）尚无 EQL 语法（D4 方向已裁定，落地归流侧后续 WI）；实跑口径（D15/WI3）：h2 与 postgresql:16-alpine 七组实跑全 PASS，其余执行未实测已标注于 `ai-dev/design/nop-stream/sql-window-dialect-matrix.md`。
- 文档引用边界：`check-doc-links.mjs` BOUNDARY 规则将 docs-for-ai 反引号引用 ai-dev/ 路径判 error（WI3 audit Blocker-1 先例）——本计划文档校正**只作纯文字转述矩阵结论，不得引用 ai-dev/ 路径**。

## Goals

- frame golden 扩齐至 **5 方言**：h2/postgresql/duckdb/postgis/h2gis 各增 `__snapshot/<dialect>.frames.sql`（rows/range/groups × inline/named 6 组 + named 无 frame 对照，经真实方言 EqlCompiler 编译产出，逐行 contains 断言）。
- fail-fast 行为快照：**6 方言**（dm/mariadb/mssql/mysql/mysql5.7/oracle）参数化断言 rows/range/groups 三单位 frame 查询各抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`（param feature=对应单位名）；db2 显式排除并注明理由。
- 文档校正：窗口口径段改写为三层交集口径（grammar 接受度 ∩ 能力位 ∩ 继承链；函数登记为排名族附加维度——保留 :40 既有表述），已实测开启方言（5 个）与 fail-fast 方言（6 个）列出，W2 未进语法层标注；**纯文字转述矩阵结论，零 ai-dev/ 路径引用**。

## Non-Goals

- 不为未实测方言开启 features（duckdb/postgis/h2gis 已开启属继承事实，无需动作；dm 等维持 false）。
- 不产出 W2 TUMBLE golden（语法未进入 grammar）。
- 不改 grammar/AST/翻译器行为代码；不重复 WI3 实跑执行（golden 仅编译产出）。

## Scope

### In Scope

- nop-orm-eql 测试：`TestDialectWindowSqlSnapshot` 扩展（frame golden 断言 + fail-fast 参数化断言；同步修正其 :33 陈旧注释）
- `__snapshot/{h2,postgresql,duckdb,postgis,h2gis}.frames.sql` 5 个 golden 文件
- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 窗口口径校正
- `ai-dev/design/nop-stream/sql-window-dialect-matrix.md` §4 相关行补一句 WI5 交叉注记（M5 裁定：补注）
- roadmap WI5 状态行与括注；当日日志；plan 收口

### Out Of Scope

- W2 TUMBLE golden；grammar/翻译器代码变更；WI3 已交付内容的重做；db2 的 driverClassName 补齐。

## Execution Plan

### Phase 1 - frame golden 与 fail-fast 快照

Status: completed
Targets: nop-orm-eql 测试（扩展 TestDialectWindowSqlSnapshot）

- Item Types: `Proof`

- [x] 5 方言 frame golden：h2/postgresql/duckdb/postgis/h2gis 各产 `<dialect>.frames.sql`（6 组 frame 查询 + named 无 frame 对照，经真实方言 EqlCompiler 编译产出——仅编译不执行，区别于 WI3 端到端），golden 入库并逐行 contains 断言；同时修正测试类 :33 陈旧注释
- [x] fail-fast 参数化断言：6 方言 × rows/range/groups 三单位各断言抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` 且 param feature=对应单位名；db2 排除注明理由
- [x] W2 标注：测试类注释注明 TUMBLE 未进入语法层
- [x] 当日日志记录 golden 与 fail-fast 结果

Exit Criteria:

- [x] 5 个新 golden 入库且断言绿；6 方言 × 3 单位 fail-fast 断言绿（18 断言，TestDialectWindowSqlSnapshot 21/21）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 全量绿
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 文档校正与收口

Status: completed
Targets: owner doc、矩阵文档、plan、roadmap

- Item Types: `Proof`

- [x] `eql-and-database-compatibility.md` 窗口口径段改写：三层交集口径（grammar 接受度 ∩ 能力位 ∩ 继承链；函数登记为排名族附加维度，保留 :40 表述）+ frame 已开启方言 5 个与 fail-fast 方言 6 个列出 + W2 未进语法层标注；**警示：矩阵结论只作纯文字转述，零 ai-dev/ 路径引用（WI3 Blocker-1 先例）**
- [x] 矩阵文档 §4 h2gis/duckdb/postgis 行补 WI5 frame golden 交叉注记
- [x] 独立子 agent closure audit（不同 task_id）：核验 golden 断言、fail-fast 集合、文档三层交集表述与 live 行为一致、零 ai-dev 引用；证据落 ai-dev/audits/nop-stream-sql/wi5-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm-eql --severity high` 退出码 0
- [x] audit 通过后 roadmap WI5 `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符；三轮 FAIL→FAIL→PASS）；解析器核对 31 + 7：`node -e "import('./tools/mission-driver/src/roadmap-check.mjs').then(m=>{const r=m.parseRoadmapMarkdown(require('fs').readFileSync('ai-dev/backlog/nop-stream-sql-roadmap.md','utf8'));console.log('items',r.phases.filter(i=>!i.isMilestone).length,'milestones',r.phases.filter(i=>i.isMilestone).length)})"`（于仓库根执行）
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI5 = done，解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/ 当日条目已更新

## Closure Gates

- [x] frame golden（5 方言 × 6 组 + 对照）入库且断言绿
- [x] 6 方言 × 3 单位 fail-fast 参数化断言绿
- [x] 文档窗口口径为三层交集表述（含函数登记附加维度保留）且与 live 行为一致（audit 核对）；零 ai-dev/ 路径引用
- [x] W2 未进语法层标注在位
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql -am` 绿
- [x] scan-hollow 高危零发现
- [x] ai-dev/logs/ 当日条目已更新
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/09-wi5-translation-golden-and-doc.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### W2 TUMBLE 系 golden

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: W2 伪表函数未进入 EQL 语法层（D4 已裁定方向，落地归流侧后续 WI），无法产出翻译 golden；roadmap W1/W2 分账规则明确 W2 永不承诺 RDBMS 等价
- Successor Required: `no`
- Successor Path: W2 语法落地后按 D4 T1/T2/T3 口径补 golden

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: （待 closure audit 后填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Audit Session: （待填写）
- Evidence: （待填写）

Follow-up:

- 无 plan-owned 剩余工作
