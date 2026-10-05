# 2337 full-code-review 3.10 nop-auth + nop-credential 增量安全审计

> Plan Status: active
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.10（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 13 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-auth.md` + `docs-for-ai/03-modules/nop-credential.md`；**本项为增量安全审计**（roadmap Details：「两次 security 审计之间 nop-auth/nop-credential 的 diff 面〔含新增 NopAuthLoginAttempt/RateLimitCounter API〕；限流存储一致性」）。审计输入四组：
> ① check2/nop-auth.md **15 条**（P1×2 :19 LOGOUT_TYPE_NONE 误设/:44 失败计数非原子 + P2×4 :70/:86/:111/:131 + P3×9 :151/:170/:188/:204/:221/:238/:255/:274/:291，15 个 fix-ai-check 处置块）
> ② check/nop-auth.md **14 条**（P0×1 :23 max-login-fail-count 绕过 + P1×1 :51 + P2×3 :72/:92/:114 + P3×9 :139 起，14 处置块；P0 处置记录在 check/SUMMARY.md :96 安全类 8 条修复波次）
> ③ security-audit 基线（2026-09-18/19）：AUTH-01~06、CRED-01~04、consolidation-summary（其 ledger 状态为 diff 基线——**逐项 diff 检测 2026-09-19 后的代码变更**）
> ④ msg-cluster-cred.md 中 credential 条目 2 条（:172 主密钥 passphrase 异常 param 泄漏 P1——**跨 nop-credential-service DefaultCredentialKeyProvider.java:107-135 + kms-vault VaultCredentialKeyProvider.java:250-274 双文件域**；:355 CredentialProviderImpl.countUsage 全量加载 P3）
> 只读审计（P0 走异步注入）；上述报告为点名例外输入。
> **标定口径**：already-fixed 判定须定位 live 修复点（file:line）并抽样复跑对应回归测试；pre-existing「暂缓/需 owner 裁定」处置须显式承接；标定结论与处置块声明冲突时复核后按实际 severity 判级（处置记录过期/stale 归 partially-fixed 台账行，确为 P0 级 live 缺陷才走即时通道）。**增量纪律**：security-audit ledger 已闭环条目（FIXED-batch-N/watch-only/successor-deferred）不重复深审，仅 diff 检测回归；新增代码（NopAuthLoginAttempt/RateLimitCounter 等 2026-09-19 后 API）为深审重点。
> Draft Review: agent_e981008c 首轮 5 Major（M1 两文件构成勘正 check2 P2×4+P3×9/check P2×3+P3×9——:151 实为 P3；M2 In-Scope 补 nop-oauth〔:92 标定所需，零 diff〕；M3 nop-credential.md owner doc 实存；M4 msg-cluster-cred 补 :355 + :172 双文件域；M5 工作量收窄——credential 零 commit 空集登记 + 锚点迁移预登记 + 分面轮次上限）+ 7 Minor（sso 三条标定纳入/MFA 消费点归 auth agent/含 _gen 口径标注/P0 升级措辞收敛/快照新鲜判定/分歧终裁指引）——本轮全部修复；agent_b92d2a33 聚焦复核 8 项全 PASS（3 nit 非阻塞）——无 Blocker/Major 残留，进入执行。

## Purpose

执行 roadmap 工作项 3.10：nop-auth + nop-credential 增量安全审计（两次 security 审计之间的 diff 面 + check/check2 条目标定 + 限流存储一致性），产出报告 `3.10-auth-credential-incremental-audit.md`。

## Current Baseline

- nop-auth 组 316 main Java **（含 _gen；手写 281；子模块 api/app/codegen/dao/meta/service/sso/web/oauth）**；nop-credential 组 46 main Java（含 _gen；手写 42；子模块 api/app/codegen/dao/kms-vault/meta/service/web）。
- **git log 2026-09-19 后：auth 组 8 commit（核心 5 个产品变更：97c40d6419 LoginMfaFlow 459/MfaCodeSender 231 拆分 + LoginServiceImpl 779 diff、8ffc551cd7 UserMfaSelfService 1608 迁移、98563a870d DbLoginAttemptStore 157/DbSendCodeRateLimiter 209 + 两表 ORM、d746ce4aeb 纯生成物、2798c23b8f sso +21）；credential 组零 commit。**
- **修复锚点已迁移预登记**：check2 :44 → ILoginAttemptStore.incrementLoginFailCount（Db:96/Redis:71）；check :163/:181 tracker → 新类 LocalSendCodeRateLimiter；行号漂移/类迁移不得误报为回归，标定按迁移后锚点核对。
- security-audit（2026-09-18/19）已闭环：AUTH-01~06、CRED-01~04 共 10 份报告 + consolidation-summary ledger（CRITICAL 0/HIGH 1〔F-AI4-1 AI 域〕/MEDIUM 23/LOW 若干）。
- 测试资产：以 find 实测为准（Phase 1 复核）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS。**Phase 1 机械基线须 git log 复核两组 2026-09-19（security-audit 基线日）后的产品代码变更并登记**——这是增量审计的核心输入（plan 2335/2336 先例：基线核对被对抗审查要求）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.10-auth-credential-incremental-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-3.10-<序号>`）+ 驳回台账 + 裁决。
- **diff 面深审**（roadmap 点名）：git log 2026-09-19 后两组全部产品代码变更逐 commit 审阅——新增 NopAuthLoginAttempt/RateLimitCounter API 的安全语义（登录尝试记录的 PII/锁定绕过面、限流计数器的一致性）、其他 diff 的安全回归检测。
- **限流存储一致性**（roadmap 点名）：check2 :44 失败计数非原子域 + check :163 本地限流时间戳非原子域 + :181 限流 Map 无清理域 + RateLimitCounter 新 API——DB 存储与本地存储的一致性横切标定。
- check2 15 条（P1×2+P2×4+P3×9）+ check 14 条（P0×1+P1×1+P2×3+P3×9）标定全覆盖（still-live/already-fixed/false-positive/duplicated，check↔check2 同源合并）+ 处置块声明冲突升级评估。
- credential 域：CRED-01~04 ledger 状态 diff 检测 + :172 passphrase 泄漏双文件域标定（DefaultCredentialKeyProvider + VaultCredentialKeyProvider）+ :355 countUsage 标定 + credential 新增面（MFA codec 类型不对称 P1-check2-nosql-67 移交承接——**消费点 mfa/store/SmsCodeEntry/EmailCodeEntry 在 auth 面，归 auth agent；本 agent 只登记移交边界**）。
- index 登记 + 独立 closure audit + roadmap 3.10 翻 done。

## Non-Goals

- security-audit ledger 已闭环条目重复深审（仅 diff 检测回归）；item 14 MEDIUM×14 逐项现状标定（3.11 域）；nop-auth meta/web/app/codegen 生成面（抽查即可）；nop-auth-sso 全面深审（仅 diff 面 + check2 :238/check :239/:254 三条 sso 域条目标定）。

## Scope

### In Scope

- `nop-auth/nop-auth-service/**`、`nop-auth/nop-auth-dao/**`、`nop-auth/nop-auth-api/**`、`nop-auth/nop-oauth/**`（:92 标定所需；09-19 后零 diff）、`nop-auth/nop-auth-sso/**`（diff 面〔2798c23b8f +21〕+ check2 :238/check :239/:254 标定）
- `nop-credential/nop-credential-service/**`、`nop-credential/nop-credential-dao/**`、`nop-credential/nop-credential-kms-vault/**`、`nop-credential/nop-credential-api/**`
- 只读输入：check2/check 的 nop-auth.md、check/msg-cluster-cred.md（credential 条目）、check/SUMMARY.md（:96 处置）、security-audit 的 consolidation-summary 与 AUTH/CRED 报告（diff 基线）、`docs-for-ai/03-modules/nop-auth.md`、`docs-for-ai/03-modules/nop-credential.md`

### Out Of Scope

- item 14 MEDIUM×14（3.11）；security-audit 已闭环条目重复深审；生成面；demo/app。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: planned
Targets: nop-auth + nop-credential

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：两组手写文件与行数清单、**git log 2026-09-19 后两组产品代码变更全量清单（逐 commit 列表——diff 审计核心输入）**、四组输入逐条清点（构成勘正）、处置块状态盘点、测试资产 find 实测
- [ ] auth 增量子 agent（维度 13）：diff 面逐 commit 审阅（97c40d6419/8ffc551cd7/98563a870d/2798c23b8f 四 commit；**含 sso diff**）+ NopAuthLoginAttempt/RateLimitCounter 新 API 安全语义深审 + 限流存储一致性横切（check2 :44 + check :163/:181 + RateLimitCounter）+ MFA codec 消费点对照（mfa/store 两文件）+ check2 15 条与 check 14 条撞车合并标定（**分面轮次上限：diff 面 2 轮 + 标定面 2 轮**）
- [ ] credential 增量子 agent（维度 13）：**credential diff 面为零 commit 空集（已登记，不派深审）**——CRED-01~04 ledger 状态 diff 检测 + :172 双文件域标定 + :355 countUsage 标定 + msg-cluster-cred credential 条目处置块核对（轻量面，1-2 轮）
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-3.10-{auth,cred}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] 四组输入标定结论落盘（check2 15 + check 14 + CRED ledger diff + credential 条目）+ diff 清单落盘
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 标定独立验证
- [ ] 主 agent 汇总：判级统一、与 3.9（gateway auth 面）/3.11（item 14 边界）/2.4（MFA codec P1 移交承接）协调、安全类判级按 2.6 总裁据
- [ ] 两面结论收敛（复核推翻 Phase 1 标定时升级主 agent 终裁并落盘分歧）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-3.10-{auth,cred}-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `3.10-auth-credential-incremental-audit.md`；index 登记 3.10 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl <审计域有测试资产的模块列表，Phase 1 实测后填写>`（不带 -am——对通用约定的显式偏离裁定：-am 拉入两个已归因预存失败〔2.8/3.5 域〕；**快照新鲜判定：定向测试前若编译报 NoSuchMethodError/ClassNotFound 等陈旧签名错误，先 `./mvnw install -Dmaven.test.skip=true -pl <上游>` 刷新后重跑**；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 3.10 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因预存并登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且 diff 面/限流一致性/标定/MFA 移交承接面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 3.10 回填 `done`；log 收口记录已写
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
