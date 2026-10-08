# 2362 full-code-review 7.6 nop-datav followups P2 抽样巡检

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.6（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 04/09/12 巡检侧重）
> Related: **`ai-dev/backlog/nop-datav-audit-followups.md`（Owner Doc，152 行 P2-only backlog）**——#1-17 已 resolved（plan `2026-08-14-0950-1` completed 2026-08-14）；**open P2×52+裁定项×1（对抗审查 M-01 勘正：#18-65 中 #28/#31 已✅消化〔plan 2146-3〕故 multi=open 46/open #66-69=4/#70-72=2+裁定项 #72 快照序列化 sid 暴露评估；roadmap「P2×~55」高估记口径缺陷不改文本；另未编号 open nit×1〔nop-datav-api 空模块，台账 :39〕不入抽样池维持 backlog〔m-01 显式排除〕）；2.6（biz/graphql 深审——datav 域零覆盖声明？Phase 1 对账核对）/6.7 复核 §5（12 条未抽样余量逐条指认含 datav#P1-1〔D3〕——登记在 6.7 非 6.9）+6.9（datav 域唯一相关=升级候选 V2/N3 JWT 默认密钥族扩展锚 nop-datav-app enc-key）。
> 机械基线（主 agent 实测，2026-10-08）：followups 台账 152 行；nop-datav/ **2026-08-01..HEAD = 45 提交**（增量巡检面；确定性口径沿 2357）。**台账 open P2 精确枚举 Phase 1 落盘（预期 52+裁定项——M-01 勘正）**。
> **范围裁定**：① **抽样巡检**（roadmap 7.6 明文「抽样巡检」非全量标定）：open P2 按确定性抽样抽 ≥45%（≥25 条）逐条标定三分类（fixed-by-later-plan/still-open/watch-only——2358 形态），未抽样余量为抽样制设计内残留；② 增量巡检=45 提交 diff triage（域内域外标记沿 2357）；③ 撞车检索面（对抗审查 M-02 勘正）：**6.7 复核 §5（12 条未抽样余量逐条指认含 datav#P1-1〔D3〕——登记在 6.7 非 6.9）**/6.9（datav 域唯一相关=升级候选 V2/N3 JWT 默认密钥族扩展锚 nop-datav-app enc-key）/2.6（biz/graphql 域交界核对）/既有 nop-datav 审计目录（5 份 source audits）。
> **方法学裁定**：① 发现编号 `P1-7.6-NNN`/`P2-7.6-NNN`；P0 即时通道；② 验证协议：双锚；Phase 2 still-live 100% 亲读+标定抽验 ≥30%；主 agent 抽查 ≥3 条含 ≥1 实跑；冻结协议三要素（seed=2362+清单哈希+样本表）落盘。
> **执行分支口径**：以当前工作树为读、以 master 可达性为锚；实跑红按 plan 2331 :92 先例归因登记。
> Draft Review: agent_036d5ac9 首轮 **2 Major（M-01 open 计数拆解错误/M-02 撞车面归属错误）+ 3 Minor（m-01–m-03）**——本轮修复；聚焦复核发现三处残留（:7 预期 ~55/:6 尾部归属条款/:38 只读输入未同步），全部修正后二次核认 **PASS 零问题**（commits `608dec5c58`/`34806b36c8`）——转 active。

## Purpose

执行 roadmap 工作项 7.6：nop-datav followups open P2 抽样巡检（≥45%）+ 增量巡检（45 提交），产出报告 `7.6-nop-datav-followups-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- followups 台账 152 行：#1-17 resolved+2 nit+#28/#31 ✅消化；open P2×52+裁定项×1（Phase 1 精确枚举+未编号 nit×1 显式排除）。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.6-nop-datav-followups-audit.md`：摘要 + 台账对账（open 枚举）+ 抽样标定台账 + 增量巡检台账 + 发现列表（双锚）+ 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.6 行 + 独立 closure audit + roadmap 7.6 翻 done（closure audit 先于翻 done）。

## Non-Goals

- open P2 未抽样余量的全量标定（抽样制设计内残留）；修复执行；既有审计目录重审。

## Scope

### In Scope

- followups open P2 抽样 ≥45%（≥25 条，对 52 基数即 ~48%）三分类标定
- nop-datav/ 2026-08-01 后 45 提交 diff 巡检
- 只读输入：git 历史、followups 台账、6.7 未抽样登记（复核 §5）+6.9 升级候选 V2/N3

### Out Of Scope

- 未抽样余量全量标定；修复执行；非台账与增量面发现。

## Execution Plan

### Phase 1 - 抽样标定与增量巡检（2 agent 并行）

Status: completed
Targets: followups open P2 + 增量面

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（34806b36c8）、`git status` 干净后开始
- [x] 主 agent 对账落盘（委派对账 agent）：① **open 枚举 52+裁定项 #72 精确一致零出入**（#28/#31 已✅/:37-38 nit ✅/:39 未编号 nit 显式排除/#69 特殊状态注记落 CSV special_note）② **45 提交零偏差**（域内 38/域外 7；波次 W1~W7；**工具事故登记**：`--` 后置选项曾得 613——参数顺序纠正防下游复蹈）③ 冻结协议（seed=2362+backlog `8f378493…`+clusters `5a5f868d…`+样本 25 条 48.08%≥45% 且未抽样余量 27 与 Deferred 吻合；#69 在样本内）④ 双 agent 分工确认
- [x] Agent 1「抽样标定」：25 条三分类 **fixed 2/watch 2/still-open 21**（覆盖率 48.1%≥45%；行号漂移逐条标注；低成本快赢 5 处打包建议）；**#72 独立评估**（sid 暴露面真实但属元数据泄露、AR-1 P0 修复后数据面已闭——中低紧急宜随 R1.0 ACL 收口顺带收敛）；撞车三面 clean（6.7 #50 同文件不同缺陷面无双登记/6.9 enc-key 锚亲证/2.6 零覆盖成立）→ `_tmp/fcr-7.6-audit-a.md`
- [x] Agent 2「增量巡检」：**编号发现 1 条 P2-7.6-001**（NopDatavDashboardBizModel:552 残留裸 ISE——W4 引入+台账 P2-19 四处枚举外+W7 同链修复漏此位点双重遗漏链完整，可达性窄 P2 恰当）；域外 7 提交/288 事件登记移交；撞车四面全量（**fc5b390ae↔6.7 datav#P1-1 同面非修复关系核验**——P1-1 仍 live 所有权维持 6.7；19 项预存撞车归并清单含 4 项修复证据）→ `_tmp/fcr-7.6-audit-b.md`
- [x] 主 agent 抽查质量：冻结双哈希重放一致（8f378493/5a5f868d）+ P2-7.6-001 锚亲读成立（裸 ISE 在位；路径 entity/ 微注记）
- [x] 产物 `_tmp/fcr-7.6-audit-a/b.md` + 清单
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（两 agent 一致零 P0 疑似）

Exit Criteria:

- [x] 审计产物落盘；still-live 条目各 ≥9 行；双锚证据
- [x] 抽样 48.1%≥45% 达成+45 提交全覆盖；样本表可重放（双哈希一致）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 100% 亲读+严重性终裁+标定抽验 ≥30%+净账
- [ ] 主 agent 汇总：台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-7.6-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `7.6-nop-datav-followups-audit.md`；index 登记 7.6 行 + 仍 live 进 P1 汇总或 P2 台账
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（五项：证据完整性/严重性标定/引用路径真实性/计数对应/抽样覆盖率）；**通过后** roadmap 7.6 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 巡检口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划，免除全量构建。

- [ ] 报告落盘且抽样 ≥45% 达成+45 提交全覆盖
- [ ] 每条双锚证据可复现；撞车比对留痕
- [ ] index 计数与报告清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.6 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

### open P2 未抽样余量（~27 条）

- Classification: `watch-only residual`（sampling-by-design 残留）
- Why Not Blocking Closure: roadmap 7.6 明文「抽样巡检」约定——抽样 ≥45% 覆盖三分类趋势，余量维持 Owner Doc backlog 承接（显式 successor ownership）
- Successor Required: `no`（backlog 本身即 successor 载体；R1.0 对抽样发现的 still-open 另行裁决）
- Successor Path: `ai-dev/backlog/nop-datav-audit-followups.md`（维持）

## Non-Blocking Follow-ups

（still-live 修复归 R1.x；未抽样余量维持 backlog。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
