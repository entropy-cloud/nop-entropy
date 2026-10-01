# 03 WI0c D7/D8/D13 三条编译契约裁定——schema 来源、接口面、宿主模块

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/nop-stream-sql-roadmap.md（WI0c 行、前置裁定表 D7/D8/D13 行、§3.5、Cross-Cuting 4、Framework Reuse 表）
> Related: ai-dev/plans/nop-stream-sql/01-wi0a-d2-vision-conflict-resolution.md、ai-dev/design/nop-stream/sql-vision-conflict-resolution.md
> Owner: 仓库 owner（2026-10-02 执行指令委托，授权原文见下）

## Purpose

把三条编译契约裁定落档到 ai-dev/design/nop-stream/sql-compiler-contract.md（未来交付物）：D7 表列绑定来源与解析入口（含 SQL 类型→BasicTypeInfo 映射表与时间列绑定立场）、D8 用户可见接口面、D13 编译器宿主模块与依赖方向。三条落档含负责人与日期，且不重复 D14 已作出的裁定。WI8a/WI8b/WI16/WI17 依赖本计划结论；WI8b 只等本计划的 D7 类型映射即可开工。

## Owner 授权证据（原文引用）

> 用户指令（2026-10-02，会话 /goal）：「执行 nop-stream-sql-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」

D7 的 schema 来源与解析入口在 roadmap 中无「建议」标注（仅约束选项集与二选一），D8/D13 有明确建议项。本计划据此授权链作出两类选择并显式记录：(a) D8/D13 采纳 roadmap 建议项；(b) D7 两项结论是基于上述委托作出的 **owner 级选择**（非建议项转抄），独立 closure audit 须显式核验此授权解释与技术理由的一致性。

## Current Baseline

- D7 待裁。roadmap 约束：选项集必须含「SQL 面自带 schema」（与 D8 的 `<sql>` 元素配对）；xdef `schemas` 声明面当前 build 期 fail-fast（`StreamModelDslBuilder.java:266` hasSchemas、`:271` hasCoders，2026-10-02 实测）；流类型面仅 `BasicTypeInfo`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeinfo/BasicTypeInfo.java:20-28`，内置 STRING/INT/LONG/DOUBLE/BOOLEAN/BYTE/SHORT/FLOAT/BYTE_ARRAY 九实例 + `of(Class)`；2026-10-02 实读确认），**无 SQL 类型→流类型映射**，该映射归本裁定。解析入口二选一：`EqlASTParser`（完整 SqlProgram，实测存在）与 `EqlExprASTParser`（单表达式）；ORM 外复用先例 `SqlSourceEntityExtractor.java:40`。
- D8 待裁，建议新增 `<sql>` xdef 元素；不得预设编译落点（编译落点已由 D14=(a) 裁定、WI8a 负责落档，本裁定不另设落点）。
- D13 待裁，建议 (a) 新模块 `nop-stream-sql` 同时依赖 `nop-stream-flow` 与 `nop-orm-eql`；理由：nop-stream/nop-stream-flow/pom.xml 零 nop-orm-eql 依赖（codegen/ioc 为 test scope，不影响承重结论，2026-10-02 实测）；`nop-orm-eql` 依赖 nop-dao + nop-orm-model + nop-core + nop-codegen + nop-antlr4-common，(b) 会把持久层拖进引擎；(c) 违反单一口径。
- **D13 机制风险事实（2026-10-02 实测，审查确认）**：stream 模型类型化 Java 类（`_StreamAggregateModel` 等 30 个）由 `nop-stream/nop-stream-flow/precompile/gen-stream-xdsl.xgen` 在 **nop-stream-flow 构建期**从 stream.xdef 生成；stream.xdef 全仓仅存于 `nop-kernel/nop-xdefs`；跨模块 delta 扩展 xdef 仅 test/demo 先例（`nop-auth-web` test delta `xview.xdef`、`nop-kernel-cli/demo` delta `orm.xdef` 用 `x:extends="super"`），无生产模块先例。依赖方向 sql→flow，flow 构建看不到下游新模块内的 delta xdef——**「声明面落新模块 schema」与「类型化 builder 消费留 nop-stream-flow」存在结构性张力**。
- Cross-Cuting 4 预写的新模块分支：若 D13 裁定新模块，WI8b/WI8c/WI8d 三处 xdef 声明面变更落在新模块 schema 而非 `nop-kernel/nop-xdefs`。
- D14 已裁定 (a) 参数化算子面（产物形态），与本计划正交：D13 定放哪个模块，D7/D8 定输入与入口。

## Goals

- 新建 ai-dev/design/nop-stream/sql-compiler-contract.md，三条裁定各含**全量选项集（D7 五选项、D8 五选项、D13 三选项，逐一列明并各给拒绝理由）**、结论、理由（代码证据）、负责人、日期 2026-10-02、受影响 WI。
- D7 裁定：编译期列绑定的 schema 来源（SQL 面自带 schema 为主）、解析入口（`EqlASTParser`）、SQL 类型→BasicTypeInfo 映射表（九种受管类型名，集合外编译期报错）、**时间列绑定显式立场**（受管集合不含 timestamp/date；事件时间不经列类型绑定获得，`TUMBLE(t, INTERVAL)` 的 t 列 v1 用 bigint epoch-millis 受管类型承载；DATE/TIMESTAMP/DECIMAL 列绑定进不支持清单，WI16 定稿复核，WI17 实现前不得擅自放宽）。
- D8 裁定：`<sql>` xdef 元素为用户可见入口（SQL 文本内嵌或 .sql 文件引用 + schema 声明面），编译落点已由 D14=(a) 裁定（WI8a 落档）不另设落点；排除 .sql 文件加 bean / Java API / CLI / GraphQL。
- D13 裁定 (a)：新模块 `nop-stream/nop-stream-sql`，pom 依赖 `nop-stream-flow` + `nop-orm-eql`。**两个后果按「方向 + 机制风险」记录**：(i) 模块骨架（pom + `_vfs` schema 资源目录）创建义务归 WI8b（其 plan 范围须显式增补该项）；(ii) WI8b/c/d 的 xdef 声明面落新模块 schema（Cross-Cuting 4 新模块分支）——但 delta 扩展 stream.xdef 的具体机制（delta 路径、flow 侧 `_gen` 类型化模型类生成落点、builder 消费方式）**归 WI8b 的 plan 实测裁定，若证实不可行（如必须改走直改 nop-xdefs 通路或通用 XNode 扩展通路）须回改本裁定与 roadmap 相应行**；codegen 绑定证据（gen-stream-xdsl.xgen 属 flow 构建期）写入落档。
- roadmap 更新：WI0c 状态行 → done（括注补承载 plan 路径与独立 closure audit PASS 日期，单层非嵌套、内部无右括号）；前置裁定表 D7/D8/D13 三行同步「已裁定 owner 2026-10-02」，D13 行结论短句内写明「WI8b/c/d 声明面落新模块 schema（机制归 WI8b plan 裁定）」。

## Non-Goals

- 不落档 D14（WI8a 承接）；不裁 D9-D12（WI0d）。
- 不创建模块、不改 pom、不改任何 xdef（实施归 WI8b 与 WI17）。
- roadmap 改动限于：WI0c 状态行、前置裁定表 D7/D8/D13 三行。
- 不裁 WI16 的最终不支持清单（D7 只给类型面排除项与时间列立场，清单定稿归 WI16）。

## Scope

### In Scope

- 新建 ai-dev/design/nop-stream/sql-compiler-contract.md。
- roadmap：WI0c 状态行、前置裁定表 D7/D8/D13 三行。
- 当日 ai-dev/logs/2026/10-02.md 更新。

### Out Of Scope

- 任何产品代码 / pom / xdef / dialect 变更。
- `sql-compiler-contract.md` 之外的设计文档。

## Execution Plan

### Phase 1 - 三条裁定落档

Status: completed
Targets: ai-dev/design/nop-stream/sql-compiler-contract.md（未来交付物）

- Item Types: `Decision`

- [x] D7 落档：五选项逐一列明并给拒绝理由——ORM 实体元数据（流源非 ORM 实体，拒绝为主来源）、连接器 schema（引擎无连接器 schema 面，拒绝）、.sql DDL（引入 DDL 解析负担，拒绝）、xdef `schemas` 声明面（WI8b 落地为模型级 schema，可作为补充来源，不作编译期主来源——双来源优先级细节归 WI8b/WI17 plan）、SQL 面自带 schema（**胜出**，与 D8 `<sql>` 元素配对）；解析入口 = `EqlASTParser`（完整语句，`EqlExprASTParser` 仅表达式级）；映射表九种受管类型名一一对应 BasicTypeInfo 九实例；时间列绑定立场（见 Goals）
- [x] D8 落档：五选项逐一列明并给拒绝理由——新增 `<sql>` xdef 元素（**胜出**，roadmap 建议）、.sql 文件加 bean（bean 面无消费者且平台无注解扫描，拒绝）、Java API（编程入口已有 DataStream API，SQL 用户面归 xdef，拒绝为首版范围）、CLI（无场景，拒绝）、GraphQL（与主 API 职能重叠，拒绝）；编译落点已由 D14=(a) 裁定（WI8a 落档）不另设落点
- [x] D13 落档：三选项逐一列明并给拒绝理由（(a) 胜出含 pom 证据；(b) 持久层拖进引擎；(c) 违反单一口径）；两个后果按「方向 + 机制风险」记录，含 gen-stream-xdsl.xgen 构建期绑定证据与回改条款（机制不可行须回改本裁定与 roadmap 行）；WI8b plan 范围增补义务（模块骨架创建）显式记录
- [x] 三条裁定的「不重复 D14」声明：各自显式注明与 D14=(a) 的边界

Exit Criteria:

- [x] 三条裁定各有**全量选项集**（D7 五项、D8 五项、D13 三项，逐一含拒绝理由）、结论、理由与代码证据、负责人、日期 2026-10-02、受影响 WI
- [x] 映射表九种类型与 BasicTypeInfo 实测字段一一对应；时间列绑定立场显式落档
- [x] No new test required: 纯文档变更，无产品代码
- [x] ai-dev/logs/2026/10-02.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-roadmap.md`

- Item Types: `Proof`

- [x] 前置裁定表 D7/D8/D13 三行同步「**已裁定 owner 2026-10-02**」+ 结论短句 + 落档位置；D13 行短句含新模块分支与「delta 扩展机制归 WI8b plan 实测裁定并附回改条款」（audit Minor-3：与字面表述语义等价）
- [x] 独立子 agent closure audit（不同 task_id）：核验三条裁定与 roadmap 行、§3.5 约束、WI8b/WI17 输入一致性 + **owner 授权解释一致性（D7 为 owner 级选择的授权链核验）**；证据落 ai-dev/audits/nop-stream-sql/wi0c-closure-audit.md 与本 plan Closure 段（裁定 PASS，2026-10-02）
- [x] audit 通过后 roadmap WI0c 状态行 `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符）；解析器核对 31 + 7：`node -e "import('./tools/mission-driver/src/roadmap-check.mjs').then(m=>{const r=m.parseRoadmapMarkdown(require('fs').readFileSync('ai-dev/backlog/nop-stream-sql-roadmap.md','utf8'));console.log('items',r.phases.filter(i=>!i.isMilestone).length,'milestones',r.phases.filter(i=>i.isMilestone).length)})"`（翻转后实测 items 31 milestones 7）
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0

Exit Criteria:

- [x] roadmap 三行 + WI0c 状态行同步，解析器命令实测输出 items 31 milestones 7
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql/wi0c-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0
- [x] ai-dev/logs/2026/10-02.md 收口记录三处一致

## Closure Gates

> 纯文档计划：无产品代码变更，`./mvnw compile/test`、hollow-scan 按 guide 豁免条款删除。

- [x] 三条裁定全部落档且要素齐全（全量选项集/结论/理由证据/负责人/日期/受影响 WI），不重复 D14
- [x] 映射表与 BasicTypeInfo live 代码一致；D13 结论与 Cross-Cuting 4 新模块分支一致且机制风险与回改条款落档
- [x] owner 授权证据原文已引用，D7 的 owner 级选择有技术理由并经独立 audit 核验
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] Anti-Hollow Check（文档版）：裁定与 live 代码证据一致（audit 实测抽查 EqlASTParser :11、StreamModelDslBuilder :266/:271、pom 依赖、gen-stream-xdsl.xgen、30 个 _gen 类）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/03-wi0c-compiler-contract-decisions.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 「SQL 面自带 schema 与 xdef `schemas` 声明面双来源并存时的优先级」与「delta 扩展 stream.xdef 的具体机制」均归 WI8b/WI17 的 plan 裁定（后者的回改条款已写入 D13 落档义务），不影响本计划结论方向。

## Closure

Status Note: D7/D8/D13 三条编译契约裁定全部落档 ai-dev/design/nop-stream/sql-compiler-contract.md（全量选项集 + 拒绝理由 + 时间列立场 + D13 机制风险回改条款），roadmap 三行 + WI0c 状态行同步完成，解析器 31+7 复核通过。纯文档计划，无产品代码变更。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi0c-closure-audit.md
- Evidence:
  - Phase 1/2 Exit Criteria 逐条 PASS（audit 实测：全量选项集、代码证据 8 项抽查全吻合、roadmap 3 行一致、未越界）
  - 授权链 PASS（授权原文三处一致；D7 owner 级选择叙事完整）
  - mission-driver 解析器翻转后实测 items 31 milestones 7（状态行括注无任何圆括号字符，未触发静默丢弃）
  - check-doc-links --strict 退出码 0；check-plan-checklist --strict 退出码 0
  - audit 3 Minor 已处置（记账补勾、反引号去除、措辞差异记录备查）

Follow-up:

- 见 Non-Blocking Follow-ups（双来源优先级与 delta 机制归 WI8b/WI17 plan）；无 plan-owned 剩余工作
