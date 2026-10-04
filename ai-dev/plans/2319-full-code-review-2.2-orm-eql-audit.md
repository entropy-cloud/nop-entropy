# 2319 full-code-review 2.2 nop-orm-eql 深审

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.2（MA2）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 10/15/16 + 共享前缀）
> Related: plan 2314（1.7 已裁定 EqlASTOptimizer+EqlASTProcessor 为 `__XGEN_FORCE_OVERRIDE__` 生成死代码——codegen 模板层 plan-first 工单队首，本项标「已裁定」不重复报告）；plan 2318（2.1 已审 nop-orm 会话域，check2 nop-orm 三 P1 already-fixed 先例——本项 check2 eql P0 复核同口径）
> Skill 适配注记：EQL 编译器为语言处理管线（非 XDSL 模块），维度按编译域适配——维度 10 → EQL 编译正确性（parse→AST build→transform→SQL 生成四段语义：parse 手写族与 meta 元数据供给、集合表源/操作符改写、方言路由、别名解析、参数标记）；维度 15 → AST 类型推断与转换安全；维度 16 → 测试覆盖质量（15 测试文件（142 @Test + 快照参数化用例） 对 233 main；点名「回归测试缺口评估」）。生成物豁免清单：`parse/antlr/`（EqlParser 10785/EqlLexer 2732/listener/visitor 族，ANTLR 生成）、`ast/_gen/`、`_EqlASTBuildVisitor`（1804，codegen 生成——执行时复核豁免判定）、EqlASTOptimizer（1988，已裁定死代码）。
> 只读审计（P0 走异步注入）；不读 `ai-dev/audits/`/`ai-dev/plans/` 等历史记录，具名例外输入仅二：check2/nop-orm-eql.md（roadmap 点名）与 2.1 报告 `2.1-orm-ormmodel-deep-audit.md` 发现清单（跨界发现撞车对照用）。
> Draft Review: agent_34ed232d 首轮 3 Major（EqlASTVisitor 实为 `__XGEN_FORCE_OVERRIDE__` 生成物、roadmap 点名的 EqlCompiler 零提及、parse/meta/eval/param/binder/utils 约 29 手写文件无子 agent 认领且与 Closure Gate 自斥）+ 4 Minor 全修；agent_5d5880e9 聚焦复审 PASS（M1 补 orch/M2 补 EqlASTKind/M3 修正出处标注后可执行）。横跨 nop-orm/nop-orm-eql 的交界发现若无法单边归属，登记后报 R2.0 裁决，不在本 plan 内自行裁定。

## Purpose

执行 roadmap 工作项 2.2：深审 nop-orm-eql（EQL 编译器：EqlTransformVisitor/CollectionOperatorTransformer/SQL 生成链），产出报告 `2.2-orm-eql-deep-audit.md`；复核 check2 已报 P0（集合属性表源转换 join 放大，`_some`/`_all` 受影响）的 live 状态与回归测试缺口。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-persistence/nop-orm-eql`：main 233 / test 15（142 @Test）。手写核心（实测）：EqlTransformVisitor 1673、CollectionOperatorTransformer 711、AstToEqlGenerator 1023、AstToSqlGenerator 565、CollectionTableSourceHelper 170、EqlCompiler 131、ExprTypeResolver/SqlParamTypeResolver、meta/（SqlExprMetaCache/EntityTableMeta/SelectResultTableMeta）、eval/（SqlExprToExpressionTransformer/SqlExprToFilterBeanTransformer）、param/（TenantParamBuilder/EntityPropParamBuilder）、parse 手写族（EqlASTBuildVisitor/EqlExprASTParser/EqlExprParseTreeParser/EqlParseHelper）、utils/（EqlASTBuilder/EqlHelper/OrmDialectHelper）。生成物/已裁定豁免清单：`parse/antlr/`（EqlParser 10785/EqlLexer 2732/listener/visitor 族，ANTLR 生成）、`ast/_gen/`、`_EqlASTBuildVisitor`（1804，`_` 前缀 codegen 生成——执行时复核豁免判定）、EqlASTOptimizer 1988 + EqlASTProcessor 492 + EqlASTVisitor 674 + EqlASTParser + EqlParseTreeParser + EqlASTKind 140（均带 `//__XGEN_FORCE_OVERRIDE__` 生成标记或属 1.7 已裁定死代码，见 Related）。
- check2（`ai-dev/audits/check2/nop-orm-eql.md`）11 条：P0×1（:19 集合属性表源转换在外层查询生成多余 join——行数放大/非法 SQL，`_some`/`_all` 同样受影响）+ P2×5（:76 returning 绕过方言检查、:103 十六进制/位字面量必抛、:131 ILIKE 静默降级、:154 `_all` 取反三值逻辑漂移、:180 集合表源 to-one 丢用户别名）+ P3×5。本项点名复核 P0×1 与回归测试缺口；P2/P3 不进范围（独立撞见按新发现处理，check2 余量留 R1.0 裁决或 watch-only）。
- 2.1 先例（2026-10-04）：check2 nop-orm 三 P1 全部 already-fixed 且修复在被点名调用链上——本 P0 复核不预判，以 live code + 实测为准。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS（含本模块）；2.1 定向测试已证 nop-persistence 相关链路可跑。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.2-orm-eql-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.2-<序号>`）+ 驳回台账（正文）+ 裁决。
- EQL 编译正确性面（维度 10 适配）：transform 链（EqlTransformVisitor：集合表源转换/关联 join/子查询/filter 变换）语义正确性；集合操作符改写（CollectionOperatorTransformer：`_some`/`_all`/`_none` 等的 EXISTS/join 改写与取反语义）；SQL 生成（AstToSqlGenerator：方言路由/returning/分页/参数标记 SqlParameterMarker）；别名解析与作用域（SqlTableScope：CTE/子查询遮蔽）；AstToEqlGenerator 反向生成一致性。
- 类型安全面（维度 15）：AST 表达式类型推断（字面量/函数/路径类型）、转换热点、裸强转。
- 测试覆盖面（维度 16）：15 测试类对编译链各阶段的覆盖映射；方言矩阵测试（returning/ILIKE/分页方言分支）；集合操作符语义测试（含三值逻辑/NULL）；**check2 P0 对应的回归测试缺口评估**（点名任务）。
- check2 P0 复核：live 标定 still-live / already-fixed / false-positive / duplicated——必须实测。实测载体：优先跑既有回归 `TestEqlCompileSql`（:327/:340/:351 录名于 check2 :74 处置注、:363 to-one 用例录于 check2 :200）+ `_tmp/` 下 scratch 编译取样生成 SQL（临时代码一律落 `<project-root>/_tmp/`，禁止写模块 src）；行数放大复核需 >1 子行数据卡（check2 :70 指出 1:1 数据掩盖放大），附 file:line 与生成 SQL 样本。
- index 登记 + 独立 closure audit + roadmap 2.2 翻 done。

## Non-Goals

- nop-orm 会话/持久化域（2.1 已审）；nop-dao/nop-db-migration（2.3）；EQL 语法层 grammar 文件本身的修订建议（语法属 xdef/ANTLR 生成链，发现只登记不改语法）。
- EqlASTOptimizer 死代码处置（plan 2314 已裁定，codegen 模板层工单）——本项不重复立案。
- SQL 执行层（nop-dao JdbcExecutor 等）；数据库方言库内部实现。
- check2 eql P2×5/P3×5 的逐条复核。

## Scope

### In Scope

- `nop-persistence/nop-orm-eql/**`
- 只读输入：`ai-dev/audits/check2/nop-orm-eql.md`（仅 P0 :19-74 点名条目 + 各条目标题作撞车对照）

### Out Of Scope

- nop-persistence 其余兄弟模块；ANTLR grammar 生成链；数据库 drivers。

## Execution Plan

### Phase 1 - 深挖（deep-audit 迭代轮次，子 agent 并行）

Status: completed
Targets: nop-orm-eql

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：手写文件行数榜（排除生成物，给出豁免清单核验）、测试类↔编译链阶段映射表、方言分支清单（grep SqlDialect/ILIKE/returning/分页）
- [x] 编译正确性子 agent（维度 10 适配）：EqlTransformVisitor 全链 + CollectionTableSourceHelper + SqlTableScope 别名作用域 + addTableFilterForPropJoin（含 check2 P0 复核——live 标定 + 实测）
- [x] 集合操作符子 agent（维度 10 适配）：CollectionOperatorTransformer `_some`/`_all`/`_none` 改写语义（EXISTS/join/取反/三值逻辑）+ AstToSqlGenerator 方言路由/returning/参数标记 + AstToEqlGenerator 反向一致性
- [x] 编译编排与外围包子 agent（维度 10 适配）：EqlCompiler 编排链（decorator/IEqlAstTransformer 前后钩子/useTenantModel/paramBuilders/columnBinders/DataSetMeta 组装）+ parse 手写族（EqlASTBuildVisitor/EqlParseHelper 等——check2 P2 :103 事故点在域内）+ meta/ + eval/ + param/（TenantParamBuilder 租户参数注入面）+ binder/ + utils/
- [x] 类型与测试子 agent（维度 15+16）：AST 类型推断/转换热点 + 测试覆盖映射与缺口评估（方言矩阵/集合操作符三值逻辑/错误路径）
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-2.2-{transform,coll,orch,types-tests}.md`

Exit Criteria:

- [x] 四份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check2 P0 逐条标定结论落盘（still-live/already-fixed/false-positive/duplicated + 实测证据与生成 SQL 样本）
- [x] P0 对应回归测试缺口评估结论落盘（含测试数据基数 1:1 掩盖是否仍存在的显式判定）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 每维度派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [x] 统一 severity 判级；与 2.1（会话域）/4.2（测试有效性全仓桶）边界协调；check2 重复发现的归属裁定
- [x] 集合操作符正结论收敛（改写语义/方言路由一条总评）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-2.2-{transform,coll,orch,types-tests}-review.md`；保留发现判级理由 + 证据可复现
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `2.2-orm-eql-deep-audit.md`；index 登记 2.2 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（含 check2 P0 若 still-live 的即时通道处置）
- [x] 定向验证锚点：`./mvnw test -pl nop-persistence/nop-orm-eql -am`（无条件执行；如遇失败归因预存并登记）
- [x] 独立子 agent closure audit；通过后 roadmap 2.2 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因为预存失败并在报告/log 登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且编译链全阶段覆盖（parse 手写族+meta/编排（EqlCompiler）/transform/集合操作符/SQL 生成/别名作用域/param/binder/utils/类型/测试）
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 2.2 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。check2 eql P2×5/P3×5 留 R1.0 裁决通道或 watch-only 归档处理。）

## Closure

Status Note: 只读审计计划收口。报告 `2.2-orm-eql-deep-audit.md` 落盘：**不通过附 P0×3**（否定编码根因簇：NOT `_some` 语义反转/嵌套 `_all` 量词丢失/NOT+嵌套否定错位——均探针实证，触发 P0 即时通道，异步注入 plan 2320）；P1×11/P2×17/P3×29（60 条唯一发现，66 候选中 6 条跨维度去重、降级 7、驳回 0，判级调和 7 处留痕）；check2 点名 P0 join 放大标定 already-fixed（removeIf 双路径+26/26 回归+探针正交复核），1:1 数据掩盖缺口坐实（P2-2.2-001）。定向测试 163/163 全绿（2026-10-04 实跑）。closure audit 一次 PASS；advisory（日志日期归属）已按建议归位于 10-04.md。审计工作项 done 不被 P0 阻断（修复由 plan 2320 独立承载，与 roadmap「审计=发现职责、修复=Wave 2 职责」一致）。
Completed: 2026-10-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_ed642f79-d570-4627-bdb3-57e73fa59cee（fresh session，只读 closure auditor）
- Evidence:
  - 计数四方勾稽：报告 P0=3/P1=11/P2=17/P3=29=60 ↔ 报告内实点（P0 表 3 + P1 表 11 + P2 逐数 17 + P3 逐数 29）↔ index 2.2 行 ↔ 四份复核文件 66−6=60，零缺陷
  - 去重台账 6 条与复核文件归属建议一一对应；判级调和 7 处终判与四份 review「修订后口径」一致（dim10a 少数意见保留表述在案属实）
  - P0 即时通道合规：index「P0 发现追踪」3 行=报告 P0 表，修复路径=plan 2320（已拟制、对抗审查 1 Blocker+5 Minor 全修、commit 97596184a5）；roadmap 规则 #4/横切关注点逐项合规
  - 锚点抽验 8/8 命中 + 加验 2 处（AstToSqlGenerator:332/:354-368、git show f0fb70773f --stat 半修复回归实证）
  - 定向测试：surefire 15 类 mtime 2026-10-04 19:57，163/0/0/0；TestEqlCompileSql 四个 check2 点名回归精确在位
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2319-full-code-review-2.2-orm-eql-audit.md --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0（只读审计无新增实现）
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以模块定向 test 全绿 + 探针工件（_tmp/probe/、_tmp/scratch/）为锚
  - Deferred 项分类检查：无 deferred；P0×3 走即时通道（plan 2320 open）、P1×11 经 index「P1 发现汇总」进 R1.0 簇B/C/D/E，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P0 修复=plan 2320；P1×11+P2×17+P3×29 修复归属 R1.0 对应簇；契约文档迁移归 G.2——移交注记在案）
