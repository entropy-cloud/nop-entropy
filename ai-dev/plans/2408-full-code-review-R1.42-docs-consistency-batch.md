# 2408 full-code-review R1.42 — docs 一致性修复批（P1-5.1-001/002/003 + P1-5.2-001）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.42；findings `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/5.1-docs-consistency-audit.md`（P1-5.1-001 :96 / P1-5.1-002 :116 / P1-5.1-003 :142）+ `5.2-design-drift-audit.md`（P1-5.2-001 :181）
> Related: R1.10（plan 2375，done——P1-2.6-001 代码修复已落地，**本批文档表述必须对齐修复后 live，而非审计时点的 pre-fix live**）；R1.17（plan 2383，done——nop-sys.md ILockService 已修，不在本批）；P1-5.1-001 与 P1-2.6-001 同批互引
> Owner Doc: `docs-for-ai/INDEX.md` + `ai-dev/design/README.md`（roadmap 指定）+ 各被改文档自身
> Plan Review: 首轮 agent_657e7625 **NOT READY**（2026-10-10，live 亲读+探针）——两大核心立场（R1.10 已承载 001/对齐 post-fix 双矩阵）核实成立、事实基础全准；3 Major（§四 approve 双事务节标注遗漏/grep 无 -rn 会假通过/beans.xml 放置路径约定与 AppBeanContainerLoader 装载规则不符）+ 2 Minor（行号锚漂移 5 处/「五处」计数）已全部修订。第二轮 agent_657e7625 **READY**（2026-10-10，按改动行复核）：5 项修订全落实、无新引入问题；1 Minor（Phase 1 执行项内 3 个旧行号 :69/:71 与 baseline 自相矛盾）已按复核建议顺手修正——头部「已全部修订」声明为真。

## Purpose

收口 5.1/5.2 两个文档审计的四条 P1：事务默认表述族（001 已由 R1.10 代码修复连带承载，本批核验登记）、INDEX/runbook/nop-task/source-anchors 的事务入口双矩阵统一（002）、nop-job.md 幻影注解教程改写（003）、maker-checker/03 草稿态标注（5.2-001）。**纯文档批**：零 Java/生产文件变更，完成后 doc-links `--strict` 退出码 0。

## Current Baseline（2026-10-10 live 亲读）

- **R1.10 改变了事务 live 语义（本批的核心前提）**：commit 91e7ef0a81（plan 2375）①`TransactionActionDecoratorCollector` 无 `<txn>` 时 mutation 默认加事务装饰器；②`biz-defaults.beans.xml:42-48` 注册 `nopTransactionActionDecoratorCollector`（`ioc:default` + `on-bean=nopTransactionTemplate` 守卫——无 nop-dao 的 IoC 环境豁免）；③回归测试 `TestTransactionActionDecorator` 4 用例（mutation 默认/query 无/显式 false/显式 true）。**5.1 审计报告的「三层死配置」事实已不成立**；审计的修复方向（"明确后台直调无事务"）系 pre-fix 口径，本批不照搬。
- **装饰器覆盖面（live 亲读）**：`BizObjectBuildHelper.java:85-131` 在 biz action 构建期用 collector 包装 `IServiceAction`；`BizProxyInvocationHandler.buildActionMap` 经 `bizObj.requireAction(methodName)` 取**已装饰** action——`I*Biz` 代理路径（job/MQ/service bean 注入）的 mutation 同样吃到 action 级事务；`BizActionInvoker.java:34-76` 直调路径自身 `runInSession` + 非 query 显式 `REQUIRED` 事务；GraphQL 侧 `biz-defaults.beans.xml:50-58` 另有 operation 层 `GraphQLTransactionOperationInvoker`（外层）。
- **P1-5.1-001 三处（service-layer.md:66 与 :310〔原 :277，R1.12 后移〕、api-and-graphql.md:184〔原 :174，后移〕）**：R1.10 提交已同步修正（service-layer.md:66 现文含「R1.10 起 mutation 默认事务装饰器对后台直调亦生效」、:310 含「R1.10 起 mutation **默认**包事务」；api-and-graphql.md:184 含「R1.10 起 action 级装饰器默认化」）——**R1.10 已承载，本批仅核验 + 治理登记**（行号以文本定位为准）。
- **P1-5.1-002 扩散面现状**：
  - `docs-for-ai/INDEX.md:38`「普通 `@BizMutation` 已自动进入事务，不要默认叠加 `@Transactional`」——post-R1.10 结论成立但**无条件限定**（未提 query 无事务/`<txn>` 覆盖/无 nop-dao 环境豁免/入口矩阵指引）。
  - `docs-for-ai/03-runbooks/transaction-boundaries.md`（全文 63 行，事务表述延至 :56 常见坑节）——「普通 BizModel 写操作默认只用 `@BizMutation`」等表述 post-fix 大体成立，但**缺入口矩阵**（GraphQL operation 层/action 装饰器/BizActionInvoker 直调/代理路径四入口谁开事务），也无 `@BizMutation` 默认事务的生效条件。
  - `docs-for-ai/03-modules/nop-task.md:102-110/:122/:136`（行号 2026-10-10 live；以文本 grep 定位为准，行号仅参考）——「整条流程跑在该 action 所开启的同一个事务内」「事务由 @BizMutation（或 `<txn>`）开启」：post-fix 成立，但前提句未指明「action 开启事务」的机制来源（action 装饰器/GraphQL 层）。
  - `docs-for-ai/04-reference/source-anchors.md:64` TXN-001 描述「非 query 的 Biz 操作默认会进入事务」——**表述失准**：该锚指向 `BizActionInvoker.java`，其事务包装仅覆盖**经该 helper 的直调**路径（:47-50/:69-72 显式 REQUIRED）；「Biz 操作默认进事务」的一般化表述与 action 装饰器机制（R1.10 起）归属混淆。
- **P1-5.1-003**：`docs-for-ai/03-modules/nop-job.md:246-262`「步骤 1」给 `import io.nop.api.core.annotations.ioc.IocBean` + `@IocBean("nopJobInvoker_myJob")`——该注解全仓不存在（`annotations/ioc` 包仅 BeanMethod/DelayMethod/IgnoreDepends/InjectValue 四类）；真实注册为 beans.xml 显式声明（`nop-job/nop-job-service/src/main/resources/_vfs/nop/job/beans/app-service.beans.xml:10-11` 注册 `nopJobInvoker_test`/`nopJobInvoker_rpc`）。
- **P1-5.2-001**：`ai-dev/design/maker-checker/03-pending-lock-and-concurrency.md` 状态行 `:5`（`active`）+ 全篇现在时描述 guard 装饰点/lockKey 唯一索引/approve 双事务/nop-job TTL 扫描——live 全部不存在（`nop-sys.orm.xml` NopSysCheckerRecord 23 列无锁列；decorator 目录仅 Transaction/Cache 两对；`IMakerCheckerPendingGuard`/`nop.err.sys.makerchecker.pending-lock` 全仓零命中）；目录 README:18 有一句全局澄清但正文无标注。
- **文档工具**：`node ai-dev/tools/check-doc-links.mjs --strict` 当前 0 errors / 6 warnings（warnings 均为 `ai-dev/plans/nop-bytecode`、`nop-code` 历史遗留文件，与本批无关）。

## Goals

- 事务表述全仓统一为「入口双矩阵」口径，对齐 R1.10 修复后 live：①GraphQL mutation operation 层（外层 REQUIRED）；②biz action 装饰器（R1.10 起注册，mutation 默认开、`<txn transactional="...">` 覆盖、on-bean 守卫）；③`BizActionInvoker` 直调（自身显式 REQUIRED）；④`I*Biz` 代理路径经 `requireAction` 吃到②。query 全程无事务。
- nop-job.md 幻影 `@IocBean` 教程改为 beans.xml 注册教程（含放置路径约定 + 「平台无注解扫描」注记）。
- maker-checker/03 全篇加「目标架构（未落地）」显式标注，状态行改 draft/blocked 语义，README 澄清保留。
- P1-5.1-001 三处 R1.10 已承载的核验结论落 plan + 治理登记。
- doc-links `--strict` 保持 0 errors。

## Non-Goals

- 任何 Java/生产文件变更（P1-2.6-001 代码面已由 R1.10 修复，不再动）。
- P2/P3 文档漂移（P2-5.1-001..005、P3-5.1-00x、P2-5.2-002..003、P3-5.2-001..003——另属 P2 裁决台账/watch）。
- maker-checker/01（P2-5.2-002 承载）与 02 的标注（本批仅 03 + README 互引核对）。
- 重写 maker-checker/03 的设计内容本身（只加状态标注与结论定位，不改设计结论）。

## Scope

### In Scope

- `docs-for-ai/INDEX.md`（:38 默认规则条目）
- `docs-for-ai/03-runbooks/transaction-boundaries.md`（入口矩阵节）
- `docs-for-ai/03-modules/nop-task.md`（事务模型节 3 处前提句）
- `docs-for-ai/04-reference/source-anchors.md`（TXN-001 描述行）
- `docs-for-ai/02-core-guides/service-layer.md` + `docs-for-ai/02-core-guides/api-and-graphql.md`（核验，仅在发现残留漂移时修正）
- `docs-for-ai/03-modules/nop-job.md`（:246-262 幻影教程改写）
- `ai-dev/design/maker-checker/03-pending-lock-and-concurrency.md`（状态行+分节标注）+ `ai-dev/design/maker-checker/README.md`（互引核对）
- 治理文件：roadmap R1.42、index（P1-5.1-001/002/003、P1-5.2-001 行）、daily log、本 plan

### Out Of Scope

- 全部代码文件；P2/P3 findings；`docs/` 历史遗留目录

## Execution Plan

### Phase 1 - 事务入口双矩阵统一（P1-5.1-002 + P1-5.1-001 核验）

Status: completed
Targets: `INDEX.md`、`transaction-boundaries.md`、`nop-task.md`、`source-anchors.md`、`service-layer.md`、`api-and-graphql.md`

- Item Types: `Fix | Proof`

- [x] `transaction-boundaries.md` 增「事务入口矩阵」节：四入口（GraphQL mutation operation 层外层事务 / biz action 装饰器〔R1.10 起，mutation 默认 REQUIRED、xbiz `<txn>` 显式覆盖、`on-bean=nopTransactionTemplate` 守卫——无 nop-dao IoC 环境豁免〕/ `BizActionInvoker` 直调显式 REQUIRED / `I*Biz` 代理路径经 `requireAction` 吃到装饰器）× 事务有无；query 全程无事务；`afterCommit` 前提保持；代码锚（`biz-defaults.beans.xml:42-58`、`BizActionInvoker.java:34-76`、`BizProxyInvocationHandler`→`requireAction`、`BizObjectBuildHelper.java:85-131`）
- [x] `INDEX.md:38` 条目加边界限定（mutation 默认经 action 装饰器进事务〔R1.10 起〕；query 无事务；特殊部署形态见 `03-runbooks/transaction-boundaries.md` 入口矩阵），保持「不默认叠加 @Transactional」结论
- [x] `nop-task.md` 事务模型节三处前提句对齐（「action 所开启的事务」注明机制来源=R1.10 起 action 装饰器 mutation 默认；用法一 :122 `<txn>`（或 `@BizMutation` 默认）表述核对；用法二 :136 注释核对）
- [x] `source-anchors.md` TXN-001 描述改写：「`BizActionInvoker` 直调路径对非 query 操作显式开启 REQUIRED 事务（`runInSession` + `runInTransaction`）；一般 biz action 的事务边界见 `transaction-boundaries.md` 入口矩阵（action 装饰器/GraphQL operation 层）」——锚描述与锚文件实际职责一致
- [x] 核验 P1-5.1-001 三处（service-layer.md「R1.10 起」两处 + api-and-graphql.md「R1.10 起」一处，以文本 grep「R1.10 起」定位）现文与 live 一致；如有残留漂移一并修正，无残留则在 plan 记录「R1.10 已承载零残留」（含复核探针：dao 层 `@Transactional` pointcut 消费面 `dao-defaults.beans.xml:95-98`）
- [x] 本 Phase owner-doc 处置：改动即 owner doc 本体，`No additional owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 4 统一补记）

Exit Criteria:

- [x] 入口矩阵在 `transaction-boundaries.md` 在案且四入口×事务有无与 live 代码锚一致
- [x] 六处表述（INDEX/transaction-boundaries/nop-task×3/source-anchors TXN-001，共 4 文件）无「无条件默认事务」或「pre-fix 无事务」失准口径残留；`grep -rn "默认会进入事务\|已自动进入事务" docs-for-ai` 逐处核对通过（**零残留=退出码 1**——grep 无命中返回 1，证据记录须写明）
- [x] P1-5.1-001 三处核验结论（含证据行号）记入本 plan

### Phase 2 - nop-job.md 幻影注解教程改写（P1-5.1-003）

Status: completed
Targets: `nop-job.md:246-268`

- Item Types: `Fix`

- [x] 「步骤 1：实现并注册 IJobInvoker」改写：Java 类实现 `IJobInvoker`（去掉 `@IocBean` import 与注解），注册改为 beans.xml 显式声明示例（`<bean id="nopJobInvoker_myJob" class="...MyJobInvoker"/>`）+ 放置路径约定：**模块 `_vfs/{moduleId}/beans/app-*.beans.xml`（命名必须 `app.beans.xml` 或 `app-` 前缀，任意命名不会被自动装载——`AppBeanContainerLoader` 装载规则）或应用级 `app.beans.xml`；模块级 `app-*.beans.xml` 被发现的前提是模块提供 `_vfs/{moduleId}/_module` 标记（见 `02-core-guides/ioc-and-config.md` 装载规则）**；内置先例 `nop-job-service` `app-service.beans.xml:10-11`
- [x] 「bean 命名约定」注补一句：平台无类路径注解扫描，一切 bean 经 beans.xml 注册（与 `02-core-guides/ioc-and-config.md`、AGENTS.md 口径一致）；内置先例 `nop-job-service` `app-service.beans.xml:10-11`
- [x] 本 Phase owner-doc 处置：改动即 owner doc 本体
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 4 统一补记）

Exit Criteria:

- [x] nop-job.md 全文 `grep -n "IocBean"` 零命中（幻影注解清除）
- [x] 新教程含 beans.xml 示例 + 命名约定 + 无扫描注记，与真实注册文件先例一致

### Phase 3 - maker-checker/03 草稿态标注（P1-5.2-001）

Status: completed
Targets: `ai-dev/design/maker-checker/03-pending-lock-and-concurrency.md`、`README.md`

- Item Types: `Fix | Decision`

- [x] 状态行 `:5` `active` → `draft（目标架构，未落地）`；头部加显式声明段：本文描述的 guard 装饰点/lockKey 唯一索引/approve 双事务/nop-job TTL 扫描均为**未实现的目标架构**，live 现状=maker 侧骨架（`io.nop.biz.makerchecker`、GraphQL tryAction、`nop_sys_checker_record` 表，无锁列无 guard 无扫描）
- [x] 全部机制节节首加「〔目标架构·未落地〕」标注：§一 设计结论、§二 Guard 契约与拦截位置、§三 关键并发场景、**§四 approve 执行语义（事务边界，tx1/tx2 双事务伪代码——审计点名的四大虚构机制之一，最具实现外观）**、§五 生命周期（withdraw/过期/EXECUTE_FAILED 双出口）、§六 可观测性；保持设计内容零删改
- [x] `README.md` 职责边界段核对互引（现有全局澄清保留）；`ai-dev/design/README.md` 若有 maker-checker 状态索引则同步，无则 `No update required` 记录裁定
- [x] 本 Phase owner-doc 处置：design 文档即本体（按 guide Rule 14，最终态重写留待实际实现时，本批只做草稿态标注）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 4 统一补记）

Exit Criteria:

- [x] 03 状态行不再是裸 `active`；头部声明 + §一~§六 全部机制节标注在案
- [x] README 澄清与正文标注口径一致（均为「目标架构未落地」）
- [x] 设计结论内容零删改（diff 仅状态行/声明/节标注）

### Phase 4 - 治理收口

Status: completed
Targets: 治理文件

- Item Types: `Proof`

- [x] roadmap R1.42 翻 done（含 001 由 R1.10 承载的裁决注记）；index 四条 finding 行翻 fixed（P1-5.1-001 注 R1.10 承载+本批核验）；daily log 条目
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（`plan-closure-audit-prompt.md`），evidence 写入本 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

Exit Criteria:

- [x] roadmap/index/log 三方一致
- [x] doc-links 退出码 0
- [x] 独立 closure audit 落盘

## Closure Gates

> 纯文档计划：按 plan guide 模板说明，构建/测试验证条目不适用，已删除。

- [x] 四条 in-scope P1（5.1-001/002/003 + 5.2-001）全部闭合或核验登记（001=R1.10 承载+本批核验）
- [x] 事务表述全仓与 R1.10 修复后 live 一致（无 pre-fix 口径残留）
- [x] 幻影注解教程清除，beans.xml 教程与真实先例一致
- [x] maker-checker/03 草稿态标注完成且设计内容零删改
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 缺口
- [x] 受影响 owner docs 已同步（改动即本体）或显式 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无——in-scope 四条全部本批闭合；P2/P3 本就不在本批 scope，非降级。）

## Non-Blocking Follow-ups

- P2-5.1-001..005 / P3-5.1-00x（5.1 报告 P2/P3 档，P2 裁决台账/watch 通道）
- P2-5.2-002/003、P3-5.2-001..003（5.2 报告 P2/P3 档；maker-checker/01 标注若 P2 裁决「修」则随 successor）

## Closure

Status Note: 四条 in-scope P1 全部闭合/核验登记：P1-5.1-001 三处经核验 R1.10 已承载零残留（:66/:310/:184 + dao 层 pointcut 探针）；P1-5.1-002 六处统一为 R1.10 修复后事务入口矩阵口径（runbook 新增矩阵节 + INDEX/nop-task×3/TXN-001 对齐，pre-fix 口径 grep 零命中）；P1-5.1-003 幻影注解清零（beans.xml 教程 + 装载规则 + 真实先例）；P1-5.2-001 草稿态标注（设计内容零删改，+10/-1）。纯文档批零代码文件变更。独立 closure audit 判定 Can Close。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_cf42ea02（独立 fresh-session closure auditor，2026-10-10）
- Evidence:
  - Phase 1 PASS：入口矩阵四入口逐锚核对 live（biz-defaults.beans.xml:42-48/:50-58、TransactionActionDecoratorCollector.java:45-51、BizActionInvoker.java:34-76、BizProxyInvocationHandler.java:86 requireAction）；`grep -rn` pre-fix 口径零命中（EXIT=1）；P1-5.1-001 三处 `grep "R1.10 起"` 精确命中 :66/:310/:184 + dao-defaults.beans.xml:95-98 探针属实
  - Phase 2 PASS：`grep IocBean` nop-job.md 零命中；教程与 AppBeanContainerLoader.java:281-284 / ioc-and-config.md:156-158 / app-service.beans.xml:10-11 先例逐点一致
  - Phase 3 PASS：状态行 draft + 头部声明 + §一~§六标注在案；diff 恰 +10/-1 零删改；头部「live 现状」四声明抽查属实（IMakerCheckerPendingGuard 零命中/decorator 两对/ORM 无锁列）；design README:44 已同步
  - Phase 4 PASS：roadmap/index（4 finding fixed + MR 行）/log 三方一致；doc-links --strict 重放 0 errors（6 无关 warnings）
  - Deferred 分类检查：Deferred 段为空，follow-ups 仅 P2/P3（本就 out-of-scope，非降级）；P1-5.2-001「标注不删改」系有据显式裁定（guide Rule 14），非静默降级
  - 变更清单核对：10 M + 1 新增 plan，零代码文件，与 Scope In-Scope 吻合
  - 关单前置动作已按 audit 处方完成：40 项 checklist 勾选、本 Evidence 落盘、Phase 4 翻 completed、Plan Status 翻 completed；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 关单前复跑退出码 0

Follow-up:

- P2-5.1-001..005 / P3-5.1-00x / P2-5.2-002/003 / P3-5.2-001..003（P2 裁决台账/watch 通道，非本批 scope）
