# 2363 full-code-review 7.7 nop-message-pulsar + nop-record 单轮审计残留复核

> Plan Status: completed
> Last Reviewed: 2026-10-09（报告/plan 日期采用提交时点 2026-10-09；Phase 1-3 事实登记于 10-08.md log 文件——本会话追加式 log 惯例，日期口径统一注记）
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.7（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/09/14 巡检侧重）
> Related: **`ai-dev/audits/2026-05-20-adversarial-review-nop-message-pulsar/adversarial-review-round1.md`（Owner Doc 1，422 行——发现 1~17，空壳原型时代产物；发现 17 sendAsync 丢弃 MessageId :381-399）**；**`ai-dev/audits/2026-08-16-1447-adversarial-review-nop-record-plan-203.md`（Owner Doc 2，144 行——**R1 16 条〔P0×2+P1×6+P2×8〕+R2 7 条=23 条**；含 P0-2 公式双重跳过主 Blocker）**；**plan-203 本体已 completed 并归档 `ai-dev/archived/2026-08/203-nop-record-record-file-defect-fixes-tests-first.md`（执行提交 e530b5c0cc 2026-08-16）**——对抗审查发现的修复在 HEAD 是否仍在位为本项核心复核面〔M3 锚〕；6.1（**P2-6.1-001 Pulsar seekToPosition 空实现**——发现 7 附注挂接 7.7/R1.0；6.9 escalation-final #:192〔msg-cluster-cred 行 192〕与 P2-6.1-001 同一缺陷双立标记——R1.0 合并裁定注记）；**4.4→7.7 kafka 生命周期 5 点移交（4.4 报告 :677 ①..⑤；index :189 P1-4.4-M03「7.7 移交①②」；M-03 seek 语义统一联动 6.1 :18）**〔M2——本项显式承接面〕；6.8（format-record 单元 P1×6 标定——**对照载体=`_tmp/fcr-6.8-universe.md`/`frozen.csv` 非 6.8 报告正文〔M4〕；两套 ID 空间〔plan-203 审查条目 vs check format-record#P1-N〕对照机制=文件+符号键匹配，Phase 1 落盘**）。
> 机械基线（主 agent 实测 2026-10-08；**对抗审查 agent_fb56b1fa 勘正 B1/B2**）：pulsar round1 **发现 17 条**（非 16——发现 17 :381-399；对账范围=round1 全文非 :11-356 截断）；**建设提交 28359021e5 为 2026-05-21（round1 审计次日，窗口内）——round1→现状主证据=该提交 diff**〔m1 勘正，非「更早窗口」〕；nop-record plan-203 审查 **23 条**（非 10）；nop-record 近窗 **2026-08-16..HEAD 7 提交**（plan-203 执行+回归风险面〔m2 钉定〕）。
> **范围裁定**：① pulsar 17 发现逐条现状复核（round1→28359021e5 diff 为主证据+当前代码亲读；仍 live 者立条）；② **P2-6.1-001/6.9 #:192 seekToPosition 现状复核 + kafka 5 点移交整理**（M2 显式承接：①suspended/poll 脱钩已并入 M-01 ②ALWAYS vs INIT 语义区分 ③非 ApiMessage 载荷编码裁决 ④runTask onException 保护〔P3-4.4-008〕⑤停机观察——逐点现状+裁决建议，7.7 以 4.4 报告为输入不必重读 Kafka 源码〔4.4 :677 原文〕）；③ nop-record plan-203 审查 23 条现状复核（撞车对照 6.8 format-record 标定——同源归并/异面立条）；④ 增量巡检=pulsar 1 提交（即主证据）+nop-record 7 提交 diff triage。
> **方法学裁定**：① **三 agent**（Agent 1=pulsar 17 条+seek 整理/Agent 2=plan-203 R1 16 条/Agent 3=plan-203 R2 7 条+kafka 5 点移交——40 条+移交面均衡分工）；② 发现编号 `P1-7.7-NNN`/`P2-7.7-NNN`（历史发现复核沿用原 ID 承载）；P0 即时通道；③ **撞车检索面（M1 勘正）**：6.1/6.9/4.4/6.8（_tmp 载体）/**4.1（format-record#P1-5↔4.1 :258 异根裁定在案；:90 ModelBasedResourceRecordIO 触点）**/1.5（:18 分工结论）/3.9（:699 record-netty 依赖）/4.2（:521-522 record 测试面）；**2.2 移除**（orm-eql 零 record 命中——M1 勘正）；④ 验证协议：双锚；Phase 2 still-live 100% 亲读+复核抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；**冻结协议三要素（m5 勘正）**：seed=2363+净池快照哈希+冻结表哈希落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 plan 2331 :92 先例归因登记。
> Draft Review: agent_fb56b1fa 首轮 **2 Blocker（B1 pulsar 实为 17 条/B2 plan-203 审查实为 23 条）+ 4 Major（M2 kafka 移交未接/M1 撞车面列错漏列/M3 归档锚缺失/M4 对照机制未写）+ 3 Minor（m1/m2/m5）**——本轮全部修复（详见各节勘正注记）；聚焦复核逐项 PASS（17+23=40 计数链+三 agent 分工覆盖无缺口；1 nano 元数据日期注记收口随手更新）——转 active。

## Purpose

执行 roadmap 工作项 7.7：nop-message-pulsar round1 17 发现现状复核 + nop-record plan-203 审查 23 条现状复核 + kafka 5 点移交整理 + seekToPosition 双立整理，产出报告 `7.7-pulsar-record-residual-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- pulsar 自空壳原型全面重建（28359021e5 2026-05-21 为主证据 diff）；6.1 确认 seek 实现在位。
- nop-record plan-203 已 completed 归档；其后 7 提交含回归风险面。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.7-pulsar-record-residual-audit.md`：摘要 + pulsar 17 发现现状台账 + plan-203 23 条现状台账 + kafka 5 点移交整理专节 + seekToPosition 双立整理 + 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.7 行 + 独立 closure audit + roadmap 7.7 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 修复执行；6.8 已标定条目重复标定（对照归并）；pulsar 重建面全量重审（28359021e5 diff+复核为界）。

## Scope

### In Scope

- pulsar round1 发现 17 条现状复核（含发现 7 附注+P2-6.1-001/6.9 #:192 双立整理）
- nop-record plan-203 审查 23 条现状复核（撞车对照 6.8 _tmp 载体）
- kafka 生命周期 5 点移交整理专节
- 只读输入：git 历史、两份 Owner Doc、归档 plan-203、6.1/6.8/6.9 报告

### Out Of Scope

- 修复执行；6.8 已标定条目重复标定；pulsar 重建面全量重审；Kafka 源码重读（4.4 :677 明示不必）。

## Execution Plan

### Phase 1 - 现状复核（3 agent 并行）

Status: completed
Targets: pulsar 17 发现 + plan-203 23 条 + kafka 移交

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（062a68369f）、`git status` 干净后开始
- [x] 主 agent 对账落盘（委派对账 agent）：① **pulsar 17/17 全量在册**（F01~F17 逐条；发现 17 在册——B1 勘正成立）② **plan-203 23/23 全量在册**（R1 16+R2 7；P0-2 在册——B2 勘正成立；归档锚三点互证）③ **6.8 撞车预对账**（format-record P1×6 全 labeled-fixed；**0 条同缺陷撞车，3 处同文件异面**；时序自洽 plan-203 08-16→6.8 08-21/22）④ 提交登记（pulsar 1/nop-record 7 逐行）⑤ 冻结协议（seed=2363+净池 40 条 canonical `77628d4f…`+冻结表 9 样本 `7067d0aa…` 双跑 replay identical；**偏差 8 条登记**含 F10 中间提交 2a0a081761/master 分支不可达注记/6.9 #:192 行锚精化）
- [x] Agent 1「pulsar 面」：**already-fixed 14/still-live 3**（F14 P3 收窄〔kafka 子项 b29d2db717 重建消亡〕/F16 P3〔KafkaMessageService:240-244 warn+ignore 在案行为新证〕/F17 P3〔PulsarMessageService:177-178 thenAccept 空——MessageId 丢弃，修复涉 nop-api-core plan-first〕）；**seek 专节：PulsarConsumeTask.seekToPosition :70-73 仍 TODO 空方法体——P2-6.1-001 residual 未闭合维持**，三处同点位整理=条目唯一承载 P2-6.1-001 不新立编号；实跑 13/13 绿；master 分支 pulsar 系列不可达注记 → `_tmp/fcr-7.7-audit-a.md`
- [x] Agent 2「plan-203 R1 面」：**fixed-by-later-plan 15/still-live 1**——**派生新发现 P1-7.7-001**（readCollection fixed 分支 subStart 在 subInput 后捕获→急切 reader 双重跳过；e530b5c0cc 引入零修复；触发需非流式+急切+fixed 集合零启用场景→P1 非 P0，R1.0 从严升级依据已附）；6.8 异面 3 处逐处亲验；nop-record 31 测试类 0 失败；NO_PROGRESS 守卫语义演进注记 → `_tmp/fcr-7.7-audit-b.md`
- [x] Agent 3「plan-203 R2 面+kafka 移交」：R2 7 条 **closed 5/closed-late 1〔R2-6 watch residual 窗内被 cfc8f70f0b 修复——正向发现〕/partial 1**（R2-4 后半 repeatCountFieldName 显式抛错未实现→S-7.7-C1 Minor 须与归档 plan :263 同族合并）；**kafka 5 点逐点整理**（①M-01 未修 live 维持 ②M-03 未修+INIT/ALWAYS case 体逐字相同+裁决建议 ③非 ApiMessage 编码 live 采纳 4.4 收口方案 ④P3-4.4-008 live 低成本收口建议 ⑤停机观察两处原样维持）→ `_tmp/fcr-7.7-audit-c.md`
- [x] 主 agent 抽查质量：P1-7.7-001 锚亲读成立（subStart 在 subInput 后捕获）+ F17 锚亲读成立（thenAccept 空）+ 冻结表重放交 Phase 2 按附录 canonical 规则精确执行（clusters.csv 系提交台账非冻结表载体——载体重建说明登记）
- [x] 产物 `_tmp/fcr-7.7-audit-a/b/c.md` + 清单
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（三 agent 一致零 P0——P1-7.7-001 触达面受限定 P1 附升级依据）

Exit Criteria:

- [x] 审计产物落盘；still-live 条目各 ≥9 行；双锚证据
- [x] 17+23 条全覆盖+kafka 5 点整理；样本表可重放
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：**VERDICT PASS 零翻案**——still-live 5/5 亲读全维持（F14/F16/F17 P3 维持；**P1-7.7-001 P1 维持不升 P0**——缺陷链独立复算成立但触达面深查 fixed 全仓实为 3 文件〔修正 audit-b 记 2〕且唯一使用方 @Disabled→零启用场景结论不变）；**冻结重放双 MATCH**（净池 40 行 `77628d4f…`+冻结表 10 行 `7067d0aa…`——补齐主 agent 遗留义务；RNG 三连 3/3 复现）；抽验 15/40=37.5% 零翻案；kafka 5/5 核实（b29d2db717 前提确认）
- [x] 主 agent 汇总：台账收敛（3 项精度注记转 Phase 3 报告修正）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-7.7-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [x] 编制 `7.7-pulsar-record-residual-audit.md`；index 登记 7.7 行 + 仍 live 进 P1 汇总（P1-7.7-001）或 P2 台账
- [x] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（五项：证据完整性/严重性标定/引用路径真实性/计数对应——40 条全覆盖+kafka 5 点/移交义务闭环——无静默搁浅）；**通过后** roadmap 7.7 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 复核口径可复现（复核员抽验一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [x] 报告落盘且 17+23 条全覆盖复核+kafka 5 点整理
- [x] 每条双锚证据可复现；撞车比对留痕
- [x] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入（P1-7.7-001 归位 P1 汇总）
- [x] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [x] roadmap 7.7 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项（**kafka 移交义务闭环**——M2 专项核验）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x；seekToPosition 双立归 R1.0 合并裁定；kafka 5 点裁决建议供 R1.0。）

## Closure

Status Note: 工作项 7.7 收口——40 条全量现状复核零翻案（pulsar AF 14/still-live 3 全 P3；R1 fixed 15/still-live 1→派生 P1-7.7-001；R2 closed 5/closed-late 1/partial 1），kafka 5 点移交整理闭环，seek 三处同点位整理=P2-6.1-001 唯一承载。独立 closure audit（agent_e8c16727）首轮 **FAIL 附 1 Blocker**（index P1-7.7-001 行错插 P2 裁决台账——P1 汇总缺失，注册脚本锚点错误），处方落实〔行归位 P1 汇总+**同类错位行 P2-7.3-still-open×5 一并勘正**〔7.3 注册同型错误，超出处方的同类修复〕+勾选滞后补齐+日期口径统一注记〕后轻量复检 **PASS**（md5 逐字节一致+两表 110/46 行格式完整+无其他错位行）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: agent_e8c16727-d388-4db0-b2fb-437d7db2dc4f（独立子 agent，fresh session，只读）
- Evidence:
  - 核验 A 证据完整性 通过：报告齐备；still-live 5 条历史锚↔HEAD 现状锚双锚各 ≥9 行；产物引用一致；git 证据链 7 commit 抽验全中
  - 核验 B 计数对应：40=17+23 全过；pulsar 17=14+3/R1 16=15+1/R2 7=5+1+1 全过；报告=index 0/1/0/3 全过；**P1 汇总恰 1 行子项首轮 FAIL（:243 错插）→ 行归位后复检 PASS（:193 md5 逐字节一致）**；冻结双 SHA 独立重算 MATCH（净池 77628d4f…/冻结表 7067d0aa…+RNG 三连 3/3）
  - 核验 C 引用路径真实性 通过：6/6 亲读（subStart 错序/空 lambda/SubscriptionType/seek TODO/M-03 case 体/P3-4.4-008）
  - 核验 D 流程状态 通过（附 2 项轻微→已落实）：kafka 移交义务闭环 PASS；勾选滞后补齐；日期口径统一注记
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2363-full-code-review-7.7-pulsar-record-incremental-audit.md --strict` 退出码 0
  - `scan-hollow-implementations.mjs` 豁免：只读审计工作项，无新增产品代码
  - Deferred 项分类检查：无 deferred 项；F14/F16/F17 watch-only（规则 #3）；kafka 5 点裁决建议供 R1.0

Follow-up:

- P1-7.7-001 → R1.0；P2-6.1-001 → R1.0（合并裁定承载，修复输入含 M-03 联动）；kafka 5 点裁决建议 → R1.0；S-7.7-C1 → 与归档 plan :263 同族合并；F14/F16/F17 watch-only——无本 plan 自有剩余工作
