# 2356 full-code-review 6.9 check 系列 P2/P3 批量状态标定（watch-only 归档）

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.9（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: 承接 6.4（P0 全标定——fix-ai-check 分支处置以 master 为准的方法学先例）与 6.5–6.8（P1 四批全闭）。同族先例=plan 2352–2355（独立枚举 diff/冻结三要素/处置面实测/承接路由/双轨校验形态照抄；本项为批量归档而非逐条深标定——性质对齐 6.4 的「批量状态标定」）。
> 机械基线（主 agent 实测 2026-10-08；对抗审查 agent_877eef9f 复核勘正）：check 系列 P2/P3 宇宙 = **694 条**（P2 359 + P3 335，`### [P2]`/`### [P3]` 逐份 grep 亲手加总吻合）× **54 份单元报告**（check/ 目录 56 md − ROADMAP − SUMMARY；SUMMARY :4 自证「54 个检查单元」——对抗审查 B1 勘正，初稿误写 52）。**计数口径调和（对抗审查 M1）**：标题实测 359/335（本项权威口径——逐条目自身标题）；SUMMARY 总表 355/330 与逐报告统计表 356/331 系陈旧汇总（5 份报告统计表与标题漂移：ai-core-api +1/+1/db-migration +1/0/dev-tools 0/+1/nop-excel 0/+1/nop-orm +1/+1（聚焦复核 N4 勘正：均为单向漂移非 ±）——Phase 1 对账落盘勘正表，作为流程缺陷登记不回写历史报告）；SUMMARY :101「P1/P2/P3 尚未处置」过时（实测处置块 505 个/40 文件——6.4 #6b/2353 B2 同族失败模式，本项以逐条实测为准）。fix-ai-check 提及 514 处/41 文件（含 SUMMARY 正文 3 处提及——对抗审查 m9 口径注记）。**P0 已由 6.4 全标定、P1 已由 6.5–6.8 四批全闭**——本项仅处理 P2/P3 层。
> **分类规则（三分类，归入且仅归入一类，优先级①>②>③）**：
> - **① 轮内已标定**：该 check 条目被轮内审计报告的 **check-条目级标定台账行**覆盖（对抗审查 M2 勘正——承接源仅限已实证的 check-条目级终判面：**3.9 §2.2–2.5 表〔71 行，含 3 条 P0 行不在本宇宙；§2.1 系 check2 面排除——聚焦复核 N3 勘正计数注记〕/4.4 §2.1–2.3 台账〔format-misc/search/msg-cluster 50 条〕/4.5 §2.1–2.5 台账〔dev-tools/utils/runner-cli/frontend-benchmark/migration 88 条〕/6.4 P0 台账（P0 层不在本宇宙）**；3.5/3.6/3.7 等「净新发现计数」非 check 标定计数不入承接源；**check2/ 为独立目录不在本宇宙，且行号体系与 check 相近——禁止按行号跨目录匹配**）。Phase 1 须产出「单元×承接源」覆盖矩阵（54 单元逐个登记有无行级承接面），防止虚假承接与空转超载。承接终判路由（对抗审查 M3）：**still-live（含 partially 带 live 残余）且未在 index 登记者 → 并入升级候选清单上报 R1.0（附在先行标识）**；已登记（P2-3.9-XXX 等承载）→ registered；fixed/partially-fixed 已闭合 → fixed；adjudicated-deferred → registered。
> - **② 处置块已标**：条目带 fix-ai-check 处置块（子型如实记录：fixed/partially-fixed/确认属实/暂缓维持——对抗审查 m7 勘正：②含全部处置块形态，「确认属实/暂缓」非③）。机械采信 + 抽验（下）。
> - **③ watch-only 默认**：非①非②（无处置块且无轮内行级终判）。批量归档 watch-only；**dup 溯源列**（对抗审查 m10）：若与轮内发现同根但无行级终判，登记在先发现 ID 保留溯源（归档不变）。
> **升级筛查（异常项通道，roadmap 6.9 Details）**：②③类 **P2 全部**逐条读标题+现状段（③类含 dup 溯源面），凡疑似 P1+ 量级（操作化判据：安全击穿/静默数据损坏或不可逆删除/核心契约漂移且无护栏且可达）→ 列入「升级候选清单」上报 R1.0 裁决（**不自动改判**、不自动入 P1 汇总——R1.0 裁决后才定）；①类 still-live 未登记者并入本清单（见①路由）；P3 不做升级筛查（规则 #3 watch-only）。升级候选清单**冻结落盘**（对抗审查 m8）：`_tmp/fcr-6.9-escalation.csv`，SHA-256 入冻结协议。
> **验证协议（抽验门槛，对抗审查 m5 校验点）**：② 类处置块采信抽验 = max(≥35, ②类实测数的 10%)——Phase 1 实测 ② 规模后确认下限并落盘（②∈[35,70) 时按 max 公式即 35——聚焦复核 N4 注记：小 ② 档位覆盖 max 下限，按预期 ②≈200–300 不触发，Phase 1 落盘时写明解释；<35 时全抽）；抽验含实跑 ≥10 条（6.4 F4 教训：仅读断言不判 fixed）；① 类承接锚回读 ≥15 条；③ 类升级筛查 100% 覆盖②③类 P2。冻结协议四件套（seed=2356 + 宇宙快照哈希 + 抽验样本表 `_tmp/fcr-6.9-sample.csv` + 升级候选表 `_tmp/fcr-6.9-escalation.csv`）落盘，脚本 `_tmp/fcr-6.9-sample.py`，报告附录记 SHA-256。
> **升级候选的 index 挂载（对抗审查 m6）**：沿「R1.0 输入池」先例——index 6.9 行裁决摘要列载升级候选计数，清单本体在报告 §升级候选（每条附疑似理由+在先锚）；R1.0 以报告全集对账规则读取；不占 P1 汇总/P2 台账（裁决权在 R1.0）。
> 上游承接源（仅限①类清单，对抗审查 M2 勘正）：3.9/4.4/4.5 三份 check-条目级台账（详见分类规则）；6.5–6.8 的 P1 标定不重复（已闭批次不在本宇宙）；6.5–6.8 各报告 Non-Goals 移交的「P2/P3 处置残余」即本项②③类输入。
> 只读审计。**执行分支口径**：以当前工作树（audit/full-code-review）为读、以 master 可达性为锚；实跑红按 plan 2331 :93 先例归因预存并登记。
> Draft Review: agent_877eef9f-f622-4ea3-92d8-76a32c03430f 首轮 **1 Blocker（B1 单元数 52 实为 54+Phase 1 缺独立枚举 diff）** + 4 Major（M1 SUMMARY 三口径并存未调和/:101 过时/M2 ①类承接源清单失真——净新发现计数冒充标定计数+check2 行号误配风险+无覆盖矩阵/M3 ①类 still-live 承接条目无路由——2355 M1 同型复发/M4 对账单 agent 超载 694 条×16 源）+ 6 Minor（m5–m10）——本轮全部修复（详见各节勘正注记）；聚焦复核（同一审查员）10 项全 PASS 但新发现 **N1 Major（承接面归属「甲管 3.9/乙管 4.4+4.5」与单元分组矛盾——net 族 3.9 面跨组漏认领）**+3 Minor，修复（承接面改按单元归属对照三源/分组候选文件避竞态/计数注记 71 行/措辞）后二次核认 **PASS 零问题**（commits `905f1d8edd`/`ac5cc845ae`）——转 active。

## Purpose

执行 roadmap 工作项 6.9：check 系列 P2/P3（694 条/54 单元）批量状态标定——①轮内已标定承接（含 still-live 路由）+ ②处置块采信抽验 + ③watch-only 归档（含 dup 溯源）+ 升级候选上报 R1.0，产出报告 `6.9-check-p2p3-watchonly-archive.md`。

## Current Baseline

- 宇宙 694 条/54 单元、三口径差异、分类规则见头部注记；P0/P1 层已闭不在本项范围。
- 6.5–6.8 各报告 Non-Goals 移交的 P2/P3 处置残余即本项②③类输入（移交声明属实——对抗审查实测确认）。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（实跑为抽验副作用）；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.9-check-p2p3-watchonly-archive.md`：摘要 + 宇宙对账台账（694 条三分类计数逐单元分布+三口径勘正表）+ 承接台账（①类含 still-live 路由）+ 处置块采信抽验记录（②类）+ 升级候选清单（上报 R1.0，冻结 SHA-256）+ watch-only 归档宣告（③类，全表 `_tmp` 产物引用）+ 裁决（附录记冻结协议 SHA-256）。
- 三分类全覆盖：694 = ①+②+③ 账平；「单元×承接源」覆盖矩阵落盘。
- index 登记 6.9 行（含升级候选计数）+ 独立 closure audit + roadmap 6.9 翻 done（closure audit 先于翻 done）。

## Non-Goals

- P0（6.4 已闭）/P1（6.5–6.8 已闭）层任何重标定；修复执行；升级候选的改判与登记（R1.0 裁决权）；非 check 系列的 P2/P3（各轮内报告自有发现不在本项宇宙）；check2/ 目录（独立宇宙）。

## Scope

### In Scope

- check 系列 54 份单元报告 P2/P3 全量分类（694 条，独立枚举 diff 防漏单元）+ 三分类落地 + 抽验 + 升级筛查
- 只读输入：check/ 单元报告、3.9/4.4/4.5 承接台账、git 历史

### Out Of Scope

- P0/P1 层；修复执行；改判级（R1.0 裁决权）；轮内报告自有发现（非 check 系列）；check2/ 目录。

## Execution Plan

### Phase 1 - 对账、分类与抽验（2 对账 agent 分组 + 2 验证 agent）

Status: in progress
Targets: check 系列 P2/P3 全量清单

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（ac5cc845ae）、`git status` 干净后开始
- [ ] **对账 agent 甲（Phase 1–3 单元组 31 份）+ 对账 agent 乙（Phase 4–6 单元组 23 份）**（对抗审查 M4 拆分）：各自对账落盘：① 独立枚举 diff（ROADMAP 单元表 vs 报告全集 54 份——2352 B1 教训防漏单元）② 所辖单元 694 条中份额的全量清单（条目号/处置标注实测/「单元×承接源」覆盖矩阵登记——**各 agent 对所辖单元逐一对照三源（3.9/4.4/4.5）登记覆盖矩阵**〔聚焦复核 N1 勘正：承接面按单元归属而非按源归属——3.9 面跨甲乙两组（gateway-bizauth 在甲/net-http-rpc+net-misc 在乙）、4.4 面亦跨（format-misc 在甲/search+msg-cluster 在乙）〕，覆盖矩阵汇总落盘）③ 三分类机械划分（规则见头部；①类承接台账逐条在先行标识；**①类 still-live 未登记者并入升级候选**）④ 升级筛查（所辖②③类 P2 全部标题+现状段过读，候选附 ≥3 行疑似理由，写分组文件 `_tmp/fcr-6.9-escalation-a.csv`/`-b.csv`——聚焦复核 N2 避免同名并行写竞态；主 agent 合并后统一冻结 `_tmp/fcr-6.9-escalation.csv`）⑤ 所辖统计核对（逐份+三分类账平）
- [ ] Agent 1「抽验」：②类处置块采信抽验 max(≥35, 10%)（代码亲读+实跑 ≥10——6.4 F4 教训）+ ①类承接锚回读 ≥15 条 → `_tmp/fcr-6.9-verify-a.md`
- [ ] Agent 2「升级候选复核」：两对账 agent 的升级候选清单逐条独立复核（亲读代码锚，独立判断疑似量级成立性）+ ②③类 P2 全量快扫兜底（防漏候选）→ `_tmp/fcr-6.9-verify-b.md`
- [ ] 主 agent 汇总：两分组清单合并、覆盖矩阵汇总、三分类总账平、冻结协议四件套落盘（seed=2356+宇宙快照哈希+sample.csv+escalation.csv）、主 agent 抽查（冻结表重放+抽验抽读+候选终览）
- [ ] 如发现 live P0：**发现即异步注入** + 「P0 发现追踪」登记

Exit Criteria:

- [ ] 初稿落盘；三分类账平（①+②+③=694）；升级候选每条 ≥3 行疑似理由且冻结落盘
- [ ] 抽验达标（②类下限 Phase 1 实测后确认落盘、含实跑 ≥10；①类 ≥15 锚回读）；样本表可重放（SHA-256）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：三分类边界抽验（①②③ 归类错误抽验 ≥30 条——对抗审查 M4 分类标记独立复核面）+ 升级候选终裁复核（维持/降 watch-only 理由化+漏候选兜底）+ 计数收敛
- [ ] 主 agent 汇总：分类统一、升级候选清单定稿

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.9-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [ ] 编制 `6.9-check-p2p3-watchonly-archive.md`（附录记冻结协议 SHA-256 四件套）；index 登记 6.9 行（替换占位行，裁决摘要列载升级候选计数——沿 R1.0 输入池先例，不占 P1 汇总/P2 台账）
- [ ] 定向验证锚点：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（六项：证据完整性/分类正确性/引用路径真实性/计数对应——三分类账平+抽验覆盖率/升级候选完整性与冻结重放/流程状态）；**通过后** roadmap 6.9 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 分类口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划；抽验含只读测试实跑，免除全量构建。

- [ ] 报告落盘且 694 条三分类全覆盖（①+②+③=694 账平，54 单元枚举 diff 一致）
- [ ] 抽验达标（②类实测下限落盘+含实跑 ≥10、①类 ≥15）；升级筛查 ②③类 P2 100% 覆盖
- [ ] index 计数与报告分类台账一一对应；升级候选清单完整且冻结可重放
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.9 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（升级候选的裁决与后续修复归 R1.0/R1.x；watch-only 档案为终态归档，无后续义务。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
