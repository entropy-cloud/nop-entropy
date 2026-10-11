# 2343 full-code-review 4.5 nop-frontend-support + nop-dev-tools + nop-migration + nop-runner + nop-utils 深审

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 4.5（MA4）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 02/16/17 + 共享前缀）
> Related: Roadmap Details：「CLI 双入口腐化风险；dev-tools/idea-plugin 质量；utils 依赖卫生」。
> 审计输入四份：① check/dev-tools.md **25 条**（P1×2 :22/:43 + **P2×14** :68–:362 + **P3×9** :381 起——以标题清点为准；25/25 处置块，**:557 处置块归属 :533**——输入文件自身统计表 P2=14/P3=8 与标题矛盾，标定时按标题清点）；② check/frontend-benchmark.md **19 条**（frontend-support ~17 条 + benchmark 2 条，0 处置块——**撞车对照基线**：PageProvider :47 P1 点名等）；③ check/runner-cli.md **17 条**（runner 域 37/37 全深读，0 处置块——**撞车对照基线**：NopCliMainForJdk11 启动缺陷 :180 P3 等）；④ check/nop-utils.md **23 条**（**P0×2** + P1×7，23 处置块——**标定输入：utils 域已知 P0 不得重复上报**）。域构成（主 agent 实测）：nop-frontend-support 109/nop-dev-tools 155（xlang-debugger/idea-plugin/maven-shaded-plugin/api-debugger——check 25 条主要落此二域（22/25；maven-shaded-plugin ×2 + api-debugger ×1 另计）/nop-migration 3/nop-runner 37（cli/cli-core/cli-jdk11/tool）/nop-utils **115 手写**（另有 _gen×4 不审）main Java。
> 只读审计（P0 走异步注入）。
> **标定口径**：already-fixed 须定位 live 修复点并抽样复跑对应回归测试（如可运行——idea-plugin 多为 IDE 宿主测试可能不可运行，注明原因）；与处置块声明冲突时复核后按实际 severity 判级。
> **范围注记**：idea-plugin 域（check :90–:362 大部分条目）为 IDE 宿主代码——运行时缺陷标定以代码审读为准（无法跑 IDE 实测的注明）；CLI 双入口（nop-cli vs nop-cli-jdk11）为 roadmap 点名面。
> Draft Review: agent_41fa9bed 首轮 3 Major（三份同域 check〔frontend-benchmark 19/runner-cli 17/nop-utils 23 含 P0×2〕缺失纳入撞车基线/check 构成勘正 P2×14+P3×9 且 :557 处置块归属 :533/工作量收窄——idea-plugin 标定为主+frontend prior art 增量 diff+utils pom 级判据）+ 5 Minor（_gen 口径/依赖判据允许集/预存失败登记/覆盖度量逐面/6.8 preempt）全修；agent_369f2b43 聚焦复核 8 项全 PASS（1 Minor：Draft Review 行未记录——本行即补；:7「全部落此二域」勘正 22/25）——进入执行。

## Purpose

执行 roadmap 工作项 4.5：深审 nop-frontend-support + nop-dev-tools + nop-migration + nop-runner + nop-utils（CLI 双入口、工具链质量、utils 依赖卫生），产出报告 `4.5-frontend-devtools-migration-runner-utils-audit.md`。

## Current Baseline

- 机械基线（主 agent 实测，**raw 计数**）：frontend-support 109/dev-tools 155/migration 3/runner 37/utils 119 main Java（utils 净手写 115，_gen×4 不审）。
- check/dev-tools.md 25 条带 25 处置块（主要覆盖 xlang-debugger 与 idea-plugin 域）。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；**Phase 1 机械基线须 git log 复核五组 2026-09-22 后产品代码变更并登记**。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.5-frontend-devtools-migration-runner-utils-audit.md`：摘要 + 标定台账 + P0–P3 发现（`P<级别>-4.5-<序号>`）+ 驳回台账 + 裁决。
- **dev-tools 标定与深审**：check 25 条逐条标定（debugger 并发族/idea-plugin 域）+ CLI/调试器 RPC 域深审。
- **CLI 双入口腐化风险**（roadmap 点名）：nop-cli vs nop-cli-jdk11 差异面（代码漂移/维护策略/启动路径一致性）裁定。
- **utils 依赖卫生**（roadmap 点名）：nop-utils 119 文件的依赖方向（不依赖上层模块）/工具类重复度（与其他模块工具类撞车）/巨型类（StringHelper 4932 已裁拆分 P2-1.4-001——撞车引用）现状。
- **frontend-support 与 migration/runner 深审**：frontend-support 109 文件（PageProvider 等——3.5 已立 ResourceWithHistoryProvider 死装配撞车引用）+ migration 3 文件 + runner 启动链。
- index 登记 + 独立 closure audit + roadmap 4.5 翻 done。

## Non-Goals

- idea-plugin UI 功能实测（IDE 宿主）；frontend AMIS 页面资产；各域行为缺陷全面深审（撞车对照）；nop-utils 巨型类拆分（P2-1.4-001 已立案）。

## Scope

### In Scope

- `nop-frontend-support/**`、`nop-dev-tools/**`、`nop-migration/**`、`nop-runner/**`、`nop-utils/**`（src/main Java）
- - 只读输入：check/dev-tools.md、**check/frontend-benchmark.md、check/runner-cli.md、check/nop-utils.md（三份撞车对照基线——标定归 6.4/6.8，本项逐域核对去重）**、4.2 报告（utils 域零测试撞车）、1.4 报告（StringHelper 裁定）、3.5 报告（ResourceWithHistoryProvider）

### Out Of Scope

- AMIS 页面资产；IDE 宿主功能实测；已立案域重复深审；demo。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: 五组 in-scope 模块

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净按 4.1 口径处理）
- [x] 机械基线：五组手写文件清单、check 25 条处置块盘点、git log 2026-09-22 后五组产品代码变更登记、测试资产 find 实测
- [x] dev-tools 子 agent（维度 02+16）：check/dev-tools.md 25 条标定（P1×2+P2×14+P3×9；debugger 并发族重点；**idea-plugin 域以标定为主不深审**——IDE 宿主 131 文件）+ CLI 双入口差异面裁定 + 调试器 RPC 域 + **6.8 preempt 登记**（dev-tools 属批次 d，25 条标定完成后 6.8 引用本报告）
- [x] utils+frontend+migration/runner 子 agent（维度 02+17）：utils 依赖卫生（**pom 级机械面：允许集 nop-api-core/nop-commons/nop-core 及三方库；违规候选带 nop-xlang 及以上逐个裁定；注意 nop-java-parser 的 nop-biz 依赖已注释勿误报**/工具类重复度 + nop-utils.md 23 条标定〔P0×2 不得重复上报〕）+ frontend-support **收窄口径（frontend-benchmark.md 19 条为 prior art——增量 diff〔基线后仅 1 commit〕+ 3.5 撞车域 nop-web/page 聚焦 + 抽样，不盲目全量）** + migration/runner 启动链 + **runner-cli.md 17 条标定**
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限（两面均以复核收敛替代——零驳回）
- [x] 产物 `_tmp/fcr-4.5-{dev,utils}.md`

Exit Criteria:

- [x] 两份初稿落盘，每条发现 ≥9 行含 file:line 与修复方向
- [x] check 标定结论落盘（dev-tools 25 + nop-utils 23 + runner-cli 17 + frontend-benchmark 19 + migration 4 = 88 条）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 标定独立验证（8 条新发现全保留零驳回；标定抽验 A 6/6 + B 14/14 全一致）
- [x] 主 agent 汇总：判级统一、与 4.2（nop-fsm 零测试未修复维持）/1.4（StringHelper 未拆维持）/3.5（P2-3.5-008 未修复三点复现）协调
- [x] 两面正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-4.5-{dev,utils}-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `4.5-frontend-devtools-migration-runner-utils-audit.md`；index 登记 4.5 行（P2×1 进 R1.0 输入池）
- [x] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记（本项零 P0——nop-utils P0×2 已修标定）
- [x] 定向验证锚点：复核员复跑 xlang-debugger 6/6 + maven-shaded-plugin 2/2（离线 -o）+ closure auditor 独立复跑同结果；idea-plugin IDE 宿主测试不可运行以 live 逐行核实为准（已注明）；doc-links exit 0
- [x] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——八项核验全过）；通过后 roadmap 4.5 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且五面全覆盖——覆盖度量：dev-tools=25/25 标定台账 + CLI 差异面裁定表；utils=pom 依赖矩阵 + 重复度清单 + 巨型类现状 + 23 条标定；frontend=撞车域结论 + 抽样清单；runner=启动链结论 + 17 条标定；migration=3 文件结论
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 4.5 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 五组工具链深审计划收口。报告 `4.5-frontend-devtools-migration-runner-utils-audit.md` 落盘：通过无 P0/P1 附 P2×1/P3×7（净 8 条，零驳回）。**标定台账 88 条零翻案**：dev-tools 25（17 闭环+6 成立+2 补位）/ nop-utils 23/23 已修（P0×2 重点核对）/ runner-cli 17/17 未修 / frontend-benchmark 19/19 未修 / migration 4/4 未修（移交 demo-migration 域）。**CLI 双入口三模块两真入口一死占位裁定**（EmptyMain 死占位/NopCliApplication 真 main/jdk11 漂移 3 项）；依赖卫生十模块 1 项 P3 违规（nop-java-parser 三跳传递）+ 2 项裁定不报；debugger 并发族 6 处修复全部到位；6.8 preempt 声明（dev-tools 25 条标定已完成）。closure audit 八项核验全过一次 PASS（处方 log/plan 补记已落实）。
Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_4b8b2236-c97a-44c0-962e-a22fc689668f（fresh session，只读 closure auditor；一次 PASS 附补记处方）
- Evidence:
  - 计数勾稽：P0=0/P1=0/P2=1/P3=7=8 ↔ 报告实点 ↔ index 4.5 行（0/0/1/7）↔ 映射表 8 行 ↔ 复核净账（A 6 + B 2）五处闭合
  - P1/P0 台账：两表无 4.5 新增正确
  - 标定亲读五点全命中：DebugValueHelper:45 Array.getLength/XLangStackFrame:76-88 兜底+零 reportError/UnifiedDiffLine:32-34 Guard.notNull+双测行号/ShellCommand:76-81 sh -c/NopCliMainForJdk11 裸 main 未修确认
  - 判级抽查 4/4 亲读闭环（TestXdslResourceTransformer:31-41 零断言+processXDef 唯一调用点 :73/NopCliApplication:35-38/nop-java-parser pom+2 import+三跳链/WebPageExporter:60-92 无恢复+零生产调用方 grep 复跑）
  - 格式：抽 4 条 9 要素完整
  - special 交付物：CLI 裁定 7/7 事实点独立复验全过（EmptyMain 零引用/quarkus main-class/jdk11 exclude/MainCommand 17 子命令亲数）；依赖卫生十模块矩阵齐
  - 验证：doc-links exit 0；auditor 独立复跑 xlang-debugger 6/6 + maven-shaded 2/2
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以复核员复跑 + closure auditor 独立复跑为锚
  - Deferred 项分类检查：无 deferred；P2×1 进 R1.0 输入池；P3×7 watch-only；撞车未修复项（P2-3.5-008/P1-4.2-005/P2-1.4-001/demo-migration 4 条）维持原台账归属，无 in-scope live defect 被静默降级

Follow-up:

- no remaining plan-owned work（P2×1 修复归属 R1.0 分族；P3×7 watch-only；runner-cli 17 条/frontend-benchmark 19 条未修标定归原 check 台账 R1.0 汇聚；demo-migration 4/4 未修复移交该域；6.8 preempt——dev-tools 25 条引用本报告）
