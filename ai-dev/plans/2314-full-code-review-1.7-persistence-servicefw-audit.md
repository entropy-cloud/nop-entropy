# 2314 full-code-review 1.7 nop-persistence + nop-service-framework 分层与边界合规审计

> Plan Status: draft
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.7（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/16 + 共享前缀；**roadmap 1.7 行写「维度 01/06」系 typo——skill 维度 06=Delta 定制合规性，「测试覆盖与质量」为维度 16**；roadmap 侧勘正随本项收口回填一并登记）
> Related: owner doc `docs-for-ai/02-core-guides/service-layer.md`；行为级深审归 2.1–2.4/2.6（本项只做分层边界）
> Skill 适配注记：两组按 **reactor 叶模块**口径审计（直接子目录 19 个中 5 个为聚合器：cdc→cdc-core/cdc-debezium、dbtool→dbtool-core/dbtool-web、nosql→nosql-core/nosql-lettuce、orm-drivers→orm-es/mongo/redis/tdengine、graphql→graphql-core/orm/message【graphql-grpc 被注释停用：37/7 不入审计基线，单列】）——叶模块口径 persistence 19 + servicefw 8 = **27 叶**（reactor 口径含聚合器 23+9=32，机械基线产出时留痕统一）。文件系统口径计数 main 769/test 225（persistence）、505/110（servicefw，含停用 grpc）仅作基线参照。维度 01 按两组各自 api/impl/spi 分层口径；维度 16 正文「测试覆盖与质量」聚焦两组测试面；反模式抽验只做抽样级线索、最终裁定归 4.2。
> Draft Review: agent_508bea31 首轮（2 Major：嵌套聚合器遗漏 19→reactor 叶口径、维度 06→16 编号勘误——roadmap 1.7 行 typo 已登记；5 Minor 全修）；agent_bf24cfcf 复审 PASS（2 Minor 一行勘误：biz-auth 边方向 core→api、叶计数 27）

## Purpose

执行 roadmap 工作项 1.7：审计 nop-persistence 与 nop-service-framework 两组的分层与边界合规（api/impl 边界、spi 泄漏、test scope 依赖泄漏），产出报告 `1.7-persistence-servicefw-boundary-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- nop-persistence：13 子模块，main 769/test 225；已知行为级敞口（check2 P0：migration 9/17 change CCE、eql join 放大、nosql RESP3——归 2.1–2.4 复核，本项不重复）。
- nop-service-framework：6 子模块，main 505/test 110；已知敞口（权限 fail-open 三处——归 2.6）。
- 1.1 已确认组间零环；两组跨组出边为向下合规边：kernel/core-framework 之外还有 nop-network 基础设施面（nop-dao→rpc-api、gateway→{rpc-api,rpc-core,http-api}、biz→{rpc-model,http-server-filters}、graphql-core→{rpc-core,rpc-model}）与 nop-utils 工具面（biz→fsm、gateway→router）——均已由 1.1 裁定方向健康，不作为本项新发现。
- 分层规则权威：`docs-for-ai/02-core-guides/architecture-principles.md` §二（core→dao→meta→service→web→app 单向；跨模块经 I*Biz）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.7-persistence-servicefw-boundary-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.7-<序号>`）+ 驳回台账（正文）+ 裁决。
- 维度 01：两组 33 叶模块 DAG 与分层合规——persistence 实测边基座为 orm-model+dao 平行（dao 不依赖 orm-model），orm-eql→{orm-model,dao}、orm→{dao,orm-eql,orm-model}、{orm-data,orm-geo,orm-rpc,dbtool-core}→orm、db-migration→{dao,orm}、orm-pdm→{dao,orm-model}；service-framework 实测边 biz→{graphql-core,graphql-orm}、biz-auth-core→biz-auth-api（biz-auth-core→biz 边不存在）、gateway 与 biz/graphql 无 pom 边（只依赖 rpc/http/router/record-mapping）；spi 泄漏（impl 类型出现在 api 模块签名）；两组间方向（**唯一 pom 边 graphql-orm→nop-orm**；persistence 反向依赖 service-framework 即倒置）。
- 维度 16：test scope 依赖泄漏双向（compile 依赖缺 scope 泄漏 / main import 仅 test scope 提供的类）；reactor 叶模块零测试清单与裁定；test 计数分布异常点（抽样重点：orm-eql 233/15、db-migration 103/9、orm-model 91/6）；与既有敞口（check2/权限 fail-open）边界不重复；与 4.2 的裁定权边界：本项只做抽样级线索。
- index 登记 + 独立 closure audit + roadmap 1.7 翻 done。

## Non-Goals

- ORM 会话状态机/EQL 编译器行为深审（2.1/2.2）、migration executor（2.3）、nosql 协议（2.4）、CrudBizModel/权限 fail-open（2.6）。
- 全仓测试有效性（4.2）。

## Scope

### In Scope

- `nop-persistence/**`（13 子模块）、`nop-service-framework/**`（6 子模块）

### Out Of Scope

- 两组的行为级正确性（2.1–2.4/2.6 归属）；组外模块

## Execution Plan

### Phase 1 - 两维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: 两组 19 模块

- Item Types: `Proof`

- [ ] 动手前基线确认：`git status` 干净后开始
- [ ] 机械基线：两组模块依赖边表（pom + import 双口径）、行数榜、各子模块 test 计数表、`_` 生成物计数（口径注明）
- [ ] 维度 01 子 agent：两组内部 DAG 与层链甄别；api 模块纯净性（persistence 有无 `*-api`？biz-auth-api 纯净性）；spi 泄漏（api 签名暴露 impl）；两组间方向（service-framework→persistence 合规、反向倒置甄别）
- [ ] 维度 16 子 agent：test scope 泄漏双向（pom scope 标签 × main import 扫描）；reactor 叶模块零测试清单与裁定；test 计数分布异常点；反模式抽样级线索（裁定归 4.2）
- [ ] 深挖迭代：有发现的维度派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-1.7-dim{01,16}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立维度复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 2.1–2.4/2.6/4.2 边界协调
- [ ] 两组分层结论收敛（各给一条总评）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-1.7-dim{01,16}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `1.7-persistence-servicefw-boundary-audit.md`；index 登记 1.7 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 独立子 agent closure audit；通过后 roadmap 1.7 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [ ] 报告落盘且两维度全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 1.7 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
