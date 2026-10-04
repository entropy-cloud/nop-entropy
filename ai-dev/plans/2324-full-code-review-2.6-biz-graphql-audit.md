# 2324 full-code-review 2.6 nop-biz + nop-graphql 深审

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.6（MA2）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 03/07/12/13 + 共享前缀）
> Related: plan 2314（1.7 已审 service-framework 分层边界）；owner doc `docs-for-ai/02-core-guides/service-layer.md`；2.1/2.2（nop-orm/orm-eql 已审——CrudBizModel 的 ORM/EQL 消费面撞车对照）
> Skill 适配注记：维度 03 → BizModel 公开 API 面与 xmeta/xbiz 契约；维度 07 → CrudBizModel 规范遵循与上帝类拆分评估；维度 12 → GraphQL 引擎（selection/分页/订阅/WebSocket）；维度 13 → 权限模型（GraphQLActionAuthChecker fail-open 三处 fail-fast 评估——roadmap 点名）。check2 点名复核：nop-biz P1×3（:19 树查询未过滤逻辑删除、:45 DevStatBizModel 无 @Auth、:76 cache-evicts 未消费——roadmap「fail-open 三处」对应域）+ nop-graphql P1×2（:22 maker-checker tryAction 首个 operation、:50 hasNextPage 恒 true）。check2/gateway-bizauth.md（18 条）：13 条归 3.9（nop-gateway/network）；nop-biz-auth-core 3 条+ nop-biz-file-core 3 条暂无 Wave 1 owner——登记 roadmap 归属缺口，留 R1.0 对账。不在本项。余量 P2/P3 留 R1.0 或 watch-only。
> 只读审计（P0 走异步注入）；check2/nop-biz.md 与 check2/nop-graphql.md 为沿 2.1–2.4 先例自加的例外输入（roadmap Details 未直接点名 check2，「fail-open 三处」指 GraphQLActionAuthChecker 域——已定位 :38-39/:119-120/:122-124 三处）。
> Draft Review: agent_05c76ed1 首轮 1 Major（GraphQLASTOptimizer 实为 XGEN 生成死代码对 795+219——1.7 P2-1.7-003 已裁定，基线断言纠正+dedup 登记）+ 3 Minor（check2 计数 ×6→×5 与 P2 复核集合统一、gateway-bizauth 归属 13/18+缺口登记、「roadmap 点名」改沿先例自加）全修。审查实测佐证：fail-open 三处定位成立、CrudBizModel 2283 为准、check2 五条修复疑似已合入。

## Purpose

执行 roadmap 工作项 2.6：深审 nop-biz（CrudBizModel 基础设施）+ nop-graphql（GraphQL 引擎），产出报告 `2.6-biz-graphql-deep-audit.md`；复核 check2 点名 P1×5 的 live 状态。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-service-framework/nop-biz`：102 main/25 test。核心：CrudBizModel **2283 行**（roadmap Details 的 2272 为陈旧值，以 wc -l 为准；上帝类拆分评估为 roadmap 点名）、OrmEntityCopier 654；包 biz/crud、biz/model（_gen 豁免）。
- `nop-service-framework/nop-graphql`：239 main/43 test（含 nop-graphql-core）。核心：GraphQLEngine 887、GraphQLDocumentParser 821、GraphQLASTOptimizer 795（**XGEN 生成物**——与 GraphQLASTProcessor 219 构成生成死代码对，1.7 P2-1.7-003 已裁定，本项仅标定 live 状态+登记 dedup 归 P2-1.7-003，不作结构审）、GraphQLExecutor 608、ReflectionBizModelBuilder 536、GraphQLSelectionResolver 528。
- check2 点名输入：`check2/nop-biz.md` 13 条（P1×3 :19/:45/:76 + P2/P3 余量）；`check2/nop-graphql.md` 15 条（P1×2 :22/:50 + P2×5（:73/:98/:124/:151/:176——五条全部纳入复核集合）+ P3×8）。全部带 fix-ai-check 处置块且修复疑似已合入当前分支（2.1–2.4 先例）——以 live code 为准逐条标定。
- roadmap Details：CrudBizModel 拆分评估（供 R1.0 参考，不强制拆分）；全局查询变压器单点依赖；GraphQLActionAuthChecker 等 fail-open 三处 fail-fast 方案评估。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；1.7（plan 2314）已证 service-framework 分层合规正结论。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.6-biz-graphql-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.6-<序号>`）+ 驳回台账（正文）+ 裁决。
- CrudBizModel 面（维度 07）：2283 行上帝类解剖（职责清单/拆分评估——供 R1.0 参考）；标准 CRUD 动作语义（findPage/findList/get/updateBatch 等）正确性；全局查询变压器单点依赖；tree 查询（check2 :19 复核——逻辑删除过滤）；OrmEntityCopier 拷贝语义。
- GraphQL 引擎面（维度 12）：GraphQLEngine/GraphQLExecutor 执行链（selection 解析/分页/批量 loader）；订阅（check2 :124 context.complete 域）；WebSocket（check2 :73 身份绑定域）；HTTP 路径（check2 :22/:98 复核）；hasNextPage 分页（check2 :50 复核）；ASTOptimizer live 状态标定（XGEN 生成死代码对 795+219，1.7 P2-1.7-003 已裁定——验证仍随构建回写+零引用，dedup 登记归 P2-1.7-003）。
- 权限面（维度 13）：GraphQLActionAuthChecker fail-open 三处（:38-39 checker==null 跳过/:119-120 auth==null→true/:122-124 checker==null→true）逐一确认语义+fail-fast 方案评估（roadmap 点名）；DevStatBizModel @Auth（check2 :45 复核）；MakerChecker 决策语义（check2 :22 关联）；字段级权限（xmeta readable/updatable 消费链）。
- API 面（维度 03）：BizModel 公开方法↔xmeta/xbiz 契约一致性抽查；cache-evicts（check2 :76 复核——解析未消费）。
- index 登记 + 独立 closure audit + roadmap 2.6 翻 done。

## Non-Goals

- nop-auth/nop-gateway 域（3.9/3.10——gateway-bizauth.md 18 条不在本项）；nop-orm/orm-eql 引擎内部（2.1/2.2 已审）。
- check2 余量 P2×6+P3 的逐条复核（独立撞见按新发现处理并注明重复关系）。
- CrudBizModel 实际拆分实施（评估供 R1.0 参考）。
- GraphQL 规范层（graphql-java 库内部）。

## Scope

### In Scope

- `nop-service-framework/nop-biz/**`
- `nop-service-framework/nop-graphql/**`
- 只读输入：`ai-dev/audits/check2/nop-biz.md`（P1×3 点名 + 标题撞车对照）、`ai-dev/audits/check2/nop-graphql.md`（P1×2+P2×5 点名 + 标题撞车对照）、`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.7-persistence-servicefw-boundary-audit.md`（P2-1.7-003 dedup 对照）

### Out Of Scope

- nop-service-framework 其余模块（1.7 已审结构）；nop-gateway/nop-auth（3.9/3.10）；graphql-java 内部。

## Execution Plan

### Phase 1 - 深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: nop-biz + nop-graphql

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：两模块 main/test 行数榜（_gen 豁免清单）、CrudBizModel 公开方法清单、GraphQL 引擎类图、@Auth 注解分布、check2 五条点名的处置块状态
- [ ] CrudBizModel 子 agent（维度 07）：2283 行解剖+拆分评估；CRUD 动作语义；tree 查询（**check2 :19 复核**——逻辑删除过滤 live 标定）；全局查询变压器；OrmEntityCopier
- [ ] GraphQL 引擎子 agent（维度 12）：执行链/分页/订阅/WebSocket（**check2 :22/:50/:73/:98/:124 复核**——五条 live 标定）；ASTOptimizer 非生成物核实+结构审
- [ ] 权限与 API 子 agent（维度 13+03）：GraphQLActionAuthChecker fail-open 三处定位+fail-fast 评估；DevStatBizModel（**check2 :45 复核**）；cache-evicts（**check2 :76 复核**）；xmeta/xbiz 契约抽查
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-2.6-{crud,graphql,auth}.md`

Exit Criteria:

- [ ] 三份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] check2 点名条目（P1×5）逐条标定结论落盘（still-live/already-fixed/false-positive/duplicated + 实测证据）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 2.1/2.2（ORM/EQL 消费面）/3.9/3.10（auth 域）边界协调；check2 重复发现归属
- [ ] CrudBizModel 拆分评估收敛（供 R1.0 的一条总评）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-2.6-{crud,graphql,auth}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `2.6-biz-graphql-deep-audit.md`；index 登记 2.6 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-service-framework/nop-biz,nop-service-framework/nop-graphql -am`（无条件执行；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 2.6 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因为预存失败并在报告/log 登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且三面全覆盖（crud/graphql/auth-api）
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 2.6 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。check2 余量 P2/P3 留 R1.0 裁决通道或 watch-only 归档处理；CrudBizModel 拆分实施归 R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
