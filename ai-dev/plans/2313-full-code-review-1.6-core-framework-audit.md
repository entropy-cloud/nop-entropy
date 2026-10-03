# 2313 full-code-review 1.6 nop-core-framework 六模块结构边界审计

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.6（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/02 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-plugin.md`；既有安全审计 `ai-dev/audits/security-audit/2026-09-18-core-audit-CORE-02.md`（F-C2-1 deferred）
> Skill 适配注记：组内实际为 11 个 maven 模块（按顶层目录称六模块）：boot(5/1)/config(40/6)/ioc(135 main=99 手写+36 `_gen`/71 test 含 1 资源夹具)/log(嵌套 4 子模块 log-core 5/0、log-java 2/0、log-log4j2 2/0、log-logback 2/1)/plugin(26/42，嵌套 api/manager/support)/security(12/4)。**无声明线性分层**——实测组内 DAG：config→nop-log-core、plugin-manager/support→nop-ioc、其余组内边：boot→config 及嵌套组内边（log-*→log-core、plugin-manager/support→plugin-api）；六模块→nop-kernel 组 12 条 module 级向下边。维度 01 甄别实际 DAG 与 import 一致性 + 环；维度 02 阈值 1500（500–1500 watch 移交 4.3）+ **三项点名复核**（CORE-02 F-C2-1 defer 现状、nop-config 弱加密 fail-closed、矩阵 ⑦ 列 docs 发现 CORE-01 现状标定）。
> Draft Review: agent_b50fa750 首轮（3 Major + 6 Minor 全修）；agent_964fb347 复审（2 必改残留 + 2 建议已修，修后 PASS）

## Purpose

执行 roadmap 工作项 1.6：审计 nop-core-framework 六模块（boot/config/ioc/log/plugin/security）结构边界，复核 CORE-02 defer 现状与 nop-config 弱加密默认路径，产出报告 `1.6-core-framework-structure-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- 六模块 main/test：boot 5/1、config 40/6、ioc 135/71、log 11/1、plugin 26/42（嵌套 nop-plugin-api/manager/support）、security 12/4；组合计 main 229/test 124（0.5 刷新值）。
- CORE-02（2026-09-18 security audit）：插件工件完整性控制链 10 环验证通过；**F-C2-1 MEDIUM adjudicated deferred**——`expectedHashes`（唯一独立于下载服务器的信任源）优先级最低（server header/`.sha256` sidecar 覆盖 pinned hash），妥协服务器可 defeating pinning；裁定理由=plan-344 P2-9 strict-hash mode deferred 有记录；临时缓解=HTTPS 插件服务 URL。1.6 复核其 live 现状。
- 矩阵 §2 nop-core-framework 行的「CORE-01」出现两次且含义不同：④ 安全列「CORE-01..03」指 security-audit 的 CORE-01=密钥管理审计（`2026-09-18-core-audit-CORE-01.md`，对象 nop-security IKeyManager——在本项 scope 内但非本项复核对象）；⑦ 文档列「CORE-01：缺 owner doc」才是本项标定对象。`docs-for-ai/03-modules/nop-plugin.md` 现已存在（本项 owner doc 即它）。
- nop-config 弱加密默认路径：roadmap Details 点名「fail-closed 评估」——config 模块含加密值解析面（弱加密实现待定位）。
- 1.1 结论：核心层唯一非基座向下跨组边为 nop-plugin-manager→nop-http-api（api 级合规）；core-framework→nop-kernel 组 12 条 module 级向下边（本次机械基线重推）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.6-core-framework-structure-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.6-<序号>`）+ 驳回台账（正文）+ 裁决。
- 维度 01：组内实际 DAG 与 import 一致性（config→log-core、plugin→ioc、六模块→kernel 12 条向下边；环甄别）；六模块职责纯度（如 log 是否只做日志、security 是否只做安全）；plugin 嵌套组 api/manager/support 分层合规（api 零依赖、manager→api 单向）与 log 嵌套 4 子模块分工。
- 维度 02：巨型文件/职责块盘点；CORE-02 F-C2-1 defer 现状复核（expectedHashes 优先级 live code 现状、defer 理由是否仍成立、临时缓解是否文档化）；nop-config 弱加密默认路径 fail-closed 评估（弱加密算法定位、默认路径行为、升级/降级行为）；CORE-01 owner doc 现状标定。
- index 登记 + 独立 closure audit + roadmap 1.6 翻 done。

## Non-Goals

- nop-ioc 容器语义深审（IoC 消费方视角已在矩阵 ⚠️，全面语义审计无独立工作项——本项只做结构面）；插件运行时行为深审（CORE-02 已做 10 环验证，只复核 defer 项现状）。
- 安全面全仓审计（3.11/4.1 归属；nop-security 密钥管理面归 security-audit CORE-01 已有覆盖）；nop-config 的加密算法强度密码学评估（只评默认路径行为与 fail-closed 语义）。

## Scope

### In Scope

- `nop-core-framework/nop-boot/**`、`nop-core-framework/nop-config/**`、`nop-core-framework/nop-ioc/**`、`nop-core-framework/nop-log/**`、`nop-core-framework/nop-plugin/**`、`nop-core-framework/nop-security/**`；跨组只读取证豁免：`nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/**`（AESTextCipher 默认路径取证，不展开审计）

### Out Of Scope

- 组外模块；security-audit 历史报告的重新审计（只引用与复核 defer 项）

## Execution Plan

### Phase 1 - 两维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: 六模块

- Item Types: `Proof`

- [ ] 动手前基线确认：`git status` 干净后开始
- [ ] 机械基线：组内模块依赖边表（pom + import 双口径）、行数榜、`_` 生成物计数（口径注明）
- [ ] 维度 01 子 agent：组内依赖方向 DAG（pom 声明依赖 vs import 实际；环甄别）；六模块职责纯度（模块名↔内容物一致性；跨域类放置）；plugin 嵌套组分层（api 零依赖/manager→api 单向/support→api）；对 nop-kernel 依赖面合规
- [ ] 维度 02 子 agent：行数榜与职责块（>1500 清单；500–1500 watch 移交 4.3）；**CORE-02 F-C2-1 defer 现状复核**（HttpPluginResourceResolver 哈希优先级 live 现状 vs 审计报告 L207-223 描述；defer 理由/临时缓解 HTTPS 指引是否仍有效且文档化）；**nop-config 弱加密默认路径 fail-closed 评估**（装配面在 nop-config：ConfigStarter L461-477 `new AESTextCipher()` + DefaultConfigValueEnhancer + ConfigConstants L73-77 `nop.config.encrypt-key/-salt-key/-concat-iv`；**算法与默认密钥路径在 nop-kernel/nop-commons `io.nop.commons.crypto.impl.AESTextCipher`**（legacy 兼容格式、硬编码默认 IV/固定盐、CFG_CRYPT_DEFAULT_ENC_KEY 默认密钥路径）——跨组只读取证不展开审计）；**矩阵 ⑦ 列 docs 发现 CORE-01（缺 owner doc，非 security-audit CORE-01 密钥管理）现状标定**（nop-plugin.md 覆盖面 vs 三嵌套模块实际）
- [ ] 深挖迭代：有发现的维度派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-1.6-dim{01,02}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立维度复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；与 3.11/4.1/4.3 边界协调（安全横切/错误处理/巨型文件移交）
- [ ] CORE-02 现状标定收敛（still-deferred/已修/恶化 三选一结论）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-1.6-dim{01,02}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `1.6-core-framework-structure-audit.md`；index 登记 1.6 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 独立子 agent closure audit；通过后 roadmap 1.6 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [ ] 报告落盘且两维度 + 三项点名复核（CORE-02 现状/nop-config 弱加密/CORE-01 docs 标定）全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 1.6 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- CORE-02 F-C2-1 若复核仍 deferred：维持 defer 或转 R1.x 由 R1.0 裁决（本项只标定现状）。

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
