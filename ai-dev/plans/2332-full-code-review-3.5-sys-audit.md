# 2332 full-code-review 3.5 nop-sys 深审

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.5（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 08/13/14 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-sys.md`；check2/nop-sys.md 13 条：P1×1（:19 `@seq:N` 编码规则截断）+ P2×4（:46 持久订阅 suspend/resume、:70 审计日志 NPE、:89 nop_sys_event 无清理、:116 SysCompactExtFieldHelper 缓存不刷新）+ P3×8（:147/:169/:193/:217/:238/:261/:285/:309）。全部带 fix-ai-check 处置块——以 live code 为准标定。**3.4 移交**：nop-sys-dao TestOrmEntityChangeLogInterceptorNullMarker 测试编译断裂（JunitBaseTestCase 符号缺失）归本项登记
> 只读审计（P0 走异步注入）；check2/nop-sys.md 为点名例外输入。
> Draft Review: agent_9f5b210c 首轮 1 Blocker（Phase 3 验证命令在 dao test-compile 必失败——-Dtest 过滤救不了编译期）+ 5 Major（锁 P0 复核无 Goal 行、check2 构成 P2×4/P3×8 勘正、维度 08 归属、三焦点域权威来源未列入输入、编译断裂归因前提与 git 证据相反——**plan 2306 单文件 import 写错非上游变更**）+ 3 Minor 全修；agent_5b6d91b4 二轮复审：首轮 Blocker 判「修复不完整」（`-DskipTests` 不跳 test-compile，compiler 3.11.0 无该参数）+ 1 Major（:19 旧归因残留）+ 5 Minor（零覆盖措辞/「可选顺手修」模糊词/288–290 精确文件名/2.7+3.10 协调主题/:55 措辞）——本轮全部修复；agent_c94ce111 三轮聚焦复核 7/7 PASS、无 Blocker、无新矛盾——进入执行。

## Purpose

执行 roadmap 工作项 3.5：深审 nop-sys（分布式锁过期判断反向 P0 复核、Maker-Checker、事件广播可靠性），产出报告 `3.5-sys-deep-audit.md`。

## Current Baseline

- nop-sys 组：nop-sys-api 57/nop-sys-service 24/nop-sys-dao 83 main（无 core）。子模块：api/app/codegen/dao/meta/service/web。
- **3.4 移交（归因已钉死）**：nop-sys-dao TestOrmEntityChangeLogInterceptorNullMarker 编译断裂——**plan 2306 批量修复 commit bc9c444cf3（2026-10-03）单文件 import 写错**（`io.nop.core.unittest.JunitBaseTestCase` 在 nop-core 全历史从未存在；真实类=`io.nop.autotest.junit.JunitBaseTestCase`）。一行 import 修正即解除，修复工单归 R1.x。
- check2/nop-sys.md 13 条：P1×1（:19 `@seq:N` 编码规则截断）+ P2×4（:46 持久订阅 suspend/resume 失效、:70 审计日志 NPE、:89 nop_sys_event 无清理、:116 SysCompactExtFieldHelper 缓存不刷新）+ P3×8（:147/:169/:193/:217/:238/:261/:285/:309）。全部带 fix-ai-check 处置块——以 live code 为准标定。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；nop-sys 组在基线内（编译断裂归因见上条，已钉死）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.5-sys-deep-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-3.5-<序号>`）+ 驳回台账（正文）+ 裁决。
- 锁过期反向 P0 复核（**roadmap 点名第一优先**）：check/nop-sys.md:19 原文（isExpired 判断反向）→ live `SysDaoResourceLockManager:200-202` 已用 `expireAt < getMinCurrentTimeMillis()`→两条回归测试 `testTryLockContentionDoesNotStealValidLock`/`testExpiredLockIsRecovered` 在场——需实测标定（预期 already-fixed，与 6.4 一致）
- 维度 08 归属注记：IoC/Bean 面已由 2.7 整域扫描覆盖（done，全仓 @Inject 零违规），本项仅做增量
- 状态机与规则面（维度 13/14）：编码规则引擎（check2 :19 `@seq:N` 截断 P1 标定）、Maker-Checker 语义、事件广播可靠性（nop_sys_event 清理/订阅/投递）、SysCompactExtFieldHelper 缓存。
- 编译断裂归因：**已钉死**——plan 2306 单文件 import 写错（非上游变更），核实并登记归因即可。
- index 登记 + 独立 closure audit + roadmap 3.5 翻 done。

## Non-Goals

- nop-sys-web/app/codegen/meta 全模块深审；2.7 已扫声明面。

## Scope

### In Scope

- `nop-sys/nop-sys-api/**`、`nop-sys/nop-sys-service/**`、`nop-sys/nop-sys-dao/**`
- 只读输入：`ai-dev/audits/check2/nop-sys.md`、`ai-dev/audits/check/nop-sys.md`（锁 P0 原文）、`ai-dev/audits/check/SUMMARY.md`（:48/:97 修复记录）、`docs-for-ai/03-modules/nop-sys.md`、`ai-dev/design/maker-checker/`（契约基线）、`ai-dev/plans/288-nop-sys-sys-event-reliability-refactor.md`、`ai-dev/plans/289-nop-sys-broadcast-findnext-refactor.md`、`ai-dev/plans/290-nop-sys-non-broadcast-batch-dsl-refactor.md`（事件队列重构交付现状；同名编号另有 flux-web/docs 系三份无关 plan，不读）

### Out Of Scope

- nop-sys-web/app/codegen/meta；nop-auth（3.10 域）；MA3.11 标定。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: nop-sys api/service/dao

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：三子模块文件与行数清单、check2 13 条处置块状态盘点、编译断裂归因核实（bc9c444cf3 已钉死——核实并落盘，不做全新上游调查）
- [x] 规则与事件子 agent（维度 13+14）：编码规则引擎（check2 :19 标定）+ nop_sys_event 生命周期（:89 域）+ SysCompactExtFieldHelper（:116 域）
- [x] 锁与审计子 agent（维度 13+14）：SysDaoResourceLockManager（:147 域）+ 审计日志拦截器（:70 域）+ Maker-Checker + 编译断裂归因
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A 面第 2 轮收敛；B 面第 5 轮收敛）
- [x] 产物 `_tmp/fcr-3.5-{rules,locks}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check2 13 条标定结论落盘 + 编译断裂归因落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证（25 条全保留、降 0 驳 0）
- [x] 主 agent 汇总：判级统一、跨工作项协调（与 3.4 编译断裂移交源对账；与 2.7 查重——index 已登记「MakerChecker 三重失配」孤儿注册 P2，Maker-Checker 域发现须对照；与 3.10 划界——审计日志 tagSet="audit" 实体位于 nop-credential、写线程上下文域归 3.10，本项只审 nop-sys 侧拦截器）
- [x] 规则面正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.5-{rules,locks}-review.md`
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.5-sys-deep-audit.md`；index 登记 3.5 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（本项零 P0，不适用——已确认「P0 发现追踪」表无 3.5 新增）
- [x] 定向验证锚点：两步走——(a) `./mvnw install -Dmaven.test.skip=true -pl nop-sys/nop-sys-dao` 预装 dao（maven.test.skip 同时跳过 test 编译与执行——compiler plugin 3.11.0 无 skipTests 参数，`-DskipTests` 救不了断裂 test-compile）；(b) `./mvnw test -pl nop-sys/nop-sys-api,nop-sys/nop-sys-service`（不带 -am，使用已装 dao 快照）。**nop-sys-dao 测试因编译断裂未运行（非零覆盖——dao 侧 52-run 测试资产在场）——已登记为移交 R1.x 的验证债（一行 import 修正即解除）**
- [x] 编译断裂修复工单：`nop-sys/nop-sys-dao/src/test/java/io/nop/sys/dao/log/TestOrmEntityChangeLogInterceptorNullMarker.java:6` import `io.nop.core.unittest.JunitBaseTestCase` → 应为 `io.nop.autotest.junit.JunitBaseTestCase`（一行修正，归 R1.x；**本 plan 只登记不修**——只读审计纪律）
- [x] 独立子 agent closure audit；通过后 roadmap 3.5 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS（排除编译断裂），或失败已归因预存并登记；**nop-sys-dao 测试未运行的验证债已显式登记（非零覆盖，资产在场）**
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且规则/事件/锁/审计/Maker-Checker 面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.5 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；编译断裂修复归 R1.0/R1.x；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 只读审计计划收口。报告 `3.5-sys-deep-audit.md` 落盘：通过附 P1×1（owner doc 分布式锁入口 ILockService 全仓不存在——文档→API→装配三层断裂，与死装配族 P2-3.5-008/009 同批修复互引）+P2×11/P3×13（25 条全保留，降 0 驳 0）。**check2 13 条标定全部落定**（9 already-fixed + :261 still-live 维持〔DSL 调用点实证〕+ :285 partially fixed + :217 false-positive〔runbook 已文档化〕）+ check 锁 P0 反向 already-fixed 与 6.4 一致（附 14-09 测试证据收窄注记：生产谓词在全部锁测试中零执行，变异全绿，不翻案）。编译断裂归因钉死 plan 2306 bc9c444cf3 单文件 import 写错（一行修正归 R1.x，本 plan 只登记不修）。维度 08 增量注记：IoC/Bean 面已由 2.7 整域覆盖。定向验证两步走全绿（dao install skip-test + api 11/service 14 全绿 BUILD SUCCESS；dao 测试验证债显式登记）。closure audit 首轮 FAIL（3 处机械笔误：log B 面计数/plan Phase 1 勾选遗漏/index already-fixed 计数）→ 处方落实 → 复核 4/5 落实后补齐 Phase 3 勾选（auditor 明示机械勾选无需重审）→ 满足收口条件。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_33c039b9-87f6-4ea1-9be0-767f93830349（fresh session，只读 closure auditor；首轮 FAIL 处方修正 + grep 复核确认）
- Evidence:
  - 计数勾稽：终态 P0=0/P1=1/P2=11/P3=13=25 ↔ 报告发现清单实点 ↔ index 3.5 行（0/1/11/13）↔ 编号映射表 25 行 ↔ 复核保留数（A 12 + B 13）——auditor 逐条目视清点零缺陷
  - P1 台账：P1-3.5-001 在 index「P1 发现汇总」一致；「P0 发现追踪」无 3.5 新增正确
  - check2/check 标定台账 13 条与初审/复核文件一致；:261 still-live（non-broadcast-consumer.batch.xml:37/:46）、:217 false-positive（audit-field-changes.md:48-50）、锁 P0 already-fixed（:200-202 `<` + min 口径）三重点独立确认
  - 锚点抽验 6+1 条亲读 live code 全命中（P1-3.5-001 grep 全仓零代码命中/P2-3.5-006 action-auth.xml:175-178/P2-3.5-008 ResourceWithHistoryProvider:23〔行号漂移 :37→:24 已勘正〕/P2-3.5-010 isExpired :200-202 + 测试覆写 :43-46/P3-3.5-011 DefaultCodeRule:60-67/P3-3.5-013 拦截器 :108-111/加抽 P2-3.5-001 tryResetLease :226-245）
  - 格式抽查 8 条均 ≥10 要素完整
  - 编译断裂归因：git blame -L 6,6 独立复跑命中 bc9c444cf38，与 plan :17/:93/:94 三方一致
  - 判级总裁据：P2-3.5-006 按 2.6 总裁据（有授权门槛暴露链=P2）裁定一致
  - 定向测试：两步走 (a) install -Dmaven.test.skip=true exit 0；(b) api 11 + service 14 全绿 BUILD SUCCESS（0 Failures/Errors）；dao 测试未运行验证债已登记
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（3 warning 均为无关历史 plan 预存）；`check-plan-checklist.mjs` 见下方双 --strict 记录
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + closure auditor 亲读 live code 锚点抽验为锚
  - Deferred 项分类检查：无 deferred；P1×1 经 index「P1 发现汇总」进 R1.0，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×1+P2×11 修复归属 R1.0 分族；P3×13 watch-only/R1.0 低优先池；编译断裂一行修正验证债归 R1.x；plan 288/289 记录修订归 G.2 docs 治理——已在报告移交注记）
