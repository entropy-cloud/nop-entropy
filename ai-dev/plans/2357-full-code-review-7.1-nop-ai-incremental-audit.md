# 2357 full-code-review 7.1 nop-ai 增量审计（2026-08-01 后全部非安全维度增量）

> Plan Status: draft
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 7.1（MA7）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md` + `deep-audit-prompts.md`（维度 03/09/12——契约/错误处理/测试质量；安全维度排除）
> Related: arm 台账 `ai-dev/backlog/audit-remediation-roadmap.md`（v16，2026-08-01 收口，50/50 done——本项审计其后的增量）；`ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（重构 WI 权威编号——**25 WI = 终态 24 + WI25 2026-09-28 插入**，merge 提交 72f805edae「25 WI 全部落地/裁定」；WI17=toolkit 并发安全标记 plan 2298、WI20–22=nop-ai-core 缓存键/观测/Retry-After plan 2302–2304）；`ai-dev/audits/arm-index.md`（先例承载）；4.3 报告 P2-4.3-003（ReActAgentExecutor 心跳/steering 块——已有裁定，本项只做复杂度巡检不重复拆分裁定）。
> 机械基线（主 agent 实测 2026-10-08；对抗审查 agent_16ad4c5c 复核勘正）：nop-ai/ **2026-08-01..HEAD 共 147 提交**（2026-09-01 后 66；nop-ai-agent 2026-09-25 后 16——重构波落地面；roadmap 7.1 行「原 89 提交」与实测 2026-08-01..09-01=81 的口径差在对账台账注记〔m4〕）；**pom 实测 21 个 `<module>`**（对抗审查 M2 勘正——24 目录含 deploy/model/target 非模块）；千行手写文件现势 **5 个**（全在 nop-ai-agent/engine）。**千行回涨三口径预调和（m1，git 实测 arm 收口时点 0136a514d0）**：ReActAgentExecutor arm 时已 1002 行=非回涨〔4.3 P2-4.3-003 裁定面〕、DefaultAgentEngine 999→1300 差 1 行触发回涨、AgentSessionLifecycle 880→1182 回涨、AgentToolDispatcher 456→1072 回涨、DefaultAgentEngineConfig 947→1031 回涨——**按 plan 定义回涨=4 个**（roadmap「3 个」记口径勘误不改文本；判定表 Phase 1 落盘）。
> **范围裁定（对抗审查 M3/M4 勘正）**：① **安全维度为发现级排除**——安全修复提交（147 中 ≥10 个：2798c23b8f/1a6cd37943/1f153f3c48/0b7fc8f903/c9a6cd0e56/6acf6346a0/credential-mfa 波次等）的 diff 仍入维度 03/09/12 扫描，仅「新发现的安全缺陷」不再扩报（安全面另有 security-audit 族承载；**3.10 仅覆盖 nop-auth/nop-credential，不覆盖 nop-ai**——初稿表述失准已勘正）；② **路径边界=仅审 nop-ai/ 路径内 diff**（147 中 86 提交触及域外路径——ai-dev 文档/nop-bom/nop-xdefs 等；**WI16/17/20/25 的 tool.xdef 契约面一半在 nop-kernel/nop-xdefs**：域外契约变更登记移交〔背景参考+xdef 面归属 7.4/相关项注记〕，不在本项审计）；③ deploy/sql（12 事件）=下划线生成物排除+登记；model/*.orm.xml（4 事件）=契约维 triage（arm 1306-1 修复波产物注记）；④ nop-spring-mcp-server/-support = arm v16 明文排除面但 nop-ai-mcp-server **从无 arm 基线**（增量 5 提交/13 文件事件）——全量三维审计并在报告登记该事实。
> **发现准入规则（对抗审查 M6 勘正）**：**发现=增量 diff 引入或实质恶化的新缺陷**；触碰面的预存缺陷（diff 前已存在）不新编 `P1/P2-7.1-NNN`——登记撞车台账移交（在先发现 ID 或「预存-移交 R1.0」标注）。维度 12 判据（可操作化）：**新增/重构的 main 代码簇无对应新增测试 = 维度 12 发现候选**（按 deep-audit 锚判级；既有 test 文件的改动归簇 triage）。
> **方法学裁定**：① 审计维度=03/09/12+复杂度巡检；判级锚 deep-audit 共享前缀；② 增量审计两跳：git log 聚类（重构波/修复波/配置文档波）→ 逐簇 diff triage；③ **P0 即时通道**；④ still-live 发现编号 `P1-7.1-NNN`/`P2-7.1-NNN` 进 R1.0 输入；⑤ **撞车检索面（对抗审查 M5 勘正）**：6.4（P2-6.4-001 ai-rest residual live——gateway login 面被 0b7fc8f903 窗口内触碰）/6.5–6.9 各批次 ai 域发现（6.7 批次 c ai 域 20 条 P1 标定+6.9 升级候选 ai 域条目）/6.2（P2-6.2-003 AiFileTool:178/:195——被 c9a6cd0e56 触碰）/4.1（ToolSchemaConverter 吞异常同维先例）/arm-index（arm 已修不复报）；**check 历史单元名→现行模块映射表**（ai-core-api→nop-ai-core+nop-ai-api、ai-rest→gateway/shell/coder/dao/service/maven/mcp 等、ai-toolkit-skills→toolkit+skills+tools——Phase 1 落盘）；4.2（报告 :497 明文排除 nop-ai 组）/3.11（安全维度排除）/5.2（design drift 非三维度）注记 N/A。
> **验证协议（对抗审查 m2/M6 下限）**：报告内每条发现双锚（diff 位点+代码亲读）；主 agent 抽查 ≥5 条发现（含 ≥1 处代码亲读复核）；Phase 2 独立复核 still-live 100% 亲读+其余发现抽验 ≥30% 含代码亲读；冻结协议三要素（seed=2357+提交簇清单哈希+triage 样本表）落盘；结束跑受影响模块 `./mvnw test -pl nop-ai/<受影响模块> -am` 或引用当前绿色基线。
> **执行分支口径**：以当前工作树（audit/full-code-review）为读、以 master 可达性为锚；实跑红按 plan 2331 :93 先例归因预存并登记。
> Draft Review: agent_16ad4c5c 首轮 **6 Major（M1 分工失衡+幻影 rest/M2 模块计数+MCP 无基线+deploy/model 处置/M3 安全排除层级+3.10 失准/M4 跨模块路径规则/M5 撞车面缺 6.4/6.2/4.1+映射表/M6 发现准入+维度 12 判据+抽验下限）+ 4 Minor（m1–m4）**——本轮全部修复（详见各节勘正注记）；聚焦复核（同一审查员）逐项 PASS（分配表三重验证链可操作/实测数字逐字吻合），2 处指示性数字 nit 顺手勘正（Agent3 事件 366/Agent2 明细 4）——转 active。

## Purpose

执行 roadmap 工作项 7.1：nop-ai 2026-08-01 后全部非安全维度增量审计（契约/错误处理/测试质量 + 千行回涨文件复杂度巡检），产出报告 `7.1-nop-ai-incremental-audit.md`，仍 live 者进 R1.0 输入。

## Current Baseline

- arm v16（2026-08-01）50/50 done——其后 nop-ai/ 147 提交为增量面；nop-ai-mcp-server 无 arm 基线（全量审计）。
- 6.7 批次 c 已标定 ai 域 check P1 20 条——撞车面对照不重复标定。
- 绿色基线：编制期全仓 BUILD SUCCESS；工作树在前序收口 commit 后干净。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.1-nop-ai-incremental-audit.md`：摘要 + 增量面对账台账（147 提交聚类+WI 映射+域外触碰登记+check 单元名映射表）+ 千行回涨判定表（4 回涨+1 非回涨）+ 分维度发现列表（双锚）+ 撞车台账（预存缺陷移交）+ 复杂度巡检段 + MCP 无基线面登记 + 裁决（附录记冻结协议）。
- 仍 live 者编号进 R1.0 输入；P0 即时通道。
- index 登记 7.1 行 + 独立 closure audit + roadmap 7.1 翻 done（closure audit 先于翻 done）。

## Non-Goals

- 安全维度的**新发现**扩报（安全修复提交的 diff 仍入三维扫描——发现级排除）；arm 已审代码全维度重审；拆分实施；修复执行；check 系列 ai 域重标定；域外路径（nop-kernel/nop-xdefs 等）审计（登记移交）。

## Scope

### In Scope

- nop-ai/ 21 模块（pom 口径，含 nop-spring-mcp-server/-support）的 2026-08-01..HEAD diff（仅 nop-ai/ 路径内）三维审计
- 千行手写文件复杂度巡检（现势 5 文件）
- 只读输入：git 历史、arm 台账、refactor-roadmap、6.4/6.2/6.7/6.9/4.1 ai 域发现

### Out Of Scope

- 安全维度新发现扩报；arm 已审区全维度重审；拆分实施；修复执行；_gen/生成物（deploy/sql 排除+登记）；域外路径审计（登记移交）。

## Execution Plan

### Phase 1 - 增量对账与三维审计（3 agent 并行，成员闭合）

Status: completed
Targets: nop-ai/ 增量 diff 面

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（d6b719b828）、`git status` 干净后开始
- [x] 主 agent 对账落盘（委派对账 agent）：① **148 提交全量清单**（实测较先验 147 +1——裸日期 --since 时区敏感，取确定性超集并登记偏差；+2 边界提交均 arm 收口日）（逐提交 hash/日期/子模块/主题+**路径域内/域外标记**+**提交簇分配表：每提交恰分配一 agent**—— closures 核验基线）聚类为重构波 83/修复波 64/配置文档波 1+WI 映射落盘（25 WI 全落盘；81→82 窗口口径差注记）② 千行回涨判定表（**回涨=4+非回涨 1，git 实测 5/5 行数精确复现**，增长全落 merge 72f805edae）③ check 历史单元名→现行模块映射表（三向实证）④ 冻结协议三要素（seed=2357+clusters.csv SHA `bb5dd9d3…`+样本 15 簇 `14112637…`）⑤ **三 agent 分工（按实测提交量均衡、成员闭合——对抗审查 M1 勘正）**：**Agent 1=56/Agent 2=46/Agent 3=46**（去重后实测；先验 62+5/≈107/≈77 系模块面累计口径，偏差已登记；nop-ai-codegen 补裁归 Agent2；A1 域内事件 1467 vs 先验 1068+13 系 merge 归并+去重口径）。分配表为唯一认领依据，无「等其余面」；**域外 86 提交/2181 事件登记移交**（tool.xdef 面 WI16/17/25 触、WI20 证伪——plan 表述勘正）
- [x] Agent 1/2/3「分面三维审计」：**合计发现 10 条（P1×2/P2×6/P3×2，全部双锚+still-live 亲读证实）+撞车移交 19 笔+安全面疑似 1 笔移交 security 族**（H-A3-SEC-01 日志脱敏 group(1)=密钥本体——机械复现实证）。A1：P1-7.1-A1-01 WI25 后台任务懒重投递状态机双重破坏/P2×3+复杂度巡检（AgentToolDispatcher 回涨最陡 2.35x 热路径最复杂，建议 R1.0 优先）；A2：P1-7.1-B201 PgVectorStore.search 列读取缺失（生产后端 hits content 恒 null 静默错误结果）/P2×1/P3×2+WI20–22 三维判据全过+deploy 12 事件排除登记+model 3 事件契约 triage 全 PASS；A3：P2×2（ToolManagerImpl pathQueues 永不清理/executeNow.get() 同步抛出致永久 pending）+维度 12 零发现（46 提交全带测试逐簇表落盘）
- [x] 主 agent 抽查质量：冻结表重放 SHA 一致（bb5dd9d3…）+ 抽验 2 条 P1 锚点亲读成立（A1-01 retryBridgeIfNeeded 无条件 COMPLETED 改写/B201 search 仅回读 embedding 列——生产 hits content 恒 null）+ 分配表 148 全覆盖零遗漏零重复（对账断言+机械核验）
- [x] 产物 `_tmp/fcr-7.1-audit-a/b/c.md` + 提交簇清单与分配表 + 千行回涨判定表
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（三 agent 一致零 P0 疑似）

Exit Criteria:

- [x] 初稿落盘，每条 still-live ≥9 行完整条目；发现双锚证据
- [x] 提交分配表 148 全覆盖（零遗漏零重复机械核验）；千行回涨判定 git 实测落盘；样本表可重放
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：**VERDICT PASS 零翻案**——10 条全部亲读成立判级全维持，终裁 **P0×0/P1×2/P2×6/P3×2**；专项亲读：A1-01 消费方全量枚举（污染需首败+重投成功合取，测试仅 COMPLETED 源且钉死污染行为→P1 维持）/B201 生产装配逐字亲证+消费链三环/A3-01/02 默认 toolTimeoutMs=300s>0 缓解面注记（P2 维持）；撞车抽验 12 笔全成立；维度 12 抽验 9 项全带测试；H-A3-SEC-01 移交三要素合规（机械复现独立复跑逐字符成立）；冻结重放 clusters/sample/148 分配机械复核
- [x] 主 agent 汇总：四态统一（零改判）、撞车去重终裁（12/12 抽验成立）
- [x] 台账收敛（Q1 裸 IAE/ISE 11 处非 12——Phase 3 按复核口径落笔；Q3 移交笔数去重算式报告载明）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-7.1-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- [x] 编制 `7.1-nop-ai-incremental-audit.md`；index 登记 7.1 行 + 仍 live 进 P1 汇总（P1-7.1-001/002）+ P2 台账（P2-7.1-001..006）
- [x] 定向验证锚点：受影响模块测试绿（或引用绿色基线——WI25 窗内 3793+295 全绿记录）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（五项）；**通过后** roadmap 7.1 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 审计口径可复现（复核员抽验一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码

## Closure Gates

> 只读审计计划（实跑为验证副作用），免除全量构建；受影响模块测试绿或引用绿色基线。

- [ ] 报告落盘且 147 提交增量面全覆盖（分配表零遗漏零重复+三维扫描）
- [ ] 每条发现双锚证据可复现；判级依据 deep-audit P0–P3 定义；发现准入规则合规（预存缺陷未误立条）；撞车全量比对留痕
- [ ] index 计数与报告发现清单一一对应；仍 live 清单完整进 R1.0 输入
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 7.1 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（仍 live 修复归 R1.x；复杂度巡检结论供 R1.0 参考；域外契约变更移交登记归 7.4/相关项。）

## Closure

Status Note: 工作项 7.1 收口——nop-ai 148 提交增量面三维审计全覆盖（分配表 A1=56/A2=46/A3=46 零遗漏零重复；域外 86 提交登记移交；MCP 无基线面全量），发现 10 条零翻案（终裁 P0×0/P1×2/P2×6/P3×2），撞车移交 19 笔抽验 12/12 成立，安全面 H-A3-SEC-01 移交合规。独立 closure audit（agent_330eae76）**一次 PASS 五项全过**，5 Minor 不阻塞（P2-7.1-004 模块归属勘正 nop-ai-rag 已落实/P1-002+P2-004 补 diff 锚 97be7d367a/audit-c 行号漂移无需回改/映射表 recon §3 承载/index 措辞随翻 done 更新）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: agent_330eae76-d6e0-4ad2-a85a-72fb314b2cd4（独立子 agent，fresh session，只读）
- Evidence:
  - 核验 A 证据完整性 PASS：报告六节齐备；10 条双锚（P1-002/P2-004 补 diff 提交锚后完整）；七件产物在盘逐项吻合
  - 核验 B 计数对应 PASS（亲手重算）：SHA-256 双重放一致；148 行零遗漏零重复双向唯一匹配；分配 56/46/46 机械核验；发现 10=2+6+2；报告=index=P1 汇总 2 行=P2 台账 6 行一一对应；回涨 4+1 与 regression.md 一致；撞车 19 笔算式自洽（29→19 去重规则重算）
  - 核验 C 引用路径真实性 PASS：6 锚逐行命中+佐证锚点命中（桥装配 DefaultAgentEngine:753/injectInput 抛错/BashExecutor 三终态/300_000L/rag-defaults.beans.xml 逐字）
  - 核验 D 严重性标定 PASS：三条抽验论证成立；零 P0 遗漏嫌疑；H-A3-SEC-01 移交三要素合规（独立亲读 DefaultChatLogger 双路径分叉证实）
  - 核验 E 流程状态 PASS：roadmap 翻 done 前 todo；doc-links exit 0 实测
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2357-full-code-review-7.1-nop-ai-incremental-audit.md --strict` 退出码 0
  - `scan-hollow-implementations.mjs` 豁免：只读审计工作项，无新增产品代码
  - Deferred 项分类检查：无 deferred 项；P2×6 复核终裁维持走 P2 裁决例外台账（roadmap 规则 #3 通道）；P3×2 watch-only 规则 #3 终态

Follow-up:

- P1-7.1-001/002 修复归 R1.0；P2-7.1-001..006 → R1.0 裁决（P2 台账承载）；P3×2 watch-only；H-A3-SEC-01 → security-audit 族；复杂度巡检结论 → R1.0；tool.xdef 域外契约面 → 7.4 注记——无本 plan 自有剩余工作
