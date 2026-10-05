# 2337 full-code-review 3.10 nop-auth + nop-credential 增量安全审计

> Plan Status: completed
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

Status: completed
Targets: nop-auth + nop-credential

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：两组手写文件与行数清单、**git log 2026-09-19 后两组产品代码变更全量清单（逐 commit 列表——diff 审计核心输入）**、四组输入逐条清点（构成勘正）、处置块状态盘点、测试资产 find 实测
- [x] auth 增量子 agent（维度 13）：diff 面逐 commit 审阅（97c40d6419/8ffc551cd7/98563a870d/2798c23b8f 四 commit；**含 sso diff**）+ NopAuthLoginAttempt/RateLimitCounter 新 API 安全语义深审 + 限流存储一致性横切（check2 :44 + check :163/:181 + RateLimitCounter）+ MFA codec 消费点对照（mfa/store 两文件）+ check2 15 条与 check 14 条撞车合并标定（**分面轮次上限：diff 面 2 轮 + 标定面 2 轮**）
- [x] credential 增量子 agent（维度 13）：**credential diff 面为零 commit 空集（已登记，不派深审）**——CRED-01~04 ledger 状态 diff 检测 + :172 双文件域标定 + :355 countUsage 标定 + msg-cluster-cred credential 条目处置块核对（轻量面，1-2 轮）
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A/B 面均第 2 轮收敛）
- [x] 产物 `_tmp/fcr-3.10-{auth,cred}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] 四组输入标定结论落盘（check2 15 + check 14 + CRED ledger diff + credential 条目）+ diff 清单落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 标定独立验证（7 候选：A 5 保留 + 1 驳回〔13-04 会话共享前提不成立〕、B 1 升格 P2→P1；标定抽验零推翻；check 小结勘误 13 AF）
- [x] 主 agent 汇总：判级统一、与 3.9（gateway auth 面）/3.11（item 14 边界）/2.4（MFA codec P1 移交承接）协调、安全类判级按 2.6 总裁据
- [x] 两面结论收敛（无分歧升级；13-20 升格由复核直接裁定并四理由落盘）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.10-{auth,cred}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.10-auth-credential-incremental-audit.md`；index 登记 3.10 行 + P1-3.10-001/P1-check-mcc-172 两行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（本项零 P0——check :23 P0 already-fixed 双确认，未触发即时通道）
- [x] 定向验证锚点：`./mvnw test -pl <4 审计域模块>` BUILD SUCCESS（215 例全绿；初审另 15 例标定抽样全绿）
- [x] 独立子 agent closure audit；通过后 roadmap 3.10 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且 diff 面/限流一致性/标定/MFA 移交承接面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.10 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 增量安全审计计划收口。报告 `3.10-auth-credential-incremental-audit.md` 落盘：通过附 P1×2 进 R1.0（P1-3.10-001 CRED-01 no-finding 台账虚假保证〔复核升格〕+ P1-check-mcc-172 passphrase 泄漏标定维持〔与 P1-check2-nosql-67 同批〕）+P2×1+P3×4（净 6 条新发现；A 面 1 驳回〔13-04 前提不成立〕）。**29 条 auth 标定全覆盖零冲突**（check2 9 AF+6 deferred、check 13 AF+1 partially——复核勘误）：**:44/:19 两 P1 already-fixed 零行号漂移**、**check :23 P0 already-fixed 双确认**（P0 不变量在 779 行拆分 diff 逐字保持）。MFA codec 消费点侧取证闭环（Redis 后端功能性失效 fail-closed 无旁路）。diff 四 commit 全部通过。CRED ledger 一致态零漂移。定向验证 215 例全绿。closure audit 一次通过（九项全 PASS，处方为 log/plan 补记类机械项，已落实）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_3d96c4a4-bb46-4ca8-a41e-7a7d169fd285（fresh session，只读 closure auditor；一次通过附机械处方）
- Evidence:
  - 计数勾稽：净 P0=0/P1=1/P2=1/P3=4=6 ↔ 报告实点 ↔ index 3.10 行（0/1/1/4）↔ 映射表 8 行（6 净+驳回+标定 P1）↔ 复核净账（A 5 + B 1）
  - P1/P0 台账：两行在案一致；「P0 发现追踪」无 3.10 新增正确
  - 标定亲读四重点全命中零漂移：check2 :44（DbLoginAttemptStore:96 条件 UPDATE + :119-125 + Redis :71-73 INCRBY）/:19（DaoLoginSessionStore:44 setLogoutType）/check :23（LoginServiceImpl:373-374 解耦不变量逐字注释）/:172 双文件域（Default L114-135 五点 + Vault L254-273 四点 + 修复样板 L268-269 属实 + NopException.getMessage :383-389 通道在位）
  - 锚点抽验 5/5 亲读命中（CRED-01:36-37 全称量词 + CredentialCipher truncateCiphertext 恰 4 应用点/两 BizModel 裸 CRUD + edit 表单/DbSendCodeRateLimiter:88 天界后缀机制/7 处漂移逐一命中含 AbstractUserContextCache:33 斜杠形态/OrmTemplateImpl:204-231 会话生命周期支持 13-04 驳回）
  - MFA 取证链全链亲核（PrefixEncodeHelper 不对称 + peek:118 唯一 instanceof + 两消费点门控 → fail-closed 成立）
  - 格式：抽查 5 条 10-11 要素完整
  - 13-04 驳回与 13-20 升格如实记载（含锚点勘误 :35→:36-37）
  - 定向验证：4 模块 215 例全绿；`check-doc-links.mjs --strict` exit 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + closure auditor 亲读 live code 锚点抽验为锚
  - Deferred 项分类检查：无 deferred；P1×2 经 index「P1 发现汇总」进 R1.0；6 条 deferred 维持标定均有锚点原位确认，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×2+P2×1 修复归属 R1.0 分族；P3×4 watch-only/R1.0 低优先池〔003+004 同批按 7 处清单修〕；范围外转交观察 MfaLoginPolicyServiceImpl 多租户解析已按报告裁决段移交注记登记）
