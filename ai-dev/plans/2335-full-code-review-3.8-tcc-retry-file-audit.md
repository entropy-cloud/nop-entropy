# 2335 full-code-review 3.8 nop-tcc + nop-retry + nop-file 深审

> Plan Status: completed
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.8（MA3）；Skill=`ai-dev/skills/state-machine-business-review-prompt.md` + `ai-dev/skills/deep-audit-prompts.md`（维度 13 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-tcc.md` + `docs-for-ai/03-modules/nop-retry.md` + `docs-for-ai/03-modules/nop-file.md`；**check2 无三模块独立报告**——审计输入为 `ai-dev/audits/check/file-retry-tcc.md` 18 条（P0×1 :27 CANCEL_SUCCESS 误标 + P1×3 :61/:83/:108 + P2×6 :132/:154/:175/:192/:209/:229 + P3×8 :252/:270/:290/:307/:322/:344/:364/:381），18 个 fix-ai-check 处置块 + 1 个补记块（:250）；check/SUMMARY.md :90 记录 fix-ai-check 六单元（dev-tools/file-retry-tcc/format-* 等合计 118 条，含本单元 18 条）全部发现已在该分支处置（74 修复含全部 P0/P1）——**标定时以 live code 为准逐条复核**（该单元是 SUMMARY :90 六单元之一，处置密度高、翻案风险也高）。
> 只读审计（P0 走异步注入）；check/file-retry-tcc.md 与 check/SUMMARY.md 为点名例外输入。
> **标定口径**：already-fixed 判定须定位 live 修复点（file:line）并抽样复跑对应回归测试；pre-existing「暂缓/需 owner 裁定」处置须显式承接（维持暂缓/重新裁定/转 R1.0，不得悬空）；标定结论与处置块声明冲突（声明已修复实测仍 live）时升级 P0 即时通道评估。
> Draft Review: agent_ab4a739f 首轮 1 Blocker（发现总数 19→实为 18：P3×8 非 ×9，5 处计数勘正——19 个引用块=18 处置+1 补记 :250）+ 1 Major（基线 :22 与 git 实况不符——plan 2282-WS2/WS3、2303/2304 已触及三组，:61 标定须交叉 2282 幂等键修复）+ 7 Minor（dao 生成面抽查认领/:307 显式化/验证命令补 nop-retry-api+快照新鲜注记/SUMMARY 118 条归属措辞/api 抽查与 2.7 对账注记）——本轮修复后 agent_e555acea 聚焦复核：F2+F3–F7 PASS，F1 残留两处（:6 头部/:61）已补勘正，+66/-4 行数微勘——进入执行。

## Purpose

执行 roadmap 工作项 3.8：深审 nop-tcc + nop-retry + nop-file（TccEngine 状态机 CANCEL_SUCCESS 误标 P0 复核与恢复路径、RetryEngineImpl 死信语义、DaoResourceFileStore 上传下载越权），产出报告 `3.8-tcc-retry-file-deep-audit.md`。

## Current Baseline

- nop-tcc 组 main Java：api 15/core 14/dao 9/service 5/integration 1/app 1（meta/web/codegen 0）。手写最大：TccEngine 355、TccRecordStore 356。
- nop-retry 组 main Java：api 15/engine 8/dao 14/service 7/app 1（meta/web/codegen 0）。手写最大：RetryEngineImpl 661、RetryRecordStoreImpl 332。
- nop-file 组 main Java：dao 7/service 4/api 3/app 1（meta/web/codegen 0）。手写最大：DaoResourceFileStore 422。
- check/file-retry-tcc.md 18 条构成：P0×1（:27）+ P1×3（:61/:83/:108）+ P2×6（:132/:154/:175/:192/:209/:229）+ P3×8（:252/:270/:290/:307/:322/:344/:364/:381），18 个处置块 + 1 个补记块（:250）。
- 测试资产：以 find 实测为准（Phase 1 复核）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS。**其后三组产品代码已被触及（非本 roadmap 项）**：`03d05ed88c`（plan 2282-WS2，2026-09-30）RetryEngineImpl +70/-6、IRetryRecordStore/RetryRecordStoreImpl 幂等键全生命周期查重+TOCTOU 兜底——**与 check :61 标定直接相关，状态机 agent 须交叉复核**；`1d796c1e22`（plan 2282-WS3）DaoResourceFileStore FILE_HASH SHA-256 落库；`dda2cac367`/`60d66ad47b`（plan 2303/2304）tcc/retry +117、file +36 测试用例。预存失败归因兜底见 Phase 3（已知：nop-stream-runtime TestStreamConnectorRegistryDiscovery、nop-sys-dao 测试编译断裂〔3.5 域〕——两者仅在 -am 链上）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.8-tcc-retry-file-deep-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-3.8-<序号>`）+ 驳回台账 + 裁决。
- TccEngine 状态机（维度 13 + 状态机 skill，roadmap 点名第一优先）：TccEngine（355 行）+ TccRecordStore（356 行）——:27 CANCEL_SUCCESS 误标 P0 深标定（超时取消失败路径的终态聚合语义）、TRYING/CONFIRMING/CANCELLING 状态机全转移、:154 盲写并发域、:175 CONFIRM_FAILED 空转域、恢复路径（重启后未完结事务的补偿恢复）、:108 cleanCompletedTransactions 契约域、:322/:344/:381 域。
- RetryEngineImpl 可靠性语义（维度 13 + 状态机 skill）：RetryEngineImpl（661 行）+ RetryRecordStoreImpl（332 行）——:61 并发重复执行域、:83 死信语义深标定（扫描异常早退→死循环不进死信）、:132/:192/:209/:364 域、租约/扫描调度语义、死信重放路径。
- DaoResourceFileStore 上传下载越权（维度 13，roadmap 点名）：DaoResourceFileStore（422 行）——路径拼接防御（:270 域）、上传限制（大小/类型/路径穿越）、下载越权（record 归属校验）、:229/:252 孤儿文件域、与 IResourceComponentManager 的所有权链。
- 权限横切（维度 13）：:290 data-auth 空壳 + CRUD 直接暴露引擎执行表（TCC/Retry 表内数据即执行凭据）——深标定并按 2.6 总裁据判级。
- check 18 条标定全覆盖（still-live/already-fixed/false-positive/duplicated）+ 处置块声明冲突升级评估。
- index 登记 + 独立 closure audit + roadmap 3.8 翻 done。

## Non-Goals

- 三模块 meta/web/codegen/app 深审（生成面抽查即可）；nop-orm 事务机制本体（2.1/2.3 域）；2.7 已扫声明面；nop-file-web 前端资产。

## Scope

### In Scope

- `nop-tcc/nop-tcc-core/**`、`nop-tcc/nop-tcc-dao/**`、`nop-tcc/nop-tcc-service/**`、`nop-tcc/nop-tcc-api/**`（契约对齐抽查）、`nop-tcc/nop-tcc-integration/**`
- `nop-retry/nop-retry-engine/**`、`nop-retry/nop-retry-dao/**`、`nop-retry/nop-retry-service/**`、`nop-retry/nop-retry-api/**`（契约对齐抽查）
- `nop-file/nop-file-dao/**`、`nop-file/nop-file-service/**`、`nop-file/nop-file-api/**`
- 只读输入：`ai-dev/audits/check/file-retry-tcc.md`、`ai-dev/audits/check/SUMMARY.md`（:90 六单元处置记录）、三个 owner doc

### Out Of Scope

- CORE-05/2.1 事务机制本体；nop-file meta/web/codegen/app；demo；2.7 已扫声明面。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: nop-tcc + nop-retry + nop-file

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：三组手写文件与行数清单（主 agent 已生成：RetryEngineImpl 661/DaoResourceFileStore 422/TccRecordStore 356/TccEngine 355/RetryRecordStoreImpl 332；测试分布 tcc core 7/dao 4、retry api 4/engine 4、file dao 1/service 1）、check 18 条处置块状态盘点（18 处置 + 1 补记 :250）、测试资产 find 实测
- [x] 状态机子 agent（维度 13 + state-machine skill）：TccEngine/TccRecordStore 状态机全转移（:27 P0 深标定 + :154/:175/:108/:322/:344/:381 域 + 恢复路径）+ RetryEngineImpl/RetryRecordStoreImpl 可靠性语义（:61/:83/:132/:192/:209/:364 域；**:61 标定须交叉复核 plan 2282-WS2 幂等键修复〔03d05ed88c〕**）+ check 18 条中 tcc/retry 条目（:27/:61/:83/:108/:132/:154/:175/:192/:209/:307/:322/:344/:364/:381）标定
- [x] 文件与权限子 agent（维度 13）：DaoResourceFileStore 上传下载越权（:270/:229/:252 域 + 上传限制深标定）+ :290 data-auth 空壳与引擎执行表 CRUD 暴露（三模块横切）+ nop-file check 条目标定 + api 契约对齐抽查（tcc/retry 各 ≥3 beans + crud 接口签名 vs xmeta；与 2.7 结论对账用，非重复深扫）+ **三组 dao 生成面抽查**（实体/biz 接口/常量类）
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（A 面 3 轮/B 面 2 轮收敛）
- [x] 产物 `_tmp/fcr-3.8-{sm,file}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check 18 条标定结论落盘
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check 标定独立验证（A 16/16 保留带 2 事实修正、B 9 保留 + 1 驳回〔13-26 __TEMP__ 短路证伪〕；check 18/18 核验）
- [x] 主 agent 汇总：判级统一、与 3.1（nop-wf 状态机先例）/3.2（nop-task 恢复完整性族先例）边界协调、权限类判级按 2.6 总裁据（13-21 终裁 P1=P1-2.6-006 延伸）
- [x] 两面正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.8-{sm,file}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.8-tcc-retry-file-deep-audit.md`；index 登记 3.8 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记（本项零 P0——:27 P0 already-fixed 双确认、:154/:175/:290 三处升级评估均裁定非 P0）
- [x] 定向验证锚点：六审计域模块全绿 BUILD SUCCESS（tcc-core 52/tcc-dao 32/retry-engine 44/retry-api 20/file-dao 4/file-service 4 = 156 例 0 失败；含 TestTccRunner 10/10、TestRetryEngineImpl 30/30）
- [x] 独立子 agent closure audit；通过后 roadmap 3.8 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且 TCC 状态机/Retry 可靠性/文件越权/权限横切面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.8 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 只读审计计划收口。报告 `3.8-tcc-retry-file-deep-audit.md` 落盘：通过附 P1×5 三族（文件权限面族×2 + 引擎执行表零 auth〔:290 升级，终裁 P1=P1-2.6-006 延伸〕+ 状态机恢复族×2）+P2×5+P3×14（净 24 条；B 面 1 驳回〔13-26 __TEMP__ 短路证伪〕、A 面 1 撞车索引不计数、2 条事实修正）。**check 18 条标定全覆盖**（10 AF + :154/:175 partially〔处置记录过期非回归，残余 P2-3.8-004 承载〕+ 5 still-live 与处置一致 + :290 升级）。**:27 P0 already-fixed 双确认**（TestTccRunner 10/10）+**:61 交叉 plan 2282 通过**（TestRetryEngineImpl 30/30）。定向验证六模块 156 例全绿。closure audit 一次 PASS（处方为 log/plan 补记类机械项，已落实）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_70bc5bfd-23ff-47cf-8df3-5ab5624d5fdf（fresh session，只读 closure auditor；一次 PASS 附机械处方）
- Evidence:
  - 计数勾稽：净 P0=0/P1=5/P2=5/P3=14=24 ↔ 报告实点（python 逐节切分 24 finding 节）↔ index 3.8 行（0/5/5/14）↔ 映射表 26 行（24 净+驳回+撞车）↔ 复核净账（A 15 计数 + B 9 计数）
  - P1 台账：5 行在案一致；「P0 发现追踪」无 3.8 新增正确
  - check 标定 18 条三重点亲读：:27 四修复点 + TestTccRunner 10/10 surefire 实测/:61 两阶段修复 + 30 @Test 实测/:290 升级链（GraphQLActionAuthChecker:118-121 + data-auth 空壳 + FNPT 装饰性亲证）
  - 锚点抽验 6/6 亲读命中（attachFile:383-392 无归属比对/endAsync:63-66 路由缺失 + 100 轮遗弃链/download:115-127 contentType 参数 + nosniff 零命中/TccRecordStore:188-193 CAS 失败无信号/TccStatus:64-68 KILLED 零写入者/NopRetryJobRetryBridge withNamespaceId 生产 caller〔行号勘正 :35→:40 已落实〕）
  - 格式：24/24 条 9 要素 python 全量机械核验无缺失
  - 13-26 驳回反证亲读（doGetEntity:959-964 __TEMP__ 短路）；13-20 精确化（/../.. payload + 任意记录改写）在案
  - P1 族口径与 13-21 终裁理由多处一致
  - 定向验证：六模块 156 例 0 失败（auditor 实测 surefire）；`check-doc-links.mjs --strict` exit 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + closure auditor 亲读 live code 锚点抽验为锚
  - Deferred 项分类检查：无 deferred；P1×5 经 index「P1 发现汇总」进 R1.0；:154/:175 残余由 P2-3.8-004 显式承载，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×5+P2×5 修复归属 R1.0 分族；P3×14 watch-only/R1.0 低优先池；:154/:175 残余由 P2-3.8-004 承载；SM-01 交叉 NET-04 F-N4-1 合并处置）
