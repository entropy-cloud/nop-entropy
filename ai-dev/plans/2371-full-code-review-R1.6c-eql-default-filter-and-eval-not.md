# 2371 full-code-review R1.6c — nop-orm-eql 实体级缺省过滤器+eval 转换器否定修复（簇 E：P1-2.2-009/011）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/full-code-review-roadmap.md` R1.6c 行；finding `P1-2.2-009`（簇 E，dim10c-02）/`P1-2.2-011`（dim15-03）（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.2-orm-eql-deep-audit.md`；细节 `_tmp/fcr-2.2-orch.md`/`_tmp/fcr-2.2-types-tests.md`）

## Purpose

修复 nop-orm-eql 簇 E 两条 P1：①EQL UPDATE/DELETE 不注入实体级缺省过滤（租户/逻辑删除/实体 filter）也无 FILTER marker——与 SELECT 通道不对称，useTenant 实体手写 DML 可跨租户写（租户安全面）；②eval 转换器（公开 jar 唯一 SQL→FilterBean/Expression 转换入口）丢失 `not like`/`not in` 否定语义（静默语义反转）。

## Current Baseline

- **P1-2.2-009**：`collectDefaultEntityFilter`（`EqlTransformVisitor.java:403-425`：逻辑删除+租户+实体 filter 三类）仅由 SELECT 路径调用（`addEntityFilter`:460、join 路径 :616/:917）；`visitSqlUpdate`（:1567 起）/`visitSqlDelete`（:1628 起）解析出目标 `SqlSingleTableSource` 但不注入任何缺省过滤。审计探针：同实体 SELECT 注入 `NOP_TENANT_ID=?` 而 UPDATE/DELETE 无任何租户条件。
- **注入机制可复用（审查 B1 勘正）**：`collectDefaultEntityFilter(table, consumer)` 直接可用（DML 目标 source 的 resolvedTableMeta 与 SELECT 同为 ISqlTableMeta/EntityTableMeta）；但 **`makeWhere()` 仅存在于 SqlQuerySelect**（SqlUpdate/SqlDelete 只有生成的 getWhere/setWhere）——DML 侧需内联「getWhere()==null → new SqlWhere()+setWhere()」模式后再 appendFilter。DML where 由 AstToEqlGenerator :353/:390 `printWhere(node.getWhere())` 渲染，附加条件会被生成。
- **P1-2.2-011**：`SqlExprToFilterBeanTransformer.transformLike`（:121-130 无 getNot 分支）/`transformInValues`（:164-171 无 getNot 分支）——对照 `transformBetween`（:157 有 not 包裹）与 `transformIsNull`（:145 有）证明遗漏；`FilterBeans.notIn`（:92）/`FilterBeans.not`（:292）已存在。Expression 侧 `SqlExprToExpressionTransformer.transformLike`（:144-147）/`transformInValues`（:227-231）同缺，既有做法=`UnaryExpression.not`（transformBetween :212-214）。
- **sql_lib 盘查（审计要求的跨模块核查）**：全仓 `_vfs` 资源 grep `update`/`delete from`（*.sql/*.xlib/*.xjson）——无任何手写 EQL DML 语句（命中仅为测试 init 数据）；当前 DML 触发面=代码级 `EqlCompiler` 调用。
- 测试基建同 R1.6a/b；租户/逻辑删除模型需在测试 context 显式声明（`OrmEntityModel` 租户列/逻辑删除列 setter，`collectDefaultEntityFilter` 读 `entityModel.isUseTenant()`/`tableMeta.isUseLogicalDelete()`）。

## Goals

- EQL UPDATE/DELETE 与 SELECT 缺省过滤对齐：目标表注入逻辑删除条件（`context.isDisableLogicalDelete()` 为 false 时）+ 租户条件（useTenant）+ 实体 filter（hasFilter）。
- eval 双转换器 `not like`/`not in` 否定语义正确（FilterBean 侧 notIn/not 包裹、Expression 侧 UnaryExpression.not 包裹）。
- enableFilter（数据权限 FILTER marker）保持 SELECT-only，文档显式声明边界。
- sql_lib 核查证据落档（已勘察：无现存手写 EQL DML）。

## Non-Goals

- 不为 DML 引入 FILTER marker（数据权限 marker 依赖 SELECT 渲染埋点；DML 数据权限由上游 biz 层动作鉴权承担——文档声明）。
- 不修 `SqlLikeExpr.ignoreCase` 丢弃（审计现状注记非修复指令；登记 follow-up）。
- 不修 P2-2.2-006（INSERT 租户列镜像面，台账通道）、dim10c-18。

## Scope

### In Scope

- `EqlTransformVisitor.visitSqlUpdate/visitSqlDelete`：目标 source 解析后调用 `collectDefaultEntityFilter(source, filter -> node.makeWhere().appendFilter(filter))` + `source.setFilterAlreadyAdded(true)`。
- `SqlExprToFilterBeanTransformer.transformLike/transformInValues`：not 包裹（like→not(like)、in→notIn）。
- `SqlExprToExpressionTransformer.transformLike/transformInValues`：not 包裹（UnaryExpression.not）。
- 回归测试：租户/逻辑删除模型 UPDATE+DELETE 注入断言（SQL 含 NOP_TENANT_ID 参数与逻辑删除列条件）；disableLogicalDelete 开关回归；`not like`/`not in` 双转换器四断言。

### Out Of Scope

- FILTER marker DML 化（Non-Goals 已述）；P2 台账条目；`ignoreCase`。

## Execution Plan

### Phase 1 - 注入对齐 + 否定修复 + 回归测试

Status: completed
Targets: `EqlTransformVisitor.java`、`eval/SqlExprToFilterBeanTransformer.java`、`eval/SqlExprToExpressionTransformer.java`、新测试类

- Item Types: `Fix`

- [x] visitSqlUpdate/visitSqlDelete：目标 source 解析后**内联 makeWhere 模式**（`SqlWhere where = node.getWhere(); if (where == null) { where = new SqlWhere(); node.setWhere(where); }`）→ `collectDefaultEntityFilter(source, where::appendFilter)` + `source.setFilterAlreadyAdded(true)`（保持生成类基数零改动——审查建议内联）
- [x] FilterBean 侧：transformLike `expr.getNot()` → `FilterBeans.not(like)`（**空值早退分支内同样包裹**——审查 A2：`not like ''` 现返回 alwaysFalse，包裹后应为 alwaysTrue 形态）；transformInValues `expr.getNot()` → `FilterBeans.notIn(name, values)`
- [x] Expression 侧：transformLike/transformInValues `expr.getNot()` → `UnaryExpression.not(loc, ret)`
- [x] 测试：①租户模型 update/delete SQL 含 `NOP_TENANT_ID = ?`；②逻辑删除模型注入 delete_flag 条件 + `disableLogicalDelete=true` 时不注入；③`o.status not in (1,2)` → FilterBean `$type:notIn`；④`o.name not like 'a%'` → `$type:not`；⑤Expression 侧同两形态否定断言；⑥普通 update/delete（无租户无逻辑删除模型）不受影响
- [x] 修复前判别：stash 注入与否定修复跑新测试，①②③④⑤必须 FAIL（缺租户条件/肯定语义），留输出入 daily log

Exit Criteria:

- [x] 回归测试全绿；stash 判别记录在案
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql` 全绿（197+新增）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 收口

Status: completed
Targets: owner doc、roadmap、轮次 index、本 plan

- Item Types: `Proof`

- [x] owner doc：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md` 增补「EQL DML 缺省过滤对齐 + enableFilter 边界」段（含 sql_lib 盘查结论：现存资源库无手写 EQL DML；**边界声明：UPDATE 注入逻辑删除条件后，恢复类/触及已删行的语句须走 disableLogicalDelete 通道或原生 SQL**——审查 A1 语义裁定）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-orm-eql --severity high` 退出码 0
- [x] roadmap R1.6c 行 → done；index P1-2.2-009/011 → fixed + MR 表 R1.6c 行
- [x] 文本一致性核对五处一致；check-plan-checklist --strict 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] roadmap 与 index 回填一致
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P1-2.2-009 修复落地：UPDATE/DELETE 注入三类缺省过滤（租户安全面对齐）+ disableLogicalDelete 开关保持
- [x] P1-2.2-011 修复落地：双转换器 not like/not in 否定语义回归锁定
- [x] enableFilter 边界文档化 + sql_lib 盘查结论落档
- [x] focused verification：nop-orm-eql 全量绿
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [x] **Anti-Hollow Check**：注入与否定真实改变行为（stash 判别 FAIL→PASS）
- [x] `./mvnw test -pl nop-persistence/nop-orm-eql` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Deferred But Adjudicated

### FILTER marker（数据权限）DML 化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 数据权限 marker 依赖 SELECT 渲染埋点与上游 DataAuth 体系；DML 数据权限由 biz 动作鉴权承担（审计风险自评「可达性限于手写 EQL DML」）；本批已对齐缺省过滤三类
- Successor Required: `no`

### UPDATE 触及已删行/恢复类语句的边界

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 注入后可写集合==可读集合（对称正确）；恢复类语句须走 disableLogicalDelete 通道或原生 SQL（owner doc 边界声明）；sql_lib 盘查证实无现存手写 EQL DML 受影响
- Successor Required: `no`

### SqlLikeExpr.ignoreCase 丢弃

- Classification: `optimization candidate`
- Why Not Blocking Closure: 审计现状注记非修复指令；ignoreCase 语义跨方言（lower() 包裹）需独立设计
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2-2.2-006（INSERT 租户列镜像面）：台账通道

## Closure

Status Note: 簇 E 双 P1 修复落地：EQL UPDATE/DELETE 注入逻辑删除+租户+实体 filter 三类缺省过滤（与 SELECT 同一 collectDefaultEntityFilter，disableLogicalDelete 开关保持）；eval 双转换器 not like/not in 否定补全（含空值早退分支）。enableFilter DML 边界+恢复类语句边界 owner doc 声明；sql_lib 盘查落档（现存资源库无手写 EQL DML）。**判别证据可独立复现**：stash 三文件 6/8 FAIL（DML 无租户条件/否定语义反转），对照用例双态绿。nop-orm-eql 205 tests 全绿。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，只读）agent_c549e0a3-f41c-41f3-82c0-81c4de22b723
- Evidence:
  - **P1-2.2-009 注入**：PASS——visitSqlUpdate(:1584-1590)/visitSqlDelete(:1654-1660) 内联 makeWhere+collectDefaultEntityFilter+setFilterAlreadyAdded，与 plan 逐字一致；SELECT(:466/:616/:917) 与 DML(:1590/:1660) 调用同一函数（语义同一性由构造保证）。
  - **P1-2.2-011 否定**：PASS——FilterBean 侧 transformLike(:126-134 含空值分支 alwaysTrue 包裹)/transformInValues(:174-175 notIn)；Expression 侧 :148-149/:236-237 UnaryExpression.not。
  - **开关可见性**：PASS——ISqlCompileContext:16 声明、生产读取点 EqlTransformVisitor:406；测试匿名子类动态分派实证（若覆盖不可见 DELETED 必然在场、断言必失败）。
  - **Anti-Hollow stash 判别独立重做**：PASS——auditor 独立 stash 三主文件（grep 确认注入代码 0 命中）→ 8 用例 6 FAIL，失败输出即 SQL 文本证据（delete/update 无租户与逻辑删除条件、not like→肯定 like、not in→肯定 in、Expression 无否定）；**对照双绿确认**（testPlainModelUnaffected/testInStillPositiveWhenNoNot 双态 PASS）；pop 后 8/8 绿。
  - **全量回归**：PASS——auditor 亲跑模块全量 205/0/0/skip0（197+8）；doc-links --strict 0；hollow scan 0。
  - **状态回填四处一致**：PASS——roadmap R1.6c(:137)/index :100/:102/MR :262/daily log。
  - **Deferred 分类**：PASS——三项（FILTER marker DML 化/恢复类边界/ignoreCase）均 guide 允许类别且附 Why Not Blocking；auditor 独立复扫 _vfs 证实无手写 EQL DML；P2-2.2-006 保持台账通道非降级。
  - Advisory 处置：A1 本段回填（已做）；A2 随本 closure 提交（已做）；A3 strict 勾选时序由本回填补正；A4 Expression 断言较宽但判别有效（stash 态无 ! FAIL、修复态 PASS——留档）。

Follow-up:

- FILTER marker DML 化、恢复类语句边界（Deferred But Adjudicated，owner doc 已声明逃生通道）
- SqlLikeExpr.ignoreCase（optimization candidate）
- P2-2.2-006 INSERT 租户列（P2 台账通道）
