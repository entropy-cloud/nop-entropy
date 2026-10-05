# 2334 full-code-review 3.7 nop-rule + nop-dyn 深审

> Plan Status: active
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.7（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 10/13 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-rule.md` + `docs-for-ai/03-modules/nop-dyn.md`；check2/nop-rule.md 15 条（P0×1 :19 DecoratedExecutableRule afterExecute 不执行 + P1×3 :48/:68/:89 + P2×3 :120/:145/:168 + P3×8 :186/:204/:222/:239/:259/:276/:293/:310）；check2/nop-dyn.md 16 条（P1×4 :19/:38/:64/:87 + P2×7 :110/:131/:156/:180/:207/:235/:261 + P3×5 :282/:301/:319/:340/:357）。check/nop-rule.md 8 条（P1×1 :19 ≡check2 :19、P2×4、P3×3）+ check/nop-dyn.md 15 条（P1×3 :19/:45/:72、P2×9 :96/:121/:139/:160/:184/:208/:231/:257/:280、P3×3 :307/:329/:346；**含 :208 动态表名/列名/defaultValue 直通 DDL 链——Goals 点名信任边界域，撞车标定不得漏**）与 check2 大面积同源——标定时以 check2 条目为主键、check 条目撞车合并；check2 处置块状态以 live 文件盘点为准。
> **DYN-01 复核输入**：`ai-dev/audits/security-audit/2026-09-19-api-audit-DYN-01.md`（三处 `runWithoutTenantId` standing re-verification：DynCodeGen.java:83 bootstrap probe / DynOrmModelProvider.java:64 重入守卫 / :70 基座层加载——本项逐点现状标定 + 全仓 grep 无新逃逸点）。
> 只读审计（P0 走异步注入）；check2/check/nop-rule.md、check2/check/nop-dyn.md、check/SUMMARY.md、DYN-01 文档为点名例外输入。
> **标定口径**：already-fixed 判定须定位 live 修复点（file:line）并抽样复跑对应回归测试；pre-existing「暂缓/需 owner 裁定」处置须显式承接（维持暂缓/重新裁定/转 R1.0，不得悬空）；标定结论与处置块声明冲突时升级 P0 即时通道评估。
> Draft Review: agent_5d19041f 首轮 3 Major（F1 check/nop-dyn 构成 14→15 条〔P2×9/P3×3〕三处勘正——漏配额风险恰在 :208 DDL 注入面；F2 rule.xdef/nop-dyn-meta xmeta 与 In-Scope/Non-Goals 字面矛盾——补只读契约基准例外；F3 手写 vs 全量基线口径注记）+ 7 Minor（F4 15+17→15+16、F5 处置块盘点扩 check、F6 :120/:168 归类与 Goals 补名、F7 :204/:310 暂缓承接归属、F8 验证命令扩全 in-scope 模块+reactor 注记、F9 api 抽查最小口径、F10 Phase 2/3 owner-doc 裁定+scan-hollow 豁免）——本轮全部修复；agent_fde0736f 聚焦复核 9/10 PASS、1 PARTIAL（F8 漏 nop-dyn-api，翻转前已补）——无 Blocker，进入执行。

## Purpose

执行 roadmap 工作项 3.7：深审 nop-rule + nop-dyn（规则引擎执行语义与 Excel 规则表解析、DynCodeGen 动态建模信任边界、DYN-01 复核），产出报告 `3.7-rule-dyn-deep-audit.md`。

## Current Baseline

- nop-rule 组 main Java：core 41/service 8/dao 17/api 21/app 1（meta/web/codegen 0）。手写最大：RuleTableModelParser 673（Excel 规则表解析核心）。
- nop-dyn 组 main Java：dao 52/service 24/api 45/app 1（meta/web/codegen 0）。手写最大：DynEntityMetaToOrmModel 671、DynCodeGen 534、InMemoryCodeCache 426；api 以生成风格 beans 为主（NopDynEntityOutputBean 511 等）。
- 测试资产：nop-rule core 7/api 2/dao 1/service 3/web 3/codegen 1；nop-dyn dao 5/service 12/api 2/web 3/codegen 1（以 find 实测为准，Phase 1 复核）。
- check/nop-rule.md 8 条（P1×1+P2×4+P3×3，**无处置块，实测 0**）；check/nop-dyn.md 15 条（P1×3+P2×9+P3×3，自带 15 个 fix-ai-check 处置块含红验证证据——撞车标定输入）。
- DYN-01（2026-09-19）裁定：三处 runWithoutTenantId 均 JUSTIFIED；本项现状复核。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS——其后 plan 2320/2326/2327/2330–2333 修复已合入（不波及 nop-rule/nop-dyn 域）；预存失败归因兜底见 Phase 3。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.7-rule-dyn-deep-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-3.7-<序号>`）+ 驳回台账 + 裁决。
- 规则引擎执行语义（维度 10，roadmap 点名）：DecoratedExecutableRule 生命周期（check2 :19 P0 深标定——afterExecute 契约）、ExecutableRule 求值链、inputs/outputs 契约（:48 NPE 域）、RuleRuntime 日志热路径（:145 域）、NormalizeOutputExecutableRule mandatory 输出语义（:120 域）、RuleModelCompiler 错误上下文（:168 域）。
- Excel 规则表解析（维度 10，roadmap 点名）：RuleTableModelParser（673 行——矩阵/决策树解析、:68 白名单写反 P1 深标定、:222/:239/:276 P3 域）、DaoRuleModelSaver（:89 分支覆盖 P1 + :259 副作用 P3 域）、RuleExprParser（:186 域）、FilterBeanToPredicateTransformer（:204 域）。
- DynCodeGen 动态建模信任边界（维度 13，roadmap 点名第一优先）：DynCodeGen（534 行）+ DynEntityMetaToOrmModel（671 行）+ InMemoryCodeCache（426 行）——动态模型→ORM/代码生成的输入信任（动态列名/表名/默认值到 DDL 链的校验与转义、生成代码注入风险评估、:261 VARCHAR(1) 域）、缓存并发契约（:87 锁/可见性 P1 深标定 + check :45 死锁撞车 + :357 并发 init 域）、发布/下架生命周期（:64 unpublish 类型错 P1 + :110 缓存 key 失配 P2 + check :19 撞车）、租户资源层（:180 恒 null P2 + check :72 撞车 + DYN-01 三处 runWithoutTenantId 现状标定）。
- 权限面（维度 13）：nop-dyn BizModel 动作权限（generateByAI 硬编码 :340 域 + check :160 数据权限撞车）、NopDynFunctionMeta afterEntityChange（:38 P1 深标定 + check :139/:280 撞车）。
- 暂缓项显式承接（Phase 2 主 agent 执行）：check2 :204（空 or 语义，涉平台统一裁定）与 :310（规则日志落库链路）逐条给出「维持暂缓/重新裁定/转 R1.0」结论，不得悬空。
- index 登记 + 独立 closure audit + roadmap 3.7 翻 done。

## Non-Goals

- nop-rule/nop-dyn meta/web/codegen/app 主代码深审（meta 仅作 xmeta/xbiz 契约基准只读核对，见 In Scope 只读例外；web/codegen/app 生成面抽查即可）；nop-orm/nop-xlang 引擎本体（2.1/2.2/1.2 域）；security-audit CORE-05 租户隔离机制本体（link-only，DYN-01 只做现状标定）；nop-dyn api 生成风格 beans 逐文件审计（契约对齐抽查）。

## Scope

### In Scope

- `nop-rule/nop-rule-core/**`、`nop-rule/nop-rule-service/**`、`nop-rule/nop-rule-dao/**`、`nop-rule/nop-rule-api/**`、`nop-dyn/nop-dyn-service/**`、`nop-dyn/nop-dyn-dao/**`、`nop-dyn/nop-dyn-api/**`（api 契约对齐最小口径：每 api 模块 ≥3 个 beans 抽查 + 全部 crud 接口签名与 xmeta 字段比对）
- 只读契约基准（只读例外，不属 meta 深审）：`nop-kernel/nop-xdefs/**/rule.xdef`（规则模型 xdef）、`nop-dyn/nop-dyn-meta/src/main/resources/_vfs/**`（xmeta/xbiz 契约面）、`NopDynModule.xbiz`
- 只读输入：check2/check 的 nop-rule.md 与 nop-dyn.md、`ai-dev/audits/check/SUMMARY.md`、`ai-dev/audits/security-audit/2026-09-19-api-audit-DYN-01.md`、`docs-for-ai/03-modules/nop-rule.md`、`docs-for-ai/03-modules/nop-dyn.md`

### Out Of Scope

- CORE-05 租户隔离机制本体；nop-orm/nop-xlang 引擎；2.7 已扫声明面；demo/app 模块。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: planned
Targets: nop-rule core/service/dao/api + nop-dyn service/dao/api

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：两组手写文件与行数清单（手写口径 `! -name '_*' ! -path '*_gen*'` 实测：rule core 34/dao 12/api 16，dyn dao 34——与 Current Baseline 全量计数差值为生成文件数，属预期）、check2 15+16=31 条与 check 8+15=23 条撞车对照表（由两子 agent 按域构建、主 agent 汇总）、check2 与 check 处置块状态盘点（check/nop-rule.md 无处置块，预期 0；check/nop-dyn.md 15 块）、DYN-01 三处调用点 grep 复扫
- [ ] 规则引擎子 agent（维度 10+13）：nop-rule 全域——DecoratedExecutableRule 生命周期（:19 P0 深标定）/RuleTableModelParser Excel 解析（:68 P1 深标定 + :222/:239/:276 域）/DaoRuleModelSaver（:89/:259 域）/RuleExprParser/FilterBeanToPredicateTransformer/RuleRuntime（:145 域）/NormalizeOutputExecutableRule（:120 mandatory 输出域）/RuleModelCompiler（:168 裸 IAE 域）+ 规则执行输入信任（公式/表达式注入面）+ rule.xdef/xlib 模型面（维度 10）
- [ ] 动态建模子 agent（维度 13+10）：nop-dyn 全域——DynCodeGen/DynEntityMetaToOrmModel 信任边界（DDL 注入/生成代码注入/:261/:235/:207 域）+ InMemoryCodeCache 并发契约（:87 P1 + :357 域 + check :45 撞车）+ 发布/下架/租户资源层（:64/:110/:180/:156/:131 域）+ allProps/:38 afterEntityChange/:340 域 + DYN-01 三处调用点现状标定 + dyn 模型 xdef/xmeta 对齐（维度 10）
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-3.7-{rule,dyn}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] check2 31 条 + check 23 条撞车对照标定结论落盘 + DYN-01 现状标定落盘
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证
- [ ] 主 agent 汇总：判级统一、与 security-audit（DYN-01）/2.7 边界协调、注入类安全判级按 2.6 总裁据
- [ ] 两面正结论收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-3.7-{rule,dyn}-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `3.7-rule-dyn-deep-audit.md`；index 登记 3.7 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-rule/nop-rule-core,nop-rule/nop-rule-dao,nop-rule/nop-rule-service,nop-rule/nop-rule-api,nop-dyn/nop-dyn-dao,nop-dyn/nop-dyn-service,nop-dyn/nop-dyn-api -am`（如遇失败归因预存并登记；**复跑处置块引用的回归测试时按处置记录注明的 reactor 组合**——check2/nop-rule :89 处置明确警告其测试须与 dao 同 reactor 否则误红）
- [ ] 独立子 agent closure audit；通过后 roadmap 3.7 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因预存并登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且规则引擎/Excel 解析/动态建模信任边界/缓存并发/权限面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 3.7 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
